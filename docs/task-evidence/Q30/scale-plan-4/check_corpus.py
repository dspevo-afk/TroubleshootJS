#!/usr/bin/env python3
"""Independently audit the predeclared Q30 normal-medium native corpus log."""

import argparse
import copy
import hashlib
import json
import math
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from check_native_service import (
    AuditInputError,
    PLAN_MANIFEST_PROPOSAL_STATUS,
    PLAN_MANIFEST_SCHEMA,
    PRESERVED_SEED_ORDER_SHA256,
    parse_plan_manifest as parse_q30_plan_manifest,
    parse_plan_metadata as parse_q30_plan_metadata,
    validate_plan_export_evidence,
    validate_plan_metadata_row,
)


ROW_PREFIX = "Q30_CORPUS_JSON:"
MARKER_PREFIX = "PASS: Q30 normal corpus contracts"
PLAN_MANIFEST_SCHEMA = "q30-scale-plan4-manifest-proposal@1"
PLAN_MANIFEST_PROPOSAL_STATUS = "PROPOSAL_NOT_QUALIFICATION"
SERVICE_PLAN = ("7", "13", "4", "14", "43", "3", "10", "64")
PRIOR_SERVICE_SEEDS = ("0",)
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1
PLAN_AXES = frozenset(
    "RB30_CH1_{}_A_{}".format(arrangement, driver_a)
    for arrangement in ("SEPARATE_DIRECT", "SHARED_DIRECT", "SHARED_HYSTERETIC")
    for driver_a in ("BJT", "NMOS")
) | frozenset(
    "RB30_CH2_{}_A_{}_B_{}".format(arrangement, driver_a, driver_b)
    for arrangement in ("SEPARATE_DIRECT", "SHARED_DIRECT", "SHARED_HYSTERETIC")
    for driver_a in ("BJT", "NMOS")
    for driver_b in ("BJT", "NMOS")
)
SUPPORT_FEATURES = (
    "entryCapacitor", "fiveVoltIndicator", "twelveVoltIndicator",
    "sensorInputFilterA", "outputIndicatorA", "fiveVoltBleeder",
)
CHANNEL_B_SUPPORT_FEATURES = ("sensorInputFilterB", "outputIndicatorB")
REACHABLE_FAULTS = frozenset((
    "DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN",
    "RELAY_A_COIL_OPEN", "RELAY_B_COIL_OPEN",
))

MARKER_RE = re.compile(
    r"^PASS: Q30 normal corpus contracts "
    r"outcome=(ACCEPTED|REJECTED) "
    r"routeOutcome=(SUCCESS|REJECTED) "
    r"normalAdmission=(ACCEPTED|REJECTED_POLICY|REJECTED_ROUTE) "
    r"cleanup=true assertions=([0-9]+) seed=(-?(?:0|[1-9][0-9]*))$"
)
CORPUS_CASE_RE = re.compile(
    r"^Q30_CORPUS_CASE seed=(-?(?:0|[1-9][0-9]*)) exit=(-?[0-9]+) "
    r"termination=(True|False) rowContract=(True|False)$"
)
CORPUS_CENSUS_RE = re.compile(
    r"^Q30_CORPUS_CENSUS attempted=([0-9]+) accepted=([0-9]+) "
    r"rejected=([0-9]+) failed=([0-9]+) childBudgetMs=([0-9]+)$"
)
CORPUS_FOCUSED_SUCCESS = (
    "PASS: focused current contracts; 1 Java suites. "
    "Full matrix and independent oracles NOT RUN."
)
CORPUS_FULL_SUCCESS_RE = re.compile(
    r"(?m)^PASS: current contracts; [0-9]+ Java suites, independent seed/value/role "
    r"oracles and report protocol\.\r?$"
)
CANONICAL_LONG_RE = re.compile(r"^(?:0|-[1-9][0-9]*|[1-9][0-9]*)$")
ROUTE_VECTOR_SPLIT_RE = re.compile(
    r", (?=P(?:05_ONE_FACE|07_FULLER_TWO_LAYER)@-?[0-9]+=)"
)
ROUTE_CANONICAL_RE = re.compile(
    r"^MEDIUM_BOARD@1;"
    r"outcome=(SUCCESS|REJECTED);selected=([^;]+);"
    r"placements=([0-9]+);placementRejects=([0-9]+);"
    r"placementEvaluations=([0-9]+);routes=([0-9]+);"
    r"routingWork=([0-9]+)/([0-9]+);"
    r"p05=([0-9]+)/([0-9]+);p07=([0-9]+)/([0-9]+);"
    r"selectedAttempt=(-?[0-9]+);ranked=\[.*?\];placementScores=\[.*?\];"
    r"selectedScore=(-?[0-9]+);selectedQualityMilli=(-?[0-9]+);"
    r"routeOutcomes=\[(.*?)\];failure=(.*)$"
)


class AuditError(Exception):
    pass


def fail(message):
    raise AuditError(message)


def canonical_long(value, where):
    if not isinstance(value, str) or not CANONICAL_LONG_RE.fullmatch(value):
        fail("%s is not a canonical signed-long string" % where)
    parsed = int(value)
    if parsed < LONG_MIN or parsed > LONG_MAX or str(parsed) != value:
        fail("%s is outside canonical signed-long range" % where)
    return value


def _support_identity(support):
    return "C12:{},S5:{},S12:{},filters:{},outputIndicators:{},bleeder5:{}".format(
        str(support["entryCapacitor"]).lower(),
        str(support["fiveVoltIndicator"]).lower(),
        str(support["twelveVoltIndicator"]).lower(),
        support["sensorInputFilterMask"], support["outputIndicatorMask"],
        str(support["fiveVoltBleeder"]).lower())


def _corpus_support(support):
    return "C12:{},S5:{},S12:{},F:{},O:{},B5:{}".format(
        str(support["entryCapacitor"]).lower(),
        str(support["fiveVoltIndicator"]).lower(),
        str(support["twelveVoltIndicator"]).lower(),
        support["sensorInputFilterMask"], support["outputIndicatorMask"],
        str(support["fiveVoltBleeder"]).lower())


def _expected_package_count(plan):
    channels = plan["channelCount"]
    arrangement = plan["referenceArrangement"]
    if arrangement == "SEPARATE_DIRECT":
        references = 2 * channels
    elif arrangement == "SHARED_DIRECT":
        references = 2
    elif arrangement == "SHARED_HYSTERETIC":
        references = 2 + channels
    else:
        fail("unknown plan4 reference arrangement %r" % arrangement)
    support = plan["support"]
    support_parts = (
        int(support["entryCapacitor"]) +
        2 * int(support["fiveVoltIndicator"]) +
        2 * int(support["twelveVoltIndicator"]) +
        bin(int(support["sensorInputFilterMask"])).count("1") +
        2 * bin(int(support["outputIndicatorMask"])).count("1") +
        int(support["fiveVoltBleeder"]))
    return 8 + 10 * channels + references + support_parts


