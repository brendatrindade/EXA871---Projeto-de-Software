package klondike.game;

import java.util.List;
import java.util.Map;
import klondike.cards.Card;
import klondike.cards.Suit;

/** Fotografia completa de uma posição; referencia as mesmas cartas (identidade preservada). */
public record Layout(List<ColumnLayout> columns, List<Card> stock, List<Card> waste,
                     Map<Suit, List<Card>> foundations) {
  public record ColumnLayout(List<Card> cards, int hiddenCount) { }
}
