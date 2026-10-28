package jetuml.render;

import java.util.List;
import jetuml.geometry.Point;

/**
 * Interface de desenho abstrata: o mesmo {@link DiagramPainter} alimenta a tela (Java2D), SVG, PNG/JPEG e a
 * cópia para a área de transferência. Em {@code text}, y é o centro vertical; anchor é "center" ou "w".
 */
public interface Renderer {
  void line(List<Point> points, String color, double width, boolean dash);
  void polygon(List<Point> points, String fill, String outline, double width);
  void rect(double x, double y, double w, double h, String fill, String outline, double width, double radius, boolean dash);
  void ellipse(double x, double y, double w, double h, String fill, String outline, double width);
  void text(double x, double y, String s, String color, String anchor, boolean bold, boolean italic, boolean underline);

  default void line(List<Point> points, String color) { line(points, color, 1, false); }
  default void rect(double x, double y, double w, double h, String fill, String outline) { rect(x, y, w, h, fill, outline, 1, 0, false); }
  default void rect(double x, double y, double w, double h, String fill, String outline, double width) { rect(x, y, w, h, fill, outline, width, 0, false); }
  default void ellipse(double x, double y, double w, double h, String fill, String outline) { ellipse(x, y, w, h, fill, outline, 1); }
}
