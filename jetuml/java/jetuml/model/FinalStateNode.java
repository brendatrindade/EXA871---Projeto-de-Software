package jetuml.model;

public final class FinalStateNode extends Node {
  public static final String TYPE = "FinalState";
  public FinalStateNode(double x, double y) { super(x, y); }
  @Override public String type() { return TYPE; }
  @Override public double minW() { return 24; }
  @Override public double minH() { return 24; }
}
