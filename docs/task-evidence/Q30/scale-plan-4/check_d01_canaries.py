"""Negative controls for the independent compiled Q30 D01 receipt reader."""
import argparse
import copy
import json
from pathlib import Path

import check_d01


def row_profile(row):
    report = row["report"]
    if "contextCanonical" not in report:
        return check_d01.profile_for_channels(2, plan_version=2)
    _, context = check_d01._framed_fields(report["contextCanonical"], context=True)
    profile = check_d01.validate_current_realization_manifest(
        report["realizationManifest"], row["seed"], report["topology"],
        context.get("physical.admission.canonical"))
    return profile if isinstance(profile, dict) else check_d01.profile_for_channels(
        2, plan_version=3)


def fault_evidence(document, fault):
    row = document["rows"][0]
    target = check_d01.key(fault, row_profile(row)["channels"])
    return next(item for item in row["report"]["cold"]["evidence"]
                if item["hypothesisKey"] == target)


def sample(evidence, sample_id):
    return next(item for item in evidence["samples"] if item["id"] == sample_id)


def frame(value):
    return "{}:{}".format(check_d01._java_string_length(value), value)


def admission_field(name, value):
    return "{}:{}={}:{};".format(
        check_d01._java_string_length(name), name,
        check_d01._java_string_length(value), value)


def rewrite_physical_board(report, transform):
    context = report["contextCanonical"]
    _, fields = check_d01._framed_fields(context, context=True)
    admission = fields["physical.admission.canonical"]
    prefix = "MEDIUM_BOARD_NORMAL@1"
    if not admission.startswith(prefix):
        raise AssertionError("physical admission canonical has unexpected identity")
    records = check_d01._parse_length_records(admission[len(prefix):])
    declarations = [value for name, value in records if name == "board-declaration"]
    if len(declarations) != 1 or not declarations[0]:
        raise AssertionError("could not locate unique physical board declaration")
    board = declarations[0]
    changed = transform(board)
    if changed == board:
        raise AssertionError("physical-board canary did not change its input")
    old_record = admission_field("board-declaration", board)
    new_record = admission_field("board-declaration", changed)
    if admission.count(old_record) != 1:
        raise AssertionError("could not locate exact physical board declaration")
    changed_admission = admission.replace(old_record, new_record, 1)
    report["contextCanonical"] = replace_context_field(
        context, "physical.admission.canonical", admission, changed_admission)


def rewrite_physical_net(board, net_id, transform):
    matches = [(name, value) for name, value in check_d01._parse_length_records(board)
               if name == "net" and value.startswith(net_id + "|")]
    if len(matches) != 1:
        raise AssertionError("could not locate unique physical net: " + net_id)
    old_value = matches[0][1]
    pieces = old_value.split("|", 2)
    if len(pieces) != 3:
        raise AssertionError("physical net record has malformed fields")
    members = transform(pieces[2])
    new_value = pieces[0] + "|" + pieces[1] + "|" + members
    old_record = admission_field("net", old_value)
    new_record = admission_field("net", new_value)
    if board.count(old_record) != 1:
        raise AssertionError("could not locate exact physical net record")
    return board.replace(old_record, new_record, 1)


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


def reject_corruption(name, document, seeds, historical_audit=False):
    try:
        check_d01.check_document(document, seeds,
                                 require_current_plan=not historical_audit)
    except (AssertionError, KeyError, OverflowError, TypeError, ValueError):
        return
    raise AssertionError("corrupted receipt was accepted: " + name)


