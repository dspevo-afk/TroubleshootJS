"""Source and checked-materialization controls for the generic R7 reader."""
from __future__ import annotations

import argparse
import ast
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import sys
import tempfile
import types
import unittest
from unittest import mock


_ARGUMENTS = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
for _flag in ("--reference-reader", "--kernel", "--adapter", "--wire",
              "--base-generator", "--io-helper"):
    _ARGUMENTS.add_argument(_flag)
_OPTIONS, _UNITTEST_ARGS = _ARGUMENTS.parse_known_args()
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_generic_r7_reader.py")
_SPEC = importlib.util.spec_from_file_location("generic_r7_reader_generator_tested",
                                               GENERATOR_PATH)
generator = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(generator)


PARENT_FIXTURE = b'''"""synthetic pinned T reader"""
from __future__ import annotations
import argparse
CLI_FLAGS = ("--old",)
T_CONSTANT = "kept"
def pure_reader(value):
    return value
def parse_args(argv):
    return argv
def main(argv=None):
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
'''

KERNEL_FIXTURE = b'''"""synthetic R7 result marker kernel"""
GR7_SCHEMA = "fixture"
class GR7_ReaderError(ValueError):
    pass
def gr7_need(condition, code):
    if not condition:
        raise GR7_ReaderError(code)
def validate_result_and_marker(result_raw, marker_raw, expected_run_id,
                               expected_source_sha, expected_wire_sha,
                               fixture_spec_raw):
    return {"derived": True}
'''

ADAPTER_FIXTURE = b'''def main(argv=None):
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
'''
WIRE_FIXTURE = b"# synthetic frozen wire\n"


def _patch_source_pins(parent_raw=PARENT_FIXTURE):
    return mock.patch.multiple(
        generator,
        PARENT_READER_SHA256=hashlib.sha256(parent_raw).hexdigest(),
        WIRE_SHA256=hashlib.sha256(WIRE_FIXTURE).hexdigest(),
    )


class _FakeIO:
    def __init__(self):
        self.write_limits = {}

    def _checked_directory(self, path_value, code):
        path = Path(path_value)
        if not path.is_dir():
            raise generator.GeneratorError(code + "_TYPE")
        return path

    def _directory_names(self, path, code):
        return sorted(item.name for item in Path(path).iterdir())

    def _read_regular(self, path_value, limit, code):
        path = Path(path_value)
        if not path.is_file():
            raise generator.GeneratorError(code + "_READ")
        raw = path.read_bytes()
        if not 0 < len(raw) <= limit:
            raise generator.GeneratorError(code + "_SIZE")
        return raw

    def _write_exclusive(self, path, raw, limit, code):
        if not 0 < len(raw) <= limit:
            raise generator.GeneratorError(code + "_SIZE")
        self.write_limits[Path(path).name] = limit
        with Path(path).open("xb") as stream:
            if stream.write(raw) != len(raw):
                raise generator.GeneratorError(code + "_WRITE")


class _ChangingIO(_FakeIO):
    def __init__(self, changed_path):
        super().__init__()
        self.changed_path = Path(changed_path)
        self.changed = False

    def _write_exclusive(self, path, raw, limit, code):
        super()._write_exclusive(path, raw, limit, code)
        if not self.changed:
            self.changed_path.write_bytes(self.changed_path.read_bytes() + b"# changed\n")
            self.changed = True


class _FakeBase:
    IO_HELPER_SHA256 = generator.IO_HELPER_SHA256
    _checked_source_raw = b"fixed base fixture"
    _checked_source_identity = (1, 1)

    def __init__(self, io_module):
        self.io_module = io_module

    def load_io(self, _path):
        return self.io_module


def _fake_pinned_file(_path, expected_sha, _limit, code):
    if code == "BASE_GENERATOR":
        return _FakeBase._checked_source_raw, _FakeBase._checked_source_identity
    if code == "IO_HELPER":
        return b"fixed I/O fixture", (2, 2)
    raise AssertionError(code)


def _write_sources(root):
    values = {
        "reference": PARENT_FIXTURE,
        "kernel": KERNEL_FIXTURE,
        "adapter": ADAPTER_FIXTURE,
        "wire": WIRE_FIXTURE,
    }
    paths = {}
    for name, raw in values.items():
        path = root / (name + ".source")
        path.write_bytes(raw)
        paths[name] = str(path)
    paths["base"] = str(root / "base-generator.py")
    paths["io"] = str(root / "io-helper.py")
    return paths


