"""Pontos, retângulos e utilidades geométricas (independentes de interface)."""
from __future__ import annotations

import math
from dataclasses import dataclass

GRID = 10


@dataclass(frozen=True)
class Point:
    x: float
    y: float


@dataclass(frozen=True)
class Rect:
    x: float
    y: float
    w: float
    h: float

    @property
    def right(self) -> float: return self.x + self.w
    @property
    def bottom(self) -> float: return self.y + self.h
    @property
    def center(self) -> Point: return Point(self.x + self.w / 2, self.y + self.h / 2)

    def contains(self, px: float, py: float) -> bool:
        return self.x <= px <= self.right and self.y <= py <= self.bottom

    def contains_rect(self, other: "Rect") -> bool:
        return self.x <= other.x and self.y <= other.y and other.right <= self.right and other.bottom <= self.bottom

    def intersects(self, other: "Rect") -> bool:
        return self.x <= other.right and other.x <= self.right and self.y <= other.bottom and other.y <= self.bottom

    def union(self, other: "Rect") -> "Rect":
        x, y = min(self.x, other.x), min(self.y, other.y)
        return Rect(x, y, max(self.right, other.right) - x, max(self.bottom, other.bottom) - y)

    def inflated(self, margin: float) -> "Rect":
        return Rect(self.x - margin, self.y - margin, self.w + 2 * margin, self.h + 2 * margin)


def snap(value: float) -> int:
    """Alinha à grade (RF36): vale com a grade visível ou não."""
    return int(round(value / GRID)) * GRID


def normalized_rect(a: Point, b: Point) -> Rect:
    return Rect(min(a.x, b.x), min(a.y, b.y), abs(a.x - b.x), abs(a.y - b.y))


def boundary_point(rect: Rect, toward: Point) -> Point:
    """Ponto onde a reta do centro do retângulo até `toward` cruza a borda."""
    c = rect.center
    dx, dy = toward.x - c.x, toward.y - c.y
    if dx == 0 and dy == 0:
        return c
    scale = min((rect.w / 2) / abs(dx) if dx else math.inf, (rect.h / 2) / abs(dy) if dy else math.inf)
    return Point(c.x + dx * scale, c.y + dy * scale)


def segment_distance(px: float, py: float, a: Point, b: Point) -> float:
    dx, dy = b.x - a.x, b.y - a.y
    length2 = dx * dx + dy * dy
    t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((px - a.x) * dx + (py - a.y) * dy) / length2))
    return math.hypot(px - (a.x + t * dx), py - (a.y + t * dy))
