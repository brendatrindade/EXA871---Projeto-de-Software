package jetuml.model;

import java.util.List;

/** Campo de objeto: só existe como filho de ObjectNode (RF25). */
public final class FieldNode extends Node {
  public static final String TYPE = "Field";
  public static final int FIELD_H = 22;
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "field"), Field.text("value", "Value", ""));

  public FieldNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public boolean movable() { return false; }
  @Override public double minW() { return 60; }
  @Override public double minH() { return FIELD_H; }

  public String label() {
    String value = str("value");
    return value.isEmpty() ? str("name") : str("name") + " = " + value;
  }
}
