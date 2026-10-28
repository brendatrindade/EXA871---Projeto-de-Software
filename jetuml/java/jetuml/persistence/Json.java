package jetuml.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitor/escritor JSON mínimo (objetos, listas, strings, números, true/false/null), formato igual ao do Python. */
public final class Json {
  private final String text;
  private int pos;

  private Json(String text) { this.text = text; }

  public static Object parse(String text) {
    Json parser = new Json(text);
    Object value = parser.value();
    parser.skipWhitespace();
    if (parser.pos != text.length()) throw parser.error("conteúdo extra após o documento");
    return value;
  }

  /** Como {@link #parse}, mas converte erros de sintaxe em ValidationException. */
  public static Object parseOrInvalid(String text) {
    try {
      return parse(text);
    } catch (IllegalArgumentException ex) {
      throw new jetuml.jetuml.model.ValidationException(ex.getMessage());
    }
  }

  // ---------- escrita (indentação de 2 espaços, uma entrada por linha) ----------
  public static String write(Object value) {
    StringBuilder out = new StringBuilder();
    write(value, 0, out);
    return out.toString();
  }

  private static void write(Object value, int level, StringBuilder out) {
    if (value == null) out.append("null");
    else if (value instanceof String s) out.append(quote(s));
    else if (value instanceof Boolean b) out.append(b);
    else if (value instanceof Double d) out.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : d.toString());
    else if (value instanceof Number n) out.append(n);
    else if (value instanceof Map<?, ?> map) {
      if (map.isEmpty()) { out.append("{}"); return; }
      out.append("{\n");
      int i = 0;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        out.append("  ".repeat(level + 1)).append(quote(String.valueOf(e.getKey()))).append(": ");
        write(e.getValue(), level + 1, out);
        out.append(++i < map.size() ? ",\n" : "\n");
      }
      out.append("  ".repeat(level)).append('}');
    } else if (value instanceof List<?> list) {
      if (list.isEmpty()) { out.append("[]"); return; }
      out.append("[\n");
      for (int i = 0; i < list.size(); i++) {
        out.append("  ".repeat(level + 1));
        write(list.get(i), level + 1, out);
        out.append(i < list.size() - 1 ? ",\n" : "\n");
      }
      out.append("  ".repeat(level)).append(']');
    } else {
      throw new IllegalArgumentException("Valor não serializável: " + value.getClass());
    }
  }

  public static String quote(String s) {
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

  // ---------- leitura ----------
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
    switch (c) {
      case '{': return object();
      case '[': return array();
      case '"': return string();
      default:
        pos--;
        return atom();
    }
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
          return atom.matches("-?\\d+") ? (Object) Long.valueOf(atom) : (Object) Double.valueOf(atom);
        } catch (NumberFormatException ex) {
          throw error("valor não suportado: " + atom);
        }
    }
  }
}
