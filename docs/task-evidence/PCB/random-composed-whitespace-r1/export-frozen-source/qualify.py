"""Qualify one exact normal composed-board export and its sealed host evidence."""
import argparse
import copy
import hashlib
import json
import math
import re
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Assertion-enabled Python required")

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PUBLISHED = HERE.parent / "sparse-board-diagnostic-r1" / "qualify.py"
PUBLISHED_KEY = "sparse-board-diagnostic-r1/qualify.py"
PUBLISHED_SHA = "0b8ff9c9cf22599c23d430901ed8b9d3c9867f1ecb1cb63407480647b9bbcc71"
FAMILY = "COMPOSED_CONTROLLED_INDICATOR"
PROFILE = "MEDIUM"
SEED = "-2750102463469069236"
REPLAY = "tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-2750102463469069236"
AUDIT = "PASS exact random export source1482 machinery24 frozen helpers"


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def text(path):
    return Path(path).read_text(encoding="utf-8-sig")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def replace_once(source, old, new, label, changes):
    require(source.count(old) == 1, "published reader replacement count: " + label)
    changes.append({"label": label, "replacements": 1})
    return source.replace(old, new)


def load_published_reader():
    baseline = read(HERE / "baseline-inputs.json")
    require(baseline["borrowedPublishedInputs"][PUBLISHED_KEY] == PUBLISHED_SHA and
            sha(PUBLISHED) == PUBLISHED_SHA, "published reader pin")
    source = text(PUBLISHED)
    changes = []
    source = replace_once(
        source,
        "EXPECTED={'rc':('RC_DELAY','EASY','-9187226374781659024'),\n"
        "          'composed':('COMPOSED_CONTROLLED_INDICATOR','MEDIUM','7141843758046216173')}",
        "EXPECTED={'composed':('COMPOSED_CONTROLLED_INDICATOR','MEDIUM','-2750102463469069236')}",
        "pin only the actual first-random composed replay identity", changes)
    source = replace_once(source,
        "layout['constraints']['placementPackingVersion']==3",
        "layout['constraints']['placementPackingVersion']==4",
        "require current packing4 physical epoch", changes)
    require(len(changes) == 2, "only expected identity and packing version may change")
    namespace = {"__file__": str(PUBLISHED), "__name__": "_published_sparse_board_reader"}
    exec(compile(source, str(PUBLISHED), "exec"), namespace)
    require(namespace["EXPECTED"] == {"composed": (FAMILY, PROFILE, SEED)},
            "adapted published reader identity")
    return namespace, hashlib.sha256(source.encode("utf-8")).hexdigest(), changes


def validate_row(row, sampler, phase="composed"):
    require(phase == "composed", "this reader accepts only the composed phase")
    return load_published_reader()[0]["validate_row"](row, sampler, phase)


def audit_inputs(adapter):
    inputs = runpy.run_path(str(HERE / "export_inputs.py"))
    current = inputs["record"](Path(adapter))
    require(current == read(HERE / "export-inputs.json"), "frozen export input seal")
    require(current["request"] == REPLAY, "exact accepted random request seal")
    return current


def phase_result(phase):
    folder = HERE / phase
    timing = read(folder / "timing.json")
    launch_path = folder / "process-launch.json"
    launch = read(launch_path)
    cleanup = read(folder / "process-cleanup.json")
    require(timing["phase"] == phase and timing["status"] == "PASS" and
            type(timing["exitCode"]) is int and timing["exitCode"] == 0 and
            timing["failure"] is None and timing["terminalCleanupIncluded"] is True and
            timing["cleanupDuration"] == "NOT_SEPARATELY_MEASURED", "actual successful operation")
    elapsed = timing["elapsedSeconds"]
    require(type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0,
            "actual operation elapsed time")
    require(launch["phase"] == phase and len(launch["drivers"]) == 2,
            "actual phase launch record")
    require(cleanup["status"] == "PASS" and type(cleanup["actualExecExitCode"]) is int and
            cleanup["actualExecExitCode"] == timing["exitCode"] == 0 and
            cleanup["launchSha256"] == sha(launch_path), "separate actual tool exit and launch seal")
    checks = cleanup["checks"]
    require(len(checks) == len(launch["drivers"]) == 2 and
            all(check["recordedBirthExited"] is True for check in checks), "owned driver births exited")
    project = lambda rows: [{key: row[key] for key in ("pid", "startTicks", "executable")} for row in rows]
    require(project(checks) == launch["drivers"], "driver birth correspondence")
    for side in ("before", "after"):
        require(text(folder / ("input-audit-" + side + ".txt")).strip() == AUDIT,
                "exact phase input audit " + side)
    return timing


