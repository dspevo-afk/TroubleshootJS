#!/usr/bin/env python3
"""Independent current catalog/value and named-stream oracle.

The Java contract emits one bounded4/schema2 observation row per signed seed.
This module recomputes the catalog equations, stream choice, fault choice and
canonical descriptor without importing the production resolver.
"""

from __future__ import print_function

import importlib.util
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True

SEEDS = [0, 1, 2, 3, -(1 << 63), (1 << 63) - 1,
         9007199254740993, -9007199254740993]


def load_seed_reference():
    path = Path(__file__).resolve().with_name("task46_seed_reference.py")
    spec = importlib.util.spec_from_file_location("task46_seed_reference", str(path))
    if spec is None or spec.loader is None:
        raise AssertionError("could not load Task 46 seed reference")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def catalog_rows():
    rows = []
    for decade in range(7):
        for mantissa in (10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82):
            resistance = float(mantissa * (10 ** decade))
            if resistance > 10000000.0:
                continue
            rated = 0.22 if resistance == 330.0 else 0.25
            rmin = resistance * (1.0 - 0.05)
            rmax = resistance * (1.0 + 0.05)
            imin = (4.75 - 2.0 - 0.8) / rmax
            imax = (5.25 - 1.6 - 0.0) / rmin
            pguard = 5.25 * 5.25 / rmin
            if (imin >= 0.005 and imax <= 0.016 and
                    1.25 * imax <= 0.020 and 2.0 * pguard <= rated):
                rows.append(("R_CATALOG_" + str(int(resistance)), resistance,
                             rmin, rmax, imin, imax, pguard))
    return sorted(rows)


def expected_fault(seed, reference):
    derived = reference.fnv_seed(seed, "controlled-indicator", 1, "device", "-",
                                 "fault", 1, "selected-fault")
    return ["driver-rg-open", "load-rload-open"][
        reference.next_int(reference.splitmix(derived), 2)]


def expected_descriptor(seed):
    constraints = ("tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;"
                   "domains=~;input-transitions=~;instruments=~;isolation-actions=~;"
                   "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
                   "temporal-evidence=~;temporal-samples=~")
    return ("tsj-challenge/2|constraints=" + constraints +
            "|device-intent=controlled-indicator@1|difficulty-profile=controlled-indicator@1"
            "|generator=bounded-assembler@4|geometry=3|root-seed=" + str(seed))


def expected_row(seed, reference):
    rows = catalog_rows()
    ids = [row[0] for row in rows]
    derived = reference.fnv_seed(seed, "controlled-indicator", 1, "block",
                                 "load", "values", 1, "resistance")
    index = reference.next_int(reference.splitmix(derived), len(ids))
    return rows[index]


def parse_receipt(path):
    rows = {}
    for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if not raw:
            continue
        fields = raw.split(";", 9)
        if len(fields) != 10:
            raise AssertionError("receipt row has the wrong shape")
        values = {}
        for field in fields:
            if "=" not in field:
                raise AssertionError("receipt field has no key")
            key, value = field.split("=", 1)
            if key in values:
                raise AssertionError("receipt contains a duplicate key")
            values[key] = value
        required = ("seed", "catalog", "resistance", "rmin", "rmax", "imin",
                    "imax", "pguard", "fault", "descriptor")
        if set(values) != set(required):
            raise AssertionError("receipt keys differ from the frozen schema")
        seed = int(values["seed"], 10)
        if str(seed) != values["seed"] or seed in rows:
            raise AssertionError("receipt seed is not canonical or is duplicated")
        rows[seed] = values
    return rows


def close(actual, expected, label):
    if not math.isfinite(actual) or abs(actual - expected) > 1.0e-12 * max(1.0, abs(expected)):
        raise AssertionError("%s differs: %.17g != %.17g" % (label, actual, expected))


def verify(path):
    reference = load_seed_reference()
    actual = parse_receipt(path)
    if set(actual) != set(SEEDS):
        raise AssertionError("receipt seed set differs from the eight frozen seeds")
    for seed in SEEDS:
        expected = expected_row(seed, reference)
        observed = actual[seed]
        if observed["catalog"] != expected[0]:
            raise AssertionError("seed %d selected %s, expected %s" %
                                 (seed, observed["catalog"], expected[0]))
        close(float(observed["resistance"]), expected[1], "resistance")
        close(float(observed["rmin"]), expected[2], "rmin")
        close(float(observed["rmax"]), expected[3], "rmax")
        close(float(observed["imin"]), expected[4], "imin")
        close(float(observed["imax"]), expected[5], "imax")
        close(float(observed["pguard"]), expected[6], "pguard")
        if observed["fault"] != expected_fault(seed, reference):
            raise AssertionError("seed %d selected %s, expected the independent fault decision" %
                                 (seed, observed["fault"]))
        if observed["descriptor"] != expected_descriptor(seed):
            raise AssertionError("seed %d descriptor is not the complete canonical bounded4/schema2 descriptor" % seed)
    print("PASS: current value synthesis oracle %d seeds; bounded4/schema2 catalog equations exact" % len(SEEDS))


def main(argv):
    if len(argv) != 2:
        raise AssertionError("usage: task49_value_synthesis_reference.py CURRENT_VALUE_RECEIPT")
    verify(argv[1])
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except (AssertionError, OSError, ValueError, KeyError, TypeError) as error:
        print("FAIL: current value synthesis oracle: %s" % error)
        sys.exit(1)
