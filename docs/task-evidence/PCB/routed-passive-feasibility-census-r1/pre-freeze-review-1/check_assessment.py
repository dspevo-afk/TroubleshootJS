"""Independent in-memory canaries for the closed physical-feasibility reader."""

import copy
import hashlib
import json
import sys
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"
PUBLISHED_HASH_LITERAL = (
    "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7")


def _ordered(fields, values):
    return {field: values[field] for field in fields}


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False, separators=(",", ":"),
                               allow_nan=False)


def _manual_reachability(summary):
    """Build the independent R1 oracle directly from sealed pad/net facts."""
    callbacks = summary["successfulCallbacks"]
    targets = [callback for callback in callbacks if callback["target"]]
    if len(targets) != 1:
        raise AssertionError("published oracle must have one target callback")
    pads = []
    for part in targets[0]["parts"]:
        for pad in part["pads"]:
            pad_id, net_id = pad["padId"], pad["netId"]
            if type(pad_id) is not str or type(net_id) is not str:
                raise AssertionError("published pad identity/net is not text")
            pads.append((pad_id, net_id))
    if len(pads) != 135 or len({item[0] for item in pads}) != 135:
        raise AssertionError("published oracle does not contain 135 unique pads")
    pads.sort(key=lambda item: item[0])
    payload = bytearray(b"R1\n")
    pair_count = 0
    for index, (left_id, left_net) in enumerate(pads):
        left_field = str(len(left_id.encode("utf-16-le")) // 2) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right_field = str(len(right_id.encode("utf-16-le")) // 2) + ":" + right_id
            bit = "1" if left_net == right_net else "0"
            payload.extend((left_field + right_field + bit + "\n").encode("utf-8"))
            pair_count += 1
    if pair_count != 9045 or len(payload) != 175409:
        raise AssertionError("published oracle canonical stream size changed")
    return hashlib.sha256(payload).hexdigest(), pair_count, len(payload)


def _context():
    contract = assess._load_contract()
    prior = assess._load_prior_contract(contract)
    unit_suites, unit_sha = assess._load_suite_manifest("unit", contract["protocol"])
    route_suites, route_sha = assess._load_suite_manifest("routes", contract["protocol"])
    old_reports = assess._parse_report_lines(
        contract["published"][assess.CENSUS_ROUTES_PATH].decode("utf-8"),
        contract["protocol"])
    old_by_seed = {}
    for seed in assess.ROOTS:
        old_by_seed[seed] = {}
        for prefix in (assess.WHITESPACE_PREFIX, assess.CENSUS_PREFIX,
                       assess.CASE_PREFIX):
            matches = [item for item in old_reports
                       if item[0] == prefix and item[1]["seed"] == seed]
            if len(matches) != 1:
                raise AssertionError("sealed old report fixture is not unique")
            old_by_seed[seed][prefix] = matches[0]
    expected, pair_count, byte_count = _manual_reachability(
        prior["publishedCensusSummaries"]["2"])
    if expected != PUBLISHED_HASH_LITERAL:
        raise AssertionError("independent published reachability literal changed")
    if assess._reachability_hash(prior["publishedCensusSummaries"]["2"]) != expected:
        raise AssertionError("reader oracle disagrees with independent net oracle")
    return {
        "contract": contract,
        "prior": prior,
        "protocol": contract["protocol"],
        "unitSuites": unit_suites,
        "routeSuites": route_suites,
        "unitManifestSha": unit_sha,
        "routeManifestSha": route_sha,
        "oldBySeed": old_by_seed,
        "publishedReachabilityHash": expected,
        "publishedReachabilityPairCount": pair_count,
        "publishedReachabilityByteCount": byte_count,
    }


def _timing(ctx, phase):
    return {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
        "suiteCount": 2 if phase == "unit" else 1, "phase": phase,
        "scope": ctx["protocol"]["timingScope"],
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }


def _unit_fixture(ctx):
    protocol = ctx["protocol"]
    lines = []
    for suite, assertions in zip(ctx["unitSuites"], (41, 23)):
        lines.append(
            "PASS: " + suite[1] + "assertions=" + str(assertions) +
            " fixtures=" + str(protocol["unitFixtureCounts"][suite[0]]) +
            " evaluator=" + protocol["unitEvaluators"][suite[0]])
    receipt = "\n".join(lines) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=2)
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "unit"), ctx["unitSuites"]


