"""Salvar/abrir partidas em JSON (formato `solitaire-klondike-v1`, RNF05).

Estrutura: {"schema", "cards":[{"id","suit","rank"}], "columns":[{"cards":[id],
"hidden_count":n}] x7, "stock":[id], "waste":[id], "foundations":{NAIPE:[id]}}.
Cada carta aparece uma vez; ao carregar, cada id vira exatamente um objeto Card.
Arquivos antigos com "visible_from" (mesmo significado) continuam aceitos.
"""
from __future__ import annotations

import json
from pathlib import Path

from .cards import Card, Rank, Suit
from .game import COLUMN_COUNT, Game, Layout

SCHEMA = "solitaire-klondike-v1"


def _card_id(card: Card) -> str:
    return f"{card.suit.name}:{int(card.rank)}"


def dumps(game: Game) -> str:
    layout = game.capture()
    ids = lambda cards: [_card_id(c) for c in cards]
    payload = {
        "schema": SCHEMA,
        "cards": [{"id": _card_id(c), "suit": c.suit.name, "rank": int(c.rank)}
                  for c in sorted(game.all_cards(), key=lambda c: (c.suit.name, c.rank))],
        "columns": [{"cards": ids(cards), "hidden_count": hidden} for cards, hidden in layout.columns],
        "stock": ids(layout.stock),
        "waste": ids(layout.waste),
        "foundations": {s.name: ids(cards) for s, cards in layout.foundations.items()},
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


def loads(text: str) -> Layout:
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"JSON inválido: {exc}") from exc
    try:
        return _layout_from(payload)
    except (KeyError, TypeError, AttributeError) as exc:
        raise ValueError("Estrutura da partida malformada") from exc


def _layout_from(payload: dict) -> Layout:
    if payload.get("schema") != SCHEMA:
        raise ValueError("Formato de partida desconhecido")
    expected = {f"{s.name}:{int(r)}" for s in Suit for r in Rank}
    cards: dict[str, Card] = {}
    for entry in payload["cards"]:
        suit, rank = Suit[entry["suit"]], Rank(int(entry["rank"]))
        identifier = entry["id"]
        if identifier != f"{suit.name}:{int(rank)}" or identifier in cards:
            raise ValueError("Identidade de carta inválida ou repetida")
        cards[identifier] = Card(suit, rank)
    if set(cards) != expected:
        raise ValueError("O baralho deve conter as 52 cartas padrão, sem repetição")
    columns, foundations = payload["columns"], payload["foundations"]
    if len(columns) != COLUMN_COUNT or set(foundations) != {s.name for s in Suit}:
        raise ValueError("Estrutura da partida inválida")
    refs = [r for column in columns for r in column["cards"]] + payload["stock"] + payload["waste"]
    refs += [r for group in foundations.values() for r in group]
    if len(refs) != 52 or set(refs) != expected:
        raise ValueError("Cada carta deve aparecer uma única vez nas pilhas")
    layout_columns = []
    for column in columns:
        column_cards = [cards[r] for r in column["cards"]]
        hidden = int(column.get("hidden_count", column.get("visible_from", 0)))
        if not 0 <= hidden <= len(column_cards):
            raise ValueError("Visibilidade de coluna inválida")
        layout_columns.append((column_cards, hidden))
    layout_foundations = {}
    for suit in Suit:
        pile = [cards[r] for r in foundations[suit.name]]
        if any(c.suit is not suit or c.rank != k + 1 for k, c in enumerate(pile)):
            raise ValueError("Fundação fora da ordem Ás→Rei do naipe")
        layout_foundations[suit] = pile
    return Layout(layout_columns, [cards[r] for r in payload["stock"]],
                  [cards[r] for r in payload["waste"]], layout_foundations)


def save(game: Game, path) -> None:
    Path(path).write_text(dumps(game), encoding="utf-8")


def load(game: Game, path) -> None:
    game.load_layout(loads(Path(path).read_text(encoding="utf-8")))
