"""Recheck the retired cohort, native gates, and measured chain independently."""

import hashlib
import json
import math
import subprocess
import sys
from pathlib import Path

import analyze
import receipt_order_reader as assess
import check_assessment
import check_receipt_order_reader as order_check


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OLD = HERE.parent / "routed-slide-descent-r1"
MAIN_SEAL = ROOT / "docs/task-evidence/PCB/silkscreen-scale-r1/final-inputs.json"


def _read(path):
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AssertionError("cannot read review input: " + str(path)) from exc


def _sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _need(condition, message):
    if not condition:
        raise AssertionError(message)


def _safe_file(relative, root=HERE):
    _need(type(relative) is str and "\\" not in relative,
          "review path is not slash-relative")
    target = root.joinpath(*Path(relative).parts)
    resolved_root = root.resolve()
    _need(target.resolve().is_relative_to(resolved_root) and target.is_file(),
          "review input is missing or escapes its owner: " + relative)
    return target


def _verify_group(group, folder, label):
    _need(type(group) is dict, label + " is not a hash map")
    for name, digest in group.items():
        target = _safe_file(name, folder)
        _need(type(digest) is str and _sha(target) == digest,
              label + " hash changed: " + name)


def _verify_main_and_parent_seals(seal):
    _need((seal.get("baseCount"), seal.get("copyCount")) == (1467, 1481),
          "cohort source counts changed")
    _need(len(seal.get("overrides", {})) == 4 and
          len(seal.get("additions", {})) == 14 and
          len(seal.get("gateOwners", {})) == 28,
          "cohort source delta or gate count changed")
    _verify_group(seal["gateOwners"], HERE, "cohort gate owners")
    _verify_group(seal["overrides"], HERE / "source", "cohort source overrides")
    _verify_group(seal["additions"], HERE / "source", "cohort source additions")

    main = _read(MAIN_SEAL)
    _need(_sha(MAIN_SEAL) == seal["baseMainSealSha256"] and
          type(main.get("files")) is list and len(main["files"]) == 1475,
          "accepted main1475 seal changed")
    for row in main["files"]:
        target = _safe_file(row["path"], ROOT)
        _need(_sha(target) == row["sha256"],
              "accepted main source changed: " + row["path"])

    _need(_sha(OLD / "native-inputs.json") == seal["publishedDescentSealSha256"],
          "published descent seal changed")
    prior_seal = _read(OLD / "native-inputs.json")
    _need((prior_seal["baseCount"], prior_seal["copyCount"]) == (1467, 1473),
          "published descent source counts changed")
    _verify_group(prior_seal["gateOwners"], OLD, "published descent gate owners")
    _verify_group(prior_seal["overrides"], OLD / "source",
                  "published descent source overrides")
    _verify_group(prior_seal["additions"], OLD / "source",
                  "published descent source additions")
    for group_name in (
            "preparationFailureArtifacts", "protocolRevisionArtifacts",
            "preFreezeReviewArtifacts", "nativeFailureArtifacts",
            "nativeRuntimeFailureArtifacts"):
        if group_name in prior_seal:
            _verify_group(prior_seal[group_name], OLD,
                          "published descent " + group_name)

    _need(type(seal.get("preFreezeSourceReviewArtifacts")) is dict and
          len(seal["preFreezeSourceReviewArtifacts"]) == 5,
          "cohort pre-freeze review archive is incomplete")
    _verify_group(seal["preFreezeSourceReviewArtifacts"], HERE,
                  "cohort pre-freeze review archive")
    audit = _read(HERE / "native-input-audit.json")
    _need(audit == {
        "status": "PASS", "main1475Exact": True, "copyCount": 1481,
        "sourceDeltas": 18, "gateOwners": 28,
        "publishedInputsExact": True, "preFreezeSourceReviewPreserved": True,
    }, "cohort source input audit changed")


