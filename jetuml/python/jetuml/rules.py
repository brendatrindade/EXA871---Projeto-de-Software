"""Validação das conexões por tipo de diagrama (RF18, RF19, RF30)."""
from __future__ import annotations

from .edges import EdgeKind as K
from .errors import ValidationError
from .nodes import CallNode, FieldNode, Node, NoteNode, ObjectNode, PointNode
from . import sequence

CLASS_ENDS = {
    K.DEPENDENCY: ("Class", "Interface", "Package"),
    K.INHERITANCE: ("Class", "Interface"),
    K.REALIZATION: ("Class", "Interface"),
    K.ASSOCIATION: ("Class", "Interface"),
    K.AGGREGATION: ("Class", "Interface"),
    K.COMPOSITION: ("Class", "Interface"),
}
STATE_TYPES = ("State", "InitialState", "FinalState")
USE_CASE_TYPES = ("Actor", "UseCase")
BACK_AND_FORTH = (K.INHERITANCE, K.DEPENDENCY, K.AGGREGATION, K.ASSOCIATION)
SELF_FORBIDDEN_CLASS = {K.INHERITANCE: "auto-herança", K.REALIZATION: "auto-herança", K.DEPENDENCY: "auto-dependência"}


def _same(edges, kind, a, b):
    return [e for e in edges if e.kind is kind and e.start is a and e.end is b]


def check_edge(d, kind: K, start: Node, end: Node, ignore=None) -> None:
    """Levanta ValidationError se a aresta não puder existir; não altera o diagrama."""
    edges = [e for e in d.edges if e is not ignore]
    if kind is K.NOTE_CONNECTOR:
        return _note_connector(start, end, edges)
    if isinstance(start, (NoteNode, PointNode)) or isinstance(end, (NoteNode, PointNode)):
        raise ValidationError("Notas só se ligam com Note Connector.")
    {"Class": _class, "State": _state, "Object": _object, "Use Case": _use_case, "Sequence": _sequence}[d.kind](
        edges, kind, start, end)


def _no_duplicate(edges, kind, a, b):
    if _same(edges, kind, a, b):
        raise ValidationError(f"Já existe uma aresta {kind.value} entre estes dois elementos.")


def _note_connector(start, end, edges):
    if isinstance(start, NoteNode) == isinstance(end, NoteNode) and not isinstance(end, PointNode):
        raise ValidationError("Note Connector liga uma nota a outro elemento.")
    if isinstance(end, PointNode) and not isinstance(start, NoteNode):
        raise ValidationError("Somente uma nota pode ser ancorada a um ponto.")
    if any(e.kind is K.NOTE_CONNECTOR and {e.start, e.end} == {start, end} for e in edges):
        raise ValidationError("Já existe um Note Connector entre estes elementos.")


def _class(edges, kind, a, b):
    if kind not in CLASS_ENDS:
        raise ValidationError("Tipo de aresta inválido em diagrama de classes.")
    if a.TYPE not in CLASS_ENDS[kind] or b.TYPE not in CLASS_ENDS[kind]:
        raise ValidationError(f"{kind.value} não pode ligar estes elementos.")
    if a is b and kind in SELF_FORBIDDEN_CLASS:
        raise ValidationError(f"{SELF_FORBIDDEN_CLASS[kind].capitalize()} não é permitida.")
    _no_duplicate(edges, kind, a, b)
    if kind in BACK_AND_FORTH and a is not b and _same(edges, kind, b, a):
        raise ValidationError(f"Ciclo direto de {kind.value} não é permitido.")
    other = {K.ASSOCIATION: K.AGGREGATION, K.AGGREGATION: K.ASSOCIATION}.get(kind)
    if other and any(e.kind is other and {e.start, e.end} == {a, b} for e in edges):
        raise ValidationError("Não é possível combinar Associação e Agregação entre as mesmas classes.")


