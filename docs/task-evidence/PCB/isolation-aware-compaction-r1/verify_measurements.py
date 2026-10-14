"""Independent private13 acceptance against the frozen published12 receipts."""
import argparse
import json
from pathlib import Path


def assess(baseline, logs):
    expected = {case["seed"]: case for case in baseline["cases"]}
    measured = {}
    prefix = "PCB_ISOLATION_COMPACTION_CASE "
    for log in logs:
        for line in log.read_text(encoding="utf-8-sig").splitlines():
            if line.startswith(prefix):
                case = json.loads(line[len(prefix):])
                seed = case["seed"]
                if seed in measured:
                    raise ValueError("duplicate measured seed " + seed)
                measured[seed] = case
    if set(measured) != set(expected):
        raise ValueError("measured cohort must equal the ten frozen roots")
    comparisons = []
    for seed, old in expected.items():
        new = measured[seed]
        if new["version"] != 13 or new["density"] != 12:
            raise ValueError("wrong interpretation for " + seed)
        area_delta = new["area"] - old["area"]
        void_delta = (new["largestProtectedEmptyRectangleArea"] -
                      old["largestProtectedEmptyRectangle"]["area"])
        comparisons.append({"seed": seed, "areaDelta": area_delta,
                            "protectedVoidDelta": void_delta,
                            "copperDelta": new["copperLength"] - old["copperLength"],
                            "viaDelta": new["vias"] - old["vias"]})
    no_regression = all(c["areaDelta"] <= 0 and c["protectedVoidDelta"] <= 0
                        for c in comparisons)
    improved = any(c["areaDelta"] < 0 or c["protectedVoidDelta"] < 0
                   for c in comparisons)
    return {"status": "PASS" if no_regression and improved else "FAIL",
            "noCaseRegresses": no_regression, "atLeastOneStrictImprovement": improved,
            "scope": "Metric acceptance only; full native/build/player gates remain separate",
            "cases": comparisons}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    baseline = json.loads((Path(__file__).parent / "published12-baselines.json").read_text(
        encoding="utf-8-sig"))
    result = assess(baseline, args.logs)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))
    raise SystemExit(0 if result["status"] == "PASS" else 1)
