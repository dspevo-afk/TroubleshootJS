#!/usr/bin/env python3
"""Independently audit the maintained Q30 service and solver-step receipts.

This reader consumes the stdout log and ReceiptOutputPath emitted by
scripts/verify-current-contracts.ps1.  It audits an explicitly requested set
of eight signed-long seeds across the fixed five-fault census.  It does not
launch Java, infer missing rows as successes, or accept the retired 10 us
experimental sensitivity profile.
"""

import argparse
import copy
import json
import math
import re
import sys
from pathlib import Path


FAULTS = (
    "RELAY_B_COIL_OPEN",
    "DREV_OPEN",
    "REN_OPEN",
    "SENSOR_A_OPEN",
    "DRIVE_A_OPEN",
)
SENSITIVITY_FAULTS = (
    "DREV_OPEN",
    "REN_OPEN",
    "SENSOR_A_OPEN",
    "DRIVE_A_OPEN",
    "RELAY_B_COIL_OPEN",
)
CURRENT_REFERENCE_STEP = "2.50000000e-06"
CURRENT_PRODUCTION_STEP = "5.00000000e-06"
CURRENT_CANDIDATE_KIND = "PRODUCTION_5_US"
CURRENT_REFERENCE_ROLE = "FINER_2_5_US_REFERENCE"
CURRENT_PRODUCTION_ROLE = "PRODUCTION_5_US"
CHILD_BUDGET_MS = 60000
FULL_RUNNER_SUCCESS_RE = re.compile(
    r"(?m)^PASS: current contracts; \d+ Java suites, independent seed/value/role "
    r"oracles and report protocol\.\r?$"
)
SELECTED_CENSUS_SUCCESS = (
    "PASS: focused current contracts; 2 Java suites. "
    "Full matrix and independent oracles NOT RUN."
)

SERVICE_META_RE = re.compile(
    r"(?m)^Q30_CASE\|([^|\r\n]+)\|([^|\r\n]+)\|(.*)$"
)
SENSITIVITY_META_RE = re.compile(
    r"(?m)^Q30_SENSITIVITY_CASE\s+([^\r\n]+)$"
)
SERVICE_CENSUS_RE = re.compile(
    r"(?m)^(PASS|FAIL): Q30 service flow census attempted=(\d+) passed=(\d+) "
    r"failed=(\d+) failedCases=([^\r\n ]+) seeds=([^\r\n ]+) faults=(\d+) "
    r"order=([^\r\n ]+) childBudgetMs=(\d+)\r?$"
)
SENSITIVITY_CENSUS_RE = re.compile(
    r"(?m)^Q30_SENSITIVITY_CENSUS attempted=(\d+) passed=(\d+) failed=(\d+) "
    r"childBudgetMs=(\d+)\r?$"
)
PASS_SERVICE_ROW_RE = re.compile(
    r"^PASS: Q30 service flow seed=(-?(?:0|[1-9][0-9]*)) "
    r"fault=([A-Z0-9_]+) target=[A-Z0-9_]+ fingerprintHash=[0-9a-fA-F]{1,8}$"
)
PASS_SERVICE_FINAL_RE = re.compile(
    r"^PASS: Q30 service flow contracts \d+ assertions "
    r"seed=(-?(?:0|[1-9][0-9]*)) fault=([A-Z0-9_]+)$"
)
SERVICE_TIME_RE = re.compile(
    r"^Q30_CASE_TIME operation=route-replay-and-service elapsedMillis=(\d+)$"
)
PAIR_RE = re.compile(r"(?m)^Q30_STEP_PAIR ([^\r\n]+)$")
OWNER_CLEANUP_RE = re.compile(r"(?m)^Q30_STEP_OWNER_CLEANUP ([^\r\n]+)$")
SEED_CLEANUP_RE = re.compile(r"(?m)^Q30_STEP_SEED_CLEANUP ([^\r\n]+)$")
SENSITIVITY_FINAL_RE = re.compile(
    r"^PASS: Q30 production solver step sensitivity assertions=\d+ "
    r"seed=(-?(?:0|[1-9][0-9]*)) support=[A-Z0-9_]+ faults=1 "
    r"referenceMaximumStepSeconds=([^ ]+) "
    r"productionCandidateMaximumStepSeconds=([^ ]+) "
    r"candidateKind=([^ ]+) elapsedMillis=(\d+)$"
)
TOKEN_RE = re.compile(r"([A-Za-z][A-Za-z0-9]*)=([^\s|]+)")
SEED_RE = re.compile(r"(?:0|[1-9][0-9]*|-[1-9][0-9]*)\Z")
SIGNED_LONG_MIN = -(2**63)
SIGNED_LONG_MAX = (2**63) - 1


class AuditInputError(Exception):
    """An input cannot be read or is not a valid audit request."""


def parse_seed_csv(value):
    parts = value.split(",")
    if len(parts) != 8:
        raise AuditInputError("--seeds must contain exactly eight CSV values")
    if any(not SEED_RE.fullmatch(part) for part in parts):
        raise AuditInputError("--seeds must use canonical signed-long decimal values")
    parsed = [int(part, 10) for part in parts]
    if any(number < SIGNED_LONG_MIN or number > SIGNED_LONG_MAX for number in parsed):
        raise AuditInputError("--seeds contains a value outside signed-long range")
    if len(set(parts)) != len(parts):
        raise AuditInputError("--seeds values must be distinct")
    return parts


def read_text(path, label):
    try:
        return Path(path).read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise AuditInputError("cannot read {} {}: {}".format(label, path, exc)) from exc
    except UnicodeError as exc:
        raise AuditInputError("{} is not valid UTF-8: {}".format(label, path)) from exc


