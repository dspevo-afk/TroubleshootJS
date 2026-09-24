"""Check and summarize the matched Q30-P1 structural routing corpus."""

import argparse
import hashlib
import json
import math
from collections import Counter
from pathlib import Path


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[math.ceil(fraction * len(ordered)) - 1]


def read_raw(path):
    placements = {}
    routes = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("Q30_TWO_LAYER_PLACEMENT "):
            row = json.loads(line.split(" ", 1)[1])
            key = (str(row["seed"]), row["candidate"])
            if key in placements:
                raise ValueError("duplicate placement " + repr(key))
            placements[key] = row
        elif line.startswith("Q30_TWO_LAYER_ROUTE "):
            routes.append(json.loads(line.split(" ", 1)[1]))
    if not placements or not routes:
        raise ValueError("missing matched Q30 placement or route records")
    seen = set()
    for row in routes:
        key = (str(row["seed"]), row["candidate"])
        route_key = key + (row["policy"],)
        if route_key in seen:
            raise ValueError("duplicate route " + repr(route_key))
        seen.add(route_key)
        placed = placements.get(key)
        if placed is None or placed.get("outcome") != "PLACED":
            raise ValueError("route without accepted placement " + repr(route_key))
        for field in ("placementSignature", "area"):
            if row[field] != placed[field]:
                raise ValueError("route changed " + field + " " + repr(route_key))
        row.update({name: placed[name] for name in (
            "topology", "layoutSeed", "routingSeed", "parts", "pads", "nets",
            "maxNetDegree", "width", "height")})
    return placements, routes


def summarize(routes, placement_count):
    result = {"placements": placement_count, "policies": {}}
    for policy in sorted({row["policy"] for row in routes}):
        rows = [row for row in routes if row["policy"] == policy]
        success = [row for row in rows if row["outcome"] == "SUCCESS"]
        result["policies"][policy] = {
            "rows": len(rows),
            "successes": len(success),
            "successfulSeeds": sorted({str(row["seed"]) for row in success}),
            "failureReasons": dict(sorted(Counter(
                row["outcome"] for row in rows if row["outcome"] != "SUCCESS"
            ).items())),
            "viaMin": min((row["vias"] for row in success), default=None),
            "viaMedian": percentile([row["vias"] for row in success], 0.5),
            "viaP95": percentile([row["vias"] for row in success], 0.95),
            "viaMax": max((row["vias"] for row in success), default=None),
            "areaMedian": percentile([row["area"] for row in success], 0.5),
            "copperMedian": percentile([row["uniqueCopper"] for row in success], 0.5),
            "topLengthMedian": percentile([row["topLength"] for row in success], 0.5),
            "bottomLengthMedian": percentile([row["bottomLength"] for row in success], 0.5),
            "expansionsMedianAll": percentile([row["expansions"] for row in rows], 0.5),
            "expansionsP95All": percentile([row["expansions"] for row in rows], 0.95),
            "runtimeMsMedianAll": percentile([row["elapsedMs"] for row in rows], 0.5),
            "runtimeMsP95All": percentile([row["elapsedMs"] for row in rows], 0.95),
        }
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", required=True, type=Path)
    parser.add_argument("--before", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    placements, after = read_raw(args.raw)
    before = json.loads(args.before.read_text(encoding="utf-8"))
    keys = [(str(row["seed"]), row["candidate"], row["policy"]) for row in before]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate baseline route identity")
    baseline_by_key = {key: row for key, row in zip(keys, before)}
    after_by_key = {
        (str(row["seed"]), row["candidate"], row["policy"]): row
        for row in after
    }
    if set(baseline_by_key) != set(after_by_key):
        raise ValueError("before/after seed, candidate or policy population differs")
    expected_seeds = {"0", "1", "3", "17", "42", "-1",
                      "11", "23", "37", "59", "83", "-23"}
    expected_pairs = {(seed, candidate)
                      for seed in expected_seeds for candidate in range(6)}
    if {(seed, candidate) for seed, candidate, _ in after_by_key} != expected_pairs:
        raise ValueError("matched population is not twelve seeds by six candidates")
    shared_fields = ("topology", "layoutSeed", "routingSeed", "parts", "pads",
                     "nets", "maxNetDegree")
    for key, row in after_by_key.items():
        baseline = baseline_by_key[key]
        for field in shared_fields:
            if row[field] != baseline[field]:
                raise ValueError("electrical/seed input changed: " + repr((key, field)))
    if {row["policy"] for row in after} != {
        "P05_SINGLE_FACE", "P07_ONE_LAYER", "P07_RESTRICTED_TWO_LAYER",
        "P07_FULLER_TWO_LAYER"
    }:
        raise ValueError("unmatched policy set")
    placed = [row for row in placements.values() if row["outcome"] == "PLACED"]
    if len(after) != len(placed) * 4:
        raise ValueError("missing route policy quartet")
    result = {
        "schema": 1,
        "kind": "matched actual Q30 structural routes; no normal-player admission",
        "rawSha256": hashlib.sha256(args.raw.read_bytes()).hexdigest(),
        "beforeSha256": hashlib.sha256(args.before.read_bytes()).hexdigest(),
        "before": summarize(before, len({(str(r["seed"]), r["candidate"]) for r in before})),
        "after": summarize(after, len(placed)),
        "placementRejects": [row for row in placements.values() if row["outcome"] != "PLACED"],
        "rows": after,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "beforeFuller": result["before"]["policies"]["P07_FULLER_TWO_LAYER"],
        "afterFuller": result["after"]["policies"]["P07_FULLER_TWO_LAYER"],
        "afterPlacements": len(placed),
        "placementRejects": len(result["placementRejects"]),
    }, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
