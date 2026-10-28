package jetuml.model;

public final class InitialStateNode extends Node {
  public static final String TYPE = "InitialState";
  public InitialStateNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public double minW() { return 20; }
  @Override public double minH() { return 20; }
}
