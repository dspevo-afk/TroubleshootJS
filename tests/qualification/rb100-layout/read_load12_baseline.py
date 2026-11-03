#!/usr/bin/env python3
"""Read an independently source-bound actual LOAD12 detached baseline.

This consumer verifies one observed same-cap48 copy/replay comparison. It does
not establish avoidability, route/layout adoption, electrical behavior, or a
playable-board qualification.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import re
import sys
import types
import uuid
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-load12-detached-baseline-input-v1"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
RESULT_SCHEMA = "p07-load12-detached-baseline-result-v1"
TARGET = "LOAD12"
PURPOSE = "DETACHED_BASELINE48"
SUITE = "Rb100Load12DetachedBaselineContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 20
SOURCE_INPUT_COUNT = 1079
SOURCE_JAVA_COUNT = 850
CONTRACT_JAVA_COUNT = 223
MAX_WORK_UNITS = 640
MAX_WORK_QUANTUM = 32_768
MAX_MANUAL_SLICES = MAX_WORK_UNITS * MAX_WORK_QUANTUM
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
RUN_ID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII,
)
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
PROCESS_TICKS_RE = re.compile(r"[1-9][0-9]{0,19}\Z", re.ASCII)

PINNED_SCALAR_READER_SHA256 = "0396ae2566454063c6c744fbeb49e68859edb9ef092a23b027e01a3977f52802"
MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
SOURCE_INPUT_LIMIT = 512 * 1024 * 1024
READER_LIMIT = 2 * 1024 * 1024

FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."
EVENT_MARKER = "RB100_LOAD12_DETACHED_BASELINE_EVENT"
SUMMARY_PREFIX = "PASS: RB100 LOAD12 detached baseline contracts assertions="
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")
RECEIPT_MARKERS = (
    "RB100_QUALITY_SCALAR_CONTROLS",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_QUALITY_SCALAR_EVENT",
    "RB100_QUALITY_SCALAR_PAIR",
    EVENT_MARKER,
    SUMMARY_PREFIX,
)
EXPECTED_PATH_TUPLE_SHA256 = "1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d"
EXPECTED_GOAL_SHA256 = "cc48299ed82e39f1bac10269e7c41afc6468f1e53fc96baa36a95d5c60d548d2"
EXPECTED_PRE_GEOMETRY_SHA256 = "7c963bb9ba10f7419d2e6abaecff4cfc1504ba2744914d6874ffd02ca475c447"

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

INPUT_SPEC_KEYS = frozenset((
    "schema target purpose suite sourceRoot pinsPath pinsSha256 inputCount inputBytes "
    "sourceSnapshotRevision resultPath stdoutPath receiptPath stderrPath"
).split())
LEGACY_INPUT_SPEC_KEYS = INPUT_SPEC_KEYS - frozenset(("target", "purpose", "suite"))

EVENT_KEYS = frozenset((
    "protocolVersion status runId operationId scope purpose selector original clone work comparison"
).split())
SELECTOR_KEYS = frozenset((
    "rootSeed placementSeed routingSeed routeRequestIndex selectedPlacementIndex "
    "selectedPlacementAttempt pass netPosition nextFetch netId branchOrdinal "
    "searchBranches primaryLayer grid startPadId rootPadId prefix"
).split())
GRID_KEYS = frozenset("width height cells minX minY pitch".split())
PREFIX_KEYS = frozenset("vias traces".split())
CAPTURE_KEYS = frozenset((
    "pathTupleSha256 goalSha256 nodeCount planarLength bends flips seedSha256 "
    "netOrderSha256 prefixRowsSha256 preGeometrySha256 postGeometrySha256 "
    "publicationSha256 pre post"
).split())
PRE_KEYS = frozenset("holeCount viaCount traceCount".split())
POST_KEYS = frozenset((
    "holeCount viaCount traceCount createdViaCount createdTraceCount"
).split())
WORK_KEYS = frozenset((
    "sourceOperation copy replay cloneCursor sourceCursor limits"
).split())
SOURCE_OPERATION_KEYS = frozenset((
    "operationMs workUnits maxUnitMs cleanupMs manualGroups manualSliceSteps "
    "preAdvanceCalls postAdvanceCalls"
).split())
COPY_WORK_KEYS = frozenset((
    "advanceCalls workSteps copiedEntries lastWork observerChecks"
).split())
REPLAY_WORK_KEYS = frozenset("toPreCalls toPreWork toPostCalls toPostWork".split())
CURSOR_WORK_KEYS = frozenset("preAdvanceCalls preWork postAdvanceCalls postWork".split())
WORK_LIMIT_KEYS = frozenset((
    "operationMs workUnits unitMs cleanupMs quantum maximumVias maximumTransitions"
).split())
COMPARISON_KEYS = frozenset((
    "pathEqual goalEqual metricsEqual seedEqual netOrderEqual prefixRowsEqual "
    "preGeometryEqual postGeometryEqual publicationEqual postRowsEqual "
    "sourcePreserved sourceResumed graphReceiptCreated routeAdoption "
    "layoutQualification playable100"
).split())
COMPARISON_TRUE_FIELDS = frozenset((
    "pathEqual goalEqual metricsEqual seedEqual netOrderEqual prefixRowsEqual "
    "preGeometryEqual postGeometryEqual publicationEqual postRowsEqual "
    "sourcePreserved sourceResumed"
).split())
COMPARISON_FALSE_FIELDS = frozenset((
    "graphReceiptCreated routeAdoption layoutQualification playable100"
).split())
EXPECTED_LINEAGE = {
    "rootSeed": ROOT_SEED,
    "placementSeed": PLACEMENT_SEED,
    "routingSeed": ROUTING_SEED,
    "routeRequestIndex": 0,
    "selectedPlacementIndex": 0,
    "selectedPlacementAttempt": 4,
    "pass": 1,
    "netPosition": 3,
    "nextFetch": 4,
    "netId": "LOAD12",
    "branchOrdinal": 73,
    "searchBranches": 73,
    "primaryLayer": "BOTTOM",
    "startPadId": "JLOAD.1",
    "rootPadId": "CSTORE_E.1",
}


class Load12BaselineInputError(ValueError):
    """Sanitized static failure code; input contents and paths are not echoed."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise Load12BaselineInputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _line_has_marker(line: str, marker: str) -> bool:
    return line.startswith(marker) if marker == SUMMARY_PREFIX else line.startswith(marker + " ")


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, "is_junction", None)
        if callable(is_junction) and is_junction():
            return True
        attributes = getattr(path.stat(follow_symlinks=False), "st_file_attributes", 0)
        return bool(attributes & 0x400)
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
        _need(path.is_file() and
              (0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit,
              code + "_READ_SIZE_CHANGED")
        return raw
    except Load12BaselineInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _safe_foreign_code(error: BaseException, fallback: str) -> str:
    code = getattr(error, "code", None)
    return code if type(code) is str and ERROR_CODE_RE.fullmatch(code) else fallback


def _resolve_path(value: Any, base: Path, code: str) -> Path:
    return _ledger_call(_ledger._resolve_path, code + "_PATH_UNAVAILABLE",
                        value, base, code)


def _read_bounded(path: Path, limit: int, code: str,
                  allow_empty: bool = False) -> bytes:
    return _ledger_call(_ledger._read_bounded, code + "_READ_FAILED",
                        path, limit, code, allow_empty=allow_empty)


def _strict_json(raw: bytes, label: str) -> Any:
    if "_ledger" in globals():
        return _ledger_call(_ledger._strict_json, label + "_JSON_INVALID", raw, label)
    try:
        return json.loads(raw.decode("utf-8-sig", errors="strict"),
                          object_pairs_hook=_reject_duplicate_keys,
                          parse_constant=_reject_constant)
    except Load12BaselineInputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError, ValueError, TypeError):
        _fail(label + "_JSON_INVALID")


