"""In-memory positive and negative canaries for the routed-leg reader."""

import copy
import json
import re
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"


def _line(prefix, row):
    return prefix + json.dumps(
        row, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _part(component_id, eligible=True, null_source=False):
    if not eligible:
        directions = [
            {"dx": dx, "dy": dy, "eligible": False,
             "reason": "PART_INELIGIBLE", "copperDelta": None}
            for dx, dy in assess.DIRECTIONS
        ]
        return {
            "componentId": component_id,
            "packageId": "TO92_NPN",
            "eligiblePackage": False,
            "eligibilityReason": "UNSUPPORTED_PACKAGE",
            "knownExtraCenterlineContact": False,
            "extraCenterlineContactCount": 0,
            "pads": [
                {"padId": component_id + ".1", "netId": "N1",
                 "x": 1000, "y": 1000, "incidents": []},
                {"padId": component_id + ".2", "netId": "N2",
                 "x": 1100, "y": 1000, "incidents": []},
            ],
            "directions": directions,
        }
    source0 = None if null_source else "trace/shared"
    source1 = None if null_source else "trace/shared"
    directions = [
        {"dx": -10, "dy": 0, "eligible": not null_source,
         "reason": "MISSING_TRACE_SOURCE_ID" if null_source else "ELIGIBLE",
         "copperDelta": None if null_source else 0},
        {"dx": 10, "dy": 0, "eligible": not null_source,
         "reason": "MISSING_TRACE_SOURCE_ID" if null_source else "ELIGIBLE",
         "copperDelta": None if null_source else 0},
        {"dx": 0, "dy": -10, "eligible": False,
         "reason": "MISSING_TRACE_SOURCE_ID" if null_source else
         "NON_PARALLEL_SHIFT", "copperDelta": None},
        {"dx": 0, "dy": 10, "eligible": False,
         "reason": "MISSING_TRACE_SOURCE_ID" if null_source else
         "NON_PARALLEL_SHIFT", "copperDelta": None},
    ]
    return {
        "componentId": component_id,
        "packageId": "AXIAL_RESISTOR",
        "eligiblePackage": True,
        "eligibilityReason": "ELIGIBLE",
        "knownExtraCenterlineContact": False,
        "extraCenterlineContactCount": 0,
        "pads": [
            {
                "padId": component_id + ".1", "netId": "N1",
                "x": 0, "y": 0,
                "incidents": [{
                    "traceIndex": 0, "traceSourceId": source0,
                    "traceNetId": "N1", "layer": "TOP", "width": 11,
                    "endRole": "START", "endpointX": 0, "endpointY": 0,
                    "neighborX": -40, "neighborY": 0,
                }],
            },
            {
                "padId": component_id + ".2", "netId": "N2",
                "x": 100, "y": 100,
                "incidents": [{
                    "traceIndex": 1, "traceSourceId": source1,
                    "traceNetId": "N2", "layer": "BOTTOM", "width": 13,
                    "endRole": "END", "endpointX": 100, "endpointY": 100,
                    "neighborX": 140, "neighborY": 100,
                }],
            },
        ],
        "directions": directions,
    }


def _target_parts(case):
    parts = [_part("C000")]
    for index in range(1, case["parts"]):
        parts.append(_part("C" + format(index, "03d"), eligible=False))
    return parts


def _summary(ctx, seed, case):
    protocol = ctx["protocol"]
    callbacks = []
    for index, pair in enumerate(assess._successful_callbacks(case)):
        strategy, attempt = pair
        target = (strategy == protocol["targetStrategy"] and
                  attempt == protocol["targetAttempt"])
        callbacks.append({
            "index": index,
            "strategy": strategy,
            "attempt": attempt,
            "target": target,
            "geometryHashBefore": ("%064x" % (index + 1)),
            "geometryHashAfter": ("%064x" % (index + 1)),
            "parts": _target_parts(case) if target else [],
        })
    all_parts = [part for callback in callbacks
                 if callback["target"] for part in callback["parts"]]
    directions = [direction for part in all_parts
                  for direction in part["directions"]]
    return {
        "seed": seed,
        "label": "seed" + seed,
        "version": 15,
        "phase": protocol["phase"],
        "status": "OBSERVATION_ONLY",
        "physicalFeasibility": "NOT_EVALUATED",
        "graphHashBefore": case["liveBoardGraphSha256"],
        "graphHashAfter": case["liveBoardGraphSha256"],
        "successfulCallbacks": callbacks,
        "aggregate": {
            "callbackCount": len(callbacks),
            "targetCallbackCount": sum(
                callback["target"] for callback in callbacks),
            "partCount": len(all_parts),
            "eligiblePartCount": sum(
                part["eligiblePackage"] for part in all_parts),
            "eligibleDirectionCount": sum(
                direction["eligible"] for direction in directions),
            "rejectedDirectionCount": sum(
                not direction["eligible"] for direction in directions),
        },
    }


def _context():
    context = assess._load_contract()
    unit_suites, unit_sha = assess._load_suite_manifest(
        "unit", context["protocol"])
    route_suites, route_sha = assess._load_suite_manifest(
        "routes", context["protocol"])
    context.update({
        "unitSuites": unit_suites,
        "routeSuites": route_suites,
        "unitManifestSha": unit_sha,
        "routeManifestSha": route_sha,
    })
    return context


def _unit_fixture(ctx):
    suite = ctx["unitSuites"][0]
    marker = ("PASS: " + suite[1] + "assertions=31 fixtures=" +
              str(ctx["protocol"]["unitFixtureCount"]) +
              " evaluator=" + ctx["protocol"]["evaluator"])
    receipt = marker + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=1)
    return (
        receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n",
        receipt,
        {
            "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
            "suiteCount": 1, "phase": "unit",
            "scope": ctx["protocol"]["timingScope"],
            "terminalCleanupIncluded": True,
            "cleanupDuration": "NOT_SEPARATELY_MEASURED",
        },
        ctx["unitSuites"],
    )


