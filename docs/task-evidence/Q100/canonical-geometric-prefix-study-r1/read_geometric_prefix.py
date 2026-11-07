"""Independent strict consumer for the bounded BASE geometric-reason study."""
from __future__ import annotations

import sys

if not sys.flags.isolated or not sys.dont_write_bytecode:
    sys.stderr.buffer.write(b"P8_PREFIX_READER_REJECT ISOLATION_REQUIRED\n")
    raise SystemExit(2)

import ctypes
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import time
import types
from typing import Any

SCHEMA = "tsj-q100-geometric-prefix-reader-v1"
INPUT_SCHEMA = "tsj-q100-geometric-prefix-study-inputs-v1"
HOST_SCHEMA = "tsj-q100-geometric-prefix-host-v1"
INDEX_SCHEMA = "tsj-q100-geometric-prefix-suite-index-v1"
FOOTER_SCHEMA = "tsj-q100-geometric-prefix-suite-footer-v1"
PARENT_SCHEMA = "tsj-q100-geometric-prefix-parent-v1"
SOURCE_SCHEMA = "tsj-q100-geometric-prefix-source-v1"
PARENT_SOURCE = "0891474cadc4b9e00d7911e81c124721a11efb87"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
CANARY_PINS_SHA = "11b3fa459e9bf6950c7ee3d3e804a3b37638bb23cc6c322aaa8ef94549462a5a"
CANARY_QUAL_SHA = "755f921b068f325c9e81315b88f38c4bc8b7196dd61d65f8c8ecf61e26e43276"
P6_PINS_SHA = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_READER_SHA = "1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e"
P5_READER_SHA = "c20bfc3daaa7fb8f7c428b47f29288cc6889c814f488c7caf1128023c214032b"
P4_READER_SHA = "74b05764a1b1cea405b92f50b6ca781ef44a56c0e61680f6abd399701e7be480"
P8_WIRE_SHA = "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb"
GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a"
PREFIX_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
SELECTED = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 15, 17, 20]
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
HEX_RE = re.compile(r"[0-9a-f]{64}\Z")
MAX_LINE = 65536
MAX_FILE = 100 * 1024 * 1024
MAX_INVENTORY = 1024 * 1024
MAX_PINS = 4 * 1024 * 1024
OP_BYTES = 8 * 1024 * 1024
HOST_BYTES = 8 * 1024 * 1024
SUITE_BYTES = 40 * 1024 * 1024
MAX_WORK_NS = 90_000_000_000
MAX_GROUP_NS = 5_000_000_000
MAX_GROUPS = 640
MAX_UNITS = 32768
CLEANUP_MS = 120000
SOURCE_FIELDS = {"role", "path", "bytes", "sha256"}
FILE_FIELDS = {"path", "bytes", "sha256"}
REASON_SITES = {
    "START_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION", "DOMAIN_UNKNOWN_NET",
    "DOMAIN_CORRIDOR_OVERLAP", "DOMAIN_MEMBERSHIP_SIDE", "EMPTY_FACE_RESERVATION",
    "ADJACENT_RESERVATION", "PAD_ESCAPE_INTERSECTION", "PROFILED_PAD_TOUCH",
    "COMPONENT_COURTYARD",
}
REASON_FIELDS = {
    "site", "ownerKind", "stage", "startPadId", "endpointPadId",
    "fromGridX", "fromGridY", "toGridX", "toGridY", "ownerId", "ownerNet",
    "ownerIndex", "reservationState", "reservationValue", "domainLow", "domainHigh",
    "domainHorizontal", "domainLowLimit", "domainHighLimit", "domainFirst", "domainLast",
    "startEscape", "endEscape", "underpassEnabled",
}
IDENTITY_FIELDS = {
    "layer", "fromState", "toState", "bit", "selected", "fromGridX", "fromGridY",
    "toGridX", "toGridY", "fromX", "fromY", "toX", "toY", "direction", "netId",
    "startPadId", "rootPadId", "reachedPthPadIds",
}
CLEANUP_FIELDS = {
    "sourcePreserved", "observersDetached", "referencesDropped", "ownersReleased",
    "contextRestored", "liveToPristineBeforeEqual", "queryToPristineBeforeEqual",
    "liveToPristineAfterEqual", "queryToPristineAfterEqual", "queryEffectsBeforeSha256",
    "queryEffectsAfterSha256", "sourceEffectsBeforeSha256", "sourceEffectsAfterSha256",
    "layoutGeometryBeforeSha256", "layoutGeometryAfterSha256",
}
WORK_FIELDS = {
    "copyEntries", "comparisonEntries", "copyUnits", "comparisonUnits", "queryUnits",
    "metadataUnits", "copyNanos", "comparisonNanos", "queryNanos", "metadataNanos",
    "maximumUnitNanos", "groupCount",
}
FOOTER_FIELDS = {
    "kind", "status", "complete", "runId", "inputSha256", "sourceSha256", "mode",
    "parentPrefixSha256", "parentGraphSha256", "selectedQueryCount", "queryCount",
    "returnedQueryCount", "fineEventCount", "coarseEventCount", "priorObservationUnits",
    "p8ObservationUnits", "totalObservationUnits", "copyEntries", "comparisonEntries",
    "copyUnits", "comparisonUnits", "queryUnits", "metadataUnits", "setupUnits",
    "exportUnits", "copyNanos", "comparisonNanos", "queryNanos", "metadataNanos",
    "setupNanos", "exportNanos", "workNanos", "maximumUnitNanos", "groupCount",
    "outsideQueryWork", "cleanupKind", "cleanupComplete", "sourcePreserved",
    "observersDetached", "referencesDropped", "ownersReleased", "contextRestored",
}
FOOTER_OUTSIDE_FIELDS = {
    "copyEntries", "comparisonEntries", "copyUnits", "comparisonUnits", "queryUnits",
    "metadataUnits", "setupUnits", "exportUnits", "copyNanos", "comparisonNanos",
    "queryNanos", "metadataNanos", "setupNanos", "exportNanos",
    "maximumUnitNanos", "groupCount",
}
REASON_INDEX_FIELDS = {
    "schema", "mode", "runId", "inputSha256", "sourceSha256", "wireSha256",
    "parentPrefixSha256", "parentGraphSha256", "selectedQueryOrdinals",
    "parentStageIndex", "reasonSidecar", "queryCount", "returnedQueryCount",
    "fineEventCount", "coarseEventCount", "aggregateBytes", "priorObservationUnits",
    "p8ObservationUnits", "totalObservationUnits", "cleanupKind", "cleanupComplete",
}
COMPLETE_MARKER_FIELDS = {
    "schema", "mode", "runId", "inputSha256", "sourceSha256", "wireSha256",
    "index", "aggregateBytes", "workNanos",
}
EXCEPTION_MARKER_FIELDS = {
    "schema", "runId", "mode", "inputSha256", "sourceSha256", "wireSha256",
    "queryOrdinal", "parentOrdinal", "sentinel", "exceptionClass", "exceptionMessage",
    "suppressedCount", "cleanupKind", "workNanos", "cleanupComplete", "sourcePreserved",
    "observersDetached", "referencesDropped", "ownersReleased", "contextRestored",
    "partialSidecar", "partialOperationReceipt", "parentBlockers", "parentStages",
}
READER_RESULT_FIELDS = {
    "schema", "status", "layoutQualification", "inputSha256", "sourceSha256",
    "sourceParent", "mode", "sourceClosure", "processes", "operations", "parity",
    "reasonCounts", "reasonRows", "cleanup", "outputClosure", "limits",
}
CANARY_MODE_ORDER = ["P6_CANARY", "P8_LITERAL_CANARY", "P8_BAD_MODE",
                     "P8_BAD_UUID", "P8_BAD_INPUT_SHA", "P8_NONEMPTY_DATA"]
SUITE_MODE_ORDER = CANARY_MODE_ORDER + ["NULL", "OBSERVE", "CALLBACK_THROW", "REASON_THROW"]
PROCESS_ROLES = ["preflight", "compiler", "preNative", "p6Canary", "p8Canary",
                 "badMode", "badUuid", "badInputSha", "nonemptyData"]
PROCESS_FILES = {
    "preflight": ("source-preflight-process.json", "source-preflight.stdout", "source-preflight.stderr", 60000),
    "compiler": ("compiler-process.json", "compiler.stdout", "compiler.stderr", 60000),
    "preNative": ("source-prenative-process.json", "source-prenative.stdout", "source-prenative.stderr", 60000),
    "p6Canary": ("p6-canary-process.json", "p6-canary.stdout", "p6-canary.stderr", 480000),
    "p8Canary": ("p8-canary-process.json", "p8-canary.stdout", "p8-canary.stderr", 480000),
    "badMode": ("p8-bad-mode-process.json", "p8-bad-mode.stdout", "p8-bad-mode.stderr", 480000),
    "badUuid": ("p8-bad-uuid-process.json", "p8-bad-uuid.stdout", "p8-bad-uuid.stderr", 480000),
    "badInputSha": ("p8-bad-input-sha-process.json", "p8-bad-input-sha.stdout", "p8-bad-input-sha.stderr", 480000),
    "nonemptyData": ("p8-nonempty-data-process.json", "p8-nonempty-data.stdout", "p8-nonempty-data.stderr", 480000),
    "postSource": ("source-post-process.json", "source-post.stdout", "source-post.stderr", 60000),
    "nullMode": ("runs/NULL/process.json", "runs/NULL.stdout", "runs/NULL.stderr", 480000),
    "observeMode": ("runs/OBSERVE/process.json", "runs/OBSERVE.stdout", "runs/OBSERVE.stderr", 480000),
    "callbackThrow": ("runs/CALLBACK_THROW/process.json", "runs/CALLBACK_THROW.stdout", "runs/CALLBACK_THROW.stderr", 480000),
    "reasonThrow": ("runs/REASON_THROW/process.json", "runs/REASON_THROW.stdout", "runs/REASON_THROW.stderr", 480000),
}
PROCESS_KEYS_CANARY = PROCESS_ROLES[:5] + PROCESS_ROLES[5:] + ["postSource"]
PROCESS_KEYS_SUITE = PROCESS_KEYS_CANARY[:-1] + ["nullMode", "observeMode", "callbackThrow", "reasonThrow", "postSource"]
NATIVE_FILES = {
    "NULL": {"op-1.jsonl", "fixed-prefix.jsonl", "prefix-blockers.json", "prefix-stages.json",
             "canonical-stage-index.json", "prefix-geometric-reasons.jsonl", "canonical-geometric-prefix-index.json"},
    "OBSERVE": {"op-1.jsonl", "fixed-prefix.jsonl", "prefix-blockers.json", "prefix-stages.json",
                "canonical-stage-index.json", "prefix-geometric-reasons.jsonl", "canonical-geometric-prefix-index.json"},
    "CALLBACK_THROW": {"op-1.jsonl"},
    "REASON_THROW": {"op-1.jsonl", "prefix-blockers.json", "prefix-stages.json", "prefix-geometric-reasons.jsonl"},
}
BASE_HOST_FILES = {
    "host-launch-intent.json", "scratch-intent.json",
    "source-preflight-process.json", "source-preflight.stdout", "source-preflight.stderr",
    "compiler-launch-intent.json", "compiler-process.json", "compiler.stdout", "compiler.stderr", "compiler-sources.txt",
    "bridge-copies.json", "source-prenative-process.json", "source-prenative.stdout", "source-prenative.stderr",
    "p6-canary-launch-intent.json", "p6-canary-process.json", "p6-canary.stdout", "p6-canary.stderr",
    "p8-canary-launch-intent.json", "p8-canary-process.json", "p8-canary.stdout", "p8-canary.stderr",
    "p8-bad-mode-launch-intent.json", "p8-bad-mode-process.json", "p8-bad-mode.stdout", "p8-bad-mode.stderr",
    "p8-bad-uuid-launch-intent.json", "p8-bad-uuid-process.json", "p8-bad-uuid.stdout", "p8-bad-uuid.stderr",
    "p8-bad-input-sha-launch-intent.json", "p8-bad-input-sha-process.json", "p8-bad-input-sha.stdout", "p8-bad-input-sha.stderr",
    "p8-nonempty-data-launch-intent.json", "p8-nonempty-data-process.json", "p8-nonempty-data.stdout", "p8-nonempty-data.stderr",
    "source-post-process.json", "source-post.stdout", "source-post.stderr", "suite-index.json", "suite-footer.json",
    "host-result.json",
}
BASE_DIRS = {
    "controls", "controls/P8_BAD_MODE", "controls/P8_BAD_MODE/data",
    "controls/P8_BAD_UUID", "controls/P8_BAD_UUID/data",
    "controls/P8_BAD_INPUT_SHA", "controls/P8_BAD_INPUT_SHA/data",
    "controls/P8_NONEMPTY_DATA", "controls/P8_NONEMPTY_DATA/data",
}
HOST_LIMITS = {
    "compileMs":60000,"nativeMs":480000,"auditMs":60000,"readerMs":180000,
    "cleanupMs":120000,"outerMs":1800000,"workNanos":90000000000,
    "groups":640,"groupNanos":5000000000,"observationUnits":32768,
    "operationBytes":8388608,"hostEvidenceBytes":8388608,"suiteBytes":41943040,
    "lineBytes":65536,"compilerListBytes":1048576,"inventoryBytes":1048576,
}

class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code

def need(ok: Any, code: str) -> None:
    if not ok:
        raise Reject(code)

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def _pairs(rows):
    result = {}
    for key, value in rows:
        need(key not in result, "JSON_DUPLICATE_KEY")
        result[key] = value
    return result

def _constant(_text):
    raise Reject("JSON_NONFINITE")

def _parse_int(text: str) -> int:
    need(text != "-0", "JSON_NEGATIVE_ZERO")
    try:
        value = int(text)
    except ValueError:
        raise Reject("JSON_INTEGER") from None
    need(-(2**63) <= value <= 2**63 - 1, "JSON_INTEGER_RANGE")
    return value

def _parse_float(text: str) -> float:
    try:
        value = float(text)
    except ValueError:
        raise Reject("JSON_NUMBER") from None
    need(math.isfinite(value) and text not in ("-0.0", "-0e0", "-0E0"),
         "JSON_NUMBER")
    return value

def parse_json(raw: bytes, code: str = "JSON_INVALID") -> Any:
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_ENCODING")
    try:
        return json.loads(raw.decode("utf-8", "strict"), object_pairs_hook=_pairs,
                          parse_constant=_constant, parse_int=_parse_int,
                          parse_float=_parse_float)
    except Reject:
        raise
    except (UnicodeError, json.JSONDecodeError, RecursionError, ValueError):
        raise Reject(code) from None

def exact_obj(value: Any, keys: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == keys, code)
    return value

def exact_int(value: Any, low: int = 0, high: int = 2**63 - 1, code: str = "INTEGER") -> int:
    need(type(value) is int and low <= value <= high, code)
    return value

def exact_selected_ordinals(value: Any, code: str) -> list[int]:
    need(type(value) is list and len(value) == len(SELECTED) and
         all(type(item) is int for item in value) and value == SELECTED, code)
    return value

def finite_num(value: Any, low: float = 0.0, high: float = float("inf"), code: str = "NUMBER") -> float:
    need(type(value) in (int, float) and math.isfinite(value) and low <= value <= high, code)
    return float(value)

def digest(value: Any, code: str) -> str:
    need(type(value) is str and HEX_RE.fullmatch(value) is not None, code)
    return value

def normpath(path: Any) -> str:
    return os.path.normcase(os.path.normpath(os.path.abspath(os.fspath(path))))

def same_path(a: Any, b: Any) -> bool:
    return normpath(a) == normpath(b)

def below(path: Any, parent: Any) -> bool:
    try:
        return os.path.commonpath((normpath(path), normpath(parent))) == normpath(parent)
    except ValueError:
        return False

def reparse(st: os.stat_result) -> bool:
    return stat.S_ISLNK(st.st_mode) or bool(getattr(st, "st_file_attributes", 0) & 0x400)

def no_reparse(path: Path, exists: bool = True) -> None:
    path = Path(os.path.abspath(os.fspath(path)))
    for item in (path, *path.parents):
        try:
            st = item.lstat()
        except FileNotFoundError:
            need(not exists and item == path, "PATH_MISSING")
            continue
        except OSError:
            raise Reject("PATH_READ") from None
        need(not reparse(st), "PATH_REPARSE")

def canonical(path_text: Any, code: str, kind: str | None = None, exists: bool = True) -> Path:
    need(type(path_text) is str and path_text, code)
    path = Path(path_text)
    need(path.is_absolute() and str(path) == os.path.abspath(str(path)), code)
    no_reparse(path, exists)
    if exists:
        try:
            resolved = path.resolve(strict=True)
        except OSError:
            raise Reject(code) from None
        need(str(resolved) == str(path), code)
        if kind == "file":
            need(path.is_file(), code)
        elif kind == "dir":
            need(path.is_dir(), code)
    return path

def _identity(st):
    return st.st_dev, st.st_ino, st.st_size, st.st_mtime_ns, st.st_nlink

def stable_read(path_value: Any, maximum: int, code: str = "FILE_READ",
                allow_empty: bool = False) -> bytes:
    path = canonical(os.fspath(path_value), code, "file")
    try:
        p0 = path.stat()
        need(stat.S_ISREG(p0.st_mode) and 0 <= p0.st_size <= maximum, "FILE_SIZE")
        with path.open("rb") as stream:
            h0 = os.fstat(stream.fileno())
            raw = stream.read(maximum + 1)
            h1 = os.fstat(stream.fileno())
        p1 = path.stat()
    except Reject:
        raise
    except OSError:
        raise Reject(code) from None
    need(_identity(p0) == _identity(h0) == _identity(h1) == _identity(p1)
         and p0.st_ctime_ns == p1.st_ctime_ns
         and h0.st_ctime_ns == h1.st_ctime_ns
         and stat.S_ISREG(h0.st_mode) and stat.S_ISREG(h1.st_mode)
         and len(raw) == p1.st_size <= maximum
         and (allow_empty or bool(raw)), "FILE_CHANGED")
    return raw

def stable_hash(path: Path, row: dict, maximum: int = MAX_FILE, code: str = "FILE_HASH") -> bytes:
    raw = stable_read(path, maximum, code, allow_empty=True)
    need(type(row) is dict and type(row.get("bytes")) is int
         and len(raw) == row["bytes"] and row.get("sha256") == sha(raw), code)
    return raw

def strict_line(raw: bytes, code: str, maximum: int = MAX_LINE) -> Any:
    need(type(raw) is bytes and raw.endswith(b"\n") and raw.count(b"\n") == 1
         and b"\r" not in raw and len(raw) <= maximum, code)
    return parse_json(raw[:-1], code)

def parse_document(raw: bytes, code: str, maximum: int = MAX_LINE) -> Any:
    need(type(raw) is bytes and 0 < len(raw) <= maximum and
         not raw.startswith(b"\xef\xbb\xbf"), code+"_FRAMING")
    view=raw
    if view.endswith(b"\r\n"): view=view[:-2]
    elif view.endswith(b"\n"): view=view[:-1]
    need(b"\n" not in view and b"\r" not in view, code+"_FRAMING")
    return parse_json(view,code)

def exact_ref(value: Any, path: str, raw: bytes, code: str) -> None:
    exact_obj(value, {"path", "bytes", "sha256"}, code)
    need(value["path"] == path and type(value["bytes"]) is int
         and value["bytes"] == len(raw) and value["sha256"] == sha(raw), code)

def module_from_bytes(raw: bytes, path: Path, name: str) -> Any:
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[name] = module
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except BaseException:
        sys.modules.pop(name, None)
        raise Reject("AUTHENTICATED_MODULE_EXEC") from None
    return module

def actual_image() -> Path:
    need(os.name == "nt", "PROCESS_IMAGE_UNAVAILABLE")
    cap = 32768
    buffer = ctypes.create_unicode_buffer(cap)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    fn = kernel.GetModuleFileNameW
    fn.argtypes = (ctypes.c_void_p, ctypes.c_wchar_p, ctypes.c_uint32)
    fn.restype = ctypes.c_uint32
    n = fn(None, buffer, cap)
    need(n > 0 and n < cap, "PROCESS_IMAGE_UNAVAILABLE")
    return canonical(buffer.value, "PROCESS_IMAGE_PATH", "file")

def process_root_paths(proc: dict, code: str) -> bool:
    for key in ("FilePath", "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot"):
        need(type(proc.get(key)) is str, code + "_FIELDS")
    root = Path(proc["ProcessRoot"])
    temp = Path(os.path.abspath(tempfile.gettempdir()))
    expected_parent = temp / "TroubleshootJS"
    need(root.is_absolute() and same_path(root.parent, expected_parent)
         and re.fullmatch(r"verify-process-[0-9a-f]{32}", root.name) is not None,
         code + "_ROOT")
    out, err = root / "stdout.log", root / "stderr.log"
    need(same_path(proc["StdoutLog"], out) and same_path(proc["StderrLog"], err),
         code + "_LOG_PATH")
    no_reparse(root, exists=False)
    need(not os.path.lexists(root) and not os.path.lexists(out) and not os.path.lexists(err),
         code + "_ROOT_REMAINS")
    return True

