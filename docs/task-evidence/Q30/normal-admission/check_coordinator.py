"""Independent reader for actual Q30 coordinator cold/warm measurements.

This reader accepts only complete, restored, measurement-only coordinator runs.
It rechecks the actual 5 x 37 CircuitJS diagnostic observations, the unchanged
per-unit/work-count budgets, and value-equivalent warm reuse on a distinct
owner. Its longer developer deadline is not a normal production timing or
admission result. Failed and rejected jobs are counted separately and never
enter successful measurement latency percentiles. The separate
--expect-scope-loss mode validates an intentionally canceled developer-scope
negative receipt; it cannot certify a successful qualification.
"""
import argparse
import copy
import itertools
import json
import math
import re
import sys
from pathlib import Path

# Reuse the fixed Q30 electrical contract already owned by the independent D01
# reader without creating bytecode artifacts beside the evidence scripts.
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_d01 import (  # noqa: E402
    CURRENT_PLAN_FIELDS,
    FAULTS,
    IDS,
    POINT_PROBES,
    STATES,
    _parse_plan_manifest,
    expected_catalog_registered,
    expected_outputs,
    key as hypothesis_key,
    validate_current_realization_manifest,
)


STAGE_NAMES = ("RESOLVE", "HEALTHY", "PHYSICAL", "HYPOTHESES", "SYMPTOM", "PUBLISH")
PHYSICAL_ADMISSION = "MEDIUM_BOARD_NORMAL@1"
PROVIDER_ID = "rb30-control-diagnostic@3"
SERVICE_PREPARATION_POLICY = (
    "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=exact-current-owner")
SERVICE_PREPARATION_UNITS = 5
MODEL_DUMP_EPOCH = "circuitjs-source-load-model-inputs-no-transient-dump-v6"
DIAGNOSTIC_CONTEXT_ENVELOPE = "tsj-d01-diagnostic-context-v3;"
DEFAULT_MAX_JOB_MS = 90000
MEASUREMENT_MAX_JOB_MS = 300000
MAX_UNIT_MS = 5000
MAX_JOB_STEPS = 640
EXPECTED_SAMPLE_COUNT = 37
EXPECTED_COLD_PROOF_TIMING_UNITS = {
    "REPLAY_INSTALL": 5,
    "CANDIDATE_HEALTHY_SETTLE": 25,
    "CANDIDATE_SETTLE": 25,
    "OBSERVATION_STEP:INPUT": 20,
    "OBSERVATION_STEP:WAIT": 20,
    "OBSERVATION_STEP:DC_VOLTAGE": 185,
    "OBSERVATION_STEP:POWER": 5,
    "OBSERVATION_STEP:SETTLE": 5,
    "POWER_OFF_SETTLE": 25,
    "REMOVE_SETTLE": 5,
    "REPLACE_SETTLE": 5,
    "POWER_ON_SETTLE": 5,
    "REPAIR_STATUS_SETTLE": 25,
    "CUSTOMER_RETEST": 25,
    "CUSTOMER_RETEST_COMPLETION": 5,
    "EVIDENCE_RESTORE_PRIMARY": 5,
}


class ReceiptError(Exception):
    """A receipt omitted or contradicted a required independent assertion."""


def need(condition, message):
    if not condition:
        raise ReceiptError(message)


def fields(value, required, label):
    need(isinstance(value, dict), label + " must be an object")
    missing = [name for name in required if name not in value]
    need(not missing, label + " missing fields: " + ", ".join(missing))


def integer(value, label, minimum=None, maximum=None):
    need(type(value) is int, label + " must be an integer")
    if minimum is not None:
        need(value >= minimum, label + " is below its minimum")
    if maximum is not None:
        need(value <= maximum, label + " exceeds its maximum")
    return value


def finite_number(value, label):
    need(type(value) in (int, float) and math.isfinite(value),
         label + " must be a finite number")
    return float(value)


def canonical_seed(value, label):
    need(isinstance(value, str) and re.fullmatch(r"-?(0|[1-9][0-9]*)", value) is not None,
         label + " must be a canonical signed-long string")
    parsed = int(value)
    need(-(2**63) <= parsed < 2**63, label + " is outside signed-long range")
    return parsed


def frame(value):
    return str(java_string_length(value)) + ":" + value


def java_string_length(value):
    """Match Java String.length() when reading/writing canonical frames."""
    return sum(2 if ord(character) > 0xFFFF else 1 for character in value)


def java_string_end(value, start, units):
    need(units >= 0, "negative canonical string length")
    offset = start
    consumed = 0
    while offset < len(value) and consumed < units:
        consumed += 2 if ord(value[offset]) > 0xFFFF else 1
        offset += 1
    need(consumed == units,
         "canonical frame splits a UTF-16 surrogate pair or exceeds its value")
    return offset


def read_plain_frame(value, offset):
    need(offset < len(value), "truncated canonical frame")
    if value.startswith("-1:", offset):
        return None, offset + 3
    colon = value.find(":", offset)
    need(colon > offset and value[offset:colon].isdigit(),
         "malformed canonical frame length")
    size = int(value[offset:colon])
    start = colon + 1
    end = java_string_end(value, start, size)
    return value[start:end], end


def read_context_frame(value, offset):
    need(offset < len(value), "truncated dependency-context frame")
    if value.startswith("N;", offset):
        return None, offset + 2
    need(value.startswith("V", offset), "malformed dependency-context frame")
    result, end = read_plain_frame(value, offset + 1)
    need(end < len(value) and value[end] == ";",
         "dependency-context frame terminator is missing")
    return result, end + 1


def parse_framed_fields(value, context=False):
    """Parse the emitted owner grammar, including counted lists and placement records."""
    need(isinstance(value, str) and value, "canonical field set is empty")
    if context:
        need(value.startswith(DIAGNOSTIC_CONTEXT_ENVELOPE),
             "unsupported D01 diagnostic-context envelope")
        value = value[len(DIAGNOSTIC_CONTEXT_ENVELOPE):]
        epoch, offset = read_context_frame(value, 0)
        need(epoch is not None, "dependency-context interpretation epoch is missing")
    else:
        epoch, offset = read_plain_frame(value, 0)
        need(epoch is not None, "temporal-recipe epoch is missing")
    parsed = {}
    extension_payload_counts = {
        "single-copper-layer": 1,
        "placement": 6,
        "barrier": 3,
    }
    while offset < len(value):
        if context and value[offset] != "F":
            extension, offset = read_context_frame(value, offset)
            need(extension in extension_payload_counts,
                 "unsupported dependency-context extension record")
            for _ in range(extension_payload_counts[extension]):
                _, offset = read_context_frame(value, offset)
            continue
        need(value[offset] == "F", "canonical field record marker is missing")
        offset += 1
        if context:
            field_key, offset = read_context_frame(value, offset)
            field_value, offset = read_context_frame(value, offset)
        else:
            field_key, offset = read_plain_frame(value, offset)
            field_value, offset = read_plain_frame(value, offset)
        need(isinstance(field_key, str) and field_key and field_key not in parsed,
             "canonical field key is missing or duplicated")
        parsed[field_key] = field_value
        # appendStringList emits F, key, decimal count, then that many frames.
        # A following F starts the next field; V/N begins a list/extension frame.
        if context and isinstance(field_value, str) and field_value.isdigit() and \
                offset < len(value) and value[offset] != "F":
            count = int(field_value)
            need(count <= len(value) - offset,
                 "dependency-context list count exceeds its containing value")
            for _ in range(count):
                _, offset = read_context_frame(value, offset)
            need(offset == len(value) or value[offset] in "FVN",
                 "dependency-context list boundary is malformed")
    return epoch, parsed


def replace_recipe_parameter(context_canonical, key, old, new):
    recipe_key = "parameter." + key
    target = "F" + frame(recipe_key) + frame(old)
    replacement = "F" + frame(recipe_key) + frame(new)
    need(context_canonical.count(target) == 1,
         "could not locate unique temporal recipe parameter: " + key)
    return context_canonical.replace(target, replacement, 1)


def replace_context_field(context_canonical, key, old, new):
    target = "F" + "V{}:{};".format(java_string_length(key), key) + \
        "V{}:{};".format(java_string_length(old), old)
    replacement = "F" + "V{}:{};".format(java_string_length(key), key) + \
        "V{}:{};".format(java_string_length(new), new)
    need(context_canonical.count(target) == 1,
         "could not locate unique dependency-context field: " + key)
    return context_canonical.replace(target, replacement, 1)


def parse_program(program_identity):
    prefix = "diagnostic-program-v1"
    need(isinstance(program_identity, str) and program_identity.startswith(prefix),
         "diagnostic program canonical identity is missing or unsupported")
    _, offset = read_plain_frame(program_identity, len(prefix))  # immutable plan identity
    tokens = []
    while offset < len(program_identity):
        token, offset = read_plain_frame(program_identity, offset)
        tokens.append(token)
    need(len(tokens) > 0 and len(tokens) % 6 == 0,
         "diagnostic program has an incomplete step record")
    steps = [tuple(tokens[index:index + 6]) for index in range(0, len(tokens), 6)]
    return steps


