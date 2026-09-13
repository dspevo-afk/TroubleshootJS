"""Strict reader for compiled alpha evidence; this does not certify human trials."""
import argparse
import copy
import json
import re
from pathlib import Path

COHORT = {
    "LED_INDICATOR": [0, 2, 3, 4],
    "DIODE_PROTECTED_INDICATOR": [0, 2, 3],
    "PARALLEL_DUAL_INDICATOR": [0, 2, 3],
    "RC_DELAY": [0, 2, 3],
    "NPN_LOW_SIDE_SWITCH": [0, 1, 2],
    "NMOS_LOW_SIDE_SWITCH": [0, 1, 2],
    "RELAY_OUTPUT": [0, 1, 2, 3, 4, 5],
    "RB15_CONTROL": [0, 1, 2, 3, 17, 42, 101, -1, 9007199254740993,
                     -9223372036854775808, 9223372036854775807],
    "COMPOSED_CONTROLLED_INDICATOR": [0, 3],
}
EXPECTED = {(family, str(seed)) for family, seeds in COHORT.items() for seed in seeds}
TERMINALS = {"resistor": 2, "diode": 2, "led": 2, "capacitor": 2, "npn": 3, "nmos": 3, "relay": 5}


def mutation_matrix():
    expected = set()
    for provider, terminals in TERMINALS.items():
        expected.add((provider, "acquire", "OWNER_CHANGE", 1, "REJECTED"))
        stages = {
            "acquire": ["INVENTORY_ACQUIRE", "CANONICAL_REGISTER", "GRAPH_APPEND", "COMMIT"],
            "remove": ["GRAPH_DISCONNECT", "SLOT_CLEAR", "EMPTY_SLOT_REBIND", "COMMIT"],
            "catalog": ["INVENTORY_ACQUIRE", "CANONICAL_REGISTER", "GRAPH_APPEND"],
            "install": [],
        }
        common = ["PRIMARY_BINDING", "ENDPOINT_RETARGET", "ATTACHMENT", "SLOT_MOUNT", "GRAPH_CONNECT", "GRAPH_RESTORE", "COMMIT"]
        stages["catalog"] += common
        stages["install"] += common
        for operation, checkpoints in stages.items():
            for stage in checkpoints:
                count = terminals if stage in ["ENDPOINT_RETARGET", "GRAPH_CONNECT"] else 1
                for occurrence in range(1, count + 1):
                    expected.add((provider, operation, "AFTER_" + stage, occurrence, "COMPENSATED"))
    return expected


EXPECTED_MUTATIONS = mutation_matrix()