def _synthetic_plan_metadata(row):
    plan = row["expectedPlan4"]
    support = plan["support"]
    channels = plan["channelCount"]
    relay = "RELAY_A_COIL_OPEN" if channels == 1 else "RELAY_B_COIL_OPEN"
    owner = "KA" if channels == 1 else "KB"
    topology = plan["topologyAxis"] + "_C12_" + ("Y" if support["entryCapacitor"] else "N") + \
        "_S5_" + ("Y" if support["fiveVoltIndicator"] else "N") + \
        "_S12_" + ("Y" if support["twelveVoltIndicator"] else "N") + \
        "_F{}".format(support["sensorInputFilterMask"]) + \
        "_O{}".format(support["outputIndicatorMask"]) + \
        "_B5_" + ("Y" if support["fiveVoltBleeder"] else "N")
    return {
        "schema": 1,
        "seed": plan["seed"],
        "canonical": plan["canonical"],
        "planEpoch": 4,
        "topology": topology,
        "channelCount": channels,
        "activeChannels": ["A"] if channels == 1 else ["A", "B"],
        "packageCount": plan["packageCount"],
        "referenceArrangement": plan["referenceArrangement"],
        "support": {
            "C12": support["entryCapacitor"],
            "S5": support["fiveVoltIndicator"],
            "S12": support["twelveVoltIndicator"],
            "filters": support["sensorInputFilterMask"],
            "outputIndicators": support["outputIndicatorMask"],
            "bleeder5": support["fiveVoltBleeder"],
        },
        "faults": [
            {"id": fault, "owner": owner_id}
            for fault, owner_id in (
                ("DREV_OPEN", "DREV"), ("REN_OPEN", "REN"),
                ("SENSOR_A_OPEN", "RSA"), ("DRIVE_A_OPEN", "RDA"),
                (relay, owner))
        ],
        "diagnosticProviderDeveloper": "rb30-control-diagnostic@2",
        "diagnosticProviderNormal": "rb30-control-diagnostic@4",
        "diagnosticTemplate": "RB30_CHANNEL_INPUT_SWEEP_V1",
        "diagnosticSamplesPerHypothesis": (17 if channels == 1 else 37),
        "temporalContract": "RB30_CHANNEL_FUNCTION@3",
        "sampleSeconds": 0.03,
        "profileWorkUnits": 3 if channels == 1 else 5,
        "customerRetestWorkUnits": 3 if channels == 1 else 5,
        "maxJobMillis": 90000,
        "maxJobSteps": 640,
        "activeOperationMillis": 5000,
    }


def _validate_manifest_plan_row(row, index):
    seed = row.get("seed")
    plan = row.get("expectedPlan4")
    where = "plan4 manifest seed {} at ordinal {}".format(seed, index)
    if not isinstance(plan, dict):
        fail("%s is missing expectedPlan4" % where)
    expected_keys = {
        "canonical", "channelCount", "choiceTrace", "derivationVersion",
        "driverABjt", "driverBActive", "driverBBjt", "layoutSeed",
        "packageCount", "planVersion", "referenceArrangement",
        "requestedSupportCode", "requestedSupportDraws", "routingSeed",
        "seed", "selectedFault", "support", "supportIdentity",
        "supportNormalization", "topologyAxis",
    }
    if set(plan) != expected_keys:
        fail("%s has an unexpected independent plan4 field set" % where)
    if (plan.get("seed") != seed or type(plan.get("planVersion")) is not int or
            plan["planVersion"] != 4 or type(plan.get("derivationVersion")) is not int or
            plan["derivationVersion"] != 1):
        fail("%s has a stale/mismatched seed or plan version" % where)
    channels = plan.get("channelCount")
    if type(channels) is not int or channels not in (1, 2):
        fail("%s channel count must be 1 or 2" % where)
    axis = plan.get("topologyAxis")
    axis_match = re.fullmatch(
        r"RB30_CH([12])_(SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_A_(BJT|NMOS)(?:_B_(BJT|NMOS))?",
        axis if isinstance(axis, str) else "")
    if (axis_match is None or int(axis_match.group(1)) != channels or
            (channels == 1 and axis_match.group(4) is not None) or
            (channels == 2 and axis_match.group(4) is None) or axis not in PLAN_AXES):
        fail("%s has an invalid one/two-channel topology axis" % where)
    support = plan.get("support")
    support_keys = {
        "code", "entryCapacitor", "fiveVoltBleeder", "fiveVoltIndicator",
        "outputIndicatorA", "outputIndicatorB", "outputIndicatorMask",
        "sensorInputFilterA", "sensorInputFilterB", "sensorInputFilterMask",
        "twelveVoltIndicator",
    }
    if not isinstance(support, dict) or set(support) != support_keys:
        fail("%s has an invalid feature support object" % where)
    boolean_support = support_keys - {"code", "outputIndicatorMask", "sensorInputFilterMask"}
    if any(type(support.get(field)) is not bool for field in boolean_support):
        fail("%s support feature flags are not JSON booleans" % where)
    if (type(support.get("code")) is not int or not 0 <= support["code"] <= 255 or
            type(support.get("outputIndicatorMask")) is not int or
            support["outputIndicatorMask"] not in (0, 1, 2, 3) or
            type(support.get("sensorInputFilterMask")) is not int or
            support["sensorInputFilterMask"] not in (0, 1, 2, 3)):
        fail("%s has an invalid support mask/code" % where)
    if (support["sensorInputFilterA"] != bool(support["sensorInputFilterMask"] & 1) or
            support["sensorInputFilterB"] != bool(support["sensorInputFilterMask"] & 2) or
            support["outputIndicatorA"] != bool(support["outputIndicatorMask"] & 1) or
            support["outputIndicatorB"] != bool(support["outputIndicatorMask"] & 2) or
            (channels == 1 and
             (support["sensorInputFilterB"] or support["outputIndicatorB"]))):
        fail("%s support flags disagree with masks or active channels" % where)
    if plan.get("supportIdentity") != _support_identity(support):
        fail("%s support identity string does not match its feature flags" % where)
    arrangement = plan.get("referenceArrangement")
    if arrangement != axis_match.group(2):
        fail("%s reference arrangement disagrees with topology axis" % where)
    if (type(plan.get("driverABjt")) is not bool or
            plan["driverABjt"] != (axis_match.group(3) == "BJT") or
            type(plan.get("driverBActive")) is not bool or
            plan["driverBActive"] != (channels == 2) or
            type(plan.get("driverBBjt")) is not bool or
            (channels == 2 and plan["driverBBjt"] != (axis_match.group(4) == "BJT"))):
        fail("%s driver selection does not match topology axis" % where)
    if type(plan.get("packageCount")) is not int or \
            plan["packageCount"] != _expected_package_count(plan) or \
            not 20 <= plan["packageCount"] <= 40:
        fail("%s package count does not follow the frozen physical-count formula" % where)
    for field in ("layoutSeed", "routingSeed"):
        value = plan.get(field)
        canonical_long(value, where + " " + field)
    allowed_faults = set(REACHABLE_FAULTS)
    if (plan.get("selectedFault") not in allowed_faults or
            (channels == 1 and plan["selectedFault"] == "RELAY_B_COIL_OPEN") or
            (channels == 2 and plan["selectedFault"] == "RELAY_A_COIL_OPEN")):
        fail("%s selected fault is not one of the plan's reachable channel faults" % where)
    metadata = _synthetic_plan_metadata(row)
    plan_issues = validate_plan_metadata_row(metadata, row, where)
    if plan_issues:
        fail("; ".join(plan_issues))
    return {
        "seed": seed,
        "cohort": row.get("cohort"),
        "cohortCoverageEligible": row.get("cohortCoverageEligible"),
        "rowKind": row.get("rowKind"),
        "planVersion": 4,
        "canonical": plan["canonical"],
        "channelCount": channels,
        "topologyAxis": axis,
        "support": _corpus_support(support),
        "supportFlags": support,
        "partCount": plan["packageCount"],
        "selectedFault": plan["selectedFault"],
        "expectedObservations": (7 + channels) * (1 << channels) + 1,
        "profileWorkUnits": 3 if channels == 1 else 5,
        "proofWorkUnits": 230 if channels == 1 else 390,
        "layoutSeed": plan["layoutSeed"],
        "routingSeed": plan["routingSeed"],
        "raw": row,
    }


