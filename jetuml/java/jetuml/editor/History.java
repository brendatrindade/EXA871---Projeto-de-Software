package jetuml.editor;

import java.util.ArrayList;
import java.util.List;

/** Desfazer/refazer por fotografias do diagrama (memento); uma entrada por ação do usuário (RF21). */
public final class History {
  private final List<String> undoStack = new ArrayList<>();
  private final List<String> redoStack = new ArrayList<>();
  private int version;

  public int version() { return version; }

  public void push(String before) {
    undoStack.add(before);
    redoStack.clear();
    version++;
  }

  public String undo(String current) {
    if (undoStack.isEmpty()) return null;
    redoStack.add(current);
    version++;
    return undoStack.remove(undoStack.size() - 1);
  }

  public String redo(String current) {
    if (redoStack.isEmpty()) return null;
    undoStack.add(current);
    version++;
    return redoStack.remove(redoStack.size() - 1);
  }
}
