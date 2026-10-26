package klondike.game;

import klondike.piles.TableauColumn;

/** Revela a carta oculta exposta por uma jogada (RF19). É consequência da jogada, não uma jogada. */
public final class RevealAction implements Action {
  private final TableauColumn column;
  private boolean done;

  public RevealAction(TableauColumn column) { this.column = column; }

  @Override public boolean isLegal(Game game) { return true; }

  @Override public boolean execute(Game game) {
    done = column.revealNext();
    return done;
  }

  @Override public void undo(Game game) {
    if (done) {
      column.concealLastRevealed();
      done = false;
    }
  }
}
