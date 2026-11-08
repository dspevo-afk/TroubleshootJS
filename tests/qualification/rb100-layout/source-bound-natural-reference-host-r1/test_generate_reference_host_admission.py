"""Focused source-only tests for the checked reference-host generator.

The source fixtures here are synthetic and exercise extraction mechanics.  The
actual pinned host/reference sources and reviewed adapter are inputs to the
generation integration gate, not test-owned copies.
"""
from __future__ import annotations

import ast
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

_ARGUMENTS = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
_ARGUMENTS.add_argument("--host-reader")
_ARGUMENTS.add_argument("--reference-reader")
_ARGUMENTS.add_argument("--adapter")
_OPTIONS, _UNITTEST_ARGS = _ARGUMENTS.parse_known_args()
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_reference_host_admission.py")
_SPEC = importlib.util.spec_from_file_location("reference_host_admission_tested", GENERATOR_PATH)
generator = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(generator)


HOST_FIXTURE = b'''"""synthetic pinned host reader"""
from __future__ import annotations
from pathlib import Path
MAX_JSON = 17
MAX_CLASS_BYTES = 23
MAX_CLASS_COUNT = 29
HEX = "hex"
CLASS_SCHEMA = "class"
PARENT_SCHEMA = "parent"
ROOT_CALL_OWNER_SCHEMA = "owner"
PS5_SHA256 = "ps5"
P9_ROOT_CALL_SHA256 = "root"
P9_PROCESS_MODULE_SHA256 = "process"
class ReaderError(ValueError):
    pass
def need(condition, code):
    if not condition:
        raise ReaderError(code)
def sha(raw):
    return raw
def exact_keys(value, names, code):
    return None
def is_int(value, low=0, high=1):
    return False
def is_digest(value):
    return False
def _pairs(rows):
    return rows
def _parse_int(value):
    return int(value)
def _parse_float(value):
    return float(value)
def _reject_constant(value):
    return value
def _json_load(raw):
    return raw
def _is_compact(raw):
    return True
def strict_compact_line(raw, code):
    return raw
def strict_document(raw, limit=MAX_JSON):
    return raw
def host_document(raw, limit=MAX_JSON):
    return raw
def _lstat_regular(path, code):
    return None
def _lstat_directory(path, code):
    return None
def _read(path, limit, code):
    return b""
def _canonical(path, code, require_exists=True):
    return Path(path)
def _same_path(left, right):
    return left == right
def _same_host_path(value, expected, code):
    return value == expected
def _validate_owner_call(*args, **kwargs):
    return None
def _validate_process_stderr(*args, **kwargs):
    return None
def _is_finite_number(value):
    return True
def _validate_class_tree(*args, **kwargs):
    return None
if __name__ == "__main__":
    raise SystemExit(2)
'''


REFERENCE_FIXTURE = b'''"""synthetic reference reader"""
from __future__ import annotations
import argparse
from pathlib import Path
CLI_FLAGS = ("--input",)
REFERENCE_CONSTANT = "kept"
def _bootstrap_base(path):
    return path
def checked_read(path):
    return path
def validate_packet(packet):
    return packet
def parse_args(argv):
    return argv
def main(argv=None):
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
'''


ADAPTER_FIXTURE = b'''def main(argv=None):
    return RH_need(True, "OK")

if __name__ == "__main__":
    raise SystemExit(main())
'''


class _FakeIO:
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
        with Path(path).open("xb") as stream:
            if stream.write(raw) != len(raw):
                raise generator.GeneratorError(code + "_WRITE")


class _FakeBase:
    IO_HELPER_SHA256 = generator.IO_HELPER_SHA256
    _checked_source_raw = b"pinned base source"
    _checked_source_identity = (1, 2, 3)

    def __init__(self, io_module):
        self.io_module = io_module

    def load_io(self, _path):
        return self.io_module


def _pin_fixture_sources():
    return mock.patch.multiple(
        generator,
        HOST_READER_SHA256=hashlib.sha256(HOST_FIXTURE).hexdigest(),
        REFERENCE_READER_SHA256=hashlib.sha256(REFERENCE_FIXTURE).hexdigest(),
    )