def _state(edges, kind, a, b):
    if kind is not K.TRANSITION or a.TYPE not in STATE_TYPES or b.TYPE not in STATE_TYPES:
        raise ValidationError("Transições ligam apenas estados.")
    if a.TYPE == "FinalState":
        raise ValidationError("Não há transições partindo de um estado final.")
    if b.TYPE == "InitialState":
        raise ValidationError("Não há transições chegando em um estado inicial.")
    if len(_same(edges, kind, a, b)) >= 2:
        raise ValidationError("Limite de duas transições nesta direção." if a is not b else "Limite de duas autotransições.")


def _object(edges, kind, a, b):
    if kind is K.REFERENCE:
        if not isinstance(a, FieldNode) or not isinstance(b, ObjectNode):
            raise ValidationError("Uma referência liga um campo a um objeto.")
        _no_duplicate(edges, kind, a, b)
        if any(e.kind is K.REFERENCE and e.start is a for e in edges):
            raise ValidationError("Este campo já possui uma referência.")
    elif kind is K.COLLABORATION:
        if not isinstance(a, ObjectNode) or not isinstance(b, ObjectNode):
            raise ValidationError("Colaborações ligam objetos.")
        if a is b:
            raise ValidationError("Auto-colaboração não é permitida.")
        _no_duplicate(edges, kind, a, b)
        if _same(edges, kind, b, a):
            raise ValidationError("Ciclo de colaboração entre dois objetos não é permitido.")
    else:
        raise ValidationError("Tipo de aresta inválido em diagrama de objetos.")


def _use_case(edges, kind, a, b):
    if kind not in (K.ASSOCIATION, K.USE_CASE_DEPENDENCY, K.GENERALIZATION):
        raise ValidationError("Tipo de aresta inválido em diagrama de casos de uso.")
    if a.TYPE not in USE_CASE_TYPES or b.TYPE not in USE_CASE_TYPES:
        raise ValidationError("Estas arestas ligam atores e casos de uso.")
    if a is b:
        raise ValidationError("Autoarestas não são permitidas para atores e casos de uso.")
    if kind is K.USE_CASE_DEPENDENCY and (a.TYPE != "UseCase" or b.TYPE != "UseCase"):
        raise ValidationError("Include e Extend ligam casos de uso.")
    if kind is K.GENERALIZATION and a.TYPE != b.TYPE:
        raise ValidationError("Generalização liga elementos do mesmo tipo.")
    _no_duplicate(edges, kind, a, b)


def _sequence(edges, kind, a, b):
    if kind not in (K.CALL, K.RETURN) or not isinstance(a, CallNode) or not isinstance(b, CallNode):
        raise ValidationError("Chamadas e retornos ligam apenas caixas de ativação.")


def validate_diagram(d) -> None:
    """Valida um diagrama inteiro (usado ao abrir arquivos e ao colar)."""
    from .catalog import allowed_edge_kinds, allowed_node_types
    ids = set()
    for n in d.nodes:
        if n.TYPE not in allowed_node_types(d.kind):
            raise ValidationError(f"Elemento {n.TYPE} incompatível com diagrama {d.kind}.")
        if n.id in ids:
            raise ValidationError("Identificadores de nó duplicados.")
        ids.add(n.id)
        if isinstance(n, FieldNode) and not isinstance(n.parent, ObjectNode):
            raise ValidationError("Um campo deve pertencer a um objeto.")        # RF25
        if n.parent is not None and not n.parent.accepts(n):
            raise ValidationError(f"{n.parent.TYPE} não aceita filhos do tipo {n.TYPE}.")
    for e in d.edges:
        if e.kind not in allowed_edge_kinds(d.kind):
            raise ValidationError(f"Aresta {e.kind.value} incompatível com diagrama {d.kind}.")
        if d.kind != "Sequence" or e.kind is K.NOTE_CONNECTOR:
            check_edge(d, e.kind, e.start, e.end, ignore=e)
    if d.kind == "Sequence":
        sequence.validate_structure(d)
