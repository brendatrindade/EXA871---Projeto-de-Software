package klondike.cards;

/**
 * Carta única (RF04). Não sobrescreve equals/hashCode de propósito: a igualdade é por
 * identidade, de modo que qualquer referência à carta se comporta como a mesma carta.
 */
public final class Card {
  private final Suit suit;
  private final Rank rank;

  public Card(Suit suit, Rank rank) {
    this.suit = suit;
    this.rank = rank;
  }

  public Suit suit() { return suit; }
  public Rank rank() { return rank; }
  public Color color() { return suit.color(); }

  @Override public String toString() { return rank.label() + suit.symbol(); }
}