def validate_process(raw: bytes, name: str, exe: Path, args: list[str],
                     exit_code: int = 0) -> dict:
    proc = parse_json(raw, name + "_PROCESS")
    fields = {"FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
              "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
              "TerminationProven"}
    exact_obj(proc, fields, name + "_PROCESS_FIELDS")
    need(proc["FilePath"] == str(exe), name + "_PROCESS_EXE")
    need(type(proc["Arguments"]) is list and proc["Arguments"] == args, name + "_PROCESS_ARGS")
    exact_int(proc["ProcessId"], 1, 2147483647, name + "_PROCESS_PID")
    exact_int(proc["ProcessStartTicks"], 1, 3155378975999999999, name + "_PROCESS_TICKS")
    need(type(proc["ExitCode"]) is int and
         ((proc["ExitCode"] == exit_code) if exit_code is not None else proc["ExitCode"] != 0),
         name + "_PROCESS_EXIT")
    need(type(proc["TerminationProven"]) is bool and proc["TerminationProven"],
         name + "_PROCESS_TERMINATION")
    need(type(proc["Stdout"]) is str and type(proc["Stderr"]) is str, name + "_PROCESS_STREAMS")
    process_root_paths(proc, name)
    return proc

def read_rows(raw: bytes, code: str) -> list[dict]:
    need(raw.endswith(b"\n") and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"),
         code + "_FRAMING")
    lines = raw.splitlines(keepends=True)
    need(bool(lines) and all(0 < len(line) <= MAX_LINE for line in lines), code + "_LINE_LIMIT")
    rows = [parse_json(line[:-1], code + "_JSON") for line in lines]
    need(all(type(row) is dict for row in rows), code + "_ROW_OBJECT")
    return rows

def validate_input_pins(path: Path, expected_sha: str) -> tuple[dict, bytes, dict[str, dict]]:
    raw = stable_read(path, MAX_PINS, "INPUT_PINS")
    need(sha(raw) == expected_sha, "INPUT_PINS_HASH")
    pins = parse_json(raw, "INPUT_PINS_JSON")
    exact_obj(pins, {"schema", "repositoryRoot", "parent", "canaryPrerequisite",
                     "p6Context", "sourceInventory", "roles", "files", "toolPaths"},
              "INPUT_PINS_FIELDS")
    need(pins["schema"] == INPUT_SCHEMA and pins["parent"] == PARENT_SOURCE,
         "INPUT_PINS_IDENTITY")
    pins["repositoryRoot"] = str(canonical(pins["repositoryRoot"], "REPOSITORY_ROOT", "dir"))
    can = exact_obj(pins["canaryPrerequisite"],
        {"root", "pinsPath", "pinsSha256", "qualificationPath", "qualificationSha256"},
        "CANARY_PREREQUISITE_FIELDS")
    for key, kind in (("root", "dir"), ("pinsPath", "file"), ("qualificationPath", "file")):
        can[key] = str(canonical(can[key], "CANARY_PREREQUISITE_PATH", kind))
    need(can["pinsSha256"] == CANARY_PINS_SHA and can["qualificationSha256"] == CANARY_QUAL_SHA,
         "CANARY_PREREQUISITE_IDENTITY")
    p6ctx = exact_obj(pins["p6Context"], {"pinsPath", "pinsSha256"}, "P6_CONTEXT_FIELDS")
    p6ctx["pinsPath"] = str(canonical(p6ctx["pinsPath"], "P6_PINS_PATH", "file"))
    need(p6ctx["pinsSha256"] == P6_PINS_SHA, "P6_CONTEXT_IDENTITY")
    inv = exact_obj(pins["sourceInventory"], {"path", "sha256"}, "SOURCE_INVENTORY_FIELDS")
    inv["path"] = str(canonical(inv["path"], "SOURCE_INVENTORY_PATH", "file"))
    digest(inv["sha256"], "SOURCE_INVENTORY_SHA")
    roles = exact_obj(pins["roles"], {"native", "process", "input"}, "ROLE_GROUPS")
    exact_obj(roles["native"], {"study", "inspector"}, "NATIVE_ROLES")
    exact_obj(roles["process"], {"helper", "reader", "host"}, "PROCESS_ROLES")
    exact_obj(roles["input"], {"maker", "auditor"}, "INPUT_ROLES")
    for group in roles.values():
        for key, value in group.items():
            need(type(value) is str, "ROLE_PATH")
            if group is roles["process"] and key == "helper":
                helper = canonical(value, "ROLE_PATH", "file")
                need(same_path(helper, Path(pins["repositoryRoot"]) / "scripts/VerifierIsolation.psm1"),
                     "PROCESS_HELPER_SOURCE_ROW")
                group[key] = str(helper)
                continue
            group[key] = str(canonical(value, "ROLE_PATH", "file"))
    tools = exact_obj(pins["toolPaths"], {"pythonExe", "javaHome", "powershellExe"}, "TOOL_PATHS")
    for key in ("pythonExe", "powershellExe"):
        tools[key] = str(canonical(tools[key], "TOOL_PATH", "file"))
    tools["javaHome"] = str(canonical(tools["javaHome"], "JAVA_HOME", "dir"))
    files = pins["files"]
    need(type(files) is list and len(files) == 25, "INPUT_FILES_CENSUS")
    rows: dict[str, dict] = {}
    for row in files:
        exact_obj(row, FILE_FIELDS, "INPUT_FILE_ROW")
        p = canonical(row["path"], "INPUT_FILE_PATH", "file")
        need(type(row["bytes"]) is int and 0 < row["bytes"] <= MAX_FILE,
             "INPUT_FILE_BYTES")
        digest(row["sha256"], "INPUT_FILE_SHA")
        key = normpath(p)
        need(key not in rows, "INPUT_FILE_DUPLICATE")
        rows[key] = {**row, "path": str(p)}
    for group in (roles["native"], {k:v for k,v in roles["process"].items() if k != "helper"}, roles["input"]):
        for value in group.values():
            need(normpath(value) in rows, "ROLE_FILE_BINDING")
    need(same_path(roles["process"]["reader"], Path(__file__).resolve()),
         "PROCESS_READER_SOURCE_BINDING")
    for value in (can["pinsPath"], can["qualificationPath"], p6ctx["pinsPath"], inv["path"],
                  tools["pythonExe"], tools["powershellExe"]):
        need(normpath(value) in rows, "PINNED_FILE_BINDING")
    for exe in ("java.exe", "javac.exe"):
        matched = [r for r in rows.values() if Path(r["path"]).name.lower() == exe]
        expected_exe=Path(tools["javaHome"])/"bin"/exe
        need(len(matched) == 1 and same_path(Path(matched[0]["path"]), expected_exe),
             "JDK_FILE_BINDING")
    for row in rows.values():
        stable_hash(Path(row["path"]), row, MAX_FILE, "INPUT_FILE_HASH")
    need(normpath(Path(__file__)) in rows and
         rows[normpath(Path(__file__))]["sha256"] == sha(stable_read(Path(__file__), MAX_FILE)),
         "READER_SELF_PIN")
    return pins, raw, rows

def parse_canary_prerequisite(pins: dict, rows: dict[str,dict]) -> tuple[dict,dict]:
    root = Path(pins["canaryPrerequisite"]["root"])
    pin_path = Path(pins["canaryPrerequisite"]["pinsPath"])
    qual_path = Path(pins["canaryPrerequisite"]["qualificationPath"])
    pin_raw = stable_hash(pin_path, rows[normpath(pin_path)], MAX_PINS, "CANARY_PINS_HASH")
    qual_raw = stable_hash(qual_path, rows[normpath(qual_path)], MAX_PINS, "CANARY_QUALIFICATION_HASH")
    need(sha(pin_raw) == CANARY_PINS_SHA and sha(qual_raw) == CANARY_QUAL_SHA,
         "CANARY_PREREQUISITE_HASH")
    canary = parse_json(pin_raw, "CANARY_PINS_JSON")
    qualification = parse_json(qual_raw, "CANARY_QUALIFICATION_JSON")
    need(type(canary) is dict and canary.get("schema") == "tsj-q100-geometric-canary-inputs-v1"
         and canary.get("repositoryRoot") == pins["repositoryRoot"]
         and canary.get("parent") == PARENT_SOURCE, "CANARY_PINS_IDENTITY")
    source_closure=qualification.get("sourceClosure") if type(qualification) is dict else None
    need(type(qualification) is dict and qualification.get("inputPinsSha256") == CANARY_PINS_SHA
         and type(source_closure) is dict and
         exact_int(source_closure.get("sourceRows"),1084,1084,"CANARY_QUALIFICATION_SOURCE_ROWS")==1084,
         "CANARY_QUALIFICATION_IDENTITY")
    return canary, qualification

def validate_inventory(pins: dict, rows: dict[str,dict], canary: dict) -> tuple[bytes,list[dict]]:
    path = Path(pins["sourceInventory"]["path"])
    raw = stable_hash(path, rows[normpath(path)], MAX_INVENTORY, "SOURCE_INVENTORY_HASH")
    need(sha(raw) == pins["sourceInventory"]["sha256"], "SOURCE_INVENTORY_BINDING")
    parsed = read_rows(raw, "SOURCE_INVENTORY")
    need(len(parsed) == 1086 and
         parsed[0] == {"kind":"header","schema":SOURCE_SCHEMA,"sourceParent":PARENT_SOURCE,
                       "rowCount":1084,"productionCount":850} and
         parsed[-1] == {"kind":"footer","rowCount":1084}, "SOURCE_INVENTORY_HEADER_FOOTER")
    source = []
    for item in parsed[1:-1]:
        need(set(item) == {"kind","path","bytes","sha256","role"} and item["kind"] == "source"
             and type(item["path"]) is str and "\\" not in item["path"]
             and type(item["role"]) is str and type(item["bytes"]) is int
             and 0 <= item["bytes"] <= MAX_FILE, "SOURCE_INVENTORY_ROW")
        digest(item["sha256"], "SOURCE_INVENTORY_ROW_SHA")
        rel = PurePosixPath(item["path"])
        need(not rel.is_absolute() and rel.as_posix() == item["path"]
             and all(part not in ("", ".", "..") for part in rel.parts), "SOURCE_INVENTORY_PATH")
        source.append({k:item[k] for k in ("role","path","bytes","sha256")})
    inherited = canary.get("sourceRows")
    need(type(inherited) is list and len(inherited)==1084,"CANARY_SOURCE_CENSUS")
    inherited_paths=[]
    for row in inherited:
        exact_obj(row,SOURCE_FIELDS,"CANARY_SOURCE_ROW_FIELDS")
        need(type(row["path"]) is str and "\\" not in row["path"] and
             type(row["role"]) is str and type(row["bytes"]) is int and
             0 <= row["bytes"] <= MAX_FILE,"CANARY_SOURCE_ROW")
        digest(row["sha256"],"CANARY_SOURCE_ROW_SHA")
        rel=PurePosixPath(row["path"])
        need(not rel.is_absolute() and rel.as_posix()==row["path"] and
             all(part not in ("", ".", "..") for part in rel.parts),"CANARY_SOURCE_PATH")
        inherited_paths.append(row["path"])
    need(len(set(inherited_paths))==1084,"CANARY_SOURCE_DUPLICATE")
    inherited_by_path=sorted(inherited,key=lambda row:row["path"])
    need([r["path"] for r in source] == sorted({r["path"] for r in source})
          and source == inherited_by_path, "SOURCE_INVENTORY_CANARY_PARITY")
    repo = Path(pins["repositoryRoot"])
    expected_total = sum(row["bytes"] for row in source)
    total = production = 0
    source_map = {}
    for item in source:
        need(item["role"] in ("BASE_SOURCE","HOST","READER","WIRE","WRITER"), "SOURCE_ROW_ROLE")
        path = canonical(str(repo.joinpath(*PurePosixPath(item["path"]).parts)), "SOURCE_PATH", "file")
        current = stable_read(path, MAX_FILE, "SOURCE_FILE", allow_empty=True)
        need(len(current) == item["bytes"] and sha(current) == item["sha256"], "SOURCE_FILE_HASH")
        total += len(current); production += item["path"].startswith("src/")
        need(total <= expected_total, "SOURCE_BYTE_CAP")
        source_map[item["path"]] = item
    need(len(source_map) == 1084 and production == 850 and total == expected_total,
         "SOURCE_CENSUS")
    helper = Path(pins["roles"]["process"]["helper"])
    row = source_map.get("scripts/VerifierIsolation.psm1")
    need(row is not None and same_path(helper, repo / "scripts/VerifierIsolation.psm1"),
         "PROCESS_HELPER_SOURCE_ROW")
    helper_bytes = stable_read(helper, MAX_FILE, "PROCESS_HELPER", allow_empty=True)
    need(len(helper_bytes) == row["bytes"] and sha(helper_bytes) == row["sha256"],
         "PROCESS_HELPER_HASH")
    return raw, source

def validate_reason_event(event: Any, row: dict, op: dict,
                          expected_endpoint: str | None,
                          index_bindings: dict | None = None) -> None:
    exact_obj(event, REASON_FIELDS, "REASON_EVENT_KEYS")
    need(type(event["site"]) is str and event["site"] in REASON_SITES
         and type(event["ownerKind"]) is str, "REASON_EVENT_FIELD_TYPE")
    for key in ("stage","fromGridX","fromGridY","toGridX","toGridY","ownerIndex",
                "domainHorizontal","domainLowLimit","domainHighLimit","domainFirst",
                "domainLast","startEscape","endEscape","underpassEnabled"):
        need(type(event[key]) is int, "REASON_EVENT_FIELD_TYPE")
    for key in ("startPadId","endpointPadId","ownerId","ownerNet","reservationState",
                "reservationValue","domainLow","domainHigh"):
        need(event[key] is None or type(event[key]) is str, "REASON_EVENT_FIELD_TYPE")
    identity = row["identity"]
    need(event["startPadId"] == identity["startPadId"]
         and event["endpointPadId"] == expected_endpoint
         and tuple(event[k] for k in ("fromGridX","fromGridY","toGridX","toGridY")) ==
             tuple(identity[k] for k in ("fromGridX","fromGridY","toGridX","toGridY")),
         "REASON_EVENT_CORRELATION")
    site = event["site"]
    owners = {
        "START_ESCAPE_DIRECTION":("PAD",0), "END_ESCAPE_DIRECTION":("PAD",0),
        "DOMAIN_UNKNOWN_NET":("DOMAIN_POLICY",1),
        "DOMAIN_CORRIDOR_OVERLAP":("DOMAIN_BOUNDARY",1),
        "DOMAIN_MEMBERSHIP_SIDE":("DOMAIN_BOUNDARY",1),
        "EMPTY_FACE_RESERVATION":("CACHED_RESERVATION",1),
        "ADJACENT_RESERVATION":("CACHED_RESERVATION",1),
        "PAD_ESCAPE_INTERSECTION":("PAD",1), "PROFILED_PAD_TOUCH":("PAD",1),
        "COMPONENT_COURTYARD":("COMPONENT",1),
    }
    owner_kind, stage = owners[site]
    need(event["ownerKind"] == owner_kind and event["stage"] == stage,
         "REASON_OWNER_SEMANTICS")
    defaults = {"reservationState":None,"reservationValue":None,"domainLow":None,
        "domainHigh":None,"domainHorizontal":-1,"domainLowLimit":0,"domainHighLimit":0,
        "domainFirst":0,"domainLast":0,"startEscape":-1,"endEscape":-1,
        "underpassEnabled":-1}
    if site in ("START_ESCAPE_DIRECTION","END_ESCAPE_DIRECTION"):
        endpoint = identity["startPadId"] if site.startswith("START") else event["endpointPadId"]
        defaults.update(ownerId=endpoint,ownerNet=None,ownerIndex=-1)
    elif site == "DOMAIN_UNKNOWN_NET":
        defaults.update(ownerId=None,ownerNet=identity["netId"],ownerIndex=-1,
                        domainHorizontal=0)
    elif site in ("DOMAIN_CORRIDOR_OVERLAP","DOMAIN_MEMBERSHIP_SIDE"):
        defaults.update(ownerId=None,ownerNet=None,ownerIndex=event["ownerIndex"])
        need(event["ownerIndex"] >= 0 and type(event["domainLow"]) is str and event["domainLow"]
             and type(event["domainHigh"]) is str and event["domainHigh"]
             and event["domainHorizontal"] in (0,1)
             and event["domainLowLimit"] < event["domainHighLimit"]
             and event["domainFirst"] < event["domainLast"], "REASON_DOMAIN_FIELDS")
        defaults.update(domainLow=event["domainLow"],domainHigh=event["domainHigh"],
            domainHorizontal=event["domainHorizontal"],domainLowLimit=event["domainLowLimit"],
            domainHighLimit=event["domainHighLimit"],domainFirst=event["domainFirst"],
            domainLast=event["domainLast"])
    elif site in ("EMPTY_FACE_RESERVATION","ADJACENT_RESERVATION"):
        defaults.update(ownerId=None,ownerIndex=-1)
        need(event["reservationState"] in ("SINGLE","MIXED")
             and type(event["reservationValue"]) is str, "REASON_RESERVATION_FIELDS")
        if event["reservationState"] == "SINGLE":
            need(bool(event["reservationValue"]) and event["ownerNet"] == event["reservationValue"],
                 "REASON_RESERVATION_SINGLE")
        else:
            need(event["reservationValue"] == "" and event["ownerNet"] is None,
                 "REASON_RESERVATION_MIXED")
        defaults.update(reservationState=event["reservationState"],
            reservationValue=event["reservationValue"],
            ownerNet=event["reservationValue"] if event["reservationState"]=="SINGLE" else None)
    elif site in ("PAD_ESCAPE_INTERSECTION","PROFILED_PAD_TOUCH"):
        pads = {x["padId"]:x for x in op["pads"] if type(x) is dict and type(x.get("padId")) is str}
        idx = event["ownerIndex"]
        need(type(idx) is int and 0 <= idx < len(pads) and event["ownerId"] in pads
             and event["ownerNet"] == pads[event["ownerId"]]["netId"], "REASON_PAD_OWNER")
        if index_bindings is not None:
            forward=index_bindings.setdefault("padIndexToId",{})
            reverse=index_bindings.setdefault("padIdToIndex",{})
            need((idx not in forward or forward[idx] == event["ownerId"])
                 and (event["ownerId"] not in reverse or reverse[event["ownerId"]] == idx),
                 "REASON_PAD_INDEX_CONSISTENCY")
            forward[idx]=event["ownerId"]; reverse[event["ownerId"]]=idx
        # P5's sorted geometry does not expose Router array order. This checks
        # actual ID/net correspondence and an in-range consistent reported index.
        defaults.update(ownerId=event["ownerId"],ownerNet=event["ownerNet"],ownerIndex=idx)
    elif site == "COMPONENT_COURTYARD":
        components = {p.get("componentId") for p in op.get("pads", []) if type(p) is dict}
        components.update(p.get("componentId") for p in op.get("components", []) if type(p) is dict)
        components.discard(None)
        need(0 <= event["ownerIndex"] < len(components) and event["ownerId"] in components
             and event["ownerNet"] is None and event["startEscape"] in (0,1)
             and event["endEscape"] in (0,1) and event["underpassEnabled"] in (0,1),
             "REASON_COMPONENT_OWNER")
        if index_bindings is not None:
            forward=index_bindings.setdefault("componentIndexToId",{})
            reverse=index_bindings.setdefault("componentIdToIndex",{})
            need((event["ownerIndex"] not in forward or forward[event["ownerIndex"]] == event["ownerId"])
                 and (event["ownerId"] not in reverse or reverse[event["ownerId"]] == event["ownerIndex"]),
                 "REASON_COMPONENT_INDEX_CONSISTENCY")
            forward[event["ownerIndex"]]=event["ownerId"]
            reverse[event["ownerId"]]=event["ownerIndex"]
        defaults.update(ownerId=event["ownerId"],ownerNet=None,ownerIndex=event["ownerIndex"],
                        startEscape=event["startEscape"],endEscape=event["endEscape"],
                        underpassEnabled=event["underpassEnabled"])
    for key, value in defaults.items():
        need(event[key] == value, "REASON_EVENT_DEFAULTS")

def _sum_work(rows: list[dict], outside: dict, key: str) -> int:
    return sum(row["work"].get(key, 0) for row in rows) + outside.get(key, 0)

