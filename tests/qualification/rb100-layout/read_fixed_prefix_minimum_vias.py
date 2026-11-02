#!/usr/bin/env python3
"""Strict test-only CLI for minimum VIA transitions in an accepted fixed graph."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import sys
import types
from typing import Any, Dict, Optional, Sequence, Tuple


WITNESS_READER_SHA256 = "34e9275df0edb25eafbe04cfbe6750ee1355115343592511159fc3101a60c2df"
ORACLE_SHA256 = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
MINIMUM_VIAS_SHA256 = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"
RESULT_SCHEMA = "p07-fixed-prefix-minimum-vias-cli-result-v1"
MAX_BOOTSTRAP_SOURCE_BYTES = 1_048_576
MAX_OUTPUT_BYTES = 131_072
MAX_COUNTER_STATES = 500_000
MAX_COUNTER_EDGES = 2_500_000

_SHA256_PATTERN = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
_FORBIDDEN_PATH_KEYS = frozenset(("path", "paths", "states", "edges", "predecessors",
                                   "witnessstates", "witnessedgekinds"))


class MinimumViaInputError(ValueError):
    """Sanitized failure code; never includes an input value or path."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise MinimumViaInputError(code)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


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
        return bool(getattr(path.stat(follow_symlinks=False), "st_file_attributes", 0) & 0x400)
    except FileNotFoundError:
        return False
    except OSError:
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


def _load_pinned_witness_reader() -> Tuple[types.ModuleType, Path, str]:
    try:
        own_path = Path(os.path.abspath(__file__))
        _require(not _has_reparse_component(own_path), "CLI_SOURCE_REPARSE_PATH")
        own_resolved = own_path.resolve(strict=True)
        _require(own_resolved.is_file() and not _is_reparse(own_resolved), "CLI_SOURCE_INVALID")
        directory = own_resolved.parent
        candidate = directory / "read_fixed_prefix_witness.py"
        _require(not _has_reparse_component(candidate), "WITNESS_READER_REPARSE_PATH")
        resolved = candidate.resolve(strict=True)
        _require(resolved.parent == directory and resolved.name == candidate.name and
                 resolved.is_file() and not _is_reparse(resolved),
                 "WITNESS_READER_LOCATION_INVALID")
        with resolved.open("rb") as stream:
            raw = stream.read(MAX_BOOTSTRAP_SOURCE_BYTES + 1)
        _require(0 < len(raw) <= MAX_BOOTSTRAP_SOURCE_BYTES, "WITNESS_READER_SIZE_INVALID")
        digest = _sha256(raw)
        _require(digest == WITNESS_READER_SHA256, "WITNESS_READER_SHA256_INVALID")
        try:
            source = raw.decode("utf-8", errors="strict")
            code = compile(source, os.fspath(resolved), "exec", dont_inherit=True)
        except (UnicodeError, SyntaxError, ValueError, TypeError):
            raise MinimumViaInputError("WITNESS_READER_SOURCE_INVALID") from None
        module = types.ModuleType("_p07_minimum_vias_pinned_witness_reader")
        module.__file__ = os.fspath(resolved)
        module.__cached__ = None
        module.__package__ = ""
        sys.modules[module.__name__] = module
        exec(code, module.__dict__)
        _require(Path(module.__file__).resolve(strict=True) == resolved and
                 not _has_reparse_component(Path(module.__file__)),
                 "WITNESS_READER_LOCATION_INVALID")
        reader_directory = module._reader_directory()
        _require(reader_directory == directory, "WITNESS_READER_LOCATION_INVALID")
        return module, directory, digest
    except MinimumViaInputError:
        raise
    except Exception:
        raise MinimumViaInputError("WITNESS_READER_LOAD_FAILED") from None


def _load_pinned_oracle(reader: types.ModuleType, directory: Path
                        ) -> Tuple[types.ModuleType, str]:
    try:
        oracle, digest = reader._load_local_source(
            directory,
            "read_fixed_prefix_graph.py",
            ORACLE_SHA256,
            "_p07_minimum_vias_pinned_graph_oracle",
            "ORACLE",
        )
        _require(callable(getattr(oracle, "parse_graph_bytes", None)) and
                 callable(getattr(oracle, "analyze_graph", None)) and
                 callable(getattr(oracle, "validate_payload", None)),
                 "ORACLE_INTERFACE_INVALID")
        return oracle, digest
    except Exception as error:
        code = getattr(error, "code", "ORACLE_LOAD_FAILED")
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,80}", code) is None:
            code = "ORACLE_LOAD_FAILED"
        raise MinimumViaInputError(code) from None


