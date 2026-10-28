package jetuml.model;

import java.util.List;
import jetuml.text.TextFormat;

public final class ActorNode extends Node {
  public static final String TYPE = "Actor";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Actor"));

  public ActorNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public double minW() { return 50; }
  @Override public double minH() { return 80; }
  @Override public void fit() { w = Math.max(minW(), TextFormat.textWidth(str("name")) + 10); }
}
