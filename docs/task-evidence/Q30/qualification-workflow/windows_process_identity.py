"""Shared Windows process-instance capture and fail-closed comparison.

Identity records use PID, FILETIME ticks, an explicit birth precision, and an
executable path. ``NATIVE_100NS`` is the exact 100 ns value returned by
GetProcessTimes. ``CIM_MICROSECOND`` is a CIM/CreationDate value converted to
FILETIME ticks and therefore divisible by 10. A CIM value identifies a 10-tick
interval; a native value outside that interval proves different births, while
overlap remains unresolved. The comparison never invents sub-microsecond
precision for CIM. A proven birth mismatch establishes that the recorded
instance is gone, even when the current image path cannot be queried. A birth
match alone is insufficient: both known executable paths must match.

``query_process`` retains one native process handle while reading its birth and
image. It reports a confirmed missing PID separately from an OS/query failure.
``compare_identity`` accepts an offline record with the four identity keys and
a query envelope returned by ``query_process`` (or an equivalent CIM adapter):
``{"pid": N, "queryStatus": "PRESENT", "identity": {...}}``. A confirmed
absence must be bound to the queried PID. It never terminates a process.
"""
from __future__ import annotations

import ctypes
from ctypes import wintypes
import ntpath
import os
import unicodedata


NATIVE_100NS = "NATIVE_100NS"
CIM_MICROSECOND = "CIM_MICROSECOND"
PRECISIONS = frozenset((NATIVE_100NS, CIM_MICROSECOND))

QUERY_PRESENT = "PRESENT"
QUERY_ABSENT = "ABSENT"
QUERY_FAILED = "FAILED"

MATCH = "MATCH"
ABSENT = "ABSENT"
UNRESOLVED = "UNRESOLVED"

PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
SYNCHRONIZE = 0x00100000
ERROR_INVALID_PARAMETER = 87
FILETIME_MAX = (1 << 64) - 1
PID_MAX = (1 << 32) - 1
WAIT_OBJECT_0 = 0
WAIT_TIMEOUT = 258
WAIT_FAILED = 0xFFFFFFFF


def _result(status: str, query_status: str, reason: str) -> dict:
    return {"status": status, "queryStatus": query_status, "reason": reason}


def _valid_pid(value) -> bool:
    return type(value) is int and 0 < value <= PID_MAX


def _birth(record):
    """Return (ticks, precision), or None when the birth is missing/ambiguous."""
    if not isinstance(record, dict):
        return None
    ticks, precision = record.get("creationFileTimeTicks"), record.get("birthPrecision")
    if (type(ticks) is not int or not 0 < ticks <= FILETIME_MAX or
            type(precision) is not str or precision not in PRECISIONS):
        return None
    if precision == CIM_MICROSECOND and ticks % 10:
        return None
    return ticks, precision


def _path(value):
    """Canonicalize a known absolute Windows image path, or return None."""
    if not isinstance(value, str) or not value.strip():
        return None
    if any(unicodedata.category(char) == "Cc" for char in value):
        return None
    value = value.strip()
    if not ntpath.isabs(value):
        return None
    drive, tail = ntpath.splitdrive(value)
    # ntpath considers ``\name`` absolute, but it is relative to the current
    # drive on Windows. Require an explicit drive or a complete UNC share.
    if not drive or not tail or tail[0] not in "\\/":
        return None
    return ntpath.normcase(ntpath.normpath(value))


def _birth_relation(left, right):
    """Return True for equal, False for different, and None for cross-precision overlap."""
    lticks, lprecision = left
    rticks, rprecision = right
    if lprecision == rprecision:
        return lticks == rticks
    # CIM's microsecond timestamp denotes [ticks, ticks + 9] in 100 ns units.
    native, cim = (lticks, rticks) if lprecision == NATIVE_100NS else (rticks, lticks)
    if cim <= native <= cim + 9:
        return None
    return False