def _source(ctx):
    expected = ctx["protocol"]["expectedSource"]
    graph = ctx["prior"]["publishedCensusCases"]["2"]["liveBoardGraphSha256"]
    source = {
        "liveGraphBefore": graph, "liveGraphAfter": graph,
        "geometryBefore": expected["geometryHash"],
        "geometryAfter": expected["geometryHash"],
        "copperLength": expected["copperLength"], "area": expected["area"],
        "componentCount": expected["componentCount"],
        "padCount": expected["padCount"], "traceCount": expected["traceCount"],
        "traceProvenanceHash": "a" * 64,
        "sourceRouteSourceCount": expected["sourceRouteSourceCount"],
        "reachabilityHash": ctx["publishedReachabilityHash"],
        "padPairCount": ctx["publishedReachabilityPairCount"],
    }
    return _ordered(ctx["protocol"]["sourceFields"], source)


def _candidate(ctx, index, move):
    summary = ctx["prior"]["publishedCensusSummaries"]["2"]
    part = assess._part_for_move(summary, move)
    trace_indices = {incident["traceIndex"]
                     for pad in part["pads"] for incident in pad["incidents"]}
    expected = ctx["protocol"]["expectedSource"]
    candidate = {
        "geometryHash": format(index + 1, "064x"),
        "componentCount": expected["componentCount"],
        "padCount": expected["padCount"],
        "traceCount": expected["traceCount"],
        "affectedTraceCount": len(trace_indices),
        "movedPadCount": 2,
        "copperLength": expected["copperLength"] + move["actualCopperDelta"],
        "area": expected["area"],
        "generationMetadata": assess.GENERATION_METADATA,
    }
    return _ordered(ctx["protocol"]["candidateFields"], candidate)


def _checks(stage):
    values = {
        "PLACEMENT": (False, False, False),
        "GEOMETRY": (True, False, False),
        "TWO_LAYER": (True, True, False),
        "COMPLETE": (True, True, True),
    }[stage]
    data = {
        "traceEndpointOnlyVerified": True,
        "unrelatedGeometryVerified": True,
        "sourceImmutable": True,
        "placementValidated": values[0],
        "geometryValidated": values[1],
        "twoLayerValidated": values[2],
    }
    return _ordered(assess.CHECK_FIELDS, data)


def _timing_row(stage):
    values = {
        "constructionMillis": 1,
        "placementMillis": 2,
        "geometryMillis": 3 if stage in ("GEOMETRY", "TWO_LAYER", "COMPLETE") else None,
        "twoLayerMillis": 4 if stage in ("TWO_LAYER", "COMPLETE") else None,
        "proofMillis": 5 if stage == "COMPLETE" else None,
        "totalMillis": {
            "PLACEMENT": 3, "GEOMETRY": 6, "TWO_LAYER": 10, "COMPLETE": 15,
        }[stage],
    }
    return _ordered(assess.ROW_TIMING_FIELDS, values)


def _proof(ctx):
    data = {
        "placementValidated": True, "geometryValidated": True,
        "twoLayerValidated": True,
        "padPairCount": ctx["publishedReachabilityPairCount"],
        "sourceReachabilityHash": ctx["publishedReachabilityHash"],
        "candidateReachabilityHash": ctx["publishedReachabilityHash"],
        "sourceRouteSourceCount": 145, "candidateRouteSourceCount": 145,
        "sourceTraceProvenanceHash": "a" * 64,
        "candidateTraceProvenanceHash": "a" * 64,
        "traceEndpointOnlyVerified": True,
        "unrelatedGeometryVerified": True,
        "conductorReachabilityVerified": True,
        "traceProvenanceVerified": True,
    }
    return _ordered(ctx["protocol"]["proofFields"], data)


