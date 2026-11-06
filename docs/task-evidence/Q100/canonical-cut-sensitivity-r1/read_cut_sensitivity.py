"""Independent consumer for the bounded P7 directed-cut sensitivity run."""

import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile


RESULT_SCHEMA = "tsj-canonical-cut-sensitivity-result-v1"
INDEX_SCHEMA = "tsj-canonical-cut-sensitivity-worker-v1"
READER_SCHEMA = "tsj-canonical-cut-sensitivity-reader-v1"
RESULT_STATUS = "PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY"
CANARY_STATUS = "PASS_LITERAL_HOST_CANARY_ONLY"
READER_ANALYZE_STATUS = "PASS_CONDITIONAL_SENSITIVITY_ONLY"
READER_CANARY_STATUS = "PASS_CANARY_ONLY"
SCOPE = "CANONICAL_BASE_DIRECTED_CUT_SENSITIVITY_ONLY"
PARENT_ID = "23f94f8f4ea8025fe24475fabcb194840e472fd8"
P6_QUALIFICATION_SHA = "d7f39f1a77e302e9e883b4aedbdeb9c416a93cf4cfa85029710b04da5b25d692"
P6_INPUT_SHA = "b640587f9cb1ac3f3ec8feff85940f4b7a9c8fda37570b50bfa30c39fd067771"
P6_READER_SHA = "1d58c06ecb3581ad7d8c9eef1dfcd653d8ba1e5b797b2dc7cda76b2ed621a96e"
RAW_GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a"
PREFIX_GEOMETRY_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
LIMITS = {
    "workNanos": 90_000_000_000,
    "groupNanos": 5_000_000_000,
    "groups": 640,
    "batchEntries": 4096,
    "cloneEntries": 1_048_576,
    "observationUnits": 32768,
    "artifactBytes": 8_388_608,
    "resultBytes": 65_536,
    "lineBytes": 65_536,
    "readChunkBytes": 32_768,
    "specBytes": 4_194_304,
    "parentMs": 180_000,
    "workerMs": 180_000,
    "cleanupMs": 120_000,
    "outerMs": 1_800_000,
}
CASES = (
    ("CONTROL", ()),
    ("B_SENSE_CUT_ONLY", (0,)),
    ("B_REF_CUT_ONLY", (1,)),
    ("BOTH_OBSERVED_CUTS", (0, 1)),
)
DIRECTIONS = ((1, 0, -1), (2, 1, 0), (4, 0, 1), (8, -1, 0))
_HEX = re.compile(r"[0-9a-f]{64}\Z")
_RESULT_KEYS = {
    "schema", "status", "mode", "specSha256", "scope", "parent", "source",
    "graph", "cut", "cases", "work", "acceptance", "limitations",
}
_SOURCE_KEYS = {
    "analysisModuleSha256", "workerSha256", "testsSha256", "graphOracleSha256",
    "viaOracleSha256", "captureReaderSha256", "stableBefore", "stableAfter",
}
_GRAPH_KEYS = {
    "rawSha256", "canonicalSha256", "prefixGeometrySha256", "width", "height",
    "cells", "states", "starts", "goalCount", "initialFacesMask",
}
_CUT_KEYS = {
    "queryCount", "openableCount", "geometricCount", "ownerCounts",
    "legalMoveRejected", "traverseRejected", "ordinalsSha256",
}
_CASE_KEYS = {
    "case", "owners", "openedOrdinals", "openedEdges", "counterfactual",
    "actualCaptureAttested", "canonicalGraphSha256", "changedBitCount",
    "preserved", "bfs", "minimumVias", "agreement", "work",
}
_BFS_KEYS = {
    "reachable", "reachedStates", "edgesExamined", "goalHits",
    "reachedMaskSha256", "fullCensus",
}
_CASE_WORK_KEYS = {
    "buildEntries", "buildUnits", "bfsPops", "bfsUnits", "codecCalls",
    "oracleCalls", "elapsedNanos",
}
_PRESERVED_KEYS = {
    "dimensions", "protocol", "model", "caseId", "primary", "starts",
    "goals", "vias", "base",
}
_LIMITATIONS = [
    "CONDITIONAL_MASK_OPENINGS_ONLY",
    "NOT_WHOLE_NET_REMOVAL",
    "NOT_UNIQUE_TRACE_OWNERSHIP",
    "NOT_PHYSICAL_ROUTE",
    "NO_PRODUCTION_ADOPTION",
    "NO_NEW_ELECTRICAL_OR_POPULATION_QUALIFICATION",
]


class ReaderError(ValueError):
    """A sanitized reader rejection identified by one static code."""

    def __init__(self, code):
        if type(code) is not str or not re.fullmatch(r"[A-Z0-9_]+", code):
            code = "READER_INVALID"
        self.code = code
        super().__init__(code)


