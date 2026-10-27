"""Caminho automático das arestas (RF17): recalculado a partir dos nós, nunca armazenado."""
from __future__ import annotations

from dataclasses import dataclass, field

from .edges import Edge, EdgeKind
from .geometry import Point, boundary_point, segment_distance
from .nodes import HEADER_H, CallNode, FieldNode, ImplicitParameterNode

PARALLEL_GAP = 14


@dataclass
class EdgePath:
    points: list[Point]
    start_label: Point = field(default_factory=lambda: Point(0, 0))
    end_label: Point = field(default_factory=lambda: Point(0, 0))

    @property
    def middle(self) -> Point:
        a, b = self.points[len(self.points) // 2 - 1], self.points[len(self.points) // 2]
        return Point((a.x + b.x) / 2, (a.y + b.y) / 2)


def path_distance(x: float, y: float, points: list[Point]) -> float:
    return min((segment_distance(x, y, a, b) for a, b in zip(points, points[1:])), default=float("inf"))


def edge_path(d, edge: Edge) -> EdgePath:
    if edge.kind in (EdgeKind.CALL, EdgeKind.RETURN):
        points = _sequence_points(edge)
    elif edge.is_self_edge():
        points = _loop(d, edge)
    elif edge.kind is EdgeKind.REFERENCE and isinstance(edge.start, FieldNode):
        points = _reference(edge)
    else:
        points = _straight(d, edge)
    return EdgePath(points, start_label=_near(points[0], points[1]), end_label=_near(points[-1], points[-2]))


def _near(p: Point, toward: Point) -> Point:
    return Point(p.x + (10 if toward.x >= p.x else -10), p.y - 10)


def _straight(d, edge: Edge) -> list[Point]:
    s, t = edge.start, edge.end
    same = [e for e in d.edges if not e.is_self_edge() and {e.start, e.end} == {s, t}]
    offset = (same.index(edge) - (len(same) - 1) / 2) * PARALLEL_GAP if len(same) > 1 else 0
    a, b = sorted((s, t), key=lambda n: n.id)
    ax, ay, bx, by = a.bounds.center.x, a.bounds.center.y, b.bounds.center.x, b.bounds.center.y
    length = max(((bx - ax) ** 2 + (by - ay) ** 2) ** 0.5, 1)
    nx, ny = -(by - ay) / length * offset, (bx - ax) / length * offset
    p1 = boundary_point(s.bounds, t.bounds.center)
    p2 = boundary_point(t.bounds, s.bounds.center)
    return [Point(p1.x + nx, p1.y + ny), Point(p2.x + nx, p2.y + ny)]


def _loop(d, edge: Edge) -> list[Point]:
    r = edge.start.bounds
    index = [e for e in d.edges if e.is_self_edge() and e.start is edge.start].index(edge)
    size = 18 + index * 10
    return [Point(r.right - 20, r.y), Point(r.right - 20, r.y - size), Point(r.right + size, r.y - size),
            Point(r.right + size, r.y + 20), Point(r.right, r.y + 20)]


def _reference(edge: Edge) -> list[Point]:
    f = edge.start
    start = Point(f.x + f.w - 6, f.y + f.h / 2)
    return [start, boundary_point(edge.end.bounds, start)]


def _sequence_points(edge: Edge) -> list[Point]:
    row = getattr(edge, "row_y", edge.start.y + 10)
    s, t = edge.start, edge.end
    if edge.kind is EdgeKind.CALL:
        caller, callee = s, t
    else:
        caller, callee = t, s
    if caller.parent is callee.parent and edge.kind is EdgeKind.CALL:     # autochamada (RF28)
        return [Point(caller.x + caller.w, row), Point(caller.x + caller.w + 25, row),
                Point(caller.x + caller.w + 25, row + 15), Point(callee.x + callee.w, row + 15)]
    right_of = callee.x + callee.w / 2 > caller.x + caller.w / 2
    caller_x = caller.x + caller.w if right_of else caller.x
    if edge.kind is EdgeKind.CALL and edge.props.get("constructor"):
        life = callee.parent
        callee_x = life.x if right_of else life.x + life.w
    else:
        callee_x = callee.x if right_of else callee.x + callee.w
    return [Point(caller_x, row), Point(callee_x, row)] if edge.kind is EdgeKind.CALL else [Point(callee_x, row), Point(caller_x, row)]
