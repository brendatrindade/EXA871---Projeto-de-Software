package klondike.cards;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Baralho completo de 52 cartas, sem repetição (RF04, RF07). */
public final class Deck {
  private final List<Card> cards = new ArrayList<>();

  public Deck() {
    for (Suit suit : Suit.values()) {
      for (Rank rank : Rank.values()) cards.add(new Card(suit, rank));
    }
  }

  public void shuffle(Random random) { java.util.Collections.shuffle(cards, random); }
  public Card draw() { return cards.remove(cards.size() - 1); }
  public int size() { return cards.size(); }
}
