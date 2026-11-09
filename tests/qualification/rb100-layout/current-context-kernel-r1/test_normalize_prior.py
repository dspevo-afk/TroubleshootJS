"""Synthetic-only tests for the pure prior normalizer.

No test reads the published allowlist, a real prior role, a manifest path, or
any protected/held-out body.  The synthetic byte-map and receipt fragments
below exercise private parsing/authentication seams only; none can pass the
public fixed-SHA admission path.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).resolve().with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location("_tsj_normalize_prior_under_test", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _NORMALIZER
_SPEC.loader.exec_module(_NORMALIZER)


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _synthetic_pins():
    """SYNTHETIC_ONLY pins for the lower-level byte/hash seam."""
    return {
        role: {"maxBytes": 16, "expectedBytes": 0, "sha256": _sha(b"")}
        for role in _NORMALIZER.EXPECTED_ROLE_NAMES
    }


def _synthetic_authority_material():
    """SYNTHETIC_ONLY authority fields; never passed through public admission."""
    return {"schema": "tsj-q100-current-source-authority-v1", "sourceSha256": "a" * 64}


class NormalizePriorTests(unittest.TestCase):
    def test_static_sha256_pin_inventory_is_complete_and_well_formed(self):
        pin_names = _NORMALIZER.STATIC_SHA256_PIN_NAMES
        actual_hash_constants = {
            name for name, value in vars(_NORMALIZER).items()
            if name.endswith("_SHA256") and type(value) is str
        }
        self.assertEqual(len(pin_names), len(set(pin_names)))
        self.assertEqual(set(pin_names), actual_hash_constants)
        self.assertTrue(all(_NORMALIZER.HEX64.fullmatch(getattr(_NORMALIZER, name))
                            for name in pin_names))
        _NORMALIZER._validate_static_sha256_pins()

    def test_malformed_static_pin_rejects_before_allowlist_or_role_authentication(self):
        callbacks = {name: (lambda *args, **kwargs: None)
                     for name in _NORMALIZER.KERNEL_CALLBACK_KEYS}
        codec = {name: (lambda *args, **kwargs: None)
                 for name in _NORMALIZER.CODEC_CALLBACK_KEYS}
        original = _NORMALIZER.N_PRIOR_BUILDER_SHA256
        try:
            _NORMALIZER.N_PRIOR_BUILDER_SHA256 = "malformed"
            with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
                _NORMALIZER.normalize_prior({}, b"{}", {}, callbacks, codec)
            self.assertEqual(caught.exception.code,
                             "STATIC_SHA256_PIN_N_PRIOR_BUILDER_SHA256")
        finally:
            _NORMALIZER.N_PRIOR_BUILDER_SHA256 = original

    def test_published_allowlist_sha_is_fixed_and_public_path_rejects_synthetic_bytes(self):
        self.assertEqual(
            _NORMALIZER.ALLOWLIST_SHA256,
            "c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91",
        )
        callbacks = {name: (lambda *args, **kwargs: None)
                     for name in _NORMALIZER.KERNEL_CALLBACK_KEYS}
        codec = {name: (lambda *args, **kwargs: None)
                 for name in _NORMALIZER.CODEC_CALLBACK_KEYS}
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER.normalize_prior({}, b"{}", {}, callbacks, codec)
        self.assertEqual(caught.exception.code, "ALLOWLIST_SHA256")

    def test_synthetic_role_hash_map_checks_every_role_without_decoding(self):
        pins = _synthetic_pins()
        role_bytes = {role: b"" for role in _NORMALIZER.EXPECTED_ROLE_NAMES}
        self.assertEqual(_NORMALIZER._authenticate_role_bytes(role_bytes, pins), 0)
        self.assertEqual(len(role_bytes), 20)

    def test_synthetic_role_hash_mismatch_is_rejected(self):
        pins = _synthetic_pins()
        role_bytes = {role: b"" for role in _NORMALIZER.EXPECTED_ROLE_NAMES}
        role_bytes["N_PRIOR_RESULT"] = b"SYNTHETIC"
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._authenticate_role_bytes(role_bytes, pins)
        self.assertEqual(caught.exception.code, "ROLE_EXACT_BYTES")

    def test_host_receipt_parser_preserves_finite_float_and_context_mode_rejects_it(self):
        raw = b'{"elapsedMs":112691.18650000001}'
        host_value = _NORMALIZER._json_load(raw, 1024, "SYNTHETIC_HOST",
                                             allow_float=True)
        self.assertIs(type(host_value["elapsedMs"]), float)
        self.assertEqual(repr(host_value["elapsedMs"]), "112691.18650000001")
        with self.assertRaises(_NORMALIZER.PriorNormalizationError):
            _NORMALIZER._json_load(raw, 1024, "SYNTHETIC_CONTEXT",
                                    allow_float=False)

    def test_console_parser_accepts_windows_line_ending_but_not_embedded_lines(self):
        value = _NORMALIZER._console_json(b'{"status":"SYNTHETIC"}\r\n', 1024,
                                           "SYNTHETIC_CONSOLE")
        self.assertEqual(value, {"status": "SYNTHETIC"})
        with self.assertRaises(_NORMALIZER.PriorNormalizationError):
            _NORMALIZER._console_json(b'{"status":"SYNTHETIC"}\r\nextra\n', 1024,
                                       "SYNTHETIC_CONSOLE")

    def test_synthetic_frozen_authority_digest_detects_mutation(self):
        value = _synthetic_authority_material()
        value["authoritySha256"] = _sha(json.dumps(
            value, ensure_ascii=True, sort_keys=False, separators=(",", ":"),
            allow_nan=False).encode("ascii"))
        _NORMALIZER._validate_frozen_digest(value, "authoritySha256", "SYNTHETIC_AUTHORITY")
        value["sourceSha256"] = "b" * 64
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_frozen_digest(value, "authoritySha256", "SYNTHETIC_AUTHORITY")
        self.assertEqual(caught.exception.code, "SYNTHETIC_AUTHORITY_BINDING")

    def test_current_source_authority_is_acyclic_exact_and_detached(self):
        value = _synthetic_authority_material()
        value["authoritySha256"] = _sha(json.dumps(
            value, ensure_ascii=True, sort_keys=False, separators=(",", ":"),
            allow_nan=False).encode("ascii"))
        result = _NORMALIZER._validate_current_source_authority(value)
        self.assertEqual(result, value)
        self.assertIsNot(result, value)
        value["currentRunManifestSha256"] = "b" * 64
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_current_source_authority(value)
        self.assertEqual(caught.exception.code, "CURRENT_SOURCE_AUTHORITY_KEYS")

    def test_unbounded_integer_timing_rejects_without_overflow(self):
        self.assertTrue(_NORMALIZER._finite_number(1))
        self.assertTrue(_NORMALIZER._finite_number(1.5))
        for value in (True, float('inf'), float('nan'), 10**1000, -(10**1000)):
            with self.subTest(kind=type(value).__name__):
                self.assertFalse(_NORMALIZER._finite_number(value))

    def test_synthetic_callback_error_uses_plain_value_error_code(self):
        callbacks = {"validate_operation": lambda *args: (_ for _ in ()).throw(
            ValueError("P07_SYNTHETIC_REJECTION"))}
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._parse_prior_operation(
                b"SYNTHETIC", {}, "12345678-1234-4234-8234-123456789abc",
                "a" * 64, callbacks)
        self.assertEqual(caught.exception.code,
                         "OP1_P07_VALIDATION_P07_SYNTHETIC_REJECTION")

    def test_json_clone_separates_synthetic_shared_containers(self):
        nested = {"row": [1, 2]}
        copied = _NORMALIZER._clone_json({"left": nested, "right": nested})
        self.assertEqual(copied["left"], copied["right"])
        self.assertIsNot(copied["left"], copied["right"])
        self.assertIsNot(copied["left"]["row"], copied["right"]["row"])

    def test_synthetic_pad_identity_rejects_wrong_census(self):
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._canonical_pad_identity([])
        self.assertEqual(caught.exception.code, "PAD_LAND_COUNT")


if __name__ == "__main__":
    unittest.main()