def parse_manifest(manifest_path):
    try:
        parsed = parse_q30_plan_manifest(manifest_path)
    except (OSError, UnicodeError, AuditInputError) as exc:
        fail("cannot load explicit independent plan4 manifest: %s" % exc)
    raw = parsed["raw"]
    rows = raw["seeds"]
    normalized = []
    old_matrix = []
    for index, row in enumerate(rows):
        if set(row) != {"cohort", "cohortCoverageEligible", "expectedPlan4",
                        "legacyPlan3", "rowKind", "seed", "selection"}:
            fail("manifest row %d has an unexpected field set" % index)
        item = _validate_manifest_plan_row(row, index)
        if index < 48:
            legacy = row["legacyPlan3"]
            if (not isinstance(legacy, dict) or set(legacy) != {
                    "axisCode", "partCount", "planEpoch", "supportVariant", "topologyAxis"} or
                    type(legacy.get("planEpoch")) is not int or legacy["planEpoch"] != 3):
                fail("preserved historical matrix row %s lost its plan3 audit identity" % item["seed"])
            old_matrix.append(item)
        elif row["legacyPlan3"] is not None:
            fail("signed-long/appended plan4 row %s unexpectedly claims a legacy matrix plan" % item["seed"])
        cohort = row["cohort"]
        eligible = row["cohortCoverageEligible"]
        expected_cohort = cohort in ("Representative", "Held-out")
        if type(eligible) is not bool or eligible is not expected_cohort:
            fail("manifest seed %s has inconsistent cohort coverage eligibility" % item["seed"])
        if index < 48 and cohort not in ("Representative", "Held-out"):
            fail("preserved matrix seed %s lost its original cohort" % item["seed"])
        if 48 <= index < 51 and cohort != "Signed-long replay":
            fail("preserved signed-long replay seed %s lost its cohort" % item["seed"])
        if index >= 51 and cohort not in ("Representative", "Held-out"):
            fail("appended root %s has an invalid coverage cohort" % item["seed"])
        normalized.append(item)

    if [item["seed"] for item in normalized[:51]] != parsed["seeds"][:51]:
        fail("plan4 manifest did not preserve the original 51-seed prefix")
    if [item["seed"] for item in normalized[51:]] != parsed["seeds"][51:]:
        fail("plan4 manifest appended roots changed their declared order")
    if Counter(item["cohort"] for item in normalized) != Counter({
            "Representative": 35, "Held-out": 39, "Signed-long replay": 3}):
        fail("plan4 manifest cohort census is not 35 representative/39 held-out/3 replay")
    if len(old_matrix) != 48:
        fail("plan4 manifest must retain all 48 historical matrix rows")

    for cohort in ("Representative", "Held-out"):
        cohort_rows = [item for item in normalized if item["cohort"] == cohort]
        if {item["topologyAxis"] for item in cohort_rows} != PLAN_AXES:
            fail("%s plan4 roots do not cover all 18 topology axes" % cohort)
        if {item["partCount"] for item in cohort_rows} != set(range(20, 41)):
            fail("%s plan4 roots do not cover every package count 20..40" % cohort)
        if {item["selectedFault"] for item in cohort_rows} != REACHABLE_FAULTS:
            fail("%s plan4 roots do not cover all six reachable selected faults" % cohort)
        if {item["raw"]["expectedPlan4"]["referenceArrangement"]
                for item in cohort_rows} != {
                    "SEPARATE_DIRECT", "SHARED_DIRECT", "SHARED_HYSTERETIC"}:
            fail("%s plan4 roots do not cover all reference arrangements" % cohort)
        for feature in SUPPORT_FEATURES:
            values = {item["supportFlags"][feature] for item in cohort_rows}
            if values != {False, True}:
                fail("%s roots do not cover both states of support feature %s" % (cohort, feature))
        for feature in CHANNEL_B_SUPPORT_FEATURES:
            eligible_rows = [item for item in cohort_rows if item["channelCount"] == 2]
            if {item["supportFlags"][feature] for item in eligible_rows} != {False, True}:
                fail("%s two-channel roots do not cover both states of %s" % (cohort, feature))
        if {item["raw"]["expectedPlan4"]["driverABjt"] for item in cohort_rows} != {False, True}:
            fail("%s roots do not cover both channel-A driver kinds" % cohort)
        two_channel = [item for item in cohort_rows if item["channelCount"] == 2]
        if {item["raw"]["expectedPlan4"]["driverBBjt"] for item in two_channel} != {False, True}:
            fail("%s two-channel roots do not cover both channel-B driver kinds" % cohort)

    for seed in SERVICE_PLAN + PRIOR_SERVICE_SEEDS:
        if seed not in parsed["bySeed"]:
            fail("service-plan seed %s is absent from the explicit plan4 manifest" % seed)
    by_seed = {item["seed"]: item for item in normalized}
    return {
        "raw": raw,
        "rows": normalized,
        "bySeed": by_seed,
        "seeds": [item["seed"] for item in normalized],
        "manifestStatus": raw["status"],
        "planContract": {
            "seeds": [item["seed"] for item in normalized],
            "bySeed": {item["seed"]: item["raw"] for item in normalized},
        },
    }


def parse_route_canonical(value, where):
    if not isinstance(value, str):
        fail("%s is missing its full medium route canonical" % where)
    match = ROUTE_CANONICAL_RE.fullmatch(value)
    if not match:
        fail("%s has a malformed or truncated medium route canonical" % where)
    (outcome, selected, placements, placement_rejects, placement_evaluations,
     routes, orders, expansions, p05_attempts, p05_successes, p07_attempts,
     p07_successes, selected_attempt, selected_score, quality, outcomes_text,
     failure) = match.groups()
    route_outcomes = [] if not outcomes_text else ROUTE_VECTOR_SPLIT_RE.split(outcomes_text)
    numbers = {
        "placements": int(placements),
        "placementRejects": int(placement_rejects),
        "placementEvaluations": int(placement_evaluations),
        "routes": int(routes),
        "routingOrders": int(orders),
        "routingExpansions": int(expansions),
        "p05Attempts": int(p05_attempts),
        "p05Successes": int(p05_successes),
        "p07Attempts": int(p07_attempts),
        "p07Successes": int(p07_successes),
        "selectedAttempt": int(selected_attempt),
        "selectedScore": int(selected_score),
        "selectedQualityMilli": int(quality),
    }
    if numbers["placements"] != 6 or numbers["placementRejects"] > 6:
        fail("%s did not retain the unchanged six-placement policy budget" % where)
    if (numbers["p05Attempts"] > 3 or numbers["p07Attempts"] > 3 or
            numbers["routes"] != numbers["p05Attempts"] + numbers["p07Attempts"] or
            numbers["routes"] != len(route_outcomes)):
        fail("%s route attempts disagree with the unchanged P05/P07 budgets" % where)
    if (numbers["p05Successes"] > numbers["p05Attempts"] or
            numbers["p07Successes"] > numbers["p07Attempts"] or
            numbers["routingOrders"] < 0 or numbers["routingExpansions"] < 0):
        fail("%s route statistics contain impossible success/work counts" % where)
    if outcome == "SUCCESS":
        if selected not in ("P05_ONE_FACE", "P07_FULLER_TWO_LAYER"):
            fail("%s success has no selected route policy" % where)
        if not 0 <= numbers["selectedAttempt"] < 6 or failure != "null":
            fail("%s success has an invalid selected placement or failure field" % where)
    else:
        if selected != "null" or numbers["selectedAttempt"] != -1 or failure == "null":
            fail("%s rejection lacks the complete failure/selection receipt" % where)
    return {
        "outcome": outcome,
        "selected": selected,
        "failure": failure,
        "routeOutcomes": route_outcomes,
        **numbers,
    }


