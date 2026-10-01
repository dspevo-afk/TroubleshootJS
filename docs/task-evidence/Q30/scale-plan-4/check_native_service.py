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
import hashlib
import json
import math
import re
import sys
from pathlib import Path


COMMON_FAULTS = (
    "DREV_OPEN",
    "REN_OPEN",
    "SENSOR_A_OPEN",
    "DRIVE_A_OPEN",
)
PLAN_MANIFEST_SCHEMA = "q30-scale-plan4-manifest-proposal@1"
PLAN_MANIFEST_PROPOSAL_STATUS = "PROPOSAL_NOT_QUALIFICATION"
PRESERVED_SEED_ORDER_SHA256 = "9974fd612dd5e6d758b0f965d4c53251e20a680889ab066b455fe7936c6fb632"
PLAN_METADATA_PREFIX = "Q30_CASE_PLAN "
PLAN_EXPORT_ROW_PREFIX = "Q30_PLAN_CANONICAL|"
PLAN_EXPORT_PASS = "PASS: Q30 plan contracts exported=77"
PLAN_EXPORT_RUNNER_PASS = (
    "PASS: focused current contracts; 1 Java suites. "
    "Full matrix and independent oracles NOT RUN."
)
PLAN_EXPORT_CLEANUP = (
    "CLEANUP: current JVM contract classes and task-owned scratch removed."
)
PLAN_EXPORT_CURRENT_PLAN_SHA256 = "c1323c148b32fb721e3e18ab1d493c1d8384d3d1d891cff24b2701b537e0cc67"
DEFAULT_FOCUSED_SUITE_COUNT = 2
PLAN_METADATA_KEYS = {
    "schema", "seed", "canonical", "planEpoch", "topology", "channelCount",
    "activeChannels", "packageCount", "referenceArrangement", "support", "faults",
    "diagnosticProviderDeveloper", "diagnosticProviderNormal", "diagnosticTemplate",
    "diagnosticSamplesPerHypothesis", "temporalContract", "sampleSeconds",
    "profileWorkUnits", "customerRetestWorkUnits", "maxJobMillis", "maxJobSteps",
    "activeOperationMillis",
}
PLAN_TOPOLOGY_AXIS_RE = re.compile(
    r"^RB30_CH([12])_(SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_A_(BJT|NMOS)(?:_B_(BJT|NMOS))?$"
)
SERVICE_CENSUS_FAULT_ORDER = [
    "RELAY_<channel>_COIL_OPEN", "DREV_OPEN", "REN_OPEN",
    "SENSOR_A_OPEN", "DRIVE_A_OPEN",
]
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
FOCUSED_CENSUS_SUCCESS_RE = re.compile(
    r"(?m)^PASS: focused current contracts; ([0-9]+) Java suites\. "
    r"Full matrix and independent oracles NOT RUN\.\r?$"
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
TOKEN_RE = re.compile(r"([A-Za-z][A-Za-z0-9]*)=([^\s|]+)")
SENSITIVITY_FINAL_PREFIX = "PASS: Q30 production solver step sensitivity "
SENSITIVITY_FINAL_FIELDS = {
    "assertions", "seed", "support", "topology", "canonicalPlan", "faults",
    "referenceMaximumStepSeconds", "productionCandidateMaximumStepSeconds",
    "candidateKind", "elapsedMillis",
}
SEED_RE = re.compile(r"(?:0|[1-9][0-9]*|-[1-9][0-9]*)\Z")
SIGNED_LONG_MIN = -(2**63)
SIGNED_LONG_MAX = (2**63) - 1


class AuditInputError(Exception):
    """An input cannot be read or is not a valid audit request."""


def parse_seed_csv(value):
    parts = value.split(",")
    if not 1 <= len(parts) <= 32:
        raise AuditInputError("--seeds must contain 1-32 CSV values")
    if any(not SEED_RE.fullmatch(part) for part in parts):
        raise AuditInputError("--seeds must use canonical signed-long decimal values")
    parsed = [int(part, 10) for part in parts]
    if any(number < SIGNED_LONG_MIN or number > SIGNED_LONG_MAX for number in parsed):
        raise AuditInputError("--seeds contains a value outside signed-long range")
    if len(set(parts)) != len(parts):
        raise AuditInputError("--seeds values must be distinct")
    return parts


def parse_focused_suite_count(value):
    try:
        count = int(value, 10)
    except (TypeError, ValueError) as exc:
        raise argparse.ArgumentTypeError("must be an integer of at least 2") from exc
    if count < 2:
        raise argparse.ArgumentTypeError("must be an integer of at least 2")
    return count


def read_text(path, label):
    try:
        return Path(path).read_text(encoding="utf-8-sig")
    except OSError as exc:
        raise AuditInputError("cannot read {} {}: {}".format(label, path, exc)) from exc
    except UnicodeError as exc:
        raise AuditInputError("{} is not valid UTF-8: {}".format(label, path)) from exc


def read_bytes(path, label):
    try:
        return Path(path).read_bytes()
    except OSError as exc:
        raise AuditInputError("cannot read {} {}: {}".format(label, path, exc)) from exc


def parse_plan_export_rows(text, manifest, where):
    rows = []
    for line_number, line in enumerate(text.splitlines(), 1):
        if not line.startswith(PLAN_EXPORT_ROW_PREFIX):
            continue
        match = re.fullmatch(
            r"Q30_PLAN_CANONICAL\|seed=(-?(?:0|[1-9][0-9]*))\|plan=(.+)", line)
        if not match:
            raise AuditInputError("{} has malformed plan export row at line {}".format(
                where, line_number))
        seed, canonical = match.groups()
        if seed not in manifest["bySeed"]:
            raise AuditInputError("{} has plan export seed outside the independent manifest: {}".format(
                where, seed))
        rows.append((seed, canonical))
    expected = [(seed, manifest["bySeed"][seed]["expectedPlan4"]["canonical"])
                for seed in manifest["seeds"]]
    if rows != expected:
        raise AuditInputError(
            "{} plan export rows differ from the exact 77-root manifest order/canonical identity".format(
                where))
    pass_lines = [line for line in text.splitlines() if line == PLAN_EXPORT_PASS]
    if len(pass_lines) != 1:
        raise AuditInputError("{} must contain exactly one successful 77-plan export marker".format(where))
    return {"count": len(rows), "seeds": [seed for seed, _ in rows]}


def validate_plan_export_evidence(manifest, manifest_path, log_path, receipt_path,
                                  differential_path, run_path, source_path):
    """Bind the independent plan4 manifest to both Java exports and their runner receipt."""
    log_bytes = read_bytes(log_path, "Java plan-export host log")
    receipt_bytes = read_bytes(receipt_path, "Java plan-export receipt")
    manifest_bytes = read_bytes(manifest_path, "independent plan4 manifest")
    differential_bytes = read_bytes(differential_path, "plan-export differential")
    source_bytes = read_bytes(source_path, "Java Rb30Plan source")
    run_text = read_text(run_path, "Java plan-export runner summary")
    try:
        differential = json.loads(
            differential_bytes.decode("utf-8-sig"),
            object_pairs_hook=unique_json_object,
            parse_constant=reject_json_constant)
        run_summary = json.loads(
            run_text, object_pairs_hook=unique_json_object,
            parse_constant=reject_json_constant)
    except (UnicodeError, json.JSONDecodeError, AuditInputError) as exc:
        raise AuditInputError("plan-export differential/runner summary is invalid JSON: {}".format(
            exc)) from exc
    if not isinstance(differential, dict) or set(differential) != {
            "status", "scope", "rows", "outputs", "manifestSha256", "currentPlanSha256"}:
        raise AuditInputError("plan-export differential has an unexpected field set")
    if (differential.get("status") != "PASS" or
            differential.get("scope") != (
                "independent Python structural oracle versus exact Java signed-long canonical plans; "
                "no electrical/route/timing qualification") or
            differential.get("rows") != 77):
        raise AuditInputError("plan-export differential does not report the exact scoped 77-row PASS")
    manifest_sha = hashlib.sha256(manifest_bytes).hexdigest()
    source_sha = hashlib.sha256(source_bytes.replace(b"\r\n", b"\n")).hexdigest()
    if differential.get("manifestSha256") != manifest_sha:
        raise AuditInputError("plan-export differential is not bound to the supplied manifest bytes")
    if (differential.get("currentPlanSha256") != PLAN_EXPORT_CURRENT_PLAN_SHA256 or
            source_sha != PLAN_EXPORT_CURRENT_PLAN_SHA256):
        raise AuditInputError("plan-export differential/source do not match the frozen Rb30Plan.java identity")
    expected_output_names = {
        "native-scale-plan-export.log": (log_bytes, log_path),
        "native-scale-plan-export-receipt.txt": (receipt_bytes, receipt_path),
    }
    outputs = differential.get("outputs")
    if not isinstance(outputs, dict) or set(outputs) != set(expected_output_names):
        raise AuditInputError("plan-export differential has an unexpected output set")
    stream_summaries = {}
    for output_name, (raw, path) in expected_output_names.items():
        if Path(path).name != output_name:
            raise AuditInputError("plan-export input path does not match differential output name {}".format(
                output_name))
        entry = outputs[output_name]
        digest = hashlib.sha256(raw).hexdigest()
        if (not isinstance(entry, dict) or set(entry) != {"status", "canonicalRows", "sha256"} or
                entry.get("status") != "PASS" or entry.get("canonicalRows") != 77 or
                entry.get("sha256") != digest):
            raise AuditInputError("plan-export differential does not authenticate {}".format(output_name))
        try:
            decoded = raw.decode("utf-8-sig")
        except UnicodeError as exc:
            raise AuditInputError("{} is not valid UTF-8".format(output_name)) from exc
        stream_summaries[output_name] = parse_plan_export_rows(decoded, manifest, output_name)
    if log_bytes != receipt_bytes:
        # The two streams are expected to carry identical canonical records, but
        # their surrounding runner text differs by design.
        log_text = log_bytes.decode("utf-8-sig")
        receipt_text = receipt_bytes.decode("utf-8-sig")
        if [line for line in log_text.splitlines() if line.startswith(PLAN_EXPORT_ROW_PREFIX)] != \
                [line for line in receipt_text.splitlines() if line.startswith(PLAN_EXPORT_ROW_PREFIX)]:
            raise AuditInputError("Java plan-export host log and receipt canonical rows differ")
    log_text = log_bytes.decode("utf-8-sig")
    runner_passes = [line for line in log_text.splitlines() if line == PLAN_EXPORT_RUNNER_PASS]
    cleanups = [line for line in log_text.splitlines() if line == PLAN_EXPORT_CLEANUP]
    failures = [line for line in log_text.splitlines()
                if line.startswith("CURRENT_CONTRACT_FAILURE:") or
                line.startswith("CURRENT_CONTRACT_CLEANUP:")]
    timeouts = [line for line in log_text.splitlines()
                if "Bounded process " in line and " exceeded 60000ms; logs retained at " in line]
    if len(runner_passes) != 1 or len(cleanups) != 1 or failures or timeouts:
        raise AuditInputError(
            "Java plan-export host log lacks exactly one focused runner PASS and cleanup PASS "
            "or contains a failure/timeout")
    if (not isinstance(run_summary, dict) or set(run_summary) != {
            "status", "exitCode", "elapsedSeconds", "seedCount", "phase"} or
            run_summary.get("status") != "PASS" or
            type(run_summary.get("exitCode")) is not int or run_summary.get("exitCode") != 0 or
            type(run_summary.get("seedCount")) is not int or run_summary.get("seedCount") != 77 or
            run_summary.get("phase") != "pure immutable Java plan export including maintained cleanup" or
            isinstance(run_summary.get("elapsedSeconds"), bool) or
            not isinstance(run_summary.get("elapsedSeconds"), (int, float)) or
            not math.isfinite(run_summary["elapsedSeconds"]) or run_summary["elapsedSeconds"] <= 0):
        raise AuditInputError("Java plan-export runner summary does not prove successful 77-seed execution")
    return {
        "status": "PASS_JAVA_PLAN_CROSSCHECK_ONLY",
        "scope": differential["scope"],
        "rows": 77,
        "manifestSha256": manifest_sha,
        "currentPlanSha256": source_sha,
        "runnerElapsedSeconds": run_summary["elapsedSeconds"],
        "runnerCleanup": "PASS",
        "streams": stream_summaries,
        "hostLogSha256": hashlib.sha256(log_bytes).hexdigest(),
        "receiptSha256": hashlib.sha256(receipt_bytes).hexdigest(),
    }


def unique_json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AuditInputError("duplicate JSON object key {}".format(key))
        result[key] = value
    return result


def reject_json_constant(value):
    raise AuditInputError("non-finite JSON value {}".format(value))


def parse_plan_manifest(path):
    try:
        raw = json.loads(read_text(path, "Q30 plan manifest"),
                         object_pairs_hook=unique_json_object,
                         parse_constant=reject_json_constant)
    except json.JSONDecodeError as exc:
        raise AuditInputError("Q30 plan manifest is malformed JSON: {}".format(exc)) from exc
    if not isinstance(raw, dict) or raw.get("schema") != PLAN_MANIFEST_SCHEMA or \
            raw.get("status") != PLAN_MANIFEST_PROPOSAL_STATUS:
        raise AuditInputError("Q30 plan manifest is not the explicitly expected plan4 proposal")
    rows = raw.get("seeds")
    if not isinstance(rows, list) or len(rows) != 77:
        raise AuditInputError("Q30 plan4 proposal must retain all 77 declared roots")
    seeds = []
    by_seed = {}
    row_kinds = []
    for index, row in enumerate(rows):
        if not isinstance(row, dict):
            raise AuditInputError("Q30 manifest row {} is not an object".format(index))
        seed = row.get("seed")
        if not isinstance(seed, str) or not SEED_RE.fullmatch(seed):
            raise AuditInputError("Q30 manifest row {} has a noncanonical seed".format(index))
        number = int(seed, 10)
        if number < SIGNED_LONG_MIN or number > SIGNED_LONG_MAX or str(number) != seed:
            raise AuditInputError("Q30 manifest seed {} is outside canonical signed-long range".format(seed))
        if seed in by_seed:
            raise AuditInputError("Q30 plan manifest repeats seed {}".format(seed))
        plan = row.get("expectedPlan4")
        if not isinstance(plan, dict) or plan.get("seed") != seed or plan.get("planVersion") != 4:
            raise AuditInputError("Q30 manifest seed {} lacks a bound plan4 identity".format(seed))
        canonical = plan.get("canonical")
        if not isinstance(canonical, str) or not canonical.startswith(
                "rb30-plan@4;seed={};".format(seed)):
            raise AuditInputError("Q30 manifest seed {} has an invalid plan4 canonical".format(seed))
        seeds.append(seed)
        by_seed[seed] = row
        row_kinds.append(row.get("rowKind"))

    preserved = seeds[:51]
    digest = hashlib.sha256(("\n".join(preserved) + "\n").encode("ascii")).hexdigest()
    if digest != PRESERVED_SEED_ORDER_SHA256 or seeds[51:] != [
            row["seed"] for row in rows[51:]]:
        raise AuditInputError("Q30 plan4 proposal changed the preserved 51-root prefix")
    if row_kinds[:48] != ["predeclared-epoch3-matrix"] * 48 or \
            row_kinds[48:51] != ["predeclared-signed-long-replay"] * 3 or \
            row_kinds[51:] != ["appended-plan4-structural"] * 26:
        raise AuditInputError("Q30 plan4 proposal dropped/retyped historical or appended roots")
    summary = raw.get("summary")
    preservation = raw.get("preservation")
    if (not isinstance(summary, dict) or summary.get("manifestSeedCount") != 77 or
            summary.get("preservedSeedCount") != 51 or summary.get("appendedCount") != 26 or
            not isinstance(preservation, dict) or
            preservation.get("legacyPlanEpoch") != 3 or
            preservation.get("legacyMatrixRows") != 48 or
            preservation.get("signedLongReplayRows") != 3 or
            preservation.get("preservedSeedCount") != 51 or
            preservation.get("legacyRowsRetainedAsHistory") is not True or
            preservation.get("originalSeedOrderPreserved") is not True):
        raise AuditInputError("Q30 plan4 proposal summary does not preserve the historical corpus")
    return {"raw": raw, "rows": rows, "seeds": seeds, "bySeed": by_seed,
            "manifestStatus": raw["status"]}


def validate_plan_metadata_row(plan, manifest_row, where):
    issues = []
    if not isinstance(plan, dict) or set(plan) != PLAN_METADATA_KEYS:
        return ["{} has an unexpected Q30_CASE_PLAN field set".format(where)]
    expected = manifest_row["expectedPlan4"]
    seed = manifest_row["seed"]
    support = expected.get("support")
    axis = expected.get("topologyAxis")
    channel_count = expected.get("channelCount")
    if not isinstance(support, dict) or not isinstance(axis, str) or \
            type(channel_count) is not int or channel_count not in (1, 2):
        return ["{} has incomplete independent plan4 fields for seed {}".format(where, seed)]
    match = PLAN_TOPOLOGY_AXIS_RE.fullmatch(axis)
    if not match or int(match.group(1)) != channel_count:
        return ["{} has malformed independent topology axis for seed {}".format(where, seed)]

    def check(field, value):
        if plan.get(field) != value:
            issues.append("{} {} disagrees with plan4 manifest".format(where, field))

    check("schema", 1)
    check("seed", seed)
    check("canonical", expected.get("canonical"))
    check("planEpoch", 4)
    check("channelCount", channel_count)
    check("activeChannels", ["A"] if channel_count == 1 else ["A", "B"])
    check("packageCount", expected.get("packageCount"))
    check("referenceArrangement", expected.get("referenceArrangement"))
    normalized_topology = axis + "_C12_" + ("Y" if support.get("entryCapacitor") is True else "N") + \
        "_S5_" + ("Y" if support.get("fiveVoltIndicator") is True else "N") + \
        "_S12_" + ("Y" if support.get("twelveVoltIndicator") is True else "N") + \
        "_F{}".format(support.get("sensorInputFilterMask")) + \
        "_O{}".format(support.get("outputIndicatorMask")) + \
        "_B5_" + ("Y" if support.get("fiveVoltBleeder") is True else "N")
    check("topology", normalized_topology)
    expected_support = {
        "C12": support.get("entryCapacitor"),
        "S5": support.get("fiveVoltIndicator"),
        "S12": support.get("twelveVoltIndicator"),
        "filters": support.get("sensorInputFilterMask"),
        "outputIndicators": support.get("outputIndicatorMask"),
        "bleeder5": support.get("fiveVoltBleeder"),
    }
    actual_support = plan.get("support")
    if not isinstance(actual_support, dict) or set(actual_support) != set(expected_support) or \
            actual_support != expected_support:
        issues.append("{} support flags/masks disagree with plan4 manifest".format(where))
    elif (any(type(actual_support[field]) is not bool
              for field in ("C12", "S5", "S12", "bleeder5")) or
          any(type(actual_support[field]) is not int
              for field in ("filters", "outputIndicators"))):
        issues.append("{} support values have noncanonical JSON types".format(where))
    relay = "RELAY_A_COIL_OPEN" if channel_count == 1 else "RELAY_B_COIL_OPEN"
    relay_owner = "KA" if channel_count == 1 else "KB"
    expected_faults = ([{"id": fault, "owner": owner} for fault, owner in (
        ("DREV_OPEN", "DREV"), ("REN_OPEN", "REN"),
        ("SENSOR_A_OPEN", "RSA"), ("DRIVE_A_OPEN", "RDA"),
        (relay, relay_owner))])
    if plan.get("faults") != expected_faults:
        issues.append("{} declared five-fault ID/owner/order list is invalid".format(where))
    expected_samples = 17 if channel_count == 1 else 37
    expected_work_units = 3 if channel_count == 1 else 5
    expected_scalars = {
        "diagnosticProviderDeveloper": "rb30-control-diagnostic@2",
        "diagnosticProviderNormal": "rb30-control-diagnostic@4",
        "diagnosticTemplate": "RB30_CHANNEL_INPUT_SWEEP_V1",
        "diagnosticSamplesPerHypothesis": expected_samples,
        "temporalContract": "RB30_CHANNEL_FUNCTION@3",
        "sampleSeconds": 0.03,
        "profileWorkUnits": expected_work_units,
        "customerRetestWorkUnits": expected_work_units,
        "maxJobMillis": 90000,
        "maxJobSteps": 640,
        "activeOperationMillis": 5000,
    }
    for field, value in expected_scalars.items():
        actual = plan.get(field)
        if (isinstance(value, float) and
                (isinstance(actual, bool) or not isinstance(actual, (int, float)))):
            issues.append("{} {} has invalid numeric type".format(where, field))
        elif isinstance(value, int) and type(actual) is not int:
            issues.append("{} {} has invalid integer type".format(where, field))
        elif actual != value:
            issues.append("{} {} must be {!r}".format(where, field, value))
    for field in ("schema", "planEpoch", "channelCount", "packageCount"):
        if type(plan.get(field)) is not int:
            issues.append("{} {} must be a JSON integer".format(where, field))
    canonical = plan.get("canonical")
    if isinstance(canonical, str):
        canonical_match = re.fullmatch(
            r"rb30-plan@4;seed=(-?(?:0|[1-9][0-9]*));topology=(RB30_CH[12]_(?:SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_A_(?:BJT|NMOS)(?:_B_(?:BJT|NMOS))?);"
            r"support=C12:(true|false),S5:(true|false),S12:(true|false),filters:([0-3]),outputIndicators:([0-3]),bleeder5:(true|false);"
            r"layout=(-?(?:0|[1-9][0-9]*));routing=(-?(?:0|[1-9][0-9]*));physicalPolicy=MEDIUM_BOARD@1;"
            r"fault=([A-Z0-9_]+);packages=([0-9]+);main=12V;regulator=5V-E02;coil=5V-E03;"
            r"load=isolated-12V-180ohm-per-channel;channels=([12]);sensors=([12])-E04-decisions;"
            r"sensorPullDowns=(.*);loadReference=isolated", canonical)
        if not canonical_match or canonical_match.group(1) != seed or \
                canonical_match.group(2) != axis or \
                int(canonical_match.group(12)) != expected.get("packageCount") or \
                canonical_match.group(13) != str(channel_count) or \
                canonical_match.group(14) != str(channel_count):
            issues.append("{} canonical does not match the complete current plan4 grammar".format(where))
        else:
            canon_support = tuple(canonical_match.group(index) for index in range(3, 9))
            want_support = (
                str(support.get("entryCapacitor")).lower(),
                str(support.get("fiveVoltIndicator")).lower(),
                str(support.get("twelveVoltIndicator")).lower(),
                str(support.get("sensorInputFilterMask")),
                str(support.get("outputIndicatorMask")),
                str(support.get("fiveVoltBleeder")).lower())
            if canon_support != want_support:
                issues.append("{} canonical support identity disagrees with plan4 manifest".format(where))
            if canonical_match.group(11) != expected.get("selectedFault"):
                issues.append("{} canonical selected fault disagrees with plan4 manifest".format(where))
            allowed_selected_faults = set(COMMON_FAULTS) | {relay}
            if canonical_match.group(11) not in allowed_selected_faults:
                issues.append("{} canonical selected fault is incompatible with channel count".format(where))
            expected_pulldowns = "RPIN_A:1000ohm(A_RAW,CTRL_RETURN)"
            if channel_count == 2:
                expected_pulldowns += ",RPIN_B:1000ohm(B_RAW,CTRL_RETURN)"
            if canonical_match.group(15) != expected_pulldowns:
                issues.append("{} canonical sensor pull-down order/identity is invalid".format(where))
    return issues


def parse_plan_metadata(text, manifest, required_seeds, where):
    lines = []
    rows = []
    for line_number, line in enumerate(text.splitlines(), 1):
        if line.startswith(PLAN_METADATA_PREFIX):
            raw = line[len(PLAN_METADATA_PREFIX):]
            try:
                plan = json.loads(raw, object_pairs_hook=unique_json_object,
                                  parse_constant=reject_json_constant)
            except (json.JSONDecodeError, AuditInputError) as exc:
                raise AuditInputError("{} has malformed Q30_CASE_PLAN at line {}: {}".format(
                    where, line_number, exc)) from exc
            if not isinstance(plan, dict):
                raise AuditInputError("{} Q30_CASE_PLAN at line {} is not an object".format(where, line_number))
            seed = plan.get("seed")
            if not isinstance(seed, str) or not SEED_RE.fullmatch(seed) or \
                    int(seed, 10) < SIGNED_LONG_MIN or int(seed, 10) > SIGNED_LONG_MAX or \
                    seed not in manifest["bySeed"]:
                raise AuditInputError("{} contains a Q30 plan seed outside the independent manifest: {!r}".format(where, seed))
            if any(existing.get("seed") == seed for existing in rows):
                raise AuditInputError("{} repeats Q30_CASE_PLAN seed {}".format(where, seed))
            issues = validate_plan_metadata_row(plan, manifest["bySeed"][seed],
                                                 "{} line {}".format(where, line_number))
            if issues:
                raise AuditInputError("; ".join(issues))
            rows.append(plan)
            lines.append(line)
    if not rows:
        raise AuditInputError("{} has no v4 Q30_CASE_PLAN rows".format(where))
    plan_seeds = [row["seed"] for row in rows]
    observed_requested = [seed for seed in plan_seeds if seed in set(required_seeds)]
    if observed_requested != required_seeds:
        raise AuditInputError("{} plan rows omit or reorder requested seeds; found {}".format(
            where, observed_requested))
    return {"lines": lines, "rows": rows, "seeds": plan_seeds,
            "bySeed": {row["seed"]: row for row in rows}}


def validate_plan_source_pair(log, receipt, manifest, required_seeds, label):
    log_plans = parse_plan_metadata(log, manifest, required_seeds, label + " host log")
    receipt_plans = parse_plan_metadata(receipt, manifest, required_seeds, label + " receipt")
    if log_plans["lines"] != receipt_plans["lines"]:
        raise AuditInputError("{} host-log and receipt Q30_CASE_PLAN rows differ".format(label))
    return log_plans


def expected_normal_proof_work_units(channel_count):
    if channel_count == 1:
        program_steps, work_units = 23, 3
    elif channel_count == 2:
        program_steps, work_units = 47, 5
    else:
        raise AuditInputError("normal proof-work formula has unsupported channel count")
    return 5 * (11 + program_steps + 3 * (work_units - 1) +
                (work_units - 1) + 4)


def run_plan_metadata_selftests(plans, manifest, seeds):
    seed = seeds[0]
    original = plans[seed]
    manifest_row = manifest["bySeed"][seed]
    cases = []

    def record(name, candidate, should_reject=True):
        rejected = bool(validate_plan_metadata_row(candidate, manifest_row, "canary"))
        cases.append({"name": name,
                      "status": "PASS" if rejected == should_reject else "FAIL"})

    record("exact plan4 metadata accepted", copy.deepcopy(original), should_reject=False)
    changed = copy.deepcopy(original)
    changed["planEpoch"] = 3
    record("stale plan epoch rejected", changed)
    changed = copy.deepcopy(original)
    changed["diagnosticTemplate"] = "RB30_CHANNEL_INPUT_SWEEP@1"
    record("obsolete diagnostic template rejected", changed)
    changed = copy.deepcopy(original)
    changed["canonical"] += ";foreign=true"
    record("canonical plan mutation rejected", changed)
    changed = copy.deepcopy(original)
    changed["faults"][0]["owner"] = "FOREIGN"
    record("fault owner mutation rejected", changed)
    changed = copy.deepcopy(original)
    changed["diagnosticSamplesPerHypothesis"] = 37 if original["channelCount"] == 1 else 17
    record("channel sample population mutation rejected", changed)
    return cases


def parse_key_values(text):
    return {match.group(1): match.group(2) for match in TOKEN_RE.finditer(text)}


def parse_unique_key_values(text, label):
    fields = {}
    issues = []
    for token in text.split():
        if "=" not in token:
            issues.append("{} contains a malformed key/value token".format(label))
            continue
        key, value = token.split("=", 1)
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", key):
            issues.append("{} contains a malformed key".format(label))
            continue
        if key in fields:
            issues.append("{} repeats field {}".format(label, key))
            continue
        fields[key] = value
    return fields, issues


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


def run_cleanup_audit(log, runner_scope="full",
                      focused_suite_count=DEFAULT_FOCUSED_SUITE_COUNT):
    success = re.findall(
        r"(?m)^CLEANUP: current JVM contract classes and task-owned scratch removed\.\r?$",
        log,
    )
    failures = re.findall(r"(?m)^CURRENT_CONTRACT_CLEANUP:.*$", log)
    full_runner_success = FULL_RUNNER_SUCCESS_RE.findall(log)
    focused_runner_successes = [
        match.group(1) for match in FOCUSED_CENSUS_SUCCESS_RE.finditer(log)
    ]
    runner_failures = re.findall(r"(?m)^CURRENT_CONTRACT_FAILURE:.*$", log)
    timeout_diagnostics = re.findall(
        r"(?im)^.*Bounded process .* exceeded 60000ms; logs retained at .*",
        log,
    )
    scope_issues = []
    focused_count_valid = type(focused_suite_count) is int and focused_suite_count >= 2
    if not focused_count_valid:
        scope_issues.append("focused suite count must be an integer of at least 2")
    if runner_scope == "full":
        runner_success = full_runner_success
        if focused_runner_successes:
            scope_issues.append("focused selected-census marker is not full-matrix evidence")
        if len(full_runner_success) != 1:
            scope_issues.append("exactly one full-runner PASS marker is required")
    elif runner_scope == "selected-census":
        runner_success = []
        if full_runner_success:
            scope_issues.append("full-runner marker cannot be relabeled as selected-census evidence")
        if len(focused_runner_successes) != 1:
            scope_issues.append("exactly one focused selected-census PASS marker is required")
        elif focused_count_valid and focused_runner_successes[0] != str(focused_suite_count):
            scope_issues.append(
                "focused selected-census PASS marker reports {} Java suites; expected {}".format(
                    focused_runner_successes[0], focused_suite_count)
            )
        elif focused_count_valid:
            runner_success = focused_runner_successes
    else:
        runner_success = []
        scope_issues.append("unknown runner scope")
    return {
        "runnerScope": runner_scope,
        "expectedFocusedSuiteCount": (
            focused_suite_count if runner_scope == "selected-census" else None),
        "observedFocusedSuiteCounts": focused_runner_successes,
        "cleanupSuccessMarkers": len(success),
        "cleanupFailureMarkers": failures,
        "runnerSuccessMarkers": len(full_runner_success) + len(focused_runner_successes),
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
    selected = (
        "PASS: focused current contracts; 2 Java suites. "
        "Full matrix and independent oracles NOT RUN.\n"
    )
    selected_three = (
        "PASS: focused current contracts; 3 Java suites. "
        "Full matrix and independent oracles NOT RUN.\n"
    )
    full = "PASS: current contracts; 84 Java suites, independent seed/value/role oracles and report protocol.\n"
    cases = (
        ("selected marker accepted only in selected-census scope",
         run_cleanup_audit(cleanup + selected, "selected-census")["passed"]),
        ("three-suite marker accepted only with an explicit matching count",
         run_cleanup_audit(cleanup + selected_three, "selected-census", 3)["passed"]),
        ("selected marker rejected by default full scope",
         not run_cleanup_audit(cleanup + selected)["passed"]),
        ("missing runner marker rejected",
         not run_cleanup_audit(cleanup, "selected-census")["passed"]),
        ("wrong configured focused suite count rejected",
         not run_cleanup_audit(cleanup + selected_three, "selected-census", 2)["passed"]),
        ("default two-suite count does not accept three-suite evidence",
         not run_cleanup_audit(cleanup + selected_three, "selected-census")["passed"]),
        ("duplicate focused markers rejected",
         not run_cleanup_audit(cleanup + selected + selected, "selected-census")["passed"]),
        ("focused suite count below two rejected",
         not run_cleanup_audit(cleanup + selected, "selected-census", 1)["passed"]),
        ("full marker cannot be relabeled as selected-census",
         not run_cleanup_audit(cleanup + full, "selected-census")["passed"]),
        ("selected marker cannot satisfy full scope",
         not run_cleanup_audit(cleanup + selected, "full")["passed"]),
        ("full marker remains accepted in full scope",
         run_cleanup_audit(cleanup + full, "full")["passed"]),
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
    issues = []
    for index, match in enumerate(matches):
        fields, parse_issues = parse_unique_key_values(
            match.group(1), "Q30_STEP_PAIR[{}]".format(index))
        pairs.append(fields)
        issues.extend(parse_issues)
    return pairs, issues


def sensitivity_plan_identity(expected_plan):
    if not isinstance(expected_plan, dict) or not isinstance(expected_plan.get("support"), dict):
        return None
    support = expected_plan["support"]
    def bool_text(name):
        value = support.get(name)
        return str(value).lower() if type(value) is bool else "INVALID"
    return {
        "support": "C12:{},S5:{},S12:{},filters:{},outputIndicators:{},bleeder5:{}".format(
            bool_text("C12"), bool_text("S5"), bool_text("S12"),
            support.get("filters"), support.get("outputIndicators"),
            bool_text("bleeder5")),
        "topology": expected_plan.get("topology"),
        "canonicalPlan": expected_plan.get("canonical"),
    }


def require_sensitivity_plan_identity(fields, expected_plan, label, issues):
    expected = sensitivity_plan_identity(expected_plan)
    if expected is None:
        issues.append("{} has no authenticated Q30_CASE_PLAN identity".format(label))
        return
    for name, value in expected.items():
        if fields.get(name) != value:
            issues.append("{} {} differs from authenticated plan/manifest".format(label, name))


def replace_marker_field(body, marker_prefix, field, replacement, duplicate):
    lines = body.splitlines(keepends=True)
    changed = False
    for index, line in enumerate(lines):
        content = line.rstrip("\r\n")
        ending = line[len(content):]
        if not content.startswith(marker_prefix):
            continue
        fields = content.split()
        positions = [position for position, token in enumerate(fields)
                     if token.split("=", 1)[0] == field and "=" in token]
        if len(positions) != 1:
            return False, body
        token = fields[positions[0]]
        if duplicate:
            fields.append(token)
        else:
            fields[positions[0]] = field + "=" + replacement
        lines[index] = " ".join(fields) + ending
        changed = True
        break
    return changed, "".join(lines)


def sensitivity_entry_issues(entry, seed, fault, expected_plan=None):
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
    pairs, pair_parse_issues = parse_pair_body(body)
    issues.extend(pair_parse_issues)
    selected_pairs = [pair for pair in pairs
                      if pair.get("seed") == seed and pair.get("fault") == fault]
    if len(selected_pairs) != 1:
        issues.append("exactly one requested Q30_STEP_PAIR is required")
        pair = selected_pairs[0] if selected_pairs else (pairs[0] if pairs else {})
    else:
        pair = selected_pairs[0]

    require_sensitivity_plan_identity(pair, expected_plan, "Q30_STEP_PAIR", issues)

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

    final_lines = [line for line in body.splitlines()
                   if line.startswith(SENSITIVITY_FINAL_PREFIX)]
    if len(final_lines) != 1:
        issues.append("current sensitivity final PASS marker is missing, ambiguous, or malformed")
    else:
        final, final_parse_issues = parse_unique_key_values(
            final_lines[0][len(SENSITIVITY_FINAL_PREFIX):],
            "current sensitivity final PASS marker")
        issues.extend(final_parse_issues)
        if set(final) != SENSITIVITY_FINAL_FIELDS:
            issues.append("current sensitivity final PASS marker has missing or extra fields")
        assertions = ints(final.get("assertions"))
        elapsed = ints(final.get("elapsedMillis"))
        if assertions is None or assertions < 1 or elapsed is None or elapsed < 0:
            issues.append("current sensitivity final PASS marker has invalid assertion/timing counts")
        if (final.get("seed") != seed or final.get("faults") != "1" or
                final.get("referenceMaximumStepSeconds") != CURRENT_REFERENCE_STEP or
                final.get("productionCandidateMaximumStepSeconds") != CURRENT_PRODUCTION_STEP or
                final.get("candidateKind") != CURRENT_CANDIDATE_KIND):
            issues.append("sensitivity final marker is not the current 2.5 us / 5 us profile")
        require_sensitivity_plan_identity(final, expected_plan,
                                          "sensitivity final PASS marker", issues)

    seed_rows = []
    seed_parse_issues = []
    for line in body.splitlines():
        if line.startswith("Q30_STEP_SEED "):
            fields, parse_issues = parse_unique_key_values(
                line[len("Q30_STEP_SEED "):], "Q30_STEP_SEED")
            seed_rows.append(fields)
            seed_parse_issues.extend(parse_issues)
    issues.extend(seed_parse_issues)
    if len(seed_rows) != 1:
        issues.append("exactly one Q30_STEP_SEED marker is required")
    else:
        if (seed_rows[0].get("seed") != seed or
                seed_rows[0].get("referenceMaximumStepSeconds") != CURRENT_REFERENCE_STEP or
                seed_rows[0].get("productionCandidateMaximumStepSeconds") != CURRENT_PRODUCTION_STEP or
                seed_rows[0].get("candidateKind") != CURRENT_CANDIDATE_KIND):
            issues.append("Q30_STEP_SEED marker is not bound to the current solver profile")
        require_sensitivity_plan_identity(seed_rows[0], expected_plan,
                                          "Q30_STEP_SEED", issues)

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


def expected_keys(seeds, faults_by_seed):
    return [(seed, fault) for seed in seeds for fault in faults_by_seed[seed]]


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


def validate_service_census(censuses, seeds, expected_count, faults_by_seed):
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
    if census["faults"] != 5 or census["order"] != SERVICE_CENSUS_FAULT_ORDER:
        issues.append("service census fault count/order differs from the channel-aware five-fault order")
    if expected_count != sum(len(faults_by_seed[seed]) for seed in seeds):
        issues.append("service census expected row count differs from plan-bound fault population")
    if census["childBudgetMs"] != CHILD_BUDGET_MS:
        issues.append("service census child budget is not the maintained 60000 ms")
    return issues


def validate_sensitivity_census(censuses, seeds, expected_count, faults_by_seed):
    issues = []
    if len(censuses) != 1:
        return ["expected exactly one sensitivity census, found {}".format(len(censuses))]
    census = censuses[0]
    if expected_count != sum(len(faults_by_seed[seed]) for seed in seeds):
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


def _selftest_record(entries, validator, attempted_fn, log, census_full, seeds, faults_by_seed):
    rows, _ = make_suite_rows(seeds, faults_by_seed, entries, log, validator,
                              attempted_fn, census_full)
    return rows


def run_corruption_selftests(service_entries, sensitivity_entries, service_log,
                             sensitivity_log, sensitivity_validator,
                             seeds, faults_by_seed):
    results = []
    seed, fault = seeds[0], faults_by_seed[seeds[0]][0]
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
            service_log, False, seeds, faults_by_seed)
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
            seeds, faults_by_seed, duplicated, service_log, service_entry_issues,
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
                              if entry.get("seed") == seed and entry.get("fault") == fault and
                              sensitivity_validator(entry, seed, fault)["status"] == "PASS"), None)
    if sensitivity_pass is None:
        results.append({"name": "sensitivity termination", "status": "NOT RUN",
                        "reason": "no passing actual sensitivity receipt row available to mutate"})
        results.append({"name": "sensitivity legacy 10us rejection", "status": "NOT RUN",
                        "reason": "no passing current-profile receipt row available to mutate"})
    else:
        corrupted_termination = copy.deepcopy(sensitivity_pass)
        corrupted_termination["metadata"]["termination"] = "False"
        termination_verdict = sensitivity_validator(corrupted_termination, seed, fault)
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
        version_verdict = sensitivity_validator(corrupted_version, seed, fault)
        results.append({
            "name": "sensitivity legacy 10us rejection",
            "status": "PASS" if (corrupted_version["body"] != original_body and
                                  version_verdict["status"] == "FAIL") else "FAIL",
            "basis": "changed current candidate markers to the retired 10 us profile in an actual passing receipt row",
            "observedRowStatus": version_verdict["status"],
        })

        identity_canaries = (
            ("sensitivity final plan identity", "PASS: Q30 production solver step sensitivity ",
             "canonicalPlan"),
            ("sensitivity pair plan identity", "Q30_STEP_PAIR ",
             "topology"),
            ("sensitivity seed plan identity", "Q30_STEP_SEED ",
             "support"),
        )
        for name, prefix, field in identity_canaries:
            mutated = copy.deepcopy(sensitivity_pass)
            original_body = mutated["body"]
            marker_line = next((line for line in original_body.splitlines()
                                if line.startswith(prefix)), None)
            marker_fields = (parse_key_values(marker_line[len(prefix):])
                             if marker_line is not None else {})
            original_value = marker_fields.get(field)
            replacement = "CANARY_CORRUPTED_" + (original_value or "MISSING")
            changed_body, mutated["body"] = replace_marker_field(
                original_body, prefix, field, replacement, duplicate=False)
            verdict = sensitivity_validator(mutated, seed, fault)
            results.append({
                "name": name,
                "status": "PASS" if changed_body and verdict["status"] == "FAIL" else "FAIL",
                "basis": "corrupted one actual plan-bound marker in memory",
                "observedRowStatus": verdict["status"],
            })

        duplicate_canaries = (
            ("sensitivity final duplicate plan key", "PASS: Q30 production solver step sensitivity ",
             "canonicalPlan"),
            ("sensitivity pair duplicate plan key", "Q30_STEP_PAIR ", "canonicalPlan"),
            ("sensitivity seed duplicate plan key", "Q30_STEP_SEED ", "canonicalPlan"),
        )
        for name, prefix, field in duplicate_canaries:
            mutated = copy.deepcopy(sensitivity_pass)
            original_body = mutated["body"]
            changed_body, mutated["body"] = replace_marker_field(
                original_body, prefix, field, None, duplicate=True)
            verdict = sensitivity_validator(mutated, seed, fault)
            results.append({
                "name": name,
                "status": "PASS" if changed_body and verdict["status"] == "FAIL" else "FAIL",
                "basis": "duplicated a plan-bound key on an actual marker in memory",
                "observedRowStatus": verdict["status"],
            })
    return results


