"""Synthetic-only caller boundary tests; never open historical role bodies."""

from __future__ import annotations

import importlib.util
import hashlib
import os
import ntpath
import shutil
import stat
import sys
import tempfile
import types
import unittest
from pathlib import Path


_CALLER_PATH = Path(__file__).with_name("qualify_prior.py")
_SPEC = importlib.util.spec_from_file_location(
    "_tsj_test_qualify_prior_source", str(_CALLER_PATH))
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("CALLER_SOURCE_LOADER")
_CALLER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _CALLER
_SPEC.loader.exec_module(_CALLER)


_READER_SOURCE_SHA256 = "863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8"
_READER_SOURCE_PATH = os.environ.get("TSJ_FIXED_ROLE_READER_PATH")
if type(_READER_SOURCE_PATH) is not str or not _READER_SOURCE_PATH:
    raise RuntimeError("TSJ_FIXED_ROLE_READER_PATH_REQUIRED")
_READER_SOURCE_RAW = _CALLER._bootstrap_read(
    _READER_SOURCE_PATH, _CALLER.MAX_SOURCE_BYTES, "test-reader-source")
if hashlib.sha256(_READER_SOURCE_RAW).hexdigest() != _READER_SOURCE_SHA256:
    raise RuntimeError("FIXED_ROLE_READER_SOURCE_PIN")
_READER = _CALLER._execute_authenticated_source(
    "test-reader", _READER_SOURCE_PATH, _READER_SOURCE_RAW,
    "_tsj_test_authenticated_fixed_role_reader", "test-reader-source")
_CALLER._assert_module_functions(_READER, ("_token_identity",), "test-reader-source")


_ZERO = "0" * 64


def _spec(path, byte_count=1, digest=_ZERO, maximum=None):
    if maximum is None:
        maximum = max(1, byte_count)
    return {
        "path": path,
        "expectedBytes": byte_count,
        "maxBytes": maximum,
        "sha256": digest,
    }


def _synthetic_config():
    source_specs = {}
    for name in _CALLER.SOURCE_NAMES:
        digest = _CALLER.SOURCE_PINS.get(name, _ZERO)
        maximum = (_CALLER.MAX_PROVENANCE_BYTES if name == "allowlist" else
                   _CALLER.MAX_SOURCE_BYTES)
        source_specs[name] = _spec(
            "C:\\fixture\\source\\" + name + ".bin", digest=digest,
            maximum=maximum)
    source_specs["reader"]["sha256"] = _CALLER.READER_SHA256
    source_specs["caller"]["path"] = "C:\\fixture\\source\\qualify_prior.py"
    source_specs["allowlist"]["path"] = "C:\\fixture\\source\\allowlist.json"

    tool_specs = {}
    for name in _CALLER.TOOL_NAMES:
        fixed = _CALLER.TOOL_FIXED_PINS.get(name)
        if fixed is None:
            tool_specs[name] = _spec(
                "C:\\fixture\\tools\\" + name + ".bin",
                maximum=_CALLER.MAX_READER_TOOL_BYTES)
        else:
            byte_count, digest = fixed
            tool_specs[name] = _spec(
                "C:\\fixture\\tools\\" + name + ".bin", byte_count,
                digest, _CALLER.MAX_READER_TOOL_BYTES)

    return {
        "schema": "tsj-normal-prior-caller-config-v1",
        "sourceSpecs": source_specs,
        "toolSpecs": tool_specs,
        "roots": {"N": "C:\\fixture\\roots\\N",
                  "D": "C:\\fixture\\roots\\D"},
        "scratchRoot": "C:\\fixture\\scratch",
        "allowlistPath": source_specs["allowlist"]["path"],
        "dataOutputRoot": "C:\\fixture\\scratch\\data-output",
    }


def _synthetic_observation():
    def facts(names):
        return {
            name: {
                "path": "C:\\fixture\\observed\\" + name + ".bin",
                "bytes": 17,
                "sha256": ("a" if name in _CALLER.SOURCE_NAMES else "b") * 64,
            }
            for name in names
        }

    return {
        "sourceFacts": facts(_CALLER.SOURCE_NAMES),
        "toolFacts": facts(_CALLER.TOOL_NAMES),
        "harnessModuleLinkEvidence": {"evidenceSha256": "c" * 64},
    }


