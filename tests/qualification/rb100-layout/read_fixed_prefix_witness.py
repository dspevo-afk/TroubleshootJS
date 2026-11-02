#!/usr/bin/env python3
"""Read-only, source-bound description of an accepted fixed-prefix witness.

This CLI consumes only the accepted B6 summary and its complete native receipt.
It reuses the pinned history-relaxed graph oracle and a separate descriptive
metrics module. A successful result does not attest production route feasibility.
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
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


SUMMARY_SHA256 = "9dab3b92652734ff33b1c0bc183b896e7aaa51e6d30735eba6a8844ea7f64aa8"
RECEIPT_SHA256 = "05b24b31c62fbd1d0fb14902dbb610384a1b58900339466726c4939c6e2f6086"
RECEIPT_BYTES = 7_769_545
ORACLE_SHA256 = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
METRICS_SHA256 = "3cfd4c74d5b0d0a556c5424fd869f96ca338d178609a6aca0aa01c2d5b0633d8"
SUMMARY_SCHEMA = "p07-fixed-prefix-capture-reader-result-v1"
RECEIPT_SCHEMA = "p07-fixed-prefix-native-run-v1"
READER_SHA256 = "26cbdc32361f07f759cc32dd3fe432fd640e329ce7e51dd583094dc10d0af962"
GRAPH_ORACLE_SHA256 = ORACLE_SHA256
GRAPH_TAG = b"P07_FIXED_PREFIX_GRAPH_DATA "

MAX_SUMMARY_BYTES = 1_048_576
MAX_RECEIPT_BYTES = 33_554_432
MAX_GRAPH_BYTES = 8_388_608
MAX_SOURCE_BYTES = 1_048_576
MAX_OUTPUT_BYTES = 262_144
MAX_PATH_CHARS = 4096

_HEX_SHA256 = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
_EXPECTED_CASES = {
    "shared": {
        "variant": "SHARED_HYSTERETIC_LINEAR",
        "caseId": "RB100_SHARED_HYSTERETIC_LINEAR_REQ0",
        "graphSha256": "e1937e17dd01b00c49021ade1931e0a510694eb76626b1a0146ff38bc6d06248",
        "graphBytes": 3_370_428,
        "prefixHoleCount": 48,
        "width": 478,
        "height": 309,
        "primaryLayer": 1,
        "startCell": 84_366,
        "rootCell": 118_305,
    },
    "separate": {
        "variant": "SEPARATE_DIRECT_SWITCHING",
        "caseId": "RB100_SEPARATE_DIRECT_SWITCHING_REQ0",
        "graphSha256": "5b1c80b2c97dbfd4cd65ccd45ff7dbc056e78fa53f23c794ee02cd2bba67e9a5",
        "graphBytes": 3_325_808,
        "prefixHoleCount": 39,
        "width": 478,
        "height": 309,
        "primaryLayer": 1,
        "startCell": 133_653,
        "rootCell": 9_506,
    },
}


class WitnessInputError(ValueError):
    """A static-code failure; messages never contain input values or paths."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


class _DuplicateJsonKey(Exception):
    pass


