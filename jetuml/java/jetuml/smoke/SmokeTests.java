package jetuml.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import jetuml.app.Document;
import jetuml.app.Notification;
import jetuml.app.Settings;
import jetuml.app.Workspace;
import jetuml.catalog.Catalog;
import jetuml.editor.Editor;
import jetuml.editor.Mods;
import jetuml.geometry.Point;
import jetuml.model.*;
import jetuml.persistence.DiagramSerializer;
import jetuml.persistence.Json;
import jetuml.render.Palette;
import jetuml.render.SvgRenderer;
import jetuml.rules.Rules;
import jetuml.text.TextFormat;

/** Verificações sem janela e sem dependências externas (executadas por {@code java Main --smoke}). */
public final class SmokeTests {
  private int run, failed;

  public static int run() {
    SmokeTests tests = new SmokeTests();
    tests.all();
    if (tests.failed == 0) {
      System.out.println("JetUML Java OK: " + tests.run + " testes de modelo, regras, edição, sequência, arquivos e exportação.");
      return 0;
    }
    System.out.println(tests.failed + " de " + tests.run + " testes falharam.");
    return 1;
  }

  // ---------- infraestrutura ----------
  private interface Body { void run() throws Exception; }

  private void test(String name, Body body) {
    run++;
    try {
      TextFormat.restoreDefault();
      body.run();
    } catch (Throwable t) {
      failed++;
      System.out.println("FALHA " + name + ": " + t);
      StackTraceElement[] st = t.getStackTrace();
      if (st.length > 0) System.out.println("    em " + st[0]);
    }
  }

  private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
  private static void eq(Object expected, Object actual, String message) {
    if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(message + ": esperado " + expected + " mas foi " + actual);
  }

  private static void rejects(Runnable r, String message) {
    try { r.run(); } catch (ValidationException | IllegalArgumentException expected) { return; }
    throw new AssertionError("não rejeitou: " + message);
  }

  private static final class Ctx {
    final Workspace ws = new Workspace();
    final Document doc;
    final Editor e;
    Ctx(String kind) { doc = ws.newDocument(kind); e = doc.editor; }
    Diagram d() { return doc.diagram(); }
  }

  private static void click(Editor e, double x, double y) { click(e, x, y, Mods.NONE); }
  private static void click(Editor e, double x, double y, Mods m) { e.press(x, y, m); e.release(x, y, m); }

  private static Node place(Editor e, String tool, double x, double y) {
    e.activateTool(tool);
    click(e, x, y);
    return (Node) e.selection.get(0);
  }

  private static void drag(Editor e, double x1, double y1, double x2, double y2) { drag(e, x1, y1, x2, y2, Mods.NONE); }
  private static void drag(Editor e, double x1, double y1, double x2, double y2, Mods m) {
    e.press(x1, y1, m);
    e.drag((x1 + x2) / 2, (y1 + y2) / 2, m);
    e.drag(x2, y2, m);
    e.release(x2, y2, m);
  }

  private static double cx(Node n) { return n.x + n.w / 2; }
  private static double cy(Node n) { return n.y + n.h / 2; }
  private static void link(Editor e, String tool, Node a, Node b) { e.activateTool(tool); drag(e, cx(a), cy(a), cx(b), cy(b)); }
  private static void linkCorner(Editor e, String tool, Node a, Node b) { e.activateTool(tool); drag(e, a.x + 10, a.y + 10, b.x + 10, b.y + 10); }
  private static List<EdgeKind> kinds(Diagram d) { return d.edges.stream().map(x -> x.kind).toList(); }
  private static List<String> names(List<Field> fields) { return fields.stream().map(Field::name).toList(); }
  private static long count(Diagram d, String type) { return d.nodes.stream().filter(n -> n.type().equals(type)).count(); }

  // ---------- registro ----------
  private void all() {
    catalogTests(); textTests(); nodeTests(); edgeTests(); stateTests(); objectTests(); useCaseTests();
    sequenceTests(); selectionTests(); clipboardTests(); regressionTests(); propertyTests(); persistenceTests();
    exportTests(); viewTests();
  }

  private void catalogTests() {
    test("ferramentas na ordem dos requisitos", () -> {
      eq(List.of("Select", "Class", "Interface", "Package", "Package Description", "Note", "Dependency", "Inheritance", "Realization",
          "Association", "Aggregation", "Composition", "Note Connector", "To Clipboard"), Catalog.toolNames("Class"), "Class");
      eq(List.of("Select", "Implicit Parameter", "Note", "Call", "Return", "Note Connector", "To Clipboard"), Catalog.toolNames("Sequence"), "Sequence");
      eq(List.of("State", "Initial State", "Final State"), Catalog.toolNames("State").subList(1, 4), "State");
      eq(List.of("Object", "Field"), Catalog.toolNames("Object").subList(1, 3), "Object");
      eq(List.of("Extend", "Generalization", "Note Connector", "To Clipboard"), Catalog.toolNames("Use Case").subList(Catalog.toolNames("Use Case").size() - 4, Catalog.toolNames("Use Case").size()), "Use Case");
    });
    test("atalhos 1–9, 0, A…", () -> {
      eq("1", Catalog.shortcut(0), "1"); eq("9", Catalog.shortcut(8), "9"); eq("0", Catalog.shortcut(9), "0");
      eq("A", Catalog.shortcut(10), "A"); eq("B", Catalog.shortcut(11), "B");
      eq("Class", Catalog.toolForShortcut("Class", "2").name(), "2");
      eq("Note Connector", Catalog.toolForShortcut("Class", "c").name(), "c");
      check(Catalog.toolForShortcut("Sequence", "9") == null, "9 em Sequence");
    });
  }

