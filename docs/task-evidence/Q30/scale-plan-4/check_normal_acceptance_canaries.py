#!/usr/bin/env python3
"""Focused strictness canaries for the Q30 normal acceptance receipt reader."""
from __future__ import annotations

import copy
import contextlib
import io
import json
from pathlib import Path
import sys

import check_normal_acceptance as reader


PROFILE = reader.check_d01.profile_for_channels(2, plan_version=4)
SINGLE_PROFILE = reader.check_d01.profile_for_channels(1, plan_version=4)
SEED = "1"
PLAN_TEMPLATE = "RB30_CHANNEL_INPUT_SWEEP_V1"
DEPENDENCIES = "test-stable-dependencies"


def frame(value: str) -> str:
    units = sum(2 if ord(character) > 0xFFFF else 1 for character in value)
    return "V{}:{};".format(units, value)


def request_and_plan(seed: str, topology: str) -> tuple[str, str]:
    plan = "rb30-plan@4;seed={};topology={};support=test".format(seed, topology)
    descriptor = (
        "tsj-challenge/2\n"
        "constraints=tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;"
        "domains=~;input-transitions=~;instruments=~;isolation-actions=~;"
        "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
        "temporal-evidence=~;temporal-samples=~\n"
        "device-intent=RB30_CONTROL@1\n"
        "difficulty-profile=quick-play@1\n"
        "generator=leaf@1\n"
        "geometry=3\nroot-seed=" + seed)
    request = (
        "tsj-generation-request/5;normal-medium@1;native;" + descriptor +
        ";replay=tsj-alpha/4/MEDIUM/RB30_CONTROL/" + seed +
        ";quickPlay=false;layout=13;planEpoch=4;plan=" + plan +
        ";route=MEDIUM_BOARD@1;physicalAdmission=MEDIUM_BOARD_NORMAL@1;executionPolicy=" +
        reader.POLICY +
        ";familyExecution=PLAYER_FAMILY_EXECUTION@1;family=RB30_CONTROL;"
        "candidateProfile=MEDIUM@1;policy=" + reader.POLICY +
        ";physicalAdmission=MEDIUM_BOARD_NORMAL@1;admission=4;search=false;"
        "difficulty=MEDIUM@1;assessment=2")
    return request, plan


def _value(fault: str, sample_id: str, first_index: int, profile=PROFILE) -> float:
    if sample_id == profile["ids"][0]:
        return float(first_index)
    if sample_id == "MAIN_12V":
        return 12.0
    for state, _, _ in profile["states"]:
        if sample_id == "SENSORS_{}_RAIL5".format(state):
            return 0.0 if fault in ("DREV_OPEN", "REN_OPEN") else 5.0
    for state, a_high, b_high in profile["states"]:
        for channel, expected_on in zip(
                ("A", "B"), reader.check_d01.expected_outputs(
                    fault, a_high, b_high, profile["channels"])):
            if sample_id == "SENSORS_{}_OUTPUT_{}".format(state, channel):
                return 12.0 if expected_on else 0.0
    return 0.0


def make_evidence(profile=PROFILE) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for index, (fault, (kind, owner)) in enumerate(profile["faults"].items()):
        samples = []
        for sample_id in profile["ids"]:
            value = _value(fault, sample_id, index, profile)
            samples.append({
                "id": sample_id,
                "outcome": "NUMERIC",
                "value": value,
                "tolerance": max(0.01, 0.02 * abs(value)),
            })
        rows.append({
            "hypothesisKey": reader.check_d01.key(fault, profile["channels"]),
            "routeId": "RB30_CONTROL/{}/{}".format(kind, owner),
            "admittedCandidateCount": len(profile["faults"]),
            "repairReachable": True,
            "customerRetestPassed": True,
            "stateIsolated": True,
            "deterministicResult": "PASS",
            "deterministicRejectionReason": "NONE",
            "equivalentRepairClass": "NONE",
            "executedRepairActionIds": ["REMOVE", "CATALOG_INSTALL"],
            "sampleCount": len(profile["ids"]),
            "samples": samples,
        })
    return rows


