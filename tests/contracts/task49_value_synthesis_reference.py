#!/usr/bin/env python3
"""Independent current repeated-load/value and named-stream oracle.

The Java contract emits one row per signed seed with one value receipt for
each stable channel. This module recomputes catalog equations, both instance
scoped VALUES selections, the provider-dependent FAULT population, and the
canonical descriptor without importing the production resolver.
"""

from __future__ import print_function

import importlib.util
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True

SEEDS = [-1, 0, 1, 2, 3, -(1 << 63), (1 << 63) - 1,
         9007199254740993, -9007199254740993]
CHANNEL_KEYS = ("channel-a-load", "channel-b-load")
DRIVER_KEYS = ("channel-a-driver", "channel-b-driver")


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


def expected_driver(seed, block_key, reference):
    derived = reference.fnv_seed(seed, "controlled-indicator", 1, "block",
                                 block_key, "topology", 1,
                                 "low-side-implementation")
    return ["nmos-low-side-driver", "npn-low-side-driver"][
        reference.next_int(reference.splitmix(derived), 2)]


def expected_fault(seed, reference):
    decisions = []
    for channel, driver_key in zip(("channel-a", "channel-b"), DRIVER_KEYS):
        provider = expected_driver(seed, driver_key, reference)
        target = "RB" if provider == "npn-low-side-driver" else "RG"
        decisions.append(channel + "-driver-" + target + "-OPEN")
        decisions.append(channel + "-load-RLOAD-OPEN")
    decisions.sort()
    derived = reference.fnv_seed(seed, "controlled-indicator", 1, "device", "-",
                                 "fault", 1, "selected-fault")
    return decisions[reference.next_int(reference.splitmix(derived), len(decisions))]


def expected_descriptor(seed):
    constraints = ("tsj-constraints/1;blocks=~;components=~;diagnostic-depth=~;"
                   "domains=~;input-transitions=~;instruments=~;isolation-actions=~;"
                   "parallel-ambiguity=~;plausible-owners=~;purposeful-auxiliaries=~;"
                   "temporal-evidence=~;temporal-samples=~")
    return ("tsj-challenge/2|constraints=" + constraints +
            "|device-intent=controlled-indicator@1|difficulty-profile=controlled-indicator@1"
            "|generator=bounded-assembler@5|geometry=3|root-seed=" + str(seed))


def expected_row(seed, block_key, reference):
    rows = catalog_rows()
    ids = [row[0] for row in rows]
    derived = reference.fnv_seed(seed, "controlled-indicator", 1, "block",
                                 block_key, "values", 1, "resistance")
    index = reference.next_int(reference.splitmix(derived), len(ids))
    return rows[index]


def parse_receipt(path):
    rows = {}
    for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if (not raw or raw in ("CURRENT_VALUE_RECEIPT_BEGIN",
                               "CURRENT_VALUE_RECEIPT_END") or
                raw.startswith("PASS:")):
            continue
        fields = raw.split(";", 16)
        if len(fields) != 17 or not fields[0].startswith("seed="):
            raise AssertionError("receipt row has the wrong shape")
        values = {}
        for field in fields:
            if "=" not in field:
                raise AssertionError("receipt field has no key")
            key, value = field.split("=", 1)
            if key in values:
                raise AssertionError("receipt contains a duplicate key")
            values[key] = value
        required = ("seed", "catalog-a", "resistance-a", "rmin-a", "rmax-a",
                    "imin-a", "imax-a", "pguard-a", "catalog-b",
                    "resistance-b", "rmin-b", "rmax-b", "imin-b", "imax-b",
                    "pguard-b", "fault", "descriptor")
        if set(values) != set(required):
            raise AssertionError("receipt keys differ from the frozen schema")
        seed = int(values["seed"], 10)
        if str(seed) != values["seed"] or seed in rows:
            raise AssertionError("receipt seed is not canonical or is duplicated")
        rows[seed] = values
    return rows


def close(actual, expected, label):
    if (not math.isfinite(actual) or
            abs(actual - expected) > 1.0e-12 * max(1.0, abs(expected))):
        raise AssertionError("%s differs: %.17g != %.17g" %
                             (label, actual, expected))


def verify_recipe(observed, prefix, expected):
    labels = ("resistance", "rmin", "rmax", "imin", "imax", "pguard")
    for label, value in zip(labels, expected[1:]):
        close(float(observed[label + "-" + prefix]), value,
              label + "-" + prefix)
    if observed["catalog-" + prefix] != expected[0]:
        raise AssertionError("channel %s selected %s, expected %s" %
                             (prefix, observed["catalog-" + prefix], expected[0]))


def verify(path):
    reference = load_seed_reference()
    actual = parse_receipt(path)
    if set(actual) != set(SEEDS):
        raise AssertionError("receipt seed set differs from the frozen signed corpus")
    for seed in SEEDS:
        observed = actual[seed]
        for prefix, key in zip(("a", "b"), CHANNEL_KEYS):
            verify_recipe(observed, prefix, expected_row(seed, key, reference))
        if observed["fault"] != expected_fault(seed, reference):
            raise AssertionError("seed %d selected %s, expected the independent fault decision" %
                                 (seed, observed["fault"]))
        if observed["descriptor"] != expected_descriptor(seed):
            raise AssertionError("seed %d descriptor is not the complete canonical bounded5/schema2 descriptor" %
                                 seed)
    print("PASS: current value synthesis oracle %d seeds; bounded5/schema2 repeated-channel equations exact" % len(SEEDS))


def verify_roles(path):
    reference = load_seed_reference()
    actual = {}
    for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if not raw.startswith("A05_ROLE_VECTOR "):
            continue
        fields = dict(field.split("=", 1) for field in raw[len("A05_ROLE_VECTOR "):].split(";"))
        if set(fields) != {"seed", "a", "b", "fault"}:
            raise AssertionError("role vector fields differ")
        seed = int(fields["seed"])
        if str(seed) != fields["seed"] or seed in actual:
            raise AssertionError("duplicate or noncanonical role vector")
        actual[seed] = fields
    if set(actual) != {-1, 0, 1, 2, -(1 << 63), (1 << 63) - 1}:
        raise AssertionError("role vector corpus is incomplete")
    for seed, observed in actual.items():
        expected = {"seed": str(seed), "a": expected_driver(seed, DRIVER_KEYS[0], reference),
                    "b": expected_driver(seed, DRIVER_KEYS[1], reference),
                    "fault": expected_fault(seed, reference)}
        if observed != expected:
            raise AssertionError("ordinary role selection differs from independent stream oracle: %d" % seed)
    print("PASS: A05 independent role selection oracle %d seeds" % len(actual))


def main(argv):
    if len(argv) not in (2, 3):
        raise AssertionError("usage: task49_value_synthesis_reference.py CURRENT_VALUE_RECEIPT [A05_ROLE_RECEIPT]")
    verify(argv[1])
    if len(argv) == 3:
        verify_roles(argv[2])
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv))
    except (AssertionError, OSError, ValueError, KeyError, TypeError) as error:
        print("FAIL: current value synthesis oracle: %s" % error)
        sys.exit(1)
