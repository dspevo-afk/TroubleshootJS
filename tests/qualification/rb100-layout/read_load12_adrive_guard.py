#!/usr/bin/env python3
"""Independently validate the bounded actual LOAD12 A_DRIVE guard witness.

This reader validates one copied-order pre-guard rejection and its native
transport provenance. It does not qualify or adopt a route or board layout.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import re
import stat
import sys
import types
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-load12-cost240-adrive-guard-input-v1"
RESULT_SCHEMA = "p07-load12-cost240-adrive-guard-reader-result-v1"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
EVENT_SCHEMA = "rb100-load12-adrive-guard-witness-r1"
TARGET = "LOAD12"
PURPOSE = "ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD"
SUITE = "Rb100Load12DetachedBaselineContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
BASE_EVENT_MARKER = "RB100_LOAD12_COST240_REMAINDER_EVENT"
GUARD_EVENT_MARKER = "RB100_LOAD12_ADRIVE_GUARD_EVENT"
SUMMARY_PREFIX = "PASS: RB100 LOAD12 detached cost240 guard contracts assertions="
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

SOURCE_REVISION = 20
SOURCE_INPUT_COUNT = 1_080
PRODUCTION_JAVA_COUNT = 850
CONTRACT_JAVA_COUNT = 224
MAX_SOURCE_BYTES = 512 * 1024 * 1024
MAX_PIN_BYTES = 16 * 1024 * 1024
MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_LINE_BYTES = 16 * 1024 * 1024
MAX_GUARD_PAYLOAD_BYTES = 1024 * 1024
MAX_PATH_CHARS = 4096
MAX_NATIVE_LIMIT_MS = 480_000
MAX_OUTER_LIMIT_MS = 1_800_000
MAX_CLEANUP_MS = 120_000
MAX_OPERATION_MS = 90_000
MAX_WORK_UNITS = 640
MAX_UNIT_MS = 5_000
MAX_OWNER_STEPS_PER_UNIT = 32_768
MAX_REQUEST_EXPANSIONS = 1_000_000
MAX_BRANCH_EXPANSIONS = 100_000
MAX_TRANSITIONS = 4
MAX_ORDERINGS = 3
MAX_VIAS = 48
MAX_GRID_CELLS = 250_000
MAX_WITNESS_NODES = 4_096
GRID = 10
MAX_SCALAR = 2_147_483_647
MIN_SCALAR = -(1 << 31)
MAX_JAVA_LONG = (1 << 63) - 1
MAX_OPERATION_NS = MAX_OPERATION_MS * 1_000_000

BASE_READER_SHA256 = (
    "a22690e299f14275806007894054e9087d8c4fc46b89c08507dbaa1687308e13"
)
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
SUMMARY_RE = re.compile(re.escape(SUMMARY_PREFIX) + r"([1-9][0-9]{0,9})\Z", re.ASCII)
TICKS_RE = re.compile(r"[1-9][0-9]{0,19}\Z", re.ASCII)
RUN_ID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII)
WRAPPER_FAILURE_RE = re.compile(
    r"^(?:FAIL(?:ED|URE)?|ERROR|FATAL)\b|^\[ERROR\](?:\s|:|$)|"
    r"^Exception(?:\s|:|$)|^Traceback(?:\s|:|$)|"
    r"^CategoryInfo\s*:|^FullyQualifiedErrorId\s*:|"
    r"^.*:\d+:\s*error:", re.IGNORECASE | re.ASCII)

INPUT_SPEC_KEYS = frozenset((
    "schema target purpose suite sourceRoot pinsPath pinsSha256 inputCount inputBytes "
    "sourceSnapshotRevision resultPath stdoutPath receiptPath stderrPath").split())
RUN_RESULT_KEYS = frozenset((
    "schema source suite cohortId command layoutVersion dependencyEpoch sourceSnapshotRevision "
    "inputCount inputBytes inputPinsSha256 inputAudit exitCode nativeExitCode "
    "terminationProven cleanupStatus cleanupElapsedMs viaCost nativeLimitMs outerLimitMs "
    "processId processStartTicks elapsedSeconds requestedExecutable stdoutBytes stdoutSha256 "
    "stderrBytes stderrSha256 receiptBytes receiptSha256 target").split())
GUARD_KEYS = frozenset((
    "protocolVersion schema status runId operationId scope base inputs lineage target grid "
    "endpoints path checkpoint boundary prefix work cleanup qualification").split())
BASE_BINDING_KEYS = frozenset("runId eventSha256".split())
INPUTS_KEYS = frozenset((
    "rootSeed placementSeed routingSeed routeRequestIndex selectedPlacementIndex "
    "selectedPlacementAttempt sourcePass copyMaximumVias copyViaCost").split())
LINEAGE_KEYS = frozenset("source copy".split())
SOURCE_PROJECTION_KEYS = frozenset((
    "routeSessionIdentity searchIdentity branchIdentity layoutIdentity digests preserved"
).split())
COPY_PROJECTION_KEYS = frozenset((
    "ownerIdentity searchIdentity branchIdentity layoutIdentity digests requested "
    "continuationHandoff").split())
TARGET_KEYS = frozenset((
    "ordering pass netId netVectorPosition searchNetIndex netVectorIdentity branchOrdinal "
    "searchBranches ownerIdentity searchIdentity layoutIdentity branchIdentity "
    "publisherIdentity foundNodeIdentity foundTerminalIdentity").split())
GRID_KEYS = frozenset("width height cells originX originY pitch".split())
ENDPOINTS_KEYS = frozenset("start root end foundTerminal".split())
PAD_ENDPOINT_KEYS = frozenset("padId identity x y cell".split())
NODE_ENDPOINT_KEYS = frozenset("identity x y cell".split())
PATH_KEYS = frozenset("encoding nodeCount maximumNodes rowsSha256 nodes".split())
NODE_ENCODING = "cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow"
CHECKPOINT_KEYS = frozenset((
    "publisherStage qualityIndex reversedSize storedLength storedBends storedLastDirection "
    "direct recomputedLength recomputedBends transitions directZero "
    "lengthOverThreeDirect bendsOver16 rejected").split())
BOUNDARY_KEYS = frozenset((
    "status type reason orderings cumulativeExpansions remainingRequestBudget "
    "sameTypedBoundary rejectedBranchPublished").split())
PREFIX_KEYS = frozenset("before after unchanged".split())
PREFIX_SNAPSHOT_KEYS = frozenset((
    "layoutIdentity geometrySha256 traceCount traceRowsSha256 holeCount holeRowsSha256"
).split())
WORK_KEYS = frozenset((
    "budgetPhase controllerUnitLimit ownerStepsPerUnitLimit controllerUnits subphases "
    "continuationAdvanceApiCalls continuationOwnerSteps maximumGroupOwnerSteps "
    "operationElapsedNanos phaseElapsedNanos overheadElapsedNanos capturedNodeRows "
    "verifiedNodeRows pathRowsUtf8Bytes").split())
SUBPHASE_NAMES = ("advance", "capture", "recompute", "serialize", "boundaryCheck")
SUBPHASE_KEYS = frozenset("controllerUnits elapsedNanos".split())
CLEANUP_KEYS = frozenset("status elapsedMs limitMs".split())
QUALIFICATION_KEYS = frozenset((
    "routeAccepted geometryPublished layoutQualification routeAdoption playable100"
).split())

EXPECTED_INPUTS = {
    "rootSeed": "9007199254740993",
    "placementSeed": "-6587958367974952701",
    "routingSeed": "8445301674744508279",
    "routeRequestIndex": 0,
    "selectedPlacementIndex": 0,
    "selectedPlacementAttempt": 4,
    "sourcePass": 1,
    "copyMaximumVias": 48,
    "copyViaCost": 240,
}


class ADriveGuardInputError(ValueError):
    """Sanitized failure; no source paths or artifact contents are included."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise ADriveGuardInputError(code)