def parse_key_values(text):
    return {match.group(1): match.group(2) for match in TOKEN_RE.finditer(text)}


def truth(value):
    if value == "True":
        return True
    if value == "False":
        return False
    return None


def ints(value):
    if isinstance(value, int):
        return value
    if isinstance(value, str) and re.fullmatch(r"-?(?:0|[1-9][0-9]*)", value):
        return int(value)
    return None


def parse_service_entries(receipt):
    matches = list(SERVICE_META_RE.finditer(receipt))
    entries = []
    for match in matches:
        seed, fault, suffix = match.groups()
        fields = {}
        for item in suffix.split("|"):
            if "=" in item:
                key, value = item.split("=", 1)
                fields[key] = value.rstrip("\r")
        tail = receipt[match.end():]
        stdout = ""
        stderr = ""
        block = re.search(
            r"STDOUT_BEGIN\r?\n(.*?)\r?\nSTDOUT_END\r?\n"
            r"STDERR_BEGIN\r?\n(.*?)\r?\nSTDERR_END",
            tail,
            re.DOTALL,
        )
        if block:
            stdout, stderr = block.groups()
            raw_block = tail[:block.end()]
        else:
            raw_block = match.group(0)
        entries.append({
            "seed": seed,
            "fault": fault,
            "metadata": fields,
            "metadataLine": match.group(0),
            "stdout": stdout,
            "stderr": stderr,
            "rawBlock": raw_block,
        })
    return entries


def parse_sensitivity_entries(receipt):
    matches = list(SENSITIVITY_META_RE.finditer(receipt))
    entries = []
    for index, match in enumerate(matches):
        fields = parse_key_values(match.group(1))
        start = match.end()
        next_case = matches[index + 1].start() if index + 1 < len(matches) else len(receipt)
        census = SENSITIVITY_CENSUS_RE.search(receipt, start, next_case)
        if census:
            next_case = min(next_case, census.start())
        entries.append({
            "seed": fields.get("seed"),
            "fault": fields.get("fault"),
            "metadata": fields,
            "metadataLine": match.group(0),
            "body": receipt[start:next_case],
        })
    return entries


def parse_service_census(log):
    matches = list(SERVICE_CENSUS_RE.finditer(log))
    parsed = []
    for match in matches:
        parsed.append({
            "status": match.group(1),
            "attempted": int(match.group(2)),
            "passed": int(match.group(3)),
            "failed": int(match.group(4)),
            "failedCases": match.group(5),
            "seeds": match.group(6).split(","),
            "faults": int(match.group(7)),
            "order": match.group(8).split(","),
            "childBudgetMs": int(match.group(9)),
            "raw": match.group(0),
        })
    return parsed


def parse_sensitivity_census(log):
    matches = list(SENSITIVITY_CENSUS_RE.finditer(log))
    return [
        {
            "attempted": int(match.group(1)),
            "passed": int(match.group(2)),
            "failed": int(match.group(3)),
            "childBudgetMs": int(match.group(4)),
            "raw": match.group(0),
        }
        for match in matches
    ]


def run_cleanup_audit(log, runner_scope="full"):
    success = re.findall(
        r"(?m)^CLEANUP: current JVM contract classes and task-owned scratch removed\.\r?$",
        log,
    )
    failures = re.findall(r"(?m)^CURRENT_CONTRACT_CLEANUP:.*$", log)
    full_runner_success = FULL_RUNNER_SUCCESS_RE.findall(log)
    selected_runner_success = re.findall(
        r"(?m)^" + re.escape(SELECTED_CENSUS_SUCCESS) + r"\r?$", log
    )
    runner_failures = re.findall(r"(?m)^CURRENT_CONTRACT_FAILURE:.*$", log)
    timeout_diagnostics = re.findall(
        r"(?im)^.*Bounded process .* exceeded 60000ms; logs retained at .*",
        log,
    )
    scope_issues = []
    if runner_scope == "full":
        runner_success = full_runner_success
        if selected_runner_success:
            scope_issues.append("focused selected-census marker is not full-matrix evidence")
        if len(full_runner_success) != 1:
            scope_issues.append("exactly one full-runner PASS marker is required")
    elif runner_scope == "selected-census":
        runner_success = selected_runner_success
        if full_runner_success:
            scope_issues.append("full-runner marker cannot be relabeled as selected-census evidence")
        if len(selected_runner_success) != 1:
            scope_issues.append(
                "exactly one focused two-suite PASS marker is required; other suite counts are not accepted"
            )
    else:
        runner_success = []
        scope_issues.append("unknown runner scope")
    return {
        "runnerScope": runner_scope,
        "cleanupSuccessMarkers": len(success),
        "cleanupFailureMarkers": failures,
        "runnerSuccessMarkers": len(full_runner_success) + len(selected_runner_success),
        "acceptedRunnerSuccessMarkers": len(runner_success),
        "runnerScopeIssues": scope_issues,
        "runnerFailureMarkers": runner_failures,
        "unattributedTimeoutDiagnostics": timeout_diagnostics,
        "timeoutPolicy": (
            "A row is TIMEOUT only when receipt metadata explicitly records timeout=True "
            "and termination=True; unattributed timeout text alone remains a failed run."
        ),
        "passed": len(success) == 1 and not failures and
                  len(runner_success) == 1 and not scope_issues and not runner_failures,
    }


