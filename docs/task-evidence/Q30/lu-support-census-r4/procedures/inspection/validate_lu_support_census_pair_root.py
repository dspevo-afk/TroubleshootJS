#!/usr/bin/env python3
"""Validate one LU support-census ON/OFF pair without rerunning it.

The maintained current-plan reader and its 43 corruption canaries run on both
raw reports first.  The independent LU metadata reader then validates the
candidate's cold/warm support snapshots.  Only after those checks does this
module remove the seven explicit support-census paths and the existing declared
application timing paths for exact non-timing parity.
"""

import argparse
import copy
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path


SEED = "10014"
PACKAGE_MARKER = "packages=40"
ROOT_SEED_MARKER = "root-seed=10014"
SUPPORT_PATHS = (
    "/supportCensusRequested", "/supportCensusQuery",
    "/supportCensusForRun", "/coldSupportCensus",
    "/warmSupportCensus", "/cold/supportCensus",
    "/warm/supportCensus",
)
SUPPORT_TOP_PATHS = {
    "/supportCensusRequested", "/supportCensusQuery",
    "/supportCensusForRun", "/coldSupportCensus", "/warmSupportCensus",
}
SUPPORT_RUN_PATHS = {"/cold/supportCensus", "/warm/supportCensus"}
ERROR_ARRAYS = (
    "pageErrors", "httpErrors", "consoleErrors", "attributeReadErrors",
    "listenerCleanupErrors",
)
SAFE_LABEL = re.compile(r"[A-Za-z0-9._-]{1,96}\Z")


class ValidationError(ValueError):
    pass


def need(condition, message):
    if not condition:
        raise ValidationError(message)


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    return sha256_bytes(path.read_bytes())


def read_json(path, label):
    need(path.is_file(), label + " is missing: " + str(path))
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValidationError("could not read " + label + ": " + str(error))


def load_module(path, name):
    need(path.is_file(), name + " is missing: " + str(path))
    spec = importlib.util.spec_from_file_location(name, str(path))
    need(spec is not None and spec.loader is not None,
         "could not load " + name)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def safe_label(value, label):
    need(isinstance(value, str) and SAFE_LABEL.fullmatch(value) is not None and
         value not in (".", ".."), label + " is not a safe row label")
    return value


def write_new(path, value):
    need(not path.exists(), "refusing to overwrite " + path.name)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8", newline="\n")


def raw_support_paths(value, path=""):
    found = []
    if isinstance(value, dict):
        for key, item in value.items():
            current = path + "/" + str(key)
            if "supportcensus" in str(key).casefold():
                found.append(current)
            found.extend(raw_support_paths(item, current))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            found.extend(raw_support_paths(item, path + "[{}]".format(index)))
    return found


def validate_report_mode(report, enabled, label):
    need(isinstance(report, dict), label + " report must be an object")
    need(report.get("status") == "PASS", label + " report status is not PASS")
    need(report.get("seed") == SEED and report.get("requestedSeed") == SEED,
         label + " report seed is not the exact requested seed")
    request = report.get("request")
    need(isinstance(request, str) and PACKAGE_MARKER in request and
         ROOT_SEED_MARKER in request,
         label + " request does not prove seed 10014 and package count 40")
    need(report.get("normalAdmission") is False and
         report.get("measurementOnly") is True,
         label + " is not a measurement-only Q30 report")
    for phase in ("cold", "warm"):
        need(isinstance(report.get(phase), dict),
             label + " report is missing " + phase + " run")
    present = set(raw_support_paths(report))
    unexpected = sorted(path for path in present if path not in SUPPORT_PATHS)
    need(not unexpected,
         label + " has support-census/instrumentation fields outside the exact seven paths: " +
         ", ".join(unexpected))
    if not enabled:
        need(present == set(), label + " baseline control unexpectedly exposes census fields")
        return {"enabled": False, "supportPaths": []}
    need(report.get("supportCensusRequested") is True,
         label + " ON must declare supportCensusRequested=true")
    need(present == set(SUPPORT_PATHS), label + " ON must expose exactly seven census paths")
    need(report.get("supportCensusQuery") == "tsjQ30SupportCensus=true",
         label + " support query receipt is missing or changed")
    for path in ("coldSupportCensus", "warmSupportCensus",
                 "supportCensusForRun"):
        need(isinstance(report.get(path), dict),
             label + " is missing top-level " + path)
    need(isinstance(report["cold"].get("supportCensus"), dict) and
         isinstance(report["warm"].get("supportCensus"), dict),
         label + " is missing nested cold/warm supportCensus")
    need(report["coldSupportCensus"] == report["cold"]["supportCensus"],
         label + " cold support snapshot is not an exact duplicate")
    need(report["warmSupportCensus"] == report["warm"]["supportCensus"],
         label + " warm support snapshot is not an exact duplicate")
    need(report["supportCensusForRun"] == report["warmSupportCensus"],
         label + " supportCensusForRun is not the final warm snapshot")
    return {"enabled": True, "supportPaths": sorted(present),
            "coldSupport": report["coldSupportCensus"],
            "warmSupport": report["warmSupportCensus"]}


