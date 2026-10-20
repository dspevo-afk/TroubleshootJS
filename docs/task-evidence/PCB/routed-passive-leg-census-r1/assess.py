"""Strict offline reader for the accepted15 routed passive end-leg census."""

import argparse
import hashlib
import importlib.util
import json
import math
import re
import stat
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/routed-passive-leg-census-r1"
PRIOR_ASSESS_REL = "docs/task-evidence/PCB/global-pocket-descent-trial-r1/assess.py"

PROTOCOL_SHA = "82212f6b7df2ecfdc3301a21908775ebdc6d63dac47539f2635693cd703236fd"
PRIOR_ASSESS_SHA = "0a45548e3091f6bccbf34d6ceab9fe1be1cb5c802796ae7aaa5d63ddbdca5e86"
PUBLISHED_INPUTS = {
    "docs/task-evidence/PCB/successful-route-pool-r1/native-inputs.json":
        "6b1a003c2a76bad4488d04e69b82ab86b87fa4e2284ed7545c698cc94f778ea8",
    "docs/task-evidence/PCB/successful-route-pool-r1/root-review.json":
        "086c30422719fdfd80010b9888408800124ec40be30191a98554df18f87551f6",
    "docs/task-evidence/PCB/successful-route-pool-r1/routes.txt":
        "c75c440c15780f4cf222ad882a19e2cad6d55fcedf10c993bd66fad6e209b63f",
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json":
        "5b928a7dd699dedb6411a9e44de4ec61c30864371417ccd9bb22cb4d0791f968",
    "routes-suites.json":
        "9d2cd9c7324b5c2c745be009e4622e5b55e3346652dba48a539aa0c483f4ecd1",
}
ROOTS = ("5", "2")
VERSION = 15
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_REPORTS = 100000
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1000000
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
SIGNED_INT = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
TIMING_FIELDS = (
    "status", "exitCode", "elapsedSeconds", "suiteCount", "phase", "scope",
    "terminalCleanupIncluded", "cleanupDuration",
)
TERMINAL_TEMPLATE = (
    "PASS: focused current contracts; {count} Java suites. "
    "Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."
FAILURE_LINE = re.compile(
    r"^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|Exception in thread\b|"
    r"AssertionError\b|BUILD FAILED\b|\[ERROR\]|CURRENT_CONTRACT_FAILURE:|"
    r"TIMEOUT\b|TIMED OUT\b|CURRENT_CONTRACT_TIMEOUT:|CANCELLED\b|"
    r"SKIPPED\b|IGNORED\b)",
    re.IGNORECASE,
)
SKIP_OR_FAILURE_WORD = re.compile(
    r"\b(?:SKIPPED?|IGNORED|TIMEOUT|TIMED\s+OUT|CANCELLED)\b",
    re.IGNORECASE,
)
STRATEGIES = ("P05_ONE_FACE", "P07_FULLER_TWO_LAYER")

SUMMARY_FIELDS = (
    "seed", "label", "version", "phase", "status", "physicalFeasibility",
    "graphHashBefore", "graphHashAfter", "successfulCallbacks", "aggregate",
)
CALLBACK_FIELDS = (
    "index", "strategy", "attempt", "target", "geometryHashBefore",
    "geometryHashAfter", "parts",
)
PART_FIELDS = (
    "componentId", "packageId", "eligiblePackage", "eligibilityReason",
    "knownExtraCenterlineContact", "extraCenterlineContactCount", "pads",
    "directions",
)
PAD_FIELDS = ("padId", "netId", "x", "y", "incidents")
INCIDENT_FIELDS = (
    "traceIndex", "traceSourceId", "traceNetId", "layer", "width", "endRole",
    "endpointX", "endpointY", "neighborX", "neighborY",
)
DIRECTION_FIELDS = ("dx", "dy", "eligible", "reason", "copperDelta")
AGGREGATE_FIELDS = (
    "callbackCount", "targetCallbackCount", "partCount", "eligiblePartCount",
    "eligibleDirectionCount", "rejectedDirectionCount",
)
DIRECTIONS = ((-10, 0), (10, 0), (0, -10), (0, 10))
ELIGIBILITY_REASONS = (
    "MISSING_PLACEMENT_CONSTRAINT", "UNSUPPORTED_PACKAGE", "ANCHOR_NOT_NONE",
    "CONNECTOR", "ISOLATION_BODY", "ELIGIBLE",
)
DIRECTION_REASONS = (
    "PART_INELIGIBLE", "TERMINAL_COUNT_NOT_TWO", "EXTRA_CENTERLINE_CONTACT",
    "MISSING_EXPLICIT_ENDPOINT", "MISSING_TRACE_SOURCE_ID",
    "TRACE_NET_MISMATCH", "ENDPOINT_COORDINATE_MISMATCH", "ZERO_LENGTH_LEG",
    "SAME_TRACE_BOTH_TERMINALS", "NON_PARALLEL_SHIFT", "COLLAPSE", "REVERSE",
    "ELIGIBLE",
)
PASSIVE_PACKAGES = (
    "AXIAL_RESISTOR", "AXIAL_DIODE",
    "RADIAL_ELECTROLYTIC_CAPACITOR", "RADIAL_CERAMIC_CAPACITOR",
)


class ReaderError(ValueError):
    """An input failed one of the frozen offline-reader gates."""


def _need(condition, message):
    if not condition:
        raise ReaderError(message)


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _is_reparse(path):
    try:
        info = path.lstat()
    except OSError as exc:
        raise ReaderError("sealed input is unavailable") from exc
    if stat.S_ISLNK(info.st_mode):
        return True
    return bool(getattr(info, "st_file_attributes", 0) & 0x400)


def _repo_file(relative, max_bytes=MAX_FILE_BYTES):
    _need(type(relative) is str and "\\" not in relative,
          "repository path must be slash-relative")
    pure = PurePosixPath(relative)
    _need(not pure.is_absolute() and bool(pure.parts) and
          all(part not in ("", ".", "..") for part in pure.parts),
          "repository path is not contained")
    target = REPO.joinpath(*pure.parts)
    try:
        repo_real = REPO.resolve(strict=True)
        cursor = repo_real
        for part in pure.parts:
            cursor = cursor / part
            _need(not _is_reparse(cursor), "repository input crosses a reparse point")
        resolved = target.resolve(strict=True)
        _need(resolved == target.absolute() or resolved.is_relative_to(repo_real),
              "repository input escapes its root")
        info = target.stat()
        _need(stat.S_ISREG(info.st_mode) and info.st_size <= max_bytes,
              "repository input is not a bounded regular file")
        data = target.read_bytes()
    except ReaderError:
        raise
    except (OSError, RuntimeError, ValueError) as exc:
        raise ReaderError("repository input is unavailable or unsafe") from exc
    _need(len(data) <= max_bytes, "repository input grew beyond its bound")
    return data


def _task_file(name, max_bytes=MAX_FILE_BYTES):
    _need(type(name) is str and name and "/" not in name and "\\" not in name,
          "task artifact name is invalid")
    return _repo_file(TASK_REL + "/" + name, max_bytes)


def _load_prior_reader():
    source = _repo_file(PRIOR_ASSESS_REL)
    _need(_sha(source) == PRIOR_ASSESS_SHA, "pinned prior reader hash changed")
    path = REPO.joinpath(*PurePosixPath(PRIOR_ASSESS_REL).parts)
    spec = importlib.util.spec_from_file_location(
        "pinned_global_pocket_descent_assess", path)
    _need(spec is not None and spec.loader is not None,
          "pinned prior reader cannot be loaded")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        raise ReaderError("pinned prior reader failed to load") from exc
    _need(module._sha(source) == PRIOR_ASSESS_SHA,
          "loaded prior reader differs from its pin")
    # The imported functions are the pure support boundary.  No old assessment
    # phase, root list, or prior contract is reused.
    module.ReaderError = ReaderError
    return module


PRIOR = _load_prior_reader()
_parse_json = PRIOR._parse_json
_typed_equal = PRIOR._typed_equal
_validate_case_shape = PRIOR._validate_case_shape
_whitespace_case = PRIOR._whitespace_case
CASE_FIELDS = PRIOR.CASE_FIELDS


def _require_sha(data, expected, label):
    _need(type(expected) is str and HEX64.fullmatch(expected) is not None and
          _sha(data) == expected, label + " hash changed")


def _canonical_seed(value, label):
    _need(type(value) is str and bool(value) and
          (value == "0" or not value.startswith("0")) and
          (value.startswith("-") or value.isdigit()),
          label + " is not canonical signed decimal text")
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ReaderError(label + " is not canonical signed decimal text") from exc
    _need(MIN_SIGNED_LONG <= parsed <= MAX_SIGNED_LONG and str(parsed) == value,
          label + " is outside the signed-long bound")
    return value


def _bounded_int(value, label, low=MIN_SIGNED_LONG, high=MAX_SIGNED_LONG):
    _need(type(value) is int and not isinstance(value, bool) and
          low <= value <= high, label + " is not a bounded integer")
    return value


def _bounded_text(value, label, allow_null=False):
    if allow_null and value is None:
        return value
    _need(type(value) is str and 0 < len(value.encode("utf-8", errors="strict")) <=
          MAX_FIELD_TEXT, label + " is not bounded text")
    return value


def _hex_digest(value, label):
    _need(type(value) is str and HEX64.fullmatch(value) is not None,
          label + " is not a lowercase SHA-256 digest")
    return value


def _validate_protocol(protocol):
    expected_keys = (
        "schema", "baseCommit", "scope", "roots", "versions", "density",
        "phase", "unitFixtureCount", "evaluator", "routePassDetail",
        "summaryPrefix", "casePrefix", "whitespacePrefix", "reportOrder",
        "summaryFields", "callbackFields", "partFields", "padFields",
        "incidentFields", "directionFields", "aggregateFields", "directions",
        "eligibilityReasons", "directionReasons", "passivePackageIds",
        "targetStrategy", "targetAttempt", "timingScope",
        "maxPhaseElapsedSeconds", "maxCaseMillis", "maxFileBytes",
        "publishedInputs", "pureReaderSupport", "suiteManifestSha256",
        "limits",
    )
    _need(type(protocol) is dict and tuple(protocol) == expected_keys,
          "protocol schema/order changed")
    _need(protocol["schema"] == 1 and protocol["baseCommit"] ==
          "66f8d67476288e90fca797e4fb0957c13c550dd8",
          "protocol identity changed")
    _need(protocol["scope"] ==
          "Accepted15 read-only routed passive endpoint-leg census; no move/reroute/policy/fitness change",
          "protocol scope changed")
    _need(_typed_equal(protocol["roots"], ["5", "2"]) and
          _typed_equal(protocol["versions"], [15]) and protocol["density"] == 14 and
          protocol["phase"] == "FINALIZED_ROUTE_CANDIDATE" and
          protocol["unitFixtureCount"] == 6 and
          protocol["evaluator"] == "endpointLegCensus" and
          protocol["routePassDetail"] == "roots=2 rows=2 evaluator=endpointLegCensus" and
          protocol["summaryPrefix"] == "PCB_ROUTED_LEG_CENSUS " and
          protocol["casePrefix"] == "PCB_ROUTED_LEG_CASE " and
          protocol["whitespacePrefix"] == "PCB_WHITESPACE_CASE ",
          "protocol phase or marker values changed")
    _need(protocol["reportOrder"] ==
          "WHITESPACE then one CENSUS then one CASE per seed; terminal suite PASS after bothseeds",
          "protocol report order changed")
    for key, expected in (
        ("summaryFields", SUMMARY_FIELDS), ("callbackFields", CALLBACK_FIELDS),
        ("partFields", PART_FIELDS), ("padFields", PAD_FIELDS),
        ("incidentFields", INCIDENT_FIELDS), ("directionFields", DIRECTION_FIELDS),
        ("aggregateFields", AGGREGATE_FIELDS),
    ):
        _need(_typed_equal(protocol[key], list(expected)),
              "protocol " + key + " changed")
    _need(_typed_equal(protocol["directions"], [list(item) for item in DIRECTIONS]) and
          _typed_equal(protocol["eligibilityReasons"], list(ELIGIBILITY_REASONS)) and
          _typed_equal(protocol["directionReasons"], list(DIRECTION_REASONS)) and
          _typed_equal(protocol["passivePackageIds"], list(PASSIVE_PACKAGES)),
          "protocol enumerations changed")
    _need(protocol["targetStrategy"] == "P07_FULLER_TWO_LAYER" and
          protocol["targetAttempt"] == 2 and
          protocol["timingScope"] ==
          "Native routed passive leg census; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "protocol target or timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          tuple(protocol["pureReaderSupport"]) ==
          ("path", "sha256") and
          protocol["pureReaderSupport"]["path"] == PRIOR_ASSESS_REL and
          protocol["pureReaderSupport"]["sha256"] == PRIOR_ASSESS_SHA and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          "protocol input pins changed")
    _need(type(protocol["limits"]) is list and len(protocol["limits"]) == 5 and
          all(type(item) is str and item for item in protocol["limits"]),
          "protocol limits changed")


def _parse_report_lines(text, protocol):
    _need(type(text) is str, "native phase text is missing")
    try:
        _need(len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
              "native phase text exceeds its bound")
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    prefixes = (
        protocol["whitespacePrefix"],
        protocol["summaryPrefix"],
        protocol["casePrefix"],
    )
    reports = []
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in prefixes
                       if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown native PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8", errors="strict"),
                          "native report")
        _need(type(row) is dict, "native report must be an object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS, "native report count exceeds bound")
    return reports


def _published_rows(route_data, protocol):
    try:
        text = route_data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("published routes are not strict UTF-8") from exc
    reports = []
    published_prefixes = (
        protocol["whitespacePrefix"],
        "PCB_GLOBAL_POCKET_CASE ",
        "PCB_SUCCESSFUL_ROUTE_POOL ",
    )
    for line in text.splitlines():
        if not line.startswith("PCB_"):
            continue
        prefix = next((candidate for candidate in published_prefixes
                       if line.startswith(candidate)), None)
        _need(prefix is not None, "unknown published PCB report prefix")
        row = _parse_json(line[len(prefix):].encode("utf-8", errors="strict"),
                          "published route report")
        _need(type(row) is dict, "published route report must be an object")
        reports.append((prefix, row, line))
        _need(len(reports) <= MAX_REPORTS,
              "published report count exceeds bound")
    selected = {}
    whitespace = {}
    for prefix, row, _line in reports:
        if row.get("seed") not in ROOTS or row.get("version") != VERSION:
            continue
        key = (row.get("seed"), row.get("version"))
        if prefix == "PCB_GLOBAL_POCKET_CASE ":
            _need(key not in selected, "published selected row is duplicated")
            _validate_case_shape(row, key[0], VERSION, 14)
            selected[key] = row
        elif prefix == protocol["whitespacePrefix"]:
            _need(key not in whitespace, "published whitespace row is duplicated")
            whitespace[key] = row
    expected_keys = [(seed, VERSION) for seed in ROOTS]
    _need(tuple(selected) == tuple(expected_keys) and
          tuple(whitespace) == tuple(expected_keys),
          "published routes lack the exact accepted15 root order")
    for key in expected_keys:
        seed, version = key
        _whitespace_case(whitespace[key], selected[key], seed, version)
    return selected, whitespace


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed routed-leg protocol")
    protocol = _parse_json(protocol_data, "routed-leg protocol")
    _validate_protocol(protocol)
    overrides = {} if published_overrides is None else published_overrides
    _need(type(overrides) is dict and all(type(key) is str and type(value) is bytes
                                          for key, value in overrides.items()),
          "published input overrides are malformed")
    blobs = {}
    for path, digest in PUBLISHED_INPUTS.items():
        data = overrides[path] if path in overrides else _repo_file(path)
        _require_sha(data, digest, "published input " + path)
        blobs[path] = data
    selected, whitespace = _published_rows(
        blobs["docs/task-evidence/PCB/successful-route-pool-r1/routes.txt"],
        protocol,
    )
    return {
        "protocol": protocol,
        "protocolData": protocol_data,
        "published": blobs,
        "selected": selected,
        "whitespace": whitespace,
    }


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = (
        [["PcbRoutedPassiveLegCensusContractTest",
          "PCB routed passive leg census contracts "]]
        if phase == "unit" else
        [["PcbRoutedPassiveLegCensusTrialContractTest",
          "PCB routed passive leg census trial contracts "]]
    )
    raw = (_task_file(name, 64 * 1024)
           if raw_override is None else raw_override)
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          phase + " suite manifest order/content changed")
    return actual, _sha(raw)


