package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class ObjectNode extends Node {
  public static final String TYPE = "Object";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "object:Type"));
  private static final List<String> ACCEPTS = List.of("Field");

  public ObjectNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public List<String> accepts() { return ACCEPTS; }

  @Override public void fit() {
    int widest = TextFormat.textWidth(str("name"));
    for (Node f : children) widest = Math.max(widest, TextFormat.textWidth(((FieldNode) f).label()));
    w = Math.max(minW(), widest + 20);
    h = 30 + Math.max(20, children.size() * FieldNode.FIELD_H + 8);
    for (int i = 0; i < children.size(); i++) {
      Node c = children.get(i);
      c.x = x + 4;
      c.y = y + 34 + i * FieldNode.FIELD_H;
      c.w = w - 8;
      c.h = FieldNode.FIELD_H;
    }
  }
}
