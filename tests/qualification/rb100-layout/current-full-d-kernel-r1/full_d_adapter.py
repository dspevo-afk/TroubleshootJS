"""Semantic adapter template for the generated full-D V2 pure owner.

Do not load this template as the accepted owner. The pinned source generator
removes these three fixed dependency imports, applies deterministic per-source
AST namespaces, and emits a self-contained module. Host, filesystem, process,
clock, and native-capture authority stays outside both template and output.
"""
from __future__ import annotations

import hashlib
import json
import re
from types import MappingProxyType

from source.context_codec import (
    CONTEXT_SCHEMA as _CONTEXT_SCHEMA,
    NATIVE_PROJECTION_KEYS as _NATIVE_PROJECTION_KEYS,
    OWNER_KEYS as _OWNER_KEYS,
    PAD_IDENTITY_KEYS as _CONTEXT_PAD_IDENTITY_KEYS,
    PAD_LAND_KEYS as _PAD_LAND_KEYS,
    PHYSICAL_CONTEXT_KEYS as _PHYSICAL_CONTEXT_KEYS,
    PRIOR_KEYS as _CONTEXT_PRIOR_KEYS,
    PRIOR_RECEIPT_KEYS as _CONTEXT_PRIOR_RECEIPT_KEYS,
    P07_PAD_KEYS as _P07_PAD_KEYS,
    SEED_KEYS as _SEED_KEYS,
    SOURCE_IDENTITY_KEYS as _SOURCE_IDENTITY_KEYS,
    TOP_KEYS as _CONTEXT_TOP_KEYS,
    TRIAL_DISPOSITION_KEYS as _TRIAL_DISPOSITION_KEYS,
    ContextCodecError as _ContextCodecError,
    canonical_context as _canonical_context,
    context_sha256 as _context_sha256,
    validate_context as _validate_context,
)
from source.observation_v2_kernel import (
    ObservationV2Error as _ObservationV2Error,
    ObservationV2Kernel as _ObservationV2Kernel,
)
from source.prior_kernel import (
    N_ReaderError as _PriorReaderError,
    result_tree_minimum_units as _result_tree_minimum_units,
    stable_cohort_order as _stable_cohort_order,
    validate_operation as _validate_operation,
    validate_prior_run as _validate_prior_run,
)


PRIOR_KERNEL_SHA256 = "f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873"
CONTEXT_CODEC_SHA256 = "c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27"
OBSERVATION_KERNEL_SHA256 = "502ff2e1dec1c4d01ddb4e2841aae6f7e4073a62975c1cc9668a09cf5a52b89c"
FULL_CONSUMER_SOURCE_SHA256 = "f23a965ef3bc28a78ef06158efb9c7c50c375dc164d7e2840e5d350f51db58c3"
WIRE_CONTRACT_SHA256 = "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed"

RESULT_SCHEMA = "tsj-cohort-terminal-result-v2"
RESULT_MODE = "OBSERVED_COHORT_TERMINAL_TELEMETRY"
RESULT_STATUS = "COMPLETED_DIAGNOSTIC_ONLY"
QUALIFIED_PRIOR_SCHEMA = "tsj-cohort-qualified-prior-v2"
MARKER_SCHEMA = "tsj-cohort-terminal-marker-v2"
MARKER_STATUS = "COMPLETED_DIAGNOSTIC"
CLEANUP_SCHEMA = "tsj-cohort-terminal-cleanup-v1"

RUNTIME_IDENTITY_KEYS = (
    "runId", "sourceManifestSha256", "wireContractSha256",
    "inputContextSha256",
)
RESULT_KEYS = (
    "schema", "mode", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "qualifiedPrior",
    "originalCapture", "cohort", "arms", "work", "cleanup",
)
QUALIFIED_PRIOR_KEYS = (
    "schema", "inputContextSha256", *_CONTEXT_PRIOR_KEYS,
)
MARKER_KEYS = (
    "schema", "status", "runId", "sourceManifestSha256",
    "wireContractSha256", "inputContextSha256", "resultSha256",
    "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
    "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup",
)
RECEIPT_KEYS = (
    "receiptPath", "receiptBytes", "receiptSha256", "receiptOrdinal",
    "lineageOrdinal",
)
CAPTURE_KEYS = (
    "schema", "captureId", "sourceManifestSha256", "operation", "receipt",
    "request", "seeds", "ownerSettings", "priorPromotedNetId", "netIds",
    "template", "padIdentity", "ownersReleased", "contextRestored",
    "cleanupNanos",
)
CAPTURE_REQUEST_KEYS = ("requestIndex", "selectedAttempt", "pass")
CAPTURE_OWNER_KEYS = (
    "policy", "maximumExpansions", "maximumVias", "viaCost",
    "policyTransitions", "policyVias", "branchOrderStrategy",
    "netOrderStrategy", "roleWidths", "orderingStream",
)
TEMPLATE_KEYS = (
    "geometryBeforeSha256", "geometryAfterSha256", "effectsBeforeSha256",
    "effectsAfterSha256", "traceCount", "holeCount",
)
CAPTURE_PAD_IDENTITY_KEYS = ("schema", "padCount", "padLandSha256")
COHORT_KEYS = ("netIds", "requestedNetIds")
ARM_KEYS = ("armRole", "armId", "freshRun", "observation", "cleanup")
ARM_CLEANUP_KEYS = (
    "schema", "adapterSessionAliasDropped", "adapterSearchAliasDropped",
    "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
    "adapterBoardTemplateAliasesDropped", "sourceGeometryBeforeSha256",
    "sourceGeometryAfterSha256", "contextRestored", "cleanupNanos",
)
WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
    "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
    "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits",
    "setupGroups", "setupCopiedEntries", "setupCopyChunks",
    "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
    "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
    "originalCaptureGroups", "originalCaptureCopiedEntries",
    "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits",
    "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
    "observeObserverUnits", "observerUnits", "meterUnits", "metadataUnits",
    "metadataGroups", "metadataCopiedEntries", "metadataCopyChunks",
    "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit",
)
TOP_CLEANUP_KEYS = (
    "schema", "originalCaptureOwnersReleased", "originalCaptureContextRestored",
    "nullArmAliasesDropped", "observeArmAliasesDropped",
    "sourceGeometryUnchanged", "effectsScope",
    "originalCaptureCopyEffectsUnchanged", "contextRestored", "cleanupNanos",
)
R8_P07_PAD_KEYS = (
    "record", "group", "padId", "netId", "componentId", "x", "y", "role",
)
R8_PAD_PHYSICAL_KEYS = ("netId", "componentId", "x", "y", "role")

