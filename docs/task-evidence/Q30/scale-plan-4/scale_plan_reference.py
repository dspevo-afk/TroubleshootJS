#!/usr/bin/env python3
"""Independent structural oracle and plan-4 seed-manifest proposal.

Named-stream integer operations follow the independent Task 46 Python reference.
Recipe choices are transcribed from the frozen scale-candidate Rb30Plan.java.
No Java output, route result, timing, solver result, or receipt is read.
"""
from __future__ import print_function

import argparse
import hashlib
import json
import re
from pathlib import Path

MASK = (1 << 64) - 1
SIGN = 1 << 63
OFFSET = 0xCBF29CE484222325
PRIME = 0x00000100000001B3
GAMMA = 0x9E3779B97F4A7C15
MUL1 = 0xBF58476D1CE4E5B9
MUL2 = 0x94D049BB133111EB
LONG_MIN, LONG_MAX = -(1 << 63), (1 << 63) - 1
FAMILY_ID, DEVICE_VERSION, DERIVATION_VERSION, PLAN_VERSION = "RB30_CONTROL", 1, 1, 4
MAX_PACKAGES = 40
ARRANGEMENTS = ("SEPARATE_DIRECT", "SHARED_DIRECT", "SHARED_HYSTERETIC")
SINGLE_FAULTS = ("DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN", "RELAY_A_COIL_OPEN")
DUAL_FAULTS = ("DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN", "RELAY_B_COIL_OPEN")

ENTRY, S5, S12, FA, FB, OA, OB, BLEED5 = (1, 2, 4, 8, 16, 32, 64, 128)
FEATURES = (
    ("entryCapacitor", ENTRY),
    ("fiveVoltIndicator", S5),
    ("twelveVoltIndicator", S12),
    ("sensorInputFilterA", FA),
    ("sensorInputFilterB", FB),
    ("outputIndicatorA", OA),
    ("outputIndicatorB", OB),
    ("fiveVoltBleeder", BLEED5),
)
ALWAYS_FEATURES = (
    "entryCapacitor", "fiveVoltIndicator", "twelveVoltIndicator",
    "sensorInputFilterA", "outputIndicatorA", "fiveVoltBleeder",
)
CHANNEL_B_FEATURES = ("sensorInputFilterB", "outputIndicatorB")
LEGACY_SUPPORTS = {
    "Compact 33": ("COMPACT_33", 33),
    "Standard 35": ("STANDARD_35", 35),
    "Filtered 37": ("FILTERED_37", 37),
}
LEGACY_AXES = {
    "D-BB": "RB30_SEPARATE_DIRECT_A_BJT_B_BJT",
    "D-NB": "RB30_SEPARATE_DIRECT_A_NMOS_B_BJT",
    "D-BN": "RB30_SEPARATE_DIRECT_A_BJT_B_NMOS",
    "D-NN": "RB30_SEPARATE_DIRECT_A_NMOS_B_NMOS",
    "H-BB": "RB30_SHARED_HYSTERETIC_A_BJT_B_BJT",
    "H-NB": "RB30_SHARED_HYSTERETIC_A_NMOS_B_BJT",
    "H-BN": "RB30_SHARED_HYSTERETIC_A_BJT_B_NMOS",
    "H-NN": "RB30_SHARED_HYSTERETIC_A_NMOS_B_NMOS",
}
EXTREMES = (str(LONG_MIN), str(LONG_MAX), "9007199254740993")
ROOT_RANGES = {"Representative": (10000, 20000), "Held-out": (20000, LONG_MAX)}
SCAN_LIMIT = 1000000
TABLE_HEADER = "| Support | Population | D-BB | D-NB | D-BN | D-NN | H-BB | H-NB | H-BN | H-NN |"


class ProposalError(Exception):
    pass


def fail(message):
    raise ProposalError(message)


def signed(value):
    value &= MASK
    return value - (1 << 64) if value & SIGN else value


def frame(value):
    raw = value.encode("ascii")
    return str(len(raw)).encode("ascii") + b":" + raw


