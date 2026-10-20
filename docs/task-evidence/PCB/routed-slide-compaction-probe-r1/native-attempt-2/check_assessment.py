"""Independent in-memory canaries for the selected-frame slide reader."""

import copy
import hashlib
import json
import sys
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"
PUBLISHED_HASH_LITERAL = assess.EXPECTED_REACHABILITY


def _ordered(fields, values):
    return {field: values[field] for field in fields}


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False, separators=(",", ":"),
                               allow_nan=False)


def _manual_reachability(summary):
    """Build the R1 digest directly from the sealed target callback facts."""
    callbacks = summary["successfulCallbacks"]
    targets = [callback for callback in callbacks if callback["target"]]
    if len(targets) != 1:
        raise AssertionError("published oracle must have one target callback")
    pads = []
    for part in targets[0]["parts"]:
        for pad in part["pads"]:
            if type(pad["padId"]) is not str or type(pad["netId"]) is not str:
                raise AssertionError("published pad identity/net is not text")
            pads.append((pad["padId"], pad["netId"]))
    if len(pads) != 135 or len({item[0] for item in pads}) != 135:
        raise AssertionError("published oracle does not contain 135 unique pads")
    pads.sort(key=lambda item: item[0])
    payload = bytearray(b"R1\n")
    pairs = 0
    for index, (left_id, left_net) in enumerate(pads):
        left = str(len(left_id.encode("utf-16-le")) // 2) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right = str(len(right_id.encode("utf-16-le")) // 2) + ":" + right_id
            payload.extend((left + right + ("1" if left_net == right_net else "0") + "\n").encode("utf-8"))
            pairs += 1
    if pairs != 9045 or len(payload) != 175409:
        raise AssertionError("published oracle stream size changed")
    return hashlib.sha256(payload).hexdigest(), pairs, len(payload)


def _context():
    contract = assess._load_contract()
    prior = assess._load_prior_contract(contract)
    unit_suites, unit_sha = assess._load_suite_manifest("unit", contract["protocol"])
    route_suites, route_sha = assess._load_suite_manifest("routes", contract["protocol"])
    raw = prior["oldContract"]["published"][
        "docs/task-evidence/PCB/routed-passive-leg-census-r1/routes.txt"]
    old_by_seed = {seed: {} for seed in assess.ROOTS}
    for line in raw.decode("utf-8", errors="strict").splitlines():
        for prefix in (assess.WHITESPACE_PREFIX, assess.LEG_CASE_PREFIX):
            if line.startswith(prefix):
                row = json.loads(line[len(prefix):])
                seed = row.get("seed")
                if seed in old_by_seed:
                    if prefix in old_by_seed[seed]:
                        raise AssertionError("sealed old report is duplicated")
                    old_by_seed[seed][prefix] = line
    for seed in assess.ROOTS:
        if set(old_by_seed[seed]) != {assess.WHITESPACE_PREFIX, assess.LEG_CASE_PREFIX}:
            raise AssertionError("sealed old report fixture is incomplete")
    digest, pair_count, byte_count = _manual_reachability(
        prior["publishedCensusSummaries"]["2"])
    if digest != PUBLISHED_HASH_LITERAL:
        raise AssertionError("independent published reachability literal changed")
    return {
        "contract": contract, "prior": prior, "protocol": contract["protocol"],
        "unitSuites": unit_suites, "routeSuites": route_suites,
        "unitManifestSha": unit_sha, "routeManifestSha": route_sha,
        "oldBySeed": old_by_seed, "publishedReachabilityHash": digest,
        "publishedReachabilityPairCount": pair_count,
        "publishedReachabilityByteCount": byte_count,
    }


def _timing(ctx, phase):
    return {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
        "suiteCount": 3 if phase == "unit" else 1, "phase": phase,
        "scope": ctx["protocol"]["timingScope"],
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }


def _unit_fixture(ctx):
    lines = []
    for suite, assertions in zip(ctx["unitSuites"], (31, 37, 43)):
        lines.append(
            "PASS: " + suite[1] + "assertions=" + str(assertions) +
            " fixtures=" + str(ctx["protocol"]["unitFixtureCounts"][suite[0]]) +
            " evaluator=" + ctx["protocol"]["unitEvaluators"][suite[0]])
    receipt = "\n".join(lines) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=3)
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "unit"), ctx["unitSuites"]


