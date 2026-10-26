package klondike.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitor/escritor JSON mínimo (objetos, listas, strings, inteiros, true/false/null). */
final class Json {
  private final String text;
  private int pos;

  private Json(String text) { this.text = text; }

  static Object parse(String text) {
    Json parser = new Json(text);
    Object value = parser.value();
    parser.skipWhitespace();
    if (parser.pos != text.length()) throw parser.error("conteúdo extra após o documento");
    return value;
  }

  static String quote(String s) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
      }
    }
    return out.append('"').toString();
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException("JSON inválido (posição " + pos + "): " + message);
  }

  private void skipWhitespace() {
    while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
  }

  private char next() {
    skipWhitespace();
    if (pos >= text.length()) throw error("fim inesperado");
    return text.charAt(pos++);
  }

  private void expect(char c) {
    if (next() != c) throw error("esperado '" + c + "'");
  }

  private Object value() {
    char c = next();
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      default -> {
        pos--;
        yield atom();
      }
    };
  }

  private Map<String, Object> object() {
    Map<String, Object> map = new LinkedHashMap<>();
    skipWhitespace();
    if (pos < text.length() && text.charAt(pos) == '}') { pos++; return map; }
    while (true) {
      expect('"');
      String key = string();
      expect(':');
      map.put(key, value());
      char end = next();
      if (end == '}') return map;
      if (end != ',') throw error("esperado ',' ou '}'");
    }
  }

  private List<Object> array() {
    List<Object> list = new ArrayList<>();
    skipWhitespace();
    if (pos < text.length() && text.charAt(pos) == ']') { pos++; return list; }
    while (true) {
      list.add(value());
      char end = next();
      if (end == ']') return list;
      if (end != ',') throw error("esperado ',' ou ']'");
    }
  }

  private String string() {
    StringBuilder out = new StringBuilder();
    while (pos < text.length()) {
      char c = text.charAt(pos++);
      if (c == '"') return out.toString();
      if (c != '\\') { out.append(c); continue; }
      if (pos >= text.length()) break;
      char e = text.charAt(pos++);
      switch (e) {
        case '"', '\\', '/' -> out.append(e);
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'u' -> {
          if (pos + 4 > text.length()) throw error("escape unicode inválido");
          out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
          pos += 4;
        }
        default -> throw error("escape inválido");
      }
    }
    throw error("string não terminada");
  }

  private Object atom() {
    int start = pos;
    while (pos < text.length() && ",]} \r\n\t".indexOf(text.charAt(pos)) < 0) pos++;
    String atom = text.substring(start, pos);
    switch (atom) {
      case "true": return Boolean.TRUE;
      case "false": return Boolean.FALSE;
      case "null": return null;
      default:
        try {
          return Integer.valueOf(atom);
        } catch (NumberFormatException ex) {
          throw error("valor não suportado: " + atom);
        }
    }
  }
}
