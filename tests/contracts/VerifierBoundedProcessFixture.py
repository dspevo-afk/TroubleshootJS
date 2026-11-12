"""CODE-only direct-child witness; never reads challenge or cohort data."""

import ctypes
from ctypes import wintypes
import hashlib
import json
from pathlib import Path
import os
import sys
import time


def observe_process(nonce):
    """Independent kernel witness shared by the CODE-only process contracts."""
    if os.name != "nt" or not nonce:
        raise RuntimeError("Windows fixture nonce required")
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [
        ctypes.POINTER(wintypes.FILETIME)
    ] * 4
    kernel.GetProcessTimes.restype = wintypes.BOOL
    kernel.QueryFullProcessImageNameW.argtypes = [
        wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR,
        ctypes.POINTER(wintypes.DWORD),
    ]
    kernel.QueryFullProcessImageNameW.restype = wintypes.BOOL
    kernel.GetCommandLineW.restype = wintypes.LPWSTR
    handle = kernel.GetCurrentProcess()
    times = [wintypes.FILETIME() for _ in range(4)]
    if not kernel.GetProcessTimes(handle, *[ctypes.byref(t) for t in times]):
        raise ctypes.WinError(ctypes.get_last_error())
    size = wintypes.DWORD(32768)
    image = ctypes.create_unicode_buffer(size.value)
    if not kernel.QueryFullProcessImageNameW(handle, 0, image, ctypes.byref(size)):
        raise ctypes.WinError(ctypes.get_last_error())
    creation = (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime
    return {
        "nonce": nonce,
        "pid": os.getpid(),
        "creationFileTime": creation,
        "imagePath": image.value,
        "imageSha256": hashlib.sha256(Path(image.value).read_bytes()).hexdigest(),
        "argv": sys.argv[1:],
        "commandLine": kernel.GetCommandLineW(),
    }


def main():
    if len(sys.argv) < 4:
        raise RuntimeError("Windows fixture arguments required")
    mode, state_path, nonce = sys.argv[1:4]
    if mode not in ("success", "nonzero", "sleep") or not nonce:
        raise ValueError("invalid CODE fixture mode or nonce")
    record = observe_process(nonce)
    encoded = json.dumps(record, ensure_ascii=True, separators=(",", ":")) + "\n"
    with open(state_path, "x", encoding="ascii", newline="\n") as state:
        state.write(encoded)
    sys.stdout.buffer.write(encoded.encode("ascii"))
    sys.stdout.buffer.flush()
    if mode == "sleep":
        time.sleep(30)
    else:
        sys.stderr.buffer.write(b"CODE_ONLY_STDERR\n")
        sys.stderr.buffer.flush()
    return 7 if mode == "nonzero" else 0


if __name__ == "__main__":
    raise SystemExit(main())