def _load_pinned_minimum_vias(reader: types.ModuleType, oracle: types.ModuleType,
                              directory: Path
                              ) -> Tuple[types.ModuleType, str]:
    import_name = "read_fixed_prefix_graph"
    previous = sys.modules.get(import_name)
    sys.modules[import_name] = oracle
    try:
        try:
            core, digest = reader._load_local_source(
                directory,
                "fixed_prefix_minimum_vias.py",
                MINIMUM_VIAS_SHA256,
                "_p07_fixed_prefix_minimum_vias_pinned",
                "MINIMUM_VIAS",
            )
        except Exception as error:
            code = getattr(error, "code", "MINIMUM_VIAS_LOAD_FAILED")
            if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,80}", code) is None:
                code = "MINIMUM_VIAS_LOAD_FAILED"
            raise MinimumViaInputError(code) from None
    finally:
        if previous is None:
            sys.modules.pop(import_name, None)
        else:
            sys.modules[import_name] = previous
    _require(callable(getattr(core, "analyze_minimum_vias", None)),
             "MINIMUM_VIAS_INTERFACE_INVALID")
    return core, digest


def _validate_minimum_vias_record(reader: types.ModuleType, value: Any,
                                  graph: Any) -> Dict[str, Any]:
    expected_keys = {
        "schema", "protocol", "model", "caseId", "graphSha256", "status",
        "reachable", "minimumViaCount", "goalState", "termination", "counters",
        "fullReachabilityCensus", "productionFeasibility", "acceptance",
    }
    reader._exact_keys(value, expected_keys, "MINIMUM_VIAS_RESULT_KEYS_INVALID")
    _require(value["schema"] == "p07-fixed-prefix-minimum-vias-v1" and
             value["protocol"] == graph.protocol and
             value["model"] == graph.model and
             value["caseId"] == graph.case_id and
             value["graphSha256"] == graph.graph_sha256 and
             value["productionFeasibility"] == "UNKNOWN",
             "MINIMUM_VIAS_RESULT_IDENTITY_INVALID")
    _require(type(value["reachable"]) is bool, "MINIMUM_VIAS_RESULT_INVALID")
    _require(value["termination"] in ("GOAL_POPPED", "FRONTIER_EXHAUSTED"),
             "MINIMUM_VIAS_TERMINATION_INVALID")
    if value["reachable"]:
        _require(value["status"] == "REACHABLE" and
                 value["termination"] == "GOAL_POPPED" and
                 value["fullReachabilityCensus"] is False and
                 type(value["minimumViaCount"]) is int and
                 0 <= value["minimumViaCount"] < graph.state_count and
                 type(value["goalState"]) is int and
                 0 <= value["goalState"] < graph.state_count and
                 graph.goals[value["goalState"]],
                 "MINIMUM_VIAS_REACHABLE_RESULT_INVALID")
    else:
        _require(value["status"] == "UNREACHABLE" and
                 value["termination"] == "FRONTIER_EXHAUSTED" and
                 value["fullReachabilityCensus"] is True and
                 value["minimumViaCount"] is None and value["goalState"] is None,
                 "MINIMUM_VIAS_UNREACHABLE_RESULT_INVALID")

    counters = value["counters"]
    counter_keys = {
        "uniqueStatesAdmitted", "statePops", "planarEdgesExamined", "viaChecks",
        "viaTransitionsExamined", "levelsStarted", "maximumQueueStates",
        "maximumPendingViaStates",
    }
    reader._exact_keys(counters, counter_keys, "MINIMUM_VIAS_COUNTERS_INVALID")
    _require(type(counters) is dict and
             all(type(count) is int and count >= 0 for count in counters.values()),
             "MINIMUM_VIAS_COUNTERS_INVALID")
    for key, count in counters.items():
        if key == "planarEdgesExamined":
            _require(count <= MAX_COUNTER_EDGES, "MINIMUM_VIAS_EDGE_BOUND_EXCEEDED")
        else:
            _require(count <= MAX_COUNTER_STATES, "MINIMUM_VIAS_STATE_BOUND_EXCEEDED")
    _require(counters["uniqueStatesAdmitted"] <= graph.state_count and
             counters["statePops"] <= counters["uniqueStatesAdmitted"] and
             counters["viaChecks"] <= counters["statePops"] and
             counters["viaTransitionsExamined"] <= counters["viaChecks"] and
             1 <= counters["levelsStarted"] <= counters["uniqueStatesAdmitted"] and
             counters["maximumQueueStates"] <= counters["uniqueStatesAdmitted"] and
             counters["maximumPendingViaStates"] <= counters["uniqueStatesAdmitted"],
             "MINIMUM_VIAS_COUNTER_RELATION_INVALID")

    acceptance = value["acceptance"]
    _require(type(acceptance) is dict and
             acceptance == {
                 "actualCaptureAttested": False,
                 "route": False,
                 "layout": False,
                 "electrical": False,
                 "q100": False,
             }, "MINIMUM_VIAS_ACCEPTANCE_INVALID")

    def check_tree(node: Any, depth: int = 0) -> None:
        _require(depth <= 8, "MINIMUM_VIAS_RESULT_TOO_DEEP")
        if type(node) is dict:
            _require(len(node) <= 32, "MINIMUM_VIAS_RESULT_TOO_LARGE")
            for key, child in node.items():
                _require(type(key) is str and key.lower() not in _FORBIDDEN_PATH_KEYS,
                         "MINIMUM_VIAS_PATH_DATA_FORBIDDEN")
                check_tree(child, depth + 1)
        elif type(node) is list:
            _require(len(node) <= 8, "MINIMUM_VIAS_RESULT_TOO_LARGE")
            for child in node:
                check_tree(child, depth + 1)
        elif node is None or type(node) in (str, bool, int):
            return
        else:
            raise MinimumViaInputError("MINIMUM_VIAS_RESULT_VALUE_INVALID")

    check_tree(value)
    _require(len(reader._canonical_json_bytes(value)) <= 16 * 1024,
             "MINIMUM_VIAS_RESULT_TOO_LARGE")
    return value


