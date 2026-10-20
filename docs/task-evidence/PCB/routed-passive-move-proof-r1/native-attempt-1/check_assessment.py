"""In-memory positive and negative canaries for the private-move reader."""

import copy
import hashlib
import json
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"


def _line(prefix, row):
    return prefix + json.dumps(
        row, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _context():
    contract = assess._load_contract()
    prior = assess._load_prior_contract(contract)
    unit_suites, unit_sha = assess._load_suite_manifest(
        "unit", contract["protocol"])
    route_suites, route_sha = assess._load_suite_manifest(
        "routes", contract["protocol"])
    old_reports = assess._parse_report_lines(
        contract["published"][assess.CENSUS_ROUTES_PATH].decode("utf-8"),
        contract["protocol"])
    old_by_seed = {seed: {
        prefix: next(item for item in old_reports
                     if item[0] == prefix and item[1]["seed"] == seed)
        for prefix in (assess.WHITESPACE_PREFIX, assess.SUMMARY_PREFIX,
                        assess.CASE_PREFIX)
    } for seed in assess.ROOTS}
    context = {
        "contract": contract,
        "prior": prior,
        "protocol": contract["protocol"],
        "unitSuites": unit_suites,
        "routeSuites": route_suites,
        "unitManifestSha": unit_sha,
        "routeManifestSha": route_sha,
        "oldBySeed": old_by_seed,
    }
    published_hash, published_pairs, published_bytes = _manual_reachability(
        prior["publishedCensusSummaries"]["2"])
    context["publishedReachabilityHash"] = published_hash
    context["publishedReachabilityPairCount"] = published_pairs
    context["publishedReachabilityByteCount"] = published_bytes
    old_only = [item for item in old_reports]
    assessed = assess.PRIOR._assess_routes(
        old_only, prior["selected"], prior["whitespace"], prior["protocol"])
    if assessed["status"] != "PASS":
        raise AssertionError("sealed old census fixture did not pass its prior reader")
    return context


def _timing(ctx, phase):
    return {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
        "suiteCount": 2 if phase == "unit" else 1, "phase": phase,
        "scope": ctx["protocol"]["timingScope"],
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }


def _unit_fixture(ctx):
    suites = ctx["unitSuites"]
    protocol = ctx["protocol"]
    markers = []
    assertion_counts = (40, 24)
    for suite, assertions in zip(suites, assertion_counts):
        markers.append(
            "PASS: " + suite[1] + "assertions=" + str(assertions) +
            " fixtures=" + str(protocol["unitFixtureCounts"][suite[0]]) +
            " evaluator=" + protocol["unitEvaluators"][suite[0]])
    receipt = "\n".join(markers) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "unit"), suites


def _ordered(fields, values):
    return {field: values[field] for field in fields}


