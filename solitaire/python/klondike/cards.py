"""Valores do domínio: cores, naipes, ranks, cartas e baralho (RF04–RF07)."""
from __future__ import annotations

import random
from dataclasses import dataclass
from enum import Enum, IntEnum


class Color(Enum):
    RED = "red"
    BLACK = "black"


class Suit(Enum):
    HEARTS = ("♥", Color.RED)
    DIAMONDS = ("♦", Color.RED)
    CLUBS = ("♣", Color.BLACK)
    SPADES = ("♠", Color.BLACK)

    @property
    def symbol(self) -> str:
        return self.value[0]

    @property
    def color(self) -> Color:
        return self.value[1]

    def same_color(self, other: "Suit") -> bool:
        return self.color is other.color


class Rank(IntEnum):
    """Ordem total dos ranks: ACE < 2 < ... < KING."""
    ACE = 1
    TWO = 2
    THREE = 3
    FOUR = 4
    FIVE = 5
    SIX = 6
    SEVEN = 7
    EIGHT = 8
    NINE = 9
    TEN = 10
    JACK = 11
    QUEEN = 12
    KING = 13

    @property
    def label(self) -> str:
        return _LABELS.get(self, str(int(self)))

    def is_just_below(self, other: "Rank") -> bool:
        """True se self é imediatamente inferior a other."""
        return other - self == 1


_LABELS = {Rank.ACE: "A", Rank.JACK: "J", Rank.QUEEN: "Q", Rank.KING: "K"}


@dataclass(eq=False, frozen=True)
class Card:
    """Entidade única: a igualdade é por identidade (RF04)."""
    suit: Suit
    rank: Rank

    @property
    def color(self) -> Color:
        return self.suit.color

    def __str__(self) -> str:
        return f"{self.rank.label}{self.suit.symbol}"


class Deck:
    """Baralho completo de 52 cartas únicas."""

    def __init__(self) -> None:
        self._cards = [Card(suit, rank) for suit in Suit for rank in Rank]

    def shuffle(self, rng: random.Random) -> None:
        rng.shuffle(self._cards)

    def draw(self) -> Card:
        return self._cards.pop()

    def __len__(self) -> int:
        return len(self._cards)
