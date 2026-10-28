# JetUML Java (editor de diagramas UML)

Requer JDK 17+. Execute `javac Main.java` e `java Main` (o `javac` encontra os pacotes `jetuml/` a partir desta pasta).

- `java Main` abre o editor (Swing).
- `java Main --smoke` roda os 60 testes de modelo, regras, edição, sequência, arquivos e exportação, sem abrir janela.
- O formato de arquivo é o mesmo da versão Python (`jetuml-sketch-v2`, ver `FORMATO.md`): um arquivo salvo por uma versão abre na outra. As preferências também são compartilhadas (`~/.jetuml-sketch/settings.json`).

## Arquitetura (`jetuml/`)

Mesma divisão da versão Python; as dependências apontam sempre "para baixo" (nenhum ciclo entre pacotes, exceto `ui` → tudo).

| Pacote Java | Módulo Python equivalente | Responsabilidade |
|---|---|---|
| `geometry`, `text` | `geometry`, `textfmt` | Pontos/retângulos/grade; métricas de fonte, formatação `/itálico/` `_sublinhado_`, estereótipos « » |
| `model` | `nodes`, `edges`, `diagram`, `sequence`, `layout`, `errors`, `schema` | Um arquivo por tipo de nó; `Edge`/`EdgeKind` (propriedades por tipo e conversões); `Diagram`; `SequenceSupport` (layout vertical e chamadas); `EdgePaths` (caminho das arestas) |
| `catalog` | `catalog` | Ferramentas por diagrama, atalhos 1–9, 0, A… e descrições |
| `rules` | `rules` | Validação de conexões (RF18, RF19) e de diagramas inteiros |
| `persistence` | `serialization` | `DiagramSerializer` + `Json` (leitor/escritor mínimo, sem dependências) |
| `editor` | `editor` | `Editor` (eventos abstratos de mouse, seleção, arrasto, clipboard, propriedades), `History` (desfazer/refazer por fotografias JSON), `Clipboard`, `Mods` |
| `app` | `workspace`, `settings`, `notifications`, `clipboard` | `Workspace`/`Document` (abas, arquivos, exportação), `Settings`, `Notifier`, `SystemClipboard` |
| `render` | `render`, `svg`, `raster` | `DiagramPainter` desenha sobre a interface `Renderer`; implementações `Graphics2DRenderer` (tela, PNG/JPEG, clipboard) e `SvgRenderer` |
| `ui` | `ui/` | Swing: `AppPanel` (menus, abas, barra, boas-vindas, toasts), `DiagramPanel` (canvas e eventos), `FormPanel` (formulário genérico), `AppWindow`/`FormDialog` (cascas de janela) |
| `smoke` | `tests/` | `SmokeTests`, sem JUnit |

## Escolhas idênticas à versão Python

Todas as interpretações do README da versão Python valem aqui (Shift sobre pacote, limiar de 5 px na linha elástica, retas com deslocamento para arestas paralelas, `Generalization Type` em Dependency só muda o estilo, eixo vertical da sequência calculado automaticamente, tamanho padrão = tela, etc.). O layout calculado (tamanhos de nós e caminhos de arestas) foi comparado com o do Python em arquivos dos cinco tipos de diagrama e é idêntico.

## Diferenças próprias do Java

- PNG, JPEG e cópia da imagem para a área de transferência usam apenas a biblioteca padrão (Java2D/ImageIO/AWT): não há dependência opcional nem ferramenta externa por plataforma.
- O arredondamento da grade segue a regra do Python (metades vão para o par), para que os mesmos cliques produzam as mesmas posições.
- `AppPanel` e `FormPanel` são painéis leves e independentes de janela; `AppWindow` e `FormDialog` apenas os hospedam. Isso permitiu exercitar menus, abas, barra lateral, diálogos de propriedades e notificações sem tela.
