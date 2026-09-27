#!/usr/bin/env python3
"""Compare routed results against the frozen corpus from before resumable P07."""
import argparse
import copy
import json
from pathlib import Path

PREFIX = "Q30_CORPUS_JSON:"
FIELDS = (
    "schema", "outcome", "routeOutcome", "normalAdmissionOutcome",
    "normalAdmissionReason", "topologyAxis", "support", "partCount",
    "contractValidated", "cleanup", "hypothesisCount", "observationCount",
    "physicalFingerprintHash", "layoutFingerprintHash", "coldRouteCanonical",
    "warmRouteCanonical", "coldRouteOutcomes", "warmRouteOutcomes",
    "routeRejection", "validationFailure",
)


def read_rows(path):
    rows = {}
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if not line.startswith(PREFIX):
            continue
        row = json.loads(line[len(PREFIX):])
        seed = row["seed"]
        if not isinstance(seed, str) or str(int(seed)) != seed:
            raise ValueError("Noncanonical string seed")
        if not -(1 << 63) <= int(seed) < (1 << 63) or seed in rows:
            raise ValueError("Duplicate or out-of-range seed")
        rows[seed] = row
    if not rows:
        raise ValueError("No actual corpus rows")
    return rows


def compare(baseline, candidate, seeds):
    if set(candidate) != set(seeds) or len(seeds) != len(set(seeds)):
        raise ValueError("Candidate seed census differs from the requested population")
    for seed in seeds:
        if seed not in baseline:
            raise ValueError("Seed absent from frozen baseline: " + seed)
        before, after = baseline[seed], candidate[seed]
        if before["cleanup"] is not True or after["cleanup"] is not True:
            raise ValueError("Unclean row: " + seed)
        if before["contractValidated"] is not True or after["contractValidated"] is not True:
            raise ValueError("Unvalidated row: " + seed)
        for field in FIELDS:
            if field not in before or field not in after or before[field] != after[field]:
                raise ValueError("Routed result changed: seed=" + seed + " field=" + field)
    return len(seeds)


def canaries(baseline, candidate, seeds):
    rejected = 0
    for field in ("layoutFingerprintHash", "coldRouteCanonical", "warmRouteOutcomes",
                  "outcome", "cleanup"):
        broken = copy.deepcopy(candidate)
        broken[seeds[0]][field] = "CORRUPTED"
        try:
            compare(baseline, broken, seeds)
        except ValueError:
            rejected += 1
        else:
            raise ValueError("Comparison accepted corruption: " + field)
    broken = copy.deepcopy(candidate)
    del broken[seeds[0]]
    try:
        compare(baseline, broken, seeds)
    except ValueError:
        rejected += 1
    else:
        raise ValueError("Comparison accepted a missing seed")
    return rejected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    parser.add_argument("--seeds", nargs="+")
    args = parser.parse_args()
    baseline, candidate = read_rows(args.baseline), read_rows(args.candidate)
    seeds = args.seeds if args.seeds is not None else list(baseline)
    count = compare(baseline, candidate, seeds)
    rejected = canaries(baseline, candidate, seeds)
    print("PASS: frozen route/layout/rejection equality rows=%d corruptionCanaries=%d" %
          (count, rejected))
    print("Timing and charged routing slice counts are intentionally compared separately.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError) as failure:
        raise SystemExit("FAIL: " + str(failure))
