package jetuml.render;

import java.util.List;
import jetuml.geometry.Geometry;
import jetuml.geometry.Point;
import jetuml.geometry.Rect;
import jetuml.model.*;
import jetuml.text.TextFormat;

/** Desenho do diagrama sobre um {@link Renderer} abstrato. */
public final class DiagramPainter {
  /** Elementos transitórios desenhados por cima: seleção, linha elástica e laço. */
  public record Overlay(List<Object> selection, Point rubberFrom, Point rubberTo, Rect lasso) {
    public static final Overlay NONE = new Overlay(List.of(), null, null, null);
  }

  private record EdgeStyle(boolean dashed, String startHead, String endHead, String fixedLabel) { }

  private DiagramPainter() { }

  public static void draw(Diagram d, Renderer r, Palette pal, boolean grid, Overlay overlay) {
    if (grid) {
      for (int x = 0; x <= d.width; x += Geometry.GRID) r.line(List.of(new Point(x, 0), new Point(x, d.height)), pal.grid());
      for (int y = 0; y <= d.height; y += Geometry.GRID) r.line(List.of(new Point(0, y), new Point(d.width, y)), pal.grid());
    }
    for (Node node : d.drawOrder()) drawNode(node, r, pal);
    for (Edge edge : d.edges) drawEdge(d, edge, r, pal);
    for (Object item : overlay.selection()) {
      if (item instanceof Node n) r.rect(n.x - 3, n.y - 3, n.w + 6, n.h + 6, null, pal.selection(), 2);
      else r.line(EdgePaths.edgePath(d, (Edge) item).points(), pal.selection(), 3, false);
    }
    if (overlay.rubberFrom() != null) r.line(List.of(overlay.rubberFrom(), overlay.rubberTo()), pal.selection(), 1, true);
    if (overlay.lasso() != null) {
      Rect l = overlay.lasso();
      r.rect(l.x(), l.y(), l.w(), l.h(), null, pal.selection(), 1, 0, true);
    }
  }

  // ---------- texto ----------
  private static void line(Renderer r, double x, double y, String text, String color, String anchor, boolean bold) {
    TextFormat.Parsed p = TextFormat.parseLine(text);
    r.text(x, y, p.text(), color, anchor, bold, p.italic(), p.underline());
  }

  private static int comp(int count) { return Math.max(20, count * TextFormat.lineHeight() + 10); }

  /** Compartimentos empilhados (classe/interface). */
  private static void stack(Renderer r, Node node, List<List<String>> groups, Palette pal) {
    int lh = TextFormat.lineHeight();
    double y = node.y;
    for (int gi = 0; gi < groups.size(); gi++) {
      List<String> lines = groups.get(gi);
      int height = comp(lines.size());
      if (gi > 0) r.line(List.of(new Point(node.x, y), new Point(node.x + node.w, y)), pal.foreground());
      for (int i = 0; i < lines.size(); i++) {
        String text = lines.get(i);
        if (gi == 0) line(r, node.x + node.w / 2, y + (height - lines.size() * lh) / 2.0 + i * lh + lh / 2.0, text, pal.foreground(), "center", !text.startsWith("«"));
        else line(r, node.x + 8, y + 5 + i * lh + lh / 2.0, text, pal.foreground(), "w", false);
      }
      y += height;
    }
  }

