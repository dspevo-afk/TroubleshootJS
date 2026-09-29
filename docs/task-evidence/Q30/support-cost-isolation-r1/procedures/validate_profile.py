#!/usr/bin/env python3
"""Independent sanity checks for Q30 coordinator support-cost raw reports.

This validates recorded counters and conservative accounting relationships. It does
not infer per-stamp time, project runtime, or turn the counters into a speedup claim.
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import sys
from pathlib import Path
from typing import Any

MAX_EXACT_INTEGER = (1 << 53) - 1
MODES = {"off", "track", "restricted"}
SAMPLE_STRIDE = 65536
DEFAULT_JOB_MS = 90000
MAX_UNIT_MS = 5000
MAX_JOB_STEPS = 640
MEASUREMENT_JOB_MS = 300000

COUNT_FIELDS = (
    "analyzeCalls", "factorCalls", "solveCalls", "trialAttempts",
    "acceptedSteps", "retrySteps", "runCalls", "circuitStamps",
    "matrixStampCalls",
    "supportCreates", "supportCreateScannedSlots", "supportInitialScannedSlots",
    "supportAllocatedSlots", "supportInitialCoordinates", "supportStampCalls",
    "supportExistingCoordinates", "supportNewCoordinates", "supportNewUnions",
    "supportInitialUnions", "supportDynamicUnions",
    "supportSamePartitionCoordinates", "supportRelabeledVertices",
    "supportInvalidations", "supportInvalidationCalls",
    "supportInvalidationsAnalyze", "supportInvalidationsStamp",
    "supportInvalidationsRun", "supportInvalidationsCreate",
    "supportInvalidationsStop", "supportInvalidationsOther",
    "domainAttempts", "domainSuccesses", "domainUnchangedPartition",
    "domainChangedPartition", "domainAllocatedSlots", "domainCopiedSlots",
    "luInputScannedSlots", "luPivotScannedSlots", "luUpdatedSlots",
    "lowerReferenceInsertions", "lowerReferenceClearedSlots",
    "lowerReferenceAllocatedSlots", "upperColumnAllocatedSlots",
    "matrixCopiedSlots", "rightSideCopiedSlots",
    "factorsAtUnchangedPartition", "factorsAtChangedPartition",
    "factorsWithoutStructuralSupport",
    "stampSampleCount", "outOfRangeTypeStamps",
)
TIMER_FIELDS = (
    "analyzeMillis", "factorMillis", "solveMillis", "runMillis",
    "createMillis", "domainMillis", "stampCircuitMillis",
    "maxAnalyzeMillis", "maxFactorMillis", "maxSolveMillis", "maxRunMillis",
    "maxCreateMillis", "maxDomainMillis", "maxStampCircuitMillis",
    "stampSampleMaxMicros", "stampSampleCumulativeMicros",
    "stampSampleCalibrationTotalMicros", "stampSampleCalibrationMeanMicros",
)
CALL_TIMER_PAIRS = (
    ("analyzeCalls", "analyzeMillis", "maxAnalyzeMillis"),
    ("factorCalls", "factorMillis", "maxFactorMillis"),
    ("solveCalls", "solveMillis", "maxSolveMillis"),
    ("runCalls", "runMillis", "maxRunMillis"),
    ("supportCreates", "createMillis", "maxCreateMillis"),
    ("circuitStamps", "stampCircuitMillis", "maxStampCircuitMillis"),
)
STRUCTURAL_ZERO_FIELDS = (
    "supportCreates", "supportCreateScannedSlots", "supportInitialScannedSlots",
    "supportAllocatedSlots", "supportInitialCoordinates", "supportStampCalls",
    "supportExistingCoordinates", "supportNewCoordinates", "supportNewUnions",
    "supportInitialUnions", "supportDynamicUnions",
    "supportSamePartitionCoordinates", "supportRelabeledVertices",
    "supportInvalidations", "supportInvalidationCalls",
    "supportInvalidationsAnalyze", "supportInvalidationsStamp",
    "supportInvalidationsRun", "supportInvalidationsCreate",
    "supportInvalidationsStop", "supportInvalidationsOther",
    "domainAttempts", "domainSuccesses",
    "domainUnchangedPartition", "domainChangedPartition",
    "domainAllocatedSlots", "domainCopiedSlots",
    "factorsAtUnchangedPartition", "factorsAtChangedPartition",
)
TYPE_LIMIT = 2048


class ValidationError(Exception):
    pass


def need(condition: bool, where: str, message: str) -> None:
    if not condition:
        raise ValidationError(f"{where}: {message}")


def as_object(value: Any, where: str) -> dict[str, Any]:
    need(isinstance(value, dict), where, "expected JSON object")
    return value


def as_array(value: Any, where: str) -> list[Any]:
    need(isinstance(value, list), where, "expected JSON array")
    return value


def number(value: Any, where: str) -> float:
    need(isinstance(value, (int, float)) and not isinstance(value, bool),
         where, "expected JSON number")
    try:
        result = float(value)
    except OverflowError:
        raise ValidationError(f"{where}: number is outside finite range") from None
    need(math.isfinite(result), where, "number must be finite")
    return result


def nonnegative(value: Any, where: str) -> float:
    result = number(value, where)
    need(result >= 0, where, "number must be nonnegative")
    return result


def counter(value: Any, where: str) -> int:
    result = number(value, where)
    need(result.is_integer(), where, "count must be an exact integer")
    need(result <= MAX_EXACT_INTEGER, where,
         "count exceeds the exactly representable integer range")
    return int(result)


def require_count(profile: dict[str, Any], key: str, where: str) -> int:
    need(key in profile, where, f"missing counter {key}")
    return counter(profile[key], f"{where}.{key}")


def close_enough(actual: float, expected: float) -> bool:
    return math.isclose(actual, expected, rel_tol=1e-12, abs_tol=1e-12)


def validate_dump_types(profile: dict[str, Any], counts: dict[str, int],
                        where: str) -> None:
    rows = as_array(profile.get("stampsByDumpType"), f"{where}.stampsByDumpType")
    seen: set[int] = set()
    typed_stamps = 0
    typed_new_coordinates = 0
    for index, item in enumerate(rows):
        row_where = f"{where}.stampsByDumpType[{index}]"
        row = as_object(item, row_where)
        need("dumpType" in row and "stamps" in row and "newCoordinates" in row,
             row_where, "row must contain dumpType, stamps, and newCoordinates")
        dump_type = counter(row["dumpType"], f"{row_where}.dumpType")
        stamps = counter(row["stamps"], f"{row_where}.stamps")
        new_coordinates = counter(row["newCoordinates"],
                                  f"{row_where}.newCoordinates")
        need(dump_type < TYPE_LIMIT, f"{row_where}.dumpType",
             f"dumpType must be below {TYPE_LIMIT}")
        need(dump_type not in seen, f"{row_where}.dumpType",
             "duplicate dumpType row")
        need(stamps > 0, f"{row_where}.stamps",
             "zero-stamp rows must be omitted")
        need(new_coordinates <= stamps, row_where,
             "newCoordinates cannot exceed stamps")
        seen.add(dump_type)
        typed_stamps += stamps
        typed_new_coordinates += new_coordinates

    total = typed_stamps + counts["outOfRangeTypeStamps"]
    need(total == counts["matrixStampCalls"], where,
         "typed plus out-of-range stamps must equal matrixStampCalls")
    need(typed_new_coordinates <= typed_stamps, where,
         "typed new-coordinate total exceeds typed stamps")


def validate_profile(profile_value: Any, mode: str, where: str) -> dict[str, Any]:
    profile = as_object(profile_value, where)
    need(profile.get("mode") == mode, f"{where}.mode",
         "profile mode differs from top-level supportCostMode")
    need(profile.get("clock") == "performance.now", f"{where}.clock",
         "unexpected profiling clock")
    need(profile.get("measurementValid") is True, f"{where}.measurementValid",
         "measurement was marked invalid")
    need(profile.get("frozenBeforeCleanup") is True,
         f"{where}.frozenBeforeCleanup",
         "profile must be frozen before cleanup")
    need("clockAnomalies" in profile and "counterErrors" in profile,
         where, "missing clockAnomalies or counterErrors")
    counts = {key: require_count(profile, key, where) for key in COUNT_FIELDS}
    clock_anomalies = counter(profile["clockAnomalies"],
                              f"{where}.clockAnomalies")
    counter_errors = counter(profile["counterErrors"],
                             f"{where}.counterErrors")
    need(clock_anomalies == 0, where, "clockAnomalies must be zero")
    need(counter_errors == 0, where, "counterErrors must be zero")

    timings = {}
    for key in TIMER_FIELDS:
        need(key in profile, where, f"missing timing {key}")
        timings[key] = nonnegative(profile[key], f"{where}.{key}")

    need("stampSampleStride" in profile, where, "missing stampSampleStride")
    stride = counter(profile["stampSampleStride"], f"{where}.stampSampleStride")
    need(stride == SAMPLE_STRIDE, f"{where}.stampSampleStride",
         f"expected sampling stride {SAMPLE_STRIDE}")

    bins = as_array(profile.get("stampSampleBinsLt1Lt4Lt16Lt64Lt256AndAbove"),
                    f"{where}.stampSampleBinsLt1Lt4Lt16Lt64Lt256AndAbove")
    need(len(bins) == 6, where, "stamp histogram must contain exactly six bins")
    bin_counts = [
        counter(value, f"{where}.stampSampleBins[{index}]")
        for index, value in enumerate(bins)
    ]
    sample_count = counts["stampSampleCount"]
    need(sum(bin_counts) == sample_count, where,
         "stamp histogram total must equal stampSampleCount")
    expected_samples = counts["matrixStampCalls"] // SAMPLE_STRIDE
    need(sample_count == expected_samples, where,
         "sample count must match completed matrix-stamp stride boundaries")

    sample_max = timings["stampSampleMaxMicros"]
    sample_total = timings["stampSampleCumulativeMicros"]
    calibration_total = timings["stampSampleCalibrationTotalMicros"]
    calibration_mean = timings["stampSampleCalibrationMeanMicros"]
    if sample_count == 0:
        need(sample_max == 0 and sample_total == 0 and calibration_total == 0,
             where, "empty sample set must have zero sample metrics")
        need(calibration_mean == 0, where,
             "empty sample set must have zero calibration mean")
        need(all(item == 0 for item in bin_counts), where,
             "empty sample set must have empty histogram")
    else:
        need(sample_total >= sample_max, where,
             "sample cumulative time cannot be below sample maximum")
        need(sample_total <= sample_count * sample_max or
             close_enough(sample_total, sample_count * sample_max),
             where, "sample cumulative time exceeds count times maximum")
        need(close_enough(calibration_mean, calibration_total / sample_count),
             where, "calibration mean does not match total divided by sample count")
        highest_bin = max(index for index, value in enumerate(bin_counts) if value)
        lower_edges = (0.0, 1.0, 4.0, 16.0, 64.0, 256.0)
        upper_edges = (1.0, 4.0, 16.0, 64.0, 256.0, math.inf)
        need(sample_max >= lower_edges[highest_bin], where,
             "sample maximum is below the highest occupied histogram bin")
        need(sample_max < upper_edges[highest_bin], where,
             "sample maximum is above the highest occupied histogram bin")

    validate_dump_types(profile, counts, where)

    need(timings["maxDomainMillis"] <= timings["domainMillis"] or
         close_enough(timings["maxDomainMillis"], timings["domainMillis"]),
         where, "maxDomainMillis cannot exceed domainMillis")

    for call_key, total_key, max_key in CALL_TIMER_PAIRS:
        calls = counts[call_key]
        total = timings[total_key]
        maximum = timings[max_key]
        need(maximum <= total or close_enough(maximum, total), where,
             f"{max_key} cannot exceed {total_key}")
        if calls == 0:
            need(total == 0 and maximum == 0, where,
                 f"{total_key} and {max_key} must be zero when {call_key} is zero")

    need(counts["acceptedSteps"] <= counts["trialAttempts"], where,
         "accepted steps cannot exceed trial attempts")
    need(counts["acceptedSteps"] + counts["retrySteps"] <=
         counts["trialAttempts"], where,
         "accepted steps and retries cannot exceed trial attempts")
    need(counts["retrySteps"] <= counts["trialAttempts"], where,
         "retry steps cannot exceed trial attempts")
    need(counts["factorCalls"] <= counts["trialAttempts"], where,
         "factor calls cannot exceed trial attempts")
    need(counts["solveCalls"] <= counts["trialAttempts"], where,
         "solve calls cannot exceed trial attempts")
    need(counts["supportCreates"] <= counts["circuitStamps"], where,
         "support create attempts cannot exceed circuit stamps")
    need(counts["domainSuccesses"] <= counts["domainAttempts"], where,
         "domain successes cannot exceed domain attempts")
    need(counts["domainAttempts"] <= counts["factorCalls"], where,
         "domain attempts cannot exceed factor calls")
    need(counts["domainUnchangedPartition"] + counts["domainChangedPartition"] ==
         counts["domainSuccesses"], where,
         "domain partition outcomes must reconcile to domain successes")

    need(counts["supportInitialCoordinates"] <=
         counts["supportInitialScannedSlots"], where,
         "initial coordinates cannot exceed scanned slots")
    need(counts["supportInitialUnions"] <=
         counts["supportInitialCoordinates"], where,
         "initial unions cannot exceed initial coordinates")
    need(counts["supportInitialUnions"] +
         counts["supportDynamicUnions"] == counts["supportNewUnions"], where,
         "initial plus dynamic unions must equal new unions")
    need(counts["supportExistingCoordinates"] +
         counts["supportNewCoordinates"] <= counts["supportStampCalls"], where,
         "classified support coordinates cannot exceed support stamp calls")
    need(counts["supportDynamicUnions"] <= counts["supportNewCoordinates"], where,
         "dynamic unions cannot exceed new support coordinates")
    need(counts["supportSamePartitionCoordinates"] <=
         counts["supportNewCoordinates"], where,
         "unchanged-partition coordinates cannot exceed new support coordinates")
    need(counts["supportRelabeledVertices"] >= counts["supportNewUnions"], where,
         "each new union must relabel at least one vertex")
    need(counts["supportInvalidations"] <= counts["supportCreates"], where,
         "actual profile-owned invalidations cannot exceed support create attempts")
    need(counts["supportInvalidations"] <=
         counts["supportInvalidationCalls"], where,
         "actual invalidations cannot exceed live-support invalidation calls")
    reason_calls = sum(counts[key] for key in (
        "supportInvalidationsAnalyze", "supportInvalidationsStamp",
        "supportInvalidationsRun", "supportInvalidationsCreate",
        "supportInvalidationsStop", "supportInvalidationsOther"))
    need(reason_calls == counts["supportInvalidationCalls"], where,
         "invalidation reasons must reconcile to supportInvalidationCalls")

    max_scanned_per_create = 512 * 512
    need(counts["supportCreateScannedSlots"] <=
         counts["supportCreates"] * max_scanned_per_create, where,
         "create scan count exceeds the largest supported matrix per attempt")
    need(counts["supportInitialScannedSlots"] <=
         counts["supportCreates"] * max_scanned_per_create, where,
         "initial scan count exceeds the largest supported matrix per attempt")

    need(counts["factorsAtUnchangedPartition"] +
         counts["factorsAtChangedPartition"] +
         counts["factorsWithoutStructuralSupport"] ==
         counts["factorCalls"], where,
         "factor support-state categories must reconcile to factor calls")

    if mode == "off":
        for key in STRUCTURAL_ZERO_FIELDS:
            need(counts[key] == 0, f"{where}.{key}",
                 "mode off must not record structural tracker work")
        need(counts["factorsWithoutStructuralSupport"] ==
             counts["factorCalls"], where,
             "mode off factors must be classified without structural support")
        need(counts["matrixStampCalls"] > 0, where,
             "mode off profile must still record matrix stamps")
        need(counts["acceptedSteps"] > 0, where,
             "mode off profile must retain accepted solver steps")
        need(counts["factorCalls"] > 0 and counts["luInputScannedSlots"] > 0,
             where, "mode off profile must retain numeric LU work")
        need(counts["matrixCopiedSlots"] > 0 and counts["rightSideCopiedSlots"] > 0,
             where, "mode off profile must retain numeric scratch-copy work")
    elif mode in {"track", "restricted"}:
        need(counts["supportCreates"] > 0, where,
             "tracked mode must record structural support creation attempts")
        need(counts["supportInitialScannedSlots"] > 0, where,
             "tracked mode must record at least one initialized support")

    return {
        "mode": mode,
        "matrixStampCalls": counts["matrixStampCalls"],
        "acceptedSteps": counts["acceptedSteps"],
        "factorCalls": counts["factorCalls"],
        "factorsWithoutStructuralSupport":
            counts["factorsWithoutStructuralSupport"],
        "supportCreates": counts["supportCreates"],
        "supportNewCoordinates": counts["supportNewCoordinates"],
        "supportNewUnions": counts["supportNewUnions"],
        "domainAttempts": counts["domainAttempts"],
        "domainSuccesses": counts["domainSuccesses"],
        "sampleCount": sample_count,
        "sampleMaxMicros": sample_max,
        "profileValid": True,
    }


def validate_report(report_value: Any, where: str) -> dict[str, Any]:
    report = as_object(report_value, where)
    requested = report.get("supportCostRequested")
    need(isinstance(requested, bool), f"{where}.supportCostRequested",
         "must be a JSON boolean")
    mode = report.get("supportCostMode")
    need(isinstance(mode, str) and mode in MODES,
         f"{where}.supportCostMode",
         "must be off, track, or restricted")

    need(report.get("status") == "PASS", f"{where}.status",
         "Q30 raw report must have status PASS")
    need(report.get("phase") == "complete", f"{where}.phase",
         "Q30 raw report must be complete")
    need(report.get("coordinatorQualificationPassed") is True,
         f"{where}.coordinatorQualificationPassed",
         "coordinator qualification did not pass")
    need(report.get("measurementOnly") is True and
         report.get("normalAdmission") is False and
         report.get("playerPublished") is False and
         report.get("normalPlayerLaunch") is False,
         where, "report must remain measurement-only and unpublished")
    need(report.get("cleanupComplete") is True and
         report.get("cleanupPending") is False and
         report.get("restored") is True,
         where, "cleanup and predecessor restoration must be complete")

    for key, expected in (
        ("defaultMaxJobMillis", DEFAULT_JOB_MS),
        ("maxUnitMillis", MAX_UNIT_MS),
        ("maxJobSteps", MAX_JOB_STEPS),
        ("maxJobMillis", MEASUREMENT_JOB_MS),
        ("measurementMaxJobMillis", MEASUREMENT_JOB_MS),
    ):
        need(key in report, where, f"missing budget field {key}")
        actual = counter(report[key], f"{where}.{key}")
        need(actual == expected, f"{where}.{key}",
             f"expected frozen budget {expected}")

    phases: dict[str, Any] = {}
    for phase in ("cold", "warm"):
        run_where = f"{where}.{phase}"
        run = as_object(report.get(phase), run_where)
        need(run.get("run") == phase, f"{run_where}.run",
             "run label does not match phase")
        need(run.get("outcome") == "PASS", f"{run_where}.outcome",
             "cold and warm runs must pass")
        support_cost = run.get("supportCost")
        if requested:
            need(support_cost is not None, f"{run_where}.supportCost",
                 "requested profile is missing")
            phases[phase] = validate_profile(support_cost, mode,
                                             f"{run_where}.supportCost")
        else:
            need(support_cost is None, f"{run_where}.supportCost",
                 "unrequested profile must be absent or null")
            phases[phase] = {"profileValid": False,
                             "profileStatus": "NOT_REQUESTED"}

    return {
        "status": "PASS",
        "profileRequested": requested,
        "mode": mode,
        "cold": phases["cold"],
        "warm": phases["warm"],
    }


def reject_constant(value: str) -> None:
    raise ValidationError(f"invalid JSON non-finite constant {value}")


def load_report(path: Path) -> Any:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as error:
        raise ValidationError(f"{path}: cannot read report: {error}") from error
    try:
        return json.loads(raw, parse_constant=reject_constant)
    except (json.JSONDecodeError, ValidationError) as error:
        raise ValidationError(f"{path}: invalid JSON: {error}") from error


def resolve_input(argument: str, script_dir: Path) -> Path:
    candidate = Path(argument)
    if candidate.exists():
        return candidate.resolve()
    if not candidate.suffix:
        labelled = script_dir / f"{argument}-raw-report.json"
        if labelled.exists():
            return labelled.resolve()
    local = script_dir / candidate
    if local.exists():
        return local.resolve()
    return candidate.resolve()


def make_canary_profile() -> dict[str, Any]:
    profile: dict[str, Any] = {key: 0 for key in COUNT_FIELDS}
    profile.update({
        "mode": "track",
        "clock": "performance.now",
        "measurementValid": True,
        "frozenBeforeCleanup": True,
        "clockAnomalies": 0,
        "counterErrors": 0,
        "analyzeCalls": 1,
        "factorCalls": 1,
        "solveCalls": 1,
        "trialAttempts": 2,
        "acceptedSteps": 1,
        "runCalls": 1,
        "circuitStamps": 1,
        "matrixStampCalls": SAMPLE_STRIDE,
        "supportCreates": 1,
        "supportCreateScannedSlots": 4,
        "supportInitialScannedSlots": 4,
        "supportAllocatedSlots": 34,
        "supportInitialCoordinates": 2,
        "supportStampCalls": 3,
        "supportExistingCoordinates": 1,
        "supportNewCoordinates": 2,
        "supportNewUnions": 2,
        "supportInitialUnions": 1,
        "supportDynamicUnions": 1,
        "supportSamePartitionCoordinates": 1,
        "supportRelabeledVertices": 2,
        "domainAttempts": 1,
        "domainSuccesses": 1,
        "domainChangedPartition": 1,
        "domainCopiedSlots": 4,
        "luInputScannedSlots": 2,
        "luPivotScannedSlots": 2,
        "luUpdatedSlots": 1,
        "lowerReferenceInsertions": 1,
        "matrixCopiedSlots": 2,
        "rightSideCopiedSlots": 2,
        "factorsAtChangedPartition": 1,
        "stampSampleCount": 1,
        "analyzeMillis": 1.0,
        "factorMillis": 2.0,
        "solveMillis": 1.0,
        "runMillis": 4.0,
        "createMillis": 0.5,
        "domainMillis": 0.25,
        "stampCircuitMillis": 1.5,
        "maxAnalyzeMillis": 1.0,
        "maxFactorMillis": 2.0,
        "maxSolveMillis": 1.0,
        "maxRunMillis": 4.0,
        "maxCreateMillis": 0.5,
        "maxDomainMillis": 0.25,
        "maxStampCircuitMillis": 1.5,
        "stampSampleStride": SAMPLE_STRIDE,
        "stampSampleMaxMicros": 0.5,
        "stampSampleCumulativeMicros": 0.5,
        "stampSampleCalibrationTotalMicros": 0.25,
        "stampSampleCalibrationMeanMicros": 0.25,
        "stampSampleBinsLt1Lt4Lt16Lt64Lt256AndAbove": [1, 0, 0, 0, 0, 0],
        "stampsByDumpType": [
            {"dumpType": 100, "stamps": SAMPLE_STRIDE, "newCoordinates": 0}
        ],
        "outOfRangeTypeStamps": 0,
    })
    return profile


def self_test() -> dict[str, Any]:
    base = make_canary_profile()
    validate_profile(copy.deepcopy(base), "track", "self-test.base")
    mutations = (
        ("negative count", lambda p: p.update(matrixStampCalls=-1)),
        ("fractional count", lambda p: p.update(analyzeCalls=0.5)),
        ("unsafe count", lambda p: p.update(factorCalls=1 << 53)),
        ("nonfinite timer", lambda p: p.update(runMillis=float("nan"))),
        ("negative timer", lambda p: p.update(solveMillis=-1)),
        ("maximum above total", lambda p: p.update(maxFactorMillis=3)),
        ("factor calls exceed trials", lambda p: p.update(factorCalls=3)),
        ("domain success exceeds attempts", lambda p: p.update(domainSuccesses=2)),
        ("classified coordinate overflow", lambda p: p.update(supportNewCoordinates=3)),
        ("union conservation mismatch", lambda p: p.update(supportNewUnions=3)),
        ("invalidation reason mismatch",
         lambda p: p.update(supportInvalidationCalls=1)),
        ("histogram sum mismatch",
         lambda p: p.update(stampSampleBinsLt1Lt4Lt16Lt64Lt256AndAbove=[0, 0, 0, 0, 0, 0])),
        ("dump-type stamp mismatch",
         lambda p: p.update(stampsByDumpType=[
             {"dumpType": 100, "stamps": SAMPLE_STRIDE - 1, "newCoordinates": 0}
         ])),
    )
    rejected = []
    for label, mutate in mutations:
        candidate = copy.deepcopy(base)
        mutate(candidate)
        try:
            validate_profile(candidate, "track", f"self-test.{label}")
        except ValidationError:
            rejected.append(label)
        else:
            raise ValidationError(f"self-test mutation was accepted: {label}")
    return {"status": "PASS", "negativeMutationsRejected": len(rejected),
            "mutations": rejected}


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate Q30 cold/warm support-cost counters in raw JSON reports.")
    parser.add_argument("reports", nargs="*",
                        help="raw report paths or labels with a matching -raw-report.json")
    parser.add_argument("--self-test", action="store_true",
                        help="run in-memory invalid-record mutations")
    args = parser.parse_args()
    try:
        if args.self_test:
            print(json.dumps(self_test(), sort_keys=True))
        if not args.reports:
            if args.self_test:
                return 0
            parser.error("provide at least one raw report path or label")
        results = []
        script_dir = Path(__file__).resolve().parent
        for argument in args.reports:
            path = resolve_input(argument, script_dir)
            result = validate_report(load_report(path), str(path))
            results.append({"file": path.name, **result})
        print(json.dumps({"status": "PASS", "reports": results,
                          "interpretation":
                          "Recorded counters only; no per-stamp extrapolation."},
                         sort_keys=True))
        return 0
    except ValidationError as error:
        print(json.dumps({"status": "FAIL", "error": str(error)}, sort_keys=True),
              file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
