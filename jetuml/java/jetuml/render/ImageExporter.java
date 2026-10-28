package jetuml.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import jetuml.geometry.Rect;
import jetuml.model.Diagram;

/** Imagem do diagrama (PNG/JPEG e área de transferência): recortada ao conteúdo e sem grade (RF04, RF05). */
public final class ImageExporter {
  public static final int MARGIN = 10;

  private ImageExporter() { }

  public static BufferedImage render(Diagram d, Palette palette, boolean grid, boolean contentOnly) {
    Rect bounds = contentOnly ? d.contentBounds() : null;
    if (bounds == null) bounds = contentOnly ? new Rect(0, 0, 120, 60) : new Rect(0, 0, d.width, d.height);
    else bounds = bounds.inflated(MARGIN);
    BufferedImage image = new BufferedImage(Math.max(1, (int) bounds.w()), Math.max(1, (int) bounds.h()), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(Color.decode(palette.background()));
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      DiagramPainter.draw(d, new OffsetRenderer(new Graphics2DRenderer(g, 1.0), -bounds.x(), -bounds.y()), palette, grid, DiagramPainter.Overlay.NONE);
    } finally {
      g.dispose();
    }
    return image;
  }

  public static BufferedImage render(Diagram d) { return render(d, Palette.LIGHT, false, true); }

  public static void write(BufferedImage image, Path file, String format) throws IOException {
    if (!ImageIO.write(image, format, file.toFile())) throw new IOException("Formato de imagem indisponível: " + format);
  }
}
