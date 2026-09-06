#!/usr/bin/env python3
"""Independent Task 46 seed and selection oracle.

This file intentionally contains no import from the production package.  It
recomputes the frozen integer algorithm and compares the resulting literals
with both the committed TSV and Task46ContractVectors.java.
"""

from __future__ import print_function

import csv
import re
import sys
from pathlib import Path


MASK = (1 << 64) - 1
SIGN = 1 << 63
OFFSET = 0xCBF29CE484222325
PRIME = 0x00000100000001B3
GAMMA = 0x9E3779B97F4A7C15
MUL1 = 0xBF58476D1CE4E5B9
MUL2 = 0x94D049BB133111EB

HEADER = [
    "id", "root-seed", "intent-id", "intent-version", "scope", "block-key",
    "concern", "revision", "semantic-key", "derived-seed", "draw0", "draw1",
    "draw2", "candidates", "selection",
]


def signed(value):
    value &= MASK
    return value - (1 << 64) if value & SIGN else value


def frame(value):
    encoded = value.encode("ascii")
    return str(len(encoded)).encode("ascii") + b":" + encoded


def fnv_seed(root_seed, intent_id, intent_version, scope, block_key,
             concern, revision, semantic_key):
    fields = [
        "tsj-named-seed", "1", str(root_seed), intent_id,
        str(intent_version), scope, block_key, concern, str(revision),
        semantic_key,
    ]
    result = OFFSET
    for field in fields:
        for byte in frame(field):
            result = ((result ^ byte) * PRIME) & MASK
    return signed(result)


def splitmix(seed):
    state = seed & MASK
    while True:
        state = (state + GAMMA) & MASK
        value = state
        value = ((value ^ (value >> 30)) * MUL1) & MASK
        value = ((value ^ (value >> 27)) * MUL2) & MASK
        value = (value ^ (value >> 31)) & MASK
        yield signed(value)


def next_int(stream, positive_bound):
    if positive_bound <= 0:
        raise ValueError("positive bound required")
    while True:
        nonnegative = (next(stream) & MASK) >> 1
        value = nonnegative % positive_bound
        if nonnegative - value + (positive_bound - 1) < (1 << 63):
            return value


def read_tsv(path):
    with path.open("r", encoding="utf-8", newline="") as handle:
        rows = list(csv.reader(handle, delimiter="\t"))
    if not rows or rows[0] != HEADER:
        raise AssertionError("Task 46 TSV header is not the frozen schema")
    result = rows[1:]
    if any(len(row) != len(HEADER) for row in result):
        raise AssertionError("Task 46 TSV contains a row with the wrong width")
    if not result:
        raise AssertionError("Task 46 TSV has no fixed vectors")
    if len({row[0] for row in result}) != len(result):
        raise AssertionError("Task 46 TSV vector IDs are not unique")
    return result


def read_java_literals(path):
    source = path.read_text(encoding="utf-8")
    start = source.index("private static final String[][] GOLDEN")
    end = source.index("    };", start)
    literal_text = source[start:end]
    rows = []
    for line in literal_text.splitlines():
        if line.lstrip().startswith("{"):
            values = re.findall(r'"([^"\\]*)"', line)
            if len(values) != len(HEADER):
                raise AssertionError("Java golden row has the wrong width")
            rows.append(values)
    if not rows:
        raise AssertionError("Java golden literal corpus is empty")
    return rows


def verify_row(row):
    (vector_id, root_text, intent_id, intent_version_text, scope, block_key,
     concern, revision_text, semantic_key, seed_text, draw0_text, draw1_text,
     draw2_text, candidate_text, selection) = row
    root_seed = int(root_text, 10)
    intent_version = int(intent_version_text, 10)
    revision = int(revision_text, 10)
    if str(root_seed) != root_text:
        raise AssertionError("%s root seed is not strict decimal" % vector_id)
    if str(intent_version) != intent_version_text or intent_version <= 0:
        raise AssertionError("%s intent version is not positive decimal" % vector_id)
    if str(revision) != revision_text or revision <= 0:
        raise AssertionError("%s concern revision is not positive decimal" % vector_id)
    if scope not in ("device", "block"):
        raise AssertionError("%s has an invalid scope" % vector_id)
    if scope == "device" and block_key != "-":
        raise AssertionError("%s device vector must use '-' block key" % vector_id)
    if scope == "block" and block_key == "-":
        raise AssertionError("%s block vector must name a block" % vector_id)
    concerns = {
        "topology", "block", "values", "support", "fault", "scenario",
        "placement", "routing", "presentation",
    }
    if concern not in concerns:
        raise AssertionError("%s has an unknown concern" % vector_id)
    if concern in ("block", "values") and scope != "block":
        raise AssertionError("%s illegally uses a block-only concern" % vector_id)

    expected_seed = fnv_seed(root_seed, intent_id, intent_version, scope,
                             block_key, concern, revision, semantic_key)
    stream = splitmix(expected_seed)
    expected_draws = [next(stream), next(stream), next(stream)]
    candidates = candidate_text.split("|")
    if not candidates or len(set(candidates)) != len(candidates):
        raise AssertionError("%s has duplicate or empty candidates" % vector_id)
    canonical = sorted(candidates)
    selected = canonical[next_int(splitmix(expected_seed), len(canonical))]

    actual = [
        vector_id, root_text, intent_id, intent_version_text, scope, block_key,
        concern, revision_text, semantic_key, str(expected_seed),
        str(expected_draws[0]), str(expected_draws[1]), str(expected_draws[2]),
        candidate_text, selected,
    ]
    if actual != row:
        raise AssertionError(
            "%s literal mismatch:\nexpected %r\nactual   %r" %
            (vector_id, row, actual))
    if selection not in canonical:
        raise AssertionError("%s selection is outside its canonical population" % vector_id)
    return expected_seed


def main():
    repository = Path(__file__).resolve().parents[2]
    tsv_rows = read_tsv(repository / "tests" / "contracts" / "task46-seed-vectors.tsv")
    java_rows = read_java_literals(
        repository / "src" / "com" / "lushprojects" / "circuitjs1" / "client"
        / "Task46ContractVectors.java")
    if tsv_rows != java_rows:
        raise AssertionError("TSV rows and Java literal rows differ")
    seeds = [verify_row(row) for row in tsv_rows]

    roots = {int(row[1], 10) for row in tsv_rows}
    required_roots = {
        0, -1, 2147483647, 2147483648, -2147483648, -2147483649,
        9007199254740991, 9007199254740992, 9007199254740993,
        9223372036854775807, -9223372036854775808,
    }
    if not required_roots.issubset(roots):
        raise AssertionError("fixed corpus is missing a required seed boundary")
    if {row[4] for row in tsv_rows} != {"device", "block"}:
        raise AssertionError("fixed corpus does not cover both scopes")
    if {row[6] for row in tsv_rows} != {
        "topology", "block", "values", "support", "fault", "scenario",
        "placement", "routing", "presentation",
    }:
        raise AssertionError("fixed corpus does not cover every named concern")
    if seeds[11] == seeds[12] or seeds[13] == seeds[14]:
        raise AssertionError("adjacent-field ambiguity pair unexpectedly collided")
    if not any(row[13] != "|".join(sorted(row[13].split("|"))) for row in tsv_rows):
        raise AssertionError("fixed corpus does not exercise candidate canonicalization")

    print("PASS: Task46 independent seed oracle %d vectors; literal TSV/Java correspondence exact" % len(tsv_rows))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (AssertionError, OSError, ValueError) as error:
        print("FAIL: Task46 independent seed oracle: %s" % error)
        sys.exit(1)
