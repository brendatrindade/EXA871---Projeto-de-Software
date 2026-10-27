"""Preferências do usuário (View) e arquivos recentes; persistidas em JSON."""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path

MIN_SIZE, MAX_SIZE = 250, 4000
MAX_RECENT = 10
DEFAULT_PATH = Path.home() / ".jetuml-sketch" / "settings.json"


def default_diagram_size(screen_w: int, screen_h: int) -> tuple[int, int]:
    """Tamanho padrão proporcional à tela (RF38, RNF01)."""
    clamp = lambda v: max(MIN_SIZE, min(MAX_SIZE, int(v)))
    return clamp(screen_w), clamp(screen_h)


@dataclass
class Settings:
    show_grid: bool = True
    dark_mode: bool = False
    auto_edit: bool = False
    show_tool_hint: bool = False
    verbose_tooltips: bool = False
    notification_duration: int = 5
    font_family: str | None = None
    font_size: int | None = None
    diagram_size: tuple[int, int] | None = None      # None = padrão relativo à tela
    recent_files: list[str] = field(default_factory=list)

    def set_notification_duration(self, seconds: int) -> None:
        if not 0 <= int(seconds) <= 10:
            raise ValueError("A duração deve estar entre 0 e 10 segundos")
        self.notification_duration = int(seconds)

    def restore_notification_duration(self) -> None: self.notification_duration = 5

    def set_diagram_size(self, width: int, height: int) -> None:
        if not (MIN_SIZE <= width <= MAX_SIZE and MIN_SIZE <= height <= MAX_SIZE):
            raise ValueError(f"Largura e altura devem estar entre {MIN_SIZE} e {MAX_SIZE}")
        self.diagram_size = (int(width), int(height))

    def restore_diagram_size(self) -> None: self.diagram_size = None

    def effective_size(self, screen: tuple[int, int]) -> tuple[int, int]:
        return self.diagram_size or default_diagram_size(*screen)

    def add_recent(self, path: str) -> None:
        self.recent_files = [path] + [p for p in self.recent_files if p != path]
        del self.recent_files[MAX_RECENT:]

    def save(self, path: Path = DEFAULT_PATH) -> None:
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(asdict(self), indent=2), encoding="utf-8")
        except OSError:
            pass

    @classmethod
    def load(cls, path: Path = DEFAULT_PATH) -> "Settings":
        settings = cls()
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            for key, value in data.items():
                if hasattr(settings, key):
                    setattr(settings, key, tuple(value) if key == "diagram_size" and value else value)
            settings.set_notification_duration(settings.notification_duration)
        except (OSError, ValueError, TypeError):
            return cls()
        return settings