def _synthetic_journal():
    return {
        "schema": _CALLER.PHASE_JOURNAL_SCHEMA,
        "status": _CALLER.PHASE_JOURNAL_STATUS,
        "sourceManifestSha256": "1" * 64,
        "configSha256": "2" * 64,
        "rootsSha256": "3" * 64,
        "allowlistSha256": _CALLER.ALLOWLIST_SHA256,
        "readerGateSha256": _CALLER.READER_GATE_SHA256,
        "normalizerGateSha256": _CALLER.NORMALIZER_GATE_SHA256,
        "callerGateSha256": "4" * 64,
        "createdUtc": "2026-11-08T01:20:30Z",
    }


def _synthetic_control_token():
    parent = (11, 22, stat.S_IFDIR | 0o777, 100, 200, 300, 0)
    leaf = (11, 33, stat.S_IFREG | 0o666, 12, 400, 500, 0)
    handle = (11, 33, stat.S_IFREG, 12, 400, 0)
    return (
        "c:\\fixture\\input.json",
        (("C:\\fixture", parent), ("C:\\fixture\\input.json", leaf)),
        handle, handle,
    )


class _SyntheticPathReader:
    """Small filesystem fixture for output I/O only; never reads role paths."""

    def __init__(self):
        self.identity_overrides = {}
        self.tamper_output_readback = False

    def _check_windows_local_lexical_path(self, raw):
        if (type(raw) is not str or not ntpath.isabs(raw) or "/" in raw
                or ".." in raw.split("\\")):
            raise _CALLER.CallerError("SYNTHETIC_PATH")

    def _inspect_path(self, raw, expected_kind):
        self._check_windows_local_lexical_path(raw)
        path = Path(raw)
        try:
            item = os.lstat(str(path))
        except OSError:
            raise _CALLER.CallerError("SYNTHETIC_PATH") from None
        if stat.S_ISLNK(item.st_mode):
            raise _CALLER.CallerError("SYNTHETIC_REPARSE")
        if expected_kind == "directory" and not stat.S_ISDIR(item.st_mode):
            raise _CALLER.CallerError("SYNTHETIC_KIND")
        if expected_kind == "file" and not stat.S_ISREG(item.st_mode):
            raise _CALLER.CallerError("SYNTHETIC_KIND")
        snapshot = list(_CALLER._bootstrap_snapshot(item))
        override = self.identity_overrides.get(ntpath.normcase(raw))
        if override is not None:
            snapshot[1] = override
        identity = ((str(path), tuple(snapshot)),)
        return path, path.resolve(strict=True), identity

    def _read_file_once(self, raw_path, limit, expected_bytes, expected_sha, role):
        path, real, before = self._inspect_path(raw_path, "file")
        before_snapshot = before[-1][1]
        descriptor = os.open(str(real), os.O_RDONLY | getattr(os, "O_BINARY", 0))
        chunks = []
        remaining = limit + 1
        try:
            opened_snapshot = _CALLER._bootstrap_snapshot(os.fstat(descriptor))
            if (_CALLER._bootstrap_handle_identity(before_snapshot)
                    != _CALLER._bootstrap_handle_identity(opened_snapshot)):
                raise _CALLER.CallerError("SYNTHETIC_HANDLE")
            while remaining:
                block = os.read(descriptor, min(65536, remaining))
                if not block:
                    break
                chunks.append(block)
                remaining -= len(block)
            raw = b"".join(chunks)
            after_handle = _CALLER._bootstrap_snapshot(os.fstat(descriptor))
        finally:
            os.close(descriptor)
        _after_path, after_real, after = self._inspect_path(str(path), "file")
        digest = _CALLER._sha(raw)
        if (after != before or os.path.normcase(str(after_real)) != os.path.normcase(str(real))
                or _CALLER._bootstrap_handle_identity(opened_snapshot)
                    != _CALLER._bootstrap_handle_identity(after_handle)
                or len(raw) > limit
                or (expected_bytes is not None and len(raw) != expected_bytes)
                or digest != expected_sha):
            raise _CALLER.CallerError("SYNTHETIC_READ_BINDING")
        token = (os.path.normcase(str(real)), before,
                 _CALLER._bootstrap_handle_identity(opened_snapshot),
                 _CALLER._bootstrap_handle_identity(after_handle))
        if self.tamper_output_readback and role == "CALLER_OUTPUT_READBACK":
            altered = raw + b"x"
            return altered, _CALLER._sha(altered), token
        return raw, digest, token