def validate_reason_sidecar(raw: bytes, context: dict) -> dict:
    mode = context["mode"]
    rows = read_rows(raw, "REASON")
    interrupted = mode == "REASON_THROW"
    normal = mode in ("NULL","OBSERVE")
    need((normal and len(rows) == 16) or (interrupted and len(rows) == 3), "REASON_FRAMING")
    header = rows[0]
    exact_obj(header, {"kind","schema","runId","inputSha256","sourceSha256","mode",
        "parentPrefixSha256","parentGraphSha256","selectedQueryOrdinals"}, "REASON_HEADER_FIELDS")
    need(header["kind"] == "header" and header["schema"] == "tsj-q100-geometric-reasons-v1"
         and header["runId"] == context["runId"] and header["inputSha256"] == context["inputSha"]
         and header["sourceSha256"] == context["sourceSha"] and header["mode"] == mode
          and header["parentPrefixSha256"] == PREFIX_SHA and header["parentGraphSha256"] == GRAPH_SHA,
          "REASON_HEADER_BINDING")
    exact_selected_ordinals(header["selectedQueryOrdinals"], "REASON_HEADER_BINDING")
    queries = rows[1:-1]
    expected_n = 1 if interrupted else 14
    need(len(queries) == expected_n, "REASON_QUERY_ORDER")
    p6_queries = context["p6Stage"]["queries"]
    reason_count = coarse_count = 0
    reason_rows = []
    index_bindings = {}
    for idx, q in enumerate(queries):
        exact_obj(q, {"kind","ordinal","parentOrdinal","identity","allowed","fineEvents",
                      "coarseEvents","cleanup","work"}, "REASON_QUERY_FIELDS")
        parent_ordinal = SELECTED[idx]
        need(q["kind"] == "query" and type(q["ordinal"]) is int and q["ordinal"] == idx
             and type(q["parentOrdinal"]) is int and q["parentOrdinal"] == parent_ordinal,
             "REASON_QUERY_ORDER")
        ident = exact_obj(q["identity"], IDENTITY_FIELDS, "REASON_QUERY_IDENTITY")
        for key in ("layer","fromState","toState","bit","fromGridX","fromGridY","toGridX",
                    "toGridY","fromX","fromY","toX","toY","direction"):
            need(type(ident[key]) is int, "REASON_QUERY_IDENTITY")
        need(ident["layer"] in (0,1) and type(ident["selected"]) is bool
             and all(type(ident[k]) is str and ident[k] for k in ("netId","startPadId","rootPadId"))
             and type(ident["reachedPthPadIds"]) is list
             and all(type(x) is str and x for x in ident["reachedPthPadIds"]),
             "REASON_QUERY_IDENTITY")
        stage_query = p6_queries[parent_ordinal]
        cut_query = context["cut"]["cutQueries"][parent_ordinal]
        need(type(stage_query["ordinal"]) is int and stage_query["ordinal"]==parent_ordinal
             and type(cut_query["ordinal"]) is int and cut_query["ordinal"]==parent_ordinal
             and cut_query["ordinal"]==q["parentOrdinal"], "REASON_QUERY_IDENTITY")
        for key in ("layer","fromState","toState","bit"):
            need(type(cut_query[key]) is type(stage_query[key])
                 and cut_query[key]==stage_query[key], "REASON_QUERY_IDENTITY")
        directions=context["p5Directions"]
        need(type(cut_query["direction"]) is str and type(directions) is tuple and len(directions)==4
             and all(type(row) is tuple and len(row)==4 and type(row[0]) is str
                     and type(row[1]) is int and type(row[2]) is int and type(row[3]) is int
                     for row in directions), "REASON_QUERY_IDENTITY")
        direction_matches=[ordinal for ordinal,row in enumerate(directions)
                           if row[0]==cut_query["direction"]]
        need(len(direction_matches)==1,"REASON_QUERY_IDENTITY")
        direction=direction_matches[0]
        need(directions[direction][1]==cut_query["bit"],"REASON_QUERY_IDENTITY")
        p6_identity=context["p6Stage"]
        facts=context["facts"]
        for key in ("netId","startPadId","rootPadId","reachedPthPadIds"):
            need(type(p6_identity[key]) is type(facts[key]) and p6_identity[key]==facts[key],
                 "REASON_QUERY_IDENTITY")
        expected_identity={
            "layer":stage_query["layer"],"fromState":stage_query["fromState"],
            "toState":stage_query["toState"],"bit":stage_query["bit"],
            "selected":stage_query["selected"],"fromGridX":stage_query["fromGridX"],
            "fromGridY":stage_query["fromGridY"],"toGridX":stage_query["toGridX"],
            "toGridY":stage_query["toGridY"],"fromX":cut_query["fromX"],
            "fromY":cut_query["fromY"],"toX":cut_query["toX"],"toY":cut_query["toY"],
            "direction":direction,"netId":p6_identity["netId"],
            "startPadId":p6_identity["startPadId"],"rootPadId":p6_identity["rootPadId"],
            "reachedPthPadIds":p6_identity["reachedPthPadIds"],
        }
        need(set(expected_identity)==IDENTITY_FIELDS,"REASON_QUERY_IDENTITY")
        for key,expected in expected_identity.items():
            need(type(ident[key]) is type(expected) and ident[key]==expected,
                 "REASON_QUERY_IDENTITY")
        if interrupted:
            need(q["allowed"] is None, "REASON_QUERY_RESULT")
        else:
            need(type(q["allowed"]) is bool and q["allowed"] is False, "REASON_QUERY_RESULT")
        fine, coarse = q["fineEvents"], q["coarseEvents"]
        need(type(fine) is list and type(coarse) is list, "REASON_EVENT_COUNT")
        if mode == "NULL":
            need(not fine, "REASON_MODE_EVENTS")
        elif interrupted:
            need(len(fine) == 1 and len(coarse) == 0, "REASON_EVENT_COUNT")
        else:
            need(len(fine) == 1 and len(coarse) == 1, "REASON_EVENT_COUNT")
        if coarse:
            c = coarse[0]
            exact_obj(c, {"ordinal","netId","startPadId","endpointPadId","fromGridX","fromGridY",
                          "toGridX","toGridY","direction","stage"}, "REASON_COARSE_FIELDS")
            exact_int(c["ordinal"], 0, MAX_UNITS-1, "REASON_EVENT_FIELD_TYPE")
            for key in ("fromGridX","fromGridY","toGridX","toGridY","direction"):
                need(type(c[key]) is int, "REASON_EVENT_FIELD_TYPE")
            for key in ("netId","startPadId","endpointPadId","stage"):
                need(type(c[key]) is str and bool(c[key]), "REASON_EVENT_FIELD_TYPE")
            stage_event = next((e for e in stage_query.get("events", [])
                                if e.get("ordinal") == c.get("ordinal")), None)
            need((mode == "NULL" or (stage_event is not None and c == stage_event))
                 and c["netId"] == ident["netId"]
                 and c["startPadId"] == ident["startPadId"]
                 and c["fromGridX"] == ident["fromGridX"] and c["fromGridY"] == ident["fromGridY"]
                 and c["toGridX"] == ident["toGridX"] and c["toGridY"] == ident["toGridY"]
                 and c["direction"] == ident["direction"],
                 "REASON_EVENT_CORRELATION")
        if fine:
            first_stage = next((event for event in stage_query.get("events", [])
                                if type(event) is dict), None)
            expected_endpoint = first_stage.get("endpointPadId") if first_stage else None
            validate_reason_event(fine[0], q, context["operation"], expected_endpoint,
                                  index_bindings)
        cleanup = exact_obj(q["cleanup"], CLEANUP_FIELDS, "REASON_CLEANUP_FIELDS")
        for key in CLEANUP_FIELDS:
            if key.endswith("Sha256"):
                digest(cleanup[key], "REASON_CLEANUP_SHA")
            else:
                need(type(cleanup[key]) is bool and cleanup[key] is True, "REASON_CLEANUP_PROOF")
        need(cleanup["queryEffectsBeforeSha256"] == cleanup["queryEffectsAfterSha256"]
             and cleanup["sourceEffectsBeforeSha256"] == cleanup["sourceEffectsAfterSha256"]
             and cleanup["layoutGeometryBeforeSha256"] == cleanup["layoutGeometryAfterSha256"],
             "REASON_CLEANUP_STABILITY")
        layer = ident["layer"]
        face = context["p6Stage"]["sourceProof"]["faces"][layer]
        need(cleanup["sourceEffectsBeforeSha256"] == face["sourceEffectsBeforeSha256"]
             and cleanup["sourceEffectsAfterSha256"] == face["sourceEffectsAfterSha256"],
             "REASON_SOURCE_EFFECTS")
        work = exact_obj(q["work"], WORK_FIELDS, "REASON_WORK_FIELDS")
        for key,val in work.items():
            high=(MAX_UNITS*4096 if "Entries" in key else MAX_WORK_NS if "Nanos" in key
                  else MAX_GROUP_NS if key=="maximumUnitNanos" else MAX_UNITS)
            exact_int(val, 0, high, "REASON_WORK_TYPE")
        need(work["copyEntries"] <= work["copyUnits"]*4096 and
             work["comparisonEntries"] <= work["comparisonUnits"]*4096,
             "REASON_UNIT_ARITHMETIC")
        need(work["queryUnits"] == 1 and work["maximumUnitNanos"] <= MAX_GROUP_NS,
             "REASON_WORK_UNIT")
        need(work["groupCount"] == sum(work[k] for k in
             ("copyUnits","comparisonUnits","queryUnits","metadataUnits")),
             "REASON_GROUP_ARITHMETIC")
        reason_count += len(fine); coarse_count += len(coarse)
        reason_rows.append({"ordinal":q["ordinal"],"parentOrdinal":parent_ordinal,
                            "identity":ident,"fineEvents":fine,"coarseEvents":coarse,
                            "cleanup":cleanup})
    footer = exact_obj(rows[-1], FOOTER_FIELDS, "REASON_FOOTER_FIELDS")
    need(footer["kind"] == "footer" and footer["runId"] == context["runId"]
         and footer["inputSha256"] == context["inputSha"]
         and footer["sourceSha256"] == context["sourceSha"] and footer["mode"] == mode
         and footer["parentPrefixSha256"] == PREFIX_SHA and footer["parentGraphSha256"] == GRAPH_SHA,
         "REASON_FOOTER_BINDING")
    status = "EXPECTED_REASON_THROW_ONLY" if interrupted else (
        "COMPLETE_NULL_CONTROL_ONLY" if mode == "NULL" else "COMPLETE_REASON_OBSERVATIONS_ONLY")
    need(footer["status"] == status and footer["complete"] is (not interrupted)
         and type(footer["complete"]) is bool and footer["cleanupKind"] == "FULL_NATIVE_WALL_PLUS_POST_SOURCE"
         and all(footer[k] is True for k in ("cleanupComplete","sourcePreserved","observersDetached",
                    "referencesDropped","ownersReleased","contextRestored")), "REASON_FOOTER_STATUS")
    outside = exact_obj(footer["outsideQueryWork"], FOOTER_OUTSIDE_FIELDS, "REASON_FOOTER_WORK_FIELDS")
    for key,val in outside.items():
        high=(MAX_UNITS*4096 if "Entries" in key else MAX_WORK_NS if "Nanos" in key
              else MAX_GROUP_NS if key=="maximumUnitNanos" else MAX_UNITS)
        exact_int(val, 0, high, "REASON_FOOTER_WORK_TYPE")
    need(outside["copyEntries"] <= outside["copyUnits"]*4096 and
         outside["comparisonEntries"] <= outside["comparisonUnits"]*4096,
         "REASON_UNIT_ARITHMETIC")
    need(outside["groupCount"] == sum(outside[k] for k in
         ("copyUnits","comparisonUnits","queryUnits","metadataUnits","setupUnits","exportUnits")),
         "REASON_GROUP_ARITHMETIC")
    entry_total_limit=MAX_UNITS*4096
    footer_integer_limits={
        "selectedQueryCount":MAX_UNITS,"queryCount":MAX_UNITS,
        "returnedQueryCount":MAX_UNITS,"fineEventCount":MAX_UNITS,
        "coarseEventCount":MAX_UNITS,"priorObservationUnits":MAX_UNITS,
        "p8ObservationUnits":MAX_UNITS,"totalObservationUnits":MAX_UNITS,
        "copyEntries":entry_total_limit,"comparisonEntries":entry_total_limit,
        "copyUnits":MAX_UNITS,"comparisonUnits":MAX_UNITS,"queryUnits":MAX_UNITS,
        "metadataUnits":MAX_UNITS,"setupUnits":MAX_UNITS,"exportUnits":MAX_UNITS,
        "copyNanos":MAX_WORK_NS,"comparisonNanos":MAX_WORK_NS,"queryNanos":MAX_WORK_NS,
        "metadataNanos":MAX_WORK_NS,"setupNanos":MAX_WORK_NS,"exportNanos":MAX_WORK_NS,
        "workNanos":MAX_WORK_NS,"maximumUnitNanos":MAX_GROUP_NS,"groupCount":MAX_UNITS,
    }
    for key,high in footer_integer_limits.items():
        exact_int(footer[key],0,high,"REASON_FOOTER_INTEGER")
    sum_fields = {
        key: _sum_work(queries, outside, key) for key in
        ("copyEntries","comparisonEntries","copyUnits","comparisonUnits","queryUnits","metadataUnits",
         "copyNanos","comparisonNanos","queryNanos","metadataNanos")}
    for key in ("setupUnits","exportUnits","setupNanos","exportNanos"):
        sum_fields[key] = outside[key]
    for key,value in sum_fields.items():
        need(type(footer[key]) is int and footer[key] == value, "REASON_UNIT_ARITHMETIC")
    need(footer["copyEntries"] <= footer["copyUnits"]*4096 and
         footer["comparisonEntries"] <= footer["comparisonUnits"]*4096,
         "REASON_UNIT_ARITHMETIC")
    need(footer["groupCount"]==_sum_work(queries,outside,"groupCount"),
         "REASON_GROUP_ARITHMETIC")
    units = sum(footer[k] for k in ("copyUnits","comparisonUnits","queryUnits","metadataUnits","setupUnits","exportUnits"))
    nanos = sum(footer[k] for k in ("copyNanos","comparisonNanos","queryNanos","metadataNanos","setupNanos","exportNanos"))
    need(footer["groupCount"] == units, "REASON_GROUP_ARITHMETIC")
    observed_max = max([outside["maximumUnitNanos"]] +
                       [row["work"]["maximumUnitNanos"] for row in queries])
    need(footer["p8ObservationUnits"] == units and footer["totalObservationUnits"] == footer["priorObservationUnits"]+units
         and footer["workNanos"] == nanos and footer["priorObservationUnits"] == context["stageTotals"]["totalObservationUnits"]
         and footer["queryUnits"] == expected_n and footer["selectedQueryCount"] == expected_n
         and footer["queryCount"] == expected_n and footer["returnedQueryCount"] == (0 if interrupted else expected_n)
         and footer["fineEventCount"] == reason_count and footer["coarseEventCount"] == coarse_count
         and footer["maximumUnitNanos"] == observed_max
         and footer["groupCount"] <= MAX_UNITS and units <= MAX_UNITS
         and footer["totalObservationUnits"] <= MAX_UNITS and 0 <= nanos <= MAX_WORK_NS
         and footer["maximumUnitNanos"] <= MAX_GROUP_NS, "REASON_UNIT_ARITHMETIC")
    need((mode == "NULL" and reason_count == 0) or
         (mode == "OBSERVE" and reason_count == coarse_count == 14) or
         (interrupted and reason_count == 1 and coarse_count == 0),
         "REASON_MODE_EVENTS" if mode == "NULL" else "REASON_EVENT_COUNT")
    return {"status":status,"header":header,"queries":queries,"footer":footer,"reasonRows":reason_rows,
            "reasonCount":reason_count,"coarseCount":coarse_count,"raw":raw}

def validate_reason_index(index_raw: bytes, data: dict, context: dict,
                          stage_raw: bytes, side_raw: bytes, parent_aggregate: int) -> dict:
    value = strict_line(index_raw, "REASON_INDEX_FRAMING")
    exact_obj(value, REASON_INDEX_FIELDS, "REASON_INDEX_FIELDS")
    exact_selected_ordinals(value["selectedQueryOrdinals"],"REASON_INDEX_BINDING")
    for key in ("queryCount","returnedQueryCount","fineEventCount","coarseEventCount",
                "priorObservationUnits","p8ObservationUnits","totalObservationUnits"):
        exact_int(value[key],0,MAX_UNITS,"REASON_INDEX_INTEGER")
    exact_int(value["aggregateBytes"],0,OP_BYTES,"REASON_INDEX_AGGREGATE")
    need(value["schema"] == "tsj-canonical-geometric-prefix-index-v1"
         and value["mode"] == context["mode"] and value["runId"] == context["runId"]
         and value["inputSha256"] == context["inputSha"]
         and value["sourceSha256"] == context["sourceSha"] and value["wireSha256"] == P8_WIRE_SHA
         and value["parentPrefixSha256"] == PREFIX_SHA and value["parentGraphSha256"] == GRAPH_SHA
         and value["queryCount"] == data["footer"]["queryCount"]
         and value["returnedQueryCount"] == data["footer"]["returnedQueryCount"]
         and value["fineEventCount"] == data["footer"]["fineEventCount"]
         and value["coarseEventCount"] == data["footer"]["coarseEventCount"]
         and value["priorObservationUnits"] == data["footer"]["priorObservationUnits"]
         and value["p8ObservationUnits"] == data["footer"]["p8ObservationUnits"]
         and value["totalObservationUnits"] == data["footer"]["totalObservationUnits"]
         and value["cleanupKind"] == "FULL_NATIVE_WALL_PLUS_POST_SOURCE"
         and value["cleanupComplete"] is True, "REASON_INDEX_BINDING")
    exact_ref(value["parentStageIndex"], "canonical-stage-index.json", stage_raw, "REASON_PARENT_STAGE_REF")
    exact_ref(value["reasonSidecar"], "prefix-geometric-reasons.jsonl", side_raw, "REASON_SIDECAR_REF")
    need(value["aggregateBytes"] == parent_aggregate + len(side_raw), "REASON_INDEX_AGGREGATE")
    return value

def expected_output(mode: str) -> tuple[set[str],set[str]]:
    need(mode in ("CANARY","SUITE"), "HOST_MODE")
    files = set(BASE_HOST_FILES)
    files.add("controls/P8_NONEMPTY_DATA/data/sentinel.txt")
    dirs = set(BASE_DIRS)
    if mode == "SUITE":
        dirs.update({"runs","runs/NULL","runs/NULL/data","runs/OBSERVE","runs/OBSERVE/data",
                     "runs/CALLBACK_THROW","runs/CALLBACK_THROW/data","runs/REASON_THROW","runs/REASON_THROW/data"})
        for run_mode,native_files in NATIVE_FILES.items():
            root = "runs/" + run_mode
            files.update({root+"/launch-intent.json",root+"/process.json",root+".stdout",root+".stderr"})
            files.update(root+"/data/"+name for name in native_files)
    return files,dirs

def artifact_role(path: str) -> str:
    base = path.rsplit("/",1)[-1]
    if base == "sentinel.txt": return "CONTROL_REPORT"
    if base == "compiler-sources.txt": return "COMPILER_INPUT"
    if base == "bridge-copies.json": return "BRIDGE_RECEIPT"
    if base in ("canonical-stage-index.json","canonical-geometric-prefix-index.json"):
        return "MODE_INDEX"
    if "/data/" in path: return "NATIVE_DATA"
    if base.endswith("launch-intent.json") or base == "scratch-intent.json": return "HOST_INTENT"
    if base.endswith("process.json"): return "PROCESS_RECEIPT"
    if path in ("source-preflight.stdout","source-preflight.stderr",
                "source-prenative.stdout","source-prenative.stderr",
                "source-post.stdout","source-post.stderr"):
        return "SOURCE_AUDIT"
    if path in ("p6-canary.stdout","p8-canary.stdout"): return "CANARY_REPORT"
    if base.endswith(".stderr"): return "STDERR"
    if base.endswith(".stdout"): return "STDOUT"
    need(False,"OUTPUT_ROLE_UNKNOWN")

def artifact_operation(path: str) -> str:
    run_streams={
        "runs/NULL.stdout":"NULL","runs/NULL.stderr":"NULL",
        "runs/OBSERVE.stdout":"OBSERVE","runs/OBSERVE.stderr":"OBSERVE",
        "runs/CALLBACK_THROW.stdout":"CALLBACK_THROW",
        "runs/CALLBACK_THROW.stderr":"CALLBACK_THROW",
        "runs/REASON_THROW.stdout":"REASON_THROW",
        "runs/REASON_THROW.stderr":"REASON_THROW",
    }
    if path in run_streams:
        return run_streams[path]
    if path.startswith("runs/"):
        return path.split("/",2)[1]
    control_streams={
        "p8-bad-mode.stdout":"P8_BAD_MODE","p8-bad-mode.stderr":"P8_BAD_MODE",
        "p8-bad-uuid.stdout":"P8_BAD_UUID","p8-bad-uuid.stderr":"P8_BAD_UUID",
        "p8-bad-input-sha.stdout":"P8_BAD_INPUT_SHA","p8-bad-input-sha.stderr":"P8_BAD_INPUT_SHA",
        "p8-nonempty-data.stdout":"P8_NONEMPTY_DATA",
        "p8-nonempty-data.stderr":"P8_NONEMPTY_DATA",
    }
    if path in control_streams:
        return control_streams[path]
    if path.startswith("controls/P8_BAD_MODE/"): return "P8_BAD_MODE"
    if path.startswith("controls/P8_BAD_UUID/"): return "P8_BAD_UUID"
    if path.startswith("controls/P8_BAD_INPUT_SHA/"): return "P8_BAD_INPUT_SHA"
    if path.startswith("controls/P8_NONEMPTY_DATA/"): return "P8_NONEMPTY_DATA"
    for stem,operation in (("p8-bad-mode-","P8_BAD_MODE"),("p8-bad-uuid-","P8_BAD_UUID"),
                           ("p8-bad-input-sha-","P8_BAD_INPUT_SHA"),
                           ("p8-nonempty-data-","P8_NONEMPTY_DATA")):
        if path.startswith(stem): return operation
    if path.startswith("p6-canary-") or path == "p6-canary.stdout" or path == "p6-canary.stderr":
        return "P6_CANARY"
    if path.startswith("p8-canary-") or path == "p8-canary.stdout" or path == "p8-canary.stderr":
        return "P8_LITERAL_CANARY"
    return "HOST"

