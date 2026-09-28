#!/usr/bin/env python3
"""Strict validator for the isolated Q30 solver publication profile.

The Java producer owns the JSON shape.  This module validates one detached
``publicationProfile`` snapshot and supplies negative canaries built from an
actual snapshot.  It deliberately contains no host, browser, comparison, or
application-proof logic.
"""

import argparse
import copy
import hashlib
import json
import math
import sys
from pathlib import Path


SAMPLE_INTERVAL = 512
MAX_RAW_DURATIONS = 64
PHASE_NAMES = (
    "applySolvedRightSide",
    "rollbackVoltagePublication",
    "wireRefreshImmediateAcceptedStep",
    "wireRefreshDelayedOwnedAcceptedStep",
    "wireRefreshDeferredUiBatch",
    "acceptedStepBookkeeping",
)
ROOT_KEYS = (
    "sampleInterval", "valid", "frozen", "clock", "timerErrors",
    "lateErrors", "unknownPathErrors", "openSamples", "eligible",
    "selected", "completed", "failed", "sampledElapsedMs", "phases",
)
PHASE_KEYS = (
    "name", "eligible", "selected", "completed", "failed", "elapsedMs",
    "zeroDurationCount", "timerErrors", "rawDurationCount",
    "rawDurationsDropped", "counterOverflow", "rawDurationsMs",
)
RELATIVE_TOLERANCE = 1e-9
ABSOLUTE_TOLERANCE = 1e-9


class ValidationError(ValueError):
    """Raised when a profile or duplicate report snapshot is invalid."""


def need(condition, message):
    if not condition:
        raise ValidationError(message)


def _exact_keys(value, expected, label):
    need(isinstance(value, dict), label + " must be an object")
    expected_set = set(expected)
    missing = [key for key in expected if key not in value]
    extra = [key for key in value if key not in expected_set]
    need(not missing, label + " is missing keys: " + ", ".join(missing))
    need(not extra, label + " has unexpected keys: " + ", ".join(map(str, extra)))


def _exact_int(value, label, minimum=0):
    need(type(value) is int,
         label + " must be an integer; booleans are not integers")
    need(value >= minimum, label + " is below its minimum")
    return value


def _finite_nonnegative(value, label):
    need(type(value) in (int, float),
         label + " must be a finite number; booleans are not numbers")
    need(math.isfinite(value), label + " must be finite")
    need(value >= 0, label + " is below zero")
    return float(value)


def _close(left, right):
    scale = max(1.0, abs(left), abs(right))
    return abs(left - right) <= max(
        ABSOLUTE_TOLERANCE, RELATIVE_TOLERANCE * scale)


def _phase_rows(profile, label):
    phases = profile["phases"]
    need(isinstance(phases, list), label + ".phases must be an array")
    need(len(phases) == len(PHASE_NAMES),
         label + ".phases must contain exactly six rows")
    rows = []
    seen = set()
    for index, row in enumerate(phases):
        phase_label = "{}.phases[{}]".format(label, index)
        _exact_keys(row, PHASE_KEYS, phase_label)
        name = row["name"]
        need(isinstance(name, str), phase_label + ".name must be a string")
        need(name == PHASE_NAMES[index],
             phase_label + ".name is not the frozen phase at this index")
        need(name not in seen, phase_label + ".name is duplicated")
        seen.add(name)
        rows.append(row)
    return rows


def _phase_summary(row):
    return {
        "name": row["name"],
        "eligible": row["eligible"],
        "selected": row["selected"],
        "completed": row["completed"],
        "failed": row["failed"],
        "elapsedMs": row["elapsedMs"],
        "zeroDurationCount": row["zeroDurationCount"],
        "timerErrors": row["timerErrors"],
        "rawDurationCount": row["rawDurationCount"],
        "rawDurationsDropped": row["rawDurationsDropped"],
        "counterOverflow": row["counterOverflow"],
    }


