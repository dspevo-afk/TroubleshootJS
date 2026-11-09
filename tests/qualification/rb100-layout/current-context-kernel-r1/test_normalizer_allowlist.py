"""Regression tests for the public frozen normal-oracle allowlist metadata.

Run with explicit ``--allowlist`` and ``--normalizer-source`` paths.  This file
reads only that metadata file and the supplied normalizer source.  It never
opens any path listed in a role row and never supplies role bodies.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


_ALLOWLIST_SHA256 = "c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91"
_AUTHORITY_SOURCE_SHA256 = "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321"
_HELPER_SOURCE_SHA256 = "2cebcf6a26c61a3600c9ffcf5e1f1268e62bd966598a57e7d71587173d5b8724"


def _task_arguments():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--allowlist")
    parser.add_argument("--normalizer-source")
    args, remaining = parser.parse_known_args()
    sys.argv[:] = [sys.argv[0], *remaining]
    if not args.allowlist or not args.normalizer_source:
        raise RuntimeError("PASS_EXPLICIT_ALLOWLIST_AND_NORMALIZER_SOURCE")
    return args


_ARGS = _task_arguments()
_NORMALIZER_SOURCE = Path(_ARGS.normalizer_source)
_NORMALIZER_SPEC = importlib.util.spec_from_file_location(
    "_tsj_allowlist_regression_normalizer", _NORMALIZER_SOURCE)
if _NORMALIZER_SPEC is None or _NORMALIZER_SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_NORMALIZER_SPEC)
sys.modules[_NORMALIZER_SPEC.name] = _NORMALIZER
_NORMALIZER_SPEC.loader.exec_module(_NORMALIZER)

_ALLOWLIST_BYTES = Path(_ARGS.allowlist).read_bytes()
if (not 0 < len(_ALLOWLIST_BYTES) <= 262144
        or hashlib.sha256(_ALLOWLIST_BYTES).hexdigest() != _ALLOWLIST_SHA256):
    raise RuntimeError("PINNED_ALLOWLIST_METADATA_REQUIRED_BEFORE_DECODE")


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _private_json_bytes(value: dict) -> bytes:
    return json.dumps(value, ensure_ascii=True, sort_keys=False,
                      separators=(",", ":"), allow_nan=False).encode("ascii")


def _callable_maps():
    calls = []

    def synthetic_callback(*_args, **_kwargs):
        calls.append("called")
        return None

    kernel = {name: synthetic_callback for name in _NORMALIZER.KERNEL_CALLBACK_KEYS}
    codec = {name: synthetic_callback for name in _NORMALIZER.CODEC_CALLBACK_KEYS}
    return kernel, codec, calls


class PublicAllowlistRegressionTests(unittest.TestCase):
    def test_public_allowlist_metadata_is_pinned_and_accepts_null_d_summary_limits(self):
        self.assertEqual(_sha(_ALLOWLIST_BYTES), _ALLOWLIST_SHA256)
        value, pins = _NORMALIZER._validate_allowlist_bytes(_ALLOWLIST_BYTES)

        self.assertEqual(value["schema"], "tsj-full-d-v2-normal-oracle-allowlist-v1")
        self.assertEqual(value["status"], "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED")
        self.assertEqual(value["authoritySourceSha256"], _AUTHORITY_SOURCE_SHA256)
        self.assertEqual(value["helperSourceSha256"], _HELPER_SOURCE_SHA256)
        self.assertEqual(value["roleCount"], 20)
        self.assertEqual(set(pins), set(_NORMALIZER.EXPECTED_ROLE_NAMES))

        d_rows = [row for row in value["rows"] if row["root"] == "D"
                  and row["role"].startswith("D_PRIOR_QUALIFICATION_")]
        self.assertEqual(len(d_rows), 7)
        for row in d_rows:
            self.assertIsNone(row["expectedBytes"])
            expected_summary_limit = (
                1024 if row["role"] == "D_PRIOR_QUALIFICATION_STDOUT_TXT" else None)
            self.assertEqual(row["summaryParseMaxBytes"], expected_summary_limit)

    def test_public_normalizer_authenticates_metadata_before_missing_role_map(self):
        kernel, codec, callback_calls = _callable_maps()

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER.normalize_prior({}, _ALLOWLIST_BYTES, {}, kernel, codec)

        self.assertEqual(caught.exception.code, "ROLE_BYTE_MAP")
        self.assertEqual(callback_calls, [])

    def test_public_normalizer_rejects_mutated_metadata_at_fixed_digest_gate(self):
        value = json.loads(_ALLOWLIST_BYTES.decode("ascii"))
        value["readingEnabled"] = True
        mutated = _private_json_bytes(value)
        kernel, codec, callback_calls = _callable_maps()

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER.normalize_prior({}, mutated, {}, kernel, codec)

        self.assertEqual(caught.exception.code, "ALLOWLIST_SHA256")
        self.assertEqual(callback_calls, [])

    def test_private_hash_seam_rejects_extra_allowlist_key(self):
        value = json.loads(_ALLOWLIST_BYTES.decode("ascii"))
        value["unexpectedSyntheticField"] = True
        mutated = _private_json_bytes(value)

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_allowlist_bytes(mutated, expected_sha256=_sha(mutated))

        self.assertEqual(caught.exception.code, "ALLOWLIST_KEYS")

    def test_private_hash_seam_rejects_malformed_d_summary_limit(self):
        value = json.loads(_ALLOWLIST_BYTES.decode("ascii"))
        owner = next(row for row in value["rows"]
                     if row["role"] == "D_PRIOR_QUALIFICATION_OWNER_JSON")
        owner["summaryParseMaxBytes"] = 0
        mutated = _private_json_bytes(value)

        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_allowlist_bytes(mutated, expected_sha256=_sha(mutated))

        self.assertEqual(caught.exception.code, "ALLOWLIST_SUMMARY_LIMIT")


if __name__ == "__main__":
    unittest.main()
