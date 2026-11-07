"""Strict independent reader for the bounded observed-failure cohort study."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import types


ROOT = Path(__file__).resolve().parent
PLAN_SHA256 = "e2cef26d301af9a8350cd39b2f5bc9d5a296c0840d5327578404c68a2896b04b"
# Deliberately fail closed until the native source, helper, wire and run manifest
# are frozen together by the root integrator.
WIRE_SHA256 = "c7bf55d15f8166e7d8c767abd3f068ee88087b6a9132f9331af7d21c5c202362"
WIRE_RELATIVE = "source/WIRE_R1.md"
NATIVE_SOURCE_SHA256 = "dc53245d52c7187868b40456b3952cdb2ecf20bc920b2354ff400f60ff68410d"
FIXTURE_SOURCE_SHA256 = "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619"
BUILD_INPUTS_SHA256 = "cb8ae8eee78ca90293925b90eec1d8b0ed545668a7dd3a29e4ab169f39542ae2"
BUILD_INPUTS_RELATIVE = "build_inputs.py"
RESULT_SCHEMA = "tsj-failure-cohort-routing-v1"
MARKER_SCHEMA = "tsj-failure-cohort-marker-v1"
PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"
ENTRY_CLASS = "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy"
ROOT_SEED = "9007199254740993"
PLACEMENT_SEED = "-6587958367974952701"
ROUTING_SEED = "8445301674744508279"
PREFIX_SHA256 = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"
ROLE_WIDTHS = {"HIGH_CURRENT": 13, "SUPPLY": 11, "RETURN": 11, "CONTROL": 9, "SIGNAL": 9}
ROLE_PRIORITIES = {"RETURN": 0, "SUPPLY": 1, "HIGH_CURRENT": 2,
                   "CONTROL": 3, "SIGNAL": 4}
BLOCKED_CANARY_ROLES = {
    "NET_A": "HIGH_CURRENT", "NET_B": "CONTROL",
    "BLOCK_E_NET": "SIGNAL", "BLOCK_N_NET": "SIGNAL",
    "BLOCK_S_NET": "SIGNAL", "BLOCK_W_NET": "SIGNAL",
}
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SHA_RE = re.compile(r"[0-9a-f]{64}\Z")
MAX_JSON_BYTES = 8 * 1024 * 1024
MAX_JSON_LINE = 1024 * 1024
MAX_RECEIPT_BYTES = 8 * 1024 * 1024
MAX_GROUPS = 640
MAX_GROUP_NS = 5_000_000_000
MAX_GROUP_UNITS = 32_768
MAX_TOTAL_NS = 90_000_000_000
MAX_CLEANUP_NS = 120_000_000_000
MAX_EXPANSIONS = 1_000_000
MAX_VIAS = 48
MAX_TRANSITIONS = 4
MAX_COPY_ENTRIES = 1_048_576
COPY_CHUNK = 4096


class ReaderError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("BF_") else "BF_" + code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)


def exact_keys(value, keys, code: str) -> None:
    need(type(value) is dict and set(value) == set(keys), code)


def is_int(value, low: int = 0, high: int = 2**63 - 1) -> bool:
    return type(value) is int and low <= value <= high


def is_digest(value) -> bool:
    return type(value) is str and SHA_RE.fullmatch(value) is not None


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(_value):
    raise ValueError("nonfinite JSON value")


def _finite_tree(value) -> bool:
    if type(value) is float:
        return math.isfinite(value)
    if type(value) is dict:
        return all(type(key) is str and _finite_tree(item) for key, item in value.items())
    if type(value) is list:
        return all(_finite_tree(item) for item in value)
    return value is None or type(value) in (str, int, bool)


def _result_tree_minimum_units(root) -> int:
    stack = [(root, 0)]
    units = 0
    visited = 0

    def utf8_chunks(value):
        try:
            byte_count = len(value.encode("utf-8", errors="strict"))
        except UnicodeEncodeError:
            raise ReaderError("RESULT_TREE_UTF8") from None
        return (byte_count + COPY_CHUNK - 1) // COPY_CHUNK

    while stack:
        value, depth = stack.pop()
        need(depth <= 64, "RESULT_TREE_DEPTH")
        visited += 1
        need(visited <= 1_000_000, "RESULT_TREE_SIZE")
        units += 1
        if type(value) is dict:
            for key, child in value.items():
                need(type(key) is str, "RESULT_TREE_KEY")
                visited += 1
                need(visited <= 1_000_000, "RESULT_TREE_SIZE")
                units += 1 + utf8_chunks(key)
                stack.append((child, depth + 1))
        elif type(value) is list:
            stack.extend((child, depth + 1) for child in value)
        elif type(value) is str:
            units += utf8_chunks(value)
    return units


def strict_json_line(raw: bytes, *, limit: int = MAX_JSON_LINE,
                     code: str = "RESULT_FRAMING") -> dict:
    need(type(raw) is bytes and 2 <= len(raw) <= limit and raw.endswith(b"\n")
         and b"\r" not in raw and not raw.startswith(b"\xef\xbb\xbf"), code)
    body = raw[:-1]
    need(body and b"\n" not in body and b"\r" not in body, code)
    try:
        text = body.decode("utf-8", errors="strict")
        value = json.loads(text, object_pairs_hook=_pairs, parse_constant=_reject_constant)
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError):
        raise ReaderError(code) from None
    need(type(value) is dict and _finite_tree(value), code)
    inside = escaped = False
    for char in text:
        if inside:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                inside = False
        elif char == '"':
            inside = True
        else:
            need(not char.isspace(), code)
    need(not inside and text.startswith("{") and text.endswith("}"), code)
    return value


def _read_regular(path: Path, limit: int, code: str) -> bytes:
    try:
        info = path.lstat()
        okay = path.is_file() and not path.is_symlink() \
            and not (getattr(info, "st_file_attributes", 0) & 0x400)
        need(okay, code)
        with path.open("rb") as stream:
            raw = stream.read(limit + 1)
    except ReaderError:
        raise
    except OSError:
        raise ReaderError(code) from None
    need(0 < len(raw) <= limit, code)
    return raw


def _load_builder():
    path = ROOT / BUILD_INPUTS_RELATIVE
    raw = _read_regular(path, 4 * 1024 * 1024, "BUILD_INPUTS_READ")
    need(sha(raw) == BUILD_INPUTS_SHA256, "BUILD_INPUTS_HASH")
    module = types.ModuleType("_authenticated_failed_first_build_inputs")
    module.__file__ = str(path)
    try:
        exec(compile(raw, str(path), "exec"), module.__dict__)
    except Exception:
        raise ReaderError("BUILD_INPUTS_LOAD") from None
    need(callable(getattr(module, "validate_run_manifest", None))
         and callable(getattr(module, "recheck_inputs", None)), "BUILD_INPUTS_API")
    return module


def validate_manifest(path_value: str | Path, expected_sha: str) -> dict:
    need(is_digest(expected_sha), "MANIFEST_SHA_FORMAT")
    path = Path(path_value)
    need(path.is_absolute(), "MANIFEST_PATH")
    builder = _load_builder()
    try:
        context = builder.validate_run_manifest(str(path), expected_sha)
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str:
            raise ReaderError(code if code.startswith("BF_") else "MANIFEST_" + code) from None
        raise ReaderError("MANIFEST_VALIDATION") from None
    wire_path = ROOT / WIRE_RELATIVE
    wire_disk = _read_regular(wire_path, 64 * 1024, "WIRE_READ")
    wire = context.get("wire") if type(context) is dict else None
    manifest = context.get("manifest") if type(context) is dict else None
    source_rows = context.get("sources") if type(context) is dict else None
    source_paths = context.get("sourcePaths") if type(context) is dict else None
    if type(source_rows) is list and type(source_paths) is list:
        source_names = [Path(row["path"]).name for row in source_rows
                        if type(row) is dict and type(row.get("path")) is str]
        native_rows = [row for row in source_rows
                       if type(row) is dict and type(row.get("path")) is str
                       and Path(row["path"]).name == "FailureCohortRoutingStudy.java"]
        fixture_rows = [row for row in source_rows
                        if type(row) is dict and type(row.get("path")) is str
                        and Path(row["path"]).name == "FailedFirstFixtures.java"]
    else:
        source_names, native_rows, fixture_rows = [], [], []
    need(type(context) is dict and context.get("sourceSha") == expected_sha
         and type(context.get("wireRaw")) is bytes and context["wireRaw"] == wire_disk
         and sha(wire_disk) == WIRE_SHA256
         and type(wire) is dict and wire.get("sha256") == WIRE_SHA256
         and wire.get("bytes") == len(wire_disk)
         and type(manifest) is dict and manifest.get("planSha256") == PLAN_SHA256
         and manifest.get("sourceParent") == "474a8354ceb2e50fa2adbb31e23cf73a0d9c86fd"
         and type(source_rows) is list and len(source_rows) == 1090
         and type(source_paths) is list and len(source_paths) == len(source_rows)
         and source_paths == [row.get("path") for row in source_rows
                              if type(row) is dict]
         and manifest.get("sources") == source_rows
         and len(source_names) == len(source_rows)
         and len(native_rows) == 1 and native_rows[0].get("sha256") == NATIVE_SOURCE_SHA256
         and is_int(native_rows[0].get("bytes"), 1, 4 * 1024 * 1024)
         and len(fixture_rows) == 1 and fixture_rows[0].get("sha256") == FIXTURE_SOURCE_SHA256
         and is_int(fixture_rows[0].get("bytes"), 1, 4 * 1024 * 1024)
         and type(context.get("baseReader")) is types.ModuleType
         and type(context.get("p07Reader")) is types.ModuleType,
         "MANIFEST_CONTEXT")
    return {"builder": builder, **context}


def stable_cohort_order(net_ids, cohort_ids) -> list[str]:
    """Independently derive a stable requested order from observed net IDs."""
    need(type(net_ids) is list and bool(net_ids) and type(cohort_ids) is list
         and bool(cohort_ids), "ORDER_INPUT")
    need(all(type(net) is str and bool(net) for net in net_ids)
         and all(type(net) is str and bool(net) for net in cohort_ids), "ORDER_INPUT")
    seen = set()
    for net in net_ids:
        need(net not in seen, "ORDER_DUPLICATE")
        seen.add(net)
    cohort_seen = set()
    for net in cohort_ids:
        need(net not in cohort_seen, "ORDER_COHORT_DUPLICATE")
        cohort_seen.add(net)
        need(net in seen, "ORDER_COHORT_UNKNOWN")
    return list(cohort_ids) + [net for net in net_ids if net not in cohort_seen]


def stable_move_to_front(net_ids, selected: str) -> list[str]:
    """Compatibility helper for the predecessor's one-net literal controls."""
    need(type(selected) is str and bool(selected), "ORDER_INPUT")
    need(type(net_ids) is list and selected in net_ids, "ORDER_SELECTED_MISSING")
    return stable_cohort_order(net_ids, [selected])


def _validate_terminal_failure(value, outcome, ordering, run_work, *, control):
    if value is None:
        need(not (control and outcome["disposition"] == "NO_PATH"),
             "TERMINAL_FAILURE_REQUIRED")
        return None
    exact_keys(value, ("failedNetId", "searchIndex", "searchPass", "passBefore",
                       "passAfter", "advanceCallOrdinal", "ownerSearchCleared",
                       "searchReleased", "ownerPromotedNetId", "ownerReason"),
               "TERMINAL_FAILURE_SCHEMA")
    failed = value["failedNetId"]
    requested = ordering["requestedNetIds"]
    traversed = ordering["traversedNetIds"]
    advance_calls = run_work["advanceCalls"]
    advance_ordinal = value["advanceCallOrdinal"]
    need(type(failed) is str and bool(failed)
         and is_int(value["searchPass"], 2, 2)
         and is_int(value["passBefore"], 2, 2)
         and is_int(value["passAfter"], 3, 3)
         and is_int(value["searchIndex"], 1, len(requested))
         and is_int(advance_calls, 2, 2_000_000)
         and is_int(advance_ordinal, 1, advance_calls - 1)
         and advance_ordinal + 1 == advance_calls
         and value["ownerSearchCleared"] is True and value["searchReleased"] is True
         and value["ownerPromotedNetId"] == failed
         and type(value["ownerReason"]) is str and bool(value["ownerReason"])
         and requested[value["searchIndex"] - 1] == failed
         and traversed.count(failed) == 1,
         "TERMINAL_FAILURE_BINDING")
    reason = outcome["reason"]
    suffix = reason.rsplit(":", 1)[-1] if type(reason) is str else None
    reason_net = reason.rsplit(":", 1)[0] if type(reason) is str and ":" in reason else None
    allowed = {
        "NO_PATH": {"NO_PATH"},
        "CAP_REJECTED": {"GRID_LIMIT", "TOTAL_SEARCH_LIMIT", "BRANCH_SEARCH_LIMIT"},
        "QUALITY_REJECTED": {"QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT"},
    }
    need(value["ownerReason"] == reason
         and suffix in allowed.get(outcome["disposition"], set())
         and (reason_net is None or reason_net == failed)
         and (suffix != "GRID_LIMIT" or reason_net is None)
         and (outcome["disposition"] != "NO_PATH" or reason_net == failed),
         "TERMINAL_FAILURE_REASON")
    return value


