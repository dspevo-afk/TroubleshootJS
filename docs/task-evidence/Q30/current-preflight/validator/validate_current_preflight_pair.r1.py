#!/usr/bin/env python3
"""Validate the predeclared seed-10014 LU-preflight profile/off evidence pair.

Reads completed host artifacts, checks sampler metadata and its negative
canaries, and runs the maintained current-plan-4 reader once per arm. It never
launches a browser, host, build, or test.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


FROZEN_PLAN_SHA256 = "e822950a95469bd5b547fdc27494756ec85f264cd7817c3c804cb3d1f548fe5c"
FROZEN_HELPER_SHA256 = "56d11822c0456ef4372bfa7cdfc0f3d79965e6aa895fcfd665e0d4a8b72da312"
FROZEN_LEGACY_CANARY_SHA256 = "a776cfa51880a2a13d87e4cf5be7daa28ee1897213cbf522d13cdb13f27c1342"
FROZEN_LEGACY_RECEIPT_SHA256 = "a4a4850853716795848026863c8164a0149980a94155ae6a5413de38c2860d25"
FROZEN_RUNNER_SHA256 = "81eab6fc77670c01d25119313b4795f86d131c5164796937cf400b7f4d05c160"
FROZEN_PROFILE_CANDIDATE_HASHES = {
    "src/com/lushprojects/circuitjs1/client/CirSim.java":
        "15a576e8a254def5e1a90dcd860992ec36cacb9d1c1dbfd61026e52988cf340d",
    "src/com/lushprojects/circuitjs1/client/Q30CoordinatorQualificationVerifier.java":
        "b378808e40c9173e337fea996c74375c361c251f1c9032d90504b777f0f147d8",
    "tests/contracts/Q30LuRepetitionProfileContractTest.java":
        "3bf162017b8967304e87a07928c3fdd5d7966dd3a7945e988560a9ed4b9d5e3c",
}
FROZEN_PROFILE_LABEL = "current-preflight-01-profile-10014"
FROZEN_OFF_LABEL = "current-preflight-02-off-10014"
EXPECTED_PLAN_SEQUENCE = (
    {"label": FROZEN_PROFILE_LABEL, "seed": "10014", "mode": "profile"},
    {"label": FROZEN_OFF_LABEL, "seed": "10014", "mode": "query-off control"},
)
RUNNER_PROFILE_MODE = "profile"
RUNNER_OFF_MODE = "off"
ROW_READ_METRIC_DEFINITION = (
    "one sampled input-matrix traversal per start/compare; first-hit counts model "
    "short-circuit scans; diagonal-first probes diagonal once then ascending columns "
    "omitting it")
PREFLIGHT_TIMING_SCOPE = (
    "sampled zero-row preflight only; excludes workspace reset/clear, LU arithmetic, "
    "and sampling traversal")


def need(condition, message):
    if not condition:
        raise AssertionError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def canonical_seed(seed):
    need(isinstance(seed, str) and re.fullmatch(r"-?(0|[1-9][0-9]*)", seed) is not None,
         "seed must be a canonical signed-long string")
    number = int(seed)
    need(-(2 ** 63) <= number < 2 ** 63, "seed is outside signed-long range")


def load_frozen_helper(scratch):
    helper_path = scratch / "validate_lu_repetition_pair.py"
    checker_path = scratch / "check_lu_sampler_metadata_canaries.py"
    legacy_receipt_path = scratch / "lu-sampler-metadata-canaries.json"
    helper_bytes = helper_path.read_bytes()
    checker_bytes = checker_path.read_bytes()
    receipt_bytes = legacy_receipt_path.read_bytes()
    helper_digest, checker_digest, receipt_digest = map(
        sha256, (helper_bytes, checker_bytes, receipt_bytes))
    need(helper_digest == FROZEN_HELPER_SHA256,
         "frozen seed-7 validation helper hash changed")
    need(checker_digest == FROZEN_LEGACY_CANARY_SHA256,
         "frozen seed-7 metadata-canary checker hash changed")
    need(receipt_digest == FROZEN_LEGACY_RECEIPT_SHA256,
         "frozen seed-7 metadata-canary receipt hash changed")

    runner_path = scratch / "run_current_preflight.py"
    runner_digest = sha256(runner_path.read_bytes())
    need(runner_digest == FROZEN_RUNNER_SHA256,
         "current-preflight host runner source hash changed")
    candidate_dir = scratch / "current-preflight-profile-draft" / "candidate"
    candidate_hashes = {}
    for relative_path, expected_digest in FROZEN_PROFILE_CANDIDATE_HASHES.items():
        candidate_path = candidate_dir.joinpath(*relative_path.split("/"))
        digest = sha256(candidate_path.read_bytes())
        need(digest == expected_digest,
             "frozen current-preflight candidate source hash changed: " + relative_path)
        candidate_hashes[relative_path] = digest

    # The old canary script has deliberate top-level execution/output; bind its
    # frozen receipt and repeat its mutations below instead of importing it.
    legacy = json.loads(receipt_bytes.decode("utf-8"))
    need(legacy.get("status") == "PASS" and
         legacy.get("validatorSha256") == helper_digest and
         len(legacy.get("negativeMetadataCanaries", [])) == 12 and
         all(item.get("status") == "REJECTED_AS_EXPECTED"
             for item in legacy["negativeMetadataCanaries"]),
         "frozen seed-7 metadata-canary receipt is not a complete PASS")

    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("frozen_q30_lu_pair_validator", helper_path)
    need(spec is not None and spec.loader is not None,
         "could not load the frozen sampler-validation helper")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module, {
        "helperPath": helper_path.name,
        "helperSha256": helper_digest,
        "legacyCanaryCheckerPath": checker_path.name,
        "legacyCanaryCheckerSha256": checker_digest,
        "legacyCanaryReceiptPath": legacy_receipt_path.name,
        "legacyCanaryReceiptSha256": receipt_digest,
        "legacyNegativeMetadataCanaries": len(legacy["negativeMetadataCanaries"]),
        "currentPreflightRunnerPath": runner_path.name,
        "currentPreflightRunnerSha256": runner_digest,
        "profileCandidateSourceFiles": candidate_hashes,
    }


def validate_plan(plan_path, seed, profile_label, off_label):
    raw = plan_path.read_bytes()
    digest = sha256(raw)
    need(digest == FROZEN_PLAN_SHA256,
         "predeclared plan hash changed; do not silently accept a rewritten sequence")
    plan = json.loads(raw.decode("utf-8"))
    need(plan.get("schema") == 1 and plan.get("status") == "PREDECLARED",
         "measurement plan is not the frozen PREDECLARED schema-1 plan")
    expected = [dict(row) for row in EXPECTED_PLAN_SEQUENCE]
    need(seed == "10014" and profile_label == FROZEN_PROFILE_LABEL and
         off_label == FROZEN_OFF_LABEL and plan.get("sequence") == expected,
         "CLI seed/labels/order/modes do not match the predeclared seed-10014 pair")
    need("40-package" in plan.get("scope", "") and
         "no population speed estimate" in plan.get("limits", ""),
         "plan scope or one-seed limitation changed")
    return plan, digest


def validate_seed_query(path, seed, label, profile_enabled):
    need(isinstance(path, str), label + " browser URL is missing")
    expected_path = (
        "circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ30=true&"
        "tsjQ30Coordinator=true&tsjQ30Seed=" + seed +
        ("&tsjQ30LuRepetitionProfile=true" if profile_enabled else ""))
    need(path == expected_path,
         label + " browser URL differs from the exact planned diagnostic case")
    query = parse_qs(urlsplit(path).query, keep_blank_values=True)
    need(query.get("tsjQ30Seed") == [seed],
         label + " browser URL does not carry the exact planned seed")
    expected_profile_value = ["true"] if profile_enabled else None
    need(query.get("tsjQ30LuRepetitionProfile") == expected_profile_value,
         label + " browser URL profile query does not match its planned arm")


def check_profile_keys(value, enabled, path="report"):
    if isinstance(value, dict):
        for key, item in value.items():
            if "profile" in str(key).casefold():
                allowed = (enabled and key == "luRepetitionProfile" and
                           path in ("report.cold", "report.warm"))
                need(allowed, "unexpected sampler/profile field at {}.{}".format(path, key))
            check_profile_keys(item, enabled, path + "." + str(key))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            check_profile_keys(item, enabled, "{}[{}]".format(path, index))


def validate_before_after_files(scratch, label, helper):
    output_dir = scratch / label
    before = load_json(output_dir / "input-manifest-before.json")
    after = load_json(output_dir / "input-manifest-after.json")
    runner_before = load_json(output_dir / "runner-before.json")
    runner_after = load_json(output_dir / "runner-after.json")
    content_before = helper.manifest_content(before)
    content_after = helper.manifest_content(after)
    need(content_before == content_after,
         label + " before/after input file manifests differ")
    audit = load_json(output_dir / "result.json")["inputAudit"]
    need(before.get("sha256") == audit.get("beforeSha256") and
         after.get("sha256") == audit.get("afterSha256") and
         before.get("sourceSha256") == audit.get("sourceSha256Before") and
         after.get("sourceSha256") == audit.get("sourceSha256After") and
         before.get("webSha256") == audit.get("webSha256Before") and
         after.get("webSha256") == audit.get("webSha256After") and
         before.get("fileCount") == audit.get("fileCountBefore") and
         after.get("fileCount") == audit.get("fileCountAfter"),
         label + " manifests disagree with the host input audit")
    need(runner_before == runner_after and
         runner_before.get("sha256") == audit.get("runnerBeforeSha256") and
         runner_after.get("sha256") == audit.get("runnerAfterSha256"),
         label + " browser-runner identity changed or disagrees with its audit")
    return before, after, runner_before


def validate_preflight_metadata(profile, phase, report, helper):
    # Start with the frozen adjacent-LU snapshot, size, equality, and timing checks.
    base_record = helper.validate_profile_record(
        profile, phase, report, report["measurementMaxJobMillis"])
    sizes = profile["sampledSizeGroups"]
    size_counts = {int(size): helper.integer(count, phase + " size count", 1)
                   for size, count in sizes.items()}
    matrix_inputs = helper.integer(profile["sampledMatrixInputs"],
                                   phase + " sampledMatrixInputs", 1)
    minimum = helper.integer(profile["minimumMatrixSize"],
                             phase + " minimumMatrixSize", 1)
    maximum = helper.integer(profile["maximumMatrixSize"],
                             phase + " maximumMatrixSize", 1)
    need(matrix_inputs == sum(size_counts.values()) and
         minimum == min(size_counts) and maximum == max(size_counts),
         phase + " preflight size census disagrees with sampler inputs")

    cell_reads = helper.integer(profile.get("sampledInputMatrixCellReads"),
                                phase + " sampledInputMatrixCellReads", 1)
    sampled_rows = helper.integer(profile.get("sampledRows"),
                                  phase + " sampledRows", 1)
    expected_cells = sum(size * size * count for size, count in size_counts.items())
    expected_rows = sum(size * count for size, count in size_counts.items())
    need(cell_reads == expected_cells,
         phase + " sampled input cell reads differ from sampled matrix sizes")
    need(sampled_rows == expected_rows,
         phase + " sampled row count differs from sampled matrix sizes")

    left_reads = helper.integer(profile.get("leftToRightFirstNonzeroReads"),
                                phase + " leftToRightFirstNonzeroReads", 1)
    diagonal_reads = helper.integer(profile.get("diagonalFirstNonzeroReads"),
                                    phase + " diagonalFirstNonzeroReads", 1)
    need(sampled_rows <= left_reads <= cell_reads and
         sampled_rows <= diagonal_reads <= cell_reads,
         phase + " first-hit read counts fall outside the sampled row/cell bounds")
    nonzero_diagonals = helper.integer(profile.get("nonzeroDiagonals"),
                                       phase + " nonzeroDiagonals")
    zero_rows = helper.integer(profile.get("zeroRows"), phase + " zeroRows")
    need(nonzero_diagonals + zero_rows <= sampled_rows,
         phase + " diagonal and zero-row categories exceed sampled rows")
    need(profile.get("rowReadMetricDefinition") == ROW_READ_METRIC_DEFINITION,
         phase + " row-read metric definition changed")

    timing_attempts = helper.integer(profile.get("zeroRowPreflightTimingAttempts"),
                                     phase + " zeroRowPreflightTimingAttempts")
    timing_completions = helper.integer(profile.get("zeroRowPreflightTimingCompletions"),
                                        phase + " zeroRowPreflightTimingCompletions")
    timing_samples = helper.integer(profile.get("zeroRowPreflightTimingSamples"),
                                    phase + " zeroRowPreflightTimingSamples")
    timing_errors = helper.integer(profile.get("zeroRowPreflightTimingErrors"),
                                   phase + " zeroRowPreflightTimingErrors")
    need(timing_attempts == matrix_inputs and timing_completions == timing_attempts and
         timing_samples + timing_errors == timing_attempts and
         timing_errors == 0 and timing_samples == timing_attempts,
         phase + " zero-row preflight timing counts do not match sampled LU inputs")
    need(profile.get("zeroRowPreflightClock") == "performance.now",
         phase + " zero-row preflight timing is not browser performance.now")
    need(profile.get("zeroRowPreflightTimingScope") == PREFLIGHT_TIMING_SCOPE,
         phase + " zero-row preflight timing scope changed")
    preflight_ms = helper.finite_number(profile.get("zeroRowPreflightMillis"),
                                        phase + " zeroRowPreflightMillis", minimum=0)
    phase_ms = helper.finite_number(report.get(phase, {}).get("elapsedMs"),
                                    phase + " elapsedMs", minimum=0)
    deadline = helper.integer(report.get("measurementMaxJobMillis"),
                              "measurementMaxJobMillis", 1)
    need(preflight_ms <= deadline and preflight_ms <= phase_ms,
         phase + " preflight scan time exceeds the measured run/deadline")

    # Keep full position arrays in the raw report; summarize validated counts here.
    base_record.pop("startPositions", None)
    base_record.pop("comparePositions", None)
    base_record.update({
        "sampledInputMatrixCellReads": cell_reads,
        "sampledRows": sampled_rows,
        "leftToRightFirstNonzeroReads": left_reads,
        "diagonalFirstNonzeroReads": diagonal_reads,
        "nonzeroDiagonals": nonzero_diagonals,
        "zeroRows": zero_rows,
        "rowReadMetricDefinition": profile["rowReadMetricDefinition"],
        "zeroRowPreflightMillis": preflight_ms,
        "zeroRowPreflightTimingScope": profile["zeroRowPreflightTimingScope"],
        "zeroRowPreflightTimingAttempts": timing_attempts,
        "zeroRowPreflightTimingCompletions": timing_completions,
        "zeroRowPreflightTimingSamples": timing_samples,
        "zeroRowPreflightTimingErrors": timing_errors,
        "zeroRowPreflightClock": profile["zeroRowPreflightClock"],
        "rowCountIdentityValidated": True,
        "cellCountIdentityValidated": True,
        "timingAttemptPartitionValidated": True,
    })
    return base_record


def run_metadata_negative_canaries(profile, phase, report, helper):
    deadline = report["measurementMaxJobMillis"]
    cases = []

    def rejects(name, mutate):
        changed = copy.deepcopy(profile)
        mutate(changed)
        try:
            validate_preflight_metadata(changed, phase, report, helper)
        except (helper.ValidationError, AssertionError, KeyError, TypeError, ValueError) as error:
            cases.append({"case": name, "status": "REJECTED_AS_EXPECTED",
                          "reason": str(error)})
        else:
            raise AssertionError("sampler metadata corruption accepted: " + name)

    # Repeat the original validator's primary mutations against this candidate record.
    rejects("foreign phase", lambda p: p.__setitem__("phase", "warm" if phase == "cold" else "cold"))
    def nonadjacent(p):
        starts, compares = p["sampleStartFactorCallPositions"], p["compareFactorCallPositions"]
        if compares:
            compares[0] += 1
        else:
            compares.append(starts[0] + 2)
    rejects("nonadjacent comparison", nonadjacent)
    def duplicate_start(p):
        starts = p["sampleStartFactorCallPositions"]
        starts.insert(1, starts[0])
    rejects("duplicate start", duplicate_start)
    rejects("completed-pair count", lambda p: p.__setitem__("completedPairs", p["completedPairs"] + 1))
    rejects("match count", lambda p: p.__setitem__("matchedPairs", p["matchedPairs"] + 1))
    rejects("compared-cell census", lambda p: p.__setitem__("comparedEntries", -1))
    rejects("clock error", lambda p: p.__setitem__("probeClockErrors", 1))
    rejects("wall-clock probe", lambda p: p.__setitem__("probeClock", "Date.now"))
    rejects("signed-zero entry overflow",
            lambda p: p.__setitem__("signedZeroDifferenceEntries", p["comparedEntries"] + 1))
    def size_drift(p):
        key = next(iter(p["sampledSizeGroups"]))
        p["sampledSizeGroups"][key] += 1
    rejects("sampled size census", size_drift)
    rejects("snapshot/compare overhead deadline",
            lambda p: p.__setitem__("probeOverheadMillis", deadline + 1))

    # One corruption for each added row/read/preflight metadata family.
    rejects("sampled input cell-read census",
            lambda p: p.__setitem__("sampledInputMatrixCellReads", 0))
    rejects("sampled row census", lambda p: p.__setitem__("sampledRows", 0))
    rejects("left-to-right row-read bound",
            lambda p: p.__setitem__("leftToRightFirstNonzeroReads", 0))
    rejects("diagonal-first row-read bound",
            lambda p: p.__setitem__("diagonalFirstNonzeroReads", 0))
    rejects("nonzero-diagonal row partition",
            lambda p: p.__setitem__("nonzeroDiagonals", p["sampledRows"] + 1))
    rejects("zero-row partition", lambda p: p.__setitem__("zeroRows", p["sampledRows"] + 1))
    rejects("row-read definition", lambda p: p.__setitem__("rowReadMetricDefinition", "ambiguous"))
    rejects("preflight timer count", lambda p: p.__setitem__(
        "zeroRowPreflightTimingAttempts", p["sampledMatrixInputs"] + 1))
    rejects("preflight timer completion count", lambda p: p.__setitem__(
        "zeroRowPreflightTimingCompletions", p["zeroRowPreflightTimingCompletions"] - 1))
    rejects("preflight timer sample count", lambda p: p.__setitem__(
        "zeroRowPreflightTimingSamples", p["zeroRowPreflightTimingSamples"] - 1))
    rejects("preflight timer error count", lambda p: p.__setitem__(
        "zeroRowPreflightTimingErrors", 1))
    rejects("preflight timer clock", lambda p: p.__setitem__(
        "zeroRowPreflightClock", "Date.now"))
    rejects("preflight timer scope", lambda p: p.__setitem__(
        "zeroRowPreflightTimingScope", "full LU and sampling"))
    rejects("preflight timer bounds", lambda p: p.__setitem__(
        "zeroRowPreflightMillis", deadline + 1))
    return cases


def validate_off_profile_absence(off_report, profile_record, helper):
    changed = copy.deepcopy(off_report)
    changed["cold"]["luRepetitionProfile"] = copy.deepcopy(profile_record)
    try:
        helper.remove_profile_fields(changed, False)
    except helper.ValidationError as error:
        return {"case": "query-off profile field", "status": "REJECTED_AS_EXPECTED",
                "reason": str(error)}
    raise AssertionError("query-off profile field corruption accepted")


def hash_reader_sources(repo):
    reader = repo / "docs" / "task-evidence" / "Q30" / "scale-plan-4" / "check_coordinator.py"
    parser = reader.parent / "check_d01.py"
    need(reader.is_file() and parser.is_file(), "current plan-4 reader/parser is missing")
    return {
        "check_coordinator.py": sha256(reader.read_bytes()),
        "check_d01.py": sha256(parser.read_bytes()),
    }


def validate_pair(args, helper, dependency_binding, plan, plan_digest):
    scratch, repo = args.scratch.resolve(), args.repo.resolve()
    profile_label, off_label, seed = args.profile_label, args.off_label, args.seed
    rows = [
        helper.validate_host_row(scratch, profile_label, RUNNER_PROFILE_MODE, True),
        helper.validate_host_row(scratch, off_label, RUNNER_OFF_MODE, False),
    ]
    for row, label, enabled in zip(rows, (profile_label, off_label), (True, False)):
        before_manifest, after_manifest, runner_before = validate_before_after_files(
            scratch, label, helper)
        case_spec = helper.load_json(scratch / (label + "-spec.json"))["cases"][0]
        result = helper.load_json(scratch / label / "result.json")
        receipt_case = result["cases"][0]["case"]
        expected_case = {
            "name": label,
            "path": ("circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&"
                     "tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=" + seed +
                     ("&tsjQ30LuRepetitionProfile=true" if enabled else "")),
            "stateAttribute": "data-tsj-q30-coordinator-state",
            "expectedState": "PASS:complete",
            "reportAttribute": "data-tsj-q30-coordinator-report",
            "timeoutSeconds": 600,
        }
        need(case_spec == expected_case,
             label + " browser case spec differs from its exact planned diagnostic case")
        for key, value in expected_case.items():
            need(receipt_case.get(key) == value,
                 label + " host receipt differs from saved case field " + key)
        validate_seed_query(case_spec.get("path"), seed, label, enabled)
        report = row["report"]
        need(report.get("seed") == seed and report.get("requestedSeed") == seed,
             label + " raw coordinator report seed differs from the frozen plan")
        check_profile_keys(report, enabled)
        host = helper.load_json(scratch / (label + "-summary.json"))
        row["hostMonotonicSeconds"] = helper.finite(host.get("hostMonotonicSeconds"),
                                                   label + " hostMonotonicSeconds", 0)
        row["wallMinusMonotonicSeconds"] = helper.finite(
            host.get("wallMinusMonotonicSeconds"), label + " wallMinusMonotonicSeconds")
        decoded_report = row["rawReportBytes"].decode("utf-8")
        row["reportLength"] = len(decoded_report)
        row["rawReportByteLength"] = len(row["rawReportBytes"])
        host_case = result["cases"][0]
        audit = result["inputAudit"]
        row["hostAudit"] = {
            "runnerExitCode": host.get("exitCode"),
            "runnerOutcome": host.get("runnerOutcome"),
            "hostOutcome": result.get("outcome"),
            "caseOutcome": host_case.get("outcome"),
            "observedState": host_case.get("observedState"),
            "terminalReached": host_case.get("terminalReached"),
            "timedOut": host_case.get("timedOut"),
            "navigationError": host_case.get("navigationError"),
            "reportObserved": host_case.get("reportObserved"),
            "reportJsonValid": host_case.get("reportJsonValid"),
            "reportMatch": host_case.get("reportMatch"),
            "errorArrays": {key: host_case.get(key) for key in helper.ERROR_ARRAYS},
            "cleanup": result.get("cleanup"),
            "inputAudit": {key: audit.get(key) for key in (
                "status", "beforeSha256", "afterSha256", "sourceSha256Before",
                "sourceSha256After", "webSha256Before", "webSha256After",
                "runnerBeforeSha256", "runnerAfterSha256", "fileCountBefore",
                "fileCountAfter")},
            "manifestIdentity": {
                "before": {key: before_manifest.get(key) for key in (
                    "sha256", "sourceSha256", "webSha256", "fileCount")},
                "after": {key: after_manifest.get(key) for key in (
                    "sha256", "sourceSha256", "webSha256", "fileCount")},
            },
            "runnerIdentity": {key: runner_before.get(key) for key in ("size", "sha256")},
        }
        projection, removed_profiles = helper.remove_profile_fields(report, enabled)
        row["readerReport"] = projection
        row["removedProfilePaths"] = removed_profiles
        row["samplerSummary"] = {}
        if enabled:
            deadline = helper.integer(report.get("measurementMaxJobMillis"),
                                      "measurementMaxJobMillis", 1)
            for phase in ("cold", "warm"):
                row["samplerSummary"][phase] = validate_preflight_metadata(
                    report[phase]["luRepetitionProfile"], phase, report, helper)

    for key in ("sourceSha256", "webSha256", "inputManifestSha256"):
        need(rows[0][key] == rows[1][key],
             "profile/off runs used different measured inputs: " + key)
    need(rows[0]["runnerSha256"] == rows[1]["runnerSha256"],
         "profile/off runs used different browser runner source")

    metadata_canaries = []
    for phase in ("cold", "warm"):
        profile = rows[0]["report"][phase]["luRepetitionProfile"]
        metadata_canaries.extend(run_metadata_negative_canaries(
            profile, phase, rows[0]["report"], helper))
    metadata_canaries.append(validate_off_profile_absence(
        rows[1]["report"], rows[0]["report"]["cold"]["luRepetitionProfile"], helper))

    reader_before = hash_reader_sources(repo)
    expected_new_outputs = [
        scratch / (label + "-strict-wrapper-" + args.tag + ".json")
        for label in (profile_label, off_label)
    ] + [
        scratch / (label + "-strict-reader-" + args.tag + ".txt")
        for label in (profile_label, off_label)
    ] + [
        scratch / (profile_label + "-vs-" + off_label +
                   "-current-preflight-validation-" + args.tag + ".json")
    ]
    need(all(not path.exists() for path in expected_new_outputs),
         "one or more validation output paths already exist; choose a fresh tag")
    reader_results = []
    for row in rows:
        result = helper.strict_reader(
            scratch, repo, row["label"], seed, row, row["removedProfilePaths"],
            row["reportSha256"], args.tag, self_test=True)
        output_text = (scratch / result["readerOutputFile"]).read_text(encoding="utf-8")
        canaries = {name: int(count) for name, count in
                    re.findall(r"([A-Za-z][A-Za-z0-9_-]*Canaries)=(\d+)", output_text)}
        need("corruptionCanaries" in canaries and canaries["corruptionCanaries"] > 0,
             row["label"] + " strict reader reported no self-negative canary count")
        result["selfTestCanaryCounts"] = canaries
        result["fullAppTimingReportPreserved"] = True
        reader_results.append(result)
    reader_after = hash_reader_sources(repo)
    need(reader_before == reader_after,
         "strict reader/parser source changed during pair validation")
    need(reader_results[0]["selfTestCanaryCounts"] ==
         reader_results[1]["selfTestCanaryCounts"],
         "current strict reader self-test counts differ between profile/off rows")

    raw_reports = [row["report"] for row in rows]
    need(raw_reports[0]["request"] == raw_reports[1]["request"],
         "profile/off requests differ")
    projections = []
    timing_paths = []
    for report, enabled in zip(raw_reports, (True, False)):
        without_profile, removed_profile_paths = helper.remove_profile_fields(report, enabled)
        timing_projection, removed_timing_paths = helper.remove_declared_timings(without_profile)
        projections.append(timing_projection)
        timing_paths.append({"profile": removed_profile_paths, "timings": removed_timing_paths})
    need(timing_paths[0]["profile"] == ["/cold/luRepetitionProfile", "/warm/luRepetitionProfile"] and
         timing_paths[1]["profile"] == [],
         "profile removal exceeded the two declared cold/warm metadata fields")
    need(timing_paths[0]["timings"] == timing_paths[1]["timings"],
         "profile/off reports expose different explicit timing paths")
    need(projections[0] == projections[1],
         "profile/off reports differ outside explicitly removed timing/profile fields")

    summary_path = scratch / (profile_label + "-vs-" + off_label +
                              "-current-preflight-validation-" + args.tag + ".json")
    need(not summary_path.exists(), "refusing to overwrite existing validation summary")
    run_summaries = []
    for row, reader in zip(rows, reader_results):
        summary = {key: value for key, value in row.items()
                   if key not in ("report", "rawReportBytes", "readerReport")}
        summary["strictReader"] = reader
        run_summaries.append(summary)
    report_a, report_b = raw_reports
    summary = {
        "schema": 1,
        "status": "PASS",
        "purpose": "single-seed row-preflight feasibility and instrumentation only; not production acceptance or a speed claim",
        "planFile": args.plan.resolve().name,
        "planSha256": plan_digest,
        "measurementPlan": {
            "candidate": plan["candidate"],
            "scope": plan["scope"],
            "timing": plan["timing"],
            "limits": plan["limits"],
        },
        "seed": seed,
        "runs": run_summaries,
        "requestExact": True,
        "reportExactOutsideDeclaredTimingAndProfileFields": True,
        "removedProfilePaths": timing_paths[0]["profile"],
        "removedTimingPaths": timing_paths[0]["timings"],
        "profileOffInputParity": True,
        "metadataNegativeCanaries": metadata_canaries,
        "strictReaderSourceHashes": reader_before,
        "timingComparison": {
            "appColdElapsedMsProfileMinusOff": report_a["cold"]["elapsedMs"] -
                                                report_b["cold"]["elapsedMs"],
            "appColdProofElapsedMsProfileMinusOff": report_a["cold"]["proofElapsedMs"] -
                                                   report_b["cold"]["proofElapsedMs"],
            "appColdRoutingElapsedMsProfileMinusOff": report_a["cold"]["routingElapsedMs"] -
                                                     report_b["cold"]["routingElapsedMs"],
            "hostMonotonicSecondsProfileMinusOff": rows[0]["hostMonotonicSeconds"] -
                                                   rows[1]["hostMonotonicSeconds"],
            "hostWallMinusMonotonicSecondsProfile": rows[0]["wallMinusMonotonicSeconds"],
            "hostWallMinusMonotonicSecondsOff": rows[1]["wallMinusMonotonicSeconds"],
        },
        "limits": {
            "singleSeedOnly": True,
            "instrumentationOnly": True,
            "notProductionAcceptance": True,
            "noPopulationSpeedEstimate": True,
            "zeroRowPreflightTimerScope": PREFLIGHT_TIMING_SCOPE,
        },
        "reproductionDependencies": dependency_binding,
    }
    for row in rows:
        path = scratch / row["reportPath"]
        raw = path.read_bytes()
        need(sha256(raw) == row["reportSha256"] and
             len(raw) == row["rawReportByteLength"] and
             len(raw.decode("utf-8")) == row["reportLength"],
             row["label"] + " raw standalone report changed during validation")
    helper.write_json_new(summary_path, summary)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scratch", type=Path, required=True)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--seed", required=True)
    parser.add_argument("--profile-label", required=True)
    parser.add_argument("--off-label", required=True)
    parser.add_argument("--tag", default="metadata-profile-off-01",
                        help="new output suffix; choose a fresh tag to retain prior failures")
    args = parser.parse_args()
    canonical_seed(args.seed)
    need(args.profile_label != args.off_label and
         re.fullmatch(r"[A-Za-z0-9._-]{1,96}", args.profile_label) is not None and
         args.profile_label not in (".", "..") and
         re.fullmatch(r"[A-Za-z0-9._-]{1,96}", args.off_label) is not None and
         args.off_label not in (".", ".."), "unsafe or duplicate run labels")
    need(re.fullmatch(r"[A-Za-z0-9._-]{1,48}", args.tag) is not None,
         "tag must contain only letters, digits, dot, underscore, or hyphen")
    args.scratch = args.scratch.resolve()
    args.repo = args.repo.resolve()
    args.plan = args.plan.resolve()
    need(args.scratch.is_dir() and args.repo.is_dir() and args.plan.is_file(),
         "scratch, repository, or predeclared plan is missing")
    summary_path = args.scratch / (args.profile_label + "-vs-" + args.off_label +
                                   "-current-preflight-validation-" + args.tag + ".json")
    try:
        plan, plan_digest = validate_plan(args.plan, args.seed,
                                          args.profile_label, args.off_label)
        helper, dependency_binding = load_frozen_helper(args.scratch)
        summary = validate_pair(args, helper, dependency_binding, plan, plan_digest)
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception as error:
        failure = {
            "schema": 1,
            "status": "FAIL",
            "purpose": "single-seed row-preflight feasibility and instrumentation only; not production acceptance or a speed claim",
            "seed": args.seed,
            "profileLabel": args.profile_label,
            "offLabel": args.off_label,
            "planSha256": sha256(args.plan.read_bytes()) if args.plan.exists() else None,
            "error": str(error),
        }
        if not summary_path.exists():
            try:
                args.scratch.mkdir(parents=True, exist_ok=True)
                summary_path.write_text(json.dumps(failure, indent=2) + "\n",
                                        encoding="utf-8", newline="\n")
            except Exception:
                pass
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