def read_output_tree(root: Path, mode: str) -> tuple[dict[str,bytes],set[str],set[str]]:
    files,dirs = expected_output(mode)
    root = canonical(str(root), "OUTPUT_ROOT", "dir")
    temp = Path(os.path.abspath(tempfile.gettempdir()))
    need(below(root,temp), "OUTPUT_ROOT_TEMP_CONFINEMENT")
    for forbidden in (Path(os.environ.get("TEMP",str(temp))),):
        need(not same_path(root,forbidden), "OUTPUT_ROOT_IDENTITY")
    need(not below(root,Path(os.path.dirname(__file__))), "OUTPUT_ROOT_SOURCE_OVERLAP")
    need(not below(root,Path(".")), "OUTPUT_ROOT_REPOSITORY_OVERLAP")
    actual_files:set[str] = set()
    actual_dirs:set[str] = set()
    entry_limit=len(files)+len(dirs)
    entry_count=0
    for relative in [""] + sorted(dirs):
        here = root if not relative else root.joinpath(*relative.split("/"))
        no_reparse(here, exists=True)
        try:
            with os.scandir(here) as scan:
                for entry in scan:
                    entry_count+=1
                    need(entry_count<=entry_limit,"OUTPUT_TREE_ENTRY_LIMIT")
                    child_rel = entry.name if not relative else relative + "/" + entry.name
                    try:
                        st = entry.stat(follow_symlinks=False)
                    except OSError:
                        raise Reject("OUTPUT_TREE_STAT") from None
                    need(not reparse(st), "OUTPUT_TREE_REPARSE")
                    if stat.S_ISDIR(st.st_mode):
                        actual_dirs.add(child_rel)
                    elif stat.S_ISREG(st.st_mode):
                        actual_files.add(child_rel)
                    else:
                        raise Reject("OUTPUT_TREE_ENTRY_KIND")
        except OSError:
            raise Reject("OUTPUT_TREE_READ") from None
    need(actual_files == files and actual_dirs == dirs, "OUTPUT_TREE_CENSUS")
    data:dict[str,bytes] = {}
    total=0
    for relative in sorted(files):
        max_bytes = MAX_LINE if relative in ("host-result.json","suite-index.json","suite-footer.json") else (
            1024*1024 if relative == "compiler-sources.txt" else OP_BYTES if "/data/" in relative else HOST_BYTES)
        raw = stable_read(root.joinpath(*relative.split("/")), max_bytes,
                          "OUTPUT_FILE_READ", allow_empty=True)
        if relative.endswith((".jsonl",".json")):
            need(len(raw) <= MAX_LINE if relative.endswith(".json") else len(raw) <= OP_BYTES,
                 "OUTPUT_STRUCTURED_LIMIT")
        data[relative]=raw
        total += len(raw)
        need(total <= SUITE_BYTES, "OUTPUT_SUITE_CAP")
    return data,actual_files,actual_dirs

def load_semantic_parent(pins: dict, rows: dict[str,dict], source_rows: list[dict]) -> dict:
    p6path=Path(pins["p6Context"]["pinsPath"])
    p6raw=stable_hash(p6path,rows[normpath(p6path)],MAX_PINS,"P6_PINS_HASH")
    need(sha(p6raw)==P6_PINS_SHA,"P6_PINS_IDENTITY")
    p6pins=parse_json(p6raw,"P6_PINS_JSON")
    need(type(p6pins) is dict and p6pins.get("parent")==P6_PARENT,"P6_PINS_PARENT")
    p6files=p6pins.get("files")
    need(type(p6files) is list,"P6_FILES")
    p6pinmap={}
    for row in p6files:
        exact_obj(row,FILE_FIELDS,"P6_PIN_ROW")
        path=canonical(row["path"],"P6_PIN_PATH","file")
        need(type(row["bytes"]) is int and row["bytes"]>0,"P6_PIN_BYTES")
        digest(row["sha256"],"P6_PIN_SHA")
        key=normpath(path); need(key not in p6pinmap,"P6_PIN_DUPLICATE")
        p6pinmap[key]={**row,"path":str(path)}
    p6reader_path=p6path.parent/"read_stages.py"
    p6reader_raw=stable_hash(p6reader_path,p6pinmap[normpath(p6reader_path)],4*1024*1024,"P6_READER_HASH")
    need(sha(p6reader_raw)==P6_READER_SHA,"P6_READER_IDENTITY")
    p5path=Path(p6pins["p5ReaderPath"])
    p5raw=stable_hash(p5path,p6pinmap[normpath(p5path)],4*1024*1024,"P5_READER_HASH")
    need(sha(p5raw)==P5_READER_SHA,"P5_READER_IDENTITY")
    p5mod=module_from_bytes(p5raw,p5path,"q100_authenticated_p5_reader")
    p6mod=module_from_bytes(p6reader_raw,p6reader_path,"q100_authenticated_p6_reader")
    p6_pinned=p5mod._pin_map(p6pins)
    p5pins_raw=stable_hash(Path(p6pins["p5InputPinsPath"]),
                           p6pinmap[normpath(Path(p6pins["p5InputPinsPath"]))],MAX_PINS,"P5_PINS_HASH")
    p5pins=parse_json(p5pins_raw,"P5_PINS_JSON")
    p4path=Path(p5pins["p4ReaderPath"])
    p4raw=stable_hash(p4path,p6pinmap[normpath(p4path)],4*1024*1024,"P4_READER_HASH")
    need(sha(p4raw)==P4_READER_SHA,"P4_READER_IDENTITY")
    p4_qual_path=Path(p5pins["p4QualificationPath"])
    p4qualraw=stable_hash(p4_qual_path,p6pinmap[normpath(p4_qual_path)],MAX_PINS,"P4_QUALIFICATION_HASH")
    p4qual=parse_json(p4qualraw,"P4_QUALIFICATION_JSON")
    published={row["path"]:row for row in p4qual.get("publishedCopies",[]) if type(row) is dict}
    p4_parent=p4_qual_path.parent
    oracle_modules={}
    for relative,name in (("oracles-r1/read_fixed_prefix_graph.py","read_fixed_prefix_graph"),
                          ("oracles-r1/fixed_prefix_minimum_vias.py","fixed_prefix_minimum_vias")):
        path=p4_parent.joinpath(*relative.split("/"))
        published_row=published.get(relative)
        need(type(published_row) is dict,"P4_ORACLE_PUBLICATION")
        raw=stable_hash(path,p6pinmap[normpath(path)],4*1024*1024,"P4_ORACLE_HASH")
        need(len(raw)==published_row.get("bytes") and sha(raw)==published_row.get("sha256"),
             "P4_ORACLE_BINDING")
        oracle_modules[name]=module_from_bytes(raw,path,name)

    source_by_path={row["path"]:row for row in source_rows}
    def module_bytes(path: Path, expected_sha: str, expected_bytes: int | None=None) -> bytes:
        key=normpath(path)
        row=p6pinmap.get(key)
        if row is None:
            try: rel=path.relative_to(Path(pins["repositoryRoot"])).as_posix()
            except ValueError: raise Reject("SEMANTIC_MODULE_UNPINNED") from None
            row=source_by_path.get(rel)
        need(type(row) is dict and row["sha256"]==expected_sha,"SEMANTIC_MODULE_PIN")
        raw=stable_hash(Path(row["path"]),row,4*1024*1024,"SEMANTIC_MODULE_HASH")
        need((expected_bytes is None or len(raw)==expected_bytes) and sha(raw)==expected_sha,
             "SEMANTIC_MODULE_HASH")
        return raw

    module_cache={normpath(p6reader_path):p6mod,normpath(p5path):p5mod}
    for name,mod in oracle_modules.items():
        module_cache[normpath(Path(mod.__file__))]=mod
    p6_builder_path=p6reader_path.parent/"build_overlay.py"
    p6_builder_key=normpath(p6_builder_path)
    p6_builder_sha="8d741d4d166d63209bc39eace2d527cadf710f3320e2c8cd01d8dff00bb7d1fc"
    p6_builder_bytes=14560
    p6_builder_pin=p6pinmap.get(p6_builder_key)
    need(type(p6_builder_pin) is dict and same_path(p6_builder_pin["path"],p6_builder_path)
         and p6_builder_pin["bytes"]==p6_builder_bytes and p6_builder_pin["sha256"]==p6_builder_sha,
         "P6_OVERLAY_BUILDER_PIN")
    p6_builder_source_cache={}
    p6_branch_relative="tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py"
    p6_branch_name="p5_pinned_old_branch_reader"
    p6_branch_sha="a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
    p6_branch_bytes=137864
    p6_branch_declared_path=Path(p6pins["repositoryRoot"]).joinpath(*p6_branch_relative.split("/"))
    p6_branch_declared_key=normpath(p6_branch_declared_path)
    p6_branch_key=None
    p6_source_closure=None
    p6_branch_source_cache={}
    def p5_module_loader(path, name, expected_sha, expected_bytes=None):
        key=normpath(path)
        if key==p6_branch_declared_key or key==p6_branch_key or name==p6_branch_name:
            need(p6_source_closure is not None and p6_branch_key is not None
                 and key==p6_branch_key and name==p6_branch_name
                 and expected_sha==p6_branch_sha and type(expected_bytes) is int
                 and expected_bytes==p6_branch_bytes,"P5_BRANCH_MODULE_BINDING")
            branch_path=Path(p6_source_closure["repo"]).joinpath(*p6_branch_relative.split("/"))
            need(same_path(path,branch_path),"P5_BRANCH_MODULE_BINDING")
            row=p6_source_closure["sourceRows"].get(p6_branch_relative)
            need(type(row) is dict and row.get("path")==p6_branch_relative
                 and row.get("role")=="READER" and type(row.get("bytes")) is int
                 and row["bytes"]==p6_branch_bytes and row.get("sha256")==p6_branch_sha,
                 "P5_BRANCH_SOURCE_ROW")
            mod=module_cache.get(key)
            if mod is not None:
                need(getattr(mod,"__name__",None)==p6_branch_name
                     and same_path(getattr(mod,"__file__",""),branch_path),
                     "P5_BRANCH_MODULE_CACHE")
                return mod
            if key not in p6_branch_source_cache:
                raw=stable_hash(branch_path,row,4*1024*1024,"P5_BRANCH_READER_HASH")
                need(len(raw)==p6_branch_bytes and sha(raw)==p6_branch_sha,
                     "P5_BRANCH_READER_HASH")
                p6_branch_source_cache[key]=raw
            mod=module_from_bytes(p6_branch_source_cache[key],branch_path,p6_branch_name)
            module_cache[key]=mod
            return mod
        if key in module_cache:
            mod=module_cache[key]
            return mod
        raw=module_bytes(Path(path),expected_sha,expected_bytes)
        mod=module_from_bytes(raw,Path(path),name)
        module_cache[key]=mod
        return mod
    def p6_module_loader(path, expected_sha, name):
        key=normpath(path)
        if (key==normpath(p5path) and expected_sha==P5_READER_SHA
                and name=="p6_pinned_p5_reader"):
            return p5mod
        need(key==p6_builder_key and expected_sha==p6_builder_sha
             and name=="p6_pinned_overlay_builder","P6_DYNAMIC_MODULE")
        mod=module_cache.get(key)
        if mod is not None:
            return mod
        raw=p6_builder_source_cache.get(key)
        if raw is None:
            raw=stable_hash(p6_builder_path,p6_builder_pin,4*1024*1024,"P6_OVERLAY_BUILDER_HASH")
            need(len(raw)==p6_builder_bytes and sha(raw)==p6_builder_sha,"P6_OVERLAY_BUILDER_HASH")
            p6_builder_source_cache[key]=raw
        mod=module_from_bytes(raw,p6_builder_path,name)
        module_cache[key]=mod
        return mod
    p5mod._module_from_pinned=p5_module_loader
    p6mod._module=p6_module_loader
    p4mod=p5_module_loader(p4path,"q100_authenticated_p4_reader",P4_READER_SHA,70178)
    need(callable(getattr(p4mod,"_operation",None)) and
         callable(getattr(p4mod,"_capture_transport",None)) and
         callable(getattr(p4mod,"_compare_capture",None)),"P4_READER_API")
    closure=p6mod._validate_source_closure(p6pins,p6_pinned,p6path,p5mod)
    closure["pinned"]=p6_pinned
    p6_source_closure=closure
    p6_branch_key=normpath(Path(closure["repo"]).joinpath(*p6_branch_relative.split("/")))
    need(p6_branch_key==p6_branch_declared_key,"P6_BRANCH_REPO_BINDING")
    parent_context=p6mod._p5_load_context(p6pins,closure,p5mod,p6_pinned)
    parent=p6mod._p5_parent(p6pins,p5mod,parent_context,closure,p6_pinned)
    p6mod._validate_zero_delta_input(p6pins,parent)
    need(parent["context"]["p4Reader"] is p4mod,"P4_CONTEXT_MODULE_BINDING")
    return {"p6":p6mod,"p5":p5mod,"p4":p4mod,"pins":p6pins,
            "pinsRaw":p6raw,"pinned":p6_pinned,"closure":closure,"parent":parent,
            "sourceRows":source_rows,"sourceByPath":source_by_path,
            "moduleCache":module_cache,"p5PinsRaw":p5pins_raw}

def run_authenticated_auditor(pins_path: Path, pins_sha: str,
                              source_sha: str, rows: dict[str,dict]) -> dict:
    path=Path(rows[normpath(Path(__file__))]["path"])
    auditor_path=Path(path).parent/"audit_study_inputs.py"
    row=rows.get(normpath(auditor_path))
    need(type(row) is dict,"INPUT_AUDITOR_PIN")
    raw=stable_hash(auditor_path,row,MAX_PINS,"INPUT_AUDITOR_HASH")
    module=module_from_bytes(raw,auditor_path,"q100_authenticated_inputs_auditor")
    need(callable(getattr(module,"validate_run",None)),"INPUT_AUDITOR_API")
    try: result=module.validate_run(str(pins_path),pins_sha)
    except Exception as error:
        code=getattr(error,"code",None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}",code):
            raise Reject("INPUT_AUDIT_"+code) from None
        raise Reject("INPUT_AUDIT_FAILED") from None
    exact_obj(result,{"status","pinsSha256","sourceInventorySha256","sourceRows","productionFiles","extraFiles"},"INPUT_AUDIT_SUMMARY")
    need(result["status"]=="PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY" and
         result["pinsSha256"]==pins_sha and result["sourceInventorySha256"]==source_sha
         and result["sourceRows"]==1084 and result["productionFiles"]==850,
         "INPUT_AUDIT_SUMMARY")
    return result

def validate_parent_receipt(parent_path: Path, pins: dict, pins_path: Path,
                             pins_sha: str, output_root: Path,
                             preliminary_host: dict) -> tuple[dict,bytes,float]:
    raw=stable_read(parent_path,MAX_LINE,"PARENT_RECEIPT")
    parent=parse_document(raw,"PARENT_RECEIPT")
    exact_obj(parent,{"schema","elapsedMs","process"},"PARENT_RECEIPT_FIELDS")
    need(parent["schema"]==PARENT_SCHEMA,"PARENT_RECEIPT_SCHEMA")
    try: elapsed=finite_num(parent["elapsedMs"],0,1800000,"PARENT_ELAPSED_TYPE_INVALID")
    except Reject: raise
    need(elapsed>0,"PARENT_ELAPSED_TYPE_INVALID")
    process=parent["process"]
    ps=Path(pins["toolPaths"]["powershellExe"])
    host=Path(pins["roles"]["process"]["host"])
    expected_args=["-NoLogo","-NoProfile","-NonInteractive","-File",str(host),
        "-RepositoryRoot",pins["repositoryRoot"],"-InputPinsPath",str(pins_path),
        "-InputPinsSha256",pins_sha,"-OutputRoot",str(output_root),
        "-JavaHome",pins["toolPaths"]["javaHome"],"-PythonExe",pins["toolPaths"]["pythonExe"],
        "-Mode",preliminary_host["mode"]]
    fields={"FilePath","Arguments","ProcessId","ProcessStartTicks","ExitCode","Stdout","Stderr",
            "StdoutLog","StderrLog","ProcessRoot","TerminationProven"}
    exact_obj(process,fields,"PARENT_PROCESS_FIELDS")
    need(process["FilePath"]==str(ps),"PARENT_PROCESS_EXECUTABLE")
    need(process["Arguments"]==expected_args and type(process["Arguments"]) is list,
         "PARENT_PROCESS_ARGUMENTS")
    exact_int(process["ProcessId"],1,2147483647,"PARENT_PROCESS_PID")
    exact_int(process["ProcessStartTicks"],1,3155378975999999999,"PARENT_PROCESS_TICKS")
    if type(process["ExitCode"]) is not int or process["ExitCode"]!=0:
        raise Reject("PARENT_PROCESS_EXIT_INVALID")
    if process["TerminationProven"] is not True:
        raise Reject("PARENT_PROCESS_TERMINATION_INVALID")
    need(type(process["Stdout"]) is str and type(process["Stderr"]) is str,
         "PARENT_PROCESS_STREAMS")
    process_root_paths(process,"PARENT_PROCESS")
    need(process["Stderr"]=="" and process["Stdout"].startswith("P8_PREFIX_HOST_COMPLETE ")
         and process["Stdout"].endswith("\r\n") and process["Stdout"].count("\n")==1,
         "PARENT_PROCESS_STREAMS")
    timing=preliminary_host.get("timing")
    need(type(timing) is dict and "cleanupStartOffsetMs" in timing,"PARENT_TIMING_SCHEMA")
    cleanup_start=finite_num(timing["cleanupStartOffsetMs"],0,1800000,"PARENT_TIMING_TYPE_INVALID")
    if elapsed < cleanup_start:
        raise Reject("PARENT_TIMING_ORDER_INVALID")
    need(elapsed<=1800000,"PARENT_ELAPSED_LIMIT")
    marker_bytes=process["Stdout"].encode("utf-8")
    prefix=b"P8_PREFIX_HOST_COMPLETE "
    need(marker_bytes.endswith(b"\r\n") and marker_bytes.count(b"\n")==1,
         "PARENT_MARKER_FRAMING")
    marker=parse_json(marker_bytes[len(prefix):-2],"PARENT_MARKER_JSON")
    exact_obj(marker,{"schema","status","mode","suiteId","inputSha256","sourceSha256","wireSha256",
                      "hostResult","suiteIndex","suiteFooter","layoutQualification"},"PARENT_MARKER_FIELDS")
    need(marker["schema"]=="tsj-q100-geometric-prefix-host-complete-v1" and
         marker["mode"]==preliminary_host["mode"] and marker["suiteId"]==preliminary_host["suiteId"] and
         marker["inputSha256"]==preliminary_host["inputSha256"] and
         marker["sourceSha256"]==preliminary_host["sourceSha256"] and
         marker["wireSha256"]==preliminary_host["wireSha256"] and
         marker["layoutQualification"] is False,"PARENT_MARKER_BINDING")
    return {"document":parent,"process":process,"marker":marker},raw,elapsed

def _validate_host_output_creation(path: Path, expected_ticks: str) -> None:
    need(type(expected_ticks) is str and re.fullmatch(r"[0-9]{1,19}",expected_ticks) is not None
         and 0 < int(expected_ticks) <= 3155378975999999999,
         "HOST_OUTPUT_CREATION_TICKS")
    try:
        path=canonical(os.fspath(path),"HOST_OUTPUT_IDENTITY","dir")
    except Exception:
        raise Reject("HOST_OUTPUT_IDENTITY") from None
    need(os.name=="nt","HOST_OUTPUT_IDENTITY")

    class _FileTime(ctypes.Structure):
        _fields_=[("dwLowDateTime",ctypes.c_uint32),
                  ("dwHighDateTime",ctypes.c_uint32)]

    class _ByHandleFileInformation(ctypes.Structure):
        _fields_=[("dwFileAttributes",ctypes.c_uint32),
                  ("ftCreationTime",_FileTime),
                  ("ftLastAccessTime",_FileTime),
                  ("ftLastWriteTime",_FileTime),
                  ("dwVolumeSerialNumber",ctypes.c_uint32),
                  ("nFileSizeHigh",ctypes.c_uint32),
                  ("nFileSizeLow",ctypes.c_uint32),
                  ("nNumberOfLinks",ctypes.c_uint32),
                  ("nFileIndexHigh",ctypes.c_uint32),
                  ("nFileIndexLow",ctypes.c_uint32)]

    try:
        kernel=ctypes.WinDLL("kernel32",use_last_error=True)
        create_file=kernel.CreateFileW
        create_file.argtypes=(ctypes.c_wchar_p,ctypes.c_uint32,ctypes.c_uint32,
                              ctypes.c_void_p,ctypes.c_uint32,ctypes.c_uint32,ctypes.c_void_p)
        create_file.restype=ctypes.c_void_p
        get_info=kernel.GetFileInformationByHandle
        get_info.argtypes=(ctypes.c_void_p,ctypes.POINTER(_ByHandleFileInformation))
        get_info.restype=ctypes.c_int
        close_handle=kernel.CloseHandle
        close_handle.argtypes=(ctypes.c_void_p,)
        close_handle.restype=ctypes.c_int
        handle=create_file(str(path),0x00000080,0x00000001|0x00000002|0x00000004,
                           None,3,0x02000000|0x00200000,None)
    except Exception:
        raise Reject("HOST_OUTPUT_IDENTITY") from None
    if handle is None or handle==ctypes.c_void_p(-1).value:
        raise Reject("HOST_OUTPUT_IDENTITY")

    try:
        info=_ByHandleFileInformation()
        try:
            opened=bool(get_info(handle,ctypes.byref(info)))
        except Exception:
            opened=False
        need(opened,"HOST_OUTPUT_IDENTITY")
        attributes=info.dwFileAttributes
        need((attributes&0x00000010)!=0 and (attributes&0x00000400)==0,
             "HOST_OUTPUT_IDENTITY")
        filetime_ticks=(int(info.ftCreationTime.dwHighDateTime)<<32)|int(info.ftCreationTime.dwLowDateTime)
        actual_ticks=filetime_ticks+504911232000000000
        need(filetime_ticks>0 and actual_ticks<=3155378975999999999
             and actual_ticks==int(expected_ticks),"HOST_OUTPUT_IDENTITY")
        try:
            no_reparse(path,exists=True)
            final_info=path.lstat()
        except Exception:
            raise Reject("HOST_OUTPUT_IDENTITY") from None
        need(stat.S_ISDIR(final_info.st_mode) and not reparse(final_info),
             "HOST_OUTPUT_IDENTITY")
    finally:
        try:
            closed=bool(close_handle(handle))
        except Exception:
            closed=False
        need(closed,"HOST_OUTPUT_IDENTITY")

