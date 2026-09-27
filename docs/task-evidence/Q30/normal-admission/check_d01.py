"""Independent census, electrical output and cleanup oracle for Q30 D01 receipts."""
import argparse
import itertools
import json
import math
import re

FAULTS = {
    "DREV_OPEN": ("DIODE_OPEN", "DREV"),
    "REN_OPEN": ("RESISTOR_OPEN", "REN"),
    "SENSOR_A_OPEN": ("RESISTOR_OPEN", "RSA"),
    "DRIVE_A_OPEN": ("RESISTOR_OPEN", "RDA"),
    "RELAY_B_COIL_OPEN": ("RELAY_COIL_OPEN", "KB"),
}
STATES = [("LOW", False, False), ("A_ONLY", True, False),
          ("B_ONLY", False, True), ("HIGH", True, True)]
POINTS = ["DREV_K", "REN_2", "RAIL5", "RSA_2", "RDA_1", "RDA_2",
          "KB_A2", "OUTPUT_A", "OUTPUT_B"]
IDS = [f"SENSORS_{s}_{p}" for s, _, _ in STATES for p in POINTS] + ["MAIN_12V"]
POINT_PROBES = {
    "DREV_K": ("DREV.K", "J1.2"),
    "REN_2": ("REN.2", "J1.2"),
    "RAIL5": ("U1.OUTPUT", "J1.2"),
    "RSA_2": ("RSA.2", "J1.2"),
    "RDA_1": ("RDA.1", "J1.2"),
    "RDA_2": ("RDA.2", "J1.2"),
    "KB_A2": ("KB.A2", "J1.2"),
    "OUTPUT_A": ("JOA.1", "JOA.2"),
    "OUTPUT_B": ("JOB.1", "JOB.2"),
}


def expected_catalog_registered(schema):
    """Schema 1 predates Q30 catalog registration; schema 2 requires it."""
    if type(schema) is not int or schema not in (1, 2):
        raise ValueError("unsupported Q30 D01 admission metadata schema")
    return schema == 2


def require_program_binding(program_identity, sample_id, red, black):
    def frame(value):
        return f"{_java_string_length(value)}:{value}"
    binding = frame("DC_VOLTAGE") + frame(sample_id) + frame(red) + frame(black)
    assert program_identity.count(binding) == 1, "sample/probe binding mismatch: " + sample_id


def _java_string_length(value):
    return sum(2 if ord(character) > 0xFFFF else 1 for character in value)


def _java_string_end(value, start, units):
    """Return the Python index after a Java String.length() count of UTF-16 units."""
    assert units >= 0, "negative canonical string length"
    offset, consumed = start, 0
    while offset < len(value) and consumed < units:
        consumed += 2 if ord(value[offset]) > 0xFFFF else 1
        offset += 1
    assert consumed == units, "canonical frame splits a UTF-16 surrogate pair or exceeds its value"
    return offset


def _plain_frame(value, offset):
    assert offset < len(value), "truncated canonical frame"
    if value.startswith("-1:", offset):
        return None, offset + 3
    colon = value.find(":", offset)
    assert colon > offset and value[offset:colon].isdigit(), "malformed canonical frame length"
    size = int(value[offset:colon])
    start = colon + 1
    end = _java_string_end(value, start, size)
    return value[start:end], end


def _context_frame(value, offset):
    assert offset < len(value), "truncated dependency-context frame"
    if value.startswith("N;", offset):
        return None, offset + 2
    assert value.startswith("V", offset), "malformed dependency-context frame"
    result, end = _plain_frame(value, offset + 1)
    assert end < len(value) and value[end] == ";", "dependency-context terminator is missing"
    return result, end + 1


def _framed_fields(value, context=False):
    assert isinstance(value, str) and value, "canonical field set is empty"
    if context:
        assert value.startswith(DIAGNOSTIC_CONTEXT_ENVELOPE), \
            "unsupported D01 diagnostic-context envelope"
        value = value[len(DIAGNOSTIC_CONTEXT_ENVELOPE):]
        epoch, offset = _context_frame(value, 0)
    else:
        epoch, offset = _plain_frame(value, 0)
    assert epoch, "canonical interpretation epoch is missing"
    result = {}
    while offset < len(value):
        if value[offset] != "F":
            assert context, "canonical field record marker is missing"
            extension, offset = _context_frame(value, offset)
            extension_payload_counts = {
                "single-copper-layer": 1,
                "placement": 6,
                "barrier": 3,
            }
            assert extension in extension_payload_counts, \
                "unsupported dependency-context extension record"
            for _ in range(extension_payload_counts[extension]):
                _, offset = _context_frame(value, offset)
            continue
        offset += 1
        reader = _context_frame if context else _plain_frame
        key, offset = reader(value, offset)
        item, offset = reader(value, offset)
        assert isinstance(key, str) and key and key not in result, "canonical field is missing or duplicated"
        result[key] = item
        # GenerationDependencyContext also emits counted string-list records:
        # F + key + count + count framed values. Skip those values so the next
        # outer record remains aligned, while preserving the count as the field value.
        if context and isinstance(item, str) and item.isdigit() and offset < len(value) and \
                value[offset] != "F":
            for _ in range(int(item)):
                _, offset = _context_frame(value, offset)
            assert offset == len(value) or value[offset] in "FVN", \
                "dependency-context list boundary is malformed"
    return epoch, result


def _program_steps(program_identity):
    prefix = "diagnostic-program-v1"
    assert isinstance(program_identity, str) and program_identity.startswith(prefix), \
        "unsupported diagnostic program canonical identity"
    _, offset = _plain_frame(program_identity, len(prefix))  # declared plan
    tokens = []
    while offset < len(program_identity):
        token, offset = _plain_frame(program_identity, offset)
        tokens.append(token)
    assert tokens and len(tokens) % 6 == 0, "diagnostic program has an incomplete step"
    return [tuple(tokens[index:index + 6]) for index in range(0, len(tokens), 6)]


