"""Pure codec for the authored Q100 terminal telemetry input context.

This module deliberately has no filesystem, process, environment, or dynamic
loader behavior. It validates current context structure and emits nativeProjection
as generated Java DATA. It does not authenticate prior artifacts or qualify the
context's physical data.
"""

import hashlib
import json
import re


CONTEXT_SCHEMA = "tsj-cohort-terminal-input-context-v2"
GENERATED_CLASS = "CohortTerminalTelemetryInputV2"
JAVA_PACKAGE = "com.lushprojects.circuitjs1.client"
MAX_CONTEXT_BYTES = 1_048_576
MAX_GENERATED_BYTES = 1_048_576
MAX_DATA_ENTRIES = 4096
MAX_DEPTH = 32
MAX_VISITS = 1_000_000
MAX_TEXT_LENGTH = 256
MAX_INT = 2**31 - 1
MIN_INT = -(2**31)
MAX_GRID_SIDE = 250_000
MAX_GRID_CELLS = 250_000
PAD_COUNT = 246

TOP_KEYS = ("schema", "nativeProjection", "physicalContext")
NATIVE_PROJECTION_KEYS = (
    "prior", "capture", "ownerSettings", "seedValues", "sourceIdentity",
)
PRIOR_KEYS = (
    "sourceParent", "manifestSha256", "sourceManifestSha256", "readerSha256",
    "resultSha256", "operation1Sha256", "parentReceiptSha256", "priorRunId",
    "priorOriginalReceipt", "trialDisposition",
)
PRIOR_RECEIPT_KEYS = (
    "receiptPath", "receiptBytes", "receiptSha256", "receiptOrdinal",
    "lineageOrdinal",
)
TRIAL_DISPOSITION_KEYS = (
    "status", "reason", "capturedFailedNetId", "controlFailedNetId",
    "cohortNetIds", "requestedNetIds", "orderChanged", "feedbackValidated",
)
CAPTURE_KEYS = (
    "cohortNetIds", "capturedNetIds", "trialDisposition", "controlDisposition",
    "trialOutcome", "passIndex", "failedNetId", "priorPromotedNetId",
    "liveSearchIndex",
)
OWNER_KEYS = (
    "boardMode", "maxExpansions", "expandedAtFailure", "maxVias", "viaCost",
    "maxTransitions", "branchStrategy", "netStrategy", "roleWidthsEnabled",
    "orderingStreamEnabled",
)
SEED_KEYS = ("rootSeed", "placementSeed", "routingSeed")
SOURCE_IDENTITY_KEYS = (
    "geometryBeforeSha256", "geometryAfterSha256", "detachedGeometrySha256",
    "effectsBeforeSha256", "effectsAfterSha256", "traceCount", "holeCount",
    "padLandIdentity",
)
PAD_IDENTITY_KEYS = ("format", "rowCount", "sha256")
PHYSICAL_CONTEXT_KEYS = ("gridWidth", "gridHeight", "padLands", "qualifiedP07Pads")
PAD_LAND_KEYS = ("padId", "netId", "x", "y", "bounds", "attachment", "layer")
P07_PAD_KEYS = (
    "record", "group", "padId", "netId", "componentId", "x", "y", "role",
)

_HEX_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_SIGNED_LONG = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
_TOKEN = re.compile(r"[-+A-Za-z0-9_.:]{1,128}\Z")
_GIT_SHA1 = re.compile(r"[0-9a-f]{40}\Z")
_UUID = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z"
)



class ContextCodecError(ValueError):
    """A stable, machine-readable input rejection."""

    def __init__(self, code):
        super().__init__(code)
        self.code = code


def _need(condition, code):
    if not condition:
        raise ContextCodecError(code)


def _exact_keys(value, expected, code):
    _need(type(value) is dict and tuple(value.keys()) == expected, code)


def _ascii(value, maximum=MAX_TEXT_LENGTH, allow_empty=False, code="ASCII"):
    _need(type(value) is str and (allow_empty or bool(value))
          and len(value) <= maximum
          and all(32 <= ord(character) <= 126 for character in value), code)
    return value


def _sha256(value, code):
    _need(type(value) is str and _HEX_SHA256.fullmatch(value) is not None, code)


def _integer(value, low, high, code):
    _need(type(value) is int and low <= value <= high, code)
    return value


