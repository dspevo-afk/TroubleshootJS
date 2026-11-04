#!/usr/bin/env python3
"""Validate one finite actual LOAD12 copied-order remainder receipt.

The result describes only the frozen LOAD12 request and its copied current
ordering. A typed route or ordering boundary is an experiment outcome; a
reader validation result does not qualify or adopt a board layout.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import re
import sys
import stat
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-load12-cost240-remainder-input-v1"
RUN_SCHEMA = "q100-quality-zero-via-native-run-v5"
RESULT_SCHEMA = "p07-load12-cost240-remainder-reader-result-v1"
EVENT_SCHEMA = "rb100-load12-cost240-remainder-r1"
TARGET = "LOAD12"
PURPOSE = "ACTUAL_LOAD12_COST240_COPIED_CURRENT_ORDER_REMAINDER"
SUITE = "Rb100Load12DetachedBaselineContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
EVENT_MARKER = "RB100_LOAD12_COST240_REMAINDER_EVENT"
SUMMARY_PREFIX = "PASS: RB100 LOAD12 detached cost240 remainder contracts assertions="
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

SOURCE_REVISION = 20
SOURCE_INPUT_COUNT = 1079
PRODUCTION_JAVA_COUNT = 850
CONTRACT_JAVA_COUNT = 223
MAX_SOURCE_BYTES = 512 * 1024 * 1024
MAX_PIN_BYTES = 16 * 1024 * 1024
MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
MAX_LINE_BYTES = 16 * 1024 * 1024
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
MAX_ACCESS_CELLS = 700_000
EXPECTED_ORDERED_PAD_PAIRS = 60_516
EXPECTED_COURTYARD_PAIRS = 4_950
EXPECTED_ESCAPE_PAIRS = 24_354
MAX_SCALAR = 2_147_483_647
MAX_JAVA_LONG = 9_223_372_036_854_775_807

SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
SUMMARY_RE = re.compile(
    re.escape(SUMMARY_PREFIX) + r"([1-9][0-9]{0,9})\Z", re.ASCII)
TICKS_RE = re.compile(r"[1-9][0-9]{0,19}\Z", re.ASCII)
WRAPPER_FAILURE_RE = re.compile(
    r"^(?:FAIL(?:ED|URE)?|ERROR|FATAL)\b|^\[ERROR\](?:\s|:|$)|"
    r"^Exception(?:\s|:|$)|^Traceback(?:\s|:|$)|"
    r"^CategoryInfo\s*:|^FullyQualifiedErrorId\s*:|"
    r"^.*:\d+:\s*error:", re.IGNORECASE | re.ASCII)

INPUT_SPEC_KEYS = frozenset((
    "schema target purpose suite sourceRoot pinsPath pinsSha256 inputCount inputBytes "
    "sourceSnapshotRevision resultPath stdoutPath receiptPath stderrPath").split())
REQUIRED_SOURCE_INPUTS = frozenset({
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
    "tests/contracts/Rb100Load12DetachedBaselineContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
    "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
    "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
})
STATIC_SOURCE_INPUTS = frozenset({
    "scripts/verify-current-contracts.ps1",
    "scripts/VerifierIsolation.psm1",
    "tests/contracts/current-contract-runner-contract.ps1",
    ".tools/gwt-2.7.0/gwt-user-2.7.0.jar",
    ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar",
    "tests/benchmarks/p03-components.tsv",
})
RUN_RESULT_KEYS = frozenset((
    "schema source suite cohortId command layoutVersion dependencyEpoch sourceSnapshotRevision "
    "inputCount inputBytes inputPinsSha256 inputAudit exitCode nativeExitCode "
    "terminationProven cleanupStatus cleanupElapsedMs viaCost nativeLimitMs outerLimitMs "
    "processId processStartTicks elapsedSeconds requestedExecutable stdoutBytes stdoutSha256 "
    "stderrBytes stderrSha256 receiptBytes receiptSha256 target").split())
RUN_RESULT_KEYS_WITH_MANIFEST_BYTES = RUN_RESULT_KEYS | frozenset({"inputManifestBytes"})
EVENT_KEYS = frozenset((
    "protocolVersion schema status runId operationId scope outcome selector source copy "
    "remainder finalizer comparator geometry oracles work cleanup sourcePreserved "
    "sourceResumption ordinaryComparisonComplete layoutQualification routeAdoption "
    "playable100 beta limits suiteMs suiteLimitMs failure").split())
SELECTOR_KEYS = frozenset((
    "rootSeed placementSeed routingSeed routeRequestIndex selectedPlacementIndex "
    "selectedPlacementAttempt pass netPosition nextFetch branchOrdinal searchBranches "
    "netId primaryLayer startPadId goalPadId grid sourcePrefix").split())
GRID_KEYS = frozenset("width height cells originX originY pitch".split())
PREFIX_KEYS = frozenset("vias traces viaCost maximumVias".split())
SOURCE_KEYS = frozenset((
    "routeSessionIdentity searchIdentity branchIdentity layoutIdentity digests "
    "before after preserved").split())
SOURCE_DIGEST_KEYS = frozenset("seeds netOrder prefixRows geometry".split())
SOURCE_STATE_KEYS = frozenset((
    "pass netIndex branchOrdinal branchExpanded queueSize bestSize holes vias traces "
    "viaCost maximumVias searchBudgetExpanded geometrySha256 seedSha256 "
    "netOrderSha256 prefixRowsSha256").split())
COPY_KEYS = frozenset((
    "ownerIdentity searchIdentity branchIdentity layoutIdentity digests requested "
    "publisher continuationHandoff").split())
COPY_DIGEST_KEYS = frozenset((
    "seeds netOrder prefixRows initialGeometry postGeometry publication").split())
COPY_REQUESTED_KEYS = frozenset("maximumVias viaCost".split())
PUBLISHER_KEYS = frozenset((
    "status stage createdVias createdTraces postVias postTraces prefixRetained").split())
HANDOFF_KEYS = frozenset("status singleUse identity ownerIdentity".split())
REMAINDER_KEYS = frozenset((
    "status boundaryType boundaryReason phase orderings cumulativeExpansions "
    "remainingRequestBudget result").split())
RESULT_KEYS = frozenset("pass orderings expansions vias layoutIdentity".split())
FINALIZER_KEYS = frozenset("status reasonType reason".split())
COMPARATOR_KEYS = frozenset("status candidate winner".split())
COMPARATOR_ROW_KEYS = frozenset(
    "layoutIdentity attempt placementScore area quality".split())
GEOMETRY_KEYS = frozenset("raw final".split())
GEOMETRY_ROW_KEYS = frozenset(
    "status layoutIdentity sha256 traceCount holeCount viaCount".split())
ORACLES_KEYS = frozenset("raw final independentTwoLayerAccess".split())
ORACLE_PHASE_KEYS = frozenset((
    "status productionGeometry twoLayerRules productionClearancePairs "
    "independentClearancePairs graphNetEquality referenceGraphSha256 "
    "orderedPadPairsExpected orderedPadPairsChecked rasterPadPairsExpected "
    "rasterPadPairsChecked rasterPointCount boardIdentity p03CourtyardExpected "
    "p03CourtyardChecked p03EscapeExpected p03EscapeChecked productionPlacementAccess").split())
ACCESS_KEYS = frozenset("status gridCells reachableCells cellLimit".split())
WORK_KEYS = frozenset((
    "operationLimitMs controllerUnitLimit unitLimitMs ownerStepsPerUnitLimit phases "
    "operationElapsedMs controllerUnits maxUnitElapsedMs maxOwnerStepsInUnit").split())
PHASE_KEYS = frozenset("name calls ownerSteps elapsedMs".split())
CLEANUP_KEYS = frozenset("status elapsedMs limitMs shutdownElapsedMs".split())
LIMITS_KEYS = frozenset((
    "requestExpansions branchExpansions gridCells orderings vias transitions "
    "operationMs controllerUnits unitMs ownerStepsPerUnit cleanupMs shutdownMs "
    "accessCells suiteMs").split())
EXPECTED_LIMITS = {
    "requestExpansions": MAX_REQUEST_EXPANSIONS,
    "branchExpansions": MAX_BRANCH_EXPANSIONS,
    "gridCells": MAX_GRID_CELLS,
    "orderings": MAX_ORDERINGS,
    "vias": MAX_VIAS,
    "transitions": MAX_TRANSITIONS,
    "operationMs": MAX_OPERATION_MS,
    "controllerUnits": MAX_WORK_UNITS,
    "unitMs": MAX_UNIT_MS,
    "ownerStepsPerUnit": MAX_OWNER_STEPS_PER_UNIT,
    "cleanupMs": MAX_CLEANUP_MS,
    "shutdownMs": 15_000,
    "accessCells": MAX_ACCESS_CELLS,
    "suiteMs": MAX_NATIVE_LIMIT_MS,
}
PHASE_NAMES = (
    "sourcePreparation", "copySetup", "copyAdvance", "branchReplay",
    "branchCapture", "orderingHandoff", "orderingRemainder", "resultTake",
    "rawPhysicalOracles", "finalizationAndReporting", "finalPhysicalOracles",
)
CONCLUSIVE_OUTCOMES = frozenset((
    "NO_ROUTE_BOUNDARY", "QUALITY_BOUNDARY", "FINALIZER_REJECTED",
    "FINALIZED_SELECTED", "FINALIZED_UNSELECTED",
))
FINALIZER_REJECTION_TYPES = frozenset({
    "PLACEMENT", "ROUTING", "BENDS", "DETOUR",
})
RUN_ID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII)


class Load12RemainderInputError(ValueError):
    """A sanitized validation error; paths and source data are never echoed."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise Load12RemainderInputError(code)


