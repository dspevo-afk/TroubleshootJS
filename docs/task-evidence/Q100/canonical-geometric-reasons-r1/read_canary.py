"""Strict independent consumer for the focused P8 geometric literal canary."""
from __future__ import annotations

import sys

if not sys.flags.isolated or not sys.dont_write_bytecode:
    sys.stderr.buffer.write(b"P8_CANARY_READER_REJECT PYTHON_ISOLATION_INVALID\n")
    sys.stderr.buffer.flush()
    raise SystemExit(2)

import hashlib
import ctypes
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import time
import types


P8_PARENT = "0891474cadc4b9e00d7911e81c124721a11efb87"
P6_PARENT = "831d69a41d9504bfb845caef6942e9ef58c9d6d2"
P6_PINS_SHA256 = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_BASELINE_SHA256 = "f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019"
P6_AUDITOR_SHA256 = "5ac397cec89917d446a77e77c1c572eabfff28381b080ab1ad4bf800de59134d"
PYTHON_SHA256 = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
JAVA_SHA256 = "e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce"
JAVAC_SHA256 = "61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64"
POWERSHELL_SHA256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
LITERAL_CANARIES_SHA256 = "c2bf8d169102b8d515f55682c63137531adf05dfaafa4880ec41397d26372a7f"

PIN_KEYS = {
    "schema", "repositoryRoot", "parent", "p6PinsPath", "p6PinsSha256",
    "sourceRows", "files", "modeFiles", "bridgeManifest", "bridgeRoot",
    "javaHome", "pythonExe", "hostPath", "auditorPath", "planPath",
    "apiPath", "hostContractPath",
}
P6_SOURCE_ROLES = {
    "router", "support", "writer", "adapter", "blocker", "queries",
    "netRouterOverlay", "stageInspector",
}
MODE_ROLES = (
    "router", "support", "writer", "adapter", "blocker", "queries",
    "stageInspector", "netRouterOverlay", "rulesOverlay", "reasonCanary",
)
SOURCE_ROW_KEYS = {"path", "bytes", "role", "sha256"}
FILE_ROW_KEYS = {"path", "bytes", "sha256"}
REASON_SITES = (
    "START_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION", "DOMAIN_UNKNOWN_NET",
    "DOMAIN_CORRIDOR_OVERLAP", "DOMAIN_MEMBERSHIP_SIDE", "EMPTY_FACE_RESERVATION",
    "ADJACENT_RESERVATION", "PAD_ESCAPE_INTERSECTION", "PROFILED_PAD_TOUCH",
    "COMPONENT_COURTYARD",
)
SCOPE = {
    "R": "SYNTHETIC_ROUTER_LITERAL",
    "I": "INVALID_RULES_INPUT",
    "D": "RULES_BOUNDARY_LITERAL",
    "L": "OBSERVER_LIFECYCLE",
}

# Frozen ordered expectation from LITERAL_CANARIES_R2.md.
LITERAL_CASES = (
    ("START_ESCAPE_REJECT", "R", "START_ESCAPE_DIRECTION", ["START_ESCAPE_DIRECTION"], 1, 1),
    ("START_ESCAPE_ALLOW", "R", None, [], 0, 1),
    ("END_ESCAPE_REJECT", "R", "END_ESCAPE_DIRECTION", ["END_ESCAPE_DIRECTION"], 1, 1),
    ("END_ESCAPE_ALLOW", "R", None, [], 0, 1),
    ("EMPTY_UNRESERVED", "R", None, [], 0, 1),
    ("EMPTY_SAME_OWNER", "R", None, [], 0, 1),
    ("EMPTY_SINGLE_OWNER", "R", "EMPTY_FACE_RESERVATION", ["EMPTY_FACE_RESERVATION"], 1, 1),
    ("EMPTY_MIXED_OWNER", "R", "EMPTY_FACE_RESERVATION", ["EMPTY_FACE_RESERVATION"], 1, 1),
    ("ADJACENT_SAME_OWNER", "R", None, [], 0, 1),
    ("ADJACENT_SINGLE_OWNER", "R", "ADJACENT_RESERVATION", ["ADJACENT_RESERVATION"], 1, 1),
    ("ADJACENT_MIXED_OWNER", "R", "ADJACENT_RESERVATION", ["ADJACENT_RESERVATION"], 1, 1),
    ("RULES_UNKNOWN_NET", "I", "DOMAIN_UNKNOWN_NET", ["DOMAIN_UNKNOWN_NET"], 1, 0),
    ("RULES_CORRIDOR", "D", "DOMAIN_CORRIDOR_OVERLAP", ["DOMAIN_CORRIDOR_OVERLAP"], 1, 0),
    ("RULES_MEMBERSHIP", "D", "DOMAIN_MEMBERSHIP_SIDE", ["DOMAIN_MEMBERSHIP_SIDE"], 1, 0),
    ("ROUTER_CORRIDOR", "R", "DOMAIN_CORRIDOR_OVERLAP", ["DOMAIN_CORRIDOR_OVERLAP"], 1, 1),
    ("ROUTER_MEMBERSHIP", "R", "DOMAIN_MEMBERSHIP_SIDE", ["DOMAIN_MEMBERSHIP_SIDE"], 1, 1),
    ("ROUTER_DOMAIN_ALLOW", "R", None, [], 0, 1),
    ("PAD_ESCAPE_REJECT", "R", "PAD_ESCAPE_INTERSECTION", ["PAD_ESCAPE_INTERSECTION"], 1, 1),
    ("PAD_ESCAPE_ALLOW", "R", None, [], 0, 1),
    ("PROFILED_TOUCH_REJECT", "R", "PROFILED_PAD_TOUCH", ["PROFILED_PAD_TOUCH"], 1, 1),
    ("PROFILED_TOUCH_ALLOW", "R", None, [], 0, 1),
    ("COURTYARD_REJECT", "R", "COMPONENT_COURTYARD", ["COMPONENT_COURTYARD"], 1, 1),
    ("COURTYARD_ALLOW", "R", None, [], 0, 1),
    ("UNDERPASS_ALLOW", "R", None, [], 0, 1),
    ("UNDERPASS_DISABLED", "R", "COMPONENT_COURTYARD", ["COMPONENT_COURTYARD"], 1, 1),
    ("EMPTY_REACHED_ALLOW", "R", None, [], 0, 1),
    ("MULTI_ENDPOINT_ORDER", "R", "END_ESCAPE_DIRECTION",
     ["END_ESCAPE_DIRECTION", "END_ESCAPE_DIRECTION"], 2, 3),
    ("REASON_THROW", "L", "START_ESCAPE_DIRECTION", ["START_ESCAPE_DIRECTION"], 1, 0),
    ("ATTACHMENT_ROLES", "L", None, [], 0, 0),
    ("COPY_DEFAULT_NULL", "L", None, [], 0, 0),
    ("NULL_PARITY", "L", None, [], 0, 1),
)

OUTPUT_FILES = (
    "host-launch.json",
    "scratch-intent.json",
    "source-preflight-process.json", "source-preflight.stdout", "source-preflight.stderr",
    "compiler-launch-intent.json", "compiler-process.json", "compiler.stdout", "compiler.stderr",
    "compiler-sources.txt",
    "bridge-copies.json",
    "source-prenative-process.json", "source-prenative.stdout", "source-prenative.stderr",
    "p6-canary-launch-intent.json", "p6-canary-process.json", "p6-canary.stdout", "p6-canary.stderr",
    "p8-canary-launch-intent.json", "p8-canary-process.json", "p8-canary.stdout", "p8-canary.stderr",
    "source-post-process.json", "source-post.stdout", "source-post.stderr",
)
ALL_OUTPUT_FILES = frozenset((*OUTPUT_FILES, "host-result.json"))
PROCESS_ROLES = {
    "preflight": ("source-preflight-process.json", "source-preflight.stdout", "source-preflight.stderr", 60000),
    "compiler": ("compiler-process.json", "compiler.stdout", "compiler.stderr", 60000),
    "preNative": ("source-prenative-process.json", "source-prenative.stdout", "source-prenative.stderr", 60000),
    "p6Canary": ("p6-canary-process.json", "p6-canary.stdout", "p6-canary.stderr", 480000),
    "p8Canary": ("p8-canary-process.json", "p8-canary.stdout", "p8-canary.stderr", 480000),
    "postSource": ("source-post-process.json", "source-post.stdout", "source-post.stderr", 60000),
}
PROCESS_KEYS = {
    "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
    "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
    "TerminationProven",
}
LIMITS = {
    "compileMs": 60000,
    "nativeMs": 480000,
    "auditMs": 60000,
    "consumerMs": 180000,
    "cleanupMs": 120000,
    "outerMs": 1800000,
    "workNanos": 90000000000,
    "groups": 640,
    "groupNanos": 5000000000,
    "observationUnits": 32768,
    "aggregateBytes": 8388608,
    "lineBytes": 65536,
}
HOST_RESULT_KEYS = {
    "schema", "status", "mode", "inputPinsSha256", "host", "processes",
    "sourceChecks", "compiler", "bridgeCopies", "nativeCanaries", "timing",
    "cleanup", "outputClosure", "limits",
}


