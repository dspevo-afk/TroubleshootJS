"""Independent consumer for the isolated, same-branch copper-free study."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import sys
import types


ROOT = Path(__file__).resolve().parent
EPOCH = "59a11b0ef37a582b85fa157c5803df42ecd69ab2"
WIRE_SHA256 = "e88411039b6b7438a768280779ac3abbec256ef7995ff305e0bf33c5c2f736a5"
WIRE_RELATIVE = "source/WIRE_R1.md"
WIRE_SCHEMA = "tsj-copper-free-branch-v1"
MARKER_SCHEMA = "tsj-copper-free-branch-marker-v1"
MANIFEST_SCHEMA = "tsj-copper-free-branch-source-v1"
CLASS_SCHEMA = "tsj-copper-free-branch-classes-v1"
PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"
ROOT_CALL_OWNER_SCHEMA = "tsj-p9-root-call-owner-v1"
SOURCE_PARENT = "59a11b0ef37a582b85fa157c5803df42ecd69ab2"
PLAN_SHA256 = "e394fd975547bf0fc9bb79d9c0b628d9564e84f06bf0b5788465d32b214f8ccd"
P9_PINS_SHA256 = "2d717040c9b78244cf591857af96cba51bb52366af6c270e70b741db0468bf08"
P9_AUDITOR_SHA256 = "f2080fa993046bb24627233403a3e499ce843044c7403e8bad5bdfe09ea56998"
P07_READER_SHA256 = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
P9_ROOT_CALL_SHA256 = "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d"
P9_PROCESS_MODULE_SHA256 = "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c"
PS5_SHA256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
COMPILE_STDERR = (
    "warning: [options] bootstrap class path not set in conjunction with -source 1.7\r\n"
    "Note: Some input files use unchecked or unsafe operations.\r\n"
    "Note: Recompile with -Xlint:unchecked for details.\r\n"
    "1 warning\r\n"
)
MAX_JSON = 8 * 1024 * 1024
MAX_RECEIPT = 8 * 1024 * 1024
MAX_LINE = 65_536
MAX_CLASS_BYTES = 256 * 1024 * 1024
MAX_CLASS_COUNT = 10_000
MAX_COMPILER_SOURCES = 1_088
MAX_COORD = 100_000_000
MAX_ROUTE_POINTS = 250_000
MAX_TRACE_WIDTH = 63
HEX = re.compile(r"[0-9a-f]{64}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SEED = ("9007199254740993", "-6587958367974952701", "8445301674744508279")
PREFIX_SHA256 = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
ROLE_WIDTH = {"HIGH_CURRENT": 13, "SUPPLY": 11, "RETURN": 11, "CONTROL": 9, "SIGNAL": 9}
DIR_DELTA = {0: (0, -10), 1: (10, 0), 2: (0, 10), 3: (-10, 0)}
LAYER_FLIP_COST = 60
SECONDARY_LAYER_COST = 2
TURN_COST = 35
VIA_DRILL_RADIUS = 2
VIA_LAND_RADIUS = 6


class ReaderError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("BF_") else "BF_" + code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def exact_keys(value, names, code: str) -> None:
    need(type(value) is dict and set(value) == set(names), code)


def _same_json(left, right) -> bool:
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return set(left) == set(right) and all(_same_json(left[k], right[k]) for k in left)
    if type(left) is list:
        return len(left) == len(right) and all(_same_json(a, b) for a, b in zip(left, right))
    return left == right


def is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high


def is_digest(value) -> bool:
    return type(value) is str and HEX.fullmatch(value) is not None


def _pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            raise ReaderError("BF_JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _parse_int(text):
    if text == "-0":
        raise ReaderError("BF_JSON_NEGATIVE_ZERO")
    return int(text)


def _parse_float(text):
    value = float(text)
    if not math.isfinite(value):
        raise ReaderError("BF_JSON_NONFINITE")
    return value


def _reject_constant(_text):
    raise ReaderError("BF_JSON_NONFINITE")


def _json_load(raw: bytes):
    need(type(raw) is bytes and not raw.startswith(b"\xef\xbb\xbf"), "JSON_BOM")
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=_pairs,
                           parse_int=_parse_int, parse_float=_parse_float,
                           parse_constant=_reject_constant)
    except ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, OverflowError):
        raise ReaderError("BF_JSON_INVALID") from None
    def check(v):
        if type(v) is str:
            need(not any(0xD800 <= ord(c) <= 0xDFFF for c in v), "JSON_SURROGATE")
        elif type(v) is dict:
            for k, item in v.items():
                check(k)
                check(item)
        elif type(v) is list:
            for item in v:
                check(item)
        elif type(v) is float:
            need(math.isfinite(v), "JSON_NONFINITE")
    check(value)
    return value


def _is_compact(raw: bytes) -> bool:
    in_string = False
    escaped = False
    for byte in raw:
        if in_string:
            if escaped:
                escaped = False
            elif byte == 0x5C:
                escaped = True
            elif byte == 0x22:
                in_string = False
        elif byte == 0x22:
            in_string = True
        elif byte in b" \t\r\n":
            return False
    return not in_string and not escaped


def strict_compact_line(raw: bytes, code: str):
    need(_is_compact(raw), code + "_COMPACT")
    return _json_load(raw)


def strict_console_line(raw: bytes, code: str):
    need(type(raw) is bytes and raw, code + "_FRAMING")
    if raw.endswith(b"\r\n"):
        body = raw[:-2]
    elif raw.endswith(b"\n"):
        body = raw[:-1]
    else:
        raise ReaderError("BF_" + code + "_FRAMING")
    need(body and b"\r" not in body and b"\n" not in body, code + "_FRAMING")
    return strict_compact_line(body, code)


def strict_document(raw: bytes, limit: int = MAX_JSON):
    need(0 < len(raw) <= limit and raw.endswith(b"\n") and not raw.endswith(b"\n\n")
         and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"), "DOCUMENT_FRAMING")
    return strict_compact_line(raw[:-1], "DOCUMENT")


def host_document(raw: bytes, limit: int = MAX_JSON):
    """Parse bounded PowerShell JSON without normalizing or rewriting its bytes."""
    need(type(raw) is bytes and 0 < len(raw) <= limit
         and not raw.startswith(b"\xef\xbb\xbf"), "HOST_DOCUMENT_FRAMING")
    return _json_load(raw)


def _lstat_regular(path: Path, code: str) -> None:
    try:
        info = path.lstat()
    except OSError:
        raise ReaderError(code) from None
    need(stat.S_ISREG(info.st_mode) and not stat.S_ISLNK(info.st_mode)
         and not (getattr(info, "st_file_attributes", 0) & 0x400), code)


def _lstat_directory(path: Path, code: str) -> None:
    try:
        info = path.lstat()
    except OSError:
        raise ReaderError(code) from None
    need(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode)
         and not (getattr(info, "st_file_attributes", 0) & 0x400), code)


def _read(path: Path, limit: int, code: str) -> bytes:
    _lstat_regular(path, code)
    try:
        if path.stat().st_size > limit:
            raise ReaderError(code)
        raw = path.read_bytes()
    except ReaderError:
        raise
    except OSError:
        raise ReaderError(code) from None
    need(len(raw) <= limit, code)
    return raw


def _canonical(path: str | Path, code: str, require_exists: bool = True) -> Path:
    try:
        p = Path(path)
        resolved = p.resolve(strict=require_exists)
    except (OSError, RuntimeError, ValueError):
        raise ReaderError(code) from None
    need(p.is_absolute() and os.path.normcase(str(p)) == os.path.normcase(str(resolved)), code)
    for parent in (resolved, *resolved.parents):
        try:
            info = parent.lstat()
        except FileNotFoundError:
            continue
        except OSError:
            raise ReaderError(code) from None
        need(not stat.S_ISLNK(info.st_mode)
             and not (getattr(info, "st_file_attributes", 0) & 0x400), code)
    return resolved


def _inside(path: Path, root: Path, code: str) -> None:
    try:
        path.relative_to(root)
    except ValueError:
        raise ReaderError(code) from None


def _same_path(left: str | Path, right: str | Path) -> bool:
    try:
        a = Path(left).resolve(strict=False)
        b = Path(right).resolve(strict=False)
    except (OSError, RuntimeError, ValueError):
        return False
    return os.path.normcase(str(a)) == os.path.normcase(str(b))


def _same_host_path(value, expected: str | Path, code: str) -> bool:
    need(type(value) is str, code)
    actual = _canonical(value, code)
    return _same_path(actual, expected)


def _safe_source_record(row, expected_path):
    exact_keys(row, ("path", "bytes", "sha256"), "SOURCE_RECORD")
    need(type(row["path"]) is str and is_int(row["bytes"], 1, 256 * 1024 * 1024)
         and is_digest(row["sha256"]), "SOURCE_RECORD")
    path = _canonical(row["path"], "SOURCE_PATH")
    expected = _canonical(expected_path, "SOURCE_EXPECTED_PATH")
    need(str(path) == str(expected), "SOURCE_PATH")
    raw = _read(path, row["bytes"], "SOURCE_READ")
    need(len(raw) == row["bytes"] and sha(raw) == row["sha256"], "SOURCE_HASH")
    return path


def _path_below(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _load_authenticated_parent_inputs(pins_path: Path, pins_raw: bytes):
    need(sys.flags.isolated and sys.dont_write_bytecode, "PARENT_AUDITOR_MODE")
    p9 = ROOT.parent / "tsj-q100-reservation-provenance-9f553163342745b1aade3f8b3b60fefd"
    canonical_root = _canonical(p9 / "canonical-reservation-provenance-r1", "PARENT_AUDITOR_ROOT")
    auditor_path = _canonical(canonical_root / "audit_inputs.py", "PARENT_AUDITOR_PATH")
    auditor_raw = _read(auditor_path, 1024 * 1024, "PARENT_AUDITOR_READ")
    need(sha(auditor_raw) == P9_AUDITOR_SHA256, "PARENT_AUDITOR_HASH")
    module = types.ModuleType("_authenticated_p9_audit_inputs")
    module.__file__ = str(auditor_path)
    try:
        exec(compile(auditor_raw, str(auditor_path), "exec"), module.__dict__)
        context = module.validate_inputs(str(pins_path), sha(pins_raw))
    except (Exception, SystemExit):
        raise ReaderError("PARENT_INPUT_CLOSURE") from None
    need(type(context) is dict and context.get("pinsRaw") == pins_raw
         and context.get("summary", {}).get("status") == "PASS_Q100_RESERVATION_INPUTS_ONLY",
         "PARENT_INPUT_CLOSURE")
    return module, context


def _parent_file_record(auditor, context, path):
    key = auditor.normpath(path)
    row = context.get("rows", {}).get(key)
    consumed = context.get("_reads").consumed.get(key) if context.get("_reads") else None
    if row is not None and consumed is not None:
        need(row["bytes"] == consumed["bytes"] and row["sha256"] == consumed["sha256"],
             "PARENT_FILE_BINDING")
    return row if row is not None else consumed


def _validate_parent_compiler_sources(sources, source_paths, auditor, context):
    bridges = context.get("bridges")
    need(type(bridges) is list and len(bridges) == 3, "PARENT_BRIDGE_CLOSURE")
    bridge_by_name = {row.get("name"): row for row in bridges if type(row) is dict}
    bridge_names = {"CirSim.java", "ChipElm.java", "PhysicalSpecificationDeveloperVerifier.java"}
    need(set(bridge_by_name) == bridge_names, "PARENT_BRIDGE_CLOSURE")
    candidates = {}
    for path in source_paths[:1085]:
        if path.name in bridge_names:
            candidates.setdefault(path.parent, set()).add(path.name)
    directories = [path for path, names in candidates.items() if names == bridge_names]
    need(len(directories) == 1, "PARENT_BRIDGE_DIRECTORY")
    bridge_directory = directories[0]
    try:
        expected_old = auditor.expected_compiler_sources(context, str(bridge_directory))
    except Exception:
        raise ReaderError("PARENT_COMPILER_SOURCE_LIST") from None
    need(type(expected_old) is list and len(expected_old) == 1085
         and expected_old == sorted(expected_old)
         and [str(path) for path in source_paths[:1085]] == expected_old,
         "PARENT_COMPILER_SOURCE_LIST")
    bridge_destinations = {
        auditor.normpath(bridge_directory / name): bridge_by_name[name]
        for name in bridge_names
    }
    for row, path in zip(sources[:1085], source_paths[:1085]):
        expected = bridge_destinations.get(auditor.normpath(path))
        if expected is None:
            expected = _parent_file_record(auditor, context, path)
        need(type(expected) is dict and row["bytes"] == expected["bytes"]
             and row["sha256"] == expected["sha256"], "PARENT_SOURCE_HASH")
    return expected_old, bridge_directory


def _validate_parent_tools(tools, auditor, context):
    parent_tools = context.get("tools")
    need(type(parent_tools) is dict, "PARENT_TOOL_SET")
    expected = {
        "java.exe": parent_tools.get("javaExe"),
        "javac.exe": parent_tools.get("javacExe"),
        "gwt-user-2.7.0.jar": parent_tools.get("gwtUser"),
        "gwt-dev-2.7.0.jar": parent_tools.get("gwtDev"),
    }
    need(all(type(path) is str for path in expected.values()), "PARENT_TOOL_SET")
    seen = set()
    paths = []
    for row in tools:
        exact_keys(row, ("path", "bytes", "sha256"), "TOOL_RECORD")
        need(type(row["path"]) is str and is_int(row["bytes"], 1, 256 * 1024 * 1024)
             and is_digest(row["sha256"]), "TOOL_RECORD")
        path = _canonical(row["path"], "TOOL_PATH")
        name = path.name.lower()
        need(name in expected and name not in seen, "TOOL_SET")
        seen.add(name)
        expected_path = _canonical(expected[name], "PARENT_TOOL_PATH")
        need(str(path) == str(expected_path), "TOOL_PATH")
        path = _safe_source_record(row, expected_path)
        parent_record = _parent_file_record(auditor, context, path)
        need(type(parent_record) is dict and row["bytes"] == parent_record["bytes"]
             and row["sha256"] == parent_record["sha256"], "PARENT_TOOL_HASH")
        paths.append(path)
    need(seen == set(expected), "TOOL_SET")
    return paths


def _load_authenticated_p07_reader(auditor, context):
    path = _canonical(Path(context["repositoryRoot"])
                      / "tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py",
                      "P07_READER_PATH")
    parent_row = _parent_file_record(auditor, context, path)
    need(type(parent_row) is dict and parent_row["sha256"] == P07_READER_SHA256,
         "P07_READER_PARENT_PIN")
    raw = _read(path, 8 * 1024 * 1024, "P07_READER_READ")
    need(len(raw) == parent_row["bytes"] and sha(raw) == P07_READER_SHA256,
         "P07_READER_HASH")
    module = types.ModuleType("_authenticated_p07_branch_reader")
    module.__file__ = str(path)
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except Exception:
        raise ReaderError("P07_READER_LOAD") from None
    need(callable(getattr(module, "validate_operation", None)), "P07_READER_API")
    return module


def _validate_compile_source_order(source_paths, expected_new_sources) -> None:
    """Pin the exact appended task-owned source tail after the 1085 parent inputs."""
    paths = [str(path) for path in source_paths]
    tail = [str(path) for path in expected_new_sources]
    need(len(tail) in (2, 3) and len(paths) == 1085 + len(tail), "SOURCE_COUNT")
    old_paths = paths[:-len(tail)]
    need(old_paths == sorted(old_paths) and paths[-len(tail):] == tail, "SOURCE_ORDER")


def _validate_owner_call(call_dir: Path, parent_path: Path, parent, request, process,
                         expected_file: str, expected_args: list[str], limit_ms: int,
                         process_module: Path, required_stdout: str | None = None,
                         expected_stderr: str = ""):
    _lstat_directory(call_dir, "PROCESS_CALL_DIR")
    owner_raw = _read(call_dir / "owner.json", 64 * 1024, "PROCESS_OWNER")
    owner = host_document(owner_raw, 64 * 1024)
    exact_keys(owner, ("schema", "pid", "startTicks", "executable", "script", "request", "createdUtc"),
               "PROCESS_OWNER_SCHEMA")
    need(owner["schema"] == ROOT_CALL_OWNER_SCHEMA and is_int(owner["pid"], 1)
         and type(owner["startTicks"]) is str and owner["startTicks"].isdigit()
         and len(owner["startTicks"]) <= 20 and type(owner["executable"]) is str
         and type(owner["script"]) is str and type(owner["request"]) is str
         and type(owner["createdUtc"]) is str, "PROCESS_OWNER_FIELDS")
    need(_same_path(owner["request"], call_dir / "request.json"), "PROCESS_OWNER_REQUEST")
    owner_exe = _canonical(owner["executable"], "PROCESS_OWNER_EXECUTABLE")
    owner_script = _canonical(owner["script"], "PROCESS_OWNER_SCRIPT")
    owner_script_raw = _read(owner_script, 256 * 1024, "PROCESS_OWNER_SCRIPT")
    need(sha(_read(owner_exe, 32 * 1024 * 1024, "PROCESS_OWNER_EXECUTABLE")) == PS5_SHA256
         and sha(owner_script_raw) == P9_ROOT_CALL_SHA256
         and str(process_module).encode("utf-8") in owner_script_raw
         and sha(_read(process_module, 1024 * 1024, "PROCESS_MODULE")) == P9_PROCESS_MODULE_SHA256,
         "PROCESS_OWNER_TOOL_HASH")

    request_raw = _read(call_dir / "request.json", 64 * 1024, "PROCESS_REQUEST")
    on_disk_request = host_document(request_raw, 64 * 1024)
    exact_keys(on_disk_request, ("FilePath", "Arguments", "LimitMs"), "PROCESS_REQUEST_SCHEMA")
    need(on_disk_request == request, "PROCESS_REQUEST_COPY")
    need(_same_host_path(request["FilePath"], expected_file, "PROCESS_REQUEST_FILE_PATH")
         and request["Arguments"] == expected_args
         and is_int(request["LimitMs"], 1, 480_000) and request["LimitMs"] == limit_ms,
         "PROCESS_REQUEST_BINDING")

    process_raw = _read(call_dir / "process.json", 256 * 1024, "PROCESS_COPY")
    on_disk_process = host_document(process_raw, 256 * 1024)
    exact_keys(on_disk_process, ("FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
                                 "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
                                 "TerminationProven"), "PROCESS_SCHEMA")
    need(on_disk_process == process, "PROCESS_COPY")
    need(parent.get("process") == process, "PROCESS_PARENT_BINDING")
    need(type(process["FilePath"]) is str
         and _same_host_path(process["FilePath"], expected_file, "PROCESS_FILE_PATH")
         and type(process["Arguments"]) is list and process["Arguments"] == expected_args
         and is_int(process["ProcessId"], 1) and is_int(process["ProcessStartTicks"], 1, 2**63 - 1)
         and type(process["ExitCode"]) is int and process["ExitCode"] == 0
         and type(process["Stdout"]) is str and type(process["Stderr"]) is str
         and type(process["StdoutLog"]) is str and type(process["StderrLog"]) is str
         and type(process["ProcessRoot"]) is str and process["TerminationProven"] is True,
         "PROCESS_FIELDS")
    exact_keys(parent, ("schema", "elapsedMs", "process"), "PROCESS_PARENT_SCHEMA")
    need(parent.get("schema") == PARENT_SCHEMA and _is_finite_number(parent.get("elapsedMs"))
         and 0 < parent["elapsedMs"] <= limit_ms, "PROCESS_PARENT_TIMING")
    result_raw = _read(call_dir / "result.json", 64 * 1024, "PROCESS_RESULT")
    result = host_document(result_raw, 64 * 1024)
    exact_keys(result, ("status", "elapsedMs", "exitCode", "terminationProven", "stdoutBytes",
                        "stderrBytes", "processRootAbsent", "totalElapsedMs"), "PROCESS_RESULT_SCHEMA")
    need(result["status"] == "RETURNED" and _is_finite_number(result["elapsedMs"])
         and result["elapsedMs"] == parent["elapsedMs"] and result["exitCode"] == 0
         and result["terminationProven"] is True and is_int(result["stdoutBytes"])
         and is_int(result["stderrBytes"]) and result["processRootAbsent"] is True
         and _is_finite_number(result["totalElapsedMs"])
         and result["totalElapsedMs"] >= result["elapsedMs"], "PROCESS_RESULT_BINDING")
    stream_specs = (("stdout.txt", "Stdout", result["stdoutBytes"]),
                    ("stderr.txt", "Stderr", result["stderrBytes"]))
    for name, field, count in stream_specs:
        raw = _read(call_dir / name, 8 * 1024 * 1024, "PROCESS_STREAM")
        need(raw == process[field].encode("utf-8", errors="strict") and len(raw) == count,
             "PROCESS_STREAM_BINDING")
    _validate_process_stderr(process["Stderr"], result["stderrBytes"], expected_stderr)
    for field in ("StdoutLog", "StderrLog", "ProcessRoot"):
        try:
            need(not Path(process[field]).exists(), "PROCESS_RESOURCE_REMAINS")
        except (OSError, ValueError):
            raise ReaderError("PROCESS_RESOURCE_PATH") from None
    if required_stdout is not None:
        need(process["Stdout"] == required_stdout, "PROCESS_STDOUT_MARKER")
    return result


def _validate_process_stderr(stderr: str, byte_count: int, expected: str = "") -> None:
    need(type(stderr) is str and type(expected) is str and is_int(byte_count)
         and stderr == expected
         and byte_count == len(expected.encode("utf-8", errors="strict")),
         "PROCESS_STDERR")


def _is_finite_number(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value)


def _validate_class_tree(classes_root: Path, inventory_path: Path, inventory_raw: bytes,
                         manifest_build) -> list[dict]:
    need(sha(inventory_raw) == manifest_build["classInventorySha256"], "CLASS_INVENTORY_HASH")
    inv = strict_document(inventory_raw, 4 * 1024 * 1024)
    need(inventory_raw == (json.dumps(inv, ensure_ascii=True, sort_keys=True,
                                      separators=(",", ":"), allow_nan=False) + "\n").encode("ascii"),
         "CLASS_INVENTORY_CANONICAL")
    exact_keys(inv, ("schema", "files", "count", "bytes"), "CLASS_INVENTORY_SCHEMA")
    need(inv["schema"] == CLASS_SCHEMA and type(inv["files"]) is list
         and is_int(inv["count"], 1, MAX_CLASS_COUNT)
         and inv["count"] == len(inv["files"]) and is_int(inv["bytes"], 1, MAX_CLASS_BYTES),
         "CLASS_INVENTORY_FIELDS")
    _lstat_directory(classes_root, "CLASS_ROOT")
    actual = []
    total = 0
    for current, directories, files in os.walk(classes_root, followlinks=False):
        base = Path(current)
        for name in directories + files:
            item = base / name
            try:
                info = item.lstat()
            except OSError:
                raise ReaderError("CLASS_TREE_READ") from None
            need(not stat.S_ISLNK(info.st_mode)
                 and not (getattr(info, "st_file_attributes", 0) & 0x400), "CLASS_TREE_REPARSE")
        for name in files:
            path = base / name
            need(path.suffix == ".class", "CLASS_TREE_FOREIGN_FILE")
            body = _read(path, MAX_CLASS_BYTES, "CLASS_TREE_READ")
            total += len(body)
            need(total <= MAX_CLASS_BYTES and len(actual) < MAX_CLASS_COUNT, "CLASS_TREE_BOUND")
            actual.append({"path": path.relative_to(classes_root).as_posix(),
                           "bytes": len(body), "sha256": sha(body)})
    actual.sort(key=lambda row: row["path"])
    previous = None
    for row in inv["files"]:
        exact_keys(row, ("path", "bytes", "sha256"), "CLASS_ROW_SCHEMA")
        need(type(row["path"]) is str and row["path"].endswith(".class")
             and (previous is None or previous < row["path"])
             and is_int(row["bytes"], 1, MAX_CLASS_BYTES) and is_digest(row["sha256"]),
             "CLASS_ROW_FIELDS")
        previous = row["path"]
    need(actual == inv["files"] and len(actual) == inv["count"] and total == inv["bytes"],
         "CLASS_TREE_BINDING")
    return actual


def validate_manifest(manifest_path: str | Path, expected_sha: str) -> dict:
    path = _canonical(manifest_path, "MANIFEST_PATH")
    _inside(path, ROOT, "MANIFEST_ROOT")
    raw = _read(path, 16 * 1024 * 1024, "MANIFEST_READ")
    need(is_digest(expected_sha) and sha(raw) == expected_sha, "MANIFEST_HASH")
    manifest = strict_document(raw, 16 * 1024 * 1024)
    need(raw == (json.dumps(manifest, ensure_ascii=True, sort_keys=True,
                            separators=(",", ":"), allow_nan=False) + "\n").encode("ascii"),
         "MANIFEST_CANONICAL")
    exact_keys(manifest, ("schema", "parentPinsPath", "parentPinsSha256", "parentAuditorSha256",
                          "sourceParent", "planSha256", "compilerSourceCount", "compilerListSha256",
                          "sources", "tools", "wire", "build"), "MANIFEST_SCHEMA")
    need(manifest["schema"] == MANIFEST_SCHEMA and manifest["sourceParent"] == SOURCE_PARENT
         and manifest["parentPinsSha256"] == P9_PINS_SHA256
         and manifest["parentAuditorSha256"] == P9_AUDITOR_SHA256
         and is_digest(manifest["planSha256"]) and is_digest(manifest["compilerListSha256"]),
         "MANIFEST_IDENTITY")
    p9 = ROOT.parent / "tsj-q100-reservation-provenance-9f553163342745b1aade3f8b3b60fefd"
    expected_pins = p9 / "inputs-r15" / "input-provenance-pins.json"
    need(_same_path(manifest["parentPinsPath"], expected_pins), "PARENT_PINS_PATH")
    pins_path = _canonical(manifest["parentPinsPath"], "PARENT_PINS_PATH")
    pins_raw = _read(pins_path, 16 * 1024 * 1024, "PARENT_PINS_READ")
    need(sha(pins_raw) == P9_PINS_SHA256, "PARENT_PINS_HASH")
    pins = strict_document(pins_raw, 16 * 1024 * 1024)
    repository_root = _canonical(pins["repositoryRoot"], "REPOSITORY_ROOT")
    process_module = _canonical(repository_root / "scripts" / "VerifierIsolation.psm1",
                                "PROCESS_MODULE_PATH")
    need(sha(_read(process_module, 1024 * 1024, "PROCESS_MODULE")) == P9_PROCESS_MODULE_SHA256,
         "PROCESS_MODULE_HASH")
    p9_root = p9 / "canonical-reservation-provenance-r1"
    plan_raw = _read(ROOT / "PLAN_R1.md", 128 * 1024, "PLAN_READ")
    need(sha(plan_raw) == PLAN_SHA256 == manifest["planSha256"], "PLAN_HASH")
    wire = manifest["wire"]
    exact_keys(wire, ("path", "bytes", "sha256"), "WIRE_RECORD")
    wire_path = _canonical(wire["path"], "WIRE_PATH")
    expected_wire = ROOT / WIRE_RELATIVE
    need(_same_path(wire_path, expected_wire) and wire["sha256"] == WIRE_SHA256
         and is_int(wire["bytes"], 1, 64 * 1024), "WIRE_IDENTITY")
    wire_raw = _read(wire_path, 64 * 1024, "WIRE_READ")
    need(len(wire_raw) == wire["bytes"] and sha(wire_raw) == WIRE_SHA256, "WIRE_HASH")
    parent_auditor, parent_input_context = _load_authenticated_parent_inputs(pins_path, pins_raw)
    p07_reader = _load_authenticated_p07_reader(parent_auditor, parent_input_context)
    sources = manifest["sources"]
    tools = manifest["tools"]
    need(type(sources) is list and type(tools) is list and 1087 <= len(sources) <= MAX_COMPILER_SOURCES
         and len(tools) == 4 and is_int(manifest["compilerSourceCount"], len(sources), len(sources)),
         "MANIFEST_COUNTS")
    source_paths = []
    for row in sources:
        exact_keys(row, ("path", "bytes", "sha256"), "SOURCE_RECORD")
        need(type(row["path"]) is str and is_int(row["bytes"], 1, 256 * 1024 * 1024)
             and is_digest(row["sha256"]), "SOURCE_RECORD")
        source_paths.append(_canonical(row["path"], "SOURCE_PATH"))
    need(len({os.path.normcase(str(p)) for p in source_paths}) == len(source_paths), "SOURCE_DUPLICATE")
    new_study = _canonical(ROOT / "source" / "CopperFreeBranchStudy.java", "NATIVE_SOURCE_PATH")
    new_fixtures = _canonical(ROOT / "source" / "CopperFreeBranchFixtures.java",
                              "NATIVE_FIXTURE_SOURCE_PATH")
    optional_canary_path = ROOT / "source" / "CopperFreeBranchCanary.java"
    optional_canary = (_canonical(optional_canary_path, "CANARY_SOURCE_PATH")
                       if optional_canary_path.exists() else None)
    new_source_tail = [new_study, new_fixtures]
    if optional_canary is not None:
        new_source_tail.append(optional_canary)
    expected_old, _bridge_directory = _validate_parent_compiler_sources(
        sources, source_paths, parent_auditor, parent_input_context)
    _validate_compile_source_order(source_paths, new_source_tail)
    expected_all = expected_old + [str(path) for path in new_source_tail]
    need([str(path) for path in source_paths] == expected_all, "SOURCE_PARENT_BINDING")
    source_paths = [_safe_source_record(row, expected)
                    for row, expected in zip(sources, expected_all)]
    tool_paths = []
    tool_paths = _validate_parent_tools(tools, parent_auditor, parent_input_context)
    need(len({os.path.normcase(str(p)) for p in tool_paths}) == len(tool_paths), "TOOL_DUPLICATE")
    java = next((row for row in tools if Path(row["path"]).name.lower() == "java.exe"), None)
    javac = next((row for row in tools if Path(row["path"]).name.lower() == "javac.exe"), None)
    jars = {Path(row["path"]).name: row for row in tools if Path(row["path"]).suffix.lower() == ".jar"}
    need(java is not None and javac is not None
         and set(jars) == {"gwt-user-2.7.0.jar", "gwt-dev-2.7.0.jar"}, "TOOL_SET")

    build = manifest["build"]
    exact_keys(build, ("compileParentPath", "compileParentSha256", "compilerRequestPath",
                       "compilerRequestSha256", "classesRoot", "classInventoryPath",
                       "classInventorySha256", "classCount"), "BUILD_SCHEMA")
    need(all(is_digest(build[k]) for k in ("compileParentSha256", "compilerRequestSha256",
                                            "classInventorySha256"))
         and is_int(build["classCount"], 1, MAX_CLASS_COUNT), "BUILD_FIELDS")
    stage = path.parent
    classes = _canonical(build["classesRoot"], "CLASS_ROOT")
    inventory_path = _canonical(build["classInventoryPath"], "CLASS_INVENTORY_PATH")
    request_path = _canonical(build["compilerRequestPath"], "COMPILE_REQUEST_PATH")
    compile_parent_path = _canonical(build["compileParentPath"], "COMPILE_PARENT_PATH")
    for item in (classes, inventory_path, request_path):
        _inside(item, stage, "BUILD_ROOT")
    _inside(compile_parent_path, ROOT, "COMPILE_PARENT_ROOT")
    need(_same_path(classes, stage / "classes") and _same_path(inventory_path, stage / "class-inventory.json")
         and _same_path(request_path, stage / "request.json"), "BUILD_PATH_BINDING")
    inventory_raw = _read(inventory_path, 4 * 1024 * 1024, "CLASS_INVENTORY_READ")
    actual_classes = _validate_class_tree(classes, inventory_path, inventory_raw, build)
    need(len(actual_classes) == build["classCount"], "BUILD_CLASS_COUNT")

    request_raw = _read(request_path, 64 * 1024, "COMPILE_REQUEST_READ")
    need(sha(request_raw) == build["compilerRequestSha256"], "COMPILE_REQUEST_HASH")
    compile_request = host_document(request_raw, 64 * 1024)
    exact_keys(compile_request, ("FilePath", "Arguments", "LimitMs"), "COMPILE_REQUEST_SCHEMA")
    stage_classes = str(classes)
    jar_user = next(row["path"] for row in tools if Path(row["path"]).name == "gwt-user-2.7.0.jar")
    jar_dev = next(row["path"] for row in tools if Path(row["path"]).name == "gwt-dev-2.7.0.jar")
    classpath = ";".join((stage_classes, jar_user, jar_dev))
    compile_args = compile_request["Arguments"]
    need(type(compile_args) is list and len(compile_args) == 13
         and compile_request["FilePath"] == javac["path"] and compile_request["LimitMs"] == 60_000
         and compile_args[:6] == ["-source", "7", "-target", "7", "-encoding", "UTF-8"]
         and compile_args[6:8] == ["-classpath", classpath]
         and compile_args[8] == "-sourcepath", "COMPILE_ARGUMENTS")
    empty_source = _canonical(compile_args[9], "EMPTY_SOURCEPATH")
    need(compile_args[10] == "-d" and _same_path(compile_args[11], classes)
         and type(compile_args[12]) is str and compile_args[12].startswith("@"),
         "COMPILE_ARGUMENTS")
    _lstat_directory(empty_source, "EMPTY_SOURCEPATH")
    need(not any(empty_source.iterdir()), "EMPTY_SOURCEPATH_NOT_EMPTY")
    source_list = _canonical(compile_args[12][1:], "COMPILER_LIST_PATH")
    _inside(source_list, stage, "COMPILER_LIST_ROOT")
    source_list_raw = _read(source_list, 2 * 1024 * 1024, "COMPILER_LIST_READ")
    expected_source_list = "".join('"' + str(src).replace("\\", "/") + '"\n'
                                   for src in source_paths).encode("utf-8")
    need(source_list_raw == expected_source_list
         and sha(source_list_raw) == manifest["compilerListSha256"], "COMPILER_LIST_BINDING")

    compile_parent_raw = _read(compile_parent_path, 256 * 1024, "COMPILE_PARENT_READ")
    need(sha(compile_parent_raw) == build["compileParentSha256"], "COMPILE_PARENT_HASH")
    compile_parent = host_document(compile_parent_raw, 256 * 1024)
    compile_process = compile_parent.get("process") if type(compile_parent) is dict else None
    _validate_owner_call(compile_parent_path.parent, compile_parent_path, compile_parent,
                         compile_request, compile_process, javac["path"], compile_args,
                         60_000, process_module, expected_stderr=COMPILE_STDERR)

    return {
        "path": path, "raw": raw, "sha256": expected_sha, "manifest": manifest,
        "repositoryRoot": repository_root, "p9Root": p9_root,
        "parentAuditor": parent_auditor, "parentInputContext": parent_input_context,
        "p07Reader": p07_reader,
        "wireRaw": wire_raw, "sourcePaths": source_paths, "tools": tools,
        "processModule": process_module,
        "java": java, "javac": javac, "jars": jars, "build": build,
        "classesRoot": classes, "classInventory": actual_classes,
        "compileParentPath": compile_parent_path, "compileRequest": compile_request,
        "compileRequestPath": request_path, "compilerListPath": source_list,
    }


def _check_token(value, code: str, max_bytes: int = 128) -> None:
    need(type(value) is str and 0 < len(value.encode("utf-8")) <= max_bytes
         and re.fullmatch(r"[-+A-Za-z0-9_.:]+", value) is not None, code)


def _select_authored_branch(branches) -> tuple[dict, int]:
    need(type(branches) is list and len(branches) > 307, "OP1_BRANCH_COUNT")
    local_counts = {}
    selected = None
    for ordinal, branch in enumerate(branches):
        need(type(branch) is dict and is_int(branch.get("ordinal"), ordinal, ordinal),
             "OP1_BRANCH_ORDINALS")
        snapshot = branch.get("snapshot")
        need(type(snapshot) is dict and is_int(snapshot.get("pass"), 0, 2),
             "OP1_BRANCH_SNAPSHOT")
        pass_number = snapshot["pass"]
        local_counts[pass_number] = local_counts.get(pass_number, 0) + 1
        if ordinal == 307:
            selected = branch, local_counts[pass_number]
    need(selected is not None, "OP1_TARGET_BRANCH")
    branch, lineage_ordinal = selected
    need(branch["snapshot"]["pass"] == 2 and lineage_ordinal == 77,
         "OP1_TARGET_LINEAGE")
    return branch, lineage_ordinal


def _parse_operation1(raw: bytes, entry, run_id: str, source_sha: str, wire_sha: str,
                      p07_reader) -> dict:
    try:
        parsed = p07_reader.validate_operation(raw, entry, source_sha, wire_sha,
                                               1, run_id, "AUTHORED")
    except Exception:
        raise ReaderError("OP1_P07_VALIDATION") from None
    need(type(parsed) is dict and type(parsed.get("header")) is dict
         and type(parsed.get("pads")) is list and type(parsed.get("branches")) is list
         and type(parsed.get("groups")) is list and type(parsed.get("result")) is dict,
         "OP1_P07_RESULT")
    branch, lineage_ordinal = _select_authored_branch(parsed["branches"])
    logical_work = parsed["result"].get("logicalWork")
    need(type(logical_work) is dict and is_int(logical_work.get("p07OwnerSteps"), 0,
                                               32_768 * 640), "OP1_P07_WORK")
    adapted = dict(parsed)
    adapted.update({
        "candidate": parsed["header"]["candidate"],
        "selectedIndex": branch["ordinal"],
        "selectedBranch": branch,
        "snapshot": branch["snapshot"],
        "lineageOrdinal": lineage_ordinal,
        "netOrderRows": [] if parsed.get("netOrder") is None else [parsed["netOrder"]],
        "p07OwnerSteps": logical_work["p07OwnerSteps"],
        "callbackUnits": entry["callbackUnits"],
        "cleanupNanos": entry["cleanupNanos"],
    })
    return adapted

def _pad_rectangle(row) -> tuple[int, int, int, int]:
    bounds = row["bounds"]
    need(type(bounds) is list and len(bounds) == 4
         and all(is_int(item, -MAX_COORD, MAX_COORD) for item in bounds), "PAD_BOUNDS")
    x, y, width, height = bounds
    need(width > 0 and height > 0 and -MAX_COORD <= x + width <= MAX_COORD
         and -MAX_COORD <= y + height <= MAX_COORD, "PAD_BOUNDS")
    need(x <= row["x"] <= x + width and y <= row["y"] <= y + height,
         "PAD_CENTER_OUTSIDE_BOUNDS")
    return x, y, width, height


def _validate_pad_lands(pad_lands, p07_pads) -> dict:
    need(type(pad_lands) is list and len(pad_lands) == len(p07_pads) == 246,
         "PAD_LAND_COUNT")
    by_id = {}
    previous = None
    for row in pad_lands:
        exact_keys(row, ("padId", "netId", "x", "y", "bounds", "attachment", "layer"),
                   "PAD_LAND_SCHEMA")
        _check_token(row["padId"], "PAD_LAND_ID")
        _check_token(row["netId"], "PAD_LAND_ID")
        need(previous is None or previous < row["padId"], "PAD_LAND_ORDER")
        previous = row["padId"]
        need(is_int(row["x"], -1_000_000, 1_000_000)
             and is_int(row["y"], -1_000_000, 1_000_000)
             and row["attachment"] == "PLATED_THROUGH_HOLE"
             and row["layer"] == "TOP+BOTTOM", "PAD_LAND_FIELDS")
        _pad_rectangle(row)
        by_id[row["padId"]] = row
    p07_by_id = {row["padId"]: row for row in p07_pads}
    need(set(by_id) == set(p07_by_id), "PAD_LAND_CENSUS")
    role_by_net = {}
    for pad_id, land in by_id.items():
        source = p07_by_id[pad_id]
        need(land["netId"] == source["netId"] and land["x"] == source["x"]
             and land["y"] == source["y"], "PAD_LAND_P07_BINDING")
        prior = role_by_net.setdefault(source["netId"], source["role"])
        need(prior == source["role"], "P07_NET_ROLE_INCONSISTENT")
    return {"byId": by_id, "p07ById": p07_by_id, "roleByNet": role_by_net}


def _rectangles_touch(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax + aw >= bx and bx + bw >= ax and ay + ah >= by and by + bh >= ay


def _stroke_rectangle(a, b, width: int) -> tuple[int, int, int, int]:
    x1, y1 = a
    x2, y2 = b
    need((x1 == x2) != (y1 == y2), "TRACE_SEGMENT_ORTHOGONAL")
    half = width // 2
    return min(x1, x2) - half, min(y1, y2) - half, abs(x2 - x1) + width, abs(y2 - y1) + width


def _circle_touches_rect(cx: int, cy: int, radius: int,
                         rect: tuple[int, int, int, int]) -> bool:
    x, y, width, height = rect
    closest_x = min(max(cx, x), x + width)
    closest_y = min(max(cy, y), y + height)
    dx, dy = cx - closest_x, cy - closest_y
    return dx * dx + dy * dy <= radius * radius


def _expand_physical_segment(a, b) -> list[tuple]:
    x1, y1 = a
    x2, y2 = b
    dx, dy = x2 - x1, y2 - y1
    need((dx == 0) != (dy == 0), "TRACE_SEGMENT_ORTHOGONAL")
    distance = abs(dx or dy)
    need(distance % 10 == 0 and distance > 0, "TRACE_GRID_PITCH")
    step_x = 0 if dx == 0 else (10 if dx > 0 else -10)
    step_y = 0 if dy == 0 else (10 if dy > 0 else -10)
    edges = []
    x, y = x1, y1
    for _ in range(distance // 10):
        next_point = (x + step_x, y + step_y)
        first, second = sorted(((x, y), next_point))
        edges.append((first, second))
        x, y = next_point
    return edges


def _validate_node_chain(node_chain, snapshot) -> dict:
    need(type(node_chain) is list and 2 <= len(node_chain) <= MAX_ROUTE_POINTS,
         "NODE_CHAIN_COUNT")
    nodes = []
    for node in node_chain:
        exact_keys(node, ("x", "y", "layer", "direction", "transitions", "cost"),
                   "NODE_SCHEMA")
        need(is_int(node["x"], -MAX_COORD, MAX_COORD)
             and is_int(node["y"], -MAX_COORD, MAX_COORD)
             and node["layer"] in ("TOP", "BOTTOM")
             and is_int(node["direction"], 0, 4)
             and is_int(node["transitions"], 0, 4)
             and is_int(node["cost"], 0, 2**31 - 1), "NODE_FIELDS")
        nodes.append(node)
    first = nodes[0]
    need(first["direction"] == 4 and first["transitions"] == 0
         and first["cost"] in (0, SECONDARY_LAYER_COST), "NODE_ORIGIN")
    primary_layer = first["layer"] if first["cost"] == 0 else (
        "BOTTOM" if first["layer"] == "TOP" else "TOP")
    planar_edges = []
    via_points = []
    turns = 0
    for index, (before, after) in enumerate(zip(nodes, nodes[1:])):
        dx, dy = after["x"] - before["x"], after["y"] - before["y"]
        if after["layer"] == before["layer"]:
            need((dx, dy) in DIR_DELTA.values() and after["transitions"] == before["transitions"],
                 "NODE_PLANAR_STEP")
            direction = next(code for code, delta in DIR_DELTA.items() if delta == (dx, dy))
            need(after["direction"] == direction, "NODE_DIRECTION")
            turned = before["direction"] != 4 and before["direction"] != direction
            if turned:
                turns += 1
            expected_cost = (before["cost"] + 10
                             + (SECONDARY_LAYER_COST if before["layer"] != primary_layer else 0)
                             + (TURN_COST if turned else 0))
            need(after["cost"] == expected_cost, "NODE_COST_ARITHMETIC")
            start, end = sorted(((before["x"], before["y"]), (after["x"], after["y"])))
            planar_edges.append((before["layer"], start, end))
        else:
            need(dx == 0 and dy == 0 and after["transitions"] == before["transitions"] + 1
                 and after["direction"] == 4
                 and after["cost"] == before["cost"] + LAYER_FLIP_COST,
                 "NODE_VIA_STEP")
            via_points.append((before["x"], before["y"]))
    need(nodes[-1]["transitions"] == len(via_points)
         and nodes[-1]["transitions"] <= 4
         and (nodes[0]["x"], nodes[0]["y"]) == (snapshot["startX"], snapshot["startY"])
         and (nodes[-1]["x"], nodes[-1]["y"]) == (snapshot["rootX"], snapshot["rootY"]),
         "NODE_ENDPOINTS_OR_TRANSITIONS")
    need(len({(node["x"], node["y"], node["layer"], node["direction"],
               node["transitions"]) for node in nodes}) == len(nodes), "NODE_REPEATED_STATE")
    return {"nodes": nodes, "planarEdges": planar_edges, "viaPoints": via_points,
            "planarSteps": len(planar_edges), "turns": turns,
            "totalCost": nodes[-1]["cost"], "primaryLayer": primary_layer}


def _validate_published_geometry(traces, holes, pad_data, target_net: str,
                                 start_pad: str, root_pad: str, node_facts: dict,
                                 require_complete: bool) -> dict:
    need(type(traces) is list and len(traces) <= 10_000
         and type(holes) is list and len(holes) <= 48, "PUBLISHED_GEOMETRY_COUNT")
    pads = pad_data["byId"]
    p07_by_id = pad_data["p07ById"]
    role = pad_data["roleByNet"].get(target_net)
    need(role in ROLE_WIDTH, "TARGET_NET_ROLE")
    expected_width = ROLE_WIDTH[role]
    trace_edges = []
    trace_length = 0
    trace_segments = []
    endpoint_refs = []
    source_ids = set()
    for trace in traces:
        exact_keys(trace, ("sourceId", "netId", "startPadId", "endPadId", "layer", "width", "x", "y"),
                   "TRACE_SCHEMA")
        need(type(trace["sourceId"]) is str and 0 < len(trace["sourceId"].encode("utf-8")) <= 512
             and re.fullmatch(r"[A-Za-z0-9_.:/+-]+", trace["sourceId"]) is not None,
             "TRACE_SOURCE_ID")
        need(trace["sourceId"] not in source_ids, "TRACE_SOURCE_DUPLICATE")
        source_ids.add(trace["sourceId"])
        need(trace["netId"] == target_net and trace["layer"] in ("TOP", "BOTTOM")
             and is_int(trace["width"], 9, MAX_TRACE_WIDTH) and trace["width"] == expected_width,
             "TRACE_IDENTITY_OR_WIDTH")
        x = trace["x"]
        y = trace["y"]
        need(type(x) is list and type(y) is list and 2 <= len(x) == len(y) <= 50_000
             and all(is_int(v, -MAX_COORD, MAX_COORD) for v in x + y), "TRACE_POINTS")
        start_id, end_id = trace["startPadId"], trace["endPadId"]
        for pad_id in (start_id, end_id):
            need(pad_id is None or (type(pad_id) is str and pad_id in pads
                                    and p07_by_id[pad_id]["netId"] == target_net),
                 "TRACE_ENDPOINT_ID")
        for idx in range(len(x) - 1):
            a, b = (x[idx], y[idx]), (x[idx + 1], y[idx + 1])
            edges = _expand_physical_segment(a, b)
            trace_length += abs(b[0] - a[0]) + abs(b[1] - a[1])
            stroke = _stroke_rectangle(a, b, trace["width"])
            trace_segments.append((trace["layer"], a, b, stroke))
            for pad_id, pad in pads.items():
                if p07_by_id[pad_id]["netId"] == target_net:
                    continue
                need(not _rectangles_touch(stroke, _pad_rectangle(pad)), "FOREIGN_PAD_CONTACT")
            trace_edges.extend((trace["layer"], first, second) for first, second in edges)
        if start_id is not None:
            endpoint_refs.append((start_id, (x[0], y[0])))
        if end_id is not None:
            endpoint_refs.append((end_id, (x[-1], y[-1])))
    for pad_id, point in endpoint_refs:
        rect = _pad_rectangle(pads[pad_id])
        px, py = point
        need(rect[0] <= px <= rect[0] + rect[2] and rect[1] <= py <= rect[1] + rect[3],
             "TRACE_ENDPOINT_GEOMETRY")

    hole_points = []
    hole_ids = set()
    for hole in holes:
        exact_keys(hole, ("id", "netId", "kind", "x", "y", "drillRadius", "landRadius"),
                   "HOLE_SCHEMA")
        need(type(hole["id"]) is str and 0 < len(hole["id"].encode("utf-8")) <= 256
             and re.fullmatch(r"[A-Za-z0-9_.:/+-]+", hole["id"]) is not None, "HOLE_ID")
        need(hole["id"] not in hole_ids, "HOLE_DUPLICATE")
        hole_ids.add(hole["id"])
        need(hole["netId"] == target_net and hole["kind"] == "VIA"
             and is_int(hole["x"], -MAX_COORD, MAX_COORD)
             and is_int(hole["y"], -MAX_COORD, MAX_COORD)
             and hole["drillRadius"] == VIA_DRILL_RADIUS
             and hole["landRadius"] == VIA_LAND_RADIUS, "HOLE_FIELDS")
        point = (hole["x"], hole["y"])
        hole_points.append(point)
        land_bounds = (point[0] - hole["landRadius"], point[1] - hole["landRadius"],
                       2 * hole["landRadius"], 2 * hole["landRadius"])
        for pad_id, pad in pads.items():
            if p07_by_id[pad_id]["netId"] == target_net:
                continue
            need(not _rectangles_touch(land_bounds, _pad_rectangle(pad)), "FOREIGN_VIA_LAND_CONTACT")
    need(len(set(hole_points)) == len(hole_points), "HOLE_DUPLICATE_POINT")
    if require_complete:
        need(traces and len(trace_edges) == len(node_facts["planarEdges"])
             and sorted(trace_edges) == sorted(node_facts["planarEdges"]), "TRACE_NODE_PATH_MISMATCH")
        need(sorted(hole_points) == sorted(node_facts["viaPoints"]), "VIA_NODE_PATH_MISMATCH")
        need(trace_length == node_facts["planarSteps"] * 10, "ROUTE_LENGTH_ARITHMETIC")
        need((start_pad, (pads[start_pad]["x"], pads[start_pad]["y"]))
             in endpoint_refs and (root_pad, (pads[root_pad]["x"], pads[root_pad]["y"]))
             in endpoint_refs, "ROUTE_PHYSICAL_ENDPOINTS")
    elif traces:
        need(set(hole_points).issubset(set(node_facts["viaPoints"])), "PARTIAL_VIA_NODE_BINDING")
        need(set(trace_edges).issubset(set(node_facts["planarEdges"])), "PARTIAL_TRACE_NODE_BINDING")
    for trace in traces:
        for pad_id in (trace["startPadId"], trace["endPadId"]):
            need(pad_id is None or pad_id in (start_pad, root_pad), "TRACE_UNEXPECTED_ENDPOINT")
    return {"traceCount": len(traces), "holeCount": len(holes),
            "traceLength": trace_length, "routeWidth": expected_width,
            "role": role, "planarEdgeCount": len(trace_edges),
            "viaCount": len(hole_points)}


def _strict_counter(value, name: str, limit: int = 2**63 - 1) -> None:
    need(is_int(value, 0, limit), name)


def _validate_validator_scope(disposition: str, has_candidate: bool, validators) -> None:
    exact_keys(validators, ("routingGeometry", "twoLayerRules", "routeQuality", "copperGraph",
                            "endpointsConnected", "allNetContinuity"), "VALIDATOR_SCHEMA")
    allowed = ("PASS", "FAIL", "NOT_RUN", "NOT_APPLICABLE")
    for name in ("routingGeometry", "twoLayerRules", "routeQuality", "copperGraph", "allNetContinuity"):
        need(validators[name] in allowed, "VALIDATOR_VALUE")
    need(validators["allNetContinuity"] == "NOT_RUN", "VALIDATOR_SCOPE")
    if disposition == "FOUND":
        need(has_candidate and validators["routingGeometry"] == "NOT_APPLICABLE"
             and validators["twoLayerRules"] == "PASS"
             and validators["routeQuality"] == "PASS"
             and validators["copperGraph"] == "PASS"
             and validators["endpointsConnected"] is True, "FOUND_VALIDATOR_SCOPE")
    else:
        need(validators["routingGeometry"] == "NOT_RUN"
             and validators["twoLayerRules"] == "NOT_RUN"
             and validators["routeQuality"] == "NOT_RUN"
             and validators["copperGraph"] == "NOT_RUN"
             and validators["endpointsConnected"] is None, "REJECTED_VALIDATOR_SCOPE")
        need((disposition == "QUALITY_REJECTED" and has_candidate)
             or (disposition == "NO_PATH" and not has_candidate)
             or disposition == "CAP_REJECTED", "REJECTED_CANDIDATE_SCOPE")


def _validate_result_document(doc, op1, op1_raw: bytes, manifest_sha: str,
                             wire_sha: str, run_id: str) -> dict:
    exact_keys(doc, ("schema", "mode", "runId", "finalRunManifestSha256", "wireContractSha256",
                     "operation1", "reference", "work", "source", "counterfactual", "cleanup"),
               "RESULT_SCHEMA")
    need(doc["schema"] == WIRE_SCHEMA and doc["mode"] == "OBSERVE" and doc["runId"] == run_id
         and doc["finalRunManifestSha256"] == manifest_sha and doc["wireContractSha256"] == wire_sha,
         "RESULT_IDENTITY")
    operation = doc["operation1"]
    exact_keys(operation, ("operation", "mode", "runId", "sourceManifestSha256", "wireSha256",
                           "receiptPath", "receiptBytes", "receiptSha256", "elapsedToCloseNanos",
                           "maxFullGroupNanos", "groupCount", "cleanupNanos", "p07OwnerSteps",
                           "callbackUnits", "ownersReleased", "contextRestored", "requestIndex",
                           "selectedAttempt", "rootSeed",
                           "placementSeed", "routingSeed"), "OP1_RESULT_SCHEMA")
    need(is_int(operation["operation"], 1, 1) and operation["mode"] == "AUTHORED"
         and operation["runId"] == run_id and operation["sourceManifestSha256"] == manifest_sha
         and operation["wireSha256"] == wire_sha and operation["receiptPath"] == "op-1.jsonl"
         and is_int(operation["receiptBytes"], 1, MAX_RECEIPT)
         and operation["receiptBytes"] == len(op1_raw)
         and operation["receiptSha256"] == sha(op1_raw)
         and is_int(operation["elapsedToCloseNanos"], 1, 90_000_000_000)
         and is_int(operation["maxFullGroupNanos"], 1, 5_000_000_000)
         and is_int(operation["groupCount"], 1, 640)
         and operation["groupCount"] == len(op1["groups"])
         and is_int(operation["cleanupNanos"], 0, 120_000_000_000)
         and is_int(operation["p07OwnerSteps"], 0, 32_768 * 640)
         and operation["p07OwnerSteps"] == op1["p07OwnerSteps"]
         and is_int(operation["callbackUnits"], 0, 32_768)
         and operation["ownersReleased"] is True and operation["contextRestored"] is True
         and is_int(operation["requestIndex"], 0, 0) and is_int(operation["selectedAttempt"], 4, 4)
         and (operation["rootSeed"], operation["placementSeed"], operation["routingSeed"]) == SEED,
         "OP1_RESULT_BINDING")
    p07_group_max = max(row["elapsedBeforeOwnLineNanos"] for row in op1["groups"])
    need(operation["maxFullGroupNanos"] >= p07_group_max
         and operation["elapsedToCloseNanos"] >= sum(row["elapsedBeforeOwnLineNanos"]
                                                       for row in op1["groups"]),
         "OP1_TIMING_BINDING")

    reference = doc["reference"]
    exact_keys(reference, ("receiptOrdinal", "groupOrdinal", "lineageOrdinal", "pass", "netId",
                           "startPadId", "rootPadId", "terminalReason", "prefixGeometrySha256",
                           "prefixGeometryBytes", "prefixHoleCount", "prefixViaCount", "reachedPadIds",
                           "reachedPthPadIds", "reachedPadCount", "reachedPthPadCount"),
               "REFERENCE_SCHEMA")
    branch = op1["selectedBranch"]
    snapshot = op1["snapshot"]
    need(reference["receiptOrdinal"] == op1["selectedIndex"] == 307
         and branch["ordinal"] == 307
         and is_int(reference["groupOrdinal"], 0, 639)
         and reference["groupOrdinal"] == branch["group"]
         and is_int(reference["lineageOrdinal"], 0, 100_000)
         and reference["lineageOrdinal"] == op1["lineageOrdinal"] == 77
         and is_int(reference["pass"], 0, 2) and reference["pass"] == snapshot["pass"] == 2
         and reference["netId"] == snapshot["netId"] == "A_COIL_LOW"
         and reference["startPadId"] == snapshot["startPadId"] == "QA.C"
         and reference["rootPadId"] == snapshot["rootPadId"] == "DA.A"
         and reference["terminalReason"] == snapshot["terminalReason"] == "NO_PATH"
         and reference["prefixGeometrySha256"] == PREFIX_SHA256 == branch["prefixSha256"]
         and is_int(reference["prefixGeometryBytes"], 1, 1024 * 1024)
         and reference["prefixGeometryBytes"] == branch["prefixBytes"]
         and is_int(reference["prefixHoleCount"], 0, 48) and reference["prefixHoleCount"] == 48
         and is_int(reference["prefixViaCount"], 0, 48)
         and reference["prefixViaCount"] == snapshot["prefixViaCount"]
         and type(reference["reachedPadIds"]) is list
         and all(type(x) is str for x in reference["reachedPadIds"])
         and reference["reachedPadIds"] == ["DA.A"]
         and type(reference["reachedPthPadIds"]) is list
         and reference["reachedPthPadIds"] == []
         and is_int(reference["reachedPadCount"], 0, 246)
         and reference["reachedPadCount"] == len(reference["reachedPadIds"])
         and reference["reachedPadCount"] == snapshot["reachedPadCount"]
         and is_int(reference["reachedPthPadCount"], 0, 246)
         and reference["reachedPthPadCount"] == len(reference["reachedPthPadIds"]) == 0,
         "REFERENCE_BINDING")
    callback_group = next((row for row in op1["groups"]
                           if row["ordinal"] == reference["groupOrdinal"]), None)
    need(callback_group is not None
         and (callback_group["p07Steps"] or 0) + callback_group["diagnosticUnits"]
         + operation["callbackUnits"] <= 32_768, "CALLBACK_GROUP_UNITS")

    source = doc["source"]
    exact_keys(source, ("templateGeometryBeforeSha256", "templateGeometryAfterSha256",
                        "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
                        "detachedGeometrySha256", "detachedGeometryAfterSha256", "padLands",
                        "templateTraceCount", "templateHoleCount", "sourceUnchanged"),
               "SOURCE_SCHEMA")
    for name in ("templateGeometryBeforeSha256", "templateGeometryAfterSha256",
                 "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
                 "detachedGeometrySha256", "detachedGeometryAfterSha256"):
        need(is_digest(source[name]), "SOURCE_DIGEST")
    need(source["templateGeometryBeforeSha256"] == source["templateGeometryAfterSha256"]
         and source["templateEffectsBeforeSha256"] == source["templateEffectsAfterSha256"]
         and source["detachedGeometrySha256"] == source["detachedGeometryAfterSha256"]
         and is_int(source["templateTraceCount"], 0, 0)
         and is_int(source["templateHoleCount"], 0, 0)
         and source["sourceUnchanged"] is True, "SOURCE_IMMUTABILITY")
    pad_data = _validate_pad_lands(source["padLands"], op1["pads"])

    work = doc["work"]
    exact_keys(work, ("elapsedNanos", "units", "groups", "maxGroupNanos", "maximumUnitsPerGroup",
                      "resultMetadataUnits", "resultMetadataGroups"), "WORK_SCHEMA")
    for name in ("elapsedNanos", "units", "groups", "maxGroupNanos", "maximumUnitsPerGroup",
                 "resultMetadataUnits", "resultMetadataGroups"):
        _strict_counter(work[name], "WORK_INTEGER", 2**63 - 1)
    need(0 < work["elapsedNanos"] <= 90_000_000_000
         and work["groups"] <= 640 and work["maxGroupNanos"] <= 5_000_000_000
         and 1 <= work["maximumUnitsPerGroup"] <= 32_768
         and work["resultMetadataUnits"] == 2 and work["resultMetadataGroups"] == 1,
         "WORK_BOUNDS")

    counter = doc["counterfactual"]
    exact_keys(counter, ("disposition", "reason", "policy", "pass", "netId", "startPadId", "rootPadId",
                         "initialTraceCount", "initialHoleCount", "maximumVias", "maximumTransitions",
                         "maximumRequestExpansions", "maximumBranchExpansions", "work", "nodeChain",
                         "publishedTraces", "publishedHoles", "validators"), "COUNTERFACTUAL_SCHEMA")
    need(counter["disposition"] in ("FOUND", "NO_PATH", "QUALITY_REJECTED", "CAP_REJECTED")
         and (counter["reason"] is None or (type(counter["reason"]) is str and counter["reason"]))
         and counter["policy"] == "FULLER_TWO_LAYER"
         and is_int(counter["pass"], 2, 2) and counter["netId"] == "A_COIL_LOW"
         and counter["startPadId"] == "QA.C" and counter["rootPadId"] == "DA.A"
         and is_int(counter["initialTraceCount"], 0, 0)
         and is_int(counter["initialHoleCount"], 0, 0)
         and is_int(counter["maximumVias"], 48, 48)
         and is_int(counter["maximumTransitions"], 4, 4)
         and is_int(counter["maximumRequestExpansions"], 1_000_000, 1_000_000)
         and is_int(counter["maximumBranchExpansions"], 100_000, 100_000),
         "COUNTERFACTUAL_IDENTITY_OR_LIMITS")
    phase_work = counter["work"]
    exact_keys(phase_work, ("elapsedNanos", "units", "searchCalls", "publisherCalls", "expansions",
                            "groups", "maxGroupNanos"), "PHASE_WORK_SCHEMA")
    for name in phase_work:
        _strict_counter(phase_work[name], "PHASE_WORK_INTEGER", 2**63 - 1)
    need(phase_work["elapsedNanos"] <= 90_000_000_000
         and phase_work["expansions"] <= 100_000
         and phase_work["groups"] <= 640 and phase_work["maxGroupNanos"] <= 5_000_000_000,
         "PHASE_WORK_BOUNDS")
    expected_units = operation["p07OwnerSteps"] + operation["callbackUnits"] + phase_work["units"] + 2
    expected_groups = operation["groupCount"] + phase_work["groups"] + 1
    p07_max_group_units = max((row["p07Steps"] or 0) + row["diagnosticUnits"]
                              + (operation["callbackUnits"] if row["ordinal"] == reference["groupOrdinal"] else 0)
                              for row in op1["groups"])
    need(work["groups"] == expected_groups and work["units"] == expected_units
         and work["maxGroupNanos"] >= max(operation["maxFullGroupNanos"],
                                            phase_work["maxGroupNanos"])
         and work["maximumUnitsPerGroup"] >= max(p07_max_group_units, 2)
         and work["elapsedNanos"] >= operation["elapsedToCloseNanos"] + phase_work["elapsedNanos"],
         "COMBINED_WORK_CENSUS")

    validators = counter["validators"]
    _validate_validator_scope(counter["disposition"], counter["nodeChain"] is not None, validators)
    need(type(counter["publishedTraces"]) is list and type(counter["publishedHoles"]) is list,
         "PUBLISHED_LISTS")
    node_chain = counter["nodeChain"]
    if node_chain is None:
        need(counter["disposition"] in ("NO_PATH", "CAP_REJECTED")
             and counter["publishedTraces"] == [] and counter["publishedHoles"] == []
             and validators["endpointsConnected"] is None, "NO_SEARCH_PATH_STATE")
        node_facts = {"nodes": [], "planarEdges": [], "viaPoints": [], "planarSteps": 0,
                      "turns": 0, "totalCost": 0, "primaryLayer": None}
        physical = {"traceCount": 0, "holeCount": 0, "traceLength": 0,
                    "routeWidth": ROLE_WIDTH[pad_data["roleByNet"]["A_COIL_LOW"]],
                    "role": pad_data["roleByNet"]["A_COIL_LOW"], "planarEdgeCount": 0, "viaCount": 0}
    else:
        node_facts = _validate_node_chain(node_chain, snapshot)
        require_complete = counter["disposition"] == "FOUND"
        physical = _validate_published_geometry(counter["publishedTraces"], counter["publishedHoles"],
                                                pad_data, "A_COIL_LOW", "QA.C", "DA.A",
                                                node_facts, require_complete)

    cleanup = doc["cleanup"]
    exact_keys(cleanup, ("elapsedNanos", "searchReleased", "branchReleased", "publisherReleased",
                         "sessionReleased", "layoutReleased", "templateReleased", "boardReleased",
                         "referencesReleased", "contextRestored"), "CLEANUP_SCHEMA")
    _strict_counter(cleanup["elapsedNanos"], "CLEANUP_TIME", 120_000_000_000)
    need(all(cleanup[name] is True for name in ("searchReleased", "branchReleased", "publisherReleased",
                                                "sessionReleased", "layoutReleased", "templateReleased",
                                                "boardReleased", "referencesReleased", "contextRestored"))
         and operation["cleanupNanos"] + cleanup["elapsedNanos"] <= 120_000_000_000,
         "CLEANUP_PROOF")
    return {"disposition": counter["disposition"], "source": source,
            "operation1": operation, "reference": reference, "work": work,
            "phaseWork": phase_work, "cleanup": cleanup, "validators": validators,
            "nodeFacts": node_facts, "physical": physical, "padData": pad_data}


def validate_run(manifest_path: str | Path, manifest_sha: str, parent_path: str | Path,
                 data_path: str | Path) -> dict:
    context = validate_manifest(manifest_path, manifest_sha)
    parent_file = _canonical(parent_path, "NATIVE_PARENT_PATH")
    _inside(parent_file, ROOT, "NATIVE_PARENT_ROOT")
    need(parent_file.name == "parent.json", "NATIVE_PARENT_PATH")
    call_dir = parent_file.parent
    data_dir = _canonical(data_path, "NATIVE_DATA_PATH")
    _inside(data_dir, ROOT, "NATIVE_DATA_ROOT")
    _lstat_directory(data_dir, "NATIVE_DATA_DIRECTORY")
    need(data_dir != call_dir and data_dir.name.startswith("native-data-"), "NATIVE_DATA_PATH")
    try:
        children = sorted(item.name for item in data_dir.iterdir())
    except OSError:
        raise ReaderError("NATIVE_DATA_READ") from None
    need(children == ["op-1.jsonl", "result.json"], "NATIVE_DATA_TREE")

    parent_raw = _read(parent_file, 256 * 1024, "NATIVE_PARENT_READ")
    parent = host_document(parent_raw, 256 * 1024)
    exact_keys(parent, ("schema", "elapsedMs", "process"), "NATIVE_PARENT_SCHEMA")
    process = parent["process"]
    process_args = process.get("Arguments") if type(process) is dict else None
    need(type(process_args) is list and len(process_args) == 9
         and process_args[0] == "-ea" and process_args[1] == "-cp"
         and process_args[3] == "com.lushprojects.circuitjs1.client.CopperFreeBranchStudy"
         and process_args[4] == "OBSERVE" and type(process_args[5]) is str
         and UUID.fullmatch(process_args[5]) is not None, "NATIVE_ARGUMENTS")
    run_id = process_args[5]
    need(process_args[6] == context["sha256"] and process_args[7] == WIRE_SHA256,
         "NATIVE_ARGUMENT_BINDING")
    expected_cp = ";".join((str(context["classesRoot"]),
                            next(row["path"] for row in context["tools"]
                                 if Path(row["path"]).name == "gwt-user-2.7.0.jar"),
                            next(row["path"] for row in context["tools"]
                                 if Path(row["path"]).name == "gwt-dev-2.7.0.jar")))
    need(process_args[2] == expected_cp and process_args[8] == str(data_dir),
         "NATIVE_CLASSPATH_OR_OUTPUT")
    expected_args = ["-ea", "-cp", expected_cp,
                     "com.lushprojects.circuitjs1.client.CopperFreeBranchStudy",
                     "OBSERVE", run_id, context["sha256"], WIRE_SHA256, str(data_dir)]
    request_raw = _read(call_dir / "request.json", 64 * 1024, "NATIVE_REQUEST_READ")
    request = host_document(request_raw, 64 * 1024)
    exact_keys(request, ("FilePath", "Arguments", "LimitMs"), "NATIVE_REQUEST_SCHEMA")
    process_file = context["java"]["path"]
    _validate_owner_call(call_dir, parent_file, parent, request, process,
                         process_file, expected_args, 480_000, context["processModule"])

    op1_raw = _read(data_dir / "op-1.jsonl", MAX_RECEIPT, "OP1_READ")
    result_raw = _read(data_dir / "result.json", MAX_JSON, "RESULT_READ")
    marker_raw = process["Stdout"].encode("utf-8", errors="strict")
    marker = strict_console_line(marker_raw, "NATIVE_MARKER")
    exact_keys(marker, ("schema", "status", "resultSha256", "resultBytes"), "NATIVE_MARKER_SCHEMA")
    need(marker["schema"] == MARKER_SCHEMA and marker["status"] == "COMPLETED_DIAGNOSTIC"
         and marker["resultSha256"] == sha(result_raw)
         and is_int(marker["resultBytes"], 1, MAX_JSON) and marker["resultBytes"] == len(result_raw),
         "NATIVE_MARKER_BINDING")
    result_doc = strict_document(result_raw, MAX_JSON)
    operation_entry = result_doc.get("operation1") if type(result_doc) is dict else None
    need(type(operation_entry) is dict, "OP1_RESULT_SCHEMA")
    op1 = _parse_operation1(op1_raw, operation_entry, run_id, context["sha256"],
                            WIRE_SHA256, context["p07Reader"])
    checked = _validate_result_document(result_doc, op1, op1_raw, context["sha256"],
                                        WIRE_SHA256, run_id)
    try:
        context["parentAuditor"].recheck_inputs(context["parentInputContext"])
    except Exception:
        raise ReaderError("PARENT_INPUT_RECHECK") from None
    return {"context": context, "parent": parent, "processResult": request,
            "dataDirectory": data_dir, "runId": run_id,
            "op1Raw": op1_raw, "op1Sha256": sha(op1_raw), "op1": op1,
            "resultRaw": result_raw, "resultSha256": sha(result_raw),
            "nativeStdoutMarker": marker, "checked": checked}


def public_result(verified: dict) -> dict:
    checked = verified["checked"]
    node = checked["nodeFacts"]
    physical = checked["physical"]
    source = checked["source"]
    op1 = checked["operation1"]
    ref = checked["reference"]
    work = checked["work"]
    cleanup = checked["cleanup"]
    return {
        "schema": "tsj-copper-free-branch-reader-v1",
        "status": "PASS_ISOLATED_BRANCH_DIAGNOSTIC_ONLY",
        "disposition": checked["disposition"],
        "scope": "SAME_AUTHORED_BRANCH_COPPER_FREE_DIAGNOSTIC",
        "identitiesBound": True,
        "reference": {
            "receiptOrdinal": ref["receiptOrdinal"], "groupOrdinal": ref["groupOrdinal"],
            "lineageOrdinal": ref["lineageOrdinal"], "pass": ref["pass"],
            "prefixGeometrySha256": ref["prefixGeometrySha256"],
            "prefixGeometryBytes": ref["prefixGeometryBytes"],
            "prefixHoleCount": ref["prefixHoleCount"], "prefixViaCount": ref["prefixViaCount"],
            "reachedPadCount": ref["reachedPadCount"], "reachedPthPadCount": ref["reachedPthPadCount"],
        },
        "source": {"templateGeometryUnchanged": source["templateGeometryBeforeSha256"]
                   == source["templateGeometryAfterSha256"],
                   "templateEffectsUnchanged": source["templateEffectsBeforeSha256"]
                   == source["templateEffectsAfterSha256"],
                   "detachedGeometryUnchanged": source["detachedGeometrySha256"]
                   == source["detachedGeometryAfterSha256"],
                   "padCount": len(source["padLands"]),
                   "templateTraceCount": source["templateTraceCount"],
                   "templateHoleCount": source["templateHoleCount"]},
        "route": {"nodeCount": len(node["nodes"]), "planarStepCount": node["planarSteps"],
                  "turnCount": node["turns"], "transitionCount": len(node["viaPoints"]),
                  "totalSearchCost": node["totalCost"], "publishedTraceCount": physical["traceCount"],
                  "publishedHoleCount": physical["holeCount"], "traceLength": physical["traceLength"],
                  "traceWidth": physical["routeWidth"], "routeRole": physical["role"]},
        "validators": checked["validators"],
        "work": {"elapsedNanos": work["elapsedNanos"], "units": work["units"],
                 "groups": work["groups"], "maxGroupNanos": work["maxGroupNanos"],
                 "maximumUnitsPerGroup": work["maximumUnitsPerGroup"],
                 "resultMetadataUnits": work["resultMetadataUnits"],
                 "resultMetadataGroups": work["resultMetadataGroups"],
                 "operation1CallbackUnits": op1["callbackUnits"]},
        "cleanup": {"operation1Nanos": op1["cleanupNanos"], "counterfactualNanos": cleanup["elapsedNanos"],
                    "allReferencesReleased": cleanup["referencesReleased"],
                    "contextRestored": cleanup["contextRestored"]},
        "evidence": {"manifestSha256": verified["context"]["sha256"],
                     "wireSha256": WIRE_SHA256,
                     "operation1Sha256": verified["op1Sha256"],
                     "resultSha256": verified["resultSha256"],
                     "classCount": len(verified["context"]["classInventory"])},
        "layoutQualification": False,
        "productionAdopted": False,
        "playable100Accepted": False,
        "allNetContinuityClaimed": False,
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="read_counterfactual.py", add_help=True)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--manifest-sha", required=True)
    parser.add_argument("--parent", required=True)
    parser.add_argument("--data", required=True)
    args = parser.parse_args(argv)
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        print("BF_ISOLATION_REQUIRED", file=sys.stderr)
        return 2
    try:
        output = public_result(validate_run(args.manifest, args.manifest_sha,
                                            args.parent, args.data))
        sys.stdout.write(json.dumps(output, ensure_ascii=True, allow_nan=False,
                                    separators=(",", ":")) + "\n")
        return 0
    except ReaderError as exc:
        print(exc.code, file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError, TypeError, OverflowError, RuntimeError):
        print("BF_INTERNAL_VALIDATION", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())




