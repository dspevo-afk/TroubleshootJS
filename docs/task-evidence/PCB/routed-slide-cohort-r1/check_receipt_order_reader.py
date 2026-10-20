"""Pure canaries for the post-native per-pair route framing adapter."""

import hashlib
import json
import sys
from pathlib import Path

import check_assessment as original_check
import receipt_order_reader as reader


HERE = Path(__file__).resolve().parent
RESULTS_PATH = HERE / "receipt-order-canary-results.json"
ORIGINAL_RESULTS_PATH = HERE / "assessment-canary-results.json"


def _read(path):
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AssertionError("cannot read canary input: " + str(path)) from exc


def _sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _need(condition, message):
    if not condition:
        raise AssertionError(message)


def _route_inputs():
    contract = reader._load_contract()
    protocol = contract["protocol"]
    text = (HERE / "routes.txt").read_text(encoding="utf-8-sig")
    receipt = (HERE / "routes-receipt.txt").read_text(encoding="utf-8-sig")
    timing = reader._parse_json(
        (HERE / "routes-timing.json").read_bytes(), "routes timing")
    suites, _manifest_sha = reader._load_suite_manifest("routes", protocol)
    return contract, protocol, text, receipt, timing, suites


def _report_and_suite_positions(lines, terminal):
    reports = [index for index, line in enumerate(lines)
               if line.startswith("PCB_")]
    suites = [index for index, line in enumerate(lines)
              if line.startswith("PASS: ") and line != terminal]
    return reports, suites


def _segments(lines, terminal):
    report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    _need(len(report_positions) == 42 and len(suite_positions) == 7,
          "actual route fixture is not seven six-report pairs")
    result = []
    previous = -1
    for upper in suite_positions:
        segment = [position for position in report_positions
                   if previous < position < upper]
        _need(len(segment) == 6, "actual route fixture pair shape changed")
        result.append(segment)
        previous = upper
    _need(sum(len(segment) for segment in result) == len(report_positions),
          "actual route fixture has reports outside pair framing")
    return report_positions, suite_positions, result


def _all_reports_before_markers(lines, terminal, cleanup):
    report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    _need(report_positions and len(suite_positions) == 7,
          "cannot construct all-before-all framing fixture")
    reports = [lines[position] for position in report_positions]
    passes = [lines[position] for position in suite_positions]
    prefix = lines[:report_positions[0]]
    if terminal is None:
        return reports + passes
    return prefix + reports + passes + ["", terminal, cleanup]


def _swap_boundary(lines, terminal, boundary):
    _report_positions, _suite_positions, segments = _segments(lines, terminal)
    left = segments[boundary][-1]
    right = segments[boundary + 1][0]
    copy = list(lines)
    copy[left], copy[right] = copy[right], copy[left]
    return copy


def _suite_premature(lines, terminal):
    report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    copy = list(lines)
    suite_line = copy.pop(suite_positions[0])
    copy.insert(report_positions[0], suite_line)
    return copy


def _suite_moved(lines, terminal):
    _report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    copy = list(lines)
    source = suite_positions[1]
    suite_line = copy.pop(source)
    destination = suite_positions[-1]
    if source < destination:
        destination -= 1
    copy.insert(destination + 1, suite_line)
    return copy


def _suite_reordered(lines, terminal):
    _report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    copy = list(lines)
    copy[suite_positions[0]], copy[suite_positions[-1]] = (
        copy[suite_positions[-1]], copy[suite_positions[0]])
    return copy


def _suite_missing(lines, terminal):
    _report_positions, suite_positions = _report_and_suite_positions(lines, terminal)
    copy = list(lines)
    del copy[suite_positions[3]]
    return copy


def _record(results, name, action, should_pass):
    try:
        value = action()
    except reader.ReaderError as exc:
        if should_pass:
            raise AssertionError(name + " unexpectedly rejected: " + str(exc)) from exc
        results.append({"case": name, "result": "REJECTED",
                        "reason": str(exc)[:240]})
        return False
    if not should_pass:
        raise AssertionError(name + " unexpectedly passed")
    detail = {"case": name, "result": "PASS"}
    if isinstance(value, dict):
        detail["phase"] = value.get("phase")
        detail["reportRows"] = value.get("reportRows")
        detail["orderedMarkers"] = value.get("orderedMarkers")
    results.append(detail)
    return True


