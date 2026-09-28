#!/usr/bin/env python3
"""Validate the predeclared one-seed LU profile/off measurement pair.

This is a post-run evidence validator. It never launches the browser host.
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


SCRATCH_DEFAULT = Path(
    r"<private-os-temp-scratch-root>")
REPO_DEFAULT = Path(
    r"<private-repository-root>")
PLAN_NAME = "lu-repetition-current-measurement-plan.json"
PROFILE_MODE = "profile"
PLAN_OFF_MODE = "query-off control"
RUNNER_OFF_MODE = "off"
SAMPLE_INTERVAL = 1021
ERROR_ARRAYS = (
    "pageErrors", "httpErrors", "consoleErrors", "attributeReadErrors",
    "listenerCleanupErrors",
)
TIMING_FIELDS = (
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


def write_new(path, value):
    need(not path.exists(), "refusing to overwrite existing evidence output: " + path.name)
    path.write_bytes(json_bytes(value))


def write_text_new(path, text):
    need(not path.exists(), "refusing to overwrite existing evidence output: " + path.name)
    path.write_text(text, encoding="utf-8", newline="\n")


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def integer(value, label, minimum=0):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValidationError(label + " must be an integer")
    if isinstance(value, float) and (not math.isfinite(value) or not value.is_integer()):
        raise ValidationError(label + " must be a finite whole number")
    value = int(value)
    need(value >= minimum, label + " is below its minimum")
    return value


def finite_number(value, label, minimum=None, maximum=None):
    need(isinstance(value, (int, float)) and not isinstance(value, bool) and
         math.isfinite(value), label + " must be finite")
    value = float(value)
    if minimum is not None:
        need(value >= minimum, label + " is below its minimum")
    if maximum is not None:
        need(value <= maximum, label + " exceeds its maximum")
    return value


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


def validate_run_plan(scratch, profile_label, off_label):
    plan_path = scratch / PLAN_NAME
    plan = load_json(plan_path)
    need(plan.get("schema") == 1 and plan.get("status") == "PREDECLARED",
         "measurement plan is not the predeclared schema-1 plan")
    sequence = plan.get("sequence")
    need(isinstance(sequence, list) and len(sequence) == 2,
         "measurement plan must contain exactly the profile/off pair")
    expected = [
        {"label": profile_label, "seed": "7", "mode": PROFILE_MODE},
        {"label": off_label, "seed": "7", "mode": PLAN_OFF_MODE},
    ]
    need(sequence == expected, "run labels, order, seed, or modes differ from the predeclared plan")
    need("Every 1021st" in plan.get("sample", "") and
         "immediately following scoped factor call" in plan.get("sample", ""),
         "plan does not declare the adjacent 1021-factor sample")
    need("No speed" in plan.get("limits", "") or
         "no population speed estimate" in plan.get("limits", ""),
         "plan omits the one-seed/no-speed-claim limitation")
    return plan, plan_path.read_bytes()


def validate_host_row(scratch, label, runner_mode, profile_enabled):
    output_dir = scratch / label
    need(output_dir.is_dir(), "missing completed host output directory: " + label)
    result = load_json(output_dir / "result.json")
    runner_summary = load_json(scratch / (label + "-summary.json"))
    manifest = load_json(output_dir / "input-manifest-before.json")
    spec = load_json(scratch / (label + "-spec.json"))

    need(runner_summary.get("label") == label and runner_summary.get("mode") == runner_mode and
         runner_summary.get("exitCode") == 0 and runner_summary.get("runnerOutcome") == "PASS",
         label + " host runner did not exit PASS")
    need(result.get("outcome") == "PASS" and result.get("caseCount") == 1 and
         result.get("errors") == [], label + " host result did not PASS exactly one case")
    need(result.get("inputAudit", {}).get("status") == "PASS",
         label + " input audit did not PASS")
    audit = result["inputAudit"]
    for before, after in (("beforeSha256", "afterSha256"),
                          ("sourceSha256Before", "sourceSha256After"),
                          ("webSha256Before", "webSha256After"),
                          ("runnerBeforeSha256", "runnerAfterSha256"),
                          ("fileCountBefore", "fileCountAfter")):
        need(audit.get(before) is not None and audit.get(before) == audit.get(after),
             label + " input audit changed " + before)
    need(manifest.get("sha256") == audit.get("beforeSha256") and
         manifest.get("sourceSha256") == audit.get("sourceSha256Before") and
         manifest.get("webSha256") == audit.get("webSha256Before") and
         manifest.get("fileCount") == audit.get("fileCountBefore"),
         label + " before-manifest disagrees with the host input audit")

    cleanup = result.get("cleanup", {})
    need(cleanup.get("status") == "PASS" and cleanup.get("serverStopped") is True and
         cleanup.get("ownedSurvivors") == [] and cleanup.get("errors") == [],
         label + " owned host cleanup did not PASS")
    cases = result.get("cases")
    need(isinstance(cases, list) and len(cases) == 1, label + " host case census is invalid")
    case = cases[0]
    need(case.get("case", {}).get("name") == label and case.get("outcome") == "PASS" and
         case.get("terminalReached") is True and case.get("stateMatch") is True and
         case.get("observedState") == "PASS:complete" and
         case.get("lastObservedState") == "PASS:complete" and
         case.get("timedOut") is False and case.get("navigationError") is None and
         case.get("reportObserved") is True and case.get("reportJsonValid") is True and
         case.get("reportMatch") is True,
         label + " case state, terminal, timeout, navigation, or report check failed")
    for key in ERROR_ARRAYS:
        need(case.get(key) == [], label + " has host errors in " + key)

    case_path = case.get("case", {}).get("path", "")
    profile_query_present = "tsjQ30LuRepetitionProfile=true" in case_path
    need(profile_query_present == profile_enabled,
         label + " profile query presence does not match the requested mode")
    cases_spec = spec.get("cases")
    need(isinstance(cases_spec, list) and len(cases_spec) == 1 and
         cases_spec[0].get("name") == label and
         ("tsjQ30LuRepetitionProfile=true" in cases_spec[0].get("path", "")) ==
         profile_enabled, label + " browser spec disagrees with its mode")

    report_path = safe_report_path(output_dir, case.get("reportFile"))
    raw_report = report_path.read_bytes()
    report_text = raw_report.decode("utf-8")
    report_digest = sha256(raw_report)
    need(case.get("reportSha256") == report_digest and
         case.get("reportLength") == len(report_text),
         label + " embedded report receipt differs from the standalone UTF-8 report")
    report = json.loads(report_text)
    need(isinstance(report, dict) and report.get("status") == "PASS",
         label + " standalone coordinator report is not a PASS object")
    embedded_report = case.get("report")
    if embedded_report is not None:
        if isinstance(embedded_report, str):
            need(embedded_report.encode("utf-8") == raw_report,
                 label + " inline report string differs from the standalone report")
        else:
            need(embedded_report == report,
                 label + " inline report object differs from the standalone report")
    return {
        "label": label,
        "runnerMode": runner_mode,
        "profileEnabled": profile_enabled,
        "outputDir": label,
        "reportPath": report_path.relative_to(scratch).as_posix(),
        "reportSha256": report_digest,
        "sourceSha256": manifest["sourceSha256"],
        "webSha256": manifest["webSha256"],
        "inputManifestSha256": manifest["sha256"],
        "report": report,
        "rawReportBytes": raw_report,
    }


def validate_profile_record(profile, phase, report, measurement_deadline):
    need(isinstance(profile, dict), phase + " LU profile must be an object")
    need(profile.get("phase") == phase, phase + " profile phase label mismatch")
    need("nonlinear runCircuitOwned LU inputs" in profile.get("scope", "") and
         "verified coordinator advance windows" in profile.get("scope", ""),
         phase + " profile scope is not the owned coordinator window")
    need("1-based" in profile.get("positionIndexing", "") and
         "resets each phase" in profile.get("positionIndexing", ""),
         phase + " profile position indexing is undocumented")
    need(profile.get("sampleIntervalFactorCalls") == SAMPLE_INTERVAL,
         phase + " sampler interval changed")

    factor_calls = integer(profile.get("factorCalls"), phase + " factorCalls")
    starts = profile.get("sampleStartFactorCallPositions")
    comparisons = profile.get("compareFactorCallPositions")
    need(isinstance(starts, list) and isinstance(comparisons, list),
         phase + " sample position arrays are missing")
    starts = [integer(value, phase + " start position", 1) for value in starts]
    comparisons = [integer(value, phase + " compare position", 1) for value in comparisons]
    need(starts == sorted(set(starts)) and comparisons == sorted(set(comparisons)),
         phase + " sample positions are duplicated or out of order")
    expected_starts = list(range(SAMPLE_INTERVAL, factor_calls + 1, SAMPLE_INTERVAL))
    need(starts == expected_starts,
         phase + " starts do not follow the exact 1021-factor cadence")
    need(all(position <= factor_calls for position in comparisons) and
         all(position - 1 in starts for position in comparisons),
         phase + " comparison was not the immediately following sampled factor call")

    sampled_starts = integer(profile.get("sampledStarts"), phase + " sampledStarts")
    compare_attempts = integer(profile.get("compareAttempts"), phase + " compareAttempts")
    completed = integer(profile.get("completedPairs"), phase + " completedPairs")
    matched = integer(profile.get("matchedPairs"), phase + " matchedPairs")
    different = integer(profile.get("differentPairs"), phase + " differentPairs")
    aborted = integer(profile.get("abortedPendingPairs"), phase + " abortedPendingPairs")
    matrix_inputs = integer(profile.get("sampledMatrixInputs"), phase + " sampledMatrixInputs")
    need(sampled_starts == len(starts) and compare_attempts == len(comparisons),
         phase + " sample position/count fields disagree")
    need(completed == matched + different and completed <= compare_attempts and
         sampled_starts == completed + aborted and compare_attempts <= sampled_starts and
         matrix_inputs == sampled_starts + compare_attempts,
         phase + " sampler pair/input counts do not partition consistently")

    sizes = profile.get("sampledSizeGroups")
    need(isinstance(sizes, dict), phase + " sampled size groups are missing")
    size_counts = {}
    for key, count in sizes.items():
        need(isinstance(key, str) and re.fullmatch(r"0|[1-9][0-9]*", key) is not None,
             phase + " has a noncanonical sampled matrix size")
        size_counts[int(key)] = integer(count, phase + " matrix-size group count", 1)
    need(sum(size_counts.values()) == matrix_inputs,
         phase + " matrix-size group total differs from sampled inputs")
    minimum = integer(profile.get("minimumMatrixSize"), phase + " minimumMatrixSize")
    maximum = integer(profile.get("maximumMatrixSize"), phase + " maximumMatrixSize")
    if matrix_inputs == 0:
        need(not size_counts and minimum == 0 and maximum == 0,
             phase + " empty sampler has nonempty size metadata")
    else:
        need(size_counts and minimum == min(size_counts) and maximum == max(size_counts),
             phase + " matrix-size bounds disagree with sampled groups")

    compared_entries = integer(profile.get("comparedEntries"), phase + " comparedEntries")
    signed_zero_entries = integer(profile.get("signedZeroDifferenceEntries"),
                                  phase + " signedZeroDifferenceEntries")
    signed_zero_pairs = integer(profile.get("signedZeroDifferencePairs"),
                                phase + " signedZeroDifferencePairs")
    need(signed_zero_pairs <= different and signed_zero_entries >= signed_zero_pairs,
         phase + " signed-zero difference counts are inconsistent")
    need(signed_zero_entries <= compared_entries,
         phase + " signed-zero entries exceed all compared entries")
    if completed == 0:
        need(compared_entries == 0, phase + " compared entries exist without a completed comparison")
    else:
        minimum_cells = minimum * minimum
        maximum_cells = maximum * maximum
        need(completed * minimum_cells <= compared_entries <= completed * maximum_cells,
             phase + " compared-entry total is outside the observed matrix-size bounds")
        square_gcd = 0
        for size in size_counts:
            square_gcd = math.gcd(square_gcd, size * size)
        if square_gcd:
            need(compared_entries % square_gcd == 0,
                 phase + " compared-entry total is not a sum of observed matrix sizes")
        else:
            need(compared_entries == 0,
                 phase + " zero-size matrices produced nonzero compared entries")
        if len(size_counts) == 1:
            only_size = next(iter(size_counts))
            need(compared_entries == completed * only_size * only_size,
                 phase + " compared-entry total disagrees with the single observed size")
        if aborted == 0:
            sampled_cell_total = sum(size * size * count
                                     for size, count in size_counts.items())
            need(sampled_cell_total % 2 == 0 and
                 compared_entries == sampled_cell_total // 2,
                 phase + " no-abort compared entries differ from paired sampled matrix inputs")

    overhead = finite_number(profile.get("probeOverheadMillis"),
                             phase + " probeOverheadMillis", minimum=0)
    need(overhead <= measurement_deadline,
         phase + " sample-only overhead exceeds the measurement job deadline")
    need(profile.get("probeClock") == "performance.now" and
         integer(profile.get("probeClockErrors"), phase + " probeClockErrors") == 0,
         phase + " browser sampler did not use a clean monotonic performance.now clock")
    overhead_scope = profile.get("probeOverheadScope", "")
    need("excludes per-factor" in overhead_scope and "compilation" in overhead_scope,
         phase + " sample overhead scope omits its instrumentation limitations")

    phase_elapsed = finite_number(report.get(phase, {}).get("elapsedMs"),
                                  phase + " phase elapsedMs", minimum=0)
    need(overhead <= phase_elapsed,
         phase + " sample-only probe overhead exceeds its recorded phase elapsed time")
    return {
        "phase": phase,
        "factorCalls": factor_calls,
        "startPositions": starts,
        "comparePositions": comparisons,
        "sampledStarts": sampled_starts,
        "compareAttempts": compare_attempts,
        "completedPairs": completed,
        "matchedPairs": matched,
        "differentPairs": different,
        "abortedPendingPairs": aborted,
        "sampledMatrixInputs": matrix_inputs,
        "workScopeWindows": integer(profile.get("workScopeWindows"),
                                     phase + " workScopeWindows"),
        "sampledSizeGroups": {str(key): size_counts[key] for key in sorted(size_counts)},
        "minimumMatrixSize": minimum,
        "maximumMatrixSize": maximum,
        "comparedEntries": compared_entries,
        "signedZeroDifferenceEntries": signed_zero_entries,
        "signedZeroDifferencePairs": signed_zero_pairs,
        "probeOverheadMillis": overhead,
        "probeClock": profile["probeClock"],
        "probeClockErrors": 0,
        "probeOverheadWithinPhaseElapsed": overhead <= phase_elapsed,
        "overheadScope": overhead_scope,
        "interpretation": "sample feasibility only; no match-rate threshold or speed claim",
    }


def remove_profile_fields(report, profile_enabled):
    removed = []
    projected = copy.deepcopy(report)
    for phase in ("cold", "warm"):
        run = projected.get(phase)
        need(isinstance(run, dict), "coordinator report is missing " + phase)
        if profile_enabled:
            need(isinstance(run.get("luRepetitionProfile"), dict),
                 phase + " profile-mode report omitted luRepetitionProfile")
            run.pop("luRepetitionProfile")
            removed.append("/" + phase + "/luRepetitionProfile")
        else:
            need("luRepetitionProfile" not in run,
                 phase + " query-off report unexpectedly contains a profile")
    return projected, removed


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


def strict_reader(scratch, repo, label, seed, report, removed_profile_paths,
                  raw_report_sha, tag, self_test):
    wrapper_path = scratch / (label + "-strict-wrapper-" + tag + ".json")
    reader_output_path = scratch / (label + "-strict-reader-" + tag + ".txt")
    need(not wrapper_path.exists() and not reader_output_path.exists(),
         "strict-reader output name already exists for " + label)
    wrapper_row = {
        "seed": seed,
        "previewSourceDigest": report["sourceSha256"],
        "previewWebDigest": report["webSha256"],
        "report": report["readerReport"],
    }
    wrapper = {"rows": [wrapper_row]}
    wrapper_bytes = json_bytes(wrapper)
    wrapper_path.write_bytes(wrapper_bytes)
    wrapper_digest = sha256(wrapper_bytes)

    reader = repo / "docs" / "task-evidence" / "Q30" / "scale-plan-4" / "check_coordinator.py"
    need(reader.is_file(), "current scale-plan-4 coordinator reader is missing")
    command = [sys.executable, "-B", str(reader), str(wrapper_path), "--seeds", seed]
    if self_test:
        command.append("--self-test")
    completed = subprocess.run(command, capture_output=True, text=True,
                               encoding="utf-8", errors="strict", check=False)
    reader_text = completed.stdout + completed.stderr
    write_text_new(reader_output_path, reader_text)
    need(completed.returncode == 0 and
         "PASS: CURRENT PLAN@4/PROVIDER@4" in reader_text,
         label + " strict current-plan4 reader rejected the sanitized report: " +
         reader_text[-1200:])
    canary_count = 0
    if self_test:
        match = re.search(r"corruptionCanaries=(\d+)", reader_text)
        need(match is not None, label + " strict reader did not report self-negative canaries")
        canary_count = int(match.group(1))
        need(canary_count > 0, label + " strict reader ran no self-negative canaries")
    return {
        "readerPath": "docs/task-evidence/Q30/scale-plan-4/check_coordinator.py",
        "wrapperFile": wrapper_path.name,
        "wrapperSha256": wrapper_digest,
        "readerOutputFile": reader_output_path.name,
        "readerExitCode": completed.returncode,
        "currentPlan4Pass": True,
        "selfNegativeCanaries": canary_count,
        "removedProfilePaths": removed_profile_paths,
        "rawReportSha256": raw_report_sha,
    }


def parity_projection(report, profile_enabled):
    no_profile, profile_paths = remove_profile_fields(report, profile_enabled)
    projection, timing_paths = remove_declared_timings(no_profile)
    return projection, profile_paths, timing_paths


def validate_pair(scratch, repo, plan, profile_label, off_label, tag):
    profile_row = validate_host_row(scratch, profile_label, PROFILE_MODE, True)
    off_row = validate_host_row(scratch, off_label, RUNNER_OFF_MODE, False)
    for key in ("sourceSha256", "webSha256", "inputManifestSha256"):
        need(profile_row[key] == off_row[key],
             "profile/off rows used different candidate inputs: " + key)

    runs = []
    for row, profile_enabled in ((profile_row, True), (off_row, False)):
        report = row["report"]
        reader_projection, removed_profile_paths = remove_profile_fields(report, profile_enabled)
        _, timing_paths = remove_declared_timings(reader_projection)
        row["readerReport"] = reader_projection
        row["removedProfilePaths"] = removed_profile_paths
        row["removedTimingPaths"] = timing_paths
        deadline = integer(report.get("measurementMaxJobMillis"),
                           "measurementMaxJobMillis", 1)
        samplers = {}
        if profile_enabled:
            for phase in ("cold", "warm"):
                profile = report[phase]["luRepetitionProfile"]
                samplers[phase] = validate_profile_record(profile, phase, report, deadline)
        reader = strict_reader(scratch, repo, row["label"], "7", row,
                               removed_profile_paths, row["reportSha256"], tag,
                               self_test=True)
        runs.append({
            "label": row["label"],
            "planMode": PROFILE_MODE if profile_enabled else PLAN_OFF_MODE,
            "runnerMode": row["runnerMode"],
            "reportFile": row["reportPath"],
            "rawReportSha256": row["reportSha256"],
            "strictReader": reader,
            "sampler": samplers if profile_enabled else None,
        })

    need(profile_row["report"]["request"] == off_row["report"]["request"],
         "profile/off qualification requests differ")
    profile_projection, profile_paths, profile_timing_paths = parity_projection(
        profile_row["report"], True)
    off_projection, off_paths, off_timing_paths = parity_projection(
        off_row["report"], False)
    need(profile_paths == ["/cold/luRepetitionProfile", "/warm/luRepetitionProfile"] and
         off_paths == [], "profile field removal was not limited to the declared query output")
    need(profile_projection == off_projection,
         "profile/off reports differ outside the explicitly removed sampler/timing paths")
    need(profile_timing_paths == off_timing_paths,
         "profile/off reports expose different declared timing paths")

    summary_path = scratch / ("lu-repetition-pair-validation-" + tag + ".json")
    summary = {
        "schema": 1,
        "status": "PASS",
        "purpose": "single-seed sampler feasibility only; not an acceptance or speed result",
        "planFile": PLAN_NAME,
        "planSha256": sha256((scratch / PLAN_NAME).read_bytes()),
        "seed": "7",
        "runs": runs,
        "inputParity": {
            "sourceSha256": profile_row["sourceSha256"],
            "webSha256": profile_row["webSha256"],
            "inputManifestSha256": profile_row["inputManifestSha256"],
        },
        "parity": {
            "requestExact": True,
            "reportExactOutsideDeclaredTimingsAndProfileFields": True,
            "removedProfilePaths": ["/cold/luRepetitionProfile", "/warm/luRepetitionProfile"],
            "removedTimingPaths": profile_timing_paths,
            "workCacheOwnerPhysicalAndProofParity": True,
        },
        "limits": {
            "oneSeedOnly": True,
            "noAcceptanceOrSpeedClaim": True,
            "probeClock": "performance.now with zero reported clock errors",
            "probeOverheadIncludes": "sample capture/compare work inside the sampler clock brackets",
            "probeOverheadExcludes": "per-factor window/counter/modulo and compilation/code-generation costs",
            "abortedPairsAreReportedAndNotTreatedAsMatches": True,
            "noMinimumMatchRate": True,
        },
    }
    write_new(summary_path, summary)

    # Verify the validator itself did not alter either raw report after hashing.
    for row in (profile_row, off_row):
        report_path = scratch / row["reportPath"]
        need(sha256(report_path.read_bytes()) == row["reportSha256"],
             row["label"] + " raw report changed during validation")
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scratch", type=Path, default=SCRATCH_DEFAULT)
    parser.add_argument("--repo", type=Path, default=REPO_DEFAULT)
    parser.add_argument("--tag", default="profile-off-01",
                        help="new-output suffix; choose a new tag to preserve/revalidate failures")
    args = parser.parse_args()
    need(re.fullmatch(r"[A-Za-z0-9._-]{1,48}", args.tag) is not None,
         "tag must contain only letters, digits, dot, underscore, or hyphen")
    scratch = args.scratch.resolve()
    repo = args.repo.resolve()
    need(scratch.is_dir() and repo.is_dir(), "scratch or repository root is missing")
    profile_label = "lu-repetition-current-01-profile-7"
    off_label = "lu-repetition-current-02-off-7"
    summary_path = scratch / ("lu-repetition-pair-validation-" + args.tag + ".json")
    try:
        plan, _ = validate_run_plan(scratch, profile_label, off_label)
        summary = validate_pair(scratch, repo, plan, profile_label, off_label, args.tag)
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception as error:
        failure = {
            "schema": 1,
            "status": "FAIL",
            "purpose": "single-seed sampler feasibility only; not an acceptance or speed result",
            "error": str(error),
        }
        if not summary_path.exists():
            try:
                write_new(summary_path, failure)
            except Exception:
                pass
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
