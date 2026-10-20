"""Keep the fixed44 physical oracle; permit only declared RB30 reference ink."""
import copy
import difflib
import hashlib
import json
import re
import runpy
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Multi-rail reference qualification requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
FAMILIES = ("LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
    "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT", "SENSOR_CONTROL",
    "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL")
ROOTS = ("0", "17", "13", "-9223372036854775808")
TARGET = "RB30_CONTROL"
# Independent literal contract, not a traversal of the production board HashMap.
DECLARATIONS = ("J1", "F1", "DREV", "C12", "U1", "CIN", "C5", "REN", "RBLEED5",
    "U2A", "RSA", "RPIN_A", "CFLT_A", "U2B", "RSB", "RPIN_B", "CFLT_B",
    "RREF_H", "RREF_L", "RFB_A", "RFB_B", "RREF_HA", "RREF_LA", "RREF_HB", "RREF_LB",
    "RDA", "RPDA", "QA", "DA", "KA", "RDB", "RPDB", "QB", "DB", "KB",
    "RLED", "LED1", "RLED12", "LED12", "RLEDOUT_A", "LEDOUT_A", "RLEDOUT_B", "LEDOUT_B",
    "JSA", "JOA", "JSB", "JOB", "JLOAD")
TIMING = {"elapsedMillis", "maxAdvanceMillis", "generationRoutingMillis"}
MARKERS = ("Q30 plan contracts ", "Q30 physical metadata contracts ",
    "Q30 service flow contracts ", "Q30 relay service contracts ",
    "PCB normal packing identity contracts ", "U06 session save contracts ",
    "PlayerSessionFingerprintContractTest assertions=", "A02ReplayContractTest assertions=",
    "PCB silkscreen scale contracts: ", "PcbScreenLabelLayoutContractTest: ",
    "RB56 model-identity contracts ", "D01 diagnostic contracts ", "E05 isolation geometry contracts ")


def read(name):
    return json.loads((HERE / name).read_text(encoding="utf-8-sig"))


def text(name):
    return (HERE / name).read_text(encoding="utf-8-sig")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def timing(value):
    assert value["status"] == "PASS" and value["exitCode"] == 0


def expected_references(components):
    by_id = {c["id"]: c for c in components}
    assert len(by_id) == len(components) and set(by_id) <= set(DECLARATIONS)
    assert len(DECLARATIONS) == len(set(DECLARATIONS))
    result, counters = {}, {}
    for name in DECLARATIONS:
        if name not in by_id:
            continue
        if name.startswith("J"):
            result[name] = name
            continue
        prefix = "LED" if name.startswith("LED") else name[0]
        assert prefix in ("R", "C", "U", "Q", "D", "LED", "F", "K")
        counters[prefix] = counters.get(prefix, 0) + 1
        result[name] = prefix + str(counters[prefix])
    assert set(result) == set(by_id) and len(set(result.values())) == len(result)
    return result


def cases(path, marker):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith(marker):
            row = json.loads(line[len(marker):])
            key = row["family"], row["root"]
            assert key not in result, "duplicate cohort case"
            result[key] = row
    assert set(result) == {(f, r) for f in FAMILIES for r in ROOTS}, "fixed44 differs"
    return result


def success(phase):
    value = read(phase + "-timing.json")
    timing(value)
    launch = read(phase + "-process-launch.json")
    cleanup = read(phase + "-process-cleanup.json")
    assert launch["phase"] == cleanup["phase"] == phase and cleanup["status"] == "PASS"
    assert cleanup["launchSha256"] == sha(HERE / (phase + "-process-launch.json"))
    assert len(launch["drivers"]) == len(cleanup["checks"]) == 2
    assert len({d["pid"] for d in launch["drivers"]}) == 2
    assert [{k: c[k] for k in ("pid", "startTicks", "executable")} for c in cleanup["checks"]] == launch["drivers"]
    assert all(c["recordedBirthExited"] is True for c in cleanup["checks"])
    source_phase = "baseline" if phase == "baseline" else "candidate"
    for side in ("before", "after"):
        assert text(phase + "-input-audit-" + side + ".txt").strip() == (
            "PASS multi-rail reference exact input audit phase=" + source_phase + " sources=1481 gates=4")
    return value