def _route_fixture(ctx):
    protocol = ctx["protocol"]
    body = []
    for seed in assess.ROOTS:
        key = (seed, assess.VERSION)
        case = ctx["selected"][key]
        body.append(_line(protocol["whitespacePrefix"],
                          ctx["whitespace"][key]))
        body.append(_line(protocol["summaryPrefix"],
                          _summary(ctx, seed, case)))
        body.append(_line(protocol["casePrefix"], copy.deepcopy(case)))
    marker = "PASS: " + ctx["routeSuites"][0][1] + protocol["routePassDetail"]
    body.append(marker)
    receipt = "\n".join(body) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=1)
    return (
        receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n",
        receipt,
        {
            "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
            "suiteCount": 1, "phase": "routes",
            "scope": protocol["timingScope"],
            "terminalCleanupIncluded": True,
            "cleanupDuration": "NOT_SEPARATELY_MEASURED",
        },
        ctx["routeSuites"],
    )


def _phase(ctx, phase, fixture):
    return assess._phase_summary(
        fixture[0], fixture[1], fixture[2], fixture[3], phase,
        ctx["protocol"], ctx["selected"], ctx["whitespace"])


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

    return (
        rewrite(text),
        rewrite(child) if receipt else child,
        copy.deepcopy(timing),
        copy.deepcopy(suites),
    )


def _append_json_key(fixture, prefix, which, fragment):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        position = positions[which]
        lines[position] = lines[position][:-1] + fragment + "}"
        return "\n".join(lines) + "\n"

    return (
        rewrite(text), rewrite(child), copy.deepcopy(timing),
        copy.deepcopy(suites),
    )


def _move_last_report_after_pass(fixture):
    text, child, timing, suites = fixture
    prefix = "PCB_"
    marker = "PASS: " + suites[0][1] + assess._load_contract()["protocol"][
        "routePassDetail"]

    def rewrite(source):
        lines = source.splitlines()
        report_positions = [index for index, line in enumerate(lines)
                            if line.startswith(prefix)]
        report = lines.pop(report_positions[-1])
        pass_position = lines.index(marker)
        lines.insert(pass_position + 1, report)
        return "\n".join(lines) + "\n"

    return (
        rewrite(text), rewrite(child), copy.deepcopy(timing),
        copy.deepcopy(suites),
    )


