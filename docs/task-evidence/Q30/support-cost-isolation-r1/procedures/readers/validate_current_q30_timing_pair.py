#!/usr/bin/env python3
"""Validate two completed, same-seed current-plan-4 Q30 timing rows.

This evidence reader never launches a browser, host, build, or test.
"""
import argparse
import copy
import hashlib
import json
import math
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


ERROR_ARRAYS = (
    "pageErrors", "httpErrors", "consoleErrors", "attributeReadErrors",
    "listenerCleanupErrors",
)
TIMING_FIELDS = (
    "elapsedMs", "wallElapsedMs", "maxAdvanceMs", "maxManualUnitMs",
    "routingElapsedMs", "proofElapsedMs", "cleanupElapsedMs",
)
APP_PHASE_TIMINGS = (
    "elapsedMs", "wallElapsedMs", "maxAdvanceMs", "maxManualUnitMs",
    "routingElapsedMs", "proofElapsedMs", "cleanupElapsedMs",
)


class ValidationError(Exception):
    pass


def need(condition, message):
    if not condition:
        raise ValidationError(message)


def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValidationError("could not read UTF-8 JSON {}: {}".format(path.name, error))


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def write_json_new(path, value):
    need(not path.exists(), "refusing to overwrite existing evidence output: " + path.name)
    path.write_bytes(json_bytes(value))


def write_text_new(path, value):
    need(not path.exists(), "refusing to overwrite existing evidence output: " + path.name)
    path.write_text(value, encoding="utf-8", newline="\n")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def finite(value, label, minimum=None):
    need(isinstance(value, (int, float)) and not isinstance(value, bool) and
         math.isfinite(value), label + " must be finite")
    value = float(value)
    if minimum is not None:
        need(value >= minimum, label + " is below its minimum")
    return value


def valid_seed(seed):
    need(isinstance(seed, str) and re.fullmatch(r"-?(0|[1-9][0-9]*)", seed) is not None,
         "seed must be a canonical signed-long string")
    parsed = int(seed)
    need(-(2 ** 63) <= parsed < 2 ** 63, "seed is outside signed-long range")


def safe_report_path(output_dir, report_file):
    need(isinstance(report_file, str) and report_file,
         "host case omitted its standalone report path")
    relative = Path(report_file.replace("/", "\\"))
    need(not relative.is_absolute() and ".." not in relative.parts,
         "host case report path is not a safe relative path")
    root = output_dir.resolve()
    target = (output_dir / relative).resolve()
    need(target != root and root in target.parents,
         "host case report path escapes its output directory")
    return target


def manifest_content(manifest):
    keys = (
        "schema", "roots", "files", "fileCount", "sourceFileCount",
        "webFileCount", "sha256", "sourceSha256", "webSha256",
    )
    need(all(key in manifest for key in keys), "input manifest omitted required identity fields")
    return {key: manifest[key] for key in keys}


def check_no_profile_fields(value, path="report"):
    if isinstance(value, dict):
        for key, item in value.items():
            need("profile" not in str(key).casefold(),
                 "unexpected profile field in {}.{}".format(path, key))
            check_no_profile_fields(item, path + "." + str(key))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            check_no_profile_fields(item, "{}[{}]".format(path, index))


def validate_seed_path(path, seed, label):
    query = parse_qs(urlsplit(path).query, keep_blank_values=True)
    need(query.get("tsjQ30Seed") == [seed],
         label + " spec/host URL does not carry the exact requested Q30 seed")
    need(not any("profile" in key.casefold() for key in query),
         label + " URL unexpectedly enables a profile query")