def _run_canaries():
    original_receipt = _read(ORIGINAL_RESULTS_PATH)
    original_replay = original_check._run_canaries()
    _need(original_replay == original_receipt,
          "original 93-case canary receipt changed")

    results = [{
        "case": "frozen reader 93-case replay",
        "result": "PASS",
        "canaryCount": original_receipt["canaryCount"],
        "positiveCount": original_receipt["positiveCount"],
        "negativeCount": original_receipt["negativeCount"],
    }]
    contract, protocol, text, receipt, timing, suites = _route_inputs()
    terminal = reader.TERMINAL_TEMPLATE.format(count=len(suites))
    cleanup = reader.CLEANUP_LINE

    _record(results, "valid actual seven-pair route framing",
            lambda: reader.check_phase(text, receipt, timing, suites, "routes"),
            True)

    text_lines = text.splitlines()
    receipt_lines = receipt.splitlines()
    for boundary in range(len(reader.PARTITIONS) - 1):
        _record(
            results, "reports crossing pair boundary " + str(boundary + 1),
            lambda boundary=boundary: reader.check_phase(
                "\n".join(_swap_boundary(text_lines, terminal, boundary)),
                "\n".join(_swap_boundary(receipt_lines, None, boundary)),
                timing, suites, "routes"), False)

    all_before_text = "\n".join(
        _all_reports_before_markers(text_lines, terminal, cleanup))
    all_before_receipt = "\n".join(
        _all_reports_before_markers(receipt_lines, None, None))
    _record(results, "all reports before all suite PASS markers",
            lambda: reader.check_phase(all_before_text, all_before_receipt,
                                       timing, suites, "routes"), False)

    _record(
        results, "suite PASS moved before its reports",
        lambda: reader.check_phase(
            "\n".join(_suite_premature(text_lines, terminal)),
            "\n".join(_suite_premature(receipt_lines, None)),
            timing, suites, "routes"), False)
    _record(
        results, "suite PASS moved to a later partition",
        lambda: reader.check_phase(
            "\n".join(_suite_moved(text_lines, terminal)),
            "\n".join(_suite_moved(receipt_lines, None)),
            timing, suites, "routes"), False)
    _record(
        results, "suite PASS markers reordered",
        lambda: reader.check_phase(
            "\n".join(_suite_reordered(text_lines, terminal)),
            "\n".join(_suite_reordered(receipt_lines, None)),
            timing, suites, "routes"), False)
    _record(
        results, "suite PASS marker missing",
        lambda: reader.check_phase(
            "\n".join(_suite_missing(text_lines, terminal)),
            "\n".join(_suite_missing(receipt_lines, None)),
            timing, suites, "routes"), False)

    bad_terminal = terminal.replace("7 Java suites", "6 Java suites")
    _record(
        results, "terminal marker mismatch",
        lambda: reader.check_phase(text.replace(terminal, bad_terminal, 1),
                                   receipt, timing, suites, "routes"), False)
    bad_receipt = receipt.replace("PCB_WHITESPACE_CASE ",
                                 "PCB_ROUTED_LEG_CASE ", 1)
    _record(
        results, "child receipt report mismatch",
        lambda: reader.check_phase(text, bad_receipt, timing, suites, "routes"),
        False)

    fresh_count = len(results) - 1
    fresh_positive = 1
    fresh_negative = fresh_count - fresh_positive
    _need(fresh_count == 14 and fresh_positive == 1 and fresh_negative == 13,
          "fresh per-pair framing canary count changed")
    return {
        "schema": 1,
        "status": "PASS",
        "canaryCount": original_receipt["canaryCount"] + fresh_count,
        "positiveCount": original_receipt["positiveCount"] + fresh_positive,
        "negativeCount": original_receipt["negativeCount"] + fresh_negative,
        "originalCanaryCount": original_receipt["canaryCount"],
        "originalCanaryReceiptSha256": _sha(ORIGINAL_RESULTS_PATH),
        "freshCanaryCount": fresh_count,
        "freshPositiveCount": fresh_positive,
        "freshNegativeCount": fresh_negative,
        "adapterSource": reader.source_metadata(),
        "consumedSha256": {
            "assess.py": _sha(HERE / "assess.py"),
            "check_assessment.py": _sha(HERE / "check_assessment.py"),
            "receipt_order_reader.py": _sha(HERE / "receipt_order_reader.py"),
            "check_receipt_order_reader.py": _sha(HERE / "check_receipt_order_reader.py"),
            "protocol.json": _sha(HERE / "protocol.json"),
            "routes.txt": _sha(HERE / "routes.txt"),
            "routes-receipt.txt": _sha(HERE / "routes-receipt.txt"),
            "routes-timing.json": _sha(HERE / "routes-timing.json"),
            "routes-suites.json": _sha(HERE / "routes-suites.json"),
            "assessment-canary-results.json": _sha(ORIGINAL_RESULTS_PATH),
        },
        "caseCounts": {
            "roots": len(protocol["roots"]),
            "partitions": len(protocol["partitions"]),
            "reports": 42,
            "suitePasses": len(suites),
            "originalCanaries": original_receipt["canaryCount"],
            "freshCanaries": fresh_count,
        },
        "coverage": [
            "actual final route log and child receipt with all seven two-root pairs",
            "six-report pair boundaries, report crossing, and all-before-all rejection",
            "suite premature, moved, reordered, missing, terminal, and receipt mismatch",
            "frozen 93-case reader corpus replayed without modification",
        ],
        "results": results,
        "notes": [
            "Post-native framing repair only; frozen assess.py, protocol, Java, and native receipts remain unchanged.",
            "The adapter replaces exactly one pinned textual block and leaves all report, source, physics, matched-root, and proof validation unchanged.",
            "No unit-validation.json or assessment.json was written by this canary checker; root controls final artifact release.",
        ],
    }


def main():
    try:
        result = _run_canaries()
        if RESULTS_PATH.exists() or RESULTS_PATH.is_symlink():
            raise reader.ReaderError(
                "refusing to overwrite existing task artifact: receipt-order-canary-results.json")
        payload = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        with RESULTS_PATH.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(payload)
        print("PASS receipt-order canaries; cases=" + str(result["canaryCount"]) +
              " rejected=" + str(result["negativeCount"]))
        return 0
    except (AssertionError, reader.ReaderError) as exc:
        print("FAIL receipt-order canaries: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
