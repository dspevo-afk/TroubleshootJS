"""Synthetic, local fixture tests for the caller-facts observer.

The reader source is supplied by the trusted test runner and pinned here to
the qualified reader SOURCE. Tests create only task-owned temporary files.
"""

from __future__ import annotations

import hashlib
import importlib.util
import os
from pathlib import Path
import shutil
import stat
import sys
import tempfile
import unittest
from contextlib import nullcontext
from functools import wraps
from types import SimpleNamespace
from unittest import mock


QUALIFIED_READER_SHA256 = (
    "863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8"
)
READER_PATH_ENV = "TSJ_FIXED_ROLE_READER_PATH"


def _load_source_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError("SOURCE_MODULE_SPEC")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    try:
        spec.loader.exec_module(module)
    except BaseException:
        sys.modules.pop(name, None)
        raise
    return module


_HERE = Path(__file__).resolve().parent
sut = _load_source_module("_tsj_observe_caller_facts_test", _HERE / "observe_caller_facts.py")


def _read_spec(path: Path, raw: bytes, *, maximum: int):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)
    return {
        "path": str(path),
        "expectedBytes": len(raw),
        "maxBytes": max(len(raw), maximum),
        "sha256": hashlib.sha256(raw).hexdigest(),
    }


def _advance_directory_mtime(path: Path, previous_mtime_ns: int):
    current = os.stat(path)
    target_mtime_ns = max(previous_mtime_ns, current.st_mtime_ns) + 1_000_000_000
    os.utime(path, ns=(current.st_atime_ns, target_mtime_ns))


class _OwnedTempDirectory:
    """Unique OS-temp tree removed only after identity and reparse checks."""

    def __init__(self, reader):
        self.reader = reader

    def __enter__(self):
        self.path = Path(tempfile.mkdtemp(prefix="tsj-caller-facts-test-"))
        try:
            self.path = self.path.resolve(strict=True)
            self.temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
            common = os.path.normcase(os.path.commonpath((
                str(self.temp_root), str(self.path))))
            if common != os.path.normcase(str(self.temp_root)):
                raise RuntimeError("temporary directory escaped OS temp")
            _path, real, identity = self.reader._inspect_path(str(self.path), "directory")
            if os.path.normcase(str(real)) != os.path.normcase(str(self.path)):
                raise RuntimeError("temporary directory path is not canonical")
            self.object_identity = tuple(identity[-1][1][index] for index in (0, 1, 2, 6))
        except BaseException:
            print("preserved synthetic fixture: " + str(self.path), file=sys.stderr)
            raise
        return self.path

    def __exit__(self, exc_type, exc, _traceback):
        if exc_type is not None and not issubclass(exc_type, unittest.SkipTest):
            print("preserved synthetic fixture: " + str(self.path), file=sys.stderr)
            return False

        try:
            path, real, identity = self.reader._inspect_path(str(self.path), "directory")
            current_identity = tuple(identity[-1][1][index] for index in (0, 1, 2, 6))
            if (os.path.normcase(str(path.resolve(strict=True)))
                    != os.path.normcase(str(self.path))
                    or os.path.normcase(str(real)) != os.path.normcase(str(self.path))
                    or current_identity != self.object_identity):
                raise RuntimeError("temporary directory identity changed")

            root_text = os.path.normcase(str(self.path))

            def fail_walk(error):
                raise RuntimeError("temporary tree scan failed") from None

            for current, directories, files in os.walk(
                    str(self.path), followlinks=False, onerror=fail_walk):
                for name in directories + files:
                    item_path = Path(current) / name
                    try:
                        item_text = os.path.abspath(str(item_path))
                        common = os.path.normcase(os.path.commonpath((root_text, item_text)))
                        item_stat = os.lstat(str(item_path))
                    except (OSError, ValueError):
                        raise RuntimeError("temporary tree changed before cleanup") from None
                    is_directory = name in directories
                    valid_kind = (stat.S_ISDIR(item_stat.st_mode) if is_directory
                                  else stat.S_ISREG(item_stat.st_mode))
                    if (common != root_text or not valid_kind or
                            stat.S_ISLNK(item_stat.st_mode) or
                            bool(getattr(item_stat, "st_file_attributes", 0)
                                 & self.reader.REPARSE_POINT)):
                        raise RuntimeError("temporary tree contains an unsafe path")
            shutil.rmtree(str(self.path))
        except BaseException:
            print("preserved synthetic fixture: " + str(self.path), file=sys.stderr)
            raise
        return False