class ReferenceHostGeneratorTests(unittest.TestCase):
    def test_host_identifier_rename_changes_name_tokens_only(self):
        source = (b'def need(value):  # need in a comment\n'
                  b'    return "need" + value\n')
        result = generator._rename_identifier_tokens(source, {"need": "RH_need"})
        self.assertEqual(result,
                         b'def RH_need(value):  # need in a comment\n'
                         b'    return "need" + value\n')

    def test_derive_preserves_reference_nodes_and_exact_host_kernel_closure(self):
        with _pin_fixture_sources():
            result, proof = generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE,
                                             ADAPTER_FIXTURE)

        self.assertTrue(result.endswith(ADAPTER_FIXTURE))
        self.assertEqual(result.count(ADAPTER_FIXTURE), 1)
        self.assertEqual(proof["referenceExclusions"]["cliNodes"],
                         ["main", "parse_args", "CLI_FLAGS", "__main__ guard"])
        self.assertTrue(all(item["byteEqual"] for item in proof["referenceNodes"]))
        self.assertEqual(proof["hostKernelNodeCount"], 35)
        self.assertEqual(proof["hostKernelFunctionCount"], 25)
        self.assertEqual(proof["hostKernelConstantCount"], 10)
        self.assertTrue(all(item["tokenRenamed"] for item in proof["hostKernelNodes"]))

        tree = ast.parse(result.decode("utf-8"))
        names = [node.name for node in tree.body
                 if isinstance(node, (ast.FunctionDef, ast.ClassDef))]
        self.assertIn("_bootstrap_base", names)
        self.assertIn("checked_read", names)
        self.assertIn("RH_ReaderError", names)
        self.assertIn("RH__validate_class_tree", names)
        self.assertIn("main", names)
        self.assertNotIn("parse_args", names)
        self.assertNotIn("CLI_FLAGS", [target.id for node in tree.body
                                       if isinstance(node, ast.Assign)
                                       for target in node.targets if isinstance(target, ast.Name)])

    def test_derivation_rejects_changed_source_pins(self):
        with self.assertRaises(generator.GeneratorError) as raised:
            generator.derive(HOST_FIXTURE + b"# changed\n", REFERENCE_FIXTURE,
                             ADAPTER_FIXTURE)
        self.assertEqual(raised.exception.code, "HOST_READER_HASH")

    def test_optional_pinned_source_bundle_and_adapter(self):
        if not _OPTIONS.host_reader or not _OPTIONS.reference_reader:
            self.skipTest("pass --host-reader and --reference-reader for pinned source integration")
        host_raw = Path(_OPTIONS.host_reader).read_bytes()
        reference_raw = Path(_OPTIONS.reference_reader).read_bytes()
        adapter_raw = (Path(_OPTIONS.adapter).read_bytes() if _OPTIONS.adapter
                       else ADAPTER_FIXTURE)
        derived, proof = generator.derive(host_raw, reference_raw, adapter_raw)
        self.assertEqual(proof["hostReaderSha256"], generator.HOST_READER_SHA256)
        self.assertEqual(proof["referenceReaderSha256"], generator.REFERENCE_READER_SHA256)
        self.assertEqual(proof["hostKernelNodeCount"], 35)
        self.assertEqual(proof["hostKernelFunctionCount"], 25)
        self.assertEqual(proof["hostKernelConstantCount"], 10)
        self.assertTrue(derived.endswith(adapter_raw))

    def test_adapter_imports_are_restricted_and_adapter_is_required(self):
        with _pin_fixture_sources():
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE,
                                 b"import subprocess\ndef main():\n    return 0\n")
        self.assertEqual(raised.exception.code, "ADAPTER_IMPORT")

    def test_adapter_requires_one_final_conventional_entry_guard(self):
        for adapter in (b"def main():\n    return 0\n",
                        b"def main():\n    return 0\nif __name__ == '__main__':\n    main()\n",
                        ADAPTER_FIXTURE + b"AFTER_GUARD = True\n",
                        ADAPTER_FIXTURE + b"if __name__ == '__main__':\n    raise SystemExit(main())\n"):
            with self.subTest(adapter=adapter), _pin_fixture_sources():
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE, adapter)
                self.assertEqual(raised.exception.code, "ADAPTER_ENTRY_GUARD")

    def test_new_adapter_parser_owns_its_current_cli_name(self):
        adapter = b"def parse_args(argv):\n    return {'--context': argv[0]}\n" + ADAPTER_FIXTURE
        with _pin_fixture_sources():
            derived, proof = generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE, adapter)
        self.assertTrue(derived.endswith(adapter))
        self.assertIn("parse_args", proof["adapterBindings"])
        self.assertEqual(proof["referenceExclusions"]["cliNodes"],
                         ["main", "parse_args", "CLI_FLAGS", "__main__ guard"])

    def test_cli_requires_all_six_canonical_absolute_pairs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = [str(root / name) for name in
                     ("host.py", "reference.py", "adapter.py", "base.py", "io.py", "out")]
            argv = []
            for flag, value in zip(generator.CLI_FLAGS, paths):
                argv.extend((flag, value))
            self.assertEqual(generator.parse_args(argv), dict(zip(generator.CLI_FLAGS, paths)))

            host = Path(paths[0])
            if os.name == "nt":
                bad_paths = (
                    str(host.parent) + "\\..\\" + host.name,
                    str(host.parent) + "\\.\\" + host.name,
                    str(host).replace("\\", "/"),
                )
            else:
                bad_paths = (
                    str(host.parent) + "/../" + host.name,
                    str(host.parent) + "/./" + host.name,
                    str(host.parent) + "//" + host.name,
                )
            for bad_host in bad_paths:
                aliased = list(argv)
                aliased[1] = bad_host
                with self.assertRaises(generator.GeneratorError):
                    generator.parse_args(aliased)

            with self.assertRaises(generator.GeneratorError) as raised:
                generator.parse_args(argv[:-2])
            self.assertEqual(raised.exception.code, "CLI_ARGUMENTS")

    def test_output_occupancy_fails_before_reading_any_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            (output / "sentinel").write_bytes(b"preserve")
            paths = [str(root / name) for name in
                     ("host.py", "reference.py", "adapter.py", "base.py", "io.py")]
            options = dict(zip(generator.CLI_FLAGS, paths + [str(output)]))
            io_module = _FakeIO()
            base = _FakeBase(io_module)
            with mock.patch.object(generator, "load_base_generator", return_value=base):
                with mock.patch.object(generator, "_read_pinned_file",
                                       return_value=(b"pinned io source", (4, 5, 6))):
                    with self.assertRaises(generator.GeneratorError) as raised:
                        generator.generate(options)
            self.assertEqual(raised.exception.code, "OUTPUT_NOT_EMPTY")
            self.assertEqual((output / "sentinel").read_bytes(), b"preserve")

    def test_generation_appends_explicit_adapter_and_reads_back_exact_closure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            host_path = root / "host.py"
            reference_path = root / "reference.py"
            adapter_path = root / "adapter.py"
            base_path = root / "base.py"
            io_path = root / "io.py"
            output = root / "output"
            output.mkdir()
            host_path.write_bytes(HOST_FIXTURE)
            reference_path.write_bytes(REFERENCE_FIXTURE)
            adapter_path.write_bytes(ADAPTER_FIXTURE)
            paths = [str(host_path), str(reference_path), str(adapter_path),
                     str(base_path), str(io_path), str(output)]
            options = dict(zip(generator.CLI_FLAGS, paths))
            io_module = _FakeIO()
            base = _FakeBase(io_module)

            with _pin_fixture_sources():
                with mock.patch.object(generator, "load_base_generator", return_value=base):
                    with mock.patch.object(generator, "_read_pinned_base",
                                            return_value=(base._checked_source_raw,
                                                          base._checked_source_identity)):
                        with mock.patch.object(generator, "_read_pinned_file",
                                               return_value=(b"pinned io source", (4, 5, 6))):
                            proof = generator.generate(options)

            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             ["SOURCE_PROOF.json", "read_source_bound_reference_v1.py"])
            generated = (output / "read_source_bound_reference_v1.py").read_bytes()
            self.assertTrue(generated.endswith(ADAPTER_FIXTURE))
            self.assertEqual(hashlib.sha256(generated).hexdigest(), proof["derivedSha256"])
            saved_proof = json.loads((output / "SOURCE_PROOF.json").read_text(encoding="ascii"))
            self.assertEqual(saved_proof, proof)


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *_UNITTEST_ARGS])