def _validate_operation1(operation, parsed, raw: bytes, manifest_sha: str,
                         wire_sha: str, run_id: str) -> None:
    exact_keys(operation, ("operation", "mode", "runId", "sourceManifestSha256", "wireSha256",
                           "receiptPath", "receiptBytes", "receiptSha256", "elapsedToCloseNanos",
                           "maxFullGroupNanos", "cleanupNanos", "ownersReleased", "contextRestored",
                           "groupCount", "p07OwnerSteps", "callbackUnits", "requestIndex",
                           "selectedAttempt", "rootSeed", "placementSeed", "routingSeed"),
               "OP1_SCHEMA")
    need(is_int(operation["operation"], 1, 1) and operation["mode"] == "AUTHORED"
         and operation["runId"] == run_id and operation["sourceManifestSha256"] == manifest_sha
         and operation["wireSha256"] == wire_sha and operation["receiptPath"] == "op-1.jsonl"
         and is_int(operation["receiptBytes"], 1, MAX_RECEIPT_BYTES)
         and operation["receiptBytes"] == len(raw) and operation["receiptSha256"] == sha(raw)
         and is_int(operation["elapsedToCloseNanos"], 1, MAX_TOTAL_NS)
         and is_int(operation["maxFullGroupNanos"], 1, MAX_GROUP_NS)
         and is_int(operation["cleanupNanos"], 0, MAX_CLEANUP_NS)
         and operation["ownersReleased"] is True and operation["contextRestored"] is True
         and is_int(operation["groupCount"], 1, MAX_GROUPS)
         and is_int(operation["p07OwnerSteps"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(operation["callbackUnits"], 0, MAX_GROUP_UNITS)
         and is_int(operation["requestIndex"], 0, 0)
         and is_int(operation["selectedAttempt"], 4, 4)
         and (operation["rootSeed"], operation["placementSeed"], operation["routingSeed"])
             == (ROOT_SEED, PLACEMENT_SEED, ROUTING_SEED), "OP1_FIELDS")
    need(type(parsed) is dict and type(parsed.get("groups")) is list
         and type(parsed.get("pads")) is list and type(parsed.get("branches")) is list
         and type(parsed.get("selectedBranch")) is dict and type(parsed.get("snapshot")) is dict
         and operation["groupCount"] == len(parsed["groups"])
         and operation["p07OwnerSteps"] == parsed["p07OwnerSteps"], "OP1_P07_BINDING")
    p07_steps = 0
    max_elapsed = 0
    for index, group in enumerate(parsed["groups"]):
        need(type(group) is dict and is_int(group.get("ordinal"), index, index),
             "OP1_GROUP_ORDINAL")
        p07 = group.get("p07Steps")
        need(p07 is None or is_int(p07, 0, MAX_GROUP_UNITS), "OP1_GROUP_P07")
        need(is_int(group.get("diagnosticUnits"), 0, MAX_GROUP_UNITS), "OP1_GROUP_DIAGNOSTIC")
        elapsed = group.get("elapsedBeforeOwnLineNanos")
        need(is_int(elapsed, 0, MAX_GROUP_NS), "OP1_GROUP_TIME")
        p07_steps += p07 or 0
        max_elapsed = max(max_elapsed, elapsed)
    need(p07_steps == operation["p07OwnerSteps"]
         and operation["maxFullGroupNanos"] >= max_elapsed
         and operation["elapsedToCloseNanos"] >= sum(
             row["elapsedBeforeOwnLineNanos"] for row in parsed["groups"]),
         "OP1_GROUP_ACCOUNTING")
    candidate = parsed.get("candidate")
    need(type(candidate) is dict and candidate.get("traceWidthProfile") == "ROLE_WIDTHS"
         and candidate.get("componentCount") == 100
         and candidate.get("padCount") == 246 and candidate.get("netCount") == 60,
         "OP1_CANDIDATE")


def _validate_reference(reference, parsed) -> None:
    exact_keys(reference, ("receiptOrdinal", "groupOrdinal", "lineageOrdinal", "pass", "netId",
                           "startPadId", "rootPadId", "terminalReason", "prefixGeometrySha256",
                           "prefixGeometryBytes", "prefixHoleCount", "prefixViaCount", "reachedPadIds",
                           "reachedPthPadIds", "reachedPadCount", "reachedPthPadCount"),
               "REFERENCE_SCHEMA")
    branch, snapshot = parsed["selectedBranch"], parsed["snapshot"]
    need(is_int(reference["receiptOrdinal"], 307, 307)
         and is_int(branch["ordinal"], 307, 307)
         and is_int(reference["groupOrdinal"], 0, MAX_GROUPS - 1)
         and is_int(reference["lineageOrdinal"], 77, 77)
         and is_int(reference["pass"], 2, 2)
         and is_int(reference["prefixHoleCount"], MAX_VIAS, MAX_VIAS)
         and is_int(reference["prefixViaCount"], MAX_VIAS, MAX_VIAS)
         and is_int(reference["reachedPadCount"], 1, 1)
         and is_int(reference["reachedPthPadCount"], 0, 0)
         and reference["receiptOrdinal"] == branch["ordinal"] == 307
         and reference["groupOrdinal"] == branch["group"]
         and reference["lineageOrdinal"] == parsed["lineageOrdinal"] == 77
         and reference["pass"] == snapshot["pass"] == 2
         and reference["netId"] == snapshot["netId"] == "A_COIL_LOW"
         and reference["startPadId"] == snapshot["startPadId"] == "QA.C"
         and reference["rootPadId"] == snapshot["rootPadId"] == "DA.A"
         and reference["terminalReason"] == snapshot["terminalReason"] == "NO_PATH"
         and reference["prefixGeometrySha256"] == PREFIX_SHA256 == branch["prefixSha256"]
         and is_int(reference["prefixGeometryBytes"], 1, MAX_JSON_BYTES)
         and reference["prefixGeometryBytes"] == branch["prefixBytes"]
         and reference["prefixViaCount"] == snapshot["prefixViaCount"]
         and reference["reachedPadIds"] == ["DA.A"] and reference["reachedPthPadIds"] == []
         and reference["reachedPadCount"] == snapshot["reachedPadCount"]
         , "REFERENCE_BINDING")


def _validate_source(source, parsed, base_reader):
    exact_keys(source, ("templateGeometryBeforeSha256", "templateGeometryAfterSha256",
                        "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
                        "detachedGeometrySha256", "detachedGeometryAfterSha256", "padLands",
                        "templateTraceCount", "templateHoleCount", "sourceUnchanged"),
               "SOURCE_SCHEMA")
    digests = ("templateGeometryBeforeSha256", "templateGeometryAfterSha256",
               "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
               "detachedGeometrySha256", "detachedGeometryAfterSha256")
    need(all(is_digest(source[key]) for key in digests)
         and source["templateGeometryBeforeSha256"] == source["templateGeometryAfterSha256"]
         and source["templateEffectsBeforeSha256"] == source["templateEffectsAfterSha256"]
         and source["detachedGeometrySha256"] == source["detachedGeometryAfterSha256"]
         and is_int(source["templateTraceCount"], 0, 0)
         and is_int(source["templateHoleCount"], 0, 0)
         and source["sourceUnchanged"] is True, "SOURCE_IMMUTABILITY")
    check = getattr(base_reader, "_validate_pad_lands", None)
    need(callable(check), "P07_PAD_API")
    try:
        pad_data = check(source["padLands"], parsed["pads"])
    except Exception:
        raise ReaderError("SOURCE_PAD_CENSUS") from None
    return pad_data


def _validate_captured_order(value, parsed, pad_data) -> dict:
    exact_keys(value, ("pass", "failedNetId", "priorPromotedNetId", "netIds",
                       "liveSearchIndex", "owner"), "CAPTURED_ORDER_SCHEMA")
    owner = value["owner"]
    exact_keys(owner, ("policy", "maximumExpansions", "expandedAtFailure", "maximumVias",
                       "viaCost", "policyTransitions", "policyVias", "branchOrderStrategy",
                       "netOrderStrategy", "roleWidths", "orderingStream"),
               "CAPTURED_OWNER_SCHEMA")
    net_ids = value["netIds"]
    need(type(net_ids) is list and all(type(net) is str and bool(net) for net in net_ids),
         "CAPTURED_VECTOR_FIELDS")
    snapshot = parsed["snapshot"]
    need(is_int(value["pass"], 2, 2) and type(value["failedNetId"]) is str
         and value["failedNetId"] == snapshot["netId"]
         and (value["priorPromotedNetId"] is None
              or (type(value["priorPromotedNetId"]) is str
                  and bool(value["priorPromotedNetId"])
                  and value["priorPromotedNetId"] in net_ids))
         and len(net_ids) == len(set(net_ids))
         and set(net_ids) == set(pad_data["roleByNet"])
         and value["failedNetId"] in net_ids
         # Search selects currentNet with nets.get(netIndex++): this is the next cursor.
         and is_int(value["liveSearchIndex"], 1, len(net_ids))
         and net_ids[value["liveSearchIndex"] - 1] == value["failedNetId"],
         "CAPTURED_VECTOR_BINDING")
    need(owner["policy"] == "FULLER_TWO_LAYER"
         and is_int(owner["maximumExpansions"], 1, MAX_EXPANSIONS)
         and is_int(owner["expandedAtFailure"], 0, owner["maximumExpansions"])
         and is_int(owner["maximumVias"], MAX_VIAS, MAX_VIAS)
         and is_int(owner["viaCost"], 0, 1_000_000)
         and is_int(owner["policyTransitions"], MAX_TRANSITIONS, MAX_TRANSITIONS)
         and is_int(owner["policyVias"], MAX_VIAS, MAX_VIAS)
         and owner["branchOrderStrategy"] == "CANONICAL_NEAREST"
         and owner["netOrderStrategy"] == "CANONICAL"
         and owner["roleWidths"] is True and type(owner["orderingStream"]) is bool,
         "CAPTURED_OWNER_BINDING")
    return {"owner": owner, "netIds": list(net_ids), "failedNetId": value["failedNetId"],
            "priorPromotedNetId": value["priorPromotedNetId"]}


def _validate_run(run, kind: str, captured: dict, cohort_ids) -> dict:
    exact_keys(run, ("kind", "initial", "ordering", "outcome", "terminalFailure",
                     "finalizers", "layout", "work"), "FRESH_RUN_SCHEMA")
    need(run["kind"] == kind, "FRESH_RUN_KIND")
    owner = captured["owner"]
    initial = run["initial"]
    exact_keys(initial, ("pass", "passes", "expanded", "maximumExpansions", "maximumVias",
                         "priorPromotedNetId", "policy", "viaCost", "policyTransitions",
                         "policyVias", "branchOrderStrategy", "netOrderStrategy", "roleWidths",
                         "capturedOrderingStream", "sessionOrderingStream",
                         "instrumentationModeDifference", "routingRelevantSettingsMatch",
                         "traceCount", "holeCount"), "FRESH_INITIAL_SCHEMA")
    int_expectations = (("pass", 2), ("passes", 0), ("expanded", 0),
                        ("maximumExpansions", owner["maximumExpansions"]),
                        ("maximumVias", owner["maximumVias"]), ("viaCost", owner["viaCost"]),
                        ("policyTransitions", owner["policyTransitions"]),
                        ("policyVias", owner["policyVias"]), ("traceCount", 0), ("holeCount", 0))
    need(all(is_int(initial[name], expected, expected) for name, expected in int_expectations)
         and initial["priorPromotedNetId"] == captured["priorPromotedNetId"]
         and initial["policy"] == owner["policy"]
         and initial["branchOrderStrategy"] == owner["branchOrderStrategy"]
         and initial["netOrderStrategy"] == owner["netOrderStrategy"]
         and initial["roleWidths"] is owner["roleWidths"]
         and initial["capturedOrderingStream"] is owner["orderingStream"]
         and initial["sessionOrderingStream"] is False
         and initial["instrumentationModeDifference"] is owner["orderingStream"]
         and initial["routingRelevantSettingsMatch"] is True,
         "FRESH_INITIAL_BINDING")
    ordering = run["ordering"]
    exact_keys(ordering, ("capturedNetIds", "computedNetIds", "requestedCohortNetIds",
                          "requestedNetIds", "traversedNetIds", "orderMatchesCapture",
                          "searchCount"), "FRESH_ORDERING_SCHEMA")
    captured_ids = captured["netIds"]
    expected = stable_cohort_order(captured_ids, cohort_ids)
    traversed = ordering["traversedNetIds"]
    need(type(traversed) is list and all(type(net) is str for net in traversed)
         and ordering["capturedNetIds"] == captured_ids
         and ordering["computedNetIds"] == captured_ids
         and ordering["requestedCohortNetIds"] == cohort_ids
         and ordering["requestedNetIds"] == expected
         and ordering["orderMatchesCapture"] is True
         and is_int(ordering["searchCount"], 1, 1)
         and len(traversed) <= len(expected)
         and traversed == expected[:len(traversed)]
         and len(set(traversed)) == len(traversed),
         "FRESH_ORDERING_BINDING")
    outcome = run["outcome"]
    exact_keys(outcome, ("disposition", "reason", "complete", "ownerComplete", "pass", "passes",
                         "expanded", "maximumExpansions", "errorClass", "errorMessage"),
               "FRESH_OUTCOME_SCHEMA")
    disposition, reason = outcome["disposition"], outcome["reason"]
    need(disposition in ("FOUND", "NO_PATH", "CAP_REJECTED", "QUALITY_REJECTED")
         and (reason is None or (type(reason) is str and bool(reason)))
         and type(outcome["complete"]) is bool and type(outcome["ownerComplete"]) is bool
         and outcome["ownerComplete"] is True
         and is_int(outcome["pass"], 2, 3) and is_int(outcome["passes"], 1, 1)
         and is_int(outcome["expanded"], 0, owner["maximumExpansions"])
         and is_int(outcome["maximumExpansions"], owner["maximumExpansions"],
                    owner["maximumExpansions"])
         and outcome["errorClass"] is None and outcome["errorMessage"] is None,
         "FRESH_OUTCOME_BINDING")
    if disposition == "FOUND":
        need(reason == "SUCCESS" and outcome["complete"] is True
             and outcome["pass"] == 2 and traversed == expected,
             "FOUND_OUTCOME")
    else:
        need(type(reason) is str and bool(reason)
             and outcome["complete"] is False and outcome["pass"] == 3,
             "NONFOUND_OUTCOME")
        suffix = reason.rsplit(":", 1)[-1]
        reason_net = reason.rsplit(":", 1)[0] if ":" in reason else None
        allowed_suffixes = {
            "NO_PATH": {"NO_PATH"},
            "CAP_REJECTED": {"GRID_LIMIT", "TOTAL_SEARCH_LIMIT", "BRANCH_SEARCH_LIMIT"},
            "QUALITY_REJECTED": {"QUALITY_LIMIT", "BRANCH_QUALITY_LIMIT"},
        }
        need(suffix in allowed_suffixes[disposition]
             and (reason_net is None or reason_net in captured["netIds"])
             and (suffix != "GRID_LIMIT" or reason_net is None),
             "OUTCOME_REASON")
    need((disposition == "FOUND") == (run["layout"] is not None),
         "FRESH_LAYOUT_DISPOSITION")
    finalizers = run["finalizers"]
    exact_keys(finalizers, ("routingGeometry", "allNetConnectivity", "twoLayerRules",
                            "routeQuality", "viaCap"), "FINALIZERS_SCHEMA")
    if disposition == "FOUND":
        need(all(finalizers[field] == "PASS" for field in finalizers), "FOUND_FINALIZERS")
    elif disposition == "QUALITY_REJECTED" and reason == "QUALITY_LIMIT":
        need(finalizers == {"routingGeometry": "PASS", "allNetConnectivity": "PASS",
                            "twoLayerRules": "PASS", "routeQuality": "REJECTED",
                            "viaCap": "NOT_RUN"}, "QUALITY_FINALIZERS")
    else:
        need(all(finalizers[field] == "NOT_RUN" for field in finalizers),
             "NONFOUND_FINALIZERS")
    work = run["work"]
    exact_keys(work, ("advanceCalls", "ownerUnits", "wrapperUnits", "copyEntries", "copyChunks",
                      "groups", "maxGroupNanos", "maximumUnitsPerGroup",
                      "failureObservationUnits", "terminalEvidenceUnits"), "RUN_WORK_SCHEMA")
    need(is_int(work["advanceCalls"], 1, 2_000_000)
         and is_int(work["ownerUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(work["wrapperUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and is_int(work["failureObservationUnits"], work["advanceCalls"],
                    work["advanceCalls"])
         and is_int(work["terminalEvidenceUnits"], 0, MAX_GROUP_UNITS * MAX_GROUPS)
         and work["wrapperUnits"] >= (work["advanceCalls"]
                                       + work["failureObservationUnits"]
                                       + work["terminalEvidenceUnits"])
         and is_int(work["copyEntries"], 0, MAX_COPY_ENTRIES)
         and is_int(work["copyChunks"], 0, MAX_COPY_ENTRIES)
         and is_int(work["groups"], 1, MAX_GROUPS)
         and is_int(work["maxGroupNanos"], 1, MAX_GROUP_NS)
         and is_int(work["maximumUnitsPerGroup"], 1, MAX_GROUP_UNITS)
         and work["copyChunks"] <= work["copyEntries"]
         and (work["copyEntries"] == 0 or work["copyChunks"] >=
              (work["copyEntries"] + COPY_CHUNK - 1) // COPY_CHUNK),
         "RUN_WORK_BOUNDS")
    need(traversed or disposition == "FOUND", "EMPTY_TRAVERSAL")
    terminal = _validate_terminal_failure(run["terminalFailure"], outcome, ordering, work,
                                          control=kind == "OBSERVED_FAILURE_CONTROL")
    expected_terminal_units = (0 if terminal is None else
                               11 + len(traversed)
                               + (1 if kind == "OBSERVED_FAILURE_CONTROL"
                                  and disposition == "NO_PATH" else 0))
    need(work["terminalEvidenceUnits"] == expected_terminal_units,
         "TERMINAL_EVIDENCE_ACCOUNTING")
    return {"expectedOrder": expected, "ordering": ordering, "outcome": outcome,
            "terminalFailure": terminal, "work": work, "layout": run["layout"]}


def _validate_trial_disposition(value, trial_run, captured, control):
    exact_keys(value, ("status", "reason", "capturedFailedNetId", "controlFailedNetId",
                       "cohortNetIds", "requestedNetIds", "orderChanged",
                       "feedbackValidated"), "TRIAL_DISPOSITION_SCHEMA")
    need(value["status"] in ("RUN", "SKIPPED")
         and value["reason"] in ("TRIAL_EXECUTED", "CONTROL_FOUND",
                                  "CONTROL_NON_NO_PATH_TERMINAL", "NO_ORDER_CHANGE")
         and value["capturedFailedNetId"] == captured["failedNetId"]
         and type(value["orderChanged"]) is bool
         and value["feedbackValidated"] is True,
         "TRIAL_DISPOSITION_FIELDS")
    disposition = control["outcome"]["disposition"]
    if disposition == "FOUND":
        need(value["status"] == "SKIPPED" and value["reason"] == "CONTROL_FOUND"
             and trial_run is None and value["controlFailedNetId"] is None
             and value["cohortNetIds"] is None and value["requestedNetIds"] is None
             and value["orderChanged"] is False,
             "TRIAL_SKIP_CONTROL_FOUND")
        return {"trial": None, "cohortNetIds": None, "requestedNetIds": None}
    if disposition != "NO_PATH":
        terminal = control["terminalFailure"]
        control_failed = None if terminal is None else terminal["failedNetId"]
        need(value["status"] == "SKIPPED"
             and value["reason"] == "CONTROL_NON_NO_PATH_TERMINAL"
             and trial_run is None and value["controlFailedNetId"] == control_failed
             and value["cohortNetIds"] is None and value["requestedNetIds"] is None
             and value["orderChanged"] is False,
             "TRIAL_SKIP_NON_NO_PATH")
        return {"trial": None, "cohortNetIds": None, "requestedNetIds": None}

    terminal = control["terminalFailure"]
    need(type(terminal) is dict, "TRIAL_CONTROL_FEEDBACK_REQUIRED")
    captured_failed = captured["failedNetId"]
    control_failed = terminal["failedNetId"]
    need(control_failed != captured_failed, "REPEATED_FAILURE_ID")
    cohort = [captured_failed, control_failed]
    requested = stable_cohort_order(captured["netIds"], cohort)
    no_change = requested == control["expectedOrder"]
    if no_change:
        need(value["status"] == "SKIPPED" and value["reason"] == "NO_ORDER_CHANGE"
             and trial_run is None and value["controlFailedNetId"] == control_failed
             and value["cohortNetIds"] == cohort and value["requestedNetIds"] == requested
             and value["orderChanged"] is False,
             "TRIAL_SKIP_NO_ORDER_CHANGE")
        return {"trial": None, "cohortNetIds": cohort, "requestedNetIds": requested}
    need(value["status"] == "RUN" and value["reason"] == "TRIAL_EXECUTED"
         and type(trial_run) is dict and value["controlFailedNetId"] == control_failed
         and value["cohortNetIds"] == cohort and value["requestedNetIds"] == requested
         and value["orderChanged"] is True,
         "TRIAL_EXECUTION_DISPOSITION")
    return {"trial": trial_run, "cohortNetIds": cohort, "requestedNetIds": requested}


def _validate_work(work, operation, parsed, reference, control, trial,
                   metadata_tree_minimum):
    exact_keys(work, ("elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
                      "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
                      "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport",
                      "originalOwnerSteps", "originalDiagnosticUnits", "originalCaptureUnits",
                      "extraTerminalCallbackUnits", "controlOwnerUnits", "controlWrapperUnits",
                      "trialOwnerUnits", "trialWrapperUnits", "controlFailureObservationUnits",
                      "trialFailureObservationUnits", "controlTerminalEvidenceUnits",
                      "trialTerminalEvidenceUnits", "metadataUnits", "copiedEntries",
                      "copyChunks", "copyChunkLimit", "copiedEntryLimit", "cleanup"),
               "WORK_SCHEMA")
    names = ("elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
             "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
             "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "originalOwnerSteps",
             "originalDiagnosticUnits", "originalCaptureUnits", "extraTerminalCallbackUnits",
             "controlOwnerUnits", "controlWrapperUnits", "trialOwnerUnits", "trialWrapperUnits",
             "controlFailureObservationUnits", "trialFailureObservationUnits",
             "controlTerminalEvidenceUnits", "trialTerminalEvidenceUnits", "metadataUnits",
             "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit")
    need(all(is_int(work[name]) for name in names), "WORK_INTEGER")
    p07_steps = 0
    diagnostic_units = 0
    original_max_ns = 0
    original_max_units = 0
    callback_group = reference["groupOrdinal"]
    for group in parsed["groups"]:
        p07 = group["p07Steps"] or 0
        diagnostic = group["diagnosticUnits"]
        units = p07 + diagnostic
        if group["ordinal"] == callback_group:
            units += operation["callbackUnits"] + work["extraTerminalCallbackUnits"]
        need(units <= MAX_GROUP_UNITS, "ORIGINAL_CALLBACK_GROUP_LIMIT")
        p07_steps += p07
        diagnostic_units += diagnostic
        original_max_ns = max(original_max_ns, group["elapsedBeforeOwnLineNanos"])
        original_max_units = max(original_max_units, units)
    need(work["originalOwnerSteps"] == p07_steps == operation["p07OwnerSteps"]
         and work["originalDiagnosticUnits"] == diagnostic_units
         and work["originalCaptureUnits"] == operation["callbackUnits"],
         "ORIGINAL_WORK_BINDING")
    trial_work = None if trial is None else trial["work"]
    need(work["controlOwnerUnits"] == control["work"]["ownerUnits"]
         and work["controlWrapperUnits"] == control["work"]["wrapperUnits"]
         and work["controlFailureObservationUnits"] ==
             control["work"]["failureObservationUnits"]
         and work["controlTerminalEvidenceUnits"] ==
             control["work"]["terminalEvidenceUnits"]
         and work["trialOwnerUnits"] == (0 if trial_work is None else trial_work["ownerUnits"])
         and work["trialWrapperUnits"] == (0 if trial_work is None else trial_work["wrapperUnits"])
         and work["trialFailureObservationUnits"] ==
             (0 if trial_work is None else trial_work["failureObservationUnits"])
         and work["trialTerminalEvidenceUnits"] ==
             (0 if trial_work is None else trial_work["terminalEvidenceUnits"]),
         "RUN_WORK_BINDING")
    additive = (operation["p07OwnerSteps"] + diagnostic_units + operation["callbackUnits"]
                + work["extraTerminalCallbackUnits"] + work["controlOwnerUnits"]
                + work["controlWrapperUnits"] + work["trialOwnerUnits"]
                + work["trialWrapperUnits"] + work["metadataUnits"])
    need(work["unitsBeforeExport"] == additive, "WORK_UNITS")
    need(work["metadataUnits"] >= metadata_tree_minimum, "METADATA_TREE_ACCOUNTING")
    run_groups = control["work"]["groups"] + (0 if trial_work is None else trial_work["groups"])
    need(work["groupsBeforeExport"] == operation["groupCount"] + run_groups
             + work["wrapperGroupsBeforeExport"]
         and work["wrapperGroupsBeforeExport"] >= 1
         and work["groupsBeforeExport"] + 1 <= MAX_GROUPS,
         "WORK_GROUPS")
    max_ns = max(operation["maxFullGroupNanos"], original_max_ns,
                 control["work"]["maxGroupNanos"],
                 0 if trial_work is None else trial_work["maxGroupNanos"])
    max_units = max(original_max_units, control["work"]["maximumUnitsPerGroup"],
                    0 if trial_work is None else trial_work["maximumUnitsPerGroup"])
    control_entries = control["work"]["copyEntries"]
    trial_entries = 0 if trial_work is None else trial_work["copyEntries"]
    control_chunks = control["work"]["copyChunks"]
    trial_chunks = 0 if trial_work is None else trial_work["copyChunks"]
    need(work["maxGroupNanosBeforeExport"] >= max_ns
         and 1 <= work["maxGroupNanosBeforeExport"] <= MAX_GROUP_NS
         and work["maximumUnitsPerGroupBeforeExport"] >= max_units
         and 1 <= work["maximumUnitsPerGroupBeforeExport"] <= MAX_GROUP_UNITS
         and work["unitsBeforeExport"] <= work["groupsBeforeExport"] * MAX_GROUP_UNITS
         and work["copiedEntries"] <= MAX_COPY_ENTRIES
         and work["copyChunkLimit"] == COPY_CHUNK
         and work["copiedEntryLimit"] == MAX_COPY_ENTRIES
         and work["copiedEntries"] >= control_entries + trial_entries
         and work["copyChunks"] >= control_chunks + trial_chunks
         and work["copyChunks"] <= work["copiedEntries"]
         and (work["copiedEntries"] == 0 or work["copyChunks"] >=
              (work["copiedEntries"] + COPY_CHUNK - 1) // COPY_CHUNK)
         and work["cleanupNanos"] >= operation["cleanupNanos"]
         and work["cleanupNanos"] <= MAX_CLEANUP_NS
         and 0 < work["elapsedCutoffNanos"] <= MAX_TOTAL_NS,
         "WORK_BOUNDS")
    if trial_work is None:
        need(work["trialOwnerUnits"] == work["trialWrapperUnits"] == 0
             and work["trialFailureObservationUnits"] == 0
             and work["trialTerminalEvidenceUnits"] == 0,
             "NULL_TRIAL_WORK")
    cleanup = work["cleanup"]
    exact_keys(cleanup, ("originalOwnersReleased", "originalContextRestored",
                         "controlReferencesDropped", "trialReferencesDropped",
                         "sourceUnchanged", "allReferencesDropped"), "CLEANUP_SCHEMA")
    need(cleanup["originalOwnersReleased"] is True
         and cleanup["originalContextRestored"] is True
         and cleanup["controlReferencesDropped"] is True
         and cleanup["trialReferencesDropped"] is True
         and cleanup["sourceUnchanged"] is True
         and cleanup["allReferencesDropped"] is True,
         "CLEANUP_PROOF")
    return {"p07Steps": p07_steps, "diagnosticUnits": diagnostic_units,
            "maxGroupNanos": max_ns, "maxGroupUnits": max_units,
            "cleanup": cleanup}


def _expected_classpath(context) -> str:
    jars = context.get("jars")
    need(type(jars) is dict and type(jars.get("gwt-user-2.7.0.jar")) is dict
         and type(jars.get("gwt-dev-2.7.0.jar")) is dict, "NATIVE_JARS")
    return ";".join((str(context["classesRoot"]),
                     jars["gwt-user-2.7.0.jar"]["path"],
                     jars["gwt-dev-2.7.0.jar"]["path"]))


def _validate_call(context, parent_path: str | Path, *, canary: bool,
                   data_path: str | Path | None = None):
    base = context["baseReader"]
    parent_file = Path(parent_path)
    need(parent_file.is_absolute(), "PARENT_PATH_ABSOLUTE")
    parent_file = base._canonical(parent_file, "PARENT_PATH")
    root = ROOT.resolve()
    base._inside(parent_file, root, "PARENT_PATH_ROOT")
    need(parent_file.name == "parent.json", "PARENT_PATH_NAME")
    call_dir = parent_file.parent
    base._lstat_directory(call_dir, "CALL_DIRECTORY")
    try:
        call_names = sorted(entry.name for entry in call_dir.iterdir())
    except OSError:
        raise ReaderError("CALL_TREE_READ") from None
    need(call_names == ["owner.json", "parent.json", "process.json", "request.json",
                        "result.json", "stderr.txt", "stdout.txt"], "CALL_TREE")
    parent = base.host_document(base._read(parent_file, 256 * 1024, "PARENT_READ"),
                                256 * 1024)
    exact_keys(parent, ("schema", "elapsedMs", "process"), "PARENT_SCHEMA")
    process = parent["process"]
    args = process.get("Arguments") if type(process) is dict else None
    need(type(args) is list and type(process) is dict, "PARENT_PROCESS")
    classpath = _expected_classpath(context)
    if canary:
        expected_args = ["-ea", "-cp", classpath, ENTRY_CLASS, "--canary"]
        need(args == expected_args, "CANARY_ARGUMENTS")
    else:
        data_dir = Path(data_path)
        need(data_dir.is_absolute(), "DATA_PATH_ABSOLUTE")
        data_dir = base._canonical(data_dir, "DATA_PATH")
        base._inside(data_dir, root, "DATA_PATH_ROOT")
        base._lstat_directory(data_dir, "DATA_DIRECTORY")
        need(data_dir != call_dir, "OUTPUT_SEPARATION")
        try:
            names = sorted(entry.name for entry in data_dir.iterdir())
        except OSError:
            raise ReaderError("DATA_TREE_READ") from None
        need(names == ["op-1.jsonl", "result.json"], "DATA_TREE")
        need(len(args) == 9 and args[:5] == ["-ea", "-cp", classpath, ENTRY_CLASS,
                                               "FAILURE_COHORT"]
             and type(args[5]) is str and UUID_RE.fullmatch(args[5]) is not None
             and args[6] == context["sourceSha"] and args[7] == WIRE_SHA256
             and args[8] == str(data_dir), "NATIVE_ARGUMENTS")
        expected_args = ["-ea", "-cp", classpath, ENTRY_CLASS, "FAILURE_COHORT", args[5],
                         context["sourceSha"], WIRE_SHA256, str(data_dir)]
    request = base.host_document(base._read(call_dir / "request.json", 64 * 1024,
                                            "REQUEST_READ"), 64 * 1024)
    process_result = base._validate_owner_call(
        call_dir, parent_file, parent, request, process, context["java"]["path"],
        expected_args, 480_000, context["processModule"])
    marker_raw = process["Stdout"].encode("utf-8", errors="strict")
    marker = base.strict_console_line(marker_raw, "NATIVE_MARKER")
    return parent, process, marker, process_result, (data_dir if not canary else None)


def _validate_marker(marker, result_raw: bytes, doc: dict) -> dict:
    exact_keys(marker, ("schema", "status", "resultSha256", "resultBytes", "elapsedNanos",
                        "finalExportNanos", "totalGroups", "totalUnits", "maxGroupNanos",
                        "maximumUnitsPerGroup"), "MARKER_SCHEMA")
    work = doc["work"]
    final_export_units = 5 * ((len(result_raw) + COPY_CHUNK - 1) // COPY_CHUNK) + 3
    need(marker["schema"] == MARKER_SCHEMA and marker["status"] == "COMPLETED_DIAGNOSTIC"
         and marker["resultSha256"] == sha(result_raw)
         and is_int(marker["resultBytes"], len(result_raw), len(result_raw))
         and is_int(marker["elapsedNanos"], 1, MAX_TOTAL_NS)
         and is_int(marker["finalExportNanos"], 1, MAX_GROUP_NS)
         and is_int(marker["totalGroups"], work["groupsBeforeExport"] + 1,
                    work["groupsBeforeExport"] + 1)
         and is_int(marker["totalUnits"], work["unitsBeforeExport"] + final_export_units,
                    work["unitsBeforeExport"] + final_export_units)
         and is_int(marker["maxGroupNanos"],
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]),
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]))
         and is_int(marker["maximumUnitsPerGroup"],
                    max(work["maximumUnitsPerGroupBeforeExport"], final_export_units),
                    max(work["maximumUnitsPerGroupBeforeExport"], final_export_units))
         and final_export_units <= MAX_GROUP_UNITS
         and marker["elapsedNanos"] >= work["elapsedCutoffNanos"]
         + marker["finalExportNanos"], "MARKER_BINDING")
    return marker


"""Independent copper identity/connectivity checks for a serialized FOUND layout.

This fragment intentionally does not certify obstacle clearance, placement,
route quality, or the native finalizers.  It checks physical conductor identity,
same-layer contact/shorts, and per-net copper connectivity only.
"""


class ConductorError(ValueError):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


_CONDUCTOR_ROLE_WIDTHS = {
    "RETURN": 11,
    "SUPPLY": 11,
    "HIGH_CURRENT": 13,
    "CONTROL": 9,
    "SIGNAL": 9,
}
_CONDUCTOR_ROLE_CANONICAL = (
    "ROLE_TRACE@1;RETURN=11;SUPPLY=11;HIGH_CURRENT=13;CONTROL=9;SIGNAL=9;"
    "units=drawing;edge-clearance=6"
)
_CONDUCTOR_LAYERS = ("TOP", "BOTTOM")
_CONDUCTOR_EXPOSURES = ("EXPOSED", "COVERED")
_CONDUCTOR_ATTACHMENTS = ("PLATED_THROUGH_HOLE", "SURFACE_PAD")
_CONDUCTOR_HOLE_KINDS = ("VIA", "PLATED", "NON_PLATED")
_CONDUCTOR_MAX_COORD = 100000000
_CONDUCTOR_MAX_IDS = 100000
_CONDUCTOR_MAX_TRACE_POINTS = 200000
_CONDUCTOR_CELL = 256
_CONDUCTOR_MAX_SHAPES = 200000
_CONDUCTOR_MAX_BUCKET_REFS = 2000000
_CONDUCTOR_MAX_CONTACT_TESTS = 8000000
_CONDUCTOR_MAX_CELLS_PER_SHAPE = 4096

_CONDUCTOR_LAYOUT_KEYS = {
    "geometrySha256", "width", "height", "outline", "partsTray", "pads",
    "components", "regions", "labels", "placementConstraints",
    "clearanceRules", "traceCount", "holeCount", "viaCount", "traces", "holes",
}
_CONDUCTOR_PAD_KEYS = {
    "padId", "netId", "componentId", "terminalId", "x", "y", "padBounds",
    "probeBounds", "escapeDirection", "escapeLength", "attachment", "mountingSide",
    "exposure", "topCopper", "bottomCopper",
}
_CONDUCTOR_TRACE_KEYS = {
    "sourceId", "netId", "startPadId", "endPadId", "layer", "exposure", "width",
    "x", "y",
}
_CONDUCTOR_HOLE_KEYS = {
    "id", "netId", "kind", "exposure", "x", "y", "drillRadius", "landRadius",
}
_CONDUCTOR_CLEARANCE_KEYS = {
    "traceWidth", "copperHeight", "minimumVisibleClearance",
    "minimumCenterlineClearance", "routingGridClearanceCells", "roleWidths",
    "maximumTransitions", "maximumVias",
}


def _conductor_need(condition, code):
    if not condition:
        raise ConductorError(code)


def _conductor_id(value, code):
    _conductor_need(type(value) is str and 0 < len(value) <= 1024, code)
    _conductor_need("\x00" not in value, code)
    return value


def _conductor_int(value, low, high, code):
    _conductor_need(type(value) is int and low <= value <= high, code)
    return value


def _conductor_coordinate(value, code):
    return _conductor_int(value, -_CONDUCTOR_MAX_COORD,
                           _CONDUCTOR_MAX_COORD, code)


def _conductor_exact_object(value, keys, code):
    _conductor_need(type(value) is dict and set(value) == keys, code)
    return value


def _conductor_rectangle(value, code):
    _conductor_need(type(value) is list and len(value) == 4, code)
    x = _conductor_coordinate(value[0], code)
    y = _conductor_coordinate(value[1], code)
    width = _conductor_int(value[2], 1, 2 * _CONDUCTOR_MAX_COORD, code)
    height = _conductor_int(value[3], 1, 2 * _CONDUCTOR_MAX_COORD, code)
    _conductor_coordinate(x + width, code)
    _conductor_coordinate(y + height, code)
    return (x, y, width, height)


def _conductor_contains_rect(outer, inner):
    return (inner[0] >= outer[0] and inner[1] >= outer[1] and
            inner[0] + inner[2] <= outer[0] + outer[2] and
            inner[1] + inner[3] <= outer[1] + outer[3])


def _conductor_contains_point(rect, x, y):
    return (x >= rect[0] and y >= rect[1] and
            x <= rect[0] + rect[2] and y <= rect[1] + rect[3])


def _conductor_touch(a, b):
    return (a[0] + a[2] >= b[0] and b[0] + b[2] >= a[0] and
            a[1] + a[3] >= b[1] and b[1] + b[3] >= a[1])


def _conductor_stroke(x1, y1, x2, y2, width):
    _conductor_need((x1 == x2) != (y1 == y2), "TRACE_GEOMETRY")
    half = width // 2
    box = (min(x1, x2) - half, min(y1, y2) - half,
           abs(x2 - x1) + width, abs(y2 - y1) + width)
    _conductor_rectangle(list(box), "TRACE_GEOMETRY")
    return box


def _conductor_copy(value):
    if type(value) is dict:
        return {key: _conductor_copy(item) for key, item in value.items()}
    if type(value) is list:
        return [_conductor_copy(item) for item in value]
    if type(value) is tuple:
        return tuple(_conductor_copy(item) for item in value)
    return value


def validate_complete_layout(layout, p07_pads, role_by_net, requested_net_ids):
    """Validate complete copper conductor connectivity independently of Java.

    `p07_pads` is the authenticated parsed operation-1 PAD census.  Its required
    fields are padId/netId/componentId/role/x/y; unrelated P07 fields are ignored.
    No IDs are returned in `facts`.
    """
    _conductor_need(type(layout) is dict and set(layout) == _CONDUCTOR_LAYOUT_KEYS,
                    "LAYOUT_SCHEMA")
    _conductor_need(type(p07_pads) is list and type(role_by_net) is dict,
                    "INPUT_SHAPE")
    if type(requested_net_ids) in (list, tuple):
        requested = [_conductor_id(net, "NET_VECTOR_ID") for net in requested_net_ids]
        _conductor_need(len(requested) == len(set(requested)), "NET_VECTOR_DUPLICATE")
    elif type(requested_net_ids) in (set, frozenset):
        requested = [_conductor_id(net, "NET_VECTOR_ID") for net in requested_net_ids]
    else:
        raise ConductorError("INPUT_SHAPE")
    _conductor_need(len(requested) <= _CONDUCTOR_MAX_IDS, "CONDUCTOR_COMPLEXITY")
    requested_set = set(_conductor_id(net, "NET_VECTOR_ID") for net in requested)
    _conductor_need(bool(requested_set) and len(requested_set) <= _CONDUCTOR_MAX_IDS,
                    "NET_VECTOR_EMPTY")

    geometry_sha = layout.get("geometrySha256")
    _conductor_need(type(geometry_sha) is str and len(geometry_sha) == 64 and
                    all(ch in "0123456789abcdef" for ch in geometry_sha),
                    "LAYOUT_GEOMETRY_HASH")
    _conductor_int(layout.get("width"), 1, 2 * _CONDUCTOR_MAX_COORD, "LAYOUT_DIMENSIONS")
    _conductor_int(layout.get("height"), 1, 2 * _CONDUCTOR_MAX_COORD, "LAYOUT_DIMENSIONS")
    _conductor_rectangle(layout.get("outline"), "LAYOUT_OUTLINE")
    _conductor_rectangle(layout.get("partsTray"), "LAYOUT_PARTS_TRAY")
    _conductor_need(type(layout.get("pads")) is list and
                    type(layout.get("components")) is list and
                    type(layout.get("regions")) is list and
                    type(layout.get("labels")) is list and
                    type(layout.get("placementConstraints")) is dict and
                    type(layout.get("traces")) is list and type(layout.get("holes")) is list,
                    "LAYOUT_COLLECTIONS")
    _conductor_need(len(p07_pads) <= _CONDUCTOR_MAX_IDS and
                    len(layout["pads"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["components"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["holes"]) <= _CONDUCTOR_MAX_IDS and
                    len(layout["traces"]) <= _CONDUCTOR_MAX_IDS,
                    "CONDUCTOR_COMPLEXITY")

    constraints = layout["placementConstraints"]
    _conductor_need(constraints.get("traceWidthProfileIdentity") == "ROLE_TRACE@1" and
                    constraints.get("traceWidthProfileCanonical") ==
                    _CONDUCTOR_ROLE_CANONICAL,
                    "TRACE_PROFILE")
    clearance = _conductor_exact_object(layout.get("clearanceRules"),
                                        _CONDUCTOR_CLEARANCE_KEYS, "CLEARANCE_SCHEMA")
    _conductor_need(clearance.get("traceWidth") == 9 and
                    clearance.get("roleWidths") == _CONDUCTOR_ROLE_WIDTHS,
                    "TRACE_PROFILE")
    maximum_vias = _conductor_int(clearance.get("maximumVias"), 0,
                                  _CONDUCTOR_MAX_COORD, "CLEARANCE_SCHEMA")

    _conductor_need(type(layout.get("traceCount")) is int and
                    layout["traceCount"] == len(layout["traces"]) and
                    type(layout.get("holeCount")) is int and
                    layout["holeCount"] == len(layout["holes"]), "LAYOUT_COUNTS")
    _conductor_int(layout.get("traceCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")
    _conductor_int(layout.get("holeCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")
    _conductor_int(layout.get("viaCount"), 0, _CONDUCTOR_MAX_SHAPES, "LAYOUT_COUNTS")

    _conductor_need(set(role_by_net) == requested_set, "ROLE_NET_SET")
    for net, role in role_by_net.items():
        _conductor_id(net, "ROLE_NET_ID")
        _conductor_need(type(role) is str and role in _CONDUCTOR_ROLE_WIDTHS,
                        "ROLE_NAME")

    p07_by_id = {}
    p07_role_by_net = {}
    for row in p07_pads:
        _conductor_need(type(row) is dict, "P07_PAD_SCHEMA")
        pad_id = _conductor_id(row.get("padId"), "P07_PAD_SCHEMA")
        net_id = _conductor_id(row.get("netId"), "P07_PAD_SCHEMA")
        component_id = _conductor_id(row.get("componentId"), "P07_PAD_SCHEMA")
        role = row.get("role")
        _conductor_need(type(role) is str and role in _CONDUCTOR_ROLE_WIDTHS,
                        "P07_PAD_ROLE")
        x = _conductor_coordinate(row.get("x"), "P07_PAD_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "P07_PAD_SCHEMA")
        _conductor_need(net_id in requested_set, "P07_PAD_NET")
        _conductor_need(pad_id not in p07_by_id, "P07_PAD_DUPLICATE")
        p07_by_id[pad_id] = (net_id, component_id, x, y, role)
        prior_role = p07_role_by_net.get(net_id)
        _conductor_need(prior_role is None or prior_role == role, "P07_NET_ROLE_CONFLICT")
        p07_role_by_net[net_id] = role
    _conductor_need(set(p07_role_by_net) == requested_set and
                    p07_role_by_net == role_by_net, "ROLE_IDENTITY")

    component_terminals = {}
    component_ids = set()
    for component in layout["components"]:
        _conductor_need(type(component) is dict, "COMPONENT_SCHEMA")
        _conductor_need(len(component_ids) < _CONDUCTOR_MAX_IDS,
                        "CONDUCTOR_COMPLEXITY")
        component_id = _conductor_id(component.get("componentId"), "COMPONENT_SCHEMA")
        _conductor_need(component_id not in component_ids, "COMPONENT_DUPLICATE")
        component_ids.add(component_id)
        terminals = component.get("terminals")
        _conductor_need(type(terminals) is list, "COMPONENT_TERMINALS")
        _conductor_need(len(terminals) <= _CONDUCTOR_MAX_IDS,
                        "CONDUCTOR_COMPLEXITY")
        terminal_ids = set()
        for terminal in terminals:
            _conductor_need(type(terminal) is dict, "COMPONENT_TERMINALS")
            terminal_id = _conductor_id(terminal.get("terminalId"), "COMPONENT_TERMINALS")
            _conductor_need(terminal_id not in terminal_ids, "COMPONENT_TERMINAL_DUPLICATE")
            terminal_ids.add(terminal_id)
        component_terminals[component_id] = terminal_ids

    pads_by_id = {}
    pad_nodes = {}
    pad_nodes_by_net = {net: [] for net in requested_set}
    shapes = []
    nodes = []
    parents = []
    ranks = []
    interlayer_links = 0

    def new_node(net_id):
        _conductor_need(net_id in requested_set, "CONDUCTOR_NET")
        index = len(nodes)
        nodes.append(net_id)
        parents.append(index)
        ranks.append(0)
        return index

    def find_node(index):
        root = index
        while parents[root] != root:
            root = parents[root]
        while parents[index] != index:
            following = parents[index]
            parents[index] = root
            index = following
        return root

    def union_nodes(first, second):
        first_root = find_node(first)
        second_root = find_node(second)
        if first_root == second_root:
            return False
        _conductor_need(nodes[first_root] == nodes[second_root], "FOREIGN_COPPER_SHORT")
        if ranks[first_root] < ranks[second_root]:
            first_root, second_root = second_root, first_root
        parents[second_root] = first_root
        if ranks[first_root] == ranks[second_root]:
            ranks[first_root] += 1
        return True

    def add_shape(node, net_id, layer, box, kind, owner):
        _conductor_need(len(shapes) < _CONDUCTOR_MAX_SHAPES, "CONDUCTOR_COMPLEXITY")
        shapes.append((node, net_id, layer, box, kind, owner))

    layout_pads = layout["pads"]
    _conductor_need(len(layout_pads) == len(p07_by_id), "PAD_SET_MISMATCH")
    for row in layout_pads:
        _conductor_exact_object(row, _CONDUCTOR_PAD_KEYS, "PAD_SCHEMA")
        pad_id = _conductor_id(row.get("padId"), "PAD_SCHEMA")
        net_id = _conductor_id(row.get("netId"), "PAD_SCHEMA")
        component_id = _conductor_id(row.get("componentId"), "PAD_SCHEMA")
        terminal_id = _conductor_id(row.get("terminalId"), "PAD_SCHEMA")
        x = _conductor_coordinate(row.get("x"), "PAD_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "PAD_SCHEMA")
        _conductor_need(pad_id not in pads_by_id, "PAD_DUPLICATE")
        _conductor_need(pad_id in p07_by_id, "PAD_SET_MISMATCH")
        _conductor_need(p07_by_id[pad_id][:4] == (net_id, component_id, x, y),
                        "PAD_IDENTITY")
        _conductor_need(net_id in requested_set, "PAD_NET")
        _conductor_need(component_id in component_terminals and
                        terminal_id in component_terminals[component_id],
                        "PAD_TERMINAL_IDENTITY")
        attachment = row.get("attachment")
        mounting = row.get("mountingSide")
        exposure = row.get("exposure")
        _conductor_need(attachment in _CONDUCTOR_ATTACHMENTS and
                        mounting in _CONDUCTOR_LAYERS and
                        exposure in _CONDUCTOR_EXPOSURES, "PAD_PHYSICAL_SCHEMA")
        _conductor_need(type(row.get("topCopper")) is bool and
                        type(row.get("bottomCopper")) is bool, "PAD_PHYSICAL_SCHEMA")
        expected_layers = ("TOP", "BOTTOM") if attachment == "PLATED_THROUGH_HOLE" else (mounting,)
        expected_top = "TOP" in expected_layers
        expected_bottom = "BOTTOM" in expected_layers
        _conductor_need(row["topCopper"] is expected_top and
                        row["bottomCopper"] is expected_bottom, "PAD_COPPER_FLAGS")
        pad_bounds = _conductor_rectangle(row.get("padBounds"), "PAD_BOUNDS")
        probe_bounds = _conductor_rectangle(row.get("probeBounds"), "PAD_PROBE_BOUNDS")
        _conductor_need(_conductor_contains_rect(probe_bounds, pad_bounds) and
                        _conductor_contains_point(pad_bounds, x, y), "PAD_BOUNDS")
        escape = row.get("escapeDirection")
        _conductor_need(type(escape) is list and len(escape) == 2, "PAD_ESCAPE")
        dx = _conductor_int(escape[0], -1, 1, "PAD_ESCAPE")
        dy = _conductor_int(escape[1], -1, 1, "PAD_ESCAPE")
        _conductor_need(abs(dx) + abs(dy) <= 1, "PAD_ESCAPE")
        escape_length = _conductor_int(row.get("escapeLength"), 0,
                                       2 * _CONDUCTOR_MAX_COORD, "PAD_ESCAPE")
        _conductor_need(escape_length == 0 or dx != 0 or dy != 0, "PAD_ESCAPE")
        _conductor_coordinate(x + dx * escape_length, "PAD_ESCAPE")
        _conductor_coordinate(y + dy * escape_length, "PAD_ESCAPE")
        pads_by_id[pad_id] = row
        pad_nodes[pad_id] = {}
        for layer in expected_layers:
            node = new_node(net_id)
            pad_nodes[pad_id][layer] = node
            pad_nodes_by_net[net_id].append(node)
            add_shape(node, net_id, layer, pad_bounds, "pad", pad_id)
        if attachment == "PLATED_THROUGH_HOLE":
            if union_nodes(pad_nodes[pad_id]["TOP"], pad_nodes[pad_id]["BOTTOM"]):
                interlayer_links += 1

    _conductor_need(set(pads_by_id) == set(p07_by_id), "PAD_SET_MISMATCH")
    for pad_id, (net_id, component_id, x, y, role) in p07_by_id.items():
        _conductor_need(role_by_net[net_id] == role, "ROLE_IDENTITY")

    holes = layout["holes"]
    hole_ids = set()
    hole_nodes = {}
    via_count = 0
    for row in holes:
        _conductor_exact_object(row, _CONDUCTOR_HOLE_KEYS, "HOLE_SCHEMA")
        hole_id = _conductor_id(row.get("id"), "HOLE_SCHEMA")
        _conductor_need(hole_id not in hole_ids, "HOLE_DUPLICATE")
        hole_ids.add(hole_id)
        kind = row.get("kind")
        exposure = row.get("exposure")
        _conductor_need(kind in _CONDUCTOR_HOLE_KINDS and
                        exposure in _CONDUCTOR_EXPOSURES, "HOLE_SCHEMA")
        net_id = row.get("netId")
        x = _conductor_coordinate(row.get("x"), "HOLE_SCHEMA")
        y = _conductor_coordinate(row.get("y"), "HOLE_SCHEMA")
        drill = _conductor_int(row.get("drillRadius"), 1,
                               _CONDUCTOR_MAX_COORD, "HOLE_RADIUS")
        land = _conductor_int(row.get("landRadius"), 1,
                              _CONDUCTOR_MAX_COORD, "HOLE_RADIUS")
        _conductor_need((kind == "NON_PLATED" and net_id is None and land == drill) or
                        (kind != "NON_PLATED" and type(net_id) is str and
                         net_id in requested_set and land > drill), "HOLE_NET_OR_RADIUS")
        if kind == "VIA":
            via_count += 1
        if kind == "NON_PLATED":
            continue
        box = _conductor_rectangle([x - land, y - land, 2 * land, 2 * land],
                                   "HOLE_BOUNDS")
        hole_nodes[hole_id] = {}
        for layer in _CONDUCTOR_LAYERS:
            node = new_node(net_id)
            hole_nodes[hole_id][layer] = node
            add_shape(node, net_id, layer, box, "hole", (hole_id, layer))
        if union_nodes(hole_nodes[hole_id]["TOP"], hole_nodes[hole_id]["BOTTOM"]):
            interlayer_links += 1
    _conductor_need(via_count == layout["viaCount"] and via_count <= maximum_vias,
                    "VIA_COUNT")

    trace_nodes = []
    trace_source_ids = set()
    total_trace_points = 0
    trace_layer_counts = {"TOP": 0, "BOTTOM": 0}
    role_trace_counts = dict((role, 0) for role in _CONDUCTOR_ROLE_WIDTHS)
    exposure_counts = {"EXPOSED": 0, "COVERED": 0}
    for row in layout["traces"]:
        _conductor_exact_object(row, _CONDUCTOR_TRACE_KEYS, "TRACE_SCHEMA")
        source_id = _conductor_id(row.get("sourceId"), "TRACE_SCHEMA")
        _conductor_need(source_id not in trace_source_ids, "TRACE_SOURCE_DUPLICATE")
        trace_source_ids.add(source_id)
        net_id = _conductor_id(row.get("netId"), "TRACE_SCHEMA")
        layer = row.get("layer")
        exposure = row.get("exposure")
        _conductor_need(layer in _CONDUCTOR_LAYERS and
                        exposure in _CONDUCTOR_EXPOSURES, "TRACE_LAYER_OR_EXPOSURE")
        _conductor_need(net_id in requested_set, "TRACE_NET")
        width = _conductor_int(row.get("width"), 1, 63, "TRACE_WIDTH")
        role = role_by_net[net_id]
        _conductor_need(width == _CONDUCTOR_ROLE_WIDTHS[role], "TRACE_WIDTH")
        xs = row.get("x")
        ys = row.get("y")
        _conductor_need(type(xs) is list and type(ys) is list and
                        len(xs) == len(ys) and 2 <= len(xs) <= _CONDUCTOR_MAX_SHAPES,
                        "TRACE_GEOMETRY")
        total_trace_points += len(xs)
        _conductor_need(total_trace_points <= _CONDUCTOR_MAX_TRACE_POINTS,
                        "CONDUCTOR_COMPLEXITY")
        points = []
        for px, py in zip(xs, ys):
            points.append((_conductor_coordinate(px, "TRACE_GEOMETRY"),
                           _conductor_coordinate(py, "TRACE_GEOMETRY")))
        start_id = row.get("startPadId")
        end_id = row.get("endPadId")
        _conductor_need(start_id is None or type(start_id) is str, "TRACE_ENDPOINT_PAD")
        _conductor_need(end_id is None or type(end_id) is str, "TRACE_ENDPOINT_PAD")
        trace_node = new_node(net_id)
        for endpoint, point in ((start_id, points[0]), (end_id, points[-1])):
            if endpoint is None:
                continue
            _conductor_need(endpoint in pads_by_id, "TRACE_ENDPOINT_PAD")
            pad = pads_by_id[endpoint]
            _conductor_need(pad["netId"] == net_id, "TRACE_ENDPOINT_NET")
            _conductor_need(point == (pad["x"], pad["y"]), "TRACE_ENDPOINT_COORDINATE")
            _conductor_need(layer in pad_nodes[endpoint], "TRACE_ENDPOINT_LAYER")
            union_nodes(trace_node, pad_nodes[endpoint][layer])
        segment_count = 0
        previous = points[0]
        for point in points[1:]:
            if point == previous:
                continue
            box = _conductor_stroke(previous[0], previous[1], point[0], point[1], width)
            add_shape(trace_node, net_id, layer, box, "trace", source_id)
            segment_count += 1
            previous = point
        _conductor_need(segment_count > 0, "TRACE_GEOMETRY")
        trace_nodes.append((trace_node, net_id))
        trace_layer_counts[layer] += 1
        role_trace_counts[role] += 1
        exposure_counts[exposure] += 1

    _conductor_need(len(shapes) <= _CONDUCTOR_MAX_SHAPES, "CONDUCTOR_COMPLEXITY")
    bucket_index = {}
    bucket_refs = 0
    contact_tests = 0
    contact_merges = 0
    hole_contact_keys = set()
    for shape_index, shape in enumerate(shapes):
        node, net_id, layer, box, kind, owner = shape
        x0 = box[0] // _CONDUCTOR_CELL
        y0 = box[1] // _CONDUCTOR_CELL
        x1 = (box[0] + box[2]) // _CONDUCTOR_CELL
        y1 = (box[1] + box[3]) // _CONDUCTOR_CELL
        cell_count = (x1 - x0 + 1) * (y1 - y0 + 1)
        _conductor_need(0 < cell_count <= _CONDUCTOR_MAX_CELLS_PER_SHAPE,
                        "CONDUCTOR_COMPLEXITY")
        candidate_indexes = set()
        for cell_x in range(x0, x1 + 1):
            for cell_y in range(y0, y1 + 1):
                for candidate in bucket_index.get((layer, cell_x, cell_y), ()):
                    candidate_indexes.add(candidate)
        for candidate in candidate_indexes:
            other = shapes[candidate]
            if other[0] == node:
                continue
            contact_tests += 1
            _conductor_need(contact_tests <= _CONDUCTOR_MAX_CONTACT_TESTS,
                            "CONDUCTOR_COMPLEXITY")
            if not _conductor_touch(box, other[3]):
                continue
            _conductor_need(net_id == other[1], "FOREIGN_COPPER_SHORT")
            if kind == "hole":
                hole_contact_keys.add(owner)
            if other[4] == "hole":
                hole_contact_keys.add(other[5])
            if union_nodes(node, other[0]):
                contact_merges += 1
        for cell_x in range(x0, x1 + 1):
            for cell_y in range(y0, y1 + 1):
                key = (layer, cell_x, cell_y)
                bucket = bucket_index.get(key)
                if bucket is None:
                    bucket = []
                    bucket_index[key] = bucket
                bucket.append(shape_index)
                bucket_refs += 1
                _conductor_need(bucket_refs <= _CONDUCTOR_MAX_BUCKET_REFS,
                                "CONDUCTOR_COMPLEXITY")

    for hole_id in hole_nodes:
        for layer in _CONDUCTOR_LAYERS:
            _conductor_need((hole_id, layer) in hole_contact_keys,
                            "PLATED_HOLE_UNATTACHED")

    net_pad_roots = {}
    net_pad_ids = {net: set() for net in requested_set}
    for pad_id, pad in pads_by_id.items():
        net_pad_ids[pad["netId"]].add(pad_id)
    for net_id in requested_set:
        _conductor_need(bool(net_pad_ids[net_id]), "NET_WITHOUT_PADS")
        roots = set(find_node(node) for node in pad_nodes_by_net[net_id])
        _conductor_need(len(roots) == 1, "NET_DISCONNECTED")
        net_pad_roots[net_id] = next(iter(roots))
    for node, net_id in trace_nodes:
        _conductor_need(find_node(node) == net_pad_roots[net_id], "ORPHAN_TRACE")
    for net_holes in hole_nodes.values():
        for node in net_holes.values():
            net_id = nodes[find_node(node)]
            _conductor_need(find_node(node) == net_pad_roots[net_id], "ORPHAN_HOLE")

    singleton_count = sum(1 for ids in net_pad_ids.values() if len(ids) == 1)
    return {
        "allNetsConnected": True,
        "netCount": len(requested_set),
        "connectedNetCount": len(requested_set),
        "singletonNetCount": singleton_count,
        "padCount": len(pads_by_id),
        "traceCount": len(layout["traces"]),
        "holeCount": len(holes),
        "viaCount": via_count,
        "traceLayerCounts": trace_layer_counts,
        "roleTraceCounts": role_trace_counts,
        "traceExposureCounts": exposure_counts,
        "sameLayerContactMerges": contact_merges,
        "interlayerLinkCount": interlayer_links,
        "conductorShapeCount": len(shapes),
    }


def _checked_complete_layout(layout, p07_pads, role_by_net, requested_net_ids):
    try:
        return validate_complete_layout(layout, p07_pads, role_by_net, requested_net_ids)
    except ConductorError as failure:
        raise ReaderError("CONDUCTOR_" + failure.code) from None


def _validate_canary_run(run, *, expected_kind, expected_cohort, expected_net_ids):
    exact_keys(run, ("kind", "initial", "ordering", "outcome", "terminalFailure",
                     "finalizers", "layout", "work"), "CANARY_RUN_SCHEMA")
    need(run["kind"] == expected_kind, "CANARY_RUN_KIND")
    initial, ordering = run["initial"], run["ordering"]
    need(type(initial) is dict and type(ordering) is dict
         and ordering.get("capturedNetIds") == expected_net_ids,
         "CANARY_CAPTURED_VECTOR")
    captured = {
        "netIds": expected_net_ids,
        "failedNetId": expected_cohort[0],
        "priorPromotedNetId": initial.get("priorPromotedNetId"),
        "owner": {
            "policy": initial.get("policy"),
            "maximumExpansions": initial.get("maximumExpansions"),
            "expandedAtFailure": 0,
            "maximumVias": initial.get("maximumVias"),
            "viaCost": initial.get("viaCost"),
            "policyTransitions": initial.get("policyTransitions"),
            "policyVias": initial.get("policyVias"),
            "branchOrderStrategy": initial.get("branchOrderStrategy"),
            "netOrderStrategy": initial.get("netOrderStrategy"),
            "roleWidths": initial.get("roleWidths"),
            "orderingStream": initial.get("capturedOrderingStream"),
        },
    }
    facts = _validate_run(run, expected_kind, captured, expected_cohort)
    return {"run": run, "netIds": expected_net_ids, "layout": run["layout"],
            **facts}

def _canary_roles(layout):
    need(type(layout) is dict and type(layout.get("traces")) is list,
         "CANARY_LAYOUT_SCHEMA")
    rules = layout.get("clearanceRules")
    widths = rules.get("roleWidths") if type(rules) is dict else None
    need(type(widths) is dict and widths == ROLE_WIDTHS, "CANARY_ROLE_WIDTHS")
    expected = {"NET_A": "HIGH_CURRENT", "NET_B": "CONTROL"}
    trace_widths = {}
    for trace in layout["traces"]:
        need(type(trace) is dict and type(trace.get("netId")) is str,
             "CANARY_TRACE_SCHEMA")
        need(is_int(trace.get("width"), 1, 63), "CANARY_TRACE_WIDTH")
        trace_widths.setdefault(trace["netId"], set()).add(trace.get("width"))
    need(set(trace_widths) == set(expected), "CANARY_NET_ROLES")
    for net, role in expected.items():
        need(trace_widths[net] == {widths[role]}, "CANARY_TRACE_WIDTH_CONSISTENCY")
    return expected


def _expected_canary_order(roles):
    need(type(roles) is dict and {"NET_A", "NET_B"}.issubset(set(roles))
         and all(type(role) is str and role in ROLE_PRIORITIES for role in roles.values()),
         "CANARY_PRIORITY_ROLES")
    # The owner sorts pass-2 nets by ascending role priority, then ID, and reverses the vector.
    return sorted(roles, key=lambda net: (ROLE_PRIORITIES[roles[net]], net), reverse=True)


def _bind_canary_priority_order(run_facts, roles):
    expected = _expected_canary_order(roles)
    need(type(run_facts) is dict and run_facts.get("netIds") == expected,
         "CANARY_PRIORITY_ORDER")
    return expected


def _validate_canary_document(doc):
    exact_keys(doc, ("schema", "status", "openPositive", "blockedNoFallback",
                     "activeExceptionCleanup", "stableTransform"), "CANARY_SCHEMA")
    need(doc["schema"] == "tsj-failure-cohort-canary-v1" and doc["status"] == "PASS",
         "CANARY_IDENTITY")

    open_case = doc["openPositive"]
    exact_keys(open_case, ("control", "trialDisposition"), "CANARY_OPEN_SCHEMA")
    open_ordering = open_case["control"].get("ordering") if type(open_case["control"]) is dict else None
    open_ids = open_ordering.get("capturedNetIds") if type(open_ordering) is dict else None
    need(type(open_ids) is list and all(type(item) is str and bool(item) for item in open_ids)
         and len(open_ids) == len(set(open_ids)) and set(open_ids) == {"NET_A", "NET_B"},
         "CANARY_OPEN_CAPTURED_NETS")
    opened = _validate_canary_run(open_case["control"],
                                  expected_kind="OBSERVED_FAILURE_CONTROL",
                                  expected_cohort=["NET_A"], expected_net_ids=open_ids)
    roles = _canary_roles(opened["layout"])
    need(opened["netIds"] == _expected_canary_order(roles),
         "CANARY_OPEN_COMPUTED_ORDER")
    expected_open_order = stable_cohort_order(open_ids, ["NET_A"])
    need(opened["ordering"]["requestedNetIds"] == expected_open_order
         and opened["outcome"]["disposition"] == "FOUND"
         and opened["terminalFailure"] is None
         and opened["ordering"]["traversedNetIds"] == ["NET_A", "NET_B"],
         "CANARY_OPEN_FIXTURE_IDENTITY")
    open_layout = opened["layout"]
    exact_keys(open_layout, _CONDUCTOR_LAYOUT_KEYS, "CANARY_LAYOUT_SCHEMA")
    need(type(open_layout.get("pads")) is list
         and is_int(open_layout["traceCount"], 2, 2)
         and is_int(open_layout["holeCount"], 0, 0)
         and is_int(open_layout["viaCount"], 0, 0)
         and len(open_layout["pads"]) == 4,
         "CANARY_OPEN_LAYOUT_COUNTS")
    expected_points = {
        "NET_A": {(260, 300), (500, 300)},
        "NET_B": {(260, 450), (500, 450)},
    }
    pads = []
    actual_points = {net: set() for net in expected_points}
    for row in open_layout["pads"]:
        need(type(row) is dict, "CANARY_PAD_ROW")
        role = roles.get(row.get("netId"))
        need(role is not None, "CANARY_PAD_NET")
        need(row.get("attachment") == "PLATED_THROUGH_HOLE"
             and row.get("topCopper") is True and row.get("bottomCopper") is True
             and is_int(row.get("x"), -100_000_000, 100_000_000)
             and is_int(row.get("y"), -100_000_000, 100_000_000),
             "CANARY_PAD_FIXTURE")
        actual_points[row["netId"]].add((row["x"], row["y"]))
        pads.append({"padId": row.get("padId"), "netId": row.get("netId"),
                     "componentId": row.get("componentId"), "role": role,
                     "x": row.get("x"), "y": row.get("y")})
    need(actual_points == expected_points, "CANARY_PAD_POINTS")
    layout_facts = _checked_complete_layout(open_layout, pads, roles, opened["netIds"])
    canary_capture = {"failedNetId": "NET_A", "netIds": open_ids}
    _validate_trial_disposition(open_case["trialDisposition"], None, canary_capture, opened)

    blocked = doc["blockedNoFallback"]
    exact_keys(blocked, ("control", "repeatedFeedback", "noFallback"),
               "CANARY_BLOCKED_SCHEMA")
    blocked_ids = ("NET_A", "NET_B", "BLOCK_E_NET", "BLOCK_N_NET",
                   "BLOCK_S_NET", "BLOCK_W_NET")
    blocked_run_value = blocked["control"]
    blocked_ordering = (blocked_run_value.get("ordering")
                        if type(blocked_run_value) is dict else None)
    actual_blocked_ids = (blocked_ordering.get("capturedNetIds")
                          if type(blocked_ordering) is dict else None)
    need(type(actual_blocked_ids) is list
         and all(type(item) is str and bool(item) for item in actual_blocked_ids)
         and set(actual_blocked_ids) == set(blocked_ids)
         and len(actual_blocked_ids) == len(blocked_ids),
         "CANARY_BLOCKED_CAPTURED_NETS")
    blocked_run = _validate_canary_run(
        blocked_run_value, expected_kind="OBSERVED_FAILURE_CONTROL",
        expected_cohort=["NET_A"], expected_net_ids=actual_blocked_ids)
    need(actual_blocked_ids == _expected_canary_order(BLOCKED_CANARY_ROLES)
         and blocked["noFallback"] is True
         and blocked_run["ordering"]["searchCount"] == 1
         and blocked_run["outcome"]["disposition"] == "NO_PATH"
         and blocked_run["outcome"]["reason"] == "NET_A:NO_PATH"
         and blocked_run["terminalFailure"]["failedNetId"] == "NET_A"
         and blocked_run["terminalFailure"]["searchIndex"] == 1
         and blocked_run["outcome"]["pass"] == 3
         and blocked_run["outcome"]["passes"] == 1
         and blocked_run["ordering"]["traversedNetIds"] == ["NET_A"]
         and blocked_run["layout"] is None,
         "CANARY_NO_FALLBACK_BINDING")
    repeated = blocked["repeatedFeedback"]
    exact_keys(repeated, ("capturedFailedNetId", "controlFailedNetId", "status", "reason",
                          "trialSessionCreations"), "CANARY_REPEATED_FEEDBACK_SCHEMA")
    need(repeated == {"capturedFailedNetId": "NET_A", "controlFailedNetId": "NET_A",
                      "status": "REJECTED_BEFORE_TRIAL", "reason": "REPEATED_FAILURE_ID",
                      "trialSessionCreations": 0}, "CANARY_REPEATED_FEEDBACK")

    exception = doc["activeExceptionCleanup"]
    exception_keys = ("exceptionClass", "message", "sameThrowable", "callbackCalls",
                      "expandedAtThrow", "searchPresent", "branchPresent",
                      "ownerDiagnosticFailureSame", "sessionReleased", "searchReleased",
                      "candidateReleased", "layoutReleased", "boardReleased",
                      "adapterSearchAliasesReleased", "referencesReleased", "advanceCalls",
                      "failureObservationUnits", "terminalEvidenceUnits", "ownerUnits",
                      "wrapperUnits", "attemptWorkAccounted", "cleanupNanos")
    exact_keys(exception, exception_keys, "CANARY_EXCEPTION_SCHEMA")
    exception_bools = ("sameThrowable", "searchPresent", "branchPresent",
                       "ownerDiagnosticFailureSame", "sessionReleased", "searchReleased",
                       "candidateReleased", "layoutReleased", "boardReleased",
                       "adapterSearchAliasesReleased", "referencesReleased")
    need(type(exception["exceptionClass"]) is str
         and exception["exceptionClass"] == "java.lang.IllegalStateException"
         and exception["message"] == "FAILED_FIRST_ACTIVE_BRANCH_CANARY"
         and all(exception[name] is True for name in exception_bools)
         and is_int(exception["callbackCalls"], 1, 1)
         and is_int(exception["expandedAtThrow"], 1, MAX_EXPANSIONS)
         and is_int(exception["cleanupNanos"], 1, MAX_CLEANUP_NS),
         "CANARY_EXCEPTION")
    need(is_int(exception["advanceCalls"], 1, 1)
         and is_int(exception["failureObservationUnits"], 1, 1)
         and is_int(exception["terminalEvidenceUnits"], 0, 0)
         and is_int(exception["ownerUnits"], 0, MAX_GROUP_UNITS)
         and is_int(exception["wrapperUnits"], 1, MAX_GROUP_UNITS)
         and exception["ownerUnits"] + exception["wrapperUnits"] <= MAX_GROUP_UNITS
         and exception["attemptWorkAccounted"] is True
         and exception["wrapperUnits"] >= 2 + (1 if exception["ownerUnits"] == 0 else 0),
         "CANARY_EXCEPTION_WORK")

    stable = doc["stableTransform"]
    stable_keys = ("source", "cohort", "requested", "noChangeSource", "noChangeCohort",
                   "noChangeRequested", "unrelatedOrderPreserved", "noChangeSkipped",
                   "duplicateCohortRejected", "missingCohortRejected", "unknownSourceRejected",
                   "missingFeedbackRejected", "unknownFeedbackRejected",
                   "repeatedFeedbackRejected", "rejectedFeedbackTrialSessions",
                   "noChangeTrialSessions")
    exact_keys(stable, stable_keys, "CANARY_STABLE_SCHEMA")
    source = ["NET_A", "NET_B", "NET_C", "NET_D"]
    cohort = ["NET_C", "NET_A"]
    requested = ["NET_C", "NET_A", "NET_B", "NET_D"]
    no_change_source = ["NET_C", "NET_A", "NET_B", "NET_D"]
    no_change_cohort = ["NET_C", "NET_A"]
    stable_bools = ("unrelatedOrderPreserved", "noChangeSkipped", "duplicateCohortRejected",
                    "missingCohortRejected", "unknownSourceRejected", "missingFeedbackRejected",
                    "unknownFeedbackRejected", "repeatedFeedbackRejected")
    need(stable["source"] == source and stable["cohort"] == cohort
         and stable["requested"] == stable_cohort_order(source, cohort) == requested
         and stable["noChangeSource"] == no_change_source
         and stable["noChangeCohort"] == no_change_cohort
         and stable["noChangeRequested"] == no_change_source
         and all(stable[name] is True for name in stable_bools)
         and is_int(stable["rejectedFeedbackTrialSessions"], 0, 0)
         and is_int(stable["noChangeTrialSessions"], 0, 0),
         "CANARY_STABLE_TRANSFORM")
    return {"openPositive": layout_facts, "blockedNoFallback": True,
            "activeExceptionCleanup": True, "stableTransform": True}

def validate_canary(manifest_path: str | Path, manifest_sha: str,
                    parent_path: str | Path) -> dict:
    context = validate_manifest(manifest_path, manifest_sha)
    parent, process, marker, process_result, _ = _validate_call(
        context, parent_path, canary=True)
    del parent, process_result
    canary_facts = _validate_canary_document(marker)
    before = sha(_read_regular(Path(__file__).resolve(), 8 * 1024 * 1024, "READER_SELF_READ"))
    try:
        refreshed = context["builder"].recheck_inputs(context)
    except Exception:
        raise ReaderError("FINAL_INPUT_RECHECK") from None
    need(refreshed.get("sourceSha") == manifest_sha
         and sha(_read_regular(Path(__file__).resolve(), 8 * 1024 * 1024, "READER_SELF_RECHECK"))
             == before, "FINAL_INPUT_RECHECK")
    return {"manifestSha256": manifest_sha, "readerSha256": before,
            "canaryFacts": canary_facts, "stdoutBytes": len(process["Stdout"].encode("utf-8"))}


def validate_run(manifest_path: str | Path, manifest_sha: str,
                 parent_path: str | Path, data_path: str | Path) -> dict:
    context = validate_manifest(manifest_path, manifest_sha)
    base = context["baseReader"]
    parent, process, marker, process_result, data_dir = _validate_call(
        context, parent_path, canary=False, data_path=data_path)
    result_raw = base._read(data_dir / "result.json", MAX_JSON_BYTES, "RESULT_READ")
    op1_raw = base._read(data_dir / "op-1.jsonl", MAX_RECEIPT_BYTES, "OP1_READ")
    need(sha(result_raw) == marker.get("resultSha256")
         and is_int(marker.get("resultBytes"), len(result_raw), len(result_raw)),
         "RESULT_MARKER_HASH")
    doc = strict_json_line(result_raw)
    try:
        parsed = base._parse_operation1(op1_raw, doc.get("operation1"), process["Arguments"][5],
                                        manifest_sha, WIRE_SHA256, context["p07Reader"])
    except Exception:
        raise ReaderError("OP1_P07_VALIDATION") from None
    validated = validate_semantics(doc, parsed, op1_raw, manifest_sha, WIRE_SHA256,
                                   process["Arguments"][5], base)
    _validate_marker(marker, result_raw, doc)
    own_sha = sha(_read_regular(Path(__file__).resolve(), 8 * 1024 * 1024, "READER_SELF_READ"))
    try:
        refreshed = context["builder"].recheck_inputs(context)
    except Exception:
        raise ReaderError("FINAL_INPUT_RECHECK") from None
    need(refreshed.get("sourceSha") == manifest_sha
         and refreshed.get("wireRaw") == context["wireRaw"]
         and sha(_read_regular(Path(__file__).resolve(), 8 * 1024 * 1024,
                               "READER_SELF_RECHECK")) == own_sha,
         "FINAL_INPUT_RECHECK")
    return {"validated": validated, "context": context, "parent": parent, "marker": marker,
            "resultSha256": sha(result_raw), "op1Sha256": sha(op1_raw),
            "resultBytes": len(result_raw), "op1Bytes": len(op1_raw),
            "processResult": process_result, "readerSha256": own_sha}


def public_result(verified, manifest_sha):
    validated = verified["validated"]
    control = validated["control"]
    trial = validated["trial"]
    return {
        "schema": "tsj-failure-cohort-reader-v1",
        "status": "PASS_OBSERVED_FAILURE_COHORT_DIAGNOSIS_ONLY",
        "manifestSha256": manifest_sha,
        "readerSha256": verified["readerSha256"],
        "resultSha256": verified["resultSha256"],
        "operation1Sha256": verified["op1Sha256"],
        "trialDisposition": validated["trialDisposition"]["reason"],
        "netCount": len(validated["capturedOrder"]["netIds"]),
        "controlDisposition": control["outcome"]["disposition"],
        "trialOutcomeDisposition": None if trial is None else trial["outcome"]["disposition"],
        "controlTraversedCount": len(control["ordering"]["traversedNetIds"]),
        "trialTraversedCount": None if trial is None else len(trial["ordering"]["traversedNetIds"]),
        "controlLayoutConnectivity": (
            None if validated["layoutFacts"]["control"] is None else
            validated["layoutFacts"]["control"]["allNetsConnected"]),
        "trialLayoutConnectivity": (None if validated["layoutFacts"]["trial"] is None else
                                     validated["layoutFacts"]["trial"]["allNetsConnected"]),
        "layoutQualification": False,
        "routingTreatmentSelected": False,
        "productionAdopted": False,
        "feasibilityClaimed": False,
        "playable100Accepted": False,
    }


def public_canary_result(verified, manifest_sha):
    facts = verified["canaryFacts"]
    return {"schema": "tsj-failure-cohort-reader-v1",
            "status": "PASS_OWNER_CANARY_ONLY", "manifestSha256": manifest_sha,
            "readerSha256": verified["readerSha256"],
            "stdoutBytes": verified["stdoutBytes"],
            "openPositiveIndependentConnectivity":
                facts["openPositive"]["allNetsConnected"],
            "blockedNoFallback": facts["blockedNoFallback"],
            "activeExceptionCleanup": facts["activeExceptionCleanup"],
            "stableCohortTransform": facts["stableTransform"],
            "layoutQualification": False, "routingTreatmentSelected": False,
            "productionAdopted": False, "feasibilityClaimed": False,
            "playable100Accepted": False}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="read_failure_cohort.py")
    parser.add_argument("--canary", action="store_true")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--manifest-sha", required=True)
    parser.add_argument("--parent", required=True)
    parser.add_argument("--data")
    args = parser.parse_args(argv)
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        print("BF_ISOLATION_REQUIRED", file=sys.stderr)
        return 2
    try:
        if args.canary:
            need(args.data is None, "CANARY_ARGUMENTS")
            verified = validate_canary(args.manifest, args.manifest_sha, args.parent)
            out = public_canary_result(verified, args.manifest_sha)
        else:
            need(args.data is not None, "DATA_ARGUMENT_REQUIRED")
            verified = validate_run(args.manifest, args.manifest_sha, args.parent, args.data)
            out = public_result(verified, args.manifest_sha)
        sys.stdout.write(json.dumps(out, ensure_ascii=True, allow_nan=False,
                                    separators=(",", ":")) + "\n")
        return 0
    except ReaderError as exc:
        sys.stderr.write(exc.code + "\n")
        return 2


def validate_semantics(doc: dict, parsed_op1: dict, op1_raw: bytes,
                       manifest_sha: str, wire_sha: str, run_id: str,
                       base_reader=None) -> dict:
    exact_keys(doc, ("schema", "mode", "runId", "finalRunManifestSha256",
                     "wireContractSha256", "operation1", "reference", "source",
                     "capturedOrder", "control", "trial", "trialDisposition", "work"),
               "RESULT_SCHEMA")
    need(doc["schema"] == RESULT_SCHEMA and doc["mode"] == "OBSERVED_FAILURE_COHORT"
         and doc["runId"] == run_id and doc["finalRunManifestSha256"] == manifest_sha
         and doc["wireContractSha256"] == wire_sha, "RESULT_IDENTITY")
    metadata_tree_minimum = _result_tree_minimum_units(doc)
    operation = doc["operation1"]
    _validate_operation1(operation, parsed_op1, op1_raw, manifest_sha, wire_sha, run_id)
    reference = doc["reference"]
    _validate_reference(reference, parsed_op1)
    need(type(base_reader) is types.ModuleType, "P07_READER_CONTEXT")
    pad_data = _validate_source(doc["source"], parsed_op1, base_reader)
    captured = _validate_captured_order(doc["capturedOrder"], parsed_op1, pad_data)
    control_cohort = [captured["failedNetId"]]
    control = _validate_run(doc["control"], "OBSERVED_FAILURE_CONTROL", captured,
                            control_cohort)
    disposition = _validate_trial_disposition(doc["trialDisposition"], doc["trial"],
                                              captured, control)
    trial = (_validate_run(disposition["trial"], "OBSERVED_FAILURE_COHORT_TRIAL", captured,
                           disposition["cohortNetIds"])
             if disposition["trial"] is not None else None)
    if trial is not None:
        need(trial["expectedOrder"] == disposition["requestedNetIds"], "TRIAL_ORDER")
    work = _validate_work(doc["work"], operation, parsed_op1, reference, control, trial,
                          metadata_tree_minimum)
    layout_facts = {"control": None, "trial": None}
    if control["layout"] is not None:
        layout_facts["control"] = _checked_complete_layout(
            control["layout"], parsed_op1["pads"], pad_data["roleByNet"], captured["netIds"])
    if trial is not None and trial["layout"] is not None:
        layout_facts["trial"] = _checked_complete_layout(
            trial["layout"], parsed_op1["pads"], pad_data["roleByNet"], captured["netIds"])
    return {"operation1": operation, "reference": reference, "source": doc["source"],
            "capturedOrder": doc["capturedOrder"], "control": control, "trial": trial,
            "trialDisposition": doc["trialDisposition"], "work": doc["work"], "workFacts": work,
            "layoutFacts": layout_facts}


if __name__ == "__main__":
    raise SystemExit(main())
