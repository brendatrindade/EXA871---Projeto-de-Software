package jetuml.rules;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import jetuml.catalog.Catalog;
import jetuml.model.*;
import static jetuml.model.EdgeKind.*;

/** Validação das conexões por tipo de diagrama (RF18, RF19, RF30). */
public final class Rules {
  private static final Map<EdgeKind, List<String>> CLASS_ENDS = Map.of(
      DEPENDENCY, List.of("Class", "Interface", "Package"), INHERITANCE, List.of("Class", "Interface"),
      REALIZATION, List.of("Class", "Interface"), ASSOCIATION, List.of("Class", "Interface"),
      AGGREGATION, List.of("Class", "Interface"), COMPOSITION, List.of("Class", "Interface"));
  private static final List<String> STATE_TYPES = List.of("State", "InitialState", "FinalState");
  private static final List<String> USE_CASE_TYPES = List.of("Actor", "UseCase");
  private static final List<EdgeKind> BACK_AND_FORTH = List.of(INHERITANCE, DEPENDENCY, AGGREGATION, ASSOCIATION);
  private static final Map<EdgeKind, String> SELF_FORBIDDEN_CLASS = Map.of(
      INHERITANCE, "Auto-herança", REALIZATION, "Auto-herança", DEPENDENCY, "Auto-dependência");

  private Rules() { }

  private static boolean same(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    return count(edges, kind, a, b) > 0;
  }

  private static long count(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    return edges.stream().filter(e -> e.kind == kind && e.start == a && e.end == b).count();
  }

  private static boolean pair(Edge e, Node a, Node b) { return (e.start == a && e.end == b) || (e.start == b && e.end == a); }

  /** Levanta ValidationException se a aresta não puder existir; não altera o diagrama. */
  public static void checkEdge(Diagram d, EdgeKind kind, Node start, Node end, Edge ignore) {
    List<Edge> edges = d.edges.stream().filter(e -> e != ignore).toList();
    if (kind == NOTE_CONNECTOR) {
      noteConnector(start, end, edges);
      return;
    }
    boolean notes = start instanceof NoteNode || start instanceof PointNode || end instanceof NoteNode || end instanceof PointNode;
    if (notes) throw new ValidationException("Notas só se ligam com Note Connector.");
    switch (d.kind) {
      case "Class" -> classRules(edges, kind, start, end);
      case "State" -> stateRules(edges, kind, start, end);
      case "Object" -> objectRules(edges, kind, start, end);
      case "Use Case" -> useCaseRules(edges, kind, start, end);
      default -> {
        if ((kind != CALL && kind != RETURN) || !(start instanceof CallNode) || !(end instanceof CallNode)) {
          throw new ValidationException("Chamadas e retornos ligam apenas caixas de ativação.");
        }
      }
    }
  }

  public static void checkEdge(Diagram d, EdgeKind kind, Node start, Node end) { checkEdge(d, kind, start, end, null); }

  private static void noDuplicate(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    if (same(edges, kind, a, b)) throw new ValidationException("Já existe uma aresta " + kind.value() + " entre estes dois elementos.");
  }

  private static void noteConnector(Node start, Node end, List<Edge> edges) {
    if ((start instanceof NoteNode) == (end instanceof NoteNode) && !(end instanceof PointNode)) {
      throw new ValidationException("Note Connector liga uma nota a outro elemento.");
    }
    if (end instanceof PointNode && !(start instanceof NoteNode)) {
      throw new ValidationException("Somente uma nota pode ser ancorada a um ponto.");
    }
    if (edges.stream().anyMatch(e -> e.kind == NOTE_CONNECTOR && pair(e, start, end))) {
      throw new ValidationException("Já existe um Note Connector entre estes elementos.");
    }
  }

  private static void classRules(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    List<String> ends = CLASS_ENDS.get(kind);
    if (ends == null) throw new ValidationException("Tipo de aresta inválido em diagrama de classes.");
    if (!ends.contains(a.type()) || !ends.contains(b.type())) throw new ValidationException(kind.value() + " não pode ligar estes elementos.");
    if (a == b && SELF_FORBIDDEN_CLASS.containsKey(kind)) throw new ValidationException(SELF_FORBIDDEN_CLASS.get(kind) + " não é permitida.");
    noDuplicate(edges, kind, a, b);
    if (BACK_AND_FORTH.contains(kind) && a != b && same(edges, kind, b, a)) {
      throw new ValidationException("Ciclo direto de " + kind.value() + " não é permitido.");
    }
    EdgeKind other = kind == ASSOCIATION ? AGGREGATION : kind == AGGREGATION ? ASSOCIATION : null;
    if (other != null && edges.stream().anyMatch(e -> e.kind == other && pair(e, a, b))) {
      throw new ValidationException("Não é possível combinar Associação e Agregação entre as mesmas classes.");
    }
  }

