"""Jogo automático: decide (somente leitura) e sugere uma ação (RF28–RF31).

A estratégia não altera o estado; Game.perform verifica a legalidade e executa.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Callable, Optional

from .actions import Action, NullAction

if TYPE_CHECKING:
    from .game import Game


class Strategy(ABC):
    @abstractmethod
    def decide(self, game: "Game") -> Action: ...


class NullStrategy(Strategy):
    """Estratégia desativada: o resto do jogo não precisa tratá-la de forma especial (RNF02)."""
    def decide(self, game):
        return NullAction()


class AutomaticStrategy(Strategy):
    """Regras avaliadas na ordem de prioridade de RF29; a primeira possível vence."""

    def decide(self, game):
        rules: tuple[Callable[["Game"], Optional[Action]], ...] = (
            self._draw_when_waste_empty,      # 1
            self._waste_to_foundation,        # 2
            self._waste_to_tableau,           # 3
            self._tableau_to_foundation,      # 4
            self._tableau_with_gain,          # 5
            self._draw_when_nothing_else,     # 6
        )
        for rule in rules:
            action = rule(game)
            if action is not None:
                return action
        return NullAction()                   # 7

    @staticmethod
    def _draw_when_waste_empty(game):
        if game.waste.empty() and not game.stock.empty():
            return game.plan_draw()

    @staticmethod
    def _waste_to_foundation(game):
        card = game.waste.top()
        if card is not None and game.can_move(card, game.foundations[card.suit]):
            return game.plan_move(card, game.foundations[card.suit])

    @staticmethod
    def _waste_to_tableau(game):
        card = game.waste.top()
        if card is not None:
            for column in game.columns:
                if game.can_move(card, column):
                    return game.plan_move(card, column)

    @staticmethod
    def _tableau_to_foundation(game):
        for column in game.columns:
            card = column.top()
            if card is not None and game.can_move(card, game.foundations[card.suit]):
                return game.plan_move(card, game.foundations[card.suit])

    @staticmethod
    def _tableau_with_gain(game):
        """Só move a primeira carta visível: revela carta oculta ou esvazia a coluna (RF30)."""
        for source in game.columns:
            if source.empty():
                continue
            card = source.cards[source.hidden_count]
            if game.is_king_at_base(card):
                continue
            for destination in game.columns:
                if destination is not source and game.can_move(card, destination):
                    return game.plan_move(card, destination)

    @staticmethod
    def _draw_when_nothing_else(game):
        if not game.stock.empty():
            return game.plan_draw()