def run_runner_scope_selftests():
    cleanup = "CLEANUP: current JVM contract classes and task-owned scratch removed.\n"
    selected = SELECTED_CENSUS_SUCCESS + "\n"
    full = "PASS: current contracts; 84 Java suites, independent seed/value/role oracles and report protocol.\n"
    cases = (
        ("selected marker accepted only in selected-census scope",
         run_cleanup_audit(cleanup + selected, "selected-census")["passed"]),
        ("selected marker rejected by default full scope",
         not run_cleanup_audit(cleanup + selected)["passed"]),
        ("missing runner marker rejected",
         not run_cleanup_audit(cleanup, "selected-census")["passed"]),
        ("wrong focused suite count rejected",
         not run_cleanup_audit(
             cleanup + selected.replace("2 Java suites", "1 Java suite"),
             "selected-census")["passed"]),
        ("full marker cannot be relabeled as selected-census",
         not run_cleanup_audit(cleanup + full, "selected-census")["passed"]),
        ("selected marker cannot satisfy full scope",
         not run_cleanup_audit(cleanup + selected, "full")["passed"]),
    )
    return [
        {"name": name, "status": "PASS" if passed else "FAIL"}
        for name, passed in cases
    ]


def service_entry_issues(entry, seed, fault):
    issues = []
    if entry.get("seed") != seed:
        issues.append("receipt seed does not match expected seed")
    if entry.get("fault") != fault:
        issues.append("receipt fault does not match expected fault")
    fields = entry.get("metadata", {})
    outcome = fields.get("outcome")
    reason = fields.get("reason", "")
    explicit_timeout = fields.get("timeout") == "True" and fields.get("termination") == "True"
    if outcome == "TIMEOUT":
        if not explicit_timeout:
            issues.append("TIMEOUT lacks explicit timeout=True and termination=True metadata")
    elif outcome != "PASS":
        issues.append("runner outcome is {}".format(outcome or "missing"))
    elif reason != "qualified":
        issues.append("PASS receipt reason is not qualified")

    stdout = entry.get("stdout", "")
    lines = stdout.splitlines()
    row_matches = [PASS_SERVICE_ROW_RE.fullmatch(line) for line in lines
                   if line.startswith("PASS: Q30 service flow seed=")]
    row_matches = [match for match in row_matches if match]
    if len(row_matches) != 1:
        issues.append("service PASS row is missing, ambiguous, or malformed")
    elif row_matches[0].groups() != (seed, fault):
        issues.append("service PASS row identifies a different seed or fault")

    final_lines = [line for line in lines if line.startswith("PASS: Q30 service flow contracts ")]
    final_matches = [PASS_SERVICE_FINAL_RE.fullmatch(line) for line in final_lines]
    final_matches = [match for match in final_matches if match]
    if len(final_matches) != 1:
        issues.append("service final PASS marker is missing, ambiguous, or malformed")
    elif final_matches[0].groups() != (seed, fault):
        issues.append("service final PASS marker identifies a different seed or fault")

    timing_matches = [SERVICE_TIME_RE.fullmatch(line) for line in lines
                      if line.startswith("Q30_CASE_TIME ")]
    timing_matches = [match for match in timing_matches if match]
    elapsed = int(timing_matches[0].group(1)) if len(timing_matches) == 1 else None
    if len(timing_matches) != 1:
        issues.append("service operation timing is missing, ambiguous, or malformed")
    if not stdout:
        issues.append("receipt has no delimited child stdout")
    return {
        "issues": issues,
        "elapsedMillis": elapsed,
        "status": "PASS" if not issues else ("TIMEOUT" if explicit_timeout else "FAIL"),
        "failureEvidence": {
            "metadataLine": entry.get("metadataLine"),
            "reason": reason,
            "stdout": stdout,
            "stderr": entry.get("stderr", ""),
        } if issues else None,
    }


def parse_pair_body(body):
    matches = list(PAIR_RE.finditer(body))
    pairs = []
    for match in matches:
        pairs.append(parse_key_values(match.group(1)))
    return pairs