  private static void stateRules(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    if (kind != TRANSITION || !STATE_TYPES.contains(a.type()) || !STATE_TYPES.contains(b.type())) {
      throw new ValidationException("Transições ligam apenas estados.");
    }
    if (a.type().equals("FinalState")) throw new ValidationException("Não há transições partindo de um estado final.");
    if (b.type().equals("InitialState")) throw new ValidationException("Não há transições chegando em um estado inicial.");
    if (count(edges, kind, a, b) >= 2) {
      throw new ValidationException(a != b ? "Limite de duas transições nesta direção." : "Limite de duas autotransições.");
    }
  }

  private static void objectRules(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    if (kind == REFERENCE) {
      if (!(a instanceof FieldNode) || !(b instanceof ObjectNode)) throw new ValidationException("Uma referência liga um campo a um objeto.");
      noDuplicate(edges, kind, a, b);
      if (edges.stream().anyMatch(e -> e.kind == REFERENCE && e.start == a)) throw new ValidationException("Este campo já possui uma referência.");
    } else if (kind == COLLABORATION) {
      if (!(a instanceof ObjectNode) || !(b instanceof ObjectNode)) throw new ValidationException("Colaborações ligam objetos.");
      if (a == b) throw new ValidationException("Auto-colaboração não é permitida.");
      noDuplicate(edges, kind, a, b);
      if (same(edges, kind, b, a)) throw new ValidationException("Ciclo de colaboração entre dois objetos não é permitido.");
    } else {
      throw new ValidationException("Tipo de aresta inválido em diagrama de objetos.");
    }
  }

  private static void useCaseRules(List<Edge> edges, EdgeKind kind, Node a, Node b) {
    if (kind != ASSOCIATION && kind != USE_CASE_DEPENDENCY && kind != GENERALIZATION) {
      throw new ValidationException("Tipo de aresta inválido em diagrama de casos de uso.");
    }
    if (!USE_CASE_TYPES.contains(a.type()) || !USE_CASE_TYPES.contains(b.type())) {
      throw new ValidationException("Estas arestas ligam atores e casos de uso.");
    }
    if (a == b) throw new ValidationException("Autoarestas não são permitidas para atores e casos de uso.");
    if (kind == USE_CASE_DEPENDENCY && (!a.type().equals("UseCase") || !b.type().equals("UseCase"))) {
      throw new ValidationException("Include e Extend ligam casos de uso.");
    }
    if (kind == GENERALIZATION && !a.type().equals(b.type())) throw new ValidationException("Generalização liga elementos do mesmo tipo.");
    noDuplicate(edges, kind, a, b);
  }

  /** Valida um diagrama inteiro (usado ao abrir arquivos). */
  public static void validateDiagram(Diagram d) {
    Set<Integer> ids = new HashSet<>();
    Set<String> types = Catalog.allowedNodeTypes(d.kind);
    for (Node n : d.nodes) {
      if (!types.contains(n.type())) throw new ValidationException("Elemento " + n.type() + " incompatível com diagrama " + d.kind + ".");
      if (!ids.add(n.id)) throw new ValidationException("Identificadores de nó duplicados.");
      if (n instanceof FieldNode && !(n.parent instanceof ObjectNode)) throw new ValidationException("Um campo deve pertencer a um objeto.");   // RF25
      if (n.parent != null && !n.parent.accepts(n)) {
        throw new ValidationException(n.parent.type() + " não aceita filhos do tipo " + n.type() + ".");
      }
    }
    Set<EdgeKind> kinds = Catalog.allowedEdgeKinds(d.kind);
    for (Edge e : d.edges) {
      if (!kinds.contains(e.kind)) throw new ValidationException("Aresta " + e.kind.value() + " incompatível com diagrama " + d.kind + ".");
      if (!d.kind.equals("Sequence") || e.kind == NOTE_CONNECTOR) checkEdge(d, e.kind, e.start, e.end, e);
    }
    if (d.kind.equals("Sequence")) SequenceSupport.validateStructure(d);
  }
}
