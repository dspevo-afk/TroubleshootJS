"""In-memory synthetic canaries for the strict successful route pool reader."""

import argparse
import copy
import hashlib
import json
import re
import sys
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False, separators=(",", ":"), allow_nan=False)


def _contract():
    old, protocol, protocol_data, source_blobs, selected, whitespace = assess._contract()
    unit_suites, unit_sha = assess._suite_manifest("unit", old, protocol)
    route_suites, route_sha = assess._suite_manifest("routes", old, protocol)
    return {
        "old": old,
        "protocol": protocol,
        "protocolData": protocol_data,
        "sources": source_blobs,
        "selected": selected,
        "whitespace": whitespace,
        "unitSuites": unit_suites,
        "routeSuites": route_suites,
        "unitManifestSha": unit_sha,
        "routeManifestSha": route_sha,
    }


def _synthetic_event(ctx, seed, version, index, pair, case):
    old = ctx["old"]
    protocol = ctx["protocol"]
    score_records = old._placement_score_records(case)
    outline = {
        "x": case["outlineX"], "y": case["outlineY"],
        "width": case["width"], "height": case["height"],
    }
    occupied = {
        "x": case["occupiedX"], "y": case["occupiedY"],
        "width": case["occupiedWidth"], "height": case["occupiedHeight"],
    }
    identity = ("synthetic-parser-event:" + seed + ":" + str(version) + ":" +
                pair[0] + ":" + str(pair[1])).encode("ascii")
    return {
        "caseLabel": "seed" + seed,
        "routeIndex": index,
        "seed": seed,
        "version": version,
        "strategy": pair[0],
        "attempt": pair[1],
        "phase": protocol["phase"],
        "placementCheapScore": score_records[pair[1]]["total"],
        "routeQualityMilli": case["selectedQualityMilli"],
        "area": case["area"],
        "majorSide": max(case["width"], case["height"]),
        "preferCompact": True,
        "preferDensePacking": False,
        "rawOutline": outline,
        "occupiedBounds": occupied,
        "copperManhattanLength": case["copperManhattanLength"],
        "vias": case["vias"],
        "traceCount": 1,
        "geometrySha256": hashlib.sha256(identity + b":geometry").hexdigest(),
        "componentGeometryFingerprint": "synthetic-parser-only component fingerprint " + seed,
        "traceGeometryFingerprint": "synthetic-parser-only trace fingerprint " + seed,
        "sourceGraphBeforeSha256": case["liveBoardGraphSha256"],
        "sourceGraphAfterSha256": case["liveBoardGraphSha256"],
    }