def make_partition(program: str, evidence: list[dict[str, object]], profile=PROFILE) -> str:
    keys = sorted(row["hypothesisKey"] for row in evidence)
    samples = {row["hypothesisKey"]: row["samples"][0] for row in evidence}
    labels = {
        key: reader._sample_identity(samples[key])
        for key in keys
    }
    ordered = sorted(keys, key=lambda key: labels[key])
    output = [
        frame("tsj-diagnostic-partition-v1"),
        frame("rb30-control-diagnostic@4"),
        frame(program),
        frame(str(len(keys))),
    ]
    output.extend(frame(key) for key in keys)
    output.extend((frame("6"), frame(str(len(keys))), frame("B"),
                   frame(profile["ids"][0]), frame(str(len(keys)))))
    for key in ordered:
        output.extend((frame(labels[key]), frame("L"), frame("NONE"),
                       frame("1"), frame(key)))
    return "".join(output)


def make_generation_receipt(request: str, work: int) -> str:
    stages = (
        request,
        "CircuitJS:healthy-and-selected-fault:PASS;fresh-materialization-includes-layout;elements=42",
        DEPENDENCIES,
        "workUnits={};complete=true;dependencies={}".format(work, DEPENDENCIES),
        "selected-fault-validated;complete-hypotheses=5;scenario-compatible;answer-private",
        "complete=true;atomic=true",
    )
    return (
        "TSJ-A10-GENERATION-1;manifest={}:{};dependencies={}:{}"
        ";maxJobMillis=90000;maxStepMillis=5000;maxSteps=640".format(
            sum(2 if ord(c) > 0xFFFF else 1 for c in request), request,
            sum(2 if ord(c) > 0xFFFF else 1 for c in DEPENDENCIES), DEPENDENCIES) +
        "".join(";stage{}={}:{}".format(
            index, sum(2 if ord(c) > 0xFFFF else 1 for c in stage), stage)
               for index, stage in enumerate(stages)))


