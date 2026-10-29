"""Controlador de edição de um diagrama: ferramentas, seleção, arrasto, clipboard, desfazer e propriedades.

Recebe eventos abstratos (coordenadas do modelo + modificadores), de modo que toda a interação
pode ser testada sem interface gráfica. A janela apenas traduz eventos do toolkit.
"""
from __future__ import annotations

import copy
from dataclasses import dataclass
from typing import Callable, NamedTuple

from . import sequence
from . import catalog
from . import rules
from .diagram import Diagram
from .edges import Edge, EdgeKind
from .errors import ValidationError
from .geometry import Point, Rect, normalized_rect, snap
from .nodes import NODE_TYPES, FieldNode, ImplicitParameterNode, Node, NoteNode, PointNode
from .notifications import Notifier
from .settings import Settings

ZOOM_STEPS = 2
CLICK_THRESHOLD = 5
PASTE_OFFSET = 20


class Mods(NamedTuple):
    ctrl: bool = False
    shift: bool = False


class History:
    """Desfazer/refazer por fotografias do diagrama (memento); uma entrada por ação do usuário (RF21)."""

    def __init__(self) -> None:
        self.undo_stack: list[dict] = []
        self.redo_stack: list[dict] = []
        self.version = 0

    def push(self, before: dict) -> None:
        self.undo_stack.append(before)
        self.redo_stack.clear()
        self.version += 1

    def undo(self, current: dict) -> dict | None:
        if not self.undo_stack:
            return None
        self.redo_stack.append(current)
        self.version += 1
        return self.undo_stack.pop()

    def redo(self, current: dict) -> dict | None:
        if not self.redo_stack:
            return None
        self.undo_stack.append(current)
        self.version += 1
        return self.redo_stack.pop()


@dataclass
class ClipData:
    kind: str
    nodes: list[dict]
    edges: list[dict]


class Clipboard:
    """Área de transferência interna, compartilhada entre os diagramas abertos (RF34)."""
    def __init__(self) -> None:
        self.data: ClipData | None = None
        self.pastes = 0