def _unique_strings(value, length, code):
    _need(type(value) is list and len(value) == length, code + "_COUNT")
    for item in value:
        _ascii(item, code=code + "_ITEM")
    _need(len(set(value)) == length, code + "_DUPLICATE")
    return value


def _reject_float(_text):
    raise ContextCodecError("JSON_FLOAT")


def _reject_constant(_text):
    raise ContextCodecError("JSON_NONFINITE")


def _parse_int(text):
    value = int(text)
    _integer(value, MIN_INT, MAX_INT, "JSON_INTEGER_RANGE")
    return value


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ContextCodecError("JSON_DUPLICATE_KEY")
        result[key] = value
    return result


def _walk_json(value):
    """Check the bounded JSON domain and reject cycles or shared containers."""
    visited = 0
    containers = set()

    def visit(node, depth):
        nonlocal visited
        _need(depth <= MAX_DEPTH, "JSON_DEPTH")
        visited += 1
        _need(visited <= MAX_VISITS, "JSON_VISITS")
        kind = type(node)
        if node is None or kind is bool:
            return
        if kind is int:
            _integer(node, MIN_INT, MAX_INT, "JSON_INTEGER_RANGE")
            return
        if kind is str:
            _ascii(node, code="JSON_STRING")
            return
        if kind is list or kind is dict:
            identity = id(node)
            _need(identity not in containers, "JSON_ALIAS")
            containers.add(identity)
            if kind is list:
                for child in node:
                    visit(child, depth + 1)
                return
            for key, child in node.items():
                _need(type(key) is str, "JSON_OBJECT_KEY_TYPE")
                visit(key, depth + 1)
                visit(child, depth + 1)
            return
        raise ContextCodecError("JSON_VALUE_TYPE")

    visit(value, 0)
    return visited


def canonical_json(value):
    """Encode an ordered JSON tree as compact printable ASCII with one LF."""
    _walk_json(value)
    try:
        raw = (json.dumps(value, ensure_ascii=True, sort_keys=False,
                           separators=(",", ":"), allow_nan=False) + "\n").encode("ascii")
    except (TypeError, ValueError, UnicodeError, RecursionError):
        raise ContextCodecError("JSON_SERIALIZE") from None
    return raw


def _validate_trial_disposition(value, code):
    _exact_keys(value, TRIAL_DISPOSITION_KEYS, code + "_KEYS")
    for key in ("status", "reason", "capturedFailedNetId", "controlFailedNetId"):
        _ascii(value[key], code=code + "_" + key.upper())
    cohort = _unique_strings(value["cohortNetIds"], 2, code + "_COHORT")
    requested = _unique_strings(value["requestedNetIds"], 60, code + "_REQUESTED")
    _need(cohort == ["A_COIL_LOW", "B_COIL_LOW"]
          and set(cohort).issubset(set(requested)), code + "_COHORT_BINDING")
    _need(type(value["orderChanged"]) is bool
          and type(value["feedbackValidated"]) is bool, code + "_FLAGS")
    _need(value["status"] == "RUN" and value["reason"] == "TRIAL_EXECUTED"
          and value["capturedFailedNetId"] == "A_COIL_LOW"
          and value["controlFailedNetId"] == "B_COIL_LOW"
          and value["feedbackValidated"] is True, code + "_QUALIFIED")


def _validate_prior(value):
    _exact_keys(value, PRIOR_KEYS, "PRIOR_KEYS")
    _need(type(value["sourceParent"]) is str
          and _GIT_SHA1.fullmatch(value["sourceParent"]) is not None,
          "PRIOR_SOURCE_PARENT")
    for key in (
        "manifestSha256", "sourceManifestSha256", "readerSha256", "resultSha256",
        "operation1Sha256", "parentReceiptSha256",
    ):
        _sha256(value[key], "PRIOR_" + key.upper())
    _need(type(value["priorRunId"]) is str
          and _UUID.fullmatch(value["priorRunId"]) is not None, "PRIOR_RUN_ID")

    receipt = value["priorOriginalReceipt"]
    _exact_keys(receipt, PRIOR_RECEIPT_KEYS, "PRIOR_RECEIPT_KEYS")
    _need(receipt["receiptPath"] == "op-1.jsonl", "PRIOR_RECEIPT_PATH")
    _integer(receipt["receiptBytes"], 1, MAX_INT, "PRIOR_RECEIPT_BYTES")
    _sha256(receipt["receiptSha256"], "PRIOR_RECEIPT_SHA256")
    _need(receipt["receiptSha256"] == value["operation1Sha256"],
          "PRIOR_RECEIPT_OPERATION_BINDING")
    _need(type(receipt["receiptOrdinal"]) is int and receipt["receiptOrdinal"] == 307
          and type(receipt["lineageOrdinal"]) is int and receipt["lineageOrdinal"] == 77,
          "PRIOR_RECEIPT_ORDINALS")
    _validate_trial_disposition(value["trialDisposition"], "PRIOR_TRIAL")