def check_canaries():
    timing = phase_result("canaries")
    record = read(HERE / "canaries" / "canaries.json")
    checks = record["checks"]
    require([item["kind"] for item in checks] == ["seed", "family"], "actual binding case order")
    expected_keys = {"kind", "status", "actualAdapterExitCode", "elapsedSeconds",
                     "privateLogSha256", "privateLogBytes"}
    for item in checks:
        elapsed = item["elapsedSeconds"]
        require(set(item) == expected_keys and item["status"] == "PASS_REJECTED_AT_PARAMETER_BINDING" and
                type(item["actualAdapterExitCode"]) is int and item["actualAdapterExitCode"] == 1 and
                type(elapsed) in (int, float) and math.isfinite(elapsed) and elapsed > 0 and
                re.fullmatch(r"[0-9a-f]{64}", item["privateLogSha256"]) is not None and
                type(item["privateLogBytes"]) is int and item["privateLogBytes"] > 0,
                "actual host rejection evidence")
    require(record["limits"] ==
            "Actual selected adapter rejects a noncanonical signed long and excluded RC family before compiler/native work; raw stderr private/hash-bound",
            "host canary scope")
    runner = text(HERE / "run_export.ps1")
    require(runner.count("$family=if($kind -eq 'family'){'RC_DELAY'}else{'COMPOSED_CONTROLLED_INDICATOR'}") == 1 and
            runner.count("$seed=if($kind -eq 'seed'){'-02750102463469069236'}else{'-2750102463469069236'}") == 1,
            "exact noncanonical-seed and excluded-family host inputs")
    require("Cannot validate argument on parameter '" in runner and
            "CURRENT_CONTRACT_|javac|PASS: focused current contracts" in runner,
            "binding rejection precedes compiler/native execution")
    require(not (HERE / "canaries" / "native.txt").exists() and
            not (HERE / "canaries" / "receipt.txt").exists(), "negative controls produced no native export")
    return timing, checks


def read_rows(receipt, prefix):
    return [json.loads(line[len(prefix):]) for line in receipt.splitlines() if line.startswith(prefix)]


def reader_negatives(row, sampler, validator):
    cases = (
        ("public identity", lambda item: item.update(publicSeed="0")),
        ("pad net membership", lambda item: item["layout"]["pads"][0].update(netId="__WRONG_NET__")),
        ("independent copper", lambda item: item["physicalMetrics"].update(
            copper=item["physicalMetrics"]["copper"] + 1)),
        ("independent protected void", lambda item: item["physicalMetrics"].update(
            protectedVoid=item["physicalMetrics"]["protectedVoid"] + 1)),
        ("current physical epochs", lambda item: item["layout"]["constraints"].update(
            placementPackingVersion=3)),
    )
    rejected = []
    for message, mutate in cases:
        changed = copy.deepcopy(row)
        mutate(changed)
        try:
            validator(changed, sampler, "composed")
        except AssertionError as error:
            require(str(error) == message, "reader negative was rejected at the wrong assertion")
            rejected.append(message)
        else:
            raise AssertionError("reader accepted negative control: " + message)
    return rejected