MAX_JSON_LINE = 1_048_576
MAX_MARKER_BYTES = 656
MAX_MARKER_TRANSPORT_BYTES = 722
MAX_OPERATION1_BYTES = 8 * 1024 * 1024
MAX_TOTAL_NS = 90_000_000_000
MAX_CLEANUP_NS = 120_000_000_000
MAX_GROUPS = 640
MAX_GROUP_NS = 5_000_000_000
MAX_GROUP_UNITS = 32_768
MAX_EXPANSIONS = 1_000_000
MAX_COPY_ENTRIES = 1_048_576
COPY_CHUNK = 4096
EXPORT_DIGEST_BYTES = 32
MIN_SIGNED_LONG = -(2**63)
MAX_SIGNED_LONG = 2**63 - 1
MAX_INT = 2**31 - 1
MIN_INT = -(2**31)
MAX_BODY_DEPTH = 64
MAX_BODY_VISITS = 1_000_000
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
SHA_RE = re.compile(r"[0-9a-f]{64}\Z")


class ReaderError(ValueError):
    """Path-free validation error with a stable code."""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code if code.startswith("BF_") else "BF_" + code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise ReaderError(code)


def exact_keys(value, keys, code: str) -> None:
    need(type(value) is dict and set(value) == set(keys), code)


def ordered_keys(value, keys, code: str) -> None:
    exact_keys(value, keys, code)
    need(tuple(value) == tuple(keys), code + "_ORDER")


def _typed_equal(actual, expected) -> bool:
    """Compare JSON values without Python's bool/int equality coercion."""
    if type(actual) is not type(expected):
        return False
    if type(expected) is dict:
        return (actual.keys() == expected.keys()
                and all(_typed_equal(actual[key], expected[key]) for key in expected))
    if type(expected) is list:
        return (len(actual) == len(expected)
                and all(_typed_equal(left, right)
                        for left, right in zip(actual, expected)))
    return actual == expected


def is_int(value, low: int = 0, high: int = MAX_SIGNED_LONG) -> bool:
    return type(value) is int and low <= value <= high


def is_sha(value) -> bool:
    return type(value) is str and SHA_RE.fullmatch(value) is not None


def is_uuid(value) -> bool:
    return type(value) is str and UUID_RE.fullmatch(value) is not None


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


def _full_domain(value, code: str) -> None:
    visits = 0
    stack = [(value, 0)]
    while stack:
        node, depth = stack.pop()
        visits += 1
        need(visits <= MAX_BODY_VISITS and depth <= MAX_BODY_DEPTH, code)
        if node is None or type(node) is bool:
            continue
        if type(node) is int:
            need(MIN_SIGNED_LONG <= node <= MAX_SIGNED_LONG, code)
            continue
        if type(node) is str:
            need(len(node) <= 256 and all(0x20 <= ord(char) <= 0x7e for char in node), code)
            continue
        if type(node) is list:
            stack.extend((child, depth + 1) for child in reversed(node))
            continue
        if type(node) is dict:
            for key, child in reversed(tuple(node.items())):
                need(type(key) is str and len(key) <= 256
                     and all(0x20 <= ord(char) <= 0x7e for char in key), code)
                stack.append((child, depth + 1))
                visits += 1
                need(visits <= MAX_BODY_VISITS, code)
            continue
        raise ReaderError(code)


def _strict_json_line_core(raw: bytes, code: str) -> dict:
    need(type(raw) is bytes and 2 <= len(raw) <= MAX_JSON_LINE
         and raw.endswith(b"\n") and b"\r" not in raw
         and not raw.startswith(b"\xef\xbb\xbf"), code)
    body = raw[:-1]
    need(body and b"\n" not in body, code)
    try:
        text = body.decode("utf-8", errors="strict")
        value = json.loads(text, object_pairs_hook=_pairs,
                           parse_constant=_reject_constant)
    except (UnicodeError, json.JSONDecodeError, ValueError, TypeError, RecursionError):
        raise ReaderError(code) from None
    need(type(value) is dict, code)
    _full_domain(value, code)
    return value


def _canonical_json_line(value, raw: bytes, max_bytes: int, code: str) -> int:
    need(type(raw) is bytes and len(raw) >= 2 and len(raw) <= max_bytes
         and raw.endswith(b"\n") and b"\r" not in raw
         and not raw.startswith(b"\xef\xbb\xbf")
         and b"\n" not in raw[:-1], code + "_FRAMING")
    _full_domain(value, code + "_DOMAIN")
    try:
        canonical = (json.dumps(value, ensure_ascii=True, allow_nan=False,
                                separators=(",", ":"), sort_keys=False)
                     .encode("ascii") + b"\n")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise ReaderError(code + "_ENCODING") from None
    need(raw == canonical, code + "_CANONICAL")
    return len(raw)


def _validate_context_order(context) -> None:
    ordered_keys(context, _CONTEXT_TOP_KEYS, "CONTEXT_KEYS")
    projection = context["nativeProjection"]
    ordered_keys(projection, _NATIVE_PROJECTION_KEYS, "NATIVE_PROJECTION_KEYS")
    prior = projection["prior"]
    ordered_keys(prior, _CONTEXT_PRIOR_KEYS, "PRIOR_KEYS")
    ordered_keys(prior["priorOriginalReceipt"], _CONTEXT_PRIOR_RECEIPT_KEYS,
                 "PRIOR_RECEIPT_KEYS")
    ordered_keys(prior["trialDisposition"], _TRIAL_DISPOSITION_KEYS,
                 "TRIAL_DISPOSITION_KEYS")
    capture = projection["capture"]
    ordered_keys(capture, (
        "cohortNetIds", "capturedNetIds", "trialDisposition", "controlDisposition",
        "trialOutcome", "passIndex", "failedNetId", "priorPromotedNetId",
        "liveSearchIndex",
    ), "CAPTURE_KEYS")
    ordered_keys(projection["ownerSettings"], _OWNER_KEYS, "OWNER_KEYS")
    ordered_keys(projection["seedValues"], _SEED_KEYS, "SEED_KEYS")
    source_identity = projection["sourceIdentity"]
    ordered_keys(source_identity, _SOURCE_IDENTITY_KEYS, "SOURCE_IDENTITY_KEYS")
    ordered_keys(source_identity["padLandIdentity"], _CONTEXT_PAD_IDENTITY_KEYS,
                 "PAD_IDENTITY_KEYS")
    physical = context["physicalContext"]
    ordered_keys(physical, _PHYSICAL_CONTEXT_KEYS, "PHYSICAL_CONTEXT_KEYS")
    need(type(physical["padLands"]) is list
         and type(physical["qualifiedP07Pads"]) is list,
         "PHYSICAL_CONTEXT_ROWS")
    for row in physical["padLands"]:
        ordered_keys(row, _PAD_LAND_KEYS, "PAD_LAND_KEYS")
    for row in physical["qualifiedP07Pads"]:
        ordered_keys(row, _P07_PAD_KEYS, "P07_PAD_KEYS")


