#!/usr/bin/env python3
"""Run the frozen owned-zero-row timing sequence; never builds or edits source."""
import hashlib
import json
import math
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


SCRATCH = Path(__file__).resolve().parent
REPO = Path(r"[REDACTED_USER_PATH]")
PLAN = SCRATCH / "owned-zero-row-measurement-plan.json"
PAIR_POINTER = SCRATCH / "owned-zero-row-r2-source-pair-path.txt"
GATE_RECEIPT = SCRATCH / "owned-zero-row-gates-r2.json"
RUNNER = SCRATCH / "run_timing.py"
PAIR_VALIDATOR = SCRATCH / "validate_current_q30_timing_pair.py"
RESULT = SCRATCH / "owned-zero-row-sequence-result.json"

PLAN_SHA256 = "a95259c25ac3845d784f45fc78a4137f88426dd5bf0dd42aedd5c5041795e553"
RUNNER_SHA256 = "a7ef67c63397a28c1781b40746507fc0fa129f030742b8c6201350f099a9765f"
PAIR_VALIDATOR_SHA256 = "67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b"
EXPECTED_HEAD = "e285675d6f8f689cd7e73d09543f6cd37de8987f"
BASELINE_CIRSIM_SHA256 = "f836bfc58bdfb87d67f25184ebcd51e60d9eb820d6cc5acb966bcde98a64296b"
CANDIDATE_CIRSIM_SHA256 = "deb593ff2471904a72690cf0332a53bc968a4c93f6f2ac28e3140fa59dc1e242"
ZERO_ROW_TEST = "tests/contracts/Q30OwnedLuZeroRowContractTest.java"
DRIVER = "scripts/verify-current-contracts.ps1"
DRIVER_CLASS = "Q30OwnedLuZeroRowContractTest"
DRIVER_MARKER = "Q30 owned LU zero-row contracts "
ERROR_ARRAYS = (
    "pageErrors", "httpErrors", "consoleErrors", "attributeReadErrors",
    "listenerCleanupErrors",
)
EXPECTED_SEQUENCE = [
    {"label": "owned-zero-row-01-control-7", "arm": "control", "seed": "7"},
    {"label": "owned-zero-row-02-candidate-7", "arm": "candidate", "seed": "7"},
    {"label": "owned-zero-row-03-candidate-10387", "arm": "candidate", "seed": "10387"},
    {"label": "owned-zero-row-04-control-10387", "arm": "control", "seed": "10387"},
    {"label": "owned-zero-row-05-control-10014", "arm": "control", "seed": "10014"},
    {"label": "owned-zero-row-06-candidate-10014", "arm": "candidate", "seed": "10014"},
    {"label": "owned-zero-row-07-candidate-7", "arm": "candidate", "seed": "7"},
    {"label": "owned-zero-row-08-control-7", "arm": "control", "seed": "7"},
]


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_plan():
    raw = PLAN.read_bytes()
    require(sha256(raw) == PLAN_SHA256, "predeclared timing plan hash changed")
    plan = json.loads(raw.decode("utf-8"))
    require(plan.get("schema") == 1 and plan.get("status") == "PREDECLARED" and
            plan.get("sequence") == EXPECTED_SEQUENCE,
            "timing plan does not match the exact eight-row predeclared sequence")
    require("5%" in plan.get("earlyStop", "") and
            "Accept only a valid attributable intermediate improvement." in plan.get("interpretation", ""),
            "predeclared first-pair stop or interpretation changed")
    return plan, sha256(raw)