class _NonFiniteJsonNumber(Exception):
    pass


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise WitnessInputError(code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _canonical_json_bytes(value: Any) -> bytes:
    try:
        return json.dumps(
            value,
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=True,
            allow_nan=False,
        ).encode("ascii")
    except (TypeError, ValueError, OverflowError):
        raise WitnessInputError("JSON_VALUE_INVALID") from None


def _object_without_duplicates(pairs: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateJsonKey
        result[key] = value
    return result


def _reject_nonfinite(_value: str) -> None:
    raise _NonFiniteJsonNumber


def _parse_finite_float(value: str) -> float:
    parsed = float(value)
    if not math.isfinite(parsed):
        raise _NonFiniteJsonNumber
    return parsed


def _parse_json_bytes(raw: bytes, *, label: str) -> Any:
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise WitnessInputError(label + "_UTF8_INVALID") from None
    try:
        return json.loads(
            text,
            object_pairs_hook=_object_without_duplicates,
            parse_constant=_reject_nonfinite,
            parse_float=_parse_finite_float,
        )
    except _DuplicateJsonKey:
        raise WitnessInputError(label + "_JSON_DUPLICATE_KEY") from None
    except _NonFiniteJsonNumber:
        raise WitnessInputError(label + "_JSON_NONFINITE_NUMBER") from None
    except (json.JSONDecodeError, RecursionError, UnicodeError, ValueError):
        raise WitnessInputError(label + "_JSON_INVALID") from None


def _exact_keys(value: Any, keys: Iterable[str], code: str) -> None:
    _require(type(value) is dict and set(value) == set(keys), code)


def _verify_sha_argument(value: Any, expected: str, code: str) -> None:
    _require(type(value) is str and _HEX_SHA256.fullmatch(value) is not None and
             value == expected, code)


def _is_reparse(path: Path) -> bool:
    try:
        if path.is_symlink():
            return True
        is_junction = getattr(path, "is_junction", None)
        if callable(is_junction) and is_junction():
            return True
    except OSError:
        return True
    try:
        attributes = getattr(path.stat(follow_symlinks=False), "st_file_attributes", 0)
    except FileNotFoundError:
        return False
    except OSError:
        return True
    return bool(attributes & 0x400)


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


def _resolve_input(value: Any, code: str) -> Path:
    _require(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS, code + "_PATH_INVALID")
    try:
        candidate = Path(value)
        if not candidate.is_absolute():
            candidate = Path.cwd() / candidate
        candidate = Path(os.path.abspath(os.fspath(candidate)))
        _require(not _has_reparse_component(candidate), code + "_REPARSE_PATH")
        resolved = candidate.resolve(strict=True)
        _require(resolved.is_file() and not _is_reparse(resolved), code + "_NOT_REGULAR_FILE")
        return resolved
    except WitnessInputError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise WitnessInputError(code + "_PATH_UNAVAILABLE") from None


def _read_bounded_input(value: Any, limit: int, code: str) -> bytes:
    path = _resolve_input(value, code)
    try:
        with path.open("rb") as stream:
            raw = stream.read(limit + 1)
        _require(0 < len(raw) <= limit, code + "_SIZE_INVALID")
        _require(not _has_reparse_component(path) and path.resolve(strict=True) == path,
                 code + "_REPARSE_PATH")
        return raw
    except WitnessInputError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise WitnessInputError(code + "_READ_FAILED") from None


def _prepare_output_path(value: Any) -> Path:
    _require(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS,
             "OUTPUT_PATH_INVALID")
    try:
        candidate = Path(value)
        if not candidate.is_absolute():
            candidate = Path.cwd() / candidate
        candidate = Path(os.path.abspath(os.fspath(candidate)))
        _require(candidate.name not in ("", ".", ".."), "OUTPUT_PATH_INVALID")
        _require(not _has_reparse_component(candidate), "OUTPUT_REPARSE_PATH")
        parent = candidate.parent.resolve(strict=True)
        _require(parent.is_dir(), "OUTPUT_PARENT_INVALID")
        target = parent / candidate.name
        _require(not _is_reparse(target), "OUTPUT_REPARSE_PATH")
        _require(not target.exists(), "OUTPUT_ALREADY_EXISTS")
        return target
    except WitnessInputError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise WitnessInputError("OUTPUT_PATH_UNAVAILABLE") from None


def _write_new_output(path: Path, raw: bytes) -> None:
    _require(len(raw) <= MAX_OUTPUT_BYTES, "OUTPUT_SIZE_INVALID")
    _require(not _has_reparse_component(path) and not path.exists(),
             "OUTPUT_ALREADY_EXISTS")
    try:
        with path.open("xb") as stream:
            stream.write(raw)
            stream.flush()
    except FileExistsError:
        raise WitnessInputError("OUTPUT_ALREADY_EXISTS") from None
    except (OSError, RuntimeError, TypeError, ValueError):
        raise WitnessInputError("OUTPUT_WRITE_FAILED") from None


def _extract_tagged_graph_payloads(receipt_raw: bytes) -> Tuple[Tuple[bytes, Any], ...]:
    """Extract exactly two canonical graph bodies after the caller pins all bytes."""
    _require(type(receipt_raw) is bytes and 0 < len(receipt_raw) <= MAX_RECEIPT_BYTES,
             "RECEIPT_SIZE_INVALID")
    try:
        receipt_raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise WitnessInputError("RECEIPT_UTF8_INVALID") from None

    found: List[Tuple[bytes, Any]] = []
    for line in receipt_raw.split(b"\n"):
        if line.endswith(b"\r"):
            line = line[:-1]
        if not line.startswith(GRAPH_TAG):
            continue
        body = line[len(GRAPH_TAG):]
        _require(0 < len(body) <= MAX_GRAPH_BYTES, "GRAPH_LINE_SIZE_INVALID")
        payload = _parse_json_bytes(body, label="GRAPH_LINE")
        _require(_canonical_json_bytes(payload) == body, "GRAPH_LINE_NONCANONICAL")
        found.append((body, payload))
        _require(len(found) <= 2, "GRAPH_LINE_COUNT_INVALID")
    _require(len(found) == 2, "GRAPH_LINE_COUNT_INVALID")
    return tuple(found)


def _validate_summary_content(value: Any) -> Dict[str, Any]:
    top_keys = {
        "acceptance", "graphOracleSourceSha256", "nativeRun", "readerSourceSha256",
        "runId", "schema", "scopeLimits", "source", "status", "summary", "variants",
    }
    _exact_keys(value, top_keys, "SUMMARY_KEYS_INVALID")
    _require(value["schema"] == SUMMARY_SCHEMA and
             value["status"] == "PASS_CAPTURE_CONSISTENCY_ONLY" and
             type(value["runId"]) is str and value["runId"] == "b0578cdb-6297-4cfd-87c7-4c2b1638a77d" and
             value["readerSourceSha256"] == READER_SHA256 and
             value["graphOracleSourceSha256"] == GRAPH_ORACLE_SHA256,
             "SUMMARY_IDENTITY_INVALID")

    source = value["source"]
    _exact_keys(source, {"inputBytes", "inputCount", "pinsSha256", "sourceSnapshotRevision"},
                "SUMMARY_SOURCE_KEYS_INVALID")
    _require(type(source["inputBytes"]) is int and source["inputBytes"] == 85_885_501 and
             type(source["inputCount"]) is int and source["inputCount"] == 1066 and
             source["pinsSha256"] == "87517e32e4e181b50eb5afaef443d8aa37e3584fb352f15016acd9e655d697da" and
             type(source["sourceSnapshotRevision"]) is int and
             source["sourceSnapshotRevision"] == 3,
             "SUMMARY_SOURCE_INVALID")

    native = value["nativeRun"]
    native_keys = {
        "cleanupStatus", "command", "dependencyEpoch", "elapsedSeconds", "exitCode",
        "inputAudit", "layoutVersion", "nativeExitCode", "nativeLimitMs", "outerLimitMs",
        "receiptBytes", "receiptSha256", "resultSha256", "schema", "source", "stderrBytes",
        "stderrSha256", "stdoutBytes", "stdoutSha256", "suite", "terminationProven",
    }
    _exact_keys(native, native_keys, "SUMMARY_NATIVE_KEYS_INVALID")
    command = native["command"]
    _exact_keys(command, {"args", "entry", "environment", "suite"},
                "SUMMARY_COMMAND_KEYS_INVALID")
    _require(command == {
                 "args": [],
                 "entry": "scripts/verify-current-contracts.ps1",
                 "environment": {},
                 "suite": "Rb100FixedPrefixCaptureContractTest",
             } and
             native["schema"] == RECEIPT_SCHEMA and
             native["suite"] == "Rb100FixedPrefixCaptureContractTest" and
             native["source"] == "candidate" and
             native["dependencyEpoch"] == "tsj-generation-dependencies-v27" and
             type(native["layoutVersion"]) is int and native["layoutVersion"] == 25 and
             type(native["exitCode"]) is int and native["exitCode"] == 0 and
             type(native["nativeExitCode"]) is int and native["nativeExitCode"] == 0 and
             native["terminationProven"] is True and
             native["cleanupStatus"] == "PASS" and
             native["inputAudit"] == "PASS_BEFORE_AND_AFTER" and
             type(native["receiptBytes"]) is int and native["receiptBytes"] == RECEIPT_BYTES and
             native["receiptSha256"] == RECEIPT_SHA256,
             "SUMMARY_NATIVE_BINDING_INVALID")

    acceptance = value["acceptance"]
    _exact_keys(acceptance, {
        "captureConsistency", "electricalQualification", "layoutQualification",
        "q100Qualification", "routeAccepted",
    }, "SUMMARY_ACCEPTANCE_KEYS_INVALID")
    _require(acceptance == {
                 "captureConsistency": True,
                 "electricalQualification": False,
                 "layoutQualification": False,
                 "q100Qualification": False,
                 "routeAccepted": False,
             }, "SUMMARY_ACCEPTANCE_INVALID")

    summary = value["summary"]
    _exact_keys(summary, {"graphCount", "layoutQualification", "operationCount", "pairCount", "variants"},
                "SUMMARY_COUNTS_KEYS_INVALID")
    _require(summary == {
                 "graphCount": 2,
                 "layoutQualification": False,
                 "operationCount": 4,
                 "pairCount": 2,
                 "variants": ["SHARED_HYSTERETIC_LINEAR", "SEPARATE_DIRECT_SWITCHING"],
             }, "SUMMARY_COUNTS_INVALID")
    _require(value["scopeLimits"] == [
                 "The independent result describes only each captured fixed graph in its history-relaxed model.",
                 "Reachability is not route acceptance; unreachability is limited to that graph and model.",
                 "This reader does not qualify layout, electrical behavior, playable100, or Q100.",
             ], "SUMMARY_SCOPE_INVALID")

    variants = value["variants"]
    _require(type(variants) is list and len(variants) == 2, "SUMMARY_VARIANTS_INVALID")
    by_variant: Dict[str, Dict[str, Any]] = {}
    expected_order = ("SHARED_HYSTERETIC_LINEAR", "SEPARATE_DIRECT_SWITCHING")
    for index, variant_row in enumerate(variants):
        _require(type(variant_row) is dict and variant_row.get("variant") == expected_order[index],
                 "SUMMARY_VARIANTS_INVALID")
        variant = variant_row["variant"]
        _require(variant not in by_variant, "SUMMARY_VARIANTS_DUPLICATE")
        expected = _EXPECTED_CASES["shared" if variant == "SHARED_HYSTERETIC_LINEAR" else "separate"]
        fact = variant_row.get("captureFact")
        graph_meta = variant_row.get("graph")
        _require(type(fact) is dict and type(graph_meta) is dict,
                 "SUMMARY_GRAPH_METADATA_INVALID")
        expected_fact = {
            "variant": expected["variant"],
            "scope": "ACTUAL_GENERATOR_FIXED_PREFIX_OBSERVATION_ONLY",
            "dependencyEpoch": "tsj-generation-dependencies-v27",
            "layoutVersion": 25,
            "planVersion": 1,
            "modelVersion": 4,
            "physicalIntentVersion": 9,
            "rootSeed": "9007199254740993",
            "placementSeed": "-6587958367974952701",
            "routingSeed": "8445301674744508279",
            "selectedPlacementAttempt": 4,
            "selectedPlacementIndex": 0,
            "routeRequestIndex": 0,
            "initialFacesMask": 3,
            "graphCaseId": expected["caseId"],
            "graphSha256": expected["graphSha256"],
            "graphBytes": expected["graphBytes"],
            "prefixHoleCount": expected["prefixHoleCount"],
            "gridPitch": 10,
            "gridWidth": expected["width"],
            "gridHeight": expected["height"],
            "cellCount": expected["width"] * expected["height"],
            "primaryLayer": expected["primaryLayer"],
            "startCell": expected["startCell"],
            "reachabilityClaim": False,
            "layoutQualification": False,
        }
        for field, expected_value in expected_fact.items():
            _require(type(fact.get(field)) is type(expected_value) and
                     fact.get(field) == expected_value,
                     "SUMMARY_CAPTURE_FACT_INVALID")
        expected_graph = {
            "caseId": expected["caseId"],
            "graphSha256": expected["graphSha256"],
            "graphUtf8Bytes": expected["graphBytes"],
            "width": expected["width"],
            "height": expected["height"],
            "primaryLayer": expected["primaryLayer"],
            "startCell": expected["startCell"],
            "rootCell": expected["rootCell"],
        }
        for field, expected_value in expected_graph.items():
            _require(type(graph_meta.get(field)) is type(expected_value) and
                     graph_meta.get(field) == expected_value,
                     "SUMMARY_GRAPH_METADATA_INVALID")
        by_variant[variant] = variant_row
    return by_variant


def _reader_directory() -> Path:
    try:
        script_path = Path(os.path.abspath(__file__))
        _require(not _has_reparse_component(script_path), "READER_SOURCE_REPARSE_PATH")
        resolved = script_path.resolve(strict=True)
        _require(resolved.is_file() and not _is_reparse(resolved), "READER_SOURCE_INVALID")
        return resolved.parent
    except WitnessInputError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        raise WitnessInputError("READER_SOURCE_UNAVAILABLE") from None


def _load_local_source(directory: Path, filename: str, expected_sha256: Optional[str],
                       module_name: str, label: str) -> Tuple[types.ModuleType, str]:
    candidate = directory / filename
    _require(not _has_reparse_component(candidate), label + "_SOURCE_REPARSE_PATH")
    try:
        resolved = candidate.resolve(strict=True)
        _require(resolved.parent == directory and resolved.name == filename and
                 resolved.is_file() and not _is_reparse(resolved),
                 label + "_SOURCE_LOCATION_INVALID")
        with resolved.open("rb") as stream:
            raw = stream.read(MAX_SOURCE_BYTES + 1)
        _require(0 < len(raw) <= MAX_SOURCE_BYTES, label + "_SOURCE_SIZE_INVALID")
        digest = _sha256(raw)
        if expected_sha256 is not None:
            _require(digest == expected_sha256, label + "_SOURCE_SHA256_INVALID")
        try:
            source = raw.decode("utf-8", errors="strict")
            code = compile(source, os.fspath(resolved), "exec", dont_inherit=True)
        except (UnicodeError, SyntaxError, ValueError, TypeError):
            raise WitnessInputError(label + "_SOURCE_INVALID") from None
        module = types.ModuleType(module_name)
        module.__file__ = os.fspath(resolved)
        module.__cached__ = None
        module.__package__ = ""
        sys.modules[module_name] = module
        exec(code, module.__dict__)
        _require(not _has_reparse_component(Path(module.__file__)) and
                 Path(module.__file__).resolve(strict=True) == resolved,
                 label + "_SOURCE_LOCATION_INVALID")
        return module, digest
    except WitnessInputError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError, AttributeError, ImportError):
        raise WitnessInputError(label + "_SOURCE_LOAD_FAILED") from None


def _load_local_witness_modules(directory: Path
                                ) -> Tuple[types.ModuleType, str, types.ModuleType, str]:
    oracle, oracle_sha = _load_local_source(
        directory, "read_fixed_prefix_graph.py", ORACLE_SHA256,
        "_p07_fixed_prefix_graph_pinned", "ORACLE",
    )
    _require(callable(getattr(oracle, "parse_graph_bytes", None)) and
             callable(getattr(oracle, "analyze_graph", None)),
             "ORACLE_INTERFACE_INVALID")

    # The metrics module imports the reviewed oracle by its conventional
    # module name. Temporarily bind that name to this already hash-checked
    # module, then restore any caller module so tests and host scripts retain
    # their prior import state.
    import_name = "read_fixed_prefix_graph"
    previous = sys.modules.get(import_name)
    sys.modules[import_name] = oracle
    try:
        metrics, metrics_sha = _load_local_source(
            directory, "fixed_prefix_witness_metrics.py", METRICS_SHA256,
            "_p07_fixed_prefix_witness_metrics_local", "METRICS",
        )
    finally:
        if previous is None:
            sys.modules.pop(import_name, None)
        else:
            sys.modules[import_name] = previous
    summarize = getattr(metrics, "summarize_witness", None)
    metric_error = getattr(metrics, "WitnessMetricError", None)
    _require(callable(summarize) and isinstance(metric_error, type) and
             issubclass(metric_error, Exception), "METRICS_INTERFACE_INVALID")
    return oracle, oracle_sha, metrics, metrics_sha


def _validate_graphs(graph_payloads: Sequence[Tuple[bytes, Any]], summary_by_variant: Dict[str, Any],
                     oracle: types.ModuleType) -> Dict[str, Tuple[Any, bytes]]:
    results: Dict[str, Tuple[Any, bytes]] = {}
    expected_by_case = {case["caseId"]: case for case in _EXPECTED_CASES.values()}
    for raw, payload in graph_payloads:
        _require(type(payload) is dict, "GRAPH_PAYLOAD_INVALID")
        try:
            graph = oracle.parse_graph_bytes(raw)
        except Exception:
            raise WitnessInputError("GRAPH_ORACLE_REJECTED_INPUT") from None
        case = expected_by_case.get(getattr(graph, "case_id", None))
        _require(case is not None, "GRAPH_CASE_ID_INVALID")
        _require(case["caseId"] not in results, "GRAPH_CASE_ID_DUPLICATE")
        _require(len(raw) == case["graphBytes"] and _sha256(raw) == case["graphSha256"] and
                 graph.graph_sha256 == case["graphSha256"] and
                 type(payload.get("caseId")) is str and payload["caseId"] == case["caseId"] and
                 graph.width == case["width"] and graph.height == case["height"] and
                 graph.primary == case["primaryLayer"],
                 "GRAPH_ACCEPTED_PIN_MISMATCH")
        variant = case["variant"]
        variant_row = summary_by_variant.get(variant)
        _require(variant_row is not None, "GRAPH_SUMMARY_VARIANT_MISSING")
        fact = variant_row["captureFact"]
        graph_meta = variant_row["graph"]
        _require(len(raw) == fact["graphBytes"] == graph_meta["graphUtf8Bytes"] and
                 _sha256(raw) == fact["graphSha256"] == graph_meta["graphSha256"] and
                 graph.case_id == fact["graphCaseId"] == graph_meta["caseId"] and
                 graph.width == fact["gridWidth"] == graph_meta["width"] and
                 graph.height == fact["gridHeight"] == graph_meta["height"] and
                 graph.primary == fact["primaryLayer"] == graph_meta["primaryLayer"],
                 "GRAPH_SUMMARY_BINDING_INVALID")
        expected_starts = [face * graph.cells + fact["startCell"]
                           for face in range(2) if fact["initialFacesMask"] & (1 << face)]
        _require(list(graph.starts) == expected_starts and
                 graph.goals[case["rootCell"]] is True and
                 graph.goals[graph.cells + case["rootCell"]] is True,
                 "GRAPH_LINEAGE_ENDPOINTS_INVALID")
        results[case["caseId"]] = (graph, raw)
    _require(set(results) == set(expected_by_case), "GRAPH_CASE_SET_INVALID")
    return results


def _validate_metrics_result(value: Any, graph: Any) -> Dict[str, Any]:
    _require(type(value) is dict and value.get("schema") == "p07-fixed-prefix-witness-metrics-v1" and
             value.get("caseId") == graph.case_id and
             value.get("graphSha256") == graph.graph_sha256 and
             value.get("productionFeasibility") == "UNKNOWN",
             "METRICS_RESULT_INVALID")
    acceptance = value.get("acceptance")
    _require(type(acceptance) is dict and len(acceptance) > 0 and
             all(item is False for item in acceptance.values()),
             "METRICS_ACCEPTANCE_INVALID")
    _require(all(acceptance.get(field) is False for field in
                 ("route", "layout", "electrical", "q100")),
             "METRICS_ACCEPTANCE_INVALID")
    _require(type(value.get("witness")) is dict and
             type(value.get("historySubset")) is dict,
             "METRICS_RESULT_INVALID")

    forbidden_keys = {
        "path", "paths", "fullpath", "fullpaths", "states", "edges",
        "witnessstates", "witnessedgekinds", "pathstates", "pathedges",
    }

    def check_json_tree(node: Any, depth: int = 0) -> None:
        _require(depth <= 16, "METRICS_RESULT_TOO_DEEP")
        if type(node) is dict:
            _require(len(node) <= 64, "METRICS_RESULT_TOO_LARGE")
            for key, child in node.items():
                _require(type(key) is str and key.lower() not in forbidden_keys,
                         "METRICS_PATH_DATA_FORBIDDEN")
                check_json_tree(child, depth + 1)
        elif type(node) is list:
            _require(len(node) <= 16, "METRICS_ARRAY_TOO_LARGE")
            for child in node:
                check_json_tree(child, depth + 1)
        elif node is None or type(node) in (str, bool, int):
            return
        elif type(node) is float:
            _require(node == node and abs(node) != float("inf"), "METRICS_NONFINITE_NUMBER")
        else:
            raise WitnessInputError("METRICS_VALUE_INVALID")

    check_json_tree(value)
    encoded = _canonical_json_bytes(value)
    _require(len(encoded) <= 64 * 1024, "METRICS_RESULT_TOO_LARGE")
    return value


def _build_report(args: Dict[str, str]) -> Tuple[Dict[str, Any], bytes, Path]:
    _verify_sha_argument(args.get("summary-sha256"), SUMMARY_SHA256,
                         "SUMMARY_SHA256_ARGUMENT_INVALID")
    _verify_sha_argument(args.get("receipt-sha256"), RECEIPT_SHA256,
                         "RECEIPT_SHA256_ARGUMENT_INVALID")
    _require(args.get("case") in _EXPECTED_CASES, "CASE_INVALID")

    output_path = _prepare_output_path(args.get("output"))
    summary_raw = _read_bounded_input(args.get("summary"), MAX_SUMMARY_BYTES, "SUMMARY")
    _require(_sha256(summary_raw) == SUMMARY_SHA256, "SUMMARY_SHA256_MISMATCH")
    summary_value = _parse_json_bytes(summary_raw, label="SUMMARY")
    summary_by_variant = _validate_summary_content(summary_value)

    receipt_raw = _read_bounded_input(args.get("receipt"), MAX_RECEIPT_BYTES, "RECEIPT")
    _require(len(receipt_raw) == RECEIPT_BYTES and _sha256(receipt_raw) == RECEIPT_SHA256,
             "RECEIPT_SHA256_OR_LENGTH_MISMATCH")

    # No receipt line is interpreted until the whole raw receipt and the full
    # source-bound accepted summary have both matched their frozen pins.
    graph_payloads = _extract_tagged_graph_payloads(receipt_raw)
    source_dir = _reader_directory()
    oracle, oracle_sha, metrics, metrics_sha = _load_local_witness_modules(source_dir)
    summarize = getattr(metrics, "summarize_witness", None)
    metric_error = getattr(metrics, "WitnessMetricError", None)

    graph_rows = _validate_graphs(graph_payloads, summary_by_variant, oracle)
    selected = _EXPECTED_CASES[args["case"]]
    graph, _graph_raw = graph_rows[selected["caseId"]]
    try:
        result = oracle.analyze_graph(graph)
        metrics_record = summarize(
            graph,
            result,
            prefix_hole_count=selected["prefixHoleCount"],
            maximum_vias=48,
            max_transitions=4,
            grid_pitch=10,
        )
    except metric_error as error:
        code = getattr(error, "code", "METRICS_REJECTED_WITNESS")
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,64}", code) is None:
            code = "METRICS_REJECTED_WITNESS"
        raise WitnessInputError(code) from None
    except Exception:
        raise WitnessInputError("WITNESS_ANALYSIS_FAILED") from None
    metrics_record = _validate_metrics_result(metrics_record, graph)

    report: Dict[str, Any] = {
        "schema": "p07-fixed-prefix-witness-cli-result-v1",
        "status": "PASS_BOUNDED_WITNESS_DESCRIPTION_ONLY",
        "source": {
            "oracleFilename": "read_fixed_prefix_graph.py",
            "oracleDirectory": "reader-directory",
            "oracleSha256": oracle_sha,
            "metricsFilename": "fixed_prefix_witness_metrics.py",
            "metricsDirectory": "reader-directory",
            "metricsSha256": metrics_sha,
        },
        "provenance": {
            "summarySha256": SUMMARY_SHA256,
            "summaryBytes": len(summary_raw),
            "summarySchema": SUMMARY_SCHEMA,
            "nativeReceiptSha256": RECEIPT_SHA256,
            "nativeReceiptBytes": len(receipt_raw),
            "nativeRunSchema": RECEIPT_SCHEMA,
            "nativeExitCode": 0,
            "terminationProven": True,
            "source": "candidate",
            "inputAudit": "PASS_BEFORE_AND_AFTER",
            "cleanupStatus": "PASS",
            "nativeSourceContext": {
                "inputCount": summary_value["source"]["inputCount"],
                "inputBytes": summary_value["source"]["inputBytes"],
                "pinsSha256": summary_value["source"]["pinsSha256"],
                "sourceSnapshotRevision": summary_value["source"]["sourceSnapshotRevision"],
                "layoutVersion": summary_value["nativeRun"]["layoutVersion"],
                "dependencyEpoch": summary_value["nativeRun"]["dependencyEpoch"],
                "command": summary_value["nativeRun"]["command"],
            },
            "lineage": "PREVIOUSLY_ACCEPTED_B6_ONLY_NO_FRESH_NATIVE_PROOF",
            "graphLineCount": 2,
        },
        "selectedCase": {
            "variant": selected["variant"],
            "caseId": graph.case_id,
            "graphSha256": graph.graph_sha256,
            "graphBytes": selected["graphBytes"],
            "width": graph.width,
            "height": graph.height,
            "primaryLayer": graph.primary,
            "prefixHoleCount": selected["prefixHoleCount"],
            "gridPitch": 10,
        },
        "acceptedCaptureContext": {
            "contextSource": "PREVIOUSLY_ACCEPTED_B6_PACKET",
            "generatorScope": "BARE_PRIVATE_GENERATOR",
            "supportedEnvelope": None,
            "playerEnvelopeQualified": False,
            "layoutVersion": 25,
            "dependencyEpoch": "tsj-generation-dependencies-v27",
            "planVersion": 1,
            "modelVersion": 4,
            "physicalIntentVersion": 9,
            "roleWidths": "PRESERVED",
            "densityVersion": 14,
            "placementPolicy": "CONNECTED_POCKET",
            "routePolicy": "FULLER_P07",
        },
        "witnessMetrics": metrics_record,
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
        "scopeLimits": [
            "The deterministic history-relaxed graph oracle supplies the witness; this CLI does not run a second shortest-path search.",
            "Reported checks cover only selected production-history subsets and do not prove the actual router's full state, path interaction, scheduler, or layout acceptance.",
            "The full native receipt hash binds previously accepted B6 lineage; this CLI does not claim fresh native proof.",
            "ROLE widths, density14, connected-pocket placement, required FULLER P07, and the bare-generator envelope=null scope are inherited from that accepted packet, not freshly observed by this CLI.",
            "This result does not qualify route feasibility, layout, electrical behavior, or Q100.",
        ],
    }
    report_bytes = _canonical_json_bytes(report) + b"\n"
    _require(len(report_bytes) <= MAX_OUTPUT_BYTES, "OUTPUT_SIZE_INVALID")
    return report, report_bytes, output_path