def _need(condition: Any, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("JSON_CANONICALIZATION_INVALID")


def _same_json(left: Any, right: Any) -> bool:
    return _canonical_json(left) == _canonical_json(right)


def _identity(value: Any, code: str) -> int:
    _need(_integer(value, MIN_SCALAR, MAX_SCALAR), code)
    return value


def _digest(value: Any, code: str) -> str:
    _need(type(value) is str and SHA256_RE.fullmatch(value) is not None, code)
    return value


def _bounded_text(value: Any, maximum: int, code: str) -> str:
    _need(type(value) is str and 1 <= len(value) <= maximum and
          all(ord(char) >= 0x20 and ord(char) != 0x7f for char in value), code)
    return value


def _bootstrap_is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        junction = getattr(path, "is_junction", None)
        if callable(junction) and junction():
            return True
        info = path.lstat()
        attributes = getattr(info, "st_file_attributes", 0)
        return stat.S_ISLNK(info.st_mode) or bool(attributes & 0x400)
    except FileNotFoundError:
        return False
    except (OSError, RuntimeError, ValueError):
        return True


def _bootstrap_has_reparse(path: Path) -> bool:
    try:
        absolute = Path(os.path.abspath(os.fspath(path)))
        current = Path(absolute.anchor)
        for part in absolute.parts[1:]:
            current = current / part
            if _bootstrap_is_reparse(current):
                return True
        return False
    except (OSError, RuntimeError, TypeError, ValueError):
        return True


def _bootstrap_read(path: Path, limit: int, code: str) -> bytes:
    try:
        _need(not _bootstrap_has_reparse(path), code + "_REPARSE_PATH")
        info = path.stat()
        _need(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= limit,
              code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit,
              code + "_READ_SIZE_CHANGED")
        return raw
    except ADriveGuardInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _load_base_reader() -> Tuple[types.ModuleType, bytes, Path]:
    helper_path = Path(os.path.abspath(__file__)).parent / "read_load12_remainder.py"
    raw = _bootstrap_read(helper_path, MAX_RESULT_BYTES, "BASE_READER")
    _need(_sha256(raw) == BASE_READER_SHA256, "BASE_READER_HASH_MISMATCH")
    try:
        source = raw.decode("utf-8", errors="strict")
        module = types.ModuleType("_p07_adrive_guard_base_reader_private")
        module.__file__ = os.fspath(helper_path)
        module.__package__ = ""
        exec(compile(source, os.fspath(helper_path), "exec", dont_inherit=True),
             module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("BASE_READER_SOURCE_INVALID")
    after = _bootstrap_read(helper_path, MAX_RESULT_BYTES, "BASE_READER_RECHECK")
    _need(after == raw and _sha256(after) == BASE_READER_SHA256,
          "BASE_READER_CHANGED_DURING_LOAD")
    for name in ("_validate_event", "_audit_inputs_manifest", "_strict_json",
                 "_parse_tagged", "_resolve_path", "_read_bounded",
                 "_decode_lines", "_decode_wrapper_lines"):
        _need(callable(getattr(module, name, None)), "BASE_READER_API_INVALID")
    return module, raw, helper_path


def _base_call(helper: types.ModuleType, function_name: str, fallback: str,
               *args, **kwargs):
    function = getattr(helper, function_name, None)
    _need(callable(function), "BASE_READER_API_INVALID")
    try:
        return function(*args, **kwargs)
    except getattr(helper, "Load12RemainderInputError", ()):  # type: ignore[arg-type]
        _fail(fallback)


def _strict_json(raw: bytes, label: str,
                 helper: Optional[types.ModuleType] = None) -> Any:
    if helper is None:
        helper, _raw, _path = _load_base_reader()
    return _base_call(helper, "_strict_json", label + "_JSON_INVALID", raw, label)


def _parse_tagged(line: str, marker: str, code: str,
                  helper: Optional[types.ModuleType] = None) -> Any:
    if helper is None:
        helper, _raw, _path = _load_base_reader()
    return _base_call(helper, "_parse_tagged", code + "_INVALID",
                      line, marker, code)


def _resolve_path(value: Any, base: Path, code: str,
                  helper: Optional[types.ModuleType] = None) -> Path:
    if helper is None:
        helper, _raw, _path = _load_base_reader()
    return _base_call(helper, "_resolve_path", code + "_PATH_INVALID",
                      value, base, code)


def _read_bounded(path: Path, limit: int, code: str,
                  allow_empty: bool = False,
                  helper: Optional[types.ModuleType] = None) -> bytes:
    if helper is None:
        helper, _raw, _helper_path = _load_base_reader()
    return _base_call(helper, "_read_bounded", code + "_READ_FAILED",
                      path, limit, code, allow_empty=allow_empty)


def _audit_inputs_manifest(raw: bytes, spec: Mapping[str, Any], source_root: Path,
                           helper: Optional[types.ModuleType] = None) -> Dict[str, Any]:
    if helper is None:
        helper, _helper_raw, _helper_path = _load_base_reader()
    return _base_call(helper, "_audit_inputs_manifest", "SOURCE_MANIFEST_INVALID",
                      raw, spec, source_root)


def _bind_selected_source_root(source_root: Path,
                               helper: types.ModuleType) -> Path:
    """Bind the input manifest to this reader's selected checkout root."""
    reader_path = Path(os.path.abspath(__file__))
    _need(len(reader_path.parents) > 3, "READER_WORKTREE_ROOT_UNAVAILABLE")
    selected = reader_path.parents[3]
    selected_root = _resolve_path(os.fspath(selected), selected.parent,
                                  "READER_WORKTREE_ROOT", helper)
    _need(selected_root.is_dir() and source_root == selected_root,
          "INPUT_SPEC_SOURCE_ROOT_NOT_SELECTED_WORKTREE")
    return selected_root


def _expected_command() -> Dict[str, Any]:
    return {
        "entry": ENTRY_SCRIPT,
        "suite": SUITE,
        "suites": [SUITE],
        "args": ["-Rb100Load12Cost240GuardReplay"],
        "environment": {},
    }


def _validate_input_spec(value: Any, spec_directory: Path,
                         helper: types.ModuleType) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    _exact_keys(value, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    _need(value["schema"] == INPUT_SCHEMA and value["target"] == TARGET and
          value["purpose"] == PURPOSE and value["suite"] == SUITE and
          type(value["sourceSnapshotRevision"]) is int and
          value["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(value["inputCount"]) is int and
          value["inputCount"] == SOURCE_INPUT_COUNT and
          _integer(value["inputBytes"], 1, MAX_SOURCE_BYTES) and
          type(value["pinsSha256"]) is str and
          SHA256_RE.fullmatch(value["pinsSha256"]) is not None,
          "INPUT_SPEC_IDENTITY_INVALID")
    source_root = _resolve_path(value["sourceRoot"], spec_directory,
                                "SOURCE_ROOT", helper)
    _need(source_root.is_dir(), "SOURCE_ROOT_NOT_DIRECTORY")
    _bind_selected_source_root(source_root, helper)
    pins_path = _resolve_path(value["pinsPath"], spec_directory,
                              "SOURCE_PINS", helper)
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS", helper=helper)
    pin_context = _audit_inputs_manifest(pins_raw, value, source_root, helper)
    artifacts = {
        name: _resolve_path(value[name], spec_directory, name.upper(), helper)
        for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath")
    }
    artifact_paths = list(artifacts.values())
    _need(len(set(artifact_paths)) == len(artifact_paths) and
          pins_path not in artifact_paths and source_root not in artifact_paths,
          "INPUT_SPEC_ARTIFACT_PATH_ALIAS")
    return dict(value), {
        "sourceRoot": source_root,
        "pinsPath": pins_path,
        "pinsRaw": pins_raw,
        **pin_context,
        "artifacts": artifacts,
        "sourceSnapshotRevision": SOURCE_REVISION,
    }


def _validate_native_run(value: Any, context: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, RUN_RESULT_KEYS, "NATIVE_RUN_KEYS_INVALID")
    _need(value["schema"] == RUN_SCHEMA and value["source"] == "candidate" and
          value["suite"] == SUITE and value["cohortId"] is None and
          value["target"] == TARGET and value["command"] == _expected_command(),
          "NATIVE_RUN_IDENTITY_INVALID")
    _need(type(value["layoutVersion"]) is int and value["layoutVersion"] == 26 and
          value["dependencyEpoch"] == "tsj-generation-dependencies-v28" and
          type(value["sourceSnapshotRevision"]) is int and
          value["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(value["inputCount"]) is int and
          value["inputCount"] == context["inputCount"] == SOURCE_INPUT_COUNT and
          type(value["inputBytes"]) is int and
          value["inputBytes"] == context["inputBytes"] and
          value["inputPinsSha256"] == context["pinsSha256"] and
          value["inputAudit"] == "PASS_BEFORE_AND_AFTER",
          "NATIVE_RUN_SOURCE_BINDING_INVALID")
    _need(type(value["exitCode"]) is int and value["exitCode"] == 0 and
          type(value["nativeExitCode"]) is int and value["nativeExitCode"] == 0 and
          value["terminationProven"] is True and
          value["cleanupStatus"] == "PASS" and
          value["cleanupElapsedMs"] is None and
          _integer(value["viaCost"], 60, 60) and
          _integer(value["nativeLimitMs"], MAX_NATIVE_LIMIT_MS, MAX_NATIVE_LIMIT_MS) and
          _integer(value["outerLimitMs"], MAX_OUTER_LIMIT_MS, MAX_OUTER_LIMIT_MS) and
          value["requestedExecutable"] == "powershell.exe",
          "NATIVE_RUN_EXIT_CLEANUP_OR_BUDGET_INVALID")
    _need(_integer(value["processId"], 1, MAX_JAVA_LONG) and
          type(value["processStartTicks"]) is str and
          TICKS_RE.fullmatch(value["processStartTicks"]) is not None and
          type(value["elapsedSeconds"]) in (int, float) and
          math.isfinite(value["elapsedSeconds"]) and
          0 < value["elapsedSeconds"] <= 1_800,
          "NATIVE_RUN_PROCESS_INVALID")
    for label, raw in (("stdout", context["stdoutRaw"]),
                       ("stderr", context["stderrRaw"]),
                       ("receipt", context["receiptRaw"])):
        _need(_integer(value[label + "Bytes"], 0, MAX_CAPTURE_BYTES) and
              value[label + "Bytes"] == len(raw) and
              type(value[label + "Sha256"]) is str and
              SHA256_RE.fullmatch(value[label + "Sha256"]) is not None and
              value[label + "Sha256"] == _sha256(raw),
              "NATIVE_RUN_" + label.upper() + "_BINDING_INVALID")
    _need(context["stderrRaw"] == b"", "RUN_STDERR_NOT_EMPTY")
    return dict(value)


def _line_body(line: str, marker: str, code: str,
               maximum: int = MAX_LINE_BYTES) -> bytes:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix), code + "_MARKER_INVALID")
    try:
        body = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail(code + "_UTF8_INVALID")
    _need(0 < len(body) <= maximum, code + "_SIZE_INVALID")
    return body


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, helper: types.ModuleType
                                  ) -> Tuple[List[str], List[str], Dict[str, Any], bytes,
                                             Dict[str, Any], bytes]:
    console = _base_call(helper, "_decode_wrapper_lines", "RUN_STDOUT_INVALID",
                         stdout_raw, "RUN_STDOUT")
    receipt = _base_call(helper, "_decode_lines", "RUN_RECEIPT_INVALID",
                         receipt_raw, "RUN_RECEIPT")
    _need(stderr_raw == b"", "RUN_STDERR_NOT_EMPTY")
    _need(receipt_raw.endswith(b"\n") and len(receipt) == 3 and
          receipt[0].startswith(BASE_EVENT_MARKER + " ") and
          receipt[1].startswith(GUARD_EVENT_MARKER + " ") and
          SUMMARY_RE.fullmatch(receipt[2]) is not None,
          "RECEIPT_LINE_ORDER_OR_COUNT_INVALID")
    base_payload = _line_body(receipt[0], BASE_EVENT_MARKER,
                              "BASE_EVENT", MAX_LINE_BYTES)
    guard_payload = _line_body(receipt[1], GUARD_EVENT_MARKER,
                               "GUARD_EVENT", MAX_GUARD_PAYLOAD_BYTES)
    summary = receipt[2]
    event_lines = [line for line in receipt
                   if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    console_events = [line for line in console
                      if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    _need(event_lines == receipt and console_events == event_lines,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    for line in console:
        left_trimmed = line.lstrip()
        if left_trimmed.startswith(("RB100_", "P07_", "PASS: RB100")):
            _need(line == left_trimmed, "RUN_CONSOLE_EVENT_EDGE_WHITESPACE")
        _need(WRAPPER_FAILURE_RE.search(line.strip()) is None,
              "RUN_CONSOLE_FAILURE_LINE")
    base_index = console.index(receipt[0])
    _need(console.count(receipt[0]) == 1 and console.count(receipt[1]) == 1 and
          console.count(summary) == 1 and
          console.index(receipt[1]) == base_index + 1 and
          console.index(summary) == base_index + 2 and
          console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(summary) < console.index(FOCUSED_PASS) <
          console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    match = SUMMARY_RE.fullmatch(summary)
    _need(match is not None and
          _integer(int(match.group(1)), 1, MAX_SCALAR),
          "RECEIPT_SUMMARY_ASSERTIONS_INVALID")
    base_event = _base_call(helper, "_parse_tagged", "BASE_EVENT_INVALID",
                            receipt[0], BASE_EVENT_MARKER, "BASE_EVENT")
    guard_event = _base_call(helper, "_parse_tagged", "GUARD_EVENT_INVALID",
                             receipt[1], GUARD_EVENT_MARKER, "GUARD_EVENT")
    return console, receipt, base_event, base_payload, guard_event, guard_payload


def _map_pad_cell(x: int, y: int, grid: Mapping[str, int], code: str) -> int:
    dx = x - grid["originX"]
    dy = y - grid["originY"]
    _need(dx >= 0 and dy >= 0 and dx % GRID == 0 and dy % GRID == 0,
          code + "_OFF_GRID")
    column = dx // GRID
    row = dy // GRID
    _need(column < grid["width"] and row < grid["height"],
          code + "_OUT_OF_GRID")
    return row * grid["width"] + column


def _guard_predicates(direct: int, length: int, bends: int) -> Tuple[bool, bool, bool]:
    return direct == 0, length > 3 * direct, bends > 16


def _validate_grid(value: Any) -> Dict[str, int]:
    _exact_keys(value, GRID_KEYS, "GUARD_GRID_KEYS_INVALID")
    _need(_integer(value["width"], 1, MAX_GRID_CELLS) and
          _integer(value["height"], 1, MAX_GRID_CELLS) and
          _integer(value["cells"], 1, MAX_GRID_CELLS) and
          value["cells"] == value["width"] * value["height"] and
          value["pitch"] == GRID and type(value["pitch"]) is int and
          _integer(value["originX"], MIN_SCALAR, MAX_SCALAR) and
          _integer(value["originY"], MIN_SCALAR, MAX_SCALAR),
          "GUARD_GRID_VALUES_INVALID")
    return dict(value)


def _validate_pad_endpoint(value: Any, grid: Mapping[str, int], code: str
                           ) -> Dict[str, Any]:
    _exact_keys(value, PAD_ENDPOINT_KEYS, code + "_KEYS_INVALID")
    _bounded_text(value["padId"], 256, code + "_PAD_ID_INVALID")
    _identity(value["identity"], code + "_IDENTITY_INVALID")
    _need(_integer(value["x"], MIN_SCALAR, MAX_SCALAR) and
          _integer(value["y"], MIN_SCALAR, MAX_SCALAR) and
          _integer(value["cell"], 0, grid["cells"] - 1), code + "_COORDINATE_INVALID")
    _need(value["cell"] == _map_pad_cell(value["x"], value["y"], grid, code),
          code + "_CELL_MAPPING_INVALID")
    return dict(value)


def _validate_endpoints(value: Any, grid: Mapping[str, int], target: Mapping[str, Any]
                        ) -> Dict[str, Any]:
    _exact_keys(value, ENDPOINTS_KEYS, "GUARD_ENDPOINTS_KEYS_INVALID")
    start = _validate_pad_endpoint(value["start"], grid, "GUARD_START")
    root = _validate_pad_endpoint(value["root"], grid, "GUARD_ROOT")
    end = value["end"]
    _exact_keys(end, NODE_ENDPOINT_KEYS, "GUARD_END_KEYS_INVALID")
    _identity(end["identity"], "GUARD_END_IDENTITY_INVALID")
    _need(_integer(end["x"], MIN_SCALAR, MAX_SCALAR) and
          _integer(end["y"], MIN_SCALAR, MAX_SCALAR) and
          _integer(end["cell"], 0, grid["cells"] - 1),
          "GUARD_END_COORDINATE_INVALID")
    expected_x = grid["originX"] + (end["cell"] % grid["width"]) * GRID
    expected_y = grid["originY"] + (end["cell"] // grid["width"]) * GRID
    _need(end["x"] == expected_x and end["y"] == expected_y and
          end["identity"] == target["foundNodeIdentity"],
          "GUARD_END_GRID_BINDING_INVALID")
    found = value["foundTerminal"]
    if found is not None:
        found = _validate_pad_endpoint(found, grid, "GUARD_FOUND_TERMINAL")
    _need((None if found is None else found["identity"]) ==
          target["foundTerminalIdentity"],
          "GUARD_FOUND_TERMINAL_IDENTITY_MISMATCH")
    return {"start": start, "root": root, "end": dict(end),
            "foundTerminal": found}


def _derive_path_metrics(nodes: Sequence[Sequence[int]], grid: Mapping[str, int]
                         ) -> Dict[str, Any]:
    length = 0
    bends = 0
    transitions = 0
    last_direction = 4
    prior_move: Optional[int] = None
    for index in range(1, len(nodes)):
        previous = nodes[index - 1]
        current = nodes[index]
        if previous[1] == current[1]:
            dx = current[2] - previous[2]
            dy = current[3] - previous[3]
            direction_by_delta = {
                (0, -GRID): 0,
                (GRID, 0): 1,
                (0, GRID): 2,
                (-GRID, 0): 3,
            }
            direction = direction_by_delta.get((dx, dy))
            _need(direction is not None and previous[0] != current[0] and
                  current[5] == previous[5],
                  "GUARD_PATH_PLANAR_EDGE_INVALID")
            _need(current[4] == direction, "GUARD_PATH_DIRECTION_INVALID")
            length += GRID
            if prior_move is not None and direction != prior_move:
                bends += 1
            prior_move = direction
            last_direction = direction
        else:
            _need(previous[0] == current[0] and
                  previous[2] == current[2] and previous[3] == current[3] and
                  current[5] == previous[5] + 1 and current[4] == 4,
                  "GUARD_PATH_VIA_EDGE_INVALID")
            transitions += 1
            _need(transitions <= MAX_TRANSITIONS, "GUARD_PATH_TRANSITIONS_INVALID")
            prior_move = None
            last_direction = 4
    return {"length": length, "bends": bends, "transitions": transitions,
            "lastDirection": last_direction}


def _validate_path(value: Any, grid: Mapping[str, int],
                   endpoints: Mapping[str, Any]) -> Tuple[Dict[str, Any], Dict[str, Any], bytes]:
    _exact_keys(value, PATH_KEYS, "GUARD_PATH_KEYS_INVALID")
    _need(value["encoding"] == NODE_ENCODING and
          _integer(value["maximumNodes"], MAX_WITNESS_NODES, MAX_WITNESS_NODES),
          "GUARD_PATH_ENCODING_OR_BOUND_INVALID")
    nodes = value["nodes"]
    _need(type(nodes) is list and 1 <= len(nodes) <= MAX_WITNESS_NODES and
          _integer(value["nodeCount"], len(nodes), len(nodes)),
          "GUARD_PATH_NODE_COUNT_INVALID")
    _digest(value["rowsSha256"], "GUARD_PATH_ROWS_SHA_INVALID")
    for index, row in enumerate(nodes):
        _need(type(row) is list and len(row) == 11 and
              all(type(item) is int for item in row),
              "GUARD_PATH_ROW_SHAPE_INVALID")
        cell, layer, x, y, direction, transitions, cost, heuristic, serial, key, parent = row
        _need(_integer(cell, 0, grid["cells"] - 1) and
              layer in (0, 1) and
              _integer(x, MIN_SCALAR, MAX_SCALAR) and
              _integer(y, MIN_SCALAR, MAX_SCALAR) and
              direction in (0, 1, 2, 3, 4) and
              _integer(transitions, 0, MAX_TRANSITIONS) and
              _integer(cost, 0, MAX_SCALAR) and
              _integer(heuristic, 0, MAX_SCALAR) and
              _integer(serial, 0, MAX_SCALAR) and
              _integer(key, 0, MAX_SCALAR),
              "GUARD_PATH_ROW_VALUE_INVALID")
        expected_x = grid["originX"] + (cell % grid["width"]) * GRID
        expected_y = grid["originY"] + (cell // grid["width"]) * GRID
        expected_key = (((transitions * 2 + layer) * 5 + direction) *
                        grid["cells"]) + cell
        _need(x == expected_x and y == expected_y and key == expected_key,
              "GUARD_PATH_ROW_COORDINATE_OR_KEY_INVALID")
        if index == 0:
            _need(parent == -1 and cell == endpoints["start"]["cell"] and
                  direction == 4 and transitions == 0,
                  "GUARD_PATH_ROOT_INVALID")
        else:
            _need(parent == index - 1, "GUARD_PATH_PARENT_INVALID")
            prior = nodes[index - 1]
            _need(transitions >= prior[5], "GUARD_PATH_TRANSITION_REGRESSION")
    _need(nodes[-1][0] == endpoints["end"]["cell"],
          "GUARD_PATH_END_CELL_MISMATCH")
    rows_raw = _canonical_json(nodes)
    _need(_sha256(rows_raw) == value["rowsSha256"], "GUARD_PATH_ROWS_SHA_MISMATCH")
    metrics = _derive_path_metrics(nodes, grid)
    _need(nodes[-1][5] == metrics["transitions"],
          "GUARD_PATH_TRANSITION_COUNT_MISMATCH")
    return dict(value), metrics, rows_raw


def _base_projection(base: Mapping[str, Any]) -> Dict[str, Any]:
    source_raw = base["source"]
    copy_raw = base["copy"]
    source = {
        "routeSessionIdentity": source_raw["routeSessionIdentity"],
        "searchIdentity": source_raw["searchIdentity"],
        "branchIdentity": source_raw["branchIdentity"],
        "layoutIdentity": source_raw["layoutIdentity"],
        "digests": {key: source_raw["digests"][key]
                    for key in ("seeds", "netOrder", "prefixRows", "geometry")},
        "preserved": source_raw["preserved"],
    }
    copied = {
        "ownerIdentity": copy_raw["ownerIdentity"],
        "searchIdentity": copy_raw["searchIdentity"],
        "branchIdentity": copy_raw["branchIdentity"],
        "layoutIdentity": copy_raw["layoutIdentity"],
        "digests": {key: copy_raw["digests"][key] for key in (
            "seeds", "netOrder", "prefixRows", "initialGeometry",
            "postGeometry", "publication")},
        "requested": {key: copy_raw["requested"][key]
                      for key in ("maximumVias", "viaCost")},
        "continuationHandoff": {
            "identity": copy_raw["continuationHandoff"]["identity"],
            "ownerIdentity": copy_raw["continuationHandoff"]["ownerIdentity"],
        },
    }
    return {"source": source, "copy": copied}


def _validate_target(value: Any, base: Mapping[str, Any],
                     checked_base: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, TARGET_KEYS, "GUARD_TARGET_KEYS_INVALID")
    for key in ("ordering", "pass", "netVectorPosition", "searchNetIndex",
                "branchOrdinal", "searchBranches"):
        _need(_integer(value[key], 0, MAX_SCALAR), "GUARD_TARGET_INTEGER_INVALID")
    _need(_integer(value["ordering"], 1, MAX_ORDERINGS) and
          value["ordering"] == checked_base["remainder"]["orderings"] == 2 and
          value["pass"] == checked_base["selector"]["pass"] and
          value["netId"] == "A_DRIVE" and
          value["searchNetIndex"] == value["netVectorPosition"] + 1 and
          _integer(value["branchOrdinal"], 1, MAX_BRANCH_EXPANSIONS) and
          _integer(value["searchBranches"], 1, MAX_BRANCH_EXPANSIONS) and
          value["branchOrdinal"] == value["searchBranches"],
          "GUARD_TARGET_LINEAGE_INVALID")
    for key in ("netVectorIdentity", "ownerIdentity", "searchIdentity", "layoutIdentity",
                "branchIdentity", "publisherIdentity", "foundNodeIdentity"):
        _identity(value[key], "GUARD_TARGET_IDENTITY_INVALID")
    terminal_identity = value["foundTerminalIdentity"]
    if terminal_identity is not None:
        _identity(terminal_identity, "GUARD_TARGET_TERMINAL_IDENTITY_INVALID")
    copied = base["copy"]
    _need(value["ownerIdentity"] == copied["ownerIdentity"] and
          value["searchIdentity"] == copied["searchIdentity"] and
          value["layoutIdentity"] == copied["layoutIdentity"],
          "GUARD_TARGET_COPY_BINDING_INVALID")
    return dict(value)


def _validate_checkpoint(value: Any, target: Mapping[str, Any],
                         endpoints: Mapping[str, Any], metrics: Mapping[str, Any],
                         node_count: int) -> Dict[str, Any]:
    _exact_keys(value, CHECKPOINT_KEYS, "GUARD_CHECKPOINT_KEYS_INVALID")
    for key in ("publisherStage", "qualityIndex", "reversedSize", "storedLength",
                "storedBends", "storedLastDirection", "direct", "recomputedLength",
                "recomputedBends", "transitions"):
        _need(type(value[key]) is int, "GUARD_CHECKPOINT_INTEGER_INVALID")
    _need(value["publisherStage"] == 1 and
          value["qualityIndex"] == value["reversedSize"] == node_count and
          1 <= value["qualityIndex"] <= MAX_WITNESS_NODES and
          0 <= value["storedLength"] <= MAX_SCALAR and
          0 <= value["storedBends"] <= MAX_SCALAR and
          0 <= value["storedLastDirection"] <= 4 and
          0 <= value["direct"] <= MAX_SCALAR and
          0 <= value["recomputedLength"] <= MAX_SCALAR and
          0 <= value["recomputedBends"] <= MAX_SCALAR and
          0 <= value["transitions"] <= MAX_TRANSITIONS,
          "GUARD_CHECKPOINT_VALUES_INVALID")
    direct = (abs(endpoints["start"]["x"] - endpoints["end"]["x"]) +
              abs(endpoints["start"]["y"] - endpoints["end"]["y"]))
    _need(direct <= MAX_SCALAR and value["direct"] == direct and
          value["recomputedLength"] == metrics["length"] and
          value["recomputedBends"] == metrics["bends"] and
          value["storedLength"] == metrics["length"] and
          value["storedBends"] == metrics["bends"] and
          value["storedLastDirection"] == metrics["lastDirection"] and
          value["transitions"] == metrics["transitions"],
          "GUARD_CHECKPOINT_RECOMPUTATION_MISMATCH")
    direct_zero, length_over, bends_over = _guard_predicates(
        direct, metrics["length"], metrics["bends"])
    _need(type(value["directZero"]) is bool and
          type(value["lengthOverThreeDirect"]) is bool and
          type(value["bendsOver16"]) is bool and
          type(value["rejected"]) is bool and
          value["directZero"] is direct_zero and
          value["lengthOverThreeDirect"] is length_over and
          value["bendsOver16"] is bends_over and
          value["rejected"] is (direct_zero or length_over or bends_over) and
          value["rejected"] is True,
          "GUARD_CHECKPOINT_PREDICATE_MISMATCH")
    return dict(value)


def _validate_boundary(value: Any, checked_base: Mapping[str, Any],
                       base: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, BOUNDARY_KEYS, "GUARD_BOUNDARY_KEYS_INVALID")
    expected = {
        "status": "ORDERING_BOUNDARY",
        "type": "ORDERING_BOUNDARY",
        "reason": "A_DRIVE:BRANCH_QUALITY_LIMIT",
        "orderings": 2,
        "cumulativeExpansions": 774_550,
        "remainingRequestBudget": 225_450,
        "sameTypedBoundary": True,
        "rejectedBranchPublished": False,
    }
    _need(_same_json(value, expected), "GUARD_BOUNDARY_VALUES_INVALID")
    remainder = base["remainder"]
    _need(remainder["status"] == value["status"] and
          remainder["boundaryType"] == value["type"] and
          remainder["boundaryReason"] == value["reason"] and
          remainder["orderings"] == value["orderings"] and
          remainder["cumulativeExpansions"] == value["cumulativeExpansions"] and
          remainder["remainingRequestBudget"] == value["remainingRequestBudget"] and
          checked_base["outcome"] == "QUALITY_BOUNDARY",
          "GUARD_BOUNDARY_BASE_BINDING_INVALID")
    return dict(value)


def _validate_prefix(value: Any, base: Mapping[str, Any]) -> Dict[str, Any]:
    # The wire carries digests and counts, not serialized geometry rows. The
    # consumer checks digest shape, copy-layout lineage and before/after equality;
    # it does not claim an independent full-geometry or P08 recomputation.
    _exact_keys(value, PREFIX_KEYS, "GUARD_PREFIX_KEYS_INVALID")
    for name in ("before", "after"):
        row = value[name]
        _exact_keys(row, PREFIX_SNAPSHOT_KEYS, "GUARD_PREFIX_SNAPSHOT_KEYS_INVALID")
        _identity(row["layoutIdentity"], "GUARD_PREFIX_LAYOUT_IDENTITY_INVALID")
        _digest(row["geometrySha256"], "GUARD_PREFIX_GEOMETRY_SHA_INVALID")
        _digest(row["traceRowsSha256"], "GUARD_PREFIX_TRACE_SHA_INVALID")
        _digest(row["holeRowsSha256"], "GUARD_PREFIX_HOLE_SHA_INVALID")
        _need(_integer(row["traceCount"], 0, MAX_SCALAR) and
              _integer(row["holeCount"], 0, MAX_SCALAR),
              "GUARD_PREFIX_COUNT_INVALID")
        _need(row["layoutIdentity"] == base["copy"]["layoutIdentity"],
              "GUARD_PREFIX_COPY_LAYOUT_MISMATCH")
    _need(type(value["unchanged"]) is bool and value["unchanged"] is True and
          _same_json(value["before"], value["after"]),
          "GUARD_PREFIX_CHANGED")
    _need(base["source"]["preserved"] is True and
          _same_json(base["source"]["before"], base["source"]["after"]),
          "GUARD_BASE_SOURCE_NOT_PRESERVED")
    return {"before": dict(value["before"]), "after": dict(value["after"]),
            "unchanged": True}


def _validate_work(value: Any, checked_base: Mapping[str, Any],
                   node_count: int, path_bytes: int) -> Dict[str, Any]:
    _exact_keys(value, WORK_KEYS, "GUARD_WORK_KEYS_INVALID")
    _need(value["budgetPhase"] == "orderingRemainder" and
          _integer(value["controllerUnitLimit"], MAX_WORK_UNITS, MAX_WORK_UNITS) and
          _integer(value["ownerStepsPerUnitLimit"], MAX_OWNER_STEPS_PER_UNIT,
                   MAX_OWNER_STEPS_PER_UNIT) and
          _integer(value["controllerUnits"], 1, MAX_WORK_UNITS),
          "GUARD_WORK_LIMITS_INVALID")
    _exact_keys(value["subphases"], SUBPHASE_NAMES, "GUARD_WORK_SUBPHASE_KEYS_INVALID")
    total_units = 0
    total_subphase_ns = 0
    subphases: Dict[str, Dict[str, int]] = {}
    for name in SUBPHASE_NAMES:
        row = value["subphases"][name]
        _exact_keys(row, SUBPHASE_KEYS, "GUARD_WORK_SUBPHASE_KEYS_INVALID")
        _need(_integer(row["controllerUnits"], 1, MAX_WORK_UNITS) and
              _integer(row["elapsedNanos"], 0, MAX_OPERATION_NS),
              "GUARD_WORK_SUBPHASE_VALUES_INVALID")
        total_units += row["controllerUnits"]
        total_subphase_ns += row["elapsedNanos"]
        subphases[name] = dict(row)
    phase = checked_base["work"]["phases"][6]
    base_work = checked_base["work"]
    _need(value["controllerUnits"] == total_units == phase["calls"] and
          phase["name"] == "orderingRemainder" and
          value["continuationOwnerSteps"] == phase["ownerSteps"] and
          _integer(value["maximumGroupOwnerSteps"], 0, MAX_OWNER_STEPS_PER_UNIT) and
          value["maximumGroupOwnerSteps"] <= base_work["maxOwnerStepsInUnit"],
          "GUARD_WORK_BASE_BINDING_INVALID")
    _need(_integer(value["continuationAdvanceApiCalls"], 1, MAX_JAVA_LONG) and
          _integer(value["continuationOwnerSteps"], 0,
                   MAX_WORK_UNITS * MAX_OWNER_STEPS_PER_UNIT) and
          value["continuationOwnerSteps"] <=
          (subphases["advance"]["controllerUnits"] +
           subphases["boundaryCheck"]["controllerUnits"]) * MAX_OWNER_STEPS_PER_UNIT,
          "GUARD_WORK_CONTINUATION_INVALID")
    for key in ("operationElapsedNanos", "phaseElapsedNanos", "overheadElapsedNanos"):
        _need(type(value[key]) is int, "GUARD_WORK_TIME_INVALID")
    _need(0 < value["operationElapsedNanos"] <= MAX_OPERATION_NS and
          0 <= value["phaseElapsedNanos"] <= value["operationElapsedNanos"] and
          0 <= value["overheadElapsedNanos"] <= MAX_OPERATION_NS and
          value["phaseElapsedNanos"] == total_subphase_ns + value["overheadElapsedNanos"] and
          value["operationElapsedNanos"] // 1_000_000 ==
          base_work["operationElapsedMs"] and
          value["phaseElapsedNanos"] // 1_000_000 == phase["elapsedMs"],
          "GUARD_WORK_TIME_BINDING_INVALID")
    _need(_integer(value["capturedNodeRows"], node_count, node_count) and
          _integer(value["verifiedNodeRows"], node_count, node_count) and
          _integer(value["pathRowsUtf8Bytes"], path_bytes, path_bytes),
          "GUARD_WORK_CAPTURE_BINDING_INVALID")
    _need(value["continuationOwnerSteps"] == phase["ownerSteps"] and
          value["maximumGroupOwnerSteps"] <= MAX_OWNER_STEPS_PER_UNIT and
          phase["calls"] <= MAX_WORK_UNITS,
          "GUARD_WORK_LIMIT_EXCEEDED")
    return dict(value)


def _validate_witness(value: Any, base: Any, base_payload_raw: bytes,
                      helper: Optional[types.ModuleType] = None) -> Dict[str, Any]:
    """Validate the wire witness against raw base bytes and the frozen f11 API."""
    _exact_keys(value, GUARD_KEYS, "GUARD_EVENT_KEYS_INVALID")
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 1 and
          value["schema"] == EVENT_SCHEMA and value["status"] == "PASS" and
          type(value["runId"]) is str and RUN_ID_RE.fullmatch(value["runId"]) is not None and
          value["operationId"] == "QUALITY_PROBE" and
          value["scope"] == "ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD",
          "GUARD_EVENT_IDENTITY_INVALID")
    if helper is None:
        helper, _helper_raw, _helper_path = _load_base_reader()
    _exact_keys(value["base"], BASE_BINDING_KEYS, "GUARD_BASE_KEYS_INVALID")
    _digest(value["base"]["eventSha256"], "GUARD_BASE_SHA_INVALID")
    _need(type(base_payload_raw) is bytes and 0 < len(base_payload_raw) <= MAX_LINE_BYTES and
          _sha256(base_payload_raw) == value["base"]["eventSha256"],
          "GUARD_BASE_PAYLOAD_SHA_MISMATCH")
    parsed_base = _strict_json(base_payload_raw, "BASE_EVENT", helper)
    _need(_same_json(parsed_base, base), "GUARD_BASE_PAYLOAD_EVENT_MISMATCH")
    checked_base = _base_call(helper, "_validate_event", "GUARD_BASE_EVENT_INVALID", base)
    _need(type(base) is dict and base["schema"] == "rb100-load12-cost240-remainder-r1" and
          base["runId"] == value["runId"] and
          value["base"]["runId"] == base["runId"] and
          RUN_ID_RE.fullmatch(base["runId"]) is not None,
          "GUARD_BASE_RUN_BINDING_INVALID")
    _exact_keys(value["inputs"], INPUTS_KEYS, "GUARD_INPUTS_KEYS_INVALID")
    _need(_same_json(value["inputs"], EXPECTED_INPUTS), "GUARD_INPUTS_INVALID")
    _exact_keys(value["lineage"], LINEAGE_KEYS, "GUARD_LINEAGE_KEYS_INVALID")
    expected_lineage = _base_projection(base)
    _need(_same_json(value["lineage"], expected_lineage),
          "GUARD_LINEAGE_BASE_BINDING_INVALID")
    source = base["source"]
    copied = base["copy"]
    _need(checked_base["sourcePreserved"] is True and
          base["source"]["preserved"] is True and
          source["before"] == source["after"] and
          checked_base["selector"]["rootSeed"] == EXPECTED_INPUTS["rootSeed"] and
          checked_base["selector"]["placementSeed"] == EXPECTED_INPUTS["placementSeed"] and
          checked_base["selector"]["routingSeed"] == EXPECTED_INPUTS["routingSeed"] and
          checked_base["selector"]["routeRequestIndex"] == 0 and
          checked_base["selector"]["selectedPlacementIndex"] == 0 and
          checked_base["selector"]["selectedPlacementAttempt"] == 4 and
          source["before"]["pass"] == 1 and
          copied["requested"]["maximumVias"] == 48 and
          copied["requested"]["viaCost"] == 240,
          "GUARD_BASE_INPUT_LINEAGE_INVALID")
    target = _validate_target(value["target"], base, checked_base)
    grid = _validate_grid(value["grid"])
    _need(_same_json(grid, checked_base["selector"]["grid"]),
          "GUARD_GRID_BASE_BINDING_INVALID")
    endpoints = _validate_endpoints(value["endpoints"], grid, target)
    path, metrics, path_rows = _validate_path(value["path"], grid, endpoints)
    checkpoint = _validate_checkpoint(value["checkpoint"], target, endpoints,
                                      metrics, path["nodeCount"])
    boundary = _validate_boundary(value["boundary"], checked_base, base)
    prefix = _validate_prefix(value["prefix"], base)
    work = _validate_work(value["work"], checked_base, path["nodeCount"], len(path_rows))
    _exact_keys(value["cleanup"], CLEANUP_KEYS, "GUARD_CLEANUP_KEYS_INVALID")
    cleanup = value["cleanup"]
    _need(cleanup["status"] == "PASS" and
          _integer(cleanup["elapsedMs"], 0, MAX_CLEANUP_MS) and
          _integer(cleanup["limitMs"], MAX_CLEANUP_MS, MAX_CLEANUP_MS) and
          cleanup["status"] == base["cleanup"]["status"] and
          cleanup["elapsedMs"] == base["cleanup"]["elapsedMs"],
          "GUARD_CLEANUP_BASE_BINDING_INVALID")
    _exact_keys(value["qualification"], QUALIFICATION_KEYS,
                "GUARD_QUALIFICATION_KEYS_INVALID")
    _need(all(type(item) is bool and item is False
              for item in value["qualification"].values()),
          "GUARD_QUALIFICATION_CLAIM_INVALID")
    return {
        "schema": EVENT_SCHEMA,
        "runId": value["runId"],
        "base": dict(value["base"]),
        "target": target,
        "grid": grid,
        "endpoints": endpoints,
        "path": path,
        "checkpoint": checkpoint,
        "boundary": boundary,
        "prefix": prefix,
        "work": work,
        "cleanup": dict(cleanup),
        "qualification": dict(value["qualification"]),
        "lineage": expected_lineage,
    }


def _validate_native_artifacts(spec_raw: bytes, spec_path: Path,
                               spec_sha256: str, spec: Mapping[str, Any],
                               context: Mapping[str, Any], own_raw: bytes,
                               helper: types.ModuleType, helper_raw: bytes,
                               helper_path: Path) -> Dict[str, Any]:
    artifacts = context["artifacts"]
    raw_by_name = {
        "result": _read_bounded(artifacts["resultPath"], MAX_RESULT_BYTES,
                                "RUN_RESULT", helper=helper),
        "stdout": _read_bounded(artifacts["stdoutPath"], MAX_CAPTURE_BYTES,
                                "RUN_STDOUT", helper=helper),
        "receipt": _read_bounded(artifacts["receiptPath"], MAX_CAPTURE_BYTES,
                                 "RUN_RECEIPT", helper=helper),
        "stderr": _read_bounded(artifacts["stderrPath"], MAX_CAPTURE_BYTES,
                                "RUN_STDERR", allow_empty=True, helper=helper),
    }
    run_value = _strict_json(raw_by_name["result"], "NATIVE_RUN", helper)
    run_context = dict(context)
    run_context.update({
        "stdoutRaw": raw_by_name["stdout"],
        "receiptRaw": raw_by_name["receipt"],
        "stderrRaw": raw_by_name["stderr"],
    })
    native_run = _validate_native_run(run_value, run_context)
    _console, _receipt_lines, base_event, base_payload, guard_event, guard_payload = \
        _validate_console_and_receipt(raw_by_name["stdout"], raw_by_name["receipt"],
                                      raw_by_name["stderr"], helper)
    checked = _validate_witness(guard_event, base_event, base_payload, helper)
    _need(0 < len(guard_payload) <= MAX_GUARD_PAYLOAD_BYTES,
          "GUARD_EVENT_PAYLOAD_SIZE_INVALID")
    _stable_source_check(spec_raw, spec_path, spec_sha256, spec, context,
                         raw_by_name, own_raw, helper_raw, helper_path, helper)
    return {
        "schema": RESULT_SCHEMA,
        "status": "VALIDATED_BOUNDED_ADRIVE_GUARD_WITNESS",
        "target": TARGET,
        "purpose": PURPOSE,
        "source": {
            "snapshotRevision": context["sourceSnapshotRevision"],
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
        },
        "nativeRun": {
            "schema": native_run["schema"],
            "suite": native_run["suite"],
            "command": native_run["command"],
            "layoutVersion": native_run["layoutVersion"],
            "dependencyEpoch": native_run["dependencyEpoch"],
            "inputAudit": native_run["inputAudit"],
            "nativeLimitMs": native_run["nativeLimitMs"],
            "outerLimitMs": native_run["outerLimitMs"],
            "processId": native_run["processId"],
            "processStartTicks": native_run["processStartTicks"],
            "elapsedSeconds": native_run["elapsedSeconds"],
            "exitCode": native_run["exitCode"],
            "nativeExitCode": native_run["nativeExitCode"],
            "terminationProven": native_run["terminationProven"],
            "cleanupStatus": native_run["cleanupStatus"],
            "resultSha256": _sha256(raw_by_name["result"]),
            "stdoutBytes": len(raw_by_name["stdout"]),
            "stdoutSha256": _sha256(raw_by_name["stdout"]),
            "receiptBytes": len(raw_by_name["receipt"]),
            "receiptSha256": _sha256(raw_by_name["receipt"]),
            "stderrBytes": len(raw_by_name["stderr"]),
            "stderrSha256": _sha256(raw_by_name["stderr"]),
        },
        "experiment": checked,
        "limitations": {
            "routeAccepted": False,
            "geometryPublished": False,
            "routeAdoption": False,
            "layoutQualification": False,
            "playable100": False,
        },
        "readerSourceSha256": _sha256(own_raw),
        "baseReaderSha256": BASE_READER_SHA256,
    }


def _stable_source_check(spec_raw: bytes, spec_path: Path,
                         spec_sha256: str, spec: Mapping[str, Any],
                         context: Mapping[str, Any], raw_by_name: Mapping[str, bytes],
                         own_raw: bytes, helper_raw: bytes, helper_path: Path,
                         helper: types.ModuleType) -> None:
    spec_again = _read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC_RECHECK",
                               helper=helper)
    _need(spec_again == spec_raw and _sha256(spec_again) == spec_sha256,
          "INPUT_SPEC_CHANGED_DURING_VALIDATION")
    source_root = _resolve_path(spec["sourceRoot"], spec_path.parent,
                                "SOURCE_ROOT_RECHECK", helper)
    pins_path = _resolve_path(spec["pinsPath"], spec_path.parent,
                              "SOURCE_PINS_RECHECK", helper)
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS_RECHECK",
                             helper=helper)
    _need(source_root == context["sourceRoot"] and pins_path == context["pinsPath"] and
          pins_raw == context["pinsRaw"], "SOURCE_INPUT_MANIFEST_CHANGED")
    second = _audit_inputs_manifest(pins_raw, spec, source_root, helper)
    _need(second["pinMap"] == context["pinMap"] and
          second["inputCount"] == context["inputCount"] and
          second["inputBytes"] == context["inputBytes"] and
          second["pinsSha256"] == context["pinsSha256"],
          "SOURCE_INPUTS_CHANGED_DURING_VALIDATION")
    for name, raw in raw_by_name.items():
        path = context["artifacts"]["resultPath" if name == "result" else name + "Path"]
        again = _read_bounded(path,
                              MAX_RESULT_BYTES if name == "result" else MAX_CAPTURE_BYTES,
                              "RUN_" + name.upper() + "_RECHECK",
                              allow_empty=(name == "stderr"), helper=helper)
        _need(again == raw, "RUN_" + name.upper() + "_CHANGED_DURING_VALIDATION")
    own_path = Path(os.path.abspath(__file__))
    own_after = _bootstrap_read(own_path, MAX_RESULT_BYTES, "READER_SOURCE_RECHECK")
    helper_after = _bootstrap_read(helper_path, MAX_RESULT_BYTES, "BASE_READER_RECHECK")
    _need(own_after == own_raw, "READER_SOURCE_CHANGED_DURING_VALIDATION")
    _need(helper_after == helper_raw and
          _sha256(helper_after) == BASE_READER_SHA256,
          "BASE_READER_CHANGED_DURING_VALIDATION")


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    helper, helper_raw, helper_path = _load_base_reader()
    own_path = Path(os.path.abspath(__file__))
    own_raw = _bootstrap_read(own_path, MAX_RESULT_BYTES, "READER_SOURCE")
    spec_path = _resolve_path(spec_argument, Path.cwd(), "INPUT_SPEC", helper)
    spec_raw = _read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC", helper=helper)
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _strict_json(spec_raw, "INPUT_SPEC", helper)
    spec, context = _validate_input_spec(spec, spec_path.parent, helper)
    return _validate_native_artifacts(
        spec_raw, spec_path, spec_sha256, spec, context, own_raw,
        helper, helper_raw, helper_path)


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    _need(type(argv) in (list, tuple) and len(argv) == 4 and
          argv[0] == "--input-spec" and argv[2] == "--input-spec-sha256",
          "CLI_ARGUMENTS_INVALID")
    spec_path, spec_sha256 = argv[1], argv[3]
    _need(type(spec_path) is str and 0 < len(spec_path) <= MAX_PATH_CHARS and
          type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    return spec_path, spec_sha256


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        spec_path, spec_sha256 = _parse_cli(
            list(sys.argv[1:] if argv is None else argv))
        result = run_validation(spec_path, spec_sha256)
        sys.stdout.buffer.write(_canonical_json(result) + b"\n")
        sys.stdout.buffer.flush()
        return 0
    except ADriveGuardInputError as error:
        code = error.code if ERROR_CODE_RE.fullmatch(error.code) else "VALIDATION_FAILED"
        sys.stdout.write("FAIL: " + code + "\n")
        sys.stdout.flush()
        return 2
    except Exception as error:
        code = getattr(error, "code", None)
        code = code if type(code) is str and ERROR_CODE_RE.fullmatch(code) else "VALIDATION_FAILED"
        sys.stdout.write("FAIL: " + code + "\n")
        sys.stdout.flush()
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
