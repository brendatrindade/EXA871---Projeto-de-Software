"""Modelo de um diagrama: nós (planos, com vínculo pai/filho), arestas, layout e consultas."""
from __future__ import annotations

from .edges import Edge, EdgeKind
from .geometry import Rect
from .nodes import CallNode, Node, PointNode
from . import sequence

DIAGRAM_KINDS = ("Class", "Sequence", "State", "Object", "Use Case")


class Diagram:
    def __init__(self, kind: str, size: tuple[int, int] = (900, 650)) -> None:
        if kind not in DIAGRAM_KINDS:
            raise ValueError(f"Tipo de diagrama desconhecido: {kind}")
        self.kind = kind
        self.size = size
        self.nodes: list[Node] = []
        self.edges: list[Edge] = []
        self._next_id = 1

    # ---------- construção ----------
    def add_node(self, node: Node, parent: Node | None = None) -> Node:
        node.id = self._next_id
        self._next_id += 1
        self.nodes.append(node)
        if parent is not None:
            node.parent = parent
            parent.children.append(node)
        self.relayout()
        return node

    def add_edge(self, edge: Edge) -> Edge:
        edge.id = self._next_id
        self._next_id += 1
        self.edges.append(edge)
        self.relayout()
        return edge

    def remove_edge(self, edge: Edge) -> None:
        if edge in self.edges:
            self.edges.remove(edge)
        self._drop_orphan_points()

    def remove_node(self, node: Node) -> None:
        doomed = [node, *node.descendants()]
        if node.parent is not None:
            node.parent.children.remove(node)
        for n in doomed:
            if n in self.nodes:
                self.nodes.remove(n)
        self.edges = [e for e in self.edges if e.start not in doomed and e.end not in doomed]
        self._drop_orphan_points()

    def _drop_orphan_points(self) -> None:
        used = {n for e in self.edges for n in (e.start, e.end)}
        self.nodes = [n for n in self.nodes if not isinstance(n, PointNode) or n in used]

    def edges_of(self, node: Node) -> list[Edge]:
        return [e for e in self.edges if e.start is node or e.end is node]

    # ---------- consultas ----------
    def roots(self) -> list[Node]:
        return [n for n in self.nodes if n.parent is None]

    def draw_order(self) -> list[Node]:
        """Pais antes de filhos; filhos desenhados por cima."""
        ordered: list[Node] = []
        def visit(node: Node) -> None:
            ordered.append(node)
            for child in node.children:
                visit(child)
        for root in self.roots():
            visit(root)
        return ordered

    def node_at(self, x: float, y: float) -> Node | None:
        for node in reversed(self.draw_order()):
            if node.SELECTABLE and node.bounds.contains(x, y):
                return node
        return None

    def container_at(self, x: float, y: float, candidate: Node, exclude: Node | None = None) -> Node | None:
        """Nó mais interno sob (x, y) que aceita `candidate` como filho."""
        banned = {exclude, *exclude.descendants()} if exclude is not None else set()
        for node in reversed(self.draw_order()):
            if node not in banned and node.accepts(candidate) and node.bounds.contains(x, y):
                return node
        return None

    def edge_at(self, x: float, y: float, tolerance: float = 5) -> Edge | None:
        from .layout import edge_path, path_distance
        for edge in reversed(self.edges):
            if path_distance(x, y, edge_path(self, edge).points) <= tolerance:
                return edge
        return None

    def content_bounds(self) -> Rect | None:
        from .layout import edge_path
        rects = [n.bounds for n in self.nodes if n.SELECTABLE or n.parent is None]
        for e in self.edges:
            pts = edge_path(self, e).points
            xs, ys = [p.x for p in pts], [p.y for p in pts]
            rects.append(Rect(min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)))
        if not rects:
            return None
        result = rects[0]
        for r in rects[1:]:
            result = result.union(r)
        return result

    # ---------- edição estrutural ----------
    def move_node(self, node: Node, dx: float, dy: float) -> None:
        for n in (node, *node.descendants()):
            n.x += dx
            n.y += dy

    def detach(self, node: Node) -> None:
        if node.parent is not None:
            node.parent.children.remove(node)
            node.parent = None

    def attach(self, node: Node, parent: Node) -> None:
        self.detach(node)
        node.parent = parent
        parent.children.append(node)

    def relayout(self) -> None:
        """Tamanhos, posições de campos, crescimento de pacotes e layout de sequência."""
        def fit(node: Node) -> None:
            for child in node.children:
                fit(child)
            node.fit()
        for root in self.roots():
            fit(root)
        if self.kind == "Sequence":
            sequence.relayout(self)

    def grow_to_content(self, margin: int = 40) -> None:
        bounds = self.content_bounds()
        if bounds is not None:
            self.size = (max(self.size[0], int(bounds.right + margin)), max(self.size[1], int(bounds.bottom + margin)))

    # ---------- persistência em memória (usada pelo histórico) ----------
    def to_dict(self) -> dict:
        from .serialization import diagram_to_dict
        return diagram_to_dict(self)

    def restore(self, data: dict) -> None:
        from .serialization import diagram_from_dict
        other = diagram_from_dict(data, validate=False)
        self.kind, self.nodes, self.edges, self._next_id = other.kind, other.nodes, other.edges, other._next_id