def _validate_capture(value):
    _exact_keys(value, CAPTURE_KEYS, "CAPTURE_KEYS")
    cohort = _unique_strings(value["cohortNetIds"], 2, "CAPTURE_COHORT")
    captured = _unique_strings(value["capturedNetIds"], 60, "CAPTURE_NETS")
    _need(cohort == ["A_COIL_LOW", "B_COIL_LOW"]
          and set(cohort).issubset(set(captured)), "CAPTURE_COHORT_BINDING")
    trial = value["trialDisposition"]
    _validate_trial_disposition(trial, "CAPTURE_TRIAL")
    _need(trial["cohortNetIds"] == cohort, "CAPTURE_TRIAL_COHORT")
    _need(value["controlDisposition"] == "NO_PATH"
          and value["trialOutcome"] == "CAP_REJECTED", "CAPTURE_OUTCOMES")
    _integer(value["passIndex"], 2, 2, "CAPTURE_PASS")
    _need(value["failedNetId"] == "A_COIL_LOW"
          and value["priorPromotedNetId"] == "D_SENSE", "CAPTURE_NET_BINDING")
    _need(captured[43] == value["failedNetId"]
          and value["priorPromotedNetId"] in captured, "CAPTURE_VECTOR_BINDING")
    _integer(value["liveSearchIndex"], 44, 44, "CAPTURE_LIVE_SEARCH_INDEX")
    for key in ("controlDisposition", "trialOutcome", "failedNetId", "priorPromotedNetId"):
        _ascii(value[key], code="CAPTURE_" + key.upper())


def _validate_owner_settings(value):
    _exact_keys(value, OWNER_KEYS, "OWNER_SETTINGS_KEYS")
    _need(value["boardMode"] == "FULLER_TWO_LAYER"
          and value["maxExpansions"] == 1_000_000
          and value["expandedAtFailure"] == 918_222
          and value["maxVias"] == 48 and value["viaCost"] == 60
          and value["maxTransitions"] == 4
          and value["branchStrategy"] == "CANONICAL_NEAREST"
          and value["netStrategy"] == "CANONICAL"
          and value["roleWidthsEnabled"] is True
          and value["orderingStreamEnabled"] is True, "OWNER_SETTINGS_FIXED")
    for key, low, high in (
        ("maxExpansions", 1, 1_000_000),
        ("expandedAtFailure", 0, 1_000_000),
        ("maxVias", 0, 48),
        ("viaCost", 0, 1_000_000),
        ("maxTransitions", 0, 4),
    ):
        _integer(value[key], low, high, "OWNER_" + key.upper())
    for key in ("boardMode", "branchStrategy", "netStrategy"):
        _ascii(value[key], code="OWNER_" + key.upper())
    for key in ("roleWidthsEnabled", "orderingStreamEnabled"):
        _need(type(value[key]) is bool, "OWNER_" + key.upper() + "_TYPE")


def _validate_seed_values(value):
    _exact_keys(value, SEED_KEYS, "SEED_KEYS")
    expected = {
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
    }
    for key in SEED_KEYS:
        seed = _ascii(value[key], maximum=20, code="SEED_" + key.upper())
        _need(_SIGNED_LONG.fullmatch(seed) is not None, "SEED_" + key.upper() + "_FORMAT")
        integer = int(seed)
        _need(-(2**63) <= integer <= 2**63 - 1, "SEED_" + key.upper() + "_RANGE")
    _need(value == expected, "SEED_VALUES_FIXED")


