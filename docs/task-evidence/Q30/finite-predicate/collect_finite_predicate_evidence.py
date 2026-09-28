#!/usr/bin/env python3
"""Collect finalized finite-predicate timing evidence without editing inputs.

This standard-library-only collector intentionally requires an explicit
root-authored finalization marker. It will not consume in-flight run folders.
It preserves application/strict-reader proof JSON bytes in deterministic gzip;
only wrapper JSON, logs, and path-bearing inventories are sanitized.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import sys
from pathlib import Path
from typing import Any


PLAN_NAME = "finite-comparison-plan.json"
MARKER_NAME = "finite-comparison-finalization.json"
EXPECTED_BASELINE = "0b29680c7daa62123732b91e3c1157e8ba0d6abd"
EXPECTED_SEQUENCE = [
    ["F", "7"], ["B", "7"], ["B", "13"], ["F", "13"],
    ["F", "64"], ["B", "64"], ["B", "7"], ["F", "7"],
]
PATCH_FILES = [
    "src/com/lushprojects/circuitjs1/client/SolverExecutionBoundary.java",
    "src/com/lushprojects/circuitjs1/client/A07ExecutionContractVectors.java",
]
PAIR_IDS = [(0, 1), (2, 3), (4, 5), (6, 7)]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def ensure_same_or_write(path: Path, data: bytes) -> None:
    """Write an owned artifact, refusing to replace different existing data."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        old = path.read_bytes()
        if old != data:
            raise RuntimeError(f"refusing to overwrite differing evidence: {path.name}")
        return
    path.write_bytes(data)


def deterministic_gzip(data: bytes) -> bytes:
    stream = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=stream, mtime=0) as gz:
        gz.write(data)
    return stream.getvalue()


def _replace_ci(text: str, old: str, new: str) -> str:
    if not old:
        return text
    return re.sub(re.escape(old), lambda _m: new, text, flags=re.IGNORECASE)


def sanitize_text(text: str, roots: list[tuple[str, str]]) -> tuple[str, list[str]]:
    changed: list[str] = []
    # Replace the most specific supplied roots first, in both path styles.
    for raw, replacement in sorted(roots, key=lambda pair: len(pair[0]), reverse=True):
        for old in {raw, raw.replace("/", "\\"), raw.replace("\\", "/")}:
            before = text
            text = _replace_ci(text, old, replacement)
            if text != before and replacement not in changed:
                changed.append(replacement)
    before = text
    text = re.sub(r"(?i)[A-Z]:\\Users\\[^\\/\s\"'<>|]+", "<USER_HOME>", text)
    if text != before and "<USER_HOME>" not in changed:
        changed.append("<USER_HOME>")
    before = text
    text = re.sub(r"(?i)http://127\.0\.0\.1:\d+", "http://127.0.0.1:<ephemeral-port>", text)
    if text != before and "<ephemeral-port>" not in changed:
        changed.append("<ephemeral-port>")
    return text, changed


def _redact_object(value: Any, roots: list[tuple[str, str]]) -> tuple[Any, list[str]]:
    changes: list[str] = []
    if isinstance(value, str):
        result, changed = sanitize_text(value, roots)
        return result, changed
    if isinstance(value, list):
        result = []
        for item in value:
            redacted, changed = _redact_object(item, roots)
            result.append(redacted)
            changes.extend(changed)
        return result, sorted(set(changes))
    if isinstance(value, dict):
        result = {}
        for key, item in value.items():
            redacted, changed = _redact_object(item, roots)
            result[key] = redacted
            changes.extend(changed)
        return result, sorted(set(changes))
    return value, changes


def object_has_personal_absolute_path(value: Any) -> bool:
    if isinstance(value, str):
        return bool(re.search(r"(?i)[A-Z]:[\\/](?:Users|Documents and Settings)[\\/][^\\/]+", value))
    if isinstance(value, list):
        return any(object_has_personal_absolute_path(item) for item in value)
    if isinstance(value, dict):
        return any(object_has_personal_absolute_path(item) for item in value.values())
    return False