def _parse_plan_manifest(canonical, version, expected_fields):
    prefix = "rb30-plan@{}".format(version)
    assert isinstance(canonical, str) and canonical.startswith(prefix + ";"), \
        "unsupported Q30 realization plan epoch"
    result = {}
    for item in canonical[len(prefix) + 1:].split(";"):
        assert "=" in item, "malformed Q30 realization plan field"
        name, value = item.split("=", 1)
        assert name and name not in result, "missing or duplicate Q30 plan field"
        result[name] = value
    assert tuple(result) == expected_fields, "Q30 realization plan field schema changed"
    return result


def _parse_length_records(canonical):
    """Parse the length-delimited fields used by physical admission snapshots."""
    records = []
    offset = 0
    while offset < len(canonical):
        colon = canonical.find(":", offset)
        assert colon > offset and canonical[offset:colon].isdigit(), \
            "malformed physical-admission field name length"
        name_length = int(canonical[offset:colon])
        name_start = colon + 1
        name_end = _java_string_end(canonical, name_start, name_length)
        assert name_end < len(canonical) and canonical[name_end] == "=", \
            "malformed physical-admission field name"
        name = canonical[name_start:name_end]
        value_length_start = name_end + 1
        value_colon = canonical.find(":", value_length_start)
        assert value_colon > value_length_start, \
            "malformed physical-admission field value length"
        length_text = canonical[value_length_start:value_colon]
        assert length_text == "-1" or length_text.isdigit(), \
            "invalid physical-admission field value length"
        value_start = value_colon + 1
        if length_text == "-1":
            value = None
            assert value_start < len(canonical) and canonical[value_start] == ";", \
                "physical-admission null field terminator is missing"
            offset = value_start + 1
        else:
            value_length = int(length_text)
            value_end = _java_string_end(canonical, value_start, value_length)
            assert value_end < len(canonical) and canonical[value_end] == ";", \
                "physical-admission field exceeds its containing value"
            value = canonical[value_start:value_end]
            offset = value_end + 1
        records.append((name, value))
    return records


def _admission_component_records(admission_canonical):
    prefix = "MEDIUM_BOARD_NORMAL@1"
    assert isinstance(admission_canonical, str) and admission_canonical.startswith(prefix), \
        "current owner omitted its normal-medium physical admission canonical"
    admission_records = _parse_length_records(admission_canonical[len(prefix):])
    board_declarations = [value for name, value in admission_records
                          if name == "board-declaration"]
    assert len(board_declarations) == 1 and board_declarations[0], \
        "normal-medium admission has no unique board declaration"
    return _parse_length_records(board_declarations[0])


def validate_current_realization_manifest(manifest, seed, topology,
                                          admission_canonical):
    """Bind provider@3 support choice and package count to the actual board."""
    fields = _parse_plan_manifest(manifest, 3, CURRENT_PLAN_FIELDS)
    assert fields["seed"] == seed, "provider@3 plan manifest seed mismatch"
    assert fields["fault"] in FAULTS, "provider@3 plan has an unknown selected fault"
    for name, expected in PLAN_STATIC_FIELDS.items():
        assert fields[name] == expected, "provider@3 plan changed " + name
    assert fields["sensorPullDowns"] == SENSOR_PULL_DOWNS, \
        "provider@3 plan omitted the two declared 1k raw-sensor pull-downs"

    support_identity = fields["support"]
    assert support_identity in CURRENT_SUPPORTS, "provider@3 support identity is unknown"
    support_suffix, package_count = CURRENT_SUPPORTS[support_identity]
    assert fields["topology"] + "_" + support_suffix == topology, \
        "provider@3 plan support/topology identity mismatch"
    assert fields["packages"] == str(package_count), \
        "provider@3 declared package count disagrees with support identity"

    board_records = _admission_component_records(admission_canonical)
    component_ids = [value for name, value in board_records if name == "component"]
    assert len(component_ids) == package_count and len(set(component_ids)) == package_count, \
        "provider@3 actual board component count disagrees with declared support"
    assert "RPIN_A" in component_ids and "RPIN_B" in component_ids, \
        "provider@3 physical board lacks its raw-sensor pull-down components"

    groups = {}
    current_id = None
    for name, value in board_records:
        if name == "component":
            current_id = value
            groups[current_id] = []
        elif current_id is not None:
            groups[current_id].append((name, value))
    pads = []
    for name, value in board_records:
        if name == "pad":
            pieces = value.split("|")
            assert len(pieces) == 4, "malformed physical board pad declaration"
            pads.append(pieces)
    for component_id, expected_nets in (
            ("RPIN_A", {"A_RAW", "CTRL_RETURN"}),
            ("RPIN_B", {"B_RAW", "CTRL_RETURN"})):
        attributes = {}
        for name, value in groups[component_id]:
            attributes.setdefault(name, []).append(value)
        assert attributes.get("component-type") == ["RESISTOR"] and \
            attributes.get("component-package") == ["AXIAL_RESISTOR"], \
            "provider@3 pull-down is not an axial resistor: " + component_id
        component_pads = [pad for pad in pads if pad[1] == component_id]
        assert len(component_pads) == 2 and {pad[3] for pad in component_pads} == expected_nets, \
            "provider@3 pull-down does not connect raw input to CTRL_RETURN: " + component_id
    return package_count


