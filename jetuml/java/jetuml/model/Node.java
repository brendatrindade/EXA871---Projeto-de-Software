package jetuml.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jetuml.geometry.Rect;
import jetuml.text.TextFormat;

/** Nó de um diagrama: posição, tamanho calculado, propriedades e hierarquia pai/filho. */
public abstract class Node {
  public int id;
  public double x, y, w, h;
  public Node parent;
  public final List<Node> children = new ArrayList<>();
  public final Map<String, Object> props = new LinkedHashMap<>();

  protected Node(double x, double y) {
    this.x = x;
    this.y = y;
    this.w = minW();
    this.h = minH();
    for (Field field : fields()) props.put(field.name(), field.defaultValue());
  }

  public abstract String type();
  public List<Field> fields() { return List.of(); }
  /** Tipos de filhos aceitos. */
  public List<String> accepts() { return List.of(); }
  public boolean movable() { return true; }
  public boolean selectable() { return true; }
  public double minW() { return 100; }
  public double minH() { return 60; }
  /** Recalcula w/h a partir das propriedades. */
  public void fit() { }

  public Rect bounds() { return new Rect(x, y, w, h); }
  public Object get(String name) { return props.get(name); }
  public void set(String name, Object value) { props.put(name, value); }
  public String str(String name) { Object v = props.get(name); return v == null ? "" : v.toString(); }
  public boolean accepts(Node node) { return accepts().contains(node.type()); }

  public List<Node> ancestors() {
    List<Node> result = new ArrayList<>();
    for (Node n = parent; n != null; n = n.parent) result.add(n);
    return result;
  }

  public List<Node> descendants() {
    List<Node> result = new ArrayList<>();
    for (Node child : children) {
      result.add(child);
      result.addAll(child.descendants());
    }
    return result;
  }

  @SafeVarargs
  protected final void textBox(int header, List<String>... groups) {
    int widest = 0;
    for (List<String> group : groups) for (String line : group) widest = Math.max(widest, TextFormat.textWidth(line));
    w = Math.max(minW(), widest + 20);
    h = header;
    for (List<String> group : groups) h += Math.max(20, group.size() * TextFormat.lineHeight() + 10);
  }
}
