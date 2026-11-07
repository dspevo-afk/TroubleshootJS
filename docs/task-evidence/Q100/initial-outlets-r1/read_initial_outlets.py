"""Strict reader for the bounded initial planar-outlet diagnostic."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import types


ROOT = Path(__file__).resolve().parent
PLAN_SHA256 = "d78724b8b26f93b0dc935aa863a50f8fd1efa20b3faeb3401dcd7bea27ead0d2"
WIRE_SHA256 = "97279dcd9de3a915977eb0c0d22fa0a8d58f18541cf41a15222a7498872f4224"
WIRE_RELATIVE = "source/WIRE_R1.md"
WIRE_SCHEMA = "tsj-initial-outlet-v1"
MARKER_SCHEMA = "tsj-initial-outlet-marker-v1"
MANIFEST_SCHEMA = "tsj-initial-outlet-source-v1"
BUILD_INPUTS_SHA256 = "87951c6c464fe4a39ae0b908017a7e857036e543c26b5332ee683839440c4d76"
BUILD_INPUTS_RELATIVE = "build_inputs.py"
ENTRY_CLASS = "com.lushprojects.circuitjs1.client.InitialOutletStudy"
PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"
SEEDS = ("9007199254740993", "-6587958367974952701", "8445301674744508279")
PREFIX_SHA256 = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
ROLE_WIDTH = {"HIGH_CURRENT": 13, "SUPPLY": 11, "RETURN": 11, "CONTROL": 9, "SIGNAL": 9}
FACE_ORDER = ("TOP", "BOTTOM")
DIRECTION_ORDER = ("N", "E", "S", "W")
DIRECTION_BITS = {"N": 1, "E": 2, "S": 4, "W": 8}
DIRECTION_DELTA = {"N": (0, -10), "E": (10, 0), "S": (0, 10), "W": (-10, 0)}
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
HEX = re.compile(r"[0-9a-f]{64}\Z")
MAX_JSON = 8 * 1024 * 1024
MAX_RECEIPT = 8 * 1024 * 1024
MAX_LINE = 1_048_576
MAX_COPY_ENTRIES = 1_048_576
MAX_COORD = 1_000_000
MAX_TIME_NS = 120_000_000_000


class ReaderError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("IO_") else "IO_" + code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)


def is_int(value, low=0, high=2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high


def is_digest(value) -> bool:
    return type(value) is str and HEX.fullmatch(value) is not None


def exact_keys(value, keys, code):
    need(type(value) is dict and set(value) == set(keys), code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def compact_json(value) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False,
                      separators=(",", ":"), sort_keys=True).encode("ascii")


def _count_json_nodes(value) -> int:
    if type(value) is dict:
        return sum(1 + _count_json_nodes(item) for item in value.values())
    if type(value) is list:
        return sum(1 + _count_json_nodes(item) for item in value)
    return 1


def _validate_pre_export_units(doc: dict, work: dict) -> int:
    count = _count_json_nodes(doc)
    need(count <= MAX_COPY_ENTRIES and work["preExportUnits"] == count,
         "PREEXPORT_TREE_UNITS")
    return count


def _finite_tree(value) -> bool:
    if type(value) is float:
        return math.isfinite(value)
    if type(value) is dict:
        return all(_finite_tree(key) and _finite_tree(item) for key, item in value.items())
    if type(value) is list:
        return all(_finite_tree(item) for item in value)
    return True


def _strict_json_line(raw: bytes, limit: int, code: str):
    need(type(raw) is bytes and 0 < len(raw) <= min(limit, MAX_LINE) and raw.endswith(b"\n")
         and b"\r" not in raw and b"\xef\xbb\xbf" not in raw, code)
    body = raw[:-1]
    need(b"\n" not in body and b"\r" not in body, code)

    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, code)
            result[key] = value
        return result

    def reject_constant(_):
        raise ReaderError(code)

    try:
        text = body.decode("utf-8", errors="strict")
        value = json.loads(text, object_pairs_hook=pairs, parse_constant=reject_constant)
    except ReaderError:
        raise
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError):
        raise ReaderError(code) from None
    need(type(value) is dict and _finite_tree(value), code)
    in_string = False
    escaped = False
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        else:
            need(not char.isspace(), code)
    need(not in_string, code)
    return value


def _load_build_inputs():
    path = ROOT / BUILD_INPUTS_RELATIVE
    try:
        st = path.lstat()
        need(path.is_file() and not path.is_symlink()
             and not (getattr(st, "st_file_attributes", 0) & 0x400),
             "BUILD_INPUTS_FILE")
        raw = path.read_bytes()
    except ReaderError:
        raise
    except OSError:
        raise ReaderError("BUILD_INPUTS_READ") from None
    need(is_digest(BUILD_INPUTS_SHA256) and sha(raw) == BUILD_INPUTS_SHA256,
         "BUILD_INPUTS_HASH")
    module = types.ModuleType("_authenticated_initial_outlet_build_inputs")
    module.__file__ = str(path)
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except Exception:
        raise ReaderError("BUILD_INPUTS_LOAD") from None
    need(callable(getattr(module, "validate_run_manifest", None)), "BUILD_INPUTS_API")
    return module


def validate_manifest(path: str | Path, expected_sha: str) -> dict:
    need(is_digest(expected_sha), "MANIFEST_HASH_FORMAT")
    manifest_path = Path(path)
    need(manifest_path.is_absolute(), "MANIFEST_PATH_ABSOLUTE")
    builder = _load_build_inputs()
    try:
        context = builder.validate_run_manifest(str(manifest_path), expected_sha)
    except ReaderError:
        raise
    except Exception as exc:
        helper_code = getattr(exc, "code", None)
        if type(helper_code) is str and helper_code.startswith("IO_"):
            raise ReaderError(helper_code) from None
        raise ReaderError("MANIFEST_VALIDATION") from None
    wire_raw = context.get("wireRaw") if type(context) is dict else None
    wire_path = ROOT / WIRE_RELATIVE
    try:
        wire_on_disk = wire_path.read_bytes()
    except OSError:
        raise ReaderError("WIRE_READ") from None
    need(type(context) is dict and context.get("sourceSha") == expected_sha
         and type(wire_raw) is bytes and wire_raw == wire_on_disk
         and sha(wire_raw) == WIRE_SHA256
         and type(context.get("baseReader")) is types.ModuleType
         and type(context.get("p07Reader")) is types.ModuleType,
         "MANIFEST_CONTEXT")
    return {"builder": builder, **context}


def _validate_pad_table(pad_lands, p07_pads):
    need(type(pad_lands) is list and type(p07_pads) is list
         and len(pad_lands) == len(p07_pads) == 246, "PAD_TABLE_COUNT")
    p07_by_id = {}
    roles = {}
    for row in p07_pads:
        need(type(row) is dict and type(row.get("padId")) is str
             and type(row.get("netId")) is str and is_int(row.get("x"), -MAX_COORD, MAX_COORD)
             and is_int(row.get("y"), -MAX_COORD, MAX_COORD)
             and row.get("role") in ROLE_WIDTH, "P07_PAD_FIELDS")
        need(row["padId"] not in p07_by_id, "P07_PAD_DUPLICATE")
        p07_by_id[row["padId"]] = row
        prior = roles.setdefault(row["netId"], row.get("role"))
        need(type(row.get("role")) is str and prior == row["role"], "P07_NET_ROLE")
    need(len(roles) == 60, "P07_NET_COUNT")

    by_id = {}
    previous = None
    for row in pad_lands:
        exact_keys(row, ("padId", "netId", "x", "y", "bounds", "attachment", "layer"),
                   "PAD_LAND_SCHEMA")
        need(type(row["padId"]) is str and row["padId"]
             and (previous is None or previous < row["padId"]), "PAD_LAND_ORDER")
        previous = row["padId"]
        need(type(row["netId"]) is str and row["netId"]
             and is_int(row["x"], -MAX_COORD, MAX_COORD)
             and is_int(row["y"], -MAX_COORD, MAX_COORD)
             and row["attachment"] == "PLATED_THROUGH_HOLE"
             and row["layer"] == "TOP+BOTTOM", "PAD_LAND_FIELDS")
        bounds = row["bounds"]
        need(type(bounds) is list and len(bounds) == 4
             and all(is_int(v, -MAX_COORD, MAX_COORD) for v in bounds), "PAD_BOUNDS")
        x, y, width, height = bounds
        need(width > 0 and height > 0 and -MAX_COORD <= x + width <= MAX_COORD
             and -MAX_COORD <= y + height <= MAX_COORD
             and x <= row["x"] <= x + width and y <= row["y"] <= y + height,
             "PAD_BOUNDS")
        source = p07_by_id.get(row["padId"])
        need(source is not None and source["netId"] == row["netId"]
             and source["x"] == row["x"] and source["y"] == row["y"],
             "PAD_LAND_P07_BINDING")
        by_id[row["padId"]] = row
    need(set(by_id) == set(p07_by_id), "PAD_LAND_CENSUS")
    return by_id, roles


def _expected_pairs(p07_pads, branches):
    need(type(p07_pads) is list and type(branches) is list, "PAIR_INPUT_TYPE")
    by_net = {}
    by_pad = {}
    for row in p07_pads:
        need(type(row) is dict and type(row.get("padId")) is str
             and type(row.get("netId")) is str and is_int(row.get("x"), -MAX_COORD, MAX_COORD)
             and is_int(row.get("y"), -MAX_COORD, MAX_COORD), "PAIR_PAD_FIELDS")
        need(row["padId"] not in by_pad, "PAIR_PAD_DUPLICATE")
        by_pad[row["padId"]] = row
        by_net.setdefault(row["netId"], []).append(row)
    for rows in by_net.values():
        rows.sort(key=lambda item: item["padId"])

    first_by_net = {}
    for global_ordinal, branch in enumerate(branches):
        need(type(branch) is dict and is_int(branch.get("ordinal"), global_ordinal, global_ordinal)
             and type(branch.get("snapshot")) is dict, "PAIR_BRANCH_FIELDS")
        snapshot = branch["snapshot"]
        pass_number = snapshot.get("pass")
        if pass_number == 2:
            net_id = snapshot.get("netId")
            need(type(net_id) is str and net_id in by_net, "PAIR_BRANCH_NET")
            first_by_net.setdefault(net_id, branch)

    result = {}
    for net_id in sorted(by_net):
        pads = by_net[net_id]
        pad_ids = [row["padId"] for row in pads]
        if len(pads) == 1:
            need(net_id not in first_by_net, "PAIR_SINGLETON_BRANCH")
            result[net_id] = {"padIds": pad_ids, "paired": False,
                              "rootPadId": None, "startPadId": None, "branch": None}
            continue
        root = pads[0]
        remaining = pads[1:]
        # PcbNetRouter.chooseNext uses strict '<', preserving the sorted first pad on ties.
        start = min(remaining, key=lambda row: (abs(row["x"] - root["x"])
                                                + abs(row["y"] - root["y"]), row["padId"]))
        branch = first_by_net.get(net_id)
        if branch is not None:
            snapshot = branch["snapshot"]
            need(snapshot.get("pass") == 2 and snapshot.get("netId") == net_id
                 and snapshot.get("rootPadId") == root["padId"]
                 and snapshot.get("startPadId") == start["padId"], "PAIR_FIRST_BRANCH_BINDING")
        result[net_id] = {"padIds": pad_ids, "paired": True,
                          "rootPadId": root["padId"], "startPadId": start["padId"],
                          "branch": branch}
    need(set(first_by_net).issubset({net for net, item in result.items() if item["paired"]}),
         "PAIR_BRANCH_COVERAGE")
    return result


def _validate_orientation(value, code):
    exact_keys(value, ("topMask", "bottomMask", "topCount", "bottomCount", "totalCount"), code)
    for key in ("topMask", "bottomMask"):
        need(is_int(value[key], 0, 15), code)
    top = value["topMask"].bit_count()
    bottom = value["bottomMask"].bit_count()
    need(is_int(value["topCount"], 0, 4) and value["topCount"] == top
         and is_int(value["bottomCount"], 0, 4) and value["bottomCount"] == bottom
         and is_int(value["totalCount"], 0, 8)
         and value["totalCount"] == top + bottom, code)
    return top + bottom


def validate_measurement(measurement, p07_pads, branches, candidate, role_by_net,
                         detached_geometry_sha, detached_geometry_after_sha):
    exact_keys(measurement, ("policy", "pass", "widthProfile", "grid", "primaryFace",
                             "oppositeFace", "faceOrder", "directionOrder", "directionBits",
                             "queriesPerPair", "queryCount", "singletonNetCount", "pairedNetCount",
                             "rows", "distribution", "sourceGeometryBeforeSha256",
                             "sourceGeometryAfterSha256", "coldReplayMatches"), "MEASUREMENT_SCHEMA")
    need(measurement["policy"] == "FULLER_TWO_LAYER" and is_int(measurement["pass"], 2, 2)
         and measurement["widthProfile"] == "ROLE_WIDTHS" and is_int(measurement["grid"], 10, 10)
         and measurement["faceOrder"] == list(FACE_ORDER)
         and measurement["directionOrder"] == list(DIRECTION_ORDER)
         and is_int(measurement["queriesPerPair"], 16, 16), "MEASUREMENT_POLICY")
    exact_keys(measurement["directionBits"], DIRECTION_ORDER, "DIRECTION_BITS_SCHEMA")
    need(all(is_int(measurement["directionBits"][direction], DIRECTION_BITS[direction],
                    DIRECTION_BITS[direction]) for direction in DIRECTION_ORDER),
         "DIRECTION_BITS")
    need(measurement["primaryFace"] in FACE_ORDER
         and measurement["oppositeFace"] in FACE_ORDER
         and measurement["primaryFace"] != measurement["oppositeFace"], "MEASUREMENT_FACE")
    need(type(candidate) is dict and candidate.get("traceWidthProfile") == "ROLE_WIDTHS",
         "MEASUREMENT_CANDIDATE")
    need(is_digest(measurement["sourceGeometryBeforeSha256"])
         and is_digest(measurement["sourceGeometryAfterSha256"])
         and measurement["sourceGeometryBeforeSha256"] == detached_geometry_sha
         and measurement["sourceGeometryAfterSha256"] == detached_geometry_after_sha
         and measurement["coldReplayMatches"] is None
         and detached_geometry_sha == detached_geometry_after_sha,
         "MEASUREMENT_SOURCE_GEOMETRY")

    expected = _expected_pairs(p07_pads, branches)
    rows = measurement["rows"]
    need(type(rows) is list and len(rows) == len(expected), "MEASUREMENT_ROW_COUNT")
    net_ids = sorted(expected)
    values = []
    safe_rows = []
    singletons = paired = 0
    for index, (row, net_id) in enumerate(zip(rows, net_ids)):
        need(type(row) is dict and row.get("netId") == net_id, "MEASUREMENT_ROW_ORDER")
        need(row.get("padIds") == expected[net_id]["padIds"], "MEASUREMENT_PAD_IDS")
        if expected[net_id]["paired"]:
            exact_keys(row, ("netId", "padIds", "paired", "rootPadId", "startPadId", "role",
                             "traceWidth",
                             "forward", "reverse", "value"), "MEASUREMENT_PAIR_ROW")
            need(row["paired"] is True and row["rootPadId"] == expected[net_id]["rootPadId"]
                 and row["startPadId"] == expected[net_id]["startPadId"]
                 and row["role"] == role_by_net[net_id]
                 and is_int(row["traceWidth"], ROLE_WIDTH[role_by_net[net_id]],
                            ROLE_WIDTH[role_by_net[net_id]]),
                 "MEASUREMENT_PAIR_IDENTITY")
            forward = _validate_orientation(row["forward"], "MEASUREMENT_FORWARD")
            reverse = _validate_orientation(row["reverse"], "MEASUREMENT_REVERSE")
            value = min(forward, reverse)
            need(is_int(row["value"], 0, 8) and row["value"] == value, "MEASUREMENT_VALUE")
            values.append(value)
            paired += 1
            safe_rows.append({"netId": net_id, "padIds": expected[net_id]["padIds"],
                              "role": row["role"], "traceWidth": row["traceWidth"],
                              "forward": row["forward"], "reverse": row["reverse"],
                              "value": value})
        else:
            exact_keys(row, ("netId", "padIds", "paired", "rootPadId", "startPadId", "role",
                             "traceWidth",
                             "forward", "reverse", "value"), "MEASUREMENT_SINGLETON_ROW")
            need(row["paired"] is False and row["rootPadId"] is None
                 and row["startPadId"] is None and row["forward"] is None
                 and row["reverse"] is None and row["value"] is None
                 and row["role"] == role_by_net[net_id]
                 and is_int(row["traceWidth"], ROLE_WIDTH[role_by_net[net_id]],
                            ROLE_WIDTH[role_by_net[net_id]]),
                 "MEASUREMENT_SINGLETON_FIELDS")
            singletons += 1
            safe_rows.append({"netId": net_id, "padIds": expected[net_id]["padIds"],
                              "role": row["role"], "traceWidth": row["traceWidth"],
                              "paired": False})

    query_count = paired * 16
    need(is_int(measurement["queryCount"], 0, 1_000_000)
         and measurement["queryCount"] == query_count
         and is_int(measurement["singletonNetCount"], 0, 60)
         and measurement["singletonNetCount"] == singletons
         and is_int(measurement["pairedNetCount"], 0, 60)
         and measurement["pairedNetCount"] == paired, "MEASUREMENT_COUNTS")
    distribution = measurement["distribution"]
    exact_keys(distribution, ("pairedValueHistogram", "distinctValueCount", "varies"),
               "DISTRIBUTION_SCHEMA")
    histogram = distribution["pairedValueHistogram"]
    expected_hist_keys = tuple(str(value) for value in range(9))
    exact_keys(histogram, expected_hist_keys, "DISTRIBUTION_HISTOGRAM_SCHEMA")
    actual_hist = {str(value): values.count(value) for value in range(9)}
    need(all(is_int(histogram[key], 0, paired) for key in expected_hist_keys)
         and histogram == actual_hist, "DISTRIBUTION_HISTOGRAM")
    distinct = sum(1 for count in actual_hist.values() if count)
    need(is_int(distribution["distinctValueCount"], 0, 9)
         and distribution["distinctValueCount"] == distinct
         and type(distribution["varies"]) is bool
         and distribution["varies"] is (distinct > 1), "DISTRIBUTION_SUMMARY")
    safe_rows_raw = compact_json(safe_rows)
    return {"netCount": len(expected), "singletonNetCount": singletons,
            "pairedNetCount": paired, "queryCount": query_count,
            "pairedValueHistogram": actual_hist, "distinctValueCount": distinct,
            "varies": distinct > 1, "rowsSha256": sha(safe_rows_raw),
            "expectedPairs": expected}


def _derive_original_units(operation, parsed_op1, reference):
    groups = parsed_op1.get("groups")
    need(type(groups) is list and len(groups) > 0, "ORIGINAL_GROUPS")
    p07_steps = diagnostic_units = 0
    max_time = max_units = 0
    callback_group = reference["groupOrdinal"]
    seen = set()
    for ordinal, row in enumerate(groups):
        need(type(row) is dict and is_int(row.get("ordinal"), ordinal, ordinal),
             "ORIGINAL_GROUP_ORDINAL")
        p07 = row.get("p07Steps")
        need(p07 is None or is_int(p07, 0, 32_768), "ORIGINAL_P07_STEPS")
        diagnostic = row.get("diagnosticUnits")
        need(is_int(diagnostic, 0, 32_768), "ORIGINAL_DIAGNOSTIC_UNITS")
        group_units = (p07 or 0) + diagnostic
        if ordinal == callback_group:
            group_units += operation["callbackUnits"]
            seen.add(ordinal)
        elapsed = row.get("elapsedBeforeOwnLineNanos")
        need(is_int(elapsed, 0, 5_000_000_000), "ORIGINAL_GROUP_TIME")
        max_time = max(max_time, elapsed)
        max_units = max(max_units, group_units)
        p07_steps += p07 or 0
        diagnostic_units += diagnostic
    need(seen == {callback_group}, "ORIGINAL_CALLBACK_GROUP")
    need(operation["groupCount"] == len(groups)
         and operation["groupP07StepsSum"] == p07_steps
         and operation["p07OwnerSteps"] == p07_steps == parsed_op1["p07OwnerSteps"]
         and operation["diagnosticUnits"] == diagnostic_units
         and operation["maximumUnitsPerGroup"] == max_units
         and operation["maxFullGroupNanos"] >= max_time,
         "ORIGINAL_GROUP_ACCOUNTING")
    return p07_steps, diagnostic_units, operation["callbackUnits"], max_time, max_units


def _validate_work(work, operation, p07_steps, diagnostic_units, callback_units,
                   original_max_units, metric_summary):
    exact_keys(work, ("cutoff", "elapsedNanos", "units", "groups", "maxGroupNanos",
                      "maximumUnitsPerGroup", "originalP07OwnerSteps", "originalDiagnosticUnits",
                      "originalCallbackUnits", "metricUnits", "preExportUnits", "metricGroups",
                      "preExportGroups", "queryCount"), "WORK_SCHEMA")
    for name in ("elapsedNanos", "units", "groups", "maxGroupNanos", "maximumUnitsPerGroup",
                 "originalP07OwnerSteps", "originalDiagnosticUnits", "originalCallbackUnits",
                 "metricUnits", "preExportUnits", "metricGroups", "preExportGroups", "queryCount"):
        need(is_int(work[name], 0, 2**63 - 1), "WORK_INTEGER")
    need(work["cutoff"] == "PRE_RESULT_SERIALIZATION"
         and 0 < work["elapsedNanos"] <= 90_000_000_000
         and work["groups"] <= 640 and work["maxGroupNanos"] <= 5_000_000_000
         and work["maximumUnitsPerGroup"] <= 32_768
         and work["originalP07OwnerSteps"] == p07_steps
         and work["originalDiagnosticUnits"] == diagnostic_units
         and work["originalCallbackUnits"] == callback_units
         and work["units"] == p07_steps + diagnostic_units + callback_units
                            + work["metricUnits"] + work["preExportUnits"]
         and work["metricGroups"] == metric_summary["netCount"] + 6
         and work["preExportGroups"] == 1
         and work["groups"] == operation["groupCount"] + work["metricGroups"]
                              + work["preExportGroups"]
         and work["units"] <= 32_768 * work["groups"]
         and work["metricUnits"] >= metric_summary["queryCount"]
         and work["metricUnits"] <= 32_768 * work["metricGroups"]
         and work["preExportUnits"] <= 32_768 * work["preExportGroups"]
         and work["maxGroupNanos"] >= operation["maxFullGroupNanos"]
         and work["maximumUnitsPerGroup"] >= original_max_units
         and work["queryCount"] == metric_summary["queryCount"], "WORK_ACCOUNTING")
    return work


def _validate_cleanup(cleanup, operation):
    exact_keys(cleanup, ("originalOwnersReleased", "contextRestored", "querySearchReleased",
                         "querySessionReleased", "queryLayoutReleased", "queryRoutersReleased",
                         "sourceTemplateReleased", "sourceBoardReleased", "allReferencesReleased",
                         "originalCleanupNanos", "queryCleanupNanos", "sourceReleaseNanos",
                         "combinedCleanupNanos"), "CLEANUP_SCHEMA")
    flags = ("originalOwnersReleased", "contextRestored", "querySearchReleased", "querySessionReleased",
             "queryLayoutReleased", "queryRoutersReleased", "sourceTemplateReleased",
             "sourceBoardReleased", "allReferencesReleased")
    need(all(cleanup[name] is True for name in flags)
         and is_int(cleanup["originalCleanupNanos"], 0, MAX_TIME_NS)
         and is_int(cleanup["queryCleanupNanos"], 0, MAX_TIME_NS)
         and is_int(cleanup["sourceReleaseNanos"], 0, MAX_TIME_NS)
         and is_int(cleanup["combinedCleanupNanos"], 0, MAX_TIME_NS)
         and cleanup["originalCleanupNanos"] == operation["cleanupNanos"]
         and cleanup["combinedCleanupNanos"] == cleanup["originalCleanupNanos"]
                                                  + cleanup["queryCleanupNanos"]
                                                  + cleanup["sourceReleaseNanos"],
         "CLEANUP_PROOF")
    return cleanup


def validate_semantics(doc: dict, parsed_op1: dict, op1_raw: bytes,
                       manifest_sha: str, wire_sha: str, run_id: str) -> dict:
    """Pure result validator reusable for targeted mutations of an actual result."""
    exact_keys(doc, ("schema", "mode", "runId", "finalRunManifestSha256", "wireContractSha256",
                     "operation1", "reference", "source", "measurement", "work", "cleanup"),
               "RESULT_SCHEMA")
    need(doc["schema"] == WIRE_SCHEMA and doc["mode"] == "OBSERVE" and doc["runId"] == run_id
         and doc["finalRunManifestSha256"] == manifest_sha and doc["wireContractSha256"] == wire_sha,
         "RESULT_IDENTITY")
    operation = doc["operation1"]
    exact_keys(operation, ("operation", "mode", "runId", "sourceManifestSha256", "wireSha256",
                           "receiptPath", "receiptBytes", "receiptSha256", "elapsedToCloseNanos",
                           "maxFullGroupNanos", "cleanupNanos", "ownersReleased", "contextRestored",
                           "groupCount", "p07OwnerSteps", "groupP07StepsSum", "diagnosticUnits",
                           "callbackUnits",
                           "maximumUnitsPerGroup", "requestIndex", "selectedAttempt", "receiptAttempt",
                           "rootSeed", "placementSeed", "routingSeed"), "OP1_SCHEMA")
    need(is_int(operation["operation"], 1, 1) and operation["mode"] == "AUTHORED"
         and operation["runId"] == run_id and operation["sourceManifestSha256"] == manifest_sha
         and operation["wireSha256"] == wire_sha and operation["receiptPath"] == "op-1.jsonl"
         and is_int(operation["receiptBytes"], 1, MAX_RECEIPT)
         and operation["receiptBytes"] == len(op1_raw) and operation["receiptSha256"] == sha(op1_raw)
         and is_int(operation["elapsedToCloseNanos"], 1, 90_000_000_000)
         and is_int(operation["maxFullGroupNanos"], 1, 5_000_000_000)
         and is_int(operation["cleanupNanos"], 0, MAX_TIME_NS)
         and operation["ownersReleased"] is True and operation["contextRestored"] is True
         and is_int(operation["groupCount"], 1, 640)
         and is_int(operation["p07OwnerSteps"], 0, 32_768 * 640)
         and is_int(operation["groupP07StepsSum"], 0, 32_768 * 640)
         and is_int(operation["diagnosticUnits"], 0, 32_768 * 640)
         and is_int(operation["callbackUnits"], 0, 32_768)
         and is_int(operation["maximumUnitsPerGroup"], 0, 32_768)
         and is_int(operation["requestIndex"], 0, 0)
         and is_int(operation["selectedAttempt"], 4, 4)
         and is_int(operation["receiptAttempt"], 4, 4)
         and (operation["rootSeed"], operation["placementSeed"], operation["routingSeed"]) == SEEDS,
         "OP1_BINDING")
    need(type(parsed_op1) is dict and type(parsed_op1.get("candidate")) is dict
         and parsed_op1["candidate"].get("traceWidthProfile") == "ROLE_WIDTHS"
         and parsed_op1["candidate"].get("componentCount") == 100
         and parsed_op1["candidate"].get("padCount") == 246
         and parsed_op1["candidate"].get("netCount") == 60,
         "P07_CANDIDATE_BINDING")

    reference = doc["reference"]
    exact_keys(reference, ("receiptOrdinal", "groupOrdinal", "lineageOrdinal", "pass", "netId",
                           "startPadId", "rootPadId", "terminalReason", "prefixGeometrySha256",
                           "prefixGeometryBytes", "prefixHoleCount", "prefixViaCount", "reachedPadIds",
                           "reachedPthPadIds", "reachedPadCount", "reachedPthPadCount"),
               "REFERENCE_SCHEMA")
    branch = parsed_op1["selectedBranch"]
    snapshot = parsed_op1["snapshot"]
    need(is_int(reference["receiptOrdinal"], 307, 307)
         and reference["receiptOrdinal"] == branch["ordinal"] == 307
         and is_int(reference["groupOrdinal"], 0, 639)
         and reference["groupOrdinal"] == branch["group"]
         and is_int(reference["lineageOrdinal"], 77, 77)
         and reference["lineageOrdinal"] == parsed_op1["lineageOrdinal"]
         and is_int(reference["pass"], 2, 2) and reference["pass"] == snapshot["pass"] == 2
         and reference["netId"] == snapshot["netId"] == "A_COIL_LOW"
         and reference["startPadId"] == snapshot["startPadId"] == "QA.C"
         and reference["rootPadId"] == snapshot["rootPadId"] == "DA.A"
         and reference["terminalReason"] == snapshot["terminalReason"] == "NO_PATH"
         and reference["prefixGeometrySha256"] == PREFIX_SHA256 == branch["prefixSha256"]
         and is_int(reference["prefixGeometryBytes"], 1, 1024 * 1024)
         and reference["prefixGeometryBytes"] == branch["prefixBytes"]
         and is_int(reference["prefixHoleCount"], 48, 48)
         and is_int(reference["prefixViaCount"], 48, 48)
         and reference["reachedPadIds"] == ["DA.A"]
         and reference["reachedPthPadIds"] == []
         and is_int(reference["reachedPadCount"], 1, 1)
         and is_int(reference["reachedPthPadCount"], 0, 0), "REFERENCE_BINDING")
    p07_steps, diagnostic_units, callback_units, original_max_time, original_max_units = \
        _derive_original_units(operation, parsed_op1, reference)

    source = doc["source"]
    exact_keys(source, ("componentCount", "netCount", "templateGeometryBeforeSha256",
                        "templateGeometryAfterSha256", "templateEffectsBeforeSha256",
                        "templateEffectsAfterSha256", "detachedGeometrySha256",
                        "detachedGeometryAfterSha256", "templateTraceCount", "templateHoleCount",
                        "queryRouterEffectsBeforeSha256", "queryRouterEffectsAfterSha256",
                        "sourceUnchanged", "padLands"), "SOURCE_SCHEMA")
    need(is_int(source["componentCount"], 100, 100) and is_int(source["netCount"], 60, 60)
         and all(is_digest(source[name]) for name in
                 ("templateGeometryBeforeSha256", "templateGeometryAfterSha256",
                  "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
                  "detachedGeometrySha256", "detachedGeometryAfterSha256",
                  "queryRouterEffectsBeforeSha256", "queryRouterEffectsAfterSha256"))
         and source["templateGeometryBeforeSha256"] == source["templateGeometryAfterSha256"]
         and source["templateEffectsBeforeSha256"] == source["templateEffectsAfterSha256"]
         and source["detachedGeometrySha256"] == source["detachedGeometryAfterSha256"]
         and is_int(source["templateTraceCount"], 0, 0)
         and is_int(source["templateHoleCount"], 0, 0)
         and source["sourceUnchanged"] is True, "SOURCE_IMMUTABILITY")
    pad_by_id, role_by_net = _validate_pad_table(source["padLands"], parsed_op1["pads"])
    metric_summary = validate_measurement(doc["measurement"], parsed_op1["pads"],
                                          parsed_op1["branches"], parsed_op1["candidate"],
                                          role_by_net, source["detachedGeometrySha256"],
                                          source["detachedGeometryAfterSha256"])
    need(metric_summary["netCount"] == source["netCount"]
         and metric_summary["pairedNetCount"] + metric_summary["singletonNetCount"]
         == source["netCount"], "SOURCE_NET_CENSUS")

    work = _validate_work(doc["work"], operation, p07_steps, diagnostic_units,
                          callback_units, original_max_units, metric_summary)
    _validate_pre_export_units(doc, work)
    cleanup = _validate_cleanup(doc["cleanup"], operation)
    return {"operation1": operation, "reference": reference, "source": source,
            "measurement": doc["measurement"], "measurementSummary": metric_summary,
            "work": work, "cleanup": cleanup,
            "originalMaxGroupNanos": original_max_time,
            "originalMaxUnitsPerGroup": original_max_units,
            "roleByNet": role_by_net, "padById": pad_by_id}


def _validate_marker(marker, result_raw: bytes, work, parent_elapsed_ms, cleanup_nanos):
    exact_keys(marker, ("schema", "status", "resultSha256", "resultBytes", "exportElapsedNanos",
                        "exportUnits", "exportGroupCount", "exportMaxGroupNanos",
                        "exportMaximumUnitsPerGroup", "nativeElapsedNanos"), "MARKER_SCHEMA")
    need(marker["schema"] == MARKER_SCHEMA and marker["status"] == "COMPLETED_DIAGNOSTIC"
         and marker["resultSha256"] == sha(result_raw)
         and is_int(marker["resultBytes"], 1, MAX_JSON) and marker["resultBytes"] == len(result_raw)
         and is_int(marker["exportElapsedNanos"], 1, 5_000_000_000)
         and is_int(marker["exportUnits"], 0, 32_768)
         and is_int(marker["exportGroupCount"], 1, 1)
         and is_int(marker["exportMaxGroupNanos"], 1, 5_000_000_000)
         and is_int(marker["exportMaximumUnitsPerGroup"], 1, 32_768)
         and marker["exportMaxGroupNanos"] == marker["exportElapsedNanos"]
         and marker["exportMaximumUnitsPerGroup"] == marker["exportUnits"]
         and marker["exportUnits"] == (len(result_raw) + 4095) // 4096 + 3
         and is_int(marker["nativeElapsedNanos"], 1, 90_000_000_000)
         and marker["nativeElapsedNanos"] >= work["elapsedNanos"]
                                  + marker["exportElapsedNanos"]
                                  + cleanup_nanos
         and work["groups"] + marker["exportGroupCount"] <= 640
         and parent_elapsed_ms * 1_000_000 >= marker["nativeElapsedNanos"], "MARKER_BINDING")


def _public_result(validated, manifest_sha, result_sha, op1_sha, marker):
    summary = validated["measurementSummary"]
    work = validated["work"]
    cleanup = validated["cleanup"]
    return {
        "schema": "tsj-initial-outlet-reader-v1",
        "status": "PASS_INITIAL_OUTLET_DISTRIBUTION_DIAGNOSTIC_ONLY",
        "scope": "AUTHORED_INITIAL_FIRST_BRANCH_PLANAR_OUTLET_COUNTS_ONLY",
        "boardNetCount": summary["netCount"],
        "singletonNetCount": summary["singletonNetCount"],
        "pairedNetCount": summary["pairedNetCount"],
        "queriesPerPair": 16,
        "queryCount": summary["queryCount"],
        "pairedValueHistogram": summary["pairedValueHistogram"],
        "distinctValueCount": summary["distinctValueCount"],
        "varies": summary["varies"],
        "metricsSha256": summary["rowsSha256"],
        "work": {"elapsedNanos": work["elapsedNanos"], "units": work["units"],
                 "groups": work["groups"], "maxGroupNanos": work["maxGroupNanos"],
                 "maximumUnitsPerGroup": work["maximumUnitsPerGroup"],
                 "metricUnits": work["metricUnits"], "metricGroups": work["metricGroups"],
                 "preExportUnits": work["preExportUnits"],
                 "preExportGroups": work["preExportGroups"],
                 "exportElapsedNanos": marker["exportElapsedNanos"],
                 "exportUnits": marker["exportUnits"]},
        "cleanup": {"originalCleanupNanos": cleanup["originalCleanupNanos"],
                    "queryCleanupNanos": cleanup["queryCleanupNanos"],
                    "sourceReleaseNanos": cleanup["sourceReleaseNanos"],
                    "combinedCleanupNanos": cleanup["combinedCleanupNanos"],
                    "allReferencesReleased": cleanup["allReferencesReleased"],
                    "contextRestored": cleanup["contextRestored"]},
        "evidence": {"manifestSha256": manifest_sha, "wireSha256": WIRE_SHA256,
                     "operation1Sha256": op1_sha, "resultSha256": result_sha},
        "routingTreatmentSelected": False,
        "routePublished": False,
        "feasibilityClaimed": False,
        "layoutQualification": False,
        "productionAdopted": False,
    }


def validate_run(manifest_path: str | Path, manifest_sha: str,
                 parent_path: str | Path, data_path: str | Path) -> dict:
    context = validate_manifest(manifest_path, manifest_sha)
    base = context["baseReader"]
    need(type(base) is types.ModuleType, "BASE_READER_MODULE")
    parent_file = Path(parent_path)
    data_dir = Path(data_path)
    need(parent_file.is_absolute() and data_dir.is_absolute(), "OUTPUT_PATH_ABSOLUTE")
    parent_file = base._canonical(parent_file, "PARENT_PATH")
    data_dir = base._canonical(data_dir, "DATA_PATH")
    base._inside(parent_file, ROOT, "PARENT_ROOT")
    base._inside(data_dir, ROOT, "DATA_ROOT")
    need(parent_file.name == "parent.json" and data_dir.name.startswith("native-data-"),
         "OUTPUT_NAMES")
    call_dir = parent_file.parent
    base._lstat_directory(call_dir, "CALL_DIRECTORY")
    base._lstat_directory(data_dir, "DATA_DIRECTORY")
    base._inside(call_dir, ROOT, "CALL_ROOT")
    need(data_dir != call_dir, "OUTPUT_SEPARATION")
    try:
        call_entries = list(call_dir.iterdir())
        call_names = sorted(item.name for item in call_entries)
    except OSError:
        raise ReaderError("CALL_TREE_READ") from None
    need(call_names == ["owner.json", "parent.json", "process.json", "request.json",
                        "result.json", "stderr.txt", "stdout.txt"], "CALL_TREE")
    try:
        data_names = sorted(item.name for item in data_dir.iterdir())
    except OSError:
        raise ReaderError("DATA_TREE_READ") from None
    need(data_names == ["op-1.jsonl", "result.json"], "DATA_TREE")

    parent_raw = base._read(parent_file, 256 * 1024, "PARENT_READ")
    parent = base.host_document(parent_raw, 256 * 1024)
    exact_keys(parent, ("schema", "elapsedMs", "process"), "PARENT_SCHEMA")
    process = parent["process"]
    process_args = process.get("Arguments") if type(process) is dict else None
    need(type(process_args) is list and len(process_args) == 9
         and process_args[0] == "-ea" and process_args[1] == "-cp"
         and process_args[3] == ENTRY_CLASS and process_args[4] == "OBSERVE"
         and type(process_args[5]) is str and UUID.fullmatch(process_args[5]) is not None,
         "NATIVE_ARGUMENTS")
    run_id = process_args[5]
    need(process_args[6] == manifest_sha and process_args[7] == WIRE_SHA256,
         "NATIVE_ARGUMENT_BINDING")
    jars = context["jars"]
    need(type(jars) is dict and type(jars.get("gwt-user-2.7.0.jar")) is dict
         and type(jars.get("gwt-dev-2.7.0.jar")) is dict, "NATIVE_JAR_CONTEXT")
    gwt_user = jars["gwt-user-2.7.0.jar"]["path"]
    gwt_dev = jars["gwt-dev-2.7.0.jar"]["path"]
    expected_cp = ";".join((str(context["classesRoot"]), gwt_user, gwt_dev))
    need(process_args[2] == expected_cp, "NATIVE_CLASSPATH")
    expected_args = ["-ea", "-cp", expected_cp, ENTRY_CLASS, "OBSERVE", run_id,
                     manifest_sha, WIRE_SHA256, str(data_dir)]
    need(process_args[8] == str(data_dir), "NATIVE_OUTPUT_ARGUMENT")
    request = base.host_document(base._read(parent_file.parent / "request.json", 64 * 1024,
                                            "REQUEST_READ"), 64 * 1024)
    request_result = base._validate_owner_call(parent_file.parent, parent_file, parent, request,
        process, context["java"]["path"], expected_args, 480_000,
        context["processModule"])
    marker_raw = process["Stdout"].encode("utf-8", errors="strict")
    marker = base.strict_console_line(marker_raw, "NATIVE_MARKER")
    op1_raw = base._read(data_dir / "op-1.jsonl", MAX_RECEIPT, "OP1_READ")
    result_raw = base._read(data_dir / "result.json", MAX_JSON, "RESULT_READ")
    need(sha(result_raw) == marker.get("resultSha256"), "RESULT_MARKER_HASH")
    doc = _strict_json_line(result_raw, MAX_JSON, "RESULT_FRAMING")
    need(doc.get("operation1") is not None, "OP1_RESULT_MISSING")
    try:
        parsed_op1 = base._parse_operation1(op1_raw, doc["operation1"], run_id,
                                            manifest_sha, WIRE_SHA256, context["p07Reader"])
    except Exception:
        raise ReaderError("OP1_P07_VALIDATION") from None
    validated = validate_semantics(doc, parsed_op1, op1_raw, manifest_sha, WIRE_SHA256, run_id)
    _validate_marker(marker, result_raw, validated["work"], parent["elapsedMs"],
                     validated["cleanup"]["combinedCleanupNanos"])
    recheck = getattr(context["builder"], "recheck_inputs", None)
    need(callable(recheck), "FINAL_INPUT_RECHECK_API")
    try:
        recheck(context)
    except Exception:
        raise ReaderError("FINAL_INPUT_RECHECK") from None
    return {"validated": validated, "context": context, "parent": parent,
            "marker": marker, "resultSha256": sha(result_raw), "op1Sha256": sha(op1_raw),
            "resultBytes": len(result_raw), "receiptBytes": len(op1_raw),
            "processResult": request_result}


def public_result(result, manifest_sha):
    return _public_result(result["validated"], manifest_sha, result["resultSha256"],
                          result["op1Sha256"], result["marker"])


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="read_initial_outlets.py")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--manifest-sha", required=True)
    parser.add_argument("--parent", required=True)
    parser.add_argument("--data", required=True)
    args = parser.parse_args(argv)
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        print("IO_ISOLATION_REQUIRED", file=sys.stderr)
        return 2
    try:
        validated = validate_run(args.manifest, args.manifest_sha, args.parent, args.data)
        output = public_result(validated, args.manifest_sha)
        sys.stdout.write(json.dumps(output, ensure_ascii=True, allow_nan=False,
                                    separators=(",", ":")) + "\n")
        return 0
    except ReaderError as exc:
        print(exc.code, file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError, TypeError, OverflowError, RuntimeError, StopIteration):
        print("IO_INTERNAL_VALIDATION", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
