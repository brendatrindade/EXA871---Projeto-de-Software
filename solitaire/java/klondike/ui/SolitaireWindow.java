package klondike.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import klondike.game.Game;
import klondike.game.NullStrategy;
import klondike.game.Strategy;
import klondike.persistence.GameStore;
import klondike.strategy.AutomaticStrategy;

/** Janela principal: barra de botões e tabuleiro. */
public final class SolitaireWindow extends JFrame {
  private final Game game;

  public SolitaireWindow(Game game) {
    super("Solitaire — EXA871");
    this.game = game;
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    add(toolbar(), BorderLayout.NORTH);
    add(new BoardPanel(game), BorderLayout.CENTER);
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "undo");
    getRootPane().getActionMap().put("undo", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { game.undo(); }
    });
    pack();
    setLocationRelativeTo(null);
  }

  private JPanel toolbar() {
    JPanel bar = new JPanel();
    bar.add(button("Comprar / Nova partida", game::draw));
    bar.add(button("Desfazer", game::undo));
    bar.add(button("Jogada automática", game::automaticMove));
    bar.add(button("Salvar", this::save));
    bar.add(button("Abrir", this::open));
    JCheckBox strategyToggle = new JCheckBox("Estratégia ativa", !(game.strategy() instanceof NullStrategy));
    strategyToggle.addActionListener(e -> game.setStrategy(
        strategyToggle.isSelected() ? (Strategy) new AutomaticStrategy() : new NullStrategy()));
    bar.add(strategyToggle);
    return bar;
  }

  private static JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(e -> action.run());
    return button;
  }

  private void save() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    try {
      GameStore.save(game, chooser.getSelectedFile().toPath());
    } catch (IOException ex) {
      error("Falha ao salvar", ex);
    }
  }

  private void open() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path file = chooser.getSelectedFile().toPath();
    try {
      GameStore.load(game, file);
    } catch (IOException | IllegalArgumentException ex) {
      error("Falha ao abrir", ex);
    }
  }

  private void error(String title, Exception ex) {
    JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
  }
}