class CallerFactsFixture:
    def __init__(self, reader):
        self.reader = reader
        self.owned_temp = _OwnedTempDirectory(reader)

    def __enter__(self):
        self.base = self.owned_temp.__enter__()
        try:
            self._populate()
        except BaseException as exc:
            self.owned_temp.__exit__(type(exc), exc, exc.__traceback__)
            raise
        return self

    def __exit__(self, exc_type, exc, traceback):
        return self.owned_temp.__exit__(exc_type, exc, traceback)

    def _populate(self):
        self.root = self.base / "sandbox"
        self.root.mkdir()
        self.directory_specs = {}
        self.absence_specs = {}

        call_names = (
            "owner.json", "parent.json", "process.json", "request.json",
            "result.json", "stderr.txt", "stdout.txt",
        )
        for name in ("qualification", "nativePrior"):
            directory = self.root / "resources" / name
            directory.mkdir(parents=True)
            for child_name in call_names:
                (directory / child_name).write_bytes(b"synthetic fixture\n")
            self.directory_specs[name] = {
                "path": str(directory),
                "containmentRoot": str(self.root),
            }

            scratch = self.root / "scratch" / name
            scratch.mkdir(parents=True)
            self.absence_specs[name] = {
                "processRoot": {
                    "path": str(scratch / "process"),
                    "containmentRoot": str(self.root),
                },
                "stderrLog": {
                    "path": str(scratch / "logs" / "stderr.txt"),
                    "containmentRoot": str(self.root),
                },
                "stdoutLog": {
                    "path": str(scratch / "logs" / "stdout.txt"),
                    "containmentRoot": str(self.root),
                },
            }

        data_directory = self.root / "resources" / "nativeData"
        data_directory.mkdir(parents=True)
        (data_directory / "op-1.jsonl").write_bytes(b"synthetic operation fixture\n")
        (data_directory / "result.json").write_bytes(b"synthetic result fixture\n")
        self.directory_specs["nativeData"] = {
            "path": str(data_directory),
            "containmentRoot": str(self.root),
        }

        tool_directory = self.root / "tools"
        tool_directory.mkdir()
        module_path = tool_directory / "process_module.py"
        module_bytes = b"synthetic selected process module\n"
        module_path.write_bytes(module_bytes)
        harness_bytes = b"PROCESS_MODULE = r\"" + str(module_path).encode("utf-8") + b"\"\n"
        tool_bodies = {
            "qualificationPython": b"synthetic Python tool\n",
            "rootCallPowerShell": b"synthetic PowerShell tool\n",
            "nativeJava": b"synthetic Java tool\n",
            "rootCallHarness": harness_bytes,
        }
        self.tool_specs = {
            name: _read_spec(tool_directory / (name + ".bin"), raw, maximum=1024)
            for name, raw in tool_bodies.items()
        }
        self.tool_specs["processModule"] = _read_spec(
            module_path, module_bytes, maximum=1024)

        source_directory = self.root / "source"
        source_bytes = b"synthetic current SOURCE fixture\n"
        self.source_specs = {
            "normalizerSource": _read_spec(
                source_directory / "normalize_prior.py", source_bytes, maximum=4096),
        }

    def observe(self):
        return sut.observe_caller_facts(
            reader=self.reader,
            tool_specs=self.tool_specs,
            source_specs=self.source_specs,
            directory_specs=self.directory_specs,
            absence_specs=self.absence_specs,
        )

    def observe_bytes(self):
        return sut.observe_byte_facts(
            reader=self.reader, tool_specs=self.tool_specs,
            source_specs=self.source_specs)

    def observe_resources(self):
        return sut.observe_resource_facts(
            reader=self.reader, directory_specs=self.directory_specs,
            absence_specs=self.absence_specs)


def _with_fixture(method):
    @wraps(method)
    def run(self):
        with CallerFactsFixture(self.reader) as fixture:
            return method(self, fixture)
    return run


class ObserveCallerFactsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        reader_path_text = os.environ.get(READER_PATH_ENV)
        if not reader_path_text:
            raise RuntimeError("QUALIFIED_READER_PATH_REQUIRED")
        reader_path = Path(reader_path_text)
        raw = reader_path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != QUALIFIED_READER_SHA256:
            raise RuntimeError("QUALIFIED_READER_SOURCE_SHA256")
        cls.reader = _load_source_module("_tsj_qualified_reader_fixture_test", reader_path)

    def assert_observer_code(self, expected_code, function):
        with self.assertRaises(sut.CallerObservationError) as raised:
            function()
        self.assertEqual(expected_code, raised.exception.code)

    @_with_fixture
    def test_positive_returns_actual_byte_facts_and_exact_closures(self, fixture):
        result = fixture.observe()
        self.assertEqual(
            ("qualificationPython", "rootCallPowerShell", "nativeJava",
             "rootCallHarness", "processModule"),
            tuple(result["toolFacts"].keys()),
        )
        self.assertEqual(("normalizerSource",), tuple(result["sourceFacts"].keys()))
        self.assertEqual(("qualification", "nativePrior", "nativeData"),
                         tuple(result["resourceFacts"].keys()))
        self.assertEqual(64, len(result["observationEvidence"]["evidenceSha256"]))
        self.assertEqual(
            ["owner.json", "parent.json", "process.json", "request.json",
             "result.json", "stderr.txt", "stdout.txt"],
            result["resourceFacts"]["qualification"]["directoryNames"],
        )
        self.assertEqual(["op-1.jsonl", "result.json"],
                         result["resourceFacts"]["nativeData"]["directoryNames"])
        self.assertTrue(result["resourceFacts"]["nativePrior"]["processRoot"]["absent"])
        self.assertNotIn("content", result["toolFacts"]["rootCallHarness"])
        self.assertEqual("tsj-caller-observation-evidence-v2",
                         result["observationEvidence"]["schema"])
        self.assertEqual("tsj-caller-resource-observation-v2",
                         result["observationEvidence"]["resources"]["qualification"]
                         ["record"]["schema"])
        self.assertEqual("tsj-fixed-role-reader-path-identity-v1",
                         result["observationEvidence"]["resources"]["qualification"]
                         ["record"]["identityProjection"])

    @_with_fixture
    def test_real_ancestor_clock_change_is_projected_but_leaf_is_exact(self, fixture):
        source_path = Path(fixture.source_specs["normalizerSource"]["path"])
        before = self.reader._inspect_path(str(source_path), "file")[2]
        parent_mtime_ns = before[-2][1][4]
        sibling = source_path.parent / "clock-churn-sibling.txt"
        sibling.write_bytes(b"synthetic sibling\n")
        _advance_directory_mtime(source_path.parent, parent_mtime_ns)
        after = self.reader._inspect_path(str(source_path), "file")[2]

        self.assertGreater(after[-2][1][4], before[-2][1][4])
        self.assertEqual(before[-1][1], after[-1][1])
        self.assertEqual(self.reader._path_identity(before),
                         self.reader._path_identity(after))

    @_with_fixture
    def test_byte_recheck_accepts_real_sibling_clock_churn(self, fixture):
        target = os.path.normcase(os.path.abspath(
            fixture.source_specs["normalizerSource"]["path"]))
        original = self.reader._read_file_once
        tokens = []
        raw_values = []
        changed = {"value": False}

        def add_sibling_after_read(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if os.path.normcase(os.path.abspath(str(path))) == target:
                raw_values.append(result[0])
                tokens.append(result[2])
                if not changed["value"]:
                    Path(path).parent.joinpath("read-recheck-sibling.txt").write_bytes(
                        b"synthetic sibling\n")
                    _advance_directory_mtime(
                        Path(path).parent, result[2][1][-2][1][4])
                    changed["value"] = True
            return result

        with mock.patch.object(self.reader, "_read_file_once",
                               side_effect=add_sibling_after_read):
            result = fixture.observe_bytes()

        self.assertTrue(changed["value"])
        self.assertEqual(2, len(tokens))
        self.assertEqual(raw_values[0], raw_values[1])
        self.assertGreater(tokens[1][1][-2][1][4], tokens[0][1][-2][1][4])
        self.assertEqual(tokens[0][1][-1][1], tokens[1][1][-1][1])
        self.assertEqual(self.reader._token_identity(tokens[0]),
                         self.reader._token_identity(tokens[1]))
        self.assertEqual(len(raw_values[0]), result["sourceFacts"]["normalizerSource"]
                         ["bytes"])

    @_with_fixture
    def test_resource_evidence_ignores_real_shared_ancestor_clock_churn(self, fixture):
        baseline_facts, baseline_evidence = fixture.observe_resources()
        root_before = self.reader._inspect_path(str(fixture.root), "directory")[2]
        sibling = fixture.base / "shared-ancestor-sibling.txt"
        original_scan = sut._scan_names
        changed = {"value": False}

        def churn_after_first_scan(path):
            names = original_scan(path)
            if not changed["value"]:
                sibling.write_bytes(b"synthetic sibling\n")
                _advance_directory_mtime(fixture.base, root_before[-2][1][4])
                changed["value"] = True
            return names

        with mock.patch.object(sut, "_scan_names", side_effect=churn_after_first_scan):
            current_facts, current_evidence = fixture.observe_resources()

        self.assertTrue(changed["value"])
        root_after = self.reader._inspect_path(str(fixture.root), "directory")[2]
        self.assertGreater(root_after[-2][1][4], root_before[-2][1][4])
        self.assertEqual(root_before[-1][1], root_after[-1][1])
        self.assertEqual(self.reader._path_identity(root_before),
                         self.reader._path_identity(root_after))
        self.assertEqual(baseline_facts, current_facts)
        self.assertEqual(baseline_evidence, current_evidence)

    @_with_fixture
    def test_source_parent_identity_replacement_rejects_even_with_same_leaf(self, fixture):
        source_path = Path(fixture.source_specs["normalizerSource"]["path"])
        original = self.reader._read_file_once
        tokens = []
        changed = {"value": False}
        parked_parent = fixture.base / "parked-source-parent"

        def replace_parent_after_first_read(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if (os.path.normcase(os.path.abspath(str(path)))
                    == os.path.normcase(os.path.abspath(str(source_path)))):
                tokens.append(result[2])
                if not changed["value"]:
                    os.replace(source_path.parent, parked_parent)
                    source_path.parent.mkdir()
                    os.replace(parked_parent / source_path.name, source_path)
                    changed["value"] = True
            return result

        with mock.patch.object(self.reader, "_read_file_once",
                               side_effect=replace_parent_after_first_read):
            self.assert_observer_code("SOURCE_CHANGED", fixture.observe_bytes)

        self.assertTrue(changed["value"])
        self.assertEqual(2, len(tokens))
        before = self.reader._token_identity(tokens[0])
        after = self.reader._token_identity(tokens[1])
        self.assertEqual(before[1][-1][1], after[1][-1][1])
        self.assertNotEqual(before[1][-2], after[1][-2])
        self.assertEqual(tokens[0][0], tokens[1][0])

    @_with_fixture
    def test_source_leaf_metadata_change_rejects(self, fixture):
        source_path = Path(fixture.source_specs["normalizerSource"]["path"])
        original = self.reader._read_file_once
        changed = {"value": False}

        def change_leaf_metadata(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if (not changed["value"] and
                    os.path.normcase(os.path.abspath(str(path)))
                    == os.path.normcase(os.path.abspath(str(source_path)))):
                before_stat = os.stat(path)
                os.utime(path, (before_stat.st_atime, before_stat.st_mtime + 5))
                changed["value"] = True
            return result

        with mock.patch.object(self.reader, "_read_file_once",
                               side_effect=change_leaf_metadata):
            self.assert_observer_code("SOURCE_CHANGED", fixture.observe_bytes)
        self.assertTrue(changed["value"])

    @_with_fixture
    def test_source_content_change_rejects(self, fixture):
        source_path = Path(fixture.source_specs["normalizerSource"]["path"])
        original = self.reader._read_file_once
        changed = {"value": False}

        def change_leaf_content(path, *args, **kwargs):
            result = original(path, *args, **kwargs)
            if (not changed["value"] and
                    os.path.normcase(os.path.abspath(str(path)))
                    == os.path.normcase(os.path.abspath(str(source_path)))):
                source_path.write_bytes(b"synthetic current SOURCE fixTure\n")
                changed["value"] = True
            return result

        with mock.patch.object(self.reader, "_read_file_once",
                               side_effect=change_leaf_content):
            self.assert_observer_code("SOURCE_READ_SHA256", fixture.observe_bytes)
        self.assertTrue(changed["value"])

    @_with_fixture
    def test_source_parent_reparse_is_rejected(self, fixture):
        source_path = Path(fixture.source_specs["normalizerSource"]["path"])
        source_dir = source_path.parent
        target_dir = fixture.base / "source-reparse-target"
        os.replace(source_dir, target_dir)
        try:
            source_dir.symlink_to(target_dir, target_is_directory=True)
        except OSError:
            os.replace(target_dir, source_dir)
            self.skipTest("directory symlink creation is unavailable")
        try:
            self.assert_observer_code("SOURCE_READ_REPARSE", fixture.observe_bytes)
        finally:
            source_dir.unlink()
            os.replace(target_dir, source_dir)

    @_with_fixture
    def test_extra_call_entry_rejects_closure(self, fixture):
        path = Path(fixture.directory_specs["qualification"]["path"])
        (path / "extra.txt").write_bytes(b"extra synthetic entry\n")
        self.assert_observer_code("qualification_DIRECTORY_CLOSURE", fixture.observe)

    @_with_fixture
    def test_extra_subdirectory_rejects_closure(self, fixture):
        path = Path(fixture.directory_specs["nativeData"]["path"])
        (path / "nested").mkdir()
        self.assert_observer_code("nativeData_DIRECTORY_CLOSURE", fixture.observe)

    @_with_fixture
    def test_scandir_order_does_not_change_exact_closure(self, fixture):
        real_scandir = os.scandir
        counts = {}
        target = os.path.normcase(os.path.abspath(
            fixture.directory_specs["qualification"]["path"]))

        def changing_listing(path):
            with real_scandir(path) as entries:
                names = [entry.name for entry in entries]
            key = os.path.normcase(os.path.abspath(os.fspath(path)))
            counts[key] = counts.get(key, 0) + 1
            if counts[key] % 2 == 0:
                names.reverse()
            return nullcontext([SimpleNamespace(name=name) for name in names])

        with mock.patch.object(sut.os, "scandir", side_effect=changing_listing):
            result = fixture.observe()
        self.assertEqual(2, counts[target])
        self.assertEqual(
            ["owner.json", "parent.json", "process.json", "request.json",
             "result.json", "stderr.txt", "stdout.txt"],
            result["resourceFacts"]["qualification"]["directoryNames"],
        )

    @_with_fixture
    def test_directory_outside_explicit_root_rejects(self, fixture):
        outside = fixture.base / "outside"
        outside.mkdir()
        (outside / "owner.json").write_bytes(b"fixture\n")
        spec = fixture.directory_specs["qualification"]
        spec["path"] = str(outside)
        self.assert_observer_code("qualification_DIRECTORY_CONTAINMENT", fixture.observe)

    @_with_fixture
    def test_harness_must_embed_selected_module_path(self, fixture):
        spec = fixture.tool_specs["rootCallHarness"]
        harness_path = Path(spec["path"])
        raw = b"# selected module omitted\n"
        harness_path.write_bytes(raw)
        spec["expectedBytes"] = len(raw)
        spec["maxBytes"] = max(len(raw), spec["maxBytes"])
        spec["sha256"] = hashlib.sha256(raw).hexdigest()
        self.assert_observer_code("HARNESS_PROCESS_MODULE_LINK", fixture.observe)

    @_with_fixture
    def test_wrong_external_tool_pin_rejects(self, fixture):
        fixture.tool_specs["nativeJava"]["sha256"] = "0" * 64
        self.assert_observer_code("TOOL_READ_SHA256", fixture.observe)

    @_with_fixture
    def test_source_limit_is_bounded_to_four_mib(self, fixture):
        fixture.source_specs["normalizerSource"]["maxBytes"] = 4 * 1024 * 1024 + 1
        self.assert_observer_code("SOURCE_SPEC_LIMIT", fixture.observe)

    @_with_fixture
    def test_existing_process_root_is_not_reported_absent(self, fixture):
        target = Path(fixture.absence_specs["qualification"]["processRoot"]["path"])
        target.mkdir()
        self.assert_observer_code("ABSENCE_TARGET_PRESENT", fixture.observe)

    @_with_fixture
    def test_missing_ancestor_absence_is_rechecked(self, fixture):
        result = fixture.observe()
        fact = result["resourceFacts"]["qualification"]["stderrLog"]
        self.assertTrue(fact["absent"])
        evidence = result["observationEvidence"]["resources"]["qualification"]["record"]
        absence = evidence["absences"]["stderrLog"]
        self.assertEqual("logs", Path(absence["missingComponent"]).name)

    @_with_fixture
    def test_absence_outside_explicit_root_rejects(self, fixture):
        outside = fixture.base / "outside" / "missing" / "process"
        spec = fixture.absence_specs["qualification"]["processRoot"]
        spec["path"] = str(outside)
        self.assert_observer_code(
            "QUALIFICATION_PROCESSROOT_CONTAINMENT", fixture.observe)

    @_with_fixture
    def test_reader_must_be_an_explicit_module(self, fixture):
        self.assert_observer_code(
            "READER_MODULE_TYPE",
            lambda: sut.observe_byte_facts(
                reader=object(), tool_specs=fixture.tool_specs,
                source_specs=fixture.source_specs),
        )


if __name__ == "__main__":
    unittest.main()
