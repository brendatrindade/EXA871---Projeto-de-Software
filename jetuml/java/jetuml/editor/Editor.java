package jetuml.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jetuml.app.Notifier;
import jetuml.app.Settings;
import jetuml.catalog.Catalog;
import jetuml.catalog.Tool;
import jetuml.geometry.Point;
import jetuml.geometry.Rect;
import jetuml.model.*;
import jetuml.persistence.DiagramSerializer;
import jetuml.persistence.Json;
import jetuml.rules.Rules;
import static jetuml.geometry.Geometry.normalizedRect;
import static jetuml.geometry.Geometry.snap;

/**
 * Controlador de edição de um diagrama: ferramentas, seleção, arrasto, clipboard, desfazer e propriedades.
 * Recebe eventos abstratos (coordenadas do modelo + modificadores); a janela só os traduz.
 */
public final class Editor {
  public static final int ZOOM_STEPS = 2, PASTE_OFFSET = 20;
  private static final double CLICK_THRESHOLD = 5;

  public record Rubber(Point from, Point to) { }

  private static final class Drag {
    Point last;
    final String before;
    boolean moved;
    Drag(Point last, String before) { this.last = last; this.before = before; }
  }

  public final Diagram diagram;
  public final History history = new History();
  public final List<Runnable> listeners = new ArrayList<>();
  public List<Object> selection = new ArrayList<>();
  public Rubber rubber;
  public Rect lasso;
  public int zoomLevel;
  public Consumer<Object> onEditRequested;

  private final Notifier notifier;
  private final Clipboard clipboard;
  private final Settings settings;
  private Tool tool;
  private Node edgeStart;
  private Point edgeOrigin, lassoOrigin;
  private Drag drag;
  private Object pressHit;

  public Editor(Diagram diagram, Notifier notifier, Clipboard clipboard, Settings settings) {
    this.diagram = diagram;
    this.notifier = notifier;
    this.clipboard = clipboard;
    this.settings = settings;
    this.tool = Catalog.tool(diagram.kind, "Select");
  }

  // ---------- infraestrutura ----------
  public void changed() { for (Runnable l : new ArrayList<>(listeners)) l.run(); }

  /** Executa uma edição como uma única entrada de histórico; erros viram notificações. */
  private Object attempt(Supplier<Object> action) {
    String before = DiagramSerializer.dumps(diagram);
    Object result;
    try {
      result = action.get();
    } catch (ValidationException ex) {
      if (!ex.silent()) notifier.error(ex.getMessage());
      changed();
      return null;
    }
    diagram.relayout();
    if (!DiagramSerializer.dumps(diagram).equals(before)) history.push(before);
    changed();
    return result != null ? result : Boolean.TRUE;
  }

  // ---------- ferramentas (RF07) ----------
  public Tool tool() { return tool; }

  /** Retorna "clipboard" para To Clipboard (tratada pela janela) ou "ok". */
  public String activateTool(String name) {
    Tool t = Catalog.tool(diagram.kind, name);
    if (t.category().equals("clipboard")) return "clipboard";
    tool = t;
    cancelGesture();
    return "ok";
  }

  public String activateShortcut(String key) {
    Tool t = Catalog.toolForShortcut(diagram.kind, key);
    return t == null ? null : activateTool(t.name());
  }

  private void cancelGesture() { rubber = null; lasso = null; drag = null; edgeStart = null; }

  // ---------- zoom (RF37) ----------
  public double zoomFactor() { return Math.pow(1.25, zoomLevel); }
  public void zoomIn() { zoomLevel = Math.min(ZOOM_STEPS, zoomLevel + 1); changed(); }
  public void zoomOut() { zoomLevel = Math.max(-ZOOM_STEPS, zoomLevel - 1); changed(); }
  public void resetZoom() { zoomLevel = 0; changed(); }