def _validate_source_identity(value):
    _exact_keys(value, SOURCE_IDENTITY_KEYS, "SOURCE_IDENTITY_KEYS")
    for key in (
        "geometryBeforeSha256", "geometryAfterSha256", "detachedGeometrySha256",
        "effectsBeforeSha256", "effectsAfterSha256",
    ):
        _sha256(value[key], "SOURCE_IDENTITY_" + key.upper())
    _integer(value["traceCount"], 0, 0, "SOURCE_IDENTITY_TRACE_COUNT")
    _integer(value["holeCount"], 0, 0, "SOURCE_IDENTITY_HOLE_COUNT")
    pad = value["padLandIdentity"]
    _exact_keys(pad, PAD_IDENTITY_KEYS, "PAD_IDENTITY_KEYS")
    _need(pad["format"] == "TSJ_PAD_LAND_ASCII_V1", "PAD_IDENTITY_FORMAT")
    _integer(pad["rowCount"], PAD_COUNT, PAD_COUNT, "PAD_IDENTITY_COUNT")
    _sha256(pad["sha256"], "PAD_IDENTITY_SHA256")
    _need(pad["sha256"] == "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0",
          "PAD_IDENTITY_FIXED")


def _validate_pad_land(row, code):
    _exact_keys(row, PAD_LAND_KEYS, code + "_KEYS")
    pad_id = row["padId"]
    _need(type(pad_id) is str and _TOKEN.fullmatch(pad_id) is not None,
          code + "_ID")
    _need(type(row["netId"]) is str and _TOKEN.fullmatch(row["netId"]) is not None,
          code + "_NET")
    _integer(row["x"], -1_000_000, 1_000_000, code + "_X")
    _integer(row["y"], -1_000_000, 1_000_000, code + "_Y")
    bounds = row["bounds"]
    _need(type(bounds) is list and len(bounds) == 4, code + "_BOUNDS_SHAPE")
    for item in bounds:
        _integer(item, -100_000_000, 100_000_000, code + "_BOUNDS_VALUE")
    x, y, width, height = bounds
    _need(width > 0 and height > 0
          and -100_000_000 <= x + width <= 100_000_000
          and -100_000_000 <= y + height <= 100_000_000
          and x <= row["x"] <= x + width and y <= row["y"] <= y + height,
          code + "_BOUNDS_BINDING")
    _need(row["attachment"] == "PLATED_THROUGH_HOLE"
          and row["layer"] == "TOP+BOTTOM", code + "_PHYSICAL_TYPE")
    return pad_id


def _validate_p07_pad(row, code):
    _exact_keys(row, P07_PAD_KEYS, code + "_KEYS")
    _need(row["record"] == "PAD", code + "_RECORD")
    _integer(row["group"], 0, 639, code + "_GROUP")
    pad_id = row["padId"]
    for key in ("padId", "netId", "componentId", "role"):
        _need(type(row[key]) is str and _TOKEN.fullmatch(row[key]) is not None,
              code + "_" + key.upper())
    _integer(row["x"], -1_000_000, 1_000_000, code + "_X")
    _integer(row["y"], -1_000_000, 1_000_000, code + "_Y")
    return pad_id

def _validate_physical_context(value):
    _exact_keys(value, PHYSICAL_CONTEXT_KEYS, "PHYSICAL_CONTEXT_KEYS")
    width = _integer(value["gridWidth"], 1, MAX_GRID_SIDE, "GRID_WIDTH")
    height = _integer(value["gridHeight"], 1, MAX_GRID_SIDE, "GRID_HEIGHT")
    _need(width * height <= MAX_GRID_CELLS, "GRID_AREA")

    pad_lands = value["padLands"]
    p07_rows = value["qualifiedP07Pads"]
    _need(type(pad_lands) is list and len(pad_lands) == PAD_COUNT,
          "PAD_LAND_COUNT")
    _need(type(p07_rows) is list and len(p07_rows) == PAD_COUNT,
          "P07_PAD_COUNT")

    land_by_id = {}
    previous_id = None
    for row in pad_lands:
        pad_id = _validate_pad_land(row, "PAD_LAND")
        _need(previous_id is None or previous_id < pad_id, "PAD_LAND_ORDER")
        previous_id = pad_id
        land_by_id[pad_id] = row

    p07_by_id = {}
    role_by_net = {}
    previous_group = None
    for row in p07_rows:
        pad_id = _validate_p07_pad(row, "P07_PAD")
        group = row["group"]
        _need(previous_group is None or previous_group <= group, "P07_PAD_GROUP_ORDER")
        previous_group = group
        _need(pad_id not in p07_by_id, "P07_PAD_DUPLICATE")
        p07_by_id[pad_id] = row
        net_id = row["netId"]
        prior_role = role_by_net.setdefault(net_id, row["role"])
        _need(prior_role == row["role"], "P07_NET_ROLE_INCONSISTENT")

    _need(set(land_by_id) == set(p07_by_id), "PAD_CENSUS_BINDING")
    for pad_id, land in land_by_id.items():
        row = p07_by_id[pad_id]
        _need(land["netId"] == row["netId"] and land["x"] == row["x"]
              and land["y"] == row["y"], "PAD_P07_PHYSICAL_BINDING")


