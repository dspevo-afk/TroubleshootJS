"""Strict offline reader for one private routed passive geometry move proof."""

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
TASK_REL = "docs/task-evidence/PCB/routed-passive-move-proof-r1"
PRIOR_ASSESS_REL = "docs/task-evidence/PCB/routed-passive-leg-census-r1/assess.py"

PROTOCOL_SHA = "6bdde6eed11b1b441e8440843d0130eecf70079043017472c1e8cf7ce214568d"
PRIOR_ASSESS_SHA = "f9b79e21875864aecde86e41d1b49d955d6f9201585cebebf462ffd87fb82229"
PUBLISHED_INPUTS = {
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/native-inputs.json":
        "c3567e48821637702bd1f88ffc05a09ab34f8f0dde0f13553e5f5885acf1da05",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/root-review.json":
        "45eed3d73435073eb3eeb7126e7be04374e1fce0354843bf2b364da8c1a5631d",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/protocol.json":
        "82212f6b7df2ecfdc3301a21908775ebdc6d63dac47539f2635693cd703236fd",
    "docs/task-evidence/PCB/routed-passive-leg-census-r1/routes.txt":
        "ea6076b1189b44ada29a7638e5892990cd7271117594a875d28a485f2012f22e",
    "docs/task-evidence/PCB/successful-route-pool-r1/native-inputs.json":
        "6b1a003c2a76bad4488d04e69b82ab86b87fa4e2284ed7545c698cc94f778ea8",
    "docs/task-evidence/PCB/successful-route-pool-r1/routes.txt":
        "c75c440c15780f4cf222ad882a19e2cad6d55fcedf10c993bd66fad6e209b63f",
}
SUITE_MANIFEST_SHA = {
    "unit-suites.json":
        "bdec52313bb9e597df0da9088a7ebc95ee5db3669209e3619561382d11646f0a",
    "routes-suites.json":
        "d1561e10dbd6b37cc447faa0c4304cd44f9f8e1a8bdbc8b407833530534a14c3",
}
CENSUS_ROUTES_PATH = "docs/task-evidence/PCB/routed-passive-leg-census-r1/routes.txt"
WHITESPACE_PREFIX = "PCB_WHITESPACE_CASE "
SUMMARY_PREFIX = "PCB_ROUTED_LEG_CENSUS "
CASE_PREFIX = "PCB_ROUTED_LEG_CASE "
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
    spec = importlib.util.spec_from_file_location("pinned_routed_leg_census_assess", path)
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
    # Only pure parsing, case-shape, whitespace and census-route helpers are
    # composed.  The old phase suite and artifact assessment are not reused.
    module.ReaderError = ReaderError
    return module


PRIOR = _load_prior_reader()
_typed_equal = PRIOR._typed_equal