def strip_support_paths(report):
    projected = copy.deepcopy(report)
    removed = []

    def pop(mapping, key, path):
        if isinstance(mapping, dict) and key in mapping:
            mapping.pop(key)
            removed.append(path + "/" + key)

    pop(projected, "supportCensusRequested", "")
    pop(projected, "supportCensusQuery", "")
    pop(projected, "supportCensusForRun", "")
    pop(projected, "coldSupportCensus", "")
    pop(projected, "warmSupportCensus", "")
    pop(projected.get("cold"), "supportCensus", "/cold")
    pop(projected.get("warm"), "supportCensus", "/warm")
    return projected, removed


def validate_case_receipt(scratch, label, timing, support_enabled):
    spec = read_json(scratch / (label + "-spec.json"), label + " browser spec")
    cases = spec.get("cases")
    need(isinstance(cases, list) and len(cases) == 1,
         label + " browser spec must contain one case")
    path = cases[0].get("path")
    timing.validate_case_path(path, SEED, support_enabled,
                              label + " browser spec path")


def validate_strict_row(scratch, repo, adapter, label, timing,
                        support_enabled, tag):
    row = adapter.validate_host_row(scratch, label, SEED)
    validate_case_receipt(scratch, label, timing, support_enabled)
    counts = row.get("host", {}).get("selfTestCanaryCounts", {})
    # validate_host_row returns the host summary under row, while strict_reader
    # below returns the actual reader canary counts.  Keep the host summary
    # identity check separate from the reader's 43 negative mutations.
    strict = adapter.strict_reader(scratch, repo, row, SEED, tag)
    strict_counts = strict.get("selfTestCanaryCounts", {})
    need(strict.get("currentPlan4Pass") is True and
         strict.get("fullTimingReportPreserved") is True,
         label + " strict reader did not preserve the raw report")
    need(strict_counts.get("corruptionCanaries") == 43,
         label + " strict reader did not report exactly 43 corruption canaries")
    raw_path = (scratch / row["reportPath"]).resolve()
    raw = raw_path.read_bytes()
    need(sha256_bytes(raw) == row["reportSha256"],
         label + " raw report changed after strict-reader validation")
    return row, strict


