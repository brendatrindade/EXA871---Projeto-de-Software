import json
import sys
import tempfile
import unittest
import xml.dom.minidom
from pathlib import Path

from jetuml.java.jetuml.persistence.python.jetuml import catalog, serialization, svg

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from jetuml.java.jetuml.persistence.python.jetuml import textfmt
from jetuml.java.jetuml.persistence.python.jetuml.edges import Edge, EdgeKind as K
from jetuml.java.jetuml.persistence.python.jetuml.editor import Editor, Mods
from jetuml.java.jetuml.persistence.python.jetuml.errors import ValidationError
from jetuml.java.jetuml.persistence.python.jetuml.settings import Settings, default_diagram_size
from jetuml.java.jetuml.persistence.python.jetuml.workspace import Workspace

CTRL, SHIFT = Mods(ctrl=True), Mods(shift=True)


def make(kind):
    ws = Workspace()
    return ws, ws.new_document(kind)


def click(editor, x, y, mods=Mods()):
    editor.press(x, y, mods)
    editor.release(x, y, mods)


def place(editor, tool, x, y):
    editor.activate_tool(tool)
    click(editor, x, y)
    return editor.selection[0]


def drag(editor, x1, y1, x2, y2, mods=Mods()):
    editor.press(x1, y1, mods)
    editor.drag((x1 + x2) / 2, (y1 + y2) / 2, mods)
    editor.drag(x2, y2, mods)
    editor.release(x2, y2, mods)


def errors(ws): return [n.message for n in ws.notifier.history if n.level == "error"]


class Catalog(unittest.TestCase):
    def test_tool_order_matches_requirements(self):
        names = {k: [t.name for t in catalog.tools_for(k)] for k in catalog.TOOLS}
        self.assertEqual(names["Class"], ["Select", "Class", "Interface", "Package", "Package Description", "Note", "Dependency",
                                           "Inheritance", "Realization", "Association", "Aggregation", "Composition",
                                           "Note Connector", "To Clipboard"])
        self.assertEqual(names["Sequence"], ["Select", "Implicit Parameter", "Note", "Call", "Return", "Note Connector", "To Clipboard"])
        self.assertEqual(names["State"][1:4], ["State", "Initial State", "Final State"])
        self.assertEqual(names["Object"][1:3], ["Object", "Field"])
        self.assertEqual(names["Use Case"][-4:], ["Extend", "Generalization", "Note Connector", "To Clipboard"])

    def test_shortcuts(self):
        self.assertEqual([catalog.shortcut(i) for i in (0, 8, 9, 10, 11)], ["1", "9", "0", "A", "B"])
        self.assertEqual(catalog.tool_for_shortcut("Class", "2").name, "Class")
        self.assertEqual(catalog.tool_for_shortcut("Class", "c").name, "Note Connector")   # C = 13ª ferramenta
        self.assertIsNone(catalog.tool_for_shortcut("Sequence", "9"))


class Text(unittest.TestCase):
    def test_formatting_and_stereotype(self):
        self.assertEqual(textfmt.parse_line("/Abstract/"), ("Abstract", True, False))
        self.assertEqual(textfmt.parse_line("_count_"), ("count", False, True))
        self.assertEqual(textfmt.parse_line("plain"), ("plain", False, False))
        self.assertEqual(textfmt.insert_stereotype("ab", 1), ("a«»b", 2))


