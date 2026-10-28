package jetuml.editor;

/** Modificadores do evento (Ctrl, Shift). */
public record Mods(boolean ctrl, boolean shift) {
  public static final Mods NONE = new Mods(false, false);
  public static final Mods CTRL = new Mods(true, false);
  public static final Mods SHIFT = new Mods(false, true);
}
