package jetuml.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jetuml.geometry.Point;
import jetuml.geometry.Rect;

/** Modelo de um diagrama: nós (lista plana com vínculo pai/filho), arestas, layout e consultas. */
public final class Diagram {
  public static final List<String> KINDS = List.of("Class", "Sequence", "State", "Object", "Use Case");

  public String kind;
  public int width = 900, height = 650;
  public final List<Node> nodes = new ArrayList<>();
  public final List<Edge> edges = new ArrayList<>();
  private int nextId = 1;

  public Diagram(String kind) {
    if (!KINDS.contains(kind)) throw new IllegalArgumentException("Tipo de diagrama desconhecido: " + kind);
    this.kind = kind;
  }

  // ---------- construção ----------
  public Node addNode(Node node, Node parent) {
    node.id = nextId++;
    nodes.add(node);
    if (parent != null) {
      node.parent = parent;
      parent.children.add(node);
    }
    relayout();
    return node;
  }

  public Node addNode(Node node) { return addNode(node, null); }

  public Edge addEdge(Edge edge) {
    edge.id = nextId++;
    edges.add(edge);
    relayout();
    return edge;
  }

  public void removeEdge(Edge edge) {
    edges.remove(edge);
    dropOrphanPoints();
  }

  public void removeNode(Node node) {
    List<Node> doomed = new ArrayList<>(List.of(node));
    doomed.addAll(node.descendants());
    if (node.parent != null) node.parent.children.remove(node);
    nodes.removeAll(doomed);
    edges.removeIf(e -> doomed.contains(e.start) || doomed.contains(e.end));
    dropOrphanPoints();
  }

  private void dropOrphanPoints() {
    Set<Node> used = new HashSet<>();
    for (Edge e : edges) {
      used.add(e.start);
      used.add(e.end);
    }
    nodes.removeIf(n -> n instanceof PointNode && !used.contains(n));
  }

  public List<Edge> edgesOf(Node node) {
    List<Edge> result = new ArrayList<>();
    for (Edge e : edges) if (e.start == node || e.end == node) result.add(e);
    return result;
  }

  // ---------- consultas ----------
  public List<Node> roots() {
    List<Node> roots = new ArrayList<>();
    for (Node n : nodes) if (n.parent == null) roots.add(n);
    return roots;
  }

  /** Pais antes de filhos; filhos desenhados por cima. */
  public List<Node> drawOrder() {
    List<Node> ordered = new ArrayList<>();
    for (Node root : roots()) visit(root, ordered);
    return ordered;
  }

  private static void visit(Node node, List<Node> out) {
    out.add(node);
    for (Node child : node.children) visit(child, out);
  }

  public Node nodeAt(double x, double y) {
    List<Node> order = drawOrder();
    for (int i = order.size() - 1; i >= 0; i--) {
      Node n = order.get(i);
      if (n.selectable() && n.bounds().contains(x, y)) return n;
    }
    return null;
  }

  /** Nó mais interno sob (x, y) que aceita {@code candidate} como filho. */
  public Node containerAt(double x, double y, Node candidate, Node exclude) {
    Set<Node> banned = new HashSet<>();
    if (exclude != null) {
      banned.add(exclude);
      banned.addAll(exclude.descendants());
    }
    List<Node> order = drawOrder();
    for (int i = order.size() - 1; i >= 0; i--) {
      Node n = order.get(i);
      if (!banned.contains(n) && n.accepts(candidate) && n.bounds().contains(x, y)) return n;
    }
    return null;
  }

  public Edge edgeAt(double x, double y, double tolerance) {
    for (int i = edges.size() - 1; i >= 0; i--) {
      Edge e = edges.get(i);
      if (EdgePaths.pathDistance(x, y, EdgePaths.edgePath(this, e).points()) <= tolerance) return e;
    }
    return null;
  }

  public Edge edgeAt(double x, double y) { return edgeAt(x, y, 5); }

  public Rect contentBounds() {
    Rect result = null;
    List<Rect> rects = new ArrayList<>();
    for (Node n : nodes) if (n.selectable() || n.parent == null) rects.add(n.bounds());
    for (Edge e : edges) {
      double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
      for (Point p : EdgePaths.edgePath(this, e).points()) {
        minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
        minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
      }
      rects.add(new Rect(minX, minY, maxX - minX, maxY - minY));
    }
    for (Rect r : rects) result = result == null ? r : result.union(r);
    return result;
  }

  // ---------- edição estrutural ----------
  public void moveNode(Node node, double dx, double dy) {
    List<Node> all = new ArrayList<>(List.of(node));
    all.addAll(node.descendants());
    for (Node n : all) {
      n.x += dx;
      n.y += dy;
    }
  }

  public void detach(Node node) {
    if (node.parent != null) {
      node.parent.children.remove(node);
      node.parent = null;
    }
  }

  public void attach(Node node, Node parent) {
    detach(node);
    node.parent = parent;
    parent.children.add(node);
  }

  /** Tamanhos, posições de campos, crescimento de pacotes e layout de sequência. */
  public void relayout() {
    for (Node root : roots()) fit(root);
    if (kind.equals("Sequence")) SequenceSupport.relayout(this);
  }

  private static void fit(Node node) {
    for (Node child : node.children) fit(child);
    node.fit();
  }

  public void growToContent() { growToContent(40); }

  public void growToContent(int margin) {
    Rect bounds = contentBounds();
    if (bounds != null) {
      width = Math.max(width, (int) (bounds.right() + margin));
      height = Math.max(height, (int) (bounds.bottom() + margin));
    }
  }

  /** Substitui o conteúdo por o de {@code other} (usado por desfazer/refazer). */
  public void adopt(Diagram other) {
    kind = other.kind;
    nodes.clear();
    nodes.addAll(other.nodes);
    edges.clear();
    edges.addAll(other.edges);
    nextId = other.nextId;
  }

  public void recomputeNextId() {
    int max = 0;
    for (Node n : nodes) max = Math.max(max, n.id);
    for (Edge e : edges) max = Math.max(max, e.id);
    nextId = max + 1;
  }
}