class Nodes(unittest.TestCase):
    def test_create_snaps_and_undo_redo(self):
        ws, doc = make("Class")
        node = place(doc.editor, "Class", 103, 98)
        self.assertEqual((node.x, node.y), (100, 100))
        self.assertTrue(doc.dirty)
        doc.editor.undo()
        self.assertEqual(doc.diagram.nodes, [])
        doc.editor.redo()
        self.assertEqual(len(doc.diagram.nodes), 1)

    def test_cannot_create_on_occupied_space(self):
        ws, doc = make("Class")
        place(doc.editor, "Class", 100, 100)
        doc.editor.activate_tool("Class")
        click(doc.editor, 120, 120)
        self.assertEqual(len(doc.diagram.nodes), 1)
        self.assertTrue(errors(ws))

    def test_package_children_and_shift(self):
        ws, doc = make("Class")
        e = doc.editor
        pkg = place(e, "Package", 100, 100)
        child = place(e, "Class", pkg.x + 20, pkg.y + 30)               # clique dentro do pacote
        self.assertIs(child.parent, pkg)
        loose = place(e, "Class", 500, 400)
        e.activate_tool("Select")
        drag(e, loose.x + 5, loose.y + 5, pkg.x + 40, pkg.y + 60, SHIFT)  # solta sobre o pacote com Shift
        self.assertIs(loose.parent, pkg)
        drag(e, child.x + 5, child.y + 5, child.x + 5, child.y + 5 + 200, SHIFT)  # Shift desanexa
        self.assertIsNone(child.parent)

    def test_moving_package_moves_children(self):
        ws, doc = make("Class")
        e = doc.editor
        pkg = place(e, "Package", 100, 100)
        child = place(e, "Class", 120, 130)
        e.activate_tool("Select")
        before = child.x
        drag(e, pkg.x + 2, pkg.y + 2, pkg.x + 102, pkg.y + 2)
        self.assertEqual(child.x, before + 100)

    def test_field_requires_object(self):
        ws, doc = make("Object")
        e = doc.editor
        e.activate_tool("Field")
        click(e, 300, 300)
        self.assertEqual(doc.diagram.nodes, [])
        self.assertTrue(errors(ws))
        obj = place(e, "Object", 100, 100)
        field = place(e, "Field", obj.x + 10, obj.y + 10)
        self.assertIs(field.parent, obj)

    def test_auto_edit_requests_properties(self):
        ws, doc = make("Class")
        asked = []
        doc.editor.on_edit_requested = asked.append
        place(doc.editor, "Class", 100, 100)
        self.assertEqual(asked, [])
        ws.settings.auto_edit = True
        place(doc.editor, "Class", 400, 100)
        self.assertEqual(len(asked), 1)


class Edges(unittest.TestCase):
    def setUp(self):
        self.ws, self.doc = make("Class")
        e = self.doc.editor
        self.a = place(e, "Class", 100, 100)
        self.b = place(e, "Class", 400, 100)

    def connect(self, tool, a, b):
        self.doc.editor.activate_tool(tool)
        drag(self.doc.editor, a.x + 10, a.y + 10, b.x + 10, b.y + 10)

    def test_valid_edge_and_duplicate_rejected(self):
        self.connect("Association", self.a, self.b)
        self.assertEqual(len(self.doc.diagram.edges), 1)
        self.connect("Association", self.a, self.b)
        self.assertEqual(len(self.doc.diagram.edges), 1)
        self.assertTrue(errors(self.ws))

    def test_back_and_forth_cycles_rejected(self):
        for kind in ("Inheritance", "Dependency", "Aggregation"):
            self.connect(kind, self.a, self.b)
            n = len(self.doc.diagram.edges)
            self.connect(kind, self.b, self.a)
            self.assertEqual(len(self.doc.diagram.edges), n, kind)

    def test_association_and_aggregation_cannot_coexist(self):
        self.connect("Association", self.a, self.b)
        self.connect("Aggregation", self.b, self.a)
        self.assertEqual([e.kind for e in self.doc.diagram.edges], [K.ASSOCIATION])

    def test_self_edges(self):
        e = self.doc.editor
        for tool in ("Inheritance", "Dependency"):
            e.activate_tool(tool)
            drag(e, self.a.x + 10, self.a.y + 10, self.a.x + 40, self.a.y + 20)
        self.assertEqual(self.doc.diagram.edges, [])
        e.activate_tool("Association")
        drag(e, self.a.x + 10, self.a.y + 10, self.a.x + 40, self.a.y + 20)
        self.assertEqual(len(self.doc.diagram.edges), 1)

    def test_click_without_drag_creates_nothing(self):
        self.doc.editor.activate_tool("Association")
        click(self.doc.editor, self.a.x + 10, self.a.y + 10)
        self.assertEqual(self.doc.diagram.edges, [])

    def test_edge_ending_on_empty_space_is_reported(self):
        self.doc.editor.activate_tool("Association")
        drag(self.doc.editor, self.a.x + 10, self.a.y + 10, 700, 500)
        self.assertEqual(self.doc.diagram.edges, [])
        self.assertTrue(errors(self.ws))

    def test_edges_follow_nodes(self):
        self.connect("Association", self.a, self.b)
        from jetuml.java.jetuml.persistence.python.jetuml.layout import edge_path
        before = edge_path(self.doc.diagram, self.doc.diagram.edges[0]).points[0]
        self.doc.editor.activate_tool("Select")
        drag(self.doc.editor, self.a.x + 10, self.a.y + 10, self.a.x + 10, self.a.y + 110)
        after = edge_path(self.doc.diagram, self.doc.diagram.edges[0]).points[0]
        self.assertNotEqual(before, after)

    def test_edge_properties_and_conversion(self):
        self.connect("Aggregation", self.a, self.b)
        edge = self.doc.diagram.edges[0]
        fields = [f.name for f in edge.fields()]
        self.assertEqual(fields, ["start_label", "middle_label", "end_label", "directionality", "aggregation_type"])
        self.assertTrue(self.doc.editor.apply_properties(edge, {"middle_label": "has", "aggregation_type": "Composition",
                                                                  "directionality": "Unidirectional"}))
        self.assertIs(edge.kind, K.COMPOSITION)
        self.assertEqual(edge.get("middle_label"), "has")
        self.doc.editor.undo()
        self.assertIs(self.doc.diagram.edges[0].kind, K.AGGREGATION)

    def test_generalization_type_switches_inheritance_and_realization(self):
        self.connect("Inheritance", self.a, self.b)
        edge = self.doc.diagram.edges[0]
        self.doc.editor.apply_properties(edge, {"generalization_type": "Implementation"})
        self.assertIs(edge.kind, K.REALIZATION)

    def test_note_connector_to_node_and_to_point(self):
        e = self.doc.editor
        note = place(e, "Note", 100, 400)
        e.activate_tool("Note Connector")
        drag(e, self.a.x + 10, self.a.y + 10, note.x + 5, note.y + 5)            # nó → nota
        drag(e, note.x + 5, note.y + 5, 600, 450)                                 # nota → ponto fixo
        kinds = [x.kind for x in self.doc.diagram.edges]
        self.assertEqual(kinds, [K.NOTE_CONNECTOR, K.NOTE_CONNECTOR])
        self.assertEqual(sum(1 for n in self.doc.diagram.nodes if n.TYPE == "Point"), 1)
        drag(e, self.a.x + 10, self.a.y + 10, self.b.x + 10, self.b.y + 10)       # nó → nó: inválido
        self.assertEqual(len(self.doc.diagram.edges), 2)
        e.activate_tool("Select")
        e.selection = [self.doc.diagram.edges[1]]
        e.delete_selected()
        self.assertEqual(sum(1 for n in self.doc.diagram.nodes if n.TYPE == "Point"), 0)   # âncora órfã removida


