"""Synthetic canaries for assess.py. This script never runs native code."""

import copy
import hashlib
import json
from collections import Counter
from pathlib import Path

import assess


HERE = Path(__file__).resolve().parent


def _line(prefix, row):
    return prefix + json.dumps(row, ensure_ascii=True, separators=(",", ":"), allow_nan=False)


def _event(seed, call, candidate, axis, kind, **fields):
    return {"seed": seed, "call": call, "candidate": candidate, "axis": axis,
            "event": kind, **fields}


def _root_reports(seed, root_index, baseline_row):
    candidate = root_index % 6
    dims = (1000, 800)
    rows = []

    def add(kind, row):
        rows.append(("PCB_COMPACTOR_CUT_EVENT ", row))

    add("BEGIN", _event(seed, 0, candidate, 0, "BEGIN", beforeWidth=dims[0], beforeHeight=dims[1], traces=44, holes=12))
    add("CUT", _event(seed, 0, candidate, 0, "CUT", index=0, coordinate=-10, amount=10))
    add("CUT", _event(seed, 0, candidate, 0, "CUT", index=1, coordinate=0, amount=20))
    add("END", _event(seed, 0, candidate, 0, "END", cutCount=2, removed=30, outcome="ACCEPTED", reason="", afterWidth=970, afterHeight=800))
    add("BEGIN", _event(seed, 1, candidate, 1, "BEGIN", beforeWidth=970, beforeHeight=800, traces=44, holes=12))
    add("END", _event(seed, 1, candidate, 1, "END", cutCount=0, removed=0, outcome="NOCUT", reason="", afterWidth=970, afterHeight=800))

    second_candidate = (candidate + 1) % 6
    add("BEGIN", _event(seed, 2, second_candidate, 0, "BEGIN", beforeWidth=1000, beforeHeight=800, traces=44, holes=12))
    add("CUT", _event(seed, 2, second_candidate, 0, "CUT", index=0, coordinate=100, amount=10))
    add("END", _event(seed, 2, second_candidate, 0, "END", cutCount=1, removed=10, outcome="PLACEMENT_REJECTED", reason="MISSING_COMPONENT", afterWidth=1000, afterHeight=800))
    add("BEGIN", _event(seed, 3, second_candidate, 1, "BEGIN", beforeWidth=1000, beforeHeight=800, traces=44, holes=12))
    add("CUT", _event(seed, 3, second_candidate, 1, "CUT", index=0, coordinate=200, amount=20))
    add("END", _event(seed, 3, second_candidate, 1, "END", cutCount=1, removed=20, outcome="QUALITY_REJECTED", reason="BENDS", afterWidth=1000, afterHeight=800))

    whitespace = {
        "seed": seed, "version": 15, "parts": baseline_row["parts"], "gridUnits": 10,
        "sampledCells": 1000, "packageAccessOccupiedCells": 200,
        "protectedFreeCells": 800, "protectedFreeFraction": 0.8,
        "largestProtectedEmptyRectangle": 500, "scope": "synthetic reader canary",
    }
    case = dict(baseline_row)
    case["elapsedMillis"] = 0
    rows.append(("PCB_WHITESPACE_CASE ", whitespace))
    rows.append(("PCB_COMPACTOR_CUT_CASE ", case))
    return rows


def _reports_for_all(rows):
    grouped = []
    for index, (seed, baseline) in enumerate(zip(assess.ROOTS, rows)):
        grouped.append(_root_reports(seed, index, baseline))
    return grouped


def _phase_timing(phase):
    count = 1 if phase == "unit" else 7
    return {
        "status": "PASS", "exitCode": 0, "elapsedSeconds": 3.25,
        "suiteCount": count, "phase": phase, "scope": assess.TIMING_SCOPE,
        "terminalCleanupIncluded": True, "cleanupDuration": "NOT_SEPARATELY_MEASURED",
    }