def audit_suite(name, seeds, faults_by_seed, entries, log, census, cleanup_audit,
                validator, attempted_fn, census_validator):
    expected = expected_keys(seeds, faults_by_seed)
    count = len(expected)
    census_issues = census_validator(census, seeds, count, faults_by_seed)
    census_claims_full = False
    if len(census) == 1:
        census_claims_full = census[0].get("attempted") == count
    rows, unexpected = make_suite_rows(
        seeds, faults_by_seed, entries, log, validator, attempted_fn, census_claims_full)
    expected_order = expected
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
        service_cleanup, sensitivity_cleanup, sensitivity_validator, faults_by_seed):
    """Remove actual passing rows in memory and require each full suite to fail."""
    cases = []
    expected_count = len(expected_keys(seeds, faults_by_seed))
    specifications = (
        ("service", service_entries, service_log, service_census, service_cleanup,
         faults_by_seed, service_entry_issues, row_attempted_in_service_log,
         validate_service_census),
        ("sensitivity", sensitivity_entries, sensitivity_log, sensitivity_census,
         sensitivity_cleanup, faults_by_seed, sensitivity_validator,
         row_attempted_in_sensitivity_log, validate_sensitivity_census),
    )
    for name, entries, log, census, cleanup_audit, faults, validator, attempted_fn, census_validator in specifications:
        expected_key = (seeds[0], faults[seeds[0]][0])
        removed = next((entry for entry in entries
                        if (entry.get("seed"), entry.get("fault")) == expected_key), None)
        if removed is None:
            cases.append({
                "name": name + " plan-bound census completeness",
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
            corrupted["expectedRowCount"] == expected_count and
            len(corrupted["rows"]) == expected_count and
            missing is not None and missing["status"] != "PASS"
        )
        cases.append({
            "name": name + " plan-bound census completeness",
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
                        help="require full-runner PASS by default; selected-census requires one matching focused-suite marker")
    parser.add_argument("--focused-suite-count", type=parse_focused_suite_count,
                        default=DEFAULT_FOCUSED_SUITE_COUNT, metavar="N",
                        help="selected-census Java suite count to require (minimum 2; default: 2)")
    parser.add_argument("--seeds", required=True,
                        help="1-32 distinct plan4 manifest seeds, comma separated")
    parser.add_argument("--manifest", required=True,
                        help="explicit independent Q30 plan4 manifest JSON")
    parser.add_argument("--plan-export-log", required=True,
                        help="host log from the focused 77-seed Java plan export")
    parser.add_argument("--plan-export-receipt", required=True,
                        help="ReceiptOutputPath from the focused 77-seed Java plan export")
    parser.add_argument("--plan-differential", required=True,
                        help="independent differential binding the plan export streams and manifest")
    parser.add_argument("--plan-run", required=True,
                        help="runner summary JSON for the focused 77-seed Java plan export")
    parser.add_argument("--plan-source", required=True,
                        help="the exact Rb30Plan.java source used by the Java export")
    parser.add_argument("--service-log", help="maintained runner stdout log containing service suite")
    parser.add_argument("--service-receipt", help="maintained ReceiptOutputPath containing service rows")
    parser.add_argument("--sensitivity-log", help="maintained runner stdout log containing sensitivity suite")
    parser.add_argument("--sensitivity-receipt", help="maintained ReceiptOutputPath containing sensitivity rows")
    parser.add_argument("--log", help="shared runner stdout log when both suites ran together")
    parser.add_argument("--receipt", help="shared runner ReceiptOutputPath when both suites ran together")
    parser.add_argument("--report", help="write the JSON audit report to this path instead of stdout")
    args = parser.parse_args(argv)

    if (args.runner_scope == "full" and
            args.focused_suite_count != DEFAULT_FOCUSED_SUITE_COUNT):
        raise AuditInputError(
            "--focused-suite-count may be overridden only with --runner-scope selected-census"
        )

    seeds = parse_seed_csv(args.seeds)
    manifest = parse_plan_manifest(args.manifest)
    plan_export_evidence = validate_plan_export_evidence(
        manifest, args.manifest, args.plan_export_log, args.plan_export_receipt,
        args.plan_differential, args.plan_run, args.plan_source)
    unknown = [seed for seed in seeds if seed not in manifest["bySeed"]]
    if unknown:
        raise AuditInputError("--seeds includes values outside the independent plan4 manifest: {}".format(unknown))
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
                "selected-census requires one combined --log/--receipt from the configured focused run"
            )
        service_log_path, service_receipt_path, sensitivity_log_path, sensitivity_receipt_path = split_values

    service_log = read_text(service_log_path, "service stdout log")
    service_receipt_text = read_text(service_receipt_path, "service receipt")
    sensitivity_log = read_text(sensitivity_log_path, "sensitivity stdout log")
    sensitivity_receipt_text = read_text(sensitivity_receipt_path, "sensitivity receipt")

    service_plans = validate_plan_source_pair(
        service_log, service_receipt_text, manifest, seeds, "service")
    sensitivity_plans = validate_plan_source_pair(
        sensitivity_log, sensitivity_receipt_text, manifest, seeds, "sensitivity")
    for seed in seeds:
        if service_plans["bySeed"][seed] != sensitivity_plans["bySeed"][seed]:
            raise AuditInputError("service/sensitivity plan metadata differ for seed {}".format(seed))

    def sensitivity_validator(entry, seed, fault):
        return sensitivity_entry_issues(
            entry, seed, fault, sensitivity_plans["bySeed"].get(seed))

    faults_by_seed = {}
    for seed in seeds:
        channel_count = service_plans["bySeed"][seed]["channelCount"]
        relay_fault = "RELAY_A_COIL_OPEN" if channel_count == 1 else "RELAY_B_COIL_OPEN"
        faults_by_seed[seed] = [relay_fault] + list(COMMON_FAULTS)

    service_entries = parse_service_entries(service_receipt_text)
    sensitivity_entries = parse_sensitivity_entries(sensitivity_receipt_text)
    service_census = parse_service_census(service_log)
    sensitivity_census = parse_sensitivity_census(sensitivity_log)
    service_cleanup = run_cleanup_audit(
        service_log, args.runner_scope, args.focused_suite_count)
    sensitivity_cleanup = run_cleanup_audit(
        sensitivity_log, args.runner_scope, args.focused_suite_count)

    service = audit_suite(
        "service", seeds, faults_by_seed, service_entries, service_log, service_census,
        service_cleanup, service_entry_issues, row_attempted_in_service_log,
        validate_service_census,
    )
    sensitivity = audit_suite(
        "sensitivity", seeds, faults_by_seed, sensitivity_entries,
        sensitivity_log, sensitivity_census, sensitivity_cleanup,
        sensitivity_validator, row_attempted_in_sensitivity_log,
        validate_sensitivity_census,
    )
    corruption = run_corruption_selftests(
        service_entries, sensitivity_entries, service_log, sensitivity_log,
        sensitivity_validator, seeds,
        faults_by_seed)
    plan_selftests = run_plan_metadata_selftests(
        service_plans["bySeed"], manifest, seeds)
    scope_selftests = run_runner_scope_selftests()
    census_selftests = run_census_completeness_selftests(
        seeds, service_entries, sensitivity_entries, service_log, sensitivity_log,
        service_census, sensitivity_census, service_cleanup, sensitivity_cleanup,
        sensitivity_validator, faults_by_seed)
    all_selftests = corruption + plan_selftests + scope_selftests + census_selftests
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
        "schema": "q30-native-service-audit-v2-plan4",
        "status": report_status,
        "summaryLabel": summary_label,
        "runnerScope": args.runner_scope,
        "expectedFocusedSuiteCount": (
            args.focused_suite_count if args.runner_scope == "selected-census" else None),
        "selectedCensusStatus": (
            "PASS" if args.runner_scope == "selected-census" and overall_pass else
            "FAIL" if args.runner_scope == "selected-census" else "NOT RUN"
        ),
        "fullMatrixStatus": (
            "PASS" if args.runner_scope == "full" and overall_pass else
            "FAIL" if args.runner_scope == "full" else "NOT RUN"
        ),
        "requestedSeeds": seeds,
        "planManifest": {
            "schema": manifest["raw"]["schema"],
            "status": manifest["manifestStatus"],
            "seedCount": len(manifest["seeds"]),
            "qualification": "PASS_JAVA_PLAN_CROSSCHECK_ONLY",
        },
        "javaPlanExportEvidence": plan_export_evidence,
        "planMetadataOrder": service_plans["seeds"],
        "channelCountBySeed": {
            seed: service_plans["bySeed"][seed]["channelCount"] for seed in seeds
        },
        "expectedNormalProofWorkUnitsBySeed": {
            seed: expected_normal_proof_work_units(
                service_plans["bySeed"][seed]["channelCount"])
            for seed in seeds
        },
        "faultOrderBySeed": {seed: faults_by_seed[seed] for seed in seeds},
        "sensitivityFaultOrderBySeed": {seed: faults_by_seed[seed] for seed in seeds},
        "serviceFaultOrderToken": ",".join(SERVICE_CENSUS_FAULT_ORDER),
        "expectedRowsPerSuite": len(expected_keys(seeds, faults_by_seed)),
        "expectedTotalRows": 2 * len(expected_keys(seeds, faults_by_seed)),
        "observedTotalRows": len(service_entries) + len(sensitivity_entries),
        "currentSensitivityProfile": {
            "referenceMaximumStepSeconds": CURRENT_REFERENCE_STEP,
            "productionCandidateMaximumStepSeconds": CURRENT_PRODUCTION_STEP,
            "candidateKind": CURRENT_CANDIDATE_KIND,
        },
        "service": service,
        "sensitivity": sensitivity,
        "corruptionSelfTests": corruption,
        "planMetadataSelfTests": plan_selftests,
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
