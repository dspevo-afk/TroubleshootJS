"""Audit the recorded ordinary player flow; no solver or screenshot oracle."""
import hashlib
import json
import re
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Reference player audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def text(name):
    return (HERE / name).read_text(encoding="utf-8-sig")


def main():
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    assert inputs["record"]() == read("candidate-inputs.json")
    assert read("native-assessment.json")["status"] == "PASS"
    expected = read("preview-expected.json")
    assert expected == read("post-player-provenance.json")
    fields = {"SourceDigest": "data-tsj-preview-source-digest",
              "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest",
              "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    served = read("player-served.json")
    assert set(expected) == set(fields) | {"Protocol"}
    assert all(str(expected[k]) == served[v] for k, v in fields.items())
    assert read("browser-owner.json")["sandbox"] is True
    assert read("preview-owner.json")["status"] == "PASS"

    menu = text("component-probe-menu-dom.txt")
    component_options = menu.split('combobox "Select component":', 1)[1].split('combobox "Probe terminal":', 1)[0]
    actual = re.findall(r'option "([^"]+)"', component_options)
    assert actual[0] == "Select component"
    assert len(actual[1:]) == len(set(actual[1:])) == 15
    assert set(actual[1:]) == {"R1", "R2", "R3", "R4", "R5", "R6", "R7",
        "Q1", "Q2", "LED1", "LED2", "LED3", "J1", "J2", "J3"}
    for name in actual[1:]:
        assert 'option "' + name + ' terminal ' in menu
    assert "tsj-realization" not in menu
    assert "Install as Channel A / R2" in text("supporting-r2-loose-dom.txt")
    assert "PARTS TRAY · 0 parts" in text("supporting-r2-restored-body.txt")
    assert "Install as Channel B / R4" in text("r4-replacement-target-dom.txt")
    assert "Part 9 - Color bands" in text("r4-replacement-target-dom.txt")
    assert re.search(r"(?m)^1 kOhm$", text("replacement-lifted-lead-reading-body.txt"))
    assert re.search(r"(?m)^OL$", text("removed-original-direct-reading-body.txt"))
    assert "State: Installed" in text("r4-reconnected-component.txt")
    assert "Customer retest did not pass. Continue troubleshooting." in text("unrepaired-body.txt")
    assert "Customer retest passed. The reported behavior is resolved." in text("repaired-retest-dom.txt")
    assert "Board Power: OFF" in text("final-off-body.txt")
    assert "PARTS TRAY · 1 part" in text("final-off-body.txt")
    assert "Resume current board" in text("final-menu-dom.txt")

    events = read("player-events.json")
    by_event = {e["event"]: e for e in events}
    assert by_event["Unrepaired customer retest"]["actualStatus"] == "FAIL"
    assert by_event["Repaired customer retest"]["actualStatus"] == "PASS"
    assert by_event["Isolated channel B driver measurement"]["status"] == "NOT_COMPONENT_PROOF"
    assert by_event["Corrected replacement isolated measurement"]["actualReading"] == "1 kOhm"
    assert by_event["Removed original independently measured"]["actualReading"] == "OL"
    code = "tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-1531998593498751668"
    assert by_event["Actual public replay observed after repair and OFF"]["observedReplay"] == code
    assert code in text("final-menu-dom.txt")

    visual = read("visual-review.json")
    assert visual["status"] == "OBSERVED"
    assert len(visual["primaryViews"]) == len(set(visual["primaryViews"])) == 5
    assert visual["actualRawImagesInspected"] == len(visual["rawCaptures"]) == 8
    assert set(visual["primaryViews"]) < {c["file"] for c in visual["rawCaptures"]}
    for capture in visual["rawCaptures"]:
        raw = (HERE / capture["file"]).read_bytes()
        assert len(raw) == capture["bytes"]
        assert hashlib.sha256(raw).hexdigest() == capture["sha256"]

    logs = read("browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 1
    warning = logs["console"][0]
    assert warning["type"] == "error" and "404" in warning["text"]
    assert warning["location"]["url"].endswith("/favicon.ico")
    for name in ("pre-close-browser-check.json", "browser-api-cleanup.json",
                 "preview-stop-timing.json", "process-resource-cleanup.json"):
        assert read(name)["status"] == "PASS"
    cleanup = read("process-resource-cleanup.json")
    assert cleanup["newProfileAbsent"] is True
    assert cleanup["preview8907Listening"] is False and cleanup["managedStatePresent"] is False
    assert len(cleanup["checks"]) == 11
    assert all(c["recordedBirthExited"] is True for c in cleanup["checks"])
    assert read("browser-api-cleanup.json")["rootExitCode"] == 0
    assert read("preview-stop-timing.json")["exitCode"] == 0
    redaction = read("privacy-redactions.json")
    assert hashlib.sha256((HERE / "build.txt").read_bytes()).hexdigest() == redaction["publicSha256"]

    result = {"playerObservationAudit": "PASS", "actualReplay": code,
        "primaryImagesInspected": 5, "retainedDiagnosticImages": 3,
        "probeCorrection": "Initial persistent-land OL views rejected as component proof; direct lifted/loose lead checks recorded separately",
        "behavior": "Unrepaired FAIL; supporting R2 removal/original reinstall; actual replacement and original measurements; repaired customer PASS; final OFF/tray1",
        "cleanup": "All11 current recorded births exited, owned profile absent, maintained preview stop and released8907",
        "limits": visual["limits"],
        "publication": "Pending root final diff/privacy/staged bytes/normal branch push; no main/email"}
    (HERE / "player-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS actual public references/supporting service/direct measurements/customer FAIL->PASS; five primary raw views and three diagnostic frames")


if __name__ == "__main__":
    main()
