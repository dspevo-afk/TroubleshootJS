#!/usr/bin/env python3
"""Focused, repository-native regressions for the A01 qualification gate."""

import copy
import importlib.util
import json
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("a01_checker", ROOT / "scripts" / "a01.py")
A01 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(A01)


def artifact():
    return copy.deepcopy(A01.identity()["execution"])


def attempt(corpus, size, seed, temperature):
    resistances = [A01.expected_resistance(seed, index) for index in range(size)]
    total = sum(resistances)
    current = 10.0 / total
    nodes = [10.0]
    running = 10.0
    for resistance in resistances:
        running -= current * resistance
        nodes.append(running)
    unsupported = [
        {"stage": stage, "status": "UNSUPPORTED", "reason": "focused synthetic fixture"}
        for stage in sorted(A01.UNSUPPORTED_SYNTHETIC)
    ]
    return {
        "attemptId": f"{corpus}-r0-{temperature}-{size}-{seed}",
        "corpus": corpus,
        "round": 0,
        "temperature": temperature,
        "size": size,
        "seed": seed,
        "status": "PASS",
        "fixtureVersion": "a01-series-ladder-v1",
        "fixtureIdentity": A01.expected_fixture_identity(size, seed),
        "identityIndependentOfTiming": True,
        "timingIndependentIdentity": True,
        "physicalPackages": None,
        "pads": None,
        "nets": None,
        "rawSegments": None,
        "canonicalSegments": None,
        "hypothesisCount": None,
        "unsupportedStages": unsupported,
        "solverElements": size + 2,
        "voltageSourceCount": 2,
        "matrixFullSize": size + 3,
        "matrixReducedSize": size + 1,
        "analysisCount": 1,
        "stampCount": 1,
        "factorizationCount": 1,
        "solveCount": 2,
        "iterationCount": 2,
        "subIterationCount": 2,
        "acceptedStepCount": 2,
        "sourceVoltage": 10.0,
        "totalResistance": total,
        "expectedCurrent": current,
        "observedCurrent": current,
        "expectedNodeVoltages": nodes,
        "nodeVoltages": nodes[:],
        "elapsedMs": 4,
        "timingTrace": [
            {"event": "constructed", "ms": 0},
            {"event": "analyzed", "ms": 1},
            {"event": "step1", "ms": 2},
            {"event": "step2", "ms": 3},
            {"event": "finished", "ms": 4},
        ],
        "stageStatus": "SOLVER_PASS",
        "identityMatchesWarm": True,
    }


def report(corpus, status="PASS"):
    attempts = [
        attempt(corpus, size, seed, temperature)
        for size in A01.SIZES
        for seed in A01.SEEDS[corpus]
        for temperature in ("cold", "warm")
    ]
    value = {
        "protocol": "TSJ-A01-REPORT-1",
        "status": status,
        "corpus": corpus,
        "round": 0,
        "fixtureVersion": "a01-series-ladder-v1",
        "sourceFingerprint": A01.identity()["sourceFingerprint"],
        "buildFingerprint": A01.identity()["buildFingerprint"],
        "executedArtifact": artifact(),
        "baseline": {
            "stage": "IMPLEMENTED_SMALL_BASELINE",
            "physicalPackages": 3,
            "solverElements": 17,
            "pads": 6,
            "nets": 3,
            "rawSegments": 0,
            "canonicalSegments": 0,
            "hypothesisCount": 4,
            "matrixFullSize": 16,
            "matrixReducedSize": 11,
        },
        "attempts": attempts,
        "acceptedSteps": 32,
        "attemptCount": 16,
        "budget": {
            "protocol": "TSJ-A01-BUDGET-1",
            "version": "a01-budget-v1",
            "frozen": True,
            "frozenBeforeHoldout": True,
            "maxAttemptElapsedMs": 5000,
            "maxTotalElapsedMs": 30000,
            "maxSolverElements": 102,
            "maxMatrixFullSize": 103,
            "requiredAcceptedSteps": 32,
            "combinedRequiredAcceptedSteps": 64,
        },
        "unsupportedStagePolicy": "explicit-null-with-reason",
        "coldWarmProtocol": "cold-first-then-warm-in-one-loaded-application",
        "timingCannotAffectIdentity": True,
        "allOutcomesRetained": True,
        "originalOwnerRestored": True,
        "cleanup": "PASS",
        "performance": {
            "sampleCount": 16,
            "passedAttempts": 16,
            "failedAttempts": 0,
            "p50AttemptElapsedMs": 4,
            "p95AttemptElapsedMs": 4,
            "worstAttemptElapsedMs": 4,
            "memory": {"status": "UNAVAILABLE", "reason": "focused test"},
            "cancellation": "bounded-two-steps-per-attempt",
        },
        "forcedFailure": False,
        "totalElapsedMs": 100,
    }
    return value


def browser_metadata():
    return {
        "userAgent": "a01-integrity-test",
        "userAgentReason": None,
        "viewport": {"width": 1280, "height": 720},
        "devicePixelRatio": 1,
        "memory": {"status": "UNAVAILABLE", "reason": "focused test"},
    }


