#!/usr/bin/env python3
"""Read a source-bound current-v28 completed-NO_PATH prefix ledger.

The result describes two fixed relaxed graphs and their captured hole/net
inventories.  It does not establish a production route, PCB layout, electrical
behavior, or Q100 acceptance.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import types
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Set, Tuple


INPUT_SCHEMA = "p07-current-prefix-ledger-input-v1"
RUN_SCHEMA = "p07-current-prefix-native-run-v1"
RESULT_SCHEMA = "p07-current-prefix-ledger-reader-result-v1"
SUITE = "Rb100FixedPrefixCaptureContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
COMMAND = {
    "entry": ENTRY_SCRIPT,
    "suite": SUITE,
    "args": ["--completed-no-path"],
    "environment": {},
}

SOURCE_REVISION = 5
SOURCE_INPUT_COUNT = 1067
SOURCE_JAVA_COUNT = 850
CONTRACT_JAVA_COUNT = 211
SOURCE_INPUT_LIMIT = 512 * 1024 * 1024
PIN_FILE_LIMIT = 16 * 1024 * 1024
SPEC_LIMIT = 1024 * 1024
RESULT_LIMIT = 2 * 1024 * 1024
CAPTURE_LIMIT = 32 * 1024 * 1024
MAX_LINE_BYTES = 16 * 1024 * 1024
MAX_VIAS = 48
MAX_TRANSITIONS = 4
MAX_ORDERINGS = 3
MAX_OPERATION_MS = 90_000
MAX_CLEANUP_MS = 120_000
MAX_WORK_UNITS = 640
MAX_UNIT_MS = 5000
MAX_GRID_CELLS = 250_000
MAX_PAD_COUNT = 246
MAX_POLICY_CANONICAL_BYTES = 4 * 1024 * 1024
MAX_FINGERPRINT_BYTES = 4 * 1024 * 1024
MAX_ROUTE_EXPANSIONS = 160_000_000
MAX_P07_EXPANSIONS = 1_000_000

ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9

CAPTURE_READER_SHA256 = "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"
GRAPH_ORACLE_SHA256 = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
MINIMUM_VIAS_SHA256 = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"

GRAPH_TAG = "P07_FIXED_PREFIX_GRAPH_DATA "
FACT_TAG = "P07_FIXED_PREFIX_CAPTURE_FACT "
OPERATION_TAG = "RB100_FIXED_PREFIX_CAPTURE_OPERATION "
PAIR_TAG = "RB100_FIXED_PREFIX_CAPTURE_PAIR "
NET_CENSUS_TAG = "RB100_FIXED_PREFIX_NET_CENSUS "
SUBSET_TAG = "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS "
SUMMARY_PREFIX = "PASS: RB100 fixed-prefix capture contracts "
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

VARIANTS: Dict[str, Dict[str, Any]] = {
    "SHARED_HYSTERETIC_LINEAR": {
        "nullOperationId": "SHARED_NULL",
        "captureOperationId": "SHARED_CAPTURE",
        "graphCaseId": "RB100_SHARED_HYSTERETIC_LINEAR_NO_PATH_REQ2",
        "netId": "F_DRAIN",
        "nets": 59,
        "selectedPlacementIndex": 2,
        "selectedPlacementAttempt": 0,
        "routeRequestIndex": 2,
        "eligiblePlacementAttempts": [4, 2, 0, 1, 3],
        "eligibilitySkips": [
            "attempt=5;width=929;height=643;cells=597347;reason=GRID_LIMIT",
        ],
        "routeOutcomes": [
            "P07_FULLER_TWO_LAYER@4=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
            "P07_FULLER_TWO_LAYER@2=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
            "P07_FULLER_TWO_LAYER@0=REJECTED:P07_FULLER_TWO_LAYER:F_DRAIN:NO_PATH",
        ],
        "orderingAttempts": [4, 2, 0],
        "primaryRoutingExpansions": 2_970_804,
    },
    "SEPARATE_DIRECT_SWITCHING": {
        "nullOperationId": "SEPARATE_NULL",
        "captureOperationId": "SEPARATE_CAPTURE",
        "graphCaseId": "RB100_SEPARATE_DIRECT_SWITCHING_NO_PATH_REQ0",
        "netId": "A_COIL_LOW",
        "nets": 60,
        "selectedPlacementIndex": 0,
        "selectedPlacementAttempt": 4,
        "routeRequestIndex": 0,
        "eligiblePlacementAttempts": [4, 2, 3, 0, 1],
        "eligibilitySkips": [
            "attempt=5;width=929;height=643;cells=597347;reason=GRID_LIMIT",
        ],
        "routeOutcomes": [
            "P07_FULLER_TWO_LAYER@4=REJECTED:P07_FULLER_TWO_LAYER:A_COIL_LOW:NO_PATH",
            "P07_FULLER_TWO_LAYER@2=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
            "P07_FULLER_TWO_LAYER@3=REJECTED:P07_FULLER_TWO_LAYER:TOTAL_SEARCH_LIMIT",
        ],
        "orderingAttempts": [4, 2, 3],
        "primaryRoutingExpansions": 2_918_222,
    },
}
OPERATION_ORDER = ["SHARED_NULL", "SHARED_CAPTURE", "SEPARATE_NULL", "SEPARATE_CAPTURE"]
OPERATION_IDENTITY = {
    operation_id: (variant, mode)
    for variant, config in VARIANTS.items()
    for mode, operation_id in (("NULL", config["nullOperationId"]),
                               ("CAPTURE", config["captureOperationId"]))
}

STATIC_NATIVE_INPUTS = {
    "scripts/verify-current-contracts.ps1",
    "scripts/VerifierIsolation.psm1",
    "tests/contracts/current-contract-runner-contract.ps1",
    ".tools/gwt-2.7.0/gwt-user-2.7.0.jar",
    ".tools/gwt-2.7.0/gwt-dev-2.7.0.jar",
    "tests/benchmarks/p03-components.tsv",
}
REQUIRED_NATIVE_INPUTS = (
    STATIC_NATIVE_INPUTS
    | {
        "src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java",
        "src/com/lushprojects/circuitjs1/client/PcbNetRouter.java",
        "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java",
        "src/com/lushprojects/circuitjs1/client/ReducedMatrixOrdering.java",
        "src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingChecks.java",
        "src/com/lushprojects/circuitjs1/client/ReducedMatrixOrderingRuntimeChecks.java",
        "tests/contracts/Rb100PhysicalDiagnostics.java",
        "tests/contracts/Rb100FixedPrefixCaptureContractTest.java",
    }
)

SUMMARY_KEYS = frozenset({
    "status", "operations", "nullOperations", "captureOperations", "pairs", "graphs",
    "authoredVariants", "variants", "runId", "rootSeed", "placementSeed",
    "routingSeed", "layoutVersion", "dependencyEpoch", "planVersion", "modelVersion",
    "physicalIntentVersion", "assertions", "suiteMs", "suiteLimitMs", "scope",
    "layoutQualification", "captureSelection",
})
NET_CENSUS_KEYS = frozenset({
    "protocolVersion", "runId", "operationId", "variant", "graphCaseId",
    "graphSha256", "netIds",
})
SUBSET_KEYS = frozenset({
    "protocol", "runId", "variant", "status", "independentTwoLayerAccess",
    "layoutQualification",
})

_SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
_UUID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\Z",
    re.ASCII,
)
_FORBIDDEN_TREE_KEYS = frozenset({
    "path", "paths", "states", "edges", "predecessors", "witnessstates",
    "witnessedgekinds",
})


class LedgerInputError(ValueError):
    """Static error code that never embeds input content or a filesystem path."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise LedgerInputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _reject_duplicate_keys(pairs: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            _fail("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _reject_constant(_value: str) -> None:
    _fail("JSON_NONFINITE_NUMBER")


def _strict_json(raw: bytes, label: str) -> Any:
    try:
        return json.loads(raw.decode("utf-8-sig", errors="strict"),
                          object_pairs_hook=_reject_duplicate_keys,
                          parse_constant=_reject_constant)
    except LedgerInputError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, RecursionError, ValueError, TypeError):
        _fail(label + "_JSON_INVALID")


def _canonical_json(value: Any) -> bytes:
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


def _resolve_path(value: Any, base: Path, code: str) -> Path:
    _need(type(value) is str and 0 < len(value) <= 4096, code + "_PATH_INVALID")
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