def _validate_selected_case(actual, expected, seed, protocol):
    _validate_case_shape(actual, seed, VERSION, 14)
    _need(tuple(actual) == tuple(CASE_FIELDS),
          "selected case schema/order changed")
    actual_core = dict(actual)
    expected_core = dict(expected)
    actual_elapsed = actual_core.pop("elapsedMillis")
    expected_core.pop("elapsedMillis")
    _need(_typed_equal(actual_core, expected_core),
          "selected case non-time fields differ from published accepted15 row")
    _bounded_int(actual_elapsed, "selected case elapsedMillis", 0,
                 protocol["maxCaseMillis"])
    return actual


def _successful_callbacks(case):
    text = case["statisticsCanonical"]
    _bounded_text(text, "selected statisticsCanonical")
    selected_matches = re.findall(
        r"(?:^|;)selected=(P05_ONE_FACE|P07_FULLER_TWO_LAYER)(?=;|$)", text)
    _need(len(selected_matches) == 1, "selected strategy is missing or duplicated")
    outcome_matches = list(re.finditer(r";routeOutcomes=\[(.*)\]\Z", text))
    _need(len(outcome_matches) == 1, "route outcomes are missing or duplicated")
    raw = outcome_matches[0].group(1)
    tokens = raw.split(", ") if raw else []
    _need(bool(tokens), "route outcomes are empty")
    pattern = re.compile(
        r"(P05_ONE_FACE|P07_FULLER_TWO_LAYER)@([0-5])="
        r"(SUCCESS|REJECTED:(.+))\Z"
    )
    seen = set()
    successes = []
    counts = {strategy: 0 for strategy in STRATEGIES}
    for token in tokens:
        match = pattern.fullmatch(token)
        _need(match is not None, "route outcome is malformed")
        strategy, attempt_text, status = match.group(1), match.group(2), match.group(3)
        attempt = int(attempt_text)
        pair = (strategy, attempt)
        _need(pair not in seen, "route outcome attempt is duplicated")
        seen.add(pair)
        counts[strategy] += 1
        if status == "SUCCESS":
            successes.append(pair)
        else:
            _need(match.group(4) is not None and bool(match.group(4)),
                  "rejected route outcome has no reason")
    _need(type(case["routeAttempts"]) is int and
          case["routeAttempts"] == len(tokens) and
          type(case["oneFaceAttempts"]) is int and
          case["oneFaceAttempts"] == counts["P05_ONE_FACE"] and
          type(case["twoLayerAttempts"]) is int and
          case["twoLayerAttempts"] == counts["P07_FULLER_TWO_LAYER"],
          "route outcome count differs from selected case")
    selected_pair = (selected_matches[0], case["selectedAttempt"])
    _need(selected_pair in successes,
          "selected attempt is absent from successful route outcomes")
    return successes


