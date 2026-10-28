package jetuml.ui;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import jetuml.app.Notification;

/** Notificações temporárias no canto inferior esquerdo: erros em vermelho, confirmações em verde (RF41, RF42). */
final class ToastLayer extends JPanel {
  private static final long serialVersionUID = 1L;
  private static final Color ERROR = new Color(0xc6, 0x28, 0x28), INFO = new Color(0x2e, 0x7d, 0x32);

  ToastLayer() {
    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
  }

  void show(Notification note, int seconds, Runnable layoutChanged) {
    JLabel label = new JLabel(note.message());
    label.setOpaque(true);
    label.setForeground(Color.WHITE);
    label.setBackground(note.level().equals("error") ? ERROR : INFO);
    label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    add(label);
    add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
    layoutChanged.run();
    Timer timer = new Timer(Math.max(1, seconds) * 1000, e -> {
      remove(label);
      if (getComponentCount() > 0) remove(0);          // espaçador associado
      layoutChanged.run();
    });
    timer.setRepeats(false);
    timer.start();
  }
}
