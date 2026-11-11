"""Selected-Windows CODE/NO_DATA I/O canaries, never a normal-input qualifier.

The root controller freezes and authenticates this driver and the bootstrap
loader before launch. The existing loader authenticates reader/caller Source;
its real reader then authenticates the stager and explicit NO_DATA fixture.
TSJ_STAGING_CONFIG identifies only those finite Source files and owned scratch.
"""
import ast
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import time
import types
import unittest


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def load_lab():
    assert sys.platform == "win32" and os.name == "nt"
    assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
    cfg = json.loads(Path(os.environ["TSJ_STAGING_CONFIG"]).read_bytes())
    row = cfg["loader"]
    raw = Path(row["path"]).read_bytes()
    assert digest(raw) == row["sha256"] and len(raw) == row["bytes"]
    loader = types.ModuleType("_stage_lab_bootstrap")
    loader.__file__ = row["path"]
    exec(compile(raw, row["path"], "exec", dont_inherit=True), loader.__dict__)
    args = types.SimpleNamespace(reader=cfg["reader"]["path"],
        reader_sha=cfg["reader"]["sha256"], caller=cfg["caller"]["path"],
        caller_sha=cfg["caller"]["sha256"])
    caller, reader, _, _ = loader._load_modules(args)
    row = cfg["stager"]
    raw, sha, _ = reader._read_file_once(row["path"], 4194304,
        row["bytes"], row["sha256"], "STAGER_CODE")
    assert sha == row["sha256"]
    module = loader._exec_authenticated_module("_current_staging_lab",
                                              row["path"], raw)
    row = cfg["noData"]
    fixture, _, _ = reader._read_file_once(row["path"], 1048576,
        row["bytes"], row["sha256"], "EXPLICIT_NO_DATA_CODE")
    assert b'NO_DATA_FIXTURE_STOP' in fixture
    base = Path(cfg["scratch"])
    assert not base.exists()
    base.mkdir()
    return cfg, loader, caller, reader, module, fixture, base, raw


class StageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        (cls.cfg, cls.loader, cls.caller, cls.reader, cls.module,
         cls.fixture, cls.base, cls.module_raw) = load_lab()
        cls.cleanups = []

    @classmethod
    def tearDownClass(cls):
        (cls.base / "FIXTURE_DISPOSITION.json").write_text(json.dumps({
            "disposition": "ORDINARY_CODE_NO_DATA_FILES_RETAINED",
            "junctionCleanups": cls.cleanups,
            "externalNamespaceRaceProof": False}), encoding="ascii")

    def setUp(self):
        self.root = self.base / self._testMethodName
        self.root.mkdir()
        self.n = self.root / "CODE-N"
        self.d = self.root / "CODE-D"
        self.scratch = self.root / "scratch"
        self.source_dir = self.scratch / "sealed-upstream"
        self.dest_dir = self.scratch / "native-stage"
        for path in (self.n, self.d, self.scratch, self.source_dir, self.dest_dir):
            path.mkdir()
        self.source = self.source_dir / "current-input.java"
        self.source.write_bytes(self.fixture)
        self.dest = self.dest_dir / "CohortTerminalTelemetryInputV2.java"
        self.refresh()

    def refresh(self):
        roots = {"N": str(self.n), "D": str(self.d)}
        self.sc = {"roots": roots, "scratchRoot": str(self.scratch),
                   "dataOutputRoot": str(self.source_dir)}
        self.dc = {"roots": roots, "scratchRoot": str(self.scratch),
                   "dataOutputRoot": str(self.dest_dir)}
        self.ss = self.bind(self.sc)
        self.ds = self.bind(self.dc)
        raw = self.source.read_bytes()
        _, _, self.token = self.reader._read_file_once(str(self.source),
            1048576, len(raw), digest(raw), "OWNED_NO_DATA_EXPECTATION")
        self.kw = dict(reader=self.reader, io_owner=self.caller,
            source_config=self.sc, source_roots_sha256=self.ss,
            destination_config=self.dc, destination_roots_sha256=self.ds,
            expected_bytes=len(raw), expected_sha256=digest(raw),
            expected_source_token=self.token)

    def bind(self, config):
        return self.caller._root_bindings(self.reader, config["roots"],
            config["scratchRoot"], config["dataOutputRoot"])[1]

    def run_stage(self, **changes):
        return self.module.stage_producer_source(**dict(self.kw, **changes))

    def reject(self, code, **changes):
        with self.assertRaises((self.module.StagingError,
                                self.caller.CallerError)) as cm:
            self.run_stage(**changes)
        self.assertEqual(code, cm.exception.code)
        return cm.exception

    def empty(self):
        self.assertEqual([], list(self.dest_dir.iterdir()))

    def profile_after_write(self, mutate):
        """Owned syscall-state injection; not an external racing-process proof."""
        def profile(frame, event, arg):
            if (event == "return" and frame.f_code.co_name == "_write_exclusive"
                    and frame.f_globals is self.caller.__dict__):
                sys.setprofile(None)
                mutate()
        sys.setprofile(profile)
        self.addCleanup(sys.setprofile, None)

    def test_00_exact_no_data_copy(self):
        before = self.source.stat()
        facts = self.run_stage()
        self.assertEqual(self.fixture, self.dest.read_bytes())
        self.assertEqual(self.fixture, self.source.read_bytes())
        self.assertEqual({self.source.name}, {p.name for p in self.source_dir.iterdir()})
        self.assertEqual({self.dest.name}, {p.name for p in self.dest_dir.iterdir()})
        self.assertEqual(before.st_ino, self.source.stat().st_ino)
        self.assertNotEqual(before.st_ino, self.dest.stat().st_ino)
        self.assertEqual(len(self.fixture), facts["bytes"])
        self.assertEqual(digest(self.fixture), facts["sha256"])
        self.assertNotIn("status", facts)
        self.assertNotIn("normalHostAcceptance", facts)

    def test_01_wrong_digest_before_write(self):
        self.reject("READER_SHA256", expected_sha256="0" * 64)
        self.empty()

    def test_wrong_length_before_write(self):
        self.reject("READER_EXPECTED_BYTES", expected_bytes=len(self.fixture) + 1)
        self.empty()

    def test_existing_target_keeps_sentinel(self):
        self.dest.write_bytes(b"OWNED_SENTINEL_DO_NOT_OVERWRITE\n")
        before = self.dest.stat()
        self.reject("EXCLUSIVE_OUTPUT_CREATE")
        self.assertEqual(b"OWNED_SENTINEL_DO_NOT_OVERWRITE\n", self.dest.read_bytes())
        self.assertEqual(before.st_ino, self.dest.stat().st_ino)

    def test_existing_target_directory_rejects(self):
        self.dest.mkdir()
        self.reject("EXCLUSIVE_OUTPUT_CREATE")
        self.assertTrue(self.dest.is_dir())
        self.assertEqual([], list(self.dest.iterdir()))

    def test_stale_source_identity_same_bytes(self):
        self.source.rename(self.source.with_suffix(".preserved"))
        self.source.write_bytes(self.fixture)
        self.reject("STAGING_SOURCE_IDENTITY")
        self.empty()

    def test_wrong_source_token_same_bytes(self):
        other = self.source_dir / "other.java"
        other.write_bytes(self.fixture)
        _, _, token = self.reader._read_file_once(str(other), 1048576,
            len(self.fixture), digest(self.fixture), "OWNED_CODE_OTHER")
        self.reject("STAGING_SOURCE_IDENTITY", expected_source_token=token)
        self.empty()

    def test_replaced_output_directory_before_call(self):
        self.dest_dir.rename(self.dest_dir.with_name("native-stage-preserved"))
        self.dest_dir.mkdir()
        self.reject("ROOTS_CHANGED")
        self.empty()

    def test_destination_outside_scratch(self):
        outside = self.root / "outside"
        outside.mkdir()
        dc = dict(self.dc, dataOutputRoot=str(outside))
        self.reject("DATA_OUTPUT_CONTAINMENT", destination_config=dc,
                    destination_roots_sha256=self.bind(dc))
        self.assertEqual([], list(outside.iterdir()))

    def test_same_endpoint_rejects(self):
        self.reject("STAGING_ENDPOINT_OVERLAP", destination_config=self.sc,
                    destination_roots_sha256=self.ss)
        self.assertFalse((self.source_dir / self.dest.name).exists())

    def test_nested_endpoint_rejects(self):
        child = self.source_dir / "child"
        child.mkdir()
        dc = dict(self.dc, dataOutputRoot=str(child))
        self.reject("STAGING_ENDPOINT_OVERLAP", destination_config=dc,
                    destination_roots_sha256=self.bind(dc))
        self.assertEqual([], list(child.iterdir()))

    def test_output_in_reference_root_rejects(self):
        dc = dict(self.dc, roots={"N": str(self.scratch), "D": str(self.d)})
        sc = dict(self.sc, roots=dc["roots"])
        self.reject("STAGING_REFERENCE_OVERLAP", source_config=sc,
                    source_roots_sha256=self.bind(sc), destination_config=dc,
                    destination_roots_sha256=self.bind(dc))
        self.empty()

    def test_different_reference_roots_rejects(self):
        extra = self.root / "other-CODE-N"
        extra.mkdir()
        dc = dict(self.dc, roots={"N": str(extra), "D": str(self.d)})
        self.reject("STAGING_REFERENCE_IDENTITY", destination_config=dc,
                    destination_roots_sha256=self.bind(dc))
        self.empty()

    def test_maximum_source_exact_bytes(self):
        raw = self.fixture + b" " * (1048576 - len(self.fixture))
        self.source.write_bytes(raw)
        self.refresh()
        facts = self.run_stage()
        self.assertEqual(1048576, facts["bytes"])
        self.assertEqual(raw, self.dest.read_bytes())

    def test_invalid_expectations_before_content(self):
        for value in (True, False, 0, -1, 1048577, "652", None):
            with self.subTest(value=value):
                self.reject("STAGING_EXPECTED_BYTES", expected_bytes=value)
        for key in ("expected_sha256", "source_roots_sha256", "destination_roots_sha256"):
            for value in (None, "", "A" * 64, "0" * 63, "z" * 64):
                with self.subTest(key=key, value=value):
                    self.reject("STAGING_SHA256", **{key: value})
        self.empty()

    def test_over_limit_file_rejected_before_write(self):
        self.source.write_bytes(self.fixture + b" " * (1048577 - len(self.fixture)))
        self.reject("READER_SIZE", expected_bytes=1048576)
        self.empty()

    def test_partial_write_failure_retains_owned_file(self):
        original_os = self.caller.os
        class WriteFailure:
            def __getattr__(self, name):
                return getattr(original_os, name)

            def write(self, fd, raw):
                original_os.write(fd, raw[:17])
                raise OSError("OWNED_WRITE_FAILURE_INJECTION")
        self.caller.os = WriteFailure()
        try:
            self.reject("OUTPUT_WRITE")
        finally:
            self.caller.os = original_os
        self.assertEqual(self.fixture[:17], self.dest.read_bytes())
        self.assertEqual(self.fixture, self.source.read_bytes())
        self.reject("EXCLUSIVE_OUTPUT_CREATE")
        self.assertEqual(self.fixture[:17], self.dest.read_bytes())

    def test_source_change_after_write_retains_failure(self):
        self.profile_after_write(lambda: self.source.write_bytes(
            bytes([self.fixture[0] ^ 1]) + self.fixture[1:]))
        self.reject("READER_SHA256")
        self.assertEqual(self.fixture, self.dest.read_bytes())
        self.assertNotEqual(self.fixture, self.source.read_bytes())

    def test_target_same_bytes_replacement_after_write_rejects(self):
        def replace():
            self.dest.rename(self.dest.with_suffix(".preserved"))
            self.dest.write_bytes(self.fixture)
        self.profile_after_write(replace)
        self.reject("STAGING_DESTINATION_IDENTITY_CHANGED_AFTER_WRITES")
        self.assertEqual(self.fixture, self.dest.read_bytes())

    def test_source_same_bytes_replacement_after_write_rejects(self):
        def replace():
            self.source.rename(self.source.with_suffix(".preserved"))
            self.source.write_bytes(self.fixture)
        self.profile_after_write(replace)
        self.reject("STAGING_SOURCE_IDENTITY_CHANGED_AFTER_WRITES")
        self.assertEqual(self.fixture, self.dest.read_bytes())

    def test_destination_root_replaced_after_write_rejects(self):
        def replace():
            self.dest_dir.rename(self.dest_dir.with_name("native-stage-preserved"))
            self.dest_dir.mkdir()
        self.profile_after_write(replace)
        self.reject("ROOTS_CHANGED")
        self.empty()

    def test_real_junction_destination_rejected(self):
        import _winapi
        alias = self.scratch / "owned-junction"
        _winapi.CreateJunction(str(self.dest_dir), str(alias))
        try:
            snap = os.lstat(alias)
            self.assertTrue(snap.st_file_attributes & 0x400)
            self.assertEqual(0xA0000003, snap.st_reparse_tag)
            self.assertEqual(os.path.normcase(str(self.dest_dir.resolve())),
                os.path.normcase(str(alias.resolve())))
            dc = dict(self.dc, dataOutputRoot=str(alias))
            self.reject("READER_REPARSE", destination_config=dc)
            self.empty()
        finally:
            # Node-only cleanup after exact task ownership, tag and target proof.
            snap = os.lstat(alias)
            assert snap.st_reparse_tag == 0xA0000003
            assert alias.resolve() == self.dest_dir.resolve()
            started = time.monotonic_ns()
            os.rmdir(alias)
            elapsed = time.monotonic_ns() - started
            self.cleanups.append(dict(kind="JUNCTION_NODE_ONLY", tag="A0000003",
                exactOwnedTargetRevalidated=True, cleanupNanos=elapsed,
                nodeAbsent=not alias.exists()))
        self.assertFalse(alias.exists())

    def test_real_hardlink_existing_target_keeps_source(self):
        os.link(self.source, self.dest)
        self.assertEqual(self.source.stat().st_ino, self.dest.stat().st_ino)
        self.reject("EXCLUSIVE_OUTPUT_CREATE")
        self.assertEqual(self.fixture, self.source.read_bytes())
        self.assertEqual(self.fixture, self.dest.read_bytes())

    def test_import_has_only_definition_and_literal_statements(self):
        tree = ast.parse(self.module_raw)
        allowed = (ast.Import, ast.ImportFrom, ast.FunctionDef, ast.ClassDef)
        pending = list(tree.body)
        while pending:
            node = pending.pop()
            if isinstance(node, allowed):
                if isinstance(node, (ast.FunctionDef, ast.ClassDef)):
                    self.assertFalse(node.decorator_list)
                if isinstance(node, ast.FunctionDef):
                    self.assertIsNone(node.returns)
                    for arg in (node.args.posonlyargs + node.args.args +
                                node.args.kwonlyargs):
                        self.assertIsNone(arg.annotation)
                    for arg in (node.args.vararg, node.args.kwarg):
                        if arg:
                            self.assertIsNone(arg.annotation)
                    for default in node.args.defaults + node.args.kw_defaults:
                        if default:
                            ast.literal_eval(default)
                if isinstance(node, ast.ClassDef):
                    self.assertFalse(node.keywords)
                    self.assertEqual(["ValueError"],
                                     [b.id for b in node.bases if isinstance(b, ast.Name)])
                    self.assertEqual(1, len(node.bases))
                    pending.extend(node.body)
                continue
            if isinstance(node, ast.Expr):
                self.assertIsInstance(node.value, ast.Constant)
                self.assertIsInstance(node.value.value, str)
            else:
                self.assertIsInstance(node, ast.Assign)
                ast.literal_eval(node.value)


if __name__ == "__main__":
    unittest.main(verbosity=2)
