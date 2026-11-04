"""Small independent boundary fixtures for the cap48 source graphspec exporter."""
from __future__ import annotations

import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

import export_load12_graphspec as exporter


def _passed_report():
    return {
        "schema": "q100-quality-zero-via-reader-result-v5",
        "status": "PASS",
        "target": "LOAD12",
        "suite": "Rb100Load12ZeroViaGraphContractTest",
        "sourceSnapshotRevision": exporter.SOURCE_REVISION,
        "source": {
            "readerSha256": exporter.CURRENT_READER_SHA256,
            "pinsSha256": exporter.SOURCE_PINS_SHA256,
            "inputCount": exporter.SOURCE_INPUT_COUNT,
            "inputBytes": exporter.SOURCE_INPUT_BYTES,
        },
        "artifacts": {"inputSpecSha256": exporter.SOURCE_SPEC_SHA256},
    }


class ExportLoad12GraphspecBoundaryTests(unittest.TestCase):
    def test_cli_requires_explicit_spec_digest_and_output(self):
        parsed = exporter._parse_cli([
            "--input-spec", "source-spec.json",
            "--input-spec-sha256", exporter.SOURCE_SPEC_SHA256,
            "--output", "graphspec.json",
        ])
        self.assertEqual(parsed, (
            "source-spec.json", exporter.SOURCE_SPEC_SHA256, "graphspec.json"))
        for argv in (
                ["--input-spec", "source-spec.json", "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json", "--input-spec-sha256", "0" * 64,
                 "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json", "--input-spec-sha256",
                 exporter.SOURCE_SPEC_SHA256, "--output", "graphspec.json", "extra"]):
            with self.subTest(argv=argv), self.assertRaises(exporter.GraphspecExportError):
                exporter._parse_cli(argv)

    def test_pass_report_must_bind_load12_and_approved_artifacts(self):
        exporter._validate_report_envelope(
            _passed_report(), exporter.CURRENT_READER_SHA256,
            exporter.SOURCE_SPEC_SHA256)
        wrong_target = _passed_report()
        wrong_target["target"] = "D_SENSE"
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_IDENTITY_INVALID"):
            exporter._validate_report_envelope(
                wrong_target, exporter.CURRENT_READER_SHA256,
                exporter.SOURCE_SPEC_SHA256)
        wrong_source = _passed_report()
        wrong_source["source"]["pinsSha256"] = "0" * 64
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID"):
            exporter._validate_report_envelope(
                wrong_source, exporter.CURRENT_READER_SHA256,
                exporter.SOURCE_SPEC_SHA256)
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID"):
            exporter._validate_report_envelope(
                _passed_report(), "0" * 64, exporter.SOURCE_SPEC_SHA256)

    def test_manifest_paths_are_relative_and_canonical(self):
        self.assertEqual(
            exporter._validate_repo_relative_path("tests/contracts/Board.java"),
            "tests/contracts/Board.java")
        for value in (
                "C:/private/pins.json", "../outside.txt", "tests//Board.java",
                "tests/./Board.java", "\\\\server\\share\\pins.json"):
            with self.subTest(value=value), self.assertRaisesRegex(
                    exporter.GraphspecExportError, "SOURCE_MANIFEST_PATH_INVALID"):
                exporter._validate_repo_relative_path(value)

    def test_prefix_counts_bind_normalized_totals_to_raw_receipt_shape(self):
        normalized_pre = {
            "prefix": {"inventory": {"totalHoles": 3}},
            "prefixTraces": [{}, {}],
        }
        raw_pre = {
            "prefixInventory": {
                "totals": {"byKind": {"VIA": 3, "PLATED": 0, "NON_PLATED": 0}},
            },
            "prefixTraces": {"count": 2, "rows": [{}, {}]},
        }
        self.assertNotIn("byKind", normalized_pre["prefix"]["inventory"])
        self.assertEqual(exporter._validated_prefix_counts(normalized_pre, raw_pre),
                         (3, 2))

        mismatched = {
            "prefixInventory": {
                "totals": {"byKind": {"VIA": 2, "PLATED": 0, "NON_PLATED": 0}},
            },
            "prefixTraces": {"count": 2, "rows": [{}, {}]},
        }
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "SOURCE_PREFIX_COUNTS_MISMATCH"):
            exporter._validated_prefix_counts(normalized_pre, mismatched)

    def test_logical_root_can_have_a_nullable_tree_goal_terminal(self):
        # The logical root identity is not a claim that the captured tree
        # endpoint selected that pad as its found terminal.
        self.assertIsNone(exporter._validate_terminal_binding(
            "CSTORE_E.1", None, None))
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "SOURCE_GRAPH_TERMINAL_BINDING_INVALID"):
            exporter._validate_terminal_binding(
                "CSTORE_E.1", "REACHED_PTH.1", None)

    def test_json_export_tree_rejects_private_paths_and_non_json_numbers(self):
        exporter._assert_json_tree({
            "code": "tests/contracts/Board.java",
            "sha256": "a" * 64,
            "count": 3,
        })
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "GRAPH_SPEC_PRIVATE_PATH"):
            exporter._assert_json_tree({"artifact": "C:\\Users\\private\\receipt.json"})
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "GRAPH_SPEC_VALUE_TYPE_INVALID"):
            exporter._assert_json_tree({"notPortable": 1.0})

    def test_output_destination_must_be_fresh(self):
        with tempfile.TemporaryDirectory(prefix="tsj-graphspec-output-") as root:
            output = Path(root) / "source-graphspec.json"
            self.assertEqual(exporter._prepare_output_path(str(output)), output)
            output.write_bytes(b"preexisting")
            with self.assertRaisesRegex(exporter.GraphspecExportError,
                                        "OUTPUT_ALREADY_EXISTS"):
                exporter._prepare_output_path(str(output))

    def test_verified_graphspec_api_is_read_only(self):
        # This isolates only the API's no-write contract. Root's real source
        # reader and CLI controls remain the qualification evidence.
        fake_reader = types.ModuleType("fake_source_reader")
        fake_reader.run_validation = lambda _spec, _sha: {"status": "PASS"}
        graphspec_fixture = {"schema": exporter.SCHEMA, "fixture": True}
        with tempfile.TemporaryDirectory(prefix="tsj-graphspec-readonly-") as root:
            untouched = Path(root) / "must-not-be-created.json"
            with patch.object(
                    exporter, "_load_public_reader",
                    return_value=(fake_reader, b"reader-source", Path("reader.py"))), \
                 patch.object(exporter, "_validate_report_envelope"), \
                 patch.object(exporter, "_load_context_and_records",
                              return_value=({}, {}, {})), \
                 patch.object(exporter, "_build_graphspec",
                              return_value=graphspec_fixture), \
                 patch.object(exporter, "_write_atomic_exclusive") as writer:
                graphspec, canonical = exporter.build_verified_graphspec(
                    "fixture-spec.json", exporter.SOURCE_SPEC_SHA256)
            self.assertEqual(graphspec, graphspec_fixture)
            self.assertEqual(canonical, exporter._canonical_json(graphspec_fixture) + b"\n")
            self.assertFalse(untouched.exists())
            writer.assert_not_called()

    def test_atomic_publish_reports_temp_unlink_failure_after_link(self):
        with tempfile.TemporaryDirectory(prefix="tsj-graphspec-unlink-failure-") as root:
            output = Path(root) / "source-graphspec.json"
            real_unlink = Path.unlink
            leaked_temps = []

            def reject_owned_temp_unlink(path, *args, **kwargs):
                if path.name.startswith(".tsj-load12-graphspec-") and path.suffix == ".tmp":
                    leaked_temps.append(path)
                    raise PermissionError("synthetic unlink failure")
                return real_unlink(path, *args, **kwargs)

            with patch.object(Path, "unlink", new=reject_owned_temp_unlink):
                with self.assertRaisesRegex(
                        exporter.GraphspecExportError,
                        "OUTPUT_ATOMIC_CLEANUP_FAILED"):
                    exporter._write_atomic_exclusive(output, b"validated graphspec")
            self.assertEqual(output.read_bytes(), b"validated graphspec")
            self.assertEqual(len(leaked_temps), 1)
            self.assertTrue(leaked_temps[0].exists())
            real_unlink(leaked_temps[0])
            output.unlink()

    def test_atomic_publish_reports_raw_descriptor_close_failure(self):
        with tempfile.TemporaryDirectory(prefix="tsj-graphspec-close-failure-") as root:
            output = Path(root) / "source-graphspec.json"
            real_close = exporter.os.close

            def close_then_report_failure(descriptor):
                real_close(descriptor)
                raise OSError("synthetic descriptor close failure")

            with patch.object(exporter.os, "fdopen",
                              side_effect=OSError("synthetic fdopen failure")), \
                 patch.object(exporter.os, "close",
                              side_effect=close_then_report_failure):
                with self.assertRaisesRegex(
                        exporter.GraphspecExportError,
                        "OUTPUT_ATOMIC_CLEANUP_FAILED"):
                    exporter._write_atomic_exclusive(output, b"validated graphspec")
            self.assertFalse(output.exists())
            self.assertEqual(list(Path(root).glob(".tsj-load12-graphspec-*.tmp")), [])


if __name__ == "__main__":
    unittest.main()