def make_report(seed: str = SEED, profile=PROFILE, topology: str | None = None) -> dict[str, object]:
    topology = topology or "synthetic-q30-canary-topology/{}".format(profile["channels"])
    request, plan = request_and_plan(seed, topology)
    proof_work = 230 if profile["channels"] == 1 else 390
    total_work = proof_work + 5
    program = "diagnostic-program-v1synthetic-normal-program"
    evidence = make_evidence(profile)
    partition = make_partition(program, evidence, profile)
    proof: dict[str, object] = {
        "status": "PASS",
        "family": reader.FAMILY,
        "topology": topology,
        "warmReuseReceipt": False,
        "providerId": "rb30-control-diagnostic@4",
        "programIdentity": program,
        "contextCanonical": "synthetic-trusted-context",
        "contextTrusted": True,
        "partitionCanonical": partition,
        "partitionNodeCount": 6,
        "partitionLeafCount": 5,
        "hypothesisCount": 5,
        "actualSampleCount": len(profile["faults"]) * len(profile["ids"]),
        "elapsedMillis": 10,
        "workUnits": proof_work,
        "explicitCompletion": True,
        "evidence": evidence,
    }
    owner: dict[str, object] = {
        "freshOwner": True,
        "ownerIdentity": 12345,
        "family": reader.FAMILY,
        "seed": seed,
        "developerRoute": False,
        "physicalAdmission": reader.PHYSICAL_ADMISSION,
        "generationReceiptStages": 6,
        "generationReceiptWork": total_work,
        "generationReceipt": make_generation_receipt(request, proof_work),
        "diagnosticProof": proof,
    }
    stage_names = reader.STAGE_NAMES
    stages = [
        {"name": name, "work": proof_work if name == "HYPOTHESES" else 1, "elapsedMs": 1}
        for name in stage_names
    ]
    normal = {
        "run": "normal",
        "outcome": "PASS",
        "maxJobMillis": 90000,
        "elapsedMs": 380,
        "wallElapsedMs": 390,
        "requestElapsedMonotonicMs": 390.0,
        "maxManualAdvanceMonotonicMs": 5.0,
        "coldSerialProofWork": True,
        "maxAdvanceMs": 4,
        "maxManualUnitMs": 4,
        "manualAdvanceCalls": total_work,
        "totalWork": total_work,
        "hypothesisWork": proof_work,
        "routingElapsedMs": 1,
        "proofElapsedMs": 2,
        "proofCacheHitDelta": 0,
        "proofCacheMissDelta": 0,
        "proofCacheSize": 0,
        "planCacheHitDelta": 0,
        "planCacheMissDelta": 0,
        "stages": stages,
        "diagnosticWorkTimings": [],
        "owner": owner,
        "cleanupComplete": True,
        "ownerRestored": True,
        "cleanupElapsedMs": 2,
        "cleanupElapsedMonotonicMs": 2.0,
        "cleanupRetryCount": 0,
    }
    report = {
        "schema": 1,
        "status": "PASS",
        "phase": "complete",
        "seed": seed,
        "requestedSeed": seed,
        "request": request,
        "family": reader.FAMILY,
        "profile": reader.PROFILE,
        "normalAdmission": True,
        "measurementOnly": False,
        "developerVerificationDriver": True,
        "normalRequest": True,
        "normalExecutionPolicy": reader.POLICY,
        "physicalAdmissionIdentity": reader.PHYSICAL_ADMISSION,
        "candidateSearch": False,
        "explicitCompletionRequired": True,
        "normalCatalogRegistered": True,
        "normalCatalogEnabled": True,
        "normalAcceptancePassed": True,
        "playerPublished": True,
        "playerUiUsed": False,
        "predecessorOwnerPresent": True,
        "restored": True,
        "cleanupComplete": True,
        "cleanupPending": False,
        "cleanupRetryCount": 0,
        "clock": "performance.now",
        "totalElapsedMonotonicMs": 400.0,
        "requestElapsedMonotonicMs": 390.0,
        "cleanupElapsedMonotonicMs": 2.0,
        "totalElapsedWallMs": 400,
        "phaseElapsedMonotonicMs": 395.0,
        "maxJobMillis": 90000,
        "defaultMaxJobMillis": 90000,
        "measurementMaxJobMillis": 300000,
        "maxUnitMillis": 5000,
        "maxJobSteps": 640,
        "manualAdvancesPerTimer": 1,
        "measurementCacheInitialSize": 0,
        "measurementCacheSizeAfter": 0,
        "measurementCacheHitsBefore": 0,
        "measurementCacheHitsAfter": 0,
        "measurementCacheMissesBefore": 0,
        "measurementCacheMissesAfter": 0,
        "measurementCacheUntouched": True,
        "normalProofCacheSizeBefore": 0,
        "normalProofCacheSizeAfter": 0,
        "normalProofCacheHitsBefore": 0,
        "normalProofCacheHitsAfter": 0,
        "normalProofCacheMissesBefore": 0,
        "normalProofCacheMissesAfter": 0,
        "normalProofCacheUntouched": True,
        "normal": normal,
    }
    return report


class StubD01:
    """Stub only the not-yet-available v4 context/work receipt boundary."""

    def __init__(self, profile, seed: str, topology: str):
        self.profile = profile
        self.seed = seed
        self.topology = topology
        self.template = PLAN_TEMPLATE

    def validate_q30_diagnostic_context(self, canonical: str, seed: str,
                                        topology: str, request_manifest: str | None = None):
        assert canonical == "synthetic-trusted-context"
        assert seed == self.seed and topology == self.topology
        assert request_manifest == self.request
        return ({"request.manifest": self.request, "realization.manifest": self.plan,
                 "diagnostic.plan.template": self.template}, self.profile)

    def validate_q30_work_contract(self, proof: dict[str, object], seed: str,
                                   expected_explicit_completion: bool = False):
        assert seed == self.seed and expected_explicit_completion is True
        assert proof["explicitCompletion"] is True
        assert proof["providerId"] == "rb30-control-diagnostic@4"
        expected_work = 230 if self.profile["channels"] == 1 else 390
        assert proof["workUnits"] == expected_work
        return expected_work, "plan@4", self.profile

    def validate_q30_electrical_census(self, evidence, profile):
        assert profile is self.profile
        return reader.check_d01.validate_q30_electrical_census(evidence, profile)