def _source(ctx, seed):
    baseline = ctx["protocol"]["selectedBaselines"][seed]
    if seed == "2":
        trace_count, provenance, reachability = 145, assess.OLD_SEED2_TRACE_PROVENANCE, PUBLISHED_HASH_LITERAL
    else:
        trace_count, provenance, reachability = 141, "a" * 64, "e" * 64
    values = {
        "liveGraphBefore": baseline["liveBoardGraphSha256"],
        "liveGraphAfter": baseline["liveBoardGraphSha256"],
        "geometryBefore": baseline["geometrySha256"],
        "geometryAfter": baseline["geometrySha256"],
        "copperLength": baseline["copperManhattanLength"], "area": baseline["area"],
        "componentCount": baseline["parts"], "padCount": baseline["pads"],
        "traceCount": trace_count, "traceProvenanceHash": provenance,
        "sourceRouteSourceCount": trace_count, "reachabilityHash": reachability,
        "padPairCount": baseline["pads"] * (baseline["pads"] - 1) // 2,
    }
    return _ordered(ctx["protocol"]["sourceFields"], values)


def _metrics(ctx, seed, source, generation, geometry=None, area=None, copper=None,
             vias=None, void=None, stable="b" * 64, label="c" * 64):
    baseline = ctx["protocol"]["selectedBaselines"][seed]
    values = {
        "area": baseline["area"] if area is None else area,
        "copperLength": baseline["copperManhattanLength"] if copper is None else copper,
        "vias": baseline["vias"] if vias is None else vias,
        "largestProtectedEmptyRectangleArea": baseline["largestProtectedEmptyRectangleArea"] if void is None else void,
        "geometrySha256": source["geometryBefore"] if geometry is None else geometry,
        "stableIdentitySha256": stable,
        "labelIdentitySha256": label,
        "generationMetadata": generation,
    }
    return _ordered(ctx["protocol"]["metricsFields"], values)


def _proof(ctx, source, final=True, reachability=None, flags=True):
    fields = assess.PROOF_FIELDS if final else assess.FEASIBILITY_PROOF_FIELDS
    if reachability is None:
        reachability = source["reachabilityHash"]
    values = {
        "placementValidated": flags, "geometryValidated": flags,
        "twoLayerValidated": flags, "padPairCount": source["padPairCount"],
        "sourceReachabilityHash": reachability, "candidateReachabilityHash": reachability,
        "sourceRouteSourceCount": source["sourceRouteSourceCount"],
        "candidateRouteSourceCount": source["sourceRouteSourceCount"],
        "sourceTraceProvenanceHash": source["traceProvenanceHash"],
        "candidateTraceProvenanceHash": source["traceProvenanceHash"],
        "conductorReachabilityVerified": flags,
        "traceProvenanceVerified": flags,
    }
    if final:
        values.update({
            "routingValidated": flags, "routeQualityValidated": flags,
            "traceClearanceValidated": flags, "stableIdentityVerified": flags,
            "labelIdentityVerified": flags, "sourceImmutable": flags,
        })
    else:
        values.update({"traceEndpointOnlyVerified": flags, "unrelatedGeometryVerified": flags})
    return _ordered(fields, values)


def _control(ctx, seed, source, source_metrics):
    metrics = _metrics(ctx, seed, source, assess.PRIVATE_GENERATION,
                       stable=source_metrics["stableIdentitySha256"],
                       label=source_metrics["labelIdentitySha256"])
    values = {"compacted": False, "metrics": metrics,
              "proof": _proof(ctx, source, final=True), "sourceImmutable": True}
    return _ordered(ctx["protocol"]["controlFields"], values)


def _candidate(ctx, index, move, source):
    summary = ctx["prior"]["publishedCensusSummaries"]["2"]
    part = assess.OLD._part_for_move(summary, move)
    traces = {incident["traceIndex"] for pad in part["pads"] for incident in pad["incidents"]}
    values = {
        "geometryHash": format(index + 1, "064x"), "componentCount": 56,
        "padCount": 135, "traceCount": 145, "affectedTraceCount": len(traces),
        "movedPadCount": 2,
        "copperLength": source["copperLength"] + move["actualCopperDelta"],
        "area": source["area"], "generationMetadata": assess.OLD.GENERATION_METADATA,
    }
    return _ordered(assess.OLD.CANDIDATE_FIELDS, values)


