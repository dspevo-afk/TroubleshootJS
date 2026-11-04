#!/usr/bin/env python3
"""Independently validate the bounded actual LOAD12 detached-cost240 replay.

This reader binds one current source/copy replay to a frozen cap48 graph
reference, recomputes both captured node-cost recurrences, and checks normal
publication plus ordinary source resumption. It does not qualify a layout,
adopt a route, or establish whole-board feasibility.
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


INPUT_SCHEMA = "p07-load12-detached-cost240-input-v1"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
RESULT_SCHEMA = "p07-load12-detached-cost240-reader-result-v1"
TARGET = "LOAD12"
PURPOSE = "ACTUAL_COST240"
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
SOURCE_GRAPH_SPEC_SHA256 = (
    "602fb4b250f22e8ab55b40ee124ac9dfc7b6575d3268132ae2123b0076b78df1")
SOURCE_GRAPH_PATH_SHA256 = (
    "1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d")
SOURCE_GOAL_SHA256 = (
    "cc48299ed82e39f1bac10269e7c41afc6468f1e53fc96baa36a95d5c60d548d2")
SOURCE_PREFIX_GEOMETRY_SHA256 = (
    "7c963bb9ba10f7419d2e6abaecff4cfc1504ba2744914d6874ffd02ca475c447")

QUALITY_READER_SHA256 = (
    "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc")
GRAPH_SPEC_EXPORTER_SHA256 = (
    "4475aaded5c56c79ab236ba58fdefa1ca89926e08df67a151aacdd7a0b52e112")
CAP17_PURE_READER_SHA256 = (
    "0d7a2708a66628764d92887b75a74a0886a3313d9088ccd1bfbe6b8905e8cee1")

COST240_EVENT_SCHEMA = "rb100-load12-detached-cost240-r1"
COST240_EVENT_MARKER = "RB100_LOAD12_DETACHED_COST240_EVENT"
COST240_SCOPE = "ONE_ACTUAL_LOAD12_BRANCH_DETACHED_COST240_REPLAY"
COST240_SUMMARY_PREFIX = (
    "PASS: RB100 LOAD12 detached cost240 contracts assertions=")
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN.")
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

MAX_WORK_UNITS = 640
MAX_WORK_QUANTUM = 32_768
MAX_TOTAL_WORK = MAX_WORK_UNITS * MAX_WORK_QUANTUM
MAX_ACTIVE_MS = 90_000
MAX_CLEANUP_MS = 120_000
MAX_UNIT_MS = 5_000
MAX_NATIVE_LIMIT_MS = 480_000
MAX_OUTER_LIMIT_MS = 1_800_000
MAX_VIAS = 48
SOURCE_VIA_COST = 60
DETACHED_VIA_COST = 240
MIN_SEARCH_VIA_COST = 35
MAX_TRANSITIONS = 4
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
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
SUMMARY_RE = re.compile(
    re.escape(COST240_SUMMARY_PREFIX) + r"([1-9][0-9]{0,9})\Z", re.ASCII)

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
    "sessionMaximumVias searchMaximumVias sessionViaCost searchViaCost initial "
    "afterReplayBeforeResume").split())
CLONE_KEYS = frozenset((
    "requestedMaximumVias requestedViaCost sessionMaximumVias searchMaximumVias "
    "sessionViaCost searchViaCost maximumTransitions initial path preResult "
    "postResult normalPublisher").split())
INITIAL_KEYS = frozenset((
    "seeds netOrder netOrderSha256 prefixRows prefixRowsSha256 "
    "preGeometrySha256 prefix").split())
PREFIX_SUMMARY_KEYS = frozenset("holeCount viaCount traceCount".split())
INITIAL_PREFIX_ROW_KEYS = frozenset("id fingerprint".split())
SOURCE_AFTER_KEYS = frozenset((
    "sessionMaximumVias searchMaximumVias sessionViaCost searchViaCost "
    "branchExpanded frontier holeCount traceCount").split())
FRONTIER_KEYS = frozenset("queueSize bestSize seeds".split())
CLONE_PATH_KEYS = frozenset((
    "pathTupleSha256 goalFaceBitsSha256 nodeCount planarLength bends flips "
    "transitionCount endCost").split())
ORIGINAL_KEYS = frozenset((
    "path preGeometrySha256 postGeometrySha256 publicationSha256 preResult "
    "postResult pre post").split())
ORIGINAL_PATH_KEYS = CLONE_PATH_KEYS
ORIGINAL_PRE_KEYS = frozenset("holeCount viaCount traceCount".split())
ORIGINAL_POST_KEYS = frozenset((
    "holeCount viaCount traceCount createdViaCount createdTraceCount").split())
LIMIT_KEYS = frozenset((
    "operationMs workUnits unitMs cleanupMs quantum maximumSourceVias "
    "maximumDetachedVias sourceViaCost detachedViaCost maximumTransitions").split())
COMPARISON_KEYS = frozenset((
    "initialSeedsEqual initialNetOrderEqual initialPrefixRowsEqual "
    "initialPrefixGeometryEqual fullGoalFaceBitsEqual pathTupleEqualObserved "
    "postGeometryEqualObserved publicationEqualObserved postRowsEqual "
    "prefixRetained sourcePreserved sourceResumed "
    "sourceCostRecurrenceVerified cloneCostRecurrenceVerified").split())
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


class Load12Cost240InputError(ValueError):
    """A sanitized failure code; paths and private source data are not echoed."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise Load12Cost240InputError(code)


def _need(condition: Any, code: str) -> None:
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
    except Load12Cost240InputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _exec_private(raw: bytes, path: Path, name: str, fallback: str
                  ) -> types.ModuleType:
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
        quality_raw, quality_path, "_p07_load12_cost240_quality_reader",
        "QUALITY_READER_SOURCE_INVALID")
    try:
        frozen, scalar, ledger, capture, hashes = quality._load_pinned_helpers()
    except quality.QualityZeroViaInputError as error:
        _fail(_safe_foreign_code(error, "QUALITY_HELPERS_INVALID"))

    ledger.INPUT_SCHEMA = INPUT_SCHEMA
    ledger.SOURCE_REVISION = SOURCE_REVISION
    ledger.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    ledger.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    scalar.RUN_SCHEMA = RUN_SCHEMA
    scalar.SOURCE_REVISION = SOURCE_REVISION
    scalar.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    scalar.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    scalar.SUITE = SUITE
    hashes["cost240QualityReaderSha256"] = QUALITY_READER_SHA256
    return quality, frozen, scalar, ledger, capture, hashes


def _load_pinned_pure_reader(directory: Path
                             ) -> Tuple[types.ModuleType, bytes, Path]:
    path = directory / "read_load12_cap17.py"
    raw = _read_local(path, MAX_READER_BYTES, "CAP17_PURE_READER")
    _need(_sha256(raw) == CAP17_PURE_READER_SHA256,
          "CAP17_PURE_READER_HASH_MISMATCH")
    module = _exec_private(raw, path, "_p07_cost240_cap17_pure_api",
                           "CAP17_PURE_READER_SOURCE_INVALID")
    _need(callable(getattr(module, "_validate_graphspec_path_edges", None)),
          "CAP17_GRAPH_PATH_API_INVALID")
    return module, raw, path


