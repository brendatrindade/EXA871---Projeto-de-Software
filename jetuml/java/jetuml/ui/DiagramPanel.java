package jetuml.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import jetuml.app.Settings;
import jetuml.catalog.Catalog;
import jetuml.catalog.Tool;
import jetuml.editor.Editor;
import jetuml.editor.Mods;
import jetuml.render.DiagramPainter;
import jetuml.render.Graphics2DRenderer;
import jetuml.render.Palette;

/** Uma aba: desenha o diagrama e traduz mouse/teclado em eventos do {@link Editor} (RF09, RF32). */
public final class DiagramPanel extends JPanel {
  private static final long serialVersionUID = 1L;
  /** Ações da janela acionadas pelo painel (ferramentas por atalho ou menu de contexto). */
  public interface ToolChooser {
    void chooseTool(String name);
    void chooseShortcut(Editor editor, String key);
  }

  private final transient Editor editor;
  private final transient Settings settings;
  private final transient ToolChooser chooser;

  public DiagramPanel(Editor editor, Settings settings, ToolChooser chooser) {
    this.editor = editor;
    this.settings = settings;
    this.chooser = chooser;
    setFocusable(true);
    MouseAdapter mouse = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (e.isPopupTrigger()) { popup(e); return; }
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        if (e.getClickCount() == 2) editor.doubleClick(modelX(e), modelY(e));
        else editor.press(modelX(e), modelY(e), mods(e));
      }

      @Override public void mouseDragged(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) editor.drag(modelX(e), modelY(e), mods(e));
      }

      @Override public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) popup(e);
        else if (SwingUtilities.isLeftMouseButton(e)) editor.release(modelX(e), modelY(e), mods(e));
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addKeyListener(new KeyAdapter() {
      @Override public void keyTyped(KeyEvent e) {
        char ch = e.getKeyChar();
        if (!e.isControlDown() && Character.isLetterOrDigit(ch)) chooser.chooseShortcut(editor, String.valueOf(ch));
      }
    });
    editor.listeners.add(this::refresh);
    refresh();
  }

  private double modelX(MouseEvent e) { return e.getX() / editor.zoomFactor(); }
  private double modelY(MouseEvent e) { return e.getY() / editor.zoomFactor(); }
  private static Mods mods(MouseEvent e) { return new Mods(e.isControlDown(), e.isShiftDown()); }

  private void popup(MouseEvent e) {
    JPopupMenu menu = new JPopupMenu();
    for (Tool tool : Catalog.toolsFor(editor.diagram.kind)) {
      JMenuItem item = new JMenuItem(tool.name());
      item.addActionListener(a -> chooser.chooseTool(tool.name()));
      menu.add(item);
    }
    menu.show(this, e.getX(), e.getY());
  }

  /** Tamanho fixo do diagrama vezes o zoom (RF37, RF38). */
  private void refresh() {
    double z = editor.zoomFactor();
    setPreferredSize(new Dimension((int) (editor.diagram.width * z), (int) (editor.diagram.height * z)));
    revalidate();
    repaint();
  }

  @Override protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      Palette palette = settings.darkMode ? Palette.DARK : Palette.LIGHT;
      g.setColor(Color.decode(palette.background()));
      g.fillRect(0, 0, getWidth(), getHeight());
      Editor.Rubber rubber = editor.rubber;
      DiagramPainter.Overlay overlay = new DiagramPainter.Overlay(List.copyOf(editor.selection),
          rubber == null ? null : rubber.from(), rubber == null ? null : rubber.to(), editor.lasso);
      DiagramPainter.draw(editor.diagram, new Graphics2DRenderer(g, editor.zoomFactor()), palette, settings.showGrid, overlay);
    } finally {
      g.dispose();
    }
  }
}
