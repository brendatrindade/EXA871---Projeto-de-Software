"""Exportação SVG sem dependências externas (RF04)."""
from __future__ import annotations

from html import escape

from . import textfmt
from .geometry import Point
from .render import LIGHT, OffsetRenderer, draw_diagram


class SvgRenderer:
    def __init__(self) -> None:
        self.parts: list[str] = []

    @staticmethod
    def _paint(fill, outline, width, dash=False):
        stroke = f'stroke="{outline}" stroke-width="{width}"' if outline else 'stroke="none"'
        return f'fill="{fill or "none"}" {stroke}' + (' stroke-dasharray="5,4"' if dash else "")

    def line(self, points, color, width=1, dash=False):
        pts = " ".join(f"{p.x:.1f},{p.y:.1f}" for p in points)
        self.parts.append(f'<polyline points="{pts}" {self._paint(None, color, width, dash)}/>')

    def polygon(self, points, fill, outline, width=1):
        pts = " ".join(f"{p.x:.1f},{p.y:.1f}" for p in points)
        self.parts.append(f'<polygon points="{pts}" {self._paint(fill, outline, width)}/>')

    def rect(self, x, y, w, h, fill, outline, width=1, radius=0, dash=False):
        self.parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{radius}" '
                          f'{self._paint(fill, outline, width, dash)}/>')

    def ellipse(self, x, y, w, h, fill, outline, width=1):
        self.parts.append(f'<ellipse cx="{x + w / 2:.1f}" cy="{y + h / 2:.1f}" rx="{w / 2:.1f}" ry="{h / 2:.1f}" '
                          f'{self._paint(fill, outline, width)}/>')

    def text(self, x, y, s, color, anchor="center", bold=False, italic=False, underline=False):
        attrs = f'text-anchor="{"middle" if anchor == "center" else "start"}" dominant-baseline="central"'
        if bold: attrs += ' font-weight="bold"'
        if italic: attrs += ' font-style="italic"'
        if underline: attrs += ' text-decoration="underline"'
        m = textfmt.METRICS
        self.parts.append(f'<text x="{x:.1f}" y="{y:.1f}" fill="{color}" font-family="{escape(m.family)},sans-serif" '
                          f'font-size="{m.size}" {attrs}>{escape(s)}</text>')


def render_svg(d, bounds=None, palette=LIGHT, grid=False) -> str:
    """SVG do diagrama; `bounds` recorta ao conteúdo (usado também pela cópia de imagem)."""
    width, height = (d.size if bounds is None else (int(bounds.w), int(bounds.h)))
    renderer = SvgRenderer()
    target = renderer if bounds is None else OffsetRenderer(renderer, -bounds.x, -bounds.y)
    draw_diagram(d, target, palette, grid=grid)
    background = f'<rect width="100%" height="100%" fill="{palette.background}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
            + background + "".join(renderer.parts) + "</svg>")