def _set_directions(part, specs):
    if len(specs) != len(assess.DIRECTIONS):
        raise AssertionError("literal reader fixture direction count changed")
    part["directions"] = [
        {"dx": dx, "dy": dy, "eligible": eligible,
         "reason": reason, "copperDelta": delta}
        for (dx, dy), (eligible, reason, delta) in zip(
            assess.DIRECTIONS, specs)
    ]


def _all_rejected(reason):
    return [(False, reason, None) for _direction in assess.DIRECTIONS]


def _literal_reader_fixtures():
    fixtures = []

    signed = _part("F000")
    signed["pads"][1]["incidents"][0]["neighborX"] = 60
    _set_directions(signed, [
        (True, "ELIGIBLE", -20),
        (True, "ELIGIBLE", 20),
        (False, "NON_PARALLEL_SHIFT", None),
        (False, "NON_PARALLEL_SHIFT", None),
    ])
    fixtures.append(("valid signed copper deltas -20 0 +20", signed))

    zero = _part("F001")
    _set_directions(zero, [
        (True, "ELIGIBLE", 0),
        (True, "ELIGIBLE", 0),
        (False, "NON_PARALLEL_SHIFT", None),
        (False, "NON_PARALLEL_SHIFT", None),
    ])
    fixtures.append(("valid zero copper deltas", zero))

    missing_source = _part("F002", null_source=True)
    _set_directions(missing_source, _all_rejected(
        "MISSING_TRACE_SOURCE_ID"))
    fixtures.append(("reported null trace source", missing_source))

    missing_endpoint = _part("F003")
    missing_endpoint["pads"][1]["incidents"] = []
    _set_directions(missing_endpoint, _all_rejected(
        "MISSING_EXPLICIT_ENDPOINT"))
    fixtures.append(("reported missing explicit endpoint", missing_endpoint))

    wrong_net = _part("F004")
    wrong_net["pads"][0]["incidents"][0]["traceNetId"] = "N2"
    _set_directions(wrong_net, _all_rejected("TRACE_NET_MISMATCH"))
    fixtures.append(("reported trace net mismatch", wrong_net))

    extra = _part("F005")
    extra["knownExtraCenterlineContact"] = True
    extra["extraCenterlineContactCount"] = 1
    _set_directions(extra, _all_rejected("EXTRA_CENTERLINE_CONTACT"))
    fixtures.append(("reported extra centerline contact", extra))

    same_trace = _part("F006")
    first = same_trace["pads"][0]["incidents"][0]
    second = same_trace["pads"][1]["incidents"][0]
    same_trace["pads"][1]["netId"] = "N1"
    second.update({
        "traceIndex": first["traceIndex"],
        "traceSourceId": first["traceSourceId"],
        "traceNetId": first["traceNetId"],
        "layer": first["layer"],
        "width": first["width"],
        "endRole": "END",
    })
    _set_directions(same_trace, _all_rejected(
        "SAME_TRACE_BOTH_TERMINALS"))
    fixtures.append(("reported same trace terminals", same_trace))

    collapse = _part("F007")
    collapse["pads"][0]["incidents"][0]["neighborX"] = -10
    _set_directions(collapse, [
        (False, "COLLAPSE", None),
        (True, "ELIGIBLE", 0),
        (False, "NON_PARALLEL_SHIFT", None),
        (False, "NON_PARALLEL_SHIFT", None),
    ])
    fixtures.append(("reported collapse", collapse))

    reverse = _part("F008")
    reverse["pads"][0]["incidents"][0]["neighborX"] = 5
    _set_directions(reverse, [
        (True, "ELIGIBLE", 20),
        (False, "REVERSE", None),
        (False, "NON_PARALLEL_SHIFT", None),
        (False, "NON_PARALLEL_SHIFT", None),
    ])
    fixtures.append(("reported reverse", reverse))
    return fixtures


