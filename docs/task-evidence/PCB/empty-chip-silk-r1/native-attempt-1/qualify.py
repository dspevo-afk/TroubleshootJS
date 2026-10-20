"""Correct receipt/footer framing without changing frozen gates or native data."""
import copy
import hashlib
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Empty-chip silk qualification requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
MARKERS = ("PCB silkscreen scale contracts: ", "bench instrument presentation contracts ",
           "P01 physical pose contracts ", "U01 viewport contracts ",
           "power service-part contracts ", "physical serviceability contracts ",
           "U06 session save contracts ", "PCB normal packing identity contracts ",
           "Q30 physical metadata contracts ")


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def framing(receipt, log, timings):
    for marker in MARKERS:
        assert receipt.count(marker) == 1, "missing/duplicate native suite marker"
    assert log.count("PASS: focused current contracts; 9 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    assert set(timings) == {"contracts", "build"}
    assert all(t["status"] == "PASS" and t["exitCode"] == 0 for t in timings.values())


def main():
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    candidate, baseline = read(HERE / "candidate-inputs.json"), read(HERE / "baseline-inputs.json")
    assert inputs["record"]() == candidate
    assert set(baseline["files"]) == set(candidate["files"])
    assert baseline["gates"] == candidate["gates"]
    delta = {name for name in baseline["files"] if baseline["files"][name] != candidate["files"][name]}
    assert delta == inputs["CHANGED"]
    archive = HERE / "candidate-source"
    archived = {p.relative_to(archive).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in archive.rglob("*") if p.is_file()}
    assert archived == {name: candidate["files"][name] for name in delta}
    timings = {phase: read(HERE / (phase + "-timing.json")) for phase in ("contracts", "build")}
    for phase in timings:
        assert read(HERE / (phase + "-process-cleanup.json"))["status"] == "PASS"
        for side in ("before", "after"):
            assert (HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip() == (
                "PASS empty chip silk exact input audit phase=candidate sources=1481 gates=4")
    receipt = (HERE / "contracts-receipt.txt").read_text(encoding="utf-8-sig")
    log = (HERE / "contracts.txt").read_text(encoding="utf-8-sig")
    framing(receipt, log, timings)
    cases = [{"case": "actual nine receipt markers and separate driver footer", "result": "PASS"}]
    bad_timing = copy.deepcopy(timings)
    bad_timing["contracts"]["exitCode"] = 2
    for name, r, l, t in (
            ("missing suite", receipt.replace(MARKERS[0], "omitted "), log, timings),
            ("duplicate suite", receipt + MARKERS[0], log, timings),
            ("wrong driver count", receipt, log.replace("9 Java suites", "8 Java suites"), timings),
            ("failed operation", receipt, log, bad_timing)):
        try:
            framing(r, l, t)
        except AssertionError:
            cases.append({"case": name, "result": "PASS_REJECTED"})
        else:
            raise RuntimeError("Negative framing canary accepted: " + name)
    build = (HERE / "build.txt").read_text(encoding="utf-8-sig")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --",
                   "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1
    result = {"nativeBuildQualification": "PASS", "sourceCount": 1481, "originalGates": 4,
              "changedOwners": sorted(delta), "operationSeconds": {p: v["elapsedSeconds"] for p, v in timings.items()},
              "qualificationCases": cases, "qualifierSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "framing": "Nine suite markers are checked in the actual receipt; the maintained driver footer is checked separately in its log",
              "reusedBoundary": "All generation/layout/solver/identity/physical-geometry/graph/mutation/save owners unchanged; no fresh fixed44 claim",
              "player": "Separate actual normal empty-chip silk/removal/reinstall evidence required",
              "limits": "No full unrelated matrix, all-seed/manufacturing or fresh functional-repair claim"}
    (HERE / "native-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS native/build framing; five canaries; frozen inputs/gates exact")


if __name__ == "__main__":
    main()
