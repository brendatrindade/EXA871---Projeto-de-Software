"""Sessão do aplicativo (sem toolkit gráfico): documentos/abas, arquivos, exportação e configurações."""
from __future__ import annotations

from pathlib import Path

from . import svg
from . import clipboard as system_clipboard
from . import raster
from . import serialization
from .diagram import Diagram
from .editor import Clipboard, Editor
from .errors import ValidationError
from .notifications import Notifier
from .settings import Settings
from . import textfmt

IMAGE_FORMATS = {".svg": "SVG", ".png": "PNG", ".jpg": "JPEG", ".jpeg": "JPEG"}


class Document:
    def __init__(self, editor: Editor, path: Path | None = None, title: str = "Sem título") -> None:
        self.editor, self.path, self.title = editor, path, title
        self.saved_version = editor.history.version

    @property
    def diagram(self) -> Diagram: return self.editor.diagram
    @property
    def dirty(self) -> bool: return self.editor.history.version != self.saved_version
    @property
    def display_title(self) -> str: return (self.path.name if self.path else self.title) + (" *" if self.dirty else "")


class Workspace:
    def __init__(self, settings: Settings | None = None, screen: tuple[int, int] = (1280, 800)) -> None:
        self.settings = settings or Settings()
        self.notifier = Notifier(self.settings)
        self.clipboard = Clipboard()
        self.screen = screen
        self.documents: list[Document] = []
        self.active: Document | None = None
        self._apply_saved_font()

    def _apply_saved_font(self) -> None:
        if self.settings.font_family and self.settings.font_size:
            try:
                textfmt.METRICS.set_font(self.settings.font_family, self.settings.font_size)
            except ValueError:
                pass

    # ---------- documentos (RF01–RF03) ----------
    def _adopt(self, diagram: Diagram, path: Path | None, title: str) -> Document:
        diagram.size = self.settings.effective_size(self.screen)
        diagram.grow_to_content()                       # RF38: diagramas maiores aumentam a área
        editor = Editor(diagram, self.notifier, self.clipboard, self.settings)
        document = Document(editor, path, title)
        self.documents.append(document)
        self.active = document
        return document

    def new_document(self, kind: str) -> Document:
        return self._adopt(Diagram(kind), None, f"Novo {kind}")

    def open(self, path) -> Document:
        path = Path(path)
        try:
            diagram = serialization.loads(path.read_text(encoding="utf-8"))
        except OSError as exc:
            raise ValidationError(f"Não foi possível ler o arquivo: {exc}") from exc
        document = self._adopt(diagram, path, path.name)
        self.settings.add_recent(str(path))
        self.notifier.info(f"{path.name} aberto.")
        return document

    def save(self, document: Document, path=None) -> None:
        target = Path(path) if path else document.path
        if target is None:
            raise ValueError("Informe o caminho para salvar.")
        if target.suffix.lower() != ".json":
            target = target.with_suffix(".json")
        target.write_text(serialization.dumps(document.diagram), encoding="utf-8")
        document.path, document.saved_version = target, document.editor.history.version
        self.settings.add_recent(str(target))
        self.notifier.info(f"{target.name} salvo.")

    def close(self, document: Document) -> None:
        index = self.documents.index(document)
        self.documents.remove(document)
        self.active = self.documents[min(index, len(self.documents) - 1)] if self.documents else None

    def duplicate(self, document: Document) -> Document:
        copy = serialization.diagram_from_dict(document.diagram.to_dict())
        return self._adopt(copy, None, f"{document.path.stem if document.path else document.title} (cópia)")

    # ---------- imagens (RF04, RF05) ----------
    def export_image(self, document: Document, path) -> None:
        path = Path(path)
        fmt = IMAGE_FORMATS.get(path.suffix.lower())
        if fmt is None:
            raise ValidationError("Formato não suportado; use .svg, .png ou .jpg.")
        d = document.diagram
        if fmt == "SVG":
            bounds = d.content_bounds()
            path.write_text(svg.render_svg(d, bounds.inflated(raster.EXPORT_MARGIN) if bounds else None), encoding="utf-8")
        else:
            raster.render_image(d).save(path, format=fmt)
        self.notifier.info(f"Imagem exportada para {path.name}.")

    def copy_to_clipboard(self, document: Document) -> None:
        try:
            system_clipboard.copy_image(raster.render_image(document.diagram))
        except (raster.MissingDependency, system_clipboard.ClipboardUnavailable) as exc:
            self.notifier.error(str(exc))
            return
        self.notifier.info("Diagrama copiado para a área de transferência.")

    # ---------- View (RF38, RF39) ----------
    def set_diagram_size(self, width: int, height: int) -> None:
        self.settings.set_diagram_size(width, height)
        self._resize_all()

    def restore_diagram_size(self) -> None:
        self.settings.restore_diagram_size()
        self._resize_all()

    def _resize_all(self) -> None:
        for document in self.documents:
            document.diagram.size = self.settings.effective_size(self.screen)
            document.diagram.grow_to_content()
            document.editor.changed()

    def set_font(self, family: str, size: int) -> None:
        textfmt.METRICS.set_font(family, size)
        self.settings.font_family, self.settings.font_size = family, size
        self._relayout_all()

    def restore_font(self) -> None:
        textfmt.METRICS.restore_default()
        self.settings.font_family = self.settings.font_size = None
        self._relayout_all()

    def _relayout_all(self) -> None:
        for document in self.documents:
            document.diagram.relayout()
            document.editor.changed()
