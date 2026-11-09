"""Pure authentication and normalization for the pinned Q100 prior inputs.

This module accepts bytes already read by its caller.  It performs no file,
process, environment, or dynamic-module operations.  The caller owns private
roots, directory closure, resource absence, source/tool byte facts, and the
separate current-source authority.  The current-source authority is returned
separately and is never folded into the historical prior context.

The public entry point deliberately requires the published allowlist bytes,
the complete 20-role byte map, the independently frozen caller authority, the
five source-extracted N/P07 pure functions, and the current context-codec
functions.  No old builder, reader, descriptor, or module object is accepted.
"""

from __future__ import annotations

import hashlib
import json
import math
import ntpath
import re
from typing import NamedTuple


ALLOWLIST_SHA256 = "c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91"
ALLOWLIST_SCHEMA = "tsj-full-d-v2-normal-oracle-allowlist-v1"
ALLOWLIST_STATUS = "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED"
ALLOWLIST_ROLE_COUNT = 20
MAX_ALLOWLIST_BYTES = 256 * 1024
MAX_HOST_RECEIPT_BYTES = 512 * 1024
MAX_OBSERVATION_BYTES = 128 * 1024
MAX_QUALIFICATION_STDOUT = 1024
MAX_PRIOR_JSON_BYTES = 8 * 1024 * 1024
MAX_OPERATION_BYTES = 8 * 1024 * 1024
MAX_SOURCE_METADATA_BYTES = 4 * 1024 * 1024
MAX_PAD_ROW_BYTES = 4096
MAX_PAD_TOTAL_BYTES = 1024 * 1024

SOURCE_PARENT = "474a8354ceb2e50fa2adbb31e23cf73a0d9c86fd"
N_PRIOR_READER_SHA256 = "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
N_PRIOR_BUILDER_SHA256 = "cb8ae8eee78ca90293925b90eec1d8b0ed545668a7dd3a29e4ab169f39542ae2"
D_BUILDER_SHA256 = "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321"
D_DESCRIPTOR_SHA256 = "2cebcf6a26c61a3600c9ffcf5e1f1268e62bd966598a57e7d71587173d5b8724"
D_PAD_HELPER_SHA256 = "5c39150dad347bfa7dba5b902f149b4da61551dddc76def5176c2dbc84853c64"
D_LITERAL_CODEC_SHA256 = "58ac9b029097d63e078adb70044e31aed29cd5e8d3bea1afee8b54df01a278e9"
HOST_READER_SHA256 = "dbe7ee4411d78217d08f118967dee873d63551d908747266145d12d1c44b5806"
P07_SOURCE_SHA256 = "a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e"
OBSERVATION_READER_SHA256 = "f23a965ef3bc28a78ef06158efb9c7c50c375dc164d7e2840e5d350f51db58c3"
D_WIRE_SHA256 = "31a81de89f91655fa64f348a0cdcc543ed13429eccf62a16286b6f4a5f55ca70"
N_PRIOR_WIRE_SHA256 = "c7bf55d15f8166e7d8c767abd3f068ee88087b6a9132f9331af7d21c5c202362"
HOST_WIRE_SHA256 = "e88411039b6b7438a768280779ac3abbec256ef7995ff305e0bf33c5c2f736a5"
CONTEXT_CODEC_SHA256 = "c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27"
ROOT_CALL_HARNESS_SHA256 = "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d"
PROCESS_MODULE_SHA256 = "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c"
POWERSHELL_SHA256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
QUALIFICATION_PYTHON_SHA256 = "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec"
PAD_LAND_SHA256 = "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0"

CURRENT_SOURCE_AUTHORITY_SCHEMA = "tsj-q100-current-source-authority-v1"
NORMALIZER_AUTHORITY_SCHEMA = "tsj-q100-normalizer-authority-v1"
PARENT_SCHEMA = "tsj-q100-reservation-provenance-parent-v1"
OWNER_SCHEMA = "tsj-p9-root-call-owner-v1"
N_RESULT_SCHEMA = "tsj-failure-cohort-routing-v1"
N_MARKER_SCHEMA = "tsj-failure-cohort-marker-v1"
N_RESULT_MODE = "OBSERVED_FAILURE_COHORT"
N_ENTRY_CLASS = "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy"
QUALIFICATION_STATUS = "PASS_OBSERVED_FAILURE_COHORT_DIAGNOSIS_ONLY"
QUALIFICATION_SUMMARY_KEYS = (
    "schema", "status", "manifestSha256", "readerSha256", "resultSha256",
    "operation1Sha256", "trialDisposition", "netCount", "controlDisposition",
    "trialOutcomeDisposition", "controlTraversedCount", "trialTraversedCount",
    "controlLayoutConnectivity", "trialLayoutConnectivity", "layoutQualification",
    "routingTreatmentSelected", "productionAdopted", "feasibilityClaimed",
    "playable100Accepted",
)
N_SOURCE_KEYS = (
    "schema", "sourceParent", "parentManifestPath", "parentManifestSha256",
    "parentHelperSha256", "planSha256", "compilerSourceCount", "compilerListPath",
    "compilerListBytes", "compilerListSha256", "compilerRequestPath",
    "compilerRequestSha256", "classesRoot", "emptySourcepath", "sources", "tools", "wire",
)
N_RUN_KEYS = N_SOURCE_KEYS + ("build",)
N_BUILD_KEYS = (
    "sourceManifestPath", "sourceManifestSha256", "compileCallDirectory",
    "compileParentPath", "compileParentSha256", "classInventoryPath",
    "classInventorySha256", "classCount", "classBytes",
)
N_RESULT_KEYS = (
    "schema", "mode", "runId", "finalRunManifestSha256", "wireContractSha256",
    "operation1", "reference", "source", "capturedOrder", "control", "trial",
    "trialDisposition", "work",
)
N_OPERATION_KEYS = (
    "operation", "mode", "runId", "sourceManifestSha256", "wireSha256", "receiptPath",
    "receiptBytes", "receiptSha256", "elapsedToCloseNanos", "maxFullGroupNanos",
    "cleanupNanos", "ownersReleased", "contextRestored", "groupCount", "p07OwnerSteps",
    "callbackUnits", "requestIndex", "selectedAttempt", "rootSeed", "placementSeed",
    "routingSeed",
)
N_REFERENCE_KEYS = (
    "receiptOrdinal", "groupOrdinal", "lineageOrdinal", "pass", "netId", "startPadId",
    "rootPadId", "terminalReason", "prefixGeometrySha256", "prefixGeometryBytes",
    "prefixHoleCount", "prefixViaCount", "reachedPadIds", "reachedPthPadIds",
    "reachedPadCount", "reachedPthPadCount",
)
N_SOURCE_RESULT_KEYS = (
    "templateGeometryBeforeSha256", "templateGeometryAfterSha256",
    "templateEffectsBeforeSha256", "templateEffectsAfterSha256", "detachedGeometrySha256",
    "detachedGeometryAfterSha256", "padLands", "templateTraceCount", "templateHoleCount",
    "sourceUnchanged",
)
N_CAPTURE_KEYS = (
    "pass", "failedNetId", "priorPromotedNetId", "netIds", "liveSearchIndex", "owner",
)
N_CAPTURE_OWNER_KEYS = (
    "policy", "maximumExpansions", "expandedAtFailure", "maximumVias", "viaCost",
    "policyTransitions", "policyVias", "branchOrderStrategy", "netOrderStrategy",
    "roleWidths", "orderingStream",
)
N_TRIAL_DISPOSITION_KEYS = (
    "status", "reason", "capturedFailedNetId", "controlFailedNetId", "cohortNetIds",
    "requestedNetIds", "orderChanged", "feedbackValidated",
)
N_WORK_KEYS = (
    "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport", "wrapperGroupsBeforeExport",
    "maxGroupNanosBeforeExport", "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport",
    "originalOwnerSteps", "originalDiagnosticUnits", "originalCaptureUnits",
    "extraTerminalCallbackUnits", "controlOwnerUnits", "controlWrapperUnits", "trialOwnerUnits",
    "trialWrapperUnits", "controlFailureObservationUnits", "trialFailureObservationUnits",
    "controlTerminalEvidenceUnits", "trialTerminalEvidenceUnits", "metadataUnits",
    "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit", "cleanup",
)
N_CLEANUP_KEYS = (
    "originalOwnersReleased", "originalContextRestored", "controlReferencesDropped",
    "trialReferencesDropped", "sourceUnchanged", "allReferencesDropped",
)
PAD_ROW_KEYS = ("padId", "netId", "x", "y", "bounds", "attachment", "layer")
P07_PAD_KEYS = ("record", "group", "padId", "netId", "componentId", "x", "y", "role")

ROLE_TO_ARTIFACT = {
    "N_PRIOR_CALL_OWNER_JSON": "call-owner",
    "N_PRIOR_CALL_PARENT_JSON": "call-parent",
    "N_PRIOR_CALL_PROCESS_JSON": "call-process",
    "N_PRIOR_CALL_REQUEST_JSON": "call-request",
    "N_PRIOR_CALL_RESULT_JSON": "call-result",
    "N_PRIOR_CALL_STDERR_TXT": "call-stderr",
    "N_PRIOR_CALL_STDOUT_TXT": "call-stdout",
    "N_PRIOR_OPERATION1": "data-op-1",
    "N_PRIOR_RESULT": "data-result",
}
ROLE_TO_D_QUALIFICATION = {
    "D_PRIOR_QUALIFICATION_OWNER_JSON": "owner.json",
    "D_PRIOR_QUALIFICATION_PARENT_JSON": "parent.json",
    "D_PRIOR_QUALIFICATION_PROCESS_JSON": "process.json",
    "D_PRIOR_QUALIFICATION_REQUEST_JSON": "request.json",
    "D_PRIOR_QUALIFICATION_RESULT_JSON": "result.json",
    "D_PRIOR_QUALIFICATION_STDERR_TXT": "stderr.txt",
    "D_PRIOR_QUALIFICATION_STDOUT_TXT": "stdout.txt",
}
CALL_DIRECTORY_NAMES = (
    "owner.json", "parent.json", "process.json", "request.json", "result.json",
    "stderr.txt", "stdout.txt",
)
DATA_DIRECTORY_NAMES = ("op-1.jsonl", "result.json")
SOURCE_BINDING_PINS = {
    "dBuilder": D_BUILDER_SHA256,
    "dDescriptorHelper": D_DESCRIPTOR_SHA256,
    "padIdentityHelper": D_PAD_HELPER_SHA256,
    "literalCodec": D_LITERAL_CODEC_SHA256,
    "priorReader": N_PRIOR_READER_SHA256,
    "priorBuilder": N_PRIOR_BUILDER_SHA256,
    "hostReader": HOST_READER_SHA256,
    "p07Reader": P07_SOURCE_SHA256,
    "observationReader": OBSERVATION_READER_SHA256,
    "dWire": D_WIRE_SHA256,
    "priorWire": N_PRIOR_WIRE_SHA256,
    "hostWire": HOST_WIRE_SHA256,
    "contextCodec": CONTEXT_CODEC_SHA256,
    "rootCallHarness": ROOT_CALL_HARNESS_SHA256,
    "processModule": PROCESS_MODULE_SHA256,
}
STATIC_SHA256_PIN_NAMES = (
    "ALLOWLIST_SHA256", "N_PRIOR_READER_SHA256", "N_PRIOR_BUILDER_SHA256",
    "D_BUILDER_SHA256", "D_DESCRIPTOR_SHA256", "D_PAD_HELPER_SHA256",
    "D_LITERAL_CODEC_SHA256", "HOST_READER_SHA256", "P07_SOURCE_SHA256",
    "OBSERVATION_READER_SHA256", "D_WIRE_SHA256", "N_PRIOR_WIRE_SHA256",
    "HOST_WIRE_SHA256", "CONTEXT_CODEC_SHA256", "ROOT_CALL_HARNESS_SHA256",
    "PROCESS_MODULE_SHA256", "POWERSHELL_SHA256", "QUALIFICATION_PYTHON_SHA256",
    "PAD_LAND_SHA256", "PREFIX_SHA256",
)
KERNEL_CALLBACK_KEYS = (
    "stable_cohort_order", "validate_prior_run", "validate_prior_terminal_failure",
    "result_tree_minimum_units", "validate_operation",
)
CODEC_CALLBACK_KEYS = ("validate_context", "canonical_context", "native_projection")
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z")
TOKEN = re.compile(r"[-+A-Za-z0-9_.:]{1,128}\Z")
SIGNED_LONG = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
PAD_HEADER = b"TSJ_PAD_LAND_ASCII_V1\n"
PAD_KEYS = frozenset(PAD_ROW_KEYS)
N_ROOT_SEED = "9007199254740993"
N_PLACEMENT_SEED = "-6587958367974952701"
N_ROUTING_SEED = "8445301674744508279"
PREFIX_SHA256 = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"


