"""Synthetic offline canaries for the routed trace-guard assessment reader."""

import copy
import hashlib
import json
from collections import Counter
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"


def _json_line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=True, separators=(",", ":"), allow_nan=False)


def _rectangle_for(route, area):
    for width in range(10, route["width"] + 1, 10):
        if area % width:
            continue
        height = area // width
        if height > 0 and height % 10 == 0 and height <= route["height"]:
            return {
                "x": route["outlineX"], "y": route["outlineY"],
                "width": width, "height": height, "area": area,
            }
    raise AssertionError("synthetic protected rectangle does not fit its route")


def _case20(old, area_regression=False, seed_override=None):
    row = dict(old)
    seed = old["seed"] if seed_override is None else seed_override
    row["seed"] = seed
    row["version"] = 20
    row["density"] = 15
    row["elapsedMillis"] = 1
    row["sourcePlanSha256"] = hashlib.sha256(("synthetic-plan-20:" + seed).encode("ascii")).hexdigest()
    row["geometrySha256"] = hashlib.sha256(("synthetic-geometry-20:" + seed).encode("ascii")).hexdigest()
    if area_regression:
        row["width"] = old["width"] + 10
        row["area"] = row["width"] * row["height"]
    return row


def _whitespace(seed, version, route):
    area = route["largestProtectedEmptyRectangleArea"]
    return {
        "seed": seed,
        "version": version,
        "parts": route["parts"],
        "gridUnits": 10,
        "sampledCells": 1000,
        "packageAccessOccupiedCells": 240,
        "protectedFreeCells": 610,
        "protectedFreeFraction": 0.61,
        "largestProtectedEmptyRectangle": _rectangle_for(route, area),
        "scope": assess.WHITESPACE_SCOPE,
    }


