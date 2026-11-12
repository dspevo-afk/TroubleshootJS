"""Synthetic-only caller boundary tests; never open historical role bodies."""

from __future__ import annotations

import contextlib
import importlib.util
import hashlib
import io
import os
import ntpath
import shutil
import stat
import sys
import tempfile
import types
import unittest
from unittest import mock
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


def _synthetic_tool_facts(prefix, digest):
    return {
        name: {
            "path": "C:\\fixture\\" + prefix + "\\" + name + ".exe",
            "bytes": 17,
            "sha256": digest,
        }
        for name in _CALLER.TOOL_NAMES
    }


def _synthetic_tool_snapshot():
    snapshot = {
        "schema": _CALLER.HISTORICAL_TOOL_SNAPSHOT_SCHEMA,
        "origin": "ACCEPTED_SOURCE_PREFLIGHT_ONLY",
        "acceptedSourceCommit": _CALLER.HISTORICAL_SNAPSHOT_ACCEPTED_SOURCE_COMMIT,
        "configSha256": _CALLER.HISTORICAL_SNAPSHOT_CONFIG_SHA256,
        "sourceManifestSha256": _CALLER.HISTORICAL_SNAPSHOT_SOURCE_MANIFEST_SHA256,
        "preflightEvidenceSha256": _CALLER.HISTORICAL_SNAPSHOT_PREFLIGHT_SHA256,
        "allowlistSha256": _CALLER.ALLOWLIST_SHA256,
        "rootsSha256": "e" * 64,
        "toolFacts": _synthetic_tool_facts("snapshot", "d" * 64),
        "captureReceiptBindingEstablished": False,
        "priorRunBindingEstablished": False,
        "fixedRoleReadAuthorized": False,
        "priorQualificationEstablished": False,
    }
    snapshot["snapshotSha256"] = _CALLER._sha(_CALLER._canonical_json(snapshot))
    return snapshot


def _synthetic_historical_input_records():
    rows = (
        ("C:\\fixture\\historical\\config.json", _CALLER.MAX_CONFIG_BYTES,
         b"SYNTHETIC_HISTORICAL_CONFIG", "HISTORICAL_SNAPSHOT_CONFIG"),
        ("C:\\fixture\\historical\\source-manifest.json", _CALLER.MAX_MANIFEST_BYTES,
         b"SYNTHETIC_HISTORICAL_SOURCE_MANIFEST",
         "HISTORICAL_SNAPSHOT_SOURCE_MANIFEST"),
        ("C:\\fixture\\historical\\preflight.json", _CALLER.MAX_JOURNAL_BYTES,
         b"SYNTHETIC_HISTORICAL_PREFLIGHT", "HISTORICAL_SNAPSHOT_PREFLIGHT"),
    )
    overrides = {}
    records = []
    for index, (path, maximum, raw, role) in enumerate(rows):
        prefix = (
            ("CONFIG", "HISTORICAL_SNAPSHOT_CONFIG"),
            ("SOURCE_MANIFEST", "HISTORICAL_SNAPSHOT_SOURCE_MANIFEST"),
            ("PREFLIGHT", "HISTORICAL_SNAPSHOT_PREFLIGHT"),
        )[index]
        name, constant_prefix = prefix
        overrides[constant_prefix + "_BYTES"] = len(raw)
        overrides[constant_prefix + "_SHA256"] = _CALLER._sha(raw)
        digest = _CALLER._sha(raw)
        records.append((path, maximum, len(raw), digest, role, raw,
                        ("SYNTHETIC_TOKEN_" + name,)))
    return tuple(records), overrides


