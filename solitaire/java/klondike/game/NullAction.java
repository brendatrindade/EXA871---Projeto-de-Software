package klondike.game;

/** Ação sem efeito (RF24). */
public final class NullAction implements Action {
  @Override public boolean isLegal(Game game) { return true; }
  @Override public boolean execute(Game game) { return false; }
  @Override public void undo(Game game) { }
}
