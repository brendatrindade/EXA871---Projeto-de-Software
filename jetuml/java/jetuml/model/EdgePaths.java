package jetuml.model;

import java.util.ArrayList;
import java.util.List;
import jetuml.geometry.Geometry;
import jetuml.geometry.Point;

/** Caminho automático das arestas (RF17): recalculado a partir dos nós, nunca armazenado. */
public final class EdgePaths {
  private static final double PARALLEL_GAP = 14;

  public record EdgePath(List<Point> points, Point startLabel, Point endLabel) {
    public Point middle() {
      Point a = points.get(points.size() / 2 - 1), b = points.get(points.size() / 2);
      return new Point((a.x() + b.x()) / 2, (a.y() + b.y()) / 2);
    }
  }

  private EdgePaths() { }

  public static double pathDistance(double x, double y, List<Point> points) {
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i + 1 < points.size(); i++) best = Math.min(best, Geometry.segmentDistance(x, y, points.get(i), points.get(i + 1)));
    return best;
  }

  public static EdgePath edgePath(Diagram d, Edge edge) {
    List<Point> points;
    if (edge.kind == EdgeKind.CALL || edge.kind == EdgeKind.RETURN) points = sequencePoints(edge);
    else if (edge.isSelfEdge()) points = loop(d, edge);
    else if (edge.kind == EdgeKind.REFERENCE && edge.start instanceof FieldNode) points = reference(edge);
    else points = straight(d, edge);
    return new EdgePath(points, near(points.get(0), points.get(1)), near(points.get(points.size() - 1), points.get(points.size() - 2)));
  }

  private static Point near(Point p, Point toward) { return new Point(p.x() + (toward.x() >= p.x() ? 10 : -10), p.y() - 10); }

  private static List<Point> straight(Diagram d, Edge edge) {
    Node s = edge.start, t = edge.end;
    List<Edge> same = new ArrayList<>();
    for (Edge e : d.edges) {
      if (!e.isSelfEdge() && ((e.start == s && e.end == t) || (e.start == t && e.end == s))) same.add(e);
    }
    double offset = same.size() > 1 ? (same.indexOf(edge) - (same.size() - 1) / 2.0) * PARALLEL_GAP : 0;
    Node a = s.id <= t.id ? s : t, b = a == s ? t : s;
    double ax = a.bounds().center().x(), ay = a.bounds().center().y(), bx = b.bounds().center().x(), by = b.bounds().center().y();
    double length = Math.max(Math.hypot(bx - ax, by - ay), 1);
    double nx = -(by - ay) / length * offset, ny = (bx - ax) / length * offset;
    Point p1 = Geometry.boundaryPoint(s.bounds(), t.bounds().center());
    Point p2 = Geometry.boundaryPoint(t.bounds(), s.bounds().center());
    return List.of(new Point(p1.x() + nx, p1.y() + ny), new Point(p2.x() + nx, p2.y() + ny));
  }

  private static List<Point> loop(Diagram d, Edge edge) {
    var r = edge.start.bounds();
    int index = 0;
    for (Edge e : d.edges) {
      if (e == edge) break;
      if (e.isSelfEdge() && e.start == edge.start) index++;
    }
    double size = 18 + index * 10;
    return List.of(new Point(r.right() - 20, r.y()), new Point(r.right() - 20, r.y() - size), new Point(r.right() + size, r.y() - size),
        new Point(r.right() + size, r.y() + 20), new Point(r.right(), r.y() + 20));
  }

  private static List<Point> reference(Edge edge) {
    Node f = edge.start;
    Point start = new Point(f.x + f.w - 6, f.y + f.h / 2);
    return List.of(start, Geometry.boundaryPoint(edge.end.bounds(), start));
  }

  private static List<Point> sequencePoints(Edge edge) {
    double row = edge.rowY != null ? edge.rowY : edge.start.y + 10;
    boolean call = edge.kind == EdgeKind.CALL;
    Node caller = call ? edge.start : edge.end, callee = call ? edge.end : edge.start;
    if (caller.parent == callee.parent && call) {                                        // autochamada (RF28)
      return List.of(new Point(caller.x + caller.w, row), new Point(caller.x + caller.w + 25, row),
          new Point(caller.x + caller.w + 25, row + 15), new Point(callee.x + callee.w, row + 15));
    }
    boolean rightOf = callee.x + callee.w / 2 > caller.x + caller.w / 2;
    double callerX = rightOf ? caller.x + caller.w : caller.x;
    double calleeX;
    if (call && Boolean.TRUE.equals(edge.props.get("constructor"))) {
      Node life = callee.parent;
      calleeX = rightOf ? life.x : life.x + life.w;
    } else {
      calleeX = rightOf ? callee.x : callee.x + callee.w;
    }
    return call ? List.of(new Point(callerX, row), new Point(calleeX, row)) : List.of(new Point(calleeX, row), new Point(callerX, row));
  }
}