def validate_legacy_realization_manifest(manifest, seed, topology):
    """Keep the audited provider@2 plan schema isolated from current v3 rules."""
    fields = _parse_plan_manifest(manifest, 2, LEGACY_PLAN_FIELDS)
    assert fields["seed"] == seed, "historical provider@2 plan seed mismatch"
    assert fields["fault"] in FAULTS, "historical provider@2 plan has an unknown selected fault"
    for name, expected in PLAN_STATIC_FIELDS.items():
        assert fields[name] == expected, "historical provider@2 plan changed " + name
    support_identity = fields["support"]
    assert support_identity in LEGACY_SUPPORTS, \
        "historical provider@2 support identity is unknown"
    support_suffix, package_count = LEGACY_SUPPORTS[support_identity]
    assert fields["topology"] + "_" + support_suffix == topology, \
        "historical provider@2 plan support/topology identity mismatch"
    assert fields["packages"] == str(package_count), \
        "historical provider@2 package count disagrees with its support identity"
    return package_count


SERVICE_PREPARATION_POLICY = (
    "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=exact-current-owner")
SERVICE_PREPARATION_UNITS = 5
MODEL_DUMP_EPOCH = "circuitjs-source-load-model-inputs-no-transient-dump-v6"
DIAGNOSTIC_CONTEXT_ENVELOPE = "tsj-d01-diagnostic-context-v3;"
CURRENT_SUPPORTS = {
    "RB30_COMPACT_NO_STATUS@2": ("COMPACT_33", 33),
    "RB30_STANDARD_STATUS@2": ("STANDARD_35", 35),
    "RB30_FILTERED_SENSOR_INPUTS@2": ("FILTERED_37", 37),
}
LEGACY_SUPPORTS = {
    "RB30_COMPACT_NO_STATUS@1": ("COMPACT_31", 31),
    "RB30_STANDARD_STATUS@1": ("STANDARD_33", 33),
    "RB30_FILTERED_SENSOR_INPUTS@1": ("FILTERED_35", 35),
}
CURRENT_PLAN_FIELDS = (
    "seed", "topology", "support", "layout", "routing", "physicalPolicy",
    "fault", "packages", "main", "regulator", "coil", "load", "sensors",
    "sensorPullDowns", "loadReference")
LEGACY_PLAN_FIELDS = tuple(
    name for name in CURRENT_PLAN_FIELDS if name != "sensorPullDowns")
PLAN_STATIC_FIELDS = {
    "physicalPolicy": "MEDIUM_BOARD@1",
    "main": "12V",
    "regulator": "5V-E02",
    "coil": "5V-E03",
    "load": "isolated-12V-180ohm-per-channel",
    "sensors": "two-E04-decisions",
    "loadReference": "isolated",
}
SENSOR_PULL_DOWNS = (
    "RPIN_A:1000ohm(A_RAW,CTRL_RETURN),"
    "RPIN_B:1000ohm(B_RAW,CTRL_RETURN)")


def _validate_q30_program(program_identity, legacy_fixed_waits):
    steps = _program_steps(program_identity)
    expected_count = 52 if legacy_fixed_waits else 47
    assert len(steps) == expected_count, "Q30 diagnostic program step census changed"
    measurements = [step for step in steps if step[0] == "DC_VOLTAGE"]
    assert [step[1] for step in measurements] == IDS, \
        "actual program lost/reordered its 37 measurements"
    expected_bindings = {}
    for state, _, _ in STATES:
        for point, probes in POINT_PROBES.items():
            sample_id = f"SENSORS_{state}_{point}"
            expected_bindings[sample_id] = probes
            require_program_binding(program_identity, sample_id, *probes)
    expected_bindings["MAIN_12V"] = ("J1.1", "J1.2")
    require_program_binding(program_identity, "MAIN_12V", "J1.1", "J1.2")
    for sample_id, step in zip(IDS, measurements):
        assert step[2:6] == (expected_bindings[sample_id][0], expected_bindings[sample_id][1], None, "0"), \
            "wrong DC probe binding: " + sample_id

    expected_steps = []
    for state, _, _ in STATES:
        expected_steps.append(("INPUT", "SENSORS_" + state, None, None, None, "0"))
        expected_steps.append(("WAIT", state + "_INPUT_SETTLED", None, None, None, "0"))
        for point, probes in POINT_PROBES.items():
            expected_steps.append(("DC_VOLTAGE", "SENSORS_" + state + "_" + point,
                                   probes[0], probes[1], None, "0"))
    expected_steps.append(("DC_VOLTAGE", "MAIN_12V", "J1.1", "J1.2", None, "0"))
    expected_steps.append(("POWER", "BOARD_POWER_OFF_SERVICE", None, None, "UNPOWERED", "0"))
    if legacy_fixed_waits:
        for _ in range(5):
            expected_steps.append(("WAIT", "SERVICE_DISCHARGE", None, None, None,
                                   "3fa999999999999a"))
    expected_steps.append(("SETTLE", None, None, None, None, "0"))
    assert steps == expected_steps, "Q30 diagnostic program step order/service recipe changed"

    input_steps = [step[1] for step in steps if step[0] == "INPUT"]
    assert input_steps == ["SENSORS_LOW", "SENSORS_A_ONLY", "SENSORS_B_ONLY", "SENSORS_HIGH"]
    condition_waits = [step[1] for step in steps if step[0] == "WAIT" and
                       step[1] != "SERVICE_DISCHARGE"]
    assert condition_waits == ["LOW_INPUT_SETTLED", "A_ONLY_INPUT_SETTLED",
                               "B_ONLY_INPUT_SETTLED", "HIGH_INPUT_SETTLED"]
    service_power = [index for index, step in enumerate(steps)
                     if step[0] == "POWER" and step[1] == "BOARD_POWER_OFF_SERVICE"]
    service_wait = [index for index, step in enumerate(steps)
                    if step[0] == "WAIT" and step[1] == "SERVICE_DISCHARGE" and
                    step[5] == "3fa999999999999a"]
    settle = [index for index, step in enumerate(steps) if step[0] == "SETTLE"]
    assert len(service_power) == 1 and steps[service_power[0]][4] == "UNPOWERED"
    assert len(settle) == 1
    if legacy_fixed_waits:
        assert len(service_wait) == 5
        assert service_power[0] < service_wait[0] < service_wait[-1] < settle[0]
    else:
        assert not service_wait and service_power[0] < settle[0]
    return steps


