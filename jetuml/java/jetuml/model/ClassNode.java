package jetuml.model;

import java.util.List;
import jetuml.text.TextFormat;

public final class ClassNode extends Node {
  public static final String TYPE = "Class";
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", "Class"),
      Field.multiline("attributes", "Attributes"), Field.multiline("methods", "Methods"));

  public ClassNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }

  @Override public void fit() {
    textBox(0, List.of(str("name")), TextFormat.linesOf(str("attributes")), TextFormat.linesOf(str("methods")));
    h = Math.max(minH(), h);
  }
}
