"""Source-only checks for the bounded root-body emitter.

These tests exercise byte pins and insertion behavior without compiling Java or
loading application/runtime classes.
"""

import argparse
import importlib.util
import re
import stat
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace


TASK_ROOT = Path(__file__).resolve().parent


def _test_options():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--source-root")
    args, remaining = parser.parse_known_args()
    source_root = Path(args.source_root).resolve() if args.source_root else TASK_ROOT / "source"
    return source_root, remaining


SOURCE_ROOT, TEST_HARNESS_ARGS = _test_options()
GENERATOR_PATH = TASK_ROOT / "generate_root_body.py"
FRAGMENT_PATH = TASK_ROOT / "RootBodyConstructor.java.fragment"
CODEC_PATH = SOURCE_ROOT / "CurrentV2CanonicalCodec.java"

_spec = importlib.util.spec_from_file_location("generate_root_body", GENERATOR_PATH)
_generator = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_generator)


class RootBodySourceTests(unittest.TestCase):
    def test_cli_emits_exactly_one_insertion(self):
        codec = CODEC_PATH.read_bytes()
        fragment = FRAGMENT_PATH.read_bytes()
        offset = codec.index(_generator.ANCHOR)
        expected = codec[:offset] + fragment + codec[offset:]

        with tempfile.TemporaryDirectory(prefix="root-body-source-") as temporary:
            output_root = Path(temporary)
            result = subprocess.run(
                [
                    sys.executable,
                    "-I",
                    "-B",
                    str(GENERATOR_PATH),
                    "--source-root",
                    str(SOURCE_ROOT),
                    "--component-root",
                    str(TASK_ROOT),
                    "--output-root",
                    str(output_root),
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            output_path = output_root / "CurrentV2CanonicalCodec.java"
            self.assertEqual(output_path.read_bytes(), expected)
            self.assertEqual(expected.count(_generator.ANCHOR), 1)
            self.assertEqual(len(expected), len(codec) + len(fragment))

    def test_cli_rejects_unknown_emitter_argument(self):
        with tempfile.TemporaryDirectory(prefix="root-body-cli-args-") as temporary:
            output_root = Path(temporary)
            result = subprocess.run(
                [
                    sys.executable,
                    "-I",
                    "-B",
                    str(GENERATOR_PATH),
                    "--source-root",
                    str(SOURCE_ROOT),
                    "--component-root",
                    str(TASK_ROOT),
                    "--output-root",
                    str(output_root),
                    "--unknown-emitter-option",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 2)
            self.assertFalse((output_root / "CurrentV2CanonicalCodec.java").exists())

    def test_source_pin_rejects_changed_codec_before_output(self):
        codec = CODEC_PATH.read_bytes()
        fragment = FRAGMENT_PATH.read_bytes()
        with tempfile.TemporaryDirectory(prefix="root-body-pin-") as temporary:
            root = Path(temporary)
            source_root = root / "source"
            source_root.mkdir()
            output_root = root / "output"
            output_root.mkdir()
            (source_root / "CurrentV2CanonicalCodec.java").write_bytes(codec + b" ")
            (root / _generator.FRAGMENT_NAME).write_bytes(fragment)

            with self.assertRaises(_generator.EmitError) as caught:
                _generator.emit(source_root, root, output_root)
            self.assertEqual(str(caught.exception), "ROOTBODY_SOURCE_PIN")
            self.assertFalse((output_root / _generator.OUTPUT_NAME).exists())

    def test_component_pin_rejects_changed_fragment(self):
        fragment = FRAGMENT_PATH.read_bytes()
        with tempfile.TemporaryDirectory(prefix="root-body-component-pin-") as temporary:
            root = Path(temporary)
            component_root = root / "component"
            component_root.mkdir()
            output_root = root / "output"
            output_root.mkdir()
            (component_root / _generator.FRAGMENT_NAME).write_bytes(fragment + b" ")
            with self.assertRaises(_generator.EmitError) as caught:
                _generator.emit(SOURCE_ROOT, component_root, output_root)
            self.assertEqual(str(caught.exception), "ROOTBODY_COMPONENT_PIN")
            self.assertFalse((output_root / _generator.OUTPUT_NAME).exists())

    def test_existing_output_is_preserved(self):
        with tempfile.TemporaryDirectory(prefix="root-body-exclusive-") as temporary:
            output_root = Path(temporary)
            destination = output_root / _generator.OUTPUT_NAME
            destination.write_bytes(b"preserve")
            with self.assertRaises(_generator.EmitError) as caught:
                _generator.emit(SOURCE_ROOT, TASK_ROOT, output_root)
            self.assertEqual(str(caught.exception), "ROOTBODY_OUTPUT_EXISTS")
            self.assertEqual(destination.read_bytes(), b"preserve")

    def test_anchor_must_be_unique_and_insertion_preserves_surrounding_bytes(self):
        prefix = b"prefix\x00"
        suffix = b"suffix\xff"
        codec = prefix + _generator.ANCHOR + suffix
        fragment = FRAGMENT_PATH.read_bytes()
        self.assertEqual(
            _generator.insert_at_unique_anchor(codec, fragment),
            prefix + fragment + _generator.ANCHOR + suffix,
        )
        with self.assertRaises(_generator.EmitError) as missing:
            _generator.insert_at_unique_anchor(b"no constructor", fragment)
        self.assertEqual(str(missing.exception), "ROOTBODY_ANCHOR")
        with self.assertRaises(_generator.EmitError) as duplicate:
            _generator.insert_at_unique_anchor(codec + _generator.ANCHOR, fragment)
        self.assertEqual(str(duplicate.exception), "ROOTBODY_ANCHOR")

    def test_oversized_input_and_output_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="root-body-bounds-") as temporary:
            root = Path(temporary)
            source_root = root / "source"
            source_root.mkdir()
            output_root = root / "output"
            output_root.mkdir()
            (source_root / _generator.CODEC_NAME).write_bytes(
                b"x" * (_generator.SOURCE_CAP + 1)
            )
            with self.assertRaises(_generator.EmitError) as caught:
                _generator.emit(source_root, TASK_ROOT, output_root)
            self.assertEqual(str(caught.exception), "ROOTBODY_SOURCE_INPUT")
            self.assertFalse((output_root / _generator.OUTPUT_NAME).exists())

        over_bound_codec = (
            b"x" * (_generator.SOURCE_CAP - len(_generator.ANCHOR) + 1)
            + _generator.ANCHOR
        )
        with self.assertRaises(_generator.EmitError) as output_bound:
            _generator.insert_at_unique_anchor(
                over_bound_codec, FRAGMENT_PATH.read_bytes()
            )
        self.assertEqual(str(output_bound.exception), "ROOTBODY_OUTPUT_BOUND")

    def test_fragment_has_exact_thirteen_key_and_value_order(self):
        source = FRAGMENT_PATH.read_text(encoding="utf-8")
        expected = [
            ("0", "RESULT_SCHEMA"),
            ("1", "RESULT_MODE"),
            ("2", "RESULT_STATUS"),
            ("3", "runId"),
            ("4", "sourceManifestSha"),
            ("5", "WIRE_CONTRACT_SHA256"),
            ("6", "inputContextSha"),
            ("7", "null"),
            ("8", "originalCapture"),
            ("9", "cohort"),
            ("10", "arms"),
            ("11", "null"),
            ("12", "cleanup"),
        ]
        actual = re.findall(
            r"body\.put\(BODY_ROOT_KEYS\[(\d+)\],\s*([A-Za-z0-9_]+|null)\);",
            source,
        )
        self.assertEqual(actual, expected)
        self.assertEqual(source.count("CohortTerminalTelemetryStudy.SetupMeter meter"), 1)
        self.assertNotRegex(source, r"(?<![A-Za-z0-9_])Study\.SetupMeter")
        self.assertEqual(source.count("meter.copy(13);"), 1)
        self.assertEqual(source.count("meter.charge(1);"), 1)
        self.assertLess(source.index("meter.copy(13);"), source.index("meter.charge(1);"))
        self.assertLess(
            source.index("meter.charge(1);"),
            source.index("new java.util.LinkedHashMap<String, Object>(32)"),
        )
        self.assertLess(
            source.index("new java.util.LinkedHashMap<String, Object>(32)"),
            source.index("body.put("),
        )
        self.assertNotIn(".get(", source)
        self.assertNotIn(".entrySet(", source)

    def test_snapshot_ignores_atime(self):
        class Metadata:
            st_dev = 1
            st_ino = 2
            st_mode = 0o100600
            st_nlink = 1
            st_size = 3
            st_mtime = 4.0
            st_mtime_ns = 400
            st_ctime = 5.0
            st_ctime_ns = 500
            st_file_attributes = 0

            def __init__(self, atime):
                self.st_atime_ns = atime

        self.assertEqual(
            _generator._snapshot(Metadata(100)),
            _generator._snapshot(Metadata(200)),
        )

    def test_mocked_lstat_reparse_metadata_is_rejected(self):
        metadata = SimpleNamespace(
            st_mode=stat.S_IFREG,
            st_file_attributes=_generator.REPARSE_POINT,
        )
        with self.assertRaises(_generator.EmitError) as caught:
            _generator._check_plain_path_item(metadata, "ROOTBODY_SOURCE_INPUT")
        self.assertEqual(str(caught.exception), "ROOTBODY_SOURCE_INPUT")


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]] + TEST_HARNESS_ARGS)