  private void textTests() {
    test("formatação e estereótipo", () -> {
      eq(new TextFormat.Parsed("Abstract", true, false), TextFormat.parseLine("/Abstract/"), "itálico");
      eq(new TextFormat.Parsed("count", false, true), TextFormat.parseLine("_count_"), "sublinhado");
      eq(new TextFormat.Parsed("plain", false, false), TextFormat.parseLine("plain"), "simples");
      eq(new TextFormat.Inserted("a«»b", 2), TextFormat.insertStereotype("ab", 1), "estereótipo");
    });
  }

  private void nodeTests() {
    test("criar alinha à grade e desfazer/refazer", () -> {
      Ctx c = new Ctx("Class");
      Node node = place(c.e, "Class", 103, 98);
      eq(100.0, node.x, "x"); eq(100.0, node.y, "y");
      check(c.doc.dirty(), "sujo");
      c.e.undo();
      check(c.d().nodes.isEmpty(), "desfeito");
      c.e.redo();
      eq(1, c.d().nodes.size(), "refeito");
    });
    test("não cria sobre espaço ocupado", () -> {
      Ctx c = new Ctx("Class");
      place(c.e, "Class", 100, 100);
      c.e.activateTool("Class");
      click(c.e, 120, 120);
      eq(1, c.d().nodes.size(), "nós");
      check(!c.ws.notifier.errors().isEmpty(), "erro notificado");
    });
    test("filhos de pacote e Shift", () -> {
      Ctx c = new Ctx("Class");
      Node pkg = place(c.e, "Package", 100, 100);
      Node child = place(c.e, "Class", pkg.x + 20, pkg.y + 30);
      check(child.parent == pkg, "filho direto");
      Node loose = place(c.e, "Class", 500, 400);
      c.e.activateTool("Select");
      drag(c.e, loose.x + 5, loose.y + 5, pkg.x + 40, pkg.y + 60, Mods.SHIFT);
      check(loose.parent == pkg, "anexado com Shift");
      drag(c.e, child.x + 5, child.y + 5, child.x + 5, child.y + 205, Mods.SHIFT);
      check(child.parent == null, "desanexado com Shift");
    });
    test("mover pacote move filhos", () -> {
      Ctx c = new Ctx("Class");
      Node pkg = place(c.e, "Package", 100, 100);
      Node child = place(c.e, "Class", 120, 130);
      c.e.activateTool("Select");
      double before = child.x;
      drag(c.e, pkg.x + 2, pkg.y + 2, pkg.x + 102, pkg.y + 2);
      eq(before + 100, child.x, "filho acompanha");
    });
    test("campo exige objeto", () -> {
      Ctx c = new Ctx("Object");
      c.e.activateTool("Field");
      click(c.e, 300, 300);
      check(c.d().nodes.isEmpty(), "campo solto");
      check(!c.ws.notifier.errors().isEmpty(), "erro");
      Node obj = place(c.e, "Object", 100, 100);
      Node field = place(c.e, "Field", obj.x + 10, obj.y + 10);
      check(field.parent == obj, "campo dentro do objeto");
    });
    test("auto edit pede propriedades", () -> {
      Ctx c = new Ctx("Class");
      List<Object> asked = new ArrayList<>();
      c.e.onEditRequested = asked::add;
      place(c.e, "Class", 100, 100);
      eq(0, asked.size(), "sem auto edit");
      c.ws.settings.autoEdit = true;
      place(c.e, "Class", 400, 100);
      eq(1, asked.size(), "com auto edit");
    });
  }

  private static final class ClassEdges {
    final Ctx c = new Ctx("Class");
    final Node a = place(c.e, "Class", 100, 100), b = place(c.e, "Class", 400, 100);
    void connect(String tool, Node x, Node y) { linkCorner(c.e, tool, x, y); }
  }

