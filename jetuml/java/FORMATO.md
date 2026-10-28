# Formato de arquivo `jetuml-sketch-v2` (RNF02)

JSON em UTF-8, legível e editável por ferramentas externas.

```json
{
  "schema": "jetuml-sketch-v2",
  "diagram": "Class",
  "nodes": [
    {"id": 1, "type": "Class", "x": 100, "y": 100, "parent": null,
     "properties": {"name": "Cliente", "attributes": "- nome: String", "methods": "+ getNome()"}}
  ],
  "edges": [
    {"id": 3, "kind": "Association", "start": 1, "end": 2,
     "properties": {"start_label": "", "middle_label": "tem", "end_label": "", "directionality": "Unspecified"}}
  ]
}
```

- `diagram`: `Class`, `Sequence`, `State`, `Object` ou `Use Case`.
- `nodes[].type`: `Class`, `Interface`, `Package`, `PackageDescription`, `Note`, `Field`, `Object`, `Actor`, `UseCase`, `State`, `InitialState`, `FinalState`, `ImplicitParameter`, `Call` (caixa de ativação), `Point` (âncora de Note Connector).
- `nodes[].parent`: id do pacote/objeto/linha de vida que contém o nó (ou `null`). `Field` só é válido com pai `Object`.
- `edges[].kind`: `Dependency`, `Inheritance`, `Realization`, `Association`, `Aggregation`, `Composition`, `NoteConnector`, `Call`, `Return`, `Transition`, `Reference`, `Collaboration`, `UseCaseDependency`, `Generalization`.
- `edges[].properties`: rótulos (`start_label`, `middle_label`, `end_label`), `directionality`, `generalization_type`, `dependency_type` (`None`/`Include`/`Extend`); chamadas de construtor trazem `"constructor": true`.
- Tamanho e posição dos nós (`w`/`h`), caminhos das arestas e posições verticais do diagrama de sequência **não** são gravados: são recalculados ao abrir.
- Ao abrir, o arquivo é validado com as mesmas regras da edição (tipos de nó/aresta compatíveis, campos dentro de objeto, uma única chamada raiz, um chamador por caixa de ativação etc.). Arquivos inválidos são recusados com mensagem.
