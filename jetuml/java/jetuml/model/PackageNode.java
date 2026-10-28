package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class PackageNode extends Node {
  public static final String TYPE = "Package";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Package"));
  private static final List<String> ACCEPTS = List.of("Class", "Interface", "Package", "PackageDescription", "Note");

  public PackageNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public List<String> accepts() { return ACCEPTS; }
  @Override public double minH() { return 80; }

  @Override public void fit() {
    w = Math.max(minW(), TextFormat.textWidth(str("name")) + 30);
    h = minH();
    for (Node child : children) {
      w = Math.max(w, child.x + child.w + 10 - x);
      h = Math.max(h, child.y + child.h + 10 - y);
    }
  }
}
