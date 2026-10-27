"""Formato JSON aberto `jetuml-sketch-v2` (RNF02); estrutura documentada em FORMATO.md."""
from __future__ import annotations

import json

from .diagram import DIAGRAM_KINDS, Diagram
from .edges import Edge, EdgeKind
from .errors import ValidationError
from .nodes import NODE_TYPES

SCHEMA = "jetuml-sketch-v2"


def diagram_to_dict(d: Diagram) -> dict:
    return {
        "schema": SCHEMA,
        "diagram": d.kind,
        "nodes": [{"id": n.id, "type": n.TYPE, "x": n.x, "y": n.y,
                   "parent": n.parent.id if n.parent else None, "properties": dict(n.props)} for n in d.nodes],
        "edges": [{"id": e.id, "kind": e.kind.value, "start": e.start.id, "end": e.end.id,
                   "properties": dict(e.props)} for e in d.edges],
    }


def diagram_from_dict(data: dict, validate: bool = True) -> Diagram:
    try:
        if data.get("schema") != SCHEMA:
            raise ValidationError("Formato desconhecido (esperado jetuml-sketch-v2).")
        if data["diagram"] not in DIAGRAM_KINDS:
            raise ValidationError("Tipo de diagrama inválido.")
        d = Diagram(data["diagram"])
        by_id = {}
        for entry in data["nodes"]:
            if entry["type"] not in NODE_TYPES:
                raise ValidationError(f"Tipo de nó desconhecido: {entry['type']}")
            node = NODE_TYPES[entry["type"]](entry["x"], entry["y"])
            node.id = int(entry["id"])
            if node.id in by_id:
                raise ValidationError("Identificadores de nó duplicados.")
            node.props.update(entry.get("properties", {}))
            by_id[node.id] = node
            d.nodes.append(node)
        for entry in data["nodes"]:
            if entry.get("parent") is not None:
                parent = by_id.get(entry["parent"])
                if parent is None:
                    raise ValidationError("Pai inexistente.")
                node = by_id[entry["id"]]
                node.parent = parent
                parent.children.append(node)
        for entry in data["edges"]:
            if entry["start"] not in by_id or entry["end"] not in by_id:
                raise ValidationError("Aresta referencia elemento inexistente.")
            edge = Edge(EdgeKind(entry["kind"]), by_id[entry["start"]], by_id[entry["end"]])
            edge.id = int(entry["id"])
            edge.props.update(entry.get("properties", {}))
            d.edges.append(edge)
        d._next_id = max([n.id for n in d.nodes] + [e.id for e in d.edges] + [0]) + 1
        d.relayout()
        if validate:
            from .rules import validate_diagram
            validate_diagram(d)
        return d
    except (KeyError, TypeError, ValueError, AttributeError) as exc:
        if isinstance(exc, ValidationError):
            raise
        raise ValidationError(f"Arquivo de diagrama malformado: {exc}") from exc


def dumps(d: Diagram) -> str:
    return json.dumps(diagram_to_dict(d), indent=2, ensure_ascii=False)


def loads(text: str) -> Diagram:
    try:
        return diagram_from_dict(json.loads(text))
    except json.JSONDecodeError as exc:
        raise ValidationError(f"JSON inválido: {exc}") from exc
