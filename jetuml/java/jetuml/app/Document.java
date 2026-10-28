package jetuml.app;

import java.nio.file.Path;
import jetuml.editor.Editor;
import jetuml.model.Diagram;

/** Uma aba: diagrama + editor + arquivo associado. */
public final class Document {
  public final Editor editor;
  public Path path;
  public String title;
  public int savedVersion;

  public Document(Editor editor, Path path, String title) {
    this.editor = editor;
    this.path = path;
    this.title = title;
    this.savedVersion = editor.history.version();
  }

  public Diagram diagram() { return editor.diagram; }
  public boolean dirty() { return editor.history.version() != savedVersion; }
  public String displayTitle() { return (path != null ? path.getFileName().toString() : title) + (dirty() ? " *" : ""); }
}
