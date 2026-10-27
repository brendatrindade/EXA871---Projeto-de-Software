# JetUML Python (editor de diagramas UML)

Requer Python 3.10+ com Tkinter. Pillow é opcional (PNG/JPEG e cópia de imagem): `python -m pip install pillow`.

- `python main.py` abre o editor.
- `python main.py --smoke` roda os 57 testes do modelo, regras, edição, sequência, arquivos e exportação, sem abrir janela (também: `python -m unittest discover -s tests`).
- Formato de arquivo: ver `FORMATO.md`.

## Arquitetura (`jetuml/`)

| Módulo | Responsabilidade |
|---|---|
| `geometry`, `textfmt`, `schema`, `errors` | Pontos/retângulos/grade, métricas e formatação de texto, descrição de campos, erro de validação |
| `nodes`, `edges` | Tipos de nó (campos, tamanho, filhos aceitos) e de aresta (propriedades por tipo, conversão Aggregation↔Composition, Inheritance↔Realization) |
| `diagram` | Nós/arestas, pai/filho, layout, consultas (`node_at`, `container_at`, `edge_at`) |
| `rules` | Validação de conexões por tipo de diagrama (RF18, RF19) |
| `sequence` | Layout vertical automático, criação de chamadas/retornos/autochamadas/construtor, validação de fluxo (RF27–RF31) |
| `layout` | Caminho das arestas, recalculado a partir dos nós (RF17) |
| `catalog` | Ferramentas por diagrama, atalhos 1–9, 0, A… (RF07, RF08), descrições (RF10) |
| `editor` | Controlador de um diagrama: seleção/laço, arrasto, criação, rubber band, cut/copy/paste, desfazer/refazer, propriedades. Recebe eventos abstratos, por isso é testável sem janela |
| `workspace` | Abas/documentos, abrir/salvar/duplicar, exportar, copiar imagem, tamanho e fonte |
| `settings`, `notifications` | Preferências (View, recentes) e notificações |
| `render`, `svg`, `raster`, `clipboard` | Um único desenho (`draw_diagram`) sobre renderizadores Tk, SVG e Pillow; clipboard por plataforma |
| `ui/` | Tkinter: `app` (menus, abas, barra, boas-vindas, toasts), `view` (canvas e eventos), `dialogs` (formulário genérico), `tk_renderer` |

## Cobertura dos requisitos (Anexo II)

Todos os RF e RNF têm implementação; estas são as interpretações e limites que valem registrar:

- **RF10** ícones são glifos Unicode (sem imagens); "Show Tool Hint" acrescenta o nome ao lado do ícone.
- **RF15** Shift ao *soltar* um nó arrastado: sobre um pacote anexa; já filho de pacote, desanexa.
- **RF16** a linha elástica só cria aresta se o arrasto passar de 5 px (clique simples não cria autoaresta).
- **RF17** caminhos são retas entre as bordas dos nós (arestas paralelas ficam deslocadas; autoarestas desenham um laço).
- **RF20** `Generalization Type` em Dependency altera apenas o estilo da linha (tracejada/contínua); em Inheritance/Realization troca o tipo da aresta.
- **RF27–RF31** o eixo vertical do diagrama de sequência é calculado automaticamente a partir da ordem das chamadas; linhas de vida só se movem na horizontal. Chamada a partir de uma caixa que já retornou é o "salto" que falha em silêncio.
- **RF33** Select All seleciona nós e arestas; arrastar um nó selecionado move toda a seleção.
- **RF05** a imagem copiada é recortada ao conteúdo, sem grade, e exige Pillow e: Windows (nativo), macOS (`osascript`) ou Linux (`xclip`/`wl-copy`).
- **RF38** o tamanho padrão é o da tela (limitado a 250–4000); vale para novos diagramas, arquivos abertos e abas existentes.
- **RF40** o modo escuro troca a paleta do canvas e dos painéis; botões e menus seguem o tema do sistema.
- Desfazer/refazer usa fotografias do diagrama (memento), uma entrada por ação do usuário.
- O formato `jetuml-sketch-v2` substitui o `v1` do protótipo anterior (não são compatíveis).