def compare_identity(recorded: dict, query: dict) -> dict:
    """Classify one recorded process against a PID query without guessing.

    Results have ``status`` (MATCH, ABSENT, or UNRESOLVED), ``queryStatus``
    (PRESENT, ABSENT, FAILED, or UNKNOWN), and a stable reason code. Malformed
    or incomplete records are UNRESOLVED. A birth mismatch is sufficient for
    ABSENT; matching births require matching known executable paths.
    """
    recorded_pid = recorded.get("pid") if isinstance(recorded, dict) else None
    query_pid = query.get("pid") if isinstance(query, dict) else None
    raw_query_status = query.get("queryStatus") if isinstance(query, dict) else None
    query_status = (raw_query_status if isinstance(raw_query_status, str) and raw_query_status in {
        QUERY_PRESENT, QUERY_ABSENT, QUERY_FAILED
    } else "UNKNOWN")

    if not _valid_pid(recorded_pid):
        return _result(UNRESOLVED, query_status, "recorded-pid-missing-or-invalid")
    if not _valid_pid(query_pid) or query_pid != recorded_pid:
        return _result(UNRESOLVED, query_status, "query-pid-missing-or-mismatched")
    recorded_birth = _birth(recorded)
    if recorded_birth is None:
        return _result(UNRESOLVED, query_status, "recorded-birth-missing-or-ambiguous")
    if query_status == QUERY_ABSENT:
        return _result(ABSENT, query_status, "pid-query-confirmed-absent")
    if query_status == QUERY_FAILED:
        return _result(UNRESOLVED, query_status, "pid-query-failed")
    if query_status != QUERY_PRESENT:
        return _result(UNRESOLVED, query_status, "pid-query-state-unknown")

    live = query.get("identity") if isinstance(query, dict) else None
    if not isinstance(live, dict):
        return _result(UNRESOLVED, query_status, "live-identity-unavailable")
    if live.get("pid") != recorded_pid or not _valid_pid(live.get("pid")):
        return _result(UNRESOLVED, query_status, "live-pid-missing-or-mismatched")
    live_birth = _birth(live)
    if live_birth is None:
        return _result(UNRESOLVED, query_status, "live-birth-missing-or-ambiguous")
    relation = _birth_relation(recorded_birth, live_birth)
    if relation is False:
        return _result(ABSENT, query_status, "precise-birth-differs")
    if relation is None:
        return _result(UNRESOLVED, query_status, "cross-precision-birth-overlap")

    recorded_path, live_path = _path(recorded.get("executable")), _path(live.get("executable"))
    if recorded_path is None or live_path is None:
        return _result(UNRESOLVED, query_status, "matching-birth-image-missing-or-ambiguous")
    if recorded_path != live_path:
        return _result(UNRESOLVED, query_status, "matching-birth-image-differs")
    return _result(MATCH, query_status, "birth-and-image-match")


class _FILETIME(ctypes.Structure):
    _fields_ = [("low", wintypes.DWORD), ("high", wintypes.DWORD)]


class ProcessIdentityError(RuntimeError):
    """A required field could not be captured from the supplied process handle."""

    def __init__(self, detail: dict):
        super().__init__(f"{detail.get('operation')}: {detail.get('message')}")
        self.detail = detail


def _win_error(operation: str, code: int) -> dict:
    try:
        message = ctypes.FormatError(code).strip()
    except (AttributeError, OSError):  # pragma: no cover - unusual Python/Windows build
        message = "Windows error"
    return {"operation": operation, "winerror": int(code), "message": message}


def _api():
    if os.name != "nt":
        raise OSError("native process identity capture requires Windows")
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.OpenProcess.restype = wintypes.HANDLE
    kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel32.GetProcessTimes.restype = wintypes.BOOL
    kernel32.GetProcessTimes.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(_FILETIME), ctypes.POINTER(_FILETIME),
        ctypes.POINTER(_FILETIME), ctypes.POINTER(_FILETIME),
    ]
    kernel32.QueryFullProcessImageNameW.restype = wintypes.BOOL
    kernel32.QueryFullProcessImageNameW.argtypes = [
        wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD),
    ]
    kernel32.CloseHandle.restype = wintypes.BOOL
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.WaitForSingleObject.restype = wintypes.DWORD
    kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel32.GetProcessId.restype = wintypes.DWORD
    kernel32.GetProcessId.argtypes = [wintypes.HANDLE]
    return kernel32