def require_bool(row, key, expected, seed):
    value = row.get(key)
    if type(value) is not bool or value is not expected:
        fail("seed %s has invalid %s=%r" % (seed, key, value))


def require_exact_int(value, expected, where):
    if type(value) is not int or value != expected:
        fail("%s must be integer %d, got %r" % (where, expected, value))


def require_int(row, key, seed, minimum=0):
    value = row.get(key)
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        fail("seed %s has invalid timing/count %s=%r" % (seed, key, value))
    return value


def require_nullable_int(row, key, seed, should_exist):
    value = row.get(key)
    if should_exist:
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            fail("seed %s has missing/invalid %s=%r" % (seed, key, value))
    elif value is not None:
        fail("seed %s should not report %s=%r" % (seed, key, value))
    return value


def nonempty_hash(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{1,8}", value) is not None


def validate_row(row, manifest):
    if not isinstance(row, dict):
        fail("corpus JSON row is not an object")
    seed = canonical_long(row.get("seed"), "corpus row seed")
    expected = manifest["bySeed"].get(seed)
    if expected is None:
        fail("corpus row seed %s is not in the explicit independent plan4 manifest" % seed)
    require_exact_int(row.get("schema"), 1, "seed %s schema" % seed)
    require_exact_int(row.get("planEpoch"), 4, "seed %s planEpoch" % seed)

    support = row.get("support")
    axis = row.get("topologyAxis")
    parts = row.get("partCount")
    for key, expected_value in (
            ("support", expected["support"]),
            ("topologyAxis", expected["topologyAxis"]),
            ("partCount", expected["partCount"])):
        if row.get(key) != expected_value:
            fail("seed %s disagrees with independent plan4 %s: expected %r got %r" %
                 (seed, key, expected_value, row.get(key)))

    require_bool(row, "contractValidated", True, seed)
    require_bool(row, "cleanup", True, seed)
    if row.get("validationFailure") is not None:
        fail("seed %s contains a validation failure: %r" %
             (seed, row.get("validationFailure")))
    if row.get("proofPhase") != "NOT_RUN_BY_NORMAL_CORPUS_CONTRACT":
        fail("seed %s misstates the solver-proof scope of this structural harness" % seed)
    if row.get("routeTimingMethod") != \
            "first six session advances=placement; remaining advances=routing":
        fail("seed %s has an undocumented placement/routing phase boundary" % seed)

    route_outcome = row.get("routeOutcome")
    normal_outcome = row.get("outcome")
    admission = row.get("normalAdmissionOutcome")
    if route_outcome not in ("SUCCESS", "REJECTED"):
        fail("seed %s has invalid routeOutcome=%r" % (seed, route_outcome))
    if normal_outcome not in ("ACCEPTED", "REJECTED"):
        fail("seed %s has invalid normal outcome=%r" % (seed, normal_outcome))

    cold = parse_route_canonical(row.get("coldRouteCanonical"),
                                 "seed %s cold route" % seed)
    warm = parse_route_canonical(row.get("warmRouteCanonical"),
                                 "seed %s warm route" % seed)
    if row["coldRouteCanonical"] != row["warmRouteCanonical"]:
        fail("seed %s cold/warm full route receipts differ" % seed)
    for field in ("coldRouteOutcomes", "warmRouteOutcomes"):
        value = row.get(field)
        if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
            fail("seed %s has malformed %s" % (seed, field))
    if (row["coldRouteOutcomes"] != cold["routeOutcomes"] or
            row["warmRouteOutcomes"] != warm["routeOutcomes"] or
            row["coldRouteOutcomes"] != row["warmRouteOutcomes"]):
        fail("seed %s omitted or changed an individual cold/warm route outcome" % seed)

    cold_placement_advances = require_int(row, "coldPlacementAdvances", seed)
    cold_routing_advances = require_int(row, "coldRoutingAdvances", seed)
    if cold_placement_advances != 6 or cold_routing_advances <= 0:
        fail("seed %s has incomplete cold route-phase advance counts" % seed)
    # The current row schema serializes warm phase durations but not warm
    # advance counts. The exact warm canonical below still proves its six
    # placement candidates and route attempt census; expose the count omission
    # as a reader limitation rather than fabricating an elapsed-work count.

    required_timing = (
        "coldConstructionNanos", "coldRouteNanos", "coldPlacementAdvanceNanos",
        "coldRoutingAdvanceNanos", "warmConstructionNanos", "warmRouteNanos",
        "warmPlacementAdvanceNanos", "warmRoutingAdvanceNanos", "cleanupNanos",
        "totalNanos",
    )
    for key in required_timing:
        require_int(row, key, seed)
    for prefix in ("cold", "warm"):
        if row[prefix + "RouteNanos"] < (
                row[prefix + "PlacementAdvanceNanos"] +
                row[prefix + "RoutingAdvanceNanos"]):
            fail("seed %s %s route time is smaller than its measured advances" %
                 (seed, prefix))
    required_elapsed = (row["coldConstructionNanos"] + row["coldRouteNanos"] +
                        row["warmConstructionNanos"] + row["warmRouteNanos"] +
                        row["cleanupNanos"])
    if row["totalNanos"] < required_elapsed:
        fail("seed %s total wall time excludes recorded generation/routing/cleanup work" % seed)
    if not isinstance(row.get("coldRouteCanonical"), str):
        fail("seed %s cold route canonical is absent" % seed)

    route_reasons = []
    for outcome in row["coldRouteOutcomes"]:
        if "=REJECTED:" in outcome:
            route_reasons.append(outcome.split("=REJECTED:", 1)[1])

    if normal_outcome == "ACCEPTED":
        if (route_outcome != "SUCCESS" or admission != "ACCEPTED" or
                cold["outcome"] != "SUCCESS" or
                cold["selected"] != "P07_FULLER_TWO_LAYER" or
                cold["p07Successes"] < 1):
            fail("seed %s accepted without a selected normal-eligible P07 route" % seed)
        if row.get("normalAdmissionReason") is not None:
            fail("seed %s accepted row contains a rejection reason" % seed)
        if row.get("routeRejection") is not None:
            fail("seed %s accepted route carries a terminal route rejection" % seed)
        if (type(row.get("hypothesisCount")) is not int or
                row.get("hypothesisCount") != 5 or
                type(row.get("observationCount")) is not int or
                row.get("observationCount") != expected["expectedObservations"]):
            fail("seed %s accepted without the plan4 five-hypothesis/%d-observation structure" %
                 (seed, expected["expectedObservations"]))
        if not nonempty_hash(row.get("physicalFingerprintHash")) or \
                not nonempty_hash(row.get("layoutFingerprintHash")):
            fail("seed %s accepted without physical and layout replay fingerprints" % seed)
        require_nullable_int(row, "coldAdmissionAssemblyNanos", seed, True)
        require_nullable_int(row, "warmAdmissionAssemblyNanos", seed, True)
        require_nullable_int(row, "coldGenerationNanos", seed, True)
        require_nullable_int(row, "warmGenerationNanos", seed, True)
        require_nullable_int(row, "normalStructuralReplayNanos", seed, True)
        if row["coldGenerationNanos"] != (
                row["coldConstructionNanos"] + row["coldAdmissionAssemblyNanos"]):
            fail("seed %s cold generation time does not reconcile" % seed)
        if row["warmGenerationNanos"] != (
                row["warmConstructionNanos"] + row["warmAdmissionAssemblyNanos"]):
            fail("seed %s warm generation time does not reconcile" % seed)
        if row["routeRejection"] is not None:
            fail("seed %s accepted row also claims terminal route rejection" % seed)
    else:
        if (admission not in ("REJECTED_ROUTE", "REJECTED_POLICY") or
                type(row.get("hypothesisCount")) is not int or
                row.get("hypothesisCount") != 0 or
                type(row.get("observationCount")) is not int or
                row.get("observationCount") != 0 or
                row.get("physicalFingerprintHash") is not None):
            fail("seed %s rejection was mislabeled or carries qualified-board proof" % seed)
        require_nullable_int(row, "coldAdmissionAssemblyNanos", seed, False)
        require_nullable_int(row, "warmAdmissionAssemblyNanos", seed, False)
        require_nullable_int(row, "coldGenerationNanos", seed, False)
        require_nullable_int(row, "warmGenerationNanos", seed, False)
        require_nullable_int(row, "normalStructuralReplayNanos", seed, False)
        if admission == "REJECTED_ROUTE":
            if (route_outcome != "REJECTED" or cold["outcome"] != "REJECTED" or
                    cold["selected"] != "null" or row.get("routeRejection") is None or
                    not isinstance(row.get("routeRejection"), str) or
                    not row["routeRejection"]):
                fail("seed %s route rejection was omitted or misclassified" % seed)
            if row.get("layoutFingerprintHash") is not None:
                fail("seed %s rejected route published a qualified layout fingerprint" % seed)
            if cold["failure"] == "null":
                fail("seed %s route rejection has no raw failure receipt" % seed)
        else:
            if (route_outcome != "SUCCESS" or cold["outcome"] != "SUCCESS" or
                    cold["selected"] != "P05_ONE_FACE" or
                    row.get("normalAdmissionReason") !=
                        "P05_ONLY_NORMAL_ADMISSION_REQUIRES_P07" or
                    row.get("routeRejection") is not None or
                    not nonempty_hash(row.get("layoutFingerprintHash"))):
                fail("seed %s P05-only normal rejection was lost or misclassified" % seed)
        if row.get("normalAdmissionOutcome") != admission:
            fail("seed %s admission outcome disagrees with normal rejection" % seed)

    return {
        "seed": seed,
        "cohort": expected["cohort"],
        "rowKind": expected["rowKind"],
        "channelCount": expected["channelCount"],
        "selectedFault": expected["selectedFault"],
        "expectedObservations": expected["expectedObservations"],
        "outcome": normal_outcome,
        "routeOutcome": route_outcome,
        "normalAdmissionOutcome": admission,
        "normalAdmissionReason": row.get("normalAdmissionReason"),
        "support": support,
        "supportFlags": expected["supportFlags"],
        "partCount": parts,
        "topologyAxis": axis,
        "selectedRoutePolicy": cold["selected"],
        "routeFailureReasons": route_reasons,
        "coldConstructionNanos": row["coldConstructionNanos"],
        "coldPlacementAdvanceNanos": row["coldPlacementAdvanceNanos"],
        "coldRoutingAdvanceNanos": row["coldRoutingAdvanceNanos"],
        "coldAdmissionAssemblyNanos": row.get("coldAdmissionAssemblyNanos"),
        "normalStructuralReplayNanos": row.get("normalStructuralReplayNanos"),
        "warmConstructionNanos": row["warmConstructionNanos"],
        "warmPlacementAdvanceNanos": row["warmPlacementAdvanceNanos"],
        "warmRoutingAdvanceNanos": row["warmRoutingAdvanceNanos"],
        "warmAdmissionAssemblyNanos": row.get("warmAdmissionAssemblyNanos"),
        "routeRejection": row.get("routeRejection"),
    }


def parse_log(text):
    rows = []
    markers = []
    for line_number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith(ROW_PREFIX):
            raw = stripped[len(ROW_PREFIX):]
            try:
                row = json.loads(raw,
                    parse_constant=lambda v: fail("non-finite JSON value %s" % v),
                    object_pairs_hook=unique_json_object)
            except (json.JSONDecodeError, TypeError) as exc:
                fail("malformed corpus JSON at line %d: %s" % (line_number, exc))
            rows.append((line_number, row))
        if MARKER_PREFIX in line:
            match = MARKER_RE.fullmatch(stripped)
            if not match:
                fail("malformed or failed corpus PASS marker at line %d" % line_number)
            outcome, route_outcome, admission, assertions, seed = match.groups()
            markers.append({
                "line": line_number,
                "outcome": outcome,
                "routeOutcome": route_outcome,
                "normalAdmissionOutcome": admission,
                "assertions": int(assertions),
                "seed": canonical_long(seed, "PASS marker seed"),
            })
        if re.search(r"\b(?:TIMEOUT|TIMED_OUT|TIMED OUT|BLOCKED|PROCESS_FAILED|"
                     r"EXIT_CODE=2|EXIT CODE 2|timed out|exceeded outer limit)\b",
                     stripped, re.I):
            fail("native corpus log contains a timeout/block/process failure at line %d: %s" %
                 (line_number, stripped[:240]))
        if stripped.startswith("Exception in thread ") or \
                stripped.startswith("AssertionError:") or \
                stripped.startswith("FAIL: Q30 normal corpus"):
            fail("native corpus log contains a failed child at line %d: %s" %
                 (line_number, stripped[:240]))
    return rows, markers


def unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail("duplicate JSON object key %s" % key)
        result[key] = value
    return result


def validate_census(rows_with_lines, markers, expected_seeds, manifest, full_census):
    expected_set = set(expected_seeds)
    by_seed = {}
    line_by_seed = {}
    for line_number, row in rows_with_lines:
        if not isinstance(row, dict):
            fail("JSON row at line %d is not an object" % line_number)
        seed = canonical_long(row.get("seed"), "JSON row seed at line %d" % line_number)
        if seed in by_seed:
            fail("duplicate corpus row for seed %s at lines %d and %d" %
                 (seed, line_by_seed[seed], line_number))
        by_seed[seed] = row
        line_by_seed[seed] = line_number
    actual_set = set(by_seed)
    if actual_set != expected_set:
        missing = sorted(expected_set - actual_set, key=int)
        extra = sorted(actual_set - expected_set, key=int)
        fail("corpus seed census mismatch; missing=%s extra=%s" % (missing, extra))
    actual_order = [canonical_long(row.get("seed"), "JSON row seed")
                    for _, row in rows_with_lines if isinstance(row, dict)]
    if actual_order != expected_seeds:
        fail("corpus JSON rows do not preserve declared batch/manifest seed order")

    marker_by_seed = {}
    for marker in markers:
        seed = marker["seed"]
        if seed in marker_by_seed:
            fail("duplicate final PASS marker for seed %s" % seed)
        marker_by_seed[seed] = marker
    if set(marker_by_seed) != expected_set:
        missing = sorted(expected_set - set(marker_by_seed), key=int)
        extra = sorted(set(marker_by_seed) - expected_set, key=int)
        fail("final marker census mismatch; missing=%s extra=%s" % (missing, extra))
    if [marker["seed"] for marker in markers] != expected_seeds:
        fail("corpus final markers do not preserve declared batch/manifest seed order")

    checked = []
    for seed in expected_seeds:
        row = by_seed[seed]
        result = validate_row(row, manifest)
        marker = marker_by_seed[seed]
        for key in ("outcome", "routeOutcome", "normalAdmissionOutcome"):
            if marker[key] != row.get(key):
                fail("seed %s PASS marker %s disagrees with its JSON row" % (seed, key))
        if marker["assertions"] <= 0:
            fail("seed %s PASS marker reports no contract assertions" % seed)
        checked.append(result)

    if full_census:
        if expected_seeds != manifest["seeds"] or len(checked) != 77:
            fail("full plan4 corpus must retain all 77 roots in declared manifest order")
        eligible_rows = [row for row in checked if row["cohort"] in (
            "Representative", "Held-out")]
        if len(eligible_rows) != 74:
            fail("full plan4 corpus lost representative/held-out roots or retyped replays")
        for cohort in ("Representative", "Held-out"):
            cohort_rows = [row for row in eligible_rows if row["cohort"] == cohort]
            if {row["topologyAxis"] for row in cohort_rows} != PLAN_AXES:
                fail("full %s rows do not cover all 18 plan4 topology axes" % cohort)
            if {row["partCount"] for row in cohort_rows} != set(range(20, 41)):
                fail("full %s rows do not cover package counts 20..40" % cohort)
            for feature in SUPPORT_FEATURES:
                if {row["supportFlags"][feature] for row in cohort_rows} != {False, True}:
                    fail("full %s rows lost support states for %s" % (cohort, feature))
            two_channel = [row for row in cohort_rows if row["channelCount"] == 2]
            for feature in CHANNEL_B_SUPPORT_FEATURES:
                if {row["supportFlags"][feature] for row in two_channel} != {False, True}:
                    fail("full %s two-channel rows lost support states for %s" %
                         (cohort, feature))
            if {row["selectedFault"] for row in cohort_rows} != REACHABLE_FAULTS:
                fail("full %s rows lost reachable selected fault values" % cohort)
        if sum(row["rowKind"] == "predeclared-epoch3-matrix" for row in checked) != 48 or \
                sum(row["rowKind"] == "predeclared-signed-long-replay" for row in checked) != 3 or \
                sum(row["rowKind"] == "appended-plan4-structural" for row in checked) != 26:
            fail("full plan4 corpus changed historical, replay, or appended root populations")
    return checked, by_seed


def nearest_rank(values, percentile):
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, int(math.ceil(percentile * len(ordered))))
    return ordered[rank - 1]


