package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

public final class NoteNode extends Node {
  public static final String TYPE = "Note";
  private static final List<Field> FIELDS = List.of(Field.multiline("text", "Text", "Note"));

  public NoteNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public double minW() { return 60; }
  @Override public double minH() { return 40; }

  @Override public void fit() {
    List<String> lines = TextFormat.linesOf(str("text"));
    if (lines.isEmpty()) lines = List.of("");
    int widest = 0;
    for (String line : lines) widest = Math.max(widest, TextFormat.textWidth(line));
    w = Math.max(minW(), widest + 20);
    h = Math.max(minH(), lines.size() * TextFormat.lineHeight() + 16);
  }
}
