"""Synthetic AUTHORED P07 receipt tests against the pinned pure parser."""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


HERE = Path(__file__).resolve().parent
KERNEL_PATH = HERE.parent / "current-context-kernel-r1" / "prior_kernel.py"
FIXTURE_PATH = HERE / "synthetic_authored_p07.py"
KERNEL_SHA256 = "f87c3ef799a47f14955240111d7af20737db3c329242c036f449eb1002f11873"
FIXTURE_SHA256 = "4f80ec976e565a585db061207baee63ccfedf52fb5c64c627895ccbcd890d8f7"
RUN_ID = "1c6b89c7-7cc2-4fb2-96ab-3438dce474ab"
SOURCE_SHA256 = "1" * 64
WIRE_SHA256 = "2" * 64


def _load_pinned_source(path, module_name, expected_sha256):
    raw = path.read_bytes()
    if len(raw) > 4 * 1024 * 1024:
        raise AssertionError("PINNED_SOURCE_LIMIT")
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise AssertionError("PINNED_SOURCE_SHA256")
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise AssertionError("PINNED_SOURCE_LOADER")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    try:
        spec.loader.exec_module(module)
    except BaseException:
        sys.modules.pop(module_name, None)
        raise
    return module


def _canonical_jsonl(rows):
    return b"".join(
        (json.dumps(row, ensure_ascii=True, sort_keys=True,
                    separators=(",", ":"), allow_nan=False) + "\n").encode("ascii")
        for row in rows
    )


def _rows(raw):
    return [json.loads(line) for line in raw.splitlines()]