def _validate_runtime_identity(runtime_identity, context) -> bytes:
    ordered_keys(runtime_identity, RUNTIME_IDENTITY_KEYS, "RUNTIME_IDENTITY")
    need(is_uuid(runtime_identity["runId"])
         and all(is_sha(runtime_identity[name]) for name in (
             "sourceManifestSha256", "wireContractSha256", "inputContextSha256")),
         "RUNTIME_IDENTITY_FIELDS")
    need(runtime_identity["wireContractSha256"] == WIRE_CONTRACT_SHA256,
         "RUNTIME_WIRE_IDENTITY")
    try:
        canonical = _canonical_context(context)
        measured = _context_sha256(context)
    except _ContextCodecError as failure:
        raise ReaderError(failure.code) from None
    except RecursionError:
        raise ReaderError("RUNTIME_CONTEXT_DEPTH") from None
    need(type(canonical) is bytes and len(canonical) <= MAX_JSON_LINE
         and sha(canonical) == measured
         and measured == runtime_identity["inputContextSha256"],
         "RUNTIME_CONTEXT_BINDING")
    _validate_context_order(context)
    return canonical


def _prior_semantics(context):
    projection = context["nativeProjection"]
    capture = projection["capture"]
    settings = projection["ownerSettings"]
    trial = capture["trialDisposition"]
    captured_ids = capture["capturedNetIds"]
    expected_owner = {
        "policy": settings["boardMode"],
        "maximumExpansions": settings["maxExpansions"],
        "expandedAtFailure": settings["expandedAtFailure"],
        "maximumVias": settings["maxVias"],
        "viaCost": settings["viaCost"],
        "policyTransitions": settings["maxTransitions"],
        "policyVias": settings["maxVias"],
        "branchOrderStrategy": settings["branchStrategy"],
        "netOrderStrategy": settings["netStrategy"],
        "roleWidths": settings["roleWidthsEnabled"],
        "orderingStream": settings["orderingStreamEnabled"],
    }
    need(capture["passIndex"] == 2 and type(captured_ids) is list
         and len(captured_ids) == 60 and len(set(captured_ids)) == 60
         and capture["failedNetId"] in captured_ids
         and capture["priorPromotedNetId"] in captured_ids
         and is_int(capture["liveSearchIndex"], 1, 60)
         and captured_ids[capture["liveSearchIndex"] - 1] == capture["failedNetId"],
         "PRIOR_VECTOR_BINDING")
    need(capture["controlDisposition"] == "NO_PATH"
         and capture["trialOutcome"] == "CAP_REJECTED"
         and trial["status"] == "RUN" and trial["reason"] == "TRIAL_EXECUTED"
         and trial["feedbackValidated"] is True
         and trial["cohortNetIds"] == capture["cohortNetIds"]
         and trial["capturedFailedNetId"] == capture["cohortNetIds"][0]
         and trial["controlFailedNetId"] == capture["cohortNetIds"][1],
         "PRIOR_COHORT_BINDING")
    need(capture["priorPromotedNetId"] == "D_SENSE", "PRIOR_PROMOTION_BINDING")
    captured = {
        "owner": expected_owner,
        "netIds": captured_ids,
        "failedNetId": capture["failedNetId"],
        "priorPromotedNetId": capture["priorPromotedNetId"],
    }
    return context["physicalContext"]["padLands"], captured


def _prior_grid_dimensions(context):
    physical = context["physicalContext"]
    width, height = physical["gridWidth"], physical["gridHeight"]
    need(is_int(width, 1, 250_000) and is_int(height, 1, 250_000)
         and width * height <= 250_000, "PRIOR_GRID_BINDING")
    return width, height


def _expected_prior_ref(context, runtime_identity):
    prior = context["nativeProjection"]["prior"]
    result = {
        "schema": QUALIFIED_PRIOR_SCHEMA,
        "inputContextSha256": runtime_identity["inputContextSha256"],
    }
    result.update(prior)
    ordered_keys(result, QUALIFIED_PRIOR_KEYS, "QUALIFIED_PRIOR_SCHEMA")
    return result


def _pad_lookup(pads):
    need(type(pads) is list and len(pads) == 246, "PAD_CENSUS")
    by_id = {}
    by_net = {}
    for row in pads:
        need(type(row) is dict, "PAD_ROW")
        pad_id, net_id = row.get("padId"), row.get("netId")
        x, y = row.get("x"), row.get("y")
        need(type(pad_id) is str and bool(pad_id) and type(net_id) is str
             and bool(net_id) and is_int(x, MIN_INT, MAX_INT)
             and is_int(y, MIN_INT, MAX_INT) and pad_id not in by_id,
             "PAD_ROW_BINDING")
        by_id[pad_id] = (net_id, x, y)
        by_net.setdefault(net_id, set()).add(pad_id)
    return by_id, by_net


def _validate_original_capture(value, context, captured, op1_raw,
                               runtime_identity):
    exact_keys(value, CAPTURE_KEYS, "ORIGINAL_CAPTURE_SCHEMA")
    need(value["schema"] == "tsj-cohort-terminal-original-capture-v1"
         and is_uuid(value["captureId"])
         and value["captureId"] != runtime_identity["runId"]
         and value["sourceManifestSha256"] == runtime_identity["sourceManifestSha256"]
         and value["operation"] == "AUTHORED", "ORIGINAL_CAPTURE_IDENTITY")
    receipt = value["receipt"]
    exact_keys(receipt, RECEIPT_KEYS, "ORIGINAL_RECEIPT_SCHEMA")
    need(receipt["receiptPath"] == "op-1.jsonl"
         and is_int(receipt["receiptBytes"], len(op1_raw), len(op1_raw))
         and receipt["receiptSha256"] == sha(op1_raw)
         and is_int(receipt["receiptOrdinal"], 1, MAX_INT)
         and is_int(receipt["lineageOrdinal"], 1, MAX_INT)
         and receipt["receiptOrdinal"] == 307
         and receipt["lineageOrdinal"] == 77, "ORIGINAL_RECEIPT_BINDING")
    exact_keys(value["request"], CAPTURE_REQUEST_KEYS, "ORIGINAL_REQUEST_SCHEMA")
    need(_typed_equal(value["request"],
                      {"requestIndex": 0, "selectedAttempt": 4, "pass": 2}),
         "ORIGINAL_REQUEST_BINDING")
    projection = context["nativeProjection"]
    need(_typed_equal(value["seeds"], projection["seedValues"]),
         "ORIGINAL_SEED_BINDING")
    settings = projection["ownerSettings"]
    expected_owner = {
        "policy": settings["boardMode"],
        "maximumExpansions": settings["maxExpansions"],
        "maximumVias": settings["maxVias"],
        "viaCost": settings["viaCost"],
        "policyTransitions": settings["maxTransitions"],
        "policyVias": settings["maxVias"],
        "branchOrderStrategy": settings["branchStrategy"],
        "netOrderStrategy": settings["netStrategy"],
        "roleWidths": settings["roleWidthsEnabled"],
        "orderingStream": settings["orderingStreamEnabled"],
    }
    exact_keys(value["ownerSettings"], CAPTURE_OWNER_KEYS, "ORIGINAL_OWNER_SCHEMA")
    need(_typed_equal(value["ownerSettings"], expected_owner)
         and value["priorPromotedNetId"] == captured["priorPromotedNetId"]
         and type(value["netIds"]) is list and len(value["netIds"]) == 60
         and all(type(net_id) is str for net_id in value["netIds"])
         and value["netIds"] == captured["netIds"]
         and len(set(value["netIds"])) == 60, "ORIGINAL_OWNER_BINDING")
    source_identity = projection["sourceIdentity"]
    template = value["template"]
    exact_keys(template, TEMPLATE_KEYS, "ORIGINAL_TEMPLATE_SCHEMA")
    expected_template = {
        "geometryBeforeSha256": source_identity["geometryBeforeSha256"],
        "geometryAfterSha256": source_identity["geometryAfterSha256"],
        "effectsBeforeSha256": source_identity["effectsBeforeSha256"],
        "effectsAfterSha256": source_identity["effectsAfterSha256"],
        "traceCount": source_identity["traceCount"],
        "holeCount": source_identity["holeCount"],
    }
    need(_typed_equal(template, expected_template) and template["traceCount"] == 0
         and template["holeCount"] == 0
         and template["effectsBeforeSha256"] == template["effectsAfterSha256"],
         "ORIGINAL_TEMPLATE_BINDING")
    pad_identity = value["padIdentity"]
    exact_keys(pad_identity, CAPTURE_PAD_IDENTITY_KEYS, "ORIGINAL_PAD_SCHEMA")
    prior_pad = source_identity["padLandIdentity"]
    need(pad_identity == {
        "schema": prior_pad["format"], "padCount": prior_pad["rowCount"],
        "padLandSha256": prior_pad["sha256"],
    } and pad_identity["schema"] == "TSJ_PAD_LAND_ASCII_V1"
         and pad_identity["padCount"] == 246, "ORIGINAL_PAD_BINDING")
    need(all(is_sha(template[name]) for name in (
        "geometryBeforeSha256", "geometryAfterSha256",
        "effectsBeforeSha256", "effectsAfterSha256")), "ORIGINAL_TEMPLATE_DIGEST")
    need(value["ownersReleased"] is True and value["contextRestored"] is True
         and is_int(value["cleanupNanos"], 0, MAX_CLEANUP_NS),
         "ORIGINAL_CAPTURE_CLEANUP")
    return receipt


