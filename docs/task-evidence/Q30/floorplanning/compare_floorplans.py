#!/usr/bin/env python3
"""Compare two labeled Q30 floorplan metric receipts."""

from __future__ import print_function

import argparse
import hashlib
import json
import pathlib
import sys


METRICS = [
    "structure.parts", "structure.pads", "structure.nets",
    "structure.connectors", "area.boardArea", "area.courtyardArea",
    "area.courtyardFraction", "area.boardAspect",
    "area.courtyardEnvelopeAspect", "locality.netHplTotal",
    "locality.netMstTotal", "locality.componentPairDistanceMedian",
    "locality.electricallyAdjacentPadPairCount",
    "locality.electricallyAdjacentPadDistanceMean",
    "locality.electricallyAdjacentPadDistanceP95",
    "locality.maxNetFanout", "locality.maxFanoutNetHplMean",
    "locality.maxFanoutNetHplP95",
    "locality.netRoleMetrics.SUPPLY.hplTotal",
    "locality.netRoleMetrics.SUPPLY.hplP95",
    "locality.netRoleMetrics.SUPPLY.maxFanout",
    "locality.netRoleMetrics.RETURN.hplTotal",
    "locality.netRoleMetrics.RETURN.hplP95",
    "locality.netRoleMetrics.RETURN.maxFanout",
    "locality.sameRegionPairFraction", "locality.regionCrossingNetFraction",
    "locality.regionTransitionCount", "crossing.estimatedRatsnestCrossings",
    "crossing.netBoundingBoxOverlapPairs",
    "connector.nearestSameNetPadDistanceMedian",
    "connector.nearestSameNetPadDistanceP95",
    "connector.crossRegionIncidenceFraction",
    "area.courtyardOverlapPairs", "area.minimumCourtyardEdgeClearance",
]


SHARED_PROVENANCE = [
    "q30SourceSha256", "q30PlanSourceSha256", "q30FloorplanPatchSha256",
    "syntheticRawSha256", "syntheticFixtureSourceSha256",
    "q30PlacementSource", "q30Seeds", "q30CandidatesPerSeed",
    "syntheticSuccessfulSeeds",
]


def sha256(path):
    digest = hashlib.sha256()
    with pathlib.Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load(path):
    with pathlib.Path(path).open("r", encoding="utf-8") as source:
        value = json.load(source)
    if value.get("schema") != 1:
        raise ValueError("unsupported comparison schema in %s" % path)
    if "fixtureProvenance" not in value or "corpora" not in value:
        raise ValueError("missing provenance/corpora in %s" % path)
    return value


def q30_descriptor(path, value):
    provenance = value["fixtureProvenance"]
    corpus = value["corpora"]["q30CurrentExactRoot"]
    measurement = value.get("measurement", {})
    input_path = pathlib.Path(path)
    # Keep receipts portable when callers pass an absolute local path. The
    # command examples use relative paths so matched directory provenance is
    # retained in normal evidence output.
    input_display = (input_path.name if input_path.is_absolute()
                     else input_path.as_posix())
    required = [
        "q30SourceSha256", "q30PlanSourceSha256", "q30PlannerSourceSha256",
        "q30PlannerInput", "q30PlannerSourceMode", "plannerLabel",
        "measurementLabel", "measurementStatus", "q30InspectSha256",
        "syntheticRawSha256", "q30FloorplanPatchSha256",
        "syntheticFixtureSourceSha256", "q30Seeds", "q30CandidatesPerSeed",
    ]
    missing = [key for key in required if key not in provenance]
    if missing:
        raise ValueError("missing provenance fields in %s: %s" %
                         (path, ", ".join(missing)))
    return {
        "comparisonInput": input_display,
        "comparisonInputSha256": sha256(path),
        "measurement": measurement,
        "provenance": provenance,
        "corpus": {
            "attemptCount": corpus["attemptCount"],
            "placementCount": corpus["placementCount"],
            "rejectedCount": corpus["rejectedCount"],
            "rejectedReasons": corpus.get("rejectedReasons", {}),
            "seedCount": corpus["seedCount"],
            "candidateCount": corpus["candidateCount"],
            "rowsFile": corpus["rowsFile"],
        },
    }


def q30_median(value, metric):
    summary = value["corpora"]["q30CurrentExactRoot"]["summary"]
    if metric not in summary:
        raise ValueError("metric %s absent from Q30 summary" % metric)
    return summary[metric]["median"], summary[metric]["count"]


def structured_median(value, metric):
    summary = value["corpora"]["synthetic33Successful"]["summary"]
    if metric not in summary:
        raise ValueError("metric %s absent from structured fixture summary" % metric)
    return summary[metric]["median"], summary[metric]["count"]