def _pass_line(phase, marker):
    if phase == "unit":
        return "PASS: " + marker + "assertions=19 fixtures=3 evaluator=compactRouted"
    return "PASS: " + marker + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true"


def _phase_fixture(phase, grouped_reports=None):
    protocol = assess._expected_protocol()
    suites = protocol["unitSuites"] if phase == "unit" else protocol["routesSuites"]
    body = []
    if phase == "unit":
        body.append(_pass_line(phase, suites[0][1]))
    else:
        for suite_index, suite in enumerate(suites):
            for root_reports in grouped_reports[suite_index * 2:suite_index * 2 + 2]:
                body.extend(_line(prefix, row) for prefix, row in root_reports)
            body.append(_pass_line(phase, suite[1]))
    receipt = "\n".join(body) + "\n"
    terminal = assess.TERMINAL_TEMPLATE.format(count=len(suites))
    text = receipt + terminal + "\n" + assess.CLEANUP_LINE + "\n"
    return text, receipt, _phase_timing(phase), suites


def _report_lines(text):
    return [line for line in text.splitlines() if line.startswith("PCB_")]


def _mutate_report(text, report_index, mutation):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    target_index = indexes[report_index]
    line = lines[target_index]
    prefix = next(prefix for prefix in assess.REPORT_PREFIXES if line.startswith(prefix))
    if mutation is None:
        lines.pop(target_index)
        return "\n".join(lines) + "\n"
    row = json.loads(line[len(prefix):])
    changed = mutation(row)
    lines[target_index] = _line(prefix, changed)
    return "\n".join(lines) + "\n"


def _duplicate_report(text, report_index):
    lines = text.splitlines()
    indexes = [index for index, line in enumerate(lines) if line.startswith("PCB_")]
    source_index = indexes[report_index]
    lines.insert(source_index + 1, lines[source_index])
    return "\n".join(lines) + "\n"