def _validate_summary(summary, seed, expected_case, protocol):
    _need(type(summary) is dict and tuple(summary) == SUMMARY_FIELDS,
          "census summary schema/order changed")
    _need(summary["seed"] == seed and type(summary["seed"]) is str and
          summary["label"] == "seed" + seed and type(summary["label"]) is str and
          summary["version"] == VERSION and type(summary["version"]) is int and
          summary["phase"] == protocol["phase"] and
          summary["status"] == "OBSERVATION_ONLY" and
          summary["physicalFeasibility"] == "NOT_EVALUATED",
          "census summary identity or disposition changed")
    _hex_digest(summary["graphHashBefore"], "census graphHashBefore")
    _hex_digest(summary["graphHashAfter"], "census graphHashAfter")
    _need(summary["graphHashBefore"] == summary["graphHashAfter"] ==
          expected_case["liveBoardGraphSha256"],
          "census graph hashes are not anchored to selected live graph")
    _need(type(summary["successfulCallbacks"]) is list and
          type(summary["aggregate"]) is dict,
          "census callback/aggregate values are malformed")
    expected_pairs = _successful_callbacks(expected_case)
    callbacks = summary["successfulCallbacks"]
    _need(len(callbacks) == len(expected_pairs),
          "successful callback count differs from selected route outcomes")

    aggregate = summary["aggregate"]
    _need(tuple(aggregate) == AGGREGATE_FIELDS,
          "census aggregate schema/order changed")
    callback_count = 0
    target_count = 0
    part_count = 0
    eligible_part_count = 0
    eligible_direction_count = 0
    rejected_direction_count = 0
    for index, (callback, pair) in enumerate(zip(callbacks, expected_pairs)):
        counts = _validate_callback(
            callback, index, pair, expected_case, protocol)
        callback_count += 1
        target_count += counts["target"]
        part_count += counts["parts"]
        eligible_part_count += counts["eligibleParts"]
        eligible_direction_count += counts["eligibleDirections"]
        rejected_direction_count += counts["rejectedDirections"]
    expected_aggregate = {
        "callbackCount": callback_count,
        "targetCallbackCount": target_count,
        "partCount": part_count,
        "eligiblePartCount": eligible_part_count,
        "eligibleDirectionCount": eligible_direction_count,
        "rejectedDirectionCount": rejected_direction_count,
    }
    _need(_typed_equal(aggregate, expected_aggregate),
          "census aggregate does not match callback facts")
    _need(eligible_direction_count + rejected_direction_count ==
          4 * part_count,
          "census direction aggregate is not four directions per part")
    return {
        "seed": seed,
        "callbackCount": callback_count,
        "targetCallbackCount": target_count,
        "partCount": part_count,
        "eligiblePartCount": eligible_part_count,
        "eligibleDirectionCount": eligible_direction_count,
        "rejectedDirectionCount": rejected_direction_count,
    }