def fnv_seed(root, scope, block, concern, revision, semantic):
    fields = (
        "tsj-named-seed", str(DERIVATION_VERSION), str(root), FAMILY_ID,
        str(DEVICE_VERSION), scope, block, concern, str(revision), semantic,
    )
    result = OFFSET
    for field in fields:
        for byte in frame(field):
            result = ((result ^ byte) * PRIME) & MASK
    return signed(result)


def splitmix(seed):
    state = seed & MASK
    while True:
        state = (state + GAMMA) & MASK
        value = state
        value = ((value ^ (value >> 30)) * MUL1) & MASK
        value = ((value ^ (value >> 27)) * MUL2) & MASK
        value = (value ^ (value >> 31)) & MASK
        yield signed(value)


def next_int(stream, bound):
    if bound <= 0:
        raise ValueError("positive bound required")
    while True:
        random = (next(stream) & MASK) >> 1
        value = random % bound
        if random - value + bound - 1 < (1 << 63):
            return value


def draw(root, scope, block, concern, revision, semantic, bound):
    derived = fnv_seed(root, scope, block, concern, revision, semantic)
    index = next_int(splitmix(derived), bound)
    return index, {"derivedSeed": str(derived), "bound": bound, "index": index}


def device_draw(root, concern, revision, semantic, bound):
    return draw(root, "device", "-", concern, revision, semantic, bound)


def block_draw(root, block, concern, revision, semantic, bound):
    return draw(root, "block", block, concern, revision, semantic, bound)


def popcount(value):
    return bin(value).count("1")


def package_count(channels, arrangement, code):
    refs = 2 * channels if arrangement == ARRANGEMENTS[0] else (
        2 if arrangement == ARRANGEMENTS[1] else 2 + channels
    )
    filters = (1 if code & FA else 0) | (2 if code & FB else 0)
    outputs = (1 if code & OA else 0) | (2 if code & OB else 0)
    return (
        8 + 10 * channels + refs +
        (1 if code & ENTRY else 0) +
        (2 if code & S5 else 0) +
        (2 if code & S12 else 0) +
        popcount(filters) + 2 * popcount(outputs) +
        (1 if code & BLEED5 else 0)
    )


def support_values(code):
    values = {name: bool(code & bit) for name, bit in FEATURES}
    values["sensorInputFilterMask"] = (
        (1 if code & FA else 0) | (2 if code & FB else 0)
    )
    values["outputIndicatorMask"] = (
        (1 if code & OA else 0) | (2 if code & OB else 0)
    )
    values["code"] = code
    return values


def normalize_support(root, channels, arrangement, requested):
    if package_count(channels, arrangement, requested) <= MAX_PACKAGES:
        return requested, {"applied": False, "hammingDistance": 0,
                           "nearestCodes": [], "tieIndex": None}
    nearest, best = [], 1 << 30
    for code in range(256):
        if channels == 1 and code & (FB | OB):
            continue
        if package_count(channels, arrangement, code) > MAX_PACKAGES:
            continue
        distance = popcount(code ^ requested)
        if distance < best:
            nearest, best = [], distance
        if distance == best:
            nearest.append(code)
    if not nearest:
        fail("no package-ceiling support vector")
    tie, trace = device_draw(root, "support", 2, "support-ceiling-tie", len(nearest))
    return nearest[tie], {
        "applied": True, "hammingDistance": best, "nearestCodes": nearest,
        "tieIndex": tie, "tieChoice": trace,
    }


def axis_name(channels, arrangement, driver_a, driver_b):
    value = "RB30_CH%d_%s_A_%s" % (
        channels, arrangement, "BJT" if driver_a else "NMOS"
    )
    return value + ("_B_" + ("BJT" if driver_b else "NMOS") if channels == 2 else "")