def require_program_binding(program_identity, sample_id, red, black):
    binding = (frame("DC_VOLTAGE") + frame(sample_id) + frame(red) + frame(black))
    need(program_identity.count(binding) == 1,
         "program/sample/probe binding mismatch: " + sample_id)


def validate_program(program_identity):
    steps = parse_program(program_identity)
    measured = [step for step in steps if step[0] == "DC_VOLTAGE"]
    need([step[1] for step in measured] == IDS,
         "diagnostic program's exact 37 DC observation order changed")
    expected_bindings = {}
    for state, _, _ in STATES:
        for point, probes in POINT_PROBES.items():
            sample_id = "SENSORS_" + state + "_" + point
            expected_bindings[sample_id] = probes
            require_program_binding(program_identity, "SENSORS_" + state + "_" + point,
                                    probes[0], probes[1])
    require_program_binding(program_identity, "MAIN_12V", "J1.1", "J1.2")
    expected_bindings["MAIN_12V"] = ("J1.1", "J1.2")
    for sample, step in zip(IDS, measured):
        expected_red, expected_black = expected_bindings[sample]
        need(step[2] == expected_red and step[3] == expected_black and
             step[4] is None and step[5] == "0",
             "diagnostic program has an incorrect DC probe binding: " + sample)
    expected_steps = []
    for state, _, _ in STATES:
        expected_steps.append(("INPUT", "SENSORS_" + state, None, None, None, "0"))
        expected_steps.append(("WAIT", state + "_INPUT_SETTLED", None, None, None, "0"))
        for point, probes in POINT_PROBES.items():
            expected_steps.append(("DC_VOLTAGE", "SENSORS_" + state + "_" + point,
                                   probes[0], probes[1], None, "0"))
    expected_steps.append(("DC_VOLTAGE", "MAIN_12V", "J1.1", "J1.2", None, "0"))
    expected_steps.append(("POWER", "BOARD_POWER_OFF_SERVICE", None, None, "UNPOWERED", "0"))
    expected_steps.append(("SETTLE", None, None, None, None, "0"))
    need(steps == expected_steps and len(steps) == 47,
         "diagnostic program differs from the exact 47-step v3 observation/service recipe")
    return len(steps)


def validate_temporal_context(context_canonical, seed, family, topology, request):
    epoch, fields_by_name = parse_framed_fields(context_canonical, context=True)
    need(epoch == "tsj-generation-dependencies-v16",
         "diagnostic context interpretation epoch is not recognized")
    need(fields_by_name.get("epoch.circuit-dump") == MODEL_DUMP_EPOCH,
         "trusted context does not bind the current CircuitJS model-dump epoch v6")
    need(fields_by_name.get("request.manifest") == request,
         "trusted diagnostic context is bound to a different generation request")
    need(fields_by_name.get("epoch.temporal-context") == "recipe-cache-plus-raw-owner-v1",
         "Q30 diagnostic context cache/owner epoch changed unexpectedly")
    need(fields_by_name.get("board.family") == family == "RB30_CONTROL" and
         fields_by_name.get("board.seed") == seed and
         fields_by_name.get("board.topology") == topology,
         "trusted temporal context family/seed/topology does not match its owner")
    realization_manifest = fields_by_name.get("realization.manifest")
    need(isinstance(realization_manifest, str) and realization_manifest,
         "trusted context omits the current Q30 realization manifest")
    try:
        validate_current_realization_manifest(
            realization_manifest, seed, topology,
            fields_by_name.get("physical.admission.canonical"))
    except AssertionError as error:
        raise ReceiptError(str(error)) from error
    need(fields_by_name.get("physical.admission.identity") == PHYSICAL_ADMISSION,
         "trusted context lacks normal-medium physical admission")
    need(fields_by_name.get("temporal.contract.id") == "RB30_TWO_CHANNEL_FUNCTION" and
         fields_by_name.get("temporal.contract.version") == "2",
         "trusted context lacks the recognized Q30 temporal contract v2")
    need(fields_by_name.get("diagnostic.service-preparation.policy") ==
         SERVICE_PREPARATION_POLICY,
         "trusted context lacks the exact current-owner REMOVE readiness policy")
    recipe = fields_by_name.get("temporal.contract.cache-recipe")
    recipe_epoch, recipe_fields = parse_framed_fields(recipe, context=False)
    need(recipe_epoch == "generated-temporal-recipe-v1",
         "Q30 temporal cache recipe encoding is unsupported")
    profile_units = recipe_fields.get("parameter.profile-work-units")
    retest_units = recipe_fields.get("parameter.customer-retest-work-units")
    need(profile_units == "5" and retest_units == "5",
         "Q30 temporal profile/retest work-unit contract must declare 5 each")
    return int(profile_units), int(retest_units), SERVICE_PREPARATION_UNITS


def exact_number(left, right):
    """Compare JSON doubles as Java's sameEvidence compares their bits."""
    return (type(left) in (int, float) and type(right) in (int, float) and
            math.isfinite(left) and math.isfinite(right) and
            float(left).hex() == float(right).hex())


def validate_electrical_evidence(proof, seed, phase, request):
    fields(proof, ("status", "family", "topology", "providerId", "programIdentity",
                   "contextCanonical", "contextTrusted", "explicitCompletion",
                   "partitionCanonical", "partitionNodeCount", "partitionLeafCount",
                   "hypothesisCount", "actualSampleCount", "workUnits", "elapsedMillis",
                   "evidence", "warmReuseReceipt"),
          phase + " diagnostic proof")
    need(proof["status"] == "PASS" and proof["family"] == "RB30_CONTROL",
         phase + " diagnostic proof status or family is invalid")
    need(isinstance(proof["topology"], str) and proof["topology"],
         phase + " diagnostic topology identity is missing")
    need(proof["contextTrusted"] is True,
         phase + " diagnostic context is not a trusted capture")
    need(proof["explicitCompletion"] is True,
         phase + " proof omits the private normal retest completion boundary")
    need(proof["providerId"] == PROVIDER_ID,
         phase + " diagnostic provider identity changed")
    program_step_count = validate_program(proof["programIdentity"])
    need(isinstance(proof["contextCanonical"], str) and proof["contextCanonical"],
         phase + " trusted diagnostic context was not serialized")
    need(isinstance(proof["partitionCanonical"], str) and proof["partitionCanonical"],
         phase + " diagnostic partition was not serialized")
    integer(proof["partitionNodeCount"], phase + " partition node count", 1)
    integer(proof["partitionLeafCount"], phase + " partition leaf count", 1)
    profile_units, customer_retest_units, service_preparation_units = validate_temporal_context(
        proof["contextCanonical"], seed, proof["family"], proof["topology"], request)
    need(proof["hypothesisCount"] == len(FAULTS),
         phase + " diagnostic hypothesis count is not five")
    need(proof["actualSampleCount"] == len(FAULTS) * EXPECTED_SAMPLE_COUNT,
         phase + " diagnostic sample count is not 185")
    per_hypothesis = (11 + program_step_count + 3 * (profile_units - 1) +
                      customer_retest_units - 1 + service_preparation_units - 1)
    if not proof["explicitCompletion"]:
        per_hypothesis += profile_units - 1
    expected_work_units = len(FAULTS) * per_hypothesis
    need(proof["workUnits"] == expected_work_units,
         phase + " full proof work count differs from the v2 temporal contract formula")
    integer(proof["elapsedMillis"], phase + " diagnostic proof elapsed time", 0)
    if phase == "cold":
        need(proof["elapsedMillis"] > 0, "cold proof has no measured solver time")
    else:
        need(proof["elapsedMillis"] == 0,
             "warm cache reuse performed serialized diagnostic solver work")

    expected = {hypothesis_key(name): name for name in FAULTS}
    evidence = proof["evidence"]
    need(isinstance(evidence, list) and len(evidence) == len(FAULTS),
         phase + " must serialize all five actual hypothesis records")
    measurements = {}
    seen = set()
    count_map = {}
    for item in evidence:
        fields(item, ("hypothesisKey", "routeId", "admittedCandidateCount",
                      "repairReachable", "customerRetestPassed", "stateIsolated",
                      "deterministicResult", "deterministicRejectionReason",
                      "equivalentRepairClass", "executedRepairActionIds",
                      "sampleCount", "samples"), phase + " hypothesis evidence")
        key = item["hypothesisKey"]
        need(key in expected, phase + " contains a foreign diagnostic hypothesis")
        need(key not in seen, phase + " contains a duplicate diagnostic hypothesis")
        seen.add(key)
        fault = expected[key]
        kind, owner = FAULTS[fault]
        need(item["routeId"] == "RB30_CONTROL/" + kind + "/" + owner,
             phase + " hypothesis route does not match its physical fault")
        need(item["admittedCandidateCount"] == len(FAULTS),
             phase + " proof was not evaluated against all five candidates")
        for flag in ("repairReachable", "customerRetestPassed", "stateIsolated"):
            need(item[flag] is True, phase + " lacks " + flag + " evidence")
        need(item["deterministicResult"] == "PASS" and
             item["deterministicRejectionReason"] == "NONE" and
             item["equivalentRepairClass"] == "NONE",
             phase + " contains a rejected or ambiguous diagnostic result")
        need(item["executedRepairActionIds"] == ["REMOVE", "CATALOG_INSTALL"],
             phase + " repair action evidence changed")
        need(item["sampleCount"] == EXPECTED_SAMPLE_COUNT,
             phase + " hypothesis does not contain 37 actual samples")
        count_map[key] = item["sampleCount"]
        samples = item["samples"]
        need(isinstance(samples, list) and len(samples) == EXPECTED_SAMPLE_COUNT,
             phase + " hypothesis sample array is incomplete")
        need([sample.get("id") for sample in samples if isinstance(sample, dict)] == IDS,
             phase + " sample identities or declared program order changed")

        measured = {}
        for sample in samples:
            fields(sample, ("id", "outcome", "value", "tolerance"), phase + " sample")
            need(sample["outcome"] == "NUMERIC",
                 phase + " sample is not a numeric CircuitJS observation: " + sample["id"])
            value = finite_number(sample["value"], phase + " sample value " + sample["id"])
            tolerance = finite_number(sample["tolerance"],
                                      phase + " sample tolerance " + sample["id"])
            need(tolerance >= 0 and math.isclose(
                tolerance, max(0.01, 0.02 * abs(value)), rel_tol=1e-12, abs_tol=1e-12),
                phase + " sample comparison tolerance changed: " + sample["id"])
            measured[sample["id"]] = value

        need(11.4 <= measured["MAIN_12V"] <= 12.6,
             phase + " main 12V source observation is out of range")
        for state, _, _ in STATES:
            rail = measured["SENSORS_" + state + "_RAIL5"]
            if fault in ("DREV_OPEN", "REN_OPEN"):
                need(rail < 4.75,
                     phase + " fault should remove the regulated 5V rail")
            else:
                need(4.75 <= rail <= 5.25,
                     phase + " regulated 5V rail is outside its contract")
        for state, a_high, b_high in STATES:
            output_a, output_b = expected_outputs(fault, a_high, b_high)
            for channel, on in (("A", output_a), ("B", output_b)):
                value = measured["SENSORS_" + state + "_OUTPUT_" + channel]
                good = (10.8 <= value <= 12.6) if on else abs(value) <= 0.05
                need(good, phase + " channel " + channel + " output disagrees with fault behavior")
        measurements[fault] = samples

    need(seen == set(expected) and set(count_map) == set(expected) and
         sum(count_map.values()) == proof["actualSampleCount"],
         phase + " exact five-hypothesis / 185-sample census is incomplete")
    pair_count = 0
    for first, second in itertools.combinations(FAULTS, 2):
        separated = False
        for left, right in zip(measurements[first], measurements[second]):
            left_value = finite_number(left["value"], "pairwise left sample")
            right_value = finite_number(right["value"], "pairwise right sample")
            left_tolerance = finite_number(left["tolerance"], "pairwise left tolerance")
            right_tolerance = finite_number(right["tolerance"], "pairwise right tolerance")
            if abs(left_value - right_value) > left_tolerance + right_tolerance:
                separated = True
                break
        need(separated, phase + " has electrically indistinguishable faults " + first + " / " + second)
        pair_count += 1
    return pair_count, expected_work_units