  // ---------- eventos do mouse ----------
  public void press(double x, double y, Mods mods) {
    switch (tool.category()) {
      case "select" -> pressSelect(x, y, mods);
      case "node" -> createNode(x, y);
      case "edge" -> beginEdge(x, y);
      default -> { }
    }
  }

  public void drag(double x, double y, Mods mods) {
    if (lassoOrigin != null) lasso = normalizedRect(lassoOrigin, new Point(x, y));
    else if (edgeStart != null) rubber = new Rubber(edgeOrigin, new Point(x, y));
    else if (drag != null) moveSelection(x, y);
    changed();
  }

  public void release(double x, double y, Mods mods) {
    if (edgeStart != null) finishEdge(x, y);
    else if (lassoOrigin != null) finishLasso();
    else if (drag != null) finishDrag(mods);
    cancelGesture();
    lassoOrigin = null;
    pressHit = null;
    changed();
  }

  public void doubleClick(double x, double y) {
    Object hit = diagram.nodeAt(x, y);
    if (hit == null) hit = diagram.edgeAt(x, y);
    if (hit != null) {
      selection = new ArrayList<>(List.of(hit));
      requestProperties();
    }
  }

  // ---------- seleção (RF32, RF33) ----------
  private void pressSelect(double x, double y, Mods mods) {
    Object hit = diagram.nodeAt(x, y);
    if (hit == null) hit = diagram.edgeAt(x, y);
    pressHit = hit;
    if (hit == null) {
      if (!mods.ctrl()) selection = new ArrayList<>();
      lassoOrigin = new Point(x, y);
      lasso = new Rect(x, y, 0, 0);
    } else if (mods.ctrl()) {
      if (selection.contains(hit)) selection.remove(hit);
      else selection.add(hit);
    } else {
      if (!selection.contains(hit)) selection = new ArrayList<>(List.of(hit));
      boolean movable = selection.stream().anyMatch(s -> s instanceof Node n && n.movable());
      if (movable) drag = new Drag(new Point(x, y), DiagramSerializer.dumps(diagram));
    }
    changed();
  }

  private void finishLasso() {
    Rect region = lasso;
    List<Node> nodes = new ArrayList<>();
    for (Node n : diagram.nodes) if (n.selectable() && region.containsRect(n.bounds())) nodes.add(n);
    List<Object> picked = new ArrayList<>(nodes);
    for (Edge e : diagram.edges) if (inside(e.start, nodes) && inside(e.end, nodes)) picked.add(e);
    if (!picked.isEmpty()) {
      Set<Object> merged = new LinkedHashSet<>(selection);
      merged.addAll(picked);
      selection = new ArrayList<>(merged);
    }
  }

  private static boolean inside(Node n, List<Node> nodes) { return nodes.contains(n) || n instanceof PointNode; }

  public void selectAll() {
    List<Object> all = new ArrayList<>();
    for (Node n : diagram.nodes) if (n.selectable()) all.add(n);
    all.addAll(diagram.edges);
    selection = all;
    changed();
  }

  private List<Node> topLevelMovers() {
    List<Node> chosen = new ArrayList<>();
    for (Object s : selection) if (s instanceof Node n && n.movable()) chosen.add(n);
    List<Node> movers = new ArrayList<>();
    for (Node n : chosen) if (n.ancestors().stream().noneMatch(chosen::contains)) movers.add(n);
    return movers;
  }

  private void moveSelection(double x, double y) {
    double dx = x - drag.last.x(), dy = y - drag.last.y();
    drag.last = new Point(x, y);
    if (Math.abs(dx) + Math.abs(dy) > 0) drag.moved = true;
    for (Node node : topLevelMovers()) diagram.moveNode(node, dx, node instanceof ImplicitParameterNode ? 0 : dy);
    diagram.relayout();
  }

