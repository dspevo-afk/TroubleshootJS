"""Verify one retrospective two-reading tree; never assign a profile."""
import copy
import hashlib
import json
import math
from pathlib import Path

PACKET = Path(__file__).resolve().parent
REPO = PACKET.parents[3]
R9 = PACKET.parent / "q60-calibration-r9"
CAPTURES = (
    ("40", "0ff41777aa399b012bbd5451938c49897576fc4f2a8fe212306e3068f8e72486"),
    ("55", "e375f80a2c5f5d068d7f8103ff0bb5818e2e586f6195bab54205e0d21f5116e0"),
    ("60", "6077bbff866fec2d2e34fdc1991061e02211d57746f9d11d2d600a5c7281ae17"),
)
IDS = ("DRIVER_A_COMMAND", "DRIVER_A_INPUT", "REGULATED_5V")


def need(condition, message):
    if not condition:
        raise ValueError(message)


def sample(row, identity):
    found = [item for item in row["samples"] if item["id"] == identity]
    need(len(found) == 1, "missing or duplicate tree observation")
    value = found[0]
    need(value["outcome"] == "NUMERIC" and
         type(value["value"]) in (int, float) and math.isfinite(value["value"]),
         "tree requires an actual finite DC observation")
    expected = max(.01, abs(value["value"]) * .02)
    need(math.isclose(value["tolerance"], expected, rel_tol=1e-12, abs_tol=1e-12),
         "observation tolerance changed")
    return value


def same(a, b):
    return abs(a["value"] - b["value"]) <= max(a["tolerance"], b["tolerance"])


def prove(rows):
    need(len(rows) == 5 and [r["ordinal"] for r in rows] == list(range(5)) and
         [r["repairClass"] for r in rows] == list(range(5)), "repair census changed")
    values = [{identity: sample(row, identity) for identity in IDS} for row in rows]
    for ordinal in range(5):
        group = [0, 1] if ordinal < 2 else [2, 3, 4]
        observed = [i for i in range(5) if same(values[ordinal][IDS[0]], values[i][IDS[0]])]
        need(observed == group, "root observation does not produce the stated branches")
        second = IDS[1] if ordinal < 2 else IDS[2]
        remaining = [i for i in group if same(values[ordinal][second], values[i][second])]
        need(remaining == [ordinal], "second reading does not distinguish the repair")
    return [{"ordinal": i, **{identity: values[i][identity]["value"] for identity in IDS}}
            for i in range(5)]


def falsifiers(rows):
    def replace(data, ordinal, identity, value):
        target = next(x for x in data[ordinal]["samples"] if x["id"] == identity)
        target.update(value=value, tolerance=max(.01, abs(value) * .02))
    negatives = []
    bad = copy.deepcopy(rows); replace(bad, 0, IDS[1], sample(rows[1], IDS[1])["value"]); negatives.append(bad)
    bad = copy.deepcopy(rows); replace(bad, 2, IDS[2], sample(rows[3], IDS[2])["value"]); negatives.append(bad)
    bad = copy.deepcopy(rows); replace(bad, 0, IDS[0], 0); negatives.append(bad)
    bad = copy.deepcopy(rows); bad[1]["repairClass"] = 0; negatives.append(bad)
    bad = copy.deepcopy(rows); sample(bad[0], IDS[0])["tolerance"] = -1; negatives.append(bad)
    for bad in negatives:
        try:
            prove(bad)
        except ValueError:
            continue
        raise ValueError("counterexample falsifier was accepted")
    return len(negatives)


def main():
    plan = json.loads((R9 / "cohort-plan.json").read_text(encoding="utf-8-sig"))
    baseline = json.loads((R9 / "baseline-inputs.json").read_text(encoding="utf-8-sig"))
    sources = {}
    for name in ("Rb56DiagnosticProvider", "Rb56Plan", "Rb56PowerDomains", "MeasurementReferencePolicy"):
        path = "src/com/lushprojects/circuitjs1/client/" + name + ".java"
        actual = hashlib.sha256((REPO / path).read_bytes()).hexdigest()
        need(actual == baseline[path], "reference/program source changed")
        sources[path] = actual
    cases = []
    for index, (size, digest) in enumerate(CAPTURES):
        raw = (R9 / ("capture-development-" + size + ".json")).read_bytes()
        need(hashlib.sha256(raw).hexdigest() == digest, "accepted capture changed")
        capture = json.loads(raw)
        need(capture["seed"] == plan["development"][index] and
             capture["build"] == plan["build"] and capture["sourceBinding"] == plan["sourceBinding"],
             "capture identity mismatch")
        report = capture["report"]
        need(report["status"] == "PASS" and report["diagnosticProgramVersion"] == 2,
             "current accepted program required")
        rows = report["calibrationObservations"]
        cases.append({"seed": capture["seed"], "captureSha256": digest,
                      "values": prove(rows), "negativeCanariesRejected": falsifiers(rows)})
    result = {
        "status": "PASS_RETROSPECTIVE_COUNTEREXAMPLE_ONLY", "cases": cases,
        "root": IDS[0], "branches": [{"ordinals": [0, 1], "second": IDS[1]},
                                     {"ordinals": [2, 3, 4], "second": IDS[2]}],
        "sourceAudit": sources,
        "context": "Source-audited powered DC / SENSORS_HIGH; all black probes UAC.OUT- / CTRL_RETURN / SECONDARY",
        "scope": "Two-reading witness within certified observations; not executed player-adaptive order or global minimum preparation",
        "newDifficultyFeature": "NO_GO: optional OFF/ohms waits are not unavoidable diagnostic cost",
        "profileAssigned": None, "protectedRegressionRead": False,
        "readerSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    (PACKET / "counterexample.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("PASS: three pinned retrospective trees; fifteen negative canaries; no HARD feature or profile")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, TypeError) as error:
        print("FAIL:", error)
        raise SystemExit(2)
