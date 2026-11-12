"""Pure tests for the authenticated prior normalizer tool-fact gate.

Run with --normalizer-source PATH --normalizer-sha SHA256. Only that selected
normalizer CODE file is read; no prior records or generated data are opened.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import types
import unittest
from pathlib import Path


def _load_selected_normalizer():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--normalizer-source", required=True)
    parser.add_argument("--normalizer-sha", required=True)
    args, unittest_args = parser.parse_known_args()
    sys.argv = [sys.argv[0], *unittest_args]

    if re.fullmatch(r"[0-9a-f]{64}", args.normalizer_sha) is None:
        raise SystemExit("--normalizer-sha must be 64 lowercase hexadecimal characters")
    source_path = Path(args.normalizer_source)
    raw = source_path.read_bytes()
    actual_sha = hashlib.sha256(raw).hexdigest()
    if actual_sha != args.normalizer_sha:
        raise SystemExit("selected normalizer source SHA-256 mismatch")

    module = types.ModuleType("_tsj_explicit_prior_normalizer_for_tool_tests")
    module.__file__ = str(source_path)
    module.__package__ = ""
    sys.modules[module.__name__] = module
    try:
        code = compile(raw, str(source_path), "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(module.__name__, None)
        raise
    return module


NORMALIZER = _load_selected_normalizer()

HISTORICAL_FIXED_TOOL_FACTS = {
    "qualificationPython": (
        171744,
        "ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec",
        "QUALIFICATION_PYTHON_TOOL_PIN",
    ),
    "rootCallPowerShell": (
        495616,
        "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f",
        "ROOT_CALL_POWERSHELL_PIN",
    ),
    "rootCallHarness": (
        2941,
        "28aab617e686d2a4eec074a46b2e2861f6fa7c008544b33f7b070f0c6d77ea1d",
        "ROOT_CALL_HARNESS_PIN",
    ),
    "processModule": (
        1040144,
        "fb07ad540e22a225a11b8a12e9dc8e5a20d88f58f89cf32a5fd347613530954c",
        "PROCESS_MODULE_PIN",
    ),
}


def _literal_historical_tool_facts():
    facts = {
        "qualificationPython": {
            "path": r"C:\fixture\python.exe",
            "bytes": HISTORICAL_FIXED_TOOL_FACTS["qualificationPython"][0],
            "sha256": HISTORICAL_FIXED_TOOL_FACTS["qualificationPython"][1],
        },
        "rootCallPowerShell": {
            "path": r"C:\fixture\powershell.exe",
            "bytes": HISTORICAL_FIXED_TOOL_FACTS["rootCallPowerShell"][0],
            "sha256": HISTORICAL_FIXED_TOOL_FACTS["rootCallPowerShell"][1],
        },
        "nativeJava": {
            "path": r"C:\fixture\java.exe",
            "bytes": 12345,
            "sha256": "a" * 64,
        },
        "rootCallHarness": {
            "path": r"C:\fixture\Invoke-RootCall-R1.ps1",
            "bytes": HISTORICAL_FIXED_TOOL_FACTS["rootCallHarness"][0],
            "sha256": HISTORICAL_FIXED_TOOL_FACTS["rootCallHarness"][1],
        },
        "processModule": {
            "path": r"C:\fixture\VerifierIsolation.psm1",
            "bytes": HISTORICAL_FIXED_TOOL_FACTS["processModule"][0],
            "sha256": HISTORICAL_FIXED_TOOL_FACTS["processModule"][1],
        },
    }
    return facts


class PriorToolFactsTests(unittest.TestCase):
    def test_literal_historical_tuple_passes_and_returns_same_dict(self):
        facts = _literal_historical_tool_facts()
        returned = NORMALIZER.validate_prior_tool_facts(facts)
        self.assertIs(returned, facts)

    def test_current_powershell_tuple_rejects_with_historical_pin_code(self):
        facts = _literal_historical_tool_facts()
        facts["rootCallPowerShell"]["sha256"] = (
            "d8f182560210d43baaf767d482ad1dc1207372522e91413b9d5e515a875e552d"
        )
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "ROOT_CALL_POWERSHELL_PIN")

    def test_each_fixed_tuple_wrong_bytes_and_hash_rejects_stably(self):
        for name, (expected_bytes, _expected_sha, error_code) in (
                HISTORICAL_FIXED_TOOL_FACTS.items()):
            with self.subTest(tool=name, changed="bytes"):
                facts = _literal_historical_tool_facts()
                facts[name]["bytes"] = expected_bytes + 1
                with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
                    NORMALIZER.validate_prior_tool_facts(facts)
                self.assertEqual(raised.exception.code, error_code)

            with self.subTest(tool=name, changed="sha256"):
                facts = _literal_historical_tool_facts()
                facts[name]["sha256"] = "0" * 64
                with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
                    NORMALIZER.validate_prior_tool_facts(facts)
                self.assertEqual(raised.exception.code, error_code)

    def test_tool_map_rejects_missing_and_extra_names(self):
        facts = _literal_historical_tool_facts()
        facts.pop("nativeJava")
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "TOOL_FACTS")

        facts = _literal_historical_tool_facts()
        facts["unexpected"] = {
            "path": r"C:\fixture\unexpected.exe", "bytes": 1, "sha256": "b" * 64,
        }
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "TOOL_FACTS")

    def test_tool_rows_reject_missing_extra_and_invalid_identity_fields(self):
        facts = _literal_historical_tool_facts()
        facts["rootCallHarness"].pop("sha256")
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "TOOL_ROOTCALLHARNESS_KEYS")

        facts = _literal_historical_tool_facts()
        facts["rootCallHarness"]["extra"] = "reject"
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "TOOL_ROOTCALLHARNESS_KEYS")

        facts = _literal_historical_tool_facts()
        facts["nativeJava"]["bytes"] = 0
        with self.assertRaises(NORMALIZER.PriorNormalizationError) as raised:
            NORMALIZER.validate_prior_tool_facts(facts)
        self.assertEqual(raised.exception.code, "TOOL_NATIVEJAVA_IDENTITY")

    def test_native_java_is_shape_checked_here_and_manifest_binding_is_deferred(self):
        facts = _literal_historical_tool_facts()
        facts["nativeJava"]["bytes"] = 1
        facts["nativeJava"]["sha256"] = "f" * 64
        self.assertIs(NORMALIZER.validate_prior_tool_facts(facts), facts)


if __name__ == "__main__":
    unittest.main()