def plan4(root):
    if not isinstance(root, int) or not LONG_MIN <= root <= LONG_MAX:
        fail("root is outside signed-long range")
    choices = {}
    channel_choice, choices["channelPopulation"] = device_draw(
        root, "topology", 2, "channel-population", 2
    )
    channels = channel_choice + 1
    arrangement_choice, choices["referenceArrangement"] = device_draw(
        root, "topology", 2, "reference-arrangement", 3
    )
    arrangement = ARRANGEMENTS[arrangement_choice]
    driver_a_choice, choices["driverA"] = block_draw(
        root, "output-a", "topology", 1, "driver", 2
    )
    driver_b_choice, choices["driverB"] = block_draw(
        root, "output-b", "topology", 1, "driver", 2
    )
    driver_a, driver_b = driver_a_choice == 0, driver_b_choice == 0

    requested = 0
    requested_draws = {}
    def request(name, bit):
        index, details = device_draw(root, "support", 2, name, 2)
        requested_draws[name] = dict(details, present=(index == 0))
        return bit if index == 0 else 0

    requested |= request("entry-capacitor", ENTRY)
    requested |= request("five-volt-indicator", S5)
    requested |= request("twelve-volt-indicator", S12)
    requested |= request("sensor-filter-A", FA)
    if channels == 2:
        requested |= request("sensor-filter-B", FB)
    requested |= request("output-indicator-A", OA)
    if channels == 2:
        requested |= request("output-indicator-B", OB)
    requested |= request("five-volt-bleeder", BLEED5)

    code, normalization = normalize_support(root, channels, arrangement, requested)
    features = support_values(code)
    count = package_count(channels, arrangement, code)
    if not 20 <= count <= 40:
        fail("plan-4 count outside source bounds")
    faults = SINGLE_FAULTS if channels == 1 else DUAL_FAULTS
    fault_index, choices["fault"] = device_draw(
        root, "fault", 1, "serviceable-region", 5
    )
    layout = fnv_seed(root, "device", "-", "placement", 1, "board")
    routing = fnv_seed(root, "device", "-", "routing", 1, "copper")
    axis = axis_name(channels, arrangement, driver_a, driver_b)
    channels_list = ("A",) if channels == 1 else ("A", "B")
    pulldowns = ",".join(
        "RPIN_%s:1000ohm(%s_RAW,CTRL_RETURN)" % (ch, ch) for ch in channels_list
    )
    def jb(value):
        return "true" if value else "false"
    support_identity = (
        "C12:%s,S5:%s,S12:%s,filters:%d,outputIndicators:%d,bleeder5:%s"
        % (jb(features["entryCapacitor"]), jb(features["fiveVoltIndicator"]),
           jb(features["twelveVoltIndicator"]), features["sensorInputFilterMask"],
           features["outputIndicatorMask"], jb(features["fiveVoltBleeder"]))
    )
    canonical = (
        "rb30-plan@4;seed=%d;topology=%s;support=C12:%s,S5:%s,S12:%s,"
        "filters:%d,outputIndicators:%d,bleeder5:%s;layout=%d;routing=%d;"
        "physicalPolicy=MEDIUM_BOARD@1;fault=%s;packages=%d;main=12V;"
        "regulator=5V-E02;coil=5V-E03;load=isolated-12V-180ohm-per-channel;"
        "channels=%d;sensors=%d-E04-decisions;sensorPullDowns=%s;"
        "loadReference=isolated"
        % (root, axis, jb(features["entryCapacitor"]),
           jb(features["fiveVoltIndicator"]), jb(features["twelveVoltIndicator"]),
           features["sensorInputFilterMask"], features["outputIndicatorMask"],
           jb(features["fiveVoltBleeder"]), layout, routing, faults[fault_index],
           count, channels, channels, pulldowns)
    )
    return {
        "planVersion": PLAN_VERSION, "derivationVersion": DERIVATION_VERSION,
        "seed": str(root), "channelCount": channels,
        "referenceArrangement": arrangement,
        "driverABjt": driver_a, "driverBBjt": driver_b,
        "driverBActive": channels == 2, "topologyAxis": axis,
        "support": features, "requestedSupportCode": requested,
        "requestedSupportDraws": requested_draws,
        "supportNormalization": normalization, "packageCount": count,
        "selectedFault": faults[fault_index], "layoutSeed": str(layout),
        "routingSeed": str(routing), "supportIdentity": support_identity,
        "canonical": canonical, "choiceTrace": choices,
    }


