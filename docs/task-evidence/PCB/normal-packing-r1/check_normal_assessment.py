"""Pure in-memory positive and negative canaries for assess_normal.py."""

import copy
import json
import re
from pathlib import Path

import assess_normal as assess


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "assessment-canary-results.json"


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=False,
                                separators=(",", ":"), allow_nan=False)


def _digest(letter):
    return letter * 64


def _context():
    return assess._load_contract()


def _accepted(expected, layout_version, area=8000, whitespace=1200):
    dimensions = {
        7000: (100, 70),
        8000: (100, 80),
        8100: (90, 90),
        9000: (100, 90),
    }
    width, height = dimensions.get(area, (100, 80))
    row = {
        "family": expected["family"],
        "profile": "NORMAL_PROFILE",
        "root": expected["root"],
        "seed": expected["root"],
        "status": "ACCEPTED",
        "phase": "FINALIZED",
        "executionPolicy": "NORMAL_SMALL",
        "physicalAdmission": "PHYSICAL@1",
        "elapsedMillis": 123,
        "maxAdvanceMillis": None,
        "graphBefore": _digest("a"),
        "graphAfter": _digest("a"),
        "logicalParts": 10,
        "logicalPads": 20,
        "logicalNets": 12,
        "layoutParts": 10,
        "layoutPads": 20,
        "traceCount": 18,
        "geometrySha256": _digest("b"),
        "layoutAlgorithmVersion": layout_version,
        "outline": {"x": 0, "y": 0, "width": width, "height": height,
                    "area": area},
        "copperManhattanLength": 100,
        "holeCount": 4,
        "viaCount": 2,
        "generationPlacementAttempts": 3,
        "generationRoutingAttempts": 2,
        "generationRoutingExpansions": 300,
        "generationRoutingMillis": 450,
        "routingRecovery": None,
        "whitespace": whitespace,
    }
    return row


def _rejected(expected, phase="ROUTING", message="synthetic rejection"):
    return {
        "family": expected["family"],
        "profile": "NORMAL_PROFILE",
        "root": expected["root"],
        "seed": expected["root"],
        "status": "REJECTED",
        "phase": phase,
        "type": "com.example.SyntheticRejection",
        "message": message,
        "elapsedMillis": 124,
        "maxAdvanceMillis": None,
    }


def _whitespace(expected, area=1200):
    return {
        "seed": expected["root"],
        "version": 1,
        "parts": 10,
        "gridUnits": 10,
        "sampledCells": 10000,
        "packageAccessOccupiedCells": 2000,
        "protectedFreeCells": 4000,
        "protectedFreeFraction": 0.4,
        "largestProtectedEmptyRectangle": {
            "x": 100, "y": 200, "width": 40, "height": area // 40,
            "area": area,
        },
        "scope": assess.WHITESPACE_SCOPE,
    }


def _case_plan(ctx):
    cases, groups = assess._expected_cases(ctx["protocol"])
    baseline = {}
    candidate = {}
    for expected in cases:
        key = (expected["family"], expected["root"])
        accepted = expected["ordinal"] % 3 == 0
        baseline[key] = (_accepted(expected, 16) if accepted else
                         _rejected(expected))
        if accepted:
            area = 7000 if expected["ordinal"] == 0 else 8000
            candidate[key] = _accepted(expected, 17, area=area)
        elif expected["ordinal"] == 1:
            candidate[key] = _accepted(expected, 17, area=8100)
        else:
            candidate[key] = _rejected(expected)
    return cases, groups, baseline, candidate