def _load_graphspec_exporter(directory: Path
                             ) -> Tuple[types.ModuleType, bytes, Path]:
    path = directory / "export_load12_graphspec.py"
    raw = _read_local(path, MAX_READER_BYTES, "GRAPH_SPEC_EXPORTER")
    _need(_sha256(raw) == GRAPH_SPEC_EXPORTER_SHA256,
          "GRAPH_SPEC_EXPORTER_HASH_MISMATCH")
    module = _exec_private(
        raw, path, "_p07_load12_cost240_graphspec_exporter",
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


def _graph_call(graph_reader: types.ModuleType, function, fallback: str,
                *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except graph_reader.Load12Cap17InputError as error:
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
          spec["graphspecSha256"] == SOURCE_GRAPH_SPEC_SHA256 and
          type(spec["sourceGraphInputSpecSha256"]) is str and
          spec["sourceGraphInputSpecSha256"] ==
          SOURCE_GRAPH_INPUT_SPEC_SHA256,
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
          "COST240_SOURCE_PINS_OMIT_REQUIRED_INPUT")
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
        "args": ["-Rb100Load12Cost240Replay"],
        "environment": {},
    }
    _need(value["command"] == expected_command,
          "NATIVE_RUN_COST240_COMMAND_INVALID")

    # The exact cost240 command is checked above. The pinned scalar envelope
    # describes the shared no-argument native run, so give it a private view.
    shared_view = {key: item for key, item in value.items() if key != "target"}
    shared_command = dict(value["command"])
    shared_command["args"] = []
    shared_view["command"] = shared_command
    _scalar_call(scalar, scalar._validate_native_run, "NATIVE_RUN_INVALID",
                 shared_view, context, stdout_raw, receipt_raw, stderr_raw)
    return dict(value)


def _decode_lines(raw: bytes, code: str, ledger: types.ModuleType) -> List[str]:
    return _ledger_call(ledger, ledger._decode_lines, code, raw, code)


def _validate_scalar_prefix(lines: Sequence[str], ledger: types.ModuleType,
                            scalar: types.ModuleType,
                            graph_reader: types.ModuleType) -> Dict[str, Any]:
    return _graph_call(
        graph_reader, graph_reader._validate_scalar_prefix,
        "SCALAR_PREFIX_INVALID", lines, ledger, scalar)


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                   stderr_raw: bytes,
                                   ledger: types.ModuleType,
                                   scalar: types.ModuleType,
                                   graph_reader: types.ModuleType
                                   ) -> Tuple[List[str], List[str], List[str],
                                              Dict[str, Any]]:
    console = _decode_lines(stdout_raw, "RUN_STDOUT", ledger)
    receipt = _decode_lines(receipt_raw, "RUN_RECEIPT", ledger)
    stderr = _decode_lines(stderr_raw, "RUN_STDERR", ledger)
    _need(stderr_raw == b"", "RUN_STDERR_NOT_EMPTY")
    markers = tuple(scalar.RECEIPT_MARKERS[:7]) + (
        COST240_EVENT_MARKER, COST240_SUMMARY_PREFIX)
    _need(len(receipt) == len(markers) and all(receipt),
          "COST240_RECEIPT_COUNT_INVALID")
    for line, marker in zip(receipt, markers):
        if marker == COST240_SUMMARY_PREFIX:
            _need(line.startswith(marker) and SUMMARY_RE.fullmatch(line) is not None,
                  "COST240_SUMMARY_INVALID")
        else:
            _need(line.startswith(marker + " "),
                  "COST240_RECEIPT_MARKER_ORDER_INVALID")
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
    scalar_prefix = _validate_scalar_prefix(
        receipt[:7], ledger, scalar, graph_reader)
    return console, receipt, stderr, scalar_prefix


def _parse_tagged(line: str, marker: str, code: str,
                  ledger: types.ModuleType) -> Any:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix),
          code + "_MARKER_INVALID")
    return _ledger_call(ledger, ledger._strict_json, code + "_JSON_INVALID",
                        line[len(prefix):].encode("utf-8"), code)


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