def distribution(rows, key):
    values = [row[key] for row in rows if isinstance(row.get(key), int)
              and not isinstance(row.get(key), bool)]
    return {
        "count": len(values),
        "p50NearestRank": nearest_rank(values, 0.50),
        "p95NearestRank": nearest_rank(values, 0.95),
    }


def latency_groups(checked):
    accepted = [row for row in checked if row["outcome"] == "ACCEPTED"]
    rejected_route = [row for row in checked
                      if row["normalAdmissionOutcome"] == "REJECTED_ROUTE"]
    rejected_policy = [row for row in checked
                       if row["normalAdmissionOutcome"] == "REJECTED_POLICY"]
    keys = (
        "coldConstructionNanos", "coldPlacementAdvanceNanos",
        "coldRoutingAdvanceNanos", "coldAdmissionAssemblyNanos",
        "normalStructuralReplayNanos", "warmConstructionNanos",
        "warmPlacementAdvanceNanos", "warmRoutingAdvanceNanos",
        "warmAdmissionAssemblyNanos",
    )
    return {
        "accepted": {key: distribution(accepted, key) for key in keys},
        "rejectedRoute": {key: distribution(rejected_route, key) for key in keys},
        "rejectedPolicy": {key: distribution(rejected_policy, key) for key in keys},
    }