def sensitivity_entry_issues(entry, seed, fault):
    issues = []
    if entry.get("seed") != seed:
        issues.append("receipt seed does not match expected seed")
    if entry.get("fault") != fault:
        issues.append("receipt fault does not match expected fault")
    metadata = entry.get("metadata", {})
    exit_code = ints(metadata.get("exit"))
    termination = truth(metadata.get("termination"))
    qualified = truth(metadata.get("qualified"))
    explicit_timeout = metadata.get("timeout") == "True" and termination is True
    if explicit_timeout:
        issues.append("runner recorded a terminated child timeout")
    else:
        if termination is not True:
            issues.append("child termination is unproven")
        if exit_code != 0:
            issues.append("child exit code is {}".format(metadata.get("exit", "missing")))
        if qualified is not True:
            issues.append("runner qualified flag is not True")

    body = entry.get("body", "")
    pairs = parse_pair_body(body)
    selected_pairs = [pair for pair in pairs
                      if pair.get("seed") == seed and pair.get("fault") == fault]
    if len(selected_pairs) != 1:
        issues.append("exactly one requested Q30_STEP_PAIR is required")
        pair = selected_pairs[0] if selected_pairs else (pairs[0] if pairs else {})
    else:
        pair = selected_pairs[0]

    expected_values = {
        "referenceMaximumStepSeconds": CURRENT_REFERENCE_STEP,
        "productionCandidateMaximumStepSeconds": CURRENT_PRODUCTION_STEP,
        "candidateKind": CURRENT_CANDIDATE_KIND,
        "cleanup": "true",
    }
    for field, expected in expected_values.items():
        if pair.get(field) != expected:
            issues.append("Q30_STEP_PAIR {} must be {} (found {})".format(
                field, expected, pair.get(field, "missing")))
    for field in ("referenceElapsedMillis", "productionCandidateElapsedMillis"):
        if ints(pair.get(field)) is None or ints(pair.get(field)) < 0:
            issues.append("Q30_STEP_PAIR {} is missing or invalid".format(field))
    for field in ("referenceCleanupMillis", "productionCandidateCleanupMillis"):
        if ints(pair.get(field)) is None or ints(pair.get(field)) < 0:
            issues.append("Q30_STEP_PAIR {} is missing or invalid".format(field))

    finals = []
    for line in body.splitlines():
        if line.startswith("PASS: Q30 production solver step sensitivity "):
            match = SENSITIVITY_FINAL_RE.fullmatch(line)
            if match:
                finals.append(match)
    if len(finals) != 1:
        issues.append("current sensitivity final PASS marker is missing, ambiguous, or malformed")
    else:
        final = finals[0]
        if (final.group(1) != seed or final.group(2) != CURRENT_REFERENCE_STEP or
                final.group(3) != CURRENT_PRODUCTION_STEP or
                final.group(4) != CURRENT_CANDIDATE_KIND):
            issues.append("sensitivity final marker is not the current 2.5 us / 5 us profile")

    seed_rows = []
    for line in body.splitlines():
        if line.startswith("Q30_STEP_SEED "):
            seed_rows.append(parse_key_values(line[len("Q30_STEP_SEED "):]))
    if len(seed_rows) != 1:
        issues.append("exactly one Q30_STEP_SEED marker is required")
    elif (seed_rows[0].get("seed") != seed or
          seed_rows[0].get("referenceMaximumStepSeconds") != CURRENT_REFERENCE_STEP or
          seed_rows[0].get("productionCandidateMaximumStepSeconds") != CURRENT_PRODUCTION_STEP or
          seed_rows[0].get("candidateKind") != CURRENT_CANDIDATE_KIND):
        issues.append("Q30_STEP_SEED marker is not bound to the current solver profile")

    owner_rows = []
    for match in OWNER_CLEANUP_RE.finditer(body):
        values = parse_key_values(match.group(1))
        if values.get("seed") == seed and values.get("fault") == fault:
            owner_rows.append(values)
    cleanup_expectations = {
        (CURRENT_REFERENCE_STEP, CURRENT_REFERENCE_ROLE),
        (CURRENT_PRODUCTION_STEP, CURRENT_PRODUCTION_ROLE),
    }
    observed = set()
    for row in owner_rows:
        role_pair = (row.get("declaredMaximumStepSeconds"), row.get("stepRole"))
        if row.get("cleanup") == "true":
            observed.add(role_pair)
        else:
            issues.append("paired solver owner cleanup is not true")
        if ints(row.get("elapsedMillis")) is None or ints(row.get("elapsedMillis")) < 0:
            issues.append("paired solver owner cleanup time is missing or invalid")
    if observed != cleanup_expectations or len(owner_rows) != 2:
        issues.append("paired owner cleanup receipts do not prove one reference and one production owner")

    seed_cleanup_rows = []
    for match in SEED_CLEANUP_RE.finditer(body):
        values = parse_key_values(match.group(1))
        if values.get("seed") == seed:
            seed_cleanup_rows.append(values)
    if len(seed_cleanup_rows) != 1 or seed_cleanup_rows[0].get("cleanup") != "true":
        issues.append("seed-scoped cleanup receipt is missing, ambiguous, or failed")
    elif ints(seed_cleanup_rows[0].get("elapsedMillis")) is None or \
            ints(seed_cleanup_rows[0].get("elapsedMillis")) < 0:
        issues.append("seed-scoped cleanup time is missing or invalid")

    cleanup_failures = [line for line in body.splitlines()
                        if line.startswith("Q30_STEP_CLEANUP_FAILURE ")]
    if cleanup_failures:
        issues.append("one or more Q30 owner cleanup failures were reported")

    pair_timing = {
        "referenceElapsedMillis": ints(pair.get("referenceElapsedMillis")),
        "productionCandidateElapsedMillis": ints(pair.get("productionCandidateElapsedMillis")),
    }
    cleanup_timing = {
        "referenceCleanupMillis": ints(pair.get("referenceCleanupMillis")),
        "productionCandidateCleanupMillis": ints(pair.get("productionCandidateCleanupMillis")),
        "ownerCleanupMillis": [ints(row.get("elapsedMillis")) for row in owner_rows],
        "seedCleanupMillis": (ints(seed_cleanup_rows[0].get("elapsedMillis"))
                              if len(seed_cleanup_rows) == 1 else None),
    }
    status = "TIMEOUT" if explicit_timeout else ("PASS" if not issues else "FAIL")
    if explicit_timeout:
        status = "TIMEOUT"
    return {
        "issues": issues,
        "status": status,
        "operationTimingsMillis": pair_timing,
        "cleanupTimingsMillis": cleanup_timing,
        "failureEvidence": {
            "metadataLine": entry.get("metadataLine"),
            "body": body,
        } if issues else None,
    }


def expected_keys(seeds, faults):
    return [(seed, fault) for seed in seeds for fault in faults]


def key_string(key):
    return "{}/{}".format(key[0], key[1])


def row_attempted_in_service_log(log, seed, fault):
    prefix = "PASS: Q30 service flow seed={} fault=".format(seed)
    failed = "Q30_CASE_FAIL|{}|{}|".format(seed, fault)
    return any(line.startswith(prefix) and line.split(" fault=", 1)[1].startswith(fault + " ")
               for line in log.splitlines()) or failed in log


def row_attempted_in_sensitivity_log(log, seed, fault):
    patterns = (
        r"(?m)^Q30_STEP_PAIR seed=" + re.escape(seed) + r"\b.*\bfault=" + re.escape(fault) + r"\b",
        r"(?m)^Q30_STEP_OWNER_CLEANUP seed=" + re.escape(seed) + r" fault=" + re.escape(fault) + r"\b",
        r"(?m)^Q30_STEP_CLEANUP_FAILURE seed=" + re.escape(seed) + r" fault=" + re.escape(fault) + r"\b",
    )
    return any(re.search(pattern, log) for pattern in patterns)


