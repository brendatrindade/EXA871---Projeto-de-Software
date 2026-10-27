"""Janela principal: menus (File/Edit/View), abas, barra de ferramentas, tela de boas-vindas e notificações."""
from __future__ import annotations

import tkinter as tk
import tkinter.font as tkfont
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .. import catalog
from ..diagram import DIAGRAM_KINDS
from ..errors import ValidationError
from ..schema import Field
from ..settings import MAX_SIZE, MIN_SIZE
from .dialogs import FormDialog
from .view import DiagramView

ERROR_BG, INFO_BG = "#c62828", "#2e7d32"


class Tooltip:
    def __init__(self, widget, text_provider) -> None:
        self.widget, self.text_provider, self.tip = widget, text_provider, None
        widget.bind("<Enter>", self._show)
        widget.bind("<Leave>", self._hide)

    def _show(self, _event) -> None:
        self._hide()
        self.tip = tk.Toplevel(self.widget)
        self.tip.wm_overrideredirect(True)
        self.tip.wm_geometry(f"+{self.widget.winfo_rootx() + 30}+{self.widget.winfo_rooty() + 20}")
        tk.Label(self.tip, text=self.text_provider(), background="#ffffe0", relief="solid", borderwidth=1,
                 justify="left", wraplength=280).pack()

    def _hide(self, _event=None) -> None:
        if self.tip is not None:
            self.tip.destroy()
            self.tip = None


