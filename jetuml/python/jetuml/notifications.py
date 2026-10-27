"""Notificações temporárias (RF41, RF42): erros em vermelho, confirmações em verde."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from .settings import Settings


@dataclass(frozen=True)
class Notification:
    level: str          # "error" | "info"
    message: str


class Notifier:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.listeners: list[Callable[[Notification], None]] = []
        self.history: list[Notification] = []

    def post(self, level: str, message: str) -> Notification | None:
        if self.settings.notification_duration == 0:      # 0 desativa todas as notificações
            return None
        note = Notification(level, message)
        self.history.append(note)
        for listener in list(self.listeners):
            listener(note)
        return note

    def error(self, message: str): return self.post("error", message)
    def info(self, message: str): return self.post("info", message)