def _validate_callback(callback, index, expected_pair, expected_case, protocol):
    _need(type(callback) is dict and tuple(callback) == CALLBACK_FIELDS,
          "census callback schema/order changed")
    strategy, attempt = expected_pair
    _need(callback["index"] == index and type(callback["index"]) is int and
          0 <= callback["index"] <= MAX_COUNT and
          callback["strategy"] == strategy and type(callback["strategy"]) is str and
          callback["attempt"] == attempt and type(callback["attempt"]) is int and
          0 <= callback["attempt"] <= 5,
          "successful callback order or identity differs from selected statistics")
    expected_target = strategy == protocol["targetStrategy"] and \
        attempt == protocol["targetAttempt"]
    _need(type(callback["target"]) is bool and
          callback["target"] is expected_target,
          "successful callback target filter changed")
    _hex_digest(callback["geometryHashBefore"], "callback geometryHashBefore")
    _hex_digest(callback["geometryHashAfter"], "callback geometryHashAfter")
    _need(callback["geometryHashBefore"] == callback["geometryHashAfter"],
          "callback geometry changed during read-only census")
    _need(type(callback["parts"]) is list,
          "callback parts are malformed")
    if not expected_target:
        _need(callback["parts"] == [], "non-target callback includes census parts")
        return {
            "target": 0, "parts": 0, "eligibleParts": 0,
            "eligibleDirections": 0, "rejectedDirections": 0,
        }
    _need(len(callback["parts"]) == expected_case["parts"],
          "target callback part count differs from selected case")
    previous = None
    counts = {
        "target": 1, "parts": len(callback["parts"]), "eligibleParts": 0,
        "eligibleDirections": 0, "rejectedDirections": 0,
    }
    for part in callback["parts"]:
        component_id, result = _validate_part(part, previous)
        previous = component_id
        counts["eligibleParts"] += result["eligible"]
        counts["eligibleDirections"] += result["eligibleDirections"]
        counts["rejectedDirections"] += result["rejectedDirections"]
    return counts


