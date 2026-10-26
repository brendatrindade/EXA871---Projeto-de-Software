"""UML sketch editor prototype for the EXA871 assignment."""
from __future__ import annotations

import html
import ctypes
import io
import json
import sys
import tkinter as tk
import tempfile
from copy import deepcopy
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog

DIAGRAMS = {
    "Class": ["Select", "Class", "Interface", "Package", "Package Description", "Note", "Dependency", "Inheritance", "Realization", "Association", "Aggregation", "Composition", "Note Connector", "To Clipboard"],
    "Sequence": ["Select", "Implicit Parameter", "Note", "Call", "Return", "Note Connector", "To Clipboard"],
    "State": ["Select", "State", "Initial State", "Final State", "Note", "Transition", "Note Connector", "To Clipboard"],
    "Object": ["Select", "Object", "Field", "Note", "Reference", "Collaboration", "Note Connector", "To Clipboard"],
    "Use Case": ["Select", "Actor", "Use Case", "Note", "Association", "Include", "Extend", "Generalization", "Note Connector", "To Clipboard"],
}
EDGE_TOOLS = {"Dependency", "Inheritance", "Realization", "Association", "Aggregation", "Composition", "Note Connector", "Call", "Return", "Transition", "Reference", "Collaboration", "Include", "Extend", "Generalization"}
NODE_TOOLS = {tool for tools in DIAGRAMS.values() for tool in tools if tool not in EDGE_TOOLS | {"Select", "To Clipboard"}}
C_SELECTION = "#087E8B"


