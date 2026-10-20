"""Audit read-only fresh New board screenshots without broadening their claim."""
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Fresh board observation audit requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    prior = HERE.parent / "empty-chip-silk-r1"
    inputs = runpy.run_path(str(prior / "inputs.py"))
    assert inputs["record"]() == read(prior / "candidate-inputs.json")
    for side in ("before", "after"):
        assert (HERE / ("reused-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip() == (
            "PASS empty chip silk exact input audit phase=candidate sources=1481 gates=4")
    assert read(HERE / "build-reuse.json")["status"] == "PASS"
    expected, served, post = (read(HERE / name) for name in (
        "preview-expected.json", "player-served.json", "post-player-provenance.json"))
    fields = {"SourceDigest": "data-tsj-preview-source-digest", "ScriptDigest": "data-tsj-preview-script-digest",
              "WebDigest": "data-tsj-preview-web-digest", "Digest": "data-tsj-preview-execution-digest",
              "FileCount": "data-tsj-preview-file-count"}
    assert expected == post == read(prior / "post-player-provenance.json")
    assert set(expected) == set(fields) | {"Protocol"}
    assert all(str(expected[k]) == served[v] for k, v in fields.items())
    visual = read(HERE / "visual-review.json")
    assert visual["status"] == "OBSERVED" and visual["actualRawImagesInspected"] == 5
    assert [c["file"] for c in visual["rawCaptures"]] == ["random-mosfet.jpg", "random-relay.jpg",
        "random-two-channel.jpg", "random-multirail.jpg", "random-multirail-inspection.jpg"]
    for c in visual["rawCaptures"]:
        raw = (HERE / c["file"]).read_bytes()
        assert len(raw) == c["bytes"] and hashlib.sha256(raw).hexdigest() == c["sha256"]
    events = read(HERE / "player-events.json")
    assert [e["attempt"] for e in events if "action" in e] == [1, 2, 3, 4]
    assert [e["attempt"] for e in events if e.get("event") == "Customer ticket visible"] == [1, 2, 3, 4]
    assert [e["observedReplay"] for e in events if "observedReplay" in e] == [
        "tsj-alpha/6/EASY/NMOS_LOW_SIDE_SWITCH/-7281719014759016657",
        "tsj-alpha/6/EASY/RELAY_OUTPUT/-2859644269863457366",
        "tsj-alpha/6/MEDIUM/COMPOSED_CONTROLLED_INDICATOR/-1531998593498751668",
        "tsj-alpha/6/MEDIUM/RB30_CONTROL/-5864181845794033320"]
    assert len([e for e in events if e.get("status") == "AUTOMATION_SELECTOR_TIMEOUT"]) == 1
    logs = read(HERE / "browser-logs.json")
    assert not logs["pageErrors"] and not logs["failedResponses"]
    assert len(logs["console"]) == 2 and all(e["type"] == "error" and "404" in e["text"] and
        e["location"]["url"].endswith("/favicon.ico") for e in logs["console"])
    for name in ("pre-close-browser-check.json", "browser-api-cleanup.json", "preview-stop-timing.json", "process-resource-cleanup.json"):
        assert read(HERE / name)["status"] == "PASS"
    assert read(HERE / "browser-api-cleanup.json")["rootExitObserved"] is True
    cleanup = read(HERE / "process-resource-cleanup.json")
    assert cleanup["newProfileAbsent"] is True and cleanup["preview8907Listening"] is False
    assert all(c["recordedBirthExited"] for c in cleanup["checks"])
    body = (HERE / "final-off-body.txt").read_text(encoding="utf-8")
    menu = (HERE / "final-menu-dom.txt").read_text(encoding="utf-8")
    assert "Board Power: OFF" in body and "PARTS TRAY · 0 parts" in body
    assert "Start a service job" in menu and "Resume current board" in menu
    result = {"observationAudit": "PASS", "sourceCountUnchanged": 1481,
        "freshRandomLaunches": 4, "actualPublicIdentitiesRecorded": 4, "rawImagesInspected": 5,
        "build": "Reused only after exact1481 and source/script/web/execution/count equality before/after",
        "nextConcreteQuestion": "Unique public component references on the composed board; stable graph IDs must remain intact",
        "limits": visual["limits"], "findings": visual["findings"],
        "cleanup": "PASS new owned resources; earlier policy-blocked unused directory remains separate",
        "publication": "Pending root diff/privacy/staged-byte audit and normal existing-branch push; no main/email"}
    (HERE / "acceptance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS four fresh ordinary launches/five inspected views; unchanged build and current resource cleanup")


if __name__ == "__main__":
    main()