def run_canaries():
    protocol, baseline_data, _baseline_doc, baseline_rows = assess._load_contract()
    groups = Counter()
    cases = []

    def positive(group, name, fn):
        result = fn()
        groups[group] += 1
        cases.append({"group": group, "name": name, "result": "PASS"})
        return result

    def rejected(group, name, fn):
        try:
            fn()
        except assess.ReaderError:
            groups[group] += 1
            cases.append({"group": group, "name": name, "result": "REJECTED"})
            return
        raise AssertionError("negative canary unexpectedly passed: " + name)

    def change_pass_pair(text, receipt, old_line, new_line):
        assert text.count(old_line) == 1 and receipt.count(old_line) == 1
        return text.replace(old_line, new_line, 1), receipt.replace(old_line, new_line, 1)

    positive("pinned-inputs", "protocol and baseline load", lambda: (protocol, baseline_data, baseline_rows))
    rejected("pinned-inputs", "protocol digest mismatch",
             lambda: assess._protocol_from_bytes(assess._repo_file(assess.PROTOCOL_REL) + b" "))
    changed_protocol = dict(protocol)
    changed_protocol["scope"] = "altered"
    rejected("pinned-inputs", "protocol content mismatch",
             lambda: assess._protocol_from_bytes(json.dumps(changed_protocol).encode("utf-8"), verify_digest=False))
    rejected("pinned-inputs", "generic input digest mismatch",
             lambda: assess._require_sha(b"sealed", "0" * 64, "canary input"))

    grouped_reports = _reports_for_all(baseline_rows)
    valid_route_text, valid_route_receipt, valid_route_timing, route_suites = _phase_fixture("routes", grouped_reports)
    valid_summary = positive("event-census", "14-root complete paired-axis census",
                             lambda: assess.assess_events(valid_route_text, baseline_rows))
    assert valid_summary["caseRows"] == 14
    assert valid_summary["eventCount"] == 168 and valid_summary["axisCalls"] == 56
    assert valid_summary["proposedRemovedTotal"] == 840
    assert "overallShrinkage" not in valid_summary
    assert valid_summary["roots"][0]["outcomes"] == {
        "NOCUT": 1, "ACCEPTED": 1, "PLACEMENT_REJECTED": 1, "QUALITY_REJECTED": 1,
    }
    assert valid_summary["roots"][0]["reasons"] == {"BENDS": 1, "MISSING_COMPONENT": 1}
    assert valid_summary["roots"][0]["proposedRemoved"] == 60

    rejected("event-lifecycle", "missing END", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 3, None), baseline_rows))

    def swap_first_begin_cut():
        lines = valid_route_text.splitlines()
        indexes = [i for i, line in enumerate(lines) if line.startswith("PCB_")]
        lines[indexes[0]], lines[indexes[1]] = lines[indexes[1]], lines[indexes[0]]
        return assess.assess_events("\n".join(lines) + "\n", baseline_rows)

    rejected("event-order", "CUT before BEGIN", swap_first_begin_cut)
    rejected("event-order", "duplicate cut index", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 2, lambda row: {**row, "index": 0}), baseline_rows))
    rejected("event-order", "non-increasing coordinates", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 2, lambda row: {**row, "coordinate": -10}), baseline_rows))
    rejected("event-order", "noncanonical negative zero", lambda: assess.assess_events(
        valid_route_text.replace('"coordinate":-10', '"coordinate":-0', 1), baseline_rows))
    rejected("event-order", "coordinate exceeds source bound", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 1, lambda row: {**row, "coordinate": 100000001}), baseline_rows))
    rejected("event-order", "foreign root event", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 0, lambda row: {**row, "seed": "17"}), baseline_rows))
    rejected("event-order", "missing root lifecycle", lambda: assess.assess_events(
        "\n".join(line for line in valid_route_text.splitlines()
                    if not line.startswith("PCB_COMPACTOR_CUT_EVENT ")) + "\n", baseline_rows))
    rejected("event-order", "axis pair candidate mismatch", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 4, lambda row: {**row, "candidate": (row["candidate"] + 1) % 6}), baseline_rows))
    rejected("event-order", "axis one input dimensions mismatch", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 4, lambda row: {**row, "beforeWidth": 971}), baseline_rows))

    rejected("event-arithmetic", "cut sum mismatch", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 3, lambda row: {**row, "removed": 29}), baseline_rows))
    rejected("event-arithmetic", "dimension equation mismatch", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 3, lambda row: {**row, "afterWidth": 969}), baseline_rows))
    rejected("event-arithmetic", "off-grid amount", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 1, lambda row: {**row, "amount": 11}), baseline_rows))
    rejected("event-arithmetic", "boolean integer field", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 0, lambda row: {**row, "beforeWidth": True}), baseline_rows))
    rejected("event-arithmetic", "dimension over bound", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 0, lambda row: {**row, "beforeWidth": 16001}), baseline_rows))

    rejected("event-outcomes", "unlisted placement rejection reason", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 8, lambda row: {**row, "reason": "OTHER"}), baseline_rows))
    rejected("event-outcomes", "accepted outcome with rejection reason", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 3, lambda row: {**row, "reason": "BENDS"}), baseline_rows))
    rejected("event-outcomes", "outcome and cut-count mismatch", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 5, lambda row: {**row, "outcome": "ACCEPTED"}), baseline_rows))
    rejected("event-outcomes", "duplicate JSON field", lambda: assess.assess_events(
        valid_route_text.replace('"seed":"-1017","call":0', '"seed":"-1017","seed":"-1017","call":0', 1),
        baseline_rows))
    rejected("event-outcomes", "unknown PCB report prefix", lambda: assess.assess_events(
        valid_route_text.replace("PCB_COMPACTOR_CUT_EVENT ", "PCB_UNEXPECTED_EVENT ", 1), baseline_rows))

    rejected("case-proof", "non-time case width changed", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 13, lambda row: {**row, "width": row["width"] + 1}), baseline_rows))
    rejected("case-proof", "case elapsed is boolean", lambda: assess.assess_events(
        _mutate_report(valid_route_text, 13, lambda row: {**row, "elapsedMillis": True}), baseline_rows))
    rejected("case-proof", "duplicate case row", lambda: assess.assess_events(
        _duplicate_report(valid_route_text, 13), baseline_rows))

    route_phase = positive("phase-receipts", "routes log/receipt/timing/suite PASS",
                           lambda: assess.check_phase(valid_route_text, valid_route_receipt,
                                                     valid_route_timing, route_suites))
    assert route_phase["phase"] == "routes" and route_phase["reportRows"] == 196
    unit_text, unit_receipt, unit_timing, unit_suites = _phase_fixture("unit")
    unit_phase = positive("phase-receipts", "unit log/receipt/timing/suite PASS",
                          lambda: assess.check_phase(unit_text, unit_receipt, unit_timing, unit_suites))
    assert unit_phase["phase"] == "unit" and unit_phase["reportRows"] == 0

    unit_pass = _pass_line("unit", unit_suites[0][1])
    unit_bare = "PASS: " + unit_suites[0][1]
    bad_text, bad_receipt = change_pass_pair(unit_text, unit_receipt, unit_pass, unit_bare)
    rejected("phase-receipts", "unit bare PASS detail", lambda: assess.check_phase(
        bad_text, bad_receipt, unit_timing, unit_suites))
    bad_text, bad_receipt = change_pass_pair(unit_text, unit_receipt, unit_pass,
                                              unit_pass.replace("fixtures=3", "fixtures=4"))
    rejected("phase-receipts", "unit wrong fixture count", lambda: assess.check_phase(
        bad_text, bad_receipt, unit_timing, unit_suites))
    bad_text, bad_receipt = change_pass_pair(unit_text, unit_receipt, unit_pass,
                                              unit_pass.replace("evaluator=compactRouted", "evaluator=mediumPlace"))
    rejected("phase-receipts", "unit wrong evaluator", lambda: assess.check_phase(
        bad_text, bad_receipt, unit_timing, unit_suites))
    route_pass = _pass_line("routes", route_suites[0][1])
    route_bare = "PASS: " + route_suites[0][1]
    bad_text, bad_receipt = change_pass_pair(valid_route_text, valid_route_receipt,
                                              route_pass, route_bare)
    rejected("phase-receipts", "routes bare PASS detail", lambda: assess.check_phase(
        bad_text, bad_receipt, valid_route_timing, route_suites))

    rejected("phase-receipts", "routes suite marker detail changed", lambda: assess.check_phase(
        valid_route_text.replace("rows=2", "rows=3", 1), valid_route_receipt.replace("rows=2", "rows=3", 1),
        valid_route_timing, route_suites))
    rejected("phase-receipts", "suite order changed", lambda: assess.check_phase(
        valid_route_text.replace(
            "PASS: " + route_suites[0][1] + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true",
            "PASS: " + route_suites[1][1] + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true", 1),
        valid_route_receipt.replace(
            "PASS: " + route_suites[0][1] + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true",
            "PASS: " + route_suites[1][1] + "roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true", 1),
        valid_route_timing, route_suites))
    rejected("phase-receipts", "terminal receipt diverges", lambda: assess.check_phase(
        valid_route_text, valid_route_receipt.replace("PCB_COMPACTOR_CUT_CASE ", "PCB_BAD_CASE ", 1),
        valid_route_timing, route_suites))
    rejected("phase-receipts", "unknown failure output", lambda: assess.check_phase(
        valid_route_text + "FAIL: synthetic failure\n", valid_route_receipt,
        valid_route_timing, route_suites))
    rejected("phase-receipts", "unit phase emits corpus reports", lambda: assess.check_phase(
        unit_text.replace(assess.TERMINAL_TEMPLATE.format(count=1),
                          "PCB_WHITESPACE_CASE {\"seed\":\"-1017\",\"version\":15}\n" +
                          assess.TERMINAL_TEMPLATE.format(count=1)),
        unit_receipt, unit_timing, unit_suites))

    rejected("cleanup-boundary", "terminal PASS missing", lambda: assess.check_phase(
        valid_route_text.replace(assess.TERMINAL_TEMPLATE.format(count=7) + "\n", "", 1),
        valid_route_receipt, valid_route_timing, route_suites))
    rejected("cleanup-boundary", "cleanup marker missing", lambda: assess.check_phase(
        valid_route_text.replace(assess.CLEANUP_LINE + "\n", "", 1),
        valid_route_receipt, valid_route_timing, route_suites))
    rejected("cleanup-boundary", "cleanup before terminal", lambda: assess.check_phase(
        valid_route_text.replace(assess.TERMINAL_TEMPLATE.format(count=7) + "\n" + assess.CLEANUP_LINE,
                                 assess.CLEANUP_LINE + "\n" + assess.TERMINAL_TEMPLATE.format(count=7), 1),
        valid_route_receipt, valid_route_timing, route_suites))
    rejected("cleanup-boundary", "outer terminal leaked into child receipt", lambda: assess.check_phase(
        valid_route_text, valid_route_receipt + assess.TERMINAL_TEMPLATE.format(count=7) + "\n",
        valid_route_timing, route_suites))
    rejected("cleanup-boundary", "log receipt report rows differ", lambda: assess.check_phase(
        valid_route_text, valid_route_receipt.replace('"coordinate":-10', '"coordinate":-11', 1),
        valid_route_timing, route_suites))
    rejected("cleanup-boundary", "report after terminal PASS", lambda: assess.check_phase(
        valid_route_text.replace(assess.CLEANUP_LINE + "\n",
                                 "PCB_COMPACTOR_CUT_EVENT {}\n" + assess.CLEANUP_LINE + "\n", 1),
        valid_route_receipt, valid_route_timing, route_suites))

    for name, change in (
        ("native scope changed", lambda timing: timing.update(scope="different")),
        ("nonzero process exit", lambda timing: timing.update(exitCode=2)),
        ("cleanup omitted", lambda timing: timing.update(terminalCleanupIncluded=False)),
        ("elapsed boolean", lambda timing: timing.update(elapsedSeconds=True)),
    ):
        bad_timing = copy.deepcopy(valid_route_timing)
        change(bad_timing)
        rejected("timing-boundary", name, lambda bad_timing=bad_timing: assess.check_phase(
            valid_route_text, valid_route_receipt, bad_timing, route_suites))

    return {
        "schema": 1,
        "status": "PASS",
        "scope": "Synthetic offline reader canaries only; native runtime evidence NOT RUN.",
        "protocolSha256": assess._sha(assess._repo_file(assess.PROTOCOL_REL)),
        "baselineSha256": assess._sha(baseline_data),
        "readerSha256": hashlib.sha256((HERE / "assess.py").read_bytes()).hexdigest(),
        "nativeRuntimeEvidence": "NOT_RUN",
        "caseCount": len(cases),
        "groups": [{"name": name, "cases": count} for name, count in sorted(groups.items())],
        "cases": cases,
    }


def main():
    result = run_canaries()
    output = HERE / "assessment-canary-results.json"
    output.write_text(json.dumps(result, ensure_ascii=True, indent=2, allow_nan=False) + "\n",
                      encoding="utf-8", newline="\n")
    group_summary = ", ".join(item["name"] + "=" + str(item["cases"]) for item in result["groups"])
    print("PASS synthetic assessment canaries: " + group_summary +
          "; native runtime NOT RUN")


if __name__ == "__main__":
    main()
