#!/usr/bin/env python3
"""Prepare a bound, source-only LU support-census OFF/ON/OFF sequence.

This helper is intentionally inert until its caller executes it.  It copies no
source, runs no build or native test, and starts no host.  When invoked, it
checks the already prepared control/candidate trees, captures read-only source
and compiled-input identities, and writes a new measurement plan plus an
execution binding.  The later sequence driver refuses any identity drift.
"""

import argparse
import hashlib
import importlib.util
import json
import sys
from pathlib import Path


sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent
REPO_DEFAULT = Path(r"<TASK_PATH>")
BASE_SOURCE_DEFAULT = ROOT / "scratch/pivot-lower-fusion-r2-preparation/candidate"
CANDIDATE_SOURCE_DEFAULT = ROOT / "lu-support-census-r2-preparation/candidate"
SOURCE_AUDIT_DEFAULT = ROOT / "lu-support-census-r2-preparation/source-audit-r4.json"
BASE_AUDIT_DEFAULT = ROOT / "scratch/pivot-lower-fusion-r2-preparation/source-audit.json"
SEED = "10014"
PACKAGE_COUNT = 40
BASE_APPLICATION_QUERY = (
    "circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&"
    "tsjVerifyQ30=true&tsjQ30Coordinator=true&tsjQ30Seed=" + SEED
)
SUPPORT_QUERY = "tsjQ30SupportCensus=true"
SOURCE_AUDIT_SHA256 = (
    "d021ec847e38fc792e7dbc095e2de8a012930010ede1aff1888e0c9f7a3d94d0"
)
BASE_AUDIT_SHA256 = (
    "0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197"
)
EXPECTED_SOURCE_COUNTS = {"control": 1340, "candidate": 1343}
EXPECTED_RUNTIME = {
    "control": {
        "sha256": "53f869e8164f6b8edc3b75ba1e6a299a984de5c88829b60b04a9ca59e76787a9",
        "sourceSha256": "cb257f2158d02ece1fb0a3f7ac7386ec9a14e43100be1ad337d2e344aae0828e",
        "webSha256": "e7377863185fc8208cb356474b39d14c9a82a35f5470bd7824d68113e1c03961",
        "fileCount": 1525,
    },
    "candidate": {
        "sha256": "f7ea79156a7f313b4221e71870fc8cd0ff7fb7f48edd84151ad954e1461fdf15",
        "sourceSha256": "c247281c076f6e6e1cab891232d052429e5a0e3c1c5989f0f028676a7ec26531",
        "webSha256": "7c6fd8378338b33b9789477e0de05bd243979714577f0caac8135682186cd67c",
        "fileCount": 1526,
    },
}
HOST_RUNNER_RELATIVE = "tests/browser/compiled_attribute_acceptance.py"
HOST_RUNNER_SHA256 = "1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4"
METADATA_NAME = "lu-support-census-metadata-validator.py"


