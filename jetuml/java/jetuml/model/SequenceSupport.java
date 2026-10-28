package jetuml.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Diagrama de sequência: layout vertical automático, criação de chamadas/retornos e validação (RF27–RF31). */
public final class SequenceSupport {
  public static final int TOP = 20, BOX_W = 16, NEST = 8;
  public static final String CREATE_LABEL = "«create»";

  private SequenceSupport() { }

  public record Deletion(Set<Node> nodes, Set<Edge> edges) { }

  // ---------- consultas ----------
  public static Edge incoming(Diagram d, Node node) {
    for (Edge e : d.edges) if (e.kind == EdgeKind.CALL && e.end == node) return e;
    return null;
  }

  public static List<Edge> outgoing(Diagram d, Node node) {
    List<Edge> result = new ArrayList<>();
    for (Edge e : d.edges) if (e.kind == EdgeKind.CALL && e.start == node) result.add(e);
    return result;
  }

  public static boolean hasReturn(Diagram d, Node node) {
    for (Edge e : d.edges) if (e.kind == EdgeKind.RETURN && e.start == node) return true;
    return false;
  }

  public static List<CallNode> orphanRoots(Diagram d) {
    List<CallNode> roots = new ArrayList<>();
    for (Node n : d.nodes) if (n instanceof CallNode c && incoming(d, c) == null) roots.add(c);
    return roots;
  }

  /** True se {@code node} é alcançável a partir de {@code ancestor} seguindo chamadas. */
  public static boolean reaches(Diagram d, Node ancestor, Node node) {
    List<Node> stack = new ArrayList<>(List.of(ancestor));
    Set<Node> seen = new HashSet<>();
    while (!stack.isEmpty()) {
      Node current = stack.remove(stack.size() - 1);
      if (current == node) return true;
      if (seen.add(current)) for (Edge e : outgoing(d, current)) stack.add(e.end);
    }
    return false;
  }

  // ---------- layout ----------
  private static final class Placer {
    final Map<Node, List<Edge>> out = new HashMap<>();
    final Map<Node, Integer> open = new HashMap<>();
    final Map<Node, Double> bottoms = new HashMap<>();
    final Set<Node> placed = new HashSet<>();

    double place(CallNode node, double top) {
      Node life = node.parent;
      node.x = life.x + life.w / 2 - BOX_W / 2.0 + open.getOrDefault(life, 0) * NEST;
      node.y = top;
      node.w = BOX_W;
      placed.add(node);
      open.merge(life, 1, Integer::sum);
      double y = top + 20;
      for (Edge edge : out.getOrDefault(node, List.of())) {
        if (!(edge.end instanceof CallNode callee) || placed.contains(callee)) continue;
        double row = y + 10;
        edge.rowY = row;
        double calleeTop;
        if (Boolean.TRUE.equals(edge.props.get("constructor"))) {
          callee.parent.y = row - ImplicitParameterNode.HEADER_H / 2.0;
          calleeTop = callee.parent.y + ImplicitParameterNode.HEADER_H;
        } else {
          calleeTop = row - 10;
        }
        y = place(callee, calleeTop) + 10;
      }
      double bottom = Math.max(top + 30, y);
      node.h = bottom - top;
      open.merge(life, -1, Integer::sum);
      bottoms.merge(life, bottom, Math::max);
      return bottom;
    }
  }

  public static void relayout(Diagram d) {
    List<ImplicitParameterNode> lifelines = new ArrayList<>();
    for (Node n : d.nodes) if (n instanceof ImplicitParameterNode l) lifelines.add(l);
    for (ImplicitParameterNode life : lifelines) life.y = TOP;
    Placer placer = new Placer();
    for (Edge e : d.edges) if (e.kind == EdgeKind.CALL) placer.out.computeIfAbsent(e.start, k -> new ArrayList<>()).add(e);
    double cursor = TOP + ImplicitParameterNode.HEADER_H + 10;
    List<CallNode> roots = orphanRoots(d);
    roots.sort((a, b) -> Integer.compare(a.id, b.id));
    for (CallNode root : roots) {
      if (root.parent == null) continue;
      cursor = placer.place(root, cursor) + 40;
    }
    for (Edge e : d.edges) if (e.kind == EdgeKind.RETURN) e.rowY = e.start.y + e.start.h - 10;
    for (ImplicitParameterNode life : lifelines) {
      double bottom = placer.bottoms.getOrDefault(life, life.y + ImplicitParameterNode.HEADER_H + 40);
      life.h = Math.max(life.minH(), bottom + 40 - life.y);
    }
  }

  // ---------- criação ----------
  private static CallNode rootOn(Diagram d, Node life) {
    for (CallNode n : orphanRoots(d)) if (n.parent == life) return n;
    return null;
  }