def canonical_long(raw, where):
    if not isinstance(raw, str) or not re.fullmatch(
            r"(?:0|-[1-9][0-9]*|[1-9][0-9]*)", raw):
        fail("%s is not canonical decimal" % where)
    value = int(raw)
    if not LONG_MIN <= value <= LONG_MAX or str(value) != raw:
        fail("%s is outside signed-long range" % where)
    return value


def preserved_rows(readme):
    text = Path(readme).read_text(encoding="utf-8")
    lines = text.splitlines()
    try:
        start = lines.index(TABLE_HEADER)
    except ValueError:
        fail("README frozen matrix table not found")
    rows, used, matrix = [], set(), set()
    for number, line in enumerate(lines[start + 2:], start + 3):
        if not line.startswith("|"):
            break
        cells = [part.strip() for part in line.strip().strip("|").split("|")]
        if len(cells) != 10:
            fail("bad matrix row at README line %d" % number)
        support_label, cohort = cells[:2]
        if support_label not in LEGACY_SUPPORTS or cohort not in ("Representative", "Held-out"):
            fail("unknown matrix row identity at README line %d" % number)
        support, old_count = LEGACY_SUPPORTS[support_label]
        for axis, raw in zip(LEGACY_AXES, cells[2:]):
            seed = canonical_long(raw, "matrix seed")
            if raw in used:
                fail("duplicate frozen seed %s" % raw)
            used.add(raw)
            matrix.add((cohort, support, axis))
            rows.append({
                "seed": raw, "cohort": cohort, "cohortCoverageEligible": True,
                "rowKind": "predeclared-epoch3-matrix",
                "selection": {"kind": "predeclared", "reason": "retain frozen README seed and cohort"},
                "legacyPlan3": {
                    "planEpoch": 3, "supportVariant": support,
                    "partCount": old_count, "axisCode": axis,
                    "topologyAxis": LEGACY_AXES[axis],
                },
                "expectedPlan4": plan4(seed),
            })
    required = {
        (cohort, support[0], axis)
        for cohort in ("Representative", "Held-out")
        for support in LEGACY_SUPPORTS.values()
        for axis in LEGACY_AXES
    }
    if len(rows) != 48 or matrix != required:
        fail("README matrix is not the complete 48-cell two-cohort table")
    match = re.search(
        r"Additional exact signed-long replay seeds are\s*(.*?)\.", text, re.S
    )
    if not match:
        fail("README exact signed-long replay seed declaration missing")
    tick = chr(96)
    extremes = re.findall(tick + r"(-?[0-9]+)" + tick, match.group(1))
    if tuple(extremes) != EXTREMES:
        fail("README signed-long extreme roots changed")
    for raw in extremes:
        seed = canonical_long(raw, "signed-long replay seed")
        if raw in used:
            fail("replay extreme duplicates a matrix root")
        used.add(raw)
        rows.append({
            "seed": raw, "cohort": "Signed-long replay",
            "cohortCoverageEligible": False,
            "rowKind": "predeclared-signed-long-replay",
            "selection": {"kind": "predeclared", "reason": "retain frozen exact-long replay root"},
            "legacyPlan3": None, "expectedPlan4": plan4(seed),
        })
    if len(rows) != 51 or len(used) != 51:
        fail("must retain all 48 matrix and 3 signed-long rows")
    return rows


def topology_axes():
    result = []
    for channels in (1, 2):
        for arrangement in ARRANGEMENTS:
            for a in (True, False):
                for b in ((True, False) if channels == 2 else (None,)):
                    result.append(axis_name(channels, arrangement, a, b))
    return sorted(result)


REQUIRED_AXES = topology_axes()


