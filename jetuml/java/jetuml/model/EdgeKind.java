package jetuml.model;

public enum EdgeKind {
  DEPENDENCY("Dependency"), INHERITANCE("Inheritance"), REALIZATION("Realization"), ASSOCIATION("Association"),
  AGGREGATION("Aggregation"), COMPOSITION("Composition"), NOTE_CONNECTOR("NoteConnector"), CALL("Call"),
  RETURN("Return"), TRANSITION("Transition"), REFERENCE("Reference"), COLLABORATION("Collaboration"),
  USE_CASE_DEPENDENCY("UseCaseDependency"), GENERALIZATION("Generalization");

  private final String value;
  EdgeKind(String value) { this.value = value; }
  public String value() { return value; }

  public static EdgeKind fromValue(String value) {
    for (EdgeKind kind : values()) if (kind.value.equals(value)) return kind;
    throw new IllegalArgumentException("Tipo de aresta desconhecido: " + value);
  }
}
