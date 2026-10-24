#!/usr/bin/env python3
"""Independent, bounded reader for the U05 Q60 calibration observation cohort.

This tool consumes captured verifier envelopes. It does not run the simulator,
choose seeds, fit thresholds, or assign a player difficulty.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from functools import lru_cache
from pathlib import Path
from typing import Any, Callable

HERE = Path(__file__).resolve().parent
PLAN_SCHEMA = "tsj-u05-q60-current-calibration-plan/2"
CAPTURE_SCHEMA = "tsj-u05-calibration-capture/2"
READER_SCHEMA = "tsj-u05-calibration-feature-reader/1"
BASE_COMMIT = "885d5c6c3e1c228928d68c55702d75f6fab5aa77"
SOURCE_EPOCH = "tsj-generation-dependencies-v18"
FAMILY = "RB56_CONTROL"
EXPECTED_SEEDS = {
    "development": ["-1017", "-1067", "-1022"],
    "regression": ["9007199254741579", "-1004", "-1028"],
}
LIMITS = {"jobMillis": 90000, "jobWork": 640, "unitMillis": 5000}
MAX_RESISTANCE = 10_000_000.0
MAX_JSON_BYTES = 16 * 1024 * 1024
DC_SAMPLE_IDS = (
    "RECTIFIED_INPUT",
    "PRIMARY_BIAS",
    "PRIMARY_ENABLE",
    "ISOLATED_12V",
    "REGULATOR_ENABLE",
    "REGULATED_5V",
    "SENSOR_A_CONDITIONED",
    "DRIVER_A_COMMAND",
    "DRIVER_A_INPUT",
    "RELAY_COIL_RETURN",
    "OUTPUT_A",
)
OMEGA_ID = "DRIVER_A_RESISTANCE"
ALLOWED_SCOPES = {"PRIVATE_SHARED_WORKBENCH", "PRIVATE_PLAYER_SESSION"}
REQUIRED_TRUE = (
    "normalPhysicalAdmission",
    "fullProductionD01",
    "coldProof",
    "qualifiedPublication",
    "ownerRestored",
    "candidateDisposed",
    "temporaryMeterClean",
    "debugRestored",
    "coordinatorReleased",
    "playerSessionReleased",
    "playerBridgeReleased",
)


class ReaderError(ValueError):
    pass


def _need(condition: bool, message: str) -> None:
    if not condition:
        raise ReaderError(message)


def _is_int(value: Any) -> bool:
    return type(value) is int


def _is_number(value: Any) -> bool:
    if type(value) not in (int, float):
        return False
    try:
        return math.isfinite(float(value))
    except OverflowError:
        return False


def _object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ReaderError("duplicate JSON key")
        result[key] = value
    return result


def _reject_constant(_: str) -> None:
    raise ReaderError("non-finite JSON number")


def _decode_json(data: bytes, label: str) -> Any:
    _need(len(data) <= MAX_JSON_BYTES, f"{label}: file exceeds reader limit")
    try:
        return json.loads(
            data.decode("utf-8"),
            object_pairs_hook=_object_pairs,
            parse_constant=_reject_constant,
        )
    except ReaderError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ReaderError(f"{label}: invalid UTF-8 JSON") from exc


def _read_json(path: Path, label: str) -> tuple[Any, str]:
    try:
        with path.open("rb") as stream:
            data = stream.read(MAX_JSON_BYTES + 1)
    except OSError as exc:
        raise ReaderError(f"{label}: cannot read input") from exc
    _need(len(data) <= MAX_JSON_BYTES, f"{label}: file exceeds reader limit")
    return _decode_json(data, label), hashlib.sha256(data).hexdigest()


def _canonical_signed_long(value: Any, label: str) -> str:
    _need(type(value) is str, f"{label}: expected canonical signed-long text")
    _need(len(value) <= 20
          and re.fullmatch(r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)", value) is not None,
          f"{label}: noncanonical signed-long text")
    number = int(value)
    _need(-(1 << 63) <= number <= (1 << 63) - 1, f"{label}: signed-long overflow")
    return value


def _validate_plan(plan: Any) -> dict[str, Any]:
    _need(type(plan) is dict, "plan must be a JSON object")
    _need(plan.get("schema") == PLAN_SCHEMA, "unexpected cohort plan schema")
    _need(plan.get("baseCommit") == BASE_COMMIT, "cohort base commit mismatch")
    _need(plan.get("dependencyEpoch") == 18 and _is_int(plan.get("dependencyEpoch")),
          "dependency epoch mismatch")
    _need(plan.get("placement") == 15 and _is_int(plan.get("placement")),
          "placement version mismatch")
    _need(plan.get("development") == EXPECTED_SEEDS["development"],
          "development cohort differs from frozen seed order")
    _need(plan.get("regression") == EXPECTED_SEEDS["regression"],
          "regression cohort differs from frozen seed order")
    _need(plan.get("build") and type(plan["build"]) is str
          and re.fullmatch(r"[0-9A-F]{32}", plan["build"]) is not None,
          "plan must pin one uppercase 32-hex final build")
    _need(type(plan.get("sourceBinding")) is str
          and re.fullmatch(r"[0-9a-fA-F]{64}", plan["sourceBinding"]) is not None,
          "plan must pin a 64-hex final source binding")
    budgets = plan.get("budgets")
    _need(type(budgets) is dict, "plan budgets missing")
    _need(budgets.get("jobMillis") == LIMITS["jobMillis"]
          and budgets.get("jobWork") == LIMITS["jobWork"]
          and budgets.get("unitMillis") == LIMITS["unitMillis"],
          "plan budgets differ from the unchanged verifier caps")
    return plan


def _sample_same(left: dict[str, Any], right: dict[str, Any]) -> bool:
    if left["id"] != right["id"] or left["outcome"] != right["outcome"]:
        return False
    if left["outcome"] == "OVER_RANGE":
        return True
    return abs(float(left["value"]) - float(right["value"])) <= max(
        float(left["tolerance"]), float(right["tolerance"])
    )


def _expected_tolerance(value: float) -> float:
    return max(0.01, abs(value) * 0.02)


def _validate_observations(report: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    observations = report.get("calibrationObservations")
    _need(report.get("calibrationObservationFormat") == 1
          and _is_int(report.get("calibrationObservationFormat")),
          "calibration observation format must be 1")
    _need(type(observations) is list and len(observations) == 5,
          "exactly five ordered calibration observations are required")
    rows: list[dict[str, Any]] = []
    expected_ids = list(DC_SAMPLE_IDS)
    for raw_row in observations:
        _need(type(raw_row) is dict, "observation row must be an object")
        _need(set(raw_row) == {"ordinal", "repairClass", "samples"},
              "observation row has missing or unexpected fields")
        ordinal = raw_row["ordinal"]
        repair_class = raw_row["repairClass"]
        _need(_is_int(ordinal) and ordinal == len(rows), "observation ordinal/order mismatch")
        _need(_is_int(repair_class) and repair_class >= 0,
              "repair-class ordinal must be a nonnegative integer")
        samples = raw_row["samples"]
        _need(type(samples) is list, "observation samples must be an array")
        ids = [item.get("id") if type(item) is dict else None for item in samples]
        if not rows:
            allowed_inventories = (
                list(DC_SAMPLE_IDS) + [OMEGA_ID],
                list(DC_SAMPLE_IDS) + ["OUTPUT_B", OMEGA_ID],
            )
            _need(ids in allowed_inventories,
                  "observation sample inventory/order mismatch")
            expected_ids = ids
        else:
            _need(ids == expected_ids,
                  "observation sample inventory changed between rows")

        checked_samples: list[dict[str, Any]] = []
        for sample in samples:
            _need(type(sample) is dict, "sample must be an object")
            sample_id = sample.get("id")
            outcome = sample.get("outcome")
            _need(type(sample_id) is str and type(outcome) is str,
                  "sample identity and outcome must be strings")
            _need(outcome in ("NUMERIC", "OVER_RANGE"), "unknown observation outcome")
            is_omega = sample_id == OMEGA_ID
            if outcome == "OVER_RANGE":
                _need(is_omega, "only the resistance observation can be OVER_RANGE")
                _need(set(sample) == {"id", "outcome"},
                      "OVER_RANGE sample must not disclose a hidden value or tolerance")
                checked_samples.append({"id": sample_id, "outcome": outcome})
                continue

            _need(set(sample) == {"id", "outcome", "value", "tolerance"},
                  "numeric sample has missing or unexpected fields")
            value = sample["value"]
            tolerance = sample["tolerance"]
            _need(_is_number(value) and _is_number(tolerance),
                  "numeric value and tolerance must be finite numbers")
            value = float(value)
            tolerance = float(tolerance)
            _need(abs(value) <= 1.0e12, "numeric sample outside bounded reader range")
            _need(tolerance > 0.0
                  and math.isclose(tolerance, _expected_tolerance(value),
                                   rel_tol=1e-12, abs_tol=1e-12),
                  "numeric tolerance does not match the exported measurement rule")
            if is_omega:
                _need(value >= 0.0 and value <= MAX_RESISTANCE,
                      "numeric resistance is negative or beyond the meter range")
            checked_samples.append({
                "id": sample_id,
                "outcome": outcome,
                "value": value,
                "tolerance": tolerance,
            })
        _need(len(checked_samples) == len(expected_ids),
              "observation sample count mismatch")
        rows.append({
            "ordinal": ordinal,
            "repairClass": repair_class,
            "samples": checked_samples,
        })

    class_order: list[int] = []
    for row in rows:
        value = row["repairClass"]
        if value not in class_order:
            class_order.append(value)
    _need(class_order == list(range(len(class_order))),
          "opaque repair-class ordinals must be contiguous in first-seen order")
    for i in range(len(rows)):
        for j in range(i + 1, len(rows)):
            if rows[i]["repairClass"] != rows[j]["repairClass"]:
                same_all = all(
                    _sample_same(a, b)
                    for a, b in zip(rows[i]["samples"], rows[j]["samples"])
                )
                _need(not same_all,
                      "indistinguishable observation rows have different repair classes")
    return rows, {"sampleIds": expected_ids, "repairClassCount": len(class_order)}


def _analyze(rows: list[dict[str, Any]]) -> dict[str, Any]:
    count = len(rows)
    sample_ids = [sample["id"] for sample in rows[0]["samples"]]
    sample_count = len(sample_ids)
    same = [
        [
            [_sample_same(rows[i]["samples"][m], rows[j]["samples"][m])
             for j in range(count)]
            for i in range(count)
        ]
        for m in range(sample_count)
    ]
    classes = [row["repairClass"] for row in rows]
    full_mask = (1 << count) - 1

    def class_count(mask: int) -> int:
        return len({classes[i] for i in range(count) if mask & (1 << i)})

    def neighbor_mask(mask: int, measurement: int, exemplar: int) -> int:
        result = 0
        for candidate in range(count):
            if mask & (1 << candidate) and same[measurement][exemplar][candidate]:
                result |= 1 << candidate
        return result

    single_remaining = min(
        max(class_count(neighbor_mask(full_mask, m, i)) for i in range(count))
        for m in range(sample_count)
    )

    @lru_cache(maxsize=None)
    def minimum_worst_case(mask: int) -> int:
        if class_count(mask) <= 1:
            return 0
        best = count + 1
        for measurement in range(sample_count):
            worst = 0
            for exemplar in range(count):
                if not mask & (1 << exemplar):
                    continue
                child = neighbor_mask(mask, measurement, exemplar)
                if child == mask:
                    worst = count
                    break
                worst = max(worst, minimum_worst_case(child))
            best = min(best, 1 + worst)
        return best

    readings = minimum_worst_case(full_mask)
    _need(readings <= count,
          "exported features cannot distinguish the observed repair classes")
    if OMEGA_ID in sample_ids:
        omega_index = sample_ids.index(OMEGA_ID)
        omega_worst = max(
            class_count(neighbor_mask(full_mask, omega_index, i))
            for i in range(count)
        )
        omega_outcomes = {
            "numeric": sum(row["samples"][omega_index]["outcome"] == "NUMERIC" for row in rows),
            "overRange": sum(row["samples"][omega_index]["outcome"] == "OVER_RANGE" for row in rows),
        }
    else:
        omega_worst = None
        omega_outcomes = {"numeric": 0, "overRange": 0}
    return {
        "readings": readings,
        "singleRemaining": single_remaining,
        "omegaWorstRemaining": omega_worst,
        "omegaOutcomes": omega_outcomes,
        "repairClasses": class_count(full_mask),
        "hypotheses": count,
        "sampleIds": sample_ids,
    }


_FEATURE_RE = re.compile(
    r"tsj-difficulty-calibration/1;"
    r"status=UNCALIBRATED;"
    r"parts=([0-9]+);"
    r"hypotheses=([0-9]+);"
    r"repairs=([0-9]+);"
    r"owners=([0-9]+);"
    r"readings=([0-9]+);"
    r"singleRemaining=([0-9]+);"
    r"executedEvidenceDepth=([0-9]+)-([0-9]+);"
    r"legalWitnessActions=([0-9]+)-([0-9]+);"
    r"modes=([0-9]+);"
    r"inputs=([0-9]+);"
    r"railTargets=([0-9]+);"
    r"temporal=([0-9]+);"
    r"parallel=(true|false)"
)


def _parse_features(value: Any, packages: int, analysis: dict[str, Any]) -> dict[str, Any]:
    _need(type(value) is str, "difficultyFeatures must be a canonical string")
    match = _FEATURE_RE.fullmatch(value)
    _need(match is not None, "difficultyFeatures has invalid version or field order")
    values = [int(item) if item not in ("true", "false") else item
              for item in match.groups()]
    keys = (
        "parts", "hypotheses", "repairs", "owners", "readings",
        "singleRemaining", "evidenceDepthLow", "evidenceDepthHigh",
        "legalActionsLow", "legalActionsHigh", "modes", "inputs",
        "railTargets", "temporal", "parallel",
    )
    parsed = dict(zip(keys, values))
    _need(parsed["parts"] == packages, "feature part count differs from package count")
    _need(parsed["hypotheses"] == analysis["hypotheses"] == 5,
          "feature hypothesis count mismatch")
    _need(parsed["repairs"] == analysis["repairClasses"],
          "feature repair-class count mismatch")
    _need(parsed["readings"] == analysis["readings"],
          "feature minimax reading count mismatch")
    _need(parsed["singleRemaining"] == analysis["singleRemaining"],
          "feature one-reading worst-case count mismatch")
    _need(parsed["owners"] > 0 and parsed["modes"] > 0 and parsed["inputs"] > 0,
          "feature owner/mode/input counts must be positive")
    _need(parsed["evidenceDepthLow"] <= parsed["evidenceDepthHigh"],
          "feature evidence-depth range is inverted")
    _need(parsed["legalActionsLow"] <= parsed["legalActionsHigh"],
          "feature legal-action range is inverted")
    _need(parsed["railTargets"] >= 0 and parsed["temporal"] >= 0,
          "feature rail/temporal counts must be nonnegative")
    return parsed


def _validate_report(report: Any, expected_seed: str, plan: dict[str, Any]) -> dict[str, Any]:
    _need(type(report) is dict, "capture report must be an object")
    _need(report.get("protocol") == "TSJ-RB56-QUALIFICATION-1",
          "unexpected qualification protocol")
    _need(report.get("status") == "PASS", "native qualification did not pass")
    _need(not report.get("failure"), "report carries a failure despite PASS status")
    if "jobOutcome" in report:
        _need(report["jobOutcome"] == "PASS", "reported generation job did not pass")
    _need(report.get("family") == FAMILY, "wrong qualification family")
    _need(report.get("seed") == expected_seed, "report seed differs from frozen cohort")
    _canonical_signed_long(report.get("seed"), "report.seed")
    _need(report.get("sourceEpoch") == SOURCE_EPOCH, "source epoch mismatch")
    _need(report.get("planVersion") == 1 and _is_int(report.get("planVersion")),
          "qualification plan version mismatch")
    _need(type(report.get("scope")) is str and report["scope"] in ALLOWED_SCOPES, "qualification scope is not private")
    _need(report.get("forcedAfterPublish") is False, "forced-after-publish is forbidden")
    _need(report.get("normalCatalogEnabled") is False,
          "normal catalog must remain disabled during calibration")

    admission = report.get("physicalAdmission")
    _need(admission == "RB56_BOARD_NORMAL@1", "physical admission version mismatch")
    for flag in REQUIRED_TRUE:
        _need(report.get(flag) is True, f"required proof/cleanup flag is false: {flag}")
    _need(report.get("cleanupPending") is False, "cleanup remains pending")
    # Automatic qualification finishes after the complete D01 repair proofs;
    # it does not enter or complete an ordinary retained player view. A held
    # view has an additional real customer-retest obligation before cleanup.
    if "holdMillis" in report:
        _need(_is_int(report["holdMillis"]) and report["holdMillis"] > 0,
              "retained-view duration is invalid")
        _need(report.get("ordinaryCustomerRetestPassed") is True,
              "retained view did not pass its ordinary customer retest")
    else:
        _need(report.get("ordinaryCustomerRetestPassed") is False,
              "automatic qualification cannot claim an ordinary player retest")

    for key, expected in (
        ("maxJobMillis", LIMITS["jobMillis"]),
        ("maxJobSteps", LIMITS["jobWork"]),
        ("maxUnitMillis", LIMITS["unitMillis"]),
    ):
        _need(_is_int(report.get(key)) and report[key] == expected,
              f"unchanged verifier cap mismatch: {key}")
    _need(_is_int(report.get("generationMillis"))
          and 0 <= report["generationMillis"] <= LIMITS["jobMillis"],
          "generation time exceeds the job cap")
    _need(_is_int(report.get("generationWork"))
          and 0 <= report["generationWork"] <= LIMITS["jobWork"],
          "generation work exceeds the job cap")
    _need(_is_int(report.get("generationStages")) and report["generationStages"] == 6,
          "generation stage count differs from the frozen six-stage verifier proof")
    _need(_is_int(report.get("solverSamples")) and report["solverSamples"] > 0,
          "solver sample count must be positive")
    _need(_is_int(report.get("maxManualUnitMillis"))
          and 0 <= report["maxManualUnitMillis"] <= LIMITS["unitMillis"],
          "manual unit time exceeds the unit cap or is missing")
    if "manualUnitMaxMillis" in report:
        _need(_is_int(report["manualUnitMaxMillis"])
              and 0 <= report["manualUnitMaxMillis"] <= LIMITS["unitMillis"],
              "job manual unit time exceeds the unit cap")
        _need(report["manualUnitMaxMillis"] == report["maxManualUnitMillis"],
              "manual unit maximum fields disagree")
    for key, cap in (("jobMillis", LIMITS["jobMillis"]), ("jobWork", LIMITS["jobWork"])):
        if key in report:
            _need(_is_int(report[key]) and 0 <= report[key] <= cap,
                  f"actual job value exceeds cap: {key}")
    _need(report.get("difficultyFeatureStatus") == "UNCALIBRATED",
          "feature status must remain uncalibrated")
    _need(report.get("difficultyFeatureVersion") == 1
          and _is_int(report.get("difficultyFeatureVersion")),
          "difficulty feature version mismatch")
    _need(_is_int(report.get("difficultyFeatureExtractionMillis"))
          and report["difficultyFeatureExtractionMillis"] >= 0,
          "feature extraction time is invalid")

    rows, inventory = _validate_observations(report)
    analysis = _analyze(rows)
    _need(_is_int(report.get("packages")) and 40 <= report["packages"] <= 60,
          "package count is outside the qualified RB56 envelope")
    features = _parse_features(report.get("difficultyFeatures"), report["packages"], analysis)
    _need(report["solverSamples"] == len(rows) * len(inventory["sampleIds"]),
          "solver sample count differs from exported observation inventory")
    return {
        "analysis": analysis,
        "features": features,
        "inventory": inventory,
        "generationMillis": report["generationMillis"],
        "generationWork": report["generationWork"],
        "manualUnitMaxMillis": report["maxManualUnitMillis"],
        "packages": report["packages"],
        "scope": report["scope"],
        "seed": expected_seed,
    }


def _validate_capture(envelope: Any, expected_seed: str, plan: dict[str, Any]) -> dict[str, Any]:
    _need(type(envelope) is dict, "capture envelope must be an object")
    _need(set(envelope) == {"schema", "seed", "build", "sourceBinding", "report"},
          "capture envelope has missing or unexpected fields")
    _need(envelope.get("schema") == CAPTURE_SCHEMA, "capture envelope schema mismatch")
    _need(envelope.get("seed") == expected_seed, "envelope seed differs from frozen cohort")
    _canonical_signed_long(envelope.get("seed"), "envelope.seed")
    _need(envelope.get("build") == plan["build"], "capture build differs from final plan pin")
    _need(envelope.get("sourceBinding") == plan["sourceBinding"],
          "capture source binding differs from final plan pin")
    result = _validate_report(envelope.get("report"), expected_seed, plan)
    result["build"] = envelope["build"]
    result["sourceBinding"] = envelope["sourceBinding"]
    return result


def validate_cohort(
    plan: Any,
    partition: str,
    envelopes: list[tuple[Any, str]],
) -> dict[str, Any]:
    """Validate one complete frozen three-seed partition and return reduced features."""
    checked_plan = _validate_plan(plan)
    _need(type(partition) is str and partition in EXPECTED_SEEDS, "unknown cohort partition")
    expected = EXPECTED_SEEDS[partition]
    _need(type(envelopes) is list and len(envelopes) == len(expected), "the full frozen three-case partition is required")
    cases = []
    for index, ((envelope, digest), seed) in enumerate(zip(envelopes, expected)):
        _need(type(digest) is str and re.fullmatch(r"[0-9a-f]{64}", digest) is not None,
              "capture digest must be a lowercase SHA-256")
        checked = _validate_capture(envelope, seed, checked_plan)
        cases.append({
            "ordinal": index,
            "seed": seed,
            "captureSha256": digest,
            "generationMillis": checked["generationMillis"],
            "generationWork": checked["generationWork"],
            "manualUnitMaxMillis": checked["manualUnitMaxMillis"],
            "packages": checked["packages"],
            "hypotheses": checked["analysis"]["hypotheses"],
            "repairClasses": checked["analysis"]["repairClasses"],
            "readings": checked["analysis"]["readings"],
            "singleRemaining": checked["analysis"]["singleRemaining"],
            "omegaWorstRemaining": checked["analysis"]["omegaWorstRemaining"],
            "omegaOutcomes": checked["analysis"]["omegaOutcomes"],
            "omegaSampleCount": len(checked["analysis"]["sampleIds"]),
            "scope": checked["scope"],
        })
    _need(len({case["seed"] for case in cases}) == len(cases),
          "duplicate seed in cohort")
    return {
        "schema": READER_SCHEMA,
        "status": "PASS_FEATURE_REDUCTION_ONLY",
        "partition": partition,
        "selection": "FROZEN_CURATED_COHORT_NOT_RANDOM_HOLDOUT",
        "baseCommit": checked_plan["baseCommit"],
        "build": checked_plan["build"],
        "sourceBinding": checked_plan["sourceBinding"],
        "planBudgets": dict(LIMITS),
        "profileAssignment": "NONE",
        "difficultyLabelAssigned": False,
        "globalToolsetOptimumClaimed": False,
        "cases": cases,
    }


def _synthetic_plan() -> dict[str, Any]:
    return {
        "schema": PLAN_SCHEMA,
        "status": "FROZEN_COHORT_PRE_CAPTURE_SCOPE_AMENDED",
        "baseCommit": BASE_COMMIT,
        "build": "A" * 32,
        "sourceBinding": "b" * 64,
        "dependencyEpoch": 18,
        "placement": 15,
        "development": EXPECTED_SEEDS["development"],
        "regression": EXPECTED_SEEDS["regression"],
        "budgets": dict(LIMITS),
    }


def _feature_string(packages: int, analysis: dict[str, Any]) -> str:
    return (
        "tsj-difficulty-calibration/1;status=UNCALIBRATED;"
        f"parts={packages};hypotheses={analysis['hypotheses']};"
        f"repairs={analysis['repairClasses']};owners=5;"
        f"readings={analysis['readings']};singleRemaining={analysis['singleRemaining']};"
        "executedEvidenceDepth=1-3;legalWitnessActions=1-4;"
        "modes=2;inputs=1;railTargets=6;temporal=3;parallel=true"
    )


def _synthetic_report(seed: str, with_over_range: bool = False) -> dict[str, Any]:
    ids = list(DC_SAMPLE_IDS) + [OMEGA_ID]
    rows = []
    for ordinal in range(5):
        samples = []
        for column, sample_id in enumerate(ids):
            if sample_id == OMEGA_ID and with_over_range and ordinal == 4:
                samples.append({"id": sample_id, "outcome": "OVER_RANGE"})
            else:
                value = float(ordinal * 100 + column + 1)
                samples.append({
                    "id": sample_id,
                    "outcome": "NUMERIC",
                    "value": value,
                    "tolerance": _expected_tolerance(value),
                })
        rows.append({"ordinal": ordinal, "repairClass": ordinal, "samples": samples})
    provisional = {"calibrationObservations": rows, "calibrationObservationFormat": 1}
    normalized, _ = _validate_observations(provisional)
    analysis = _analyze(normalized)
    packages = 47
    return {
        "protocol": "TSJ-RB56-QUALIFICATION-1",
        "status": "PASS",
        "jobOutcome": "PASS",
        "family": FAMILY,
        "seed": seed,
        "sourceEpoch": SOURCE_EPOCH,
        "planVersion": 1,
        "scope": "PRIVATE_SHARED_WORKBENCH",
        "forcedAfterPublish": False,
        "normalCatalogEnabled": False,
        "physicalAdmission": "RB56_BOARD_NORMAL@1",
        **{flag: True for flag in REQUIRED_TRUE},
        "cleanupPending": False,
        "ordinaryCustomerRetestPassed": False,
        "maxJobMillis": LIMITS["jobMillis"],
        "maxJobSteps": LIMITS["jobWork"],
        "maxUnitMillis": LIMITS["unitMillis"],
        "generationMillis": 1000,
        "generationWork": 300,
        "generationStages": 6,
        "solverSamples": len(rows) * len(ids),
        "maxManualUnitMillis": 900,
        "manualUnitMaxMillis": 900,
        "difficultyFeatureStatus": "UNCALIBRATED",
        "difficultyFeatureVersion": 1,
        "difficultyFeatureExtractionMillis": 3,
        "packages": packages,
        "difficultyFeatures": _feature_string(packages, analysis),
        "calibrationObservationFormat": 1,
        "calibrationObservations": rows,
    }


def _expect_failure(label: str, action: Callable[[], Any]) -> None:
    try:
        action()
    except ReaderError:
        return
    raise AssertionError(f"negative self-test was accepted: {label}")


def self_test() -> None:
    # Two independent readings are needed to isolate four equivalence classes.
    four = [
        {"repairClass": i, "samples": [
            {"id": "A", "outcome": "NUMERIC",
             "value": (i // 2) * 10.0, "tolerance": 0.2},
            {"id": "B", "outcome": "NUMERIC",
             "value": (i % 2) * 10.0, "tolerance": 0.2},
        ]}
        for i in range(4)
    ]
    four_stats = _analyze(four)
    _need(four_stats["readings"] == 2, "minimax two-reading fixture failed")
    one = [
        {"repairClass": i, "samples": [
            {"id": "A", "outcome": "NUMERIC",
             "value": i * 10.0, "tolerance": 0.2},
        ]}
        for i in range(4)
    ]
    _need(_analyze(one)["readings"] == 1, "single-reading fixture failed")
    overlap_a = {"id": "A", "outcome": "NUMERIC", "value": 1.0, "tolerance": 0.02}
    overlap_b = {"id": "A", "outcome": "NUMERIC", "value": 1.01, "tolerance": 0.01}
    _need(_sample_same(overlap_a, overlap_b), "max-tolerance overlap fixture failed")
    _need(_sample_same(
        {"id": "A", "outcome": "OVER_RANGE"},
        {"id": "A", "outcome": "OVER_RANGE"},
    ), "over-range equivalence fixture failed")
    equivalent = [
        {"repairClass": repair_class, "samples": [
            {"id": "A", "outcome": "NUMERIC", "value": value, "tolerance": 0.2},
        ]}
        for repair_class, value in ((0, 1.0), (0, 10.0), (1, 20.0))
    ]
    _need(_analyze(equivalent)["readings"] == 1,
          "equivalent-repair-class fixture failed")

    plan = _synthetic_plan()
    seed = EXPECTED_SEEDS["development"][0]
    envelope = {
        "schema": CAPTURE_SCHEMA,
        "seed": seed,
        "build": plan["build"],
        "sourceBinding": plan["sourceBinding"],
        "report": _synthetic_report(seed, with_over_range=True),
    }
    _validate_capture(envelope, seed, plan)

    automatic = json.loads(json.dumps(envelope))
    del automatic["report"]["jobOutcome"]
    del automatic["report"]["manualUnitMaxMillis"]
    _validate_capture(automatic, seed, plan)
    retained = json.loads(json.dumps(envelope))
    retained["report"]["holdMillis"] = 120000
    _expect_failure("unrepaired retained view", lambda: _validate_capture(retained, seed, plan))
    retained["report"]["ordinaryCustomerRetestPassed"] = True
    _validate_capture(retained, seed, plan)
    invented_player_pass = json.loads(json.dumps(envelope))
    invented_player_pass["report"]["ordinaryCustomerRetestPassed"] = True
    _expect_failure("invented ordinary player retest", lambda: _validate_capture(invented_player_pass, seed, plan))

    bad = json.loads(json.dumps(envelope))
    bad["seed"] = "01"
    _expect_failure("noncanonical seed", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["build"] = "B" * 32
    _expect_failure("wrong build", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["sourceBinding"] = "c" * 64
    _expect_failure("wrong source binding", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["report"]["sourceEpoch"] = "tsj-generation-dependencies-v17"
    _expect_failure("wrong epoch", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["report"]["cleanupPending"] = True
    _expect_failure("pending cleanup", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["report"]["generationWork"] = 641
    _expect_failure("budget overrun", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["report"]["calibrationObservations"][0]["samples"][-1]["outcome"] = "OVER_RANGE"
    _expect_failure("over-range hidden numeric", lambda: _validate_capture(bad, seed, plan))
    bad = json.loads(json.dumps(envelope))
    bad["report"]["calibrationObservations"][0]["unexpected"] = True
    _expect_failure("unexpected observation key", lambda: _validate_capture(bad, seed, plan))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--partition", choices=tuple(EXPECTED_SEEDS))
    parser.add_argument("--reports", nargs=3, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            _need(args.plan is None and args.partition is None and args.reports is None
                  and args.output is None, "--self-test cannot be combined with cohort inputs")
            self_test()
            print("PASS: U05 calibration feature reader self-test")
            return 0
        _need(args.plan is not None and args.partition is not None
              and args.reports is not None and args.output is not None,
              "cohort mode requires --plan, --partition, three --reports, and --output")
        plan_raw, plan_digest = _read_json(args.plan, "plan")
        plan = _validate_plan(plan_raw)
        loaded = [_read_json(path, f"capture[{index}]")
                  for index, path in enumerate(args.reports)]
        result = validate_cohort(plan, args.partition, loaded)
        result["planSha256"] = plan_digest
        try:
            reader_digest = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        except OSError:
            reader_digest = "unavailable"
        result["readerSha256"] = reader_digest
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                               encoding="utf-8")
        print(
            f"PASS: U05 calibration feature reader partition={args.partition} "
            f"cases={len(result['cases'])} status={result['status']}"
        )
        return 0
    except (ReaderError, OSError, AssertionError) as exc:
        print(f"FAIL: U05 calibration feature reader: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
