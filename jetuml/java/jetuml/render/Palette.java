package jetuml.render;

public record Palette(String background, String foreground, String nodeFill, String noteFill, String grid, String selection) {
  public static final Palette LIGHT = new Palette("#ffffff", "#000000", "#ffffff", "#fff2b2", "#e6e6e6", "#087E8B");
  public static final Palette DARK = new Palette("#20242a", "#f0f0f0", "#30343a", "#5c5424", "#484c52", "#3fd0e0");
}
