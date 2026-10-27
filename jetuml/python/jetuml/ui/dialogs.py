"""Diálogos genéricos: um único FormDialog serve a propriedades, tamanho, fonte e duração."""
from __future__ import annotations

import tkinter as tk
from tkinter import messagebox, ttk

from .. import textfmt


class FormDialog:
    """Formulário modal descrito por campos (`schema.Field`, mais kind="int" com choices=(mín, máx)).

    Tab navega entre os campos, Esc fecha, Ctrl+Q insere « » (RF22, RF23).
    `on_ok(values)` retorna False para manter o diálogo aberto.
    """

    def __init__(self, parent, title, fields, values, on_ok, extra_buttons=()) -> None:
        self.top = tk.Toplevel(parent)
        self.top.title(title)
        self.top.transient(parent)
        self.fields, self.on_ok = list(fields), on_ok
        self.widgets: dict[str, tuple[str, object]] = {}
        body = tk.Frame(self.top, padx=10, pady=8)
        body.pack(fill="both", expand=True)
        first = None
        for row, field in enumerate(self.fields):
            tk.Label(body, text=field.label, anchor="w").grid(row=row, column=0, sticky="nw", padx=(0, 8), pady=3)
            widget = self._make(body, field, values.get(field.name, field.default))
            widget.grid(row=row, column=1, sticky="ew", pady=3)
            first = first or widget
        body.columnconfigure(1, weight=1)
        buttons = tk.Frame(self.top, padx=10, pady=8)
        buttons.pack(fill="x")
        tk.Button(buttons, text="OK", command=self.accept).pack(side="right")
        tk.Button(buttons, text="Cancelar", command=self.top.destroy).pack(side="right", padx=6)
        for label, callback in extra_buttons:
            tk.Button(buttons, text=label, command=lambda cb=callback: (cb(), self.top.destroy())).pack(side="left", padx=(0, 6))
        self.top.bind("<Escape>", lambda _e: self.top.destroy())
        self.top.bind("<Control-Return>", lambda _e: self.accept())
        self.top.grab_set()
        if first is not None:
            first.focus_set()

    def _make(self, parent, field, value):
        kind = field.kind
        if kind == "multiline":
            widget = tk.Text(parent, width=34, height=5, wrap="word")
            widget.insert("1.0", str(value))
            widget.bind("<Tab>", lambda e: (e.widget.tk_focusNext().focus_set(), "break")[1])
            widget.bind("<Control-q>", self._stereotype_text)
        elif kind == "choice":
            widget = ttk.Combobox(parent, values=list(field.choices), state="readonly")
            widget.set(str(value))
        elif kind == "bool":
            var = tk.BooleanVar(value=bool(value))
            widget = tk.Checkbutton(parent, variable=var)
            self.widgets[field.name] = (kind, var)
            return widget
        elif kind == "int":
            low, high = (int(v) for v in field.choices)
            widget = tk.Spinbox(parent, from_=low, to=high, width=8)
            widget.delete(0, "end")
            widget.insert(0, str(value))
        else:
            widget = tk.Entry(parent, width=36)
            widget.insert(0, str(value))
            widget.bind("<Control-q>", self._stereotype_entry)
            widget.bind("<Return>", lambda _e: self.accept())
        self.widgets[field.name] = (kind, widget)
        return widget

    @staticmethod
    def _stereotype_text(event):
        event.widget.insert("insert", textfmt.OPEN + textfmt.CLOSE)
        event.widget.mark_set("insert", "insert-1c")
        return "break"

    @staticmethod
    def _stereotype_entry(event):
        entry = event.widget
        text, cursor = textfmt.insert_stereotype(entry.get(), entry.index("insert"))
        entry.delete(0, "end")
        entry.insert(0, text)
        entry.icursor(cursor)
        return "break"

    def collect(self) -> dict:
        values = {}
        for name, (kind, widget) in self.widgets.items():
            if kind == "multiline":
                values[name] = widget.get("1.0", "end-1c")
            elif kind == "bool":
                values[name] = bool(widget.get())
            elif kind == "int":
                values[name] = int(widget.get())
            else:
                values[name] = widget.get()
        return values

    def accept(self) -> None:
        try:
            values = self.collect()
        except ValueError:
            messagebox.showerror("Valor inválido", "Informe números inteiros.", parent=self.top)
            return
        if self.on_ok(values) is not False:
            self.top.destroy()