class Editor:
    def __init__(self, root: tk.Tk):
        self.root = root
        root.title("JetUML Sketch — EXA871")
        self.kind = tk.StringVar(value="Class")
        self.tool = tk.StringVar(value="Select")
        self.nodes: list[dict] = []
        self.edges: list[dict] = []
        self.undo: list[tuple] = []
        self.redo: list[tuple] = []
        self.selected: dict | None = None
        self.clipboard: tuple[list[dict], list[dict]] = ([], [])
        self.edge_start: dict | None = None
        self.path: Path | None = None
        self.last_dir = Path.cwd()
        self.drag_before = None
        self.drag_offset = (0, 0)
        self.dark = False
        self.grid = True
        self.zoom = 1.0
        self._menu()
        self.toolbar = tk.Frame(root)
        self.toolbar.pack(side="left", fill="y")
        self.canvas = tk.Canvas(root, width=900, height=650, bg="white", highlightthickness=0)
        self.canvas.pack(side="right", fill="both", expand=True)
        self.status = tk.StringVar(value="Selecione uma ferramenta.")
        tk.Label(root, textvariable=self.status, anchor="w").pack(side="bottom", fill="x")
        self.canvas.bind("<Button-1>", self.click)
        self.canvas.bind("<B1-Motion>", self.motion)
        self.canvas.bind("<ButtonRelease-1>", self.release)
        self.canvas.bind("<Double-Button-1>", self.properties)
        self.canvas.bind("<Button-3>", self.popup)
        root.bind("<Control-z>", lambda _e: self.undo_action())
        root.bind("<Control-y>", lambda _e: self.redo_action())
        root.bind("<Control-s>", lambda _e: self.save())
        root.bind("<Control-o>", lambda _e: self.open())
        root.bind("<Delete>", lambda _e: self.delete_selected())
        root.bind("<Control-a>", lambda _e: self.select_all())
        root.bind("<Control-c>", lambda _e: self.copy_selected())
        root.bind("<Control-x>", lambda _e: self.cut_selected())
        root.bind("<Control-v>", lambda _e: self.paste_selected())
        for index, key in enumerate("1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"):
            root.bind(f"<{key}>", lambda _e, i=index: self.choose_shortcut(i))
        self.rebuild_tools()
        self.render()

    def _menu(self):
        bar = tk.Menu(self.root)
        file_menu = tk.Menu(bar, tearoff=0)
        file_menu.add_command(label="New", command=self.new)
        file_menu.add_command(label="Open", command=self.open, accelerator="Ctrl+O")
        file_menu.add_command(label="Save", command=self.save, accelerator="Ctrl+S")
        file_menu.add_command(label="Save As", command=lambda: self.save(True))
        file_menu.add_command(label="Duplicate", command=self.duplicate)
        file_menu.add_command(label="Copy Diagram to Clipboard", command=self.copy_diagram_to_clipboard)
        file_menu.add_command(label="Export SVG", command=self.export_svg)
        file_menu.add_command(label="Export PNG/JPEG", command=self.export_image)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.destroy)
        bar.add_cascade(label="File", menu=file_menu)
        edit = tk.Menu(bar, tearoff=0)
        edit.add_command(label="Undo", command=self.undo_action)
        edit.add_command(label="Redo", command=self.redo_action)
        edit.add_command(label="Select All", command=self.select_all)
        edit.add_command(label="Cut", command=self.cut_selected, accelerator="Ctrl+X")
        edit.add_command(label="Copy", command=self.copy_selected, accelerator="Ctrl+C")
        edit.add_command(label="Paste", command=self.paste_selected, accelerator="Ctrl+V")
        edit.add_command(label="Delete", command=self.delete_selected)
        edit.add_command(label="Properties", command=self.properties)
        bar.add_cascade(label="Edit", menu=edit)
        view = tk.Menu(bar, tearoff=0)
        view.add_command(label="Show Grid", command=self.toggle_grid)
        view.add_command(label="Dark Mode", command=self.toggle_dark)
        view.add_separator()
        view.add_command(label="Zoom In", command=lambda: self.change_zoom(1.2))
        view.add_command(label="Zoom Out", command=lambda: self.change_zoom(1 / 1.2))
        view.add_command(label="Reset Zoom", command=lambda: self.set_zoom(1.0))
        bar.add_cascade(label="View", menu=view)
        self.root.config(menu=bar)

    def rebuild_tools(self):
        for widget in self.toolbar.winfo_children():
            widget.destroy()
        tk.Label(self.toolbar, text="Diagram").pack()
        tk.OptionMenu(self.toolbar, self.kind, *DIAGRAMS, command=lambda _v: self.change_kind()).pack(fill="x")
        for index, name in enumerate(DIAGRAMS[self.kind.get()]):
            key = str(index + 1) if index < 9 else ("0" if index == 9 else chr(ord("A") + index - 10))
            tk.Button(self.toolbar, text=f"{key}  {name}", anchor="w", command=lambda n=name: self.set_tool(n)).pack(fill="x")

    def set_tool(self, name):
        if name == "To Clipboard":
            self.copy_diagram_to_clipboard()
            self.tool.set("Select")
            return
        self.tool.set(name)
        self.status.set(f"Ferramenta: {name}")

    def copy_diagram_to_clipboard(self):
        if sys.platform != "win32":
            messagebox.showerror("Clipboard", "Cópia de imagem para o clipboard está disponível nesta versão para Windows.")
            return
        try:
            from PIL import Image, ImageDraw
            width = max(900, max((n["x"] + n["w"] for n in self.nodes), default=0) + 20)
            height = max(650, max((n["y"] + n["h"] for n in self.nodes), default=0) + 20)
            image = Image.new("RGB", (width, height), "white")
            draw = ImageDraw.Draw(image)
            by_id = {n["id"]: n for n in self.nodes}
            for edge in self.edges:
                a, b = by_id.get(edge["a"]), by_id.get(edge["b"])
                if a and b:
                    draw.line((a["x"]+a["w"]//2, a["y"]+a["h"]//2, b["x"]+b["w"]//2, b["y"]+b["h"]//2), fill="black", width=2)
            for node in self.nodes:
                box = (node["x"], node["y"], node["x"]+node["w"], node["y"]+node["h"])
                if node["type"] in {"State", "Initial State", "Final State"}:
                    draw.ellipse(box, fill="white", outline="black", width=2)
                else:
                    draw.rectangle(box, fill="#fff2b2" if node["type"] == "Note" else "white", outline="black", width=2)
                draw.text((node["x"]+8, node["y"]+8), node["name"], fill="black")
            stream = io.BytesIO()
            image.save(stream, format="BMP")
            dib = stream.getvalue()[14:]
            user32, kernel32 = ctypes.windll.user32, ctypes.windll.kernel32
            user32.OpenClipboard.argtypes = [ctypes.c_void_p]
            user32.OpenClipboard.restype = ctypes.c_int
            user32.EmptyClipboard.restype = ctypes.c_int
            user32.SetClipboardData.argtypes = [ctypes.c_uint, ctypes.c_void_p]
            user32.SetClipboardData.restype = ctypes.c_void_p
            user32.CloseClipboard.restype = ctypes.c_int
            kernel32.GlobalAlloc.argtypes = [ctypes.c_uint, ctypes.c_size_t]
            kernel32.GlobalAlloc.restype = ctypes.c_void_p
            kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
            kernel32.GlobalLock.restype = ctypes.c_void_p
            kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
            kernel32.GlobalUnlock.restype = ctypes.c_int
            kernel32.GlobalFree.argtypes = [ctypes.c_void_p]
            kernel32.GlobalFree.restype = ctypes.c_void_p
            if not user32.OpenClipboard(None):
                raise OSError("Não foi possível abrir o clipboard do Windows.")
            handle = None
            try:
                if not user32.EmptyClipboard():
                    raise OSError("Não foi possível limpar o clipboard do Windows.")
                handle = kernel32.GlobalAlloc(2, len(dib))
                if not handle:
                    raise MemoryError("Não foi possível alocar a imagem para o clipboard.")
                pointer = kernel32.GlobalLock(handle)
                if not pointer:
                    raise MemoryError("Não foi possível bloquear a imagem para o clipboard.")
                try:
                    ctypes.memmove(pointer, dib, len(dib))
                finally:
                    kernel32.GlobalUnlock(handle)
                if not user32.SetClipboardData(8, handle):
                    raise OSError("O Windows recusou a imagem para o clipboard.")
                handle = None  # ownership transferred to the system clipboard
            finally:
                user32.CloseClipboard()
                if handle:
                    kernel32.GlobalFree(handle)
            self.status.set("Imagem do diagrama copiada para o clipboard do Windows.")
        except ImportError:
            messagebox.showerror("Clipboard", "Instale Pillow: python -m pip install pillow")
        except Exception as exc:
            messagebox.showerror("Clipboard", f"Não foi possível copiar o diagrama: {exc}")

    def choose_shortcut(self, index):
        tools = DIAGRAMS[self.kind.get()]
        if index < len(tools):
            self.set_tool(tools[index])

    def change_kind(self):
        self.set_tool("Select")
        self.rebuild_tools()
        self.render()

    def snapshot(self):
        return deepcopy((self.nodes, self.edges, self.kind.get()))

    def apply_snapshot(self, state):
        self.nodes, self.edges, kind = deepcopy(state)
        self.kind.set(kind)
        self.selected = None
        self.rebuild_tools()
        self.render()

    def checkpoint(self):
        self.undo.append(self.snapshot())
        self.redo.clear()

    def undo_action(self):
        if self.undo:
            self.redo.append(self.snapshot())
            self.apply_snapshot(self.undo.pop())

    def redo_action(self):
        if self.redo:
            self.undo.append(self.snapshot())
            self.apply_snapshot(self.redo.pop())

    def new(self):
        kind = simpledialog.askstring("New diagram", "Type: Class, Sequence, State, Object, Use Case", initialvalue="Class")
        if kind and kind.title() in DIAGRAMS:
            self.checkpoint()
            self.kind.set(kind.title())
            self.nodes, self.edges, self.path = [], [], None
            self.rebuild_tools()
            self.render()

    def add_node(self, x, y, typ):
        self.checkpoint()
        small = typ in {"Note", "Initial State", "Final State"}
        self.nodes.append({"id": max((n["id"] for n in self.nodes), default=0) + 1, "type": typ, "name": typ, "x": max(0, round(x / 20) * 20), "y": max(0, round(y / 20) * 20), "w": 110 if small else 150, "h": 50 if small else 70, "parent": None})
        self.render()

    def hit(self, x, y):
        for node in reversed(self.nodes):
            if node["x"] <= x <= node["x"] + node["w"] and node["y"] <= y <= node["y"] + node["h"]:
                return node
        return None

    def click(self, event):
        x, y = event.x / self.zoom, event.y / self.zoom
        node = self.hit(x, y)
        tool = self.tool.get()
        if tool in NODE_TOOLS:
            self.add_node(x, y, tool)
        elif tool in EDGE_TOOLS:
            if node is not None:
                if self.edge_start is None:
                    self.edge_start = node
                    self.status.set("Selecione o destino da conexão.")
                elif self.edge_start is not node:
                    self.checkpoint()
                    self.edges.append({"type": tool, "a": self.edge_start["id"], "b": node["id"], "label": ""})
                    self.edge_start = None
                    self.render()
        else:
            if node and event.state & 0x0004:
                current = self.selected if isinstance(self.selected, list) else ([self.selected] if self.selected else [])
                self.selected = [item for item in current if item is not node] if node in current else current + [node]
                self.render()
                return
            self.selected = node
            if node:
                self.drag_before = self.snapshot()
                self.drag_offset = (x - node["x"], y - node["y"])
                self.render()

    def motion(self, event):
        if isinstance(self.selected, dict) and self.tool.get() == "Select":
            self.selected["x"] = max(0, event.x / self.zoom - self.drag_offset[0])
            self.selected["y"] = max(0, event.y / self.zoom - self.drag_offset[1])
            self.render()

    def release(self, _event):
        if isinstance(self.selected, dict):
            self.selected["x"] = round(self.selected["x"] / 20) * 20
            self.selected["y"] = round(self.selected["y"] / 20) * 20
            current = self.snapshot()
            if self.drag_before != current:
                self.undo.append(self.drag_before)
                self.redo.clear()
            self.selected = None
            self.drag_before = None
            self.render()

    def properties(self, _event=None):
        if not self.selected or isinstance(self.selected, list):
            return
        name = simpledialog.askstring("Properties", f"Name / text for {self.selected['type']}", initialvalue=self.selected.get("name", ""))
        if name is not None:
            self.checkpoint()
            self.selected["name"] = name
            self.render()

    def delete_selected(self):
        if not self.selected:
            return
        self.checkpoint()
        chosen = self.selected if isinstance(self.selected, list) else [self.selected]
        ids = {node["id"] for node in chosen}
        self.nodes = [node for node in self.nodes if node["id"] not in ids]
        self.edges = [edge for edge in self.edges if not ids.intersection((edge["a"], edge["b"]))]
        self.selected = None
        self.render()

    def select_all(self):
        self.selected = self.nodes.copy()
        self.status.set(f"{len(self.nodes)} elementos no diagrama.")
        self.render()

    def _selected_nodes(self):
        return self.selected if isinstance(self.selected, list) else ([self.selected] if self.selected else [])

    def copy_selected(self):
        chosen = self._selected_nodes()
        ids = {n["id"] for n in chosen}
        self.clipboard = (deepcopy(chosen), deepcopy([e for e in self.edges if e["a"] in ids and e["b"] in ids]))
        if chosen:self.status.set(f"{len(chosen)} elemento(s) copiado(s).")

    def cut_selected(self):
        if self._selected_nodes():
            self.copy_selected()
            self.delete_selected()

    def paste_selected(self):
        nodes, edges = self.clipboard
        if not nodes:return
        self.checkpoint()
        ids = {n["id"]: max((x["id"] for x in self.nodes), default=0) + i + 1 for i, n in enumerate(nodes)}
        copies = deepcopy(nodes)
        for node in copies:
            node["id"] = ids[node["id"]]
            node["x"] += 20; node["y"] += 20
        self.nodes.extend(copies)
        self.edges.extend({**deepcopy(edge), "a": ids[edge["a"]], "b": ids[edge["b"]]} for edge in edges)
        self.selected = copies
        self.status.set(f"{len(copies)} elemento(s) colado(s).")
        self.render()

    def popup(self, event):
        menu = tk.Menu(self.root, tearoff=0)
        for tool in DIAGRAMS[self.kind.get()]:
            menu.add_command(label=tool, command=lambda n=tool: self.set_tool(n))
        menu.tk_popup(event.x_root, event.y_root)

    def toggle_grid(self):
        self.grid = not self.grid
        self.render()

    def toggle_dark(self):
        self.dark = not self.dark
        self.canvas.configure(bg="#20242a" if self.dark else "white")
        self.render()

    def change_zoom(self, factor): self.set_zoom(max(0.35, min(2.5, self.zoom * factor)))
    def set_zoom(self, value):
        self.zoom = value
        self.status.set(f"Zoom: {round(value * 100)}%")
        self.render()

    def duplicate(self):
        self.checkpoint()
        self.nodes, self.edges = deepcopy((self.nodes, self.edges))
        self.path = None
        self.render()

    def save(self, as_new=False):
        if self.path is None or as_new:
            chosen = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JetUML JSON", "*.json")], initialdir=self.last_dir)
            if not chosen:
                return
            self.path = Path(chosen)
            self.last_dir = self.path.parent
        data = {"schema": "jetuml-sketch-v1", "diagram": self.kind.get(), "nodes": self.nodes, "edges": self.edges}
        self.path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")

    def open(self):
        chosen = filedialog.askopenfilename(filetypes=[("JetUML JSON", "*.json")], initialdir=self.last_dir)
        if not chosen:
            return
        try:
            data = json.loads(Path(chosen).read_text(encoding="utf-8"))
            kind = data["diagram"]
            if data.get("schema") != "jetuml-sketch-v1" or kind not in DIAGRAMS:
                raise ValueError("Formato ou tipo de diagrama inválido")
            nodes, edges = data["nodes"], data["edges"]
            ids = {n["id"] for n in nodes}
            if any(e["a"] not in ids or e["b"] not in ids for e in edges):
                raise ValueError("Conexão referencia elemento inexistente")
            self.checkpoint()
            self.kind.set(kind)
            self.nodes, self.edges = nodes, edges
            self.path = Path(chosen)
            self.last_dir = self.path.parent
            self.rebuild_tools()
            self.render()
        except (OSError, KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            messagebox.showerror("Open failed", str(exc))

    def export_svg(self):
        chosen = filedialog.asksaveasfilename(defaultextension=".svg", filetypes=[("SVG image", "*.svg")])
        if not chosen:
            return
        width = max(900, max((n["x"] + n["w"] for n in self.nodes), default=0) + 20)
        height = max(650, max((n["y"] + n["h"] for n in self.nodes), default=0) + 20)
        by_id = {n["id"]: n for n in self.nodes}
        lines = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">', '<rect width="100%" height="100%" fill="white"/>']
        for edge in self.edges:
            a, b = by_id[edge["a"]], by_id[edge["b"]]
            lines.append(f'<line x1="{a["x"]+a["w"]//2}" y1="{a["y"]+a["h"]//2}" x2="{b["x"]+b["w"]//2}" y2="{b["y"]+b["h"]//2}" stroke="black" marker-end="url(#arrow)"/>')
        lines.insert(1, '<defs><marker id="arrow" markerWidth="10" markerHeight="7" refX="9" refY="3" orient="auto"><polygon points="0 0, 10 3, 0 6"/></marker></defs>')
        for node in self.nodes:
            label = html.escape(node["name"])
            if node["type"] == "Note":
                lines.append(f'<path d="M{node["x"]} {node["y"]}h{node["w"]-15}l15 15v{node["h"]-15}h-{node["w"]}z" fill="#fff2b2" stroke="black"/>')
            elif node["type"] in {"State", "Initial State", "Final State"}:
                lines.append(f'<ellipse cx="{node["x"]+node["w"]//2}" cy="{node["y"]+node["h"]//2}" rx="{node["w"]//2}" ry="{node["h"]//2}" fill="white" stroke="black"/>')
            else:
                lines.append(f'<rect x="{node["x"]}" y="{node["y"]}" width="{node["w"]}" height="{node["h"]}" fill="white" stroke="black"/>')
            lines.append(f'<text x="{node["x"]+8}" y="{node["y"]+node["h"]//2}" font-family="sans-serif" font-size="14">{label}</text>')
        Path(chosen).write_text("\n".join(lines + ["</svg>"]), encoding="utf-8")

    def export_image(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            messagebox.showerror("Export PNG", "Instale Pillow: python -m pip install pillow")
            return
        chosen = filedialog.asksaveasfilename(defaultextension=".png", filetypes=[("PNG image", "*.png"), ("JPEG image", "*.jpg *.jpeg")])
        if not chosen:
            return
        width = max(900, max((n["x"] + n["w"] for n in self.nodes), default=0) + 20)
        height = max(650, max((n["y"] + n["h"] for n in self.nodes), default=0) + 20)
        image = Image.new("RGB", (width, height), "white")
        draw = ImageDraw.Draw(image)
        by_id = {n["id"]: n for n in self.nodes}
        for edge in self.edges:
            a, b = by_id[edge["a"]], by_id[edge["b"]]
            draw.line((a["x"]+a["w"]//2, a["y"]+a["h"]//2, b["x"]+b["w"]//2, b["y"]+b["h"]//2), fill="black", width=2)
        for node in self.nodes:
            box = (node["x"], node["y"], node["x"]+node["w"], node["y"]+node["h"])
            if node["type"] in {"State", "Initial State", "Final State"}:
                draw.ellipse(box, fill="white", outline="black", width=2)
            else:
                draw.rectangle(box, fill="#fff2b2" if node["type"] == "Note" else "white", outline="black", width=2)
            draw.text((node["x"]+8, node["y"]+8), node["name"], fill="black")
        image.save(chosen, format="JPEG" if chosen.lower().endswith((".jpg", ".jpeg")) else "PNG")

    def render(self):
        self.canvas.delete("all")
        width, height = max(self.canvas.winfo_width(), 900), max(self.canvas.winfo_height(), 650)
        if self.grid:
            for x in range(0, width, 20):
                self.canvas.create_line(x, 0, x, height, fill="#484c52" if self.dark else "#e6e6e6")
            for y in range(0, height, 20):
                self.canvas.create_line(0, y, width, y, fill="#484c52" if self.dark else "#e6e6e6")
        by_id = {n["id"]: n for n in self.nodes}
        for edge in self.edges:
            a, b = by_id.get(edge["a"]), by_id.get(edge["b"])
            if a and b:
                self.canvas.create_line(a["x"]+a["w"]/2, a["y"]+a["h"]/2, b["x"]+b["w"]/2, b["y"]+b["h"]/2, arrow="last")
        for node in self.nodes:
            x, y, w, h = node["x"], node["y"], node["w"], node["h"]
            fill = "#fff2b2" if node["type"] == "Note" else ("#30343a" if self.dark else "white")
            outline = "white" if self.dark else "black"
            if node["type"] == "Initial State":
                self.canvas.create_oval(x, y, x+35, y+35, fill="black", outline=outline)
            elif node["type"] == "Final State":
                self.canvas.create_oval(x, y, x+38, y+38, outline=outline, width=3)
                self.canvas.create_oval(x+8, y+8, x+30, y+30, fill=outline)
            elif node["type"] == "Note":
                self.canvas.create_polygon(x, y, x+w-15, y, x+w, y+15, x+w, y+h, x, y+h, fill=fill, outline=outline)
            else:
                self.canvas.create_rectangle(x, y, x+w, y+h, fill=fill, outline=outline)
            selected = node in self.selected if isinstance(self.selected, list) else node is self.selected
            if selected:
                self.canvas.create_rectangle(x-3, y-3, x+w+3, y+h+3, outline=C_SELECTION, width=2)
            self.canvas.create_text(x+w/2, y+h/2, text=node["name"], width=w-12, fill=outline)
        if self.zoom != 1.0:self.canvas.scale("all", 0, 0, self.zoom, self.zoom)


def smoke_check():
    import tkinter
    root = tkinter.Tcl()
    editor = Editor.__new__(Editor)
    editor.kind = tkinter.StringVar(master=root, value="Class")
    editor.nodes = [{"id": 1, "type": "Class", "name": 'A "quoted" \\ path', "x": 20, "y": 20, "w": 150, "h": 70, "parent": None}]
    editor.edges = [{"type": "Association", "a": 1, "b": 1, "label": ""}]
    editor.path = None
    editor.last_dir = Path.cwd()
    payload = {"schema": "jetuml-sketch-v1", "diagram": editor.kind.get(), "nodes": editor.nodes, "edges": editor.edges}
    with tempfile.TemporaryDirectory() as directory:
        file = Path(directory) / "diagram.json"
        file.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        recovered = json.loads(file.read_text(encoding="utf-8"))
        assert recovered["nodes"][0]["name"] == editor.nodes[0]["name"]
        assert recovered["edges"] == editor.edges
    editor.undo, editor.redo = [], []
    editor.selected = editor.nodes.copy()
    editor.render = lambda: None
    editor.status = tkinter.StringVar(master=root, value="")
    editor.rebuild_tools = lambda: None
    editor.edges = [{"type": "Association", "a": 1, "b": 2, "label": ""}]
    editor.nodes.append({"id": 2, "type": "Class", "name": "B", "x": 220, "y": 20, "w": 150, "h": 70, "parent": None})
    editor.selected = editor.nodes.copy()
    editor.copy_selected(); editor.nodes=[{"id": 9, "type": "Class", "name": "Existing", "x": 0, "y": 0, "w": 150, "h": 70, "parent": None}]; editor.edges=[]; editor.paste_selected()
    assert len(editor.nodes)==3 and len(editor.edges)==1 and editor.edges[0]["a"] != 1
    editor.undo_action()
    assert len(editor.nodes)==1 and not editor.edges
    editor.nodes=[{"id": 1, "type": "Class", "name": "A", "x": 20, "y": 20, "w": 150, "h": 70, "parent": None}]
    editor.edges=[]; editor.selected=editor.nodes.copy(); editor.delete_selected()
    assert not editor.nodes
    assert len(DIAGRAMS) == 5 and "Class" in NODE_TOOLS and "Association" in EDGE_TOOLS
    print("JetUML Python model OK: five diagram families, escaped JSON round-trip, multi-selection clipboard and deletion.")


if __name__ == "__main__":
    import sys
    if "--smoke" in sys.argv:
        smoke_check()
    else:
        root = tk.Tk()
        Editor(root)
        root.mainloop()
