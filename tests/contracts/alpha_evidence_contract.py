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
    "SENSOR_CONTROL": [0, 1, 2],
    "RB15_CONTROL": [0, 1, 2, 3, 17, 42, 101, -1, 9007199254740993,
                     -9223372036854775808, 9223372036854775807],
    "COMPOSED_CONTROLLED_INDICATOR": [0, 3],
}
LEGACY_COHORT = {
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
COHORT_BY_PROTOCOL = {
    "TSJ-ALPHA-1": LEGACY_COHORT,
    "TSJ-ALPHA-2": COHORT,
}
EXPECTED_BY_PROTOCOL = {
    protocol: {(family, str(seed)) for family, seeds in cohort.items() for seed in seeds}
    for protocol, cohort in COHORT_BY_PROTOCOL.items()
}
EXPECTED = EXPECTED_BY_PROTOCOL["TSJ-ALPHA-2"]
LEGACY_TERMINALS = {"resistor": 2, "diode": 2, "led": 2, "capacitor": 2, "npn": 3, "nmos": 3, "relay": 5,
                    "connector": 2, "fuse": 2}
# These are the production package terminal counts: the E04 decision-control
# package has five posts and the TO-220 regulator package has four.
CURRENT_TERMINALS = dict(LEGACY_TERMINALS,
                         **{"e04-decision-control": 5, "regulator": 4})
TERMINALS_BY_PROTOCOL = {
    "TSJ-ALPHA-1": LEGACY_TERMINALS,
    "TSJ-ALPHA-2": CURRENT_TERMINALS,
}
# The non-sensor service contract remains the historical provider allowlist.
TERMINALS = LEGACY_TERMINALS
SENSOR_CONTROL_SERVICE_TYPES = {
    "U2": "e04-decision-control",
    "RBIAS": "resistor",
    "RREF": "resistor",
    "RFB": "resistor",
    "RREF_LOW": "resistor",
    "RFB_HYST": "resistor",
    "J1": "connector",
    "J2": "connector",
    "J3": "connector",
    "U1": "regulator",
}
SENSOR_CONTROL_COMPONENTS = frozenset(SENSOR_CONTROL_SERVICE_TYPES)
SENSOR_CONTROL_DIRECT_COMPONENTS = SENSOR_CONTROL_COMPONENTS - {"RFB_HYST"}
SENSOR_CONTROL_COMPONENTS_BY_SEED = {
    "0": SENSOR_CONTROL_DIRECT_COMPONENTS,
    "1": SENSOR_CONTROL_COMPONENTS,
    "2": SENSOR_CONTROL_DIRECT_COMPONENTS,
}
SERVICE_POSITIONS = dict(zip(LEGACY_COHORT, [3, 4, 5, 6, 7, 6, 9, 16, 15]))
FEATURE_SCHEMA_BY_PROTOCOL = {
    "TSJ-ALPHA-1": "difficulty/1",
    "TSJ-ALPHA-2": "difficulty/2",
}
SERVICE_POSITIONS_BY_PROTOCOL = {}
for protocol, cohort in COHORT_BY_PROTOCOL.items():
    positions = {
        (family, str(seed)): SERVICE_POSITIONS[family]
        for family, seeds in cohort.items()
        if family != "SENSOR_CONTROL"
        for seed in seeds
    }
    if protocol == "TSJ-ALPHA-2":
        # The deterministic sensor variant adds one serviceable hysteresis resistor
        # for odd seeds; the release corpus contains direct seeds 0 and 2 and
        # hysteretic seed 1.
        positions.update({
            ("SENSOR_CONTROL", "0"): 9,
            ("SENSOR_CONTROL", "1"): 10,
            ("SENSOR_CONTROL", "2"): 9,
        })
    SERVICE_POSITIONS_BY_PROTOCOL[protocol] = positions


def mutation_matrix(terminals_by_provider):
    expected = set()
    for provider, terminals in terminals_by_provider.items():
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
                count = terminals if stage in ["ENDPOINT_RETARGET", "GRAPH_CONNECT", "GRAPH_DISCONNECT"] else 1
                if provider == "connector" and stage in ["GRAPH_CONNECT", "GRAPH_DISCONNECT"]:
                    count += 2  # Two independently owned external cable contacts.
                for occurrence in range(1, count + 1):
                    expected.add((provider, operation, "AFTER_" + stage, occurrence, "COMPENSATED"))
    return expected


EXPECTED_MUTATIONS_BY_PROTOCOL = {
    protocol: mutation_matrix(terminals)
    for protocol, terminals in TERMINALS_BY_PROTOCOL.items()
}
EXPECTED_MUTATIONS = EXPECTED_MUTATIONS_BY_PROTOCOL["TSJ-ALPHA-2"]
SHOP_GEOMETRY_SOURCES = {
    ("NPN_LOW_SIDE_SWITCH", "0"),
    ("COMPOSED_CONTROLLED_INDICATOR", "0"),
    ("COMPOSED_CONTROLLED_INDICATOR", "3"),
}
AXIAL_RESISTOR_PAD_SPACING = {
    # PhysicalPackages.axialResistorVariant places pads at x=30 and x=span-30.
    "SPAN_220": 160,
    "SPAN_240": 180,
    "SPAN_260": 200,
}


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


def feature_schema_for_protocol(protocol):
    require(type(protocol) is str and protocol in FEATURE_SCHEMA_BY_PROTOCOL,
            "Unknown alpha report protocol")
    return FEATURE_SCHEMA_BY_PROTOCOL[protocol]


def integer(value, minimum=0, maximum=2**53 - 1):
    return type(value) is int and minimum <= value <= maximum


def validate_shop_slot_geometry(component, geometry):
    require(type(component) is str and component and type(geometry) is dict and
            set(geometry) == {"variant", "pads"},
            "Malformed Shop source slot geometry")
    variant = geometry.get("variant")
    require(variant in AXIAL_RESISTOR_PAD_SPACING,
            "Unknown Shop source resistor geometry variant")
    pads = geometry.get("pads")
    require(type(pads) is list and len(pads) == 2,
            "Shop source resistor must expose exactly two physical pads")
    for index, terminal in enumerate(("1", "2")):
        pad = pads[index]
        require(type(pad) is dict and set(pad) == {"id", "terminal", "x", "y"} and
                type(pad.get("id")) is str and pad["id"] and
                pad.get("terminal") == terminal and
                integer(pad.get("x")) and integer(pad.get("y")),
                "Malformed Shop source resistor pad identity or coordinate")
    require(pads[0]["id"] != pads[1]["id"],
            "Shop source resistor pads must have distinct identities")
    dx = abs(pads[1]["x"] - pads[0]["x"])
    dy = abs(pads[1]["y"] - pads[0]["y"])
    require((dx == 0) != (dy == 0) and
            dx + dy == AXIAL_RESISTOR_PAD_SPACING[variant],
            "Shop source resistor span disagrees with canonical package pads")
    return variant


def read(path):
    def invalid(value):
        raise ValueError("Non-finite JSON number: " + value)
    return json.loads(Path(path).read_text(encoding="utf-8-sig"), parse_constant=invalid)


def validate(report, negative=False):
    require(type(report) is dict, "Report")
    protocol = report.get("protocol")
    feature_schema = feature_schema_for_protocol(protocol)
    expected = EXPECTED_BY_PROTOCOL[protocol]
    service_positions = SERVICE_POSITIONS_BY_PROTOCOL[protocol]
    mutation_terminals = TERMINALS_BY_PROTOCOL[protocol]
    expected_mutations = EXPECTED_MUTATIONS_BY_PROTOCOL[protocol]
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
    require(len(report["cases"]) == len(expected) and report["activeCase"] == len(expected), "Incomplete corpus")
    require(report.get("operation") == "catalog-and-repair", "Final operation")
    require(report["assertions"] >= 400 and report["acquisitions"] > len(expected) and
            report["staleCallbacks"] >= len(expected) and report["negativeChecks"] >= len(expected), "Missing boundary coverage")
    require(type(report.get("mutationProviders")) is list and len(report["mutationProviders"]) == len(mutation_terminals) and
            set(report["mutationProviders"]) == set(mutation_terminals), "Missing actual acquisition/installation compensation providers")
    require(type(report.get("mutationCases")) is list and len(report["mutationCases"]) == report["mutationChecks"] == len(expected_mutations),
            "Missing actual acquisition/installation compensation cases")
    observed_mutations = set()
    for row in report["mutationCases"]:
        require(type(row) is dict and integer(row.get("occurrence"), 1, 5), "Malformed mutation evidence")
        key = tuple(row.get(field) for field in ["provider", "operation", "stage", "occurrence", "status"])
        require(key in expected_mutations and key not in observed_mutations, "Missing/duplicate/wrong mutation outcome")
        observed_mutations.add(key)
    require(observed_mutations == expected_mutations, "Incomplete mutation coverage")
    validate_cross_targets(report.get("crossTargetCases"))
    require(type(report.get("serviceCases")) is list, "Missing all-position physical service evidence")
    service = {key: set() for key in expected}
    shop_source_variants = {key: set() for key in SHOP_GEOMETRY_SOURCES}
    for row in report["serviceCases"]:
        require(type(row) is dict, "Malformed service evidence")
        key = (row.get("family"), row.get("seed"))
        component = row.get("component")
        require(key in service and type(component) is str and component and component not in service[key],
                "Foreign/duplicate physical service position")
        if key[0] == "SENSOR_CONTROL":
            require(protocol == "TSJ-ALPHA-2" and
                    component in SENSOR_CONTROL_SERVICE_TYPES and
                    row.get("type") == SENSOR_CONTROL_SERVICE_TYPES[component],
                    "Unknown Sensor Control service component/type")
        else:
            require(row.get("type") in TERMINALS,
                    "Physical service type is outside the release-provider allowlist")
        if protocol == "TSJ-ALPHA-2" and key in SHOP_GEOMETRY_SOURCES:
            if key[0] == "NPN_LOW_SIDE_SWITCH":
                expected_type = {
                    "RLOAD": "resistor", "RB": "resistor", "RPD": "resistor",
                    "Q1": "npn", "J1": "connector", "J2": "connector",
                    "LED1": "led",
                }.get(component)
                require(expected_type is not None and row.get("type") == expected_type,
                        "Current NPN Shop source has an unknown/mistyped physical service position")
            else:
                require("/component/" in component,
                        "Current composed Shop source lacks a namespaced component identity")
                local_component = component.rsplit("/component/", 1)[1]
                if local_component.startswith("R"):
                    require(row.get("type") == "resistor",
                            "Current composed resistor position has the wrong service type")
                elif local_component == "LED1":
                    require(row.get("type") == "led",
                            "Current composed LED position has the wrong service type")
                elif local_component == "Q1":
                    require(row.get("type") in {"npn", "nmos"},
                            "Current composed transistor position has the wrong service type")
                elif local_component.startswith("J"):
                    require(row.get("type") == "connector",
                            "Current composed connector position has the wrong service type")
                else:
                    raise ValueError("Unknown current composed Shop source component")
            if row.get("type") == "resistor":
                variant = validate_shop_slot_geometry(component, row.get("slotGeometry"))
                shop_source_variants[key].add(variant)
            else:
                require("slotGeometry" not in row,
                        "Unexpected resistor geometry on a non-resistor service position")
        require(row.get("removeReplaceReinstall") is True,
                "Physical position was not removed, replaced and reinstalled")
        service[key].add(component)
    for (family, seed), positions in service.items():
        require(len(positions) == service_positions[(family, seed)], "Missing service positions: " + family + "/" + seed)
        if family == "SENSOR_CONTROL":
            require(positions == SENSOR_CONTROL_COMPONENTS_BY_SEED[seed],
                    "Wrong Sensor Control service positions: " + seed)
    if protocol == "TSJ-ALPHA-1":
        expected_shop = {("NPN_LOW_SIDE_SWITCH", "0", "SPAN_240"),
                         ("NPN_LOW_SIDE_SWITCH", "0", "SPAN_260"),
                         ("COMPOSED_CONTROLLED_INDICATOR", "0", "SPAN_220"),
                         ("COMPOSED_CONTROLLED_INDICATOR", "3", "SPAN_220")}
        require(type(report.get("shopCases")) is list and len(report["shopCases"]) == 4,
                "Missing historical public Shop specification/physical-fit requests")
    else:
        expected_shop = set()
        for source in SHOP_GEOMETRY_SOURCES:
            variants = shop_source_variants[source]
            require(len(variants) >= 2,
                    "Current Shop source lacks multiple independently serviceable resistor geometries")
            if source == ("NPN_LOW_SIDE_SWITCH", "0"):
                require("SPAN_260" in variants,
                        "Current NPN Shop wide-fit rejection fixture is missing")
            expected_shop.update((source[0], source[1], variant) for variant in variants)
        require(type(report.get("shopCases")) is list and
                len(report["shopCases"]) == len(expected_shop),
                "Missing current public Shop choices for actual resistor slot geometries")
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
    found = set()
    for case in report["cases"]:
        require(type(case) is dict, "Malformed case")
        key = (case.get("family"), case.get("seed"))
        require(key in expected and key not in found, "Foreign, rounded or duplicate seed")
        found.add(key)
        profile = "MEDIUM" if key[0] == "COMPOSED_CONTROLLED_INDICATOR" else "EASY"
        require(case.get("requested") == case.get("computed") == profile, "Unproved profile")
        require(case.get("repairPassed") is True, "Acquisition/repair/retest was not exercised")
        require(integer(case.get("admissionMs"), 1, 90000) and integer(case.get("maxUnitMs"), 0, 5000) and
                integer(case.get("workUnits"), 6, 640), "Admission budget violation")
        features = case.get("features", "").split(";")
        require(features[0] == feature_schema and len(features) == 15,
                "Feature version/schema does not match alpha protocol")
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
    require(found == expected, "Missing release seed")


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
                 lambda x: x["serviceCases"].pop(),
                 lambda x: x["serviceCases"].append(x["serviceCases"][0]),
                 lambda x: x["serviceCases"][0].update(removeReplaceReinstall=False),
                  lambda x: x["mutationCases"].pop(), lambda x: x["mutationCases"][0].update(status="ISOLATED"),
                  lambda x: x["shopCases"].pop(), lambda x: x["shopCases"].append(x["shopCases"][0]),
                  lambda x: x["shopCases"][0].update(variant="SPAN_UNKNOWN"),
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
    if report.get("protocol") == "TSJ-ALPHA-2":
        def unknown_sensor_component_without_type(bad):
            row = next(row for row in bad["serviceCases"]
                       if row.get("family") == "SENSOR_CONTROL" and row.get("seed") == "0" and
                       row.get("component") == "U2")
            row["component"] = "UNKNOWN"
            row.pop("type", None)

        def missing_sensor_component_type(bad):
            row = next(row for row in bad["serviceCases"]
                       if row.get("family") == "SENSOR_CONTROL" and row.get("seed") == "0" and
                       row.get("component") == "U2")
            row.pop("type")

        def same_count_wrong_sensor_position(bad):
            row = next(row for row in bad["serviceCases"]
                       if row.get("family") == "SENSOR_CONTROL" and row.get("seed") == "0" and
                       row.get("component") == "RFB")
            row["component"] = "RFB_HYST"

        def wrong_current_shop_slot_variant(bad):
            row = next(row for row in bad["serviceCases"]
                       if (row.get("family"), row.get("seed")) in SHOP_GEOMETRY_SOURCES and
                       row.get("type") == "resistor")
            geometry = row["slotGeometry"]
            geometry["variant"] = next(variant for variant in AXIAL_RESISTOR_PAD_SPACING
                                       if variant != geometry["variant"])

        def wrong_current_shop_slot_spacing(bad):
            row = next(row for row in bad["serviceCases"]
                       if (row.get("family"), row.get("seed")) in SHOP_GEOMETRY_SOURCES and
                       row.get("type") == "resistor")
            pads = row["slotGeometry"]["pads"]
            if pads[0]["x"] != pads[1]["x"]:
                pads[1]["x"] += 1
            else:
                pads[1]["y"] += 1

        def duplicate_current_shop_identity(bad):
            first = bad["shopCases"][0]
            duplicate = next(row for row in reversed(bad["shopCases"])
                             if row is not first and
                             (row.get("family"), row.get("seed"), row.get("variant")) !=
                             (first.get("family"), first.get("seed"), first.get("variant")))
            for field in ["family", "seed", "variant"]:
                duplicate[field] = first[field]

        def wrong_current_mutation_provider(bad):
            index = bad["mutationProviders"].index("regulator")
            bad["mutationProviders"][index] = "unknown-provider"

        def wrong_current_mutation_outcome(bad):
            row = next(row for row in bad["mutationCases"]
                       if row.get("provider") == "e04-decision-control" and
                       row.get("operation") == "install" and
                       row.get("stage") == "AFTER_ENDPOINT_RETARGET" and
                       row.get("occurrence") == 5)
            row["status"] = "ISOLATED"

        mutations.extend([
            lambda x: x.update(cases=[case for case in x["cases"]
                                       if not (case.get("family") == "SENSOR_CONTROL" and case.get("seed") == "2")]),
            lambda x: x.update(serviceCases=[row for row in x["serviceCases"]
                                             if not (row.get("family") == "SENSOR_CONTROL" and
                                                     row.get("seed") == "1" and row.get("component") == "RFB_HYST")]),
            unknown_sensor_component_without_type,
            missing_sensor_component_type,
            same_count_wrong_sensor_position,
            lambda x: next(row for row in x["serviceCases"]
                           if (row.get("family"), row.get("seed")) in SHOP_GEOMETRY_SOURCES and
                           row.get("type") == "resistor").pop("slotGeometry"),
            wrong_current_shop_slot_variant,
            wrong_current_shop_slot_spacing,
            duplicate_current_shop_identity,
            wrong_current_mutation_provider,
            wrong_current_mutation_outcome,
        ])
    for mutation in mutations:
        bad = copy.deepcopy(report)
        mutation(bad)
        try:
            validate(bad)
        except (ValueError, TypeError):
            continue
        raise AssertionError("Malformed evidence accepted")
    return len(mutations)


def protocol_feature_canaries(report):
    """Reject known cross-version pairs and unknown report/feature versions."""
    protocol = report["protocol"]
    feature_schema_for_protocol(protocol)
    other_protocol, other_feature = next(
        (candidate, candidate_feature)
        for candidate, candidate_feature in FEATURE_SCHEMA_BY_PROTOCOL.items()
        if candidate != protocol)

    def reject(name, bad):
        try:
            validate(bad)
        except (ValueError, TypeError):
            return
        raise AssertionError("Protocol/feature version corruption accepted: " + name)

    cross_version = copy.deepcopy(report)
    cross_version["protocol"] = other_protocol
    reject("cross-version report protocol", cross_version)

    cross_feature = copy.deepcopy(report)
    first_case_features = cross_feature["cases"][0]["features"].split(";", 1)
    first_case_features[0] = other_feature
    cross_feature["cases"][0]["features"] = ";".join(first_case_features)
    reject("cross-version difficulty feature", cross_feature)

    unknown_protocol = copy.deepcopy(report)
    unknown_protocol["protocol"] = "TSJ-ALPHA-99"
    reject("unknown report protocol", unknown_protocol)

    unknown_feature = copy.deepcopy(report)
    first_case_features = unknown_feature["cases"][0]["features"].split(";", 1)
    first_case_features[0] = "difficulty/99"
    unknown_feature["cases"][0]["features"] = ";".join(first_case_features)
    reject("unknown difficulty feature", unknown_feature)
    return 2, 2


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--negative", required=True)
    args = parser.parse_args()
    accepted = read(args.report)
    validate(accepted)
    validate(read(args.negative), negative=True)
    count = malformed_canaries(accepted)
    cross_version, unknown_versions = protocol_feature_canaries(accepted)
    print(f"PASS: alpha compiled corpus={len(EXPECTED_BY_PROTOCOL[accepted['protocol']])} malformed-canaries={count} protocol-feature-cross-version-canaries={cross_version} unknown-version-canaries={unknown_versions}; human trials are separate")