def validate_cross_targets(groups):
    require(type(groups) is list and len(groups) == 2, "Missing composed cross-target fixtures")
    seeds = set()
    for group in groups:
        require(type(group) is dict and group.get("family") == "COMPOSED_CONTROLLED_INDICATOR" and
                group.get("seed") in {"0", "3"} and group["seed"] not in seeds,
                "Foreign/duplicate cross-target owner")
        seeds.add(group["seed"])
        rows = group.get("cases")
        require(type(rows) is list and len(rows) == group.get("checks") == 26,
                "Incomplete cross-target mutation evidence")
        expected = {("cross-eligibility", stage, 1) for stage in
                    ["OCCUPIED", "WRONG_TYPE", "WRONG_GEOMETRY", "FOREIGN_SAME_ID", "FOREIGN_BOARD", "STALE_PROVIDER", "POWERED", "ORIGINAL_TARGET"]}
        for stage in ["PRIMARY_BINDING", "ENDPOINT_RETARGET", "ATTACHMENT", "SLOT_MOUNT", "GRAPH_CONNECT", "GRAPH_RESTORE", "COMMIT"]:
            for occurrence in range(1, 3 if stage in {"ENDPOINT_RETARGET", "GRAPH_CONNECT"} else 2):
                expected.add(("cross-install", "AFTER_" + stage, occurrence))
        for stage in ["GRAPH_DISCONNECT", "SLOT_CLEAR", "EMPTY_SLOT_REBIND", "COMMIT"]:
            expected.add(("cross-remove", "AFTER_" + stage, 1))
        successes = {("cross-install", "COMMIT", 1): "SOURCE_INVENTORY_RETAINED",
                     ("cross-stress", "ACTUAL_CURRENT", 1): "DAMAGED_SAME_PART",
                     ("cross-remove", "COMMIT", 1): "SOURCE_LOOSE_RETAINED",
                     ("cross-remove-check", "IDENTITY", 1): "SAME_SOURCE_PART",
                     ("cross-reinstall", "COMMIT", 1): "ALTERNATE_TARGET"}
        expected.update(successes)
        found = set()
        for row in rows:
            require(type(row) is dict and row.get("provider") == "resistor-cross" and
                    integer(row.get("occurrence"), 1, 2), "Malformed cross-target case")
            key = tuple(row.get(field) for field in ["operation", "stage", "occurrence"])
            require(key in expected and key not in found, "Missing/duplicate/wrong cross-target case")
            found.add(key)
            if key in successes:
                require(row.get("status") == successes[key], "Unproved cross-target result")
            elif key[0] == "cross-eligibility":
                allowed = {"REJECTED", "NOT_APPLICABLE"} if key[1] in {"WRONG_TYPE", "WRONG_GEOMETRY"} else {"REJECTED"}
                require(row.get("status") in allowed, "Cross-target eligibility boundary accepted")
            else:
                require(row.get("status") == "COMPENSATED", "Cross-target partial write was not compensated")
            if key[0] == "cross-stress":
                for field in ["actualPowerW", "ratedPowerW", "serviceBefore", "serviceAfter", "damageBefore", "damageAfter"]:
                    value = row.get(field)
                    require(type(value) in (int, float) and 0 <= value < float("inf"), "Missing finite cross-mounted stress evidence")
                require(row["actualPowerW"] > row["ratedPowerW"] > 0 and
                        row["serviceAfter"] > row["serviceBefore"] and row["damageAfter"] > row["damageBefore"] and
                        row.get("samePart") is True, "Cross-mounted overload did not damage the same physical part")
        require(found == expected, "Incomplete cross-target coverage")
    require(seeds == {"0", "3"}, "Missing composed cross-target seed")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def integer(value, minimum=0, maximum=2**53 - 1):
    return type(value) is int and minimum <= value <= maximum


def read(path):
    def invalid(value):
        raise ValueError("Non-finite JSON number: " + value)
    return json.loads(Path(path).read_text(encoding="utf-8-sig"), parse_constant=invalid)


