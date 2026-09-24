"""Summarize the provider-selected Q30 medium physical policy population."""

import argparse
import hashlib
import json
import math
from collections import Counter
from pathlib import Path


SEEDS = {"0", "1", "3", "17", "42", "-1", "11", "23", "37", "59", "83", "-23"}
HELD_OUT = {"11", "23", "37", "59", "83", "-23"}


def percentile(values, q):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[math.ceil(q * len(ordered)) - 1]


def distribution(rows, key):
    values = [row[key] for row in rows]
    return {
        "min": min(values) if values else None,
        "median": percentile(values, 0.5),
        "p95": percentile(values, 0.95),
        "max": max(values) if values else None,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    rows = [
        json.loads(line.split(" ", 1)[1])
        for line in args.raw.read_text(encoding="utf-8").splitlines()
        if line.startswith("Q30_P1_POLICY ")
    ]
    by_seed = {row["seed"]: row for row in rows}
    if len(rows) != len(by_seed) or set(by_seed) != SEEDS:
        raise ValueError("Q30-P1 policy corpus must include each specified seed exactly once")
    if {row["policy"] for row in rows} != {"MEDIUM_BOARD@1"}:
        raise ValueError("Q30-P1 policy identity differs across the corpus")
    for row in rows:
        if row["placementCandidates"] != 6 or row["placementRejections"] < 0:
            raise ValueError("unbounded or incomplete placement search: " + row["seed"])
        if not 0 <= row["placementEvaluations"] <= 6 * 60000:
            raise ValueError("placement evaluation receipt exceeds candidate bounds: " + row["seed"])
        if row["routeAttempts"] > 6 or row["routeAttempts"] != (
            row["oneFaceAttempts"] + row["twoLayerAttempts"]
        ):
            raise ValueError("unbounded or inconsistent route subset: " + row["seed"])
        if len(row["rankedPlacementAttempts"]) != (
            row["placementCandidates"] - row["placementRejections"]
        ):
            raise ValueError("ranked placement accounting differs: " + row["seed"])
        if len(row["placementScores"]) != len(row["rankedPlacementAttempts"]):
            raise ValueError("placement score accounting differs: " + row["seed"])
        if len(row["routeOutcomes"]) != row["routeAttempts"]:
            raise ValueError("route outcome accounting differs: " + row["seed"])
        if row["outcome"] == "SUCCESS":
            if row["vias"] and (not row["topLength"] or not row["bottomLength"]):
                raise ValueError("two-face success lacks both copper faces: " + row["seed"])
            if row["selectedPlacementAttempt"] < 0:
                raise ValueError("success has no selected placement: " + row["seed"])
    success = [row for row in rows if row["outcome"] == "SUCCESS"]
    failed = [row for row in rows if row["outcome"] != "SUCCESS"]
    result = {
        "schema": 1,
        "kind": "provider-selected actual Q30 structural physical policy; normal admission unqualified",
        "rawSha256": hashlib.sha256(args.raw.read_bytes()).hexdigest(),
        "seeds": sorted(SEEDS, key=int),
        "heldOutSeeds": sorted(HELD_OUT, key=int),
        "attempts": len(rows),
        "successes": len(success),
        "heldOutSuccesses": sum(row["seed"] in HELD_OUT for row in success),
        "failureReasons": dict(sorted(Counter(row["failure"] for row in failed).items())),
        "topologyCounts": dict(sorted(Counter(row["topology"] for row in rows).items())),
        "selectedRoutePolicies": dict(sorted(Counter(
            row["selectedRoutePolicy"] for row in success
        ).items())),
        "successfulVias": distribution(success, "vias"),
        "successfulArea": distribution(success, "area"),
        "successfulPlacementScore": distribution(success, "selectedPlacementScore"),
        "successfulUniqueCopper": distribution(success, "uniqueCopper"),
        "allPlacementEvaluations": distribution(rows, "placementEvaluations"),
        "allRouteExpansions": distribution(rows, "routeExpansions"),
        "allElapsedMs": distribution(rows, "elapsedMs"),
        "rows": rows,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in (
        "attempts", "successes", "heldOutSuccesses", "failureReasons",
        "selectedRoutePolicies", "successfulVias", "successfulArea",
        "allPlacementEvaluations", "allRouteExpansions", "allElapsedMs",
    )}, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