class StateDiagram(unittest.TestCase):
    def test_transition_rules(self):
        ws, doc = make("State")
        e = doc.editor
        s1, s2 = place(e, "State", 100, 100), place(e, "State", 400, 100)
        ini, fin = place(e, "Initial State", 100, 300), place(e, "Final State", 400, 300)

        def tr(a, b):
            e.activate_tool("Transition")
            drag(e, a.x + a.w / 2, a.y + a.h / 2, b.x + b.w / 2, b.y + b.h / 2)
        count = lambda: len(doc.diagram.edges)
        tr(s1, s2); tr(s1, s2)
        self.assertEqual(count(), 2)                       # até duas por direção
        tr(s1, s2)
        self.assertEqual(count(), 2)
        tr(s2, s1)
        self.assertEqual(count(), 3)
        tr(fin, s1)
        self.assertEqual(count(), 3)                       # nada parte de estado final
        tr(s1, ini)
        self.assertEqual(count(), 3)                       # nada chega em estado inicial
        for _ in range(3):
            e.activate_tool("Transition")
            drag(e, s1.x + 10, s1.y + 10, s1.x + 30, s1.y + 20)
        self.assertEqual(sum(1 for x in doc.diagram.edges if x.is_self_edge()), 2)   # duas autotransições


class ObjectDiagram(unittest.TestCase):
    def test_reference_and_collaboration(self):
        ws, doc = make("Object")
        e = doc.editor
        o1, o2 = place(e, "Object", 100, 100), place(e, "Object", 400, 100)
        f = place(e, "Field", o1.x + 10, o1.y + 10)
        e.activate_tool("Reference")
        drag(e, f.x + 5, f.y + 5, o2.x + 10, o2.y + 10)                           # parte esquerda: inválido
        self.assertEqual(doc.diagram.edges, [])
        drag(e, f.x + f.w - 8, f.y + 5, o2.x + 10, o2.y + 10)
        self.assertEqual([x.kind for x in doc.diagram.edges], [K.REFERENCE])
        e.activate_tool("Collaboration")
        drag(e, o1.x + 10, o1.y + 10, o2.x + 10, o2.y + 10)
        drag(e, o2.x + 10, o2.y + 10, o1.x + 10, o1.y + 10)                       # ciclo
        drag(e, o1.x + 10, o1.y + 10, o1.x + 40, o1.y + 20)                       # auto-colaboração
        self.assertEqual(sum(1 for x in doc.diagram.edges if x.kind is K.COLLABORATION), 1)