  private void finishDrag(Mods mods) {
    if (!drag.moved) {
      if (pressHit != null && !mods.ctrl()) selection = new ArrayList<>(List.of(pressHit));
      return;
    }
    for (Node node : topLevelMovers()) {
      double dy = node instanceof ImplicitParameterNode ? 0 : Math.max(0, snap(node.y)) - node.y;
      diagram.moveNode(node, Math.max(0, snap(node.x)) - node.x, dy);
      if (mods.shift()) togglePackageMembership(node);
    }
    diagram.relayout();
    if (!DiagramSerializer.dumps(diagram).equals(drag.before)) history.push(drag.before);
  }

  /** Shift ao soltar: desanexa do pacote atual ou anexa ao pacote sob o nó (RF15). */
  private void togglePackageMembership(Node node) {
    if (node.parent != null && node.parent.type().equals("Package")) {
      diagram.detach(node);
      return;
    }
    Point c = node.bounds().center();
    Node container = diagram.containerAt(c.x(), c.y(), node, node);
    if (container != null) diagram.attach(node, container);
  }

  // ---------- criação de nós (RF11–RF13, RF15, RF25) ----------
  private void createNode(double x, double y) {
    Object created = attempt(() -> {
      Node node = tool.createNode(Math.max(0, snap(x)), Math.max(0, snap(y)));
      Node parent = diagram.containerAt(x, y, node, null);
      if (node instanceof FieldNode && parent == null) {
        throw new ValidationException("Um campo deve pertencer a um objeto: clique dentro de um objeto.");
      }
      if (parent == null && diagram.nodeAt(x, y) != null) throw new ValidationException("Clique em um espaço vazio para criar o elemento.");
      diagram.addNode(node, parent);
      selection = new ArrayList<>(List.of(node));
      return node;
    });
    if (created instanceof Node node && settings.autoEdit && !node.fields().isEmpty()) requestProperties();
  }

  // ---------- criação de arestas (RF14, RF16, RF19, RF26–RF30) ----------
  private void beginEdge(double x, double y) {
    Node start = diagram.nodeAt(x, y);
    if (start == null) return;
    if (tool.edgeKind() == EdgeKind.REFERENCE && !(start instanceof FieldNode && x >= start.x + start.w / 2)) {
      notifier.error("Referências partem da parte Value de um campo.");
      return;
    }
    edgeStart = start;
    edgeOrigin = new Point(x, y);
    rubber = new Rubber(edgeOrigin, edgeOrigin);
  }

  private void finishEdge(double x, double y) {
    Node start = edgeStart;
    Node end = diagram.nodeAt(x, y);
    boolean moved = Math.abs(x - edgeOrigin.x()) + Math.abs(y - edgeOrigin.y()) >= CLICK_THRESHOLD;
    if (moved) connect(start, end, new Point(x, y));
  }

  private void connect(Node start, Node end, Point point) {
    Tool t = tool;
    attempt(() -> {
      EdgeKind kind = t.edgeKind();
      Edge edge;
      if (kind == EdgeKind.CALL) {
        edge = SequenceSupport.connectCall(diagram, start, end, point.y());
      } else if (kind == EdgeKind.RETURN) {
        edge = SequenceSupport.connectReturn(diagram, start, end);
      } else {
        Node target = end;
        if (target == null) {
          if (kind == EdgeKind.NOTE_CONNECTOR && start instanceof NoteNode) target = new PointNode(snap(point.x()), snap(point.y()));
          else throw new ValidationException("A aresta deve terminar sobre um elemento.");
        }
        Rules.checkEdge(diagram, kind, start, target);
        if (target instanceof PointNode && !diagram.nodes.contains(target)) diagram.addNode(target);
        edge = new Edge(kind, start, target);
        edge.props.putAll(t.preset());
        diagram.addEdge(edge);
      }
      selection = new ArrayList<>(List.of(edge));
      return null;
    });
  }

  // ---------- exclusão (RF35) ----------
  public void deleteSelected() {
    if (!selection.isEmpty()) attempt(this::deleteCurrentSelection);
  }