class _SyntheticOutputTree:
    def __init__(self):
        self.path = tempfile.mkdtemp(prefix="tsj-caller-output-synthetic-")
        base = Path(self.path)
        self.closed = False
        self.root_identity = self._identity(base)
        self.owner_marker = base / ".tsj-caller-output-fixture-owner"
        self.owner_marker_bytes = ("SYNTHETIC_OUTPUT_FIXTURE_OWNER:" + self.path).encode("utf-8")
        self.owner_marker.write_bytes(self.owner_marker_bytes)
        self.n_root = base / "N"
        self.d_root = base / "D"
        self.scratch = base / "scratch"
        self.output = self.scratch / "data-output"
        for path in (self.n_root, self.d_root, self.scratch, self.output):
            path.mkdir(parents=True, exist_ok=True)
        self.config = {
            "roots": {"N": str(self.n_root), "D": str(self.d_root)},
            "scratchRoot": str(self.scratch),
            "dataOutputRoot": str(self.output),
        }
        self.reader = _SyntheticPathReader()
        _bindings, self.roots_sha = _CALLER._root_bindings(
            self.reader, self.config["roots"], self.config["scratchRoot"],
            self.config["dataOutputRoot"])

    @staticmethod
    def _identity(path):
        item = os.lstat(str(path))
        return (item.st_dev, item.st_ino, stat.S_IFMT(item.st_mode),
                getattr(item, "st_file_attributes", 0))

    @staticmethod
    def _reject_reparse(path):
        item = os.lstat(str(path))
        reparse = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
        if (stat.S_ISLNK(item.st_mode)
                or getattr(item, "st_file_attributes", 0) & reparse):
            raise RuntimeError("SYNTHETIC_FIXTURE_REPARSE")
        return item

    def _verify_owned_tree(self, root):
        pending = [root]
        while pending:
            current = pending.pop()
            item = self._reject_reparse(current)
            if stat.S_ISDIR(item.st_mode):
                try:
                    with os.scandir(str(current)) as entries:
                        children = [Path(entry.path) for entry in entries]
                except OSError:
                    raise RuntimeError("SYNTHETIC_FIXTURE_SCAN") from None
                for child in children:
                    child_stat = self._reject_reparse(child)
                    if stat.S_ISDIR(child_stat.st_mode):
                        pending.append(child)

    def close(self):
        if self.closed:
            return
        try:
            base = Path(self.path)
            temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
            resolved = base.resolve(strict=True)
            common = Path(os.path.commonpath((str(temp_root), str(resolved))))
            if (os.path.normcase(str(common)) != os.path.normcase(str(temp_root))
                    or os.path.normcase(str(resolved)) == os.path.normcase(str(temp_root))
                    or not base.name.startswith("tsj-caller-output-synthetic-")):
                raise RuntimeError("SYNTHETIC_FIXTURE_CONTAINMENT")
            self._reject_reparse(base)
            if self._identity(base) != self.root_identity:
                raise RuntimeError("SYNTHETIC_FIXTURE_ROOT_IDENTITY")
            self._verify_owned_tree(base)
            if self.owner_marker.read_bytes() != self.owner_marker_bytes:
                raise RuntimeError("SYNTHETIC_FIXTURE_OWNER_MARKER")
            shutil.rmtree(self.path)
            if os.path.lexists(self.path):
                raise RuntimeError("SYNTHETIC_FIXTURE_REMAINS")
            self.closed = True
        except Exception:
            sys.stderr.write("SYNTHETIC_FIXTURE_CLEANUP_FAILED path=" + self.path + "\n")
            raise