def make_suite_rows(seeds, faults, entries, log, validator, attempted_fn, census_claims_full):
    expected = expected_keys(seeds, faults)
    by_key = {}
    unexpected = []
    for entry in entries:
        seed = entry.get("seed")
        fault = entry.get("fault")
        try:
            key = (seed, fault)
            if key not in expected:
                unexpected.append({"key": key_string(key), "entry": entry})
                continue
        except TypeError:
            unexpected.append({"key": "<malformed>", "entry": entry})
            continue
        by_key.setdefault(key, []).append(entry)

    rows = []
    for key in expected:
        seed, fault = key
        matching = by_key.get(key, [])
        if len(matching) > 1:
            rows.append({
                "seed": seed,
                "fault": fault,
                "status": "FAIL",
                "issues": ["duplicate receipt rows for expected seed/fault"],
                "duplicateCount": len(matching),
                "failureEvidence": [
                    {
                        "metadataLine": item.get("metadataLine"),
                        "stdout": item.get("stdout"),
                        "stderr": item.get("stderr"),
                        "body": item.get("body"),
                    }
                    for item in matching
                ],
            })
        elif not matching:
            attempted = attempted_fn(log, seed, fault)
            claims = census_claims_full
            status = "FAIL" if attempted or claims else "NOT RUN"
            reason = ("runner log shows attempt but receipt row is missing" if attempted else
                      "census claims a complete attempt but receipt row is missing" if claims else
                      "no row receipt or attempt marker")
            rows.append({
                "seed": seed,
                "fault": fault,
                "status": status,
                "issues": [reason],
                "elapsedMillis": None,
                "failureEvidence": None,
            })
        else:
            entry = matching[0]
            verdict = validator(entry, seed, fault)
            row = {
                "seed": seed,
                "fault": fault,
                "status": verdict["status"],
                "issues": verdict["issues"],
                "metadataLine": entry.get("metadataLine"),
                "elapsedMillis": verdict.get("elapsedMillis"),
                "operationTimingsMillis": verdict.get("operationTimingsMillis"),
                "cleanupTimingsMillis": verdict.get("cleanupTimingsMillis"),
                "failureEvidence": verdict.get("failureEvidence"),
            }
            rows.append(row)
    return rows, unexpected


def validate_service_census(censuses, seeds, expected_count):
    issues = []
    if len(censuses) != 1:
        return ["expected exactly one service census, found {}".format(len(censuses))]
    census = censuses[0]
    if census["status"] != "PASS":
        issues.append("service census status is {}".format(census["status"]))
    if census["attempted"] != expected_count:
        issues.append("service census attempted count differs from expected {}".format(expected_count))
    if census["passed"] != expected_count or census["failed"] != 0:
        issues.append("service census counts are not all passing")
    if census["seeds"] != seeds:
        issues.append("service census seed list/order differs from --seeds")
    if census["faults"] != len(FAULTS) or census["order"] != list(FAULTS):
        issues.append("service census fault count/order differs from the maintained five-fault order")
    if census["childBudgetMs"] != CHILD_BUDGET_MS:
        issues.append("service census child budget is not the maintained 60000 ms")
    return issues


def validate_sensitivity_census(censuses, seeds, expected_count):
    issues = []
    if len(censuses) != 1:
        return ["expected exactly one sensitivity census, found {}".format(len(censuses))]
    census = censuses[0]
    if expected_count != len(seeds) * len(SENSITIVITY_FAULTS):
        issues.append("sensitivity census expected count differs from the requested seed/fault matrix")
    if census["attempted"] != expected_count:
        issues.append("sensitivity census attempted count differs from expected {}".format(expected_count))
    if census["passed"] != expected_count or census["failed"] != 0:
        issues.append("sensitivity census counts are not all passing")
    if census["childBudgetMs"] != CHILD_BUDGET_MS:
        issues.append("sensitivity census child budget is not the maintained 60000 ms")
    return issues


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(float(value) for value in values)
    index = (len(ordered) - 1) * fraction
    lower = int(math.floor(index))
    upper = int(math.ceil(index))
    if lower == upper:
        return ordered[lower]
    weight = index - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def summarize_timings(rows, suite):
    passing = [row for row in rows if row["status"] == "PASS"]
    failed = [row for row in rows if row["status"] in ("FAIL", "TIMEOUT")]
    if suite == "service":
        successful_values = [row.get("elapsedMillis") for row in passing
                             if row.get("elapsedMillis") is not None]
        failed_values = [
            {"key": key_string((row["seed"], row["fault"])),
             "elapsedMillis": row.get("elapsedMillis"),
             "operationTimingsMillis": row.get("operationTimingsMillis")}
            for row in failed if row.get("elapsedMillis") is not None
        ]
        return {
            "operation": "route-replay-and-service",
            "successfulCount": len(successful_values),
            "successfulP50Millis": percentile(successful_values, 0.50),
            "successfulP95Millis": percentile(successful_values, 0.95),
            "percentileMethod": "linear interpolation over sorted successful-row durations",
            "failedRowTimings": failed_values,
        }

    reference = [row.get("operationTimingsMillis", {}).get("referenceElapsedMillis")
                 for row in passing]
    production = [row.get("operationTimingsMillis", {}).get("productionCandidateElapsedMillis")
                  for row in passing]
    reference = [value for value in reference if value is not None]
    production = [value for value in production if value is not None]
    failed_values = []
    for row in failed:
        timings = row.get("operationTimingsMillis") or {}
        if any(value is not None for value in timings.values()):
            failed_values.append({
                "key": key_string((row["seed"], row["fault"])),
                "referenceElapsedMillis": timings.get("referenceElapsedMillis"),
                "productionCandidateElapsedMillis": timings.get("productionCandidateElapsedMillis"),
            })
    return {
        "successfulReferenceOperation": {
            "maximumStepSeconds": CURRENT_REFERENCE_STEP,
            "successfulCount": len(reference),
            "successfulP50Millis": percentile(reference, 0.50),
            "successfulP95Millis": percentile(reference, 0.95),
        },
        "successfulProductionOperation": {
            "maximumStepSeconds": CURRENT_PRODUCTION_STEP,
            "successfulCount": len(production),
            "successfulP50Millis": percentile(production, 0.50),
            "successfulP95Millis": percentile(production, 0.95),
        },
        "percentileMethod": "linear interpolation over sorted successful-row durations",
        "failedRowTimings": failed_values,
    }


