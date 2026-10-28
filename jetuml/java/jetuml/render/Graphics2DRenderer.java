package jetuml.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import jetuml.geometry.Point;
import jetuml.text.TextFormat;

/** Renderizador Java2D com escala de zoom (RF37); serve à tela Swing, à imagem exportada e ao clipboard. */
public final class Graphics2DRenderer implements Renderer {
  private static final float[] DASH = {5f, 4f};
  private final Graphics2D g;
  private final double scale;

  public Graphics2DRenderer(Graphics2D g, double scale) {
    this.g = g;
    this.scale = scale;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }

  private Stroke stroke(double width, boolean dash) {
    float w = (float) Math.max(1, width);
    return dash ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH, 0f) : new BasicStroke(w);
  }

  private Path2D path(List<Point> pts, boolean close) {
    Path2D p = new Path2D.Double();
    for (int i = 0; i < pts.size(); i++) {
      if (i == 0) p.moveTo(pts.get(i).x() * scale, pts.get(i).y() * scale);
      else p.lineTo(pts.get(i).x() * scale, pts.get(i).y() * scale);
    }
    if (close) p.closePath();
    return p;
  }

  private void paint(java.awt.Shape shape, String fill, String outline, double width, boolean dash) {
    if (fill != null) { g.setColor(Color.decode(fill)); g.fill(shape); }
    if (outline != null) { g.setColor(Color.decode(outline)); g.setStroke(stroke(width, dash)); g.draw(shape); }
  }

  @Override public void line(List<Point> pts, String color, double width, boolean dash) { paint(path(pts, false), null, color, width, dash); }
  @Override public void polygon(List<Point> pts, String fill, String outline, double width) { paint(path(pts, true), fill, outline, width, false); }

  @Override public void rect(double x, double y, double w, double h, String fill, String outline, double width, double radius, boolean dash) {
    java.awt.Shape shape = radius > 0 ? new RoundRectangle2D.Double(x * scale, y * scale, w * scale, h * scale, radius * 2 * scale, radius * 2 * scale)
        : new java.awt.geom.Rectangle2D.Double(x * scale, y * scale, w * scale, h * scale);
    paint(shape, fill, outline, width, dash);
  }

  @Override public void ellipse(double x, double y, double w, double h, String fill, String outline, double width) {
    paint(new Ellipse2D.Double(x * scale, y * scale, w * scale, h * scale), fill, outline, width, false);
  }

  @Override public void text(double x, double y, String s, String color, String anchor, boolean bold, boolean italic, boolean underline) {
    int style = (bold ? Font.BOLD : Font.PLAIN) | (italic ? Font.ITALIC : 0);
    g.setFont(new Font(TextFormat.family(), style, Math.max(6, (int) Math.round(TextFormat.size() * scale))));
    FontMetrics fm = g.getFontMetrics();
    double width = fm.stringWidth(s);
    double left = anchor.equals("center") ? x * scale - width / 2 : x * scale;
    double baseline = y * scale + (fm.getAscent() - fm.getDescent()) / 2.0;
    g.setColor(Color.decode(color));
    g.drawString(s, (float) left, (float) baseline);
    if (underline) {
      g.setStroke(new BasicStroke(1f));
      g.drawLine((int) left, (int) baseline + 2, (int) (left + width), (int) baseline + 2);
    }
  }
}
