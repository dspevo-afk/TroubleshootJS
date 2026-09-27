#!/usr/bin/env python3
"""Independently audit the predeclared Q30 normal-medium native corpus log."""

import argparse
import copy
import json
import math
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path


HERE = Path(__file__).resolve().parent
DEFAULT_README = HERE / "README.md"
DEFAULT_LOG = HERE / "native-corpus-02.log"
ROW_PREFIX = "Q30_CORPUS_JSON:"
MARKER_PREFIX = "PASS: Q30 normal corpus contracts"

SUPPORTS = {
    "COMPACT_33": ("Compact 33", 33),
    "STANDARD_35": ("Standard 35", 35),
    "FILTERED_37": ("Filtered 37", 37),
}
AXES = {
    "D-BB": "RB30_SEPARATE_DIRECT_A_BJT_B_BJT",
    "D-NB": "RB30_SEPARATE_DIRECT_A_NMOS_B_BJT",
    "D-BN": "RB30_SEPARATE_DIRECT_A_BJT_B_NMOS",
    "D-NN": "RB30_SEPARATE_DIRECT_A_NMOS_B_NMOS",
    "H-BB": "RB30_SHARED_HYSTERETIC_A_BJT_B_BJT",
    "H-NB": "RB30_SHARED_HYSTERETIC_A_NMOS_B_BJT",
    "H-BN": "RB30_SHARED_HYSTERETIC_A_BJT_B_NMOS",
    "H-NN": "RB30_SHARED_HYSTERETIC_A_NMOS_B_NMOS",
}
SERVICE_PLAN = ("7", "13", "4", "14", "43", "3", "10", "64")
PRIOR_SERVICE_SEEDS = ("0",)
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1

MARKER_RE = re.compile(
    r"^PASS: Q30 normal corpus contracts "
    r"outcome=(ACCEPTED|REJECTED) "
    r"routeOutcome=(SUCCESS|REJECTED) "
    r"normalAdmission=(ACCEPTED|REJECTED_POLICY|REJECTED_ROUTE) "
    r"cleanup=true assertions=([0-9]+) seed=(-?(?:0|[1-9][0-9]*))$"
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


def parse_manifest(readme_path):
    text = Path(readme_path).read_text(encoding="utf-8")
    lines = text.splitlines()
    header = "| Support | Population | D-BB | D-NB | D-BN | D-NN | H-BB | H-NB | H-BN | H-NN |"
    try:
        start = lines.index(header)
    except ValueError:
        fail("normal-admission README has no canonical frozen corpus table")

    manifest = []
    for line_number, line in enumerate(lines[start + 2:], start + 3):
        if not line.startswith("|"):
            break
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) != 10:
            fail("frozen corpus row at README line %d has %d cells" %
                 (line_number, len(cells)))
        support_label, cohort = cells[:2]
        support = next((key for key, value in SUPPORTS.items()
                        if value[0] == support_label), None)
        if support is None or cohort not in ("Representative", "Held-out"):
            fail("unknown frozen corpus row identity at README line %d" % line_number)
        for axis_code, raw_seed in zip(AXES, cells[2:]):
            canonical_long(raw_seed, "README seed at line %d" % line_number)
            manifest.append({
                "seed": raw_seed,
                "support": support,
                "partCount": SUPPORTS[support][1],
                "axisCode": axis_code,
                "topologyAxis": AXES[axis_code],
                "cohort": cohort,
            })

    if len(manifest) != 48:
        fail("README frozen table has %d cells; expected 48" % len(manifest))
    matrix_cells = set()
    seen = set()
    for item in manifest:
        if item["seed"] in seen:
            fail("README frozen table repeats seed %s" % item["seed"])
        seen.add(item["seed"])
        cell = (item["cohort"], item["support"], item["axisCode"])
        if cell in matrix_cells:
            fail("README frozen table repeats matrix cell %r" % (cell,))
        matrix_cells.add(cell)
    expected_cells = {
        (cohort, support, axis)
        for cohort in ("Representative", "Held-out")
        for support in SUPPORTS
        for axis in AXES
    }
    if matrix_cells != expected_cells:
        fail("README frozen table does not cover every support/topology/cohort cell")

    extremes_match = re.search(
        r"Additional exact signed-long replay seeds are\s*(.*?)\.", text, re.S)
    if not extremes_match:
        fail("README does not declare the three exact signed-long replay seeds")
    extremes = re.findall(r"`(-?[0-9]+)`", extremes_match.group(1))
    required_extremes = (str(LONG_MIN), str(LONG_MAX), "9007199254740993")
    if tuple(extremes) != required_extremes:
        fail("README extreme seeds changed: %r" % (extremes,))
    for seed in extremes:
        canonical_long(seed, "README extreme seed")
        if seed in seen:
            fail("README extreme seed duplicates the 48-cell matrix: %s" % seed)
        seen.add(seed)
        manifest.append({"seed": seed, "extreme": True})

    if len(manifest) != 51 or len(seen) != 51:
        fail("README does not declare exactly 51 unique corpus seeds")
    by_seed = {item["seed"]: item for item in manifest}
    for seed in SERVICE_PLAN:
        if seed not in by_seed or by_seed[seed].get("extreme"):
            fail("service-plan seed %s is not a frozen matrix row" % seed)
    return {"rows": manifest, "bySeed": by_seed, "seeds": [r["seed"] for r in manifest]}


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
        fail("corpus row seed %s is not in the frozen README manifest" % seed)
    require_exact_int(row.get("schema"), 1, "seed %s schema" % seed)
    require_exact_int(row.get("planEpoch"), 3, "seed %s planEpoch" % seed)

    support = row.get("support")
    axis = row.get("topologyAxis")
    parts = row.get("partCount")
    if expected.get("extreme"):
        if (support not in SUPPORTS or type(parts) is not int or
                parts != SUPPORTS[support][1] or axis not in AXES.values()):
            fail("extreme seed %s has invalid support/topology derivation" % seed)
    else:
        for key in ("support", "topologyAxis", "partCount"):
            if row.get(key) != expected[key]:
                fail("seed %s disagrees with frozen README %s: expected %r got %r" %
                     (seed, key, expected[key], row.get(key)))

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
                row.get("observationCount") != 37):
            fail("seed %s accepted without the complete five-hypothesis/37-observation structure" % seed)
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
        "outcome": normal_outcome,
        "routeOutcome": route_outcome,
        "normalAdmissionOutcome": admission,
        "normalAdmissionReason": row.get("normalAdmissionReason"),
        "support": support,
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
        matrix_rows = [r for r in checked if r["seed"] not in (
            str(LONG_MIN), str(LONG_MAX), "9007199254740993")]
        matrix_axes = {r["topologyAxis"] for r in matrix_rows}
        if matrix_axes != set(AXES.values()):
            fail("full matrix rows do not retain all eight topology axes")
        support_counts = Counter(r["support"] for r in matrix_rows)
        if support_counts != Counter({"COMPACT_33": 16, "STANDARD_35": 16,
                                      "FILTERED_37": 16}):
            fail("full matrix support census is incomplete: %r" % dict(support_counts))
        accepted_axes = {r["topologyAxis"] for r in matrix_rows
                         if r["outcome"] == "ACCEPTED"}
        if accepted_axes != set(AXES.values()):
            fail("accepted matrix rows do not cover all eight topology axes")
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

    def omit_route_outcome(row):
        row["coldRouteOutcomes"] = row["coldRouteOutcomes"][:-1]
    expect_reject("route-failure-outcome-omitted", lambda: mutated_row(
        rejected, omit_route_outcome))
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