def _validate_part(part, previous_component_id):
    _need(type(part) is dict and tuple(part) == PART_FIELDS,
          "census part schema/order changed")
    component_id = _bounded_text(part["componentId"], "componentId")
    _need(previous_component_id is None or previous_component_id < component_id,
          "census component IDs are not canonical sorted unique order")
    package_id = _bounded_text(part["packageId"], "packageId")
    eligible_package = part["eligiblePackage"]
    _need(type(eligible_package) is bool,
          "eligiblePackage must be a strict boolean")
    _need(part["eligibilityReason"] in ELIGIBILITY_REASONS and
          type(part["eligibilityReason"]) is str,
          "eligibilityReason is not a protocol enum")
    if eligible_package:
        _need(package_id in PASSIVE_PACKAGES and
              part["eligibilityReason"] == "ELIGIBLE",
              "eligible package/reason is inconsistent")
    else:
        _need(part["eligibilityReason"] != "ELIGIBLE",
              "ineligible package/reason is inconsistent")
        # Eligibility is source-owned: MISSING_PLACEMENT_CONSTRAINT has
        # precedence over package/anchor/connector/isolation checks.
        if package_id not in PASSIVE_PACKAGES:
            _need(part["eligibilityReason"] in
                  ("MISSING_PLACEMENT_CONSTRAINT", "UNSUPPORTED_PACKAGE"),
                  "non-target package/reason is inconsistent")
        else:
            _need(part["eligibilityReason"] != "UNSUPPORTED_PACKAGE",
                  "target package/reason is inconsistent")

    _need(type(part["knownExtraCenterlineContact"]) is bool,
          "knownExtraCenterlineContact must be a strict boolean")
    extra_count = _bounded_int(
        part["extraCenterlineContactCount"], "extraCenterlineContactCount", 0)
    _need(part["knownExtraCenterlineContact"] is (extra_count > 0),
          "known extra-contact flag/count disagree")
    _need(type(part["pads"]) is list and type(part["directions"]) is list,
          "census pads/directions are malformed")
    pads = []
    previous_pad = None
    for pad in part["pads"]:
        parsed = _validate_pad(pad, previous_pad)
        previous_pad = parsed["padId"]
        pads.append(parsed)
    _validate_trace_identity(pads)
    if not eligible_package:
        _need(extra_count == 0 and
              all(not pad["incidents"] for pad in pads),
              "ineligible part must retain unscanned empty incidents")
    _need(len(part["directions"]) == 4,
          "census directions must contain four fixed entries")
    structural_failure = "PART_INELIGIBLE" if not eligible_package else \
        _structural_failure(pads, extra_count)
    directions = []
    for direction, expected in zip(part["directions"], DIRECTIONS):
        parsed = _validate_direction(direction, expected)
        directions.append(parsed)
    expected_directions = _expected_directions(
        pads, structural_failure, directions)
    _need(_typed_equal(directions, expected_directions),
          "census direction facts/deltas are not independently reproducible")
    return component_id, {
        "eligible": int(eligible_package),
        "eligibleDirections": sum(item["eligible"] for item in directions),
        "rejectedDirections": sum(not item["eligible"] for item in directions),
    }


def _validate_pad(pad, previous_pad_id):
    _need(type(pad) is dict and tuple(pad) == PAD_FIELDS,
          "census pad schema/order changed")
    pad_id = _bounded_text(pad["padId"], "padId")
    _need(previous_pad_id is None or previous_pad_id < pad_id,
          "census pad IDs are not canonical sorted unique order")
    net_id = _bounded_text(pad["netId"], "pad netId")
    x = _bounded_int(pad["x"], "pad x", -100000000, 100000000)
    y = _bounded_int(pad["y"], "pad y", -100000000, 100000000)
    _need(type(pad["incidents"]) is list,
          "census pad incidents are malformed")
    incidents = []
    previous_key = None
    for incident in pad["incidents"]:
        parsed = _validate_incident(incident, previous_key)
        previous_key = (parsed["traceIndex"], parsed["endRole"])
        incidents.append(parsed)
    return {
        "padId": pad_id, "netId": net_id, "x": x, "y": y,
        "incidents": incidents,
    }


