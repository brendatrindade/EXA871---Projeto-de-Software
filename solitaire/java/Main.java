import java.util.Random;
import javax.swing.SwingUtilities;
import klondike.game.Game;
import klondike.smoke.SmokeTests;
import klondike.strategy.AutomaticStrategy;
import klondike.ui.SolitaireWindow;

/** Ponto de entrada: {@code java Main} abre o jogo; {@code java Main --smoke} roda os testes sem janela. */
public final class Main {
  private Main() { }

  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("--smoke")) {
      System.exit(SmokeTests.run());
    }
    SwingUtilities.invokeLater(() ->
        new SolitaireWindow(new Game(new Random(), new AutomaticStrategy())).setVisible(true));
  }
}
