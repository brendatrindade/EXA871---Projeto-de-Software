package klondike.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import klondike.cards.Card;
import klondike.cards.Deck;
import klondike.cards.Rank;
import klondike.cards.Suit;
import klondike.piles.Foundation;
import klondike.piles.Location;
import klondike.piles.Pile;
import klondike.piles.TableauColumn;

/**
 * Mecanismo central: regras, histórico, notificação e execução de jogadas.
 * As consultas nunca alteram o estado (RF27); interface e estratégia só consultam as regras
 * aqui definidas (RF21, RNF03, RNF04).
 */
public final class Game {
  public static final int COLUMN_COUNT = 7;

  private final Random random;
  private final Pile stock = new Pile();
  private final Pile waste = new Pile();
  private final List<TableauColumn> columns = new ArrayList<>();
  private final Map<Suit, Foundation> foundations = new EnumMap<>(Suit.class);
  private final Deque<Action> history = new ArrayDeque<>();
  private final List<Runnable> listeners = new ArrayList<>();
  private Strategy strategy;

  public Game(Random random, Strategy strategy) {
    this.random = random;
    this.strategy = strategy;
    for (int i = 0; i < COLUMN_COUNT; i++) columns.add(new TableauColumn());
    for (Suit suit : Suit.values()) foundations.put(suit, new Foundation(suit));
    shuffleAndDeal();
  }

  // ---------- notificação (RF26) ----------
  public void subscribe(Runnable listener) { listeners.add(listener); }

  private void notifyListeners() {
    for (Runnable listener : List.copyOf(listeners)) listener.run();
  }

  // ---------- partida (RF01–RF03) ----------
  public void shuffleAndDeal() {
    Deck deck = new Deck();
    deck.shuffle(random);
    for (int i = 0; i < COLUMN_COUNT; i++) {
      List<Card> dealt = new ArrayList<>();
      for (int j = 0; j <= i; j++) dealt.add(deck.draw());
      columns.get(i).replace(dealt, i);
    }
    List<Card> rest = new ArrayList<>();
    while (deck.size() > 0) rest.add(deck.draw());
    stock.replace(rest);
    waste.replace(List.of());
    for (Foundation foundation : foundations.values()) foundation.replace(List.of());
  }

  public int score() { return foundations.values().stream().mapToInt(Pile::size).sum(); }
  public boolean won() { return score() == 52; }

  // ---------- acesso às pilhas ----------
  public Pile stock() { return stock; }
  public Pile waste() { return waste; }
  public List<TableauColumn> columns() { return Collections.unmodifiableList(columns); }
  public Foundation foundation(Suit suit) { return foundations.get(suit); }

  public List<Pile> piles() {
    List<Pile> all = new ArrayList<>(List.of(stock, waste));
    all.addAll(columns);
    all.addAll(foundations.values());
    return all;
  }

  public List<Card> allCards() {
    List<Card> all = new ArrayList<>();
    for (Pile pile : piles()) all.addAll(pile.cards());
    return all;
  }

  // ---------- consultas somente leitura (RF09, RF10, RF20, RF27) ----------
  /** Local de uma carta acessível: topo do descarte ou carta visível do tableau. */
  public Optional<Location> locate(Card card) {
    if (waste.top() == card) return Optional.of(new Location(waste, waste.size() - 1));
    for (TableauColumn column : columns) {
      int index = column.indexOf(card);
      if (index >= 0 && column.isVisible(index)) return Optional.of(new Location(column, index));
    }
    return Optional.empty();
  }

  /** A carta e as que estão sobre ela (as que seriam movidas junto). */
  public List<Card> sequenceFrom(Card card) {
    return locate(card).map(l -> l.pile().cards().subList(l.index(), l.pile().size())).orElse(List.of());
  }

  public boolean isVisible(Card card) { return locate(card).isPresent(); }

  public boolean isFirstVisible(Card card) {
    return locate(card)
        .filter(l -> l.pile() instanceof TableauColumn column && l.index() == column.hiddenCount())
        .isPresent();
  }

  public boolean isKingAtBase(Card card) {
    return card.rank() == Rank.KING
        && locate(card).filter(l -> l.pile() instanceof TableauColumn && l.index() == 0).isPresent();
  }

  // ---------- legalidade (RF21) ----------
  public boolean canMove(Card card, Pile destination) {
    Optional<Location> location = locate(card);
    if (location.isEmpty() || destination == location.get().pile()) return false;
    Location source = location.get();
    return destination.canTake(source.pile().cards().subList(source.index(), source.pile().size()));
  }

  // ---------- construção de ações (não alteram o jogo) ----------
  public Action planDraw() { return stock.isEmpty() ? new NewGameAction() : new DrawAction(); }

  public Action planMove(Card card, Pile destination) {
    Optional<Location> location = locate(card);
    if (location.isEmpty()) return new NullAction();
    Pile source = location.get().pile();
    int index = location.get().index();
    Action move = new MoveCardsAction(source, index, destination);
    if (source instanceof TableauColumn column && column.mustRevealAfterRemoving(index)) {
      return new CompositeAction(List.of(move, new RevealAction(column)));
    }
    return move;
  }

  // ---------- execução e histórico (RF25) ----------
  /** Verifica a legalidade como em qualquer jogada, executa, registra e notifica. */
  public boolean perform(Action action) {
    if (!action.isLegal(this) || !action.execute(this)) return false;
    history.push(action);
    notifyListeners();
    return true;
  }

  public boolean draw() { return perform(planDraw()); }
  public boolean move(Card card, Pile destination) { return perform(planMove(card, destination)); }
  public boolean automaticMove() { return perform(strategy.decide(this)); }

  public boolean undo() {
    if (history.isEmpty()) return false;
    history.pop().undo(this);
    notifyListeners();
    return true;
  }

  public int historySize() { return history.size(); }
  public void setStrategy(Strategy strategy) { this.strategy = strategy; }
  public Strategy strategy() { return strategy; }

  // ---------- fotografia de posição ----------
  public Layout capture() {
    List<Layout.ColumnLayout> cols = new ArrayList<>();
    for (TableauColumn column : columns) cols.add(new Layout.ColumnLayout(column.cards(), column.hiddenCount()));
    Map<Suit, List<Card>> found = new EnumMap<>(Suit.class);
    foundations.forEach((suit, foundation) -> found.put(suit, foundation.cards()));
    return new Layout(cols, stock.cards(), waste.cards(), found);
  }

  public void apply(Layout layout) {
    for (int i = 0; i < COLUMN_COUNT; i++) {
      columns.get(i).replace(layout.columns().get(i).cards(), layout.columns().get(i).hiddenCount());
    }
    stock.replace(layout.stock());
    waste.replace(layout.waste());
    foundations.forEach((suit, foundation) -> foundation.replace(layout.foundations().get(suit)));
  }

  /** Substitui a partida (ex.: Abrir); a posição carregada passa a ser a inicial. */
  public void loadLayout(Layout layout) {
    apply(layout);
    history.clear();
    notifyListeners();
  }
}