def _validate_arm_cleanup(value, original_template=None):
    exact_keys(value, ARM_CLEANUP_KEYS, "ARM_CLEANUP_SCHEMA")
    need(value["schema"] == "tsj-cohort-terminal-arm-cleanup-v1"
         and all(value[name] is True for name in (
             "adapterSessionAliasDropped", "adapterSearchAliasDropped",
             "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
             "adapterBoardTemplateAliasesDropped", "contextRestored"))
         and all(is_sha(value[name]) for name in (
             "sourceGeometryBeforeSha256", "sourceGeometryAfterSha256"))
         and value["sourceGeometryBeforeSha256"] == value["sourceGeometryAfterSha256"]
         and (original_template is None
              or value["sourceGeometryBeforeSha256"]
              == original_template["geometryBeforeSha256"]
              == original_template["geometryAfterSha256"])
         and is_int(value["cleanupNanos"], 0, MAX_CLEANUP_NS),
         "ARM_CLEANUP_BINDING")
    return value


def _validate_snapshot(snapshot, requested, traversed, pads_by_id, pads_by_net,
                       expected_grid_dimensions):
    try:
        return _ObservationV2Kernel.validate_snapshot(
            snapshot, requested, traversed, pads_by_id, pads_by_net,
            expected_grid_dimensions)
    except _ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _validate_observation(value, arm_role, fresh_run, facts, pads_by_id,
                          pads_by_net, expected_grid_dimensions):
    try:
        return _ObservationV2Kernel.validate_observation(
            value, arm_role, fresh_run["work"]["advanceCalls"],
            fresh_run["ordering"]["requestedNetIds"],
            fresh_run["ordering"]["traversedNetIds"], pads_by_id, pads_by_net,
            expected_grid_dimensions, facts["outcome"], facts["terminalFailure"])
    except _ObservationV2Error as failure:
        raise ReaderError(failure.code) from None


def _validate_arm(value, arm_role, cohort_ids, captured, pads_by_id, pads_by_net,
                  original_template, runtime_identity, expected_grid_dimensions):
    exact_keys(value, ARM_KEYS, "ARM_SCHEMA")
    need(value["armRole"] == arm_role and is_uuid(value["armId"])
         and value["armId"] != runtime_identity["runId"], "ARM_IDENTITY")
    fresh_run = value["freshRun"]
    try:
        facts = _validate_prior_run(fresh_run, "OBSERVED_FAILURE_COHORT_TRIAL",
                                    captured, cohort_ids)
    except _PriorReaderError as failure:
        raise ReaderError(failure.code) from None
    observation_facts = _validate_observation(
        value["observation"], arm_role, fresh_run, facts, pads_by_id, pads_by_net,
        expected_grid_dimensions)
    cleanup = _validate_arm_cleanup(value["cleanup"], original_template)
    need(fresh_run["kind"] == "OBSERVED_FAILURE_COHORT_TRIAL", "ARM_RUN_KIND")
    return {"freshRun": fresh_run, "observation": value["observation"],
            "facts": facts, "observationFacts": observation_facts,
            "cleanup": cleanup}