def _parse_json(data, label):
    """Compose the pinned parser while normalizing its nested ReaderError type."""
    try:
        return PRIOR._parse_json(data, label)
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc


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
        "schema", "baseCommit", "scope", "roots", "version", "density",
        "phase", "targetStrategy", "targetAttempt", "constructionLimitPerTargetCallback",
        "selection", "proofPrefix", "reportOrder", "proofFields", "moveFields",
        "sourceFields", "candidateFields", "validationFields", "expectedStatus",
        "expectedMove", "expectedSource", "expectedCandidate", "expectedPadPairCount",
        "reachabilityCanonical", "unitFixtureCounts", "unitEvaluators", "routePassDetail",
        "timingScope", "maxPhaseElapsedSeconds", "maxCaseMillis", "maxFileBytes",
        "publishedInputs", "priorCensusReader", "suiteManifestSha256", "limits",
    )
    _need(type(protocol) is dict and tuple(protocol) == expected_keys,
          "move-proof protocol schema/order changed")
    _need(protocol["schema"] == 1 and protocol["baseCommit"] ==
          "a816fdf186e2c89316bdd57baf60393299795a02",
          "move-proof protocol identity changed")
    _need(protocol["scope"] ==
          "One private endpoint-only passive move; no selected-layout, route, policy or fitness change",
          "move-proof scope changed")
    _need(_typed_equal(protocol["roots"], list(ROOTS)) and
          type(protocol["version"]) is int and protocol["version"] == VERSION and
          protocol["density"] == 14 and protocol["phase"] == "FINALIZED_ROUTE_CANDIDATE" and
          protocol["targetStrategy"] == "P07_FULLER_TWO_LAYER" and
          protocol["targetAttempt"] == 2 and
          protocol["constructionLimitPerTargetCallback"] == 1 and
          protocol["selection"] ==
          "First canonical census part/direction structurally eligible with analyticCopperDelta<=0",
          "move-proof target or selection changed")
    _need(protocol["proofPrefix"] == "PCB_ROUTED_PASSIVE_MOVE_PROOF " and
          _typed_equal(protocol["reportOrder"], [
              "PCB_WHITESPACE_CASE ", "PCB_ROUTED_LEG_CENSUS ",
              "PCB_ROUTED_LEG_CASE ", "PCB_ROUTED_PASSIVE_MOVE_PROOF "]),
          "move-proof marker/order changed")
    _need(_typed_equal(protocol["proofFields"], [
        "seed", "label", "version", "phase", "status", "constructionCount",
        "newRouteRequests", "selectedMutation", "move", "source", "candidate",
        "proof", "physicalFeasibility"]), "move-proof fields changed")
    _need(_typed_equal(protocol["moveFields"], [
        "componentId", "dx", "dy", "analyticCopperDelta", "actualCopperDelta"]),
          "move fields changed")
    _need(_typed_equal(protocol["sourceFields"], [
        "liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter",
        "copperLength", "area"]), "source fields changed")
    _need(_typed_equal(protocol["candidateFields"], [
        "geometryHash", "componentCount", "padCount", "traceCount",
        "affectedTraceCount", "movedPadCount", "copperLength", "area",
        "generationMetadata"]), "candidate fields changed")
    _need(_typed_equal(protocol["validationFields"], [
        "placementValidated", "geometryValidated", "twoLayerValidated", "padPairCount",
        "sourceReachabilityHash", "candidateReachabilityHash", "sourceRouteSourceCount",
        "candidateRouteSourceCount", "sourceTraceProvenanceHash",
        "candidateTraceProvenanceHash", "traceEndpointOnlyVerified",
        "unrelatedGeometryVerified", "conductorReachabilityVerified",
        "traceProvenanceVerified"]), "validation fields changed")
    _need(_typed_equal(protocol["expectedStatus"], {
        "5": "NO_TARGET", "2": "PRIVATE_VALIDATED",
    }) and _typed_equal(protocol["expectedMove"], {
        "componentId": "C5", "dx": 0, "dy": -10,
        "analyticCopperDelta": -10, "actualCopperDelta": -10,
    }), "move-proof expected statuses changed")
    _need(_typed_equal(protocol["expectedSource"], {
        "area": 9313352, "copperLength": 51860, "traceCount": 145,
        "geometryHash": "e5242445527c50145b0848e79efb045518ef56e0a3f559ddeeb9ba798efd6c6e",
    }) and _typed_equal(protocol["expectedCandidate"], {
        "componentCount": 56, "padCount": 135, "traceCount": 145,
        "affectedTraceCount": 3, "movedPadCount": 2, "copperLength": 51850,
        "area": 9313352, "generationMetadata": "NOT_ASSIGNED_PRIVATE_CANDIDATE",
    }) and protocol["expectedPadPairCount"] == 9045,
          "move-proof source/candidate expectations changed")
    _need(protocol["reachabilityCanonical"] ==
          "R1\\n then sorted pad pairs i<j: field(a)+field(b)+bit+'\\n'; field=UTF16-code-unit-length+':'+ID; bit=1 iff same source net; UTF8 SHA256",
          "reachability canonicalization changed")
    _need(_typed_equal(protocol["unitFixtureCounts"], {
        "PcbRoutedPassiveLegCensusContractTest": 6,
        "PcbRoutedPassiveMoveProofContractTest": 4,
    }) and _typed_equal(protocol["unitEvaluators"], {
        "PcbRoutedPassiveLegCensusContractTest": "endpointLegCensus",
        "PcbRoutedPassiveMoveProofContractTest": "singlePrivateMove",
    }) and protocol["routePassDetail"] ==
          "roots=2 rows=2 evaluator=singlePrivateMove",
          "move-proof suite contract changed")
    _need(protocol["timingScope"] ==
          "Native single private passive move proof; unchanged per-JVM/search/physical limits" and
          protocol["maxPhaseElapsedSeconds"] == 36000 and
          protocol["maxCaseMillis"] == 60000 and
          protocol["maxFileBytes"] == MAX_FILE_BYTES,
          "move-proof timing bounds changed")
    _need(_typed_equal(protocol["publishedInputs"], PUBLISHED_INPUTS) and
          _typed_equal(protocol["priorCensusReader"], {
              "path": PRIOR_ASSESS_REL, "sha256": PRIOR_ASSESS_SHA,
          }) and _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          "move-proof input pins changed")
    _need(type(protocol["limits"]) is list and len(protocol["limits"]) == 6 and
          all(type(item) is str and item for item in protocol["limits"]),
          "move-proof limits changed")