class SyntheticAuthoredP07Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.kernel = _load_pinned_source(
            KERNEL_PATH, "_full_d_synthetic_pinned_prior_kernel", KERNEL_SHA256)
        cls.fixture = _load_pinned_source(
            FIXTURE_PATH, "_full_d_synthetic_authored_p07", FIXTURE_SHA256)
        cls.context = cls.fixture.make_physical_context()
        cls.built = cls.fixture.build_authored_operation1(
            run_id=RUN_ID,
            source_sha256=SOURCE_SHA256,
            wire_sha256=WIRE_SHA256,
            physical_context=cls.context,
            branch_net_id="D_COIL_LOW",
        )

    def validate(self, raw=None, *, run_id=RUN_ID):
        return self.kernel.validate_operation(
            self.built["raw"] if raw is None else raw,
            self.built["entry"],
            SOURCE_SHA256,
            WIRE_SHA256,
            1,
            run_id,
            "AUTHORED",
        )

    def reject(self, code, rows, *, run_id=RUN_ID):
        with self.assertRaises(ValueError) as caught:
            self.validate(_canonical_jsonl(rows), run_id=run_id)
        self.assertEqual(getattr(caught.exception, "code", str(caught.exception)), code)

    def test_authored_operation1_is_accepted_by_pinned_p07(self):
        parsed = self.validate()
        pads = parsed["pads"]
        net_ids = {row["netId"] for row in pads}
        component_ids = {row["componentId"] for row in pads}
        self.assertEqual((len(pads), len(component_ids), len(net_ids)), (246, 100, 60))
        self.assertEqual(parsed["header"]["mode"], "AUTHORED")
        self.assertEqual(parsed["header"]["candidate"]["rootSeed"], "9007199254740993")
        self.assertEqual(parsed["header"]["candidate"]["placementSeed"], "-6587958367974952701")
        self.assertEqual(parsed["header"]["candidate"]["routingSeed"], "8445301674744508279")
        self.assertEqual(parsed["header"]["runId"], RUN_ID)
        self.assertEqual(parsed["header"]["sourceManifestSha256"], SOURCE_SHA256)
        self.assertEqual(parsed["header"]["wireSha256"], WIRE_SHA256)
        self.assertEqual(parsed["branches"][0]["snapshot"]["netId"], "D_COIL_LOW")
        self.assertEqual(parsed["branches"][0]["snapshot"]["rootPadId"], "PAD_042")
        self.assertEqual(parsed["branches"][0]["snapshot"]["startPadId"], "PAD_162")
        self.assertEqual(parsed["choices"][0]["nearest"], "PAD_162")
        self.assertEqual([group["phase"] for group in parsed["groups"]], ["ADVANCE", "REPORT"])
        self.assertEqual(parsed["result"]["route"], "REQUEST_FINALIZED")

    def test_reusable_physical_context_and_independent_counts(self):
        pads = self.built["pads"]
        lands = self.built["padLands"]
        self.assertEqual(self.built["physicalContext"]["gridWidth"], 100)
        self.assertEqual(self.built["physicalContext"]["gridHeight"], 100)
        self.assertEqual(len(pads), 246)
        self.assertEqual(len(lands), 246)
        self.assertEqual(len({row["netId"] for row in pads}), 60)
        self.assertEqual(len({row["componentId"] for row in pads}), 100)
        self.assertEqual(pads[42]["netId"], "D_COIL_LOW")
        self.assertEqual(self.built["expected"]["branchNetId"], "D_COIL_LOW")
        self.assertEqual(self.built["expected"]["grid"], (100, 100))
        self.assertEqual(self.built["entry"], {
            "elapsedToCloseNanos": 1_000,
            "maxFullGroupNanos": 1_000,
        })

    def test_pad_census_hash_and_work_ledger_are_independently_counted(self):
        parsed = self.validate()
        canonical_pad_rows = b"".join(
            ("\t".join(str(row[key]) for key in
                       ("padId", "netId", "componentId", "x", "y", "role")) + "\n").encode("utf-8")
            for row in parsed["pads"]
        )
        self.assertEqual(
            parsed["result"]["context"]["padCensusSha256"],
            hashlib.sha256(canonical_pad_rows).hexdigest(),
        )
        group0 = parsed["groups"][0]
        pristine_bytes = len(self.fixture.DEFAULT_PRISTINE)
        self.assertEqual(group0["serializedBeforeOwnLineBytes"],
                         sum(len(line) for line in self.built["raw"].splitlines(keepends=True)[:248]))
        self.assertEqual(group0["hashInputBytes"], 2 * pristine_bytes + len(canonical_pad_rows) + 1)
        self.assertEqual(parsed["result"]["logicalWork"]["p07OwnerSteps"], 7)
        self.assertEqual(parsed["result"]["logicalWork"]["routingOwnerSteps"], 7)

    def test_candidate_seed_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        next(row for row in rows if row["record"] == "HEADER")["candidate"]["rootSeed"] = "9007199254740992"
        self.reject("AUTHORED_CANDIDATE", rows)

    def test_net_census_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        # Keep the serialized row byte length stable so group accounting stays valid.
        next(row for row in rows if row["record"] == "PAD")["netId"] = "X_COIL_LOW"
        self.reject("PAD_CENSUS", rows)

    def test_component_census_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        # Keep the serialized row byte length stable so group accounting stays valid.
        next(row for row in rows if row["record"] == "PAD")["componentId"] = "EXTR_000"
        self.reject("PAD_CENSUS", rows)

    def test_pad_order_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        rows[1], rows[2] = rows[2], rows[1]
        self.reject("PAD_CENSUS", rows)

    def test_branch_before_pad_order_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        branch_index = next(index for index, row in enumerate(rows)
                            if row["record"] == "BRANCH")
        rows[1], rows[branch_index] = rows[branch_index], rows[1]
        self.reject("PAD_VALUES_ORDER", rows)

    def test_group_ordinal_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        next(row for row in rows if row["record"] == "GROUP")["ordinal"] = 1
        self.reject("GROUP_IDENTITY", rows)

    def test_run_identity_mutation_rejects(self):
        rows = _rows(self.built["raw"])
        next(row for row in rows if row["record"] == "HEADER")["runId"] = "0" * 36
        self.reject("HEADER_IDENTITY", rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--prior-kernel-source", type=Path,
        default=HERE.parent / "current-context-kernel-r1" / "prior_kernel.py")
    parser.add_argument(
        "--fixture-builder", type=Path,
        default=HERE / "synthetic_authored_p07.py")
    args, unittest_args = parser.parse_known_args()
    KERNEL_PATH = args.prior_kernel_source
    FIXTURE_PATH = args.fixture_builder
    unittest.main(argv=[sys.argv[0], *unittest_args])
