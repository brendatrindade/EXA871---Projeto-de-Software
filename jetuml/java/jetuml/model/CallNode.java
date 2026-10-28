package jetuml.model;

import java.util.List;

/** Caixa de ativação; criada pelas chamadas, nunca diretamente pelo usuário. */
public final class CallNode extends Node {
  public static final String TYPE = "Call";
  private static final List<Field> FIELDS = List.of(Field.bool("open_bottom", "Open Bottom", false));

  public CallNode(double x, double y) { super(x, y); }
  public CallNode() { this(0, 0); }
  @Override public String type() { return TYPE; }
  @Override public List<Field> fields() { return FIELDS; }
  @Override public boolean movable() { return false; }
  @Override public double minW() { return 16; }
  @Override public double minH() { return 30; }
}