def _read_bounded(path: Path, limit: int, code: str, allow_empty: bool = False) -> bytes:
    try:
        _need(not _has_reparse_component(path) and not _is_reparse(path), code + "_REPARSE_PATH")
        info = path.stat()
        _need(path.is_file(), code + "_NOT_FILE")
        _need((0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit, code + "_READ_SIZE_CHANGED")
        return raw
    except LedgerInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _safe_relative_path(value: Any) -> str:
    _need(type(value) is str and 0 < len(value) <= 4096, "SOURCE_PATH_INVALID")
    normalized = value.replace("\\", "/")
    _need(value == normalized and not normalized.startswith("/") and
          re.match(r"^[A-Za-z]:", normalized) is None and
          all(part not in ("", ".", "..") for part in normalized.split("/")),
          "SOURCE_PATH_INVALID")
    return normalized


def _closed_source_paths(source_root: Path, capture_helper: types.ModuleType) -> Set[str]:
    try:
        source_java = capture_helper._closed_java_paths(source_root, "src", recursive=True)
        contract_java = capture_helper._closed_java_paths(
            source_root, "tests/contracts", recursive=False)
    except Exception:
        _fail("SOURCE_CENSUS_HELPER_INVALID")
    _need(len(source_java) == SOURCE_JAVA_COUNT, "SOURCE_CENSUS_SOURCE_JAVA_COUNT_INVALID")
    _need(len(contract_java) == CONTRACT_JAVA_COUNT, "SOURCE_CENSUS_CONTRACT_JAVA_COUNT_INVALID")
    paths = source_java | contract_java
    for relative in STATIC_NATIVE_INPUTS:
        path = source_root.joinpath(*relative.split("/"))
        _need(not _has_reparse_component(path), "SOURCE_CENSUS_REPARSE_PATH")
        try:
            resolved = path.resolve(strict=True)
            resolved_relative = resolved.relative_to(source_root).as_posix()
        except (OSError, RuntimeError, ValueError):
            _fail("SOURCE_CENSUS_STATIC_INPUT_UNAVAILABLE")
        _need(resolved.is_file() and resolved_relative == relative,
              "SOURCE_CENSUS_STATIC_INPUT_INVALID")
        paths.add(relative)
    _need(len(paths) == SOURCE_INPUT_COUNT, "SOURCE_CENSUS_TOTAL_COUNT_INVALID")
    return paths


def _validate_spec_identity(spec: Any) -> None:
    _exact_keys(spec, {
        "schema", "sourceRoot", "pinsPath", "pinsSha256", "inputCount", "inputBytes",
        "sourceSnapshotRevision", "resultPath", "stdoutPath", "receiptPath", "stderrPath",
    }, "INPUT_SPEC_KEYS_INVALID")
    _need(spec["schema"] == INPUT_SCHEMA and
          spec["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(spec["sourceSnapshotRevision"]) is int and
          spec["inputCount"] == SOURCE_INPUT_COUNT and type(spec["inputCount"]) is int and
          _integer(spec["inputBytes"], 1, SOURCE_INPUT_LIMIT) and
          type(spec["pinsSha256"]) is str and _SHA256_RE.fullmatch(spec["pinsSha256"]) is not None,
          "INPUT_SPEC_IDENTITY_INVALID")


def _validate_input_spec(spec: Any, spec_directory: Path,
                         capture_helper: types.ModuleType) -> Dict[str, Any]:
    _validate_spec_identity(spec)
    source_root = _resolve_path(spec["sourceRoot"], spec_directory, "SOURCE_ROOT")
    _need(source_root.is_dir(), "SOURCE_ROOT_NOT_DIRECTORY")
    pins_path = _resolve_path(spec["pinsPath"], spec_directory, "SOURCE_PINS")
    pins_raw = _read_bounded(pins_path, PIN_FILE_LIMIT, "SOURCE_PINS")
    _need(_sha256(pins_raw) == spec["pinsSha256"], "SOURCE_PINS_HASH_MISMATCH")
    try:
        pin_rows = _strict_json(pins_raw, "SOURCE_PINS")
    except LedgerInputError:
        raise
    _need(type(pin_rows) is list and len(pin_rows) == SOURCE_INPUT_COUNT,
          "SOURCE_PINS_COUNT_INVALID")
    closed_paths = _closed_source_paths(source_root, capture_helper)
    pin_map: Dict[str, Dict[str, Any]] = {}
    total_bytes = 0
    for row in pin_rows:
        _exact_keys(row, {"path", "bytes", "sha256"}, "SOURCE_PIN_KEYS_INVALID")
        relative = _safe_relative_path(row["path"])
        _need(relative not in pin_map and _integer(row["bytes"], 0, SOURCE_INPUT_LIMIT) and
              type(row["sha256"]) is str and _SHA256_RE.fullmatch(row["sha256"]) is not None,
              "SOURCE_PIN_INVALID")
        path = source_root.joinpath(*relative.split("/"))
        _need(not _has_reparse_component(path), "SOURCE_PIN_REPARSE_PATH")
        try:
            resolved = path.resolve(strict=True)
            relative_resolved = resolved.relative_to(source_root).as_posix()
            raw = _read_bounded(resolved, SOURCE_INPUT_LIMIT, "SOURCE_PIN_CONTENT", allow_empty=True)
        except (OSError, RuntimeError, ValueError):
            _fail("SOURCE_PIN_UNAVAILABLE")
        _need(resolved.is_file() and relative_resolved == relative and
              len(raw) == row["bytes"] and _sha256(raw) == row["sha256"],
              "SOURCE_PIN_CONTENT_MISMATCH")
        total_bytes += len(raw)
        _need(total_bytes <= SOURCE_INPUT_LIMIT, "SOURCE_PIN_BYTES_OVERFLOW")
        pin_map[relative] = {"bytes": len(raw), "sha256": row["sha256"]}
    _need(total_bytes == spec["inputBytes"], "SOURCE_PIN_BYTE_COUNT_MISMATCH")
    _need(REQUIRED_NATIVE_INPUTS <= set(pin_map), "SOURCE_PINS_OMIT_REQUIRED_NATIVE_INPUT")
    _need(set(pin_map) == closed_paths, "SOURCE_PINS_CENSUS_MISMATCH")
    artifacts = {
        name: _resolve_path(spec[name], spec_directory, name.upper())
        for name in ("resultPath", "stdoutPath", "receiptPath", "stderrPath")
    }
    return {
        "sourceRoot": source_root,
        "pinsPath": pins_path,
        "pinsSha256": _sha256(pins_raw),
        "inputCount": len(pin_map),
        "inputBytes": total_bytes,
        "sourceSnapshotRevision": SOURCE_REVISION,
        "pinMap": pin_map,
        "artifacts": artifacts,
    }


def _expected_command() -> Dict[str, Any]:
    return COMMAND


def _validate_run_result(result: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes, stderr_raw: bytes) -> None:
    keys = {
        "schema", "source", "suite", "cohortId", "command", "layoutVersion",
        "dependencyEpoch", "sourceSnapshotRevision", "inputCount", "inputBytes",
        "inputPinsSha256", "inputAudit", "exitCode", "nativeExitCode",
        "terminationProven", "cleanupStatus", "cleanupElapsedMs", "nativeLimitMs",
        "outerLimitMs", "processId", "processStartTicks", "elapsedSeconds",
        "requestedExecutable", "stdoutBytes", "stdoutSha256", "stderrBytes",
        "stderrSha256", "receiptBytes", "receiptSha256",
    }
    _exact_keys(result, keys, "RUN_RESULT_KEYS_INVALID")
    _need(result["schema"] == RUN_SCHEMA and result["source"] == "candidate" and
          result["suite"] == SUITE and result["cohortId"] is None and
          result["command"] == _expected_command(), "RUN_RESULT_IDENTITY_INVALID")
    _need(type(result["layoutVersion"]) is int and result["layoutVersion"] == LAYOUT_VERSION and
          result["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(result["sourceSnapshotRevision"]) is int and
          result["sourceSnapshotRevision"] == SOURCE_REVISION and
          type(result["inputCount"]) is int and result["inputCount"] == context["inputCount"] and
          type(result["inputBytes"]) is int and result["inputBytes"] == context["inputBytes"],
          "RUN_RESULT_SOURCE_EPOCH_INVALID")
    _need(result["inputPinsSha256"] == context["pinsSha256"] and
          result["inputAudit"] == "PASS_BEFORE_AND_AFTER", "RUN_RESULT_SOURCE_BINDING_INVALID")
    _need(type(result["exitCode"]) is int and result["exitCode"] == 0 and
          type(result["nativeExitCode"]) is int and result["nativeExitCode"] == 0 and
          result["terminationProven"] is True and result["cleanupStatus"] == "PASS" and
          result["cleanupElapsedMs"] is None, "RUN_RESULT_EXIT_OR_CLEANUP_INVALID")
    _need(result["nativeLimitMs"] == 900_000 and result["outerLimitMs"] == 1_800_000 and
          result["requestedExecutable"] == "powershell.exe", "RUN_RESULT_BUDGET_INVALID")
    _need(_integer(result["processId"], 1, (1 << 63) - 1) and
          type(result["processStartTicks"]) is str and
          re.fullmatch(r"[1-9][0-9]{0,19}", result["processStartTicks"], re.ASCII) is not None and
          type(result["elapsedSeconds"]) in (int, float) and
          math.isfinite(result["elapsedSeconds"]) and 0 < result["elapsedSeconds"] <= 1800,
          "RUN_RESULT_PROCESS_INVALID")
    for label, raw in (("stdout", stdout_raw), ("receipt", receipt_raw), ("stderr", stderr_raw)):
        size = result[label + "Bytes"]
        digest = result[label + "Sha256"]
        _need(_integer(size, 0, CAPTURE_LIMIT) and size == len(raw) and
              type(digest) is str and _SHA256_RE.fullmatch(digest) is not None and
              digest == _sha256(raw), "RUN_RESULT_" + label.upper() + "_BINDING_INVALID")


def _decode_lines(raw: bytes, code: str) -> List[str]:
    try:
        return raw.decode("utf-8-sig", errors="strict").splitlines()
    except UnicodeDecodeError:
        _fail(code + "_UTF8_INVALID")


def _parse_tagged_json(line: str, prefix: str, code: str) -> Any:
    _need(line.startswith(prefix), code + "_PREFIX_INVALID")
    try:
        body = line[len(prefix):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail(code + "_ENCODING_INVALID")
    _need(0 < len(body) <= MAX_LINE_BYTES, code + "_SIZE_INVALID")
    return _strict_json(body, code)


def _parse_graph_line(line: str, oracle: types.ModuleType) -> Dict[str, Any]:
    _need(line.startswith(GRAPH_TAG), "GRAPH_LINE_PREFIX_INVALID")
    try:
        raw = line[len(GRAPH_TAG):].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        _fail("GRAPH_LINE_ENCODING_INVALID")
    _need(0 < len(raw) <= oracle.MAX_INPUT_BYTES, "GRAPH_LINE_SIZE_INVALID")
    payload = _strict_json(raw, "GRAPH_LINE")
    _need(_canonical_json(payload) == raw, "GRAPH_LINE_NONCANONICAL")
    try:
        graph = oracle.parse_graph_bytes(raw)
    except Exception:
        _fail("GRAPH_LINE_INVALID")
    return {"raw": raw, "payload": payload, "graph": graph}


def _validate_layout_output(value: Any) -> None:
    common = {
        "layoutReturned", "zeroOutput", "fingerprint", "outline", "rawSegments",
        "topTraceCount", "bottomTraceCount", "totalTraceCount", "holeCount", "viaCount",
        "platedHoleCount", "nonPlatedHoleCount",
    }
    _exact_keys(value, common, "OPERATION_LAYOUT_OUTPUT_KEYS_INVALID")
    _need(value["layoutReturned"] is False and value["zeroOutput"] is True and
          value["fingerprint"] is None and value["outline"] is None,
          "OPERATION_LAYOUT_OUTPUT_INVALID")
    count_names = (
        "rawSegments", "topTraceCount", "bottomTraceCount", "totalTraceCount", "holeCount",
        "viaCount", "platedHoleCount", "nonPlatedHoleCount",
    )
    for name in count_names:
        _need(_integer(value[name], 0, MAX_ROUTE_EXPANSIONS), "OPERATION_LAYOUT_COUNT_INVALID")
    _need(value["totalTraceCount"] == value["topTraceCount"] + value["bottomTraceCount"] and
          value["holeCount"] == value["viaCount"] + value["platedHoleCount"] +
          value["nonPlatedHoleCount"] and all(value[name] == 0 for name in count_names),
          "OPERATION_ZERO_LAYOUT_CENSUS_INVALID")


def _java_string_length(value: str) -> int:
    return len(value.encode("utf-16-le", errors="strict")) // 2


def _valid_identity_text(value: Any) -> bool:
    return (type(value) is str and 0 < len(value) <= 512 and
            all(ord(ch) >= 0x20 and ord(ch) != 0x7F for ch in value))


def _java_string_sort_key(value: str) -> bytes:
    return value.encode("utf-16-be", errors="strict")


def _validate_completed_ordering_receipt(canonical: str, expected: Mapping[str, Any],
                                         operation: Mapping[str, Any]) -> None:
    marker = ";orderingSearchReceipts=["
    _need(";packingProbe=" not in canonical, "OPERATION_PACKING_PROBE_UNEXPECTED")
    _need(canonical.count(marker) == 1 and canonical.endswith("]"),
          "OPERATION_ORDERING_RECEIPTS_MISSING")
    body = canonical.split(marker, 1)[1][:-1]
    entries = body.split(",") if body else []
    attempts = expected["orderingAttempts"]
    _need(len(entries) == len(attempts), "OPERATION_ORDERING_RECEIPT_COUNT_INVALID")
    expected_fields = (
        "attempt", "startedOrderings", "completedOrderings", "yieldedOrderings",
        "finalizedOrderings", "selectedOrdering", "expansions", "guidanceStarted",
        "comparisonComplete", "termination",
    )
    parsed = []
    for index, entry in enumerate(entries):
        fields = entry.split(";")
        _need(len(fields) == len(expected_fields), "OPERATION_ORDERING_RECEIPT_FORMAT_INVALID")
        values: Dict[str, str] = {}
        for field in fields:
            _need("=" in field, "OPERATION_ORDERING_RECEIPT_FORMAT_INVALID")
            key, item = field.split("=", 1)
            _need(key not in values, "OPERATION_ORDERING_RECEIPT_FORMAT_INVALID")
            values[key] = item
        _need(tuple(values) == expected_fields and values["attempt"] == str(attempts[index]),
              "OPERATION_ORDERING_RECEIPT_ORDER_INVALID")
        numeric = ("startedOrderings", "completedOrderings", "yieldedOrderings",
                   "finalizedOrderings", "selectedOrdering", "expansions")
        _need(all(re.fullmatch(r"-?(?:0|[1-9][0-9]*)", values[name], re.ASCII) is not None
                  for name in numeric), "OPERATION_ORDERING_RECEIPT_NUMBER_INVALID")
        row = {name: int(values[name]) for name in numeric}
        _need(1 <= row["startedOrderings"] <= MAX_ORDERINGS and
              1 <= row["completedOrderings"] <= row["startedOrderings"] and
              0 <= row["yieldedOrderings"] <= row["completedOrderings"] and
              0 <= row["finalizedOrderings"] <= row["yieldedOrderings"] and
              -1 <= row["selectedOrdering"] < MAX_ORDERINGS and row["expansions"] >= 0 and
              values["guidanceStarted"] in ("true", "false") and
              values["comparisonComplete"] in ("true", "false"),
              "OPERATION_ORDERING_RECEIPT_RANGE_INVALID")
        row["attempt"] = int(values["attempt"])
        row["comparisonComplete"] = values["comparisonComplete"] == "true"
        row["termination"] = values["termination"]
        parsed.append(row)

    target_index = expected["routeRequestIndex"]
    target = parsed[target_index]
    _need(target["attempt"] == expected["selectedPlacementAttempt"] and
          target["startedOrderings"] == MAX_ORDERINGS and
          target["completedOrderings"] == MAX_ORDERINGS and
          target["comparisonComplete"] is True and
          target["termination"] == expected["netId"] + ":NO_PATH",
          "OPERATION_COMPLETED_NO_PATH_RECEIPT_INVALID")
    for index, row in enumerate(parsed):
        if index == target_index:
            continue
        outcome = expected["routeOutcomes"][index]
        if outcome.endswith(":TOTAL_SEARCH_LIMIT"):
            _need(row["termination"] == "TOTAL_SEARCH_LIMIT" and
                  row["comparisonComplete"] is False,
                  "OPERATION_TOTAL_LIMIT_RECEIPT_INVALID")
    _need(sum(row["expansions"] for row in parsed) ==
          operation["primaryRoutingExpansions"] and
          sum(row["startedOrderings"] for row in parsed) ==
          operation["primaryRoutingOrders"], "OPERATION_ORDERING_STATISTICS_MISMATCH")


def _validate_route_candidate_census(operation: Mapping[str, Any],
                                     expected: Mapping[str, Any]) -> None:
    _need(type(operation["routeEligiblePlacementAttempts"]) is list and
          operation["routeEligiblePlacementAttempts"] == expected["eligiblePlacementAttempts"],
          "OPERATION_CANDIDATE_ORDER_INVALID")
    _need(type(operation["routeEligibilitySkips"]) is list and
          operation["routeEligibilitySkips"] == expected["eligibilitySkips"],
          "OPERATION_ELIGIBILITY_SKIP_INVALID")
    _need(type(operation["routeOutcomes"]) is list and
          operation["routeOutcomes"] == expected["routeOutcomes"] and
          type(operation["firstP07RequestAttempt"]) is int and
          operation["firstP07RequestAttempt"] == 4,
          "OPERATION_REQUEST_ORDER_INVALID")


def _validate_operation(operation: Any, operation_id: str, run_id: str,
                        capture_helper: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(operation, capture_helper.OPERATION_KEYS, "OPERATION_KEYS_INVALID")
    _need(operation_id in OPERATION_IDENTITY, "OPERATION_ID_UNKNOWN")
    variant, mode = OPERATION_IDENTITY[operation_id]
    expected = VARIANTS[variant]
    _need(type(operation["protocolVersion"]) is int and operation["protocolVersion"] == 1 and
          operation["runId"] == run_id and operation["operationId"] == operation_id and
          operation["variant"] == variant and operation["mode"] == mode and
          operation["status"] == "PASS" and operation["terminalStatus"] == "EXHAUSTED" and
          operation["policyOutcome"] == "REJECTED" and
          operation["selectedRoutePolicy"] is None and
          type(operation["policySelectedPlacementAttempt"]) is int and
          operation["policySelectedPlacementAttempt"] == -1 and
          operation["layoutQualification"] is False,
          "OPERATION_IDENTITY_OR_STATUS_INVALID")
    for name, wanted in (("rootSeed", ROOT_SEED), ("placementSeed", PLACEMENT_SEED),
                         ("routingSeed", ROUTING_SEED)):
        _need(type(operation[name]) is str and operation[name] == wanted,
              "OPERATION_SEED_INVALID")
    _need(type(operation["layoutVersion"]) is int and
          operation["layoutVersion"] == LAYOUT_VERSION and
          operation["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(operation["planVersion"]) is int and operation["planVersion"] == PLAN_VERSION and
          type(operation["modelVersion"]) is int and operation["modelVersion"] == MODEL_VERSION and
          type(operation["physicalIntentVersion"]) is int and
          operation["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION,
          "OPERATION_EPOCH_INVALID")
    _validate_route_candidate_census(operation, expected)
    _need(_integer(operation["primaryRoutingOrders"], 1, 2400) and
          type(operation["primaryRoutingExpansions"]) is int and
          operation["primaryRoutingExpansions"] == expected["primaryRoutingExpansions"],
          "OPERATION_ROUTING_STATISTICS_INVALID")
    canonical = operation["policyCanonical"]
    _need(type(canonical) is str and 0 < len(canonical.encode("utf-8")) <=
          MAX_POLICY_CANONICAL_BYTES and
          _integer(operation["policyCanonicalLength"], 1, MAX_POLICY_CANONICAL_BYTES) and
          _java_string_length(canonical) == operation["policyCanonicalLength"] and
          type(operation["policyCanonicalSha256"]) is str and
          _SHA256_RE.fullmatch(operation["policyCanonicalSha256"]) is not None and
          _sha256(canonical.encode("utf-8")) == operation["policyCanonicalSha256"],
          "OPERATION_POLICY_CANONICAL_INVALID")
    canonical_candidates = ";routeEligiblePlacementAttempts=[" + ", ".join(
        str(value) for value in expected["eligiblePlacementAttempts"]) + "]"
    canonical_routes = ";routeOutcomes=[" + ", ".join(expected["routeOutcomes"]) + "]"
    _need(canonical_candidates in canonical and canonical_routes in canonical,
          "OPERATION_CANONICAL_CENSUS_MISMATCH")
    _validate_completed_ordering_receipt(canonical, expected, operation)
    _need(_integer(operation["components"], 100, 100) and
          _integer(operation["pads"], MAX_PAD_COUNT, MAX_PAD_COUNT) and
          _integer(operation["nets"], expected["nets"], expected["nets"]),
          "OPERATION_LOGICAL_CENSUS_INVALID")
    _validate_layout_output(operation["layoutOutput"])
    _need(_integer(operation["operationMs"], 0, MAX_OPERATION_MS) and
          _integer(operation["cleanupMs"], 0, MAX_CLEANUP_MS) and
          operation["cleanupStatus"] == "PASS" and
          _integer(operation["workUnits"], 0, MAX_WORK_UNITS) and
          _integer(operation["maxUnitMs"], 0, MAX_UNIT_MS),
          "OPERATION_LIMIT_INVALID")
    _need(all(type(operation[name]) is str and operation[name] in ("", "none")
              for name in ("failurePhase", "failureType", "cleanupFailureType")) and
          operation["scope"] == "OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY",
          "OPERATION_FAILURE_OR_SCOPE_INVALID")
    return operation


def _grid_cell(x: int, y: int, origin_x: int, origin_y: int,
               pitch: int, width: int, height: int, label: str) -> int:
    dx, dy = x - origin_x, y - origin_y
    _need(pitch > 0 and dx >= 0 and dy >= 0 and dx % pitch == 0 and dy % pitch == 0,
          label + "_GRID_ALIGNMENT_INVALID")
    cell_x, cell_y = dx // pitch, dy // pitch
    _need(cell_x < width and cell_y < height, label + "_GRID_BOUNDS_INVALID")
    return cell_y * width + cell_x


def _validate_prefix_inventory(fact: Any, graph: Any, net_ids: Sequence[str],
                               capture_helper: types.ModuleType) -> Dict[str, Any]:
    """Validate captured all-hole census, owners, centers, and graph VIA mask joins."""
    try:
        capture_helper._validate_prefix_holes(fact)
    except Exception:
        _fail("PREFIX_HOLE_INVENTORY_INVALID")
    _need(type(fact["prefixHoleCount"]) is int and 0 <= fact["prefixHoleCount"] <= MAX_VIAS and
          type(fact["prefixViaCount"]) is int and 0 <= fact["prefixViaCount"] <= MAX_VIAS,
          "PREFIX_HOLE_BUDGET_INVALID")
    net_set = set(net_ids)
    centers: Set[Tuple[int, int]] = set()
    by_cell: Dict[int, Dict[str, Any]] = {}
    per_net: Dict[str, Dict[str, int]] = {}
    unowned = {"holeCount": 0, "viaCount": 0, "platedHoleCount": 0,
               "nonPlatedHoleCount": 0}
    via_mask_joins = {"sameNetExistingHole": 0, "foreignOrUnownedHoleBlocked": 0,
                      "unoccupiedPossibleSite": 0}
    for hole in fact["prefixHoles"]:
        center = (hole["x"], hole["y"])
        _need(center not in centers, "PREFIX_HOLE_CENTER_DUPLICATE")
        centers.add(center)
        owner = hole["netId"]
        if owner is not None:
            _need(type(owner) is str and owner in net_set, "PREFIX_HOLE_OWNER_UNKNOWN")
            tally = per_net.setdefault(owner, {
                "holeCount": 0, "viaCount": 0, "platedHoleCount": 0,
                "nonPlatedHoleCount": 0,
            })
        else:
            _need(hole["kind"] == "NON_PLATED", "PREFIX_HOLE_OWNER_MISSING")
            tally = unowned
        tally["holeCount"] += 1
        tally[{"VIA": "viaCount", "PLATED": "platedHoleCount",
               "NON_PLATED": "nonPlatedHoleCount"}[hole["kind"]]] += 1
        try:
            cell = _grid_cell(hole["x"], hole["y"], fact["gridOriginX"],
                              fact["gridOriginY"], fact["gridPitch"], graph.width,
                              graph.height, "PREFIX_VIA")
        except LedgerInputError as error:
            if hole["kind"] == "VIA":
                raise
            # A non-grid plated/mechanical hole is not a graph-cell VIA target.
            continue
        by_cell[cell] = hole
    for cell, hole in by_cell.items():
        if hole["netId"] == fact["netId"]:
            _need(graph.vias[cell] is True, "PREFIX_SAME_NET_VIA_MASK_MISMATCH")
            via_mask_joins["sameNetExistingHole"] += 1
        else:
            _need(graph.vias[cell] is False, "PREFIX_FOREIGN_NET_VIA_NOT_BLOCKED")
            via_mask_joins["foreignOrUnownedHoleBlocked"] += 1
    occupied_via_cells = set(by_cell)
    for cell, allowed in enumerate(graph.vias):
        if allowed and cell not in occupied_via_cells:
            via_mask_joins["unoccupiedPossibleSite"] += 1
    _need(fact["netId"] in net_set, "CAPTURE_NET_NOT_IN_CENSUS")
    _need(len(fact["prefixHoles"]) == fact["prefixHoleCount"],
          "PREFIX_HOLE_TOTAL_MISMATCH")
    totals = {"holeCount": fact["prefixHoleCount"],
              "viaCount": fact["prefixHoleCountsByKind"]["VIA"],
              "platedHoleCount": fact["prefixHoleCountsByKind"]["PLATED"],
              "nonPlatedHoleCount": fact["prefixHoleCountsByKind"]["NON_PLATED"]}
    _need(all(sum(row[field] for row in per_net.values()) + unowned[field] == total
              for field, total in totals.items()), "PREFIX_HOLE_OWNER_TOTALS_MISMATCH")
    return {
        "prefixHoleCount": fact["prefixHoleCount"],
        "prefixViaCount": fact["prefixViaCount"],
        "prefixHoleCountsByKind": dict(fact["prefixHoleCountsByKind"]),
        "uniqueHoleCenters": len(centers),
        "byNet": {net_id: per_net[net_id]
                  for net_id in sorted(per_net, key=_java_string_sort_key)},
        "unownedMechanical": unowned,
        "viaMaskJoins": via_mask_joins,
    }


def _validate_fact(fact: Any, graph_entry: Mapping[str, Any], operation: Mapping[str, Any],
                   run_id: str, net_census: Mapping[str, Any],
                   capture_helper: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(fact, capture_helper.FACT_KEYS, "CAPTURE_FACT_KEYS_INVALID")
    variant = operation["variant"]
    expected = VARIANTS[variant]
    graph = graph_entry["graph"]
    _need(type(fact["protocolVersion"]) is int and fact["protocolVersion"] == 1 and
          fact["runId"] == run_id and fact["operationId"] == operation["operationId"] and
          fact["mode"] == "CAPTURE" and fact["variant"] == variant and
          fact["graphCaseId"] == expected["graphCaseId"] and
          fact["layoutQualification"] is False,
          "CAPTURE_FACT_IDENTITY_INVALID")
    for name, wanted in (("rootSeed", ROOT_SEED), ("placementSeed", PLACEMENT_SEED),
                         ("routingSeed", ROUTING_SEED)):
        _need(type(fact[name]) is str and fact[name] == wanted,
              "CAPTURE_FACT_SEED_INVALID")
    _need(type(fact["layoutVersion"]) is int and fact["layoutVersion"] == LAYOUT_VERSION and
          fact["dependencyEpoch"] == DEPENDENCY_EPOCH and
          type(fact["planVersion"]) is int and fact["planVersion"] == PLAN_VERSION and
          type(fact["modelVersion"]) is int and fact["modelVersion"] == MODEL_VERSION and
          type(fact["physicalIntentVersion"]) is int and
          fact["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION,
          "CAPTURE_FACT_EPOCH_INVALID")
    _need(fact["routeEligiblePlacementAttempts"] == expected["eligiblePlacementAttempts"] and
          type(fact["routeEligiblePlacementAttempts"]) is list and
          type(fact["selectedPlacementIndex"]) is int and
          fact["selectedPlacementIndex"] == expected["selectedPlacementIndex"] and
          type(fact["selectedPlacementAttempt"]) is int and
          fact["selectedPlacementAttempt"] == expected["selectedPlacementAttempt"] and
          fact["routeRequestIndex"] == expected["routeRequestIndex"] and
          type(fact["routeRequestIndex"]) is int and
          fact["routeEligiblePlacementAttempts"][fact["selectedPlacementIndex"]] ==
          fact["selectedPlacementAttempt"], "CAPTURE_FACT_SELECTOR_INVALID")
    _need(fact["netId"] == expected["netId"] and type(fact["netId"]) is str and
          fact["terminalReason"] == "NO_PATH" and
          fact["scope"] == "ACTUAL_GENERATOR_FIXED_PREFIX_OBSERVATION_ONLY" and
          fact["reachabilityClaim"] is False,
          "CAPTURE_FACT_TARGET_INVALID")
    _need(_integer(fact["components"], 100, 100) and
          _integer(fact["pads"], MAX_PAD_COUNT, MAX_PAD_COUNT) and
          _integer(fact["nets"], expected["nets"], expected["nets"]) and
          fact["components"] == operation["components"] and
          fact["pads"] == operation["pads"] and fact["nets"] == operation["nets"] and
          len(net_census["netIds"]) == fact["nets"],
          "CAPTURE_FACT_LOGICAL_CENSUS_INVALID")
    _need(net_census["runId"] == run_id and
          net_census["operationId"] == operation["operationId"] and
          net_census["variant"] == variant and
          net_census["graphCaseId"] == fact["graphCaseId"] and
          net_census["graphSha256"] == fact["graphSha256"] == graph.graph_sha256,
          "NET_CENSUS_CAPTURE_JOIN_INVALID")
    for name in ("rawPlacementFingerprint", "prefixGeometryFingerprint"):
        text = fact[name]
        length_key = name + "Length"
        sha_key = name + "Sha256"
        _need(type(text) is str and 0 < len(text.encode("utf-8")) <= MAX_FINGERPRINT_BYTES and
              type(fact[length_key]) is int and 1 <= fact[length_key] <= MAX_FINGERPRINT_BYTES and
              _java_string_length(text) == fact[length_key] and
              type(fact[sha_key]) is str and _SHA256_RE.fullmatch(fact[sha_key]) is not None and
              _sha256(text.encode("utf-8")) == fact[sha_key],
              "CAPTURE_FACT_FINGERPRINT_INVALID")
    _need(type(fact["graphSha256"]) is str and fact["graphSha256"] == graph.graph_sha256 and
          _integer(fact["graphBytes"], 1, 8 * 1024 * 1024) and
          fact["graphBytes"] == len(graph_entry["raw"]) and
          fact["graphCaseId"] == graph.case_id and
          fact["gridWidth"] == graph.width and fact["gridHeight"] == graph.height and
          fact["cellCount"] == graph.cells and fact["gridWidth"] * fact["gridHeight"] ==
          fact["cellCount"] and fact["primaryLayer"] == graph.primary,
          "CAPTURE_FACT_GRAPH_JOIN_INVALID")
    bounded = (
        ("gridWidth", 1, MAX_GRID_CELLS), ("gridHeight", 1, MAX_GRID_CELLS),
        ("gridOriginX", -(1 << 31), (1 << 31) - 1),
        ("gridOriginY", -(1 << 31), (1 << 31) - 1), ("gridPitch", 10, 10),
        ("cellCount", 1, MAX_GRID_CELLS), ("primaryLayer", 1, 1),
        ("startCell", 0, graph.cells - 1), ("initialFacesMask", 3, 3),
        ("pass", 0, 2), ("branchOrdinal", 1, MAX_PAD_COUNT),
        ("startX", -(1 << 31), (1 << 31) - 1),
        ("startY", -(1 << 31), (1 << 31) - 1),
        ("rootX", -(1 << 31), (1 << 31) - 1),
        ("rootY", -(1 << 31), (1 << 31) - 1),
        ("prefixHoleCount", 0, MAX_VIAS), ("prefixViaCount", 0, MAX_VIAS),
        ("branchExpanded", 0, 100_000), ("totalRouteExpansions", 0, MAX_P07_EXPANSIONS),
        ("goalsTop", 0, graph.cells), ("goalsBottom", 0, graph.cells),
        ("captureWorkUnits", 1, 4 * MAX_WORK_UNITS),
        ("copyEntries", 1, 32 * graph.state_count),
        ("goalProbes", 1, 8 * graph.state_count),
        ("ordinaryEdgeProbes", 1, 8 * graph.state_count),
        ("viaProbes", 1, 4 * graph.state_count),
        ("comparisonEntries", 1, 32 * graph.state_count),
        ("captureActiveElapsedMillis", 0, MAX_OPERATION_MS),
        ("serializationElapsedMillis", 0, MAX_OPERATION_MS),
    )
    for name, minimum, maximum in bounded:
        _need(_integer(fact[name], minimum, maximum), "CAPTURE_FACT_COUNTER_INVALID")
    expected_edge_probes = 4 * ((graph.width - 1) * graph.height +
                                graph.width * (graph.height - 1))
    _need(fact["branchExpanded"] <= fact["totalRouteExpansions"] and
          fact["goalProbes"] == 2 * graph.cells and
          fact["ordinaryEdgeProbes"] == expected_edge_probes and
          fact["viaProbes"] == graph.cells and
          fact["captureWorkUnits"] <= 4 * operation["workUnits"] and
          fact["captureActiveElapsedMillis"] + fact["serializationElapsedMillis"] <=
          operation["operationMs"], "CAPTURE_FACT_COUNTER_RELATION_INVALID")
    for name in ("netId", "startPadId", "rootPadId", "terminalReason"):
        _need(type(fact[name]) is str and 0 < len(fact[name]) <= 1024,
              "CAPTURE_FACT_STRING_INVALID")
    reached = fact["reachedPadIds"]
    pth = fact["reachedPthPadIds"]
    _need(type(reached) is list and 0 < len(reached) <= fact["pads"] and
          all(type(item) is str and 0 < len(item) <= 512 for item in reached) and
          len(reached) == len(set(reached)) and type(pth) is list and
          all(type(item) is str and 0 < len(item) <= 512 for item in pth) and
          len(pth) <= len(reached) and len(pth) == len(set(pth)) and
          set(pth) <= set(reached) and fact["rootPadId"] in reached and
          fact["rootPadId"] not in pth, "CAPTURE_FACT_PAD_LINEAGE_INVALID")
    start_cell = _grid_cell(fact["startX"], fact["startY"], fact["gridOriginX"],
                            fact["gridOriginY"], fact["gridPitch"], graph.width,
                            graph.height, "CAPTURE_START")
    root_cell = _grid_cell(fact["rootX"], fact["rootY"], fact["gridOriginX"],
                           fact["gridOriginY"], fact["gridPitch"], graph.width,
                           graph.height, "CAPTURE_ROOT")
    _need(start_cell == fact["startCell"] and start_cell != root_cell and
          list(graph.starts) == [face * graph.cells + start_cell for face in range(2)] and
          sum(graph.goals[:graph.cells]) == fact["goalsTop"] and
          sum(graph.goals[graph.cells:]) == fact["goalsBottom"] and
          fact["goalsTop"] > 0 and fact["goalsBottom"] > 0 and
          graph.goals[root_cell] and graph.goals[graph.cells + root_cell],
          "CAPTURE_FACT_GRAPH_GEOMETRY_JOIN_INVALID")
    inventory = _validate_prefix_inventory(fact, graph, net_census["netIds"], capture_helper)
    return {"fact": fact, "graph": graph, "startCell": start_cell,
            "rootCell": root_cell, "inventory": inventory}


def _validate_net_census(value: Any, operation: Mapping[str, Any],
                         graph_entry: Mapping[str, Any], run_id: str) -> Dict[str, Any]:
    _exact_keys(value, NET_CENSUS_KEYS, "NET_CENSUS_KEYS_INVALID")
    variant = operation["variant"]
    expected = VARIANTS[variant]
    net_ids = value["netIds"]
    _need(type(value["protocolVersion"]) is int and value["protocolVersion"] == 1 and
          value["runId"] == run_id and value["operationId"] == operation["operationId"] and
          operation["mode"] == "CAPTURE" and value["variant"] == variant and
          value["graphCaseId"] == expected["graphCaseId"] == graph_entry["graph"].case_id and
          value["graphSha256"] == graph_entry["graph"].graph_sha256,
          "NET_CENSUS_IDENTITY_INVALID")
    _need(type(net_ids) is list and len(net_ids) == expected["nets"] and
          all(_valid_identity_text(net_id) for net_id in net_ids) and
          net_ids == sorted(net_ids, key=_java_string_sort_key) and
          len(net_ids) == len(set(net_ids)), "NET_CENSUS_LIST_INVALID")
    return value


def _validate_capacity_subset(value: Any, variant: str, run_id: str) -> Dict[str, Any]:
    _exact_keys(value, SUBSET_KEYS, "CAPACITY_SUBSET_KEYS_INVALID")
    _need(value["protocol"] == "rb100-physical-subsets-v1" and
          value["runId"] == run_id and value["variant"] == variant and
          value["status"] == "NOT_RUN_NO_LAYOUT" and
          value["independentTwoLayerAccess"] is False and
          value["layoutQualification"] is False,
          "CAPACITY_SUBSET_IDENTITY_OR_CLAIM_INVALID")
    return value


def _validate_pair(pair: Any, variant: str, run_id: str,
                   null_operation: Mapping[str, Any], capture_operation: Mapping[str, Any],
                   capture_helper: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(pair, capture_helper.PAIR_KEYS, "PAIR_KEYS_INVALID")
    expected = VARIANTS[variant]
    _need(type(pair["protocolVersion"]) is int and pair["protocolVersion"] == 1 and
          pair["runId"] == run_id and pair["variant"] == variant and
          pair["nullOperationId"] == expected["nullOperationId"] and
          pair["captureOperationId"] == expected["captureOperationId"] and
          pair["status"] == "PASS" and pair["parityStatus"] == "PASS" and
          pair["scope"] == "OBSERVATIONAL_NULL_CAPTURE_PARITY_ONLY" and
          pair["layoutQualification"] is False and
          all(pair[key] is True for key in capture_helper.PAIR_EQUALITY_KEYS),
          "PAIR_IDENTITY_INVALID")
    _need(all(null_operation[key] == capture_operation[key]
              for key in capture_helper.OPERATION_PARITY_FIELDS),
          "NULL_CAPTURE_OPERATION_PARITY_MISMATCH")
    _need(null_operation["policyCanonical"] == capture_operation["policyCanonical"] and
          null_operation["terminalStatus"] == capture_operation["terminalStatus"] and
          null_operation["policyOutcome"] == capture_operation["policyOutcome"],
          "NULL_CAPTURE_POLICY_PARITY_MISMATCH")
    return pair


def _validate_summary(summary: Any, run_id: str) -> Dict[str, Any]:
    _exact_keys(summary, SUMMARY_KEYS, "SUITE_SUMMARY_KEYS_INVALID")
    _need(summary["status"] == "PASS" and summary["captureSelection"] == "COMPLETED_NO_PATH" and
          summary["runId"] == run_id and summary["operations"] == 4 and
          summary["nullOperations"] == 2 and summary["captureOperations"] == 2 and
          summary["pairs"] == 2 and summary["graphs"] == 2 and
          summary["authoredVariants"] == 2 and
          summary["variants"] == list(VARIANTS) and
          summary["rootSeed"] == ROOT_SEED and summary["placementSeed"] == PLACEMENT_SEED and
          summary["routingSeed"] == ROUTING_SEED and
          type(summary["layoutVersion"]) is int and summary["layoutVersion"] == LAYOUT_VERSION and
          summary["dependencyEpoch"] == DEPENDENCY_EPOCH and
          summary["planVersion"] == PLAN_VERSION and summary["modelVersion"] == MODEL_VERSION and
          summary["physicalIntentVersion"] == PHYSICAL_INTENT_VERSION and
          _integer(summary["assertions"], 1, 10_000_000) and
          _integer(summary["suiteMs"], 0, 900_000) and summary["suiteLimitMs"] == 900_000 and
          summary["scope"] == "OBSERVATIONAL_ACTUAL_GENERATOR_FIXED_PREFIX_CAPTURE_ONLY" and
          summary["layoutQualification"] is False,
          "SUITE_SUMMARY_INVALID")
    return summary


def _parse_event_stream(receipt_lines: Sequence[str], oracle: types.ModuleType,
                        capture_helper: types.ModuleType) -> Tuple[str, List[Dict[str, Any]]]:
    events: List[Dict[str, Any]] = []
    position = 0
    run_id: Optional[str] = None

    def take(prefix: str, code: str) -> str:
        nonlocal position
        _need(position < len(receipt_lines) and receipt_lines[position].startswith(prefix),
              code + "_ORDER_OR_MISSING")
        line = receipt_lines[position]
        try:
            size = len(line.encode("utf-8", errors="strict"))
        except UnicodeEncodeError:
            _fail(code + "_ENCODING_INVALID")
        _need(size <= MAX_LINE_BYTES, code + "_LINE_TOO_LARGE")
        position += 1
        return line

    for variant, expected in VARIANTS.items():
        subset_null_line = take(SUBSET_TAG, "NULL_CAPACITY_SUBSET")
        subset_null = _parse_tagged_json(
            subset_null_line, SUBSET_TAG, "CAPACITY_SUBSET")
        if run_id is None:
            run_id = (subset_null.get("runId")
                      if type(subset_null) is dict else None)
        _need(type(run_id) is str and _UUID_RE.fullmatch(run_id) is not None,
              "CAPACITY_SUBSET_RUN_ID_INVALID")
        _validate_capacity_subset(subset_null, variant, run_id)

        null_line = take(OPERATION_TAG, "NULL_OPERATION")
        null_op = _parse_tagged_json(null_line, OPERATION_TAG, "OPERATION")
        _need(type(run_id) is str and _UUID_RE.fullmatch(run_id) is not None,
              "OPERATION_RUN_ID_INVALID")
        checked_null = _validate_operation(
            null_op, expected["nullOperationId"], run_id, capture_helper)

        subset_capture_line = take(SUBSET_TAG, "CAPTURE_CAPACITY_SUBSET")
        subset_capture = _parse_tagged_json(
            subset_capture_line, SUBSET_TAG, "CAPACITY_SUBSET")
        _validate_capacity_subset(subset_capture, variant, run_id)

        graph_line = take(GRAPH_TAG, "GRAPH_DATA")
        graph_entry = _parse_graph_line(graph_line, oracle)
        _need(graph_entry["graph"].case_id == expected["graphCaseId"],
              "GRAPH_CASE_ID_INVALID")

        fact_line = take(FACT_TAG, "CAPTURE_FACT")
        fact = _parse_tagged_json(fact_line, FACT_TAG, "CAPTURE_FACT")

        net_line = take(NET_CENSUS_TAG, "NET_CENSUS")
        net_census = _parse_tagged_json(net_line, NET_CENSUS_TAG, "NET_CENSUS")

        capture_line = take(OPERATION_TAG, "CAPTURE_OPERATION")
        capture_op = _parse_tagged_json(capture_line, OPERATION_TAG, "OPERATION")
        checked_capture = _validate_operation(
            capture_op, expected["captureOperationId"], run_id, capture_helper)
        _need(checked_null["variant"] == variant and checked_capture["variant"] == variant,
              "OPERATION_VARIANT_INVALID")

        checked_census = _validate_net_census(net_census, checked_capture, graph_entry, run_id)
        checked_fact = _validate_fact(fact, graph_entry, checked_capture, run_id,
                                      checked_census, capture_helper)

        pair_line = take(PAIR_TAG, "PAIR")
        pair = _parse_tagged_json(pair_line, PAIR_TAG, "PAIR")
        checked_pair = _validate_pair(
            pair, variant, run_id, checked_null, checked_capture, capture_helper)
        events.append({
            "variant": variant,
            "capacitySubsets": {
                "null": subset_null,
                "capture": subset_capture,
            },
            "nullOperation": checked_null,
            "graphEntry": graph_entry,
            "captureFact": checked_fact,
            "netCensus": checked_census,
            "captureOperation": checked_capture,
            "pair": checked_pair,
        })

    summary_line = take(SUMMARY_PREFIX, "SUITE_SUMMARY")
    summary = _parse_tagged_json(summary_line, SUMMARY_PREFIX, "SUITE_SUMMARY")
    _validate_summary(summary, run_id or "")
    _need(position == len(receipt_lines), "RUN_RECEIPT_TRAILING_EVENT")
    return run_id or "", events


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes) -> Tuple[List[str], List[str]]:
    console = _decode_lines(stdout_raw, "RUN_STDOUT")
    receipt = _decode_lines(receipt_raw, "RUN_RECEIPT")
    _need(bool(receipt) and all(receipt), "RUN_RECEIPT_EMPTY_OR_BLANK")
    tags = (GRAPH_TAG, FACT_TAG, OPERATION_TAG, PAIR_TAG, NET_CENSUS_TAG,
            SUBSET_TAG, SUMMARY_PREFIX)
    _need(all(line.startswith(tags) for line in receipt), "RUN_RECEIPT_UNEXPECTED_LINE")
    console_events = [line for line in console if line.startswith(tags)]
    _need(console_events == receipt, "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1,
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    _need(sum(line.startswith(SUMMARY_PREFIX) for line in receipt) == 1,
          "SUITE_SUMMARY_MARKER_INVALID")
    return console, receipt


def _validate_minimum_record(value: Any, graph: Any) -> Dict[str, Any]:
    expected_keys = {
        "schema", "protocol", "model", "caseId", "graphSha256", "status", "reachable",
        "minimumViaCount", "goalState", "termination", "counters", "fullReachabilityCensus",
        "productionFeasibility", "acceptance",
    }
    _exact_keys(value, expected_keys, "MINIMUM_VIAS_RESULT_KEYS_INVALID")
    _need(value["schema"] == "p07-fixed-prefix-minimum-vias-v1" and
          value["protocol"] == graph.protocol and value["model"] == graph.model and
          value["caseId"] == graph.case_id and value["graphSha256"] == graph.graph_sha256 and
          value["productionFeasibility"] == "UNKNOWN", "MINIMUM_VIAS_RESULT_IDENTITY_INVALID")
    _need(type(value["reachable"]) is bool and
          value["termination"] in ("GOAL_POPPED", "FRONTIER_EXHAUSTED"),
          "MINIMUM_VIAS_RESULT_INVALID")
    if value["reachable"]:
        _need(value["status"] == "REACHABLE" and value["termination"] == "GOAL_POPPED" and
              value["fullReachabilityCensus"] is False and
              _integer(value["minimumViaCount"], 0, graph.state_count - 1) and
              _integer(value["goalState"], 0, graph.state_count - 1) and
              graph.goals[value["goalState"]], "MINIMUM_VIAS_REACHABLE_INVALID")
    else:
        _need(value["status"] == "UNREACHABLE" and
              value["termination"] == "FRONTIER_EXHAUSTED" and
              value["fullReachabilityCensus"] is True and
              value["minimumViaCount"] is None and value["goalState"] is None,
              "MINIMUM_VIAS_UNREACHABLE_INVALID")
    counter_keys = {
        "uniqueStatesAdmitted", "statePops", "planarEdgesExamined", "viaChecks",
        "viaTransitionsExamined", "levelsStarted", "maximumQueueStates",
        "maximumPendingViaStates",
    }
    _exact_keys(value["counters"], counter_keys, "MINIMUM_VIAS_COUNTERS_INVALID")
    counters = value["counters"]
    _need(all(_integer(count, 0, 2_500_000) for count in counters.values()) and
          counters["planarEdgesExamined"] <= 2_500_000 and
          counters["uniqueStatesAdmitted"] <= graph.state_count and
          counters["statePops"] <= counters["uniqueStatesAdmitted"] and
          counters["viaChecks"] <= counters["statePops"] and
          counters["viaTransitionsExamined"] <= counters["viaChecks"] and
          1 <= counters["levelsStarted"] <= counters["uniqueStatesAdmitted"] and
          counters["maximumQueueStates"] <= counters["uniqueStatesAdmitted"] and
          counters["maximumPendingViaStates"] <= counters["uniqueStatesAdmitted"],
          "MINIMUM_VIAS_COUNTER_RELATION_INVALID")
    _need(value["acceptance"] == {
        "actualCaptureAttested": False, "route": False, "layout": False,
        "electrical": False, "q100": False,
    }, "MINIMUM_VIAS_ACCEPTANCE_INVALID")
    def check_tree(node: Any, depth: int = 0) -> None:
        _need(depth <= 8, "MINIMUM_VIAS_RESULT_TOO_DEEP")
        if type(node) is dict:
            _need(len(node) <= 32, "MINIMUM_VIAS_RESULT_TOO_LARGE")
            for key, child in node.items():
                _need(type(key) is str and key.lower() not in _FORBIDDEN_TREE_KEYS,
                      "MINIMUM_VIAS_PATH_DATA_FORBIDDEN")
                check_tree(child, depth + 1)
        elif type(node) is list:
            _need(len(node) <= 8, "MINIMUM_VIAS_RESULT_TOO_LARGE")
            for child in node:
                check_tree(child, depth + 1)
        else:
            _need(node is None or type(node) in (str, bool, int),
                  "MINIMUM_VIAS_RESULT_VALUE_INVALID")
    check_tree(value)
    _need(len(_canonical_json(value)) <= 16 * 1024, "MINIMUM_VIAS_RESULT_TOO_LARGE")
    return value


def _budget_disposition(prefix_hole_count: int, minimum_vias: Mapping[str, Any]) -> Dict[str, Any]:
    _need(_integer(prefix_hole_count, 0, MAX_VIAS), "PREFIX_HOLE_BUDGET_INVALID")
    remaining = min(MAX_TRANSITIONS, MAX_VIAS - prefix_hole_count)
    if not minimum_vias["reachable"]:
        disposition = "NO_PATH_IN_FIXED_GRAPH_ONLY"
    elif minimum_vias["minimumViaCount"] > remaining:
        disposition = "EXCLUDED_UNDER_FIXED_GRAPH_BOUND"
    else:
        disposition = "INCONCLUSIVE_WITHIN_FIXED_GRAPH_BOUND"
    return {
        "charge": "PREFIX_HOLES_PLUS_TRANSITIONS",
        "prefixHoleCount": prefix_hole_count,
        "maximumTotalHoles": MAX_VIAS,
        "maximumRouteTransitions": MAX_TRANSITIONS,
        "remainingTransitionAllowance": remaining,
        "minimumViaCount": minimum_vias["minimumViaCount"],
        "disposition": disposition,
    }


def _load_pinned_sources() -> Tuple[types.ModuleType, types.ModuleType, types.ModuleType,
                                    str, str, str]:
    try:
        own_path = Path(os.path.abspath(__file__))
        _need(not _has_reparse_component(own_path), "CLI_SOURCE_REPARSE_PATH")
        own = own_path.resolve(strict=True)
        _need(own.is_file() and not _is_reparse(own), "CLI_SOURCE_INVALID")
        directory = own.parent

        def read_source(filename: str, expected: str, label: str) -> Tuple[bytes, Path]:
            candidate = directory / filename
            _need(not _has_reparse_component(candidate), label + "_REPARSE_PATH")
            resolved = candidate.resolve(strict=True)
            _need(resolved.parent == directory and resolved.name == filename and
                  resolved.is_file() and not _is_reparse(resolved), label + "_LOCATION_INVALID")
            raw = _read_bounded(resolved, 4 * 1024 * 1024, label + "_SOURCE")
            _need(_sha256(raw) == expected, label + "_SOURCE_SHA256_INVALID")
            return raw, resolved

        graph_raw, graph_path = read_source("read_fixed_prefix_graph.py",
                                            GRAPH_ORACLE_SHA256, "GRAPH_ORACLE")
        core_raw, core_path = read_source("fixed_prefix_minimum_vias.py",
                                          MINIMUM_VIAS_SHA256, "MINIMUM_VIAS")
        capture_raw, capture_path = read_source("read_fixed_prefix_capture.py",
                                                CAPTURE_READER_SHA256, "CAPTURE_HELPER")
        module_graph = types.ModuleType("_current_prefix_pinned_graph_oracle")
        module_graph.__file__ = os.fspath(graph_path)
        module_graph.__package__ = ""
        sys.modules[module_graph.__name__] = module_graph
        exec(compile(graph_raw.decode("utf-8", errors="strict"), os.fspath(graph_path),
                     "exec", dont_inherit=True), module_graph.__dict__)

        def load_with_graph(raw: bytes, path: Path, name: str) -> types.ModuleType:
            module = types.ModuleType(name)
            module.__file__ = os.fspath(path)
            module.__package__ = ""
            previous = sys.modules.get("read_fixed_prefix_graph")
            sys.modules["read_fixed_prefix_graph"] = module_graph
            sys.modules[name] = module
            try:
                exec(compile(raw.decode("utf-8", errors="strict"), os.fspath(path),
                             "exec", dont_inherit=True), module.__dict__)
            finally:
                if previous is None:
                    sys.modules.pop("read_fixed_prefix_graph", None)
                else:
                    sys.modules["read_fixed_prefix_graph"] = previous
            sys.modules.pop(name, None)
            return module

        module_core = load_with_graph(core_raw, core_path, "_current_prefix_pinned_minimum_vias")
        module_capture = load_with_graph(capture_raw, capture_path,
                                         "_current_prefix_pinned_capture_helper")
        _need(callable(getattr(module_graph, "parse_graph_bytes", None)) and
              callable(getattr(module_graph, "validate_payload", None)) and
              callable(getattr(module_core, "analyze_minimum_vias", None)) and
              callable(getattr(module_capture, "_validate_prefix_holes", None)),
              "PINNED_HELPER_INTERFACE_INVALID")
        sys.modules.pop(module_graph.__name__, None)
        return (module_graph, module_core, module_capture,
                _sha256(graph_raw), _sha256(core_raw), _sha256(capture_raw))
    except LedgerInputError:
        raise
    except Exception:
        _fail("PINNED_HELPER_LOAD_FAILED")


def _build_result(spec_raw: bytes, context: Mapping[str, Any], result_raw: bytes,
                  run_result: Mapping[str, Any], stdout_raw: bytes, receipt_raw: bytes,
                  stderr_raw: bytes, run_id: str, events: Sequence[Mapping[str, Any]],
                  graph_oracle: types.ModuleType, minimum_core: types.ModuleType,
                  helper_hashes: Tuple[str, str, str], reader_hash: str) -> Dict[str, Any]:
    graph_sha, core_sha, capture_sha = helper_hashes
    cases = []
    for event in events:
        fact = event["captureFact"]["fact"]
        graph = event["captureFact"]["graph"]
        try:
            minimum = minimum_core.analyze_minimum_vias(graph)
        except Exception:
            _fail("MINIMUM_VIAS_ANALYSIS_FAILED")
        minimum = _validate_minimum_record(minimum, graph)
        budget = _budget_disposition(fact["prefixHoleCount"], minimum)
        cases.append({
            "variant": event["variant"],
            "selectedRequest": {
                "operationId": fact["operationId"],
                "requestIndex": fact["routeRequestIndex"],
                "placementIndex": fact["selectedPlacementIndex"],
                "placementAttempt": fact["selectedPlacementAttempt"],
                "netId": fact["netId"],
                "terminalReason": fact["terminalReason"],
                "graphCaseId": graph.case_id,
                "graphSha256": graph.graph_sha256,
            },
            "graph": {
                "protocol": graph.protocol,
                "model": graph.model,
                "graphBytes": len(event["graphEntry"]["raw"]),
                "width": graph.width,
                "height": graph.height,
                "cells": graph.cells,
                "primaryLayer": graph.primary,
                "netCensusCount": len(event["netCensus"]["netIds"]),
                "netCensusSha256": _sha256(_canonical_json(event["netCensus"])) ,
            },
            "capacityBoundPhysicalSubsets": event["capacitySubsets"],
            "prefixInventory": event["captureFact"]["inventory"],
            "minimumVias": minimum,
            "budget": budget,
        })
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS_CURRENT_PREFIX_LEDGER_DESCRIPTION_ONLY",
        "runId": run_id,
        "source": {
            "inputSpecSha256": _sha256(spec_raw),
            "sourceSnapshotRevision": context["sourceSnapshotRevision"],
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": {
                name: context["pinMap"][name]["sha256"]
                for name in sorted(REQUIRED_NATIVE_INPUTS)
            },
            "readerSha256": reader_hash,
            "graphOracleSha256": graph_sha,
            "minimumViasSha256": core_sha,
            "capturedHoleHelperSha256": capture_sha,
        },
        "nativeRun": {
            "schema": run_result["schema"],
            "source": run_result["source"],
            "suite": run_result["suite"],
            "command": run_result["command"],
            "layoutVersion": run_result["layoutVersion"],
            "dependencyEpoch": run_result["dependencyEpoch"],
            "sourceSnapshotRevision": run_result["sourceSnapshotRevision"],
            "inputCount": run_result["inputCount"],
            "inputBytes": run_result["inputBytes"],
            "inputPinsSha256": run_result["inputPinsSha256"],
            "inputAudit": run_result["inputAudit"],
            "exitCode": run_result["exitCode"],
            "nativeExitCode": run_result["nativeExitCode"],
            "terminationProven": run_result["terminationProven"],
            "cleanupStatus": run_result["cleanupStatus"],
            "cleanupElapsedMs": run_result["cleanupElapsedMs"],
            "nativeLimitMs": run_result["nativeLimitMs"],
            "outerLimitMs": run_result["outerLimitMs"],
            "elapsedSeconds": run_result["elapsedSeconds"],
            "processId": run_result["processId"],
            "processStartTicks": run_result["processStartTicks"],
            "requestedExecutable": run_result["requestedExecutable"],
            "resultSha256": _sha256(result_raw),
            "stdoutBytes": len(stdout_raw),
            "stdoutSha256": _sha256(stdout_raw),
            "receiptBytes": len(receipt_raw),
            "receiptSha256": _sha256(receipt_raw),
            "stderrBytes": len(stderr_raw),
            "stderrSha256": _sha256(stderr_raw),
        },
        "candidateContext": {
            "rootSeed": ROOT_SEED,
            "placementSeed": PLACEMENT_SEED,
            "routingSeed": ROUTING_SEED,
            "layoutVersion": LAYOUT_VERSION,
            "dependencyEpoch": DEPENDENCY_EPOCH,
            "planVersion": PLAN_VERSION,
            "modelVersion": MODEL_VERSION,
            "physicalIntentVersion": PHYSICAL_INTENT_VERSION,
            "densityVersion": 14,
            "routePolicy": "FULLER_TWO_LAYER",
            "supportedEnvelope": None,
        },
        "cases": cases,
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
        "scopeLimits": [
            "Each result is limited to its source-bound completed branch and fixed directed two-face relaxed graph.",
            "The minimum VIA count omits production history, clearance, spacing, ordering, finalization, and electrical checks.",
            "A bound exclusion applies only to that fixed relaxed graph; an inconclusive result is not route evidence.",
            "Prefixes above 48 total holes are outside this diagnostic domain and are rejected.",
            "No result qualifies a physical layout, electrical behavior, playable100, or Q100.",
        ],
    }


def run_validation(spec_path: Any, spec_sha256: Any) -> Dict[str, Any]:
    _need(type(spec_sha256) is str and _SHA256_RE.fullmatch(spec_sha256) is not None,
          "INPUT_SPEC_SHA256_ARGUMENT_INVALID")
    path = Path(spec_path)
    if not path.is_absolute():
        path = Path.cwd() / path
    _need(not _has_reparse_component(path), "INPUT_SPEC_REPARSE_PATH")
    try:
        path = path.resolve(strict=True)
    except (OSError, RuntimeError, ValueError):
        _fail("INPUT_SPEC_PATH_UNAVAILABLE")
    spec_raw = _read_bounded(path, SPEC_LIMIT, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _strict_json(spec_raw, "INPUT_SPEC")
    graph_oracle, minimum_core, capture_helper, graph_sha, core_sha, capture_sha = \
        _load_pinned_sources()
    context = _validate_input_spec(spec, path.parent, capture_helper)
    result_raw = _read_bounded(context["artifacts"]["resultPath"], RESULT_LIMIT, "RUN_RESULT")
    stdout_raw = _read_bounded(context["artifacts"]["stdoutPath"], CAPTURE_LIMIT, "RUN_STDOUT")
    receipt_raw = _read_bounded(context["artifacts"]["receiptPath"], CAPTURE_LIMIT, "RUN_RECEIPT")
    stderr_raw = _read_bounded(context["artifacts"]["stderrPath"], CAPTURE_LIMIT,
                               "RUN_STDERR", allow_empty=True)
    run_result = _strict_json(result_raw, "RUN_RESULT")
    _validate_run_result(run_result, context, stdout_raw, receipt_raw, stderr_raw)
    _console, receipt_lines = _validate_console_and_receipt(stdout_raw, receipt_raw)
    run_id, events = _parse_event_stream(receipt_lines, graph_oracle, capture_helper)
    # Parse the already-bound pass summary from the final receipt event.
    summary = _parse_tagged_json(receipt_lines[-1], SUMMARY_PREFIX, "SUITE_SUMMARY")
    _validate_summary(summary, run_id)
    reader_path = Path(os.path.abspath(__file__))
    reader_hash = _sha256(_read_bounded(reader_path, 2 * 1024 * 1024, "CLI_SOURCE"))
    return _build_result(spec_raw, context, result_raw, run_result, stdout_raw,
                         receipt_raw, stderr_raw, run_id, events, graph_oracle,
                         minimum_core, (graph_sha, core_sha, capture_sha), reader_hash)


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str]:
    _need(len(argv) == 4 and argv[0] == "--input-spec" and
          argv[2] == "--input-spec-sha256", "CLI_ARGUMENTS_INVALID")
    _need(bool(argv[1]) and type(argv[1]) is str and
          type(argv[3]) is str and _SHA256_RE.fullmatch(argv[3]) is not None,
          "CLI_ARGUMENTS_INVALID")
    return argv[1], argv[3]


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        spec_path, spec_sha256 = _parse_cli(list(sys.argv[1:] if argv is None else argv))
        report = run_validation(spec_path, spec_sha256)
        raw = _canonical_json(report) + b"\n"
        _need(len(raw) <= 128 * 1024, "OUTPUT_SIZE_INVALID")
        sys.stdout.buffer.write(raw)
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,96}", code, re.ASCII) is None:
            code = "INPUT_INVALID"
        try:
            sys.stdout.write("FAIL: " + code + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