def _build_record(reader: types.ModuleType, *, summary_raw: bytes, receipt_raw: bytes,
                  summary_value: Dict[str, Any], graph: Any, case: Dict[str, Any],
                  reader_sha: str, oracle_sha: str, core_sha: str,
                  minimum_vias_record: Dict[str, Any]) -> Dict[str, Any]:
    source = summary_value["source"]
    native = summary_value["nativeRun"]
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS_MINIMUM_VIA_LOWER_BOUND_DESCRIPTION_ONLY",
        "source": {
            "readerFilename": "read_fixed_prefix_witness.py",
            "readerDirectory": "same-directory-as-cli",
            "readerSha256": reader_sha,
            "oracleFilename": "read_fixed_prefix_graph.py",
            "oracleDirectory": "same-directory-as-cli",
            "oracleSha256": oracle_sha,
            "minimumViasFilename": "fixed_prefix_minimum_vias.py",
            "minimumViasDirectory": "same-directory-as-cli",
            "minimumViasSha256": core_sha,
        },
        "provenance": {
            "summarySha256": reader.SUMMARY_SHA256,
            "summaryBytes": len(summary_raw),
            "summarySchema": reader.SUMMARY_SCHEMA,
            "nativeReceiptSha256": reader.RECEIPT_SHA256,
            "nativeReceiptBytes": len(receipt_raw),
            "nativeRunSchema": reader.RECEIPT_SCHEMA,
            "nativeExitCode": native["nativeExitCode"],
            "terminationProven": native["terminationProven"],
            "source": native["source"],
            "inputAudit": native["inputAudit"],
            "cleanupStatus": native["cleanupStatus"],
            "nativeSourceContext": {
                "inputCount": source["inputCount"],
                "inputBytes": source["inputBytes"],
                "pinsSha256": source["pinsSha256"],
                "sourceSnapshotRevision": source["sourceSnapshotRevision"],
                "layoutVersion": native["layoutVersion"],
                "dependencyEpoch": native["dependencyEpoch"],
                "command": native["command"],
            },
            "lineage": "PREVIOUSLY_ACCEPTED_B6_ONLY_NO_FRESH_NATIVE_PROOF",
            "graphLineCount": 2,
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
        "selectedCase": {
            "variant": case["variant"],
            "caseId": graph.case_id,
            "graphSha256": graph.graph_sha256,
            "graphBytes": case["graphBytes"],
            "width": graph.width,
            "height": graph.height,
            "primaryLayer": graph.primary,
            "prefixHoleCount": case["prefixHoleCount"],
            "gridPitch": 10,
        },
        "minimumVias": minimum_vias_record,
        "productionFeasibility": "UNKNOWN",
        "acceptance": {
            "actualCaptureAttested": False,
            "route": False,
            "layout": False,
            "electrical": False,
            "q100": False,
        },
        "scopeLimits": [
            "The result is the exact minimum VIA count for this fixed directed two-face graph only.",
            "The search does not replay production history, via spacing, scheduler limits, path interaction, clearance, connectivity, or layout finalization.",
            "An unreachable result applies only to this fixed graph; it does not prove the whole board impossible or explain production search exhaustion.",
            "This CLI emits the minimum count without comparing it to a production budget; any later comparison belongs to source-bound qualification.",
            "The complete receipt hash binds previously accepted B6 lineage; this CLI does not claim fresh native proof.",
        ],
    }