def _run_literal_reader_fixtures():
    results = []
    for name, part in _literal_reader_fixtures():
        expected = copy.deepcopy(part["directions"])
        component_id, counts = assess._validate_part(part, None)
        if component_id != part["componentId"]:
            raise AssertionError("literal reader fixture component changed")
        if part["directions"] != expected:
            raise AssertionError("literal reader fixture directions changed")
        if counts["eligibleDirections"] != sum(
                direction["eligible"] for direction in expected):
            raise AssertionError("literal reader fixture aggregate changed")
        results.append({
            "case": name,
            "result": "PASS",
            "expectedReasons": [
                direction["reason"] for direction in expected
            ],
            "expectedDeltas": [
                direction["copperDelta"] for direction in expected
            ],
        })
    return results


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

    accept("valid unit phase", "unit", unit)
    accept("valid routes phase with both roots", "routes", routes)
    aggregate = _phase(ctx, "routes", routes)["census"]
    if (aggregate["roots"], aggregate["rows"],
            aggregate["selectedCaseRows"]) != (2, 2, 2):
        raise AssertionError("positive route aggregate counts changed")
    results.append({"case": "positive aggregate census counts", "result": "PASS"})
    positives.append("positive aggregate census counts")
    literal_results = _run_literal_reader_fixtures()
    results.extend(literal_results)
    positives.extend(item["case"] for item in literal_results)

    summary_prefix = ctx["protocol"]["summaryPrefix"]
    case_prefix = ctx["protocol"]["casePrefix"]

    reject("duplicate summary key", "routes", _append_json_key(
        routes, summary_prefix, 0, ',"seed":"5"'))
    reject("unexpected summary key", "routes", _append_json_key(
        routes, summary_prefix, 0, ',"unexpected":1'))
    reject("wrong typed target boolean", "routes", _change_report(
        routes, summary_prefix, 0,
        lambda row: row["successfulCallbacks"][0].__setitem__("target", 1)))
    reject("foreign but equal graph anchor", "routes", _change_report(
        routes, summary_prefix, 0,
        lambda row: (
            row.__setitem__("graphHashBefore", "f" * 64),
            row.__setitem__("graphHashAfter", "f" * 64))))
    reject("selected 42-field drift", "routes", _change_report(
        routes, case_prefix, 0,
        lambda row: row.__setitem__("area", row["area"] + 1)))
    reject("successful callback sequence drift", "routes", _change_report(
        routes, summary_prefix, 0,
        lambda row: row["successfulCallbacks"][0].__setitem__("attempt", 4)))
    reject("target callback filter drift", "routes", _change_report(
        routes, summary_prefix, 0,
        lambda row: row["successfulCallbacks"][0].__setitem__("target", True)))
    reject("package eligibility drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0].__setitem__(
            "packageId", "TO92_NPN")))

    def target_package_unsupported(row):
        part = row["successfulCallbacks"][0]["parts"][0]
        replacement = _part(part["componentId"], eligible=False)
        replacement["packageId"] = "AXIAL_RESISTOR"
        part.clear()
        part.update(replacement)

    reject("target package marked unsupported", "routes", _change_report(
        routes, summary_prefix, 1, target_package_unsupported))
    reject("traceNetId drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("traceNetId", "N2")))
    reject("traceIndex bound drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("traceIndex", assess.MAX_COUNT + 1)))
    reject("missing trace source identity", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("traceSourceId", None)))
    reject("endpoint coordinate drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("endpointX", 1)))
    reject("zero length leg", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("neighborX", 0)))
    reject("missing explicit endpoint", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][1].__setitem__(
            "incidents", [])))
    reject("terminal count drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"].pop()))

    def same_trace(row):
        part = row["successfulCallbacks"][0]["parts"][0]
        first = part["pads"][0]["incidents"][0]
        second = part["pads"][1]["incidents"][0]
        part["pads"][1]["netId"] = "N1"
        second.update({
            "traceIndex": first["traceIndex"],
            "traceSourceId": first["traceSourceId"],
            "traceNetId": first["traceNetId"],
            "layer": first["layer"],
            "width": first["width"],
            "endRole": "END",
        })

    reject("traceIndex same-trace drift", "routes", _change_report(
        routes, summary_prefix, 1, same_trace))
    reject("nonparallel direction drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("neighborY", 1)))
    reject("collapse direction drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("neighborX", -10)))
    reject("reverse direction drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["pads"][0][
            "incidents"][0].__setitem__("neighborX", 5)))
    reject("copper delta drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["successfulCallbacks"][0]["parts"][0]["directions"][
            0].__setitem__("copperDelta", 1)))
    reject("known extra-contact drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: (
            row["successfulCallbacks"][0]["parts"][0].__setitem__(
                "knownExtraCenterlineContact", True),
            row["successfulCallbacks"][0]["parts"][0].__setitem__(
                "extraCenterlineContactCount", 1))))
    reject("aggregate drift", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row["aggregate"].__setitem__(
            "partCount", row["aggregate"]["partCount"] + 1)))
    timing_drift = copy.deepcopy(routes)
    timing_drift[2]["elapsedSeconds"] = 36001
    reject("timing bound drift", "routes", timing_drift)
    marker_drift = (
        routes[0].replace(ctx["protocol"]["routePassDetail"], "roots=2 rows=3 " +
                          "evaluator=endpointLegCensus"),
        routes[1].replace(ctx["protocol"]["routePassDetail"], "roots=2 rows=3 " +
                          "evaluator=endpointLegCensus"),
        copy.deepcopy(routes[2]), copy.deepcopy(routes[3]),
    )
    reject("suite marker drift", "routes", marker_drift)
    reject("receipt report mismatch", "routes", _change_report(
        routes, summary_prefix, 1,
        lambda row: row.__setitem__("label", "receipt-mismatch"),
        receipt=False))
    reject("routes PASS before reports", "routes",
           _move_last_report_after_pass(routes))

    unit_summary = _phase(ctx, "unit", unit)
    expected_unit = assess._unit_validation(
        ctx, unit_summary, {"unit.txt": "synthetic"}, ctx["unitManifestSha"])
    try:
        assess._validate_unit_prerequisite(
            b"{}", expected_unit)
    except assess.ReaderError:
        negatives.append("stale unit prerequisite")
        results.append({"case": "stale unit prerequisite", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted stale unit prerequisite")

    protocol_data = ctx["protocolData"]
    altered_protocol = protocol_data.replace(b'"schema": 1', b'"schema": 2', 1)
    try:
        assess._load_contract(protocol_override=altered_protocol)
    except assess.ReaderError:
        negatives.append("protocol pin drift")
        results.append({"case": "protocol pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered protocol pin")

    route_path = next(path for path in assess.PUBLISHED_INPUTS
                      if path.endswith("/routes.txt"))
    altered_routes = ctx["published"][route_path] + b"\n"
    try:
        assess._load_contract(published_overrides={route_path: altered_routes})
    except assess.ReaderError:
        negatives.append("published input pin drift")
        results.append({"case": "published input pin drift", "result": "REJECTED"})
    else:
        raise AssertionError("reader accepted altered published input pin")

    if len(negatives) < 20:
        raise AssertionError("negative canary corpus is unexpectedly small")
    return {
        "schema": 1,
        "status": "PASS",
        "fixtureCount": ctx["protocol"]["unitFixtureCount"],
        "canaryCount": len(results),
        "positiveCount": len(positives),
        "negativeCount": len(negatives),
        "readerFixtureCount": len(literal_results),
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha(
                (HERE / "check_assessment.py").read_bytes()),
            "protocol.json": assess._sha(ctx["protocolData"]),
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
    RESULTS_PATH.write_text(
        json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
        encoding="utf-8", newline="\n")
    print("PASS assessment canaries; cases=" + str(result["canaryCount"]) +
          " rejected=" + str(result["negativeCount"]))


if __name__ == "__main__":
    main()