def _route_fixture(ctx):
    protocol = ctx["protocol"]
    old = ctx["old"]
    body = []
    for suite_index, seed in enumerate(assess.ROOTS):
        for version in assess.VERSIONS:
            key = (seed, version)
            case = ctx["selected"][key]
            body.append(_line(protocol["whitespacePrefix"], ctx["whitespace"][key]))
            successes, _selected_strategy, _selected_attempt = assess._successful_route_pairs(case, old)
            for index, pair in enumerate(successes):
                event = _synthetic_event(ctx, seed, version, index, pair, case)
                body.append(_line(protocol["poolPrefix"], event))
            body.append(_line(protocol["casePrefix"], case))
        suite = ctx["routeSuites"][suite_index]
        body.append("PASS: " + suite[1] + protocol["routePassDetail"])
    receipt = "\n".join(body) + "\n"
    terminal = old.TERMINAL_TEMPLATE.format(count=len(ctx["routeSuites"]))
    text = receipt + terminal + "\n" + old.CLEANUP_LINE + "\n"
    timing = {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
        "suiteCount": len(ctx["routeSuites"]), "phase": "routes",
        "scope": protocol["timingScope"], "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    return text, receipt, timing, ctx["routeSuites"]


def _unit_fixture(ctx):
    protocol = ctx["protocol"]
    suite = ctx["unitSuites"][0]
    line = "PASS: " + suite[1] + "assertions=31 fixtures=" + str(protocol["unitFixtureCount"])
    line += " evaluator=" + protocol["unitEvaluator"]
    receipt = line + "\n"
    terminal = ctx["old"].TERMINAL_TEMPLATE.format(count=1)
    text = receipt + terminal + "\n" + ctx["old"].CLEANUP_LINE + "\n"
    timing = {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 0.0,
        "suiteCount": 1, "phase": "unit", "scope": ctx["protocol"]["timingScope"],
        "terminalCleanupIncluded": True, "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    return text, receipt, timing, ctx["unitSuites"]


def _report_positions(text, prefix):
    lines = text.splitlines()
    return [index for index, line in enumerate(lines) if line.startswith(prefix)]


def _rewrite_pair(text, receipt, mutation):
    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith("PCB_SUCCESSFUL_ROUTE_POOL ")]
        mutation(lines, positions)
        return "\n".join(lines) + "\n"
    return rewrite(text), rewrite(receipt)


def _mutate_json_line(lines, position, prefix, mutation):
    line = lines[position]
    row = json.loads(line[len(prefix):])
    changed = mutation(row)
    lines[position] = _line(prefix, changed)


def _phase_result(ctx, phase, fixture):
    text, receipt, timing, suites = fixture
    return assess._phase_summary(text, receipt, timing, suites, phase,
                                 ctx["protocol"], ctx["selected"],
                                 ctx["whitespace"], ctx["old"])


def run_canaries():
    """Run all synthetic cases in memory; return receipt data without writing it."""
    ctx = _contract()
    unit = _unit_fixture(ctx)
    routes = _route_fixture(ctx)
    results = []

    def accept(name, phase, fixture):
        _phase_result(ctx, phase, fixture)
        results.append({"case": name, "result": "PASS"})

    def reject(name, phase, fixture):
        try:
            _phase_result(ctx, phase, fixture)
        except assess.ReaderError:
            results.append({"case": name, "result": "REJECTED"})
            return
        raise AssertionError("reader accepted rejected synthetic canary: " + name)

    accept("valid unit phase", "unit", unit)
    accept("valid paired routes phase", "routes", routes)
    aggregate = _phase_result(ctx, "routes", routes)["census"]
    if (aggregate["roots"], aggregate["pairs"], aggregate["rows"],
            aggregate["selectedCaseRows"]) != (2, 2, 4, 4):
        raise AssertionError("paired routes census did not aggregate both roots")
    results.append({"case": "paired routes aggregate census counts", "result": "PASS"})

    route_report_indices = _report_positions(routes[0], "PCB_")
    pool_positions = _report_positions(routes[0], ctx["protocol"]["poolPrefix"])
    case_positions = _report_positions(routes[0], ctx["protocol"]["casePrefix"])
    whitespace_positions = _report_positions(routes[0], ctx["protocol"]["whitespacePrefix"])
    if len(route_report_indices) < 12 or not pool_positions or not case_positions:
        raise AssertionError("synthetic fixture lacks the expected selected and pool rows")

    def change_report(fixture, target_prefix, which, mutation, change_text=True, change_receipt=True):
        text, receipt, timing, suites = fixture

        def rewrite(source):
            lines = source.splitlines()
            positions = [index for index, line in enumerate(lines) if line.startswith(target_prefix)]
            if which >= len(positions):
                raise AssertionError("canary report row is unavailable: " + target_prefix)
            _mutate_json_line(lines, positions[which], target_prefix, mutation)
            return "\n".join(lines) + "\n"

        return (rewrite(text) if change_text else text,
                rewrite(receipt) if change_receipt else receipt,
                copy.deepcopy(timing), suites)

    reject("missing whitespace report", "routes", (
        "\n".join(line for index, line in enumerate(routes[0].splitlines())
                  if index != whitespace_positions[0]) + "\n",
        "\n".join(line for index, line in enumerate(routes[1].splitlines())
                  if index != whitespace_positions[0]) + "\n",
        copy.deepcopy(routes[2]), routes[3]))
    reject("missing successful pool event", "routes", _rewrite_pair(
        *routes[:2], lambda lines, positions: lines.pop(positions[0])) + routes[2:])

    def duplicate_first_pool(lines, positions):
        first = positions[0]
        lines.insert(first + 1, lines[first])

    reject("duplicated successful pool event", "routes", _rewrite_pair(
        *routes[:2], duplicate_first_pool) + routes[2:])

    multi_case = next(((seed, version) for seed in assess.ROOTS for version in assess.VERSIONS
                       if len(assess._successful_route_pairs(ctx["selected"][(seed, version)], ctx["old"])[0]) >= 2),
                      None)
    if multi_case is None:
        raise AssertionError("pinned prior rows lack a two-success route pool for ordering canary")
    multi_seed, multi_version = multi_case
    multi_events = [index for index in pool_positions if
                    (lambda row: row["seed"] == multi_seed and row["version"] == multi_version)(
                        json.loads(routes[0].splitlines()[index][len(ctx["protocol"]["poolPrefix"]):]))]
    if len(multi_events) < 2:
        raise AssertionError("two-success route pool fixture was not assembled")

    def reorder_two_pool(lines, positions):
        same_case = []
        for position in positions:
            row = json.loads(lines[position][len(ctx["protocol"]["poolPrefix"]):])
            if row["seed"] == multi_seed and row["version"] == multi_version:
                same_case.append(position)
        lines[same_case[0]], lines[same_case[1]] = lines[same_case[1]], lines[same_case[0]]

    def reorder_fixture(fixture):
        text, receipt = _rewrite_pair(*fixture[:2], reorder_two_pool)
        return text, receipt, copy.deepcopy(fixture[2]), fixture[3]

    reject("reordered successful pool events", "routes", reorder_fixture(routes))
    reject("unknown PCB report", "routes", _rewrite_pair(
        *routes[:2], lambda lines, _positions: lines.insert(0, "PCB_UNKNOWN_REPORT {}")) + routes[2:])

    reject("wrong pool root", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "seed": "2" if row["seed"] == "5" else "5"}))
    reject("wrong pool version", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "version": 22 if row["version"] == 15 else 15}))
    reject("boolean route index", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "routeIndex": True}))
    reject("noninteger route quality", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "routeQualityMilli": 1.5}))
    reject("outline area mismatch", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "area": row["area"] + 1}))
    reject("major side mismatch", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "majorSide": row["majorSide"] + 1}))
    reject("invalid compact flag", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "preferCompact": False}))
    reject("invalid dense packing flag", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "preferDensePacking": True}))
    reject("placement score differs from pinned record", "routes", change_report(
        routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "placementCheapScore": row["placementCheapScore"] + 1}))
    reject("wrong finalized pool phase", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "phase": "AFTER_SELECTED_TRIM"}))
    reject("source graph mutation", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "sourceGraphAfterSha256": "0" * 64}))
    reject("self-consistent graph differs from selected row", "routes", change_report(
        routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "sourceGraphBeforeSha256": "1" * 64,
                     "sourceGraphAfterSha256": "1" * 64}))
    reject("malformed geometry digest", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "geometrySha256": "not-a-sha"}))
    reject("empty immutable component fingerprint", "routes", change_report(
        routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "componentGeometryFingerprint": ""}))
    reject("nonpositive rectangle dimension", "routes", change_report(
        routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "rawOutline": {**row["rawOutline"], "width": 0}}))
    reject("event field order changed", "routes", change_report(routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: dict(reversed(list(row.items())))))

    def drift_selected(row):
        row["geometrySha256"] = "0" * 64
        return row

    reject("selected 42-field witness drift", "routes", change_report(
        routes, ctx["protocol"]["casePrefix"], 0, drift_selected))
    reject("selected elapsed case bound", "routes", change_report(
        routes, ctx["protocol"]["casePrefix"], 0,
        lambda row: {**row, "elapsedMillis": 60001}))

    rejected_pairs = {}
    for seed in assess.ROOTS:
        for version in assess.VERSIONS:
            case = ctx["selected"][(seed, version)]
            successes, _strategy, _attempt = assess._successful_route_pairs(case, ctx["old"])
            outcomes = re.search(r";routeOutcomes=\[(.*)\]\Z", case["statisticsCanonical"]).group(1)
            tokens = outcomes.split(", ")
            failed = [token for token in tokens if "=REJECTED:" in token]
            if failed:
                rejected_pairs[(seed, version)] = re.match(
                    r"(P05_ONE_FACE|P07_FULLER_TWO_LAYER)@([0-5])=", failed[0]).groups()
    if not rejected_pairs:
        raise AssertionError("pinned route rows lack a rejected attempt for failed-attempt canary")
    failed_pair = next((row for row in route_report_indices
                        if routes[0].splitlines()[row].startswith(ctx["protocol"]["poolPrefix"])), None)
    if failed_pair is None:
        raise AssertionError("synthetic pool event is missing")
    failed_seed_version = next(iter(rejected_pairs))
    failed_strategy, failed_attempt = rejected_pairs[failed_seed_version]
    first_event = json.loads(routes[0].splitlines()[pool_positions[0]][
                             len(ctx["protocol"]["poolPrefix"]):])
    first_key = (first_event["seed"], first_event["version"])
    first_failure = rejected_pairs.get(first_key)
    if first_failure is None:
        raise AssertionError("first synthetic pool case lacks a rejected attempt")
    reject("pool event for failed route attempt", "routes", change_report(
        routes, ctx["protocol"]["poolPrefix"], 0,
        lambda row: {**row, "strategy": first_failure[0], "attempt": int(first_failure[1])}))

    def mutate_route_stats(change):
        case = copy.deepcopy(ctx["selected"][(multi_seed, multi_version)])
        case["statisticsCanonical"] = change(case["statisticsCanonical"])
        try:
            assess._successful_route_pairs(case, ctx["old"])
        except assess.ReaderError:
            results.append({"case": "route outcome sequence " + change.__name__, "result": "REJECTED"})
            return
        raise AssertionError("route outcome canary was accepted: " + change.__name__)

    def missing_selected_success(text):
        case = ctx["selected"][(multi_seed, multi_version)]
        _successes, strategy, attempt = assess._successful_route_pairs(case, ctx["old"])
        target = re.escape(strategy + "@" + str(attempt) + "=SUCCESS")
        return re.sub(target, strategy + "@" + str(attempt) + "=REJECTED:SYNTHETIC", text, count=1)

    def duplicate_success(text):
        match = re.search(r"(P07_FULLER_TWO_LAYER@[0-5]=SUCCESS)", text)
        if not match:
            raise AssertionError("pinned route statistics lack a P07 success")
        return text[:match.end()] + ", " + match.group(1) + text[match.end():]

    def duplicate_selected(text):
        selected_match = re.search(r"(;selected=P07_FULLER_TWO_LAYER)", text)
        if not selected_match:
            raise AssertionError("pinned route statistics lack P07 selected strategy")
        return text.replace(selected_match.group(1), selected_match.group(1) + selected_match.group(1), 1)

    for mutation in (missing_selected_success, duplicate_success, duplicate_selected):
        mutate_route_stats(mutation)

    reject("missing suite PASS marker", "routes", (
        "\n".join(line for line in routes[0].splitlines()
                  if not line.startswith("PASS: " + ctx["routeSuites"][0][1])) + "\n",
        "\n".join(line for line in routes[1].splitlines()
                  if not line.startswith("PASS: " + ctx["routeSuites"][0][1])) + "\n",
        copy.deepcopy(routes[2]), routes[3]))
    reject("duplicate suite PASS marker", "routes", (
        routes[0] + "PASS: " + ctx["routeSuites"][0][1] + ctx["protocol"]["routePassDetail"] + "\n",
        routes[1] + "PASS: " + ctx["routeSuites"][0][1] + ctx["protocol"]["routePassDetail"] + "\n",
        copy.deepcopy(routes[2]), routes[3]))
    reject("wrong fixture PASS marker", "unit", (
        unit[0].replace("fixtures=4", "fixtures=5"),
        unit[1].replace("fixtures=4", "fixtures=5"),
        copy.deepcopy(unit[2]), unit[3]))
    reject("zero assertion count", "unit", (
        unit[0].replace("assertions=31", "assertions=0"),
        unit[1].replace("assertions=31", "assertions=0"),
        copy.deepcopy(unit[2]), unit[3]))

    reject("missing terminal marker", "routes", (
        routes[0].replace(ctx["old"].TERMINAL_TEMPLATE.format(count=2) + "\n", ""),
        routes[1].replace(ctx["old"].TERMINAL_TEMPLATE.format(count=2) + "\n", ""),
        copy.deepcopy(routes[2]), routes[3]))
    reject("duplicate terminal marker", "routes", (
        routes[0] + ctx["old"].TERMINAL_TEMPLATE.format(count=2) + "\n",
        routes[1] + ctx["old"].TERMINAL_TEMPLATE.format(count=2) + "\n",
        copy.deepcopy(routes[2]), routes[3]))
    reject("missing cleanup marker", "unit", (
        unit[0].replace(ctx["old"].CLEANUP_LINE + "\n", ""),
        unit[1], copy.deepcopy(unit[2]), unit[3]))
    reject("cleanup before terminal", "unit", (
        unit[0].replace(ctx["old"].TERMINAL_TEMPLATE.format(count=1) + "\n" +
                        ctx["old"].CLEANUP_LINE + "\n",
                        ctx["old"].CLEANUP_LINE + "\n" +
                        ctx["old"].TERMINAL_TEMPLATE.format(count=1) + "\n"),
        unit[1], copy.deepcopy(unit[2]), unit[3]))
    reject("cleanup included in child receipt", "unit", (
        unit[0], unit[1] + ctx["old"].CLEANUP_LINE + "\n",
        copy.deepcopy(unit[2]), unit[3]))
    reject("log receipt report mismatch", "routes", (
        routes[0].replace(ctx["protocol"]["poolPrefix"],
                          ctx["protocol"]["poolPrefix"], 1).replace(
                              "\"routeIndex\":0", "\"routeIndex\":9", 1),
        routes[1], copy.deepcopy(routes[2]), routes[3]))

    def timing_change(name, field, value, fixture):
        text, receipt, timing, suites = fixture
        timing[field] = value
        reject(name, "routes", (text, receipt, timing, suites))

    timing_change("boolean elapsed time", "elapsedSeconds", True, routes)
    timing_change("negative elapsed time", "elapsedSeconds", -0.01, routes)
    timing_change("phase timing cap", "elapsedSeconds", 36000.01, routes)
    timing_change("boolean exit code", "exitCode", False, routes)
    timing_change("boolean suite count", "suiteCount", True, routes)
    timing_change("integer cleanup flag", "terminalCleanupIncluded", 1, routes)
    timing_change("changed cleanup duration", "cleanupDuration", "0", routes)

    reject("unit report leakage", "unit", (
        unit[0] + routes[0].splitlines()[pool_positions[0]] + "\n",
        unit[1] + routes[1].splitlines()[pool_positions[0]] + "\n",
        copy.deepcopy(unit[2]), unit[3]))

    event_line = routes[0].splitlines()[pool_positions[0]]
    reject("duplicate JSON object key", "routes", (
        routes[0].replace(event_line, ctx["protocol"]["poolPrefix"] +
                         '{"caseLabel":"seed5","caseLabel":"seed5"}', 1),
        routes[1].replace(event_line, ctx["protocol"]["poolPrefix"] +
                           '{"caseLabel":"seed5","caseLabel":"seed5"}', 1),
        copy.deepcopy(routes[2]), routes[3]))
    reject("nonstandard JSON number", "routes", (
        routes[0].replace(event_line, ctx["protocol"]["poolPrefix"] +
                         '{"routeQualityMilli":NaN}', 1),
        routes[1].replace(event_line, ctx["protocol"]["poolPrefix"] +
                           '{"routeQualityMilli":NaN}', 1),
        copy.deepcopy(routes[2]), routes[3]))

    try:
        assess._suite_manifest("routes", ctx["old"], ctx["protocol"], b"[[\"changed\",\"marker \"]]\n")
    except assess.ReaderError:
        results.append({"case": "suite manifest bytes differ from pin", "result": "REJECTED"})
    else:
        raise AssertionError("suite manifest pin accepted changed bytes")
    changed_protocol = ctx["protocolData"] + b" "
    try:
        assess._contract(protocol_override=changed_protocol)
    except assess.ReaderError:
        results.append({"case": "protocol bytes differ from pin", "result": "REJECTED"})
    else:
        raise AssertionError("protocol pin accepted changed bytes")

    changed_source = dict(ctx["sources"])
    changed_source["routes.txt"] = changed_source["routes.txt"] + b"\n"
    try:
        assess._verify_published_inputs(ctx["protocol"], ctx["old"], changed_source)
    except assess.ReaderError:
        results.append({"case": "published route source differs from pin", "result": "REJECTED"})
    else:
        raise AssertionError("published route source pin accepted changed bytes")

    unit_summary = _phase_result(ctx, "unit", unit)
    unit_validation = assess._unit_validation(
        (ctx["old"], ctx["protocol"], ctx["protocolData"], ctx["sources"],
         ctx["selected"], ctx["whitespace"]),
        unit_summary, {"unit.txt": "0" * 64}, ctx["unitManifestSha"])
    try:
        assess._validate_unit_prerequisite(b"{}", unit_validation, ctx["old"])
    except assess.ReaderError:
        results.append({"case": "stale unit prerequisite", "result": "REJECTED"})
    else:
        raise AssertionError("stale unit prerequisite was accepted")
    prereq_bytes = (json.dumps(unit_validation, ensure_ascii=False, indent=2,
                               allow_nan=False) + "\n").encode("utf-8")
    _need = assess._validate_unit_prerequisite(prereq_bytes, unit_validation, ctx["old"])
    results.append({"case": "fresh matching unit prerequisite", "result": "PASS"})

    names = [row["case"] for row in results]
    if len(names) != len(set(names)):
        raise AssertionError("synthetic canary names are not unique")
    if any(row["result"] not in ("PASS", "REJECTED") for row in results):
        raise AssertionError("synthetic canary result state is unknown")
    return {
        "status": "PASS",
        "scope": "In-memory synthetic parser canaries only; event payloads are not route measurements",
        "canaryCount": len(results),
        "positiveCases": sum(row["result"] == "PASS" for row in results),
        "rejectionCases": sum(row["result"] == "REJECTED" for row in results),
        "fixtureCount": ctx["protocol"]["unitFixtureCount"],
        "nativeRuntimeEvidence": "NOT_RUN",
        "consumedSha256": {
            "assess.py": assess._sha((HERE / "assess.py").read_bytes()),
            "check_assessment.py": assess._sha(Path(__file__).read_bytes()),
            "protocol.json": assess._sha(ctx["protocolData"]),
            "priorAssess.py": assess.OLD_ASSESS_SHA,
            "publishedInputs": {name: assess._sha(data) for name, data in ctx["sources"].items()},
            "suiteManifests": {
                "unit-suites.json": ctx["unitManifestSha"],
                "routes-suites.json": ctx["routeManifestSha"],
            },
        },
        "cases": results,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-receipt", action="store_true",
                        help="write the final canary receipt after root source review")
    args = parser.parse_args(argv)
    try:
        result = run_canaries()
        if args.write_receipt:
            if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
                raise assess.ReaderError("refusing to overwrite existing canary receipt")
            payload = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
            with RESULTS_PATH.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(payload)
        print("PASS synthetic offline assessment canaries: " + str(result["canaryCount"]) +
              " cases; receipt " + ("written" if args.write_receipt else "not written"))
        return 0
    except (assess.ReaderError, OSError) as exc:
        print("FAIL synthetic offline assessment canaries: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
