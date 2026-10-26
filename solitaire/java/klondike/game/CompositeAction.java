package klondike.game;

import java.util.List;

/** Executa as ações na ordem e as desfaz na ordem inversa (RF23). */
public final class CompositeAction implements Action {
  private final List<Action> actions;

  public CompositeAction(List<Action> actions) { this.actions = List.copyOf(actions); }

  @Override public boolean isLegal(Game game) {
    return actions.stream().allMatch(action -> action.isLegal(game));
  }

  @Override public boolean execute(Game game) {
    boolean changed = false;
    for (Action action : actions) changed |= action.execute(game);
    return changed;
  }

  @Override public void undo(Game game) {
    for (int i = actions.size() - 1; i >= 0; i--) actions.get(i).undo(game);
  }
}