def _proof_row(ctx, seed):
    protocol = ctx["protocol"]
    source_rows = ctx["oldBySeed"][seed]
    summary = source_rows[assess.SUMMARY_PREFIX][1]
    case = source_rows[assess.CASE_PREFIX][1]
    fields = protocol["proofFields"]
    common = {
        "seed": seed, "label": "seed" + seed, "version": assess.VERSION,
        "phase": protocol["phase"], "status": protocol["expectedStatus"][seed],
        "constructionCount": 0 if seed == "5" else 1,
        "newRouteRequests": 0, "selectedMutation": False,
    }
    if seed == "5":
        common.update({
            "move": None, "source": None, "candidate": None,
            "proof": None, "physicalFeasibility": "NOT_EVALUATED",
        })
        return _ordered(fields, common)

    callback = next(item for item in summary["successfulCallbacks"] if item["target"])
    candidate_hash = "0" * 64
    provenance_hash = "1" * 64
    move = assess._derive_move(summary, protocol)
    source = {
        "liveGraphBefore": case["liveBoardGraphSha256"],
        "liveGraphAfter": case["liveBoardGraphSha256"],
        "geometryBefore": callback["geometryHashBefore"],
        "geometryAfter": callback["geometryHashAfter"],
        "copperLength": protocol["expectedSource"]["copperLength"],
        "area": protocol["expectedSource"]["area"],
    }
    candidate = {
        "geometryHash": candidate_hash,
        "componentCount": protocol["expectedCandidate"]["componentCount"],
        "padCount": protocol["expectedCandidate"]["padCount"],
        "traceCount": protocol["expectedCandidate"]["traceCount"],
        "affectedTraceCount": protocol["expectedCandidate"]["affectedTraceCount"],
        "movedPadCount": protocol["expectedCandidate"]["movedPadCount"],
        "copperLength": protocol["expectedCandidate"]["copperLength"],
        "area": protocol["expectedCandidate"]["area"],
        "generationMetadata": protocol["expectedCandidate"]["generationMetadata"],
    }
    reachability = ctx["publishedReachabilityHash"]
    proof = {
        "placementValidated": True, "geometryValidated": True,
        "twoLayerValidated": True,
        "padPairCount": protocol["expectedPadPairCount"],
        "sourceReachabilityHash": reachability,
        "candidateReachabilityHash": reachability,
        "sourceRouteSourceCount": 145,
        "candidateRouteSourceCount": 145,
        "sourceTraceProvenanceHash": provenance_hash,
        "candidateTraceProvenanceHash": provenance_hash,
        "traceEndpointOnlyVerified": True,
        "unrelatedGeometryVerified": True,
        "conductorReachabilityVerified": True,
        "traceProvenanceVerified": True,
    }
    common.update({
        "move": _ordered(protocol["moveFields"], move),
        "source": _ordered(protocol["sourceFields"], source),
        "candidate": _ordered(protocol["candidateFields"], candidate),
        "proof": _ordered(protocol["validationFields"], proof),
        "physicalFeasibility": "VALIDATED_PRIVATE_CANDIDATE",
    })
    return _ordered(fields, common)


def _route_fixture(ctx):
    protocol = ctx["protocol"]
    body = []
    for seed in assess.ROOTS:
        rows = ctx["oldBySeed"][seed]
        for prefix in (assess.WHITESPACE_PREFIX, assess.SUMMARY_PREFIX,
                       assess.CASE_PREFIX):
            body.append(rows[prefix][2])
        body.append(_line(protocol["proofPrefix"], _proof_row(ctx, seed)))
    marker = "PASS: " + ctx["routeSuites"][0][1] + protocol["routePassDetail"]
    receipt = "\n".join(body + [marker]) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=1)
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _timing(ctx, "routes"), ctx["routeSuites"]


def _phase(ctx, phase, fixture):
    return assess._phase_summary(
        fixture[0], fixture[1], fixture[2], fixture[3], phase,
        ctx["protocol"], ctx["prior"],
        ctx["prior"]["selected"], ctx["prior"]["whitespace"])


def _change_report(fixture, prefix, which, mutation, receipt=True):
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

    return rewrite(text), rewrite(child) if receipt else child, copy.deepcopy(timing), copy.deepcopy(suites)


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


def _swap_case_proof(fixture):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith("PCB_")]
        lines[positions[2]], lines[positions[3]] = lines[positions[3]], lines[positions[2]]
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _move_last_report_after_pass(fixture):
    text, child, timing, suites = fixture
    marker = "PASS: " + suites[0][1] + assess._load_contract()["protocol"]["routePassDetail"]

    def rewrite(source):
        lines = source.splitlines()
        report_positions = [index for index, line in enumerate(lines)
                            if line.startswith("PCB_")]
        report = lines.pop(report_positions[-1])
        pass_position = lines.index(marker)
        lines.insert(pass_position + 1, report)
        return "\n".join(lines) + "\n"

    return rewrite(text), rewrite(child), copy.deepcopy(timing), copy.deepcopy(suites)


def _add_failure_line(fixture):
    text, child, timing, suites = fixture
    return ("FAIL: synthetic native failure\n" + text,
            "FAIL: synthetic native failure\n" + child,
            copy.deepcopy(timing), copy.deepcopy(suites))