def _reject(code):
    raise ReaderError(code)


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            _reject("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _parse_float(value):
    parsed = float(value)
    if not math.isfinite(parsed):
        _reject("JSON_NONFINITE")
    return parsed


def _parse_constant(_value):
    _reject("JSON_NONFINITE")


def _json_load(raw, *, final_lf=False, max_bytes=LIMITS["artifactBytes"]):
    if type(raw) is not bytes or len(raw) > max_bytes or not raw:
        _reject("JSON_SIZE_INVALID")
    if raw.startswith(b"\xef\xbb\xbf") or (final_lf and
            (not raw.endswith(b"\n") or raw.count(b"\n") != 1)):
        _reject("JSON_FRAMING_INVALID")
    try:
        text = raw.decode("utf-8", "strict")
        return json.loads(text, object_pairs_hook=_pairs_no_duplicates,
                          parse_float=_parse_float, parse_constant=_parse_constant)
    except ReaderError:
        raise
    except Exception:
        _reject("JSON_INVALID")


def _json_bytes(value):
    try:
        return json.dumps(value, ensure_ascii=True, allow_nan=False,
                          separators=(",", ":"), sort_keys=True).encode("ascii")
    except Exception:
        _reject("JSON_VALUE_INVALID")


def _keys(value, expected, code):
    if type(value) is not dict or set(value) != set(expected):
        _reject(code)


def _same_typed(left, right):
    if type(left) is not type(right):
        return False
    if type(left) is dict:
        return (set(left) == set(right) and
                all(_same_typed(left[key], right[key]) for key in left))
    if type(left) in (list, tuple):
        return (len(left) == len(right) and
                all(_same_typed(a, b) for a, b in zip(left, right)))
    return left == right


def _validate_acceptance(value, code):
    expected_keys = {"route", "layout", "electrical", "q100",
                     "productionFeasibility", "newGeneration", "newP07Search"}
    _keys(value, expected_keys, code)
    for key in ("route", "layout", "electrical", "q100", "newGeneration", "newP07Search"):
        if value[key] is not False or type(value[key]) is not bool:
            _reject(code)
    if value["productionFeasibility"] != "UNKNOWN" or type(value["productionFeasibility"]) is not str:
        _reject(code)


def _int(value, code, *, minimum=0, maximum=None):
    if type(value) is not int or value < minimum or (maximum is not None and value > maximum):
        _reject(code)
    return value


def _finite_number(value, code, *, minimum=0, maximum=None):
    if type(value) not in (int, float) or not math.isfinite(value):
        _reject(code)
    if value < minimum or (maximum is not None and value > maximum):
        _reject(code)
    return value


def _positive_tick(value, code):
    return _int(value, code, minimum=1)


def _bool(value, code):
    if type(value) is not bool:
        _reject(code)
    return value


def _str(value, code, *, nonempty=True):
    if type(value) is not str or (nonempty and not value):
        _reject(code)
    return value


def _sha(value, code):
    if type(value) is not str or not _HEX.fullmatch(value):
        _reject(code)
    return value


def _strict_path(value, *, exists=True, directory=None):
    _str(value, "PATH_INVALID")
    path = Path(value)
    if not path.is_absolute() or ".." in path.parts:
        _reject("PATH_INVALID")
    try:
        resolved = path.resolve(strict=exists)
    except Exception:
        _reject("PATH_INVALID")
    if str(resolved) != str(path):
        _reject("PATH_NONCANONICAL")
    if directory is True and (not resolved.is_dir()):
        _reject("PATH_TYPE_INVALID")
    if directory is False and (not resolved.is_file()):
        _reject("PATH_TYPE_INVALID")
    return resolved


def _digest(raw):
    return hashlib.sha256(raw).hexdigest()


def _canonical_spec(raw, document):
    expected = (json.dumps(document, sort_keys=True, ensure_ascii=True,
                           allow_nan=False, separators=(",", ":")) + "\n").encode("ascii")
    if raw != expected:
        _reject("SPEC_CANONICAL_ENCODING")


def _load_helper_and_spec(spec_path, spec_sha):
    spec_path = _strict_path(spec_path, directory=False)
    spec_sha = _sha(spec_sha, "SPEC_SHA_INVALID")
    try:
        raw = spec_path.read_bytes()
    except Exception:
        _reject("SPEC_READ_FAILED")
    if len(raw) > LIMITS["specBytes"] or _digest(raw) != spec_sha:
        _reject("SPEC_HASH_MISMATCH")
    spec = _json_load(raw, final_lf=True, max_bytes=LIMITS["specBytes"])
    if type(spec) is not dict or spec.get("schema") != "tsj-canonical-cut-sensitivity-input-v1":
        _reject("SPEC_SCHEMA_INVALID")
    _canonical_spec(raw, spec)
    expected_helper = Path(__file__).resolve().with_name("run_cut_sensitivity.py")
    worker_path = _strict_path(spec.get("worker"), directory=False)
    if worker_path != expected_helper:
        _reject("WORKER_PATH_MISMATCH")
    worker_raw = worker_path.read_bytes()
    if _digest(worker_raw) != _sha(spec.get("workerSha256"), "WORKER_SHA_INVALID"):
        _reject("WORKER_HASH_MISMATCH")
    module_spec = importlib.util.spec_from_file_location("p7_pinned_worker_helpers", worker_path)
    if module_spec is None or module_spec.loader is None:
        _reject("HELPER_IMPORT_FAILED")
    helper = importlib.util.module_from_spec(module_spec)
    try:
        module_spec.loader.exec_module(helper)
        loaded = helper.load_spec(str(spec_path), spec_sha, full=True)
    except ReaderError:
        raise
    except Exception:
        _reject("SPEC_VALIDATION_FAILED")
    if type(loaded) is not dict or loaded != spec:
        _reject("SPEC_VALIDATION_MISMATCH")
    return helper, spec, spec_path, raw


def _stable(helper, path, limit=LIMITS["artifactBytes"]):
    try:
        raw = helper.stable(str(path), limit=limit)
    except ReaderError:
        raise
    except Exception:
        _reject("STABLE_READ_FAILED")
    if type(raw) is not bytes or len(raw) > limit:
        _reject("STABLE_READ_INVALID")
    return raw


def _snapshot_run_files(helper, root, mode):
    expected_top = {"host-receipt.json", "worker-process.json", "worker.stdout",
                    "worker.stderr", "data"}
    if mode == "ANALYZE":
        expected_top.update({"parent-process.json", "parent.stdout", "parent.stderr"})
    try:
        children = list(root.iterdir())
    except Exception:
        _reject("RUN_ROOT_READ_FAILED")
    names = {child.name for child in children}
    if names != expected_top or len(children) != len(names):
        _reject("RUN_ROOT_CONTENTS_INVALID")
    data = root / "data"
    try:
        data_info = data.lstat()
        data_children = list(data.iterdir())
    except Exception:
        _reject("RUN_DATA_INVALID")
    if (data.is_symlink() or getattr(data_info, "st_file_attributes", 0) & 1024 or
            len(data_children) != 1 or data_children[0].name != "sensitivity-result.json" or
            not data_children[0].is_file() or data_children[0].is_symlink()):
        _reject("RUN_DATA_CONTENTS_INVALID")
    paths = [root / name for name in sorted(expected_top - {"data"})]
    paths.append(data / "sensitivity-result.json")
    result = {}
    total = 0
    for path in paths:
        try:
            link_count = path.stat().st_nlink
        except Exception:
            _reject("RUN_FILE_STAT_FAILED")
        if type(link_count) is not int or link_count != 1:
            _reject("RUN_FILE_LINK_INVALID")
        raw = _stable(helper, path, LIMITS["artifactBytes"])
        total += len(raw)
        if total > LIMITS["artifactBytes"]:
            _reject("RUN_ARTIFACT_AGGREGATE_LIMIT")
        result[path.relative_to(root).as_posix()] = _digest(raw)
    return result


def _read_document(helper, path, limit=LIMITS["artifactBytes"], final_lf=False):
    return _json_load(_stable(helper, path, limit), final_lf=final_lf, max_bytes=limit)


def _dfs(graph):
    width = graph.width
    height = graph.height
    cells = width * height
    state_count = cells * 2
    if (type(width) is not int or type(height) is not int or
            type(graph.steps) is not tuple or len(graph.steps) != state_count or
            type(graph.vias) is not tuple or len(graph.vias) != cells or
            type(graph.goals) is not tuple or len(graph.goals) != state_count or
            type(graph.starts) is not tuple):
        _reject("GRAPH_SHAPE_INVALID")
    reached = bytearray(state_count)
    stack = []
    for start in graph.starts:
        _int(start, "GRAPH_START_INVALID", maximum=state_count - 1)
        if not reached[start]:
            reached[start] = 1
            stack.append(start)
    edges_examined = 0
    goal_hits = 0
    while stack:
        state = stack.pop()
        if graph.goals[state]:
            goal_hits += 1
        layer = state // cells
        cell = state % cells
        x = cell % width
        y = cell // width
        mask = graph.steps[state]
        if type(mask) is not int or mask < 0 or mask > 15:
            _reject("GRAPH_STEP_INVALID")
        for bit, dx, dy in DIRECTIONS:
            if not (mask & bit):
                continue
            nx = x + dx
            ny = y + dy
            if nx < 0 or nx >= width or ny < 0 or ny >= height:
                _reject("GRAPH_EDGE_OUT_OF_BOUNDS")
            edges_examined += 1
            neighbor = layer * cells + ny * width + nx
            if not reached[neighbor]:
                reached[neighbor] = 1
                stack.append(neighbor)
    return {
        "reachable": goal_hits > 0,
        "reachedStates": sum(reached),
        "edgesExamined": edges_examined,
        "goalHits": goal_hits,
        "reachedMaskSha256": _digest(bytes(reached)),
        "fullCensus": True,
    }, reached


def _derive_cut(graph, reached):
    cells = graph.width * graph.height
    cut = []
    for state, is_reached in enumerate(reached):
        if not is_reached:
            continue
        layer = state // cells
        cell = state % cells
        x = cell % graph.width
        y = cell // graph.width
        mask = graph.steps[state]
        for bit, dx, dy in DIRECTIONS:
            nx = x + dx
            ny = y + dy
            if nx < 0 or nx >= graph.width or ny < 0 or ny >= graph.height:
                continue
            neighbor = layer * cells + ny * graph.width + nx
            if not (mask & bit) and not reached[neighbor]:
                cut.append((layer, state, neighbor, bit))
    return cut


def _validate_openings(graph, openings, owner_order):
    if type(openings) is not tuple or len(openings) != 22:
        _reject("OPENING_CENSUS_INVALID")
    if (type(owner_order) is not tuple or len(owner_order) != 2 or
            any(type(owner) is not str or not owner for owner in owner_order) or
            owner_order[0] == owner_order[1]):
        _reject("OWNER_ORDER_INVALID")
    reached, reached_mask = _dfs(graph)
    expected_cut = _derive_cut(graph, reached_mask)
    if len(expected_cut) != len(openings):
        _reject("OPENING_CUT_CENSUS_MISMATCH")
    seen_ordinals = set()
    seen_edges = set()
    owner_counts = {owner: 0 for owner in owner_order}
    normalized = []
    cells = graph.width * graph.height
    for expected_ordinal, (row, edge) in enumerate(zip(openings, expected_cut)):
        if type(row) is not tuple or len(row) != 7:
            _reject("OPENING_ROW_INVALID")
        ordinal, layer, from_state, to_state, bit, owner, selected = row
        for value in (ordinal, layer, from_state, to_state, bit):
            if type(value) is not int:
                _reject("OPENING_TYPE_INVALID")
        if type(selected) is not bool:
            _reject("OPENING_TYPE_INVALID")
        if owner is not None and (type(owner) is not str or not owner):
            _reject("OPENING_OWNER_INVALID")
        if (ordinal in seen_ordinals or ordinal <= (normalized[-1][0] if normalized else -1) or
                (layer, from_state, to_state, bit) in seen_edges):
            _reject("OPENING_DUPLICATE")
        if ordinal != expected_ordinal:
            _reject("OPENING_ORDINAL_MISMATCH")
        seen_ordinals.add(ordinal)
        seen_edges.add((layer, from_state, to_state, bit))
        if (layer, from_state, to_state, bit) != edge:
            _reject("OPENING_CUT_EDGE_MISMATCH")
        if layer not in (0, 1) or from_state < 0 or from_state >= len(graph.steps):
            _reject("OPENING_STATE_INVALID")
        if to_state < 0 or to_state >= len(graph.steps) or to_state // cells != layer:
            _reject("OPENING_STATE_INVALID")
        if bit not in (1, 2, 4, 8) or graph.steps[from_state] & bit:
            _reject("OPENING_BIT_INVALID")
        if selected:
            if owner not in owner_counts:
                _reject("OPENING_OWNER_INVALID")
            owner_counts[owner] += 1
        normalized.append(row)
    if sum(owner_counts.values()) != 8 or sorted(owner_counts.values()) != [4, 4]:
        _reject("OPENING_OWNER_COUNTS_INVALID")
    if sum(not row[6] for row in normalized) != 14:
        _reject("OPENING_GEOMETRIC_COUNTS_INVALID")
    return reached, reached_mask, normalized, owner_counts


def _open_variant(base, rows, owners, codec):
    masks = {}
    opened = []
    for row in rows:
        if row[6] and row[5] in owners:
            state, bit = row[2], row[4]
            if masks.get(state, 0) & bit:
                _reject("OPENING_DUPLICATE")
            masks[state] = masks.get(state, 0) | bit
            opened.append(row)
    if not opened:
        _reject("OPENING_OWNER_SET_EMPTY")
    if sum(mask.bit_count() for mask in masks.values()) != len(opened):
        _reject("OPENING_MASK_INVALID")
    original_steps = base.steps
    steps = [mask | masks.get(index, 0) for index, mask in enumerate(original_steps)]
    vias = list(base.vias)
    goals = list(base.goals)
    starts = list(base.starts)
    if len(steps) + len(vias) + len(goals) + len(starts) > LIMITS["cloneEntries"]:
        _reject("GRAPH_CLONE_LIMIT")
    payload = {
        "protocol": base.protocol,
        "model": base.model,
        "caseId": base.case_id,
        "width": base.width,
        "height": base.height,
        "primary": base.primary,
        "steps": steps,
        "vias": vias,
        "goals": goals,
        "starts": starts,
    }
    try:
        variant = codec.validate_payload(payload)
    except Exception:
        _reject("VARIANT_CODEC_REJECTED")
    if type(variant) is not type(base):
        _reject("VARIANT_TYPE_INVALID")
    for key in ("protocol", "model", "case_id", "width", "height", "primary", "vias", "goals", "starts"):
        if getattr(variant, key) != getattr(base, key):
            _reject("VARIANT_BASE_CHANGED")
    if type(variant.steps) is not tuple or len(variant.steps) != len(base.steps):
        _reject("VARIANT_STEPS_INVALID")
    for index, mask in enumerate(base.steps):
        if type(variant.steps[index]) is not int or variant.steps[index] != (mask | masks.get(index, 0)):
            _reject("VARIANT_STEP_DELTA_INVALID")
    changed = sum((variant.steps[index] ^ base.steps[index]).bit_count()
                  for index in range(len(base.steps)))
    if changed != len(opened) or variant.graph_sha256 == base.graph_sha256:
        _reject("VARIANT_CHANGED_BITS_INVALID")
    return variant, opened, masks


def _minimum_vias(via_oracle, graph):
    method = getattr(via_oracle, "analyze_minimum_vias", None)
    if not callable(method):
        _reject("VIA_ORACLE_API_INVALID")
    try:
        value = method(graph)
    except Exception:
        _reject("VIA_ORACLE_FAILED")
    if type(value) is not dict:
        _reject("VIA_ORACLE_RESULT_INVALID")
    reachable = value.get("reachable")
    minimum = value.get("minimumViaCount")
    graph_sha = value.get("graphSha256")
    if (type(reachable) is not bool or graph_sha != graph.graph_sha256 or
            (minimum is not None and (type(minimum) is not int or minimum < 0)) or
            reachable != (minimum is not None)):
        _reject("VIA_ORACLE_RESULT_INVALID")
    return value


def _work_int(value, code, *, maximum=None):
    return _int(value, code, maximum=maximum)


def _validate_case_work(case, expected_bfs, graph, index):
    value = case.get("work")
    _keys(value, _CASE_WORK_KEYS, "CASE_WORK_SHAPE_INVALID")
    for key in _CASE_WORK_KEYS:
        _work_int(value[key], "CASE_WORK_INVALID")
    cells = graph.width * graph.height
    states = cells * 2
    if index == 0:
        expected_build_entries = expected_build_units = expected_codec = 0
    elif index == 1:
        expected_build_entries = states + cells + states + len(graph.starts)
        expected_build_units = (math.ceil(states / 4096) * 2 +
                                math.ceil(cells / 4096) +
                                math.ceil(len(graph.starts) / 4096))
        expected_codec = 1
    else:
        expected_build_entries = states
        expected_build_units = math.ceil(states / 4096)
        expected_codec = 1
    if (value["buildEntries"] != expected_build_entries or
            value["buildUnits"] != expected_build_units or
            value["bfsPops"] != expected_bfs["reachedStates"] or
            value["bfsUnits"] != math.ceil(expected_bfs["reachedStates"] / 4096) or
            value["codecCalls"] != expected_codec or value["oracleCalls"] != 1 or
            value["elapsedNanos"] <= 0):
        _reject("CASE_WORK_MISMATCH")


def _validate_analysis_result(result, spec, graph, openings, owner_order, context):
    _keys(result, _RESULT_KEYS, "RESULT_SHAPE_INVALID")
    if (result["schema"] != RESULT_SCHEMA or result["status"] != RESULT_STATUS or
            result["mode"] != "ANALYZE" or result["scope"] != SCOPE or
            result["specSha256"] != spec["_sha256"]):
        _reject("RESULT_IDENTITY_MISMATCH")
    _validate_acceptance(result["acceptance"], "RESULT_ACCEPTANCE_INVALID")
    if not _same_typed(result["limitations"], _LIMITATIONS):
        _reject("RESULT_ACCEPTANCE_INVALID")
    source = result["source"]
    _keys(source, _SOURCE_KEYS, "RESULT_SOURCE_SHAPE_INVALID")
    for name in ("analysisModuleSha256", "workerSha256", "testsSha256",
                 "graphOracleSha256", "viaOracleSha256", "captureReaderSha256"):
        _sha(source[name], "RESULT_SOURCE_HASH_INVALID")
        expected = spec.get(name)
        if expected is not None and source[name] != expected:
            _reject("RESULT_SOURCE_HASH_MISMATCH")
    if source["stableBefore"] is not True or source["stableAfter"] is not True:
        _reject("RESULT_SOURCE_STABILITY_INVALID")

    if graph.width != 478 or graph.height != 309:
        _reject("GRAPH_DIMENSIONS_INVALID")
    control_bfs, control_mask = _dfs(graph)
    cut = _derive_cut(graph, control_mask)
    if len(cut) != 22:
        _reject("CONTROL_CUT_COUNT_INVALID")
    normalized = []
    # Reuse validation so the exact ordered descriptors and owner assignments
    # are independently joined to the complete outgoing directed cut.
    _validated_bfs, _validated_mask, normalized, owner_counts = _validate_openings(
        graph, openings, owner_order)
    cases = result["cases"]
    if type(cases) is not list or len(cases) != len(CASES):
        _reject("CASE_CENSUS_INVALID")
    graph_fields = result["graph"]
    _keys(graph_fields, _GRAPH_KEYS, "GRAPH_RESULT_SHAPE_INVALID")
    expected_graph = {
        "rawSha256": RAW_GRAPH_SHA,
        "canonicalSha256": graph.graph_sha256,
        "prefixGeometrySha256": PREFIX_GEOMETRY_SHA,
        "width": graph.width,
        "height": graph.height,
        "cells": graph.width * graph.height,
        "states": graph.width * graph.height * 2,
        "starts": list(graph.starts),
        "goalCount": sum(1 for value in graph.goals if value),
        "initialFacesMask": 3,
    }
    if (not _same_typed(graph_fields, expected_graph) or
            not _same_typed(context.get("graph"), expected_graph)):
        _reject("GRAPH_RESULT_MISMATCH")
    cut_fields = result["cut"]
    _keys(cut_fields, _CUT_KEYS, "CUT_RESULT_SHAPE_INVALID")
    # The stage census is independently tied to the frozen P6 event projection
    # loaded by the strict source helper; its owner/event facts are not inferred
    # from the producer's result.
    accepted_cut = context.get("cut") if type(context) is dict else None
    if type(accepted_cut) is not dict:
        _reject("CUT_CONTEXT_MISSING")
    _keys(accepted_cut, _CUT_KEYS, "CUT_CONTEXT_INVALID")
    expected_cut = {
        "queryCount": 22,
        "openableCount": 8,
        "geometricCount": 14,
        "ownerCounts": owner_counts,
        "legalMoveRejected": 9,
        "traverseRejected": 5,
        "ordinalsSha256": accepted_cut["ordinalsSha256"],
    }
    if not _same_typed(accepted_cut, expected_cut):
        _reject("CUT_CONTEXT_INVALID")
    _sha(accepted_cut["ordinalsSha256"], "CUT_ORDINAL_HASH_INVALID")
    ordinal_digest = _digest(_json_bytes([list(row) for row in normalized]) + b"\n")
    if accepted_cut["ordinalsSha256"] != ordinal_digest:
        _reject("CUT_ORDINAL_HASH_MISMATCH")
    if not _same_typed(cut_fields, expected_cut):
        _reject("CUT_RESULT_MISMATCH")

    via_oracle = context.get("via")
    codec = context.get("codec")
    if via_oracle is None or codec is None:
        _reject("VIA_CONTEXT_MISSING")
    old_minimum = context.get("acceptedMinimumVia")
    if type(old_minimum) is not dict:
        _reject("CONTROL_PARENT_DIAGNOSIS_MISSING")
    if not _same_typed(control_bfs, _validated_bfs):
        _reject("CONTROL_DFS_MISMATCH")
    base_steps_ref = graph.steps
    total_case_counters = {key: 0 for key in _CASE_WORK_KEYS}
    for index, ((name, _owner_indexes), case) in enumerate(zip(CASES, cases)):
        _keys(case, _CASE_KEYS, "CASE_SHAPE_INVALID")
        if case["case"] != name:
            _reject("CASE_ORDER_INVALID")
        owners = tuple(owner_order[owner_index] for owner_index in _owner_indexes)
        if index == 0:
            case_graph = graph
            opened_rows = []
        else:
            case_graph, opened_rows, _masks = _open_variant(graph, normalized, owners, codec)
        expected_bfs, _reached = _dfs(case_graph)
        expected_minimum = _minimum_vias(via_oracle, case_graph)
        agreement = expected_bfs["reachable"] == (expected_minimum["minimumViaCount"] == 0)
        if not agreement:
            _reject("ORACLE_DISAGREEMENT")
        if index == 0 and (
                expected_bfs["reachedStates"] != 12 or expected_bfs["edgesExamined"] != 23 or
                expected_minimum.get("minimumViaCount") is not None or
                expected_minimum.get("termination") != "FRONTIER_EXHAUSTED" or
                expected_minimum.get("reachable") is not False or
                old_minimum.get("minimumViaCount") is not None or
                old_minimum.get("termination") != "FRONTIER_EXHAUSTED" or
                old_minimum.get("reachable") is not False):
            _reject("CONTROL_PARENT_DIAGNOSIS_MISMATCH")
        opened_ordinals = [row[0] for row in opened_rows]
        opened_edges = [
            {"ordinal": row[0], "layer": row[1], "fromState": row[2],
             "toState": row[3], "bit": row[4], "ownerNetId": row[5]}
            for row in opened_rows
        ]
        changed_count = len(opened_rows)
        if changed_count != (0, 4, 4, 8)[index]:
            _reject("CASE_CHANGED_BIT_COUNT_INVALID")
        expected_static = {
            "owners": list(owners),
            "openedOrdinals": opened_ordinals,
            "openedEdges": opened_edges,
            "counterfactual": index != 0,
            "actualCaptureAttested": False,
            "canonicalGraphSha256": case_graph.graph_sha256,
            "changedBitCount": changed_count,
            "preserved": {key: True for key in _PRESERVED_KEYS},
            "bfs": expected_bfs,
            "minimumVias": expected_minimum,
            "agreement": agreement,
        }
        for key, expected in expected_static.items():
            if key == "bfs":
                _keys(case[key], _BFS_KEYS, "CASE_BFS_SHAPE_INVALID")
                _bool(case[key]["reachable"], "CASE_BFS_INVALID")
                for field in ("reachedStates", "edgesExamined", "goalHits"):
                    _int(case[key][field], "CASE_BFS_INVALID")
                _bool(case[key]["fullCensus"], "CASE_BFS_INVALID")
                _sha(case[key]["reachedMaskSha256"], "CASE_BFS_INVALID")
            if not _same_typed(case[key], expected):
                _reject("CASE_RESULT_MISMATCH")
        for key in ("counterfactual", "actualCaptureAttested", "agreement"):
            _bool(case[key], "CASE_RESULT_INVALID")
        _keys(case["preserved"], _PRESERVED_KEYS, "CASE_PRESERVED_SHAPE_INVALID")
        if any(type(value) is not bool or value is not True for value in case["preserved"].values()):
            _reject("CASE_PRESERVED_INVALID")
        _validate_case_work(case, expected_bfs, graph, index)
        for key in total_case_counters:
            total_case_counters[key] += case["work"][key]
        if index != 0:
            del case_graph
            del _masks
        del _reached
        if graph.steps is not base_steps_ref:
            _reject("BASE_GRAPH_MUTATED")

    work = result["work"]
    expected_work_keys = {
        "analysis", "prerequisiteWallMs", "workerElapsedNanos",
        "sourceVerificationNanos", "serializationNanos", "casesRun",
    }
    _keys(work, expected_work_keys, "RESULT_WORK_SHAPE_INVALID")
    if type(work["casesRun"]) is not int or work["casesRun"] != 4:
        _reject("RESULT_CASE_COUNT_INVALID")
    if work["prerequisiteWallMs"] is not None:
        _reject("RESULT_PREREQUISITE_TIME_MUST_BE_HOST_ONLY")
    for key in ("workerElapsedNanos", "sourceVerificationNanos", "serializationNanos"):
        _int(work[key], "RESULT_WORK_INVALID")
    analysis = work["analysis"]
    analysis_keys = {
        "groups", "elapsedNanos", "maximumGroupNanos", "copyEntries", "copyUnits",
        "bfsPops", "bfsUnits", "opaqueCalls", "totalObservationUnits",
        "actualNewP07Steps", "actualNewP07Advances", "actualNewP07Expansions",
    }
    _keys(analysis, analysis_keys, "ANALYSIS_WORK_SHAPE_INVALID")
    for key in analysis_keys:
        _int(analysis[key], "ANALYSIS_WORK_INVALID")
    expected_bfs_pops = total_case_counters["bfsPops"]
    expected_bfs_units = total_case_counters["bfsUnits"]
    expected_copy_entries = total_case_counters["buildEntries"]
    expected_copy_units = total_case_counters["buildUnits"]
    if (analysis["groups"] > 640 or analysis["groups"] < 1 or
            analysis["elapsedNanos"] <= 0 or analysis["elapsedNanos"] > LIMITS["workNanos"] or
            analysis["maximumGroupNanos"] > LIMITS["groupNanos"] or
            analysis["copyEntries"] != expected_copy_entries or
            analysis["copyUnits"] != expected_copy_units or
            analysis["bfsPops"] != expected_bfs_pops or
            analysis["bfsUnits"] != expected_bfs_units or
            analysis["totalObservationUnits"] != analysis["groups"] or
            analysis["opaqueCalls"] + analysis["copyUnits"] + analysis["bfsUnits"] > analysis["groups"] or
            (analysis["actualNewP07Steps"], analysis["actualNewP07Advances"],
             analysis["actualNewP07Expansions"]) != (0, 0, 0) or
            total_case_counters["bfsPops"] != expected_bfs_pops or
            total_case_counters["bfsUnits"] != expected_bfs_units or
            total_case_counters["codecCalls"] != 3 or
            total_case_counters["oracleCalls"] != 4 or
            total_case_counters["elapsedNanos"] > analysis["elapsedNanos"] or
            work["workerElapsedNanos"] != analysis["elapsedNanos"]):
        _reject("ANALYSIS_WORK_MISMATCH")
    return {
        "rawGraphSha256": RAW_GRAPH_SHA,
        "canonicalGraphSha256": graph.graph_sha256,
        "caseCount": 4,
        "cutQueryCount": 22,
        "openableCount": 8,
        "analysisGroups": analysis["groups"],
        "casesRun": 4,
    }


def _check_process_receipt(helper, run_root, filename, stdout_name, stderr_name,
                           expected_args, selected_python, expected_exit=0):
    path = run_root / filename
    raw = _stable(helper, path, 1_048_576)
    process = _json_load(raw)
    fields = {
        "FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
        "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
        "TerminationProven",
    }
    _keys(process, fields, "PROCESS_RECEIPT_SHAPE_INVALID")
    if process["FilePath"] != selected_python or process["Arguments"] != expected_args:
        _reject("PROCESS_ARGUMENTS_MISMATCH")
    _int(process["ProcessId"], "PROCESS_ID_INVALID", minimum=1)
    _positive_tick(process["ProcessStartTicks"], "PROCESS_START_INVALID")
    if (type(process["ExitCode"]) is not int or process["ExitCode"] != expected_exit or
            process["TerminationProven"] is not True):
        _reject("PROCESS_COMPLETION_INVALID")
    _str(process["Stdout"], "PROCESS_STDOUT_INVALID", nonempty=False)
    _str(process["Stderr"], "PROCESS_STDERR_INVALID", nonempty=False)
    if process["Stderr"] != "":
        _reject("PROCESS_STDERR_NONEMPTY")
    _verify_process_log_origins(helper, process)
    stdout_raw = _stable(helper, run_root / stdout_name, LIMITS["lineBytes"])
    stderr_raw = _stable(helper, run_root / stderr_name, LIMITS["lineBytes"])
    try:
        if stdout_raw.decode("utf-8", "strict") != process["Stdout"]:
            _reject("PROCESS_STDOUT_MISMATCH")
        if stderr_raw.decode("utf-8", "strict") != process["Stderr"]:
            _reject("PROCESS_STDERR_MISMATCH")
    except UnicodeError:
        _reject("PROCESS_LOG_ENCODING_INVALID")
    return raw, process, stdout_raw


def _verify_process_log_origins(helper, process):
    """Bind raw verifier paths to their cleaned task-temp process root."""
    root_text = _str(process["ProcessRoot"], "PROCESS_ROOT_INVALID")
    try:
        process_root = Path(helper.absolute(root_text))
        expected_parent = Path(helper.absolute(
            str(Path(tempfile.gettempdir()) / "TroubleshootJS")))
        stdout_path = Path(helper.absolute(process["StdoutLog"]))
        stderr_path = Path(helper.absolute(process["StderrLog"]))
    except Exception:
        _reject("PROCESS_LOG_ORIGIN_INVALID")
    if (os.path.normcase(str(process_root.parent)) != os.path.normcase(str(expected_parent)) or
            re.fullmatch(r"verify-process-[0-9a-f]{32}", process_root.name) is None or
            os.path.lexists(str(process_root))):
        _reject("PROCESS_ROOT_OWNERSHIP_INVALID")
    expected_stdout = process_root / "stdout.log"
    expected_stderr = process_root / "stderr.log"
    if (os.path.normcase(str(stdout_path)) != os.path.normcase(str(expected_stdout)) or
            os.path.normcase(str(stderr_path)) != os.path.normcase(str(expected_stderr))):
        _reject("PROCESS_LOG_PATH_MISMATCH")


def _worker_args(spec_path, spec_sha, mode, data_root, parent_path, parent_sha):
    return ["-B", str(Path(__file__).resolve().with_name("run_cut_sensitivity.py")),
            mode, str(spec_path), spec_sha, str(data_root), parent_path, parent_sha]


def _parse_worker_index(raw, mode, spec_sha):
    if len(raw) > LIMITS["lineBytes"] or not raw.endswith(b"\n") or raw.count(b"\n") != 1:
        _reject("WORKER_INDEX_FRAMING_INVALID")
    marker = b"CANONICAL_CUT_SENSITIVITY_COMPLETE "
    if not raw.startswith(marker):
        _reject("WORKER_INDEX_MARKER_INVALID")
    index = _json_load(raw[len(marker):], final_lf=True, max_bytes=LIMITS["lineBytes"])
    _keys(index, {"schema", "status", "mode", "specSha256", "result",
                  "aggregateBytes", "elapsedNanos", "work"}, "WORKER_INDEX_SHAPE_INVALID")
    if index["schema"] != INDEX_SCHEMA or index["mode"] != mode or index["specSha256"] != spec_sha:
        _reject("WORKER_INDEX_IDENTITY_MISMATCH")
    if index["status"] != ("PASS_LITERAL_HOST_CANARY_ONLY" if mode == "CANARY" else "PASS_CONDITIONAL_CUT_SENSITIVITY_ONLY"):
        _reject("WORKER_INDEX_STATUS_INVALID")
    _int(index["aggregateBytes"], "WORKER_INDEX_SIZE_INVALID", maximum=LIMITS["artifactBytes"])
    _int(index["elapsedNanos"], "WORKER_INDEX_TIME_INVALID", maximum=LIMITS["workNanos"])
    _keys(index["result"], {"path", "bytes", "sha256"}, "WORKER_INDEX_RESULT_INVALID")
    if index["result"]["path"] != "sensitivity-result.json":
        _reject("WORKER_INDEX_RESULT_PATH_INVALID")
    _int(index["result"]["bytes"], "WORKER_INDEX_RESULT_SIZE_INVALID", minimum=1,
         maximum=LIMITS["resultBytes"])
    _sha(index["result"]["sha256"], "WORKER_INDEX_RESULT_SHA_INVALID")
    return index


def _validate_final_index(result, index):
    analysis = result["work"].get("analysis")
    final = index["work"]
    work_keys = {
        "groups", "elapsedNanos", "maximumGroupNanos", "copyEntries", "copyUnits",
        "bfsPops", "bfsUnits", "opaqueCalls", "totalObservationUnits",
        "actualNewP07Steps", "actualNewP07Advances", "actualNewP07Expansions",
    }
    _keys(analysis, work_keys, "RESULT_WORK_SNAPSHOT_INVALID")
    _keys(final, work_keys, "WORKER_FINAL_WORK_INVALID")
    for key in work_keys:
        _int(analysis[key], "RESULT_WORK_INVALID")
        _int(final[key], "WORKER_FINAL_WORK_INVALID")
    if (final["groups"] != analysis["groups"] + 1 or
            final["opaqueCalls"] != analysis["opaqueCalls"] + 1 or
            final["totalObservationUnits"] != final["groups"] or
            final["elapsedNanos"] != index["elapsedNanos"] or
            final["elapsedNanos"] < analysis["elapsedNanos"] or
            final["maximumGroupNanos"] < analysis["maximumGroupNanos"] or
            any(final[key] != analysis[key] for key in
                ("copyEntries", "copyUnits", "bfsPops", "bfsUnits",
                 "actualNewP07Steps", "actualNewP07Advances", "actualNewP07Expansions")) or
            final["groups"] > LIMITS["groups"] or
            final["elapsedNanos"] > LIMITS["workNanos"] or
            final["maximumGroupNanos"] > LIMITS["groupNanos"] or
            (final["actualNewP07Steps"], final["actualNewP07Advances"],
             final["actualNewP07Expansions"]) != (0, 0, 0)):
        _reject("WORKER_FINAL_WORK_MISMATCH")


def _validate_host_receipt(helper, run_root, spec, mode, spec_sha,
                           worker_process, parent_process, index):
    host = _read_document(helper, run_root / "host-receipt.json", 1_048_576)
    host_keys = {
        "schema", "status", "mode", "specSha256", "host", "sourceBefore",
        "sourceAfter", "parent", "worker", "work", "cleanup", "outerWallMs",
    }
    _keys(host, host_keys, "HOST_RECEIPT_SHAPE_INVALID")
    if (host["schema"] != "tsj-canonical-cut-sensitivity-host-v1" or
            host["status"] != "HOST_COMPLETE_READER_PENDING" or host["mode"] != mode or
            host["specSha256"] != spec_sha):
        _reject("HOST_RECEIPT_IDENTITY_MISMATCH")
    host_fields = {"pid", "startTicks", "executable", "script", "scriptSha256"}
    _keys(host["host"], host_fields, "HOST_IDENTITY_SHAPE_INVALID")
    _int(host["host"]["pid"], "HOST_ID_INVALID", minimum=1)
    _positive_tick(host["host"]["startTicks"], "HOST_START_INVALID")
    if host["host"]["executable"] != spec["selectedPowerShell"]:
        _reject("HOST_EXECUTABLE_MISMATCH")
    expected_script = Path(spec["worker"]).resolve().with_name("run-cut-sensitivity.ps1")
    if _strict_path(host["host"]["script"], directory=False) != expected_script:
        _reject("HOST_SCRIPT_MISMATCH")
    expected_script_row = [row for row in spec["files"] if row["path"] == str(expected_script)]
    if len(expected_script_row) != 1 or host["host"]["scriptSha256"] != expected_script_row[0]["sha256"]:
        _reject("HOST_SCRIPT_SHA_MISMATCH")
    for key in ("sourceBefore", "sourceAfter"):
        census = host[key]
        _keys(census, {"status", "sourceRows", "sourceFiles", "extraFiles", "elapsedMs"},
              "HOST_SOURCE_CENSUS_SHAPE_INVALID")
        if (census["status"] != "PASS" or census["sourceRows"] != 1084 or
                census["sourceFiles"] != 2168):
            _reject("HOST_SOURCE_CENSUS_INVALID")
        _int(census["extraFiles"], "HOST_SOURCE_CENSUS_INVALID")
        _finite_number(census["elapsedMs"], "HOST_SOURCE_CENSUS_INVALID")
    if any(host["sourceBefore"][field] != host["sourceAfter"][field]
           for field in ("status", "sourceRows", "sourceFiles", "extraFiles")):
        _reject("HOST_SOURCE_CENSUS_CHANGED")
    if mode == "CANARY":
        if host["parent"] is not None or parent_process is not None:
            _reject("CANARY_PARENT_PRESENT")
    else:
        _keys(host["parent"], {"receipt", "sha256", "wallMs"}, "HOST_PARENT_SHAPE_INVALID")
        if host["parent"]["receipt"] != "parent-process.json" or parent_process is None:
            _reject("HOST_PARENT_RECEIPT_INVALID")
        _sha(host["parent"]["sha256"], "HOST_PARENT_HASH_INVALID")
        _finite_number(host["parent"]["wallMs"], "HOST_PARENT_WALL_INVALID", minimum=0,
                       maximum=LIMITS["parentMs"])
    _keys(host["worker"], {"receipt", "sha256", "wallMs"}, "HOST_WORKER_SHAPE_INVALID")
    if host["worker"]["receipt"] != "worker-process.json":
        _reject("HOST_WORKER_RECEIPT_INVALID")
    _sha(host["worker"]["sha256"], "HOST_WORKER_HASH_INVALID")
    _finite_number(host["worker"]["wallMs"], "HOST_WORKER_WALL_INVALID", minimum=0,
                   maximum=LIMITS["workerMs"])
    work = host["work"]
    _keys(work, {"indexElapsedNanos", "residualUpperNanos", "finalGroups",
                 "maximumGroupNanos", "fullWorkerWallNanos"}, "HOST_WORK_SHAPE_INVALID")
    for key in work:
        _int(work[key], "HOST_WORK_INVALID")
    expected_residual = max(0, work["fullWorkerWallNanos"] - index["elapsedNanos"])
    index_work = index["work"]
    _keys(index_work, {"groups", "elapsedNanos", "maximumGroupNanos", "copyEntries",
                       "copyUnits", "bfsPops", "bfsUnits", "opaqueCalls",
                       "totalObservationUnits", "actualNewP07Steps", "actualNewP07Advances",
                       "actualNewP07Expansions"}, "WORKER_INDEX_WORK_SHAPE_INVALID")
    _int(index_work["groups"], "WORKER_INDEX_WORK_INVALID")
    _int(index_work["elapsedNanos"], "WORKER_INDEX_WORK_INVALID")
    _int(index_work["maximumGroupNanos"], "WORKER_INDEX_WORK_INVALID")
    if (work["indexElapsedNanos"] != index["elapsedNanos"] or
            work["residualUpperNanos"] != expected_residual or
            work["finalGroups"] != index_work["groups"] + 1 or
            work["maximumGroupNanos"] != max(index_work["maximumGroupNanos"], expected_residual) or
            work["fullWorkerWallNanos"] > LIMITS["workNanos"] or
            work["residualUpperNanos"] > LIMITS["groupNanos"] or
            work["finalGroups"] > LIMITS["groups"] or
            work["maximumGroupNanos"] > LIMITS["groupNanos"] or
            abs(work["fullWorkerWallNanos"] - host["worker"]["wallMs"] * 1_000_000) > 1_000_000):
        _reject("HOST_WORK_MISMATCH")
    cleanup = host["cleanup"]
    _keys(cleanup, {"status", "accounting", "conservativeUpperMs", "boundMs",
                    "allProcessesTerminated", "temporaryOutputRetainedAsEvidence"},
          "HOST_CLEANUP_SHAPE_INVALID")
    if (cleanup["status"] != "PASS" or cleanup["accounting"] != "FULL_PARENT_WORKER_POST_SOURCE" or
            cleanup["boundMs"] != LIMITS["cleanupMs"] or
            cleanup["allProcessesTerminated"] is not True or
            cleanup["temporaryOutputRetainedAsEvidence"] is not True):
        _reject("HOST_CLEANUP_INVALID")
    _finite_number(cleanup["conservativeUpperMs"], "HOST_CLEANUP_INVALID",
                   maximum=LIMITS["cleanupMs"])
    _finite_number(host["outerWallMs"], "HOST_OUTER_WALL_INVALID", maximum=LIMITS["outerMs"])
    minimum_cleanup = host["worker"]["wallMs"] + host["sourceAfter"]["elapsedMs"]
    if mode == "ANALYZE":
        minimum_cleanup += host["parent"]["wallMs"]
    if cleanup["conservativeUpperMs"] + 0.001 < minimum_cleanup:
        _reject("HOST_CLEANUP_UNDERCHARGED")
    identities = [(host["host"]["pid"], _positive_tick(host["host"]["startTicks"], "HOST_START_INVALID")),
                  (worker_process["ProcessId"], _positive_tick(
                      worker_process["ProcessStartTicks"], "PROCESS_START_INVALID"))]
    if parent_process is not None:
        identities.append((parent_process["ProcessId"], _positive_tick(
            parent_process["ProcessStartTicks"], "PROCESS_START_INVALID")))
    if len(set(identities)) != len(identities):
        _reject("PROCESS_IDENTITY_REUSED")
    return host


def validate_run(run_root, spec_path, spec_sha, mode):
    """Independently validate one completed P7 worker run."""
    if mode not in ("CANARY", "ANALYZE") or type(mode) is not str:
        _reject("MODE_INVALID")
    helper, spec, spec_path, spec_raw = _load_helper_and_spec(spec_path, spec_sha)
    spec["_sha256"] = _digest(spec_raw)
    root = _strict_path(run_root, directory=True)
    run_snapshot = _snapshot_run_files(helper, root, mode)
    data_root = root / "data"
    data_root = _strict_path(str(data_root), directory=True)
    parent_path = root / "parent-process.json"
    parent_process = None
    parent_raw = None
    parent_sha = "-"
    parent_arg = "-"
    parent_summary = None
    if mode == "ANALYZE":
        parent_raw, parent_process, parent_stdout_raw = _check_process_receipt(
            helper, root, "parent-process.json", "parent.stdout", "parent.stderr",
            ["-B", spec["parentReader"], spec["parentRun"], spec["parentPins"],
             spec["parentPinsSha256"], "OBSERVE"], spec["selectedPython"])
        parent_sha = _digest(parent_raw)
        parent_arg = str(parent_path.resolve(strict=True))
        if _digest(_stable(helper, parent_path, 1_048_576)) != parent_sha:
            _reject("PARENT_RECEIPT_CHANGED")
        # P6's unchanged Windows text stream ends CRLF. Keep the copied/raw
        # receipt bytes exact and normalize only the local JSON parse view.
        if (not parent_stdout_raw.endswith(b"\r\n") or
                parent_stdout_raw.count(b"\r") != 1 or parent_stdout_raw.count(b"\n") != 1):
            _reject("PARENT_STDOUT_FRAMING_INVALID")
        parent_parse = parent_stdout_raw[:-2] + b"\n"
        if _json_load(parent_parse, final_lf=True, max_bytes=LIMITS["lineBytes"]).get(
                "status") != "PASS_STAGE_OBSERVATIONS_ONLY":
            _reject("PARENT_READER_STATUS_INVALID")
        try:
            parent_summary = helper.validate_parent_receipt(
                spec, str(parent_path), parent_sha)
        except Exception:
            _reject("PARENT_PROOF_INVALID")
    worker_raw, worker_process, worker_stdout = _check_process_receipt(
        helper, root, "worker-process.json", "worker.stdout", "worker.stderr",
        _worker_args(spec_path, spec_sha, mode, data_root, parent_arg, parent_sha),
        spec["selectedPython"])
    index = _parse_worker_index(worker_stdout, mode, spec_sha)
    result_path = data_root / "sensitivity-result.json"
    result_raw = _stable(helper, result_path, LIMITS["resultBytes"])
    if (_digest(result_raw) != index["result"]["sha256"] or
            len(result_raw) != index["result"]["bytes"] or
            index["aggregateBytes"] != len(result_raw)):
        _reject("RESULT_FILE_BINDING_MISMATCH")
    result = _json_load(result_raw, final_lf=True, max_bytes=LIMITS["resultBytes"])
    host = _validate_host_receipt(helper, root, spec, mode, spec_sha,
                                  worker_process, parent_process, index)
    if _digest(worker_raw) != host["worker"]["sha256"]:
        _reject("WORKER_RECEIPT_HASH_MISMATCH")
    if mode == "ANALYZE" and _digest(parent_raw) != host["parent"]["sha256"]:
        _reject("PARENT_RECEIPT_HASH_MISMATCH")
    _validate_final_index(result, index)
    if mode == "CANARY":
        summary = _validate_canary(result, spec, spec_sha, index, helper)
        parent_run_id = None
    else:
        try:
            graph, openings, context = helper.load_inputs(spec, budget=None)
        except Exception:
            _reject("INPUT_GRAPH_LOAD_FAILED")
        owner_order = ("B_SENSE", "B_REF")
        expected_parent = parent_summary
        _validate_parent_summary(result, spec, expected_parent, parent_raw)
        if (type(context) is not dict or type(context.get("index")) is not dict or
                context["index"].get("runId") != result["parent"].get("runId")):
            _reject("PARENT_CAPTURE_RUN_MISMATCH")
        summary = _validate_analysis_result(
            result, spec, graph, openings, owner_order, context)
        parent_run_id = result["parent"].get("runId")
    try:
        verified_spec = helper.load_spec(str(spec_path), spec_sha, full=True)
    except Exception:
        _reject("SOURCE_POSTCHECK_FAILED")
    if verified_spec != {key: value for key, value in spec.items() if key != "_sha256"}:
        _reject("SOURCE_POSTCHECK_MISMATCH")
    if _snapshot_run_files(helper, root, mode) != run_snapshot:
        _reject("RUN_FILES_CHANGED")
    return {
        "schema": READER_SCHEMA,
        "status": READER_CANARY_STATUS if mode == "CANARY" else READER_ANALYZE_STATUS,
        "mode": mode,
        "specSha256": spec_sha,
        "resultSha256": _digest(result_raw),
        "workerIndexSha256": _digest(worker_stdout),
        "parentRunId": parent_run_id,
        "summary": summary,
        "acceptance": {"route": False, "layout": False, "electrical": False, "q100": False},
    }


def _validate_parent_summary(result, spec, parent_summary, parent_raw):
    parent = result["parent"]
    _keys(parent, {"qualificationSha256", "inputPinsSha256", "readerSha256",
                   "runId", "receiptSha256", "status"}, "PARENT_RESULT_SHAPE_INVALID")
    if (parent["qualificationSha256"] != P6_QUALIFICATION_SHA or
            parent["inputPinsSha256"] != P6_INPUT_SHA or
            parent["readerSha256"] != P6_READER_SHA or
            parent["receiptSha256"] != _digest(parent_raw) or
            parent["status"] != "PASS_STAGE_OBSERVATIONS_ONLY"):
        _reject("PARENT_RESULT_MISMATCH")
    if type(parent_summary) is not dict:
        _reject("PARENT_PROOF_INVALID")
    for key in ("qualificationSha256", "inputPinsSha256", "readerSha256", "runId", "status"):
        if key in parent_summary and not _same_typed(parent[key], parent_summary[key]):
            _reject("PARENT_RESULT_MISMATCH")
    if type(parent["runId"]) is not str or not parent["runId"]:
        _reject("PARENT_RUN_ID_INVALID")


def _validate_canary(result, spec, spec_sha, index, helper):
    _keys(result, _RESULT_KEYS, "CANARY_RESULT_SHAPE_INVALID")
    if (result["schema"] != RESULT_SCHEMA or result["status"] != CANARY_STATUS or
            result["mode"] != "CANARY" or result["specSha256"] != spec_sha or
            result["scope"] != SCOPE or result["parent"] is not None or
            result["graph"] is not None or result["cut"] is not None or
            type(result["cases"]) is not list or result["cases"] != []):
        _reject("CANARY_RESULT_INVALID")
    _validate_acceptance(result["acceptance"], "CANARY_ACCEPTANCE_INVALID")
    if not _same_typed(result["limitations"], _LIMITATIONS):
        _reject("CANARY_ACCEPTANCE_INVALID")
    if type(result["source"]) is not dict:
        _reject("CANARY_SOURCE_INVALID")
    _keys(result["source"], _SOURCE_KEYS, "CANARY_SOURCE_SHAPE_INVALID")
    for key in ("analysisModuleSha256", "workerSha256", "testsSha256",
                "graphOracleSha256", "viaOracleSha256", "captureReaderSha256"):
        _sha(result["source"][key], "CANARY_SOURCE_HASH_INVALID")
        if spec.get(key) is not None and result["source"][key] != spec[key]:
            _reject("CANARY_SOURCE_HASH_MISMATCH")
    if result["source"]["stableBefore"] is not True or result["source"]["stableAfter"] is not True:
        _reject("CANARY_SOURCE_STABILITY_INVALID")
    if index["status"] != CANARY_STATUS:
        _reject("CANARY_INDEX_STATUS_INVALID")
    work = result["work"]
    _keys(work, {"analysis", "prerequisiteWallMs", "workerElapsedNanos",
                 "sourceVerificationNanos", "serializationNanos", "casesRun",
                 "canaryAssertions"}, "CANARY_WORK_SHAPE_INVALID")
    if (type(work["casesRun"]) is not int or work["casesRun"] != 0 or
            work["prerequisiteWallMs"] is not None or
            work["workerElapsedNanos"] != work["analysis"].get("elapsedNanos")):
        _reject("CANARY_WORK_INVALID")
    for key in ("workerElapsedNanos", "sourceVerificationNanos", "serializationNanos"):
        _int(work[key], "CANARY_WORK_INVALID")
    if _int(work["canaryAssertions"], "CANARY_ASSERTION_COUNT_INVALID", minimum=1) != 2:
        _reject("CANARY_ASSERTION_COUNT_INVALID")
    oracle_dir = Path(spec["graphOracle"]).parent
    if not oracle_dir.is_dir():
        _reject("CANARY_ORACLE_DIRECTORY_INVALID")
    added = str(oracle_dir)
    sys.path.insert(0, added)
    try:
        codec = helper.module(spec["graphOracle"], spec["graphOracleSha256"],
                              "read_fixed_prefix_graph")
        via = helper.module(spec["viaOracle"], spec["viaOracleSha256"],
                            "fixed_prefix_minimum_vias")
        base = codec.validate_payload({
            "protocol": codec.GRAPH_PROTOCOL, "model": codec.GRAPH_MODEL,
            "caseId": "P7_LITERAL_CANARY", "width": 2, "height": 2,
            "primary": 0, "steps": [0] * 8, "vias": [False] * 4,
            "goals": [False, True] + [False] * 6, "starts": [0],
        })
        rows = ((0, 0, 0, 1, 2, "A", True), (1, 0, 0, 2, 4, "B", True))
        owner_order = ("A", "B")
        expected_reachable = []
        expected_changed = []
        graphs = [base]
        for _name, owner_indexes in CASES[1:]:
            owners = tuple(owner_order[owner_index] for owner_index in owner_indexes)
            variant, opened, _masks = _open_variant(base, rows, owners, codec)
            graphs.append(variant)
            expected_changed.append(len(opened))
        expected_changed.insert(0, 0)
        for graph in graphs:
            bfs, _mask = _dfs(graph)
            minimum = _minimum_vias(via, graph)
            if bfs["reachable"] != (minimum["minimumViaCount"] == 0):
                _reject("CANARY_ORACLE_DISAGREEMENT")
            expected_reachable.append(bfs["reachable"])
        if (expected_reachable != [False, True, False, True] or
                expected_changed != [0, 1, 1, 2] or base.steps != (0,) * 8):
            _reject("CANARY_LITERAL_EXPECTATION")
    except ReaderError:
        raise
    except Exception:
        _reject("CANARY_LITERAL_INVALID")
    finally:
        try:
            sys.path.remove(added)
        except ValueError:
            pass
    index_work = index["work"]
    _keys(index_work, {"groups", "elapsedNanos", "maximumGroupNanos", "copyEntries",
                       "copyUnits", "bfsPops", "bfsUnits", "opaqueCalls",
                       "totalObservationUnits", "actualNewP07Steps", "actualNewP07Advances",
                       "actualNewP07Expansions"}, "CANARY_INDEX_WORK_SHAPE_INVALID")
    _keys(work["analysis"], {
        "groups", "elapsedNanos", "maximumGroupNanos", "copyEntries", "copyUnits",
        "bfsPops", "bfsUnits", "opaqueCalls", "totalObservationUnits",
        "actualNewP07Steps", "actualNewP07Advances", "actualNewP07Expansions",
    }, "CANARY_ANALYSIS_WORK_SHAPE_INVALID")
    analysis = work["analysis"]
    for key in analysis:
        _int(analysis[key], "CANARY_ANALYSIS_WORK_INVALID")
    if (analysis["copyEntries"] != 37 or analysis["copyUnits"] != 6 or
            analysis["bfsPops"] != 8 or analysis["bfsUnits"] != 4 or
            analysis["groups"] < analysis["copyUnits"] + analysis["bfsUnits"] + analysis["opaqueCalls"] or
            analysis["totalObservationUnits"] != analysis["groups"] or
            analysis["elapsedNanos"] <= 0 or analysis["elapsedNanos"] > LIMITS["workNanos"] or
            analysis["maximumGroupNanos"] > LIMITS["groupNanos"] or
            analysis["groups"] > LIMITS["groups"] or
            (analysis["actualNewP07Steps"], analysis["actualNewP07Advances"],
             analysis["actualNewP07Expansions"]) != (0, 0, 0) or
            index_work["groups"] > LIMITS["groups"] or
            index_work["elapsedNanos"] > LIMITS["workNanos"] or
            index_work["maximumGroupNanos"] > LIMITS["groupNanos"] or
            index_work["totalObservationUnits"] != index_work["groups"] or
            (index_work["actualNewP07Steps"], index_work["actualNewP07Advances"],
             index_work["actualNewP07Expansions"]) != (0, 0, 0)):
        _reject("CANARY_INDEX_WORK_INVALID")
    return {"casesRun": 0, "canaryAssertions": work["canaryAssertions"]}


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    if len(argv) != 4:
        sys.stderr.write("CLI_ARGUMENTS_INVALID\n")
        return 2
    try:
        result = validate_run(argv[0], argv[1], argv[2], argv[3])
        sys.stdout.write(json.dumps(result, ensure_ascii=True, allow_nan=False,
                                    separators=(",", ":"), sort_keys=True) + "\n")
        return 0
    except ReaderError as error:
        sys.stderr.write(error.code + "\n")
        return 2
    except Exception:
        sys.stderr.write("READER_REJECTED\n")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
