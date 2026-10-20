"""Keep the exact paired44 physical oracle; vary only composed public ink text."""
import json
import runpy
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Reference qualification requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
FAMILIES = ("LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
    "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT", "SENSOR_CONTROL",
    "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL")
ROOTS = ("0", "17", "13", "-9223372036854775808")
COMPOSED = "COMPOSED_CONTROLLED_INDICATOR"
COUNTERS = {"RESISTOR": "R", "LED": "LED", "NPN_TRANSISTOR": "Q", "NMOS_TRANSISTOR": "Q"}
MARKERS = ("A04PhysicalDeclarationContractTest assertions=", "A04ConstructionContractTest assertions=",
    "current controlled-indicator contracts ", "PCB normal packing identity contracts ",
    "U06 session save contracts ", "PlayerSessionFingerprintContractTest assertions=",
    "actual RB56 geometry report ", "A02ReplayContractTest assertions=", "PCB packing probe receipt contracts ",
    "PCB packing metrics contracts ", "PCB silkscreen scale contracts: ", "PcbScreenLabelLayoutContractTest: ",
    "E05 isolation geometry contracts ")


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def text(name):
    return (HERE / name).read_text(encoding="utf-8-sig")


def cases(path, marker):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith(marker):
            row = json.loads(line[len(marker):])
            key = row["family"], row["root"]
            assert key not in result, "duplicate case"
            result[key] = row
    assert set(result) == {(f, r) for f in FAMILIES for r in ROOTS}, "fixed44 coverage differs"
    return result


def success(phase):
    timing = read(phase + "-timing.json")
    assert timing["status"] == "PASS" and timing["exitCode"] == 0
    assert read(phase + "-process-cleanup.json")["status"] == "PASS"
    input_phase = "baseline" if phase == "baseline" else "candidate"
    for side in ("before", "after"):
        assert text(phase + "-input-audit-" + side + ".txt").strip() == (
            "PASS reference exact input audit phase=" + input_phase + " sources=1481 gates=4")
    return timing


def cohort(phase):
    timing = success(phase)
    assert text(phase + ".txt").count(
        "PASS: focused current contracts; 8 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    receipt = HERE / (phase + "-receipt.txt")
    rows = cases(receipt, "PCB_NORMAL_PACKING_CASE ")
    refs = cases(receipt, "PCB_NORMAL_REFERENCE_CASE ")
    titles = cases(receipt, "PCB_NORMAL_SILK_CASE ")
    for row in rows.values():
        assert row["status"] == "ACCEPTED" and row["graphBefore"] == row["graphAfter"]
        assert row["layoutAlgorithmVersion"] == 17
    excluded = {"geometrySha256", "elapsedMillis", "maxAdvanceMillis", "generationRoutingMillis"}
    if phase == "baseline":
        prior = cases(HERE.parent / "normal-silkscreen-title-r1/candidate-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
        for key, row in rows.items():
            assert set(row) == set(prior[key])
            assert all(row[k] == prior[key][k] for k in row if k not in excluded - {"geometrySha256"}), key
    else:
        baseline = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
        before_refs = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_REFERENCE_CASE ")
        before_titles = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_SILK_CASE ")
        for key, row in rows.items():
            old = baseline[key]
            assert set(row) == set(old)
            assert all(row[k] == old[k] for k in row if k not in excluded), ("physical/graph/work/oracle changed", key)
            a, b = before_refs[key], refs[key]
            assert a["geometryApartFromReferenceTextSha256"] == b["geometryApartFromReferenceTextSha256"], key
            assert a["physicalBoardSha256"] == b["physicalBoardSha256"], key
            assert len(a["components"]) == len(b["components"])
            count, expected_names = {}, {}
            for first, second in zip(a["components"], b["components"]):
                assert set(first) == set(second) == {"id", "type", "name"}
                assert first["id"] == second["id"] and first["type"] == second["type"]
                expected = first["name"]
                if key[0] == COMPOSED:
                    prefix = COUNTERS.get(first["type"])
                    assert prefix is not None or first["type"] == "CONNECTOR"
                    if prefix is not None:
                        count[prefix] = count.get(prefix, 0) + 1
                        expected = prefix + str(count[prefix])
                assert second["name"] == expected, ("wrong public reference", key, second)
                expected_names["component:" + second["id"]] = expected
            if key[0] == COMPOSED:
                assert len({r["name"] for r in b["components"]}) == len(b["components"])
                assert count["Q"] == 2 and count["LED"] == 3 and 1 <= count["R"] <= 9
            assert len(a["labels"]) == len(b["labels"])
            for first, second in zip(a["labels"], b["labels"]):
                assert set(first) == set(second) == {"id", "text", "bounds", "fontSize", "bold", "targetPadId"}
                assert all(first[k] == second[k] for k in first if k != "text"), ("label reservation/style/target changed", key)
                expected = expected_names.get(first["id"], first["text"])
                assert second["text"] == expected
            changed = a["components"] != b["components"]
            assert changed == (key[0] == COMPOSED)
            assert (row["geometrySha256"] != old["geometrySha256"]) == changed
            assert {k: v for k, v in titles[key].items() if k != "geometryApartFromTitleSha256"} == {
                k: v for k, v in before_titles[key].items() if k != "geometryApartFromTitleSha256"}
            if not changed:
                assert a == b and titles[key] == before_titles[key]
    value = {"status": "PASS", "phase": phase, "cases": 44, "families": 11,
        "elapsedSeconds": timing["elapsedSeconds"],
        "physicalOracle": "Exact graph, physical identity, routing recovery, geometry, whitespace and deterministic work; only composed reference text may vary",
        "limits": "Fixed informed corpus; no unseen/all-seed/manufacturing/performance proof"}
    (HERE / (phase + "-assessment.json")).write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    print("PASS exact reference cohort " + phase + " cases=44")


def main():
    phase = sys.argv[1]
    assert phase in ("baseline", "candidate", "final")
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    frozen_phase = "baseline" if phase == "baseline" else "candidate"
    assert inputs["record"]() == read(frozen_phase + "-inputs.json")
    if phase != "final":
        cohort(phase)
        return
    cohort("candidate")
    for name in ("contracts", "build"):
        success(name)
    receipt = text("contracts-receipt.txt")
    assert all(receipt.count(marker) == 1 for marker in MARKERS)
    assert text("contracts.txt").count(
        "PASS: focused current contracts; 13 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    build = text("build.txt")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1
    result = {"status": "PASS", "pairedCases": 44, "focusedSuites": 13, "actualJdk8GwtPermutations": 5,
        "operationSeconds": {p: read(p + "-timing.json")["elapsedSeconds"] for p in ("baseline", "candidate", "contracts", "build")},
        "player": "Separate fresh actual composed-reference/service/repair evidence still required",
        "limits": "No full unrelated matrix, FileReader, unseen/all-seed or physical size change claim"}
    (HERE / "native-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS paired44 exact physical oracle/focused13/final JDK8GWT5; player gate separate")


if __name__ == "__main__":
    main()