def validate_shared(before, after):
    before_provenance = before["fixtureProvenance"]
    after_provenance = after["fixtureProvenance"]
    mismatches = {}
    for key in SHARED_PROVENANCE:
        if before_provenance.get(key) != after_provenance.get(key):
            mismatches[key] = {
                "before": before_provenance.get(key),
                "after": after_provenance.get(key),
            }
    before_corpus = before["corpora"]["q30CurrentExactRoot"]
    after_corpus = after["corpora"]["q30CurrentExactRoot"]
    for key in ("attemptCount", "seedCount", "candidateCount"):
        if before_corpus.get(key) != after_corpus.get(key):
            mismatches["q30CurrentExactRoot." + key] = {
                "before": before_corpus.get(key),
                "after": after_corpus.get(key),
            }
    if mismatches:
        raise ValueError("before/after shared-input mismatch: %s" %
                         json.dumps(mismatches, sort_keys=True))


def build(before_path, after_path):
    before = load(before_path)
    after = load(after_path)
    validate_shared(before, after)
    before_descriptor = q30_descriptor(before_path, before)
    after_descriptor = q30_descriptor(after_path, after)
    table = []
    for metric in METRICS:
        before_value, before_count = q30_median(before, metric)
        after_value, after_count = q30_median(after, metric)
        structured_value, structured_count = structured_median(before, metric)
        table.append({
            "metric": metric,
            "beforeMedian": before_value,
            "afterMedian": after_value,
            "structuredMedian": structured_value,
            "afterMinusBefore": after_value - before_value,
            "afterOverBefore": (after_value / before_value
                                 if before_value != 0 else None),
            "beforeCount": before_count,
            "afterCount": after_count,
            "structuredCount": structured_count,
        })
    shared = {}
    for key in SHARED_PROVENANCE:
        shared[key] = before["fixtureProvenance"].get(key)
    return {
        "schema": 1,
        "kind": "Q30-P1 floorplan diagnosis; stable planner before/after",
        "measurement": {
            "label": "before-after-planner-comparison",
            "status": "comparison",
        },
        "inputHashes": {
            "shared": shared,
            "beforePlannerSourceSha256":
                before["fixtureProvenance"]["q30PlannerSourceSha256"],
            "afterPlannerSourceSha256":
                after["fixtureProvenance"]["q30PlannerSourceSha256"],
            "beforeComparisonSha256": sha256(before_path),
            "afterComparisonSha256": sha256(after_path),
            "beforeInspectSha256":
                before["fixtureProvenance"]["q30InspectSha256"],
            "afterInspectSha256":
                after["fixtureProvenance"]["q30InspectSha256"],
        },
        "before": before_descriptor,
        "after": after_descriptor,
        "methodology": {
            "population": "Both runs use the same 12 signed seeds and candidates 0..5; every requested attempt is retained and no best-row filtering is applied.",
            "plannerIsolation": "The harness copies the complete source tree into unique OS temp scratch; when PlannerSource is supplied, that file replaces only scratch src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java before JDK8 compilation.",
            "beforeSource": "The BEFORE snapshot is produced by git show HEAD:src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java and passed as an explicit planner source input.",
            "afterSource": "The AFTER run must be supplied explicitly after floorplan_impl signals a stable planner; its planner source hash is recorded separately.",
            "aggregation": "Each Q30 table value is the median over accepted placement rows; the structuredMedian column is the median over every validated successful 33-part control row. Rejected Q30 attempts remain in corpus counts and rejection reasons.",
            "delta": "afterMinusBefore is afterMedian - beforeMedian; afterOverBefore is afterMedian / beforeMedian when the baseline is nonzero.",
            "sharedFixture": "The comparison fails unless Q30 source, actual Rb30Plan source, exporter patch, synthetic raw/source, seed set, and candidate population match.",
            "metricDefinitions": "The two input receipts carry the complete metric definitions and limits; this table reuses their Q30 summary medians and the shared structured fixture summary. Per-region, channel, role, and connector diagnostics remain in each run's Q30 summary and JSONL rows.",
        },
        "beforeAfterTable": table,
        "limits": [
            "This table isolates placement geometry and does not prove routed copper, solver behavior, player inspectability, normal admission, or diagnosis.",
            "A planner source snapshot is the only intentionally varied input; Q30 source and Rb30Plan hashes must remain equal, but unrelated source dependencies are still governed by the worktree snapshot.",
            "Metrics remain geometric proxies and are not a single quality score; interpretation must account for rejected-attempt counts and the different synthetic control graph.",
            "Structured fixture values are a control distribution with seven validated seeds, not a matched Q30 electrical graph; they are included to expose scale and metric behavior.",
        ],
    }


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args(argv)
    result = build(args.before, args.after)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as target:
        json.dump(result, target, indent=2, sort_keys=True)
        target.write("\n")
    print("PASS: compared %d Q30 metrics" % len(result["beforeAfterTable"]))
    print("Comparison: %s" % output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