  private Object deleteCurrentSelection() {
    Set<Node> nodes = new LinkedHashSet<>();
    Set<Edge> edges = new LinkedHashSet<>();
    for (Object s : selection) {
      if (s instanceof Node n) nodes.add(n);
      else if (s instanceof Edge e) edges.add(e);
    }
    if (diagram.kind.equals("Sequence")) {
      SequenceSupport.Deletion all = SequenceSupport.expandDeletion(diagram, nodes, edges);
      nodes = all.nodes();
      edges = all.edges();
    }
    for (Edge e : edges) diagram.removeEdge(e);
    for (Node n : nodes) if (diagram.nodes.contains(n)) diagram.removeNode(n);
    if (diagram.kind.equals("Sequence")) SequenceSupport.pruneIsolated(diagram);
    selection = new ArrayList<>();
    return null;
  }

  // ---------- recortar, copiar, colar (RF34) ----------
  public boolean copySelected() {
    Set<Node> nodes = new LinkedHashSet<>();
    Set<Edge> chosen = new LinkedHashSet<>();
    for (Object s : selection) {
      if (s instanceof Node n) {
        nodes.add(n);
        nodes.addAll(n.descendants());
      } else if (s instanceof Edge e) {
        chosen.add(e);
      }
    }
    for (Edge e : chosen) {
      if (e.start instanceof PointNode) nodes.add(e.start);
      if (e.end instanceof PointNode) nodes.add(e.end);
    }
    if (nodes.isEmpty()) return false;
    List<Map<String, Object>> nodeDicts = new ArrayList<>();
    for (Node n : diagram.nodes) if (nodes.contains(n)) nodeDicts.add(nodeDict(n, nodes));
    List<Map<String, Object>> edgeDicts = new ArrayList<>();
    for (Edge e : diagram.edges) {
      if (!nodes.contains(e.start) || !nodes.contains(e.end)) continue;
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("kind", e.kind.value());
      m.put("start", e.start.id);
      m.put("end", e.end.id);
      m.put("properties", new LinkedHashMap<>(e.props));
      edgeDicts.add(m);
    }
    clipboard.data = new Clipboard.ClipData(diagram.kind, nodeDicts, edgeDicts);
    clipboard.pastes = 0;
    notifier.info(nodeDicts.size() + " elemento(s) copiado(s).");
    return true;
  }

