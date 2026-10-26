package klondike.strategy;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import klondike.cards.Card;
import klondike.game.Action;
import klondike.game.Game;
import klondike.game.NullAction;
import klondike.game.Strategy;
import klondike.piles.TableauColumn;

/** Regras avaliadas na ordem de prioridade de RF29; a primeira possível vence. Não altera o jogo. */
public final class AutomaticStrategy implements Strategy {
  private static final List<Function<Game, Optional<Action>>> RULES = List.of(
      AutomaticStrategy::drawWhenWasteIsEmpty,   // 1
      AutomaticStrategy::wasteToFoundation,      // 2
      AutomaticStrategy::wasteToTableau,         // 3
      AutomaticStrategy::tableauToFoundation,    // 4
      AutomaticStrategy::tableauMoveWithGain,    // 5
      AutomaticStrategy::drawWhenNothingElse);   // 6

  @Override public Action decide(Game game) {
    for (Function<Game, Optional<Action>> rule : RULES) {
      Optional<Action> action = rule.apply(game);
      if (action.isPresent()) return action.get();
    }
    return new NullAction();                     // 7
  }

  private static Optional<Action> drawWhenWasteIsEmpty(Game game) {
    return game.waste().isEmpty() && !game.stock().isEmpty() ? Optional.of(game.planDraw()) : Optional.empty();
  }

  private static Optional<Action> wasteToFoundation(Game game) {
    Card card = game.waste().top();
    if (card == null) return Optional.empty();
    return planIfLegal(game, card, game.foundation(card.suit()));
  }

  private static Optional<Action> wasteToTableau(Game game) {
    Card card = game.waste().top();
    if (card == null) return Optional.empty();
    for (TableauColumn column : game.columns()) {
      Optional<Action> action = planIfLegal(game, card, column);
      if (action.isPresent()) return action;
    }
    return Optional.empty();
  }

  private static Optional<Action> tableauToFoundation(Game game) {
    for (TableauColumn column : game.columns()) {
      Card card = column.top();
      if (card == null) continue;
      Optional<Action> action = planIfLegal(game, card, game.foundation(card.suit()));
      if (action.isPresent()) return action;
    }
    return Optional.empty();
  }

  /** Só move a primeira carta visível: revela carta oculta ou esvazia a coluna (RF30). */
  private static Optional<Action> tableauMoveWithGain(Game game) {
    for (TableauColumn source : game.columns()) {
      if (source.isEmpty()) continue;
      Card card = source.cards().get(source.hiddenCount());
      if (game.isKingAtBase(card)) continue;
      for (TableauColumn destination : game.columns()) {
        if (destination == source) continue;
        Optional<Action> action = planIfLegal(game, card, destination);
        if (action.isPresent()) return action;
      }
    }
    return Optional.empty();
  }

  private static Optional<Action> drawWhenNothingElse(Game game) {
    return game.stock().isEmpty() ? Optional.empty() : Optional.of(game.planDraw());
  }

  private static Optional<Action> planIfLegal(Game game, Card card, klondike.piles.Pile destination) {
    return game.canMove(card, destination) ? Optional.of(game.planMove(card, destination)) : Optional.empty();
  }
}
