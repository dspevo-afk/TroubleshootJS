"""Pure synthetic canaries for the bounded routed-slide descent reader."""

import copy
import hashlib
import json
import sys
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"
PUBLISHED_HASH_LITERAL = (
    "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7"
)


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False, separators=(",", ":"),
                               allow_nan=False)


def _digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _manual_reachability(parts):
    """Independent R1 pad/net oracle, separate from assess._reachability_hash_parts."""
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
            payload.extend((left + right +
                            ("1" if left_net == right_net else "0") + "\n").encode("utf-8"))
            pairs += 1
    return hashlib.sha256(payload).hexdigest(), pairs, len(payload)


def _context():
    contract = assess._load_contract()
    prior = assess._load_prior_contract(contract)
    fixture_prior = dict(prior)
    fixture_prior["publishedSourceFacts"] = copy.deepcopy(prior["publishedSourceFacts"])
    target = [callback for callback in
              prior["publishedCensusSummaries"]["2"]["successfulCallbacks"]
              if callback["target"]]
    if len(target) != 1:
        raise AssertionError("sealed source target callback is not unique")
    parts2 = copy.deepcopy(target[0]["parts"])
    # The old packet has no seed5 full pad map.  This is an explicit synthetic
    # source fixture: it preserves the 54/131 shape and derives its own oracle,
    # while the production reader still binds runtime seed5 to its native hash.
    parts5 = [copy.deepcopy(part) for part in parts2
              if part["componentId"] not in ("F1", "JAC")]
    if len(parts5) != 54 or sum(len(part["pads"]) for part in parts5) != 131:
        raise AssertionError("seed5 synthetic structural shape changed")
    for part in parts5:
        if part["eligiblePackage"]:
            part["knownExtraCenterlineContact"] = True
            part["extraCenterlineContactCount"] = 1
            part["directions"] = [
                {"dx": direction["dx"], "dy": direction["dy"],
                 "eligible": False, "reason": "EXTRA_CENTERLINE_CONTACT",
                 "copperDelta": None}
                for direction in part["directions"]
            ]
    synthetic_trace_ids = sorted({incident["traceIndex"]
                                  for part in parts5 for pad in part["pads"]
                                  for incident in pad["incidents"]})
    synthetic_trace_map = {old: new for new, old in enumerate(synthetic_trace_ids)}
    for part in parts5:
        for pad in part["pads"]:
            for incident in pad["incidents"]:
                incident["traceIndex"] = synthetic_trace_map[incident["traceIndex"]]
    seed5_oracle = _manual_reachability(parts5)
    fixture_prior["publishedSourceFacts"]["5"]["source"]["reachabilityHash"] = seed5_oracle[0]
    raw_old = contract["published"][
        "docs/task-evidence/PCB/routed-slide-compaction-probe-r1/routes.txt"]
    old_by_seed = {seed: {} for seed in assess.ROOTS}
    for line in raw_old.decode("utf-8", errors="strict").splitlines():
        for prefix in (assess.WHITESPACE_PREFIX, assess.LEG_CASE_PREFIX):
            if line.startswith(prefix):
                row = assess._parse_json(line[len(prefix):].encode("utf-8"),
                                         "sealed old baseline")
                seed = row.get("seed")
                if seed in old_by_seed:
                    if prefix in old_by_seed[seed]:
                        raise AssertionError("sealed old baseline is duplicated")
                    old_by_seed[seed][prefix] = (row, line)
    if any(set(rows) != {assess.WHITESPACE_PREFIX, assess.LEG_CASE_PREFIX}
           for rows in old_by_seed.values()):
        raise AssertionError("sealed old baseline rows are incomplete")
    oracle = _manual_reachability(parts2)
    if oracle != (PUBLISHED_HASH_LITERAL, 9045, 175409):
        raise AssertionError("independent published seed2 oracle literal changed")
    return {
        "contract": contract, "prior": fixture_prior,
        "protocol": contract["protocol"], "parts": {"5": parts5, "2": parts2},
        "oldBySeed": old_by_seed, "oracle": oracle, "seed5Oracle": seed5_oracle,
    }


