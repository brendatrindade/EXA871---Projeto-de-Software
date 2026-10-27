"""Imagem na área de transferência do sistema (RF05), com implementação por plataforma."""
from __future__ import annotations

import ctypes
import io
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


class ClipboardUnavailable(RuntimeError):
    pass


def copy_image(image) -> None:
    if sys.platform == "win32":
        _copy_windows(image)
    elif sys.platform == "darwin":
        _copy_macos(image)
    else:
        _copy_linux(image)


def _png_bytes(image) -> bytes:
    stream = io.BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def _copy_linux(image) -> None:
    for command in (["xclip", "-selection", "clipboard", "-t", "image/png"], ["wl-copy", "--type", "image/png"]):
        if shutil.which(command[0]):
            subprocess.run(command, input=_png_bytes(image), check=True)
            return
    raise ClipboardUnavailable("Instale xclip (X11) ou wl-clipboard (Wayland) para copiar imagens.")


def _copy_macos(image) -> None:
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "diagram.png"
        path.write_bytes(_png_bytes(image))
        script = f'set the clipboard to (read (POSIX file "{path}") as «class PNGf»)'
        subprocess.run(["osascript", "-e", script], check=True)


def _copy_windows(image) -> None:
    stream = io.BytesIO()
    image.convert("RGB").save(stream, format="BMP")
    dib = stream.getvalue()[14:]
    user32, kernel32 = ctypes.windll.user32, ctypes.windll.kernel32
    user32.OpenClipboard.argtypes, user32.OpenClipboard.restype = [ctypes.c_void_p], ctypes.c_int
    user32.SetClipboardData.argtypes, user32.SetClipboardData.restype = [ctypes.c_uint, ctypes.c_void_p], ctypes.c_void_p
    kernel32.GlobalAlloc.argtypes, kernel32.GlobalAlloc.restype = [ctypes.c_uint, ctypes.c_size_t], ctypes.c_void_p
    kernel32.GlobalLock.argtypes, kernel32.GlobalLock.restype = [ctypes.c_void_p], ctypes.c_void_p
    kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
    kernel32.GlobalFree.argtypes = [ctypes.c_void_p]
    if not user32.OpenClipboard(None):
        raise ClipboardUnavailable("Não foi possível abrir a área de transferência do Windows.")
    handle = None
    try:
        user32.EmptyClipboard()
        handle = kernel32.GlobalAlloc(2, len(dib))
        pointer = kernel32.GlobalLock(handle) if handle else None
        if not pointer:
            raise ClipboardUnavailable("Memória insuficiente para a imagem.")
        ctypes.memmove(pointer, dib, len(dib))
        kernel32.GlobalUnlock(handle)
        if not user32.SetClipboardData(8, handle):
            raise ClipboardUnavailable("O Windows recusou a imagem.")
        handle = None
    finally:
        user32.CloseClipboard()
        if handle:
            kernel32.GlobalFree(handle)