def _records(rows_by_seed, metric_regression=None):
    records = []
    for index, seed in enumerate(assess.ROOTS):
        old = rows_by_seed[index]
        case15 = dict(old)
        case15["elapsedMillis"] = 0
        case20 = _case20(old)
        if index == 0:
            case20["width"] = old["width"] - 10
            case20["area"] = case20["width"] * case20["height"]
        if metric_regression and metric_regression[0] == seed:
            metric = metric_regression[1]
            if metric == "area":
                case20["width"] = old["width"] + 10
                case20["area"] = case20["width"] * case20["height"]
            elif metric == "largestProtectedEmptyRectangleArea":
                case20[metric] = (case20["width"] // 10 * 10) * (case20["height"] // 10 * 10)
            else:
                case20[metric] = old[metric] + 1
        for version, case in ((15, case15), (20, case20)):
            whitespace = _whitespace(seed, version, case)
            records.append(("PCB_WHITESPACE_CASE ", whitespace))
            records.append(("PCB_TRACE_GUARD_CASE ", case))
    return records


def _route_lines(records):
    return [_json_line(prefix, row) for prefix, row in records]


def _phase_timing(phase):
    return {
        "status": "PASS",
        "exitCode": 0,
        "elapsedSeconds": 0.0,
        "suiteCount": 3 if phase == "unit" else 7,
        "phase": phase,
        "scope": assess.TIMING_SCOPE,
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }


def _unit_pass_lines(suites):
    return [
        "PASS: " + suites[0][1] + "assertions=31 fixtures=3 evaluator=compactRouted",
        "PASS: " + suites[1][1] + "assertions=84",
        "PASS: " + suites[2][1] +
        "assertions=18 scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false",
    ]


def _route_pass_line(marker):
    return "PASS: " + marker + "roots=2 pairs=2 rows=4 evaluator=compactRouted"


def _phase_fixture(phase, records=None):
    protocol = assess._expected_protocol()
    suites = protocol["unitSuites"] if phase == "unit" else protocol["routesSuites"]
    body = []
    if phase == "unit":
        body.extend(_unit_pass_lines(suites))
    else:
        _need_records = records is not None and len(records) == 56
        if not _need_records:
            raise AssertionError("routes fixture must contain all 56 report rows")
        for group_index, suite in enumerate(suites):
            # Two fixed roots, each with whitespace/case pairs for versions 15 then 20.
            body.extend(_route_lines(records[group_index * 8:(group_index + 1) * 8]))
            body.append(_route_pass_line(suite[1]))
    receipt = "\n".join(body) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _phase_timing(phase), suites


def _report_lines(text):
    return [line for line in text.splitlines() if line.startswith("PCB_")]


def _mutate_report(text, report_index, mutation):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    target = indexes[report_index]
    line = lines[target]
    prefix = next(prefix for prefix in assess.REPORT_PREFIXES if line.startswith(prefix))
    row = json.loads(line[len(prefix):])
    lines[target] = _json_line(prefix, mutation(row))
    return "\n".join(lines) + "\n"


def _remove_report(text, report_index):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    lines.pop(indexes[report_index])
    return "\n".join(lines) + "\n"


def _duplicate_report(text, report_index):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    source = indexes[report_index]
    lines.insert(source + 1, lines[source])
    return "\n".join(lines) + "\n"


def _swap_reports(text, first, second):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    lines[indexes[first]], lines[indexes[second]] = lines[indexes[second]], lines[indexes[first]]
    return "\n".join(lines) + "\n"


def _result_canary(group, name, fn, groups, cases):
    result = fn()
    groups[group] += 1
    cases.append({"group": group, "name": name, "result": "PASS"})
    return result


def _reject_canary(group, name, fn, groups, cases):
    try:
        fn()
    except assess.ReaderError:
        groups[group] += 1
        cases.append({"group": group, "name": name, "result": "REJECTED"})
        return
    raise AssertionError("negative canary unexpectedly passed: " + name)


def _canary_unit_prerequisite(protocol, baseline_data, unit_fixture):
    unit_text, unit_receipt, unit_timing, unit_suites = unit_fixture
    summary = assess.check_phase(unit_text, unit_receipt, unit_timing, unit_suites)
    _manifest, manifest_sha = assess._suite_manifest("unit", protocol)
    inputs = {
        "synthetic-unit-log.txt": hashlib.sha256(unit_text.encode("utf-8")).hexdigest(),
        "synthetic-unit-receipt.txt": hashlib.sha256(unit_receipt.encode("utf-8")).hexdigest(),
        "synthetic-unit-timing.json": hashlib.sha256(
            json.dumps(unit_timing, separators=(",", ":")).encode("utf-8")
        ).hexdigest(),
        "unit-suites.json": manifest_sha,
    }
    return assess._unit_validation_from_evidence(protocol, baseline_data, summary, inputs, manifest_sha)


def run_canaries():
    _proposal, protocol, baseline_data, _baseline_doc, baseline_rows, source_data = assess._load_contract()
    groups = Counter()
    cases = []

    _result_canary("pinned-inputs", "frozen proposal, protocol, baseline and original log load",
                   lambda: True, groups, cases)
    _reject_canary("strict-json", "duplicate JSON object keys",
                   lambda: assess._parse_json(b'{"x":1,"x":2}', "canary"), groups, cases)
    _reject_canary("strict-json", "NaN rejected",
                   lambda: assess._parse_json(b'{"x":NaN}', "canary"), groups, cases)
    _reject_canary("strict-json", "infinite float rejected",
                   lambda: assess._parse_json(b'{"x":1e9999}', "canary"), groups, cases)
    _reject_canary("strict-json", "noncanonical negative zero rejected",
                   lambda: assess._parse_json(b'{"x":-0}', "canary"), groups, cases)
    _reject_canary("strict-json", "invalid UTF-8 rejected",
                   lambda: assess._parse_json(b"\xff", "canary"), groups, cases)
    _reject_canary("pinned-inputs", "protocol digest mismatch",
                   lambda: assess._protocol_from_bytes(assess._repo_file(assess.PROTOCOL_REL) + b" "),
                   groups, cases)
    _reject_canary("pinned-inputs", "proposal content mismatch",
                   lambda: assess._proposal_from_bytes(b"{}"), groups, cases)
    _reject_canary("pinned-inputs", "suite manifest digest mismatch",
                   lambda: assess._require_sha(b"altered", assess.UNIT_SUITES_SHA, "canary manifest"),
                   groups, cases)

    records = _records(baseline_rows)
    route_text, route_receipt, route_timing, route_suites = _phase_fixture("routes", records)
    route_phase = _result_canary(
        "positive-phases", "complete ordered 15/20 route phase with finite metrics",
        lambda: assess.check_phase(route_text, route_receipt, route_timing, route_suites), groups, cases)
    assert route_phase["phase"] == "routes" and route_phase["reportRows"] == 56
    assert route_phase["census"]["nativeAdmission"]["status"] == "PASS"
    assert route_phase["census"]["strictFitness"]["status"] == "PASS"
    assert route_phase["census"]["strictFitness"]["reasons"] == []

    unit_fixture = _phase_fixture("unit")
    unit_phase = _result_canary(
        "positive-phases", "all three unit markers, terminal and cleanup",
        lambda: assess.check_phase(*unit_fixture), groups, cases)
    assert unit_phase["phase"] == "unit" and unit_phase["reportRows"] == 0
    current_unit = _canary_unit_prerequisite(protocol, baseline_data, unit_fixture)
    prereq_bytes = (json.dumps(current_unit, ensure_ascii=True, separators=(",", ":")) + "\n").encode("utf-8")
    _result_canary("unit-prerequisite", "exact current unit-validation recomputation",
                   lambda: assess._validate_unit_prerequisite(prereq_bytes, current_unit), groups, cases)
    stale_unit = copy.deepcopy(current_unit)
    stale_unit["phaseInputs"]["unit.txt"] = "0" * 64
    stale_bytes = (json.dumps(stale_unit, ensure_ascii=True, separators=(",", ":")) + "\n").encode("utf-8")
    _reject_canary("unit-prerequisite", "stale unit-validation input hash rejected",
                   lambda: assess._validate_unit_prerequisite(stale_bytes, current_unit), groups, cases)

    _reject_canary("ordered-reports", "15/20 report order changed",
                   lambda: assess.assess_routes(_swap_reports(route_text, 0, 1), baseline_rows), groups, cases)
    _reject_canary("ordered-reports", "case row schema/order changed",
                   lambda: assess.assess_routes(_mutate_report(route_text, 1, lambda row: {**row, "extra": 1}),
                                                baseline_rows), groups, cases)
    _reject_canary("ordered-reports", "case field order changed",
                   lambda: assess.assess_routes(_mutate_report(route_text, 1,
                       lambda row: dict(reversed(list(row.items())))), baseline_rows), groups, cases)
    _reject_canary("ordered-reports", "duplicate case row",
                   lambda: assess.assess_routes(_duplicate_report(route_text, 1), baseline_rows), groups, cases)
    _reject_canary("ordered-reports", "missing case row",
                   lambda: assess.assess_routes(_remove_report(route_text, 3), baseline_rows), groups, cases)
    _reject_canary("identity", "foreign root identity",
                   lambda: assess.assess_routes(_mutate_report(route_text, 0,
                                                lambda row: {**row, "seed": "17"}), baseline_rows), groups, cases)
    _reject_canary("identity", "noncanonical negative-zero seed rejected",
                   lambda: assess.assess_routes(_mutate_report(route_text, 0,
                                                lambda row: {**row, "seed": "-0"}), baseline_rows),
                   groups, cases)
    _reject_canary("identity", "saved15 non-time mismatch",
                   lambda: assess.assess_routes(_mutate_report(route_text, 1,
                                                lambda row: {**row, "width": row["width"] + 10,
                                                             "area": (row["width"] + 10) * row["height"]}),
                                                baseline_rows),
                   groups, cases)
    _reject_canary("identity", "explicit20 live graph mismatch",
                   lambda: assess.assess_routes(_mutate_report(route_text, 3,
                                                lambda row: {**row, "liveBoardGraphSha256": "a" * 64}), baseline_rows),
                   groups, cases)
    _reject_canary("strict-types", "seed transported as floating point",
                   lambda: assess.assess_routes(route_text.replace('"seed":"-1017"', '"seed":-1017.0', 1),
                                                baseline_rows), groups, cases)
    _reject_canary("strict-types", "boolean metric rejected",
                   lambda: assess.assess_routes(_mutate_report(route_text, 3,
                                                lambda row: {**row, "area": True}), baseline_rows), groups, cases)
    _reject_canary("closed-reports", "unknown PCB report rejected",
                   lambda: assess.assess_routes(route_text.replace("PCB_TRACE_GUARD_CASE ",
                                                "PCB_UNKNOWN_CASE ", 1), baseline_rows), groups, cases)
    _reject_canary("case-timing", "per-case time over 60000ms rejected",
                   lambda: assess.assess_routes(_mutate_report(route_text, 1,
                                                lambda row: {**row, "elapsedMillis": 60001}), baseline_rows),
                   groups, cases)
    _reject_canary("case-timing", "boolean case elapsed rejected",
                   lambda: assess.assess_routes(_mutate_report(route_text, 1,
                                                lambda row: {**row, "elapsedMillis": True}), baseline_rows),
                   groups, cases)

    for metric in assess.METRICS:
        regression_records = _records(baseline_rows, metric_regression=(assess.ROOTS[1], metric))
        regression_text, _receipt, _timing, _suites = _phase_fixture("routes", regression_records)
        fitness = assess.assess_routes(regression_text, baseline_rows)
        assert fitness["nativeAdmission"]["status"] == "PASS"
        assert fitness["strictFitness"]["status"] == "FAIL"
        assert any(item["seed"] == assess.ROOTS[1] and item["metric"] == metric
                   for item in fitness["strictFitness"]["violations"])
        assert len(fitness["strictFitness"]["deltas"]) == 14
        groups["strict-fitness"] += 1
        cases.append({"group": "strict-fitness", "name": metric + " regression retained in assessment",
                      "result": "NATIVE_PASS_STRICT_FAIL"})

    altered_receipt = route_receipt.replace("PCB_TRACE_GUARD_CASE ", "PCB_TRACE_GUARD_CASE  ", 1)
    _reject_canary("phase-receipts", "log and child receipt report mismatch",
                   lambda: assess.check_phase(route_text, altered_receipt, route_timing, route_suites),
                   groups, cases)
    _reject_canary("phase-receipts", "missing terminal PASS rejected",
                   lambda: assess.check_phase(route_text.replace(
                       assess.TERMINAL_TEMPLATE.format(count=7) + "\n", "", 1),
                       route_receipt, route_timing, route_suites), groups, cases)
    _reject_canary("phase-receipts", "missing cleanup marker rejected",
                   lambda: assess.check_phase(route_text.replace(assess.CLEANUP_LINE + "\n", "", 1),
                                              route_receipt, route_timing, route_suites), groups, cases)
    _reject_canary("phase-receipts", "unknown failure or skip rejected",
                   lambda: assess.check_phase(route_text + "SKIPPED: synthetic canary\n", route_receipt,
                                              route_timing, route_suites), groups, cases)
    _reject_canary("timing", "boolean elapsed seconds rejected",
                   lambda: assess.check_phase(route_text, route_receipt,
                       {**route_timing, "elapsedSeconds": True}, route_suites), groups, cases)
    _reject_canary("timing", "negative elapsed seconds rejected",
                   lambda: assess.check_phase(route_text, route_receipt,
                       {**route_timing, "elapsedSeconds": -0.1}, route_suites), groups, cases)
    _reject_canary("timing", "elapsed seconds above unchanged cap rejected",
                   lambda: assess.check_phase(route_text, route_receipt,
                       {**route_timing, "elapsedSeconds": 36000.1}, route_suites), groups, cases)
    _reject_canary("timing", "timing field order changed",
                   lambda: assess.check_phase(route_text, route_receipt,
                       {"phase": "routes", **{k: v for k, v in route_timing.items() if k != "phase"}},
                       route_suites), groups, cases)
    _reject_canary("markers", "route rows detail changed",
                   lambda: assess.check_phase(route_text.replace("rows=4", "rows=3", 1),
                       route_receipt.replace("rows=4", "rows=3", 1), route_timing, route_suites),
                   groups, cases)
    _reject_canary("markers", "unit fixture count changed",
                   lambda: assess.check_phase(
                       unit_fixture[0].replace("fixtures=3", "fixtures=4", 1),
                       unit_fixture[1].replace("fixtures=3", "fixtures=4", 1),
                       unit_fixture[2], unit_fixture[3]), groups, cases)
    _reject_canary("markers", "REL-B identity owner marker changed",
                   lambda: assess.check_phase(
                       unit_fixture[0].replace("ownerRoundTrip=false", "ownerRoundTrip=true", 1),
                       unit_fixture[1].replace("ownerRoundTrip=false", "ownerRoundTrip=true", 1),
                       unit_fixture[2], unit_fixture[3]), groups, cases)
    _reject_canary("markers", "routes suite order changed",
                   lambda: assess.check_phase(route_text.replace(
                       "PASS: " + route_suites[0][1], "PASS: " + route_suites[1][1], 1),
                       route_receipt.replace("PASS: " + route_suites[0][1],
                                             "PASS: " + route_suites[1][1], 1),
                       route_timing, route_suites), groups, cases)

    consumed = {
        "assess.py": hashlib.sha256((HERE / "assess.py").read_bytes()).hexdigest(),
        "check_assessment.py": hashlib.sha256((HERE / "check_assessment.py").read_bytes()).hexdigest(),
        "proposal.json": assess.PROPOSAL_SHA,
        "protocol.json": assess.PROTOCOL_SHA,
        "unit-suites.json": assess.UNIT_SUITES_SHA,
        "routes-suites.json": assess.ROUTES_SUITES_SHA,
        assess.BASELINE_REL: assess.BASELINE_SHA,
        assess.BASELINE_SOURCE_REL: assess.BASELINE_SOURCE_SHA,
    }
    return {
        "schema": 1,
        "status": "PASS",
        "scope": "Synthetic offline-reader canaries only; native runtime evidence NOT RUN.",
        "protocolSha256": assess._sha(assess._repo_file(assess.PROTOCOL_REL)),
        "baselineSha256": assess._sha(baseline_data),
        "baselineSourceSha256": assess._sha(source_data),
        "consumedSha256": consumed,
        "nativeRuntimeEvidence": "NOT_RUN",
        "caseCount": len(cases),
        "groups": [{"name": name, "cases": count} for name, count in sorted(groups.items())],
        "cases": cases,
    }


def main():
    result = run_canaries()
    if RESULTS_PATH.exists():
        raise RuntimeError("refusing to overwrite existing assessment-canary-results.json")
    data = (json.dumps(result, ensure_ascii=True, indent=2, allow_nan=False) + "\n").encode("utf-8")
    with RESULTS_PATH.open("xb") as stream:
        stream.write(data)
    group_summary = ", ".join(item["name"] + "=" + str(item["cases"]) for item in result["groups"])
    print("PASS synthetic assessment canaries: " + group_summary + "; native runtime NOT RUN")


if __name__ == "__main__":
    main()