def _checks(stage):
    values = {
        "PLACEMENT": (False, False, False), "GEOMETRY": (True, False, False),
        "TWO_LAYER": (True, True, False), "COMPLETE": (True, True, True),
    }[stage]
    return _ordered(assess.OLD.CHECK_FIELDS, {
        "traceEndpointOnlyVerified": True, "unrelatedGeometryVerified": True,
        "sourceImmutable": True, "placementValidated": values[0],
        "geometryValidated": values[1], "twoLayerValidated": values[2],
    })


def _timing_row(stage):
    values = {
        "constructionMillis": 1, "placementMillis": 2,
        "geometryMillis": 3 if stage in ("GEOMETRY", "TWO_LAYER", "COMPLETE") else None,
        "twoLayerMillis": 4 if stage in ("TWO_LAYER", "COMPLETE") else None,
        "proofMillis": 5 if stage == "COMPLETE" else None,
        "totalMillis": {"PLACEMENT": 3, "GEOMETRY": 6, "TWO_LAYER": 10, "COMPLETE": 15}[stage],
    }
    return _ordered(assess.OLD.ROW_TIMING_FIELDS, values)


def _feasibility_row(ctx, index, source, validated=False, stage="GEOMETRY", reason=None):
    move = ctx["protocol"]["expectedMoves"][index]
    candidate = _candidate(ctx, index, move, source)
    if validated:
        values = {
            "move": move, "candidate": candidate,
            "disposition": assess.OLD.DISPOSITION_VALIDATED, "stage": "COMPLETE",
            "reason": None, "message": None, "checks": _checks("COMPLETE"),
            "proof": _proof(ctx, source, final=False), "timing": _timing_row("COMPLETE"),
        }
    else:
        reason = "TRACE_COMPONENT_COURTYARD" if reason is None else reason
        old_protocol = ctx["prior"]["oldProtocol"]
        if reason in old_protocol["geometryPrefixReasons"]:
            message = old_protocol["geometryPrefixReasons"][reason] + "synthetic " + move["componentId"]
        elif reason in old_protocol.get("qualityReasons", {}):
            message = old_protocol["qualityReasons"][reason] + "synthetic " + move["componentId"]
        else:
            message = "synthetic two-layer rejection"
        values = {
            "move": move, "candidate": candidate,
            "disposition": assess.OLD.DISPOSITION_REJECTED, "stage": stage,
            "reason": reason, "message": message, "checks": _checks(stage),
            "proof": None, "timing": _timing_row(stage),
        }
    return _ordered(assess.OLD.ROW_FIELDS, values)


def _nonordered_complete_timing(row):
    timing = row["rows"][1]["feasibility"]["timing"]
    timing.update({
        "constructionMillis": 7, "placementMillis": 3, "geometryMillis": 11,
        "twoLayerMillis": 1, "proofMillis": 5, "totalMillis": 27,
    })
    row["elapsedMillis"] += 12


def _placement_message_mismatch(row):
    feasibility = row["rows"][0]["feasibility"]
    feasibility.update({
        "stage": "PLACEMENT", "reason": "ENVELOPE_ACCESS_OR_DOMAIN",
        "message": "mismatched placement message",
        "checks": _checks("PLACEMENT"), "timing": _timing_row("PLACEMENT"),
    })


def _bare_geometry_prefix(row, prefix):
    feasibility = row["rows"][0]["feasibility"]
    feasibility["message"] = prefix


def _unknown_rejection_reason(row):
    feasibility = row["rows"][0]["feasibility"]
    feasibility.update({"reason": "UNKNOWN_CATALOG_REASON", "message": "UNKNOWN_CATALOG_REASON"})


def _compacted_false(row):
    row["rows"][1]["compaction"]["compacted"] = False