def cohort_rows(rows, cohort):
    return [r for r in rows if r["cohort"] == cohort and r["cohortCoverageEligible"]]


def structural_gaps(rows, cohort):
    plans = [r["expectedPlan4"] for r in cohort_rows(rows, cohort)]
    missing_axes = sorted(set(REQUIRED_AXES) - {p["topologyAxis"] for p in plans})
    feature_gaps = {}
    for feature in ALWAYS_FEATURES:
        seen = {bool(p["support"][feature]) for p in plans}
        feature_gaps[feature] = [
            state for state, value in (("present", True), ("absent", False))
            if value not in seen
        ]
    dual = [p for p in plans if p["channelCount"] == 2]
    for feature in CHANNEL_B_FEATURES:
        seen = {bool(p["support"][feature]) for p in dual}
        feature_gaps[feature] = [
            state for state, value in (("present", True), ("absent", False))
            if value not in seen
        ]
    return {"topologyAxes": missing_axes, "supportFeatureStates": feature_gaps}


def structural_reasons(plan, gaps):
    reasons = {"topologyAxes": [], "supportFeatureStates": []}
    axis = plan["topologyAxis"]
    if axis in gaps["topologyAxes"]:
        reasons["topologyAxes"].append(axis)
    for name, states in gaps["supportFeatureStates"].items():
        if name in CHANNEL_B_FEATURES and plan["channelCount"] != 2:
            continue
        state = "present" if plan["support"][name] else "absent"
        if state in states:
            reasons["supportFeatureStates"].append({"feature": name, "state": state})
    return reasons


def append_row(rows, used, seed, cohort, phase, reason):
    raw = str(seed)
    if raw in used:
        fail("append tried reusing seed %s" % raw)
    used.add(raw)
    rows.append({
        "seed": raw, "cohort": cohort, "cohortCoverageEligible": True,
        "rowKind": "appended-plan4-structural",
        "selection": {"kind": phase, "reason": reason},
        "legacyPlan3": None, "expectedPlan4": plan4(seed),
    })


def append_counts(rows, used):
    selected = []
    for cohort, (start, stop) in ROOT_RANGES.items():
        missing = set(range(20, 41)) - {
            row["expectedPlan4"]["packageCount"] for row in cohort_rows(rows, cohort)
        }
        root = start
        while missing:
            if root >= stop or root - start >= SCAN_LIMIT:
                fail("count scan exhausted for %s" % cohort)
            candidate = plan4(root)
            if str(root) not in used and candidate["packageCount"] in missing:
                count = candidate["packageCount"]
                append_row(
                    rows, used, root, cohort, "first-ascending-missing-package-count",
                    "first unused root in [%d,%d) with still-missing package count %d"
                    % (start, stop, count),
                )
                missing.remove(count)
                selected.append({"seed": str(root), "cohort": cohort, "packageCount": count})
            root += 1
    return selected


def append_structure(rows, used):
    selected = []
    for cohort, (start, stop) in ROOT_RANGES.items():
        root = start
        while True:
            gaps = structural_gaps(rows, cohort)
            if not gaps["topologyAxes"] and not any(gaps["supportFeatureStates"].values()):
                break
            if root >= stop or root - start >= SCAN_LIMIT:
                fail("structural scan exhausted for %s" % cohort)
            if str(root) not in used:
                candidate = plan4(root)
                reasons = structural_reasons(candidate, gaps)
                if reasons["topologyAxes"] or reasons["supportFeatureStates"]:
                    append_row(rows, used, root, cohort,
                               "first-ascending-structural-coverage", reasons)
                    selected.append({
                        "seed": str(root), "cohort": cohort,
                        "packageCount": candidate["packageCount"], "fills": reasons,
                    })
            root += 1
    return selected


def count_distribution(rows, cohort):
    result = {str(n): 0 for n in range(20, 41)}
    for row in cohort_rows(rows, cohort):
        key = str(row["expectedPlan4"]["packageCount"])
        result[key] += 1
    return result


