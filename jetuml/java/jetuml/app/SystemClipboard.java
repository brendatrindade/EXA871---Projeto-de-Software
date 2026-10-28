package jetuml.app;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/** Imagem na área de transferência do sistema (RF05). */
public final class SystemClipboard {
  private SystemClipboard() { }

  private record ImageSelection(Image image) implements Transferable {
    @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
    @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
    @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
      return image;
    }
  }

  /** @throws IllegalStateException se o sistema não oferece área de transferência (ex.: ambiente sem tela). */
  public static void copy(Image image) {
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
    } catch (java.awt.HeadlessException | IllegalStateException | SecurityException ex) {
      throw new IllegalStateException("A área de transferência do sistema não está disponível: " + ex.getMessage(), ex);
    }
  }
}