class App:
    def __init__(self, root, workspace) -> None:
        self.root, self.ws = root, workspace
        self.settings = workspace.settings
        self.views: dict[object, DiagramView] = {}
        self.tool_buttons: dict[str, tk.Button] = {}
        root.title("JetUML Sketch — EXA871")
        root.geometry("1180x780")
        self.toolbar = tk.Frame(root, padx=4, pady=4)
        self.toolbar.pack(side="left", fill="y")
        self.stage = tk.Frame(root)
        self.stage.pack(side="right", fill="both", expand=True)
        self.notebook = ttk.Notebook(self.stage)
        self.notebook.bind("<<NotebookTabChanged>>", self._tab_changed)
        self.welcome = self._build_welcome()
        self.toasts = tk.Frame(root)
        self.toasts.place(relx=0.0, rely=1.0, x=8, y=-8, anchor="sw")
        workspace.notifier.listeners.append(self._toast)
        self._build_menu()
        self._bind_keys()
        root.protocol("WM_DELETE_WINDOW", self.exit)
        self._refresh_stage()
        self.apply_theme()

    # ---------- boas-vindas e abas (RF01) ----------
    def _build_welcome(self):
        frame = tk.Frame(self.stage)
        tk.Label(frame, text="JetUML Sketch", font=("Helvetica", 22, "bold")).pack(pady=(80, 6))
        tk.Label(frame, text="Escolha o tipo de diagrama para começar").pack(pady=(0, 16))
        for kind in DIAGRAM_KINDS:
            tk.Button(frame, text=f"{kind} Diagram", width=24, command=lambda k=kind: self.new_diagram(k)).pack(pady=3)
        tk.Button(frame, text="Abrir arquivo…", width=24, command=self.open_file).pack(pady=(14, 3))
        return frame

    def _refresh_stage(self) -> None:
        if self.ws.documents:
            self.welcome.pack_forget()
            self.notebook.pack(fill="both", expand=True)
        else:
            self.notebook.pack_forget()
            self.welcome.pack(fill="both", expand=True)
            self._rebuild_toolbar()

    def _add_view(self, document) -> None:
        view = DiagramView(self.notebook, document, self.ws, self)
        self.views[document] = view
        document.editor.listeners.append(lambda d=document: self._update_title(d))
        document.editor.on_edit_requested = lambda element, d=document: self.edit_properties(d, element)
        self.notebook.add(view.frame, text=document.display_title)
        self.notebook.select(view.frame)
        self._refresh_stage()
        self.ws.active = document
        self._rebuild_toolbar()

    def _update_title(self, document) -> None:
        view = self.views.get(document)
        if view is not None:
            self.notebook.tab(view.frame, text=document.display_title)

    def _tab_changed(self, _event=None) -> None:
        selected = str(self.notebook.select())
        for document, view in self.views.items():
            if str(view.frame) == selected:
                self.ws.active = document
                self._rebuild_toolbar()
                return

    @property
    def document(self):
        return self.ws.active

    # ---------- barra de ferramentas (RF07, RF08, RF10) ----------
    def _rebuild_toolbar(self) -> None:
        for widget in self.toolbar.winfo_children():
            widget.destroy()
        self.tool_buttons = {}
        if self.document is None:
            return
        kind = self.document.diagram.kind
        tk.Label(self.toolbar, text=f"{kind} Diagram").pack(pady=(0, 4))
        for index, tool in enumerate(catalog.tools_for(kind)):
            label = f"{tool.icon} {catalog.shortcut(index)}" + (f"  {tool.name}" if self.settings.show_tool_hint else "")
            button = tk.Button(self.toolbar, text=label, anchor="w", command=lambda n=tool.name: self.choose_tool(n))
            button.pack(fill="x")
            Tooltip(button, lambda t=tool: t.description if self.settings.verbose_tooltips else t.name)
            self.tool_buttons[tool.name] = button
        self._highlight_tool()

    def _highlight_tool(self) -> None:
        if self.document is None:
            return
        active = self.document.editor.tool.name
        for name, button in self.tool_buttons.items():
            button.configure(relief="sunken" if name == active else "raised")

    def choose_tool(self, name: str) -> None:
        if self.document is None:
            return
        if self.document.editor.activate_tool(name) == "clipboard":
            self.copy_to_clipboard()
        self._highlight_tool()

    def choose_shortcut(self, editor, key: str) -> None:
        if editor is not (self.document and self.document.editor):
            return
        tool = catalog.tool_for_shortcut(editor.diagram.kind, key)
        if tool is not None:
            self.choose_tool(tool.name)

    # ---------- menus ----------
    def _build_menu(self) -> None:
        bar = tk.Menu(self.root)
        file_menu = tk.Menu(bar, tearoff=0)
        new_menu = tk.Menu(file_menu, tearoff=0)
        for kind in DIAGRAM_KINDS:
            new_menu.add_command(label=f"{kind} Diagram", command=lambda k=kind: self.new_diagram(k))
        file_menu.add_cascade(label="New", menu=new_menu)
        self.recent_menu = tk.Menu(file_menu, tearoff=0, postcommand=self._fill_recent)
        for label, command, accel in (("Open…", self.open_file, "Ctrl+O"), ("Save", self.save, "Ctrl+S"),
                                      ("Save As…", self.save_as, None), ("Close", self.close_current, "Ctrl+W"),
                                      ("Duplicate", self.duplicate, "Ctrl+D"), ("Export Image…", self.export_image, None),
                                      ("Copy to Clipboard", self.copy_to_clipboard, "Ctrl+B")):
            file_menu.add_command(label=label, command=command, accelerator=accel)
            if label.startswith("Open"):
                file_menu.add_cascade(label="Recent Files", menu=self.recent_menu)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.exit, accelerator="Ctrl+Q")
        bar.add_cascade(label="File", menu=file_menu)

        edit = tk.Menu(bar, tearoff=0)
        for label, command, accel in (("Undo", lambda: self._do("undo"), "Ctrl+Z"), ("Redo", lambda: self._do("redo"), "Ctrl+Y"),
                                      ("Select All", lambda: self._do("select_all"), "Ctrl+A"),
                                      ("Cut", lambda: self._do("cut_selected"), "Ctrl+X"),
                                      ("Copy", lambda: self._do("copy_selected"), "Ctrl+C"),
                                      ("Paste", lambda: self._do("paste"), "Ctrl+V"),
                                      ("Delete", lambda: self._do("delete_selected"), "Del"),
                                      ("Properties", lambda: self._do("request_properties"), "Ctrl+Enter")):
            edit.add_command(label=label, command=command, accelerator=accel)
        bar.add_cascade(label="Edit", menu=edit)

        view = tk.Menu(bar, tearoff=0)
        self.vars = {name: tk.BooleanVar(value=getattr(self.settings, name))
                     for name in ("show_grid", "dark_mode", "show_tool_hint", "verbose_tooltips", "auto_edit")}
        for label, name in (("Show Grid", "show_grid"), ("Show Tool Hint", "show_tool_hint"),
                            ("Verbose Tooltips", "verbose_tooltips"), ("Auto Edit Node", "auto_edit"), ("Dark Mode", "dark_mode")):
            view.add_checkbutton(label=label, variable=self.vars[name], command=lambda n=name: self._toggle(n))
        view.add_separator()
        view.add_command(label="Zoom In", command=lambda: self._do("zoom_in"), accelerator="Ctrl+=")
        view.add_command(label="Zoom Out", command=lambda: self._do("zoom_out"), accelerator="Ctrl+-")
        view.add_command(label="Reset Zoom", command=lambda: self._do("reset_zoom"), accelerator="Ctrl+0")
        view.add_separator()
        view.add_command(label="Set Diagram Size…", command=self.set_diagram_size)
        view.add_command(label="Change Font…", command=self.change_font)
        view.add_command(label="Notification Duration…", command=self.set_duration)
        bar.add_cascade(label="View", menu=view)
        self.root.config(menu=bar)

    def _bind_keys(self) -> None:
        r = self.root
        bindings = {"<Control-n>": lambda: self.new_diagram("Class"), "<Control-o>": self.open_file, "<Control-s>": self.save,
                    "<Control-w>": self.close_current, "<Control-d>": self.duplicate, "<Control-b>": self.copy_to_clipboard,
                    "<Control-q>": self.exit, "<Control-z>": lambda: self._do("undo"), "<Control-y>": lambda: self._do("redo"),
                    "<Control-a>": lambda: self._do("select_all"), "<Control-x>": lambda: self._do("cut_selected"),
                    "<Control-c>": lambda: self._do("copy_selected"), "<Control-v>": lambda: self._do("paste"),
                    "<Delete>": lambda: self._do("delete_selected"), "<Control-Return>": lambda: self._do("request_properties"),
                    "<Control-equal>": lambda: self._do("zoom_in"), "<Control-plus>": lambda: self._do("zoom_in"),
                    "<Control-KP_Add>": lambda: self._do("zoom_in"), "<Control-minus>": lambda: self._do("zoom_out"),
                    "<Control-KP_Subtract>": lambda: self._do("zoom_out"), "<Control-Key-0>": lambda: self._do("reset_zoom")}
        for sequence, command in bindings.items():
            r.bind(sequence, lambda _e, c=command: (c(), "break")[1])

    def _do(self, method: str) -> None:
        if self.document is not None:
            getattr(self.document.editor, method)()

    def _toggle(self, name: str) -> None:
        setattr(self.settings, name, self.vars[name].get())
        if name == "show_tool_hint":
            self._rebuild_toolbar()
        if name == "dark_mode":
            self.apply_theme()
        for document in self.ws.documents:
            document.editor.changed()

    def apply_theme(self) -> None:
        dark = self.settings.dark_mode
        background, foreground = ("#2b2f36", "#f0f0f0") if dark else ("#f0f0f0", "#000000")
        for frame in (self.root, self.toolbar, self.stage, self.welcome):
            frame.configure(bg=background)
        for widget in (*self.toolbar.winfo_children(), *self.welcome.winfo_children()):
            if isinstance(widget, tk.Label):
                widget.configure(bg=background, fg=foreground)

    # ---------- arquivos (RF01–RF06) ----------
    def new_diagram(self, kind: str) -> None:
        self._add_view(self.ws.new_document(kind))

    def open_file(self, path=None) -> None:
        chosen = path or filedialog.askopenfilename(filetypes=[("JetUML JSON", "*.json")])
        if not chosen:
            return
        try:
            self._add_view(self.ws.open(chosen))
        except ValidationError as exc:
            messagebox.showerror("Falha ao abrir", exc.message)

    def _fill_recent(self) -> None:
        self.recent_menu.delete(0, "end")
        for path in self.settings.recent_files:
            self.recent_menu.add_command(label=path, command=lambda p=path: self.open_file(p))
        if not self.settings.recent_files:
            self.recent_menu.add_command(label="(vazio)", state="disabled")

    def save(self) -> bool:
        doc = self.document
        if doc is None:
            return False
        if doc.path is None:
            return self.save_as()
        return self._write(doc, None)

    def save_as(self) -> bool:
        doc = self.document
        if doc is None:
            return False
        chosen = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JetUML JSON", "*.json")])
        return bool(chosen) and self._write(doc, chosen)

    def _write(self, doc, path) -> bool:
        try:
            self.ws.save(doc, path)
        except OSError as exc:
            messagebox.showerror("Falha ao salvar", str(exc))
            return False
        self._update_title(doc)
        return True

    def close_current(self) -> None:
        doc = self.document
        if doc is not None and self._confirm_discard(doc):
            view = self.views.pop(doc)
            self.notebook.forget(view.frame)
            view.frame.destroy()
            self.ws.close(doc)
            self._refresh_stage()
            self._rebuild_toolbar()

    def _confirm_discard(self, doc) -> bool:
        if not doc.dirty:
            return True
        answer = messagebox.askyesnocancel("Salvar alterações", f"Salvar {doc.display_title.rstrip(' *')} antes de fechar?")
        if answer is None:
            return False
        return self.save() if answer else True

    def duplicate(self) -> None:
        if self.document is not None:
            self._add_view(self.ws.duplicate(self.document))

    def export_image(self) -> None:
        doc = self.document
        if doc is None:
            return
        chosen = filedialog.asksaveasfilename(defaultextension=".png", filetypes=[
            ("PNG image", "*.png"), ("JPEG image", "*.jpg *.jpeg"), ("SVG image", "*.svg")])
        if chosen:
            try:
                self.ws.export_image(doc, chosen)
            except (ValidationError, RuntimeError, OSError) as exc:
                messagebox.showerror("Falha ao exportar", getattr(exc, "message", str(exc)))

    def copy_to_clipboard(self) -> None:
        if self.document is not None:
            self.ws.copy_to_clipboard(self.document)

    def exit(self) -> None:
        for doc in list(self.ws.documents):
            self.ws.active = doc
            self.notebook.select(self.views[doc].frame)
            if not self._confirm_discard(doc):
                return
        self.settings.save()
        self.root.destroy()

    # ---------- propriedades (RF22) ----------
    def edit_properties(self, document, element) -> None:
        editor = document.editor
        fields = editor.fields_for(element)
        title = f"Propriedades — {getattr(element, 'TYPE', None) or element.kind.value}"
        FormDialog(self.root, title, fields, editor.values_for(element),
                   lambda values: editor.apply_properties(element, values) or None)

    # ---------- View ----------
    def set_diagram_size(self) -> None:
        width, height = self.ws.settings.effective_size(self.ws.screen)
        fields = [Field("width", "Width", "int", (str(MIN_SIZE), str(MAX_SIZE))), Field("height", "Height", "int", (str(MIN_SIZE), str(MAX_SIZE)))]
        def apply(values):
            try:
                self.ws.set_diagram_size(values["width"], values["height"])
            except ValueError as exc:
                messagebox.showerror("Tamanho inválido", str(exc))
                return False
        FormDialog(self.root, "Set Diagram Size", fields, {"width": width, "height": height}, apply,
                   [("Restore Defaults", self.ws.restore_diagram_size)])

    def change_font(self) -> None:
        from .. import textfmt
        families = tuple(sorted(set(tkfont.families())))
        fields = [Field("family", "Font", "choice", families), Field("size", "Size", "int", ("6", "48"))]
        def apply(values):
            try:
                self.ws.set_font(values["family"], values["size"])
            except ValueError as exc:
                messagebox.showerror("Fonte inválida", str(exc))
                return False
        FormDialog(self.root, "Change Font", fields, {"family": textfmt.METRICS.family, "size": textfmt.METRICS.size}, apply,
                   [("Restore Default", self.ws.restore_font)])

    def set_duration(self) -> None:
        fields = [Field("seconds", "Seconds (0 = off)", "int", ("0", "10"))]
        def apply(values):
            try:
                self.settings.set_notification_duration(values["seconds"])
            except ValueError as exc:
                messagebox.showerror("Duração inválida", str(exc))
                return False
        FormDialog(self.root, "Notification Duration", fields, {"seconds": self.settings.notification_duration}, apply,
                   [("Restore Default", self.settings.restore_notification_duration)])

    # ---------- notificações (RF41, RF42) ----------
    def _toast(self, notification) -> None:
        label = tk.Label(self.toasts, text=notification.message, fg="white", padx=10, pady=5, anchor="w",
                         bg=ERROR_BG if notification.level == "error" else INFO_BG)
        label.pack(side="bottom", anchor="w", pady=2)
        self.root.after(max(1, self.settings.notification_duration) * 1000, label.destroy)