class CallerStaticBoundaryTests(unittest.TestCase):
    """Pure synthetic schema and phase gates, with no role-data fixture."""

    def _assert_code(self, expected, callable_, *args, **kwargs):
        with self.assertRaises(_CALLER.CallerError) as caught:
            callable_(*args, **kwargs)
        self.assertEqual(caught.exception.code, expected)

    def test_exact_synthetic_config_shape(self):
        config = _synthetic_config()
        self.assertIs(
            _CALLER._validate_config(config, current_file=config["sourceSpecs"]["caller"]["path"]),
            config)

    def test_config_unknown_or_missing_fields_reject(self):
        config = _synthetic_config()
        config["extra"] = "SYNTHETIC_ONLY"
        self._assert_code("CONFIG_KEYS", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

        config = _synthetic_config()
        del config["scratchRoot"]
        self._assert_code("CONFIG_KEYS", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

    def test_config_source_and_tool_pins_reject_mutations(self):
        config = _synthetic_config()
        config["sourceSpecs"]["normalizer"]["sha256"] = "f" * 64
        self._assert_code("SOURCE_PIN_NORMALIZER", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

        config = _synthetic_config()
        config["toolSpecs"]["rootCallHarness"]["expectedBytes"] += 1
        self._assert_code("TOOL_PIN_ROOTCALLHARNESS", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

    def test_allowlist_spec_retains_its_one_mib_bound(self):
        config = _synthetic_config()
        config["sourceSpecs"]["allowlist"]["maxBytes"] = (
            _CALLER.MAX_PROVENANCE_BYTES + 1)
        self._assert_code("SPEC_MAX_BYTES", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

    def test_reader_source_retains_the_four_mib_observer_bound(self):
        config = _synthetic_config()
        config["sourceSpecs"]["reader"]["maxBytes"] = _CALLER.MAX_SOURCE_BYTES + 1
        self._assert_code("SPEC_MAX_BYTES", _CALLER._validate_config, config,
                          current_file=config["sourceSpecs"]["caller"]["path"])

    def test_allowlist_reconciliation_keeps_role_in_each_independent_row(self):
        rows = [{"role": "SYNTHETIC_ROLE_%d" % index, "root": "N",
                 "path": "synthetic/%d.json" % index, "maxBytes": 32,
                 "expectedBytes": index, "sha256": "a" * 64}
                for index in range(20)]
        pins = {row["role"]: dict(row) for row in rows}
        specs = tuple(types.SimpleNamespace(
            role=row["role"], root=row["root"], relative_path=row["path"],
            max_bytes=row["maxBytes"], expected_bytes=row["expectedBytes"],
            sha256=row["sha256"], exact_content_authority=None,
            summary_parse_max_bytes=None) for row in rows)
        reader = types.SimpleNamespace(_load_pinned_allowlist=lambda _path: (
            specs, {"roleCount": 20, "allowlistSha256": _CALLER.ALLOWLIST_SHA256}))
        normalizer = types.SimpleNamespace(_validate_allowlist_bytes=lambda _raw: (
            {"roleCount": 20}, pins))
        value, actual = _CALLER._compare_allowlist(
            reader, normalizer, b"SYNTHETIC_ONLY", "C:\\fixture\\metadata.json")
        self.assertEqual(value, {"roleCount": 20})
        self.assertEqual(actual, pins)
        pins[rows[0]["role"]]["role"] = "SYNTHETIC_WRONG_ROLE"
        self._assert_code("ALLOWLIST_READER_NORMALIZER_DISAGREEMENT",
                          _CALLER._compare_allowlist, reader, normalizer,
                          b"SYNTHETIC_ONLY", "C:\\fixture\\metadata.json")

    def test_manifest_is_path_free_and_acyclic(self):
        observation = _synthetic_observation()
        manifest = _CALLER._source_manifest(
            "1" * 64, _CALLER.ALLOWLIST_SHA256, "2" * 64, "3" * 64,
            observation)
        _CALLER._validate_source_manifest_shape(manifest)
        self.assertEqual(manifest["purpose"], "SOURCE_ONLY_BEFORE_DATA")
        self.assertEqual(
            set(manifest),
            {"schema", "purpose", "configSha256", "allowlistSha256",
             "kernelProvenanceSha256", "rootsSha256", "sourceFacts", "toolFacts",
             "harnessModuleLinkEvidenceSha256", "byteObservationEvidenceSha256"})
        raw = _CALLER._source_manifest_bytes(manifest)
        self.assertNotIn(b"C:\\", raw)
        for forbidden in (b"currentContext", b"generatedData", b"classSha256",
                          b"runManifest", b"phaseJournalSha256"):
            self.assertNotIn(forbidden, raw)

    def test_manifest_rejects_run_or_path_material(self):
        manifest = _CALLER._source_manifest(
            "1" * 64, _CALLER.ALLOWLIST_SHA256, "2" * 64, "3" * 64,
            _synthetic_observation())
        manifest["currentContextSha256"] = "5" * 64
        self._assert_code("SOURCE_MANIFEST_KEYS", _CALLER._validate_source_manifest_shape,
                          manifest)

    def test_valid_synthetic_phase_journal(self):
        journal = _synthetic_journal()
        self.assertIs(
            _CALLER._validate_phase_journal(
                journal, config_sha="2" * 64, manifest_sha="1" * 64,
                roots_sha="3" * 64),
            journal)

    def test_phase_journal_rejects_wrong_or_extra_authority(self):
        mutations = (
            ("PHASE_JOURNAL_STATUS", {"status": "ALLOW_OTHER_READ"}),
            ("PHASE_JOURNAL_CONFIGSHA256", {"configSha256": "9" * 64}),
            ("PHASE_JOURNAL_ROOTSSHA256", {"rootsSha256": "8" * 64}),
            ("PHASE_JOURNAL_READERGATESHA256", {"readerGateSha256": "7" * 64}),
            ("PHASE_JOURNAL_CALLER_GATE", {"callerGateSha256": "bad"}),
            ("PHASE_JOURNAL_CREATED_UTC", {"createdUtc": "not-a-time"}),
            ("PHASE_JOURNAL_CREATED_UTC", {"createdUtc": "2026-02-30T01:20:30Z"}),
        )
        for expected, mutation in mutations:
            with self.subTest(expected=expected):
                journal = _synthetic_journal()
                journal.update(mutation)
                self._assert_code(
                    expected, _CALLER._validate_phase_journal, journal,
                    config_sha="2" * 64, manifest_sha="1" * 64,
                    roots_sha="3" * 64)

        journal = _synthetic_journal()
        journal["currentRunSha256"] = "6" * 64
        self._assert_code(
            "PHASE_JOURNAL_KEYS", _CALLER._validate_phase_journal, journal,
            config_sha="2" * 64, manifest_sha="1" * 64, roots_sha="3" * 64)

    def test_bad_phase_journal_never_calls_fixed_role_reader(self):
        class ReaderTrap:
            def __init__(self):
                self.calls = 0

            def read_normal_prior(self, **_kwargs):
                self.calls += 1
                raise AssertionError("synthetic closed-phase guard was bypassed")

        reader = ReaderTrap()
        self._assert_code(
            "PHASE_JOURNAL_KEYS", _CALLER._read_fixed_roles_after_journal,
            reader, config=_synthetic_config(), journal={},
            config_sha="2" * 64, manifest_sha="1" * 64,
            roots_sha="3" * 64, read_started_state={"value": False})
        self.assertEqual(reader.calls, 0)

    def test_wrong_authenticated_document_digest_rejects_synthetic_bytes(self):
        raw = b'{"purpose":"SYNTHETIC_ONLY"}'

        class SyntheticReader:
            def _check_windows_local_lexical_path(self, _path):
                return None

            def _read_file_once(self, _path, _limit, _expected_bytes,
                                _expected_sha, _role):
                return raw, _CALLER._sha(raw), ("SYNTHETIC_ONLY",)

        self._assert_code(
            "READER_RESULT_BINDING", _CALLER._read_authenticated_document,
            SyntheticReader(), "C:\\fixture\\only.json",
            _CALLER.MAX_JOURNAL_BYTES, "f" * 64,
            "SYNTHETIC_DOCUMENT", "phase-journal")

    def test_final_input_recheck_binds_bytes_and_identity_for_all_documents(self):
        for role in ("CONFIG", "SOURCE_MANIFEST", "PHASE_JOURNAL"):
            with self.subTest(role=role):
                raw = (role + "_SYNTHETIC").encode("ascii")
                token = _synthetic_control_token()
                _CALLER._require_same_input_document(
                    _READER, role, raw, token, raw, token, "synthetic-final-recheck")
                self._assert_code(
                    role + "_BYTES_CHANGED_AFTER_WRITES",
                    _CALLER._require_same_input_document,
                    _READER, role, raw, token, raw + b"!", token,
                    "synthetic-final-recheck")
                self._assert_code(
                    role + "_IDENTITY_CHANGED_AFTER_WRITES",
                    _CALLER._require_same_input_document,
                    _READER, role, raw, token, raw,
                    (token[0], token[1], (11, 99, stat.S_IFREG, 12, 400, 0),
                     (11, 99, stat.S_IFREG, 12, 400, 0)),
                    "synthetic-final-recheck")

    def test_cross_read_token_allows_parent_clock_changes_but_pins_identity(self):
        raw = b"SYNTHETIC_CONTROL_DOCUMENT"
        token = _synthetic_control_token()
        parent_path, parent = token[1][0]
        changed_parent = (parent[0], parent[1], parent[2],
                          parent[3] + 1, parent[4] + 1, parent[5] + 1, parent[6])
        clock_changed = (token[0], ((parent_path, changed_parent), token[1][1]),
                         token[2], token[3])
        _CALLER._require_same_input_document(
            _READER, "SOURCE_MANIFEST", raw, token, raw, clock_changed,
            "synthetic-ancestor-clock-change")

        replaced_parent = (parent[0], parent[1] + 1, parent[2], parent[3],
                           parent[4], parent[5], parent[6])
        replaced = (token[0], ((parent_path, replaced_parent), token[1][1]),
                    token[2], token[3])
        self._assert_code(
            "SOURCE_MANIFEST_IDENTITY_CHANGED_AFTER_WRITES",
            _CALLER._require_same_input_document,
            _READER, "SOURCE_MANIFEST", raw, token, raw, replaced,
            "synthetic-ancestor-replacement")

        leaf_path, leaf = token[1][1]
        replaced_leaf = (leaf[0], leaf[1] + 1, leaf[2], leaf[3],
                         leaf[4], leaf[5], leaf[6])
        replacement_handle = (token[2][0], leaf[1] + 1, stat.S_IFREG,
                              token[2][3], token[2][4], token[2][5])
        replaced_file = (
            token[0], (token[1][0], (leaf_path, replaced_leaf)),
            replacement_handle, replacement_handle)
        self._assert_code(
            "SOURCE_MANIFEST_IDENTITY_CHANGED_AFTER_WRITES",
            _CALLER._require_same_input_document,
            _READER, "SOURCE_MANIFEST", raw, token, raw, replaced_file,
            "synthetic-file-replacement")

        touched_leaf = (leaf[0], leaf[1], leaf[2], leaf[3],
                        leaf[4] + 1, leaf[5] + 1, leaf[6])
        touched_handle = (token[2][0], token[2][1], token[2][2],
                          token[2][3], token[2][4] + 1, token[2][5])
        touched = (token[0], (token[1][0], (leaf_path, touched_leaf)),
                   touched_handle, touched_handle)
        self._assert_code(
            "SOURCE_MANIFEST_IDENTITY_CHANGED_AFTER_WRITES",
            _CALLER._require_same_input_document,
            _READER, "SOURCE_MANIFEST", raw, token, raw, touched,
            "synthetic-leaf-timestamp-change")


class CallerOutputIoTests(unittest.TestCase):
    """Owned temporary output fixtures only; no historical inputs are present."""

    def setUp(self):
        self.tree = _SyntheticOutputTree()

    def tearDown(self):
        if not self.tree.closed:
            sys.stderr.write("SYNTHETIC_FIXTURE_PRESERVED path=" + self.tree.path + "\n")

    def _assert_code(self, expected, callable_, *args, **kwargs):
        with self.assertRaises(_CALLER.CallerError) as caught:
            callable_(*args, **kwargs)
        self.assertEqual(caught.exception.code, expected)

    def _expected_files(self):
        names = (_CALLER.ATTEMPT_FILE,) + tuple(
            name for name, _label in _CALLER.ROLE_OUTPUT_NAMES)
        return {name: ("SYNTHETIC_OUTPUT_ONLY:" + name).encode("ascii")
                for name in names}

    def test_output_root_leaf_identity_is_part_of_root_binding(self):
        bindings, digest = _CALLER._root_bindings(
            self.tree.reader, self.tree.config["roots"],
            self.tree.config["scratchRoot"], self.tree.config["dataOutputRoot"])
        self.assertIn("dataOutputRoot", bindings["roots"])
        self.assertEqual(digest, self.tree.roots_sha)
        output_path = ntpath.normcase(self.tree.config["dataOutputRoot"])
        output_stat = os.lstat(self.tree.config["dataOutputRoot"])
        self.tree.reader.identity_overrides[output_path] = output_stat.st_ino + 1
        self._assert_code(
            "ROOTS_CHANGED", _CALLER._verify_root_bindings,
            self.tree.reader, self.tree.config, self.tree.roots_sha,
            "synthetic-output-root-replacement")
        self.tree.close()

    def test_exclusive_output_write_has_authenticated_identity_readback(self):
        raw = b"SYNTHETIC_OUTPUT_ONLY"
        path = str(self.tree.output / "context.json")
        fact = _CALLER._write_exclusive(
            self.tree.reader, self.tree.config, path, raw,
            _CALLER.MAX_OUTPUT_BYTES, self.tree.config["scratchRoot"],
            self.tree.roots_sha, "synthetic-output-write")
        self.assertEqual(fact, {"bytes": len(raw), "sha256": _CALLER._sha(raw)})
        self.assertEqual(Path(path).read_bytes(), raw)
        self._assert_code(
            "EXCLUSIVE_OUTPUT_CREATE", _CALLER._write_exclusive,
            self.tree.reader, self.tree.config, path, raw,
            _CALLER.MAX_OUTPUT_BYTES, self.tree.config["scratchRoot"],
            self.tree.roots_sha, "synthetic-output-overwrite")
        self.tree.close()

    def test_output_readback_rejects_changed_bytes(self):
        raw = b"SYNTHETIC_OUTPUT_ONLY"
        self.tree.reader.tamper_output_readback = True
        self._assert_code(
            "READER_RESULT_BINDING", _CALLER._write_exclusive,
            self.tree.reader, self.tree.config,
            str(self.tree.output / "context.json"), raw,
            _CALLER.MAX_OUTPUT_BYTES, self.tree.config["scratchRoot"],
            self.tree.roots_sha, "synthetic-output-tamper")
        self.tree.close()

    def test_exact_output_closure_reads_five_files_and_rejects_extras(self):
        expected = self._expected_files()
        for name, raw in expected.items():
            (self.tree.output / name).write_bytes(raw)
        closure = _CALLER._validate_output_closure(
            self.tree.reader, self.tree.config, expected, self.tree.roots_sha)
        self.assertEqual(closure["fileCount"], 5)
        self.assertEqual({item["name"] for item in closure["files"]}, set(expected))
        (self.tree.output / "unexpected.bin").write_bytes(b"SYNTHETIC_EXTRA")
        self._assert_code(
            "OUTPUT_CLOSURE_NAMES", _CALLER._validate_output_closure,
            self.tree.reader, self.tree.config, expected, self.tree.roots_sha)
        self.tree.close()

    def test_failure_evidence_identity_error_preserves_original_and_does_not_retarget(self):
        original = _CALLER.CallerError("NORMALIZER_SYNTHETIC_REJECT", "normalization")
        output_path = ntpath.normcase(self.tree.config["dataOutputRoot"])
        output_stat = os.lstat(self.tree.config["dataOutputRoot"])
        self.tree.reader.identity_overrides[output_path] = output_stat.st_ino + 1
        record = _CALLER._failure_record(
            original.code, original.phase, "1" * 64, "2" * 64,
            "3" * 64, self.tree.roots_sha, True)
        _CALLER._best_effort_failure_evidence(
            self.tree.reader, self.tree.config, record,
            self.tree.roots_sha, original)
        self.assertEqual(original.code, "NORMALIZER_SYNTHETIC_REJECT")
        self.assertEqual(original.phase, "normalization")
        self.assertEqual(original.reporting_code, "ROOTS_CHANGED")
        self.assertFalse((self.tree.output / _CALLER.FAILURE_FILE).exists())
        self.tree.close()


if __name__ == "__main__":
    unittest.main()