def _validate_work(value, original_capture, arms, observations):
    ordered_keys(value, WORK_KEYS, "WORK_SCHEMA")
    copy_names = {"setupCopiedEntries", "originalCaptureCopiedEntries",
                  "metadataCopiedEntries", "copiedEntries"}
    chunk_names = {"setupCopyChunks", "originalCaptureCopyChunks",
                   "metadataCopyChunks", "copyChunks", "copyChunkLimit"}
    group_names = {"groupsBeforeExport", "wrapperGroupsBeforeExport", "setupGroups",
                   "originalCaptureGroups", "metadataGroups"}
    for name in WORK_KEYS:
        if name == "elapsedCutoffNanos":
            okay = is_int(value[name], 1, MAX_TOTAL_NS)
        elif name == "cleanupNanos":
            okay = is_int(value[name], 0, MAX_CLEANUP_NS)
        elif name in copy_names or name in chunk_names:
            okay = is_int(value[name], 0, MAX_COPY_ENTRIES)
        elif name in group_names:
            okay = is_int(value[name], 0, MAX_GROUPS)
        elif name == "maxGroupNanosBeforeExport":
            okay = is_int(value[name], 1, MAX_GROUP_NS)
        elif name == "maximumUnitsPerGroupBeforeExport":
            okay = is_int(value[name], 1, MAX_GROUP_UNITS)
        else:
            okay = is_int(value[name], 0, MAX_GROUP_UNITS * MAX_GROUPS)
        need(okay, "WORK_INTEGER")
    need(0 < value["elapsedCutoffNanos"] <= MAX_TOTAL_NS
         and value["setupUnits"] > 0 and value["setupGroups"] > 0
         and value["metadataUnits"] > 0 and value["metadataGroups"] > 0
         and value["cleanupNanos"] <= MAX_CLEANUP_NS
         and value["groupsBeforeExport"] + 1 <= MAX_GROUPS
         and 0 < value["wrapperGroupsBeforeExport"] <= value["groupsBeforeExport"]
         and value["maxGroupNanosBeforeExport"] <= MAX_GROUP_NS
         and value["maximumUnitsPerGroupBeforeExport"] <= MAX_GROUP_UNITS
         and value["unitsBeforeExport"] <= value["groupsBeforeExport"] * MAX_GROUP_UNITS
         and value["copyChunkLimit"] == COPY_CHUNK
         and value["copiedEntryLimit"] == MAX_COPY_ENTRIES, "WORK_LIMITS")
    null_run, observe_run = arms["null"]["freshRun"], arms["observe"]["freshRun"]
    null_obs, observe_obs = observations["null"], observations["observe"]
    need(value["nullOwnerUnits"] == null_run["work"]["ownerUnits"]
         and value["nullWrapperUnits"] == null_run["work"]["wrapperUnits"]
         and value["nullObserverUnits"] == null_obs["observerUnits"] == 0
         and value["observeOwnerUnits"] == observe_run["work"]["ownerUnits"]
         and value["observeWrapperUnits"] == observe_run["work"]["wrapperUnits"]
         and value["observeObserverUnits"] == observe_obs["observerUnits"]
         and value["observerUnits"] == value["nullObserverUnits"]
         + value["observeObserverUnits"], "WORK_ARM_BINDING")
    need(value["originalCaptureGroups"] >= 1
         and value["originalCaptureCopiedEntries"] >= 0
         and value["originalCaptureCopyChunks"] <= value["originalCaptureCopiedEntries"],
         "WORK_CAPTURE_GROUPS")
    expected_units = sum(value[name] for name in (
        "setupUnits", "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
        "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
        "nullOwnerUnits", "nullWrapperUnits", "nullObserverUnits",
        "observeOwnerUnits", "observeWrapperUnits", "observeObserverUnits",
        "metadataUnits"))
    need(value["meterUnits"] == expected_units == value["unitsBeforeExport"],
         "WORK_UNIT_EQUATION")
    expected_groups = (value["setupGroups"] + value["originalCaptureGroups"]
                       + null_run["work"]["groups"] + observe_run["work"]["groups"]
                       + value["metadataGroups"])
    need(value["groupsBeforeExport"] == expected_groups, "WORK_GROUP_EQUATION")
    expected_entries = (value["setupCopiedEntries"]
                        + value["originalCaptureCopiedEntries"]
                        + null_run["work"]["copyEntries"]
                        + observe_run["work"]["copyEntries"]
                        + observe_obs["copiedEntries"]
                        + value["metadataCopiedEntries"])
    expected_chunks = (value["setupCopyChunks"]
                       + value["originalCaptureCopyChunks"]
                       + null_run["work"]["copyChunks"]
                       + observe_run["work"]["copyChunks"]
                       + observe_obs["copyChunks"]
                       + value["metadataCopyChunks"])
    need(value["copiedEntries"] == expected_entries
         and value["copyChunks"] == expected_chunks
         and value["copiedEntries"] <= MAX_COPY_ENTRIES
         and value["copyChunks"] <= value["copiedEntries"]
         and (value["copiedEntries"] == 0 or value["copyChunks"] >=
              (value["copiedEntries"] + COPY_CHUNK - 1) // COPY_CHUNK),
         "WORK_COPY_EQUATION")
    need(value["maxGroupNanosBeforeExport"] >= max(
        null_run["work"]["maxGroupNanos"], observe_run["work"]["maxGroupNanos"])
         and value["maximumUnitsPerGroupBeforeExport"] >= max(
             null_run["work"]["maximumUnitsPerGroup"],
             observe_run["work"]["maximumUnitsPerGroup"]), "WORK_MAXIMA")
    return value


def _validate_top_cleanup(value, capture, arms, work):
    exact_keys(value, TOP_CLEANUP_KEYS, "TOP_CLEANUP_SCHEMA")
    null_cleanup, observe_cleanup = arms["null"]["cleanup"], arms["observe"]["cleanup"]
    geometry = (capture["template"]["geometryBeforeSha256"]
                == capture["template"]["geometryAfterSha256"]
                == null_cleanup["sourceGeometryBeforeSha256"]
                == null_cleanup["sourceGeometryAfterSha256"]
                == observe_cleanup["sourceGeometryBeforeSha256"]
                == observe_cleanup["sourceGeometryAfterSha256"])
    copy_effects_unchanged = (capture["template"]["effectsBeforeSha256"]
                              == capture["template"]["effectsAfterSha256"])
    aliases = lambda arm: all(arm["cleanup"][name] is True for name in (
        "adapterSessionAliasDropped", "adapterSearchAliasDropped",
        "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
        "adapterBoardTemplateAliasesDropped"))
    expected = {
        "schema": CLEANUP_SCHEMA,
        "originalCaptureOwnersReleased": capture["ownersReleased"],
        "originalCaptureContextRestored": capture["contextRestored"],
        "nullArmAliasesDropped": aliases(arms["null"]),
        "observeArmAliasesDropped": aliases(arms["observe"]),
        "sourceGeometryUnchanged": geometry,
        "effectsScope": "ORIGINAL_CAPTURE_COPY_ONLY",
        "originalCaptureCopyEffectsUnchanged": copy_effects_unchanged,
        "contextRestored": (capture["contextRestored"]
                             and null_cleanup["contextRestored"]
                             and observe_cleanup["contextRestored"]),
        "cleanupNanos": work["cleanupNanos"],
    }
    need(geometry is True and copy_effects_unchanged is True,
         "TOP_CLEANUP_UNCHANGED")
    need(value == expected and all(type(value[name]) is bool for name in (
        "originalCaptureOwnersReleased", "originalCaptureContextRestored",
        "nullArmAliasesDropped", "observeArmAliasesDropped",
        "sourceGeometryUnchanged", "originalCaptureCopyEffectsUnchanged",
        "contextRestored"))
         and is_int(value["cleanupNanos"], 0, MAX_CLEANUP_NS),
         "TOP_CLEANUP_BINDING")
    minimum_cleanup = (capture["cleanupNanos"] + null_cleanup["cleanupNanos"]
                       + observe_cleanup["cleanupNanos"])
    need(value["cleanupNanos"] >= minimum_cleanup, "TOP_CLEANUP_ACCOUNTING")


def _parity_projection(run):
    return {
        "initial": run["initial"],
        "ordering": run["ordering"],
        "outcome": run["outcome"],
        "terminalFailure": run["terminalFailure"],
        "finalizers": run["finalizers"],
        "layout": run["layout"],
        "advanceCalls": run["work"]["advanceCalls"],
    }


def _validate_finite_expectation(arms):
    for name in ("null", "observe"):
        run = arms[name]["freshRun"]
        facts = arms[name]["facts"]
        observation = arms[name]["observationFacts"]
        observation_dto = arms[name]["observation"]
        need(facts["outcome"]["reason"] == "D_COIL_LOW:BRANCH_SEARCH_LIMIT"
             and facts["outcome"]["expanded"] == 419_905
             and len(run["ordering"]["traversedNetIds"]) == 43
             and observation["state"] == (
                 "NOT_ENABLED" if name == "null" else "FIRST_NON_FOUND"),
             "FINITE_REFERENCE_MISMATCH")
        if name == "null":
            need(observation_dto["selectedSnapshot"] is None,
                 "FINITE_REFERENCE_MISMATCH")
        else:
            selected = observation_dto["selectedSnapshot"]
            need(type(selected) is dict and selected["netId"] == "D_COIL_LOW"
                 and selected["terminalReason"] == "BRANCH_SEARCH_LIMIT"
                 and selected["totalExpanded"] == 419_905,
                 "FINITE_REFERENCE_MISMATCH")


def _validate_result_semantics(doc, context, runtime_identity, op1_raw,
                               result_raw):
    _full_domain(doc, "RESULT_ASCII")
    ordered_keys(doc, RESULT_KEYS, "RESULT_SCHEMA")
    need(doc["schema"] == RESULT_SCHEMA and doc["mode"] == RESULT_MODE
         and doc["status"] == RESULT_STATUS
         and doc["runId"] == runtime_identity["runId"]
         and doc["sourceManifestSha256"] == runtime_identity["sourceManifestSha256"]
         and doc["wireContractSha256"] == runtime_identity["wireContractSha256"]
         and doc["inputContextSha256"] == runtime_identity["inputContextSha256"],
         "RESULT_IDENTITY")
    prior_expected = _expected_prior_ref(context, runtime_identity)
    ordered_keys(doc["qualifiedPrior"], QUALIFIED_PRIOR_KEYS,
                 "QUALIFIED_PRIOR_SCHEMA")
    ordered_keys(doc["qualifiedPrior"]["priorOriginalReceipt"], RECEIPT_KEYS,
                 "PRIOR_RECEIPT_SCHEMA")
    need(_typed_equal(doc["qualifiedPrior"], prior_expected),
         "QUALIFIED_PRIOR_BINDING")
    pad_rows, captured = _prior_semantics(context)
    capture = doc["originalCapture"]
    receipt = _validate_original_capture(capture, context, captured, op1_raw,
                                         runtime_identity)
    expected_grid = _prior_grid_dimensions(context)
    cohort = doc["cohort"]
    exact_keys(cohort, COHORT_KEYS, "COHORT_SCHEMA")
    prior_disposition = prior_expected["trialDisposition"]
    cohort_ids = prior_disposition["cohortNetIds"]
    requested = _stable_cohort_order(captured["netIds"], cohort_ids)
    need(cohort["netIds"] == cohort_ids and cohort["requestedNetIds"] == requested
         and prior_disposition["requestedNetIds"] == requested,
         "COHORT_ORDER_BINDING")
    arms_value = doc["arms"]
    exact_keys(arms_value, ("null", "observe"), "ARMS_SCHEMA")
    arm_ids = [arms_value[name].get("armId")
               if type(arms_value[name]) is dict else None
               for name in ("null", "observe")]
    need(all(is_uuid(arm_id) for arm_id in arm_ids), "ARM_IDENTITY")
    need(len(set(arm_ids + [runtime_identity["runId"], capture["captureId"]])) == 4,
         "ARM_ID_DUPLICATE")
    pads_by_id, pads_by_net = _pad_lookup(pad_rows)
    need(len(pads_by_net) == 60 and set(pads_by_net) == set(captured["netIds"])
         and all(pads_by_net[net] for net in captured["netIds"]),
         "PAD_NET_BIJECTION")
    arms = {}
    observations = {}
    for name, role in (("null", "NULL"), ("observe", "OBSERVE")):
        arms[name] = _validate_arm(
            arms_value[name], role, cohort_ids, captured, pads_by_id, pads_by_net,
            capture["template"], runtime_identity, expected_grid)
        observations[name] = arms[name]["observationFacts"]
    null_run, observe_run = arms["null"]["freshRun"], arms["observe"]["freshRun"]
    need(_parity_projection(null_run) == _parity_projection(observe_run),
         "ARM_PARITY")
    _validate_finite_expectation(arms)
    work = _validate_work(doc["work"], capture, arms, observations)
    need(work["metadataUnits"] >= _result_tree_minimum_units(doc),
         "WORK_METADATA_TREE")
    need(receipt["receiptOrdinal"] == 307 and receipt["lineageOrdinal"] == 77,
         "ORIGINAL_RECEIPT_PRECONDITION")
    _validate_top_cleanup(doc["cleanup"], capture, arms, work)
    return {"arms": arms, "observations": observations, "work": work,
            "capture": capture, "receipt": receipt}


def validate_marker_line(marker, marker_raw: bytes) -> int:
    need(type(marker) is dict, "MARKER_SCHEMA")
    ordered_keys(marker, MARKER_KEYS, "MARKER_SCHEMA")
    size = _canonical_json_line(marker, marker_raw, MAX_MARKER_BYTES, "MARKER")
    need(size <= MAX_MARKER_TRANSPORT_BYTES, "MARKER_TRANSPORT_BOUND")
    string_fields = (
        "schema", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "inputContextSha256", "resultSha256",
    )
    need(all(type(marker[name]) is str for name in string_fields)
         and marker["schema"] == MARKER_SCHEMA
         and marker["status"] == MARKER_STATUS
         and is_uuid(marker["runId"])
         and all(is_sha(marker[name]) for name in (
             "sourceManifestSha256", "wireContractSha256", "inputContextSha256",
             "resultSha256")), "MARKER_STRINGS")
    numeric_fields = (
        "resultBytes", "elapsedNanos", "finalExportNanos", "totalGroups",
        "totalUnits", "maxGroupNanos", "maximumUnitsPerGroup",
    )
    need(all(type(marker[name]) is int and 0 <= marker[name] <= MAX_SIGNED_LONG
             for name in numeric_fields), "MARKER_NUMBERS")
    return size


def _validate_marker_binding(marker, marker_raw, result_raw, doc, runtime_identity):
    validate_marker_line(marker, marker_raw)
    work = doc["work"]
    export_units = 5 * ((len(result_raw) + COPY_CHUNK - 1) // COPY_CHUNK) + 3
    need(marker["runId"] == runtime_identity["runId"]
         and marker["sourceManifestSha256"] == runtime_identity["sourceManifestSha256"]
         and marker["wireContractSha256"] == runtime_identity["wireContractSha256"]
         and marker["inputContextSha256"] == runtime_identity["inputContextSha256"]
         and marker["resultSha256"] == sha(result_raw)
         and is_int(marker["resultBytes"], len(result_raw), len(result_raw))
         and is_int(marker["elapsedNanos"], 1, MAX_TOTAL_NS)
         and is_int(marker["finalExportNanos"], 1, MAX_GROUP_NS)
         and is_int(marker["totalGroups"], work["groupsBeforeExport"] + 1,
                    work["groupsBeforeExport"] + 1)
         and is_int(marker["totalUnits"], work["unitsBeforeExport"] + export_units,
                    work["unitsBeforeExport"] + export_units)
         and is_int(marker["maxGroupNanos"],
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]),
                    max(work["maxGroupNanosBeforeExport"], marker["finalExportNanos"]))
         and is_int(marker["maximumUnitsPerGroup"],
                    max(work["maximumUnitsPerGroupBeforeExport"], export_units),
                    max(work["maximumUnitsPerGroupBeforeExport"], export_units))
         and export_units <= MAX_GROUP_UNITS
         and marker["elapsedNanos"] >= work["elapsedCutoffNanos"]
         + marker["finalExportNanos"], "MARKER_BINDING")
    return marker