class UseCaseDiagram(unittest.TestCase):
    def test_rules_and_dependency_type(self):
        ws, doc = make("Use Case")
        e = doc.editor
        actor, u1, u2 = place(e, "Actor", 50, 100), place(e, "Use Case", 300, 100), place(e, "Use Case", 300, 300)
        c = lambda n: (n.x + n.w / 2, n.y + n.h / 2)

        def link(tool, a, b):
            e.activate_tool(tool)
            drag(e, *c(a), *c(b))
        link("Association", actor, u1)
        link("Include", u1, u2)
        link("Include", actor, u2)                                    # ator não participa de include
        link("Generalization", actor, u1)                             # tipos diferentes
        e.activate_tool("Association")
        drag(e, u1.x + 20, u1.y + 20, u1.x + 40, u1.y + 25)           # autoaresta
        self.assertEqual([x.kind for x in doc.diagram.edges], [K.ASSOCIATION, K.USE_CASE_DEPENDENCY])
        dep = doc.diagram.edges[1]
        self.assertEqual(dep.get("dependency_type"), "Include")
        e.apply_properties(dep, {"dependency_type": "Extend"})
        self.assertEqual(dep.get("dependency_type"), "Extend")


class SequenceDiagram(unittest.TestCase):
    def setUp(self):
        self.ws, self.doc = make("Sequence")
        e = self.doc.editor
        self.a = place(e, "Implicit Parameter", 50, 20)
        self.b = place(e, "Implicit Parameter", 250, 20)
        self.c = place(e, "Implicit Parameter", 450, 20)

    def call(self, start, end, y=100):
        e = self.doc.editor
        e.activate_tool("Call")
        sx = start.x + start.w / 2 if start.TYPE == "ImplicitParameter" else start.x + 8
        ex = end.x + end.w / 2 if end.TYPE == "ImplicitParameter" else end.x + 8
        drag(e, sx, 100 if start.TYPE == "ImplicitParameter" else start.y + 10, ex, y)

    def boxes(self): return [n for n in self.doc.diagram.nodes if n.TYPE == "Call"]

    def test_initial_call_creates_both_activation_boxes(self):
        self.call(self.a, self.b)
        self.assertEqual(len(self.boxes()), 2)
        self.assertEqual([x.kind for x in self.doc.diagram.edges], [K.CALL])

    def test_additional_call_from_activation_box(self):
        self.call(self.a, self.b)
        callee = self.doc.diagram.edges[0].end
        self.call(callee, self.c)
        self.assertEqual(len(self.boxes()), 3)

    def test_self_call_inside_activation_box(self):
        self.call(self.a, self.b)
        callee = self.doc.diagram.edges[0].end
        e = self.doc.editor
        e.activate_tool("Call")
        drag(e, callee.x + 8, callee.y + 10, callee.x + 8, callee.y + 20)
        self.assertEqual(len(self.boxes()), 3)
        self.assertIs(self.boxes()[-1].parent, self.b)

    def test_constructor_call(self):
        self.call(self.a, self.b)
        e = self.doc.editor
        e.activate_tool("Call")
        first = self.doc.diagram.edges[0].end
        drag(e, first.x + 8, first.y + 10, self.c.x + 30, self.c.y + 10)          # solta sobre o cabeçalho
        ctor = self.doc.diagram.edges[1]
        self.assertEqual(ctor.get("middle_label"), "«create»")
        self.assertEqual(self.c.y, ctor.row_y - 20)                                # alinhado verticalmente
        self.assertIs(ctor.end.parent, self.c)

    def test_return_and_silent_jump(self):
        self.call(self.a, self.b)
        call = self.doc.diagram.edges[0]
        e = self.doc.editor
        e.activate_tool("Return")
        drag(e, call.end.x + 8, call.end.y + 10, call.start.x + 8, call.start.y + 10)
        self.assertEqual([x.kind for x in self.doc.diagram.edges], [K.CALL, K.RETURN])
        n_errors = len(errors(self.ws))
        self.call(call.end, self.c)                            # chamar a partir de caixa que já retornou: salto, silencioso
        self.assertEqual(len(self.doc.diagram.edges), 2)
        self.assertEqual(len(errors(self.ws)), n_errors)

    def test_structural_rejections_are_reported(self):
        self.call(self.a, self.b)
        first, second = self.doc.diagram.edges[0].start, self.doc.diagram.edges[0].end
        self.call(second, self.c)
        third = self.doc.diagram.edges[1].end
        e = self.doc.editor
        e.activate_tool("Call")
        drag(e, first.x + 8, first.y + 10, third.x + 8, third.y + 10)     # caixa com dois chamadores
        self.assertEqual(len(self.doc.diagram.edges), 2)
        e.activate_tool("Call")
        drag(e, self.a.x + 5, 100, 700, 300)                               # destino vazio
        drag(e, first.x + 8, first.y + 10, self.a.x + 40, self.a.y + 10)  # sobre cabeçalho já com caixa
        self.assertGreaterEqual(len(errors(self.ws)), 2)

    def test_single_root_only(self):
        self.call(self.a, self.b)
        e = self.doc.editor
        e.activate_tool("Call")
        drag(e, self.c.x + 30, 100, self.c.x + 30, 110)    # nova raiz em outra linha de vida
        self.assertEqual(len(self.boxes()), 2)
        self.assertTrue(errors(self.ws))

    def test_calls_and_returns_only_between_activation_boxes(self):
        from jetuml.java.jetuml.persistence.python.jetuml import rules
        with self.assertRaises(ValidationError):
            rules.check_edge(self.doc.diagram, K.CALL, self.a, self.b)

    def test_delete_call_cascades(self):
        self.call(self.a, self.b)
        callee = self.doc.diagram.edges[0].end
        self.call(callee, self.c)
        e = self.doc.editor
        e.activate_tool("Select")
        e.selection = [self.doc.diagram.edges[0]]
        e.delete_selected()
        self.assertEqual(self.doc.diagram.edges, [])
        self.assertEqual(self.boxes(), [])

    def test_paste_into_sequence_is_rejected(self):
        src = self.ws.new_document("Class")
        place(src.editor, "Note", 100, 100)
        src.editor.copy_selected()
        self.doc.editor.paste()
        self.assertEqual(len([n for n in self.doc.diagram.nodes if n.TYPE == "Note"]), 0)
        self.assertTrue(errors(self.ws))

    def test_open_bottom_property(self):
        self.call(self.a, self.b)
        box = self.boxes()[0]
        self.assertEqual([f.name for f in box.FIELDS], ["open_bottom"])
        self.assertTrue(self.doc.editor.apply_properties(box, {"open_bottom": True}))


