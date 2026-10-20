"""Supplement the original comparison with recovery-work and static style proof.

No native input or original gate changes. Full geometry fingerprints include
label text/bounds, not font/bold attributes; those are checked at unchanged
source owners and call sites, then exercised visually in the production player.
"""
import copy
import hashlib
import json
import runpy
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
BASE = "438ac2e2aba6e433a192c29807f24ef283b2c404"
GEN = "src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java"
TITLE_SELECTION = '''        String fallbackTitle;
        if (board.getId().equals("RB15_CONTROL_BOARD"))
            fallbackTitle = "TSJ CONTROL BOARD";
        else if (board.getId().equals("DIODE_PROTECTED_INDICATOR"))
            fallbackTitle = "TSJ DIODE INDICATOR";
        else if (board.getId().equals("PARALLEL_DUAL_INDICATOR"))
            fallbackTitle = "TSJ PARALLEL INDICATORS";
        else
            fallbackTitle = "TSJ LED INDICATOR";
        String title = board.getSilkscreenTitle();
        if (title == null) title = fallbackTitle;
        // A public title uses the existing family header slot. Shorter ink must
        // not move other labels, change cropping, or alter packing occupancy.
        int titleWidth = Math.max(textWidth(title, 14), textWidth(fallbackTitle, 14));
'''
LINES = {
    "RcDelayGenerator.java": '        board.setSilkscreenTitle("TSJ RC DELAY");\n',
    "SensorControlGenerator.java": '        board.setSilkscreenTitle("TSJ SENSOR CTRL");\n',
    "NpnLowSideSwitchGenerator.java": '        board.setSilkscreenTitle("TSJ NPN SWITCH");\n',
    "NmosLowSideSwitchGenerator.java": '        board.setSilkscreenTitle("TSJ NMOS SWITCH");\n',
    "RelayOutputGenerator.java": '        if (plan == null) a.board.setSilkscreenTitle("TSJ RELAY OUTPUT");\n',
    "PhysicalConstructionMaterializer.java": '        if (plan.isControlledIndicator()) board.setSilkscreenTitle("TSJ CTRL IND");\n',
}
reader = runpy.run_path(str(HERE / "assess.py"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def compare(first, second, first_ink, second_ink):
    reader["compare"](first, second, first_ink, second_ink)
    for key in first:
        assert "routingRecovery" in first[key] and "routingRecovery" in second[key]
        assert first[key]["routingRecovery"] == second[key]["routingRecovery"], (
            "complete deterministic routing recovery receipt differs", key)


def styles(overrides=None):
    overrides = overrides or {}
    baseline = read(HERE / "baseline-inputs.json")
    candidate = read(HERE / "candidate-inputs.json")
    current = runpy.run_path(str(HERE / "inputs.py"))["record"]()
    assert current == candidate, "actual candidate source/gates drifted"
    changes = {name for name in baseline["files"]
               if baseline["files"][name] != candidate["files"][name]}
    expected = {GEN} | {"src/com/lushprojects/circuitjs1/client/" + name for name in LINES}
    assert changes == expected, "only seven title owners may change"
    assert baseline["gates"] == candidate["gates"], "original four gates changed"
    for path in sorted(expected):
        original = subprocess.check_output(["git", "show", BASE + ":" + path], cwd=ROOT)
        assert baseline["files"][path] in {sha(original), sha(original.replace(b"\n", b"\r\n"))}, (
            "baseline raw code differs from published reference", path)
        original = original.decode("utf-8").replace("\r\n", "\n")
        text = overrides.get(path, (ROOT / path).read_text(encoding="utf-8"))
        if path != GEN:
            line = LINES[Path(path).name]
            assert text.count(line) == 1, "metadata change is not the declared single line"
            erased = text.replace(line, "")
        else:
            start = text.index("        String fallbackTitle;\n")
            end = text.index("        int componentLeft =", start)
            assert text[start:end] == TITLE_SELECTION, "title block differs from the declared text/slot-only logic"
            prior_start = original.index("        String title = board.getSilkscreenTitle();\n")
            prior_end = original.index("        int componentLeft =", prior_start)
            erased = text[:start] + original[prior_start:prior_end] + text[end:]
            width = "            titleWidth, 18));"
            assert erased.count(width) == 1
            erased = erased.replace(width, "            textWidth(title, 14), 18));")
        assert erased == original, "non-title source/style change: " + path
    return {"status": "PASS", "changedSourceOwners": sorted(expected),
            "proof": "Seven exact erasures restore published code. All label font/bold/baseline arguments, style models and renderer owners remain unchanged.",
            "limit": "Static source proof; actual glyph ink fit is a separate visible-player check"}


def main():
    cases = reader["cases"]
    first = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
    second = cases(HERE / "candidate-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
    first_ink = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_SILK_CASE ")
    second_ink = cases(HERE / "candidate-receipt.txt", "PCB_NORMAL_SILK_CASE ")
    accepted = cases(HERE.parent / "normal-packing-r2/routes-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
    assert all(first[key]["routingRecovery"] == accepted[key]["routingRecovery"] for key in first)
    compare(first, second, first_ink, second_ink)
    style = styles()
    canaries = [{"name": "actual complete paired44 and static style owners", "expected": "PASS", "result": "PASS"}]

    def rejects(name, operation):
        try:
            operation()
        except (AssertionError, KeyError, ValueError):
            canaries.append({"name": name, "expected": "REJECT", "result": "PASS"})
            return
        raise AssertionError("negative qualification accepted: " + name)

    key = ("RC_DELAY", "13")
    for field, value in (("routingRecovery", "tampered"), ("whitespace", second[key]["whitespace"] + 100),
                         ("graphAfter", "0" * 64)):
        bad = copy.deepcopy(second)
        bad[key][field] = value
        rejects("changed " + field, lambda bad=bad: compare(first, bad, first_ink, second_ink))
    bad = copy.deepcopy(second)
    del bad[key]["routingRecovery"]
    rejects("missing deterministic recovery receipt", lambda: compare(first, bad, first_ink, second_ink))
    for field, value in (("title", "TSJ LED INDICATOR"), ("bounds", [0, 0, 153, 18]),
                         ("geometryApartFromTitleSha256", "0" * 64)):
        bad_ink = copy.deepcopy(second_ink)
        bad_ink[key][field] = value
        rejects("changed " + field, lambda bad_ink=bad_ink: compare(first, second, first_ink, bad_ink))
    text = (ROOT / GEN).read_text(encoding="utf-8")
    slot = "            titleBounds, 14, true, null);"
    assert text.count(slot) == 1
    for replacement in ("            titleBounds, 13, true, null);", "            titleBounds, 14, false, null);"):
        rejects("changed title style: " + replacement.strip(),
                lambda replacement=replacement: styles({GEN: text.replace(slot, replacement)}))

    class TextSource:
        def __init__(self, text):
            self.text = text

        def read_text(self, **kwargs):
            return self.text

    rows = ["PCB_NORMAL_SILK_CASE " + json.dumps(row) for row in second_ink.values()]
    rejects("duplicate observed family/root", lambda: cases(TextSource("\n".join(rows + rows[:1])), "PCB_NORMAL_SILK_CASE "))
    rejects("missing observed family/root", lambda: cases(TextSource("\n".join(rows[:-1])), "PCB_NORMAL_SILK_CASE "))
    result = {"status": "PASS", "cases": 44, "routingRecovery": "PASS; full deterministic canonical equality",
              "geometry": "PASS; full fingerprint apart from exactly board-title text, including all label bounds/text",
              "styles": style, "qualificationCases": canaries, "caseCount": len(canaries),
              "nativeEvidence": "Reused; source1481 and original four gates unchanged after native runs",
              "originalReader": "assess.py retained unchanged; two additional acceptance requirements supplied here",
              "limits": "Font/bold source proof is static; production glyph ink-fit and final build remain separate"}
    (HERE / "qualification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS title qualification actual44; canaries=" + str(len(canaries)))


if __name__ == "__main__":
    main()