def validate_q30_work_contract(report, seed):
    """Validate the preserved provider@2 receipt or derive provider@3 work."""
    has_context = "contextCanonical" in report
    has_completion = "explicitCompletion" in report
    assert has_context == has_completion, "partial temporal-contract metadata"
    if not has_context:
        # Preserve the audited provider@2 compiled evidence format. This exact
        # count is historical evidence only; new receipts must publish context.
        assert report["providerId"] == "rb30-control-diagnostic@2"
        _validate_q30_program(report["programIdentity"], legacy_fixed_waits=True)
        validate_legacy_realization_manifest(
            report["realizationManifest"], seed, report["topology"])
        assert report["proofUnits"] == 315 and report["cold"]["workUnits"] == 315, \
            "known provider@2 Q30 receipt work census changed"
        return 315, "provider@2"

    steps = _validate_q30_program(report["programIdentity"], legacy_fixed_waits=False)
    assert report["providerId"] == "rb30-control-diagnostic@3", \
        "current Q30 receipt has an unrecognized diagnostic provider"
    assert report["explicitCompletion"] is False, "direct D01 must use its independent false boundary"
    context_epoch, context = _framed_fields(report["contextCanonical"], context=True)
    assert context_epoch == "tsj-generation-dependencies-v16"
    assert context.get("epoch.circuit-dump") == MODEL_DUMP_EPOCH, \
        "current Q30 context lacks the required CircuitJS model-dump epoch v6"
    assert context.get("request.manifest") == report["requestManifest"]
    assert context.get("realization.manifest") == report["realizationManifest"], \
        "provider@3 report manifest differs from the trusted dependency context"
    assert context.get("epoch.temporal-context") == "recipe-cache-plus-raw-owner-v1"
    assert context.get("board.family") == "RB30_CONTROL" and context.get("board.seed") == seed
    assert context.get("board.topology") == report["topology"]
    assert context.get("physical.admission.identity") == "MEDIUM_BOARD_NORMAL@1"
    assert context.get("temporal.contract.id") == "RB30_TWO_CHANNEL_FUNCTION"
    assert context.get("temporal.contract.version") == "2"
    assert context.get("diagnostic.service-preparation.policy") == SERVICE_PREPARATION_POLICY, \
        "Q30 context lacks the exact current-owner REMOVE readiness policy"
    validate_current_realization_manifest(
        report["realizationManifest"], seed, report["topology"],
        context.get("physical.admission.canonical"))
    recipe_epoch, recipe = _framed_fields(context["temporal.contract.cache-recipe"])
    assert recipe_epoch == "generated-temporal-recipe-v1"
    profile_units = int(recipe["parameter.profile-work-units"])
    retest_units = int(recipe["parameter.customer-retest-work-units"])
    assert profile_units == retest_units == 5, "Q30 temporal work contract must declare five-unit slices"

    # Proof work includes the provider-owned service readiness cursor in
    # addition to the 47 observation-program steps.
    per_hypothesis = (11 + len(steps) + 3 * (profile_units - 1) +
                      (retest_units - 1) +
                      (SERVICE_PREPARATION_UNITS - 1) + (profile_units - 1))
    expected = len(FAULTS) * per_hypothesis
    assert expected == 410, "Q30 direct service-preparation work formula changed"
    assert report["proofUnits"] == expected, "direct D01 executed work differs from v2 formula"
    assert report["cold"]["workUnits"] == expected, "direct D01 receipt work differs from v2 formula"
    return expected, "v2"


def _require_record(value, expected_keys, label):
    assert type(value) is dict, label + " must be an object"
    assert set(value) == set(expected_keys), label + " field schema changed"
    return value


def _require_json_integer(value, label, minimum=None, maximum=None):
    require_integer(value, label)
    if minimum is not None:
        assert value >= minimum, label + " is below its allowed range"
    if maximum is not None:
        assert value <= maximum, label + " exceeds its allowed range"


def _require_finite_number(value, label):
    assert type(value) in (int, float) and math.isfinite(value), label + " must be finite"


