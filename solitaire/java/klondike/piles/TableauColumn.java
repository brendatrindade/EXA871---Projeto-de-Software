package klondike.piles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import klondike.cards.Card;
import klondike.cards.Rank;

/** Coluna do tableau: as {@code hiddenCount} primeiras cartas estão viradas (RF16, RF17). */
public final class TableauColumn extends Pile {
  private int hiddenCount;

  public int hiddenCount() { return hiddenCount; }
  public boolean isVisible(int index) { return index >= hiddenCount; }

  @Override public void replace(Collection<Card> newCards) { replace(newCards, 0); }

  public void replace(Collection<Card> newCards, int hidden) {
    if (hidden < 0 || hidden > newCards.size()) {
      throw new IllegalArgumentException("Quantidade de cartas ocultas inválida");
    }
    super.replace(new ArrayList<>(newCards));
    hiddenCount = hidden;
  }

  /** Remover a primeira carta visível (índice &gt; 0) expõe uma carta oculta (RF19). */
  public boolean mustRevealAfterRemoving(int index) { return index > 0 && index == hiddenCount; }

  public boolean revealNext() {
    if (hiddenCount > 0 && hiddenCount == size()) {
      hiddenCount--;
      return true;
    }
    return false;
  }

  public void concealLastRevealed() { hiddenCount++; }

  @Override public boolean canTake(List<Card> moving) {
    if (moving.isEmpty()) return false;
    Card first = moving.get(0);
    Card top = top();
    if (top == null) return first.rank() == Rank.KING;
    return first.rank().isJustBelow(top.rank()) && first.color() != top.color();
  }
}
