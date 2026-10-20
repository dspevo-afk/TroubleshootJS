"""Compare observed normal titles and exact geometry outside their one text field."""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
FAMILIES = ("LED_INDICATOR", "DIODE_PROTECTED_INDICATOR", "PARALLEL_DUAL_INDICATOR",
            "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH", "RELAY_OUTPUT",
            "SENSOR_CONTROL", "RB15_CONTROL", "COMPOSED_CONTROLLED_INDICATOR", "RB30_CONTROL")
ROOTS = ("0", "17", "13", "-9223372036854775808")
CORRECTED = {"RC_DELAY": "TSJ RC DELAY", "NPN_LOW_SIDE_SWITCH": "TSJ NPN SWITCH",
             "NMOS_LOW_SIDE_SWITCH": "TSJ NMOS SWITCH", "RELAY_OUTPUT": "TSJ RELAY OUTPUT",
             "SENSOR_CONTROL": "TSJ SENSOR CTRL", "COMPOSED_CONTROLLED_INDICATOR": "TSJ CTRL IND"}
UNCHANGED = {"LED_INDICATOR": "TSJ LED INDICATOR", "DIODE_PROTECTED_INDICATOR": "TSJ DIODE INDICATOR",
             "PARALLEL_DUAL_INDICATOR": "TSJ PARALLEL INDICATORS", "RB15_CONTROL": "TSJ CONTROL BOARD",
             "RB30_CONTROL": "TSJ MULTI-RAIL CONTROL"}
PHYSICAL_FIELDS = ("family", "profile", "root", "seed", "status", "phase", "executionPolicy",
                   "physicalAdmission", "graphBefore", "graphAfter", "logicalParts", "logicalPads",
                   "logicalNets", "layoutParts", "layoutPads", "traceCount", "layoutAlgorithmVersion",
                   "outline", "copperManhattanLength", "holeCount", "viaCount", "generationPlacementAttempts",
                   "generationRoutingAttempts", "generationRoutingExpansions", "whitespace")


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def cases(path, marker):
    result = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.startswith(marker):
            continue
        row = json.loads(line[len(marker):])
        key = row["family"], row["root"]
        assert key not in result, "duplicate case"
        result[key] = row
    assert set(result) == {(f, r) for f in FAMILIES for r in ROOTS}, "fixed44 coverage differs"
    return result


def compare(first, second, first_ink, second_ink):
    for key in first:
        a, b, ai, bi = first[key], second[key], first_ink[key], second_ink[key]
        assert all(field in a and field in b and a[field] == b[field] for field in PHYSICAL_FIELDS), (
            "physical/graph/work changed", key)
        assert ai["bounds"] == bi["bounds"], ("title slot changed", key)
        assert ai["geometryApartFromTitleSha256"] == bi["geometryApartFromTitleSha256"], (
            "geometry outside title ink changed", key)
        family = key[0]
        expected = CORRECTED.get(family, UNCHANGED.get(family))
        assert bi["title"] == expected, ("incorrect public family title", key, bi["title"])
        assert ai["title"] == UNCHANGED.get(family, "TSJ LED INDICATOR"), ("unexpected baseline title", key)
        assert (a["geometrySha256"] != b["geometrySha256"]) == (family in CORRECTED), (
            "full fingerprint must reflect exactly the corrected title families", key)


def main():
    phase = sys.argv[1]
    assert phase in ("baseline", "candidate")
    timing = read(HERE / (phase + "-timing.json"))
    assert timing["status"] == "PASS" and timing["exitCode"] == 0
    for side in ("before", "after"):
        assert ("PASS title phase exact input audit phase=" + phase) in (
            HERE / (phase + "-input-audit-" + side + ".txt")).read_text(encoding="utf-8-sig")
    receipt = HERE / (phase + "-receipt.txt")
    current = cases(receipt, "PCB_NORMAL_PACKING_CASE ")
    ink = cases(receipt, "PCB_NORMAL_SILK_CASE ")
    for row in current.values():
        assert row["status"] == "ACCEPTED" and row["graphBefore"] == row["graphAfter"]
        assert row["layoutAlgorithmVersion"] == 17
    if phase == "baseline":
        accepted = cases(HERE.parent / "normal-packing-r2/routes-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
        for key, row in current.items():
            assert all(row.get(field) == accepted[key][field] for field in PHYSICAL_FIELDS + ("geometrySha256",)), key
            assert ink[key]["title"] == UNCHANGED.get(key[0], "TSJ LED INDICATOR"), key
    else:
        baseline = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_PACKING_CASE ")
        baseline_ink = cases(HERE / "baseline-receipt.txt", "PCB_NORMAL_SILK_CASE ")
        compare(baseline, current, baseline_ink, ink)
    output = {"status": "PASS", "phase": phase, "cases": 44, "families": 11,
              "correctedTitleFamilies": list(CORRECTED) if phase == "candidate" else [],
              "physicalEquality": "PASS; exact full geometry apart from title text" if phase == "candidate" else "PASS; exact accepted r2 geometry",
              "elapsedSeconds": timing["elapsedSeconds"], "limits": "Fixed informed corpus; no all-seed/manufacturing/Edge wrapper proof"}
    (HERE / (phase + "-assessment.json")).write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