def build_summary(checked, by_seed, manifest, full_census, canaries):
    outcome_counts = Counter(row["outcome"] for row in checked)
    route_counts = Counter(row["routeOutcome"] for row in checked)
    admission_counts = Counter(row["normalAdmissionOutcome"] for row in checked)
    axis_counts = Counter(row["topologyAxis"] for row in checked)
    accepted_by_axis = {
        axis: [row["seed"] for row in checked
               if row["topologyAxis"] == axis and row["outcome"] == "ACCEPTED"]
        for axis in AXES.values()
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
        "readerStatus": "PASS",
        "scope": "full-census" if full_census else "canary-subset",
        "completeCensus": full_census,
        "manifestSeedCount": len(manifest["seeds"]),
        "rowsValidated": len(checked),
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


def parse_seed_subset(raw, manifest):
    values = raw.split(",")
    if not values or any(value == "" for value in values):
        fail("--seeds must be a comma-separated list of canonical manifest seeds")
    for value in values:
        canonical_long(value, "--seeds entry")
    if len(values) != len(set(values)):
        fail("--seeds contains duplicates")
    unknown = [value for value in values if value not in manifest["bySeed"]]
    if unknown:
        fail("--seeds contains values outside the frozen README manifest: %s" % unknown)
    return values


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", default=str(DEFAULT_LOG),
                        help="native child-output log (default: native-corpus-02.log)")
    parser.add_argument("--readme", default=str(DEFAULT_README),
                        help="frozen corpus README (default: adjacent README.md)")
    parser.add_argument("--seeds", default=None,
                        help="explicit comma-separated manifest subset for a canary run")
    args = parser.parse_args(argv)

    try:
        manifest = parse_manifest(args.readme)
        expected_seeds = manifest["seeds"] if args.seeds is None else \
            parse_seed_subset(args.seeds, manifest)
        full_census = set(expected_seeds) == set(manifest["seeds"])
        text = Path(args.log).read_text(encoding="utf-8")
        rows, markers = parse_log(text)
        checked, by_seed = validate_census(
            rows, markers, expected_seeds, manifest, full_census)
        canaries = corruption_canaries(rows, manifest, expected_seeds)
        summary = build_summary(checked, by_seed, manifest, full_census, canaries)
        print(json.dumps(summary, sort_keys=True, separators=(",", ":")))
        return 0
    except (AuditError, OSError, UnicodeError) as exc:
        print(json.dumps({"readerStatus": "FAIL", "error": str(exc)},
                         sort_keys=True, separators=(",", ":")), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