def _export_copy_deltas(result_bytes, marker_bytes):
    need(is_int(result_bytes, 2, MAX_JSON_LINE)
         and is_int(marker_bytes, 2, MAX_MARKER_BYTES), "EXPORT_COPY_LENGTHS")
    result_chunks = (result_bytes + COPY_CHUNK - 1) // COPY_CHUNK
    return (2 * result_bytes + EXPORT_DIGEST_BYTES + marker_bytes,
            2 * result_chunks + 2, result_chunks)


def derive_export_copy_reconciliation(result_doc, result_raw,
                                      marker_doc, marker_raw):
    """Derive bounded export demand; this is not a Meter observation."""
    need(type(result_doc) is dict, "EXPORT_RESULT_SCHEMA")
    ordered_keys(result_doc, RESULT_KEYS, "EXPORT_RESULT_SCHEMA")
    result_bytes = _canonical_json_line(
        result_doc, result_raw, MAX_JSON_LINE, "EXPORT_RESULT")
    need(result_doc["schema"] == RESULT_SCHEMA
         and result_doc["mode"] == RESULT_MODE
         and result_doc["status"] == RESULT_STATUS
         and is_uuid(result_doc["runId"])
         and is_sha(result_doc["sourceManifestSha256"])
         and result_doc["wireContractSha256"] == WIRE_CONTRACT_SHA256
         and is_sha(result_doc["inputContextSha256"]), "EXPORT_RESULT_IDENTITY")
    marker_bytes = validate_marker_line(marker_doc, marker_raw)
    need(marker_doc["runId"] == result_doc["runId"]
         and marker_doc["sourceManifestSha256"] == result_doc["sourceManifestSha256"]
         and marker_doc["wireContractSha256"] == result_doc["wireContractSha256"]
         and marker_doc["inputContextSha256"] == result_doc["inputContextSha256"]
         and marker_doc["resultBytes"] == result_bytes
         and marker_doc["resultSha256"] == sha(result_raw),
         "EXPORT_MARKER_RESULT_BINDING")
    work = result_doc["work"]
    ordered_keys(work, WORK_KEYS, "EXPORT_WORK_SCHEMA")
    pre_entries, pre_chunks = work["copiedEntries"], work["copyChunks"]
    need(is_int(pre_entries, 0, MAX_COPY_ENTRIES)
         and is_int(pre_chunks, 0, MAX_COPY_ENTRIES)
         and pre_chunks <= pre_entries
         and work["copiedEntryLimit"] == MAX_COPY_ENTRIES
         and work["copyChunkLimit"] == COPY_CHUNK, "EXPORT_PRECOPY_BINDING")
    export_entries, export_chunks, result_chunks = _export_copy_deltas(
        result_bytes, marker_bytes)
    required_entries = pre_entries + export_entries
    required_chunks = pre_chunks + export_chunks
    need(required_entries <= MAX_COPY_ENTRIES
         and required_chunks <= MAX_COPY_ENTRIES
         and required_chunks <= required_entries, "EXPORT_COPY_CAPACITY")
    return {
        "status": "EXPORT_COPY_RECONCILIATION_DERIVED_ONLY",
        "resultBytes": result_bytes,
        "resultChunks": result_chunks,
        "markerBytes": marker_bytes,
        "derivedExportCopiedEntries": export_entries,
        "derivedExportCopyChunks": export_chunks,
        "requiredCumulativeCopiedEntries": required_entries,
        "requiredCumulativeCopyChunks": required_chunks,
        "scope": "SOURCE_BOUND_DERIVATION_NOT_METER_OBSERVATION",
    }