def validate_host_launch(raw: bytes, host: dict, pins: dict, pins_rows: dict,
                         pins_sha: str, output_root: Path, parent_process: dict) -> dict:
    doc=parse_document(raw,"HOST_LAUNCH")
    keys={"schema","status","mode","pid","startTicks","executable","executableSha256",
          "script","scriptSha256","inputPinsSha256","outputRoot","outputCreationTicks",
          "compileLimitMs","auditLimitMs","nativeLimitMs","consumerLimitMs","cleanupLimitMs","outerLimitMs"}
    exact_obj(doc,keys,"HOST_LAUNCH_FIELDS")
    host_path=Path(pins["roles"]["process"]["host"])
    ps=Path(pins["toolPaths"]["powershellExe"])
    host_row=pins_rows[normpath(host_path)]
    ps_row=pins_rows[normpath(ps)]
    need(doc["schema"]=="tsj-q100-geometric-prefix-host-launch-v1" and
         doc["status"]=="LAUNCHED_NOT_ACCEPTED" and doc["mode"]==host["mode"] and
         doc["inputPinsSha256"]==pins_sha and doc["outputRoot"]==str(output_root),
         "HOST_LAUNCH_CONTEXT")
    exact_int(doc["pid"],1,2147483647,"HOST_LAUNCH_PID")
    need(type(doc["startTicks"]) is str and doc["startTicks"].isdigit() and int(doc["startTicks"])>0 and
         doc["pid"]==parent_process["ProcessId"] and int(doc["startTicks"])==parent_process["ProcessStartTicks"] and
         doc["executable"]==str(ps) and doc["executableSha256"]==ps_row["sha256"] and
         doc["script"]==str(host_path) and doc["scriptSha256"]==host_row["sha256"],
         "HOST_LAUNCH_IDENTITY")
    ticks=doc["outputCreationTicks"]
    _validate_host_output_creation(output_root,ticks)
    expected={"compileLimitMs":60000,"auditLimitMs":60000,"nativeLimitMs":480000,
              "consumerLimitMs":180000,"cleanupLimitMs":120000,"outerLimitMs":1800000}
    for key,value in expected.items(): exact_int(doc[key],value,value,"HOST_LAUNCH_LIMITS")
    host_identity=host["host"]
    need(host_identity["pid"]==doc["pid"] and host_identity["startTicks"]==doc["startTicks"] and
         host_identity["executable"]==doc["executable"] and
         host_identity["executableSha256"]==doc["executableSha256"] and
         host_identity["script"]==doc["script"] and host_identity["scriptSha256"]==doc["scriptSha256"],
         "HOST_IDENTITY_BINDING")
    return doc

def validate_scratch(raw: bytes, cleanup: dict, host_launch: dict,
                     output_root: Path, suite_id: str) -> Path:
    doc=parse_document(raw,"SCRATCH_INTENT")
    exact_obj(doc,{"status","scratch","hostPid","hostStartTicks","executable","runId"},"SCRATCH_INTENT_FIELDS")
    need(doc["status"]=="INTENT_NOT_CREATION_PROOF" and doc["hostPid"]==host_launch["pid"] and
         doc["hostStartTicks"]==host_launch["startTicks"] and doc["executable"]==host_launch["executable"] and
         doc["runId"]==suite_id,"SCRATCH_INTENT_BINDING")
    scratch=canonical(doc["scratch"],"SCRATCH_PATH",None,exists=False)
    temp=Path(os.path.abspath(tempfile.gettempdir()))
    need(same_path(scratch.parent,temp) and re.fullmatch(
        r"tsj-q100-geometric-prefix-study-r1-[0-9a-f]{32}",scratch.name) is not None,
        "SCRATCH_PATH")
    need(not below(output_root,scratch) and not below(scratch,output_root),"SCRATCH_OUTPUT_ALIAS")
    need(not os.path.lexists(scratch),"SCRATCH_REMAINS")
    no_reparse(scratch,exists=False)
    need(cleanup["scratchPath"]==str(scratch) and type(cleanup["scratchCreationTicks"]) is str and
         cleanup["scratchCreationTicks"].isdigit() and int(cleanup["scratchCreationTicks"])>0 and
         cleanup["scratchRemoved"] is True and exact_int(cleanup["boundMs"],120000,120000) and
         cleanup["formula"]=="nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)",
         "SCRATCH_CLEANUP_BINDING")
    return scratch

def validate_suite_index(index_raw: bytes, footer_raw: bytes, host_result_raw: bytes,
                         host: dict, data: dict[str,bytes], expected_files: set[str],
                         expected_dirs: set[str], marker: dict) -> dict:
    index=strict_line(index_raw,"SUITE_INDEX",MAX_LINE)
    exact_obj(index,{"schema","status","suiteId","mode","inputSha256","sourceSha256","wireSha256",
                     "modeOrder","artifactCount","aggregateBytes","artifacts","directories","layoutQualification"},
              "SUITE_INDEX_FIELDS")
    order=CANARY_MODE_ORDER if host["mode"]=="CANARY" else SUITE_MODE_ORDER
    need(index["schema"]==INDEX_SCHEMA and index["status"]=="HOST_INDEX_COMPLETE" and
         index["suiteId"]==host["suiteId"] and index["mode"]==host["mode"] and
         index["inputSha256"]==host["inputSha256"] and index["sourceSha256"]==host["sourceSha256"] and
         index["wireSha256"]==host["wireSha256"] and index["modeOrder"]==order and
         type(index["modeOrder"]) is list and index["layoutQualification"] is False,
         "SUITE_INDEX_BINDING")
    artifact_paths=sorted(expected_files-{"suite-index.json","suite-footer.json","host-result.json"})
    rows=index["artifacts"]
    need(type(rows) is list and len(rows)==len(artifact_paths),"SUITE_INDEX_CENSUS")
    total=0; op_bytes={k:0 for k in order}
    host_bytes=0
    for row,path in zip(rows,artifact_paths):
        exact_obj(row,{"path","bytes","sha256","role","operation"},"SUITE_ARTIFACT_FIELDS")
        raw=data[path]
        need(row["path"]==path and type(row["bytes"]) is int and row["bytes"]==len(raw) and
             row["sha256"]==sha(raw) and row["role"]==artifact_role(path) and
             row["operation"]==artifact_operation(path),"SUITE_ARTIFACT_BINDING")
        total+=len(raw)
        if row["operation"]=="HOST": host_bytes+=len(raw)
        else: op_bytes[row["operation"]]+=len(raw)
    need(exact_int(index["artifactCount"],len(rows),len(rows),"SUITE_INDEX_ARTIFACT_COUNT") and
         exact_int(index["aggregateBytes"],total,total,"SUITE_INDEX_AGGREGATE") and
         index["directories"]==sorted(expected_dirs),"SUITE_INDEX_TOTAL")
    footer=strict_line(footer_raw,"SUITE_FOOTER",MAX_LINE)
    exact_obj(footer,{"schema","status","suiteId","inputSha256","sourceSha256","wireSha256",
        "modeOrder","artifactCount","indexBytes","indexSha256","aggregateBytes","directoryCount",
        "layoutQualification"},"SUITE_FOOTER_FIELDS")
    need(footer["schema"]==FOOTER_SCHEMA and footer["status"]=="HOST_OUTPUT_CLOSED" and
         footer["suiteId"]==index["suiteId"] and footer["inputSha256"]==index["inputSha256"] and
         footer["sourceSha256"]==index["sourceSha256"] and footer["wireSha256"]==index["wireSha256"] and
         footer["modeOrder"]==order and footer["artifactCount"]==len(rows) and
         footer["indexBytes"]==len(index_raw) and footer["indexSha256"]==sha(index_raw) and
         footer["aggregateBytes"]==total and footer["directoryCount"]==len(expected_dirs) and
         footer["layoutQualification"] is False,"SUITE_FOOTER_BINDING")
    closure=host["outputClosure"]
    exact_obj(closure,{"files","directories","aggregateBytes","hostResultExcluded"},"OUTPUT_CLOSURE_FIELDS")
    closure_names=sorted(expected_files-{"host-result.json"})
    closure_rows=closure["files"]
    need(type(closure_rows) is list and len(closure_rows)==len(closure_names) and
         closure["directories"]==sorted(expected_dirs) and closure["hostResultExcluded"] is True,
         "OUTPUT_CLOSURE_CENSUS")
    closure_bytes=0
    for row,path in zip(closure_rows,closure_names):
        exact_obj(row,{"name","bytes","sha256"},"OUTPUT_CLOSURE_ROW")
        raw=data[path]
        need(row["name"]==path and type(row["bytes"]) is int and row["bytes"]==len(raw) and
             row["sha256"]==sha(raw),"OUTPUT_CLOSURE_HASH")
        closure_bytes+=len(raw)
    need(exact_int(closure["aggregateBytes"],closure_bytes,closure_bytes,"OUTPUT_CLOSURE_BYTES"),
         "OUTPUT_CLOSURE_TOTAL")
    host_total=sum(op_bytes.values())+host_bytes+len(index_raw)+len(footer_raw)+len(host_result_raw)
    host_evidence=(host_bytes+sum(op_bytes[k] for k in order if k not in ("NULL","OBSERVE","CALLBACK_THROW","REASON_THROW"))+
                   len(index_raw)+len(footer_raw)+len(host_result_raw))
    need(all(n<=OP_BYTES for n in op_bytes.values()) and host_total<=SUITE_BYTES and
         host_evidence<=HOST_BYTES,
         "OUTPUT_PARTITION_CAP")
    need(host_total==sum(len(raw) for raw in data.values()) and host_total<=SUITE_BYTES,
         "OUTPUT_TOTAL_CAP")
    for name,path,raw in (("hostResult","host-result.json",host_result_raw),
                          ("suiteIndex","suite-index.json",index_raw),
                          ("suiteFooter","suite-footer.json",footer_raw)):
        exact_ref(marker.get(name),path,raw,"PARENT_MARKER_REF")
    need(host_evidence<=HOST_BYTES,"HOST_EVIDENCE_CAP")
    return {"index":index,"footer":footer,"operationBytes":op_bytes,
            "hostBytes":host_bytes,"hostEvidenceBytes":host_evidence,
            "closureBytes":closure_bytes,"totalBytes":host_total}

def validate_process_row(host: dict, data: dict[str,bytes], role: str,
                         exe: Path, args: list[str], exit_code: int,
                         wall_max: int) -> tuple[dict,float]:
    row=host["processes"].get(role)
    receipt_name,stdout_name,stderr_name,role_max=PROCESS_FILES[role]
    exact_obj(row,{"receipt","stdout","stderr","wallMs"},"HOST_PROCESS_ROW")
    wall=finite_num(row["wallMs"],0,min(wall_max,role_max),"HOST_PROCESS_WALL")
    need(row["receipt"]==receipt_name and row["stdout"]==stdout_name and
         row["stderr"]==stderr_name,"HOST_PROCESS_PATH_BINDING")
    process=validate_process(data[receipt_name],role.upper(),exe,args,exit_code)
    need(data[stdout_name]==process["Stdout"].encode("utf-8") and
         data[stderr_name]==process["Stderr"].encode("utf-8"),"HOST_PROCESS_STREAM_BINDING")
    return process,wall

def parse_auditor_stdout(raw: bytes, pins_sha: str, source_sha: str,
                         extra_files: int, code: str) -> dict:
    prefix=b"PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY "
    need(raw.endswith(b"\n") and raw.count(b"\n")==1 and b"\r" not in raw and
         raw.startswith(prefix) and len(raw)<=MAX_LINE,code+"_MARKER")
    value=parse_json(raw[len(prefix):-1],code+"_JSON")
    exact_obj(value,{"status","pinsSha256","sourceInventorySha256","sourceRows","productionFiles","extraFiles"},code+"_FIELDS")
    need(value["status"]=="PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY" and
         value["pinsSha256"]==pins_sha and value["sourceInventorySha256"]==source_sha and
         exact_int(value["sourceRows"],1084,1084,code+"_SOURCE_ROWS") and
         exact_int(value["productionFiles"],850,850,code+"_PRODUCTION_FILES") and
         exact_int(value["extraFiles"],extra_files,extra_files,code+"_EXTRA_FILES"),code+"_SUMMARY")
    return value

def load_canary_reader(pins: dict, canary: dict) -> Any:
    root=Path(pins["canaryPrerequisite"]["root"])
    reader_path=canonical(str(root/"read_canary.py"),"CANARY_READER_PATH","file")
    rows=canary.get("files")
    need(type(rows) is list,"CANARY_FILES")
    matches=[r for r in rows if type(r) is dict and same_path(r.get("path",""),reader_path)]
    need(len(matches)==1,"CANARY_READER_ROW")
    raw=stable_hash(reader_path,matches[0],4*1024*1024,"CANARY_READER_HASH")
    name="q100_authenticated_canary_reader"
    module=module_from_bytes(raw,reader_path,name)
    need(callable(getattr(module,"validate_native_p6",None)) and
         callable(getattr(module,"validate_native_p8",None)) and
         callable(getattr(module,"expected_compiler_sources",None)),"CANARY_READER_API")
    return module

def validate_native_marker(raw: bytes, prefix: bytes, code: str) -> dict:
    need(type(raw) is bytes and raw.endswith(b"\n") and raw.count(b"\n")==1 and
         b"\r" not in raw and raw.startswith(prefix) and len(raw)<=MAX_LINE,code+"_FRAMING")
    return parse_json(raw[len(prefix):-1],code+"_JSON")

def validate_control_process(host: dict, output_data: dict[str,bytes], pins: dict,
                             pins_path: Path, pins_sha: str, source_sha: str,
                             wire_sha: str, root: Path, role: str,
                             expected_code: str, arg_index: int | None,
                             mode: str, run_id: str, input_sha: str,
                             data_relative: str, expected_classpath: str) -> tuple[dict,float]:
    intent_name={"badMode":"p8-bad-mode-launch-intent.json","badUuid":"p8-bad-uuid-launch-intent.json",
        "badInputSha":"p8-bad-input-sha-launch-intent.json","nonemptyData":"p8-nonempty-data-launch-intent.json"}[role]
    intent=parse_document(output_data[intent_name],"CONTROL_INTENT")
    exact_obj(intent,{"status","executable","sha256","arguments","limitMs"},"CONTROL_INTENT_FIELDS")
    need(intent["status"]=="INTENT_NOT_START_PROOF" and exact_int(intent["limitMs"],480000,480000),
         "CONTROL_INTENT_BINDING")
    java=Path(pins["toolPaths"]["javaHome"])/"bin/java.exe"
    expected_mode=mode
    expected_app=[expected_mode,run_id,input_sha,wire_sha,source_sha,
                  str(root.joinpath(*data_relative.split("/")))]
    if role=="badMode": expected_app[0]="NOT_A_MODE"
    if role=="badUuid": expected_app[1]="NOT_A_UUID"
    if role=="badInputSha": expected_app[2]="not-a-sha"
    expected_args=["-ea","-cp",expected_classpath,
                   "com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy"]+expected_app
    need(intent["executable"]==str(java) and intent["sha256"]==next(r["sha256"] for r in pins["files"] if same_path(r["path"],java))
         and intent["arguments"]==expected_args,"CONTROL_INTENT_ARGUMENTS")
    row=host["processes"][role]
    receipt_name,stdout_name,stderr_name,_=PROCESS_FILES[role]
    # The fixed top-level filenames are pinned independently of process JSON.
    exact_obj(row,{"receipt","stdout","stderr","wallMs"},"CONTROL_PROCESS_ROW")
    need(row["receipt"]==receipt_name and row["stdout"]==stdout_name and row["stderr"]==stderr_name,
         "CONTROL_PROCESS_PATHS")
    wall=finite_num(row["wallMs"],0,480000,"CONTROL_PROCESS_WALL")
    proc=validate_process(output_data[receipt_name],role.upper(),java,expected_args,None)
    need(output_data[stdout_name]==b"" and proc["Stdout"]=="" and proc["Stderr"]==output_data[stderr_name].decode("utf-8","strict"),
         "CONTROL_PROCESS_STREAMS")
    marker=b"P8_GEOMETRIC_PREFIX_PREFLIGHT_REJECT "
    stderr=output_data[stderr_name]
    need(stderr.endswith(b"\n") and stderr.count(b"\n")==1 and b"\r" not in stderr and stderr.startswith(marker),
         "CONTROL_REJECTION_FRAME")
    payload=parse_json(stderr[len(marker):-1],"CONTROL_REJECTION_JSON")
    exact_obj(payload,{"schema","phase","code","argIndex"},"CONTROL_REJECTION_FIELDS")
    need(payload=={"schema":"tsj-canonical-geometric-prefix-preflight-v1","phase":"PREFLIGHT",
                   "code":expected_code,"argIndex":arg_index},"CONTROL_REJECTION_EXPECTATION")
    return proc,wall

def _process_summary(role: str, process: dict, wall: float) -> dict:
    return {"role":role,"exitCode":process["ExitCode"],
            "terminationProven":process["TerminationProven"],"wallMs":wall,
            "processRootAbsent":True}

def _process_stream_binding(data: dict[str,bytes], process: dict,
                            stdout_name: str, stderr_name: str, code: str) -> None:
    try:
        expected_out=process["Stdout"].encode("utf-8","strict")
        expected_err=process["Stderr"].encode("utf-8","strict")
    except UnicodeError:
        raise Reject(code+"_STREAM_UTF8") from None
    need(data[stdout_name]==expected_out and data[stderr_name]==expected_err,
         code+"_STREAM_BINDING")

def _validate_process_role(host: dict, data: dict[str,bytes], role: str,
                           exe: Path, args: list[str], exit_code: int,
                           wall_limit: int) -> tuple[dict,float,dict]:
    process,wall=validate_process_row(host,data,role,exe,args,exit_code,wall_limit)
    return process,wall,_process_summary(role,process,wall)

def _validate_launch_intent(raw: bytes, executable: Path, executable_sha: str,
                            arguments: list[str], limit: int, code: str) -> dict:
    value=parse_document(raw,code)
    exact_obj(value,{"status","executable","sha256","arguments","limitMs"},code+"_FIELDS")
    need(value["status"]=="INTENT_NOT_START_PROOF" and
         value["executable"]==str(executable) and value["sha256"]==executable_sha and
         value["arguments"]==arguments and type(value["arguments"]) is list and
         exact_int(value["limitMs"],limit,limit,code+"_LIMIT"),code+"_BINDING")
    return value