def _parse_arguments(argv: Sequence[str]) -> Dict[str, str]:
    option_names = {
        "--summary": "summary",
        "--summary-sha256": "summary-sha256",
        "--receipt": "receipt",
        "--receipt-sha256": "receipt-sha256",
        "--case": "case",
        "--output": "output",
    }
    _require(type(argv) in (list, tuple) and len(argv) == 12, "ARGUMENTS_INVALID")
    parsed: Dict[str, str] = {}
    for index in range(0, len(argv), 2):
        option = argv[index]
        value = argv[index + 1]
        key = option_names.get(option) if type(option) is str else None
        _require(key is not None and key not in parsed and type(value) is str,
                 "ARGUMENTS_INVALID")
        parsed[key] = value
    _require(set(parsed) == set(option_names.values()), "ARGUMENTS_INVALID")
    return parsed


def main(argv: Optional[Sequence[str]] = None) -> int:
    if argv is None:
        argv = sys.argv[1:]
    try:
        args = _parse_arguments(argv)
        _report, report_bytes, output_path = _build_report(args)
        _write_new_output(output_path, report_bytes)
        print("PASS: bounded fixed-prefix witness description only; production feasibility UNKNOWN")
        return 0
    except WitnessInputError as error:
        print("FAIL: " + error.code)
        return 2
    except Exception:
        print("FAIL: INPUT_INVALID")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
