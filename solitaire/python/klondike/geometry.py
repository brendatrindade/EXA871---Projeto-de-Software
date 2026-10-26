"""Geometria do tabuleiro em um só lugar (desenho, clique e arraste usam os mesmos números)."""
from __future__ import annotations

CARD_W, CARD_H = 76, 104
TOP_Y = 35
STOCK_X, WASTE_X = 10, 105
FOUNDATION_X, FOUNDATION_SPACING = 500, 95
COLUMN_X, COLUMN_SPACING, COLUMN_Y, FAN = 10, 125, 170, 25
COLUMN_COUNT = 7


def column_x(index: int) -> int:
    return COLUMN_X + index * COLUMN_SPACING


def foundation_x(slot: int) -> int:
    return FOUNDATION_X + slot * FOUNDATION_SPACING


def column_at(x: float) -> int:
    return max(0, min(COLUMN_COUNT - 1, int((x - COLUMN_X + 5) // COLUMN_SPACING)))


def foundation_slot_at(x: float) -> int:
    return max(0, min(3, int((x - FOUNDATION_X) // FOUNDATION_SPACING)))


def within_column_width(x: float, index: int) -> bool:
    return column_x(index) <= x <= column_x(index) + CARD_W


def in_stock(x: float, y: float) -> bool:
    return STOCK_X <= x <= STOCK_X + CARD_W and TOP_Y <= y <= TOP_Y + CARD_H


def in_waste(x: float, y: float) -> bool:
    return WASTE_X <= x <= WASTE_X + CARD_W and TOP_Y <= y <= TOP_Y + CARD_H


def in_foundation_area(x: float, y: float) -> bool:
    return y < COLUMN_Y - 25 and x >= FOUNDATION_X


def in_tableau_area(y: float) -> bool:
    return y >= COLUMN_Y - 25


def card_y(position: int) -> int:
    return COLUMN_Y + position * FAN


def card_index_at(y: float, count: int) -> int | None:
    """Índice da carta (de cima para baixo) sob y numa coluna com `count` cartas."""
    for index in range(count - 1, -1, -1):
        height = CARD_H if index == count - 1 else FAN
        if card_y(index) <= y < card_y(index) + height:
            return index
    return None