def _reject_duplicate_keys(pairs: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            _fail("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _reject_constant(_value: str) -> None:
    _fail("JSON_NONFINITE_NUMBER")


def _ledger_call(function, fallback: str, *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except _ledger.LedgerInputError as error:
        _fail(_safe_foreign_code(error, fallback))
    except Load12BaselineInputError:
        raise
    except Exception:
        _fail(fallback)


def _scalar_call(function, fallback: str, *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except _scalar.QualityScalarInputError as error:
        _fail(_safe_foreign_code(error, fallback))
    except Load12BaselineInputError:
        raise
    except Exception:
        _fail(fallback)


def _validate_input_spec_identity(spec: Any) -> Dict[str, Any]:
    _exact_keys(spec, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    _need(spec["schema"] == INPUT_SCHEMA and
          spec["target"] == TARGET and spec["purpose"] == PURPOSE and
          spec["suite"] == SUITE and
          type(spec["sourceSnapshotRevision"]) is int and
          spec["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(spec["inputCount"]) is int and spec["inputCount"] == SOURCE_INPUT_COUNT and
          _integer(spec["inputBytes"], 1, SOURCE_INPUT_LIMIT) and
          type(spec["pinsSha256"]) is str and SHA256_RE.fullmatch(spec["pinsSha256"]) is not None,
          "INPUT_SPEC_IDENTITY_INVALID")
    return {key: spec[key] for key in LEGACY_INPUT_SPEC_KEYS}


def _exec_private(raw: bytes, path: Path, name: str) -> types.ModuleType:
    module = types.ModuleType(name)
    module.__file__ = os.fspath(path)
    module.__package__ = ""
    previous = sys.modules.get(name)
    had_previous = name in sys.modules
    sys.modules[name] = module
    try:
        source = raw.decode("utf-8", errors="strict")
        exec(compile(source, os.fspath(path), "exec", dont_inherit=True), module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("PINNED_HELPER_SOURCE_INVALID")
    finally:
        if had_previous:
            sys.modules[name] = previous
        else:
            sys.modules.pop(name, None)
    return module


def _load_pinned_helpers() -> Tuple[types.ModuleType, types.ModuleType,
                                    types.ModuleType, Dict[str, str]]:
    unresolved = Path(os.path.abspath(__file__))
    _need(not _has_reparse_component(unresolved), "READER_SOURCE_REPARSE_PATH")
    own_path = unresolved.resolve(strict=True)
    _need(not _is_reparse(own_path) and own_path.is_file(),
          "READER_SOURCE_INVALID")
    own_raw = _read_local(own_path, READER_LIMIT, "READER_SOURCE")
    _need(_sha256(own_raw) == _reader_sha256, "READER_SOURCE_CHANGED_DURING_LOAD")
    scalar_path = own_path.parent / "read_quality_scalars.py"
    scalar_raw = _read_local(scalar_path, READER_LIMIT, "SCALAR_HELPER")
    _need(_sha256(scalar_raw) == PINNED_SCALAR_READER_SHA256,
          "SCALAR_HELPER_HASH_MISMATCH")
    scalar = _exec_private(scalar_raw, scalar_path,
                           "_p07_load12_baseline_scalar_" + uuid.uuid4().hex)
    scalar.INPUT_SCHEMA = INPUT_SCHEMA
    scalar.RUN_SCHEMA = RUN_SCHEMA
    scalar.SOURCE_REVISION = SOURCE_REVISION
    scalar.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    scalar.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    scalar.SUITE = SUITE
    try:
        ledger, capture, helper_hashes = scalar._load_pinned_helpers()
    except scalar.QualityScalarInputError as error:
        _fail(_safe_foreign_code(error, "PINNED_HELPER_LOAD_FAILED"))
    ledger.INPUT_SCHEMA = INPUT_SCHEMA
    ledger.SOURCE_REVISION = SOURCE_REVISION
    ledger.SOURCE_INPUT_COUNT = SOURCE_INPUT_COUNT
    ledger.CONTRACT_JAVA_COUNT = CONTRACT_JAVA_COUNT
    helper_hashes["scalarReaderSha256"] = _sha256(scalar_raw)
    helper_hashes["load12BaselineReaderSha256"] = _reader_sha256
    return scalar, ledger, capture, helper_hashes


def _validate_input_spec(spec: Any, spec_directory: Path,
                         ledger: types.ModuleType,
                         capture: types.ModuleType) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    legacy = _validate_input_spec_identity(spec)
    context = _ledger_call(
        ledger._validate_input_spec, "INPUT_SPEC_INVALID", legacy, spec_directory, capture)
    _need(REQUIRED_NATIVE_INPUTS <= set(context["pinMap"]),
          "BASELINE_SOURCE_PINS_OMIT_REQUIRED_INPUT")
    return legacy, context


def _validate_native_run(value: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes,
                         stderr_raw: bytes) -> Dict[str, Any]:
    _exact_keys(value, set(_scalar.NATIVE_RUN_KEYS) | {"target"},
                "NATIVE_RUN_KEYS_INVALID")
    _need(type(value["target"]) is str and value["target"] == TARGET,
          "NATIVE_RUN_TARGET_INVALID")
    legacy = {key: item for key, item in value.items() if key != "target"}
    _scalar_call(_scalar._validate_native_run, "NATIVE_RUN_INVALID", legacy,
                 context, stdout_raw, receipt_raw, stderr_raw)
    return dict(value)


def _decode_lines(raw: bytes, code: str) -> List[str]:
    try:
        return raw.decode("utf-8-sig", errors="strict").splitlines()
    except UnicodeDecodeError:
        _fail(code + "_UTF8_INVALID")


def _parse_tagged(line: str, marker: str, code: str) -> Any:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix), code + "_PREFIX_INVALID")
    try:
        body = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail(code + "_ENCODING_INVALID")
    _need(0 < len(body) <= 16 * 1024 * 1024, code + "_SIZE_INVALID")
    return _strict_json(body, code)


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes) -> Tuple[List[str], List[str]]:
    console = _decode_lines(stdout_raw, "RUN_STDOUT")
    receipt = _decode_lines(receipt_raw, "RUN_RECEIPT")
    stderr = _decode_lines(stderr_raw, "RUN_STDERR")
    _need(bool(receipt) and all(line for line in receipt), "RUN_RECEIPT_EMPTY_OR_BLANK")
    _need(len(receipt) == len(RECEIPT_MARKERS) and
          all(_line_has_marker(line, marker) for line, marker in
              zip(receipt, RECEIPT_MARKERS)), "RUN_RECEIPT_MARKER_ORDER_INVALID")
    _need(all(line.startswith(EVENT_PREFIXES) for line in receipt),
          "RUN_RECEIPT_UNEXPECTED_LINE")
    receipt_events = [line for line in receipt if line.startswith(EVENT_PREFIXES)]
    console_events = [line for line in console if line.startswith(EVENT_PREFIXES)]
    stderr_events = [line for line in stderr if line.startswith(EVENT_PREFIXES)]
    _need(console_events == receipt_events and not stderr_events,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(FOCUSED_PASS) < console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    _need(receipt.index(next(line for line in receipt
                             if line.startswith(EVENT_MARKER + " "))) <
          receipt.index(next(line for line in receipt
                             if line.startswith(SUMMARY_PREFIX))),
          "RUN_RECEIPT_EVENT_ORDER_INVALID")
    return console, receipt


def _stable_source_check(spec_raw: bytes, spec_path: Path, spec_sha256: str,
                         legacy_spec: Mapping[str, Any], context: Mapping[str, Any],
                         ledger: types.ModuleType,
                         capture: types.ModuleType) -> None:
    _scalar_call(_scalar._stable_source_check, "SOURCE_REVALIDATION_FAILED",
                 spec_raw, spec_path, spec_sha256, legacy_spec,
                 context, ledger, capture)


def _stable_helpers(helper_hashes: Mapping[str, str]) -> None:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_bounded(own_path, READER_LIMIT, "READER_SOURCE")
    _need(_sha256(own_raw) == helper_hashes["load12BaselineReaderSha256"],
          "READER_SOURCE_CHANGED_DURING_READ")
    scalar_raw = _read_bounded(own_path.parent / "read_quality_scalars.py",
                               READER_LIMIT, "SCALAR_HELPER")
    _need(_sha256(scalar_raw) == helper_hashes["scalarReaderSha256"],
          "SCALAR_HELPER_CHANGED_DURING_READ")
    _scalar_call(_scalar._stable_helper_check, "PINNED_HELPER_REVALIDATION_FAILED",
                 helper_hashes)


def _stable_artifact_check(artifacts: Mapping[str, Path], captures: Mapping[str, bytes]) -> None:
    for name, limit, allow_empty in (
            ("resultPath", MAX_RESULT_BYTES, False),
            ("stdoutPath", MAX_CAPTURE_BYTES, False),
            ("receiptPath", MAX_CAPTURE_BYTES, False),
            ("stderrPath", MAX_CAPTURE_BYTES, True)):
        again = _ledger_call(_ledger._read_bounded, "NATIVE_ARTIFACT_REVALIDATION_FAILED",
                             artifacts[name], limit, name.upper(), allow_empty=allow_empty)
        _need(again == captures[name], "NATIVE_ARTIFACT_CHANGED_DURING_READ")


_scalar: types.ModuleType
_ledger: types.ModuleType
_reader_sha256 = ""


def _load_runtime_helpers() -> Tuple[types.ModuleType, types.ModuleType,
                                     types.ModuleType, Dict[str, str]]:
    global _scalar, _ledger, _reader_sha256
    own_path = Path(os.path.abspath(__file__))
    raw = _read_local(own_path, READER_LIMIT, "READER_SOURCE")
    _reader_sha256 = _sha256(raw)
    _scalar, _ledger, capture, hashes = _load_pinned_helpers()
    return _scalar, _ledger, capture, hashes


def _validate_event(value: Any, expected_run_id: Optional[str] = None) -> Dict[str, Any]:
    """Validate the detached baseline against independent frozen expectations."""
    _exact_keys(value, EVENT_KEYS, "BASELINE_EVENT_KEYS_INVALID")
    return _validate_event_frozen(value, expected_run_id)


def _validate_event_frozen(value: Mapping[str, Any],
                           expected_run_id: Optional[str]) -> Dict[str, Any]:
    _need(_integer(value["protocolVersion"], 1, 1) and value["status"] == "PASS" and
          type(value["runId"]) is str and RUN_ID_RE.fullmatch(value["runId"]) is not None and
          (expected_run_id is None or value["runId"] == expected_run_id) and
          value["operationId"] == "QUALITY_PROBE" and
          value["scope"] == "BASELINE48_ONLY" and
          value["purpose"] == "DETACHED_LOAD12_REPLAY",
          "BASELINE_EVENT_IDENTITY_OR_SCOPE_INVALID")
    selector = value["selector"]
    _exact_keys(selector, SELECTOR_KEYS, "BASELINE_SELECTOR_KEYS_INVALID")
    for name, expected in EXPECTED_LINEAGE.items():
        _need(type(selector[name]) is type(expected) and selector[name] == expected,
              "BASELINE_SELECTOR_LINEAGE_INVALID")
    _need(type(selector["primaryLayer"]) is str and
          selector["primaryLayer"] == "BOTTOM",
          "BASELINE_SELECTOR_LINEAGE_INVALID")
    _exact_keys(selector["grid"], GRID_KEYS, "BASELINE_GRID_KEYS_INVALID")
    _need(all(type(selector["grid"][key]) is int for key in GRID_KEYS) and
          selector["grid"] == {
              "width": 478, "height": 309, "cells": 147702,
              "minX": 30, "minY": 30, "pitch": 10,
          }, "BASELINE_GRID_INVALID")
    _exact_keys(selector["prefix"], PREFIX_KEYS, "BASELINE_PREFIX_KEYS_INVALID")
    _need(type(selector["prefix"]["vias"]) is int and
          type(selector["prefix"]["traces"]) is int and
          selector["prefix"] == {"vias": 17, "traces": 88},
          "BASELINE_PREFIX_INVALID")

    original = _validate_capture(value["original"], "original")
    clone = _validate_capture(value["clone"], "clone")
    comparison = value["comparison"]
    _exact_keys(comparison, COMPARISON_KEYS, "BASELINE_COMPARISON_KEYS_INVALID")
    _need(all(type(comparison[name]) is bool and comparison[name] is True
              for name in COMPARISON_TRUE_FIELDS) and
          all(type(comparison[name]) is bool and comparison[name] is False
              for name in COMPARISON_FALSE_FIELDS),
          "BASELINE_COMPARISON_FLAGS_INVALID")
    _need(_equal_captures(original, clone), "BASELINE_CAPTURE_COMPARISON_INVALID")
    _validate_work(value["work"])
    return {
        "protocolVersion": 1,
        "status": "PASS",
        "runId": value["runId"],
        "operationId": "QUALITY_PROBE",
        "scope": "BASELINE48_ONLY",
        "purpose": "DETACHED_LOAD12_REPLAY",
        "selector": dict(selector),
        "original": original,
        "clone": clone,
        "work": value["work"],
        "comparison": dict(comparison),
    }


def _validate_capture(value: Any, label: str) -> Dict[str, Any]:
    _exact_keys(value, CAPTURE_KEYS, "BASELINE_CAPTURE_KEYS_INVALID")
    for name in ("pathTupleSha256", "goalSha256", "seedSha256", "netOrderSha256",
                 "prefixRowsSha256", "preGeometrySha256", "postGeometrySha256",
                 "publicationSha256"):
        _need(type(value[name]) is str and SHA256_RE.fullmatch(value[name]) is not None,
              "BASELINE_CAPTURE_HASH_INVALID")
    _need(value["pathTupleSha256"] == EXPECTED_PATH_TUPLE_SHA256 and
          value["goalSha256"] == EXPECTED_GOAL_SHA256 and
          value["preGeometrySha256"] == EXPECTED_PRE_GEOMETRY_SHA256,
          "BASELINE_CAPTURE_EXPECTED_HASH_MISMATCH")
    for name, expected in (("nodeCount", 222), ("planarLength", 2170),
                           ("bends", 2), ("flips", 4)):
        _need(type(value[name]) is int and value[name] == expected,
              "BASELINE_CAPTURE_METRICS_INVALID")
    pre = value["pre"]
    _exact_keys(pre, PRE_KEYS, "BASELINE_PRE_KEYS_INVALID")
    _need(all(type(pre[name]) is int and pre[name] >= 0 for name in PRE_KEYS) and
          pre == {"holeCount": 17, "viaCount": 17, "traceCount": 88},
          "BASELINE_PRE_COUNTS_INVALID")
    post = value["post"]
    _exact_keys(post, POST_KEYS, "BASELINE_POST_KEYS_INVALID")
    _need(all(type(post[name]) is int and post[name] >= 0 for name in POST_KEYS) and
          post == {
              "holeCount": 21, "viaCount": 21, "traceCount": 93,
              "createdViaCount": 4, "createdTraceCount": 5,
          } and
          post["holeCount"] - pre["holeCount"] == post["createdViaCount"] and
          post["viaCount"] - pre["viaCount"] == post["createdViaCount"] and
          post["traceCount"] - pre["traceCount"] == post["createdTraceCount"],
          "BASELINE_POST_COUNTS_OR_DELTA_INVALID")
    return dict(value)


def _equal_captures(original: Mapping[str, Any], clone: Mapping[str, Any]) -> bool:
    return all(original[name] == clone[name] for name in (
        "pathTupleSha256", "goalSha256", "nodeCount", "planarLength", "bends", "flips",
        "seedSha256", "netOrderSha256", "prefixRowsSha256", "preGeometrySha256",
        "postGeometrySha256", "publicationSha256", "pre", "post"))


def _validate_work(value: Any, probe_operation: Optional[Mapping[str, Any]] = None,
                   scalar_event: Optional[Mapping[str, Any]] = None) -> None:
    _exact_keys(value, WORK_KEYS, "BASELINE_WORK_KEYS_INVALID")
    source = value["sourceOperation"]
    _exact_keys(source, SOURCE_OPERATION_KEYS, "BASELINE_SOURCE_WORK_KEYS_INVALID")
    _need(_integer(source["operationMs"], 1, 90_000) and
          _integer(source["workUnits"], 1, 640) and
          _integer(source["maxUnitMs"], 0, 5_000) and
          _integer(source["cleanupMs"], 0, 120_000) and
          _integer(source["manualGroups"], 1, 640) and
          _integer(source["manualSliceSteps"], 1, MAX_MANUAL_SLICES) and
          _integer(source["preAdvanceCalls"], 1, 640) and
          _integer(source["postAdvanceCalls"], 1, 640),
          "BASELINE_SOURCE_WORK_LIMIT_INVALID")
    copy = value["copy"]
    _exact_keys(copy, COPY_WORK_KEYS, "BASELINE_COPY_WORK_KEYS_INVALID")
    _need(_integer(copy["advanceCalls"], 1, source["workUnits"]) and
          _integer(copy["workSteps"], 1, MAX_MANUAL_SLICES) and
          _integer(copy["copiedEntries"], 1, 10_000_000) and
          _integer(copy["lastWork"], 1, MAX_WORK_QUANTUM) and
          _integer(copy["observerChecks"], 1, MAX_MANUAL_SLICES) and
          copy["observerChecks"] >= copy["advanceCalls"] and
          copy["advanceCalls"] <= copy["workSteps"] <=
              copy["advanceCalls"] * MAX_WORK_QUANTUM and
          copy["lastWork"] <= copy["workSteps"], "BASELINE_COPY_WORK_INVALID")
    replay = value["replay"]
    _exact_keys(replay, REPLAY_WORK_KEYS, "BASELINE_REPLAY_WORK_KEYS_INVALID")
    _need(_integer(replay["toPreCalls"], 1, MAX_MANUAL_SLICES) and
          _integer(replay["toPreWork"], replay["toPreCalls"],
                   replay["toPreCalls"]) and
          _integer(replay["toPostCalls"], 1, source["workUnits"]) and
          _integer(replay["toPostWork"], replay["toPostCalls"],
                   replay["toPostCalls"] * MAX_WORK_QUANTUM),
          "BASELINE_REPLAY_WORK_INVALID")
    phase_work = [copy["workSteps"], replay["toPreWork"], replay["toPostWork"]]
    for name in ("cloneCursor", "sourceCursor"):
        cursor = value[name]
        _exact_keys(cursor, CURSOR_WORK_KEYS, "BASELINE_CURSOR_WORK_KEYS_INVALID")
        _need(_integer(cursor["preAdvanceCalls"], 1, source["workUnits"]) and
              _integer(cursor["preWork"], cursor["preAdvanceCalls"],
                       cursor["preAdvanceCalls"] * MAX_WORK_QUANTUM) and
              _integer(cursor["postAdvanceCalls"], 1, source["workUnits"]) and
              _integer(cursor["postWork"], cursor["postAdvanceCalls"],
                       cursor["postAdvanceCalls"] * MAX_WORK_QUANTUM),
              "BASELINE_CURSOR_WORK_INVALID")
        phase_work.extend((cursor["preWork"], cursor["postWork"]))
    limits = value["limits"]
    _exact_keys(limits, WORK_LIMIT_KEYS, "BASELINE_WORK_LIMIT_KEYS_INVALID")
    _need(limits == {
        "operationMs": 90_000, "workUnits": 640, "unitMs": 5_000,
        "cleanupMs": 120_000, "quantum": 32_768,
        "maximumVias": 48, "maximumTransitions": 4,
    } and sum(phase_work) <= source["workUnits"] * limits["quantum"],
          "BASELINE_WORK_LIMIT_INVALID")
    if probe_operation is not None and scalar_event is not None:
        _need(source["operationMs"] == probe_operation["operationMs"] and
              source["workUnits"] == probe_operation["workUnits"] and
              source["maxUnitMs"] == probe_operation["maxUnitMs"] and
              source["cleanupMs"] == probe_operation["cleanupMs"] and
              source["manualSliceSteps"] == scalar_event["manualSliceSteps"] and
              source["preAdvanceCalls"] == value["sourceCursor"]["preAdvanceCalls"] and
              source["postAdvanceCalls"] == value["sourceCursor"]["postAdvanceCalls"],
              "BASELINE_SOURCE_WORK_BINDING_INVALID")


def _validate_receipt_lines(lines: Sequence[str]) -> Dict[str, Any]:
    _need(type(lines) in (list, tuple) and len(lines) == len(RECEIPT_MARKERS) and
          all(type(line) is str and line for line in lines),
          "RUN_RECEIPT_EVENT_COUNT_INVALID")
    _need(all(_line_has_marker(line, marker) for line, marker in
              zip(lines, RECEIPT_MARKERS)), "RUN_RECEIPT_MARKER_ORDER_INVALID")
    records = [_parse_tagged(line, marker, "BASELINE_RECEIPT_RECORD")
               for line, marker in zip(lines[:7], RECEIPT_MARKERS[:7])]
    controls = _scalar_call(_scalar._validate_controls, "SCALAR_CONTROLS_INVALID", records[0])
    _scalar_call(_scalar._validate_subset, "SCALAR_SUBSET_INVALID", records[1], controls)
    null = _scalar_call(_scalar._validate_operation, "SCALAR_NULL_OPERATION_INVALID",
                        records[2], controls, "QUALITY_NULL", "NULL")
    _scalar_call(_scalar._validate_subset, "SCALAR_SUBSET_INVALID", records[3], controls)
    probe = _scalar_call(_scalar._validate_operation, "SCALAR_PROBE_OPERATION_INVALID",
                         records[4], controls, "QUALITY_PROBE", "PROBE")
    scalar_event = _scalar_call(_scalar._validate_event, "SCALAR_EVENT_INVALID",
                                records[5], controls, probe)
    _scalar_call(_scalar._validate_pair, "SCALAR_PAIR_INVALID",
                 records[6], controls, null, probe)
    event = _validate_event(
        _parse_tagged(lines[7], EVENT_MARKER, "BASELINE_EVENT"), controls)
    _validate_work(event["work"], probe["result"], scalar_event)
    summary = lines[8]
    summary_match = re.fullmatch(re.escape(SUMMARY_PREFIX) + r"([1-9][0-9]{0,9})",
                                 summary, re.ASCII)
    _need(summary_match is not None, "BASELINE_SUMMARY_INVALID")
    return {"event": event, "assertions": int(summary_match.group(1))}


def _run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, READER_LIMIT, "READER_SOURCE")
    own_sha = _sha256(own_raw)
    _scalar, _ledger, capture, helper_hashes = _load_runtime_helpers()
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _ledger_call(_ledger._resolve_path, "INPUT_SPEC_INVALID",
                             spec_argument, Path.cwd(), "INPUT_SPEC")
    spec_raw = _ledger_call(_ledger._read_bounded, "INPUT_SPEC_INVALID",
                            spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(_ledger._strict_json, "INPUT_SPEC_INVALID", spec_raw, "INPUT_SPEC")
    legacy_spec, context = _validate_input_spec(spec, spec_path.parent, _ledger, capture)
    artifacts = context["artifacts"]
    captures = {
        "resultPath": _ledger_call(_ledger._read_bounded, "NATIVE_RUN_READ_FAILED",
                                   artifacts["resultPath"], MAX_RESULT_BYTES, "NATIVE_RUN"),
        "stdoutPath": _ledger_call(_ledger._read_bounded, "RUN_STDOUT_READ_FAILED",
                                   artifacts["stdoutPath"], MAX_CAPTURE_BYTES, "RUN_STDOUT"),
        "receiptPath": _ledger_call(_ledger._read_bounded, "RUN_RECEIPT_READ_FAILED",
                                    artifacts["receiptPath"], MAX_CAPTURE_BYTES, "RUN_RECEIPT"),
        "stderrPath": _ledger_call(_ledger._read_bounded, "RUN_STDERR_READ_FAILED",
                                   artifacts["stderrPath"], MAX_CAPTURE_BYTES, "RUN_STDERR",
                                   allow_empty=True),
    }
    run_result = _ledger_call(_ledger._strict_json, "NATIVE_RUN_JSON_INVALID",
                              captures["resultPath"], "NATIVE_RUN")
    _validate_native_run(run_result, context, captures["stdoutPath"],
                         captures["receiptPath"], captures["stderrPath"])
    _console, receipt_lines = _validate_console_and_receipt(
        captures["stdoutPath"], captures["receiptPath"], captures["stderrPath"])
    parsed = _validate_receipt_lines(receipt_lines)
    _stable_source_check(spec_raw, spec_path, spec_sha256, legacy_spec, context,
                         _ledger, capture)
    _stable_helpers(helper_hashes)
    _stable_artifact_check(artifacts, captures)
    _need(_sha256(own_raw) == own_sha, "READER_SOURCE_CHANGED_DURING_READ")
    required_hashes = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(REQUIRED_NATIVE_INPUTS)
    }
    event = parsed["event"]
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "target": TARGET,
        "purpose": PURPOSE,
        "suite": SUITE,
        "sourceSnapshotRevision": SOURCE_REVISION,
        "source": {
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": required_hashes,
            "readerSha256": own_sha,
            "scalarReaderSha256": helper_hashes["scalarReaderSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
        },
        "artifacts": {
            "inputSpecSha256": _sha256(spec_raw),
            "nativeRunSha256": _sha256(captures["resultPath"]),
            "stdoutBytes": len(captures["stdoutPath"]),
            "stdoutSha256": _sha256(captures["stdoutPath"]),
            "receiptBytes": len(captures["receiptPath"]),
            "receiptSha256": _sha256(captures["receiptPath"]),
            "stderrBytes": len(captures["stderrPath"]),
            "stderrSha256": _sha256(captures["stderrPath"]),
        },
        "nativeRun": {
            "processId": run_result["processId"],
            "processStartTicks": run_result["processStartTicks"],
            "elapsedSeconds": run_result["elapsedSeconds"],
            "nativeLimitMs": run_result["nativeLimitMs"],
            "outerLimitMs": run_result["outerLimitMs"],
            "cleanupStatus": run_result["cleanupStatus"],
            "inputAudit": run_result["inputAudit"],
        },
        "baselineObservation": {
            "runId": event["runId"],
            "selector": event["selector"],
            "scope": "BASELINE48_ONLY",
            "purpose": "DETACHED_LOAD12_REPLAY",
            "avoidability": "UNKNOWN",
            "routeAdoption": False,
            "layoutAdoption": False,
            "playableBoards": 0,
            "original": event["original"],
            "clone": event["clone"],
            "comparison": event["comparison"],
            "work": event["work"],
            "assertions": parsed["assertions"],
        },
    }


def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("OUTPUT_CANONICALIZATION_INVALID")


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    _need(len(argv) == 4 and argv[0] == "--input-spec" and
          argv[2] == "--input-spec-sha256", "CLI_ARGUMENTS_INVALID")
    _need(type(argv[1]) is str and 0 < len(argv[1]) <= 4096 and
          type(argv[3]) is str and SHA256_RE.fullmatch(argv[3]) is not None,
          "CLI_ARGUMENTS_INVALID")
    return argv[1], argv[3]


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    try:
        return _run_validation(spec_argument, spec_sha256)
    except Load12BaselineInputError:
        raise
    except Exception:
        _fail("INPUT_INVALID")


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        spec_path, spec_sha = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        report = run_validation(spec_path, spec_sha)
        raw = _canonical_json(report) + b"\n"
        _need(len(raw) <= 128 * 1024, "OUTPUT_SIZE_INVALID")
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = _safe_foreign_code(error, "INPUT_INVALID")
        try:
            sys.stdout.write("FAIL: " + code + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