def _validate_source_graphspec(spec: Mapping[str, Any],
                               graphspec_raw: bytes,
                               source_spec_path: Path,
                               source_spec_raw: bytes,
                               exporter: types.ModuleType,
                               ledger: types.ModuleType,
                               quality: types.ModuleType
                               ) -> Tuple[Dict[str, Any], bytes, Dict[str, Any]]:
    _need(_sha256(source_spec_raw) == SOURCE_GRAPH_INPUT_SPEC_SHA256,
          "SOURCE_GRAPH_INPUT_SPEC_SHA256_MISMATCH")
    _need(0 < len(graphspec_raw) <= MAX_GRAPH_SPEC_BYTES and
          _sha256(graphspec_raw) == SOURCE_GRAPH_SPEC_SHA256 ==
          spec["graphspecSha256"],
          "SOURCE_GRAPH_SPEC_SHA256_MISMATCH")
    graphspec = _ledger_call(ledger, ledger._strict_json,
                             "SOURCE_GRAPH_SPEC_JSON_INVALID",
                             graphspec_raw, "SOURCE_GRAPH_SPEC")
    _need(type(graphspec) is dict and
          graphspec.get("schema") == "tsj-load12-source-graphspec-v1" and
          graphspec.get("status") == "PASS" and
          graphspec.get("sourceCap") == MAX_VIAS and
          graphspec.get("scope") == "SOURCE_CAP48_LOAD12_REFERENCE_ONLY",
          "SOURCE_GRAPH_SPEC_IDENTITY_INVALID")
    evidence = graphspec.get("sourceEvidence")
    _need(type(evidence) is dict and
          type(evidence.get("readerAndHelperSha256")) is dict and
          evidence.get("inputSpecSha256") == SOURCE_GRAPH_INPUT_SPEC_SHA256 and
          evidence.get("inputPinsSha256") == SOURCE_GRAPH_PINS_SHA256 and
          evidence.get("inputCount") == SOURCE_INPUT_COUNT and
          evidence.get("inputBytes") == 86_847_809 and
          evidence["readerAndHelperSha256"].get("currentReader") ==
          QUALITY_READER_SHA256 and
          evidence.get("exporterSha256") == GRAPH_SPEC_EXPORTER_SHA256,
          "SOURCE_GRAPH_SPEC_EVIDENCE_INVALID")
    source_graph = graphspec.get("sourceGraph")
    _need(type(source_graph) is dict and
          type(source_graph.get("event")) is dict and
          type(source_graph["event"].get("diagnostic")) is dict,
          "SOURCE_GRAPH_SPEC_MASKS_INVALID")
    masks = source_graph["event"]["diagnostic"].get("masks")
    _need(type(masks) is dict and
          set(masks) == {"topStepsHex", "bottomStepsHex", "goalFacesHex"},
          "SOURCE_GRAPH_SPEC_MASKS_INVALID")

    try:
        recomputed, canonical = exporter.build_verified_graphspec(
            os.fspath(source_spec_path), SOURCE_GRAPH_INPUT_SPEC_SHA256)
    except Exception as error:
        _fail(_safe_foreign_code(error, "SOURCE_GRAPH_RECOMPUTE_FAILED"))
    expected_canonical = _strict_canonical_json(recomputed) + b"\n"
    _need(type(recomputed) is dict and type(canonical) is bytes and
          canonical == expected_canonical == graphspec_raw and
          recomputed == graphspec and
          _sha256(canonical) == SOURCE_GRAPH_SPEC_SHA256,
          "SOURCE_GRAPH_SPEC_RECOMPUTE_MISMATCH")
    source_pre = _quality_call(
        quality, quality._validate_load12_pre, "SOURCE_GRAPH_PRE_INVALID",
        graphspec["load12Pre"], expected_maximum_vias=MAX_VIAS)
    _need(source_pre["path"]["tupleSha256"] == SOURCE_GRAPH_PATH_SHA256 and
          source_pre["path"]["goal"]["faceBitsSha256"] == SOURCE_GOAL_SHA256 and
          graphspec["prefixGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256,
          "SOURCE_GRAPH_SPEC_ANCHORS_INVALID")
    return graphspec, canonical, source_pre


def _java_length(value: str) -> int:
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError:
        _fail("COST240_SOURCE_TEXT_INVALID")


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


def _primary_layer_name_ordinal(value: Any, code: str) -> int:
    _need(type(value) is str and value in ("TOP", "BOTTOM"), code)
    return 0 if value == "TOP" else 1


def _primary_layer_ordinal(raw_pre: Mapping[str, Any], code: str) -> int:
    _need(type(raw_pre) is dict and type(raw_pre.get("source")) is dict and
          type(raw_pre["source"].get("grid")) is dict,
          code + "_SOURCE_GRID_INVALID")
    return _primary_layer_name_ordinal(
        raw_pre["source"]["grid"].get("primaryLayer"),
        code + "_PRIMARY_LAYER_INVALID")


def _validate_initial_capture(value: Any,
                              expected_pre: Mapping[str, Any],
                              code: str) -> Dict[str, Any]:
    _exact_keys(value, INITIAL_KEYS, code + "_KEYS_INVALID")
    raw = expected_pre["pre"]
    prefix_rows = expected_pre["prefix"]["inventory"]["rows"]
    summary_rows = [{"id": row["id"], "fingerprint": row["fingerprint"]}
                    for row in prefix_rows]
    expected = {
        "seeds": raw["seeds"],
        "netOrder": expected_pre["source"]["netOrder"],
        "netOrderSha256": _net_order_sha256(expected_pre["source"]["netOrder"]),
        "prefixRows": summary_rows,
        "prefixRowsSha256": _prefix_rows_sha256(summary_rows),
        "preGeometrySha256": raw["prefixInventory"]["stability"][
            "prefixGeometrySha256"],
        "prefix": {
            "holeCount": len(prefix_rows),
            "viaCount": sum(row["kind"] == "VIA" for row in prefix_rows),
            "traceCount": len(expected_pre["prefixTraces"]),
        },
    }
    _exact_keys(value["prefix"], PREFIX_SUMMARY_KEYS,
                code + "_PREFIX_KEYS_INVALID")
    _need(type(value["prefixRows"]) is list and
          all(type(row) is dict and set(row) == INITIAL_PREFIX_ROW_KEYS
              for row in value["prefixRows"]) and
          value == expected and
          value["preGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256 and
          value["prefix"] == {"holeCount": 17, "viaCount": 17,
                              "traceCount": 88},
          code + "_IDENTITY_INVALID")
    return dict(value)


def _validate_selector(value: Any, graphspec: Mapping[str, Any],
                       clone_pre: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, SELECTOR_KEYS, "COST240_SELECTOR_KEYS_INVALID")
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
        "pass": 1,
        "netPosition": 3,
        "nextFetch": 4,
        "branchOrdinal": 73,
        "searchBranches": 73,
        "netId": "LOAD12",
        "primaryLayer": "BOTTOM",
        "startPadId": "JLOAD.1",
        "rootPadId": "CSTORE_E.1",
        "grid": {"width": 478, "height": 309, "cells": 147702,
                 "minX": 30, "minY": 30, "pitch": 10},
        "prefix": {"vias": 17, "traces": 88},
    }
    _need(value == expected,
          "COST240_SELECTOR_LINEAGE_INVALID")
    graph_selection = graphspec["selection"]
    _need(graph_selection == {
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
        "maximumTransitions": MAX_TRANSITIONS,
        "prefixHoleCount": 17,
        "prefixTraceCount": 88,
    }, "COST240_SOURCE_GRAPH_SELECTION_MISMATCH")
    graph_primary_layer = _primary_layer_ordinal(
        graphspec["load12Pre"], "COST240_GRAPH_PRIMARY_LAYER")
    clone_primary_layer = _primary_layer_ordinal(
        clone_pre["pre"], "COST240_CLONE_PRIMARY_LAYER")
    selector_primary_layer = _primary_layer_name_ordinal(
        value["primaryLayer"], "COST240_SELECTOR_PRIMARY_LAYER_INVALID")
    _need(graph_primary_layer == clone_primary_layer == selector_primary_layer,
          "COST240_PRIMARY_LAYER_BINDING_MISMATCH")
    raw_source = clone_pre["pre"]["source"]
    grid = raw_source["grid"]
    _need(raw_source["pass"] == value["pass"] and
          raw_source["branchOrdinal"] == value["branchOrdinal"] and
          raw_source["netId"] == value["netId"] and
          raw_source["netPosition"] == value["netPosition"] and
          raw_source["netIndexNextFetch"] == value["nextFetch"] and
          raw_source["startPadId"] == value["startPadId"] and
          raw_source["rootPadId"] == value["rootPadId"] and
          raw_source["policy"] == "FULLER_TWO_LAYER" and
          grid["primaryLayer"] == value["primaryLayer"] and
          grid["maximumVias"] == MAX_VIAS and
          grid["maximumTransitions"] == MAX_TRANSITIONS and
          {key: grid[key] for key in ("width", "height", "cells", "minX", "minY")} ==
          {key: value["grid"][key]
           for key in ("width", "height", "cells", "minX", "minY")},
          "COST240_SELECTOR_SOURCE_BINDING_INVALID")
    graph_grid = graphspec["grid"]
    _need(graph_grid["width"] == value["grid"]["width"] and
          graph_grid["height"] == value["grid"]["height"] and
          graph_grid["cells"] == value["grid"]["cells"] and
          graph_grid["originX"] == value["grid"]["minX"] and
          graph_grid["originY"] == value["grid"]["minY"] and
          graph_grid["pitch"] == value["grid"]["pitch"] and
          graphspec["initialIdentities"]["startPadId"] == value["startPadId"] and
          graphspec["initialIdentities"]["rootPadId"] == value["rootPadId"],
          "COST240_SELECTOR_GRAPH_GEOMETRY_INVALID")
    _exact_keys(value["grid"], GRID_KEYS, "COST240_SELECTOR_GRID_KEYS_INVALID")
    _exact_keys(value["prefix"], PREFIX_KEYS,
                "COST240_SELECTOR_PREFIX_KEYS_INVALID")
    return dict(value)


def _validate_source_clone_settings(source: Any, clone: Any) -> None:
    _exact_keys(source, SOURCE_KEYS, "COST240_SOURCE_KEYS_INVALID")
    _exact_keys(clone, CLONE_KEYS, "COST240_CLONE_KEYS_INVALID")
    _need(_integer(source["sessionMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(source["searchMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(source["sessionViaCost"], SOURCE_VIA_COST, SOURCE_VIA_COST) and
          _integer(source["searchViaCost"], SOURCE_VIA_COST, SOURCE_VIA_COST) and
          _integer(clone["requestedMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(clone["requestedViaCost"], DETACHED_VIA_COST,
                   DETACHED_VIA_COST) and
          _integer(clone["sessionMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(clone["searchMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(clone["sessionViaCost"], DETACHED_VIA_COST,
                   DETACHED_VIA_COST) and
          _integer(clone["searchViaCost"], DETACHED_VIA_COST,
                   DETACHED_VIA_COST) and
          _integer(clone["maximumTransitions"], MAX_TRANSITIONS,
                   MAX_TRANSITIONS),
          "COST240_SOURCE_OR_CLONE_CAP_COST_INVALID")


def _validate_source_after_replay(value: Any,
                                  source_initial: Mapping[str, Any]
                                  ) -> Dict[str, Any]:
    _exact_keys(value, SOURCE_AFTER_KEYS,
                "COST240_SOURCE_AFTER_REPLAY_KEYS_INVALID")
    frontier = value["frontier"]
    _exact_keys(frontier, FRONTIER_KEYS,
                "COST240_SOURCE_FRONTIER_KEYS_INVALID")
    _need(_integer(value["sessionMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(value["searchMaximumVias"], MAX_VIAS, MAX_VIAS) and
          _integer(value["sessionViaCost"], SOURCE_VIA_COST, SOURCE_VIA_COST) and
          _integer(value["searchViaCost"], SOURCE_VIA_COST, SOURCE_VIA_COST) and
          _integer(value["branchExpanded"], 0, 0) and
          _integer(value["holeCount"], 17, 17) and
          _integer(value["traceCount"], 88, 88) and
          _integer(frontier["queueSize"], 2, 2) and
          _integer(frontier["bestSize"], 2, 2) and
          frontier["seeds"] == source_initial["seeds"],
          "COST240_SOURCE_AFTER_REPLAY_STATE_INVALID")
    return dict(value)


def _validate_cost_recurrence(nodes: Sequence[Mapping[str, Any]],
                              via_cost: int,
                              primary_layer: int,
                              code: str = "COST240_COST_RECURRENCE") -> int:
    """Recompute all g-costs from the independent Search charging contract."""
    _need(_integer(via_cost, MIN_SEARCH_VIA_COST, DETACHED_VIA_COST) and
          _integer(primary_layer, 0, 1) and
          type(nodes) in (list, tuple) and 2 <= len(nodes) <= 4096,
          code + "_INPUT_INVALID")
    first = nodes[0]
    _need(_integer(first.get("layer"), 0, 1) and
          _integer(first.get("cost"), 0, MAX_SCALAR) and
          first["cost"] == (0 if first["layer"] == primary_layer else 2) and
          first.get("direction") == 4 and first.get("transitions") == 0,
          code + "_SEED_COST_INVALID")
    expected_cost = first["cost"]
    for before, after in zip(nodes, nodes[1:]):
        _need(_integer(before.get("layer"), 0, 1) and
              _integer(after.get("layer"), 0, 1) and
              _integer(before.get("cost"), 0, MAX_SCALAR) and
              _integer(after.get("cost"), 0, MAX_SCALAR) and
              _integer(before.get("direction"), 0, 4) and
              _integer(after.get("direction"), 0, 4) and
              _integer(before.get("transitions"), 0, MAX_TRANSITIONS) and
              _integer(after.get("transitions"), 0, MAX_TRANSITIONS),
              code + "_NODE_DOMAIN_INVALID")
        expected_cost = before["cost"]
        if before["layer"] != after["layer"]:
            _need(before.get("cell") == after.get("cell") and
                  before.get("x") == after.get("x") and
                  before.get("y") == after.get("y") and
                  after["layer"] == 1 - before["layer"] and
                  before["direction"] != 4 and after["direction"] == 4 and
                  after["transitions"] == before["transitions"] + 1,
                  code + "_FLIP_INVALID")
            expected_cost += via_cost
        else:
            dx = after.get("x") - before.get("x")
            dy = after.get("y") - before.get("y")
            directions = {(0, -10): 0, (10, 0): 1,
                           (0, 10): 2, (-10, 0): 3}
            direction = directions.get((dx, dy))
            _need(direction is not None and
                  after["direction"] == direction and
                  after["transitions"] == before["transitions"],
                  code + "_PLANAR_EDGE_INVALID")
            expected_cost += 10 + (2 if after["layer"] != primary_layer else 0)
            if before["direction"] != 4 and before["direction"] != direction:
                expected_cost += 35
        _need(after["cost"] == expected_cost,
              code + "_NODE_COST_MISMATCH")
    return expected_cost


def _validate_limits(value: Any) -> Dict[str, Any]:
    _exact_keys(value, LIMIT_KEYS, "COST240_LIMIT_KEYS_INVALID")
    expected = {
        "operationMs": MAX_ACTIVE_MS,
        "workUnits": MAX_WORK_UNITS,
        "unitMs": MAX_UNIT_MS,
        "cleanupMs": MAX_CLEANUP_MS,
        "quantum": MAX_WORK_QUANTUM,
        "maximumSourceVias": MAX_VIAS,
        "maximumDetachedVias": MAX_VIAS,
        "sourceViaCost": SOURCE_VIA_COST,
        "detachedViaCost": DETACHED_VIA_COST,
        "maximumTransitions": MAX_TRANSITIONS,
    }
    _need(value == expected, "COST240_LIMIT_VALUES_INVALID")
    return dict(value)


def _validate_clone_path_summary(value: Any,
                                 clone_pre: Mapping[str, Any]
                                 ) -> Dict[str, Any]:
    _exact_keys(value, CLONE_PATH_KEYS,
                "COST240_CLONE_PATH_SUMMARY_KEYS_INVALID")
    path = clone_pre["path"]
    expected = {
        "pathTupleSha256": path["tupleSha256"],
        "goalFaceBitsSha256": path["goal"]["faceBitsSha256"],
        "nodeCount": len(path["nodes"]),
        "planarLength": path["length"],
        "bends": path["bends"],
        "flips": path["transitions"],
        "transitionCount": path["transitions"],
        "endCost": path["nodes"][-1]["cost"],
    }
    _need(value == expected,
          "COST240_CLONE_PATH_SUMMARY_MISMATCH")
    return dict(value)


def _validate_normal_publisher(pre: Mapping[str, Any],
                               post: Mapping[str, Any],
                               publisher: Any) -> Dict[str, Any]:
    _exact_keys(publisher, PUBLISHER_KEYS,
                "COST240_NORMAL_PUBLISHER_KEYS_INVALID")
    pre_rows = pre["prefix"]["inventory"]["rows"]
    post_rows = post["rows"]
    created_rows = post["createdTraceRows"]
    post_inventory = post["inventoryObject"]
    pre_vias = sum(row["kind"] == "VIA" for row in pre_rows)
    post_vias = sum(row["kind"] == "VIA" for row in post_rows)
    created_ids = set(post["createdHoleIds"])
    created_vias = sum(row["kind"] == "VIA" and row["id"] in created_ids
                       for row in post_rows)
    expected = {
        "completed": True,
        "stage": 6,
        "replayComplete": True,
        "preHoleCount": len(pre_rows),
        "preViaCount": pre_vias,
        "preTraceCount": len(pre["prefixTraces"]),
        "postHoleCount": len(post_rows),
        "postViaCount": post_vias,
        "postTraceCount": len(pre["prefixTraces"]) + len(created_rows),
        "createdHoleCount": post_inventory["createdHoleCount"],
        "createdViaCount": created_vias,
        "createdTraceCount": len(created_rows),
    }
    prefix_by_id = {row["id"]: row for row in pre_rows}
    post_by_id = {row["id"]: row for row in post_rows}
    _need(all(post_by_id.get(hole_id) is not None and
              post_by_id[hole_id]["fingerprint"] == row["fingerprint"]
              for hole_id, row in prefix_by_id.items()) and
          publisher == expected and
          post["source"]["publisherStage"] == 6 and
          len(created_rows) == len(pre["path"]["planarRuns"]) and
          len(post["transitionNodes"]) == pre["path"]["transitions"],
          "COST240_NORMAL_PUBLISHER_BINDING_INVALID")
    return {
        "stage": 6,
        "prefixRowsRetained": True,
        "createdVias": created_vias,
        "createdTraces": len(created_rows),
        "pathTransitions": pre["path"]["transitions"],
    }


def _path_summary(pre: Mapping[str, Any]) -> Dict[str, Any]:
    path = pre["path"]
    return {
        "pathTupleSha256": path["tupleSha256"],
        "goalFaceBitsSha256": path["goal"]["faceBitsSha256"],
        "nodeCount": len(path["nodes"]),
        "planarLength": path["length"],
        "bends": path["bends"],
        "flips": path["transitions"],
        "transitionCount": path["transitions"],
        "endCost": path["nodes"][-1]["cost"],
    }


def _validate_original_source(value: Any,
                              graphspec: Mapping[str, Any],
                              source_pre: Mapping[str, Any],
                              quality: types.ModuleType,
                              graph_reader: types.ModuleType
                              ) -> Dict[str, Any]:
    _exact_keys(value, ORIGINAL_KEYS, "COST240_ORIGINAL_KEYS_INVALID")
    original_pre = _quality_call(
        quality, quality._validate_load12_pre, "COST240_ORIGINAL_PRE_INVALID",
        value["preResult"], expected_maximum_vias=MAX_VIAS)
    original_post = _quality_call(
        quality, quality._validate_load12_post, "COST240_ORIGINAL_POST_INVALID",
        value["postResult"], original_pre)
    _need(original_pre["source"]["source"] == source_pre["source"]["source"] and
          original_pre["source"]["grid"] == source_pre["source"]["grid"] and
          original_pre["pre"]["seeds"] == source_pre["pre"]["seeds"] and
          original_pre["pre"]["goal"] == source_pre["pre"]["goal"] and
          original_pre["prefix"]["inventory"]["rows"] ==
          source_pre["prefix"]["inventory"]["rows"] and
          original_pre["pre"]["prefixInventory"]["stability"][
              "prefixGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256 and
          original_pre["prefixTraces"] == source_pre["prefixTraces"] and
          original_pre["path"] == source_pre["path"],
          "COST240_ORIGINAL_PRE_SOURCE_BINDING_INVALID")
    _need(original_pre["pre"]["nativePublisher"]["stage"] == 1,
          "COST240_ORIGINAL_PRE_NOT_AT_SEARCH_BOUNDARY")
    graph_primary_layer = _primary_layer_ordinal(
        graphspec["load12Pre"], "COST240_GRAPH_PRIMARY_LAYER")
    source_primary_layer = _primary_layer_ordinal(
        source_pre["pre"], "COST240_SOURCE_GRAPH_PRIMARY_LAYER")
    original_primary_layer = _primary_layer_ordinal(
        original_pre["pre"], "COST240_ORIGINAL_PRIMARY_LAYER")
    _need(graph_primary_layer == source_primary_layer == original_primary_layer,
          "COST240_ORIGINAL_PRIMARY_LAYER_BINDING_MISMATCH")
    source_end_cost = _validate_cost_recurrence(
        original_pre["path"]["nodes"], SOURCE_VIA_COST,
        original_primary_layer, "COST240_SOURCE_COST")
    graph_edges = _graph_call(
        graph_reader, graph_reader._validate_graphspec_path_edges,
        "COST240_SOURCE_GRAPH_EDGE_CHECK_FAILED", original_pre, graphspec)
    _need(graph_edges["transitions"] == original_pre["path"]["transitions"],
          "COST240_ORIGINAL_GRAPH_EDGE_COUNT_INVALID")

    path = value["path"]
    _exact_keys(path, ORIGINAL_PATH_KEYS,
                "COST240_ORIGINAL_PATH_KEYS_INVALID")
    expected_path = _path_summary(original_pre)
    _need(path == expected_path and
          path["pathTupleSha256"] == graphspec["sourcePath"]["tupleSha256"] ==
          SOURCE_GRAPH_PATH_SHA256 and
          path["goalFaceBitsSha256"] == SOURCE_GOAL_SHA256 and
          path["nodeCount"] == graphspec["sourcePath"]["nodeCount"] and
          path["planarLength"] == graphspec["sourcePath"]["length"] and
          path["bends"] == graphspec["sourcePath"]["bends"] and
          path["flips"] == graphspec["sourcePath"]["transitions"],
          "COST240_ORIGINAL_PATH_SOURCE_GRAPH_MISMATCH")

    prefix_rows = original_pre["prefix"]["inventory"]["rows"]
    pre_counts = {
        "holeCount": len(prefix_rows),
        "viaCount": sum(row["kind"] == "VIA" for row in prefix_rows),
        "traceCount": len(original_pre["prefixTraces"]),
    }
    _exact_keys(value["pre"], ORIGINAL_PRE_KEYS,
                "COST240_ORIGINAL_PRE_COUNTS_KEYS_INVALID")
    _need(value["pre"] == pre_counts == {
        "holeCount": 17, "viaCount": 17, "traceCount": 88},
        "COST240_ORIGINAL_PRE_COUNTS_INVALID")
    _exact_keys(value["post"], ORIGINAL_POST_KEYS,
                "COST240_ORIGINAL_POST_COUNTS_KEYS_INVALID")
    post_rows = original_post["rows"]
    created_ids = set(original_post["createdHoleIds"])
    created_via_count = sum(1 for row in post_rows
                            if row["id"] in created_ids and row["kind"] == "VIA")
    created_trace_count = len(original_post["createdTraceRows"])
    post_counts = {
        "holeCount": len(post_rows),
        "viaCount": sum(row["kind"] == "VIA" for row in post_rows),
        "traceCount": pre_counts["traceCount"] + created_trace_count,
        "createdViaCount": created_via_count,
        "createdTraceCount": created_trace_count,
    }
    _need(value["post"] == post_counts and
          post_counts == {"holeCount": 21, "viaCount": 21,
                          "traceCount": 93, "createdViaCount": 4,
                          "createdTraceCount": 5} and
          created_via_count == len(created_ids) ==
          original_pre["path"]["transitions"] and
          original_post["source"]["preHoleCount"] == pre_counts["holeCount"] and
          original_post["source"]["preTraceCount"] == pre_counts["traceCount"],
          "COST240_ORIGINAL_POST_COUNTS_OR_RESUME_INVALID")
    for field in ("preGeometrySha256", "postGeometrySha256",
                  "publicationSha256"):
        _need(type(value[field]) is str and
              SHA256_RE.fullmatch(value[field]) is not None,
              "COST240_ORIGINAL_DIGEST_INVALID")
    _need(value["preGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256 ==
          graphspec["prefixGeometrySha256"] ==
          original_pre["pre"]["prefixInventory"]["stability"][
              "prefixGeometrySha256"],
          "COST240_ORIGINAL_PREFIX_GEOMETRY_MISMATCH")
    _need(path["endCost"] == source_end_cost,
          "COST240_SOURCE_END_COST_MISMATCH")
    return {"value": dict(value), "pre": original_pre,
            "post": original_post, "preCounts": pre_counts,
            "postCounts": post_counts, "graphEdges": graph_edges,
            "primaryLayer": original_primary_layer,
            "endCost": source_end_cost}


def _validate_work(value: Any, pre: Mapping[str, Any],
                   post: Mapping[str, Any]) -> Dict[str, Any]:
    _exact_keys(value, WORK_KEYS, "COST240_WORK_KEYS_INVALID")
    source_operation = value["sourceOperation"]
    _exact_keys(source_operation, SOURCE_OPERATION_KEYS,
                "COST240_SOURCE_OPERATION_KEYS_INVALID")
    _need(_integer(source_operation["operationMs"], 1, MAX_ACTIVE_MS) and
          _integer(source_operation["workUnits"], 1, MAX_WORK_UNITS) and
          _integer(source_operation["maxUnitMs"], 0, MAX_UNIT_MS) and
          _integer(source_operation["cleanupMs"], 0, MAX_CLEANUP_MS) and
          _integer(source_operation["manualGroups"], 0,
                   source_operation["workUnits"]) and
          _integer(source_operation["manualSliceSteps"], 0, MAX_TOTAL_WORK) and
          source_operation["manualGroups"] <=
          source_operation["manualSliceSteps"] and
          source_operation["manualSliceSteps"] <=
          source_operation["workUnits"] * MAX_WORK_QUANTUM,
          "COST240_SOURCE_OPERATION_BUDGET_OR_CONSERVATION_INVALID")

    copy_work = value["copy"]
    _exact_keys(copy_work, COPY_WORK_KEYS, "COST240_COPY_WORK_KEYS_INVALID")
    _need(_integer(copy_work["advanceCalls"], 1, MAX_WORK_UNITS) and
          _integer(copy_work["workSteps"], 1, MAX_TOTAL_WORK) and
          _integer(copy_work["copiedEntries"], 0, copy_work["workSteps"]) and
          _integer(copy_work["lastWork"], 0, MAX_WORK_QUANTUM) and
          copy_work["lastWork"] <= copy_work["workSteps"] and
          _integer(copy_work["observerChecks"], 0, copy_work["workSteps"]) and
          copy_work["workSteps"] <=
          copy_work["advanceCalls"] * MAX_WORK_QUANTUM,
          "COST240_COPY_WORK_INVALID")
    replay = value["replay"]
    _exact_keys(replay, REPLAY_WORK_KEYS, "COST240_REPLAY_WORK_KEYS_INVALID")
    _need(_integer(replay["toPreCalls"], 1, MAX_TOTAL_WORK) and
          _integer(replay["toPreWork"], replay["toPreCalls"], MAX_TOTAL_WORK) and
          _integer(replay["toPostCalls"], 1, MAX_TOTAL_WORK) and
          _integer(replay["toPostWork"], replay["toPostCalls"], MAX_TOTAL_WORK),
          "COST240_REPLAY_WORK_INVALID")

    clone_cursor = value["cloneCursor"]
    source_cursor = value["sourceCursor"]
    for cursor, label in ((clone_cursor, "CLONE"), (source_cursor, "SOURCE")):
        _exact_keys(cursor, CURSOR_WORK_KEYS,
                    "COST240_" + label + "_CURSOR_KEYS_INVALID")
        _need(_integer(cursor["preAdvanceCalls"], 1, MAX_WORK_UNITS) and
              _integer(cursor["preWork"], 0, MAX_TOTAL_WORK) and
              _integer(cursor["postAdvanceCalls"], 1, MAX_WORK_UNITS) and
              _integer(cursor["postWork"], 0, MAX_TOTAL_WORK),
              "COST240_" + label + "_CURSOR_WORK_INVALID")
    _need(clone_cursor["preAdvanceCalls"] == pre["work"]["advanceCalls"] and
          clone_cursor["preWork"] <= pre["work"]["workSteps"] and
          clone_cursor["postAdvanceCalls"] == post["work"]["advanceCalls"] and
          clone_cursor["postWork"] <= post["work"]["workSteps"],
          "COST240_CLONE_CURSOR_RESULT_MISMATCH")
    outcome = value["sourceOutcome"]
    _exact_keys(outcome, SOURCE_OUTCOME_KEYS,
                "COST240_SOURCE_OUTCOME_KEYS_INVALID")
    _need(outcome == {
        "phase": "COMPLETE", "qualityRejected": True,
        "targetRequestAccounted": True, "sourceResumed": True,
    }, "COST240_SOURCE_RESUME_OUTCOME_INVALID")
    return {"sourceOperation": dict(source_operation), "copy": dict(copy_work),
            "replay": dict(replay), "cloneCursor": dict(clone_cursor),
            "sourceCursor": dict(source_cursor), "sourceOutcome": dict(outcome)}


def _validate_comparisons(value: Any,
                          source_initial: Mapping[str, Any],
                          clone_initial: Mapping[str, Any],
                          source_pre: Mapping[str, Any],
                          clone_pre: Mapping[str, Any],
                          source_after: Mapping[str, Any],
                          clone_post: Mapping[str, Any],
                          work: Mapping[str, Any],
                          original: Mapping[str, Any],
                          source_cost_ok: bool,
                          clone_cost_ok: bool
                          ) -> Dict[str, Any]:
    _exact_keys(value, COMPARISON_KEYS, "COST240_COMPARISON_KEYS_INVALID")
    _need(all(type(flag) is bool for flag in value.values()),
          "COST240_COMPARISON_TYPES_INVALID")
    source_raw = source_pre["pre"]
    clone_raw = clone_pre["pre"]
    prefix_rows = clone_pre["prefix"]["inventory"]["rows"]
    post_rows = clone_post["rows"]
    original_checked = original["pre"]
    original_post = original["post"]
    prefix_by_id = {row["id"]: row for row in prefix_rows}
    post_by_id = {row["id"]: row for row in post_rows}
    source_preserved = (
        source_after["sessionMaximumVias"] ==
        source_after["searchMaximumVias"] ==
        original_checked["source"]["source"]["grid"]["maximumVias"] == MAX_VIAS and
        source_after["sessionViaCost"] ==
        source_after["searchViaCost"] == SOURCE_VIA_COST and
        source_after["branchExpanded"] == 0 and
        source_after["holeCount"] == original["preCounts"]["holeCount"] == 17 and
        source_after["traceCount"] == original["preCounts"]["traceCount"] == 88 and
        source_after["frontier"]["seeds"] == source_initial["seeds"] ==
        original_checked["pre"]["seeds"])
    original_source = original_post["source"]
    source_resumed = (
        original_checked["path"]["publisher"]["stage"] == 1 and
        original_source["pass"] == 1 and
        original_source["publisherStage"] == 6 and
        original_source["searchBranchIsNull"] is True and
        original_source["searchPublisherIsNull"] is True and
        original_source["preHoleCount"] == original["preCounts"]["holeCount"] and
        original_source["preTraceCount"] == original["preCounts"]["traceCount"] and
        work["sourceOutcome"]["phase"] == "COMPLETE" and
        work["sourceOutcome"]["qualityRejected"] is True and
        work["sourceOutcome"]["targetRequestAccounted"] is True and
        work["sourceOutcome"]["sourceResumed"] is True)
    computed = {
        "initialSeedsEqual": source_initial["seeds"] == clone_initial["seeds"] and
            source_initial["seeds"] == source_raw["seeds"] == clone_raw["seeds"],
        "initialNetOrderEqual": source_initial["netOrder"] ==
            clone_initial["netOrder"] == source_raw["source"]["netOrder"] ==
            clone_raw["source"]["netOrder"],
        "initialPrefixRowsEqual": source_initial["prefixRows"] ==
            clone_initial["prefixRows"] and
            source_initial["prefixRows"] == [
                {"id": row["id"], "fingerprint": row["fingerprint"]}
                for row in source_raw["prefixInventory"]["rows"]] and
            source_initial["prefixRows"] == [
                {"id": row["id"], "fingerprint": row["fingerprint"]}
                for row in clone_raw["prefixInventory"]["rows"]],
        "initialPrefixGeometryEqual": source_initial["preGeometrySha256"] ==
            clone_initial["preGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256,
        "fullGoalFaceBitsEqual": source_raw["goal"]["faceBits"] ==
            clone_raw["goal"]["faceBits"],
        "pathTupleEqualObserved":
            original["value"]["path"]["pathTupleSha256"] ==
            _path_summary(clone_pre)["pathTupleSha256"],
        "postRowsEqual": (
            [row["id"] for row in original_post["rows"]] ==
            [row["id"] for row in clone_post["rows"]] and
            [row["fingerprint"] for row in original_post["rows"]] ==
            [row["fingerprint"] for row in clone_post["rows"]] and
            original_post["createdHoleIds"] == clone_post["createdHoleIds"]),
        "prefixRetained": all(
            post_by_id.get(hole_id) is not None and
            post_by_id[hole_id]["fingerprint"] == row["fingerprint"]
            for hole_id, row in prefix_by_id.items()),
        "sourcePreserved": source_preserved,
        "sourceResumed": source_resumed,
        "sourceCostRecurrenceVerified": source_cost_ok,
        "cloneCostRecurrenceVerified": clone_cost_ok,
    }
    _need(type(value["postGeometryEqualObserved"]) is bool and
          type(value["publicationEqualObserved"]) is bool,
          "COST240_COMPARISON_OBSERVATIONS_INVALID")
    for field, expected in computed.items():
        _need(value[field] is expected,
              "COST240_COMPARISON_DATA_MISMATCH")
    _need(work["sourceOutcome"]["sourceResumed"] is source_resumed,
          "COST240_SOURCE_OUTCOME_EVIDENCE_MISMATCH")
    # The event includes only the original geometry/publication digests and
    # the native comparison booleans. Keep those booleans visibly separate
    # from independently recomputed comparisons; the clone digest is absent.
    return {**computed,
            "producerReportedUnverified": {
                "postGeometryEqual": value["postGeometryEqualObserved"],
                "publicationEqual": value["publicationEqualObserved"],
            }}


def _validate_event(value: Any, graphspec: Mapping[str, Any],
                    source_pre: Mapping[str, Any],
                    quality: types.ModuleType,
                    graph_reader: types.ModuleType
                    ) -> Dict[str, Any]:
    _exact_keys(value, EVENT_KEYS, "COST240_EVENT_KEYS_INVALID")
    _need(value["schema"] == COST240_EVENT_SCHEMA and
          value["status"] == "PASS" and
          _integer(value["protocolVersion"], 1, 1) and
          type(value["runId"]) is str and RUN_ID_RE.fullmatch(value["runId"]) and
          value["operationId"] == "QUALITY_PROBE" and
          value["scope"] == COST240_SCOPE,
          "COST240_EVENT_IDENTITY_INVALID")
    _validate_source_clone_settings(value["source"], value["clone"])
    source = value["source"]
    clone = value["clone"]
    _validate_limits(value["limits"])

    clone_pre = _quality_call(
        quality, quality._validate_load12_pre, "COST240_CLONE_PRE_INVALID",
        clone["preResult"], expected_maximum_vias=MAX_VIAS)
    _need(clone_pre["pre"]["source"] == graphspec["load12Pre"]["source"] and
          clone_pre["pre"]["seeds"] == source_pre["pre"]["seeds"] and
          clone_pre["pre"]["prefixInventory"]["rows"] ==
          source_pre["pre"]["prefixInventory"]["rows"] and
          clone_pre["pre"]["prefixTraces"] == source_pre["pre"]["prefixTraces"] and
          clone_pre["pre"]["goal"] == source_pre["pre"]["goal"] and
          clone_pre["pre"]["prefixInventory"]["stability"][
              "prefixGeometrySha256"] == SOURCE_PREFIX_GEOMETRY_SHA256,
          "COST240_CLONE_SOURCE_PRE_BINDING_INVALID")
    selector = _validate_selector(value["selector"], graphspec, clone_pre)
    graph_primary_layer = _primary_layer_ordinal(
        graphspec["load12Pre"], "COST240_GRAPH_PRIMARY_LAYER")
    source_primary_layer = _primary_layer_ordinal(
        source_pre["pre"], "COST240_SOURCE_GRAPH_PRIMARY_LAYER")
    clone_primary_layer = _primary_layer_ordinal(
        clone_pre["pre"], "COST240_CLONE_PRIMARY_LAYER")
    selector_primary_layer = _primary_layer_name_ordinal(
        selector["primaryLayer"], "COST240_SELECTOR_PRIMARY_LAYER_INVALID")
    _need(graph_primary_layer == source_primary_layer == clone_primary_layer ==
          selector_primary_layer,
          "COST240_PRIMARY_LAYER_BINDING_MISMATCH")
    source_initial = _validate_initial_capture(
        source["initial"], source_pre, "COST240_SOURCE_INITIAL")
    clone_initial = _validate_initial_capture(
        clone["initial"], source_pre, "COST240_CLONE_INITIAL")
    _need(source_initial == clone_initial,
          "COST240_INITIAL_COPY_IDENTITY_MISMATCH")
    source_after = _validate_source_after_replay(
        source["afterReplayBeforeResume"], source_initial)

    original = _validate_original_source(
        value["original"], graphspec, source_pre, quality, graph_reader)
    _need(original["primaryLayer"] == graph_primary_layer,
          "COST240_ORIGINAL_PRIMARY_LAYER_MISMATCH")
    path_summary = _validate_clone_path_summary(clone["path"], clone_pre)
    clone_cost = _validate_cost_recurrence(
        clone_pre["path"]["nodes"], DETACHED_VIA_COST, clone_primary_layer,
        "COST240_CLONE_COST")
    _need(original["endCost"] ==
          original["pre"]["path"]["nodes"][-1]["cost"] and
          clone_cost == path_summary["endCost"],
          "COST240_END_COST_MISMATCH")
    graph_edges = _graph_call(
        graph_reader, graph_reader._validate_graphspec_path_edges,
        "COST240_CLONE_GRAPH_EDGE_CHECK_FAILED", clone_pre, graphspec)

    clone_post = _quality_call(
        quality, quality._validate_load12_post, "COST240_CLONE_POST_INVALID",
        clone["postResult"], clone_pre)
    publication = _validate_normal_publisher(
        clone_pre, clone_post, clone["normalPublisher"])
    work = _validate_work(value["work"], clone_pre, clone_post)
    comparison = _validate_comparisons(
        value["comparison"], source_initial, clone_initial,
        source_pre, clone_pre, source_after, clone_post, work, original,
        True, True)
    _need(value["branchRouteFound"] is True and
          value["fullBoardFeasibility"] == "UNKNOWN" and
          value["routeAdoption"] is False and
          value["layoutQualification"] is False and
          value["playable100"] is False,
          "COST240_SCOPE_OR_LIMITATION_CLAIMS_INVALID")
    _need(clone_post["claims"] == {
        "avoidability": "UNKNOWN", "chronologyProof": False,
        "routeAdoption": False, "layoutQualification": False,
        "playable100": 0,
    }, "COST240_CLONE_CLAIMS_INVALID")
    return {
        "schema": COST240_EVENT_SCHEMA,
        "status": "PASS",
        "runId": value["runId"],
        "operationId": value["operationId"],
        "sourceCap": MAX_VIAS,
        "sourceViaCost": SOURCE_VIA_COST,
        "detachedCap": MAX_VIAS,
        "detachedViaCost": DETACHED_VIA_COST,
        "selector": selector,
        "path": {
            "primaryLayer": selector["primaryLayer"],
            "primaryLayerOrdinal": clone_primary_layer,
            "nodeCount": clone_pre["path"]["path"]["nodeCount"],
            "planarLength": clone_pre["path"]["length"],
            "bends": clone_pre["path"]["bends"],
            "transitions": clone_pre["path"]["transitions"],
            "pathTupleSha256": clone_pre["path"]["tupleSha256"],
            "goalFaceBitsSha256": clone_pre["path"]["goal"]["faceBitsSha256"],
            "endpointCell": graph_edges["endpointCell"],
            "endpointLayer": graph_edges["endpointLayer"],
            "sourceMaskPlanarEdges": graph_edges["planarEdges"],
            "endCost": clone_cost,
        },
        "publication": publication,
        "comparison": comparison,
        "work": work,
        "claims": clone_post["claims"],
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
                               helper_hashes: Mapping[str, str],
                               graph_reader: types.ModuleType,
                               graph_reader_raw: bytes,
                               graph_reader_path: Path
                               ) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256),
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
    directory = Path(os.path.abspath(__file__)).parent
    exporter, exporter_raw, exporter_path = _load_graphspec_exporter(directory)
    graphspec, graphspec_canonical, source_pre = _validate_source_graphspec(
        spec, graphspec_raw, source_spec_path, source_spec_raw,
        exporter, ledger, quality)

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
    _console, receipt_lines, _stderr, scalar_prefix = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger, scalar, graph_reader)
    event = _parse_tagged(receipt_lines[7], COST240_EVENT_MARKER,
                          "COST240_EVENT", ledger)
    checked = _validate_event(event, graphspec, source_pre, quality, graph_reader)
    _need(event["runId"] == scalar_prefix["runId"],
          "COST240_SCALAR_RUN_ID_MISMATCH")
    summary_match = SUMMARY_RE.fullmatch(receipt_lines[8])
    _need(summary_match is not None and
          _integer(int(summary_match.group(1)), 1, MAX_SCALAR),
          "COST240_SUMMARY_ASSERTION_COUNT_INVALID")

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
             "GRAPH_SPEC_EXPORTER", False),
            (graph_reader_path, graph_reader_raw, MAX_READER_BYTES,
             "CAP17_PURE_READER", False)):
        again = _ledger_call(ledger, ledger._read_bounded,
                             code + "_REVALIDATION_FAILED", path, limit,
                             code, allow_empty=allow_empty)
        _need(again == expected, code + "_CHANGED_DURING_VALIDATION")
    _need(_sha256(graphspec_canonical) == SOURCE_GRAPH_SPEC_SHA256 and
          _sha256(exporter_raw) == GRAPH_SPEC_EXPORTER_SHA256 and
          _sha256(graph_reader_raw) == CAP17_PURE_READER_SHA256 and
          _sha256(_read_local(Path(os.path.abspath(__file__)),
                              MAX_READER_BYTES, "READER_SOURCE")) == own_sha,
          "COST240_SOURCE_OR_EXPORTER_CHANGED_DURING_VALIDATION")
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "target": TARGET,
        "purpose": PURPOSE,
        "runId": event["runId"],
        "nativeRunSha256": _sha256(result_raw),
        "inputSpecSha256": spec_sha256,
        "graphspecSha256": SOURCE_GRAPH_SPEC_SHA256,
        "sourceGraphInputSpecSha256": SOURCE_GRAPH_INPUT_SPEC_SHA256,
        "sourcePinsSha256": context["pinsSha256"],
        "sourceInputCount": context["inputCount"],
        "sourceInputBytes": context["inputBytes"],
        "sourceSnapshotRevision": context["sourceSnapshotRevision"],
        "command": dict(native_run["command"]),
        "suite": SUITE,
        "sourceCap": MAX_VIAS,
        "sourceViaCost": SOURCE_VIA_COST,
        "detachedCap": MAX_VIAS,
        "detachedViaCost": DETACHED_VIA_COST,
        "path": checked["path"],
        "publication": checked["publication"],
        "comparison": checked["comparison"],
        "work": checked["work"],
        "claims": checked["claims"],
        "limitations": checked["limitations"],
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
    graph_reader, graph_reader_raw, graph_reader_path = _load_pinned_pure_reader(
        own_path.parent)
    return _validate_native_artifacts(
        spec_argument, spec_sha256, own_sha, quality, frozen, scalar,
        ledger, capture, helper_hashes, graph_reader,
        graph_reader_raw, graph_reader_path)


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    _need(type(argv) in (list, tuple) and len(argv) == 4 and
          argv[0] == "--input-spec" and argv[2] == "--input-spec-sha256",
          "CLI_ARGUMENTS_INVALID")
    spec_path, spec_sha256 = argv[1], argv[3]
    _need(type(spec_path) is str and 0 < len(spec_path) <= MAX_PATH_CHARS and
          type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256),
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
    except Load12Cost240InputError as error:
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