def compare_proofs(cold, warm):
    cold_proof = cold
    warm_proof = warm
    for name in ("family", "topology", "providerId", "programIdentity", "contextCanonical",
                 "contextTrusted", "explicitCompletion", "partitionCanonical", "partitionNodeCount",
                 "partitionLeafCount", "hypothesisCount", "actualSampleCount", "workUnits"):
        need(cold_proof[name] == warm_proof[name], "warm proof changed " + name)
    need(len(cold_proof["evidence"]) == len(warm_proof["evidence"]),
         "warm proof evidence count differs from cold")
    cold_rows = {item["hypothesisKey"]: item for item in cold_proof["evidence"]}
    warm_rows = {item["hypothesisKey"]: item for item in warm_proof["evidence"]}
    need(set(cold_rows) == set(warm_rows), "warm proof hypothesis set differs from cold")
    for key in cold_rows:
        left, right = cold_rows[key], warm_rows[key]
        for name in ("routeId", "admittedCandidateCount", "repairReachable",
                     "customerRetestPassed", "stateIsolated", "deterministicResult",
                     "deterministicRejectionReason", "equivalentRepairClass",
                     "executedRepairActionIds", "sampleCount"):
            need(left[name] == right[name], "warm evidence changed " + name + " for " + key)
        need(len(left["samples"]) == len(right["samples"]),
             "warm evidence sample count changed for " + key)
        for cold_sample, warm_sample in zip(left["samples"], right["samples"]):
            need(cold_sample["id"] == warm_sample["id"] and
                 cold_sample["outcome"] == warm_sample["outcome"],
                 "warm evidence changed sample identity/outcome for " + key)
            need(exact_number(cold_sample["value"], warm_sample["value"]) and
                 exact_number(cold_sample["tolerance"], warm_sample["tolerance"]),
                 "warm evidence changed exact measured value/tolerance for " + key)
    cold_counts = {item["hypothesisKey"]: item["sampleCount"]
                   for item in cold_proof["evidence"]}
    warm_counts = {item["hypothesisKey"]: item["sampleCount"]
                   for item in warm_proof["evidence"]}
    need(cold_counts == warm_counts and
         sum(cold_counts.values()) == cold_proof["actualSampleCount"] ==
         sum(warm_counts.values()) == warm_proof["actualSampleCount"],
         "warm proof per-hypothesis sample counts differ from cold")