def feature_coverage(rows, cohort):
    plans = [r["expectedPlan4"] for r in cohort_rows(rows, cohort)]
    result = {}
    for name in ALWAYS_FEATURES + CHANNEL_B_FEATURES:
        eligible = plans if name in ALWAYS_FEATURES else [p for p in plans if p["channelCount"] == 2]
        present = [p["seed"] for p in eligible if p["support"][name]]
        absent = [p["seed"] for p in eligible if not p["support"][name]]
        result[name] = {
            "eligibility": "all cohort rows" if name in ALWAYS_FEATURES else "two-channel rows only",
            "eligibleRows": len(eligible), "presentRows": len(present), "absentRows": len(absent),
            "bothStatesCovered": bool(present and absent),
            "missingStates": [] if present and absent else (["present"] if not present else ["absent"]),
            "presentSeedExamples": sorted(present)[:10], "absentSeedExamples": sorted(absent)[:10],
        }
    return result


def coverage(rows, cohort):
    items = cohort_rows(rows, cohort)
    plans = [r["expectedPlan4"] for r in items]
    axes = sorted({p["topologyAxis"] for p in plans})
    counts = count_distribution(rows, cohort)
    supports = feature_coverage(rows, cohort)
    faults = sorted({p["selectedFault"] for p in plans})
    return {
        "rowCount": len(items),
        "channelCounts": {str(n): sum(p["channelCount"] == n for p in plans) for n in (1, 2)},
        "referenceArrangements": sorted({p["referenceArrangement"] for p in plans}),
        "driverAChoices": sorted({"BJT" if p["driverABjt"] else "NMOS" for p in plans}),
        "driverBChoicesWhenActive": sorted({
            "BJT" if p["driverBBjt"] else "NMOS" for p in plans if p["driverBActive"]
        }),
        "topologyAxisCoverage": {
            "requiredAxisCount": len(REQUIRED_AXES), "presentAxisCount": len(axes),
            "presentAxes": axes, "missingAxes": sorted(set(REQUIRED_AXES) - set(axes)),
            "complete": set(REQUIRED_AXES).issubset(axes),
        },
        "supportPresenceAbsence": supports,
        "missingSupportFeatures": sorted(name for name, value in supports.items()
                                         if not value["bothStatesCovered"]),
        "selectedFaultValues": faults,
        "allReachableFaultValuesPresent": set(faults) == set(SINGLE_FAULTS + DUAL_FAULTS),
        "packageCountDistribution20To40": counts,
        "missingPackageCounts20To40": [int(n) for n, amount in counts.items() if amount == 0],
        "layoutSeedDistinctCount": len({p["layoutSeed"] for p in plans}),
        "routingSeedDistinctCount": len({p["routingSeed"] for p in plans}),
    }


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest().upper()