class PriorNormalizationError(ValueError):
    """Stable, path-free rejection from the pure prior normalizer."""

    def __init__(self, code):
        super().__init__(code)
        self.code = code


class PriorNormalizationResult(NamedTuple):
    context: dict
    context_bytes: bytes
    native_projection: dict
    prior_result_raw: bytes
    prior_result: dict
    prior_operation_raw: bytes
    prior_operation: dict
    qualification_summary: dict
    authenticated_role_rows: tuple
    external_current_source_authority: dict


def _need(condition, code):
    if not condition:
        raise PriorNormalizationError(code)


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _exact_keys(value, expected, code, *, ordered=False):
    _need(type(value) is dict, code + "_TYPE")
    if ordered:
        _need(tuple(value.keys()) == tuple(expected), code)
    else:
        _need(len(value) == len(expected) and set(value) == set(expected), code)


def _is_int(value, low=-(2**63), high=2**63 - 1):
    return type(value) is int and low <= value <= high


def _is_sha(value):
    return type(value) is str and HEX64.fullmatch(value) is not None


def _validate_static_sha256_pins():
    for name in STATIC_SHA256_PIN_NAMES:
        _need(_is_sha(globals().get(name)), "STATIC_SHA256_PIN_" + name)


def _finite_number(value):
    if type(value) is int:
        return -(2**63) <= value <= 2**63 - 1
    return type(value) is float and math.isfinite(value)


def _reject_nonfinite(_text):
    raise PriorNormalizationError("JSON_NONFINITE")


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        _need(key not in result, "JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _json_load(raw, limit, code, *, allow_float, line=False):
    _need(type(raw) is bytes and 0 < len(raw) <= limit, code + "_SIZE")
    _need(not raw.startswith(b"\xef\xbb\xbf"), code + "_BOM")
    payload = raw
    if line:
        _need(raw.endswith(b"\n") and not raw.endswith(b"\n\n") and b"\r" not in raw,
              code + "_FRAMING")
        payload = raw[:-1]
    try:
        text = payload.decode("utf-8", errors="strict")
        value = json.loads(
            text,
            object_pairs_hook=_pairs_no_duplicates,
            parse_constant=_reject_nonfinite,
            parse_float=(float if allow_float else _reject_nonfinite),
        )
    except PriorNormalizationError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, RecursionError):
        raise PriorNormalizationError(code + "_JSON") from None
    _need(type(value) is dict, code + "_ROOT")
    return value


def _console_json(raw, limit, code):
    """Parse one console JSON line, accepting the pinned host's LF or CRLF."""
    _need(type(raw) is bytes and 0 < len(raw) <= limit, code + "_SIZE")
    if raw.endswith(b"\r\n"):
        payload = raw[:-2]
    elif raw.endswith(b"\n"):
        payload = raw[:-1]
    else:
        raise PriorNormalizationError(code + "_FRAMING")
    _need(payload and b"\r" not in payload and b"\n" not in payload,
          code + "_FRAMING")
    return _json_load(payload, limit, code, allow_float=False)


def _clone_json(value):
    if type(value) is dict:
        return {key: _clone_json(child) for key, child in value.items()}
    if type(value) is list:
        return [_clone_json(child) for child in value]
    return value


def _container_ids(value, output=None):
    if output is None:
        output = set()
    if type(value) is dict or type(value) is list:
        identity = id(value)
        _need(identity not in output, "JSON_ALIAS")
        output.add(identity)
        children = value.values() if type(value) is dict else value
        for child in children:
            _container_ids(child, output)
    return output


def _path_text(value, code):
    _need(type(value) is str and bool(value) and "\x00" not in value
          and "\r" not in value and "\n" not in value, code)
    return value


def _same_host_path(left, right):
    return (type(left) is str and type(right) is str
            and ntpath.normcase(ntpath.normpath(left.replace("/", "\\")))
            == ntpath.normcase(ntpath.normpath(right.replace("/", "\\"))))


def _join_role_path(root, relative):
    _path_text(root, "ROOT_PATH")
    _need(ntpath.isabs(root), "ROOT_ABSOLUTE")
    _need(type(relative) is str and relative and not relative.startswith("/")
          and "\\" not in relative
          and all(part not in ("", ".", "..") for part in relative.split("/")),
          "ROLE_RELATIVE_PATH")
    return ntpath.normpath(ntpath.join(root, relative.replace("/", "\\")))


def _validate_allowlist_bytes(raw, expected_sha256=ALLOWLIST_SHA256):
    """Validate published metadata.  The optional pin is private test-only seam."""
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_ALLOWLIST_BYTES,
          "ALLOWLIST_SIZE")
    _need(_is_sha(expected_sha256) and _sha(raw) == expected_sha256,
          "ALLOWLIST_SHA256")
    value = _json_load(raw, MAX_ALLOWLIST_BYTES, "ALLOWLIST", allow_float=False)
    _exact_keys(value, ("authoritySourceSha256", "helperSourceSha256",
                        "noRecursiveManifestFollowing", "oldDDescriptorResultGeneratedInputDenied",
                        "protectedAndHeldOutDenied", "qualificationProjectionIsCrosscheckOnly",
                        "readingEnabled", "roleCount", "rootPathsArePrivateAndExternallyFixed",
                        "rows", "schema", "status"), "ALLOWLIST_KEYS")
    _need(value["schema"] == ALLOWLIST_SCHEMA and value["status"] == ALLOWLIST_STATUS
          and type(value["roleCount"]) is int and value["roleCount"] == ALLOWLIST_ROLE_COUNT
          and value["authoritySourceSha256"] == D_BUILDER_SHA256
          and value["helperSourceSha256"] == D_DESCRIPTOR_SHA256, "ALLOWLIST_IDENTITY")
    _need(value.get("oldDDescriptorResultGeneratedInputDenied") is True
          and value.get("protectedAndHeldOutDenied") is True
          and value.get("qualificationProjectionIsCrosscheckOnly") is True
          and value.get("noRecursiveManifestFollowing") is True
          and value.get("rootPathsArePrivateAndExternallyFixed") is True
          and value.get("readingEnabled") is False, "ALLOWLIST_POLICY")
    rows = value["rows"]
    _need(type(rows) is list and len(rows) == ALLOWLIST_ROLE_COUNT, "ALLOWLIST_ROLE_COUNT")
    by_role = {}
    for row in rows:
        allowed_keys = ("expectedBytes", "maxBytes", "path", "role", "root", "sha256")
        optional = ("exactContentAuthority", "summaryParseMaxBytes")
        _need(type(row) is dict and set(row).issubset(set(allowed_keys + optional))
              and set(allowed_keys).issubset(row), "ALLOWLIST_ROW_KEYS")
        role = row["role"]
        _need(type(role) is str and role not in by_role, "ALLOWLIST_ROLE_DUPLICATE")
        _need(row["root"] in ("N", "D") and type(row["path"]) is str
              and row["path"] and "\\" not in row["path"]
              and not row["path"].startswith("/")
              and all(part not in ("", ".", "..") for part in row["path"].split("/")),
              "ALLOWLIST_PATH")
        _need(_is_sha(row["sha256"]) and _is_int(row["maxBytes"], 1, 8 * 1024 * 1024),
              "ALLOWLIST_LIMITS")
        expected_bytes = row["expectedBytes"]
        _need(expected_bytes is None or _is_int(expected_bytes, 0, row["maxBytes"]),
              "ALLOWLIST_EXPECTED_LENGTH")
        if "summaryParseMaxBytes" in row:
            _need(expected_bytes is None and (row["summaryParseMaxBytes"] is None
                  or _is_int(row["summaryParseMaxBytes"], 1, 1024)),
                  "ALLOWLIST_SUMMARY_LIMIT")
        if "exactContentAuthority" in row:
            _need(row["exactContentAuthority"] == "PINNED_SOURCE_HASH_NO_INITIAL_DATA_SNAPSHOT",
                  "ALLOWLIST_CONTENT_AUTHORITY")
        by_role[role] = row
    _need(set(by_role) == set(EXPECTED_ROLE_NAMES), "ALLOWLIST_ROLE_NAMES")
    return value, by_role


EXPECTED_ROLE_NAMES = (
    "N_PRIOR_CALL_OWNER_JSON", "N_PRIOR_CALL_PARENT_JSON", "N_PRIOR_CALL_PROCESS_JSON",
    "N_PRIOR_CALL_REQUEST_JSON", "N_PRIOR_CALL_RESULT_JSON", "N_PRIOR_CALL_STDERR_TXT",
    "N_PRIOR_CALL_STDOUT_TXT", "D_PRIOR_QUALIFICATION_OWNER_JSON",
    "D_PRIOR_QUALIFICATION_PARENT_JSON", "D_PRIOR_QUALIFICATION_PROCESS_JSON",
    "D_PRIOR_QUALIFICATION_REQUEST_JSON", "D_PRIOR_QUALIFICATION_RESULT_JSON",
    "D_PRIOR_QUALIFICATION_STDERR_TXT", "D_PRIOR_QUALIFICATION_STDOUT_TXT",
    "N_PRIOR_OPERATION1", "N_PRIOR_RESULT", "D_PRIOR_OBSERVATION_CROSSCHECK",
    "N_PRIOR_RUN_MANIFEST", "N_PRIOR_SOURCE_MANIFEST", "N_PRIOR_CLASS_INVENTORY",
)


def _authenticate_role_bytes(role_bytes, pins):
    """Check every role's type, exact length, cap, and hash before any decode."""
    _exact_keys(role_bytes, EXPECTED_ROLE_NAMES, "ROLE_BYTE_MAP")
    total = 0
    for role in EXPECTED_ROLE_NAMES:
        raw = role_bytes[role]
        row = pins[role]
        _need(type(raw) is bytes, "ROLE_BYTES_TYPE")
        total += len(raw)
        _need(len(raw) <= row["maxBytes"], "ROLE_MAX_BYTES")
        if row["expectedBytes"] is not None:
            _need(len(raw) == row["expectedBytes"], "ROLE_EXACT_BYTES")
        _need(_sha(raw) == row["sha256"], "ROLE_SHA256")
    return total


def _validate_source_bindings(value):
    _exact_keys(value, tuple(SOURCE_BINDING_PINS), "SOURCE_BINDINGS")
    _need(value == SOURCE_BINDING_PINS, "SOURCE_BINDING_PINS")