def _published_rows(data, protocol):
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise ReaderError("published census routes are not strict UTF-8") from exc
    return _parse_report_lines(text, protocol)


def _load_contract(protocol_override=None, published_overrides=None):
    protocol_data = (_task_file("protocol.json", 256 * 1024)
                     if protocol_override is None else protocol_override)
    _require_sha(protocol_data, PROTOCOL_SHA, "sealed move-proof protocol")
    protocol = _parse_json(protocol_data, "move-proof protocol")
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
    return {"protocol": protocol, "protocolData": protocol_data, "published": blobs}


def _load_prior_contract(contract):
    """Load the pinned census reader and bind its published summary facts."""
    try:
        prior = PRIOR._load_contract()
        raw = contract["published"][CENSUS_ROUTES_PATH]
        text = raw.decode("utf-8", errors="strict")
        published_reports = PRIOR._parse_report_lines(text, prior["protocol"])
        published_assessment = PRIOR._assess_routes(
            published_reports, prior["selected"], prior["whitespace"],
            prior["protocol"])
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    _need(published_assessment["status"] == "PASS" and
          published_assessment["rows"] == 2 and
          published_assessment["selectedCaseRows"] == 2,
          "published census rows failed the pinned prior reader")
    summaries = {}
    for prefix, row, _line in published_reports:
        if prefix == SUMMARY_PREFIX:
            _need(row["seed"] not in summaries,
                  "published census summary is duplicated")
            summaries[row["seed"]] = row
    _need(tuple(summaries) == ROOTS,
          "published census summaries lack the exact root order")
    prior["publishedCensusSummaries"] = summaries
    return prior


def _load_suite_manifest(phase, protocol, raw_override=None):
    _need(phase in ("unit", "routes"), "unknown native phase")
    name = "unit-suites.json" if phase == "unit" else "routes-suites.json"
    expected = (
        [["PcbRoutedPassiveLegCensusContractTest",
          "PCB routed passive leg census contracts "],
         ["PcbRoutedPassiveMoveProofContractTest",
          "PCB routed passive move proof contracts "]]
        if phase == "unit" else
        [["PcbRoutedPassiveMoveProofTrialContractTest",
          "PCB routed passive move proof trial contracts "]]
    )
    raw = (_task_file(name, 64 * 1024) if raw_override is None else raw_override)
    _require_sha(raw, SUITE_MANIFEST_SHA[name], "sealed " + phase + " suite manifest")
    actual = _parse_json(raw, phase + " suite manifest")
    _need(_typed_equal(actual, expected) and
          _typed_equal(protocol["suiteManifestSha256"], SUITE_MANIFEST_SHA),
          phase + " suite manifest order/content changed")
    return actual, _sha(raw)


