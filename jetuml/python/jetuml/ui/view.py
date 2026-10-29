"""Uma aba: canvas com rolagem, tradução de eventos do mouse/teclado e menu de contexto (RF09)."""
from __future__ import annotations

import sys
import tkinter as tk

from .. import textfmt
from .. import catalog
from ..editor import Mods
from ..render import DARK, LIGHT, draw_diagram
from .tk_renderer import TkRenderer


class DiagramView:
    def __init__(self, parent, document, workspace, app) -> None:
        self.document, self.workspace, self.app = document, workspace, app
        self.editor = document.editor
        self.frame = tk.Frame(parent)
        self.canvas = tk.Canvas(self.frame, highlightthickness=0, takefocus=1)
        hbar = tk.Scrollbar(self.frame, orient="horizontal", command=self.canvas.xview)
        vbar = tk.Scrollbar(self.frame, orient="vertical", command=self.canvas.yview)
        self.canvas.configure(xscrollcommand=hbar.set, yscrollcommand=vbar.set)
        self.canvas.grid(row=0, column=0, sticky="nsew")
        vbar.grid(row=0, column=1, sticky="ns")
        hbar.grid(row=1, column=0, sticky="ew")
        self.frame.rowconfigure(0, weight=1)
        self.frame.columnconfigure(0, weight=1)
        c = self.canvas
        c.bind("<Button-1>", self._press)
        c.bind("<B1-Motion>", self._drag)
        c.bind("<ButtonRelease-1>", self._release)
        c.bind("<Double-Button-1>", self._double)
        c.bind("<Button-2>" if sys.platform == "darwin" else "<Button-3>", self._popup)
        c.bind("<Key>", self._key)
        self.editor.listeners.append(self.redraw)
        self.redraw()

    # ---------- eventos ----------
    def _model(self, event):
        z = self.editor.zoom_factor
        return self.canvas.canvasx(event.x) / z, self.canvas.canvasy(event.y) / z

    @staticmethod
    def _mods(event) -> Mods:
        return Mods(ctrl=bool(event.state & 0x4), shift=bool(event.state & 0x1))

    def _press(self, event):
        self.canvas.focus_set()
        self.editor.press(*self._model(event), self._mods(event))

    def _drag(self, event): self.editor.drag(*self._model(event), self._mods(event))
    def _release(self, event): self.editor.release(*self._model(event), self._mods(event))

    def _double(self, event):
        self.editor.double_click(*self._model(event))

    def _key(self, event):
        if event.char and not (event.state & 0x4) and event.char.isalnum():
            self.app.choose_shortcut(self.editor, event.char)

    def _popup(self, event):
        menu = tk.Menu(self.canvas, tearoff=0)
        for tool in catalog.tools_for(self.editor.diagram.kind):
            menu.add_command(label=tool.name, command=lambda n=tool.name: self.app.choose_tool(n))
        menu.tk_popup(event.x_root, event.y_root)

    # ---------- desenho ----------
    def redraw(self) -> None:
        d, c, settings = self.editor.diagram, self.canvas, self.workspace.settings
        z = self.editor.zoom_factor
        palette = DARK if settings.dark_mode else LIGHT
        c.delete("all")
        c.configure(bg=palette.background, scrollregion=(0, 0, d.size[0] * z, d.size[1] * z))
        renderer = TkRenderer(c, z, textfmt.METRICS.family, textfmt.METRICS.size)
        draw_diagram(d, renderer, palette, grid=settings.show_grid, selection=self.editor.selection,
                     rubber=self.editor.rubber, lasso=self.editor.lasso)