def _row(ctx, index, stage="GEOMETRY", reason=None, validated=False):
    protocol = ctx["protocol"]
    move = protocol["expectedMoves"][index]
    if validated:
        data = {
            "move": move, "candidate": _candidate(ctx, index, move),
            "disposition": assess.DISPOSITION_VALIDATED, "stage": "COMPLETE",
            "reason": None, "message": None, "checks": _checks("COMPLETE"),
            "proof": _proof(ctx), "timing": _timing_row("COMPLETE"),
        }
    else:
        reason = "TRACE_COMPONENT_COURTYARD" if reason is None else reason
        if reason in protocol["geometryPrefixReasons"]:
            prefix = protocol["geometryPrefixReasons"][reason]
            message = (protocol["expectedFirstRejection"]["message"]
                       if index == 0 and reason == "TRACE_COMPONENT_COURTYARD"
                       else prefix + "synthetic " + move["componentId"])
        elif reason in protocol["qualityReasons"]:
            message = protocol["qualityReasons"][reason] + "synthetic " + move["componentId"]
        else:
            message = protocol["twoLayerExactReasons"][reason]
        data = {
            "move": move, "candidate": _candidate(ctx, index, move),
            "disposition": assess.DISPOSITION_REJECTED, "stage": stage,
            "reason": reason, "message": message, "checks": _checks(stage),
            "proof": None, "timing": _timing_row(stage),
        }
    return _ordered(protocol["rowFields"], data)


def _summary(ctx, seed, mixed=False):
    protocol = ctx["protocol"]
    if seed == "5":
        data = {
            "seed": "5", "label": "seed5", "version": assess.VERSION,
            "phase": protocol["phase"], "status": "NO_TARGET",
            "newRouteRequests": 0, "selectedMutation": False,
            "constructionCount": 0, "source": None, "rows": [],
            "aggregate": {
                "structuralDirections": 0, "constructionCount": 0,
                "physicallyValidated": 0, "physicallyRejected": 0,
                "placementRejected": 0, "geometryRejected": 0,
                "twoLayerRejected": 0,
            },
            "elapsedMillis": 0,
        }
        return _ordered(protocol["summaryFields"], data)
    rows = [_row(ctx, index, validated=mixed and index == 1)
            for index in range(31)]
    counts = {
        "structuralDirections": 31, "constructionCount": 31,
        "physicallyValidated": sum(row["disposition"] == assess.DISPOSITION_VALIDATED
                                    for row in rows),
        "physicallyRejected": sum(row["disposition"] == assess.DISPOSITION_REJECTED
                                   for row in rows),
        "placementRejected": sum(row["stage"] == "PLACEMENT" for row in rows
                                  if row["disposition"] == assess.DISPOSITION_REJECTED),
        "geometryRejected": sum(row["stage"] == "GEOMETRY" for row in rows
                                 if row["disposition"] == assess.DISPOSITION_REJECTED),
        "twoLayerRejected": sum(row["stage"] == "TWO_LAYER" for row in rows
                                if row["disposition"] == assess.DISPOSITION_REJECTED),
    }
    data = {
        "seed": "2", "label": "seed2", "version": assess.VERSION,
        "phase": protocol["phase"], "status": "CENSUS_COMPLETE",
        "newRouteRequests": 0, "selectedMutation": False,
        "constructionCount": 31, "source": _source(ctx), "rows": rows,
        "aggregate": counts,
        "elapsedMillis": sum(row["timing"]["totalMillis"] for row in rows),
    }
    return _ordered(protocol["summaryFields"], data)


def _route_fixture(ctx, mixed=False):
    protocol = ctx["protocol"]
    lines = []
    for seed in assess.ROOTS:
        old = ctx["oldBySeed"][seed]
        for prefix in (assess.WHITESPACE_PREFIX, assess.CENSUS_PREFIX,
                       assess.CASE_PREFIX):
            lines.append(old[prefix][2])
        lines.append(_line(protocol["summaryPrefix"], _summary(ctx, seed, mixed)))
    marker = "PASS: " + ctx["routeSuites"][0][1] + protocol["routePassDetail"]
    receipt = "\n".join(lines + [marker]) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=1)
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "routes"), ctx["routeSuites"]


