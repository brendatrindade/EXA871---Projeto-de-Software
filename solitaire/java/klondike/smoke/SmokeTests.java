package klondike.smoke;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import klondike.cards.Card;
import klondike.cards.Rank;
import klondike.cards.Suit;
import klondike.game.Action;
import klondike.game.DrawAction;
import klondike.game.Game;
import klondike.game.Layout;
import klondike.game.Layout.ColumnLayout;
import klondike.game.NewGameAction;
import klondike.game.NullAction;
import klondike.game.NullStrategy;
import klondike.persistence.GameStore;
import klondike.piles.Foundation;
import klondike.piles.TableauColumn;
import klondike.strategy.AutomaticStrategy;

/** Verificações do modelo sem janela e sem dependências externas (executadas por {@code java Main --smoke}). */
public final class SmokeTests {
  private int run;
  private int failed;

  public static int run() {
    SmokeTests tests = new SmokeTests();
    tests.all();
    if (tests.failed == 0) {
      System.out.println("Solitaire Java OK: " + tests.run + " testes do modelo, estratégia, histórico e persistência.");
      return 0;
    }
    System.out.println(tests.failed + " de " + tests.run + " testes falharam.");
    return 1;
  }

  private void all() {
    test("distribuição inicial", this::initialDeal);
    test("ranks e cores", this::ranksAndColors);
    test("embaralhamento varia", this::shuffleVaries);
    test("regras da fundação", this::foundationRules);
    test("regras da coluna e descarte", this::columnRules);
    test("coluna → fundação com revelação", this::columnToFoundation);
    test("sequência move junta", this::sequenceMoves);
    test("cartas ocultas não se movem", this::hiddenNotMovable);
    test("consultas não alteram estado", this::queriesReadOnly);
    test("rei na base", this::kingAtBase);
    test("comprar e desfazer", this::drawAndUndo);
    test("monte vazio inicia nova partida", this::newGameOnEmptyStock);
    test("ações nula e ilegal", this::nullAndIllegal);
    test("desfazer tudo após jogo longo", this::undoAll);
    test("decidir é somente leitura", this::decideReadOnly);
    test("prioridade descarte → fundação", this::priorityWasteFoundation);
    test("prioridade coluna → fundação", this::priorityColumnFoundation);
    test("rei na base não é movido", this::kingNotMoved);
    test("estratégia desativada", this::disabledStrategy);
    test("JSON ida e volta", this::roundTrip);
    test("JSON legado visible_from", this::legacyFormat);
    test("JSON inválido rejeitado", this::invalidFiles);
  }