def _r8_pad_projection(rows, code):
    need(type(rows) is list, code + "_ROWS")
    by_pad_id = {}
    for row in rows:
        exact_keys(row, R8_P07_PAD_KEYS, code + "_SCHEMA")
        pad_id, net_id = row["padId"], row["netId"]
        component_id, role = row["componentId"], row["role"]
        x, y = row["x"], row["y"]
        need(all(type(value) is str and bool(value)
                 for value in (pad_id, net_id, component_id, role))
             and is_int(x, MIN_INT, MAX_INT) and is_int(y, MIN_INT, MAX_INT),
             code + "_PHYSICAL_FIELDS")
        _full_domain(pad_id, code + "_PHYSICAL_FIELDS")
        _full_domain(net_id, code + "_PHYSICAL_FIELDS")
        _full_domain(component_id, code + "_PHYSICAL_FIELDS")
        _full_domain(role, code + "_PHYSICAL_FIELDS")
        need(pad_id not in by_pad_id, code + "_DUPLICATE_PAD_ID")
        by_pad_id[pad_id] = tuple(row[name] for name in R8_PAD_PHYSICAL_KEYS)
    return by_pad_id


def derive_original_group_reconciliation(result_doc, op1_raw, qualified_pads,
                                         expected_grid_dimensions):
    """Derive mandatory owner-group and physical-PAD reconciliation."""
    need(type(result_doc) is dict, "R8_RESULT_SCHEMA")
    ordered_keys(result_doc, RESULT_KEYS, "R8_RESULT_SCHEMA")
    need(result_doc["schema"] == RESULT_SCHEMA
         and result_doc["mode"] == RESULT_MODE
         and result_doc["status"] == RESULT_STATUS
         and is_uuid(result_doc["runId"])
         and is_sha(result_doc["sourceManifestSha256"])
         and result_doc["wireContractSha256"] == WIRE_CONTRACT_SHA256
         and is_sha(result_doc["inputContextSha256"]), "R8_RESULT_IDENTITY")
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= MAX_OPERATION1_BYTES,
         "R8_OPERATION1_BYTES")
    work = result_doc["work"]
    ordered_keys(work, WORK_KEYS, "R8_WORK_SCHEMA")
    elapsed_envelope = work["elapsedCutoffNanos"]
    group_envelope = work["maxGroupNanosBeforeExport"]
    original_capture_groups = work["originalCaptureGroups"]
    groups_before_export = work["groupsBeforeExport"]
    wrapper_groups = work["wrapperGroupsBeforeExport"]
    need(is_int(elapsed_envelope, 1, MAX_TOTAL_NS)
         and is_int(group_envelope, 1, MAX_GROUP_NS)
         and is_int(original_capture_groups, 1, MAX_GROUPS)
         and is_int(groups_before_export, 1, MAX_GROUPS - 1)
         and is_int(wrapper_groups, 1, MAX_GROUPS)
         and wrapper_groups <= groups_before_export, "R8_WORK_GROUP_BOUNDS")
    entry = {"elapsedToCloseNanos": elapsed_envelope,
             "maxFullGroupNanos": group_envelope}
    try:
        parsed = _validate_operation(
            op1_raw, entry, result_doc["sourceManifestSha256"],
            result_doc["wireContractSha256"], 1, result_doc["runId"], "AUTHORED")
    except Exception as failure:
        reason = str(failure)
        if not re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            reason = "PARSE"
        raise ReaderError("R8_OPERATION1_" + reason.upper()) from None
    need(type(parsed) is dict and type(parsed.get("groups")) is list
         and type(parsed.get("pads")) is list
         and type(parsed.get("branches")) is list
         and bool(parsed["branches"]), "R8_OPERATION1_PARSED_SHAPE")
    need(type(expected_grid_dimensions) is tuple
         and len(expected_grid_dimensions) == 2, "R8_GRID_CONTEXT")
    grid_width, grid_height = expected_grid_dimensions
    for branch in parsed["branches"]:
        snapshot = branch.get("snapshot") if type(branch) is dict else None
        need(type(snapshot) is dict
             and snapshot.get("gridWidth") == grid_width
             and snapshot.get("gridHeight") == grid_height,
             "R8_GRID_BINDING")
    owner_group_count = len(parsed["groups"])
    need(is_int(owner_group_count, 1, MAX_GROUPS), "R8_OWNER_GROUP_CENSUS")
    need(original_capture_groups >= owner_group_count, "R8_CAPTURE_OWNER_GROUPS")
    need(groups_before_export - owner_group_count == wrapper_groups,
         "R8_WRAPPER_GROUP_RECONCILIATION")
    prior_pad_projection = _r8_pad_projection(qualified_pads, "R8_PRIOR_PAD")
    fresh_pad_projection = _r8_pad_projection(parsed["pads"], "R8_FRESH_PAD")
    need(bool(prior_pad_projection) and fresh_pad_projection == prior_pad_projection,
         "R8_PAD_PHYSICAL_IDENTITY_MISMATCH")
    return {
        "schema": "R8_ORIGINAL_GROUP_CENSUS_DERIVED_ONLY",
        "ownerGroupCount": owner_group_count,
        "originalCaptureGroups": original_capture_groups,
        "groupsBeforeExport": groups_before_export,
        "wrapperGroupsBeforeExport": wrapper_groups,
        "padBinding": "QUALIFIED_PRIOR_PHYSICAL_IDENTITY_MATCH",
        "padCount": len(prior_pad_projection),
        "timingBinding": "CONSERVATIVE_TOTAL_PREFIX_ENVELOPE",
        "scope": "AUTHENTICATED_OPERATION1_GROUP_AND_PAD_CENSUS_ONLY",
    }


