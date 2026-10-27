"""Descrição declarativa dos campos editáveis (painel de propriedades, RF20/RF22)."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Field:
    name: str
    label: str
    kind: str = "text"            # text | multiline | choice | bool
    choices: tuple[str, ...] = ()
    default: object = ""
