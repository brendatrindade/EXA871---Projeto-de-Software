package klondike.game;

/** Monte → descarte (RF11); desfazer devolve exatamente a mesma carta ao monte (RF12). */
public final class DrawAction implements Action {
  @Override public boolean isLegal(Game game) { return !game.stock().isEmpty(); }

  @Override public boolean execute(Game game) {
    game.waste().push(game.stock().pop());
    return true;
  }

  @Override public void undo(Game game) { game.stock().push(game.waste().pop()); }
}
