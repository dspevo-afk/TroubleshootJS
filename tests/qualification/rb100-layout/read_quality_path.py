#!/usr/bin/env python3
"""Strict source-bound reader for the RB100 Publisher quality-path receipt.

The reader validates one captured actual path and its source-bound rejection
context. Repeated geometry is reported as a candidate for inspection; this
module does not prove a legal shortcut, another reachable route, physical
layout, electrical behavior, or playable qualification.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import types
import uuid
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, Optional, Sequence, Tuple


INPUT_SCHEMA = "p07-quality-path-input-v1"
RUN_SCHEMA = "q100-quality-path-native-run-v1"
RESULT_SCHEMA = "q100-quality-path-reader-result-v1"
SUITE = "Rb100QualityPathContractTest"
ENTRY_SCRIPT = "scripts/verify-current-contracts.ps1"
SOURCE_REVISION = 14
SOURCE_INPUT_COUNT = 1068
CONTRACT_JAVA_COUNT = 212
LAYOUT_VERSION = 26
DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28"
PLAN_VERSION = 1
MODEL_VERSION = 4
PHYSICAL_INTENT_VERSION = 9
MAX_SCALAR = 2_147_483_647
MAX_GRID_CELLS = 250_000
SECONDARY_LAYER_COST = 2
TURN_COST = 35
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
VARIANT = "SEPARATE_DIRECT_SWITCHING"
NULL_OPERATION_ID = "QUALITY_NULL"
PROBE_OPERATION_ID = "QUALITY_PROBE"
FOCUSED_PASS = (
    "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."

PINNED_SCALAR_READER_SHA256 = "0396ae2566454063c6c744fbeb49e68859edb9ef092a23b027e01a3977f52802"
QUALITY_REQUIRED_INPUTS = frozenset({
    "tests/contracts/Rb100QualityPathContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "scripts/verify-current-contracts.ps1",
    "tests/contracts/current-contract-runner-contract.ps1",
})

PATH_EVENT_MARKER = "RB100_QUALITY_PATH_EVENT"
PATH_SUMMARY_MARKER = "PASS: RB100 quality path contracts"
PATH_EVENT_KEYS = frozenset((
    "protocolVersion sourceRevision runId operationId order captureStage "
    "captureQualityIndex parentIdentityVerified nodeIdentityUnique nodes scope "
    "feasibility legalShortcutProof layoutQualification"
).split())
PATH_NODE_KEYS = frozenset((
    "index parentIndex cell x y layer direction transitions cost heuristic serial key"
).split())
PATH_SUMMARY_KEYS = frozenset((
    "protocolVersion sourceRevision status runId operationId events nodes scope "
    "feasibility legalShortcutProof layoutQualification"
).split())
RECEIPT_MARKERS = (
    "RB100_QUALITY_SCALAR_CONTROLS",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS",
    "RB100_QUALITY_SCALAR_OPERATION",
    "RB100_QUALITY_SCALAR_EVENT",
    "RB100_QUALITY_SCALAR_PAIR",
    "PASS: RB100 quality scalar contracts",
    PATH_EVENT_MARKER,
    PATH_SUMMARY_MARKER,
)
EVENT_PREFIXES = ("RB100_", "P07_", "PASS: RB100")
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)

MAX_SPEC_BYTES = 1024 * 1024
MAX_RESULT_BYTES = 2 * 1024 * 1024
MAX_CAPTURE_BYTES = 32 * 1024 * 1024
class QualityPathInputError(ValueError):
    """A static sanitized failure; input text and paths are never interpolated."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise QualityPathInputError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