def validate_service_preparation_canaries(evidence):
    """Validate the real provider@3 readiness, stale-context and cleanup canaries."""
    top_keys = (
        "status", "providerId", "policyCanonical", "caseCount", "passedCount",
        "totalSolverAdvances", "healthyProfileAdvanceUnits",
        "serviceCursorWorkUnitsCompleted", "setupElapsedMs", "elapsedMs",
        "cleanupComplete", "cases", "cleanup")
    record = _require_record(evidence, top_keys, "service-preparation canaries")
    assert record["status"] == "PASS"
    assert record["providerId"] == "rb30-control-diagnostic@3"
    assert record["policyCanonical"] == SERVICE_PREPARATION_POLICY
    _require_json_integer(record["caseCount"], "service canary case count", 7, 7)
    _require_json_integer(record["passedCount"], "service canary passed count", 7, 7)
    assert record["cleanupComplete"] is True
    _require_json_integer(record["totalSolverAdvances"], "service solver advance count", 1, 5)
    _require_json_integer(record["healthyProfileAdvanceUnits"],
                          "healthy profile advance units", 1, 5)
    _require_json_integer(record["serviceCursorWorkUnitsCompleted"],
                          "service cursor completed units", 10, 10)
    _require_json_integer(record["setupElapsedMs"], "service setup elapsed time", 0)
    _require_json_integer(record["elapsedMs"], "service canary elapsed time", 1)
    assert record["elapsedMs"] >= record["setupElapsedMs"]

    cases = _require_record(record["cases"], (
        "relayDelayed", "resistorImmediate", "staleOwner", "stalePower",
        "staleSourceCommand", "staleSolverRecipe", "cancellation"),
        "service-preparation case set")
    base_case_keys = {
        "status", "outcome", "componentId", "targetId", "workUnits",
        "completedUnits", "solverAdvances", "elapsedMs",
    }

    def common_case(name, expected_component, expected_outcome, expected_completed,
                    expected_advances):
        item = cases[name]
        for key_name in base_case_keys:
            assert key_name in item, name + " missing " + key_name
        assert item["status"] == "PASS"
        assert item["outcome"] == expected_outcome
        if expected_component is not None:
            assert item["componentId"] == expected_component
        assert isinstance(item["targetId"], str) and item["targetId"]
        _require_json_integer(item["workUnits"], name + " work units", 5, 5)
        _require_json_integer(item["completedUnits"], name + " completed units",
                              expected_completed, expected_completed)
        _require_json_integer(item["solverAdvances"], name + " solver advances",
                              expected_advances, expected_advances)
        _require_json_integer(item["elapsedMs"], name + " elapsed time", 0)
        return item

    relay = common_case("relayDelayed", None, "DELAYED_READY_WITHIN_BOUND", 5, None)
    _require_record(relay, base_case_keys | {
        "advancedSeconds", "maximumAdvanceSeconds", "availableBefore", "availableAfter",
        "coilCurrentBeforePowerOffAmps", "coilCurrentAfterPowerOffSettleAmps",
        "maxSolverAdvances", "cursorClosed"}, "delayed relay case")
    assert relay["componentId"] in {"KA", "KB"}
    assert relay["targetId"] == relay["componentId"] + "_ORIGINAL"
    _require_json_integer(relay["solverAdvances"], "relay solver advances", 1, 5)
    _require_json_integer(relay["maxSolverAdvances"], "relay maximum solver advances", 5, 5)
    _require_finite_number(relay["advancedSeconds"], "relay advanced time")
    assert 0 < relay["advancedSeconds"] <= 0.25 + 1e-12
    _require_finite_number(relay["maximumAdvanceSeconds"], "relay maximum advance")
    assert math.isclose(relay["maximumAdvanceSeconds"], .05, rel_tol=0, abs_tol=1e-15)
    assert relay["advancedSeconds"] <= relay["solverAdvances"] * .05 + 1e-12
    assert relay["availableBefore"] is False and relay["availableAfter"] is True
    assert relay["cursorClosed"] is True
    for name in ("coilCurrentBeforePowerOffAmps", "coilCurrentAfterPowerOffSettleAmps"):
        _require_finite_number(relay[name], "relay " + name)
        assert abs(relay[name]) >= 1e-6, "relay canary lacks demonstrated stored coil current"

    resistor = common_case("resistorImmediate", "RDA", "IMMEDIATE_READY_NO_ADVANCE", 5, 0)
    _require_record(resistor, base_case_keys | {
        "advancedSeconds", "maximumAdvanceSeconds", "availableBefore", "availableAfter",
        "cursorClosed"}, "immediate resistor case")
    assert resistor["availableBefore"] is True and resistor["availableAfter"] is True
    assert resistor["targetId"] == "RDA_ORIGINAL"
    assert resistor["cursorClosed"] is True
    _require_finite_number(resistor["advancedSeconds"], "resistor advanced time")
    assert resistor["advancedSeconds"] == 0
    _require_finite_number(resistor["maximumAdvanceSeconds"], "resistor maximum advance")
    assert math.isclose(resistor["maximumAdvanceSeconds"], .05, rel_tol=0, abs_tol=1e-15)

    stale_names = ("staleOwner", "stalePower", "staleSourceCommand", "staleSolverRecipe")
    stale_keys = base_case_keys | {
        "maxSolverAdvances", "rejected", "rejection", "rejectedBeforeAdvance",
        "setupAdvancedSeconds",
        "guardRestored", "ownerRestored", "powerStateRestored", "powerStateBefore",
        "powerStateAfter", "allSourcesDisconnectedBefore", "allSourcesDisconnectedAfter",
        "sourceControlsRestored", "sourceCommandRestored", "controlRevisionAdvanced",
        "controlStateRestored", "solverRecipeRestored", "cursorCancelled",
    }
    for name in stale_names:
        item = common_case(name, "RDA", "REJECTED_BEFORE_ADVANCE", 0, 0)
        _require_record(item, stale_keys, name + " stale-context case")
        assert item["targetId"] == "RDA_ORIGINAL"
        _require_json_integer(item["maxSolverAdvances"], name + " maximum solver advances", 5, 5)
        assert item["rejected"] is True and item["rejectedBeforeAdvance"] is True
        assert isinstance(item["rejection"], str) and item["rejection"]
        expected_rejection = ("lost its exact installed owner" if name == "staleOwner" else
                              "lost its exact owner, controls, part, or solver recipe")
        assert expected_rejection in item["rejection"]
        _require_finite_number(item["setupAdvancedSeconds"], name + " setup advance")
        assert item["setupAdvancedSeconds"] >= 0
        for boolean in ("guardRestored", "ownerRestored", "powerStateRestored",
                        "allSourcesDisconnectedBefore", "allSourcesDisconnectedAfter",
                        "sourceControlsRestored", "sourceCommandRestored",
                        "controlStateRestored", "solverRecipeRestored", "cursorCancelled"):
            assert item[boolean] is True, name + " failed " + boolean
        assert item["powerStateBefore"] == item["powerStateAfter"] == "UNPOWERED"
        assert item["controlRevisionAdvanced"] is (name == "stalePower")

    cancellation = common_case("cancellation", "RDA", "CANCELLED_NO_ADVANCE_OR_REPOWER", 0, 0)
    _require_record(cancellation, base_case_keys | {
        "maxSolverAdvances", "powerStateBefore", "powerStateAfter",
        "allSourcesDisconnectedBefore", "allSourcesDisconnectedAfter", "cursorCancelled"},
        "service cursor cancellation case")
    assert cancellation["targetId"] == "RDA_ORIGINAL"
    _require_json_integer(cancellation["maxSolverAdvances"],
                          "cancellation maximum solver advances", 5, 5)
    assert cancellation["powerStateBefore"] == cancellation["powerStateAfter"] == "UNPOWERED"
    assert cancellation["allSourcesDisconnectedBefore"] is True
    assert cancellation["allSourcesDisconnectedAfter"] is True
    assert cancellation["cursorCancelled"] is True
    assert record["totalSolverAdvances"] == relay["solverAdvances"] + resistor["solverAdvances"]

    cleanup = _require_record(record["cleanup"], (
        "status", "exactOwnerRestored", "exactControllerRestored", "exactGraphIdentityRestored",
        "exactGraphContentsRestored", "powerStateRestored", "allSourceControlsRestored",
        "sourceControlRevisionAdvanced", "allSourceCommandsRestored", "solverRecipeRestored",
        "selectedFaultRestored", "inputStateRestored", "physicalValidationAfterRestore",
        "profileRepreparedAfterCanaries", "contextCapturedAfterCanaries",
        "simulationTimeBefore", "simulationTimeAfter", "simulationTimeAdvanced",
        "cursorCreatedCount", "cursorClosedCount", "cursorRetainedCount", "mutationCount",
        "elapsedMs", "analysisPending", "challengeOperationInProgress", "challengeReady",
        "challengeState", "cursorCleanupComplete", "dcAnalysisPending", "electricallyUnpowered",
        "failedChecks", "failedOwnerMatches", "faultBindingIdentityRestored",
        "generatedRuntimeSettled", "generatedVerificationAnalyzed",
        "generatedVerificationPending", "generatedVerificationRunning",
        "generationCoordinatorBetweenSteps", "healthyFamilyValidated",
        "lifecycleEvidencePresent", "measurementOverlayActive", "observationalValidationDepth",
        "pendingPowerState", "physicalMutationInProgress", "powerState",
        "runtimeInstallationInProgress", "selectedFaultApplied", "selectedFaultValidated",
        "stopMessagePresent"), "service canary cleanup")
    assert cleanup["status"] == "PASS"
    for key_name in ("exactOwnerRestored", "exactControllerRestored", "exactGraphIdentityRestored",
                     "exactGraphContentsRestored", "powerStateRestored", "allSourceControlsRestored",
                     "sourceControlRevisionAdvanced", "allSourceCommandsRestored",
                     "solverRecipeRestored", "selectedFaultRestored", "inputStateRestored",
                     "physicalValidationAfterRestore", "profileRepreparedAfterCanaries",
                     "contextCapturedAfterCanaries", "simulationTimeAdvanced",
                     "challengeReady", "cursorCleanupComplete", "faultBindingIdentityRestored",
                     "generatedRuntimeSettled", "generatedVerificationAnalyzed",
                     "healthyFamilyValidated", "lifecycleEvidencePresent",
                     "selectedFaultApplied", "selectedFaultValidated"):
        assert cleanup[key_name] is True, "service canary cleanup failed " + key_name
    for key_name in ("analysisPending", "challengeOperationInProgress", "dcAnalysisPending",
                     "electricallyUnpowered", "failedOwnerMatches",
                     "generatedVerificationPending", "generatedVerificationRunning",
                     "generationCoordinatorBetweenSteps", "measurementOverlayActive",
                     "physicalMutationInProgress", "runtimeInstallationInProgress",
                     "stopMessagePresent"):
        assert cleanup[key_name] is False, "service canary cleanup left " + key_name
    assert cleanup["challengeState"] == "READY"
    assert cleanup["powerState"] == "POWERED"
    assert cleanup["pendingPowerState"] == "NONE"
    assert cleanup["failedChecks"] == ""
    _require_json_integer(cleanup["observationalValidationDepth"],
                          "service cleanup validation depth", 0, 0)
    _require_finite_number(cleanup["simulationTimeBefore"], "service cleanup initial simulation time")
    _require_finite_number(cleanup["simulationTimeAfter"], "service cleanup final simulation time")
    assert cleanup["simulationTimeAfter"] > cleanup["simulationTimeBefore"]
    _require_json_integer(cleanup["cursorCreatedCount"], "service cursors created", 7, 7)
    _require_json_integer(cleanup["cursorClosedCount"], "service cursors closed", 7, 7)
    _require_json_integer(cleanup["cursorRetainedCount"], "service cursors retained", 0, 0)
    _require_json_integer(cleanup["mutationCount"], "service mutation count", 0, 0)
    _require_json_integer(cleanup["elapsedMs"], "service cleanup elapsed time", 1)
    assert cleanup["elapsedMs"] <= record["elapsedMs"]


