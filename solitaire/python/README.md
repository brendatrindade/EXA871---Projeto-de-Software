# Solitaire Python (Klondike)

Requer Python 3.10+ com Tkinter.

- `python main.py` abre o jogo.
- `python main.py --smoke` roda os testes do modelo sem abrir janela (também: `python -m unittest discover -s tests`).

## Estrutura (`klondike/`)

| Módulo | Responsabilidade |
|---|---|
| `cards.py` | `Suit`, `Color`, `Rank` (ordem total), `Card` (identidade única), `Deck` |
| `piles.py` | `Pile` comum; `Foundation` e `TableauColumn` sobrescrevem `can_take` (regras de empilhamento) |
| `actions.py` | `Action` reversível: `DrawAction`, `MoveCardsAction`, `RevealAction`, `NewGameAction`, `CompositeAction`, `NullAction` |
| `game.py` | Regras, consultas somente leitura, histórico/desfazer, notificação, execução (`perform`) |
| `strategy.py` | `Strategy` (decide, sem alterar estado), `AutomaticStrategy` (prioridades RF29), `NullStrategy` |
| `persistence.py` | JSON `solitaire-klondike-v1` (formato documentado no módulo) |
| `geometry.py` | Coordenadas únicas para desenho, clique e arraste |
| `ui.py` | Tkinter; só consulta `Game.can_move` |

Uso: arraste cartas/sequências (a sequência acompanha o mouse e o destino fica verde/vermelho); clique no monte para comprar; monte vazio inicia nova partida; "Estratégia ativa" liga/desliga o jogo automático.
