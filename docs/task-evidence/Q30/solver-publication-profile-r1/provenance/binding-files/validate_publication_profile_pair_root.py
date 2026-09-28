#!/usr/bin/env python3
"""Validate one Q30 publication-profile row against one control row.

This is a small composition layer over the frozen current-plan-4 timing pair
adapter.  It does not launch a host, build, browser, or application.  The
adapter's host, cleanup, report, strict-reader, and corruption checks remain
authoritative.  Only the adapter's profile-rejection predicates are replaced
temporarily for the candidate arm so the untouched profile report can reach
the original strict reader.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


SEED = "10014"
PROFILE_QUERY_KEY = "tsjQ30PublicationProfile"
PROFILE_QUERY = PROFILE_QUERY_KEY + "=true"
ADAPTER_SHA256 = "67430d7c56d872d115a7c38732bf60101cff52e8bf40f7af075213debc8e7e3b"
METADATA_SHA256 = "2504b4e478636bb60b98e195c94a371de89734a563ca6e7bd392670c71771dfd"
SAFE_LABEL = re.compile(r"[A-Za-z0-9._-]{1,96}\Z")
PROFILE_PATHS = (
    "/publicationProfileRequested",
    "/publicationProfileQuery",
    "/coldPublicationProfile",
    "/warmPublicationProfile",
    "/cold/publicationProfile",
    "/warm/publicationProfile",
)


class ValidationError(Exception):
    pass


def need(condition, message):
    if not condition:
        raise ValidationError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, str(path))
    need(spec is not None and spec.loader is not None,
         "could not load " + name)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def verify_module(path, expected, name):
    need(path.is_file(), name + " does not exist: " + str(path))
    actual = sha256(path.read_bytes())
    need(actual.casefold() == expected.casefold(),
         name + " SHA256 differs from the frozen adapter binding")
    return actual


def _safe_label(value, label):
    need(isinstance(value, str) and SAFE_LABEL.fullmatch(value) is not None and
         value not in (".", ".."), label + " contains unsafe path characters")
    return value


def _check_candidate_profile_fields(value, path="report"):
    """Allow only the six declared publication-profile report locations."""
    if isinstance(value, dict):
        for key, item in value.items():
            current = path + "." + str(key)
            if "profile" in str(key).casefold():
                slash_path = current.replace("report", "", 1).replace(".", "/")
                need(slash_path in PROFILE_PATHS,
                     "candidate has profile field outside allowlist: " + current)
            _check_candidate_profile_fields(item, current)
    elif isinstance(value, list):
        for index, item in enumerate(value):
            _check_candidate_profile_fields(item, "{}[{}]".format(path, index))


def _validate_candidate_seed_path(path, seed, label):
    need(isinstance(path, str) and path, label + " path is missing")
    query = parse_qs(urlsplit(path).query, keep_blank_values=True)
    need(query.get("tsjQ30Seed") == [seed],
         label + " path does not carry the exact requested seed")
    profile_keys = [key for key in query if "profile" in key.casefold()]
    need(profile_keys == [PROFILE_QUERY_KEY],
         label + " path contains a non-allowlisted profile query")
    need(query.get(PROFILE_QUERY_KEY) == ["true"],
         label + " path does not carry the exact publication profile query")


def _validate_candidate_host(adapter, scratch, label):
    """Run the frozen host validator with only candidate profile allowances."""
    original_profile_check = adapter.check_no_profile_fields
    original_seed_check = adapter.validate_seed_path
    try:
        adapter.check_no_profile_fields = _check_candidate_profile_fields
        adapter.validate_seed_path = _validate_candidate_seed_path
        return adapter.validate_host_row(scratch, label, SEED)
    finally:
        adapter.check_no_profile_fields = original_profile_check
        adapter.validate_seed_path = original_seed_check


def _validate_candidate_report(report, metadata, label):
    """Validate exact candidate locations and both duplicate snapshots."""
    need(isinstance(report, dict), label + " report must be an object")
    _check_candidate_profile_fields(report)
    need(report.get("publicationProfileRequested") is True,
         label + " is missing publicationProfileRequested=true")
    need(report.get("publicationProfileQuery") == PROFILE_QUERY,
         label + " is missing the exact publication profile query receipt")
    snapshots = {}
    metadata_results = {}
    canaries = {}
    for phase in ("cold", "warm"):
        run = report.get(phase)
        need(isinstance(run, dict), label + " is missing " + phase + " run")
        nested = run.get("publicationProfile")
        root_key = phase + "PublicationProfile"
        duplicate = report.get(root_key)
        need(isinstance(nested, dict),
             label + " is missing " + phase + ".publicationProfile")
        need(isinstance(duplicate, dict),
             label + " is missing " + root_key)
        need(duplicate == nested,
             label + " " + root_key + " differs from " +
             phase + ".publicationProfile")
        snapshots[phase] = nested
        metadata_results[phase] = metadata.validate(
            nested, label + "." + root_key)
        canaries[phase] = metadata.self_test(
            nested, label + "." + root_key)
    return {
        "snapshots": snapshots,
        "metadata": metadata_results,
        "metadataNegativeCanaries": canaries,
        "duplicateSnapshotsExact": True,
    }


def _remove_publication_profile_fields(report, enabled):
    """Remove only declared profile fields from a comparison-only deep copy."""
    projected = copy.deepcopy(report)
    removed = []

    def pop(mapping, key, path):
        if isinstance(mapping, dict) and key in mapping:
            mapping.pop(key)
            removed.append(path + "/" + key)

    if enabled:
        pop(projected, "publicationProfileRequested", "")
        pop(projected, "publicationProfileQuery", "")
        pop(projected, "coldPublicationProfile", "")
        pop(projected, "warmPublicationProfile", "")
        for phase in ("cold", "warm"):
            pop(projected.get(phase), "publicationProfile", "/" + phase)
    return projected, removed


def _load_adapter_and_metadata(adapter_path, metadata_path):
    adapter_digest = verify_module(adapter_path, ADAPTER_SHA256,
                                   "timing pair adapter")
    metadata_digest = verify_module(metadata_path, METADATA_SHA256,
                                    "publication profile metadata validator")
    adapter = load_module(adapter_path, "frozen_current_q30_timing_pair")
    metadata = load_module(metadata_path, "frozen_publication_profile_metadata")
    need(callable(getattr(adapter, "validate_host_row", None)),
         "timing adapter lacks validate_host_row")
    need(callable(getattr(adapter, "strict_reader", None)),
         "timing adapter lacks strict_reader")
    need(callable(getattr(adapter, "remove_declared_timings", None)),
         "timing adapter lacks remove_declared_timings")
    need(callable(getattr(metadata, "validate", None)) and
         callable(getattr(metadata, "self_test", None)),
         "publication metadata validator lacks validate/self_test")
    return adapter, metadata, adapter_digest, metadata_digest


def validate_pair(scratch, repo, candidate_label, control_label, tag,
                  adapter_path=None, metadata_path=None):
    """Validate candidate/control rows and return private pair evidence."""
    scratch = Path(scratch).resolve()
    repo = Path(repo).resolve()
    _safe_label(candidate_label, "candidate label")
    _safe_label(control_label, "control label")
    _safe_label(tag, "validation tag")
    need(candidate_label != control_label, "candidate and control labels must differ")
    need(scratch.is_dir() and repo.is_dir(),
         "scratch or repository root does not exist")
    adapter_path = Path(adapter_path or Path(__file__).with_name(
        "validate_current_q30_timing_pair.py")).resolve()
    metadata_path = Path(metadata_path or Path(__file__).with_name(
        "solver-publication-profile-metadata-validator.py")).resolve()
    adapter, metadata, adapter_digest, metadata_digest = (
        _load_adapter_and_metadata(adapter_path, metadata_path))

    # The control uses the adapter without any modification.  The candidate
    # uses only the exact publication-profile allowlist above.
    control = adapter.validate_host_row(scratch, control_label, SEED)
    candidate = _validate_candidate_host(adapter, scratch, candidate_label)
    candidate_metadata = _validate_candidate_report(
        candidate["report"], metadata, candidate_label)

    rows = [candidate, control]
    strict_results = [
        adapter.strict_reader(scratch, repo, row, SEED, tag)
        for row in rows
    ]
    need(all(item.get("currentPlan4Pass") is True and
             item.get("fullTimingReportPreserved") is True
             for item in strict_results),
         "current-plan-4 strict reader did not preserve both original reports")
    for row, result in zip(rows, strict_results):
        counts = result.get("selfTestCanaryCounts", {})
        need(isinstance(counts, dict) and
             counts.get("corruptionCanaries", 0) > 0,
             row["label"] + " strict reader reported no corruption canaries")
    need(strict_results[0]["readerSha256"] == strict_results[1]["readerSha256"] and
         strict_results[0]["parserSha256"] == strict_results[1]["parserSha256"],
         "strict reader/parser identity changed between arms")
    need(strict_results[0]["selfTestCanaryCounts"] ==
         strict_results[1]["selfTestCanaryCounts"],
         "strict reader self-test canary counts differ between arms")

    reports = [row["report"] for row in rows]
    need(reports[0].get("request") == reports[1].get("request"),
         "candidate/control qualification requests differ")
    projections = []
    removed_profile_paths = []
    removed_timing_paths = []
    for report, enabled in ((reports[0], True), (reports[1], False)):
        profile_projection, profile_paths = _remove_publication_profile_fields(
            report, enabled)
        timing_projection, timing_paths = adapter.remove_declared_timings(
            profile_projection)
        projections.append(timing_projection)
        removed_profile_paths.append(profile_paths)
        removed_timing_paths.append(timing_paths)
    need(removed_profile_paths[0] == list(PROFILE_PATHS),
         "candidate profile removal did not match the declared whitelist")
    need(removed_profile_paths[1] == [],
         "control unexpectedly exposed publication profile fields")
    need(removed_timing_paths[0] == removed_timing_paths[1],
         "candidate/control timing paths differ")
    need(projections[0] == projections[1],
         "candidate/control reports differ outside declared profile/timing fields")

    raw_hashes = []
    for row in rows:
        report_path = scratch / row["reportPath"]
        raw = report_path.read_bytes()
        report_text = raw.decode("utf-8")
        need(sha256(raw) == row["reportSha256"] and
             len(report_text) == row["reportLength"],
             row["label"] + " raw report changed during validation")
        raw_hashes.append({
            "label": row["label"],
            "reportPath": row["reportPath"],
            "sha256": row["reportSha256"],
            "byteLength": len(raw),
        })
    need(sha256(adapter_path.read_bytes()).casefold() ==
         adapter_digest.casefold(),
         "timing adapter changed during validation")
    need(sha256(metadata_path.read_bytes()).casefold() ==
         metadata_digest.casefold(),
         "metadata validator changed during validation")

    comparisons = {
        "coldElapsedMsCandidateMinusControl": (
            reports[0]["cold"]["elapsedMs"] - reports[1]["cold"]["elapsedMs"]),
        "coldRoutingElapsedMsCandidateMinusControl": (
            reports[0]["cold"]["routingElapsedMs"] -
            reports[1]["cold"]["routingElapsedMs"]),
        "coldProofElapsedMsCandidateMinusControl": (
            reports[0]["cold"]["proofElapsedMs"] -
            reports[1]["cold"]["proofElapsedMs"]),
        "warmElapsedMsCandidateMinusControl": (
            reports[0]["warm"]["elapsedMs"] - reports[1]["warm"]["elapsedMs"]),
        "warmRoutingElapsedMsCandidateMinusControl": (
            reports[0]["warm"]["routingElapsedMs"] -
            reports[1]["warm"]["routingElapsedMs"]),
        "warmProofElapsedMsCandidateMinusControl": (
            reports[0]["warm"]["proofElapsedMs"] -
            reports[1]["warm"]["proofElapsedMs"]),
        "hostMonotonicSecondsCandidateMinusControl": (
            rows[0]["hostMonotonicSeconds"] - rows[1]["hostMonotonicSeconds"]),
    }
    return {
        "schema": 1,
        "status": "PASS",
        "purpose": "private Q30 solver publication profile pair evidence",
        "privateExperiment": True,
        "productionAcceptance": False,
        "zeroInstrumentationOverheadClaim": False,
        "seed": SEED,
        "candidateLabel": candidate_label,
        "controlLabel": control_label,
        "timingAdapterSha256": adapter_digest,
        "metadataValidatorSha256": metadata_digest,
        "rawOriginalReports": raw_hashes,
        "strictReader": strict_results,
        "publicationProfileFieldWhitelist": list(PROFILE_PATHS),
        "removedPublicationProfilePaths": removed_profile_paths,
        "removedTimingPaths": removed_timing_paths[0],
        "candidateMetadata": candidate_metadata["metadata"],
        "metadataNegativeCanaries": candidate_metadata["metadataNegativeCanaries"],
        "duplicateSnapshotsExact": candidate_metadata["duplicateSnapshotsExact"],
        "reportExactOutsideDeclaredProfileAndTimingFields": True,
        "comparisons": comparisons,
        "limits": {
            "strictReaderRunsOnOriginalCandidateAndControlReports": True,
            "metadataValidatedBeforeComparisonProjection": True,
            "rawReportsRemainUnchanged": True,
            "sourceRuntimeBindingOwnedExternally": True,
            "notProductionAcceptance": True,
            "notZeroInstrumentationOverheadClaim": True,
        },
    }


def _write_new(path, value):
    path = Path(path)
    need(not path.exists(), "refusing to overwrite validation output: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scratch", type=Path)
    parser.add_argument("repo", type=Path)
    parser.add_argument("candidate_label")
    parser.add_argument("control_label")
    parser.add_argument("--tag", required=True,
                        help="fresh suffix for strict-reader outputs")
    parser.add_argument("--output", type=Path,
                        help="new pair summary; defaults under scratch")
    parser.add_argument("--timing-adapter", type=Path,
                        help="optional frozen timing adapter path")
    parser.add_argument("--metadata-validator", type=Path,
                        help="optional frozen publication metadata validator path")
    args = parser.parse_args()
    output = args.output or (
        args.scratch / (args.candidate_label + "-vs-" + args.control_label +
                        "-publication-profile-" + args.tag + ".json"))
    try:
        summary = validate_pair(
            args.scratch, args.repo, args.candidate_label,
            args.control_label, args.tag, args.timing_adapter,
            args.metadata_validator)
        _write_new(output, summary)
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except Exception as error:
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    sys.exit(main())
