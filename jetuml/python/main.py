"""Ponto de entrada. `python main.py` abre o editor; `python main.py --smoke` roda os testes sem janela."""
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def run_smoke() -> int:
    suite = unittest.defaultTestLoader.discover(str(HERE / "tests"), top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    if result.wasSuccessful():
        print(f"JetUML Python OK: {result.testsRun} testes de modelo, regras, edição, sequência, arquivos e exportação.")
        return 0
    return 1


def main() -> int:
    if "--smoke" in sys.argv:
        return run_smoke()
    import tkinter as tk
    from jetuml.java.jetuml.persistence.python.jetuml.settings import Settings
    from jetuml.java.jetuml.persistence.python.jetuml.ui.app import App
    from jetuml.java.jetuml.persistence.python.jetuml.workspace import Workspace
    root = tk.Tk()
    workspace = Workspace(Settings.load(), (root.winfo_screenwidth(), root.winfo_screenheight()))
    App(root, workspace)
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