def build(readme, plan_source, streams_source):
    rows = preserved_rows(readme)
    original = list(rows)
    original_seeds = {row["seed"] for row in original}
    used = set(original_seeds)
    initial = {c: coverage(rows, c) for c in ROOT_RANGES}
    count_append_order = append_counts(rows, used)
    after_counts = {c: coverage(rows, c) for c in ROOT_RANGES}
    structure_append_order = append_structure(rows, used)
    final = {c: coverage(rows, c) for c in ROOT_RANGES}
    for cohort, result in final.items():
        if result["missingPackageCounts20To40"] or not result["topologyAxisCoverage"]["complete"] or result["missingSupportFeatures"]:
            fail("final cohort coverage incomplete: %s" % cohort)
    additions = {
        c: [r["seed"] for r in rows if r["cohort"] == c and r["seed"] not in original_seeds]
        for c in ROOT_RANGES
    }
    return {
        "schema": "q30-scale-plan4-manifest-proposal@1",
        "status": "PROPOSAL_NOT_QUALIFICATION",
        "recipe": {
            "familyId": FAMILY_ID, "planVersion": PLAN_VERSION,
            "derivationVersion": DERIVATION_VERSION, "deviceIntentVersion": DEVICE_VERSION,
            "sourceFiles": {
                "plan4": {
                    "path": "scale-candidate/src/com/lushprojects/circuitjs1/client/Rb30Plan.java",
                    "sha256": sha256(plan_source),
                },
                "namedRandomStreams": {
                    "path": "scale-candidate/src/com/lushprojects/circuitjs1/client/NamedRandomStreams.java",
                    "sha256": sha256(streams_source),
                },
                "independentAlgorithmBasis": "tests/contracts/task46_seed_reference.py",
            },
            "rules": {
                "channelPopulation": "device/topology/rev2/channel-population: nextInt(2)+1",
                "referenceArrangement": "device/topology/rev2/reference-arrangement: enum index 0..2",
                "drivers": "block/output-a and output-b, topology rev1, semantic driver; nextInt(2)==0 means BJT",
                "supportFeatures": "independent device/support/rev2 feature streams; index 0 means present",
                "supportCeiling": "if requested package count exceeds 40, enumerate 0..255 valid codes; minimum Hamming distance; ascending-code ties selected by support-ceiling-tie",
                "packageCount": "8 + 10*channels + reference parts + support feature weights; maximum 40",
                "fault": "device/fault/rev1/serviceable-region selects from channel-specific five-fault list",
                "layoutSeed": "device/placement/rev1/board",
                "routingSeed": "device/routing/rev1/copper",
                "physicalPolicy": "MEDIUM_BOARD@1",
            },
        },
        "preservation": {
            "sourceManifest": "docs/task-evidence/Q30/normal-admission/README.md",
            "legacyPlanEpoch": 3, "legacyMatrixRows": 48,
            "legacyRepresentativeRows": 24, "legacyHeldOutRows": 24,
            "signedLongReplayRows": 3, "preservedSeedCount": len(original),
            "originalSeedOrderPreserved": True, "originalCohortsPreserved": True,
            "legacyRowsRetainedAsHistory": True,
        },
        "appendPolicy": {
            "usesOnlyPlan4Structure": True,
            "forbiddenInputs": [
                "routing outcomes", "route timing", "solver results", "proof results",
                "normal-admission outcomes", "compiled receipts",
            ],
            "rootRanges": {
                c: {"startInclusive": start, "stopExclusive": stop, "ascending": True}
                for c, (start, stop) in ROOT_RANGES.items()
            },
            "packageCountPass": "Per cohort, ascending scan appends the first unused root filling each missing package count 20..40.",
            "structuralPass": "After count completion, rescan ascending from the cohort start, skipping used roots, and append the first root filling any missing topology-axis or eligible support state.",
            "structuralRequirements": {
                "topologyAxesPerCohort": REQUIRED_AXES,
                "supportFlagsBothStatesPerCohort": list(ALWAYS_FEATURES),
                "channelBFlagsBothStatesAmongTwoChannelRows": list(CHANNEL_B_FEATURES),
                "selectedFaultValues": "reported only; not an append criterion",
            },
            "maximumRootsScannedPerPass": SCAN_LIMIT,
        },
        "summary": {
            "manifestSeedCount": len(rows), "preservedSeedCount": len(original),
            "appendedCount": len(rows) - len(original),
            "appendedByCohort": additions,
            "packageCountAppendOrder": count_append_order,
            "structuralAppendOrder": structure_append_order,
            "initialCoverage": initial,
            "afterPackageCountCoverage": after_counts,
            "finalCoverage": final,
        },
        "seeds": rows,
    }