  private static void drawNode(Node n, Renderer r, Palette pal) {
    String fg = pal.foreground(), fill = pal.nodeFill();
    if (n instanceof PointNode) return;
    if (n instanceof ClassNode) {
      r.rect(n.x, n.y, n.w, n.h, fill, fg);
      stack(r, n, List.of(List.of(n.str("name")), TextFormat.linesOf(n.str("attributes")), TextFormat.linesOf(n.str("methods"))), pal);
    } else if (n instanceof InterfaceNode) {
      r.rect(n.x, n.y, n.w, n.h, fill, fg);
      stack(r, n, List.of(List.of("«interface»", n.str("name")), TextFormat.linesOf(n.str("methods"))), pal);
    } else if (n instanceof PackageNode || n instanceof PackageDescriptionNode) {
      double tabW = Math.min(n.w, Math.max(60, TextFormat.textWidth(n.str("name")) + 20));
      r.rect(n.x, n.y, tabW, 20, fill, fg);
      r.rect(n.x, n.y + 20, n.w, n.h - 20, fill, fg);
      line(r, n.x + tabW / 2, n.y + 10, n.str("name"), fg, "center", false);
      if (n instanceof PackageDescriptionNode) {
        List<String> lines = TextFormat.linesOf(n.str("contents"));
        for (int i = 0; i < lines.size(); i++) line(r, n.x + 8, n.y + 30 + i * TextFormat.lineHeight(), lines.get(i), fg, "w", false);
      }
    } else if (n instanceof NoteNode) {
      r.polygon(List.of(new Point(n.x, n.y), new Point(n.x + n.w - 12, n.y), new Point(n.x + n.w, n.y + 12),
          new Point(n.x + n.w, n.y + n.h), new Point(n.x, n.y + n.h)), pal.noteFill(), fg, 1);
      List<String> lines = TextFormat.linesOf(n.str("text"));
      for (int i = 0; i < lines.size(); i++) line(r, n.x + 8, n.y + 14 + i * TextFormat.lineHeight(), lines.get(i), fg, "w", false);
    } else if (n instanceof ObjectNode) {
      r.rect(n.x, n.y, n.w, n.h, fill, fg);
      r.line(List.of(new Point(n.x, n.y + 30), new Point(n.x + n.w, n.y + 30)), fg);
      r.text(n.x + n.w / 2, n.y + 15, n.str("name"), fg, "center", false, false, true);
    } else if (n instanceof FieldNode f) {
      r.text(n.x + 4, n.y + n.h / 2, f.label(), fg, "w", false, false, false);
    } else if (n instanceof ActorNode) {
      double cx = n.x + n.w / 2, top = n.y;
      r.ellipse(cx - 8, top, 16, 16, fill, fg);
      double[][] segments = {{cx, top + 16, cx, top + 42}, {cx - 14, top + 26, cx + 14, top + 26}, {cx, top + 42, cx - 12, top + 58}, {cx, top + 42, cx + 12, top + 58}};
      for (double[] s : segments) r.line(List.of(new Point(s[0], s[1]), new Point(s[2], s[3])), fg);
      r.text(cx, n.y + n.h - 10, n.str("name"), fg, "center", false, false, false);
    } else if (n instanceof UseCaseNode) {
      r.ellipse(n.x, n.y, n.w, n.h, fill, fg);
      r.text(n.x + n.w / 2, n.y + n.h / 2, n.str("name"), fg, "center", false, false, false);
    } else if (n instanceof StateNode) {
      r.rect(n.x, n.y, n.w, n.h, fill, fg, 1, 14, false);
      r.text(n.x + n.w / 2, n.y + n.h / 2, n.str("name"), fg, "center", false, false, false);
    } else if (n instanceof InitialStateNode) {
      r.ellipse(n.x, n.y, n.w, n.h, fg, fg);
    } else if (n instanceof FinalStateNode) {
      r.ellipse(n.x, n.y, n.w, n.h, fill, fg, 2);
      r.ellipse(n.x + 6, n.y + 6, n.w - 12, n.h - 12, fg, fg);
    } else if (n instanceof ImplicitParameterNode) {
      r.rect(n.x, n.y, n.w, ImplicitParameterNode.HEADER_H, fill, fg);
      r.text(n.x + n.w / 2, n.y + ImplicitParameterNode.HEADER_H / 2.0, n.str("name"), fg, "center", false, false, true);
      r.line(List.of(new Point(n.x + n.w / 2, n.y + ImplicitParameterNode.HEADER_H), new Point(n.x + n.w / 2, n.y + n.h)), fg, 1, true);
    } else if (n instanceof CallNode) {
      r.rect(n.x, n.y, n.w, n.h, fill, fg);
      if (Boolean.TRUE.equals(n.get("open_bottom"))) {
        r.rect(n.x, n.y + n.h - 2, n.w, 2, fill, fill);
        r.line(List.of(new Point(n.x + n.w / 2, n.y + n.h), new Point(n.x + n.w / 2, n.y + n.h + 14)), fg, 1, true);
      }
    }
  }

