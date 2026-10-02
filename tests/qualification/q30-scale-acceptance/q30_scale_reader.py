#!/usr/bin/env python3
"""Strict plan-4 reader for per-root ordinary cold Q30 acceptance receipts."""
import argparse
import hashlib
import importlib.util
import json
import math
import re
import sys
from pathlib import Path


PACKAGE = Path(__file__).resolve().parent
READER_PATH = PACKAGE.parent / "q30-normal-screen" / "q30_screen_reader.py"
SPEC = importlib.util.spec_from_file_location("q30_normal_screen_reader", READER_PATH)
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
LIMITS = {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000}
MANIFEST = "docs/task-evidence/Q30/scale-plan-4/manifest-proposal.json"
LONG_MIN, LONG_MAX = -(1 << 63), (1 << 63) - 1
HASH = re.compile(r"^[0-9a-f]{64}$")
MATRIX_OBSERVATION_SCOPE = (
    "Existing 25 ms observer plus successful publication; sampled live reduced "
    "matrix order, not an exhaustive trial maximum"
)
MATRIX_OBSERVATION_FIELDS = (
    "observedMatrixOrderMax", "matrixObservations", "matrixObservationScope",
)


class Invalid(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise Invalid(message)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def pairs(rows):
    result = {}
    for key, value in rows:
        if key in result:
            raise Invalid("duplicate JSON key: " + key)
        result[key] = value
    return result


def bad_constant(value):
    raise Invalid("non-finite JSON constant: " + value)


def load(path):
    raw = Path(path).read_bytes()
    try:
        obj = json.loads(raw.decode("utf-8"), object_pairs_hook=pairs,
                         parse_constant=bad_constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise Invalid("invalid UTF-8 JSON %s: %s" % (path, error)) from error
    need(isinstance(obj, dict), "expected JSON object: " + str(path))
    return obj, raw


def canonical_long(value, label):
    need(isinstance(value, str) and bool(value), label + " must be decimal text")
    try:
        parsed = int(value, 10)
    except ValueError as error:
        raise Invalid(label + " is not decimal") from error
    need(LONG_MIN <= parsed <= LONG_MAX and str(parsed) == value,
         label + " is not canonical signed-long text")
    return parsed


def frozen_acceptance(acceptance_path=None):
    """A continuation can rebind HEAD, while every frozen contract stays exact."""
    bundled, bundled_raw = load(PACKAGE / "acceptance-plan.json")
    if acceptance_path is None:
        return bundled, bundled_raw
    continued, continued_raw = load(acceptance_path)
    continued_contract = {key: value for key, value in continued.items() if key != "baseHead"}
    bundled_contract = {key: value for key, value in bundled.items() if key != "baseHead"}
    need(json.dumps(continued_contract, sort_keys=True, separators=(",", ":")) ==
         json.dumps(bundled_contract, sort_keys=True, separators=(",", ":")),
         "continued acceptance plan changes the frozen cohort or contracts")
    need(isinstance(continued.get("baseHead"), str) and
         re.fullmatch(r"[0-9a-f]{40}", continued["baseHead"]),
         "continued acceptance plan base HEAD is malformed")
    return continued, continued_raw


def validate_plan(plan, raw, acceptance_path=None):
    acceptance, acceptance_raw = frozen_acceptance(acceptance_path)
    need(plan.get("schema") == 1 and plan.get("status") == "PLANNED" and
         plan.get("planEpoch") == 4, "plan schema/status/epoch mismatch")
    need(plan.get("acceptancePlanSha256") == digest(acceptance_raw),
         "plan does not bind the bundled frozen acceptance plan")
    need(plan.get("baseHead") == acceptance.get("baseHead"), "plan source HEAD mismatch")
    need(isinstance(plan.get("baseHead"), str) and
         re.fullmatch(r"[0-9a-f]{40}", plan["baseHead"]), "plan base HEAD is malformed")
    need(plan.get("manifest") == MANIFEST and
         plan.get("manifestSha256") == acceptance.get("manifestSha256"),
         "plan-4 source manifest identity mismatch")
    need(plan.get("limits") == LIMITS, "authorized generation budgets changed")
    for key in ("compiledJavaExportSha256", "sourceIdentity", "sourceSnapshotSha256",
                "wrapperIdentity", "hostRunnerSha256", "baselinePatchSha256"):
        value = plan.get(key)
        need(isinstance(value, str) and HASH.fullmatch(value),
             "plan lacks a valid " + key)
    export_evidence = plan.get("manifestExportEvidence")
    need(isinstance(export_evidence, dict) and
         export_evidence.get("outcome") == "PASS" and
         export_evidence.get("reportSha256") == plan["compiledJavaExportSha256"] and
         isinstance(export_evidence.get("hostRecordSha256"), str) and
         HASH.fullmatch(export_evidence["hostRecordSha256"]) and
         all(isinstance(export_evidence.get(k), str) and export_evidence[k]
             for k in ("startedUtc", "finishedUtc")),
         "plan lacks the passing compiled-export host/timestamp evidence")
    files = plan.get("sourceFiles")
    need(isinstance(files, list) and bool(files), "plan lacks source file hashes")
    seen_paths = set()
    for row in files:
        need(isinstance(row, dict) and isinstance(row.get("path"), str) and
             type(row.get("size")) is int and row["size"] >= 0 and
             isinstance(row.get("sha256"), str) and HASH.fullmatch(row["sha256"]),
             "malformed source identity row")
        need(row["path"] not in seen_paths, "duplicate source identity path")
        seen_paths.add(row["path"])
    identity = digest(json.dumps({"schema": 1, "baseHead": plan["baseHead"],
                                  "files": files}, sort_keys=True,
                                 separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
    need(identity == plan.get("sourceIdentity"), "source file hashes do not match sourceIdentity")
    input_id = plan.get("preparedInputIdentity")
    need(isinstance(input_id, dict) and
         type(input_id.get("fileCount")) is int and input_id["fileCount"] > 0 and
         type(input_id.get("sourceFileCount")) is int and input_id["sourceFileCount"] > 0 and
         type(input_id.get("webFileCount")) is int and input_id["webFileCount"] > 0 and
         all(isinstance(input_id.get(k), str) and HASH.fullmatch(input_id[k])
             for k in ("combinedSha256", "sourceSha256", "webSha256")),
         "prepared source/web input identity is malformed")
    cases, order = plan.get("cases"), plan.get("coldOrder")
    frozen = acceptance.get("cases")
    need(plan.get("rootCount") == 77 and isinstance(cases, list) and len(cases) == 77 and
         isinstance(order, list) and len(order) == 77 and
         isinstance(frozen, list) and len(frozen) == 77,
         "plan must retain all 77 frozen roots")
    by_seed = {}
    for index, (row, source) in enumerate(zip(cases, frozen)):
        need(isinstance(row, dict) and row.get("manifestOrder") == index,
             "case order mismatch at %d" % index)
        seed = row.get("seed")
        canonical_long(seed, "root seed")
        for key in ("seed", "cohort", "canonical", "packages", "channels"):
            expected = source.get("cohort" if key == "cohort" else
                                  "canonical" if key == "canonical" else
                                  "packages" if key == "packages" else
                                  "channels" if key == "channels" else "seed")
            need(row.get(key) == expected, "case differs from frozen manifest at seed " + seed)
        canonical = row.get("canonical")
        need(isinstance(canonical, str) and canonical.startswith("rb30-plan@4;seed=" + seed + ";") and
             20 <= row["packages"] <= 40 and row["channels"] in (1, 2),
             "root plan identity/package/channel envelope mismatch")
        candidates = row.get("candidates")
        need(isinstance(candidates, list) and len(candidates) == 4,
             "root must bind four Java candidate manifests")
        candidate_seeds = set()
        for ordinal, candidate in enumerate(candidates):
            need(isinstance(candidate, dict) and candidate.get("ordinal") == ordinal,
                 "candidate order mismatch for root " + seed)
            cseed = candidate.get("seed")
            canonical_long(cseed, "candidate seed")
            need(cseed not in candidate_seeds, "duplicate candidate seed")
            candidate_seeds.add(cseed)
            need(isinstance(candidate.get("manifest"), str) and
                 candidate["manifest"].startswith("candidate=%02d;" % ordinal) and
                 "seed=" + cseed + ";" in candidate["manifest"] and
                 "plan=rb30-plan@4;" in candidate["manifest"],
                 "candidate manifest identity/epoch mismatch")
            need(candidate.get("planCanonical", "").startswith("rb30-plan@4;seed=" + cseed + ";") and
                 type(candidate.get("packages")) is int and 20 <= candidate["packages"] <= 40 and
                 type(candidate.get("channels")) is int and candidate["channels"] in (1, 2),
                 "candidate plan/package/channel mismatch")
        first_exact = candidates[0]["manifest"].split(";", 1)[1]
        expected_request = first_exact.replace(";search=false;", ";search=true;", 1)
        need(row.get("requestCanonical") == expected_request,
             "root request is not the frozen ordinary four-candidate search")
        by_seed[seed] = row
    need(len(by_seed) == 77 and len(set(order)) == 77 and set(order) == set(by_seed),
         "cold order is not a permutation of all frozen roots")
    need(order == acceptance.get("coldOrder"), "cold order differs from frozen acceptance order")
    return by_seed


def validate_cache_cleanup(report):
    for key in ("initialCachesEmpty", "privateCacheUntouched", "cleanupComplete"):
        need(type(report.get(key)) is bool and report[key] is True,
             key + " must be true")
    need(report.get("ordinaryCacheHits") == 0,
         "ordinary proof cache was warm")


def validate_matrix_observations(report):
    """Validate optional telemetry added by the current isolated overlay."""
    present = [key in report for key in MATRIX_OBSERVATION_FIELDS]
    if not any(present):
        return  # r1-r3 historical receipts predate this optional telemetry.
    need(all(present), "matrix observation metadata is incomplete")
    maximum = report.get("observedMatrixOrderMax")
    observations = report.get("matrixObservations")
    need(type(maximum) is int and maximum >= 0,
         "observed matrix maximum must be a nonnegative JSON integer")
    need(type(observations) is int and observations >= 0,
         "matrix observation count must be a nonnegative JSON integer")
    need(report.get("matrixObservationScope") == MATRIX_OBSERVATION_SCOPE,
         "matrix observation scope mismatch")
    need((observations == 0) == (maximum == 0),
         "observed matrix maximum/count mismatch")
    if report.get("classification") == "PASS":
        need(observations > 0 and maximum > 0,
             "PASS lacks a positive observed matrix order")


def validate_terminal_consistency(report):
    """Tie every non-pass status to the final attempt that produced it."""
    classification = report.get("classification")
    if classification == "PASS":
        return
    outcomes = {
        "TIMEOUT": {"TIMEOUT"},
        "ADMISSION_REJECTED": {"EXPECTED_REJECTION", "WORK_EXHAUSTED"},
        # The harness deliberately reports these terminal GenerationJob
        # outcomes under one infrastructure classification. Keep the actual
        # application outcome bound to the terminal attempt below.
        "INFRASTRUCTURE_FAILURE": {
            "CANCELLED", "STALE", "PROGRAMMING_FAILURE", "INFRASTRUCTURE_FAILURE",
        },
    }
    need(classification in outcomes, "non-pass classification has no terminal contract")
    app_outcome = report.get("applicationOutcome")
    if classification == "TIMEOUT":
        need(app_outcome == "TIMEOUT", "TIMEOUT classification/application outcome mismatch")
    else:
        need(app_outcome in outcomes[classification],
             "admission-rejected classification/application outcome mismatch")
    attempts = report.get("attempts")
    need(isinstance(attempts, list) and 0 < len(attempts) <= 4,
         "non-pass attempt count is empty or exceeds the four-candidate limit")
    terminal = attempts[-1]
    need(isinstance(terminal, dict), "terminal candidate attempt is malformed")
    need(type(terminal.get("ordinal")) is int and
         terminal.get("ordinal") == len(attempts) - 1,
         "terminal attempt ordinal is not the last candidate")
    need(terminal.get("outcome") == app_outcome,
         "terminal attempt outcome differs from application outcome")
    stage = terminal.get("stage")
    need(stage in ("RESOLVE", "HEALTHY", "PHYSICAL", "HYPOTHESES", "SYMPTOM", "PUBLISH") and
         stage == report.get("terminalStage"),
         "terminal attempt stage differs from application terminal stage")
    failure_type, failure_message = terminal.get("failureType"), terminal.get("failureMessage")
    need((failure_type is None and failure_message is None) or
         (isinstance(failure_type, str) and bool(failure_type) and
          isinstance(failure_message, str) and bool(failure_message)),
         "terminal attempt failure type/message are incomplete")
    failure = report.get("failure")
    if classification in ("TIMEOUT", "INFRASTRUCTURE_FAILURE"):
        need(isinstance(failure_type, str) and isinstance(failure_message, str) and
             bool(failure_type) and bool(failure_message) and
             isinstance(failure, str) and bool(failure),
             "terminal failure lacks an exact attempt failure record")
        prefix, separator, message = failure.partition(": ")
        reported_type = prefix.rsplit("$", 1)[-1].rsplit(".", 1)[-1]
        need(separator and reported_type == failure_type and message == failure_message,
             "app failure differs from terminal attempt failure")
    elif classification == "ADMISSION_REJECTED" and app_outcome == "EXPECTED_REJECTION":
        need(isinstance(failure_type, str) and isinstance(failure_message, str),
             "expected rejection lacks terminal failure type/message")
    if classification not in ("TIMEOUT", "INFRASTRUCTURE_FAILURE") and failure is not None:
        need(isinstance(failure_type, str) and isinstance(failure_message, str),
             "application failure is not bound to terminal attempt failure")
        prefix, separator, message = failure.partition(": ")
        reported_type = prefix.rsplit("$", 1)[-1].rsplit(".", 1)[-1]
        need(separator and reported_type == failure_type and message == failure_message,
             "application failure differs from terminal attempt failure")


def validate_proof(proof, sample_count):
    need(isinstance(proof, dict), "PASS lacks diagnostic proof")
    need(type(proof.get("warmReuse")) is bool and proof["warmReuse"] is False,
         "proof reused a warm cache")
    need(proof.get("explicitCompletion") is True, "proof lacks explicit completion")
    for key in ("context", "program", "partition"):
        need(isinstance(proof.get(key), str) and bool(proof[key]), "proof lacks " + key)
    evidence = proof.get("evidence")
    need(isinstance(evidence, list) and len(evidence) == 5,
         "proof must contain all five hypotheses")
    hypotheses = set()
    for row in evidence:
        need(isinstance(row, dict), "hypothesis evidence malformed")
        hypothesis = row.get("hypothesis")
        need(isinstance(hypothesis, str) and hypothesis and hypothesis not in hypotheses,
             "missing or duplicate hypothesis")
        hypotheses.add(hypothesis)
        for key in ("repairReachable", "customerRetestPassed", "stateIsolated"):
            need(row.get(key) is True, "hypothesis proof lacks " + key)
        need(isinstance(row.get("deterministicResult"), str) and row["deterministicResult"],
             "hypothesis deterministic result missing")
        samples = row.get("samples")
        need(isinstance(samples, list) and len(samples) == sample_count,
             "hypothesis sample count differs from candidate channel contract")
        ids = set()
        for sample in samples:
            need(isinstance(sample, dict), "sample evidence malformed")
            sid = sample.get("id")
            need(isinstance(sid, str) and sid and sid not in ids, "missing or duplicate sample id")
            ids.add(sid)
            outcome = sample.get("outcome")
            need(outcome in ("NUMERIC", "OVER_RANGE"), "unknown solver sample outcome")
            if outcome == "NUMERIC":
                for key in ("value", "tolerance"):
                    value = sample.get(key)
                    need(type(value) in (int, float) and math.isfinite(value),
                         "invalid numeric solver sample")
                need(sample["tolerance"] >= 0, "negative solver tolerance")
            else:
                need("value" not in sample and "tolerance" not in sample,
                     "over-range sample contains numeric payload")


def app_ok(report, raw, plans, plan):
    need(report.get("sourceIdentity") == plan["sourceIdentity"] and
         report.get("manifestSha256") == plan["manifestSha256"],
         "app source identity or frozen manifest hash mismatch")
    need(report.get("planVersion") == 4, "app plan epoch mismatch")
    validate_cache_cleanup(report)
    validate_matrix_observations(report)
    validate_terminal_consistency(report)
    seed = report.get("rootSeed")
    case = plans.get(seed)
    need(case is not None, "root seed is not in the frozen plan")
    need(report.get("sourceCommit") == plan["baseHead"] and
         report.get("planCanonical") == case["canonical"] and
         type(report.get("plannedPackages")) is int and
         report.get("plannedPackages") == case["packages"] and
         type(report.get("channels")) is int and
         report.get("channels") == case["channels"] and
         report.get("requestCanonical") == case["requestCanonical"],
         "app root plan/package/channel/request differs from frozen Java export")
    if report.get("classification") == "PASS":
        actual_candidates = report.get("candidates")
        need(isinstance(actual_candidates, list), "PASS lacks candidate rows")
        published = [row.get("ordinal") for row in actual_candidates
                     if isinstance(row, dict) and row.get("published") is True]
        need(len(published) == 1 and type(published[0]) is int and
             0 <= published[0] < len(case["candidates"]),
             "PASS lacks one in-range published candidate")
        channels = case["candidates"][published[0]]["channels"]
        validate_proof(report.get("proof"), 17 if channels == 1 else 37)
    # Reuse the established screen reader's full budget, attempt-order, owner,
    # timing, and receipt checks after adapting only the epoch-4 case shape.
    adapted = dict(report)
    adapted["planVersion"] = 3
    if report.get("classification") == "INFRASTRUCTURE_FAILURE":
        # The older screen reader short-circuits PROGRAMMING_FAILURE and
        # INFRASTRUCTURE_FAILURE before checking request identity, budgets,
        # attempts, owner flags and timing. Terminal consistency above has
        # already validated the real classification/outcome/failure tuple.
        # Reuse its full non-pass envelope checks with this private validation
        # view; attempt rows stay untouched and the returned labels are restored.
        adapted["classification"] = "ADMISSION_REJECTED"
        adapted["applicationOutcome"] = "WORK_EXHAUSTED"
    old_plan = {}
    for key, row in plans.items():
        old_plan[key] = {
            "seed": key,
            "planCanonical": row["canonical"],
            "declaredPackageCount": row["packages"],
            "recipeComponentCount": row["packages"],
            "plannedCandidates": [
                {"ordinal": c["ordinal"], "seed": c["seed"],
                 "declaredPackageCount": c["packages"], "manifest": c["manifest"]}
                for c in row["candidates"]],
        }
    BASE.SOURCE = plan["baseHead"]
    # Proof was checked above against the actual channel-dependent sample
    # count. Suppress only the legacy fixed-37 predicate while reusing its
    # independent attempt, budget, publication, and receipt checks.
    original_proof_check = BASE.proof_ok
    if report.get("classification") == "PASS":
        BASE.proof_ok = lambda proof: None
    try:
        checked = BASE.app_ok(adapted, raw, old_plan)
    finally:
        BASE.proof_ok = original_proof_check
    need(checked.get("status") ==
         ("APP_PASS" if report.get("classification") == "PASS" else "COMPLETED_NONPASS"),
         "base screen reader changed the strict pass/non-pass result")
    checked["classification"] = report.get("classification")
    checked["applicationOutcome"] = report.get("applicationOutcome")
    return checked


def host_ok(host, report, report_raw, plan):
    checked = BASE.host_ok(host, report, report_raw)
    audit = host.get("inputAudit", {})
    expected = plan["preparedInputIdentity"]
    for actual, want, label in (
            (audit.get("sourceSha256Before"), expected["sourceSha256"], "source"),
            (audit.get("sourceSha256After"), expected["sourceSha256"], "source after"),
            (audit.get("webSha256Before"), expected["webSha256"], "web"),
            (audit.get("webSha256After"), expected["webSha256"], "web after"),
            (audit.get("runnerBeforeSha256"), plan["hostRunnerSha256"], "host runner"),
            (audit.get("runnerAfterSha256"), plan["hostRunnerSha256"], "host runner after"),
            (audit.get("fileCountBefore"), expected["fileCount"], "prepared input count"),
            (audit.get("fileCountAfter"), expected["fileCount"], "prepared input count after")):
        if actual != want:
            checked["errors"].append(label + " hash/count differs from frozen plan")
    if checked["errors"]:
        checked["status"] = "HOST_FAIL"
    return checked


def evaluate(report, report_raw, plan_obj, plan_raw, host=None, acceptance_path=None):
    plans = validate_plan(plan_obj, plan_raw, acceptance_path)
    app = app_ok(report, report_raw, plans, plan_obj)
    host_result = host_ok(host, report, report_raw, plan_obj) if host is not None else None
    status = app["status"]
    if status == "APP_PASS" and host_result and host_result["status"] != "HOST_PASS":
        status = "APP_PASS_HOST_FAIL"
    return {"status": status, "app": app, "host": host_result, "synthetic": False}


def qualification_code_digests():
    """Return exact helper hashes without changing the prepared app receipt."""
    paths = {
        "readerSha256": Path(__file__).resolve(),
        "freezePlanSha256": PACKAGE / "freeze_plan.py",
    }
    return {key: digest(path.read_bytes()) for key, path in paths.items()}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--plan", required=True)
    parser.add_argument("--host-record")
    parser.add_argument("--acceptance-plan", help="Explicit frozen continuation; only baseHead may differ")
    parser.add_argument("--output")
    args = parser.parse_args(argv)
    code_digests = qualification_code_digests()
    plan_digest = None
    try:
        plan, plan_raw = load(args.plan)
        plan_digest = digest(plan_raw)
        report, report_raw = load(args.report)
        host = load(args.host_record)[0] if args.host_record else None
        result = evaluate(report, report_raw, plan, plan_raw, host, args.acceptance_plan)
        code = 0 if result["status"] == "APP_PASS" else 1
    except (OSError, Invalid, KeyError, TypeError, IndexError) as error:
        result = {"status": "FAIL_INVALID", "app": {"status": "FAIL_INVALID",
                  "errors": [str(error)]}, "host": None, "synthetic": False}
        code = 2
    result["scalePlanSha256"] = plan_digest
    result.update(code_digests)
    output = json.dumps(result, sort_keys=True, indent=2, allow_nan=False) + "\n"
    if args.output:
        Path(args.output).write_text(output, encoding="utf-8")
    print(output, end="")
    return code


if __name__ == "__main__":
    sys.exit(main())
