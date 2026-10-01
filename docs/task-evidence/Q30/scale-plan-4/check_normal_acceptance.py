#!/usr/bin/env python3
"""Strict independent reader for Q30 normal-player acceptance receipts."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import re
import struct
import sys
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
D01_DIR = SCRIPT_DIR
if str(D01_DIR) not in sys.path:
    sys.path.insert(0, str(D01_DIR))
try:
    import check_d01
except Exception as exc:
    raise RuntimeError("cannot load the shared Q30 scale/D01 reader: {}".format(exc))

I64_MIN, I64_MAX = -(1 << 63), (1 << 63) - 1
NORMAL_JOB_MS, STEP_MS, MAX_STEPS, MEASUREMENT_JOB_MS = 90000, 5000, 640, 300000
FAMILY, PROFILE = "RB30_CONTROL", "MEDIUM"
PHYSICAL_ADMISSION = "MEDIUM_BOARD_NORMAL@1"
POLICY = ("NORMAL_MEDIUM_EXECUTION@2;physical=MEDIUM_BOARD_NORMAL@1;maxJobMillis=90000;"
          "maxJobSteps=640;maxUnitMillis=5000;candidateBudget=shared;clock=foreground")
LAYOUT_VERSION, PLAN_VERSION = 13, 4
PLAN_TEMPLATE = "RB30_CHANNEL_INPUT_SWEEP_V1"
REPLAY_EPOCH, QUICKPLAY_VERSION, DIFFICULTY_VERSION, ASSESSMENT_VERSION = "tsj-alpha/4", 4, 1, 2
PHYSICAL_POLICY = "MEDIUM_BOARD@1"
TOP_REQUIRED = {
    "schema", "status", "phase", "seed", "requestedSeed", "request", "family", "profile",
    "normalAdmission", "measurementOnly", "developerVerificationDriver", "normalRequest",
    "normalExecutionPolicy", "physicalAdmissionIdentity", "candidateSearch",
    "explicitCompletionRequired", "normalCatalogRegistered", "normalCatalogEnabled",
    "normalAcceptancePassed", "playerPublished", "playerUiUsed", "predecessorOwnerPresent",
    "restored", "cleanupComplete", "cleanupPending", "cleanupRetryCount", "clock",
    "totalElapsedMonotonicMs", "requestElapsedMonotonicMs", "cleanupElapsedMonotonicMs",
    "totalElapsedWallMs", "phaseElapsedMonotonicMs", "maxJobMillis", "defaultMaxJobMillis",
    "measurementMaxJobMillis", "maxUnitMillis", "maxJobSteps", "manualAdvancesPerTimer",
    "measurementCacheInitialSize", "measurementCacheSizeAfter", "measurementCacheHitsBefore",
    "measurementCacheHitsAfter", "measurementCacheMissesBefore", "measurementCacheMissesAfter",
    "measurementCacheUntouched", "normalProofCacheSizeBefore", "normalProofCacheSizeAfter",
    "normalProofCacheHitsBefore", "normalProofCacheHitsAfter", "normalProofCacheMissesBefore",
    "normalProofCacheMissesAfter", "normalProofCacheUntouched", "normal",
}
TOP_OPTIONAL = {
    "cleanupOwnerKind", "cleanupPendingReason", "failurePhase", "failure", "cleanupFailure",
    "scopeLossCanaryQuery", "scopeLossCanaryRequested", "scopeLossCanaryInjected",
    "scopeLossCanaryStage", "scopeLossCanaryProofUnitsBeforeLoss", "scopeLossCanaryFlagRestored",
    "scopeLossDetected", "coordinatorRunningAfterScopeLoss", "savedOwnersAfterScopeLoss",
    "observationRetainedAfterScopeLoss",
}
NORMAL_REQUIRED = {
    "run", "outcome", "maxJobMillis", "elapsedMs", "wallElapsedMs",
    "requestElapsedMonotonicMs", "maxManualAdvanceMonotonicMs", "coldSerialProofWork",
    "maxAdvanceMs", "maxManualUnitMs", "manualAdvanceCalls", "totalWork", "hypothesisWork",
    "routingElapsedMs", "proofElapsedMs", "proofCacheHitDelta", "proofCacheMissDelta",
    "proofCacheSize", "planCacheHitDelta", "planCacheMissDelta", "stages",
    "diagnosticWorkTimings", "owner", "cleanupComplete", "ownerRestored", "cleanupElapsedMs",
    "cleanupElapsedMonotonicMs", "cleanupRetryCount",
}
OWNER_REQUIRED = {
    "freshOwner", "ownerIdentity", "family", "seed", "developerRoute", "physicalAdmission",
    "generationReceiptStages", "generationReceiptWork", "generationReceipt", "diagnosticProof",
}
PROOF_REQUIRED = {
    "status", "family", "topology", "warmReuseReceipt", "providerId", "programIdentity",
    "contextCanonical", "contextTrusted", "partitionCanonical", "partitionNodeCount",
    "partitionLeafCount", "hypothesisCount", "actualSampleCount", "elapsedMillis", "workUnits",
    "explicitCompletion", "evidence",
}
EVIDENCE_REQUIRED = {
    "hypothesisKey", "routeId", "admittedCandidateCount", "repairReachable",
    "customerRetestPassed", "stateIsolated", "deterministicResult",
    "deterministicRejectionReason", "equivalentRepairClass", "executedRepairActionIds",
    "sampleCount", "samples",
}
SAMPLE_REQUIRED = {"id", "outcome", "value", "tolerance"}
STAGE_NAMES = ("RESOLVE", "HEALTHY", "PHYSICAL", "HYPOTHESES", "SYMPTOM", "PUBLISH")
TIMING_REQUIRED = {"label", "units", "elapsedMs", "maxUnitMs"}
BLOCKED_KEYS = {
    "schema", "status", "phase", "seed", "requestedSeed", "family", "normalAdmission",
    "measurementOnly", "normalCatalogRegistered", "normalCatalogEnabled", "generationStarted",
    "snapshotCaptured", "liveOwnerMutation", "cleanupRequired",
}
SEED_RE = re.compile(r"(?:0|-?[1-9][0-9]*)\Z")

class ReceiptError(ValueError):
    pass

def need(condition: bool, message: str) -> None:
    if not condition:
        raise ReceiptError(message)

def object_fields(value: Any, required: set[str], label: str,
                  optional: set[str] | None = None) -> dict[str, Any]:
    need(type(value) is dict, label + " must be an object")
    optional = optional or set()
    missing, extra = required - set(value), set(value) - required - optional
    need(not missing, label + " is missing fields: " + ", ".join(sorted(missing)))
    need(not extra, label + " has unknown fields: " + ", ".join(sorted(extra)))
    return value

def text(value: Any, label: str, *, nonempty: bool = True) -> str:
    need(type(value) is str, label + " must be a string")
    if nonempty:
        need(bool(value), label + " must not be empty")
    return value

def boolean(value: Any, label: str) -> bool:
    need(type(value) is bool, label + " must be a JSON boolean")
    return value

def integer(value: Any, label: str, minimum: int | None = None,
            maximum: int | None = None) -> int:
    need(type(value) is int, label + " must be a JSON integer")
    if minimum is not None:
        need(value >= minimum, label + " is below its allowed range")
    if maximum is not None:
        need(value <= maximum, label + " exceeds its allowed range")
    return value

def finite(value: Any, label: str, minimum: float | None = None,
           maximum: float | None = None) -> float:
    need(type(value) in (int, float) and math.isfinite(value), label + " must be finite")
    result = float(value)
    if minimum is not None:
        need(result >= minimum, label + " is below its allowed range")
    if maximum is not None:
        need(result <= maximum, label + " exceeds its allowed range")
    return result

def parse_seed(value: Any, label: str = "seed") -> str:
    need(type(value) is str and SEED_RE.fullmatch(value) is not None,
         label + " must be canonical signed-long decimal text")
    parsed = int(value, 10)
    need(I64_MIN <= parsed <= I64_MAX and str(parsed) == value,
         label + " is outside the canonical signed-long range")
    return value

def _read_utf16_frame(source: str, offset: int, label: str) -> tuple[str, int]:
    colon = source.find(":", offset)
    need(colon > offset, label + " has a malformed length frame")
    digits = source[offset:colon]
    need(digits.isascii() and digits.isdigit() and str(int(digits)) == digits,
         label + " has a noncanonical frame length")
    target, cursor, consumed = int(digits), colon + 1, 0
    while cursor < len(source) and consumed < target:
        width = 2 if ord(source[cursor]) > 0xFFFF else 1
        need(consumed + width <= target, label + " frame splits a UTF-16 surrogate pair")
        consumed += width
        cursor += 1
    need(consumed == target, label + " frame exceeds its value")
    return source[colon + 1:cursor], cursor

def parse_generation_receipt(canonical: Any) -> dict[str, Any]:
    source = text(canonical, "generation receipt")
    prefix = "TSJ-A10-GENERATION-1"
    need(source.startswith(prefix), "generation receipt has an unsupported envelope")
    cursor = len(prefix)
    def named_frame(name: str) -> str:
        nonlocal cursor
        marker = ";" + name + "="
        need(source.startswith(marker, cursor), "generation receipt omitted " + name)
        cursor += len(marker)
        value, cursor = _read_utf16_frame(source, cursor, name)
        return value
    manifest, dependencies = named_frame("manifest"), named_frame("dependencies")
    limits = re.match(r";maxJobMillis=(0|[1-9][0-9]*);maxStepMillis=(0|[1-9][0-9]*);maxSteps=(0|[1-9][0-9]*)", source[cursor:])
    need(limits is not None, "generation receipt has malformed budget fields")
    max_job, max_step, max_steps = map(int, limits.groups())
    cursor += limits.end()
    stages = [named_frame("stage" + str(i)) for i in range(6)]
    need(cursor == len(source), "generation receipt has trailing data")
    return {"manifest": manifest, "dependencies": dependencies, "maxJobMillis": max_job,
            "maxStepMillis": max_step, "maxSteps": max_steps, "stages": stages}

def _partition_frame(source: str, offset: int) -> tuple[str, int]:
    need(offset < len(source) and source[offset] == "V",
         "partition contains a missing or null value frame")
    value, end = _read_utf16_frame(source, offset + 1, "partition value")
    need(end < len(source) and source[end] == ";", "partition frame terminator is missing")
    return value, end + 1

def _partition_count(source_value: str, label: str, minimum: int,
                     maximum: int | None = None) -> int:
    need(type(source_value) is str and re.fullmatch(r"(?:0|[1-9][0-9]*)", source_value) is not None,
         label + " is not canonical decimal text")
    return integer(int(source_value), label, minimum, maximum)

def _double_hex(value: Any, label: str) -> str:
    number = finite(value, label)
    return format(struct.unpack(">Q", struct.pack(">d", number))[0], "x")

def _sample_identity(sample: dict[str, Any]) -> str:
    need(sample.get("outcome") == "NUMERIC", "partition sample is not numeric")
    return "N:{}:{}".format(_double_hex(sample["value"], "partition sample value"),
                            _double_hex(sample["tolerance"], "partition sample tolerance"))

def _samples_overlap(first: dict[str, Any], second: dict[str, Any]) -> bool:
    return abs(float(first["value"]) - float(second["value"])) <= \
        float(first["tolerance"]) + float(second["tolerance"])

def _sample_for(row: dict[str, Any], sample_id: str) -> dict[str, Any]:
    matches = [sample for sample in row["samples"] if sample["id"] == sample_id]
    need(len(matches) == 1, "partition sample is absent or duplicated in evidence")
    return matches[0]

def validate_partition(proof: dict[str, Any], profile: dict[str, Any]) -> None:
    source = text(proof["partitionCanonical"], "diagnostic partition canonical")
    cursor = 0
    def frame(label: str) -> str:
        nonlocal cursor
        value, cursor = _partition_frame(source, cursor)
        return value
    need(frame("version") == "tsj-diagnostic-partition-v1", "diagnostic partition version changed")
    need(frame("provider") == "rb30-control-diagnostic@4", "diagnostic partition belongs to another provider")
    need(frame("program") == proof["programIdentity"], "diagnostic partition is not bound to the proof program")
    hcount = _partition_count(frame("hypothesis count"), "partition hypothesis count", 1)
    need(hcount == len(profile["faults"]), "partition hypothesis count differs from channel profile")
    expected_keys = sorted(check_d01.key(fault, profile["channels"]) for fault in profile["faults"])
    encoded_keys = [frame("hypothesis key") for _ in range(hcount)]
    need(encoded_keys == expected_keys, "diagnostic partition hypothesis keys changed")
    declared_nodes = _partition_count(frame("node count"), "partition node count", 1, 4096)
    declared_leaves = _partition_count(frame("leaf count"), "partition leaf count", 1, 512)
    evidence = {row["hypothesisKey"]: row for row in proof["evidence"]}
    sample_order = {sample_id: index for index, sample_id in enumerate(profile["ids"])}
    leaf_keys: list[str] = []
    actual_nodes = actual_leaves = 0

    def parse_node(subset: list[str], minimum_sample_index: int, depth: int) -> set[str]:
        nonlocal actual_nodes, actual_leaves
        need(depth <= len(profile["ids"]), "diagnostic partition exceeds sample depth")
        tag = frame("node tag")
        actual_nodes += 1
        if tag == "L":
            repair_class = frame("leaf repair class")
            count = _partition_count(frame("leaf key count"), "partition leaf key count", 1)
            keys = [frame("leaf hypothesis key") for _ in range(count)]
            need(keys == sorted(keys) and keys == subset,
                 "partition leaf does not contain its complete canonical hypothesis subset")
            need(count == 1 and repair_class == "NONE",
                 "normal Q30 partition must resolve every non-equivalent fault separately")
            leaf_keys.extend(keys)
            actual_leaves += 1
            return set(keys)
        need(tag == "B", "diagnostic partition contains an unknown node tag")
        sample_id = frame("branch sample id")
        need(sample_id in sample_order, "partition branches on an undeclared observation")
        sample_index = sample_order[sample_id]
        need(sample_index >= minimum_sample_index,
             "partition observations are not in canonical program order")
        count = _partition_count(frame("branch count"), "partition branch count", 2, len(subset))
        labels, child_sets = [], []
        union: set[str] = set()
        for _ in range(count):
            label = frame("branch label")
            candidates = [key for key in subset
                          if _sample_identity(_sample_for(evidence[key], sample_id)) == label]
            need(candidates, "partition branch has no measured representative")
            representative = _sample_for(evidence[candidates[0]], sample_id)
            group = [key for key in subset
                     if _samples_overlap(_sample_for(evidence[key], sample_id), representative)]
            need(bool(group) and not (set(group) & union),
                 "partition branch tolerance intervals overlap")
            child_keys = parse_node(group, sample_index + 1, depth + 1)
            need(child_keys == set(group), "partition child does not route its measured branch")
            labels.append(label)
            child_sets.append(set(group))
            union.update(group)
        need(labels == sorted(labels) and len(labels) == len(set(labels)),
             "partition branches are not uniquely canonical")
        need(union == set(subset), "partition branches omit a hypothesis")
        return set(subset)

    routed = parse_node(expected_keys, 0, 0)
    need(cursor == len(source), "diagnostic partition has trailing data")
    need(actual_nodes == declared_nodes == proof["partitionNodeCount"],
         "diagnostic partition node counts disagree")
    need(actual_leaves == declared_leaves == proof["partitionLeafCount"] == len(expected_keys),
         "diagnostic partition leaf counts disagree")
    need(sorted(leaf_keys) == expected_keys and routed == set(expected_keys),
         "diagnostic partition does not resolve the complete fault population")

def validate_normal_request(request: str, seed: str, context: dict[str, Any],
                            profile: dict[str, Any]) -> None:
    prefix = "tsj-generation-request/5;normal-medium@1;native;"
    need(request.startswith(prefix), "request is not the current normal-medium staged request")
    rest = request[len(prefix):]
    marker = ";replay="
    need(marker in rest, "normal request has no exact PlayerLaunchRequest replay identity")
    descriptor, suffix = rest.split(marker, 1)
    expected_descriptor = (
        "tsj-challenge/2\n"
        "constraints=tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;"
        "domains=~;input-transitions=~;instruments=~;isolation-actions=~;"
        "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
        "temporal-evidence=~;temporal-samples=~\n"
        "device-intent=RB30_CONTROL@1\n"
        "difficulty-profile=quick-play@1\n"
        "generator=leaf@1\n"
        "geometry=3\nroot-seed=" + seed)
    need(descriptor == expected_descriptor,
         "normal request descriptor is not the exact current RB30 signed-long launch")
    replay = REPLAY_EPOCH + "/MEDIUM/" + FAMILY + "/" + seed
    replay_prefix = replay + ";quickPlay=false;layout="
    need(suffix.startswith(replay_prefix), "normal request differs from exact non-search MEDIUM replay")
    match = re.match(re.escape(replay_prefix) + r"([1-9][0-9]*);planEpoch=([1-9][0-9]*);plan=", suffix)
    need(match is not None, "normal request layout or plan epoch is malformed")
    layout_version, plan_version = int(match.group(1)), int(match.group(2))
    need(layout_version == LAYOUT_VERSION and plan_version == PLAN_VERSION and
         profile.get("planVersion") == PLAN_VERSION, "normal request layout or plan@4 epoch changed")
    plan_start = match.end()
    route_marker = ";route="
    route_index = suffix.find(route_marker, plan_start)
    need(route_index > plan_start, "normal request omitted its canonical plan or route")
    need(suffix[plan_start:route_index] == context.get("realization.manifest"),
         "normal request plan differs from trusted physical proof context")
    family_execution = (
        "PLAYER_FAMILY_EXECUTION@1;family=RB30_CONTROL;candidateProfile=MEDIUM@1;policy=" +
        POLICY + ";physicalAdmission=" + PHYSICAL_ADMISSION)
    expected_tail = (
        PHYSICAL_POLICY + ";physicalAdmission=" + PHYSICAL_ADMISSION +
        ";executionPolicy=" + POLICY + ";familyExecution=" + family_execution +
        ";admission=" + str(QUICKPLAY_VERSION) + ";search=false;difficulty=MEDIUM@" +
        str(DIFFICULTY_VERSION) + ";assessment=" + str(ASSESSMENT_VERSION))
    need(suffix[route_index + len(route_marker):] == expected_tail,
         "normal request contains a non-player route, policy, candidate or difficulty field")
    need(context.get("request.manifest") == request,
         "trusted D01 context is not bound to the exact normal request canonical")

def _validate_proof(proof_value: Any, seed: str, request: str,
                    d01: Any) -> tuple[dict[str, Any], int, str]:
    proof = object_fields(proof_value, PROOF_REQUIRED, "normal diagnostic proof")
    need(proof["status"] == "PASS" and proof["family"] == FAMILY,
         "normal diagnostic proof did not pass for RB30")
    need(proof["providerId"] == "rb30-control-diagnostic@4",
         "normal proof is not the current provider@4 scale receipt")
    need(boolean(proof["contextTrusted"], "proof.contextTrusted") and
         boolean(proof["explicitCompletion"], "proof.explicitCompletion"),
         "normal proof lacks the trusted explicit-completion boundary")
    need(proof["explicitCompletion"] is True and
         boolean(proof["warmReuseReceipt"], "proof.warmReuseReceipt") is False,
         "normal proof omitted explicit completion or reused a warm proof receipt")
    topology = text(proof["topology"], "proof.topology")
    context_result = d01.validate_q30_diagnostic_context(
        proof["contextCanonical"], seed, topology, request_manifest=request)
    need(type(context_result) in (tuple, list) and len(context_result) == 2,
         "shared D01 context oracle returned an invalid result")
    context, context_profile = context_result
    need(type(context) is dict and type(context_profile) is dict,
         "shared D01 context oracle omitted its canonical context/profile")
    need(context.get("diagnostic.plan.template") == PLAN_TEMPLATE,
         "normal proof used an unsupported plan@4 diagnostic template")
    work_result = d01.validate_q30_work_contract(
        proof, seed, expected_explicit_completion=True)
    need(type(work_result) in (tuple, list) and len(work_result) == 3,
         "shared D01 work oracle returned an invalid result")
    expected_work, work_version, work_profile = work_result
    integer(expected_work, "independent expected proof work", 1)
    need(work_version == "plan@4" and work_profile.get("planVersion") == 4 and
         context_profile.get("planVersion") == 4 and
         work_profile.get("providerId") == "rb30-control-diagnostic@4" and
         context_profile.get("providerId") == "rb30-control-diagnostic@4",
         "normal proof used a historical or unsupported Q30 work contract")
    need(context_profile.get("channels") == work_profile.get("channels") and
         context_profile.get("ids") == work_profile.get("ids") and
         context_profile.get("faults") == work_profile.get("faults"),
         "shared context/work profiles disagree")
    validate_normal_request(request, seed, context, work_profile)
    expected_samples = 17 if work_profile["channels"] == 1 else 37
    expected_work_by_channel = 230 if work_profile["channels"] == 1 else 390
    need(len(work_profile["ids"]) == expected_samples and expected_work == expected_work_by_channel,
         "plan@4 observation/work census changed")
    evidence = proof["evidence"]
    need(type(evidence) is list and len(evidence) == len(work_profile["faults"]),
         "normal proof omitted an active fault hypothesis")
    for index, row_value in enumerate(evidence):
        row = object_fields(row_value, EVIDENCE_REQUIRED,
                            "normal proof hypothesis[{}]".format(index))
        integer(row["admittedCandidateCount"], "hypothesis admitted candidate count", 1)
        integer(row["sampleCount"], "hypothesis sample count", 0)
        need(type(row["executedRepairActionIds"]) is list and
             all(type(action) is str for action in row["executedRepairActionIds"]),
             "hypothesis repair action IDs must be strings")
        need(type(row["samples"]) is list, "hypothesis samples must be an array")
        for sample_index, sample_value in enumerate(row["samples"]):
            object_fields(sample_value, SAMPLE_REQUIRED,
                          "hypothesis[{}].sample[{}]".format(index, sample_index))
    pair_count = d01.validate_q30_electrical_census(evidence, work_profile)
    pair_expected = len(work_profile["faults"]) * (len(work_profile["faults"]) - 1) // 2
    need(pair_count == pair_expected, "shared D01 electrical oracle omitted a fault pair")
    hypothesis_count = integer(proof["hypothesisCount"], "proof hypothesis count", 1)
    sample_total = integer(proof["actualSampleCount"], "proof actual sample count", 1)
    need(hypothesis_count == len(work_profile["faults"]) and
         sample_total == hypothesis_count * expected_samples and
         sum(row["sampleCount"] for row in evidence) == sample_total,
         "normal proof sample/hypothesis census differs from plan@4 oracle")
    need(integer(proof["workUnits"], "proof work units", 1) == expected_work,
         "normal proof work differs from independent plan@4 formula")
    integer(proof["partitionNodeCount"], "proof partition node count", 1, 4096)
    integer(proof["partitionLeafCount"], "proof partition leaf count", 1, 512)
    integer(proof["elapsedMillis"], "proof elapsed milliseconds", 0)
    validate_partition(proof, work_profile)
    return work_profile, expected_work, work_version

def _validate_owner(owner_value: Any, seed: str, request: str,
                    proof_work: int, profile: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    owner = object_fields(owner_value, OWNER_REQUIRED, "published normal owner")
    need(boolean(owner["freshOwner"], "owner.freshOwner") and owner["family"] == FAMILY and
         owner["seed"] == seed and boolean(owner["developerRoute"], "owner.developerRoute") is False and
         owner["physicalAdmission"] == PHYSICAL_ADMISSION,
         "published owner identity/normal physical admission is invalid")
    integer(owner["ownerIdentity"], "owner identity")
    integer(owner["generationReceiptStages"], "generation receipt stage count", 6, 6)
    total_work = integer(owner["generationReceiptWork"], "generation receipt work", 1, MAX_STEPS)
    receipt = parse_generation_receipt(owner["generationReceipt"])
    need(receipt["manifest"] == request,
         "generation receipt manifest differs from exact normal PlayerLaunchRequest")
    need(receipt["maxJobMillis"] == NORMAL_JOB_MS and receipt["maxStepMillis"] == STEP_MS and
         receipt["maxSteps"] == MAX_STEPS,
         "generation receipt carries changed coordinator limits")
    stages = receipt["stages"]
    need(stages[0] == request, "generation RESOLVE receipt differs from normal request")
    need(re.fullmatch(
        r"CircuitJS:healthy-and-selected-fault:PASS;fresh-materialization-includes-layout;elements=[1-9][0-9]*",
        stages[1]) is not None, "generation HEALTHY receipt omitted fresh CircuitJS validation")
    need(stages[2] == receipt["dependencies"],
         "generation PHYSICAL receipt differs from captured dependencies")
    need(stages[3] == "workUnits={};complete=true;dependencies={}".format(
        proof_work, receipt["dependencies"]),
        "generation HYPOTHESES receipt differs from the full proof/dependency census")
    need(stages[4] == "selected-fault-validated;complete-hypotheses={};scenario-compatible;answer-private".format(
        len(profile["faults"])), "generation SYMPTOM receipt omitted the full answer-private fault census")
    need(stages[5] == "complete=true;atomic=true", "generation PUBLISH receipt is incomplete")
    need(total_work == proof_work + 5,
         "generation receipt work does not include exactly one non-hypothesis stage unit each")
    return owner, receipt

def _validate_stages(normal: dict[str, Any], proof_work: int, profile: dict[str, Any],
                     owner: dict[str, Any]) -> int:
    stage_rows = normal["stages"]
    need(type(stage_rows) is list and len(stage_rows) == len(STAGE_NAMES),
         "normal generation did not report exactly six stages")
    counts = []
    for index, (stage_value, expected_name) in enumerate(zip(stage_rows, STAGE_NAMES)):
        row = object_fields(stage_value, {"name", "work", "elapsedMs"},
                            "normal stage[{}]".format(index))
        need(row["name"] == expected_name, "normal coordinator stage order changed")
        expected_count = proof_work if expected_name == "HYPOTHESES" else 1
        counts.append(integer(row["work"], "stage " + expected_name + " work", 0, MAX_STEPS))
        need(counts[-1] == expected_count,
             "normal stage " + expected_name + " work differs from independent expectation")
        integer(row["elapsedMs"], "stage " + expected_name + " elapsed", 0, NORMAL_JOB_MS)
    expected_total = proof_work + 5
    need(sum(counts) == expected_total and expected_total <= MAX_STEPS,
         "normal six-stage totals exceed or differ from independent Q30 work census")
    need(integer(normal["hypothesisWork"], "normal hypothesis work", 0, MAX_STEPS) == proof_work and
         integer(normal["totalWork"], "normal total work", 0, MAX_STEPS) == expected_total and
         integer(normal["manualAdvanceCalls"], "manual advance calls", 0, MAX_STEPS) == expected_total,
         "normal hypothesis, total-work or manual-turn counts differ from stage totals")
    need(integer(owner["generationReceiptWork"], "generation receipt work", 0, MAX_STEPS) == expected_total and
         integer(owner["generationReceiptStages"], "generation receipt stages", 0, 6) == 6,
         "published generation receipt totals differ from live stage totals")
    return expected_total

def validate_report(report_value: Any, expected_seed: Any, d01: Any = check_d01) -> dict[str, Any]:
    report = object_fields(report_value, TOP_REQUIRED, "normal acceptance receipt", TOP_OPTIONAL)
    seed = parse_seed(expected_seed, "expected seed")
    need(report["status"] == "PASS", "BLOCKED/FAIL/non-PASS acceptance receipt is rejected")
    need(report["phase"] == "complete" and integer(report["schema"], "receipt schema", 1, 1) == 1,
         "normal acceptance did not complete supported schema 1")
    need(parse_seed(report["seed"]) == seed and parse_seed(report["requestedSeed"], "requestedSeed") == seed,
         "receipt seed does not match the expected exact signed-long identity")
    request = text(report["request"], "normal request canonical")
    need(report["family"] == FAMILY and report["profile"] == PROFILE,
         "normal launch family/profile differs from Q30 MEDIUM")
    for name in ("normalAdmission", "normalRequest", "developerVerificationDriver",
                 "normalCatalogRegistered", "normalCatalogEnabled", "normalAcceptancePassed",
                 "playerPublished", "restored", "cleanupComplete"):
        need(boolean(report[name], name), name + " is false")
    for name in ("measurementOnly", "candidateSearch", "playerUiUsed", "cleanupPending"):
        need(boolean(report[name], name) is False, name + " must be false for normal acceptance")
    need(boolean(report["explicitCompletionRequired"], "explicitCompletionRequired") and
         report["normalExecutionPolicy"] == POLICY and
         report["physicalAdmissionIdentity"] == PHYSICAL_ADMISSION,
         "normal completion/execution/physical admission contract changed")
    need(report["clock"] == "performance.now", "acceptance did not use a monotonic performance clock")
    need(integer(report["maxJobMillis"], "normal maxJobMillis", 1) == NORMAL_JOB_MS and
         integer(report["defaultMaxJobMillis"], "default maxJobMillis", 1) == NORMAL_JOB_MS and
         integer(report["measurementMaxJobMillis"], "measurement maxJobMillis", 1) == MEASUREMENT_JOB_MS and
         integer(report["maxUnitMillis"], "normal max unit", 1) == STEP_MS and
         integer(report["maxJobSteps"], "normal max steps", 1) == MAX_STEPS and
         integer(report["manualAdvancesPerTimer"], "manual advances per timer", 1) == 1,
         "normal coordinator budget/turn contract changed")
    forbidden = {"failurePhase", "failure", "cleanupFailure", "cleanupPendingReason",
                 "cleanupOwnerKind", "scopeLossCanaryQuery", "scopeLossCanaryRequested",
                 "scopeLossCanaryInjected", "scopeLossCanaryStage",
                 "scopeLossCanaryProofUnitsBeforeLoss", "scopeLossCanaryFlagRestored",
                 "scopeLossDetected", "coordinatorRunningAfterScopeLoss",
                 "savedOwnersAfterScopeLoss", "observationRetainedAfterScopeLoss"}
    need(not (set(report) & forbidden),
         "PASS receipt contains failure, pending-cleanup, or scope-loss-canary fields")
    integer(report["cleanupRetryCount"], "top-level cleanup retry count", 0, 0)
    total_mono = finite(report["totalElapsedMonotonicMs"], "total monotonic elapsed", 0)
    request_mono = finite(report["requestElapsedMonotonicMs"], "request monotonic elapsed", 0, NORMAL_JOB_MS)
    cleanup_mono = finite(report["cleanupElapsedMonotonicMs"], "cleanup monotonic elapsed", 0)
    phase_mono = finite(report["phaseElapsedMonotonicMs"], "phase monotonic elapsed", 0)
    total_wall = integer(report["totalElapsedWallMs"], "total wall elapsed", 0)
    need(total_mono + 0.01 >= request_mono + cleanup_mono and
         phase_mono + 0.01 >= request_mono and total_mono + 0.01 >= phase_mono,
         "monotonic request, cleanup and completion clocks are not separately ordered")

    need(boolean(report["measurementCacheUntouched"], "measurementCacheUntouched") and
         boolean(report["normalProofCacheUntouched"], "normalProofCacheUntouched"),
         "proof caches changed during normal acceptance")
    cache_fields = (
        ("measurementCacheInitialSize", "measurementCacheSizeAfter",
         "measurementCacheHitsBefore", "measurementCacheHitsAfter",
         "measurementCacheMissesBefore", "measurementCacheMissesAfter"),
        ("normalProofCacheSizeBefore", "normalProofCacheSizeAfter",
         "normalProofCacheHitsBefore", "normalProofCacheHitsAfter",
         "normalProofCacheMissesBefore", "normalProofCacheMissesAfter"),
    )
    for group in cache_fields:
        values = [integer(report[name], name, 0) for name in group]
        need(values[0] == values[1] and values[2] == values[3] and values[4] == values[5],
             group[0].split("Size")[0] + " counters changed")

    normal = object_fields(report["normal"], NORMAL_REQUIRED, "normal coordinator record", {"cleanupFailure"})
    need("cleanupFailure" not in normal and normal["run"] == "normal" and normal["outcome"] == "PASS" and
         boolean(normal["coldSerialProofWork"], "coldSerialProofWork"),
         "normal coordinator did not complete cold serial proof work")
    need(boolean(normal["cleanupComplete"], "normal.cleanupComplete") and
         boolean(normal["ownerRestored"], "normal.ownerRestored"),
         "normal coordinator cleanup/owner restoration did not pass")
    integer(normal["cleanupRetryCount"], "normal cleanup retries", 0, 0)
    need(integer(normal["maxJobMillis"], "normal run maxJobMillis", 1) == NORMAL_JOB_MS,
         "normal coordinator record changed its job deadline")
    job_elapsed = integer(normal["elapsedMs"], "normal job elapsed", 0, NORMAL_JOB_MS)
    wall_elapsed = integer(normal["wallElapsedMs"], "normal wall elapsed", 0)
    finite(normal["requestElapsedMonotonicMs"], "normal request monotonic elapsed", 0, NORMAL_JOB_MS)
    normal_cleanup_mono = finite(normal["cleanupElapsedMonotonicMs"],
                                 "normal cleanup monotonic elapsed", 0)
    finite(normal["maxManualAdvanceMonotonicMs"], "max manual monotonic advance", 0, STEP_MS)
    need(abs(float(normal["requestElapsedMonotonicMs"]) - request_mono) <= 0.01 and
         abs(normal_cleanup_mono - cleanup_mono) <= 0.01,
         "top-level and coordinator monotonic timing copies disagree")
    need(integer(normal["maxAdvanceMs"], "max coordinator advance", 0, STEP_MS) <= STEP_MS and
         integer(normal["maxManualUnitMs"], "max manual unit", 0, STEP_MS) <= STEP_MS,
         "normal coordinator exceeded the 5000 ms work-unit limit")
    for name in ("routingElapsedMs", "proofElapsedMs", "cleanupElapsedMs"):
        integer(normal[name], "normal." + name, 0)
    need(integer(normal["proofCacheHitDelta"], "normal proof cache hits") == 0 and
         integer(normal["proofCacheMissDelta"], "normal proof cache misses") == 0,
         "normal proof cache recorded a hit or miss")
    need(integer(normal["proofCacheSize"], "normal proof cache size", 0) ==
         integer(report["normalProofCacheSizeBefore"], "normal proof cache size before", 0),
         "normal proof cache size snapshot differs from the run record")
    integer(normal["planCacheHitDelta"], "plan cache hits", 0)
    integer(normal["planCacheMissDelta"], "plan cache misses", 0)
    timings = normal["diagnosticWorkTimings"]
    need(type(timings) is list, "diagnostic timing evidence must be an array")
    for index, timing in enumerate(timings):
        row = object_fields(timing, TIMING_REQUIRED, "diagnostic timing[{}]".format(index))
        text(row["label"], "diagnostic timing label")
        integer(row["units"], "diagnostic timing units", 0)
        integer(row["elapsedMs"], "diagnostic timing elapsed", 0)
        integer(row["maxUnitMs"], "diagnostic timing max unit", 0)

    owner_value = object_fields(normal["owner"], OWNER_REQUIRED, "published normal owner")
    profile, proof_work, work_version = _validate_proof(owner_value["diagnosticProof"], seed, request, d01)
    owner, receipt = _validate_owner(owner_value, seed, request, proof_work, profile)
    need(owner["physicalAdmission"] == report["physicalAdmissionIdentity"],
         "normal request and published physical owner admissions differ")
    expected_total = _validate_stages(normal, proof_work, profile, owner)
    need(request_mono <= NORMAL_JOB_MS and job_elapsed <= NORMAL_JOB_MS and expected_total <= MAX_STEPS,
         "normal job exceeded cumulative time or work budget")
    need(normal["cleanupElapsedMonotonicMs"] == report["cleanupElapsedMonotonicMs"],
         "normal cleanup duration differs from top-level cleanup accounting")
    need(integer(normal["cleanupElapsedMs"], "normal cleanup wall elapsed", 0) <= total_wall,
         "cleanup wall duration exceeds verifier wall elapsed")
    boolean(report["predecessorOwnerPresent"], "predecessorOwnerPresent")
    return {
        "status": "PASS", "seed": seed, "providerId": "rb30-control-diagnostic@4",
        "planVersion": 4, "workContract": work_version, "channels": profile["channels"],
        "observationsPerHypothesis": len(profile["ids"]), "hypotheses": len(profile["faults"]),
        "proofWorkUnits": proof_work, "stageWorkUnits": expected_total,
        "separableFaultPairs": len(profile["faults"]) * (len(profile["faults"]) - 1) // 2,
        "requestElapsedMonotonicMs": request_mono, "cleanupElapsedMonotonicMs": cleanup_mono,
        "totalElapsedMonotonicMs": total_mono, "cleanupComplete": True,
        "ownerRestored": True, "cachesUntouched": True,
    }

def validate_blocked_no_mutation(report_value: Any, expected_seed: Any) -> dict[str, Any]:
    report = object_fields(report_value, BLOCKED_KEYS, "expected disabled-catalog canary")
    seed = parse_seed(expected_seed, "expected seed")
    need(integer(report["schema"], "blocked canary schema", 1, 1) == 1 and
         report["status"] == "BLOCKED" and report["phase"] == "normal-catalog-disabled",
         "expected disabled-catalog canary did not fail closed as BLOCKED")
    need(parse_seed(report["seed"]) == seed and
         parse_seed(report["requestedSeed"], "blocked requestedSeed") == seed and
         report["family"] == FAMILY, "blocked canary identity differs from exact Q30 seed/family")
    need(report["normalAdmission"] is False and report["measurementOnly"] is False and
         report["normalCatalogRegistered"] is True and report["normalCatalogEnabled"] is False and
         report["generationStarted"] is False and report["snapshotCaptured"] is False and
         report["liveOwnerMutation"] is False and report["cleanupRequired"] is False,
         "disabled-catalog canary mutated or started the live owner")
    return {"status": "PASS_BLOCKED_CANARY", "seed": seed,
            "phase": "normal-catalog-disabled", "generationStarted": False,
            "snapshotCaptured": False, "liveOwnerMutation": False, "cleanupRequired": False,
            "normalAcceptance": "BLOCKED"}

def _load_json(path: Path) -> Any:
    def unique_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ReceiptError("JSON object repeats key: " + key)
            result[key] = value
        return result
    def reject_constant(value: str) -> None:
        raise ReceiptError("JSON contains non-standard numeric constant " + value)
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_pairs,
                          parse_constant=reject_constant)
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReceiptError("cannot read strict JSON receipt: {}".format(exc)) from exc

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipt", type=Path)
    parser.add_argument("--seed", required=True,
                        help="expected exact canonical signed-long seed; never inferred from receipt counts")
    parser.add_argument("--expect-blocked-no-mutation", action="store_true",
                        help="check only the disabled-catalog negative canary; this does not accept admission")
    args = parser.parse_args(argv)
    try:
        report = _load_json(args.receipt)
        result = (validate_blocked_no_mutation(report, args.seed) if args.expect_blocked_no_mutation
                  else validate_report(report, args.seed))
    except (ReceiptError, AssertionError, KeyError, TypeError, ValueError) as exc:
        print("FAIL: {}".format(exc), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
