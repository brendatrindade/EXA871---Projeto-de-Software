package jetuml.model;

import java.util.List;

import jetuml.text.TextFormat;

/** Linha de vida: cabeçalho + linha tracejada. A altura vem do layout de sequência. */
public final class ImplicitParameterNode extends Node {
  public static final String TYPE = "ImplicitParameter";
  public static final int HEADER_H = 40;
  private static final List<Field> FIELDS = List.of(Field.text("name", "Name", ":Type"));
  private static final List<String> ACCEPTS = List.of("Call");

  public ImplicitParameterNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public List<String> accepts() { return ACCEPTS; }
  @Override public double minW() { return 80; }
  @Override public double minH() { return HEADER_H + 80; }

  @Override public void fit() {
    w = Math.max(minW(), TextFormat.textWidth(str("name")) + 20);
    h = minH();
  }

  public boolean inHeader(double py) { return py < y + HEADER_H; }
}
