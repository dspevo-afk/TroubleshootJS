"""Check native/build receipts and the frozen non-generation boundary."""
import json
import runpy
from pathlib import Path

if not __debug__:
    raise RuntimeError("Reference-mark assessment requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    frozen = read(HERE / "candidate-inputs.json")
    assert inputs["record"]() == frozen
    before = read(HERE / "baseline-inputs.json")
    assert before["gates"] == frozen["gates"]
    assert set(before["files"]) == set(frozen["files"])
    delta = {name for name in before["files"] if before["files"][name] != frozen["files"][name]}
    assert delta == inputs["CHANGED"]
    timings = {}
    for phase in ("contracts", "build"):
        value = read(HERE / (phase + "-timing.json"))
        assert value["status"] == "PASS" and value["exitCode"] == 0
        assert read(HERE / (phase + "-process-cleanup.json"))["status"] == "PASS"
        for side in ("before", "after"):
            assert (HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig").strip() == (
                "PASS chip reference exact input audit phase=candidate sources=1481 gates=4")
        timings[phase] = value["elapsedSeconds"]
    native = (HERE / "contracts-receipt.txt").read_text(encoding="utf-8-sig")
    for marker in ("bench instrument presentation contracts ", "P01 physical pose contracts ",
                   "architecture footprint contracts ", "U01 viewport contracts ",
                   "lifted lead appearance contracts ", "U06 session save contracts ",
                   "PCB normal packing identity contracts ", "Q30 physical metadata contracts "):
        assert native.count(marker) == 1, "missing/duplicate maintained native marker: " + marker
    assert "8 Java suites" in native
    build = (HERE / "build.txt").read_text(encoding="utf-8-sig")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --",
                   "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1, "missing/duplicate GWT marker"
    result = {"nativeBuildQualification": "PASS", "sourceCount": 1481, "gates": 4,
              "changedOwners": sorted(delta), "operationSeconds": timings,
              "reusedBoundary": "All generation, layout, solver, identity, geometry, graph, mutation and save owners unchanged; prior fixed44 proof remains its own frozen artifact",
              "player": "PENDING real normal context-menu and dot/pinout captures",
              "limits": "No fresh full matrix, all-seed/manufacturing or fresh repair claim"}
    (HERE / "native-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS chip reference native/build evidence; actual player evidence separate")


if __name__ == "__main__":
    main()