  // ---------- arestas ----------
  private static void head(Renderer r, Point tip, Point origin, String style, String color, String background) {
    double len = Math.hypot(tip.x() - origin.x(), tip.y() - origin.y());
    if (len == 0) len = 1;
    double ux = (tip.x() - origin.x()) / len, uy = (tip.y() - origin.y()) / len, px = -uy, py = ux, size = 11;
    Point back = new Point(tip.x() - ux * size, tip.y() - uy * size);
    Point wing1 = new Point(back.x() + px * size / 2.2, back.y() + py * size / 2.2);
    Point wing2 = new Point(back.x() - px * size / 2.2, back.y() - py * size / 2.2);
    switch (style) {
      case "open" -> r.line(List.of(wing1, tip, wing2), color);
      case "filled" -> r.polygon(List.of(tip, wing1, wing2), color, color, 1);
      case "triangle" -> r.polygon(List.of(tip, wing1, wing2), background, color, 1);
      case "diamond", "diamond_filled" -> {
        Point far = new Point(tip.x() - ux * size * 1.8, tip.y() - uy * size * 1.8);
        r.polygon(List.of(tip, wing1, far, wing2), style.equals("diamond_filled") ? color : background, color, 1);
      }
      case "dot" -> r.ellipse(tip.x() - 4, tip.y() - 4, 8, 8, color, color);
      default -> { }
    }
  }

  private static EdgeStyle style(Edge e) {
    EdgeKind k = e.kind;
    String direction = String.valueOf(e.props.getOrDefault("directionality", ""));
    boolean both = direction.equals("Bidirectional"), arrow = direction.equals("Unidirectional") || both;
    return switch (k) {
      case INHERITANCE, REALIZATION -> new EdgeStyle(e.str("generalization_type").equals("Implementation"), both ? "open" : null, "triangle", null);
      case DEPENDENCY -> new EdgeStyle(e.str("generalization_type").equals("Implementation"), both ? "open" : null, "open", null);
      case ASSOCIATION -> new EdgeStyle(false, both ? "open" : null, arrow ? "open" : null, null);
      case AGGREGATION -> new EdgeStyle(false, "diamond", arrow ? "open" : null, null);
      case COMPOSITION -> new EdgeStyle(false, "diamond_filled", arrow ? "open" : null, null);
      case GENERALIZATION -> new EdgeStyle(false, null, "triangle", null);
      case USE_CASE_DEPENDENCY -> {
        String t = e.str("dependency_type");
        yield new EdgeStyle(true, null, "open", t.equals("Include") ? "«include»" : t.equals("Extend") ? "«extend»" : null);
      }
      case CALL, TRANSITION -> new EdgeStyle(false, null, "filled", null);
      case RETURN -> new EdgeStyle(true, null, "open", null);
      case REFERENCE -> new EdgeStyle(false, "dot", "filled", null);
      case NOTE_CONNECTOR -> new EdgeStyle(true, null, null, null);
      default -> new EdgeStyle(false, null, null, null);
    };
  }

  private static void drawEdge(Diagram d, Edge e, Renderer r, Palette pal) {
    EdgePaths.EdgePath path = EdgePaths.edgePath(d, e);
    List<Point> pts = path.points();
    EdgeStyle st = style(e);
    String fg = pal.foreground(), bg = pal.nodeFill();
    r.line(pts, fg, 1, st.dashed());
    if (st.endHead() != null) head(r, pts.get(pts.size() - 1), pts.get(pts.size() - 2), st.endHead(), fg, bg);
    if (st.startHead() != null) head(r, pts.get(0), pts.get(1), st.startHead(), fg, bg);
    Point mid = path.middle();
    String middle = st.fixedLabel() != null ? st.fixedLabel() : String.valueOf(e.props.getOrDefault("middle_label", ""));
    if (!middle.isEmpty()) line(r, mid.x(), mid.y() - 9, middle, fg, "center", false);
    String startLabel = String.valueOf(e.props.getOrDefault("start_label", "")), endLabel = String.valueOf(e.props.getOrDefault("end_label", ""));
    if (!startLabel.isEmpty()) line(r, path.startLabel().x(), path.startLabel().y(), startLabel, fg, "center", false);
    if (!endLabel.isEmpty()) line(r, path.endLabel().x(), path.endLabel().y(), endLabel, fg, "center", false);
  }
}