def load_fixture():
    require(PAIR_POINTER.is_file(), "missing root-prepared r2 source-pair pointer")
    fixture = Path(PAIR_POINTER.read_text(encoding="utf-8").strip()).resolve()
    require(fixture.is_dir() and fixture.parent == SCRATCH.resolve() and
            fixture.name.startswith("trial-owned-zero-row-"),
            "source-pair pointer is outside the expected scratch-owned fixture")
    audit_path = fixture / "source-audit.json"
    require(audit_path.is_file(), "source-pair audit is missing")
    audit_raw = audit_path.read_bytes()
    audit = json.loads(audit_raw.decode("utf-8"))
    require(audit.get("status") == "SOURCE_ONLY_PAIR_PREPARED_NOT_VALIDATED" and
            audit.get("head", audit.get("rootHead")) == EXPECTED_HEAD and
            audit.get("generatedWarCopied") is False,
            "source-pair audit is not the expected source-only fixture")
    differences = audit.get("differences", [])
    diff_paths = {item if isinstance(item, str) else item.get("path")
                  for item in differences}
    require(diff_paths == {"src/com/lushprojects/circuitjs1/client/CirSim.java"},
            "control/candidate source audit has differences beyond CirSim.java")
    shared_changes = set(audit.get("sharedTestChanges", []))
    require(shared_changes == {DRIVER, ZERO_ROW_TEST},
            "source audit does not bind exactly the shared native test and driver registration")
    control, candidate = fixture / "control", fixture / "candidate"
    require(control.is_dir() and candidate.is_dir(), "source-pair arm is missing")
    cirsim_rel = Path("src/com/lushprojects/circuitjs1/client/CirSim.java")
    require(sha256((control / cirsim_rel).read_bytes()) == BASELINE_CIRSIM_SHA256 and
            sha256((candidate / cirsim_rel).read_bytes()) == CANDIDATE_CIRSIM_SHA256,
            "source-pair CirSim hashes do not match the frozen candidate")
    test_rel = Path(*ZERO_ROW_TEST.split("/"))
    driver_rel = Path(*DRIVER.split("/"))
    control_test, candidate_test = control / test_rel, candidate / test_rel
    control_driver, candidate_driver = control / driver_rel, candidate / driver_rel
    require(control_test.is_file() and candidate_test.is_file() and
            control_test.read_bytes() == candidate_test.read_bytes(),
            "the same owned-zero-row contract test is not registered in both arms")
    require(control_driver.is_file() and candidate_driver.is_file() and
            control_driver.read_bytes() == candidate_driver.read_bytes(),
            "the maintained native driver differs between arms")
    driver_text = control_driver.read_text(encoding="utf-8")
    require(driver_text.count("Name = '" + DRIVER_CLASS + "'") == 1 and
            driver_text.count("Marker = '" + DRIVER_MARKER + "'") == 1,
            "owned-zero-row contract is not registered exactly once in the driver")
    require(len(audit.get("rootInputs", [])) == 1338,
            "source audit does not record the expected 1,338 root inputs")
    for arm in ("control", "candidate"):
        inputs = audit.get("armInputs", {}).get(arm, [])
        require(len(inputs) == 1339, "missing complete arm source census")
        for item in inputs:
            raw = (fixture / arm / item["path"]).read_bytes()
            require(sha256(raw) == item["sha256"] and len(raw) == item["bytes"],
                    "arm source changed since preparation: " + arm + "/" + item["path"])
    return fixture, audit, sha256(audit_raw)


def validate_gate_receipt():
    require(GATE_RECEIPT.is_file(), "root-owned focused gate receipt is missing")
    raw = GATE_RECEIPT.read_bytes()
    receipt = json.loads(raw.decode("utf-8"))
    expected_steps = {
        "native-candidate", "candidate-gwt", "candidate-compiled-canaries",
        "candidate-a07-strict", "control-gwt", "control-compiled-canaries",
        "control-a07-strict",
    }
    steps = receipt.get("steps", [])
    names = {step.get("name") for step in steps}
    require(receipt.get("status") == "FOCUSED_GATES_PASS_NOT_ACCEPTANCE" and
            names == expected_steps and len(steps) == len(expected_steps) and
            all(step.get("exitCode") == 0 for step in steps),
            "root-owned native/GWT/compiled/A07 precondition gates did not PASS")
    return receipt, sha256(raw)


def host_result(scratch, label, seed, runner_summary):
    result_path = scratch / label / "result.json"
    require(result_path.is_file(), label + " host result is missing")
    result = read_json(result_path)
    require(runner_summary.get("exitCode") == 0 and
            runner_summary.get("runnerOutcome") == "PASS" and
            result.get("outcome") == "PASS" and result.get("caseCount") == 1 and
            result.get("errors") == [], label + " host runner did not PASS")
    cleanup = result.get("cleanup", {})
    require(cleanup.get("status") == "PASS" and cleanup.get("serverStopped") is True and
            cleanup.get("ownedSurvivors") == [] and cleanup.get("errors") == [],
            label + " owned host cleanup did not PASS")
    cases = result.get("cases", [])
    require(len(cases) == 1, label + " host case census is invalid")
    case = cases[0]
    case_config = case.get("case", {})
    expected_path = (
        "circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&"
        "tsjQ30Coordinator=true&tsjQ30Seed=" + seed)
    query = parse_qs(urlsplit(case_config.get("path", "")).query, keep_blank_values=True)
    require(case_config.get("name") == label and case_config.get("path") == expected_path and
            query.get("tsjQ30Seed") == [seed] and
            "tsjQ30KernelProfile" not in query and
            "tsjQ30LuRepetitionProfile" not in query and
            runner_summary.get("profile") is False,
            label + " case does not match the exact unprofiled seed request")
    require(case.get("outcome") == "PASS" and case.get("terminalReached") is True and
            case.get("observedState") == "PASS:complete" and
            case.get("timedOut") is False and case.get("navigationError") is None and
            case.get("reportObserved") is True and case.get("reportJsonValid") is True and
            case.get("reportMatch") is True and
            all(case.get(key) == [] for key in ERROR_ARRAYS),
            label + " browser case failed state, report, timeout, or error checks")
    require(result.get("inputAudit", {}).get("status") == "PASS",
            label + " browser input audit did not PASS")
    require(all(runner_summary.get(phase, {}).get("cleanupComplete") is True and
                runner_summary.get(phase, {}).get("profileKeys") == []
                for phase in ("cold", "warm")),
            label + " is missing cold/warm timing or unexpectedly contains profile data")
    return {
        "status": "PASS",
        "outcome": result["outcome"],
        "observedState": case["observedState"],
        "timedOut": case["timedOut"],
        "navigationError": case["navigationError"],
        "errorArrays": {key: case.get(key) for key in ERROR_ARRAYS},
        "reportFile": case.get("reportFile"),
        "reportSha256": case.get("reportSha256"),
        "reportLength": case.get("reportLength"),
        "cleanup": cleanup,
        "inputAuditStatus": result["inputAudit"]["status"],
    }


