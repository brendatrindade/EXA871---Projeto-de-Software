"""Exportação PNG/JPEG e imagem para a área de transferência (RF04, RF05); requer Pillow."""
from __future__ import annotations

from . import textfmt
from .geometry import Point, Rect
from .render import LIGHT, OffsetRenderer, draw_diagram

EXPORT_MARGIN = 10


class MissingDependency(RuntimeError):
    pass


def _pillow():
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError as exc:
        raise MissingDependency("Instale Pillow: python -m pip install pillow") from exc
    return Image, ImageDraw, ImageFont


class PilRenderer:
    def __init__(self, draw, font_factory) -> None:
        self.draw, self.fonts = draw, font_factory

    @staticmethod
    def _xy(points): return [(p.x, p.y) for p in points]

    def _dashed(self, points, color, width):
        for a, b in zip(points, points[1:]):
            length = max(((b.x - a.x) ** 2 + (b.y - a.y) ** 2) ** 0.5, 1)
            steps = int(length // 9) + 1
            for i in range(steps):
                t0, t1 = i * 9 / length, min(1, (i * 9 + 5) / length)
                if t0 < 1:
                    self.draw.line([(a.x + (b.x - a.x) * t0, a.y + (b.y - a.y) * t0),
                                    (a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1)], fill=color, width=int(width))

    def line(self, points, color, width=1, dash=False):
        if dash: self._dashed(points, color, width)
        else: self.draw.line(self._xy(points), fill=color, width=int(width))

    def polygon(self, points, fill, outline, width=1):
        self.draw.polygon(self._xy(points), fill=fill, outline=outline)

    def rect(self, x, y, w, h, fill, outline, width=1, radius=0, dash=False):
        box = (x, y, x + w, y + h)
        if radius: self.draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=int(width))
        elif dash and outline:
            for a, b in ((Point(x, y), Point(x + w, y)), (Point(x + w, y), Point(x + w, y + h)),
                         (Point(x + w, y + h), Point(x, y + h)), (Point(x, y + h), Point(x, y))):
                self._dashed([a, b], outline, width)
        else: self.draw.rectangle(box, fill=fill, outline=outline, width=int(width))

    def ellipse(self, x, y, w, h, fill, outline, width=1):
        self.draw.ellipse((x, y, x + w, y + h), fill=fill, outline=outline, width=int(width))

    def text(self, x, y, s, color, anchor="center", bold=False, italic=False, underline=False):
        font = self.fonts(bold)
        width = self.draw.textlength(s, font=font)
        left = x - width / 2 if anchor == "center" else x
        self.draw.text((left, y), s, fill=color, font=font, anchor="lm")
        if underline:
            self.draw.line([(left, y + font.size * 0.6), (left + width, y + font.size * 0.6)], fill=color)


def render_image(d, palette=LIGHT, grid=False, content_only=True):
    """Imagem do diagrama: recortada ao conteúdo e sem grade por padrão (RF05)."""
    Image, ImageDraw, ImageFont = _pillow()
    bounds = d.content_bounds() if content_only else None
    if bounds is None:
        bounds = Rect(0, 0, 120, 60) if content_only else Rect(0, 0, *d.size)
    else:
        bounds = bounds.inflated(EXPORT_MARGIN)
    image = Image.new("RGB", (max(1, int(bounds.w)), max(1, int(bounds.h))), palette.background)
    size = textfmt.METRICS.size
    fonts = lambda bold: ImageFont.load_default(size)
    draw_diagram(d, OffsetRenderer(PilRenderer(ImageDraw.Draw(image), fonts), -bounds.x, -bounds.y), palette, grid=grid)
    return image