def validate_context(value):
    """Validate the exact current three-object canonical context schema."""
    _exact_keys(value, TOP_KEYS, "CONTEXT_KEYS")
    _walk_json(value)
    _need(value["schema"] == CONTEXT_SCHEMA, "CONTEXT_SCHEMA")

    projection = value["nativeProjection"]
    _exact_keys(projection, NATIVE_PROJECTION_KEYS, "NATIVE_PROJECTION_KEYS")
    _validate_prior(projection["prior"])
    _validate_capture(projection["capture"])
    _need(projection["prior"]["trialDisposition"]
          == projection["capture"]["trialDisposition"], "PRIOR_CAPTURE_TRIAL_BINDING")
    _validate_owner_settings(projection["ownerSettings"])
    _validate_seed_values(projection["seedValues"])
    _validate_source_identity(projection["sourceIdentity"])
    _validate_physical_context(value["physicalContext"])
    return value


def canonical_context(value):
    """Return validated canonical context bytes, including exactly one LF."""
    validate_context(value)
    raw = canonical_json(value)
    _need(0 < len(raw) <= MAX_CONTEXT_BYTES, "CONTEXT_SIZE")
    return raw


def parse_context(raw):
    """Parse one strict canonical ASCII context line without duplicate keys."""
    _need(type(raw) is bytes and 0 < len(raw) <= MAX_CONTEXT_BYTES,
          "CONTEXT_DOCUMENT_SIZE")
    _need(raw.endswith(b"\n") and not raw.endswith(b"\n\n") and b"\r" not in raw
          and not raw.startswith(b"\xef\xbb\xbf"), "CONTEXT_FRAMING")
    try:
        text = raw[:-1].decode("ascii", errors="strict")
        value = json.loads(
            text, object_pairs_hook=_pairs_no_duplicates,
            parse_float=_reject_float, parse_constant=_reject_constant,
            parse_int=_parse_int,
        )
    except ContextCodecError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError, RecursionError):
        raise ContextCodecError("CONTEXT_JSON") from None
    validate_context(value)
    _need(raw == canonical_context(value), "CONTEXT_NOT_CANONICAL")
    return value


def context_sha256(value):
    """Return the SHA-256 of the canonical current context."""
    return hashlib.sha256(canonical_context(value)).hexdigest()


def _clone_json(value):
    kind = type(value)
    if kind is dict:
        return {key: _clone_json(child) for key, child in value.items()}
    if kind is list:
        return [_clone_json(child) for child in value]
    return value


def native_projection(value):
    """Return a detached fresh JSON tree for the native factory DATA view."""
    validate_context(value)
    return _clone_json(value["nativeProjection"])


def data_entries(value):
    """Count JSON container entries; this is inventory, not total factory work."""
    _need(type(value) in (dict, list), "DATA_ROOT_TYPE")
    _walk_json(value)

    def count(node):
        total = 1 + len(node)
        _need(total <= MAX_DATA_ENTRIES, "DATA_ENTRIES_LIMIT")
        children = node.values() if type(node) is dict else node
        for child in children:
            if type(child) in (dict, list):
                total += count(child)
                _need(total <= MAX_DATA_ENTRIES, "DATA_ENTRIES_LIMIT")
        return total

    return count(value)


def java_ascii_literal(value):
    """Port of the pinned ASCII-only, fixed-width octal Java literal encoder."""
    _ascii(value, maximum=MAX_TEXT_LENGTH, allow_empty=True, code="JAVA_LITERAL_TEXT")
    return '"' + "".join("\\%03o" % ord(character) for character in value) + '"'


