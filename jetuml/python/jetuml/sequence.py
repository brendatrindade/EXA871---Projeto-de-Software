"""Diagrama de sequência: layout vertical automático, criação de chamadas/retornos e validação (RF27–RF31)."""
from __future__ import annotations

from collections import defaultdict

from .edges import Edge, EdgeKind
from .errors import ValidationError
from .nodes import HEADER_H, CallNode, ImplicitParameterNode

TOP, BOX_W, NEST = 20, 16, 8
CREATE_LABEL = "«create»"


# ---------- consultas ----------
def incoming(d, node):
    return next((e for e in d.edges if e.kind is EdgeKind.CALL and e.end is node), None)

def outgoing(d, node):
    return [e for e in d.edges if e.kind is EdgeKind.CALL and e.start is node]

def has_return(d, node) -> bool:
    return any(e.kind is EdgeKind.RETURN and e.start is node for e in d.edges)

def orphan_roots(d) -> list[CallNode]:
    return [n for n in d.nodes if isinstance(n, CallNode) and incoming(d, n) is None]

def reaches(d, ancestor, node) -> bool:
    """True se `node` é alcançável a partir de `ancestor` seguindo chamadas."""
    stack, seen = [ancestor], set()
    while stack:
        current = stack.pop()
        if current is node:
            return True
        if current not in seen:
            seen.add(current)
            stack.extend(e.end for e in outgoing(d, current))
    return False


# ---------- layout ----------
def relayout(d) -> None:
    lifelines = [n for n in d.nodes if isinstance(n, ImplicitParameterNode)]
    for life in lifelines:
        life.y = TOP
    outgoing_by = defaultdict(list)
    for e in d.edges:
        if e.kind is EdgeKind.CALL:
            outgoing_by[e.start].append(e)
    open_count: dict = defaultdict(int)
    bottoms: dict = {}

    def place(node: CallNode, top: float) -> float:
        life = node.parent
        node.x = life.x + life.w / 2 - BOX_W / 2 + open_count[life] * NEST
        node.y, node.w = top, BOX_W
        open_count[life] += 1
        y = top + 20
        for edge in outgoing_by[node]:
            row = y + 10
            edge.row_y = row
            callee = edge.end
            if edge.props.get("constructor"):
                callee.parent.y = row - HEADER_H / 2
                callee_top = callee.parent.y + HEADER_H
            else:
                callee_top = row - 10
            y = place(callee, callee_top) + 10
        bottom = max(top + 30, y)
        node.h = bottom - top
        open_count[life] -= 1
        bottoms[life] = max(bottoms.get(life, 0), bottom)
        return bottom

    cursor = TOP + HEADER_H + 10
    for root in sorted(orphan_roots(d), key=lambda n: n.id):
        cursor = place(root, cursor) + 40
    for e in d.edges:
        if e.kind is EdgeKind.RETURN:
            e.row_y = e.start.y + e.start.h - 10
    for life in lifelines:
        life.h = max(life.MIN_H, bottoms.get(life, life.y + HEADER_H + 40) + 40 - life.y)


# ---------- criação ----------
def _root_on(d, life):
    return next((n for n in orphan_roots(d) if n.parent is life), None)


def connect_call(d, start, end, y: float) -> Edge:
    if not isinstance(start, (CallNode, ImplicitParameterNode)):
        raise ValidationError("Chamadas partem de linhas de vida ou de caixas de ativação.")
    if isinstance(end, CallNode):
        mode, life = "box", end.parent
    elif isinstance(end, ImplicitParameterNode):
        mode, life = ("constructor" if end.in_header(y) else "body"), end
    else:
        raise ValidationError("Uma chamada deve terminar em uma linha de vida ou em um parâmetro implícito.")
    caller, new_root = start, False
    if isinstance(start, ImplicitParameterNode):
        caller = _root_on(d, start)
        new_root = caller is None
        if new_root and orphan_roots(d):
            raise ValidationError("O diagrama só pode ter uma chamada raiz sem chamador.")
    if caller is not None and has_return(d, caller):
        raise ValidationError("Salto no fluxo de controle.", silent=True)       # RF30: falha silenciosa
    if mode == "box":
        if end is start:
            mode = "self"                                                         # RF28
        elif incoming(d, end) is not None:
            raise ValidationError("Uma caixa de ativação não pode receber chamadas de vários chamadores.")
        elif caller is not None and reaches(d, end, caller):
            raise ValidationError("A chamada criaria um ciclo de controle.")
    if mode == "constructor":
        if end.children:
            raise ValidationError("O parâmetro implícito já possui caixas de ativação.")
        if caller is not None and caller.parent is end:
            raise ValidationError("Um objeto não pode criar a si mesmo.")
    if new_root:
        caller = d.add_node(CallNode(), parent=start)
    if mode == "box":
        callee = end
    else:
        callee = d.add_node(CallNode(), parent=start.parent if mode == "self" else life)
    edge = Edge(EdgeKind.CALL, caller, callee)
    if mode == "constructor":
        edge.props.update(constructor=True, middle_label=CREATE_LABEL)
    return d.add_edge(edge)


def connect_return(d, start, end) -> Edge:
    if not isinstance(start, CallNode) or not isinstance(end, CallNode):
        raise ValidationError("Retornos conectam caixas de ativação.")
    call = next((e for e in d.edges if e.kind is EdgeKind.CALL and e.start is end and e.end is start), None)
    if call is None or has_return(d, start):
        raise ValidationError("Salto no fluxo de controle.", silent=True)
    return d.add_edge(Edge(EdgeKind.RETURN, start, end))


# ---------- exclusão e validação ----------
def expand_deletion(d, nodes: set, edges: set) -> tuple[set, set]:
    """Apagar uma chamada ou caixa apaga também o que dependia dela."""
    nodes, edges = set(nodes), set(edges)
    for n in list(nodes):
        nodes.update(n.descendants())
    work = [e for e in edges if e.kind is EdgeKind.CALL] + [e for n in nodes for e in outgoing(d, n)]
    while work:
        edge = work.pop()
        edges.add(edge)
        callee = edge.end
        if callee not in nodes and isinstance(callee, CallNode):
            nodes.add(callee)
            work.extend(outgoing(d, callee))
    return nodes, edges


def prune_isolated(d) -> None:
    for n in [n for n in d.nodes if isinstance(n, CallNode)]:
        if n in d.nodes and not d.edges_of(n):
            d.remove_node(n)


def validate_structure(d) -> None:
    for e in d.edges:
        if e.kind in (EdgeKind.CALL, EdgeKind.RETURN) and not (isinstance(e.start, CallNode) and isinstance(e.end, CallNode)):
            raise ValidationError("Chamadas e retornos devem ligar caixas de ativação.")
    seen = set()
    for e in d.edges:
        if e.kind is EdgeKind.CALL:
            if e.end in seen:
                raise ValidationError("Uma caixa de ativação recebe chamadas de vários chamadores.")
            seen.add(e.end)
        elif e.kind is EdgeKind.RETURN and not any(c.kind is EdgeKind.CALL and c.start is e.end and c.end is e.start for c in d.edges):
            raise ValidationError("Retorno sem a chamada correspondente.")
    if len(orphan_roots(d)) > 1:
        raise ValidationError("Mais de uma chamada raiz sem chamador.")