  private void edgeTests() {
    test("aresta válida e duplicada rejeitada", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Association", t.a, t.b);
      eq(1, t.c.d().edges.size(), "criada");
      t.connect("Association", t.a, t.b);
      eq(1, t.c.d().edges.size(), "duplicada");
      check(!t.c.ws.notifier.errors().isEmpty(), "erro");
    });
    test("ciclos diretos rejeitados", () -> {
      ClassEdges t = new ClassEdges();
      for (String kind : List.of("Inheritance", "Dependency", "Aggregation")) {
        t.connect(kind, t.a, t.b);
        int n = t.c.d().edges.size();
        t.connect(kind, t.b, t.a);
        eq(n, t.c.d().edges.size(), kind);
      }
    });
    test("associação e agregação não coexistem", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Association", t.a, t.b);
      t.connect("Aggregation", t.b, t.a);
      eq(List.of(EdgeKind.ASSOCIATION), kinds(t.c.d()), "tipos");
    });
    test("autoarestas", () -> {
      ClassEdges t = new ClassEdges();
      for (String tool : List.of("Inheritance", "Dependency")) {
        t.c.e.activateTool(tool);
        drag(t.c.e, t.a.x + 10, t.a.y + 10, t.a.x + 40, t.a.y + 20);
      }
      check(t.c.d().edges.isEmpty(), "auto-herança/dependência");
      t.c.e.activateTool("Association");
      drag(t.c.e, t.a.x + 10, t.a.y + 10, t.a.x + 40, t.a.y + 20);
      eq(1, t.c.d().edges.size(), "auto-associação");
    });
    test("clique sem arrasto não cria aresta", () -> {
      ClassEdges t = new ClassEdges();
      t.c.e.activateTool("Association");
      click(t.c.e, t.a.x + 10, t.a.y + 10);
      check(t.c.d().edges.isEmpty(), "sem aresta");
    });
    test("aresta terminando no vazio é reportada", () -> {
      ClassEdges t = new ClassEdges();
      t.c.e.activateTool("Association");
      drag(t.c.e, t.a.x + 10, t.a.y + 10, 700, 500);
      check(t.c.d().edges.isEmpty(), "sem aresta");
      check(!t.c.ws.notifier.errors().isEmpty(), "erro");
    });
    test("arestas acompanham os nós", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Association", t.a, t.b);
      Point before = jetuml.model.EdgePaths.edgePath(t.c.d(), t.c.d().edges.get(0)).points().get(0);
      t.c.e.activateTool("Select");
      drag(t.c.e, t.a.x + 10, t.a.y + 10, t.a.x + 10, t.a.y + 110);
      Point after = jetuml.model.EdgePaths.edgePath(t.c.d(), t.c.d().edges.get(0)).points().get(0);
      check(!before.equals(after), "caminho mudou");
    });
    test("propriedades da aresta e conversão", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Aggregation", t.a, t.b);
      Edge edge = t.c.d().edges.get(0);
      eq(List.of("start_label", "middle_label", "end_label", "directionality", "aggregation_type"), names(edge.fields()), "campos");
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("middle_label", "has");
      values.put("aggregation_type", "Composition");
      values.put("directionality", "Unidirectional");
      check(t.c.e.applyProperties(edge, values), "aplicado");
      eq(EdgeKind.COMPOSITION, edge.kind, "convertida");
      eq("has", edge.get("middle_label"), "rótulo");
      t.c.e.undo();
      eq(EdgeKind.AGGREGATION, t.c.d().edges.get(0).kind, "desfeita");
    });
    test("Generalization Type alterna herança e realização", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Inheritance", t.a, t.b);
      Edge edge = t.c.d().edges.get(0);
      t.c.e.applyProperties(edge, Map.of("generalization_type", "Implementation"));
      eq(EdgeKind.REALIZATION, edge.kind, "realização");
    });
    test("Note Connector para nó e para ponto fixo", () -> {
      ClassEdges t = new ClassEdges();
      Node note = place(t.c.e, "Note", 100, 400);
      t.c.e.activateTool("Note Connector");
      drag(t.c.e, t.a.x + 10, t.a.y + 10, note.x + 5, note.y + 5);
      drag(t.c.e, note.x + 5, note.y + 5, 600, 450);
      eq(List.of(EdgeKind.NOTE_CONNECTOR, EdgeKind.NOTE_CONNECTOR), kinds(t.c.d()), "dois conectores");
      eq(1L, count(t.c.d(), "Point"), "uma âncora");
      drag(t.c.e, t.a.x + 10, t.a.y + 10, t.b.x + 10, t.b.y + 10);
      eq(2, t.c.d().edges.size(), "nó→nó inválido");
      t.c.e.activateTool("Select");
      t.c.e.selection = new ArrayList<>(List.of(t.c.d().edges.get(1)));
      t.c.e.deleteSelected();
      eq(0L, count(t.c.d(), "Point"), "âncora órfã removida");
    });
  }

  private void stateTests() {
    test("regras de transição", () -> {
      Ctx c = new Ctx("State");
      Node s1 = place(c.e, "State", 100, 100), s2 = place(c.e, "State", 400, 100);
      Node ini = place(c.e, "Initial State", 100, 300), fin = place(c.e, "Final State", 400, 300);
      link(c.e, "Transition", s1, s2); link(c.e, "Transition", s1, s2);
      eq(2, c.d().edges.size(), "duas por direção");
      link(c.e, "Transition", s1, s2);
      eq(2, c.d().edges.size(), "terceira rejeitada");
      link(c.e, "Transition", s2, s1);
      eq(3, c.d().edges.size(), "direção oposta");
      link(c.e, "Transition", fin, s1);
      eq(3, c.d().edges.size(), "de estado final");
      link(c.e, "Transition", s1, ini);
      eq(3, c.d().edges.size(), "para estado inicial");
      for (int i = 0; i < 3; i++) {
        c.e.activateTool("Transition");
        drag(c.e, s1.x + 10, s1.y + 10, s1.x + 30, s1.y + 20);
      }
      eq(2L, c.d().edges.stream().filter(Edge::isSelfEdge).count(), "autotransições");
    });
  }

  private void objectTests() {
    test("referência e colaboração", () -> {
      Ctx c = new Ctx("Object");
      Node o1 = place(c.e, "Object", 100, 100), o2 = place(c.e, "Object", 400, 100);
      Node f = place(c.e, "Field", o1.x + 10, o1.y + 10);
      c.e.activateTool("Reference");
      drag(c.e, f.x + 5, f.y + 5, o2.x + 10, o2.y + 10);
      check(c.d().edges.isEmpty(), "parte esquerda do campo");
      drag(c.e, f.x + f.w - 8, f.y + 5, o2.x + 10, o2.y + 10);
      eq(List.of(EdgeKind.REFERENCE), kinds(c.d()), "referência");
      c.e.activateTool("Collaboration");
      drag(c.e, o1.x + 10, o1.y + 10, o2.x + 10, o2.y + 10);
      drag(c.e, o2.x + 10, o2.y + 10, o1.x + 10, o1.y + 10);
      drag(c.e, o1.x + 10, o1.y + 10, o1.x + 40, o1.y + 20);
      eq(1L, c.d().edges.stream().filter(x -> x.kind == EdgeKind.COLLABORATION).count(), "uma colaboração");
    });
  }

  private void useCaseTests() {
    test("regras e Dependency Type", () -> {
      Ctx c = new Ctx("Use Case");
      Node actor = place(c.e, "Actor", 50, 100), u1 = place(c.e, "Use Case", 300, 100), u2 = place(c.e, "Use Case", 300, 300);
      link(c.e, "Association", actor, u1);
      link(c.e, "Include", u1, u2);
      link(c.e, "Include", actor, u2);
      link(c.e, "Generalization", actor, u1);
      c.e.activateTool("Association");
      drag(c.e, u1.x + 20, u1.y + 20, u1.x + 40, u1.y + 25);
      eq(List.of(EdgeKind.ASSOCIATION, EdgeKind.USE_CASE_DEPENDENCY), kinds(c.d()), "tipos");
      Edge dep = c.d().edges.get(1);
      eq("Include", dep.get("dependency_type"), "include");
      c.e.applyProperties(dep, Map.of("dependency_type", "Extend"));
      eq("Extend", dep.get("dependency_type"), "extend");
    });
  }

  // ---------- sequência ----------
  private static final class Seq {
    final Ctx c = new Ctx("Sequence");
    final Node a = place(c.e, "Implicit Parameter", 50, 20), b = place(c.e, "Implicit Parameter", 250, 20), cc = place(c.e, "Implicit Parameter", 450, 20);
    void call(Node start, Node end) { call(start, end, 100); }
    void call(Node start, Node end, double y) {
      c.e.activateTool("Call");
      boolean sl = start.type().equals("ImplicitParameter"), el = end.type().equals("ImplicitParameter");
      drag(c.e, sl ? start.x + start.w / 2 : start.x + 8, sl ? 100 : start.y + 10, el ? end.x + end.w / 2 : end.x + 8, y);
    }
    List<Node> boxes() { return c.d().nodes.stream().filter(n -> n.type().equals("Call")).toList(); }
  }

  private void sequenceTests() {
    test("chamada inicial cria as duas caixas", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      eq(2, s.boxes().size(), "caixas"); eq(List.of(EdgeKind.CALL), kinds(s.c.d()), "chamada");
    });
    test("chamada adicional a partir da caixa", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      s.call(s.c.d().edges.get(0).end, s.cc);
      eq(3, s.boxes().size(), "caixas");
    });
    test("autochamada dentro da caixa", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      Node callee = s.c.d().edges.get(0).end;
      s.c.e.activateTool("Call");
      drag(s.c.e, callee.x + 8, callee.y + 10, callee.x + 8, callee.y + 20);
      eq(3, s.boxes().size(), "caixas");
      check(s.boxes().get(2).parent == s.b, "na mesma linha de vida");
    });
    test("chamada de construtor", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      Node first = s.c.d().edges.get(0).end;
      s.c.e.activateTool("Call");
      drag(s.c.e, first.x + 8, first.y + 10, s.cc.x + 30, s.cc.y + 10);
      Edge ctor = s.c.d().edges.get(1);
      eq("«create»", ctor.get("middle_label"), "rótulo");
      eq(ctor.rowY - 20, s.cc.y, "alinhado verticalmente");
      check(ctor.end.parent == s.cc, "caixa anexada ao parâmetro");
    });
    test("retorno e salto silencioso", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      Edge call = s.c.d().edges.get(0);
      s.c.e.activateTool("Return");
      drag(s.c.e, call.end.x + 8, call.end.y + 10, call.start.x + 8, call.start.y + 10);
      eq(List.of(EdgeKind.CALL, EdgeKind.RETURN), kinds(s.c.d()), "chamada e retorno");
      int errors = s.c.ws.notifier.errors().size();
      s.call(call.end, s.cc);
      eq(2, s.c.d().edges.size(), "sem nova aresta");
      eq(errors, s.c.ws.notifier.errors().size(), "sem notificação");
    });
    test("rejeições estruturais são notificadas", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      Node first = s.c.d().edges.get(0).start, second = s.c.d().edges.get(0).end;
      s.call(second, s.cc);
      Node third = s.c.d().edges.get(1).end;
      s.c.e.activateTool("Call");
      drag(s.c.e, first.x + 8, first.y + 10, third.x + 8, third.y + 10);
      eq(2, s.c.d().edges.size(), "dois chamadores");
      s.c.e.activateTool("Call");
      drag(s.c.e, s.a.x + 5, 100, 700, 300);
      drag(s.c.e, first.x + 8, first.y + 10, s.a.x + 40, s.a.y + 10);
      check(s.c.ws.notifier.errors().size() >= 2, "erros notificados");
    });
    test("uma única chamada raiz", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      s.c.e.activateTool("Call");
      drag(s.c.e, s.cc.x + 30, 100, s.cc.x + 30, 110);
      eq(2, s.boxes().size(), "caixas");
      check(!s.c.ws.notifier.errors().isEmpty(), "erro");
    });
    test("chamadas e retornos só entre caixas de ativação", () -> {
      Seq s = new Seq();
      rejects(() -> Rules.checkEdge(s.c.d(), EdgeKind.CALL, s.a, s.b), "chamada entre linhas de vida");
    });
    test("apagar chamada apaga em cascata", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      s.call(s.c.d().edges.get(0).end, s.cc);
      s.c.e.activateTool("Select");
      s.c.e.selection = new ArrayList<>(List.of(s.c.d().edges.get(0)));
      s.c.e.deleteSelected();
      check(s.c.d().edges.isEmpty() && s.boxes().isEmpty(), "tudo removido");
    });
    test("colar em diagrama de sequência é rejeitado", () -> {
      Seq s = new Seq();
      Document src = s.c.ws.newDocument("Class");
      place(src.editor, "Note", 100, 100);
      src.editor.copySelected();
      s.c.e.paste();
      eq(0L, count(s.c.d(), "Note"), "nota colada");
      check(!s.c.ws.notifier.errors().isEmpty(), "erro");
    });
    test("propriedade Open Bottom", () -> {
      Seq s = new Seq(); s.call(s.a, s.b);
      Node box = s.boxes().get(0);
      eq(List.of("open_bottom"), names(box.fields()), "campos");
      check(s.c.e.applyProperties(box, Map.of("open_bottom", Boolean.TRUE)), "aplicado");
    });
  }

  // ---------- seleção ----------
  private static final class Sel {
    final Ctx c = new Ctx("Class");
    final Node a = place(c.e, "Class", 100, 100), b = place(c.e, "Class", 400, 100), cNode = place(c.e, "Class", 700, 100);
    Sel() {
      c.e.activateTool("Association");
      drag(c.e, 110, 110, 410, 110);
      c.e.activateTool("Select");
      c.e.selection = new ArrayList<>();
    }
  }

  private void selectionTests() {
    test("clique e Ctrl+clique alternam", () -> {
      Sel s = new Sel();
      click(s.c.e, s.a.x + 5, s.a.y + 5);
      click(s.c.e, s.b.x + 5, s.b.y + 5, Mods.CTRL);
      eq(List.of(s.a, s.b), s.c.e.selection, "dois");
      click(s.c.e, s.a.x + 5, s.a.y + 5, Mods.CTRL);
      eq(List.of(s.b), s.c.e.selection, "um");
    });
    test("laço seleciona a região", () -> {
      Sel s = new Sel();
      drag(s.c.e, 50, 50, 600, 300);
      Set<Object> expected = new HashSet<>(List.of(s.a, s.b, s.c.d().edges.get(0)));
      eq(expected, new HashSet<>(s.c.e.selection), "laço");
    });
    test("Select All inclui arestas e move tudo", () -> {
      Sel s = new Sel();
      s.c.e.selectAll();
      eq(4, s.c.e.selection.size(), "seleção");
      List<Double> xs = s.c.d().nodes.stream().map(n -> n.x).toList();
      drag(s.c.e, s.a.x + 5, s.a.y + 5, s.a.x + 55, s.a.y + 5);
      for (int i = 0; i < xs.size(); i++) eq(xs.get(i) + 50, s.c.d().nodes.get(i).x, "nó " + i);
    });
    test("arrasto alinha à grade mesmo sem grade", () -> {
      Sel s = new Sel();
      s.c.ws.settings.showGrid = false;
      drag(s.c.e, s.a.x + 5, s.a.y + 5, s.a.x + 18, s.a.y + 13);
      eq(0.0, s.a.x % 10, "x"); eq(0.0, s.a.y % 10, "y");
    });
    test("excluir remove arestas incidentes", () -> {
      Sel s = new Sel();
      s.c.e.selection = new ArrayList<>(List.of(s.a));
      s.c.e.deleteSelected();
      check(s.c.d().edges.isEmpty(), "aresta removida");
      eq(2, s.c.d().nodes.size(), "nós");
      s.c.e.undo();
      eq(1, s.c.d().edges.size(), "desfeito");
    });
  }

  private void clipboardTests() {
    test("copiar, colar e recortar no mesmo diagrama", () -> {
      Ctx c = new Ctx("Class");
      Node a = place(c.e, "Class", 100, 100); place(c.e, "Class", 400, 100);
      c.e.activateTool("Association");
      drag(c.e, 110, 110, 410, 110);
      c.e.activateTool("Select");
      c.e.selectAll();
      check(c.e.copySelected(), "copiado");
      c.e.paste();
      eq(4, c.d().nodes.size(), "nós"); eq(2, c.d().edges.size(), "arestas");
      eq(a.x + 20, c.d().nodes.get(2).x, "deslocamento");
      c.e.selection = new ArrayList<>(c.d().nodes.subList(0, 2));
      c.e.cutSelected();
      eq(2, c.d().nodes.size(), "recortado");
      c.e.paste();
      eq(4, c.d().nodes.size(), "colado");
    });
    test("colar entre diagramas verifica compatibilidade", () -> {
      Workspace ws = new Workspace();
      Document src = ws.newDocument("Class"), other = ws.newDocument("State"), same = ws.newDocument("Class");
      place(src.editor, "Class", 100, 100);
      src.editor.copySelected();
      other.editor.paste();
      check(other.diagram().nodes.isEmpty(), "incompatível");
      check(!ws.notifier.errors().isEmpty(), "erro");
      same.editor.paste();
      eq(1, same.diagram().nodes.size(), "compatível");
      place(src.editor, "Note", 400, 100);
      src.editor.copySelected();
      other.editor.paste();
      eq("Note", other.diagram().nodes.get(0).type(), "nota vale em qualquer diagrama");
    });
    test("campo não pode ser colado sozinho", () -> {
      Ctx c = new Ctx("Object");
      Node obj = place(c.e, "Object", 100, 100);
      Node field = place(c.e, "Field", obj.x + 10, obj.y + 10);
      c.e.selection = new ArrayList<>(List.of(field));
      c.e.copySelected();
      c.e.paste();
      eq(2, c.d().nodes.size(), "nós");
      check(!c.ws.notifier.errors().isEmpty(), "erro");
    });
    test("copiar objeto leva seus campos", () -> {
      Ctx c = new Ctx("Object");
      Node obj = place(c.e, "Object", 100, 100);
      place(c.e, "Field", obj.x + 10, obj.y + 10);
      c.e.selection = new ArrayList<>(List.of(obj));
      c.e.copySelected();
      c.e.paste();
      eq(4, c.d().nodes.size(), "nós");
    });
  }

  private void regressionTests() {
    test("Dependency nasce tracejada", () -> {
      ClassEdges t = new ClassEdges();
      t.connect("Dependency", t.a, t.b);
      Edge edge = t.c.d().edges.get(0);
      eq("Implementation", edge.get("generalization_type"), "tipo");
      eq("Unidirectional", edge.get("directionality"), "direcionalidade");
    });
    test("colar filho sem o pacote não trava", () -> {
      Ctx c = new Ctx("Class");
      Node pkg = place(c.e, "Package", 100, 100);
      Node child = place(c.e, "Class", pkg.x + 20, pkg.y + 30);
      c.e.selection = new ArrayList<>(List.of(child));
      c.e.copySelected();
      c.e.paste();
      check(c.d().nodes.get(c.d().nodes.size() - 1).parent == null, "vira raiz");
      eq(3, c.d().nodes.size(), "nós");
    });
  }

  private void propertyTests() {
    test("campos por tipo de elemento", () -> {
      Ctx c = new Ctx("Class");
      Map<String, List<String>> expected = new LinkedHashMap<>();
      expected.put("Class", List.of("name", "attributes", "methods")); expected.put("Interface", List.of("name", "methods"));
      expected.put("Package", List.of("name")); expected.put("Package Description", List.of("name", "contents")); expected.put("Note", List.of("text"));
      int i = 0;
      for (var entry : expected.entrySet()) {
        Node node = place(c.e, entry.getKey(), 20 + i++ * 180, 100);
        eq(entry.getValue(), names(node.fields()), entry.getKey());
      }
      eq(List.of("name"), names(Catalog.tool("Object", "Object").createNode(0, 0).fields()), "Object");
      eq(List.of("name", "value"), names(Catalog.tool("Object", "Field").createNode(0, 0).fields()), "Field");
      eq(List.of("name"), names(Catalog.tool("Use Case", "Actor").createNode(0, 0).fields()), "Actor");
      eq(List.of("name"), names(Catalog.tool("Use Case", "Use Case").createNode(0, 0).fields()), "Use Case");
      eq(List.of("name"), names(Catalog.tool("State", "State").createNode(0, 0).fields()), "State");
      eq(List.of("name"), names(Catalog.tool("Sequence", "Implicit Parameter").createNode(0, 0).fields()), "Implicit Parameter");
      check(Catalog.tool("State", "Initial State").createNode(0, 0).fields().isEmpty(), "estado inicial sem campos");
    });
    test("aplicar propriedades e desfazer", () -> {
      Ctx c = new Ctx("Class");
      Node node = place(c.e, "Class", 100, 100);
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("name", "Cliente"); values.put("attributes", "nome\nidade"); values.put("methods", "/abstrato()/");
      c.e.applyProperties(node, values);
      eq("Cliente", node.get("name"), "nome");
      check(node.h > 60, "altura cresceu");
      c.e.undo();
      eq("Class", c.d().nodes.get(0).get("name"), "desfeito");
    });
    test("duplo clique e Ctrl+Enter pedem o diálogo", () -> {
      Ctx c = new Ctx("Class");
      Node node = place(c.e, "Class", 100, 100);
      List<Object> asked = new ArrayList<>();
      c.e.onEditRequested = asked::add;
      c.e.activateTool("Select");
      c.e.doubleClick(node.x + 5, node.y + 5);
      c.e.requestProperties();
      eq(List.of(node, node), asked, "pedidos");
    });
  }

  // ---------- arquivos ----------
  private void persistenceTests() {
    test("ida e volta dos cinco tipos de diagrama", () -> {
      Workspace ws = new Workspace();
      Path folder = Files.createTempDirectory("jetuml");
      for (String kind : Diagram.KINDS) {
        Document doc = ws.newDocument(kind);
        Editor e = doc.editor;
        switch (kind) {
          case "Class" -> {
            Node a = place(e, "Class", 100, 100); place(e, "Class", 400, 100);
            e.applyProperties(a, Map.of("name", "A \"q\" \\ ç", "attributes", "x: int"));
            e.activateTool("Association");
            drag(e, 110, 110, 410, 110);
          }
          case "Sequence" -> {
            Node la = place(e, "Implicit Parameter", 50, 20), lb = place(e, "Implicit Parameter", 250, 20);
            e.activateTool("Call");
            drag(e, la.x + 40, 100, lb.x + 40, 100);
          }
          case "Object" -> {
            Node o = place(e, "Object", 100, 100);
            place(e, "Field", o.x + 10, o.y + 10);
          }
          default -> { }
        }
        Path path = folder.resolve(kind.replace(" ", "") + ".json");
        ws.save(doc, path);
        check(!doc.dirty(), "salvo");
        Document reopened = ws.open(path);
        eq(DiagramSerializer.dumps(doc.diagram()), DiagramSerializer.dumps(reopened.diagram()), kind);
      }
      eq(5, ws.settings.recentFiles.size(), "recentes");
    });
    test("arquivos inválidos são recusados", () -> {
      Ctx c = new Ctx("Object");
      Node o = place(c.e, "Object", 100, 100);
      place(c.e, "Field", o.x + 10, o.y + 10);
      String good = DiagramSerializer.dumps(c.d());
      Map<String, Object> orphan = copy(good), dangling = copy(good), wrongType = copy(good), unknown = copy(good);
      node(orphan, 1).put("parent", null);
      list(dangling, "edges").add(new LinkedHashMap<>(Map.of("id", 9, "kind", "Reference", "start", 99, "end", 1)));
      node(wrongType, 0).put("type", "Class");
      unknown.put("schema", "x");
      for (Map<String, Object> bad : List.of(orphan, dangling, wrongType, unknown, new LinkedHashMap<String, Object>())) {
        rejects(() -> DiagramSerializer.loads(Json.write(bad)), "arquivo inválido " + bad.keySet());
      }
      rejects(() -> DiagramSerializer.loads("[]"), "lista");
      rejects(() -> DiagramSerializer.loads("{nope"), "json quebrado");
    });
    test("aceita arquivo no formato escrito pela versão Python", () -> {
      String python = "{\"schema\": \"jetuml-sketch-v2\", \"diagram\": \"Class\", \"nodes\": ["
          + "{\"id\": 1, \"type\": \"Class\", \"x\": 100, \"y\": 100.0, \"parent\": null, \"properties\": {\"name\": \"A\", \"attributes\": \"\", \"methods\": \"\"}},"
          + "{\"id\": 2, \"type\": \"Class\", \"x\": 400, \"y\": 100, \"parent\": null, \"properties\": {\"name\": \"B\", \"attributes\": \"\", \"methods\": \"\"}}],"
          + "\"edges\": [{\"id\": 3, \"kind\": \"Association\", \"start\": 1, \"end\": 2, \"properties\": {\"start_label\": \"\", \"middle_label\": \"tem\", \"end_label\": \"\", \"directionality\": \"Unspecified\"}}]}";
      Diagram d = DiagramSerializer.loads(python);
      eq(2, d.nodes.size(), "nós"); eq("tem", d.edges.get(0).get("middle_label"), "rótulo");
    });
    test("duplicar e fechar abas", () -> {
      Workspace ws = new Workspace();
      Document doc = ws.newDocument("Class");
      place(doc.editor, "Class", 100, 100);
      Document copy = ws.duplicate(doc);
      check(copy.diagram() != doc.diagram(), "diagramas distintos");
      eq(1, copy.diagram().nodes.size(), "nós"); eq(2, ws.documents.size(), "abas");
      ws.close(copy);
      check(ws.active == doc, "ativa");
      ws.close(doc);
      check(ws.active == null, "sem abas");
    });
    test("abrir arquivo grande aumenta a área", () -> {
      Workspace ws = new Workspace();
      Document doc = ws.newDocument("Class");
      place(doc.editor, "Class", 1500, 1200);
      Path path = Files.createTempDirectory("jetuml").resolve("big.json");
      ws.save(doc, path);
      check(ws.open(path).diagram().width > 1500, "largura aumentou");
    });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> copy(String json) { return (Map<String, Object>) Json.parse(json); }
  @SuppressWarnings("unchecked")
  private static List<Object> list(Map<String, Object> m, String key) { return (List<Object>) m.get(key); }
  @SuppressWarnings("unchecked")
  private static Map<String, Object> node(Map<String, Object> m, int i) { return (Map<String, Object>) list(m, "nodes").get(i); }

  private void exportTests() {
    test("exporta SVG, PNG e JPEG", () -> {
      Workspace ws = new Workspace();
      Document doc = ws.newDocument("Class");
      place(doc.editor, "Class", 100, 100); place(doc.editor, "Interface", 400, 100);
      doc.editor.activateTool("Realization");
      drag(doc.editor, 110, 110, 410, 110);
      Path folder = Files.createTempDirectory("jetuml");
      ws.exportImage(doc, folder.resolve("d.svg"));
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(folder.resolve("d.svg").toFile());
      for (String name : List.of("d.png", "d.jpg")) {
        ws.exportImage(doc, folder.resolve(name));
        check(Files.size(folder.resolve(name)) > 100, name);
        check(javax.imageio.ImageIO.read(folder.resolve(name).toFile()) != null, name + " legível");
      }
      rejects(() -> { try { ws.exportImage(doc, folder.resolve("d.gif")); } catch (IOException ex) { throw new IllegalStateException(ex); } }, "gif");
    });
    test("exportação recortada e sem grade", () -> {
      Ctx c = new Ctx("Class");
      place(c.e, "Class", 300, 300);
      String svg = SvgRenderer.render(c.d(), c.d().contentBounds().inflated(10), Palette.LIGHT, false);
      check(!svg.contains("#e6e6e6"), "sem grade");
      var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      check(Integer.parseInt(doc.getDocumentElement().getAttribute("width")) < 400, "largura recortada");
    });
    test("diagrama vazio exporta", () -> {
      Ctx c = new Ctx("State");
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
          SvgRenderer.render(c.d(), null, Palette.LIGHT, false).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      check(jetuml.render.ImageExporter.render(c.d()).getWidth() > 0, "imagem vazia");
    });
  }

  private void viewTests() {
    test("zoom com dois níveis para cada lado", () -> {
      Ctx c = new Ctx("Class");
      for (int i = 0; i < 5; i++) c.e.zoomIn();
      eq(2, c.e.zoomLevel, "máximo");
      for (int i = 0; i < 9; i++) c.e.zoomOut();
      eq(-2, c.e.zoomLevel, "mínimo");
      c.e.resetZoom();
      eq(1.0, c.e.zoomFactor(), "reset");
    });
    test("tamanho do diagrama: validação e padrão relativo à tela", () -> {
      Workspace ws = new Workspace(new Settings(), 1920, 1080);
      eq(1920, ws.newDocument("Class").diagram().width, "largura");
      rejects(() -> ws.setDiagramSize(100, 500), "largura baixa");
      rejects(() -> ws.setDiagramSize(500, 5000), "altura alta");
      ws.setDiagramSize(250, 4000);
      eq(4000, ws.newDocument("State").diagram().height, "altura configurada");
      ws.restoreDiagramSize();
      eq(1080, ws.newDocument("State").diagram().height, "padrão");
      eq(250, Settings.defaultDiagramSize(100, 9000)[0], "mínimo"); eq(4000, Settings.defaultDiagramSize(100, 9000)[1], "máximo");
    });
    test("fonte altera o layout e restaura", () -> {
      Ctx c = new Ctx("Class");
      Node node = place(c.e, "Class", 100, 100);
      double base = node.h;
      c.ws.setFont("Courier", 20);
      check(node.h >= base, "altura");
      rejects(() -> c.ws.setFont("Courier", 2), "fonte pequena");
      c.ws.restoreFont();
      eq(base, node.h, "restaurado");
    });
    test("notificações e duração", () -> {
      Ctx c = new Ctx("Class");
      List<Notification> seen = new ArrayList<>();
      c.ws.notifier.listeners.add(seen::add);
      c.ws.notifier.error("x"); c.ws.notifier.info("y");
      eq(List.of("error", "info"), seen.stream().map(Notification::level).toList(), "níveis");
      rejects(() -> c.ws.settings.setNotificationDuration(11), "duração 11");
      c.ws.settings.setNotificationDuration(0);
      check(c.ws.notifier.error("silenciada") == null, "0 desativa");
      eq(2, seen.size(), "sem novas");
      c.ws.settings.restoreNotificationDuration();
      eq(5, c.ws.settings.notificationDuration, "padrão");
    });
    test("preferências persistem e recentes ficam limitados", () -> {
      Path file = Files.createTempDirectory("jetuml").resolve("s.json");
      Settings s = new Settings();
      s.darkMode = true; s.setDiagramSize(800, 600);
      for (int i = 0; i < 12; i++) s.addRecent("/f" + i + ".json");
      s.save(file);
      Settings loaded = Settings.load(file);
      check(loaded.darkMode, "modo escuro");
      eq(800, loaded.diagramSize[0], "largura"); eq(10, loaded.recentFiles.size(), "recentes"); eq("/f11.json", loaded.recentFiles.get(0), "mais recente");
      Files.writeString(file, "lixo");
      check(!Settings.load(file).darkMode, "arquivo inválido → padrão");
    });
  }
}