def _manual_reachability(summary):
    callbacks = summary["successfulCallbacks"]
    targets = [callback for callback in callbacks if callback["target"]]
    if len(targets) != 1:
        raise AssertionError("published oracle must have one target callback")
    pads = []
    for part in targets[0]["parts"]:
        for pad in part["pads"]:
            pad_id = pad["padId"]
            net_id = pad["netId"]
            if type(pad_id) is not str or type(net_id) is not str:
                raise AssertionError("published pad identity/net is not text")
            pads.append((pad_id, net_id))
    if len(pads) != 135 or len({pad_id for pad_id, _net_id in pads}) != 135:
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


def _literal_reachability(ctx):
    summary = ctx["prior"]["publishedCensusSummaries"]["2"]
    expected, pair_count, byte_count = _manual_reachability(summary)
    proof = _proof_row(ctx, "2")
    actual = proof["proof"]["sourceReachabilityHash"]
    if expected != "9a1081bec94f99493fff9bf12f2653f27d18db64b4b49b9b2ea826094c4d46b7":
        raise AssertionError("published reachability oracle digest changed")
    if actual != expected or proof["proof"]["candidateReachabilityHash"] != expected:
        raise AssertionError("literal reachability fixture is not anchored to net IDs")
    if ctx["publishedReachabilityHash"] != expected:
        raise AssertionError("fixture context did not retain independent oracle")
    return {"expected": expected, "pairs": pair_count, "bytes": byte_count,
            "result": "PASS"}


def _foreign_net_and_matching_hash(ctx, fixture):
    summary_prefix = assess.SUMMARY_PREFIX

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

    altered = _change_report(fixture, summary_prefix, 1, alter_net)
    reports = assess._parse_report_lines(altered[0], ctx["protocol"])
    summary = next(row for prefix, row, _line in reports
                   if prefix == summary_prefix and row["seed"] == "2")
    foreign_hash, _pair_count, _byte_count = _manual_reachability(summary)
    if foreign_hash == ctx["publishedReachabilityHash"]:
        raise AssertionError("one-pad foreign oracle unexpectedly matched published hash")
    return _change_report(
        altered, ctx["protocol"]["proofPrefix"], 1,
        lambda row: (
            row["proof"].__setitem__("sourceReachabilityHash", foreign_hash),
            row["proof"].__setitem__("candidateReachabilityHash", foreign_hash)))


