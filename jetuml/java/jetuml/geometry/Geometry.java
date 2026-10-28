package jetuml.geometry;

/** Utilidades geométricas independentes de interface. */
public final class Geometry {
  public static final int GRID = 10;

  private Geometry() { }

  /** Alinha à grade (RF36); arredonda metades para o par, como a versão Python. */
  public static int snap(double value) { return (int) Math.rint(value / GRID) * GRID; }

  public static Rect normalizedRect(Point a, Point b) {
    return new Rect(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
  }

  /** Ponto onde a reta do centro do retângulo até {@code toward} cruza a borda. */
  public static Point boundaryPoint(Rect rect, Point toward) {
    Point c = rect.center();
    double dx = toward.x() - c.x(), dy = toward.y() - c.y();
    if (dx == 0 && dy == 0) return c;
    double sx = dx != 0 ? (rect.w() / 2) / Math.abs(dx) : Double.POSITIVE_INFINITY;
    double sy = dy != 0 ? (rect.h() / 2) / Math.abs(dy) : Double.POSITIVE_INFINITY;
    double scale = Math.min(sx, sy);
    return new Point(c.x() + dx * scale, c.y() + dy * scale);
  }

  public static double segmentDistance(double px, double py, Point a, Point b) {
    double dx = b.x() - a.x(), dy = b.y() - a.y();
    double length2 = dx * dx + dy * dy;
    double t = length2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - a.x()) * dx + (py - a.y()) * dy) / length2));
    return Math.hypot(px - (a.x() + t * dx), py - (a.y() + t * dy));
  }
}
