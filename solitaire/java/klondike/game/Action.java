package klondike.game;

/** Ação reversível. Simples, compostas e nula compartilham esta interface (RF22, RNF02). */
public interface Action {
  boolean isLegal(Game game);

  /** Executa; retorna true se houve mudança efetiva de estado. */
  boolean execute(Game game);

  void undo(Game game);
}
