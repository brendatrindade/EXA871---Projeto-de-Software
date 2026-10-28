package jetuml.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Notificações temporárias (RF41, RF42): erros em vermelho, confirmações em verde. */
public final class Notifier {
  private final Settings settings;
  public final List<Consumer<Notification>> listeners = new ArrayList<>();
  public final List<Notification> history = new ArrayList<>();

  public Notifier(Settings settings) { this.settings = settings; }

  public Notification post(String level, String message) {
    if (settings.notificationDuration == 0) return null;          // 0 desativa todas as notificações
    Notification note = new Notification(level, message);
    history.add(note);
    for (Consumer<Notification> listener : new ArrayList<>(listeners)) listener.accept(note);
    return note;
  }

  public Notification error(String message) { return post("error", message); }
  public Notification info(String message) { return post("info", message); }

  public List<String> errors() {
    List<String> out = new ArrayList<>();
    for (Notification n : history) if (n.level().equals("error")) out.add(n.message());
    return out;
  }
}
