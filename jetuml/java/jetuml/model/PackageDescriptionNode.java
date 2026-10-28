package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class PackageDescriptionNode extends Node {
  public static final String TYPE = "PackageDescription";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Package"), Field.multiline("contents", "Contents"));

  public PackageDescriptionNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public void fit() { textBox(0, List.of(str("name")), TextFormat.linesOf(str("contents"))); }
}