def _compaction(ctx, seed, source, source_metrics, control_metrics, index, strict=False):
    baseline = ctx["protocol"]["selectedBaselines"][seed]
    metrics = _metrics(
        ctx, seed, source, assess.PRIVATE_GENERATION,
        geometry=format(index + 100, "064x"),
        area=min(source_metrics["area"], control_metrics["area"]) - (1 if strict else 0),
        copper=min(source_metrics["copperLength"], control_metrics["copperLength"]),
        vias=min(source_metrics["vias"], control_metrics["vias"]),
        void=min(source_metrics["largestProtectedEmptyRectangleArea"], control_metrics["largestProtectedEmptyRectangleArea"]),
        stable=source_metrics["stableIdentitySha256"], label=source_metrics["labelIdentitySha256"],
    )
    # For seed 2 the independent net oracle is literal; seed 5 has no offline pad map.
    proof = _proof(ctx, source, final=True)
    values = {"compacted": True, "metrics": metrics, "proof": proof,
              "sourceImmutable": True, "strictImprovement": strict}
    return _ordered(ctx["protocol"]["compactionFields"], values)


def _summary(ctx, seed, mixed=False):
    source = _source(ctx, seed)
    source_metrics = _metrics(ctx, seed, source, assess.SOURCE_GENERATION)
    control = _control(ctx, seed, source, source_metrics)
    control_metrics = control["metrics"]
    if seed == "5":
        values = {
            "seed": seed, "label": "seed5", "version": assess.VERSION,
            "phase": ctx["protocol"]["phase"], "status": "CONTROL_ONLY",
            "selectedAttempt": 5, "selectedRoute": assess.SELECTED_ROUTE,
            "newRouteRequests": 0, "selectedMutation": False, "source": source,
            "sourceMetrics": source_metrics, "control": control, "rows": [],
            "aggregate": {"structuralDirections": 0, "validatedMoves": 0,
                           "rejectedMoves": 0, "compactions": 0,
                           "areaReductions": 0, "strictImprovements": 0},
            "elapsedMillis": 10,
        }
        return _ordered(ctx["protocol"]["summaryFields"], values)
    rows = []
    for index in range(31):
        valid = mixed and index == 1
        feasibility = _feasibility_row(ctx, index, source, valid)
        compaction = _compaction(ctx, seed, source, source_metrics, control_metrics, index, strict=valid)
        rows.append(_ordered(ctx["protocol"]["rowFields"], {
            "feasibility": feasibility, "compaction": compaction if valid else None,
        }))
    aggregate = {
        "structuralDirections": 31, "validatedMoves": 1 if mixed else 0,
        "rejectedMoves": 30 if mixed else 31, "compactions": 1 if mixed else 0,
        "areaReductions": 1 if mixed else 0, "strictImprovements": 1 if mixed else 0,
    }
    values = {
        "seed": seed, "label": "seed2", "version": assess.VERSION,
        "phase": ctx["protocol"]["phase"], "status": "PROBE_COMPLETE",
        "selectedAttempt": 2, "selectedRoute": assess.SELECTED_ROUTE,
        "newRouteRequests": 0, "selectedMutation": False, "source": source,
        "sourceMetrics": source_metrics, "control": control, "rows": rows,
        "aggregate": aggregate,
        "elapsedMillis": sum(row["feasibility"]["timing"]["totalMillis"] for row in rows),
    }
    return _ordered(ctx["protocol"]["summaryFields"], values)


def _route_fixture(ctx, mixed=False):
    lines = []
    for seed in assess.ROOTS:
        lines.extend((ctx["oldBySeed"][seed][assess.WHITESPACE_PREFIX],
                      ctx["oldBySeed"][seed][assess.LEG_CASE_PREFIX]))
        lines.append(_line(ctx["protocol"]["summaryPrefix"], _summary(ctx, seed, mixed)))
    marker = "PASS: " + ctx["routeSuites"][0][1] + ctx["protocol"]["routePassDetail"]
    receipt = "\n".join(lines + [marker]) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=1)
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "routes"), ctx["routeSuites"]


def _phase(ctx, phase, fixture):
    return assess._phase_summary(fixture[0], fixture[1], fixture[2], fixture[3],
                                 phase, ctx["protocol"], ctx["prior"])


def _rewrite_report(fixture, prefix, which, mutation, receipt=True):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines) if line.startswith(prefix)]
        if which >= len(positions):
            raise AssertionError("canary report row is unavailable")
        position = positions[which]
        row = json.loads(lines[position][len(prefix):])
        mutation(row)
        lines[position] = _line(prefix, row)
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child) if receipt else child, copy.deepcopy(timing), copy.deepcopy(suites)


