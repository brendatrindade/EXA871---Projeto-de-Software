package jetuml.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jetuml.editor.Clipboard;
import jetuml.editor.Editor;
import jetuml.model.Diagram;
import jetuml.model.ValidationException;
import jetuml.persistence.DiagramSerializer;
import jetuml.render.ImageExporter;
import jetuml.render.SvgRenderer;
import jetuml.text.TextFormat;

/** Sessão do aplicativo (sem toolkit gráfico): documentos/abas, arquivos, exportação e configurações. */
public final class Workspace {
  private static final Map<String, String> IMAGE_FORMATS = Map.of(".svg", "SVG", ".png", "png", ".jpg", "jpg", ".jpeg", "jpg");

  public final Settings settings;
  public final Notifier notifier;
  public final Clipboard clipboard = new Clipboard();
  public final List<Document> documents = new ArrayList<>();
  public Document active;
  private final int screenW, screenH;

  public Workspace(Settings settings, int screenW, int screenH) {
    this.settings = settings;
    this.notifier = new Notifier(settings);
    this.screenW = screenW;
    this.screenH = screenH;
    if (settings.fontFamily != null && settings.fontSize != null) {
      try {
        TextFormat.setFont(settings.fontFamily, settings.fontSize);
      } catch (IllegalArgumentException ignored) {
        // fonte salva inválida: mantém o padrão
      }
    }
  }

  public Workspace() { this(new Settings(), 1280, 800); }

  public int screenWidth() { return screenW; }
  public int screenHeight() { return screenH; }

  // ---------- documentos (RF01–RF03) ----------
  private Document adopt(Diagram diagram, Path path, String title) {
    int[] size = settings.effectiveSize(screenW, screenH);
    diagram.width = size[0];
    diagram.height = size[1];
    diagram.growToContent();                              // RF38: diagramas maiores aumentam a área
    Document document = new Document(new Editor(diagram, notifier, clipboard, settings), path, title);
    documents.add(document);
    active = document;
    return document;
  }

  public Document newDocument(String kind) { return adopt(new Diagram(kind), null, "Novo " + kind); }

  public Document open(Path path) {
    Diagram diagram;
    try {
      diagram = DiagramSerializer.loads(Files.readString(path));
    } catch (IOException ex) {
      throw new ValidationException("Não foi possível ler o arquivo: " + ex.getMessage());
    }
    Document document = adopt(diagram, path, path.getFileName().toString());
    settings.addRecent(path.toString());
    notifier.info(path.getFileName() + " aberto.");
    return document;
  }

  public void save(Document document, Path path) throws IOException {
    Path target = path != null ? path : document.path;
    if (target == null) throw new IllegalArgumentException("Informe o caminho para salvar.");
    if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
      target = target.resolveSibling(target.getFileName() + ".json");
    }
    Files.writeString(target, DiagramSerializer.dumps(document.diagram()));
    document.path = target;
    document.savedVersion = document.editor.history.version();
    settings.addRecent(target.toString());
    notifier.info(target.getFileName() + " salvo.");
  }

  public void close(Document document) {
    int index = documents.indexOf(document);
    documents.remove(document);
    active = documents.isEmpty() ? null : documents.get(Math.min(index, documents.size() - 1));
  }

  public Document duplicate(Document document) {
    Diagram copy = DiagramSerializer.fromMap(DiagramSerializer.toMap(document.diagram()), true);
    String base = document.path != null ? document.path.getFileName().toString().replaceAll("\\.[^.]*$", "") : document.title;
    return adopt(copy, null, base + " (cópia)");
  }

  // ---------- imagens (RF04, RF05) ----------
  public void exportImage(Document document, Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
    String format = IMAGE_FORMATS.get(ext);
    if (format == null) throw new ValidationException("Formato não suportado; use .svg, .png ou .jpg.");
    Diagram d = document.diagram();
    if (format.equals("SVG")) {
      var bounds = d.contentBounds();
      Files.writeString(path, SvgRenderer.render(d, bounds == null ? null : bounds.inflated(ImageExporter.MARGIN),
          jetuml.render.Palette.LIGHT, false));
    } else {
      ImageExporter.write(ImageExporter.render(d), path, format);
    }
    notifier.info("Imagem exportada para " + path.getFileName() + ".");
  }

  public void copyToClipboard(Document document) {
    try {
      SystemClipboard.copy(ImageExporter.render(document.diagram()));
    } catch (IllegalStateException ex) {
      notifier.error(ex.getMessage());
      return;
    }
    notifier.info("Diagrama copiado para a área de transferência.");
  }

  // ---------- View (RF38, RF39) ----------
  public void setDiagramSize(int width, int height) {
    settings.setDiagramSize(width, height);
    resizeAll();
  }

  public void restoreDiagramSize() {
    settings.restoreDiagramSize();
    resizeAll();
  }

  private void resizeAll() {
    for (Document document : documents) {
      int[] size = settings.effectiveSize(screenW, screenH);
      document.diagram().width = size[0];
      document.diagram().height = size[1];
      document.diagram().growToContent();
      document.editor.changed();
    }
  }

  public void setFont(String family, int size) {
    TextFormat.setFont(family, size);
    settings.fontFamily = family;
    settings.fontSize = size;
    relayoutAll();
  }

  public void restoreFont() {
    TextFormat.restoreDefault();
    settings.fontFamily = null;
    settings.fontSize = null;
    relayoutAll();
  }

  private void relayoutAll() {
    for (Document document : documents) {
      document.diagram().relayout();
      document.editor.changed();
    }
  }
}