def _verify_failure_archive(seal):
    """Audit the preserved failed first native attempt without accepting it."""
    artifacts = seal.get("nativeFailureArtifacts")
    _need(type(artifacts) is dict and artifacts,
          "preserved failed native attempt archive is missing")
    _verify_group(artifacts, HERE, "cohort native failure archive")
    names = list(artifacts)
    timing_names = [name for name in names
                    if Path(name).name == "unit-timing.json"]
    _need(len(timing_names) == 1, "failed unit timing archive is missing or duplicated")
    timing = _read(_safe_file(timing_names[0]))
    _need(timing.get("phase") == "unit" and timing.get("status") == "FAIL" and
          timing.get("exitCode") == 2,
          "preserved failed unit attempt is not FAIL/exit2")
    failure_names = [name for name in names
                     if Path(name).name == "failure.json"]
    _need(len(failure_names) == 1,
          "failed unit failure receipt is missing or duplicated")
    failure = _read(_safe_file(failure_names[0]))
    _need(failure.get("phase") == "unit" and
          failure.get("status") == "FAIL" and
          failure.get("exitCode") == 2 and
          failure.get("routes") == "NOT_RUN",
          "preserved failed unit receipt changed")
    cleanup_names = [name for name in names
                     if Path(name).name == "unit-process-cleanup.json"]
    _need(len(cleanup_names) == 1,
          "failed unit cleanup archive is missing or duplicated")
    cleanup = _read(_safe_file(cleanup_names[0]))
    _need(cleanup.get("status") == "PASS" and
          cleanup.get("terminationAttempted") is False,
          "failed unit attempt cleanup did not pass")
    launch_names = [name for name in names
                    if Path(name).name == "unit-process-launch.json"]
    _need(len(launch_names) == 1,
          "failed unit process launch receipt is missing or duplicated")
    launch = _read(_safe_file(launch_names[0]))
    _need(launch.get("phase") == "unit" and
          type(launch.get("drivers")) is list and
          len(launch["drivers"]) == len(cleanup.get("drivers", [])) == 2,
          "failed unit process birth receipt changed")
    for before, after in zip(launch["drivers"], cleanup["drivers"]):
        _need(all(before.get(key) == after.get(key)
                  for key in ("pid", "startTicks", "executable")) and
              after.get("outcome") == "EXITED" and
              after.get("sameCreationStillRunning") is False,
              "failed unit driver cleanup identity changed")
    _need(not any(Path(name).name in ("unit-validation.json", "assessment.json")
                  for name in names),
          "failed native archive contains a success artifact")
    for name in names:
        if Path(name).name == "protocol.json":
            _need(_sha(_safe_file(name)) == _sha(HERE / "protocol.json"),
                  "failed native attempt carried a different protocol")
    return len(launch["drivers"])


def _verify_source_review(seal):
    subprocess.run([
        sys.executable, "-X", "utf8", "-B", str(HERE / "review_source.py"),
        "--archive",
    ], check=True)
    for name in ("source-review.diff", "source-review.json"):
        _need(_sha(HERE / name) == seal["gateOwners"][name],
              "source review artifact changed: " + name)


def _verify_transport_and_ack(contract, canaries):
    ack = _read(HERE / "review-ack.json")
    _need(ack["rootReviewed"] is True and
          ack["diffSha256"] == _sha(HERE / "source-review.diff") and
          ack["protocolSha256"] == _sha(HERE / "protocol.json") and
          ack["syntheticReaderCases"] == canaries["canaryCount"],
          "root review acknowledgement changed")
    _need(canaries["fixtureCounts"] == contract["protocol"]["unitFixtureCounts"],
          "reader canary fixture counts differ from protocol")
    transport = _read(HERE / "suite-transport-canaries.json")
    _need(transport["status"] == "PASS" and transport["caseCount"] == 12 and
          transport["actualManifests"] == 2 and
          transport["negativeCases"] == 10,
          "actual PowerShell suite transport receipt changed")
    for name, digest in transport["consumedSha256"].items():
        _need(_sha(HERE / name) == digest,
              "suite transport input changed: " + name)
    return transport


