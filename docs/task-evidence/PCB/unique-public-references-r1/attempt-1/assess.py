"""Keep the exact paired44 physical oracle; vary only composed public ink text."""
import hashlib
import json
import re
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
    "E05 isolation geometry contracts ", "D01 diagnostic contracts ", "RB56 model-identity contracts ")


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
    launch = read(phase + "-process-launch.json")
    cleanup = read(phase + "-process-cleanup.json")
    assert launch["phase"] == cleanup["phase"] == phase and cleanup["status"] == "PASS"
    assert cleanup["launchSha256"] == hashlib.sha256((HERE / (phase + "-process-launch.json")).read_bytes()).hexdigest()
    assert len(launch["drivers"]) == len(cleanup["checks"]) == 2
    assert len({d["pid"] for d in launch["drivers"]}) == 2
    assert [{k: r[k] for k in ("pid", "startTicks", "executable")} for r in cleanup["checks"]] == launch["drivers"]
    assert all(r["recordedBirthExited"] is True for r in cleanup["checks"])
    input_phase = "baseline" if phase == "baseline" else "candidate"
    for side in ("before", "after"):
        assert text(phase + "-input-audit-" + side + ".txt").strip() == (
            "PASS reference exact input audit phase=" + input_phase + " sources=1481 gates=4")
    return timing


def inventory(value, row):
    assert set(value) == {"family", "root", "components", "labels", "labelCount", "physicalBoardSha256", "geometryApartFromReferenceTextSha256"}
    for field in ("physicalBoardSha256", "geometryApartFromReferenceTextSha256"):
        assert isinstance(value[field], str) and re.fullmatch(r"[0-9a-f]{64}", value[field]), field
    components, labels = value["components"], value["labels"]
    assert type(value["labelCount"]) is int and value["labelCount"] == len(labels)
    assert len(components) == row["logicalParts"] == row["layoutParts"]
    component_ids = [c["id"] for c in components]
    label_ids = [c["id"] for c in labels]
    assert len(set(component_ids)) == len(component_ids) and component_ids == sorted(component_ids)
    assert len(set(label_ids)) == len(label_ids) and label_ids == sorted(label_ids)
    assert {"component:" + name for name in component_ids} <= set(label_ids)
    assert len([name for name in label_ids if name.startswith("component:")]) == len(component_ids)
    for item in components:
        assert set(item) == {"id", "type", "name"} and all(isinstance(v, str) and v for v in item.values())
    for label in labels:
        assert set(label) == {"id", "text", "bounds", "fontSize", "bold", "targetPadId"}
        assert isinstance(label["text"], str) and label["text"]
        assert len(label["bounds"]) == 4 and all(type(v) is int for v in label["bounds"])
        assert label["bounds"][2] > 0 and label["bounds"][3] > 0
        assert type(label["fontSize"]) is int and label["fontSize"] > 0 and type(label["bold"]) is bool
        assert label["targetPadId"] is None or isinstance(label["targetPadId"], str)
    by_label = {v["id"]: v for v in labels}
    assert all(by_label["component:" + c["id"]]["text"] == c["name"] for c in components)
    if row["family"] == COMPOSED:
        assert sum(c["type"] == "RESISTOR" for c in components) == 7


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
        key = row["family"], row["root"]
        inventory(refs[key], row)
        title = titles[key]
        assert set(title) == {"family", "root", "title", "bounds", "geometryApartFromTitleSha256"}
        assert isinstance(title["title"], str) and re.fullmatch(r"[0-9a-f]{64}", title["geometryApartFromTitleSha256"])
        board_titles = [l for l in refs[key]["labels"] if l["id"] == "board-title"]
        assert len(board_titles) == 1
        board_title = board_titles[0]
        assert board_title["text"] == title["title"] and board_title["bounds"] == title["bounds"]
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
            inventory(a, old)
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
                assert count == {"Q": 2, "LED": 3, "R": 7}
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
    for name in (("baseline", "candidate") if phase == "final" else (frozen_phase,)):
        manifest = read(name + "-inputs.json")
        accepted = read("accepted-inputs.json")["files"]
        changed = {p: v for p, v in manifest["files"].items() if accepted[p] != v}
        archive = HERE / (name + "-source")
        assert {p.relative_to(archive).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in archive.rglob("*") if p.is_file()} == changed
    if phase != "final":
        cohort(phase)
        return
    cohort("candidate")
    for name in ("contracts", "build"):
        success(name)
    receipt = text("contracts-receipt.txt")
    assert all(receipt.count(marker) == 1 for marker in MARKERS)
    assert text("contracts.txt").count(
        "PASS: focused current contracts; 15 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    build = text("build.txt")
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert build.count(marker) == 1
    result = {"status": "PASS", "pairedCases": 44, "focusedSuites": 15, "actualJdk8GwtPermutations": 5,
        "operationSeconds": {p: read(p + "-timing.json")["elapsedSeconds"] for p in ("baseline", "candidate", "contracts", "build")},
        "player": "Separate fresh actual composed-reference/service/repair evidence still required",
        "limits": "No full unrelated matrix, FileReader, unseen/all-seed or physical size change claim"}
    (HERE / "native-assessment.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS paired44 exact physical oracle/focused15/final JDK8GWT5; player gate separate")


if __name__ == "__main__":
    main()