def _validate_current_source_authority(value):
    """Bind an external pre-DATA SOURCE manifest, never a current run manifest.

    The caller authenticates the SOURCE-only manifest bytes before DATA creation.
    Its digest is supplied here; this pure function does not read or follow it.
    The exact three fields prevent run/class/generated-DATA identities entering
    this authority or the canonical historical context.
    """
    _exact_keys(value, ("schema", "sourceSha256", "authoritySha256"),
                "CURRENT_SOURCE_AUTHORITY_KEYS")
    _need(value.get("schema") == CURRENT_SOURCE_AUTHORITY_SCHEMA, "CURRENT_SOURCE_AUTHORITY_SCHEMA")
    _need(_is_sha(value.get("sourceSha256")), "CURRENT_SOURCE_AUTHORITY_SOURCE_SHA")
    _need(_is_sha(value.get("authoritySha256")), "CURRENT_SOURCE_AUTHORITY_SHA")
    material = {key: child for key, child in value.items() if key != "authoritySha256"}
    try:
        encoded = json.dumps(material, ensure_ascii=True, sort_keys=False,
                             separators=(",", ":"), allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise PriorNormalizationError("CURRENT_SOURCE_AUTHORITY_DOMAIN") from None
    _need(_sha(encoded) == value["authoritySha256"], "CURRENT_SOURCE_AUTHORITY_BINDING")
    return _clone_json(value)


def _validate_frozen_digest(value, field_name, code):
    _need(type(value) is dict and _is_sha(value.get(field_name)), code + "_SHA")
    material = {key: child for key, child in value.items() if key != field_name}
    try:
        encoded = json.dumps(material, ensure_ascii=True, sort_keys=False,
                             separators=(",", ":"), allow_nan=False).encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise PriorNormalizationError(code + "_DOMAIN") from None
    _need(_sha(encoded) == value[field_name], code + "_BINDING")


def _validate_tool_fact(value, code):
    _exact_keys(value, ("path", "bytes", "sha256"), code + "_KEYS")
    _path_text(value["path"], code + "_PATH")
    _need(ntpath.isabs(value["path"]), code + "_PATH_ABSOLUTE")
    _need(_is_int(value["bytes"], 1, 32 * 1024 * 1024) and _is_sha(value["sha256"]),
          code + "_IDENTITY")


def _validate_resource_facts(value, call_kind, process):
    _exact_keys(value, ("directoryNames", "evidenceSha256", "processRoot",
                        "stderrLog", "stdoutLog"), call_kind + "_RESOURCE_KEYS")
    _need(type(value["directoryNames"]) is list
          and value["directoryNames"] == list(CALL_DIRECTORY_NAMES),
          call_kind + "_DIRECTORY_CLOSURE")
    _need(_is_sha(value["evidenceSha256"]), call_kind + "_RESOURCE_EVIDENCE")
    for key, process_key in (("processRoot", "ProcessRoot"),
                             ("stdoutLog", "StdoutLog"),
                             ("stderrLog", "StderrLog")):
        entry = value[key]
        _exact_keys(entry, ("path", "absent"), call_kind + "_" + key.upper() + "_KEYS")
        _path_text(entry["path"], call_kind + "_" + key.upper() + "_PATH")
        _need(entry["path"] == process[process_key] and entry["absent"] is True,
              call_kind + "_" + key.upper() + "_ABSENCE")


def _validate_history_authority(authority, source_manifest):
    _exact_keys(authority, ("schema", "authoritySha256", "kernelProvenanceSha256",
                            "nRoot", "dRoot", "currentSourceAuthority", "sourceBindings",
                            "toolFacts", "resourceFacts"),
                "NORMALIZER_AUTHORITY_KEYS")
    _validate_frozen_digest(authority, "authoritySha256", "NORMALIZER_AUTHORITY")
    _need(authority["schema"] == NORMALIZER_AUTHORITY_SCHEMA, "NORMALIZER_AUTHORITY_SCHEMA")
    _need(_is_sha(authority["kernelProvenanceSha256"]), "KERNEL_PROVENANCE_SHA256")
    _validate_source_bindings(authority["sourceBindings"])
    current = _validate_current_source_authority(authority["currentSourceAuthority"])
    n_root = _path_text(authority["nRoot"], "N_ROOT_PATH")
    d_root = _path_text(authority["dRoot"], "D_ROOT_PATH")
    _need(ntpath.isabs(n_root) and ntpath.isabs(d_root), "AUTHORITY_ROOT_ABSOLUTE")
    tool_facts = authority["toolFacts"]
    tool_keys = ("qualificationPython", "rootCallPowerShell", "nativeJava",
                 "rootCallHarness", "processModule")
    _exact_keys(tool_facts, tool_keys, "TOOL_FACTS")
    for key in tool_keys:
        _validate_tool_fact(tool_facts[key], "TOOL_" + key.upper())
    python = tool_facts["qualificationPython"]
    _need(python["bytes"] == 171744 and python["sha256"] == QUALIFICATION_PYTHON_SHA256,
          "QUALIFICATION_PYTHON_TOOL_PIN")
    powershell = tool_facts["rootCallPowerShell"]
    _need(powershell["bytes"] == 495616 and powershell["sha256"] == POWERSHELL_SHA256,
          "ROOT_CALL_POWERSHELL_PIN")
    harness = tool_facts["rootCallHarness"]
    _need(harness["bytes"] == 2941 and harness["sha256"] == ROOT_CALL_HARNESS_SHA256,
          "ROOT_CALL_HARNESS_PIN")
    process_module = tool_facts["processModule"]
    _need(process_module["bytes"] == 1040144
          and process_module["sha256"] == PROCESS_MODULE_SHA256,
          "PROCESS_MODULE_PIN")
    native = tool_facts["nativeJava"]
    native_row = _find_tool_row(source_manifest, native["path"])
    _need(native_row is not None and native_row["bytes"] == native["bytes"]
          and native_row["sha256"] == native["sha256"], "NATIVE_JAVA_TOOL_BINDING")
    resource_facts = authority["resourceFacts"]
    _exact_keys(resource_facts, ("qualification", "nativePrior", "nativeData"),
                "RESOURCE_FACTS")
    _exact_keys(resource_facts["qualification"],
                ("directoryNames", "evidenceSha256", "processRoot", "stderrLog", "stdoutLog"),
                "QUALIFICATION_RESOURCE_KEYS")
    _exact_keys(resource_facts["nativePrior"],
                ("directoryNames", "evidenceSha256", "processRoot", "stderrLog", "stdoutLog"),
                "NATIVE_RESOURCE_KEYS")
    _exact_keys(resource_facts["nativeData"], ("directoryNames", "evidenceSha256"),
                "NATIVE_DATA_RESOURCE_KEYS")
    _need(resource_facts["nativeData"]["directoryNames"] == list(DATA_DIRECTORY_NAMES)
          and _is_sha(resource_facts["nativeData"]["evidenceSha256"]),
          "NATIVE_DATA_DIRECTORY_CLOSURE")
    return {
        "nRoot": n_root,
        "dRoot": d_root,
        "currentSourceAuthority": current,
        "toolFacts": tool_facts,
        "resourceFacts": resource_facts,
    }


def _find_tool_row(source_manifest, path):
    tools = source_manifest.get("tools") if type(source_manifest) is dict else None
    if type(tools) is not list:
        return None
    matches = []
    for row in tools:
        if type(row) is dict and type(row.get("path")) is str and _same_host_path(row["path"], path):
            matches.append(row)
    if len(matches) != 1:
        return None
    row = matches[0]
    if not _is_int(row.get("bytes"), 1, 32 * 1024 * 1024) or not _is_sha(row.get("sha256")):
        return None
    return row


def _validate_role_paths(pins, authority_values):
    n_root, d_root = authority_values["nRoot"], authority_values["dRoot"]
    for role, row in pins.items():
        root = n_root if row["root"] == "N" else d_root
        expected = _join_role_path(root, row["path"])
        if role.startswith("N_PRIOR_CALL_"):
            _need(_same_host_path(expected, ntpath.join(n_root, "native-observe-r3",
                    row["path"].split("/")[-1])), "N_CALL_ROLE_PATH")
        elif role.startswith("N_PRIOR_OPERATION") or role == "N_PRIOR_RESULT":
            _need(_same_host_path(expected, ntpath.join(n_root, "native-data-r3",
                    row["path"].split("/")[-1])), "N_DATA_ROLE_PATH")
        elif role.startswith("D_PRIOR_QUALIFICATION_"):
            _need(_same_host_path(expected, ntpath.join(d_root, "prior-input-qualify-r2",
                    row["path"].split("/")[-1])), "D_QUALIFICATION_ROLE_PATH")
        elif role == "D_PRIOR_OBSERVATION_CROSSCHECK":
            _need(_same_host_path(expected, ntpath.join(d_root, row["path"])),
                  "D_OBSERVATION_ROLE_PATH")
        elif role.startswith("N_PRIOR_"):
            _need(_same_host_path(expected, ntpath.join(n_root, row["path"])),
                  "N_METADATA_ROLE_PATH")


def _validate_host_call(docs, call_kind, expected_file, expected_args, limit_ms,
                        expected_owner_executable, expected_owner_script,
                        expected_owner_request, tool_facts, resource_facts,
                        *, stdout_expected=None):
    owner, parent, process = docs["owner"], docs["parent"], docs["process"]
    request, result = docs["request"], docs["result"]
    stdout_raw, stderr_raw = docs["stdoutRaw"], docs["stderrRaw"]
    owner_fields = ("schema", "pid", "startTicks", "executable", "script", "request", "createdUtc")
    process_fields = ("FilePath", "Arguments", "ProcessId", "ProcessStartTicks", "ExitCode",
                      "Stdout", "Stderr", "StdoutLog", "StderrLog", "ProcessRoot",
                      "TerminationProven")
    request_fields = ("FilePath", "Arguments", "LimitMs")
    result_fields = ("status", "elapsedMs", "exitCode", "terminationProven", "stdoutBytes",
                     "stderrBytes", "processRootAbsent", "totalElapsedMs")
    _exact_keys(owner, owner_fields, call_kind + "_OWNER_SCHEMA")
    _exact_keys(parent, ("schema", "elapsedMs", "process"), call_kind + "_PARENT_SCHEMA")
    _exact_keys(process, process_fields, call_kind + "_PROCESS_SCHEMA")
    _exact_keys(request, request_fields, call_kind + "_REQUEST_SCHEMA")
    _exact_keys(result, result_fields, call_kind + "_RESULT_SCHEMA")
    _need(owner["schema"] == OWNER_SCHEMA and _is_int(owner["pid"], 1)
          and type(owner["startTicks"]) is str and owner["startTicks"].isdigit()
          and len(owner["startTicks"]) <= 20
          and all(type(owner[key]) is str for key in ("executable", "script", "request", "createdUtc")),
          call_kind + "_OWNER_FIELDS")
    _need(_same_host_path(owner["executable"], expected_owner_executable)
          and _same_host_path(owner["script"], expected_owner_script)
          and _same_host_path(owner["request"], expected_owner_request),
          call_kind + "_OWNER_BINDING")
    _need(parent["schema"] == PARENT_SCHEMA and _finite_number(parent["elapsedMs"])
          and 0 < parent["elapsedMs"] <= limit_ms, call_kind + "_PARENT_TIMING")
    _need(type(process["FilePath"]) is str and _same_host_path(process["FilePath"], expected_file)
          and type(process["Arguments"]) is list and process["Arguments"] == expected_args
          and _is_int(process["ProcessId"], 1)
          and _is_int(process["ProcessStartTicks"], 1, 2**63 - 1)
          and process["ExitCode"] == 0 and type(process["ExitCode"]) is int
          and type(process["Stdout"]) is str and type(process["Stderr"]) is str
          and all(type(process[key]) is str and bool(process[key])
                  for key in ("StdoutLog", "StderrLog", "ProcessRoot"))
          and process["TerminationProven"] is True,
          call_kind + "_PROCESS_FIELDS")
    _need(parent["process"] == process, call_kind + "_PARENT_PROCESS_COPY")
    _need(type(request["FilePath"]) is str and _same_host_path(request["FilePath"], expected_file)
          and request["Arguments"] == expected_args and _is_int(request["LimitMs"], 1, 480_000)
          and request["LimitMs"] == limit_ms,
          call_kind + "_REQUEST_BINDING")
    _need(result["status"] == "RETURNED" and _finite_number(result["elapsedMs"])
          and result["elapsedMs"] == parent["elapsedMs"] and result["exitCode"] == 0
          and type(result["exitCode"]) is int and result["terminationProven"] is True
          and _is_int(result["stdoutBytes"], 0, 8 * 1024 * 1024)
          and _is_int(result["stderrBytes"], 0, 8 * 1024 * 1024)
          and result["processRootAbsent"] is True and _finite_number(result["totalElapsedMs"])
          and result["totalElapsedMs"] >= result["elapsedMs"],
          call_kind + "_RESULT_BINDING")
    try:
        expected_stdout = process["Stdout"].encode("utf-8", errors="strict")
        expected_stderr = process["Stderr"].encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        raise PriorNormalizationError(call_kind + "_STREAM_UTF8") from None
    _need(stdout_raw == expected_stdout and len(stdout_raw) == result["stdoutBytes"]
          and stderr_raw == expected_stderr and len(stderr_raw) == result["stderrBytes"],
          call_kind + "_STREAM_BINDING")
    _need(process["Stderr"] == "" and result["stderrBytes"] == 0 and stderr_raw == b"",
          call_kind + "_STDERR_EMPTY")
    if stdout_expected is not None:
        _need(process["Stdout"] == stdout_expected, call_kind + "_STDOUT_MARKER")
    facts = resource_facts
    _validate_resource_facts(facts, call_kind, process)
    _validate_tool_fact(tool_facts["processExecutable"], call_kind + "_EXECUTABLE_TOOL")
    _validate_tool_fact(tool_facts["ownerExecutable"], call_kind + "_OWNER_EXECUTABLE_TOOL")
    _validate_tool_fact(tool_facts["ownerScript"], call_kind + "_OWNER_SCRIPT_TOOL")
    _validate_tool_fact(tool_facts["processModule"], call_kind + "_PROCESS_MODULE_TOOL")
    _need(_same_host_path(tool_facts["processExecutable"]["path"], expected_file)
          and _same_host_path(tool_facts["ownerExecutable"]["path"], expected_owner_executable)
          and _same_host_path(tool_facts["ownerScript"]["path"], expected_owner_script),
          call_kind + "_TOOL_PATH_BINDING")
    _need(tool_facts["ownerScript"]["bytes"] == 2941
          and tool_facts["ownerScript"]["sha256"] == ROOT_CALL_HARNESS_SHA256
          and tool_facts["processModule"]["bytes"] == 1040144
          and tool_facts["processModule"]["sha256"] == PROCESS_MODULE_SHA256,
          call_kind + "_SOURCE_TOOL_PINS")
    return {"owner": owner, "parent": parent, "process": process,
            "request": request, "result": result, "stdoutRaw": stdout_raw,
            "stderrRaw": stderr_raw}


def _artifact_rows(role_bytes, pins):
    rows = []
    for role, artifact in ROLE_TO_ARTIFACT.items():
        pin = pins[role]
        raw = role_bytes[role]
        _need(pin["root"] == "N", "ARTIFACT_ROOT")
        rows.append({"role": artifact, "path": pin["path"], "bytes": len(raw),
                     "sha256": _sha(raw)})
    return rows


def _qualification_hash_map(role_bytes, pins):
    result = {}
    for role, name in ROLE_TO_D_QUALIFICATION.items():
        _need(pins[role]["root"] == "D", "QUALIFICATION_ROOT")
        result[name] = _sha(role_bytes[role])
    return result


def _validate_prior_metadata(role_bytes, pins):
    run_raw = role_bytes["N_PRIOR_RUN_MANIFEST"]
    source_raw = role_bytes["N_PRIOR_SOURCE_MANIFEST"]
    inventory_raw = role_bytes["N_PRIOR_CLASS_INVENTORY"]
    run = _json_load(run_raw, MAX_SOURCE_METADATA_BYTES, "PRIOR_RUN_MANIFEST", allow_float=False)
    source = _json_load(source_raw, MAX_SOURCE_METADATA_BYTES, "PRIOR_SOURCE_MANIFEST", allow_float=False)
    inventory = _json_load(inventory_raw, MAX_SOURCE_METADATA_BYTES, "PRIOR_CLASS_INVENTORY",
                           allow_float=False)
    _exact_keys(source, N_SOURCE_KEYS, "PRIOR_SOURCE_MANIFEST_KEYS")
    _exact_keys(run, N_RUN_KEYS, "PRIOR_RUN_MANIFEST_KEYS")
    _need(source.get("schema") == "tsj-failure-cohort-source-input-v1"
          and run.get("schema") == "tsj-failure-cohort-source-v1",
          "PRIOR_MANIFEST_SCHEMAS")
    _need(source.get("sourceParent") == SOURCE_PARENT and run.get("sourceParent") == SOURCE_PARENT
          and source.get("compilerSourceCount") == 1090
          and run.get("compilerSourceCount") == 1090,
          "PRIOR_MANIFEST_SOURCE_BINDING")
    _need(type(source.get("sources")) is list and len(source["sources"]) == 1090
          and type(source.get("tools")) is list and len(source["tools"]) == 4
          and type(source.get("wire")) is dict
          and source["wire"].get("sha256") == N_PRIOR_WIRE_SHA256,
          "PRIOR_SOURCE_METADATA")
    seen_paths = set()
    for row in source["sources"]:
        _exact_keys(row, ("path", "bytes", "sha256"), "PRIOR_SOURCE_ROW_KEYS")
        path = _path_text(row["path"], "PRIOR_SOURCE_ROW_PATH")
        _need(_is_int(row["bytes"], 1, 256 * 1024 * 1024) and _is_sha(row["sha256"]),
              "PRIOR_SOURCE_ROW_FIELDS")
        key = ntpath.normcase(ntpath.normpath(path.replace("/", "\\")))
        _need(key not in seen_paths, "PRIOR_SOURCE_ROW_DUPLICATE")
        seen_paths.add(key)
    for key in N_SOURCE_KEYS:
        if key != "schema":
            _need(run.get(key) == source.get(key), "PRIOR_RUN_SOURCE_BINDING")
    _exact_keys(run["build"], N_BUILD_KEYS, "PRIOR_RUN_BUILD_KEYS")
    build = run["build"]
    _need(build.get("sourceManifestSha256") == _sha(source_raw)
          and build.get("classInventorySha256") == _sha(inventory_raw),
          "PRIOR_BUILD_METADATA_BINDING")
    _exact_keys(inventory, ("schema", "sourceManifestSha256", "classCount", "totalBytes", "entries"),
                "PRIOR_CLASS_INVENTORY_KEYS")
    _need(inventory["schema"] == "tsj-failure-cohort-classes-v1"
          and inventory["sourceManifestSha256"] == _sha(source_raw)
          and inventory["classCount"] == 4139 and inventory["totalBytes"] == 19289113
          and build["classCount"] == inventory["classCount"]
          and build["classBytes"] == inventory["totalBytes"]
          and type(inventory["entries"]) is list and len(inventory["entries"]) == 4139,
          "PRIOR_CLASS_INVENTORY_BINDING")
    previous = None
    seen_class_paths = set()
    class_bytes = 0
    for row in inventory["entries"]:
        _exact_keys(row, ("path", "bytes", "sha256"), "PRIOR_CLASS_ENTRY_KEYS")
        path = row["path"]
        _need(type(path) is str and path and not path.startswith("/") and "\\" not in path
              and all(part not in ("", ".", "..") for part in path.split("/")),
              "PRIOR_CLASS_ENTRY_PATH")
        _need(previous is None or previous < path, "PRIOR_CLASS_ENTRY_ORDER")
        previous = path
        folded = path.casefold()
        _need(folded not in seen_class_paths, "PRIOR_CLASS_ENTRY_DUPLICATE")
        seen_class_paths.add(folded)
        _need(_is_int(row["bytes"], 1, 256 * 1024 * 1024) and _is_sha(row["sha256"]),
              "PRIOR_CLASS_ENTRY_FIELDS")
        class_bytes += row["bytes"]
    _need(class_bytes == inventory["totalBytes"], "PRIOR_CLASS_TOTAL_BYTES")
    _need(run.get("build", {}).get("classInventoryPath")
          and run.get("build", {}).get("sourceManifestPath")
          and run.get("classesRoot"), "PRIOR_METADATA_PATH_FIELDS")
    _need(pins["N_PRIOR_RUN_MANIFEST"]["sha256"] == _sha(run_raw)
          and pins["N_PRIOR_SOURCE_MANIFEST"]["sha256"] == _sha(source_raw)
          and pins["N_PRIOR_CLASS_INVENTORY"]["sha256"] == _sha(inventory_raw),
          "PRIOR_METADATA_ROLE_BINDING")
    return run, source, inventory


def _expected_prior_call_arguments(authority, pins, metadata, prior_id, role_bytes):
    n_root, d_root = authority["nRoot"], authority["dRoot"]
    run_sha = _sha(role_bytes["N_PRIOR_RUN_MANIFEST"])
    _need(type(prior_id) is str and UUID.fullmatch(prior_id) is not None,
          "PRIOR_RUN_ID")
    source_manifest = metadata[1]
    n_parent = _join_role_path(n_root, pins["N_PRIOR_CALL_PARENT_JSON"]["path"])
    n_data = _join_role_path(n_root, pins["N_PRIOR_OPERATION1"]["path"].rsplit("/", 1)[0])
    n_manifest = _join_role_path(n_root, pins["N_PRIOR_RUN_MANIFEST"]["path"])
    d_qual_dir = _join_role_path(d_root, pins["D_PRIOR_QUALIFICATION_OWNER_JSON"]["path"].rsplit("/", 1)[0])
    n_reader = ntpath.join(n_root, "read_failure_cohort.py")
    n_source_manifest = _join_role_path(n_root, pins["N_PRIOR_SOURCE_MANIFEST"]["path"])
    d_request = ntpath.join(d_qual_dir, "request.json")
    stage = ntpath.join(n_root, "j8-failure-cohort-r3")
    _need(_same_host_path(source_manifest.get("classesRoot", ""),
                          ntpath.join(stage, "classes"))
          and _same_host_path(metadata[0]["build"].get("sourceManifestPath", ""),
                              n_source_manifest)
          and _same_host_path(metadata[0]["build"].get("classInventoryPath", ""),
                              ntpath.join(stage, "class-inventory.json")),
          "PRIOR_MANIFEST_PATH_BINDING")
    qualification_args = [
        "-I", "-B", n_reader, "--manifest", n_manifest, "--manifest-sha", run_sha,
        "--parent", n_parent, "--data", n_data,
    ]
    java_classpath = _expected_classpath(source_manifest)
    native_args = ["-ea", "-cp", java_classpath, N_ENTRY_CLASS, "FAILURE_COHORT",
                   prior_id, run_sha, N_PRIOR_WIRE_SHA256, n_data]
    return {"qualification": qualification_args, "nativePrior": native_args,
            "nativeData": n_data, "nativeParent": n_parent,
            "nativeManifest": n_manifest, "sourceManifest": n_source_manifest,
            "qualificationDirectory": d_qual_dir,
            "qualificationRequest": d_request}


def _validate_prior_call_authorities(authority, prior_result, role_bytes,
                                     pins, metadata, host_calls):
    n_root, d_root = authority["nRoot"], authority["dRoot"]
    run_sha = _sha(role_bytes["N_PRIOR_RUN_MANIFEST"])
    prior_id = prior_result.get("runId")
    expected = _expected_prior_call_arguments(authority, pins, metadata, prior_id, role_bytes)
    n_parent = expected["nativeParent"]
    n_data = expected["nativeData"]
    n_manifest = expected["nativeManifest"]
    d_qual_dir = expected["qualificationDirectory"]
    n_source_manifest = expected["sourceManifest"]
    d_request = expected["qualificationRequest"]
    _need(host_calls["qualification"]["process"]["Arguments"] == expected["qualification"],
          "QUALIFICATION_ARGUMENTS")
    _need(_same_host_path(host_calls["qualification"]["process"]["FilePath"],
                          authority["toolFacts"]["qualificationPython"]["path"].replace("\\", "/"))
          and _same_host_path(host_calls["qualification"]["owner"]["executable"],
                              authority["toolFacts"]["rootCallPowerShell"]["path"])
          and _same_host_path(host_calls["qualification"]["owner"]["script"],
                              authority["toolFacts"]["rootCallHarness"]["path"])
          and _same_host_path(host_calls["qualification"]["owner"]["request"], d_request),
          "QUALIFICATION_HISTORICAL_TOOL_BINDING")
    n_native = authority["toolFacts"]["nativeJava"]
    _need(host_calls["nativePrior"]["process"]["Arguments"] == expected["nativePrior"],
          "NATIVE_PRIOR_ARGUMENTS")
    _need(_same_host_path(host_calls["nativePrior"]["process"]["FilePath"], n_native["path"])
          and _same_host_path(host_calls["nativePrior"]["request"]["FilePath"], n_native["path"]),
          "NATIVE_PRIOR_EXECUTABLE_BINDING")
    native_call_dir = _join_role_path(n_root, pins["N_PRIOR_CALL_PARENT_JSON"]["path"].rsplit("/", 1)[0])
    qualification_call_dir = d_qual_dir
    for name, call, expected_dir, data_dir in (
        ("nativePrior", host_calls["nativePrior"], native_call_dir, None),
        ("qualification", host_calls["qualification"], qualification_call_dir, None),
    ):
        resource = authority["resourceFacts"][name]
        _need(resource["directoryNames"] == list(CALL_DIRECTORY_NAMES), name + "_CALL_CLOSURE")
        _need(_is_sha(resource["evidenceSha256"]), name + "_CALL_CLOSURE_EVIDENCE")
        expected_request = ntpath.join(expected_dir, "request.json")
        _need(_same_host_path(call["owner"]["request"], expected_request)
              and _same_host_path(call["request"]["FilePath"], n_native["path"]
                                  if name == "nativePrior" else
                                  authority["toolFacts"]["qualificationPython"]["path"].replace("\\", "/")),
              name + "_REQUEST_EXPECTATION")
    data_facts = authority["resourceFacts"]["nativeData"]
    _need(data_facts["directoryNames"] == list(DATA_DIRECTORY_NAMES)
          and _is_sha(data_facts["evidenceSha256"]), "NATIVE_DATA_CLOSURE")
    _need(_same_host_path(n_data, ntpath.join(n_root, "native-data-r3"))
          and _same_host_path(n_manifest, ntpath.join(n_root, "j8-failure-cohort-r3",
                                                       "run-manifest.json"))
          and _same_host_path(n_source_manifest, ntpath.join(n_root, "j8-failure-cohort-r3",
                                                               "source-manifest.json")),
          "PRIOR_ROOT_PATH_BINDING")
    return {"priorRunId": prior_id, "manifestSha256": run_sha,
            "nativeDataDirectory": n_data, "nativeCallDirectory": native_call_dir,
            "qualificationCallDirectory": qualification_call_dir}


def _expected_classpath(source_manifest):
    classes = source_manifest.get("classesRoot")
    tools = source_manifest.get("tools")
    _path_text(classes, "NATIVE_CLASSES_ROOT")
    _need(type(tools) is list and len(tools) == 4, "NATIVE_TOOL_SET")
    by_basename = {}
    for row in tools:
        _need(type(row) is dict and type(row.get("path")) is str, "NATIVE_TOOL_ROW")
        base = ntpath.basename(row["path"].replace("/", "\\")).lower()
        by_basename[base] = row["path"]
    user = by_basename.get("gwt-user-2.7.0.jar")
    dev = by_basename.get("gwt-dev-2.7.0.jar")
    _need(type(user) is str and type(dev) is str, "NATIVE_GWT_JARS")
    return ";".join((classes, user, dev))


def _canonical_pad_identity(rows):
    _need(type(rows) is list and len(rows) == 246, "PAD_LAND_COUNT")
    encoded = []
    seen = set()
    total = len(PAD_HEADER)
    for row in rows:
        _need(type(row) is dict and set(row) == PAD_KEYS, "PAD_LAND_KEYS")
        pad_id, net_id = row["padId"], row["netId"]
        _need(type(pad_id) is str and 1 <= len(pad_id) <= 256
              and type(net_id) is str and 1 <= len(net_id) <= 256
              and all(32 <= ord(character) <= 126 for character in pad_id + net_id),
              "PAD_LAND_TEXT")
        _need(pad_id not in seen, "PAD_LAND_DUPLICATE")
        seen.add(pad_id)
        bounds = row["bounds"]
        _need(type(bounds) is list and len(bounds) == 4, "PAD_LAND_BOUNDS")
        coords = (row["x"], row["y"], *bounds)
        _need(all(_is_int(value, -(2**31), 2**31 - 1) for value in coords)
              and bounds[2] > 0 and bounds[3] > 0,
              "PAD_LAND_COORDINATES")
        attachment, layer = row["attachment"], row["layer"]
        _need(type(attachment) is str and 1 <= len(attachment) <= 64
              and type(layer) is str and 1 <= len(layer) <= 64
              and all(32 <= ord(character) <= 126 for character in attachment + layer),
              "PAD_LAND_PHYSICAL_TEXT")
        def part(text):
            return str(len(text)) + ":" + text + ";"
        text = part(pad_id) + part(net_id)
        text += "".join(str(value) + ";" for value in coords)
        text += part(attachment) + part(layer) + "\n"
        chunk = text.encode("ascii")
        _need(len(chunk) <= MAX_PAD_ROW_BYTES, "PAD_LAND_ROW_BYTES")
        total += len(chunk)
        _need(total <= MAX_PAD_TOTAL_BYTES, "PAD_LAND_TOTAL_BYTES")
        encoded.append((pad_id, chunk))
    encoded.sort(key=lambda item: item[0])
    digest = hashlib.sha256()
    digest.update(PAD_HEADER)
    maximum = 0
    for _pad, chunk in encoded:
        digest.update(chunk)
        maximum = max(maximum, len(chunk))
    result = {"format": "TSJ_PAD_LAND_ASCII_V1", "rowCount": len(rows),
              "canonicalBytes": total, "maximumChunkBytes": maximum,
              "sha256": digest.hexdigest()}
    _need(result["canonicalBytes"] == 20651 and result["maximumChunkBytes"] == 94
          and result["sha256"] == PAD_LAND_SHA256, "PAD_LAND_IDENTITY_PIN")
    return result


def _select_authored_branch(branches):
    _need(type(branches) is list and len(branches) > 307, "OP1_BRANCH_COUNT")
    counts = {}
    selected = None
    for ordinal, branch in enumerate(branches):
        _need(type(branch) is dict and _is_int(branch.get("ordinal"), ordinal, ordinal),
              "OP1_BRANCH_ORDINAL")
        snapshot = branch.get("snapshot")
        _need(type(snapshot) is dict and _is_int(snapshot.get("pass"), 0, 2),
              "OP1_BRANCH_SNAPSHOT")
        current_pass = snapshot["pass"]
        counts[current_pass] = counts.get(current_pass, 0) + 1
        if ordinal == 307:
            selected = branch, counts[current_pass]
    _need(selected is not None and selected[0]["snapshot"]["pass"] == 2
          and selected[1] == 77, "OP1_TARGET_LINEAGE")
    return selected[0], selected[1]


def _parse_prior_operation(raw, entry, prior_run_id, run_sha, callbacks):
    try:
        parsed = callbacks["validate_operation"](
            raw, entry, run_sha, N_PRIOR_WIRE_SHA256, 1, prior_run_id, "AUTHORED")
    except Exception as failure:
        reason = getattr(failure, "code", str(failure))
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise PriorNormalizationError("OP1_P07_VALIDATION_" + reason) from None
        raise PriorNormalizationError("OP1_P07_VALIDATION") from None
    _need(type(parsed) is dict and type(parsed.get("header")) is dict
          and type(parsed.get("pads")) is list and type(parsed.get("branches")) is list
          and type(parsed.get("groups")) is list and type(parsed.get("result")) is dict,
          "OP1_P07_RESULT_SHAPE")
    branch, lineage = _select_authored_branch(parsed["branches"])
    logical_work = parsed["result"].get("logicalWork")
    _need(type(logical_work) is dict and _is_int(logical_work.get("p07OwnerSteps"), 0,
                                                   32768 * 640), "OP1_P07_WORK")
    header = parsed["header"]
    candidate = header.get("candidate")
    _need(type(candidate) is dict and candidate.get("traceWidthProfile") == "ROLE_WIDTHS"
          and candidate.get("componentCount") == 100 and candidate.get("padCount") == 246
          and candidate.get("netCount") == 60, "OP1_CANDIDATE")
    grid_width = branch["snapshot"].get("gridWidth")
    grid_height = branch["snapshot"].get("gridHeight")
    _need(_is_int(grid_width, 1, 250_000) and _is_int(grid_height, 1, 250_000)
          and grid_width * grid_height <= 250_000, "OP1_GRID_BOUNDS")
    for other in parsed["branches"]:
        snap = other.get("snapshot") if type(other) is dict else None
        _need(type(snap) is dict and snap.get("gridWidth") == grid_width
              and snap.get("gridHeight") == grid_height, "OP1_GRID_CONSISTENCY")
    adapted = dict(parsed)
    adapted.update({
        "candidate": candidate, "selectedIndex": branch["ordinal"],
        "selectedBranch": branch, "snapshot": branch["snapshot"],
        "lineageOrdinal": lineage,
        "netOrderRows": [] if parsed.get("netOrder") is None else [parsed["netOrder"]],
        "p07OwnerSteps": logical_work["p07OwnerSteps"],
        "callbackUnits": entry["callbackUnits"], "cleanupNanos": entry["cleanupNanos"],
    })
    return adapted


def _check_prior_operation_entry(operation, parsed, raw, run_sha, prior_id):
    _exact_keys(operation, N_OPERATION_KEYS, "PRIOR_OPERATION_KEYS")
    _need(operation["operation"] == 1 and type(operation["operation"]) is int
          and operation["mode"] == "AUTHORED" and operation["runId"] == prior_id
          and operation["sourceManifestSha256"] == run_sha
          and operation["wireSha256"] == N_PRIOR_WIRE_SHA256
          and operation["receiptPath"] == "op-1.jsonl"
          and _is_int(operation["receiptBytes"], 1, MAX_OPERATION_BYTES)
          and operation["receiptBytes"] == len(raw) and operation["receiptSha256"] == _sha(raw)
          and _is_int(operation["elapsedToCloseNanos"], 1, 90_000_000_000)
          and _is_int(operation["maxFullGroupNanos"], 1, 5_000_000_000)
          and _is_int(operation["cleanupNanos"], 0, 120_000_000_000)
          and operation["ownersReleased"] is True and operation["contextRestored"] is True
          and _is_int(operation["groupCount"], 1, 640)
          and _is_int(operation["p07OwnerSteps"], 0, 32768 * 640)
          and _is_int(operation["callbackUnits"], 0, 32768)
          and operation["requestIndex"] == 0 and type(operation["requestIndex"]) is int
          and operation["selectedAttempt"] == 4 and type(operation["selectedAttempt"]) is int
          and (operation["rootSeed"], operation["placementSeed"], operation["routingSeed"])
              == (N_ROOT_SEED, N_PLACEMENT_SEED, N_ROUTING_SEED),
          "PRIOR_OPERATION_FIELDS")
    _need(type(parsed.get("groups")) is list and type(parsed.get("pads")) is list
          and type(parsed.get("branches")) is list and type(parsed.get("selectedBranch")) is dict
          and type(parsed.get("snapshot")) is dict
          and operation["groupCount"] == len(parsed["groups"])
          and operation["p07OwnerSteps"] == parsed["p07OwnerSteps"],
          "PRIOR_OPERATION_P07_BINDING")
    p07_steps = 0
    max_elapsed = 0
    sum_elapsed = 0
    for index, group in enumerate(parsed["groups"]):
        _need(type(group) is dict and _is_int(group.get("ordinal"), index, index),
              "PRIOR_GROUP_ORDINAL")
        p07 = group.get("p07Steps")
        _need(p07 is None or _is_int(p07, 0, 32768), "PRIOR_GROUP_P07")
        diagnostic = group.get("diagnosticUnits")
        elapsed = group.get("elapsedBeforeOwnLineNanos")
        _need(_is_int(diagnostic, 0, 32768) and _is_int(elapsed, 0, 5_000_000_000),
              "PRIOR_GROUP_FIELDS")
        p07_steps += p07 or 0
        max_elapsed = max(max_elapsed, elapsed)
        sum_elapsed += elapsed
    _need(p07_steps == operation["p07OwnerSteps"]
          and operation["maxFullGroupNanos"] >= max_elapsed
          and operation["elapsedToCloseNanos"] >= sum_elapsed,
          "PRIOR_GROUP_ACCOUNTING")


def _validate_reference(reference, parsed):
    _exact_keys(reference, N_REFERENCE_KEYS, "PRIOR_REFERENCE_KEYS")
    branch, snapshot = parsed["selectedBranch"], parsed["snapshot"]
    _need(_is_int(reference["receiptOrdinal"], 307, 307)
          and _is_int(reference["groupOrdinal"], 0, 639)
          and _is_int(reference["lineageOrdinal"], 77, 77)
          and _is_int(reference["pass"], 2, 2)
          and _is_int(reference["prefixHoleCount"], 48, 48)
          and _is_int(reference["prefixViaCount"], 48, 48)
          and _is_int(reference["reachedPadCount"], 1, 1)
          and _is_int(reference["reachedPthPadCount"], 0, 0)
          and reference["receiptOrdinal"] == branch["ordinal"] == 307
          and reference["groupOrdinal"] == branch["group"] == 98
          and reference["lineageOrdinal"] == parsed["lineageOrdinal"] == 77
          and reference["pass"] == snapshot["pass"] == 2
          and reference["netId"] == snapshot["netId"] == "A_COIL_LOW"
          and reference["startPadId"] == snapshot["startPadId"] == "QA.C"
          and reference["rootPadId"] == snapshot["rootPadId"] == "DA.A"
          and reference["terminalReason"] == snapshot["terminalReason"] == "NO_PATH"
          and reference["prefixGeometrySha256"] == PREFIX_SHA256
          and branch.get("prefixSha256") == PREFIX_SHA256
          and _is_int(reference["prefixGeometryBytes"], 1, MAX_PRIOR_JSON_BYTES)
          and reference["prefixGeometryBytes"] == 265894
          and reference["prefixGeometryBytes"] == branch.get("prefixBytes")
          and reference["prefixViaCount"] == snapshot.get("prefixViaCount")
          and reference["reachedPadIds"] == ["DA.A"]
          and reference["reachedPthPadIds"] == []
          and reference["reachedPadCount"] == snapshot.get("reachedPadCount"),
          "PRIOR_REFERENCE_BINDING")


def _validate_pad_lands(pad_lands, p07_pads):
    _need(type(pad_lands) is list and type(p07_pads) is list
          and len(pad_lands) == len(p07_pads) == 246, "PAD_LAND_CENSUS")
    by_id = {}
    previous = None
    for row in pad_lands:
        _exact_keys(row, PAD_ROW_KEYS, "PAD_LAND_ROW_KEYS")
        pad_id, net_id = row["padId"], row["netId"]
        _need(type(pad_id) is str and TOKEN.fullmatch(pad_id) is not None
              and type(net_id) is str and TOKEN.fullmatch(net_id) is not None
              and (previous is None or previous < pad_id), "PAD_LAND_ID_ORDER")
        previous = pad_id
        _need(_is_int(row["x"], -1_000_000, 1_000_000)
              and _is_int(row["y"], -1_000_000, 1_000_000)
              and row["attachment"] == "PLATED_THROUGH_HOLE"
              and row["layer"] == "TOP+BOTTOM", "PAD_LAND_FIELDS")
        bounds = row["bounds"]
        _need(type(bounds) is list and len(bounds) == 4
              and all(_is_int(item, -100_000_000, 100_000_000) for item in bounds),
              "PAD_LAND_BOUNDS")
        x, y, width, height = bounds
        _need(width > 0 and height > 0
              and -100_000_000 <= x + width <= 100_000_000
              and -100_000_000 <= y + height <= 100_000_000
              and x <= row["x"] <= x + width and y <= row["y"] <= y + height,
              "PAD_LAND_BOUNDS_BINDING")
        by_id[pad_id] = row
    p07_by_id = {}
    role_by_net = {}
    previous_group = None
    for row in p07_pads:
        _exact_keys(row, P07_PAD_KEYS, "P07_PAD_ROW_KEYS")
        _need(row["record"] == "PAD" and _is_int(row["group"], 0, 639),
              "P07_PAD_FIELDS")
        for key in ("padId", "netId", "componentId", "role"):
            _need(type(row[key]) is str and TOKEN.fullmatch(row[key]) is not None,
                  "P07_PAD_TOKEN")
        _need(_is_int(row["x"], -1_000_000, 1_000_000)
              and _is_int(row["y"], -1_000_000, 1_000_000)
              and (previous_group is None or previous_group <= row["group"])
              and row["padId"] not in p07_by_id,
              "P07_PAD_ORDER_OR_DUPLICATE")
        previous_group = row["group"]
        p07_by_id[row["padId"]] = row
        prior_role = role_by_net.setdefault(row["netId"], row["role"])
        _need(prior_role == row["role"], "P07_NET_ROLE_INCONSISTENT")
    _need(set(by_id) == set(p07_by_id), "PAD_LAND_P07_CENSUS")
    for pad_id, land in by_id.items():
        row = p07_by_id[pad_id]
        _need(land["netId"] == row["netId"] and land["x"] == row["x"]
              and land["y"] == row["y"], "PAD_LAND_P07_BINDING")
    return {"byId": by_id, "p07ById": p07_by_id, "roleByNet": role_by_net}


def _validate_captured_order(value, parsed, pad_data):
    _exact_keys(value, N_CAPTURE_KEYS, "CAPTURED_ORDER_KEYS")
    owner = value["owner"]
    _exact_keys(owner, N_CAPTURE_OWNER_KEYS, "CAPTURED_OWNER_KEYS")
    net_ids = value["netIds"]
    snapshot = parsed["snapshot"]
    _need(type(net_ids) is list and len(net_ids) == 60
          and all(type(net) is str and bool(net) for net in net_ids)
          and len(set(net_ids)) == 60
          and _is_int(value["pass"], 2, 2)
          and value["failedNetId"] == snapshot["netId"] == "A_COIL_LOW"
          and value["priorPromotedNetId"] == "D_SENSE"
          and set(net_ids) == set(pad_data["roleByNet"])
          and value["failedNetId"] in net_ids
          and _is_int(value["liveSearchIndex"], 1, len(net_ids))
          and net_ids[value["liveSearchIndex"] - 1] == value["failedNetId"],
          "CAPTURED_VECTOR_BINDING")
    _need(owner["policy"] == "FULLER_TWO_LAYER"
          and _is_int(owner["maximumExpansions"], 1, 1_000_000)
          and owner["maximumExpansions"] == 1_000_000
          and _is_int(owner["expandedAtFailure"], 0, 1_000_000)
          and owner["expandedAtFailure"] == 918_222
          and owner["maximumVias"] == 48 and type(owner["maximumVias"]) is int
          and owner["viaCost"] == 60 and type(owner["viaCost"]) is int
          and owner["policyTransitions"] == 4 and type(owner["policyTransitions"]) is int
          and owner["policyVias"] == 48 and type(owner["policyVias"]) is int
          and owner["branchOrderStrategy"] == "CANONICAL_NEAREST"
          and owner["netOrderStrategy"] == "CANONICAL"
          and owner["roleWidths"] is True and owner["orderingStream"] is True,
          "CAPTURED_OWNER_BINDING")
    return {"owner": owner, "netIds": list(net_ids), "failedNetId": value["failedNetId"],
            "priorPromotedNetId": value["priorPromotedNetId"]}


def _validate_prior_result(doc, parsed, operation_raw, run_sha, callbacks):
    _exact_keys(doc, N_RESULT_KEYS, "PRIOR_RESULT_KEYS")
    _need(doc["schema"] == N_RESULT_SCHEMA and doc["mode"] == N_RESULT_MODE
          and UUID.fullmatch(doc["runId"] or "") is not None
          and doc["finalRunManifestSha256"] == run_sha
          and doc["wireContractSha256"] == N_PRIOR_WIRE_SHA256,
          "PRIOR_RESULT_IDENTITY")
    operation = doc["operation1"]
    _check_prior_operation_entry(operation, parsed, operation_raw, run_sha, doc["runId"])
    _validate_reference(doc["reference"], parsed)
    source = doc["source"]
    _exact_keys(source, N_SOURCE_RESULT_KEYS, "PRIOR_SOURCE_KEYS")
    digest_keys = (
        "templateGeometryBeforeSha256", "templateGeometryAfterSha256",
        "templateEffectsBeforeSha256", "templateEffectsAfterSha256",
        "detachedGeometrySha256", "detachedGeometryAfterSha256",
    )
    _need(all(_is_sha(source[key]) for key in digest_keys)
          and source["templateGeometryBeforeSha256"] == source["templateGeometryAfterSha256"]
          and source["templateEffectsBeforeSha256"] == source["templateEffectsAfterSha256"]
          and source["detachedGeometrySha256"] == source["detachedGeometryAfterSha256"]
          and _is_int(source["templateTraceCount"], 0, 0)
          and _is_int(source["templateHoleCount"], 0, 0)
          and source["sourceUnchanged"] is True, "PRIOR_SOURCE_IMMUTABILITY")
    pad_data = _validate_pad_lands(source["padLands"], parsed["pads"])
    captured_doc = doc["capturedOrder"]
    captured = _validate_captured_order(captured_doc, parsed, pad_data)
    trial = doc["trialDisposition"]
    _exact_keys(trial, N_TRIAL_DISPOSITION_KEYS, "PRIOR_TRIAL_DISPOSITION_KEYS")
    _need(trial["status"] == "RUN" and trial["reason"] == "TRIAL_EXECUTED"
          and trial["capturedFailedNetId"] == captured["failedNetId"]
          and trial["controlFailedNetId"] == "B_COIL_LOW"
          and trial["cohortNetIds"] == ["A_COIL_LOW", "B_COIL_LOW"]
          and trial["requestedNetIds"] == _call_kernel(
              callbacks, "stable_cohort_order", captured["netIds"], trial["cohortNetIds"])
          and trial["orderChanged"] is True and trial["feedbackValidated"] is True,
          "PRIOR_TRIAL_DISPOSITION_BINDING")
    control_result = _validate_prior_run(callbacks, doc["control"],
                                         "OBSERVED_FAILURE_CONTROL", captured,
                                         [captured["failedNetId"]], control=True)
    trial_result = _validate_prior_run(callbacks, doc["trial"],
                                       "OBSERVED_FAILURE_COHORT_TRIAL", captured,
                                       trial["cohortNetIds"], control=False)
    _need(control_result["outcome"].get("disposition") == "NO_PATH"
          and control_result["terminalFailure"].get("failedNetId") == "B_COIL_LOW"
          and trial_result["outcome"].get("disposition") == "CAP_REJECTED"
          and trial_result["expectedOrder"] == trial["requestedNetIds"],
          "PRIOR_RUN_COHORT_BINDING")
    work = doc["work"]
    minimum_units = _call_kernel(callbacks, "result_tree_minimum_units", doc)
    _validate_prior_work(work, operation, parsed, doc["reference"], control_result,
                         trial_result, minimum_units)
    for run, facts, is_control in ((doc["control"], control_result, True),
                                   (doc["trial"], trial_result, False)):
        try:
            terminal = callbacks["validate_prior_terminal_failure"](
                run.get("terminalFailure"), run["outcome"], run["ordering"], run["work"],
                control=is_control)
        except Exception as failure:
            reason = getattr(failure, "code", str(failure))
            if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
                raise PriorNormalizationError("PRIOR_TERMINAL_FAILURE_" + reason) from None
            raise PriorNormalizationError("PRIOR_TERMINAL_FAILURE") from None
        _need(terminal == run.get("terminalFailure")
              and facts["terminalFailure"] == run.get("terminalFailure"),
              "PRIOR_TERMINAL_FAILURE_BINDING")
    return {"captured": captured, "padData": pad_data, "control": control_result,
            "trial": trial_result, "operation": operation, "minimumUnits": minimum_units}


def _call_kernel(callbacks, name, *args, **kwargs):
    try:
        return callbacks[name](*args, **kwargs)
    except Exception as failure:
        reason = getattr(failure, "code", str(failure))
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise PriorNormalizationError("KERNEL_" + name.upper() + "_" + reason) from None
        raise PriorNormalizationError("KERNEL_" + name.upper()) from None


def _validate_prior_run(callbacks, run, kind, captured, cohort_ids, *, control):
    try:
        result = callbacks["validate_prior_run"](run, kind, captured, cohort_ids)
    except Exception as failure:
        reason = getattr(failure, "code", str(failure))
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise PriorNormalizationError("PRIOR_RUN_" + reason) from None
        raise PriorNormalizationError("PRIOR_RUN_VALIDATION") from None
    _need(type(result) is dict and type(result.get("outcome")) is dict
          and type(result.get("ordering")) is dict and type(result.get("work")) is dict
          and (result.get("terminalFailure") is None or type(result.get("terminalFailure")) is dict),
          "PRIOR_RUN_CALLBACK_RESULT")
    try:
        terminal = callbacks["validate_prior_terminal_failure"](
            run.get("terminalFailure"), run["outcome"], run["ordering"], run["work"],
            control=control)
    except Exception as failure:
        reason = getattr(failure, "code", str(failure))
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise PriorNormalizationError("PRIOR_TERMINAL_FAILURE_" + reason) from None
        raise PriorNormalizationError("PRIOR_TERMINAL_FAILURE") from None
    _need(terminal == run.get("terminalFailure")
          and result.get("terminalFailure") == terminal,
          "PRIOR_RUN_TERMINAL_BINDING")
    expected = _call_kernel(callbacks, "stable_cohort_order", captured["netIds"], cohort_ids)
    _need(result.get("expectedOrder") == expected, "PRIOR_RUN_ORDER_BINDING")
    return result


def _validate_prior_work(work, operation, parsed, reference, control, trial, minimum_units):
    _exact_keys(work, N_WORK_KEYS, "PRIOR_WORK_KEYS")
    numeric = N_WORK_KEYS[:-1]
    _need(all(_is_int(work[key], 0, 2**63 - 1) for key in numeric), "PRIOR_WORK_INTEGER")
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
        _need(units <= 32768, "ORIGINAL_CALLBACK_GROUP_LIMIT")
        p07_steps += p07
        diagnostic_units += diagnostic
        original_max_ns = max(original_max_ns, group["elapsedBeforeOwnLineNanos"])
        original_max_units = max(original_max_units, units)
    _need(work["originalOwnerSteps"] == p07_steps == operation["p07OwnerSteps"]
          and work["originalDiagnosticUnits"] == diagnostic_units
          and work["originalCaptureUnits"] == operation["callbackUnits"],
          "ORIGINAL_WORK_BINDING")
    control_work = control["work"]
    trial_work = trial["work"]
    _need(work["controlOwnerUnits"] == control_work["ownerUnits"]
          and work["controlWrapperUnits"] == control_work["wrapperUnits"]
          and work["controlFailureObservationUnits"] == control_work["failureObservationUnits"]
          and work["controlTerminalEvidenceUnits"] == control_work["terminalEvidenceUnits"]
          and work["trialOwnerUnits"] == trial_work["ownerUnits"]
          and work["trialWrapperUnits"] == trial_work["wrapperUnits"]
          and work["trialFailureObservationUnits"] == trial_work["failureObservationUnits"]
          and work["trialTerminalEvidenceUnits"] == trial_work["terminalEvidenceUnits"],
          "PRIOR_RUN_WORK_BINDING")
    additive = (operation["p07OwnerSteps"] + diagnostic_units + operation["callbackUnits"]
                + work["extraTerminalCallbackUnits"] + work["controlOwnerUnits"]
                + work["controlWrapperUnits"] + work["trialOwnerUnits"]
                + work["trialWrapperUnits"] + work["metadataUnits"])
    _need(work["unitsBeforeExport"] == additive and work["metadataUnits"] >= minimum_units,
          "PRIOR_WORK_UNITS")
    groups = control_work["groups"] + trial_work["groups"]
    _need(work["groupsBeforeExport"] == operation["groupCount"] + groups
          + work["wrapperGroupsBeforeExport"]
          and work["wrapperGroupsBeforeExport"] >= 1
          and work["groupsBeforeExport"] + 1 <= 640,
          "PRIOR_WORK_GROUPS")
    max_ns = max(operation["maxFullGroupNanos"], original_max_ns,
                 control_work["maxGroupNanos"], trial_work["maxGroupNanos"])
    max_units = max(original_max_units, control_work["maximumUnitsPerGroup"],
                    trial_work["maximumUnitsPerGroup"])
    control_entries = control_work["copyEntries"]
    trial_entries = trial_work["copyEntries"]
    control_chunks = control_work["copyChunks"]
    trial_chunks = trial_work["copyChunks"]
    copied = work["copiedEntries"]
    chunks = work["copyChunks"]
    _need(work["maxGroupNanosBeforeExport"] >= max_ns
          and 1 <= work["maxGroupNanosBeforeExport"] <= 5_000_000_000
          and work["maximumUnitsPerGroupBeforeExport"] >= max_units
          and 1 <= work["maximumUnitsPerGroupBeforeExport"] <= 32768
          and work["unitsBeforeExport"] <= work["groupsBeforeExport"] * 32768
          and copied <= 1_048_576 and work["copyChunkLimit"] == 4096
          and work["copiedEntryLimit"] == 1_048_576
          and copied >= control_entries + trial_entries
          and chunks >= control_chunks + trial_chunks and chunks <= copied
          and (copied == 0 or chunks >= (copied + 4095) // 4096)
          and work["cleanupNanos"] >= operation["cleanupNanos"]
          and work["cleanupNanos"] <= 120_000_000_000
          and 0 < work["elapsedCutoffNanos"] <= 90_000_000_000,
          "PRIOR_WORK_BOUNDS")
    cleanup = work["cleanup"]
    _exact_keys(cleanup, N_CLEANUP_KEYS, "PRIOR_CLEANUP_KEYS")
    _need(all(cleanup[key] is True for key in N_CLEANUP_KEYS), "PRIOR_CLEANUP_PROOF")


def _validate_qualification_summary(raw, d_parent, d_result, prior_result, role_bytes,
                                    pins, operation_raw, result_raw):
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_QUALIFICATION_STDOUT
          and len(raw) == 812,
          "QUALIFICATION_STDOUT_SIZE")
    summary = _json_load(raw, MAX_QUALIFICATION_STDOUT, "QUALIFICATION_STDOUT",
                         allow_float=False, line=True)
    _exact_keys(summary, QUALIFICATION_SUMMARY_KEYS, "QUALIFICATION_SUMMARY_KEYS")
    _need(summary["schema"] == "tsj-failure-cohort-reader-v1"
          and summary["status"] == QUALIFICATION_STATUS
          and summary["manifestSha256"] == _sha(role_bytes["N_PRIOR_RUN_MANIFEST"])
          and summary["readerSha256"] == N_PRIOR_READER_SHA256
          and summary["resultSha256"] == _sha(result_raw)
          and summary["operation1Sha256"] == _sha(operation_raw)
          and summary["trialDisposition"] == "TRIAL_EXECUTED"
          and summary["netCount"] == 60 and type(summary["netCount"]) is int
          and summary["controlDisposition"] == "NO_PATH"
          and summary["trialOutcomeDisposition"] == "CAP_REJECTED"
          and summary["controlLayoutConnectivity"] is None
          and summary["trialLayoutConnectivity"] is None,
          "QUALIFICATION_SUMMARY_BINDING")
    for key in ("controlTraversedCount", "trialTraversedCount"):
        _need(_is_int(summary[key], 0, 60), "QUALIFICATION_TRAVERSAL_COUNT")
    for key in ("layoutQualification", "routingTreatmentSelected", "productionAdopted",
                "feasibilityClaimed", "playable100Accepted"):
        _need(type(summary[key]) is bool and summary[key] is False,
              "QUALIFICATION_CLAIM_TYPE_OR_VALUE")
    _need(summary["controlTraversedCount"] == 44
          and summary["trialTraversedCount"] == 43,
          "QUALIFICATION_TRAVERSAL_FIXED")
    parent_elapsed = d_parent["elapsedMs"]
    total_elapsed = d_result["totalElapsedMs"]
    _need(type(parent_elapsed) is float and type(total_elapsed) is float
          and repr(parent_elapsed) == "112691.18650000001"
          and repr(total_elapsed) == "112943.8217", "QUALIFICATION_ELAPSED_BINDING")
    _need(type(prior_result.get("control")) is dict and type(prior_result.get("trial")) is dict
          and _is_int(summary["controlTraversedCount"], 0, 60)
          and _is_int(summary["trialTraversedCount"], 0, 60),
          "QUALIFICATION_PRIOR_RESULT_BINDING")
    return summary


def _build_context(role_bytes, pins, metadata, host_calls, qualification_summary,
                   prior_result, prior_result_raw, prior_operation, prior_operation_raw,
                   parsed_operation, prior_facts, callbacks, codec):
    run_sha = _sha(role_bytes["N_PRIOR_RUN_MANIFEST"])
    source_sha = _sha(role_bytes["N_PRIOR_SOURCE_MANIFEST"])
    result_sha = _sha(prior_result_raw)
    operation_sha = _sha(prior_operation_raw)
    parent_sha = _sha(role_bytes["N_PRIOR_CALL_PARENT_JSON"])
    trial = prior_result["trialDisposition"]
    op_summary = prior_result["operation1"]
    capture_doc = prior_result["capturedOrder"]
    source_doc = prior_result["source"]
    pad_identity = _canonical_pad_identity(source_doc["padLands"])
    _need(pad_identity["sha256"] == PAD_LAND_SHA256, "PRIOR_PAD_IDENTITY")
    prior = {
        "sourceParent": metadata[0]["sourceParent"],
        "manifestSha256": run_sha,
        "sourceManifestSha256": source_sha,
        "readerSha256": qualification_summary["readerSha256"],
        "resultSha256": result_sha,
        "operation1Sha256": operation_sha,
        "parentReceiptSha256": parent_sha,
        "priorRunId": prior_result["runId"],
        "priorOriginalReceipt": {
            "receiptPath": "op-1.jsonl", "receiptBytes": len(prior_operation_raw),
            "receiptSha256": operation_sha, "receiptOrdinal": 307, "lineageOrdinal": 77,
        },
        "trialDisposition": _clone_json(trial),
    }
    capture = {
        "cohortNetIds": _clone_json(trial["cohortNetIds"]),
        "capturedNetIds": _clone_json(capture_doc["netIds"]),
        "trialDisposition": _clone_json(trial),
        "controlDisposition": prior_result["control"]["outcome"]["disposition"],
        "trialOutcome": prior_result["trial"]["outcome"]["disposition"],
        "passIndex": capture_doc["pass"], "failedNetId": capture_doc["failedNetId"],
        "priorPromotedNetId": capture_doc["priorPromotedNetId"],
        "liveSearchIndex": capture_doc["liveSearchIndex"],
    }
    owner = capture_doc["owner"]
    owner_settings = {
        "boardMode": owner["policy"], "maxExpansions": owner["maximumExpansions"],
        "expandedAtFailure": owner["expandedAtFailure"], "maxVias": owner["maximumVias"],
        "viaCost": owner["viaCost"], "maxTransitions": owner["policyTransitions"],
        "branchStrategy": owner["branchOrderStrategy"], "netStrategy": owner["netOrderStrategy"],
        "roleWidthsEnabled": owner["roleWidths"],
        "orderingStreamEnabled": owner["orderingStream"],
    }
    seeds = {key: op_summary[key] for key in ("rootSeed", "placementSeed", "routingSeed")}
    source_identity = {
        "geometryBeforeSha256": source_doc["templateGeometryBeforeSha256"],
        "geometryAfterSha256": source_doc["templateGeometryAfterSha256"],
        "detachedGeometrySha256": source_doc["detachedGeometrySha256"],
        "effectsBeforeSha256": source_doc["templateEffectsBeforeSha256"],
        "effectsAfterSha256": source_doc["templateEffectsAfterSha256"],
        "traceCount": source_doc["templateTraceCount"],
        "holeCount": source_doc["templateHoleCount"],
        "padLandIdentity": {"format": pad_identity["format"], "rowCount": pad_identity["rowCount"],
                            "sha256": pad_identity["sha256"]},
    }
    grid_width = parsed_operation["snapshot"]["gridWidth"]
    grid_height = parsed_operation["snapshot"]["gridHeight"]
    physical_context = {
        "gridWidth": grid_width,
        "gridHeight": grid_height,
        "padLands": source_doc["padLands"],
        "qualifiedP07Pads": parsed_operation["pads"],
    }
    context = {
        "schema": "tsj-cohort-terminal-input-context-v2",
        "nativeProjection": {
            "prior": prior,
            "capture": capture,
            "ownerSettings": owner_settings,
            "seedValues": seeds,
            "sourceIdentity": source_identity,
        },
        "physicalContext": physical_context,
    }
    _call_codec(codec, "validate_context", context)
    canonical = _call_codec(codec, "canonical_context", context)
    _need(type(canonical) is bytes and canonical.endswith(b"\n"), "CONTEXT_CANONICAL_BYTES")
    projection = _call_codec(codec, "native_projection", context)
    _need(type(projection) is dict and tuple(projection.keys())
          == ("prior", "capture", "ownerSettings", "seedValues", "sourceIdentity"),
          "NATIVE_PROJECTION_KEYS")
    _need(projection == context["nativeProjection"] and projection is not context["nativeProjection"],
          "NATIVE_PROJECTION_COPY")
    _need(_container_ids(context).isdisjoint(_container_ids(projection)),
          "NATIVE_PROJECTION_DETACHED")
    return context, canonical, projection


def _call_codec(codec, name, *args):
    try:
        return codec[name](*args)
    except Exception as failure:
        reason = getattr(failure, "code", str(failure))
        if type(reason) is str and re.fullmatch(r"[A-Za-z0-9_]{1,96}", reason):
            raise PriorNormalizationError("CONTEXT_" + name.upper() + "_" + reason) from None
        raise PriorNormalizationError("CONTEXT_" + name.upper()) from None


def normalize_prior(role_bytes, allowlist_bytes, authority, kernel_callbacks, codec_callbacks):
    """Authenticate and normalize the fixed prior evidence from caller bytes.

    `authority` is separately frozen by the reader/caller and has this exact
    shape: schema, authoritySha256, kernelProvenanceSha256, nRoot, dRoot,
    currentSourceAuthority, sourceBindings, toolFacts, resourceFacts.  Its
    authority SHA covers its other fields.  It contains no current
    run/manifest identity.
    `resourceFacts` contains caller-observed call/data directory closure and
    process/log absence paths.  `toolFacts` contains caller-frozen path, byte
    count, and SHA-256 tuples for the selected tools.  This function verifies
    their bindings but never creates those facts itself.
    """
    # Guard the source-bound pin vector before authenticating or decoding any
    # caller-supplied role body.
    _validate_static_sha256_pins()
    _exact_keys(kernel_callbacks, KERNEL_CALLBACK_KEYS, "KERNEL_CALLBACKS")
    _need(all(callable(kernel_callbacks[key]) for key in KERNEL_CALLBACK_KEYS),
          "KERNEL_CALLBACK_TYPE")
    _exact_keys(codec_callbacks, CODEC_CALLBACK_KEYS, "CODEC_CALLBACKS")
    _need(all(callable(codec_callbacks[key]) for key in CODEC_CALLBACK_KEYS), "CODEC_CALLBACK_TYPE")

    # Published metadata authentication precedes any decode of the 20 role bodies.
    allowlist, pins = _validate_allowlist_bytes(allowlist_bytes)
    total_role_bytes = _authenticate_role_bytes(role_bytes, pins)
    # All normalizer decoding happens after every role length/cap/SHA passed.

    qualification_prefix_roles = {
        "owner": "D_PRIOR_QUALIFICATION_OWNER_JSON",
        "parent": "D_PRIOR_QUALIFICATION_PARENT_JSON",
        "process": "D_PRIOR_QUALIFICATION_PROCESS_JSON",
        "request": "D_PRIOR_QUALIFICATION_REQUEST_JSON",
        "result": "D_PRIOR_QUALIFICATION_RESULT_JSON",
        "stderr": "D_PRIOR_QUALIFICATION_STDERR_TXT",
        "stdout": "D_PRIOR_QUALIFICATION_STDOUT_TXT",
    }
    native_prefix_roles = {
        "owner": "N_PRIOR_CALL_OWNER_JSON",
        "parent": "N_PRIOR_CALL_PARENT_JSON",
        "process": "N_PRIOR_CALL_PROCESS_JSON",
        "request": "N_PRIOR_CALL_REQUEST_JSON",
        "result": "N_PRIOR_CALL_RESULT_JSON",
        "stderr": "N_PRIOR_CALL_STDERR_TXT",
        "stdout": "N_PRIOR_CALL_STDOUT_TXT",
    }
    metadata = _validate_prior_metadata(role_bytes, pins)
    authority_values = _validate_history_authority(authority, metadata[1])
    _validate_role_paths(pins, authority_values)
    prior_result_raw = role_bytes["N_PRIOR_RESULT"]
    prior_operation_raw = role_bytes["N_PRIOR_OPERATION1"]
    prior_result = _json_load(prior_result_raw, MAX_PRIOR_JSON_BYTES, "PRIOR_RESULT",
                              allow_float=False, line=True)
    expected_arguments = _expected_prior_call_arguments(
        authority_values, pins, metadata, prior_result.get("runId"), role_bytes)
    host_calls = {}
    for call_name, role_names in (("qualification", qualification_prefix_roles),
                                  ("nativePrior", native_prefix_roles)):
        docs = {}
        for key, role in role_names.items():
            raw = role_bytes[role]
            if key in ("stdout", "stderr"):
                docs[key + "Raw"] = raw
            else:
                docs[key] = _json_load(raw, MAX_HOST_RECEIPT_BYTES,
                                        call_name.upper() + "_" + key.upper(), allow_float=True)
        process_tool = authority_values["toolFacts"][
            "qualificationPython" if call_name == "qualification" else "nativeJava"]
        call_tools = {
            "processExecutable": process_tool,
            "ownerExecutable": authority_values["toolFacts"]["rootCallPowerShell"],
            "ownerScript": authority_values["toolFacts"]["rootCallHarness"],
            "processModule": authority_values["toolFacts"]["processModule"],
        }
        expected_owner_request = _join_role_path(
            authority_values["dRoot"] if call_name == "qualification" else authority_values["nRoot"],
            pins[role_names["request"]]["path"])
        validated = _validate_host_call(
            docs, call_name.upper(), process_tool["path"],
            expected_arguments[call_name],
            180_000 if call_name == "qualification" else 480_000,
            authority_values["toolFacts"]["rootCallPowerShell"]["path"],
            authority_values["toolFacts"]["rootCallHarness"]["path"],
            expected_owner_request, call_tools,
            authority_values["resourceFacts"][call_name])
        host_calls[call_name] = validated

    prior_call_facts = _validate_prior_call_authorities(
        authority_values, prior_result, role_bytes, pins, metadata, host_calls)
    qualification_raw = role_bytes["D_PRIOR_QUALIFICATION_STDOUT_TXT"]
    qualification_summary = _validate_qualification_summary(
        qualification_raw, host_calls["qualification"]["parent"],
        host_calls["qualification"]["result"], prior_result, role_bytes, pins,
        prior_operation_raw, prior_result_raw)
    n_result_raw = host_calls["nativePrior"]["stdoutRaw"]
    marker = _console_json(n_result_raw, 64 * 1024, "NATIVE_PRIOR_MARKER")
    _exact_keys(marker, ("schema", "status", "resultSha256", "resultBytes"), "NATIVE_MARKER_KEYS")
    _need(marker["schema"] == N_MARKER_SCHEMA and marker["status"] == "COMPLETED_DIAGNOSTIC"
          and marker["resultSha256"] == _sha(prior_result_raw)
          and _is_int(marker["resultBytes"], 1, MAX_PRIOR_JSON_BYTES)
          and marker["resultBytes"] == len(prior_result_raw), "NATIVE_MARKER_BINDING")

    run_sha = _sha(role_bytes["N_PRIOR_RUN_MANIFEST"])
    source_manifest = metadata[1]
    prior_id = prior_result.get("runId")
    operation_entry = prior_result.get("operation1")
    _need(type(operation_entry) is dict, "PRIOR_OPERATION_ENTRY")
    parsed_operation = _parse_prior_operation(
        prior_operation_raw, operation_entry, prior_id, run_sha, kernel_callbacks)
    prior_facts = _validate_prior_result(prior_result, parsed_operation,
                                         prior_operation_raw, run_sha, kernel_callbacks)
    _need(parsed_operation["pads"] == prior_result["source"]["padLands"]
          or len(parsed_operation["pads"]) == len(prior_result["source"]["padLands"]) == 246,
          "PRIOR_OPERATION_PAD_COUNT")
    # Reconcile authenticated qualification projection against raw independent facts.
    observation = _json_load(role_bytes["D_PRIOR_OBSERVATION_CROSSCHECK"],
                             MAX_OBSERVATION_BYTES, "OBSERVATION_CROSSCHECK", allow_float=False)
    _need(observation.get("status") == "PASS_PRIOR_INPUT_ONLY"
          and observation.get("projectionIsFrozenDescriptor") is False
          and type(observation.get("newAuthoredSessions")) is int
          and observation.get("newAuthoredSessions") == 0
          and observation.get("qualificationSummary") == qualification_summary,
          "OBSERVATION_QUALIFICATION_BINDING")
    _need(observation.get("qualificationReceiptHashes") ==
          _qualification_hash_map(role_bytes, pins), "OBSERVATION_RECEIPT_HASHES")
    expected_artifacts = _artifact_rows(role_bytes, pins)
    projected_artifacts = observation.get("approvedPriorArtifactRows")
    _need(type(projected_artifacts) is list and len(projected_artifacts) == 9,
          "OBSERVATION_ARTIFACT_ROWS")
    for row in projected_artifacts:
        _exact_keys(row, ("role", "path", "bytes", "sha256"), "OBSERVATION_ARTIFACT_ROW_KEYS")
    _need(projected_artifacts == expected_artifacts, "OBSERVATION_ARTIFACT_BINDING")
    pads_count = len(prior_result["source"]["padLands"])
    captured_count = len(prior_result["capturedOrder"]["netIds"])
    _need(observation.get("projectedCohortNetIds")
          == prior_result["trialDisposition"]["cohortNetIds"]
          and observation.get("capturedVectorCount") == captured_count
          and observation.get("padLandCount") == pads_count == 246,
          "OBSERVATION_RAW_RESULT_BINDING")
    n_call_sha_map = {name: _sha(role_bytes[role]) for role, name in ROLE_TO_ARTIFACT.items()
                      if name.startswith("call-")}
    _need(observation.get("qualificationReceiptHashes") == _qualification_hash_map(role_bytes, pins),
          "OBSERVATION_QUALIFICATION_SHA_MAP")

    # Qualification stdout counts are cross-checked against the parsed prior runs.
    control_count = len(prior_result["control"]["ordering"]["traversedNetIds"])
    trial_count = len(prior_result["trial"]["ordering"]["traversedNetIds"])
    _need(qualification_summary["controlTraversedCount"] == control_count
          and qualification_summary["trialTraversedCount"] == trial_count,
          "QUALIFICATION_TRAVERSAL_BINDING")
    context, canonical, projection = _build_context(
        role_bytes, pins, metadata, host_calls, qualification_summary,
        prior_result, prior_result_raw, prior_result["operation1"], prior_operation_raw,
        parsed_operation, prior_facts, kernel_callbacks, codec_callbacks)
    role_rows = tuple({"role": role, "root": pins[role]["root"],
                       "path": pins[role]["path"], "bytes": len(role_bytes[role]),
                       "sha256": pins[role]["sha256"], "maxBytes": pins[role]["maxBytes"]}
                      for role in EXPECTED_ROLE_NAMES)
    # Keep these internal bindings explicit. They are not canonical-context fields.
    _need(len(role_rows) == ALLOWLIST_ROLE_COUNT and total_role_bytes >= 0
          and source_manifest["schema"] == "tsj-failure-cohort-source-input-v1"
          and n_call_sha_map["call-parent"] == parent_sha_from_role(role_bytes)
          and prior_call_facts["priorRunId"] == prior_result["runId"],
          "NORMALIZER_FINAL_BINDING")
    return PriorNormalizationResult(
        context=context,
        context_bytes=canonical,
        native_projection=projection,
        prior_result_raw=prior_result_raw,
        prior_result=prior_result,
        prior_operation_raw=prior_operation_raw,
        prior_operation=parsed_operation,
        qualification_summary=qualification_summary,
        authenticated_role_rows=role_rows,
        external_current_source_authority=authority_values["currentSourceAuthority"],
    )


def parent_sha_from_role(role_bytes):
    return _sha(role_bytes["N_PRIOR_CALL_PARENT_JSON"])
