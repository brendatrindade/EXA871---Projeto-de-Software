package jetuml.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jetuml.model.*;
import jetuml.rules.Rules;

/** Formato JSON aberto {@code jetuml-sketch-v2} (RNF02), igual ao da versão Python; ver FORMATO.md. */
public final class DiagramSerializer {
  public static final String SCHEMA = "jetuml-sketch-v2";

  private DiagramSerializer() { }

  public static Map<String, Object> toMap(Diagram d) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("schema", SCHEMA);
    out.put("diagram", d.kind);
    List<Object> nodes = new ArrayList<>();
    for (Node n : d.nodes) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", n.id);
      m.put("type", n.type());
      m.put("x", n.x);
      m.put("y", n.y);
      m.put("parent", n.parent == null ? null : n.parent.id);
      m.put("properties", new LinkedHashMap<>(n.props));
      nodes.add(m);
    }
    List<Object> edges = new ArrayList<>();
    for (Edge e : d.edges) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.id);
      m.put("kind", e.kind.value());
      m.put("start", e.start.id);
      m.put("end", e.end.id);
      m.put("properties", new LinkedHashMap<>(e.props));
      edges.add(m);
    }
    out.put("nodes", nodes);
    out.put("edges", edges);
    return out;
  }

  public static String dumps(Diagram d) { return Json.write(toMap(d)); }

  public static Diagram loads(String text) { return fromMap(asMap(Json.parseOrInvalid(text), "documento"), true); }

  public static Diagram fromMap(Map<String, Object> data, boolean validate) {
    try {
      if (!SCHEMA.equals(data.get("schema"))) throw new ValidationException("Formato desconhecido (esperado jetuml-sketch-v2).");
      String kind = asString(data.get("diagram"), "diagram");
      if (!Diagram.KINDS.contains(kind)) throw new ValidationException("Tipo de diagrama inválido.");
      Diagram d = new Diagram(kind);
      Map<Integer, Node> byId = new HashMap<>();
      List<Object> nodeEntries = asList(data.get("nodes"), "nodes");
      for (Object o : nodeEntries) {
        Map<String, Object> entry = asMap(o, "nó");
        String type = asString(entry.get("type"), "type");
        if (!NodeTypes.known(type)) throw new ValidationException("Tipo de nó desconhecido: " + type);
        Node node = NodeTypes.create(type, asNumber(entry.get("x")), asNumber(entry.get("y")));
        node.id = (int) asNumber(entry.get("id"));
        if (byId.put(node.id, node) != null) throw new ValidationException("Identificadores de nó duplicados.");
        if (entry.get("properties") != null) node.props.putAll(asMap(entry.get("properties"), "properties"));
        d.nodes.add(node);
      }
      for (Object o : nodeEntries) {
        Map<String, Object> entry = asMap(o, "nó");
        if (entry.get("parent") != null) {
          Node parent = byId.get((int) asNumber(entry.get("parent")));
          if (parent == null) throw new ValidationException("Pai inexistente.");
          Node node = byId.get((int) asNumber(entry.get("id")));
          node.parent = parent;
          parent.children.add(node);
        }
      }
      for (Node n : d.nodes) {
        int depth = 0;
        for (Node a = n.parent; a != null; a = a.parent) {
          if (++depth > d.nodes.size()) throw new ValidationException("Hierarquia de pais inválida.");
        }
      }
      for (Object o : asList(data.get("edges"), "edges")) {
        Map<String, Object> entry = asMap(o, "aresta");
        Node start = byId.get((int) asNumber(entry.get("start"))), end = byId.get((int) asNumber(entry.get("end")));
        if (start == null || end == null) throw new ValidationException("Aresta referencia elemento inexistente.");
        Edge edge = new Edge(EdgeKind.fromValue(asString(entry.get("kind"), "kind")), start, end);
        edge.id = (int) asNumber(entry.get("id"));
        if (entry.get("properties") != null) edge.props.putAll(asMap(entry.get("properties"), "properties"));
        d.edges.add(edge);
      }
      d.recomputeNextId();
      d.relayout();
      if (validate) Rules.validateDiagram(d);
      return d;
    } catch (ValidationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new ValidationException("Arquivo de diagrama malformado: " + ex.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, String what) {
    if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
    throw new ValidationException("Arquivo de diagrama malformado: " + what);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value, String what) {
    if (value instanceof List<?>) return (List<Object>) value;
    throw new ValidationException("Arquivo de diagrama malformado: " + what);
  }

  private static String asString(Object value, String what) {
    if (value instanceof String s) return s;
    throw new ValidationException("Arquivo de diagrama malformado: " + what);
  }

  private static double asNumber(Object value) {
    if (value instanceof Number n) return n.doubleValue();
    throw new ValidationException("Arquivo de diagrama malformado: número esperado");
  }
}