def _synthetic_authority_state():
    normalizer = types.ModuleType("_tsj_synthetic_normalizer_authority")
    normalizer.NORMALIZER_AUTHORITY_SCHEMA = "tsj-q100-normalizer-authority-v2"
    normalizer.SOURCE_BINDING_PINS = {"caller": "a" * 64}
    normalizer.seen_historical = []
    normalizer.reject_historical = False

    def validate_prior_tool_facts(facts):
        seen_historical.append(facts)
        if reject_historical:
            error_type = type("SyntheticToolPinError", (Exception,), {})
            error = error_type()
            error.code = "SYNTHETIC_PIN"
            raise error
        return facts

    normalizer.validate_prior_tool_facts = types.FunctionType(
        validate_prior_tool_facts.__code__, normalizer.__dict__,
        "validate_prior_tool_facts")
    normalizer.validate_prior_tool_facts.__module__ = normalizer.__name__
    current_facts = _synthetic_tool_facts("current", "b" * 64)
    state = {
        "modules": {"normalizer": normalizer},
        "sourceManifestSha256": "c" * 64,
        "byteObservation": {"toolFacts": current_facts},
    }
    config = {"roots": {"N": "C:\\fixture\\root\\N",
                        "D": "C:\\fixture\\root\\D"}}
    return config, state, normalizer


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
        "callerGate": {
            "schema": "tsj-q100-caller-gate-result-v1",
            "definition": "normal-prior-caller-synthetic-v2",
            "scope": "CURRENT_CALLER_SYNTHETIC_SOURCE_ONLY",
            "sourceSha256": "0" * 64,
            "testSourceSha256": _CALLER.CALLER_GATE_TEST_SHA256,
            "pythonSha256": _CALLER.TOOL_FIXED_PINS["qualificationPython"][1],
            # Synthetic references only; they do not identify real gate evidence.
            "producerSourceSha256": "c" * 64,
            "invocationReceiptSha256": "d" * 64,
            "result": {
                "status": "PASS",
                "testsRun": 36,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "exitCode": 0,
                "terminationProven": True,
            },
        },
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

    def test_root_powershell_pin_selects_current_host_and_rejects_stale_or_wrong_specs(self):
        current_bytes = 495616
        current_sha256 = "d8f182560210d43baaf767d482ad1dc1207372522e91413b9d5e515a875e552d"
        config = _synthetic_config()
        current_file = config["sourceSpecs"]["caller"]["path"]
        config["toolSpecs"]["rootCallPowerShell"] = _spec(
            "C:\\fixture\\tools\\rootCallPowerShell.exe", current_bytes,
            current_sha256, _CALLER.MAX_READER_TOOL_BYTES)
        self.assertIs(_CALLER._validate_config(config, current_file=current_file), config)

        stale_sha256 = "60c9b29843624dd8af6b6a7b26147753be6aeef65098b9a503cc50850afe410f"
        mutations = (
            (current_bytes, stale_sha256),
            (current_bytes, "a" * 64),
            (current_bytes - 1, current_sha256),
        )
        for byte_count, digest in mutations:
            candidate = _synthetic_config()
            candidate["toolSpecs"]["rootCallPowerShell"] = _spec(
                "C:\\fixture\\tools\\rootCallPowerShell.exe", byte_count,
                digest, _CALLER.MAX_READER_TOOL_BYTES)
            self._assert_code(
                "TOOL_PIN_ROOTCALLPOWERSHELL", _CALLER._validate_config,
                candidate, current_file=candidate["sourceSpecs"]["caller"]["path"])


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
        """Valid contract shape only; the operational journal wrapper stays closed."""
        journal = _synthetic_journal()
        self.assertIs(
            _CALLER._validate_phase_journal_contract(
                journal, config_sha="2" * 64, manifest_sha="1" * 64,
                roots_sha="3" * 64, caller_source_sha="0" * 64),
            journal)

    def test_phase_journal_rejects_wrong_or_extra_authority(self):
        mutations = (
            ("PHASE_JOURNAL_STATUS", {"status": "ALLOW_OTHER_READ"}),
            ("PHASE_JOURNAL_CONFIGSHA256", {"configSha256": "9" * 64}),
            ("PHASE_JOURNAL_ROOTSSHA256", {"rootsSha256": "8" * 64}),
            ("PHASE_JOURNAL_READERGATESHA256", {"readerGateSha256": "7" * 64}),
            ("PHASE_JOURNAL_CALLER_GATE_KEYS_TYPE", {"callerGate": "bad"}),
            ("PHASE_JOURNAL_CREATED_UTC", {"createdUtc": "not-a-time"}),
            ("PHASE_JOURNAL_CREATED_UTC", {"createdUtc": "2026-02-30T01:20:30Z"}),
        )
        for expected, mutation in mutations:
            with self.subTest(expected=expected):
                journal = _synthetic_journal()
                journal.update(mutation)
                self._assert_code(
                    expected, _CALLER._validate_phase_journal_contract, journal,
                    config_sha="2" * 64, manifest_sha="1" * 64,
                    roots_sha="3" * 64, caller_source_sha="0" * 64)

        journal = _synthetic_journal()
        journal["currentRunSha256"] = "6" * 64
        self._assert_code(
            "PHASE_JOURNAL_KEYS", _CALLER._validate_phase_journal_contract, journal,
            config_sha="2" * 64, manifest_sha="1" * 64,
            roots_sha="3" * 64, caller_source_sha="0" * 64)

    def test_phase_journal_rejects_v1_scalar_caller_gate(self):
        journal = _synthetic_journal()
        journal["schema"] = "tsj-q100-normal-prior-phase-journal-v1"
        del journal["callerGate"]
        journal["callerGateSha256"] = "4" * 64
        self._assert_code(
            "PHASE_JOURNAL_KEYS", _CALLER._validate_phase_journal_contract, journal,
            config_sha="2" * 64, manifest_sha="1" * 64,
            roots_sha="3" * 64, caller_source_sha="0" * 64)

    def test_phase_journal_rejects_stale_caller_gate_bindings(self):
        mutations = (
            ("PHASE_JOURNAL_CALLER_GATE_CONTRACT",
             lambda gate: gate.update(schema="tsj-q100-caller-gate-result-v0")),
            ("PHASE_JOURNAL_CALLER_GATE_SOURCE",
             lambda gate: gate.update(sourceSha256="1" * 64)),
            ("PHASE_JOURNAL_CALLER_GATE_TEST_SOURCE",
             lambda gate: gate.update(testSourceSha256="2" * 64)),
            ("PHASE_JOURNAL_CALLER_GATE_PYTHON",
             lambda gate: gate.update(pythonSha256="3" * 64)),
            ("PHASE_JOURNAL_CALLER_GATE_CONTRACT",
             lambda gate: gate.update(definition="normal-prior-caller-synthetic-v1")),
            ("PHASE_JOURNAL_CALLER_GATE_CONTRACT",
             lambda gate: gate.update(scope="CURRENT_CALLER_OTHER_SCOPE")),
        )
        for expected, mutate_gate in mutations:
            with self.subTest(expected=expected):
                journal = _synthetic_journal()
                mutate_gate(journal["callerGate"])
                self._assert_code(
                    expected, _CALLER._validate_phase_journal_contract, journal,
                    config_sha="2" * 64, manifest_sha="1" * 64,
                    roots_sha="3" * 64, caller_source_sha="0" * 64)

    def test_phase_journal_rejects_failed_partial_or_untyped_results(self):
        mutations = (
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "status", "FAIL"),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "testsRun", 35),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "failures", 1),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "errors", 1),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "skipped", 1),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "exitCode", 2),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "terminationProven", False),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "testsRun", True),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "failures", False),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "exitCode", True),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "terminationProven", 1),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT", "testsRun", "36"),
        )
        for expected, field, value in mutations:
            with self.subTest(expected=expected, field=field):
                journal = _synthetic_journal()
                journal["callerGate"]["result"][field] = value
                self._assert_code(
                    expected, _CALLER._validate_phase_journal_contract, journal,
                    config_sha="2" * 64, manifest_sha="1" * 64,
                    roots_sha="3" * 64, caller_source_sha="0" * 64)

    def test_phase_journal_rejects_bad_provenance_shape_and_operational_use(self):
        journal = _synthetic_journal()
        journal["callerGate"] = "bad"
        self._assert_code(
            "PHASE_JOURNAL_CALLER_GATE_KEYS_TYPE",
            _CALLER._validate_phase_journal_contract, journal,
            config_sha="2" * 64, manifest_sha="1" * 64,
            roots_sha="3" * 64, caller_source_sha="0" * 64)

        mutations = (
            ("PHASE_JOURNAL_CALLER_GATE_KEYS",
             lambda gate: gate.pop("producerSourceSha256")),
            ("PHASE_JOURNAL_CALLER_GATE_KEYS",
             lambda gate: gate.update(extra="SYNTHETIC_ONLY")),
            ("PHASE_JOURNAL_CALLER_GATE_PROVENANCE",
             lambda gate: gate.update(producerSourceSha256=3)),
            ("PHASE_JOURNAL_CALLER_GATE_PROVENANCE",
             lambda gate: gate.update(invocationReceiptSha256=4)),
            ("PHASE_JOURNAL_CALLER_GATE_PROVENANCE",
             lambda gate: gate.update(invocationReceiptSha256="g" * 64)),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT_KEYS_TYPE",
             lambda gate: gate.update(result=[])),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT_KEYS",
             lambda gate: gate["result"].pop("errors")),
            ("PHASE_JOURNAL_CALLER_GATE_RESULT_KEYS",
             lambda gate: gate["result"].update(extra="SYNTHETIC_ONLY")),
        )
        for expected, mutate_gate in mutations:
            with self.subTest(expected=expected):
                journal = _synthetic_journal()
                mutate_gate(journal["callerGate"])
                self._assert_code(
                    expected, _CALLER._validate_phase_journal_contract, journal,
                    config_sha="2" * 64, manifest_sha="1" * 64,
                    roots_sha="3" * 64, caller_source_sha="0" * 64)

        for key in ("producerSourceSha256", "invocationReceiptSha256"):
            journal = _synthetic_journal()
            del journal["callerGate"][key]
            self._assert_code(
                "PHASE_JOURNAL_CALLER_GATE_KEYS", _CALLER._validate_phase_journal_contract,
                journal, config_sha="2" * 64, manifest_sha="1" * 64,
                roots_sha="3" * 64, caller_source_sha="0" * 64)

        class ReaderTrap:
            def __init__(self):
                self.calls = 0

            def read_normal_prior(self, **_kwargs):
                self.calls += 1
                raise AssertionError("synthetic gate contract reached operational reader")

        reader = ReaderTrap()
        read_started = {"value": False}
        self._assert_code(
            "PHASE_JOURNAL_PRODUCER_NOT_QUALIFIED",
            _CALLER._read_fixed_roles_after_journal, reader,
            config=_synthetic_config(), journal=_synthetic_journal(),
            config_sha="2" * 64, manifest_sha="1" * 64, roots_sha="3" * 64,
            read_started_state=read_started)
        self.assertEqual(reader.calls, 0)
        self.assertIs(read_started["value"], False)

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


