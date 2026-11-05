#!/usr/bin/env python3
"""Strict independent reader for the bounded actual LOAD12 A_DRIVE graph capture.

This validates the captured, history-relaxed graph and its provenance against
the frozen LOAD12 base and guard records. It does not search the graph, qualify
a route, or attest a physical/electrical board.
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


INPUT_SCHEMA = "p07-load12-cost240-adrive-guard-graph-input-v2"
RESULT_SCHEMA = "p07-load12-cost240-adrive-graph-reader-result-v2"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
EVENT_SCHEMA = "rb100-load12-adrive-guard-graph-r2"
TARGET = "LOAD12"
PURPOSE = "ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD_GRAPH_CAPTURE"
SCOPE = "ACTUAL_LOAD12_COST240_COPIED_ORDER_ADRIVE_PRE_GUARD_GRAPH_CAPTURE"
SUITE = "Rb100Load12DetachedBaselineContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
BASE_EVENT_MARKER = "RB100_LOAD12_COST240_REMAINDER_EVENT"
GUARD_EVENT_MARKER = "RB100_LOAD12_ADRIVE_GUARD_EVENT"
GRAPH_EVENT_MARKER = "RB100_LOAD12_ADRIVE_GRAPH_EVENT"
SUMMARY_PREFIX = "PASS: RB100 LOAD12 detached cost240 guard graph contracts assertions="
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
MAX_GRAPH_PAYLOAD_BYTES = 8 * 1024 * 1024
MAX_GUARD_PAYLOAD_BYTES = 1024 * 1024
MAX_PATH_CHARS = 4096
MAX_NATIVE_LIMIT_MS = 480_000
MAX_OUTER_LIMIT_MS = 1_800_000
MAX_CLEANUP_MS = 120_000
MAX_OPERATION_MS = 90_000
MAX_WORK_UNITS = 640
MAX_UNIT_MS = 5_000
MAX_UNIT_NS = MAX_UNIT_MS * 1_000_000
MAX_OWNER_STEPS_PER_UNIT = 32_768
MAX_TRANSITIONS = 4
MAX_HOLES = 390
MAX_PADS = 246
MAX_SOURCE_ROWS = 16_384
MAX_GRID_CELLS = 250_000
MAX_GRAPH_BYTES = 8_388_608
MAX_WITNESS_NODES = 4_096
MAX_SCALAR = 2_147_483_647
MIN_SCALAR = -(1 << 31)
MAX_ABS_BOARD_COORDINATE = 100_000_000
MAX_JAVA_LONG = (1 << 63) - 1
MAX_OPERATION_NS = MAX_OPERATION_MS * 1_000_000
MAX_INTERNAL_UNITS = 2_560
MAX_SCAN_PROBES = 4_096
MAX_COPY_QUANTUM = 32_768
MAX_OBSERVER_CALLS = 4
MAX_COPY_OBSERVER_ENTRIES = MAX_COPY_QUANTUM * MAX_OBSERVER_CALLS
MAX_SCAN_OBSERVER_ENTRIES = MAX_SCAN_PROBES * MAX_OBSERVER_CALLS
MAX_METADATA_CHARS_PER_GROUP = 32_768

BASE_READER_SHA256 = (
    "a22690e299f14275806007894054e9087d8c4fc46b89c08507dbaa1687308e13"
)
GUARD_READER_SHA256 = (
    "2c2b1a7bf0ee8b0bb6330490dbc55e997f58c903302223c3b1a6bd95c89ea19d"
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
EVENT_KEYS = frozenset((
    "protocolVersion schema status runId operationId scope bindings inputs lineage target grid "
    "endpoints capture prefix graph graphIntegrity policy sourceCensus work cleanup claims"
).split())
BINDING_KEYS = frozenset("base guard".split())
BINDING_ROW_KEYS = frozenset("runId eventSha256".split())
PREFIX_KEYS = frozenset("before after unchanged".split())
PREFIX_SNAPSHOT_KEYS = frozenset((
    "layoutIdentity geometrySha256 traceCount traceRowsSha256 holeCount holeRowsSha256"
).split())
GRAPH_KEYS = frozenset((
    "protocol model caseId width height primary steps vias goals starts"
).split())
GRAPH_INTEGRITY_KEYS = frozenset("encoding canonicalBytes sha256".split())
CAPTURE_KEYS = frozenset((
    "status taskClass captureTaskIdentity snapshotIdentity contextIdentity ownerIdentity "
    "searchIdentity branchIdentity layoutIdentity routerIdentities branchOrdinal pass netId "
    "sourceGoalFaceCount sourceGoalBitsSha256 branchAtPublisherStage sourcePaused "
    "routerStateUnchanged routerEffectsUnchanged prefixGeometryUnchanged goalArrayParity "
    "sourceAttributionComplete liveReferencesReleased"
).split())
POLICY_KEYS = frozenset((
    "layerMap directionBits viaMaskSemantics viaMaskPredicate primaryStepCost "
    "secondaryStepCost secondaryStartCost turnCost copiedViaCost maximumHoleCount "
    "actualAllHoleCount actualViaKindCount maximumTransitions pathTransitionPredicate "
    "viaMaskExcludesRemainingHoleCapacity viaMaskExcludesEarlierPathViaSpacing "
    "searchPerformed routeFeasibilityClaim viaSpacing"
).split())
VIA_SPACING_KEYS = frozenset((
    "axisThreshold comparison combineAxes compareAgainst equalityAt20Permitted"
).split())
SOURCE_CENSUS_KEYS = frozenset((
    "padRows traceRows holeRows reachedPthPadIds holeKindCounts goalProvenance rowCounts "
    "sourceRowsCanonicalBytes sourceRowsSha256"
).split())
PAD_KEYS = frozenset((
    "padId netId x y escapeDx escapeDy escapeLength attachment mountingSide exposure "
    "padBounds probeBounds copperLayerMask gridCell"
).split())
RECT_KEYS = frozenset("x y width height".split())
TRACE_KEYS = frozenset((
    "sourceId netId startPadId endPadId layer exposure width xPoints yPoints routerGridCells"
).split())
HOLE_KEYS = frozenset((
    "id kind netId x y drillRadius landRadius exposure conductiveLayerMask gridCell"
).split())
KIND_COUNT_KEYS = frozenset("kind count".split())
GOAL_PROVENANCE_KEYS = frozenset("state cell layer sourceRefs".split())
SOURCE_REF_KEYS = frozenset("sourceType sourceId role".split())
ROW_COUNT_KEYS = frozenset((
    "padRows traceRows holeRows sourceRows goalStates allHoleCount viaKindCount"
).split())
WORK_KEYS = frozenset((
    "budgetPhase graphCaptureGroups graphTaskGroups graphMetadataGroups captureInternalUnits "
    "copyEntries comparisonEntries goalProbes ordinaryEdgeProbes viaProbes "
    "captureActiveElapsedMillis continuationOwnerSteps graphCanonicalBytes "
    "sourceRowsCanonicalBytes groupLedger preReport reportPreparation final"
).split())
GROUP_KEYS = frozenset((
    "kind internalCalls copyEntries comparisonEntries goalProbes ordinaryEdgeProbes viaProbes "
    "conservativeWorkUpperBound elapsedNanos"
).split())
PRE_REPORT_KEYS = frozenset("controllerUnits maximumUnitElapsedMs operationElapsedMs".split())
REPORT_PREPARATION_KEYS = frozenset("unitName controllerUnits elapsedNanos ownerSteps".split())
FINAL_KEYS = frozenset((
    "controllerUnits maximumUnitElapsedNanos operationElapsedNanos continuationOwnerSteps"
).split())
CLEANUP_KEYS = frozenset("status elapsedMs limitMs".split())
CLAIM_KEYS = frozenset((
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
EXPECTED_TASK_CLASS = (
    "com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype$Search$BranchSearch$"
    "FixedPrefixCaptureTask"
)
EXPECTED_DIRECTION_BITS = "NORTH=1,EAST=2,SOUTH=4,WEST=8"
EXPECTED_UNIT_NAME = "p07-final-report-preparation-adrive-graph-envelope"
GRAPH_PROTOCOL = "p07-fixed-prefix-packed-graph-v1"
GRAPH_MODEL = "DIRECTED_TWO_FACE_HISTORY_RELAXED"
GRAPH_CASE_ID = "Q100_LOAD12_COST240_ADRIVE_PRE_GUARD"
GRAPH_ENCODING = "CANONICAL_COMPACT_ASCII_SORTED_KEYS_PACKED_MASKS"
OBSERVED_PATH_SHA256 = "a2d33696158882cf3d6bd4c949c021c4456a00b325b80fe3815b09df2db15c10"

SOURCE_ROLES = {
    "PAD": frozenset(("ROOT_PAD", "REACHED_PTH_PAD")),
    "TRACE": frozenset(("SAME_NET_TREE_TRACE",)),
    "HOLE": frozenset(("SAME_NET_VIA_BARREL", "SAME_NET_PTH_BARREL")),
}


class ADriveGraphInputError(ValueError):
    """Sanitized failure; error codes never include paths or artifact contents."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise ADriveGraphInputError(code)


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


def _utf16_units(value: Any, code: str, maximum: Optional[int] = None) -> int:
    _need(type(value) is str, code)
    try:
        raw = value.encode("utf-16-le", errors="strict")
    except UnicodeEncodeError:
        _fail(code)
    units = len(raw) // 2
    if maximum is not None:
        _need(units <= maximum, code)
    return units


def _identifier(value: Any, code: str, maximum: int = 4_096) -> str:
    units = _utf16_units(value, code, maximum)
    _need(units > 0 and all(ord(char) >= 0x20 and ord(char) != 0x7f for char in value), code)
    return value


def _check_all_strings(value: Any) -> None:
    pending = [(value, 0)]
    count = 0
    while pending:
        item, depth = pending.pop()
        count += 1
        _need(count <= 2_000_000 and depth <= 64, "JSON_COMPLEXITY_LIMIT")
        if type(item) is str:
            _utf16_units(item, "JSON_UNPAIRED_UTF16")
        elif type(item) is list:
            pending.extend((child, depth + 1) for child in item)
        elif type(item) is dict:
            for key, child in item.items():
                _utf16_units(key, "JSON_KEY_UNPAIRED_UTF16")
                pending.append((child, depth + 1))


def _bounded_bytes(raw: Any, maximum: int, code: str, minimum: int = 1) -> bytes:
    _need(type(raw) is bytes and minimum <= len(raw) <= maximum, code)
    return raw


def _decode_console_records(raw: bytes) -> List[str]:
    try:
        text = raw.decode("utf-8-sig", errors="strict")
    except UnicodeDecodeError:
        _fail("RUN_STDOUT_UTF8_INVALID")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    for index, line in enumerate(lines):
        if line.endswith("\r"):
            lines[index] = line[:-1]
        _need("\r" not in lines[index], "RUN_STDOUT_LINE_ENDING_INVALID")
    return lines


def _is_reserved_event_line(line: str) -> bool:
    return line.lstrip().upper().startswith(("RB100_", "P07_", "PASS: RB100"))


def _java_sort_key(value: str) -> bytes:
    """UTF-16BE byte ordering equals Java String.compareTo code-unit ordering."""
    return value.encode("utf-16-be", errors="strict")