def receipt(corpus, status="PASS", terminal=None, cleanup="PASS"):
    inner = report(corpus)
    return {
        "protocol": "TSJ-A01-COLLECTION-1",
        "corpus": corpus,
        "round": 0 if corpus == "pilot" else 1,
        "debug": True,
        "forcedFailure": False,
        "sourceFingerprint": inner["sourceFingerprint"],
        "buildFingerprint": inner["buildFingerprint"],
        "status": status,
        "report": inner,
        "terminal": terminal or ("PASS:a01" if status == "PASS" else "FAIL:a01"),
        "cleanup": cleanup,
        "artifactIdentity": copy.deepcopy(inner["executedArtifact"]),
        "tabClosed": True,
        "browser": browser_metadata(),
    }


def write_json(directory, name, value):
    path = directory / name
    path.write_text(json.dumps(value) + "\n", encoding="utf-8")
    return path


def expect_rejected(label, values, directory):
    paths = [write_json(directory, f"{label}-{index}.json", value)
             for index, value in enumerate(values)]
    try:
        A01.check_measurements([str(path) for path in paths])
    except ValueError:
        return
    raise AssertionError(label + " was accepted")


def main():
    with tempfile.TemporaryDirectory(prefix="tsj-a01-integrity-") as scratch:
        root = Path(scratch)

        pilot = receipt("pilot")
        holdout = receipt("holdout")
        accepted = A01.check_measurements([
            str(write_json(root, "pilot.json", pilot)),
            str(write_json(root, "holdout.json", holdout)),
        ])
        assert accepted["reports"] == 2 and accepted["acceptedSteps"] == 64

        cleanup_failed = copy.deepcopy(pilot)
        cleanup_failed["status"] = "INFRASTRUCTURE_FAILURE"
        cleanup_failed["cleanup"] = "FAIL"
        cleanup_failed["error"] = "A01 report completed without cleanup PASS"
        expect_rejected("cleanup-failed", [cleanup_failed, holdout], root)

        contradiction = copy.deepcopy(pilot)
        contradiction["status"] = "INFRASTRUCTURE_FAILURE"
        contradiction["terminal"] = "FAIL:a01:contradiction"
        contradiction["error"] = "A01 terminal/report status mismatch"
        expect_rejected("terminal-report-contradiction", [contradiction, holdout], root)

        errored_pass = copy.deepcopy(pilot)
        errored_pass["error"] = "late collector error"
        expect_rejected("errored-pass", [errored_pass, holdout], root)

        excessive_total = copy.deepcopy(pilot)
        excessive_total["report"]["totalElapsedMs"] = 30001
        expect_rejected("excessive-total", [excessive_total], root)

        missing_total = copy.deepcopy(pilot)
        missing_total["report"].pop("totalElapsedMs")
        expect_rejected("missing-total", [missing_total], root)

        trace_outside_interval = copy.deepcopy(pilot)
        trace_outside_interval["report"]["attempts"][0]["timingTrace"][-1]["ms"] = 999999
        expect_rejected("trace-outside-interval", [trace_outside_interval], root)

        served_mismatch = copy.deepcopy(pilot)
        served_mismatch["report"]["executedArtifact"]["executionDigest"] = "f" * 64
        served_mismatch["artifactIdentity"] = copy.deepcopy(
            served_mismatch["report"]["executedArtifact"])
        expect_rejected("served-artifact-mismatch", [served_mismatch], root)

        oracle_tampered = copy.deepcopy(pilot)
        oracle_tampered["report"]["attempts"][0]["observedCurrent"] += 0.001
        expect_rejected("oracle-tamper", [oracle_tampered], root)

        failure = receipt("pilot", status="FAIL", terminal="FAIL:a01:solver")
        failed_attempt = {
            "corpus": "pilot",
            "round": 0,
            "temperature": "canary",
            "size": 20,
            "seed": 0,
            "status": "FAIL",
            "stageStatus": "SOLVER_FAIL",
            "cleanup": "PASS",
            "error": "focused genuine failure",
            "acceptedStepCount": 0,
        }
        failure["report"]["status"] = "FAIL"
        failure["report"]["attempts"] = [failed_attempt]
        failure["report"]["cleanup"] = "PASS"
        failure["report"]["originalOwnerRestored"] = True
        # A genuine failed report remains parseable for diagnosis, while the
        # qualification gate rejects it as a corpus input.
        A01.report_from_value(failure, "genuine-failure")
        expect_rejected("genuine-failure", [failure], root)

    print(json.dumps({
        "status": "PASS",
        "protocol": "TSJ-A01-INTEGRITY-REGRESSIONS-1",
        "cases": [
            "valid pilot + holdout aggregate",
            "cleanup-failed collection retained/rejected",
            "terminal/report contradiction retained/rejected",
            "recorded error cannot coexist with PASS",
            "excessive total elapsed rejected",
            "missing total elapsed rejected",
            "trace outside attempt interval rejected",
            "served artifact identity mismatch rejected",
            "electrical oracle tampering rejected",
            "genuine failure remains parseable",
        ],
    }, indent=2))


if __name__ == "__main__":
    main()
