package klondike.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import klondike.cards.Card;
import klondike.cards.Suit;
import klondike.game.Game;
import klondike.piles.Location;
import klondike.piles.Pile;
import klondike.piles.TableauColumn;

/** Desenha o tabuleiro e traduz o mouse em jogadas. Não contém regras: consulta {@link Game#canMove}. */
final class BoardPanel extends JPanel {
  private static final Color FELT = new Color(23, 107, 70), SLOT = new Color(183, 215, 198);
  private static final Font CARD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);

  private static final class Drag {
    final List<Card> cards;
    final int offsetX, offsetY;
    int x, y;
    boolean moved;

    Drag(List<Card> cards, int offsetX, int offsetY) {
      this.cards = cards;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
    }
  }

  private final Game game;
  private Drag drag;
  private int pressX = -1, pressY = -1;

  BoardPanel(Game game) {
    this.game = game;
    setPreferredSize(new Dimension(920, 650));
    setBackground(FELT);
    game.subscribe(this::repaint);
    MouseAdapter mouse = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { onPress(e.getX(), e.getY()); }
      @Override public void mouseDragged(MouseEvent e) { onDrag(e.getX(), e.getY()); }
      @Override public void mouseReleased(MouseEvent e) { onRelease(e.getX(), e.getY()); }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  // ---------- entrada ----------
  private Card cardAt(int x, int y) {
    if (Geometry.inWaste(x, y)) return game.waste().top();
    if (y >= Geometry.COLUMN_Y) {
      int index = Geometry.columnAt(x);
      TableauColumn column = game.columns().get(index);
      if (Geometry.withinColumnWidth(x, index)) {
        int position = Geometry.cardIndexAt(y, column.size());
        if (position >= 0 && column.isVisible(position)) return column.cards().get(position);
      }
    }
    return null;
  }

  private int[] originOf(Card card) {
    Location location = game.locate(card).orElseThrow();
    if (location.pile() instanceof TableauColumn column) {
      return new int[] {Geometry.columnX(game.columns().indexOf(column)), Geometry.cardY(location.index())};
    }
    return new int[] {Geometry.WASTE_X, Geometry.TOP_Y};
  }

  private void onPress(int x, int y) {
    pressX = x;
    pressY = y;
    drag = null;
    Card card = cardAt(x, y);
    if (card != null) {
      int[] origin = originOf(card);
      drag = new Drag(game.sequenceFrom(card), x - origin[0], y - origin[1]);
    }
  }

  private void onDrag(int x, int y) {
    if (drag == null) return;
    drag.x = x;
    drag.y = y;
    drag.moved = true;
    repaint();
  }

  private void onRelease(int x, int y) {
    Drag finished = drag;
    boolean startedOnStock = pressX >= 0 && Geometry.inStock(pressX, pressY);
    drag = null;
    pressX = pressY = -1;
    if (startedOnStock && Geometry.inStock(x, y)) {
      game.draw();
    } else if (finished != null && finished.moved) {
      Pile destination = dropTarget(x, y);
      if (destination != null) game.move(finished.cards.get(0), destination);
    }
    repaint();
  }

  private Pile dropTarget(int x, int y) {
    if (Geometry.inFoundationArea(x, y)) return game.foundation(Suit.values()[Geometry.foundationSlotAt(x)]);
    if (Geometry.inTableauArea(y)) return game.columns().get(Geometry.columnAt(x));
    return null;
  }

  // ---------- desenho ----------
  @Override protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Set<Card> dragged = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    if (drag != null && drag.moved) dragged.addAll(drag.cards);

    slot(g, Geometry.STOCK_X, Geometry.TOP_Y, game.stock().isEmpty() ? "↻" : "Monte");
    slot(g, Geometry.WASTE_X, Geometry.TOP_Y, "");
    Card wasteTop = game.waste().top();
    if (wasteTop != null && !dragged.contains(wasteTop)) card(g, Geometry.WASTE_X, Geometry.TOP_Y, wasteTop);
    for (Suit suit : Suit.values()) {
      int x = Geometry.foundationX(suit.ordinal());
      slot(g, x, Geometry.TOP_Y, suit.symbol());
      Card top = game.foundation(suit).top();
      if (top != null) card(g, x, Geometry.TOP_Y, top);
    }
    for (int i = 0; i < game.columns().size(); i++) {
      TableauColumn column = game.columns().get(i);
      int x = Geometry.columnX(i);
      if (column.isEmpty()) slot(g, x, Geometry.COLUMN_Y, "+");
      List<Card> cards = column.cards();
      for (int p = 0; p < cards.size(); p++) {
        if (!dragged.contains(cards.get(p))) card(g, x, Geometry.cardY(p), column.isVisible(p) ? cards.get(p) : null);
      }
    }
    if (drag != null && drag.moved) paintDrag(g);
    g.setColor(Color.WHITE);
    g.setFont(CARD_FONT);
    g.drawString("Jogadas: " + game.historySize() + (game.won() ? "   Vitória!" : ""), 10, 630);
  }

  private void paintDrag(Graphics2D g) {
    Pile destination = dropTarget(drag.x, drag.y);
    if (destination != null) {
      boolean legal = game.canMove(drag.cards.get(0), destination);
      int left, top, height;
      if (destination instanceof TableauColumn column) {
        left = Geometry.columnX(game.columns().indexOf(column));
        top = Geometry.COLUMN_Y;
        height = Geometry.CARD_H + Geometry.FAN * Math.max(0, column.size() - 1);
      } else {
        left = Geometry.foundationX(((klondike.piles.Foundation) destination).suit().ordinal());
        top = Geometry.TOP_Y;
        height = Geometry.CARD_H;
      }
      g.setColor(legal ? new Color(57, 227, 109) : new Color(244, 67, 54));
      g.setStroke(new BasicStroke(3));
      g.drawRect(left, top, Geometry.CARD_W, height);
      g.setStroke(new BasicStroke(1));
    }
    for (int i = 0; i < drag.cards.size(); i++) {
      card(g, drag.x - drag.offsetX, drag.y - drag.offsetY + i * Geometry.FAN, drag.cards.get(i));
    }
  }

  private void slot(Graphics2D g, int x, int y, String label) {
    g.setColor(SLOT);
    g.setStroke(new BasicStroke(2));
    g.drawRect(x, y, Geometry.CARD_W, Geometry.CARD_H);
    g.setStroke(new BasicStroke(1));
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
    g.drawString(label, x + 38 - g.getFontMetrics().stringWidth(label) / 2, y + 58);
  }

  private void card(Graphics2D g, int x, int y, Card card) {
    if (card == null) {
      g.setColor(new Color(36, 90, 66));
      g.fillRect(x, y, Geometry.CARD_W, Geometry.CARD_H);
      g.setColor(SLOT);
      g.drawRect(x, y, Geometry.CARD_W, Geometry.CARD_H);
      return;
    }
    g.setColor(new Color(255, 253, 245));
    g.fillRect(x, y, Geometry.CARD_W, Geometry.CARD_H);
    g.setColor(new Color(51, 51, 51));
    g.drawRect(x, y, Geometry.CARD_W, Geometry.CARD_H);
    g.setColor(card.color() == klondike.cards.Color.RED ? new Color(189, 36, 56) : new Color(23, 37, 29));
    g.setFont(CARD_FONT);
    g.drawString(card.toString(), x + 8, y + 20);
  }
}
