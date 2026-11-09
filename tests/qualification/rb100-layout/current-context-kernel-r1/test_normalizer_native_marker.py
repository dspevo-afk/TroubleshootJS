"""Synthetic-only tests for the complete native prior marker contract."""

from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location(
    "_tsj_normalizer_native_marker_subject", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _NORMALIZER
_SPEC.loader.exec_module(_NORMALIZER)


_EXPECTED_KEYS = (
    "schema", "status", "resultSha256", "resultBytes", "elapsedNanos",
    "finalExportNanos", "totalGroups", "totalUnits", "maxGroupNanos",
    "maximumUnitsPerGroup",
)
_MARKER_SCHEMA = "tsj-failure-cohort-marker-v1"
_MARKER_STATUS = "COMPLETED_DIAGNOSTIC"
_COPY_CHUNK_BYTES = 4096


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _fixture(result_raw=b"SYNTHETIC_ONLY_PRIOR_RESULT", *,
             groups_before=10, units_before=100,
             max_group_before=400_000_000, max_units_before=6,
             cutoff_nanos=200_000_000, final_export_nanos=750_000_000,
             elapsed_nanos=1_000_000_000):
    final_export_units = 5 * ((len(result_raw) + _COPY_CHUNK_BYTES - 1)
                              // _COPY_CHUNK_BYTES) + 3
    work = {
        "groupsBeforeExport": groups_before,
        "unitsBeforeExport": units_before,
        "maxGroupNanosBeforeExport": max_group_before,
        "maximumUnitsPerGroupBeforeExport": max_units_before,
        "elapsedCutoffNanos": cutoff_nanos,
    }
    doc = {"work": work}
    marker = {
        "schema": _MARKER_SCHEMA,
        "status": _MARKER_STATUS,
        "resultSha256": _sha(result_raw),
        "resultBytes": len(result_raw),
        "elapsedNanos": elapsed_nanos,
        "finalExportNanos": final_export_nanos,
        "totalGroups": groups_before + 1,
        "totalUnits": units_before + final_export_units,
        "maxGroupNanos": max(max_group_before, final_export_nanos),
        "maximumUnitsPerGroup": max(max_units_before, final_export_units),
    }
    return marker, result_raw, doc


def _validate(marker, result_raw, doc):
    return _NORMALIZER._validate_native_marker(marker, result_raw, doc)


class NativeMarkerContractTests(unittest.TestCase):
    def _assert_code(self, expected, marker, result_raw, doc):
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _validate(marker, result_raw, doc)
        self.assertEqual(caught.exception.code, expected)

    def test_complete_marker_matches_independent_hashes_and_export_counts(self):
        marker, result_raw, doc = _fixture(result_raw=b"R" * 4096)
        self.assertEqual(tuple(marker), _EXPECTED_KEYS)
        self.assertEqual(marker["resultSha256"], hashlib.sha256(result_raw).hexdigest())
        self.assertEqual(marker["resultBytes"], 4096)
        self.assertEqual(marker["totalUnits"], 108)
        self.assertEqual(marker["totalGroups"], 11)
        self.assertEqual(_validate(marker, result_raw, doc), marker)

    def test_export_units_match_literal_chunk_boundary_oracle(self):
        cases = ((1, 8), (4095, 8), (4096, 8),
                 (4097, 13), (8192, 13), (8193, 18))
        for byte_count, expected_units in cases:
            with self.subTest(byte_count=byte_count):
                marker, result_raw, doc = _fixture(result_raw=b"R" * byte_count)
                self.assertEqual(marker["totalUnits"], 100 + expected_units)
                self.assertEqual(marker["maximumUnitsPerGroup"], expected_units)
                self.assertEqual(_validate(marker, result_raw, doc), marker)

    def test_work_maxima_and_640_group_boundary_remain_bound(self):
        marker, result_raw, doc = _fixture(
            result_raw=b"R", groups_before=639, max_group_before=1_000_000_000,
            max_units_before=32768, cutoff_nanos=85_000_000_000,
            elapsed_nanos=90_000_000_000)
        self.assertEqual(marker["totalGroups"], 640)
        self.assertEqual(marker["maxGroupNanos"], 1_000_000_000)
        self.assertEqual(marker["maximumUnitsPerGroup"], 32768)
        self.assertEqual(_validate(marker, result_raw, doc), marker)

    def test_elapsed_and_export_upper_limits_are_inclusive(self):
        marker, result_raw, doc = _fixture(
            cutoff_nanos=85_000_000_000, final_export_nanos=5_000_000_000,
            elapsed_nanos=90_000_000_000)
        self.assertEqual(marker["maxGroupNanos"], 5_000_000_000)
        self.assertEqual(_validate(marker, result_raw, doc), marker)

    def test_four_key_extra_and_missing_key_markers_reject(self):
        marker, result_raw, doc = _fixture()
        old_four = {key: marker[key] for key in _EXPECTED_KEYS[:4]}
        self._assert_code("NATIVE_MARKER_KEYS", old_four, result_raw, doc)

        extra = dict(marker)
        extra["syntheticExtra"] = "SYNTHETIC_ONLY"
        self._assert_code("NATIVE_MARKER_KEYS", extra, result_raw, doc)

        missing = dict(marker)
        del missing["maximumUnitsPerGroup"]
        self._assert_code("NATIVE_MARKER_KEYS", missing, result_raw, doc)

    def test_marker_identity_and_result_binding_mutations_reject(self):
        marker, result_raw, doc = _fixture()
        mutations = (
            {"schema": "synthetic-wrong-schema"},
            {"status": "SYNTHETIC_NOT_COMPLETED"},
            {"resultSha256": "0" * 64},
            {"resultBytes": len(result_raw) + 1},
        )
        for change in mutations:
            with self.subTest(change=change):
                changed = dict(marker)
                changed.update(change)
                self._assert_code("NATIVE_MARKER_BINDING", changed, result_raw, doc)
        self._assert_code("NATIVE_MARKER_BINDING", marker, result_raw + b"!", doc)

    def test_boolean_and_float_numeric_masquerades_reject(self):
        marker, result_raw, doc = _fixture()
        mutations = (
            {"resultBytes": True},
            {"resultBytes": float(len(result_raw))},
            {"elapsedNanos": True},
            {"finalExportNanos": float(marker["finalExportNanos"])},
            {"totalGroups": True},
            {"totalUnits": float(marker["totalUnits"])},
            {"maxGroupNanos": True},
            {"maximumUnitsPerGroup": float(marker["maximumUnitsPerGroup"])},
        )
        for change in mutations:
            with self.subTest(change=change):
                changed = dict(marker)
                changed.update(change)
                self._assert_code("NATIVE_MARKER_BINDING", changed, result_raw, doc)

    def test_group_unit_and_maximum_couplings_reject_mutations(self):
        marker, result_raw, doc = _fixture()
        for key in ("totalGroups", "totalUnits", "maxGroupNanos",
                    "maximumUnitsPerGroup"):
            with self.subTest(key=key):
                changed = dict(marker)
                changed[key] += 1
                self._assert_code("NATIVE_MARKER_BINDING", changed, result_raw, doc)

    def test_time_caps_and_elapsed_cutoff_coupling_reject(self):
        marker, result_raw, doc = _fixture()
        cases = (
            ("elapsedNanos", 90_000_000_001),
            ("finalExportNanos", 5_000_000_001),
            ("elapsedNanos", doc["work"]["elapsedCutoffNanos"]
             + marker["finalExportNanos"] - 1),
        )
        for key, value in cases:
            with self.subTest(key=key, value=value):
                changed = dict(marker)
                changed[key] = value
                self._assert_code("NATIVE_MARKER_BINDING", changed, result_raw, doc)

    def test_final_export_group_unit_cap_rejects_synthetic_oversized_result(self):
        # This exceeds the production result-byte envelope only to reach the
        # independent marker predicate for MAX_GROUP_UNITS in memory.
        result_raw = b"R" * 26_841_089
        marker, result_raw, doc = _fixture(result_raw=result_raw)
        self.assertGreater(marker["maximumUnitsPerGroup"], 32768)
        self._assert_code("NATIVE_MARKER_BINDING", marker, result_raw, doc)


if __name__ == "__main__":
    unittest.main()