def _verify_order_adapter(canaries):
    path = HERE / "receipt-order-canary-results.json"
    _need(path.is_file(), "receipt-order canary receipt is missing")
    recorded = _read(path)
    _need(recorded["status"] == "PASS" and
          recorded["originalCanaryCount"] == canaries["canaryCount"] == 93 and
          recorded["canaryCount"] == 107 and
          recorded["freshCanaryCount"] == 14 and
          recorded["freshPositiveCount"] == 1 and
          recorded["freshNegativeCount"] == 13,
          "receipt-order canary counts changed")
    _need(recorded["adapterSource"] == assess.source_metadata(),
          "receipt-order adapter source pin changed")
    consumed = recorded["consumedSha256"]
    for name, digest in consumed.items():
        _need(_sha(HERE / name) == digest,
              "receipt-order canary input changed: " + name)
    _need(recorded["originalCanaryReceiptSha256"] ==
          _sha(HERE / "assessment-canary-results.json"),
          "receipt-order original canary receipt pin changed")
    _need(order_check._run_canaries() == recorded,
          "receipt-order canary replay changed")
    return recorded


def _verify_reuse_boundary(contract):
    audit_path = HERE / "native-reuse-audit.json"
    failure_path = HERE / "reader-framing-failure.json"
    _need(audit_path.is_file() and failure_path.is_file(),
          "post-native reuse/framing audit is incomplete")
    audit = _read(audit_path)
    _need(audit.get("status") == "PASS" and
          audit.get("nativeInputSealSha256") == _sha(HERE / "native-inputs.json") and
          audit.get("main1475Exact") is True and
          audit.get("copy1481Exact") is True and
          audit.get("sourceOverridesAndAdditionsExact18") is True and
          audit.get("originalFrozenGateOwnersExact28") is True,
          "native reuse boundary changed")
    expected_phase_inputs = {
        "unit.txt", "unit-receipt.txt", "unit-timing.json",
        "unit-process-launch.json", "unit-process-cleanup.json",
        "unit-log-sanitization.json", "routes.txt", "routes-receipt.txt",
        "routes-timing.json", "routes-process-launch.json",
        "routes-process-cleanup.json", "routes-log-sanitization.json",
    }
    phase_inputs = audit.get("phaseInputsSha256")
    _need(type(phase_inputs) is dict and
          set(phase_inputs) == expected_phase_inputs,
          "native reuse phase input set changed")
    for name, digest in phase_inputs.items():
        _need(_sha(HERE / name) == digest,
              "native reuse phase input changed: " + name)
    _need(audit.get("unitValidationSha256") == _sha(HERE / "unit-validation.json") and
          audit.get("readerFramingRejectionSha256") == _sha(failure_path),
          "native reuse derived receipt pins changed")
    _need("postnative" in audit.get("changedConsumedInputs", "").lower() and
          "originalfrozenreader" in audit.get("changedConsumedInputs", "").lower(),
          "native reuse change boundary is undocumented")

    rejection = _read(failure_path)
    native = rejection.get("nativePhaseStatus")
    elapsed = native.get("elapsedSeconds") if type(native) is dict else None
    _need(rejection.get("status") == "REJECTED" and
          rejection.get("command") == "python -X utf8 -B assess.py routes" and
          rejection.get("exitCode") == 2 and
          rejection.get("assessmentWritten") is False and
          type(native) is dict and native.get("status") == "PASS" and
          native.get("exitCode") == 0 and native.get("suiteCount") == 7 and
          native.get("phase") == "routes" and
          type(elapsed) in (int, float) and not isinstance(elapsed, bool) and
          math.isfinite(elapsed) and 0 <= elapsed <= contract["protocol"]["maxPhaseElapsedSeconds"] and
          native.get("scope") == contract["protocol"]["timingScope"] and
          native.get("terminalCleanupIncluded") is True and
          native.get("cleanupDuration") == "NOT_SEPARATELY_MEASURED",
          "original frozen route-reader exit-2 rejection changed")
    _need(rejection.get("sourceAndGateSealSha256") ==
          audit["nativeInputSealSha256"] and
          rejection.get("frozenReaderSha256") == _sha(HERE / "assess.py") and
          rejection.get("frozenCheckerSha256") == _sha(HERE / "check_assessment.py") and
          rejection.get("protocolSha256") == _sha(HERE / "protocol.json"),
          "original framing rejection source pins changed")
    rejection_inputs = rejection.get("phaseInputsSha256")
    _need(type(rejection_inputs) is dict and set(rejection_inputs) == {
        "routes.txt", "routes-receipt.txt", "routes-timing.json",
        "routes-process-cleanup.json",
    }, "original framing rejection input set changed")
    for name, digest in rejection_inputs.items():
        _need(_sha(HERE / name) == digest,
              "original framing rejection input changed: " + name)
    _need("all42reports" in rejection.get("cause", "") and
          "per-pair" in rejection.get("repairBoundary", ""),
          "original framing rejection explanation changed")
    return audit, rejection


