"""Synthetic-only tests for pinned qualification console framing."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location(
    "_tsj_normalizer_qualification_stdout_subject", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _NORMALIZER
_SPEC.loader.exec_module(_NORMALIZER)


_EXPECTED_KEYS = (
    "schema", "status", "manifestSha256", "readerSha256", "resultSha256",
    "operation1Sha256", "trialDisposition", "netCount", "controlDisposition",
    "trialOutcomeDisposition", "controlTraversedCount", "trialTraversedCount",
    "controlLayoutConnectivity", "trialLayoutConnectivity", "layoutQualification",
    "routingTreatmentSelected", "productionAdopted", "feasibilityClaimed",
    "playable100Accepted",
)
_QUALIFICATION_READER_SHA256 = (
    "84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52"
)
_SUMMARY_BYTES = 812


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _fixture():
    manifest_raw = b"SYNTHETIC_ONLY_RUN_MANIFEST"
    operation_raw = b"SYNTHETIC_ONLY_OPERATION_1"
    result_raw = b"SYNTHETIC_ONLY_RESULT"
    summary = {
        "schema": "tsj-failure-cohort-reader-v1",
        "status": "PASS_OBSERVED_FAILURE_COHORT_DIAGNOSIS_ONLY",
        "manifestSha256": _sha(manifest_raw),
        "readerSha256": _QUALIFICATION_READER_SHA256,
        "resultSha256": _sha(result_raw),
        "operation1Sha256": _sha(operation_raw),
        "trialDisposition": "TRIAL_EXECUTED",
        "netCount": 60,
        "controlDisposition": "NO_PATH",
        "trialOutcomeDisposition": "CAP_REJECTED",
        "controlTraversedCount": 44,
        "trialTraversedCount": 43,
        "controlLayoutConnectivity": None,
        "trialLayoutConnectivity": None,
        "layoutQualification": False,
        "routingTreatmentSelected": False,
        "productionAdopted": False,
        "feasibilityClaimed": False,
        "playable100Accepted": False,
    }
    role_bytes = {"N_PRIOR_RUN_MANIFEST": manifest_raw}
    d_parent = {"elapsedMs": 112691.18650000001}
    d_result = {"totalElapsedMs": 112943.8217}
    prior_result = {"control": {}, "trial": {}}
    return summary, d_parent, d_result, prior_result, role_bytes, {}, operation_raw, result_raw


def _summary_json(summary):
    return json.dumps(summary, ensure_ascii=True, separators=(",", ":"),
                      allow_nan=False).encode("ascii")


def _framed_summary(summary, terminator=b"\r\n"):
    payload = _summary_json(summary)
    padding = _SUMMARY_BYTES - len(payload) - len(terminator)
    if padding < 0:
        raise AssertionError("SYNTHETIC_SUMMARY_DOES_NOT_FIT")
    raw = payload + b" " * padding + terminator
    if len(raw) != _SUMMARY_BYTES:
        raise AssertionError("SYNTHETIC_SUMMARY_SIZE")
    return raw


def _validate(raw):
    return _NORMALIZER._validate_qualification_summary(raw, *_fixture()[1:])


class QualificationStdoutFramingTests(unittest.TestCase):
    def _assert_code(self, expected, raw):
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _validate(raw)
        self.assertEqual(caught.exception.code, expected)

    def test_full_validator_accepts_exact_812_byte_crlf_console_record(self):
        summary, *_rest = _fixture()
        self.assertEqual(tuple(summary), _EXPECTED_KEYS)
        raw = _framed_summary(summary, b"\r\n")
        self.assertEqual(len(raw), 812)
        self.assertEqual(_validate(raw), summary)

    def test_full_validator_accepts_exact_812_byte_lf_console_record(self):
        summary, *_rest = _fixture()
        self.assertEqual(tuple(summary), _EXPECTED_KEYS)
        raw = _framed_summary(summary, b"\n")
        self.assertEqual(len(raw), 812)
        self.assertEqual(_validate(raw), summary)

    def test_full_validator_rejects_missing_console_terminator(self):
        summary, *_rest = _fixture()
        self._assert_code(
            "QUALIFICATION_STDOUT_FRAMING", _framed_summary(summary, b""))

    def test_full_validator_rejects_multiple_console_records(self):
        tail = b"\r\n{}\r\n"
        first = b"{}"
        raw = first + b" " * (_SUMMARY_BYTES - len(first) - len(tail)) + tail
        self.assertEqual(len(raw), _SUMMARY_BYTES)
        self._assert_code("QUALIFICATION_STDOUT_FRAMING", raw)

    def test_full_validator_rejects_malformed_json(self):
        raw = b"{" + b" " * (_SUMMARY_BYTES - 3) + b"\r\n"
        self.assertEqual(len(raw), _SUMMARY_BYTES)
        self._assert_code("QUALIFICATION_STDOUT_JSON", raw)

    def test_full_validator_rejects_oversized_summary(self):
        raw = b" " * 1023 + b"\r\n"
        self.assertEqual(len(raw), 1025)
        self._assert_code("QUALIFICATION_STDOUT_SIZE", raw)

    def test_full_validator_rejects_changed_claim(self):
        summary, *_rest = _fixture()
        summary["playable100Accepted"] = True
        self._assert_code(
            "QUALIFICATION_CLAIM_TYPE_OR_VALUE",
            _framed_summary(summary, b"\r\n"))

    def test_strict_jsonl_still_rejects_crlf_and_accepts_lf(self):
        accepted = _NORMALIZER._json_load(
            b'{"record":"SYNTHETIC_ONLY"}\n', 128, "STRICT_JSONL",
            allow_float=False, line=True)
        self.assertEqual(accepted, {"record": "SYNTHETIC_ONLY"})
        self._assert_jsonl_code("STRICT_JSONL_FRAMING", b'{"record":"SYNTHETIC_ONLY"}\r\n')

    def _assert_jsonl_code(self, expected, raw):
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._json_load(
                raw, 128, "STRICT_JSONL", allow_float=False, line=True)
        self.assertEqual(caught.exception.code, expected)


if __name__ == "__main__":
    unittest.main()