def cleanup_summary(rows):
    good = [row for row in rows if row["status"] == "PASS"]
    failed = [row for row in rows if row["status"] != "PASS"]
    values = []
    for row in good:
        timings = row.get("cleanupTimingsMillis") or {}
        for name, value in timings.items():
            if name == "ownerCleanupMillis" and isinstance(value, list):
                values.extend((key_string((row["seed"], row["fault"])), name, item)
                              for item in value if item is not None)
            elif value is not None:
                values.append((key_string((row["seed"], row["fault"])), name, value))
    return {
        "successfulRowCount": len(good),
        "failedOrUnrunRows": [key_string((row["seed"], row["fault"]))
                               for row in failed],
        "successfulCleanupMeasurements": [
            {"key": key, "operation": name, "elapsedMillis": value}
            for key, name, value in values
        ],
        "failedRowsWithCleanupEvidence": [
            {"key": key_string((row["seed"], row["fault"])),
             "cleanupTimingsMillis": row.get("cleanupTimingsMillis"),
             "issues": row.get("issues", [])}
            for row in failed
        ],
    }


def suite_status(rows, census_issues, cleanup_audit, unexpected):
    return (not census_issues and cleanup_audit["passed"] and not unexpected and
            len(rows) > 0 and all(row["status"] == "PASS" for row in rows))


def _selftest_record(entries, validator, attempted_fn, log, census_full, seeds, faults):
    rows, _ = make_suite_rows(seeds, faults, entries, log, validator,
                              attempted_fn, census_full)
    return rows


def run_corruption_selftests(service_entries, sensitivity_entries, service_log,
                             sensitivity_log, seeds):
    results = []
    seed, fault = seeds[0], FAULTS[0]
    service_pass = next((entry for entry in service_entries
                         if entry.get("seed") == seed and entry.get("fault") == fault and
                         service_entry_issues(entry, seed, fault)["status"] == "PASS"), None)
    if service_pass is None:
        results.append({"name": "service missing-row", "status": "NOT RUN",
                        "reason": "no passing actual service receipt row available to mutate"})
        results.append({"name": "service duplicate-row", "status": "NOT RUN",
                        "reason": "no passing actual service receipt row available to mutate"})
    else:
        removed = [entry for entry in service_entries if entry is not service_pass]
        # Explicitly remove the actual row after constructing the mutation source.
        mutated_rows = _selftest_record(
            removed, service_entry_issues, row_attempted_in_service_log,
            service_log, False, seeds, FAULTS)
        removed_row = next(row for row in mutated_rows
                           if row["seed"] == seed and row["fault"] == fault)
        results.append({
            "name": "service missing-row",
            "status": "PASS" if removed_row["status"] != "PASS" else "FAIL",
            "basis": "removed one actual passing receipt row in memory",
            "observedRowStatus": removed_row["status"],
        })
        duplicated = list(service_entries) + [copy.deepcopy(service_pass)]
        duplicate_rows, _ = make_suite_rows(
            seeds, FAULTS, duplicated, service_log, service_entry_issues,
            row_attempted_in_service_log, False)
        duplicate_row = next(row for row in duplicate_rows
                             if row["seed"] == seed and row["fault"] == fault)
        results.append({
            "name": "service duplicate-row",
            "status": "PASS" if duplicate_row["status"] == "FAIL" else "FAIL",
            "basis": "duplicated one actual passing receipt row in memory",
            "observedRowStatus": duplicate_row["status"],
        })

    sensitivity_pass = next((entry for entry in sensitivity_entries
                              if entry.get("seed") == seed and entry.get("fault") == SENSITIVITY_FAULTS[0] and
                              sensitivity_entry_issues(entry, seed, SENSITIVITY_FAULTS[0])["status"] == "PASS"), None)
    if sensitivity_pass is None:
        results.append({"name": "sensitivity termination", "status": "NOT RUN",
                        "reason": "no passing actual sensitivity receipt row available to mutate"})
        results.append({"name": "sensitivity legacy 10us rejection", "status": "NOT RUN",
                        "reason": "no passing current-profile receipt row available to mutate"})
    else:
        corrupted_termination = copy.deepcopy(sensitivity_pass)
        corrupted_termination["metadata"]["termination"] = "False"
        termination_verdict = sensitivity_entry_issues(
            corrupted_termination, seed, SENSITIVITY_FAULTS[0])
        results.append({
            "name": "sensitivity termination",
            "status": "PASS" if termination_verdict["status"] == "FAIL" else "FAIL",
            "basis": "changed termination metadata on an actual passing receipt row in memory",
            "observedRowStatus": termination_verdict["status"],
        })

        corrupted_version = copy.deepcopy(sensitivity_pass)
        original_body = corrupted_version["body"]
        corrupted_version["body"] = original_body.replace(
            "referenceMaximumStepSeconds=" + CURRENT_REFERENCE_STEP,
            "referenceMaximumStepSeconds=5.00000000e-06", 1).replace(
            "productionCandidateMaximumStepSeconds=" + CURRENT_PRODUCTION_STEP,
            "experimentalCandidateMaximumStepSeconds=1.00000000e-05", 1).replace(
                "candidateKind=" + CURRENT_CANDIDATE_KIND,
                "candidateKind=EXPERIMENTAL_NOT_PROMOTED", 1)
        version_verdict = sensitivity_entry_issues(
            corrupted_version, seed, SENSITIVITY_FAULTS[0])
        results.append({
            "name": "sensitivity legacy 10us rejection",
            "status": "PASS" if (corrupted_version["body"] != original_body and
                                  version_verdict["status"] == "FAIL") else "FAIL",
            "basis": "changed current candidate markers to the retired 10 us profile in an actual passing receipt row",
            "observedRowStatus": version_verdict["status"],
        })
    return results


