"""Final offline audit, distinct from actual player input and process observations."""
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Final chip-reference audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def main():
    runpy.run_path(str(HERE / "qualify.py"))["main"]()
    native = read("native-assessment.json")
    assert native["nativeBuildQualification"] == "PASS" and len(native["qualificationCases"]) == 5
    expected, served, post = read("preview-expected.json"), read("player-served.json"), read("post-player-provenance.json")
    fields = {"SourceDigest": "data-tsj-preview-source-digest", "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest", "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    assert all(expected[k] == post[k] and str(expected[k]) == served[v] for k, v in fields.items())
    visual = read("visual-review.json")
    assert visual["status"] == "PASS" and len(visual["captures"]) == 5
    for capture in visual["captures"]:
        raw = (HERE / capture["file"]).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == capture["sha256"] and len(raw) == capture["bytes"]
    for name, terminal in (("u1-pinout-dom.txt", "INPUT"), ("u2-pinout-dom.txt", "SENSOR"),
                           ("removed-u2-pinout-dom.txt", "SENSOR")):
        dom = (HERE / name).read_text(encoding="utf-8")
        assert "Dot marks " + terminal + ", the first listed terminal." in dom
        assert "Close schematic" in dom
    for name in ("u1-context-dom.txt", "u2-context-dom.txt", "removed-u2-context-dom.txt"):
        dom = (HERE / name).read_text(encoding="utf-8")
        assert "View schematic" in dom and "Close schematic" not in dom
    settled = (HERE / "reinstalled-off-body.txt").read_text(encoding="utf-8")
    assert "Board Power: OFF" in settled and "PARTS TRAY · 0 parts" in settled
    menu = (HERE / "final-menu-dom.txt").read_text(encoding="utf-8")
    assert "Start a service job" in menu and "Resume current board" in menu
    events = read("player-events.json")
    assert [e["observedReplay"] for e in events if "observedReplay" in e] == ["tsj-alpha/6/EASY/SENSOR_CONTROL/0"]
    assert any(e.get("observed") == "Parts tray0 and board remains OFF" for e in events)
    logs = read("browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 2
    assert all(e["type"] == "error" and "404" in e["text"] and e["location"]["url"].endswith("/favicon.ico")
               for e in logs["console"])
    assert read("browser-api-cleanup.json")["rootExitObserved"] is True
    assert read("pre-close-browser-check.json")["status"] == "PASS"
    assert read("process-resource-cleanup.json")["status"] == "PASS"
    assert read("preview-stop-timing.json")["status"] == "PASS"
    result = {"applicationQualification": "PASS", "sourceCount": 1481, "originalGateCount": 4,
              "operationSeconds": native["operationSeconds"], "actualJdk8GwtPermutations": 5,
              "maintainedNativeSuites": 8, "benchAssertions": 235, "offlineFramingCanaries": 5,
              "player": "PASS; normal Sensor0 mounted U1/U2 right-click menus, public diagrams, powered-off original U2 removal/loose diagram/reinstallation and OFF/menu",
              "publicReplay": "tsj-alpha/6/EASY/SENSOR_CONTROL/0", "rawCapturesInspected": 5,
              "provenance": "PASS; served/expected/post-player five fields exact",
              "newTaskResourceCleanup": "PASS; owned births exited, new profile absent, maintained stop released8907",
              "preparationFailures": "Retained: promises-vs-sync metadata API, early exact-seed control lookup, tray ARIA capitalization, initial offline receipt/footer framing and hidden-power-control final-menu oracle",
              "limits": ["Two existing favicon404 console errors; no page errors or other recorded warnings/errors",
                         "No fresh functional repair/retest/FileReader/full matrix/private Close7/CDP/Edge qualification",
                         "Generic pose fixtures are transform checks, not expansion of allowed package poses",
                         "Earlier unused profile deletion remains policy-blocked and untouched; old baseline/private audit metadata retained",
                         "No new compaction, all-seed/manufacturing or commercial pin-number claim"],
              "publication": "Pending root diff/staged-byte audit and normal commit/push; no main/email"}
    (HERE / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS final chip-reference application evidence; older-directory limit separate")


if __name__ == "__main__":
    main()
