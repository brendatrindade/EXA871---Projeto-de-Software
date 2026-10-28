package jetuml.ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import jetuml.model.Field;

/** Janela modal que hospeda um {@link FormPanel}; Esc fecha e Ctrl+Enter confirma (RF22). */
public final class FormDialog extends JDialog {
  private static final long serialVersionUID = 1L;

  /** Botão extra (ex.: "Restore Defaults"): executa e fecha o diálogo. */
  public record Extra(String label, Runnable action) { }

  public FormDialog(Window owner, String title, List<Field> fields, Map<String, Object> values,
                    Predicate<Map<String, Object>> onOk, List<Extra> extras) {
    super(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
    FormPanel form = new FormPanel(fields, values, onOk);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    for (Extra extra : extras) {
      JButton b = new JButton(extra.label());
      b.addActionListener(e -> { extra.action().run(); dispose(); });
      buttons.add(b);
    }
    JButton cancel = new JButton("Cancelar"), ok = new JButton("OK");
    cancel.addActionListener(e -> dispose());
    ok.addActionListener(e -> { if (form.accept()) dispose(); });
    buttons.add(cancel);
    buttons.add(ok);
    add(form, BorderLayout.CENTER);
    add(buttons, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(ok);
    FormPanel.bind(getRootPane(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", this::dispose);
    FormPanel.bind(getRootPane(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "accept", ok::doClick);
    pack();
    setLocationRelativeTo(owner);
    JComponent first = form.firstWidget();
    if (first != null) first.requestFocusInWindow();
  }
}
