package jetuml.render;

import java.util.List;
import java.util.Locale;
import jetuml.geometry.Point;
import jetuml.geometry.Rect;
import jetuml.model.Diagram;
import jetuml.text.TextFormat;

/** Exportação SVG sem dependências externas (RF04). */
public final class SvgRenderer implements Renderer {
  private final StringBuilder parts = new StringBuilder();

  private static String paint(String fill, String outline, double width, boolean dash) {
    String stroke = outline != null ? String.format(Locale.ROOT, "stroke=\"%s\" stroke-width=\"%s\"", outline, num(width)) : "stroke=\"none\"";
    return "fill=\"" + (fill != null ? fill : "none") + "\" " + stroke + (dash ? " stroke-dasharray=\"5,4\"" : "");
  }

  private static String num(double v) { return String.format(Locale.ROOT, "%.1f", v); }

  private static String points(List<Point> pts) {
    StringBuilder sb = new StringBuilder();
    for (Point p : pts) sb.append(sb.length() > 0 ? " " : "").append(num(p.x())).append(',').append(num(p.y()));
    return sb.toString();
  }

  static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

  @Override public void line(List<Point> pts, String color, double width, boolean dash) {
    parts.append("<polyline points=\"").append(points(pts)).append("\" ").append(paint(null, color, width, dash)).append("/>");
  }

  @Override public void polygon(List<Point> pts, String fill, String outline, double width) {
    parts.append("<polygon points=\"").append(points(pts)).append("\" ").append(paint(fill, outline, width, false)).append("/>");
  }

  @Override public void rect(double x, double y, double w, double h, String fill, String outline, double width, double radius, boolean dash) {
    parts.append(String.format(Locale.ROOT, "<rect x=\"%s\" y=\"%s\" width=\"%s\" height=\"%s\" rx=\"%s\" ", num(x), num(y), num(w), num(h), num(radius)))
        .append(paint(fill, outline, width, dash)).append("/>");
  }

  @Override public void ellipse(double x, double y, double w, double h, String fill, String outline, double width) {
    parts.append(String.format(Locale.ROOT, "<ellipse cx=\"%s\" cy=\"%s\" rx=\"%s\" ry=\"%s\" ", num(x + w / 2), num(y + h / 2), num(w / 2), num(h / 2)))
        .append(paint(fill, outline, width, false)).append("/>");
  }

  @Override public void text(double x, double y, String s, String color, String anchor, boolean bold, boolean italic, boolean underline) {
    StringBuilder attrs = new StringBuilder("text-anchor=\"" + (anchor.equals("center") ? "middle" : "start") + "\" dominant-baseline=\"central\"");
    if (bold) attrs.append(" font-weight=\"bold\"");
    if (italic) attrs.append(" font-style=\"italic\"");
    if (underline) attrs.append(" text-decoration=\"underline\"");
    parts.append("<text x=\"").append(num(x)).append("\" y=\"").append(num(y)).append("\" fill=\"").append(color)
        .append("\" font-family=\"").append(escape(TextFormat.family())).append(",sans-serif\" font-size=\"").append(TextFormat.size())
        .append("\" ").append(attrs).append('>').append(escape(s)).append("</text>");
  }

  /** SVG do diagrama; {@code bounds} recorta ao conteúdo (usado também pela cópia de imagem). */
  public static String render(Diagram d, Rect bounds, Palette palette, boolean grid) {
    int width = bounds == null ? d.width : (int) bounds.w(), height = bounds == null ? d.height : (int) bounds.h();
    SvgRenderer renderer = new SvgRenderer();
    Renderer target = bounds == null ? renderer : new OffsetRenderer(renderer, -bounds.x(), -bounds.y());
    DiagramPainter.draw(d, target, palette, grid, DiagramPainter.Overlay.NONE);
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height
        + "\"><rect width=\"100%\" height=\"100%\" fill=\"" + palette.background() + "\"/>" + renderer.parts + "</svg>";
  }
}