class CallerHistoricalToolExpectationTests(unittest.TestCase):
    def _assert_code(self, expected, callable_, *args, **kwargs):
        with self.assertRaises(_CALLER.CallerError) as caught:
            callable_(*args, **kwargs)
        self.assertEqual(caught.exception.code, expected)

    def test_snapshot_rows_validate_as_detached_expectations_only(self):
        snapshot = _synthetic_tool_snapshot()
        with mock.patch.object(
                _CALLER, "HISTORICAL_TOOL_SNAPSHOT_SHA256",
                snapshot["snapshotSha256"]):
            expectations = _CALLER._historical_tool_expectations(snapshot)

        self.assertEqual(expectations, snapshot["toolFacts"])
        for name in _CALLER.TOOL_NAMES:
            self.assertIsNot(expectations[name], snapshot["toolFacts"][name])
        expectations["nativeJava"]["path"] = "C:\\fixture\\mutated\\java.exe"
        self.assertNotEqual(
            expectations["nativeJava"]["path"],
            snapshot["toolFacts"]["nativeJava"]["path"])

    def test_snapshot_expectation_binding_rejects_wrong_flags_and_changed_facts(self):
        for field in ("captureReceiptBindingEstablished", "priorRunBindingEstablished",
                      "fixedRoleReadAuthorized", "priorQualificationEstablished"):
            with self.subTest(field=field):
                snapshot = _synthetic_tool_snapshot()
                with mock.patch.object(
                        _CALLER, "HISTORICAL_TOOL_SNAPSHOT_SHA256",
                        snapshot["snapshotSha256"]):
                    snapshot[field] = True
                    self._assert_code(
                        "HISTORICAL_TOOL_EXPECTATIONS_BINDING",
                        _CALLER._historical_tool_expectations, snapshot)

        snapshot = _synthetic_tool_snapshot()
        with mock.patch.object(
                _CALLER, "HISTORICAL_TOOL_SNAPSHOT_SHA256",
                snapshot["snapshotSha256"]):
            snapshot["toolFacts"]["nativeJava"]["sha256"] = "f" * 64
            self._assert_code(
                "HISTORICAL_TOOL_EXPECTATIONS_BINDING",
                _CALLER._historical_tool_expectations, snapshot)

    def test_builder_requires_explicit_historical_rows_and_separates_copies(self):
        config, state, normalizer = _synthetic_authority_state()
        with self.assertRaises(TypeError):
            _CALLER._make_normalizer_authority(config, state, {})

        expected = _synthetic_tool_facts("expected", "d" * 64)
        current_before = {
            name: dict(row)
            for name, row in state["byteObservation"]["toolFacts"].items()
        }
        resource_facts = {"SYNTHETIC_ONLY": {"bytes": 1}}
        authority = _CALLER._make_normalizer_authority(
            config, state, resource_facts,
            historical_tool_expectations=expected)

        self.assertEqual(authority["schema"], "tsj-q100-normalizer-authority-v2")
        self.assertEqual(authority["historicalToolExpectations"], expected)
        self.assertEqual(
            authority["currentExecutionToolFacts"],
            {"sourceSha256": state["sourceManifestSha256"],
             "toolFacts": current_before})
        self.assertEqual(
            authority["currentExecutionToolFacts"]["sourceSha256"],
            authority["currentSourceAuthority"]["sourceSha256"])
        self.assertEqual(normalizer.seen_historical,
                         [authority["historicalToolExpectations"]])
        for name in _CALLER.TOOL_NAMES:
            self.assertIsNot(authority["historicalToolExpectations"][name], expected[name])
            self.assertIsNot(authority["currentExecutionToolFacts"]["toolFacts"][name],
                             state["byteObservation"]["toolFacts"][name])
            self.assertIsNot(authority["historicalToolExpectations"][name],
                             authority["currentExecutionToolFacts"]["toolFacts"][name])
        authority["currentExecutionToolFacts"]["toolFacts"]["nativeJava"]["path"] = (
            "C:\\fixture\\mutated\\java.exe")
        self.assertEqual(state["byteObservation"]["toolFacts"], current_before)

    def test_builder_uses_historical_validator_for_explicit_rows(self):
        config, state, normalizer = _synthetic_authority_state()
        normalizer.reject_historical = True
        self._assert_code(
            "HISTORICAL_TOOL_EXPECTATIONS_SYNTHETIC_PIN",
            _CALLER._make_normalizer_authority,
            config, state, {},
            historical_tool_expectations=_synthetic_tool_facts(
                "expected", "d" * 64))

    def test_historical_input_reader_authenticates_three_inputs_before_decode(self):
        original_inputs, overrides = _synthetic_historical_input_records()
        raw_by_path = {record[0]: record[5] for record in original_inputs}
        token_by_path = {record[0]: record[6] for record in original_inputs}
        events = []
        expected_snapshot = _synthetic_tool_snapshot()

        def read_input(_reader, path, maximum, expected_bytes, expected_sha,
                       role, phase):
            events.append(role)
            record = next(item for item in original_inputs if item[0] == path)
            self.assertEqual(maximum, record[1])
            self.assertEqual(expected_bytes, record[2])
            self.assertEqual(expected_sha, record[3])
            self.assertEqual(phase, "historical-tool-snapshot")
            return raw_by_path[path], token_by_path[path]

        def decode(config_raw, manifest_raw, preflight_raw):
            events.append("decode")
            self.assertEqual((config_raw, manifest_raw, preflight_raw),
                             tuple(record[5] for record in original_inputs))
            return expected_snapshot

        with mock.patch.multiple(_CALLER, **overrides), \
                mock.patch.object(_CALLER, "_read_with_reader",
                                  side_effect=read_input), \
                mock.patch.object(_CALLER, "_historical_tool_snapshot_bytes",
                                  side_effect=decode):
            snapshot, retained_inputs = _CALLER._read_historical_tool_inputs(
                object(),
                historical_config_path=original_inputs[0][0],
                historical_source_manifest_path=original_inputs[1][0],
                historical_preflight_path=original_inputs[2][0])

        self.assertIs(snapshot, expected_snapshot)
        self.assertEqual(events, [
            "HISTORICAL_SNAPSHOT_CONFIG",
            "HISTORICAL_SNAPSHOT_SOURCE_MANIFEST",
            "HISTORICAL_SNAPSHOT_PREFLIGHT",
            "decode",
        ])
        self.assertEqual(retained_inputs, original_inputs)
        self.assertIs(type(retained_inputs), tuple)
        self.assertTrue(all(type(record) is tuple and len(record) == 7
                            for record in retained_inputs))

    def test_historical_input_recheck_rejects_stale_bytes(self):
        original_inputs, overrides = _synthetic_historical_input_records()
        events = []

        def read_input(_reader, path, maximum, expected_bytes, expected_sha,
                       role, phase):
            events.append(("read", role, phase))
            record = next(item for item in original_inputs if item[0] == path)
            return record[5], record[6]

        def compare(_reader, role, expected_raw, _expected_token,
                    current_raw, _current_token, phase):
            events.append(("compare", role, phase))
            if expected_raw != current_raw:
                raise _CALLER.CallerError(role + "_BYTES_CHANGED_AFTER_WRITES", phase)

        with mock.patch.multiple(_CALLER, **overrides), \
                mock.patch.object(_CALLER, "_read_with_reader",
                                  side_effect=read_input), \
                mock.patch.object(_CALLER, "_require_same_input_document",
                                  side_effect=compare):
            _CALLER._recheck_historical_tool_inputs(object(), original_inputs)
        self.assertEqual(len([event for event in events if event[0] == "read"]), 3)
        self.assertEqual(len([event for event in events if event[0] == "compare"]), 3)

        events.clear()

        def read_stale_input(_reader, path, maximum, expected_bytes, expected_sha,
                             role, phase):
            record = next(item for item in original_inputs if item[0] == path)
            raw = record[5] + b"CHANGED" if role == "HISTORICAL_SNAPSHOT_CONFIG" else record[5]
            return raw, record[6]

        with mock.patch.multiple(_CALLER, **overrides), \
                mock.patch.object(_CALLER, "_read_with_reader",
                                  side_effect=read_stale_input), \
                mock.patch.object(_CALLER, "_require_same_input_document",
                                  side_effect=compare):
            self._assert_code(
                "HISTORICAL_SNAPSHOT_CONFIG_BYTES_CHANGED_AFTER_WRITES",
                _CALLER._recheck_historical_tool_inputs, object(), original_inputs)

    def test_stale_historical_inputs_stop_public_qualify_before_attempt_and_roles(self):
        events = []
        historical_inputs = (("SYNTHETIC_ONLY",),)
        snapshot = {"snapshotSha256": _CALLER.HISTORICAL_TOOL_SNAPSHOT_SHA256}
        manifest_raw = b"SYNTHETIC_CURRENT_SOURCE_MANIFEST"
        journal_raw = b"SYNTHETIC_PHASE_JOURNAL"
        manifest_value = {"purpose": "SYNTHETIC_ONLY"}
        journal_value = {"status": "SYNTHETIC_ONLY"}
        config = {"dataOutputRoot": "C:\\fixture\\output",
                  "scratchRoot": "C:\\fixture\\scratch",
                  "sourceSpecs": {"caller": {"sha256": "0" * 64}},
                  "roots": {"N": "C:\\fixture\\N", "D": "C:\\fixture\\D"}}
        state = {
            "sourceManifestBytes": manifest_raw,
            "sourceManifest": manifest_value,
            "rootsSha256": "d" * 64,
        }

        def read_config(*_args):
            events.append("current-preflight")
            return b"SYNTHETIC_CONFIG", config, object(), object(), object()

        def make_preflight_state(*_args):
            events.append("preflight-state")
            return state

        def current_guard(_state):
            events.append("unchanged-current-guard")

        def read_historical(_reader, **_paths):
            events.append("historical-inputs")
            self.assertEqual(_paths, {
                "historical_config_path": "C:\\fixture\\historical\\config.json",
                "historical_source_manifest_path": (
                    "C:\\fixture\\historical\\source-manifest.json"),
                "historical_preflight_path": (
                    "C:\\fixture\\historical\\preflight.json"),
            })
            return snapshot, historical_inputs

        def validate_expectations(_snapshot):
            events.append("historical-expectations")
            return _synthetic_tool_facts("expected", "d" * 64)

        def read_document(_reader, _path, _maximum, _expected_sha, role, _phase):
            events.append(role)
            if role == "SOURCE_MANIFEST":
                return manifest_raw, manifest_value, ("SYNTHETIC_MANIFEST_TOKEN",)
            return journal_raw, journal_value, ("SYNTHETIC_JOURNAL_TOKEN",)

        def reject_stale(_reader, _original_inputs):
            events.append("pre-read-snapshot-recheck")
            raise _CALLER.CallerError(
                "HISTORICAL_SNAPSHOT_CONFIG_BYTES_CHANGED_AFTER_WRITES",
                "historical-tool-snapshot-recheck")

        with mock.patch.object(_CALLER, "_read_config_and_runtime",
                               side_effect=read_config), \
                mock.patch.object(_CALLER, "_preflight_state",
                                  side_effect=make_preflight_state), \
                mock.patch.object(_CALLER, "_validate_prior_tool_compatibility",
                                  side_effect=current_guard), \
                mock.patch.object(_CALLER, "_read_historical_tool_inputs",
                                  side_effect=read_historical), \
                mock.patch.object(_CALLER, "_historical_tool_expectations",
                                  side_effect=validate_expectations), \
                mock.patch.object(_CALLER, "_read_authenticated_document",
                                  side_effect=read_document), \
                mock.patch.object(_CALLER, "_manifest_matches"), \
                mock.patch.object(_CALLER, "_validate_phase_journal"), \
                mock.patch.object(_CALLER, "_verify_root_bindings"), \
                mock.patch.object(_CALLER, "_directory_empty"), \
                mock.patch.object(_CALLER, "_recheck_historical_tool_inputs",
                                  side_effect=reject_stale), \
                mock.patch.object(_CALLER, "_attempt_record") as attempt_record, \
                mock.patch.object(_CALLER, "_write_exclusive") as output_write, \
                mock.patch.object(_CALLER, "_read_fixed_roles_after_journal") as role_read:
            self._assert_code(
                "HISTORICAL_SNAPSHOT_CONFIG_BYTES_CHANGED_AFTER_WRITES",
                _CALLER.qualify,
                config_path="C:\\fixture\\config.json", config_sha="a" * 64,
                source_manifest_path="C:\\fixture\\manifest.json",
                source_manifest_sha=_CALLER._sha(manifest_raw),
                phase_journal_path="C:\\fixture\\journal.json",
                phase_journal_sha=_CALLER._sha(journal_raw),
                historical_config_path="C:\\fixture\\historical\\config.json",
                historical_source_manifest_path=(
                    "C:\\fixture\\historical\\source-manifest.json"),
                historical_preflight_path="C:\\fixture\\historical\\preflight.json")

        self.assertEqual(events, [
            "current-preflight", "preflight-state", "unchanged-current-guard",
            "historical-inputs", "historical-expectations",
            "SOURCE_MANIFEST", "PHASE_JOURNAL",
            "pre-read-snapshot-recheck",
        ])
        attempt_record.assert_not_called()
        output_write.assert_not_called()
        role_read.assert_not_called()

    def test_final_consumed_input_recheck_includes_historical_tokens(self):
        reader = object()
        observer = object()
        config = {"sourceSpecs": {"caller": {"sha256": "0" * 64}}}
        state = {"rootsSha256": "d" * 64, "configToken": ("SYNTHETIC_CONFIG",),
                 "byteObservation": {"SYNTHETIC_ONLY": True},
                 "modules": {"normalizer": object()}}
        manifest_raw = b"SYNTHETIC_SOURCE_MANIFEST"
        journal_raw = b"SYNTHETIC_PHASE_JOURNAL"
        manifest_value = {"source": "SYNTHETIC_ONLY"}
        journal_value = {"journal": "SYNTHETIC_ONLY"}
        state["sourceManifest"] = manifest_value
        manifest_token = ("SYNTHETIC_MANIFEST_TOKEN",)
        journal_token = ("SYNTHETIC_JOURNAL_TOKEN",)
        historical_inputs = (("SYNTHETIC_HISTORICAL_INPUTS",),)
        role_bytes = {"SYNTHETIC_ROLE": b"SYNTHETIC_ROLE_BYTES"}
        reader_evidence = {"scope": "SYNTHETIC_ONLY"}
        resource_facts = {"resource": "SYNTHETIC_ONLY"}
        resource_evidence = {"evidence": "SYNTHETIC_ONLY"}

        def read_document(_reader, _path, _maximum, _sha, role, _phase):
            if role == "SOURCE_MANIFEST":
                return manifest_raw, manifest_value, manifest_token
            return journal_raw, journal_value, journal_token

        with mock.patch.object(_CALLER, "_verify_root_bindings"), \
                mock.patch.object(_CALLER, "_read_with_reader",
                                  return_value=(b"SYNTHETIC_CONFIG", state["configToken"])), \
                mock.patch.object(_CALLER, "_require_same_input_document"), \
                mock.patch.object(_CALLER, "_read_authenticated_document",
                                  side_effect=read_document), \
                mock.patch.object(_CALLER, "_validate_phase_journal"), \
                mock.patch.object(_CALLER, "_recheck_historical_tool_inputs") as history_recheck, \
                mock.patch.object(_CALLER, "_observe_source_tools",
                                  return_value=state["byteObservation"]), \
                mock.patch.object(_CALLER, "_observe_resources",
                                  return_value=(resource_facts, resource_evidence)), \
                mock.patch.object(_CALLER, "_read_fixed_roles_after_journal",
                                  return_value=(role_bytes, reader_evidence)):
            _CALLER._recheck_consumed_inputs(
                reader, observer, config, state,
                config_path="C:\\fixture\\config.json",
                config_raw=b"SYNTHETIC_CONFIG", config_sha="a" * 64,
                manifest_path="C:\\fixture\\manifest.json",
                manifest_sha="b" * 64, manifest_raw=manifest_raw,
                manifest_value=manifest_value, manifest_token=manifest_token,
                journal_path="C:\\fixture\\journal.json",
                journal_sha="c" * 64, journal_raw=journal_raw,
                journal_value=journal_value, journal_token=journal_token,
                historical_inputs=historical_inputs,
                role_bytes=role_bytes, reader_evidence=reader_evidence,
                resource_facts=resource_facts, resource_evidence=resource_evidence,
                read_started_state={"value": True})
        history_recheck.assert_called_once_with(reader, historical_inputs)

    def test_attempt_and_failure_records_bind_fixed_snapshot_digest_v2(self):
        snapshot_sha = _CALLER.HISTORICAL_TOOL_SNAPSHOT_SHA256
        attempt = _CALLER._attempt_record(
            "a" * 64, "b" * 64, "c" * 64, "d" * 64,
            historical_snapshot_sha=snapshot_sha)
        failure = _CALLER._failure_record(
            "SYNTHETIC_FAILURE", "synthetic-phase", "a" * 64, "b" * 64,
            "c" * 64, "d" * 64, False,
            historical_snapshot_sha=snapshot_sha)
        self.assertEqual(attempt["schema"], "tsj-q100-normal-prior-read-attempt-v2")
        self.assertEqual(failure["schema"], "tsj-q100-normal-prior-qualification-evidence-v2")
        self.assertEqual(attempt["historicalToolSnapshotSha256"], snapshot_sha)
        self.assertEqual(failure["historicalToolSnapshotSha256"], snapshot_sha)
        for bad_sha in ("A" * 64, "0" * 64, None):
            with self.subTest(bad_sha=bad_sha):
                self._assert_code(
                    "HISTORICAL_TOOL_SNAPSHOT_SHA256", _CALLER._attempt_record,
                    "a" * 64, "b" * 64, "c" * 64, "d" * 64,
                    historical_snapshot_sha=bad_sha)
                self._assert_code(
                    "HISTORICAL_TOOL_SNAPSHOT_SHA256", _CALLER._failure_record,
                    "SYNTHETIC_FAILURE", "synthetic-phase", "a" * 64, "b" * 64,
                    "c" * 64, "d" * 64, False,
                    historical_snapshot_sha=bad_sha)

    def test_qualify_cli_requires_three_historical_metadata_paths(self):
        base = [
            "qualify",
            "--config", "C:\\fixture\\config.json",
            "--config-sha", "a" * 64,
            "--source-manifest", "C:\\fixture\\manifest.json",
            "--source-manifest-sha", "b" * 64,
            "--phase-journal", "C:\\fixture\\journal.json",
            "--phase-journal-sha", "c" * 64,
        ]
        historical = [
            "--historical-config", "C:\\fixture\\historical\\config.json",
            "--historical-source-manifest",
            "C:\\fixture\\historical\\source-manifest.json",
            "--historical-preflight", "C:\\fixture\\historical\\preflight.json",
        ]
        parsed = _CALLER._parse_cli(base + historical)
        self.assertEqual(parsed.historical_config, historical[1])
        self.assertEqual(parsed.historical_source_manifest, historical[3])
        self.assertEqual(parsed.historical_preflight, historical[5])

        for omitted in ("--historical-config", "--historical-source-manifest",
                        "--historical-preflight"):
            with self.subTest(omitted=omitted):
                args = list(base + historical)
                index = args.index(omitted)
                del args[index:index + 2]
                with contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        _CALLER._parse_cli(args)

        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                _CALLER._parse_cli(base + historical + ["--historical-tool-snapshot", "{}"])

    def test_qualify_requires_three_historical_paths_before_any_io(self):
        with mock.patch.object(_CALLER, "_read_config_and_runtime") as read_config:
            with self.assertRaises(TypeError):
                _CALLER.qualify(
                    config_path="C:\\fixture\\config.json", config_sha="a" * 64,
                    source_manifest_path="C:\\fixture\\manifest.json",
                    source_manifest_sha="b" * 64,
                    phase_journal_path="C:\\fixture\\journal.json",
                    phase_journal_sha="c" * 64)
        read_config.assert_not_called()

    def test_qualify_rejects_retired_dictionary_argument_before_any_io(self):
        with mock.patch.object(_CALLER, "_read_config_and_runtime") as read_config:
            with self.assertRaises(TypeError):
                _CALLER.qualify(
                    config_path="C:\\fixture\\config.json", config_sha="a" * 64,
                    source_manifest_path="C:\\fixture\\manifest.json",
                    source_manifest_sha="b" * 64,
                    phase_journal_path="C:\\fixture\\journal.json",
                    phase_journal_sha="c" * 64,
                    historical_config_path="C:\\fixture\\historical\\config.json",
                    historical_source_manifest_path=(
                        "C:\\fixture\\historical\\source-manifest.json"),
                    historical_preflight_path=(
                        "C:\\fixture\\historical\\preflight.json"),
                    historical_tool_snapshot={})
        read_config.assert_not_called()

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
            "3" * 64, self.tree.roots_sha, True,
            historical_snapshot_sha=_CALLER.HISTORICAL_TOOL_SNAPSHOT_SHA256)
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