def _verify_process_births():
    drivers = 0
    for phase in ("unit", "routes"):
        launch = _read(HERE / (phase + "-process-launch.json"))
        cleanup = _read(HERE / (phase + "-process-cleanup.json"))
        _need(cleanup["status"] == "PASS" and
              cleanup["terminationAttempted"] is False and
              len(launch["drivers"]) == len(cleanup["drivers"]) == 2,
              phase + " process birth/cleanup receipt changed")
        for before, after in zip(launch["drivers"], cleanup["drivers"]):
            _need(all(before[key] == after[key]
                      for key in ("pid", "startTicks", "executable")) and
                  after["outcome"] == "EXITED" and
                  after["sameCreationStillRunning"] is False,
                  phase + " driver cleanup identity changed")
            drivers += 1
    _need(drivers == 4, "recorded driver birth count changed")
    return drivers


def _verify_native_artifacts(contract, prior, canaries):
    unit_path = HERE / "unit-validation.json"
    _need(unit_path.is_file(), "successful unit validation is missing")
    unit = _read(unit_path)
    unit_summary, unit_inputs, unit_manifest = assess._read_phase_files(
        "unit", contract, prior)
    expected_unit = assess._unit_validation(
        contract, unit_summary, unit_inputs, unit_manifest)
    _need(assess._typed_equal(unit, expected_unit),
          "unit validation artifact does not replay exactly")
    unit_sha = assess._validate_unit_prerequisite(unit_path.read_bytes(), expected_unit)

    route_summary, route_inputs, route_manifest = assess._read_phase_files(
        "routes", contract, prior)
    expected_assessment = assess._routes_assessment(
        contract, route_summary, route_inputs, route_manifest, unit_sha)
    assessment = _read(HERE / "assessment.json")
    _need(assess._typed_equal(assessment, expected_assessment),
          "routes assessment artifact does not replay exactly")
    _need(check_assessment._run_canaries() == canaries,
          "pure reader canary replay changed")
    return unit_sha, assessment


