package jetuml.text;

import java.util.Arrays;
import java.util.List;

/** Métricas de texto (fonte, RF39), formatação de linhas (RF24) e estereótipos (RF23). */
public final class TextFormat {
  public static final String OPEN = "«", CLOSE = "»";
  public static final String DEFAULT_FAMILY = "Helvetica";
  public static final int DEFAULT_SIZE = 11;

  private static String family = DEFAULT_FAMILY;
  private static int size = DEFAULT_SIZE;

  public record Parsed(String text, boolean italic, boolean underline) { }
  public record Inserted(String text, int cursor) { }

  private TextFormat() { }

  public static String family() { return family; }
  public static int size() { return size; }
  public static int charWidth() { return (int) Math.max(4, Math.rint(size * 0.62)); }
  public static int lineHeight() { return (int) Math.max(10, Math.rint(size * 1.6)); }

  public static void setFont(String newFamily, int newSize) {
    if (newSize < 6 || newSize > 48) throw new IllegalArgumentException("Tamanho de fonte deve estar entre 6 e 48");
    family = newFamily;
    size = newSize;
  }

  public static void restoreDefault() { family = DEFAULT_FAMILY; size = DEFAULT_SIZE; }

  /** {@code /texto/} → itálico; {@code _texto_} → sublinhado. */
  public static Parsed parseLine(String line) {
    boolean italic = false, underline = false, changed = true;
    while (changed && line.length() >= 2) {
      changed = false;
      char first = line.charAt(0), last = line.charAt(line.length() - 1);
      if (first == '/' && last == '/' && !italic) {
        line = line.substring(1, line.length() - 1);
        italic = changed = true;
      } else if (first == '_' && last == '_' && !underline) {
        line = line.substring(1, line.length() - 1);
        underline = changed = true;
      }
    }
    return new Parsed(line, italic, underline);
  }

  public static int textWidth(String line) { return parseLine(line).text().length() * charWidth(); }

  public static List<String> linesOf(String text) {
    return text == null || text.isEmpty() ? List.of() : Arrays.asList(text.split("\n", -1));
  }

  /** Ctrl+Q: insere « » no cursor e posiciona o cursor entre eles (RF23). */
  public static Inserted insertStereotype(String text, int cursor) {
    return new Inserted(text.substring(0, cursor) + OPEN + CLOSE + text.substring(cursor), cursor + 1);
  }
}
