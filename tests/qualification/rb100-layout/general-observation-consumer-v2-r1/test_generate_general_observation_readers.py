"""Source/adapter/checked-I/O tests; no original capture or full-D qualification.

Run Python -I -B with explicit --generic-reader, --full-reader, --kernel,
--base-generator and --io-helper pairs after root has fixed the reviewed pin.
Adapter forwarding seams are not whole-result or captured-run evidence.
"""
from __future__ import annotations

import ast
import contextlib
import hashlib
import io
import json
import ntpath
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

OPTIONS = None
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_general_observation_readers.py")
g = types.ModuleType("_general_observation_generator_test")
g.__file__ = str(GENERATOR_PATH)
exec(compile(GENERATOR_PATH.read_bytes(), str(GENERATOR_PATH), "exec"), g.__dict__)
TEST_FLAGS = ("--generic-reader", "--full-reader", "--kernel", "--base-generator", "--io-helper")


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def span(raw, start, end):
    if raw.count(start) != 1 or raw.count(end) != 1:
        raise AssertionError("SOURCE_ORACLE_ANCHOR_COUNT")
    begin, finish = raw.index(start), raw.index(end)
    if finish <= begin:
        raise AssertionError("SOURCE_ORACLE_ANCHOR_ORDER")
    return raw[begin:finish]


def functions(raw):
    text = raw.decode("utf-8")
    return {node.name: ast.get_source_segment(text, node)
            for node in ast.parse(text).body if type(node) is ast.FunctionDef}


def assignments(raw):
    text = raw.decode("utf-8")
    return {node.targets[0].id: ast.get_source_segment(text, node)
            for node in ast.parse(text).body if type(node) is ast.Assign
            and len(node.targets) == 1 and type(node.targets[0]) is ast.Name}


def module_from(raw, path):
    module = types.ModuleType("_derived_observation_component_test")
    module.__file__ = str(path)
    exec(compile(raw, str(path), "exec"), module.__dict__)
    return module


class ConsumerOverlayTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if OPTIONS is None:
            raise RuntimeError("TEST_SOURCE_OPTIONS_REQUIRED")
        cls.sources = {key: Path(OPTIONS[key]).read_bytes() for key in TEST_FLAGS}
        expected = {
            "--generic-reader": "685e18d44f322a61d36f1aafa430cb562a5a7f536e2aa9c2640be929881d646b",
            "--full-reader": "2d53b33fb77e85c6c36f46b013612883866cffea2996596751ffcb2d30e2bc0a",
            "--base-generator": "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0",
            "--io-helper": "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923",
            "--kernel": g.KERNEL_SHA256,
        }
        if g.KERNEL_SHA256 == "ROOT_PIN_REQUIRED":
            raise RuntimeError("ROOT_KERNEL_PIN_REQUIRED")
        for key in TEST_FLAGS:
            if digest(cls.sources[key]) != expected[key]:
                raise RuntimeError("TEST_SOURCE_PIN")
        cls.r, cls.d, cls.changes = g.derive(
            cls.sources["--generic-reader"], cls.sources["--full-reader"], cls.sources["--kernel"])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tsj-general-observation-consumer-tests-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        names = ("generic.py", "full.py", "kernel.py", "base.py", "exporter.py")
        self.options = {}
        for key, name in zip(TEST_FLAGS, names):
            path = self.root / name
            path.write_bytes(self.sources[key])
            self.options[key] = str(path)
        self.output = self.root / "output"
        self.output.mkdir()
        self.options["--output-dir"] = str(self.output)

    def derive(self, **overrides):
        return g.derive(overrides.get("generic", self.sources["--generic-reader"]),
                        overrides.get("full", self.sources["--full-reader"]),
                        overrides.get("kernel", self.sources["--kernel"]))

    def assert_code(self, code, call, owner=None):
        with self.assertRaises(Exception) as caught:
            call()
        if owner is not None:
            self.assertIs(type(caught.exception), owner)
        self.assertEqual(getattr(caught.exception, "code", None), code)

    def selected_io(self):
        base = g.load_base_generator(self.options["--base-generator"])
        return base, base.load_io(self.options["--io-helper"])

    def with_io(self, base, checked_io):
        stack = contextlib.ExitStack()
        stack.enter_context(mock.patch.object(g, "load_base_generator", return_value=base))
        stack.enter_context(mock.patch.object(base, "load_io", return_value=checked_io))
        return stack

    def test_fixed_pins_and_deterministic_source_only_derivation(self):
        self.assertEqual(g.GENERIC_READER_SHA256, digest(self.sources["--generic-reader"]))
        self.assertEqual(g.FULL_READER_SHA256, digest(self.sources["--full-reader"]))
        self.assertEqual(g.BASE_GENERATOR_SHA256, digest(self.sources["--base-generator"]))
        self.assertEqual(g.IO_HELPER_SHA256, digest(self.sources["--io-helper"]))
        self.assertRegex(g.KERNEL_SHA256, r"\A[0-9a-f]{64}\Z")
        # Source derivation cannot execute either reader or the kernel.
        with mock.patch.object(g, "exec", create=True, side_effect=AssertionError("EXECUTED")):
            self.assertEqual(self.derive(), (self.r, self.d, self.changes))
        self.assertEqual(self.changes, g.CHANGES)
        self.assertEqual(len(self.changes), len(set(self.changes)))
        for raw in (self.r, self.d):
            self.assertIs(type(raw), bytes)
            self.assertNotIn(b"\r", raw)
            self.assertLessEqual(len(raw), g.MAX_SOURCE_BYTES)

    def test_placeholder_fails_closed_before_loading_any_source(self):
        with mock.patch.object(g, "KERNEL_SHA256", "ROOT_PIN_REQUIRED"), \
                mock.patch.object(g, "load_base_generator", side_effect=AssertionError("LOAD")):
            self.assert_code("ROOT_KERNEL_PIN_REQUIRED", self.derive, g.OverlayError)
            self.assert_code("ROOT_KERNEL_PIN_REQUIRED", lambda: g.generate(self.options), g.OverlayError)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_each_source_type_size_and_pin_is_enforced(self):
        for name, code in (("generic", "GENERIC_READER"), ("full", "FULL_READER"), ("kernel", "KERNEL")):
            for bad in (None, "source", bytearray(b"source"), b"", b"x" * (g.MAX_SOURCE_BYTES + 1)):
                with self.subTest(name=name, bad_type=type(bad)):
                    self.assert_code(code + "_SIZE", lambda: self.derive(**{name: bad}), g.OverlayError)
            self.assert_code(code + "_HASH", lambda: self.derive(**{name: b"untrusted\n"}), g.OverlayError)
        raw = self.sources["--generic-reader"].replace(b"\n", b"\r\n")
        with mock.patch.object(g, "GENERIC_READER_SHA256", digest(raw)):
            self.assert_code("GENERIC_READER_LINE_ENDINGS", lambda: self.derive(generic=raw), g.OverlayError)

    def test_exact_once_and_ordered_splice_guards(self):
        for bad in (None, "text", bytearray(b"text")):
            self.assert_code("SPLICE_TYPES", lambda: g.replace_once(bad, b"x", b"y"), g.OverlayError)
        for raw, before in ((b"a", b""), (b"a", b"x"), (b"xx", b"x")):
            self.assert_code("SPLICE_COUNT", lambda: g.replace_once(raw, before, b"y"), g.OverlayError)
        self.assert_code("REG_START", lambda: g.replace_region(b"end", b"start", b"end", b"x", "REG"))
        self.assert_code("REG_END_COUNT", lambda: g.replace_region(b"start", b"start", b"end", b"x", "REG"))
        self.assert_code("REG_END", lambda: g.replace_region(b"endstart", b"start", b"end", b"x", "REG"))
        raw = self.sources["--generic-reader"]
        anchor = b"def _head(h, s):\n"
        for changed in (raw.replace(anchor, b"def altered(h, s):\n"), raw + anchor):
            with mock.patch.object(g, "GENERIC_READER_SHA256", digest(changed)):
                self.assert_code("GENERIC_SNAPSHOT_END_COUNT", lambda: self.derive(generic=changed))

    def test_verbatim_kernel_after_standard_imports_exactly_once(self):
        kernel = self.sources["--kernel"]
        for raw in (self.r, self.d):
            self.assertEqual(raw.count(kernel), 1)
            self.assertIn(b"import types\n\n" + kernel + b"\n", raw)
            self.assertEqual(raw.count(b"class ObservationV2Kernel:"), 1)
            self.assertEqual(raw.count(b"class ObservationV2Error("), 1)
            parsed = ast.parse(raw)
            self.assertEqual(sum(type(node) is ast.ImportFrom and node.module == "__future__"
                                 for node in parsed.body), 1)
        for raw in (b"no anchor\n", b"import types\nimport types\n"):
            self.assert_code("KERNEL_IMPORT_ANCHOR", lambda: g._embed_kernel(raw, kernel))
        self.assert_code("KERNEL_ALREADY_PRESENT",
                         lambda: g._embed_kernel(b"import types\n" + kernel, kernel))
        for extra in (b"from __future__ import annotations\n", b"# __main__\n"):
            changed = kernel + extra
            with mock.patch.object(g, "KERNEL_SHA256", digest(changed)):
                self.assert_code("KERNEL_STANDALONE_DOMAIN", lambda: self.derive(kernel=changed))

    def test_unmodified_generic_profile_parser_limits_cli_and_summary(self):
        before, after = functions(self.sources["--generic-reader"]), functions(self.r)
        changed = {"_snapshot", "_head", "_observation_accounting"}
        kernel_functions = set(functions(self.sources["--kernel"]))
        self.assertTrue(all(name.startswith("_ov2_") for name in kernel_functions))
        self.assertEqual(set(before), set(after) - kernel_functions)
        for name in set(before) - changed:
            self.assertEqual(before[name], after[name], name)
        for name, value in assignments(self.sources["--generic-reader"]).items():
            self.assertEqual(value, assignments(self.r)[name], name)
        for name in changed:
            self.assertNotEqual(before[name], after[name])
        self.assertNotIn("94 * d", after["_observation_accounting"])
        self.assertNotIn("expected_key", after["_head"])
        self.assertNotIn("relaxedBoundStarted", after["_snapshot"])

    def test_full_retired_domains_and_byte_equal_work_algebra(self):
        before, after = functions(self.sources["--full-reader"]), functions(self.d)
        changed = {"_validate_snapshot", "_validate_observation", "main"}
        for name in set(before) - changed:
            self.assertEqual(before[name], after[name], name)
        self.assertEqual(span(self.sources["--full-reader"], b"def _validate_work(", b"def _validate_top_cleanup("),
                         span(self.d, b"def _validate_work(", b"def _validate_top_cleanup("))
        for stale in (b'tsj-cohort-terminal-observation-v1', b'\nOBSERVATION_SCHEMA =',
                      b'\nOBSERVATION_KEYS =', b'\nSNAPSHOT_KEYS =',
                      b'\nMAX_CALLBACKS =', b'\nMAX_OBSERVER_EVENT_UNITS ='):
            self.assertNotIn(stale, self.d)
        self.assertNotIn("33 * callbacks", after["_validate_observation"])
        self.assertNotIn("branchExpanded", after["_validate_snapshot"])
        self.assertEqual(assignments(self.d)["N_ROOT"],
                         'N_ROOT = ROOT / "_unavailable_full_dependencies"')

    def test_sanitized_dependency_anchor_rejects_missing_or_duplicate(self):
        raw = self.sources["--full-reader"]
        lines = [line for line in raw.splitlines(keepends=True) if line.startswith(b"N_ROOT = Path(")]
        self.assertEqual(len(lines), 1)
        for changed in (raw.replace(lines[0], b""), raw + lines[0]):
            with mock.patch.object(g, "FULL_READER_SHA256", digest(changed)):
                self.assert_code("FULL_DEPENDENCY_DEFAULT_ANCHOR", lambda: self.derive(full=changed))
        self.assertNotIn(lines[0].rstrip(), self.d)
        for node in ast.walk(ast.parse(self.d)):
            if type(node) is ast.Constant and type(node.value) is str:
                self.assertFalse(ntpath.isabs(node.value), "PRIVATE_ABSOLUTE_LITERAL")

    def test_generic_fixed_context_and_error_translation(self):
        r = module_from(self.r, self.output / g.OUTPUT_NAMES[0])
        context = {
            "requestedNetIds": ["N"], "traversedNetIds": ["N"],
            "padsById": {"a": {"netId": "N", "x": -3, "y": 5},
                         "b": {"netId": "N", "x": 9, "y": 5}},
            "gridWidth": 4, "gridHeight": 3,
            "outcome": {"disposition": "NO_PATH", "reason": "N:NO_PATH", "expanded": 0},
            "terminalFailure": None, "callbackOpportunities": 1,
        }
        s = dict.fromkeys(r.SNAPSHOT_KEYS)
        s.update(netId="N", startPadId="a", rootPadId="b")
        with mock.patch.object(r.ObservationV2Kernel, "validate_snapshot", return_value=1) as check:
            self.assertIsNone(r._snapshot(s, context))
            check.assert_called_once_with(s, ["N"], ["N"],
                                          {"a": ("N", -3, 5), "b": ("N", 9, 5)},
                                          {"N": {"a", "b"}}, (4, 3))
            for field, bad in (("requestedNetIds", ["N", "M"]),
                               ("traversedNetIds", []), ("padsById", {})):
                altered = dict(context, **{field: bad})
                with self.assertRaises(r.ReaderError):
                    r._snapshot(s, altered)
            with self.assertRaises(r.ReaderError):
                r._snapshot(dict(s, rootPadId="a"), context)
            self.assertEqual(check.call_count, 1)
        for method, call in (("validate_snapshot", lambda: r._snapshot(s, context)),
                             ("validate_head", lambda: r._head({}, {})),
                             ("accounting", lambda: r._observation_accounting({}))):
            with mock.patch.object(r.ObservationV2Kernel, method,
                                   side_effect=r.ObservationV2Error("DOMAIN")):
                self.assert_code("SW_DOMAIN", call, r.ReaderError)

    def test_full_adapter_exact_context_forwarding_and_immutable_result(self):
        d = module_from(self.d, self.output / g.OUTPUT_NAMES[1])
        value, requested, traversed = {}, ["A", "B"], ["A", "B"]
        pads, members, grid = {"p": ("B", -2, 3)}, {"B": {"p"}}, (7, 9)
        outcome, terminal = {}, {}
        fresh = {"work": {"advanceCalls": 3},
                 "ordering": {"requestedNetIds": requested, "traversedNetIds": traversed}}
        facts = {"outcome": outcome, "terminalFailure": terminal}
        result = types.MappingProxyType({"observerUnits": 0, "copiedEntries": 0,
                                         "copyChunks": 0, "state": "NO_FAILURE"})
        with mock.patch.object(d.ObservationV2Kernel, "validate_observation", return_value=result) as check:
            actual = d._validate_observation(value, "OBSERVE", fresh, facts, object(), pads, members, grid)
            self.assertIs(actual, result)
            check.assert_called_once_with(value, "OBSERVE", 3, requested, traversed,
                                          pads, members, grid, outcome, terminal)
            with self.assertRaises(TypeError):
                actual["observerUnits"] = 1
        with mock.patch.object(d.ObservationV2Kernel, "validate_snapshot", return_value=2) as check:
            self.assertEqual(d._validate_snapshot(value, requested, traversed, pads, members, grid), 2)
            check.assert_called_once_with(value, requested, traversed, pads, members, grid)
        with mock.patch.object(d.ObservationV2Kernel, "validate_observation",
                               side_effect=d.ObservationV2Error("DOMAIN")):
            self.assert_code("BF_DOMAIN", lambda: d._validate_observation(
                value, "OBSERVE", fresh, facts, None, pads, members, grid), d.ReaderError)

    def test_full_main_denies_before_all_argument_data_and_host_hooks(self):
        d = module_from(self.d, self.output / g.OUTPUT_NAMES[1])
        with contextlib.ExitStack() as stack:
            for name in ("_parse_cli_args", "_require_supported_cli", "_load_d_builder",
                         "_audit_host_call", "_read_regular", "validate_result", "validate_canary"):
                stack.enter_context(mock.patch.object(d, name, side_effect=AssertionError(name)))
            for argv in (None, [], ["--manifest", "unread"], object()):
                err, out = io.StringIO(), io.StringIO()
                with contextlib.redirect_stderr(err), contextlib.redirect_stdout(out):
                    self.assertEqual(d.main(argv), 2)
                self.assertEqual(err.getvalue(), "GENERAL_OBSERVATION_COMPONENT_ONLY\n")
                self.assertEqual(out.getvalue(), "")
        self.assertEqual(list(self.output.iterdir()), [])

    def test_exact_six_pair_parser_and_raw_path_aliases(self):
        args = [part for pair in self.options.items() for part in pair]
        self.assertEqual(g.parse_args(args), self.options)
        for bad in (None, {}, "args", args[:-1], args + ["--old", "x"], args[:2] * 6):
            self.assert_code("CLI_ARGUMENTS", lambda: g.parse_args(bad))
        for flag in g.CLI_FLAGS:
            path = Path(self.options[flag])
            alias = str(path.parent) + os.sep + "." + os.sep + path.name
            with mock.patch.object(g, "load_base_generator", side_effect=AssertionError("LOAD")):
                self.assert_code("INPUT_CANONICAL", lambda: g.generate(dict(self.options, **{flag: alias})))
                self.assert_code("INPUT_ABSOLUTE", lambda: g.generate(dict(self.options, **{flag: path.name})))

    def test_real_checked_io_positive_exact_outputs_and_private_path_free_proof(self):
        proof = g.generate(self.options)
        self.assertEqual(set(path.name for path in self.output.iterdir()), set(g.OUTPUT_NAMES))
        self.assertEqual((self.output / g.OUTPUT_NAMES[0]).read_bytes(), self.r)
        self.assertEqual((self.output / g.OUTPUT_NAMES[1]).read_bytes(), self.d)
        raw = (self.output / "SOURCE_PROOF.json").read_bytes()
        self.assertEqual(json.loads(raw), proof)
        self.assertFalse(proof["fullDQualification"])
        self.assertEqual(proof["kernelSha256"], digest(self.sources["--kernel"]))
        self.assertEqual(proof["changes"], list(g.CHANGES))
        self.assertEqual(proof["derivedGenericSha256"], digest(self.r))
        self.assertEqual(proof["derivedFullComponentSha256"], digest(self.d))
        self.assertNotIn(str(self.root).encode(), raw)
        for value in proof.values():
            if type(value) is str:
                self.assertFalse(ntpath.isabs(value))
        self.assert_code("OUTPUT_NOT_EMPTY", lambda: g.generate(self.options))

    def test_real_checked_io_wrong_source_base_and_io_pins_leave_empty_output(self):
        for flag, expected in (("--generic-reader", "GENERIC_READER_HASH"),
                               ("--full-reader", "FULL_READER_HASH"),
                               ("--kernel", "KERNEL_HASH"),
                               ("--base-generator", "BASE_GENERATOR_HASH"),
                               ("--io-helper", "IO_HELPER_HASH")):
            path = Path(self.options[flag])
            original = path.read_bytes()
            path.write_bytes(original + b"# changed\n")
            try:
                self.assert_code(expected, lambda: g.generate(self.options))
                self.assertEqual(list(self.output.iterdir()), [])
            finally:
                path.write_bytes(original)

    def test_real_checked_io_occupied_output_and_exclusive_race_preserve_bytes(self):
        occupied = self.output / "preserved"
        occupied.write_bytes(b"preserve")
        self.assert_code("OUTPUT_NOT_EMPTY", lambda: g.generate(self.options))
        self.assertEqual(occupied.read_bytes(), b"preserve")
        occupied.unlink()
        base, checked_io = self.selected_io()
        write = checked_io._write_exclusive
        def collide(path, raw, limit, code):
            path.write_bytes(b"racing-owner")
            return write(path, raw, limit, code)
        with self.with_io(base, checked_io), mock.patch.object(checked_io, "_write_exclusive", side_effect=collide):
            with self.assertRaises(Exception):
                g.generate(self.options)
        self.assertEqual((self.output / g.OUTPUT_NAMES[0]).read_bytes(), b"racing-owner")
        self.assertEqual(len(list(self.output.iterdir())), 1)

    def test_output_census_and_readback_fail_closed(self):
        base, checked_io = self.selected_io()
        write = checked_io._write_exclusive
        def extra(path, raw, limit, code):
            write(path, raw, limit, code)
            if path.name == "SOURCE_PROOF.json":
                (path.parent / "unexpected").write_bytes(b"extra")
        with self.with_io(base, checked_io), mock.patch.object(checked_io, "_write_exclusive", side_effect=extra):
            self.assert_code("OUTPUT_CLOSURE", lambda: g.generate(self.options))
        # A second owned directory isolates the independent readback canary.
        second = self.root / "second-output"
        second.mkdir()
        options = dict(self.options, **{"--output-dir": str(second)})
        read = checked_io._read_regular
        def changed(path, limit, code):
            raw = read(path, limit, code)
            return raw + b" " if code == "OUTPUT_READBACK" else raw
        with self.with_io(base, checked_io), mock.patch.object(checked_io, "_read_regular", side_effect=changed):
            self.assert_code("OUTPUT_READBACK_CHANGED", lambda: g.generate(options))

    def test_real_symlink_boundaries_where_supported(self):
        link = self.root / "source-link.py"
        try:
            link.symlink_to(Path(self.options["--generic-reader"]))
        except (OSError, NotImplementedError):
            self.skipTest("OS_SYMLINK_UNAVAILABLE_ROOT_MUST_RECORD_REAL_JUNCTION_CANARY")
        try:
            with self.assertRaises(Exception):
                g.generate(dict(self.options, **{"--generic-reader": str(link)}))
            self.assertEqual(list(self.output.iterdir()), [])
        finally:
            link.unlink()

    def test_actual_isolated_generator_and_full_component_cli(self):
        args = [part for pair in self.options.items() for part in pair]
        completed = subprocess.run([sys.executable, "-I", "-B", str(GENERATOR_PATH), *args],
                                   cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(completed.stderr, b"")
        self.assertEqual(json.loads(completed.stdout)["files"], 3)
        denied = subprocess.run([sys.executable, "-I", "-B", str(self.output / g.OUTPUT_NAMES[1]),
                                 "--manifest", str(self.root / "does-not-exist"), "--old-mode"],
                                cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(denied.returncode, 2)
        self.assertEqual(denied.stdout, b"")
        self.assertEqual(denied.stderr, ("GENERAL_OBSERVATION_COMPONENT_ONLY" + os.linesep).encode("ascii"))
        self.assertEqual(set(path.name for path in self.output.iterdir()), set(g.OUTPUT_NAMES))
        self.assertFalse((self.output / "__pycache__").exists())
        occupied = subprocess.run([sys.executable, "-I", "-B", str(GENERATOR_PATH), *args],
                                  cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(occupied.returncode, 2)
        self.assertEqual(occupied.stdout, b"")
        self.assertEqual(occupied.stderr, ("GENERAL_OBSERVATION_OVERLAY_FAIL OUTPUT_NOT_EMPTY" + os.linesep).encode("ascii"))
        self.assertEqual((self.output / g.OUTPUT_NAMES[0]).read_bytes(), self.r)

    def test_actual_generator_bad_pin_and_isolation_rejections(self):
        path = Path(self.options["--kernel"])
        path.write_bytes(self.sources["--kernel"] + b"# invalid\n")
        args = [part for pair in self.options.items() for part in pair]
        completed = subprocess.run([sys.executable, "-I", "-B", str(GENERATOR_PATH), *args],
                                   cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(completed.stdout, b"")
        self.assertEqual(completed.stderr, ("GENERAL_OBSERVATION_OVERLAY_FAIL KERNEL_HASH" + os.linesep).encode("ascii"))
        self.assertEqual(list(self.output.iterdir()), [])
        completed = subprocess.run([sys.executable, "-B", str(GENERATOR_PATH), *args],
                                   cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(completed.stderr, ("GENERAL_OBSERVATION_OVERLAY_FAIL PYTHON_ISOLATION" + os.linesep).encode("ascii"))

    def test_actual_generator_raw_alias_rejection_precedes_reads(self):
        options = dict(self.options)
        options["--kernel"] = str(self.root) + os.sep + "." + os.sep + "absent-kernel.py"
        args = [part for pair in options.items() for part in pair]
        completed = subprocess.run([sys.executable, "-I", "-B", str(GENERATOR_PATH), *args],
                                   cwd=str(self.root), capture_output=True, timeout=30)
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(completed.stdout, b"")
        self.assertEqual(completed.stderr, ("GENERAL_OBSERVATION_OVERLAY_FAIL INPUT_CANONICAL" + os.linesep).encode("ascii"))
        self.assertEqual(list(self.output.iterdir()), [])


def parse_test_args(argv):
    if type(argv) not in (list, tuple) or len(argv) != 2 * len(TEST_FLAGS):
        raise ValueError("TEST_ARGUMENTS")
    options = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        if (type(flag) is not str or flag not in TEST_FLAGS or flag in options
                or type(value) is not str or not value or "\0" in value
                or not Path(value).is_absolute() or str(Path(value)) != value):
            raise ValueError("TEST_ARGUMENTS")
        options[flag] = value
    return options


if __name__ == "__main__":
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        raise SystemExit("PYTHON_ISOLATION_REQUIRED")
    OPTIONS = parse_test_args(sys.argv[1:])
    unittest.main(argv=[sys.argv[0]])