  /** Um filho copiado sem o pai vira raiz; campos mantêm o pai para a colagem ser recusada (RF25). */
  private static Map<String, Object> nodeDict(Node n, Set<Node> copied) {
    boolean keepsParent = n.parent != null && (copied.contains(n.parent) || n instanceof FieldNode);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.id);
    m.put("type", n.type());
    m.put("x", n.x);
    m.put("y", n.y);
    m.put("parent", keepsParent ? n.parent.id : null);
    m.put("properties", new LinkedHashMap<>(n.props));
    return m;
  }

  public void cutSelected() {
    if (copySelected()) deleteSelected();
  }

  public void paste() {
    Clipboard.ClipData data = clipboard.data;
    if (data != null) attempt(() -> pasteData(data));
  }

  @SuppressWarnings("unchecked")
  private Object pasteData(Clipboard.ClipData data) {
    if (diagram.kind.equals("Sequence")) {
      throw new ValidationException("Colar não é permitido em diagramas de sequência (geraria fluxo de controle inválido).");
    }
    Set<Object> nodeIds = new HashSet<>();
    for (Map<String, Object> n : data.nodes()) nodeIds.add(n.get("id"));
    Set<String> types = Catalog.allowedNodeTypes(diagram.kind);
    Set<EdgeKind> kinds = Catalog.allowedEdgeKinds(diagram.kind);
    boolean incompatible = data.nodes().stream().anyMatch(n -> !types.contains(n.get("type")))
        || data.edges().stream().anyMatch(e -> !kinds.contains(EdgeKind.fromValue((String) e.get("kind"))));
    if (incompatible) {
      throw new ValidationException("Elementos copiados de um diagrama " + data.kind() + " são incompatíveis com este diagrama " + diagram.kind + ".");
    }
    if (data.nodes().stream().anyMatch(n -> n.get("type").equals("Field") && !nodeIds.contains(n.get("parent")))) {
      throw new ValidationException("Campos só podem ser colados junto com o objeto a que pertencem.");
    }
    clipboard.pastes++;
    int offset = PASTE_OFFSET * clipboard.pastes;
    Map<Object, Node> created = new HashMap<>();
    List<Map<String, Object>> pending = new ArrayList<>(data.nodes());
    while (!pending.isEmpty()) {
      boolean progress = false;
      for (Map<String, Object> entry : new ArrayList<>(pending)) {
        Object parentId = entry.get("parent");
        if (parentId != null && !created.containsKey(parentId)) continue;
        Node node = NodeTypes.create((String) entry.get("type"), ((Number) entry.get("x")).doubleValue() + offset,
            ((Number) entry.get("y")).doubleValue() + offset);
        node.props.putAll((Map<String, Object>) entry.get("properties"));
        diagram.addNode(node, parentId == null ? null : created.get(parentId));
        created.put(entry.get("id"), node);
        pending.remove(entry);
        progress = true;
      }
      if (!progress) throw new ValidationException("Dados copiados inconsistentes.");
    }
    List<Edge> edges = new ArrayList<>();
    for (Map<String, Object> entry : data.edges()) {
      EdgeKind kind = EdgeKind.fromValue((String) entry.get("kind"));
      Node start = created.get(entry.get("start")), end = created.get(entry.get("end"));
      Rules.checkEdge(diagram, kind, start, end);
      Edge edge = new Edge(kind, start, end);
      edge.props.putAll((Map<String, Object>) entry.get("properties"));
      edges.add(diagram.addEdge(edge));
    }
    List<Object> pasted = new ArrayList<>();
    for (Node n : created.values()) if (n.selectable()) pasted.add(n);
    pasted.addAll(edges);
    selection = pasted;
    return null;
  }

  // ---------- desfazer/refazer (RF21) ----------
  public void undo() { travel(true); }
  public void redo() { travel(false); }

  @SuppressWarnings("unchecked")
  private void travel(boolean back) {
    String current = DiagramSerializer.dumps(diagram);
    String snapshot = back ? history.undo(current) : history.redo(current);
    if (snapshot == null) return;
    diagram.adopt(DiagramSerializer.fromMap((Map<String, Object>) Json.parse(snapshot), false));
    diagram.relayout();
    selection = new ArrayList<>();
    changed();
  }

  // ---------- propriedades (RF20, RF22–RF23) ----------
  public void requestProperties() {
    if (selection.size() == 1 && !fieldsFor(selection.get(0)).isEmpty() && onEditRequested != null) onEditRequested.accept(selection.get(0));
  }

  public static List<Field> fieldsFor(Object element) {
    return element instanceof Edge e ? e.fields() : ((Node) element).fields();
  }

  public Map<String, Object> valuesFor(Object element) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (Field f : fieldsFor(element)) values.put(f.name(), element instanceof Edge e ? e.get(f.name()) : ((Node) element).get(f.name()));
    return values;
  }

  public boolean applyProperties(Object element, Map<String, Object> values) {
    return attempt(() -> {
      EdgeKind oldKind = element instanceof Edge e ? e.kind : null;
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        if (element instanceof Edge edge) {
          edge.set(entry.getKey(), entry.getValue());
          if (edge.kind != oldKind) {
            try {
              Rules.checkEdge(diagram, edge.kind, edge.start, edge.end, edge);
            } catch (ValidationException ex) {
              edge.kind = oldKind;
              throw ex;
            }
            oldKind = edge.kind;
          }
        } else {
          ((Node) element).set(entry.getKey(), entry.getValue());
        }
      }
      return null;
    }) != null;
  }
}