def _metrics(source_metrics, **changes):
    result = copy.deepcopy(source_metrics)
    result.update(changes)
    return result


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


def _control(ctx, source, source_metrics, tag):
    metrics = _metrics(
        source_metrics,
        geometrySha256=_digest("control:" + tag + ":" + source["geometryBefore"]),
        generationMetadata=assess.PRIVATE_GENERATION,
    )
    values = {
        "compacted": False, "metrics": metrics,
        "proof": _proof(source, final=True), "sourceImmutable": True,
    }
    return {field: values[field] for field in assess.CONTROL_FIELDS}


def _candidate(ctx, source, parts, move, tag):
    matches = [part for part in parts if part["componentId"] == move["componentId"]]
    if len(matches) != 1:
        raise AssertionError("synthetic move component is not unique")
    affected = len({incident["traceIndex"] for pad in matches[0]["pads"]
                     for incident in pad["incidents"]})
    values = {
        "geometryHash": _digest("candidate:" + tag + ":" + move["componentId"] +
                                ":" + str(move["dx"]) + ":" + str(move["dy"])),
        "componentCount": source["componentCount"], "padCount": source["padCount"],
        "traceCount": source["traceCount"], "affectedTraceCount": affected,
        "movedPadCount": 2,
        "copperLength": source["copperLength"] + move["actualCopperDelta"],
        "area": source["area"],
        "generationMetadata": assess.PHYS.GENERATION_METADATA,
    }
    return {field: values[field] for field in assess.PHYS.CANDIDATE_FIELDS}


def _timing(stage, offset=0):
    values = {
        "constructionMillis": 1 + offset, "placementMillis": 2,
        "geometryMillis": None, "twoLayerMillis": None, "proofMillis": None,
        "totalMillis": 0,
    }
    if stage in ("GEOMETRY", "TWO_LAYER", "COMPLETE"):
        values["geometryMillis"] = 4
    if stage in ("TWO_LAYER", "COMPLETE"):
        values["twoLayerMillis"] = 5
    if stage == "COMPLETE":
        values["proofMillis"] = 6
    values["totalMillis"] = sum(value for value in values.values()
                                 if type(value) is int)
    return {field: values[field] for field in assess.PHYS.ROW_TIMING_FIELDS}


def _checks(stage):
    stage_values = {
        "PLACEMENT": (False, False, False),
        "GEOMETRY": (True, False, False),
        "TWO_LAYER": (True, True, False),
        "COMPLETE": (True, True, True),
    }[stage]
    values = {
        "traceEndpointOnlyVerified": True,
        "unrelatedGeometryVerified": True,
        "sourceImmutable": True,
        "placementValidated": stage_values[0],
        "geometryValidated": stage_values[1],
        "twoLayerValidated": stage_values[2],
    }
    return {field: values[field] for field in assess.PHYS.CHECK_FIELDS}


def _feasibility_row(ctx, source, parts, move, valid, tag, stage="PLACEMENT"):
    values = {
        "move": move,
        "candidate": _candidate(ctx, source, parts, move, tag),
        "disposition": assess.PHYS.DISPOSITION_VALIDATED if valid else assess.PHYS.DISPOSITION_REJECTED,
        "stage": "COMPLETE" if valid else stage,
        "reason": None if valid else ctx["prior"]["physicalProtocol"]["placementReasons"][0],
        "message": None if valid else ctx["prior"]["physicalProtocol"]["placementReasons"][0],
        "checks": _checks("COMPLETE" if valid else stage),
        "proof": _proof(source, final=False) if valid else None,
        "timing": _timing("COMPLETE" if valid else stage),
    }
    feasibility = {field: values[field] for field in assess.PHYS.ROW_FIELDS}
    return {"feasibility": feasibility, "compaction": None}


def _structural(source, parts):
    return {
        "geometryHashBefore": source["geometryBefore"],
        "geometryHashAfter": source["geometryAfter"],
        "parts": copy.deepcopy(parts),
    }


