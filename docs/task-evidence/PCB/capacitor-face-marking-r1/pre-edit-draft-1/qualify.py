"""Check exact paint-only source boundaries and real native/build receipts."""
import copy
import difflib
import hashlib
import json
import runpy
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Capacitor face qualification requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
MARKERS = ("PCB silkscreen scale contracts: ", "bench instrument presentation contracts ",
           "P01 physical pose contracts ", "U01 viewport contracts ",
           "power service-part contracts ", "physical serviceability contracts ",
           "U06 session save contracts ", "PCB normal packing identity contracts ",
           "Q30 physical metadata contracts ")
FOOTER = "PASS: focused current contracts; 9 Java suites. Full matrix and independent oracles NOT RUN."


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def framing(receipt, log, timings):
    assert all(receipt.count(marker) == 1 for marker in MARKERS)
    assert log.count(FOOTER) == 1
    assert set(timings) == {"contracts", "build"}
    assert all(v["status"] == "PASS" and v["exitCode"] == 0 for v in timings.values())


def canaries():
    receipt = "\n".join(MARKERS)
    timings = {p: {"status": "PASS", "exitCode": 0} for p in ("contracts", "build")}
    framing(receipt, FOOTER, timings)
    cases = [{"case": "synthetic complete framing", "result": "PASS"}]
    failed = copy.deepcopy(timings)
    failed["contracts"]["exitCode"] = 2
    for name, r, log, value in (
            ("missing suite", receipt.replace(MARKERS[0], "missing"), FOOTER, timings),
            ("duplicate suite", receipt + MARKERS[0], FOOTER, timings),
            ("wrong footer", receipt, FOOTER.replace("9 Java", "8 Java"), timings),
            ("exit2", receipt, FOOTER, failed)):
        try:
            framing(r, log, value)
        except AssertionError:
            cases.append({"case": name, "result": "PASS_REJECTED"})
        else:
            raise RuntimeError("Invalid framing accepted: " + name)
    (HERE / "gate-canaries.json").write_text(json.dumps({"status": "PASS", "cases": cases,
        "limits": "Pure synthetic framing cases; no native/build/OS execution inferred"}, indent=2) + "\n", encoding="utf-8")
    print("PASS five pure framing canaries; no execution claim")


def main():
    if sys.argv[1:] == ["--canaries"]:
        canaries()
        return
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    baseline = read(HERE / "baseline-inputs.json")
    candidate = read(HERE / "candidate-inputs.json")
    assert inputs["record"]() == candidate
    changed = {p for p in candidate["files"] if candidate["files"][p] != baseline["files"][p]}
    assert changed == inputs["CHANGED"]
    for phase, manifest in (("baseline", baseline), ("candidate", candidate)):
        archive = HERE / (phase + "-source")
        assert {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob("*") if p.is_file()} == {
            p: manifest["files"][p] for p in changed}
    boundary = []
    for name in sorted(changed):
        old = (HERE / "baseline-source" / name).read_text(encoding="utf-8")
        new = (HERE / "candidate-source" / name).read_text(encoding="utf-8")
        if name.endswith("StandardPhysicalPartRenderProviders.java"):
            old_call = ('            WorkbenchVisualTheme.marking(graphics, specification.getNameplate().getMarking(),\n'
                        '                b, Math.max(8, context.scale(10)), WorkbenchVisualTheme.SILK);\n')
            new_call = ('            Rectangle markingBounds = WorkbenchVisualTheme.electrolyticMarkingBounds(b);\n'
                        '            if (markingBounds != null)\n'
                        '                WorkbenchVisualTheme.marking(graphics, specification.getNameplate().getMarking(),\n'
                        '                    markingBounds, Math.max(8, context.scale(10)), WorkbenchVisualTheme.SILK);\n')
            assert old.count(old_call) == 1 and new == old.replace(old_call, new_call, 1)
            boundary.append({"path": name, "boundary": "Only original complete-marking call receives bounded face rectangle"})
        else:
            operations = difflib.SequenceMatcher(None, old.splitlines(), new.splitlines(), autojunk=False).get_opcodes()
            assert all(op in ("equal", "insert") for op, *_ in operations), name
            boundary.append({"path": name, "boundary": "Insertion only; every existing line/assertion unchanged"})
    timings = {p: read(HERE / (p + "-timing.json")) for p in ("contracts", "build")}
    for phase in timings:
        launch_path = HERE / (phase + "-process-launch.json")
        launch = read(launch_path)
        cleanup = read(HERE / (phase + "-process-cleanup.json"))
        assert launch["phase"] == cleanup["phase"] == phase and cleanup["status"] == "PASS"
        assert cleanup["launchSha256"] == sha(launch_path)
        assert len(launch["drivers"]) == len(cleanup["checks"]) == 2
        assert len({p["pid"] for p in launch["drivers"]}) == 2
        assert [{k: row[k] for k in ("pid", "startTicks", "executable")} for row in cleanup["checks"]] == launch["drivers"]
        assert all(row["recordedBirthExited"] is True for row in cleanup["checks"])
        for side in ("before", "after"):
            assert (HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip() == (
                "PASS capacitor face exact input audit phase=candidate sources=1481 gates=4")
    receipt = (HERE / "contracts-receipt.txt").read_text(encoding="utf-8-sig")
    log = (HERE / "contracts.txt").read_text(encoding="utf-8-sig")
    framing(receipt, log, timings)
    build = (HERE / "build.txt").read_text(encoding="utf-8-sig")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1
    result = {"nativeBuildQualification": "PASS", "sourceCount": 1481,
        "freshNativeSuites": 9, "actualGwtPermutations": 5, "sourceBoundaries": boundary,
        "operationSeconds": {p: t["elapsedSeconds"] for p, t in timings.items()},
        "reuse": "Prior fixed44 physical/graph/metric/work evidence bound; every non-paint/native-extension source and machinery input exact",
        "player": "Fresh actual RC/multi-rail marking/service evidence still required separately",
        "limits": "No fresh fixed44/full unrelated matrix, unseen/all-seed, pixel, manufacturing or new-size claim"}
    (HERE / "native-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS paint-only source boundary/native9/final actual JDK8GWT5 and launch-bound cleanup")


if __name__ == "__main__":
    main()