def _validate_incident(incident, previous_key):
    _need(type(incident) is dict and tuple(incident) == INCIDENT_FIELDS,
          "census incident schema/order changed")
    trace_index = _bounded_int(incident["traceIndex"], "traceIndex", 0,
                              MAX_COUNT)
    source_id = incident["traceSourceId"]
    _bounded_text(source_id, "traceSourceId", allow_null=True)
    if source_id is not None:
        _need(bool(source_id), "traceSourceId cannot be empty")
    trace_net_id = _bounded_text(incident["traceNetId"], "traceNetId")
    layer = _bounded_text(incident["layer"], "trace layer")
    _need(layer in ("TOP", "BOTTOM"), "trace layer is invalid")
    width = _bounded_int(incident["width"], "trace width", 9, 63)
    _need(width % 2 == 1, "trace width must be an odd physical width")
    end_role = incident["endRole"]
    _need(type(end_role) is str and end_role in ("START", "END"),
          "incident endRole is invalid")
    key = (trace_index, end_role)
    _need(previous_key is None or previous_key < key,
          "incidents are not canonical traceIndex/endRole order")
    return {
        "traceIndex": trace_index, "traceSourceId": source_id,
        "traceNetId": trace_net_id, "layer": layer, "width": width,
        "endRole": end_role, "endpointX": _bounded_int(
            incident["endpointX"], "incident endpointX", -100000000, 100000000),
        "endpointY": _bounded_int(
            incident["endpointY"], "incident endpointY", -100000000, 100000000),
        "neighborX": _bounded_int(
            incident["neighborX"], "incident neighborX", -100000000, 100000000),
        "neighborY": _bounded_int(
            incident["neighborY"], "incident neighborY", -100000000, 100000000),
    }


def _validate_trace_identity(pads):
    metadata = {}
    endpoint_owners = {}
    for pad in pads:
        for incident in pad["incidents"]:
            trace_index = incident["traceIndex"]
            current = (
                incident["traceSourceId"], incident["traceNetId"],
                incident["layer"], incident["width"],
            )
            if trace_index in metadata:
                _need(metadata[trace_index] == current,
                      "traceIndex metadata differs across incidents")
            else:
                metadata[trace_index] = current
            endpoint_key = (trace_index, incident["endRole"])
            owner = endpoint_owners.get(endpoint_key)
            _need(owner is None or owner == pad["padId"],
                  "traceIndex/endRole is attached to multiple pads")
            endpoint_owners[endpoint_key] = pad["padId"]


def _structural_failure(pads, extra_count):
    if len(pads) != 2:
        return "TERMINAL_COUNT_NOT_TWO"
    for pad in pads:
        if not pad["incidents"]:
            return ("EXTRA_CENTERLINE_CONTACT" if extra_count > 0
                    else "MISSING_EXPLICIT_ENDPOINT")
    for pad in pads:
        for incident in pad["incidents"]:
            if incident["traceSourceId"] is None:
                return "MISSING_TRACE_SOURCE_ID"
            if pad["netId"] != incident["traceNetId"]:
                return "TRACE_NET_MISMATCH"
            if (incident["endpointX"], incident["endpointY"]) != \
                    (pad["x"], pad["y"]):
                return "ENDPOINT_COORDINATE_MISMATCH"
            if (incident["endpointX"], incident["endpointY"]) == \
                    (incident["neighborX"], incident["neighborY"]):
                return "ZERO_LENGTH_LEG"
    by_trace = {}
    for pad_index, pad in enumerate(pads):
        for incident in pad["incidents"]:
            by_trace.setdefault(incident["traceIndex"], []).append(
                (pad_index, incident["endRole"]))
    for roles in by_trace.values():
        pad_indexes = {pad_index for pad_index, _role in roles}
        role_names = {role for _pad_index, role in roles}
        if len(pad_indexes) > 1 and {"START", "END"} <= role_names:
            return "SAME_TRACE_BOTH_TERMINALS"
    if extra_count > 0:
        return "EXTRA_CENTERLINE_CONTACT"
    return None


def _validate_direction(direction, expected):
    _need(type(direction) is dict and tuple(direction) == DIRECTION_FIELDS,
          "census direction schema/order changed")
    dx, dy = expected
    _need(direction["dx"] == dx and direction["dy"] == dy and
          type(direction["dx"]) is int and type(direction["dy"]) is int,
          "census direction order changed")
    _need(type(direction["eligible"]) is bool and
          direction["reason"] in DIRECTION_REASONS and
          type(direction["reason"]) is str,
          "census direction type/enum changed")
    delta = direction["copperDelta"]
    if direction["eligible"]:
        _bounded_int(delta, "eligible copperDelta")
        _need(direction["reason"] == "ELIGIBLE",
              "eligible direction lacks ELIGIBLE reason")
    else:
        _need(delta is None, "ineligible direction must have null copperDelta")
    return {
        "dx": dx, "dy": dy, "eligible": direction["eligible"],
        "reason": direction["reason"], "copperDelta": delta,
    }


