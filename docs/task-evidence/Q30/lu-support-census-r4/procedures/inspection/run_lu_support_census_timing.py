#!/usr/bin/env python3
"""Run one private Q30 LU support-census browser row.

The caller supplies a prepared control or candidate source tree.  This adapter
only creates the one-case browser spec, delegates to the maintained host runner,
and records monotonic wrapper timing plus raw report identity.  It does not
interpret support counts or claim a speed result.
"""

import hashlib
import json
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit


BASE_QUERY = (
    ("tsjChallenge", "led"), ("seed", "3"), ("tsjDebug", "true"),
    ("tsjVerifyQ30", "true"), ("tsjQ30Coordinator", "true"),
)
SUPPORT_QUERY_KEY = "tsjQ30SupportCensus"
STATE_ATTRIBUTE = "data-tsj-q30-coordinator-state"
REPORT_ATTRIBUTE = "data-tsj-q30-coordinator-report"
EXPECTED_STATE = "PASS:complete"
SEED_PATTERN = "tsjQ30Seed"


class TimingError(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise TimingError(message)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def expected_path(seed, support_enabled):
    query = list(BASE_QUERY) + [(SEED_PATTERN, seed)]
    if support_enabled:
        query.append((SUPPORT_QUERY_KEY, "true"))
    return "circuitjs.html?" + "&".join(
        key + "=" + value for key, value in query)


def validate_case_path(path, seed, support_enabled, label="case.path"):
    need(isinstance(path, str) and path, label + " is missing")
    parsed = urlsplit(path)
    need(parsed.scheme == "" and parsed.netloc == "" and
         parsed.path == "circuitjs.html" and not parsed.fragment,
         label + " must be a relative circuitjs.html URL")
    expected = list(BASE_QUERY) + [(SEED_PATTERN, seed)]
    if support_enabled:
        expected.append((SUPPORT_QUERY_KEY, "true"))
    actual = parse_qsl(parsed.query, keep_blank_values=True)
    need(actual == expected,
         label + " contains an unexpected or reordered instrumentation/query key")
    return path


def read_json(path, label):
    need(path.is_file(), label + " is missing: " + str(path))
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise TimingError("could not read " + label + ": " + str(error))


def safe_report_path(output_dir, report_file):
    need(isinstance(report_file, str) and report_file,
         "host case report path is missing")
    relative = Path(report_file.replace("/", "\\"))
    need(not relative.is_absolute() and ".." not in relative.parts,
         "host case report path escapes its row directory")
    root = output_dir.resolve()
    target = (output_dir / relative).resolve()
    need(target != root and root in target.parents,
         "host case report path is outside its row directory")
    return target


def report_phase_summary(report, phase):
    row = report.get(phase)
    if not isinstance(row, dict):
        return None
    return {
        key: row.get(key) for key in (
            "elapsedMs", "wallElapsedMs", "proofElapsedMs", "routingElapsedMs",
            "totalWork", "hypothesisWork", "cleanupComplete")
    }


def actual_support_paths(report, path=""):
    found = []
    if isinstance(report, dict):
        for key,value in report.items():
            child=path+"/"+key
            if "supportcensus" in key.casefold(): found.append(child)
            found.extend(actual_support_paths(value,child))
    elif isinstance(report,list):
        for index,value in enumerate(report):
            found.extend(actual_support_paths(value,path+"["+str(index)+"]"))
    return found


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]
    need(len(argv) == 6,
         "usage: run_lu_support_census_timing.py repo scratch source label seed off|on")
    repo, scratch, source = (Path(argv[0]).resolve(), Path(argv[1]).resolve(),
                             Path(argv[2]).resolve())
    label, seed, mode = argv[3], argv[4], argv[5]
    need(mode in ("off", "on"), "mode must be off or on")
    support_enabled = mode == "on"
    need(repo.is_dir() and scratch.is_dir() and source.is_dir(),
         "repo, scratch, or source root is missing")
    need(label and all(char.isalnum() or char in "._-" for char in label),
         "label contains unsafe path characters")
    need(seed == "10014", "this private sequence requires exact seed 10014")
    path = expected_path(seed, support_enabled)
    validate_case_path(path, seed, support_enabled)
    spec_path = scratch / (label + "-spec.json")
    log_path = scratch / (label + ".log")
    summary_path = scratch / (label + "-summary.json")
    output_dir = scratch / label
    for target in (spec_path, log_path, summary_path, output_dir):
        need(not target.exists(), "refusing to overwrite existing row output: " + target.name)
    spec = {
        "cases": [{
            "name": label,
            "path": path,
            "stateAttribute": STATE_ATTRIBUTE,
            "expectedState": EXPECTED_STATE,
            "reportAttribute": REPORT_ATTRIBUTE,
            "timeoutSeconds": 600,
        }]
    }
    spec_path.write_bytes(json_bytes(spec))
    started_wall = time.time()
    started_monotonic = time.monotonic()
    process_return = None
    runner = None
    report = None
    report_sha = None
    report_length = None
    host_command = [
        sys.executable, "-B", str(repo / "tests/browser/compiled_attribute_acceptance.py"),
        str(source), str(output_dir), str(spec_path),
    ]
    try:
        with log_path.open("w", encoding="utf-8", newline="\n") as log:
            process = subprocess.run(host_command, stdout=log, stderr=subprocess.STDOUT,
                                     check=False)
        process_return = process.returncode
        result_path = output_dir / "result.json"
        if result_path.is_file():
            runner = read_json(result_path, "host result")
            cases = runner.get("cases")
            if isinstance(cases, list) and len(cases) == 1:
                report_file = cases[0].get("reportFile")
                if report_file:
                    report_path = safe_report_path(output_dir, report_file)
                    if report_path.is_file():
                        raw = report_path.read_bytes()
                        report_sha = sha256_bytes(raw)
                        report_length = len(raw.decode("utf-8"))
                        report = json.loads(raw.decode("utf-8"))
        summary = {
            "schema": 1,
            "label": label,
            "seed": seed,
            "mode": mode,
            "supportEnabled": support_enabled,
            "casePath": path,
            "exitCode": process_return,
            "hostMonotonicSeconds": time.monotonic() - started_monotonic,
            "wallMinusMonotonicSeconds": (
                time.time() - started_wall - (time.monotonic() - started_monotonic)),
            "runnerOutcome": runner.get("outcome") if isinstance(runner, dict) else None,
            "cleanup": runner.get("cleanup") if isinstance(runner, dict) else None,
            "reportSha256": report_sha,
            "reportLength": report_length,
            "reportKeys": list(report) if isinstance(report, dict) else None,
            "supportPathsPresent": actual_support_paths(report),
            "cold": report_phase_summary(report, "cold") if report else None,
            "warm": report_phase_summary(report, "warm") if report else None,
        }
        summary_path.write_bytes(json_bytes(summary))
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")), flush=True)
        return process_return if process_return is not None else 2
    except BaseException:
        # The host output directory, log, and any partial report are preserved
        # for root review.  Do not attempt broad cleanup from this adapter.
        raise


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (TimingError, OSError, ValueError, json.JSONDecodeError) as error:
        print("TIMING ROW FAILED: " + str(error), file=sys.stderr)
        raise SystemExit(2)
