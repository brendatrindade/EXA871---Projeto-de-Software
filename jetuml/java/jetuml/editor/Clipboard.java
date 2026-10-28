package jetuml.editor;

import java.util.List;
import java.util.Map;

/** Área de transferência interna, compartilhada entre os diagramas abertos (RF34). */
public final class Clipboard {
  public record ClipData(String kind, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) { }

  public ClipData data;
  public int pastes;
}
