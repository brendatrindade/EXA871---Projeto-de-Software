package klondike.cards;

/** Naipes: Copas e Ouros são vermelhos; Paus e Espadas são pretos (RF06). */
public enum Suit {
  HEARTS("♥", Color.RED), DIAMONDS("♦", Color.RED), CLUBS("♣", Color.BLACK), SPADES("♠", Color.BLACK);

  private final String symbol;
  private final Color color;

  Suit(String symbol, Color color) {
    this.symbol = symbol;
    this.color = color;
  }

  public String symbol() { return symbol; }
  public Color color() { return color; }
  public boolean sameColor(Suit other) { return color == other.color; }
}
