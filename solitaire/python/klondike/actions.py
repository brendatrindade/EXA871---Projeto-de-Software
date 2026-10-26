"""Ações reversíveis (RF22–RF24). Simples, compostas e nula têm a mesma interface (RNF02)."""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import TYPE_CHECKING

from .piles import Pile, TableauColumn

if TYPE_CHECKING:
    from .game import Game


class Action(ABC):
    @abstractmethod
    def is_legal(self, game: "Game") -> bool: ...

    @abstractmethod
    def do(self, game: "Game") -> bool:
        """Executa; retorna True se houve mudança efetiva de estado."""

    @abstractmethod
    def undo(self, game: "Game") -> None: ...


class NullAction(Action):
    """Ação sem efeito (RF24)."""
    def is_legal(self, game): return True
    def do(self, game): return False
    def undo(self, game): pass


class CompositeAction(Action):
    """Executa em ordem e desfaz em ordem inversa (RF23)."""

    def __init__(self, actions: list[Action]) -> None:
        self._actions = list(actions)

    def is_legal(self, game):
        return all(action.is_legal(game) for action in self._actions)

    def do(self, game):
        changed = False
        for action in self._actions:
            changed = action.do(game) or changed
        return changed

    def undo(self, game):
        for action in reversed(self._actions):
            action.undo(game)


class DrawAction(Action):
    """Monte → descarte (RF11); desfazer devolve a mesma carta (RF12)."""
    def is_legal(self, game): return not game.stock.empty()
    def do(self, game):
        game.waste.push(game.stock.pop())
        return True
    def undo(self, game):
        game.stock.push(game.waste.pop())


class MoveCardsAction(Action):
    """Move a carta (e as que estão sobre ela) de `source[index:]` para `destination`."""

    def __init__(self, source: Pile, index: int, destination: Pile) -> None:
        self._source, self._index, self._destination = source, index, destination
        self._cards = source.cards[index:]

    def is_legal(self, game):
        if not self._cards or self._source.cards[self._index:] != self._cards:
            return False
        return game.can_move(self._cards[0], self._destination)

    def do(self, game):
        self._destination.push_all(self._source.take_from(self._index))
        return True

    def undo(self, game):
        moved = self._destination.take_from(len(self._destination) - len(self._cards))
        self._source.push_all(moved)


class RevealAction(Action):
    """Revela a carta oculta exposta por uma jogada (RF19). É consequência, não jogada."""

    def __init__(self, column: TableauColumn) -> None:
        self._column = column
        self._done = False

    def is_legal(self, game): return True
    def do(self, game):
        self._done = self._column.reveal_next()
        return self._done
    def undo(self, game):
        if self._done:
            self._column.conceal_last_revealed()
            self._done = False


class NewGameAction(Action):
    """Embaralha e distribui de novo; reversível para manter o histórico íntegro."""

    def __init__(self) -> None:
        self._previous = None

    def is_legal(self, game): return game.stock.empty()
    def do(self, game):
        self._previous = game.capture()
        game.shuffle_and_deal()
        return True
    def undo(self, game):
        game.apply(self._previous)
