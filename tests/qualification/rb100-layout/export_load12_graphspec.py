"""Export a portable, separately validated cap48 LOAD12 source graphspec.

The maintained quality-zero-via reader remains the owner of all source,
receipt, graph-mask and LOAD12-pre validation.  This adapter calls its public
validation entry point, binds the same bounded artifact bytes to that report,
and reparses those bytes only to retain the full already-validated records.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import tempfile
import types
from pathlib import Path
from typing import Any, Dict, Mapping, Optional, Sequence, Tuple


SCHEMA = "tsj-load12-source-graphspec-v1"
RESULT_SCHEMA = "tsj-load12-source-graphspec-export-result-v1"
MAX_EXPORT_BYTES = 16 * 1024 * 1024
MAX_PATH_CHARS = 4096
SHA256_RE = re.compile(r"[0-9a-f]{64}\Z", re.ASCII)
ERROR_CODE_RE = re.compile(r"[A-Z0-9_]{1,96}\Z", re.ASCII)
HEX_MASK_RE = re.compile(r"[0-9a-f]+\Z", re.ASCII)

SOURCE_SPEC_SHA256 = "20c469e804d64a7740c0a9d79066841045bb672c6d2fbc26099b89c1a52f7298"
SOURCE_PINS_SHA256 = "0516a02dc18a221f0f71a50720e8c84004770f39cc588306cd463e472b72a82f"
CURRENT_READER_SHA256 = "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc"
SOURCE_INPUT_COUNT = 1079
SOURCE_INPUT_BYTES = 86847809
SOURCE_REVISION = 20
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
SOURCE_PATH_TUPLE_SHA256 = "1410a3abd90478627a638109cc31971802e53b3590015f83ebe4d8a57923a13d"
SOURCE_GOAL_SHA256 = "cc48299ed82e39f1bac10269e7c41afc6468f1e53fc96baa36a95d5c60d548d2"
SOURCE_PREFIX_GEOMETRY_SHA256 = "7c963bb9ba10f7419d2e6abaecff4cfc1504ba2744914d6874ffd02ca475c447"

PRODUCER_CODE_PATHS = (
    "tests/contracts/Rb100Load12ZeroViaGraphContractTest.java",
    "tests/contracts/Rb100PhysicalDiagnostics.java",
    "tests/contracts/P07AcceptedBranchCaptureDiagnostics.java",
    "tests/contracts/P07AcceptedBranchCaptureContractTest.java",
    "tests/contracts/P07ZeroViaGraphDiagnostics.java",
    "tests/contracts/P07ZeroViaGraphContractTest.java",
)

TOP_KEYS = frozenset((
    "schema", "status", "scope", "sourceCap", "sourceEvidence",
    "selection", "grid", "initialIdentities", "prefixGeometrySha256",
    "sourcePath", "sourceGraph", "load12Pre", "limitations",
))
SOURCE_EVIDENCE_KEYS = frozenset((
    "inputSpecSchema", "inputSpecSha256", "nativeRunSha256",
    "sourceSnapshotRevision", "inputCount", "inputBytes",
    "inputPinsSha256", "nativeInputPins", "requiredNativeInputSha256",
    "producerCodeSha256", "readerAndHelperSha256", "exporterSha256",
    "artifacts", "validatedRecordSha256", "modelEpoch",
))
SELECTION_KEYS = frozenset((
    "operationId", "rootSeed", "placementSeed", "routingSeed",
    "routeRequestIndex", "selectedPlacementIndex", "selectedPlacementAttempt",
    "pass", "branchOrdinal", "netId", "netPosition", "netIndexNextFetch",
    "policy", "maximumTransitions", "prefixHoleCount", "prefixTraceCount",
))
GRID_KEYS = frozenset((
    "width", "height", "cells", "originX", "originY", "pitch",
    "startCell", "initialLayerMask", "endCell", "endLayer",
    "goalUnionCellCount",
))
IDENTITY_KEYS = frozenset((
    "startPadId", "rootPadId", "reachedPadIds", "reachedPthPadIds",
    "foundTerminalPadId", "seedQueueSize", "seedFaceMask", "seedSerials",
))
SOURCE_PATH_KEYS = frozenset((
    "nodeCount", "length", "bends", "transitions", "directDistance",
    "tupleSha256", "endpointPadId",
))
SOURCE_GRAPH_KEYS = frozenset(("event", "analysis", "summary"))
SOURCE_GRAPH_EVENT_KEYS = frozenset((
    "protocolVersion", "sourceRevision", "runId", "operationId", "target",
    "scope", "binding", "diagnostic", "feasibility", "legalRouteProof",
    "layoutQualification", "routeAdoption",
))
SOURCE_GRAPH_SUMMARY_KEYS = frozenset((
    "protocolVersion", "sourceRevision", "status", "runId", "operationId",
    "events", "target", "scope", "ordinaryQueries",
    "sourceReleasedBeforeSuccessorWork", "feasibility", "legalRouteProof",
    "layoutQualification", "routeAdoption",
))
SOURCE_GRAPH_DIAGNOSTIC_KEYS = frozenset(
    ("schema", "stability", "grid", "path", "work", "masks"))
LIMITATION_KEYS = frozenset((
    "sourceGraphFeasibility", "routeAdoption", "layoutQualification",
    "playable100", "cap17OutcomeIncluded",
))


class GraphspecExportError(ValueError):
    """An export failure with a stable, path-free machine code."""

    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


def _fail(code: str) -> None:
    raise GraphspecExportError(code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        _fail(code)


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
    except GraphspecExportError:
        raise
    except (OSError, RuntimeError, ValueError):
        _fail(code + "_READ_FAILED")


def _load_public_reader() -> Tuple[types.ModuleType, bytes, Path]:
    own_path = Path(os.path.abspath(__file__))
    _need(not _has_reparse_component(own_path) and own_path.is_file(),
          "EXPORTER_SOURCE_INVALID")
    reader_path = own_path.parent / "read_quality_zero_via.py"
    raw = _read_bounded(reader_path, 2 * 1024 * 1024, "CURRENT_READER")
    _need(_sha256(raw) == CURRENT_READER_SHA256,
          "CURRENT_READER_HASH_MISMATCH")
    module = types.ModuleType("_tsj_load12_graphspec_source_reader")
    module.__file__ = os.fspath(reader_path)
    module.__package__ = ""
    try:
        source = raw.decode("utf-8", errors="strict")
        exec(compile(source, os.fspath(reader_path), "exec", dont_inherit=True),
             module.__dict__)
    except (UnicodeDecodeError, SyntaxError, ValueError, TypeError):
        _fail("CURRENT_READER_SOURCE_INVALID")
    return module, raw, reader_path


def _parse_cli(argv: Sequence[str]) -> Tuple[str, str, str]:
    _need(type(argv) in (list, tuple) and len(argv) == 6 and
          argv[0] == "--input-spec" and argv[2] == "--input-spec-sha256" and
          argv[4] == "--output", "CLI_ARGUMENTS_INVALID")
    spec_path, spec_sha, output_path = argv[1], argv[3], argv[5]
    _need(type(spec_path) is str and 0 < len(spec_path) <= MAX_PATH_CHARS and
          type(output_path) is str and 0 < len(output_path) <= MAX_PATH_CHARS and
          type(spec_sha) is str and SHA256_RE.fullmatch(spec_sha) is not None,
          "CLI_ARGUMENTS_INVALID")
    _need(spec_sha == SOURCE_SPEC_SHA256, "SOURCE_SPEC_HASH_NOT_APPROVED")
    return spec_path, spec_sha, output_path


def _prepare_output_path(value: str) -> Path:
    _need(type(value) is str and 0 < len(value) <= MAX_PATH_CHARS,
          "OUTPUT_PATH_INVALID")
    try:
        candidate = Path(value)
        if not candidate.is_absolute():
            candidate = Path.cwd() / candidate
        candidate = Path(os.path.abspath(os.fspath(candidate)))
        _need(candidate.name not in ("", ".", ".."), "OUTPUT_PATH_INVALID")
        _need(not _has_reparse_component(candidate), "OUTPUT_REPARSE_PATH")
        parent = candidate.parent.resolve(strict=True)
        _need(parent.is_dir() and not _has_reparse_component(parent),
              "OUTPUT_PARENT_INVALID")
        target = parent / candidate.name
        _need(not _is_reparse(target), "OUTPUT_REPARSE_PATH")
        _need(not target.exists(), "OUTPUT_ALREADY_EXISTS")
        return target
    except GraphspecExportError:
        raise
    except (OSError, RuntimeError, TypeError, ValueError):
        _fail("OUTPUT_PATH_UNAVAILABLE")


def _write_atomic_exclusive(path: Path, raw: bytes) -> None:
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_EXPORT_BYTES,
          "OUTPUT_SIZE_INVALID")
    _need(not _has_reparse_component(path) and not path.exists() and
          not _is_reparse(path), "OUTPUT_ALREADY_EXISTS")
    temporary_path: Optional[Path] = None
    descriptor: Optional[int] = None
    file_stream = None
    primary_failure: Optional[BaseException] = None
    primary_cause: Optional[BaseException] = None
    cleanup_failures = []
    try:
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=".tsj-load12-graphspec-", suffix=".tmp", dir=os.fspath(path.parent))
        temporary_path = Path(temporary_name)
        file_stream = os.fdopen(descriptor, "wb")
        descriptor = None
        file_stream.write(raw)
        file_stream.flush()
        os.fsync(file_stream.fileno())
        # Closing is part of cleanup. Do not publish if it fails; the finally
        # block retries the close and still attempts to remove the temp file.
        try:
            file_stream.close()
        except (OSError, RuntimeError, ValueError) as error:
            cleanup_failures.append(error)
        else:
            file_stream = None
        if not cleanup_failures:
            _need(not _has_reparse_component(path.parent) and
                  not _has_reparse_component(path) and not path.exists(),
                  "OUTPUT_ALREADY_EXISTS")
            # A hard link publishes the fully closed file atomically and fails
            # if another actor created the destination after our last check.
            os.link(temporary_path, path)
    except FileExistsError as error:
        primary_failure = GraphspecExportError("OUTPUT_ALREADY_EXISTS")
        primary_cause = error
    except GraphspecExportError as error:
        primary_failure = error
    except (OSError, RuntimeError, TypeError, ValueError) as error:
        primary_failure = GraphspecExportError("OUTPUT_ATOMIC_WRITE_FAILED")
        primary_cause = error
    finally:
        if file_stream is not None:
            try:
                file_stream.close()
            except (OSError, RuntimeError, ValueError) as error:
                cleanup_failures.append(error)
        if descriptor is not None:
            try:
                os.close(descriptor)
            except (OSError, RuntimeError, ValueError) as error:
                cleanup_failures.append(error)
        if temporary_path is not None:
            try:
                temporary_path.unlink(missing_ok=True)
            except (OSError, RuntimeError, ValueError) as error:
                cleanup_failures.append(error)

    if cleanup_failures:
        cleanup_failure = GraphspecExportError("OUTPUT_ATOMIC_CLEANUP_FAILED")
        cleanup_failure.cleanup_failures = tuple(cleanup_failures)
        if primary_failure is not None:
            if primary_cause is not None:
                primary_failure.__cause__ = primary_cause
            raise cleanup_failure from primary_failure
        raise cleanup_failure from cleanup_failures[0]
    if primary_failure is not None:
        if primary_cause is not None:
            raise primary_failure from primary_cause
        raise primary_failure


def _validate_report_envelope(report: Any, reader_sha: str,
                              spec_sha: str) -> Mapping[str, Any]:
    _need(type(report) is dict and report.get("status") == "PASS" and
          report.get("schema") == "q100-quality-zero-via-reader-result-v5" and
          report.get("target") == "LOAD12" and
          report.get("suite") == "Rb100Load12ZeroViaGraphContractTest" and
          report.get("sourceSnapshotRevision") == SOURCE_REVISION,
          "PUBLIC_READER_REPORT_IDENTITY_INVALID")
    source = report.get("source")
    artifacts = report.get("artifacts")
    _need(type(source) is dict and type(artifacts) is dict and
          source.get("readerSha256") == reader_sha == CURRENT_READER_SHA256 and
          source.get("pinsSha256") == SOURCE_PINS_SHA256 and
          source.get("inputCount") == SOURCE_INPUT_COUNT and
          source.get("inputBytes") == SOURCE_INPUT_BYTES and
          artifacts.get("inputSpecSha256") == spec_sha,
          "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID")
    return report


def _ledger_call(ledger: types.ModuleType, function: Any, fallback: str,
                 *args: Any, **kwargs: Any) -> Any:
    try:
        return function(*args, **kwargs)
    except ledger.LedgerInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _path_call(frozen: types.ModuleType, function: Any, fallback: str,
               *args: Any) -> Any:
    try:
        return function(*args)
    except frozen.QualityPathInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _scalar_call(scalar: types.ModuleType, function: Any, fallback: str,
                 *args: Any) -> Any:
    try:
        return function(*args)
    except scalar.QualityScalarInputError as error:
        _fail(_safe_foreign_code(error, fallback))


def _load_context_and_records(reader: types.ModuleType, spec_argument: str,
                              spec_sha: str, report: Mapping[str, Any],
                              reader_raw: bytes, reader_path: Path,
                              exporter_raw: bytes
                              ) -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
    frozen, scalar, ledger, capture, helper_hashes = reader._load_pinned_helpers()
    _need(helper_hashes.get("newReaderSha256") == _sha256(reader_raw),
          "CURRENT_READER_HASH_MISMATCH")
    spec_path = _ledger_call(ledger, ledger._resolve_path,
                             "INPUT_SPEC_INVALID", spec_argument,
                             Path.cwd(), "INPUT_SPEC")
    spec_raw = _ledger_call(ledger, ledger._read_bounded,
                            "INPUT_SPEC_INVALID", spec_path,
                            reader.MAX_SPEC_BYTES, "INPUT_SPEC")
    _need(_sha256(spec_raw) == spec_sha == SOURCE_SPEC_SHA256,
          "INPUT_SPEC_SHA256_MISMATCH")
    spec = _ledger_call(ledger, ledger._strict_json,
                        "INPUT_SPEC_INVALID", spec_raw, "INPUT_SPEC")
    target, suite, legacy_spec = reader._validate_target_spec(spec)
    _need(target == "LOAD12" and suite == reader.SUITES["LOAD12"],
          "SOURCE_TARGET_NOT_LOAD12")
    frozen.SUITE = suite
    scalar.SUITE = suite
    context = _ledger_call(ledger, ledger._validate_input_spec,
                           "INPUT_SPEC_INVALID", legacy_spec,
                           spec_path.parent, capture)
    _need(context["inputCount"] == SOURCE_INPUT_COUNT and
          context["inputBytes"] == SOURCE_INPUT_BYTES and
          context["pinsSha256"] == SOURCE_PINS_SHA256 and
          reader.QUALITY_REQUIRED_INPUTS <= set(context["pinMap"]),
          "SOURCE_MANIFEST_IDENTITY_INVALID")

    artifacts = context["artifacts"]
    captures = {}
    for name, limit, allow_empty in (
            ("resultPath", reader.MAX_RESULT_BYTES, False),
            ("stdoutPath", reader.MAX_CAPTURE_BYTES, False),
            ("receiptPath", reader.MAX_CAPTURE_BYTES, False),
            ("stderrPath", reader.MAX_CAPTURE_BYTES, True)):
        captures[name] = _ledger_call(
            ledger, ledger._read_bounded, "NATIVE_ARTIFACT_READ_FAILED",
            artifacts[name], limit, name.upper(), allow_empty=allow_empty)

    expected_required = {
        relative: context["pinMap"][relative]["sha256"]
        for relative in sorted(reader.QUALITY_REQUIRED_INPUTS)
    }
    source_report = report["source"]
    _need(source_report.get("requiredNativeInputSha256") == expected_required,
          "PUBLIC_READER_REPORT_INPUT_HASH_BINDING_INVALID")
    expected_artifacts = {
        "inputSpecSha256": spec_sha,
        "nativeRunSha256": _sha256(captures["resultPath"]),
        "stdoutBytes": len(captures["stdoutPath"]),
        "stdoutSha256": _sha256(captures["stdoutPath"]),
        "receiptBytes": len(captures["receiptPath"]),
        "receiptSha256": _sha256(captures["receiptPath"]),
        "stderrBytes": len(captures["stderrPath"]),
        "stderrSha256": _sha256(captures["stderrPath"]),
    }
    _need(report["artifacts"] == expected_artifacts,
          "PUBLIC_READER_REPORT_ARTIFACT_HASH_BINDING_INVALID")

    run_result = _ledger_call(ledger, ledger._strict_json,
                              "NATIVE_RUN_JSON_INVALID", captures["resultPath"],
                              "NATIVE_RUN")
    legacy_run_result = reader._validate_native_run_target(
        run_result, target, scalar.NATIVE_RUN_KEYS)
    _path_call(frozen, frozen._validate_native_run, "NATIVE_RUN_INVALID",
               legacy_run_result, context, captures["stdoutPath"],
               captures["receiptPath"], captures["stderrPath"], scalar)
    _console, receipt_lines = reader._validate_console_and_receipt(
        captures["stdoutPath"], captures["receiptPath"],
        captures["stderrPath"], ledger)
    parsed = reader._validate_receipt_lines(
        receipt_lines, ledger, frozen, scalar, target)
    _assert_report_matches_parsed(report, parsed)

    _scalar_call(scalar, scalar._stable_source_check,
                 "SOURCE_REVALIDATION_FAILED", spec_raw, spec_path,
                 spec_sha, legacy_spec, context, ledger, capture)
    helper_sha = helper_hashes["frozenQualityPathReaderSha256"]
    reader._stable_helpers(_sha256(reader_raw), helper_sha,
                           helper_hashes, frozen, scalar)
    _need(_read_bounded(spec_path, reader.MAX_SPEC_BYTES,
                        "INPUT_SPEC_REVALIDATION") == spec_raw,
          "INPUT_SPEC_CHANGED_DURING_READ")
    for name, raw, limit, allow_empty in (
            ("resultPath", captures["resultPath"], reader.MAX_RESULT_BYTES, False),
            ("stdoutPath", captures["stdoutPath"], reader.MAX_CAPTURE_BYTES, False),
            ("receiptPath", captures["receiptPath"], reader.MAX_CAPTURE_BYTES, False),
            ("stderrPath", captures["stderrPath"], reader.MAX_CAPTURE_BYTES, True)):
        again = _ledger_call(ledger, ledger._read_bounded,
                             "NATIVE_ARTIFACT_REVALIDATION_FAILED",
                             artifacts[name], limit, name.upper(),
                             allow_empty=allow_empty)
        _need(again == raw, "NATIVE_ARTIFACT_CHANGED_DURING_READ")
    _need(_read_bounded(reader_path, 2 * 1024 * 1024,
                        "CURRENT_READER_REVALIDATION") == reader_raw,
          "CURRENT_READER_CHANGED_DURING_READ")
    _need(_read_bounded(Path(os.path.abspath(__file__)),
                        2 * 1024 * 1024,
                        "EXPORTER_SOURCE_REVALIDATION") == exporter_raw,
          "EXPORTER_SOURCE_CHANGED_DURING_READ")
    return spec, context, parsed


def _assert_report_matches_parsed(report: Mapping[str, Any],
                                  parsed: Mapping[str, Any]) -> None:
    scalar = parsed["scalar"]["event"]
    graph = parsed["graph"]
    load12 = parsed["load12Branch"]
    observation = report["qualityObservation"]
    graph_report = report["qualityZeroViaGraphObservation"]
    load_report = report["load12BranchObservation"]
    _need(observation["runId"] == scalar["runId"] and
          graph_report["target"] == graph["target"] == "LOAD12" and
          graph_report["binding"] == graph["binding"] and
          graph_report["grid"] == graph["diagnostic"]["grid"] and
          load_report["runId"] == load12["runId"] and
          load_report["approvedRequest"]["rootSeed"] ==
          load12["event"]["rootSeed"] and
          load_report["path"]["tupleSha256"] ==
          load12["pre"]["path"]["tupleSha256"] and
          load_report["goal"]["faceBitsSha256"] ==
          load12["pre"]["path"]["goal"]["faceBitsSha256"],
          "PUBLIC_READER_REPORT_RECORD_BINDING_INVALID")


def _assert_json_tree(value: Any, path: Tuple[str, ...] = ()) -> None:
    if value is None or type(value) in (str, bool, int):
        if type(value) is str:
            _need(not re.match(r"^(?:[A-Za-z]:[/\\]|[/\\]{2})", value),
                  "GRAPH_SPEC_PRIVATE_PATH")
        return
    if type(value) is list:
        _need(len(value) <= 1_000_000, "GRAPH_SPEC_ARRAY_SIZE_INVALID")
        for index, item in enumerate(value):
            _assert_json_tree(item, path + (str(index),))
        return
    if type(value) is dict:
        _need(all(type(key) is str for key in value),
              "GRAPH_SPEC_OBJECT_KEY_INVALID")
        _need(len(value) <= 100_000, "GRAPH_SPEC_OBJECT_SIZE_INVALID")
        for key, item in value.items():
            _assert_json_tree(item, path + (key,))
        return
    _fail("GRAPH_SPEC_VALUE_TYPE_INVALID")


def _safe_manifest(pin_map: Mapping[str, Mapping[str, Any]]) -> Dict[str, Dict[str, Any]]:
    _need(type(pin_map) is dict and len(pin_map) == SOURCE_INPUT_COUNT,
          "SOURCE_MANIFEST_COUNT_INVALID")
    result: Dict[str, Dict[str, Any]] = {}
    for relative in sorted(pin_map):
        normalized = _validate_repo_relative_path(relative)
        item = pin_map[relative]
        _need(type(item) is dict and type(item.get("bytes")) is int and
              item["bytes"] >= 0 and type(item.get("sha256")) is str and
              SHA256_RE.fullmatch(item["sha256"]) is not None,
              "SOURCE_MANIFEST_ENTRY_INVALID")
        result[normalized] = {"bytes": item["bytes"], "sha256": item["sha256"]}
    _need(sum(item["bytes"] for item in result.values()) == SOURCE_INPUT_BYTES,
          "SOURCE_MANIFEST_BYTE_COUNT_INVALID")
    return result


def _validate_repo_relative_path(relative: Any) -> str:
    normalized = relative.replace("\\", "/") if type(relative) is str else ""
    _need(relative == normalized and normalized and
          not normalized.startswith("/") and
          re.match(r"^[A-Za-z]:", normalized) is None and
          all(part not in ("", ".", "..") for part in normalized.split("/")),
          "SOURCE_MANIFEST_PATH_INVALID")
    return normalized


def _validated_prefix_counts(normalized_pre: Mapping[str, Any],
                             raw_pre: Mapping[str, Any]) -> Tuple[int, int]:
    """Bind normalized prefix totals to their validated raw receipt fields."""
    normalized_inventory = normalized_pre["prefix"]["inventory"]
    raw_prefix = raw_pre["prefixInventory"]
    raw_totals = raw_prefix["totals"]
    by_kind = raw_totals["byKind"]
    normalized_traces = normalized_pre["prefixTraces"]
    raw_traces = raw_pre["prefixTraces"]
    _need(type(by_kind) is dict and
          set(by_kind) == {"VIA", "PLATED", "NON_PLATED"} and
          type(normalized_inventory.get("totalHoles")) is int and
          type(by_kind["VIA"]) is int and by_kind["VIA"] ==
          normalized_inventory["totalHoles"] and
          by_kind["PLATED"] == 0 and by_kind["NON_PLATED"] == 0 and
          type(normalized_traces) is list and type(raw_traces) is dict and
          set(raw_traces) == {"count", "rows"} and
          type(raw_traces["count"]) is int and
          raw_traces["count"] == len(normalized_traces) == len(raw_traces["rows"]),
          "SOURCE_PREFIX_COUNTS_MISMATCH")
    return by_kind["VIA"], raw_traces["count"]


def _validate_terminal_binding(logical_root_pad_id: Any,
                               path_endpoint_pad_id: Any,
                               graph_found_terminal_pad_id: Any) -> None:
    """Keep the logical root distinct from an optional tree-goal terminal."""
    _need(type(logical_root_pad_id) is str and bool(logical_root_pad_id) and
          (path_endpoint_pad_id is None or
           (type(path_endpoint_pad_id) is str and bool(path_endpoint_pad_id))) and
          graph_found_terminal_pad_id == path_endpoint_pad_id,
          "SOURCE_GRAPH_TERMINAL_BINDING_INVALID")


def _validate_source_anchors(parsed: Mapping[str, Any]) -> Dict[str, Any]:
    graph = parsed["graph"]
    load12 = parsed["load12Branch"]
    event = load12["event"]
    pre = load12["pre"]
    raw_pre = pre["pre"]
    source = pre["source"]["source"]
    source_grid = source["grid"]
    prefix = pre["prefix"]
    path = pre["path"]
    goal = path["goal"]
    graph_event = graph["event"]
    binding = graph["binding"]
    diagnostic = graph["diagnostic"]
    graph_grid = diagnostic["grid"]
    graph_masks = diagnostic["masks"]
    graph_stability = diagnostic["stability"]
    prefix_geometry = prefix["stability"]["prefixGeometrySha256"]
    prefix_via_count, prefix_trace_count = _validated_prefix_counts(pre, raw_pre)
    _validate_terminal_binding(source["rootPadId"], path["endTerminalPadId"],
                               binding["foundTerminalPadId"])
    _need(event["rootSeed"] == ROOT_SEED and
          event["placementSeed"] == PLACEMENT_SEED and
          event["routingSeed"] == ROUTING_SEED and
          event["routeRequestIndex"] == 0 and
          event["selectedPlacementIndex"] == 0 and
          event["selectedPlacementAttempt"] == 4 and
          event["operationId"] == "QUALITY_PROBE" and
          source["pass"] == 1 and source["netId"] == "LOAD12" and
          source["branchOrdinal"] == 73 and source["netPosition"] == 3 and
          source["netIndexNextFetch"] == 4 and
          source["startPadId"] == "JLOAD.1" and
          source["rootPadId"] == "CSTORE_E.1" and
          source["policy"] == "FULLER_TWO_LAYER" and
          source_grid["primaryLayer"] == "BOTTOM" and
          source_grid["maximumVias"] == 48 and
          source_grid["maximumTransitions"] == 4 and
          source_grid["minX"] == 30 and source_grid["minY"] == 30 and
          source_grid["width"] == 478 and source_grid["height"] == 309 and
          source_grid["cells"] == 147702 and
          prefix["inventory"]["totalHoles"] == 17 and
          prefix_via_count == 17 and prefix_trace_count == 88 and
          path["path"]["nodeCount"] == 222 and path["length"] == 2170 and
          path["bends"] == 2 and path["transitions"] == 4 and
          path["tupleSha256"] == SOURCE_PATH_TUPLE_SHA256 and
          goal["faceBitsSha256"] == SOURCE_GOAL_SHA256 and
          prefix_geometry == SOURCE_PREFIX_GEOMETRY_SHA256 and
          graph_event["target"] == "LOAD12" and
          binding["maximumVias"] == 48 and
          binding["maximumTransitions"] == 4 and
          binding["branchOrdinal"] == 73 and
          binding["netPosition"] == 3 and
          binding["netIndexNextFetch"] == 4 and
          binding["pathNodeCount"] == 222 and
          binding["pathTransitionCount"] == 4 and
          binding["pathTupleSha256"] == SOURCE_PATH_TUPLE_SHA256 and
          binding["goalFaceBitsSha256"] == SOURCE_GOAL_SHA256 and
          diagnostic["schema"] == "p07-zero-via-graph-r5" and
          graph_stability["prefixGeometrySha256"] == prefix_geometry and
          graph_grid["width"] == 478 and graph_grid["height"] == 309 and
          graph_grid["cells"] == 147702 and graph_grid["originX"] == 30 and
          graph_grid["originY"] == 30 and graph_grid["pitch"] == 10,
          "SOURCE_GRAPH_ANCHOR_MISMATCH")
    cells = graph_grid["cells"]
    _need(type(graph_masks) is dict and
          set(graph_masks) == {"topStepsHex", "bottomStepsHex", "goalFacesHex"} and
          all(type(graph_masks[key]) is str and len(graph_masks[key]) == cells and
              HEX_MASK_RE.fullmatch(graph_masks[key]) is not None
              for key in ("topStepsHex", "bottomStepsHex", "goalFacesHex")) and
          graph_masks["goalFacesHex"] == goal["faceBits"] and
          _sha256(graph_masks["goalFacesHex"].encode("ascii")) == SOURCE_GOAL_SHA256,
          "SOURCE_GRAPH_MASKS_INVALID")
    return {
        "event": event,
        "load12Pre": raw_pre,
        "validatedLoad12Pre": pre,
        "source": source,
        "sourceGrid": source_grid,
        "prefix": prefix,
        "path": path,
        "goal": goal,
        "graphEvent": graph_event,
        "graphBinding": binding,
        "graphDiagnostic": diagnostic,
        "graphSummary": parsed["graphSummary"],
        "prefixGeometrySha256": prefix_geometry,
    }


def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"),
                          ensure_ascii=True, allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        _fail("GRAPH_SPEC_CANONICALIZATION_INVALID")


def _canonical_sha256(value: Any) -> str:
    return _sha256(_canonical_json(value))


def _build_graphspec(reader: types.ModuleType, report: Mapping[str, Any],
                     spec: Mapping[str, Any], context: Mapping[str, Any],
                     parsed: Mapping[str, Any], exporter_sha: str) -> Dict[str, Any]:
    anchor = _validate_source_anchors(parsed)
    source = anchor["source"]
    source_grid = anchor["sourceGrid"]
    path = anchor["path"]
    goal = anchor["goal"]
    graph_event = anchor["graphEvent"]
    graph_binding = anchor["graphBinding"]
    graph_grid = anchor["graphDiagnostic"]["grid"]
    validated_pre = anchor["validatedLoad12Pre"]
    graph_report = report["qualityZeroViaGraphObservation"]
    load_report = report["load12BranchObservation"]

    pin_map = _safe_manifest(context["pinMap"])
    required_hashes = {
        relative: pin_map[relative]["sha256"]
        for relative in sorted(reader.QUALITY_REQUIRED_INPUTS)
    }
    producer_hashes = {
        relative: pin_map[relative]["sha256"]
        for relative in PRODUCER_CODE_PATHS
    }
    helper_source = report["source"]
    reader_helpers = {
        name: helper_source[source_key]
        for name, source_key in (
            ("currentReader", "readerSha256"),
            ("frozenQualityPathReader", "frozenQualityPathReaderSha256"),
            ("frozenScalarReader", "frozenScalarReaderSha256"),
            ("prefixLedger", "prefixLedgerSha256"),
            ("captureHelper", "captureHelperSha256"),
            ("graphOracle", "graphOracleSha256"),
            ("minimumViaHelper", "minimumViaHelperSha256"),
        )
    }
    _need(all(type(value) is str and SHA256_RE.fullmatch(value) is not None
              for value in reader_helpers.values()),
          "SOURCE_HELPER_HASH_INVALID")

    artifacts = report["artifacts"]
    scalar_event = parsed["scalar"]["event"]
    load_event = anchor["event"]
    raw_pre = anchor["load12Pre"]
    validated_records = {
        "qualityScalarEventSha256": _canonical_sha256(scalar_event),
        "load12BranchEventSha256": _canonical_sha256(load_event),
        "load12PreSha256": _canonical_sha256(raw_pre),
        "zeroViaGraphEventSha256": _canonical_sha256(graph_event),
    }
    model_epoch = {
        "layoutVersion": reader.LAYOUT_VERSION,
        "dependencyEpoch": reader.DEPENDENCY_EPOCH,
        "planVersion": reader.PLAN_VERSION,
        "modelVersion": reader.MODEL_VERSION,
        "physicalIntentVersion": reader.PHYSICAL_INTENT_VERSION,
    }
    source_evidence = {
        "inputSpecSchema": spec["schema"],
        "inputSpecSha256": artifacts["inputSpecSha256"],
        "nativeRunSha256": artifacts["nativeRunSha256"],
        "sourceSnapshotRevision": context["sourceSnapshotRevision"],
        "inputCount": context["inputCount"],
        "inputBytes": context["inputBytes"],
        "inputPinsSha256": context["pinsSha256"],
        "nativeInputPins": pin_map,
        "requiredNativeInputSha256": required_hashes,
        "producerCodeSha256": producer_hashes,
        "readerAndHelperSha256": reader_helpers,
        "exporterSha256": exporter_sha,
        "artifacts": {
            "stdoutBytes": artifacts["stdoutBytes"],
            "stdoutSha256": artifacts["stdoutSha256"],
            "receiptBytes": artifacts["receiptBytes"],
            "receiptSha256": artifacts["receiptSha256"],
            "stderrBytes": artifacts["stderrBytes"],
            "stderrSha256": artifacts["stderrSha256"],
        },
        "validatedRecordSha256": validated_records,
        "modelEpoch": model_epoch,
    }
    selection = {
        "operationId": load_event["operationId"],
        "rootSeed": load_event["rootSeed"],
        "placementSeed": load_event["placementSeed"],
        "routingSeed": load_event["routingSeed"],
        "routeRequestIndex": load_event["routeRequestIndex"],
        "selectedPlacementIndex": load_event["selectedPlacementIndex"],
        "selectedPlacementAttempt": load_event["selectedPlacementAttempt"],
        "pass": source["pass"],
        "branchOrdinal": source["branchOrdinal"],
        "netId": source["netId"],
        "netPosition": source["netPosition"],
        "netIndexNextFetch": source["netIndexNextFetch"],
        "policy": source["policy"],
        "maximumTransitions": source_grid["maximumTransitions"],
        "prefixHoleCount": anchor["prefix"]["inventory"]["totalHoles"],
        "prefixTraceCount": len(validated_pre["prefixTraces"]),
    }
    grid = {
        "width": graph_grid["width"], "height": graph_grid["height"],
        "cells": graph_grid["cells"], "originX": graph_grid["originX"],
        "originY": graph_grid["originY"], "pitch": graph_grid["pitch"],
        "startCell": graph_grid["startCell"],
        "initialLayerMask": graph_grid["initialLayerMask"],
        "endCell": graph_grid["endCell"], "endLayer": graph_grid["endLayer"],
        "goalUnionCellCount": graph_grid["goalUnionCellCount"],
    }
    initial = {
        "startPadId": source["startPadId"],
        "rootPadId": source["rootPadId"],
        "reachedPadIds": source["reachedPadIds"],
        "reachedPthPadIds": source["reachedPthPadIds"],
        "foundTerminalPadId": graph_binding["foundTerminalPadId"],
        "seedQueueSize": validated_pre["seeds"]["snapshot"]["queueSize"],
        "seedFaceMask": validated_pre["seeds"]["snapshot"]["faceMask"],
        "seedSerials": [node["serial"] for node in validated_pre["seeds"]["nodes"]],
    }
    source_path = {
        "nodeCount": path["path"]["nodeCount"],
        "length": path["length"], "bends": path["bends"],
        "transitions": path["transitions"],
        "directDistance": path["directDistance"],
        "tupleSha256": path["tupleSha256"],
        "endpointPadId": path["endTerminalPadId"],
    }
    graph_capture = {
        "event": graph_event,
        "analysis": parsed["graph"]["analysis"],
        "summary": anchor["graphSummary"],
    }
    _need(graph_report["binding"] == graph_binding and
          load_report["runId"] == load_event["runId"],
          "PUBLIC_REPORT_EXPORT_BINDING_INVALID")

    graphspec = {
        "schema": SCHEMA,
        "status": "PASS",
        "scope": "SOURCE_CAP48_LOAD12_REFERENCE_ONLY",
        "sourceCap": source_grid["maximumVias"],
        "sourceEvidence": source_evidence,
        "selection": selection,
        "grid": grid,
        "initialIdentities": initial,
        "prefixGeometrySha256": anchor["prefixGeometrySha256"],
        "sourcePath": source_path,
        "sourceGraph": graph_capture,
        "load12Pre": raw_pre,
        "limitations": {
            "sourceGraphFeasibility": "UNKNOWN",
            "routeAdoption": False,
            "layoutQualification": False,
            "playable100": False,
            "cap17OutcomeIncluded": False,
        },
    }
    _validate_graphspec_envelope(graphspec)
    return graphspec


def _validate_graphspec_envelope(value: Any) -> None:
    _need(type(value) is dict and set(value) == TOP_KEYS and
          value["schema"] == SCHEMA and value["status"] == "PASS" and
          value["scope"] == "SOURCE_CAP48_LOAD12_REFERENCE_ONLY" and
          value["sourceCap"] == 48,
          "GRAPH_SPEC_ENVELOPE_INVALID")
    for field, expected in (
            ("sourceEvidence", SOURCE_EVIDENCE_KEYS),
            ("selection", SELECTION_KEYS), ("grid", GRID_KEYS),
            ("initialIdentities", IDENTITY_KEYS),
            ("sourcePath", SOURCE_PATH_KEYS),
            ("sourceGraph", SOURCE_GRAPH_KEYS),
            ("limitations", LIMITATION_KEYS)):
        _need(type(value[field]) is dict and set(value[field]) == expected,
              "GRAPH_SPEC_CLOSED_SCHEMA_INVALID")
    evidence = value["sourceEvidence"]
    _need(evidence["inputSpecSha256"] == SOURCE_SPEC_SHA256 and
          evidence["inputPinsSha256"] == SOURCE_PINS_SHA256 and
          evidence["sourceSnapshotRevision"] == SOURCE_REVISION and
          evidence["inputCount"] == SOURCE_INPUT_COUNT and
          evidence["inputBytes"] == SOURCE_INPUT_BYTES and
          evidence["readerAndHelperSha256"]["currentReader"] ==
          CURRENT_READER_SHA256 and
          evidence["nativeInputPins"].get(
              "tests/contracts/Rb100PhysicalDiagnostics.java", {}).get("sha256") ==
          evidence["producerCodeSha256"][
              "tests/contracts/Rb100PhysicalDiagnostics.java"],
          "GRAPH_SPEC_EVIDENCE_BINDING_INVALID")
    _need(type(value["grid"]["cells"]) is int and
          value["grid"]["cells"] == 147702 and
          value["grid"]["width"] * value["grid"]["height"] ==
          value["grid"]["cells"] and
          type(value["sourceGraph"]["event"]) is dict and
          set(value["sourceGraph"]["event"]) == SOURCE_GRAPH_EVENT_KEYS and
          type(value["sourceGraph"]["summary"]) is dict and
          set(value["sourceGraph"]["summary"]) == SOURCE_GRAPH_SUMMARY_KEYS and
          type(value["load12Pre"]) is dict and
          set(value["load12Pre"]) == {
              "schema", "source", "seeds", "prefixInventory", "prefixTraces",
              "path", "goal", "nativePublisher", "work", "stability"},
          "GRAPH_SPEC_SOURCE_RECORD_INVALID")
    diagnostic = value["sourceGraph"]["event"]["diagnostic"]
    _need(type(diagnostic) is dict and
          set(diagnostic) == SOURCE_GRAPH_DIAGNOSTIC_KEYS,
          "GRAPH_SPEC_DIAGNOSTIC_SCHEMA_INVALID")
    masks = diagnostic.get("masks") if type(diagnostic) is dict else None
    _need(type(masks) is dict and
          set(masks) == {"topStepsHex", "bottomStepsHex", "goalFacesHex"} and
          all(type(masks[key]) is str and len(masks[key]) == 147702 and
              HEX_MASK_RE.fullmatch(masks[key]) is not None
              for key in ("topStepsHex", "bottomStepsHex", "goalFacesHex")) and
          masks["goalFacesHex"] == value["load12Pre"]["goal"]["faceBits"],
          "GRAPH_SPEC_FULL_MASKS_INVALID")
    _need(value["limitations"] == {
        "sourceGraphFeasibility": "UNKNOWN", "routeAdoption": False,
        "layoutQualification": False, "playable100": False,
        "cap17OutcomeIncluded": False,
    }, "GRAPH_SPEC_LIMITATION_CLAIMS_INVALID")
    _assert_json_tree(value)


def _make_summary(graphspec: Mapping[str, Any], raw: bytes) -> Dict[str, Any]:
    return {
        "schema": RESULT_SCHEMA,
        "status": "PASS",
        "graphspecSha256": _sha256(raw),
        "graphspecBytes": len(raw),
        "sourceSpecSha256": graphspec["sourceEvidence"]["inputSpecSha256"],
        "manifestSha256": graphspec["sourceEvidence"]["inputPinsSha256"],
        "nativeReceiptSha256": graphspec["sourceEvidence"]["artifacts"]["receiptSha256"],
        "sourceCap": graphspec["sourceCap"],
        "grid": graphspec["grid"],
        "maskCellCount": graphspec["grid"]["cells"],
        "sourcePath": graphspec["sourcePath"],
        "prefixHoleCount": graphspec["selection"]["prefixHoleCount"],
        "prefixTraceCount": graphspec["selection"]["prefixTraceCount"],
        "limits": graphspec["limitations"],
    }


def build_verified_graphspec(spec_argument: str,
                             spec_sha: str) -> Tuple[Dict[str, Any], bytes]:
    """Validate the approved source and return its canonical graphspec bytes.

    This public API is read-only: it has no output path and does not create or
    modify files. Callers can independently recompute the portable artifact.
    """
    _need(type(spec_sha) is str and SHA256_RE.fullmatch(spec_sha) is not None,
          "CLI_ARGUMENTS_INVALID")
    _need(spec_sha == SOURCE_SPEC_SHA256, "SOURCE_SPEC_HASH_NOT_APPROVED")
    own_path = Path(os.path.abspath(__file__))
    exporter_raw = _read_bounded(own_path, 2 * 1024 * 1024, "EXPORTER_SOURCE")
    exporter_sha = _sha256(exporter_raw)
    reader, reader_raw, reader_path = _load_public_reader()
    report = reader.run_validation(spec_argument, spec_sha)
    _validate_report_envelope(report, _sha256(reader_raw), spec_sha)
    spec, context, parsed = _load_context_and_records(
        reader, spec_argument, spec_sha, report, reader_raw, reader_path,
        exporter_raw)
    graphspec = _build_graphspec(reader, report, spec, context, parsed,
                                 exporter_sha)
    _need(_read_bounded(own_path, 2 * 1024 * 1024,
                        "EXPORTER_SOURCE_REVALIDATION") == exporter_raw,
          "EXPORTER_SOURCE_CHANGED_DURING_READ")
    raw = _canonical_json(graphspec) + b"\n"
    _need(len(raw) <= MAX_EXPORT_BYTES, "OUTPUT_SIZE_INVALID")
    return graphspec, raw


def run_export(spec_argument: str, spec_sha: str,
               output_argument: str) -> Dict[str, Any]:
    output_path = _prepare_output_path(output_argument)
    graphspec, raw = build_verified_graphspec(spec_argument, spec_sha)
    output_path = _prepare_output_path(output_argument)
    _write_atomic_exclusive(output_path, raw)
    return _make_summary(graphspec, raw)


def main(argv: Optional[Sequence[str]] = None) -> int:
    try:
        spec_path, spec_sha, output_path = _parse_cli(
            list(sys.argv[1:] if argv is None else argv))
        summary = run_export(spec_path, spec_sha, output_path)
        sys.stdout.buffer.write(_canonical_json(summary) + b"\n")
        sys.stdout.buffer.flush()
        return 0
    except Exception as error:
        code = _safe_foreign_code(error, "GRAPH_SPEC_EXPORT_INVALID")
        try:
            sys.stdout.write("FAIL: " + code + "\n")
            sys.stdout.flush()
        except Exception:
            pass
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