def validate(profile, label="publicationProfile"):
    """Validate one terminal frozen snapshot and return compact counts.

    The returned ``sampledElapsedMs`` is the producer's observed sum.  This
    function never extrapolates selected samples into a whole-operation cost.
    """
    _exact_keys(profile, ROOT_KEYS, label)
    sample_interval = _exact_int(profile["sampleInterval"],
                                 label + ".sampleInterval")
    need(sample_interval == SAMPLE_INTERVAL,
         label + ".sampleInterval must be 512")
    need(type(profile["valid"]) is bool, label + ".valid must be boolean")
    need(type(profile["frozen"]) is bool, label + ".frozen must be boolean")
    need(profile["valid"] is True, label + ".valid must be true")
    need(profile["frozen"] is True, label + ".frozen must be true")

    clock = profile["clock"]
    need(clock in ("performance.now", "not-observed"),
         label + ".clock is not an allowed clock state")
    for key in ("timerErrors", "lateErrors", "unknownPathErrors", "openSamples"):
        value = _exact_int(profile[key], label + "." + key)
        need(value == 0, label + "." + key + " must be zero")

    rows = _phase_rows(profile, label)
    total_eligible = 0
    total_selected = 0
    total_completed = 0
    total_failed = 0
    total_elapsed = 0.0
    summaries = []

    for index, row in enumerate(rows):
        phase_label = label + ".phases[{}]".format(index)
        eligible = _exact_int(row["eligible"], phase_label + ".eligible")
        selected = _exact_int(row["selected"], phase_label + ".selected")
        completed = _exact_int(row["completed"], phase_label + ".completed")
        failed = _exact_int(row["failed"], phase_label + ".failed")
        zero_count = _exact_int(
            row["zeroDurationCount"], phase_label + ".zeroDurationCount")
        timer_errors = _exact_int(row["timerErrors"], phase_label + ".timerErrors")
        raw_count = _exact_int(
            row["rawDurationCount"], phase_label + ".rawDurationCount")
        dropped = _exact_int(
            row["rawDurationsDropped"], phase_label + ".rawDurationsDropped")
        elapsed = _finite_nonnegative(row["elapsedMs"], phase_label + ".elapsedMs")
        need(selected == eligible // SAMPLE_INTERVAL,
             phase_label + ".selected is not floor(eligible/512)")
        need(completed + failed == selected,
             phase_label + " completed+failed must equal selected")
        need(timer_errors == 0, phase_label + ".timerErrors must be zero")
        need(type(row["counterOverflow"]) is bool,
             phase_label + ".counterOverflow must be boolean")
        need(row["counterOverflow"] is False,
             phase_label + ".counterOverflow must be false")
        need(raw_count == min(selected, MAX_RAW_DURATIONS),
             phase_label + ".rawDurationCount is not min(selected,64)")
        need(dropped == selected - raw_count,
             phase_label + ".rawDurationsDropped disagrees with selected")

        raw_values = row["rawDurationsMs"]
        need(isinstance(raw_values, list),
             phase_label + ".rawDurationsMs must be an array")
        need(len(raw_values) == raw_count,
             phase_label + ".rawDurationsMs length disagrees with rawDurationCount")
        normalized_raw = []
        for raw_index, raw_value in enumerate(raw_values):
            normalized_raw.append(_finite_nonnegative(
                raw_value, "{} .rawDurationsMs[{}]".format(
                    phase_label, raw_index).replace(" .", ".")))
        raw_sum = math.fsum(normalized_raw)
        need(raw_sum <= elapsed + max(
            ABSOLUTE_TOLERANCE,
            RELATIVE_TOLERANCE * max(1.0, abs(raw_sum), abs(elapsed))),
             phase_label + " raw durations exceed elapsedMs")
        visible_zero_count = sum(value == 0 for value in normalized_raw)
        need(zero_count <= selected,
             phase_label + ".zeroDurationCount exceeds selected")
        if dropped == 0:
            need(zero_count == visible_zero_count,
                 phase_label + ".zeroDurationCount disagrees with visible samples")
            need(_close(raw_sum, elapsed),
                 phase_label + " undropped raw durations do not equal elapsedMs")
        else:
            need(visible_zero_count <= zero_count <= visible_zero_count + dropped,
                 phase_label + ".zeroDurationCount is outside dropped-sample bounds")

        total_eligible += eligible
        total_selected += selected
        total_completed += completed
        total_failed += failed
        total_elapsed += elapsed
        summaries.append(_phase_summary(row))

    eligible = _exact_int(profile["eligible"], label + ".eligible")
    selected = _exact_int(profile["selected"], label + ".selected")
    completed = _exact_int(profile["completed"], label + ".completed")
    failed = _exact_int(profile["failed"], label + ".failed")
    sampled_elapsed = _finite_nonnegative(
        profile["sampledElapsedMs"], label + ".sampledElapsedMs")
    need(eligible == total_eligible, label + ".eligible disagrees with phase sums")
    need(selected == total_selected, label + ".selected disagrees with phase sums")
    need(completed == total_completed,
         label + ".completed disagrees with phase sums")
    need(failed == total_failed, label + ".failed disagrees with phase sums")
    need(_close(sampled_elapsed, total_elapsed),
         label + ".sampledElapsedMs disagrees with phase elapsed sum")
    need((selected > 0 and clock == "performance.now") or
         (selected == 0 and clock == "not-observed"),
         label + ".clock does not match selected-sample observation")

    return {
        "status": "PASS",
        "label": label,
        "sampleInterval": sample_interval,
        "maxRawDurations": MAX_RAW_DURATIONS,
        "valid": profile["valid"],
        "frozen": profile["frozen"],
        "clock": clock,
        "eligible": eligible,
        "selected": selected,
        "completed": completed,
        "failed": failed,
        "sampledElapsedMs": profile["sampledElapsedMs"],
        "sampledElapsedIsObservedSum": True,
        "phases": summaries,
    }