class Collector:
    def __init__(self, scratch: Path, candidate: Path, output: Path,
                 repo: Path, a07_readers: list[Path], a07_negative_count: int):
        self.scratch = scratch.resolve()
        self.candidate = candidate.resolve()
        self.output = output.resolve()
        self.repo = repo.resolve()
        self.a07_readers = [p.resolve() for p in a07_readers]
        self.a07_negative_count = a07_negative_count
        self.artifacts: list[dict[str, Any]] = []
        self.roots = [
            (str(self.scratch), "<SCRATCH_ROOT>"),
            (str(self.candidate), "<CANDIDATE_ROOT>"),
            (str(self.repo), "<REPOSITORY_ROOT>"),
        ]

    def _record(self, source_label: str, destination: Path, raw: bytes,
                stored: bytes, transformations: list[str], compression: str) -> None:
        self.artifacts.append({
            "source": source_label,
            "path": destination.relative_to(self.output).as_posix(),
            "sourceBytes": len(raw),
            "sourceSha256": sha256(raw),
            "storedBytes": len(stored),
            "storedSha256": sha256(stored),
            "compression": compression,
            "transformations": transformations,
        })

    def save_exact(self, source: Path, destination: Path, label: str) -> None:
        raw = source.read_bytes()
        ensure_same_or_write(destination, raw)
        self._record(label, destination, raw, raw, [], "none")

    def save_text_sanitized(self, source: Path, destination: Path, label: str) -> None:
        raw = source.read_bytes()
        text = raw.decode("utf-8", errors="replace")
        safe, changes = sanitize_text(text, self.roots)
        stored = safe.encode("utf-8")
        ensure_same_or_write(destination, stored)
        self._record(label, destination, raw, stored, changes, "none")

    def save_json_sanitized(self, source: Path, destination: Path, label: str,
                            compress: bool = False) -> dict[str, Any]:
        raw = source.read_bytes()
        value = json.loads(raw.decode("utf-8"))
        safe, changes = _redact_object(value, self.roots)
        sanitized = json_bytes(safe)
        stored = deterministic_gzip(sanitized) if compress else sanitized
        ensure_same_or_write(destination, stored)
        self._record(label, destination, raw, stored, changes,
                     "gzip" if compress else "none")
        return safe

    def save_proof_json_gzip(self, source: Path, destination: Path,
                             label: str) -> dict[str, Any]:
        raw = source.read_bytes()
        value = json.loads(raw.decode("utf-8"))
        if object_has_personal_absolute_path(value):
            raise RuntimeError(f"proof payload contains a personal absolute path; preserve semantics and review: {label}")
        stored = deterministic_gzip(raw)
        ensure_same_or_write(destination, stored)
        self._record(label, destination, raw, stored, [], "gzip; payload byte-identical")
        return value

    def save_inventory_gzip(self, source: Path, destination: Path,
                            label: str) -> None:
        raw = source.read_bytes()
        value = json.loads(raw.decode("utf-8"))
        if object_has_personal_absolute_path(value):
            self.save_json_sanitized(source, destination, label, compress=True)
            return
        stored = deterministic_gzip(raw)
        ensure_same_or_write(destination, stored)
        self._record(label, destination, raw, stored, [], "gzip; payload byte-identical")

    def verify_patch_bundle(self) -> dict[str, Any]:
        meta = load_json(self.output / "patch-verification.json")
        if meta.get("baselineCommit") != EXPECTED_BASELINE or meta.get("changedFileCount") != 2:
            raise RuntimeError("patch verification manifest does not identify the declared two-file baseline delta")
        for rel in PATCH_FILES:
            entry = next((item for item in meta["changedFiles"] if item["path"] == rel), None)
            if entry is None:
                raise RuntimeError(f"patch verification omits {rel}")
            candidate_bytes = (self.candidate / rel).read_bytes()
            if sha256(candidate_bytes) != entry["candidateSha256"]:
                raise RuntimeError(f"candidate source changed since patch capture: {rel}")
        for key in ("exactPatch", "reviewPatch"):
            entry = meta[key]
            patch_bytes = (self.output / entry["path"]).read_bytes()
            if sha256(patch_bytes) != entry["sha256"]:
                raise RuntimeError(f"archived patch hash mismatch: {entry['path']}")
        return meta

    def verify_integration(self) -> dict[str, Any]:
        path = self.scratch / "finite-integration.json"
        value = load_json(path)
        patch_meta = load_json(self.output / "patch-verification.json")
        expected = {row["path"]: row["candidateSha256"] for row in patch_meta["changedFiles"]}
        declared = value.get("onlyPaths")
        rows = value.get("rows", [])
        if value.get("status") != "PASS" or sorted(declared or []) != sorted(PATCH_FILES):
            raise RuntimeError("source integration receipt does not attest exactly the two declared files")
        by_path = {row.get("path"): row for row in rows}
        if set(by_path) != set(PATCH_FILES):
            raise RuntimeError("source integration receipt rows differ from the two-file patch")
        for rel in PATCH_FILES:
            row = by_path[rel]
            integrated = (self.repo / rel).read_bytes()
            candidate = (self.candidate / rel).read_bytes()
            if sha256(candidate) != expected[rel] or sha256(integrated) != row.get("integratedRawSha256"):
                raise RuntimeError(f"integrated source hash differs from reviewed trial: {rel}")
            normalized = integrated.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
            if sha256(normalized) != row.get("normalizedTrialAndIntegratedSha256") or row.get("preservedTargetLineEndings") is not True:
                raise RuntimeError(f"normalized source equality/line-ending audit failed: {rel}")
        safe = self.save_json_sanitized(path, self.output / "source" / "finite-integration.json", "finite-integration.json")
        return safe

    def _copy_run(self, run_id: str, required: bool, require_pass: bool) -> dict[str, Any]:
        src = self.scratch / run_id
        dst = self.output / "runs" / run_id
        summary: dict[str, Any] = {"runId": run_id, "status": "MISSING"}
        result_path = src / "result.json"
        if not result_path.is_file():
            if required:
                raise RuntimeError(f"finalization marker lists missing terminal run result: {run_id}")
            return summary
        result_raw = result_path.read_bytes()
        result = json.loads(result_raw.decode("utf-8"))
        if not result.get("finishedUtc"):
            raise RuntimeError(f"terminal run lacks finishedUtc: {run_id}")
        self.save_json_sanitized(result_path, dst / "result.json", f"{run_id}/result.json")
        summary.update({
            "status": result.get("outcome", "UNKNOWN"),
            "outcome": result.get("outcome"),
            "resultSha256": sha256(result_raw),
            "operationStartedUtc": result.get("operationStartedUtc"),
            "operationSeconds": (result.get("cases") or [{}])[0].get("operationSeconds"),
            "finishedUtc": result.get("finishedUtc"),
        })
        input_audit = result.get("inputAudit", {})
        summary["inputAuditStable"] = bool(
            input_audit.get("status") == "PASS"
            and input_audit.get("beforeSha256") == input_audit.get("afterSha256")
            and input_audit.get("sourceSha256Before") == input_audit.get("sourceSha256After")
            and input_audit.get("webSha256Before") == input_audit.get("webSha256After")
            and input_audit.get("runnerBeforeSha256") == input_audit.get("runnerAfterSha256")
        )
        cleanup = result.get("cleanup", {})
        summary["cleanupComplete"] = bool(
            cleanup.get("status") == "PASS" and cleanup.get("serverStopped") is True
            and cleanup.get("ownedSurvivors", []) == [] and cleanup.get("errors", []) == []
        )
        summary["hostCleanup"], _cleanup_redactions = _redact_object(cleanup, self.roots)
        if (src / "spec.json").is_file():
            self.save_json_sanitized(src / "spec.json", dst / "spec.json", f"{run_id}/spec.json")
        for name in ("runner-before.json", "runner-after.json"):
            p = src / name
            if p.is_file():
                self.save_json_sanitized(p, dst / name, f"{run_id}/{name}")
        for name in ("input-manifest-before.json", "input-manifest-after.json"):
            p = src / name
            if p.is_file():
                self.save_inventory_gzip(p, dst / (name + ".gz"), f"{run_id}/{name}")
        for name in ("strict-wrapper.json",):
            p = src / name
            if p.is_file():
                wrapper = self.save_proof_json_gzip(p, dst / (name + ".gz"), f"{run_id}/{name}")
            else:
                wrapper = None
        case_files = sorted((src / "cases").glob("*.json")) if (src / "cases").is_dir() else []
        reports = [p for p in case_files if p.name.endswith(".report.json")]
        for p in (p for p in case_files if not p.name.endswith(".report.json")):
            self.save_json_sanitized(p, dst / "cases" / p.name, f"{run_id}/cases/{p.name}")
        for p in reports:
            app_report = self.save_proof_json_gzip(p, dst / "cases" / (p.name + ".gz"), f"{run_id}/cases/{p.name}")
            case_entries = result.get("cases", [])
            case_sha = case_entries[0].get("reportSha256") if case_entries else None
            report_sha = sha256(p.read_bytes())
            summary["applicationReportSha256"] = report_sha
            summary["applicationReportMatchesReceipt"] = bool(case_sha == report_sha)
            summary["applicationOutcome"] = app_report.get("status")
            summary["coldElapsedMs"] = app_report.get("cold", {}).get("elapsedMs")
            summary["coldProofElapsedMs"] = app_report.get("cold", {}).get("proofElapsedMs")
            summary["coldRoutingElapsedMs"] = app_report.get("cold", {}).get("routingElapsedMs")
            summary["coldWork"] = app_report.get("cold", {}).get("totalWork")
            summary["coldProofWork"] = app_report.get("cold", {}).get("hypothesisWork")
            summary["coldProofSamples"] = app_report.get("cold", {}).get("owner", {}).get("diagnosticProof", {}).get("actualSampleCount")
            summary["warmElapsedMs"] = app_report.get("warm", {}).get("elapsedMs")
            summary["warmProofCacheHitDelta"] = app_report.get("warm", {}).get("proofCacheHitDelta")
            summary["warmReuseReceipt"] = app_report.get("warm", {}).get("owner", {}).get("diagnosticProof", {}).get("warmReuseReceipt")
            summary["applicationCleanupComplete"] = app_report.get("cleanupComplete") is True
            summary["diagnosticProofParityWithStrictWrapper"] = None
            if wrapper and wrapper.get("rows"):
                summary["diagnosticProofParityWithStrictWrapper"] = wrapper["rows"][0].get("report") == app_report
        for p in sorted((src / "cases").glob("*.state.txt")) if (src / "cases").is_dir() else []:
            self.save_text_sanitized(p, dst / "cases" / p.name, f"{run_id}/cases/{p.name}")
        if require_pass:
            cases = result.get("cases", [])
            case = cases[0] if cases else {}
            if (result.get("outcome") != "PASS" or not summary.get("inputAuditStable")
                    or not summary.get("cleanupComplete") or not case.get("terminalReached")
                    or case.get("stateMatch") is not True or case.get("timedOut") is not False
                    or case.get("reportObserved") is not True or case.get("reportJsonValid") is not True
                    or case.get("reportMatch") is not True or case.get("outcome") != "PASS"
                    or summary.get("applicationOutcome") != "PASS"
                    or summary.get("applicationReportMatchesReceipt") is not True
                    or summary.get("diagnosticProofParityWithStrictWrapper") is not True
                    or summary.get("applicationCleanupComplete") is not True):
                raise RuntimeError(f"completed run lacks exact PASS/proof/input/cleanup evidence: {run_id}")
        # Small runner and per-run reader outputs are copied from the scratch root.
        reader_path = self.scratch / (run_id + "-reader.txt")
        if require_pass and (not reader_path.is_file() or "PASS" not in reader_path.read_text(encoding="utf-8", errors="replace").upper()):
            raise RuntimeError(f"completed run lacks its strict-reader PASS receipt: {run_id}")
        for suffix, ext in (("-reader.txt", "txt"), ("-summary.json", "json"), (".log", "log")):
            p = self.scratch / (run_id + suffix)
            if p.is_file():
                target = self.output / "comparison" / p.name
                if ext == "json":
                    self.save_json_sanitized(p, target, f"comparison/{p.name}")
                else:
                    self.save_text_sanitized(p, target, f"comparison/{p.name}")
        return summary

    def collect(self, marker_path: Path) -> None:
        # Check the operator-authored finalization record before opening run data.
        if not marker_path.is_file():
            raise RuntimeError(f"no finalization marker yet; leaving active sequence untouched: {marker_path.name}")
        marker = load_json(marker_path)
        plan_path = self.scratch / PLAN_NAME
        plan = load_json(plan_path)
        if plan.get("schema") != "q30-finite-predicate-trial-plan-v1" or plan.get("baseline") != EXPECTED_BASELINE or plan.get("sequence") != EXPECTED_SEQUENCE:
            raise RuntimeError("predeclared comparison plan differs from the declared focused sequence")
        run_ids = [f"finite-{i:02d}-{variant}{seed}" for i, (variant, seed) in enumerate(EXPECTED_SEQUENCE, 1)]
        state = marker.get("state")
        terminal = marker.get("terminalRunIds")
        not_run = marker.get("notRunIds")
        reason = marker.get("reason")
        if state not in ("COMPLETE", "EARLY_STOP") or not isinstance(terminal, list) or not isinstance(not_run, list):
            raise RuntimeError("finalization marker must specify state, terminalRunIds, and notRunIds")
        if terminal != run_ids[:len(terminal)] or not_run != run_ids[len(terminal):] or not reason:
            raise RuntimeError("finalization marker run IDs must be a sequence prefix plus its exact NOT RUN suffix and reason")
        if state == "COMPLETE" and (terminal != run_ids or not_run):
            raise RuntimeError("COMPLETE marker requires all eight planned runs and no NOT RUN rows")
        if state == "EARLY_STOP" and not_run == []:
            raise RuntimeError("EARLY_STOP marker must retain at least one NOT RUN row")
        patch_meta = self.verify_patch_bundle()
        integration_meta = self.verify_integration()
        self.save_exact(plan_path, self.output / "predeclared-comparison-plan.json", PLAN_NAME)
        self.save_json_sanitized(marker_path, self.output / "finalization-marker.json", MARKER_NAME)
        self._collect_gates()
        root_canary_meta = self._collect_root_canaries()
        run_summaries = []
        for run_id in terminal:
            run_summaries.append(self._copy_run(run_id, required=True, require_pass=(state == "COMPLETE")))
        for run_id in not_run:
            run_summaries.append({"runId": run_id, "status": "NOT_RUN", "reason": reason})
        pair_audits = self._collect_pair_artifacts(run_ids, terminal, require_pass=(state == "COMPLETE"))
        manifest = {
            "schema": 1,
            "kind": "focused comparison sequence evidence collection; not an acceptance verdict",
            "finalizationState": state,
            "finalizationReason": reason,
            "finalizedUtc": marker.get("finalizedUtc"),
            "baselineCommit": EXPECTED_BASELINE,
            "candidatePatch": patch_meta,
            "sourceIntegration": integration_meta,
            "rootCanaries": root_canary_meta,
            "decision": marker.get("decision"),
            "q30Status": marker.get("q30Status"),
            "normalPublicationEnabled": marker.get("normalPublicationEnabled"),
            "pairedColdSavingsMs": marker.get("pairedColdSavingsMs"),
            "medianPairedColdSavingsMs": marker.get("medianPairedColdSavingsMs"),
            "seed7ControlRepeatRangeMs": marker.get("seed7ControlRepeatRangeMs"),
            "seed7CandidateRepeatRangeMs": marker.get("seed7CandidateRepeatRangeMs"),
            "plannedSequence": run_ids,
            "runRows": run_summaries,
            "pairArtifacts": pair_audits,
            "a07NegativeCanaryCountClaimedByReaderReceipt": self.a07_negative_count,
            "timingInterpretation": "No aggregate performance acceptance is made by this collector. Compare paired observations against the predeclared plan and measured within-variant variation.",
            "artifacts": self.artifacts,
        }
        ensure_same_or_write(self.output / "collection-manifest.json", json_bytes(manifest))

    def _collect_root_canaries(self) -> dict[str, Any]:
        src = self.scratch / "root-finite-canaries"
        dst = self.output / "gates" / "root-canaries"
        result_path = src / "result.json"
        result_raw = result_path.read_bytes()
        result = json.loads(result_raw.decode("utf-8"))
        if result.get("outcome") != "PASS" or result.get("caseCount") != 2:
            raise RuntimeError("final-source root canary run is not a two-case PASS")
        audit = result.get("inputAudit", {})
        if (audit.get("status") != "PASS" or audit.get("beforeSha256") != audit.get("afterSha256")
                or audit.get("sourceSha256Before") != audit.get("sourceSha256After")
                or audit.get("webSha256Before") != audit.get("webSha256After")
                or audit.get("runnerBeforeSha256") != audit.get("runnerAfterSha256")):
            raise RuntimeError("final-source root canary input audit did not remain stable")
        cleanup = result.get("cleanup", {})
        if (cleanup.get("status") != "PASS" or cleanup.get("serverStopped") is not True
                or cleanup.get("ownedSurvivors", []) or cleanup.get("errors", [])):
            raise RuntimeError("final-source root canary cleanup did not PASS")
        self.save_json_sanitized(result_path, dst / "result.json", "root-finite-canaries/result.json")
        for name in ("input-manifest-before.json", "input-manifest-after.json"):
            path = src / name
            if path.is_file():
                self.save_inventory_gzip(path, dst / (name + ".gz"), f"root-finite-canaries/{name}")
        for name in ("runner-before.json", "runner-after.json", "spec.json"):
            path = src / name
            if path.is_file():
                self.save_json_sanitized(path, dst / name, f"root-finite-canaries/{name}")
        case_summaries = []
        for p in sorted((src / "cases").glob("*.json")):
            if p.name.endswith(".report.json"):
                report = self.save_proof_json_gzip(p, dst / "cases" / (p.name + ".gz"), f"root-finite-canaries/cases/{p.name}")
                digest = sha256(p.read_bytes())
                case = next((c for c in result.get("cases", []) if c.get("reportSha256") == digest), None)
                if case is None or case.get("reportJsonValid") is not True or case.get("reportMatch") is not True or case.get("outcome") != "PASS":
                    raise RuntimeError(f"root canary report does not match a passing wrapper receipt: {p.name}")
                if case.get("observedState") != case.get("case", {}).get("expectedState"):
                    raise RuntimeError(f"root canary observed state differs from its expected state: {p.name}")
                row = {"report": p.name, "sha256": digest, "status": report.get("status"),
                       "phase": report.get("phase"), "caseName": case.get("case", {}).get("name"),
                       "caseOutcome": case.get("outcome"), "observedState": case.get("observedState")}
                if case.get("case", {}).get("name") == "root-q30-disabled":
                    row["noLiveMutation"] = (report.get("normalCatalogEnabled") is False
                                              and report.get("generationStarted") is False
                                              and report.get("snapshotCaptured") is False
                                              and report.get("liveOwnerMutation") is False)
                    if report.get("status") != "BLOCKED" or report.get("phase") != "normal-catalog-disabled" or not row["noLiveMutation"]:
                        raise RuntimeError("normal-player-disabled verifier report did not reject before live mutation")
                elif report.get("status") != "PASS":
                    raise RuntimeError(f"positive root A07 report did not PASS: {p.name}")
                case_summaries.append(row)
            else:
                self.save_json_sanitized(p, dst / "cases" / p.name, f"root-finite-canaries/cases/{p.name}")
        for p in sorted((src / "cases").glob("*.state.txt")):
            self.save_text_sanitized(p, dst / "cases" / p.name, f"root-finite-canaries/cases/{p.name}")
        if (len(case_summaries) != 2 or sum(row["status"] == "PASS" for row in case_summaries) != 1
                or sum(row["status"] == "BLOCKED" and row.get("noLiveMutation") is True for row in case_summaries) != 1
                or any(row["caseOutcome"] != "PASS" for row in case_summaries)):
            raise RuntimeError("root canary output lacks positive A07 and pre-mutation blocked Q30 reports")
        spec = self.scratch / "root-finite-canaries-spec.json"
        if spec.is_file():
            self.save_json_sanitized(spec, dst / "declared-spec.json", spec.name)
        for ext in (".json", ".log"):
            path = self.scratch / ("root-finite-plan4-gwt" + ext)
            if ext == ".json":
                receipt = self.save_json_sanitized(path, self.output / "gates" / "gwt" / path.name, path.name)
                if receipt.get("status") != "PASS" or receipt.get("exitCode") != 0:
                    raise RuntimeError("final-source plan4 GWT build is not PASS")
            else:
                self.save_text_sanitized(path, self.output / "gates" / "gwt" / path.name, path.name)
        reader = self.scratch / "root-finite-a07-reader.txt"
        text = reader.read_text(encoding="utf-8", errors="replace")
        if "PASS" not in text.upper() or "NEGATIVE" not in text.upper():
            raise RuntimeError("final-source A07 strict-reader/negative receipt is not a PASS")
        self.save_text_sanitized(reader, dst / reader.name, reader.name)
        normal_reader = self.scratch / "root-finite-disabled-reader.txt"
        if not normal_reader.is_file():
            raise RuntimeError("final-source normal-disabled strict-reader receipt is missing")
        normal_reader_text = normal_reader.read_text(encoding="utf-8", errors="replace").strip()
        try:
            normal_reader_json = json.loads(normal_reader_text)
        except json.JSONDecodeError as error:
            raise RuntimeError("normal-disabled strict-reader receipt is not JSON") from error
        if (normal_reader_json.get("status") != "PASS_BLOCKED_CANARY"
                or normal_reader_json.get("normalAcceptance") != "BLOCKED"
                or normal_reader_json.get("phase") != "normal-catalog-disabled"
                or normal_reader_json.get("generationStarted") is not False
                or normal_reader_json.get("snapshotCaptured") is not False
                or normal_reader_json.get("liveOwnerMutation") is not False
                or normal_reader_json.get("cleanupRequired") is not False):
            raise RuntimeError("normal-disabled strict-reader receipt does not prove pre-mutation BLOCKED")
        self.save_text_sanitized(normal_reader, dst / normal_reader.name, normal_reader.name)
        normal_reader_record = f"gates/root-canaries/{normal_reader.name}"
        safe_cleanup, _ = _redact_object(cleanup, self.roots)
        return {"outcome": result.get("outcome"), "caseCount": result.get("caseCount"),
                "inputAuditStable": True, "cleanup": safe_cleanup,
                "applicationReports": case_summaries,
                "normalAdmissionProbe": "BLOCKED before generation/snapshot/live-owner mutation; this rejection is not a normal-player acceptance pass.",
                "normalReaderTextReceipt": normal_reader_record,
                "interpretation": "This build includes the unaccepted plan4/power-registration source. Its canary reports are not normal-player acceptance."}

    def _collect_gates(self) -> None:
        gates = self.output / "gates"
        native_base = self.scratch / "native-finite-predicate"
        gwt_base = self.scratch / "finite-predicate-gwt"
        for suffix in (".json", ".log"):
            source = native_base.with_suffix(suffix)
            if suffix == ".json":
                receipt = self.save_json_sanitized(source, gates / "native" / source.name, source.name)
                if receipt.get("status") != "PASS" or receipt.get("exitCode") != 0:
                    raise RuntimeError("focused native gate is not PASS")
            else:
                self.save_text_sanitized(source, gates / "native" / source.name, source.name)
        native_text = self.scratch / "native-finite-predicate-receipt.txt"
        self.save_text_sanitized(native_text, gates / "native" / native_text.name, native_text.name)
        for suffix in (".json", ".log"):
            source = gwt_base.with_suffix(suffix)
            if suffix == ".json":
                receipt = self.save_json_sanitized(source, gates / "gwt" / source.name, source.name)
                if receipt.get("status") != "PASS" or receipt.get("exitCode") != 0:
                    raise RuntimeError("maintained GWT gate is not PASS")
            else:
                self.save_text_sanitized(source, gates / "gwt" / source.name, source.name)
        self._copy_preflight_a07(gates / "a07")

    def _copy_preflight_a07(self, dst: Path) -> None:
        src = self.scratch / "finite-predicate-a07"
        result = src / "result.json"
        result_value = self.save_json_sanitized(result, dst / "result.json", "finite-predicate-a07/result.json")
        if result_value.get("outcome") != "PASS" or not result_value.get("cases"):
            raise RuntimeError("compiled A07 positive preflight did not PASS")
        expected_report_sha = result_value["cases"][0].get("reportSha256")
        a07_report_ok = False
        for name in ("spec.json", "runner-before.json", "runner-after.json"):
            p = src / name
            if p.is_file():
                self.save_json_sanitized(p, dst / name, f"finite-predicate-a07/{name}")
        for name in ("input-manifest-before.json", "input-manifest-after.json"):
            p = src / name
            if p.is_file():
                self.save_inventory_gzip(p, dst / (name + ".gz"), f"finite-predicate-a07/{name}")
        cases = src / "cases"
        for p in sorted(cases.glob("*.json")):
            if p.name.endswith(".report.json"):
                report = self.save_proof_json_gzip(p, dst / "cases" / (p.name + ".gz"), f"finite-predicate-a07/cases/{p.name}")
                a07_report_ok = sha256(p.read_bytes()) == expected_report_sha and report.get("status") == "PASS"
            else:
                self.save_json_sanitized(p, dst / "cases" / p.name, f"finite-predicate-a07/cases/{p.name}")
        if not a07_report_ok:
            raise RuntimeError("compiled A07 full application report does not match its PASS receipt")
        for p in sorted(cases.glob("*.state.txt")):
            self.save_text_sanitized(p, dst / "cases" / p.name, f"finite-predicate-a07/cases/{p.name}")
        # Root captures the strict maintained-reader and three negative canaries separately.
        if not self.a07_readers or self.a07_negative_count != 3:
            raise RuntimeError("supply the strict A07 reader receipt(s) and its three-negative canary count")
        combined_reader_text = []
        for index, p in enumerate(self.a07_readers, 1):
            text = p.read_text(encoding="utf-8", errors="replace")
            if not text.strip() or "PASS" not in text.upper():
                raise RuntimeError(f"A07 strict-reader receipt is not a PASS record: {p.name}")
            combined_reader_text.append(text.upper())
            self.save_text_sanitized(p, dst / "reader-receipts" / f"reader-{index:02d}.txt", f"A07 reader receipt {index}")
        evidence_text = "\n".join(combined_reader_text)
        if "NEGATIVE" not in evidence_text or not re.search(r"\b(3|THREE)\b", evidence_text):
            raise RuntimeError("A07 strict-reader receipts do not state the three negative corruption canaries")

    def _collect_pair_artifacts(self, run_ids: list[str], terminal: list[str], require_pass: bool) -> list[dict[str, Any]]:
        pairs: list[dict[str, Any]] = []
        for left_i, right_i in PAIR_IDS:
            left, right = run_ids[left_i], run_ids[right_i]
            pair = {"runs": [left, right], "status": "NOT_RUN"}
            if left in terminal and right in terminal:
                parity_path = self.scratch / f"{left}-vs-{right}-parity.json"
                if parity_path.is_file():
                    value = self.save_json_sanitized(parity_path, self.output / "comparison" / parity_path.name, parity_path.name)
                    pair["status"] = value.get("status", "RECORDED")
                    pair["artifact"] = f"comparison/{parity_path.name}"
                    pair["negativeCanaries"] = value.get("negativeCanaries")
                    pair["exactRequest"] = value.get("exactRequest")
                    pair["completeParity"] = value.get("completeOwnerPhysicalAndProofParityExcludingTiming")
                    if require_pass and (pair["status"] != "PASS" or pair["negativeCanaries"] != 37
                                         or value.get("strictReaders") != 2 or pair["exactRequest"] is not True
                                         or pair["completeParity"] is not True):
                        raise RuntimeError(f"completed pair did not retain full parity and 37 corruption canaries: {left} / {right}")
                else:
                    pair["status"] = "MISSING_PARITY_RECEIPT"
                    if require_pass:
                        raise RuntimeError(f"completed pair lacks parity receipt: {left} / {right}")
            pairs.append(pair)
        return pairs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scratch-root", type=Path, required=True)
    parser.add_argument("--candidate-root", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--finalization-marker", type=Path)
    parser.add_argument("--a07-reader", action="append", type=Path, default=[],
                        help="root-retained strict-reader/negative-canary text receipt; repeat if separate")
    parser.add_argument("--a07-negative-count", type=int, default=3)
    args = parser.parse_args()
    scratch = args.scratch_root.resolve()
    marker = args.finalization_marker.resolve() if args.finalization_marker else scratch / MARKER_NAME
    try:
        Collector(scratch, args.candidate_root, args.output_root, args.repository_root,
                  args.a07_reader, args.a07_negative_count).collect(marker)
    except (OSError, ValueError, KeyError, RuntimeError, json.JSONDecodeError) as exc:
        print(f"collection not completed: {exc}", file=sys.stderr)
        return 2
    print("evidence collection complete; no performance/normal-acceptance verdict produced")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
