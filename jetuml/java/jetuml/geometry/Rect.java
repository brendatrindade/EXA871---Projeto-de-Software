package jetuml.geometry;

public record Rect(double x, double y, double w, double h) {
  public double right() { return x + w; }
  public double bottom() { return y + h; }
  public Point center() { return new Point(x + w / 2, y + h / 2); }
  public boolean contains(double px, double py) { return x <= px && px <= right() && y <= py && py <= bottom(); }
  public boolean containsRect(Rect o) { return x <= o.x && y <= o.y && o.right() <= right() && o.bottom() <= bottom(); }
  public boolean intersects(Rect o) { return x <= o.right() && o.x <= right() && y <= o.bottom() && o.y <= bottom(); }

  public Rect union(Rect o) {
    double nx = Math.min(x, o.x), ny = Math.min(y, o.y);
    return new Rect(nx, ny, Math.max(right(), o.right()) - nx, Math.max(bottom(), o.bottom()) - ny);
  }

  public Rect inflated(double m) { return new Rect(x - m, y - m, w + 2 * m, h + 2 * m); }
}
