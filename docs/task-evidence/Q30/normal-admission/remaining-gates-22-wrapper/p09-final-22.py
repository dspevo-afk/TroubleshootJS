#!/usr/bin/env python3
"""Prepare and run the final strict P09 evidence reader after all source gates pass."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

PREFIX = "GATE_ROW "
FINAL_REL = Path("docs/task-evidence/Q30/normal-admission/p09-final-22")
NATIVE_PASS = re.compile(r"(?m)^PASS: current contracts; \d+ Java suites, independent seed/value/role oracles and report protocol\.\r?$")
NATIVE_CLEANUP = re.compile(r"(?m)^CLEANUP: current JVM contract classes and task-owned scratch removed\.\r?$")
READER_PASS = re.compile(r"^PASS: Quick Play gate population/geometry/repair/retry reader; malformed controls=\d+$")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def checked_root_result(path, *, label, case_count, input_count=None, native=False):
    result = read_json(path)
    require(result.get("status") == "PASS", label + " root result is not PASS")
    if not native:
        require(result.get("exitCode") == 0, label + " source exit code is not 0")
        require(result.get("resultOutcome") == "PASS", label + " result outcome is not PASS")
        require(result.get("caseCount") == case_count, label + " case count changed")
        require(result.get("inputMismatches") == [], label + " has input mismatches")
        cleanup = result.get("cleanup") or {}
        require(cleanup.get("serverThreadStopped") is True and cleanup.get("ownedSurvivors") == [],
                label + " cleanup proof is incomplete")
        if input_count is not None:
            require(result.get("inputCount") == input_count, label + " frozen input count changed")
        return {"status": result["status"], "exitCode": result["exitCode"],
                "resultOutcome": result["resultOutcome"], "caseCount": result["caseCount"],
                "inputCount": result.get("inputCount"), "cleanup": cleanup}

    require(result.get("runnerSummary", {}).get("actualExitCode") == 0,
            "native05 actual runner exit code is not 0")
    runner = result.get("runnerSummary", {})
    require(runner.get("fullPassMarkerCount") == 1 and runner.get("cleanupMarkerCount") == 1 and
            runner.get("failureMarkerCount") == 0 and runner.get("cleanupFailureMarkerCount") == 0 and
            runner.get("receiptExists") is True and runner.get("qualified") is True,
            "native05 runner PASS/cleanup/receipt proof is incomplete")
    require(result.get("inputAudit", {}).get("count") == 1248 and
            result.get("inputAudit", {}).get("unchanged") is True and
            result.get("readerInputAudit", {}).get("count") == 3 and
            result.get("readerInputAudit", {}).get("unchanged") is True,
            "native05 input audit is incomplete")
    require(result.get("checks") and all(value is True for value in result["checks"].values()),
            "native05 root checks contain a failure")
    exits = result.get("processExitCodes", {})
    require(exits.get("nativeRunner") == 0 and exits.get("nativeServiceReader") == 0 and
            exits.get("corpusReader") == 0, "native05 strict-reader exit code is not 0")
    return {"status": result["status"], "actualRunnerExitCode": runner["actualExitCode"],
            "fullPassMarkerCount": runner["fullPassMarkerCount"],
            "cleanupMarkerCount": runner["cleanupMarkerCount"],
            "failureMarkerCount": runner["failureMarkerCount"],
            "cleanupFailureMarkerCount": runner["cleanupFailureMarkerCount"],
            "receiptExists": runner["receiptExists"],
            "inputCount": result["inputAudit"]["count"],
            "nativeServiceReaderExitCode": exits["nativeServiceReader"],
            "corpusReaderExitCode": exits["corpusReader"]}


def extract_native_rows(log_path, source_path, runner_path):
    source = source_path.read_text(encoding="utf-8-sig")
    require('GATE_ROW {\\"seed\\":\\"' in source,
            "current QuickPlayGateCorpus source no longer emits the expected GATE_ROW JSON prefix")
    runner = runner_path.read_text(encoding="utf-8-sig")
    first = runner.find("@{ Name = 'QuickPlayGateCorpus'; Marker =")
    holdout = runner.find("@{ Name = 'QuickPlayGateHoldoutCorpus'; Marker =")
    require(first >= 0 and holdout > first,
            "current unfiltered runner no longer orders the natural and holdout corpus suites")
    holdout_source = source_path.with_name("QuickPlayGateHoldoutCorpus.java").read_text(encoding="utf-8-sig")
    require("QuickPlayGateCorpus.run(true);" in holdout_source,
            "current holdout suite no longer delegates to the seeded gate corpus")
    text = log_path.read_text(encoding="utf-8-sig")
    rows = []
    for line_number, line in enumerate(text.splitlines(), 1):
        if line.startswith("GATE_ROW"):
            require(line.startswith(PREFIX), "malformed GATE_ROW prefix at native log line " + str(line_number))
            try:
                row = json.loads(line[len(PREFIX):])
            except json.JSONDecodeError as error:
                raise ValueError("invalid GATE_ROW JSON at native log line " + str(line_number)) from error
            require(isinstance(row, dict), "native GATE_ROW is not a JSON object")
            rows.append(row)
    require(len(rows) == 72, "native05 log must contain exactly 72 natural GATE_ROW records")
    cohorts = [row.get("cohort") for row in rows]
    require(cohorts == ["development"] * 24 + ["holdout"] * 48,
            "native GATE_ROW cohort/order does not match the two current unfiltered suites")
    for row in rows:
        require(not ({"controlledNegative", "caseId", "replayGroup"} & set(row)),
                "controlled metadata appeared in the native natural population")
    return rows, {"rowCount": len(rows), "developmentRows": 24, "holdoutRows": 48,
                  "prefix": PREFIX, "suiteOrderVerified": True}


def redact(text, replacements):
    for old, new in sorted(replacements, key=lambda item: len(item[0]), reverse=True):
        if old:
            text = text.replace(old, new)
    return text


def has_private_absolute_path(text):
    patterns = (r"(?i)\b[A-Z]:\\+Users\\+", r"(?i)\b[A-Z]:/Users/",
                r"(?i)<user-home>/\s]+/", r"(?i)<user-home>/\s]+/")
    return any(re.search(pattern, text) for pattern in patterns)


def sanitize_private_paths(text):
    patterns = (r"(?i)\b[A-Z]:(?:\\{1,2})Users(?:\\{1,2})[^\s]+",
                r"(?i)\b[A-Z]:<user-home>", r"(?i)(?:/home|/Users)/[^/\s]+/[^\s]+")
    for pattern in patterns:
        text = re.sub(pattern, "<PRIVATE_PATH>", text)
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo_root", help="Repository root; no tests/builds are launched by preparation.")
    args = parser.parse_args()
    repo = Path(args.repo_root).resolve(strict=True)
    require((repo / "tests/contracts/quickplay_gate_evidence.py").is_file(), "repository root is invalid")

    pointer = repo / ".tools/q30-native-22-wrapper-path.txt"
    require(pointer.is_file(), "native05 wrapper run pointer is missing")
    native_run = Path(pointer.read_text(encoding="utf-8-sig").strip()).resolve(strict=True)
    require(native_run.is_dir() and native_run.parent == Path(__file__).resolve().parent and
            native_run.name.startswith("native-final-05-run-"),
            "native05 pointer does not resolve to this wrapper parent's native05 run directory")
    native_root_path = native_run / "native-final-05-root-result.json"
    native_log = native_run / "native-final-05.log"
    require(native_root_path.is_file() and native_log.is_file(), "native05 root result or full log is missing")
    native_summary = checked_root_result(native_root_path, label="native05", case_count=None, native=True)

    evidence = repo / FINAL_REL
    require(not evidence.exists(), "refusing to overwrite existing final P09 evidence directory")
    inputs = {
        "nativePointer": (pointer, ".tools/q30-native-22-wrapper-path.txt"),
        "nativeFullLog": (native_log, "native-final-05.log"),
        "nativeRootResult": (native_root_path, "native-final-05-root-result.json"),
        "compiledCases": (repo / "docs/task-evidence/Q30/normal-admission/compiled-p09-22-cases.json",
                          "docs/task-evidence/Q30/normal-admission/compiled-p09-22-cases.json"),
        "compiledRootResult": (repo / "docs/task-evidence/Q30/normal-admission/compiled-p09-22-root-result.json",
                               "docs/task-evidence/Q30/normal-admission/compiled-p09-22-root-result.json"),
        "controlledCases": (repo / "docs/task-evidence/Q30/normal-admission/compiled-p09-controlled-22-cases.json",
                            "docs/task-evidence/Q30/normal-admission/compiled-p09-controlled-22-cases.json"),
        "controlledRootResult": (repo / "docs/task-evidence/Q30/normal-admission/compiled-p09-controlled-22-root-result.json",
                                 "docs/task-evidence/Q30/normal-admission/compiled-p09-controlled-22-root-result.json"),
        "nativeSource": (repo / "tests/contracts/QuickPlayGateCorpus.java", "tests/contracts/QuickPlayGateCorpus.java"),
        "holdoutSource": (repo / "tests/contracts/QuickPlayGateHoldoutCorpus.java", "tests/contracts/QuickPlayGateHoldoutCorpus.java"),
        "runnerScript": (repo / "scripts/verify-current-contracts.ps1", "scripts/verify-current-contracts.ps1"),
        "strictReader": (repo / "tests/contracts/quickplay_gate_evidence.py", "tests/contracts/quickplay_gate_evidence.py"),
    }
    for path, _ in inputs.values():
        require(path.is_file(), "required combined-gate input is missing")
    before = {key: sha256(path) for key, (path, _) in inputs.items()}
    compiled_summary = checked_root_result(inputs["compiledRootResult"][0], label="compiled P09-22",
                                           case_count=72, input_count=394)
    controlled_summary = checked_root_result(inputs["controlledRootResult"][0], label="controlled P09-22",
                                             case_count=6, input_count=394)

    # Create the final directory only after all source gate results are proven PASS.
    evidence.mkdir(parents=False, exist_ok=False)
    display_paths = {key: label for key, (_, label) in inputs.items()}
    write_json(evidence / "input-audit-before.json", {
        "captured": "immediately before corpus assembly and strict-reader invocation",
        "files": {key: {"path": display_paths[key], "sha256": before[key]} for key in inputs},
    })

    redactions = [(str(repo), "<REPO_ROOT>"), (str(evidence), "<FINAL_EVIDENCE_DIR>"),
                  (str(native_run), "<NATIVE_RUN_DIR>")]
    result = {"gate": "p09-final-22", "status": "FAIL", "nativeSource": native_summary,
              "compiledSource": compiled_summary, "controlledSource": controlled_summary,
              "reader": {"actualExitCode": None, "stdout": "", "stderr": ""},
              "nativeRows": None, "inputAudit": None, "errors": []}
    after = None
    try:
        rows, row_summary = extract_native_rows(native_log, inputs["nativeSource"][0], inputs["runnerScript"][0])
        log_text = native_log.read_text(encoding="utf-8-sig")
        require(len(NATIVE_PASS.findall(log_text)) == 1 and len(NATIVE_CLEANUP.findall(log_text)) == 1,
                "native05 full log does not contain exactly one full PASS and cleanup marker")
        require("CURRENT_CONTRACT_FAILURE:" not in log_text and "CURRENT_CONTRACT_CLEANUP:" not in log_text,
                "native05 full log contains a failure marker")
        compiled = read_json(inputs["compiledCases"][0])
        controlled = read_json(inputs["controlledCases"][0])
        require(isinstance(compiled, list) and len(compiled) == 72,
                "raw compiled P09-22 case file must contain exactly 72 cases")
        require(isinstance(controlled, list) and len(controlled) == 6,
                "raw controlled P09-22 case file must contain exactly 6 canaries")
        corpus = {"native": rows, "compiled": compiled, "canaries": controlled}
        corpus_text = json.dumps(corpus, ensure_ascii=False, indent=2)
        require(not has_private_absolute_path(corpus_text), "personal absolute path found in assembled corpus")
        write_json(evidence / "corpus.json", corpus)
        result["nativeRows"] = row_summary
        command = [sys.executable, "-B", "tests/contracts/quickplay_gate_evidence.py", FINAL_REL.as_posix()]
        proc = subprocess.run(command, cwd=str(repo), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", check=False)
        replacements = [(str(repo), "<REPO_ROOT>"), (str(evidence), "<FINAL_EVIDENCE_DIR>"),
                        (str(native_run), "<NATIVE_RUN_DIR>")]
        stdout = sanitize_private_paths(redact(proc.stdout, replacements))
        stderr = sanitize_private_paths(redact(proc.stderr, replacements))
        result["reader"] = {"actualExitCode": proc.returncode, "stdout": stdout, "stderr": stderr,
                            "stdoutSha256": hashlib.sha256(proc.stdout.encode("utf-8")).hexdigest(),
                            "stderrSha256": hashlib.sha256(proc.stderr.encode("utf-8")).hexdigest(),
                            "pathsRedacted": stdout != proc.stdout or stderr != proc.stderr,
                            "command": ["python", "-B", "tests/contracts/quickplay_gate_evidence.py",
                                        FINAL_REL.as_posix()]}
        (evidence / "reader-stdout.txt").write_text(stdout, encoding="utf-8")
        (evidence / "reader-stderr.txt").write_text(stderr, encoding="utf-8")
        reader_result_path = evidence / "reader-result.json"
        require(proc.returncode == 0, "strict reader actual exit code was not 0")
        require(READER_PASS.fullmatch(stdout.strip()) is not None and stderr == "",
                "strict reader stdout/stderr did not show a clean PASS")
        require(reader_result_path.is_file() and read_json(reader_result_path).get("status") == "PASS",
                "strict reader did not write a PASS result")
        after = {key: sha256(path) for key, (path, _) in inputs.items()}
        changed = [key for key in inputs if before[key] != after[key]]
        result["inputAudit"] = {"files": {
            key: {"path": display_paths[key], "sha256Before": before[key],
                  "sha256After": after[key], "unchanged": before[key] == after[key]}
            for key in inputs}, "unchanged": not changed}
        write_json(evidence / "input-audit-after.json", {
            "captured": "after strict-reader completion", "files": result["inputAudit"]["files"]})
        require(not changed, "combined-gate inputs changed during corpus assembly/reader: " + ", ".join(changed))
        for artifact in evidence.iterdir():
            if artifact.is_file():
                require(not has_private_absolute_path(artifact.read_text(encoding="utf-8-sig")),
                        "personal absolute path found in final evidence")
        result["status"] = "PASS"
        result["limits"] = "Frozen natural native population plus the separately declared controlled-negative canaries; not a universal success guarantee."
    except Exception as error:
        result["errors"].append(redact(str(error), redactions))
        if after is None:
            try:
                after = {key: sha256(path) for key, (path, _) in inputs.items()}
                result["inputAudit"] = {"files": {
                    key: {"path": display_paths[key], "sha256Before": before[key],
                          "sha256After": after[key], "unchanged": before[key] == after[key]}
                    for key in inputs}, "unchanged": before == after}
                write_json(evidence / "input-audit-after.json", {
                    "captured": "after failed corpus assembly/reader attempt", "files": result["inputAudit"]["files"]})
            except Exception as audit_error:
                result["errors"].append("input audit failed: " + redact(str(audit_error), redactions))
    result_payload = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if has_private_absolute_path(result_payload):
        result["status"] = "FAIL"
        result["errors"].append("personal absolute path found in final result")
        result_payload = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    (evidence / "p09-final-22-result.json").write_text(result_payload, encoding="utf-8")
    print("P09_FINAL_22 " + result["status"])
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        # Preconditions fail before creating evidence, so no stale or partial PASS can result.
        print("P09_FINAL_22 PRECONDITION_FAIL: " + str(error), file=sys.stderr)
        raise SystemExit(1)