def key(fault):
    kind, owner = FAULTS[fault]
    fields = ["RB30_CONTROL", kind, owner, fault, "9221120237041090560",
              "9221120237041090560"]  # canonical NaN for non-value-mutation faults
    return "fault-hypothesis-v1|" + "".join(f"{len(v)}:{v}" for v in fields)


def expected_outputs(fault, a_high, b_high):
    """Channel response dictated by the physical Q30 fault locus."""
    if fault in ("DREV_OPEN", "REN_OPEN"):
        return False, False
    if fault in ("SENSOR_A_OPEN", "DRIVE_A_OPEN"):
        return False, b_high
    if fault == "RELAY_B_COIL_OPEN":
        return a_high, False
    raise AssertionError("unknown expected Q30 fault")


def require_integer(value, label):
    assert type(value) is int, label


def validate_warm_and_negative_evidence(report, seed):
    warm = report["warm"]
    assert warm["status"] == "PASS", "warm cache reuse did not pass"
    assert warm["cacheScope"] == report["cacheScope"] == "verifierOnly"
    require_integer(report["cacheEntries"], "warm cache entry count")
    require_integer(warm["cacheHits"], "warm cache hits")
    require_integer(warm["cacheMisses"], "warm cache misses")
    require_integer(warm["samplePopulationPerHypothesis"], "warm sample count")
    require_integer(warm["hypothesisCount"], "warm hypothesis count")
    require_integer(warm["elapsedMillis"], "warm elapsed time")
    assert report["cacheEntries"] == 1 and report["coldStageCleanupComplete"] is True
    assert warm["cacheHit"] is True and warm["cacheHits"] == 1 and warm["cacheMisses"] == 1
    assert warm["sameTrustedContext"] is True
    assert warm["sameProviderProgramPartition"] is True
    assert warm["sameImmutableEvidence"] is True
    assert warm["samplePopulationPerHypothesis"] == 37 and warm["hypothesisCount"] == 5
    assert warm["receiptBoundToFreshOwner"] is True and warm["warmReuseReceipt"] is True
    assert warm["elapsedMillis"] == 0 and warm["ownerSeed"] == seed

    negatives = report["negatives"]
    expected_negative_names = {
        "incompleteSession", "foreignHypothesis", "failedCleanupRetry",
        "sourceContextChange", "mismatchedContext",
    }
    assert set(negatives) == expected_negative_names, "negative canary census"
    for name, evidence in negatives.items():
        assert evidence["status"] == "PASS", "negative canary did not complete: " + name

    incomplete = negatives["incompleteSession"]
    assert incomplete["rejected"] is True and incomplete["receiptIssued"] is False
    assert incomplete["exactOwnerRestored"] is True
    assert "incomplete" in incomplete["rejection"].lower()

    foreign = negatives["foreignHypothesis"]
    foreign_seed = foreign["foreignSeed"]
    assert isinstance(foreign_seed, str) and str(int(foreign_seed)) == foreign_seed
    assert -(2**63) <= int(foreign_seed) < 2**63 and foreign_seed != seed
    expected_foreign_seed = int(seed) - 1 if int(seed) == 2**63 - 1 else int(seed) + 1
    assert int(foreign_seed) == expected_foreign_seed
    assert foreign["hypothesisKey"] == key("DREV_OPEN")
    assert foreign["rejected"] is True and foreign["foreignConstructionDisposed"] is True
    assert "foreign q30 diagnostic hypothesis" in foreign["rejection"].lower()

    retry = negatives["failedCleanupRetry"]
    assert retry["injectedFailure"] is True
    assert "injected diagnostic private cleanup failure" in retry["failure"].lower()
    assert retry["receiptIssued"] is False and retry["exactOwnerRestored"] is True
    assert retry["pendingCleanupBeforeRetry"] is True
    assert retry["pendingCleanupAfterRetry"] is False
    assert retry["exactRetryInvoked"] is True and retry["retryCompleted"] is True
    assert retry["contextRestoredAfterCanaries"] is True
    retry_audit = retry["cleanupAudit"]
    assert report["cleanupFailureCanaryAudit"] == retry_audit
    for name in ("cleanupAttemptCount", "disposedHypothesisCount", "disconnectedBindingCount",
                 "disposedElementCount", "disposalFailureCount", "lastBindingCount",
                 "lastDisconnectedBindingCount", "lastElementCount", "lastDeletedElementCount"):
        require_integer(retry_audit[name], "failed-cleanup retry audit: " + name)
    assert retry_audit["cleanupAttemptCount"] > 0
    assert retry_audit["disposalFailureCount"] == 1
    assert retry_audit["disposedHypothesisCount"] == 1
    assert retry_audit["disconnectedBindingCount"] > 0
    assert retry_audit["disposedElementCount"] > 0
    for name in ("lastOwnerGuardPassed", "lastBindingsActuallyDisconnected",
                 "lastElementsActuallyDeleted", "lastGraphDetached", "lastCleanupComplete"):
        assert retry_audit[name] is True, "failed-cleanup retry audit: " + name
    assert retry_audit["lastBindingCount"] == retry_audit["lastDisconnectedBindingCount"] > 0
    assert retry_audit["lastElementCount"] == retry_audit["lastDeletedElementCount"] > 0

    source_change = negatives["sourceContextChange"]
    assert source_change["inputId"] == "MAIN12"
    assert source_change["changedContextCaptured"] is True
    assert source_change["sourceConnectionRestored"] is True
    assert source_change["restoredContextMatchesWarm"] is True
    require_integer(source_change["changedContextHash"], "source context hash")

    mismatch = negatives["mismatchedContext"]
    assert mismatch["mismatchedRequestCaptured"] is True
    require_integer(mismatch["cacheMissCount"], "mismatched-context cache misses")
    assert mismatch["cacheMissed"] is True and mismatch["cacheMissCount"] == 1
    assert mismatch["receiptRejectedMismatchedContext"] is True
    require_integer(mismatch["mismatchedContextHash"], "mismatched request hash")


