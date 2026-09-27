"""Negative controls for the independent compiled Q30 D01 receipt reader."""
import argparse
import copy
import json
from pathlib import Path

import check_d01


def fault_evidence(document, fault):
    target = check_d01.key(fault)
    return next(item for item in document["rows"][0]["report"]["cold"]["evidence"]
                if item["hypothesisKey"] == target)


def sample(evidence, sample_id):
    return next(item for item in evidence["samples"] if item["id"] == sample_id)


def replace_context_field(context, key, old, new):
    key_frame = "V{}:{};".format(len(key), key)
    old_frame = "V{}:{};".format(len(old), old)
    target = "F" + key_frame + old_frame
    replacement = "F" + key_frame + "V{}:{};".format(len(new), new)
    if context.count(target) != 1:
        raise AssertionError("could not locate exact context field: " + key)
    return context.replace(target, replacement, 1)


def replace_recipe_parameter(context, key, old, new):
    _, fields = check_d01._framed_fields(context, context=True)
    recipe = fields["temporal.contract.cache-recipe"]
    target = "F{}:{}{}:{}".format(len("parameter." + key), "parameter." + key,
                                  len(old), old)
    replacement = "F{}:{}{}:{}".format(len("parameter." + key), "parameter." + key,
                                       len(new), new)
    if recipe.count(target) != 1:
        raise AssertionError("could not locate exact temporal recipe parameter: " + key)
    return context.replace(recipe, recipe.replace(target, replacement, 1), 1)


def reject_corruption(name, document, seeds):
    try:
        check_d01.check_document(document, seeds)
    except (AssertionError, KeyError, OverflowError, TypeError, ValueError):
        return
    raise AssertionError("corrupted receipt was accepted: " + name)


