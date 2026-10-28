package jetuml.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static jetuml.model.EdgeKind.*;

/** Aresta e suas propriedades por tipo (RF20). */
public final class Edge {
  private static final List<Field> LABELS = List.of(Field.text("start_label", "Start Label", ""),
      Field.text("middle_label", "Middle Label", ""), Field.text("end_label", "End Label", ""));
  private static final Field DIR3 = Field.choice("directionality", "Directionality", "Unspecified", "Unspecified", "Unidirectional", "Bidirectional");
  private static final Field DIR2 = Field.choice("directionality", "Directionality", "Unidirectional", "Unidirectional", "Bidirectional");
  private static final Field AGGREGATION_TYPE = Field.choice("aggregation_type", "Aggregation Type", "Aggregation", "Aggregation", "Composition");
  private static final List<Field> MIDDLE = List.of(Field.text("middle_label", "Middle Label", ""));
  private static final Map<EdgeKind, List<Field>> FIELDS = new EnumMap<>(EdgeKind.class);

  static {
    FIELDS.put(ASSOCIATION, join(LABELS, DIR3));
    FIELDS.put(AGGREGATION, join(LABELS, DIR3, AGGREGATION_TYPE));
    FIELDS.put(COMPOSITION, join(LABELS, DIR3, AGGREGATION_TYPE));
    FIELDS.put(DEPENDENCY, List.of(Field.choice("generalization_type", "Generalization Type", "Implementation", "Inheritance", "Implementation"), DIR2));
    for (EdgeKind k : List.of(INHERITANCE, REALIZATION)) {
      FIELDS.put(k, List.of(Field.choice("generalization_type", "Generalization Type", "Inheritance", "Inheritance", "Implementation"), DIR2));
    }
    for (EdgeKind k : List.of(CALL, RETURN, TRANSITION, COLLABORATION)) FIELDS.put(k, MIDDLE);
    FIELDS.put(USE_CASE_DEPENDENCY, List.of(Field.choice("dependency_type", "Dependency Type", "None", "None", "Include", "Extend")));
  }

  private static List<Field> join(List<Field> base, Field... more) {
    List<Field> all = new ArrayList<>(base);
    all.addAll(List.of(more));
    return all;
  }

  public int id;
  public EdgeKind kind;
  public Node start, end;
  public final Map<String, Object> props = new LinkedHashMap<>();
  /** Linha vertical calculada pelo layout de sequência (RF17); {@code null} fora de diagramas de sequência. */
  public Double rowY;

  public Edge(EdgeKind kind, Node start, Node end) {
    this.kind = kind;
    this.start = start;
    this.end = end;
    for (Field f : fields()) if (derived(f.name(), kind) == null) props.put(f.name(), f.defaultValue());   // derivadas do tipo não são armazenadas
  }

  public List<Field> fields() { return FIELDS.getOrDefault(kind, List.of()); }
  public boolean isSelfEdge() { return start == end; }

  /** Propriedades cujo valor decorre do tipo da aresta; mudar o valor converte o tipo (RF20). */
  private static String derived(String name, EdgeKind kind) {
    if (name.equals("aggregation_type")) {
      if (kind == AGGREGATION) return "Aggregation";
      if (kind == COMPOSITION) return "Composition";
    } else if (name.equals("generalization_type")) {
      if (kind == INHERITANCE) return "Inheritance";
      if (kind == REALIZATION) return "Implementation";
    }
    return null;
  }

  public Object get(String name) {
    String d = derived(name, kind);
    return d != null ? d : props.getOrDefault(name, "");
  }

  public String str(String name) { return String.valueOf(get(name)); }

  public void set(String name, Object value) {
    if (derived(name, kind) != null) {
      kind = name.equals("aggregation_type") ? ("Aggregation".equals(value) ? AGGREGATION : COMPOSITION)
                                              : ("Inheritance".equals(value) ? INHERITANCE : REALIZATION);
    } else {
      props.put(name, value);
    }
  }
}
