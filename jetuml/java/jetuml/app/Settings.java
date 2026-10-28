package jetuml.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jetuml.persistence.Json;

/** Preferências (View) e arquivos recentes; persistidas em JSON, no mesmo arquivo e chaves da versão Python. */
public final class Settings {
  public static final int MIN_SIZE = 250, MAX_SIZE = 4000, MAX_RECENT = 10;
  public static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".jetuml-sketch", "settings.json");

  public boolean showGrid = true, darkMode, autoEdit, showToolHint, verboseTooltips;
  public int notificationDuration = 5;
  public String fontFamily;
  public Integer fontSize;
  public int[] diagramSize;                                  // null = padrão relativo à tela
  public final List<String> recentFiles = new ArrayList<>();

  /** Tamanho padrão proporcional à tela (RF38, RNF01). */
  public static int[] defaultDiagramSize(int screenW, int screenH) {
    return new int[] {clamp(screenW), clamp(screenH)};
  }

  private static int clamp(int v) { return Math.max(MIN_SIZE, Math.min(MAX_SIZE, v)); }

  public void setNotificationDuration(int seconds) {
    if (seconds < 0 || seconds > 10) throw new IllegalArgumentException("A duração deve estar entre 0 e 10 segundos");
    notificationDuration = seconds;
  }

  public void restoreNotificationDuration() { notificationDuration = 5; }

  public void setDiagramSize(int width, int height) {
    if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE) {
      throw new IllegalArgumentException("Largura e altura devem estar entre " + MIN_SIZE + " e " + MAX_SIZE);
    }
    diagramSize = new int[] {width, height};
  }

  public void restoreDiagramSize() { diagramSize = null; }

  public int[] effectiveSize(int screenW, int screenH) {
    return diagramSize != null ? diagramSize.clone() : defaultDiagramSize(screenW, screenH);
  }

  public void addRecent(String path) {
    recentFiles.remove(path);
    recentFiles.add(0, path);
    while (recentFiles.size() > MAX_RECENT) recentFiles.remove(recentFiles.size() - 1);
  }

  public void save(Path path) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("show_grid", showGrid);
    data.put("dark_mode", darkMode);
    data.put("auto_edit", autoEdit);
    data.put("show_tool_hint", showToolHint);
    data.put("verbose_tooltips", verboseTooltips);
    data.put("notification_duration", notificationDuration);
    data.put("font_family", fontFamily);
    data.put("font_size", fontSize);
    data.put("diagram_size", diagramSize == null ? null : List.of(diagramSize[0], diagramSize[1]));
    data.put("recent_files", new ArrayList<>(recentFiles));
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, Json.write(data));
    } catch (IOException ignored) {
      // preferências são opcionais
    }
  }

  public void save() { save(DEFAULT_PATH); }

  public static Settings load(Path path) {
    Settings s = new Settings();
    try {
      Object parsed = Json.parse(Files.readString(path));
      if (!(parsed instanceof Map<?, ?> data)) return new Settings();
      s.showGrid = bool(data.get("show_grid"), true);
      s.darkMode = bool(data.get("dark_mode"), false);
      s.autoEdit = bool(data.get("auto_edit"), false);
      s.showToolHint = bool(data.get("show_tool_hint"), false);
      s.verboseTooltips = bool(data.get("verbose_tooltips"), false);
      if (data.get("notification_duration") instanceof Number n) s.setNotificationDuration(n.intValue());
      if (data.get("font_family") instanceof String f) s.fontFamily = f;
      if (data.get("font_size") instanceof Number n) s.fontSize = n.intValue();
      if (data.get("diagram_size") instanceof List<?> l && l.size() == 2) s.setDiagramSize(((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue());
      if (data.get("recent_files") instanceof List<?> l) for (Object o : l) if (o instanceof String p) s.recentFiles.add(p);
      return s;
    } catch (IOException | RuntimeException ex) {
      return new Settings();
    }
  }

  public static Settings load() { return load(DEFAULT_PATH); }

  private static boolean bool(Object v, boolean def) { return v instanceof Boolean b ? b : def; }
}