class Editor:
    def __init__(self, diagram: Diagram, notifier: Notifier, clipboard: Clipboard, settings: Settings) -> None:
        self.diagram, self.notifier, self.clipboard, self.settings = diagram, notifier, clipboard, settings
        self.history = History()
        self.tool = catalog.tool(diagram.kind, "Select")
        self.selection: list = []
        self.zoom_level = 0
        self.rubber: tuple[Point, Point] | None = None
        self.lasso: Rect | None = None
        self.on_edit_requested: Callable[[object], None] | None = None
        self.listeners: list[Callable[[], None]] = []
        self._edge_start: Node | None = None
        self._edge_origin: Point | None = None
        self._lasso_origin: Point | None = None
        self._drag: dict | None = None
        self._press_hit = None

    # ---------- infraestrutura ----------
    def changed(self) -> None:
        for listener in list(self.listeners):
            listener()

    def _attempt(self, action: Callable[[], object]):
        """Executa uma edição como uma única entrada de histórico; erros viram notificações."""
        before = self.diagram.to_dict()
        try:
            result = action()
        except ValidationError as exc:
            if not exc.silent:
                self.notifier.error(exc.message)
            self.changed()
            return None
        self.diagram.relayout()
        if self.diagram.to_dict() != before:
            self.history.push(before)
        self.changed()
        return result if result is not None else True

    # ---------- ferramentas (RF07) ----------
    def activate_tool(self, name: str) -> str:
        tool = catalog.tool(self.diagram.kind, name)
        if tool.category == "clipboard":
            return "clipboard"
        self.tool = tool
        self._cancel_gesture()
        return "ok"

    def activate_shortcut(self, key: str) -> str | None:
        tool = catalog.tool_for_shortcut(self.diagram.kind, key)
        return self.activate_tool(tool.name) if tool else None

    def _cancel_gesture(self) -> None:
        self.rubber = self.lasso = self._drag = self._edge_start = None

    # ---------- zoom (RF37) ----------
    @property
    def zoom_factor(self) -> float: return 1.25 ** self.zoom_level
    def zoom_in(self) -> None: self.zoom_level = min(ZOOM_STEPS, self.zoom_level + 1); self.changed()
    def zoom_out(self) -> None: self.zoom_level = max(-ZOOM_STEPS, self.zoom_level - 1); self.changed()
    def reset_zoom(self) -> None: self.zoom_level = 0; self.changed()

    # ---------- eventos do mouse ----------
    def press(self, x: float, y: float, mods: Mods = Mods()) -> None:
        category = self.tool.category
        if category == "select":
            self._press_select(x, y, mods)
        elif category == "node":
            self._create_node(x, y)
        elif category == "edge":
            self._begin_edge(x, y)

    def drag(self, x: float, y: float, _mods: Mods = Mods()) -> None:
        if self._lasso_origin is not None:
            self.lasso = normalized_rect(self._lasso_origin, Point(x, y))
        elif self._edge_start is not None:
            self.rubber = (self._edge_origin, Point(x, y))
        elif self._drag is not None:
            self._move_selection(x, y)
        self.changed()

    def release(self, x: float, y: float, mods: Mods = Mods()) -> None:
        if self._edge_start is not None:
            self._finish_edge(x, y)
        elif self._lasso_origin is not None:
            self._finish_lasso()
        elif self._drag is not None:
            self._finish_drag(mods)
        self._cancel_gesture()
        self._lasso_origin = self._press_hit = None
        self.changed()

    def double_click(self, x: float, y: float) -> None:
        hit = self.diagram.node_at(x, y) or self.diagram.edge_at(x, y)
        if hit is not None:
            self.selection = [hit]
            self.request_properties()

    # ---------- seleção (RF32, RF33) ----------
    def _press_select(self, x, y, mods) -> None:
        d = self.diagram
        hit = d.node_at(x, y) or d.edge_at(x, y)
        self._press_hit = hit
        if hit is None:
            if not mods.ctrl:
                self.selection = []
            self._lasso_origin = Point(x, y)
            self.lasso = Rect(x, y, 0, 0)
        elif mods.ctrl:
            self.selection = [s for s in self.selection if s is not hit] if hit in self.selection else self.selection + [hit]
        else:
            if hit not in self.selection:
                self.selection = [hit]
            if any(isinstance(s, Node) and s.MOVABLE for s in self.selection):
                self._drag = {"last": Point(x, y), "before": d.to_dict(), "moved": False}
        self.changed()

    def _finish_lasso(self) -> None:
        region = self.lasso
        d = self.diagram
        nodes = [n for n in d.nodes if n.SELECTABLE and region.contains_rect(n.bounds)]
        inside = lambda n: n in nodes or isinstance(n, PointNode)
        edges = [e for e in d.edges if inside(e.start) and inside(e.end)]
        picked = nodes + edges
        self.selection = list(dict.fromkeys(self.selection + picked)) if picked else self.selection

    def select_all(self) -> None:
        self.selection = [n for n in self.diagram.nodes if n.SELECTABLE] + list(self.diagram.edges)
        self.changed()

    def _top_level_movers(self) -> list[Node]:
        chosen = [s for s in self.selection if isinstance(s, Node) and s.MOVABLE]
        return [n for n in chosen if not any(a in chosen for a in n.ancestors())]

    def _move_selection(self, x: float, y: float) -> None:
        drag = self._drag
        dx, dy = x - drag["last"].x, y - drag["last"].y
        drag["last"] = Point(x, y)
        if abs(dx) + abs(dy) > 0:
            drag["moved"] = True
        for node in self._top_level_movers():
            self.diagram.move_node(node, dx, 0 if isinstance(node, ImplicitParameterNode) else dy)
        self.diagram.relayout()

    def _finish_drag(self, mods: Mods) -> None:
        drag, d = self._drag, self.diagram
        if not drag["moved"]:
            if self._press_hit is not None and not mods.ctrl:
                self.selection = [self._press_hit]
            return
        for node in self._top_level_movers():
            d.move_node(node, max(0, snap(node.x)) - node.x, 0 if isinstance(node, ImplicitParameterNode) else max(0, snap(node.y)) - node.y)
            if mods.shift:
                self._toggle_package_membership(node)
        d.relayout()
        if d.to_dict() != drag["before"]:
            self.history.push(drag["before"])

    def _toggle_package_membership(self, node: Node) -> None:
        """Shift ao soltar: desanexa do pacote atual ou anexa ao pacote sob o nó (RF15)."""
        d = self.diagram
        if node.parent is not None and node.parent.TYPE == "Package":
            d.detach(node)
            return
        c = node.bounds.center
        container = d.container_at(c.x, c.y, node, exclude=node)
        if container is not None:
            d.attach(node, container)

    # ---------- criação de nós (RF11–RF13, RF15, RF25) ----------
    def _create_node(self, x: float, y: float) -> None:
        def create():
            d = self.diagram
            node = self.tool.node_cls(max(0, snap(x)), max(0, snap(y)))
            parent = d.container_at(x, y, node)
            if isinstance(node, FieldNode) and parent is None:
                raise ValidationError("Um campo deve pertencer a um objeto: clique dentro de um objeto.")
            if parent is None and d.node_at(x, y) is not None:
                raise ValidationError("Clique em um espaço vazio para criar o elemento.")
            d.add_node(node, parent)
            self.selection = [node]
            return node
        node = self._attempt(create)
        if node and node is not True and self.settings.auto_edit and node.FIELDS:
            self.request_properties()

    # ---------- criação de arestas (RF14, RF16, RF19, RF26–RF30) ----------
    def _begin_edge(self, x: float, y: float) -> None:
        start = self.diagram.node_at(x, y)
        if start is None:
            return
        if self.tool.edge_kind is EdgeKind.REFERENCE and not (isinstance(start, FieldNode) and x >= start.x + start.w / 2):
            self.notifier.error("Referências partem da parte Value de um campo.")
            return
        self._edge_start, self._edge_origin = start, Point(x, y)
        self.rubber = (self._edge_origin, self._edge_origin)

    def _finish_edge(self, x: float, y: float) -> None:
        start, origin = self._edge_start, self._edge_origin
        end = self.diagram.node_at(x, y)
        moved = abs(x - origin.x) + abs(y - origin.y) >= CLICK_THRESHOLD
        if not moved:
            return
        self._connect(start, end, Point(x, y))

    def _connect(self, start: Node, end: Node | None, point: Point) -> None:
        tool, d = self.tool, self.diagram
        def connect():
            kind, target = tool.edge_kind, end
            if kind is EdgeKind.CALL:
                edge = sequence.connect_call(d, start, target, point.y)
            elif kind is EdgeKind.RETURN:
                edge = sequence.connect_return(d, start, target)
            else:
                if target is None:
                    if kind is EdgeKind.NOTE_CONNECTOR and isinstance(start, NoteNode):
                        target = PointNode(snap(point.x), snap(point.y))
                    else:
                        raise ValidationError("A aresta deve terminar sobre um elemento.")
                rules.check_edge(d, kind, start, target)
                if isinstance(target, PointNode) and target not in d.nodes:
                    d.add_node(target)
                edge = d.add_edge(Edge(kind, start, target, **tool.preset))
            self.selection = [edge]
        self._attempt(connect)

    # ---------- exclusão (RF35) ----------
    def delete_selected(self) -> None:
        if self.selection:
            self._attempt(self._delete_current_selection)

    def _delete_current_selection(self) -> None:
        d = self.diagram
        nodes = {s for s in self.selection if isinstance(s, Node)}
        edges = {s for s in self.selection if isinstance(s, Edge)}
        if d.kind == "Sequence":
            nodes, edges = sequence.expand_deletion(d, nodes, edges)
        for edge in edges:
            d.remove_edge(edge)
        for node in nodes:
            if node in d.nodes:
                d.remove_node(node)
        if d.kind == "Sequence":
            sequence.prune_isolated(d)
        self.selection = []

    # ---------- recortar, copiar, colar (RF34) ----------
    def copy_selected(self) -> bool:
        d = self.diagram
        nodes = {s for s in self.selection if isinstance(s, Node)}
        for n in list(nodes):
            nodes.update(n.descendants())
        chosen = {s for s in self.selection if isinstance(s, Edge)}
        for e in chosen:
            nodes.update(n for n in (e.start, e.end) if isinstance(n, PointNode))
        if not nodes:
            return False
        edges = [e for e in d.edges if e in chosen or (e.start in nodes and e.end in nodes)]
        ordered = [n for n in d.nodes if n in nodes]
        self.clipboard.data = ClipData(d.kind, [self._node_dict(n, nodes) for n in ordered],
                                       [{"kind": e.kind.value, "start": e.start.id, "end": e.end.id,
                                         "properties": copy.deepcopy(e.props)} for e in edges if e.start in nodes and e.end in nodes])
        self.clipboard.pastes = 0
        self.notifier.info(f"{len(ordered)} elemento(s) copiado(s).")
        return True

    @staticmethod
    def _node_dict(n: Node, copied: set) -> dict:
        # Um filho copiado sem o pai vira raiz; campos mantêm o pai para a colagem ser recusada (RF25).
        keeps_parent = n.parent is not None and (n.parent in copied or n.TYPE == "Field")
        return {"id": n.id, "type": n.TYPE, "x": n.x, "y": n.y, "parent": n.parent.id if keeps_parent else None,
                "properties": copy.deepcopy(n.props)}

    def cut_selected(self) -> None:
        if self.copy_selected():
            self.delete_selected()

    def paste(self) -> None:
        data = self.clipboard.data
        if data is None:
            return
        self._attempt(lambda: self._paste(data))

    def _paste(self, data: ClipData) -> None:
        d = self.diagram
        if d.kind == "Sequence":
            raise ValidationError("Colar não é permitido em diagramas de sequência (geraria fluxo de controle inválido).")
        node_ids = {n["id"] for n in data.nodes}
        types, kinds = catalog.allowed_node_types(d.kind), catalog.allowed_edge_kinds(d.kind)
        if any(n["type"] not in types for n in data.nodes) or any(EdgeKind(e["kind"]) not in kinds for e in data.edges):
            raise ValidationError(f"Elementos copiados de um diagrama {data.kind} são incompatíveis com este diagrama {d.kind}.")
        if any(n["type"] == "Field" and n["parent"] not in node_ids for n in data.nodes):
            raise ValidationError("Campos só podem ser colados junto com o objeto a que pertencem.")
        self.clipboard.pastes += 1
        offset = PASTE_OFFSET * self.clipboard.pastes
        created: dict[int, Node] = {}
        pending = list(data.nodes)
        while pending:
            for entry in list(pending):
                if entry["parent"] is None or entry["parent"] in created:
                    node = NODE_TYPES[entry["type"]](entry["x"] + offset, entry["y"] + offset)
                    node.props.update(copy.deepcopy(entry["properties"]))
                    d.add_node(node, created.get(entry["parent"]))
                    created[entry["id"]] = node
                    pending.remove(entry)
        edges = []
        for entry in data.edges:
            kind = EdgeKind(entry["kind"])
            start, end = created[entry["start"]], created[entry["end"]]
            rules.check_edge(d, kind, start, end)
            edge = Edge(kind, start, end)
            edge.props.update(copy.deepcopy(entry["properties"]))
            edges.append(d.add_edge(edge))
        self.selection = [n for n in created.values() if n.SELECTABLE] + edges

    # ---------- desfazer/refazer (RF21) ----------
    def undo(self) -> None: self._travel(self.history.undo)
    def redo(self) -> None: self._travel(self.history.redo)

    def _travel(self, step) -> None:
        snapshot = step(self.diagram.to_dict())
        if snapshot is not None:
            self.diagram.restore(snapshot)
            self.diagram.relayout()
            self.selection = []
            self.changed()

    # ---------- propriedades (RF20, RF22–RF23) ----------
    def request_properties(self) -> None:
        callback = self.on_edit_requested
        if callback is not None and len(self.selection) == 1 and self.fields_for(self.selection[0]):
            callback(self.selection[0])

    @staticmethod
    def fields_for(element):
        return element.fields() if isinstance(element, Edge) else element.FIELDS

    def values_for(self, element) -> dict:
        return {f.name: element.get(f.name) for f in self.fields_for(element)}

    def apply_properties(self, element, values: dict) -> bool:
        def apply():
            old_kind = element.kind if isinstance(element, Edge) else None
            for name, value in values.items():
                element.set(name, value)
                if isinstance(element, Edge) and element.kind is not old_kind:
                    try:
                        rules.check_edge(self.diagram, element.kind, element.start, element.end, ignore=element)
                    except ValidationError:
                        element.kind = old_kind
                        raise
                    old_kind = element.kind
        return bool(self._attempt(apply))
