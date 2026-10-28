package jetuml.catalog;

import java.util.Map;
import jetuml.model.EdgeKind;
import jetuml.model.Node;

/** Ferramenta da barra lateral (RF07). category: select | node | edge | clipboard. */
public record Tool(String name, String category, String icon, String description, String nodeType,
                   NodeFactory factory, EdgeKind edgeKind, Map<String, Object> preset) {
  public interface NodeFactory { Node create(double x, double y); }

  public Node createNode(double x, double y) { return factory.create(x, y); }
}