def _phase(ctx, phase, fixture):
    return assess._phase_summary(
        fixture[0], fixture[1], fixture[2], fixture[3], phase,
        ctx["protocol"], ctx["prior"], ctx["prior"]["selected"],
        ctx["prior"]["whitespace"])


def _rewrite_report(fixture, prefix, which, mutation, receipt=True):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        if which >= len(positions):
            raise AssertionError("canary report row is unavailable")
        position = positions[which]
        row = json.loads(lines[position][len(prefix):])
        mutation(row)
        lines[position] = _line(prefix, row)
        return "\n".join(lines) + "\n"

    return (rewrite(text), rewrite(child) if receipt else child,
            copy.deepcopy(timing), copy.deepcopy(suites))


def _append_json_key(fixture, prefix, which, fragment):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        position = positions[which]
        lines[position] = lines[position][:-1] + fragment + "}"
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _swap_reports(fixture, first, second):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith("PCB_")]
        lines[positions[first]], lines[positions[second]] = (
            lines[positions[second]], lines[positions[first]])
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _move_report_after_pass(fixture):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        report_positions = [index for index, line in enumerate(lines)
                            if line.startswith("PCB_")]
        report = lines.pop(report_positions[-1])
        marker = "PASS: " + suites[0][1] + assess._load_contract()["protocol"]["routePassDetail"]
        pass_position = lines.index(marker)
        lines.insert(pass_position + 1, report)
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _failure_line(fixture):
    text, child, timing, suites = fixture
    return ("FAIL: synthetic native failure\n" + text,
            "FAIL: synthetic native failure\n" + child,
            copy.deepcopy(timing), copy.deepcopy(suites))


def _foreign_net_matching_hash(ctx, fixture):
    old_prefix = assess.CENSUS_PREFIX
    new_prefix = ctx["protocol"]["summaryPrefix"]

    def alter_net(row):
        changed = False
        for callback in row["successfulCallbacks"]:
            for part in callback["parts"]:
                for pad in part["pads"]:
                    if not changed and pad["padId"] == "C5.1":
                        if pad["netId"] != "RAIL5":
                            raise AssertionError("published C5.1 net changed")
                        pad["netId"] = "FOREIGN_NET"
                        for incident in pad["incidents"]:
                            if incident["traceNetId"] != "RAIL5":
                                raise AssertionError("published C5.1 trace net changed")
                            incident["traceNetId"] = "FOREIGN_NET"
                        changed = True
        if not changed:
            raise AssertionError("published C5.1 pad was not found")

    altered = _rewrite_report(fixture, old_prefix, 1, alter_net)
    reports = assess._parse_report_lines(altered[0], ctx["protocol"])
    summary = next(row for prefix, row, _line in reports
                   if prefix == old_prefix and row["seed"] == "2")
    foreign_hash, _pairs, _bytes = _manual_reachability(summary)
    if foreign_hash == ctx["publishedReachabilityHash"]:
        raise AssertionError("foreign partition unexpectedly matched published hash")

    def patch_new(row):
        row["source"]["reachabilityHash"] = foreign_hash
        for candidate in row["rows"]:
            if candidate["disposition"] == assess.DISPOSITION_VALIDATED:
                candidate["proof"]["sourceReachabilityHash"] = foreign_hash
                candidate["proof"]["candidateReachabilityHash"] = foreign_hash

    return _rewrite_report(altered, new_prefix, 1, patch_new)


def _record_failure(results, negatives, name, phase, fixture, ctx):
    try:
        _phase(ctx, phase, fixture)
    except assess.ReaderError:
        negatives.append(name)
        results.append({"case": name, "result": "REJECTED"})
        return
    raise AssertionError("reader accepted rejected canary: " + name)


def _record_accept(results, positives, name, phase, fixture, ctx):
    _phase(ctx, phase, fixture)
    positives.append(name)
    results.append({"case": name, "result": "PASS"})


