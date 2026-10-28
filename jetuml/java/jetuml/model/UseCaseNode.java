package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class UseCaseNode extends Node {
  public static final String TYPE = "UseCase";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Use Case"));

  public UseCaseNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public double minW() { return 110; }
  @Override public double minH() { return 50; }
  @Override public void fit() { w = Math.max(minW(), TextFormat.textWidth(str("name")) + 40); }
}