def validate_host_row(scratch, label, seed):
    output_dir = scratch / label
    need(output_dir.is_dir(), "missing host output directory: " + label)
    result = load_json(output_dir / "result.json")
    before = load_json(output_dir / "input-manifest-before.json")
    after = load_json(output_dir / "input-manifest-after.json")
    runner_before = load_json(output_dir / "runner-before.json")
    runner_after = load_json(output_dir / "runner-after.json")
    spec = load_json(scratch / (label + "-spec.json"))
    host = load_json(scratch / (label + "-summary.json"))

    need(result.get("outcome") == "PASS" and result.get("caseCount") == 1 and
         result.get("errors") == [], label + " host result did not PASS exactly one case")
    audit = result.get("inputAudit", {})
    need(audit.get("status") == "PASS", label + " input audit did not PASS")
    for before_key, after_key in (
            ("beforeSha256", "afterSha256"),
            ("sourceSha256Before", "sourceSha256After"),
            ("webSha256Before", "webSha256After"),
            ("runnerBeforeSha256", "runnerAfterSha256"),
            ("fileCountBefore", "fileCountAfter")):
        need(audit.get(before_key) is not None and audit.get(before_key) == audit.get(after_key),
             label + " input audit changed " + before_key)

    before_content, after_content = manifest_content(before), manifest_content(after)
    need(before_content == after_content,
         label + " before/after file manifests differ")
    need(before.get("sha256") == audit.get("beforeSha256") and
         after.get("sha256") == audit.get("afterSha256") and
         before.get("sourceSha256") == audit.get("sourceSha256Before") and
         after.get("sourceSha256") == audit.get("sourceSha256After") and
         before.get("webSha256") == audit.get("webSha256Before") and
         after.get("webSha256") == audit.get("webSha256After") and
         before.get("fileCount") == audit.get("fileCountBefore") and
         after.get("fileCount") == audit.get("fileCountAfter"),
         label + " manifest contents disagree with the host input audit")
    need(runner_before.get("sha256") == audit.get("runnerBeforeSha256") and
         runner_after.get("sha256") == audit.get("runnerAfterSha256") and
         runner_before == runner_after,
         label + " browser runner identity changed or disagrees with the host input audit")

    cleanup = result.get("cleanup", {})
    need(cleanup.get("status") == "PASS" and cleanup.get("serverStopped") is True and
         cleanup.get("ownedSurvivors") == [] and cleanup.get("errors") == [],
         label + " owned host cleanup did not PASS")
    cases = result.get("cases")
    need(isinstance(cases, list) and len(cases) == 1,
         label + " host case census is invalid")
    case = cases[0]
    receipt_case = case.get("case", {})
    need(receipt_case.get("name") == label and case.get("outcome") == "PASS" and
         case.get("terminalReached") is True and case.get("stateMatch") is True and
         case.get("observedState") == "PASS:complete" and
         case.get("lastObservedState") == "PASS:complete" and
         case.get("timedOut") is False and case.get("navigationError") is None and
         case.get("reportObserved") is True and case.get("reportJsonValid") is True and
         case.get("reportMatch") is True,
         label + " case state, terminal, timeout, navigation, or report check failed")
    for key in ERROR_ARRAYS:
        need(case.get(key) == [], label + " has host errors in " + key)

    spec_cases = spec.get("cases")
    need(isinstance(spec_cases, list) and len(spec_cases) == 1,
         label + " browser spec must contain exactly one case")
    spec_case = spec_cases[0]
    need(spec_case.get("name") == label and
         spec_case.get("path") == receipt_case.get("path") and
         spec_case.get("expectedState") == "PASS:complete" and
         receipt_case.get("expectedState") == "PASS:complete",
         label + " host receipt does not match its exact browser spec")
    validate_seed_path(spec_case.get("path", ""), seed, label)

    need(host.get("label") == label and host.get("exitCode") == 0 and
         host.get("runnerOutcome") == "PASS",
         label + " host wrapper did not exit PASS")
    host_monotonic = finite(host.get("hostMonotonicSeconds"),
                            label + " hostMonotonicSeconds", minimum=0)
    host_drift = finite(host.get("wallMinusMonotonicSeconds"),
                        label + " wallMinusMonotonicSeconds")
    if "cleanup" in host:
        need(host["cleanup"].get("status") == "PASS" and
             host["cleanup"].get("ownedSurvivors") == [] and
             host["cleanup"].get("errors") == [],
             label + " summary cleanup differs from passing host cleanup")

    report_path = safe_report_path(output_dir, case.get("reportFile"))
    raw_report = report_path.read_bytes()
    report_text = raw_report.decode("utf-8")
    report_digest = sha256(raw_report)
    need(case.get("reportSha256") == report_digest and
         case.get("reportLength") == len(report_text),
         label + " standalone report differs from the embedded UTF-8 receipt")
    report = json.loads(report_text)
    need(isinstance(report, dict) and report.get("status") == "PASS" and
         report.get("seed") == seed and report.get("requestedSeed") == seed,
         label + " raw coordinator report status/seed is not the exact requested run")
    check_no_profile_fields(report)
    embedded = case.get("report")
    if embedded is not None:
        if isinstance(embedded, str):
            need(embedded.encode("utf-8") == raw_report,
                 label + " inline report string differs from standalone report")
        else:
            need(embedded == report,
                 label + " inline report object differs from standalone report")

    summary_timings = {
        "totalElapsedMs": report.get("totalElapsedMs"),
        "phaseElapsedMs": report.get("phaseElapsedMs"),
        "cold": {key: report["cold"].get(key) for key in APP_PHASE_TIMINGS},
        "warm": {key: report["warm"].get(key) for key in APP_PHASE_TIMINGS},
    }
    for phase in ("cold", "warm"):
        need(isinstance(report.get(phase), dict), label + " report is missing " + phase)
        for key in ("elapsedMs", "wallElapsedMs", "proofElapsedMs", "routingElapsedMs"):
            finite(report[phase].get(key), label + " " + phase + " app " + key,
                   minimum=0)
        for summary_key, report_key in (("elapsedMs", "elapsedMs"),
                                        ("proofElapsedMs", "proofElapsedMs"),
                                        ("routingElapsedMs", "routingElapsedMs"),
                                        ("totalWork", "totalWork"),
                                        ("hypothesisWork", "hypothesisWork")):
            if summary_key in host.get(phase, {}):
                need(host[phase][summary_key] == report[phase].get(report_key),
                     label + " host timing summary disagrees with raw " + phase + "." + report_key)

    return {
        "label": label,
        "outputDir": label,
        "reportPath": report_path.relative_to(scratch).as_posix(),
        "reportSha256": report_digest,
        "reportLength": len(report_text),
        "sourceSha256": before["sourceSha256"],
        "webSha256": before["webSha256"],
        "inputManifestSha256": before["sha256"],
        "runnerSha256": runner_before["sha256"],
        "hostMonotonicSeconds": host_monotonic,
        "wallMinusMonotonicSeconds": host_drift,
        "appTimings": summary_timings,
        "report": report,
        "rawReportBytes": raw_report,
    }