def validate_run(run, name, seed, request):
    fields(run, ("run", "outcome", "elapsedMs", "wallElapsedMs", "maxJobMillis", "maxAdvanceMs",
                 "maxManualUnitMs", "manualAdvanceCalls", "totalWork", "hypothesisWork",
                 "routingElapsedMs", "proofElapsedMs", "diagnosticWorkTimings",
                 "proofCacheHitDelta", "proofCacheMissDelta", "proofCacheSize",
                 "planCacheHitDelta", "planCacheMissDelta", "stages", "owner",
                 "cleanupComplete", "ownerRestored", "cleanupElapsedMs",
                 "cleanupRetryCount"), name + " run")
    need(run["run"] == name and run["outcome"] == "PASS",
         name + " coordinator job did not pass")
    job_limit = integer(run["maxJobMillis"], name + " actual job deadline",
                        MEASUREMENT_MAX_JOB_MS, MEASUREMENT_MAX_JOB_MS)
    elapsed = integer(run["elapsedMs"], name + " elapsed time", 1, job_limit)
    wall_elapsed = integer(run["wallElapsedMs"], name + " wall elapsed time", elapsed, job_limit)
    integer(run["maxAdvanceMs"], name + " maximum coordinator advance", 0, MAX_UNIT_MS)
    integer(run["maxManualUnitMs"], name + " maximum manual work unit", 0, MAX_UNIT_MS)
    total_work = integer(run["totalWork"], name + " work count", 1, MAX_JOB_STEPS)
    integer(run["hypothesisWork"], name + " hypothesis work", 1, MAX_JOB_STEPS)
    calls = integer(run["manualAdvanceCalls"], name + " manual advance calls", 1, MAX_JOB_STEPS)
    need(calls == total_work,
         name + " did not advance exactly one coordinator work unit per timer callback")
    for field in ("proofCacheHitDelta", "proofCacheMissDelta", "proofCacheSize",
                  "planCacheHitDelta", "planCacheMissDelta"):
        integer(run[field], name + " " + field, 0)
    need(run["cleanupComplete"] is True and run["ownerRestored"] is True,
         name + " did not complete owner cleanup and exact restoration")
    integer(run["cleanupElapsedMs"], name + " cleanup time", 0)
    need(integer(run["cleanupRetryCount"], name + " cleanup retry count", 0) == 0,
         name + " required an incomplete-owner cleanup retry")
    need(run.get("cleanupFailure") is None,
         name + " reported a cleanup failure")

    stages = run["stages"]
    need(isinstance(stages, list) and len(stages) == len(STAGE_NAMES),
         name + " must publish all six stage timings")
    stage_map = {}
    stage_work = 0
    for stage in stages:
        fields(stage, ("name", "work", "elapsedMs"), name + " stage timing")
        stage_name = stage["name"]
        need(stage_name in STAGE_NAMES and stage_name not in stage_map,
             name + " has an unknown or duplicate stage timing")
        work = integer(stage["work"], name + " " + stage_name + " work", 1)
        integer(stage["elapsedMs"], name + " " + stage_name + " elapsed time", 0)
        stage_map[stage_name] = stage
        stage_work += work
    need(set(stage_map) == set(STAGE_NAMES) and stage_work == total_work,
         name + " six-stage work census does not match total coordinator work")
    routing_elapsed = integer(run["routingElapsedMs"], name + " active routing time", 0)
    need(routing_elapsed <= stage_map["HEALTHY"]["elapsedMs"] and
         routing_elapsed <= elapsed and routing_elapsed <= wall_elapsed,
         name + " routing time exceeds the stage that constructed the layout or the whole job")
    proof_elapsed = integer(run["proofElapsedMs"], name + " proof session elapsed time", 0)
    timing_records = run["diagnosticWorkTimings"]
    need(isinstance(timing_records, list),
         name + " diagnostic step timings must be an array")
    timing_units = {}
    timing_elapsed_sum = 0
    for timing in timing_records:
        fields(timing, ("label", "units", "elapsedMs", "maxUnitMs"),
               name + " diagnostic step timing")
        label = timing["label"]
        need(isinstance(label, str) and label and label not in timing_units,
             name + " diagnostic timing label is empty or duplicated")
        units = integer(timing["units"], name + " timing units " + label, 1)
        elapsed_units = integer(timing["elapsedMs"], name + " timing elapsed " + label, 0)
        max_unit = integer(timing["maxUnitMs"], name + " timing max unit " + label,
                           0, MAX_UNIT_MS)
        need(max_unit <= elapsed_units,
             name + " timing maximum unit exceeds the aggregate duration for " + label)
        timing_units[label] = units
        timing_elapsed_sum += elapsed_units
    if name == "cold":
        need(timing_units == EXPECTED_COLD_PROOF_TIMING_UNITS,
             "cold timing labels/counts differ from the exact 5 x 78 proof-step census")
        need(sum(timing_units.values()) == 390 and proof_elapsed > 0,
             "cold timing evidence does not cover all 390 serial proof steps")
        need(timing_elapsed_sum <= proof_elapsed <= elapsed and proof_elapsed <= wall_elapsed,
             "cold summed step time, yielded proof time, and job elapsed time are inconsistent")
    else:
        need(not timing_units and proof_elapsed == 0,
             "warm cache reuse performed serial diagnostic proof steps")

    owner = run["owner"]
    fields(owner, ("freshOwner", "ownerIdentity", "family", "seed", "developerRoute",
                   "physicalAdmission", "generationReceiptStages", "generationReceiptWork",
                   "diagnosticProof"), name + " published owner")
    need(owner["freshOwner"] is True and type(owner["ownerIdentity"]) is int,
         name + " did not publish a fresh identifiable owner")
    need(owner["family"] == "RB30_CONTROL" and owner["seed"] == seed,
         name + " owner family/seed differs from the request")
    need(owner["developerRoute"] is False,
         name + " published a developer-only fault route")
    need(owner["physicalAdmission"] == PHYSICAL_ADMISSION,
         name + " owner lacks normal-medium physical admission")
    need(owner["generationReceiptStages"] == len(STAGE_NAMES),
         name + " lacks the complete six-stage GenerationReceipt")
    need(owner["generationReceiptWork"] == total_work,
         name + " receipt work count differs from coordinator telemetry")

    proof = owner["diagnosticProof"]
    need(proof.get("warmReuseReceipt") is (name == "warm"),
         name + " diagnostic proof does not identify its cold/warm source")
    _, expected_proof_work = validate_electrical_evidence(proof, seed, name, request)
    if name == "cold":
        need(run["proofCacheHitDelta"] == 0 and run["proofCacheMissDelta"] == 1 and
             run["proofCacheSize"] == 1,
             "cold generation did not execute one real diagnostic-cache miss")
        need(run["hypothesisWork"] == expected_proof_work,
             "cold generation hypothesis work differs from the derived temporal proof count")
    else:
        need(run["proofCacheHitDelta"] == 1 and run["proofCacheMissDelta"] == 0 and
             run["proofCacheSize"] == 1 and run["hypothesisWork"] == 1,
             "warm generation did not reuse exactly one cached proof value")
    return {"elapsedMs": elapsed, "wallElapsedMs": wall_elapsed,
            "maxJobMillis": job_limit,
            "routingElapsedMs": routing_elapsed, "proofElapsedMs": proof_elapsed,
            "diagnosticWorkTimings": timing_records,
            "stages": stage_map,
            "ownerIdentity": owner["ownerIdentity"], "owner": owner}


def validate_qualification_request(request, expected_seed, label):
    """Check the exact request framing and canonical signed seed field."""
    need(isinstance(request, str) and request.startswith(
         "tsj-generation-request/4;native;qualification-only;tsj-challenge/2\n"),
         label + " is not the current private Q30 qualification request")
    root_seed_fields = re.findall(
        r"(?m)^root-seed=(-?(?:0|[1-9][0-9]*));quickPlay=false;layout=", request)
    need(root_seed_fields == [expected_seed],
         label + " canonical root seed is missing, duplicated, or differs from expected seed")
    need(re.findall(r"(?m)^device-intent=RB30_CONTROL@1$", request) ==
         ["device-intent=RB30_CONTROL@1"],
         label + " does not identify the exact RB30_CONTROL descriptor")
    need(";qualification=true;explicitCompletion=true;planEpoch=" in request and
         request.endswith(";physicalAdmission=" + PHYSICAL_ADMISSION),
         label + " omits the qualification completion or physical-admission boundary")


def validate_scope_loss_run(run, seed):
    """Validate an actual owned-job cancellation at the first proof boundary."""
    fields(run, ("run", "outcome", "maxJobMillis", "elapsedMs", "wallElapsedMs",
                 "maxAdvanceMs", "maxManualUnitMs", "manualAdvanceCalls", "totalWork",
                 "hypothesisWork", "routingElapsedMs", "proofElapsedMs",
                 "diagnosticWorkTimings", "proofCacheHitDelta", "proofCacheMissDelta",
                 "proofCacheSize", "planCacheHitDelta", "planCacheMissDelta", "stages",
                 "cleanupComplete", "ownerRestored", "cleanupElapsedMs",
                 "cleanupRetryCount"), "scope-loss cold run")
    need(run["run"] == "cold" and run["outcome"] == "CANCELLED",
         "scope-loss cold job was not canceled")
    need(integer(run["maxJobMillis"], "scope-loss actual job deadline",
                 MEASUREMENT_MAX_JOB_MS, MEASUREMENT_MAX_JOB_MS) == MEASUREMENT_MAX_JOB_MS,
         "scope-loss cold run lacks the selected measurement-only deadline")
    elapsed = integer(run["elapsedMs"], "scope-loss job elapsed time", 0,
                      MEASUREMENT_MAX_JOB_MS)
    wall_elapsed = integer(run["wallElapsedMs"], "scope-loss job wall time", 1,
                           MEASUREMENT_MAX_JOB_MS)
    need(elapsed <= wall_elapsed,
         "scope-loss job active elapsed time exceeds its wall time")
    integer(run["maxAdvanceMs"], "scope-loss maximum solver advance", 0, MAX_UNIT_MS)
    integer(run["maxManualUnitMs"], "scope-loss maximum manual unit", 0, MAX_UNIT_MS)
    total_work = integer(run["totalWork"], "scope-loss completed work", 1, MAX_JOB_STEPS)
    need(integer(run["hypothesisWork"], "scope-loss hypothesis work", 0) == 0,
         "scope-loss job executed a proof hypothesis unit")
    calls = integer(run["manualAdvanceCalls"], "scope-loss manual callbacks", 1,
                    MAX_JOB_STEPS)
    need(calls == total_work,
         "scope-loss run did not record one manual callback per completed coordinator unit")

    stages = run["stages"]
    need(isinstance(stages, list) and len(stages) == len(STAGE_NAMES),
         "scope-loss run must publish all six stage records")
    stage_map = {}
    stage_work = 0
    for stage in stages:
        fields(stage, ("name", "work", "elapsedMs"), "scope-loss stage timing")
        name = stage["name"]
        need(name in STAGE_NAMES and name not in stage_map,
             "scope-loss run has an unknown or duplicate stage")
        work = integer(stage["work"], "scope-loss " + name + " work", 0)
        integer(stage["elapsedMs"], "scope-loss " + name + " elapsed time", 0)
        stage_map[name] = stage
        stage_work += work
    need(set(stage_map) == set(STAGE_NAMES) and stage_work == total_work,
         "scope-loss stage work census differs from completed job work")
    need(all(stage_map[name]["work"] > 0 for name in STAGE_NAMES[:3]) and
         all(stage_map[name]["work"] == 0 for name in STAGE_NAMES[3:]),
         "scope loss did not occur after setup and before the first proof-stage unit")

    routing_elapsed = integer(run["routingElapsedMs"], "scope-loss routing time", 0)
    need(routing_elapsed <= stage_map["HEALTHY"]["elapsedMs"] and
         routing_elapsed <= elapsed and routing_elapsed <= wall_elapsed,
         "scope-loss routing time exceeds its setup stage or whole job")
    need(integer(run["proofElapsedMs"], "scope-loss proof elapsed time", 0) == 0 and
         run["diagnosticWorkTimings"] == [],
         "scope-loss run performed or timed diagnostic proof work")
    for field in ("proofCacheHitDelta", "proofCacheMissDelta", "proofCacheSize",
                  "planCacheHitDelta", "planCacheMissDelta"):
        integer(run[field], "scope-loss " + field, 0)
    need(run["proofCacheHitDelta"] == 0 and run["proofCacheMissDelta"] == 0 and
         run["proofCacheSize"] == 0,
         "scope loss touched the private measurement proof cache")
    need("owner" not in run,
         "scope-loss canceled job unexpectedly retained a published owner")
    need(run["cleanupComplete"] is True and run["ownerRestored"] is True,
         "scope-loss run did not complete exact-owner cleanup/restoration")
    integer(run["cleanupElapsedMs"], "scope-loss cleanup time", 0)
    need(integer(run["cleanupRetryCount"], "scope-loss cleanup retry count", 0) == 0 and
         run.get("cleanupFailure") is None,
         "scope-loss cleanup required a retry or reported failure")
    return {"elapsedMs": elapsed, "wallElapsedMs": wall_elapsed,
            "routingElapsedMs": routing_elapsed, "stages": stage_map}


