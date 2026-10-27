"""Nós dos cinco tipos de diagrama. Cada tipo conhece seus campos, tamanho e filhos aceitos."""
from __future__ import annotations

from .geometry import Rect
from .schema import Field
from . import textfmt

FIELD_H = 22
HEADER_H = 40          # cabeçalho de um parâmetro implícito


class Node:
    TYPE = "Node"
    FIELDS: tuple[Field, ...] = ()
    ACCEPTS: tuple[str, ...] = ()      # tipos de filhos aceitos
    MOVABLE = True
    SELECTABLE = True
    MIN_W, MIN_H = 100, 60

    def __init__(self, x: float = 0, y: float = 0, **props) -> None:
        self.id = 0
        self.x, self.y = x, y
        self.w, self.h = self.MIN_W, self.MIN_H
        self.parent: Node | None = None
        self.children: list[Node] = []
        self.props = {f.name: f.default for f in self.FIELDS}
        self.props.update(props)

    @property
    def bounds(self) -> Rect: return Rect(self.x, self.y, self.w, self.h)

    def get(self, name: str): return self.props[name]
    def set(self, name: str, value) -> None: self.props[name] = value

    def accepts(self, node: "Node") -> bool: return node.TYPE in self.ACCEPTS
    def fit(self) -> None: """Recalcula w/h a partir das propriedades."""

    def ancestors(self):
        node = self.parent
        while node is not None:
            yield node
            node = node.parent

    def descendants(self):
        for child in self.children:
            yield child
            yield from child.descendants()

    def _text_box(self, *line_groups: list[str], extra_w: int = 20, header: int = 30) -> None:
        widest = max((textfmt.text_width(l) for g in line_groups for l in g), default=0)
        self.w = max(self.MIN_W, widest + extra_w)
        self.h = header + sum(max(20, len(g) * textfmt.METRICS.line_h + 10) for g in line_groups)


NAME = Field("name", "Name")


class ClassNode(Node):
    TYPE = "Class"
    FIELDS = (Field("name", "Name", default="Class"), Field("attributes", "Attributes", "multiline"),
              Field("methods", "Methods", "multiline"))

    def fit(self) -> None:
        self._text_box([self.get("name")], textfmt.lines_of(self.get("attributes")),
                       textfmt.lines_of(self.get("methods")), header=0)
        self.h = max(self.MIN_H, self.h)


class InterfaceNode(Node):
    TYPE = "Interface"
    FIELDS = (Field("name", "Name", default="Interface"), Field("methods", "Methods", "multiline"))

    def fit(self) -> None:
        self._text_box(["«interface»", self.get("name")], textfmt.lines_of(self.get("methods")), header=0)


class PackageNode(Node):
    TYPE = "Package"
    FIELDS = (Field("name", "Name", default="Package"),)
    ACCEPTS = ("Class", "Interface", "Package", "PackageDescription", "Note")
    MIN_H = 80

    def fit(self) -> None:
        self.w = max(self.MIN_W, textfmt.text_width(self.get("name")) + 30)
        self.h = self.MIN_H
        for child in self.children:
            self.w = max(self.w, child.x + child.w + 10 - self.x)
            self.h = max(self.h, child.y + child.h + 10 - self.y)


class PackageDescriptionNode(Node):
    TYPE = "PackageDescription"
    FIELDS = (Field("name", "Name", default="Package"), Field("contents", "Contents", "multiline"))

    def fit(self) -> None:
        self._text_box([self.get("name")], textfmt.lines_of(self.get("contents")), header=0)


class NoteNode(Node):
    TYPE = "Note"
    FIELDS = (Field("text", "Text", "multiline", default="Note"),)
    MIN_W, MIN_H = 60, 40

    def fit(self) -> None:
        lines = textfmt.lines_of(self.get("text")) or [""]
        self.w = max(self.MIN_W, max(textfmt.text_width(l) for l in lines) + 20)
        self.h = max(self.MIN_H, len(lines) * textfmt.METRICS.line_h + 16)


class FieldNode(Node):
    """Campo de objeto: só existe como filho de ObjectNode (RF25)."""
    TYPE = "Field"
    FIELDS = (Field("name", "Name", default="field"), Field("value", "Value"))
    MOVABLE = False
    MIN_W, MIN_H = 60, FIELD_H

    def label(self) -> str:
        value = self.get("value")
        return f"{self.get('name')} = {value}" if value else self.get("name")


class ObjectNode(Node):
    TYPE = "Object"
    FIELDS = (Field("name", "Name", default="object:Type"),)
    ACCEPTS = ("Field",)

    def fit(self) -> None:
        widest = max([textfmt.text_width(self.get("name"))] + [textfmt.text_width(f.label()) for f in self.children])
        self.w = max(self.MIN_W, widest + 20)
        self.h = 30 + max(20, len(self.children) * FIELD_H + 8)
        for i, child in enumerate(self.children):
            child.x, child.y, child.w, child.h = self.x + 4, self.y + 34 + i * FIELD_H, self.w - 8, FIELD_H


class ActorNode(Node):
    TYPE = "Actor"
    FIELDS = (Field("name", "Name", default="Actor"),)
    MIN_W, MIN_H = 50, 80

    def fit(self) -> None:
        self.w = max(self.MIN_W, textfmt.text_width(self.get("name")) + 10)


class UseCaseNode(Node):
    TYPE = "UseCase"
    FIELDS = (Field("name", "Name", default="Use Case"),)
    MIN_W, MIN_H = 110, 50

    def fit(self) -> None:
        self.w = max(self.MIN_W, textfmt.text_width(self.get("name")) + 40)


class StateNode(Node):
    TYPE = "State"
    FIELDS = (Field("name", "Name", default="State"),)
    MIN_W, MIN_H = 80, 50

    def fit(self) -> None:
        self.w = max(self.MIN_W, textfmt.text_width(self.get("name")) + 30)


class InitialStateNode(Node):
    TYPE = "InitialState"
    MIN_W, MIN_H = 20, 20


class FinalStateNode(Node):
    TYPE = "FinalState"
    MIN_W, MIN_H = 24, 24


class ImplicitParameterNode(Node):
    """Linha de vida: cabeçalho + linha tracejada. A altura vem do layout de sequência."""
    TYPE = "ImplicitParameter"
    FIELDS = (Field("name", "Name", default=":Type"),)
    ACCEPTS = ("Call",)
    MIN_W, MIN_H = 80, HEADER_H + 80

    def fit(self) -> None:
        self.w = max(self.MIN_W, textfmt.text_width(self.get("name")) + 20)
        self.h = self.MIN_H

    def in_header(self, y: float) -> bool:
        return y < self.y + HEADER_H


class CallNode(Node):
    """Caixa de ativação; criada pelas chamadas, nunca diretamente pelo usuário."""
    TYPE = "Call"
    FIELDS = (Field("open_bottom", "Open Bottom", "bool", default=False),)
    MOVABLE = False
    MIN_W, MIN_H = 16, 30


class PointNode(Node):
    """Âncora invisível: extremidade fixa de um Note Connector (RF14)."""
    TYPE = "Point"
    SELECTABLE = False
    MOVABLE = False
    MIN_W, MIN_H = 0, 0


NODE_TYPES = {cls.TYPE: cls for cls in (
    ClassNode, InterfaceNode, PackageNode, PackageDescriptionNode, NoteNode, FieldNode, ObjectNode, ActorNode,
    UseCaseNode, StateNode, InitialStateNode, FinalStateNode, ImplicitParameterNode, CallNode, PointNode)}