def _birth_from_handle(kernel32, handle):
    created, exited, kernel, user = (_FILETIME() for _ in range(4))
    ctypes.set_last_error(0)
    if not kernel32.GetProcessTimes(handle, ctypes.byref(created), ctypes.byref(exited),
                                   ctypes.byref(kernel), ctypes.byref(user)):
        raise ProcessIdentityError(_win_error("GetProcessTimes", ctypes.get_last_error()))
    return (int(created.high) << 32) | int(created.low)


def _image_from_handle(kernel32, handle):
    image, length = ctypes.create_unicode_buffer(32768), wintypes.DWORD(32768)
    ctypes.set_last_error(0)
    if not kernel32.QueryFullProcessImageNameW(handle, 0, image, ctypes.byref(length)):
        raise ProcessIdentityError(_win_error("QueryFullProcessImageNameW", ctypes.get_last_error()))
    value = image.value[:length.value]
    if _path(value) is None:
        raise ProcessIdentityError({"operation": "QueryFullProcessImageNameW",
                                    "message": "returned a non-absolute image path"})
    return value


def identity_from_handle(handle, pid=None) -> dict:
    """Read a complete native identity from a caller-owned retained handle.

    The caller retains ownership and must close the handle. If ``pid`` is not
    supplied, it is obtained from this same handle. Missing required birth or
    image data raises ``ProcessIdentityError`` so launch code can fail closed.
    """
    kernel32 = _api()
    ctypes.set_last_error(0)
    native_pid = int(kernel32.GetProcessId(handle))
    if not native_pid:
        raise ProcessIdentityError(_win_error("GetProcessId", ctypes.get_last_error()))
    if pid is not None and (not _valid_pid(pid) or pid != native_pid):
        raise ValueError("supplied pid differs from the retained process handle")
    pid = native_pid
    return {"pid": pid, "creationFileTimeTicks": _birth_from_handle(kernel32, handle),
            "birthPrecision": NATIVE_100NS, "executable": _image_from_handle(kernel32, handle)}


def query_process(pid: int) -> dict:
    """Capture a PID's identity from one retained handle; never terminate it.

    A missing PID is reported as ``queryStatus == ABSENT`` for Windows'
    ERROR_INVALID_PARAMETER from OpenProcess or a signaled process handle.
    Access denial and query failures remain distinct. An active retained handle
    establishes presence even when one identity field is unavailable.
    """
    if not _valid_pid(pid):
        raise ValueError("pid must be a positive integer")
    try:
        kernel32 = _api()
    except OSError as exc:
        return {"pid": pid, "queryStatus": QUERY_FAILED, "identity": None,
                "queryError": {"operation": "OpenProcess", "message": str(exc)}}

    ctypes.set_last_error(0)
    handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE, False, pid)
    if not handle:
        code = ctypes.get_last_error()
        return {"pid": pid,
                "queryStatus": QUERY_ABSENT if code == ERROR_INVALID_PARAMETER else QUERY_FAILED,
                "identity": None,
                "queryError": _win_error("OpenProcess", code)}

    identity = {"pid": pid, "creationFileTimeTicks": None,
                "birthPrecision": NATIVE_100NS, "executable": None}
    errors = []
    query_status = QUERY_FAILED
    try:
        try:
            identity["creationFileTimeTicks"] = _birth_from_handle(kernel32, handle)
        except ProcessIdentityError as exc:
            errors.append(exc.detail)
        try:
            identity["executable"] = _image_from_handle(kernel32, handle)
        except ProcessIdentityError as exc:
            errors.append(exc.detail)

        ctypes.set_last_error(0)
        state = kernel32.WaitForSingleObject(handle, 0)
        if state == WAIT_OBJECT_0:
            query_status = QUERY_ABSENT
        elif state == WAIT_TIMEOUT:
            query_status = QUERY_PRESENT
        elif state == WAIT_FAILED:
            errors.append(_win_error("WaitForSingleObject", ctypes.get_last_error()))
        else:
            errors.append({"operation": "WaitForSingleObject", "waitStatus": int(state)})
    finally:
        ctypes.set_last_error(0)
        closed = bool(kernel32.CloseHandle(handle))
        close_error = None if closed else _win_error("CloseHandle", ctypes.get_last_error())

    result = {"pid": pid, "queryStatus": query_status, "identity": identity,
              "metadataErrors": errors}
    if close_error is not None:
        result["handleCloseError"] = close_error
        result["queryStatus"] = QUERY_FAILED
    return result
