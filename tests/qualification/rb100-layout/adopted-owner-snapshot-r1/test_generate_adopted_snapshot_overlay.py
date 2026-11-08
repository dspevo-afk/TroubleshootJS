"""Synthetic source and filesystem tests for adopted-snapshot derivation.

Run with Python -I -B. No production Study is needed unless the optional
--study, --base-generator, and --io-helper paths are all supplied.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest import mock

OPTIONS = None
GENERATOR_PATH = Path(__file__).resolve().with_name("generate_adopted_snapshot_overlay.py")
g = types.ModuleType("_adopted_snapshot_generator_under_test")
g.__file__ = str(GENERATOR_PATH)
exec(compile(GENERATOR_PATH.read_bytes(), str(GENERATOR_PATH), "exec"), g.__dict__)

SYNTHETIC_SOURCE = (
    b"class CohortTerminalTelemetryStudy {\n"
    + g.MAIN_BEFORE
    + b"static final class R5Snapshot {\n"
    + b"    void captureClosed() throws Exception {\n"
    + g.SNAPSHOT_BEFORE
    + b"\n    }\n}\n}\n"
)
SYNTHETIC_SHA256 = hashlib.sha256(SYNTHETIC_SOURCE).hexdigest()


class FakeExportError(ValueError):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class FakePinnedIO:
    """Test double for the pinned exporter's checked I/O methods."""

    @staticmethod
    def _path(value, code):
        try:
            path = value if isinstance(value, Path) else Path(value)
            if not path.is_absolute():
                raise FakeExportError(code + "_ABSOLUTE")
            for component in (path, *path.parents):
                info = component.lstat()
                if (component.is_symlink() or
                        bool(getattr(info, "st_file_attributes", 0) & 0x400)):
                    raise FakeExportError(code + "_REPARSE")
            if path.resolve(strict=True) != path:
                raise FakeExportError(code + "_CANONICAL")
            return path
        except FakeExportError:
            raise
        except (OSError, RuntimeError, TypeError, ValueError):
            raise FakeExportError(code + "_PATH") from None

    def _checked_directory(self, value, code):
        path = self._path(value, code)
        try:
            if not path.is_dir():
                raise FakeExportError(code + "_TYPE")
        except OSError:
            raise FakeExportError(code + "_STAT") from None
        return path

    def _directory_names(self, path, code):
        try:
            return [item.name for item in path.iterdir()]
        except OSError:
            raise FakeExportError(code + "_READ") from None

    def _read_regular(self, value, limit, code):
        path = self._path(value, code)
        try:
            info = path.lstat()
            if not path.is_file():
                raise FakeExportError(code + "_TYPE")
            if info.st_size <= 0 or info.st_size > limit:
                raise FakeExportError(code + "_SIZE")
            raw = path.read_bytes()
        except FakeExportError:
            raise
        except OSError:
            raise FakeExportError(code + "_READ") from None
        if not raw or len(raw) > limit:
            raise FakeExportError(code + "_SIZE")
        return raw

    def _write_exclusive(self, path, raw, limit, code):
        if type(raw) is not bytes or not raw or len(raw) > limit:
            raise FakeExportError(code + "_SIZE")
        descriptor = None
        try:
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_BINARY", 0)
            descriptor = os.open(str(path), flags, 0o600)
            with os.fdopen(descriptor, "wb", closefd=True) as stream:
                descriptor = None
                stream.write(raw)
                stream.flush()
                os.fsync(stream.fileno())
        except FileExistsError:
            raise FakeExportError(code + "_EXISTS") from None
        except OSError:
            raise FakeExportError(code + "_WRITE") from None
        finally:
            if descriptor is not None:
                try:
                    os.close(descriptor)
                except OSError:
                    pass