def markdown(manifest):
    summary = manifest["summary"]
    out = [
        "# Q30 scale plan-4 manifest proposal",
        "",
        "**Proposal only; not qualification evidence.** Seed selection uses no routing,",
        "timing, solver, proof, normal-admission, or compiled-receipt results.",
        "",
        "## Frozen input and source recipe",
        "",
        "All 51 seeds from the normal-admission README are retained: 24 Representative",
        "matrix rows, 24 Held-out matrix rows, and three exact signed-long replay roots.",
        "Each matrix row keeps its original epoch-3 cohort/support/topology metadata and",
        "adds a separate plan-version-4 expected identity. Historical epoch-3 evidence",
        "is not relabeled or replaced. Plan-4 grammar comes from the scale-candidate",
        "Rb30Plan.java; named-stream arithmetic mirrors the independent Task 46 Python",
        "reference. Source hashes and every plan identity are in the JSON.",
        "",
        "## Append rule",
        "",
        "Scan Representative roots upward from 10000 and stop before 20000; scan",
        "Held-out roots upward from 20000. First append the first unused root filling",
        "each missing package count in 20..40. Then rescan each root range from its",
        "start, skipping used roots, and append the first seed filling a missing valid",
        "topology axis or missing present/absent state of an eligible support feature.",
        "Topology requires all valid one/two-channel, reference, and active driver axes.",
        "Channel-B features are checked only among two-channel plans. Fault choices are",
        "reported but do not drive extension.",
        "",
        "| Cohort | Rows | Appended | Package counts | Topology axes | Missing support states |",
        "| --- | ---: | ---: | --- | ---: | --- |",
    ]
    for cohort in ROOT_RANGES:
        value = summary["finalCoverage"][cohort]
        out.append(
            "| %s | %d | %d | all 21 (20–40) | %d/%d | %s |"
            % (cohort, value["rowCount"], len(summary["appendedByCohort"][cohort]),
               value["topologyAxisCoverage"]["presentAxisCount"],
               value["topologyAxisCoverage"]["requiredAxisCount"],
               ", ".join(value["missingSupportFeatures"]) or "none")
        )
    out.extend([
        "",
        "The JSON records exact per-count totals, topology axes, support present/absent",
        "coverage, selected fault values, signed-long layout/routing seeds, all 51",
        "preserved plan-4 identities, and each appended root with its rule/reason.",
        "Total proposed rows: %d (preserved 51, appended %d; Representative %d, "
        "Held-out %d)." % (
            summary["manifestSeedCount"], summary["appendedCount"],
            len(summary["appendedByCohort"]["Representative"]),
            len(summary["appendedByCohort"]["Held-out"]),
        ),
        "",
        "## Limits",
        "",
        "This oracle predicts immutable plan structure only. It does not construct a",
        "board or establish placement, routing, solver proof, normal admission, or",
        "timing. Differential comparison with Java plan-4 outputs and all qualification",
        "gates remain required before adoption.",
        "",
    ])
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--readme", required=True)
    parser.add_argument("--plan-source", required=True)
    parser.add_argument("--streams-source", required=True)
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()
    destination = Path(args.out_dir)
    destination.mkdir(parents=True, exist_ok=True)
    proposal = build(args.readme, args.plan_source, args.streams_source)
    json_path = destination / "manifest-proposal.json"
    md_path = destination / "manifest-proposal.md"
    json_path.write_text(json.dumps(proposal, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    md_path.write_text(markdown(proposal), encoding="utf-8")
    summary = proposal["summary"]
    print("PROPOSAL_NOT_QUALIFICATION preserved=%d appended=%d total=%d" % (
        summary["preservedSeedCount"], summary["appendedCount"], summary["manifestSeedCount"]
    ))
    for cohort in ROOT_RANGES:
        value = summary["finalCoverage"][cohort]
        print("%s rows=%d axes=%d/%d missingCounts=%s missingSupport=%s faults=%s roots=%s" % (
            cohort, value["rowCount"], value["topologyAxisCoverage"]["presentAxisCount"],
            value["topologyAxisCoverage"]["requiredAxisCount"],
            value["missingPackageCounts20To40"], value["missingSupportFeatures"],
            value["selectedFaultValues"], summary["appendedByCohort"][cohort]
        ))
    print("wrote %s and %s" % (json_path, md_path))


if __name__ == "__main__":
    try:
        main()
    except (ProposalError, OSError, ValueError) as error:
        raise SystemExit("FAIL: scale-plan reference: %s" % error)