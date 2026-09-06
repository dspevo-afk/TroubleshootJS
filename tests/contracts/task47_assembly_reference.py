#!/usr/bin/env python3
"""Independent Task 47 selection oracle.

The integer derivation and SplitMix64 implementation are imported from the
accepted Task 46 reference.  This file contains only the Task 47 candidate
populations and receipt parser, so the Java selector cannot validate itself.
"""

from __future__ import print_function

import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True


SEEDS = [
    0,
    1,
    2,
    3,
    -(1 << 63),
    (1 << 63) - 1,
    -9007199254740993,
    9007199254740993,
]


def load_task46_reference():
    path = Path(__file__).resolve().with_name("task46_seed_reference.py")
    spec = importlib.util.spec_from_file_location("task46_seed_reference", str(path))
    if spec is None or spec.loader is None:
        raise AssertionError("could not load Task 46 reference")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def expected(reference, root_seed, scope, block_key, concern, semantic_key,
             candidates):
    derived = reference.fnv_seed(
        root_seed, "resistive-coupling", 1, scope, block_key, concern, 1,
        semantic_key)
    stream = reference.splitmix(derived)
    canonical = sorted(candidates)
    return canonical[reference.next_int(stream, len(canonical))]


def parse_receipt(path):
    rows = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        if not raw:
            continue
        fields = raw.split(";", 4)
        if len(fields) != 5:
            raise AssertionError("receipt row has the wrong shape")
        seed_text, source_text, load_text, fault, descriptor = fields
        seed = int(seed_text.split("=", 1)[1], 10)
        source = float(source_text.split("=", 1)[1])
        load = float(load_text.split("=", 1)[1])
        fault = fault.split("=", 1)[1]
        descriptor = descriptor.split("=", 1)[1]
        if seed in rows:
            raise AssertionError("receipt contains a duplicate seed")
        rows[seed] = (source, load, fault, descriptor)
    return rows


def verify(path):
    reference = load_task46_reference()
    actual = parse_receipt(path)
    if set(actual) != set(SEEDS):
        raise AssertionError("receipt seed set differs from the eight frozen seeds")
    for seed in SEEDS:
        source_key = expected(reference, seed, "block", "source", "values",
                              "resistance", ["r100", "r220"])
        load_key = expected(reference, seed, "block", "load", "values",
                            "resistance", ["r1000", "r2200"])
        fault = expected(reference, seed, "device", "-", "fault",
                         "selected-fault",
                         ["source-high-resistance", "load-high-resistance"])
        source_value = {"r100": 100.0, "r220": 220.0}[source_key]
        load_value = {"r1000": 1000.0, "r2200": 2200.0}[load_key]
        actual_source, actual_load, actual_fault, descriptor = actual[seed]
        if actual_source != source_value or actual_load != load_value \
                or actual_fault != fault:
            raise AssertionError(
                "seed %d differs: expected %.1f/%.1f/%s, got %.1f/%.1f/%s" %
                (seed, source_value, load_value, fault, actual_source,
                 actual_load, actual_fault))
        if ("device-intent=resistive-coupling@1" not in descriptor or
                "generator=bounded-assembler@1" not in descriptor or
                "root-seed=" + str(seed) not in descriptor):
            raise AssertionError("seed %d descriptor is not canonical" % seed)
    print("PASS: Task47 independent assembly oracle %d seeds" % len(SEEDS))


def main(argv):
    if len(argv) != 2:
        raise AssertionError("usage: task47_assembly_reference.py RECEIPT")
    verify(argv[1])
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except (AssertionError, OSError, ValueError, KeyError) as error:
        print("FAIL: Task47 independent assembly oracle: %s" % error)
        sys.exit(1)
