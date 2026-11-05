"""Small independent boundary fixtures for the cap48 source graphspec exporter."""
from __future__ import annotations

import errno
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import patch

import export_load12_graphspec as exporter


# These are independent literals for the frozen graphspec source identity.
# The live reader has moved to a different 1080-input capture.
FROZEN_READER_SHA256 = "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc"
CURRENT_QUALITY_READER_SHA256 = "17f4407a5954fe1a706b53af6181d0fc2c94acb23041b15726f74636577d3a61"
SOURCE_SPEC_SHA256 = "20c469e804d64a7740c0a9d79066841045bb672c6d2fbc26099b89c1a52f7298"
SOURCE_PINS_SHA256 = "0516a02dc18a221f0f71a50720e8c84004770f39cc588306cd463e472b72a82f"
FROZEN_INPUT_COUNT = 1079
FROZEN_INPUT_BYTES = 86_847_809
CURRENT_INPUT_COUNT = 1080


def _passed_report():
    return {
        "schema": "q100-quality-zero-via-reader-result-v5",
        "status": "PASS",
        "target": "LOAD12",
        "suite": "Rb100Load12ZeroViaGraphContractTest",
        "sourceSnapshotRevision": exporter.SOURCE_REVISION,
        "source": {
            "readerSha256": FROZEN_READER_SHA256,
            "pinsSha256": SOURCE_PINS_SHA256,
            "inputCount": FROZEN_INPUT_COUNT,
            "inputBytes": FROZEN_INPUT_BYTES,
        },
        "artifacts": {"inputSpecSha256": SOURCE_SPEC_SHA256},
    }


