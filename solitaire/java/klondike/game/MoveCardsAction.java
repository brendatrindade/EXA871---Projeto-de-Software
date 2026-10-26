package klondike.game;

import java.util.List;
import klondike.cards.Card;
import klondike.piles.Pile;

/** Move a carta em {@code source[index]} e as que estão sobre ela para {@code destination}. */
public final class MoveCardsAction implements Action {
  private final Pile source;
  private final int index;
  private final Pile destination;
  private final List<Card> cards;

  public MoveCardsAction(Pile source, int index, Pile destination) {
    this.source = source;
    this.index = index;
    this.destination = destination;
    this.cards = List.copyOf(source.cards().subList(index, source.size()));
  }

  @Override public boolean isLegal(Game game) {
    if (cards.isEmpty() || index >= source.size()) return false;
    if (!source.cards().subList(index, source.size()).equals(cards)) return false;
    return game.canMove(cards.get(0), destination);
  }

  @Override public boolean execute(Game game) {
    destination.pushAll(source.takeFrom(index));
    return true;
  }

  @Override public void undo(Game game) {
    source.pushAll(destination.takeFrom(destination.size() - cards.size()));
  }
}