def inventory(value, row):
    assert set(value) == {"family", "root", "components", "labels", "labelCount", "physicalBoardSha256", "geometryApartFromReferenceTextSha256"}
    for field in ("physicalBoardSha256", "geometryApartFromReferenceTextSha256"):
        assert re.fullmatch(r"[0-9a-f]{64}", value[field])
    components, labels = value["components"], value["labels"]
    assert len(components) == row["logicalParts"] == row["layoutParts"]
    ids = [c["id"] for c in components]
    assert ids == sorted(ids) and len(set(ids)) == len(ids)
    for c in components:
        assert set(c) == {"id", "type", "name", "referenceLabelCharacters"}
        assert all(isinstance(c[k], str) and c[k] for k in ("id", "type", "name"))
        assert type(c["referenceLabelCharacters"]) is int and c["referenceLabelCharacters"] >= len(c["name"])
    assert type(value["labelCount"]) is int and value["labelCount"] == len(labels)
    label_ids = [c["id"] for c in labels]
    assert label_ids == sorted(label_ids) and len(set(label_ids)) == len(labels)
    assert {"component:" + n for n in ids} <= set(label_ids)
    assert sum(n.startswith("component:") for n in label_ids) == len(ids)
    for label in labels:
        assert set(label) == {"id", "text", "bounds", "fontSize", "bold", "targetPadId"}
        assert isinstance(label["text"], str) and label["text"]
        assert len(label["bounds"]) == 4 and all(type(n) is int for n in label["bounds"])
        assert label["bounds"][2] > 0 and label["bounds"][3] > 0
        assert type(label["fontSize"]) is int and label["fontSize"] > 0 and type(label["bold"]) is bool
        assert label["targetPadId"] is None or isinstance(label["targetPadId"], str)
    ink = {v["id"]: v for v in labels}
    assert all(ink["component:" + c["id"]]["text"] == c["name"] for c in components)