def _run(argv: Optional[Sequence[str]] = None) -> Tuple[types.ModuleType, Path, bytes]:
    reader, directory, reader_sha = _load_pinned_witness_reader()
    args = reader._parse_arguments(sys.argv[1:] if argv is None else argv)
    reader._verify_sha_argument(args.get("summary-sha256"), reader.SUMMARY_SHA256,
                                "SUMMARY_SHA256_ARGUMENT_INVALID")
    reader._verify_sha_argument(args.get("receipt-sha256"), reader.RECEIPT_SHA256,
                                "RECEIPT_SHA256_ARGUMENT_INVALID")
    _require(args.get("case") in reader._EXPECTED_CASES, "CASE_INVALID")
    output_path = reader._prepare_output_path(args.get("output"))

    summary_raw = reader._read_bounded_input(
        args.get("summary"), reader.MAX_SUMMARY_BYTES, "SUMMARY")
    _require(reader._sha256(summary_raw) == reader.SUMMARY_SHA256,
             "SUMMARY_SHA256_MISMATCH")
    summary_value = reader._parse_json_bytes(summary_raw, label="SUMMARY")
    summary_by_variant = reader._validate_summary_content(summary_value)

    receipt_raw = reader._read_bounded_input(
        args.get("receipt"), reader.MAX_RECEIPT_BYTES, "RECEIPT")
    _require(len(receipt_raw) == reader.RECEIPT_BYTES and
             reader._sha256(receipt_raw) == reader.RECEIPT_SHA256,
             "RECEIPT_SHA256_OR_LENGTH_MISMATCH")

    # The full accepted summary and receipt are pinned before any receipt line
    # is interpreted. The reader's strict extractor consumes only its two graph
    # rows; other raw receipt lines remain bound by the full receipt digest.
    graph_payloads = reader._extract_tagged_graph_payloads(receipt_raw)
    oracle, oracle_sha = _load_pinned_oracle(reader, directory)
    graph_rows = reader._validate_graphs(graph_payloads, summary_by_variant, oracle)
    case = reader._EXPECTED_CASES[args["case"]]
    graph, _graph_raw = graph_rows[case["caseId"]]

    core, core_sha = _load_pinned_minimum_vias(reader, oracle, directory)
    try:
        minimum_vias_record = core.analyze_minimum_vias(graph)
    except Exception:
        raise MinimumViaInputError("MINIMUM_VIAS_ANALYSIS_FAILED") from None
    minimum_vias_record = _validate_minimum_vias_record(reader, minimum_vias_record, graph)
    report = _build_record(
        reader,
        summary_raw=summary_raw,
        receipt_raw=receipt_raw,
        summary_value=summary_value,
        graph=graph,
        case=case,
        reader_sha=reader_sha,
        oracle_sha=oracle_sha,
        core_sha=core_sha,
        minimum_vias_record=minimum_vias_record,
    )
    raw_output = reader._canonical_json_bytes(report) + b"\n"
    _require(len(raw_output) <= MAX_OUTPUT_BYTES, "OUTPUT_SIZE_INVALID")
    return reader, output_path, raw_output


def main(argv: Optional[Sequence[str]] = None) -> int:
    reader: Optional[types.ModuleType] = None
    try:
        reader, output_path, raw_output = _run(argv)
        reader._write_new_output(output_path, raw_output)
        print("PASS: fixed-graph minimum VIA description only; production feasibility UNKNOWN")
        return 0
    except Exception as error:
        code = getattr(error, "code", None)
        if type(code) is not str or re.fullmatch(r"[A-Z0-9_]{1,96}", code) is None:
            code = "INPUT_INVALID"
        print("FAIL: " + code)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
