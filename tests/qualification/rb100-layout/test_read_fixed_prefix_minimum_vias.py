#!/usr/bin/env python3
"""Synthetic CLI binding, source-pin, and boundary tests for minimum VIA counts."""

from __future__ import annotations

import contextlib
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import read_fixed_prefix_minimum_vias as cli


EXPECTED_CORE_SHA256 = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"


def load_pinned_stack():
    reader, directory, reader_sha = cli._load_pinned_witness_reader()
    oracle, oracle_sha = cli._load_pinned_oracle(reader, directory)
    core, core_sha = cli._load_pinned_minimum_vias(reader, oracle, directory)
    return reader, oracle, core, directory, reader_sha, oracle_sha, core_sha


def graph_payload(oracle, *, width=1, height=1, steps=None, vias=None,
                  goals=None, starts=None, case_id="MIN_VIA_CLI_SYNTHETIC"):
    cells = width * height
    return {
        "protocol": oracle.GRAPH_PROTOCOL,
        "model": oracle.GRAPH_MODEL,
        "caseId": case_id,
        "width": width,
        "height": height,
        "primary": 0,
        "steps": [0] * (cells * 2) if steps is None else list(steps),
        "vias": [False] * cells if vias is None else list(vias),
        "goals": [False] * (cells * 2) if goals is None else list(goals),
        "starts": [0] if starts is None else list(starts),
    }


