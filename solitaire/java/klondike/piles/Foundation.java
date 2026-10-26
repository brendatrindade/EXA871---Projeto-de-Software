package klondike.piles;

import java.util.List;
import klondike.cards.Card;
import klondike.cards.Rank;
import klondike.cards.Suit;

/** Fundação de um naipe, preenchida do Ás ao Rei, uma carta por vez (RF14, RF15). */
public final class Foundation extends Pile {
  private final Suit suit;

  public Foundation(Suit suit) { this.suit = suit; }

  public Suit suit() { return suit; }

  @Override public boolean canTake(List<Card> moving) {
    if (moving.size() != 1) return false;
    Card card = moving.get(0);
    if (card.suit() != suit) return false;
    Card top = top();
    return top == null ? card.rank() == Rank.ACE : top.rank().isJustBelow(card.rank());
  }
}
