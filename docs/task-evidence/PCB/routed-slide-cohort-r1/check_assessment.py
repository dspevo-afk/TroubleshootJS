"""Pure independent fixtures and canaries for the informed14 cohort reader."""

import copy
import hashlib
import json
import sys
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"
PUBLISHED_REACHABILITY = "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"


def _digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _manual_reachability(parts):
    """Independent R1 all-pad/net oracle for synthetic and published fixtures."""
    pads = [(pad["padId"], pad["netId"])
            for part in parts for pad in part["pads"]]
    if len(pads) != len({item[0] for item in pads}):
        raise AssertionError("synthetic pad IDs are not unique")
    pads.sort(key=lambda item: item[0])
    payload = bytearray(b"R1\n")
    pairs = 0
    for index, (left_id, left_net) in enumerate(pads):
        left = str(len(left_id.encode("utf-16-le")) // 2) + ":" + left_id
        for right_id, right_net in pads[index + 1:]:
            right = str(len(right_id.encode("utf-16-le")) // 2) + ":" + right_id
            payload.extend((left + right + ("1" if left_net == right_net else "0") + "\n").encode("utf-8"))
            pairs += 1
    return hashlib.sha256(payload).hexdigest(), pairs, len(payload)


def _part_directions(eligible):
    if not eligible:
        return [
            {"dx": dx, "dy": dy, "eligible": False,
             "reason": "PART_INELIGIBLE", "copperDelta": None}
            for dx, dy in assess.LEG.DIRECTIONS
        ]
    return [
        {"dx": -10, "dy": 0, "eligible": True, "reason": "ELIGIBLE", "copperDelta": 0},
        {"dx": 10, "dy": 0, "eligible": True, "reason": "ELIGIBLE", "copperDelta": 0},
        {"dx": 0, "dy": -10, "eligible": False, "reason": "NON_PARALLEL_SHIFT", "copperDelta": None},
        {"dx": 0, "dy": 10, "eligible": False, "reason": "NON_PARALLEL_SHIFT", "copperDelta": None},
    ]


def _build_parts(seed, part_count, pad_count):
    if part_count < 2 or pad_count < 2:
        raise AssertionError("synthetic baseline shape is too small")
    parts = []
    extra = pad_count - 2
    ineligible_count = part_count - 1
    base, remainder = divmod(extra, ineligible_count)
    trace_ids = (0, 1)
    first_id = "C000"
    net0, net1 = "SYN_" + seed + "_0", "SYN_" + seed + "_1"
    parts.append({
        "componentId": first_id, "packageId": "AXIAL_RESISTOR", "eligiblePackage": True,
        "eligibilityReason": "ELIGIBLE", "knownExtraCenterlineContact": False,
        "extraCenterlineContactCount": 0,
        "pads": [
            {"padId": first_id + ".P00", "netId": net0, "x": 100, "y": 100,
             "incidents": [{"traceIndex": trace_ids[0], "traceSourceId": "synthetic/" + seed + "/0",
                             "traceNetId": net0, "layer": "TOP", "width": 11,
                             "endRole": "START", "endpointX": 100, "endpointY": 100,
                             "neighborX": 120, "neighborY": 100}]},
            {"padId": first_id + ".P01", "netId": net1, "x": 140, "y": 100,
             "incidents": [{"traceIndex": trace_ids[1], "traceSourceId": "synthetic/" + seed + "/1",
                             "traceNetId": net1, "layer": "TOP", "width": 11,
                             "endRole": "END", "endpointX": 140, "endpointY": 100,
                             "neighborX": 120, "neighborY": 100}]},
        ],
        "directions": _part_directions(True),
    })
    for index in range(1, part_count):
        count = base + (1 if index <= remainder else 0)
        component_id = "C" + str(index).zfill(3)
        pads = [
            {"padId": component_id + ".P" + str(pad_index).zfill(2),
             "netId": "SYN_" + seed + "_EMPTY_" + str(index) + "_" + str(pad_index),
             "x": 1000 + index * 20 + pad_index * 5, "y": 1000 + index * 10,
             "incidents": []}
            for pad_index in range(count)
        ]
        parts.append({
            "componentId": component_id, "packageId": "SYNTHETIC_CONNECTOR",
            "eligiblePackage": False, "eligibilityReason": "UNSUPPORTED_PACKAGE",
            "knownExtraCenterlineContact": False, "extraCenterlineContactCount": 0,
            "pads": pads, "directions": _part_directions(False),
        })
    return parts


def _proof(source, final=False):
    fields = assess.PROOF_FIELDS if final else assess.FEASIBILITY_PROOF_FIELDS
    values = {}
    for field in fields:
        if field == "padPairCount":
            values[field] = source["padPairCount"]
        elif field.endswith("RouteSourceCount"):
            values[field] = source["sourceRouteSourceCount"]
        elif "ReachabilityHash" in field:
            values[field] = source["reachabilityHash"]
        elif "TraceProvenanceHash" in field:
            values[field] = source["traceProvenanceHash"]
        else:
            values[field] = True
    return {field: values[field] for field in fields}


def _timing(stage, offset=0):
    values = {
        "constructionMillis": 1 + offset, "placementMillis": 2,
        "geometryMillis": None, "twoLayerMillis": None, "proofMillis": None,
        "totalMillis": 0,
    }
    if stage in ("GEOMETRY", "TWO_LAYER", "COMPLETE"):
        values["geometryMillis"] = 3
    if stage in ("TWO_LAYER", "COMPLETE"):
        values["twoLayerMillis"] = 4
    if stage == "COMPLETE":
        values["proofMillis"] = 5
    values["totalMillis"] = sum(value for value in values.values() if type(value) is int)
    return {field: values[field] for field in assess.PHYS.ROW_TIMING_FIELDS}


def _checks(stage):
    expected = {
        "PLACEMENT": (False, False, False), "GEOMETRY": (True, False, False),
        "TWO_LAYER": (True, True, False), "COMPLETE": (True, True, True),
    }[stage]
    values = {
        "traceEndpointOnlyVerified": True, "unrelatedGeometryVerified": True,
        "sourceImmutable": True, "placementValidated": expected[0],
        "geometryValidated": expected[1], "twoLayerValidated": expected[2],
    }
    return {field: values[field] for field in assess.PHYS.CHECK_FIELDS}


def _candidate(source, parts, move, tag):
    target = [part for part in parts if part["componentId"] == move["componentId"]]
    if len(target) != 1:
        raise AssertionError("synthetic move component is not unique")
    affected = len({incident["traceIndex"] for pad in target[0]["pads"]
                     for incident in pad["incidents"]})
    values = {
        "geometryHash": _digest("candidate:" + tag),
        "componentCount": source["componentCount"], "padCount": source["padCount"],
        "traceCount": source["traceCount"], "affectedTraceCount": affected,
        "movedPadCount": 2, "copperLength": source["copperLength"] + move["actualCopperDelta"],
        "area": source["area"], "generationMetadata": assess.PHYS.GENERATION_METADATA,
    }
    return {field: values[field] for field in assess.PHYS.CANDIDATE_FIELDS}


def _feasibility(source, parts, move, valid, tag, physical_protocol, stage="PLACEMENT"):
    actual_stage = "COMPLETE" if valid else stage
    reason = None if valid else physical_protocol["placementReasons"][0]
    values = {
        "move": copy.deepcopy(move), "candidate": _candidate(source, parts, move, tag),
        "disposition": assess.PHYS.DISPOSITION_VALIDATED if valid else assess.PHYS.DISPOSITION_REJECTED,
        "stage": actual_stage, "reason": reason, "message": reason,
        "checks": _checks(actual_stage), "proof": _proof(source, final=False) if valid else None,
        "timing": _timing(actual_stage),
    }
    if valid:
        values["message"] = None
    return {field: values[field] for field in assess.PHYS.ROW_FIELDS}


def _control(source, metrics, tag, compacted=False):
    control_metrics = copy.deepcopy(metrics)
    control_metrics["geometrySha256"] = source["geometryBefore"]
    control_metrics["generationMetadata"] = assess.PRIVATE_GENERATION
    values = {
        "compacted": compacted, "metrics": control_metrics,
        "proof": _proof(source, final=True), "sourceImmutable": True,
    }
    return {field: values[field] for field in assess.CONTROL_FIELDS}


def _compact(source, metrics, tag, compacted=False):
    result = copy.deepcopy(metrics)
    result["area"] -= 100
    result["geometrySha256"] = _digest("compact:" + tag)
    result["generationMetadata"] = assess.PRIVATE_GENERATION
    values = {
        "compacted": compacted, "metrics": result, "proof": _proof(source, final=True),
        "sourceImmutable": True, "strictImprovement": True,
    }
    return {field: values[field] for field in assess.COMPACTION_FIELDS}, result


def _synthetic_summary(ctx, seed, mode="noimprove", max_evaluated=None,
                       control_compacted=False, compaction_compacted=False):
    protocol = ctx["contract"]["protocol"]
    source_initial = copy.deepcopy(ctx["sources"][seed])
    metrics_initial = copy.deepcopy(ctx["metrics"][seed])
    parts = ctx["parts"][seed]
    max_eval = protocol["limits"]["maxEvaluatedMoves"] if max_evaluated is None else max_evaluated
    target = {"firststrict": 1, "stepcap": 4}.get(mode, 0)
    current_source = copy.deepcopy(source_initial)
    current_metrics = copy.deepcopy(metrics_initial)
    physical_protocol = ctx["prior_bundle"]["prior"]["physicalProtocol"]
    steps = []
    accepted = evaluated = validated = rejected = compactions = timing_total = 0
    while True:
        source = copy.deepcopy(current_source)
        metrics = copy.deepcopy(current_metrics)
        structural = {
            "geometryHashBefore": source["geometryBefore"],
            "geometryHashAfter": source["geometryAfter"], "parts": copy.deepcopy(parts),
        }
        _parts, moves, _facts = assess.PRIOR._validate_structural(
            structural, source, ctx["root_prior"][seed], seed)
        control = _control(source, metrics, seed + ":" + str(len(steps) + 1), control_compacted)
        rows = []
        accepted_index = None
        if accepted < target and moves and evaluated < max_eval:
            move = moves[0]
            feasibility = _feasibility(source, parts, move, True,
                                       seed + ":accepted:" + str(accepted + 1),
                                       physical_protocol)
            compaction, next_metrics = _compact(
                source, metrics, seed + ":" + str(accepted + 1), compaction_compacted)
            rows.append({"feasibility": feasibility, "compaction": compaction})
            accepted_index = 0
            accepted += 1; evaluated += 1; validated += 1; compactions += 1
            timing_total += feasibility["timing"]["totalMillis"]
            current_source["area"] = next_metrics["area"]
            current_source["geometryBefore"] = next_metrics["geometrySha256"]
            current_source["geometryAfter"] = next_metrics["geometrySha256"]
            current_metrics = next_metrics
        else:
            remaining = max_eval - evaluated
            for index, move in enumerate(moves[:max(0, remaining)]):
                feasibility = _feasibility(source, parts, move, False,
                                            seed + ":rejected:" + str(index),
                                            physical_protocol)
                rows.append({"feasibility": feasibility, "compaction": None})
                evaluated += 1; rejected += 1
                timing_total += feasibility["timing"]["totalMillis"]
        steps.append({
            "index": len(steps) + 1, "source": source, "sourceMetrics": metrics,
            "control": control, "structural": structural, "rows": rows,
            "acceptedRowIndex": accepted_index,
        })
        if accepted >= protocol["limits"]["maxAcceptedSteps"]:
            status = "STEP_CAP"; break
        if evaluated >= max_eval:
            status = "EVALUATION_CAP"; break
        if not moves:
            status = "NO_STRUCTURAL_MOVES"; break
        if accepted_index is None:
            status = "NO_STRICT_IMPROVEMENT"; break
    aggregate = {
        "steps": len(steps), "acceptedSteps": accepted,
        "evaluatedMoves": evaluated, "validatedMoves": validated,
        "rejectedMoves": rejected, "compactions": compactions,
    }
    return {
        "seed": seed, "label": "seed" + seed, "version": assess.VERSION,
        "phase": protocol["phase"], "status": status,
        "selectedAttempt": protocol["selectedBaselines"][seed]["selectedAttempt"],
        "selectedRoute": assess.SELECTED_ROUTE, "newRouteRequests": 0,
        "selectedMutation": False, "limits": copy.deepcopy(protocol["limits"]),
        "source": copy.deepcopy(source_initial), "sourceMetrics": copy.deepcopy(metrics_initial),
        "initialControl": copy.deepcopy(steps[0]["control"]), "steps": steps,
        "finalMetrics": copy.deepcopy(current_metrics), "aggregate": aggregate,
        "elapsedMillis": max(1, timing_total + 1),
    }


def _context():
    contract = assess._load_contract()
    prior_bundle = assess._load_prior_contract(contract)
    baselines = assess._load_baselines(contract)
    parts = {}; sources = {}; metrics = {}; root_prior = {}
    for seed in assess.ROOTS:
        if seed in ("5", "2"):
            parent = prior_bundle["summaries"][seed]
            parts[seed] = copy.deepcopy(parent["steps"][0]["structural"]["parts"])
            sources[seed] = copy.deepcopy(parent["source"])
            metrics[seed] = copy.deepcopy(parent["sourceMetrics"])
        else:
            baseline = contract["protocol"]["selectedBaselines"][seed]
            parts[seed] = _build_parts(seed, baseline["parts"], baseline["pads"])
            reachability, pairs, _bytes = _manual_reachability(parts[seed])
            sources[seed] = {
                "liveGraphBefore": baseline["liveBoardGraphSha256"],
                "liveGraphAfter": baseline["liveBoardGraphSha256"],
                "geometryBefore": baseline["geometrySha256"],
                "geometryAfter": baseline["geometrySha256"],
                "copperLength": baseline["copperManhattanLength"], "area": baseline["area"],
                "componentCount": baseline["parts"], "padCount": baseline["pads"],
                "traceCount": 2, "traceProvenanceHash": _digest("trace:" + seed),
                "sourceRouteSourceCount": 2, "reachabilityHash": reachability,
                "padPairCount": pairs,
            }
            metrics[seed] = {
                "area": baseline["area"], "copperLength": baseline["copperManhattanLength"],
                "vias": baseline["vias"],
                "largestProtectedEmptyRectangleArea": baseline["largestProtectedEmptyRectangleArea"],
                "geometrySha256": baseline["geometrySha256"],
                "stableIdentitySha256": _digest("stable:" + seed),
                "labelIdentitySha256": _digest("label:" + seed),
                "generationMetadata": assess.SOURCE_GENERATION,
            }
        root_prior[seed] = assess._root_prior(prior_bundle, seed, sources[seed], metrics[seed])
    # Independent check of the published two-root oracle used by the pinned parent.
    parent_parts = parts["2"]
    oracle = _manual_reachability(parent_parts)
    if oracle != (PUBLISHED_REACHABILITY, 9045, 175409):
        raise AssertionError("published seed2 pad/net oracle changed")
    return {
        "contract": contract, "prior_bundle": prior_bundle, "baselines": baselines,
        "parts": parts, "sources": sources, "metrics": metrics, "root_prior": root_prior,
        "oracle": oracle,
    }


def _route_fixture(ctx, synthetic_mode=None, control_compacted=False,
                   compaction_compacted=False, max_evaluated=None,
                   bundle=None):
    summaries = {}
    synthetic_mode = {} if synthetic_mode is None else synthetic_mode
    for seed in assess.ROOTS:
        if seed in ("5", "2") and seed not in synthetic_mode:
            summaries[seed] = copy.deepcopy(ctx["prior_bundle"]["summaries"][seed])
        else:
            summaries[seed] = _synthetic_summary(
                ctx, seed, synthetic_mode.get(seed, "noimprove"), max_evaluated,
                control_compacted, compaction_compacted)
    reports = []
    for seed in assess.ROOTS:
        for prefix in (assess.WHITESPACE_PREFIX, assess.LEG_PREFIX):
            row = copy.deepcopy(ctx["baselines"]["whitespace" if prefix == assess.WHITESPACE_PREFIX else "selected"][seed])
            reports.append((prefix, row, _line(prefix, row)))
        row = summaries[seed]
        reports.append((assess.SUMMARY_PREFIX, row, _line(assess.SUMMARY_PREFIX, row)))
    return reports


def _route_check(ctx, reports, bundle=None, contract=None):
    effective_contract = ctx["contract"] if contract is None else {
        "protocol": contract,
        "published": ctx["contract"]["published"],
    }
    return assess._assess_routes(
        reports, effective_contract,
        ctx["prior_bundle"] if bundle is None else bundle)


def _replace_summary(reports, seed, mutator):
    result = copy.deepcopy(reports)
    for index, (prefix, row, _line_value) in enumerate(result):
        if prefix == assess.SUMMARY_PREFIX and row.get("seed") == seed:
            mutator(row)
            result[index] = (prefix, row, _line(prefix, row))
            return result
    raise AssertionError("summary seed not found")


def _replace_old(reports, prefix, seed, mutator):
    result = copy.deepcopy(reports)
    for index, (actual_prefix, row, _line_value) in enumerate(result):
        if actual_prefix == prefix and row.get("seed") == seed:
            mutator(row)
            result[index] = (actual_prefix, row, _line(actual_prefix, row))
            return result
    raise AssertionError("old report not found")


def _first_validated_row(summary):
    for step in summary["steps"]:
        for row in step["rows"]:
            if row["feasibility"]["proof"] is not None:
                return row
    raise AssertionError("synthetic summary has no validated row")


def _record(results, positives, negatives, name, thunk, accepted):
    try:
        thunk()
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


def _unit_fixture(ctx):
    suites, _sha_value = assess._load_suite_manifest("unit", ctx["contract"]["protocol"])
    passes = [
        "PASS: " + suite[1] + "assertions=1 fixtures=" +
        str(ctx["contract"]["protocol"]["unitFixtureCounts"][suite[0]]) +
        " evaluator=" + ctx["contract"]["protocol"]["unitEvaluators"][suite[0]]
        for suite in suites
    ]
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = "\n".join(passes + [terminal, assess.CLEANUP_LINE]) + "\n"
    receipt = "\n".join(passes) + "\n"
    timing = {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 1,
        "suiteCount": len(suites), "phase": "unit", "scope": ctx["contract"]["protocol"]["timingScope"],
        "terminalCleanupIncluded": True, "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    return text, receipt, timing, suites


def _routes_fixture(ctx, reports=None):
    protocol = ctx["contract"]["protocol"]
    suites, _sha_value = assess._load_suite_manifest("routes", protocol)
    rows = _route_fixture(ctx) if reports is None else reports
    report_lines = [item[2] for item in rows]
    passes = ["PASS: " + suite[1] + protocol["routePassDetail"] for suite in suites]
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = "\n".join(report_lines + passes + [terminal, assess.CLEANUP_LINE]) + "\n"
    receipt = "\n".join(report_lines + passes) + "\n"
    timing = {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 1,
        "suiteCount": len(suites), "phase": "routes",
        "scope": protocol["timingScope"], "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    return text, receipt, timing, suites


def _matched_time_positive(reports):
    result = copy.deepcopy(reports)
    for index, (prefix, row, _line_value) in enumerate(result):
        if prefix != assess.SUMMARY_PREFIX or row.get("seed") not in ("5", "2"):
            continue
        row["elapsedMillis"] += 10
        for step in row["steps"]:
            for item in step["rows"]:
                timing = item["feasibility"]["timing"]
                for key in ("constructionMillis", "placementMillis", "geometryMillis",
                            "twoLayerMillis", "proofMillis", "totalMillis"):
                    if timing[key] is not None:
                        timing[key] += 1
                timing["totalMillis"] += 2
        result[index] = (prefix, row, _line(prefix, row))
    return result


def _run_canaries():
    ctx = _context()
    results = []; positives = []; negatives = []
    base = _route_fixture(ctx)
    _record(results, positives, negatives, "valid complete fourteen-root cohort",
            lambda: _route_check(ctx, base), True)
    synthetic_first = {seed: "firststrict" for seed in assess.ROOTS if seed != "2"}
    synthetic_first["5"] = "firststrict"
    synthetic_bundle = dict(ctx["prior_bundle"])
    synthetic_bundle["summaries"] = dict(ctx["prior_bundle"]["summaries"])
    synthetic_bundle["summaries"]["5"] = _synthetic_summary(ctx, "5", "firststrict")
    first = _route_fixture(ctx, synthetic_first, bundle=synthetic_bundle)
    _record(results, positives, negatives, "valid independent first-strict cohort fixture",
            lambda: _route_check(ctx, first, synthetic_bundle), True)
    stepcap_modes = {seed: "stepcap" for seed in assess.ROOTS if seed != "2"}
    stepcap_bundle = dict(ctx["prior_bundle"])
    stepcap_bundle["summaries"] = dict(ctx["prior_bundle"]["summaries"])
    stepcap_bundle["summaries"]["5"] = _synthetic_summary(ctx, "5", "stepcap")
    stepcap = _route_fixture(ctx, stepcap_modes, bundle=stepcap_bundle)
    _record(results, positives, negatives, "valid four-step cap cohort fixture",
            lambda: _route_check(ctx, stepcap, stepcap_bundle), True)
    compacted_true = _route_fixture(ctx, control_compacted=True, compaction_compacted=True)
    _record(results, positives, negatives, "valid control and compaction true flags",
            lambda: _route_check(ctx, compacted_true), True)
    _record(results, positives, negatives, "valid compacted false and matched timing",
            lambda: _route_check(ctx, _matched_time_positive(base)), True)
    eval_modes = {seed: "noimprove" for seed in assess.ROOTS}
    eval_bundle = dict(ctx["prior_bundle"]); eval_bundle["summaries"] = dict(ctx["prior_bundle"]["summaries"])
    eval_protocol = copy.deepcopy(ctx["contract"]["protocol"])
    eval_protocol["limits"] = {"maxAcceptedSteps": 4, "maxEvaluatedMoves": 1}
    eval_protocol["matchedRoots"] = []
    eval_reports = _route_fixture(ctx, eval_modes, max_evaluated=1, bundle=eval_bundle)
    for index, (prefix, row, _line_value) in enumerate(eval_reports):
        if prefix == assess.SUMMARY_PREFIX:
            row["limits"] = copy.deepcopy(eval_protocol["limits"])
            eval_reports[index] = (prefix, row, _line(prefix, row))
    _record(results, positives, negatives, "valid evaluation cap fixture",
            lambda: _route_check(ctx, eval_reports, eval_bundle, eval_protocol), True)
    oracle = ctx["oracle"]
    if oracle != (PUBLISHED_REACHABILITY, 9045, 175409):
        raise AssertionError("independent published oracle changed")
    positives.append("independent published seed2 oracle")
    results.append({"case": "independent published seed2 oracle", "result": "PASS",
                    "sha256": oracle[0], "padPairCount": oracle[1], "canonicalUtf8Bytes": oracle[2]})
    unit = _unit_fixture(ctx)
    _record(results, positives, negatives, "valid five-suite unit phase",
            lambda: assess.check_phase(*unit, "unit"), True)
    route_phase = _routes_fixture(ctx, base)
    _record(results, positives, negatives, "valid seven-suite route phase",
            lambda: assess.check_phase(*route_phase, "routes"), True)

    bad = [
        ("summary status drift", _replace_summary(base, "-1017", lambda r: r.__setitem__("status", "STEP_CAP"))),
        ("summary version float", _replace_summary(base, "-1017", lambda r: r.__setitem__("version", 15.0))),
        ("summary selected route drift", _replace_summary(base, "-1017", lambda r: r.__setitem__("selectedRoute", "P05_ONE_FACE"))),
        ("summary limits drift", _replace_summary(base, "-1017", lambda r: r["limits"].__setitem__("maxAcceptedSteps", 5))),
        ("summary aggregate drift", _replace_summary(base, "-1017", lambda r: r["aggregate"].__setitem__("compactions", 1))),
        ("summary elapsed float", _replace_summary(base, "-1017", lambda r: r.__setitem__("elapsedMillis", 1.5))),
        ("summary selected attempt drift", _replace_summary(base, "-1017", lambda r: r.__setitem__("selectedAttempt", 0))),
        ("summary noncanonical signed seed", _replace_summary(base, "-1017", lambda r: r.__setitem__("seed", "-01017"))),
        ("source graph drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("liveGraphAfter", "0" * 64))),
        ("source geometry drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("geometryAfter", "0" * 64))),
        ("source area drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("area", 1))),
        ("source copper drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("copperLength", 1))),
        ("source pad count drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("padCount", 1))),
        ("source trace count drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("traceCount", 1))),
        ("source route count drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("sourceRouteSourceCount", 1))),
        ("source pair count drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("padPairCount", 1))),
        ("source reachability drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("reachabilityHash", "e" * 64))),
        ("source provenance drift", _replace_summary(base, "-1017", lambda r: r["source"].__setitem__("traceProvenanceHash", "f" * 64))),
        ("source metrics generation drift", _replace_summary(base, "-1017", lambda r: r["sourceMetrics"].__setitem__("generationMetadata", assess.PRIVATE_GENERATION))),
        ("source metric area drift", _replace_summary(base, "-1017", lambda r: r["sourceMetrics"].__setitem__("area", 1))),
        ("initial control compacted type", _replace_summary(base, "-1017", lambda r: r["initialControl"].__setitem__("compacted", 1))),
        ("control proof flag false", _replace_summary(base, "-1017", lambda r: r["steps"][0]["control"]["proof"].__setitem__("routingValidated", False))),
        ("control identity drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["control"]["metrics"].__setitem__("labelIdentitySha256", "1" * 64))),
        ("control immutable false", _replace_summary(base, "-1017", lambda r: r["steps"][0]["control"].__setitem__("sourceImmutable", False))),
        ("row order drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"].reverse())),
        ("row duplicate", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"].__setitem__(1, copy.deepcopy(r["steps"][0]["rows"][0])))),
        ("row missing", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"].pop())),
        ("move component drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["move"].__setitem__("componentId", "FOREIGN"))),
        ("move delta drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["move"].__setitem__("actualCopperDelta", 1))),
        ("candidate geometry source", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("geometryHash", r["steps"][0]["source"]["geometryBefore"]))),
        ("candidate affected trace drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("affectedTraceCount", 0))),
        ("candidate generation drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("generationMetadata", assess.PRIVATE_GENERATION))),
        ("rejected row has compaction", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0].__setitem__("compaction", {"x": 1}))),
        ("validated row lacks compaction", _replace_summary(base, "5", lambda r: _first_validated_row(r).__setitem__("compaction", None))),
        ("old disposition alias", _replace_summary(base, "5", lambda r: r["steps"][0]["rows"][0]["feasibility"].__setitem__("disposition", "VALIDATED_COMPLETE"))),
        ("embedded proof count drift", _replace_summary(base, "5", lambda r: _first_validated_row(r)["feasibility"]["proof"].__setitem__("padPairCount", 1))),
        ("embedded proof flag false", _replace_summary(base, "5", lambda r: _first_validated_row(r)["feasibility"]["proof"].__setitem__("traceProvenanceVerified", False))),
        ("final proof count drift", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["proof"].__setitem__("padPairCount", 1))),
        ("final proof hash drift", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["proof"].__setitem__("candidateReachabilityHash", "d" * 64))),
        ("final proof identity false", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["proof"].__setitem__("stableIdentityVerified", False))),
        ("compaction immutable false", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"].__setitem__("sourceImmutable", False))),
        ("strict improvement mismatch", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"].__setitem__("strictImprovement", True))),
        ("zero compaction area", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["metrics"].__setitem__("area", 0))),
        ("zero compaction copper", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["metrics"].__setitem__("copperLength", 0))),
        ("protected void exceeds area", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["metrics"].__setitem__("largestProtectedEmptyRectangleArea", 99999999))),
        ("compaction geometry source", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["metrics"].__setitem__("geometrySha256", r["steps"][0]["source"]["geometryBefore"]))),
        ("float proof count", _replace_summary(base, "5", lambda r: _first_validated_row(r)["compaction"]["proof"].__setitem__("padPairCount", 8515.0))),
        ("float physical timing", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("placementMillis", 2.5))),
        ("negative physical timing", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("constructionMillis", -1))),
        ("timing total below floor", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 0))),
        ("timing total over case bound", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 60001))),
        ("timing unrun stage nonnull", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("geometryMillis", 3))),
        ("structural geometry drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["structural"].__setitem__("geometryHashAfter", "a" * 64))),
        ("structural part reorder", _replace_summary(base, "-1017", lambda r: r["steps"][0]["structural"]["parts"].__setitem__(0, r["steps"][0]["structural"]["parts"][1]))),
        ("structural pad net drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["structural"]["parts"][0]["pads"][0].__setitem__("netId", "FOREIGN_NET"))),
        ("structural direction drift", _replace_summary(base, "-1017", lambda r: r["steps"][0]["structural"]["parts"][0]["directions"][0].__setitem__("eligible", False))),
        ("structural trace index range", _replace_summary(base, "-1017", lambda r: r["steps"][0]["structural"]["parts"][0]["pads"][0]["incidents"][0].__setitem__("traceIndex", r["source"]["traceCount"]))),
        ("later source transition drift", _replace_summary(base, "5", lambda r: r["steps"][1]["source"].__setitem__("area", r["steps"][1]["source"]["area"] + 1))),
        ("later source generation drift", _replace_summary(base, "5", lambda r: r["steps"][1]["sourceMetrics"].__setitem__("generationMetadata", assess.SOURCE_GENERATION))),
        ("extra step after terminal", _replace_summary(base, "-1017", lambda r: r["steps"].append(copy.deepcopy(r["steps"][0])))),
        ("evaluation cap overrun", _replace_summary(base, "-1017", lambda r: r["steps"][0]["rows"].extend(copy.deepcopy(r["steps"][0]["rows"]))),),
        ("matched non-time source drift", _replace_summary(base, "5", lambda r: r["source"]["area"] .__class__ and r["source"].__setitem__("area", r["source"]["area"] + 1))),
        ("matched timing null presence", _replace_summary(base, "5", lambda r: r["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("geometryMillis", 1))),
    ]
    for name, fixture in bad:
        _record(results, positives, negatives, name,
                lambda fixture=fixture: _route_check(ctx, fixture), False)

    missing = copy.deepcopy(base); missing.pop()
    _record(results, positives, negatives, "missing final root report",
            lambda: _route_check(ctx, missing), False)
    extra = copy.deepcopy(base); extra.append(copy.deepcopy(extra[-1]))
    _record(results, positives, negatives, "extra cohort report",
            lambda: _route_check(ctx, extra), False)
    order = copy.deepcopy(base); order[0], order[1] = order[1], order[0]
    _record(results, positives, negatives, "report partition reorder",
            lambda: _route_check(ctx, order), False)
    old_drift = _replace_old(base, assess.LEG_PREFIX, "-1017",
                             lambda r: r.__setitem__("parts", r["parts"] + 1))
    _record(results, positives, negatives, "accepted15 leg baseline drift",
            lambda: _route_check(ctx, old_drift), False)
    ws_drift = _replace_old(base, assess.WHITESPACE_PREFIX, "-1017",
                            lambda r: r.__setitem__("sampledCells", r["sampledCells"] + 1))
    _record(results, positives, negatives, "current15 whitespace drift",
            lambda: _route_check(ctx, ws_drift), False)
    duplicate = copy.deepcopy(base); duplicate.insert(3, copy.deepcopy(duplicate[3]))
    _record(results, positives, negatives, "duplicate root partition",
            lambda: _route_check(ctx, duplicate), False)
    wrong_prefix = copy.deepcopy(base); wrong_prefix[2] = ("PCB_UNKNOWN_CASE ", wrong_prefix[2][1], wrong_prefix[2][2])
    _record(results, positives, negatives, "unknown cohort prefix",
            lambda: _route_check(ctx, wrong_prefix), False)

    unit = _unit_fixture(ctx)
    text, receipt, timing, suites = unit
    terminal = assess.TERMINAL_TEMPLATE.format(count=5)
    after_pass = text.replace(terminal + "\n", terminal + "\nPASS: " + suites[0][1] +
                              "assertions=1 fixtures=6 evaluator=" +
                              ctx["contract"]["protocol"]["unitEvaluators"][suites[0][0]] + "\n")
    _record(results, positives, negatives, "unit PASS after terminal",
            lambda: assess.check_phase(after_pass, receipt, timing, suites, "unit"), False)
    _record(results, positives, negatives, "native failure marker",
            lambda: assess.check_phase("FAIL: synthetic native failure\n" + text, receipt, timing, suites, "unit"), False)
    bad_timing = dict(timing); bad_timing["elapsedSeconds"] = 36001
    _record(results, positives, negatives, "native phase timing bound",
            lambda: assess.check_phase(text, receipt, bad_timing, suites, "unit"), False)
    bad_exit = dict(timing); bad_exit["exitCode"] = 1
    _record(results, positives, negatives, "native phase exit status",
            lambda: assess.check_phase(text, receipt, bad_exit, suites, "unit"), False)
    reversed_suites = list(suites); reversed_suites.reverse()
    _record(results, positives, negatives, "unit suite order drift",
            lambda: assess.check_phase(text, receipt, timing, reversed_suites, "unit"), False)
    route_text, route_receipt, route_timing, route_suites = route_phase
    missing_route_marker = route_text.replace(
        "PASS: " + route_suites[0][1] + ctx["contract"]["protocol"]["routePassDetail"] + "\n", "", 1)
    _record(results, positives, negatives, "route PASS marker missing",
            lambda: assess.check_phase(missing_route_marker, route_receipt,
                                       route_timing, route_suites, "routes"), False)
    route_receipt_drift = route_receipt.replace("PCB_WHITESPACE_CASE ",
                                                "PCB_ROUTED_LEG_CASE ", 1)
    _record(results, positives, negatives, "route receipt report drift",
            lambda: assess.check_phase(route_text, route_receipt_drift,
                                       route_timing, route_suites, "routes"), False)
    first_route_pass = "PASS: " + route_suites[0][1] + ctx["contract"]["protocol"]["routePassDetail"]
    route_report_after_pass = route_text.replace(
        first_route_pass + "\n", first_route_pass + "\n" + base[0][2] + "\n", 1)
    _record(results, positives, negatives, "route report after suite PASS",
            lambda: assess.check_phase(route_report_after_pass, route_receipt,
                                       route_timing, route_suites, "routes"), False)
    _record(results, positives, negatives, "route native failure marker",
            lambda: assess.check_phase("FAIL: synthetic route failure\n" + route_text,
                                       route_receipt, route_timing, route_suites, "routes"), False)
    _record(results, positives, negatives, "noncanonical signed seed helper",
            lambda: assess._canonical_seed("01", "synthetic signed seed"), False)
    expected_unit = assess._unit_validation(
        ctx["contract"], assess.check_phase(*unit, "unit"), {"unit.txt": "synthetic"},
        ctx["contract"]["protocol"]["suiteManifestSha256"]["unit-suites.json"])
    _record(results, positives, negatives, "stale unit prerequisite",
            lambda: assess._validate_unit_prerequisite(b"{}", expected_unit), False)
    altered_protocol = ctx["contract"]["protocolData"].replace(b'"schema": 1', b'"schema": 2', 1)
    _record(results, positives, negatives, "cohort protocol pin drift",
            lambda: assess._load_contract(protocol_override=altered_protocol), False)
    altered_prior = ctx["contract"]["published"][assess.PRIOR_READER_REL] + b"\n"
    _record(results, positives, negatives, "prior reader pin drift",
            lambda: assess._load_contract(published_overrides={assess.PRIOR_READER_REL: altered_prior}), False)
    _record(results, positives, negatives, "duplicate JSON key",
            lambda: assess._parse_json(b'{"duplicate":1,"duplicate":2}', "synthetic duplicate-key object"), False)
    if len(negatives) < 70:
        raise AssertionError("negative cohort canary corpus is unexpectedly small")
    protocol_data = ctx["contract"]["protocolData"]
    return {
        "schema": 1, "status": "PASS", "canaryCount": len(results),
        "positiveCount": len(positives), "negativeCount": len(negatives),
        "fixtureCounts": dict(ctx["contract"]["protocol"]["unitFixtureCounts"]),
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha((HERE / "check_assessment.py").read_bytes()),
            "protocol.json": assess._sha(protocol_data), "priorAssess.py": assess.PRIOR_READER_SHA,
            "publishedInputs": dict(ctx["contract"]["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["contract"]["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["contract"]["protocol"]["classificationOwnerSha256"]),
        },
        "sourceHashes": {
            "protocol": assess._sha(protocol_data), "priorReader": assess.PRIOR_READER_SHA,
            "publishedInputs": dict(ctx["contract"]["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["contract"]["protocol"]["suiteManifestSha256"]),
            "classificationOwners": dict(ctx["contract"]["protocol"]["classificationOwnerSha256"]),
        },
        "caseCounts": {"roots": 14, "oldBaselineReports": 28, "cohortSummaries": 14,
                       "unitSuites": 5, "routeSuites": 7},
        "coverage": [
            "sealed all14 protocol, accepted15 baseline, whitespace, prior reader, owner, and suite pins",
            "independent published seed2 9045-pair/175409-byte pad-net oracle",
            "generic current census parts, directions, trace bounds, affected traces, and source chaining",
            "full14 report order, matched roots5/2 non-time comparison, timing-only variation, and caps",
            "control/compaction flags, metrics, identity, physical/final proof, counters, and termination",
            "seven route PASS markers, terminal cleanup, failure/exit/timing, duplicate keys, and unit prerequisite",
        ],
        "independentOracle": {"source": "sealed parent seed2 target callback pad/net facts",
                             "sha256": PUBLISHED_REACHABILITY, "padPairCount": 9045,
                             "canonicalUtf8Bytes": 175409},
        "results": results,
        "notes": [
            "Pure offline canaries only; native, GWT, browser, solver, and runtime cohort execution were not run.",
            "Positive non-5/2 cases use explicit synthetic complete censuses bound to each sealed baseline shape; this fixture path cannot bypass the pinned CLI contract.",
            "Roots 5 and 2 use the published parent summaries for normal replay; only the six declared timing paths may vary and null stage presence remains exact.",
            "The reader validates endpoint incidents and independent pad/net reachability; complete native trace paths and physical compactor ownership remain native proof responsibilities.",
        ],
    }


def main():
    try:
        result = _run_canaries()
        if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
            raise assess.ReaderError("refusing to overwrite existing task artifact: assessment-canary-results.json")
        payload = (json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
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
