"""Supplement the unchanged frozen assessor with exact expanded service framing.

The original final assertion incorrectly requires one service marker. The pinned
maintained runner executes eight default seeds times five independently bounded
faults. Preserve that assessor and its failure; retain every other final check.
"""
import copy
import hashlib
import json
import re
import runpy
import sys
from pathlib import Path

if not __debug__:
    raise RuntimeError("Expanded service framing requires assertion-enabled Python")
HERE = Path(__file__).resolve().parent
SEEDS = ("7", "13", "4", "14", "43", "3", "10", "64")
RELAYS = ("B", "A", "B", "A", "B", "B", "B", "A")
COMMON = ("DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN")
OWNERS = {"DREV_OPEN": "DREV", "REN_OPEN": "REN", "SENSOR_A_OPEN": "RSA",
          "DRIVE_A_OPEN": "RDA", "RELAY_A_COIL_OPEN": "KA", "RELAY_B_COIL_OPEN": "KB"}
EXPECTED = tuple((seed, fault) for seed, relay in zip(SEEDS, RELAYS)
                 for fault in ("RELAY_" + relay + "_COIL_OPEN",) + COMMON)
BLOCK = re.compile(r"^Q30_CASE\|(?P<seed>-?\d+)\|(?P<fault>[A-Z0-9_]+)"
                   r"\|outcome=PASS\|reason=qualified\r?\nSTDOUT_BEGIN\r?\n"
                   r"(?P<stdout>.*?)\r?\nSTDOUT_END\r?\nSTDERR_BEGIN\r?\n"
                   r"(?P<stderr>.*?)\r?\nSTDERR_END(?=\r?$)", re.M | re.S)
CENSUS = ("PASS: Q30 service flow census attempted=40 passed=40 failed=0 failedCases=none "
          "seeds=7,13,4,14,43,3,10,64 faults=5 "
          "order=RELAY_<channel>_COIL_OPEN,DREV_OPEN,REN_OPEN,SENSOR_A_OPEN,DRIVE_A_OPEN "
          "childBudgetMs=60000")
FOOTER = "PASS: focused current contracts; 13 Java suites. Full matrix and independent oracles NOT RUN."


def service(text):
    plans = [json.loads(line[len("Q30_CASE_PLAN "):]) for line in text.splitlines()
             if line.startswith("Q30_CASE_PLAN ")]
    assert tuple(p["seed"] for p in plans) == SEEDS, "exact eight preflight plans"
    for plan, relay in zip(plans, RELAYS):
        assert plan["schema"] == 1 and plan["planEpoch"] == 4
        assert tuple((f["id"], f["owner"]) for f in plan["faults"]) == tuple(
            (f, OWNERS[f]) for f in COMMON + ("RELAY_" + relay + "_COIL_OPEN",))
    blocks = list(BLOCK.finditer(text))
    assert text.count("Q30_CASE|") == len(blocks) == 40, "complete qualified child blocks"
    assert tuple((b["seed"], b["fault"]) for b in blocks) == EXPECTED, "exact unique ordered cases"
    assert text.count("PASS: Q30 service flow contracts ") == 40
    assert text.count("PASS: Q30 service flow seed=") == 40
    fingerprints = {}
    for b in blocks:
        assert not b["stderr"].strip(), "unexpected child stderr"
        rows = [s for s in b["stdout"].splitlines() if s.startswith("PASS: Q30 service flow seed=")]
        markers = [s for s in b["stdout"].splitlines() if s.startswith("PASS: Q30 service flow contracts ")]
        assert len(rows) == len(markers) == 1
        row = re.fullmatch(r"PASS: Q30 service flow seed=" + re.escape(b["seed"]) +
                          " fault=" + b["fault"] + " target=" + OWNERS[b["fault"]] +
                          r" fingerprintHash=([0-9a-fA-F]{1,8})", rows[0])
        assert row is not None, "exact child seed/fault/target"
        final = re.fullmatch(r"PASS: Q30 service flow contracts ([1-9]\d*) assertions seed=" +
                             re.escape(b["seed"]) + " fault=" + b["fault"], markers[0])
        assert final is not None, "exact child final marker"
        fingerprints.setdefault(b["seed"], row[1])
        assert fingerprints[b["seed"]] == row[1], "same seeded service graph across faults"
    census = [s for s in text.splitlines() if "Q30 service flow census " in s]
    assert census == [CENSUS], "exact complete passing census and unchanged child budget"
    return len(blocks)