def validate_scope_loss_row(wrapper, expected_seed):
    fields(wrapper, ("seed", "previewSourceDigest", "previewWebDigest", "report"),
           "compiled scope-loss wrapper row")
    need(wrapper["seed"] == expected_seed,
         "scope-loss wrapper seed differs from expected seed")
    for name in ("previewSourceDigest", "previewWebDigest"):
        need(isinstance(wrapper[name], str) and
             re.fullmatch(r"[0-9a-f]{64}", wrapper[name]) is not None,
             "invalid compiled build identity: " + name)
    report = wrapper["report"]
    fields(report, ("schema", "status", "phase", "seed", "requestedSeed", "request",
                    "normalAdmission", "measurementOnly", "coordinatorQualificationPassed",
                    "normalCatalogRegistered", "playerPublished", "normalPlayerLaunch",
                    "predecessorOwnerPresent", "restored", "cleanupComplete",
                    "cleanupPending", "cleanupRetryCount", "totalElapsedMs", "phaseElapsedMs",
                    "defaultMaxJobMillis", "measurementMaxJobMillis", "maxJobMillis",
                    "maxUnitMillis", "maxJobSteps", "manualAdvancesPerTimer",
                    "measurementCacheCleared", "measurementCacheInitialSize",
                    "measurementCacheSizeBeforeClear", "measurementCacheSizeAfterClear",
                    "measurementCacheHitsBefore", "measurementCacheHitsAfter",
                    "measurementCacheMissesBefore", "measurementCacheMissesAfter",
                    "normalCacheSizeBefore", "normalCacheSizeAfter",
                    "normalCacheHitsBefore", "normalCacheHitsAfter",
                    "normalCacheMissesBefore", "normalCacheMissesAfter",
                    "scopeLossCanaryQuery", "scopeLossCanaryRequested",
                    "scopeLossCanaryInjected", "scopeLossCanaryStage",
                    "scopeLossCanaryProofUnitsBeforeLoss", "scopeLossCanaryFlagRestored",
                    "scopeLossDetected", "coordinatorRunningAfterScopeLoss",
                    "savedOwnersAfterScopeLoss", "observationRetainedAfterScopeLoss",
                    "cold", "warm", "failurePhase", "failure"),
           "scope-loss coordinator report")
    expected_registered = expected_catalog_registered(report["schema"])
    need(report["status"] == "FAIL" and
         report["phase"] == "scope-loss" and report["failurePhase"] == "scope-loss",
         "scope-loss canary did not terminate as the expected scope-loss failure")
    need(report["seed"] == expected_seed and report["requestedSeed"] == expected_seed,
         "scope-loss report/requested seed differs from wrapper seed")
    validate_qualification_request(report["request"], expected_seed,
                                   "scope-loss request")
    need(report["normalAdmission"] is False and report["measurementOnly"] is True and
         report["coordinatorQualificationPassed"] is False and
         report["normalCatalogRegistered"] is expected_registered and
         report["playerPublished"] is False and
         report["normalPlayerLaunch"] is False,
         "scope-loss canary crossed normal admission/publication or reported qualification PASS")
    need(type(report["predecessorOwnerPresent"]) is bool and
         report["restored"] is True and report["cleanupComplete"] is True and
         report["cleanupPending"] is False,
         "scope-loss canary did not restore the exact predecessor or finish cleanup")
    need(integer(report["cleanupRetryCount"], "scope-loss report cleanup retries", 0) == 0,
         "scope-loss report required a cleanup retry")
    total_elapsed = integer(report["totalElapsedMs"], "scope-loss total elapsed time", 1)
    phase_elapsed = integer(report["phaseElapsedMs"], "scope-loss final phase time", 0)
    need(phase_elapsed <= total_elapsed,
         "scope-loss final phase duration exceeds total canary duration")
    need(report["maxJobMillis"] == MEASUREMENT_MAX_JOB_MS and
         report["defaultMaxJobMillis"] == DEFAULT_MAX_JOB_MS and
         report["measurementMaxJobMillis"] == MEASUREMENT_MAX_JOB_MS and
         report["maxUnitMillis"] == MAX_UNIT_MS and report["maxJobSteps"] == MAX_JOB_STEPS and
         report["manualAdvancesPerTimer"] == 1,
         "scope-loss receipt lacks actual measurement deadline provenance or changed fixed budgets")
    need(report["measurementCacheCleared"] is True and
         integer(report["measurementCacheInitialSize"],
                 "scope-loss initial measurement cache size", 0) == 0 and
         integer(report["measurementCacheSizeBeforeClear"],
                 "scope-loss measurement cache before clear", 0) == 0 and
         integer(report["measurementCacheSizeAfterClear"],
                 "scope-loss measurement cache after clear", 0) == 0,
         "scope-loss cleanup left or populated the private measurement cache")
    for metric in ("Hits", "Misses"):
        before = integer(report["measurementCache" + metric + "Before"],
                         "scope-loss measurement cache " + metric + " before", 0)
        after = integer(report["measurementCache" + metric + "After"],
                        "scope-loss measurement cache " + metric + " after", 0)
        need(before == after,
             "scope-loss canary recorded private proof-cache " + metric.lower())
    for metric in ("Size", "Hits", "Misses"):
        before = integer(report["normalCache" + metric + "Before"],
                         "scope-loss normal cache " + metric + " before", 0)
        after = integer(report["normalCache" + metric + "After"],
                        "scope-loss normal cache " + metric + " after", 0)
        need(before == after,
             "scope-loss canary altered the ordinary diagnostic proof cache " + metric)
    need(report["scopeLossCanaryQuery"] == "tsjQ30CoordinatorScopeLoss=true" and
         report["scopeLossCanaryRequested"] is True and
         report["scopeLossCanaryInjected"] is True and
         report["scopeLossCanaryStage"] == "HYPOTHESES" and
         integer(report["scopeLossCanaryProofUnitsBeforeLoss"],
                 "scope-loss pre-injection proof units", 0) == 0 and
         report["scopeLossCanaryFlagRestored"] is True,
         "scope-loss canary was not injected/restored at the first proof boundary")
    need(report["scopeLossDetected"] is True and
         report["coordinatorRunningAfterScopeLoss"] is False and
         report["savedOwnersAfterScopeLoss"] is False and
         report["observationRetainedAfterScopeLoss"] is False,
         "scope-loss handler left owned coordinator/observation state active")
    need(isinstance(report["failure"], str) and "scope" in report["failure"].lower(),
         "scope-loss report lacks the expected diagnostic failure")
    need(report.get("cleanupFailure") is None,
         "scope-loss report contains cleanup failure text")

    cold = validate_scope_loss_run(report["cold"], expected_seed)
    warm = report["warm"]
    need(isinstance(warm, dict) and warm.get("outcome") == "NOT_RUN" and
         set(warm) == {"outcome"},
         "scope-loss canary unexpectedly started a warm run")
    return {"cold": cold, "build": (wrapper["previewSourceDigest"],
                                      wrapper["previewWebDigest"])}