def _summary(ctx, seed, mode="noimprove", max_evaluated=None):
    protocol = ctx["protocol"]
    facts = ctx["prior"]["publishedSourceFacts"][seed]
    initial_source = copy.deepcopy(facts["source"])
    initial_metrics = copy.deepcopy(facts["metrics"])
    parts = ctx["parts"][seed]
    max_eval = protocol["limits"]["maxEvaluatedMoves"] if max_evaluated is None else max_evaluated
    accepted_target = {"mixed": 1, "firststrict": 1, "compactedfalse": 1,
                       "stepcap": 4}.get(mode, 0)
    current_source = copy.deepcopy(initial_source)
    current_metrics = copy.deepcopy(initial_metrics)
    steps = []
    accepted = evaluated = validated = rejected = compactions = timing_total = 0
    while True:
        source_for_step = copy.deepcopy(current_source)
        metrics_for_step = copy.deepcopy(current_metrics)
        structural = _structural(source_for_step, parts)
        _parts, moves, _facts = assess._validate_structural(
            structural, source_for_step, ctx["prior"], seed)
        control = _control(ctx, source_for_step, metrics_for_step,
                           seed + ":" + str(len(steps) + 1))
        rows = []
        accepted_index = None
        if accepted < accepted_target and moves and evaluated < max_eval:
            move = moves[0]
            rows.append(_feasibility_row(
                ctx, source_for_step, parts, move, True,
                seed + ":accepted:" + str(accepted + 1)))
            compact_metrics = _metrics(
                metrics_for_step,
                area=metrics_for_step["area"] - 100,
                copperLength=metrics_for_step["copperLength"] - 10,
                geometrySha256=_digest(seed + ":private:" + str(accepted + 1)),
                generationMetadata=assess.PRIVATE_GENERATION,
            )
            rows[0]["compaction"] = {
                "compacted": mode != "compactedfalse",
                "metrics": compact_metrics,
                "proof": _proof(source_for_step, final=True),
                "sourceImmutable": True,
                "strictImprovement": True,
            }
            accepted_index = 0
            accepted += 1
            evaluated += 1
            validated += 1
            compactions += 1
            timing_total += rows[0]["feasibility"]["timing"]["totalMillis"]
            current_source = copy.deepcopy(source_for_step)
            current_source["area"] = compact_metrics["area"]
            current_source["copperLength"] = compact_metrics["copperLength"]
            current_source["geometryBefore"] = compact_metrics["geometrySha256"]
            current_source["geometryAfter"] = compact_metrics["geometrySha256"]
            current_metrics = compact_metrics
        else:
            remaining = max_eval - evaluated
            for index, move in enumerate(moves[:max(0, remaining)]):
                row = _feasibility_row(
                    ctx, source_for_step, parts, move, False,
                    seed + ":rejected:" + str(index))
                rows.append(row)
                rejected += 1
                evaluated += 1
                timing_total += row["feasibility"]["timing"]["totalMillis"]
        step = {
            "index": len(steps) + 1, "source": source_for_step,
            "sourceMetrics": metrics_for_step, "control": control,
            "structural": structural, "rows": rows,
            "acceptedRowIndex": accepted_index,
        }
        steps.append(step)
        if accepted >= accepted_target and accepted_target > 0:
            if mode == "stepcap" or accepted >= protocol["limits"]["maxAcceptedSteps"]:
                status = "STEP_CAP"
                break
            if len(steps) >= 2:
                status = "NO_STRICT_IMPROVEMENT"
                break
        if not moves:
            status = "NO_STRUCTURAL_MOVES"
            break
        if evaluated >= max_eval:
            status = "EVALUATION_CAP"
            break
        if accepted_index is None:
            status = "NO_STRICT_IMPROVEMENT"
            break
        if len(steps) >= 4:
            status = "STEP_CAP"
            break
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
        "source": copy.deepcopy(initial_source),
        "sourceMetrics": copy.deepcopy(initial_metrics),
        "initialControl": copy.deepcopy(steps[0]["control"]), "steps": steps,
        "finalMetrics": copy.deepcopy(current_metrics), "aggregate": aggregate,
        "elapsedMillis": max(1, timing_total + 1),
    }


