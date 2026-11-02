#!/usr/bin/env python3
"""Synthetic input and path-boundary contracts for the witness CLI."""

from __future__ import annotations

import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import read_fixed_prefix_witness as reader


def canonical_json(value):
    return json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
        allow_nan=False,
    ).encode("ascii")


def graph_line(case_id):
    return reader.GRAPH_TAG + canonical_json({"caseId": case_id, "value": 1})


class FixedPrefixWitnessReaderTests(unittest.TestCase):
    def test_strict_json_rejects_utf8_duplicates_nonfinite_and_invalid_json(self):
        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._parse_json_bytes(b"\xff", label="SYNTHETIC")
        self.assertEqual(caught.exception.code, "SYNTHETIC_UTF8_INVALID")

        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._parse_json_bytes(b'{"x":1,"x":2}', label="SYNTHETIC")
        self.assertEqual(caught.exception.code, "SYNTHETIC_JSON_DUPLICATE_KEY")

        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._parse_json_bytes(b'{"x":NaN}', label="SYNTHETIC")
        self.assertEqual(caught.exception.code, "SYNTHETIC_JSON_NONFINITE_NUMBER")

        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._parse_json_bytes(b'{"x":1e999}', label="SYNTHETIC")
        self.assertEqual(caught.exception.code, "SYNTHETIC_JSON_NONFINITE_NUMBER")

        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._parse_json_bytes(b"{", label="SYNTHETIC")
        self.assertEqual(caught.exception.code, "SYNTHETIC_JSON_INVALID")

    def test_extracts_exactly_two_canonical_graph_lines(self):
        first = graph_line("SYNTHETIC_A")
        second = graph_line("SYNTHETIC_B")
        receipt = b"ignored native text\r\n" + first + b"\r\n" + second + b"\nend\n"

        parsed = reader._extract_tagged_graph_payloads(receipt)

        self.assertEqual(len(parsed), 2)
        self.assertEqual(parsed[0][0], first[len(reader.GRAPH_TAG):])
        self.assertEqual(parsed[0][1]["caseId"], "SYNTHETIC_A")
        self.assertEqual(parsed[1][1]["caseId"], "SYNTHETIC_B")

    def test_extract_rejects_missing_duplicate_and_extra_graph_lines(self):
        line = graph_line("SYNTHETIC")
        for receipt in (
            b"no graph rows\n",
            line + b"\n",
            line + b"\n" + line + b"\n" + line + b"\n",
        ):
            with self.subTest(receipt_count=receipt.count(reader.GRAPH_TAG)):
                with self.assertRaises(reader.WitnessInputError) as caught:
                    reader._extract_tagged_graph_payloads(receipt)
                self.assertEqual(caught.exception.code, "GRAPH_LINE_COUNT_INVALID")

    def test_extract_rejects_invalid_utf8_and_malformed_graph_json(self):
        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._extract_tagged_graph_payloads(b"ignored\xff\n")
        self.assertEqual(caught.exception.code, "RECEIPT_UTF8_INVALID")

        with self.assertRaises(reader.WitnessInputError) as caught:
            reader._extract_tagged_graph_payloads(
                reader.GRAPH_TAG + b'{"caseId":"SYNTHETIC"\n'
            )
        self.assertEqual(caught.exception.code, "GRAPH_LINE_JSON_INVALID")

    def test_extract_rejects_duplicate_nonfinite_and_noncanonical_graph_json(self):
        bad_bodies = (
            b'{"caseId":"A","caseId":"B"}',
            b'{"caseId":"A","value":NaN}',
            b'{ "caseId":"A" }',
        )
        for body, code in zip(
                bad_bodies,
                ("GRAPH_LINE_JSON_DUPLICATE_KEY", "GRAPH_LINE_JSON_NONFINITE_NUMBER",
                 "GRAPH_LINE_NONCANONICAL")):
            with self.subTest(code=code):
                raw = reader.GRAPH_TAG + body
                with self.assertRaises(reader.WitnessInputError) as caught:
                    reader._extract_tagged_graph_payloads(raw + b"\n" + graph_line("OTHER"))
                self.assertEqual(caught.exception.code, code)

    def test_graph_line_size_limit_is_inclusive(self):
        body = canonical_json({"caseId": "SYNTHETIC", "value": 1})
        original_limit = reader.MAX_GRAPH_BYTES
        try:
            reader.MAX_GRAPH_BYTES = len(body)
            parsed = reader._extract_tagged_graph_payloads(
                reader.GRAPH_TAG + body + b"\n" + graph_line("SECOND")
            )
            self.assertEqual(parsed[0][0], body)

            reader.MAX_GRAPH_BYTES = len(body) - 1
            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._extract_tagged_graph_payloads(
                    reader.GRAPH_TAG + body + b"\n" + graph_line("SECOND")
                )
            self.assertEqual(caught.exception.code, "GRAPH_LINE_SIZE_INVALID")
        finally:
            reader.MAX_GRAPH_BYTES = original_limit

    def test_bounded_file_reader_accepts_exact_limit_and_rejects_overflow_and_missing(self):
        with tempfile.TemporaryDirectory(prefix="witness-reader-test-") as directory:
            exact = Path(directory) / "exact.bin"
            exact.write_bytes(b"1234")
            self.assertEqual(reader._read_bounded_input(str(exact), 4, "SYNTHETIC"), b"1234")

            over = Path(directory) / "over.bin"
            over.write_bytes(b"12345")
            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._read_bounded_input(str(over), 4, "SYNTHETIC")
            self.assertEqual(caught.exception.code, "SYNTHETIC_SIZE_INVALID")

            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._read_bounded_input(str(Path(directory) / "missing.bin"), 4, "SYNTHETIC")
            self.assertEqual(caught.exception.code, "SYNTHETIC_PATH_UNAVAILABLE")

    def test_wrong_pin_types_and_values_reject(self):
        for value in (None, 123, "A" * 64, "0" * 63, "0" * 64):
            with self.subTest(value_type=type(value).__name__):
                with self.assertRaises(reader.WitnessInputError) as caught:
                    reader._verify_sha_argument(value, reader.SUMMARY_SHA256, "PIN_INVALID")
                self.assertEqual(caught.exception.code, "PIN_INVALID")

        reader._verify_sha_argument(reader.SUMMARY_SHA256, reader.SUMMARY_SHA256, "PIN_INVALID")

    def test_pinned_source_loader_and_metrics_adapter_accept_a_synthetic_zero_edge_witness(self):
        source_dir = reader._reader_directory()
        oracle, oracle_sha, metrics, metrics_sha = reader._load_local_witness_modules(source_dir)
        self.assertEqual(oracle_sha, reader.ORACLE_SHA256)
        self.assertEqual(metrics_sha, reader.METRICS_SHA256)

        graph = oracle.validate_payload({
            "protocol": oracle.GRAPH_PROTOCOL,
            "model": oracle.GRAPH_MODEL,
            "caseId": "SYNTHETIC_ZERO_EDGE",
            "width": 1,
            "height": 1,
            "primary": 0,
            "steps": [0, 0],
            "vias": [False],
            "goals": [True, False],
            "starts": [0],
        })
        result = oracle.analyze_graph(graph)
        record = metrics.summarize_witness(
            graph,
            result,
            prefix_hole_count=0,
            maximum_vias=48,
            max_transitions=4,
            grid_pitch=10,
        )
        safe_record = reader._validate_metrics_result(record, graph)

        self.assertEqual(safe_record["witness"]["stateCount"], 1)
        self.assertEqual(safe_record["witness"]["edgeCount"], 0)
        self.assertEqual(safe_record["productionFeasibility"], "UNKNOWN")
        self.assertTrue(all(value is False for value in safe_record["acceptance"].values()))

    def test_altered_temp_metrics_helper_cannot_fabricate_nonaccepting_counts(self):
        source_dir = reader._reader_directory()
        original = (source_dir / "fixed_prefix_witness_metrics.py").read_bytes()
        forged = original + b'''\n\n# Synthetic tampering canary: the false flags do not make fabricated counts valid.\n\ndef summarize_witness(*args, **kwargs):\n    return {\n        "schema": "p07-fixed-prefix-witness-metrics-v1",\n        "caseId": "SYNTHETIC",\n        "graphSha256": "0000000000000000000000000000000000000000000000000000000000000000",\n        "witness": {"reachable": True, "stateCount": 999999, "edgeCount": 999998},\n        "historySubset": {},\n        "productionFeasibility": "UNKNOWN",\n        "acceptance": {"actualCaptureAttested": False, "route": False, "layout": False, "electrical": False, "q100": False},\n    }\n'''
        with tempfile.TemporaryDirectory(prefix="witness-metrics-drift-") as directory:
            helper_path = Path(directory) / "fixed_prefix_witness_metrics.py"
            helper_path.write_bytes(forged)

            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._load_local_source(
                    Path(directory),
                    "fixed_prefix_witness_metrics.py",
                    reader.METRICS_SHA256,
                    "_synthetic_forged_metrics",
                    "METRICS",
                )

            self.assertEqual(caught.exception.code, "METRICS_SOURCE_SHA256_INVALID")

    def test_output_preflight_allows_only_a_missing_leaf(self):
        with tempfile.TemporaryDirectory(prefix="witness-output-test-") as directory:
            path = Path(directory) / "fresh.json"
            self.assertEqual(reader._prepare_output_path(str(path)), path)
            self.assertFalse(path.exists())

            path.write_text("existing", encoding="utf-8")
            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._prepare_output_path(str(path))
            self.assertEqual(caught.exception.code, "OUTPUT_ALREADY_EXISTS")

    def test_output_preflight_rejects_an_actual_ntfs_junction(self):
        if os.name != "nt":
            self.skipTest("NTFS junction control is Windows-specific")
        with tempfile.TemporaryDirectory(prefix="witness-junction-test-") as directory:
            root = Path(directory)
            target = root / "target"
            target.mkdir()
            junction = root / "junction"
            command = 'mklink /J "{}" "{}"'.format(junction, target)
            result = subprocess.run(
                command,
                shell=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            if result.returncode != 0:
                diagnostic = (result.stdout + result.stderr)
                diagnostic = diagnostic.replace(str(junction), "<junction>")
                diagnostic = diagnostic.replace(str(target), "<target>")
                self.fail("mklink /J failed: " + diagnostic.strip())
            try:
                self.assertTrue(junction.is_junction())
                with self.assertRaises(reader.WitnessInputError) as caught:
                    reader._prepare_output_path(str(junction / "result.json"))
                self.assertEqual(caught.exception.code, "OUTPUT_REPARSE_PATH")
            finally:
                if junction.is_junction():
                    os.rmdir(junction)

    def test_cli_wrong_pin_fails_without_echoing_user_path_or_creating_output(self):
        with tempfile.TemporaryDirectory(prefix="witness-cli-secret-path-") as directory:
            summary = Path(directory) / "private-summary.json"
            receipt = Path(directory) / "private-receipt.txt"
            output = Path(directory) / "result.json"
            summary.write_bytes(b"synthetic")
            receipt.write_bytes(b"synthetic")
            argv = [
                "--summary", str(summary),
                "--summary-sha256", "0" * 64,
                "--receipt", str(receipt),
                "--receipt-sha256", reader.RECEIPT_SHA256,
                "--case", "shared",
                "--output", str(output),
            ]
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                exit_code = reader.main(argv)

            self.assertEqual(exit_code, 2)
            self.assertEqual(captured.getvalue().strip(), "FAIL: SUMMARY_SHA256_ARGUMENT_INVALID")
            self.assertNotIn(str(directory), captured.getvalue())
            self.assertFalse(output.exists())

    def test_cli_missing_input_fails_without_acceptance_output_or_file(self):
        with tempfile.TemporaryDirectory(prefix="witness-cli-missing-") as directory:
            output = Path(directory) / "result.json"
            argv = [
                "--summary", str(Path(directory) / "missing-summary.json"),
                "--summary-sha256", reader.SUMMARY_SHA256,
                "--receipt", str(Path(directory) / "missing-receipt.txt"),
                "--receipt-sha256", reader.RECEIPT_SHA256,
                "--case", "shared",
                "--output", str(output),
            ]
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                exit_code = reader.main(argv)

            self.assertEqual(exit_code, 2)
            self.assertEqual(captured.getvalue().strip(), "FAIL: SUMMARY_PATH_UNAVAILABLE")
            self.assertNotIn(str(directory), captured.getvalue())
            self.assertNotIn("PASS", captured.getvalue())
            self.assertFalse(output.exists())

    def test_argument_parser_rejects_duplicate_and_unknown_switches_without_echoing_values(self):
        bad = [
            ["--summary", "secret", "--summary", "other"],
            ["--unknown", "secret"],
        ]
        for argv in bad:
            with self.subTest(argv_length=len(argv)):
                captured = io.StringIO()
                with contextlib.redirect_stdout(captured):
                    exit_code = reader.main(argv)
                self.assertEqual(exit_code, 2)
                self.assertEqual(captured.getvalue().strip(), "FAIL: ARGUMENTS_INVALID")
                self.assertNotIn("secret", captured.getvalue())


if __name__ == "__main__":
    unittest.main()
