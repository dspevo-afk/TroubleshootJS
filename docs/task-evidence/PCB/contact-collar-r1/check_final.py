"""Bind actual native/build/player evidence; retain the rejected fixture attempt."""
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Final contact audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def main():
    runpy.run_path(str(HERE / "qualify.py"))["main"]()
    native = read("native-assessment.json")
    assert native["nativeBuildQualification"] == "PASS" and len(native["qualificationCases"]) == 5
    current = read("candidate-inputs.json")
    failed = read("native-attempt-1/candidate-inputs.json")
    assert current["gates"] == failed["gates"]
    assert set(current["files"]) == set(failed["files"])
    assert {p for p in current["files"] if current["files"][p] != failed["files"][p]} == {
        "tests/contracts/PowerServicePartContractTest.java"}
    assert read("native-attempt-1/failure.json")["status"] == "FAIL_NATIVE"
    assert read("native-attempt-1/contracts-timing.json")["exitCode"] == 2
    assert read("native-attempt-1/contracts-process-cleanup.json")["status"] == "PASS"
    archive = HERE / "native-attempt-1/candidate-source"
    assert {p.relative_to(archive).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in archive.rglob("*") if p.is_file()} == {
                name: failed["files"][name] for name in native["changedOwners"]}
    boundary = read("production-static-boundary.json")
    assert boundary["status"] == "PASS" and len(boundary["files"]) == 3
    assert all(row["candidateRawSha256"] == current["files"][row["path"]] and
               row["existingSourceCanonicalUnchanged"] for row in boundary["files"])
    expected, served, post = read("preview-expected.json"), read("player-served.json"), read("post-player-provenance.json")
    fields = {"SourceDigest": "data-tsj-preview-source-digest", "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest", "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    assert all(expected[k] == post[k] and str(expected[k]) == served[v] for k, v in fields.items())
    visual = read("visual-review.json")
    assert visual["status"] == "PASS" and len(visual["primaryCaptures"]) == 5
    assert len(visual["additionalRetestConfirmation"]) == 1 and visual["actualRawImagesInspected"] == 6
    for capture in visual["primaryCaptures"] + visual["additionalRetestConfirmation"]:
        raw = (HERE / capture["file"]).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == capture["sha256"] and len(raw) == capture["bytes"]
    for name, value in (("led-live-supply-dom.txt", "5 V"), ("led-isolated-r1-ohms-dom.txt", "OL"),
                        ("led-replacement-ohms-dom.txt", "330 Ohm")):
        assert 'row "' + value + '"' in (HERE / name).read_text(encoding="utf-8")
    assert "Customer retest did not pass. Continue troubleshooting." in (
        HERE / "led-unrepaired-retest-dom.txt").read_text(encoding="utf-8")
    assert "Customer retest passed. The reported behavior is resolved." in (
        HERE / "led-repaired-retest-dom.txt").read_text(encoding="utf-8")
    for name, count in (("sensor-restored-off-body.txt", "0 parts"), ("final-off-body.txt", "1 part")):
        text = (HERE / name).read_text(encoding="utf-8")
        assert "Board Power: OFF" in text and "PARTS TRAY · " + count in text
    menu = (HERE / "final-menu-dom.txt").read_text(encoding="utf-8")
    assert "Start a service job" in menu and "Resume current board" in menu
    assert [e["observedReplay"] for e in read("player-events.json") if "observedReplay" in e] == [
        "tsj-alpha/6/EASY/SENSOR_CONTROL/0", "tsj-alpha/6/EASY/LED_INDICATOR/5577758334702827054"]
    logs = read("browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 2 and all(e["type"] == "error" and "404" in e["text"] and
        e["location"]["url"].endswith("/favicon.ico") for e in logs["console"])
    assert read("browser-api-cleanup.json")["rootExitObserved"] is True
    for name in ("pre-close-browser-check.json", "process-resource-cleanup.json", "preview-stop-timing.json"):
        assert read(name)["status"] == "PASS"
    result = {"applicationQualification": "PASS", "sourceCount": 1481, "originalGates": 4,
              "operationSeconds": native["operationSeconds"], "maintainedNativeSuites": 11,
              "powerServiceAssertions": 577, "actualJdk8GwtPermutations": 5,
              "failedAttempt": "Retained native fixture sequencing failure58.93794s/driver2; production unchanged, existing solver advanced before successor mutations",
              "staticBoundary": "Three insertion-only production changes; old probe/hit/geometry/paint code unchanged canonically",
              "normalPlayer": "PASS; Sensor0 single-lead lift/reconnect, original chip removal/reinstall; known LED actual5V, isolated R1 OL, replacement330Ohm, fresh customer FAIL->PASS",
              "rawCaptures": "Five primary inspected views plus one retained immediate completion frame",
              "provenance": "Five served/expected/post-player fields exact",
              "currentResourceCleanup": "PASS; actual browser API/root exit0, new profile absent, recorded births exited, maintained stop released8907",
              "limits": ["Presentation convention for modeled physical attachment; no solder quality/side/material or manufacturing-state claim",
                         "New native test does not directly exercise a covered-land negative; unchanged canProbe guard explicit",
                         "No fresh FileReader/full unrelated matrix/private Close7/CDP/Edge/unseen/all-seed/new-size proof",
                         "Two existing favicon404 errors; no page errors or other recorded warnings/errors",
                         "Earlier unused profile deletion remains policy-blocked/untouched; old baseline/private audit metadata retained"],
              "publication": "Pending root diff/privacy/staged-byte audit and normal commit/push; no main/email"}
    (HERE / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS final contact-collar application evidence; retained failure and older-directory limit separate")


if __name__ == "__main__":
    main()