class FakePinnedBase:
    IO_HELPER_SHA256 = g.IO_HELPER_SHA256

    def __init__(self, io, helper_path):
        self.io = io
        self.helper_path = Path(helper_path)

    def load_io(self, path_value):
        path = Path(path_value)
        if path.is_symlink():
            raise FakeExportError("IO_HELPER_REPARSE")
        if path != self.helper_path:
            raise FakeExportError("IO_HELPER_PATH")
        return self.io


class OverlayTests(unittest.TestCase):
    def setUp(self):
        self.pin_patch = mock.patch.object(g, "STUDY_SHA256", SYNTHETIC_SHA256)
        self.pin_patch.start()
        self.addCleanup(self.pin_patch.stop)
        self.source = SYNTHETIC_SOURCE
        self.derived, self.changes = g.derive(self.source)

        self.temp = tempfile.TemporaryDirectory(prefix="tsj-adopted-snapshot-overlay-tests-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.study = self.root / "Study.java"
        self.base_path = self.root / "base.py"
        self.io_path = self.root / "io.py"
        self.output = self.root / "output"
        self.study.write_bytes(self.source)
        self.base_path.write_bytes(b"pinned base placeholder")
        self.io_path.write_bytes(b"pinned io placeholder")
        self.output.mkdir()
        self.io = FakePinnedIO()
        self.base = FakePinnedBase(self.io, self.io_path)
        self.options = {
            "--study": str(self.study),
            "--base-generator": str(self.base_path),
            "--io-helper": str(self.io_path),
            "--output-dir": str(self.output),
        }

    def assert_code(self, owner, code, call):
        with self.assertRaises(owner) as caught:
            call()
        self.assertIs(type(caught.exception), owner)
        self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), code)

    def generate_with_fake_selected_owners(self):
        with mock.patch.object(g, "load_base_generator", return_value=self.base):
            return g.generate(self.options)

    def test_synthetic_derivation_changes_exactly_the_two_pinned_regions(self):
        expected = self.source.replace(g.MAIN_BEFORE, g.MAIN_AFTER, 1)
        expected = expected.replace(g.SNAPSHOT_BEFORE, g.SNAPSHOT_AFTER, 1)
        self.assertEqual(g.derive(self.source), (expected, g.CHANGES))
        self.assertEqual(self.derived, expected)
        self.assertEqual(self.changes, (
            "DENY_ALL_MAIN_ENTRIES",
            "REQUIRE_ADOPTED_OWNER_FOR_CLOSED_SNAPSHOT",
        ))
        self.assertEqual(self.derived.count(g.MAIN_AFTER), 1)
        self.assertEqual(self.derived.count(g.SNAPSHOT_AFTER), 1)
        main = self.derived[self.derived.index(b"    public static void main("):
                             self.derived.index(b"static final class R5Snapshot")]
        self.assertIn(b'throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");', main)
        self.assertNotIn(b"System.nanoTime", main)
        self.assertNotIn(b"args.length", main)
        self.assertNotIn(b"args[", main)
        self.assertIn(b"constructorEntries>=0 && prior>=1", self.derived)
        self.assertIn(b"ownerStarted && ownerAdopted", self.derived)
        self.assertNotIn(g.SNAPSHOT_BEFORE, self.derived)
        self.assertNotIn(b"R7MeterPoint", self.derived)

    def test_source_types_size_hash_and_line_ending_guards(self):
        for bad in (None, "java", bytearray(self.source), b"",
                    b"x" * (g.MAX_SOURCE_BYTES + 1)):
            self.assert_code(g.OverlayError, "SOURCE_SIZE", lambda bad=bad: g.derive(bad))
        self.assert_code(g.OverlayError, "STUDY_HASH",
                         lambda: g.derive(self.source + b"x"))
        crlf = self.source.replace(b"\n", b"\r\n")
        with mock.patch.object(g, "STUDY_SHA256", g.sha(crlf)):
            self.assert_code(g.OverlayError, "STUDY_LINE_ENDINGS", lambda: g.derive(crlf))

    def test_exact_splice_anchors_reject_missing_and_duplicate_regions(self):
        self.assertEqual(g.replace_once(b"AoldZ", b"old", b"new"), b"AnewZ")
        for raw, before, after in (
            (b"no marker", b"old", b"new"),
            (b"old and old", b"old", b"new"),
        ):
            self.assert_code(g.OverlayError, "SPLICE_COUNT",
                             lambda raw=raw, before=before, after=after:
                                 g.replace_once(raw, before, after))
        self.assert_code(g.OverlayError, "SPLICE_TYPES",
                         lambda: g.replace_once("x", b"x", b"y"))
        duplicated_main = self.source + g.MAIN_BEFORE
        with mock.patch.object(g, "STUDY_SHA256", g.sha(duplicated_main)):
            self.assert_code(g.OverlayError, "SPLICE_COUNT",
                             lambda: g.derive(duplicated_main))
        duplicated_snapshot = self.source + g.SNAPSHOT_BEFORE
        with mock.patch.object(g, "STUDY_SHA256", g.sha(duplicated_snapshot)):
            self.assert_code(g.OverlayError, "SPLICE_COUNT",
                             lambda: g.derive(duplicated_snapshot))

    def test_generation_writes_exact_pair_and_sanitized_proof(self):
        proof = self.generate_with_fake_selected_owners()
        self.assertEqual({item.name for item in self.output.iterdir()},
                         {"CohortTerminalTelemetryStudy.java", "SOURCE_PROOF.json"})
        source_out = self.output / "CohortTerminalTelemetryStudy.java"
        proof_out = self.output / "SOURCE_PROOF.json"
        self.assertEqual(source_out.read_bytes(), self.derived)
        proof_raw = proof_out.read_bytes()
        self.assertEqual(json.loads(proof_raw), proof)
        self.assertEqual(proof["studyParentSha256"], SYNTHETIC_SHA256)
        self.assertEqual(proof["derivedSha256"], hashlib.sha256(self.derived).hexdigest())
        self.assertEqual(proof["changes"], list(g.CHANGES))
        self.assertEqual(proof["authoredInvocations"], 0)
        self.assertEqual(proof["budgets"], "UNCHANGED")
        for local in (str(self.root), str(self.study), str(self.output)):
            self.assertNotIn(local.encode("utf-8"), proof_raw)
        self.assertNotRegex(proof_raw.decode("ascii"), r"[A-Za-z]:[\\/]")
        self.assert_code(g.OverlayError, "OUTPUT_NOT_EMPTY",
                         self.generate_with_fake_selected_owners)

    def test_occupied_output_and_bad_source_are_rejected_before_mutation(self):
        occupied = self.output / "keep.bin"
        occupied.write_bytes(b"preserve")
        self.assert_code(g.OverlayError, "OUTPUT_NOT_EMPTY",
                         self.generate_with_fake_selected_owners)
        self.assertEqual(occupied.read_bytes(), b"preserve")
        occupied.unlink()
        self.study.write_bytes(self.source + b"\n")
        self.assert_code(g.OverlayError, "STUDY_HASH",
                         self.generate_with_fake_selected_owners)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_output_closure_and_readback_failures_preserve_evidence(self):
        original_write = self.io._write_exclusive

        def write_and_add(path, raw, limit, code):
            original_write(path, raw, limit, code)
            if path.name == "SOURCE_PROOF.json":
                (self.output / "unexpected.txt").write_bytes(b"preserve")

        with mock.patch.object(self.io, "_write_exclusive", side_effect=write_and_add):
            self.assert_code(g.OverlayError, "OUTPUT_CLOSURE",
                             self.generate_with_fake_selected_owners)
        self.assertEqual((self.output / "unexpected.txt").read_bytes(), b"preserve")

        for item in self.output.iterdir():
            item.unlink()
        original_write = self.io._write_exclusive

        def write_and_change(path, raw, limit, code):
            original_write(path, raw, limit, code)
            if path.name == "SOURCE_PROOF.json":
                (self.output / "CohortTerminalTelemetryStudy.java").write_bytes(b"changed")

        with mock.patch.object(self.io, "_write_exclusive", side_effect=write_and_change):
            self.assert_code(g.OverlayError, "OUTPUT_READBACK_CHANGED",
                             self.generate_with_fake_selected_owners)
        self.assertEqual((self.output / "CohortTerminalTelemetryStudy.java").read_bytes(),
                         b"changed")

    def make_symlink_or_skip(self, link, target, directory=False):
        try:
            os.symlink(target, link, target_is_directory=directory)
        except (OSError, NotImplementedError):
            self.skipTest("Host does not permit symlinks; selected CLI gate must cover junctions")

    def test_base_loader_path_hash_and_symlink_guards(self):
        self.assert_code(g.OverlayError, "BASE_GENERATOR_ABSOLUTE",
                         lambda: g.load_base_generator("relative.py"))
        for bad in (None, "", "a\0b"):
            self.assert_code(g.OverlayError, "BASE_GENERATOR_PATH",
                             lambda bad=bad: g.load_base_generator(bad))
        self.assert_code(g.OverlayError, "BASE_GENERATOR_HASH",
                         lambda: g.load_base_generator(str(self.base_path)))
        alias = str(self.root) + os.sep + "." + os.sep + self.base_path.name
        self.assert_code(g.OverlayError, "BASE_GENERATOR_CANONICAL",
                         lambda: g.load_base_generator(alias))
        link = self.root / "base-link.py"
        self.make_symlink_or_skip(link, self.base_path)
        self.assert_code(g.OverlayError, "BASE_GENERATOR_REPARSE",
                         lambda: g.load_base_generator(str(link)))

    def test_raw_option_aliases_reject_before_base_loading(self):
        for option, code in (("--study", "OWNER_SOURCE"),
                             ("--base-generator", "BASE_GENERATOR"),
                             ("--io-helper", "IO_HELPER"),
                             ("--output-dir", "OUTPUT_DIRECTORY")):
            original = Path(self.options[option])
            alias = str(original.parent) + os.sep + "." + os.sep + original.name
            with self.subTest(option=option), mock.patch.object(g, "load_base_generator") as loader:
                self.assert_code(g.OverlayError, code + "_CANONICAL",
                                 lambda option=option, alias=alias:
                                     g.generate({**self.options, option: alias}))
                loader.assert_not_called()
                self.assertEqual(list(self.output.iterdir()), [])

    def test_source_output_and_io_helper_symlink_guards(self):
        source_link = self.root / "study-link.java"
        self.make_symlink_or_skip(source_link, self.study)
        with mock.patch.object(g, "load_base_generator", return_value=self.base):
            self.assert_code(FakeExportError, "OWNER_SOURCE_REPARSE",
                             lambda: g.generate({**self.options, "--study": str(source_link)}))
        self.assertEqual(list(self.output.iterdir()), [])

        output_link = self.root / "output-link"
        self.make_symlink_or_skip(output_link, self.output, directory=True)
        with mock.patch.object(g, "load_base_generator", return_value=self.base):
            self.assert_code(FakeExportError, "OUTPUT_DIRECTORY_REPARSE",
                             lambda: g.generate({**self.options, "--output-dir": str(output_link)}))

        helper_link = self.root / "helper-link.py"
        self.make_symlink_or_skip(helper_link, self.io_path)
        with mock.patch.object(g, "load_base_generator", return_value=self.base):
            self.assert_code(FakeExportError, "IO_HELPER_REPARSE",
                             lambda: g.generate({**self.options, "--io-helper": str(helper_link)}))
        self.assertEqual(list(self.output.iterdir()), [])

    def test_cli_argument_and_option_validation(self):
        pairs = [value for pair in self.options.items() for value in pair]
        self.assertEqual(g.parse_args(pairs), self.options)
        self.assertEqual(g.parse_args(tuple(pairs)), self.options)
        for bad in (None, {}, pairs[:-1], pairs + ["extra", "value"],
                    ["--study", "a"] * 4, pairs[:1] + [""] + pairs[2:],
                    pairs[:1] + ["a\0b"] + pairs[2:],
                    pairs[:1] + ["--bad"] + pairs[2:]):
            self.assert_code(g.OverlayError, "CLI_ARGUMENTS",
                             lambda bad=bad: g.parse_args(bad))
        for bad in (None, {}, {**self.options, "--other": "value"},
                    {**self.options, "--study": None}):
            self.assert_code(g.OverlayError, "GENERATOR_OPTIONS",
                             lambda bad=bad: g.generate(bad))

    def test_base_and_io_pin_mismatch_rejected(self):
        wrong_base = self.root / "wrong-base.py"
        wrong_base.write_bytes(b"raise RuntimeError('MUST_NOT_LOAD')\n")
        self.assert_code(g.OverlayError, "BASE_GENERATOR_HASH",
                         lambda: g.load_base_generator(str(wrong_base)))
        bad_base = FakePinnedBase(self.io, self.io_path)
        bad_base.IO_HELPER_SHA256 = "0" * 64
        with mock.patch.object(g, "load_base_generator", return_value=bad_base):
            self.assert_code(g.OverlayError, "IO_HELPER_PIN",
                             lambda: g.generate(self.options))
        self.assertEqual(list(self.output.iterdir()), [])

    def test_cli_failure_codes_do_not_include_untrusted_paths(self):
        with mock.patch.object(g, "generate", side_effect=RuntimeError(str(self.root))), \
                mock.patch.object(g.sys, "stderr") as stderr, \
                mock.patch.object(g.sys, "stdout") as stdout:
            result = g.main([value for pair in self.options.items() for value in pair])
        self.assertEqual(result, 2)
        self.assertEqual(stderr.write.call_args[0][0],
                         "ADOPTED_SNAPSHOT_OVERLAY_FAIL INTERNAL_ERROR\n")
        stdout.write.assert_not_called()

    def test_optional_actual_pinned_sources(self):
        if OPTIONS is None:
            self.skipTest("optional actual-source gate paths not supplied")
        study = Path(OPTIONS["--study"]).read_bytes()
        base = Path(OPTIONS["--base-generator"]).read_bytes()
        io = Path(OPTIONS["--io-helper"]).read_bytes()
        self.assertEqual(hashlib.sha256(study).hexdigest(),
                         "7d62a0f343a270056bfd7e536145a47874ccc90c10cd24b93ec89ee6d6f94570")
        self.assertEqual(hashlib.sha256(base).hexdigest(), g.BASE_GENERATOR_SHA256)
        self.assertEqual(hashlib.sha256(io).hexdigest(), g.IO_HELPER_SHA256)
        with mock.patch.object(g, "STUDY_SHA256",
                               "7d62a0f343a270056bfd7e536145a47874ccc90c10cd24b93ec89ee6d6f94570"):
            derived, changes = g.derive(study)
        self.assertEqual(changes, g.CHANGES)
        self.assertEqual(derived.count(g.MAIN_AFTER), 1)
        self.assertEqual(derived.count(g.SNAPSHOT_AFTER), 1)


def parse_test_options(argv):
    keys = {"--study", "--base-generator", "--io-helper"}
    if not argv:
        return None
    if len(argv) != 6:
        raise ValueError("TEST_ARGUMENTS")
    result = {}
    for index in range(0, 6, 2):
        key, value = argv[index:index + 2]
        if (key not in keys or key in result or not value or value.startswith("--")
                or "\0" in value):
            raise ValueError("TEST_ARGUMENTS")
        result[key] = value
    if set(result) != keys:
        raise ValueError("TEST_ARGUMENTS")
    return result


if __name__ == "__main__":
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        raise SystemExit("TEST_PYTHON_ISOLATION_REQUIRED")
    OPTIONS = parse_test_options(sys.argv[1:])
    unittest.main(argv=[sys.argv[0]], verbosity=2)