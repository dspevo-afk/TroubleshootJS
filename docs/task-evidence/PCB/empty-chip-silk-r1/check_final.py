"""Bind the actual empty-chip silk source, native, build, player and cleanup evidence."""
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Final empty-chip audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def main():
    runpy.run_path(str(HERE / "qualify.py"))["main"]()
    current = read("candidate-inputs.json")
    old = read("native-attempt-1/candidate-inputs.json")
    assert current["gates"] == old["gates"]
    assert set(current["files"]) == set(old["files"])
    assert {p for p in current["files"] if current["files"][p] != old["files"][p]} == {
        "tests/contracts/PcbSilkscreenScaleContractTest.java"}
    failed = read("native-attempt-1/failure.json")
    assert failed["status"] == "FAIL_NATIVE" and failed["driverExitCode"] == 2
    assert read("native-attempt-1/contracts-timing.json")["status"] == "FAIL"
    assert read("native-attempt-1/contracts-process-cleanup.json")["status"] == "PASS"
    assert not (HERE / "native-attempt-1/contracts-receipt.txt").exists()
    archive = HERE / "native-attempt-1/candidate-source"
    assert {p.relative_to(archive).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in archive.rglob("*") if p.is_file()} == {
                name: old["files"][name] for name in read("native-assessment.json")["changedOwners"]}
    for name, digest in old["gates"].items():
        assert hashlib.sha256((HERE / "native-attempt-1" / name).read_bytes()).hexdigest() == digest
    boundary = read("static-boundary.json")
    assert boundary["status"] == "PASS" and len(boundary["files"]) == 3
    assert all(row["candidateRawSha256"] == current["files"][row["path"]] and
               row["existingSourceCanonicalUnchanged"] for row in boundary["files"])
    expected, served, post = read("preview-expected.json"), read("player-served.json"), read("post-player-provenance.json")
    fields = {"SourceDigest": "data-tsj-preview-source-digest", "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest", "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    assert all(expected[k] == post[k] and str(expected[k]) == served[v] for k, v in fields.items())
    assert set(expected) == set(post) == set(fields) | {"Protocol"}
    visual = read("visual-review.json")
    assert visual["status"] == "PASS" and visual["actualRawImagesInspected"] == 5
    captures = visual["rawCaptures"]
    assert [c["file"] for c in captures] == ["sensor-installed.jpg", "sensor-empty-footprint.jpg",
        "sensor-empty-bottom.jpg", "sensor-reinstalled.jpg", "led-repaired.jpg"]
    for c in captures:
        raw = (HERE / c["file"]).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == c["sha256"] and len(raw) == c["bytes"]
    def text(name):
        return (HERE / name).read_text(encoding="utf-8")
    for name in ("sensor-unrepaired-retest-dom.txt", "sensor-roundtrip-retest-dom.txt", "led-unrepaired-retest-dom.txt"):
        assert "Customer retest did not pass. Continue troubleshooting." in text(name)
    assert "Customer retest passed. The reported behavior is resolved." in text("led-repaired-retest-dom.txt")
    assert 'row "OL"' in text("led-isolated-r1-ohms-dom.txt")
    assert 'row "330 Ohm"' in text("led-replacement-ohms-dom.txt")
    assert "Dot marks SENSOR, the first listed terminal." in text("loose-u2-pinout-dom.txt")
    for name, count in (("sensor-restored-off-body.txt", "0 parts"), ("final-off-body.txt", "1 part")):
        assert "Board Power: OFF" in text(name) and "PARTS TRAY · " + count in text(name)
    assert "Start a service job" in text("final-menu-dom.txt") and "Resume current board" in text("final-menu-dom.txt")
    assert [e["observedReplay"] for e in read("player-events.json") if "observedReplay" in e] == [
        "tsj-alpha/6/EASY/SENSOR_CONTROL/0", "tsj-alpha/6/EASY/LED_INDICATOR/5577758334702827054"]
    logs = read("browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 1 and all(e["type"] == "error" and "404" in e["text"] and
        e["location"]["url"].endswith("/favicon.ico") for e in logs["console"])
    for name in ("browser-api-cleanup.json", "pre-close-browser-check.json", "process-resource-cleanup.json", "preview-stop-timing.json"):
        assert read(name)["status"] == "PASS"
    assert read("browser-api-cleanup.json")["rootExitObserved"] is True
    assert read("process-resource-cleanup.json")["newProfileAbsent"] is True
    assert read("process-resource-cleanup.json")["preview8907Listening"] is False
    assert read("optimized-negative.json")["status"] == "PASS_REJECTED"
    assert read("static-review.json")["status"] == "NO_CONCRETE_BLOCKER"
    assert hashlib.sha256((HERE / "build.txt").read_bytes()).hexdigest() == read("log-publication.json")["publishedSha256"]
    result = {"applicationQualification": "PASS", "sourceCount": 1481, "originalGates": 4,
        "nativeSuites": 9, "silkscreenAssertions": 79, "powerServiceAssertions": 620,
        "operationSeconds": read("native-assessment.json")["operationSeconds"], "actualJdk8GwtPermutations": 5,
        "failedAttempt": "Retained41.329293s disallowed fixture pose/driver2; test-only correction preserves production restrictions",
        "normalPlayer": "Sensor0 initial, empty footprint, bottom absence, loose named pinout, original reinstall and unchanged complaint; known LED isolated OL/replacement330Ohm/fresh customer FAIL->PASS",
        "rawImagesInspected": 5, "provenance": "Five served/expected/post-player fields exact",
        "currentCleanup": "PASS owned API close/root exit0/profile absent/native/build/browser/preview/launcher births exited/8907 released",
        "limits": read("preparation-limitations.json"),
        "publication": "Pending root staged-byte/privacy review and normal commit/push; no main/email"}
    (HERE / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS final empty-chip application evidence; retained native failure and older-directory limit separate")


if __name__ == "__main__":
    main()