def d01_for(report: dict[str, object], profile=PROFILE) -> StubD01:
    proof = report["normal"]["owner"]["diagnosticProof"]
    stub = StubD01(profile, report["seed"], proof["topology"])
    stub.request = report["request"]
    marker = ";plan="
    start = stub.request.index(marker) + len(marker)
    end = stub.request.index(";route=", start)
    stub.plan = stub.request[start:end]
    return stub


def expect_rejected(label: str, fn) -> None:
    try:
        fn()
    except (reader.ReceiptError, AssertionError, KeyError, TypeError, ValueError):
        return
    raise AssertionError("canary was accepted: " + label)


def test_signed_long_seed() -> None:
    for seed in ("0", "1", "-1", "9223372036854775807", "-9223372036854775808"):
        assert reader.parse_seed(seed) == seed
    for seed in ("+1", "01", "-0", "9223372036854775808", "-9223372036854775809", 1, True):
        expect_rejected("noncanonical/out-of-range seed {!r}".format(seed),
                        lambda seed=seed: reader.parse_seed(seed))


def test_normal_report_and_mutations() -> None:
    report = make_report()
    result = reader.validate_report(report, SEED, d01_for(report))
    assert result["status"] == "PASS" and result["providerId"] == "rb30-control-diagnostic@4"
    assert result["proofWorkUnits"] == 390 and result["stageWorkUnits"] == 395
    assert result["observationsPerHypothesis"] == 37 and result["separableFaultPairs"] == 10

    wrong_template = d01_for(report)
    wrong_template.template = "RB30_CHANNEL_INPUT_SWEEP@1"
    expect_rejected("historical plan template identifier", lambda: reader.validate_report(
        report, SEED, wrong_template))

    single_report = make_report(profile=SINGLE_PROFILE)
    single_result = reader.validate_report(
        single_report, SEED, d01_for(single_report, SINGLE_PROFILE))
    assert single_result["channels"] == 1
    assert single_result["observationsPerHypothesis"] == 17
    assert single_result["proofWorkUnits"] == 230 and single_result["stageWorkUnits"] == 235

    expect_rejected("wrong expected seed",
                    lambda: reader.validate_report(report, "2", d01_for(report)))
    mutations = (
        ("non-PASS status", lambda r: r.update(status="BLOCKED")),
        ("historical provider", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            providerId="rb30-control-diagnostic@3")),
        ("warm proof reuse", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            warmReuseReceipt=True)),
        ("incomplete proof", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            explicitCompletion=False)),
        ("wrong work units", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            workUnits=389)),
        ("wrong sample count", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            actualSampleCount=184)),
        ("truncated evidence", lambda r: r["normal"]["owner"]["diagnosticProof"]["evidence"].pop()),
        ("tampered partition", lambda r: r["normal"]["owner"]["diagnosticProof"].update(
            partitionCanonical="V1:X;")),
        ("disabled normal catalog", lambda r: r.update(normalCatalogEnabled=False)),
        ("owner not restored", lambda r: r["normal"].update(ownerRestored=False)),
        ("changed measurement cache", lambda r: r.update(measurementCacheHitsAfter=1)),
        ("changed proof cache", lambda r: r.update(normalProofCacheUntouched=False)),
        ("cumulative time order", lambda r: r.update(totalElapsedMonotonicMs=391.0)),
        ("stage work mismatch", lambda r: r["normal"]["stages"][3].update(work=389)),
        ("request work mismatch", lambda r: r.update(request=r["request"].replace(
            "layout=13;planEpoch=4", "layout=13;planEpoch=3"))),
        ("historical replay epoch", lambda r: r.update(request=r["request"].replace(
            "replay=tsj-alpha/4/", "replay=tsj-alpha/3/"))),
        ("descriptor mismatch", lambda r: r.update(request=r["request"].replace(
            "difficulty-profile=quick-play@1", "difficulty-profile=MEDIUM@1"))),
        ("unknown field", lambda r: r.update(unreviewedField=True)),
        ("missing required field", lambda r: r.pop("normalAcceptancePassed")),
    )
    for label, mutate in mutations:
        changed = copy.deepcopy(report)
        mutate(changed)
        expect_rejected(label, lambda changed=changed: reader.validate_report(
            changed, SEED, d01_for(changed)))