class PreparationError(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise PreparationError(message)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    return sha256_bytes(path.read_bytes())


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n").encode("utf-8")


def load_json(path, label):
    need(path.is_file(), label + " does not exist: " + str(path))
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PreparationError("could not read " + label + ": " + str(error))


def safe_relative(value, label):
    need(isinstance(value, str) and value and not value.startswith("/"),
         label + " must be a relative path")
    relative = Path(value.replace("/", "\\"))
    need(not relative.is_absolute() and ".." not in relative.parts,
         label + " escapes its source root")
    return relative


def verify_source_tree(source, records, label):
    source = source.resolve()
    need(source.is_dir() and not source.is_symlink(), label + " is not a real directory")
    need(isinstance(records, list) and records, label + " source records are missing")
    paths = set()
    for index, record in enumerate(records):
        need(isinstance(record, dict), label + " record {} is not an object".format(index))
        need(set(record) == {"path", "bytes", "sha256"},
             label + " record {} field set changed".format(index))
        relative = safe_relative(record["path"], label + " record path")
        key = relative.as_posix()
        need(key not in paths, label + " contains duplicate path " + key)
        paths.add(key)
        path = source / relative
        need(path.is_file() and not path.is_symlink(),
             label + " is missing " + key)
        raw = path.read_bytes()
        need(len(raw) == record["bytes"] and sha256_bytes(raw).casefold() ==
             str(record["sha256"]).casefold(),
             label + " changed " + key)
    return {
        "root": str(source),
        "fileCount": len(records),
        "recordsSha256": sha256_bytes(json.dumps(
            records, ensure_ascii=False, sort_keys=True,
            separators=(",", ":")).encode("utf-8")),
    }


def load_host_module(repo):
    host_path = (repo / HOST_RUNNER_RELATIVE).resolve()
    need(host_path.is_file(), "maintained host runner is missing: " + str(host_path))
    need(sha256_file(host_path).casefold() == HOST_RUNNER_SHA256,
         "maintained host runner SHA-256 changed before binding")
    spec = importlib.util.spec_from_file_location("q30_lu_sequence_host", str(host_path))
    need(spec is not None and spec.loader is not None,
         "could not load maintained host runner")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    need(callable(getattr(module, "capture_input_manifest", None)),
         "maintained host runner lacks capture_input_manifest")
    return module, host_path


def runtime_summary(host, source, expected, label):
    manifest = host.capture_input_manifest(source)
    summary = {key: manifest[key] for key in (
        "schema", "roots", "fileCount", "sourceFileCount", "webFileCount",
        "sha256", "sourceSha256", "webSha256")}
    for key, value in expected.items():
        need(summary[key] == value,
             label + " runtime " + key + " differs from the predeclared identity")
    return summary


def source_records(audit, arm, label):
    arms = audit.get("armInputs")
    need(isinstance(arms, dict) and isinstance(arms.get(arm), list),
         label + " audit is missing armInputs." + arm)
    return arms[arm]


def write_new(path, value):
    need(not path.exists(), "refusing to overwrite existing output: " + path.name)
    path.write_bytes(json_bytes(value))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=REPO_DEFAULT)
    parser.add_argument("--source-audit", type=Path, default=SOURCE_AUDIT_DEFAULT)
    parser.add_argument("--base-audit", type=Path, default=BASE_AUDIT_DEFAULT)
    parser.add_argument("--control-source", type=Path, default=BASE_SOURCE_DEFAULT)
    parser.add_argument("--candidate-source", type=Path, default=CANDIDATE_SOURCE_DEFAULT)
    parser.add_argument("--plan", type=Path,
                        default=ROOT / "lu-support-census-r4-measurement-plan.json")
    parser.add_argument("--binding", type=Path,
                        default=ROOT / "lu-support-census-r4-execution-binding.json")
    args = parser.parse_args(argv)
    try:
        repo = args.repo.resolve()
        source_audit_path = args.source_audit.resolve()
        base_audit_path = args.base_audit.resolve()
        control_source = args.control_source.resolve()
        candidate_source = args.candidate_source.resolve()
        plan_path = args.plan.resolve()
        binding_path = args.binding.resolve()
        need(repo.is_dir(), "repository root is missing")
        need(sha256_file(source_audit_path).casefold() == SOURCE_AUDIT_SHA256,
             "source-audit-r4 SHA-256 differs from the reviewed source binding")
        need(sha256_file(base_audit_path).casefold() == BASE_AUDIT_SHA256,
             "pivot-lower baseline source audit changed")
        audit = load_json(source_audit_path, "source-audit-r4")
        base_audit = load_json(base_audit_path, "pivot-lower baseline source audit")
        need(audit.get("sourceInputs") == EXPECTED_SOURCE_COUNTS["candidate"],
             "source-audit-r4 candidate source count is not 1343")
        candidate_records = source_records(audit, "candidate", "source-audit-r4")
        control_records = source_records(base_audit, "candidate", "pivot-lower baseline")
        need(len(candidate_records) == EXPECTED_SOURCE_COUNTS["candidate"],
             "candidate source record count is not 1343")
        need(len(control_records) == EXPECTED_SOURCE_COUNTS["control"],
             "control source record count is not 1340")
        source_identity = {
            "control": verify_source_tree(control_source, control_records, "control source"),
            "candidate": verify_source_tree(candidate_source, candidate_records, "candidate source"),
        }
        host, host_path = load_host_module(repo)
        runtime_identity = {
            "control": runtime_summary(host, control_source,
                                        EXPECTED_RUNTIME["control"], "control"),
            "candidate": runtime_summary(host, candidate_source,
                                          EXPECTED_RUNTIME["candidate"], "candidate"),
        }
        timing_path = ROOT / "run_lu_support_census_timing.py"
        pair_path = ROOT / "validate_lu_support_census_pair_root.py"
        metadata_path = ROOT / METADATA_NAME
        for path, label in ((timing_path, "timing adapter"),
                            (pair_path, "pair validator"),
                            (metadata_path, "metadata validator")):
            need(path.is_file(), label + " is missing: " + str(path))

        sequence = [
            {"label": "lu-support-census-r4-01-off-10014", "arm": "control",
             "supportEnabled": False, "seed": SEED},
            {"label": "lu-support-census-r4-02-on-10014", "arm": "candidate",
             "supportEnabled": True, "seed": SEED},
            {"label": "lu-support-census-r4-03-off-10014", "arm": "control",
             "supportEnabled": False, "seed": SEED},
        ]
        plan = {
            "schema": 1,
            "status": "PREDECLARED",
            "purpose": "PRIVATE_LU_SUPPORT_CENSUS",
            "seed": SEED,
            "requestedPackageCount": PACKAGE_COUNT,
            "productionAcceptance": False,
            "sourceAuditPath": str(source_audit_path.relative_to(ROOT)).replace("\\", "/"),
            "sourceAuditSha256": sha256_file(source_audit_path),
            "baselineAuditSha256": sha256_file(base_audit_path),
            "source": {
                "control": "scratch/pivot-lower-fusion-r2-preparation/candidate",
                "candidate": "lu-support-census-r2-preparation/candidate",
            },
            "runtime": runtime_identity,
            "applicationQuery": BASE_APPLICATION_QUERY,
            "supportQuery": SUPPORT_QUERY,
            "sequence": sequence,
            "requirements": [
                "Use one fresh owned browser/host row for each OFF/ON/OFF run and preserve every raw report.",
                "Require exact seed 10014, Q30 coordinator PASS:complete, package count 40, and unchanged source/runtime identities.",
                "Controls omit the support query and all support-census snapshots; ON carries only the seven allowlisted support paths.",
                "Run strict current-plan readers with their 43 corruption canaries on every raw row before parity projection.",
                "Validate candidate cold/warm support metadata independently before removing any allowlisted fields.",
                "Stop immediately on source, host, report, metadata, reader, parity, or cleanup failure; preserve the failed row and all diagnostics.",
            ],
            "interpretation": (
                "This is a structural support census only. Candidate counts and graph/IPVT facts are not elapsed time, "
                "numeric work, allocation, or an optimization proof. OFF/ON/OFF timing fields remain observed host/application "
                "diagnostics and are not a speed claim. The unavailable fallback observation keeps exact=false."
            ),
            "limits": {
                "normalMaxJobMillis": 90000,
                "privateMeasurementMaxJobMillis": 300000,
                "maxUnitMillis": 5000,
                "maxJobSteps": 640,
                "oneSeedOnly": True,
            },
        }
        plan_bytes = json_bytes(plan)
        write_new(plan_path, plan)
        binding = {
            "schema": 1,
            "status": "FROZEN_READY_FOR_PRIVATE_MEASUREMENT",
            "purpose": "PRIVATE_LU_SUPPORT_CENSUS_OFF_ON_OFF",
            "seed": SEED,
            "requestedPackageCount": PACKAGE_COUNT,
            "planPath": plan_path.name,
            "planSha256": sha256_bytes(plan_bytes),
            "sourceAuditPath": str(source_audit_path.relative_to(ROOT)).replace("\\", "/"),
            "sourceAuditSha256": sha256_file(source_audit_path),
            "baselineAuditPath": str(base_audit_path.relative_to(ROOT)).replace("\\", "/"),
            "baselineAuditSha256": sha256_file(base_audit_path),
            "source": source_identity,
            "runtime": runtime_identity,
            "hostRunner": {
                "path": str(host_path.relative_to(repo)).replace("\\", "/"),
                "sha256": sha256_file(host_path),
            },
            "tools": {
                "prepare": {"path": Path(__file__).name,
                             "sha256": sha256_file(Path(__file__))},
                "sequence": {"path": "run_lu_support_census_sequence_root.py"},
                "timing": {"path": timing_path.name,
                           "sha256": sha256_file(timing_path)},
                "pair": {"path": pair_path.name,
                         "sha256": sha256_file(pair_path)},
                "metadata": {"path": metadata_path.name,
                             "sha256": sha256_file(metadata_path)},
            },
            "applicationQuery": BASE_APPLICATION_QUERY,
            "supportQuery": SUPPORT_QUERY,
            "sequence": sequence,
            "allowlistedSupportPaths": [
                "/supportCensusRequested", "/supportCensusQuery",
                "/supportCensusForRun", "/coldSupportCensus",
                "/warmSupportCensus", "/cold/supportCensus",
                "/warm/supportCensus",
            ],
            "noSpeedClaim": True,
            "sourceOnlyPreparation": True,
        }
        binding["tools"]["sequence"]["sha256"] = sha256_file(
            ROOT / binding["tools"]["sequence"]["path"])
        binding_bytes = json_bytes(binding)
        write_new(binding_path, binding)
        print(json.dumps({
            "status": "SOURCE_ONLY_BINDING_READY_NOT_RUN",
            "plan": plan_path.name,
            "planSha256": sha256_bytes(plan_bytes),
            "binding": binding_path.name,
            "bindingSha256": sha256_bytes(binding_bytes),
            "sourceAuditSha256": binding["sourceAuditSha256"],
            "sourceCounts": {arm: source_identity[arm]["fileCount"]
                             for arm in ("control", "candidate")},
            "runtimeCounts": {arm: runtime_identity[arm]["fileCount"]
                              for arm in ("control", "candidate")},
        }, sort_keys=True))
        return 0
    except (PreparationError, OSError, KeyError, TypeError, ValueError) as error:
        print("PREPARATION BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