def _run_canaries():
    ctx = _context()
    unit = _unit_fixture(ctx)
    routes = _route_fixture(ctx)
    mixed = _route_fixture(ctx, mixed=True)
    results, positives, negatives = [], [], []

    _record_accept(results, positives, "valid unit phase with both evaluators", "unit", unit, ctx)
    _record_accept(results, positives, "valid all-rejected route census", "routes", routes, ctx)
    _record_accept(results, positives, "valid mixed rejected and validated rows", "routes", mixed, ctx)

    literal, pairs, byte_count = _manual_reachability(
        ctx["prior"]["publishedCensusSummaries"]["2"])
    if (literal != PUBLISHED_HASH_LITERAL or pairs != 9045 or byte_count != 175409 or
            ctx["publishedReachabilityHash"] != literal):
        raise AssertionError("manual reachability oracle is not the sealed literal")
    results.append({"case": "independent pad-net oracle", "result": "PASS",
                    "sha256": literal, "pairCount": pairs, "canonicalUtf8Bytes": byte_count})
    positives.append("independent pad-net oracle")

    summary_prefix = ctx["protocol"]["summaryPrefix"]
    nested = _rewrite_report(
        routes, summary_prefix, 1,
        lambda row: (row["rows"][1].__setitem__("reason", "DRILL_DOMAIN_BARRIER"),
                     row["rows"][1].__setitem__(
                         "message", ctx["protocol"]["twoLayerExactReasons"]["DRILL_DOMAIN_BARRIER"])))
    _record_accept(results, positives, "geometry outer call may report nested two-layer reason",
                   "routes", nested, ctx)
    later = _rewrite_report(
        routes, summary_prefix, 1,
        lambda row: (row["rows"][1].__setitem__("stage", "TWO_LAYER"),
                     row["rows"][1].__setitem__("reason", "DRILL_DOMAIN_BARRIER"),
                     row["rows"][1].__setitem__(
                         "message", ctx["protocol"]["twoLayerExactReasons"]["DRILL_DOMAIN_BARRIER"]),
                     row["rows"][1]["checks"].update(_checks("TWO_LAYER")),
                     row["rows"][1]["timing"].update(_timing_row("TWO_LAYER")),
                     row["aggregate"].__setitem__("geometryRejected", 30),
                     row["aggregate"].__setitem__("twoLayerRejected", 1),
                     row.__setitem__("elapsedMillis", 190)))
    _record_accept(results, positives, "explicit later two-layer call semantics",
                   "routes", later, ctx)
    placement = _rewrite_report(
        routes, summary_prefix, 1,
        lambda row: (row["rows"][1].__setitem__("stage", "PLACEMENT"),
                     row["rows"][1].__setitem__("reason", "DOMAIN_CORRIDOR"),
                     row["rows"][1].__setitem__("message", "synthetic placement"),
                     row["rows"][1]["checks"].update(_checks("PLACEMENT")),
                     row["rows"][1]["timing"].update(_timing_row("PLACEMENT")),
                     row["aggregate"].__setitem__("geometryRejected", 30),
                     row["aggregate"].__setitem__("placementRejected", 1),
                     row.__setitem__("elapsedMillis", 183)))
    _record_accept(results, positives, "placement rejection stage semantics",
                   "routes", placement, ctx)

    _record_failure(results, negatives, "foreign equal source graph pair", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].update(
                                        {"liveGraphBefore": "f" * 64,
                                         "liveGraphAfter": "f" * 64})), ctx)
    _record_failure(results, negatives, "changed foreign net with matching source and proof hashes",
                    "routes", _foreign_net_matching_hash(ctx, mixed), ctx)
    _record_failure(results, negatives, "first C5 courtyard rejection stage drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][0].__setitem__("stage", "TWO_LAYER")), ctx)
    _record_failure(results, negatives, "first C5 courtyard rejection reason drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][0].__setitem__("reason", "TRACE_SELF_OVERLAP")), ctx)
    _record_failure(results, negatives, "first C5 courtyard rejection message drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][0].__setitem__("message", "synthetic")), ctx)
    _record_failure(results, negatives, "forbidden removed drill-land reason", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: (row["rows"][0].__setitem__(
                                        "reason", "DRILL_LAND_CLEARANCE"),
                                        row["rows"][0].__setitem__(
                                            "message", "P07 overlapping drill/land clearance"))), ctx)
    _record_failure(results, negatives, "wrong canonical component move", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["move"].__setitem__("componentId", "C5")), ctx)
    _record_failure(results, negatives, "wrong canonical move direction", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["move"].__setitem__("dy", 10)), ctx)
    _record_failure(results, negatives, "analytic copper delta drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["move"].__setitem__("analyticCopperDelta", -9)), ctx)
    _record_failure(results, negatives, "actual copper delta drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["move"].__setitem__("actualCopperDelta", -9)), ctx)
    _record_failure(results, negatives, "31-row order drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"].__setitem__(
                                        1, row["rows"][2])), ctx)
    _record_failure(results, negatives, "missing structural direction row", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"].pop()), ctx)
    _record_failure(results, negatives, "duplicate structural direction row", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"].append(copy.deepcopy(row["rows"][-1]))), ctx)
    _record_failure(results, negatives, "target callback filter drift", "routes",
                    _rewrite_report(routes, assess.CENSUS_PREFIX, 1,
                                    lambda row: row["successfulCallbacks"][0].__setitem__("target", False)), ctx)
    _record_failure(results, negatives, "old census report order drift", "routes",
                    _swap_reports(routes, 2, 3), ctx)
    _record_failure(results, negatives, "routes report after suite PASS", "routes",
                    _move_report_after_pass(routes), ctx)
    _record_failure(results, negatives, "source geometry pair drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("geometryAfter", "0" * 64)), ctx)
    _record_failure(results, negatives, "candidate geometry equals source", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__(
                                        "geometryHash", row["source"]["geometryBefore"])), ctx)
    _record_failure(results, negatives, "source area drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("area", 9313351)), ctx)
    _record_failure(results, negatives, "source copper drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("copperLength", 51859)), ctx)
    _record_failure(results, negatives, "candidate area drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("area", 9313351)), ctx)
    _record_failure(results, negatives, "candidate copper metric drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("copperLength", 51851)), ctx)
    _record_failure(results, negatives, "candidate component count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("componentCount", 55)), ctx)
    _record_failure(results, negatives, "candidate pad count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("padCount", 134)), ctx)
    _record_failure(results, negatives, "candidate trace count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("traceCount", 144)), ctx)
    _record_failure(results, negatives, "candidate affected trace count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("affectedTraceCount", 0)), ctx)
    _record_failure(results, negatives, "candidate moved pad count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__("movedPadCount", 1)), ctx)
    _record_failure(results, negatives, "private generation metadata drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["candidate"].__setitem__(
                                        "generationMetadata", "FABRICATED_PLAN")), ctx)
    _record_failure(results, negatives, "source trace count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("traceCount", 144)), ctx)
    _record_failure(results, negatives, "source route source count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("sourceRouteSourceCount", 144)), ctx)
    _record_failure(results, negatives, "source pair count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("padPairCount", 9044)), ctx)
    _record_failure(results, negatives, "source reachability hash drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("reachabilityHash", "e" * 64)), ctx)
    _record_failure(results, negatives, "source provenance is not a digest", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["source"].__setitem__("traceProvenanceHash", "bad")), ctx)
    _record_failure(results, negatives, "source check boolean is not strict true", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["checks"].__setitem__("sourceImmutable", 1)), ctx)
    _record_failure(results, negatives, "geometry stage flag is inconsistent", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["checks"].__setitem__("geometryValidated", True)), ctx)
    _record_failure(results, negatives, "two-layer stage without geometry", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: (row["rows"][1].__setitem__("stage", "TWO_LAYER"),
                                                 row["rows"][1]["checks"].update(_checks("GEOMETRY")))), ctx)
    _record_failure(results, negatives, "unknown rejection classification", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1].__setitem__("reason", "BUDGET_EXHAUSTED")), ctx)
    _record_failure(results, negatives, "unknown rejection stage", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1].__setitem__("stage", "UNKNOWN")), ctx)
    _record_failure(results, negatives, "validated row with null proof", "routes",
                    _rewrite_report(mixed, summary_prefix, 1,
                                    lambda row: row["rows"][1].__setitem__("proof", None)), ctx)
    _record_failure(results, negatives, "validated row with false proof flag", "routes",
                    _rewrite_report(mixed, summary_prefix, 1,
                                    lambda row: row["rows"][1]["proof"].__setitem__(
                                        "conductorReachabilityVerified", False)), ctx)
    _record_failure(results, negatives, "rejected row with nonnull proof", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1].__setitem__("proof", {})), ctx)
    _record_failure(results, negatives, "proof pair count drift", "routes",
                    _rewrite_report(mixed, summary_prefix, 1,
                                    lambda row: row["rows"][1]["proof"].__setitem__("padPairCount", 9044)), ctx)
    _record_failure(results, negatives, "proof reachability hashes are foreign", "routes",
                    _rewrite_report(mixed, summary_prefix, 1,
                                    lambda row: (row["rows"][1]["proof"].__setitem__("sourceReachabilityHash", "e" * 64),
                                                 row["rows"][1]["proof"].__setitem__("candidateReachabilityHash", "e" * 64))), ctx)
    _record_failure(results, negatives, "proof provenance hashes differ", "routes",
                    _rewrite_report(mixed, summary_prefix, 1,
                                    lambda row: row["rows"][1]["proof"].__setitem__("candidateTraceProvenanceHash", "b" * 64)), ctx)
    _record_failure(results, negatives, "wrong geometry rejection prefix", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: (row["rows"][1].__setitem__("reason", "TRACE_SELF_OVERLAP"),
                                                 row["rows"][1].__setitem__("message", "wrong"))), ctx)
    _record_failure(results, negatives, "wrong two-layer rejection message", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: (row["rows"][1].__setitem__("stage", "TWO_LAYER"),
                                                 row["rows"][1].__setitem__("reason", "DRILL_DOMAIN_BARRIER"),
                                                 row["rows"][1].__setitem__("message", "wrong"),
                                                 row["rows"][1]["checks"].update(_checks("TWO_LAYER")),
                                                 row["rows"][1]["timing"].update(_timing_row("TWO_LAYER")))), ctx)
    _record_failure(results, negatives, "row timing field present before stage", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["timing"].__setitem__("twoLayerMillis", 4)), ctx)
    _record_failure(results, negatives, "row timing total below floor", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["timing"].__setitem__("totalMillis", 1)), ctx)
    _record_failure(results, negatives, "row timing exceeds case limit", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["rows"][1]["timing"].__setitem__("totalMillis", 60001)), ctx)
    _record_failure(results, negatives, "summary elapsed below row totals", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row.__setitem__("elapsedMillis", 0)), ctx)
    _record_failure(results, negatives, "aggregate classification drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row["aggregate"].__setitem__("geometryRejected", 30)), ctx)
    _record_failure(results, negatives, "construction count drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row.__setitem__("constructionCount", 30)), ctx)
    _record_failure(results, negatives, "new route request drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row.__setitem__("newRouteRequests", 1)), ctx)
    _record_failure(results, negatives, "selected mutation strict false drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row.__setitem__("selectedMutation", 0)), ctx)
    _record_failure(results, negatives, "summary version drift", "routes",
                    _rewrite_report(routes, summary_prefix, 1,
                                    lambda row: row.__setitem__("version", 14)), ctx)
    _record_failure(results, negatives, "duplicate summary JSON key", "routes",
                    _append_json_key(routes, summary_prefix, 0, ',"seed":"5"'), ctx)
    _record_failure(results, negatives, "unexpected summary JSON key", "routes",
                    _append_json_key(routes, summary_prefix, 0, ',"unexpected":1'), ctx)
    _record_failure(results, negatives, "native failure marker", "routes", _failure_line(routes), ctx)
    failed_timing = copy.deepcopy(routes)
    failed_timing[2]["elapsedSeconds"] = 36001
    _record_failure(results, negatives, "native timing bound drift", "routes", failed_timing, ctx)
    failed_status = copy.deepcopy(routes)
    failed_status[2]["status"] = "FAIL"
    _record_failure(results, negatives, "native exit status drift", "routes", failed_status, ctx)

    unit_summary = _phase(ctx, "unit", unit)
    expected_unit = assess._unit_validation(
        ctx["contract"], unit_summary, {"unit.txt": "synthetic"}, ctx["unitManifestSha"])
    try:
        assess._validate_unit_prerequisite(b"{}", expected_unit)
    except assess.ReaderError:
        negatives.append("stale unit prerequisite")
        results.append({"case": "stale unit prerequisite", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted stale unit prerequisite")

    altered_protocol = ctx["contract"]["protocolData"].replace(
        b'"schema": 1', b'"schema": 2', 1)
    try:
        assess._load_contract(protocol_override=altered_protocol)
    except assess.ReaderError:
        negatives.append("protocol pin drift")
        results.append({"case": "protocol pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered protocol pin")

    altered_routes = ctx["contract"]["published"][assess.CENSUS_ROUTES_PATH] + b"\n"
    try:
        assess._load_contract(published_overrides={assess.CENSUS_ROUTES_PATH: altered_routes})
    except assess.ReaderError:
        negatives.append("published input pin drift")
        results.append({"case": "published input pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered published input pin")

    if len(negatives) < 40:
        raise AssertionError("negative canary corpus is unexpectedly small")
    return {
        "schema": 1, "status": "PASS",
        "canaryCount": len(results), "positiveCount": len(positives),
        "negativeCount": len(negatives),
        "fixtureCounts": dict(ctx["protocol"]["unitFixtureCounts"]),
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha((HERE / "check_assessment.py").read_bytes()),
            "protocol.json": assess._sha(ctx["contract"]["protocolData"]),
            "priorAssess.py": assess.PRIOR_ASSESS_SHA,
            "publishedInputs": dict(ctx["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["protocol"]["classificationOwnerSha256"]),
        },
        "sourceHashes": {
            "protocol": assess._sha(ctx["contract"]["protocolData"]),
            "priorReader": assess.PRIOR_ASSESS_SHA,
            "publishedInputs": dict(ctx["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["protocol"]["classificationOwnerSha256"]),
        },
        "caseCounts": {
            "roots": 2, "oldCensusReports": 6, "feasibilityRows": 31,
            "routeReports": 8, "unitSuites": 2, "routeSuites": 1,
        },
        "coverage": [
            "sealed protocol and published-input pins",
            "composed old census boundary and exact report order",
            "31 canonical moves, source/candidate metrics, and stage taxonomy",
            "independent 135-pad reachability and foreign-partition rejection",
            "proof, timing, terminal, prerequisite, schema, and pin failures",
        ],
        "independentOracle": {
            "source": "published seed2 target callback pad/net facts",
            "sha256": ctx["publishedReachabilityHash"],
            "padPairCount": ctx["publishedReachabilityPairCount"],
            "canonicalUtf8Bytes": ctx["publishedReachabilityByteCount"],
        },
        "results": results,
        "notes": [
            "Pure offline canaries; no native feasibility trial was executed by this checker.",
            "Synthetic candidate facts exercise reader schema and proof gates only.",
            "Physical feasibility and native source/provenance comparisons remain native evidence.",
        ],
    }


def main():
    try:
        result = _run_canaries()
        if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
            raise assess.ReaderError(
                "refusing to overwrite existing task artifact: assessment-canary-results.json")
        payload = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        with RESULTS_PATH.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
        print("PASS assessment canaries; cases=" + str(result["canaryCount"]) +
              " rejected=" + str(result["negativeCount"]))
        return 0
    except assess.ReaderError as exc:
        print("FAIL assessment canaries: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