def test_blocked_no_mutation() -> None:
    blocked = {
        "schema": 1, "status": "BLOCKED", "phase": "normal-catalog-disabled",
        "seed": SEED, "requestedSeed": SEED, "family": reader.FAMILY,
        "normalAdmission": False, "measurementOnly": False,
        "normalCatalogRegistered": True, "normalCatalogEnabled": False,
        "generationStarted": False, "snapshotCaptured": False,
        "liveOwnerMutation": False, "cleanupRequired": False,
    }
    result = reader.validate_blocked_no_mutation(blocked, SEED)
    assert result["status"] == "PASS_BLOCKED_CANARY"
    assert result["normalAcceptance"] == "BLOCKED"
    expect_rejected("blocked receipt accepted by the normal reader",
                    lambda: reader.validate_report(blocked, SEED))
    for name, value in (("generationStarted", True), ("snapshotCaptured", True),
                        ("liveOwnerMutation", True), ("cleanupRequired", True),
                        ("normalCatalogEnabled", True)):
        changed = dict(blocked)
        changed[name] = value
        expect_rejected("blocked mutation flag " + name,
                        lambda changed=changed: reader.validate_blocked_no_mutation(changed, SEED))


def test_canonical_parsers(tmp_dir: Path) -> None:
    report = make_report()
    receipt = report["normal"]["owner"]["generationReceipt"]
    parsed = reader.parse_generation_receipt(receipt)
    assert parsed["maxJobMillis"] == 90000 and len(parsed["stages"]) == 6
    expect_rejected("generation receipt trailing data",
                    lambda: reader.parse_generation_receipt(receipt + "x"))
    proof = report["normal"]["owner"]["diagnosticProof"]
    partition = proof["partitionCanonical"]
    partition_prefix = (frame("tsj-diagnostic-partition-v1") +
                        frame("rb30-control-diagnostic@4") + frame(proof["programIdentity"]))
    canonical_count = partition_prefix + frame("5")
    assert partition.startswith(canonical_count)
    noncanonical = dict(proof, partitionCanonical=
                        partition_prefix + frame("05") + partition[len(canonical_count):])
    expect_rejected("noncanonical partition count",
                    lambda: reader.validate_partition(noncanonical, PROFILE))
    duplicate = tmp_dir / "duplicate.json"
    duplicate.write_text('{"status":"PASS","status":"BLOCKED"}', encoding="utf-8")
    expect_rejected("duplicate JSON key", lambda: reader._load_json(duplicate))
    nonstandard = tmp_dir / "nonstandard.json"
    nonstandard.write_text('{"elapsed":NaN}', encoding="utf-8")
    expect_rejected("non-standard JSON NaN", lambda: reader._load_json(nonstandard))
    blocked = tmp_dir / "blocked.json"
    blocked.write_text(json.dumps({
        "schema": 1, "status": "BLOCKED", "phase": "normal-catalog-disabled",
        "seed": SEED, "requestedSeed": SEED, "family": reader.FAMILY,
        "normalAdmission": False, "measurementOnly": False,
        "normalCatalogRegistered": True, "normalCatalogEnabled": False,
        "generationStarted": False, "snapshotCaptured": False,
        "liveOwnerMutation": False, "cleanupRequired": False,
    }), encoding="utf-8")
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        assert reader.main([str(blocked), "--seed", SEED]) == 2
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        assert reader.main([str(blocked), "--seed", SEED,
                            "--expect-blocked-no-mutation"]) == 0
    assert '"normalAcceptance":"BLOCKED"' in out.getvalue()


def main() -> None:
    from tempfile import TemporaryDirectory

    test_signed_long_seed()
    test_normal_report_and_mutations()
    test_blocked_no_mutation()
    with TemporaryDirectory(prefix="q30-normal-reader-") as directory:
        test_canonical_parsers(Path(directory))
    print("PASS: strict normal-reader canaries (canonical seeds, plan@4 normal receipt, "
          "work/stage/cache/cleanup/partition rejection, blocked-no-mutation and JSON framing)")


if __name__ == "__main__":
    main()
