"""Source-only tests for the checked generic R7 host generator.

Literal synthetic inputs exercise derivation and filesystem boundaries. Their
mocked source pins are UNIT fixtures only; they do not prove anything about the
actual host. The optional five-source gate reads source code only, never a result
packet or native receipt.
"""
from __future__ import annotations

import argparse
import ast
from contextlib import ExitStack, contextmanager
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock


EXPECTED_HOST_SHA256 = "dbe7ee4411d78217d08f118967dee873d63551d908747266145d12d1c44b5806"
EXPECTED_REFERENCE_SHA256 = "4f9573c223025c564c7063f7de8e31f79b777845b92a332cb85ee418e8394d72"
EXPECTED_WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
EXPECTED_HOST_FUNCTIONS = (
    "ReaderError", "need", "sha", "exact_keys", "is_int", "is_digest",
    "_pairs", "_parse_int", "_parse_float", "_reject_constant",
    "_json_load", "_is_compact", "strict_compact_line", "strict_document",
    "host_document", "_lstat_regular", "_lstat_directory", "_read",
    "_canonical", "_same_path", "_same_host_path", "_validate_owner_call",
    "_validate_process_stderr", "_is_finite_number", "_validate_class_tree",
)
EXPECTED_HOST_CONSTANTS = (
    "MAX_JSON", "MAX_CLASS_BYTES", "MAX_CLASS_COUNT", "HEX", "CLASS_SCHEMA",
    "PARENT_SCHEMA", "ROOT_CALL_OWNER_SCHEMA", "PS5_SHA256",
    "P9_ROOT_CALL_SHA256", "P9_PROCESS_MODULE_SHA256",
)
EXPECTED_CLI_FLAGS = (
    "--host-reader", "--reference-reader", "--kernel", "--adapter", "--wire",
    "--base-generator", "--io-helper", "--output-dir",
)

_ARGUMENTS = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
_ARGUMENTS.add_argument("--host-reader")
_ARGUMENTS.add_argument("--reference-reader")
_ARGUMENTS.add_argument("--kernel")
_ARGUMENTS.add_argument("--adapter")
_ARGUMENTS.add_argument("--wire")
_OPTIONS, _UNITTEST_ARGS = _ARGUMENTS.parse_known_args()
_SOURCE_ARGUMENTS = (
    _OPTIONS.host_reader, _OPTIONS.reference_reader, _OPTIONS.kernel,
    _OPTIONS.adapter, _OPTIONS.wire,
)
if any(_SOURCE_ARGUMENTS) and not all(_SOURCE_ARGUMENTS):
    _ARGUMENTS.error("pass all five source paths together")

GENERATOR_PATH = Path(__file__).resolve().with_name("generate_generic_r7_host.py")
_SPEC = importlib.util.spec_from_file_location("generic_r7_host_generator_tested", GENERATOR_PATH)
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

REFERENCE_FIXTURE = b'''"""synthetic Tpure reference reader"""
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

REFERENCE_PURE_NODES = (
    b'REFERENCE_CONSTANT = "kept"\n',
    b"def _bootstrap_base(path):\n    return path\n",
    b"def checked_read(path):\n    return path\n",
    b"def validate_packet(packet):\n    return packet\n",
)

KERNEL_FIXTURE = b'''import json
"""synthetic kernel module docstring"""
GR7_RESULT_SCHEMA = "synthetic-result-v1"
def gr7_need(condition, code):
    if not condition:
        raise ValueError(code)
def validate_result_and_marker(result, marker):
    return isinstance(result, dict) and isinstance(marker, dict)
