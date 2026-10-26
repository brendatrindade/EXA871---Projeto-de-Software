package klondike.game;

/** Embaralha e distribui de novo (RF01). Reversível, para manter o histórico íntegro. */
public final class NewGameAction implements Action {
  private Layout previous;

  @Override public boolean isLegal(Game game) { return game.stock().isEmpty(); }

  @Override public boolean execute(Game game) {
    previous = game.capture();
    game.shuffleAndDeal();
    return true;
  }

  @Override public void undo(Game game) { game.apply(previous); }
}
