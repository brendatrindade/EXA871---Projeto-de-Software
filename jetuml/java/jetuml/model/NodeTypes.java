package jetuml.model;

import java.util.Map;

/** Registro dos tipos de nó, usado ao abrir arquivos e colar. */
public final class NodeTypes {
  private interface Factory { Node create(double x, double y); }

  private static final Map<String, Factory> FACTORIES = Map.ofEntries(
      Map.entry(ClassNode.TYPE, ClassNode::new), Map.entry(InterfaceNode.TYPE, InterfaceNode::new),
      Map.entry(PackageNode.TYPE, PackageNode::new), Map.entry(PackageDescriptionNode.TYPE, PackageDescriptionNode::new),
      Map.entry(NoteNode.TYPE, NoteNode::new), Map.entry(FieldNode.TYPE, FieldNode::new),
      Map.entry(ObjectNode.TYPE, ObjectNode::new), Map.entry(ActorNode.TYPE, ActorNode::new),
      Map.entry(UseCaseNode.TYPE, UseCaseNode::new), Map.entry(StateNode.TYPE, StateNode::new),
      Map.entry(InitialStateNode.TYPE, InitialStateNode::new), Map.entry(FinalStateNode.TYPE, FinalStateNode::new),
      Map.entry(ImplicitParameterNode.TYPE, ImplicitParameterNode::new), Map.entry(CallNode.TYPE, CallNode::new),
      Map.entry(PointNode.TYPE, PointNode::new));

  private NodeTypes() { }

  public static boolean known(String type) { return FACTORIES.containsKey(type); }

  public static Node create(String type, double x, double y) {
    Factory factory = FACTORIES.get(type);
    if (factory == null) throw new ValidationException("Tipo de nó desconhecido: " + type);
    return factory.create(x, y);
  }
}