class ExportLoad12GraphspecBoundaryTests(unittest.TestCase):
    def test_frozen_source_identity_uses_independent_old_capture_literals(self):
        self.assertEqual(1079, FROZEN_INPUT_COUNT)
        self.assertEqual(86_847_809, FROZEN_INPUT_BYTES)
        self.assertEqual(
            "d16c7385a604aa3e126ae7fd39f98d6b8fe5753c2ea6bb2d8244dc8b74ce5cbc",
            FROZEN_READER_SHA256)
        self.assertEqual(
            "0516a02dc18a221f0f71a50720e8c84004770f39cc588306cd463e472b72a82f",
            SOURCE_PINS_SHA256)
        self.assertEqual(
            "20c469e804d64a7740c0a9d79066841045bb672c6d2fbc26099b89c1a52f7298",
            SOURCE_SPEC_SHA256)
        self.assertEqual(FROZEN_INPUT_COUNT, exporter.SOURCE_INPUT_COUNT)
        self.assertEqual(FROZEN_INPUT_BYTES, exporter.SOURCE_INPUT_BYTES)
        self.assertEqual(FROZEN_READER_SHA256, exporter.FROZEN_READER_SHA256)
        self.assertEqual(SOURCE_PINS_SHA256, exporter.SOURCE_PINS_SHA256)
        self.assertEqual(SOURCE_SPEC_SHA256, exporter.SOURCE_SPEC_SHA256)
        self.assertNotEqual(CURRENT_INPUT_COUNT, FROZEN_INPUT_COUNT)
        self.assertEqual(
            CURRENT_QUALITY_READER_SHA256,
            exporter._sha256((Path(exporter.__file__).resolve().parent /
                              "read_quality_zero_via.py").read_bytes()))
        self.assertNotEqual(CURRENT_QUALITY_READER_SHA256,
                            FROZEN_READER_SHA256)

    def test_cli_requires_the_closed_four_argument_contract(self):
        frozen_directory = "../frozen-oracle/tests/qualification/rb100-layout"
        parsed = exporter._parse_cli([
            "--input-spec", "source-spec.json",
            "--input-spec-sha256", SOURCE_SPEC_SHA256,
            "--frozen-oracle-directory", frozen_directory,
            "--output", "graphspec.json",
        ])
        self.assertEqual(parsed, (
            "source-spec.json", SOURCE_SPEC_SHA256,
            frozen_directory, "graphspec.json"))
        for argv in (
                ["--input-spec", "source-spec.json", "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json",
                 "--input-spec-sha256", SOURCE_SPEC_SHA256,
                 "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json",
                 "--input-spec-sha256", "0" * 64,
                 "--frozen-oracle-directory", frozen_directory,
                 "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json",
                 "--input-spec-sha256", SOURCE_SPEC_SHA256,
                 "--frozen-oracle-directory", str(Path.cwd()),
                 "--output", "graphspec.json"],
                ["--input-spec", "source-spec.json",
                 "--input-spec-sha256", SOURCE_SPEC_SHA256,
                 "--output", "graphspec.json", "extra"],
                ["--input-spec-sha256", SOURCE_SPEC_SHA256,
                 "--input-spec", "source-spec.json",
                 "--frozen-oracle-directory", frozen_directory,
                 "--output", "graphspec.json"]):
            with self.subTest(argv=argv), self.assertRaises(exporter.GraphspecExportError):
                exporter._parse_cli(argv)
        with self.assertRaises(TypeError):
            exporter.build_verified_graphspec("source-spec.json", SOURCE_SPEC_SHA256)

    def test_frozen_reader_resolves_relative_directory_from_spec_parent(self):
        with tempfile.TemporaryDirectory(prefix="tsj-frozen-reader-relative-") as root:
            spec_parent = Path(root) / "source"
            spec_parent.mkdir()
            spec_path = spec_parent / "source-spec.json"
            spec_path.write_bytes(b"fixture spec")
            frozen_directory = spec_parent / "frozen"
            frozen_directory.mkdir()
            (frozen_directory / "read_quality_zero_via.py").write_bytes(
                b"not the frozen reader")
            with self.assertRaisesRegex(
                    exporter.GraphspecExportError,
                    "FROZEN_READER_HASH_MISMATCH"):
                exporter._load_frozen_reader(str(spec_path), "frozen")

    def test_current_quality_reader_is_rejected_as_the_frozen_reader(self):
        current_reader = (Path(exporter.__file__).resolve().parent /
                          "read_quality_zero_via.py")
        self.assertEqual(CURRENT_QUALITY_READER_SHA256,
                         exporter._sha256(current_reader.read_bytes()))
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "FROZEN_READER_HASH_MISMATCH"):
            exporter._load_frozen_reader(
                str(current_reader.parent / "source-spec.json"), ".")

    def test_frozen_reader_rejects_missing_and_oversized_sources(self):
        with tempfile.TemporaryDirectory(prefix="tsj-frozen-reader-bounds-") as root:
            spec_parent = Path(root) / "source"
            spec_parent.mkdir()
            spec_path = spec_parent / "source-spec.json"
            spec_path.write_bytes(b"fixture spec")
            frozen_directory = spec_parent / "frozen"
            frozen_directory.mkdir()
            with self.assertRaisesRegex(exporter.GraphspecExportError,
                                        "FROZEN_READER_READ_FAILED"):
                exporter._load_frozen_reader(str(spec_path), "frozen")

            (frozen_directory / "read_quality_zero_via.py").write_bytes(
                b"x" * (2 * 1024 * 1024 + 1))
            with self.assertRaisesRegex(exporter.GraphspecExportError,
                                        "FROZEN_READER_SIZE_INVALID"):
                exporter._load_frozen_reader(str(spec_path), "frozen")

    def test_frozen_reader_rejects_absolute_and_reparse_directories(self):
        current_reader = Path(exporter.__file__).resolve().parent / "read_quality_zero_via.py"
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "FROZEN_ORACLE_DIRECTORY_INVALID"):
            exporter._load_frozen_reader(str(current_reader.parent / "source-spec.json"),
                                         str(current_reader.parent))

        with tempfile.TemporaryDirectory(prefix="tsj-frozen-reader-reparse-") as root:
            spec_parent = Path(root) / "source"
            spec_parent.mkdir()
            spec_path = spec_parent / "source-spec.json"
            spec_path.write_bytes(b"fixture spec")
            target = spec_parent / "frozen-target"
            target.mkdir()
            link = spec_parent / "frozen-link"
            try:
                link.symlink_to(target, target_is_directory=True)
            except NotImplementedError as error:
                self.skipTest("symlink creation is unavailable: %s" % error)
            except OSError as error:
                if (error.errno in (errno.EPERM, errno.EACCES) or
                        getattr(error, "winerror", None) == 1314):
                    self.skipTest("symlink creation is unavailable: %s" % error)
                raise
            with self.assertRaisesRegex(exporter.GraphspecExportError,
                                        "FROZEN_ORACLE_DIRECTORY_INVALID"):
                exporter._load_frozen_reader(str(spec_path), "frozen-link")

    def test_pass_report_must_bind_load12_and_approved_artifacts(self):
        exporter._validate_report_envelope(
            _passed_report(), FROZEN_READER_SHA256, SOURCE_SPEC_SHA256)
        wrong_target = _passed_report()
        wrong_target["target"] = "D_SENSE"
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_IDENTITY_INVALID"):
            exporter._validate_report_envelope(
                wrong_target, FROZEN_READER_SHA256, SOURCE_SPEC_SHA256)
        wrong_source = _passed_report()
        wrong_source["source"]["pinsSha256"] = "0" * 64
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID"):
            exporter._validate_report_envelope(
                wrong_source, FROZEN_READER_SHA256, SOURCE_SPEC_SHA256)
        for field, value in (("readerSha256", CURRENT_QUALITY_READER_SHA256),
                             ("inputCount", CURRENT_INPUT_COUNT),
                             ("inputBytes", FROZEN_INPUT_BYTES - 1)):
            wrong_source = _passed_report()
            wrong_source["source"][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(
                    exporter.GraphspecExportError,
                    "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID"):
                exporter._validate_report_envelope(
                    wrong_source, FROZEN_READER_SHA256, SOURCE_SPEC_SHA256)
        with self.assertRaisesRegex(exporter.GraphspecExportError,
                                    "PUBLIC_READER_REPORT_SOURCE_BINDING_INVALID"):
            exporter._validate_report_envelope(_passed_report(), "0" * 64,
                                               SOURCE_SPEC_SHA256)

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
                    exporter, "_load_frozen_reader",
                    return_value=(fake_reader, b"reader-source", Path("reader.py"))) as frozen_loader, \
                 patch.object(exporter, "_validate_report_envelope"), \
                 patch.object(exporter, "_load_context_and_records",
                              return_value=({}, {}, {})), \
                 patch.object(exporter, "_build_graphspec",
                              return_value=graphspec_fixture), \
                 patch.object(exporter, "_write_atomic_exclusive") as writer:
                graphspec, canonical = exporter.build_verified_graphspec(
                    "fixture-spec.json", SOURCE_SPEC_SHA256,
                    "../frozen-oracle/tests/qualification/rb100-layout")
            frozen_loader.assert_called_once_with(
                "fixture-spec.json", "../frozen-oracle/tests/qualification/rb100-layout")
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
