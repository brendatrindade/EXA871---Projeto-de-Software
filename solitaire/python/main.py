"""Ponto de entrada. `python main.py` abre o jogo; `python main.py --smoke` roda os testes sem janela."""
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def run_smoke() -> int:
    suite = unittest.defaultTestLoader.discover(str(HERE / "tests"), top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    if result.wasSuccessful():
        print(f"Solitaire Python OK: {result.testsRun} testes do modelo, estratégia, histórico e persistência.")
        return 0
    return 1


def main() -> int:
    if "--smoke" in sys.argv:
        return run_smoke()
    import tkinter as tk
    from klondike.game import Game
    from klondike.ui import SolitaireUI
    root = tk.Tk()
    root.title("Solitaire — EXA871")
    SolitaireUI(root, Game())
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
