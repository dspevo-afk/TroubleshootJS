#!/usr/bin/env python3
"""Independently validate one source-bound actual LOAD12 cap17 replay.

This reader proves a bounded branch path and its normal publication against a
separately validated cap48 graphspec. It does not establish all-path
infeasibility, route adoption, layout qualification, or a playable board.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import types
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-load12-detached-cap17-input-v1"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
RESULT_SCHEMA = "p07-load12-detached-cap17-reader-result-v1"
TARGET = "LOAD12"
PURPOSE = "ACTUAL_CAP17"
SUITE = "Rb100Load12DetachedBaselineContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 20
SOURCE_INPUT_COUNT = 1079
CONTRACT_JAVA_COUNT = 223
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"

ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
SOURCE_GRAPH_INPUT_SPEC_SHA256 = (
    "20c469e804d64a7740c0a9d79066841045bb672c6d2fbc26099b89c1a52f7298")
SOURCE_GRAPH_PINS_SHA256 = (
    "0516a02dc18a221f0f71a50720e8c84004770f39cc588306cd463e472b72a82f")
SOURCE_GRAPH_PATH_SHA256 = (
    "1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d")
SOURCE_GOAL_SHA256 = (
    "cc48299ed82e39f1bac10269e7c41afc6468f1e53fc96baa36a95d5c60d548d2")
SOURCE_PREFIX_GEOMETRY_SHA256 = (
    "7c963bb9ba10f7419d2e6abaecff4cfc1504ba2744914d6874ffd02ca475c447")
CAP17_PATH_SCOPE = "ONE_ACTUAL_LOAD12_BRANCH_DETACHED_CAP17_REPLAY"
CAP17_EVENT_SCHEMA = "rb100-load12-detached-cap17-r2"
CAP17_EVENT_MARKER = "RB100_LOAD12_DETACHED_CAP17_EVENT"
CAP17_SUMMARY_PREFIX = (
    "PASS: RB100 LOAD12 detached cap17 contracts assertions=")
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

# The current reader SHA is frozen only after the root reviewed its narrow
# default-48/expected-cap helper change.
QUALITY_READER_SHA256 = (
    "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc")
GRAPH_SPEC_EXPORTER_SHA256 = (
    "4475aaded5c56c79ab236ba58fdefa1ca89926e08df67a151aacdd7a0b52e112")

MAX_WORK_UNITS = 640
MAX_WORK_QUANTUM = 32_768
MAX_TOTAL_WORK = MAX_WORK_UNITS * MAX_WORK_QUANTUM
MAX_ACTIVE_MS = 90_000
MAX_CLEANUP_MS = 120_000
MAX_UNIT_MS = 5_000
MAX_NATIVE_LIMIT_MS = 480_000
MAX_OUTER_LIMIT_MS = 1_800_000
MAX_VIAS = 48
CAP17 = 17
MAX_GRID_CELLS = 250_000
MAX_SCALAR = 2_147_483_647
MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_GRAPH_SPEC_BYTES = 16 * 1024 * 1024
MAX_READER_BYTES = 2 * 1024 * 1024
MAX_PATH_CHARS = 4096

RUN_ID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII)
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
PROCESS_TICKS_RE = re.compile(r"[1-9][0-9]{0,19}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
SUMMARY_RE = re.compile(
    re.escape(CAP17_SUMMARY_PREFIX) + r"([1-9][0-9]{0,9})\Z", re.ASCII)
HEX_RE = re.compile(r"[0-9a-f]+\Z", re.ASCII)
DECIMAL_LONG_RE = re.compile(r"-?(?:0|[1-9][0-9]*)\Z", re.ASCII)

INPUT_SPEC_KEYS = frozenset((
    "schema target purpose suite sourceRoot pinsPath pinsSha256 inputCount inputBytes "
    "sourceSnapshotRevision resultPath stdoutPath receiptPath stderrPath "
    "graphspecPath graphspecSha256 sourceGraphInputSpecPath "
    "sourceGraphInputSpecSha256").split())
LEGACY_INPUT_SPEC_KEYS = INPUT_SPEC_KEYS - frozenset((
    "target", "purpose", "suite", "graphspecPath", "graphspecSha256",
    "sourceGraphInputSpecPath", "sourceGraphInputSpecSha256"))
REQUIRED_NATIVE_INPUTS = frozenset({
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
    "tests/contracts/Rb100Load12DetachedBaselineContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/contracts/P07AcceptedBranchCaptureDiagnostics.java",
    "tests/contracts/P07AcceptedBranchCaptureContractTest.java",
    "tests/contracts/P07DetachedBranchReplayContractTest.java",
    "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
})

EVENT_KEYS = frozenset((
    "schema status protocolVersion runId operationId scope source selector clone "
    "original limits work comparison branchRouteFound fullBoardFeasibility "
    "routeAdoption layoutQualification playable100").split())
SELECTOR_KEYS = frozenset((
    "rootSeed placementSeed routingSeed routeRequestIndex selectedPlacementIndex "
    "selectedPlacementAttempt eligiblePlacementAttempts placementFingerprintSha256 "
    "pass netPosition nextFetch branchOrdinal searchBranches netId primaryLayer "
    "startPadId rootPadId grid prefix").split())
GRID_KEYS = frozenset("width height cells minX minY pitch".split())
PREFIX_KEYS = frozenset("vias traces".split())
SOURCE_KEYS = frozenset((
    "sessionMaximumVias searchMaximumVias initial afterReplayBeforeResume").split())
CLONE_KEYS = frozenset((
    "requestedMaximumVias sessionMaximumVias searchMaximumVias initial path "
    "preResult postResult normalPublisher").split())
INITIAL_KEYS = frozenset((
    "seeds netOrder netOrderSha256 prefixRows prefixRowsSha256 "
    "preGeometrySha256 prefix").split())
PREFIX_SUMMARY_KEYS = frozenset("holeCount viaCount traceCount".split())
INITIAL_PREFIX_ROW_KEYS = frozenset("id fingerprint".split())
PATH_SUMMARY_KEYS = frozenset((
    "pathTupleSha256 goalFaceBitsSha256 nodeCount planarLength bends flips").split())
ORIGINAL_KEYS = frozenset((
    "path pre post preGeometrySha256 postGeometrySha256 publicationSha256 "
    "preResult postResult").split())
ORIGINAL_PRE_KEYS = frozenset("holeCount viaCount traceCount".split())
ORIGINAL_POST_KEYS = frozenset((
    "holeCount viaCount traceCount createdViaCount createdTraceCount").split())
AFTER_REPLAY_KEYS = frozenset((
    "sessionMaximumVias searchMaximumVias branchExpanded holeCount traceCount seeds"
).split())
LIMIT_KEYS = frozenset((
    "operationMs workUnits unitMs cleanupMs quantum maximumSourceVias "
    "maximumDetachedVias maximumTransitions").split())
COMPARISON_KEYS = frozenset((
    "initialSeedsEqual initialNetOrderEqual initialPrefixRowsEqual "
    "initialPrefixGeometryEqual fullGoalFaceBitsEqual pathTupleEqualObserved "
    "postGeometryEqualObserved publicationEqualObserved prefixRetained "
    "zeroNewVias sourcePreserved sourceResumed").split())
WORK_KEYS = frozenset(
    "sourceOperation copy replay cloneCursor sourceCursor sourceOutcome".split())
SOURCE_OPERATION_KEYS = frozenset((
    "operationMs workUnits maxUnitMs cleanupMs manualGroups manualSliceSteps").split())
COPY_WORK_KEYS = frozenset(
    "advanceCalls workSteps copiedEntries lastWork observerChecks".split())
REPLAY_WORK_KEYS = frozenset(
    "toPreCalls toPreWork toPostCalls toPostWork".split())
CURSOR_WORK_KEYS = frozenset(
    "preAdvanceCalls preWork postAdvanceCalls postWork".split())
SOURCE_OUTCOME_KEYS = frozenset((
    "phase qualityRejected targetRequestAccounted sourceResumed").split())
PUBLISHER_KEYS = frozenset((
    "completed stage replayComplete preHoleCount preViaCount preTraceCount "
    "postHoleCount postViaCount postTraceCount createdHoleCount createdViaCount "
    "createdTraceCount").split())


class Load12Cap17InputError(ValueError):
    """A sanitized failure code; input paths and contents are not echoed."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise Load12Cap17InputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _safe_foreign_code(error: BaseException, fallback: str) -> str:
    code = getattr(error, "code", None)
    return code if type(code) is str and ERROR_CODE_RE.fullmatch(code) else fallback