'''

ADAPTER_FIXTURE = b'''"""synthetic adapter module docstring"""
def main(argv=None):
    RH_need(True, "OK")
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
'''

WIRE_FIXTURE = b"synthetic wire fixture; intentionally not the pinned R7 contract\n"


class _FakeIO:
    """Small filesystem double for generator-unit boundaries, not host proof."""

    def __init__(self, output_dir=None, before_write=None, corrupt_readback=False):
        self.output_dir = Path(output_dir) if output_dir is not None else None
        self.before_write = before_write
        self.corrupt_readback = corrupt_readback
        self.read_paths = []
        self.write_paths = []

    def _checked_directory(self, path_value, code):
        path = Path(path_value)
        if not path.is_dir():
            raise generator.GeneratorError(code + "_TYPE")
        return path

    def _directory_names(self, path, code):
        return sorted(item.name for item in Path(path).iterdir())

    def _read_regular(self, path_value, limit, code):
        path = Path(path_value)
        self.read_paths.append(path)
        if not path.is_file():
            raise generator.GeneratorError(code + "_READ")
        raw = path.read_bytes()
        if not 0 < len(raw) <= limit:
            raise generator.GeneratorError(code + "_SIZE")
        if (self.corrupt_readback and self.output_dir == path.parent
                and path.name == generator.OUTPUT_NAMES[0]):
            return raw + b"x"
        return raw

    def _write_exclusive(self, path_value, raw, limit, code):
        path = Path(path_value)
        self.write_paths.append(path)
        if not 0 < len(raw) <= limit:
            raise generator.GeneratorError(code + "_SIZE")
        if self.before_write is not None:
            self.before_write(path, raw)
        try:
            with path.open("xb") as stream:
                if stream.write(raw) != len(raw):
                    raise generator.GeneratorError(code + "_WRITE")
        except FileExistsError:
            raise generator.GeneratorError(code + "_EXISTS") from None


class _FakeBase:
    IO_HELPER_SHA256 = generator.IO_HELPER_SHA256
    _checked_source_raw = b"synthetic pinned base source"
    _checked_source_identity = (1, 2, 3)

    def __init__(self, io_module):
        self.io_module = io_module

    def load_io(self, _path):
        return self.io_module


def _sha(raw):
    return hashlib.sha256(raw).hexdigest()


def _synthetic_pins(host=HOST_FIXTURE, reference=REFERENCE_FIXTURE, wire=WIRE_FIXTURE):
    """Trust only literal unit fixtures; the optional source gate uses no mocks."""
    return mock.patch.multiple(
        generator,
        HOST_READER_SHA256=_sha(host),
        REFERENCE_READER_SHA256=_sha(reference),
        WIRE_SHA256=_sha(wire),
    )


@contextmanager
def _patched_generation(base):
    with ExitStack() as stack:
        stack.enter_context(_synthetic_pins())
        stack.enter_context(mock.patch.object(generator, "load_base_generator",
                                               return_value=base))
        stack.enter_context(mock.patch.object(
            generator, "_read_pinned_base",
            return_value=(base._checked_source_raw, base._checked_source_identity)))
        stack.enter_context(mock.patch.object(
            generator, "_read_pinned_file",
            return_value=(b"synthetic pinned IO helper", (4, 5, 6))))
        yield


def _write_generation_inputs(root):
    sources = (
        ("--host-reader", "host.py", HOST_FIXTURE),
        ("--reference-reader", "reference.py", REFERENCE_FIXTURE),
        ("--kernel", "kernel.py", KERNEL_FIXTURE),
        ("--adapter", "adapter.py", ADAPTER_FIXTURE),
        ("--wire", "wire.md", WIRE_FIXTURE),
    )
    paths = {}
    for flag, name, raw in sources:
        path = Path(root) / name
        path.write_bytes(raw)
        paths[flag] = path
    output = Path(root) / "output"
    output.mkdir()
    options = {
        "--host-reader": str(paths["--host-reader"]),
        "--reference-reader": str(paths["--reference-reader"]),
        "--kernel": str(paths["--kernel"]),
        "--adapter": str(paths["--adapter"]),
        "--wire": str(paths["--wire"]),
        "--base-generator": str(Path(root) / "base-generator.py"),
        "--io-helper": str(Path(root) / "io-helper.py"),
        "--output-dir": str(output),
    }
    return paths, output, options


class GenericR7HostGeneratorTests(unittest.TestCase):
    def test_token_rename_changes_name_tokens_only(self):
        source = (b"def need(value):  # need in a comment\n"
                  b"    return 'need' + value + need\n")
        result = generator._rename_identifier_tokens(source, {"need": "RH_need"})
        self.assertEqual(result,
                         b"def RH_need(value):  # need in a comment\n"
                         b"    return 'need' + value + RH_need\n")

    def test_pure_reference_bytes_survive_and_legacy_cli_nodes_are_removed(self):
        with _synthetic_pins():
            derived, proof = generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE,
                                              KERNEL_FIXTURE, ADAPTER_FIXTURE,
                                              WIRE_FIXTURE)
        self.assertTrue(derived.endswith(ADAPTER_FIXTURE))
        self.assertEqual(derived.count(ADAPTER_FIXTURE), 1)
        self.assertEqual(proof["referenceExclusions"]["cliNodes"],
                         ["main", "parse_args", "CLI_FLAGS", "__main__ guard"])
        self.assertTrue(all(item["byteEqual"] for item in proof["referenceNodes"]))
        for source in REFERENCE_PURE_NODES:
            self.assertEqual(derived.count(source), 1)
            self.assertEqual(_sha(source), next(
                item["sourceSha256"] for item in proof["referenceNodes"]
                if item["sourceBytes"] == len(source)
                and item["sourceSha256"] == _sha(source)))
        tree = ast.parse(derived.decode("utf-8"))
        functions = [node.name for node in tree.body
                     if isinstance(node, (ast.FunctionDef, ast.ClassDef))]
        self.assertIn("_bootstrap_base", functions)
        self.assertIn("checked_read", functions)
        self.assertIn("validate_packet", functions)
        self.assertEqual(functions.count("main"), 1)  # adapter owns the current entry point
        self.assertNotIn("parse_args", functions)
        self.assertNotIn("CLI_FLAGS", [target.id for node in tree.body
                                       if isinstance(node, ast.Assign)
                                       for target in node.targets if isinstance(target, ast.Name)])
        self.assertNotIn(b"def parse_args", derived)
        self.assertNotIn(b'def main(argv=None):\n    return 0\n', derived)
        self.assertNotIn(b'CLI_FLAGS = ("--input",)', derived)

    def test_exact_25_function_10_constant_host_closure_and_kernel_bytes(self):
        self.assertEqual(generator.HOST_FUNCTIONS, EXPECTED_HOST_FUNCTIONS)
        self.assertEqual(generator.HOST_CONSTANTS, EXPECTED_HOST_CONSTANTS)
        self.assertEqual(len(EXPECTED_HOST_FUNCTIONS), 25)
        self.assertEqual(len(EXPECTED_HOST_CONSTANTS), 10)
        with _synthetic_pins():
            derived, proof = generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE,
                                              KERNEL_FIXTURE, ADAPTER_FIXTURE,
                                              WIRE_FIXTURE)
        self.assertEqual(proof["hostKernelFunctionCount"], 25)
        self.assertEqual(proof["hostKernelConstantCount"], 10)
        self.assertEqual(proof["hostKernelNodeCount"], 35)
        self.assertEqual({item["name"] for item in proof["hostKernelNodes"]},
                         set(EXPECTED_HOST_FUNCTIONS + EXPECTED_HOST_CONSTANTS))
        self.assertTrue(all(item["derivedName"] == "RH_" + item["name"]
                            and item["tokenRenamed"]
                            for item in proof["hostKernelNodes"]))
        self.assertEqual(proof["kernelBindings"],
                         ["GR7_RESULT_SCHEMA", "gr7_need", "validate_result_and_marker"])
        self.assertTrue(proof["kernelByteEqual"])
        self.assertEqual(proof["kernelSha256"], _sha(KERNEL_FIXTURE))
        self.assertEqual(derived.count(KERNEL_FIXTURE), 1)
        self.assertTrue(proof["adapterByteEqual"])
        self.assertEqual(derived.count(ADAPTER_FIXTURE), 1)
        self.assertEqual(proof["R7ActualCounterProof"], "NOT_EXECUTED_SOURCE_ONLY")

    def test_host_extraction_rejects_missing_and_duplicate_nodes_but_ignores_unselected_nodes(self):
        missing = HOST_FIXTURE.replace(b"def _validate_class_tree(",
                                       b"def validate_unselected(")
        with _synthetic_pins(host=missing):
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(missing, REFERENCE_FIXTURE, KERNEL_FIXTURE,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "HOST_KERNEL_CLOSURE")

        duplicate = HOST_FIXTURE + b"\ndef need(condition, code):\n    return None\n"
        with _synthetic_pins(host=duplicate):
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(duplicate, REFERENCE_FIXTURE, KERNEL_FIXTURE,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "HOST_READER_DUPLICATE_NAME")

        extra = HOST_FIXTURE + b"\nUNSELECTED_HOST_HELPER = 9\n"
        with _synthetic_pins(host=extra):
            derived, proof = generator.derive(extra, REFERENCE_FIXTURE, KERNEL_FIXTURE,
                                              ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(proof["hostKernelNodeCount"], 35)
        self.assertNotIn(b"UNSELECTED_HOST_HELPER", derived)

    def test_source_pins_reject_changed_host_reference_and_wire_bytes(self):
        cases = (
            (HOST_FIXTURE + b"# changed\n", REFERENCE_FIXTURE, WIRE_FIXTURE,
             "HOST_READER_HASH"),
            (HOST_FIXTURE, REFERENCE_FIXTURE + b"# changed\n", WIRE_FIXTURE,
             "REFERENCE_READER_HASH"),
            (HOST_FIXTURE, REFERENCE_FIXTURE, WIRE_FIXTURE + b"# changed\n",
             "WIRE_HASH"),
        )
        for host, reference, wire, expected_code in cases:
            with self.subTest(expected_code=expected_code), _synthetic_pins():
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.derive(host, reference, KERNEL_FIXTURE,
                                     ADAPTER_FIXTURE, wire)
                self.assertEqual(raised.exception.code, expected_code)

    def test_wire_contract_hash_is_fixed_and_rejects_synthetic_wire(self):
        self.assertEqual(generator.WIRE_SHA256, EXPECTED_WIRE_SHA256)
        with self.assertRaises(generator.GeneratorError) as raised:
            generator._pinned_source(WIRE_FIXTURE, EXPECTED_WIRE_SHA256, "WIRE")
        self.assertEqual(raised.exception.code, "WIRE_HASH")

    def test_kernel_requires_current_binding_and_forbids_future_cli_loaders_and_sys_path(self):
        invalid = (
            (b"from __future__ import annotations\n" + KERNEL_FIXTURE,
             "KERNEL_FUTURE_IMPORT"),
            (KERNEL_FIXTURE + b"\nif __name__ == '__main__':\n    pass\n",
             "KERNEL_MAIN_GUARD"),
            (KERNEL_FIXTURE + b"\nprint('unexpected top-level call')\n",
             "KERNEL_TOP_LEVEL_NODE"),
            (KERNEL_FIXTURE.replace(b"def validate_result_and_marker(",
                                    b"def validate_packet("),
             "KERNEL_BINDING_PROFILE"),
            (KERNEL_FIXTURE + b"\ndef main():\n    return 0\n",
             "KERNEL_BINDING_PROFILE"),
            (KERNEL_FIXTURE + b"\ndef gr7_bad():\n    open('packet.json')\n",
             "KERNEL_EXECUTABLE_LOADER"),
            (KERNEL_FIXTURE + b"\ndef gr7_bad():\n    exec('value = 1')\n",
             "KERNEL_EXECUTABLE_LOADER"),
            (b"import sys\n" + KERNEL_FIXTURE
             + b"\ndef gr7_bad():\n    return sys.path\n",
             "KERNEL_SYS_PATH"),
        )
        for source, expected_code in invalid:
            with self.subTest(expected_code=expected_code), self.assertRaises(
                    generator.GeneratorError) as raised:
                generator._audit_kernel(source)
            self.assertEqual(raised.exception.code, expected_code)

    def test_string_expression_docstrings_are_nonbinding_mid_module(self):
        kernel_bindings = generator._audit_kernel(KERNEL_FIXTURE)
        self.assertEqual(kernel_bindings,
                         ["GR7_RESULT_SCHEMA", "gr7_need", "validate_result_and_marker"])
        _adapter_tree, adapter_bindings = generator._audit_adapter(ADAPTER_FIXTURE)
        self.assertEqual(adapter_bindings, ["main", "<main-guard>"])
        with _synthetic_pins():
            derived, proof = generator.derive(HOST_FIXTURE, REFERENCE_FIXTURE,
                                              KERNEL_FIXTURE, ADAPTER_FIXTURE,
                                              WIRE_FIXTURE)
        self.assertIn(b'"""synthetic kernel module docstring"""', derived)
        self.assertIn(b'"""synthetic adapter module docstring"""', derived)
        self.assertEqual(proof["kernelBindings"], kernel_bindings)
        self.assertTrue(proof["adapterByteEqual"])

    def test_reference_kernel_name_collision_is_rejected(self):
        reference = REFERENCE_FIXTURE.replace(
            b'REFERENCE_CONSTANT = "kept"\n',
            b'REFERENCE_CONSTANT = "kept"\nGR7_COLLISION = "reference"\n')
        kernel = KERNEL_FIXTURE.replace(
            b"def validate_result_and_marker(",
            b'GR7_COLLISION = "kernel"\ndef validate_result_and_marker(')
        with _synthetic_pins(reference=reference):
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.derive(HOST_FIXTURE, reference, kernel,
                                 ADAPTER_FIXTURE, WIRE_FIXTURE)
        self.assertEqual(raised.exception.code, "DERIVED_DUPLICATE_BINDING")

    def test_adapter_entry_and_loader_boundaries(self):
        cases = (
            (b"import subprocess\n" + ADAPTER_FIXTURE, "ADAPTER_IMPORT"),
            (b"from __future__ import annotations\n" + ADAPTER_FIXTURE,
             "ADAPTER_FUTURE_IMPORT"),
            (b"import sys\ndef main():\n    return sys.path\n"
             b"if __name__ == '__main__':\n    raise SystemExit(main())\n",
             "ADAPTER_SYS_PATH"),
            (b"def main():\n    __import__('json')\n"
             b"if __name__ == '__main__':\n    raise SystemExit(main())\n",
             "ADAPTER_DYNAMIC_LOADER"),
            (b"def main():\n    exec('value = 1')\n"
             b"if __name__ == '__main__':\n    raise SystemExit(main())\n",
             "ADAPTER_DYNAMIC_LOADER"),
            (ADAPTER_FIXTURE.replace(b"def main(argv=None):",
                                     b"print('unexpected top-level call')\ndef main(argv=None):"),
             "ADAPTER_TOP_LEVEL_NODE"),
            (b"def main():\n    return 0\n", "ADAPTER_ENTRY_GUARD"),
        )
        for source, expected_code in cases:
            with self.subTest(expected_code=expected_code), self.assertRaises(
                    generator.GeneratorError) as raised:
                generator._audit_adapter(source)
            self.assertEqual(raised.exception.code, expected_code)

    def test_cli_requires_exact_eight_canonical_flag_value_pairs(self):
        self.assertEqual(generator.CLI_FLAGS, EXPECTED_CLI_FLAGS)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            names = ("host.py", "reference.py", "kernel.py", "adapter.py",
                     "wire.md", "base.py", "io.py", "output")
            paths = [str(root / name) for name in names]
            argv = [part for pair in zip(EXPECTED_CLI_FLAGS, paths) for part in pair]
            expected = dict(zip(EXPECTED_CLI_FLAGS, paths))
            self.assertEqual(generator.parse_args(argv), expected)

            invalid = []
            invalid.append((argv[:-2], "missing flag/value pair"))
            duplicate = list(argv)
            duplicate[-2] = EXPECTED_CLI_FLAGS[0]
            invalid.append((duplicate, "duplicate flag"))
            unknown = list(argv)
            unknown[0] = "--unknown"
            invalid.append((unknown, "unknown flag"))
            no_value = list(argv)
            no_value.pop()
            invalid.append((no_value, "missing value"))
            for bad_argv, label in invalid:
                with self.subTest(label=label), self.assertRaises(
                        generator.GeneratorError) as raised:
                    generator.parse_args(bad_argv)
                self.assertEqual(raised.exception.code, "CLI_ARGUMENTS")

            aliased = list(argv)
            if os.name == "nt":
                aliased[1] = str(root) + "\\..\\" + Path(paths[0]).name
            else:
                aliased[1] = str(root) + "/../" + Path(paths[0]).name
            with self.assertRaises(generator.GeneratorError) as raised:
                generator.parse_args(aliased)
            self.assertEqual(raised.exception.code, "CLI_PATH_ALIAS")

    def test_generate_options_must_match_the_eight_flag_contract(self):
        with self.assertRaises(generator.GeneratorError) as raised:
            generator.generate({"--host-reader": "unused"})
        self.assertEqual(raised.exception.code, "GENERATOR_OPTIONS")

    def test_occupied_output_is_rejected_before_any_source_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / "output"
            output.mkdir()
            sentinel = output / "sentinel"
            sentinel.write_bytes(b"preserve")
            options = {
                "--host-reader": str(root / "missing-host.py"),
                "--reference-reader": str(root / "missing-reference.py"),
                "--kernel": str(root / "missing-kernel.py"),
                "--adapter": str(root / "missing-adapter.py"),
                "--wire": str(root / "missing-wire.md"),
                "--base-generator": str(root / "base.py"),
                "--io-helper": str(root / "io.py"),
                "--output-dir": str(output),
            }
            io_module = _FakeIO(output)
            base = _FakeBase(io_module)
            with _patched_generation(base):
                with mock.patch.object(generator, "_read_input_bound",
                                       side_effect=AssertionError("source read before output guard")) as read:
                    with self.assertRaises(generator.GeneratorError) as raised:
                        generator.generate(options)
            self.assertEqual(raised.exception.code, "OUTPUT_NOT_EMPTY")
            read.assert_not_called()
            self.assertEqual(io_module.read_paths, [])
            self.assertEqual(sentinel.read_bytes(), b"preserve")

    def test_generate_exclusively_writes_both_outputs_and_reads_back_exact_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths, output, options = _write_generation_inputs(root)
            io_module = _FakeIO(output)
            base = _FakeBase(io_module)
            with _patched_generation(base):
                expected_source, expected_proof = generator.derive(
                    HOST_FIXTURE, REFERENCE_FIXTURE, KERNEL_FIXTURE,
                    ADAPTER_FIXTURE, WIRE_FIXTURE)
                proof = generator.generate(options)
            self.assertEqual(proof, expected_proof)
            proof_raw = (json.dumps(proof, sort_keys=True, indent=2,
                                    ensure_ascii=True) + "\n").encode("ascii")
            expected_files = {
                "read_source_bound_generic_r7_v1.py": expected_source,
                "SOURCE_PROOF.json": proof_raw,
            }
            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             sorted(expected_files))
            self.assertEqual([path.name for path in io_module.write_paths],
                             list(expected_files))
            for name, raw in expected_files.items():
                self.assertEqual((output / name).read_bytes(), raw)
            for source_path in paths.values():
                self.assertEqual(io_module.read_paths.count(source_path), 3)

    def test_exclusive_create_rejects_a_racing_occupied_output_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _paths, output, options = _write_generation_inputs(root)
            state = {"occupied": False}

            def occupy_target(path, _raw):
                if path.name == generator.OUTPUT_NAMES[0] and not state["occupied"]:
                    state["occupied"] = True
                    with path.open("xb") as stream:
                        stream.write(b"foreign file")

            io_module = _FakeIO(output, before_write=occupy_target)
            base = _FakeBase(io_module)
            with _patched_generation(base):
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.generate(options)
            self.assertEqual(raised.exception.code, "OUTPUT_FILE_EXISTS")
            self.assertEqual((output / generator.OUTPUT_NAMES[0]).read_bytes(),
                             b"foreign file")

    def test_readback_mismatch_fails_and_preserves_written_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            _paths, output, options = _write_generation_inputs(root)
            io_module = _FakeIO(output, corrupt_readback=True)
            base = _FakeBase(io_module)
            with _patched_generation(base):
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.generate(options)
            self.assertEqual(raised.exception.code, "OUTPUT_READBACK_CHANGED")
            self.assertTrue((output / generator.OUTPUT_NAMES[0]).is_file())
            # This owner writes both files before its readback pass. A failed
            # readback preserves both unaccepted outputs; it does not roll back.
            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             ["SOURCE_PROOF.json", "read_source_bound_generic_r7_v1.py"])
            actual_source = (output / generator.OUTPUT_NAMES[0]).read_bytes()
            actual_proof = json.loads((output / "SOURCE_PROOF.json").read_bytes())
            self.assertEqual(actual_proof["derivedSha256"],
                             hashlib.sha256(actual_source).hexdigest())
            self.assertEqual(actual_proof["status"], "GENERATED_SOURCE_ONLY_NOT_EXECUTED")

    def test_changed_source_is_rejected_on_final_recheck_after_outputs_exist(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths, output, options = _write_generation_inputs(root)
            state = {"changed": False}

            def change_host(path, _raw):
                if path.name == generator.OUTPUT_NAMES[0] and not state["changed"]:
                    state["changed"] = True
                    paths["--host-reader"].write_bytes(HOST_FIXTURE + b"# changed later\n")

            io_module = _FakeIO(output, before_write=change_host)
            base = _FakeBase(io_module)
            with _patched_generation(base):
                with self.assertRaises(generator.GeneratorError) as raised:
                    generator.generate(options)
            self.assertEqual(raised.exception.code, "SOURCE_CHANGED")
            self.assertTrue(state["changed"])
            self.assertEqual(sorted(item.name for item in output.iterdir()),
                             sorted(generator.OUTPUT_NAMES))
            self.assertEqual(paths["--host-reader"].read_bytes(),
                             HOST_FIXTURE + b"# changed later\n")

    @unittest.skipUnless(all(_SOURCE_ARGUMENTS),
                         "pass the five pinned source paths for the source-only integration gate")
    def test_optional_actual_five_source_bundle(self):
        # These are source files only: no result packet, marker, or receipt is opened.
        host_raw = Path(_OPTIONS.host_reader).read_bytes()
        reference_raw = Path(_OPTIONS.reference_reader).read_bytes()
        kernel_raw = Path(_OPTIONS.kernel).read_bytes()
        adapter_raw = Path(_OPTIONS.adapter).read_bytes()
        wire_raw = Path(_OPTIONS.wire).read_bytes()
        self.assertEqual(generator.HOST_READER_SHA256, EXPECTED_HOST_SHA256)
        self.assertEqual(generator.REFERENCE_READER_SHA256, EXPECTED_REFERENCE_SHA256)
        self.assertEqual(generator.WIRE_SHA256, EXPECTED_WIRE_SHA256)
        self.assertEqual(_sha(host_raw), EXPECTED_HOST_SHA256)
        self.assertEqual(_sha(reference_raw), EXPECTED_REFERENCE_SHA256)
        self.assertEqual(_sha(wire_raw), EXPECTED_WIRE_SHA256)

        derived, proof = generator.derive(host_raw, reference_raw, kernel_raw,
                                          adapter_raw, wire_raw)
        self.assertEqual(proof["hostReaderSha256"], EXPECTED_HOST_SHA256)
        self.assertEqual(proof["referenceReaderSha256"], EXPECTED_REFERENCE_SHA256)
        self.assertEqual(proof["wireContractSha256"], EXPECTED_WIRE_SHA256)
        self.assertEqual(proof["hostKernelFunctionCount"], 25)
        self.assertEqual(proof["hostKernelConstantCount"], 10)
        self.assertEqual(proof["hostKernelNodeCount"], 35)
        self.assertTrue(all(item["byteEqual"] for item in proof["referenceNodes"]))
        self.assertIn("validate_result_and_marker", proof["kernelBindings"])
        self.assertEqual(len(proof["kernelBindings"]), len(set(proof["kernelBindings"])))
        self.assertTrue(proof["kernelByteEqual"])
        self.assertTrue(proof["adapterByteEqual"])
        self.assertEqual(derived.count(kernel_raw), 1)
        self.assertTrue(derived.endswith(adapter_raw))
        self.assertEqual(proof["status"], "GENERATED_SOURCE_ONLY_NOT_EXECUTED")
        self.assertEqual(proof["R7ActualCounterProof"], "NOT_EXECUTED_SOURCE_ONLY")


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *_UNITTEST_ARGS])
