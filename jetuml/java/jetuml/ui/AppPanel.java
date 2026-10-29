package jetuml.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import jetuml.app.Document;
import jetuml.app.Workspace;
import jetuml.catalog.Catalog;
import jetuml.catalog.Tool;
import jetuml.editor.Editor;
import jetuml.model.Diagram;
import jetuml.model.Edge;
import jetuml.model.Field;
import jetuml.model.Node;
import jetuml.model.ValidationException;
import jetuml.text.TextFormat;

/** Conteúdo da janela principal: menus (File/Edit/View), abas, barra de ferramentas, boas-vindas e notificações. */
public final class AppPanel extends JLayeredPane implements DiagramPanel.ToolChooser {
  private static final long serialVersionUID = 1L;

  /** Abre um formulário (padrão: {@link FormDialog} modal); substituível para testes sem janela. */
  public interface DialogOpener {
    void open(String title, List<Field> fields, Map<String, Object> values, Predicate<Map<String, Object>> onOk, List<FormDialog.Extra> extras);
  }

  private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

  private final transient Workspace ws;
  private final JTabbedPane tabs = new JTabbedPane();
  private final transient Map<Document, JScrollPane> scrolls = new LinkedHashMap<>();
  private final transient Map<String, AbstractButton> toolButtons = new LinkedHashMap<>();
  private final JPanel toolbar = new JPanel();
  private final JPanel stage = new JPanel(new CardLayout());
  private final JPanel welcome = new JPanel();
  private final ToastLayer toasts = new ToastLayer();
  private final JPanel base = new JPanel(new BorderLayout());
  private final JMenu recentMenu = new JMenu("Recent Files");
  private final JMenuBar menuBar;
  private transient Runnable onExit = () -> { };
  private transient DialogOpener dialogs = this::openFormDialog;
  private final transient Map<String, JCheckBoxMenuItem> toggles = new LinkedHashMap<>();

  public AppPanel(Workspace workspace) {
    this.ws = workspace;
    toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
    toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    buildWelcome();
    stage.add(welcome, "welcome");
    stage.add(tabs, "tabs");
    base.add(toolbar, BorderLayout.WEST);
    base.add(stage, BorderLayout.CENTER);
    add(base, JLayeredPane.DEFAULT_LAYER);
    add(toasts, JLayeredPane.POPUP_LAYER);
    menuBar = buildMenu();
    registerZoomKeys();
    tabs.addChangeListener(e -> tabChanged());
    ws.notifier.listeners.add(note -> toasts.show(note, ws.settings.notificationDuration, this::placeToasts));
    refreshStage();
    applyTheme();
  }

  public JMenuBar menuBar() { return menuBar; }
  public void setOnExit(Runnable onExit) { this.onExit = onExit; }
  public void setDialogOpener(DialogOpener opener) { this.dialogs = opener; }
  public Workspace workspace() { return ws; }
  public JTabbedPane tabs() { return tabs; }
  public JPanel toolbar() { return toolbar; }

  @Override public void doLayout() {
    base.setBounds(0, 0, getWidth(), getHeight());
    placeToasts();
  }

  private void openFormDialog(String title, List<Field> fields, Map<String, Object> values,
                              Predicate<Map<String, Object>> onOk, List<FormDialog.Extra> extras) {
    new FormDialog(SwingUtilities.getWindowAncestor(this), title, fields, values, onOk, extras).setVisible(true);
  }

  private void placeToasts() {
    Component[] parts = toasts.getComponents();
    java.awt.Dimension pref = toasts.getPreferredSize();
    toasts.setBounds(8, getHeight() - pref.height - 8, Math.max(pref.width, 1), Math.max(pref.height, 1));
    toasts.revalidate();
    toasts.repaint();
    if (parts.length == 0) toasts.setBounds(0, 0, 0, 0);
  }

