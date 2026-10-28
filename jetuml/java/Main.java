import java.awt.Toolkit;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import jetuml.app.Settings;
import jetuml.app.Workspace;
import jetuml.smoke.SmokeTests;
import jetuml.ui.AppWindow;

/** Ponto de entrada: {@code java Main} abre o editor; {@code java Main --smoke} roda os testes sem janela. */
public final class Main {
  private Main() { }

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("--smoke")) System.exit(SmokeTests.run());
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
        // mantém o visual padrão do Swing
      }
      java.awt.Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      Workspace workspace = new Workspace(Settings.load(), screen.width, screen.height);
      new AppWindow(workspace).setVisible(true);
    });
  }
}