def _validate_host_processes(host: dict, data: dict[str,bytes], pins: dict,
                             pins_rows: dict[str,dict], pins_path: Path, pins_sha: str,
                             source_sha: str, wire_sha: str, output_root: Path,
                             mode: str, suite_id: str, host_launch: dict,
                             scratch: Path, canary_reader: Any, canary: dict,
                             source_rows: list[dict]) -> tuple[dict,dict,dict]:
    expected_roles=PROCESS_KEYS_CANARY if mode=="CANARY" else PROCESS_KEYS_SUITE
    processes=host["processes"]
    need(type(processes) is dict and list(processes)==expected_roles,"HOST_PROCESS_CENSUS")
    by_name={role:processes[role] for role in expected_roles}
    summaries=[]; walls={}; raw_processes={}
    python=Path(pins["toolPaths"]["pythonExe"])
    java_home=Path(pins["toolPaths"]["javaHome"])
    java=java_home/"bin"/"java.exe"
    javac=java_home/"bin"/"javac.exe"
    java_sha=pins_rows[normpath(java)]["sha256"]
    javac_sha=pins_rows[normpath(javac)]["sha256"]
    python_sha=pins_rows[normpath(python)]["sha256"]
    auditor=Path(pins["roles"]["input"]["auditor"])
    audit_args=["-I","-B",str(auditor),str(pins_path),pins_sha]
    expected_summary=run_authenticated_auditor(pins_path,pins_sha,source_sha,pins_rows)

    def consume(role: str, exe: Path, args: list[str], exit_code: int,
                limit: int) -> dict:
        process,wall,summary=_validate_process_role(host,data,role,exe,args,exit_code,limit)
        walls[role]=wall; raw_processes[role]=process; summaries.append(summary)
        return process

    for role in ("preflight","preNative","postSource"):
        process=consume(role,python,audit_args,0,60000)
        need(process["Stderr"]=="","SOURCE_AUDIT_"+role.upper()+"_STDERR")
        record=by_name[role]
        stdout_name=record["stdout"]
        parsed=parse_auditor_stdout(data[stdout_name],pins_sha,source_sha,
                                    len(pins_rows),"SOURCE_AUDIT_"+role.upper())
        source_check=host["sourceChecks"][role]
        exact_obj(source_check,{"status","sourceRows","productionFiles","extraFiles",
                                "pinsSha256","sourceInventorySha256"},"HOST_SOURCE_CHECK_FIELDS")
        expected_host_summary={"status":parsed["status"],"sourceRows":parsed["sourceRows"],
            "productionFiles":parsed["productionFiles"],"extraFiles":parsed["extraFiles"],
            "pinsSha256":parsed["pinsSha256"],"sourceInventorySha256":source_sha}
        need(source_check==expected_host_summary=={
            "status":"PASS_Q100_GEOMETRIC_PREFIX_INPUTS_ONLY","sourceRows":1084,
            "productionFiles":850,"extraFiles":len(pins_rows),"pinsSha256":pins_sha,
            "sourceInventorySha256":source_sha},"HOST_SOURCE_CHECK_BINDING")
        need(parsed==expected_summary,"SOURCE_AUDIT_PHASE_PARITY")

    compiler_launch=parse_document(data["compiler-launch-intent.json"],"COMPILER_INTENT")
    exact_obj(compiler_launch,{"status","executable","sha256","arguments","sourceListPath",
        "sourceListSha256","compilerSourceCount","limitMs"},"COMPILER_INTENT_FIELDS")
    args=compiler_launch["arguments"]
    need(type(args) is list and all(type(x) is str for x in args) and
         len(args)==13 and args[:6]==["-source","7","-target","7","-encoding","UTF-8"] and
         args[6]=="-classpath" and args[8]=="-sourcepath" and args[10]=="-d" and
         args[12].startswith("@"),"COMPILER_ARGUMENT_SHAPE")
    classes=Path(args[11]); empty_source=Path(args[9]); source_list_path=Path(args[12][1:])
    for value,code in ((classes,"COMPILER_CLASSES"),(empty_source,"COMPILER_SOURCEPATH"),
                       (source_list_path,"COMPILER_SOURCE_LIST_PATH")):
        need(value.is_absolute() and str(value)==os.path.abspath(str(value)),code)
    need(same_path(classes.parent,scratch) and classes.name=="classes" and
         same_path(empty_source.parent,scratch) and empty_source.name=="empty-sourcepath" and
         same_path(source_list_path.parent,scratch) and source_list_path.name=="sources.txt",
         "COMPILER_SCRATCH_BINDING")
    cp=args[7].split(";")
    need(len(cp)==3 and same_path(cp[0],classes),"COMPILER_CLASSPATH")
    expected_jars={row["path"]:row for row in source_rows
                   if row["path"].endswith((".tools/gwt-2.7.0/gwt-user-2.7.0.jar",
                                             ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar"))}
    need(len(expected_jars)==2,"COMPILER_GWT_SOURCE_CENSUS")
    user_rel=next(path for path in expected_jars if path.endswith("gwt-user-2.7.0.jar"))
    dev_rel=next(path for path in expected_jars if path.endswith("gwt-dev-2.7.0.jar"))
    user_path=Path(pins["repositoryRoot"]).joinpath(*PurePosixPath(user_rel).parts)
    dev_path=Path(pins["repositoryRoot"]).joinpath(*PurePosixPath(dev_rel).parts)
    need(same_path(cp[1],user_path) and same_path(cp[2],dev_path),"COMPILER_CLASSPATH_BINDING")
    need(compiler_launch["status"]=="INTENT_NOT_START_PROOF" and
         compiler_launch["executable"]==str(javac) and compiler_launch["sha256"]==javac_sha and
         exact_int(compiler_launch["limitMs"],60000,60000,"COMPILER_INTENT_LIMIT") and
         compiler_launch["compilerSourceCount"]==1083 and
         compiler_launch["sourceListPath"]==str(source_list_path) and
         compiler_launch["sourceListSha256"]==sha(data["compiler-sources.txt"]),
         "COMPILER_INTENT_BINDING")
    compiler=host["compiler"]
    exact_obj(compiler,{"sourceCount","sourceList","sourceListBytes","sourceListSha256"},
              "HOST_COMPILER_FIELDS")
    need(compiler["sourceCount"]==1083 and compiler["sourceList"]=="compiler-sources.txt" and
         compiler["sourceListBytes"]==len(data["compiler-sources.txt"]) and
         compiler["sourceListSha256"]==sha(data["compiler-sources.txt"]),"HOST_COMPILER_BINDING")
    bridge_receipt=parse_document(data["bridge-copies.json"],"BRIDGE_RECEIPT")
    need(type(bridge_receipt) is list and len(bridge_receipt)==3,"BRIDGE_RECEIPT_CENSUS")
    can_rows={normpath(Path(row["path"])):row for row in canary["files"]}
    manifest_path=canonical(canary["bridgeManifest"],"CANARY_BRIDGE_MANIFEST","file")
    manifest_raw=stable_hash(manifest_path,can_rows[normpath(manifest_path)],65536,"CANARY_BRIDGE_MANIFEST")
    manifest=parse_json(manifest_raw,"CANARY_BRIDGE_MANIFEST_JSON")
    need(type(manifest) is list and len(manifest)==3,"CANARY_BRIDGE_MANIFEST_CENSUS")
    bridge_map={}
    for row in manifest:
        exact_obj(row,{"path","bytes","sha256"},"CANARY_BRIDGE_ROW")
        need(row["path"] not in bridge_map and type(row["bytes"]) is int and row["bytes"]>0,
             "CANARY_BRIDGE_ROW")
        digest(row["sha256"],"CANARY_BRIDGE_SHA")
        bridge_map[row["path"]]=row
    normalized_bridges=[]
    for row in bridge_receipt:
        exact_obj(row,{"path","bytes","sha256"},"BRIDGE_RECEIPT_ROW")
        need(type(row["path"]) is str and type(row["bytes"]) is int and row["bytes"]>0,
             "BRIDGE_RECEIPT_ROW")
        path=Path(row["path"])
        need(path.is_absolute() and str(path)==os.path.abspath(str(path)) and
             same_path(path.parent,scratch),"BRIDGE_COPY_LOCATION")
        name=path.name
        source_name=name
        source_row=bridge_map.get(source_name)
        need(type(source_row) is dict and row["bytes"]==source_row["bytes"] and
             row["sha256"]==source_row["sha256"],"BRIDGE_COPY_BINDING")
        # The qualified copy paths are recorded after the scratch directory is removed.
        normalized_bridges.append({"path":str(path),"bytes":row["bytes"],"sha256":row["sha256"]})
    need(len({Path(row["path"]).name for row in normalized_bridges})==3 and
         set(Path(row["path"]).name for row in normalized_bridges)==set(bridge_map),
         "BRIDGE_COPY_CENSUS")
    bridge_host=host["bridgeCopies"]
    exact_obj(bridge_host,{"receipt","count","verifiedAfterCompile","verifiedBeforeNative",
                          "verifiedAfterNative"},"HOST_BRIDGE_FIELDS")
    need(bridge_host=={"receipt":"bridge-copies.json","count":3,
        "verifiedAfterCompile":True,"verifiedBeforeNative":True,"verifiedAfterNative":True},
        "HOST_BRIDGE_BINDING")
    reader_info={"pins":canary,"repository":Path(canary["repositoryRoot"]),
                 "sourceRows":canary["sourceRows"]}
    try:
        old_sources=canary_reader.expected_compiler_sources(reader_info,bridge_receipt)
    except Exception:
        raise Reject("CANARY_COMPILER_SOURCE_DERIVATION") from None
    native_sources=[pins["roles"]["native"]["study"],pins["roles"]["native"]["inspector"]]
    expected_paths=[*old_sources,*native_sources]
    need(len(expected_paths)==1083 and len({normpath(p) for p in expected_paths})==1083,
         "COMPILER_SOURCE_CENSUS")
    expected_list="".join('"'+str(path).replace("\\","/")+'"\r\n' for path in expected_paths).encode("utf-8")
    need(data["compiler-sources.txt"]==expected_list,"COMPILER_SOURCE_LIST")
    javac_args=args
    compiler_proc=consume("compiler",javac,javac_args,0,60000)
    need(compiler_proc["TerminationProven"] is True,"COMPILER_TERMINATION")

    p6_args=["-ea","-cp",args[7],"com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy","--canary"]
    p8_args=["-ea","-cp",args[7],"com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary"]
    for role,stem,expected_args in (("p6Canary","p6-canary",p6_args),
                                    ("p8Canary","p8-canary",p8_args)):
        _validate_launch_intent(data[stem+"-launch-intent.json"],java,java_sha,expected_args,
                                480000,stem.upper()+"_LAUNCH")
        process=consume(role,java,expected_args,0,480000)
        need(process["Stderr"]=="",stem.upper()+"_STDERR")
    p6_summary=canary_reader.validate_native_p6(data["p6-canary.stdout"])
    p8_summary=canary_reader.validate_native_p8(data["p8-canary.stdout"])
    p6_host=host["nativeCanaries"]["p6"]
    exact_obj(p6_host,{"schema","status","assertions","blockerAssertions","stageAssertions",
        "elapsedNanos","groups","maximumGroupNanos","layoutQualification"},"HOST_P6_CANARY_FIELDS")
    p6_expected={k:p6_summary[k] for k in p6_host}
    need(p6_host==p6_expected,"HOST_P6_CANARY_BINDING")
    p8_host=host["nativeCanaries"]["p8"]
    exact_obj(p8_host,{"schema","status","assertions","caseCount","workNanos","groups",
        "maximumGroupNanos","totalObservationUnits","layoutQualification"},"HOST_P8_CANARY_FIELDS")
    p8_expected={k:p8_summary[k] for k in p8_host}
    need(p8_host==p8_expected,"HOST_P8_CANARY_BINDING")
    need(p6_summary["layoutQualification"] is False and p8_summary["layoutQualification"] is False,
         "CANARY_LAYOUT_QUALIFICATION")

    input_control_rows=[]
    if type(host["nativeCanaries"].get("inputControls")) is not list or len(host["nativeCanaries"]["inputControls"])!=4:
        raise Reject("HOST_INPUT_CONTROLS_CENSUS")
    control_specs=(
        ("badMode","P8_BAD_MODE","INVALID_MODE",0,"NOT_A_MODE"),
        ("badUuid","P8_BAD_UUID","INVALID_RUN_UUID",1,"NOT_A_UUID"),
        ("badInputSha","P8_BAD_INPUT_SHA","INVALID_INPUT_SHA256",2,"not-a-sha"),
        ("nonemptyData","P8_NONEMPTY_DATA","OUTPUT_DIRECTORY_NOT_EMPTY",5,None),
    )
    for summary_row,(role,name,code,arg_index,variant) in zip(host["nativeCanaries"]["inputControls"],control_specs):
        exact_obj(summary_row,{"name","expectedFailureCode","observedFailureCode","exitCode",
            "terminationProven","dataDirectoryUnchanged"},"HOST_INPUT_CONTROL_ROW")
        need(summary_row["name"]==name and summary_row["expectedFailureCode"]==code and
             summary_row["observedFailureCode"]==code and type(summary_row["exitCode"]) is int and
             summary_row["exitCode"]!=0 and summary_row["terminationProven"] is True and
             summary_row["dataDirectoryUnchanged"] is True,"HOST_INPUT_CONTROL_BINDING")
        mode_val="NOT_A_MODE" if role=="badMode" else "NULL"
        run_val="NOT_A_UUID" if role=="badUuid" else "00000000-0000-4000-8000-000000000008"
        sha_val="not-a-sha" if role=="badInputSha" else pins_sha
        data_rel={"badMode":"controls/P8_BAD_MODE/data","badUuid":"controls/P8_BAD_UUID/data",
                  "badInputSha":"controls/P8_BAD_INPUT_SHA/data",
                  "nonemptyData":"controls/P8_NONEMPTY_DATA/data"}[role]
        process,wall=validate_control_process(host,data,pins,pins_path,pins_sha,source_sha,
            P8_WIRE_SHA,output_root,role,code,arg_index,mode_val,run_val,sha_val,data_rel,args[7])
        walls[role]=wall; raw_processes[role]=process
        summaries.append(_process_summary(role,process,wall))
        need(summary_row["exitCode"]==process["ExitCode"],"HOST_INPUT_CONTROL_EXIT_BINDING")
        input_control_rows.append(summary_row)
    sentinel=b"P8_PREEXISTING_DATA_DO_NOT_CHANGE\n"
    need(data["controls/P8_NONEMPTY_DATA/data/sentinel.txt"]==sentinel,
         "CONTROL_SENTINEL_BYTES")
    need(host["nativeCanaries"]=={"p6":p6_host,"p8":p8_host,
                                  "inputControls":input_control_rows},"HOST_CANARY_KEYS")

    if mode=="SUITE":
        operation_rows=host["operations"]
        need(type(operation_rows) is list and len(operation_rows)==4,"HOST_OPERATION_CENSUS")
        specs=(("NULL","nullMode","runs/NULL/data","COMPLETE"),
               ("OBSERVE","observeMode","runs/OBSERVE/data","COMPLETE"),
               ("CALLBACK_THROW","callbackThrow","runs/CALLBACK_THROW/data","EXPECTED_THROW"),
               ("REASON_THROW","reasonThrow","runs/REASON_THROW/data","EXPECTED_THROW"))
        for row,(op_mode,role,data_rel,disposition) in zip(operation_rows,specs):
            exact_obj(row,{"mode","runId","disposition","processKey","dataDirectory",
                "workNanos","groups","observationUnits"},"HOST_OPERATION_FIELDS")
            need(row["mode"]==op_mode and row["disposition"]==disposition and
                 row["processKey"]==role and row["dataDirectory"]==data_rel and
                 type(row["runId"]) is str and UUID_RE.fullmatch(row["runId"]) is not None,
                 "HOST_OPERATION_BINDING")
            if op_mode=="CALLBACK_THROW":
                need(row["workNanos"] is None,"HOST_CALLBACK_THROW_WORK")
            else:
                exact_int(row["workNanos"],0,MAX_WORK_NS,"HOST_OPERATION_WORK")
            exact_int(row["groups"],0,MAX_GROUPS,"HOST_OPERATION_GROUPS")
            exact_int(row["observationUnits"],0,MAX_UNITS,"HOST_OPERATION_UNITS")
        need(host["nativeCanaries"]=={"p6":p6_host,"p8":p8_host,
              "inputControls":input_control_rows},"HOST_CANARY_KEYS")
    else:
        need(host["operations"]==[],"CANARY_OPERATIONS_EMPTY")
    return {"processes":summaries,"walls":walls,"raw":raw_processes,
            "p6Canary":p6_summary,"p8Canary":p8_summary,
            "operations":host.get("operations",[]),"classpath":args[7],
            "sourceClosure":expected_summary}

def _parent_call(code: str, function: Any, *args: Any) -> Any:
    try:
        return function(*args)
    except Reject:
        raise
    except Exception as error:
        parent_code=getattr(error,"code",None)
        if type(parent_code) is not str:
            parent_code=str(error)
        if re.fullmatch(r"[A-Z0-9_]{1,96}",parent_code or ""):
            raise Reject(code+"_"+parent_code[:72]) from None
        raise Reject(code+"_FAILED") from None

def _p6_normal_operation(root: Path, data: dict[str,bytes], mode: str,
                         operation_row: dict, marker: dict, parent_bundle: dict) -> dict:
    p6=parent_bundle["p6"]; p5=parent_bundle["p5"]; p4=parent_bundle["p4"]
    pins=parent_bundle["pins"]; closure=parent_bundle["closure"]
    parent=parent_bundle["parent"]; run_id=operation_row["runId"]
    base="runs/"+mode+"/data/"
    index_raw=data[base+"canonical-stage-index.json"]
    need(index_raw.endswith(b"\n") and index_raw.count(b"\n")==1 and b"\r" not in index_raw,
         "P6_INDEX_FRAMING")
    index=parse_json(index_raw[:-1],"P6_INDEX_JSON")
    info={"runId":run_id}
    entry,stage_ref=_parent_call("P6_STAGE_INDEX",p6._stage_index,index_raw,index,mode,
                                 info,parent_bundle["pinsSha"],p5,parent)
    receipt_raw=data[base+entry["receipt"]["path"]]
    exact_ref(entry["receipt"],"op-1.jsonl",receipt_raw,"P6_OPERATION_REF")
    operation=_parent_call("P4_OPERATION",p4._operation,receipt_raw,entry,run_id,
                           parent_bundle["pinsSha"],parent["context"]["branchReader"])
    groups=operation["groups"]
    need(type(groups) is list and groups and len(groups)+3<=MAX_GROUPS and
         max(row["elapsedBeforeOwnLineNanos"] for row in groups)<=MAX_GROUP_NS and
         sum(row["elapsedBeforeOwnLineNanos"] for row in groups)<=entry["elapsedToCloseNanos"] and
         max(row["elapsedBeforeOwnLineNanos"] for row in groups)<=entry["maxFullGroupNanos"],
         "P6_OPERATION_GROUP_BUDGET")
    transport_ref=index.get("transport"); attribution_ref=index.get("attribution")
    need(type(transport_ref) is dict and type(attribution_ref) is dict,"P6_INDEX_SIDECARS")
    transport_raw=data[base+transport_ref["path"]]
    attribution_raw=data[base+attribution_ref["path"]]
    stages_raw=data[base+stage_ref["path"]]
    need(transport_ref["path"]=="fixed-prefix.jsonl" and
         transport_ref["bytes"]==len(transport_raw) and
         transport_ref["sha256"]==sha(transport_raw),"P6_TRANSPORT_REF")
    exact_ref(attribution_ref,"prefix-blockers.json",attribution_raw,"P6_ATTRIBUTION_REF")
    exact_ref(stage_ref,"prefix-stages.json",stages_raw,"P6_STAGES_REF")
    facts,diagnosis=_parent_call("P6_CAPTURE_TRANSPORT",p4._capture_transport,
        transport_raw,index,operation,run_id,parent_bundle["pinsSha"])
    decoded=_parent_call("P6_TRANSPORT_DECODE",p4.decode_transport,transport_raw,True)
    need(diagnosis.get("status")=="ZERO_VIA_ROUTE_EXCLUDED" and
         facts.get("prefixGeometrySha256")==PREFIX_SHA and
         index["transport"]["path"]=="fixed-prefix.jsonl" and
         index["transport"]["bytes"]==len(transport_raw) and
         sha(decoded["graphBytes"])==GRAPH_SHA and
         decoded["graphBytes"]==parent["context"]["p4GraphBytes"],"P6_GRAPH_BINDING")
    cut=_parent_call("P5_CUT",p5._validate_cut_document,
        Path(parent["context"]["p5Pins"]["cutInputPath"]),
        p6.P5_INPUT_SHA,decoded["graph"],facts)
    attribution=parse_json(attribution_raw[:-1],"P5_ATTRIBUTION_JSON")
    attribution_totals=_parent_call("P5_ATTRIBUTION",p5._validate_attribution,
        attribution_raw,attribution,"CAPTURE",{"runId":run_id,"workNanos":marker["workNanos"]},
        facts,operation,parent["context"],closure,parent_bundle["pinsSha"],cut)
    need(attribution_totals["copyUnits"]==attribution["totals"]["copyUnits"] and
         attribution_totals["comparisonUnits"]==attribution["totals"]["comparisonUnits"],
         "P5_ATTRIBUTION_TOTALS")
    p5_capture={"receipt":receipt_raw,"facts":facts,"decodedTransport":decoded}
    _parent_call("P5_CONTROL_PARITY",p5._validate_capture_parity,p5_capture,
                 {"receipt":parent["control"]["receipt"]},parent["context"])
    capture_route=_parent_call("P4_CAPTURE_PARITY",p4._compare_capture,receipt_raw,
                               parent["capture"]["receipt"],parent["context"]["branchReader"])
    need(capture_route.get("p07OwnerStepsDelta")==0,"P5_CAPTURE_ROUTE_EQUALITY")
    old_facts=dict(parent["capture"]["facts"]); new_facts=dict(facts)
    for key in ("runId","sourceManifestSha256","captureActiveElapsedMillis"):
        old_facts.pop(key,None); new_facts.pop(key,None)
    need(old_facts==new_facts,"P5_CAPTURE_FACTS_PARITY")
    stage_payload=parse_json(stages_raw[:-1],"P6_STAGE_JSON")
    stage_totals=_parent_call("P6_STAGE_PAYLOAD",p6._validate_stage_payload,
        stages_raw,stage_payload,mode,run_id,parent_bundle["pinsSha"],facts,operation,
        attribution,attribution_totals,cut,marker["workNanos"],p5,
        parent["context"]["branchReader"])
    aggregate=sum(map(len,(index_raw,receipt_raw,transport_raw,attribution_raw,stages_raw)))
    need(aggregate<=p6.LIMITS["artifactBytes"],"P6_ARTIFACT_CAP")
    return {"info":info,"index":index,"indexRaw":index_raw,"entry":entry,
        "receipt":receipt_raw,"operation":operation,"transportRaw":transport_raw,
        "decodedTransport":decoded,"facts":facts,"attributionRaw":attribution_raw,
        "attribution":attribution,"attributionTotals":attribution_totals,
        "cutDocument":cut,"stagesRaw":stages_raw,"stages":stage_payload,
        "stageTotals":stage_totals,"aggregateBytes":aggregate,"wrapperWorkNanos":marker["workNanos"]}

def _parse_throw_marker(stream: str, prefix: str, code: str) -> dict:
    need(type(stream) is str and stream.startswith(prefix) and stream.count(prefix)==1,
         code+"_POSITION")
    first,sep,tail=stream.partition("\n")
    need(bool(sep) and not tail and stream.endswith("\n") and stream.count("\n")==1,
         code+"_LINE_END")
    if first.endswith("\r"): first=first[:-1]
    try: raw=first.encode("utf-8","strict")
    except UnicodeError: raise Reject(code+"_UTF8") from None
    need(len(raw)<=MAX_LINE,code+"_LINE_SIZE")
    return parse_json(raw[len(prefix.encode("ascii")):],code+"_JSON")

def _validate_reason_throw(root: Path, data: dict[str,bytes], host: dict,
                           operation_row: dict, process_bundle: dict,
                           null_run: dict, parent_bundle: dict, pins: dict,
                           pins_sha: str, source_sha: str) -> dict:
    mode="REASON_THROW"; run_id=operation_row["runId"]
    proc=process_bundle["raw"]["reasonThrow"]
    need(proc["ExitCode"]!=0 and proc["Stdout"]=="","REASON_THROW_PROCESS_RESULT")
    marker=_parse_throw_marker(proc["Stderr"],"CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW ",
                               "REASON_THROW_MARKER")
    exact_obj(marker,EXCEPTION_MARKER_FIELDS,"REASON_THROW_MARKER_FIELDS")
    wrapper_work=exact_int(marker["workNanos"],0,MAX_WORK_NS,"REASON_THROW_WORK")
    exact_int(marker["queryOrdinal"],0,0,"REASON_THROW_ORDINAL")
    exact_int(marker["parentOrdinal"],0,0,"REASON_THROW_PARENT_ORDINAL")
    exact_int(marker["suppressedCount"],0,0,"REASON_THROW_SUPPRESSED")
    need(marker["schema"]=="tsj-canonical-geometric-prefix-exception-v1" and
         marker["runId"]==run_id and marker["mode"]==mode and
         marker["inputSha256"]==pins_sha and marker["sourceSha256"]==source_sha and
         marker["wireSha256"]==P8_WIRE_SHA and marker["queryOrdinal"]==0 and
         marker["parentOrdinal"]==0 and marker["sentinel"]=="FIRST_GEOMETRIC_REASON_CALLBACK" and
         marker["exceptionClass"]=="java.lang.IllegalStateException" and
         marker["exceptionMessage"]=="FIRST_GEOMETRIC_REASON_CALLBACK" and
         marker["suppressedCount"]==0 and marker["cleanupKind"]=="FULL_NATIVE_WALL_PLUS_POST_SOURCE" and
         all(marker[k] is True for k in ("cleanupComplete","sourcePreserved","observersDetached",
             "referencesDropped","ownersReleased","contextRestored")),"REASON_THROW_MARKER_BINDING")
    refs=(("partialSidecar","prefix-geometric-reasons.jsonl"),
          ("partialOperationReceipt","op-1.jsonl"),
          ("parentBlockers","prefix-blockers.json"),
          ("parentStages","prefix-stages.json"))
    base="runs/REASON_THROW/data/"; partial={}
    for key,name in refs:
        raw=data[base+name]
        exact_ref(marker[key],name,raw,"REASON_THROW_"+key.upper()+"_REF")
        partial[name]=raw
    p6=parent_bundle["p6"]; p5=parent_bundle["p5"]; parent=parent_bundle["parent"]
    # P5 and P6 complete their normal 22-query observations before the P8 sink
    # interrupts its first fine callback. Their run identity is new; the route,
    # graph, facts and cut are independently taken from the completed BASE NULL.
    null=null_run; null_p6=null["p6"]
    blockers=parse_json(partial["prefix-blockers.json"][:-1],"REASON_THROW_BLOCKERS_JSON")
    p5_totals=_parent_call("REASON_THROW_P5",p5._validate_attribution,
        partial["prefix-blockers.json"],blockers,"CAPTURE",
        {"runId":run_id,"workNanos":wrapper_work},null_p6["facts"],null_p6["operation"],
        parent["context"],parent_bundle["closure"],pins_sha,null_p6["cutDocument"])
    stages_raw=partial["prefix-stages.json"]
    stages=parse_json(stages_raw[:-1],"REASON_THROW_STAGES_JSON")
    p6_totals=_parent_call("REASON_THROW_P6",p6._validate_stage_payload,
        stages_raw,stages,"OBSERVE",run_id,pins_sha,null_p6["facts"],null_p6["operation"],
        blockers,p5_totals,null_p6["cutDocument"],wrapper_work,p5,parent["context"]["branchReader"])
    try:
        partial_prefix,partial_rows=p5._receipt_prefix_through_branch(
            partial["op-1.jsonl"],parent["context"]["branchReader"])
    except Exception as error:
        raise Reject("REASON_THROW_RECEIPT_PREFIX") from None
    need(partial_prefix and all(row.get("record")!="FOOTER" for row in partial_rows),
         "REASON_THROW_RECEIPT_INCOMPLETE")
    throw_info={"runId":run_id,"native":proc,"workNanos":wrapper_work}
    _parent_call("REASON_THROW_RECEIPT_PARITY",p5._compare_partial_to_control,
        partial_rows,null_p6["receipt"],throw_info,pins_sha,parent["context"]["branchReader"])
    reason_context={"mode":mode,"runId":run_id,"inputSha":pins_sha,"sourceSha":source_sha,
        "p6Stage":stages,"stageTotals":p6_totals,"facts":null_p6["facts"],
        "operation":null_p6["operation"],"cut":null_p6["cutDocument"],
        "p5Directions":p5.DIRECTIONS}
    parsed=validate_reason_sidecar(partial["prefix-geometric-reasons.jsonl"],reason_context)
    need(parsed["status"]=="EXPECTED_REASON_THROW_ONLY" and
         parsed["reasonCount"]==1 and parsed["coarseCount"]==0 and
         parsed["footer"]["returnedQueryCount"]==0 and
         parsed["footer"]["totalObservationUnits"]==operation_row["observationUnits"],
         "REASON_THROW_PARTIAL_SIDECAR")
    need(operation_row["workNanos"]==wrapper_work,"REASON_THROW_OPERATION_WORK")
    full_rows=_parent_call("REASON_THROW_FULL_RECEIPT_ROWS",p5._receipt_rows,
        partial["op-1.jsonl"],parent["context"]["branchReader"])
    raw_groups=sum(1 for row in full_rows if row.get("record")=="GROUP")
    actual_groups=raw_groups+3
    need(operation_row["groups"]==raw_groups and actual_groups<=MAX_GROUPS and
         operation_row["observationUnits"]==p6_totals["totalObservationUnits"]+
             parsed["footer"]["p8ObservationUnits"],
         "REASON_THROW_OPERATION_TOTALS")
    return {"mode":mode,"runId":run_id,"status":"EXPECTED_THROW",
            "queryCount":1,"fineEvents":1,"coarseEvents":0,"workNanos":wrapper_work,
            "priorObservationUnits":p6_totals["totalObservationUnits"],
            "p8ObservationUnits":parsed["footer"]["p8ObservationUnits"],
            "totalObservationUnits":parsed["footer"]["totalObservationUnits"],
            "groups":actual_groups,"reason":parsed,"reasonContext":reason_context,
            "reasonRaw":partial["prefix-geometric-reasons.jsonl"],
            "reasonRows":parsed["reasonRows"],"p6":p6_totals,"p5":p5_totals}

def _validate_complete_operation(root: Path, data: dict[str,bytes], host: dict,
                                 operation_row: dict, process_bundle: dict,
                                 parent_bundle: dict, pins_sha: str,
                                 source_sha: str) -> dict:
    mode=operation_row["mode"]; run_id=operation_row["runId"]
    process_key=operation_row["processKey"]
    proc=process_bundle["raw"][process_key]
    need(proc["ExitCode"]==0 and proc["Stderr"]=="","NATIVE_COMPLETE_PROCESS")
    marker=validate_native_marker(proc["Stdout"].encode("utf-8","strict"),
        b"CANONICAL_GEOMETRIC_PREFIX_COMPLETE ","NATIVE_COMPLETE_MARKER")
    exact_obj(marker,COMPLETE_MARKER_FIELDS,"NATIVE_COMPLETE_MARKER_FIELDS")
    need(marker["schema"]=="tsj-canonical-geometric-prefix-complete-v1" and
         marker["mode"]==mode and marker["runId"]==run_id and
         marker["inputSha256"]==pins_sha and marker["sourceSha256"]==source_sha and
         marker["wireSha256"]==P8_WIRE_SHA,"NATIVE_COMPLETE_MARKER_BINDING")
    exact_int(marker["workNanos"],0,MAX_WORK_NS,"NATIVE_COMPLETE_WORK")
    exact_int(marker["aggregateBytes"],0,OP_BYTES,"NATIVE_COMPLETE_AGGREGATE")
    need(operation_row["workNanos"]==marker["workNanos"],"HOST_OPERATION_WORK_BINDING")
    base="runs/"+mode+"/data/"
    side_raw=data[base+"prefix-geometric-reasons.jsonl"]
    p8_index_raw=data[base+"canonical-geometric-prefix-index.json"]
    p6=parent_bundle["p6"]
    p6run=_p6_normal_operation(root,data,mode,operation_row,marker,parent_bundle)
    reason_context={"mode":mode,"runId":run_id,"inputSha":pins_sha,"sourceSha":source_sha,
        "p6Stage":p6run["stages"],"stageTotals":p6run["stageTotals"],
        "facts":p6run["facts"],"operation":p6run["operation"],"cut":p6run["cutDocument"],
        "p5Directions":parent_bundle["p5"].DIRECTIONS}
    parsed=validate_reason_sidecar(side_raw,reason_context)
    p6_index_raw=p6run["indexRaw"]
    parent_aggregate=sum(len(data[base+name]) for name in
        ("op-1.jsonl","fixed-prefix.jsonl","prefix-blockers.json","prefix-stages.json",
         "canonical-stage-index.json"))
    p8_index=validate_reason_index(p8_index_raw,parsed,reason_context,p6_index_raw,
                                   side_raw,parent_aggregate)
    exact_ref(marker["index"],"canonical-geometric-prefix-index.json",p8_index_raw,
              "NATIVE_COMPLETE_INDEX_REF")
    p8_files=NATIVE_FILES[mode]
    aggregate=sum(len(data[base+name]) for name in p8_files)
    need(marker["aggregateBytes"]==aggregate and aggregate<=OP_BYTES and
         p8_index["aggregateBytes"]==parent_aggregate+len(side_raw),
         "NATIVE_COMPLETE_AGGREGATE")
    groups_raw=len(p6run["operation"]["groups"])
    groups=groups_raw+3
    need(operation_row["groups"]==groups_raw and groups<=MAX_GROUPS,
         "HOST_OPERATION_GROUP_BINDING")
    need(operation_row["observationUnits"]==parsed["footer"]["totalObservationUnits"] and
         parsed["footer"]["totalObservationUnits"]<=MAX_UNITS,
         "HOST_OPERATION_UNITS_BINDING")
    return {"mode":mode,"runId":run_id,
        "status":"COMPLETE_NULL_CONTROL_ONLY" if mode=="NULL" else "COMPLETE_REASON_OBSERVATIONS_ONLY",
        "queryCount":parsed["footer"]["queryCount"],"fineEvents":parsed["reasonCount"],
        "coarseEvents":parsed["coarseCount"],"workNanos":marker["workNanos"],
        "priorObservationUnits":parsed["footer"]["priorObservationUnits"],
        "p8ObservationUnits":parsed["footer"]["p8ObservationUnits"],
        "totalObservationUnits":parsed["footer"]["totalObservationUnits"],
        "groups":groups,"reason":parsed,"reasonContext":reason_context,
        "reasonRaw":side_raw,"reasonRows":parsed["reasonRows"],"p6":p6run,
        "parentAggregateBytes":parent_aggregate,"p8Index":p8_index}

def _validate_callback_throw(root: Path, data: dict[str,bytes], operation_row: dict,
                             process_bundle: dict, parent_bundle: dict,
                             pins_sha: str) -> dict:
    proc=process_bundle["raw"]["callbackThrow"]
    need(proc["ExitCode"]!=0 and proc["Stdout"]=="","CALLBACK_THROW_PROCESS_RESULT")
    p6=parent_bundle["p6"]
    marker_prefix="CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW "
    stderr=proc["Stderr"]
    need(type(stderr) is str and stderr.count(marker_prefix)==1 and
         stderr.startswith(marker_prefix),"CALLBACK_THROW_MARKER_PROJECTION_POSITION")
    first,sep,_tail=stderr.partition("\n")
    need(bool(sep),"CALLBACK_THROW_MARKER_PROJECTION_END")
    original_line=first+"\n"
    if first.endswith("\r"):
        first=first[:-1]
    try:
        original_bytes=original_line.encode("utf-8","strict")
        payload_bytes=first[len(marker_prefix):].encode("utf-8","strict")
    except UnicodeError:
        raise Reject("CALLBACK_THROW_MARKER_PROJECTION_UTF8") from None
    need(len(original_bytes)<=MAX_LINE,"CALLBACK_THROW_MARKER_PROJECTION_SIZE")
    marker_preview=parse_json(payload_bytes,"CALLBACK_THROW_MARKER_PROJECTION_JSON")
    need(type(marker_preview) is dict and "inclusiveWorkNanos" in marker_preview,
         "CALLBACK_THROW_MARKER_PROJECTION_FIELDS")
    work=exact_int(marker_preview["inclusiveWorkNanos"],1,MAX_WORK_NS,
                   "CALLBACK_THROW_MARKER_PROJECTION_WORK")
    native_wall=process_bundle["walls"]["callbackThrow"]
    need(work<=native_wall*1_000_000.0,"CALLBACK_THROW_WORK_WALL_BINDING")
    # P8 intentionally gives CALLBACK_THROW no work credit. P6's unchanged
    # inner exception kernel needs a local descriptor: work comes from this
    # native P6 marker and wall from its independently validated process role.
    # This is not a synthesized P6 host receipt or a P8 host work measurement.
    p6_inner_projection={"runId":operation_row["runId"],"native":proc,"workNanos":work,
        "host":{"wrapperWorkNanos":work,
                "nativeWallMs":native_wall}}
    p6pins=dict(parent_bundle["pins"])
    p6pins["nullRunRoot"]=str(root/"runs"/"NULL")
    # pins_sha is already bound to this process's actual argv; P6 binds the
    # marker sourceManifestSha256 to the same value inside its inner kernel.
    loaded=_parent_call("CALLBACK_THROW_VALIDATION",p6._exception_run,
        root/"runs"/"CALLBACK_THROW",p6_inner_projection,p6pins,pins_sha,parent_bundle["parent"],
        parent_bundle["p5"])
    full_rows=_parent_call("CALLBACK_THROW_FULL_RECEIPT_ROWS",parent_bundle["p5"]._receipt_rows,
        loaded["partialRaw"],parent_bundle["parent"]["context"]["branchReader"])
    groups_raw=sum(1 for row in full_rows if row.get("record")=="GROUP")
    groups=groups_raw+3
    prior_units=loaded["stageTotals"]["priorObservationUnits"]
    total_units=loaded["stageTotals"]["totalObservationUnits"]
    need(operation_row["groups"]==groups_raw and groups<=MAX_GROUPS and
         prior_units==0 and operation_row["observationUnits"]==total_units,
         "CALLBACK_THROW_OPERATION_BINDING")
    return {"mode":"CALLBACK_THROW","runId":operation_row["runId"],
        "status":"EXPECTED_THROW","queryCount":len(loaded["stages"].get("queries",[])),
        "fineEvents":None,"coarseEvents":None,"workNanos":None,
        "priorObservationUnits":0,"p8ObservationUnits":None,
        "totalObservationUnits":total_units,
        "groups":groups,"exception":loaded}

def _compare_reason_modes(null_run: dict, observe_run: dict) -> None:
    null_rows=null_run["reason"]["queries"]
    observe_rows=observe_run["reason"]["queries"]
    need(len(null_rows)==len(observe_rows)==14,"P8_MODE_QUERY_CENSUS")
    cleanup_hashes=("queryEffectsBeforeSha256","queryEffectsAfterSha256",
        "sourceEffectsBeforeSha256","sourceEffectsAfterSha256",
        "layoutGeometryBeforeSha256","layoutGeometryAfterSha256")
    for left,right in zip(null_rows,observe_rows):
        need(left["ordinal"]==right["ordinal"] and left["parentOrdinal"]==right["parentOrdinal"] and
             left["identity"]==right["identity"] and left["allowed"]==right["allowed"] and
             left["coarseEvents"]==right["coarseEvents"],"P8_MODE_RESULT_PARITY")
        for field in cleanup_hashes:
            need(left["cleanup"][field]==right["cleanup"][field],"P8_MODE_EFFECT_PARITY")
    need(null_run["p6"]["decodedTransport"]["graphBytes"]==
             observe_run["p6"]["decodedTransport"]["graphBytes"],
         "P8_MODE_PARENT_PARITY")

def _validate_operation_processes(root: Path, data: dict[str,bytes], host: dict,
                                  pins: dict, pins_rows: dict[str,dict],
                                  pins_sha: str, source_sha: str,
                                  process_bundle: dict) -> dict:
    java=Path(pins["toolPaths"]["javaHome"])/"bin"/"java.exe"
    java_sha=pins_rows[normpath(java)]["sha256"]
    mode_order=("NULL","OBSERVE","CALLBACK_THROW","REASON_THROW")
    marker_by_mode={}
    for row in host["operations"]:
        mode=row["mode"]; role=row["processKey"]; run_id=row["runId"]
        data_relative="runs/"+mode+"/data"
        data_path=str(root.joinpath(*data_relative.split("/")))
        if mode=="CALLBACK_THROW":
            app=[mode,run_id,pins_sha,P8_WIRE_SHA,data_path]
            class_name="com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy"
        else:
            app=[mode,run_id,pins_sha,P8_WIRE_SHA,source_sha,data_path]
            class_name="com.lushprojects.circuitjs1.client.CanonicalGeometricPrefixStudy"
        args=["-ea","-cp",process_bundle["classpath"],class_name]+app
        launch_path="runs/"+mode+"/launch-intent.json"
        _validate_launch_intent(data[launch_path],java,java_sha,args,480000,
                                "OPERATION_"+mode+"_LAUNCH")
        expected_exit=1 if mode in ("CALLBACK_THROW","REASON_THROW") else 0
        process,wall,summary=_validate_process_role(host,data,role,java,args,expected_exit,480000)
        process_bundle["raw"][role]=process
        process_bundle["walls"][role]=wall
        process_bundle["processes"].append(summary)
        if mode in ("NULL","OBSERVE"):
            need(process["Stderr"]=="","OPERATION_"+mode+"_STDERR")
            marker=validate_native_marker(process["Stdout"].encode("utf-8","strict"),
                b"CANONICAL_GEOMETRIC_PREFIX_COMPLETE ","OPERATION_"+mode+"_MARKER")
            exact_obj(marker,COMPLETE_MARKER_FIELDS,"OPERATION_MARKER_FIELDS")
            need(marker["schema"]=="tsj-canonical-geometric-prefix-complete-v1" and
                 marker["mode"]==mode and marker["runId"]==run_id and
                 marker["inputSha256"]==pins_sha and marker["sourceSha256"]==source_sha and
                 marker["wireSha256"]==P8_WIRE_SHA and
                 marker["workNanos"]==row["workNanos"],"OPERATION_MARKER_BINDING")
            marker_by_mode[mode]=marker
        else:
            need(process["Stdout"]=="","OPERATION_"+mode+"_STDOUT")
    need(tuple(row["mode"] for row in host["operations"])==mode_order,
         "OPERATION_ORDER")
    return marker_by_mode

def _timing_and_cleanup(host: dict, process_bundle: dict, parent_elapsed: float,
                        mode: str, marker_by_mode: dict) -> dict:
    timing=host["timing"]
    timing_keys={"nativePhaseStartOffsetMs","cleanupStartOffsetMs","nativePhaseElapsedMs",
        "childWallSumMs","workCreditsMs","nativePhaseChargeMs","nativePhaseOverheadMs",
        "hostElapsedBeforeFinalWriteMs"}
    exact_obj(timing,timing_keys,"HOST_TIMING_FIELDS")
    values={k:finite_num(timing[k],0,1800000,"HOST_TIMING_NUMBER") for k in timing_keys}
    start=values["nativePhaseStartOffsetMs"]; cleanup_start=values["cleanupStartOffsetMs"]
    phase=values["nativePhaseElapsedMs"]; child_sum=values["childWallSumMs"]
    credits=values["workCreditsMs"]; reported=values["nativePhaseChargeMs"]
    overhead=values["nativePhaseOverheadMs"]; before_final=values["hostElapsedBeforeFinalWriteMs"]
    need(0<=start<=cleanup_start<=before_final<=parent_elapsed<=1800000,
         "HOST_TIMING_ORDER")
    role_order=["p6Canary","p8Canary","badMode","badUuid","badInputSha","nonemptyData"]
    if mode=="SUITE": role_order += ["nullMode","observeMode","callbackThrow","reasonThrow"]
    actual_child_sum=sum(process_bundle["walls"][r] for r in role_order)
    need(math.isclose(phase,cleanup_start-start,rel_tol=0.0,abs_tol=0.002) and
         math.isclose(child_sum,actual_child_sum,rel_tol=0.0,abs_tol=0.002) and
         phase>=child_sum,"HOST_TIMING_INTERVAL")
    actual_overhead=phase-child_sum
    need(actual_overhead>=0 and math.isclose(overhead,actual_overhead,rel_tol=0.0,abs_tol=0.002),
         "HOST_TIMING_OVERHEAD")
    actual_credits=0.0
    if mode=="SUITE":
        actual_credits=sum(marker_by_mode[m]["workNanos"] for m in ("NULL","OBSERVE"))/1_000_000.0
        for m in ("NULL","OBSERVE"):
            need(marker_by_mode[m]["workNanos"]/1_000_000.0 <=
                 process_bundle["walls"]["nullMode" if m=="NULL" else "observeMode"],
                 "HOST_WORK_CREDIT_WALL")
    need(math.isclose(credits,actual_credits,rel_tol=0.0,abs_tol=0.002),
         "HOST_WORK_CREDITS")
    interval_residual=phase-actual_credits
    need(interval_residual>=0,"HOST_TIMING_NEGATIVE_RESIDUAL")
    role_residuals=0.0
    credit_roles={"nullMode":"NULL","observeMode":"OBSERVE"}
    for role in role_order:
        wall=process_bundle["walls"][role]
        credit=(marker_by_mode[credit_roles[role]]["workNanos"]/1_000_000.0
                if role in credit_roles else 0.0)
        residual=wall-credit
        need(residual>=0,"HOST_ROLE_NEGATIVE_RESIDUAL")
        role_residuals+=residual
    conservative_charge=max(reported,interval_residual,role_residuals)
    need(conservative_charge>=0 and conservative_charge>=role_residuals and
         math.isclose(reported,interval_residual,rel_tol=0.0,abs_tol=0.002),
         "HOST_NATIVE_CHARGE")
    cleanup=host["cleanup"]
    exact_obj(cleanup,{"scratchPath","scratchCreationTicks","scratchRemoved","boundMs","formula"},
              "HOST_CLEANUP_FIELDS")
    exact_int(cleanup["boundMs"],CLEANUP_MS,CLEANUP_MS,"HOST_CLEANUP_BOUND")
    need(cleanup["formula"]=="nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)"
         and cleanup["scratchRemoved"] is True,"HOST_CLEANUP_BINDING")
    tail=parent_elapsed-cleanup_start
    need(tail>=0,"HOST_CLEANUP_NEGATIVE_TAIL")
    upper=conservative_charge+tail
    need(upper<=CLEANUP_MS,"HOST_CLEANUP_UPPER_BOUND")
    return {"boundMs":CLEANUP_MS,"nativePhaseChargeMs":conservative_charge,
            "finalHostTailMs":tail,"upperBoundMs":upper,
            "processRootsAbsent":True,"scratchRemoved":cleanup["scratchRemoved"],
            "cleanupStartOffsetMs":cleanup_start}

def _check_output_disjoint(root: Path, pins: dict, pins_path: Path,
                           parent_path: Path, canary: dict, p6pins: dict) -> None:
    temp=Path(os.path.abspath(tempfile.gettempdir()))
    need(below(root,temp),"OUTPUT_ROOT_TEMP_CONFINEMENT")
    roots=[Path(pins["repositoryRoot"]),Path(pins["canaryPrerequisite"]["root"]),
           Path(p6pins.get("repositoryRoot",".")),Path(p6pins.get("historicalRoot",".")),
           Path(__file__).resolve().parent]
    for candidate in roots:
        candidate=Path(os.path.abspath(os.fspath(candidate)))
        need(not below(root,candidate) and not below(candidate,root),
             "OUTPUT_ROOT_SOURCE_OR_INPUT_OVERLAP")
    for source_file in (pins_path,parent_path):
        source_file=Path(os.path.abspath(os.fspath(source_file)))
        need(not same_path(root,source_file) and not below(source_file,root),
             "OUTPUT_ROOT_INPUT_FILE_OVERLAP")

def _validate_host_and_parent(pins: dict, pins_rows: dict[str,dict], pins_path: Path,
                              pins_sha: str, source_sha: str, wire_sha: str,
                              output_root: Path, parent_path: Path,
                              preliminary: dict, host_raw: bytes,
                              data: dict[str,bytes], expected_files: set,
                              expected_dirs: set) -> tuple[dict,dict,dict,dict]:
    exact_obj(preliminary,{"schema","status","mode","suiteId","inputSha256","sourceSha256",
        "wireSha256","modeOrder","host","processes","sourceChecks","compiler","bridgeCopies",
        "nativeCanaries","operations","timing","cleanup","outputClosure","limits",
        "layoutQualification"},"HOST_RESULT_FIELDS")
    mode=preliminary["mode"]
    need(mode in ("CANARY","SUITE") and preliminary["schema"]==HOST_SCHEMA and
         preliminary["status"]==("HOST_CANARY_COMPLETE_READER_PENDING" if mode=="CANARY"
                                  else "HOST_COMPLETE_READER_PENDING") and
         preliminary["inputSha256"]==pins_sha and preliminary["sourceSha256"]==source_sha and
         preliminary["wireSha256"]==wire_sha==P8_WIRE_SHA and
         preliminary["layoutQualification"] is False,"HOST_RESULT_IDENTITY")
    suite_id=preliminary["suiteId"]
    need(type(suite_id) is str and UUID_RE.fullmatch(suite_id) is not None,
         "HOST_SUITE_ID")
    order=CANARY_MODE_ORDER if mode=="CANARY" else SUITE_MODE_ORDER
    need(type(preliminary["modeOrder"]) is list and preliminary["modeOrder"]==order,
         "HOST_MODE_ORDER")
    host_obj=exact_obj(preliminary["host"],{"pid","startTicks","executable","executableSha256",
        "script","scriptSha256"},"HOST_IDENTITY_FIELDS")
    exact_int(host_obj["pid"],1,2147483647,"HOST_PID")
    need(type(host_obj["startTicks"]) is str and host_obj["startTicks"].isdigit() and
         int(host_obj["startTicks"])>0,"HOST_START_TICKS")
    ps=Path(pins["toolPaths"]["powershellExe"])
    host_script=Path(pins["roles"]["process"]["host"])
    ps_row=pins_rows[normpath(ps)]; script_row=pins_rows[normpath(host_script)]
    need(host_obj["executable"]==str(ps) and host_obj["executableSha256"]==ps_row["sha256"] and
         host_obj["script"]==str(host_script) and host_obj["scriptSha256"]==script_row["sha256"],
         "HOST_IDENTITY_PIN")
    parent_info,parent_raw,parent_elapsed=validate_parent_receipt(
        parent_path,pins,pins_path,pins_sha,output_root,preliminary)
    parent_process=parent_info["process"]
    need(host_obj["pid"]==parent_process["ProcessId"] and
         int(host_obj["startTicks"])==parent_process["ProcessStartTicks"],
         "HOST_PARENT_IDENTITY")
    launch=validate_host_launch(data["host-launch-intent.json"],preliminary,pins,pins_rows,
                                pins_sha,output_root,parent_process)
    cleanup=parse_document(data["scratch-intent.json"],"SCRATCH_INTENT")
    scratch=validate_scratch(data["scratch-intent.json"],preliminary["cleanup"],launch,
                             output_root,suite_id)
    index_footer=validate_suite_index(data["suite-index.json"],data["suite-footer.json"],
        host_raw,preliminary,data,expected_files,expected_dirs,parent_info["marker"])
    limits=preliminary["limits"]
    exact_obj(limits,set(HOST_LIMITS),"HOST_LIMITS_FIELDS")
    for key,value in HOST_LIMITS.items(): exact_int(limits[key],value,value,"HOST_LIMITS")
    need(preliminary["sourceChecks"] is not None and type(preliminary["sourceChecks"]) is dict and
         list(preliminary["sourceChecks"])==["preflight","preNative","postSource"],
         "HOST_SOURCE_CHECKS_CENSUS")
    process_bundle={"host":preliminary,"processes":[],"walls":{},"raw":{}}
    return {"host":preliminary,"mode":mode,"suiteId":suite_id,"order":order,
            "parent":parent_info,"parentRaw":parent_raw,"parentElapsed":parent_elapsed,
            "hostLaunch":launch,"scratch":scratch,"indexFooter":index_footer,
            "limits":limits,"processBundle":process_bundle}

def _recheck_all_inputs(pins_path: Path, pins_raw: bytes, pins_rows: dict[str,dict],
                        inventory_raw: bytes, source_rows: list[dict],
                        canary: dict, parent_bundle: dict | None) -> None:
    need(stable_read(pins_path,MAX_PINS,"FINAL_INPUT_PINS")==pins_raw,
         "FINAL_INPUT_PINS_CHANGED")
    for row in pins_rows.values():
        stable_hash(Path(row["path"]),row,MAX_FILE,"FINAL_INPUT_FILE_CHANGED")
    # Every inherited CANARY direct file that its reader/compiler/bridge proofs
    # depend on is pinned by the immutable prerequisite pins.
    canary_files=canary.get("files")
    need(type(canary_files) is list,"FINAL_CANARY_FILE_CENSUS")
    canary_seen=set()
    for row in canary_files:
        exact_obj(row,FILE_FIELDS,"FINAL_CANARY_FILE_ROW")
        path=canonical(row["path"],"FINAL_CANARY_FILE_PATH","file")
        key=normpath(path); need(key not in canary_seen,"FINAL_CANARY_FILE_DUPLICATE")
        canary_seen.add(key)
        stable_hash(path,row,MAX_FILE,"FINAL_CANARY_FILE_CHANGED")
    for row in source_rows:
        # Source rows are repository-relative and the repository is retained in
        # the authenticated P8 pins closure.
        repo=Path(parent_bundle["baseRepository"] if parent_bundle is not None
                  else canary["repositoryRoot"])
        source_path=repo.joinpath(*PurePosixPath(row["path"]).parts)
        current=stable_read(source_path,MAX_FILE,"FINAL_SOURCE_FILE",allow_empty=True)
        need(len(current)==row["bytes"] and sha(current)==row["sha256"],
             "FINAL_SOURCE_FILE_CHANGED")
    if parent_bundle is not None:
        _parent_call("FINAL_P6_SOURCE_CLOSURE",parent_bundle["p6"]._recheck_inputs,
            parent_bundle["p5"],parent_bundle["pinned"],parent_bundle["pinsPath"],
            parent_bundle["pinsRaw"],parent_bundle["closure"])

def validate_bundle(pins_path: Any, pins_sha: str, output_root: Any,
                    parent_path: Any, return_context: bool = False) -> Any:
    started=time.perf_counter_ns()
    need(type(pins_sha) is str and HEX_RE.fullmatch(pins_sha) is not None,"INPUT_PINS_HASH_FORMAT")
    pins_path=canonical(pins_path,"INPUT_PINS_PATH","file")
    output_root=canonical(output_root,"OUTPUT_ROOT","dir")
    parent_path=canonical(parent_path,"PARENT_RECEIPT_PATH","file")
    pins,pins_raw,pins_rows=validate_input_pins(pins_path,pins_sha)
    python=Path(pins["toolPaths"]["pythonExe"])
    image=actual_image()
    need(same_path(image,python),"READER_PYTHON_IMAGE")
    stable_hash(image,pins_rows[normpath(python)],MAX_FILE,"READER_PYTHON_HASH")
    canary,qualification=parse_canary_prerequisite(pins,pins_rows)
    inventory_raw,source_rows=validate_inventory(pins,pins_rows,canary)
    source_sha=pins["sourceInventory"]["sha256"]
    canary_reader=load_canary_reader(pins,canary)
    p6_path=Path(pins["p6Context"]["pinsPath"])
    p6_raw=stable_hash(p6_path,pins_rows[normpath(p6_path)],MAX_PINS,"P6_CONTEXT_HASH")
    p6_pins=parse_json(p6_raw,"P6_CONTEXT_JSON")
    _check_output_disjoint(output_root,pins,pins_path,parent_path,canary,p6_pins)
    try:
        host_raw=stable_read(output_root/"host-result.json",MAX_LINE,"HOST_RESULT_READ")
    except Reject:
        raise
    preliminary=parse_document(host_raw,"HOST_RESULT")
    expected_files,expected_dirs=expected_output(preliminary.get("mode"))
    data,actual_files,actual_dirs=read_output_tree(output_root,preliminary["mode"])
    need(data["host-result.json"]==host_raw,"HOST_RESULT_CHANGED")
    host_info=_validate_host_and_parent(pins,pins_rows,pins_path,pins_sha,source_sha,
        P8_WIRE_SHA,output_root,parent_path,preliminary,host_raw,data,expected_files,expected_dirs)
    host=host_info["host"]; mode=host_info["mode"]
    process_bundle=host_info["processBundle"]
    # Validate the three audited source snapshots, compiler input closure,
    # canaries, and four Java preflight children against their actual receipts.
    process_result=_validate_host_processes(host,data,pins,pins_rows,pins_path,pins_sha,
        source_sha,P8_WIRE_SHA,output_root,mode,host_info["suiteId"],
        host_info["hostLaunch"],host_info["scratch"],canary_reader,canary,source_rows)
    process_bundle.update(process_result)
    source_closure=process_result["sourceClosure"]
    if mode=="SUITE":
        _validate_operation_processes(output_root,data,host,pins,pins_rows,pins_sha,
                                      source_sha,process_bundle)
    expected_process_roles=PROCESS_KEYS_CANARY if mode=="CANARY" else PROCESS_KEYS_SUITE
    summary_by_role={row["role"]:row for row in process_bundle["processes"]}
    need(set(summary_by_role)==set(expected_process_roles),"HOST_PROCESS_SUMMARY_CENSUS")
    process_bundle["processes"]=[summary_by_role[role] for role in expected_process_roles]
    marker_by_mode={}
    if mode=="SUITE":
        # All normal completion markers are parsed and bound before deriving
        # work credits; throwing calls receive no credits.
        for row in host["operations"]:
            if row["mode"] in ("NULL","OBSERVE"):
                marker=validate_native_marker(
                    process_bundle["raw"][row["processKey"]]["Stdout"].encode("utf-8","strict"),
                    b"CANONICAL_GEOMETRIC_PREFIX_COMPLETE ","OPERATION_MARKER")
                marker_by_mode[row["mode"]]=marker
    semantic_bundle=None
    operation_summaries=[]; reason_counts={}; reason_rows=[]
    parity={"p4Capture":None,"p5Attribution":None,"p6Stages":None,
            "p8ResultEffects":None,"oldThrowPrefix":None,"newThrowPrefix":None}
    reason_contexts={}; reason_raws={}
    if mode=="SUITE":
        semantic_bundle=load_semantic_parent(pins,pins_rows,source_rows)
        semantic_bundle.update({"pinsSha":pins_sha,"pinsPath":pins["p6Context"]["pinsPath"],
            "baseRepository":pins["repositoryRoot"]})
        run_results={}
        for row in host["operations"]:
            if row["mode"] in ("NULL","OBSERVE"):
                result=_validate_complete_operation(output_root,data,host,row,process_bundle,
                                                    semantic_bundle,pins_sha,source_sha)
                run_results[row["mode"]]=result
        null_run=run_results["NULL"]; observe_run=run_results["OBSERVE"]
        p6=semantic_bundle["p6"]; p5=semantic_bundle["p5"]
        _parent_call("P6_CROSS_MODE_PARITY",p6._compare_capture_runs,
                     null_run["p6"],observe_run["p6"],p5,semantic_bundle["parent"])
        _compare_reason_modes(null_run,observe_run)
        callback_row=next(row for row in host["operations"] if row["mode"]=="CALLBACK_THROW")
        callback=_validate_callback_throw(output_root,data,callback_row,process_bundle,
                                          semantic_bundle,pins_sha)
        reason_row=next(row for row in host["operations"] if row["mode"]=="REASON_THROW")
        reason_throw=_validate_reason_throw(output_root,data,host,reason_row,process_bundle,
            null_run,semantic_bundle,pins,pins_sha,source_sha)
        run_results["CALLBACK_THROW"]=callback
        run_results["REASON_THROW"]=reason_throw
        for mode_name in ("NULL","OBSERVE"):
            current=run_results[mode_name]
            reason_contexts[mode_name]=current["reasonContext"]
            reason_raws[mode_name]=current["reasonRaw"]
        counts={}
        for row in observe_run["reasonRows"]:
            for event in row["fineEvents"]:
                counts[event["site"]]=counts.get(event["site"],0)+1
        reason_counts={key:counts[key] for key in sorted(counts)}
        reason_rows=observe_run["reasonRows"]
        for mode_name in ("NULL","OBSERVE"):
            current=run_results[mode_name]
            operation_summaries.append({k:current[k] for k in (
                "mode","runId","status","queryCount","fineEvents","coarseEvents","workNanos",
                "priorObservationUnits","p8ObservationUnits","totalObservationUnits")})
        for mode_name in ("CALLBACK_THROW","REASON_THROW"):
            current=run_results[mode_name]
            operation_summaries.append({k:current[k] for k in (
                "mode","runId","status","queryCount","fineEvents","coarseEvents","workNanos",
                "priorObservationUnits","p8ObservationUnits","totalObservationUnits")})
        parity={"p4Capture":True,"p5Attribution":True,"p6Stages":True,
                "p8ResultEffects":True,"oldThrowPrefix":True,"newThrowPrefix":True}

    cleanup_summary=_timing_and_cleanup(host,process_bundle,host_info["parentElapsed"],
                                        mode,marker_by_mode)
    ordered_roles=PROCESS_KEYS_CANARY if mode=="CANARY" else PROCESS_KEYS_SUITE
    processes=[]
    for role in ordered_roles:
        proc=process_bundle["raw"][role]
        processes.append(_process_summary(role,proc,process_bundle["walls"][role]))
    # Recheck all external inputs and every file in the accepted output tree
    # after semantic validation; no receipt or source path is rewritten.
    _recheck_all_inputs(pins_path,pins_raw,pins_rows,inventory_raw,source_rows,
                        canary,semantic_bundle)
    final_data,final_files,final_dirs=read_output_tree(output_root,mode)
    before_fingerprint=[{"path":path,"bytes":len(raw),"sha256":sha(raw)}
                        for path,raw in sorted(data.items())]
    after_fingerprint=[{"path":path,"bytes":len(raw),"sha256":sha(raw)}
                       for path,raw in sorted(final_data.items())]
    need(final_files==actual_files and final_dirs==actual_dirs and
         after_fingerprint==before_fingerprint,"OUTPUT_CHANGED_DURING_READ")
    need(stable_read(parent_path,MAX_LINE,"FINAL_PARENT_RECEIPT")==host_info["parentRaw"],
         "PARENT_RECEIPT_CHANGED")
    elapsed=(time.perf_counter_ns()-started)/1_000_000.0
    need(math.isfinite(elapsed) and elapsed<=HOST_LIMITS["readerMs"],"READER_WALL_LIMIT")
    summary={"schema":SCHEMA,
        "status":"PASS_Q100_GEOMETRIC_PREFIX_HOST_CANARY_ONLY" if mode=="CANARY"
                 else "PASS_Q100_GEOMETRIC_PREFIX_OBSERVATIONS_ONLY",
        "layoutQualification":False,"inputSha256":pins_sha,"sourceSha256":source_sha,
        "sourceParent":PARENT_SOURCE,"mode":mode,"sourceClosure":source_closure,
        "processes":processes,"operations":operation_summaries,"parity":parity,
        "reasonCounts":reason_counts,"reasonRows":reason_rows,"cleanup":cleanup_summary,
        "outputClosure":{"files":after_fingerprint,"bytes":sum(r["bytes"] for r in after_fingerprint),
                         "unchanged":True},"limits":host_info["limits"]}
    exact_obj(summary,READER_RESULT_FIELDS,"READER_RESULT_SCHEMA")
    need(len(json.dumps(summary,sort_keys=True,separators=(",",":"),ensure_ascii=True,
                        allow_nan=False).encode("ascii"))<=MAX_LINE,"READER_RESULT_SIZE")
    state={"reasonContexts":reason_contexts,"reasonRaws":reason_raws,
           "reasonContext":reason_contexts.get("OBSERVE"),
           "reasonRaw":reason_raws.get("OBSERVE"),
           "outputFingerprint":after_fingerprint}
    if return_context: return summary,state
    return summary

def main() -> int:
    if len(sys.argv)!=5:
        sys.stderr.buffer.write(b"P8_PREFIX_READER_REJECT ARGUMENT_COUNT\n")
        sys.stderr.buffer.flush()
        return 2
    try:
        result=validate_bundle(sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4])
        raw=(json.dumps(result,sort_keys=True,separators=(",",":"),ensure_ascii=True,
                        allow_nan=False)+"\n").encode("ascii")
        need(len(raw)<=MAX_LINE,"READER_RESULT_SIZE")
        sys.stdout.buffer.write(raw); sys.stdout.buffer.flush()
        return 0
    except Reject as error:
        code=error.code if type(error.code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}",error.code) else "UNEXPECTED_FAILURE"
    except Exception:
        code="UNEXPECTED_FAILURE"
    sys.stderr.buffer.write(("P8_PREFIX_READER_REJECT "+code+"\n").encode("ascii"))
    sys.stderr.buffer.flush()
    return 2

if __name__=="__main__":
    raise SystemExit(main())