def run_canaries(receipt_path, seeds):
    with open(receipt_path, encoding="utf-8") as handle:
        source = json.load(handle)
    baseline_pairs = check_d01.check_document(source, seeds)
    expected_pairs = len(seeds) * (len(check_d01.FAULTS) * (len(check_d01.FAULTS) - 1) // 2)
    if baseline_pairs != expected_pairs:
        raise AssertionError("baseline pairwise census is incomplete")

    catalog_corruption = copy.deepcopy(source)
    catalog_report = catalog_corruption["rows"][0]["report"]
    catalog_report["registered"] = not check_d01.expected_catalog_registered(
        catalog_report["schema"])
    reject_corruption("catalog claim contradicts metadata schema",
                      catalog_corruption, seeds)

    canaries = []

    corrupted = copy.deepcopy(source)
    evidence = fault_evidence(corrupted, "RELAY_B_COIL_OPEN")
    wrong_output = sample(evidence, "SENSORS_HIGH_OUTPUT_A")
    wrong_output["value"] = 0.0
    wrong_output["tolerance"] = 0.01
    canaries.append(("wrong-output-value", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    sample(fault_evidence(corrupted, "DREV_OPEN"), "SENSORS_LOW_DREV_K")["tolerance"] += 1.0
    canaries.append(("inflated-tolerance", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    fault_evidence(corrupted, "SENSOR_A_OPEN")["samples"].pop()
    canaries.append(("missing-sample", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    corrupted["rows"][0]["report"]["cold"]["evidence"].pop()
    canaries.append(("missing-hypothesis", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    evidence = corrupted["rows"][0]["report"]["cold"]["evidence"]
    evidence[1]["hypothesisKey"] = evidence[0]["hypothesisKey"]
    evidence[1]["routeId"] = evidence[0]["routeId"]
    canaries.append(("duplicate-hypothesis", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    fault_evidence(corrupted, "DREV_OPEN")["routeId"] = "RB30_CONTROL/RESISTOR_OPEN/DREV"
    canaries.append(("wrong-hypothesis-route", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    corrupted["rows"][0]["report"]["programIdentity"] = corrupted["rows"][0]["report"][
        "programIdentity"].replace(
            "21:SENSORS_HIGH_OUTPUT_A5:JOA.15:JOA.2",
            "21:SENSORS_HIGH_OUTPUT_A5:JOA.25:JOA.2")
    canaries.append(("wrong-output-probe-binding", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    overflow = "9223372036854775808"
    row = corrupted["rows"][0]
    row["seed"] = overflow
    row["report"]["seed"] = overflow
    row["report"]["requestedSeed"] = overflow
    row["report"]["realizationManifest"] = row["report"]["realizationManifest"].replace(
        ";seed=0;", ";seed=" + overflow + ";")
    canaries.append(("signed-long-overflow", corrupted, [overflow]))

    corrupted = copy.deepcopy(source)
    corrupted["rows"][0]["report"]["requestedSeed"] = "9007199254740993"
    canaries.append(("mismatched-requested-long", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    second = copy.deepcopy(corrupted["rows"][0])
    second["seed"] = "37"
    second["previewSourceDigest"] = "f" * 64
    second["report"]["seed"] = "37"
    second["report"]["requestedSeed"] = "37"
    second["report"]["realizationManifest"] = second["report"]["realizationManifest"].replace(
        ";seed=0;", ";seed=37;")
    corrupted["rows"].append(second)
    canaries.append(("mixed-compiled-build", corrupted, ["0", "37"]))

    corrupted = copy.deepcopy(source)
    row = corrupted["rows"][0]["report"]
    row["proofUnits"] = row["cold"]["workUnits"] = 414
    canaries.append(("wrong-work-unit-census", corrupted, seeds))

    # Historical schema-v1 receipts predate trusted context/work-unit fields.
    # Keep their cold baseline readable, while exercising the newer contract
    # canaries only when the actual receipt declares v2 metadata.
    if "contextCanonical" in source["rows"][0]["report"]:
        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "temporal.contract.version", "2", "3")
        canaries.append(("unrecognized-temporal-contract-version", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "epoch.circuit-dump", check_d01.MODEL_DUMP_EPOCH,
            "circuitjs-source-load-model-inputs-no-transient-dump-v5")
        canaries.append(("stale-model-dump-epoch", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        del corrupted["rows"][0]["report"]["contextCanonical"]
        canaries.append(("missing-trusted-temporal-context", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        row["contextCanonical"] = replace_recipe_parameter(
            row["contextCanonical"], "profile-work-units", "5", "4")
        canaries.append(("wrong-profile-work-units", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        row["contextCanonical"] = replace_recipe_parameter(
            row["contextCanonical"], "customer-retest-work-units", "5", "6")
        canaries.append(("wrong-customer-retest-work-units", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["explicitCompletion"] = True
        canaries.append(("wrong-direct-completion-boundary", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    sample(fault_evidence(corrupted, "SENSOR_A_OPEN"), "SENSORS_LOW_RAIL5")["value"] = 0.0
    sample(fault_evidence(corrupted, "SENSOR_A_OPEN"), "SENSORS_LOW_RAIL5")["tolerance"] = 0.01
    canaries.append(("wrong-regulated-rail", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    row = corrupted["rows"][0]["report"]
    row["serviceElapsedMs"] = 0
    row["cold"]["elapsedMillis"] = 0
    canaries.append(("service-not-run", corrupted, seeds))

    if "contextCanonical" in source["rows"][0]["report"]:
        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        old_step = "5:POWER23:BOARD_POWER_OFF_SERVICE-1:-1:9:UNPOWERED1:0"
        new_step = "5:POWER23:BOARD_POWER_OFF_SERVICE-1:-1:7:POWERED1:0"
        if row["programIdentity"].count(old_step) != 1:
            raise AssertionError("could not locate the unique board service power-off step")
        row["programIdentity"] = row["programIdentity"].replace(old_step, new_step, 1)
        canaries.append(("service-power-off-not-proven", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "diagnostic.service-preparation.policy",
            check_d01.SERVICE_PREPARATION_POLICY,
            "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=always-available")
        canaries.append(("wrong-readiness-policy-same-unit-count", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        manifest_fields = check_d01._parse_plan_manifest(
            row["realizationManifest"], 3, check_d01.CURRENT_PLAN_FIELDS)
        current_count = manifest_fields["packages"]
        wrong_count = "35" if current_count != "35" else "33"
        old_manifest = row["realizationManifest"]
        wrong_manifest = old_manifest.replace(
            ";packages=" + current_count + ";", ";packages=" + wrong_count + ";", 1)
        if wrong_manifest == old_manifest:
            raise AssertionError("could not alter the unique plan package count")
        row["realizationManifest"] = wrong_manifest
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "realization.manifest", old_manifest, wrong_manifest)
        canaries.append(("support-package-count-mismatch", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        _, context_fields = check_d01._framed_fields(row["contextCanonical"], context=True)
        old_admission = context_fields["physical.admission.canonical"]
        new_admission = old_admission.replace("RPIN_A", "RPIN_C")
        if len(new_admission) != len(old_admission) or new_admission == old_admission:
            raise AssertionError("could not alter the physical RPIN_A declaration")
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "physical.admission.canonical",
            old_admission, new_admission)
        canaries.append(("missing-physical-raw-pull-down", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["providerId"] = "rb30-control-diagnostic@2"
        canaries.append(("wrong-provider-version", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        del corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        canaries.append(("missing-service-preparation-canaries", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        prep = corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        prep["policyCanonical"] = "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=always-available"
        canaries.append(("wrong-service-preparation-policy", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["servicePreparationCanaries"]["providerId"] = "rb30-control-diagnostic@2"
        canaries.append(("wrong-service-preparation-provider", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["servicePreparationCanaries"]["status"] = "FAIL"
        canaries.append(("service-preparation-not-passed", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["servicePreparationCanaries"]["passedCount"] = 6
        canaries.append(("service-preparation-case-census-short", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["servicePreparationCanaries"]["serviceCursorWorkUnitsCompleted"] = 9
        canaries.append(("service-preparation-work-unit-census-short", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        del service_cases["staleSolverRecipe"]
        canaries.append(("missing-stale-recipe-case", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["extraStaleOwner"] = copy.deepcopy(service_cases["staleOwner"])
        canaries.append(("duplicate-service-preparation-case", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["relayDelayed"]["availableBefore"] = True
        canaries.append(("relay-readiness-was-already-available", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["relayDelayed"]["targetId"] = "RDA_ORIGINAL"
        canaries.append(("relay-target-identity-mismatch", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["relayDelayed"]["coilCurrentAfterPowerOffSettleAmps"] = 0.0
        canaries.append(("relay-not-energized-before-service", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["relayDelayed"]["solverAdvances"] = 0
        canaries.append(("relay-readiness-did-not-advance", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["relayDelayed"]["advancedSeconds"] = 0.3
        canaries.append(("relay-advance-exceeds-bound", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["resistorImmediate"]["solverAdvances"] = 1
        canaries.append(("immediate-readiness-advanced-solver", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["staleSourceCommand"]["rejectedBeforeAdvance"] = False
        canaries.append(("stale-source-accepted-before-advance", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["stalePower"]["setupAdvancedSeconds"] = -0.001
        canaries.append(("negative-stale-guard-setup-advance", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["stalePower"]["controlRevisionAdvanced"] = False
        canaries.append(("stale-power-revision-not-observed", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        service_cases = corrupted["rows"][0]["report"]["servicePreparationCanaries"]["cases"]
        service_cases["cancellation"]["powerStateAfter"] = "POWERED"
        canaries.append(("cancelled-cursor-repowered-board", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        prep = corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        prep["cleanup"]["exactGraphContentsRestored"] = False
        canaries.append(("service-canary-graph-cleanup-failed", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        prep = corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        prep["cleanup"]["cursorRetainedCount"] = 1
        canaries.append(("service-canary-retained-cursor", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        prep = corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        prep["cleanup"]["contextCapturedAfterCanaries"] = False
        canaries.append(("service-canary-context-captured-too-early", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        prep = corrupted["rows"][0]["report"]["servicePreparationCanaries"]
        prep["cleanup"]["generatedRuntimeSettled"] = False
        canaries.append(("service-canary-runtime-not-settled", corrupted, seeds))
    else:
        corrupted = copy.deepcopy(source)
        corrupted["rows"][0]["report"]["programIdentity"] = corrupted["rows"][0]["report"][
            "programIdentity"].replace("16:3fa999999999999a", "16:3fa0000000000000", 1)
        canaries.append(("missing-service-discharge-interval", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    fault_evidence(corrupted, "REN_OPEN")["executedRepairActionIds"] = []
    canaries.append(("physical-replacement-absent", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    row = corrupted["rows"][0]["report"]
    for audit in (row["cleanupAudit"], row["cold"]["cleanupAudit"]):
        audit["lastGraphDetached"] = False
    canaries.append(("private-graph-not-detached", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    corrupted["rows"][0]["report"]["cleanupAudit"]["lastGraphDetached"] = False
    canaries.append(("cleanup-audit-copy-mismatch", corrupted, seeds))

    for name, corrupted, case_seeds in canaries:
        reject_corruption(name, corrupted, case_seeds)
    return baseline_pairs, len(canaries), True


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt", nargs="?", default=str(
        Path(__file__).with_name("compiled-d01-canary-01.json")))
    parser.add_argument("--seeds", nargs="+", default=["0"])
    args = parser.parse_args()
    pairs, count, catalog_flip = run_canaries(args.receipt, args.seeds)
    print(f"PASS: baseline pairs={pairs}; corrupted-receipt rejection canaries={count}; schema-bound catalog claim flip={'PASS' if catalog_flip else 'FAIL'}")
