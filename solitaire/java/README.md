# Solitaire Java

Requer JDK 17+. Execute `javac Main.java` e `java Main` (o `javac` encontra os pacotes `klondike/` a partir desta pasta). `java Main --smoke` roda os testes do modelo sem abrir janela.

## Pacotes (`klondike/`)

| Pacote | Responsabilidade |
|---|---|
| `cards` | `Suit`, `Color`, `Rank` (ordem total), `Card` (igualdade por identidade), `Deck` |
| `piles` | `Pile` comum; `Foundation` e `TableauColumn` sobrescrevem `canTake` (regras de empilhamento); `Location` |
| `game` | `Game` (regras, consultas somente leitura, histórico, notificação, `perform`), `Action` e suas implementações (`DrawAction`, `MoveCardsAction`, `RevealAction`, `NewGameAction`, `CompositeAction`, `NullAction`), `Strategy`/`NullStrategy`, `Layout` |
| `strategy` | `AutomaticStrategy`: prioridades de RF29; só decide, não altera o estado |
| `persistence` | `GameStore` + `Json`: formato `solitaire-klondike-v1`, o mesmo da versão Python (arquivos intercambiáveis) |
| `ui` | `SolitaireWindow`, `BoardPanel`, `Geometry`: Swing; só consulta `Game.canMove` |
| `smoke` | `SmokeTests`: verificações executadas por `--smoke` |

`Strategy` fica no pacote `game` e `AutomaticStrategy` em `strategy`, então `game` não depende de `strategy`: a estratégia é injetada em `new Game(random, strategy)`.

Uso: arraste cartas/sequências (a sequência acompanha o mouse; o destino fica verde ou vermelho); clique no monte para comprar; com o monte vazio, inicia nova partida; "Estratégia ativa" liga/desliga o jogo automático; Ctrl+Z desfaz.