def _generator_options(paths, output):
    return dict(zip(generator.GENERATOR_FLAGS,
                    [paths["reference"], paths["kernel"], paths["adapter"],
                     paths["wire"], paths["base"], paths["io"], str(output)]))


class GenericR7ReaderGeneratorTests(unittest.TestCase):
    def test_source_extraction_excludes_only_old_cli_and_preserves_reviewed_runs(self):
        with _patch_source_pins():
            derived, proof = generator.derive(PARENT_FIXTURE, KERNEL_FIXTURE,
                                               ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertIn(b"def pure_reader(value):\n    return value\n", derived)
        self.assertNotIn(b"CLI_FLAGS", derived)
        self.assertNotIn(b"def parse_args(", derived)
        self.assertEqual(derived.count(KERNEL_FIXTURE), 1)
        self.assertTrue(derived.endswith(ADAPTER_FIXTURE))
        self.assertEqual(proof["referenceExclusions"]["retiredCliNodes"],
                         ["main", "parse_args", "CLI_FLAGS", "__main__ guard"])
        self.assertTrue(all(row["byteEqual"] for row in proof["referenceNodes"]))
        tree = ast.parse(derived.decode("utf-8"))
        functions = [node.name for node in tree.body if isinstance(node, ast.FunctionDef)]
        self.assertIn("validate_result_and_marker", functions)
        self.assertEqual(functions.count("main"), 1)
        self.assertEqual(proof["kernelAdapterPinOwner"], "ROOT_EXTERNAL_REVIEW_REQUIRED")
        self.assertFalse(proof["actualNativeCounterProof"])

    def test_parent_and_wire_source_pins_are_mandatory(self):
        with self.assertRaises(generator.GeneratorError) as raised:
            generator.derive(PARENT_FIXTURE + b"# changed\n", KERNEL_FIXTURE,
                             ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "REFERENCE_READER_HASH")
        with _patch_source_pins():
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(PARENT_FIXTURE, KERNEL_FIXTURE,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE + b"changed")
        self.assertEqual(raised.exception.code, "WIRE_HASH")

    def test_parent_guard_and_kernel_loader_are_rejected_when_changed(self):
        altered_guard = PARENT_FIXTURE.replace(
            b"    raise SystemExit(main())", b"    raise SystemExit(9)")
        # Authenticate this synthetic source only to reach the independent AST
        # guard check. Production parent pins remain fixed and are tested above.
        with _patch_source_pins(altered_guard):
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(altered_guard, KERNEL_FIXTURE,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "REFERENCE_MAIN_GUARD")

        dynamic_kernel = KERNEL_FIXTURE.replace(
            b'    return {"derived": True}', b"    exec('hidden loader')\n    return {\"derived\": True}")
        with _patch_source_pins():
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(PARENT_FIXTURE, dynamic_kernel,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "KERNEL_DYNAMIC_LOADER")

    def test_kernel_and_adapter_namespace_collisions_are_rejected(self):
        colliding = (b"def pure_reader(value):\n    return value\n"
                     b"def main(argv=None):\n    return 0\n"
                     b"if __name__ == \"__main__\":\n"
                     b"    raise SystemExit(main())\n")
        with _patch_source_pins():
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(PARENT_FIXTURE, KERNEL_FIXTURE,
                                 colliding, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "SOURCE_BINDING_COLLISION")

        malformed_kernel = KERNEL_FIXTURE.replace(b"GR7_SCHEMA", b"T_CONSTANT")
        with _patch_source_pins():
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(PARENT_FIXTURE, malformed_kernel,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "KERNEL_NAMESPACE")

        dynamic_adapter = (b"import subprocess\n" + ADAPTER_FIXTURE)
        with _patch_source_pins():
            with self.assertRaises(generator.GeneratorError):
                generator.derive(PARENT_FIXTURE, KERNEL_FIXTURE,
                                 dynamic_adapter, WIRE_FIXTURE)

    def test_generator_cli_requires_seven_canonical_path_pairs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = [str(root / name) for name in
                     ("reference.py", "kernel.py", "adapter.py", "wire.md",
                      "base.py", "io.py", "out")]
            argv = []
            for flag, value in zip(generator.GENERATOR_FLAGS, paths):
                argv.extend((flag, value))
            self.assertEqual(generator.parse_args(argv),
                             dict(zip(generator.GENERATOR_FLAGS, paths)))
            alias = list(argv)
            if os.name == "nt":
                alias[1] = str(root) + "\\.\\reference.py"
            else:
                alias[1] = str(root) + "/../" + root.name + "/reference.py"
            with self.assertRaises(generator.GeneratorError):
                generator.parse_args(alias)
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.parse_args(argv[:-2])
            self.assertEqual(raised.exception.code, "CLI_ARGUMENTS")

    def test_reader_adapter_declares_exact_eight_pairs_and_rechecks_before_output(self):
        adapter_raw = Path(__file__).with_name("generic_r7_reader_cli.py").read_bytes()
        tree = ast.parse(adapter_raw.decode("utf-8"))
        assignments = {}
        for node in tree.body:
            if isinstance(node, ast.Assign) and len(node.targets) == 1 \
                    and isinstance(node.targets[0], ast.Name):
                assignments[node.targets[0].id] = ast.literal_eval(node.value)
        self.assertEqual(assignments["R7_CLI_FLAGS"],
                         ("--result", "--marker", "--run-id", "--source-sha",
                          "--wire-sha", "--fixture-spec", "--base-generator", "--io-helper"))
        self.assertEqual(assignments["R7_PATH_FLAGS"],
                         ("--result", "--marker", "--fixture-spec",
                          "--base-generator", "--io-helper"))
        main = next(node for node in tree.body
                    if isinstance(node, ast.FunctionDef) and node.name == "main")
        calls = [node.func.id for node in ast.walk(main)
                 if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)]
        self.assertIn("load_fixture_spec", calls)
        self.assertIn("validate_result_and_marker", calls)
        self.assertIn("_r7_recheck_inputs", calls)
        self.assertIn("canonical_line", calls)
        self.assertEqual(calls.count("validate_result_and_marker"), 1)

    def test_pinned_file_reader_rejects_reparse_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            target = root / "target"
            link = root / "link"
            target.write_bytes(b"ordinary source")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("this host cannot create a test symlink")
            with self.assertRaises(generator.GeneratorError):
                generator._read_pinned_file(str(link), hashlib.sha256(b"ordinary source").hexdigest(),
                                            1024, "TEST_SOURCE")

    def test_handle_identity_ignores_only_ctime_and_bad_hash_is_rejected(self):
        fields = {"st_dev": 1, "st_ino": 2, "st_mode": stat.S_IFREG | 0o600,
                  "st_size": 12, "st_mtime_ns": 34, "st_file_attributes": 0}
        before = types.SimpleNamespace(**fields, st_ctime_ns=56)
        opened = types.SimpleNamespace(**fields, st_ctime_ns=78)
        self.assertEqual(generator._handle_identity(before),
                         generator._handle_identity(opened))
        self.assertNotEqual(generator._identity(before), generator._identity(opened))
        for field, value in (("st_dev", 9), ("st_ino", 9),
                             ("st_mode", stat.S_IFREG | 0o644), ("st_size", 13),
                             ("st_mtime_ns", 35), ("st_file_attributes", 0x400)):
            changed = dict(fields)
            changed[field] = value
            altered = types.SimpleNamespace(**changed, st_ctime_ns=56)
            self.assertNotEqual(generator._handle_identity(before),
                                generator._handle_identity(altered), field)

        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "source.py"
            target.write_bytes(b"pinned bytes")
            wrong_sha = hashlib.sha256(b"different bytes").hexdigest()
            with self.assertRaises(generator.GeneratorError) as raised:
                generator._read_pinned_file(str(target), wrong_sha, 1024, "TEST_SOURCE")
        self.assertEqual(raised.exception.code, "TEST_SOURCE_HASH")

    def test_source_and_proof_byte_limits_are_separate(self):
        self.assertEqual(generator.MAX_SOURCE_BYTES, 4 * 1024 * 1024)
        self.assertEqual(generator.MAX_DERIVED_BYTES, 4 * 1024 * 1024)
        self.assertEqual(generator.MAX_DERIVED_BYTES, generator.MAX_SOURCE_BYTES)
        self.assertEqual(generator.MAX_PROOF_BYTES, 8 * 1024 * 1024)
        with _patch_source_pins():
            with mock.patch.object(generator, "MAX_DERIVED_BYTES", 1):
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.derive(PARENT_FIXTURE, KERNEL_FIXTURE,
                                     ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "DERIVED_SIZE")

    def test_output_occupancy_is_rejected_without_overwriting(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            sentinel = output / "sentinel"
            sentinel.write_bytes(b"preserve")
            paths = _write_sources(root)
            options = _generator_options(paths, output)
            fake_io = _FakeIO()
            base = _FakeBase(fake_io)
            with _patch_source_pins():
                with mock.patch.object(generator, "load_base_generator", return_value=base):
                    with mock.patch.object(generator, "_read_pinned_file",
                                            side_effect=_fake_pinned_file):
                        with self.assertRaises(generator.GeneratorError) as raised:
                            generator.generate(options)
            self.assertEqual(raised.exception.code, "OUTPUT_NOT_EMPTY")
            self.assertEqual(sentinel.read_bytes(), b"preserve")

    def test_exclusive_materialization_and_input_recheck(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            paths = _write_sources(root)
            options = _generator_options(paths, output)
            fake_io = _FakeIO()
            base = _FakeBase(fake_io)
            with _patch_source_pins():
                with mock.patch.object(generator, "load_base_generator", return_value=base):
                    with mock.patch.object(generator, "_read_pinned_file",
                                            side_effect=_fake_pinned_file):
                        proof = generator.generate(options)
            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             ["SOURCE_PROOF.json", "read_generic_r7_result_v1.py"])
            generated = (output / "read_generic_r7_result_v1.py").read_bytes()
            self.assertTrue(generated.endswith(ADAPTER_FIXTURE))
            self.assertEqual(hashlib.sha256(generated).hexdigest(), proof["derivedSha256"])
            saved = json.loads((output / "SOURCE_PROOF.json").read_text(encoding="ascii"))
            self.assertEqual(saved, proof)
            self.assertEqual(fake_io.write_limits,
                             {"read_generic_r7_result_v1.py": 4 * 1024 * 1024,
                              "SOURCE_PROOF.json": 8 * 1024 * 1024})

    def test_replacement_during_exclusive_write_fails_and_keeps_unaccepted_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            paths = _write_sources(root)
            fake_io = _ChangingIO(paths["reference"])
            base = _FakeBase(fake_io)
            options = _generator_options(paths, output)
            with _patch_source_pins():
                with mock.patch.object(generator, "load_base_generator", return_value=base):
                    with mock.patch.object(generator, "_read_pinned_file",
                                            side_effect=_fake_pinned_file):
                        with self.assertRaises(generator.GeneratorError) as raised:
                            generator.generate(options)
            self.assertEqual(raised.exception.code, "SOURCE_CHANGED")
            self.assertEqual(set(item.name for item in output.iterdir()),
                             {"read_generic_r7_result_v1.py", "SOURCE_PROOF.json"})

    def test_optional_real_source_bundle(self):
        supplied = (_OPTIONS.reference_reader, _OPTIONS.kernel, _OPTIONS.adapter,
                    _OPTIONS.wire, _OPTIONS.base_generator, _OPTIONS.io_helper)
        if not all(supplied):
            self.skipTest("pass all six source/tool paths for the real checked-I/O integration")
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "empty-output"
            output.mkdir()
            argv = []
            for flag, value in zip(generator.GENERATOR_FLAGS,
                                   [*supplied, str(output)]):
                argv.extend((flag, value))
            proof = generator.generate(generator.parse_args(argv))
            generated = (output / "read_generic_r7_result_v1.py").read_bytes()
            self.assertEqual(proof["parentReaderSha256"],
                             generator.PARENT_READER_SHA256)
            self.assertEqual(proof["wireContractSha256"], generator.WIRE_SHA256)
            self.assertTrue(generated.endswith(Path(_OPTIONS.adapter).read_bytes()))
            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             ["SOURCE_PROOF.json", "read_generic_r7_result_v1.py"])


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *_UNITTEST_ARGS])