  // ---------- infraestrutura ----------
  private void test(String name, Runnable body) {
    run++;
    try {
      body.run();
    } catch (Throwable t) {
      failed++;
      System.out.println("FALHA " + name + ": " + t);
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static Game game(long seed) { return new Game(new Random(seed), new AutomaticStrategy()); }
  private static Card card(Suit suit, int rank) { return new Card(suit, Rank.fromValue(rank)); }
  private static ColumnLayout col(int hidden, Card... cards) { return new ColumnLayout(List.of(cards), hidden); }

  private static Layout layout(List<ColumnLayout> columns, List<Card> stock, List<Card> waste) {
    List<ColumnLayout> padded = new ArrayList<>(columns);
    while (padded.size() < Game.COLUMN_COUNT) padded.add(col(0));
    Map<Suit, List<Card>> found = new EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) found.put(suit, List.of());
    return new Layout(padded, stock, waste, found);
  }

  private static Layout layout(List<ColumnLayout> columns) { return layout(columns, List.of(), List.of()); }

  // ---------- testes ----------
  private void initialDeal() {
    Game g = game(1);
    for (int i = 0; i < 7; i++) {
      check(g.columns().get(i).size() == i + 1, "tamanho da coluna " + i);
      check(g.columns().get(i).hiddenCount() == i, "ocultas da coluna " + i);
    }
    check(g.stock().size() == 24 && g.waste().isEmpty() && g.score() == 0, "monte/descarte/pontuação");
    List<Card> cards = g.allCards();
    Set<Card> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    identities.addAll(cards);
    check(cards.size() == 52 && identities.size() == 52, "52 cartas únicas");
    check(cards.stream().map(Card::toString).distinct().count() == 52, "52 valores distintos");
  }

  private void ranksAndColors() {
    check(Rank.SIX.isJustBelow(Rank.SEVEN) && !Rank.SEVEN.isJustBelow(Rank.SIX), "ordem dos ranks");
    check(Suit.HEARTS.sameColor(Suit.DIAMONDS) && !Suit.HEARTS.sameColor(Suit.SPADES), "cores");
  }

  private void shuffleVaries() {
    check(!game(1).stock().cards().toString().equals(game(2).stock().cards().toString()), "sementes iguais");
  }

  private void foundationRules() {
    Game g = game(1);
    Card ace = card(Suit.HEARTS, 1), two = card(Suit.HEARTS, 2), king = card(Suit.HEARTS, 13);
    Foundation hearts = g.foundation(Suit.HEARTS);
    check(hearts.canTake(List.of(ace)), "Ás em fundação vazia");
    check(!hearts.canTake(List.of(two)), "2 em fundação vazia");
    check(!hearts.canTake(List.of(ace, two)), "duas cartas de uma vez");
    check(!g.foundation(Suit.CLUBS).canTake(List.of(ace)), "naipe errado");
    hearts.push(ace);
    check(hearts.canTake(List.of(two)) && !hearts.canTake(List.of(king)), "sucessor");
  }

  private void columnRules() {
    Game g = game(1);
    Card seven = card(Suit.SPADES, 7), six = card(Suit.HEARTS, 6);
    g.loadLayout(layout(List.of(col(0, seven), col(0)), List.of(), List.of(six)));
    check(g.canMove(six, g.columns().get(0)), "6 vermelho sobre 7 preto");
    check(!g.canMove(six, g.columns().get(1)), "coluna vazia só aceita Rei");
    check(!g.canMove(six, g.waste()) && !g.canMove(six, g.stock()), "descarte/monte não são destinos");
  }

  private void columnToFoundation() {
    Game g = game(1);
    Card ace = card(Suit.HEARTS, 1), hidden = card(Suit.SPADES, 5);
    g.loadLayout(layout(List.of(col(1, hidden, ace))));
    Foundation hearts = g.foundation(Suit.HEARTS);
    check(g.canMove(ace, hearts) && g.move(ace, hearts), "Ás da coluna para a fundação");
    check(g.score() == 1 && g.columns().get(0).isVisible(0), "revelação automática");
    g.undo();
    check(g.columns().get(0).hiddenCount() == 1 && g.columns().get(0).top() == ace, "desfazer oculta de novo");
  }

  private void sequenceMoves() {
    Game g = game(1);
    Card h8 = card(Suit.HEARTS, 8), s7 = card(Suit.SPADES, 7), h6 = card(Suit.HEARTS, 6), s9 = card(Suit.SPADES, 9);
    g.loadLayout(layout(List.of(col(1, card(Suit.CLUBS, 3), h8, s7, h6), col(0, s9))));
    check(g.sequenceFrom(h8).equals(List.of(h8, s7, h6)), "sequência a partir do 8");
    check(!g.canMove(h8, g.foundation(Suit.HEARTS)), "sequência não vai à fundação");
    check(g.move(h8, g.columns().get(1)), "mover sequência");
    check(g.columns().get(1).cards().equals(List.of(s9, h8, s7, h6)), "ordem preservada");
    check(g.columns().get(0).hiddenCount() == 0, "carta revelada");
  }

  private void hiddenNotMovable() {
    Game g = game(1);
    Card hidden = card(Suit.HEARTS, 1);
    g.loadLayout(layout(List.of(col(1, hidden, card(Suit.SPADES, 5)))));
    check(g.locate(hidden).isEmpty() && !g.move(hidden, g.foundation(Suit.HEARTS)), "carta oculta");
  }

  private void queriesReadOnly() {
    Game g = game(3);
    Layout before = g.capture();
    for (Card c : g.allCards()) {
      g.locate(c); g.sequenceFrom(c); g.isVisible(c); g.isFirstVisible(c); g.isKingAtBase(c);
      g.canMove(c, g.columns().get(0));
    }
    check(before.equals(g.capture()), "estado alterado por consulta");
  }

  private void kingAtBase() {
    Game g = game(1);
    Card kingH = card(Suit.HEARTS, 13), kingS = card(Suit.SPADES, 13);
    g.loadLayout(layout(List.of(col(0, kingH), col(1, card(Suit.CLUBS, 2), kingS))));
    check(g.isKingAtBase(kingH) && !g.isKingAtBase(kingS), "rei na base");
  }

  private void drawAndUndo() {
    Game g = game(1);
    Card top = g.stock().top();
    int[] notifications = {0};
    g.subscribe(() -> notifications[0]++);
    check(g.draw() && g.waste().top() == top, "compra");
    g.undo();
    check(g.stock().top() == top && g.waste().isEmpty() && notifications[0] == 2, "desfazer devolve a mesma carta");
  }

  private void newGameOnEmptyStock() {
    Game g = game(1);
    g.loadLayout(layout(List.of(col(0, card(Suit.SPADES, 5))), List.of(), List.of(card(Suit.HEARTS, 2))));
    Layout before = g.capture();
    check(g.planDraw() instanceof NewGameAction, "plano é nova partida");
    check(g.draw() && g.stock().size() == 24 && g.waste().isEmpty(), "nova partida");
    g.undo();
    check(before.equals(g.capture()), "desfazer volta à partida anterior");
  }

  private void nullAndIllegal() {
    Game g = game(1);
    Layout before = g.capture();
    check(!g.perform(new NullAction()) && !g.perform(new NewGameAction()), "ação sem efeito/ilegal");
    check(g.historySize() == 0 && before.equals(g.capture()), "nada muda");
  }

  private void undoAll() {
    for (int seed = 0; seed < 40; seed++) {
      Game g = game(seed);
      Layout initial = g.capture();
      int steps = 0;
      while (g.automaticMove()) check(++steps < 2000, "jogo automático em ciclo (seed " + seed + ")");
      Set<Card> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
      identities.addAll(g.allCards());
      check(identities.size() == 52, "52 cartas após jogar");
      while (g.undo()) { }
      check(initial.equals(g.capture()), "estado inicial restaurado (seed " + seed + ")");
      check(g.automaticMove(), "continua jogável");
    }
  }

  private void decideReadOnly() {
    Game g = game(7);
    Layout before = g.capture();
    Action action = g.strategy().decide(g);
    check(action instanceof DrawAction && before.equals(g.capture()), "primeira decisão é comprar sem alterar");
  }

  private void priorityWasteFoundation() {
    Game g = game(1);
    Card ace = card(Suit.HEARTS, 1);
    g.loadLayout(layout(List.of(col(0, card(Suit.SPADES, 2))), List.of(card(Suit.CLUBS, 9)), List.of(ace)));
    check(g.automaticMove() && g.foundation(Suit.HEARTS).top() == ace, "descarte → fundação");
  }

  private void priorityColumnFoundation() {
    Game g = game(1);
    Card ace = card(Suit.HEARTS, 1);
    g.loadLayout(layout(List.of(col(0, ace)), List.of(card(Suit.CLUBS, 9)), List.of(card(Suit.CLUBS, 4))));
    check(g.automaticMove() && g.foundation(Suit.HEARTS).top() == ace, "coluna → fundação");
  }

  private void kingNotMoved() {
    Game g = game(1);
    g.loadLayout(layout(List.of(col(0, card(Suit.SPADES, 13)))));
    check(!g.automaticMove() && g.historySize() == 0, "rei na base fica parado");
  }

  private void disabledStrategy() {
    Game g = game(1);
    g.setStrategy(new NullStrategy());
    check(!g.automaticMove() && g.historySize() == 0, "estratégia nula");
  }

  private void roundTrip() {
    Game g = game(3);
    g.draw();
    String text = GameStore.dumps(g);
    Game other = game(9);
    other.loadLayout(GameStore.loads(text));
    check(GameStore.dumps(other).equals(text), "JSON idêntico");
    Set<Card> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    identities.addAll(other.allCards());
    check(identities.size() == 52 && other.historySize() == 0, "52 objetos distintos, histórico vazio");
    check(g.waste().cards().toString().equals(other.waste().cards().toString()), "descarte preservado");
  }

  private void legacyFormat() {
    String legacy = GameStore.dumps(game(2)).replace("hidden_count", "visible_from");
    check(GameStore.loads(legacy).columns().get(3).hiddenCount() == 3, "visible_from aceito");
  }

  private void invalidFiles() {
    String valid = GameStore.dumps(game(2));
    String[] bad = {
        valid.replace("solitaire-klondike-v1", "outro"),
        valid.replaceFirst("\"stock\": \\[\"[A-Z]+:[0-9]+\", ", "\"stock\": ["),
        valid.replaceFirst("\"hidden_count\": 3", "\"hidden_count\": 9"),
        "{}", "[]", "{not json"};
    for (String text : bad) {
      boolean rejected = false;
      try {
        GameStore.loads(text);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      check(rejected, "arquivo inválido aceito: " + text.substring(0, Math.min(30, text.length())));
    }
  }
}