def _is_sorted_unique(values: Sequence[str], code: str) -> None:
    keys = [_java_sort_key(value) for value in values]
    _need(keys == sorted(keys) and len(set(keys)) == len(keys), code)


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
    except ADriveGraphInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _load_guard_reader() -> Tuple[types.ModuleType, types.ModuleType, bytes,
                                  Path, bytes, Path]:
    guard_path = Path(os.path.abspath(__file__)).parent / "read_load12_adrive_guard.py"
    guard_raw = _bootstrap_read(guard_path, MAX_RESULT_BYTES, "GUARD_READER")
    _need(_sha256(guard_raw) == GUARD_READER_SHA256,
          "GUARD_READER_HASH_MISMATCH")
    try:
        source = guard_raw.decode("utf-8", errors="strict")
        guard = types.ModuleType("_p07_adrive_guard_reader_private")
        guard.__file__ = os.fspath(guard_path)
        guard.__package__ = ""
        exec(compile(source, os.fspath(guard_path), "exec", dont_inherit=True),
             guard.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("GUARD_READER_SOURCE_INVALID")
    after = _bootstrap_read(guard_path, MAX_RESULT_BYTES, "GUARD_READER_RECHECK")
    _need(after == guard_raw and _sha256(after) == GUARD_READER_SHA256,
          "GUARD_READER_CHANGED_DURING_LOAD")
    for name in ("_validate_witness", "_load_base_reader", "_bind_selected_source_root",
                 "_resolve_path", "_read_bounded"):
        _need(callable(getattr(guard, name, None)), "GUARD_READER_API_INVALID")
    try:
        helper, helper_raw, helper_path = guard._load_base_reader()
    except Exception:
        _fail("BASE_READER_LOAD_FAILED")
    _need(_sha256(helper_raw) == BASE_READER_SHA256 and
          callable(getattr(helper, "_validate_event", None)) and
          callable(getattr(helper, "_audit_inputs_manifest", None)) and
          callable(getattr(helper, "_strict_json", None)) and
          callable(getattr(helper, "_decode_lines", None)) and
          callable(getattr(helper, "_decode_wrapper_lines", None)) and
          callable(getattr(helper, "_parse_tagged", None)) and
          callable(getattr(helper, "_resolve_path", None)) and
          callable(getattr(helper, "_read_bounded", None)),
          "BASE_READER_API_INVALID")
    return guard, helper, guard_raw, guard_path, helper_raw, helper_path


def _helper_call(helper: types.ModuleType, name: str, fallback: str, *args, **kwargs):
    function = getattr(helper, name, None)
    _need(callable(function), "BASE_READER_API_INVALID")
    try:
        return function(*args, **kwargs)
    except getattr(helper, "Load12RemainderInputError", ()):  # type: ignore[arg-type]
        _fail(fallback)


def _resolve_path(value: Any, base: Path, code: str, helper: types.ModuleType) -> Path:
    return _helper_call(helper, "_resolve_path", code + "_PATH_INVALID", value, base, code)


def _read_bounded(path: Path, limit: int, code: str, helper: types.ModuleType,
                  allow_empty: bool = False) -> bytes:
    return _helper_call(helper, "_read_bounded", code + "_READ_FAILED", path, limit,
                        code, allow_empty=allow_empty)


def _strict_json(raw: bytes, label: str, helper: types.ModuleType) -> Any:
    value = _helper_call(helper, "_strict_json", label + "_JSON_INVALID", raw, label)
    _check_all_strings(value)
    return value


def _line_body(line: str, marker: str, code: str, maximum: int) -> bytes:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix), code + "_MARKER_INVALID")
    try:
        body = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail(code + "_UTF8_INVALID")
    _bounded_bytes(body, maximum, code + "_SIZE_INVALID")
    _need(not body.startswith(b"\xef\xbb\xbf"), code + "_BOM_INVALID")
    return body


def _expected_command() -> Dict[str, Any]:
    return {
        "entry": ENTRY_SCRIPT,
        "suite": SUITE,
        "suites": [SUITE],
        "args": ["-Rb100Load12Cost240GuardGraphReplay"],
        "environment": {},
    }