class Selection(unittest.TestCase):
    def setUp(self):
        self.ws, self.doc = make("Class")
        e = self.doc.editor
        self.a, self.b, self.c = place(e, "Class", 100, 100), place(e, "Class", 400, 100), place(e, "Class", 700, 100)
        e.activate_tool("Association")
        drag(e, 110, 110, 410, 110)
        e.activate_tool("Select")
        e.selection = []

    def test_click_ctrl_click_toggle(self):
        e = self.doc.editor
        click(e, self.a.x + 5, self.a.y + 5)
        click(e, self.b.x + 5, self.b.y + 5, CTRL)
        self.assertEqual(e.selection, [self.a, self.b])
        click(e, self.a.x + 5, self.a.y + 5, CTRL)
        self.assertEqual(e.selection, [self.b])

    def test_lasso_selects_region(self):
        e = self.doc.editor
        drag(e, 50, 50, 600, 300)
        self.assertEqual(set(map(id, e.selection)), {id(self.a), id(self.b), id(self.doc.diagram.edges[0])})

    def test_select_all_includes_edges_and_move_all(self):
        e = self.doc.editor
        e.select_all()
        self.assertEqual(len(e.selection), 4)
        xs = [n.x for n in self.doc.diagram.nodes]
        drag(e, self.a.x + 5, self.a.y + 5, self.a.x + 55, self.a.y + 5)
        self.assertEqual([n.x for n in self.doc.diagram.nodes], [x + 50 for x in xs])

    def test_drag_snaps_to_grid_even_without_grid(self):
        self.ws.settings.show_grid = False
        e = self.doc.editor
        drag(e, self.a.x + 5, self.a.y + 5, self.a.x + 18, self.a.y + 13)
        self.assertEqual((self.a.x % 10, self.a.y % 10), (0, 0))

    def test_delete_removes_incident_edges(self):
        e = self.doc.editor
        e.selection = [self.a]
        e.delete_selected()
        self.assertEqual(self.doc.diagram.edges, [])
        self.assertEqual(len(self.doc.diagram.nodes), 2)
        e.undo()
        self.assertEqual(len(self.doc.diagram.edges), 1)


