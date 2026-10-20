"""Bounded offline reader for the normal-packing baseline/candidate census."""

import argparse
import hashlib
import json
import math
import re
import stat
import sys
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
TASK_REL = "docs/task-evidence/PCB/normal-packing-r1"

PROTOCOL_SHA = "1456fb21aec5ee02af3c779cd14cae8d0cd8a280095e9e34b0f44671b34269cc"
SOURCE_HELPER_SHA = "3b43e98367873b54b2606427d68f6287bac49ab39d08eec3501e6290e0966ff0"
SUITE_MANIFEST_SHA = {
    "unit-suites.json":
        "1ac63deadce73c15ebc010d7b4a68f0197249b47bf52b7fb116fb84196907930",
    "routes-suites.json":
        "4c9760b9999a35451c1255d6c83c13f94f6279dc76c5acd4994faac21a7dc1d3",
}

MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_FIELD_TEXT = 1024 * 1024
MAX_COUNT = 1_000_000
MAX_COORDINATE = 1_000_000_000
MAX_PHASE_MILLIS = 36_000 * 1000
MAX_SIGNED_LONG = (1 << 63) - 1
MIN_SIGNED_LONG = -(1 << 63)
HEX64 = re.compile(r"[0-9a-f]{64}\Z")
SIGNED_DECIMAL = re.compile(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)\Z")
WHITESPACE_SCOPE = (
    "GRID10_FULL_PACKAGE_ACCESS_BOTH_COPPER_FACES_VIAS_SILK_AND_DECLARED_"
    "ISOLATION;FREE_IS_NOT_PACKABLE_PROOF"
)
TERMINAL_TEMPLATE = (
    "PASS: focused current contracts; {count} Java suites. "
    "Full matrix and independent oracles NOT RUN."
)
CLEANUP_LINE = "CLEANUP: current JVM contract classes and task-owned scratch removed."
ROUTE_SUITE_PREFIX = "PCB normal packing census "
ROUTE_SUITE_NAMES = (
    "base-families-0-2",
    "base-families-3-5",
    "base-families-6-8",
    "controlled-indicator-all-roots",
    "rb30-root-0",
    "rb30-root-17",
    "rb30-root-13",
    "rb30-root-Long.MIN_VALUE",
)
TIMING_FIELDS = (
    "status", "exitCode", "elapsedSeconds", "suiteCount", "phase", "scope",
    "terminalCleanupIncluded", "cleanupDuration",
)
WHITESPACE_FIELDS = (
    "seed", "version", "parts", "gridUnits", "sampledCells",
    "packageAccessOccupiedCells", "protectedFreeCells", "protectedFreeFraction",
    "largestProtectedEmptyRectangle", "scope",
)
WHITESPACE_RECT_FIELDS = ("x", "y", "width", "height", "area")
ACCEPTED_FIELDS = (
    "family", "profile", "root", "seed", "status", "phase",
    "executionPolicy", "physicalAdmission", "elapsedMillis", "maxAdvanceMillis",
    "graphBefore", "graphAfter", "logicalParts", "logicalPads", "logicalNets",
    "layoutParts", "layoutPads", "traceCount", "geometrySha256",
    "layoutAlgorithmVersion", "outline", "copperManhattanLength", "holeCount",
    "viaCount", "generationPlacementAttempts", "generationRoutingAttempts",
    "generationRoutingExpansions", "generationRoutingMillis", "routingRecovery",
    "whitespace",
)
REJECTED_FIELDS = (
    "family", "profile", "root", "seed", "status", "phase", "type", "message",
    "elapsedMillis", "maxAdvanceMillis",
)
OUTLINE_FIELDS = ("x", "y", "width", "height", "area")
INPUT_SEAL_FIELDS = (
    "schema", "phase", "baseCommit", "protocolSha256", "baseMainSealSha256",
    "sourceCount", "files", "gateOwners",
)
INPUT_FILE_FIELDS = ("path", "sha256")
MATCHED_FIELDS = (
    "family", "profile", "root", "seed", "executionPolicy",
    "physicalAdmission", "graphBefore", "graphAfter", "logicalParts",
    "logicalPads", "logicalNets", "layoutParts", "layoutPads",
)


class ReaderError(ValueError):
    """An input failed a frozen offline-reader gate."""


class DuplicateKey(ValueError):
    """JSON contained a duplicate object key."""


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
            _need(not _is_reparse(cursor),
                  "repository input crosses a reparse point")
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


def _reject_constant(token):
    raise ReaderError("JSON constant is not permitted: " + token)


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKey(key)
        result[key] = value
    return result


def _parse_json(data, label):
    _need(type(data) is bytes, label + " is not bytes")
    try:
        text = data.decode("utf-8", errors="strict")
        return json.loads(text, object_pairs_hook=_pairs,
                          parse_constant=_reject_constant)
    except DuplicateKey as exc:
        raise ReaderError(label + " contains a duplicate JSON key") from exc
    except (UnicodeDecodeError, json.JSONDecodeError, ReaderError) as exc:
        if isinstance(exc, ReaderError):
            raise
        raise ReaderError(label + " is not strict JSON") from exc


def _typed_equal(left, right):
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return tuple(left) == tuple(right) and all(
            _typed_equal(left[key], right[key]) for key in left)
    if isinstance(left, list):
        return len(left) == len(right) and all(
            _typed_equal(a, b) for a, b in zip(left, right))
    return left == right