def validate_row(wrapper, expected_seed):
    fields(wrapper, ("seed", "previewSourceDigest", "previewWebDigest", "report"),
           "compiled wrapper row")
    need(wrapper["seed"] == expected_seed, "wrapper seed differs from expected census")
    for name in ("previewSourceDigest", "previewWebDigest"):
        need(isinstance(wrapper[name], str) and
             re.fullmatch(r"[0-9a-f]{64}", wrapper[name]) is not None,
             "invalid compiled build identity: " + name)
    report = wrapper["report"]
    fields(report, ("schema", "status", "phase", "seed", "requestedSeed", "request",
                    "normalAdmission", "coordinatorQualificationPassed",
                    "normalCatalogRegistered", "playerPublished", "normalPlayerLaunch",
                    "predecessorOwnerPresent", "restored", "cleanupComplete",
                    "totalElapsedMs", "phaseElapsedMs", "measurementOnly",
                    "defaultMaxJobMillis", "measurementMaxJobMillis", "maxJobMillis", "maxUnitMillis",
                    "maxJobSteps", "manualAdvancesPerTimer", "measurementCacheCleared",
                    "measurementCacheInitialSize",
                    "measurementCacheSizeBeforeClear", "measurementCacheSizeAfterClear",
                    "measurementCacheHitsBefore", "measurementCacheHitsAfter",
                    "measurementCacheMissesBefore", "measurementCacheMissesAfter",
                    "normalCacheSizeBefore", "normalCacheSizeAfter",
                    "normalCacheHitsBefore", "normalCacheHitsAfter",
                    "normalCacheMissesBefore", "normalCacheMissesAfter",
                    "cold", "warm"), "coordinator report")
    expected_registered = expected_catalog_registered(report["schema"])
    need(report["status"] == "PASS" and report["phase"] == "complete",
         "coordinator report did not complete successfully")
    need(report["seed"] == expected_seed and report["requestedSeed"] == expected_seed,
         "report or requested seed differs from the exact wrapper seed")
    request = report["request"]
    validate_qualification_request(request, expected_seed,
                                   "request canonical identity")
    need(report["normalAdmission"] is False and
         report["coordinatorQualificationPassed"] is True and
         report["normalCatalogRegistered"] is expected_registered and
         report["playerPublished"] is False and
         report["normalPlayerLaunch"] is False,
         "coordinator qualification crossed a player admission/publication boundary")
    need(report["measurementOnly"] is True,
         "coordinator result is not explicitly marked measurement-only")
    need(type(report["predecessorOwnerPresent"]) is bool,
         "predecessor owner presence is missing")
    need(report["restored"] is True and report["cleanupComplete"] is True,
         "coordinator qualification did not restore and clean up")
    integer(report["totalElapsedMs"], "full cold/warm elapsed time", 1)
    integer(report["phaseElapsedMs"], "final phase elapsed time", 0)
    need(report["phaseElapsedMs"] <= report["totalElapsedMs"],
         "final phase time exceeds the full qualification time")
    need(report["maxJobMillis"] == MEASUREMENT_MAX_JOB_MS and
         report["defaultMaxJobMillis"] == DEFAULT_MAX_JOB_MS and
         report["measurementMaxJobMillis"] == MEASUREMENT_MAX_JOB_MS and
         report["maxUnitMillis"] == MAX_UNIT_MS and
         report["maxJobSteps"] == MAX_JOB_STEPS and report["manualAdvancesPerTimer"] == 1,
         "measurement-only deadline or unchanged unit/step budget contract changed")
    need(report["measurementCacheCleared"] is True and
         integer(report["measurementCacheInitialSize"],
                 "measurement cache initial size", 0) == 0,
         "measurement-only proof cache did not start empty and clear on cleanup")
    measurement_size_before = integer(report["measurementCacheSizeBeforeClear"],
                                       "measurement cache size before clear", 0)
    measurement_size_after = integer(report["measurementCacheSizeAfterClear"],
                                      "measurement cache size after clear", 0)
    need(measurement_size_before == 1 and measurement_size_after == 0,
         "measurement-only proof cache was not populated once and cleared after the runs")
    measurement_hits_before = integer(report["measurementCacheHitsBefore"],
                                      "measurement cache hits before", 0)
    measurement_hits_after = integer(report["measurementCacheHitsAfter"],
                                     "measurement cache hits after", 0)
    measurement_misses_before = integer(report["measurementCacheMissesBefore"],
                                        "measurement cache misses before", 0)
    measurement_misses_after = integer(report["measurementCacheMissesAfter"],
                                       "measurement cache misses after", 0)
    need(measurement_hits_after - measurement_hits_before == 1 and
         measurement_misses_after - measurement_misses_before == 1,
         "measurement proof cache did not record one warm hit and one cold miss")
    for cache_metric in ("Size", "Hits", "Misses"):
        before = integer(report["normalCache" + cache_metric + "Before"],
                         "normal cache " + cache_metric + " before", 0)
        after = integer(report["normalCache" + cache_metric + "After"],
                        "normal cache " + cache_metric + " after", 0)
        need(before == after,
             "measurement qualification altered the ordinary diagnostic cache " + cache_metric)
    need(report.get("failure") is None and report.get("cleanupFailure") is None,
         "coordinator report contains failure or cleanup failure text")

    cold = validate_run(report["cold"], "cold", expected_seed, request)
    warm = validate_run(report["warm"], "warm", expected_seed, request)
    need(cold["ownerIdentity"] != warm["ownerIdentity"],
         "warm cache hit reused the cold electrical owner")
    compare_proofs(cold["owner"]["diagnosticProof"], warm["owner"]["diagnosticProof"])
    return {"cold": cold, "warm": warm,
            "pairCount": len(FAULTS) * (len(FAULTS) - 1) // 2,
            "measurementOnly": True,
            "defaultMaxJobMillis": DEFAULT_MAX_JOB_MS,
            "measurementMaxJobMillis": MEASUREMENT_MAX_JOB_MS}


def nearest_rank(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, math.ceil(fraction * len(ordered)) - 1)
    return ordered[index]


def classify_outcome(run):
    if not isinstance(run, dict):
        return "MISSING"
    outcome = run.get("outcome")
    if not isinstance(outcome, str) or not outcome:
        return "MISSING"
    if outcome == "PASS":
        return "PASS"
    if outcome == "EXPECTED_REJECTION":
        return "EXPECTED_REJECTION"
    if outcome == "NOT_RUN":
        return "NOT_RUN"
    return "FAIL"


