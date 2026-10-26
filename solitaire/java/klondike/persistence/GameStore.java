package klondike.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import klondike.cards.Card;
import klondike.cards.Rank;
import klondike.cards.Suit;
import klondike.game.Game;
import klondike.game.Layout;

/**
 * Salvar/abrir partidas em JSON, formato {@code solitaire-klondike-v1} (RNF05), o mesmo da versão Python.
 *
 * <pre>
 * {"schema", "cards":[{"id","suit","rank"}], "columns":[{"cards":[id],"hidden_count":n}] x7,
 *  "stock":[id], "waste":[id], "foundations":{NAIPE:[id]}}
 * </pre>
 * Cada carta aparece uma vez; ao carregar, cada id vira exatamente um objeto {@link Card}.
 * Arquivos com "visible_from" (mesmo significado de "hidden_count") também são aceitos.
 */
public final class GameStore {
  public static final String SCHEMA = "solitaire-klondike-v1";

  private GameStore() { }

  private static String id(Card card) { return card.suit().name() + ":" + card.rank().value(); }

  private static String ids(List<Card> cards) {
    List<String> quoted = new ArrayList<>();
    for (Card card : cards) quoted.add(Json.quote(id(card)));
    return "[" + String.join(", ", quoted) + "]";
  }

  public static String dumps(Game game) {
    Layout layout = game.capture();
    List<Card> all = new ArrayList<>(game.allCards());
    all.sort(Comparator.comparing((Card c) -> c.suit().name()).thenComparing(c -> c.rank().value()));
    StringBuilder out = new StringBuilder("{\n  \"schema\": " + Json.quote(SCHEMA) + ",\n  \"cards\": [\n");
    for (int i = 0; i < all.size(); i++) {
      Card c = all.get(i);
      out.append("    {\"id\": ").append(Json.quote(id(c))).append(", \"suit\": ").append(Json.quote(c.suit().name()))
          .append(", \"rank\": ").append(c.rank().value()).append('}').append(i < all.size() - 1 ? ",\n" : "\n");
    }
    out.append("  ],\n  \"columns\": [\n");
    for (int i = 0; i < layout.columns().size(); i++) {
      Layout.ColumnLayout column = layout.columns().get(i);
      out.append("    {\"cards\": ").append(ids(column.cards())).append(", \"hidden_count\": ")
          .append(column.hiddenCount()).append('}').append(i < layout.columns().size() - 1 ? ",\n" : "\n");
    }
    out.append("  ],\n  \"stock\": ").append(ids(layout.stock())).append(",\n  \"waste\": ")
        .append(ids(layout.waste())).append(",\n  \"foundations\": {\n");
    Suit[] suits = Suit.values();
    for (int i = 0; i < suits.length; i++) {
      out.append("    ").append(Json.quote(suits[i].name())).append(": ")
          .append(ids(layout.foundations().get(suits[i]))).append(i < suits.length - 1 ? ",\n" : "\n");
    }
    return out.append("  }\n}\n").toString();
  }

  public static Layout loads(String text) {
    Map<String, Object> payload = map(Json.parse(text), "documento");
    if (!SCHEMA.equals(payload.get("schema"))) throw new IllegalArgumentException("Formato de partida desconhecido");

    Set<String> expected = new HashSet<>();
    for (Suit suit : Suit.values()) for (Rank rank : Rank.values()) expected.add(suit.name() + ":" + rank.value());

    Map<String, Card> cards = new LinkedHashMap<>();
    for (Object entry : list(payload.get("cards"), "cards")) {
      Map<String, Object> m = map(entry, "carta");
      Suit suit = Suit.valueOf(string(m.get("suit"), "suit"));
      Rank rank = Rank.fromValue(integer(m.get("rank"), "rank"));
      String identifier = string(m.get("id"), "id");
      if (!identifier.equals(suit.name() + ":" + rank.value()) || cards.containsKey(identifier)) {
        throw new IllegalArgumentException("Identidade de carta inválida ou repetida");
      }
      cards.put(identifier, new Card(suit, rank));
    }
    if (!cards.keySet().equals(expected)) {
      throw new IllegalArgumentException("O baralho deve conter as 52 cartas padrão, sem repetição");
    }

    List<Object> columns = list(payload.get("columns"), "columns");
    Map<String, Object> foundations = map(payload.get("foundations"), "foundations");
    Set<String> suitNames = new HashSet<>();
    for (Suit suit : Suit.values()) suitNames.add(suit.name());
    if (columns.size() != Game.COLUMN_COUNT || !foundations.keySet().equals(suitNames)) {
      throw new IllegalArgumentException("Estrutura da partida inválida");
    }

    List<String> refs = new ArrayList<>();
    List<Layout.ColumnLayout> layoutColumns = new ArrayList<>();
    for (Object value : columns) {
      Map<String, Object> column = map(value, "coluna");
      List<Card> columnCards = resolve(list(column.get("cards"), "cards da coluna"), cards, refs);
      Object hiddenValue = column.containsKey("hidden_count") ? column.get("hidden_count") : column.get("visible_from");
      int hidden = integer(hiddenValue, "hidden_count");
      if (hidden < 0 || hidden > columnCards.size()) throw new IllegalArgumentException("Visibilidade de coluna inválida");
      layoutColumns.add(new Layout.ColumnLayout(columnCards, hidden));
    }
    List<Card> stock = resolve(list(payload.get("stock"), "stock"), cards, refs);
    List<Card> waste = resolve(list(payload.get("waste"), "waste"), cards, refs);
    Map<Suit, List<Card>> layoutFoundations = new EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) {
      List<Card> pile = resolve(list(foundations.get(suit.name()), "fundação"), cards, refs);
      for (int k = 0; k < pile.size(); k++) {
        if (pile.get(k).suit() != suit || pile.get(k).rank().value() != k + 1) {
          throw new IllegalArgumentException("Fundação fora da ordem Ás→Rei do naipe");
        }
      }
      layoutFoundations.put(suit, pile);
    }
    if (refs.size() != 52 || !new HashSet<>(refs).equals(expected)) {
      throw new IllegalArgumentException("Cada carta deve aparecer uma única vez nas pilhas");
    }
    return new Layout(layoutColumns, stock, waste, layoutFoundations);
  }

  public static void save(Game game, Path file) throws IOException {
    Files.writeString(file, dumps(game));
  }

  public static void load(Game game, Path file) throws IOException {
    game.loadLayout(loads(Files.readString(file)));
  }

  private static List<Card> resolve(List<Object> references, Map<String, Card> cards, List<String> seen) {
    List<Card> resolved = new ArrayList<>();
    for (Object reference : references) {
      String identifier = string(reference, "referência de carta");
      Card card = cards.get(identifier);
      if (card == null) throw new IllegalArgumentException("Carta desconhecida: " + identifier);
      seen.add(identifier);
      resolved.add(card);
    }
    return resolved;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value, String what) {
    if (value instanceof Map<?, ?>) return (Map<String, Object>) value;
    throw new IllegalArgumentException("Estrutura malformada: " + what);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value, String what) {
    if (value instanceof List<?>) return (List<Object>) value;
    throw new IllegalArgumentException("Estrutura malformada: " + what);
  }

  private static String string(Object value, String what) {
    if (value instanceof String s) return s;
    throw new IllegalArgumentException("Estrutura malformada: " + what);
  }

  private static int integer(Object value, String what) {
    if (value instanceof Integer i) return i;
    throw new IllegalArgumentException("Estrutura malformada: " + what);
  }
}