def _run_canaries():
    ctx = _context()
    unit = _unit_fixture(ctx)
    routes = _route_fixture(ctx)
    results = []
    positives = []
    negatives = []

    def accept(name, phase, fixture):
        _phase(ctx, phase, fixture)
        positives.append(name)
        results.append({"case": name, "result": "PASS"})

    def reject(name, phase, fixture):
        try:
            _phase(ctx, phase, fixture)
        except assess.ReaderError:
            negatives.append(name)
            results.append({"case": name, "result": "REJECTED"})
            return
        raise AssertionError("reader accepted rejected canary: " + name)

    accept("valid unit phase with both fixture evaluators", "unit", unit)
    accept("valid routes phase with exact old census and new move rows", "routes", routes)
    literal = _literal_reachability(ctx)
    results.append({"case": "literal reachability hash from all 135 pad net IDs", **literal})
    positives.append("literal reachability hash from all 135 pad net IDs")
    proof = _proof_row(ctx, "2")
    if proof["candidate"]["geometryHash"] == proof["source"]["geometryBefore"]:
        raise AssertionError("positive candidate geometry did not change")
    results.append({"case": "changed physical candidate geometry accepted", "result": "PASS"})
    positives.append("changed physical candidate geometry accepted")

    proof_prefix = ctx["protocol"]["proofPrefix"]
    summary_prefix = assess.SUMMARY_PREFIX

    reject("foreign but equal source graph pair", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: (
            row["source"].__setitem__("liveGraphBefore", "f" * 64),
            row["source"].__setitem__("liveGraphAfter", "f" * 64))))
    reject("current census foreign net with matching foreign hashes", "routes",
           _foreign_net_and_matching_hash(ctx, routes))
    reject("wrong candidate component count", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["candidate"].__setitem__("componentCount", 55)))
    reject("wrong candidate trace count", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["candidate"].__setitem__("traceCount", 144)))
    reject("wrong derived move direction", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["move"].__setitem__("dy", 10)))
    reject("target callback filter drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0].__setitem__("target", False)))
    reject("case and move report order drift", "routes", _swap_case_proof(routes))
    reject("candidate geometry hash equals source", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["candidate"].__setitem__(
            "geometryHash", row["source"]["geometryBefore"])))
    reject("source versus candidate geometry mismatch", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["source"].__setitem__("geometryAfter", "0" * 64)))
    reject("selected mutation is not strict false", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row.__setitem__("selectedMutation", 0)))
    reject("move-proof label is not strict text", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row.__setitem__("label", 2)))
    reject("source area drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["source"].__setitem__("area", 9313351)))
    reject("actual copper delta drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["move"].__setitem__("actualCopperDelta", -9)))
    reject("analytic copper delta drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["move"].__setitem__("analyticCopperDelta", -9)))
    reject("pad pair count drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["proof"].__setitem__("padPairCount", 9044)))
    reject("provenance hashes differ", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["proof"].__setitem__(
            "candidateTraceProvenanceHash", "2" * 64)))
    reject("reachability hashes are foreign to net oracle", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: (
            row["proof"].__setitem__("sourceReachabilityHash", "e" * 64),
            row["proof"].__setitem__("candidateReachabilityHash", "e" * 64))))
    reject("route source count drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["proof"].__setitem__("candidateRouteSourceCount", 144)))
    reject("validation flag is not strict true", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["proof"].__setitem__("geometryValidated", 1)))
    reject("private physical disposition drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row.__setitem__("physicalFeasibility", "NOT_EVALUATED")))
    reject("no-target nested field is not null", "routes", _change_report(
        routes, proof_prefix, 0,
        lambda row: row.__setitem__("move", {})))
    reject("candidate area drift", "routes", _change_report(
        routes, proof_prefix, 1,
        lambda row: row["candidate"].__setitem__("area", 9313351)))
    reject("move proof row omitted", "routes", (
        routes[0].replace("\n" + proof_prefix, "\n", 1),
        routes[1].replace("\n" + proof_prefix, "\n", 1),
        copy.deepcopy(routes[2]), copy.deepcopy(routes[3])))
    reject("routes report appears after suite PASS", "routes",
           _move_last_report_after_pass(routes))
    reject("native failure marker", "routes", _add_failure_line(routes))
    timing_drift = copy.deepcopy(routes)
    timing_drift[2]["elapsedSeconds"] = 36001
    reject("native timing bound drift", "routes", timing_drift)
    status_drift = copy.deepcopy(routes)
    status_drift[2]["status"] = "FAIL"
    reject("native exit status drift", "routes", status_drift)
    reject("duplicate summary JSON key", "routes", _append_json_key(
        routes, summary_prefix, 0, ',"seed":"5"'))
    reject("unexpected summary JSON key", "routes", _append_json_key(
        routes, summary_prefix, 0, ',"unexpected":1'))

    unit_summary = _phase(ctx, "unit", unit)
    expected_unit = assess._unit_validation(
        ctx["contract"], unit_summary, {"unit.txt": "synthetic"},
        ctx["unitManifestSha"])
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

    if len(negatives) < 25:
        raise AssertionError("negative canary corpus is unexpectedly small")
    return {
        "schema": 1,
        "status": "PASS",
        "canaryCount": len(results),
        "positiveCount": len(positives),
        "negativeCount": len(negatives),
        "fixtureCounts": dict(ctx["protocol"]["unitFixtureCounts"]),
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha((HERE / "check_assessment.py").read_bytes()),
            "protocol.json": assess._sha(ctx["contract"]["protocolData"]),
            "priorAssess.py": assess.PRIOR_ASSESS_SHA,
            "publishedInputs": dict(ctx["protocol"]["publishedInputs"]),
            "suiteManifests": dict(ctx["protocol"]["suiteManifestSha256"]),
        },
        "results": results,
    }


def main():
    result = _run_canaries()
    if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
        raise assess.ReaderError(
            "refusing to overwrite existing task artifact: assessment-canary-results.json")
    payload = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    with RESULTS_PATH.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(payload)
    print("PASS assessment canaries; cases=" + str(result["canaryCount"]) +
          " rejected=" + str(result["negativeCount"]))


if __name__ == "__main__":
    main()