def _first_phase(profile):
    need(isinstance(profile.get("phases"), list) and profile["phases"],
         "cannot construct canaries without a phase")
    return profile["phases"][0]


def _mutations(profile):
    first = _first_phase(profile)
    mutations = []

    def add(name, mutate):
        mutations.append((name, mutate))

    add("missing root key", lambda p: p.pop("valid"))
    add("extra root key", lambda p: p.__setitem__("unexpected", 1))
    add("wrong root boolean type", lambda p: p.__setitem__("valid", 1))
    add("missing phase", lambda p: p["phases"].pop())
    add("duplicate phase", lambda p: p["phases"][1].__setitem__(
        "name", p["phases"][0]["name"]))
    add("unknown phase", lambda p: p["phases"][0].__setitem__(
        "name", "unknownPhase"))
    add("missing phase key", lambda p: p["phases"][0].pop("elapsedMs"))
    add("extra phase key", lambda p: p["phases"][0].__setitem__(
        "unexpected", 0))
    add("wrong phase integer type", lambda p: p["phases"][0].__setitem__(
        "eligible", True))
    add("NaN elapsed", lambda p: p["phases"][0].__setitem__(
        "elapsedMs", float("nan")))
    add("infinite elapsed", lambda p: p["phases"][0].__setitem__(
        "elapsedMs", float("inf")))
    add("NaN raw duration", lambda p: p["phases"][0][
        "rawDurationsMs"].__setitem__(0, float("nan"))
        if p["phases"][0]["rawDurationsMs"] else
        p["phases"][0].__setitem__("rawDurationsMs", [float("nan")]))
    add("infinite raw duration", lambda p: p["phases"][0][
        "rawDurationsMs"].__setitem__(0, float("inf"))
        if p["phases"][0]["rawDurationsMs"] else
        p["phases"][0].__setitem__("rawDurationsMs", [float("inf")]))
    add("backwards elapsed", lambda p: p["phases"][0].__setitem__(
        "elapsedMs", -1.0))
    add("selected count mismatch", lambda p: p.__setitem__(
        "selected", p["selected"] + 1))
    add("phase completed count mismatch", lambda p: p["phases"][0].__setitem__(
        "completed", p["phases"][0]["completed"] + 1))
    add("invalid valid flag", lambda p: p.__setitem__("valid", False))
    add("unfrozen flag", lambda p: p.__setitem__("frozen", False))
    add("timer error", lambda p: p.__setitem__("timerErrors", 1))
    add("late error", lambda p: p.__setitem__("lateErrors", 1))
    add("unknown phase error", lambda p: p.__setitem__("unknownPathErrors", 1))
    add("open sample", lambda p: p.__setitem__("openSamples", 1))
    add("phase timer error", lambda p: p["phases"][0].__setitem__(
        "timerErrors", 1))
    add("counter overflow", lambda p: p["phases"][0].__setitem__(
        "counterOverflow", True))
    add("invalid drop count", lambda p: p["phases"][0].__setitem__(
        "rawDurationsDropped", p["phases"][0]["rawDurationsDropped"] + 1))
    add("invalid raw count", lambda p: p["phases"][0].__setitem__(
        "rawDurationCount", p["phases"][0]["rawDurationCount"] + 1))
    add("raw array length mismatch", lambda p: p["phases"][0][
        "rawDurationsMs"].append(0.0))
    add("negative raw duration", lambda p: p["phases"][0][
        "rawDurationsMs"].__setitem__(0, -1.0)
        if p["phases"][0]["rawDurationsMs"] else
        p["phases"][0].__setitem__("rawDurationsMs", [-1.0]))
    add("zero duration count mismatch", lambda p: p["phases"][0].__setitem__(
        "zeroDurationCount", p["phases"][0]["selected"] + 1))
    add("wrong clock", lambda p: p.__setitem__("clock", "Date.now"))
    add("sampled elapsed mismatch", lambda p: p.__setitem__(
        "sampledElapsedMs", p["sampledElapsedMs"] + 1.0))
    return mutations