def generate_input_java(value, expected_context_sha256=None):
    """Emit deterministic Java DATA with the frozen V2 factory API."""
    raw_context = canonical_context(value)
    digest = hashlib.sha256(raw_context).hexdigest()
    if expected_context_sha256 is not None:
        _sha256(expected_context_sha256, "EXPECTED_CONTEXT_SHA256")
        _need(digest == expected_context_sha256, "EXPECTED_CONTEXT_SHA256_MISMATCH")

    tree = _clone_json(value["nativeProjection"])
    entries = data_entries(tree)
    lines = [
        "package " + JAVA_PACKAGE + ";",
        "",
        "public final class " + GENERATED_CLASS + " {",
        "    private " + GENERATED_CLASS + "() {}",
        "    public static String schemaTag() { return " + java_ascii_literal(CONTEXT_SCHEMA) + "; }",
        "    public static String contextSha256() { return " + java_ascii_literal(digest) + "; }",
        "    public static int dataEntries() { return " + str(entries) + "; }",
        "    public static java.util.Map<String,Object> dataJson() {",
    ]
    serial = [0]

    def emit(node, indent):
        kind = type(node)
        if kind is dict:
            name = "m" + str(serial[0])
            serial[0] += 1
            lines.append(indent + "java.util.Map<String,Object> " + name +
                         " = new java.util.LinkedHashMap<String,Object>();")
            for key, child in node.items():
                _need(type(key) is str, "JSON_OBJECT_KEY_TYPE")
                expression = emit(child, indent)
                lines.append(indent + name + ".put(" + java_ascii_literal(key) + ", " +
                             expression + ");")
            return name
        if kind is list:
            name = "l" + str(serial[0])
            serial[0] += 1
            lines.append(indent + "java.util.List<Object> " + name +
                         " = new java.util.ArrayList<Object>();")
            for child in node:
                expression = emit(child, indent)
                lines.append(indent + name + ".add(" + expression + ");")
            return name
        if kind is str:
            return java_ascii_literal(node)
        if kind is bool:
            return "true" if node else "false"
        if node is None:
            return "null"
        _integer(node, MIN_INT, MAX_INT, "GENERATED_INTEGER")
        return "Integer.valueOf(" + str(node) + ")"

    root_name = emit(tree, "        ")
    lines.append("        return " + root_name + ";")
    lines.append("    }")
    lines.append("}")
    try:
        raw = ("\n".join(lines) + "\n").encode("ascii")
    except UnicodeEncodeError:
        raise ContextCodecError("GENERATED_SOURCE_ASCII") from None
    _need(len(raw) <= MAX_GENERATED_BYTES, "GENERATED_SOURCE_SIZE")
    _need(b"java.util.Map<String,Object>" in raw
          and b"java.util.LinkedHashMap<String,Object>" in raw
          and b"dataJson()" in raw, "GENERATED_SOURCE_CONTENT")
    return raw


# Result metadata has its own current schema; canonical input stays unchanged.
QUALIFIED_PRIOR_SCHEMA = "tsj-cohort-terminal-qualified-prior-v2"
QUALIFIED_PRIOR_KEYS = ("schema", "inputContextSha256") + PRIOR_KEYS


def qualified_prior(context):
    """Build current result metadata, borrowing validated prior nested objects.

    The caller retains the unmutated input context. This creates a fresh top
    map only; full-result serialization must still reject aliases elsewhere.
    """
    digest = context_sha256(context)
    prior = context["nativeProjection"]["prior"]
    result = {
        "schema": QUALIFIED_PRIOR_SCHEMA,
        "inputContextSha256": digest,
    }
    for key in PRIOR_KEYS:
        result[key] = prior[key]
    return result


def validate_qualified_prior(value, expected_input_context_sha256):
    """Validate current result metadata against the caller's current input SHA.

    This is structural/semantic binding, not authentication of prior artifacts
    or authority to read a host receipt. Return the original map unchanged.
    """
    _exact_keys(value, QUALIFIED_PRIOR_KEYS, "QUALIFIED_PRIOR_KEYS")
    _walk_json(value)
    _need(value["schema"] == QUALIFIED_PRIOR_SCHEMA, "QUALIFIED_PRIOR_SCHEMA")
    _sha256(expected_input_context_sha256, "EXPECTED_INPUT_CONTEXT_SHA256")
    _sha256(value["inputContextSha256"], "QUALIFIED_PRIOR_INPUT_CONTEXT_SHA256")
    _need(value["inputContextSha256"] == expected_input_context_sha256,
          "QUALIFIED_PRIOR_INPUT_CONTEXT_SHA256_MISMATCH")
    _validate_prior({key: value[key] for key in PRIOR_KEYS})
    return value