def artifact_paths(plan, result_path):
    paths = [result_path]
    for row in plan["sequence"]:
        label = row["label"]
        paths.extend((SCRATCH / label, SCRATCH / (label + "-spec.json"),
                      SCRATCH / (label + ".log"), SCRATCH / (label + "-summary.json")))
    for pair_index in range(4):
        first, second = plan["sequence"][pair_index * 2:pair_index * 2 + 2]
        tag = "owned-zero-row-pair-" + str(pair_index + 1)
        paths.extend((
            SCRATCH / (first["label"] + "-vs-" + second["label"] +
                       "-timing-parity-" + tag + ".json"),
            SCRATCH / ("owned-zero-row-pair-" + str(pair_index + 1) +
                       "-validation.log"),
        ))
        for row in (first, second):
            paths.extend((SCRATCH / (row["label"] + "-strict-wrapper-" + tag + ".json"),
                          SCRATCH / (row["label"] + "-strict-reader-" + tag + ".txt")))
    return paths


def save_result(path, result, initial=False):
    payload = (json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
    if initial:
        with path.open("x", encoding="utf-8", newline="\n") as handle:
            handle.write(payload)
    else:
        path.write_text(payload, encoding="utf-8", newline="\n")


def main():
    plan, plan_digest = load_plan()
    require(REPO.is_dir(), "repository worktree is missing")
    require(sha256(RUNNER.read_bytes()) == RUNNER_SHA256,
            "timing runner changed from the frozen source")
    require(sha256(PAIR_VALIDATOR.read_bytes()) == PAIR_VALIDATOR_SHA256,
            "current strict timing-pair validator changed from the frozen source")
    fixture, source_audit, source_audit_digest = load_fixture()
    _, gate_receipt_digest = validate_gate_receipt()
    planned_paths = artifact_paths(plan, RESULT)
    require(len(planned_paths) == len(set(planned_paths)), "planned output paths collide")
    existing = [str(path.name) for path in planned_paths if path.exists()]
    require(not existing, "refusing to overwrite existing timing evidence: " + ", ".join(existing))

    result = {
        "schema": 1,
        "status": "RUNNING",
        "purpose": "focused intermediate timing comparison; not production acceptance",
        "planFile": PLAN.name,
        "planSha256": plan_digest,
        "sourcePairDirectory": fixture.name,
        "sourcePairAuditSha256": source_audit_digest,
        "focusedGateReceipt": GATE_RECEIPT.name,
        "focusedGateReceiptSha256": gate_receipt_digest,
        "rootHead": source_audit.get("head", source_audit.get("rootHead")),
        "runnerSha256": RUNNER_SHA256,
        "pairValidatorSha256": PAIR_VALIDATOR_SHA256,
        "rows": [],
        "pairs": [],
        "notRun": [row["label"] for row in plan["sequence"]],
        "limits": {
            "singlePredeclaredSequence": True,
            "strictReaderPerPairArm": True,
            "notProductionAcceptance": True,
            "normalQ30AcceptanceRemainsBlockedUntilAllRequirementsPass": True,
        },
    }
    save_result(RESULT, result, initial=True)

    try:
        for index, row in enumerate(plan["sequence"]):
            label, arm, seed = row["label"], row["arm"], row["seed"]
            require(arm in ("control", "candidate"), "plan contains an unknown arm")
            source_root = fixture / arm
            command = [sys.executable, "-B", str(RUNNER), str(REPO), str(SCRATCH),
                       str(source_root), label, seed]
            print("START " + label + " arm=" + arm + " seed=" + seed, flush=True)
            process = subprocess.run(command, check=False)
            summary_path = SCRATCH / (label + "-summary.json")
            if summary_path.is_file():
                row_summary = read_json(summary_path)
                row_summary["plannedArm"] = arm
                try:
                    row_summary["hostAudit"] = host_result(
                        SCRATCH, label, seed, row_summary)
                except Exception as error:
                    row_summary["hostAudit"] = {"status": "FAIL", "error": str(error)}
            else:
                row_summary = {"label": label, "plannedArm": arm,
                               "exitCode": process.returncode,
                               "status": "NO_RUN_SUMMARY"}
            result["rows"].append(row_summary)
            result["notRun"].remove(label)
            save_result(RESULT, result)
            if process.returncode != 0 or row_summary.get("exitCode") != 0 or \
                    row_summary.get("hostAudit", {}).get("status") != "PASS":
                result["status"] = "STOPPED_HOST_CORRECTNESS_OR_CLEANUP_FAILURE"
                save_result(RESULT, result)
                return 1

            if index % 2 == 1:
                first, second = plan["sequence"][index - 1], row
                pair_index = (index + 1) // 2
                tag = "owned-zero-row-pair-" + str(pair_index)
                validation_log = SCRATCH / (tag + "-validation.log")
                command = [sys.executable, "-B", str(PAIR_VALIDATOR), str(SCRATCH),
                           str(REPO), first["label"], second["label"], seed,
                           "--tag", tag]
                print("VALIDATE " + tag + " labels=" + first["label"] + "," +
                      second["label"], flush=True)
                with validation_log.open("x", encoding="utf-8", newline="\n") as log:
                    validation = subprocess.run(command, stdout=log,
                                                 stderr=subprocess.STDOUT, check=False)
                pair_path = SCRATCH / (first["label"] + "-vs-" + second["label"] +
                                       "-timing-parity-" + tag + ".json")
                pair_summary = read_json(pair_path) if pair_path.is_file() else {
                    "status": "NO_RECEIPT"}
                pair_record = {
                    "tag": tag,
                    "labelsInPlanOrder": [first["label"], second["label"]],
                    "seed": seed,
                    "validatorExitCode": validation.returncode,
                    "receiptFile": pair_path.name,
                    "status": pair_summary.get("status"),
                    "timingComparison": pair_summary.get("timingComparison"),
                    "removedTimingPaths": pair_summary.get("removedTimingPaths"),
                    "runEvidence": pair_summary.get("runs"),
                }
                result["pairs"].append(pair_record)
                save_result(RESULT, result)
                if validation.returncode != 0 or pair_summary.get("status") != "PASS":
                    result["status"] = "STOPPED_STRICT_READER_OR_PARITY_FAILURE"
                    save_result(RESULT, result)
                    return 1

                if pair_index == 1:
                    control = result["rows"][0]
                    candidate = result["rows"][1]
                    control_proof = control.get("cold", {}).get("proofElapsedMs")
                    candidate_proof = candidate.get("cold", {}).get("proofElapsedMs")
                    require(isinstance(control_proof, (int, float)) and
                            not isinstance(control_proof, bool) and
                            math.isfinite(control_proof) and control_proof > 0 and
                            isinstance(candidate_proof, (int, float)) and
                            not isinstance(candidate_proof, bool) and
                            math.isfinite(candidate_proof) and candidate_proof >= 0,
                            "first-pair proof timing is missing or non-finite")
                    result["firstPairProofRegression"] = {
                        "controlMs": control_proof,
                        "candidateMs": candidate_proof,
                        "candidateOverControl": candidate_proof / control_proof,
                        "stopThreshold": 1.05,
                    }
                    if candidate_proof > control_proof * 1.05:
                        result["status"] = "STOPPED_PREDECLARED_FIRST_PAIR_REGRESSION"
                        save_result(RESULT, result)
                        return 2
                    save_result(RESULT, result)

        require(sha256(PLAN.read_bytes()) == plan_digest and
                sha256(RUNNER.read_bytes()) == RUNNER_SHA256 and
                sha256(PAIR_VALIDATOR.read_bytes()) == PAIR_VALIDATOR_SHA256 and
                sha256(GATE_RECEIPT.read_bytes()) == gate_receipt_digest and
                sha256((fixture / "source-audit.json").read_bytes()) == source_audit_digest,
                "plan, validator, or source-pair audit changed during the sequence")
        result["status"] = "FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE"
        save_result(RESULT, result)
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")), flush=True)
        return 0
    except Exception as error:
        result["status"] = "STOPPED_SEQUENCE_ERROR"
        result["error"] = str(error)
        save_result(RESULT, result)
        print("FAIL: " + str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
