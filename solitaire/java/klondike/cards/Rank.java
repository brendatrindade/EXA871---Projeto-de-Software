package klondike.cards;

/** Ordem total dos ranks: Ás &lt; 2 &lt; ... &lt; Rei (RF05). */
public enum Rank {
  ACE(1, "A"), TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"), SEVEN(7, "7"),
  EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K");

  private final int value;
  private final String label;

  Rank(int value, String label) {
    this.value = value;
    this.label = label;
  }

  public int value() { return value; }
  public String label() { return label; }

  /** True se este rank é imediatamente inferior a {@code other}. */
  public boolean isJustBelow(Rank other) { return other.value - value == 1; }

  public static Rank fromValue(int value) {
    for (Rank rank : values()) {
      if (rank.value == value) return rank;
    }
    throw new IllegalArgumentException("Rank inválido: " + value);
  }
}