class Clipboard(unittest.TestCase):
    def test_copy_paste_cut_with_edges_in_same_diagram(self):
        ws, doc = make("Class")
        e = doc.editor
        a, b = place(e, "Class", 100, 100), place(e, "Class", 400, 100)
        e.activate_tool("Association")
        drag(e, 110, 110, 410, 110)
        e.activate_tool("Select")
        e.select_all()
        self.assertTrue(e.copy_selected())
        e.paste()
        self.assertEqual((len(doc.diagram.nodes), len(doc.diagram.edges)), (4, 2))
        self.assertEqual(doc.diagram.nodes[2].x, a.x + 20)
        e.selection = [n for n in doc.diagram.nodes[:2]]
        e.cut_selected()
        self.assertEqual(len(doc.diagram.nodes), 2)
        e.paste()
        self.assertEqual(len(doc.diagram.nodes), 4)

    def test_paste_between_diagrams_checks_compatibility(self):
        ws = Workspace()
        src, other, same = ws.new_document("Class"), ws.new_document("State"), ws.new_document("Class")
        place(src.editor, "Class", 100, 100)
        src.editor.copy_selected()
        other.editor.paste()
        self.assertEqual(other.diagram.nodes, [])
        self.assertTrue(errors(ws))
        same.editor.paste()
        self.assertEqual(len(same.diagram.nodes), 1)
        note = place(src.editor, "Note", 400, 100)
        src.editor.copy_selected()
        other.editor.paste()                                 # nota é compatível com qualquer diagrama
        self.assertEqual([n.TYPE for n in other.diagram.nodes], ["Note"])

    def test_field_cannot_be_pasted_alone(self):
        ws, doc = make("Object")
        obj = place(doc.editor, "Object", 100, 100)
        field = place(doc.editor, "Field", obj.x + 10, obj.y + 10)
        doc.editor.selection = [field]
        doc.editor.copy_selected()
        doc.editor.paste()
        self.assertEqual(len(doc.diagram.nodes), 2)
        self.assertTrue(errors(ws))

    def test_copy_object_brings_its_fields(self):
        ws, doc = make("Object")
        obj = place(doc.editor, "Object", 100, 100)
        place(doc.editor, "Field", obj.x + 10, obj.y + 10)
        doc.editor.selection = [obj]
        doc.editor.copy_selected()
        doc.editor.paste()
        self.assertEqual(len(doc.diagram.nodes), 4)


class Regressions(unittest.TestCase):
    def test_dependency_defaults_to_dashed_style(self):
        ws, doc = make("Class")
        a, b = place(doc.editor, "Class", 100, 100), place(doc.editor, "Class", 400, 100)
        doc.editor.activate_tool("Dependency")
        drag(doc.editor, 110, 110, 410, 110)
        edge = doc.diagram.edges[0]
        self.assertEqual(edge.get("generalization_type"), "Implementation")
        self.assertEqual(edge.get("directionality"), "Unidirectional")

    def test_pasting_a_child_copied_without_its_package_does_not_hang(self):
        ws, doc = make("Class")
        pkg = place(doc.editor, "Package", 100, 100)
        child = place(doc.editor, "Class", pkg.x + 20, pkg.y + 30)
        doc.editor.selection = [child]
        doc.editor.copy_selected()
        doc.editor.paste()
        pasted = doc.diagram.nodes[-1]
        self.assertIsNone(pasted.parent)
        self.assertEqual(len(doc.diagram.nodes), 3)


