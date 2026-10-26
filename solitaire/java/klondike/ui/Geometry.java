package klondike.ui;

/** Geometria do tabuleiro em um só lugar: desenho, clique e arraste usam os mesmos números. */
final class Geometry {
  static final int CARD_W = 76, CARD_H = 104, TOP_Y = 35;
  static final int STOCK_X = 10, WASTE_X = 105;
  static final int FOUNDATION_X = 500, FOUNDATION_SPACING = 95;
  static final int COLUMN_X = 10, COLUMN_SPACING = 125, COLUMN_Y = 170, FAN = 25, COLUMN_COUNT = 7;

  private Geometry() { }

  static int columnX(int index) { return COLUMN_X + index * COLUMN_SPACING; }
  static int foundationX(int slot) { return FOUNDATION_X + slot * FOUNDATION_SPACING; }
  static int cardY(int position) { return COLUMN_Y + position * FAN; }

  static int columnAt(int x) {
    return Math.max(0, Math.min(COLUMN_COUNT - 1, Math.floorDiv(x - COLUMN_X + 5, COLUMN_SPACING)));
  }

  static int foundationSlotAt(int x) {
    return Math.max(0, Math.min(3, Math.floorDiv(x - FOUNDATION_X, FOUNDATION_SPACING)));
  }

  static boolean withinColumnWidth(int x, int index) {
    return x >= columnX(index) && x <= columnX(index) + CARD_W;
  }

  static boolean inStock(int x, int y) { return inCard(x, y, STOCK_X); }
  static boolean inWaste(int x, int y) { return inCard(x, y, WASTE_X); }
  static boolean inFoundationArea(int x, int y) { return y < COLUMN_Y - 25 && x >= FOUNDATION_X; }
  static boolean inTableauArea(int y) { return y >= COLUMN_Y - 25; }

  private static boolean inCard(int x, int y, int left) {
    return x >= left && x <= left + CARD_W && y >= TOP_Y && y <= TOP_Y + CARD_H;
  }

  /** Índice da carta (de cima para baixo) sob y numa coluna com {@code count} cartas, ou -1. */
  static int cardIndexAt(int y, int count) {
    for (int index = count - 1; index >= 0; index--) {
      int height = index == count - 1 ? CARD_H : FAN;
      if (y >= cardY(index) && y < cardY(index) + height) return index;
    }
    return -1;
  }
}