def validate_pair(scratch, repo, on_label, off_label, tag,
                  binding_path=None):
    safe_label(on_label, "on label")
    safe_label(off_label, "off label")
    need(on_label != off_label, "on and off labels must differ")
    timing_path = scratch.parent / "run_lu_support_census_timing.py"
    if not timing_path.is_file():
        timing_path = Path(__file__).with_name("run_lu_support_census_timing.py")
    adapter_path = Path(__file__).with_name("validate_current_q30_timing_pair.py")
    metadata_path = Path(__file__).with_name("lu-support-census-metadata-validator.py")
    timing = load_module(timing_path, "support timing adapter")
    adapter = load_module(adapter_path, "maintained timing-pair adapter")
    metadata = load_module(metadata_path, "support metadata validator")
    need(callable(getattr(adapter, "validate_host_row", None)) and
         callable(getattr(adapter, "strict_reader", None)) and
         callable(getattr(adapter, "remove_declared_timings", None)),
         "maintained timing-pair adapter API is incomplete")
    need(callable(getattr(metadata, "validate_report", None)) and
         callable(getattr(metadata, "selftest", None)),
         "support metadata validator API is incomplete")
    if binding_path is not None:
        binding = read_json(Path(binding_path), "execution binding")
        tools = binding.get("tools", {})
        for key, path in (("timing", timing_path), ("pair", Path(__file__)),
                          ("metadata", metadata_path)):
            expected = tools.get(key, {}).get("sha256")
            need(isinstance(expected, str) and sha256_file(path).casefold() ==
                 expected.casefold(), key + " tool changed after binding")

    scratch = Path(scratch).resolve()
    repo = Path(repo).resolve()
    need(scratch.is_dir() and repo.is_dir(), "scratch or repo root is missing")
    on_row, on_strict = validate_strict_row(
        scratch, repo, adapter, on_label, timing, True,
        tag + "-on")
    off_row, off_strict = validate_strict_row(
        scratch, repo, adapter, off_label, timing, False,
        tag + "-off")
    on_report = on_row["report"]
    off_report = off_row["report"]
    on_mode = validate_report_mode(on_report, True, on_label)
    off_mode = validate_report_mode(off_report, False, off_label)
    metadata_selftest = metadata.selftest()
    need(isinstance(metadata_selftest, dict) and
         metadata_selftest.get("status") == "PASS" and
         metadata_selftest.get("negativeCaseCount", 0) >= 14,
         "support metadata validator selftest did not retain negative canaries")
    metadata_summary = {
        "cold": metadata.validate_report(on_report, "cold"),
        "warm": metadata.validate_report(on_report, "warm"),
    }
    need(all(item.get("status") == "PASS" for item in metadata_summary.values()),
         "support metadata validation did not PASS for both phases")

    need(on_report.get("request") == off_report.get("request"),
         "ON/OFF qualification requests differ")
    on_projection, on_removed = strip_support_paths(on_report)
    off_projection, off_removed = strip_support_paths(off_report)
    need(set(on_removed) == set(SUPPORT_PATHS),
         "ON support projection did not remove exactly the seven allowlisted paths")
    need(off_removed == [],
         "OFF support projection removed an unexpected path")
    on_timing_projection, on_timing_removed = adapter.remove_declared_timings(on_projection)
    off_timing_projection, off_timing_removed = adapter.remove_declared_timings(off_projection)
    need(on_timing_removed == off_timing_removed,
         "ON/OFF timing paths differ outside the support allowlist")
    need(on_timing_projection == off_timing_projection,
         "ON/OFF reports differ outside support and declared timing paths")
    for row, label in ((on_row, on_label), (off_row, off_label)):
        raw_path = (scratch / row["reportPath"]).resolve()
        need(sha256_file(raw_path) == row["reportSha256"],
             label + " raw report digest changed during pair validation")

    summary = {
        "schema": 1,
        "status": "PASS",
        "purpose": "private LU support-census metadata/parity pair; no speed claim",
        "seed": SEED,
        "requestedPackageCount": 40,
        "on": {"label": on_label, "reportSha256": on_row["reportSha256"],
               "strictReader": on_strict, "support": on_mode,
               "metadata": metadata_summary},
        "off": {"label": off_label, "reportSha256": off_row["reportSha256"],
                "strictReader": off_strict, "support": off_mode},
        "requestExact": True,
        "reportExactOutsideSupportAndTiming": True,
        "removedSupportPathsOn": on_removed,
        "removedSupportPathsOff": off_removed,
        "removedTimingPaths": on_timing_removed,
        "metadataSelftest": metadata_selftest,
        "limits": {
            "strictReaderPerArm": True,
            "strictCorruptionCanariesPerArm": 43,
            "supportMetadataIndependentBeforeProjection": True,
            "structuralCountsAreNotTiming": True,
            "noSpeedClaim": True,
        },
    }
    output = scratch / (on_label + "-vs-" + off_label +
                        "-support-census-parity-" + tag + ".json")
    write_new(output, summary)
    return summary


def sha256_file(path):
    return sha256_bytes(path.read_bytes())


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scratch", type=Path)
    parser.add_argument("repo", type=Path)
    parser.add_argument("on_label")
    parser.add_argument("off_label")
    parser.add_argument("--tag", default="pair-1")
    parser.add_argument("--binding", type=Path)
    args = parser.parse_args(argv)
    summary_path = (args.scratch / (args.on_label + "-vs-" + args.off_label +
                                    "-support-census-parity-" + args.tag + ".json"))
    try:
        need(re.fullmatch(r"[A-Za-z0-9._-]{1,48}\Z", args.tag) is not None,
             "tag contains unsafe path characters")
        summary = validate_pair(args.scratch, args.repo, args.on_label,
                                args.off_label, args.tag, args.binding)
        print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
        return 0
    except (ValidationError, OSError, KeyError, TypeError, ValueError) as error:
        failure = {
            "schema": 1,
            "status": "FAIL",
            "purpose": "private LU support-census metadata/parity pair; no speed claim",
            "labels": [args.on_label, args.off_label],
            "error": str(error),
        }
        if not summary_path.exists():
            try:
                write_new(summary_path, failure)
            except Exception:
                pass
        print("FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
