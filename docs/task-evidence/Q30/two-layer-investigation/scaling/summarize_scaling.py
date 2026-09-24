"""Preserve every P07 structural scaling row and summarize bounded attempts."""
import collections
import json
import math
import pathlib
import statistics


BATCHES = [
    "sweep-raw.txt",
    "extended-raw.txt",
    "boundary-raw.txt",
    "high-raw.txt",
    "eighty-raw.txt",
    "largest-raw.txt",
    "upper-raw.txt",
    "thirtythree-raw.txt",
]


def percentile(values, fraction):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(fraction * len(ordered)) - 1)]


def main():
    evidence = pathlib.Path(__file__).resolve().parent
    source = evidence
    rows = []
    seen = set()
    for name in BATCHES:
        for line in (source / name).read_text(encoding="utf-8").splitlines():
            if line.startswith("SCALE_TIMEOUT ") or line.startswith("SCALE_ERROR "):
                raise AssertionError("Non-row result must be retained and classified: " + line)
            if not line.startswith("SCALE_ROW "):
                continue
            row = json.loads(line[len("SCALE_ROW "):])
            key = (row["count"], row["seed"], row["policy"])
            if key in seen:
                raise AssertionError("Duplicate scaling row: " + repr(key))
            seen.add(key)
            if not row["inputIntact"] or row["expansions"] > 1000000:
                raise AssertionError("Input mutation or broken P07 work bound: " + repr(key))
            if row["outcome"] == "SUCCESS" and not row["validMixedLayer"]:
                raise AssertionError("Unvalidated success: " + repr(key))
            row["batch"] = name
            row["gridCells"] = ((row["boardWidth"] - 20) // 10 + 1) * (
                (row["boardHeight"] - 20) // 10 + 1)
            rows.append(row)
    rows.sort(key=lambda r: (r["count"], r["seed"], r["policy"]))
    with (evidence / "results.jsonl").open("w", encoding="utf-8") as output:
        for row in rows:
            output.write(json.dumps(row, sort_keys=True, separators=(",", ":")) + "\n")
    groups = collections.defaultdict(list)
    for row in rows:
        groups[(row["count"], row["policy"])].append(row)
    summaries = []
    for (count, policy), group in sorted(groups.items()):
        success = [r for r in group if r["outcome"] == "SUCCESS"]
        reference = group[0]
        if any((r["boardArea"], r["maxNetDegree"], r["pads"], r["nets"],
                r["channels"], r["extras"]) !=
               (reference["boardArea"], reference["maxNetDegree"], reference["pads"],
                reference["nets"], reference["channels"], reference["extras"])
               for r in group):
            raise AssertionError("Structural population changed within size " + str(count))
        summary = {
            "count": count,
            "policy": policy,
            "attempts": len(group),
            "successes": len(success),
            "seeds": [r["seed"] for r in group],
            "failures": dict(sorted(collections.Counter(
                r["outcome"] for r in group if r["outcome"] != "SUCCESS").items())),
            "channels": reference["channels"],
            "auxiliaryShuntsOrLoads": reference["extras"],
            "parts": reference["parts"],
            "pads": reference["pads"],
            "nets": reference["nets"],
            "maxNetDegree": reference["maxNetDegree"],
            "boardWidth": reference["boardWidth"],
            "boardHeight": reference["boardHeight"],
            "boardArea": reference["boardArea"],
            "gridCells": reference["gridCells"],
            "expansionsP50": percentile([r["expansions"] for r in group], .50),
            "expansionsP95": percentile([r["expansions"] for r in group], .95),
            "routeMsP50": percentile([r["routeMs"] for r in group], .50),
            "routeMsP95": percentile([r["routeMs"] for r in group], .95),
            "viasSuccessMedian": statistics.median([r["vias"] for r in success]) if success else None,
            "viasSuccessMax": max([r["vias"] for r in success], default=None),
            "topLengthSuccessMedian": statistics.median([r["topLength"] for r in success]) if success else None,
            "bottomLengthSuccessMedian": statistics.median([r["bottomLength"] for r in success]) if success else None,
            "topSegmentsSuccessMedian": statistics.median([r["topSegments"] for r in success]) if success else None,
            "bottomSegmentsSuccessMedian": statistics.median([r["bottomSegments"] for r in success]) if success else None,
        }
        summaries.append(summary)
    result = {
        "schema": 1,
        "base": "e0c368855a3891acd4673e94ce9afa732390e2bf",
        "kind": "synthetic structural P07 two-layer router experiment; no CircuitJS or normal-player claim",
        "rows": len(rows),
        "sizePolicySummaries": summaries,
        "percentileRule": "nearest rank over all attempts at the size/policy; p95=sorted[ceil(0.95*n)-1]",
        "largestSingleSuccessInSample": max(r["count"] for r in rows if r["outcome"] == "SUCCESS"),
        "highestTenSeedAllSuccessSize": max(s["count"] for s in summaries
                                            if s["policy"] == "FULLER_TWO_LAYER"
                                            and s["attempts"] >= 10 and s["successes"] == s["attempts"]),
        "globalMaximumMeasured": False,
    }
    (evidence / "summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for s in summaries:
        print(s["count"], s["policy"], "%d/%d" % (s["successes"], s["attempts"]),
              "exp", s["expansionsP50"], s["expansionsP95"], "vias", s["viasSuccessMedian"],
              s["viasSuccessMax"])
    print("rows", len(rows))


if __name__ == "__main__":
    main()