def _expected_directions(pads, structural_failure, actual):
    if structural_failure is not None:
        return [
            {"dx": dx, "dy": dy, "eligible": False,
             "reason": structural_failure, "copperDelta": None}
            for dx, dy in DIRECTIONS
        ]
    expected = []
    for (dx, dy), observed in zip(DIRECTIONS, actual):
        delta = 0
        reason = "ELIGIBLE"
        eligible = True
        for pad in pads:
            for incident in pad["incidents"]:
                horizontal = (incident["endpointY"] == incident["neighborY"] and
                              incident["endpointX"] != incident["neighborX"])
                vertical = (incident["endpointX"] == incident["neighborX"] and
                            incident["endpointY"] != incident["neighborY"])
                if (dx != 0 and not horizontal) or (dy != 0 and not vertical):
                    eligible = False
                    reason = "NON_PARALLEL_SHIFT"
                    break
                shift = dx if dx != 0 else dy
                endpoint = incident["endpointX"] if dx != 0 else incident["endpointY"]
                neighbor = incident["neighborX"] if dx != 0 else incident["neighborY"]
                old_vector = neighbor - endpoint
                new_vector = neighbor - (endpoint + shift)
                if new_vector == 0:
                    eligible = False
                    reason = "COLLAPSE"
                    break
                if ((old_vector < 0 and new_vector > 0) or
                        (old_vector > 0 and new_vector < 0)):
                    eligible = False
                    reason = "REVERSE"
                    break
                delta += abs(new_vector) - abs(old_vector)
            if not eligible:
                break
        expected.append({
            "dx": dx, "dy": dy, "eligible": eligible, "reason": reason,
            "copperDelta": delta if eligible else None,
        })
    return expected


def _assess_routes(reports, selected_rows, whitespace_rows, protocol):
    expected_keys = [(seed, VERSION) for seed in ROOTS]
    cursor = 0
    rows = []
    whitespace_count = 0
    callback_count = 0
    for seed, version in expected_keys:
        _need(cursor < len(reports) and
              reports[cursor][0] == protocol["whitespacePrefix"],
              "required whitespace report is missing or out of order")
        whitespace = reports[cursor][1]
        cursor += 1
        expected_case = selected_rows[(seed, version)]
        expected_whitespace = whitespace_rows[(seed, version)]
        _whitespace_case(whitespace, expected_case, seed, version)
        _need(_typed_equal(whitespace, expected_whitespace),
              "whitespace report differs from published accepted15 row")

        _need(cursor < len(reports) and
              reports[cursor][0] == protocol["summaryPrefix"],
              "census summary report is missing or out of order")
        summary = reports[cursor][1]
        cursor += 1
        census = _validate_summary(summary, seed, expected_case, protocol)

        _need(cursor < len(reports) and
              reports[cursor][0] == protocol["casePrefix"],
              "selected routed-leg case report is missing or out of order")
        actual_case = reports[cursor][1]
        cursor += 1
        _validate_selected_case(actual_case, expected_case, seed, protocol)
        rows.append(census)
        whitespace_count += 1
        callback_count += census["callbackCount"]
    _need(cursor == len(reports),
          "extra or foreign routed-leg reports remain")
    return {
        "status": "PASS",
        "scope": protocol["scope"],
        "roots": len(ROOTS),
        "rows": len(rows),
        "selectedCaseRows": len(rows),
        "whitespaceRows": whitespace_count,
        "successfulCallbacks": callback_count,
        "cases": rows,
        "observationDisposition": "PASS0; read-only endpoint-leg census only",
        "physicalFeasibility": "NOT_EVALUATED",
        "fitnessDisposition": "NOT_EVALUATED",
    }


def _unit_pass_line(line, suite, protocol):
    prefix = "PASS: " + suite[1]
    match = re.fullmatch(
        re.escape(prefix) + r"assertions=([1-9][0-9]*) fixtures=" +
        str(protocol["unitFixtureCount"]) + r" evaluator=" +
        re.escape(protocol["evaluator"]), line)
    _need(match is not None,
          "unit PASS marker/count/evaluator is malformed")
    _bounded_int(int(match.group(1)), "unit assertion count", 1)