class Properties(unittest.TestCase):
    def test_fields_per_type(self):
        expected = {"Class": ["name", "attributes", "methods"], "Interface": ["name", "methods"], "Package": ["name"],
                    "Package Description": ["name", "contents"], "Note": ["text"]}
        ws, doc = make("Class")
        for i, (tool, names) in enumerate(expected.items()):
            node = place(doc.editor, tool, 20 + i * 180, 100)
            self.assertEqual([f.name for f in node.FIELDS], names, tool)
        for kind, tool, names in (("Object", "Object", ["name"]), ("Object", "Field", ["name", "value"]),
                                  ("Use Case", "Actor", ["name"]), ("Use Case", "Use Case", ["name"]),
                                  ("State", "State", ["name"]), ("Sequence", "Implicit Parameter", ["name"])):
            self.assertEqual([f.name for f in catalog.tool(kind, tool).node_cls.FIELDS], names)
        self.assertEqual(catalog.tool("State", "Initial State").node_cls.FIELDS, ())

    def test_apply_properties_and_undo(self):
        ws, doc = make("Class")
        node = place(doc.editor, "Class", 100, 100)
        doc.editor.apply_properties(node, {"name": "Cliente", "attributes": "nome\nidade", "methods": "/abstrato()/"})
        self.assertEqual(node.get("name"), "Cliente")
        self.assertGreater(node.h, 60)
        doc.editor.undo()
        self.assertEqual(doc.diagram.nodes[0].get("name"), "Class")

    def test_double_click_and_ctrl_enter_request_dialog(self):
        ws, doc = make("Class")
        node = place(doc.editor, "Class", 100, 100)
        asked = []
        doc.editor.on_edit_requested = asked.append
        doc.editor.activate_tool("Select")
        doc.editor.double_click(node.x + 5, node.y + 5)
        doc.editor.request_properties()
        self.assertEqual(asked, [node, node])


class Persistence(unittest.TestCase):
    def test_roundtrip_all_diagram_types(self):
        ws = Workspace()
        with tempfile.TemporaryDirectory() as folder:
            for kind in ("Class", "State", "Object", "Use Case", "Sequence"):
                doc = ws.new_document(kind)
                if kind == "Class":
                    a = place(doc.editor, "Class", 100, 100)
                    b = place(doc.editor, "Class", 400, 100)
                    doc.editor.apply_properties(a, {"name": 'A "q" \\ ç', "attributes": "x: int"})
                    doc.editor.activate_tool("Association")
                    drag(doc.editor, 110, 110, 410, 110)
                elif kind == "Sequence":
                    la, lb = place(doc.editor, "Implicit Parameter", 50, 20), place(doc.editor, "Implicit Parameter", 250, 20)
                    doc.editor.activate_tool("Call")
                    drag(doc.editor, la.x + 40, 100, lb.x + 40, 100)
                elif kind == "Object":
                    o = place(doc.editor, "Object", 100, 100)
                    place(doc.editor, "Field", o.x + 10, o.y + 10)
                path = Path(folder) / f"{kind.replace(' ', '')}.json"
                ws.save(doc, path)
                self.assertFalse(doc.dirty)
                reopened = ws.open(path)
                self.assertEqual(reopened.diagram.to_dict(), doc.diagram.to_dict())
            self.assertEqual(len(ws.settings.recent_files), 5)

    def test_invalid_files_rejected(self):
        ws = Workspace()
        doc = ws.new_document("Object")
        o = place(doc.editor, "Object", 100, 100)
        place(doc.editor, "Field", o.x + 10, o.y + 10)
        good = json.loads(serialization.dumps(doc.diagram))
        orphan = json.loads(json.dumps(good)); orphan["nodes"][1]["parent"] = None             # campo solto (RF25)
        dangling = json.loads(json.dumps(good)); dangling["edges"].append({"id": 9, "kind": "Reference", "start": 99, "end": 1})
        wrong_type = json.loads(json.dumps(good)); wrong_type["nodes"][0]["type"] = "Class"
        unknown = json.loads(json.dumps(good)); unknown["schema"] = "x"
        for bad in (orphan, dangling, wrong_type, unknown, {}, []):
            with self.assertRaises(ValidationError):
                serialization.loads(json.dumps(bad))
        with self.assertRaises(ValidationError):
            serialization.loads("{nope")

    def test_duplicate_and_close(self):
        ws = Workspace()
        doc = ws.new_document("Class")
        place(doc.editor, "Class", 100, 100)
        copy = ws.duplicate(doc)
        self.assertIsNot(copy.diagram, doc.diagram)
        self.assertEqual(len(copy.diagram.nodes), 1)
        self.assertEqual(len(ws.documents), 2)
        ws.close(copy)
        self.assertIs(ws.active, doc)
        ws.close(doc)
        self.assertIsNone(ws.active)

    def test_open_grows_diagram_size(self):
        ws = Workspace()
        doc = ws.new_document("Class")
        place(doc.editor, "Class", 1500, 1200)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "big.json"
            ws.save(doc, path)
            reopened = ws.open(path)
        self.assertGreater(reopened.diagram.size[0], 1500)