def audit_suite(name, seeds, faults, entries, log, census, cleanup_audit,
                validator, attempted_fn, census_validator):
    count = len(seeds) * len(faults)
    census_issues = census_validator(census, seeds, count)
    census_claims_full = False
    if len(census) == 1:
        census_claims_full = census[0].get("attempted") == count
    rows, unexpected = make_suite_rows(
        seeds, faults, entries, log, validator, attempted_fn, census_claims_full)
    expected_order = expected_keys(seeds, faults)
    observed_order = [(entry.get("seed"), entry.get("fault")) for entry in entries]
    order_issues = []
    if observed_order != expected_order:
        order_issues.append("receipt rows do not preserve the exact requested seed/fault order")
    status = (suite_status(rows, census_issues, cleanup_audit, unexpected) and
              not order_issues)
    suite = {
        "status": "PASS" if status else "FAIL",
        "expectedRowCount": count,
        "rows": rows,
        "census": census,
        "censusIssues": census_issues,
        "receiptOrderIssues": order_issues,
        "unexpectedReceiptRows": [
            {
                "key": item["key"],
                "metadataLine": item["entry"].get("metadataLine"),
                "stdout": item["entry"].get("stdout"),
                "stderr": item["entry"].get("stderr"),
                "body": item["entry"].get("body"),
            }
            for item in unexpected
        ],
        "cleanupAudit": cleanup_audit,
        "operationTimings": summarize_timings(rows, name),
    }
    if name == "sensitivity":
        suite["cleanupSummary"] = cleanup_summary(rows)
    return suite