def _phase_summary(text, receipt, timing, suites, phase, protocol,
                   selected_rows, whitespace_rows):
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    _need(phase in ("unit", "routes") and timing["phase"] == phase,
          "native phase differs from requested phase")
    expected_suites = (
        [["PcbRoutedPassiveLegCensusContractTest",
          "PCB routed passive leg census contracts "]]
        if phase == "unit" else
        [["PcbRoutedPassiveLegCensusTrialContractTest",
          "PCB routed passive leg census trial contracts "]]
    )
    _need(_typed_equal(suites, expected_suites),
          "native suite rows differ from frozen order")
    _need(timing["status"] == "PASS" and type(timing["status"]) is str and
          type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
          type(timing["suiteCount"]) is int and
          timing["suiteCount"] == len(expected_suites),
          "native phase status/exit/suite count failed")
    elapsed = timing["elapsedSeconds"]
    _need(type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and
          0 <= elapsed <= protocol["maxPhaseElapsedSeconds"],
          "native phase elapsed time is malformed or outside its bound")
    _need(timing["scope"] == protocol["timingScope"] and
          type(timing["terminalCleanupIncluded"]) is bool and
          timing["terminalCleanupIncluded"] is True and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native phase scope or cleanup boundary changed")
    _need(type(text) is str and type(receipt) is str,
          "native phase log/receipt is missing")
    try:
        text_size = len(text.encode("utf-8", errors="strict"))
        receipt_size = len(receipt.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    _need(text_size <= protocol["maxFileBytes"] and
          receipt_size <= protocol["maxFileBytes"],
          "native phase log/receipt exceeds its bound")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) or SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")
    terminal = TERMINAL_TEMPLATE.format(count=len(expected_suites))
    _need(text_lines.count(terminal) == 1 and
          text_lines.count(CLEANUP_LINE) == 1,
          "native terminal PASS or cleanup marker is missing/duplicated")
    _need(terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines and
          text_lines.index(terminal) < text_lines.index(CLEANUP_LINE),
          "native receipt or terminal cleanup boundary changed")
    suite_passes = [line for line in text_lines
                    if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and
          len(suite_passes) == len(expected_suites),
          "suite PASS lines differ between terminal log and child receipt")
    for line, suite in zip(suite_passes, expected_suites):
        if phase == "unit":
            _unit_pass_line(line, suite, protocol)
        else:
            _need(line == "PASS: " + suite[1] + protocol["routePassDetail"],
                  "routes PASS detail/order differs from frozen contract")
        _need(text_lines.index(line) < text_lines.index(terminal),
              "suite PASS appeared after terminal marker")
    text_reports = _parse_report_lines(text, protocol)
    receipt_reports = _parse_report_lines(receipt, protocol)
    _need([item[2] for item in text_reports] ==
          [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    terminal_position = text_lines.index(terminal)
    _need(all(position < terminal_position
              for position, line in enumerate(text_lines)
              if line.startswith("PCB_")),
          "native report appeared after terminal marker")
    report_positions = [position for position, line in enumerate(text_lines)
                        if line.startswith("PCB_")]
    if phase == "routes":
        _need(report_positions and
              all(text_lines.index(line) > max(report_positions)
                  for line in suite_passes),
              "routes suite PASS appeared before all PCB reports")
    if phase == "unit":
        _need(not text_reports and not receipt_reports,
              "unit contract leaked routed-leg reports")
        census = None
    else:
        census = _assess_routes(
            text_reports, selected_rows, whitespace_rows, protocol)
    return {
        "status": "PASS",
        "phase": phase,
        "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports),
        "orderedMarkers": [row[1] for row in expected_suites],
        "census": census,
    }


def check_phase(text, receipt, timing, suites, phase):
    """Validate an in-memory native phase against the frozen contract."""
    contract = _load_contract()
    protocol = contract["protocol"]
    actual_suites, _manifest_sha = _load_suite_manifest(phase, protocol)
    _need(_typed_equal(suites, actual_suites),
          "caller suite manifest differs from pinned bytes")
    return _phase_summary(
        text, receipt, timing, suites, phase, protocol,
        contract["selected"], contract["whitespace"])


def _read_phase_files(phase, contract):
    protocol = contract["protocol"]
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json")
    raw = {name: _task_file(name, protocol["maxFileBytes"]) for name in names}
    try:
        text = raw[names[0]].decode("utf-8", errors="strict")
        receipt = raw[names[1]].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("native phase log/receipt is not strict UTF-8") from exc
    timing = _parse_json(raw[names[2]], "native phase timing")
    suites, manifest_sha = _load_suite_manifest(phase, protocol)
    summary = _phase_summary(
        text, receipt, timing, suites, phase, protocol,
        contract["selected"], contract["whitespace"])
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1,
        "status": "PASS",
        "phase": "unit",
        "scope": "Endpoint-leg census unit fixtures only; routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_ASSESS_SHA,
        "publishedInputsSha256": {
            name: _sha(data) for name, data in contract["published"].items()
        },
        "suiteManifestSha256": manifest_sha,
        "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary,
        "nativeRuntimeEvidence": "PASS",
    }


def _validate_unit_prerequisite(data, expected):
    actual = _parse_json(data, "unit prerequisite")
    _need(_typed_equal(actual, expected),
          "routes phase lacks a fresh matching current unit-validation PASS prerequisite")
    return _sha(data)


def _routes_assessment(contract, summary, phase_inputs, manifest_sha,
                       unit_prerequisite_sha):
    protocol = contract["protocol"]
    census = summary["census"]
    _need(type(census) is dict and census.get("status") == "PASS",
          "routed-leg census is missing or failed")
    return {
        "schema": 1,
        "assessmentStatus": "PASS",
        "scope": protocol["scope"],
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_ASSESS_SHA,
        "publishedInputsSha256": {
            name: _sha(data) for name, data in contract["published"].items()
        },
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "routesSuiteManifestSha256": manifest_sha,
        "routePhaseInputsSha256": phase_inputs,
        "nativePhase": {"status": "PASS", "phase": "routes",
                        "evidence": summary},
        "observationDisposition": "PASS0; read-only endpoint-leg census only",
        "fitnessDisposition": "NOT_EVALUATED",
        "policyDisposition": "NO_POLICY_OR_ACCEPTANCE_CHANGE",
        "productionChange": "NONE",
        "limits": list(protocol["limits"]),
    }


def _write_artifact(name, value):
    target = HERE / name
    _need(not target.exists() and not target.is_symlink(),
          "refusing to overwrite existing task artifact: " + name)
    payload = (json.dumps(value, ensure_ascii=False, indent=2,
                          allow_nan=False) + "\n").encode("utf-8")
    try:
        with target.open("xb") as stream:
            stream.write(payload)
    except FileExistsError as exc:
        raise ReaderError("refusing to overwrite existing task artifact: " + name) from exc
    return _sha(payload)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("unit", "routes"))
    args = parser.parse_args(argv)
    try:
        contract = _load_contract()
        summary, phase_inputs, manifest_sha = _read_phase_files(args.phase, contract)
        if args.phase == "unit":
            output = _unit_validation(contract, summary, phase_inputs, manifest_sha)
            _write_artifact("unit-validation.json", output)
            print("PASS unit phase; routes and fitness are not evaluated")
            return 0
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files(
            "unit", contract)
        expected_unit = _unit_validation(
            contract, unit_summary, unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        output = _routes_assessment(
            contract, summary, phase_inputs, manifest_sha, prerequisite_sha)
        _write_artifact("assessment.json", output)
        print("PASS routes phase; observation PASS0; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
