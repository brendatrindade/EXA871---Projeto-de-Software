package jetuml.catalog;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jetuml.model.*;

/** Ferramentas por tipo de diagrama (RF07), atalhos 1–9, 0, A… (RF08) e descrições (RF10). */
public final class Catalog {
  private static final String SHORTCUTS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final Map<String, List<Tool>> TOOLS = new LinkedHashMap<>();

  private static Tool select() { return new Tool("Select", "select", "↖", "Seleciona, move e edita elementos", null, null, null, Map.of()); }
  private static Tool clipboard() { return new Tool("To Clipboard", "clipboard", "⎘", "Copia a imagem do diagrama para a área de transferência", null, null, null, Map.of()); }
  private static Tool node(String name, String icon, String type, Tool.NodeFactory factory, String description) {
    return new Tool(name, "node", icon, description, type, factory, null, Map.of());
  }
  private static Tool edge(String name, String icon, EdgeKind kind, String description) { return edge(name, icon, kind, description, Map.of()); }
  private static Tool edge(String name, String icon, EdgeKind kind, String description, Map<String, Object> preset) {
    return new Tool(name, "edge", icon, description, null, null, kind, preset);
  }
  private static Tool note() { return node("Note", "▤", NoteNode.TYPE, NoteNode::new, "Nota com informações complementares"); }
  private static Tool noteConnector() { return edge("Note Connector", "┄", EdgeKind.NOTE_CONNECTOR, "Liga uma nota a um nó ou a um ponto fixo"); }

  static {
    TOOLS.put("Class", List.of(select(), node("Class", "▭", ClassNode.TYPE, ClassNode::new, "Classe UML com atributos e métodos"),
        node("Interface", "◫", InterfaceNode.TYPE, InterfaceNode::new, "Interface UML"),
        node("Package", "▣", PackageNode.TYPE, PackageNode::new, "Pacote que pode conter outros elementos"),
        node("Package Description", "▥", PackageDescriptionNode.TYPE, PackageDescriptionNode::new, "Pacote com descrição textual"),
        note(), edge("Dependency", "⇢", EdgeKind.DEPENDENCY, "Dependência (seta tracejada)"),
        edge("Inheritance", "△", EdgeKind.INHERITANCE, "Herança (generalização)"),
        edge("Realization", "⇡", EdgeKind.REALIZATION, "Realização de interface"),
        edge("Association", "—", EdgeKind.ASSOCIATION, "Associação"), edge("Aggregation", "◇", EdgeKind.AGGREGATION, "Agregação"),
        edge("Composition", "◆", EdgeKind.COMPOSITION, "Composição"), noteConnector(), clipboard()));
    TOOLS.put("Sequence", List.of(select(), node("Implicit Parameter", "⏐", ImplicitParameterNode.TYPE, ImplicitParameterNode::new, "Parâmetro implícito com linha de vida"),
        note(), edge("Call", "→", EdgeKind.CALL, "Chamada de método entre linhas de vida"),
        edge("Return", "⇠", EdgeKind.RETURN, "Retorno de uma chamada"), noteConnector(), clipboard()));
    TOOLS.put("State", List.of(select(), node("State", "▢", StateNode.TYPE, StateNode::new, "Estado"),
        node("Initial State", "●", InitialStateNode.TYPE, InitialStateNode::new, "Estado inicial"),
        node("Final State", "◉", FinalStateNode.TYPE, FinalStateNode::new, "Estado final"), note(),
        edge("Transition", "↪", EdgeKind.TRANSITION, "Transição entre estados"), noteConnector(), clipboard()));
    TOOLS.put("Object", List.of(select(), node("Object", "▭", ObjectNode.TYPE, ObjectNode::new, "Objeto"),
        node("Field", "≡", FieldNode.TYPE, FieldNode::new, "Campo dentro de um objeto"), note(),
        edge("Reference", "•→", EdgeKind.REFERENCE, "Referência da parte Value de um campo a um objeto"),
        edge("Collaboration", "↔", EdgeKind.COLLABORATION, "Colaboração entre objetos"), noteConnector(), clipboard()));
    TOOLS.put("Use Case", List.of(select(), node("Actor", "☺", ActorNode.TYPE, ActorNode::new, "Ator"),
        node("Use Case", "◯", UseCaseNode.TYPE, UseCaseNode::new, "Caso de uso"), note(),
        edge("Association", "—", EdgeKind.ASSOCIATION, "Associação"),
        edge("Include", "⇢", EdgeKind.USE_CASE_DEPENDENCY, "Dependência «include»", Map.of("dependency_type", "Include")),
        edge("Extend", "⇢", EdgeKind.USE_CASE_DEPENDENCY, "Dependência «extend»", Map.of("dependency_type", "Extend")),
        edge("Generalization", "△", EdgeKind.GENERALIZATION, "Generalização"), noteConnector(), clipboard()));
  }

  private Catalog() { }

  public static List<Tool> toolsFor(String diagramKind) { return TOOLS.get(diagramKind); }

  public static Tool tool(String diagramKind, String name) {
    for (Tool t : TOOLS.get(diagramKind)) if (t.name().equals(name)) return t;
    throw new IllegalArgumentException("Ferramenta desconhecida: " + name);
  }

  public static String shortcut(int index) { return String.valueOf(SHORTCUTS.charAt(index)); }

  public static Tool toolForShortcut(String diagramKind, String key) {
    int index = SHORTCUTS.indexOf(key.toUpperCase());
    List<Tool> tools = TOOLS.get(diagramKind);
    return index >= 0 && index < tools.size() ? tools.get(index) : null;
  }

  public static Set<String> allowedNodeTypes(String diagramKind) {
    Set<String> types = new HashSet<>();
    for (Tool t : TOOLS.get(diagramKind)) if (t.nodeType() != null) types.add(t.nodeType());
    types.add(PointNode.TYPE);
    if (diagramKind.equals("Sequence")) types.add(CallNode.TYPE);
    return types;
  }

  public static Set<EdgeKind> allowedEdgeKinds(String diagramKind) {
    Set<EdgeKind> kinds = EnumSet.noneOf(EdgeKind.class);
    for (Tool t : TOOLS.get(diagramKind)) if (t.edgeKind() != null) kinds.add(t.edgeKind());
    return kinds;
  }

  public static List<String> toolNames(String diagramKind) {
    List<String> names = new ArrayList<>();
    for (Tool t : TOOLS.get(diagramKind)) names.add(t.name());
    return names;
  }
}