def _bounded_int(value, label, low=MIN_SIGNED_LONG, high=MAX_SIGNED_LONG):
    _need(type(value) is int and not isinstance(value, bool) and
          low <= value <= high, label + " is not a bounded integer")
    return value


def _bounded_text(value, label, allow_null=False, allow_empty=False):
    if allow_null and value is None:
        return value
    _need(type(value) is str, label + " is not text")
    _need(allow_empty or bool(value), label + " is empty")
    try:
        size = len(value.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError(label + " is not strict UTF-8") from exc
    _need(0 < size <= MAX_FIELD_TEXT, label + " is outside its bound")
    return value


def _bounded_float(value, label, low=None, high=None):
    _need(type(value) is float and math.isfinite(value),
          label + " is not a finite JSON float")
    if low is not None:
        _need(value >= low, label + " is below its bound")
    if high is not None:
        _need(value <= high, label + " is above its bound")
    return value


def _digest(value, label):
    _need(type(value) is str and HEX64.fullmatch(value) is not None,
          label + " is not a lowercase SHA-256 digest")
    return value


def _canonical_root(value, label="root"):
    _need(type(value) is str and SIGNED_DECIMAL.fullmatch(value) is not None,
          label + " is not canonical signed-long text")
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ReaderError(label + " is not canonical signed-long text") from exc
    _need(MIN_SIGNED_LONG <= parsed <= MAX_SIGNED_LONG and
          str(parsed) == value, label + " is outside the signed-long bound")
    return value


def _validate_protocol(protocol):
    expected = (
        "schema", "baseCommit", "scope", "scopeSha256", "families", "roots",
        "groups", "caseCount", "routeSuites", "unitSuites", "baselineLayoutVersion",
        "candidateLayoutVersion", "baselineReplayEpoch", "candidateReplayEpoch",
        "packingVersion", "casePrefix", "whitespacePrefix", "whitespaceVersion",
        "maxFileBytes", "maxPhaseSeconds", "nativeJvmMillis", "normalStepMillis",
        "nativeCaseMillis", "baselineCasesMayReject", "rejectionPhases",
        "requiredMatchedFields", "metricNonregression", "strictGain",
        "rejectedCaseRule", "phaseRule", "limits",
    )
    _need(type(protocol) is dict and tuple(protocol) == expected,
          "normal-packing protocol schema/order changed")
    _need(protocol["schema"] == 1 and protocol["baseCommit"] ==
          "2a73b949a02de718bd968c2920ba6045d7406c5a",
          "normal-packing protocol identity changed")
    _need(protocol["scope"] ==
          "Actual active11 normal random generation; fixed44 baseline/current packing comparison",
          "normal-packing scope changed")
    _digest(protocol["scopeSha256"], "scopeSha256")
    _need(type(protocol["families"]) is list and len(protocol["families"]) == 11 and
          all(type(item) is str and item for item in protocol["families"]),
          "normal family catalog is malformed")
    _need(_typed_equal(protocol["roots"],
                       ["0", "17", "13", "-9223372036854775808"]),
          "normal root order changed")
    for root in protocol["roots"]:
        _canonical_root(root)
    _need(type(protocol["groups"]) is list and len(protocol["groups"]) == 8,
          "normal group count changed")
    expected_groups = [(0, 3), (3, 3), (6, 3), (9, 1),
                       (10, 1), (10, 1), (10, 1), (10, 1)]
    for group_index, (group, expected_group) in enumerate(
            zip(protocol["groups"], expected_groups)):
        _need(type(group) is dict and tuple(group) == ("families", "roots"),
              "normal group schema/order changed")
        first, family_count = expected_group
        expected_roots = (protocol["roots"] if group_index < 4 else
                          [protocol["roots"][group_index - 4]])
        _need(_typed_equal(group["families"],
                           protocol["families"][first:first + family_count]) and
              _typed_equal(group["roots"], expected_roots),
              "normal group family/root order changed")
    _need(protocol["caseCount"] == 44 and protocol["routeSuites"] and
          len(protocol["routeSuites"]) == 8 and
          len(protocol["unitSuites"]) == 2,
          "normal suite/case counts changed")
    route_names = []
    for suite in protocol["routeSuites"]:
        _need(type(suite) is list and len(suite) == 2 and
              type(suite[1]) is str and suite[1].startswith(ROUTE_SUITE_PREFIX) and
              suite[1].endswith(" "),
              "normal route suite marker prefix changed")
        route_names.append(suite[1][len(ROUTE_SUITE_PREFIX):-1])
    _need(tuple(route_names) == ROUTE_SUITE_NAMES and
          len(set(route_names)) == len(route_names),
          "normal route suite collector names changed")
    _need(protocol["baselineLayoutVersion"] == 16 and
          protocol["candidateLayoutVersion"] == 17 and
          protocol["baselineReplayEpoch"] == "tsj-alpha/5" and
          protocol["candidateReplayEpoch"] == "tsj-alpha/6" and
          protocol["packingVersion"] == 2,
          "normal layout/replay identity changed")
    _need(protocol["casePrefix"] == "PCB_NORMAL_PACKING_CASE " and
          protocol["whitespacePrefix"] == "PCB_WHITESPACE_CASE " and
          protocol["whitespaceVersion"] == 1,
          "normal report prefixes changed")
    _need(protocol["maxFileBytes"] == MAX_FILE_BYTES and
          protocol["maxPhaseSeconds"] == 36_000 and
          protocol["nativeJvmMillis"] == 60_000 and
          protocol["normalStepMillis"] == 5_000 and
          protocol["nativeCaseMillis"] == 60_000 and
          protocol["baselineCasesMayReject"] is True,
          "normal timing or rejection bounds changed")
    _need(_typed_equal(protocol["rejectionPhases"],
                       ["ROUTING", "ENVELOPE", "NORMAL_POLICY"]),
          "normal rejection phases changed")
    _need(_typed_equal(protocol["requiredMatchedFields"], list(MATCHED_FIELDS)) and
          _typed_equal(protocol["metricNonregression"],
                       ["outline.area", "copperManhattanLength", "viaCount",
                        "whitespace"]),
          "normal comparison fields changed")
    _need(type(protocol["strictGain"]) is str and type(protocol["rejectedCaseRule"]) is str and
          type(protocol["phaseRule"]) is str and
          type(protocol["limits"]) is list and
          len(protocol["limits"]) == 5 and
          all(type(item) is str and item for item in protocol["limits"]),
          "normal comparison policy or limits changed")


def _load_manifest(name, expected, protocol):
    raw = _task_file(name, 64 * 1024)
    _need(_sha(raw) == SUITE_MANIFEST_SHA[name], name + " hash changed")
    value = _parse_json(raw, name)
    _need(_typed_equal(value, expected), name + " content/order changed")
    return value, _sha(raw)


def _load_contract():
    protocol_data = _task_file("protocol.json", 256 * 1024)
    _need(_sha(protocol_data) == PROTOCOL_SHA, "normal-packing protocol hash changed")
    protocol = _parse_json(protocol_data, "normal-packing protocol")
    _validate_protocol(protocol)
    scope_data = _task_file("scope.md", MAX_FILE_BYTES)
    _need(_sha(scope_data) == protocol["scopeSha256"], "scope hash changed")
    unit_suites, unit_sha = _load_manifest(
        "unit-suites.json", protocol["unitSuites"], protocol)
    route_suites, route_sha = _load_manifest(
        "routes-suites.json", protocol["routeSuites"], protocol)
    return {
        "protocol": protocol,
        "protocolData": protocol_data,
        "scopeData": scope_data,
        "unitSuites": unit_suites,
        "routeSuites": route_suites,
        "unitManifestSha": unit_sha,
        "routeManifestSha": route_sha,
    }


def _validate_input_seal(data, phase, contract):
    seal = _parse_json(data, phase + " input seal")
    _need(type(seal) is dict and tuple(seal) == INPUT_SEAL_FIELDS,
          phase + " input seal schema/order changed")
    _need(seal["schema"] == 1 and type(seal["schema"]) is int and
          seal["phase"] == phase and type(seal["phase"]) is str and
          seal["baseCommit"] == contract["protocol"]["baseCommit"] and
          seal["protocolSha256"] == _sha(contract["protocolData"]),
          phase + " input seal identity changed")
    _digest(seal["baseMainSealSha256"], phase + " baseMainSealSha256")
    source_count = _bounded_int(seal["sourceCount"], phase + " sourceCount", 1,
                                MAX_COUNT)
    files = seal["files"]
    _need(type(files) is list and len(files) == source_count,
          phase + " input seal file count changed")
    previous_path = None
    for item in files:
        _need(type(item) is dict and tuple(item) == INPUT_FILE_FIELDS,
              phase + " input seal file schema/order changed")
        path = _bounded_text(item["path"], phase + " input path")
        _need("\\" not in path and not PurePosixPath(path).is_absolute() and
              all(part not in ("", ".", "..") for part in PurePosixPath(path).parts),
              phase + " input path is not canonical relative text")
        _need(previous_path is None or previous_path < path,
              phase + " input paths are not sorted unique order")
        previous_path = path
        _digest(item["sha256"], phase + " input file hash")
    owners = seal["gateOwners"]
    _need(type(owners) is dict and bool(owners),
          phase + " gateOwners is empty or malformed")
    for filename, digest in owners.items():
        _bounded_text(filename, phase + " gate owner filename")
        _digest(digest, phase + " gate owner hash")
    return seal


def _read_input_seal(phase, contract):
    name = "baseline-inputs.json" if phase == "baseline" else "candidate-inputs.json"
    data = _task_file(name, 4 * 1024 * 1024)
    return _validate_input_seal(data, phase, contract), _sha(data)


def _expected_cases(protocol):
    cases = []
    groups = []
    cursor = 0
    for group_index, group in enumerate(protocol["groups"]):
        group_cases = []
        for family in group["families"]:
            for root in group["roots"]:
                item = {
                    "family": family,
                    "root": root,
                    "group": group_index,
                    "ordinal": cursor,
                }
                cases.append(item)
                group_cases.append(item)
                cursor += 1
        groups.append(group_cases)
    _need(len(cases) == protocol["caseCount"],
          "normal case matrix does not contain 44 cases")
    return cases, groups


def _validate_max_advance(value):
    if value is None:
        return None
    return _bounded_int(value, "maxAdvanceMillis", 0, 5_000)


def _validate_identity(row, expected):
    _need(row["family"] == expected["family"] and
          row["root"] == expected["root"] and
          row["seed"] == expected["root"],
          "normal case family/root/seed identity changed")
    _bounded_text(row["family"], "family")
    _bounded_text(row["profile"], "profile")
    _canonical_root(row["root"])
    _canonical_root(row["seed"], "seed")


def _validate_outline(outline):
    _need(type(outline) is dict and tuple(outline) == OUTLINE_FIELDS,
          "normal outline schema/order changed")
    for key in ("x", "y"):
        _bounded_int(outline[key], "outline." + key,
                     -MAX_COORDINATE, MAX_COORDINATE)
    width = _bounded_int(outline["width"], "outline.width", 1, MAX_COORDINATE)
    height = _bounded_int(outline["height"], "outline.height", 1, MAX_COORDINATE)
    area = _bounded_int(outline["area"], "outline.area", 1,
                        MAX_COORDINATE * MAX_COORDINATE)
    _need(area == width * height, "outline area does not match width*height")
    return outline


def _validate_count_fields(row, fields):
    for field in fields:
        _bounded_int(row[field], field, 0, MAX_COUNT)


def _validate_case_row(row, expected, layout_version, protocol):
    _need(type(row) is dict, "normal case report is not an object")
    _need(tuple(row) in (ACCEPTED_FIELDS, REJECTED_FIELDS),
          "normal case field set/order changed")
    _validate_identity(row, expected)
    _need(type(row["status"]) is str and
          row["status"] in ("ACCEPTED", "REJECTED"),
          "normal case status is invalid")
    _bounded_int(row["elapsedMillis"], "elapsedMillis", 0,
                 protocol["nativeCaseMillis"])
    _validate_max_advance(row["maxAdvanceMillis"])
    if row["status"] == "REJECTED":
        _need(tuple(row) == REJECTED_FIELDS,
              "rejected case fields/order changed")
        _need(row["phase"] in protocol["rejectionPhases"],
              "rejected case phase is not a declared typed rejection")
        _bounded_text(row["type"], "rejection type")
        _bounded_text(row["message"], "rejection message",
                      allow_null=True, allow_empty=True)
        return row
    _need(tuple(row) == ACCEPTED_FIELDS,
          "accepted case fields/order changed")
    _need(row["phase"] == "FINALIZED", "accepted case phase changed")
    _bounded_text(row["executionPolicy"], "executionPolicy")
    _bounded_text(row["physicalAdmission"], "physicalAdmission")
    _digest(row["graphBefore"], "graphBefore")
    _digest(row["graphAfter"], "graphAfter")
    _need(row["graphBefore"] == row["graphAfter"],
          "normal accepted case changed the live graph")
    _validate_count_fields(row, (
        "logicalParts", "logicalPads", "logicalNets", "layoutParts",
        "layoutPads", "traceCount", "holeCount", "viaCount",
        "generationPlacementAttempts", "generationRoutingAttempts",
        "generationRoutingExpansions",
    ))
    _bounded_int(row["generationRoutingMillis"], "generationRoutingMillis",
                 0, MAX_PHASE_MILLIS)
    _need(row["layoutParts"] == row["logicalParts"] and
          row["layoutPads"] == row["logicalPads"],
          "normal layout population differs from the logical population")
    _need(row["viaCount"] <= row["holeCount"],
          "normal via count exceeds hole count")
    _digest(row["geometrySha256"], "geometrySha256")
    _need(row["layoutAlgorithmVersion"] == layout_version and
          type(row["layoutAlgorithmVersion"]) is int,
          "normal layout algorithm identity changed")
    _validate_outline(row["outline"])
    _bounded_int(row["copperManhattanLength"], "copperManhattanLength", 0,
                 MAX_COUNT * MAX_COUNT)
    _need(row["routingRecovery"] is None or
          (type(row["routingRecovery"]) is str and
           bool(row["routingRecovery"])),
          "routingRecovery has an invalid type")
    _bounded_text(row["routingRecovery"], "routingRecovery", allow_null=True)
    _bounded_int(row["whitespace"], "whitespace", 0,
                 MAX_COORDINATE * MAX_COORDINATE)
    return row


def _validate_whitespace(row, expected_root):
    _need(type(row) is dict and tuple(row) == WHITESPACE_FIELDS,
          "whitespace report schema/order changed")
    _need(row["seed"] == expected_root and type(row["seed"]) is str,
          "whitespace seed differs from the adjacent case")
    _canonical_root(row["seed"], "whitespace seed")
    _need(row["version"] == 1 and type(row["version"]) is int,
          "whitespace version changed")
    _bounded_int(row["parts"], "whitespace parts", 0, MAX_COUNT)
    _need(row["gridUnits"] == 10 and type(row["gridUnits"]) is int,
          "whitespace grid is not GRID10")
    sampled = _bounded_int(row["sampledCells"], "sampledCells", 1, 250_000)
    package = _bounded_int(row["packageAccessOccupiedCells"],
                           "packageAccessOccupiedCells", 0, sampled)
    free = _bounded_int(row["protectedFreeCells"], "protectedFreeCells", 0,
                        sampled)
    fraction = _bounded_float(row["protectedFreeFraction"],
                              "protectedFreeFraction", 0.0, 1.0)
    _need(abs(fraction - (free / float(sampled))) <= 1e-12,
          "whitespace free fraction is not independently reproducible")
    _need(package + free <= sampled,
          "whitespace occupied/free cells exceed the sampler bound")
    rectangle = row["largestProtectedEmptyRectangle"]
    _need(type(rectangle) is dict and tuple(rectangle) == WHITESPACE_RECT_FIELDS,
          "whitespace rectangle schema/order changed")
    _bounded_int(rectangle["x"], "whitespace rectangle x",
                 -MAX_COORDINATE, MAX_COORDINATE)
    _bounded_int(rectangle["y"], "whitespace rectangle y",
                 -MAX_COORDINATE, MAX_COORDINATE)
    width = _bounded_int(rectangle["width"], "whitespace rectangle width", 0,
                         MAX_COORDINATE)
    height = _bounded_int(rectangle["height"], "whitespace rectangle height", 0,
                          MAX_COORDINATE)
    area = _bounded_int(rectangle["area"], "whitespace rectangle area", 0,
                        MAX_COORDINATE * MAX_COORDINATE)
    _need(area == width * height,
          "whitespace rectangle area does not match width*height")
    _need(row["scope"] == WHITESPACE_SCOPE and type(row["scope"]) is str,
          "whitespace scope changed")
    return row


def _route_suite_short_name(suite_marker):
    _need(type(suite_marker) is str and
          suite_marker.startswith(ROUTE_SUITE_PREFIX) and
          suite_marker.endswith(" "),
          "normal route suite marker identity changed")
    short_name = suite_marker[len(ROUTE_SUITE_PREFIX):-1]
    _need(short_name in ROUTE_SUITE_NAMES,
          "normal route suite collector name is unknown")
    return short_name


def _validate_route_suite_marker(line, suite_name, expected_cases, accepted, rejected):
    collector_name = _route_suite_short_name(suite_name)
    prefix = "PASS: " + suite_name
    pattern = (re.escape(prefix) +
               r"cases=(0|[1-9][0-9]*) accepted=(0|[1-9][0-9]*) "
               r"rejected=(0|[1-9][0-9]*) assertions=([1-9][0-9]*) "
               r"suite=(\"(?:\\.|[^\"\\])*\")")
    match = re.fullmatch(pattern, line)
    _need(match is not None, "normal route suite PASS marker is malformed")
    _need(int(match.group(1)) == expected_cases and
          int(match.group(2)) == accepted and
          int(match.group(3)) == rejected,
          "normal route suite counts differ from its group reports")
    suite_json = _parse_json(match.group(5).encode("utf-8"), "suite name")
    _need(type(suite_json) is str and suite_json == collector_name,
          "normal route suite JSON identity changed")


def _validate_unit_marker(line, suite, protocol):
    prefix = "PASS: " + suite[1]
    if suite[0] == "PcbConnectedPocketPackingContractTest":
        match = re.fullmatch(
            re.escape(prefix) + r"assertions=([1-9][0-9]*) fixtures=([1-9][0-9]*)",
            line)
        _need(match is not None, "connected-pocket unit marker is malformed")
        return {"assertions": int(match.group(1)), "fixtures": int(match.group(2))}
    match = re.fullmatch(
        re.escape(prefix) + r"assertions=([1-9][0-9]*) packing=2 replay=" +
        re.escape(protocol["candidateReplayEpoch"]), line)
    _need(match is not None, "normal identity unit marker is malformed")
    return {"assertions": int(match.group(1)), "packing": 2,
            "replay": protocol["candidateReplayEpoch"]}


def _parse_events(text, phase, protocol):
    _need(type(text) is str, phase + " log is not text")
    try:
        size = len(text.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ReaderError(phase + " log is not strict UTF-8") from exc
    _need(size <= protocol["maxFileBytes"], phase + " log exceeds its bound")
    terminal = TERMINAL_TEMPLATE.format(
        count=2 if phase == "unit" else len(protocol["routeSuites"]))
    events = []
    lines = text.splitlines()
    for position, line in enumerate(lines):
        if (re.search(r"\b(?:SKIP(?:PED)?|IGNORED|TIMEOUT|TIMED\s+OUT|"
                      r"CANCELLED)\b", line, re.IGNORECASE) or
                re.match(r"^\s*(?:FAIL(?:ED)?\b|ERROR\b|FATAL\b|"
                         r"Exception in thread\b|AssertionError\b|BUILD FAILED\b|"
                         r"\[ERROR\])", line, re.IGNORECASE)):
            _need(False, phase + " log contains failure/skip output")
        if line.startswith("PCB_"):
            if line.startswith(protocol["whitespacePrefix"]):
                prefix = protocol["whitespacePrefix"]
                row = _parse_json(line[len(prefix):].encode("utf-8"),
                                  phase + " whitespace report")
                events.append(("whitespace", row, line, position))
            elif line.startswith(protocol["casePrefix"]):
                prefix = protocol["casePrefix"]
                row = _parse_json(line[len(prefix):].encode("utf-8"),
                                  phase + " case report")
                events.append(("case", row, line, position))
            else:
                _need(False, "unknown normal PCB report prefix")
        elif line.startswith("PASS: "):
            if line == terminal:
                continue
            if phase == "unit":
                if not line.startswith("PASS: PCB "):
                    _need(False, "unknown unit PASS marker")
            elif line.startswith("PASS: PCB normal packing census "):
                pass
            else:
                _need(False, "unknown route PASS marker")
            events.append(("suite", None, line, position))
        elif line.startswith("CLEANUP:"):
            continue
    return lines, events


def _phase_frame(text, receipt, timing, suites, phase, contract):
    protocol = contract["protocol"]
    expected_suites = (contract["unitSuites"] if phase == "unit"
                       else contract["routeSuites"])
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    _need(timing["status"] == "PASS" and type(timing["status"]) is str and
          type(timing["exitCode"]) is int and not isinstance(timing["exitCode"], bool) and
          timing["exitCode"] == 0 and type(timing["suiteCount"]) is int and
          timing["suiteCount"] == len(expected_suites) and timing["phase"] == phase,
          "native phase status/exit/suite count/phase failed")
    elapsed = timing["elapsedSeconds"]
    _need(type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and 0 <= elapsed <= protocol["maxPhaseSeconds"],
          "native phase elapsed time is outside its bound")
    _need(timing["scope"] == protocol["scope"] and
          timing["terminalCleanupIncluded"] is True and
          type(timing["terminalCleanupIncluded"]) is bool and
          timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED",
          "native timing scope/cleanup changed")
    _need(_typed_equal(suites, expected_suites),
          "native suite manifest differs from the frozen manifest")
    text_lines, text_events = _parse_events(text, phase, protocol)
    receipt_lines, receipt_events = _parse_events(receipt, phase, protocol)
    terminal = TERMINAL_TEMPLATE.format(count=len(expected_suites))
    _need(text_lines.count(terminal) == 1 and
          text_lines.count(CLEANUP_LINE) == 1 and
          text_lines.index(terminal) < text_lines.index(CLEANUP_LINE) and
          text_lines[-1] == CLEANUP_LINE,
          "native terminal PASS/cleanup boundary changed")
    _need(terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines,
          "child receipt contains terminal or cleanup output")
    _need([event[2] for event in text_events] ==
          [event[2] for event in receipt_events],
          "stdout and child receipt report/suite lines differ")
    terminal_position = text_lines.index(terminal)
    _need(all(event[3] < terminal_position for event in text_events),
          "native report/suite output appeared after terminal marker")
    if phase == "unit":
        _need(all(event[0] == "suite" for event in text_events) and
              len(text_events) == len(expected_suites),
              "unit phase contains census reports or wrong suite count")
        unit_markers = []
        for event, suite in zip(text_events, expected_suites):
            _need(event[0] == "suite", "unit suite marker order changed")
            unit_markers.append(_validate_unit_marker(event[2], suite, protocol))
        return {
            "status": "PASS", "phase": phase,
            "suiteCount": len(expected_suites), "reportRows": 0,
            "suiteMarkers": unit_markers, "eventLines": [event[2] for event in text_events],
        }
    records, group_summaries = _consume_census_events(
        text_events, protocol, expected_suites, phase)
    return {
        "status": "PASS", "phase": phase,
        "suiteCount": len(expected_suites), "reportRows": len(records),
        "records": records, "groups": group_summaries,
        "eventLines": [event[2] for event in text_events],
    }


def _consume_census_events(events, protocol, expected_suites, phase):
    cases, groups = _expected_cases(protocol)
    layout_version = (protocol["baselineLayoutVersion"] if phase == "baseline"
                      else protocol["candidateLayoutVersion"])
    cursor = 0
    records = []
    group_summaries = []
    for group_index, group_cases in enumerate(groups):
        accepted = 0
        rejected = 0
        for expected in group_cases:
            whitespace = None
            if cursor < len(events) and events[cursor][0] == "whitespace":
                whitespace = events[cursor][1]
                _validate_whitespace(whitespace, expected["root"])
                cursor += 1
            _need(cursor < len(events) and events[cursor][0] == "case",
                  "normal case is missing or suite PASS precedes its group reports")
            row = events[cursor][1]
            _validate_case_row(row, expected, layout_version, protocol)
            cursor += 1
            if row["status"] == "ACCEPTED":
                _need(whitespace is not None,
                      "accepted normal case lacks its adjacent GRID10 report")
                _need(whitespace["parts"] == row["logicalParts"] and
                      whitespace["largestProtectedEmptyRectangle"]["area"] ==
                      row["whitespace"],
                      "accepted case does not match its adjacent whitespace metric")
                accepted += 1
            else:
                _need(whitespace is None,
                      "rejected normal case has an adjacent whitespace report")
                rejected += 1
            records.append({
                "family": expected["family"], "root": expected["root"],
                "group": group_index, "row": row, "whitespace": whitespace,
            })
        _need(cursor < len(events) and events[cursor][0] == "suite",
              "normal group suite PASS is missing or out of order")
        _validate_route_suite_marker(
            events[cursor][2], expected_suites[group_index][1], len(group_cases),
            accepted, rejected)
        cursor += 1
        group_summaries.append({
            "group": group_index, "cases": len(group_cases),
            "accepted": accepted, "rejected": rejected,
            "suite": _route_suite_short_name(expected_suites[group_index][1]),
        })
    _need(cursor == len(events), "extra/foreign normal reports or suite markers remain")
    _need(len(records) == len(cases), "normal census did not retain all 44 cases")
    return records, group_summaries


def check_phase(text, receipt, timing, suites, phase):
    """Validate one in-memory native phase without reading outcome files."""
    contract = _load_contract()
    _need(phase in ("baseline", "unit", "routes"), "unknown normal phase")
    return _phase_frame(text, receipt, timing, suites, phase, contract)


def _read_phase_files(phase, contract):
    protocol = contract["protocol"]
    _need(phase in ("baseline", "unit", "routes"), "unknown normal phase")
    names = (phase + ".txt", phase + "-receipt.txt", phase + "-timing.json")
    raw = {name: _task_file(name, protocol["maxFileBytes"]) for name in names}
    try:
        text = raw[names[0]].decode("utf-8", errors="strict")
        receipt = raw[names[1]].decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError(phase + " log/receipt is not strict UTF-8") from exc
    timing = _parse_json(raw[names[2]], phase + " timing")
    suites = contract["unitSuites"] if phase == "unit" else contract["routeSuites"]
    summary = _phase_frame(text, receipt, timing, suites, phase, contract)
    return summary, {name: _sha(data) for name, data in raw.items()}, (
        contract["unitManifestSha"] if phase == "unit" else contract["routeManifestSha"])


def _common_evidence(contract, phase_inputs, manifest_sha):
    return {
        "schema": 1,
        "baseCommit": contract["protocol"]["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "scopeSha256": _sha(contract["scopeData"]),
        "sourceHelperSha256": SOURCE_HELPER_SHA,
        "suiteManifestSha256": manifest_sha,
        "phaseInputsSha256": phase_inputs,
    }


def _baseline_assessment(contract, summary, phase_inputs, manifest_sha,
                         input_seal=None, input_seal_sha=None):
    _need(summary["phase"] == "baseline" and summary["status"] == "PASS",
          "baseline summary is missing or failed")
    accepted = sum(record["row"]["status"] == "ACCEPTED"
                   for record in summary["records"])
    rejected = len(summary["records"]) - accepted
    result = _common_evidence(contract, phase_inputs, manifest_sha)
    result.update({
        "assessmentStatus": "PASS", "phase": "baseline",
        "scope": "Fixed44 baseline normal packing census only.",
        "nativePhase": summary,
        "caseCount": len(summary["records"]),
        "acceptedCount": accepted,
        "rejectedCount": rejected,
        "nativeRuntimeEvidence": "PASS",
    })
    if input_seal is not None:
        result["inputSeal"] = input_seal
        result["inputSealSha256"] = input_seal_sha
    return result


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    _need(summary["phase"] == "unit" and summary["status"] == "PASS",
          "unit summary is missing or failed")
    result = _common_evidence(contract, phase_inputs, manifest_sha)
    result.update({
        "assessmentStatus": "PASS", "phase": "unit",
        "scope": "Normal connected-pocket and identity unit contracts only.",
        "nativePhase": summary,
        "nativeRuntimeEvidence": "PASS",
    })
    return result


def _validate_unit_prerequisite(data, expected):
    actual = _parse_json(data, "unit prerequisite")
    _need(_typed_equal(actual, expected),
          "routes phase lacks a fresh matching unit-validation PASS prerequisite")
    return _sha(data)


def _compare_records(baseline, candidate, protocol):
    baseline_by_key = {(item["family"], item["root"]): item
                       for item in baseline["records"]}
    candidate_by_key = {(item["family"], item["root"]): item
                        for item in candidate["records"]}
    _need(tuple(baseline_by_key) == tuple(candidate_by_key),
          "baseline/candidate case identity or order changed")
    _need(len(baseline_by_key) == protocol["caseCount"],
          "baseline/candidate comparison is not the complete 44-case matrix")
    lost = []
    regressions = []
    strict_area_gains = []
    new_admissions = []
    retained_rejections = []
    rejection_drift = []
    for key in baseline_by_key:
        before = baseline_by_key[key]
        after = candidate_by_key[key]
        before_row = before["row"]
        after_row = after["row"]
        _need(before_row["family"] == after_row["family"] and
              before_row["root"] == after_row["root"] and
              before_row["seed"] == after_row["seed"],
              "baseline/candidate source identity changed")
        if before_row["status"] == "ACCEPTED":
            if after_row["status"] != "ACCEPTED":
                lost.append({"family": key[0], "root": key[1]})
                continue
            for field in MATCHED_FIELDS:
                _need(_typed_equal(before_row[field], after_row[field]),
                      "matched source field changed: " + field)
            before_metrics = {
                "outline.area": before_row["outline"]["area"],
                "copperManhattanLength": before_row["copperManhattanLength"],
                "viaCount": before_row["viaCount"],
                "whitespace": before_row["whitespace"],
            }
            after_metrics = {
                "outline.area": after_row["outline"]["area"],
                "copperManhattanLength": after_row["copperManhattanLength"],
                "viaCount": after_row["viaCount"],
                "whitespace": after_row["whitespace"],
            }
            for metric in protocol["metricNonregression"]:
                before_value = before_metrics[metric]
                after_value = after_metrics[metric]
                regressed = after_value > before_value
                if regressed:
                    regressions.append({
                        "family": key[0], "root": key[1], "metric": metric,
                        "before": before_value, "after": after_value,
                    })
            if after_row["outline"]["area"] < before_row["outline"]["area"]:
                strict_area_gains.append({
                    "family": key[0], "root": key[1],
                    "before": before_row["outline"]["area"],
                    "after": after_row["outline"]["area"],
                })
        elif after_row["status"] == "REJECTED":
            _need(before_row["phase"] == after_row["phase"] and
                  before_row["type"] == after_row["type"],
                  "fixed typed rejection changed phase/type")
            retained_rejections.append({"family": key[0], "root": key[1]})
        else:
            new_admissions.append({
                "family": key[0], "root": key[1],
                "candidateArea": after_row["outline"]["area"],
                "candidateCopper": after_row["copperManhattanLength"],
                "candidateVias": after_row["viaCount"],
                "candidateWhitespace": after_row["whitespace"],
            })
    fitness = {
        "status": "PASS" if not lost and not regressions and strict_area_gains else "FAIL",
        "baselineAcceptedRetained": len(baseline["records"]) - len(lost) -
        sum(item["row"]["status"] == "REJECTED" for item in baseline["records"]),
        "baselineAcceptedCount": sum(item["row"]["status"] == "ACCEPTED"
                                      for item in baseline["records"]),
        "lostAdmissions": lost,
        "metricRegressions": regressions,
        "strictAreaGains": strict_area_gains,
        "newAdmissions": new_admissions,
        "retainedTypedRejections": retained_rejections,
        "rejectionDrift": rejection_drift,
    }
    return fitness


def _routes_assessment(contract, baseline_summary, baseline_inputs,
                       baseline_manifest_sha, candidate_summary,
                       candidate_inputs, candidate_manifest_sha,
                       unit_prerequisite_sha, baseline_input_seal=None,
                       baseline_input_seal_sha=None, candidate_input_seal=None,
                       candidate_input_seal_sha=None):
    _need(baseline_summary["phase"] == "baseline" and
          candidate_summary["phase"] == "routes" and
          baseline_summary["status"] == "PASS" and
          candidate_summary["status"] == "PASS",
          "baseline/candidate native phase is missing or failed")
    fitness = _compare_records(
        baseline_summary, candidate_summary, contract["protocol"])
    result = _common_evidence(contract, candidate_inputs, candidate_manifest_sha)
    result.update({
        "assessmentStatus": "PASS" if fitness["status"] == "PASS" else "FAIL",
        "integrityStatus": "PASS",
        "nativeStatus": "PASS",
        "fitnessStatus": fitness["status"],
        "phase": "routes",
        "scope": contract["protocol"]["scope"],
        "baselinePhaseInputsSha256": baseline_inputs,
        "baselineSuiteManifestSha256": baseline_manifest_sha,
        "unitPrerequisiteSha256": unit_prerequisite_sha,
        "nativePhase": candidate_summary,
        "fitness": fitness,
        "observationDisposition": "PASS; baseline/current normal packing comparison",
        "productionChange": "NONE",
        "limits": list(contract["protocol"]["limits"]),
    })
    if baseline_input_seal is not None:
        result["baselineInputSeal"] = baseline_input_seal
        result["baselineInputSealSha256"] = baseline_input_seal_sha
    if candidate_input_seal is not None:
        result["candidateInputSeal"] = candidate_input_seal
        result["candidateInputSealSha256"] = candidate_input_seal_sha
    return result


def _write_artifact(name, value):
    target = HERE / name
    _need(not target.exists() and not target.is_symlink(),
          "refusing to overwrite existing task artifact: " + name)
    data = (json.dumps(value, ensure_ascii=False, indent=2,
                       allow_nan=False) + "\n").encode("utf-8")
    try:
        with target.open("xb") as stream:
            stream.write(data)
    except FileExistsError as exc:
        raise ReaderError("refusing to overwrite existing task artifact: " + name) from exc
    return _sha(data)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("baseline", "unit", "routes"))
    args = parser.parse_args(argv)
    try:
        contract = _load_contract()
        if args.phase == "baseline":
            summary, inputs, manifest_sha = _read_phase_files("baseline", contract)
            seal, seal_sha = _read_input_seal("baseline", contract)
            output = _baseline_assessment(
                contract, summary, inputs, manifest_sha, seal, seal_sha)
            _write_artifact("baseline-assessment.json", output)
            print("PASS baseline phase; fixed44 matrix validated")
            return 0
        if args.phase == "unit":
            summary, inputs, manifest_sha = _read_phase_files("unit", contract)
            output = _unit_validation(contract, summary, inputs, manifest_sha)
            _write_artifact("unit-validation.json", output)
            print("PASS unit phase; normal packing contracts validated")
            return 0
        baseline_summary, baseline_inputs, baseline_manifest_sha = _read_phase_files(
            "baseline", contract)
        baseline_seal, baseline_seal_sha = _read_input_seal("baseline", contract)
        expected_baseline = _baseline_assessment(
            contract, baseline_summary, baseline_inputs, baseline_manifest_sha,
            baseline_seal, baseline_seal_sha)
        baseline_data = _task_file("baseline-assessment.json", 4 * 1024 * 1024)
        _need(_typed_equal(_parse_json(baseline_data, "baseline prerequisite"),
                           expected_baseline),
              "routes phase lacks a fresh matching baseline assessment")
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files("unit", contract)
        expected_unit = _unit_validation(contract, unit_summary, unit_inputs, unit_manifest_sha)
        unit_data = _task_file("unit-validation.json", 4 * 1024 * 1024)
        unit_prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        candidate_summary, candidate_inputs, candidate_manifest_sha = _read_phase_files(
            "routes", contract)
        candidate_seal, candidate_seal_sha = _read_input_seal("candidate", contract)
        output = _routes_assessment(
            contract, baseline_summary, baseline_inputs, baseline_manifest_sha,
            candidate_summary, candidate_inputs, candidate_manifest_sha,
            unit_prerequisite_sha, baseline_seal, baseline_seal_sha,
            candidate_seal, candidate_seal_sha)
        _write_artifact("routes-assessment.json", output)
        if output["fitnessStatus"] != "PASS":
            print("FAIL routes fitness: see routes-assessment.json", file=sys.stderr)
            return 2
        print("PASS routes phase; integrity/native/fitness validated")
        return 0
    except ReaderError as exc:
        print("FAIL offline normal-packing reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
