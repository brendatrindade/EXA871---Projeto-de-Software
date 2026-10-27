"""Ferramentas por tipo de diagrama (RF07), atalhos (RF08) e descrições (RF10)."""
from __future__ import annotations

from dataclasses import dataclass, field

from .edges import EdgeKind as K
from . import nodes as N


@dataclass(frozen=True)
class Tool:
    name: str
    category: str                    # select | node | edge | clipboard
    icon: str
    description: str
    node_cls: type | None = None
    edge_kind: K | None = None
    preset: dict = field(default_factory=dict)


def _node(name, icon, cls, description): return Tool(name, "node", icon, description, node_cls=cls)
def _edge(name, icon, kind, description, **preset): return Tool(name, "edge", icon, description, edge_kind=kind, preset=preset)

SELECT = Tool("Select", category="select", icon="↖", description="Seleciona, move e edita elementos")
CLIPBOARD = Tool("To Clipboard", category="clipboard", icon="⎘", description="Copia a imagem do diagrama para a área de transferência")
NOTE = _node("Note", "▤", N.NoteNode, "Nota com informações complementares")
NOTE_CONNECTOR = _edge("Note Connector", "┄", K.NOTE_CONNECTOR, "Liga uma nota a um nó ou a um ponto fixo")

TOOLS: dict[str, list[Tool]] = {
    "Class": [SELECT, _node("Class", "▭", N.ClassNode, "Classe UML com atributos e métodos"),
              _node("Interface", "◫", N.InterfaceNode, "Interface UML"),
              _node("Package", "▣", N.PackageNode, "Pacote que pode conter outros elementos"),
              _node("Package Description", "▥", N.PackageDescriptionNode, "Pacote com descrição textual"),
              NOTE,
              _edge("Dependency", "⇢", K.DEPENDENCY, "Dependência (seta tracejada)"),
              _edge("Inheritance", "△", K.INHERITANCE, "Herança (generalização)"),
              _edge("Realization", "⇡", K.REALIZATION, "Realização de interface"),
              _edge("Association", "—", K.ASSOCIATION, "Associação"),
              _edge("Aggregation", "◇", K.AGGREGATION, "Agregação"),
              _edge("Composition", "◆", K.COMPOSITION, "Composição"),
              NOTE_CONNECTOR, CLIPBOARD],
    "Sequence": [SELECT, _node("Implicit Parameter", "⏐", N.ImplicitParameterNode, "Parâmetro implícito com linha de vida"),
                 NOTE, _edge("Call", "→", K.CALL, "Chamada de método entre linhas de vida"),
                 _edge("Return", "⇠", K.RETURN, "Retorno de uma chamada"), NOTE_CONNECTOR, CLIPBOARD],
    "State": [SELECT, _node("State", "▢", N.StateNode, "Estado"), _node("Initial State", "●", N.InitialStateNode, "Estado inicial"),
              _node("Final State", "◉", N.FinalStateNode, "Estado final"), NOTE,
              _edge("Transition", "↪", K.TRANSITION, "Transição entre estados"), NOTE_CONNECTOR, CLIPBOARD],
    "Object": [SELECT, _node("Object", "▭", N.ObjectNode, "Objeto"), _node("Field", "≡", N.FieldNode, "Campo dentro de um objeto"),
               NOTE, _edge("Reference", "•→", K.REFERENCE, "Referência da parte Value de um campo a um objeto"),
               _edge("Collaboration", "↔", K.COLLABORATION, "Colaboração entre objetos"), NOTE_CONNECTOR, CLIPBOARD],
    "Use Case": [SELECT, _node("Actor", "☺", N.ActorNode, "Ator"), _node("Use Case", "◯", N.UseCaseNode, "Caso de uso"), NOTE,
                 _edge("Association", "—", K.ASSOCIATION, "Associação"),
                 _edge("Include", "⇢", K.USE_CASE_DEPENDENCY, "Dependência «include»", dependency_type="Include"),
                 _edge("Extend", "⇢", K.USE_CASE_DEPENDENCY, "Dependência «extend»", dependency_type="Extend"),
                 _edge("Generalization", "△", K.GENERALIZATION, "Generalização"), NOTE_CONNECTOR, CLIPBOARD],
}

_SHORTCUTS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def tools_for(diagram_kind: str) -> list[Tool]: return TOOLS[diagram_kind]

def tool(diagram_kind: str, name: str) -> Tool:
    return next(t for t in TOOLS[diagram_kind] if t.name == name)

def shortcut(index: int) -> str: return _SHORTCUTS[index]

def tool_for_shortcut(diagram_kind: str, key: str) -> Tool | None:
    index = _SHORTCUTS.find(key.upper())
    tools = TOOLS[diagram_kind]
    return tools[index] if 0 <= index < len(tools) else None

def allowed_node_types(diagram_kind: str) -> set[str]:
    types = {t.node_cls.TYPE for t in TOOLS[diagram_kind] if t.node_cls}
    types.add(N.PointNode.TYPE)
    if diagram_kind == "Sequence":
        types.add(N.CallNode.TYPE)
    return types

def allowed_edge_kinds(diagram_kind: str) -> set[K]:
    return {t.edge_kind for t in TOOLS[diagram_kind] if t.edge_kind}