def _append_json_key(fixture, prefix, which, fragment):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines) if line.startswith(prefix)]
        position = positions[which]
        lines[position] = lines[position][:-1] + fragment + "}"
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _swap_reports(fixture, first, second):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
        lines[positions[first]], lines[positions[second]] = lines[positions[second]], lines[positions[first]]
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _move_report_after_pass(fixture):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        report_positions = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
        report = lines.pop(report_positions[-1])
        marker = "PASS: " + suites[0][1] + assess.PROTOCOL_ROUTE_DETAIL
        pass_position = lines.index(marker)
        lines.insert(pass_position + 1, report)
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _record(results, positives, negatives, name, phase, fixture, ctx, accepted=False):
    try:
        _phase(ctx, phase, fixture)
    except assess.ReaderError:
        if accepted:
            raise
        negatives.append(name)
        results.append({"case": name, "result": "REJECTED"})
        return
    if not accepted:
        raise AssertionError("reader accepted rejected canary: " + name)
    positives.append(name)
    results.append({"case": name, "result": "PASS"})


def _run_canaries():
    ctx = _context()
    unit = _unit_fixture(ctx)
    rejected = _route_fixture(ctx)
    mixed = _route_fixture(ctx, mixed=True)
    results, positives, negatives = [], [], []
    _record(results, positives, negatives, "valid unit suites", "unit", unit, ctx, True)
    _record(results, positives, negatives, "valid all-rejected control/route", "routes", rejected, ctx, True)
    _record(results, positives, negatives, "valid mixed compaction row", "routes", mixed, ctx, True)
    _record(results, positives, negatives, "valid independent nonordered stage durations", "routes",
            _rewrite_report(mixed, ctx["protocol"]["summaryPrefix"], 1,
                            _nonordered_complete_timing), ctx, True)
    _record(results, positives, negatives, "valid unchanged compactor result flag", "routes",
            _rewrite_report(mixed, ctx["protocol"]["summaryPrefix"], 1,
                            _compacted_false), ctx, True)
    oracle = _manual_reachability(ctx["prior"]["publishedCensusSummaries"]["2"])
    if oracle != (PUBLISHED_HASH_LITERAL, 9045, 175409):
        raise AssertionError("independent published oracle literal changed")
    positives.append("independent published pad-net oracle")
    results.append({"case": "independent published pad-net oracle", "result": "PASS",
                    "sha256": oracle[0], "padPairCount": oracle[1],
                    "canonicalUtf8Bytes": oracle[2]})
    prefix = ctx["protocol"]["summaryPrefix"]
    mixed_summary = next(
        json.loads(line[len(prefix):]) for line in mixed[0].splitlines()
        if line.startswith(prefix) and json.loads(line[len(prefix):])["seed"] == "2"
    )
    validated_compaction = copy.deepcopy(mixed_summary["rows"][1]["compaction"])
    # Schema and phase ordering negatives.
    cases = [
        ("summary selected route drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("selectedRoute", "P05_ONE_FACE"))),
        ("summary selected attempt drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("selectedAttempt", 1))),
        ("summary version float", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("version", 15.0))),
        ("summary construction route request drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("newRouteRequests", 1))),
        ("summary selected mutation drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("selectedMutation", True))),
        ("summary aggregate drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["aggregate"].__setitem__("strictImprovements", 0))),
        ("summary elapsed below rows", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("elapsedMillis", 0))),
        ("source graph drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("liveGraphAfter", "0" * 64))),
        ("source geometry drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("geometryAfter", "0" * 64))),
        ("source area drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("area", 9178091))),
        ("source copper drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("copperLength", 51859))),
        ("source trace count drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("traceCount", 144))),
        ("source route count drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("sourceRouteSourceCount", 144))),
        ("source pair count drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("padPairCount", 9044))),
        ("source reachability drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("reachabilityHash", "e" * 64))),
        ("source provenance drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["source"].__setitem__("traceProvenanceHash", "f" * 64))),
        ("source metrics area drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["sourceMetrics"].__setitem__("area", 1))),
        ("source metrics generation drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["sourceMetrics"].__setitem__("generationMetadata", assess.PRIVATE_GENERATION))),
        ("control source immutable false", _rewrite_report(mixed, prefix, 1,
            lambda row: row["control"].__setitem__("sourceImmutable", False))),
        ("control proof final flag false", _rewrite_report(mixed, prefix, 1,
            lambda row: row["control"]["proof"].__setitem__("routingValidated", False))),
        ("control proof uses feasibility schema", _rewrite_report(mixed, prefix, 1,
            lambda row: row["control"].__setitem__("proof", _proof(ctx, _source(ctx, "2"), final=False)))),
        ("row order drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"].__setitem__(1, row["rows"][2]))),
        ("row missing", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"].pop())),
        ("row duplicate", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"].append(copy.deepcopy(row["rows"][-1])))),
        ("rejected row has compaction", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0].__setitem__("compaction", copy.deepcopy(validated_compaction)))),
        ("validated row lacks compaction", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1].__setitem__("compaction", None))),
        ("candidate geometry source", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["candidate"].__setitem__(
                "geometryHash", row["source"]["geometryBefore"]))),
        ("candidate copper drift", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["candidate"].__setitem__("copperLength", 1))),
        ("candidate private metadata drift", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["candidate"].__setitem__(
                "generationMetadata", assess.SOURCE_GENERATION))),
        ("embedded old disposition alias", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["feasibility"].__setitem__("disposition", "VALIDATED_COMPLETE"))),
        ("embedded feasibility proof null", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["feasibility"].__setitem__("proof", None))),
        ("embedded feasibility proof flag false", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["feasibility"]["proof"].__setitem__(
                "conductorReachabilityVerified", False))),
        ("final proof pair count drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["proof"].__setitem__("padPairCount", 9044))),
        ("final proof hash drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["proof"].__setitem__(
                "candidateReachabilityHash", "d" * 64))),
        ("final proof identity flag false", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["proof"].__setitem__(
                "stableIdentityVerified", False))),
        ("compaction source immutable false", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"].__setitem__("sourceImmutable", False))),
        ("strict improvement mismatch", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"].__setitem__("strictImprovement", False))),
        ("area reduction metric drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["metrics"].__setitem__("area", 9178092))),
        ("zero compaction area", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["metrics"].__setitem__("area", 0))),
        ("zero compaction copper", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["metrics"].__setitem__("copperLength", 0))),
        ("protected void exceeds area", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["metrics"].__setitem__(
                "largestProtectedEmptyRectangleArea",
                row["rows"][1]["compaction"]["metrics"]["area"] + 1))),
        ("identity digest drift", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["metrics"].__setitem__(
                "labelIdentitySha256", "1" * 64))),
        ("float final proof count", _rewrite_report(mixed, prefix, 1,
            lambda row: row["rows"][1]["compaction"]["proof"].__setitem__("padPairCount", 9045.0))),
        ("float summary elapsed", _rewrite_report(mixed, prefix, 1,
            lambda row: row.__setitem__("elapsedMillis", 30.5))),
        ("float physical timing", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["timing"].__setitem__("geometryMillis", 3.5))),
        ("timing total below floor", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 1))),
        ("timing total over case bound", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 60001))),
        ("timing field before stage", _rewrite_report(rejected, prefix, 1,
            lambda row: row["rows"][0]["feasibility"]["timing"].__setitem__("twoLayerMillis", 4))),
        ("placement rejection message mismatch", _rewrite_report(rejected, prefix, 1,
            _placement_message_mismatch)),
        ("bare geometry rejection prefix", _rewrite_report(rejected, prefix, 1,
            lambda row: _bare_geometry_prefix(
                row, ctx["prior"]["oldProtocol"]["geometryPrefixReasons"]
                ["TRACE_COMPONENT_COURTYARD"]))),
        ("unknown physical rejection reason", _rewrite_report(rejected, prefix, 1,
            _unknown_rejection_reason)),
        ("old baseline case drift", _rewrite_report(mixed, assess.LEG_CASE_PREFIX, 1,
            lambda row: row.__setitem__("area", 1))),
        ("old whitespace drift", _rewrite_report(mixed, assess.WHITESPACE_PREFIX, 0,
            lambda row: row.__setitem__("sampledCells", 1))),
        ("report order drift", _swap_reports(mixed, 1, 2)),
        ("report after suite PASS", _move_report_after_pass(mixed)),
        ("unknown report prefix", _rewrite_report(mixed, prefix, 0,
            lambda row: row.__setitem__("seed", "2"))),
        ("duplicate summary JSON key", _append_json_key(mixed, prefix, 0, ',"seed":"5"')),
        ("unexpected summary JSON key", _append_json_key(mixed, prefix, 0, ',"unexpected":1')),
    ]
    for name, fixture in cases:
        _record(results, positives, negatives, name, "routes", fixture, ctx, False)

    failure = copy.deepcopy(mixed)
    failure = ("FAIL: synthetic native failure\n" + failure[0],
               "FAIL: synthetic native failure\n" + failure[1], failure[2], failure[3])
    _record(results, positives, negatives, "native failure marker", "routes", failure, ctx, False)
    bad_timing = copy.deepcopy(mixed)
    bad_timing[2]["elapsedSeconds"] = 36001
    _record(results, positives, negatives, "native timing bound", "routes", bad_timing, ctx, False)
    bad_status = copy.deepcopy(mixed)
    bad_status[2]["status"] = "FAIL"
    _record(results, positives, negatives, "native exit status", "routes", bad_status, ctx, False)

    unit_summary = _phase(ctx, "unit", unit)
    expected_unit = assess._unit_validation(ctx["contract"], unit_summary,
                                           {"unit.txt": "synthetic"}, ctx["unitManifestSha"])
    try:
        assess._validate_unit_prerequisite(b"{}", expected_unit)
    except assess.ReaderError:
        negatives.append("stale unit prerequisite")
        results.append({"case": "stale unit prerequisite", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted stale unit prerequisite")
    altered_protocol = ctx["contract"]["protocolData"].replace(b'"schema": 1', b'"schema": 2', 1)
    try:
        assess._load_contract(protocol_override=altered_protocol)
    except assess.ReaderError:
        negatives.append("protocol pin drift")
        results.append({"case": "protocol pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered protocol pin")
    altered = ctx["contract"]["published"][assess.OLD_READER_REL] + b"\n"
    try:
        assess._load_contract(published_overrides={assess.OLD_READER_REL: altered})
    except assess.ReaderError:
        negatives.append("published input pin drift")
        results.append({"case": "published input pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered published input pin")
    if len(negatives) < 40:
        raise AssertionError("negative canary corpus is unexpectedly small")
    protocol_data = ctx["contract"]["protocolData"]
    return {
        "schema": 1, "status": "PASS", "canaryCount": len(results),
        "positiveCount": len(positives), "negativeCount": len(negatives),
        "fixtureCounts": dict(ctx["protocol"]["unitFixtureCounts"]),
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha((HERE / "check_assessment.py").read_bytes()),
            "protocol.json": assess._sha(protocol_data),
            "priorAssess.py": assess.OLD_READER_SHA,
            "publishedInputs": dict(ctx["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["protocol"]["classificationOwnerSha256"]),
        },
        "sourceHashes": {
            "protocol": assess._sha(protocol_data), "priorReader": assess.OLD_READER_SHA,
            "publishedInputs": dict(ctx["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["protocol"]["classificationOwnerSha256"]),
        },
        "caseCounts": {"roots": 2, "oldBaselineReports": 4,
                       "slideRows": 31, "routeReports": 6,
                       "unitSuites": 3, "routeSuites": 1},
        "coverage": [
            "sealed protocol, old reader, published-input, and suite pins",
            "exact baseline case/whitespace order and selected source metrics",
            "embedded old physical row adapter with 31 canonical moves",
            "18-field post-compaction proof, identity, comparator, and aggregate gates",
            "phase terminal, prerequisite, timing, schema, failure, and order rejection",
        ],
        "independentOracle": {
            "source": "published seed2 target callback pad/net facts",
            "sha256": PUBLISHED_HASH_LITERAL, "padPairCount": 9045,
            "canonicalUtf8Bytes": 175409,
        },
        "results": results,
        "notes": [
            "Pure offline canaries; no native slide compaction probe was executed by this checker.",
            "Seed5 pair hashes are schema fixtures only; the reader requires native source/control equality without inventing a 131-pad offline oracle.",
            "Compactor exceptions, unknown classifications, cancellation, and native proof failures remain rejection paths in the route reader.",
        ],
    }


def main():
    try:
        result = _run_canaries()
        if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
            raise assess.ReaderError("refusing to overwrite existing task artifact: assessment-canary-results.json")
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
