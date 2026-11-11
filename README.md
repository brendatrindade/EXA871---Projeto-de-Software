Implementações de **Solitaire** e **JetUML (editor de diagramas UML)** em **Java** e **Python**, para comparação com os softwares de referência

| Projeto | Python 3.10+ (Tkinter) | Java 17+ (Swing) |
|---|---|---|
| Solitaire | `solitaire/python/` | `solitaire/java/` |
| JetUML | `jetuml/python/` | `jetuml/java/` |

## Como executar
Dentro da pasta de cada projeto:
| | Abrir o programa | Testes |
|---|---|---|
| Python | `python main.py` | `python main.py --smoke` |
| Java | `javac Main.java` e `java Main` | `java Main --smoke` |

## Estrutura

Os quatro projetos seguem a mesma divisão em camadas: modelo e regras independentes da interface, ações/edição como objetos testáveis, persistência em JSON e interface só consultando o modelo.

- `solitaire/*/`: cartas, pilhas, ações reversíveis (simples, composta, nula), jogo (regras, histórico, notificação), estratégia automática (decide) separada da execução, persistência, interface.
- `jetuml/*/`: nós/arestas, regras de validação por tipo de diagrama, diagrama de sequência, editor (eventos abstratos), workspace (abas, arquivos, exportação), renderização única (tela, SVG, PNG/JPEG), interface.

## Testes

Cada projeto traz seu conjunto de testes (`--smoke`): Solitaire 23 (Python) e 22 (Java);    
JetUML 59 (Python) e 60 (Java)