class Reject(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(ok: bool, code: str) -> None:
    if not ok:
        raise Reject(code)


def exact_int(value, minimum: int = 0, maximum: int | None = None) -> bool:
    return (type(value) is int and value >= minimum and
            (maximum is None or value <= maximum))


def finite_number(value, minimum: float | None = None,
                  maximum: float | None = None) -> bool:
    if type(value) not in (int, float):
        return False
    number = float(value)
    return (math.isfinite(number) and
            (minimum is None or number >= minimum) and
            (maximum is None or number <= maximum))


def sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def parse_json(raw: bytes, code: str, *, one_lf_line: bool = False):
    need(not raw.startswith(b"\xef\xbb\xbf"), code + "_BOM")
    if one_lf_line:
        need(raw.endswith(b"\n") and raw.count(b"\n") == 1 and b"\r" not in raw,
             code + "_FRAMING")
        body = raw[:-1]
    else:
        body = raw
    try:
        text = body.decode("utf-8", "strict")
    except UnicodeError:
        raise Reject(code + "_UTF8") from None

    def pairs(rows):
        obj = {}
        for key, value in rows:
            if key in obj:
                raise Reject(code + "_DUPLICATE_KEY")
            obj[key] = value
        return obj

    def reject_constant(_value):
        raise Reject(code + "_NONFINITE")

    try:
        return json.loads(text, object_pairs_hook=pairs, parse_constant=reject_constant)
    except Reject:
        raise
    except (ValueError, TypeError, RecursionError):
        raise Reject(code + "_JSON") from None


def no_reparse_ancestors(path: Path, *, include_target: bool = True) -> None:
    absolute = Path(os.path.abspath(str(path)))
    chain = (absolute, *absolute.parents) if include_target else absolute.parents
    for item in chain:
        try:
            info = item.lstat()
        except FileNotFoundError:
            continue
        if (stat.S_ISLNK(info.st_mode) or
                (getattr(info, "st_file_attributes", 0) & 0x400)):
            raise Reject("REPARSE_PATH")


def canonical_path(value, code: str, *, kind: str | None = None,
                   must_exist: bool = True) -> Path:
    need(type(value) is str and bool(value), code)
    path = Path(value)
    need(path.is_absolute() and str(path) == os.path.abspath(value), code)
    no_reparse_ancestors(path, include_target=must_exist)
    if must_exist:
        try:
            resolved = path.resolve(strict=True)
            info = path.stat()
        except (OSError, RuntimeError):
            raise Reject(code) from None
        need(os.path.normcase(str(resolved)) == os.path.normcase(str(path)), code)
        if kind == "file":
            need(stat.S_ISREG(info.st_mode), code)
        elif kind == "dir":
            need(stat.S_ISDIR(info.st_mode), code)
    elif kind == "file":
        need(not path.exists(), code)
    return path


def system_root_directory() -> Path:
    raw_value = os.environ.get("SystemRoot")
    raw_path = Path(raw_value if raw_value is not None else r"C:\Windows")
    need(raw_path.is_absolute() and
         str(raw_path) == os.path.abspath(str(raw_path)), "SYSTEM_ROOT_PATH")
    # Check the environment spelling before resolving it; only case resolution
    # for this OS-derived root is tolerated.
    no_reparse_ancestors(raw_path)
    try:
        absolute = Path(os.path.abspath(str(raw_path)))
        resolved = absolute.resolve(strict=True)
        info = resolved.stat()
    except (OSError, RuntimeError):
        raise Reject("SYSTEM_ROOT_PATH") from None
    need(stat.S_ISDIR(info.st_mode) and
         os.path.normcase(str(absolute)) == os.path.normcase(str(resolved)),
         "SYSTEM_ROOT_PATH")
    return canonical_path(str(resolved), "SYSTEM_ROOT_PATH", kind="dir")


def current_process_image_path() -> Path:
    need(os.name == "nt", "PYTHON_IMAGE_API")
    try:
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        get_module_file_name = kernel32.GetModuleFileNameW
        get_module_file_name.argtypes = [
            ctypes.c_void_p, ctypes.POINTER(ctypes.c_wchar), ctypes.c_uint32]
        get_module_file_name.restype = ctypes.c_uint32
    except (AttributeError, OSError):
        raise Reject("PYTHON_IMAGE_API") from None

    buffer = ctypes.create_unicode_buffer(32768)
    try:
        ctypes.set_last_error(0)
        length = get_module_file_name(None, buffer, len(buffer))
        last_error = ctypes.get_last_error()
    except (OSError, OverflowError, TypeError, ValueError, ctypes.ArgumentError):
        raise Reject("PYTHON_IMAGE_API") from None
    need(type(length) is int and 0 < length < len(buffer) and last_error == 0 and
         len(buffer.value) == length, "PYTHON_IMAGE_API")
    return canonical_path(buffer.value, "PYTHON_IMAGE_PATH", kind="file")


def stable_read(path: Path, maximum: int, code: str) -> bytes:
    no_reparse_ancestors(path)
    try:
        before = path.stat()
        need(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= maximum,
             code + "_SIZE")
        stream = path.open("rb")
    except Reject:
        raise
    except OSError:
        raise Reject(code + "_READ") from None

    chunks: list[bytes] = []
    total = 0
    try:
        opened = os.fstat(stream.fileno())
        need(stat.S_ISREG(opened.st_mode), code + "_SIZE")
        while True:
            chunk = stream.read(min(32768, maximum + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            need(total <= maximum, code + "_SIZE")
            chunks.append(chunk)
        closed = os.fstat(stream.fileno())
        need(stat.S_ISREG(closed.st_mode), code + "_SIZE")
    except Reject:
        raise
    except OSError:
        raise Reject(code + "_READ") from None
    finally:
        stream.close()
    no_reparse_ancestors(path)
    try:
        after = path.stat()
    except OSError:
        raise Reject(code + "_CHANGED") from None
    need(stat.S_ISREG(after.st_mode), code + "_CHANGED")

    def common_identity(info):
        return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns,
                info.st_nlink)

    def api_identity(info):
        return (info.st_ctime_ns, getattr(info, "st_birthtime_ns", None),
                getattr(info, "st_birthtime", None), info.st_mode,
                getattr(info, "st_file_attributes", 0))

    need(all(stat.S_ISREG(info.st_mode) for info in (before, opened, closed, after)) and
         common_identity(before) == common_identity(opened) ==
         common_identity(closed) == common_identity(after) and
         api_identity(before) == api_identity(after) and
         api_identity(opened) == api_identity(closed) and
         total == after.st_size, code + "_CHANGED")
    return b"".join(chunks)


def row_lookup(rows: list[dict], code: str) -> dict[str, dict]:
    result: dict[str, dict] = {}
    previous = None
    for row in rows:
        need(type(row) is dict and set(row) == FILE_ROW_KEYS, code)
        path = canonical_path(row["path"], code + "_PATH", kind="file")
        need(exact_int(row["bytes"], 0, 104857600), code + "_BYTES")
        need(type(row["sha256"]) is str and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None,
             code + "_SHA")
        key = os.path.normcase(str(path))
        need(key not in result, code + "_DUPLICATE")
        folded = str(path).casefold()
        if previous is not None:
            need(previous <= folded, code + "_ORDER")
        previous = folded
        result[key] = row
    return result


def get_row(rows: dict[str, dict], path: Path, code: str) -> dict:
    row = rows.get(os.path.normcase(str(path)))
    need(row is not None, code)
    return row


def verify_pinned_file(rows: dict[str, dict], path: Path, expected_sha: str | None,
                       code: str, maximum: int = 104857600) -> bytes:
    row = get_row(rows, path, code + "_ROW")
    raw = stable_read(path, maximum, code)
    need(len(raw) == row["bytes"] and sha256(raw) == row["sha256"], code + "_PIN")
    if expected_sha is not None:
        need(sha256(raw) == expected_sha, code + "_EXPECTED_HASH")
    return raw


def require_object(value, keys: set[str], code: str) -> dict:
    need(type(value) is dict and set(value) == keys, code)
    return value


def parse_document(raw: bytes, code: str, *, line: bool = True):
    return parse_json(raw, code, one_lf_line=line)


def path_within(root: Path, candidate: Path) -> bool:
    root_n = os.path.normcase(os.path.abspath(str(root))).rstrip("\\/")
    candidate_n = os.path.normcase(os.path.abspath(str(candidate))).rstrip("\\/")
    return (candidate_n == root_n or
            candidate_n.startswith(root_n + os.sep))


def assert_disjoint(root: Path, candidate: Path, code: str) -> None:
    need(not path_within(root, candidate) and not path_within(candidate, root), code)


def parse_input_pins(pins_path: Path, pins_sha: str) -> tuple[dict, dict[str, dict], dict, bytes]:
    pins_raw = stable_read(pins_path, 4194304, "INPUT_PINS")
    need(sha256(pins_raw) == pins_sha, "INPUT_PINS_HASH")
    pins = parse_json(pins_raw, "INPUT_PINS", one_lf_line=False)
    require_object(pins, PIN_KEYS, "INPUT_PINS_SCHEMA")
    need(pins["schema"] == "tsj-q100-geometric-canary-inputs-v1" and
         pins["parent"] == P8_PARENT, "INPUT_PINS_CONTEXT")
    need(type(pins["p6PinsSha256"]) is str and pins["p6PinsSha256"] == P6_PINS_SHA256,
         "P6_PINS_CONTEXT")

    repository = canonical_path(pins["repositoryRoot"], "REPOSITORY_ROOT", kind="dir")
    p6_home = Path(__file__).resolve(strict=True).parent.parent / "canonical-prefix-rejection-stages-r1"
    p6_home = canonical_path(str(p6_home), "P6_ROOT", kind="dir")
    p6_pins_path = canonical_path(pins["p6PinsPath"], "P6_PINS_PATH", kind="file")
    need(p6_pins_path == p6_home / "input-pins-r6.json", "P6_PINS_PATH")

    files = pins["files"]
    need(type(files) is list and 0 < len(files) <= 256, "INPUT_FILE_CENSUS")
    rows = row_lookup(files, "INPUT_FILE_ROW")
    source_rows = pins["sourceRows"]
    need(type(source_rows) is list and len(source_rows) == 1084, "SOURCE_CENSUS")
    source_seen: set[str] = set()
    source_production = 0
    source_base_java = 0
    writer_rows = []
    for row in source_rows:
        need(type(row) is dict and set(row) == SOURCE_ROW_KEYS, "SOURCE_ROW")
        path_text = row["path"]
        need(type(path_text) is str and path_text and "\\" not in path_text,
             "SOURCE_ROW_PATH")
        rel = PurePosixPath(path_text)
        need(not rel.is_absolute() and all(part not in ("", ".", "..") for part in rel.parts) and
             rel.as_posix() == path_text, "SOURCE_ROW_PATH")
        need(exact_int(row["bytes"], 0, 104857600) and type(row["role"]) is str and
             bool(row["role"]) and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None, "SOURCE_ROW")
        key = path_text.casefold()
        need(key not in source_seen, "SOURCE_ROW_DUPLICATE")
        source_seen.add(key)
        if path_text.startswith("src/"):
            source_production += 1
        if row["role"] == "BASE_SOURCE" and path_text.endswith(".java"):
            source_base_java += 1
        if row["role"] == "WRITER":
            writer_rows.append(row)
    need(source_production == 850 and source_base_java == 1074 and len(writer_rows) == 1,
         "SOURCE_CENSUS")
    writer_relative = "tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java"
    need(writer_rows[0]["path"] == writer_relative, "WRITER_SOURCE_PATH")

    mode_files = pins["modeFiles"]
    require_object(mode_files, set(MODE_ROLES), "MODE_FILE_ROLES")
    p6_raw = verify_pinned_file(rows, p6_pins_path, P6_PINS_SHA256, "P6_PINS", 4194304)
    p6_pins = parse_json(p6_raw, "P6_PINS", one_lf_line=False)
    need(type(p6_pins) is dict and p6_pins.get("schema") == "tsj-canonical-prefix-stage-inputs-v1" and
         p6_pins.get("parent") == P6_PARENT and p6_pins.get("changedRows") == [] and
         p6_pins.get("sourceRows") == source_rows, "P6_SOURCE_CONTEXT")
    need(type(p6_pins.get("modeFiles")) is dict and
         set(p6_pins["modeFiles"]) == P6_SOURCE_ROLES, "P6_MODE_FILES")
    for role in ("router", "support", "adapter", "blocker", "queries", "stageInspector"):
        need(mode_files[role] == p6_pins["modeFiles"][role], "P6_MODE_FILE_CONTEXT")
    writer_path = canonical_path(
        os.path.abspath(os.path.join(str(repository), *PurePosixPath(writer_relative).parts)),
        "WRITER_SOURCE_PATH", kind="file")
    need(mode_files["writer"] == str(writer_path), "WRITER_SOURCE_PATH")
    p6_writer_path = canonical_path(p6_pins["modeFiles"]["writer"],
                                    "P6_WRITER_PATH", kind="file")
    p6_writer_source_rows = [row for row in p6_pins["sourceRows"]
                             if type(row) is dict and row.get("path") == writer_relative and
                             row.get("role") == "WRITER"]
    need(len(p6_writer_source_rows) == 1 and
         set(p6_writer_source_rows[0]) == SOURCE_ROW_KEYS,
         "P6_WRITER_SOURCE_CONTEXT")
    p6_file_rows = p6_pins.get("files")
    need(type(p6_file_rows) is list and len(p6_file_rows) <= 256,
         "P6_WRITER_FILE_CONTEXT")
    p6_writer_file_rows = [row for row in p6_file_rows
                           if type(row) is dict and row.get("path") == str(p6_writer_path)]
    need(len(p6_writer_file_rows) == 1 and
         set(p6_writer_file_rows[0]) == FILE_ROW_KEYS,
         "P6_WRITER_FILE_CONTEXT")
    p6_writer_source = p6_writer_source_rows[0]
    p6_writer_file = p6_writer_file_rows[0]
    writer_source = writer_rows[0]
    need(exact_int(p6_writer_file["bytes"], 0, 104857600) and
         type(p6_writer_file["sha256"]) is str and
         re.fullmatch(r"[0-9a-f]{64}", p6_writer_file["sha256"]) is not None and
         p6_writer_source["bytes"] == p6_writer_file["bytes"] == writer_source["bytes"] and
         p6_writer_source["sha256"] == p6_writer_file["sha256"] == writer_source["sha256"],
         "WRITER_SOURCE_CONTEXT")
    writer_raw = stable_read(writer_path, 104857600, "WRITER_SOURCE")
    need(len(writer_raw) == writer_source["bytes"] and
         sha256(writer_raw) == writer_source["sha256"], "WRITER_SOURCE_CONTEXT")
    for role in MODE_ROLES:
        if role == "writer":
            continue
        canonical = canonical_path(mode_files[role], "MODE_FILE_PATH", kind="file")
        get_row(rows, canonical, "MODE_FILE_PIN")

    here = Path(__file__).resolve(strict=True).parent
    expected_local = {
        "hostPath": here / "run-canary.ps1",
        "auditorPath": here / "audit_canary_inputs.py",
        "planPath": here / "PLAN_R1.md",
        "apiPath": here / "API_SCHEMA_R1.md",
        "hostContractPath": here / "CANARY_HOST_R1.md",
    }
    for name, expected in expected_local.items():
        actual = canonical_path(pins[name], "LOCAL_INPUT_PATH", kind="file")
        need(actual == expected, "LOCAL_INPUT_PATH")
        get_row(rows, actual, "LOCAL_INPUT_PIN")

    reader_path = canonical_path(str(Path(__file__).resolve(strict=True)), "READER_SELF_PATH", kind="file")
    auditor_path = Path(pins["auditorPath"])
    host_path = Path(pins["hostPath"])
    reader_raw = verify_pinned_file(rows, reader_path, None, "READER_SELF", 1048576)
    auditor_raw = verify_pinned_file(rows, auditor_path, None, "AUDITOR_SOURCE", 1048576)
    host_raw = verify_pinned_file(rows, host_path, None, "HOST_SOURCE", 1048576)

    python_path = canonical_path(pins["pythonExe"], "PYTHON_PATH", kind="file")
    python_raw = verify_pinned_file(rows, python_path, PYTHON_SHA256, "PYTHON_SOURCE")
    need(python_path == Path(os.path.abspath(pins["pythonExe"])), "PYTHON_PATH")
    # Bind the running image through Win32; sys.executable can be an app-alias path.
    image_path = current_process_image_path()
    need(str(image_path) == str(python_path), "READER_PYTHON_IDENTITY")
    image_raw = verify_pinned_file(rows, image_path, PYTHON_SHA256, "READER_PYTHON_IMAGE")
    need(image_raw == python_raw, "READER_PYTHON_IDENTITY")
    java_home = canonical_path(pins["javaHome"], "JAVA_HOME", kind="dir")
    java_path = canonical_path(str(java_home / "bin" / "java.exe"), "JAVA_PATH", kind="file")
    javac_path = canonical_path(str(java_home / "bin" / "javac.exe"), "JAVAC_PATH", kind="file")
    verify_pinned_file(rows, java_path, JAVA_SHA256, "JAVA_SOURCE")
    verify_pinned_file(rows, javac_path, JAVAC_SHA256, "JAVAC_SOURCE")
    ps_rows = [row for row in files if row["sha256"] == POWERSHELL_SHA256]
    need(len(ps_rows) == 1, "POWERSHELL_PIN")
    system_root = system_root_directory()
    powershell_path = canonical_path(
        str(system_root / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe"),
        "POWERSHELL_PATH", kind="file")
    need(ps_rows[0]["path"] == str(powershell_path), "POWERSHELL_PATH")
    verify_pinned_file(rows, powershell_path, POWERSHELL_SHA256, "POWERSHELL_SOURCE")

    literal_path = here / "LITERAL_CANARIES_R2.md"
    verify_pinned_file(rows, canonical_path(str(literal_path), "LITERAL_PATH", kind="file"),
                       LITERAL_CANARIES_SHA256, "LITERAL_SOURCE", 65536)
    baseline_rows = [row for row in files if row["sha256"] == P6_BASELINE_SHA256]
    p6_auditor_rows = [row for row in files if row["sha256"] == P6_AUDITOR_SHA256]
    need(len(baseline_rows) == 1 and len(p6_auditor_rows) == 1, "P6_EXTRA_PIN")
    verify_pinned_file(rows, Path(baseline_rows[0]["path"]), P6_BASELINE_SHA256, "P6_BASELINE", 4194304)
    verify_pinned_file(rows, Path(p6_auditor_rows[0]["path"]), P6_AUDITOR_SHA256, "P6_AUDITOR", 1048576)

    bridge_manifest = canonical_path(pins["bridgeManifest"], "BRIDGE_MANIFEST_PATH", kind="file")
    bridge_root = canonical_path(pins["bridgeRoot"], "BRIDGE_ROOT", kind="dir")
    need(bridge_manifest.parent == bridge_root, "BRIDGE_CONTEXT")
    bridge_raw = verify_pinned_file(rows, bridge_manifest, None, "BRIDGE_MANIFEST", 65536)
    bridge_rows = parse_json(bridge_raw, "BRIDGE_MANIFEST", one_lf_line=False)
    expected_bridges = [
        ("CirSim.java", 359392, "63b5d7aeb1026ab0855a0bd1a7c5142d53b9e9f4be63344313dbe4af37567812"),
        ("ChipElm.java", 10949, "a01bb797189410fb82f8b87753a783e7ca9d912b9ce99a4c6c96b35365696e24"),
        ("PhysicalSpecificationDeveloperVerifier.java", 264,
         "616b5daea7d17dfc3c4dc125f79f30175a33202fffda2cbddb789a38f53cd5b7"),
    ]
    need(type(bridge_rows) is list and len(bridge_rows) == len(expected_bridges), "BRIDGE_MANIFEST")
    for row, expected in zip(bridge_rows, expected_bridges):
        need(type(row) is dict and set(row) == {"path", "bytes", "sha256"} and
             (row["path"], row["bytes"], row["sha256"]) == expected, "BRIDGE_MANIFEST")
        verify_pinned_file(rows, bridge_root / ("bridge-" + expected[0]), expected[2],
                           "BRIDGE_SOURCE", 1048576)

    return ({
        "pins": pins,
        "rows": rows,
        "sourceRows": source_rows,
        "p6Pins": p6_pins,
        "readerPath": reader_path,
        "readerRaw": reader_raw,
        "auditorPath": auditor_path,
        "auditorRaw": auditor_raw,
        "hostPath": host_path,
        "hostRaw": host_raw,
        "pythonPath": python_path,
        "pythonRaw": python_raw,
        "powershellPath": powershell_path,
        "javaPath": java_path,
        "javacPath": javac_path,
        "bridgeRows": bridge_rows,
        "repository": repository,
        "here": here,
        "pinsSha256": sha256(pins_raw),
    }, rows, p6_pins, pins_raw)


def process_root_paths(process: dict, code: str) -> None:
    for key in ("FilePath", "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot"):
        need(type(process.get(key)) is str, code + "_STRING")
    root_text = process["ProcessRoot"]
    root = Path(root_text)
    temp_root = Path(os.path.abspath(tempfile.gettempdir()))
    expected_parent = temp_root / "TroubleshootJS"
    need(root.is_absolute() and os.path.normcase(str(root.parent)) ==
         os.path.normcase(str(expected_parent)) and
         re.fullmatch(r"verify-process-[0-9a-f]{32}", root.name) is not None,
         code + "_ROOT")
    expected_stdout = root / "stdout.log"
    expected_stderr = root / "stderr.log"
    need(os.path.normcase(process["StdoutLog"]) == os.path.normcase(str(expected_stdout)) and
         os.path.normcase(process["StderrLog"]) == os.path.normcase(str(expected_stderr)),
         code + "_LOG_PATH")
    no_reparse_ancestors(root, include_target=False)
    need(not os.path.lexists(root) and not os.path.lexists(expected_stdout) and
         not os.path.lexists(expected_stderr), code + "_LOG_ROOT_REMAINS")


def validate_process_receipt(raw: bytes, code: str, expected_exe: Path,
                             expected_args: list[str], expected_exit: int = 0) -> dict:
    process = parse_document(raw, code + "_RECEIPT")
    require_object(process, PROCESS_KEYS, code + "_RECEIPT_SHAPE")
    need(process["FilePath"] == str(expected_exe), code + "_EXECUTABLE")
    need(type(process["Arguments"]) is list and process["Arguments"] == expected_args,
         code + "_ARGUMENTS")
    need(exact_int(process["ProcessId"], 1, 2147483647), code + "_PID")
    need(exact_int(process["ProcessStartTicks"], 1, 3155378975999999999), code + "_START_TICKS")
    need(type(process["ExitCode"]) is int and process["ExitCode"] == expected_exit,
         code + "_EXIT")
    need(process["TerminationProven"] is True, code + "_TERMINATION")
    process_root_paths(process, code)
    return process


def check_parent_receipt(parent_path: Path, parent_raw: bytes, pins_info: dict,
                         pins_path: Path, pins_sha: str, output_root: Path,
                         java_home: Path, python_path: Path,
                         host_result_raw: bytes | None = None) -> tuple[dict, float]:
    parent = parse_json(parent_raw, "PARENT_RECEIPT", one_lf_line=False)
    require_object(parent, {"schema", "elapsedMs", "process"}, "PARENT_RECEIPT_SCHEMA")
    need(parent["schema"] == "tsj-q100-geometric-canary-parent-v1", "PARENT_RECEIPT_SCHEMA")
    elapsed = parent["elapsedMs"]
    if not finite_number(elapsed, 0):
        raise Reject("PARENT_ELAPSED_TYPE_INVALID")
    elapsed_ms = float(elapsed)
    need(elapsed_ms <= LIMITS["outerMs"], "PARENT_ELAPSED_LIMIT")

    process = parent["process"]
    require_object(process, PROCESS_KEYS, "PARENT_PROCESS_SCHEMA")
    need(process["FilePath"] == str(pins_info["powershellPath"]), "PARENT_PROCESS_EXECUTABLE_INVALID")
    expected_args = [
        "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(pins_info["hostPath"]),
        "-RepositoryRoot", str(pins_info["repository"]),
        "-InputPinsPath", str(pins_path), "-InputPinsSha256", pins_sha,
        "-OutputRoot", str(output_root), "-JavaHome", str(java_home),
        "-PythonExe", str(python_path),
    ]
    need(type(process["Arguments"]) is list and process["Arguments"] == expected_args,
         "PARENT_PROCESS_ARGUMENTS_INVALID")
    need(exact_int(process["ProcessId"], 1, 2147483647), "PARENT_PROCESS_ID_INVALID")
    need(exact_int(process["ProcessStartTicks"], 1, 3155378975999999999),
         "PARENT_PROCESS_START_INVALID")
    if type(process["ExitCode"]) is not int or process["ExitCode"] != 0:
        raise Reject("PARENT_PROCESS_EXIT_INVALID")
    if process["TerminationProven"] is not True:
        raise Reject("PARENT_PROCESS_TERMINATION_INVALID")
    process_root_paths(process, "PARENT_PROCESS")
    need(process["Stdout"] == "P8_GEOMETRIC_REASON_CANARY_HOST_COMPLETE\r\n" and
         process["Stderr"] == "", "PARENT_PROCESS_STREAMS_INVALID")

    if host_result_raw is not None:
        host = parse_document(host_result_raw, "HOST_RESULT")
        require_object(host, HOST_RESULT_KEYS, "HOST_RESULT_SCHEMA")
        need(type(host["timing"]) is dict and
             "cleanupStartOffsetMs" in host["timing"], "HOST_TIMING_SCHEMA")
        offset = host["timing"]["cleanupStartOffsetMs"]
        need(finite_number(offset, 0), "HOST_TIMING_TYPE_INVALID")
        if elapsed_ms < float(offset):
            raise Reject("PARENT_TIMING_ORDER_INVALID")
    return parent, elapsed_ms


def validate_host_launch(raw: bytes, host: dict, pins_info: dict, pins_sha: str,
                         output_root: Path, parent_process: dict) -> dict:
    doc = parse_document(raw, "HOST_LAUNCH")
    expected_keys = {
        "schema", "status", "mode", "pid", "startTicks", "executable",
        "executableSha256", "script", "scriptSha256", "inputPinsSha256",
        "outputRoot", "outputCreationTicks", "compileLimitMs", "auditLimitMs",
        "nativeLimitMs", "consumerLimitMs", "cleanupLimitMs", "outerLimitMs",
    }
    require_object(doc, expected_keys, "HOST_LAUNCH_SCHEMA")
    need(doc["schema"] == "tsj-q100-geometric-canary-host-launch-v1" and
         doc["status"] == "LAUNCHED_NOT_ACCEPTED" and doc["mode"] == "CANARY" and
         doc["inputPinsSha256"] == pins_sha and doc["outputRoot"] == str(output_root),
         "HOST_LAUNCH_CONTEXT")
    for field in ("pid",):
        need(exact_int(doc[field], 1, 2147483647), "HOST_LAUNCH_IDENTITY")
    need(type(doc["startTicks"]) is str and doc["startTicks"].isdigit() and
         int(doc["startTicks"]) > 0 and int(doc["startTicks"]) == parent_process["ProcessStartTicks"],
         "HOST_LAUNCH_IDENTITY")
    need(doc["pid"] == parent_process["ProcessId"], "HOST_LAUNCH_IDENTITY")
    need(doc["executable"] == str(pins_info["powershellPath"]) and
         doc["executableSha256"] == POWERSHELL_SHA256 and
         doc["script"] == str(pins_info["hostPath"]), "HOST_LAUNCH_SOURCE")
    host_row = get_row(pins_info["rows"], pins_info["hostPath"], "HOST_LAUNCH_SOURCE")
    need(doc["scriptSha256"] == host_row["sha256"], "HOST_LAUNCH_SOURCE")
    need(type(doc["outputCreationTicks"]) is str and doc["outputCreationTicks"].isdigit() and
         int(doc["outputCreationTicks"]) > 0, "HOST_LAUNCH_OUTPUT_IDENTITY")
    expected_limits = {
        "compileLimitMs": 60000, "auditLimitMs": 60000, "nativeLimitMs": 480000,
        "consumerLimitMs": 180000, "cleanupLimitMs": 120000, "outerLimitMs": 1800000,
    }
    for key, expected in expected_limits.items():
        need(exact_int(doc[key]) and doc[key] == expected, "HOST_LAUNCH_LIMITS")
    need(host["pid"] == doc["pid"] and host["startTicks"] == doc["startTicks"] and
         host["executable"] == doc["executable"] and
         host["executableSha256"] == doc["executableSha256"] and
         host["script"] == doc["script"] and host["scriptSha256"] == doc["scriptSha256"],
         "HOST_IDENTITY_MISMATCH")
    return doc


def validate_scratch(raw: bytes, cleanup: dict, host_launch: dict,
                     output_root: Path) -> tuple[Path, dict]:
    doc = parse_document(raw, "SCRATCH_INTENT")
    require_object(doc, {"status", "scratch", "hostPid", "hostStartTicks", "executable", "runId"},
                   "SCRATCH_INTENT_SCHEMA")
    need(doc["status"] == "INTENT_NOT_CREATION_PROOF" and
         doc["hostPid"] == host_launch["pid"] and
         doc["hostStartTicks"] == host_launch["startTicks"] and
         doc["executable"] == host_launch["executable"] and
         type(doc["runId"]) is str and re.fullmatch(
             r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", doc["runId"]),
         "SCRATCH_INTENT_BINDING")
    scratch = canonical_path(doc["scratch"], "SCRATCH_PATH", kind=None, must_exist=False)
    temp_root = Path(os.path.abspath(tempfile.gettempdir()))
    need(scratch.parent == temp_root and
         re.fullmatch(r"tsj-q100-geometric-canary-r1-[0-9a-f]{32}", scratch.name) is not None,
         "SCRATCH_PATH")
    assert_disjoint(output_root, scratch, "SCRATCH_OUTPUT_ALIAS")
    need(not scratch.exists() and not os.path.lexists(scratch), "SCRATCH_REMAINS")
    no_reparse_ancestors(scratch, include_target=False)
    need(cleanup["scratchPath"] == str(scratch) and
         type(cleanup["scratchCreationTicks"]) is str and
         cleanup["scratchCreationTicks"].isdigit() and int(cleanup["scratchCreationTicks"]) > 0,
         "SCRATCH_CLEANUP_IDENTITY")
    return scratch, doc


def validate_auditor_summary(value, pins_sha: str, extra_count: int, code: str) -> dict:
    keys = {"status", "sourceRows", "productionFiles", "extraFiles", "pinsSha256"}
    require_object(value, keys, code)
    need(value["status"] == "PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY" and
         exact_int(value["sourceRows"], 1084, 1084) and
         exact_int(value["productionFiles"], 850, 850) and
         exact_int(value["extraFiles"], extra_count, extra_count) and
         value["pinsSha256"] == pins_sha, code)
    return value


def invoke_authenticated_auditor(pins_info: dict, pins_path: Path,
                                 pins_sha: str) -> tuple[dict, float]:
    started = time.perf_counter_ns()
    raw = pins_info["auditorRaw"]
    name = "_p8_geometric_canary_auditor_" + pins_sha
    need(name not in sys.modules, "AUDITOR_MODULE_COLLISION")
    module = types.ModuleType(name)
    module.__file__ = str(pins_info["auditorPath"])
    module.__package__ = ""
    sys.modules[name] = module
    try:
        code = compile(raw.decode("utf-8", "strict"), str(pins_info["auditorPath"]), "exec")
        exec(code, module.__dict__)
        need(callable(getattr(module, "validate_run", None)), "AUDITOR_API_INVALID")
        result = module.validate_run(str(pins_path), pins_sha)
    except Reject:
        raise
    except Exception as exc:
        auditor_code = getattr(exc, "code", None)
        if type(auditor_code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", auditor_code):
            raise Reject("INPUT_AUDIT_" + auditor_code) from None
        raise Reject("INPUT_AUDIT_FAILED") from None
    finally:
        if sys.modules.get(name) is module:
            del sys.modules[name]
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000.0
    need(math.isfinite(elapsed_ms) and 0 <= elapsed_ms <= 180000.0, "INPUT_AUDIT_WALL_LIMIT")
    return validate_auditor_summary(result, pins_sha, len(pins_info["rows"]),
                                    "INPUT_AUDIT_SUMMARY_INVALID"), elapsed_ms


def validate_source_checks(source_checks: dict, process_summaries: dict,
                           audit_summary: dict) -> None:
    require_object(source_checks, {"preflight", "preNative", "postSource"}, "SOURCE_CHECKS_SCHEMA")
    for role in ("preflight", "preNative", "postSource"):
        summary = validate_auditor_summary(source_checks[role], audit_summary["pinsSha256"],
                                           audit_summary["extraFiles"], "HOST_SOURCE_CHECK")
        need(summary == audit_summary and process_summaries[role] == audit_summary,
             "SOURCE_CHECK_MISMATCH")


def parse_auditor_output(raw: bytes, pins_sha: str, extra_count: int, code: str) -> dict:
    prefix = b"PASS_Q100_GEOMETRIC_CANARY_INPUTS_ONLY "
    need(raw.endswith(b"\n") and raw.count(b"\n") == 1 and b"\r" not in raw and
         raw.startswith(prefix), code + "_MARKER")
    summary = parse_json(raw[len(prefix):-1], code, one_lf_line=False)
    return validate_auditor_summary(summary, pins_sha, extra_count, code + "_SUMMARY")


def validate_native_p6(raw: bytes) -> dict:
    prefix = b"CANONICAL_PREFIX_STAGE_CANARY_COMPLETE "
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeError:
        raise Reject("P6_CANARY_UTF8") from None
    marker = re.compile(r"(?m)^CANONICAL_PREFIX_STAGE_CANARY_COMPLETE (?P<json>\{[^\r\n]*\})\r?$")
    matches = list(marker.finditer(text))
    need(len(matches) == 1, "P6_CANARY_MARKER")
    match = matches[0]
    need(text[match.end():] in ("", "\n", "\r\n"), "P6_CANARY_TRAILING_DATA")
    value = parse_json(match.group("json").encode("utf-8"), "P6_CANARY", one_lf_line=False)
    keys = {"schema", "status", "assertions", "blockerAssertions", "stageAssertions",
            "elapsedNanos", "groups", "maximumGroupNanos", "layoutQualification"}
    require_object(value, keys, "P6_CANARY_SCHEMA")
    need(value["schema"] == "tsj-canonical-prefix-stage-canary-v1" and
         value["status"] == "PASS" and value["layoutQualification"] is False,
         "P6_CANARY_IDENTITY")
    for key in ("assertions", "blockerAssertions", "stageAssertions"):
        need(exact_int(value[key], 1), "P6_CANARY_ASSERTIONS")
    need(exact_int(value["elapsedNanos"], 0, LIMITS["workNanos"]) and
         exact_int(value["groups"], 1, LIMITS["groups"]) and
         exact_int(value["maximumGroupNanos"], 0, LIMITS["groupNanos"]),
         "P6_CANARY_WORK")
    return value


def validate_native_p8(raw: bytes) -> dict:
    prefix = b"P8_GEOMETRIC_REASON_CANARY_COMPLETE "
    need(raw.endswith(b"\n") and raw.count(b"\n") == 1 and b"\r" not in raw and
         raw.startswith(prefix), "P8_CANARY_MARKER")
    value = parse_json(raw[len(prefix):-1], "P8_CANARY", one_lf_line=False)
    keys = {"schema", "status", "assertions", "caseCount", "workNanos", "groups",
            "maximumGroupNanos", "totalObservationUnits", "cases", "layoutQualification"}
    require_object(value, keys, "P8_CANARY_SCHEMA")
    need(value["schema"] == "tsj-q100-geometric-reason-canary-v1" and
         value["status"] == "PASS" and value["layoutQualification"] is False,
         "P8_CANARY_IDENTITY")
    need(exact_int(value["assertions"], 1) and exact_int(value["caseCount"], 31, 31) and
         type(value["cases"]) is list and len(value["cases"]) == 31,
         "P8_CANARY_CENSUS")
    need(exact_int(value["workNanos"], 0, LIMITS["workNanos"]) and
         exact_int(value["groups"], 0, LIMITS["groups"]) and
         exact_int(value["maximumGroupNanos"], 0, LIMITS["groupNanos"]) and
         exact_int(value["totalObservationUnits"], 0, LIMITS["observationUnits"]),
         "P8_CANARY_WORK")
    actual_union: set[str] = set()
    case_keys = {"id", "status", "scope", "expectedSite", "actualSites", "fineEvents",
                 "coarseEvents", "sourcePreserved", "observersDetached", "referencesDropped"}
    for case, expected in zip(value["cases"], LITERAL_CASES):
        case_id, scope_code, expected_site, actual_sites, fine, coarse = expected
        require_object(case, case_keys, "P8_CASE_SCHEMA")
        need(case["id"] == case_id and case["status"] == "PASS" and
             case["scope"] == SCOPE[scope_code] and
             case["expectedSite"] == expected_site and
             type(case["actualSites"]) is list and case["actualSites"] == actual_sites and
             exact_int(case["fineEvents"], 0) and case["fineEvents"] == fine and
             exact_int(case["coarseEvents"], 0) and case["coarseEvents"] == coarse,
             "P8_CASE_EXPECTATION_MISMATCH")
        for flag in ("sourcePreserved", "observersDetached", "referencesDropped"):
            need(case[flag] is True, "P8_CASE_CLEANUP_PROOF")
        for site in case["actualSites"]:
            need(type(site) is str and site in REASON_SITES, "P8_CASE_SITE")
            actual_union.add(site)
        if expected_site is not None:
            need(expected_site in REASON_SITES, "P8_CASE_SITE")
    need(actual_union == set(REASON_SITES), "P8_REASON_SITE_CENSUS")
    return value


def expected_compiler_sources(pins_info: dict, bridge_copies: list[dict]) -> list[str]:
    pins = pins_info["pins"]
    repository = pins_info["repository"]
    excluded = {
        "src/com/lushprojects/circuitjs1/client/CirSim.java",
        "src/com/lushprojects/circuitjs1/client/ChipElm.java",
        "src/com/lushprojects/circuitjs1/client/PhysicalSpecificationDeveloperVerifier.java",
        "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
        "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
        "src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java",
    }
    paths = []
    for row in pins_info["sourceRows"]:
        if row["role"] == "BASE_SOURCE" and row["path"].endswith(".java"):
            if row["path"] not in excluded:
                rel = PurePosixPath(row["path"])
                paths.append(os.path.abspath(os.path.join(str(repository), *rel.parts)))
    for role in MODE_ROLES:
        paths.append(pins["modeFiles"][role])
    for row in bridge_copies:
        paths.append(row["path"])
    need(len(paths) == 1081 and len({os.path.normcase(path) for path in paths}) == 1081,
         "COMPILER_SOURCE_CENSUS")
    return paths


def verify_compiler_source_list(raw: bytes, compiler: dict, pins_info: dict,
                                bridge_copies: list[dict]) -> list[str]:
    need(len(raw) <= 1048576 and compiler["sourceCount"] == 1081 and
         compiler["sourceList"] == "compiler-sources.txt" and
         compiler["sourceListBytes"] == len(raw) and
         compiler["sourceListSha256"] == sha256(raw), "COMPILER_LIST_BINDING")
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeError:
        raise Reject("COMPILER_LIST_UTF8") from None
    need(not text.startswith("\ufeff") and text.endswith("\r\n") and "\n" not in text.replace("\r\n", "") and
         "\r" not in text.replace("\r\n", ""), "COMPILER_LIST_FRAMING")
    lines = text[:-2].split("\r\n")
    paths = expected_compiler_sources(pins_info, bridge_copies)
    expected = b"".join(('"' + path.replace("\\", "/") + '"\r\n').encode("utf-8")
                        for path in paths)
    need(raw == expected and len(lines) == 1081, "COMPILER_LIST_CONTENT")
    return paths


def verify_bridge_receipt(raw: bytes, bridge_summary: dict, pins_info: dict,
                          scratch: Path) -> list[dict]:
    copies = parse_document(raw, "BRIDGE_COPIES")
    need(type(copies) is list and len(copies) == 3, "BRIDGE_COPY_CENSUS")
    if (bridge_summary.get("receipt") != "bridge-copies.json" or
            type(bridge_summary.get("count")) is not int or bridge_summary["count"] != 3 or
            bridge_summary.get("verifiedAfterCompile") is not True or
            bridge_summary.get("verifiedBeforeNative") is not True or
            bridge_summary.get("verifiedAfterNative") is not True):
        raise Reject("BRIDGE_COPY_SUMMARY_INVALID")
    need(len(pins_info["bridgeRows"]) == 3, "BRIDGE_SOURCE_CENSUS")
    result = []
    for copy, source in zip(copies, pins_info["bridgeRows"]):
        require_object(copy, {"path", "bytes", "sha256"}, "BRIDGE_COPY_ROW")
        expected_name = source["path"]
        expected_path = scratch / expected_name
        need(copy["path"] == str(expected_path) and
             exact_int(copy["bytes"], source["bytes"], source["bytes"]) and
             copy["sha256"] == source["sha256"], "BRIDGE_COPY_ROW")
        result.append(copy)
    return result


def verify_intent(raw: bytes, expected: dict, code: str) -> dict:
    value = parse_document(raw, code)
    require_object(value, set(expected), code + "_SCHEMA")
    for key, wanted in expected.items():
        need(value[key] == wanted and type(value[key]) is type(wanted), code + "_MISMATCH")
    return value


def validate_processes(host: dict, output_data: dict[str, bytes], pins_info: dict,
                       pins_path: Path, pins_sha: str, output_root: Path,
                       scratch: Path) -> tuple[dict, dict, dict]:
    processes = host["processes"]
    require_object(processes, set(PROCESS_ROLES), "HOST_PROCESSES_SCHEMA")
    pins = pins_info["pins"]
    python = pins_info["pythonPath"]
    java = pins_info["javaPath"]
    javac = pins_info["javacPath"]
    source_summary = {}
    native_summaries = {}
    receipt_summaries = {}

    source_roles = ("preflight", "preNative", "postSource")
    for role in source_roles:
        receipt_name, stdout_name, stderr_name, maximum_wall = PROCESS_ROLES[role]
        row = processes[role]
        require_object(row, {"receipt", "stdout", "stderr", "wallMs"}, "HOST_PROCESS_ROW")
        need(row["receipt"] == receipt_name and row["stdout"] == stdout_name and
             row["stderr"] == stderr_name and finite_number(row["wallMs"], 0, maximum_wall),
             "HOST_PROCESS_ROW")
        args = ["-I", "-B", str(pins["auditorPath"]), str(pins_path), pins_sha]
        process = validate_process_receipt(output_data[receipt_name], role.upper(), python, args)
        stdout_raw = output_data[stdout_name]
        stderr_raw = output_data[stderr_name]
        need(stdout_raw == process["Stdout"].encode("utf-8") and
             stderr_raw == process["Stderr"].encode("utf-8") and process["Stderr"] == "",
             role.upper() + "_STREAM_BINDING")
        source_summary[role] = parse_auditor_output(stdout_raw, pins_sha, len(pins_info["rows"]),
                                                    role.upper() + "_AUDIT")
        receipt_summaries[role] = {
            "processId": process["ProcessId"],
            "processStartTicks": process["ProcessStartTicks"],
            "wallMs": float(row["wallMs"]),
            "exitCode": process["ExitCode"],
            "terminationProven": process["TerminationProven"],
        }

    # Compiler and native launch evidence is validated after its intents are independently decoded.
    compiler_row = processes["compiler"]
    require_object(compiler_row, {"receipt", "stdout", "stderr", "wallMs"}, "HOST_PROCESS_ROW")
    need(compiler_row["receipt"] == "compiler-process.json" and
         compiler_row["stdout"] == "compiler.stdout" and compiler_row["stderr"] == "compiler.stderr" and
         finite_number(compiler_row["wallMs"], 0, 60000), "HOST_PROCESS_ROW")
    p6_row = processes["p6Canary"]
    p8_row = processes["p8Canary"]
    for role, row in (("p6Canary", p6_row), ("p8Canary", p8_row)):
        receipt_name, stdout_name, stderr_name, maximum_wall = PROCESS_ROLES[role]
        require_object(row, {"receipt", "stdout", "stderr", "wallMs"}, "HOST_PROCESS_ROW")
        need(row["receipt"] == receipt_name and row["stdout"] == stdout_name and
             row["stderr"] == stderr_name and finite_number(row["wallMs"], 0, maximum_wall),
             "HOST_PROCESS_ROW")

    compiler_intent = parse_document(output_data["compiler-launch-intent.json"], "COMPILER_INTENT")
    require_object(compiler_intent, {"status", "executable", "sha256", "arguments",
        "sourceListPath", "sourceListSha256", "compilerSourceCount", "limitMs"},
        "COMPILER_INTENT_SCHEMA")
    source_list_path = str(scratch / "sources.txt")
    classes = str(scratch / "classes")
    empty_source = str(scratch / "empty-sourcepath")
    repo = pins_info["repository"]
    gwt_user = str(repo / ".tools" / "gwt-2.7.0" / "gwt-user-2.7.0.jar")
    gwt_dev = str(repo / ".tools" / "gwt-2.7.0" / "gwt-dev-2.7.0.jar")
    classpath = ";".join((classes, gwt_user, gwt_dev))
    compiler_args = ["-source", "7", "-target", "7", "-encoding", "UTF-8",
                     "-classpath", classpath, "-sourcepath", empty_source,
                     "-d", classes, "@" + source_list_path]
    need(compiler_intent["status"] == "INTENT_NOT_START_PROOF" and
         compiler_intent["executable"] == str(javac) and
         compiler_intent["sha256"] == JAVAC_SHA256 and
         compiler_intent["arguments"] == compiler_args and
         compiler_intent["sourceListPath"] == source_list_path and
         compiler_intent["sourceListSha256"] == host["compiler"]["sourceListSha256"] and
         exact_int(compiler_intent["compilerSourceCount"], 1081, 1081) and
         exact_int(compiler_intent["limitMs"], 60000, 60000), "COMPILER_INTENT_INVALID")
    compiler_process = validate_process_receipt(output_data["compiler-process.json"],
        "COMPILER", javac, compiler_args)
    need(output_data["compiler.stdout"] == compiler_process["Stdout"].encode("utf-8") and
         output_data["compiler.stderr"] == compiler_process["Stderr"].encode("utf-8"),
         "COMPILER_STREAM_BINDING")
    receipt_summaries["compiler"] = {
        "processId": compiler_process["ProcessId"],
        "processStartTicks": compiler_process["ProcessStartTicks"],
        "wallMs": float(compiler_row["wallMs"]),
        "exitCode": compiler_process["ExitCode"],
        "terminationProven": compiler_process["TerminationProven"],
        "stderrBytes": len(output_data["compiler.stderr"]),
    }

    expected_classpath = classpath
    p6_args = ["-ea", "-cp", expected_classpath,
               "com.lushprojects.circuitjs1.client.CanonicalPrefixStageStudy", "--canary"]
    p8_args = ["-ea", "-cp", expected_classpath,
               "com.lushprojects.circuitjs1.client.CanonicalGeometricReasonCanary"]
    for role, exe, args, intent_name, process_name, max_wall in (
        ("p6Canary", java, p6_args, "p6-canary-launch-intent.json", "p6-canary-process.json", 480000),
        ("p8Canary", java, p8_args, "p8-canary-launch-intent.json", "p8-canary-process.json", 480000),
    ):
        native_row = processes[role]
        receipt_name, expected_stdout, expected_stderr, role_wall_limit = PROCESS_ROLES[role]
        require_object(native_row, {"receipt", "stdout", "stderr", "wallMs"},
                       "HOST_PROCESS_ROW")
        need(native_row["receipt"] == receipt_name == process_name and
             native_row["stdout"] == expected_stdout and
             native_row["stderr"] == expected_stderr and
             finite_number(native_row["wallMs"], 0, min(max_wall, role_wall_limit)),
             "HOST_PROCESS_ROW")
        intent = parse_document(output_data[intent_name], role.upper() + "_INTENT")
        require_object(intent, {"status", "executable", "sha256", "arguments", "limitMs"},
                       role.upper() + "_INTENT_SCHEMA")
        need(intent["status"] == "INTENT_NOT_START_PROOF" and
             intent["executable"] == str(java) and intent["sha256"] == JAVA_SHA256 and
             intent["arguments"] == args and exact_int(intent["limitMs"], max_wall, max_wall),
             role.upper() + "_INTENT_INVALID")
        process = validate_process_receipt(output_data[process_name], role.upper(), java, args)
        stdout_name = PROCESS_ROLES[role][1]
        stderr_name = PROCESS_ROLES[role][2]
        need(output_data[stdout_name] == process["Stdout"].encode("utf-8") and
             output_data[stderr_name] == process["Stderr"].encode("utf-8") and
             process["Stderr"] == "", role.upper() + "_STREAM_BINDING")
        if role == "p6Canary":
            native_summaries["p6"] = validate_native_p6(output_data[stdout_name])
        else:
            native_summaries["p8"] = validate_native_p8(output_data[stdout_name])
        receipt_summaries[role] = {
            "processId": process["ProcessId"],
            "processStartTicks": process["ProcessStartTicks"],
            "wallMs": float(native_row["wallMs"]),
            "exitCode": process["ExitCode"],
            "terminationProven": process["TerminationProven"],
        }

    return source_summary, native_summaries, receipt_summaries


def validate_host_result(raw: bytes, pins_info: dict, pins_sha: str,
                         parent: dict, elapsed_ms: float,
                         output_root: Path, output_data: dict[str, bytes],
                         audit_summary: dict, host_launch: dict) -> dict:
    host = parse_document(raw, "HOST_RESULT")
    require_object(host, HOST_RESULT_KEYS, "HOST_RESULT_SCHEMA")
    need(host["schema"] == "tsj-q100-geometric-canary-host-v1" and
         host["status"] == "HOST_CANARY_COMPLETE_READER_PENDING" and
         host["mode"] == "CANARY" and host["inputPinsSha256"] == pins_sha,
         "HOST_RESULT_IDENTITY")

    host_identity = host["host"]
    require_object(host_identity, {"pid", "startTicks", "executable", "executableSha256", "script", "scriptSha256"},
                   "HOST_IDENTITY_SCHEMA")
    need(exact_int(host_identity["pid"], 1, 2147483647) and
         type(host_identity["startTicks"]) is str and host_identity["startTicks"].isdigit() and
         int(host_identity["startTicks"]) == parent["process"]["ProcessStartTicks"] and
         host_identity["pid"] == parent["process"]["ProcessId"] and
         host_identity["executable"] == str(pins_info["powershellPath"]) and
         host_identity["executableSha256"] == POWERSHELL_SHA256 and
         host_identity["script"] == str(pins_info["hostPath"]), "HOST_IDENTITY_INVALID")
    host_row = get_row(pins_info["rows"], pins_info["hostPath"], "HOST_SCRIPT_PIN")
    need(host_identity["scriptSha256"] == host_row["sha256"], "HOST_SCRIPT_HASH")

    timing = host["timing"]
    timing_keys = {"nativePhaseStartOffsetMs", "cleanupStartOffsetMs", "nativePhaseChargeMs",
                   "p6CanaryWallMs", "p8CanaryWallMs", "nativePhaseOverheadMs"}
    require_object(timing, timing_keys, "HOST_TIMING_SCHEMA")
    for key in timing_keys:
        need(finite_number(timing[key], 0), "HOST_TIMING_TYPE_INVALID")
    start = float(timing["nativePhaseStartOffsetMs"])
    cleanup_start = float(timing["cleanupStartOffsetMs"])
    charge = float(timing["nativePhaseChargeMs"])
    p6_wall = float(timing["p6CanaryWallMs"])
    p8_wall = float(timing["p8CanaryWallMs"])
    overhead = float(timing["nativePhaseOverheadMs"])
    if not (0 <= start <= cleanup_start <= elapsed_ms):
        raise Reject("PARENT_TIMING_ORDER_INVALID")
    tolerance_ms = 0.002
    need(abs(charge - (cleanup_start - start)) <= tolerance_ms and
         charge + tolerance_ms >= p6_wall + p8_wall and
         overhead >= 0 and abs(overhead - (charge - p6_wall - p8_wall)) <= tolerance_ms,
         "HOST_TIMING_ARITHMETIC_INVALID")

    cleanup = host["cleanup"]
    require_object(cleanup, {"scratchPath", "scratchCreationTicks", "scratchRemoved", "boundMs", "formula"},
                   "HOST_CLEANUP_SCHEMA")
    need(cleanup["scratchRemoved"] is True and exact_int(cleanup["boundMs"], 120000, 120000) and
         cleanup["formula"] == "nativePhaseChargeMs+(parentFullHostElapsedMs-cleanupStartOffsetMs)",
         "HOST_CLEANUP_INVALID")
    scratch, _scratch_intent = validate_scratch(output_data["scratch-intent.json"], cleanup,
                                                host_launch, output_root)

    compiler = host["compiler"]
    require_object(compiler, {"sourceCount", "sourceList", "sourceListBytes", "sourceListSha256"},
                   "COMPILER_SCHEMA")
    need(type(host["nativeCanaries"]) is dict and set(host["nativeCanaries"]) == {"p6", "p8"},
         "NATIVE_CANARIES_SCHEMA")
    source_summary, native_summary, process_summary = validate_processes(
        host, output_data, pins_info, Path(pins_info["pinsPath"]), pins_sha, output_root, scratch)
    need(p6_wall == process_summary["p6Canary"]["wallMs"] and
         p8_wall == process_summary["p8Canary"]["wallMs"],
         "HOST_NATIVE_WALL_BINDING")
    native_charge_upper_ms = max(charge, cleanup_start - start, p6_wall + p8_wall)
    cleanup_upper_ms = native_charge_upper_ms + (elapsed_ms - cleanup_start)
    need(math.isfinite(native_charge_upper_ms) and native_charge_upper_ms >= 0 and
         math.isfinite(cleanup_upper_ms) and cleanup_upper_ms >= 0 and
         cleanup_upper_ms <= LIMITS["cleanupMs"], "CLEANUP_UPPER_LIMIT")
    validate_source_checks(host["sourceChecks"], source_summary, audit_summary)

    bridge_summary = host["bridgeCopies"]
    require_object(bridge_summary, {"receipt", "count", "verifiedAfterCompile", "verifiedBeforeNative",
                                    "verifiedAfterNative"}, "BRIDGE_SUMMARY_SCHEMA")
    bridge_copies = verify_bridge_receipt(output_data["bridge-copies.json"], bridge_summary,
                                          pins_info, scratch)
    source_paths = verify_compiler_source_list(output_data["compiler-sources.txt"], compiler,
                                               pins_info, bridge_copies)
    need(native_summary["p6"] == host["nativeCanaries"]["p6"], "P6_HOST_SUMMARY_MISMATCH")
    p8_summary = host["nativeCanaries"].get("p8") if type(host["nativeCanaries"]) is dict else None
    need(type(p8_summary) is dict, "P8_HOST_SUMMARY_MISSING")
    expected_p8_summary = {key: native_summary["p8"][key] for key in (
        "schema", "status", "assertions", "caseCount", "workNanos", "groups",
        "maximumGroupNanos", "totalObservationUnits", "layoutQualification")}
    need(p8_summary == expected_p8_summary, "P8_HOST_SUMMARY_MISMATCH")

    limits = host["limits"]
    require_object(limits, set(LIMITS), "HOST_LIMITS_SCHEMA")
    for key, expected in LIMITS.items():
        need(exact_int(limits[key], expected, expected), "HOST_LIMITS_MISMATCH")

    return {
        "host": host,
        "cleanupUpperMs": cleanup_upper_ms,
        "nativePhaseChargeMs": charge,
        "nativePhaseChargeUpperMs": native_charge_upper_ms,
        "finalHostTailMs": elapsed_ms - cleanup_start,
        "sourceSummaries": source_summary,
        "nativeSummaries": native_summary,
        "processSummaries": process_summary,
        "compilerSources": len(source_paths),
        "bridgeCopies": bridge_copies,
        "scratch": scratch,
    }


def check_output_root(output_root_value: str, pins_info: dict,
                      pins_path: Path, parent_path: Path) -> Path:
    output_root = canonical_path(output_root_value, "OUTPUT_ROOT", kind="dir")
    temp_root = Path(os.path.abspath(tempfile.gettempdir()))
    need(path_within(temp_root, output_root) and output_root != temp_root, "OUTPUT_ROOT_TEMP")
    source_roots = [pins_info["repository"], pins_info["here"],
                    Path(pins_info["pins"]["p6PinsPath"]).parent,
                    Path(pins_info["pins"]["bridgeRoot"])]
    source_roots.extend(Path(row["path"]).parent for row in pins_info["pins"]["files"])
    for source_root in source_roots:
        assert_disjoint(source_root, output_root, "OUTPUT_ROOT_SOURCE_OVERLAP")
    assert_disjoint(output_root, pins_path, "OUTPUT_ROOT_INPUT_OVERLAP")
    assert_disjoint(output_root, parent_path, "OUTPUT_ROOT_PARENT_OVERLAP")
    return output_root


def read_output_closure(output_root: Path, expected_host_result_raw: bytes,
                        parent_raw: bytes, parent: dict) -> tuple[dict[str, bytes], dict]:
    no_reparse_ancestors(output_root)
    try:
        directory_before = output_root.stat()
        need(stat.S_ISDIR(directory_before.st_mode), "OUTPUT_ROOT_TYPE")
        entries = list(os.scandir(output_root))
    except OSError:
        raise Reject("OUTPUT_ROOT_READ") from None
    names = [entry.name for entry in entries]
    need(len(names) == len(ALL_OUTPUT_FILES) and set(names) == ALL_OUTPUT_FILES,
         "OUTPUT_FILE_CENSUS")
    output_data: dict[str, bytes] = {}
    total = 0
    for name in OUTPUT_FILES:
        raw = stable_read(output_root / name, LIMITS["aggregateBytes"], "OUTPUT_FILE")
        output_data[name] = raw
        total += len(raw)
        if name.endswith(".json"):
            need(len(raw) <= LIMITS["lineBytes"], "OUTPUT_JSON_LIMIT")
        elif name == "compiler-sources.txt":
            need(len(raw) <= 1048576, "COMPILER_LIST_LIMIT")
    host_result_raw = stable_read(output_root / "host-result.json", LIMITS["lineBytes"],
                                  "HOST_RESULT_FILE")
    need(host_result_raw == expected_host_result_raw, "HOST_RESULT_CHANGED")
    total += len(host_result_raw)
    parent_out = parent["process"]["Stdout"].encode("utf-8")
    parent_err = parent["process"]["Stderr"].encode("utf-8")
    total += len(parent_raw) + len(parent_out) + len(parent_err)
    need(total <= LIMITS["aggregateBytes"], "TOTAL_ARTIFACT_LIMIT")

    host = parse_document(host_result_raw, "HOST_RESULT")
    require_object(host, HOST_RESULT_KEYS, "HOST_RESULT_SCHEMA")
    closure = host["outputClosure"]
    require_object(closure, {"files", "aggregateBytes", "hostResultExcluded"}, "OUTPUT_CLOSURE_SCHEMA")
    need(closure["hostResultExcluded"] is True and type(closure["files"]) is list and
         len(closure["files"]) == len(OUTPUT_FILES), "OUTPUT_CLOSURE_CENSUS")
    declared_total = 0
    for row, expected_name in zip(closure["files"], OUTPUT_FILES):
        require_object(row, {"name", "bytes", "sha256"}, "OUTPUT_CLOSURE_ROW")
        raw = output_data[expected_name]
        need(row["name"] == expected_name and exact_int(row["bytes"], 0, LIMITS["aggregateBytes"]) and
             row["bytes"] == len(raw) and type(row["sha256"]) is str and
             re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None and
             row["sha256"] == sha256(raw), "OUTPUT_CLOSURE_MISMATCH")
        declared_total += len(raw)
    need(exact_int(closure["aggregateBytes"], 0, LIMITS["aggregateBytes"]) and
         closure["aggregateBytes"] == declared_total and declared_total <= LIMITS["aggregateBytes"],
         "OUTPUT_CLOSURE_TOTAL")
    directory_identity = (directory_before.st_dev, directory_before.st_ino,
                          directory_before.st_mtime_ns, directory_before.st_ctime_ns,
                          directory_before.st_mode,
                          getattr(directory_before, "st_file_attributes", 0))
    return output_data, {"files": len(OUTPUT_FILES), "declaredBytes": declared_total,
                         "totalWithFinalEvidence": total,
                         "hostResultBytes": len(host_result_raw),
                         "parentReceiptBytes": len(parent_raw),
                         "parentStreamBytes": len(parent_out) + len(parent_err),
                         "_directoryIdentity": directory_identity}


def main(argv: list[str] | None = None) -> int:
    started_ns = time.perf_counter_ns()
    if argv is None:
        argv = sys.argv[1:]
    if len(argv) != 4:
        raise Reject("CLI_ARGUMENTS_INVALID")
    pins_path = canonical_path(argv[0], "INPUT_PINS_PATH", kind="file")
    pins_sha = argv[1]
    need(type(pins_sha) is str and re.fullmatch(r"[0-9a-f]{64}", pins_sha) is not None,
         "INPUT_PINS_HASH")
    parent_path = canonical_path(argv[3], "PARENT_RECEIPT_PATH", kind="file")
    pins_info, _rows, _p6, pins_raw = parse_input_pins(pins_path, pins_sha)
    pins_info["pinsPath"] = pins_path
    pins_info["pinsRaw"] = pins_raw
    pins_info["pinsSha256"] = pins_sha
    pins_info["javaHome"] = canonical_path(pins_info["pins"]["javaHome"], "JAVA_HOME", kind="dir")
    output_root = check_output_root(argv[2], pins_info, pins_path, parent_path)

    parent_raw = stable_read(parent_path, 1048576, "PARENT_RECEIPT")
    parent_doc = parse_json(parent_raw, "PARENT_RECEIPT", one_lf_line=False)
    require_object(parent_doc, {"schema", "elapsedMs", "process"}, "PARENT_RECEIPT_SCHEMA")
    need(parent_doc["schema"] == "tsj-q100-geometric-canary-parent-v1", "PARENT_RECEIPT_SCHEMA")
    if not finite_number(parent_doc["elapsedMs"], 0):
        raise Reject("PARENT_ELAPSED_TYPE_INVALID")
    # Parent process completion checks precede deeper output and source closure.
    parent_process = parent_doc["process"]
    require_object(parent_process, PROCESS_KEYS, "PARENT_PROCESS_SCHEMA")
    need(parent_process["FilePath"] == str(pins_info["powershellPath"]),
         "PARENT_PROCESS_EXECUTABLE_INVALID")
    expected_parent_args = [
        "-NoLogo", "-NoProfile", "-NonInteractive", "-File", str(pins_info["hostPath"]),
        "-RepositoryRoot", str(pins_info["repository"]),
        "-InputPinsPath", str(pins_path), "-InputPinsSha256", pins_sha,
        "-OutputRoot", str(output_root), "-JavaHome", str(pins_info["javaHome"]),
        "-PythonExe", str(pins_info["pythonPath"]),
    ]
    need(type(parent_process["Arguments"]) is list and parent_process["Arguments"] == expected_parent_args,
         "PARENT_PROCESS_ARGUMENTS_INVALID")
    need(exact_int(parent_process["ProcessId"], 1, 2147483647) and
         exact_int(parent_process["ProcessStartTicks"], 1, 3155378975999999999),
         "PARENT_PROCESS_IDENTITY_INVALID")
    if type(parent_process["ExitCode"]) is not int or parent_process["ExitCode"] != 0:
        raise Reject("PARENT_PROCESS_EXIT_INVALID")
    if parent_process["TerminationProven"] is not True:
        raise Reject("PARENT_PROCESS_TERMINATION_INVALID")
    process_root_paths(parent_process, "PARENT_PROCESS")
    need(parent_process["Stdout"] == "P8_GEOMETRIC_REASON_CANARY_HOST_COMPLETE\r\n" and
         parent_process["Stderr"] == "", "PARENT_PROCESS_STREAMS_INVALID")
    elapsed_ms = float(parent_doc["elapsedMs"])
    need(elapsed_ms <= LIMITS["outerMs"], "PARENT_ELAPSED_LIMIT")

    minimal_host_raw = stable_read(output_root / "host-result.json", LIMITS["lineBytes"],
                                  "HOST_RESULT_FILE")
    minimal_host = parse_document(minimal_host_raw, "HOST_RESULT")
    require_object(minimal_host, HOST_RESULT_KEYS, "HOST_RESULT_SCHEMA")
    need(type(minimal_host["timing"]) is dict and
         "cleanupStartOffsetMs" in minimal_host["timing"] and
         finite_number(minimal_host["timing"]["cleanupStartOffsetMs"], 0),
         "HOST_TIMING_TYPE_INVALID")
    if elapsed_ms < float(minimal_host["timing"]["cleanupStartOffsetMs"]):
        raise Reject("PARENT_TIMING_ORDER_INVALID")

    source_start = time.perf_counter_ns()
    audit_summary, auditor_elapsed_ms = invoke_authenticated_auditor(pins_info, pins_path, pins_sha)
    source_elapsed_ms = (time.perf_counter_ns() - source_start) / 1_000_000.0
    need(math.isfinite(source_elapsed_ms) and 0 <= source_elapsed_ms <= 180000.0,
         "SOURCE_CLOSURE_WALL_LIMIT")

    closure_started = time.perf_counter_ns()
    output_data, output_snapshot = read_output_closure(output_root, minimal_host_raw,
                                                       parent_raw, parent_doc)
    host_launch = validate_host_launch(output_data["host-launch.json"], minimal_host["host"],
                                       pins_info, pins_sha, output_root, parent_process)
    full = validate_host_result(minimal_host_raw, pins_info, pins_sha, parent_doc,
                                elapsed_ms, output_root, output_data, audit_summary, host_launch)

    for key, raw, maximum, code in (
        ("reader", pins_info["readerRaw"], 1048576, "READER_SELF_POST"),
        ("auditor", pins_info["auditorRaw"], 1048576, "AUDITOR_POST"),
        ("host", pins_info["hostRaw"], 1048576, "HOST_SOURCE_POST"),
        ("python", pins_info["pythonRaw"], 104857600, "PYTHON_POST"),
    ):
        current_path = pins_info[{"reader": "readerPath", "auditor": "auditorPath",
                                  "host": "hostPath", "python": "pythonPath"}[key]]
        current = stable_read(current_path, maximum, code)
        need(current == raw, code + "_CHANGED")
    need(stable_read(pins_path, 4194304, "INPUT_PINS_POST") == pins_raw,
         "INPUT_PINS_CHANGED")
    need(stable_read(parent_path, 1048576, "PARENT_RECEIPT_POST") == parent_raw,
         "PARENT_RECEIPT_CHANGED")
    # Recompute all accepted output hashes after the independent source closure.
    for name, previous in output_data.items():
        current = stable_read(output_root / name, LIMITS["aggregateBytes"], "OUTPUT_POST")
        need(current == previous, "OUTPUT_CHANGED_DURING_READER")
    current_host_result = stable_read(output_root / "host-result.json", LIMITS["lineBytes"],
                                      "HOST_RESULT_POST")
    need(current_host_result == minimal_host_raw, "HOST_RESULT_CHANGED_DURING_READER")
    no_reparse_ancestors(output_root)
    try:
        final_entries = list(os.scandir(output_root))
        directory_after = output_root.stat()
    except OSError:
        raise Reject("OUTPUT_ROOT_POST_READ") from None
    need(len(final_entries) == len(ALL_OUTPUT_FILES) and
         {entry.name for entry in final_entries} == ALL_OUTPUT_FILES,
         "OUTPUT_FILE_CENSUS_POST")
    final_identity = (directory_after.st_dev, directory_after.st_ino,
                      directory_after.st_mtime_ns, directory_after.st_ctime_ns,
                      directory_after.st_mode,
                      getattr(directory_after, "st_file_attributes", 0))
    need(final_identity == output_snapshot["_directoryIdentity"],
         "OUTPUT_ROOT_CHANGED_DURING_READER")
    output_snapshot.pop("_directoryIdentity")
    output_elapsed_ms = (time.perf_counter_ns() - closure_started) / 1_000_000.0
    reader_elapsed_ms = (time.perf_counter_ns() - started_ns) / 1_000_000.0
    need(math.isfinite(output_elapsed_ms) and math.isfinite(reader_elapsed_ms) and
         output_elapsed_ms >= 0 and 0 <= reader_elapsed_ms <= 180000.0,
         "READER_WALL_LIMIT")

    result = {
        "schema": "tsj-q100-geometric-canary-reader-v1",
        "status": "PASS_Q100_GEOMETRIC_LITERAL_CANARY_ONLY",
        "mode": "CANARY",
        "inputPinsSha256": pins_sha,
        "readerSha256": sha256(pins_info["readerRaw"]),
        "auditorSha256": sha256(pins_info["auditorRaw"]),
        "hostResultSha256": sha256(minimal_host_raw),
        "parentReceiptSha256": sha256(parent_raw),
        "sourceClosure": {
            **audit_summary,
            "auditorElapsedMs": round(auditor_elapsed_ms, 3),
            "closureElapsedMs": round(source_elapsed_ms, 3),
        },
        "nativeCanaries": {
            "p6": full["nativeSummaries"]["p6"],
            "p8": full["nativeSummaries"]["p8"],
        },
        "compilerSourceCount": full["compilerSources"],
        "bridgeCopyCount": len(full["bridgeCopies"]),
        "processes": full["processSummaries"],
        "cleanup": {
            "nativePhaseChargeMs": round(full["nativePhaseChargeMs"], 3),
            "nativePhaseChargeUpperMs": round(full["nativePhaseChargeUpperMs"], 3),
            "finalHostTailMs": round(full["finalHostTailMs"], 3),
            "upperBoundMs": round(full["cleanupUpperMs"], 3),
            "boundMs": LIMITS["cleanupMs"],
        },
        "outputClosure": output_snapshot,
        "timing": {
            "readerElapsedMs": round(reader_elapsed_ms, 3),
            "sourceClosureElapsedMs": round(source_elapsed_ms, 3),
            "outputClosureElapsedMs": round(output_elapsed_ms, 3),
        },
        "limitations": [
            "FOCUSED_LITERAL_CANARY_ONLY",
            "NO_CANONICAL_BASE_REPLAY",
            "NO_NEW_ROUTE_OR_ADMISSION_CLAIM",
        ],
    }
    out = (json.dumps(result, sort_keys=True, separators=(",", ":"),
                      ensure_ascii=True, allow_nan=False) + "\n").encode("ascii")
    need(len(out) <= LIMITS["lineBytes"], "READER_RESULT_LIMIT")
    sys.stdout.buffer.write(out)
    sys.stdout.buffer.flush()
    return 0


def _emit_rejection(code: str) -> int:
    if not re.fullmatch(r"[A-Z0-9_]{1,96}", code):
        code = "READER_INTERNAL_ERROR"
    sys.stderr.buffer.write(("P8_CANARY_READER_REJECT " + code + "\n").encode("ascii"))
    sys.stderr.buffer.flush()
    return 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Reject as error:
        raise SystemExit(_emit_rejection(error.code))
    except Exception:
        raise SystemExit(_emit_rejection("READER_INTERNAL_ERROR"))