def _need(condition: Any, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _reject_duplicate_pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            _fail("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _reject_constant(_value: str):
    _fail("JSON_NONFINITE_NUMBER")


def _strict_json(raw: bytes, label: str) -> Any:
    try:
        return json.loads(raw.decode("utf-8-sig", errors="strict"),
                          object_pairs_hook=_reject_duplicate_pairs,
                          parse_constant=_reject_constant)
    except Load12RemainderInputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError,
            TypeError, ValueError):
        _fail(label + "_JSON_INVALID")


def _strict_canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("JSON_CANONICALIZATION_INVALID")


def _is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, "is_junction", None)
        if callable(is_junction) and is_junction():
            return True
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
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


def _resolve_path(value: Any, base: Path, code: str) -> Path:
    _need(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS,
          code + "_PATH_INVALID")
    path = Path(value)
    if not path.is_absolute():
        path = base / path
    _need(not _has_reparse_component(path), code + "_REPARSE_PATH")
    try:
        resolved = path.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_PATH_UNAVAILABLE")
    _need(not _is_reparse(resolved), code + "_REPARSE_PATH")
    return resolved


def _read_bounded(path: Path, limit: int, code: str,
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
    except Load12RemainderInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _safe_relative_path(value: Any) -> str:
    _need(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS,
          "SOURCE_PATH_INVALID")
    normalized = value.replace("\\", "/")
    _need(value == normalized and not normalized.startswith("/") and
          re.match(r"^[A-Za-z]:", normalized) is None and
          all(part not in ("", ".", "..")
              for part in normalized.split("/")),
          "SOURCE_PATH_INVALID")
    return normalized


def _closed_java_paths(source_root: Path, relative_root: str,
                       recursive: bool) -> set:
    """Return the source census, refusing reparses and incomplete walks."""
    root = source_root.joinpath(*relative_root.split("/"))
    _need(not _has_reparse_component(root), "SOURCE_CENSUS_REPARSE_PATH")
    try:
        resolved_root = root.resolve(strict=True)
        _need(resolved_root == root and resolved_root.is_dir(),
              "SOURCE_CENSUS_ROOT_INVALID")
    except (OSError, RuntimeError, ValueError):
        _fail("SOURCE_CENSUS_ROOT_UNAVAILABLE")
    paths = set()

    def record_entry(path: Path, is_directory: bool) -> None:
        _need(not _has_reparse_component(path), "SOURCE_CENSUS_REPARSE_ENTRY")
        try:
            info = path.lstat()
        except OSError:
            _fail("SOURCE_CENSUS_ENTRY_UNAVAILABLE")
        _need(not stat.S_ISLNK(info.st_mode) and
              not bool(getattr(info, "st_file_attributes", 0) & 0x400),
              "SOURCE_CENSUS_REPARSE_ENTRY")
        if is_directory:
            _need(stat.S_ISDIR(info.st_mode), "SOURCE_CENSUS_DIRECTORY_INVALID")
        elif path.suffix == ".java":
            _need(stat.S_ISREG(info.st_mode), "SOURCE_CENSUS_FILE_INVALID")
            paths.add(path.relative_to(source_root).as_posix())

    if recursive:
        def onerror(_error):
            _fail("SOURCE_CENSUS_WALK_INCOMPLETE")

        try:
            for current, dirs, files in os.walk(
                    resolved_root, topdown=True, followlinks=False, onerror=onerror):
                current_path = Path(current)
                for name in list(dirs):
                    child = current_path / name
                    record_entry(child, True)
                for name in files:
                    record_entry(current_path / name, False)
        except (OSError, RuntimeError, ValueError):
            _fail("SOURCE_CENSUS_WALK_INCOMPLETE")
    else:
        try:
            with os.scandir(resolved_root) as entries:
                for entry in entries:
                    record_entry(Path(entry.path), entry.is_dir(follow_symlinks=False))
        except (OSError, RuntimeError, ValueError):
            _fail("SOURCE_CENSUS_WALK_INCOMPLETE")
    return paths


def _audit_inputs_manifest(raw: bytes, spec: Mapping[str, Any],
                           source_root: Path
                           ) -> Dict[str, Any]:
    _need(_sha256(raw) == spec["pinsSha256"], "SOURCE_PINS_HASH_MISMATCH")
    rows = _strict_json(raw, "SOURCE_PINS")
    _need(type(rows) is list and len(rows) == SOURCE_INPUT_COUNT,
          "SOURCE_PINS_COUNT_INVALID")
    pin_map: Dict[str, Dict[str, Any]] = {}
    total_bytes = 0
    for row in rows:
        _exact_keys(row, {"path", "bytes", "sha256"}, "SOURCE_PIN_KEYS_INVALID")
        relative = _safe_relative_path(row["path"])
        _need(relative not in pin_map and
              _integer(row["bytes"], 0, MAX_SOURCE_BYTES) and
              type(row["sha256"]) is str and SHA256_RE.fullmatch(row["sha256"]) is not None,
              "SOURCE_PIN_INVALID")
        path = source_root.joinpath(*relative.split("/"))
        _need(not _has_reparse_component(path), "SOURCE_PIN_REPARSE_PATH")
        try:
            resolved = path.resolve(strict=True)
            relative_resolved = resolved.relative_to(source_root).as_posix()
            source_raw = _read_bounded(resolved, MAX_SOURCE_BYTES,
                                       "SOURCE_PIN_CONTENT", allow_empty=True)
        except (OSError, RuntimeError, ValueError):
            _fail("SOURCE_PIN_UNAVAILABLE")
        _need(relative_resolved == relative and resolved.is_file() and
              len(source_raw) == row["bytes"] and
              _sha256(source_raw) == row["sha256"],
              "SOURCE_PIN_CONTENT_MISMATCH")
        total_bytes += len(source_raw)
        _need(total_bytes <= MAX_SOURCE_BYTES, "SOURCE_PIN_BYTES_OVERFLOW")
        pin_map[relative] = {"bytes": len(source_raw), "sha256": row["sha256"]}
    _need(total_bytes == spec["inputBytes"], "SOURCE_PIN_BYTE_COUNT_MISMATCH")
    _need(REQUIRED_SOURCE_INPUTS <= set(pin_map),
          "SOURCE_PINS_OMIT_REQUIRED_NATIVE_INPUT")
    production_java = {
        path for path in pin_map
        if path.startswith("src/") and path.endswith(".java")
    }
    contract_java = {
        path for path in pin_map
        if path.startswith("tests/contracts/") and path.endswith(".java")
    }
    static_inputs = set(pin_map) - production_java - contract_java
    _need(len(production_java) == PRODUCTION_JAVA_COUNT and
          len(contract_java) == CONTRACT_JAVA_COUNT and
          static_inputs == STATIC_SOURCE_INPUTS and
          len(pin_map) == PRODUCTION_JAVA_COUNT + CONTRACT_JAVA_COUNT +
          len(STATIC_SOURCE_INPUTS),
          "SOURCE_PINS_NATIVE_CENSUS_INVALID")
    _need(production_java == _closed_java_paths(source_root, "src", recursive=True) and
          contract_java == _closed_java_paths(
              source_root, "tests/contracts", recursive=False),
          "SOURCE_PINS_JAVA_CENSUS_MISMATCH")
    return {
        "pinMap": pin_map,
        "inputCount": len(rows),
        "inputBytes": total_bytes,
        "pinsSha256": _sha256(raw),
        "inputManifestBytes": len(raw),
    }


def _validate_input_spec(spec: Any, spec_directory: Path) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    _exact_keys(spec, INPUT_SPEC_KEYS, "INPUT_SPEC_KEYS_INVALID")
    _need(spec["schema"] == INPUT_SCHEMA and spec["target"] == TARGET and
          spec["purpose"] == PURPOSE and spec["suite"] == SUITE and
          type(spec["sourceSnapshotRevision"]) is int and
          spec["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(spec["inputCount"]) is int and
          spec["inputCount"] == SOURCE_INPUT_COUNT and
          _integer(spec["inputBytes"], 1, MAX_SOURCE_BYTES) and
          type(spec["pinsSha256"]) is str and
          SHA256_RE.fullmatch(spec["pinsSha256"]) is not None,
          "INPUT_SPEC_IDENTITY_INVALID")
    source_root = _resolve_path(spec["sourceRoot"], spec_directory, "SOURCE_ROOT")
    _need(source_root.is_dir(), "SOURCE_ROOT_NOT_DIRECTORY")
    pins_path = _resolve_path(spec["pinsPath"], spec_directory, "SOURCE_PINS")
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS")
    input_manifest = _audit_inputs_manifest(pins_raw, spec, source_root)
    artifacts = {
        name: _resolve_path(spec[name], spec_directory, name.upper())
        for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath")
    }
    artifact_paths = list(artifacts.values())
    _need(len(set(artifact_paths)) == len(artifact_paths) and
          pins_path not in artifact_paths and source_root not in artifact_paths,
          "INPUT_SPEC_ARTIFACT_PATH_ALIAS")
    context = {
        "sourceRoot": source_root,
        "pinsPath": pins_path,
        "pinsRaw": pins_raw,
        **input_manifest,
        "artifacts": artifacts,
        "sourceSnapshotRevision": SOURCE_REVISION,
    }
    return dict(spec), context


def _expected_command() -> Dict[str, Any]:
    return {
        "entry": ENTRY_SCRIPT,
        "suite": SUITE,
        "suites": [SUITE],
        "args": ["-Rb100Load12Cost240RemainderReplay"],
        "environment": {},
    }


def _validate_native_run(value: Any, context: Mapping[str, Any]) -> Dict[str, Any]:
    _need(type(value) is dict and
          set(value) in (RUN_RESULT_KEYS, RUN_RESULT_KEYS_WITH_MANIFEST_BYTES),
          "NATIVE_RUN_KEYS_INVALID")
    _need(value["schema"] == RUN_SCHEMA and
          value["source"] == "candidate" and value["suite"] == SUITE and
          value["cohortId"] is None and value["target"] == TARGET and
          value["command"] == _expected_command(),
          "NATIVE_RUN_IDENTITY_INVALID")
    _need(type(value["layoutVersion"]) is int and value["layoutVersion"] == 26 and
          value["dependencyEpoch"] == "tsj-generation-dependencies-v28" and
          type(value["sourceSnapshotRevision"]) is int and
          value["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(value["inputCount"]) is int and
          value["inputCount"] == context["inputCount"] and
          type(value["inputBytes"]) is int and
          value["inputBytes"] == context["inputBytes"] and
          value["inputPinsSha256"] == context["pinsSha256"] and
          value["inputAudit"] == "PASS_BEFORE_AND_AFTER",
          "NATIVE_RUN_SOURCE_BINDING_INVALID")
    if "inputManifestBytes" in value:
        _need(type(value["inputManifestBytes"]) is int and
              value["inputManifestBytes"] == context["inputManifestBytes"],
              "NATIVE_RUN_INPUT_MANIFEST_BYTES_INVALID")
    _need(type(value["exitCode"]) is int and value["exitCode"] == 0 and
          type(value["nativeExitCode"]) is int and value["nativeExitCode"] == 0 and
          value["terminationProven"] is True and
          value["cleanupStatus"] == "PASS" and
          value["cleanupElapsedMs"] is None and
          _integer(value["viaCost"], 60, 60) and
          _integer(value["nativeLimitMs"], MAX_NATIVE_LIMIT_MS,
                   MAX_NATIVE_LIMIT_MS) and
          _integer(value["outerLimitMs"], MAX_OUTER_LIMIT_MS,
                   MAX_OUTER_LIMIT_MS) and
          value["requestedExecutable"] == "powershell.exe",
          "NATIVE_RUN_EXIT_CLEANUP_OR_BUDGET_INVALID")
    _need(_integer(value["processId"], 1, (1 << 63) - 1) and
          type(value["processStartTicks"]) is str and
          TICKS_RE.fullmatch(value["processStartTicks"]) is not None and
          type(value["elapsedSeconds"]) in (int, float) and
          math.isfinite(value["elapsedSeconds"]) and
          0 < value["elapsedSeconds"] <= 1800,
          "NATIVE_RUN_PROCESS_INVALID")
    for label, raw in (("stdout", context["stdoutRaw"]),
                       ("stderr", context["stderrRaw"]),
                       ("receipt", context["receiptRaw"])):
        size = value[label + "Bytes"]
        digest = value[label + "Sha256"]
        _need(_integer(size, 0, MAX_CAPTURE_BYTES) and size == len(raw) and
              type(digest) is str and SHA256_RE.fullmatch(digest) is not None and
              digest == _sha256(raw),
              "NATIVE_RUN_" + label.upper() + "_BINDING_INVALID")
    _need(context["stderrRaw"] == b"", "RUN_STDERR_NOT_EMPTY")
    return dict(value)


def _decode_lines(raw: bytes, code: str) -> List[str]:
    try:
        lines = raw.decode("utf-8-sig", errors="strict").splitlines()
    except UnicodeDecodeError:
        _fail(code + "_UTF8_INVALID")
    _need(all(line.strip() == line and line for line in lines),
          code + "_BLANK_OR_WHITESPACE_LINE")
    return lines


def _decode_wrapper_lines(raw: bytes, code: str) -> List[str]:
    """Decode host output without normalizing diagnostic or payload lines."""
    try:
        return raw.decode("utf-8-sig", errors="strict").splitlines()
    except UnicodeDecodeError:
        _fail(code + "_UTF8_INVALID")


def _parse_tagged(line: str, marker: str, code: str) -> Any:
    prefix = marker + " "
    _need(type(line) is str and line.startswith(prefix), code + "_MARKER_INVALID")
    body = line[len(prefix):].encode("utf-8", errors="strict")
    _need(0 < len(body) <= MAX_LINE_BYTES, code + "_SIZE_INVALID")
    return _strict_json(body, code)


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes) -> Tuple[List[str], List[str], Dict[str, Any]]:
    console = _decode_wrapper_lines(stdout_raw, "RUN_STDOUT")
    receipt = _decode_lines(receipt_raw, "RUN_RECEIPT")
    stderr = _decode_lines(stderr_raw, "RUN_STDERR") if stderr_raw else []
    _need(len(receipt) == 2 and receipt[0].startswith(EVENT_MARKER + " ") and
          SUMMARY_RE.fullmatch(receipt[1]) is not None,
          "RECEIPT_LINE_ORDER_OR_COUNT_INVALID")
    event_lines = [line for line in receipt
                   if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    console_events = [line for line in console
                      if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    stderr_events = [line for line in stderr
                     if line.startswith(("RB100_", "P07_", "PASS: RB100"))]
    _need(event_lines == receipt and console_events == event_lines and
          not stderr_events,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    for line in console:
        left_trimmed = line.lstrip()
        if left_trimmed.startswith(("RB100_", "P07_", "PASS: RB100")):
            _need(line == left_trimmed,
                  "RUN_CONSOLE_EVENT_EDGE_WHITESPACE")
        # Use trimming only to recognize an explicit diagnostic; payload text
        # remains unchanged for exact stdout/receipt comparison and JSON parse.
        diagnostic = line.strip()
        _need(WRAPPER_FAILURE_RE.search(diagnostic) is None,
              "RUN_CONSOLE_FAILURE_LINE")
    event_index = console.index(receipt[0])
    summary_index = console.index(receipt[1])
    _need(console.count(FOCUSED_PASS) == 1 and
          console.count(CLEANUP_LINE) == 1 and
          summary_index == event_index + 1 and
          summary_index < console.index(FOCUSED_PASS) <
          console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    match = SUMMARY_RE.fullmatch(receipt[1])
    _need(match is not None and
          _integer(int(match.group(1)), 1, MAX_SCALAR),
          "RECEIPT_SUMMARY_ASSERTIONS_INVALID")
    event = _parse_tagged(receipt[0], EVENT_MARKER, "REMAINDER_EVENT")
    return console, receipt, event


def _text(value: Any, minimum: int, maximum: int, code: str) -> str:
    _need(type(value) is str and minimum <= len(value) <= maximum and
          all(ord(char) >= 0x20 and ord(char) != 0x7f for char in value), code)
    return value


def _digest(value: Any, code: str) -> str:
    _need(type(value) is str and SHA256_RE.fullmatch(value) is not None, code)
    return value


def _identity(value: Any, code: str) -> int:
    _need(_integer(value, -(1 << 31), (1 << 31) - 1), code)
    return value


def _validate_limits(value: Any) -> Dict[str, int]:
    _exact_keys(value, LIMITS_KEYS, "REMAINDER_LIMITS_KEYS_INVALID")
    _need(all(type(value[name]) is int and value[name] == expected
              for name, expected in EXPECTED_LIMITS.items()),
          "REMAINDER_LIMITS_CHANGED")
    return dict(value)


def _finite_number(value: Any, minimum: float, maximum: float) -> bool:
    if type(value) is int:
        return minimum <= value <= maximum
    return (type(value) is float and math.isfinite(value) and
            minimum <= value <= maximum)


def _validate_selector(value: Any, limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, SELECTOR_KEYS, "REMAINDER_SELECTOR_KEYS_INVALID")
    expected = {
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
        "routeRequestIndex": 0,
        "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4,
        "pass": 1,
        "netPosition": 3,
        "nextFetch": 4,
        "branchOrdinal": 73,
        "searchBranches": 73,
        "netId": "LOAD12",
        "primaryLayer": "BOTTOM",
        "startPadId": "JLOAD.1",
        "grid": {
            "width": 478, "height": 309, "cells": 147702,
            "originX": 30, "originY": 30, "pitch": 10,
        },
        "sourcePrefix": {
            "vias": 17, "traces": 88, "viaCost": 60,
            "maximumVias": MAX_VIAS,
        },
    }
    # The LOAD12 target is an existing CSTORE_E.1 tree. Search may encode its
    # tree goal as a null pad ID; every other goal is rejected.
    _exact_keys(value["grid"], GRID_KEYS, "REMAINDER_SELECTOR_GRID_KEYS_INVALID")
    _exact_keys(value["sourcePrefix"], PREFIX_KEYS,
                "REMAINDER_SELECTOR_PREFIX_KEYS_INVALID")
    for key, expected_value in expected.items():
        if type(expected_value) is int:
            _need(_integer(value[key], expected_value, expected_value),
                  "REMAINDER_SELECTOR_INTEGER_INVALID")
    for key, expected_value in expected["grid"].items():
        _need(_integer(value["grid"][key], expected_value, expected_value),
              "REMAINDER_SELECTOR_GRID_INTEGER_INVALID")
    for key, expected_value in expected["sourcePrefix"].items():
        _need(_integer(value["sourcePrefix"][key], expected_value, expected_value),
              "REMAINDER_SELECTOR_PREFIX_INTEGER_INVALID")
    observed = dict(value)
    goal_pad_id = observed.pop("goalPadId")
    _need(goal_pad_id is None or goal_pad_id == "CSTORE_E.1",
          "REMAINDER_SELECTOR_GOAL_INVALID")
    _need(value["grid"]["cells"] <= limits["gridCells"] and
          value["grid"]["width"] * value["grid"]["height"] ==
          value["grid"]["cells"] and
          value["searchBranches"] <= limits["branchExpansions"] and
          value["branchOrdinal"] <= limits["branchExpansions"] and
          value["sourcePrefix"]["maximumVias"] <= limits["vias"],
          "REMAINDER_SELECTOR_LIMIT_BINDING_INVALID")
    _need(observed == expected, "REMAINDER_SELECTOR_LINEAGE_INVALID")
    return dict(value)


def _validate_source(value: Any, limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, SOURCE_KEYS, "REMAINDER_SOURCE_KEYS_INVALID")
    for key in ("routeSessionIdentity", "searchIdentity", "branchIdentity",
                "layoutIdentity"):
        _identity(value[key], "REMAINDER_SOURCE_IDENTITY_INVALID")
    _need(type(value["preserved"]) is bool and value["preserved"] is True,
          "REMAINDER_SOURCE_NOT_PRESERVED")
    digests = value["digests"]
    _exact_keys(digests, SOURCE_DIGEST_KEYS,
                "REMAINDER_SOURCE_DIGEST_KEYS_INVALID")
    for name in SOURCE_DIGEST_KEYS:
        _digest(digests[name], "REMAINDER_SOURCE_DIGEST_INVALID")
    before = value["before"]
    after = value["after"]
    _exact_keys(before, SOURCE_STATE_KEYS, "REMAINDER_SOURCE_BEFORE_KEYS_INVALID")
    _exact_keys(after, SOURCE_STATE_KEYS, "REMAINDER_SOURCE_AFTER_KEYS_INVALID")
    expected = {
        "pass": 1, "netIndex": 4, "branchOrdinal": 73,
        "branchExpanded": 0, "queueSize": 2, "bestSize": 2,
        "holes": 17, "vias": 17, "traces": 88, "viaCost": 60,
        "maximumVias": MAX_VIAS,
    }
    _need(all(_integer(before.get(key), item, item)
              for key, item in expected.items()) and
          before == after and
          _integer(before["searchBudgetExpanded"], 0,
                   limits["requestExpansions"]) and
          _integer(before["branchExpanded"], 0,
                   limits["branchExpansions"]),
          "REMAINDER_SOURCE_SNAPSHOT_CHANGED")
    _need(before["geometrySha256"] == digests["geometry"] and
          before["seedSha256"] == digests["seeds"] and
          before["netOrderSha256"] == digests["netOrder"] and
          before["prefixRowsSha256"] == digests["prefixRows"],
          "REMAINDER_SOURCE_DIGEST_BINDING_INVALID")
    for field in ("geometrySha256", "seedSha256", "netOrderSha256",
                  "prefixRowsSha256"):
        _digest(before[field], "REMAINDER_SOURCE_STATE_DIGEST_INVALID")
    return dict(value)


def _validate_copy(value: Any, source: Mapping[str, Any],
                   limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, COPY_KEYS, "REMAINDER_COPY_KEYS_INVALID")
    for key in ("ownerIdentity", "searchIdentity", "branchIdentity", "layoutIdentity"):
        _identity(value[key], "REMAINDER_COPY_IDENTITY_INVALID")
    _need(value["ownerIdentity"] != source["routeSessionIdentity"] and
          value["searchIdentity"] != source["searchIdentity"] and
          value["branchIdentity"] != source["branchIdentity"] and
          value["layoutIdentity"] != source["layoutIdentity"],
          "REMAINDER_COPY_REUSES_SOURCE_IDENTITY")

    digests = value["digests"]
    _exact_keys(digests, COPY_DIGEST_KEYS,
                "REMAINDER_COPY_DIGEST_KEYS_INVALID")
    for name in COPY_DIGEST_KEYS:
        _digest(digests[name], "REMAINDER_COPY_DIGEST_INVALID")
    source_digests = source["digests"]
    _need(digests["seeds"] == source_digests["seeds"] and
          digests["netOrder"] == source_digests["netOrder"] and
          digests["prefixRows"] == source_digests["prefixRows"] and
          digests["initialGeometry"] == source_digests["geometry"],
          "REMAINDER_COPY_INITIAL_LINEAGE_INVALID")

    requested = value["requested"]
    _exact_keys(requested, COPY_REQUESTED_KEYS,
                "REMAINDER_COPY_REQUESTED_KEYS_INVALID")
    _need(_integer(requested["maximumVias"], limits["vias"], limits["vias"]) and
          _integer(requested["viaCost"], 240, 240),
          "REMAINDER_COPY_CAP_OR_COST_INVALID")

    publisher = value["publisher"]
    _exact_keys(publisher, PUBLISHER_KEYS,
                "REMAINDER_COPY_PUBLISHER_KEYS_INVALID")
    expected_publisher = {
        "status": "PUBLISHED", "stage": 6, "createdVias": 1,
        "createdTraces": 2, "postVias": 18, "postTraces": 90,
        "prefixRetained": True,
    }
    _need(publisher["status"] == expected_publisher["status"] and
          publisher["prefixRetained"] is True and
          all(_integer(publisher[key], expected_publisher[key], expected_publisher[key])
              for key in ("stage", "createdVias", "createdTraces", "postVias", "postTraces")),
          "REMAINDER_COPY_PUBLISHER_INVALID")

    handoff = value["continuationHandoff"]
    _exact_keys(handoff, HANDOFF_KEYS,
                "REMAINDER_COPY_HANDOFF_KEYS_INVALID")
    _need(handoff["status"] == "TRANSFERRED" and
          handoff["singleUse"] is True and
          handoff["ownerIdentity"] == value["ownerIdentity"],
          "REMAINDER_COPY_HANDOFF_INVALID")
    _identity(handoff["identity"], "REMAINDER_COPY_HANDOFF_IDENTITY_INVALID")
    return dict(value)


def _validate_work(value: Any, limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, WORK_KEYS, "REMAINDER_WORK_KEYS_INVALID")
    expected_limits = {
        "operationLimitMs": limits["operationMs"],
        "controllerUnitLimit": limits["controllerUnits"],
        "unitLimitMs": limits["unitMs"],
        "ownerStepsPerUnitLimit": limits["ownerStepsPerUnit"],
    }
    _need(all(_integer(value.get(key), item, item)
              for key, item in expected_limits.items()),
          "REMAINDER_WORK_LIMITS_CHANGED")
    phases = value["phases"]
    _need(type(phases) is list and len(phases) == len(PHASE_NAMES),
          "REMAINDER_WORK_PHASE_COUNT_INVALID")
    total_calls = 0
    total_steps = 0
    total_elapsed = 0
    for index, phase in enumerate(phases):
        _exact_keys(phase, PHASE_KEYS, "REMAINDER_WORK_PHASE_KEYS_INVALID")
        _need(phase["name"] == PHASE_NAMES[index] and
              _integer(phase["calls"], 0, limits["controllerUnits"]) and
              _integer(phase["ownerSteps"], 0,
                       limits["controllerUnits"] * limits["ownerStepsPerUnit"]) and
              _integer(phase["elapsedMs"], 0, limits["operationMs"]) and
              phase["ownerSteps"] <= phase["calls"] * limits["ownerStepsPerUnit"] and
              (phase["calls"] != 0 or phase["ownerSteps"] == 0),
              "REMAINDER_WORK_PHASE_ACCOUNTING_INVALID")
        total_calls += phase["calls"]
        total_steps += phase["ownerSteps"]
        total_elapsed += phase["elapsedMs"]
    _need(_integer(value["operationElapsedMs"], 1, limits["operationMs"]) and
          value["operationElapsedMs"] == total_elapsed and
          _integer(value["controllerUnits"], 1, limits["controllerUnits"]) and
          value["controllerUnits"] == total_calls and
          _integer(value["maxUnitElapsedMs"], 0, limits["unitMs"]) and
          _integer(value["maxOwnerStepsInUnit"], 0, limits["ownerStepsPerUnit"]) and
          value["maxOwnerStepsInUnit"] <= total_steps and
          total_steps <= value["controllerUnits"] * limits["ownerStepsPerUnit"],
          "REMAINDER_WORK_TOTALS_INVALID")
    return dict(value)


def _validate_cleanup(value: Any, limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, CLEANUP_KEYS, "REMAINDER_CLEANUP_KEYS_INVALID")
    _need(value["status"] == "PASS" and
          _integer(value["elapsedMs"], 0, limits["cleanupMs"]) and
          _integer(value["limitMs"], limits["cleanupMs"], limits["cleanupMs"]) and
          value["shutdownElapsedMs"] is None,
          "REMAINDER_CLEANUP_INVALID")
    return dict(value)


def _validate_oracle_phase(value: Any, code: str) -> Dict[str, Any]:
    _exact_keys(value, ORACLE_PHASE_KEYS, code + "_KEYS_INVALID")
    access = value["productionPlacementAccess"]
    _exact_keys(access, ACCESS_KEYS, code + "_ACCESS_KEYS_INVALID")
    expected_counts = {
        "orderedPadPairsExpected": EXPECTED_ORDERED_PAD_PAIRS,
        "rasterPadPairsExpected": EXPECTED_ORDERED_PAD_PAIRS,
        "p03CourtyardExpected": EXPECTED_COURTYARD_PAIRS,
        "p03EscapeExpected": EXPECTED_ESCAPE_PAIRS,
    }
    _need(all(type(value[key]) is int and value[key] == expected
              for key, expected in expected_counts.items()) and
          type(access["cellLimit"]) is int and
          access["cellLimit"] == MAX_ACCESS_CELLS,
          code + "_FIXED_EXPECTED_COUNTS_INVALID")
    if value["status"] == "NOT_RUN":
        status_fields = (
            "productionGeometry", "twoLayerRules", "graphNetEquality", "boardIdentity",
        )
        _need(all(value[name] == "NOT_RUN" for name in status_fields) and
              value["productionClearancePairs"] is None and
              value["independentClearancePairs"] is None and
              value["referenceGraphSha256"] is None and
              all(value[name] is None for name in (
                  "orderedPadPairsChecked", "rasterPadPairsChecked",
                  "rasterPointCount", "p03CourtyardChecked", "p03EscapeChecked")) and
              access["status"] == "NOT_RUN" and
              access["gridCells"] is None and access["reachableCells"] is None and
              access["cellLimit"] == MAX_ACCESS_CELLS,
              code + "_NOT_RUN_VALUES_INVALID")
        return dict(value)
    _need(value["status"] == "PASS" and
          all(value[name] == "PASS" for name in (
              "productionGeometry", "twoLayerRules", "graphNetEquality", "boardIdentity")) and
          _integer(value["productionClearancePairs"], 1, MAX_SCALAR) and
          _integer(value["independentClearancePairs"], 1, MAX_SCALAR),
          code + "_STATUS_INVALID")
    _digest(value["referenceGraphSha256"], code + "_REFERENCE_GRAPH_DIGEST_INVALID")
    _need(_integer(value["orderedPadPairsChecked"],
                   EXPECTED_ORDERED_PAD_PAIRS, EXPECTED_ORDERED_PAD_PAIRS) and
          _integer(value["rasterPadPairsChecked"],
                   EXPECTED_ORDERED_PAD_PAIRS, EXPECTED_ORDERED_PAD_PAIRS) and
          _integer(value["rasterPointCount"], 1, MAX_GRID_CELLS) and
          _integer(value["p03CourtyardChecked"],
                   EXPECTED_COURTYARD_PAIRS, EXPECTED_COURTYARD_PAIRS) and
          _integer(value["p03EscapeChecked"],
                   EXPECTED_ESCAPE_PAIRS, EXPECTED_ESCAPE_PAIRS),
          code + "_PHYSICAL_PAIR_COVERAGE_INVALID")
    _need(access["status"] == "PASS" and
          _integer(access["gridCells"], 1, MAX_ACCESS_CELLS) and
          _integer(access["reachableCells"], 1, access["gridCells"]) and
          access["cellLimit"] == MAX_ACCESS_CELLS,
          code + "_PLACEMENT_ACCESS_INVALID")
    return dict(value)


def _validate_oracles(value: Any, route: bool,
                      final_route: bool,
                      limits: Mapping[str, int]) -> Dict[str, Any]:
    _exact_keys(value, ORACLES_KEYS, "REMAINDER_ORACLES_KEYS_INVALID")
    _need(value["independentTwoLayerAccess"] == "NOT_AVAILABLE",
          "REMAINDER_TWO_LAYER_ACCESS_CLAIM_INVALID")
    raw = _validate_oracle_phase(value["raw"], "REMAINDER_RAW_ORACLES")
    final = _validate_oracle_phase(value["final"], "REMAINDER_FINAL_ORACLES")
    _need((raw["status"] == "PASS") is route and
          (final["status"] == "PASS") is final_route,
          "REMAINDER_ORACLE_PHASE_MISMATCH")
    for phase in (raw, final):
        access = phase["productionPlacementAccess"]
        _need(access["cellLimit"] == limits["accessCells"] and
              (access["gridCells"] is None or
               access["gridCells"] <= limits["accessCells"]) and
              (access["reachableCells"] is None or
               access["reachableCells"] <= limits["accessCells"]),
              "REMAINDER_ACCESS_LIMIT_BINDING_INVALID")
    return {"raw": raw, "final": final,
            "independentTwoLayerAccess": "NOT_AVAILABLE"}


def _validate_geometry_row(value: Any, code: str,
                           minimum_vias: int = 18,
                           minimum_traces: int = 90) -> Dict[str, Any]:
    _exact_keys(value, GEOMETRY_ROW_KEYS, code + "_KEYS_INVALID")
    _need(value["status"] == "PASS", code + "_STATUS_INVALID")
    _identity(value["layoutIdentity"], code + "_LAYOUT_IDENTITY_INVALID")
    _digest(value["sha256"], code + "_DIGEST_INVALID")
    _need(_integer(value["traceCount"], minimum_traces, MAX_SCALAR) and
          _integer(value["holeCount"], 0, MAX_SCALAR) and
          _integer(value["viaCount"], minimum_vias, value["holeCount"]),
          code + "_COUNTS_INVALID")
    return dict(value)


def _validate_comparator(value: Any, final_layout_identity: int,
                         finalizer_status: str) -> Dict[str, Any]:
    _exact_keys(value, COMPARATOR_KEYS, "REMAINDER_COMPARATOR_KEYS_INVALID")
    if finalizer_status == "NOT_RUN" or finalizer_status == "REJECTED":
        _need(value == {"status": "NOT_RUN", "candidate": None, "winner": None},
              "REMAINDER_COMPARATOR_SHOULD_NOT_RUN")
        return dict(value)
    _need(value["status"] in ("SELECTED", "UNSELECTED"),
          "REMAINDER_COMPARATOR_STATUS_INVALID")
    candidate = value["candidate"]
    winner = value["winner"]
    for name, row in (("CANDIDATE", candidate), ("WINNER", winner)):
        _exact_keys(row, COMPARATOR_ROW_KEYS,
                    "REMAINDER_COMPARATOR_" + name + "_KEYS_INVALID")
        _identity(row["layoutIdentity"],
                  "REMAINDER_COMPARATOR_" + name + "_IDENTITY_INVALID")
        _need(_integer(row["attempt"], 0, MAX_SCALAR) and
              _integer(row["placementScore"], 0, MAX_JAVA_LONG) and
              _integer(row["area"], 1, MAX_JAVA_LONG) and
              _finite_number(row["quality"], 0, MAX_JAVA_LONG),
              "REMAINDER_COMPARATOR_" + name + "_METRICS_INVALID")
    _need(candidate["attempt"] == 4 and
          candidate["layoutIdentity"] == final_layout_identity,
          "REMAINDER_COMPARATOR_CANDIDATE_MISMATCH")
    if value["status"] == "SELECTED":
        _need(winner == candidate, "REMAINDER_COMPARATOR_WINNER_MISMATCH")
    else:
        _need(winner["layoutIdentity"] != candidate["layoutIdentity"],
              "REMAINDER_COMPARATOR_UNSELECTED_WINNER_MISMATCH")
    return dict(value)


def _validate_event(value: Any) -> Dict[str, Any]:
    _exact_keys(value, EVENT_KEYS, "REMAINDER_EVENT_KEYS_INVALID")
    _need(type(value["protocolVersion"]) is int and
          value["protocolVersion"] == 1 and value["schema"] == EVENT_SCHEMA and
          value["status"] == "PASS" and
          type(value["runId"]) is str and RUN_ID_RE.fullmatch(value["runId"]) and
          value["operationId"] == "QUALITY_PROBE" and
          value["scope"] == "ACTUAL_LOAD12_COST240_COPIED_CURRENT_ORDER_REMAINDER_ONLY" and
          value["outcome"] in CONCLUSIVE_OUTCOMES and value["failure"] is None,
          "REMAINDER_EVENT_IDENTITY_OR_OPERATION_STATUS_INVALID")
    limits = _validate_limits(value["limits"])
    _need(type(value["sourcePreserved"]) is bool and
          value["sourcePreserved"] is True and
          value["sourceResumption"] == "NOT_RUN" and
          value["ordinaryComparisonComplete"] is False and
          value["layoutQualification"] is False and
          value["routeAdoption"] is False and
          value["playable100"] is False and value["beta"] == "held",
          "REMAINDER_SCOPE_LIMITATIONS_INVALID")

    selector = _validate_selector(value["selector"], limits)
    source = _validate_source(value["source"], limits)
    copy = _validate_copy(value["copy"], source, limits)
    _need(source["digests"]["seeds"] == copy["digests"]["seeds"] and
          source["digests"]["netOrder"] == copy["digests"]["netOrder"] and
          source["digests"]["prefixRows"] == copy["digests"]["prefixRows"] and
          source["digests"]["geometry"] == copy["digests"]["initialGeometry"] and
          source["before"]["vias"] == selector["sourcePrefix"]["vias"] and
          source["before"]["traces"] == selector["sourcePrefix"]["traces"] and
          source["before"]["viaCost"] == selector["sourcePrefix"]["viaCost"] and
          source["before"]["maximumVias"] == selector["sourcePrefix"]["maximumVias"],
          "REMAINDER_SOURCE_COPY_PREFIX_MISMATCH")

    _exact_keys(value["remainder"], REMAINDER_KEYS,
                "REMAINDER_SEARCH_KEYS_INVALID")
    remainder = value["remainder"]
    _need(_integer(remainder["orderings"], 0, limits["orderings"]) and
          _integer(remainder["cumulativeExpansions"],
                   source["before"]["searchBudgetExpanded"],
                   limits["requestExpansions"]) and
          _integer(remainder["remainingRequestBudget"], 0,
                   limits["requestExpansions"]) and
          remainder["remainingRequestBudget"] == limits["requestExpansions"] -
          remainder["cumulativeExpansions"] and
          remainder["phase"] == "CURRENT_ORDER_REMAINDER",
          "REMAINDER_SEARCH_ACCOUNTING_INVALID")
    outcome = value["outcome"]
    route = remainder["status"] == "ROUTE_READY"
    _need(remainder["status"] in ("ROUTE_READY", "ORDERING_BOUNDARY"),
          "REMAINDER_SEARCH_STATUS_INVALID")
    if route:
        _need(remainder["orderings"] < limits["orderings"] and
              remainder["orderings"] == 2 and
              remainder["boundaryType"] is None and
              remainder["boundaryReason"] is None and
              type(remainder["result"]) is dict,
              "REMAINDER_ROUTE_SHAPE_INVALID")
        result = remainder["result"]
        _exact_keys(result, RESULT_KEYS, "REMAINDER_RESULT_KEYS_INVALID")
        _need(_integer(result["pass"], 1, 1) and
              _integer(result["orderings"], 2, 2) and
              _integer(result["expansions"],
                       remainder["cumulativeExpansions"],
                       remainder["cumulativeExpansions"]) and
              _integer(result["vias"], 0, limits["vias"]),
              "REMAINDER_RESULT_LINEAGE_INVALID")
        _identity(result["layoutIdentity"], "REMAINDER_RESULT_LAYOUT_IDENTITY_INVALID")
        _need(result["layoutIdentity"] == copy["layoutIdentity"],
              "REMAINDER_RESULT_COPY_LAYOUT_IDENTITY_MISMATCH")
        _need(outcome in ("FINALIZER_REJECTED", "FINALIZED_SELECTED",
                          "FINALIZED_UNSELECTED"),
              "REMAINDER_ROUTE_OUTCOME_INVALID")
    else:
        _need(_integer(remainder["orderings"], 2, 2) and
              remainder["orderings"] < limits["orderings"] and
              remainder["boundaryType"] == "ORDERING_BOUNDARY" and
              remainder["phase"] == "CURRENT_ORDER_REMAINDER" and
              remainder["result"] is None,
              "REMAINDER_BOUNDARY_SHAPE_INVALID")
        reason = _text(remainder["boundaryReason"], 1, 1024,
                       "REMAINDER_BOUNDARY_REASON_INVALID")
        if reason in ("TOTAL_SEARCH_LIMIT", "QUALITY_LIMIT"):
            terminal_reason = reason
        else:
            # The copied ordering continues beyond the captured LOAD12 branch.
            # Its typed rejection can name a later net in that same ordering.
            net_id, separator, terminal_reason = reason.rpartition(":")
            failure_prefixes = (
                "OPERATION", "OBSERVER", "STALE", "CANCEL", "CANCELLED",
                "CLEANUP", "UNIT", "TIMEOUT", "EXCEPTION", "ERROR", "FAILURE",
            )
            _need(separator == ":" and
                  re.fullmatch(r"[A-Z][A-Z0-9_]{0,127}", net_id) is not None and
                  not any(net_id == prefix or net_id.startswith(prefix + "_")
                          for prefix in failure_prefixes),
                  "REMAINDER_BOUNDARY_REASON_IS_OPERATION_FAILURE")
        _need(terminal_reason in {
            "NO_PATH", "TOTAL_SEARCH_LIMIT", "BRANCH_SEARCH_LIMIT",
            "QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT",
        }, "REMAINDER_BOUNDARY_REASON_INVALID")
        quality_reason = terminal_reason in {
            "QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT",
        }
        _need(outcome == ("QUALITY_BOUNDARY" if quality_reason
                          else "NO_ROUTE_BOUNDARY"),
              "REMAINDER_BOUNDARY_OUTCOME_MISMATCH")

    _exact_keys(value["finalizer"], FINALIZER_KEYS,
                "REMAINDER_FINALIZER_KEYS_INVALID")
    finalizer = value["finalizer"]
    _exact_keys(value["comparator"], COMPARATOR_KEYS,
                "REMAINDER_COMPARATOR_KEYS_INVALID")
    _exact_keys(value["geometry"], GEOMETRY_KEYS,
                "REMAINDER_GEOMETRY_KEYS_INVALID")
    final_route = outcome in ("FINALIZED_SELECTED", "FINALIZED_UNSELECTED")
    oracles = _validate_oracles(value["oracles"], route, final_route, limits)

    if not route:
        _need(outcome in ("NO_ROUTE_BOUNDARY", "QUALITY_BOUNDARY") and
              finalizer == {"status": "NOT_RUN", "reasonType": None, "reason": None} and
              value["comparator"] == {"status": "NOT_RUN", "candidate": None, "winner": None} and
              value["geometry"] == {"raw": None, "final": None} and
              oracles["raw"]["status"] == "NOT_RUN" and
              oracles["final"]["status"] == "NOT_RUN",
              "REMAINDER_BOUNDARY_FINALIZATION_ORACLE_INVALID")
    else:
        route_result = remainder["result"]
        raw_geometry = _validate_geometry_row(value["geometry"]["raw"],
                                              "REMAINDER_RAW_GEOMETRY")
        _need(raw_geometry["layoutIdentity"] == route_result["layoutIdentity"] and
              raw_geometry["viaCount"] == route_result["vias"] and
              raw_geometry["viaCount"] <= limits["vias"] and
              oracles["raw"]["status"] == "PASS",
              "REMAINDER_RAW_GEOMETRY_ORACLE_BINDING_INVALID")
        _need(finalizer["status"] in ("ACCEPTED", "REJECTED"),
              "REMAINDER_FINALIZER_STATUS_INVALID")
        if finalizer["status"] == "REJECTED":
            _need(finalizer["reasonType"] in FINALIZER_REJECTION_TYPES,
                  "REMAINDER_FINALIZER_REASON_TYPE_INVALID")
            _text(finalizer["reason"], 1, 1024,
                  "REMAINDER_FINALIZER_REASON_INVALID")
            _need(outcome == "FINALIZER_REJECTED" and
                  value["geometry"]["final"] is None and
                  oracles["final"]["status"] == "NOT_RUN" and
                  value["comparator"] == {
                      "status": "NOT_RUN", "candidate": None, "winner": None,
                  }, "REMAINDER_FINALIZER_REJECTION_INVALID")
        elif finalizer["status"] == "ACCEPTED":
            _need(finalizer["reasonType"] is None and finalizer["reason"] is None and
                  outcome in ("FINALIZED_SELECTED", "FINALIZED_UNSELECTED"),
                  "REMAINDER_FINALIZER_ACCEPTANCE_INVALID")
            final_geometry = _validate_geometry_row(
                value["geometry"]["final"], "REMAINDER_FINAL_GEOMETRY",
                minimum_vias=0, minimum_traces=0)
            _need(final_geometry["viaCount"] <= limits["vias"] and
                  oracles["final"]["status"] == "PASS",
                  "REMAINDER_FINAL_GEOMETRY_ORACLE_BINDING_INVALID")
            comparator = _validate_comparator(
                value["comparator"], final_geometry["layoutIdentity"],
                finalizer["status"])
            _need(outcome == ("FINALIZED_SELECTED" if comparator["status"] == "SELECTED"
                              else "FINALIZED_UNSELECTED"),
                  "REMAINDER_COMPARATOR_OUTCOME_MISMATCH")


    _need(_finite_number(value["suiteMs"], 1, limits["suiteMs"]) and
          _integer(value["suiteLimitMs"], limits["suiteMs"], limits["suiteMs"]),
          "REMAINDER_SUITE_TIME_INVALID")
    work = _validate_work(value["work"], limits)
    cleanup = _validate_cleanup(value["cleanup"], limits)
    _need(value["suiteMs"] >= work["operationElapsedMs"],
          "REMAINDER_SUITE_SHORTER_THAN_OPERATION")
    return {
        "outcome": outcome,
        "runId": value["runId"],
        "limits": limits,
        "selector": selector,
        "sourcePreserved": True,
        "sourceResumption": "NOT_RUN",
        "remainder": dict(remainder),
        "finalizer": dict(finalizer),
        "comparator": dict(value["comparator"]),
        "geometry": dict(value["geometry"]),
        "oracles": oracles,
        "work": work,
        "cleanup": cleanup,
    }


def _stable_source_check(spec_raw: bytes, spec_path: Path,
                         spec_sha256: str, spec: Mapping[str, Any],
                         context: Mapping[str, Any]) -> None:
    _need(_read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC_RECHECK") == spec_raw and
          _sha256(spec_raw) == spec_sha256,
          "INPUT_SPEC_CHANGED_DURING_VALIDATION")
    source_root = _resolve_path(spec["sourceRoot"], spec_path.parent, "SOURCE_ROOT_RECHECK")
    pins_path = _resolve_path(spec["pinsPath"], spec_path.parent, "SOURCE_PINS_RECHECK")
    pins_raw = _read_bounded(pins_path, MAX_PIN_BYTES, "SOURCE_PINS_RECHECK")
    _need(source_root == context["sourceRoot"] and pins_path == context["pinsPath"] and
          pins_raw == context["pinsRaw"], "SOURCE_INPUT_MANIFEST_CHANGED")
    second = _audit_inputs_manifest(pins_raw, spec, source_root)
    _need(second["pinMap"] == context["pinMap"] and
          second["inputCount"] == context["inputCount"] and
          second["inputBytes"] == context["inputBytes"],
          "SOURCE_INPUTS_CHANGED_DURING_VALIDATION")
    for name, raw in context["artifactRaw"].items():
        again = _read_bounded(context["artifacts"][name + "Path"],
                              MAX_RESULT_BYTES if name == "result" else MAX_CAPTURE_BYTES,
                              "RUN_" + name.upper() + "_RECHECK",
                              allow_empty=(name == "stderr"))
        _need(again == raw, "RUN_" + name.upper() + "_CHANGED_DURING_VALIDATION")


def _validate_artifacts(spec_raw: bytes, spec_path: Path, spec_sha256: str,
                        spec: Mapping[str, Any], context: Mapping[str, Any],
                        own_raw: bytes) -> Dict[str, Any]:
    context = dict(context)
    for label, limit, allow_empty in (
            ("result", MAX_RESULT_BYTES, False),
            ("stdout", MAX_CAPTURE_BYTES, False),
            ("receipt", MAX_CAPTURE_BYTES, False),
            ("stderr", MAX_CAPTURE_BYTES, True)):
        context[label + "Raw"] = _read_bounded(
            context["artifacts"]["resultPath" if label == "result" else label + "Path"],
            limit, "RUN_" + label.upper(), allow_empty=allow_empty)
    context["artifactRaw"] = {
        name: context[name + "Raw"] for name in ("result", "stdout", "receipt", "stderr")
    }
    run_value = _strict_json(context["resultRaw"], "NATIVE_RUN")
    context["stdoutRaw"] = context["stdoutRaw"]
    context["receiptRaw"] = context["receiptRaw"]
    context["stderrRaw"] = context["stderrRaw"]
    native_run = _validate_native_run(run_value, context)
    _console, receipt_lines, event = _validate_console_and_receipt(
        context["stdoutRaw"], context["receiptRaw"], context["stderrRaw"])
    checked = _validate_event(event)
    summary_match = SUMMARY_RE.fullmatch(receipt_lines[1])
    _need(summary_match is not None, "RECEIPT_SUMMARY_ASSERTIONS_INVALID")
    _stable_source_check(spec_raw, spec_path, spec_sha256, spec, context)
    own_after = _read_bounded(Path(os.path.abspath(__file__)), MAX_RESULT_BYTES,
                              "READER_SOURCE_RECHECK")
    _need(own_after == own_raw, "READER_SOURCE_CHANGED_DURING_VALIDATION")
    return {
        "schema": RESULT_SCHEMA,
        "status": "VALIDATED_CONCLUSIVE_EXPERIMENT_OUTCOME",
        "target": TARGET,
        "purpose": PURPOSE,
        "source": {
            "snapshotRevision": context["sourceSnapshotRevision"],
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "inputManifestBytes": context["inputManifestBytes"],
        },
        "nativeRun": {
            "schema": native_run["schema"],
            "suite": native_run["suite"],
            "command": native_run["command"],
            "layoutVersion": native_run["layoutVersion"],
            "dependencyEpoch": native_run["dependencyEpoch"],
            "viaCost": native_run["viaCost"],
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
            "cleanupElapsedMs": native_run["cleanupElapsedMs"],
            "inputManifestBytes": context["inputManifestBytes"],
            "resultSha256": _sha256(context["resultRaw"]),
            "stdoutBytes": len(context["stdoutRaw"]),
            "stdoutSha256": _sha256(context["stdoutRaw"]),
            "receiptBytes": len(context["receiptRaw"]),
            "receiptSha256": _sha256(context["receiptRaw"]),
            "stderrBytes": len(context["stderrRaw"]),
            "stderrSha256": _sha256(context["stderrRaw"]),
        },
        "experiment": checked,
        "limitations": {
            "ordinaryComparisonComplete": False,
            "sourceResumption": "NOT_RUN",
            "routeAdoption": False,
            "layoutQualification": False,
            "independentTwoLayerAccess": "NOT_AVAILABLE",
            "playable100": False,
            "beta": "HELD",
        },
        "readerSourceSha256": _sha256(own_raw),
    }


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _resolve_path(spec_argument, Path.cwd(), "INPUT_SPEC")
    spec_raw = _read_bounded(spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _strict_json(spec_raw, "INPUT_SPEC")
    spec, context = _validate_input_spec(spec, spec_path.parent)
    own_raw = _read_bounded(Path(os.path.abspath(__file__)), MAX_RESULT_BYTES,
                            "READER_SOURCE")
    return _validate_artifacts(spec_raw, spec_path, spec_sha256,
                               spec, context, own_raw)


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
    except Load12RemainderInputError as error:
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