def rejection_census(checked):
    reasons = defaultdict(list)
    for row in checked:
        for reason in row["routeFailureReasons"]:
            reasons[reason].append(row["seed"])
        if row["normalAdmissionOutcome"] == "REJECTED_ROUTE":
            reasons["normal route exhausted: " + str(row["routeRejection"])].append(row["seed"])
        elif row["normalAdmissionOutcome"] == "REJECTED_POLICY":
            reasons[row["normalAdmissionReason"]].append(row["seed"])
    return [
        {"reason": reason, "count": len(seeds), "seeds": seeds}
        for reason, seeds in sorted(reasons.items())
    ]


def corruption_canaries(rows, manifest, expected_seeds):
    by_seed = {canonical_long(row["seed"], "canary seed"): row for _, row in rows}
    accepted = next((row for row in by_seed.values()
                    if row.get("outcome") == "ACCEPTED"), None)
    rejected = next((row for row in by_seed.values()
                    if row.get("outcome") == "REJECTED"), None)
    if accepted is None or rejected is None:
        fail("corruption canaries require a real accepted row and a real rejected row")

    passed = []

    def expect_reject(name, operation):
        try:
            operation()
        except AuditError:
            passed.append(name)
        else:
            fail("corruption canary was not detected: %s" % name)

    # Row-level mutations exercise the independent schema and receipt checks.
    def mutated_row(source, mutation):
        changed = copy.deepcopy(source)
        mutation(changed)
        validate_row(changed, manifest)

    row_seed = accepted["seed"]
    expect_reject("plan-epoch-mismatch", lambda: mutated_row(
        accepted, lambda row: row.update(planEpoch=2)))
    expect_reject("noncanonical-long-seed", lambda: mutated_row(
        accepted, lambda row: row.update(seed="00")))
    expect_reject("frozen-support-or-topology-mismatch", lambda: mutated_row(
        accepted, lambda row: row.update(topologyAxis="RB30_INVALID_AXIS")))
    expect_reject("accepted-contract-flag-cleared", lambda: mutated_row(
        accepted, lambda row: row.update(contractValidated=False)))
    expect_reject("accepted-cleanup-flag-cleared", lambda: mutated_row(
        accepted, lambda row: row.update(cleanup=False)))
    expect_reject("hypothesis-population-truncated", lambda: mutated_row(
        accepted, lambda row: row.update(hypothesisCount=4)))
    expect_reject("observation-population-truncated", lambda: mutated_row(
        accepted, lambda row: row.update(observationCount=36)))
    expect_reject("layout-stamp-removed", lambda: mutated_row(
        accepted, lambda row: row.update(layoutFingerprintHash=None)))
    expect_reject("cold-warm-route-receipt-diverged", lambda: mutated_row(
        accepted, lambda row: row.update(warmRouteCanonical=
            row["warmRouteCanonical"] + ";corrupted=true")))
    expect_reject("accepted-reclassified-as-rejected", lambda: mutated_row(
        accepted, lambda row: row.update(outcome="REJECTED")))

    def corrupt_route_outcome_census(row):
        if row["coldRouteOutcomes"]:
            row["coldRouteOutcomes"] = row["coldRouteOutcomes"][:-1]
        else:
            row["coldRouteOutcomes"] = ["FOREIGN_ROUTE=SUCCESS"]
    expect_reject("route-outcome-census-corruption", lambda: mutated_row(
        rejected, corrupt_route_outcome_census))
    expect_reject("route-rejection-dropped", lambda: mutated_row(
        rejected, lambda row: row.update(outcome="ACCEPTED")))

    subset_seeds = [canonical_long(row["seed"], "canary seed")
                    for _, row in rows]
    expect_reject("missing-corpus-row", lambda: validate_census(
        rows[:-1], [], subset_seeds, manifest, False))
    duplicated = list(rows) + [(rows[0][0], copy.deepcopy(rows[0][1]))]
    expect_reject("duplicate-corpus-row", lambda: validate_census(
        duplicated, [], subset_seeds, manifest, False))
    expect_reject("timeout-is-not-a-pass", lambda: parse_log(
        "TIMEOUT|seed=%s\n" % row_seed))
    expect_reject("duplicate-json-key", lambda: parse_log(
        'Q30_CORPUS_JSON:{"seed":"0","seed":"1"}\n'))
    expect_reject("missing-final-marker", lambda: validate_census(
        rows, [], subset_seeds, manifest, False))
    return {"count": len(passed), "passed": passed}


