"""Pilhas e locais (RF08–RF10). Monte, descarte, fundações e colunas são Pile."""
from __future__ import annotations

from typing import Iterable, NamedTuple

from .cards import Card, Rank, Suit


class Pile:
    """Preserva a ordem base→topo; consultas nunca alteram o estado (RF09, RF27)."""

    def __init__(self) -> None:
        self._cards: list[Card] = []

    @property
    def cards(self) -> tuple[Card, ...]:
        return tuple(self._cards)

    def __len__(self) -> int:
        return len(self._cards)

    def empty(self) -> bool:
        return not self._cards

    def top(self) -> Card | None:
        return self._cards[-1] if self._cards else None

    def index_of(self, card: Card) -> int | None:
        for index, candidate in enumerate(self._cards):
            if candidate is card:
                return index
        return None

    def can_take(self, cards: tuple[Card, ...]) -> bool:
        """Por padrão (monte, descarte) uma pilha não é destino de jogada (RF13)."""
        return False

    # mutadores (usados apenas por ações e pelo jogo)
    def push(self, card: Card) -> None:
        self._cards.append(card)

    def pop(self) -> Card:
        return self._cards.pop()

    def push_all(self, cards: Iterable[Card]) -> None:
        self._cards.extend(cards)

    def take_from(self, index: int) -> list[Card]:
        removed = self._cards[index:]
        del self._cards[index:]
        return removed

    def replace(self, cards: Iterable[Card]) -> None:
        self._cards = list(cards)


class Foundation(Pile):
    """Uma por naipe, do Ás ao Rei (RF14, RF15)."""

    def __init__(self, suit: Suit) -> None:
        super().__init__()
        self.suit = suit

    def can_take(self, cards: tuple[Card, ...]) -> bool:
        if len(cards) != 1:
            return False
        card = cards[0]
        if card.suit is not self.suit:
            return False
        top = self.top()
        return card.rank is Rank.ACE if top is None else top.rank.is_just_below(card.rank)


class TableauColumn(Pile):
    """Coluna do tableau; as `hidden_count` primeiras cartas estão viradas (RF16)."""

    def __init__(self) -> None:
        super().__init__()
        self._hidden = 0

    @property
    def hidden_count(self) -> int:
        return self._hidden

    def is_visible(self, index: int) -> bool:
        return index >= self._hidden

    def replace(self, cards: Iterable[Card], hidden_count: int = 0) -> None:
        cards = list(cards)
        if not 0 <= hidden_count <= len(cards):
            raise ValueError("Quantidade de cartas ocultas inválida")
        self._cards = cards
        self._hidden = hidden_count

    def must_reveal_after_removing(self, index: int) -> bool:
        """Remover a primeira carta visível (índice > 0) expõe uma carta oculta (RF19)."""
        return 0 < index == self._hidden

    def reveal_next(self) -> bool:
        if 0 < self._hidden == len(self._cards):
            self._hidden -= 1
            return True
        return False

    def conceal_last_revealed(self) -> None:
        self._hidden += 1

    def can_take(self, cards: tuple[Card, ...]) -> bool:
        if not cards:
            return False
        first, top = cards[0], self.top()
        if top is None:
            return first.rank is Rank.KING
        return first.rank.is_just_below(top.rank) and first.color is not top.color


class Location(NamedTuple):
    pile: Pile
    index: int
