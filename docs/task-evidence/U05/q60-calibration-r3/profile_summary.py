"""Reduce an actual CDP CPU profile using this GWT build's symbol map."""
import argparse
import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--symbols", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    raw = args.profile.read_bytes()
    symbols_raw = args.symbols.read_bytes()
    profile = json.loads(raw)["profile"]
    symbols = {}
    for row in csv.reader(symbols_raw.decode("utf-8").splitlines()):
        if len(row) == 7 and not row[0].startswith("#"):
            symbols[row[0]] = {"class": row[2], "member": row[3],
                               "source": row[4], "sourceLine": row[5]}
    nodes = {node["id"]: node for node in profile["nodes"]}
    parents = {}
    for node in nodes.values():
        for child in node.get("children", []):
            if child in parents:
                raise ValueError("profile is not a unique call tree")
            parents[child] = node["id"]
    samples, deltas = profile["samples"], profile["timeDeltas"]
    if len(samples) != len(deltas) or not samples:
        raise ValueError("profile sampling arrays are incomplete")
    # CDP deltas encode timestamps, not weights belonging to the same sample.
    # Like DevTools, reconstruct timestamps and sort paired samples first.
    # Preserve and report out-of-order samples instead of clamping/dropping them.
    timestamp = profile["startTime"]
    timed_samples = []
    for index, (node_id, delta) in enumerate(zip(samples, deltas)):
        if node_id not in nodes or type(delta) is not int:
            raise ValueError("profile sample is invalid")
        timestamp += delta
        if not profile["startTime"] <= timestamp <= profile["endTime"]:
            raise ValueError("profile timestamp is outside capture")
        timed_samples.append((timestamp, index, node_id))
    timed_samples.sort()
    own, inclusive = defaultdict(int), defaultdict(int)
    function_own, function_inclusive = defaultdict(int), defaultdict(int)
    function_example = {}
    for index, (timestamp, _, node_id) in enumerate(timed_samples):
        next_time = (timed_samples[index + 1][0] if index + 1 < len(timed_samples)
                     else profile["endTime"])
        delta = next_time - timestamp
        own[node_id] += delta
        name = nodes[node_id]["callFrame"].get("functionName", "")
        function_own[name] += delta
        cursor, seen, seen_functions = node_id, set(), set()
        while cursor is not None:
            if cursor in seen:
                raise ValueError("profile call tree cycle")
            seen.add(cursor)
            inclusive[cursor] += delta
            name = nodes[cursor]["callFrame"].get("functionName", "")
            function_example[name] = cursor
            if name not in seen_functions:
                function_inclusive[name] += delta
                seen_functions.add(name)
            cursor = parents.get(cursor)
    total = profile["endTime"] - timed_samples[0][0]

    def describe(node_id, micros):
        frame = nodes[node_id]["callFrame"]
        name = frame.get("functionName", "")
        entry = {"function": name or "(anonymous)", "selfMillis": own[node_id] / 1000,
                 "inclusiveMillis": inclusive[node_id] / 1000,
                 "sampledPercent": round(micros * 100 / total, 3)}
        if name in symbols:
            entry.update(symbols[name])
        return entry

    result = {
        "schema": "tsj-compiled-cpu-profile-summary/1",
        "scope": "Sampled CPU diagnosis; profiler overhead and sampling are not admission proof",
        "profileSha256": hashlib.sha256(raw).hexdigest(),
        "symbolMapSha256": hashlib.sha256(symbols_raw).hexdigest(),
        "samples": len(samples), "sampledMillis": total / 1000,
        "negativeDeltas": [dict(index=i, microseconds=d) for i, d in enumerate(deltas) if d < 0],
        "durationMethod": "Stable timestamp sort; each sample to next, final sample to capture end",
        "unassignedPrefixMicros": timed_samples[0][0] - profile["startTime"],
        "profilerWallMillis": (profile["endTime"] - profile["startTime"]) / 1000,
        "topSelf": [describe(node_id, micros) for node_id, micros in
                    sorted(own.items(), key=lambda item: item[1], reverse=True)[:40]],
        "topInclusive": [describe(node_id, micros) for node_id, micros in
                         sorted(inclusive.items(), key=lambda item: item[1], reverse=True)[:40]],
        "publicAdmissionGranted": False,
    }
    result["topFunctionsSelf"] = []
    result["topFunctionsInclusive"] = []
    for key, counts in (("topFunctionsSelf", function_own),
                        ("topFunctionsInclusive", function_inclusive)):
        for name, micros in sorted(counts.items(), key=lambda item: item[1], reverse=True)[:60]:
            entry = describe(function_example[name], micros)
            entry.update(selfMillis=function_own[name] / 1000,
                         inclusiveMillis=function_inclusive[name] / 1000)
            result[key].append(entry)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"samples": len(samples), "topSelf": result["topFunctionsSelf"][:15],
                      "topInclusive": result["topFunctionsInclusive"][:15]}, indent=2))


if __name__ == "__main__":
    main()
