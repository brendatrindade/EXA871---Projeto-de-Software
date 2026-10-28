package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class InterfaceNode extends Node {
  public static final String TYPE = "Interface";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Interface"), Field.multiline("methods", "Methods"));

  public InterfaceNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public void fit() { textBox(0, List.of("«interface»", str("name")), TextFormat.linesOf(str("methods"))); }
}