class MinimumViaCliTests(unittest.TestCase):
    def test_pins_and_runs_the_synthetic_core_without_paths(self):
        reader, oracle, core, _directory, reader_sha, oracle_sha, core_sha = load_pinned_stack()
        self.assertEqual(reader_sha, cli.WITNESS_READER_SHA256)
        self.assertEqual(oracle_sha, cli.ORACLE_SHA256)
        self.assertEqual(cli.MINIMUM_VIAS_SHA256, EXPECTED_CORE_SHA256)
        self.assertEqual(core_sha, EXPECTED_CORE_SHA256)

        payload = graph_payload(
            oracle,
            width=3,
            height=1,
            steps=[2, 2, 0, 0, 0, 0],
            goals=[False, False, True, False, False, False],
            starts=[0],
            case_id="SYNTHETIC_ZERO_VIA",
        )
        graph = oracle.validate_payload(payload)
        result = core.analyze_minimum_vias(graph)
        safe = cli._validate_minimum_vias_record(reader, result, graph)

        self.assertEqual(safe["minimumViaCount"], 0)
        self.assertEqual(safe["goalState"], 2)
        self.assertEqual(safe["termination"], "GOAL_POPPED")
        self.assertEqual(safe["productionFeasibility"], "UNKNOWN")
        self.assertTrue(all(flag is False for flag in safe["acceptance"].values()))
        self.assertNotIn("predecessors", safe)
        self.assertNotIn("path", safe)

    def test_synthetic_unreachable_is_only_frontier_exhaustion(self):
        reader, oracle, core, *_rest = load_pinned_stack()
        payload = graph_payload(
            oracle,
            goals=[False, True],
            starts=[0],
            case_id="SYNTHETIC_UNREACHABLE",
        )
        graph = oracle.validate_payload(payload)

        result = cli._validate_minimum_vias_record(
            reader, core.analyze_minimum_vias(graph), graph)

        self.assertFalse(result["reachable"])
        self.assertIsNone(result["minimumViaCount"])
        self.assertEqual(result["termination"], "FRONTIER_EXHAUSTED")
        self.assertIs(result["fullReachabilityCensus"], True)

    def test_result_type_or_acceptance_drift_rejects(self):
        reader, oracle, core, *_rest = load_pinned_stack()
        graph = oracle.validate_payload(graph_payload(
            oracle,
            goals=[True, False],
            starts=[0],
            case_id="SYNTHETIC_ZERO_EDGE",
        ))
        valid = core.analyze_minimum_vias(graph)

        bad_count = dict(valid)
        bad_count["minimumViaCount"] = True
        with self.assertRaises(cli.MinimumViaInputError) as caught:
            cli._validate_minimum_vias_record(reader, bad_count, graph)
        self.assertEqual(caught.exception.code, "MINIMUM_VIAS_REACHABLE_RESULT_INVALID")

        bad_acceptance = dict(valid)
        bad_acceptance["acceptance"] = dict(valid["acceptance"], route=True)
        with self.assertRaises(cli.MinimumViaInputError) as caught:
            cli._validate_minimum_vias_record(reader, bad_acceptance, graph)
        self.assertEqual(caught.exception.code, "MINIMUM_VIAS_ACCEPTANCE_INVALID")

    def test_modified_temporary_core_cannot_forge_counts_with_unknown_false_flags(self):
        reader, oracle, _core, _directory, *_hashes = load_pinned_stack()
        with tempfile.TemporaryDirectory(prefix="minimum-vias-core-drift-") as directory:
            helper = Path(directory) / "fixed_prefix_minimum_vias.py"
            source_dir = cli._load_pinned_witness_reader()[1]
            original = (source_dir / "fixed_prefix_minimum_vias.py").read_bytes()
            forged = original + b'''\n\n# Synthetic source-drift canary: flags do not legitimize fabricated counts.\n\ndef analyze_minimum_vias(graph):\n    return {\n        "schema": "p07-fixed-prefix-minimum-vias-v1",\n        "protocol": graph.protocol,\n        "model": graph.model,\n        "caseId": graph.case_id,\n        "graphSha256": graph.graph_sha256,\n        "status": "REACHABLE",\n        "reachable": True,\n        "minimumViaCount": 0,\n        "goalState": 0,\n        "termination": "GOAL_POPPED",\n        "counters": {},\n        "fullReachabilityCensus": False,\n        "productionFeasibility": "UNKNOWN",\n        "acceptance": {"actualCaptureAttested": False, "route": False, "layout": False, "electrical": False, "q100": False},\n    }\n\nraise RuntimeError("modified helper was executed")\n'''
            helper.write_bytes(forged)
            with self.assertRaises(cli.MinimumViaInputError) as caught:
                cli._load_pinned_minimum_vias(reader, oracle, Path(directory))
            self.assertEqual(caught.exception.code, "MINIMUM_VIAS_SOURCE_SHA256_INVALID")

    def test_modified_witness_reader_is_rejected_before_execution(self):
        reader, *_rest = load_pinned_stack()
        with tempfile.TemporaryDirectory(prefix="minimum-vias-reader-drift-") as directory:
            helper = Path(directory) / "read_fixed_prefix_witness.py"
            source_dir = cli._load_pinned_witness_reader()[1]
            helper.write_bytes((source_dir / helper.name).read_bytes() +
                               b"\nraise RuntimeError('modified reader executed')\n")
            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._load_local_source(
                    Path(directory),
                    helper.name,
                    cli.WITNESS_READER_SHA256,
                    "_synthetic_modified_witness_reader",
                    "WITNESS_READER",
                )
            self.assertEqual(caught.exception.code, "WITNESS_READER_SOURCE_SHA256_INVALID")

    def test_cli_wrong_binding_and_missing_input_are_static_and_write_nothing(self):
        with tempfile.TemporaryDirectory(prefix="minimum-vias-cli-private-") as directory:
            root = Path(directory)
            summary = root / "synthetic-summary.json"
            receipt = root / "synthetic-receipt.txt"
            output = root / "result.json"
            summary.write_bytes(b"{}")
            receipt.write_bytes(b"synthetic receipt")

            wrong_binding = [
                "--summary", str(summary),
                "--summary-sha256", "0" * 64,
                "--receipt", str(receipt),
                "--receipt-sha256", cli._load_pinned_witness_reader()[0].RECEIPT_SHA256,
                "--case", "shared",
                "--output", str(output),
            ]
            output_text = io.StringIO()
            with contextlib.redirect_stdout(output_text):
                self.assertEqual(cli.main(wrong_binding), 2)
            self.assertEqual(output_text.getvalue().strip(),
                             "FAIL: SUMMARY_SHA256_ARGUMENT_INVALID")
            self.assertNotIn(str(root), output_text.getvalue())
            self.assertFalse(output.exists())

            missing = [
                "--summary", str(root / "missing-summary.json"),
                "--summary-sha256", cli._load_pinned_witness_reader()[0].SUMMARY_SHA256,
                "--receipt", str(root / "missing-receipt.txt"),
                "--receipt-sha256", cli._load_pinned_witness_reader()[0].RECEIPT_SHA256,
                "--case", "shared",
                "--output", str(output),
            ]
            output_text = io.StringIO()
            with contextlib.redirect_stdout(output_text):
                self.assertEqual(cli.main(missing), 2)
            self.assertEqual(output_text.getvalue().strip(), "FAIL: SUMMARY_PATH_UNAVAILABLE")
            self.assertNotIn(str(root), output_text.getvalue())
            self.assertNotIn("PASS", output_text.getvalue())
            self.assertFalse(output.exists())

    def test_synthetic_summary_bytes_fail_before_receipt_line_parsing(self):
        reader, *_rest = load_pinned_stack()
        with tempfile.TemporaryDirectory(prefix="minimum-vias-cli-synthetic-") as directory:
            root = Path(directory)
            summary = root / "summary.json"
            receipt = root / "receipt.txt"
            output = root / "output.json"
            summary.write_bytes(b"{}")
            receipt.write_bytes(b"not parsed graph data\n")
            argv = [
                "--summary", str(summary),
                "--summary-sha256", reader.SUMMARY_SHA256,
                "--receipt", str(receipt),
                "--receipt-sha256", reader.RECEIPT_SHA256,
                "--case", "separate",
                "--output", str(output),
            ]
            output_text = io.StringIO()
            with contextlib.redirect_stdout(output_text):
                result = cli.main(argv)
            self.assertEqual(result, 2)
            self.assertEqual(output_text.getvalue().strip(), "FAIL: SUMMARY_SHA256_MISMATCH")
            self.assertFalse(output.exists())

    def test_output_writer_is_exclusive_and_path_preflight_allows_missing_leaf(self):
        reader, *_rest = load_pinned_stack()
        with tempfile.TemporaryDirectory(prefix="minimum-vias-output-") as directory:
            path = Path(directory) / "new-report.json"
            prepared = reader._prepare_output_path(str(path))
            self.assertEqual(prepared, path)
            reader._write_new_output(prepared, b'{"synthetic":true}\n')
            self.assertEqual(path.read_bytes(), b'{"synthetic":true}\n')
            with self.assertRaises(reader.WitnessInputError) as caught:
                reader._prepare_output_path(str(path))
            self.assertEqual(caught.exception.code, "OUTPUT_ALREADY_EXISTS")

    def test_actual_ntfs_junction_output_path_rejects(self):
        if os.name != "nt":
            self.skipTest("NTFS junction test is Windows-specific")
        reader, *_rest = load_pinned_stack()
        with tempfile.TemporaryDirectory(prefix="minimum-vias-junction-") as directory:
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
                detail = (result.stdout + result.stderr).replace(str(junction), "<junction>")
                detail = detail.replace(str(target), "<target>")
                self.fail("mklink /J failed: " + detail.strip())
            try:
                self.assertTrue(junction.is_junction())
                with self.assertRaises(reader.WitnessInputError) as caught:
                    reader._prepare_output_path(str(junction / "output.json"))
                self.assertEqual(caught.exception.code, "OUTPUT_REPARSE_PATH")
            finally:
                if junction.is_junction():
                    os.rmdir(junction)


if __name__ == "__main__":
    unittest.main()
