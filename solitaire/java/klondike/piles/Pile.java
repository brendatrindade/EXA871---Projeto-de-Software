package klondike.piles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import klondike.cards.Card;

/**
 * Estrutura comum a monte, descarte, fundações e colunas (RF08). Preserva a ordem base→topo.
 * Consultas nunca alteram o estado (RF09, RF27); os mutadores são usados por ações e pelo jogo.
 */
public class Pile {
  private final List<Card> cards = new ArrayList<>();

  /** Cópia imutável das cartas, da base ao topo. */
  public List<Card> cards() { return List.copyOf(cards); }
  public int size() { return cards.size(); }
  public boolean isEmpty() { return cards.isEmpty(); }

  /** Carta do topo sem removê-la, ou {@code null} se a pilha estiver vazia. */
  public Card top() { return cards.isEmpty() ? null : cards.get(cards.size() - 1); }

  /** Índice da carta (por identidade) ou -1. */
  public int indexOf(Card card) {
    for (int i = 0; i < cards.size(); i++) {
      if (cards.get(i) == card) return i;
    }
    return -1;
  }

  /** Por padrão (monte, descarte) uma pilha não é destino de jogada (RF13). */
  public boolean canTake(List<Card> moving) { return false; }

  public void push(Card card) { cards.add(card); }
  public Card pop() { return cards.remove(cards.size() - 1); }
  public void pushAll(Collection<Card> moved) { cards.addAll(moved); }

  public List<Card> takeFrom(int index) {
    List<Card> tail = cards.subList(index, cards.size());
    List<Card> removed = new ArrayList<>(tail);
    tail.clear();
    return removed;
  }

  public void replace(Collection<Card> newCards) {
    cards.clear();
    cards.addAll(newCards);
  }
}