def run_census_completeness_selftests(
        seeds, service_entries, sensitivity_entries,
        service_log, sensitivity_log, service_census, sensitivity_census,
        service_cleanup, sensitivity_cleanup):
    """Remove actual passing rows in memory and require each 40-row suite to fail."""
    cases = []
    specifications = (
        ("service", service_entries, service_log, service_census, service_cleanup,
         FAULTS, service_entry_issues, row_attempted_in_service_log,
         validate_service_census),
        ("sensitivity", sensitivity_entries, sensitivity_log, sensitivity_census,
         sensitivity_cleanup, SENSITIVITY_FAULTS, sensitivity_entry_issues,
         row_attempted_in_sensitivity_log, validate_sensitivity_census),
    )
    for name, entries, log, census, cleanup_audit, faults, validator, attempted_fn, census_validator in specifications:
        expected_key = (seeds[0], faults[0])
        removed = next((entry for entry in entries
                        if (entry.get("seed"), entry.get("fault")) == expected_key), None)
        if removed is None:
            cases.append({
                "name": name + " 40-row census completeness",
                "status": "NOT RUN",
                "reason": "the actual receipt has no passing-source row for the in-memory removal canary",
            })
            continue
        altered = [entry for entry in entries if entry is not removed]
        corrupted = audit_suite(
            name, seeds, faults, altered, log, census, cleanup_audit,
            validator, attempted_fn, census_validator)
        missing = next((row for row in corrupted["rows"]
                        if (row["seed"], row["fault"]) == expected_key), None)
        catches_removal = (
            corrupted["status"] == "FAIL" and
            corrupted["expectedRowCount"] == 40 and
            len(corrupted["rows"]) == 40 and
            missing is not None and missing["status"] != "PASS"
        )
        cases.append({
            "name": name + " 40-row census completeness",
            "status": "PASS" if catches_removal else "FAIL",
            "basis": "removed one actual receipt row in memory",
            "observedSuiteStatus": corrupted["status"],
            "expectedRows": corrupted["expectedRowCount"],
            "observedRows": len(corrupted["rows"]),
            "removedRowStatus": None if missing is None else missing["status"],
        })
    return cases


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runner-scope", choices=("full", "selected-census"),
                        default="full",
                        help="require full-runner PASS by default; selected-census accepts only the exact two-suite focused marker")
    parser.add_argument("--seeds", required=True,
                        help="exactly eight distinct canonical signed-long seeds, comma separated")
    parser.add_argument("--service-log", help="maintained runner stdout log containing service suite")
    parser.add_argument("--service-receipt", help="maintained ReceiptOutputPath containing service rows")
    parser.add_argument("--sensitivity-log", help="maintained runner stdout log containing sensitivity suite")
    parser.add_argument("--sensitivity-receipt", help="maintained ReceiptOutputPath containing sensitivity rows")
    parser.add_argument("--log", help="shared full-runner stdout log when both suites ran together")
    parser.add_argument("--receipt", help="shared full-runner ReceiptOutputPath when both suites ran together")
    parser.add_argument("--report", help="write the JSON audit report to this path instead of stdout")
    args = parser.parse_args(argv)

    seeds = parse_seed_csv(args.seeds)
    shared = args.log is not None or args.receipt is not None
    split_values = (args.service_log, args.service_receipt,
                    args.sensitivity_log, args.sensitivity_receipt)
    if shared:
        if not args.log or not args.receipt or any(value is not None for value in split_values):
            raise AuditInputError("use both --log and --receipt, or all four suite-specific inputs")
        service_log_path = sensitivity_log_path = args.log
        service_receipt_path = sensitivity_receipt_path = args.receipt
    else:
        if any(value is None for value in split_values):
            raise AuditInputError("provide --log/--receipt or all four suite-specific inputs")
        if args.runner_scope == "selected-census":
            raise AuditInputError(
                "selected-census requires one combined --log/--receipt from the exact two-suite focused run"
            )
        service_log_path, service_receipt_path, sensitivity_log_path, sensitivity_receipt_path = split_values

    service_log = read_text(service_log_path, "service stdout log")
    service_receipt_text = read_text(service_receipt_path, "service receipt")
    sensitivity_log = read_text(sensitivity_log_path, "sensitivity stdout log")
    sensitivity_receipt_text = read_text(sensitivity_receipt_path, "sensitivity receipt")

    service_entries = parse_service_entries(service_receipt_text)
    sensitivity_entries = parse_sensitivity_entries(sensitivity_receipt_text)
    service_census = parse_service_census(service_log)
    sensitivity_census = parse_sensitivity_census(sensitivity_log)
    service_cleanup = run_cleanup_audit(service_log, args.runner_scope)
    sensitivity_cleanup = run_cleanup_audit(sensitivity_log, args.runner_scope)

    service = audit_suite(
        "service", seeds, FAULTS, service_entries, service_log, service_census,
        service_cleanup, service_entry_issues, row_attempted_in_service_log,
        validate_service_census,
    )
    sensitivity = audit_suite(
        "sensitivity", seeds, SENSITIVITY_FAULTS, sensitivity_entries,
        sensitivity_log, sensitivity_census, sensitivity_cleanup,
        sensitivity_entry_issues, row_attempted_in_sensitivity_log,
        validate_sensitivity_census,
    )
    corruption = run_corruption_selftests(
        service_entries, sensitivity_entries, service_log, sensitivity_log, seeds)
    scope_selftests = run_runner_scope_selftests()
    census_selftests = run_census_completeness_selftests(
        seeds, service_entries, sensitivity_entries, service_log, sensitivity_log,
        service_census, sensitivity_census, service_cleanup, sensitivity_cleanup)
    all_selftests = corruption + scope_selftests + census_selftests
    all_selftests_passed = bool(all_selftests) and all(
        item["status"] == "PASS" for item in all_selftests)
    overall_pass = (service["status"] == "PASS" and sensitivity["status"] == "PASS" and
                    all_selftests_passed)
    if not overall_pass:
        report_status = "FAIL"
        summary_label = ("selected-census=FAIL; full-matrix=NOT RUN"
                         if args.runner_scope == "selected-census" else "full-matrix=FAIL")
    elif args.runner_scope == "selected-census":
        report_status = "PASS_SELECTED_CENSUS"
        summary_label = "selected-census=PASS; full-matrix=NOT RUN"
    else:
        report_status = "PASS"
        summary_label = "full-matrix=PASS"

    report = {
        "schema": "q30-native-service-audit-v1",
        "status": report_status,
        "summaryLabel": summary_label,
        "runnerScope": args.runner_scope,
        "selectedCensusStatus": (
            "PASS" if args.runner_scope == "selected-census" and overall_pass else
            "FAIL" if args.runner_scope == "selected-census" else "NOT RUN"
        ),
        "fullMatrixStatus": (
            "PASS" if args.runner_scope == "full" and overall_pass else
            "FAIL" if args.runner_scope == "full" else "NOT RUN"
        ),
        "requestedSeeds": seeds,
        "faultOrder": list(FAULTS),
        "sensitivityFaultOrder": list(SENSITIVITY_FAULTS),
        "expectedRowsPerSuite": 40,
        "expectedTotalRows": 80,
        "observedTotalRows": len(service_entries) + len(sensitivity_entries),
        "currentSensitivityProfile": {
            "referenceMaximumStepSeconds": CURRENT_REFERENCE_STEP,
            "productionCandidateMaximumStepSeconds": CURRENT_PRODUCTION_STEP,
            "candidateKind": CURRENT_CANDIDATE_KIND,
        },
        "service": service,
        "sensitivity": sensitivity,
        "corruptionSelfTests": corruption,
        "runnerScopeSelfTests": scope_selftests,
        "censusCompletenessSelfTests": census_selftests,
        "overallIssues": [
            issue for issue, okay in (
                ("service suite did not qualify", service["status"] == "PASS"),
                ("sensitivity suite did not qualify", sensitivity["status"] == "PASS"),
                ("receipt, scope and census self-tests did not all pass", all_selftests_passed),
            ) if not okay
        ],
    }
    rendered = json.dumps(report, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    if args.report:
        try:
            Path(args.report).write_text(rendered, encoding="utf-8")
        except OSError as exc:
            raise AuditInputError("cannot write report {}: {}".format(args.report, exc)) from exc
    else:
        sys.stdout.write(rendered)
    return 0 if overall_pass else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AuditInputError as exc:
        sys.stderr.write("Q30_NATIVE_AUDIT_INPUT_ERROR: {}\n".format(exc))
        sys.exit(2)
