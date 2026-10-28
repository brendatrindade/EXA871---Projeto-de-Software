package jetuml.ui;

import java.awt.AWTKeyStroke;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.JTextComponent;
import jetuml.model.Field;
import jetuml.text.TextFormat;

/**
 * Formulário descrito por campos ({@link Field}; kind "int" usa choices = {mín, máx}).
 * Tab navega entre os campos, Esc fecha, Ctrl+Q insere « » (RF22, RF23). {@code onOk} retorna false para manter aberto.
 */
public final class FormPanel extends JPanel {
  private static final long serialVersionUID = 1L;
  private final transient List<Field> fields;
  private final transient Predicate<Map<String, Object>> onOk;
  private final transient Map<String, JComponent> widgets = new LinkedHashMap<>();
  private JComponent first;

  public FormPanel(List<Field> fields, Map<String, Object> values, Predicate<Map<String, Object>> onOk) {
    super(new GridBagLayout());
    this.fields = fields;
    this.onOk = onOk;
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4, 8, 4, 8);
    c.anchor = GridBagConstraints.NORTHWEST;
    int row = 0;
    for (Field field : fields) {
      c.gridy = row++;
      c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
      add(new JLabel(field.label()), c);
      JComponent widget = make(field, values.getOrDefault(field.name(), field.defaultValue()));
      widgets.put(field.name(), widget);
      c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
      add(widget instanceof JTextArea area ? new JScrollPane(area) : widget, c);
      if (first == null) first = widget;
    }
  }

  /** Primeiro campo, para receber o foco ao abrir. */
  public JComponent firstWidget() { return first; }
  public JComponent widget(String name) { return widgets.get(name); }

  static void bind(JComponent component, KeyStroke key, String name, Runnable action) {
    component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
    component.getActionMap().put(name, new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { action.run(); }
    });
  }

  private JComponent make(Field field, Object value) {
    switch (field.kind()) {
      case "multiline": {
        JTextArea area = new JTextArea(String.valueOf(value), 5, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0)));
        area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            Set.of(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)));
        stereotypeShortcut(area);
        return area;
      }
      case "choice": {
        JComboBox<String> combo = new JComboBox<>(field.choices().toArray(String[]::new));
        combo.setSelectedItem(String.valueOf(value));
        return combo;
      }
      case "bool":
        return new JCheckBox("", Boolean.TRUE.equals(value));
      case "int": {
        int min = Integer.parseInt(field.choices().get(0)), max = Integer.parseInt(field.choices().get(1));
        return new JSpinner(new SpinnerNumberModel(((Number) value).intValue(), min, max, 1));
      }
      default: {
        JTextField text = new JTextField(String.valueOf(value), 30);
        stereotypeShortcut(text);
        return text;
      }
    }
  }

  /** Ctrl+Q: insere « » e posiciona o cursor entre eles (RF23). */
  private static void stereotypeShortcut(JTextComponent component) {
    bind(component, KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), "stereotype", () -> {
      TextFormat.Inserted result = TextFormat.insertStereotype(component.getText(), component.getCaretPosition());
      component.setText(result.text());
      component.setCaretPosition(result.cursor());
    });
    component.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), "stereotype");
  }

  public Map<String, Object> collect() {
    Map<String, Object> values = new LinkedHashMap<>();
    for (Field field : fields) {
      JComponent w = widgets.get(field.name());
      if (w instanceof JTextArea a) values.put(field.name(), a.getText());
      else if (w instanceof JTextField t) values.put(field.name(), t.getText());
      else if (w instanceof JComboBox<?> c) values.put(field.name(), String.valueOf(c.getSelectedItem()));
      else if (w instanceof JCheckBox b) values.put(field.name(), b.isSelected());
      else if (w instanceof JSpinner s) values.put(field.name(), ((Number) s.getValue()).intValue());
    }
    return values;
  }

  /** Confirma campos de número digitados e chama {@code onOk}; retorna true se o formulário pode fechar. */
  public boolean accept() {
    for (JComponent w : widgets.values()) {
      if (w instanceof JSpinner s) {
        try { s.commitEdit(); } catch (java.text.ParseException ignored) { /* mantém o último valor válido */ }
      }
    }
    return onOk.test(collect());
  }
}
