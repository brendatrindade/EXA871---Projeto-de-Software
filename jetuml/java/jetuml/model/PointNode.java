package jetuml.model;

/** Âncora invisível: extremidade fixa de um Note Connector (RF14). */
public final class PointNode extends Node {
  public static final String TYPE = "Point";
  public PointNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public boolean selectable() { return false; }
  @Override public boolean movable() { return false; }
  @Override public double minW() { return 0; }
  @Override public double minH() { return 0; }
}
