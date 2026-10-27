"""Métricas de texto, formatação de linhas (RF24) e estereótipos (RF23)."""
from __future__ import annotations

OPEN, CLOSE = "«", "»"


class Metrics:
    """Fonte do diagrama (RF39). As medidas são aproximadas e independentes do toolkit gráfico."""
    DEFAULT_FAMILY, DEFAULT_SIZE = "Helvetica", 11

    def __init__(self) -> None:
        self.family, self.size = self.DEFAULT_FAMILY, self.DEFAULT_SIZE

    @property
    def char_w(self) -> int: return max(4, round(self.size * 0.62))
    @property
    def line_h(self) -> int: return max(10, round(self.size * 1.6))

    def set_font(self, family: str, size: int) -> None:
        if not 6 <= size <= 48:
            raise ValueError("Tamanho de fonte deve estar entre 6 e 48")
        self.family, self.size = family, size

    def restore_default(self) -> None:
        self.family, self.size = self.DEFAULT_FAMILY, self.DEFAULT_SIZE


METRICS = Metrics()


def parse_line(line: str) -> tuple[str, bool, bool]:
    """`/texto/` → itálico; `_texto_` → sublinhado. Retorna (texto, itálico, sublinhado)."""
    italic = underline = False
    changed = True
    while changed and len(line) >= 2:
        changed = False
        if line[0] == line[-1] == "/" and not italic:
            line, italic, changed = line[1:-1], True, True
        elif line[0] == line[-1] == "_" and not underline:
            line, underline, changed = line[1:-1], True, True
    return line, italic, underline


def text_width(line: str) -> int:
    return len(parse_line(line)[0]) * METRICS.char_w


def lines_of(text: str) -> list[str]:
    return text.split("\n") if text else []


def insert_stereotype(text: str, cursor: int) -> tuple[str, int]:
    """Ctrl+Q: insere « » na posição do cursor e posiciona o cursor entre eles (RF23)."""
    return text[:cursor] + OPEN + CLOSE + text[cursor:], cursor + 1