def _freeze(value):
    if type(value) is dict:
        return MappingProxyType({key: _freeze(child) for key, child in value.items()})
    if type(value) is list:
        return tuple(_freeze(child) for child in value)
    if type(value) is tuple:
        return tuple(_freeze(child) for child in value)
    return value


def validate_full_d_v2(*, context, runtime_identity, op1_raw, result_raw,
                       marker_raw):
    """Validate one explicit in-memory current-V2 packet.

    The result is path-free, immutable, and states semantic/derived evidence
    only. It does not establish native Meter, process, lifecycle, or publication
    acceptance.
    """
    need(type(context) is dict, "CONTEXT_TYPE")
    need(type(marker_raw) is bytes and 2 <= len(marker_raw) <= MAX_MARKER_BYTES
         and len(marker_raw) <= MAX_MARKER_TRANSPORT_BYTES,
         "MARKER_SIZE_BEFORE_PARSE")
    try:
        _validate_context(context)
    except _ContextCodecError as failure:
        raise ReaderError(failure.code) from None
    except RecursionError:
        raise ReaderError("CONTEXT_DEPTH") from None
    _validate_context_order(context)
    _validate_runtime_identity(runtime_identity, context)
    need(type(op1_raw) is bytes and 0 < len(op1_raw) <= MAX_OPERATION1_BYTES,
         "ORIGINAL_RECEIPT_BYTES")
    doc = _strict_json_line_core(result_raw, "RESULT_JSON")
    marker = _strict_json_line_core(marker_raw, "MARKER_JSON")
    result_bytes = _canonical_json_line(doc, result_raw, MAX_JSON_LINE, "RESULT")
    marker_bytes = validate_marker_line(marker, marker_raw)
    semantics = _validate_result_semantics(
        doc, context, runtime_identity, op1_raw, result_raw)
    _validate_marker_binding(marker, marker_raw, result_raw, doc, runtime_identity)
    group_reconciliation = derive_original_group_reconciliation(
        doc, op1_raw, context["physicalContext"]["qualifiedP07Pads"],
        _prior_grid_dimensions(context))
    export_reconciliation = derive_export_copy_reconciliation(
        doc, result_raw, marker, marker_raw)
    need(result_bytes == len(result_raw) and marker_bytes == len(marker_raw),
         "OUTPUT_LENGTH_BINDING")
    arms = semantics["arms"]
    summary = {
        "status": "SEMANTIC_PACKET_VALIDATED_DERIVED_ONLY",
        "runId": runtime_identity["runId"],
        "sourceManifestSha256": runtime_identity["sourceManifestSha256"],
        "wireContractSha256": runtime_identity["wireContractSha256"],
        "inputContextSha256": runtime_identity["inputContextSha256"],
        "resultSha256": sha(result_raw),
        "markerSha256": sha(marker_raw),
        "resultBytes": result_bytes,
        "markerBytes": marker_bytes,
        "qualifiedPrior": doc["qualifiedPrior"],
        "arms": {
            name: {
                "disposition": arms[name]["facts"]["outcome"]["disposition"],
                "reason": arms[name]["facts"]["outcome"]["reason"],
                "expanded": arms[name]["facts"]["outcome"]["expanded"],
                "traversed": len(arms[name]["freshRun"]["ordering"]["traversedNetIds"]),
                "observation": arms[name]["observationFacts"]["state"],
            }
            for name in ("null", "observe")
        },
        "work": semantics["work"],
        "originalGroupReconciliation": group_reconciliation,
        "exportCopyReconciliation": export_reconciliation,
        "evidenceScope": "PURE_SEMANTIC_AND_DERIVED_DEMAND_ONLY",
    }
    return _freeze(summary)


__all__ = ("ReaderError", "derive_export_copy_reconciliation",
           "derive_original_group_reconciliation", "validate_full_d_v2",
           "validate_marker_line")
