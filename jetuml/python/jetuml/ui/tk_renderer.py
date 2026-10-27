"""Renderizador sobre tk.Canvas, com escala de zoom (RF37)."""
from __future__ import annotations


class TkRenderer:
    def __init__(self, canvas, scale: float, family: str, size: int) -> None:
        self.canvas, self.scale, self.family, self.size = canvas, scale, family, size

    def _flat(self, points):
        return [v * self.scale for p in points for v in (p.x, p.y)]

    def line(self, points, color, width=1, dash=False):
        options = {"fill": color, "width": max(1, width)}
        if dash:
            options["dash"] = (5, 4)
        self.canvas.create_line(*self._flat(points), **options)

    def polygon(self, points, fill, outline, width=1):
        self.canvas.create_polygon(*self._flat(points), fill=fill or "", outline=outline or "", width=width)

    def rect(self, x, y, w, h, fill, outline, width=1, radius=0, dash=False):
        s = self.scale
        if radius:
            r = min(radius, w / 2, h / 2)
            pts = [x + r, y, x + w - r, y, x + w, y, x + w, y + r, x + w, y + h - r, x + w, y + h,
                   x + w - r, y + h, x + r, y + h, x, y + h, x, y + h - r, x, y + r, x, y]
            self.canvas.create_polygon(*[v * s for v in pts], smooth=True, fill=fill or "", outline=outline or "", width=width)
            return
        options = {"fill": fill or "", "outline": outline or "", "width": width}
        if dash:
            options["dash"] = (5, 4)
        self.canvas.create_rectangle(x * s, y * s, (x + w) * s, (y + h) * s, **options)

    def ellipse(self, x, y, w, h, fill, outline, width=1):
        s = self.scale
        self.canvas.create_oval(x * s, y * s, (x + w) * s, (y + h) * s, fill=fill or "", outline=outline or "", width=width)

    def text(self, x, y, s, color, anchor="center", bold=False, italic=False, underline=False):
        style = " ".join(n for n, on in (("bold", bold), ("italic", italic), ("underline", underline)) if on)
        font = (self.family, max(6, round(self.size * self.scale)), *( (style,) if style else () ))
        self.canvas.create_text(x * self.scale, y * self.scale, text=s, fill=color, anchor=anchor, font=font)