def build_summary(checked, by_seed, manifest, full_census, canaries, batch_evidence):
    outcome_counts = Counter(row["outcome"] for row in checked)
    route_counts = Counter(row["routeOutcome"] for row in checked)
    admission_counts = Counter(row["normalAdmissionOutcome"] for row in checked)
    axis_counts = Counter(row["topologyAxis"] for row in checked)
    accepted_by_axis = {
        axis: [row["seed"] for row in checked
               if row["topologyAxis"] == axis and row["outcome"] == "ACCEPTED"]
        for axis in sorted(PLAN_AXES)
    }
    service_plan = []
    for seed in SERVICE_PLAN + PRIOR_SERVICE_SEEDS:
        row = by_seed.get(seed)
        if row is None:
            disposition = "NOT RUN"
            disposition_reason = "seed is not present in this corpus subset"
        elif row.get("outcome") != "ACCEPTED":
            disposition = "NOT RUN"
            if row.get("routeOutcome") == "REJECTED":
                disposition_reason = "routing rejected; no serviceable layout"
            else:
                disposition_reason = "normal admission rejected; no normal-eligible P07 route"
        else:
            disposition = "ELIGIBLE; SERVICE NOT RUN BY STRUCTURAL CORPUS"
            disposition_reason = "live service proof is outside this structural corpus"
        service_plan.append({
            "seed": seed,
            "currentSelection": seed in SERVICE_PLAN,
            "presentInThisRun": row is not None,
            "outcome": row.get("outcome") if row else None,
            "routeOutcome": row.get("routeOutcome") if row else None,
            "normalAdmissionOutcome": row.get("normalAdmissionOutcome") if row else None,
            "serviceDisposition": disposition,
            "serviceDispositionReason": disposition_reason,
        })
    return {
        "readerStatus": "PASS_STRUCTURAL_AUDIT" if full_census else "FAIL_INCOMPLETE_CENSUS",
        "scope": "full-77-root-plan4-census" if full_census else "FAIL_INCOMPLETE_CENSUS",
        "completeCensus": full_census,
        "qualification": "STRUCTURAL_AUDIT_ONLY; production acceptance is not claimed",
        "manifestSchema": manifest["raw"]["schema"],
        "manifestStatus": manifest["manifestStatus"],
        "manifestSeedCount": len(manifest["seeds"]),
        "rowsValidated": len(checked),
        "batchEvidence": batch_evidence,
        "outcomeCounts": dict(sorted(outcome_counts.items())),
        "routeOutcomeCounts": dict(sorted(route_counts.items())),
        "normalAdmissionOutcomeCounts": dict(sorted(admission_counts.items())),
        "topologyCounts": dict(sorted(axis_counts.items())),
        "acceptedByTopologyAxis": accepted_by_axis,
        "acceptedLatenciesNanos": latency_groups(checked)["accepted"],
        "rejectedLatenciesNanos": {
            "route": latency_groups(checked)["rejectedRoute"],
            "normalPolicy": latency_groups(checked)["rejectedPolicy"],
        },
        "routeFailureReasons": rejection_census(checked),
        "servicePlanSeeds": service_plan,
        "rows": checked,
        "corruptionCanaries": canaries,
        "timingEvidenceLimit":
            "Native structural timings only; this reader does not prove compiled generation or solver-proof budgets.",
        "layoutReplayEvidenceLimit":
            "The corpus row publishes one post-comparison physical/layout fingerprint; cold/warm full route receipts are independently compared here.",
        "warmPhaseCountEvidenceLimit":
            "Warm phase durations are recorded; the harness does not serialize separate warm placement/routing advance counts.",
    }


def parse_corpus_case_entries(receipt):
    cases = []
    for line_number, line in enumerate(receipt.splitlines(), 1):
        if not line.startswith("Q30_CORPUS_CASE "):
            continue
        match = CORPUS_CASE_RE.fullmatch(line)
        if not match:
            fail("malformed Q30_CORPUS_CASE wrapper at receipt line %d" % line_number)
        seed, exit_code, terminated, contract = match.groups()
        cases.append({
            "line": line_number,
            "seed": canonical_long(seed, "Q30_CORPUS_CASE seed"),
            "exitCode": int(exit_code),
            "terminationProven": terminated == "True",
            "rowContract": contract == "True",
            "raw": line,
        })
    return cases


def parse_corpus_census(text, where):
    matches = list(CORPUS_CENSUS_RE.finditer(text))
    if len(matches) != 1:
        fail("%s must contain exactly one Q30_CORPUS_CENSUS, found %d" %
             (where, len(matches)))
    match = matches[0]
    return {
        "attempted": int(match.group(1)),
        "accepted": int(match.group(2)),
        "rejected": int(match.group(3)),
        "failed": int(match.group(4)),
        "childBudgetMs": int(match.group(5)),
        "raw": match.group(0),
    }


def parse_corpus_runner_cleanup(log, where):
    cleanup = re.findall(
        r"(?m)^CLEANUP: current JVM contract classes and task-owned scratch removed\.\r?$",
        log)
    cleanup_failures = re.findall(r"(?m)^CURRENT_CONTRACT_CLEANUP:.*$", log)
    runner_failures = re.findall(r"(?m)^CURRENT_CONTRACT_FAILURE:.*$", log)
    full = CORPUS_FULL_SUCCESS_RE.findall(log)
    focused = re.findall(
        r"(?m)^" + re.escape(CORPUS_FOCUSED_SUCCESS) + r"\r?$", log)
    timeouts = re.findall(
        r"(?im)^.*Bounded process .* exceeded 60000ms; logs retained at .*", log)
    scope_issues = []
    if len(full) + len(focused) != 1:
        scope_issues.append("exactly one full-runner or one-suite corpus PASS marker is required")
    if full and focused:
        scope_issues.append("full and focused runner markers cannot be combined")
    if len(cleanup) != 1:
        scope_issues.append("exactly one successful task-owned cleanup marker is required")
    if cleanup_failures:
        scope_issues.append("runner reported cleanup failure")
    if runner_failures:
        scope_issues.append("runner reported contract failure")
    if timeouts:
        scope_issues.append("runner log contains bounded-process timeout diagnostics")
    if scope_issues:
        fail("%s runner/cleanup audit failed: %s" % (where, "; ".join(scope_issues)))
    return {
        "status": "PASS",
        "runnerScope": "full" if full else "focused-corpus-single-suite",
        "cleanupSuccessMarkers": len(cleanup),
        "runnerSuccessMarkers": len(full) + len(focused),
        "timeoutDiagnostics": len(timeouts),
    }


def _marker_identity(markers):
    return [tuple(marker[key] for key in (
        "outcome", "routeOutcome", "normalAdmissionOutcome", "assertions", "seed"))
        for marker in markers]


