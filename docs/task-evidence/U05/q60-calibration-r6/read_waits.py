"""Check private executed wait traces; independent feature admission is separate."""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path


def need(condition, message):
    if not condition:
        raise ValueError(message)


def rows(report):
    need(type(report.get("diagnosticProgramVersion")) is int and
         report["diagnosticProgramVersion"] == 2, "program version is not current")
    values = report.get("calibrationTemporalWaits")
    need(type(values) is list and len(values) == 5, "five hypothesis traces required")
    totals = []
    expected = ["HIGH_INPUT_SETTLED", "SERVICE_DECAY_1", "SERVICE_DECAY_2", "SERVICE_DECAY_3"]
    for trace in values:
        need(type(trace) is list and len(trace) == 4, "four ordered waits required")
        stopped, total = False, 0.0
        for index, (value, identity) in enumerate(zip(trace, expected)):
            need(type(value) is str and value.count("@") == 1, "invalid wait encoding")
            name, seconds = value.split("@")
            need(name == identity, "wait identity/order differs")
            seconds = float(seconds)
            need(math.isfinite(seconds) and 0 <= seconds <= 1, "wait exceeds bound")
            if index == 0:
                need(seconds == 0, "HIGH marker changed its zero-duration scope")
                continue
            need(abs(seconds * 10 - round(seconds * 10)) < 1e-10, "off-grid natural wait")
            need(not stopped or seconds == 0, "wait advances after earlier READY stop")
            stopped |= seconds < 1
            total += seconds
        totals.append(total)
    return totals


def self_test():
    good = {"diagnosticProgramVersion": 2, "calibrationTemporalWaits": [
        ["HIGH_INPUT_SETTLED@0.0", "SERVICE_DECAY_1@1.0", "SERVICE_DECAY_2@0.4", "SERVICE_DECAY_3@0.0"]
        for _ in range(5)]}
    need(rows(good) == [1.4] * 5, "positive trace self-test failed")
    negatives = []
    bad = copy.deepcopy(good); bad["diagnosticProgramVersion"] = 1; negatives.append(bad)
    for index, value in ((0, "HIGH_INPUT_SETTLED@0.1"), (1, "SERVICE_DECAY_1@1.1"),
                         (2, "SERVICE_DECAY_2@0.15"), (2, "WRONG@0.4"),
                         (2, "SERVICE_DECAY_2@NaN"), (3, "SERVICE_DECAY_3@0.1")):
        bad = copy.deepcopy(good); bad["calibrationTemporalWaits"][0][index] = value; negatives.append(bad)
    for bad in negatives:
        try:
            rows(bad)
        except ValueError:
            continue
        raise ValueError("negative trace self-test accepted")
    print("PASS: temporal wait reader self-test; seven negative canaries")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--reports", nargs=3, type=Path)
    parser.add_argument("--partition", choices=("development", "regression"))
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test(); return
    need(args.plan and args.reports and args.partition and args.output, "missing inputs")
    plan = json.loads(args.plan.read_text(encoding="utf-8-sig"))
    cases = []
    for seed, path in zip(plan[args.partition], args.reports):
        raw = path.read_bytes()
        need(len(raw) < 16 * 1024 * 1024, "capture too large")
        capture = json.loads(raw.decode("utf-8-sig"))
        need(capture.get("seed") == seed and capture.get("build") == plan["build"] and
             capture.get("sourceBinding") == plan["sourceBinding"], "capture binding differs")
        report = capture["report"]
        need(report.get("status") == "PASS", "actual qualification did not pass")
        totals = rows(report)
        cases.append({"seed": seed, "requestedOffWaitSecondsByHypothesis": totals,
                      "captureSha256": hashlib.sha256(raw).hexdigest()})
    result = {"status": "PASS_TEMPORAL_TRACE_BOUND_ONLY", "cases": cases,
              "scope": "Executed requested natural time; no CPU speedup or independent electrical/admission grant",
              "featureReaderStillRequired": True, "profileAssignment": "NONE"}
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS: actual temporal traces; independent feature admission remains separate")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as error:
        print("FAIL: temporal wait reader:", error)
        raise SystemExit(2)
