#!/usr/bin/env python3
"""Strictly audit one compiled_attribute_acceptance host canary result."""

import json
from pathlib import Path
import sys


STATE_ATTRIBUTE = "data-tsj-host-canary-state"
REPORT_ATTRIBUTE = "data-tsj-host-canary-report"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def audit(mode, output, runner_exit):
    result = load(Path(output) / "result.json")
    require(result.get("schema") == 1, "runner result schema changed")
    require(result.get("caseCount") == 1 and len(result.get("cases", [])) == 1,
            "expected exactly one host-canary case")
    require(result.get("errors") == [], "runner reported an operation error")
    require(result.get("inputAudit", {}).get("status") == "PASS",
            "runner input/runner identity audit did not pass")

    identity = result.get("identity", {})
    edge_rows = identity.get("edgeProcessesBeforeCases", [])
    require(any("msedge.exe" in str(row.get("ExecutablePath", "")).lower()
                for row in edge_rows), "selected Edge process was not observed")

    cleanup = result.get("cleanup", {})
    require(cleanup.get("status") == "PASS" and cleanup.get("serverStopped") is True and
            cleanup.get("ownedSurvivors") == [] and cleanup.get("errors") == [],
            "owned Edge/server cleanup did not pass")

    case = result["cases"][0]
    require(case.get("case", {}).get("stateAttribute") == STATE_ATTRIBUTE and
            case.get("case", {}).get("reportAttribute") == REPORT_ATTRIBUTE,
            "case does not use the declared host-canary attributes")
    require(case.get("navigationError") is None and case.get("pageErrors") == [] and
            case.get("httpErrors") == [] and case.get("consoleErrors") == [] and
            case.get("listenerCleanupErrors") == [],
            "case contains an unrelated browser/navigation/listener error")
    require(case.get("stateMatch") is True and case.get("observedState") == "PASS" and
            case.get("reportMatch") is True and case.get("reportJsonValid") is True,
            "fixture did not reach its terminal state with a valid report")

    read_errors = case.get("attributeReadErrors")
    require(isinstance(read_errors, list), "attributeReadErrors is not an array")
    if mode == "old-negative":
        require(runner_exit == 0 and result.get("outcome") == "PASS" and
                case.get("outcome") == "PASS" and case.get("timedOut") is False,
                "pre-fix run did not reproduce the false PASS")
        require(bool(read_errors) and all(
            row.get("attribute") == STATE_ATTRIBUTE and
            row.get("type") == "TimeoutError" and
            "Timeout 10000ms exceeded" in row.get("message", "") and
            'locator("html")' in row.get("message", "")
            for row in read_errors),
            "pre-fix PASS lacks only the intended 10-second html-locator timeout evidence")
        return {"status": "PASS_PRE_FIX_FALSE_PASS_REPRODUCED",
                "runnerOutcome": result["outcome"], "readTimeouts": len(read_errors),
                "cleanup": cleanup["status"]}

    if mode == "new-positive":
        require(runner_exit == 0 and result.get("outcome") == "PASS" and
                case.get("outcome") == "PASS" and case.get("timedOut") is False and
                read_errors == [], "positive Edge canary did not pass cleanly")
        return {"status": "PASS_EDGE_POSITIVE", "runnerOutcome": result["outcome"],
                "cleanup": cleanup["status"]}

    if mode == "new-negative":
        require(runner_exit == 1 and result.get("outcome") == "FAIL" and
                case.get("outcome") == "FAIL" and case.get("timedOut") is False,
                "fixed runner did not reject the recovered attribute-timeout case")
        require(bool(read_errors) and all(
            row.get("attribute") == STATE_ATTRIBUTE and
            row.get("type") == "TimeoutError" and
            "Timeout 10000ms exceeded" in row.get("message", "") and
            'locator("html")' in row.get("message", "")
            for row in read_errors),
            "negative Edge canary did not capture only the intended 10-second timeout")
        return {"status": "PASS_EDGE_TIMEOUT_REJECTED", "runnerOutcome": result["outcome"],
                "terminalAndReportMatched": True, "readTimeouts": len(read_errors),
                "cleanup": cleanup["status"]}

    raise ValueError("unsupported canary mode: " + mode)


def main(argv):
    if len(argv) != 4:
        print("usage: audit_canary_result.py MODE OUTPUT_DIR RUNNER_EXIT", file=sys.stderr)
        return 2
    try:
        mode = argv[1]
        require(mode in ("old-negative", "new-positive", "new-negative"),
                "unsupported canary mode")
        runner_exit = int(argv[3])
        result = audit(mode, argv[2], runner_exit)
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
