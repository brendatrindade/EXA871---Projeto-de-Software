package klondike.game;

/** Estratégia do jogo automático: apenas decide, usando consultas somente leitura (RF31, RNF04). */
public interface Strategy {
  Action decide(Game game);
}
