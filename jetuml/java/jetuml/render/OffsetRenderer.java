package jetuml.render;

import java.util.ArrayList;
import java.util.List;
import jetuml.geometry.Point;

/** Translada as coordenadas (usado para recortar a imagem exportada ao conteúdo). */
public final class OffsetRenderer implements Renderer {
  private final Renderer inner;
  private final double dx, dy;

  public OffsetRenderer(Renderer inner, double dx, double dy) { this.inner = inner; this.dx = dx; this.dy = dy; }

  private List<Point> shift(List<Point> points) {
    List<Point> out = new ArrayList<>();
    for (Point p : points) out.add(new Point(p.x() + dx, p.y() + dy));
    return out;
  }

  @Override public void line(List<Point> points, String color, double width, boolean dash) { inner.line(shift(points), color, width, dash); }
  @Override public void polygon(List<Point> points, String fill, String outline, double width) { inner.polygon(shift(points), fill, outline, width); }
  @Override public void rect(double x, double y, double w, double h, String fill, String outline, double width, double radius, boolean dash) {
    inner.rect(x + dx, y + dy, w, h, fill, outline, width, radius, dash);
  }
  @Override public void ellipse(double x, double y, double w, double h, String fill, String outline, double width) {
    inner.ellipse(x + dx, y + dy, w, h, fill, outline, width);
  }
  @Override public void text(double x, double y, String s, String color, String anchor, boolean bold, boolean italic, boolean underline) {
    inner.text(x + dx, y + dy, s, color, anchor, bold, italic, underline);
  }
}
