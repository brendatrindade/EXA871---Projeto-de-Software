package klondike.game;

/** Estratégia desativada: o resto do jogo não precisa de tratamento especial (RF28, RNF02). */
public final class NullStrategy implements Strategy {
  @Override public Action decide(Game game) { return new NullAction(); }
}