def remove_declared_timings(report):
    projected = copy.deepcopy(report)
    removed = []

    def pop(mapping, key, path):
        if isinstance(mapping, dict) and key in mapping:
            mapping.pop(key)
            removed.append(path + "/" + key)

    for key in ("totalElapsedMs", "phaseElapsedMs"):
        pop(projected, key, "")
    for phase in ("cold", "warm"):
        run = projected[phase]
        for key in TIMING_FIELDS:
            pop(run, key, "/" + phase)
        for index, stage in enumerate(run.get("stages", [])):
            pop(stage, "elapsedMs", "/{}/stages/{}".format(phase, index))
        for index, timing in enumerate(run.get("diagnosticWorkTimings", [])):
            pop(timing, "elapsedMs", "/{}/diagnosticWorkTimings/{}".format(phase, index))
            pop(timing, "maxUnitMs", "/{}/diagnosticWorkTimings/{}".format(phase, index))
        owner = run.get("owner")
        proof = owner.get("diagnosticProof") if isinstance(owner, dict) else None
        pop(proof, "elapsedMillis", "/{}/owner/diagnosticProof".format(phase))
    return projected, removed


def strict_reader(scratch, repo, row, seed, tag):
    label = row["label"]
    wrapper_path = scratch / (label + "-strict-wrapper-" + tag + ".json")
    output_path = scratch / (label + "-strict-reader-" + tag + ".txt")
    reader = repo / "docs" / "task-evidence" / "Q30" / "scale-plan-4" / "check_coordinator.py"
    parser = reader.parent / "check_d01.py"
    need(reader.is_file() and parser.is_file(), "current plan-4 reader/parser is missing")
    reader_hashes = {"check_coordinator.py": sha256(reader.read_bytes()),
                     "check_d01.py": sha256(parser.read_bytes())}

    wrapper = {"rows": [{
        "seed": seed,
        "previewSourceDigest": row["sourceSha256"],
        "previewWebDigest": row["webSha256"],
        "report": row["report"],
    }]}
    wrapper_bytes = json_bytes(wrapper)
    write_bytes_new(wrapper_path, wrapper_bytes)
    command = [sys.executable, "-B", str(reader), str(wrapper_path), "--seeds", seed,
               "--self-test"]
    run = subprocess.run(command, capture_output=True, text=True,
                         encoding="utf-8", errors="strict", check=False)
    reader_text = run.stdout + run.stderr
    write_text_new(output_path, reader_text)
    need(run.returncode == 0 and "PASS: CURRENT PLAN@4/PROVIDER@4" in reader_text,
         label + " current-plan4 strict reader failed: " + reader_text[-1600:])
    canaries = {name: int(count) for name, count in
                re.findall(r"([A-Za-z][A-Za-z0-9_-]*Canaries)=(\d+)", reader_text)}
    need("corruptionCanaries" in canaries and canaries["corruptionCanaries"] > 0,
         label + " strict reader did not report actual self-negative canary counts")
    need(reader_hashes == {"check_coordinator.py": sha256(reader.read_bytes()),
                           "check_d01.py": sha256(parser.read_bytes())},
         "strict reader/parser source changed during validation")
    return {
        "readerPath": "docs/task-evidence/Q30/scale-plan-4/check_coordinator.py",
        "readerSha256": reader_hashes["check_coordinator.py"],
        "parserPath": "docs/task-evidence/Q30/scale-plan-4/check_d01.py",
        "parserSha256": reader_hashes["check_d01.py"],
        "wrapperFile": wrapper_path.name,
        "wrapperSha256": sha256(wrapper_bytes),
        "readerOutputFile": output_path.name,
        "readerExitCode": run.returncode,
        "currentPlan4Pass": True,
        "selfTestCanaryCounts": canaries,
        "fullTimingReportPreserved": True,
    }


