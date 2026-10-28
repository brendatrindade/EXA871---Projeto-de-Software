package jetuml.ui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import jetuml.app.Workspace;

/** Janela principal: apenas hospeda o {@link AppPanel} e trata o fechamento. */
public final class AppWindow extends JFrame {
  private static final long serialVersionUID = 1L;

  public AppWindow(Workspace workspace) {
    super("JetUML Sketch — EXA871");
    AppPanel panel = new AppPanel(workspace);
    setContentPane(panel);
    setJMenuBar(panel.menuBar());
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    Runnable exit = () -> {
      if (panel.confirmExit()) {
        workspace.settings.save();
        dispose();
        System.exit(0);
      }
    };
    panel.setOnExit(exit);
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) { exit.run(); }
    });
    setSize(1180, 780);
    setLocationRelativeTo(null);
  }
}