def _validate_input_spec(value: Any, spec_directory: Path,
                         guard: types.ModuleType, helper: types.ModuleType
                         ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    _exact_keys(value, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    _need(value["schema"] == INPUT_SCHEMA and value["target"] == TARGET and
          value["purpose"] == PURPOSE and value["suite"] == SUITE and
          type(value["sourceSnapshotRevision"]) is int and
          value["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(value["inputCount"]) is int and value["inputCount"] == SOURCE_INPUT_COUNT and
          _integer(value["inputBytes"], 1, MAX_SOURCE_BYTES) and
          type(value["pinsSha256"]) is str and SHA256_RE.fullmatch(value["pinsSha256"]) is not None,
          "INPUT_SPEC_IDENTITY_INVALID")
    source_root = _resolve_path(value["sourceRoot"], spec_directory, "SOURCE_ROOT", helper)
    _need(source_root.is_dir(), "SOURCE_ROOT_NOT_DIRECTORY")
    guard._bind_selected_source_root(source_root, helper)
    reader_path = Path(os.path.abspath(__file__))
    _need(len(reader_path.parents) > 3 and source_root == reader_path.parents[3],
          "INPUT_SPEC_SOURCE_ROOT_NOT_READER_WORKTREE")
    pins_path = _resolve_path(value["pinsPath"], spec_directory, "SOURCE_PINS", helper)
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS", helper)
    try:
        input_context = helper._audit_inputs_manifest(pins_raw, value, source_root)
    except getattr(helper, "Load12RemainderInputError", ()):  # type: ignore[arg-type]
        _fail("SOURCE_MANIFEST_INVALID")
    pinned_paths = set(input_context["pinMap"])
    production_java_count = sum(path.startswith("src/") and path.endswith(".java")
                                for path in pinned_paths)
    contract_java_count = sum(path.startswith("tests/contracts/") and
                              path.endswith(".java") for path in pinned_paths)
    _need(len(pinned_paths) == SOURCE_INPUT_COUNT and
          production_java_count == PRODUCTION_JAVA_COUNT and
          contract_java_count == CONTRACT_JAVA_COUNT and
          len(pinned_paths) - production_java_count - contract_java_count == 6,
          "SOURCE_MANIFEST_JAVA_SPLIT_INVALID")
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
        **input_context,
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
          type(value["inputBytes"]) is int and value["inputBytes"] == context["inputBytes"] and
          value["inputPinsSha256"] == context["pinsSha256"] and
          value["inputAudit"] == "PASS_BEFORE_AND_AFTER",
          "NATIVE_RUN_SOURCE_BINDING_INVALID")
    _need(type(value["exitCode"]) is int and value["exitCode"] == 0 and
          type(value["nativeExitCode"]) is int and value["nativeExitCode"] == 0 and
          value["terminationProven"] is True and value["cleanupStatus"] == "PASS" and
          value["cleanupElapsedMs"] is None and value["viaCost"] == 60 and
          type(value["viaCost"]) is int and
          value["nativeLimitMs"] == MAX_NATIVE_LIMIT_MS and
          type(value["nativeLimitMs"]) is int and
          value["outerLimitMs"] == MAX_OUTER_LIMIT_MS and
          type(value["outerLimitMs"]) is int and
          value["requestedExecutable"] == "powershell.exe",
          "NATIVE_RUN_EXIT_CLEANUP_OR_BUDGET_INVALID")
    _need(_integer(value["processId"], 1, MAX_JAVA_LONG) and
          type(value["processStartTicks"]) is str and
          TICKS_RE.fullmatch(value["processStartTicks"]) is not None and
          type(value["elapsedSeconds"]) in (int, float) and
          math.isfinite(value["elapsedSeconds"]) and 0 < value["elapsedSeconds"] <= 1_800,
          "NATIVE_RUN_PROCESS_INVALID")
    for name, raw in (("stdout", context["stdoutRaw"]),
                      ("stderr", context["stderrRaw"]),
                      ("receipt", context["receiptRaw"])):
        _need(_integer(value[name + "Bytes"], 0, MAX_CAPTURE_BYTES) and
              value[name + "Bytes"] == len(raw) and
              type(value[name + "Sha256"]) is str and
              SHA256_RE.fullmatch(value[name + "Sha256"]) is not None and
              value[name + "Sha256"] == _sha256(raw),
              "NATIVE_RUN_" + name.upper() + "_BINDING_INVALID")
    _need(context["stderrRaw"] == b"", "RUN_STDERR_NOT_EMPTY")
    return dict(value)


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, helper: types.ModuleType
                                  ) -> Tuple[List[str], List[str], Dict[str, Any], bytes,
                                             str, Dict[str, Any], bytes, str,
                                             Dict[str, Any], bytes]:
    _bounded_bytes(stdout_raw, MAX_CAPTURE_BYTES, "RUN_STDOUT_SIZE_INVALID")
    _bounded_bytes(receipt_raw, MAX_CAPTURE_BYTES, "RUN_RECEIPT_SIZE_INVALID")
    _need(not receipt_raw.startswith(b"\xef\xbb\xbf") and b"\r" not in receipt_raw and
          receipt_raw.endswith(b"\n") and receipt_raw.count(b"\n") == 4,
          "RECEIPT_UTF8_LF_SHAPE_INVALID")
    try:
        receipt_text = receipt_raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        _fail("RUN_RECEIPT_UTF8_INVALID")
    console = _decode_console_records(stdout_raw)
    receipt = receipt_text[:-1].split("\n")
    _need(stderr_raw == b"", "RUN_STDERR_NOT_EMPTY")
    _need(len(receipt) == 4 and
          receipt[0].startswith(BASE_EVENT_MARKER + " ") and
          receipt[1].startswith(GUARD_EVENT_MARKER + " ") and
          receipt[2].startswith(GRAPH_EVENT_MARKER + " ") and
          SUMMARY_RE.fullmatch(receipt[3]) is not None,
          "RECEIPT_LINE_ORDER_OR_COUNT_INVALID")
    base_payload = _line_body(receipt[0], BASE_EVENT_MARKER, "BASE_EVENT", MAX_LINE_BYTES)
    guard_payload = _line_body(receipt[1], GUARD_EVENT_MARKER,
                               "GUARD_EVENT", MAX_GUARD_PAYLOAD_BYTES)
    graph_payload = _line_body(receipt[2], GRAPH_EVENT_MARKER,
                               "GRAPH_EVENT", MAX_GRAPH_PAYLOAD_BYTES)
    # Bind these original bytes before any JSON parser sees their contents.
    base_payload_sha256 = _sha256(base_payload)
    guard_payload_sha256 = _sha256(guard_payload)
    event_lines = [line for line in receipt if _is_reserved_event_line(line)]
    console_events = [line for line in console if _is_reserved_event_line(line)]
    _need(event_lines == receipt and console_events == event_lines,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    for line in console:
        left_trimmed = line.lstrip()
        if _is_reserved_event_line(line):
            _need(line == left_trimmed, "RUN_CONSOLE_EVENT_EDGE_WHITESPACE")
        _need(WRAPPER_FAILURE_RE.search(line.strip()) is None,
              "RUN_CONSOLE_FAILURE_LINE")
    base_index = console.index(receipt[0])
    _need(console.count(receipt[0]) == 1 and console.count(receipt[1]) == 1 and
          console.count(receipt[2]) == 1 and console.count(receipt[3]) == 1 and
          console.index(receipt[1]) == base_index + 1 and
          console.index(receipt[2]) == base_index + 2 and
          console.index(receipt[3]) == base_index + 3 and
          console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(receipt[3]) < console.index(FOCUSED_PASS) <
          console.index(CLEANUP_LINE), "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    match = SUMMARY_RE.fullmatch(receipt[3])
    _need(match is not None and _integer(int(match.group(1)), 1, MAX_SCALAR),
          "RECEIPT_SUMMARY_ASSERTIONS_INVALID")
    base_event = _helper_call(helper, "_parse_tagged", "BASE_EVENT_INVALID",
                              receipt[0], BASE_EVENT_MARKER, "BASE_EVENT")
    guard_event = _helper_call(helper, "_parse_tagged", "GUARD_EVENT_INVALID",
                               receipt[1], GUARD_EVENT_MARKER, "GUARD_EVENT")
    graph_event = _helper_call(helper, "_parse_tagged", "GRAPH_EVENT_INVALID",
                               receipt[2], GRAPH_EVENT_MARKER, "GRAPH_EVENT")
    _check_all_strings(base_event)
    _check_all_strings(guard_event)
    _check_all_strings(graph_event)
    return (console, receipt, base_event, base_payload, base_payload_sha256,
            guard_event, guard_payload, guard_payload_sha256, graph_event, graph_payload)


def _map_pad_cell(x: int, y: int, grid: Mapping[str, int], code: str) -> int:
    dx = x - grid["originX"]
    dy = y - grid["originY"]
    _need(dx >= 0 and dy >= 0 and dx % grid["pitch"] == 0 and
          dy % grid["pitch"] == 0, code + "_OFF_GRID")
    column = dx // grid["pitch"]
    row = dy // grid["pitch"]
    _need(column < grid["width"] and row < grid["height"], code + "_OUT_OF_GRID")
    return row * grid["width"] + column


def _map_hole_cell(x: int, y: int, grid: Mapping[str, int]) -> Optional[int]:
    dx = x - grid["originX"]
    dy = y - grid["originY"]
    if dx < 0 or dy < 0 or dx % grid["pitch"] != 0 or dy % grid["pitch"] != 0:
        return None
    column = dx // grid["pitch"]
    row = dy // grid["pitch"]
    if column >= grid["width"] or row >= grid["height"]:
        return None
    return row * grid["width"] + column


def _check_grid_dimensions(width: Any, height: Any) -> int:
    _need(type(width) is int and type(height) is int and width > 0 and height > 0,
          "GRAPH_DIMENSIONS_INVALID")
    cells = width * height
    _need(cells <= MAX_GRID_CELLS, "GRAPH_CELL_LIMIT")
    return cells


def _check_witness_node_count(count: Any) -> int:
    _need(_integer(count, 1, MAX_WITNESS_NODES), "OBSERVED_PATH_NODE_BOUND")
    return count


def _check_final_controller_units(pre_report_units: Any, final_units: Any) -> None:
    _need(_integer(pre_report_units, 1, MAX_WORK_UNITS - 1) and
          _integer(final_units, 1, MAX_WORK_UNITS) and
          final_units == pre_report_units + 1,
          "WORK_FINAL_CONTROLLER_UNITS_INVALID")


def _validate_metadata_envelope(graph_bytes: Any, source_rows_bytes: Any,
                                goal_states: Any, graph_cells: Any,
                                metadata_groups: Any) -> None:
    _need(_integer(metadata_groups, 1, MAX_WORK_UNITS),
          "WORK_METADATA_GROUP_COUNT_INVALID")
    _need(_integer(graph_bytes, 1, MAX_GRAPH_BYTES) and
          _integer(source_rows_bytes, 1, MAX_GRAPH_BYTES) and
          _integer(goal_states, 0, MAX_GRID_CELLS * 2) and
          _integer(graph_cells, 1, MAX_GRID_CELLS),
          "WORK_METADATA_INPUT_INVALID")
    group_capacity = metadata_groups * MAX_METADATA_CHARS_PER_GROUP
    canonical_bytes = graph_bytes + source_rows_bytes
    _need(canonical_bytes <= group_capacity,
          "WORK_METADATA_SERIALIZE_ENVELOPE_INVALID")
    _need(canonical_bytes <= group_capacity,
          "WORK_METADATA_HASH_ENVELOPE_INVALID")
    _need(2 * graph_cells <= group_capacity,
          "WORK_METADATA_PROVENANCE_STATE_ENVELOPE_INVALID")
    _need(goal_states <= group_capacity,
          "WORK_METADATA_PROVENANCE_GOAL_ENVELOPE_INVALID")


def _validate_grid(value: Any) -> Dict[str, int]:
    _exact_keys(value, frozenset("width height cells originX originY pitch".split()),
                "GRAPH_EVENT_GRID_KEYS_INVALID")
    cells = _check_grid_dimensions(value["width"], value["height"])
    _need(_integer(value["cells"], 1, MAX_GRID_CELLS) and value["cells"] == cells and
          _integer(value["pitch"], 10, 10) and
          _integer(value["originX"], MIN_SCALAR, MAX_SCALAR) and
          _integer(value["originY"], MIN_SCALAR, MAX_SCALAR),
          "GRAPH_EVENT_GRID_VALUES_INVALID")
    _need(-MAX_ABS_BOARD_COORDINATE <= value["originX"] <= MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["originY"] <= MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <=
          value["originX"] + (value["width"] - 1) * value["pitch"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <=
          value["originY"] + (value["height"] - 1) * value["pitch"] <=
          MAX_ABS_BOARD_COORDINATE, "GRAPH_GRID_BOARD_COORDINATE_INVALID")
    return dict(value)


def _validate_graph(graph: Any, integrity: Any, grid: Mapping[str, int],
                    start_cell: int) -> Tuple[Dict[str, Any], bytes]:
    _exact_keys(graph, GRAPH_KEYS, "GRAPH_KEYS_INVALID")
    _need(graph["protocol"] == GRAPH_PROTOCOL and graph["model"] == GRAPH_MODEL and
          graph["caseId"] == GRAPH_CASE_ID and
          type(graph["protocol"]) is str and type(graph["model"]) is str and
          type(graph["caseId"]) is str,
          "GRAPH_IDENTITY_INVALID")
    width, height = graph["width"], graph["height"]
    cells = _check_grid_dimensions(width, height)
    _need(width == grid["width"] and height == grid["height"] and
          graph["primary"] in (0, 1) and type(graph["primary"]) is int,
          "GRAPH_GRID_OR_PRIMARY_MISMATCH")
    steps_raw = graph["steps"]
    _need(type(steps_raw) is str and len(steps_raw) == cells * 2 and
          all(("0" <= digit <= "9") or ("a" <= digit <= "f")
              for digit in steps_raw),
          "GRAPH_STEPS_INVALID")
    vias_raw = graph["vias"]
    _need(type(vias_raw) is str and len(vias_raw) == cells and
          all(digit == "0" or digit == "1" for digit in vias_raw),
          "GRAPH_VIAS_INVALID")
    goals_raw = graph["goals"]
    _need(type(goals_raw) is str and len(goals_raw) == cells * 2 and
          all(digit == "0" or digit == "1" for digit in goals_raw),
          "GRAPH_GOALS_INVALID")
    starts = graph["starts"]
    _need(_integer(start_cell, 0, cells - 1) and type(starts) is list and
          len(starts) == 2 and
          all(type(state) is int and 0 <= state < cells * 2 for state in starts) and
          starts == [start_cell, cells + start_cell], "GRAPH_STARTS_INVALID")
    _exact_keys(integrity, GRAPH_INTEGRITY_KEYS, "GRAPH_INTEGRITY_KEYS_INVALID")
    _need(integrity["encoding"] == GRAPH_ENCODING and
          _integer(integrity["canonicalBytes"], 1, MAX_GRAPH_BYTES),
          "GRAPH_INTEGRITY_VALUES_INVALID")
    _digest(integrity["sha256"], "GRAPH_INTEGRITY_SHA_INVALID")
    canonical = _canonical_json(graph)
    _need(len(canonical) <= MAX_GRAPH_BYTES and
          len(canonical) == integrity["canonicalBytes"] and
          _sha256(canonical) == integrity["sha256"],
          "GRAPH_CANONICAL_BINDING_INVALID")

    steps = [int(digit, 16) for digit in steps_raw]
    for state, mask in enumerate(steps):
        cell = state % cells
        x, y = cell % width, cell // width
        _need(not (y == 0 and mask & 1) and
              not (x == width - 1 and mask & 2) and
              not (y == height - 1 and mask & 4) and
              not (x == 0 and mask & 8), "GRAPH_STEP_LEAVES_GRID")
    normalized = dict(graph)
    normalized["steps"] = steps
    normalized["vias"] = [digit == "1" for digit in vias_raw]
    normalized["goals"] = [digit == "1" for digit in goals_raw]
    return normalized, canonical


def _validate_rect(value: Any, code: str) -> Dict[str, int]:
    _exact_keys(value, RECT_KEYS, code + "_KEYS_INVALID")
    for name in ("x", "y"):
        _need(_integer(value[name], MIN_SCALAR, MAX_SCALAR), code + "_COORDINATE_INVALID")
    for name in ("width", "height"):
        _need(_integer(value[name], 1, MAX_SCALAR), code + "_SIZE_INVALID")
    _need(-MAX_ABS_BOARD_COORDINATE <= value["x"] <= MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["y"] <= MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["x"] + value["width"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["y"] + value["height"] <=
          MAX_ABS_BOARD_COORDINATE, code + "_BOUNDS_OVERFLOW")
    return dict(value)


def _rect_contains(outer: Mapping[str, int], x: int, y: int) -> bool:
    return (outer["x"] <= x <= outer["x"] + outer["width"] and
            outer["y"] <= y <= outer["y"] + outer["height"])


def _rect_contains_rect(outer: Mapping[str, int], inner: Mapping[str, int]) -> bool:
    return (inner["x"] >= outer["x"] and inner["y"] >= outer["y"] and
            inner["x"] + inner["width"] <= outer["x"] + outer["width"] and
            inner["y"] + inner["height"] <= outer["y"] + outer["height"])


def _validate_pad_row(value: Any, grid: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, PAD_KEYS, "PAD_ROW_KEYS_INVALID")
    pad_id = _identifier(value["padId"], "PAD_ID_INVALID")
    net_id = _identifier(value["netId"], "PAD_NET_ID_INVALID")
    _need(_integer(value["x"], -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE) and
          _integer(value["y"], -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE) and
          _integer(value["escapeDx"], -1, 1) and
          _integer(value["escapeDy"], -1, 1) and
          _integer(value["escapeLength"], 0, MAX_SCALAR), "PAD_ROW_COORDINATE_INVALID")
    _need(abs(value["escapeDx"]) + abs(value["escapeDy"]) <= 1 and
          (value["escapeLength"] == 0 or
           value["escapeDx"] != 0 or value["escapeDy"] != 0),
          "PAD_ROW_ESCAPE_INVALID")
    _need(-MAX_ABS_BOARD_COORDINATE <=
          value["x"] + value["escapeDx"] * value["escapeLength"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <=
          value["y"] + value["escapeDy"] * value["escapeLength"] <=
          MAX_ABS_BOARD_COORDINATE, "PAD_ESCAPE_ENDPOINT_OUT_OF_RANGE")
    _need(value["attachment"] == "PLATED_THROUGH_HOLE" and
          value["mountingSide"] in ("TOP", "BOTTOM") and
          value["exposure"] in ("EXPOSED", "COVERED"), "PAD_ROW_ENUM_INVALID")
    pad_bounds = _validate_rect(value["padBounds"], "PAD_BOUNDS")
    probe_bounds = _validate_rect(value["probeBounds"], "PAD_PROBE_BOUNDS")
    _need(_rect_contains(pad_bounds, value["x"], value["y"]) and
          _rect_contains_rect(probe_bounds, pad_bounds), "PAD_ROW_RECTANGLE_CONTAINMENT_INVALID")
    _need(_integer(value["copperLayerMask"], 0, 3) and
          value["copperLayerMask"] == 3, "PAD_COPPER_MASK_INVALID")
    _need(_integer(value["gridCell"], 0, grid["cells"] - 1) and
          value["gridCell"] == _map_pad_cell(value["x"], value["y"], grid, "PAD_GRID"),
          "PAD_GRID_CELL_INVALID")
    return {**dict(value), "padId": pad_id, "netId": net_id,
            "padBounds": pad_bounds, "probeBounds": probe_bounds}


def _segment_grid_cells(x_points: Sequence[int], y_points: Sequence[int],
                        grid: Mapping[str, int]) -> List[int]:
    cells = set()
    max_x = grid["originX"] + (grid["width"] - 1) * grid["pitch"]
    max_y = grid["originY"] + (grid["height"] - 1) * grid["pitch"]
    for index in range(1, len(x_points)):
        x0, x1 = x_points[index - 1], x_points[index]
        y0, y1 = y_points[index - 1], y_points[index]
        horizontal = y0 == y1 and x0 != x1
        vertical = x0 == x1 and y0 != y1
        _need(horizontal or vertical, "TRACE_SEGMENT_INVALID")
        if horizontal:
            fixed = y0 - grid["originY"]
            low, high = min(x0, x1), max(x0, x1)
            _need(fixed >= 0 and fixed % grid["pitch"] == 0 and fixed // grid["pitch"] <
                  grid["height"] and grid["originX"] <= low <= high <= max_x,
                  "TRACE_HORIZONTAL_ROUTER_MAPPING_INVALID")
            row = fixed // grid["pitch"]
            first = grid["originX"] + ((low - grid["originX"] + grid["pitch"] - 1) //
                                        grid["pitch"]) * grid["pitch"]
            for point_x in range(first, high + 1, grid["pitch"]):
                cells.add(row * grid["width"] + (point_x - grid["originX"]) // grid["pitch"])
        else:
            fixed = x0 - grid["originX"]
            low, high = min(y0, y1), max(y0, y1)
            _need(fixed >= 0 and fixed % grid["pitch"] == 0 and fixed // grid["pitch"] <
                  grid["width"] and grid["originY"] <= low <= high <= max_y,
                  "TRACE_VERTICAL_ROUTER_MAPPING_INVALID")
            column = fixed // grid["pitch"]
            first = grid["originY"] + ((low - grid["originY"] + grid["pitch"] - 1) //
                                        grid["pitch"]) * grid["pitch"]
            for point_y in range(first, high + 1, grid["pitch"]):
                cells.add(((point_y - grid["originY"]) // grid["pitch"]) *
                          grid["width"] + column)
    return sorted(cells)


def _validate_trace_row(value: Any, grid: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, TRACE_KEYS, "TRACE_ROW_KEYS_INVALID")
    source_id = _identifier(value["sourceId"], "TRACE_SOURCE_ID_INVALID")
    net_id = _identifier(value["netId"], "TRACE_NET_ID_INVALID")
    _need(value["startPadId"] is None or type(value["startPadId"]) is str,
          "TRACE_START_PAD_ID_INVALID")
    _need(value["endPadId"] is None or type(value["endPadId"]) is str,
          "TRACE_END_PAD_ID_INVALID")
    start_id = (None if value["startPadId"] is None else
                _identifier(value["startPadId"], "TRACE_START_PAD_ID_INVALID"))
    end_id = (None if value["endPadId"] is None else
              _identifier(value["endPadId"], "TRACE_END_PAD_ID_INVALID"))
    _need(type(value["layer"]) is int and value["layer"] in (0, 1) and
          value["exposure"] in ("EXPOSED", "COVERED") and
          _integer(value["width"], 9, 63) and value["width"] % 2 == 1,
          "TRACE_ROW_POLICY_INVALID")
    xs, ys = value["xPoints"], value["yPoints"]
    _need(type(xs) is list and type(ys) is list and 2 <= len(xs) <= 16_384 and
          len(xs) == len(ys) and
          all(_integer(point, -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE)
              for point in xs) and
          all(_integer(point, -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE)
              for point in ys),
          "TRACE_POINT_ARRAY_INVALID")
    expected_cells = _segment_grid_cells(xs, ys, grid)
    grid_cells = value["routerGridCells"]
    _need(type(grid_cells) is list and
          all(_integer(cell, 0, grid["cells"] - 1) for cell in grid_cells) and
          grid_cells == expected_cells, "TRACE_ROUTER_GRID_CELLS_INVALID")
    return {**dict(value), "sourceId": source_id, "netId": net_id,
            "startPadId": start_id, "endPadId": end_id,
            "xPoints": list(xs), "yPoints": list(ys), "routerGridCells": expected_cells}


def _validate_hole_row(value: Any, grid: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, HOLE_KEYS, "HOLE_ROW_KEYS_INVALID")
    hole_id = _identifier(value["id"], "HOLE_ID_INVALID")
    _need(value["kind"] in ("VIA", "PLATED", "NON_PLATED"), "HOLE_KIND_INVALID")
    net_id = value["netId"]
    if value["kind"] == "NON_PLATED":
        _need(net_id is None, "NPTH_NET_ID_INVALID")
    else:
        net_id = _identifier(net_id, "HOLE_NET_ID_INVALID")
    _need(_integer(value["x"], -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE) and
          _integer(value["y"], -MAX_ABS_BOARD_COORDINATE, MAX_ABS_BOARD_COORDINATE) and
          _integer(value["drillRadius"], 1, MAX_SCALAR) and
          _integer(value["landRadius"], 1, MAX_SCALAR) and
          value["exposure"] in ("EXPOSED", "COVERED"), "HOLE_ROW_GEOMETRY_INVALID")
    _need(2 * value["landRadius"] <= MAX_SCALAR and
          -MAX_ABS_BOARD_COORDINATE <= value["x"] - value["landRadius"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["x"] + value["landRadius"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["y"] - value["landRadius"] <=
          MAX_ABS_BOARD_COORDINATE and
          -MAX_ABS_BOARD_COORDINATE <= value["y"] + value["landRadius"] <=
          MAX_ABS_BOARD_COORDINATE, "HOLE_BOUNDS_OUT_OF_RANGE")
    if value["kind"] == "NON_PLATED":
        expected_mask = 0
        _need(value["landRadius"] == value["drillRadius"], "NPTH_LAND_RADIUS_INVALID")
    else:
        expected_mask = 3
        _need(value["landRadius"] > value["drillRadius"], "PLATED_LAND_RADIUS_INVALID")
    _need(_integer(value["conductiveLayerMask"], 0, 3) and
          value["conductiveLayerMask"] == expected_mask, "HOLE_COPPER_MASK_INVALID")
    expected_cell = _map_hole_cell(value["x"], value["y"], grid)
    _need(value["gridCell"] is None or _integer(value["gridCell"], 0, grid["cells"] - 1),
          "HOLE_GRID_CELL_INVALID")
    _need(value["gridCell"] == expected_cell, "HOLE_GRID_MAPPING_INVALID")
    return {**dict(value), "id": hole_id, "netId": net_id}


def _check_source_row_limits(pad_count: int, trace_count: int, hole_count: int) -> int:
    _need(_integer(pad_count, 0, MAX_PADS) and
          _integer(trace_count, 0, MAX_SOURCE_ROWS) and
          _integer(hole_count, 0, MAX_HOLES), "SOURCE_COLLECTION_LIMIT")
    total = pad_count + trace_count + hole_count
    _need(total <= MAX_SOURCE_ROWS, "SOURCE_ROW_LIMIT")
    return total


def _source_ref_key(row: Mapping[str, Any]) -> Tuple[str, bytes, str]:
    return row["sourceType"], _java_sort_key(row["sourceId"]), row["role"]


def _validate_goal_provenance(value: Any, graph: Mapping[str, Any],
                              census_rows: Mapping[str, Any], target: Mapping[str, Any],
                              endpoints: Mapping[str, Any], grid: Mapping[str, int]) -> List[Dict[str, Any]]:
    cells = grid["cells"]
    true_states = [state for state, present in enumerate(graph["goals"]) if present]
    _need(type(value) is list and len(value) == len(true_states),
          "GOAL_PROVENANCE_COVERAGE_INVALID")
    pads = {row["padId"]: row for row in census_rows["padRows"]}
    traces = {row["sourceId"]: row for row in census_rows["traceRows"]}
    holes = {row["id"]: row for row in census_rows["holeRows"]}
    reached = set(census_rows["reachedPthPadIds"])
    root_id = endpoints["root"]["padId"]
    root_cell = endpoints["root"]["cell"]
    actual_by_state: Dict[int, set] = {}
    primary_states = set()
    expected_rows = []
    for row, state in zip(value, true_states):
        _exact_keys(row, GOAL_PROVENANCE_KEYS, "GOAL_PROVENANCE_KEYS_INVALID")
        layer, cell = divmod(state, cells)
        _need(_integer(row["state"], 0, cells * 2 - 1) and row["state"] == state and
              _integer(row["cell"], 0, cells - 1) and row["cell"] == cell and
              type(row["layer"]) is int and row["layer"] == layer,
              "GOAL_PROVENANCE_STATE_INVALID")
        refs = row["sourceRefs"]
        _need(type(refs) is list and len(refs) > 0, "GOAL_SOURCE_REFS_EMPTY")
        checked_refs = []
        for ref in refs:
            _exact_keys(ref, SOURCE_REF_KEYS, "GOAL_SOURCE_REF_KEYS_INVALID")
            source_type = ref["sourceType"]
            _need(source_type in SOURCE_ROLES and type(source_type) is str,
                  "GOAL_SOURCE_TYPE_INVALID")
            source_id = _identifier(ref["sourceId"], "GOAL_SOURCE_ID_INVALID")
            role = ref["role"]
            _need(type(role) is str and role in SOURCE_ROLES[source_type],
                  "GOAL_SOURCE_ROLE_INVALID")
            checked_refs.append({"sourceType": source_type, "sourceId": source_id, "role": role})
        keys = [_source_ref_key(ref) for ref in checked_refs]
        _need(keys == sorted(keys) and len(set(keys)) == len(keys),
              "GOAL_SOURCE_REF_ORDER_OR_DUPLICATE")
        actual = {(ref["sourceType"], ref["sourceId"], ref["role"])
                  for ref in checked_refs}
        actual_by_state[state] = actual
        for source_type, source_id, role in actual:
            if source_type == "PAD":
                _need(source_id in pads, "GOAL_PAD_SOURCE_MISSING")
                pad = pads[source_id]
                if role == "ROOT_PAD":
                    _need(source_id == root_id and pad["gridCell"] == cell and
                          pad["copperLayerMask"] & (1 << layer),
                          "GOAL_ROOT_PAD_MISMATCH")
                else:
                    _need(source_id in reached and pad["attachment"] ==
                          "PLATED_THROUGH_HOLE" and pad["gridCell"] == cell and
                          pad["copperLayerMask"] & (1 << layer),
                          "GOAL_REACHED_PAD_MISMATCH")
                primary_states.add(state)
            elif source_type == "TRACE":
                _need(source_id in traces and traces[source_id]["netId"] == target["netId"] and
                      traces[source_id]["layer"] == layer and
                      cell in traces[source_id]["routerGridCells"],
                      "GOAL_TRACE_SOURCE_MISMATCH")
                primary_states.add(state)
            else:
                _need(source_id in holes, "GOAL_HOLE_SOURCE_MISSING")
                hole = holes[source_id]
                _need(hole["netId"] == target["netId"] and
                      hole["kind"] in ("VIA", "PLATED") and hole["gridCell"] == cell and
                      hole["conductiveLayerMask"] & (1 << layer),
                      "GOAL_BARREL_SOURCE_MISMATCH")
        expected_rows.append({"state": state, "cell": cell, "layer": layer,
                              "sourceRefs": checked_refs})

    root_pad = pads[root_id]
    for layer in (0, 1):
        state = layer * cells + root_cell
        if (graph["goals"][state] and
                root_pad["copperLayerMask"] & (1 << layer)):
            _need(("PAD", root_id, "ROOT_PAD") in actual_by_state[state],
                  "GOAL_ROOT_SOURCE_OMITTED")
            primary_states.add(state)
    for pad_id in reached:
        pad = pads[pad_id]
        for layer in (0, 1):
            state = layer * cells + pad["gridCell"]
            if graph["goals"][state] and pad["copperLayerMask"] & (1 << layer):
                _need(("PAD", pad_id, "REACHED_PTH_PAD") in actual_by_state[state],
                      "GOAL_REACHED_SOURCE_OMITTED")
                primary_states.add(state)
    for trace in census_rows["traceRows"]:
        supporting = False
        for cell in trace["routerGridCells"]:
            state = trace["layer"] * cells + cell
            if graph["goals"][state]:
                supporting = True
                _need(("TRACE", trace["sourceId"], "SAME_NET_TREE_TRACE") in
                      actual_by_state[state], "GOAL_TRACE_SOURCE_OMITTED")
                primary_states.add(state)
        _need(supporting, "TRACE_ROW_UNNEEDED")
    for hole in census_rows["holeRows"]:
        if hole["netId"] != target["netId"] or hole["gridCell"] is None:
            continue
        role = ("SAME_NET_VIA_BARREL" if hole["kind"] == "VIA" else
                "SAME_NET_PTH_BARREL" if hole["kind"] == "PLATED" else None)
        if role is None:
            continue
        for layer in (0, 1):
            state = layer * cells + hole["gridCell"]
            if (graph["goals"][state] and
                    hole["conductiveLayerMask"] & (1 << layer)):
                _need(("HOLE", hole["id"], role) in actual_by_state[state],
                      "GOAL_BARREL_SOURCE_OMITTED")
    _need(len(primary_states) == len(true_states), "GOAL_HAS_NO_PRIMARY_SOURCE")
    return expected_rows


def _validate_source_census(value: Any, graph: Mapping[str, Any],
                            grid: Mapping[str, int], endpoints: Mapping[str, Any],
                            target: Mapping[str, Any], prefix: Mapping[str, Any]
                            ) -> Dict[str, Any]:
    _exact_keys(value, SOURCE_CENSUS_KEYS, "SOURCE_CENSUS_KEYS_INVALID")
    pads_raw, traces_raw, holes_raw = value["padRows"], value["traceRows"], value["holeRows"]
    _need(type(pads_raw) is list and type(traces_raw) is list and type(holes_raw) is list,
          "SOURCE_COLLECTION_SHAPE_INVALID")
    total_rows = _check_source_row_limits(len(pads_raw), len(traces_raw), len(holes_raw))
    pads = [_validate_pad_row(row, grid) for row in pads_raw]
    traces = [_validate_trace_row(row, grid) for row in traces_raw]
    holes = [_validate_hole_row(row, grid) for row in holes_raw]
    _is_sorted_unique([row["padId"] for row in pads], "PAD_ROWS_ORDER_OR_DUPLICATE")
    _is_sorted_unique([row["sourceId"] for row in traces], "TRACE_ROWS_ORDER_OR_DUPLICATE")
    _is_sorted_unique([row["id"] for row in holes], "HOLE_ROWS_ORDER_OR_DUPLICATE")
    _need(all(row["netId"] == target["netId"] for row in pads) and
          all(row["netId"] == target["netId"] for row in traces),
          "SOURCE_ROW_FOREIGN_NET")
    pad_map = {row["padId"]: row for row in pads}
    trace_ids = {row["sourceId"] for row in traces}
    hole_map = {row["id"]: row for row in holes}
    start_id, root_id = endpoints["start"]["padId"], endpoints["root"]["padId"]
    _need(start_id in pad_map and root_id in pad_map, "ENDPOINT_PAD_ROW_MISSING")
    for name, endpoint in (("start", endpoints["start"]), ("root", endpoints["root"])):
        row = pad_map[endpoint["padId"]]
        _need(row["x"] == endpoint["x"] and row["y"] == endpoint["y"] and
              row["gridCell"] == endpoint["cell"], "ENDPOINT_PAD_ROW_MISMATCH")
    reached_ids = value["reachedPthPadIds"]
    _need(type(reached_ids) is list and all(type(item) is str for item in reached_ids),
          "REACHED_PTH_IDS_INVALID")
    reached_ids = [_identifier(item, "REACHED_PTH_ID_INVALID") for item in reached_ids]
    _is_sorted_unique(reached_ids, "REACHED_PTH_IDS_ORDER_OR_DUPLICATE")
    _need(root_id not in reached_ids and start_id not in reached_ids and
          set(reached_ids) <= set(pad_map),
          "REACHED_PTH_IDS_BINDING_INVALID")
    for pad_id in reached_ids:
        _need(pad_map[pad_id]["attachment"] == "PLATED_THROUGH_HOLE" and
              pad_map[pad_id]["copperLayerMask"] == 3,
              "REACHED_PTH_ROW_INVALID")
    cells = grid["cells"]
    for pad_id in (root_id, *reached_ids):
        pad = pad_map[pad_id]
        _need(pad["attachment"] == "PLATED_THROUGH_HOLE" and
              pad["copperLayerMask"] == 3 and
              graph["goals"][pad["gridCell"]] is True and
              graph["goals"][cells + pad["gridCell"]] is True,
              "MANDATORY_PTH_GOAL_FACES_MISSING")
    needed_pad_ids = {start_id, root_id, *reached_ids}
    for trace in traces:
        for endpoint_id in (trace["startPadId"], trace["endPadId"]):
            if endpoint_id is not None:
                _need(endpoint_id in pad_map, "TRACE_ENDPOINT_PAD_ROW_MISSING")
                needed_pad_ids.add(endpoint_id)
    _need(set(pad_map) == needed_pad_ids, "PAD_ROWS_NEEDED_SET_MISMATCH")

    needed_trace_ids = set()
    for trace in traces:
        _need(any(graph["goals"][trace["layer"] * cells + cell]
                  for cell in trace["routerGridCells"]), "TRACE_ROW_UNNEEDED")
        needed_trace_ids.add(trace["sourceId"])
    _need(trace_ids == needed_trace_ids, "TRACE_ROWS_NEEDED_SET_MISMATCH")

    _need(len(holes) == prefix["before"]["holeCount"] and
          prefix["before"]["holeCount"] == 37 and
          prefix["before"]["traceCount"] == 177,
          "COPIED_PREFIX_SOURCE_COUNT_MISMATCH")
    counts_by_kind = {kind: sum(row["kind"] == kind for row in holes)
                      for kind in ("VIA", "PLATED", "NON_PLATED")}
    kind_rows = value["holeKindCounts"]
    _need(type(kind_rows) is list and len(kind_rows) == 3,
          "HOLE_KIND_COUNTS_SHAPE_INVALID")
    expected_kind_rows = []
    for row, kind in zip(kind_rows, ("VIA", "PLATED", "NON_PLATED")):
        _exact_keys(row, KIND_COUNT_KEYS, "HOLE_KIND_COUNT_KEYS_INVALID")
        _need(row["kind"] == kind and
              _integer(row["count"], 0, len(holes)) and row["count"] == counts_by_kind[kind],
              "HOLE_KIND_COUNT_MISMATCH")
        expected_kind_rows.append(dict(row))
    row_counts = value["rowCounts"]
    _exact_keys(row_counts, ROW_COUNT_KEYS, "SOURCE_ROW_COUNTS_KEYS_INVALID")
    expected_counts = {
        "padRows": len(pads), "traceRows": len(traces), "holeRows": len(holes),
        "sourceRows": total_rows,
        "goalStates": sum(graph["goals"]),
        "allHoleCount": len(holes), "viaKindCount": counts_by_kind["VIA"],
    }
    _need(_same_json(row_counts, expected_counts), "SOURCE_ROW_COUNTS_MISMATCH")
    provenance = _validate_goal_provenance(value["goalProvenance"], graph,
                                           {"padRows": pads, "traceRows": traces,
                                            "holeRows": holes,
                                            "reachedPthPadIds": reached_ids},
                                           target, endpoints, grid)
    canonical_obj = {
        "goalProvenance": value["goalProvenance"],
        "holeRows": holes_raw,
        "padRows": pads_raw,
        "traceRows": traces_raw,
    }
    canonical = _canonical_json(canonical_obj)
    _need(_integer(value["sourceRowsCanonicalBytes"], 1, MAX_GRAPH_BYTES) and
          len(canonical) == value["sourceRowsCanonicalBytes"] and
          _digest(value["sourceRowsSha256"], "SOURCE_ROWS_SHA_INVALID") ==
          _sha256(canonical), "SOURCE_ROWS_CANONICAL_BINDING_INVALID")
    return {
        "padRows": pads, "traceRows": traces, "holeRows": holes,
        "reachedPthPadIds": reached_ids, "holeKindCounts": expected_kind_rows,
        "goalProvenance": provenance, "rowCounts": expected_counts,
        "sourceRowsCanonicalBytes": len(canonical),
        "sourceRowsSha256": _sha256(canonical), "canonicalBytes": canonical,
        "padMap": pad_map, "traceIds": trace_ids, "holeMap": hole_map,
    }


def _validate_policy(value: Any, row_counts: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, POLICY_KEYS, "POLICY_KEYS_INVALID")
    spacing = value["viaSpacing"]
    _exact_keys(spacing, VIA_SPACING_KEYS, "VIA_SPACING_KEYS_INVALID")
    expected = {
        "layerMap": ["TOP", "BOTTOM"],
        "directionBits": EXPECTED_DIRECTION_BITS,
        "viaMaskSemantics": "HISTORY_RELAXED",
        "viaMaskPredicate": "relaxedViaFits",
        "primaryStepCost": 10,
        "secondaryStepCost": 12,
        "secondaryStartCost": 2,
        "turnCost": 35,
        "copiedViaCost": 240,
        "maximumHoleCount": 48,
        "actualAllHoleCount": row_counts["allHoleCount"],
        "actualViaKindCount": row_counts["viaKindCount"],
        "maximumTransitions": MAX_TRANSITIONS,
        "pathTransitionPredicate": "pathViaFits",
        "viaMaskExcludesRemainingHoleCapacity": True,
        "viaMaskExcludesEarlierPathViaSpacing": True,
        "searchPerformed": False,
        "routeFeasibilityClaim": False,
        "viaSpacing": {
            "axisThreshold": 20,
            "comparison": "STRICT_LT",
            "combineAxes": "AND",
            "compareAgainst": "EVERY_EARLIER_PATH_VIA",
            "equalityAt20Permitted": True,
        },
    }
    _need(_same_json(value, expected), "POLICY_VALUES_INVALID")
    return dict(value)


def _via_spacing_conflict(first_x: int, first_y: int,
                          second_x: int, second_y: int,
                          threshold: int = 20) -> bool:
    return abs(first_x - second_x) < threshold and abs(first_y - second_y) < threshold


def _verify_observed_path(graph: Mapping[str, Any], path: Mapping[str, Any],
                          grid: Mapping[str, int], endpoints: Mapping[str, Any],
                          policy: Mapping[str, Any], all_hole_count: int) -> Dict[str, Any]:
    nodes = path["nodes"]
    cells = grid["cells"]
    _need(type(nodes) is list, "OBSERVED_PATH_NODE_BOUND")
    _check_witness_node_count(len(nodes))
    first = nodes[0]
    _need(type(first) is list and len(first) == 11 and
          first[0] == endpoints["start"]["cell"] and first[1] in (0, 1) and
          first[4] == 4 and first[5] == 0 and
          first[6] == (0 if first[1] == graph["primary"] else 2) and
          first[1] * cells + first[0] in graph["starts"],
          "OBSERVED_PATH_START_INVALID")
    prior_direction: Optional[int] = None
    via_cells: List[int] = []
    transitions = 0
    for index in range(1, len(nodes)):
        previous, current = nodes[index - 1], nodes[index]
        _need(type(previous) is list and len(previous) == 11 and
              type(current) is list and len(current) == 11,
              "OBSERVED_PATH_ROW_INVALID")
        pcell, player, px, py, _pdir, ptrans, pcost = previous[:7]
        cell, layer, x, y, direction, trans, cost = current[:7]
        _need(type(pcell) is int and type(player) is int and type(cell) is int and
              type(layer) is int and 0 <= pcell < cells and 0 <= cell < cells and
              player in (0, 1) and layer in (0, 1), "OBSERVED_PATH_STATE_INVALID")
        if player == layer:
            dx, dy = cell % grid["width"] - pcell % grid["width"], \
                     cell // grid["width"] - pcell // grid["width"]
            direction = {(0, -1): 0, (1, 0): 1, (0, 1): 2, (-1, 0): 3}.get((dx, dy))
            _need(direction is not None and trans == ptrans and
                  current[4] == direction and current[5] == ptrans,
                  "OBSERVED_PATH_PLANAR_EDGE_INVALID")
            bit = (1, 2, 4, 8)[direction]
            source_state = player * cells + pcell
            _need(graph["steps"][source_state] & bit,
                  "OBSERVED_PATH_STEP_MASK_MISMATCH")
            turn_cost = (35 if prior_direction is not None and
                         direction != prior_direction else 0)
            step_cost = 10 if layer == graph["primary"] else 12
            _need(cost == pcost + step_cost + turn_cost,
                  "OBSERVED_PATH_PLANAR_COST_INVALID")
            prior_direction = direction
        else:
            _need(pcell == cell and current[4] == 4 and trans == ptrans + 1 and
                  current[5] == trans and graph["vias"][cell],
                  "OBSERVED_PATH_VIA_EDGE_INVALID")
            transitions += 1
            _need(transitions <= MAX_TRANSITIONS and
                  all_hole_count + transitions <= policy["maximumHoleCount"],
                  "OBSERVED_PATH_VIA_CAPACITY_INVALID")
            x_cell = grid["originX"] + (cell % grid["width"]) * grid["pitch"]
            y_cell = grid["originY"] + (cell // grid["width"]) * grid["pitch"]
            for prior_cell in via_cells:
                old_x = grid["originX"] + (prior_cell % grid["width"]) * grid["pitch"]
                old_y = grid["originY"] + (prior_cell // grid["width"]) * grid["pitch"]
                _need(not _via_spacing_conflict(x_cell, y_cell, old_x, old_y),
                      "OBSERVED_PATH_VIA_SPACING_INVALID")
            via_cells.append(cell)
            _need(cost == pcost + 240, "OBSERVED_PATH_VIA_COST_INVALID")
            prior_direction = None
    last = nodes[-1]
    end_state = last[1] * cells + last[0]
    _need(last[0] == endpoints["end"]["cell"] and
          graph["goals"][end_state] is True,
          "OBSERVED_PATH_END_NOT_CAPTURED_GOAL")
    _need(transitions == last[5] and all_hole_count + transitions <= 48,
          "OBSERVED_PATH_FINAL_CAPACITY_INVALID")
    return {"nodeCount": len(nodes), "endState": end_state,
            "transitions": transitions, "viaCells": list(via_cells),
            "pathRowsSha256": path["rowsSha256"]}


def _validate_capture(value: Any, graph: Mapping[str, Any], target: Mapping[str, Any],
                      grid: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, CAPTURE_KEYS, "CAPTURE_KEYS_INVALID")
    _need(value["status"] == "COMPLETE" and value["taskClass"] == EXPECTED_TASK_CLASS,
          "CAPTURE_STATUS_OR_TASK_CLASS_INVALID")
    for key in ("captureTaskIdentity", "snapshotIdentity", "contextIdentity",
                "ownerIdentity", "searchIdentity", "branchIdentity", "layoutIdentity"):
        _identity(value[key], "CAPTURE_IDENTITY_INVALID")
    for key in ("ownerIdentity", "searchIdentity", "branchIdentity", "layoutIdentity"):
        _need(value[key] == target[key], "CAPTURE_TARGET_IDENTITY_MISMATCH")
    routers = value["routerIdentities"]
    _need(type(routers) is list and len(routers) == 2 and
          all(_integer(item, MIN_SCALAR, MAX_SCALAR) for item in routers),
          "CAPTURE_ROUTER_IDENTITIES_INVALID")
    _need(value["branchOrdinal"] == target["branchOrdinal"] and
          _integer(value["branchOrdinal"], 0, MAX_SCALAR) and
          value["pass"] == target["pass"] and type(value["pass"]) is int and
          value["netId"] == target["netId"] and type(value["netId"]) is str,
          "CAPTURE_BRANCH_BINDING_INVALID")
    bit_bytes = bytes(ord("1") if bit else ord("0") for bit in graph["goals"])
    goal_count = sum(graph["goals"])
    _need(_integer(value["sourceGoalFaceCount"], 0, grid["cells"] * 2) and
          value["sourceGoalFaceCount"] == goal_count and
          _digest(value["sourceGoalBitsSha256"], "CAPTURE_GOAL_BITS_SHA_INVALID") ==
          _sha256(bit_bytes), "CAPTURE_GOAL_ARRAY_BINDING_INVALID")
    flag_names = (
        "branchAtPublisherStage", "sourcePaused", "routerStateUnchanged",
        "routerEffectsUnchanged", "prefixGeometryUnchanged", "goalArrayParity",
        "sourceAttributionComplete", "liveReferencesReleased",
    )
    _need(all(type(value[name]) is bool and value[name] is True for name in flag_names),
          "CAPTURE_COMPLETENESS_FLAGS_INVALID")
    return dict(value)


def _validate_prefix(value: Any, guard_event: Mapping[str, Any],
                     base_event: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, PREFIX_KEYS, "PREFIX_KEYS_INVALID")
    guard_prefix = guard_event["prefix"]
    for side in ("before", "after"):
        row = value[side]
        _exact_keys(row, PREFIX_SNAPSHOT_KEYS, "PREFIX_SNAPSHOT_KEYS_INVALID")
        _identity(row["layoutIdentity"], "PREFIX_LAYOUT_IDENTITY_INVALID")
        for key in ("geometrySha256", "traceRowsSha256", "holeRowsSha256"):
            _digest(row[key], "PREFIX_DIGEST_INVALID")
        _need(_integer(row["traceCount"], 0, MAX_SCALAR) and
              _integer(row["holeCount"], 0, MAX_SCALAR), "PREFIX_COUNTS_INVALID")
    _need(type(value["unchanged"]) is bool and value["unchanged"] is True and
          _same_json(value["before"], value["after"]) and
          _same_json(value["before"], guard_prefix["before"]) and
          _same_json(value["after"], guard_prefix["after"]),
          "PREFIX_CAPTURE_GUARD_BINDING_INVALID")
    _need(value["before"]["traceCount"] == 177 and value["before"]["holeCount"] == 37,
          "PREFIX_FIXTURE_COUNTS_INVALID")
    _need(value["before"]["layoutIdentity"] == base_event["copy"]["layoutIdentity"],
          "PREFIX_COPY_LAYOUT_BINDING_INVALID")
    source_before = base_event["source"]["before"]
    _need(type(source_before) is dict and source_before.get("holes") == 17 and
          source_before.get("traces") == 88 and
          _same_json(source_before, base_event["source"]["after"]),
          "PREFIX_SOURCE_LINEAGE_INVALID")
    return {"before": dict(value["before"]), "after": dict(value["after"]),
            "unchanged": True}


def _validate_group_ledger(ledger: Any, task_groups: int) -> Dict[str, int]:
    _need(type(ledger) is list and len(ledger) == task_groups,
          "GROUP_LEDGER_COUNT_INVALID")
    totals = {name: 0 for name in (
        "internalCalls", "copyEntries", "comparisonEntries", "goalProbes",
        "ordinaryEdgeProbes", "viaProbes")}
    categories = set()
    for row in ledger:
        _exact_keys(row, GROUP_KEYS, "GROUP_LEDGER_ROW_KEYS_INVALID")
        kind = row["kind"]
        _need(kind in ("COPY_OR_COMPARE", "SCAN", "TASK_FINALIZE"),
              "GROUP_LEDGER_KIND_INVALID")
        calls = row["internalCalls"]
        _need(_integer(calls, 1, MAX_SCALAR), "GROUP_LEDGER_CALLS_INVALID")
        for name in ("copyEntries", "comparisonEntries", "goalProbes",
                     "ordinaryEdgeProbes", "viaProbes"):
            _need(_integer(row[name], 0, MAX_SCALAR), "GROUP_LEDGER_DELTA_INVALID")
            totals[name] += row[name]
        _need(_integer(row["elapsedNanos"], 0, MAX_UNIT_NS),
              "GROUP_LEDGER_ELAPSED_INVALID")
        if kind == "COPY_OR_COMPARE":
            bound = MAX_COPY_QUANTUM * calls
            maximum_envelope = MAX_COPY_OBSERVER_ENTRIES
            _need(1 <= calls <= MAX_OBSERVER_CALLS and
                  row["goalProbes"] == row["ordinaryEdgeProbes"] ==
                  row["viaProbes"] == 0 and
                  row["copyEntries"] + row["comparisonEntries"] <= bound and
                  row["conservativeWorkUpperBound"] == bound,
                  "GROUP_COPY_COMPARE_ENVELOPE_INVALID")
        elif kind == "SCAN":
            probes = row["goalProbes"] + row["ordinaryEdgeProbes"] + row["viaProbes"]
            bound = MAX_SCAN_PROBES * calls
            maximum_envelope = MAX_SCAN_OBSERVER_ENTRIES
            _need(1 <= calls <= MAX_OBSERVER_CALLS and
                  row["copyEntries"] == row["comparisonEntries"] == 0 and
                  probes <= bound and row["conservativeWorkUpperBound"] == bound,
                  "GROUP_SCAN_ENVELOPE_INVALID")
        else:
            maximum_envelope = MAX_COPY_QUANTUM
            _need(calls == 1 and row["copyEntries"] == row["comparisonEntries"] ==
                  row["goalProbes"] == row["ordinaryEdgeProbes"] == row["viaProbes"] == 0 and
                  row["conservativeWorkUpperBound"] == MAX_COPY_QUANTUM,
                  "GROUP_FINALIZE_ENVELOPE_INVALID")
        envelope = row["conservativeWorkUpperBound"]
        observed = sum(row[name] for name in (
            "copyEntries", "comparisonEntries", "goalProbes", "ordinaryEdgeProbes", "viaProbes"))
        _need(_integer(envelope, 1, maximum_envelope) and observed <= envelope,
              "GROUP_OBSERVED_WORK_EXCEEDS_ENVELOPE")
        categories.add(kind)
        totals["internalCalls"] += calls
    _need(not any(row["kind"] == "SCAN" and
                  (row["copyEntries"] or row["comparisonEntries"]) for row in ledger),
          "GROUP_COPY_SCAN_MIX_INVALID")
    _need("TASK_FINALIZE" in categories and
          sum(row["kind"] == "TASK_FINALIZE" for row in ledger) == 1 and
          ledger[-1]["kind"] == "TASK_FINALIZE", "GROUP_FINALIZER_OMITTED_OR_ORDER_INVALID")
    return totals


def _validate_work(value: Any, base_event: Mapping[str, Any],
                   guard_event: Mapping[str, Any], graph_integrity: Mapping[str, Any],
                   source_census: Mapping[str, Any], grid: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, WORK_KEYS, "WORK_KEYS_INVALID")
    _need(value["budgetPhase"] == "orderingRemainder", "WORK_BUDGET_PHASE_INVALID")
    scalar_ranges = {
        "graphCaptureGroups": (1, MAX_WORK_UNITS),
        "graphTaskGroups": (1, MAX_WORK_UNITS),
        "graphMetadataGroups": (1, MAX_WORK_UNITS),
        "captureInternalUnits": (1, MAX_INTERNAL_UNITS),
        "copyEntries": (0, MAX_JAVA_LONG),
        "comparisonEntries": (0, MAX_JAVA_LONG),
        "goalProbes": (0, MAX_JAVA_LONG),
        "ordinaryEdgeProbes": (0, MAX_JAVA_LONG),
        "viaProbes": (0, MAX_JAVA_LONG),
        "captureActiveElapsedMillis": (0, MAX_OPERATION_MS),
        "continuationOwnerSteps": (0, MAX_WORK_UNITS * MAX_OWNER_STEPS_PER_UNIT),
        "graphCanonicalBytes": (1, MAX_GRAPH_BYTES),
        "sourceRowsCanonicalBytes": (1, MAX_GRAPH_BYTES),
    }
    for name, (minimum, maximum) in scalar_ranges.items():
        _need(_integer(value[name], minimum, maximum), "WORK_SCALAR_INVALID")
    _need(value["graphCaptureGroups"] ==
          value["graphTaskGroups"] + value["graphMetadataGroups"],
          "WORK_GRAPH_GROUP_TOTAL_INVALID")
    _validate_metadata_envelope(
        value["graphCanonicalBytes"], value["sourceRowsCanonicalBytes"],
        source_census["rowCounts"]["goalStates"], grid["cells"],
        value["graphMetadataGroups"])
    ledger = _validate_group_ledger(value["groupLedger"], value["graphTaskGroups"])
    for name in ("copyEntries", "comparisonEntries", "goalProbes",
                 "ordinaryEdgeProbes", "viaProbes"):
        _need(ledger[name] == value[name], "WORK_LEDGER_COUNTER_MISMATCH")
    _need(ledger["internalCalls"] == value["captureInternalUnits"] and
          value["captureInternalUnits"] <= 4 * value["graphTaskGroups"] and
          value["captureInternalUnits"] <= MAX_INTERNAL_UNITS,
          "WORK_INTERNAL_UNIT_LIMIT")
    base_work = base_event["work"]
    pre = value["preReport"]
    _exact_keys(pre, PRE_REPORT_KEYS, "WORK_PRE_REPORT_KEYS_INVALID")
    _need(_integer(pre["controllerUnits"], 1, MAX_WORK_UNITS) and
          _integer(pre["maximumUnitElapsedMs"], 0, MAX_UNIT_MS) and
          _integer(pre["operationElapsedMs"], 1, MAX_OPERATION_MS) and
          pre["controllerUnits"] == base_work["controllerUnits"] and
          pre["maximumUnitElapsedMs"] == base_work["maxUnitElapsedMs"] and
          pre["operationElapsedMs"] == base_work["operationElapsedMs"],
          "WORK_PRE_REPORT_BASE_BINDING_INVALID")
    _need(value["graphCaptureGroups"] <= pre["controllerUnits"] and
          value["continuationOwnerSteps"] == guard_event["work"]["continuationOwnerSteps"] and
          value["continuationOwnerSteps"] <= pre["controllerUnits"] * MAX_OWNER_STEPS_PER_UNIT,
          "WORK_BASE_OR_GUARD_BINDING_INVALID")
    _need(value["graphCanonicalBytes"] == graph_integrity["canonicalBytes"] and
          value["sourceRowsCanonicalBytes"] == source_census["sourceRowsCanonicalBytes"],
          "WORK_CANONICAL_BYTES_MISMATCH")
    report = value["reportPreparation"]
    _exact_keys(report, REPORT_PREPARATION_KEYS, "WORK_REPORT_PREPARATION_KEYS_INVALID")
    _need(report["unitName"] == EXPECTED_UNIT_NAME and
          _integer(report["controllerUnits"], 1, 1) and
          _integer(report["elapsedNanos"], 0, MAX_UNIT_NS) and
          _integer(report["ownerSteps"], 0, 0), "WORK_REPORT_PREPARATION_INVALID")
    final = value["final"]
    _exact_keys(final, FINAL_KEYS, "WORK_FINAL_KEYS_INVALID")
    _need(_integer(final["controllerUnits"], 1, MAX_WORK_UNITS) and
          final["controllerUnits"] == pre["controllerUnits"] + 1 and
          _integer(final["maximumUnitElapsedNanos"], 0, MAX_UNIT_NS) and
          _integer(final["operationElapsedNanos"], 1, MAX_OPERATION_NS) and
          _integer(final["continuationOwnerSteps"], 0,
                   MAX_WORK_UNITS * MAX_OWNER_STEPS_PER_UNIT) and
          final["continuationOwnerSteps"] == value["continuationOwnerSteps"] and
          final["operationElapsedNanos"] >= guard_event["work"]["operationElapsedNanos"] and
          final["operationElapsedNanos"] >= report["elapsedNanos"] and
          final["operationElapsedNanos"] // 1_000_000 >= pre["operationElapsedMs"] and
          value["captureActiveElapsedMillis"] <=
          final["operationElapsedNanos"] // 1_000_000,
          "WORK_FINAL_VALUES_INVALID")
    _check_final_controller_units(pre["controllerUnits"], final["controllerUnits"])
    _need(final["maximumUnitElapsedNanos"] >= report["elapsedNanos"] and
          final["maximumUnitElapsedNanos"] >= pre["maximumUnitElapsedMs"] * 1_000_000 and
          all(final["maximumUnitElapsedNanos"] >= row["elapsedNanos"]
              for row in value["groupLedger"]), "WORK_MAXIMUM_UNIT_MISMATCH")
    return dict(value)


def _validate_graph_event(value: Any, graph_payload_raw: bytes,
                          guard_event: Any, base_event: Any,
                          base_payload_raw: bytes, guard_payload_raw: bytes,
                          base_payload_sha256: str, guard_payload_sha256: str,
                          guard: types.ModuleType, helper: types.ModuleType
                          ) -> Dict[str, Any]:
    _exact_keys(value, EVENT_KEYS, "GRAPH_EVENT_KEYS_INVALID")
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 1 and
          value["schema"] == EVENT_SCHEMA and value["status"] == "PASS" and
          type(value["runId"]) is str and RUN_ID_RE.fullmatch(value["runId"]) is not None and
          value["operationId"] == "QUALITY_PROBE" and value["scope"] == SCOPE,
          "GRAPH_EVENT_IDENTITY_INVALID")
    _bounded_bytes(graph_payload_raw, MAX_GRAPH_PAYLOAD_BYTES, "GRAPH_EVENT_PAYLOAD_INVALID")
    _exact_keys(value["bindings"], BINDING_KEYS, "GRAPH_BINDINGS_KEYS_INVALID")
    _need(_sha256(base_payload_raw) == base_payload_sha256 and
          _sha256(guard_payload_raw) == guard_payload_sha256,
          "GRAPH_EVENT_INPUT_DIGEST_MISMATCH")
    for name, raw_event, raw_payload in (("base", base_event, base_payload_raw),
                                         ("guard", guard_event, guard_payload_raw)):
        binding = value["bindings"][name]
        _exact_keys(binding, BINDING_ROW_KEYS, "GRAPH_BINDING_ROW_KEYS_INVALID")
        _need(binding["runId"] == raw_event["runId"] and
              binding["eventSha256"] == _sha256(raw_payload),
              "GRAPH_BINDING_ROW_MISMATCH")
    checked_guard = guard._validate_witness(guard_event, base_event, base_payload_raw, helper)
    _need(value["runId"] == base_event["runId"] == guard_event["runId"],
          "GRAPH_RUN_ID_MISMATCH")
    for name in ("inputs", "lineage", "target", "grid", "endpoints"):
        _need(_same_json(value[name], guard_event[name]),
              "GRAPH_GUARD_" + name.upper() + "_MISMATCH")
    _need(_same_json(value["inputs"], EXPECTED_INPUTS), "GRAPH_INPUTS_FIXTURE_INVALID")
    grid = _validate_grid(value["grid"])
    _need(_same_json(grid, checked_guard["grid"]), "GRAPH_GRID_GUARD_MISMATCH")
    target = value["target"]
    endpoints = value["endpoints"]
    _need(target["ordering"] == 2 and target["pass"] == 1 and
          target["netId"] == "A_DRIVE" and target["netVectorPosition"] == 24 and
          target["searchNetIndex"] == 25 and target["branchOrdinal"] == 142 and
          target["searchBranches"] == 142 and
          grid["width"] == 478 and grid["height"] == 309 and
          grid["cells"] == 147_702 and grid["originX"] == 30 and
          grid["originY"] == 30 and grid["pitch"] == 10,
          "GRAPH_CANDIDATE_FIXTURE_MISMATCH")
    _need(endpoints["start"]["padId"] == "RPDA.1" and
          endpoints["start"]["x"] == 3_790 and endpoints["start"]["y"] == 2_560 and
          endpoints["root"]["padId"] == "QA.B" and
          endpoints["root"]["x"] == 3_830 and endpoints["root"]["y"] == 2_410 and
          endpoints["end"]["x"] == 3_830 and endpoints["end"]["y"] == 2_410 and
          endpoints["end"]["cell"] == endpoints["root"]["cell"] and
          endpoints["foundTerminal"] is not None and
          endpoints["foundTerminal"]["padId"] == "QA.B" and
          endpoints["foundTerminal"]["identity"] == endpoints["root"]["identity"],
          "GRAPH_ENDPOINT_FIXTURE_MISMATCH")
    _need(guard_event["path"]["nodeCount"] == 82 and
          guard_event["path"]["rowsSha256"] == OBSERVED_PATH_SHA256 and
          guard_event["checkpoint"]["direct"] == 190 and
          guard_event["checkpoint"]["storedLength"] == 770 and
          guard_event["checkpoint"]["storedBends"] == 5 and
          guard_event["checkpoint"]["transitions"] == 4 and
          guard_event["checkpoint"]["lengthOverThreeDirect"] is True and
          guard_event["checkpoint"]["directZero"] is False and
          guard_event["checkpoint"]["bendsOver16"] is False,
          "GRAPH_OBSERVED_GUARD_FIXTURE_MISMATCH")
    prefix = _validate_prefix(value["prefix"], guard_event, base_event)
    _need(type(value["cleanup"]) is dict and
          _same_json(value["cleanup"], guard_event["cleanup"]),
          "GRAPH_CLEANUP_GUARD_MISMATCH")
    _exact_keys(value["cleanup"], CLEANUP_KEYS, "GRAPH_CLEANUP_KEYS_INVALID")
    _need(value["cleanup"]["status"] == "PASS" and
          _integer(value["cleanup"]["elapsedMs"], 0, MAX_CLEANUP_MS) and
          value["cleanup"]["limitMs"] == MAX_CLEANUP_MS,
          "GRAPH_CLEANUP_VALUES_INVALID")
    _need(_same_json(value["claims"], guard_event["qualification"]),
          "GRAPH_CLAIMS_GUARD_MISMATCH")
    _exact_keys(value["claims"], CLAIM_KEYS, "GRAPH_CLAIMS_KEYS_INVALID")
    _need(all(type(item) is bool and item is False for item in value["claims"].values()),
          "GRAPH_CAPTURE_CLAIM_INVALID")

    graph, graph_canonical = _validate_graph(value["graph"], value["graphIntegrity"],
                                             grid, endpoints["start"]["cell"])
    _need(len(graph_payload_raw) <= MAX_GRAPH_PAYLOAD_BYTES,
          "GRAPH_EVENT_WHOLE_PAYLOAD_LIMIT")
    source_census = _validate_source_census(value["sourceCensus"], graph, grid,
                                            endpoints, target, prefix)
    policy = _validate_policy(value["policy"], source_census["rowCounts"])
    capture = _validate_capture(value["capture"], graph, target, grid)
    path_check = _verify_observed_path(graph, guard_event["path"], grid, endpoints,
                                       policy, source_census["rowCounts"]["allHoleCount"])
    work = _validate_work(value["work"], base_event, guard_event,
                          value["graphIntegrity"], value["sourceCensus"], grid)
    return {
        "schema": EVENT_SCHEMA,
        "runId": value["runId"],
        "bindings": value["bindings"],
        "target": target,
        "grid": grid,
        "endpoints": endpoints,
        "capture": capture,
        "prefix": prefix,
        "graph": {
            "protocol": graph["protocol"], "model": graph["model"],
            "caseId": graph["caseId"], "width": graph["width"],
            "height": graph["height"], "primary": graph["primary"],
            "goalStates": sum(graph["goals"]),
            "sourceGoalBitsSha256": capture["sourceGoalBitsSha256"],
        },
        "graphIntegrity": dict(value["graphIntegrity"]),
        "sourceCensus": {
            "rowCounts": source_census["rowCounts"],
            "sourceRowsCanonicalBytes": source_census["sourceRowsCanonicalBytes"],
            "sourceRowsSha256": source_census["sourceRowsSha256"],
        },
        "policy": policy,
        "pathVerification": path_check,
        "work": work,
        "cleanup": dict(value["cleanup"]),
        "claims": dict(value["claims"]),
        "_canonicalGraphBytes": graph_canonical,
        "_sourceRowsCanonicalBytes": source_census["canonicalBytes"],
    }


def _validate_native_artifacts(spec_raw: bytes, spec_path: Path, spec_sha256: str,
                               spec: Mapping[str, Any], context: Mapping[str, Any],
                               own_raw: bytes, guard: types.ModuleType,
                               guard_raw: bytes, guard_path: Path,
                               helper: types.ModuleType, helper_raw: bytes,
                               helper_path: Path) -> Dict[str, Any]:
    artifacts = context["artifacts"]
    raw_by_name = {
        "result": _read_bounded(artifacts["resultPath"], MAX_RESULT_BYTES,
                                "RUN_RESULT", helper),
        "stdout": _read_bounded(artifacts["stdoutPath"], MAX_CAPTURE_BYTES,
                                "RUN_STDOUT", helper),
        "receipt": _read_bounded(artifacts["receiptPath"], MAX_CAPTURE_BYTES,
                                 "RUN_RECEIPT", helper),
        "stderr": _read_bounded(artifacts["stderrPath"], MAX_CAPTURE_BYTES,
                                "RUN_STDERR", helper, allow_empty=True),
    }
    run_value = _strict_json(raw_by_name["result"], "NATIVE_RUN", helper)
    run_context = dict(context)
    run_context.update({"stdoutRaw": raw_by_name["stdout"],
                        "receiptRaw": raw_by_name["receipt"],
                        "stderrRaw": raw_by_name["stderr"]})
    native_run = _validate_native_run(run_value, run_context)
    (_console, _receipt, base_event, base_payload, base_payload_sha256,
     guard_event, guard_payload, guard_payload_sha256, graph_event,
     graph_payload) = _validate_console_and_receipt(
         raw_by_name["stdout"], raw_by_name["receipt"], raw_by_name["stderr"], helper)
    checked = _validate_graph_event(graph_event, graph_payload, guard_event, base_event,
                                    base_payload, guard_payload, base_payload_sha256,
                                    guard_payload_sha256, guard, helper)
    _stable_source_check(spec_raw, spec_path, spec_sha256, spec, context, raw_by_name,
                         own_raw, guard_raw, guard_path, helper_raw, helper_path,
                         guard, helper)
    return {
        "schema": RESULT_SCHEMA,
        "status": "VALIDATED_BOUNDED_ADRIVE_GRAPH_CAPTURE",
        "target": TARGET,
        "purpose": PURPOSE,
        "source": {
            "snapshotRevision": context["sourceSnapshotRevision"],
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
        },
        "nativeRun": {
            "schema": native_run["schema"], "suite": native_run["suite"],
            "command": native_run["command"], "layoutVersion": native_run["layoutVersion"],
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
        "experiment": {key: value for key, value in checked.items()
                        if not key.startswith("_")},
        "limitations": {
            "routeAccepted": False, "geometryPublished": False,
            "routeAdoption": False, "layoutQualification": False,
            "playable100": False,
        },
        "readerSourceSha256": _sha256(own_raw),
        "guardReaderSha256": GUARD_READER_SHA256,
        "baseReaderSha256": BASE_READER_SHA256,
    }


def _stable_source_check(spec_raw: bytes, spec_path: Path, spec_sha256: str,
                         spec: Mapping[str, Any], context: Mapping[str, Any],
                         raw_by_name: Mapping[str, bytes], own_raw: bytes,
                         guard_raw: bytes, guard_path: Path, helper_raw: bytes,
                         helper_path: Path, guard: types.ModuleType,
                         helper: types.ModuleType) -> None:
    spec_again = _read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC_RECHECK", helper)
    _need(spec_again == spec_raw and _sha256(spec_again) == spec_sha256,
          "INPUT_SPEC_CHANGED_DURING_VALIDATION")
    source_root = _resolve_path(spec["sourceRoot"], spec_path.parent,
                                "SOURCE_ROOT_RECHECK", helper)
    pins_path = _resolve_path(spec["pinsPath"], spec_path.parent,
                              "SOURCE_PINS_RECHECK", helper)
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS_RECHECK", helper)
    _need(source_root == context["sourceRoot"] and pins_path == context["pinsPath"] and
          pins_raw == context["pinsRaw"], "SOURCE_INPUT_MANIFEST_CHANGED")
    try:
        second = helper._audit_inputs_manifest(pins_raw, spec, source_root)
    except getattr(helper, "Load12RemainderInputError", ()):  # type: ignore[arg-type]
        _fail("SOURCE_INPUTS_CHANGED_DURING_VALIDATION")
    _need(second["pinMap"] == context["pinMap"] and
          second["inputCount"] == context["inputCount"] and
          second["inputBytes"] == context["inputBytes"] and
          second["pinsSha256"] == context["pinsSha256"],
          "SOURCE_INPUTS_CHANGED_DURING_VALIDATION")
    for name, raw in raw_by_name.items():
        path = context["artifacts"]["resultPath" if name == "result" else name + "Path"]
        again = _read_bounded(path,
                              MAX_RESULT_BYTES if name == "result" else MAX_CAPTURE_BYTES,
                              "RUN_" + name.upper() + "_RECHECK", helper,
                              allow_empty=(name == "stderr"))
        _need(again == raw, "RUN_" + name.upper() + "_CHANGED_DURING_VALIDATION")
    own_after = _bootstrap_read(Path(os.path.abspath(__file__)), MAX_RESULT_BYTES,
                                "READER_SOURCE_RECHECK")
    guard_after = _bootstrap_read(guard_path, MAX_RESULT_BYTES, "GUARD_READER_RECHECK")
    helper_after = _bootstrap_read(helper_path, MAX_RESULT_BYTES, "BASE_READER_RECHECK")
    _need(own_after == own_raw and guard_after == guard_raw and
          _sha256(guard_after) == GUARD_READER_SHA256 and helper_after == helper_raw and
          _sha256(helper_after) == BASE_READER_SHA256,
          "PINNED_READER_SOURCE_CHANGED_DURING_VALIDATION")
    guard._bind_selected_source_root(source_root, helper)


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    guard, helper, guard_raw, guard_path, helper_raw, helper_path = _load_guard_reader()
    own_path = Path(os.path.abspath(__file__))
    own_raw = _bootstrap_read(own_path, MAX_RESULT_BYTES, "READER_SOURCE")
    spec_path = _resolve_path(spec_argument, Path.cwd(), "INPUT_SPEC", helper)
    spec_raw = _read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC", helper)
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _strict_json(spec_raw, "INPUT_SPEC", helper)
    spec, context = _validate_input_spec(spec, spec_path.parent, guard, helper)
    return _validate_native_artifacts(spec_raw, spec_path, spec_sha256, spec, context,
                                     own_raw, guard, guard_raw, guard_path,
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
        spec_path, spec_sha256 = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        result = run_validation(spec_path, spec_sha256)
        sys.stdout.buffer.write(_canonical_json(result) + b"\n")
        sys.stdout.buffer.flush()
        return 0
    except ADriveGraphInputError as error:
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