def validate(report, negative=False):
    require(type(report) is dict and report.get("protocol") == "TSJ-ALPHA-1", "Protocol")
    require(report.get("pilot") is False, "A pilot cannot qualify a release")
    require(report.get("ownerRestored") is True, "Cleanup did not restore the owner")
    for key in ["assertions", "acquisitions", "staleCallbacks", "negativeChecks", "mutationChecks", "cancellationMs", "elapsedMs", "cleanupMs", "activeCase"]:
        require(integer(report.get(key)), "Missing or invalid counter: " + key)
    require(type(report.get("cases")) is list, "Missing cases")
    if negative:
        require(report.get("status") == "FAIL" and not report["cases"] and report["activeCase"] == 0,
                "Forced failure must not admit a case")
        require("alpha-explicit-failure-canary" in str(report.get("failure")), "Wrong failure")
        return
    require(report.get("status") == "PASS" and report.get("failure") is None, "Terminal PASS missing")
    require(len(report["cases"]) == len(EXPECTED) and report["activeCase"] == len(EXPECTED), "Incomplete corpus")
    require(report.get("operation") == "catalog-and-repair", "Final operation")
    require(report["assertions"] >= 400 and report["acquisitions"] > len(EXPECTED) and
            report["staleCallbacks"] >= len(EXPECTED) and report["negativeChecks"] >= len(EXPECTED), "Missing boundary coverage")
    require(type(report.get("mutationProviders")) is list and len(report["mutationProviders"]) == 7 and
            set(report["mutationProviders"]) == set(TERMINALS), "Missing actual acquisition/installation compensation providers")
    require(type(report.get("mutationCases")) is list and len(report["mutationCases"]) == report["mutationChecks"] == len(EXPECTED_MUTATIONS),
            "Missing actual acquisition/installation compensation cases")
    observed_mutations = set()
    for row in report["mutationCases"]:
        require(type(row) is dict and integer(row.get("occurrence"), 1, 5), "Malformed mutation evidence")
        key = tuple(row.get(field) for field in ["provider", "operation", "stage", "occurrence", "status"])
        require(key in EXPECTED_MUTATIONS and key not in observed_mutations, "Missing/duplicate/wrong mutation outcome")
        observed_mutations.add(key)
    require(observed_mutations == EXPECTED_MUTATIONS, "Incomplete mutation coverage")
    expected_shop = {("NPN_LOW_SIDE_SWITCH", "0", "SPAN_240"),
                     ("NPN_LOW_SIDE_SWITCH", "0", "SPAN_260"),
                     ("COMPOSED_CONTROLLED_INDICATOR", "0", "SPAN_220"),
                     ("COMPOSED_CONTROLLED_INDICATOR", "3", "SPAN_220")}
    require(type(report.get("shopCases")) is list and len(report["shopCases"]) == 4,
            "Missing public Shop specification/physical-fit requests")
    observed_shop = set()
    for row in report["shopCases"]:
        require(type(row) is dict, "Malformed public Shop evidence")
        key = tuple(row.get(field) for field in ["family", "seed", "variant"])
        require(key in expected_shop and key not in observed_shop and row.get("sourcePreserved") is True,
                "Missing/duplicate/wrong public Shop identity or source mutation")
        require(row.get("fitAndTypeRejections") is (key == ("NPN_LOW_SIDE_SWITCH", "0", "SPAN_260")),
                "Missing real loose wrong-geometry/wrong-type rejection at an empty current target")
        reading = row.get("measuredOhms")
        require(type(reading) in (int, float) and abs(reading - 330) < .02,
                "Public Shop request did not create the selected electrical value")
        observed_shop.add(key)
    require(observed_shop == expected_shop, "Incomplete public Shop physical-fit choices")
    validate_cross_targets(report.get("crossTargetCases"))
    found = set()
    for case in report["cases"]:
        require(type(case) is dict, "Malformed case")
        key = (case.get("family"), case.get("seed"))
        require(key in EXPECTED and key not in found, "Foreign, rounded or duplicate seed")
        found.add(key)
        profile = "MEDIUM" if key[0] == "COMPOSED_CONTROLLED_INDICATOR" else "EASY"
        require(case.get("requested") == case.get("computed") == profile, "Unproved profile")
        require(case.get("repairPassed") is True, "Acquisition/repair/retest was not exercised")
        require(integer(case.get("admissionMs"), 1, 90000) and integer(case.get("maxUnitMs"), 0, 5000) and
                integer(case.get("workUnits"), 6, 640), "Admission budget violation")
        features = case.get("features", "").split(";")
        require(features[0] == "difficulty/1" and len(features) == 15, "Feature version/schema")
        fields = dict(piece.split("=", 1) for piece in features[1:])
        require(len(fields) == 14 and fields.get("profile") == profile, "Duplicate/mismatched features")
        for field in ["parts", "hypotheses", "repairs", "owners", "readings", "singleRemaining", "modes", "inputs", "railTargets", "temporal"]:
            require(re.fullmatch(r"0|[1-9][0-9]*", fields.get(field, "")) is not None, "Bad feature: " + field)
        require(1 <= int(fields["parts"]) <= 20 and 1 <= int(fields["hypotheses"]) <= 12, "Unsupported population")
        require(1 <= int(fields["repairs"]) <= int(fields["hypotheses"]) and int(fields["owners"]) >= 1, "Invalid repair classes")
        require(fields.get("parallel") in ["true", "false"] and int(fields["modes"]) > 0, "Missing instrument/path evidence")
        for field in ["executedEvidenceDepth", "legalWitnessActions"]:
            require(re.fullmatch(r"[1-9][0-9]*-[1-9][0-9]*", fields.get(field, "")) is not None, "Missing action witness")
            low, high = map(int, fields[field].split("-"))
            require(low <= high, "Reversed witness bounds")
        if profile == "MEDIUM":
            require(int(fields["readings"]) >= 2 and int(fields["singleRemaining"]) >= 2 and
                    int(fields["owners"]) >= 2 and int(fields["repairs"]) >= 2 and
                    (int(fields["temporal"]) > 0 or fields["parallel"] == "true"), "One-probe or noninteracting MEDIUM")
    require(found == EXPECTED, "Missing release seed")


