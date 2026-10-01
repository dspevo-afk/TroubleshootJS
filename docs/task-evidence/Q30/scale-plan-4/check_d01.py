"""Independent census, electrical output and cleanup oracle for Q30 D01 receipts."""
import argparse
import itertools
import json
import math
import re
import struct

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

SINGLE_CHANNEL_FAULTS = {
    "DREV_OPEN": ("DIODE_OPEN", "DREV"),
    "REN_OPEN": ("RESISTOR_OPEN", "REN"),
    "SENSOR_A_OPEN": ("RESISTOR_OPEN", "RSA"),
    "DRIVE_A_OPEN": ("RESISTOR_OPEN", "RDA"),
    "RELAY_A_COIL_OPEN": ("RELAY_COIL_OPEN", "KA"),
}
SINGLE_CHANNEL_STATES = [("LOW", False, False), ("HIGH", True, False)]
SINGLE_CHANNEL_POINTS = [
    "DREV_K", "REN_2", "RAIL5", "RSA_2", "RDA_1", "RDA_2",
    "KA_A2", "OUTPUT_A",
]
SINGLE_CHANNEL_POINT_PROBES = {
    "DREV_K": ("DREV.K", "J1.2"),
    "REN_2": ("REN.2", "J1.2"),
    "RAIL5": ("U1.OUTPUT", "J1.2"),
    "RSA_2": ("RSA.2", "J1.2"),
    "RDA_1": ("RDA.1", "J1.2"),
    "RDA_2": ("RDA.2", "J1.2"),
    "KA_A2": ("KA.A2", "J1.2"),
    "OUTPUT_A": ("JOA.1", "JOA.2"),
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


def _program_template(program_identity):
    prefix = "diagnostic-program-v1"
    assert isinstance(program_identity, str) and program_identity.startswith(prefix), \
        "diagnostic program canonical identity is missing or unsupported"
    plan_identity, _ = _plain_frame(program_identity, len(prefix))
    assert isinstance(plan_identity, str) and plan_identity, \
        "diagnostic program omitted its declared plan"
    template, _ = _plain_frame(plan_identity, 0)
    assert isinstance(template, str) and template, \
        "diagnostic program omitted its declared template"
    return template


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
        name_length_text = canonical[offset:colon]
        name_length = int(name_length_text)
        assert str(name_length) == name_length_text, \
            "noncanonical physical-admission field name length"
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
        if length_text != "-1":
            assert str(int(length_text)) == length_text, \
                "noncanonical physical-admission field value length"
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


PLAN_V4_FIELDS = (
    "seed", "topology", "support", "layout", "routing", "physicalPolicy",
    "fault", "packages", "main", "regulator", "coil", "load", "channels",
    "sensors", "sensorPullDowns", "loadReference")
PLAN_V4_TOPOLOGY = re.compile(
    r"^RB30_CH([12])_(SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_"
    r"A_(BJT|NMOS)(?:_B_(BJT|NMOS))?$")
SUPPORT_V4 = re.compile(
    r"^C12:(true|false),S5:(true|false),S12:(true|false),"
    r"filters:(0|[1-3]),outputIndicators:(0|[1-3]),bleeder5:(true|false)$")


def profile_for_channels(channels, plan_version=3):
    """Return the independently fixed state/sample/fault oracle for a scale."""
    assert plan_version in (2, 3, 4), "unsupported Q30 plan profile version"
    if channels == 1:
        faults = SINGLE_CHANNEL_FAULTS
        states = SINGLE_CHANNEL_STATES
        points = SINGLE_CHANNEL_POINTS
        probes = SINGLE_CHANNEL_POINT_PROBES
    elif channels == 2:
        faults = FAULTS
        states = STATES
        points = POINTS
        probes = POINT_PROBES
    else:
        raise AssertionError("unsupported Q30 channel count")
    ids = [f"SENSORS_{state}_{point}" for state, _, _ in states
           for point in points] + ["MAIN_12V"]
    return {"channels": channels, "faults": faults, "states": states,
            "points": points, "probes": probes, "ids": ids,
            "planVersion": plan_version,
            "providerId": "rb30-control-diagnostic@" + str(plan_version)}


def _expected_v4_parts(channels, arrangement, driver_a, driver_b, support):
    """Build the strict physical roster from the frozen normal-board grammar."""
    parts = {}

    def add(component, kind, package, terminals):
        assert component not in parts, "duplicate component in independent board grammar"
        parts[component] = (kind, package, dict(terminals))

    add("J1", "CONNECTOR", "THROUGH_HOLE_CONNECTOR_2",
        {"1": "RAW12", "2": "CTRL_RETURN"})
    add("F1", "FUSE", "AXIAL_FUSE", {"1": "RAW12", "2": "FUSED12"})
    add("DREV", "DIODE", "AXIAL_DIODE", {"A": "FUSED12", "K": "RAIL12"})
    add("U1", "REGULATOR", "TO220_REGULATOR_4", {
        "INPUT": "RAIL12", "OUTPUT": "RAIL5", "RETURN": "CTRL_RETURN",
        "ENABLE": "EN5"})
    add("CIN", "CAPACITOR", "RADIAL_ELECTROLYTIC_CAPACITOR",
        {"+": "RAIL12", "-": "CTRL_RETURN"})
    add("C5", "CAPACITOR", "RADIAL_CERAMIC_CAPACITOR",
        {"1": "RAIL5", "2": "CTRL_RETURN"})
    add("REN", "RESISTOR", "AXIAL_RESISTOR", {"1": "RAIL12", "2": "EN5"})
    add("JLOAD", "CONNECTOR", "THROUGH_HOLE_CONNECTOR_2",
        {"1": "LOAD12", "2": "LOAD_RETURN"})
    if support["C12"]:
        add("C12", "CAPACITOR", "RADIAL_CERAMIC_CAPACITOR",
            {"1": "FUSED12", "2": "CTRL_RETURN"})
    if support["bleeder5"]:
        add("RBLEED5", "RESISTOR", "AXIAL_RESISTOR",
            {"1": "RAIL5", "2": "CTRL_RETURN"})

    channel_names = ("A", "B")[:channels]
    for channel in channel_names:
        reference = "REF_SHARED" if arrangement != "SEPARATE_DIRECT" else channel + "_REF"
        add("JS" + channel, "CONNECTOR", "THROUGH_HOLE_CONNECTOR_2",
            {"1": channel + "_RAW", "2": "CTRL_RETURN"})
        add("JO" + channel, "OUTPUT_HEADER", "THROUGH_HOLE_OUTPUT_HEADER_2",
            {"1": "OUT_" + channel, "2": "LOAD_RETURN"})
        add("U2" + channel, "SENSOR_CONTROL", "E04_DECISION_CONTROL_5", {
            "SENSOR": channel + "_SENSE", "REFERENCE": reference,
            "RAIL": "RAIL5", "OUTPUT": channel + "_CMD",
            "RETURN": "CTRL_RETURN"})
        add("RS" + channel, "RESISTOR", "AXIAL_RESISTOR",
            {"1": channel + "_RAW", "2": channel + "_SENSE"})
        add("RPIN_" + channel, "RESISTOR", "AXIAL_RESISTOR",
            {"1": channel + "_RAW", "2": "CTRL_RETURN"})
        if support["filters"] & (1 if channel == "A" else 2):
            add("CFLT_" + channel, "CAPACITOR", "RADIAL_CERAMIC_CAPACITOR",
                {"1": channel + "_SENSE", "2": "CTRL_RETURN"})

        add("RD" + channel, "RESISTOR", "AXIAL_RESISTOR",
            {"1": channel + "_CMD", "2": channel + "_DRIVE"})
        add("RPD" + channel, "RESISTOR", "AXIAL_RESISTOR",
            {"1": channel + "_DRIVE", "2": "CTRL_RETURN"})
        driver = driver_a if channel == "A" else driver_b
        if driver == "BJT":
            q_kind, q_package, q_terminals = "BJT", "TO92_NPN", ("B", "C", "E")
        else:
            q_kind, q_package, q_terminals = "NMOS", "TO92_NMOS", ("G", "D", "S")
        add("Q" + channel, q_kind, q_package, {
            q_terminals[0]: channel + "_DRIVE",
            q_terminals[1]: channel + "_COIL_LOW",
            q_terminals[2]: "CTRL_RETURN"})
        add("D" + channel, "DIODE", "AXIAL_DIODE",
            {"A": channel + "_COIL_LOW", "K": "RAIL5"})
        add("K" + channel, "RELAY", "RELAY_SPDT", {
            "A1": "RAIL5", "A2": channel + "_COIL_LOW", "COM": "LOAD12",
            "NC": "NC_" + channel, "NO": "OUT_" + channel})

    if arrangement == "SEPARATE_DIRECT":
        for channel in channel_names:
            add("RREF_H" + channel, "RESISTOR", "AXIAL_RESISTOR",
                {"1": "RAIL5", "2": channel + "_REF"})
            add("RREF_L" + channel, "RESISTOR", "AXIAL_RESISTOR",
                {"1": channel + "_REF", "2": "CTRL_RETURN"})
    else:
        add("RREF_H", "RESISTOR", "AXIAL_RESISTOR",
            {"1": "RAIL5", "2": "REF_SHARED"})
        add("RREF_L", "RESISTOR", "AXIAL_RESISTOR",
            {"1": "REF_SHARED", "2": "CTRL_RETURN"})
        if arrangement == "SHARED_HYSTERETIC":
            for channel in channel_names:
                add("RFB_" + channel, "RESISTOR", "AXIAL_RESISTOR",
                    {"1": channel + "_CMD", "2": channel + "_SENSE"})

    if support["S5"]:
        add("RLED", "RESISTOR", "AXIAL_RESISTOR",
            {"1": "RAIL5", "2": "LED_FEED"})
        add("LED1", "LED", "THROUGH_HOLE_LED",
            {"A": "LED_FEED", "K": "CTRL_RETURN"})
    if support["S12"]:
        add("RLED12", "RESISTOR", "AXIAL_RESISTOR",
            {"1": "RAIL12", "2": "LED12_FEED"})
        add("LED12", "LED", "THROUGH_HOLE_LED",
            {"A": "LED12_FEED", "K": "CTRL_RETURN"})
    for channel in channel_names:
        bit = 1 if channel == "A" else 2
        if support["outputIndicators"] & bit:
            feed = "LED_OUT_" + channel + "_FEED"
            add("RLEDOUT_" + channel, "RESISTOR", "AXIAL_RESISTOR",
                {"1": "OUT_" + channel, "2": feed})
            add("LEDOUT_" + channel, "LED", "THROUGH_HOLE_LED",
                {"A": feed, "K": "LOAD_RETURN"})

    references = (2 * channels if arrangement == "SEPARATE_DIRECT" else
                  2 if arrangement == "SHARED_DIRECT" else 2 + channels)
    expected_count = (8 + 10 * channels + references +
                      int(support["C12"]) + 2 * int(support["S5"]) +
                      2 * int(support["S12"]) +
                      sum(bool(support["filters"] & bit) for bit in (1, 2)) +
                      2 * sum(bool(support["outputIndicators"] & bit)
                              for bit in (1, 2)) + int(support["bleeder5"]))
    assert len(parts) == expected_count, "independent Q30 physical package formula disagrees with roster"
    return parts


POWER_DOMAIN_CONTRACT_VERSION = "1"
POWER_DOMAIN_CONTRACT_DESIGN = "RB30_MULTI_RAIL"
LEGACY_POWER_DOMAIN_CONTRACT_DESIGN = (
    "none:BoardPowerController-and-GeneratedExternalPowerBindings-only")


def _power_contract_token(value):
    """Match PowerDomainContract.token for its ASCII canonical identifiers."""
    if value is None:
        return "-1:;"
    assert isinstance(value, str) and value.isascii(), \
        "independent power-contract tokens must be ASCII strings"
    return str(len(value)) + ":" + value + ";"


def _power_double_bits(value):
    return format(struct.unpack(">Q", struct.pack(">d", float(value)))[0], "x")


def _expected_v4_power_rail_record(net, reference, storage):
    return "".join(_power_contract_token(value) for value in
                   ("rail", net, reference, storage))


def _expected_v4_power_source_record(source, rail, volts):
    envelope = "KNOWN:{}:{}".format(_power_double_bits(0.0), _power_double_bits(volts))
    capacity = "KNOWN:" + _power_double_bits(.25)
    resistance = "KNOWN:" + _power_double_bits(.05)
    current_limit = "KNOWN:" + _power_double_bits(.25)
    return "".join(_power_contract_token(value) for value in
                   ("source", source, rail, envelope, capacity, resistance,
                    current_limit, "RESISTIVE_SOURCE"))


def expected_v4_power_contract(profile):
    """Derive the exact RB30_MULTI_RAIL canonical from the validated v4 board plan."""
    assert isinstance(profile, dict) and profile.get("planVersion") == 4, \
        "RB30 power contract expectation requires the validated plan@4 profile"
    channels = profile.get("channels")
    assert type(channels) is int and channels in (1, 2), \
        "RB30 power contract profile has an invalid channel count"
    support = profile.get("support")
    arrangement = profile.get("arrangement")
    driver_a = profile.get("driverA")
    driver_b = profile.get("driverB")
    assert isinstance(support, dict) and arrangement in (
        "SEPARATE_DIRECT", "SHARED_DIRECT", "SHARED_HYSTERETIC") and \
        driver_a in ("BJT", "NMOS") and \
        (channels == 1 and driver_b is None or channels == 2 and driver_b in ("BJT", "NMOS")), \
        "RB30 power contract profile lacks its validated board grammar"
    parts = _expected_v4_parts(channels, arrangement, driver_a, driver_b, support)
    nets = {net for _, _, terminals in parts.values() for net in terminals.values()}
    assert {"CTRL_RETURN", "LOAD_RETURN"}.issubset(nets), \
        "RB30 physical grammar omits a declared power reference"

    rail_records = {}
    for net in sorted(nets - {"CTRL_RETURN", "LOAD_RETURN"}):
        load_rail = (net == "LOAD12" or net.startswith("OUT_") or
                     net.startswith("NC_") or net.startswith("LED_OUT_"))
        storage_required = net in {"FUSED12", "RAIL12", "RAIL5"}
        for channel in ("A", "B")[:channels]:
            bit = 1 if channel == "A" else 2
            if support["filters"] & bit and net == channel + "_SENSE":
                storage_required = True
        reference = "LOAD_RETURN" if load_rail else "CTRL_RETURN"
        storage = "OBSERVATION_REQUIRED" if storage_required else "NONE"
        rail_records[net] = _expected_v4_power_rail_record(net, reference, storage)

    source_records = {
        "MAIN12": _expected_v4_power_source_record("MAIN12", "RAW12", 12.0),
        "LOAD12": _expected_v4_power_source_record("LOAD12", "LOAD12", 12.0),
    }
    for channel in ("A", "B")[:channels]:
        source = "SENSOR_" + channel
        source_records[source] = _expected_v4_power_source_record(
            source, channel + "_RAW", 5.0)

    canonical = "power-domain/1;" + _power_contract_token(POWER_DOMAIN_CONTRACT_DESIGN)
    for reference, isolation in (("CTRL_RETURN", "CONTROL"),
                                 ("LOAD_RETURN", "LOAD")):
        canonical += "".join(_power_contract_token(value) for value in
                             ("reference", reference, isolation, None, "false"))
    canonical += "".join(rail_records[net] for net in sorted(rail_records))
    canonical += "".join(source_records[source] for source in sorted(source_records))
    return {
        "version": POWER_DOMAIN_CONTRACT_VERSION,
        "design": POWER_DOMAIN_CONTRACT_DESIGN,
        "canonical": canonical,
        "railRecords": rail_records,
        "sourceRecords": source_records,
    }


def validate_v4_power_contract(context, profile):
    """Reject legacy/foreign power identities and bind the exact plan-derived contract."""
    expected = expected_v4_power_contract(profile)
    assert context.get("power.contract.version") == expected["version"], \
        "plan@4 trusted context must bind power-domain contract version 1"
    assert context.get("power.contract.design") == expected["design"], \
        "plan@4 trusted context must bind RB30_MULTI_RAIL power design"
    assert context.get("power.contract.canonical") == expected["canonical"], \
        "plan@4 power-domain canonical differs from independent rails/sources/storage contract"
    return expected


def run_v4_power_contract_selftests():
    """Exercise plan-derived contexts, including FUSED12 with C12 absent."""
    profile = profile_for_channels(1, plan_version=4)
    profile.update({
        "arrangement": "SEPARATE_DIRECT",
        "driverA": "BJT",
        "driverB": None,
        "support": {"C12": False, "S5": False, "S12": False,
                    "filters": 0, "outputIndicators": 0, "bleeder5": False},
    })
    expected = expected_v4_power_contract(profile)
    baseline = {"power.contract.version": expected["version"],
                "power.contract.design": expected["design"],
                "power.contract.canonical": expected["canonical"]}
    validate_v4_power_contract(baseline, profile)
    fused_record = expected["railRecords"].get("FUSED12")
    assert fused_record is not None and "OBSERVATION_REQUIRED" in fused_record and \
        not profile["support"]["C12"], \
        "synthetic no-C12 plan must retain FUSED12 storage obligation"
    two_channel_profile = profile_for_channels(2, plan_version=4)
    two_channel_profile.update({
        "arrangement": "SHARED_DIRECT",
        "driverA": "BJT",
        "driverB": "NMOS",
        "support": {"C12": False, "S5": False, "S12": False,
                    "filters": 0, "outputIndicators": 0, "bleeder5": False},
    })
    two_channel_expected = expected_v4_power_contract(two_channel_profile)
    assert {"SENSOR_A", "SENSOR_B"}.issubset(two_channel_expected["sourceRecords"]), \
        "synthetic two-channel plan must declare a source for every active sensor input"
    cases = []

    stale_none = dict(baseline)
    stale_none.update({"power.contract.version": "NONE",
                       "power.contract.design": LEGACY_POWER_DOMAIN_CONTRACT_DESIGN,
                       "power.contract.canonical": "NONE"})
    cases.append(("stale-NONE-power-contract", stale_none, profile))

    wrong_return = dict(baseline)
    wrong_return["power.contract.canonical"] = expected["canonical"].replace(
        expected["railRecords"]["OUT_A"],
        _expected_v4_power_rail_record("OUT_A", "CTRL_RETURN", "NONE"), 1)
    cases.append(("wrong-load-rail-return-reference", wrong_return, profile))

    wrong_storage = dict(baseline)
    wrong_storage["power.contract.canonical"] = expected["canonical"].replace(
        fused_record, _expected_v4_power_rail_record(
            "FUSED12", "CTRL_RETURN", "NONE"), 1)
    cases.append(("FUSED12-storage-required-without-C12", wrong_storage, profile))

    missing_rail = dict(baseline)
    missing_rail["power.contract.canonical"] = expected["canonical"].replace(
        expected["railRecords"]["RAIL12"], "", 1)
    cases.append(("missing-required-rail", missing_rail, profile))

    missing_source = dict(baseline)
    missing_source["power.contract.canonical"] = expected["canonical"].replace(
        expected["sourceRecords"]["SENSOR_A"], "", 1)
    cases.append(("missing-active-sensor-source", missing_source, profile))
    missing_channel_b = {
        "power.contract.version": two_channel_expected["version"],
        "power.contract.design": two_channel_expected["design"],
        "power.contract.canonical": two_channel_expected["canonical"].replace(
            two_channel_expected["sourceRecords"]["SENSOR_B"], "", 1),
    }
    cases.append(("missing-active-channel-B-source", missing_channel_b,
                  two_channel_profile))

    results = [{"name": "synthetic-v4-power-contract-accepted", "status": "PASS"}]
    for name, changed, case_profile in cases:
        try:
            validate_v4_power_contract(changed, case_profile)
        except AssertionError:
            results.append({"name": name, "status": "PASS"})
        else:
            raise AssertionError("v4 power-contract self-test did not reject " + name)
    return results


def _validate_v4_physical_board(admission_canonical, expected_parts):
    records = _admission_component_records(admission_canonical)
    actual_parts = {}
    pads = {}
    nets = {}
    power_inputs = []
    placement_parts = []
    for name, value in records:
        if name == "component":
            assert value not in actual_parts, "physical board repeats a component ID"
            actual_parts[value] = {}
        elif name == "pad":
            pieces = value.split("|")
            assert len(pieces) == 4, "malformed physical board pad declaration"
            pad_id, component, terminal, net = pieces
            assert pad_id == component + "." + terminal and pad_id not in pads, \
                "physical board repeats or misnames a pad"
            pads[pad_id] = (component, terminal, net)
        elif name == "net":
            pieces = value.split("|", 2)
            assert len(pieces) == 3 and pieces[0] not in nets, \
                "malformed or duplicate physical board net declaration"
            net_id, role, encoded_members = pieces
            members = []
            for key_name, member in _parse_length_records(encoded_members):
                assert key_name == "value" and member, "malformed physical net member"
                members.append(member)
            assert len(members) == len(set(members)), "physical net repeats a pad"
            nets[net_id] = (role, set(members))
        elif name == "power-input":
            power_inputs.append(value)
        elif name == "placement-part":
            pieces = value.split("|")
            assert pieces and pieces[0] not in placement_parts, \
                "physical board repeats a placement part"
            placement_parts.append(pieces[0])
        elif name.startswith("component-") and actual_parts:
            # Component attributes precede pads/nets in the admission grammar.
            # The current component association is recoverable from the record order.
            pass

    # Re-read component attributes with their owning component record. Component
    # records are emitted as contiguous type/marking/package attribute groups.
    component_attributes = {}
    current = None
    for name, value in records:
        if name == "component":
            current = value
            component_attributes[current] = {}
        elif current is not None and name.startswith("component-"):
            component_attributes[current].setdefault(name, []).append(value)
        elif name not in ("component",) and not name.startswith("component-"):
            current = None

    assert set(actual_parts) == set(expected_parts), \
        "physical board component roster disagrees with the plan support grammar"
    expected_pads = {}
    expected_net_members = {}
    for component, (kind, package, terminals) in expected_parts.items():
        attributes = component_attributes[component]
        assert attributes.get("component-type") == [kind] and \
            attributes.get("component-marking") == [component] and \
            attributes.get("component-package") == [package], \
            "physical part type/marking/package disagrees for " + component
        for terminal, net in terminals.items():
            pad_id = component + "." + terminal
            expected_pads[pad_id] = (component, terminal, net)
            expected_net_members.setdefault(net, set()).add(pad_id)
    assert pads == expected_pads, "physical pad/terminal/net map disagrees with the Q30 board grammar"

    channel_names = sorted({component[-1] for component in expected_parts
                            if component.startswith("JS") and component in ("JSA", "JSB")})
    expected_roles = {
        "RAW12": "SUPPLY", "FUSED12": "SUPPLY", "RAIL12": "SUPPLY",
        "EN5": "SUPPLY", "RAIL5": "SUPPLY", "LOAD12": "SUPPLY",
        "CTRL_RETURN": "RETURN", "LOAD_RETURN": "RETURN",
    }
    for channel in channel_names:
        expected_roles.update({
            channel + "_RAW": "CONTROL", channel + "_SENSE": "CONTROL",
            channel + "_CMD": "CONTROL", channel + "_DRIVE": "CONTROL",
            channel + "_COIL_LOW": "HIGH_CURRENT", "OUT_" + channel: "HIGH_CURRENT",
            "NC_" + channel: "SIGNAL",
        })
    if "REF_SHARED" in expected_net_members:
        expected_roles["REF_SHARED"] = "CONTROL"
    for channel in channel_names:
        if channel + "_REF" in expected_net_members:
            expected_roles[channel + "_REF"] = "CONTROL"
    for net in expected_net_members:
        if net.startswith("LED") and net.endswith("_FEED"):
            expected_roles[net] = "SIGNAL"
    assert set(nets) == set(expected_net_members) == set(expected_roles), \
        "physical board net roster disagrees with Q30 channel/support grammar"
    for net, expected_members in expected_net_members.items():
        assert nets[net] == (expected_roles[net], expected_members), \
            "physical board net membership/role disagrees for " + net

    expected_power_inputs = {"MAIN12|J1.1|J1.2|RAW12|CTRL_RETURN",
                             "LOAD12|JLOAD.1|JLOAD.2|LOAD12|LOAD_RETURN"}
    for channel in channel_names:
        expected_power_inputs.add("SENSOR_{}|JS{}.1|JS{}.2|{}_RAW|CTRL_RETURN".format(
            channel, channel, channel, channel))
    assert len(power_inputs) == len(set(power_inputs)) and \
        set(power_inputs) == expected_power_inputs, \
        "physical power-input declarations disagree with active channel roster"
    assert len(placement_parts) == len(set(placement_parts)) and \
        set(placement_parts) == set(expected_parts), \
        "physical placement roster differs from electrically declared packages"


def _validate_v4_realization_manifest(manifest, seed, topology,
                                      admission_canonical):
    """Validate plan@4 scale, support flags, package count and full board shape."""
    fields = _parse_plan_manifest(manifest, 4, PLAN_V4_FIELDS)
    assert fields["seed"] == seed, "plan@4 seed mismatch"
    match = PLAN_V4_TOPOLOGY.fullmatch(fields["topology"])
    assert match, "plan@4 topology is outside the one/two-channel normal grammar"
    channels, arrangement, driver_a, driver_b = match.groups()
    channels = int(channels)
    assert (channels == 1 and driver_b is None) or \
        (channels == 2 and driver_b in ("BJT", "NMOS")), \
        "plan@4 topology driver roster disagrees with channel count"
    assert fields["channels"] == str(channels) and \
        fields["sensors"] == str(channels) + "-E04-decisions", \
        "plan@4 channel/sensor counts disagree with topology"
    assert fields["physicalPolicy"] == "MEDIUM_BOARD@1"
    assert fields["main"] == "12V" and fields["regulator"] == "5V-E02" and \
        fields["coil"] == "5V-E03"
    assert fields["load"] == "isolated-12V-180ohm-per-channel" and \
        fields["loadReference"] == "isolated"

    support_match = SUPPORT_V4.fullmatch(fields["support"])
    assert support_match, "plan@4 support feature grammar is malformed"
    c12, s5, s12, filters, outputs, bleeder = support_match.groups()
    support = {"C12": c12 == "true", "S5": s5 == "true", "S12": s12 == "true",
               "filters": int(filters), "outputIndicators": int(outputs),
               "bleeder5": bleeder == "true"}
    expanded_topology = (fields["topology"] + "_C12" + ("_Y" if support["C12"] else "_N") +
                         "_S5" + ("_Y" if support["S5"] else "_N") +
                         "_S12" + ("_Y" if support["S12"] else "_N") +
                         "_F" + str(support["filters"]) +
                         "_O" + str(support["outputIndicators"]) +
                         "_B5" + ("_Y" if support["bleeder5"] else "_N"))
    assert expanded_topology == topology, \
        "plan@4 support vector/full topology differs from its owner"
    assert channels == 2 or (support["filters"] & 2) == 0 and \
        (support["outputIndicators"] & 2) == 0, \
        "plan@4 support mask names absent channel B"
    channel_names = ("A", "B")[:channels]
    expected_pull_downs = ",".join(
        "RPIN_{}:1000ohm({}_RAW,CTRL_RETURN)".format(channel, channel)
        for channel in channel_names)
    assert fields["sensorPullDowns"] == expected_pull_downs, \
        "plan@4 raw sensor pull-down roster differs from active channels"

    reference_count = (2 * channels if arrangement == "SEPARATE_DIRECT" else
                       2 if arrangement == "SHARED_DIRECT" else 2 + channels)
    package_count = (8 + 10 * channels + reference_count + int(support["C12"]) +
                     2 * int(support["S5"]) + 2 * int(support["S12"]) +
                     sum(bool(support["filters"] & bit) for bit in (1, 2)) +
                     2 * sum(bool(support["outputIndicators"] & bit)
                             for bit in (1, 2)) + int(support["bleeder5"]))
    assert fields["packages"] == str(package_count), \
        "plan@4 package count disagrees with independent support accounting"
    assert fields["fault"] in profile_for_channels(channels)["faults"], \
        "plan@4 selected fault is outside the active channel fault census"

    expected_parts = _expected_v4_parts(
        channels, arrangement, driver_a, driver_b, support)
    _validate_v4_physical_board(admission_canonical, expected_parts)
    profile = profile_for_channels(channels, plan_version=4)
    profile.update({"arrangement": arrangement,
                    "driverA": driver_a, "driverB": driver_b,
                    "support": support, "packageCount": package_count,
                    "fault": fields["fault"], "template": "RB30_CHANNEL_INPUT_SWEEP_V1"})
    return profile


def validate_current_realization_manifest(manifest, seed, topology,
                                          admission_canonical):
    """Bind provider@3 support choice and package count to the actual board."""
    if isinstance(manifest, str) and manifest.startswith("rb30-plan@4;"):
        return _validate_v4_realization_manifest(
            manifest, seed, topology, admission_canonical)
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


def _validate_q30_program(program_identity, legacy_fixed_waits, profile=None):
    if profile is None:
        profile = profile_for_channels(2, plan_version=3)
    states = profile["states"]
    points = profile["points"]
    probes = profile["probes"]
    ids = profile["ids"]
    if profile.get("planVersion") == 4:
        assert _program_template(program_identity) == profile["template"], \
            "plan@4 diagnostic template identity changed"
    steps = _program_steps(program_identity)
    expected_count = len(states) * (2 + len(points)) + 3 + (5 if legacy_fixed_waits else 0)
    assert len(steps) == expected_count, "Q30 diagnostic program step census changed"
    measurements = [step for step in steps if step[0] == "DC_VOLTAGE"]
    assert [step[1] for step in measurements] == ids, \
        "actual program lost/reordered its scale-specific measurements"
    expected_bindings = {}
    for state, _, _ in states:
        for point, point_probes in probes.items():
            sample_id = f"SENSORS_{state}_{point}"
            expected_bindings[sample_id] = point_probes
            require_program_binding(program_identity, sample_id, *point_probes)
    expected_bindings["MAIN_12V"] = ("J1.1", "J1.2")
    require_program_binding(program_identity, "MAIN_12V", "J1.1", "J1.2")
    for sample_id, step in zip(ids, measurements):
        assert step[2:6] == (expected_bindings[sample_id][0], expected_bindings[sample_id][1], None, "0"), \
            "wrong DC probe binding: " + sample_id

    expected_steps = []
    for state, _, _ in states:
        expected_steps.append(("INPUT", "SENSORS_" + state, None, None, None, "0"))
        expected_steps.append(("WAIT", state + "_INPUT_SETTLED", None, None, None, "0"))
        for point, point_probes in probes.items():
            expected_steps.append(("DC_VOLTAGE", "SENSORS_" + state + "_" + point,
                                   point_probes[0], point_probes[1], None, "0"))
    expected_steps.append(("DC_VOLTAGE", "MAIN_12V", "J1.1", "J1.2", None, "0"))
    expected_steps.append(("POWER", "BOARD_POWER_OFF_SERVICE", None, None, "UNPOWERED", "0"))
    if legacy_fixed_waits:
        for _ in range(5):
            expected_steps.append(("WAIT", "SERVICE_DISCHARGE", None, None, None,
                                   "3fa999999999999a"))
    expected_steps.append(("SETTLE", None, None, None, None, "0"))
    assert steps == expected_steps, "Q30 diagnostic program step order/service recipe changed"

    input_steps = [step[1] for step in steps if step[0] == "INPUT"]
    assert input_steps == ["SENSORS_" + state for state, _, _ in states]
    condition_waits = [step[1] for step in steps if step[0] == "WAIT" and
                       step[1] != "SERVICE_DISCHARGE"]
    assert condition_waits == [state + "_INPUT_SETTLED" for state, _, _ in states]
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


FRESH_GENERATED_OWNER_COLD_V1 = (
    "owner=new-generated-board;graph=fresh;solver.t=0;solver.timeStepCount=0;"
    "solver.timeStepAccum=0;eventQueue=empty;requiresAnalysis=true")


def _validate_v4_temporal_recipe(context, profile, topology, seed):
    channels = profile["channels"]
    states = [state for state, _, _ in profile["states"]]
    work_units = 3 if channels == 1 else 5
    recipe_epoch, recipe = _framed_fields(
        context.get("temporal.contract.cache-recipe"))
    assert recipe_epoch == "generated-temporal-recipe-v1"
    active_channels = "A" if channels == 1 else "A,B"
    outputs = ";".join("JO{}.1-JO{}.2".format(channel, channel)
                       for channel in active_channels.split(","))
    if channels == 1:
        input_recipe = "LOW-HIGH-restore-inputs"
        fault_preparation = "healthy-two-conditions-then-LOW-apply-fault-then-HIGH"
    else:
        input_recipe = "LOW-A_ONLY-B_ONLY-HIGH-restore-inputs"
        fault_preparation = "healthy-four-conditions-then-LOW-apply-fault-then-HIGH"
    expected = {
        "behavior.id": "RB30_CHANNEL_FUNCTION",
        "behavior.version": "3",
        "initial-state.contract": FRESH_GENERATED_OWNER_COLD_V1,
        "endpoint.output": "JOA.1",
        "endpoint.ground": "JOA.2",
        "parameter.active-channels": active_channels,
        "parameter.condition-count": str(len(states)),
        "parameter.customer-retest-work-units": str(work_units),
        "parameter.fault-preparation": fault_preparation,
        "parameter.off-maximum-volts": "0.05",
        "parameter.on-range-volts": "10.8..12.6",
        "parameter.outputs": outputs,
        "parameter.physical-policy": "MEDIUM_BOARD@1",
        "parameter.profile-work-units": str(work_units),
        "parameter.qualification-maximum-step-seconds": "5.0E-6",
        "parameter.qualification-minimum-step-seconds": "5.0E-11",
        "parameter.qualification-solver": "CircuitJS-adaptive",
        "parameter.rail-range-volts": "4.75..5.25",
        "parameter.recipe": input_recipe,
        "parameter.sample-seconds": "0.03",
        "parameter.seed": seed,
        "parameter.topology": topology,
    }
    assert recipe == expected, "plan@4 temporal recipe fields differ from independent channel contract"
    assert context.get("temporal.contract.initial-state") == FRESH_GENERATED_OWNER_COLD_V1
    return work_units, work_units


def validate_q30_diagnostic_context(context_canonical, seed, topology,
                                    request_manifest=None):
    """Validate common trusted context bindings and return its independent scale profile.

    The result is ``(context_fields, profile)``. The profile is the channel-aware
    v4 oracle when present; historical v3 plan context maps to the fixed
    two-channel profile. Callers should still validate their own request schema.
    """
    context_epoch, context = _framed_fields(context_canonical, context=True)
    assert context_epoch == "tsj-generation-dependencies-v16"
    assert context.get("epoch.circuit-dump") == MODEL_DUMP_EPOCH
    assert context.get("epoch.temporal-context") == "recipe-cache-plus-raw-owner-v1"
    assert context.get("request.manifest"), "trusted context omits its generation request"
    if request_manifest is not None:
        assert context.get("request.manifest") == request_manifest
    assert context.get("board.family") == "RB30_CONTROL" and \
        context.get("board.seed") == seed and context.get("board.topology") == topology
    assert context.get("physical.admission.identity") == "MEDIUM_BOARD_NORMAL@1"
    assert context.get("diagnostic.service-preparation.policy") == SERVICE_PREPARATION_POLICY
    manifest = context.get("realization.manifest")
    assert isinstance(manifest, str) and manifest
    current_profile = validate_current_realization_manifest(
        manifest, seed, topology, context.get("physical.admission.canonical"))
    if isinstance(current_profile, dict) and current_profile.get("planVersion") == 4:
        validate_v4_power_contract(context, current_profile)
    profile = current_profile if isinstance(current_profile, dict) else profile_for_channels(
        2, plan_version=3)
    return context, profile


def validate_q30_work_contract(report, seed, expected_explicit_completion=False):
    """Validate historical receipts or derive scale-aware proof work."""
    has_context = "contextCanonical" in report
    has_completion = "explicitCompletion" in report
    assert has_context == has_completion, "partial temporal-contract metadata"
    if not has_context:
        # Preserve the audited provider@2 compiled evidence format. This exact
        # count is historical evidence only; new receipts must publish context.
        assert expected_explicit_completion is False
        assert report["providerId"] == "rb30-control-diagnostic@2"
        _validate_q30_program(report["programIdentity"], legacy_fixed_waits=True)
        validate_legacy_realization_manifest(
            report["realizationManifest"], seed, report["topology"])
        assert report["proofUnits"] == 315 and report["cold"]["workUnits"] == 315, \
            "known provider@2 Q30 receipt work census changed"
        return 315, "provider@2", profile_for_channels(2, plan_version=2)

    assert report["explicitCompletion"] is expected_explicit_completion, \
        "D01/normal proof has the wrong completion boundary"
    context, profile = validate_q30_diagnostic_context(
        report["contextCanonical"], seed, report["topology"],
        report.get("requestManifest"))
    realization_manifest = context["realization.manifest"]
    if "realizationManifest" in report:
        assert report["realizationManifest"] == realization_manifest, \
            "report plan differs from trusted dependency context"
    if "family" in report:
        assert report["family"] == "RB30_CONTROL"
    if profile.get("planVersion") == 4:
        assert report["providerId"] == "rb30-control-diagnostic@4", \
            "current scale receipt has an unrecognized diagnostic provider"
        assert context.get("diagnostic.provider") == report["providerId"]
        assert context.get("diagnostic.plan.template") == "RB30_CHANNEL_INPUT_SWEEP_V1"
        assert context.get("temporal.contract.id") == "RB30_CHANNEL_FUNCTION"
        assert context.get("temporal.contract.version") == "3"
        profile_units, retest_units = _validate_v4_temporal_recipe(
            context, profile, report["topology"], seed)
        steps = _validate_q30_program(
            report["programIdentity"], legacy_fixed_waits=False, profile=profile)
        work_version = "plan@4"
    else:
        assert report["providerId"] == "rb30-control-diagnostic@3", \
            "historical current-scale receipt has an unrecognized diagnostic provider"
        assert context.get("temporal.contract.id") == "RB30_TWO_CHANNEL_FUNCTION"
        assert context.get("temporal.contract.version") == "2"
        steps = _validate_q30_program(report["programIdentity"], legacy_fixed_waits=False)
        recipe_epoch, recipe = _framed_fields(context["temporal.contract.cache-recipe"])
        assert recipe_epoch == "generated-temporal-recipe-v1"
        profile_units = int(recipe["parameter.profile-work-units"])
        retest_units = int(recipe["parameter.customer-retest-work-units"])
        assert profile_units == retest_units == 5, \
            "historical Q30 temporal work contract must declare five-unit slices"
        work_version = "v2"

    # The independent formula includes the service-readiness cursor and the
    # false direct-completion boundary used by this receipt reader.
    per_hypothesis = (11 + len(steps) + 3 * (profile_units - 1) +
                      (retest_units - 1) +
                      (SERVICE_PREPARATION_UNITS - 1))
    if not expected_explicit_completion:
        per_hypothesis += profile_units - 1
    expected = len(profile["faults"]) * per_hypothesis
    expected_scale_work = (230 if profile["channels"] == 1 else 390) \
        if expected_explicit_completion else (240 if profile["channels"] == 1 else 410)
    assert expected == expected_scale_work, "Q30 direct scale/work formula changed"
    reported_work = report.get("proofUnits", report.get("workUnits"))
    assert reported_work == expected, "D01/normal executed work differs from derived scale formula"
    if "cold" in report:
        assert report["cold"]["workUnits"] == expected, \
            "direct D01 cold receipt work differs from derived scale formula"
    return expected, work_version, profile


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


def validate_service_preparation_canaries(evidence, expected_provider="rb30-control-diagnostic@3"):
    """Validate readiness, stale-context and cleanup canaries for the receipt provider."""
    top_keys = (
        "status", "providerId", "policyCanonical", "caseCount", "passedCount",
        "totalSolverAdvances", "healthyProfileAdvanceUnits",
        "serviceCursorWorkUnitsCompleted", "setupElapsedMs", "elapsedMs",
        "cleanupComplete", "cases", "cleanup")
    record = _require_record(evidence, top_keys, "service-preparation canaries")
    assert record["status"] == "PASS"
    assert record["providerId"] == expected_provider
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


def key(fault, channels=None):
    if channels == 1:
        faults = SINGLE_CHANNEL_FAULTS
    elif channels == 2:
        faults = FAULTS
    else:
        faults = dict(SINGLE_CHANNEL_FAULTS)
        faults.update(FAULTS)
    kind, owner = faults[fault]
    fields = ["RB30_CONTROL", kind, owner, fault, "9221120237041090560",
              "9221120237041090560"]  # canonical NaN for non-value-mutation faults
    return "fault-hypothesis-v1|" + "".join(f"{len(v)}:{v}" for v in fields)


def expected_outputs(fault, a_high, b_high, channels=2):
    """Channel response dictated by the physical Q30 fault locus."""
    if channels == 1:
        if fault in ("DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN",
                     "RELAY_A_COIL_OPEN"):
            return False, False
        raise AssertionError("unknown expected single-channel Q30 fault")
    if fault in ("DREV_OPEN", "REN_OPEN"):
        return False, False
    if fault in ("SENSOR_A_OPEN", "DRIVE_A_OPEN"):
        return False, b_high
    if fault == "RELAY_B_COIL_OPEN":
        return a_high, False
    raise AssertionError("unknown expected Q30 fault")


def validate_q30_electrical_census(evidence, profile):
    """Validate ordered measurements and electrical expectations for one proof."""
    faults = profile["faults"]
    channels = profile["channels"]
    sample_ids = profile["ids"]
    states = profile["states"]
    sample_count = len(sample_ids)
    hypothesis_count = len(faults)
    assert isinstance(evidence, list) and len(evidence) == hypothesis_count, \
        "diagnostic evidence does not contain every active hypothesis"
    expected = {key(fault, channels): fault for fault in faults}
    measurements = {}
    seen = set()
    for item in evidence:
        assert isinstance(item, dict), "hypothesis evidence must be an object"
        hypothesis = item["hypothesisKey"]
        assert hypothesis in expected and hypothesis not in seen, \
            "foreign or duplicate diagnostic hypothesis"
        seen.add(hypothesis)
        fault = expected[hypothesis]
        kind, owner = faults[fault]
        assert item["routeId"] == f"RB30_CONTROL/{kind}/{owner}", \
            "hypothesis route does not match its physical fault"
        assert item["admittedCandidateCount"] == hypothesis_count
        assert all(item[name] is True for name in
                   ("repairReachable", "customerRetestPassed", "stateIsolated"))
        assert item["deterministicResult"] == "PASS" and \
            item["deterministicRejectionReason"] == "NONE" and \
            item["equivalentRepairClass"] == "NONE"
        assert item["executedRepairActionIds"] == ["REMOVE", "CATALOG_INSTALL"]
        samples = item["samples"]
        assert item["sampleCount"] == sample_count and isinstance(samples, list) and \
            len(samples) == sample_count and [sample.get("id") for sample in samples] == sample_ids, \
            "hypothesis sample count/order differs from its channel profile"
        measured = {}
        for sample in samples:
            assert sample["outcome"] == "NUMERIC", \
                "sample is not a numeric CircuitJS observation: " + sample["id"]
            value, tolerance = sample["value"], sample["tolerance"]
            assert type(value) in (float, int) and math.isfinite(value)
            assert type(tolerance) in (float, int) and math.isfinite(tolerance)
            assert math.isclose(tolerance, max(.01, .02 * abs(value)),
                                rel_tol=1e-12, abs_tol=1e-12), "changed sample tolerance"
            measured[sample["id"]] = value
        assert 11.4 <= measured["MAIN_12V"] <= 12.6
        for state, _, _ in states:
            rail = measured[f"SENSORS_{state}_RAIL5"]
            if fault in ("DREV_OPEN", "REN_OPEN"):
                assert rail < 4.75, (fault, state, "regulated rail should be absent")
            else:
                assert 4.75 <= rail <= 5.25, (fault, state, "regulated rail")
        for state, a_high, b_high in states:
            output_a, output_b = expected_outputs(fault, a_high, b_high, channels)
            for channel, expected_on in (("A", output_a), ("B", output_b))[:channels]:
                value = measured[f"SENSORS_{state}_OUTPUT_{channel}"]
                assert (10.8 <= value <= 12.6) if expected_on else abs(value) <= .05, \
                    (fault, state, channel, "output differs from electrical expectation")
        measurements[fault] = samples
    assert seen == set(expected), "active hypothesis census is incomplete"
    pairs = 0
    for first, second in itertools.combinations(faults, 2):
        assert any(abs(left["value"] - right["value"]) >
                   left["tolerance"] + right["tolerance"]
                   for left, right in zip(measurements[first], measurements[second])), \
            "indistinguishable faults: {} / {}".format(first, second)
        pairs += 1
    return pairs


def require_integer(value, label):
    assert type(value) is int, label


def validate_warm_and_negative_evidence(report, seed, profile=None):
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
    profile = profile or profile_for_channels(2)
    assert warm["samplePopulationPerHypothesis"] == len(profile["ids"])
    assert warm["hypothesisCount"] == len(profile["faults"])
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


def check_document(document, seeds, require_warm=False, require_current_plan=True):
    """Validate D01 evidence; disabling the v4 guard is historical audit only."""
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
        expected_proof_units, work_contract_version, profile = validate_q30_work_contract(report, seed)
        if require_current_plan:
            assert profile.get("planVersion") == 4 and \
                profile.get("providerId") == "rb30-control-diagnostic@4", \
                "current Q30 D01 reader requires rb30-plan@4/provider@4"
        work_contract_versions.add(work_contract_version)
        if work_contract_version in ("v2", "plan@4"):
            provider = "rb30-control-diagnostic@4" if work_contract_version == "plan@4" \
                else "rb30-control-diagnostic@3"
            validate_service_preparation_canaries(
                report["servicePreparationCanaries"], provider)
        fault_count = len(profile["faults"])
        sample_count = len(profile["ids"])
        channels = profile["channels"]
        assert report["hypothesisCount"] == report["completedHypotheses"] == fault_count
        assert report["totalHypotheses"] == fault_count
        assert report["actualSampleCount"] == fault_count * sample_count
        cold = report["cold"]
        assert cold["status"] == "PASS" and cold["elapsedMillis"] > 0
        assert cold["elapsedMillis"] == report["serviceElapsedMs"]
        assert report["elapsedMs"] >= report["serviceElapsedMs"]
        assert cold["workUnits"] == report["proofUnits"] == expected_proof_units
        audit = cold["cleanupAudit"]
        assert report["cleanupAudit"] == audit, "cleanup audit copies disagree"
        assert audit["disposedHypothesisCount"] == fault_count and audit["disposalFailureCount"] == 0
        expected_power_bindings = channels + 2  # main, load, and one source per active sensor
        assert audit["cleanupAttemptCount"] == fault_count and \
            audit["disconnectedBindingCount"] == fault_count * expected_power_bindings
        assert audit["disposedElementCount"] > 0
        for name in ("lastOwnerGuardPassed", "lastBindingsActuallyDisconnected",
                     "lastElementsActuallyDeleted", "lastGraphDetached", "lastCleanupComplete"):
            assert audit[name] is True, name
        assert audit["lastBindingCount"] == audit["lastDisconnectedBindingCount"] == \
            expected_power_bindings > 0
        assert audit["lastElementCount"] == audit["lastDeletedElementCount"] > 0
        pairs += validate_q30_electrical_census(cold["evidence"], profile)
        if require_warm:
            validate_warm_and_negative_evidence(report, seed, profile)
    assert seen == set(seeds)
    assert len(work_contract_versions) == 1, "mixed temporal work-contract receipt versions"
    return pairs


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("receipt")
    parser.add_argument("--seeds", nargs="+", required=True)
    parser.add_argument("--require-warm", action="store_true",
                        help="also require fresh-owner cache reuse and all negative canaries")
    parser.add_argument("--historical-audit", action="store_true",
                        help="audit retained provider@2/@3 evidence; never an admission result")
    args = parser.parse_args()
    with open(args.receipt, encoding="utf-8") as handle:
        pairs = check_document(json.load(handle), args.seeds, args.require_warm,
                               require_current_plan=not args.historical_audit)
    proof = "cold+warm" if args.require_warm else "cold"
    if args.historical_audit:
        print(f"HISTORICAL AUDIT ONLY: {proof} Q30 D01 seeds={len(args.seeds)} pairs={pairs}; never admission")
    else:
        print(f"PASS: current plan@4/provider@4 genuine {proof} Q30 D01 seeds={len(args.seeds)} pairs={pairs}; player gates separate")