def _parse_report_lines(text, protocol):
    _need(type(text) is str, "native phase text is missing")
    try:
        _need(len(text.encode("utf-8", errors="strict")) <= protocol["maxFileBytes"],
              "native phase text exceeds its bound")
    except UnicodeEncodeError as exc:
        raise ReaderError("native phase text is not strict UTF-8") from exc
    prefixes = (
        WHITESPACE_PREFIX, SUMMARY_PREFIX, CASE_PREFIX,
        protocol["proofPrefix"],
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


def _target_callback(summary):
    callbacks = summary["successfulCallbacks"]
    targets = [callback for callback in callbacks if callback["target"]]
    _need(len(targets) <= 1, "move-proof target callback is duplicated")
    return targets[0] if targets else None


def _derive_move(summary, protocol):
    callback = _target_callback(summary)
    if callback is None:
        return None
    for part in callback["parts"]:
        if not part["eligiblePackage"]:
            continue
        for direction in part["directions"]:
            if direction["eligible"] and direction["copperDelta"] <= 0:
                move = {
                    "componentId": part["componentId"],
                    "dx": direction["dx"], "dy": direction["dy"],
                    "analyticCopperDelta": direction["copperDelta"],
                    "actualCopperDelta": protocol["expectedMove"]["actualCopperDelta"],
                }
                _need(_typed_equal({key: move[key] for key in protocol["moveFields"]},
                                   protocol["expectedMove"]),
                      "derived first nonincreasing move differs from protocol expectation")
                return move
    raise ReaderError("target callback has no nonincreasing eligible direction")


def _utf16_units(value):
    try:
        return len(value.encode("utf-16-le", errors="strict")) // 2
    except UnicodeEncodeError as exc:
        raise ReaderError("pad ID is not valid UTF-16 text") from exc


def _reachability_hash(summary):
    callback = _target_callback(summary)
    _need(callback is not None, "reachability oracle requires a target callback")
    pads = []
    for part in callback["parts"]:
        for pad in part["pads"]:
            pads.append((pad["padId"], pad["netId"]))
    _need(len(pads) == 135 and len({pad_id for pad_id, _net in pads}) == len(pads),
          "reachability oracle requires all 135 unique pads")
    pads.sort(key=lambda item: item[0])
    digest = hashlib.sha256()
    digest.update(b"R1\n")
    for index, (left_id, left_net) in enumerate(pads):
        left_field = str(_utf16_units(left_id)) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right_field = str(_utf16_units(right_id)) + ":" + right_id
            bit = "1" if left_net == right_net else "0"
            digest.update((left_field + right_field + bit + "\n").encode("utf-8"))
    return digest.hexdigest()


def _validate_nested_shape(value, fields, label):
    _need(type(value) is dict and tuple(value) == tuple(fields),
          label + " schema/order changed")


def _validate_proof(row, seed, case, summary, protocol, published_summary=None):
    _validate_nested_shape(row, protocol["proofFields"], "move-proof row")
    _need(_canonical_seed(row["seed"], "move-proof seed") == seed and
          type(row["label"]) is str and row["label"] == "seed" + seed and
          type(row["version"]) is int and row["version"] == VERSION and
          row["phase"] == protocol["phase"] and type(row["phase"]) is str and
          type(row["status"]) is str and
          row["status"] == protocol["expectedStatus"][seed],
          "move-proof identity/status changed")
    _need(type(row["constructionCount"]) is int and
          row["constructionCount"] == (0 if seed == "5" else 1) and
          type(row["newRouteRequests"]) is int and row["newRouteRequests"] == 0 and
          type(row["selectedMutation"]) is bool and row["selectedMutation"] is False,
          "move-proof construction or mutation boundary changed")
    _need(type(row["physicalFeasibility"]) is str,
          "move-proof physical disposition is not text")
    if seed == "5":
        _need(row["move"] is None and row["source"] is None and
              row["candidate"] is None and row["proof"] is None and
              row["physicalFeasibility"] == "NOT_EVALUATED",
              "no-target move proof must keep all nested fields null")
        return {"seed": seed, "status": row["status"], "reachabilityHash": None}

    _need(row["physicalFeasibility"] == "VALIDATED_PRIVATE_CANDIDATE",
          "private candidate physical disposition changed")
    oracle_summary = summary if published_summary is None else published_summary
    expected_move = _derive_move(oracle_summary, protocol)
    _validate_nested_shape(row["move"], protocol["moveFields"], "move")
    _need(_typed_equal(row["move"], expected_move),
          "move does not match the first canonical nonincreasing census direction")

    source = row["source"]
    _validate_nested_shape(source, protocol["sourceFields"], "source")
    for key in ("liveGraphBefore", "liveGraphAfter", "geometryBefore", "geometryAfter"):
        _hex_digest(source[key], "source " + key)
    target = _target_callback(oracle_summary)
    _need(target is not None and target["geometryHashBefore"] ==
          target["geometryHashAfter"] == protocol["expectedSource"]["geometryHash"],
          "target census geometry is not the pinned source geometry")
    _need(source["liveGraphBefore"] == source["liveGraphAfter"] ==
          case["liveBoardGraphSha256"] and
          source["geometryBefore"] == source["geometryAfter"] ==
          target["geometryHashBefore"] == protocol["expectedSource"]["geometryHash"] and
          type(source["copperLength"]) is int and
          source["copperLength"] == protocol["expectedSource"]["copperLength"] and
          type(source["area"]) is int and source["area"] == protocol["expectedSource"]["area"],
          "source graph/geometry or pinned source metrics changed")

    candidate = row["candidate"]
    _validate_nested_shape(candidate, protocol["candidateFields"], "candidate")
    _hex_digest(candidate["geometryHash"], "candidate geometryHash")
    _need(candidate["geometryHash"] != source["geometryBefore"] and
          type(candidate["componentCount"]) is int and
          type(candidate["padCount"]) is int and type(candidate["traceCount"]) is int and
          type(candidate["affectedTraceCount"]) is int and
          type(candidate["movedPadCount"]) is int and
          type(candidate["copperLength"]) is int and type(candidate["area"]) is int and
          candidate["generationMetadata"] ==
          protocol["expectedCandidate"]["generationMetadata"],
          "candidate geometry/metadata is malformed")
    for key, expected in protocol["expectedCandidate"].items():
        if key != "generationMetadata":
            _need(candidate[key] == expected,
                  "candidate metric changed: " + key)

    proof = row["proof"]
    _validate_nested_shape(proof, protocol["validationFields"], "proof validation")
    for key in ("placementValidated", "geometryValidated", "twoLayerValidated",
                "traceEndpointOnlyVerified", "unrelatedGeometryVerified",
                "conductorReachabilityVerified", "traceProvenanceVerified"):
        _need(type(proof[key]) is bool and proof[key] is True,
              "proof flag is not strict true: " + key)
    _need(type(proof["padPairCount"]) is int and
          proof["padPairCount"] == protocol["expectedPadPairCount"],
          "proof pad-pair count changed")
    expected_reachability = _reachability_hash(oracle_summary)
    for key in ("sourceReachabilityHash", "candidateReachabilityHash",
                "sourceTraceProvenanceHash", "candidateTraceProvenanceHash"):
        _hex_digest(proof[key], "proof " + key)
    _need(proof["sourceReachabilityHash"] ==
          proof["candidateReachabilityHash"] == expected_reachability,
          "source/candidate reachability does not match independent net oracle")
    _need(type(proof["sourceRouteSourceCount"]) is int and
          type(proof["candidateRouteSourceCount"]) is int and
          proof["sourceRouteSourceCount"] == proof["candidateRouteSourceCount"] == 145,
          "source/candidate route-source counts changed")
    _need(proof["sourceTraceProvenanceHash"] ==
          proof["candidateTraceProvenanceHash"],
          "source/candidate trace provenance hashes differ")
    return {
        "seed": seed, "status": row["status"],
        "reachabilityHash": expected_reachability,
    }


def _assess_routes(reports, contract, prior_contract):
    protocol = contract["protocol"]
    _need(type(reports) is list and len(reports) == 8,
          "routes phase requires exactly eight ordered PCB report rows")
    old_reports = []
    proof_rows = []
    cursor = 0
    rows = []
    for seed in ROOTS:
        expected = (WHITESPACE_PREFIX, SUMMARY_PREFIX, CASE_PREFIX,
                    protocol["proofPrefix"])
        _need(tuple(reports[cursor + offset][0] for offset in range(4)) == expected,
              "routes reports must be whitespace, census, case, move proof per root")
        old_reports.extend(reports[cursor:cursor + 3])
        summary = reports[cursor + 1][1]
        case = reports[cursor + 2][1]
        proof = reports[cursor + 3][1]
        rows.append((seed, summary, case, proof))
        cursor += 4
    _need(cursor == len(reports), "extra or foreign move-proof reports remain")
    # This is the prior census reader's exact current15/callback oracle.  Only
    # the three old report rows are passed to it; no old phase suite is reused.
    try:
        prior_summary = PRIOR._assess_routes(
            old_reports, prior_contract["selected"], prior_contract["whitespace"],
            prior_contract["protocol"])
    except ReaderError:
        raise
    except ValueError as exc:
        raise ReaderError(str(exc)) from exc
    _need(prior_summary["status"] == "PASS" and prior_summary["rows"] == 2 and
          prior_summary["selectedCaseRows"] == 2,
          "prior census reader did not validate both current15 rows")
    result_rows = []
    for seed, summary, case, proof in rows:
        published_summary = prior_contract["publishedCensusSummaries"][seed]
        _need(_typed_equal(summary, published_summary),
              "current census summary differs from sealed published census facts")
        proof_rows.append(_validate_proof(
            proof, seed, case, summary, protocol, published_summary))
        result_rows.append({"seed": seed, "status": proof["status"]})
    return {
        "status": "PASS", "scope": protocol["scope"], "roots": 2, "rows": 2,
        "selectedCaseRows": 2, "whitespaceRows": 2,
        "successfulCallbacks": prior_summary["successfulCallbacks"],
        "moveRows": 2, "moves": result_rows,
        "reachabilityOracle": proof_rows[1]["reachabilityHash"],
        "observationDisposition": "PASS; one private geometry proof only",
        "physicalFeasibility": "VALIDATED_PRIVATE_CANDIDATE",
        "fitnessDisposition": "NOT_EVALUATED",
    }


def _unit_pass_line(line, suite, protocol):
    counts = protocol["unitFixtureCounts"]
    evaluators = protocol["unitEvaluators"]
    expected_prefix = "PASS: " + suite[1]
    match = re.fullmatch(
        re.escape(expected_prefix) + r"assertions=([1-9][0-9]*) fixtures=" +
        str(counts[suite[0]]) + r" evaluator=" + re.escape(evaluators[suite[0]]),
        line,
    )
    _need(match is not None,
          "unit PASS marker/count/evaluator is malformed for " + suite[0])
    _bounded_int(int(match.group(1)), "unit assertion count", 1)


def _phase_summary(text, receipt, timing, suites, phase, protocol,
                   prior_contract, selected_rows=None, whitespace_rows=None):
    _need(type(timing) is dict and tuple(timing) == TIMING_FIELDS,
          "native timing schema/order changed")
    _need(phase in ("unit", "routes") and timing["phase"] == phase,
          "native phase differs from requested phase")
    actual_suites = suites
    expected_suites, _manifest_sha = _load_suite_manifest(phase, protocol)
    _need(_typed_equal(actual_suites, expected_suites),
          "native suite rows differ from pinned order")
    _need(timing["status"] == "PASS" and type(timing["status"]) is str and
          type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
          type(timing["suiteCount"]) is int and
          timing["suiteCount"] == len(expected_suites),
          "native phase status/exit/suite count failed")
    elapsed = timing["elapsedSeconds"]
    _need(type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and 0 <= elapsed <= protocol["maxPhaseElapsedSeconds"],
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
    _need(text_size <= protocol["maxFileBytes"] and receipt_size <= protocol["maxFileBytes"],
          "native phase log/receipt exceeds its bound")
    text_lines, receipt_lines = text.splitlines(), receipt.splitlines()
    _need(not any(FAILURE_LINE.search(line) or SKIP_OR_FAILURE_WORD.search(line)
                  for line in text_lines + receipt_lines),
          "native phase contains failure, timeout, skip, or cancellation output")
    terminal = TERMINAL_TEMPLATE.format(count=len(expected_suites))
    _need(text_lines.count(terminal) == 1 and text_lines.count(CLEANUP_LINE) == 1,
          "native terminal PASS or cleanup marker is missing/duplicated")
    _need(terminal not in receipt_lines and CLEANUP_LINE not in receipt_lines and
          text_lines.index(terminal) < text_lines.index(CLEANUP_LINE),
          "native receipt or terminal cleanup boundary changed")
    suite_passes = [line for line in text_lines if line.startswith("PASS: ") and line != terminal]
    receipt_passes = [line for line in receipt_lines if line.startswith("PASS: ")]
    _need(suite_passes == receipt_passes and len(suite_passes) == len(expected_suites),
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
    _need([item[2] for item in text_reports] == [item[2] for item in receipt_reports],
          "native report lines differ between terminal log and child receipt")
    terminal_position = text_lines.index(terminal)
    _need(all(position < terminal_position for position, line in enumerate(text_lines)
              if line.startswith("PCB_")),
          "native report appeared after terminal marker")
    if phase == "unit":
        _need(not text_reports and not receipt_reports,
              "unit contract leaked routed move reports")
        phase_result = None
    else:
        report_positions = [position for position, line in enumerate(text_lines)
                            if line.startswith("PCB_")]
        suite_positions = [text_lines.index(line) for line in suite_passes]
        _need(report_positions and max(report_positions) < min(suite_positions),
              "routes suite PASS appeared before all PCB reports")
        receipt_report_positions = [position for position, line in enumerate(receipt_lines)
                                    if line.startswith("PCB_")]
        receipt_suite_positions = [receipt_lines.index(line) for line in receipt_passes]
        _need(receipt_report_positions and
              max(receipt_report_positions) < min(receipt_suite_positions),
              "routes child receipt PASS appeared before all PCB reports")
        phase_result = _assess_routes(text_reports, {
            "protocol": protocol, "selected": selected_rows,
            "whitespace": whitespace_rows,
        }, prior_contract)
    return {
        "status": "PASS", "phase": phase, "suiteCount": len(expected_suites),
        "suiteClasses": [row[0] for row in expected_suites],
        "reportRows": len(text_reports),
        "orderedMarkers": [row[1] for row in expected_suites],
        "moveProof": phase_result,
    }


def check_phase(text, receipt, timing, suites, phase):
    """Validate an in-memory native phase against the sealed move-proof contract."""
    contract = _load_contract()
    prior_contract = _load_prior_contract(contract)
    actual_suites, _manifest_sha = _load_suite_manifest(phase, contract["protocol"])
    _need(_typed_equal(suites, actual_suites),
          "caller suite manifest differs from pinned bytes")
    return _phase_summary(
        text, receipt, timing, suites, phase, contract["protocol"], prior_contract,
        prior_contract["selected"], prior_contract["whitespace"])


def _read_phase_files(phase, contract, prior_contract):
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
    selected = prior_contract["selected"]
    whitespace = prior_contract["whitespace"]
    summary = _phase_summary(text, receipt, timing, suites, phase, protocol,
                             prior_contract, selected, whitespace)
    return summary, {name: _sha(data) for name, data in raw.items()}, manifest_sha


def _unit_validation(contract, summary, phase_inputs, manifest_sha):
    protocol = contract["protocol"]
    return {
        "schema": 1,
        "status": "PASS",
        "phase": "unit",
        "scope": "Private move proof unit fixtures only; routes and fitness are not evaluated.",
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": _sha(contract["protocolData"]),
        "priorAssessSha256": PRIOR_ASSESS_SHA,
        "publishedInputsSha256": {
            name: _sha(data) for name, data in contract["published"].items()
        },
        "suiteManifestSha256": manifest_sha,
        "phaseInputsSha256": phase_inputs,
        "phaseEvidence": summary,
        "fixtureCounts": dict(protocol["unitFixtureCounts"]),
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
    move_proof = summary["moveProof"]
    _need(type(move_proof) is dict and move_proof.get("status") == "PASS",
          "private move proof is missing or failed")
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
        "nativePhase": {"status": "PASS", "phase": "routes", "evidence": summary},
        "observationDisposition": "PASS; one private geometry proof only",
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
        prior_contract = _load_prior_contract(contract)
        summary, phase_inputs, manifest_sha = _read_phase_files(
            args.phase, contract, prior_contract)
        if args.phase == "unit":
            output = _unit_validation(contract, summary, phase_inputs, manifest_sha)
            _write_artifact("unit-validation.json", output)
            print("PASS unit phase; routes and fitness are not evaluated")
            return 0
        unit_data = _task_file("unit-validation.json", 1024 * 1024)
        unit_summary, unit_inputs, unit_manifest_sha = _read_phase_files(
            "unit", contract, prior_contract)
        expected_unit = _unit_validation(
            contract, unit_summary, unit_inputs, unit_manifest_sha)
        prerequisite_sha = _validate_unit_prerequisite(unit_data, expected_unit)
        output = _routes_assessment(
            contract, summary, phase_inputs, manifest_sha, prerequisite_sha)
        _write_artifact("assessment.json", output)
        print("PASS routes phase; observation PASS; fitness NOT_EVALUATED")
        return 0
    except ReaderError as exc:
        print("FAIL offline reader: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