def audit_batch(name, log, receipt, manifest, expected_seeds):
    plan_manifest = manifest["planContract"]
    plans_log = parse_q30_plan_metadata(
        log, plan_manifest, expected_seeds, name + " host log")
    plans_receipt = parse_q30_plan_metadata(
        receipt, plan_manifest, expected_seeds, name + " receipt")
    if plans_log["lines"] != plans_receipt["lines"]:
        fail("%s host log and ReceiptOutputPath Q30_CASE_PLAN rows differ" % name)
    observed_plan_batch = [seed for seed in plans_log["seeds"] if seed in set(expected_seeds)]
    if observed_plan_batch != expected_seeds:
        fail("%s Q30_CASE_PLAN rows do not preserve the exact declared batch seed order" % name)

    log_rows, log_markers = parse_log(log)
    receipt_rows, receipt_markers = parse_log(receipt)
    if ([row for _, row in log_rows] != [row for _, row in receipt_rows] or
            _marker_identity(log_markers) != _marker_identity(receipt_markers)):
        fail("%s host log and receipt corpus rows/final markers differ" % name)
    if not log_rows or not receipt_rows:
        fail("%s is missing corpus JSON rows or final row markers" % name)
    checked, by_seed = validate_census(
        receipt_rows, receipt_markers, expected_seeds, manifest, False)

    cases = parse_corpus_case_entries(receipt)
    if [case["seed"] for case in cases] != expected_seeds:
        fail("%s Q30_CORPUS_CASE wrappers are missing, duplicated, extra, or reordered" % name)
    failed_cases = [case for case in cases if
                    case["exitCode"] != 0 or not case["terminationProven"] or
                    not case["rowContract"]]
    if failed_cases:
        fail("%s has failed/unproven corpus case wrappers: %s" %
             (name, [case["seed"] for case in failed_cases]))

    log_census = parse_corpus_census(log, name + " host log")
    receipt_census = parse_corpus_census(receipt, name + " receipt")
    if log_census != receipt_census:
        fail("%s host log and receipt census disagree" % name)
    outcomes = Counter(row["outcome"] for row in checked)
    if (log_census["attempted"] != len(expected_seeds) or
            log_census["accepted"] != outcomes.get("ACCEPTED", 0) or
            log_census["rejected"] != outcomes.get("REJECTED", 0) or
            log_census["failed"] != 0 or log_census["childBudgetMs"] != 60000):
        fail("%s corpus census does not reconcile with exact row population/child budget" % name)
    if len(cases) != log_census["attempted"]:
        fail("%s wrapper count disagrees with corpus census" % name)
    cleanup = parse_corpus_runner_cleanup(log, name + " host log")
    return {
        "name": name,
        "expectedSeeds": expected_seeds,
        "rowCount": len(checked),
        "planMetadataCount": len(plans_log["rows"]),
        "planMetadataOrder": plans_log["seeds"],
        "accepted": outcomes.get("ACCEPTED", 0),
        "rejected": outcomes.get("REJECTED", 0),
        "census": log_census,
        "runnerCleanup": cleanup,
    }, receipt_rows, receipt_markers, plans_log["rows"]


def plan_metadata_canaries(plan_records, manifest, seed):
    row = manifest["bySeed"][seed]["raw"]
    original = next((plan for plan in plan_records if plan.get("seed") == seed), None)
    if original is None:
        fail("plan metadata canaries have no actual Q30_CASE_PLAN row for seed %s" % seed)
    passed = []
    if validate_plan_metadata_row(original, row, "actual plan canary source"):
        fail("actual Q30_CASE_PLAN row does not satisfy its independent manifest during canaries")

    def reject_mutation(name, change):
        changed = copy.deepcopy(original)
        change(changed)
        if not validate_plan_metadata_row(changed, row, "mutated plan canary"):
            fail("plan metadata canary was not detected: %s" % name)
        passed.append(name)

    reject_mutation("stale-plan-epoch", lambda item: item.update(planEpoch=3))
    reject_mutation("foreign-canonical", lambda item: item.update(canonical=item["canonical"] + ";extra=true"))
    reject_mutation("template-at-version-one", lambda item: item.update(
        diagnosticTemplate="RB30_CHANNEL_INPUT_SWEEP@1"))
    reject_mutation("fault-owner-mismatch", lambda item: item["faults"][0].update(owner="FOREIGN"))
    reject_mutation("wrong-channel-observation-count", lambda item: item.update(
        diagnosticSamplesPerHypothesis=37 if item["channelCount"] == 1 else 17))
    return {"count": len(passed), "passed": passed}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True,
                        help="explicit independent Q30 plan4 manifest JSON")
    parser.add_argument("--plan-export-log", required=True,
                        help="host log from the focused 77-seed Java plan export")
    parser.add_argument("--plan-export-receipt", required=True,
                        help="ReceiptOutputPath from the focused 77-seed Java plan export")
    parser.add_argument("--plan-differential", required=True,
                        help="independent differential binding the plan export streams and manifest")
    parser.add_argument("--plan-run", required=True,
                        help="runner summary JSON for the focused 77-seed Java plan export")
    parser.add_argument("--plan-source", required=True,
                        help="the exact Rb30Plan.java source used by the Java export")
    parser.add_argument("--batch", nargs=3, action="append", required=True,
                        metavar=("NAME", "LOG", "RECEIPT"),
                        help="one declared corpus batch: preserved-51 or appended-26, plus its host log and ReceiptOutputPath")
    parser.add_argument("--report", help="write the structural audit JSON to this path instead of stdout")
    args = parser.parse_args(argv)

    try:
        manifest = parse_manifest(args.manifest)
        plan_export_evidence = validate_plan_export_evidence(
            manifest["planContract"], args.manifest, args.plan_export_log,
            args.plan_export_receipt, args.plan_differential, args.plan_run,
            args.plan_source)
        if len(args.batch) != 2 or [batch[0] for batch in args.batch] != [
                "preserved-51", "appended-26"]:
            fail("provide exactly --batch preserved-51 LOG RECEIPT followed by --batch appended-26 LOG RECEIPT")
        batch_specs = (
            ("preserved-51", args.batch[0][1], args.batch[0][2], manifest["seeds"][:51]),
            ("appended-26", args.batch[1][1], args.batch[1][2], manifest["seeds"][51:]),
        )
        batch_evidence = []
        rows = []
        markers = []
        plan_records = []
        for name, log_path, receipt_path, expected_batch_seeds in batch_specs:
            log_text = Path(log_path).read_text(encoding="utf-8-sig")
            receipt_text = Path(receipt_path).read_text(encoding="utf-8-sig")
            evidence, batch_rows, batch_markers, batch_plans = audit_batch(
                name, log_text, receipt_text, manifest, expected_batch_seeds)
            batch_evidence.append(evidence)
            rows.extend(batch_rows)
            markers.extend(batch_markers)
            plan_records.extend(batch_plans)
        expected_seeds = manifest["seeds"]
        full_census = True
        checked, by_seed = validate_census(
            rows, markers, expected_seeds, manifest, full_census)
        canaries = corruption_canaries(rows, manifest, expected_seeds)
        plan_canaries = plan_metadata_canaries(
            plan_records, manifest, manifest["seeds"][0])
        summary = build_summary(checked, by_seed, manifest, full_census, canaries,
                                batch_evidence)
        summary["planMetadataCanaries"] = plan_canaries
        summary["javaPlanExportEvidence"] = plan_export_evidence
        rendered = json.dumps(summary, sort_keys=True, separators=(",", ":")) + "\n"
        if args.report:
            Path(args.report).write_text(rendered, encoding="utf-8")
        else:
            sys.stdout.write(rendered)
        # This proves plan identity and the structural corpus only. It does not
        # qualify compiled production behavior or grant normal acceptance.
        return 0 if full_census and summary["readerStatus"] == "PASS_STRUCTURAL_AUDIT" else 3
    except (AuditError, AuditInputError, OSError, UnicodeError) as exc:
        print(json.dumps({"readerStatus": "FAIL", "error": str(exc)},
                         sort_keys=True, separators=(",", ":")), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