def self_test(actual_profile, label="publicationProfile"):
    """Reject real corruptions of an actual validated profile snapshot."""
    validate(actual_profile, label)
    results = []
    for name, mutate in _mutations(actual_profile):
        candidate = copy.deepcopy(actual_profile)
        try:
            mutate(candidate)
            validate(candidate, label + " mutation " + name)
        except ValidationError as error:
            results.append({
                "case": name,
                "status": "REJECTED_AS_EXPECTED",
                "error": str(error),
            })
        else:
            raise ValidationError("profile corruption was accepted: " + name)
    return {
        "status": "PASS",
        "label": label,
        "caseCount": len(results),
        "cases": results,
    }


def validate_report(report, label="applicationReport"):
    """Validate duplicated cold/warm terminal snapshots without rewriting input."""
    need(isinstance(report, dict), label + " must be an object")
    snapshots = {}
    summaries = {}
    for phase in ("cold", "warm"):
        run = report.get(phase)
        need(isinstance(run, dict), label + " is missing " + phase + " run")
        nested = run.get("publicationProfile")
        root_key = phase + "PublicationProfile"
        root_snapshot = report.get(root_key)
        need(isinstance(nested, dict),
             label + " is missing " + phase + ".publicationProfile")
        need(isinstance(root_snapshot, dict),
             label + " is missing " + root_key)
        need(root_snapshot == nested,
             label + " duplicate " + root_key + " differs from " +
             phase + ".publicationProfile")
        snapshots[phase] = nested
        summaries[phase] = validate(nested, label + "." + root_key)
    return {
        "status": "PASS",
        "label": label,
        "duplicateSnapshotsExact": True,
        "snapshots": summaries,
    }, snapshots


def _read_json(path):
    try:
        raw = path.read_bytes()
        value = json.loads(raw.decode("utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValidationError("could not read UTF-8 JSON report: {}".format(error))
    return raw, value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path,
                        help="raw terminal application report JSON; read-only")
    parser.add_argument("--label", default="applicationReport",
                        help="label included in validation diagnostics")
    args = parser.parse_args()
    try:
        need(args.report.is_file(), "report file does not exist")
        raw, report = _read_json(args.report)
        summary, snapshots = validate_report(report, args.label)
        summary["reportSha256"] = hashlib.sha256(raw).hexdigest()
        summary["reportByteLength"] = len(raw)
        summary["metadataNegativeCanaries"] = {
            phase: self_test(snapshot, args.label + "." + phase)
            for phase, snapshot in snapshots.items()
        }
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
