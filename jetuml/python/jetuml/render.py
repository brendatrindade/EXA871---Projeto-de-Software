"""Desenho do diagrama sobre uma interface de renderização abstrata.

O mesmo código alimenta a tela (Tk), a exportação SVG, a exportação PNG/JPEG e a cópia para a área de
transferência. Renderizadores implementam: line, polygon, rect, ellipse, text.
Em `text`, `y` é o centro vertical; `anchor` é "center" ou "w" (início do texto em x).
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from . import textfmt
from .edges import Edge, EdgeKind as K
from .geometry import GRID, Point, Rect
from .layout import edge_path
from .nodes import HEADER_H, ActorNode, CallNode, FieldNode, FinalStateNode, ImplicitParameterNode, InitialStateNode, \
    InterfaceNode, Node, NoteNode, ObjectNode, PackageDescriptionNode, PackageNode, PointNode, StateNode, UseCaseNode, \
    ClassNode


@dataclass(frozen=True)
class Palette:
    background: str
    foreground: str
    node_fill: str
    note_fill: str
    grid: str
    selection: str


LIGHT = Palette("#ffffff", "#000000", "#ffffff", "#fff2b2", "#e6e6e6", "#087E8B")
DARK = Palette("#20242a", "#f0f0f0", "#30343a", "#5c5424", "#484c52", "#3fd0e0")


class OffsetRenderer:
    """Translada as coordenadas (usado para recortar a imagem exportada ao conteúdo)."""

    def __init__(self, inner, dx: float, dy: float) -> None:
        self.inner, self.dx, self.dy = inner, dx, dy

    def _p(self, pts): return [Point(p.x + self.dx, p.y + self.dy) for p in pts]
    def line(self, points, color, width=1, dash=False): self.inner.line(self._p(points), color, width, dash)
    def polygon(self, points, fill, outline, width=1): self.inner.polygon(self._p(points), fill, outline, width)
    def rect(self, x, y, w, h, fill, outline, width=1, radius=0, dash=False):
        self.inner.rect(x + self.dx, y + self.dy, w, h, fill, outline, width, radius, dash)
    def ellipse(self, x, y, w, h, fill, outline, width=1): self.inner.ellipse(x + self.dx, y + self.dy, w, h, fill, outline, width)
    def text(self, x, y, s, color, anchor="center", bold=False, italic=False, underline=False):
        self.inner.text(x + self.dx, y + self.dy, s, color, anchor, bold, italic, underline)


def draw_diagram(d, r, palette: Palette = LIGHT, *, grid: bool = False, selection=(), rubber=None, lasso=None) -> None:
    if grid:
        for x in range(0, d.size[0] + 1, GRID):
            r.line([Point(x, 0), Point(x, d.size[1])], palette.grid)
        for y in range(0, d.size[1] + 1, GRID):
            r.line([Point(0, y), Point(d.size[0], y)], palette.grid)
    for node in d.draw_order():
        _draw_node(node, r, palette)
    for edge in d.edges:
        _draw_edge(d, edge, r, palette)
    for item in selection:
        if isinstance(item, Node):
            r.rect(item.x - 3, item.y - 3, item.w + 6, item.h + 6, None, palette.selection, 2)
        else:
            r.line(edge_path(d, item).points, palette.selection, 3)
    if rubber:
        r.line([rubber[0], rubber[1]], palette.selection, 1, True)
    if lasso:
        r.rect(lasso.x, lasso.y, lasso.w, lasso.h, None, palette.selection, 1, 0, True)


# ---------- texto ----------
def _line(r, x, y, text, color, anchor="w", bold=False):
    shown, italic, underline = textfmt.parse_line(text)
    r.text(x, y, shown, color, anchor, bold, italic, underline)


def _comp(count: int) -> int:
    return max(20, count * textfmt.METRICS.line_h + 10)


def _stack(r, node, groups, pal, centered_first=True, header_lines=()):
    """Desenha compartimentos empilhados (classe/interface/descrição de pacote)."""
    lh, y = textfmt.METRICS.line_h, node.y
    for gi, lines in enumerate(groups):
        height = _comp(len(lines))
        if gi:
            r.line([Point(node.x, y), Point(node.x + node.w, y)], pal.foreground)
        for i, text in enumerate(lines):
            cy = y + (height - len(lines) * lh) / 2 + i * lh + lh / 2 if gi == 0 else y + 5 + i * lh + lh / 2
            if gi == 0:
                _line(r, node.x + node.w / 2, cy, text, pal.foreground, "center", bold=not text.startswith("«"))
            else:
                _line(r, node.x + 8, cy, text, pal.foreground)
        y += height


def _draw_node(n: Node, r, pal: Palette) -> None:
    fg, fill = pal.foreground, pal.node_fill
    if isinstance(n, PointNode):
        return
    if isinstance(n, ClassNode):
        r.rect(n.x, n.y, n.w, n.h, fill, fg)
        _stack(r, n, [[n.get("name")], textfmt.lines_of(n.get("attributes")), textfmt.lines_of(n.get("methods"))], pal)
    elif isinstance(n, InterfaceNode):
        r.rect(n.x, n.y, n.w, n.h, fill, fg)
        _stack(r, n, [["«interface»", n.get("name")], textfmt.lines_of(n.get("methods"))], pal)
    elif isinstance(n, (PackageNode, PackageDescriptionNode)):
        tab_w = min(n.w, max(60, textfmt.text_width(n.get("name")) + 20))
        r.rect(n.x, n.y, tab_w, 20, fill, fg)
        r.rect(n.x, n.y + 20, n.w, n.h - 20, fill, fg)
        _line(r, n.x + tab_w / 2, n.y + 10, n.get("name"), fg, "center")
        if isinstance(n, PackageDescriptionNode):
            for i, text in enumerate(textfmt.lines_of(n.get("contents"))):
                _line(r, n.x + 8, n.y + 30 + i * textfmt.METRICS.line_h, text, fg)
    elif isinstance(n, NoteNode):
        r.polygon([Point(n.x, n.y), Point(n.x + n.w - 12, n.y), Point(n.x + n.w, n.y + 12),
                   Point(n.x + n.w, n.y + n.h), Point(n.x, n.y + n.h)], pal.note_fill, fg)
        for i, text in enumerate(textfmt.lines_of(n.get("text"))):
            _line(r, n.x + 8, n.y + 14 + i * textfmt.METRICS.line_h, text, fg)
    elif isinstance(n, ObjectNode):
        r.rect(n.x, n.y, n.w, n.h, fill, fg)
        r.line([Point(n.x, n.y + 30), Point(n.x + n.w, n.y + 30)], fg)
        r.text(n.x + n.w / 2, n.y + 15, n.get("name"), fg, "center", underline=True)
    elif isinstance(n, FieldNode):
        r.text(n.x + 4, n.y + n.h / 2, n.label(), fg, "w")
    elif isinstance(n, ActorNode):
        cx, top = n.x + n.w / 2, n.y
        r.ellipse(cx - 8, top, 16, 16, fill, fg)
        for pts in ([(cx, top + 16), (cx, top + 42)], [(cx - 14, top + 26), (cx + 14, top + 26)],
                    [(cx, top + 42), (cx - 12, top + 58)], [(cx, top + 42), (cx + 12, top + 58)]):
            r.line([Point(*p) for p in pts], fg)
        r.text(cx, n.y + n.h - 10, n.get("name"), fg, "center")
    elif isinstance(n, UseCaseNode):
        r.ellipse(n.x, n.y, n.w, n.h, fill, fg)
        r.text(n.x + n.w / 2, n.y + n.h / 2, n.get("name"), fg, "center")
    elif isinstance(n, StateNode):
        r.rect(n.x, n.y, n.w, n.h, fill, fg, 1, 14)
        r.text(n.x + n.w / 2, n.y + n.h / 2, n.get("name"), fg, "center")
    elif isinstance(n, InitialStateNode):
        r.ellipse(n.x, n.y, n.w, n.h, fg, fg)
    elif isinstance(n, FinalStateNode):
        r.ellipse(n.x, n.y, n.w, n.h, fill, fg, 2)
        r.ellipse(n.x + 6, n.y + 6, n.w - 12, n.h - 12, fg, fg)
    elif isinstance(n, ImplicitParameterNode):
        r.rect(n.x, n.y, n.w, HEADER_H, fill, fg)
        r.text(n.x + n.w / 2, n.y + HEADER_H / 2, n.get("name"), fg, "center", underline=True)
        r.line([Point(n.x + n.w / 2, n.y + HEADER_H), Point(n.x + n.w / 2, n.y + n.h)], fg, 1, True)
    elif isinstance(n, CallNode):
        r.rect(n.x, n.y, n.w, n.h, fill, fg)
        if n.get("open_bottom"):
            r.rect(n.x, n.y + n.h - 2, n.w, 2, fill, fill)
            r.line([Point(n.x + n.w / 2, n.y + n.h), Point(n.x + n.w / 2, n.y + n.h + 14)], fg, 1, True)


# ---------- arestas ----------
def _unit(a: Point, b: Point):
    length = math.hypot(b.x - a.x, b.y - a.y) or 1
    return (b.x - a.x) / length, (b.y - a.y) / length


def _head(r, tip: Point, origin: Point, style: str, color: str, background: str) -> None:
    ux, uy = _unit(origin, tip)
    px, py = -uy, ux
    size = 11
    back = Point(tip.x - ux * size, tip.y - uy * size)
    wing1 = Point(back.x + px * size / 2.2, back.y + py * size / 2.2)
    wing2 = Point(back.x - px * size / 2.2, back.y - py * size / 2.2)
    if style == "open":
        r.line([wing1, tip, wing2], color)
    elif style == "filled":
        r.polygon([tip, wing1, wing2], color, color)
    elif style == "triangle":
        r.polygon([tip, wing1, wing2], background, color)
    elif style in ("diamond", "diamond_filled"):
        far = Point(tip.x - ux * size * 1.8, tip.y - uy * size * 1.8)
        r.polygon([tip, wing1, far, wing2], color if style == "diamond_filled" else background, color)
    elif style == "dot":
        r.ellipse(tip.x - 4, tip.y - 4, 8, 8, color, color)


def _edge_style(e: Edge):
    """(tracejada, cabeça inicial, cabeça final, rótulo central fixo)."""
    k = e.kind
    direction = e.props.get("directionality", "")
    both = direction == "Bidirectional"
    if k in (K.INHERITANCE, K.REALIZATION):
        return e.get("generalization_type") == "Implementation", "open" if both else None, "triangle", None
    if k is K.DEPENDENCY:
        return e.get("generalization_type") == "Implementation", "open" if both else None, "open", None
    if k is K.ASSOCIATION:
        return False, "open" if both else None, "open" if direction in ("Unidirectional", "Bidirectional") else None, None
    if k in (K.AGGREGATION, K.COMPOSITION):
        start = "diamond" if k is K.AGGREGATION else "diamond_filled"
        return False, start, "open" if direction in ("Unidirectional", "Bidirectional") else None, None
    if k is K.GENERALIZATION:
        return False, None, "triangle", None
    if k is K.USE_CASE_DEPENDENCY:
        t = e.get("dependency_type")
        return True, None, "open", {"Include": "«include»", "Extend": "«extend»"}.get(t)
    if k is K.CALL: return False, None, "filled", None
    if k is K.RETURN: return True, None, "open", None
    if k is K.TRANSITION: return False, None, "filled", None
    if k is K.REFERENCE: return False, "dot", "filled", None
    if k is K.NOTE_CONNECTOR: return True, None, None, None
    return False, None, None, None


def _draw_edge(d, e: Edge, r, pal: Palette) -> None:
    path = edge_path(d, e)
    pts = path.points
    dashed, start_head, end_head, fixed = _edge_style(e)
    fg, bg = pal.foreground, pal.node_fill
    r.line(pts, fg, 1, dashed)
    if end_head:
        _head(r, pts[-1], pts[-2], end_head, fg, bg)
    if start_head:
        _head(r, pts[0], pts[1], start_head, fg, bg)
    mid = path.middle
    middle = fixed or e.props.get("middle_label", "")
    if middle:
        _line(r, mid.x, mid.y - 9, middle, fg, "center")
    for key, anchor in (("start_label", path.start_label), ("end_label", path.end_label)):
        if e.props.get(key):
            _line(r, anchor.x, anchor.y, e.props[key], fg, "center")