def write_bytes_new(path, payload):
    need(not path.exists(), "refusing to overwrite existing evidence output: " + path.name)
    path.write_bytes(payload)


def validate_pair(scratch, repo, label_a, label_b, seed, tag):
    summary_path = scratch / (label_a + "-vs-" + label_b + "-timing-parity-" + tag + ".json")
    output_paths = [summary_path]
    for label in (label_a, label_b):
        output_paths.extend((scratch / (label + "-strict-wrapper-" + tag + ".json"),
                             scratch / (label + "-strict-reader-" + tag + ".txt")))
    need(not any(path.exists() for path in output_paths),
         "one or more selected evidence output names already exist; choose a fresh tag")
    rows = [validate_host_row(scratch, label, seed) for label in (label_a, label_b)]
    reader_results = [strict_reader(scratch, repo, row, seed, tag) for row in rows]
    need(reader_results[0]["readerSha256"] == reader_results[1]["readerSha256"] and
         reader_results[0]["parserSha256"] == reader_results[1]["parserSha256"],
         "strict reader/parser identity changed between A/B validations")
    reports = [row["report"] for row in rows]
    need(reports[0]["request"] == reports[1]["request"],
         "A/B qualification requests differ")
    projections = []
    timing_paths = []
    for report in reports:
        projection, removed = remove_declared_timings(report)
        projections.append(projection)
        timing_paths.append(removed)
    need(timing_paths[0] == timing_paths[1],
         "A/B reports expose different explicitly declared timing paths")
    need(projections[0] == projections[1],
         "A/B reports differ outside the declared timing fields")

    run_summaries = []
    for row, reader in zip(rows, reader_results):
        run_summary = {key: value for key, value in row.items()
                       if key not in ("report", "rawReportBytes")}
        run_summary["strictReader"] = reader
        run_summaries.append(run_summary)
    summary = {
        "schema": 1,
        "status": "PASS",
        "purpose": "same-seed intermediate performance comparison; not a production acceptance pass",
        "seed": seed,
        "runs": run_summaries,
        "requestExact": True,
        "reportExactOutsideDeclaredTimings": True,
        "removedTimingPaths": timing_paths[0],
        "crossArmBuildEqualityRequired": False,
        "timingComparison": {
            "appColdElapsedMsAminusB": reports[0]["cold"]["elapsedMs"] -
                                       reports[1]["cold"]["elapsedMs"],
            "appColdProofElapsedMsAminusB": reports[0]["cold"]["proofElapsedMs"] -
                                            reports[1]["cold"]["proofElapsedMs"],
            "appColdRoutingElapsedMsAminusB": reports[0]["cold"]["routingElapsedMs"] -
                                              reports[1]["cold"]["routingElapsedMs"],
            "hostMonotonicSecondsAminusB": rows[0]["hostMonotonicSeconds"] -
                                           rows[1]["hostMonotonicSeconds"],
            "hostWallMinusMonotonicSecondsA": rows[0]["wallMinusMonotonicSeconds"],
            "hostWallMinusMonotonicSecondsB": rows[1]["wallMinusMonotonicSeconds"],
        },
        "limits": {
            "strictReaderPerArm": True,
            "fullRawAppTimingsPreservedInReportsAndSummary": True,
            "profileFieldsRejected": True,
            "onePairIsIntermediateEvidenceOnly": True,
            "notProductionAcceptanceOrSpeedClaim": True,
        },
    }
    write_json_new(summary_path, summary)
    for row in rows:
        report_path = scratch / row["reportPath"]
        need(sha256(report_path.read_bytes()) == row["reportSha256"],
             row["label"] + " raw report changed during validation")
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scratch", type=Path)
    parser.add_argument("repo", type=Path)
    parser.add_argument("label_a")
    parser.add_argument("label_b")
    parser.add_argument("seed")
    parser.add_argument("--tag", default="timing-pair-01",
                        help="new-output suffix; use a fresh tag to preserve prior evidence")
    args = parser.parse_args()
    need(re.fullmatch(r"[A-Za-z0-9._-]{1,96}", args.label_a) is not None and
         args.label_a not in (".", "..") and
         re.fullmatch(r"[A-Za-z0-9._-]{1,96}", args.label_b) is not None and
         args.label_b not in (".", ".."), "labels contain unsafe path characters")
    need(args.label_a != args.label_b, "A and B labels must differ")
    need(re.fullmatch(r"[A-Za-z0-9._-]{1,48}", args.tag) is not None,
         "tag must contain only letters, digits, dot, underscore, or hyphen")
    valid_seed(args.seed)
    scratch, repo = args.scratch.resolve(), args.repo.resolve()
    need(scratch.is_dir() and repo.is_dir(), "scratch or repository root is missing")
    summary_path = scratch / (args.label_a + "-vs-" + args.label_b +
                              "-timing-parity-" + args.tag + ".json")
    try:
        summary = validate_pair(scratch, repo, args.label_a, args.label_b,
                                args.seed, args.tag)
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception as error:
        failure = {
            "schema": 1,
            "status": "FAIL",
            "purpose": "same-seed intermediate performance comparison; not a production acceptance pass",
            "seed": args.seed,
            "labels": [args.label_a, args.label_b],
            "error": str(error),
        }
        if not summary_path.exists():
            try:
                write_json_new(summary_path, failure)
            except Exception:
                pass
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