def _is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, "is_junction", None)
        if callable(is_junction) and is_junction():
            return True
        return bool(getattr(path.lstat(), "st_file_attributes", 0) & 0x400)
    except FileNotFoundError:
        return False
    except (OSError, RuntimeError, ValueError):
        return True


def _has_reparse_component(path: Path) -> bool:
    try:
        absolute = Path(os.path.abspath(os.fspath(path)))
        current = Path(absolute.anchor)
        for part in absolute.parts[1:]:
            current = current / part
            if _is_reparse(current):
                return True
        return False
    except (OSError, RuntimeError, TypeError, ValueError):
        return True


def _read_local(path: Path, limit: int, code: str,
                allow_empty: bool = False) -> bytes:
    try:
        _need(not _has_reparse_component(path) and not _is_reparse(path),
              code + "_REPARSE_PATH")
        info = path.stat()
        _need(path.is_file(), code + "_NOT_FILE")
        _need((0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit,
              code + "_READ_SIZE_CHANGED")
        return raw
    except Load12Cap17InputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _exec_private(raw: bytes, path: Path, name: str,
                  fallback: str) -> types.ModuleType:
    module = types.ModuleType(name)
    module.__file__ = os.fspath(path)
    module.__package__ = ""
    try:
        source = raw.decode("utf-8", errors="strict")
        exec(compile(source, os.fspath(path), "exec", dont_inherit=True),
             module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail(fallback)
    return module


def _load_helpers() -> Tuple[types.ModuleType, types.ModuleType,
                             types.ModuleType, types.ModuleType,
                             types.ModuleType, Dict[str, str]]:
    directory = Path(os.path.abspath(__file__)).parent
    quality_path = directory / "read_quality_zero_via.py"
    quality_raw = _read_local(quality_path, MAX_READER_BYTES, "QUALITY_READER")
    _need(_sha256(quality_raw) == QUALITY_READER_SHA256,
          "QUALITY_READER_HASH_MISMATCH")
    quality = _exec_private(
        quality_raw, quality_path, "_p07_load12_cap17_quality_reader",
        "QUALITY_READER_SOURCE_INVALID")
    try:
        frozen, scalar, ledger, capture, hashes = quality._load_pinned_helpers()
    except quality.QualityZeroViaInputError as error:
        _fail(_safe_foreign_code(error, "QUALITY_HELPERS_INVALID"))

    # Reuse only the pinned source census and scalar validators. Their native
    # metadata is set for this exact cap17 spec; the legacy consumer remains
    # bound to its original zero-argument mode.
    ledger.INPUT_SCHEMA = INPUT_SCHEMA
    ledger.SOURCE_REVISION = SOURCE_REVISION
    ledger.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    ledger.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    scalar.RUN_SCHEMA = RUN_SCHEMA
    scalar.SOURCE_REVISION = SOURCE_REVISION
    scalar.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    scalar.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    scalar.SUITE = SUITE
    hashes["cap17QualityReaderSha256"] = QUALITY_READER_SHA256
    return quality, frozen, scalar, ledger, capture, hashes


def _load_graphspec_exporter(directory: Path
                             ) -> Tuple[types.ModuleType, bytes, Path]:
    path = directory / "export_load12_graphspec.py"
    raw = _read_local(path, MAX_READER_BYTES, "GRAPH_SPEC_EXPORTER")
    _need(_sha256(raw) == GRAPH_SPEC_EXPORTER_SHA256,
          "GRAPH_SPEC_EXPORTER_HASH_MISMATCH")
    module = _exec_private(
        raw, path, "_p07_load12_cap17_graphspec_exporter",
        "GRAPH_SPEC_EXPORTER_SOURCE_INVALID")
    _need(callable(getattr(module, "build_verified_graphspec", None)),
          "GRAPH_SPEC_EXPORTER_API_INVALID")
    return module, raw, path


def _ledger_call(ledger: types.ModuleType, function, fallback: str,
                 *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except ledger.LedgerInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _scalar_call(scalar: types.ModuleType, function, fallback: str,
                 *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except scalar.QualityScalarInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _quality_call(quality: types.ModuleType, function, fallback: str,
                  *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except quality.QualityZeroViaInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _validate_input_spec_identity(spec: Any) -> Dict[str, Any]:
    _exact_keys(spec, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    _need(spec["schema"] == INPUT_SCHEMA and spec["target"] == TARGET and
          spec["purpose"] == PURPOSE and spec["suite"] == SUITE and
          type(spec["sourceSnapshotRevision"]) is int and
          spec["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(spec["inputCount"]) is int and
          spec["inputCount"] == SOURCE_INPUT_COUNT and
          _integer(spec["inputBytes"], 1, 512 * 1024 * 1024) and
          type(spec["pinsSha256"]) is str and
          SHA256_RE.fullmatch(spec["pinsSha256"]) is not None and
          type(spec["graphspecSha256"]) is str and
          SHA256_RE.fullmatch(spec["graphspecSha256"]) is not None and
          type(spec["sourceGraphInputSpecSha256"]) is str and
          spec["sourceGraphInputSpecSha256"] == SOURCE_GRAPH_INPUT_SPEC_SHA256,
          "INPUT_SPEC_IDENTITY_INVALID")
    return {key: spec[key] for key in LEGACY_INPUT_SPEC_KEYS}


def _validate_input_spec(spec: Any, spec_directory: Path,
                         ledger: types.ModuleType,
                         capture: types.ModuleType
                         ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    legacy = _validate_input_spec_identity(spec)
    context = _ledger_call(
        ledger, ledger._validate_input_spec, "INPUT_SPEC_INVALID",
        legacy, spec_directory, capture)
    _need(REQUIRED_NATIVE_INPUTS <= set(context["pinMap"]),
          "CAP17_SOURCE_PINS_OMIT_REQUIRED_INPUT")
    return legacy, context


def _validate_native_run(value: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes,
                         stderr_raw: bytes,
                         scalar: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, set(scalar.NATIVE_RUN_KEYS) | {"target"},
                "NATIVE_RUN_KEYS_INVALID")
    _need(type(value["target"]) is str and value["target"] == TARGET and
          value["suite"] == SUITE,
          "NATIVE_RUN_TARGET_OR_SUITE_INVALID")
    expected_command = {
        "entry": ENTRY_SCRIPT,
        "suite": SUITE,
        "suites": [SUITE],
        "args": ["-Rb100Load12Cap17Replay"],
        "environment": {},
    }
    _need(value["command"] == expected_command,
          "NATIVE_RUN_CAP17_COMMAND_INVALID")

    # After the exact cap17 command has been checked above, create a private
    # metadata-only view for the pinned v5 validator, whose shared envelope
    # contract is defined for zero-argument commands. The actual result and
    # reported command remain the original cap17 record.
    shared_view = {key: item for key, item in value.items() if key != "target"}
    shared_command = dict(value["command"])
    shared_command["args"] = []
    shared_view["command"] = shared_command
    _scalar_call(scalar, scalar._validate_native_run, "NATIVE_RUN_INVALID",
                 shared_view, context, stdout_raw, receipt_raw, stderr_raw)
    return dict(value)


def _validate_cap_pair(source: Any, clone: Any) -> None:
    _exact_keys(source, SOURCE_KEYS, "CAP17_SOURCE_KEYS_INVALID")
    _exact_keys(clone, CLONE_KEYS, "CAP17_CLONE_KEYS_INVALID")
    _need(_integer(source["sessionMaximumVias"], 48, 48) and
          _integer(source["searchMaximumVias"], 48, 48) and
          _integer(clone["requestedMaximumVias"], CAP17, CAP17) and
          _integer(clone["sessionMaximumVias"], CAP17, CAP17) and
          _integer(clone["searchMaximumVias"], CAP17, CAP17),
          "CAP17_SOURCE_OR_CLONE_CAP_INVALID")


def _decode_lines(raw: bytes, code: str, ledger: types.ModuleType) -> List[str]:
    return _ledger_call(ledger, ledger._decode_lines, code, raw, code)


def _parse_tagged(line: str, marker: str, code: str,
                  ledger: types.ModuleType) -> Any:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix),
          code + "_PREFIX_INVALID")
    try:
        raw = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail(code + "_ENCODING_INVALID")
    _need(0 < len(raw) <= MAX_GRAPH_SPEC_BYTES,
          code + "_SIZE_INVALID")
    return _ledger_call(ledger, ledger._strict_json, code + "_JSON_INVALID",
                        raw, code)


def _validate_scalar_prefix(lines: Sequence[str],
                            ledger: types.ModuleType,
                            scalar: types.ModuleType) -> Dict[str, Any]:
    markers = tuple(scalar.RECEIPT_MARKERS[:7])
    _need(type(lines) in (list, tuple) and len(lines) == len(markers),
          "SCALAR_PREFIX_COUNT_INVALID")
    records = [_parse_tagged(line, marker, "SCALAR_PREFIX_RECORD", ledger)
               for line, marker in zip(lines, markers)]
    controls = _scalar_call(scalar, scalar._validate_controls,
                            "SCALAR_CONTROLS_INVALID", records[0])
    run_id = controls
    _scalar_call(scalar, scalar._validate_subset, "SCALAR_NULL_SUBSET_INVALID",
                 records[1], run_id)
    null = _scalar_call(
        scalar, scalar._validate_operation, "SCALAR_NULL_OPERATION_INVALID",
        records[2], run_id, scalar.NULL_OPERATION_ID, "NULL")
    _scalar_call(scalar, scalar._validate_subset, "SCALAR_PROBE_SUBSET_INVALID",
                 records[3], run_id)
    probe = _scalar_call(
        scalar, scalar._validate_operation, "SCALAR_PROBE_OPERATION_INVALID",
        records[4], run_id, scalar.PROBE_OPERATION_ID, "PROBE")
    event = _scalar_call(scalar, scalar._validate_event,
                         "SCALAR_EVENT_INVALID", records[5], run_id, probe)
    pair = _scalar_call(scalar, scalar._validate_pair,
                        "SCALAR_PAIR_INVALID", records[6], run_id,
                        null, probe)
    _need(event["runId"] == pair["runId"] == run_id and
          null["mode"] == "NULL" and probe["mode"] == "PROBE",
          "SCALAR_NULL_PROBE_PARITY_INVALID")
    return {"runId": run_id, "nullOperation": null,
            "probeOperation": probe, "event": event, "pair": pair}


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes,
                                  ledger: types.ModuleType,
                                  scalar: types.ModuleType
                                  ) -> Tuple[List[str], List[str], List[str]]:
    console = _decode_lines(stdout_raw, "RUN_STDOUT", ledger)
    receipt = _decode_lines(receipt_raw, "RUN_RECEIPT", ledger)
    stderr = _decode_lines(stderr_raw, "RUN_STDERR", ledger)
    _need(stderr_raw == b"", "RUN_STDERR_NOT_EMPTY")
    markers = tuple(scalar.RECEIPT_MARKERS[:7]) + (
        CAP17_EVENT_MARKER, CAP17_SUMMARY_PREFIX)
    _need(len(receipt) == len(markers) and all(receipt),
          "CAP17_RECEIPT_COUNT_INVALID")
    for line, marker in zip(receipt, markers):
        if marker == CAP17_SUMMARY_PREFIX:
            _need(line.startswith(marker) and SUMMARY_RE.fullmatch(line) is not None,
                  "CAP17_SUMMARY_INVALID")
        else:
            _need(line.startswith(marker + " "),
                  "CAP17_RECEIPT_MARKER_ORDER_INVALID")
    event_lines = [line for line in receipt
                   if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    console_events = [line for line in console
                      if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    stderr_events = [line for line in stderr
                     if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    _need(event_lines == receipt and console_events == event_lines and
          not stderr_events,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(FOCUSED_PASS) < console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    _validate_scalar_prefix(receipt[:7], ledger, scalar)
    return console, receipt, stderr


def _strict_canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("CANONICAL_JSON_INVALID")


def _resolve_bound_path(value: Any, base: Path, code: str,
                        ledger: types.ModuleType) -> Path:
    _need(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS,
          code + "_PATH_INVALID")
    return _ledger_call(ledger, ledger._resolve_path, code + "_PATH_INVALID",
                        value, base, code)


def _validate_source_graphspec(spec: Mapping[str, Any], spec_path: Path,
                               graphspec_path: Path, graphspec_raw: bytes,
                               source_spec_path: Path,
                               source_spec_raw: bytes,
                               exporter: types.ModuleType,
                               ledger: types.ModuleType,
                               quality: types.ModuleType
                               ) -> Tuple[Dict[str, Any], bytes]:
    _need(_sha256(source_spec_raw) == SOURCE_GRAPH_INPUT_SPEC_SHA256,
          "SOURCE_GRAPH_INPUT_SPEC_SHA256_MISMATCH")
    _need(0 < len(graphspec_raw) <= MAX_GRAPH_SPEC_BYTES and
          _sha256(graphspec_raw) == spec["graphspecSha256"],
          "SOURCE_GRAPH_SPEC_SHA256_MISMATCH")
    graphspec = _ledger_call(ledger, ledger._strict_json,
                             "SOURCE_GRAPH_SPEC_JSON_INVALID",
                             graphspec_raw, "SOURCE_GRAPH_SPEC")
    _need(type(graphspec) is dict and
          graphspec.get("schema") == "tsj-load12-source-graphspec-v1" and
          graphspec.get("status") == "PASS" and
          graphspec.get("sourceCap") == 48 and
          graphspec.get("scope") == "SOURCE_CAP48_LOAD12_REFERENCE_ONLY",
          "SOURCE_GRAPH_SPEC_IDENTITY_INVALID")
    evidence = graphspec.get("sourceEvidence")
    _need(type(evidence) is dict and
          type(evidence.get("readerAndHelperSha256")) is dict and
          evidence.get("inputSpecSha256") ==
          SOURCE_GRAPH_INPUT_SPEC_SHA256 and
          evidence.get("inputPinsSha256") ==
          SOURCE_GRAPH_PINS_SHA256 and
          evidence.get("inputCount") == SOURCE_INPUT_COUNT and
          evidence.get("inputBytes") == 86_847_809 and
          evidence["readerAndHelperSha256"].get("currentReader") ==
          QUALITY_READER_SHA256 and
          evidence.get("exporterSha256") ==
          GRAPH_SPEC_EXPORTER_SHA256,
          "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
    _need(type(graphspec.get("sourceGraph")) is dict and
          type(graphspec["sourceGraph"].get("event")) is dict and
          type(graphspec["sourceGraph"]["event"].get("diagnostic")) is dict and
          type(graphspec["sourceGraph"]["event"]["diagnostic"].get("masks")) is dict,
          "SOURCE_GRAPH_SPEC_MASKS_INVALID")

    source_spec_sha = spec["sourceGraphInputSpecSha256"]
    try:
        recomputed, canonical = exporter.build_verified_graphspec(
            os.fspath(source_spec_path), source_spec_sha)
    except Exception as error:
        _fail(_safe_foreign_code(error, "SOURCE_GRAPH_RECOMPUTE_FAILED"))
    expected_canonical = _strict_canonical_json(recomputed) + b"\n"
    _need(type(recomputed) is dict and type(canonical) is bytes and
          canonical == expected_canonical and canonical == graphspec_raw and
          recomputed == graphspec and
          _sha256(canonical) == spec["graphspecSha256"],
          "SOURCE_GRAPH_SPEC_RECOMPUTE_MISMATCH")

    # Revalidate the portable cap48 pre through the pinned public reader. The
    # exporter has already re-run its private source-record checks; this second
    # path gives the cap17 consumer its own structured source binding.
    source_pre = _quality_call(
        quality, quality._validate_load12_pre, "SOURCE_LOAD12_PRE_INVALID",
        graphspec["load12Pre"])
    _need(source_pre["source"]["source"]["grid"]["maximumVias"] == 48 and
          source_pre["path"]["tupleSha256"] == SOURCE_GRAPH_PATH_SHA256 and
          source_pre["path"]["goal"]["faceBitsSha256"] == SOURCE_GOAL_SHA256 and
          graphspec["prefixGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256,
          "SOURCE_GRAPH_SPEC_ANCHORS_INVALID")
    return graphspec, canonical


def _java_length(value: str) -> int:
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError:
        _fail("CAP17_SOURCE_TEXT_INVALID")


def _net_order_sha256(net_order: Sequence[str]) -> str:
    canonical = "".join(
        str(index) + ":" + str(_java_length(net_id)) + ":" + net_id + "\n"
        for index, net_id in enumerate(net_order))
    return _sha256(canonical.encode("utf-8", errors="strict"))


def _prefix_rows_sha256(rows: Sequence[Mapping[str, str]]) -> str:
    canonical = "".join(
        str(index) + ":" + str(_java_length(row["id"])) + ":" + row["id"] +
        ":" + str(_java_length(row["fingerprint"])) + ":" +
        row["fingerprint"] + "\n"
        for index, row in enumerate(rows))
    return _sha256(canonical.encode("utf-8", errors="strict"))


def _validate_initial_capture(value: Any, expected_pre: Mapping[str, Any],
                              code: str) -> Dict[str, Any]:
    _exact_keys(value, INITIAL_KEYS, code + "_KEYS_INVALID")
    pre = expected_pre["pre"]
    source = pre["source"]
    prefix_rows = pre["prefixInventory"]["rows"]
    prefix_traces = pre["prefixTraces"]["rows"]
    summary_rows = [{"id": row["id"], "fingerprint": row["fingerprint"]}
                    for row in prefix_rows]
    _need(type(value["seeds"]) is dict and value["seeds"] == pre["seeds"] and
          type(value["netOrder"]) is list and
          value["netOrder"] == source["netOrder"] and
          type(value["prefixRows"]) is list and
          all(type(row) is dict and set(row) == INITIAL_PREFIX_ROW_KEYS
              for row in value["prefixRows"]) and
          value["prefixRows"] == summary_rows and
          type(value["prefix"]) is dict and
          set(value["prefix"]) == PREFIX_SUMMARY_KEYS and
          value["prefix"] == {
              "holeCount": len(prefix_rows),
              "viaCount": sum(1 for row in prefix_rows if row["kind"] == "VIA"),
              "traceCount": len(prefix_traces),
          }, code + "_IDENTITY_INVALID")
    _need(value["netOrderSha256"] == _net_order_sha256(value["netOrder"]) and
          value["prefixRowsSha256"] == _prefix_rows_sha256(value["prefixRows"]) and
          type(value["preGeometrySha256"]) is str and
          SHA256_RE.fullmatch(value["preGeometrySha256"]) is not None and
          value["preGeometrySha256"] ==
          pre["prefixInventory"]["stability"]["prefixGeometrySha256"],
          code + "_DIGEST_OR_GEOMETRY_INVALID")
    return dict(value)


def _validate_graphspec_path_edges(pre: Mapping[str, Any],
                                   graphspec: Mapping[str, Any]) -> Dict[str, Any]:
    source = graphspec["load12Pre"]
    masks = graphspec["sourceGraph"]["event"]["diagnostic"]["masks"]
    grid = graphspec["grid"]
    cells = grid["cells"]
    _exact_keys(masks, {"topStepsHex", "bottomStepsHex", "goalFacesHex"},
                "CAP17_SOURCE_MASK_KEYS_INVALID")
    for name in ("topStepsHex", "bottomStepsHex", "goalFacesHex"):
        _need(type(masks[name]) is str and len(masks[name]) == cells and
              HEX_RE.fullmatch(masks[name]) is not None,
              "CAP17_SOURCE_MASK_ENCODING_INVALID")
    goal_bits = masks["goalFacesHex"]
    _need(all(char in "0123" for char in goal_bits) and
          goal_bits == source["goal"]["faceBits"] ==
          pre["pre"]["goal"]["faceBits"] and
          pre["pre"]["goal"]["faceBitsSha256"] ==
          _sha256(goal_bits.encode("ascii")),
          "CAP17_FULL_GOAL_FACE_BITS_MISMATCH")

    top = [int(char, 16) for char in masks["topStepsHex"]]
    bottom = [int(char, 16) for char in masks["bottomStepsHex"]]
    nodes = pre["path"]["nodes"]
    planar_edges = 0
    transitions = 0
    for before, after in zip(nodes, nodes[1:]):
        if before["layer"] != after["layer"]:
            transitions += 1
            continue
        dx, dy = after["x"] - before["x"], after["y"] - before["y"]
        direction_bit = {
            (10, 0): 0x2, (-10, 0): 0x8,
            (0, 10): 0x4, (0, -10): 0x1,
        }.get((dx, dy), 0)
        mask = top if before["layer"] == 0 else bottom
        _need(direction_bit != 0 and before["cell"] < cells and
              bool(mask[before["cell"]] & direction_bit),
              "CAP17_CLONE_EDGE_MISSING_FROM_SOURCE_MASK")
        planar_edges += 1
    endpoint = nodes[-1]
    _need(bool(int(goal_bits[endpoint["cell"]]) & (1 << endpoint["layer"])),
          "CAP17_CLONE_ENDPOINT_NOT_IN_SOURCE_GOAL_MASK")
    _need(planar_edges == pre["path"]["gridSteps"] and
          transitions == pre["path"]["transitions"],
          "CAP17_CLONE_GRAPH_EDGE_COUNT_INVALID")
    # The source graph's endCell/endLayer/foundTerminalPadId describe its
    # cap48 witness. A cap17 clone may reach another cell in this same mask.
    return {"planarEdges": planar_edges, "transitions": transitions,
            "endpointCell": endpoint["cell"],
            "endpointLayer": endpoint["layer"]}


def _validate_cap17_publication(pre: Mapping[str, Any],
                                post: Mapping[str, Any],
                                publisher: Any) -> Dict[str, Any]:
    _exact_keys(publisher, PUBLISHER_KEYS, "CAP17_PUBLISHER_KEYS_INVALID")
    pre_raw = pre["pre"]
    source = pre_raw["source"]
    prefix_rows = pre["prefix"]["inventory"]["rows"]
    post_inventory = post["inventoryObject"]
    post_rows = post["rows"]
    created_rows = post["createdTraceRows"]
    transitions = post["transitionNodes"]
    _need(len(prefix_rows) == 17 and len(post_rows) == 17 and
          all(before == after for before, after in zip(prefix_rows, post_rows)) and
          post["createdHoleIds"] == [] and
          post_inventory["createdHoleCount"] == 0 and
          post_inventory["createdHoleIds"] == [] and
          post_inventory["holeCount"] == 17 and
          len(created_rows) == 1 and len(transitions) == 0 and
          post_inventory["transitionBindings"] == [] and
          pre["path"]["transitions"] == 0 and
          post["source"]["preHoleCount"] == 17 and
          post["source"]["preTraceCount"] == 88 and
          len(pre["prefixTraces"]) == 88 and
          post["work"]["holeRows"] == 17 and
          post["work"]["traceRows"] == 89 and
          post["work"]["transitionMatches"] == 0,
          "CAP17_POST_PREFIX_OR_CREATION_LINEAGE_INVALID")
    expected_publisher = {
        "completed": True, "stage": 6, "replayComplete": True,
        "preHoleCount": 17, "preViaCount": 17, "preTraceCount": 88,
        "postHoleCount": 17, "postViaCount": 17, "postTraceCount": 89,
        "createdHoleCount": 0, "createdViaCount": 0, "createdTraceCount": 1,
    }
    _need(publisher == expected_publisher and
          source["grid"]["maximumVias"] == CAP17 and
          post["source"]["publisherStage"] == 6,
          "CAP17_NORMAL_PUBLISHER_BINDING_INVALID")
    return {"prefixRowsRetained": True, "createdVias": 0,
            "createdTraces": len(created_rows), "publisherStage": 6}


def _validate_work(value: Any, pre: Mapping[str, Any],
                   post: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, WORK_KEYS, "CAP17_WORK_KEYS_INVALID")
    source_operation = value["sourceOperation"]
    _exact_keys(source_operation, SOURCE_OPERATION_KEYS,
                "CAP17_SOURCE_OPERATION_KEYS_INVALID")
    _need(_integer(source_operation["operationMs"], 1, MAX_ACTIVE_MS) and
          _integer(source_operation["workUnits"], 1, MAX_WORK_UNITS) and
          _integer(source_operation["maxUnitMs"], 0, MAX_UNIT_MS) and
          _integer(source_operation["cleanupMs"], 0, MAX_CLEANUP_MS) and
          _integer(source_operation["manualGroups"], 0,
                   source_operation["workUnits"]) and
          _integer(source_operation["manualSliceSteps"], 0, MAX_TOTAL_WORK) and
          source_operation["manualGroups"] <= source_operation["manualSliceSteps"] and
          source_operation["manualSliceSteps"] <=
          source_operation["workUnits"] * MAX_WORK_QUANTUM,
          "CAP17_SOURCE_OPERATION_BUDGET_OR_CONSERVATION_INVALID")

    copy_work = value["copy"]
    _exact_keys(copy_work, COPY_WORK_KEYS, "CAP17_COPY_WORK_KEYS_INVALID")
    _need(_integer(copy_work["advanceCalls"], 1, MAX_WORK_UNITS) and
          _integer(copy_work["workSteps"], 1, MAX_TOTAL_WORK) and
          _integer(copy_work["copiedEntries"], 0, copy_work["workSteps"]) and
          _integer(copy_work["lastWork"], 0, MAX_WORK_QUANTUM) and
          copy_work["lastWork"] <= copy_work["workSteps"] and
          _integer(copy_work["observerChecks"], 0, copy_work["workSteps"]) and
          copy_work["workSteps"] <=
          copy_work["advanceCalls"] * MAX_WORK_QUANTUM,
          "CAP17_COPY_WORK_CONSERVATION_INVALID")

    replay = value["replay"]
    _exact_keys(replay, REPLAY_WORK_KEYS, "CAP17_REPLAY_WORK_KEYS_INVALID")
    _need(_integer(replay["toPreCalls"], 1, MAX_TOTAL_WORK) and
          _integer(replay["toPreWork"], replay["toPreCalls"], MAX_TOTAL_WORK) and
          _integer(replay["toPostCalls"], 1, MAX_TOTAL_WORK) and
          _integer(replay["toPostWork"], replay["toPostCalls"], MAX_TOTAL_WORK),
          "CAP17_REPLAY_WORK_CONSERVATION_INVALID")

    clone_cursor = value["cloneCursor"]
    source_cursor = value["sourceCursor"]
    for cursor, label in ((clone_cursor, "CLONE"), (source_cursor, "SOURCE")):
        _exact_keys(cursor, CURSOR_WORK_KEYS, "CAP17_" + label + "_CURSOR_KEYS_INVALID")
        _need(_integer(cursor["preAdvanceCalls"], 1, MAX_WORK_UNITS) and
              _integer(cursor["preWork"], 0, MAX_TOTAL_WORK) and
              _integer(cursor["postAdvanceCalls"], 1, MAX_WORK_UNITS) and
              _integer(cursor["postWork"], 0, MAX_TOTAL_WORK) and
              cursor["preWork"] <= MAX_TOTAL_WORK and
              cursor["postWork"] <= MAX_TOTAL_WORK,
              "CAP17_" + label + "_CURSOR_WORK_INVALID")
    _need(clone_cursor["preAdvanceCalls"] == pre["work"]["advanceCalls"] and
          clone_cursor["preWork"] <= pre["work"]["workSteps"] and
          clone_cursor["postAdvanceCalls"] == post["work"]["advanceCalls"] and
          clone_cursor["postWork"] <= post["work"]["workSteps"],
          "CAP17_CLONE_CURSOR_RESULT_MISMATCH")

    outcome = value["sourceOutcome"]
    _exact_keys(outcome, SOURCE_OUTCOME_KEYS,
                "CAP17_SOURCE_OUTCOME_KEYS_INVALID")
    _need(outcome == {
        "phase": "COMPLETE", "qualityRejected": True,
        "targetRequestAccounted": True, "sourceResumed": True,
    }, "CAP17_SOURCE_RESUME_OUTCOME_INVALID")
    return {"sourceOperation": dict(source_operation), "copy": dict(copy_work),
            "replay": dict(replay), "cloneCursor": dict(clone_cursor),
            "sourceCursor": dict(source_cursor), "sourceOutcome": dict(outcome)}


def _validate_selector(value: Any, graphspec: Mapping[str, Any],
                       clone_pre: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, SELECTOR_KEYS, "CAP17_SELECTOR_KEYS_INVALID")
    expected = {
        "rootSeed": ROOT_SEED,
        "placementSeed": PLACEMENT_SEED,
        "routingSeed": ROUTING_SEED,
        "routeRequestIndex": 0,
        "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4,
        "eligiblePlacementAttempts": [4, 2, 3, 0, 1],
        "placementFingerprintSha256":
            "6553ad3df059c6bfe2980efe39ff951d3f6322fc0694b2c96e26508f8d3dfe2e",
        "pass": 1, "netPosition": 3, "nextFetch": 4,
        "branchOrdinal": 73, "searchBranches": 73, "netId": "LOAD12",
        "primaryLayer": "BOTTOM", "startPadId": "JLOAD.1",
        "rootPadId": "CSTORE_E.1",
        "grid": {"width": 478, "height": 309, "cells": 147702,
                 "minX": 30, "minY": 30, "pitch": 10},
        "prefix": {"vias": CAP17, "traces": 88},
    }
    _need(value == expected,
          "CAP17_SELECTOR_LINEAGE_INVALID")
    selection = graphspec["selection"]
    _need(selection == {
        "operationId": "QUALITY_PROBE",
        "rootSeed": ROOT_SEED,
        "placementSeed": PLACEMENT_SEED,
        "routingSeed": ROUTING_SEED,
        "routeRequestIndex": 0,
        "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4,
        "pass": 1,
        "branchOrdinal": 73,
        "netId": "LOAD12",
        "netPosition": 3,
        "netIndexNextFetch": 4,
        "policy": "FULLER_TWO_LAYER",
        "maximumTransitions": 4,
        "prefixHoleCount": 17,
        "prefixTraceCount": 88,
    }, "CAP17_SOURCE_GRAPH_SELECTION_MISMATCH")
    source = clone_pre["pre"]["source"]
    grid = source["grid"]
    _need(source["pass"] == value["pass"] and
          source["branchOrdinal"] == value["branchOrdinal"] and
          source["netId"] == value["netId"] and
          source["netPosition"] == value["netPosition"] and
          source["netIndexNextFetch"] == value["nextFetch"] and
          source["startPadId"] == value["startPadId"] and
          source["rootPadId"] == value["rootPadId"] and
          source["policy"] == "FULLER_TWO_LAYER" and
          source["grid"]["primaryLayer"] == value["primaryLayer"] and
          grid["maximumVias"] == CAP17 and
          grid["maximumTransitions"] == 4 and
          {key: grid[key] for key in ("width", "height", "cells", "minX", "minY")} ==
          {key: value["grid"][key] for key in ("width", "height", "cells", "minX", "minY")},
          "CAP17_SELECTOR_SOURCE_BINDING_INVALID")
    graph_grid = graphspec["grid"]
    _need(graph_grid["width"] == value["grid"]["width"] and
          graph_grid["height"] == value["grid"]["height"] and
          graph_grid["cells"] == value["grid"]["cells"] and
          graph_grid["originX"] == value["grid"]["minX"] and
          graph_grid["originY"] == value["grid"]["minY"] and
          graph_grid["pitch"] == value["grid"]["pitch"] and
          graphspec["initialIdentities"]["startPadId"] == value["startPadId"] and
          graphspec["initialIdentities"]["rootPadId"] == value["rootPadId"],
          "CAP17_SELECTOR_GRAPH_GEOMETRY_INVALID")
    return dict(value)


def _validate_original_pre_binding(original_pre: Mapping[str, Any],
                                   source_pre: Mapping[str, Any]) -> None:
    original_raw = original_pre["pre"]
    source_raw = source_pre["pre"]
    _need(original_pre["source"]["source"] ==
          source_pre["source"]["source"] and
          original_pre["source"]["grid"] == source_pre["source"]["grid"] and
          original_raw["seeds"] == source_raw["seeds"] and
          original_raw["goal"] == source_raw["goal"] and
          original_pre["prefix"]["inventory"]["rows"] ==
          source_pre["prefix"]["inventory"]["rows"] and
          original_raw["prefixInventory"]["stability"][
              "prefixGeometrySha256"] ==
          source_raw["prefixInventory"]["stability"][
              "prefixGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256 and
          original_pre["prefixTraces"] == source_pre["prefixTraces"] and
          original_pre["path"] == source_pre["path"],
          "CAP17_ORIGINAL_PRE_SOURCE_BINDING_INVALID")


def _original_pre_counts(pre: Mapping[str, Any]) -> Dict[str, int]:
    rows = pre["prefix"]["inventory"]["rows"]
    return {
        "holeCount": len(rows),
        "viaCount": sum(1 for row in rows if row["kind"] == "VIA"),
        "traceCount": len(pre["prefixTraces"]),
    }


def _validate_original_source(value: Any, graphspec: Mapping[str, Any],
                              source_pre: Mapping[str, Any],
                              quality: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, ORIGINAL_KEYS, "CAP17_ORIGINAL_KEYS_INVALID")
    path = value["path"]
    _exact_keys(path, PATH_SUMMARY_KEYS, "CAP17_ORIGINAL_PATH_KEYS_INVALID")
    source_path = graphspec["sourcePath"]
    expected_path = {
        "pathTupleSha256": source_path["tupleSha256"],
        "goalFaceBitsSha256": graphspec["load12Pre"]["goal"]["faceBitsSha256"],
        "nodeCount": source_path["nodeCount"],
        "planarLength": source_path["length"],
        "bends": source_path["bends"],
        "flips": source_path["transitions"],
    }
    _need(path == expected_path and
          path["pathTupleSha256"] == SOURCE_GRAPH_PATH_SHA256 and
          path["goalFaceBitsSha256"] == SOURCE_GOAL_SHA256 and
          path["nodeCount"] == 222 and path["planarLength"] == 2170 and
          path["bends"] == 2 and path["flips"] == 4,
          "CAP17_ORIGINAL_SOURCE_PATH_MISMATCH")

    original_pre = _quality_call(
        quality, quality._validate_load12_pre, "CAP17_ORIGINAL_PRE_INVALID",
        value["preResult"], expected_maximum_vias=MAX_VIAS)
    original_post = _quality_call(
        quality, quality._validate_load12_post, "CAP17_ORIGINAL_POST_INVALID",
        value["postResult"], original_pre)
    _validate_original_pre_binding(original_pre, source_pre)

    _exact_keys(value["pre"], ORIGINAL_PRE_KEYS,
                "CAP17_ORIGINAL_PRE_KEYS_INVALID")
    pre_counts = _original_pre_counts(original_pre)
    _need(pre_counts == {"holeCount": 17, "viaCount": 17, "traceCount": 88} and
          value["pre"] == pre_counts,
          "CAP17_ORIGINAL_PRE_COUNTS_INVALID")

    _exact_keys(value["post"], ORIGINAL_POST_KEYS,
                "CAP17_ORIGINAL_POST_KEYS_INVALID")
    post_rows = original_post["rows"]
    created_ids = set(original_post["createdHoleIds"])
    created_rows = [row for row in post_rows if row["id"] in created_ids]
    created_via_count = sum(1 for row in created_rows if row["kind"] == "VIA")
    created_traces = original_post["createdTraceRows"]
    post_inventory = original_post["inventoryObject"]
    post_counts = {
        "holeCount": len(post_rows),
        "viaCount": sum(1 for row in post_rows if row["kind"] == "VIA"),
        "traceCount": pre_counts["traceCount"] + len(created_traces),
        "createdViaCount": created_via_count,
        "createdTraceCount": len(created_traces),
    }
    _need(post_counts == {
        "holeCount": 21, "viaCount": 21, "traceCount": 93,
        "createdViaCount": 4, "createdTraceCount": 5,
    } and value["post"] == post_counts and
          len(created_ids) == 4 and len(created_rows) == 4 and
          created_via_count == len(created_ids) and
          original_post["source"]["preHoleCount"] == pre_counts["holeCount"] and
          original_post["source"]["preTraceCount"] == pre_counts["traceCount"] and
          post_inventory["holeCount"] == post_counts["holeCount"] and
          post_inventory["createdHoleCount"] ==
          post_counts["createdViaCount"] and
          post_inventory["createdTraceCount"] ==
          post_counts["createdTraceCount"],
          "CAP17_ORIGINAL_POST_COUNTS_INVALID")
    for name in ("preGeometrySha256", "postGeometrySha256", "publicationSha256"):
        _need(type(value[name]) is str and SHA256_RE.fullmatch(value[name]) is not None,
              "CAP17_ORIGINAL_DIGEST_INVALID")
    _need(value["preGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256 ==
          graphspec["prefixGeometrySha256"] ==
          original_pre["pre"]["prefixInventory"]["stability"][
              "prefixGeometrySha256"],
          "CAP17_ORIGINAL_PREFIX_GEOMETRY_MISMATCH")
    checked = dict(value)
    checked["_preChecked"] = original_pre
    checked["_postChecked"] = original_post
    checked["_preCounts"] = pre_counts
    checked["_postCounts"] = post_counts
    return checked


def _validate_source_after_replay(value: Any,
                                  source_initial: Mapping[str, Any],
                                  source_pre: Mapping[str, Any]
                                  ) -> Dict[str, Any]:
    _exact_keys(value, AFTER_REPLAY_KEYS,
                "CAP17_SOURCE_AFTER_REPLAY_KEYS_INVALID")
    expected_seeds = source_pre["pre"]["seeds"]
    _need(_integer(value["sessionMaximumVias"], 48, 48) and
          _integer(value["searchMaximumVias"], 48, 48) and
          _integer(value["branchExpanded"], 0, 0) and
          _integer(value["holeCount"], 17, 17) and
          _integer(value["traceCount"], 88, 88) and
          type(value["seeds"]) is dict and
          value["seeds"] == source_initial["seeds"] == expected_seeds,
          "CAP17_SOURCE_AFTER_REPLAY_STATE_INVALID")
    return dict(value)


def _validate_comparisons(value: Any, source_initial: Mapping[str, Any],
                          clone_initial: Mapping[str, Any],
                          source_pre: Mapping[str, Any],
                          clone_pre: Mapping[str, Any],
                          source_after_replay: Mapping[str, Any],
                          post: Mapping[str, Any], work: Mapping[str, Any],
                          original: Mapping[str, Any],
                          clone_path: Mapping[str, Any]
                          ) -> Dict[str, Any]:
    _exact_keys(value, COMPARISON_KEYS, "CAP17_COMPARISON_KEYS_INVALID")
    _need(all(type(flag) is bool for flag in value.values()),
          "CAP17_COMPARISON_TYPES_INVALID")
    source_raw = source_pre["pre"]
    clone_raw = clone_pre["pre"]
    post_inventory = post["inventoryObject"]
    prefix_ids = {row["id"] for row in clone_pre["prefix"]["inventory"]["rows"]}
    retained_ids = {row["id"] for row in post["rows"]}
    original_pre = original["_preChecked"]
    original_post = original["_postChecked"]
    original_pre_counts = original["_preCounts"]
    original_post_counts = original["_postCounts"]
    source_preserved = (
        source_after_replay["sessionMaximumVias"] ==
        source_after_replay["searchMaximumVias"] ==
        original_pre["source"]["grid"]["maximumVias"] == 48 and
        source_after_replay["branchExpanded"] == 0 and
        source_after_replay["holeCount"] ==
        original_pre_counts["holeCount"] == 17 and
        source_after_replay["traceCount"] ==
        original_pre_counts["traceCount"] == 88 and
        source_after_replay["seeds"] == original_pre["pre"]["seeds"] ==
        source_initial["seeds"])
    original_source = original_post["source"]
    original_path_publisher = original_pre["path"]["publisher"]
    source_resumed = (
        original_path_publisher["stage"] == 1 and
        original_source["pass"] == 1 and
        original_source["publisherStage"] == 6 and
        original_source["searchBranchIsNull"] is True and
        original_source["searchPublisherIsNull"] is True and
        original_source["preHoleCount"] == original_pre_counts["holeCount"] and
        original_source["preTraceCount"] == original_pre_counts["traceCount"] and
        original_post_counts == {
            "holeCount": 21, "viaCount": 21, "traceCount": 93,
            "createdViaCount": 4, "createdTraceCount": 5,
        })
    computed = {
        "initialSeedsEqual": source_initial["seeds"] == clone_initial["seeds"] and
            source_initial["seeds"] == source_raw["seeds"],
        "initialNetOrderEqual": source_initial["netOrder"] ==
            clone_initial["netOrder"] == source_raw["source"]["netOrder"],
        "initialPrefixRowsEqual": source_initial["prefixRows"] ==
            clone_initial["prefixRows"] and
            source_initial["prefixRows"] == [
                {"id": row["id"], "fingerprint": row["fingerprint"]}
                for row in source_raw["prefixInventory"]["rows"]],
        "initialPrefixGeometryEqual": source_initial["preGeometrySha256"] ==
            clone_initial["preGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256,
        "fullGoalFaceBitsEqual": source_raw["goal"]["faceBits"] ==
            clone_raw["goal"]["faceBits"],
        "pathTupleEqualObserved":
            original["path"]["pathTupleSha256"] == clone_path["pathTupleSha256"],
        "prefixRetained": prefix_ids == retained_ids and
            post_inventory["createdHoleCount"] == 0,
        "zeroNewVias": post_inventory["createdHoleCount"] == 0 and
            post_inventory["createdHoleIds"] == [] and
            not any(row["kind"] == "VIA"
                    for row in post["rows"]
                    if row["id"] not in prefix_ids),
        "sourcePreserved": source_preserved,
        "sourceResumed": source_resumed,
    }
    _need(work["sourceOutcome"]["qualityRejected"] is True and
          work["sourceOutcome"]["sourceResumed"] is source_resumed,
          "CAP17_SOURCE_OUTCOME_EVIDENCE_MISMATCH")
    for name, expected in computed.items():
        _need(value[name] is expected,
              "CAP17_COMPARISON_DATA_MISMATCH")
    # These two producer-reported comparisons have no clone digest fields in
    # this wire event. Keep them as observations; path, prefix, and Publisher
    # acceptance above is computed independently from captured data.
    _need(value["postGeometryEqualObserved"] is False and
          value["publicationEqualObserved"] is False,
          "CAP17_COMPARISON_OBSERVATION_INVALID")
    return {**computed,
            "postGeometryEqualObserved": value["postGeometryEqualObserved"],
            "publicationEqualObserved": value["publicationEqualObserved"]}


def _validate_event(value: Any, graphspec: Mapping[str, Any],
                    quality: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, EVENT_KEYS, "CAP17_EVENT_KEYS_INVALID")
    _need(value["schema"] == CAP17_EVENT_SCHEMA and
          value["status"] == "PASS" and
          _integer(value["protocolVersion"], 1, 1) and
          type(value["runId"]) is str and
          RUN_ID_RE.fullmatch(value["runId"]) is not None and
          value["operationId"] == "QUALITY_PROBE" and
          value["scope"] == CAP17_PATH_SCOPE,
          "CAP17_EVENT_IDENTITY_INVALID")

    source = value["source"]
    clone = value["clone"]
    _validate_cap_pair(source, clone)

    source_pre = _quality_call(
        quality, quality._validate_load12_pre, "SOURCE_GRAPH_PRE_INVALID",
        graphspec["load12Pre"])
    clone_pre = _quality_call(
        quality, quality._validate_load12_pre, "CAP17_CLONE_PRE_INVALID",
        clone["preResult"], expected_maximum_vias=CAP17)
    expected_source_fields = dict(graphspec["load12Pre"]["source"])
    expected_grid = dict(expected_source_fields["grid"])
    expected_grid["maximumVias"] = CAP17
    expected_source_fields["grid"] = expected_grid
    _need(clone_pre["pre"]["source"] == expected_source_fields and
          clone_pre["pre"]["seeds"] == source_pre["pre"]["seeds"] and
          clone_pre["pre"]["prefixInventory"]["rows"] ==
          source_pre["pre"]["prefixInventory"]["rows"] and
          clone_pre["pre"]["prefixTraces"] ==
          source_pre["pre"]["prefixTraces"] and
          clone_pre["pre"]["prefixInventory"]["stability"][
              "prefixGeometrySha256"] == graphspec["prefixGeometrySha256"],
          "CAP17_CLONE_SOURCE_PRE_BINDING_INVALID")

    selector = _validate_selector(value["selector"], graphspec, clone_pre)
    source_initial = _validate_initial_capture(
        source["initial"], source_pre, "CAP17_SOURCE_INITIAL")
    clone_initial = _validate_initial_capture(
        clone["initial"], source_pre, "CAP17_CLONE_INITIAL")
    _need(source_initial == clone_initial,
          "CAP17_INITIAL_COPY_IDENTITY_MISMATCH")

    source_after_replay = _validate_source_after_replay(
        source["afterReplayBeforeResume"], source_initial, source_pre)
    original = _validate_original_source(
        value["original"], graphspec, source_pre, quality)
    path_summary = clone["path"]
    _exact_keys(path_summary, PATH_SUMMARY_KEYS,
                "CAP17_CLONE_PATH_SUMMARY_KEYS_INVALID")
    path_checked = clone_pre["path"]
    expected_path_summary = {
        "pathTupleSha256": path_checked["tupleSha256"],
        "goalFaceBitsSha256": path_checked["goal"]["faceBitsSha256"],
        "nodeCount": len(path_checked["nodes"]),
        "planarLength": path_checked["length"],
        "bends": path_checked["bends"],
        "flips": path_checked["transitions"],
    }
    _need(path_summary == expected_path_summary,
          "CAP17_CLONE_PATH_SUMMARY_MISMATCH")
    graph_path = _validate_graphspec_path_edges(clone_pre, graphspec)

    clone_post = _quality_call(
        quality, quality._validate_load12_post, "CAP17_CLONE_POST_INVALID",
        clone["postResult"], clone_pre)
    publication = _validate_cap17_publication(
        clone_pre, clone_post, clone["normalPublisher"])
    work = _validate_work(value["work"], clone_pre, clone_post)

    comparison = _validate_comparisons(
        value["comparison"], source_initial, clone_initial,
        source_pre, clone_pre, source_after_replay, clone_post, work, original,
        path_summary)
    _need(value["branchRouteFound"] is True and
          value["fullBoardFeasibility"] == "UNKNOWN" and
          value["routeAdoption"] is False and
          value["layoutQualification"] is False and
          value["playable100"] is False,
          "CAP17_SCOPE_OR_LIMITATION_CLAIMS_INVALID")
    claims = clone_post["claims"]
    _need(claims == {
        "avoidability": "UNKNOWN", "chronologyProof": False,
        "routeAdoption": False, "layoutQualification": False,
        "playable100": 0,
    }, "CAP17_CLONE_CLAIMS_INVALID")
    return {
        "schema": CAP17_EVENT_SCHEMA,
        "status": "PASS",
        "runId": value["runId"],
        "operationId": value["operationId"],
        "sourceCap": 48,
        "cloneCap": CAP17,
        "selector": selector,
        "path": {
            "nodeCount": path_checked["path"]["nodeCount"],
            "planarLength": path_checked["length"],
            "bends": path_checked["bends"],
            "transitions": path_checked["transitions"],
            "pathTupleSha256": path_checked["tupleSha256"],
            "goalFaceBitsSha256": path_checked["goal"]["faceBitsSha256"],
            "endpointCell": graph_path["endpointCell"],
            "endpointLayer": graph_path["endpointLayer"],
            "sourceMaskPlanarEdges": graph_path["planarEdges"],
        },
        "publication": publication,
        "comparison": comparison,
        "work": work,
        "claims": claims,
        "limitations": {
            "fullBoardFeasibility": "UNKNOWN",
            "routeAdoption": False,
            "layoutQualification": False,
            "playable100": False,
        },
    }


def _validate_native_artifacts(spec_argument: str, spec_sha256: str,
                               own_sha: str,
                               quality: types.ModuleType,
                               frozen: types.ModuleType,
                               scalar: types.ModuleType,
                               ledger: types.ModuleType,
                               capture: types.ModuleType,
                               helper_hashes: Mapping[str, str]
                               ) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _resolve_bound_path(spec_argument, Path.cwd(), "INPUT_SPEC", ledger)
    spec_raw = _ledger_call(ledger, ledger._read_bounded, "INPUT_SPEC_READ_FAILED",
                            spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256,
          "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(ledger, ledger._strict_json,
                        "INPUT_SPEC_JSON_INVALID", spec_raw, "INPUT_SPEC")
    legacy_spec, context = _validate_input_spec(
        spec, spec_path.parent, ledger, capture)

    source_spec_path = _resolve_bound_path(
        spec["sourceGraphInputSpecPath"], spec_path.parent,
        "SOURCE_GRAPH_INPUT_SPEC", ledger)
    source_spec_raw = _ledger_call(
        ledger, ledger._read_bounded, "SOURCE_GRAPH_INPUT_SPEC_READ_FAILED",
        source_spec_path, MAX_SPEC_BYTES, "SOURCE_GRAPH_INPUT_SPEC")
    graphspec_path = _resolve_bound_path(
        spec["graphspecPath"], spec_path.parent, "GRAPH_SPEC", ledger)
    graphspec_raw = _ledger_call(
        ledger, ledger._read_bounded, "GRAPH_SPEC_READ_FAILED",
        graphspec_path, MAX_GRAPH_SPEC_BYTES, "GRAPH_SPEC")
    exporter, exporter_raw, exporter_path = _load_graphspec_exporter(
        Path(os.path.abspath(__file__)).parent)
    graphspec, graphspec_canonical = _validate_source_graphspec(
        spec, spec_path, graphspec_path, graphspec_raw, source_spec_path,
        source_spec_raw, exporter, ledger, quality)

    artifacts = context["artifacts"]
    result_raw = _ledger_call(
        ledger, ledger._read_bounded, "NATIVE_RUN_READ_FAILED",
        artifacts["resultPath"], MAX_RESULT_BYTES, "NATIVE_RUN")
    stdout_raw = _ledger_call(
        ledger, ledger._read_bounded, "RUN_STDOUT_READ_FAILED",
        artifacts["stdoutPath"], MAX_CAPTURE_BYTES, "RUN_STDOUT")
    receipt_raw = _ledger_call(
        ledger, ledger._read_bounded, "RUN_RECEIPT_READ_FAILED",
        artifacts["receiptPath"], MAX_CAPTURE_BYTES, "RUN_RECEIPT")
    stderr_raw = _ledger_call(
        ledger, ledger._read_bounded, "RUN_STDERR_READ_FAILED",
        artifacts["stderrPath"], MAX_CAPTURE_BYTES, "RUN_STDERR", allow_empty=True)
    run_value = _ledger_call(ledger, ledger._strict_json,
                             "NATIVE_RUN_JSON_INVALID", result_raw, "NATIVE_RUN")
    native_run = _validate_native_run(
        run_value, context, stdout_raw, receipt_raw, stderr_raw, scalar)
    _console, receipt_lines, _stderr = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger, scalar)

    event_line = receipt_lines[7]
    event = _parse_tagged(event_line, CAP17_EVENT_MARKER,
                          "CAP17_EVENT", ledger)
    cap17 = _validate_event(event, graphspec, quality)
    scalar_prefix = _validate_scalar_prefix(receipt_lines[:7], ledger, scalar)
    _need(event["runId"] == scalar_prefix["runId"],
          "CAP17_SCALAR_RUN_ID_MISMATCH")
    summary_match = SUMMARY_RE.fullmatch(receipt_lines[8])
    _need(summary_match is not None and
          _integer(int(summary_match.group(1)), 1, MAX_SCALAR),
          "CAP17_SUMMARY_ASSERTION_COUNT_INVALID")

    # Recheck every consumed input and output after parsing. The read-only
    # graphspec API recomputed its bytes above; this pass binds those bytes and
    # the cap17 receipt to the same paths until return.
    _scalar_call(scalar, scalar._stable_source_check,
                 "SOURCE_REVALIDATION_FAILED", spec_raw, spec_path,
                 spec_sha256, legacy_spec, context, ledger, capture)
    frozen_sha = helper_hashes["frozenQualityPathReaderSha256"]
    _quality_call(quality, quality._stable_helpers,
                  "QUALITY_HELPER_REVALIDATION_FAILED",
                  QUALITY_READER_SHA256, frozen_sha,
                  helper_hashes, frozen, scalar)
    for path, expected, limit, code, allow_empty in (
            (spec_path, spec_raw, MAX_SPEC_BYTES, "INPUT_SPEC", False),
            (source_spec_path, source_spec_raw, MAX_SPEC_BYTES,
             "SOURCE_GRAPH_INPUT_SPEC", False),
            (graphspec_path, graphspec_raw, MAX_GRAPH_SPEC_BYTES,
             "GRAPH_SPEC", False),
            (artifacts["resultPath"], result_raw, MAX_RESULT_BYTES,
             "NATIVE_RUN", False),
            (artifacts["stdoutPath"], stdout_raw, MAX_CAPTURE_BYTES,
             "RUN_STDOUT", False),
            (artifacts["receiptPath"], receipt_raw, MAX_CAPTURE_BYTES,
             "RUN_RECEIPT", False),
            (artifacts["stderrPath"], stderr_raw, MAX_CAPTURE_BYTES,
             "RUN_STDERR", True),
            (exporter_path, exporter_raw, MAX_READER_BYTES,
             "GRAPH_SPEC_EXPORTER", False)):
        again = _ledger_call(ledger, ledger._read_bounded,
                             code + "_REVALIDATION_FAILED",
                             path, limit, code, allow_empty=allow_empty)
        _need(again == expected,
              code + "_CHANGED_DURING_VALIDATION")
    _need(_sha256(graphspec_canonical) == spec["graphspecSha256"] and
          _sha256(exporter_raw) == GRAPH_SPEC_EXPORTER_SHA256 and
          _sha256(_read_local(Path(os.path.abspath(__file__)),
                              MAX_READER_BYTES, "READER_SOURCE")) == own_sha,
          "CAP17_SOURCE_OR_EXPORTER_CHANGED_DURING_VALIDATION")

    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "target": TARGET,
        "purpose": PURPOSE,
        "runId": event["runId"],
        "nativeRunSha256": _sha256(result_raw),
        "inputSpecSha256": spec_sha256,
        "graphspecSha256": spec["graphspecSha256"],
        "sourceGraphInputSpecSha256": SOURCE_GRAPH_INPUT_SPEC_SHA256,
        "sourcePinsSha256": context["pinsSha256"],
        "sourceInputCount": context["inputCount"],
        "sourceInputBytes": context["inputBytes"],
        "sourceSnapshotRevision": context["sourceSnapshotRevision"],
        "command": dict(native_run["command"]),
        "suite": SUITE,
        "sourceCap": 48,
        "detachedCap": CAP17,
        "path": cap17["path"],
        "publication": cap17["publication"],
        "comparison": cap17["comparison"],
        "work": cap17["work"],
        "claims": cap17["claims"],
        "limitations": cap17["limitations"],
        "receiptSha256": _sha256(receipt_raw),
        "stdoutSha256": _sha256(stdout_raw),
        "stderrSha256": _sha256(stderr_raw),
        "cleanupStatus": native_run["cleanupStatus"],
    }


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, MAX_READER_BYTES, "READER_SOURCE")
    own_sha = _sha256(own_raw)
    quality, frozen, scalar, ledger, capture, helper_hashes = _load_helpers()
    return _validate_native_artifacts(
        spec_argument, spec_sha256, own_sha, quality, frozen,
        scalar, ledger, capture, helper_hashes)


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
        sys.stdout.buffer.write(_strict_canonical_json(result) + b"\n")
        sys.stdout.buffer.flush()
        return 0
    except Load12Cap17InputError as error:
        code = error.code if ERROR_CODE_RE.fullmatch(error.code) else "VALIDATION_FAILED"
        sys.stdout.write("FAIL: " + code + "\n")
        sys.stdout.flush()
        return 2
    except Exception as error:
        code = _safe_foreign_code(error, "VALIDATION_FAILED")
        sys.stdout.write("FAIL: " + code + "\n")
        sys.stdout.flush()
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