  public static Edge connectCall(Diagram d, Node start, Node end, double y) {
    if (!(start instanceof CallNode) && !(start instanceof ImplicitParameterNode)) {
      throw new ValidationException("Chamadas partem de linhas de vida ou de caixas de ativação.");
    }
    String mode;
    Node life;
    if (end instanceof CallNode box) {
      mode = "box";
      life = box.parent;
    } else if (end instanceof ImplicitParameterNode lifeline) {
      mode = lifeline.inHeader(y) ? "constructor" : "body";
      life = lifeline;
    } else {
      throw new ValidationException("Uma chamada deve terminar em uma linha de vida ou em um parâmetro implícito.");
    }
    Node caller = start;
    boolean newRoot = false;
    if (start instanceof ImplicitParameterNode) {
      caller = rootOn(d, start);
      newRoot = caller == null;
      if (newRoot && !orphanRoots(d).isEmpty()) {
        throw new ValidationException("O diagrama só pode ter uma chamada raiz sem chamador.");
      }
    }
    if (caller != null && hasReturn(d, caller)) {
      throw new ValidationException("Salto no fluxo de controle.", true);          // RF30: falha silenciosa
    }
    if (mode.equals("box")) {
      if (end == start) {
        mode = "self";                                                               // RF28
      } else if (incoming(d, end) != null) {
        throw new ValidationException("Uma caixa de ativação não pode receber chamadas de vários chamadores.");
      } else if (caller != null && reaches(d, end, caller)) {
        throw new ValidationException("A chamada criaria um ciclo de controle.");
      }
    }
    if (mode.equals("constructor")) {
      if (!end.children.isEmpty()) throw new ValidationException("O parâmetro implícito já possui caixas de ativação.");
      if (caller != null && caller.parent == end) throw new ValidationException("Um objeto não pode criar a si mesmo.");
    }
    if (newRoot) caller = d.addNode(new CallNode(), start);
    Node callee = mode.equals("box") ? end : d.addNode(new CallNode(), mode.equals("self") ? start.parent : life);
    Edge edge = new Edge(EdgeKind.CALL, caller, callee);
    if (mode.equals("constructor")) {
      edge.props.put("constructor", Boolean.TRUE);
      edge.props.put("middle_label", CREATE_LABEL);
    }
    return d.addEdge(edge);
  }

  public static Edge connectReturn(Diagram d, Node start, Node end) {
    if (!(start instanceof CallNode) || !(end instanceof CallNode)) {
      throw new ValidationException("Retornos conectam caixas de ativação.");
    }
    boolean hasCall = false;
    for (Edge e : d.edges) if (e.kind == EdgeKind.CALL && e.start == end && e.end == start) hasCall = true;
    if (!hasCall || hasReturn(d, start)) throw new ValidationException("Salto no fluxo de controle.", true);
    return d.addEdge(new Edge(EdgeKind.RETURN, start, end));
  }

  // ---------- exclusão e validação ----------
  /** Apagar uma chamada ou caixa apaga também o que dependia dela. */
  public static Deletion expandDeletion(Diagram d, Set<Node> nodes, Set<Edge> edges) {
    Set<Node> allNodes = new LinkedHashSet<>(nodes);
    Set<Edge> allEdges = new LinkedHashSet<>(edges);
    for (Node n : new ArrayList<>(allNodes)) allNodes.addAll(n.descendants());
    List<Edge> work = new ArrayList<>();
    for (Edge e : allEdges) if (e.kind == EdgeKind.CALL) work.add(e);
    for (Node n : allNodes) work.addAll(outgoing(d, n));
    while (!work.isEmpty()) {
      Edge edge = work.remove(work.size() - 1);
      allEdges.add(edge);
      if (edge.end instanceof CallNode callee && !allNodes.contains(callee)) {
        allNodes.add(callee);
        work.addAll(outgoing(d, callee));
      }
    }
    return new Deletion(allNodes, allEdges);
  }

  public static void pruneIsolated(Diagram d) {
    for (Node n : new ArrayList<>(d.nodes)) {
      if (n instanceof CallNode && d.nodes.contains(n) && d.edgesOf(n).isEmpty()) d.removeNode(n);
    }
  }

  public static void validateStructure(Diagram d) {
    for (Edge e : d.edges) {
      if ((e.kind == EdgeKind.CALL || e.kind == EdgeKind.RETURN) && !(e.start instanceof CallNode && e.end instanceof CallNode)) {
        throw new ValidationException("Chamadas e retornos devem ligar caixas de ativação.");
      }
    }
    Set<Node> seen = new HashSet<>();
    for (Edge e : d.edges) {
      if (e.kind == EdgeKind.CALL) {
        if (!seen.add(e.end)) throw new ValidationException("Uma caixa de ativação recebe chamadas de vários chamadores.");
      } else if (e.kind == EdgeKind.RETURN) {
        boolean matched = false;
        for (Edge c : d.edges) if (c.kind == EdgeKind.CALL && c.start == e.end && c.end == e.start) matched = true;
        if (!matched) throw new ValidationException("Retorno sem a chamada correspondente.");
      }
    }
    if (orphanRoots(d).size() > 1) throw new ValidationException("Mais de uma chamada raiz sem chamador.");
  }
}