  // ---------- boas-vindas e abas (RF01) ----------
  private void buildWelcome() {
    welcome.setLayout(new BoxLayout(welcome, BoxLayout.Y_AXIS));
    welcome.add(Box.createVerticalStrut(80));
    JLabel title = new JLabel("JetUML Sketch");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
    title.setAlignmentX(CENTER_ALIGNMENT);
    welcome.add(title);
    JLabel hint = new JLabel("Escolha o tipo de diagrama para começar");
    hint.setAlignmentX(CENTER_ALIGNMENT);
    welcome.add(Box.createVerticalStrut(8));
    welcome.add(hint);
    welcome.add(Box.createVerticalStrut(16));
    for (String kind : Diagram.KINDS) welcome.add(centered(button(kind + " Diagram", () -> newDiagram(kind))));
    welcome.add(Box.createVerticalStrut(12));
    welcome.add(centered(button("Abrir arquivo…", () -> openFile(null))));
  }

  private static JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    b.addActionListener(e -> action.run());
    return b;
  }

  private static JComponent centered(JComponent c) {
    c.setAlignmentX(CENTER_ALIGNMENT);
    c.setMaximumSize(new java.awt.Dimension(240, 30));
    return c;
  }

  private void refreshStage() {
    ((CardLayout) stage.getLayout()).show(stage, ws.documents.isEmpty() ? "welcome" : "tabs");
    if (ws.documents.isEmpty()) rebuildToolbar();
  }

  private void addView(Document doc) {
    DiagramPanel panel = new DiagramPanel(doc.editor, ws.settings, this);
    JScrollPane scroll = new JScrollPane(panel);
    scrolls.put(doc, scroll);
    doc.editor.listeners.add(() -> updateTitle(doc));
    doc.editor.onEditRequested = element -> editProperties(doc, element);
    tabs.addTab(doc.displayTitle(), scroll);
    tabs.setSelectedComponent(scroll);
    ws.active = doc;
    refreshStage();
    rebuildToolbar();
    panel.requestFocusInWindow();
  }

  private void updateTitle(Document doc) {
    JScrollPane scroll = scrolls.get(doc);
    int index = scroll == null ? -1 : tabs.indexOfComponent(scroll);
    if (index >= 0) tabs.setTitleAt(index, doc.displayTitle());
  }

  private void tabChanged() {
    Component selected = tabs.getSelectedComponent();
    for (Map.Entry<Document, JScrollPane> entry : scrolls.entrySet()) {
      if (entry.getValue() == selected) {
        ws.active = entry.getKey();
        rebuildToolbar();
        return;
      }
    }
  }

  private Document document() { return ws.active; }

  // ---------- barra de ferramentas (RF07, RF08, RF10) ----------
  private void rebuildToolbar() {
    toolbar.removeAll();
    toolButtons.clear();
    Document doc = document();
    if (doc != null) {
      String kind = doc.diagram().kind;
      toolbar.add(new JLabel(kind + " Diagram"));
      toolbar.add(Box.createVerticalStrut(4));
      ButtonGroup group = new ButtonGroup();
      List<Tool> tools = Catalog.toolsFor(kind);
      for (int i = 0; i < tools.size(); i++) {
        Tool tool = tools.get(i);
        String label = tool.icon() + " " + Catalog.shortcut(i) + (ws.settings.showToolHint ? "  " + tool.name() : "");
        AbstractButton b = new JToggleButton(label);
        b.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));
        b.setToolTipText(ws.settings.verboseTooltips ? tool.description() : tool.name());
        b.addActionListener(e -> chooseTool(tool.name()));
        group.add(b);
        toolbar.add(b);
        toolButtons.put(tool.name(), b);
      }
      highlightTool();
    }
    toolbar.revalidate();
    toolbar.repaint();
  }

  private void highlightTool() {
    Document doc = document();
    if (doc == null) return;
    AbstractButton active = toolButtons.get(doc.editor.tool().name());
    if (active != null) active.setSelected(true);
  }

  @Override public void chooseTool(String name) {
    Document doc = document();
    if (doc == null) return;
    if (doc.editor.activateTool(name).equals("clipboard")) copyToClipboard();
    highlightTool();
  }

  @Override public void chooseShortcut(Editor editor, String key) {
    Document doc = document();
    if (doc == null || doc.editor != editor) return;
    Tool tool = Catalog.toolForShortcut(editor.diagram.kind, key);
    if (tool != null) chooseTool(tool.name());
  }

  // ---------- menus ----------
  private JMenuItem item(JMenu menu, String label, KeyStroke key, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    if (key != null) item.setAccelerator(key);
    item.addActionListener(e -> action.run());
    menu.add(item);
    return item;
  }

  private static KeyStroke ctrl(int code) { return KeyStroke.getKeyStroke(code, CTRL); }

  private void edit(Consumer<Editor> action) {
    Document doc = document();
    if (doc != null) action.accept(doc.editor);
  }

  private JMenuBar buildMenu() {
    JMenuBar bar = new JMenuBar();
    JMenu file = new JMenu("File");
    JMenu newMenu = new JMenu("New");
    for (String kind : Diagram.KINDS) item(newMenu, kind + " Diagram", kind.equals("Class") ? ctrl(KeyEvent.VK_N) : null, () -> newDiagram(kind));
    file.add(newMenu);
    item(file, "Open…", ctrl(KeyEvent.VK_O), () -> openFile(null));
    recentMenu.addMenuListener(new MenuListener() {
      @Override public void menuSelected(MenuEvent e) { fillRecent(); }
      @Override public void menuDeselected(MenuEvent e) { }
      @Override public void menuCanceled(MenuEvent e) { }
    });
    file.add(recentMenu);
    item(file, "Save", ctrl(KeyEvent.VK_S), this::save);
    item(file, "Save As…", null, this::saveAs);
    item(file, "Close", ctrl(KeyEvent.VK_W), this::closeCurrent);
    item(file, "Duplicate", ctrl(KeyEvent.VK_D), this::duplicate);
    item(file, "Export Image…", null, this::exportImage);
    item(file, "Copy to Clipboard", ctrl(KeyEvent.VK_B), this::copyToClipboard);
    file.addSeparator();
    item(file, "Exit", ctrl(KeyEvent.VK_Q), this::exit);
    bar.add(file);

    JMenu editMenu = new JMenu("Edit");
    item(editMenu, "Undo", ctrl(KeyEvent.VK_Z), () -> edit(Editor::undo));
    item(editMenu, "Redo", ctrl(KeyEvent.VK_Y), () -> edit(Editor::redo));
    item(editMenu, "Select All", ctrl(KeyEvent.VK_A), () -> edit(Editor::selectAll));
    item(editMenu, "Cut", ctrl(KeyEvent.VK_X), () -> edit(Editor::cutSelected));
    item(editMenu, "Copy", ctrl(KeyEvent.VK_C), () -> edit(Editor::copySelected));
    item(editMenu, "Paste", ctrl(KeyEvent.VK_V), () -> edit(Editor::paste));
    item(editMenu, "Delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), () -> edit(Editor::deleteSelected));
    item(editMenu, "Properties", ctrl(KeyEvent.VK_ENTER), () -> edit(Editor::requestProperties));
    bar.add(editMenu);

    JMenu view = new JMenu("View");
    toggle(view, "Show Grid", "grid", ws.settings.showGrid, v -> ws.settings.showGrid = v);
    toggle(view, "Show Tool Hint", "hint", ws.settings.showToolHint, v -> { ws.settings.showToolHint = v; rebuildToolbar(); });
    toggle(view, "Verbose Tooltips", "verbose", ws.settings.verboseTooltips, v -> { ws.settings.verboseTooltips = v; rebuildToolbar(); });
    toggle(view, "Auto Edit Node", "auto", ws.settings.autoEdit, v -> ws.settings.autoEdit = v);
    toggle(view, "Dark Mode", "dark", ws.settings.darkMode, v -> { ws.settings.darkMode = v; applyTheme(); });
    view.addSeparator();
    item(view, "Zoom In", ctrl(KeyEvent.VK_EQUALS), () -> edit(Editor::zoomIn));
    item(view, "Zoom Out", ctrl(KeyEvent.VK_MINUS), () -> edit(Editor::zoomOut));
    item(view, "Reset Zoom", ctrl(KeyEvent.VK_0), () -> edit(Editor::resetZoom));
    view.addSeparator();
    item(view, "Set Diagram Size…", null, this::setDiagramSize);
    item(view, "Change Font…", null, this::changeFont);
    item(view, "Notification Duration…", null, this::setDuration);
    bar.add(view);
    return bar;
  }

  private void toggle(JMenu menu, String label, String key, boolean initial, Consumer<Boolean> apply) {
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, initial);
    item.addActionListener(e -> {
      apply.accept(item.isSelected());
      ws.documents.forEach(d -> d.editor.changed());
    });
    toggles.put(key, item);
    menu.add(item);
  }

  /** Ctrl++ e Ctrl+= ampliam (RF37); o teclado numérico também funciona. */
  private void registerZoomKeys() {
    JComponent root = this;
    int[] codes = {KeyEvent.VK_PLUS, KeyEvent.VK_ADD};
    for (int code : codes) {
      root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrl(code), "zoomIn");
    }
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrl(KeyEvent.VK_SUBTRACT), "zoomOut");
    root.getActionMap().put("zoomIn", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { edit(Editor::zoomIn); }
    });
    root.getActionMap().put("zoomOut", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) { edit(Editor::zoomOut); }
    });
  }

  private void applyTheme() {
    boolean dark = ws.settings.darkMode;
    Color background = dark ? new Color(0x2b2f36) : null;
    for (JComponent c : List.of(toolbar, welcome, stage)) {
      c.setBackground(background);
      c.setOpaque(background != null || c == stage);
    }
    for (Component c : welcome.getComponents()) {
      if (c instanceof JLabel l) l.setForeground(dark ? new Color(0xf0f0f0) : null);
    }
    repaint();
  }

  // ---------- arquivos (RF01–RF06) ----------
  private void newDiagram(String kind) { addView(ws.newDocument(kind)); }

  private void openFile(Path given) {
    Path chosen = given;
    if (chosen == null) {
      JFileChooser chooser = new JFileChooser();
      chooser.setFileFilter(new FileNameExtensionFilter("JetUML JSON", "json"));
      if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
      chosen = chooser.getSelectedFile().toPath();
    }
    try {
      addView(ws.open(chosen));
    } catch (ValidationException ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "Falha ao abrir", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void fillRecent() {
    recentMenu.removeAll();
    for (String path : ws.settings.recentFiles) {
      JMenuItem item = new JMenuItem(path);
      item.addActionListener(e -> openFile(Path.of(path)));
      recentMenu.add(item);
    }
    if (ws.settings.recentFiles.isEmpty()) {
      JMenuItem empty = new JMenuItem("(vazio)");
      empty.setEnabled(false);
      recentMenu.add(empty);
    }
  }

  private boolean save() {
    Document doc = document();
    if (doc == null) return false;
    return doc.path == null ? saveAs() : write(doc, null);
  }

  private boolean saveAs() {
    Document doc = document();
    if (doc == null) return false;
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("JetUML JSON", "json"));
    return chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION && write(doc, chooser.getSelectedFile().toPath());
  }

  private boolean write(Document doc, Path path) {
    try {
      ws.save(doc, path);
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "Falha ao salvar", JOptionPane.ERROR_MESSAGE);
      return false;
    }
    updateTitle(doc);
    return true;
  }

  private void closeCurrent() {
    Document doc = document();
    if (doc == null || !confirmDiscard(doc)) return;
    JScrollPane scroll = scrolls.remove(doc);
    ws.close(doc);
    tabs.remove(scroll);
    refreshStage();
    rebuildToolbar();
  }

  private boolean confirmDiscard(Document doc) {
    if (!doc.dirty()) return true;
    int answer = JOptionPane.showConfirmDialog(this, "Salvar " + doc.displayTitle().replace(" *", "") + " antes de fechar?",
        "Salvar alterações", JOptionPane.YES_NO_CANCEL_OPTION);
    if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return false;
    return answer != JOptionPane.YES_OPTION || save();
  }

  private void duplicate() {
    Document doc = document();
    if (doc != null) addView(ws.duplicate(doc));
  }

  private void exportImage() {
    Document doc = document();
    if (doc == null) return;
    JFileChooser chooser = new JFileChooser();
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG image", "png"));
    chooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG image", "jpg", "jpeg"));
    chooser.addChoosableFileFilter(new FileNameExtensionFilter("SVG image", "svg"));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path path = chooser.getSelectedFile().toPath();
    if (!path.getFileName().toString().contains(".")) {
      String ext = chooser.getFileFilter() instanceof FileNameExtensionFilter f ? f.getExtensions()[0] : "png";
      path = path.resolveSibling(path.getFileName() + "." + ext);
    }
    try {
      ws.exportImage(doc, path);
    } catch (IOException | ValidationException ex) {
      JOptionPane.showMessageDialog(this, ex.getMessage(), "Falha ao exportar", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void copyToClipboard() {
    Document doc = document();
    if (doc != null) ws.copyToClipboard(doc);
  }

  private void exit() { onExit.run(); }

  /** Confirma (salvando se necessário) cada aba com alterações; false se o usuário cancelar. */
  public boolean confirmExit() {
    for (Document doc : List.copyOf(ws.documents)) {
      ws.active = doc;
      tabs.setSelectedComponent(scrolls.get(doc));
      if (!confirmDiscard(doc)) return false;
    }
    return true;
  }

  // ---------- propriedades (RF22) e View ----------
  private void editProperties(Document doc, Object element) {
    Editor editor = doc.editor;
    String title = "Propriedades — " + (element instanceof Edge e ? e.kind.value() : ((Node) element).type());
    dialogs.open(title, Editor.fieldsFor(element), editor.valuesFor(element),
        values -> { editor.applyProperties(element, values); return true; }, List.of());
  }

  private void setDiagramSize() {
    int[] size = ws.settings.effectiveSize(ws.screenWidth(), ws.screenHeight());
    List<Field> fields = List.of(Field.integer("width", "Width", 250, 4000, size[0]), Field.integer("height", "Height", 250, 4000, size[1]));
    dialogs.open("Set Diagram Size", fields, Map.of("width", size[0], "height", size[1]), values -> {
      try {
        ws.setDiagramSize((Integer) values.get("width"), (Integer) values.get("height"));
        return true;
      } catch (IllegalArgumentException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Tamanho inválido", JOptionPane.ERROR_MESSAGE);
        return false;
      }
    }, List.of(new FormDialog.Extra("Restore Defaults", ws::restoreDiagramSize)));
  }

  private void changeFont() {
    String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    Field family = new Field("family", "Font", "choice", List.of(families), TextFormat.family());
    List<Field> fields = List.of(family, Field.integer("size", "Size", 6, 48, TextFormat.size()));
    dialogs.open("Change Font", fields, Map.of("family", TextFormat.family(), "size", TextFormat.size()), values -> {
      try {
        ws.setFont((String) values.get("family"), (Integer) values.get("size"));
        return true;
      } catch (IllegalArgumentException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Fonte inválida", JOptionPane.ERROR_MESSAGE);
        return false;
      }
    }, List.of(new FormDialog.Extra("Restore Default", ws::restoreFont)));
  }

  private void setDuration() {
    List<Field> fields = List.of(Field.integer("seconds", "Seconds (0 = off)", 0, 10, ws.settings.notificationDuration));
    dialogs.open("Notification Duration", fields, Map.of("seconds", ws.settings.notificationDuration), values -> {
      ws.settings.setNotificationDuration((Integer) values.get("seconds"));
      return true;
    }, List.of(new FormDialog.Extra("Restore Default", ws.settings::restoreNotificationDuration)));
  }
}