def canary_mutations(document):
    """Corrupt real input copies; no synthetic report is treated as evidence."""
    def first_report(copy_of_document):
        return copy_of_document["rows"][0]["report"]

    def first_evidence(copy_of_document, phase="cold"):
        return first_report(copy_of_document)[phase]["owner"]["diagnosticProof"]["evidence"][0]

    def first_timing(copy_of_document, phase="cold"):
        return first_report(copy_of_document)[phase]["diagnosticWorkTimings"][0]

    def route_over_healthy_stage(d):
        run = first_report(d)["cold"]
        healthy = next(stage for stage in run["stages"] if stage["name"] == "HEALTHY")
        run["routingElapsedMs"] = healthy["elapsedMs"] + 1

    def corrupt_required_output(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        expected_key = hypothesis_key("DREV_OPEN")
        row = next(item for item in proof["evidence"] if item["hypothesisKey"] == expected_key)
        sample = next(item for item in row["samples"] if item["id"] == "SENSORS_LOW_OUTPUT_A")
        sample["value"] = 999.0

    def mixed_build(d):
        if len(d["rows"]) > 1:
            first = d["rows"][0]["previewWebDigest"]
            d["rows"][1]["previewWebDigest"] = "1" * 64 if first == "0" * 64 else "0" * 64
        else:
            d["rows"][0]["previewWebDigest"] = "invalid-build-digest"

    def wrong_temporal_work(d):
        first_report(d)["cold"]["owner"]["diagnosticProof"]["workUnits"] -= 1

    def wrong_temporal_parameter(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        proof["contextCanonical"] = replace_recipe_parameter(
            proof["contextCanonical"], "profile-work-units", "5", "4")

    def wrong_readiness_identity(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        proof["contextCanonical"] = replace_context_field(
            proof["contextCanonical"], "diagnostic.service-preparation.policy",
            SERVICE_PREPARATION_POLICY,
            "action=REMOVE;units=5;maximumAdvanceSeconds=0.05;readiness=always-available")

    def stale_model_dump_epoch(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        proof["contextCanonical"] = replace_context_field(
            proof["contextCanonical"], "epoch.circuit-dump", MODEL_DUMP_EPOCH,
            "circuitjs-source-load-model-inputs-no-transient-dump-v5")

    def wrong_support_package_count(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        context = proof["contextCanonical"]
        _, context_fields = parse_framed_fields(context, context=True)
        manifest = context_fields["realization.manifest"]
        plan_fields = _parse_plan_manifest(manifest, 3, CURRENT_PLAN_FIELDS)
        current_count = plan_fields["packages"]
        wrong_count = "35" if current_count != "35" else "33"
        wrong_manifest = manifest.replace(
            ";packages=" + current_count + ";", ";packages=" + wrong_count + ";", 1)
        need(wrong_manifest != manifest, "could not corrupt plan package count")
        proof["contextCanonical"] = replace_context_field(
            context, "realization.manifest", manifest, wrong_manifest)

    def missing_physical_pulldown(d):
        proof = first_report(d)["cold"]["owner"]["diagnosticProof"]
        context = proof["contextCanonical"]
        _, context_fields = parse_framed_fields(context, context=True)
        old_admission = context_fields["physical.admission.canonical"]
        new_admission = old_admission.replace("RPIN_A", "RPIN_C")
        need(len(new_admission) == len(old_admission) and new_admission != old_admission,
             "could not corrupt physical RPIN_A declaration")
        proof["contextCanonical"] = replace_context_field(
            context, "physical.admission.canonical", old_admission, new_admission)

    def wrong_catalog_claim(d):
        report = first_report(d)
        report["normalCatalogRegistered"] = not expected_catalog_registered(
            report["schema"])

    return [
        ("measurement mode not isolated", lambda d: first_report(d).__setitem__("measurementOnly", False)),
        ("selected deadline is not measurement cap", lambda d: first_report(d).__setitem__("maxJobMillis", DEFAULT_MAX_JOB_MS)),
        ("production default deadline changed", lambda d: first_report(d).__setitem__("defaultMaxJobMillis", DEFAULT_MAX_JOB_MS + 1)),
        ("measurement ceiling changed", lambda d: first_report(d).__setitem__("measurementMaxJobMillis", MEASUREMENT_MAX_JOB_MS + 1)),
        ("run deadline is not measurement cap", lambda d: first_report(d)["cold"].__setitem__("maxJobMillis", DEFAULT_MAX_JOB_MS)),
        ("step budget changed", lambda d: first_report(d).__setitem__("maxJobSteps", MAX_JOB_STEPS + 1)),
        ("measurement cache retained", lambda d: first_report(d).__setitem__("measurementCacheSizeAfterClear", 1)),
        ("measurement cache lacks one miss", lambda d: first_report(d).__setitem__("measurementCacheMissesAfter", first_report(d)["measurementCacheMissesBefore"])),
        ("normal cache was modified", lambda d: first_report(d).__setitem__("normalCacheHitsAfter", first_report(d)["normalCacheHitsBefore"] + 1)),
        ("normal catalog claim disagrees with report schema", wrong_catalog_claim),
        ("missing cold proof step timing", lambda d: first_report(d)["cold"]["diagnosticWorkTimings"].pop()),
        ("cold proof step count drift", lambda d: first_timing(d).__setitem__("units", first_timing(d)["units"] + 1)),
        ("cold proof step over unit budget", lambda d: first_timing(d).__setitem__("maxUnitMs", MAX_UNIT_MS + 1)),
        ("cold proof active timing exceeds session", lambda d: first_timing(d).__setitem__("elapsedMs", first_report(d)["cold"]["proofElapsedMs"] + 1)),
        ("warm cache hit reports proof steps", lambda d: first_report(d)["warm"]["diagnosticWorkTimings"].append({"label": "REPLAY_INSTALL", "units": 1, "elapsedMs": 1, "maxUnitMs": 1})),
        ("routing timing exceeds healthy stage", route_over_healthy_stage),
        ("wrong numeric output", corrupt_required_output),
        ("inflated tolerance", lambda d: first_evidence(d)["samples"][0].__setitem__("tolerance", 99.0)),
        ("missing sample", lambda d: first_evidence(d)["samples"].pop()),
        ("duplicate hypothesis", lambda d: first_report(d)["warm"]["owner"]["diagnosticProof"]["evidence"].__setitem__(1, copy.deepcopy(first_evidence(d)))),
        ("warm sample drift", lambda d: first_evidence(d, "warm")["samples"][0].__setitem__("value", 123.0)),
        ("warm context drift", lambda d: first_report(d)["warm"]["owner"]["diagnosticProof"].__setitem__("contextCanonical", "foreign-context")),
        ("warm program drift", lambda d: first_report(d)["warm"]["owner"]["diagnosticProof"].__setitem__("programIdentity", "foreign-program")),
        ("warm partition drift", lambda d: first_report(d)["warm"]["owner"]["diagnosticProof"].__setitem__("partitionCanonical", "foreign-partition")),
        ("warm owner collision", lambda d: first_report(d)["warm"]["owner"].__setitem__("ownerIdentity", first_report(d)["cold"]["owner"]["ownerIdentity"])),
        ("cold cache hit", lambda d: first_report(d)["cold"].__setitem__("proofCacheHitDelta", 1)),
        ("work budget overrun", lambda d: first_report(d)["cold"].__setitem__("totalWork", MAX_JOB_STEPS + 1)),
        ("unit budget overrun", lambda d: first_report(d)["warm"].__setitem__("maxManualUnitMs", MAX_UNIT_MS + 1)),
        ("missing stage", lambda d: first_report(d)["cold"]["stages"].pop()),
        ("failed cleanup", lambda d: first_report(d)["warm"].__setitem__("cleanupComplete", False)),
        ("build identity drift", mixed_build),
        ("wrong temporal proof work formula", wrong_temporal_work),
        ("wrong temporal profile unit declaration", wrong_temporal_parameter),
        ("wrong readiness policy with unchanged unit count", wrong_readiness_identity),
        ("stale model-dump interpretation epoch", stale_model_dump_epoch),
        ("support package count differs from the actual contract", wrong_support_package_count),
        ("physical board omits raw pull-down", missing_physical_pulldown),
    ]


def check_document(document, seeds, self_test=False):
    fields(document, ("rows",), "coordinator wrapper document")
    need(isinstance(document["rows"], list), "wrapper rows must be an array")
    need(seeds and len(seeds) == len(set(seeds)), "expected seed list is empty or duplicated")
    for seed in seeds:
        canonical_seed(seed, "expected seed")
    rows = document["rows"]
    need(len(rows) == len(seeds), "wrapper row count differs from expected seed census")

    outcomes = {name: {} for name in ("cold", "warm")}
    accepted = {name: {metric: [] for metric in
                       ("elapsedMs", "wallElapsedMs", "routingElapsedMs", "proofElapsedMs")}
                for name in ("cold", "warm")}
    accepted_stages = {name: {stage: [] for stage in STAGE_NAMES}
                       for name in ("cold", "warm")}
    accepted_work = {name: {} for name in ("cold", "warm")}
    failures = []
    seen = set()
    build = None
    detailed = []
    for index, wrapper in enumerate(rows):
        try:
            fields(wrapper, ("seed", "report", "previewSourceDigest", "previewWebDigest"),
                   "wrapper row " + str(index))
            seed = wrapper["seed"]
            canonical_seed(seed, "wrapper seed")
            need(seed in seeds and seed not in seen, "unexpected or duplicate wrapper seed")
            seen.add(seed)
            identity = (wrapper["previewSourceDigest"], wrapper["previewWebDigest"])
            need(all(isinstance(item, str) and re.fullmatch(r"[0-9a-f]{64}", item)
                     for item in identity), "invalid compiled build identity")
            if build is None:
                build = identity
            else:
                need(identity == build, "mixed compiled builds")
            report = wrapper["report"]
            for phase in ("cold", "warm"):
                run = report.get(phase) if isinstance(report, dict) else None
                classification = classify_outcome(run)
                outcomes[phase][classification] = outcomes[phase].get(classification, 0) + 1
            result = validate_row(wrapper, seed)
            detailed.append(result)
            for phase in ("cold", "warm"):
                for metric in ("elapsedMs", "wallElapsedMs", "routingElapsedMs",
                               "proofElapsedMs"):
                    accepted[phase][metric].append(result[phase][metric])
                for stage_name, stage in result[phase]["stages"].items():
                    accepted_stages[phase][stage_name].append(stage["elapsedMs"])
                for timing in result[phase]["diagnosticWorkTimings"]:
                    accepted_work[phase].setdefault(timing["label"], []).append(
                        timing["elapsedMs"])
        except (ReceiptError, KeyError, TypeError, ValueError) as error:
            failures.append("row " + str(index) + ": " + str(error))
    need(seen == set(seeds), "wrapper seed census is incomplete")

    if self_test:
        need(not failures and len(detailed) == len(rows),
             "negative canaries require a fully valid real receipt")
        passed = 0
        for name, mutate in canary_mutations(document):
            damaged = copy.deepcopy(document)
            try:
                mutate(damaged)
                try:
                    corrupted_result = check_document(damaged, seeds, self_test=False)
                except ReceiptError:
                    passed += 1
                else:
                    if corrupted_result["failures"]:
                        passed += 1
                    else:
                        raise ReceiptError("self-negative canary was accepted: " + name)
            except (IndexError, KeyError, TypeError, ValueError) as error:
                # A malformed mutation that never reaches the reader is not a canary pass.
                raise ReceiptError("self-negative canary could not mutate input: " + name) from error
        need(passed == len(canary_mutations(document)), "not all corruption canaries rejected")

    summaries = {}
    for phase in ("cold", "warm"):
        summaries[phase] = {}
        for metric, values in accepted[phase].items():
            summaries[phase][metric] = {
                "p50": nearest_rank(values, 0.50),
                "p95": nearest_rank(values, 0.95),
                "count": len(values),
            }
        summaries[phase]["stages"] = {
            name: {"p50": nearest_rank(values, 0.50),
                   "p95": nearest_rank(values, 0.95), "count": len(values)}
            for name, values in accepted_stages[phase].items()
        }
        summaries[phase]["workLabels"] = {
            label: {"p50": nearest_rank(values, 0.50),
                    "p95": nearest_rank(values, 0.95), "count": len(values)}
            for label, values in sorted(accepted_work[phase].items())
        }
    return {"outcomes": outcomes, "latency": summaries,
            "pairCount": sum(item["pairCount"] for item in detailed),
            "canaries": len(canary_mutations(document)) if self_test else 0,
            "build": build, "failures": failures,
            "defaultMaxJobMillis": DEFAULT_MAX_JOB_MS,
            "measurementMaxJobMillis": MEASUREMENT_MAX_JOB_MS}


def scope_loss_canary_mutations(document):
    """Mutations are applied only to copies of a real expected-negative receipt."""
    def report(copy_of_document):
        return copy_of_document["rows"][0]["report"]

    def cold(copy_of_document):
        return report(copy_of_document)["cold"]

    def stage(copy_of_document, name="HYPOTHESES"):
        return next(item for item in cold(copy_of_document)["stages"]
                    if item["name"] == name)

    return [
        ("scope loss undetected", lambda d: report(d).__setitem__("scopeLossDetected", False)),
        ("canary not injected", lambda d: report(d).__setitem__("scopeLossCanaryInjected", False)),
        ("canary injected after proof work", lambda d: report(d).__setitem__("scopeLossCanaryProofUnitsBeforeLoss", 1)),
        ("coordinator still running", lambda d: report(d).__setitem__("coordinatorRunningAfterScopeLoss", True)),
        ("successful coordinator status", lambda d: report(d).__setitem__("status", "PASS")),
        ("cold job not canceled", lambda d: cold(d).__setitem__("outcome", "PASS")),
        ("proof hypothesis executed", lambda d: cold(d).__setitem__("hypothesisWork", 1)),
        ("proof cache touched", lambda d: cold(d).__setitem__("proofCacheMissDelta", 1)),
        ("proof stage advanced", lambda d: stage(d).__setitem__("work", 1)),
        ("exact owner not restored", lambda d: cold(d).__setitem__("ownerRestored", False)),
        ("warm run unexpectedly started", lambda d: report(d)["warm"].__setitem__("outcome", "CANCELLED")),
        ("measurement cache retained", lambda d: report(d).__setitem__("measurementCacheSizeAfterClear", 1)),
        ("ordinary cache changed", lambda d: report(d).__setitem__("normalCacheHitsAfter", report(d)["normalCacheHitsBefore"] + 1)),
    ]


def check_scope_loss_document(document, seeds, self_test=False):
    """Read only an actual wrapper receipt for the injected scope-loss path."""
    fields(document, ("rows",), "scope-loss coordinator wrapper document")
    need(isinstance(document["rows"], list), "scope-loss wrapper rows must be an array")
    need(seeds and len(seeds) == len(set(seeds)),
         "expected scope-loss seed list is empty or duplicated")
    for seed in seeds:
        canonical_seed(seed, "expected scope-loss seed")
    rows = document["rows"]
    need(len(rows) == len(seeds),
         "scope-loss wrapper row count differs from expected seed census")

    failures = []
    seen = set()
    build = None
    for index, wrapper in enumerate(rows):
        try:
            fields(wrapper, ("seed", "report", "previewSourceDigest", "previewWebDigest"),
                   "scope-loss wrapper row " + str(index))
            seed = wrapper["seed"]
            canonical_seed(seed, "scope-loss wrapper seed")
            need(seed in seeds and seed not in seen,
                 "unexpected or duplicate scope-loss wrapper seed")
            seen.add(seed)
            identity = (wrapper["previewSourceDigest"], wrapper["previewWebDigest"])
            need(all(isinstance(item, str) and re.fullmatch(r"[0-9a-f]{64}", item)
                     for item in identity), "invalid compiled build identity")
            if build is None:
                build = identity
            else:
                need(identity == build, "mixed compiled builds in scope-loss corpus")
            validate_scope_loss_row(wrapper, seed)
        except (ReceiptError, KeyError, TypeError, ValueError) as error:
            failures.append("row " + str(index) + ": " + str(error))
    need(seen == set(seeds), "scope-loss wrapper seed census is incomplete")

    if self_test:
        need(not failures and len(seen) == len(rows),
             "scope-loss corruption canaries require a valid real negative receipt")
        mutations = scope_loss_canary_mutations(document)
        for name, mutate in mutations:
            damaged = copy.deepcopy(document)
            try:
                mutate(damaged)
            except (IndexError, KeyError, StopIteration, TypeError, ValueError) as error:
                raise ReceiptError(
                    "scope-loss canary could not mutate input: " + name) from error
            try:
                corrupted_result = check_scope_loss_document(
                    damaged, seeds, self_test=False)
            except ReceiptError:
                continue
            if corrupted_result["failures"]:
                continue
            raise ReceiptError("scope-loss corruption canary was accepted: " + name)
    return {"rows": len(rows), "build": build, "failures": failures,
            "canaries": len(scope_loss_canary_mutations(document)) if self_test else 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", help="normal-wrapper JSON with coordinator report rows")
    parser.add_argument("--seeds", nargs="+", required=True,
                        help="exact canonical signed-long seed strings expected in the corpus")
    parser.add_argument("--expect-scope-loss", action="store_true",
                        help="validate the separately injected coordinator scope-loss cancellation receipt")
    parser.add_argument("--self-test", action="store_true",
                        help="run corruption checks on copies of this real receipt")
    args = parser.parse_args()
    try:
        with open(args.receipt, encoding="utf-8") as handle:
            document = json.load(handle)
        if args.expect_scope_loss:
            result = check_scope_loss_document(document, args.seeds, args.self_test)
        else:
            result = check_document(document, args.seeds, args.self_test)
    except (OSError, json.JSONDecodeError, ReceiptError, KeyError, TypeError, ValueError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1

    if args.expect_scope_loss:
        if result["failures"]:
            print("FAIL: scope-loss receipt rejected: " + " | ".join(result["failures"]),
                  file=sys.stderr)
            return 1
        suffix = "; corruptionCanaries={}".format(result["canaries"]) if args.self_test else ""
        print("EXPECTED NEGATIVE VERIFIED: injected Q30 coordinator scope loss canceled the owned cold job and restored its predecessor; rows={} sameBuild={} actualDeadlineMs={} defaultProductionDeadlineMs={}{}; this is not a successful Q30 qualification".format(
            result["rows"], result["build"] is not None,
            MEASUREMENT_MAX_JOB_MS, DEFAULT_MAX_JOB_MS, suffix))
        return 0

    for phase in ("cold", "warm"):
        counts = result["outcomes"][phase]
        print("{} outcomes: PASS={} EXPECTED_REJECTION={} FAIL={} NOT_RUN={} MISSING={}".format(
            phase, counts.get("PASS", 0), counts.get("EXPECTED_REJECTION", 0),
            counts.get("FAIL", 0), counts.get("NOT_RUN", 0), counts.get("MISSING", 0)))
        active = result["latency"][phase]
        print("{} successful measurement-only complete-row elapsed ms: n={} p50={} p95={}".format(
            phase, active["elapsedMs"]["count"], active["elapsedMs"]["p50"],
            active["elapsedMs"]["p95"]))
        print("{} successful measurement-only complete-row wall ms: n={} p50={} p95={}".format(
            phase, active["wallElapsedMs"]["count"], active["wallElapsedMs"]["p50"],
            active["wallElapsedMs"]["p95"]))
        for metric, label in (("routingElapsedMs", "active routing"),
                              ("proofElapsedMs", "diagnostic proof")):
            stats = active[metric]
            print("{} successful measurement-only {} ms: n={} p50={} p95={}".format(
                phase, label, stats["count"], stats["p50"], stats["p95"]))
        for stage, stats in active["stages"].items():
            print("{} successful measurement-only stage {} ms: n={} p50={} p95={}".format(
                phase, stage, stats["count"], stats["p50"], stats["p95"]))
        for label, stats in active["workLabels"].items():
            print("{} successful measurement-only proof-work {} ms: n={} p50={} p95={}".format(
                phase, label, stats["count"], stats["p50"], stats["p95"]))
    if result["failures"]:
        print("FAIL: coordinator receipt rejected: " + " | ".join(result["failures"]),
              file=sys.stderr)
        return 1
    suffix = "; corruptionCanaries={}".format(result["canaries"]) if args.self_test else ""
    print("PASS: DEVELOPER MEASUREMENT-ONLY Q30 coordinator cold/warm evidence seeds={} electricalPairs={} sameBuild={} actualDeadlineMs={} defaultProductionDeadlineMs={}{}; this is not a normal-deadline timing/admission pass".format(
        len(args.seeds), result["pairCount"], result["build"] is not None,
        result["measurementMaxJobMillis"], result["defaultMaxJobMillis"], suffix))
    return 0


if __name__ == "__main__":
    sys.exit(main())
