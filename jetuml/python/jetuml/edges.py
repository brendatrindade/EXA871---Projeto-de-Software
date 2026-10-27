"""Arestas e suas propriedades por tipo (RF20)."""
from __future__ import annotations

from enum import Enum

from .schema import Field


class EdgeKind(str, Enum):
    DEPENDENCY = "Dependency"
    INHERITANCE = "Inheritance"
    REALIZATION = "Realization"
    ASSOCIATION = "Association"
    AGGREGATION = "Aggregation"
    COMPOSITION = "Composition"
    NOTE_CONNECTOR = "NoteConnector"
    CALL = "Call"
    RETURN = "Return"
    TRANSITION = "Transition"
    REFERENCE = "Reference"
    COLLABORATION = "Collaboration"
    USE_CASE_DEPENDENCY = "UseCaseDependency"   # Include / Extend / None (RF20)
    GENERALIZATION = "Generalization"


K = EdgeKind
_LABELS = (Field("start_label", "Start Label"), Field("middle_label", "Middle Label"), Field("end_label", "End Label"))
_DIR3 = Field("directionality", "Directionality", "choice", ("Unspecified", "Unidirectional", "Bidirectional"), "Unspecified")
_DIR2 = Field("directionality", "Directionality", "choice", ("Unidirectional", "Bidirectional"), "Unidirectional")
_MIDDLE = (Field("middle_label", "Middle Label"),)

FIELDS_BY_KIND: dict[EdgeKind, tuple[Field, ...]] = {
    K.ASSOCIATION: (*_LABELS, _DIR3),
    K.AGGREGATION: (*_LABELS, _DIR3, Field("aggregation_type", "Aggregation Type", "choice", ("Aggregation", "Composition"))),
    K.COMPOSITION: (*_LABELS, _DIR3, Field("aggregation_type", "Aggregation Type", "choice", ("Aggregation", "Composition"))),
    K.DEPENDENCY: (Field("generalization_type", "Generalization Type", "choice", ("Inheritance", "Implementation"), "Implementation"), _DIR2),
    K.INHERITANCE: (Field("generalization_type", "Generalization Type", "choice", ("Inheritance", "Implementation")), _DIR2),
    K.REALIZATION: (Field("generalization_type", "Generalization Type", "choice", ("Inheritance", "Implementation")), _DIR2),
    K.CALL: _MIDDLE, K.RETURN: _MIDDLE, K.TRANSITION: _MIDDLE, K.COLLABORATION: _MIDDLE,
    K.USE_CASE_DEPENDENCY: (Field("dependency_type", "Dependency Type", "choice", ("None", "Include", "Extend"), "None"),),
}

# Propriedades "derivadas": trocar o valor converte o tipo da aresta (RF20).
_DERIVED = {"aggregation_type": {K.AGGREGATION: "Aggregation", K.COMPOSITION: "Composition"},
            "generalization_type": {K.INHERITANCE: "Inheritance", K.REALIZATION: "Implementation"}}


class Edge:
    def __init__(self, kind: EdgeKind, start, end, **props) -> None:
        self.id = 0
        self.kind, self.start, self.end = kind, start, end
        self.props = {f.name: f.default for f in self.fields() if f.name not in _DERIVED}
        self.props.update(props)

    def fields(self) -> tuple[Field, ...]:
        return FIELDS_BY_KIND.get(self.kind, ())

    def get(self, name: str):
        derived = _DERIVED.get(name, {})
        return derived[self.kind] if self.kind in derived else self.props.get(name, "")

    def set(self, name: str, value) -> None:
        inverse = {v: k for k, v in _DERIVED.get(name, {}).items()}
        if self.kind in _DERIVED.get(name, {}):
            self.kind = inverse[value]
        else:
            self.props[name] = value

    def is_self_edge(self) -> bool: return self.start is self.end