def _phase_fixture(ctx, phase, rows):
    protocol = ctx["protocol"]
    suites = ctx["unitSuites"] if phase == "unit" else ctx["routeSuites"]
    timing = {
        "status": "PASS",
        "exitCode": 0,
        "elapsedSeconds": 1.25,
        "suiteCount": len(suites),
        "phase": phase,
        "scope": protocol["scope"],
        "terminalCleanupIncluded": True,
        "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }
    if phase == "unit":
        lines = [
            "PASS: PCB connected pocket packing contracts assertions=31 fixtures=4",
            "PASS: PCB normal packing identity contracts assertions=17 packing=2 replay=" +
            protocol["candidateReplayEpoch"],
        ]
        text = "\n".join(lines + [
            assess.TERMINAL_TEMPLATE.format(count=2), assess.CLEANUP_LINE]) + "\n"
        receipt = "\n".join(lines) + "\n"
        return text, receipt, timing, suites
    lines = []
    _cases, groups = assess._expected_cases(protocol)[0:2]
    for group_index, group_cases in enumerate(groups):
        accepted = 0
        rejected = 0
        for expected in group_cases:
            row = copy.deepcopy(rows[(expected["family"], expected["root"])])
            if row["status"] == "ACCEPTED":
                lines.append(_line(protocol["whitespacePrefix"],
                                   _whitespace(expected, row["whitespace"])))
                accepted += 1
            else:
                rejected += 1
            lines.append(_line(protocol["casePrefix"], row))
        suite = suites[group_index][1]
        collector_name = assess._route_suite_short_name(suite)
        lines.append(
            "PASS: " + suite + "cases=" + str(len(group_cases)) +
            " accepted=" + str(accepted) + " rejected=" + str(rejected) +
            " assertions=37 suite=" + json.dumps(
                collector_name, separators=(",", ":")))
    text = "\n".join(lines + [
        assess.TERMINAL_TEMPLATE.format(count=len(suites)), assess.CLEANUP_LINE]) + "\n"
    receipt = "\n".join(lines) + "\n"
    return text, receipt, timing, suites


def _phase(ctx, phase, fixture):
    return assess._phase_frame(
        fixture[0], fixture[1], fixture[2], fixture[3], phase, ctx)


def _copy_fixture(fixture):
    return tuple(copy.deepcopy(item) for item in fixture)


def _mutate_report(fixture, prefix, occurrence, mutation, receipt=True):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        if occurrence >= len(positions):
            raise AssertionError("canary report row is unavailable")
        position = positions[occurrence]
        row = json.loads(lines[position][len(prefix):])
        mutation(row)
        lines[position] = _line(prefix, row)
        return "\n".join(lines) + "\n"

    return (rewrite(text), rewrite(child) if receipt else child,
            copy.deepcopy(timing), copy.deepcopy(suites))


def _drop_whitespace(fixture, expected, area):
    marker = _line(assess._load_contract()["protocol"]["whitespacePrefix"],
                   _whitespace(expected, area))
    return (fixture[0].replace(marker + "\n", "", 1),
            fixture[1].replace(marker + "\n", "", 1),
            fixture[2], fixture[3])


def _adjust_suite_counts(fixture, suite_index, accepted, rejected):
    suite = fixture[3][suite_index][1]
    prefix = "PASS: " + suite

    def rewrite(text):
        lines = text.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        if len(positions) != 1:
            raise AssertionError("canary suite marker is unavailable")
        position = positions[0]
        lines[position] = re.sub(
            r"accepted=[0-9]+ rejected=[0-9]+",
            "accepted=" + str(accepted) + " rejected=" + str(rejected),
            lines[position], count=1)
        return "\n".join(lines) + "\n"

    return (rewrite(fixture[0]), rewrite(fixture[1]),
            fixture[2], fixture[3])


def _replace_event(fixture, occurrence, replacement, receipt=True):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        indexes = [index for index, line in enumerate(lines)
                   if line.startswith("PCB_") or line.startswith("PASS: PCB")]
        index = indexes[occurrence]
        lines[index] = replacement
        return "\n".join(lines) + "\n"

    return (rewrite(text), rewrite(child) if receipt else child,
            copy.deepcopy(timing), copy.deepcopy(suites))


def _append_json_key(fixture, prefix, occurrence, fragment):
    text, child, timing, suites = fixture

    def rewrite(source):
        lines = source.splitlines()
        positions = [index for index, line in enumerate(lines)
                     if line.startswith(prefix)]
        position = positions[occurrence]
        lines[position] = lines[position][:-1] + fragment + "}"
        return "\n".join(lines) + "\n"

    return (rewrite(text), rewrite(child), copy.deepcopy(timing),
            copy.deepcopy(suites))


def _input_seal(ctx, phase):
    protocol = ctx["protocol"]
    return {
        "schema": 1,
        "phase": phase,
        "baseCommit": protocol["baseCommit"],
        "protocolSha256": assess._sha(ctx["protocolData"]),
        "baseMainSealSha256": _digest("c"),
        "sourceCount": 2,
        "files": [
            {"path": "src/a.java", "sha256": _digest("d")},
            {"path": "tests/a.java", "sha256": _digest("e")},
        ],
        "gateOwners": {"reader.py": _digest("f")},
    }


def _record_compare(ctx, baseline_summary, candidate_summary):
    return assess._routes_assessment(
        ctx, baseline_summary,
        {"baseline.txt": _digest("1"), "baseline-receipt.txt": _digest("2"),
         "baseline-timing.json": _digest("3")},
        ctx["routeManifestSha"], candidate_summary,
        {"routes.txt": _digest("4"), "routes-receipt.txt": _digest("5"),
         "routes-timing.json": _digest("6")},
        ctx["routeManifestSha"], _digest("7"))


def _mutate_candidate_summary(summary, ordinal, mutation):
    mutated = copy.deepcopy(summary)
    record = mutated["records"][ordinal]
    mutation(record["row"], record["whitespace"])
    return mutated


def _run_canaries():
    ctx = _context()
    cases, groups, baseline_rows, candidate_rows = _case_plan(ctx)
    baseline_fixture = _phase_fixture(ctx, "baseline", baseline_rows)
    candidate_fixture = _phase_fixture(ctx, "routes", candidate_rows)
    unit_fixture = _phase_fixture(ctx, "unit", None)
    baseline_summary = _phase(ctx, "baseline", baseline_fixture)
    candidate_summary = _phase(ctx, "routes", candidate_fixture)
    results = []
    positives = []
    negatives = []

    def accept(name, callback):
        callback()
        positives.append(name)
        results.append({"case": name, "result": "PASS"})

    def reject(name, callback):
        try:
            value = callback()
            if (isinstance(value, tuple) and len(value) == 4 and
                    isinstance(value[0], str) and isinstance(value[1], str) and
                    isinstance(value[2], dict) and isinstance(value[3], list)):
                _phase(ctx, "baseline", value)
        except assess.ReaderError:
            negatives.append(name)
            results.append({"case": name, "result": "REJECTED"})
            return
        raise AssertionError("reader accepted rejected canary: " + name)

    def reject_fitness(name, callback):
        value = callback()
        if (value.get("fitnessStatus") != "FAIL" or
                value.get("assessmentStatus") != "FAIL"):
            raise AssertionError("reader accepted fitness regression: " + name)
        negatives.append(name)
        results.append({"case": name, "result": "REJECTED"})

    accept("valid baseline 44-case matrix", lambda: baseline_summary)
    accept("valid unit phase with two exact markers",
           lambda: _phase(ctx, "unit", unit_fixture))
    accept("valid candidate matrix with retained rejections and strict gain",
           lambda: _record_compare(ctx, baseline_summary, candidate_summary))
    seal = _input_seal(ctx, "baseline")
    accept("valid baseline input seal", lambda: assess._validate_input_seal(
        json.dumps(seal, separators=(",", ":")).encode("utf-8"), "baseline", ctx))
    candidate_seal = _input_seal(ctx, "candidate")
    accept("valid candidate input seal", lambda: assess._validate_input_seal(
        json.dumps(candidate_seal, separators=(",", ":")).encode("utf-8"),
        "candidate", ctx))
    positive_fixture_matrix = len(cases)
    results.append({
        "case": "literal 44-case reader fixture corpus",
        "result": "PASS",
        "accepted": sum(row["status"] == "ACCEPTED" for row in baseline_rows.values()),
        "rejected": sum(row["status"] == "REJECTED" for row in baseline_rows.values()),
        "cases": positive_fixture_matrix,
    })
    positives.append("literal 44-case reader fixture corpus")

    protocol = ctx["protocol"]
    accept("all eight actual collector suite names", lambda: (
        assess._need(
            tuple(assess._route_suite_short_name(item[1])
                  for item in protocol["routeSuites"]) ==
            assess.ROUTE_SUITE_NAMES,
            "route collector suite names are not the eight Java values")))
    case_prefix = protocol["casePrefix"]
    whitespace_prefix = protocol["whitespacePrefix"]

    reject("duplicate case key", lambda: _append_json_key(
        baseline_fixture, case_prefix, 0, ',"family":"' + cases[0]["family"] + '"'))
    reject("unexpected accepted case key", lambda: _append_json_key(
        baseline_fixture, case_prefix, 0, ',"unexpected":1'))
    reject("NaN case value", lambda: _replace_event(
        baseline_fixture, 0,
        _line(whitespace_prefix, dict(_whitespace(cases[0]))).replace(
            "0.4", "NaN")))
    reject("strict bool versus integer", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("logicalParts", True)))
    reject("unknown family", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("family", "UNKNOWN_FAMILY")))
    reject("root order drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("root", "17")))
    reject("signed long canonical drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("root", "-0")))
    reject("seed identity drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("seed", "1")))
    reject("unknown case order", lambda: _replace_event(
        baseline_fixture, 0,
        _line(whitespace_prefix, _whitespace(cases[1]))))
    reject("missing baseline case", lambda: _phase(
        ctx, "baseline", tuple([
            baseline_fixture[0].replace(
                _line(case_prefix, baseline_rows[(cases[0]["family"],
                                                   cases[0]["root"])]), "", 1),
            baseline_fixture[1].replace(
                _line(case_prefix, baseline_rows[(cases[0]["family"],
                                                   cases[0]["root"])]), "", 1),
            baseline_fixture[2], baseline_fixture[3]])))
    reject("accepted case without whitespace", lambda: _replace_event(
        baseline_fixture, 0,
        _line(case_prefix, baseline_rows[(cases[0]["family"], cases[0]["root"])])))
    reject("whitespace before rejected case", lambda: _replace_event(
        _copy_fixture(baseline_fixture), 0,
        _line(whitespace_prefix, _whitespace(cases[1]))))
    reject("whitespace version drift", lambda: _mutate_report(
        baseline_fixture, whitespace_prefix, 0,
        lambda row: row.__setitem__("version", 2)))
    reject("whitespace grid drift", lambda: _mutate_report(
        baseline_fixture, whitespace_prefix, 0,
        lambda row: row.__setitem__("gridUnits", 9)))
    reject("whitespace seed drift", lambda: _mutate_report(
        baseline_fixture, whitespace_prefix, 0,
        lambda row: row.__setitem__("seed", "17")))
    reject("whitespace metric drift", lambda: _mutate_report(
        baseline_fixture, whitespace_prefix, 0,
        lambda row: row["largestProtectedEmptyRectangle"].__setitem__("area", 1300)))
    reject("whitespace fraction drift", lambda: _mutate_report(
        baseline_fixture, whitespace_prefix, 0,
        lambda row: row.__setitem__("protectedFreeFraction", 0.3)))
    reject("duplicate whitespace key", lambda: _append_json_key(
        baseline_fixture, whitespace_prefix, 0, ',"seed":"0"'))
    reject("unknown whitespace field", lambda: _append_json_key(
        baseline_fixture, whitespace_prefix, 0, ',"unexpected":1'))
    reject("graph before/after mismatch", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("graphAfter", _digest("9"))))
    reject("foreign graph digest", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("graphBefore", "g" * 64)))
    reject("layout population drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("layoutParts", 11)))
    reject("outline area drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row["outline"].__setitem__("area", 9000)))
    reject("outline negative width", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row["outline"].__setitem__("width", -1)))
    reject("geometry digest drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("geometrySha256", "X" * 64)))
    reject("baseline layout version drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("layoutAlgorithmVersion", 17)))
    reject("max advance bound drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("maxAdvanceMillis", 5001)))
    reject("accepted elapsed bound drift", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("elapsedMillis", 60001)))
    reject("route suite PASS before group", lambda: _replace_event(
        baseline_fixture, 0,
        "PASS: " + protocol["routeSuites"][0][1] +
        "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
        assess._route_suite_short_name(protocol["routeSuites"][0][1]) + "\""))
    reject("cross-group suite boundary", lambda: _replace_event(
        baseline_fixture, 12,
        "PASS: " + protocol["routeSuites"][1][1] +
        "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
        assess._route_suite_short_name(protocol["routeSuites"][1][1]) + "\""))
    reject("suite count drift", lambda: _replace_event(
        baseline_fixture, 12,
        "PASS: " + protocol["routeSuites"][0][1] +
        "cases=11 accepted=4 rejected=7 assertions=37 suite=\"" +
        assess._route_suite_short_name(protocol["routeSuites"][0][1]) + "\""))
    reject("suite JSON identity drift", lambda: _replace_event(
        baseline_fixture, 12,
        "PASS: " + protocol["routeSuites"][0][1] +
        "cases=12 accepted=4 rejected=8 assertions=37 suite=\"wrong\""))
    reject("full marker used as suite JSON", lambda: _replace_event(
        baseline_fixture, 12,
        "PASS: " + protocol["routeSuites"][0][1] +
        "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
        protocol["routeSuites"][0][1] + "\""))
    reject("duplicate suite PASS", lambda: _copy_fixture((
        baseline_fixture[0].replace(
            "PASS: " + protocol["routeSuites"][0][1] +
            "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
            assess._route_suite_short_name(protocol["routeSuites"][0][1]) + "\"\n",
            "PASS: " + protocol["routeSuites"][0][1] +
            "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
            assess._route_suite_short_name(protocol["routeSuites"][0][1]) +
            "\"\n" +
            "PASS: " + protocol["routeSuites"][0][1] +
            "cases=12 accepted=4 rejected=8 assertions=37 suite=\"" +
            assess._route_suite_short_name(protocol["routeSuites"][0][1]) +
            "\"\n"),
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3])))
    reject("unknown PCB prefix", lambda: _replace_event(
        baseline_fixture, 0, "PCB_FOREIGN {}"))
    reject("stdout receipt report mismatch", lambda: _mutate_report(
        baseline_fixture, case_prefix, 0,
        lambda row: row.__setitem__("graphAfter", _digest("9")), receipt=False))
    reject("terminal cleanup order drift", lambda: tuple([
        baseline_fixture[0].replace(assess.CLEANUP_LINE + "\n",
                                    assess.CLEANUP_LINE + "\n" +
                                    assess.TERMINAL_TEMPLATE.format(count=8) + "\n"),
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3]]))
    reject("missing cleanup marker", lambda: tuple([
        baseline_fixture[0].replace(assess.CLEANUP_LINE + "\n", "", 1),
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3]]))
    reject("trailing output after cleanup", lambda: tuple([
        baseline_fixture[0] + "trailing native noise\n",
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3]]))
    reject("skip output", lambda: tuple([
        baseline_fixture[0].replace(
            assess.TERMINAL_TEMPLATE.format(count=8),
            "SKIPPED normal census case\n" +
            assess.TERMINAL_TEMPLATE.format(count=8), 1),
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3]]))
    reject("timeout output", lambda: tuple([
        baseline_fixture[0].replace(
            assess.TERMINAL_TEMPLATE.format(count=8),
            "TIMEOUT normal census case\n" +
            assess.TERMINAL_TEMPLATE.format(count=8), 1),
        baseline_fixture[1], baseline_fixture[2], baseline_fixture[3]]))
    reject("timing NaN", lambda: tuple([
        baseline_fixture[0], baseline_fixture[1],
        dict(baseline_fixture[2], elapsedSeconds=float("nan")),
        baseline_fixture[3]]))
    reject("timing schema key drift", lambda: tuple([
        baseline_fixture[0], baseline_fixture[1],
        dict(list(baseline_fixture[2].items()) + [("unexpected", 1)]),
        baseline_fixture[3]]))
    reject("timing scope drift", lambda: tuple([
        baseline_fixture[0], baseline_fixture[1],
        dict(baseline_fixture[2], scope="wrong"), baseline_fixture[3]]))
    reject("unit marker packing drift", lambda: _phase(
        ctx, "unit", (unit_fixture[0].replace("packing=2", "packing=1"),
                       unit_fixture[1].replace("packing=2", "packing=1"),
                       unit_fixture[2], unit_fixture[3])))
    reject("unit leaked report", lambda: _phase(
        ctx, "unit", (unit_fixture[0].replace(
            assess.TERMINAL_TEMPLATE.format(count=2),
            _line(protocol["casePrefix"], _accepted(cases[0], 17)) + "\n" +
            assess.TERMINAL_TEMPLATE.format(count=2)),
                       unit_fixture[1], unit_fixture[2], unit_fixture[3])))
    reject("input seal phase drift", lambda: assess._validate_input_seal(
        json.dumps(dict(seal, phase="candidate"), separators=(",", ":")).encode(),
        "baseline", ctx))
    reject("input seal source count drift", lambda: assess._validate_input_seal(
        json.dumps(dict(seal, sourceCount=3), separators=(",", ":")).encode(),
        "baseline", ctx))
    reject("input seal path traversal", lambda: assess._validate_input_seal(
        json.dumps(dict(seal, files=[{"path": "../secret", "sha256": _digest("d")},
                                     seal["files"][1]]), separators=(",", ":")).encode(),
        "baseline", ctx))
    reject("input seal unsorted paths", lambda: assess._validate_input_seal(
        json.dumps(dict(seal, files=list(reversed(seal["files"]))),
                    separators=(",", ":")).encode(), "baseline", ctx))
    reject("rejected case whitespace substitution", lambda: _replace_event(
        baseline_fixture, 1, _line(whitespace_prefix, _whitespace(cases[1]))))
    reject("rejected case unknown phase", lambda: _mutate_report(
        baseline_fixture, case_prefix, 1,
        lambda row: row.__setitem__("phase", "OTHER")))
    reject("rejected case missing type", lambda: _mutate_report(
        baseline_fixture, case_prefix, 1,
        lambda row: row.pop("type")))
    reject_fitness("candidate lost baseline admission", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _adjust_suite_counts(
            _drop_whitespace(
                _mutate_report(
                    candidate_fixture, case_prefix, 0,
                    lambda row: (row.clear(),
                                 row.update(_rejected(cases[0])))),
                cases[0], candidate_rows[(cases[0]["family"],
                                         cases[0]["root"])]["whitespace"]),
            0, 4, 8))))
    reject_fitness("candidate area regression", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: row["outline"].update(
                {"width": 100, "height": 90, "area": 9000})))))
    reject_fitness("candidate copper regression", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: row.__setitem__("copperManhattanLength", 101)))))
    reject_fitness("candidate via regression", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: row.__setitem__("viaCount", 3)))))
    reject_fitness("candidate whitespace regression", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            _mutate_report(candidate_fixture, case_prefix, 0,
                lambda row: row.__setitem__("whitespace", 1240)),
            whitespace_prefix, 0,
            lambda row: row["largestProtectedEmptyRectangle"].update(
                {"height": 31, "area": 1240})))))
    reject_fitness("candidate no strict area gain", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: row["outline"].update(
                {"width": 100, "height": 80, "area": 8000})))))
    reject("candidate matched graph drift", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: (
                row.__setitem__("graphBefore", _digest("9")),
                row.__setitem__("graphAfter", _digest("9")))))))
    reject("candidate fixed rejection type drift", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 2,
            lambda row: row.__setitem__("type", "different.Type")))))
    reject("candidate source identity drift", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", _mutate_report(
            candidate_fixture, case_prefix, 0,
            lambda row: row.__setitem__("profile", "OTHER_PROFILE")))))
    reject("candidate missing case", lambda: _record_compare(
        ctx, baseline_summary,
        _phase(ctx, "routes", tuple([
            candidate_fixture[0].replace(
                _line(protocol["casePrefix"], candidate_rows[
                    (cases[0]["family"], cases[0]["root"])]), "", 1),
            candidate_fixture[1], candidate_fixture[2], candidate_fixture[3]]))))
    reject("stale unit prerequisite", lambda: assess._validate_unit_prerequisite(
        b"{}", assess._unit_validation(
            ctx, _phase(ctx, "unit", unit_fixture),
            {"unit.txt": _digest("1")}, ctx["unitManifestSha"])))

    if len(negatives) < 40:
        raise AssertionError("focused negative canary corpus is unexpectedly small")
    return {
        "schema": 1,
        "status": "PASS",
        "fixtureCount": len(cases),
        "readerFixtureCount": positive_fixture_matrix,
        "canaryCount": len(results),
        "positiveCount": len(positives),
        "negativeCount": len(negatives),
        "consumedSha256": {
            "assess_normal.py": assess._sha((HERE / "assess_normal.py").read_bytes()),
            "check_normal_assessment.py": assess._sha(
                (HERE / "check_normal_assessment.py").read_bytes()),
            "protocol.json": assess._sha(ctx["protocolData"]),
            "scope.md": assess._sha(ctx["scopeData"]),
            "sourceHelper.java": assess.SOURCE_HELPER_SHA,
            "unit-suites.json": ctx["unitManifestSha"],
            "routes-suites.json": ctx["routeManifestSha"],
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
    print("PASS normal-packing assessment canaries; cases=" +
          str(result["canaryCount"]) + " rejected=" + str(result["negativeCount"]))


if __name__ == "__main__":
    main()