def malformed_canaries(report):
    mutations = [lambda x: x.update(status="RUNNING"), lambda x: x.update(pilot=True),
                 lambda x: x.update(ownerRestored=False), lambda x: x.update(failure="hidden failure"),
                 lambda x: x["cases"].pop(), lambda x: x["cases"].append(x["cases"][0]),
                 lambda x: x["cases"][0].update(seed=0), lambda x: x["cases"][0].update(seed="01"),
                 lambda x: x["cases"][0].update(seed="-4518705223253195925"),
                 lambda x: x["cases"][0].update(requested="HARD"),
                 lambda x: x["cases"][0].update(repairPassed=False),
                 lambda x: x["cases"][0].update(maxUnitMs=5001),
                 lambda x: x["cases"][0].update(admissionMs=90001),
                 lambda x: x["cases"][0].update(workUnits=641),
                 lambda x: x.update(cleanupMs=True), lambda x: x["cases"][0].update(features="difficulty/1"),
                 lambda x: x.update(mutationChecks=0), lambda x: x["mutationProviders"].pop(),
                 lambda x: x["mutationCases"].pop(), lambda x: x["mutationCases"][0].update(status="ISOLATED"),
                 lambda x: x["shopCases"].pop(), lambda x: x["shopCases"].append(x["shopCases"][0]),
                 lambda x: x["shopCases"][0].update(variant="SPAN_220"),
                 lambda x: x["shopCases"][0].update(sourcePreserved=False),
                 lambda x: x["shopCases"][0].update(measuredOhms=1000),
                 lambda x: x["shopCases"][1].update(fitAndTypeRejections=False),
                 lambda x: x["crossTargetCases"].pop(),
                 lambda x: x["crossTargetCases"][0]["cases"].pop(),
                 lambda x: x["crossTargetCases"][0].update(seed="1"),
                 lambda x: x["crossTargetCases"][0]["cases"][0].update(status="ACCEPTED"),
                 lambda x: next(row for row in x["crossTargetCases"][0]["cases"] if row["operation"] == "cross-stress").update(actualPowerW=0),
                 lambda x: next(row for row in x["crossTargetCases"][0]["cases"] if row["operation"] == "cross-stress").update(damageAfter=0),
                 lambda x: next(row for row in x["crossTargetCases"][0]["cases"] if row["operation"] == "cross-stress").update(samePart=False),
                 lambda x: next(row for row in x["mutationCases"] if row["stage"] == "AFTER_EMPTY_SLOT_REBIND").update(status="ISOLATED"),
                 lambda x: next(row for row in x["crossTargetCases"][0]["cases"] if row["operation"] == "cross-remove" and row["stage"] == "AFTER_EMPTY_SLOT_REBIND").update(status="ISOLATED")]
    for mutation in mutations:
        bad = copy.deepcopy(report)
        mutation(bad)
        try:
            validate(bad)
        except (ValueError, TypeError):
            continue
        raise AssertionError("Malformed evidence accepted")
    return len(mutations)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--negative", required=True)
    args = parser.parse_args()
    accepted = read(args.report)
    validate(accepted)
    validate(read(args.negative), negative=True)
    count = malformed_canaries(accepted)
    print(f"PASS: alpha compiled corpus={len(EXPECTED)} malformed-canaries={count}; human trials are separate")