def _integer(value: Any, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _exact_keys(value: Any, expected: Iterable[str], code: str) -> None:
    _need(type(value) is dict and set(value) == set(expected), code)


def _raise_static_error(error: BaseException, fallback: str) -> None:
    code = getattr(error, "code", None)
    _fail(code if type(code) is str and ERROR_CODE_RE.fullmatch(code) is not None
          else fallback)


def _scalar_call(scalar: types.ModuleType, function, fallback: str, *args):
    try:
        return function(*args)
    except scalar.QualityScalarInputError as error:
        _raise_static_error(error, fallback)


def _ledger_call(ledger: types.ModuleType, function, fallback: str, *args, **kwargs):
    try:
        return function(*args, **kwargs)
    except ledger.LedgerInputError as error:
        _raise_static_error(error, fallback)


def _sha256(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


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


def _read_local(path: Path, limit: int, code: str, allow_empty: bool = False) -> bytes:
    try:
        _need(not _has_reparse_component(path) and not _is_reparse(path),
              code + "_REPARSE_PATH")
        info = path.stat()
        _need(path.is_file(), code + "_NOT_FILE")
        _need((0 <= info.st_size if allow_empty else 0 < info.st_size) and
              info.st_size <= limit, code + "_SIZE_INVALID")
        raw = path.read_bytes()
        _need(len(raw) == info.st_size and len(raw) <= limit, code + "_READ_SIZE_CHANGED")
        return raw
    except QualityPathInputError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _exec_scalar_private(raw: bytes, path: Path, name: str) -> types.ModuleType:
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
        _fail("FROZEN_SCALAR_READER_SOURCE_INVALID")
    finally:
        if had_previous:
            sys.modules[name] = previous
        else:
            sys.modules.pop(name, None)
    return module


def _load_frozen_scalar() -> Tuple[types.ModuleType, types.ModuleType,
                                   types.ModuleType, Dict[str, str]]:
    """Load the byte-frozen scalar reader privately and override metadata only."""
    try:
        own_path = Path(os.path.abspath(__file__))
        _need(not _has_reparse_component(own_path), "READER_SOURCE_REPARSE_PATH")
        own_path = own_path.resolve(strict=True)
        _need(own_path.is_file() and not _is_reparse(own_path), "READER_SOURCE_INVALID")
        scalar_path = own_path.parent / "read_quality_scalars.py"
        scalar_raw = _read_local(scalar_path, 2 * 1024 * 1024, "FROZEN_SCALAR_READER")
        _need(_sha256(scalar_raw) == PINNED_SCALAR_READER_SHA256,
              "FROZEN_SCALAR_READER_HASH_MISMATCH")
        scalar = _exec_scalar_private(
            scalar_raw, scalar_path, "_q100_quality_path_scalar_" + uuid.uuid4().hex)
        # Explicit epoch/schema compatibility metadata only. Validators and source
        # remain unchanged, and this module is never left in sys.modules.
        scalar.INPUT_SCHEMA = INPUT_SCHEMA
        scalar.RUN_SCHEMA = RUN_SCHEMA
        scalar.SUITE = SUITE
        scalar.SOURCE_REVISION = SOURCE_REVISION
        scalar.QUALITY_REQUIRED_INPUTS = QUALITY_REQUIRED_INPUTS
        _need(scalar.SOURCE_INPUT_COUNT == SOURCE_INPUT_COUNT and
              scalar.CONTRACT_JAVA_COUNT == CONTRACT_JAVA_COUNT,
              "FROZEN_SCALAR_CENSUS_INVALID")
        try:
            ledger, capture, helper_hashes = scalar._load_pinned_helpers()
        except scalar.QualityScalarInputError as error:
            _raise_static_error(error, "FROZEN_SCALAR_HELPER_LOAD_FAILED")
        helper_hashes["frozenScalarReaderSha256"] = _sha256(scalar_raw)
        return scalar, ledger, capture, helper_hashes
    except QualityPathInputError:
        raise


def _parse_tagged(line: str, marker: str, ledger: types.ModuleType,
                  scalar: types.ModuleType) -> Any:
    return _scalar_call(scalar, scalar._parse_tagged, "PATH_RECEIPT_JSON_INVALID",
                        line, marker, ledger, "PATH_RECEIPT_RECORD")


def _validate_path_event(value: Any, scalar_event: Mapping[str, Any],
                         scalar: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, PATH_EVENT_KEYS, "PATH_EVENT_KEYS_INVALID")
    run_id = scalar_event["runId"]
    _scalar_call(scalar, scalar._validate_identity, "PATH_EVENT_IDENTITY_INVALID",
                 value, run_id)
    _need(value["operationId"] == PROBE_OPERATION_ID and
          value["order"] == "START_TO_END" and
          _integer(value["captureStage"], 1, 1) and
          value["parentIdentityVerified"] is True and
          value["nodeIdentityUnique"] is True and
          value["scope"] == "ACTUAL_PUBLISHER_PATH_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False,
          "PATH_EVENT_IDENTITY_OR_SCOPE_INVALID")

    nodes = value["nodes"]
    _need(type(nodes) is list and len(nodes) == scalar_event["pathNodeCount"] and
          _integer(value["captureQualityIndex"], 1, MAX_GRID_CELLS * 2) and
          value["captureQualityIndex"] == len(nodes),
          "PATH_CAPTURE_INDEX_OR_COUNT_INVALID")
    width, height = scalar_event["gridWidth"], scalar_event["gridHeight"]
    cells = width * height
    pitch = scalar_event["gridPitch"]
    _need(_integer(cells, 1, MAX_GRID_CELLS) and pitch == 10 and
          len(nodes) <= cells * 2,
          "PATH_GRID_DOMAIN_INVALID")

    parsed: List[Dict[str, int]] = []
    keys = set()
    serials = set()
    for index, node in enumerate(nodes):
        _exact_keys(node, PATH_NODE_KEYS, "PATH_NODE_KEYS_INVALID")
        _need(_integer(node["index"], 0, len(nodes) - 1) and node["index"] == index and
              _integer(node["parentIndex"], -1, len(nodes) - 1) and
              node["parentIndex"] == (-1 if index == 0 else index - 1),
              "PATH_PARENT_ORDER_INVALID")
        physical_max_x = scalar_event["gridOriginX"] + (width - 1) * pitch
        physical_max_y = scalar_event["gridOriginY"] + (height - 1) * pitch
        _need(_integer(node["x"], -MAX_SCALAR - 1, MAX_SCALAR) and
              _integer(node["y"], -MAX_SCALAR - 1, MAX_SCALAR) and
              scalar_event["gridOriginX"] <= node["x"] <= physical_max_x and
              scalar_event["gridOriginY"] <= node["y"] <= physical_max_y,
              "PATH_GRID_COORDINATE_INVALID")
        grid_dx = node["x"] - scalar_event["gridOriginX"]
        grid_dy = node["y"] - scalar_event["gridOriginY"]
        _need(grid_dx % pitch == 0 and grid_dy % pitch == 0 and
              grid_dx // pitch < width and grid_dy // pitch < height and
              _integer(node["cell"], 0, cells - 1) and
              node["cell"] == (grid_dy // pitch) * width + (grid_dx // pitch),
              "PATH_GRID_COORDINATE_INVALID")
        _need(_integer(node["layer"], 0, 1) and
              _integer(node["direction"], 0, 4) and
              _integer(node["transitions"], 0, 4) and
              _integer(node["cost"], 0, MAX_SCALAR) and
              _integer(node["heuristic"], 0, MAX_SCALAR) and
              _integer(node["serial"], 0, MAX_SCALAR) and
              _integer(node["key"], 0, MAX_SCALAR),
              "PATH_NODE_DOMAIN_INVALID")
        expected_key = (((node["transitions"] * 2 + node["layer"]) * 5 +
                         node["direction"]) * cells) + node["cell"]
        _need(node["key"] == expected_key, "PATH_STATE_KEY_INVALID")
        _need(node["key"] not in keys and node["serial"] not in serials,
              "PATH_NODE_IDENTITY_DUPLICATE")
        keys.add(node["key"])
        serials.add(node["serial"])
        parsed.append(node)

    first, last = parsed[0], parsed[-1]
    _need((first["x"], first["y"], first["layer"], first["direction"],
           first["transitions"]) ==
          (scalar_event["startX"], scalar_event["startY"],
           scalar_event["firstLayer"], 4, 0) and
          (last["x"], last["y"], last["layer"], last["transitions"]) ==
          (scalar_event["endX"], scalar_event["endY"], scalar_event["endLayer"],
           scalar_event["transitionCount"]),
          "PATH_ENDPOINT_IDENTITY_INVALID")
    initial_cost = (0 if first["layer"] == scalar_event["primaryLayer"]
                    else SECONDARY_LAYER_COST)
    _need(first["cost"] == initial_cost, "PATH_INITIAL_COST_INVALID")

    same_face_cells: Dict[Tuple[int, int], int] = {}
    same_face_edges: Dict[Tuple[int, int, int], int] = {}
    planar_edges = 0
    bends = 0
    serial_previous = -1
    for index, node in enumerate(parsed):
        _need(node["serial"] > serial_previous, "PATH_SERIAL_ORDER_INVALID")
        serial_previous = node["serial"]
        cell_key = (node["layer"], node["cell"])
        same_face_cells[cell_key] = same_face_cells.get(cell_key, 0) + 1
        if index == 0:
            continue
        previous = parsed[index - 1]
        dx, dy = node["x"] - previous["x"], node["y"] - previous["y"]
        _need(node["layer"] == previous["layer"] and
              node["transitions"] == previous["transitions"] and
              abs(dx) + abs(dy) == pitch,
              "PATH_EDGE_ADJACENCY_INVALID")
        direction = (1 if dx == pitch else 3 if dx == -pitch else
                     2 if dy == pitch else 0)
        _need(node["direction"] == direction,
              "PATH_HEADING_INVALID")
        turn = previous["direction"] != 4 and previous["direction"] != direction
        if turn:
            bends += 1
        step_cost = pitch + (SECONDARY_LAYER_COST
                             if node["layer"] != scalar_event["primaryLayer"] else 0)
        if turn:
            step_cost += TURN_COST
        _need(node["cost"] == previous["cost"] + step_cost,
              "PATH_COST_CONSERVATION_INVALID")
        planar_edges += 1
        edge = (node["layer"], min(previous["cell"], node["cell"]),
                max(previous["cell"], node["cell"]))
        same_face_edges[edge] = same_face_edges.get(edge, 0) + 1

    planar_length = planar_edges * pitch
    direct_distance = (abs(last["x"] - first["x"]) +
                       abs(last["y"] - first["y"]))
    transition_count = sum(
        1 for before, after in zip(parsed, parsed[1:])
        if before["layer"] != after["layer"])
    _need(planar_length == scalar_event["planarLength"] and
          direct_distance == scalar_event["directDistance"] and
          bends == scalar_event["bends"] and
          transition_count == scalar_event["transitionCount"] == 0 and
          len(parsed) - 1 == planar_edges + transition_count,
          "PATH_SCALAR_CONSERVATION_INVALID")

    repeated_cells = sum(count - 1 for count in same_face_cells.values() if count > 1)
    repeated_edges = sum(count - 1 for count in same_face_edges.values() if count > 1)
    return {
        "event": dict(value),
        "nodes": parsed,
        "pathNodeCount": len(parsed),
        "planarLength": planar_length,
        "directDistance": direct_distance,
        "bends": bends,
        "transitionCount": transition_count,
        "repeatedSameFaceCells": repeated_cells,
        "repeatedSameFaceEdges": repeated_edges,
        "scope": "ACTUAL_PUBLISHER_PATH_ONLY",
        "feasibility": "UNKNOWN",
        "legalShortcutProof": False,
        "layoutQualification": False,
    }


def _validate_path_summary(value: Any, event: Mapping[str, Any],
                           scalar: types.ModuleType) -> Dict[str, Any]:
    _exact_keys(value, PATH_SUMMARY_KEYS, "PATH_SUMMARY_KEYS_INVALID")
    _scalar_call(scalar, scalar._validate_identity, "PATH_SUMMARY_IDENTITY_INVALID",
                 value, event["runId"])
    _need(value["status"] == "PASS" and
          value["operationId"] == PROBE_OPERATION_ID and
          _integer(value["events"], 1, 1) and
          _integer(value["nodes"], 1, MAX_GRID_CELLS * 2) and
          value["nodes"] == event["captureQualityIndex"] and
          value["scope"] == "ACTUAL_PUBLISHER_PATH_ONLY" and
          value["feasibility"] == "UNKNOWN" and
          value["legalShortcutProof"] is False and
          value["layoutQualification"] is False,
          "PATH_SUMMARY_IDENTITY_INVALID")
    return dict(value)


def _validate_current_authored_context(scalar_receipt: Mapping[str, Any]) -> Dict[str, Any]:
    expected = {
        "physicalPolicyId": "MEDIUM_BOARD",
        "physicalPolicyVersion": 1,
        "placementDensityVersion": 14,
        "routingLayer": "BOTTOM",
        "traceWidthProfile": "ROLE_TRACE@1",
        "placementPackingVersion": 4,
    }
    for operation_name in ("nullOperation", "probeOperation"):
        operation = scalar_receipt[operation_name]
        facts = operation["facts"]
        for field, wanted in expected.items():
            actual = facts.get(field)
            if type(wanted) is int:
                valid = _integer(actual, wanted, wanted)
            else:
                valid = type(actual) is str and actual == wanted
            _need(valid, "QUALITY_PATH_CONTEXT_INVALID")
    return dict(expected)


def _validate_receipt_lines(lines: Sequence[str], ledger: types.ModuleType,
                            scalar: types.ModuleType) -> Dict[str, Any]:
    _need(type(lines) in (list, tuple) and len(lines) == len(RECEIPT_MARKERS) and
          all(type(line) is str and line for line in lines),
          "PATH_RECEIPT_EVENT_COUNT_INVALID")
    records = [
        _parse_tagged(line, marker, ledger, scalar)
        for line, marker in zip(lines, RECEIPT_MARKERS)
    ]
    old = _scalar_call(scalar, scalar._validate_receipt_lines,
                       "SCALAR_RECEIPT_INVALID", lines[:8], ledger)
    current_context = _validate_current_authored_context(old)
    path = _validate_path_event(records[8], old["event"], scalar)
    summary = _validate_path_summary(records[9], records[8], scalar)
    return {"scalar": old, "path": path, "pathSummary": summary,
            "currentContext": current_context}


def _validate_console_and_receipt(stdout_raw: bytes, receipt_raw: bytes,
                                  stderr_raw: bytes, ledger: types.ModuleType,
                                  scalar: types.ModuleType
                                  ) -> Tuple[List[str], List[str]]:
    try:
        console = ledger._decode_lines(stdout_raw, "RUN_STDOUT")
        receipt = ledger._decode_lines(receipt_raw, "RUN_RECEIPT")
        stderr = ledger._decode_lines(stderr_raw, "RUN_STDERR")
    except ledger.LedgerInputError as error:
        _raise_static_error(error, "RUN_CAPTURE_UTF8_INVALID")
    _need(len(receipt) == len(RECEIPT_MARKERS) and all(receipt),
          "RUN_RECEIPT_EVENT_COUNT_INVALID")
    _need(all(line.startswith(marker + " ")
              for line, marker in zip(receipt, RECEIPT_MARKERS)),
          "RUN_RECEIPT_MARKER_ORDER_INVALID")
    console_events = [line for line in console if line.startswith(EVENT_PREFIXES)]
    stderr_events = [line for line in stderr if line.startswith(EVENT_PREFIXES)]
    _need(console_events == receipt and not stderr_events,
          "RUN_CONSOLE_RECEIPT_MISMATCH")
    _need(console.count(FOCUSED_PASS) == 1 and console.count(CLEANUP_LINE) == 1 and
          console.index(FOCUSED_PASS) < console.index(CLEANUP_LINE),
          "RUN_CONSOLE_PASS_OR_CLEANUP_INVALID")
    _need(sum(line.startswith(PATH_SUMMARY_MARKER + " ") for line in receipt) == 1,
          "RUN_PATH_SUMMARY_MARKER_INVALID")
    return console, receipt


def _validate_native_run(value: Any, context: Mapping[str, Any],
                         stdout_raw: bytes, receipt_raw: bytes, stderr_raw: bytes,
                         scalar: types.ModuleType) -> None:
    _scalar_call(scalar, scalar._validate_native_run, "NATIVE_RUN_INVALID",
                 value, context, stdout_raw, receipt_raw, stderr_raw)


def _stable_reader_check(reader_sha: str, helper_hashes: Mapping[str, str],
                         scalar: types.ModuleType) -> None:
    own_path = Path(os.path.abspath(__file__))
    own_raw = _read_local(own_path, 2 * 1024 * 1024, "READER_SOURCE")
    _need(_sha256(own_raw) == reader_sha, "READER_SOURCE_CHANGED_DURING_READ")
    _scalar_call(scalar, scalar._stable_helper_check, "PINNED_HELPER_CHANGED_DURING_READ", {
            "readerSha256": helper_hashes["readerSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
        })


def run_validation(spec_argument: str, spec_sha256: str) -> Dict[str, Any]:
    reader_raw = _read_local(Path(os.path.abspath(__file__)),
                             2 * 1024 * 1024, "READER_SOURCE")
    reader_sha = _sha256(reader_raw)
    scalar, ledger, capture, helper_hashes = _load_frozen_scalar()
    _need(type(spec_sha256) is str and SHA256_RE.fullmatch(spec_sha256) is not None,
          "CLI_ARGUMENTS_INVALID")
    spec_path = _ledger_call(ledger, ledger._resolve_path, "INPUT_SPEC_INVALID",
                             spec_argument, Path.cwd(), "INPUT_SPEC")
    spec_raw = _ledger_call(ledger, ledger._read_bounded, "INPUT_SPEC_INVALID",
                            spec_path, MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha256, "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(ledger, ledger._strict_json, "INPUT_SPEC_INVALID",
                        spec_raw, "INPUT_SPEC")
    context = _ledger_call(ledger, ledger._validate_input_spec, "INPUT_SPEC_INVALID",
                           spec, spec_path.parent, capture)
    _need(QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]),
          "QUALITY_SOURCE_PINS_OMIT_REQUIRED_INPUT")
    result_raw = _ledger_call(ledger, ledger._read_bounded, "NATIVE_RUN_READ_FAILED",
                              context["artifacts"]["resultPath"],
                              MAX_RESULT_BYTES, "NATIVE_RUN")
    stdout_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_STDOUT_READ_FAILED",
                              context["artifacts"]["stdoutPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDOUT")
    receipt_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_RECEIPT_READ_FAILED",
                               context["artifacts"]["receiptPath"],
                               MAX_CAPTURE_BYTES, "RUN_RECEIPT")
    stderr_raw = _ledger_call(ledger, ledger._read_bounded, "RUN_STDERR_READ_FAILED",
                              context["artifacts"]["stderrPath"],
                              MAX_CAPTURE_BYTES, "RUN_STDERR", allow_empty=True)
    run_result = _ledger_call(ledger, ledger._strict_json, "NATIVE_RUN_JSON_INVALID",
                              result_raw, "NATIVE_RUN")
    _validate_native_run(run_result, context, stdout_raw, receipt_raw, stderr_raw, scalar)
    _console, receipt_lines = _validate_console_and_receipt(
        stdout_raw, receipt_raw, stderr_raw, ledger, scalar)
    parsed = _validate_receipt_lines(receipt_lines, ledger, scalar)
    _scalar_call(scalar, scalar._stable_source_check, "SOURCE_REVALIDATION_FAILED",
                 spec_raw, spec_path, spec_sha256, spec, context, ledger, capture)
    _stable_reader_check(reader_sha, helper_hashes, scalar)

    required_hashes = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(QUALITY_REQUIRED_INPUTS)
    }
    scalar_event = parsed["scalar"]["event"]
    path = parsed["path"]
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "sourceSnapshotRevision": SOURCE_REVISION,
        "source": {
            "inputCount": context["inputCount"],
            "inputBytes": context["inputBytes"],
            "pinsSha256": context["pinsSha256"],
            "requiredNativeInputSha256": required_hashes,
            "readerSha256": reader_sha,
            "frozenScalarReaderSha256": helper_hashes["frozenScalarReaderSha256"],
            "prefixLedgerSha256": helper_hashes["prefixLedgerSha256"],
            "captureHelperSha256": helper_hashes["captureHelperSha256"],
            "graphOracleSha256": helper_hashes["graphOracleSha256"],
        },
        "artifacts": {
            "inputSpecSha256": _sha256(spec_raw),
            "nativeRunSha256": _sha256(result_raw),
            "stdoutBytes": len(stdout_raw), "stdoutSha256": _sha256(stdout_raw),
            "receiptBytes": len(receipt_raw), "receiptSha256": _sha256(receipt_raw),
            "stderrBytes": len(stderr_raw), "stderrSha256": _sha256(stderr_raw),
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
        "qualityObservation": {
            "runId": parsed["scalar"]["runId"],
            "variant": VARIANT,
            "routeRequestIndex": scalar_event["routeRequestIndex"],
            "selectedPlacementIndex": scalar_event["selectedPlacementIndex"],
            "selectedPlacementAttempt": scalar_event["selectedPlacementAttempt"],
            "pass": scalar_event["pass"], "netId": scalar_event["netId"],
            **parsed["currentContext"],
            "pathNodeCount": scalar_event["pathNodeCount"],
            "transitionCount": scalar_event["transitionCount"],
            "planarLength": scalar_event["planarLength"],
            "directDistance": scalar_event["directDistance"],
            "bends": scalar_event["bends"],
            "qualityRejected": scalar_event["qualityRejected"],
            "feasibility": "UNKNOWN", "layoutQualification": False,
        },
        "qualityPathObservation": {
            "pathNodeCount": path["pathNodeCount"],
            "planarLength": path["planarLength"],
            "directDistance": path["directDistance"],
            "bends": path["bends"],
            "transitionCount": path["transitionCount"],
            "repeatedSameFaceCells": path["repeatedSameFaceCells"],
            "repeatedSameFaceEdges": path["repeatedSameFaceEdges"],
            "scope": "ACTUAL_PUBLISHER_PATH_ONLY",
            "feasibility": "UNKNOWN",
            "legalShortcutProof": False,
            "layoutQualification": False,
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