def cohort(phase):
    value = success(phase)
    assert text(phase + ".txt").count("PASS: focused current contracts; 8 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    receipt = HERE / (phase + "-receipt.txt")
    rows = cases(receipt, "PCB_NORMAL_PACKING_CASE ")
    refs = cases(receipt, "PCB_NORMAL_REFERENCE_CASE ")
    titles = cases(receipt, "PCB_NORMAL_SILK_CASE ")
    for key, row in rows.items():
        assert row["status"] == "ACCEPTED" and row["graphBefore"] == row["graphAfter"]
        assert row["layoutAlgorithmVersion"] == 17
        inventory(refs[key], row)
        title = titles[key]
        assert set(title) == {"family", "root", "title", "bounds", "geometryApartFromTitleSha256"}
        board_titles = [v for v in refs[key]["labels"] if v["id"] == "board-title"]
        assert len(board_titles) == 1 and board_titles[0]["text"] == title["title"] and board_titles[0]["bounds"] == title["bounds"]
    if phase == "baseline":
        prior_path = HERE.parent / "unique-public-references-r1/candidate-receipt.txt"
        prior = cases(prior_path, "PCB_NORMAL_PACKING_CASE ")
        prior_refs = cases(prior_path, "PCB_NORMAL_REFERENCE_CASE ")
        prior_titles = cases(prior_path, "PCB_NORMAL_SILK_CASE ")
        for key, row in rows.items():
            assert set(row) == set(prior[key])
            assert all(row[k] == prior[key][k] for k in row if k not in TIMING), key
            stripped = copy.deepcopy(refs[key])
            for c in stripped["components"]:
                del c["referenceLabelCharacters"]
            assert stripped == prior_refs[key] and titles[key] == prior_titles[key], key
            if key[0] == TARGET:
                assert all(c["name"] == c["id"] and c["referenceLabelCharacters"] == len(c["id"]) for c in refs[key]["components"])
                expected_references(refs[key]["components"])
    else:
        before = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
        old_refs = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_REFERENCE_CASE ")
        old_titles = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_SILK_CASE ")
        for key, row in rows.items():
            old = before[key]
            assert set(row) == set(old)
            assert all(row[k] == old[k] for k in row if k not in TIMING | {"geometrySha256"}), ("physical/graph/work changed", key)
            a, b = old_refs[key], refs[key]
            inventory(a, old)
            assert a["physicalBoardSha256"] == b["physicalBoardSha256"] and a["geometryApartFromReferenceTextSha256"] == b["geometryApartFromReferenceTextSha256"], key
            names = expected_references(a["components"]) if key[0] == TARGET else {c["id"]: c["name"] for c in a["components"]}
            assert len(a["components"]) == len(b["components"])
            for first, second in zip(a["components"], b["components"]):
                assert all(first[k] == second[k] for k in first if k != "name")
                assert second["name"] == names[first["id"]], ("wrong public reference", key, second)
            assert len(a["labels"]) == len(b["labels"])
            for first, second in zip(a["labels"], b["labels"]):
                assert all(first[k] == second[k] for k in first if k != "text"), ("label geometry/style/target changed", key)
                expected = names.get(first["id"][len("component:"):], first["text"]) if first["id"].startswith("component:") else first["text"]
                assert second["text"] == expected
            changed = a["components"] != b["components"]
            assert changed == (key[0] == TARGET) and (row["geometrySha256"] != old["geometrySha256"]) == changed
            assert {k: v for k, v in titles[key].items() if k != "geometryApartFromTitleSha256"} == {k: v for k, v in old_titles[key].items() if k != "geometryApartFromTitleSha256"}
            if not changed:
                assert a == b and titles[key] == old_titles[key]
    (HERE / (phase + "-assessment.json")).write_text(json.dumps({"status": "PASS", "phase": phase,
        "cases": 44, "families": 11, "elapsedSeconds": value["elapsedSeconds"],
        "oracle": "Exact physical/graph/normalized geometry/boxes/capacity/routes/metrics/work; only4 RB30 public reference texts and full ink hashes vary",
        "limits": "Fixed informed corpus; no unseen/all-seed/manufacturing/performance or new-size proof"}, indent=2) + "\n", encoding="utf-8")
    print("PASS exact multi-rail reference cohort " + phase + " cases=44")


def source_boundary(inputs):
    for phase in ("baseline", "candidate"):
        manifest = read(phase + "-inputs.json")
        changed = {n: v for n, v in manifest["files"].items() if read("accepted-inputs.json")["files"][n] != v}
        archive = HERE / (phase + "-source")
        assert {p.relative_to(archive).as_posix(): sha(p) for p in archive.rglob("*") if p.is_file()} == changed
    for n in inputs["TESTS"]:
        old = (HERE / "accepted-source" / n).read_text(encoding="utf-8")
        new = (HERE / "candidate-source" / n).read_text(encoding="utf-8")
        assert all(op in ("equal", "insert") for op, *_ in difflib.SequenceMatcher(None, old.splitlines(), new.splitlines(), autojunk=False).get_opcodes()), n
    # Production source byte seals plus exact root integrated diff review cover its three owners.
    assert read("baseline-inputs.json")["files"] == {n: (read("baseline-inputs.json")["files"][n] if n in inputs["OBSERVER"] else h) for n, h in read("accepted-inputs.json")["files"].items()}


def canaries():
    full = [{"id": n} for n in DECLARATIONS]
    assert expected_references(full)["U2B"] == "U3"
    timing({"status": "PASS", "exitCode": 0})
    result = [{"case": "literal complete naming and successful timing framing", "result": "PASS"}]
    for name, fn in (
            ("duplicate component", lambda: expected_references(full + full[:1])),
            ("unknown component", lambda: expected_references(full + [{"id": "UNKNOWN"}])),
            ("failed timing", lambda: timing({"status": "FAIL", "exitCode": 0})),
            ("exit2", lambda: timing({"status": "PASS", "exitCode": 2}))):
        try:
            fn()
        except AssertionError:
            result.append({"case": name, "result": "PASS_REJECTED"})
        else:
            raise RuntimeError("Bad synthetic frame accepted: " + name)
    (HERE / "gate-canaries.json").write_text(json.dumps({"status": "PASS", "cases": result,
        "limits": "Pure literal naming/timing framing only; not real native/build/OS proof"}, indent=2) + "\n", encoding="utf-8")
    print("PASS five pure naming/timing canaries; no execution claim")


def main():
    if sys.argv[1:] == ["--canaries"]:
        canaries()
        return
    phase = sys.argv[1]
    assert phase in ("baseline", "candidate", "final")
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    assert inputs["record"]() == read(("baseline" if phase == "baseline" else "candidate") + "-inputs.json")
    if phase != "final":
        cohort(phase)
        return
    source_boundary(inputs)
    cohort("candidate")
    for name in ("contracts", "build"):
        success(name)
    assert all(text("contracts-receipt.txt").count(marker) == 1 for marker in MARKERS)
    assert text("contracts.txt").count("PASS: focused current contracts; 13 Java suites. Full matrix and independent oracles NOT RUN.") == 1
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded", "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert text("build.txt").count(marker) == 1
    (HERE / "native-assessment.json").write_text(json.dumps({"status": "PASS", "pairedCases": 44,
        "focusedSuites": 13, "actualJdk8GwtPermutations": 5,
        "operationSeconds": {p: read(p + "-timing.json")["elapsedSeconds"] for p in ("baseline", "candidate", "contracts", "build")},
        "player": "Separate fresh actual normal reference/service evidence required",
        "limits": "No full unrelated matrix, unseen/all-seed, manufacturing, FileReader or new-size claim"}, indent=2) + "\n", encoding="utf-8")
    print("PASS paired44 exact physical oracle/native13/final JDK8GWT5; player separate")


if __name__ == "__main__":
    main()