def run_canaries(receipt_path, seeds, historical_audit=False):
    with open(receipt_path, encoding="utf-8") as handle:
        source = json.load(handle)
    baseline_pairs = check_d01.check_document(
        source, seeds, require_current_plan=not historical_audit)
    expected_pairs = sum(
        len(row_profile(row)["faults"]) * (len(row_profile(row)["faults"]) - 1) // 2
        for row in source["rows"])
    if baseline_pairs != expected_pairs:
        raise AssertionError("baseline pairwise census is incomplete")

    catalog_corruption = copy.deepcopy(source)
    catalog_report = catalog_corruption["rows"][0]["report"]
    catalog_report["registered"] = not check_d01.expected_catalog_registered(
        catalog_report["schema"])
    reject_corruption("catalog claim contradicts metadata schema",
                      catalog_corruption, seeds, historical_audit)

    canaries = []
    power_context_selftests = (
        [] if historical_audit else check_d01.run_v4_power_contract_selftests())

    first_row = source["rows"][0]
    first_report = first_row["report"]
    if not historical_audit and row_profile(first_row).get("planVersion") == 4:
        malformed = copy.deepcopy(source)
        rewrite_physical_board(
            malformed["rows"][0]["report"],
            lambda board: rewrite_physical_net(
                board, "A_CMD", lambda members: members[:-1]))
        canaries.append(("malformed-physical-net-member-frame", malformed, seeds))

        malformed = copy.deepcopy(source)
        rewrite_physical_board(
            malformed["rows"][0]["report"],
            lambda board: board.replace("3:net=", "03:net=", 1))
        canaries.append(("noncanonical-physical-board-field-length", malformed, seeds))

        corrupted = copy.deepcopy(source)
        rewrite_physical_board(
            corrupted["rows"][0]["report"],
            lambda board: rewrite_physical_net(
                board, "A_CMD", lambda members: members + admission_field(
                    "value", check_d01._parse_length_records(members)[0][1])))
        canaries.append(("duplicate-physical-net-endpoint", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        def omit_first_net_member(members):
            parsed = check_d01._parse_length_records(members)
            if not parsed:
                raise AssertionError("A_CMD physical net has no member to remove")
            member_record = admission_field(parsed[0][0], parsed[0][1])
            if members.count(member_record) != 1:
                raise AssertionError("could not locate exact A_CMD member record")
            return members.replace(member_record, "", 1)
        rewrite_physical_board(
            corrupted["rows"][0]["report"],
            lambda board: rewrite_physical_net(board, "A_CMD", omit_first_net_member))
        canaries.append(("missing-physical-net-member-count", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        def move_pad_to_wrong_net(board):
            records = check_d01._parse_length_records(board)
            matches = [(name, value) for name, value in records
                       if name == "pad" and value.startswith("J1.1|")]
            if len(matches) != 1:
                raise AssertionError("could not locate unique J1.1 physical pad")
            old_value = matches[0][1]
            pieces = old_value.split("|")
            new_value = "|".join(pieces[:3] + ["FUSED12"])
            old_record = admission_field("pad", old_value)
            new_record = admission_field("pad", new_value)
            if board.count(old_record) != 1:
                raise AssertionError("could not locate exact J1.1 pad record")
            return board.replace(old_record, new_record, 1)
        rewrite_physical_board(corrupted["rows"][0]["report"], move_pad_to_wrong_net)
        canaries.append(("physical-pad-endpoint-net-mismatch", corrupted, seeds))

    if not historical_audit and "contextCanonical" in first_report:
        first_profile = row_profile(first_row)
        if first_profile.get("planVersion") == 4:
            _, context_fields = check_d01._framed_fields(
                first_report["contextCanonical"], context=True)
            power_contract = check_d01.validate_v4_power_contract(
                context_fields, first_profile)

            def corrupt_power_context(name, transform):
                corrupted = copy.deepcopy(source)
                row = corrupted["rows"][0]["report"]
                context = row["contextCanonical"]
                _, fields = check_d01._framed_fields(context, context=True)
                old = fields["power.contract.canonical"]
                changed = transform(power_contract)
                if changed == old:
                    raise AssertionError("power-contract canary produced no change: " + name)
                row["contextCanonical"] = replace_context_field(
                    context, "power.contract.canonical", old, changed)
                canaries.append((name, corrupted, seeds))

            def stale_none_power_context():
                corrupted = copy.deepcopy(source)
                row = corrupted["rows"][0]["report"]
                context = row["contextCanonical"]
                changes = (
                    ("power.contract.version", "NONE"),
                    ("power.contract.design",
                     check_d01.LEGACY_POWER_DOMAIN_CONTRACT_DESIGN),
                    ("power.contract.canonical", "NONE"),
                )
                for key, value in changes:
                    _, fields = check_d01._framed_fields(context, context=True)
                    context = replace_context_field(context, key, fields[key], value)
                row["contextCanonical"] = context
                canaries.append(("stale-NONE-power-contract", corrupted, seeds))

            stale_none_power_context()
            corrupt_power_context(
                "wrong-load-rail-return-reference",
                lambda contract: contract["canonical"].replace(
                    contract["railRecords"]["OUT_A"],
                    check_d01._expected_v4_power_rail_record(
                        "OUT_A", "CTRL_RETURN", "NONE"), 1))
            corrupt_power_context(
                "FUSED12-storage-requirement-removed",
                lambda contract: contract["canonical"].replace(
                    contract["railRecords"]["FUSED12"],
                    check_d01._expected_v4_power_rail_record(
                        "FUSED12", "CTRL_RETURN", "NONE"), 1))
            corrupt_power_context(
                "required-power-rail-omitted",
                lambda contract: contract["canonical"].replace(
                    contract["railRecords"]["RAIL12"], "", 1))
            corrupt_power_context(
                "active-sensor-power-source-omitted",
                lambda contract: contract["canonical"].replace(
                    contract["sourceRecords"]["SENSOR_A"], "", 1))
            if "SENSOR_B" in power_contract["sourceRecords"]:
                corrupt_power_context(
                    "active-channel-B-power-source-omitted",
                    lambda contract: contract["canonical"].replace(
                        contract["sourceRecords"]["SENSOR_B"], "", 1))

    corrupted = copy.deepcopy(source)
    first_profile = row_profile(corrupted["rows"][0])
    relay_fault = "RELAY_A_COIL_OPEN" if first_profile["channels"] == 1 else "RELAY_B_COIL_OPEN"
    evidence = fault_evidence(corrupted, relay_fault)
    wrong_output = sample(evidence, "SENSORS_HIGH_OUTPUT_A")
    expected_output_a, _ = check_d01.expected_outputs(
        relay_fault, True, False, first_profile["channels"])
    wrong_output["value"] = 0.0 if expected_output_a else 12.0
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
    program = corrupted["rows"][0]["report"]["programIdentity"]
    old_binding = (frame("DC_VOLTAGE") + frame("SENSORS_HIGH_OUTPUT_A") +
                   frame("JOA.1") + frame("JOA.2"))
    wrong_binding = (frame("DC_VOLTAGE") + frame("SENSORS_HIGH_OUTPUT_A") +
                     frame("JOA.2") + frame("JOA.2"))
    if program.count(old_binding) != 1:
        raise AssertionError("could not locate the unique output A probe binding")
    corrupted["rows"][0]["report"]["programIdentity"] = program.replace(
        old_binding, wrong_binding, 1)
    canaries.append(("wrong-output-probe-binding", corrupted, seeds))

    corrupted = copy.deepcopy(source)
    overflow = "9223372036854775808"
    row = corrupted["rows"][0]
    row["seed"] = overflow
    row["report"]["seed"] = overflow
    row["report"]["requestedSeed"] = overflow
    manifest = row["report"]["realizationManifest"]
    old_seed = ";seed=" + row["seed"] + ";"
    row["report"]["realizationManifest"] = manifest.replace(
        old_seed, ";seed=" + overflow + ";", 1)
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
        _, context_fields = check_d01._framed_fields(
            row["contextCanonical"], context=True)
        old_version = context_fields["temporal.contract.version"]
        row["contextCanonical"] = replace_context_field(
            row["contextCanonical"], "temporal.contract.version", old_version,
            str(int(old_version) + 1))
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
        _, context_fields = check_d01._framed_fields(
            row["contextCanonical"], context=True)
        _, recipe_fields = check_d01._framed_fields(
            context_fields["temporal.contract.cache-recipe"])
        old_units = recipe_fields["parameter.profile-work-units"]
        row["contextCanonical"] = replace_recipe_parameter(
            row["contextCanonical"], "profile-work-units", old_units,
            str(int(old_units) + 1))
        canaries.append(("wrong-profile-work-units", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        row = corrupted["rows"][0]["report"]
        _, context_fields = check_d01._framed_fields(
            row["contextCanonical"], context=True)
        _, recipe_fields = check_d01._framed_fields(
            context_fields["temporal.contract.cache-recipe"])
        old_units = recipe_fields["parameter.customer-retest-work-units"]
        row["contextCanonical"] = replace_recipe_parameter(
            row["contextCanonical"], "customer-retest-work-units", old_units,
            str(int(old_units) + 1))
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
        version = 4 if row["realizationManifest"].startswith("rb30-plan@4;") else 3
        plan_schema = check_d01.PLAN_V4_FIELDS if version == 4 \
            else check_d01.CURRENT_PLAN_FIELDS
        manifest_fields = check_d01._parse_plan_manifest(
            row["realizationManifest"], version, plan_schema)
        current_count = manifest_fields["packages"]
        wrong_count = str(int(current_count) + 1)
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

        if row_profile(source["rows"][0])["channels"] == 1:
            corrupted = copy.deepcopy(source)
            row = corrupted["rows"][0]["report"]
            old_topology = row["topology"]
            manifest = row["realizationManifest"]
            manifest_fields = check_d01._parse_plan_manifest(
                manifest, 4, check_d01.PLAN_V4_FIELDS)
            support = manifest_fields["support"]
            support_parts = dict(item.split(":", 1) for item in support.split(","))
            old_filters = int(support_parts["filters"])
            new_filters = old_filters | 2
            support_parts["filters"] = str(new_filters)
            new_support = ",".join("{}:{}".format(name, support_parts[name]) for name in
                                   ("C12", "S5", "S12", "filters", "outputIndicators", "bleeder5"))
            wrong_manifest = manifest.replace(";support=" + support + ";",
                                              ";support=" + new_support + ";", 1)
            new_topology = old_topology.replace(
                "_F{}_O".format(old_filters), "_F{}_O".format(new_filters), 1)
            if wrong_manifest == manifest or new_topology == old_topology:
                raise AssertionError("could not add channel B feature bit to one-channel plan")
            _, context_fields = check_d01._framed_fields(row["contextCanonical"], context=True)
            old_context_topology = context_fields["board.topology"]
            context = replace_context_field(
                row["contextCanonical"], "realization.manifest", manifest, wrong_manifest)
            context = replace_context_field(
                context, "board.topology", old_context_topology, new_topology)
            context = replace_recipe_parameter(
                context, "topology", old_context_topology, new_topology)
            row["realizationManifest"] = wrong_manifest
            row["topology"] = new_topology
            row["contextCanonical"] = context
            canaries.append(("single-channel-plan-enables-absent-B-feature", corrupted, seeds))

            corrupted = copy.deepcopy(source)
            row = corrupted["rows"][0]["report"]
            _, context_fields = check_d01._framed_fields(row["contextCanonical"], context=True)
            old_admission = context_fields["physical.admission.canonical"]
            wrong_admission = old_admission.replace("JSA", "JSB")
            if wrong_admission == old_admission or len(wrong_admission) != len(old_admission):
                raise AssertionError("could not inject an inactive B-channel board part")
            row["contextCanonical"] = replace_context_field(
                row["contextCanonical"], "physical.admission.canonical",
                old_admission, wrong_admission)
            canaries.append(("single-channel-physical-board-has-B-part", corrupted, seeds))

            corrupted = copy.deepcopy(source)
            row = corrupted["rows"][0]["report"]
            old_binding = (frame("DC_VOLTAGE") + frame("SENSORS_HIGH_OUTPUT_A") +
                           frame("JOA.1") + frame("JOA.2"))
            wrong_binding = (frame("DC_VOLTAGE") + frame("SENSORS_HIGH_OUTPUT_A") +
                             frame("JOB.1") + frame("JOB.2"))
            if row["programIdentity"].count(old_binding) != 1:
                raise AssertionError("could not locate the single-channel output probe binding")
            row["programIdentity"] = row["programIdentity"].replace(
                old_binding, wrong_binding, 1)
            canaries.append(("single-channel-program-probes-B-output", corrupted, seeds))
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

    if row_profile(source["rows"][0]).get("planVersion") == 4:
        corrupted = copy.deepcopy(source)
        report = corrupted["rows"][0]["report"]
        for audit in (report["cleanupAudit"], report["cold"]["cleanupAudit"]):
            audit["disconnectedBindingCount"] -= 1
        canaries.append(("missing-cleanup-binding-total", corrupted, seeds))

        corrupted = copy.deepcopy(source)
        report = corrupted["rows"][0]["report"]
        for audit in (report["cleanupAudit"], report["cold"]["cleanupAudit"]):
            audit["lastBindingCount"] -= 1
            audit["lastDisconnectedBindingCount"] -= 1
        canaries.append(("missing-last-owner-binding", corrupted, seeds))

    for name, corrupted, case_seeds in canaries:
        reject_corruption(name, corrupted, case_seeds, historical_audit)
    return baseline_pairs, len(canaries), True, power_context_selftests


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt", nargs="?", default=str(
        Path(__file__).with_name("compiled-d01-canary-01.json")))
    parser.add_argument("--seeds", nargs="+", default=["0"])
    parser.add_argument("--historical-audit", action="store_true",
                        help="audit retained provider@2/@3 receipts; never an admission result")
    args = parser.parse_args()
    pairs, count, catalog_flip, power_context_selftests = run_canaries(
        args.receipt, args.seeds, historical_audit=args.historical_audit)
    label = "HISTORICAL AUDIT ONLY" if args.historical_audit else "PASS: PLAN@4/PROVIDER@4"
    suffix = "; never admission" if args.historical_audit else ""
    power_summary = ("power-contract synthetic canaries=NOT RUN (historical audit)" if args.historical_audit else
                     "power-contract synthetic canaries={} PASS".format(
                         len(power_context_selftests)))
    print(f"{label}: baseline pairs={pairs}; corrupted-receipt rejection canaries={count}; schema-bound catalog claim flip={'PASS' if catalog_flip else 'FAIL'}; {power_summary}{suffix}")
