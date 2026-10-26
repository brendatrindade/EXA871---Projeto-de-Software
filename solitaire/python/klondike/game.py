"""Mecanismo central: regras, histórico, notificação e execução de jogadas.

As consultas (locate, sequence_from, can_move, ...) nunca alteram o estado (RF27).
A interface e a estratégia só consultam estas regras (RF21, RNF03, RNF04).
"""
from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Callable, Iterator

from .actions import (Action, CompositeAction, DrawAction, MoveCardsAction,
                      NewGameAction, NullAction, RevealAction)
from .cards import Card, Deck, Rank, Suit
from .piles import Foundation, Location, Pile, TableauColumn
from .strategy import AutomaticStrategy, Strategy

COLUMN_COUNT = 7


@dataclass
class Layout:
    """Fotografia completa de uma posição; compartilha as mesmas cartas (identidade)."""
    columns: list[tuple[list[Card], int]]
    stock: list[Card]
    waste: list[Card]
    foundations: dict[Suit, list[Card]]


class Game:
    def __init__(self, seed: int | None = None, strategy: Strategy | None = None) -> None:
        self.rng = random.Random(seed)
        self.stock = Pile()
        self.waste = Pile()
        self.columns = [TableauColumn() for _ in range(COLUMN_COUNT)]
        self.foundations = {suit: Foundation(suit) for suit in Suit}
        self.strategy: Strategy = strategy if strategy is not None else AutomaticStrategy()
        self._history: list[Action] = []
        self._listeners: list[Callable[[], None]] = []
        self.shuffle_and_deal()

    # ---------- notificação (RF26) ----------
    def subscribe(self, listener: Callable[[], None]) -> None:
        self._listeners.append(listener)

    def _notify(self) -> None:
        for listener in tuple(self._listeners):
            listener()

    # ---------- partida (RF01–RF03) ----------
    def shuffle_and_deal(self) -> None:
        deck = Deck()
        deck.shuffle(self.rng)
        for index, column in enumerate(self.columns):
            column.replace([deck.draw() for _ in range(index + 1)], hidden_count=index)
        self.stock.replace([deck.draw() for _ in range(len(deck))])
        self.waste.replace([])
        for foundation in self.foundations.values():
            foundation.replace([])

    @property
    def score(self) -> int:
        return sum(len(foundation) for foundation in self.foundations.values())

    @property
    def won(self) -> bool:
        return self.score == 52

    # ---------- consultas somente leitura (RF09, RF10, RF20, RF27) ----------
    def piles(self) -> Iterator[Pile]:
        yield self.stock
        yield self.waste
        yield from self.columns
        yield from self.foundations.values()

    def all_cards(self) -> list[Card]:
        return [card for pile in self.piles() for card in pile.cards]

    def locate(self, card: Card) -> Location | None:
        """Local de uma carta acessível para movimentação (topo do descarte ou visível no tableau)."""
        if self.waste.top() is card:
            return Location(self.waste, len(self.waste) - 1)
        for column in self.columns:
            index = column.index_of(card)
            if index is not None and column.is_visible(index):
                return Location(column, index)
        return None

    def sequence_from(self, card: Card) -> tuple[Card, ...]:
        location = self.locate(card)
        return () if location is None else location.pile.cards[location.index:]

    def is_visible(self, card: Card) -> bool:
        return self.locate(card) is not None

    def is_first_visible(self, card: Card) -> bool:
        location = self.locate(card)
        return (location is not None and isinstance(location.pile, TableauColumn)
                and location.index == location.pile.hidden_count)

    def is_king_at_base(self, card: Card) -> bool:
        location = self.locate(card)
        return (location is not None and card.rank is Rank.KING
                and isinstance(location.pile, TableauColumn) and location.index == 0)

    # ---------- legalidade (RF21) ----------
    def can_move(self, card: Card, destination: Pile) -> bool:
        location = self.locate(card)
        if location is None or destination is location.pile:
            return False
        return destination.can_take(location.pile.cards[location.index:])

    # ---------- construção de ações (leitura; não alteram o jogo) ----------
    def plan_draw(self) -> Action:
        return DrawAction() if not self.stock.empty() else NewGameAction()

    def plan_move(self, card: Card, destination: Pile) -> Action:
        location = self.locate(card)
        if location is None:
            return NullAction()
        move = MoveCardsAction(location.pile, location.index, destination)
        source = location.pile
        if isinstance(source, TableauColumn) and source.must_reveal_after_removing(location.index):
            return CompositeAction([move, RevealAction(source)])
        return move

    # ---------- execução e histórico (RF25) ----------
    def perform(self, action: Action) -> bool:
        """Verifica a legalidade como qualquer jogada, executa, registra e notifica."""
        if not action.is_legal(self) or not action.do(self):
            return False
        self._history.append(action)
        self._notify()
        return True

    def draw(self) -> bool:
        return self.perform(self.plan_draw())

    def move(self, card: Card, destination: Pile) -> bool:
        return self.perform(self.plan_move(card, destination))

    def automatic_move(self) -> bool:
        return self.perform(self.strategy.decide(self))

    def undo(self) -> bool:
        if not self._history:
            return False
        self._history.pop().undo(self)
        self._notify()
        return True

    @property
    def history_size(self) -> int:
        return len(self._history)

    # ---------- fotografia de posição ----------
    def capture(self) -> Layout:
        return Layout(
            columns=[(list(c.cards), c.hidden_count) for c in self.columns],
            stock=list(self.stock.cards), waste=list(self.waste.cards),
            foundations={s: list(f.cards) for s, f in self.foundations.items()})

    def apply(self, layout: Layout) -> None:
        for column, (cards, hidden) in zip(self.columns, layout.columns):
            column.replace(cards, hidden)
        self.stock.replace(layout.stock)
        self.waste.replace(layout.waste)
        for suit, foundation in self.foundations.items():
            foundation.replace(layout.foundations[suit])

    def load_layout(self, layout: Layout) -> None:
        """Substitui a partida (ex.: Abrir); a posição carregada passa a ser a inicial."""
        self.apply(layout)
        self._history.clear()
        self._notify()
