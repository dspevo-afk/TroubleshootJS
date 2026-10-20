"""Check exact final title evidence; keep application and preparation outcomes separate."""
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Final title audit requires assertion-enabled Python")

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    candidate = read(HERE / "candidate-inputs.json")
    assert runpy.run_path(str(HERE / "inputs.py"))["record"]() == candidate
    old = read(HERE.parent / "normal-packing-r2/candidate-inputs.json")
    base = {row["path"]: row["sha256"] for row in old["files"]}
    base["tests/browser/quickplay_procedural_acceptance.py"] = read(
        HERE.parent / "normal-packing-r2/publication-inputs.json")["overlay"]["currentSha256"]
    for phase, count in (("baseline", 1), ("candidate", 8)):
        inputs = read(HERE / (phase + "-inputs.json"))
        delta = {name: digest for name, digest in inputs["files"].items() if base[name] != digest}
        archive = HERE / (phase + "-source")
        actual = {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob("*") if p.is_file()}
        assert len(delta) == count and actual == delta, "raw source archive drift"
    timings = {}
    for phase in ("baseline", "candidate", "contracts", "build"):
        value = read(HERE / (phase + "-timing.json"))
        assert value["status"] == "PASS" and value["exitCode"] == 0
        assert read(HERE / (phase + "-process-cleanup.json"))["status"] == "PASS"
        selected = "baseline" if phase == "baseline" else "candidate"
        marker = "PASS title phase exact input audit phase=" + selected + " sources=1481 gates=4"
        for side in ("before", "after"):
            assert marker in (HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig")
        timings[phase] = value["elapsedSeconds"]
    build = (HERE / "build.txt").read_text(encoding="utf-8-sig")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded",
                   "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1, "missing/duplicate GWT marker"
    assert "BUILD_INFRASTRUCTURE" not in build and "BUILD_FAILURE" not in build
    contracts = (HERE / "contracts.txt").read_text(encoding="utf-8-sig")
    for marker in (
            "PCB normal packing identity contracts assertions=21 packing=2 replay=tsj-alpha/6",
            "U06 session save contracts assertions=34988", "PlayerSessionFingerprintContractTest assertions=119",
            "current controlled-indicator contracts 256 assertions", "actual RB56 geometry report 588 assertions",
            "PASS: focused current contracts; 5 Java suites. Full matrix and independent oracles NOT RUN."):
        assert marker in contracts, "missing selected native marker"
    qualification = read(HERE / "qualification.json")
    qualified = read(HERE / "qualified-gate.json")
    assert qualification["status"] == qualified["status"] == "PASS"
    assert qualification["cases"] == 44 and qualification["caseCount"] == 13
    assert all(row["result"] == "PASS" for row in qualification["qualificationCases"])
    assert qualified["qualifierSha256"] == sha(HERE / "qualify.py")
    expected = read(HERE / "preview-expected.json")
    post = read(HERE / "post-player-provenance.json")
    served = read(HERE / "player-served.json")
    fields = {"SourceDigest": "data-tsj-preview-source-digest", "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest", "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    assert all(expected[k] == post[k] and str(expected[k]) == served[v] for k, v in fields.items())
    visual = read(HERE / "visual-review.json")
    assert visual["status"] == "PASS" and len(visual["captures"]) == 5
    for capture in visual["captures"]:
        path = HERE / capture["file"]
        assert sha(path) == capture["sha256"] and path.stat().st_size == capture["bytes"]
    events = read(HERE / "player-events.json")
    replays = [row["observedReplay"] for row in events if "observedReplay" in row]
    assert replays == ["tsj-alpha/6/EASY/RC_DELAY/13", "tsj-alpha/6/EASY/SENSOR_CONTROL/0"]
    assert "Board Power: OFF" in (HERE / "final-menu-dom.txt").read_text(encoding="utf-8")
    logs = read(HERE / "browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 1 and logs["console"][0]["location"]["url"].endswith("/favicon.ico")
    assert "404" in logs["console"][0]["text"]
    assert read(HERE / "browser-api-cleanup.json")["rootExitObserved"] is True
    assert read(HERE / "process-resource-cleanup.json")["status"] == "PASS"
    assert read(HERE / "preview-stop-timing.json")["status"] == "PASS"
    result = {"applicationQualification": "PASS", "candidateInputSha256": sha(HERE / "candidate-inputs.json"),
              "sourceCount": 1481, "originalGateCount": 4, "pairedCases": 44,
              "geometryApartFromTitle": "PASS", "fullFingerprintChanges": 24,
              "styleProof": "PASS; static unchanged owners/call sites plus five inspected actual captures",
              "supplementalReaderCanaries": 13, "operationSeconds": timings,
              "actualProductionBuild": "PASS; JDK8/GWT five OBF permutations and link",
              "normalPlayer": "PASS; real RC13/Sensor0 Fit/Zoom/middle-pan and exact public replays",
              "productionProvenance": "PASS; five served/expected/post-player fields exact",
              "newTaskProcessProfileListenerCleanup": "PASS",
              "preparationFailures": "Retained; direct-preview state assumption, managed outer entry exit1 after stop, wrong View selector and report/reader preparation failures",
              "olderDirectoryCleanup": "BLOCKED_BY_AUTOMATIC_APPROVAL_REVIEW; unused old profile untouched",
              "limits": ["Fixed informed corpus; no all-seed/manufacturing/new compaction gain claim",
                         "One existing favicon404; no private zero-warning/Close7/CDP/Edge qualification",
                         "Fresh RC/Sensor repair/retest/FileReader and full contract matrix NOT_RUN",
                         "Small private task audit metadata and baseline COPY retained outside Git"],
              "publication": "Pending explicit diff/staged-byte audit, normal commit/push; no main/email"}
    (HERE / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS final title application evidence; preparation and older-directory limits retained separately")


if __name__ == "__main__":
    main()