def _route_fixture(ctx, mode="mixed", max_evaluated=None):
    summaries = {
        "5": _summary(ctx, "5", "empty", max_evaluated),
        "2": _summary(ctx, "2", mode, max_evaluated),
    }
    reports = []
    for seed in assess.ROOTS:
        for prefix in (assess.WHITESPACE_PREFIX, assess.LEG_CASE_PREFIX):
            row, line = ctx["oldBySeed"][seed][prefix]
            reports.append((prefix, copy.deepcopy(row), line))
        row = summaries[seed]
        reports.append((assess.SUMMARY_PREFIX, row, _line(assess.SUMMARY_PREFIX, row)))
    return reports


def _replace_summary(reports, seed, mutator):
    result = copy.deepcopy(reports)
    for index, (prefix, row, line) in enumerate(result):
        if prefix == assess.SUMMARY_PREFIX and row.get("seed") == seed:
            mutator(row)
            result[index] = (prefix, row, _line(prefix, row))
            return result
    raise AssertionError("summary seed not found")


def _route_check(ctx, reports, protocol=None):
    contract = {"protocol": ctx["protocol"] if protocol is None else protocol}
    return assess._assess_routes(reports, contract, ctx["prior"])


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
    suites, _sha = assess._load_suite_manifest("unit", ctx["protocol"])
    passes = [
        "PASS: " + suite[1] + "assertions=1 fixtures=" +
        str(ctx["protocol"]["unitFixtureCounts"][suite[0]]) +
        " evaluator=" + ctx["protocol"]["unitEvaluators"][suite[0]]
        for suite in suites
    ]
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = "\n".join(passes + [terminal, assess.CLEANUP_LINE]) + "\n"
    receipt = "\n".join(passes) + "\n"
    timing = {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 1,
        "suiteCount": len(suites), "phase": "unit",
        "scope": ctx["protocol"]["timingScope"],
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    return text, receipt, timing, suites


def _run_canaries():
    ctx = _context()
    positive = []
    negative = []
    results = []
    valid = _route_fixture(ctx, "mixed")
    noimprove = _route_fixture(ctx, "noimprove")
    stepcap = _route_fixture(ctx, "stepcap")
    compacted_false = _route_fixture(ctx, "compactedfalse")
    _record(results, positive, negative, "valid empty seed5 and mixed seed2",
            lambda: _route_check(ctx, valid), True)
    _record(results, positive, negative, "valid all-rejected no-improvement",
            lambda: _route_check(ctx, noimprove), True)
    _record(results, positive, negative, "valid four-step accepted cap",
            lambda: _route_check(ctx, stepcap), True)
    _record(results, positive, negative, "valid compacted false invocation",
            lambda: _route_check(ctx, compacted_false), True)
    compacted_true = copy.deepcopy(valid)
    for prefix, row, line in compacted_true:
        if prefix == assess.SUMMARY_PREFIX:
            row["initialControl"]["compacted"] = True
            for step in row["steps"]:
                step["control"]["compacted"] = True
    _record(results, positive, negative, "valid compacted true control owner flag",
            lambda: _route_check(ctx, compacted_true), True)
    eval_protocol = copy.deepcopy(ctx["protocol"])
    eval_protocol["limits"] = {"maxAcceptedSteps": 4, "maxEvaluatedMoves": 1}
    eval_ctx = dict(ctx)
    eval_ctx["protocol"] = eval_protocol
    eval_fixture = _route_fixture(eval_ctx, "noimprove", max_evaluated=1)
    _record(results, positive, negative, "valid evaluation cap fixture",
            lambda: _route_check(ctx, eval_fixture, eval_protocol), True)
    oracle = ctx["oracle"]
    if oracle != (PUBLISHED_HASH_LITERAL, 9045, 175409):
        raise AssertionError("independent pad/net oracle changed")
    positive.append("independent all-pad net oracle")
    results.append({"case": "independent all-pad net oracle", "result": "PASS",
                    "sha256": oracle[0], "padPairCount": oracle[1],
                    "canonicalUtf8Bytes": oracle[2]})
    unit = _unit_fixture(ctx)
    _record(results, positive, negative, "valid unit phase",
            lambda: assess.check_phase(*unit, "unit"), True)

    cases = [
        ("summary status drift", _replace_summary(valid, "2",
            lambda row: row.__setitem__("status", "STEP_CAP"))),
        ("summary version float", _replace_summary(valid, "2",
            lambda row: row.__setitem__("version", 15.0))),
        ("summary selected route drift", _replace_summary(valid, "2",
            lambda row: row.__setitem__("selectedRoute", "P05_ONE_FACE"))),
        ("summary limits drift", _replace_summary(valid, "2",
            lambda row: row["limits"].__setitem__("maxAcceptedSteps", 5))),
        ("summary aggregate drift", _replace_summary(valid, "2",
            lambda row: row["aggregate"].__setitem__("compactions", 0))),
        ("summary elapsed float", _replace_summary(valid, "2",
            lambda row: row.__setitem__("elapsedMillis", 1.5))),
        ("summary elapsed below rows", _replace_summary(valid, "2",
            lambda row: row.__setitem__("elapsedMillis", 0))),
        ("step index zero based", _replace_summary(valid, "2",
            lambda row: row["steps"][0].__setitem__("index", 0))),
        ("accepted row index drift", _replace_summary(valid, "2",
            lambda row: row["steps"][0].__setitem__("acceptedRowIndex", None))),
        ("source graph drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("liveGraphAfter", "0" * 64))),
        ("source geometry drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("geometryAfter", "0" * 64))),
        ("source area drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("area", 1))),
        ("source copper drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("copperLength", 1))),
        ("source trace count drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("traceCount", 144))),
        ("source route count drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("sourceRouteSourceCount", 144))),
        ("source pair count drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("padPairCount", 9044))),
        ("source reachability drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("reachabilityHash", "e" * 64))),
        ("source provenance drift", _replace_summary(valid, "2",
            lambda row: row["source"].__setitem__("traceProvenanceHash", "f" * 64))),
        ("source metrics generation drift", _replace_summary(valid, "2",
            lambda row: row["sourceMetrics"].__setitem__("generationMetadata", assess.PRIVATE_GENERATION))),
        ("source metric area drift", _replace_summary(valid, "2",
            lambda row: row["sourceMetrics"].__setitem__("area", 1))),
        ("initial control compacted", _replace_summary(valid, "2",
            lambda row: row["initialControl"].__setitem__("compacted", True))),
        ("control proof flag false", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["control"]["proof"].__setitem__("routingValidated", False))),
        ("control identity drift", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["control"]["metrics"].__setitem__("labelIdentitySha256", "1" * 64))),
        ("row order drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"].__setitem__(1, row["steps"][0]["rows"][2]))),
        ("row duplicate", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"].__setitem__(1, copy.deepcopy(row["steps"][0]["rows"][0])))),
        ("row missing", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"].pop())),
        ("move component drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["move"].__setitem__("componentId", "FOREIGN"))),
        ("move delta drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["move"].__setitem__("actualCopperDelta", 1))),
        ("candidate geometry source", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("geometryHash", row["steps"][0]["source"]["geometryBefore"]))),
        ("candidate affected traces drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("affectedTraceCount", 0))),
        ("candidate generation drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["candidate"].__setitem__("generationMetadata", assess.SOURCE_GENERATION))),
        ("rejected row has compaction", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0].__setitem__("compaction", {"x": 1}))),
        ("validated row lacks compaction", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0].__setitem__("compaction", None))),
        ("old feasibility disposition alias", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"].__setitem__("disposition", "VALIDATED_COMPLETE"))),
        ("embedded proof count drift", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["proof"].__setitem__("padPairCount", 9044))),
        ("embedded proof flag false", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["proof"].__setitem__("traceProvenanceVerified", False))),
        ("final proof count drift", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["proof"].__setitem__("padPairCount", 9044))),
        ("final proof hash drift", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["proof"].__setitem__("candidateReachabilityHash", "d" * 64))),
        ("final proof identity flag false", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["proof"].__setitem__("stableIdentityVerified", False))),
        ("compaction source immutable false", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"].__setitem__("sourceImmutable", False))),
        ("strict improvement mismatch", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"].__setitem__("strictImprovement", False))),
        ("zero compaction area", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["metrics"].__setitem__("area", 0))),
        ("zero compaction copper", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["metrics"].__setitem__("copperLength", 0))),
        ("protected void exceeds area", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["metrics"].__setitem__("largestProtectedEmptyRectangleArea", 99999999))),
        ("compaction geometry source", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["metrics"].__setitem__("geometrySha256", row["steps"][0]["source"]["geometryBefore"]))),
        ("float final proof count", _replace_summary(valid, "2",
            lambda row: row["steps"][0]["rows"][0]["compaction"]["proof"].__setitem__("padPairCount", 9045.0))),
        ("float physical timing", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("placementMillis", 2.5))),
        ("negative physical timing", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("constructionMillis", -1))),
        ("timing total below floor", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 0))),
        ("timing total over case bound", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("totalMillis", 60001))),
        ("timing unrun stage nonnull", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"][0]["feasibility"]["timing"].__setitem__("geometryMillis", 4))),
        ("structural geometry drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["structural"].__setitem__("geometryHashAfter", "a" * 64))),
        ("structural part reorder", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["structural"]["parts"].__setitem__(0, row["steps"][0]["structural"]["parts"][1]))),
        ("structural pad net drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["structural"]["parts"][0]["pads"][0].__setitem__("netId", "FOREIGN_NET"))),
        ("structural trace index out of range", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["structural"]["parts"][0]["pads"][0]["incidents"][0].__setitem__("traceIndex", row["source"]["traceCount"]))),
        ("structural direction drift", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["structural"]["parts"][0]["directions"][0].__setitem__("eligible", True))),
        ("later source transition drift", _replace_summary(valid, "2",
            lambda row: row["steps"][1]["source"].__setitem__("area", row["steps"][1]["source"]["area"] + 1))),
        ("later source generation drift", _replace_summary(valid, "2",
            lambda row: row["steps"][1]["sourceMetrics"].__setitem__("generationMetadata", assess.SOURCE_GENERATION))),
        ("later step extra after terminal", _replace_summary(noimprove, "2",
            lambda row: row["steps"].append(copy.deepcopy(row["steps"][0])))),
        ("evaluation cap overrun", _replace_summary(noimprove, "2",
            lambda row: row["steps"][0]["rows"].extend(copy.deepcopy(row["steps"][0]["rows"])))),
    ]
    for name, fixture in cases:
        _record(results, positive, negative, name,
                lambda fixture=fixture: _route_check(ctx, fixture), False)

    old_drift = copy.deepcopy(valid)
    old_drift[0] = (old_drift[0][0], dict(old_drift[0][1]), old_drift[0][2])
    old_drift[0][1]["sampledCells"] += 1
    _record(results, positive, negative, "old whitespace mutation",
            lambda: _route_check(ctx, old_drift), False)
    order_drift = copy.deepcopy(valid)
    order_drift[1], order_drift[2] = order_drift[2], order_drift[1]
    _record(results, positive, negative, "report order drift",
            lambda: _route_check(ctx, order_drift), False)
    duplicate = copy.deepcopy(valid)
    duplicate.insert(5, copy.deepcopy(duplicate[5]))
    _record(results, positive, negative, "duplicate summary report",
            lambda: _route_check(ctx, duplicate), False)
    text, receipt, timing, suites = unit
    terminal = assess.TERMINAL_TEMPLATE.format(count=4)
    after_pass = text.replace(terminal + "\n", terminal + "\nPASS: " + suites[0][1] +
                              "assertions=1 fixtures=6 evaluator=" +
                              ctx["protocol"]["unitEvaluators"][suites[0][0]] + "\n")
    _record(results, positive, negative, "suite PASS after terminal",
            lambda: assess.check_phase(after_pass, receipt, timing, suites, "unit"), False)
    _record(results, positive, negative, "native failure marker",
            lambda: assess.check_phase("FAIL: synthetic native failure\n" + text,
                                       receipt, timing, suites, "unit"), False)
    bad_timing = dict(timing); bad_timing["elapsedSeconds"] = 36001
    _record(results, positive, negative, "native timing bound",
            lambda: assess.check_phase(text, receipt, bad_timing, suites, "unit"), False)
    bad_exit = dict(timing); bad_exit["exitCode"] = 1
    _record(results, positive, negative, "native exit status",
            lambda: assess.check_phase(text, receipt, bad_exit, suites, "unit"), False)
    bad_suite = list(suites); bad_suite.reverse()
    _record(results, positive, negative, "unit suite order drift",
            lambda: assess.check_phase(text, receipt, timing, bad_suite, "unit"), False)
    expected_unit = assess._unit_validation(
        ctx["contract"], assess.check_phase(*unit, "unit"), {"unit.txt": "synthetic"},
        ctx["protocol"]["suiteManifestSha256"]["unit-suites.json"])
    _record(results, positive, negative, "stale unit prerequisite",
            lambda: assess._validate_unit_prerequisite(b"{}", expected_unit), False)
    altered_protocol = ctx["contract"]["protocolData"].replace(b'"schema": 1', b'"schema": 2', 1)
    _record(results, positive, negative, "protocol pin drift",
            lambda: assess._load_contract(protocol_override=altered_protocol), False)
    altered_prior = ctx["contract"]["published"][assess.OLD_READER_REL] + b"\n"
    _record(results, positive, negative, "prior reader pin drift",
            lambda: assess._load_contract(
                published_overrides={assess.OLD_READER_REL: altered_prior}), False)
    _record(results, positive, negative, "duplicate JSON object key",
            lambda: assess._parse_json(b'{"duplicate":1,"duplicate":2}',
                                       "synthetic duplicate-key object"), False)
    if len(negative) < 40:
        raise AssertionError("negative canary corpus is unexpectedly small")
    protocol_data = ctx["contract"]["protocolData"]
    return {
        "schema": 1, "status": "PASS", "canaryCount": len(results),
        "positiveCount": len(positive), "negativeCount": len(negative),
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
        "caseCounts": {
            "roots": 2, "oldBaselineReports": 4, "descentSummaries": 2,
            "unitSuites": 4, "routeSuites": 1,
        },
        "coverage": [
            "sealed protocol, prior reader, published-input, owner, and suite pins",
            "independent 9045-pair/175409-byte seed2 pad-net oracle",
            "generic current structural parts, directions, affected trace IDs, and chaining",
            "first strict, no-improvement, no-move, step/evaluation caps, and compacted flag",
            "source/control/metrics/identity/proof/comparator/counter/timing gates",
            "old baseline filter, report order, terminal PASS, timing, exit, and prerequisite gates",
        ],
        "independentOracle": {
            "source": "sealed seed2 target callback pad/net facts",
            "sha256": PUBLISHED_HASH_LITERAL, "padPairCount": 9045,
            "canonicalUtf8Bytes": 175409,
        },
        "results": results,
        "notes": [
            "Pure offline canaries only; no native, GWT, browser, or runtime descent was executed.",
            "Seed5 valid canaries use an explicit synthetic 54-part/131-pad fixture because the sealed prior packet has no seed5 pad map; runtime seed5 must derive its own native hash.",
            "The pinned final protocol has no physicalRowContract, summaryStatuses, or expectedMoves top-level key; adapter binding, terminal statuses, and move order remain reader-owned against the sealed prior reader and current census output.",
            "Offline validation checks endpoint incidents and independent pad/net reachability; complete native trace paths and compactor ownership remain native proof responsibilities.",
        ],
    }


def main():
    try:
        result = _run_canaries()
        if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
            raise assess.ReaderError(
                "refusing to overwrite existing task artifact: assessment-canary-results.json")
        payload = (json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) +
                   "\n")
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