def synthetic():
    plans = [{"seed": s, "schema": 1, "planEpoch": 4,
              "faults": [{"id": f, "owner": OWNERS[f]} for f in
                         COMMON + ("RELAY_" + r + "_COIL_OPEN",)]}
             for s, r in zip(SEEDS, RELAYS)]
    blocks = ["Q30_CASE|" + s + "|" + f + "|outcome=PASS|reason=qualified\nSTDOUT_BEGIN\n"
              "PASS: Q30 service flow seed=" + s + " fault=" + f + " target=" + OWNERS[f] +
              " fingerprintHash=abc123\nPASS: Q30 service flow contracts 1 assertions seed=" +
              s + " fault=" + f + "\nSTDOUT_END\nSTDERR_BEGIN\n\nSTDERR_END" for s, f in EXPECTED]
    prefix = "\n".join("Q30_CASE_PLAN " + json.dumps(p) for p in plans) + "\n"
    text = prefix + "\n".join(blocks) + "\n" + CENSUS
    assert service(text) == 40
    negatives = {
        "missing child": text.replace(blocks[-1], ""),
        "duplicate child": text + "\n" + blocks[0],
        "wrong target": text.replace("target=KB", "target=KA", 1),
        "failed child": text.replace("outcome=PASS", "outcome=FAIL", 1),
        "wrong order": prefix + "\n".join([blocks[1], blocks[0]] + blocks[2:]) + "\n" + CENSUS,
        "missing final marker": text.replace("PASS: Q30 service flow contracts 1 assertions", "MISSING", 1),
        "nonempty stderr": text.replace("STDERR_BEGIN\n\n", "STDERR_BEGIN\nerror\n", 1),
        "failed census": text.replace("attempted=40 passed=40 failed=0", "attempted=40 passed=39 failed=1"),
        "foreign seed plan": text.replace('"seed": "7"', '"seed": "8"', 1),
    }
    for name, bad in negatives.items():
        try:
            service(bad)
        except AssertionError:
            continue
        raise RuntimeError("Malformed synthetic service frame accepted: " + name)
    (HERE / "expanded-framing-canaries.json").write_text(json.dumps({"status": "PASS",
        "positive": 1, "rejectedNegativeFrames": list(negatives),
        "limits": "Pure synthetic parser framing; no native/build/OS execution proof"}, indent=2) + "\n", encoding="utf-8")


def main():
    if sys.argv[1:] == ["--canaries"]:
        synthetic()
        print("PASS expanded framing canaries: one positive/nine rejected negative frames")
        return
    seal = json.loads((HERE / "expanded-framing-seal.json").read_text(encoding="utf-8-sig"))
    sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    assert seal["assessorSha256"] == sha(Path(__file__))
    original = runpy.run_path(str(HERE / "assess.py"))
    inputs = runpy.run_path(str(HERE / "inputs.py"))
    assert inputs["record"]() == original["read"]("candidate-inputs.json")
    assert seal["originalGates"] == original["read"]("candidate-inputs.json")["gates"]
    assert seal["expectedCases"] == [list(c) for c in EXPECTED]
    original["source_boundary"](inputs)
    original["cohort"]("candidate")
    for phase in ("contracts", "build"):
        original["success"](phase)
    receipt = original["text"]("contracts-receipt.txt")
    for marker in original["MARKERS"]:
        if marker != "Q30 service flow contracts ":
            assert receipt.count(marker) == 1, marker
    assert service(receipt) == 40
    assert original["text"]("contracts.txt").count(FOOTER) == 1
    for marker in ("Compiling 5 permutations", "Compile of permutations succeeded",
                   "Compilation succeeded --", "Link succeeded", "Linking succeeded --"):
        assert original["text"]("build.txt").count(marker) == 1
    assert original["read"]("original-final-assessment-failure.json")["status"] == "FAIL"
    (HERE / "native-assessment.json").write_text(json.dumps({"status": "PASS",
        "pairedCases": 44, "focusedSuites": 13, "qualifiedServiceChildren": 40,
        "actualJdk8GwtPermutations": 5,
        "operationSeconds": {p: original["read"](p + "-timing.json")["elapsedSeconds"]
                             for p in ("baseline", "candidate", "contracts", "build")},
        "correction": "Original four gates unchanged; all original final checks retained except erroneous once-only service count, replaced by exact ordered40 qualified blocks and census",
        "player": "Separate fresh actual normal reference/service evidence required",
        "limits": "No new execution from parser; reuse only exact audited phase inputs; no full unrelated matrix/all-seed/manufacturing/new-size proof"}, indent=2) + "\n", encoding="utf-8")
    print("PASS paired44/native13/service40/final JDK8GWT5 with exact expanded framing; player separate")


if __name__ == "__main__":
    main()