def qualify(private_adapter_path):
    adapter = Path(private_adapter_path)
    require(adapter.is_file(), "private adapter path")
    seal = audit_inputs(adapter)
    baseline = read(HERE / "baseline-inputs.json")
    expected_execution = baseline["expectedExecution"]
    require(set(expected_execution) == {"Protocol", "SourceDigest", "ScriptDigest", "WebDigest", "Digest", "FileCount"} and
            expected_execution == read(HERE / "execution-before.json") == read(HERE / "execution-after.json"),
            "six-field execution provenance unchanged")
    surface = text(HERE / "board-surface-review.md")
    require("NO_GO for another paint change" in surface, "retain bounded board-surface NO_GO")
    for name, digest in baseline["surfaceReviewSources"].items():
        require(sha(ROOT / name) == digest, "reviewed surface source hash: " + name)
    prior = HERE.parent / "paired-packing-r2"
    for name, digest in baseline["reviewedImages"].items():
        require(sha(prior / name) == digest, "reviewed published image hash: " + name)
    source_review = read(HERE / "source-review.json")
    require(source_review["status"] == "PASS_TASK_ONLY_ADAPTATION_SOURCE_REVIEW" and
            source_review["adapterManifestSha256"] == sha(HERE / "adapter-manifest.json") and
            source_review["exporterSha256"] == sha(HERE / "export-source" / "SparseNormalBoardDiagnostic.java"),
            "task-only source review seal")
    reader, adapted_reader_sha, reader_changes = load_published_reader()

    canary_timing, canaries = check_canaries()
    timing = phase_result("composed")
    folder = HERE / "composed"
    receipt = text(folder / "receipt.txt")
    native = text(folder / "native.txt")
    require("CURRENT_CONTRACT_FAILURE:" not in native and "CURRENT_CONTRACT_CLEANUP:" not in native,
            "no maintained native failure")
    native_marker = "PASS: focused current contracts; 1 Java suites. Full matrix and independent oracles NOT RUN."
    require(native.count(native_marker) == 1, "maintained native final marker")
    rows = read_rows(receipt, "PCB_SPARSE_NORMAL_DIAGNOSTIC ")
    samplers = read_rows(receipt, "PCB_WHITESPACE_CASE ")
    require(len(rows) == len(samplers) == 1, "exact composed observation rows")
    final_marker = (r"(?m)^PASS: sparse normal board diagnostic family=" + re.escape(FAMILY) +
                    r" seed=" + re.escape(SEED) + r" assertions=[1-9][0-9]*\r?$")
    require(len(re.findall(final_marker, receipt)) == 1, "exact native composed final marker")
    result = reader["validate_row"](rows[0], samplers[0], "composed")
    require(rows[0]["physicalMetrics"]["available"] is True, "independent negative-control metric baseline")
    negatives = reader_negatives(rows[0], samplers[0], reader["validate_row"])

    assessment = HERE / "assessment.json"
    require(not assessment.exists(), "preserve prior assessment")
    result.update({"elapsedSeconds": timing["elapsedSeconds"], "receiptSha256": sha(folder / "receipt.txt"),
                   "nativeSha256": sha(folder / "native.txt")})
    assessment_value = {
        "status": "PASS_EXACT_RANDOM_COMPOSED_WHITESPACE_DIAGNOSIS",
        "inputSealSha256": sha(HERE / "export-inputs.json"),
        "adapterSha256": seal["adapterSha256"],
        "execution": expected_execution,
        "reader": {"publishedSha256": PUBLISHED_SHA, "adaptedSha256": adapted_reader_sha,
                   "guardedChanges": reader_changes},
        "boardSurfaceReview": {"status": "NO_GO", "sha256": sha(HERE / "board-surface-review.md"),
                               "sourceCount": len(baseline["surfaceReviewSources"]),
                               "imageCount": len(baseline["reviewedImages"])},
        "canaries": {"elapsedSeconds": canary_timing["elapsedSeconds"], "checks": canaries},
        "result": result, "readerNegativeControls": negatives,
        "limits": "One actual accepted ordinary composed replay; existing grid-10 free space is not proof of packability. No production edit, new route or budget, fresh matrix/build/player claim, manufacturing, optimality, or all-seed claim.",
    }
    assessment.write_text(json.dumps(assessment_value, indent=2) + "\n", encoding="utf-8")
    return assessment_value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--private-adapter-path", type=Path, required=True)
    args = parser.parse_args()
    qualify(args.private_adapter_path)
    print("PASS exact first-random composed diagnostic; actual canaries/export/cleanup and five reader negatives")


if __name__ == "__main__":
    main()