class Export(unittest.TestCase):
    def test_svg_png_jpeg(self):
        ws = Workspace()
        doc = ws.new_document("Class")
        a, b = place(doc.editor, "Class", 100, 100), place(doc.editor, "Interface", 400, 100)
        doc.editor.activate_tool("Realization")
        drag(doc.editor, 110, 110, 410, 110)
        with tempfile.TemporaryDirectory() as folder:
            svg_path = Path(folder) / "d.svg"
            ws.export_image(doc, svg_path)
            xml.dom.minidom.parse(str(svg_path))
            try:
                import PIL
            except ImportError:
                return
            for name in ("d.png", "d.jpg"):
                ws.export_image(doc, Path(folder) / name)
                self.assertGreater((Path(folder) / name).stat().st_size, 100)
            with self.assertRaises(ValidationError):
                ws.export_image(doc, Path(folder) / "d.gif")

    def test_export_is_cropped_and_has_no_grid(self):
        ws = Workspace()
        doc = ws.new_document("Class")
        place(doc.editor, "Class", 300, 300)
        text = svg.render_svg(doc.diagram, doc.diagram.content_bounds().inflated(10))
        self.assertNotIn("#e6e6e6", text)
        self.assertLess(int(xml.dom.minidom.parseString(text).documentElement.getAttribute("width")), 400)

    def test_empty_diagram_exports(self):
        ws = Workspace()
        doc = ws.new_document("State")
        xml.dom.minidom.parseString(svg.render_svg(doc.diagram))


class ViewAndSettings(unittest.TestCase):
    def test_zoom_has_two_levels_each_way(self):
        ws, doc = make("Class")
        e = doc.editor
        for _ in range(5): e.zoom_in()
        self.assertEqual(e.zoom_level, 2)
        for _ in range(9): e.zoom_out()
        self.assertEqual(e.zoom_level, -2)
        e.reset_zoom()
        self.assertEqual(e.zoom_factor, 1)

    def test_diagram_size_validation_and_defaults(self):
        ws = Workspace(screen=(1920, 1080))
        self.assertEqual(ws.new_document("Class").diagram.size, (1920, 1080))
        with self.assertRaises(ValueError): ws.set_diagram_size(100, 500)
        with self.assertRaises(ValueError): ws.set_diagram_size(500, 5000)
        ws.set_diagram_size(250, 4000)
        self.assertEqual(ws.new_document("State").diagram.size, (250, 4000))
        ws.restore_diagram_size()
        self.assertEqual(ws.new_document("State").diagram.size, (1920, 1080))
        self.assertEqual(default_diagram_size(100, 9000), (250, 4000))

    def test_font_changes_layout_and_restores(self):
        ws, doc = make("Class")
        node = place(doc.editor, "Class", 100, 100)
        base = node.h
        ws.set_font("Courier", 20)
        self.assertGreaterEqual(node.h, base)
        with self.assertRaises(ValueError): ws.set_font("Courier", 2)
        ws.restore_font()
        self.assertEqual(node.h, base)

    def test_notifications(self):
        ws, doc = make("Class")
        seen = []
        ws.notifier.listeners.append(seen.append)
        ws.notifier.error("x"); ws.notifier.info("y")
        self.assertEqual([n.level for n in seen], ["error", "info"])
        with self.assertRaises(ValueError): ws.settings.set_notification_duration(11)
        ws.settings.set_notification_duration(0)
        self.assertIsNone(ws.notifier.error("silenciada"))
        self.assertEqual(len(seen), 2)
        ws.settings.restore_notification_duration()
        self.assertEqual(ws.settings.notification_duration, 5)

    def test_settings_persistence_and_recent(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "s.json"
            s = Settings(); s.dark_mode = True; s.set_diagram_size(800, 600)
            for i in range(12): s.add_recent(f"/f{i}.json")
            s.save(path)
            loaded = Settings.load(path)
            self.assertTrue(loaded.dark_mode)
            self.assertEqual(loaded.diagram_size, (800, 600))
            self.assertEqual(len(loaded.recent_files), 10)
            self.assertEqual(loaded.recent_files[0], "/f11.json")
            path.write_text("lixo")
            self.assertFalse(Settings.load(path).dark_mode)


if __name__ == "__main__":
    unittest.main()
