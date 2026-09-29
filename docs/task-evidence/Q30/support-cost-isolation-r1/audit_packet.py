#!/usr/bin/env python3
"""Audit the archived Q30 support-cost measurement packet without rerunning it.

Run from the packet root with ``python audit_packet.py``.  ``--seal`` writes or
refreshes only inventory.json; ``--worktrees DESKTOP Q30`` also checks saved
checkout hashes, status, and HEAD against the archived initial state.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Any

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent
EXPECTED_PROFILE_MUTANTS = 13
EXPECTED_SEQUENCE = [
    ["01-control", "control", "off", "off"],
    ["02-off", "diagnostic", "off", "off"],
    ["03-off-profile", "diagnostic", "off", "on"],
    ["04-off-profile", "diagnostic", "off", "on"],
    ["05-off", "diagnostic", "off", "off"],
    ["06-track", "diagnostic", "track", "off"],
    ["07-track-profile", "diagnostic", "track", "on"],
    ["08-track-profile", "diagnostic", "track", "on"],
    ["09-track", "diagnostic", "track", "off"],
    ["10-off", "diagnostic", "off", "off"],
    ["11-restricted", "diagnostic", "restricted", "off"],
    ["12-restricted", "diagnostic", "restricted", "off"],
    ["13-off", "diagnostic", "off", "off"],
    ["14-control", "control", "off", "off"],
]
CHANGED_R2_R3 = "tests/contracts/Q30SupportCostDiagnosticContractTest.java"
HEX_SHA256 = re.compile(r"^[0-9a-f]{64}$")
PERSONAL_PATH = re.compile(
    rb"(?i)(?:[a-z]:[\\/]{1,4}users[\\/]{1,4}david|/users/david|/home/david)(?:[\\/]|$)"
)


class AuditError(Exception):
    pass


def need(condition: bool, message: str) -> None:
    if not condition:
        raise AuditError(message)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def valid_sha(value: Any, where: str) -> str:
    need(isinstance(value, str) and HEX_SHA256.fullmatch(value) is not None,
         f"{where}: expected lowercase SHA-256")
    return value


def safe_rel(value: Any, where: str) -> str:
    need(isinstance(value, str) and value and "\\" not in value,
         f"{where}: expected a nonempty POSIX relative path")
    path = PurePosixPath(value)
    need(not path.is_absolute() and all(part not in ("", ".", "..") for part in path.parts),
         f"{where}: unsafe relative path")
    return path.as_posix()


def packet_path(relative: str) -> Path:
    rel = safe_rel(relative, "packet path")
    path = ROOT.joinpath(*PurePosixPath(rel).parts)
    need(path.resolve().is_relative_to(ROOT.resolve()), f"{rel}: escapes packet root")
    return path


def read_bytes(relative: str) -> bytes:
    path = packet_path(relative)
    try:
        return path.read_bytes()
    except OSError as error:
        raise AuditError(f"{relative}: cannot read ({error})") from None


def load_json_bytes(raw: bytes, where: str) -> Any:
    def reject_constant(value: str) -> None:
        raise ValueError(f"non-finite JSON constant {value}")
    try:
        return json.loads(raw.decode("utf-8"), parse_constant=reject_constant)
    except (UnicodeError, json.JSONDecodeError, ValueError) as error:
        raise AuditError(f"{where}: invalid UTF-8 JSON ({error})") from None


def load_json(relative: str) -> Any:
    return load_json_bytes(read_bytes(relative), relative)


def load_gzip(relative: str) -> bytes:
    try:
        return gzip.decompress(read_bytes(relative))
    except (OSError, EOFError) as error:
        raise AuditError(f"{relative}: invalid gzip payload ({error})") from None


def load_gzip_json(relative: str) -> Any:
    return load_json_bytes(load_gzip(relative), relative)


def import_module(name: str, relative: str):
    path = packet_path(relative)
    need(path.is_file(), f"missing archived reader: {relative}")
    spec = importlib.util.spec_from_file_location(name, path)
    need(spec is not None and spec.loader is not None,
         f"cannot load archived reader: {relative}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def list_packet_files() -> list[Path]:
    files = []
    for path in ROOT.rglob("*"):
        if path.is_symlink():
            raise AuditError(f"packet contains symlink: {path.relative_to(ROOT).as_posix()}")
        if path.is_file() and path.name not in {"inventory.json", "audit-result.json"}:
            files.append(path)
    return sorted(files, key=lambda item: item.relative_to(ROOT).as_posix())


def inventory_rows() -> list[dict[str, Any]]:
    rows = []
    for path in list_packet_files():
        raw = path.read_bytes()
        rows.append({"path": path.relative_to(ROOT).as_posix(),
                     "bytes": len(raw), "sha256": sha256(raw)})
    return rows


def audit_inventory(seal: bool) -> int:
    rows = inventory_rows()
    target = ROOT / "inventory.json"
    if seal:
        target.write_bytes((json.dumps(rows, indent=2) + "\n").encode("utf-8"))
    else:
        stored = load_json("inventory.json")
        need(stored == rows, "inventory.json differs from current packet contents")
    return len(rows)


def audit_privacy() -> int:
    checked = 0
    for path in ROOT.rglob("*"):
        if path.is_symlink() or not path.is_file():
            continue
        relative = path.relative_to(ROOT).as_posix()
        raw = path.read_bytes()
        blobs = [raw]
        if path.suffix.casefold() == ".gz":
            try:
                blobs.append(gzip.decompress(raw))
            except (OSError, EOFError) as error:
                raise AuditError(f"{relative}: invalid gzip while scanning privacy ({error})") from None
        if any(PERSONAL_PATH.search(blob) for blob in blobs):
            raise AuditError(f"{relative}: contains a personal absolute path")
        checked += 1
    return checked


def audit_provenance() -> int:
    records = load_json("provenance.json")
    need(isinstance(records, list) and records, "provenance.json: expected nonempty array")
    seen: set[str] = set()
    for index, record in enumerate(records):
        where = f"provenance[{index}]"
        need(isinstance(record, dict), f"{where}: expected object")
        stored = safe_rel(record.get("stored"), f"{where}.stored")
        need(stored not in seen, f"{where}: duplicate stored path {stored}")
        seen.add(stored)
        raw = read_bytes(stored)
        if record.get("gzip") is True:
            try:
                payload = gzip.decompress(raw)
            except (OSError, EOFError) as error:
                raise AuditError(f"{where}: invalid gzip for {stored} ({error})") from None
        else:
            need(record.get("gzip") is False, f"{where}.gzip: expected boolean")
            payload = raw
        source_hash = valid_sha(record.get("sourceSha256"), f"{where}.sourceSha256")
        payload_hash = valid_sha(record.get("payloadSha256"), f"{where}.payloadSha256")
        need(sha256(payload) == payload_hash, f"{stored}: provenance payload hash mismatch")
        sanitized = record.get("pathSanitized")
        exact = record.get("exactPayload")
        need(type(sanitized) is bool and type(exact) is bool,
             f"{where}: sanitization flags must be booleans")
        normalized = record.get("textNormalization")
        need(normalized is None or (isinstance(normalized, str) and bool(normalized)),
             f"{where}: textNormalization must describe an intentional format-only edit")
        if normalized is not None:
            need(stored == "procedures/validate_profile.py" and not sanitized and not record["gzip"],
                 f"{where}: unexpected format-normalized source")
            original_suffix = bytes.fromhex(record.get("originalTrailingNewlinesHex", ""))
            need(re.fullmatch(rb"(?:\r?\n)+", original_suffix) is not None,
                 f"{where}: invalid original trailing-newline suffix")
            original = payload.rstrip(b"\r\n") + original_suffix
            need(sha256(original) == source_hash,
                 f"{where}: normalization changed more than trailing newline bytes")
        need((sanitized or normalized is not None) == (source_hash != payload_hash),
             f"{where}: sanitization/normalization flags disagree with source/payload hashes")
        need(exact == (source_hash == payload_hash),
             f"{where}: exactPayload flag disagrees with source/payload hashes")
    return len(records)


def entry_map(entries: Any, where: str) -> dict[str, tuple[int, str]]:
    need(isinstance(entries, list), f"{where}: expected input-entry array")
    result: dict[str, tuple[int, str]] = {}
    for index, entry in enumerate(entries):
        label = f"{where}[{index}]"
        need(isinstance(entry, dict), f"{label}: expected object")
        path = safe_rel(entry.get("path"), f"{label}.path")
        size = entry.get("bytes")
        need(type(size) is int and size >= 0, f"{label}.bytes: expected nonnegative integer")
        digest = valid_sha(entry.get("sha256"), f"{label}.sha256")
        need(path not in result, f"{where}: duplicate input path {path}")
        result[path] = (size, digest)
    return result


def audit_source_overlays() -> tuple[int, dict[str, tuple[int, str]], dict[str, tuple[int, str]]]:
    baseline = load_gzip_json("source/baseline-inputs.json.gz")
    need(isinstance(baseline, dict), "baseline input audit must be an object")
    arms = baseline.get("armInputs")
    need(isinstance(arms, dict) and "control" in arms,
         "baseline input audit omitted armInputs.control")
    control = entry_map(arms["control"], "baseline armInputs.control")
    need(len(control) == 1340, "baseline control build-input count must be 1340")
    prototype = entry_map(arms.get("candidate"), "baseline armInputs.candidate")
    need(len(prototype) == 1343, "reconstructed prototype input count must be 1343")

    revisions: dict[str, dict[str, tuple[int, str]]] = {}
    overlay_count = 0
    for revision in ("diagnostic-r1", "diagnostic-r2", "diagnostic-r3"):
        relative = f"source/{revision}-inputs.json.gz"
        audit = load_gzip_json(relative)
        need(isinstance(audit, dict), f"{relative}: expected object")
        inputs = entry_map(audit.get("inputs"), f"{revision}.inputs")
        need(len(inputs) == 1345 and audit.get("diagnosticInputs") == 1345,
             f"{revision}: expected 1345 frozen diagnostic build inputs")
        need(audit.get("controlInputs") == 1340,
             f"{revision}: expected 1340 control build inputs")
        revisions[revision] = inputs

        changed = audit.get("changedFromPrototype")
        need(isinstance(changed, list) and changed,
             f"{revision}: missing changedFromPrototype overlay list")
        changed_paths = [safe_rel(path, f"{revision}.changedFromPrototype") for path in changed]
        need(len(changed_paths) == len(set(changed_paths)),
             f"{revision}: duplicate changedFromPrototype path")
        actual_changed = {path for path in set(prototype) | set(inputs)
                          if prototype.get(path) != inputs.get(path)}
        need(actual_changed == set(changed_paths),
             f"{revision}: unrecorded or spurious source difference from prototype")
        expected_names = {path.replace("/", "__") + ".gz" for path in changed_paths}
        folder = packet_path(f"source/{revision}")
        actual_names = {path.name for path in folder.glob("*.gz") if path.is_file()}
        need(actual_names == expected_names,
             f"{revision}: archived overlay set differs from changedFromPrototype")
        for rel in changed_paths:
            need(rel in inputs, f"{revision}: overlay path missing from frozen inputs: {rel}")
            overlay = f"source/{revision}/{rel.replace('/', '__')}.gz"
            payload = load_gzip(overlay)
            expected_size, expected_digest = inputs[rel]
            need(len(payload) == expected_size and sha256(payload) == expected_digest,
                 f"{overlay}: bytes/hash differ from {revision} build-input audit")
            overlay_count += 1

    first = revisions["diagnostic-r2"]
    second = revisions["diagnostic-r3"]
    changed_paths = {path for path in set(first) | set(second) if first.get(path) != second.get(path)}
    need(changed_paths == {CHANGED_R2_R3},
         "diagnostic-r2 to diagnostic-r3 build inputs must change only the diagnostic contract test")
    return overlay_count, control, revisions["diagnostic-r3"]


def runtime_map(manifest: Any, where: str) -> tuple[dict[str, tuple[int, str]], dict[str, tuple[int, str]]]:
    need(isinstance(manifest, dict), f"{where}: expected runtime input manifest")
    need(manifest.get("roots") == ["src", "war"], f"{where}: roots must be src and war")
    files = manifest.get("files")
    need(isinstance(files, list), f"{where}.files: expected array")
    all_files: dict[str, tuple[int, str]] = {}
    source: dict[str, tuple[int, str]] = {}
    web: dict[str, tuple[int, str]] = {}
    for index, item in enumerate(files):
        label = f"{where}.files[{index}]"
        need(isinstance(item, dict), f"{label}: expected object")
        path = safe_rel(item.get("path"), f"{label}.path")
        prefix = "src/" if path.startswith("src/") else "war/" if path.startswith("war/") else ""
        need(bool(prefix), f"{label}: path is outside src/ and war/")
        size = item.get("size")
        need(type(size) is int and size >= 0, f"{label}.size: expected nonnegative integer")
        digest = valid_sha(item.get("sha256"), f"{label}.sha256")
        need(path not in all_files, f"{where}: duplicate runtime path {path}")
        all_files[path] = (size, digest)
        (source if prefix == "src/" else web)[path] = (size, digest)
    need(manifest.get("fileCount") == len(all_files), f"{where}: fileCount mismatch")
    need(manifest.get("sourceFileCount") == len(source), f"{where}: sourceFileCount mismatch")
    need(manifest.get("webFileCount") == len(web), f"{where}: webFileCount mismatch")
    for key in ("sha256", "sourceSha256", "webSha256"):
        valid_sha(manifest.get(key), f"{where}.{key}")
    return all_files, source, web


def compare_build_sources(runtime: dict[str, tuple[int, str]],
                          build: dict[str, tuple[int, str]], where: str) -> None:
    build_src = {path: data for path, data in build.items() if path.startswith("src/")}
    need(runtime == build_src,
         f"{where}: runtime src/ mapping differs from frozen build-input mapping")


def audit_worktrees(arguments: list[str] | None) -> dict[str, Any] | None:
    if arguments is None:
        return None
    initial = load_gzip_json("audits/initial-worktrees.json.gz")
    need(isinstance(initial, dict), "initial worktree state must be an object")
    result: dict[str, Any] = {}
    for name, folder in zip(("desktop", "q30"), arguments):
        state = initial.get(name)
        need(isinstance(state, dict), f"initial state omitted {name}")
        root = Path(folder).resolve()
        need(root.is_dir(), f"{name} worktree path is not a directory")
        files = state.get("files")
        need(isinstance(files, dict), f"initial {name} file-hash map is missing")
        for relative, expected in files.items():
            rel = safe_rel(relative.replace("\\", "/"), f"initial {name} file path")
            path = root.joinpath(*PurePosixPath(rel).parts)
            need(path.is_file() and sha256(path.read_bytes()) == expected,
                 f"{name}: initial file hash changed: {rel}")
        head = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
        need(head == state.get("head"), f"{name}: HEAD differs from initial snapshot")
        status = subprocess.check_output(["git", "-C", str(root), "status", "--short"], text=True)
        current = set(status.splitlines())
        allowed = set()
        if name == "q30":
            allowed = {
                " M docs/CODEX_TASK_REPORT.md",
                "?? docs/task-evidence/Q30/support-cost-isolation-r1/",
                "?? docs/task-evidence/Q30/support-cost-isolation-r1",
            }
        current -= allowed
        expected_status = set(str(state.get("status", "")).splitlines())
        need(current == expected_status, f"{name}: worktree status differs beyond report/new packet changes")
        result[name] = {"head": head, "initialFilesHashed": len(files), "status": "PASS"}
    return result


def audit_runs(build_control: dict[str, tuple[int, str]],
               build_diagnostic: dict[str, tuple[int, str]]) -> dict[str, Any]:
    plan = load_json("plan.json")
    need(isinstance(plan, dict) and plan.get("sequence") == EXPECTED_SEQUENCE,
         "plan.json does not contain the frozen 14-row sequence")
    parity = load_json("parity.json")
    rows = parity.get("rows") if isinstance(parity, dict) else None
    need(isinstance(rows, list) and len(rows) == len(EXPECTED_SEQUENCE),
         "parity.json must contain all 14 rows")

    coordinator = import_module("archived_check_coordinator", "procedures/readers/check_coordinator.py")
    timing = import_module("archived_q30_timing_pair", "procedures/readers/validate_current_q30_timing_pair.py")
    profile_reader = import_module("independent_support_cost_profile", "procedures/validate_profile.py")

    reference_projection = None
    arm_runtime: dict[str, dict[str, tuple[int, str]]] = {}
    arm_web: dict[str, dict[str, tuple[int, str]]] = {}
    for index, expected in enumerate(EXPECTED_SEQUENCE):
        label, arm, mode, requested_text = expected
        requested = requested_text == "on"
        folder = f"runs/{label}"
        parity_row = rows[index]
        need(isinstance(parity_row, dict) and parity_row.get("label") == label,
             f"parity row {index} does not match frozen label {label}")
        raw_bytes = load_gzip(f"{folder}/raw-report.json.gz")
        raw_digest = sha256(raw_bytes)
        need(raw_digest == valid_sha(parity_row.get("rawReportSha256"), f"{label}.rawReportSha256"),
             f"{label}: raw report hash differs from parity row")
        report = load_json_bytes(raw_bytes, f"{label} raw report")
        need(isinstance(report, dict), f"{label}: report must be object")

        validation = load_json(f"{folder}/validation.json")
        need(isinstance(validation, dict) and validation.get("status") == "PASS" and
             validation.get("label") == label, f"{label}: validation artifact is not PASS")
        need(validation.get("rawReportSha256") == raw_digest and
             parity_row.get("rawReportSha256") == validation.get("rawReportSha256"),
             f"{label}: raw report hash references disagree")
        need(parity_row.get("projectionSha256") == validation.get("projectionSha256"),
             f"{label}: projection hash references disagree")

        expected_metadata = []
        if arm == "diagnostic":
            expected_metadata = ["supportCostRequested", "supportCostMode"]
            if requested:
                expected_metadata.extend(["cold.supportCost", "warm.supportCost"])
            profile_result = profile_reader.validate_report(report, f"{label} raw report")
            need(profile_result.get("status") == "PASS" and
                 profile_result.get("profileRequested") is requested and
                 profile_result.get("mode") == mode,
                 f"{label}: independent support-cost profile result differs from plan")
            need(report.get("supportCostRequested") is requested and
                 report.get("supportCostMode") == mode,
                 f"{label}: raw profile metadata differs from plan")
            metadata_reader_raw = read_bytes(f"{folder}/metadata-reader.jsonl")
            metadata_lines = [line for line in metadata_reader_raw.splitlines() if line.strip()]
            need(len(metadata_lines) == 2,
                 f"{label}: metadata-reader.jsonl must contain self-test and report records")
            self_test_record = load_json_bytes(metadata_lines[0], f"{label} profile-reader self-test")
            report_record = load_json_bytes(metadata_lines[1], f"{label} profile-reader report")
            need(isinstance(self_test_record, dict) and self_test_record.get("status") == "PASS",
                 f"{label}: profile-reader negative self-test is not PASS")
            mutations = self_test_record.get("mutations")
            need(isinstance(mutations, list) and len(mutations) == EXPECTED_PROFILE_MUTANTS and
                 len(set(mutations)) == EXPECTED_PROFILE_MUTANTS and
                 self_test_record.get("negativeMutationsRejected") == EXPECTED_PROFILE_MUTANTS,
                 f"{label}: expected all 13 distinct negative profile mutations to be rejected")
            need(isinstance(report_record, dict) and report_record.get("status") == "PASS",
                 f"{label}: archived profile-reader report result is not PASS")
            report_rows = report_record.get("reports")
            need(isinstance(report_rows, list) and len(report_rows) == 1,
                 f"{label}: archived profile-reader report must contain one report row")
            report_row = report_rows[0]
            need(report_row.get("status") == "PASS" and
                 report_row.get("profileRequested") is requested and
                 report_row.get("mode") == mode,
                 f"{label}: archived profile-reader report row differs from plan")
            for phase in ("cold", "warm"):
                phase_row = report_row.get(phase)
                need(isinstance(phase_row, dict),
                     f"{label}: profile-reader {phase} result is missing")
                if requested:
                    need(phase_row.get("profileValid") is True and phase_row.get("mode") == mode,
                         f"{label}: profile-reader {phase} did not positively validate the requested mode")
                else:
                    need(phase_row.get("profileValid") is False and
                         phase_row.get("profileStatus") == "NOT_REQUESTED",
                         f"{label}: profile-reader {phase} did not record explicit OFF absence")
            metadata_free = copy.deepcopy(report)
            metadata_free.pop("supportCostRequested", None)
            metadata_free.pop("supportCostMode", None)
            if requested:
                for phase in ("cold", "warm"):
                    need(isinstance(metadata_free.get(phase), dict) and
                         metadata_free[phase].get("supportCost") is not None,
                         f"{label}: requested {phase} supportCost is missing")
                    metadata_free[phase].pop("supportCost")
        else:
            need("supportCostRequested" not in report and "supportCostMode" not in report,
                 f"{label}: control report unexpectedly has profile metadata")
            for phase in ("cold", "warm"):
                run = report.get(phase)
                need(isinstance(run, dict) and run.get("supportCost") is None,
                     f"{label}: control report has non-null supportCost")
            metadata_free = copy.deepcopy(report)

        need(validation.get("removedMetadata") == expected_metadata and
             parity_row.get("removedMetadata") == expected_metadata,
             f"{label}: declared profile metadata removals differ from OFF/ON plan")

        host = load_json(f"{folder}/host-result.json")
        need(isinstance(host, dict) and host.get("outcome") == "PASS",
             f"{label}: host outcome is not PASS")
        need(host.get("errors") == [], f"{label}: host recorded errors")
        cases = host.get("cases")
        need(isinstance(cases, list) and len(cases) == 1 and
             cases[0].get("outcome") == "PASS" and cases[0].get("reportSha256") == raw_digest,
             f"{label}: host case does not bind the raw report hash")
        audit = host.get("inputAudit")
        cleanup = host.get("cleanup")
        need(isinstance(audit, dict) and audit.get("status") == "PASS",
             f"{label}: inputAudit is not PASS")
        need(isinstance(cleanup, dict) and cleanup.get("status") == "PASS" and
             cleanup.get("errors") == [] and cleanup.get("ownedSurvivors") == [] and
             cleanup.get("serverStopped") is True,
             f"{label}: cleanup evidence is incomplete or not PASS")

        before = load_gzip_json(f"{folder}/host/input-manifest-before.json.gz")
        after = load_gzip_json(f"{folder}/host/input-manifest-after.json.gz")
        before_all, before_src, before_web = runtime_map(before, f"{label} before manifest")
        after_all, after_src, after_web = runtime_map(after, f"{label} after manifest")
        need(set(before) == set(after), f"{label}: before/after manifest fields differ")
        before_content = dict(before)
        after_content = dict(after)
        before_content.pop("capturedUtc", None)
        after_content.pop("capturedUtc", None)
        need(before_content == after_content and before_all == after_all,
             f"{label}: runtime inputs changed during measurement")
        expected_arm_build = build_control if arm == "control" else build_diagnostic
        compare_build_sources(before_src, expected_arm_build, f"{label} before")
        compare_build_sources(after_src, expected_arm_build, f"{label} after")

        expected_audit_pairs = {
            "beforeSha256": before["sha256"], "afterSha256": after["sha256"],
            "sourceSha256Before": before["sourceSha256"], "sourceSha256After": after["sourceSha256"],
            "webSha256Before": before["webSha256"], "webSha256After": after["webSha256"],
            "fileCountBefore": before["fileCount"], "fileCountAfter": after["fileCount"],
        }
        for key, value in expected_audit_pairs.items():
            need(audit.get(key) == value, f"{label}: inputAudit.{key} differs from runtime manifest")
        for key in ("runnerBeforeSha256", "runnerAfterSha256"):
            valid_sha(audit.get(key), f"{label}.inputAudit.{key}")
        need(audit["runnerBeforeSha256"] == audit["runnerAfterSha256"],
             f"{label}: runner input changed during measurement")

        if arm in arm_runtime:
            need(arm_runtime[arm] == before_all and arm_web[arm] == before_web,
                 f"{label}: runtime source/web mapping differs from another {arm} run")
        else:
            arm_runtime[arm] = before_all
            arm_web[arm] = before_web

        wrapper = {"rows": [{
            "seed": "10014",
            "previewSourceDigest": before["sourceSha256"],
            "previewWebDigest": before["webSha256"],
            "report": metadata_free,
        }]}
        coordinator.check_document(wrapper, ["10014"], self_test=False)
        projection, removed_timings = timing.remove_declared_timings(copy.deepcopy(metadata_free))
        need(removed_timings == validation.get("removedTimings"),
             f"{label}: archived timing removals differ from validation record")
        encoded_projection = (json.dumps(projection, sort_keys=True, allow_nan=False) + "\n").encode("utf-8")
        projection_digest = sha256(encoded_projection)
        need(projection_digest == validation.get("projectionSha256") and
             projection_digest == parity_row.get("projectionSha256"),
             f"{label}: canonical UTF-8/LF projection hash mismatch")
        stored_projection = load_gzip(f"{folder}/proof-projection.json.gz")
        need(stored_projection == encoded_projection,
             f"{label}: archived proof projection bytes differ from canonical UTF-8/LF projection")
        need(reference_projection is None or projection == reference_projection,
             f"{label}: complete non-timing proof differs from control projection")
        if reference_projection is None:
            reference_projection = projection

    need(reference_projection is not None, "no report projections audited")
    return {"reports": len(EXPECTED_SEQUENCE), "strictCoordinatorReader": "PASS",
            "fullNonTimingParity": "PASS", "runtimeArms": sorted(arm_runtime)}


def run(seal: bool, worktrees: list[str] | None) -> dict[str, Any]:
    inventory_count = audit_inventory(seal)
    provenance_count = audit_provenance()
    overlay_count, control, diagnostic = audit_source_overlays()
    run_result = audit_runs(control, diagnostic)
    preservation = audit_worktrees(worktrees)
    privacy_count = audit_privacy()
    return {
        "status": "PASS",
        "inventoryFiles": inventory_count,
        "provenanceRecords": provenance_count,
        "sourceOverlayFiles": overlay_count,
        "rawReportAudit": run_result,
        "personalPathScanFiles": privacy_count,
        "worktreePreservation": preservation,
        "scope": "Stored measurement evidence, frozen source/runtime identities, and proof parity; no timing extrapolation.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seal", action="store_true",
                        help="write inventory.json, then audit the packet")
    parser.add_argument("--worktrees", nargs=2, metavar=("DESKTOP", "Q30"),
                        help="also verify the archived initial worktree state")
    args = parser.parse_args()
    try:
        result = run(args.seal, args.worktrees)
    except Exception as error:
        message = str(error)
        message = re.sub(r"(?i)[a-z]:[\\/]+Users[\\/]+[^\\/]+", "<USERPROFILE>", message)
        result = {"status": "FAIL", "error": message}
        try:
            (ROOT / "audit-result.json").write_bytes(
                (json.dumps(result, indent=2) + "\n").encode("utf-8"))
        except OSError:
            pass
        print(json.dumps(result, indent=2))
        return 1
    (ROOT / "audit-result.json").write_bytes(
        (json.dumps(result, indent=2) + "\n").encode("utf-8"))
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
