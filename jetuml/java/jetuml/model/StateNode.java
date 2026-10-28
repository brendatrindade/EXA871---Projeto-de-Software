package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class StateNode extends Node {
  public static final String TYPE = "State";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "State"));

  public StateNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public double minW() { return 80; }
  @Override public double minH() { return 50; }
  @Override public void fit() { w = Math.max(minW(), TextFormat.textWidth(str("name")) + 30); }
}
