package jetuml.model;

import java.util.List;

/** Descrição declarativa de um campo editável (RF20, RF22). kind: text | multiline | choice | bool | int. */
public record Field(String name, String label, String kind, List<String> choices, Object defaultValue) {
  public static Field text(String name, String label, String def) { return new Field(name, label, "text", List.of(), def); }
  public static Field multiline(String name, String label) { return new Field(name, label, "multiline", List.of(), ""); }
  public static Field multiline(String name, String label, String def) { return new Field(name, label, "multiline", List.of(), def); }
  public static Field bool(String name, String label, boolean def) { return new Field(name, label, "bool", List.of(), def); }
  public static Field choice(String name, String label, String def, String... choices) {
    return new Field(name, label, "choice", List.of(choices), def);
  }
  public static Field integer(String name, String label, int min, int max, int def) {
    return new Field(name, label, "int", List.of(String.valueOf(min), String.valueOf(max)), def);
  }
}
