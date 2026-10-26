"""Interface Tkinter. Não contém regras: consulta Game.can_move (RF21, RNF03)."""
from __future__ import annotations

import tkinter as tk
from dataclasses import dataclass, field
from tkinter import filedialog, messagebox

from . import geometry as G
from . import persistence
from .cards import Card, Suit
from .game import Game
from .piles import Pile, TableauColumn
from .strategy import AutomaticStrategy, NullStrategy

GREEN, SLOT = "#176b46", "#b7d7c6"


@dataclass
class _Drag:
    cards: tuple[Card, ...]
    offset: tuple[int, int]
    pos: tuple[int, int] | None = None


class SolitaireUI:
    def __init__(self, root: tk.Tk, game: Game) -> None:
        self.root, self.game = root, game
        self._drag: _Drag | None = None
        self._press: tuple[int, int] | None = None
        game.subscribe(self.render)
        self._build_toolbar()
        self.canvas = tk.Canvas(root, width=920, height=650, bg=GREEN, highlightthickness=0)
        self.canvas.pack(fill="both", expand=True)
        self.canvas.bind("<Button-1>", self._on_press)
        self.canvas.bind("<B1-Motion>", self._on_drag)
        self.canvas.bind("<ButtonRelease-1>", self._on_release)
        root.bind("<Control-z>", lambda _e: game.undo())
        self.render()

    def _build_toolbar(self) -> None:
        bar = tk.Frame(self.root)
        bar.pack(side="top", fill="x")
        self.auto_enabled = tk.BooleanVar(value=True)
        for text, command in (("Comprar / Nova partida", self.game.draw), ("Desfazer", self.game.undo),
                              ("Jogada automática", self.game.automatic_move),
                              ("Salvar", self._save), ("Abrir", self._open)):
            tk.Button(bar, text=text, command=command).pack(side="left")
        tk.Checkbutton(bar, text="Estratégia ativa", variable=self.auto_enabled,
                       command=self._toggle_strategy).pack(side="left", padx=8)

    def _toggle_strategy(self) -> None:
        self.game.strategy = AutomaticStrategy() if self.auto_enabled.get() else NullStrategy()

    def _save(self) -> None:
        chosen = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("Solitaire JSON", "*.json")])
        if chosen:
            try:
                persistence.save(self.game, chosen)
            except OSError as exc:
                messagebox.showerror("Falha ao salvar", str(exc))

    def _open(self) -> None:
        chosen = filedialog.askopenfilename(filetypes=[("Solitaire JSON", "*.json")])
        if chosen:
            try:
                persistence.load(self.game, chosen)
            except (OSError, ValueError) as exc:
                messagebox.showerror("Falha ao abrir", str(exc))

    # ---------- entrada ----------
    def _card_at(self, x: int, y: int) -> Card | None:
        if G.in_waste(x, y):
            return self.game.waste.top()
        if y >= G.COLUMN_Y:
            index = G.column_at(x)
            column = self.game.columns[index]
            if G.within_column_width(x, index):
                position = G.card_index_at(y, len(column))
                if position is not None and column.is_visible(position):
                    return column.cards[position]
        return None

    def _origin_of(self, card: Card) -> tuple[int, int]:
        location = self.game.locate(card)
        if isinstance(location.pile, TableauColumn):
            return G.column_x(self.game.columns.index(location.pile)), G.card_y(location.index)
        return G.WASTE_X, G.TOP_Y

    def _on_press(self, event) -> None:
        self._press, self._drag = (event.x, event.y), None
        card = self._card_at(event.x, event.y)
        if card is not None:
            ox, oy = self._origin_of(card)
            self._drag = _Drag(self.game.sequence_from(card), (event.x - ox, event.y - oy))

    def _on_drag(self, event) -> None:
        if self._drag is not None:
            self._drag.pos = (event.x, event.y)
            self.render()

    def _on_release(self, event) -> None:
        drag, pressed = self._drag, self._press
        self._drag = self._press = None
        if pressed and G.in_stock(*pressed) and G.in_stock(event.x, event.y):
            self.game.draw()
        elif drag is not None and drag.pos is not None:
            destination = self._drop_target(event.x, event.y)
            if destination is not None:
                self.game.move(drag.cards[0], destination)
        self.render()

    def _drop_target(self, x: int, y: int) -> Pile | None:
        if G.in_foundation_area(x, y):
            return self.game.foundations[list(Suit)[G.foundation_slot_at(x)]]
        if G.in_tableau_area(y):
            return self.game.columns[G.column_at(x)]
        return None

    # ---------- desenho ----------
    def render(self) -> None:
        if not hasattr(self, "canvas"):
            return
        c, game = self.canvas, self.game
        c.delete("all")
        dragged = {id(card) for card in self._drag.cards} if self._drag and self._drag.pos else set()
        self._slot(G.STOCK_X, G.TOP_Y, "Monte" if not game.stock.empty() else "↻")
        self._slot(G.WASTE_X, G.TOP_Y, "")
        top = game.waste.top()
        if top is not None and id(top) not in dragged:
            self._card(G.WASTE_X, G.TOP_Y, top)
        for slot, suit in enumerate(Suit):
            x = G.foundation_x(slot)
            self._slot(x, G.TOP_Y, suit.symbol)
            if game.foundations[suit].top() is not None:
                self._card(x, G.TOP_Y, game.foundations[suit].top())
        for index, column in enumerate(game.columns):
            x = G.column_x(index)
            if column.empty():
                self._slot(x, G.COLUMN_Y, "+")
            for position, card in enumerate(column.cards):
                if id(card) not in dragged:
                    self._card(x, G.card_y(position), card if column.is_visible(position) else None)
        if self._drag is not None and self._drag.pos is not None:
            self._paint_drag()
        status = f"Jogadas: {game.history_size}" + ("   Vitória!" if game.won else "")
        c.create_text(10, 630, anchor="w", fill="white", text=status)

    def _paint_drag(self) -> None:
        drag = self._drag
        x, y = drag.pos
        destination = self._drop_target(x, y)
        if destination is not None:
            legal = self.game.can_move(drag.cards[0], destination)
            if isinstance(destination, TableauColumn):
                left = G.column_x(self.game.columns.index(destination))
                top, height = G.COLUMN_Y, G.CARD_H + G.FAN * max(0, len(destination) - 1)
            else:
                left, top, height = G.foundation_x(list(Suit).index(destination.suit)), G.TOP_Y, G.CARD_H
            self.canvas.create_rectangle(left, top, left + G.CARD_W, top + height, width=3,
                                         outline="#39e36d" if legal else "#f44336")
        for i, card in enumerate(drag.cards):
            self._card(x - drag.offset[0], y - drag.offset[1] + i * G.FAN, card)

    def _slot(self, x: int, y: int, label: str) -> None:
        self.canvas.create_rectangle(x, y, x + G.CARD_W, y + G.CARD_H, outline=SLOT, width=2)
        self.canvas.create_text(x + 38, y + 52, text=label, fill=SLOT, font=("Arial", 18))

    def _card(self, x: int, y: int, card: Card | None) -> None:
        if card is None:
            self.canvas.create_rectangle(x, y, x + G.CARD_W, y + G.CARD_H, fill="#245a42", outline=SLOT)
            return
        self.canvas.create_rectangle(x, y, x + G.CARD_W, y + G.CARD_H, fill="#fffdf5", outline="#333")
        color = "#bd2438" if card.suit.color.value == "red" else "#17251d"
        self.canvas.create_text(x + 8, y + 8, anchor="nw", text=str(card), fill=color, font=("Arial", 14, "bold"))