def check_document(document, seeds, require_warm=False):
    rows = document["rows"]
    assert len(rows) == len(seeds) and len(seeds) == len(set(seeds)), "seed census"
    seen, build = set(), None
    work_contract_versions = set()
    pairs = 0
    for wrapper in rows:
        seed = wrapper["seed"]
        assert isinstance(seed, str) and str(int(seed)) == seed
        assert -(2**63) <= int(seed) < 2**63 and seed in seeds and seed not in seen
        seen.add(seed)
        identity = (wrapper["previewSourceDigest"], wrapper["previewWebDigest"])
        assert all(re.fullmatch("[0-9a-f]{64}", part) for part in identity)
        build = identity if build is None else build
        assert identity == build, "mixed compiled builds"
        report = wrapper["report"]
        expected_registered = expected_catalog_registered(report["schema"])
        assert report["seed"] == seed
        assert report["requestedSeed"] == seed, "requested seed mismatch"
        assert f";seed={seed};" in report["realizationManifest"], "realization seed mismatch"
        assert report["status"] == "PASS" and report["family"] == "RB30_CONTROL"
        warm_status = report.get("warm", {}).get("status")
        if warm_status == "PASS":
            assert report["phase"] == "warmStagedCleanup"
        else:
            assert report["phase"] == "stagedCleanup"
        assert report["normalOwner"] is True and report["d01Admission"] is True
        assert report["normalAdmission"] is False and report["registered"] is expected_registered
        assert report["playerPublished"] is False
        assert report["restored"] is True and report["ownerRestored"] is True
        assert report["cleanupComplete"] is True
        assert report["contextTrusted"] is True
        program_identity = report["programIdentity"]
        expected_proof_units, work_contract_version = validate_q30_work_contract(report, seed)
        work_contract_versions.add(work_contract_version)
        if work_contract_version == "v2":
            validate_service_preparation_canaries(report["servicePreparationCanaries"])
        assert report["hypothesisCount"] == report["completedHypotheses"] == 5
        assert report["totalHypotheses"] == 5 and report["actualSampleCount"] == 185
        cold = report["cold"]
        assert cold["status"] == "PASS" and cold["elapsedMillis"] > 0
        assert cold["elapsedMillis"] == report["serviceElapsedMs"]
        assert report["elapsedMs"] >= report["serviceElapsedMs"]
        assert cold["workUnits"] == report["proofUnits"] == expected_proof_units
        audit = cold["cleanupAudit"]
        assert report["cleanupAudit"] == audit, "cleanup audit copies disagree"
        assert audit["disposedHypothesisCount"] == 5 and audit["disposalFailureCount"] == 0
        assert audit["cleanupAttemptCount"] == 5 and audit["disconnectedBindingCount"] >= 20
        assert audit["disposedElementCount"] > 0
        for name in ("lastOwnerGuardPassed", "lastBindingsActuallyDisconnected",
                     "lastElementsActuallyDeleted", "lastGraphDetached", "lastCleanupComplete"):
            assert audit[name] is True, name
        assert audit["lastBindingCount"] == audit["lastDisconnectedBindingCount"] > 0
        assert audit["lastElementCount"] == audit["lastDeletedElementCount"] > 0
        evidence = cold["evidence"]
        assert len(evidence) == report["hypothesisCount"]
        expected = {key(f): f for f in FAULTS}
        measurements = {}
        for item in evidence:
            fault = expected[item["hypothesisKey"]]
            kind, owner = FAULTS[fault]
            assert item["routeId"] == f"RB30_CONTROL/{kind}/{owner}", "hypothesis route mismatch"
            assert fault not in measurements, "duplicate hypothesis"
            assert item["admittedCandidateCount"] == 5
            assert all(item[n] is True for n in
                       ("repairReachable", "customerRetestPassed", "stateIsolated"))
            assert item["deterministicResult"] == "PASS"
            assert item["deterministicRejectionReason"] == "NONE"
            assert item["equivalentRepairClass"] == "NONE"
            assert item["executedRepairActionIds"] == ["REMOVE", "CATALOG_INSTALL"]
            samples = item["samples"]
            assert item["sampleCount"] == 37 and [v["id"] for v in samples] == IDS
            for sample in samples:
                assert sample["outcome"] == "NUMERIC"
                value, tolerance = sample["value"], sample["tolerance"]
                assert type(value) in (float, int) and math.isfinite(value)
                assert type(tolerance) in (float, int) and math.isfinite(tolerance)
                assert math.isclose(tolerance, max(.01, .02 * abs(value)),
                                    rel_tol=1e-12, abs_tol=1e-12), "changed tolerance"
            measured = {v["id"]: v["value"] for v in samples}
            assert 11.4 <= measured["MAIN_12V"] <= 12.6
            for state, _, _ in STATES:
                rail5 = measured[f"SENSORS_{state}_RAIL5"]
                if fault in {"DREV_OPEN", "REN_OPEN"}:
                    assert rail5 < 4.75, (seed, fault, state, "regulated rail should be absent")
                else:
                    assert 4.75 <= rail5 <= 5.25, (seed, fault, state, "regulated rail")
            for state, a, b in STATES:
                output_a_on, output_b_on = expected_outputs(fault, a, b)
                for channel, on in (("A", output_a_on), ("B", output_b_on)):
                    value = measured[f"SENSORS_{state}_OUTPUT_{channel}"]
                    assert (10.8 <= value <= 12.6) if on else abs(value) <= .05, (seed, fault, state, channel)
            measurements[fault] = samples
        assert set(measurements) == set(FAULTS)
        for first, second in itertools.combinations(FAULTS, 2):
            assert any(abs(a["value"] - b["value"]) >
                       a["tolerance"] + b["tolerance"]
                       for a, b in zip(measurements[first], measurements[second])), "indistinguishable faults"
            pairs += 1
        if require_warm:
            validate_warm_and_negative_evidence(report, seed)
    assert seen == set(seeds)
    assert len(work_contract_versions) == 1, "mixed temporal work-contract receipt versions"
    return pairs


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt")
    parser.add_argument("--seeds", nargs="+", required=True)
    parser.add_argument("--require-warm", action="store_true",
                        help="also require fresh-owner cache reuse and all negative canaries")
    args = parser.parse_args()
    with open(args.receipt, encoding="utf-8") as handle:
        pairs = check_document(json.load(handle), args.seeds, args.require_warm)
    proof = "cold+warm" if args.require_warm else "cold"
    print(f"PASS: genuine {proof} Q30 D01 seeds={len(args.seeds)} samplesPerSeed=185 pairs={pairs}; player gates separate")