def result():
    seal = _read(HERE / "native-inputs.json")
    _verify_main_and_parent_seals(seal)
    failed_attempt_drivers = _verify_failure_archive(seal)
    contract = assess._load_contract()
    reuse_audit, framing_rejection = _verify_reuse_boundary(contract)
    _verify_source_review(seal)
    prior = assess._load_prior_contract(contract)
    canaries = _read(HERE / "assessment-canary-results.json")
    _need(canaries["status"] == "PASS" and canaries["canaryCount"] >= 80,
          "reader canary receipt is incomplete")
    transport = _verify_transport_and_ack(contract, canaries)
    order_canaries = _verify_order_adapter(canaries)
    _verify_native_artifacts(contract, prior, canaries)
    drivers = _verify_process_births()
    analysis = analyze.result()
    _need(_read(HERE / "descent-analysis.json") == analysis,
          "derived cohort analysis differs from its sealed artifact")
    restoration = _read(HERE / "copy-restoration.json")
    _need(restoration == {
        "status": "PASS", "fromCount": 1481, "restoredCount": 1467,
        "main1475Exact": True,
    }, "cohort copy restoration receipt changed")
    cleanup = _read(HERE / "copy-cleanup.json")
    _need(cleanup == {
        "status": "PASS", "verifiedExactFilesRemoved": 1467,
        "privateStateRemoved": True, "symlinkOrReparseBoundariesCrossed": False,
        "sharedResourcesTouched": False,
    }, "cohort copy cleanup receipt changed")
    _need(not (HERE / "local-state.json").exists(),
          "private cohort copy state remains after retirement")

    return {
        "integrityReviewStatus": "PASS",
        "nativeCohortStatus": "PASS",
        "measurementDisposition": analysis["measurementDisposition"],
        "baseCommit": contract["protocol"]["baseCommit"],
        "main1475Exact": True,
        "sourceDeltas": 18,
        "frozenGates": 28,
        "acceptedAndPublishedOwnerErasureExact": True,
        "nativeFailureAttemptPreserved": True,
        "nativeReuseAuditStatus": reuse_audit["status"],
        "nativeReuseAuditSha256": _sha(HERE / "native-reuse-audit.json"),
        "postNativeReceiptFramingAdapter": True,
        "originalFrozenRouteReaderExit2Preserved": framing_rejection["status"] == "REJECTED" and
        framing_rejection["exitCode"] == 2,
        "readerFramingRejectionSha256": _sha(HERE / "reader-framing-failure.json"),
        "syntheticReaderCases": canaries["canaryCount"],
        "receiptOrderReaderCases": order_canaries["canaryCount"],
        "receiptOrderFreshCases": order_canaries["freshCanaryCount"],
        "receiptOrderAdapterSha256": order_canaries["adapterSource"]["adaptedAssessSha256"],
        "actualTransportCases": transport["caseCount"],
        "unitSuites": 5,
        "unitFixtureCounts": canaries["fixtureCounts"],
        "routeSuites": 7,
        "selectedRowsExact42Fields": len(analysis["roots"]),
        "routeReportsExact": len(analysis["roots"]) * 3,
        "roots": [
            {
                "seed": row["seed"],
                "acceptedSteps": row["aggregate"]["acceptedSteps"],
                "evaluatedMoves": row["aggregate"]["evaluatedMoves"],
                "areaReduction": row["areaReduction"],
                "copperReduction": row["copperReduction"],
                "termination": row["termination"],
                "acceptedSequence": row["acceptedSequence"],
            }
            for row in analysis["roots"]
        ],
        "recordedDriverBirthsExited": drivers + failed_attempt_drivers,
        "finalDriverBirthsExited": drivers,
        "failedAttemptDriverBirthsExited": failed_attempt_drivers,
        "copyRestored1481AndRetired": True,
        "productionPolicyChange": "NONE",
        "GwtPlayerSolverFullMatrixUnseen": "NOT_RUN",
        "limits": list(contract["protocol"]["limitsDisclosure"]),
    }


def main():
    value = result()
    target = HERE / "root-review.json"
    if target.exists():
        _need(_read(target) == value,
              "existing root-review.json differs; preserve it")
    else:
        target.write_text(json.dumps(value, indent=2) + "\n",
                          encoding="utf-8", newline="\n")
    print("PASS cohort integrity/native gates/chain review; measurement=",
          value["measurementDisposition"])


if __name__ == "__main__":
    main()
