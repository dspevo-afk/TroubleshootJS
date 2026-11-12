"""Actual-reader tests for authenticated historical metadata transport only.

No role data, qualification, or normalizer operation is in this test surface.
The snapshot paths are supplied explicitly by the gate owner.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import stat
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock


_READER_SHA256 = "863a258cbab888cf112b9e927e16a099436632e9a1d43eefe0b70690c30543d8"
_SNAPSHOT_SHA256 = "a6cdc79ba35bc4f65e462a81ebd35b7d97260580fed5d27b75b506a9a02cc9e3"
_SNAPSHOT_SCHEMA = "tsj-q100-source-tool-snapshot-metadata-v1"
_METADATA = (
    ("HISTORICAL_SNAPSHOT_CONFIG", "snapshot-config.json", 1024 * 1024,
     4894, "c8e937946f93f62801450eb989e73016d26736d53e1d2487cb8d8843f0add6eb",
     "HISTORICAL_SNAPSHOT_CONFIG_SHA256"),
    ("HISTORICAL_SNAPSHOT_SOURCE_MANIFEST", "snapshot-source-manifest.json",
     1024 * 1024, 2124,
     "e5ea5ac6bd85ffdb0be078895e87b1da4d3d9488469029d8971751ec4846e80d",
     "HISTORICAL_SNAPSHOT_SOURCE_MANIFEST_SHA256"),
    ("HISTORICAL_SNAPSHOT_PREFLIGHT", "snapshot-preflight.json", 64 * 1024,
     1595, "8244eb5c9e5e2ee6c78ed305262dad46c4a0c5857202d3e6c6d9fa6f99241837",
     "HISTORICAL_SNAPSHOT_PREFLIGHT_SHA256"),
)
_CONTEXT = None
_RETAINED_SCRATCH_COUNT = 0


def _load_caller_source(path, expected_sha256):
    """Load only the explicitly SHA-pinned source bytes, with a 4 MiB cap.

    This bootstrap binds the executed buffer to the supplied digest; it does
    not claim source-path, ancestor, or host-path closure.
    """
    if type(expected_sha256) is not str or not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise RuntimeError("CALLER_SOURCE_SHA_FORMAT")
    try:
        with open(os.fspath(path), "rb") as stream:
            raw = stream.read(4 * 1024 * 1024 + 1)
    except OSError:
        raise RuntimeError("CALLER_SOURCE_READ") from None
    if not raw or len(raw) > 4 * 1024 * 1024:
        raise RuntimeError("CALLER_SOURCE_SIZE")
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise RuntimeError("CALLER_SOURCE_SHA")
    module_name = "_tsj_authenticated_historical_transport_caller"
    module = types.ModuleType(module_name)
    module.__file__ = os.path.abspath(os.fspath(path))
    module.__package__ = ""
    sys.modules[module_name] = module
    try:
        code = compile(raw, module.__file__, "exec", dont_inherit=True)
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(module_name, None)
        raise RuntimeError("CALLER_SOURCE_LOAD") from None
    return module


def _parse_args(argv=None):
    parser = argparse.ArgumentParser(prog="test_historical_input_transport.py")
    parser.add_argument("--caller-source", required=True)
    parser.add_argument("--caller-sha", required=True)
    parser.add_argument("--reader-source", required=True)
    parser.add_argument("--snapshot-config", required=True)
    parser.add_argument("--snapshot-source-manifest", required=True)
    parser.add_argument("--snapshot-preflight", required=True)
    parser.add_argument("--scratch-root")
    return parser.parse_args(argv)


def _load_context(args):
    caller = _load_caller_source(args.caller_source, args.caller_sha)
    caller._assert_module_functions(
        caller, ("_read_historical_tool_inputs", "_recheck_historical_tool_inputs"),
        "historical-input-test-caller")
    if caller.READER_SHA256 != _READER_SHA256:
        raise RuntimeError("READER_SOURCE_EXPECTATION")
    reader_raw = caller._bootstrap_read(
        args.reader_source, caller.MAX_SOURCE_BYTES, "historical-input-test-reader")
    if hashlib.sha256(reader_raw).hexdigest() != _READER_SHA256:
        raise RuntimeError("READER_SOURCE_SHA")
    reader = caller._execute_authenticated_source(
        "historical-input-test-reader", args.reader_source, reader_raw,
        "_tsj_authenticated_historical_transport_reader", "historical-input-test-reader")
    caller._assert_module_functions(
        reader, ("_token_identity", "_read_file_once", "_check_windows_local_lexical_path"),
        "historical-input-test-reader")
    if args.scratch_root is None:
        scratch_root = tempfile.gettempdir()
    else:
        scratch_root = args.scratch_root
    scratch_root = os.path.abspath(os.fspath(scratch_root))
    try:
        scratch_stat = os.lstat(scratch_root)
    except OSError:
        raise RuntimeError("SCRATCH_ROOT") from None
    if (not stat.S_ISDIR(scratch_stat.st_mode) or stat.S_ISLNK(scratch_stat.st_mode)
            or getattr(scratch_stat, "st_file_attributes", 0)
            & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
        raise RuntimeError("SCRATCH_ROOT")
    input_paths = {
        "historical_config_path": args.snapshot_config,
        "historical_source_manifest_path": args.snapshot_source_manifest,
        "historical_preflight_path": args.snapshot_preflight,
    }
    return caller, reader, input_paths, scratch_root


class HistoricalInputTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if _CONTEXT is None:
            raise RuntimeError("TEST_CONTEXT_UNAVAILABLE")
        cls.caller, cls.reader, cls.input_paths, cls.scratch_root = _CONTEXT
        (cls.snapshot, cls.original_inputs) = cls.caller._read_historical_tool_inputs(
            cls.reader, **cls.input_paths)

    def _assert_originals_stable(self):
        self.assertIsNone(self.caller._recheck_historical_tool_inputs(
            self.reader, self.original_inputs))

    def _owned_scratch(self, prefix):
        global _RETAINED_SCRATCH_COUNT
        path = tempfile.mkdtemp(prefix=prefix, dir=self.scratch_root)
        path = os.path.abspath(path)
        try:
            value = os.lstat(path)
            common = os.path.normcase(os.path.commonpath((self.scratch_root, path)))
        except (OSError, ValueError):
            raise RuntimeError("OWNED_SCRATCH_BINDING") from None
        if (common != os.path.normcase(self.scratch_root)
                or os.path.normcase(path) == os.path.normcase(self.scratch_root)
                or not stat.S_ISDIR(value.st_mode)
                or stat.S_ISLNK(value.st_mode)
                or getattr(value, "st_file_attributes", 0)
                & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
            raise RuntimeError("OWNED_SCRATCH_BINDING")
        _RETAINED_SCRATCH_COUNT += 1
        return path

    def _copy_originals(self, directory):
        paths = []
        for item, expected in zip(self.original_inputs, _METADATA):
            path = Path(directory) / expected[1]
            path.write_bytes(item[5])
            paths.append(str(path))
        return {
            "historical_config_path": paths[0],
            "historical_source_manifest_path": paths[1],
            "historical_preflight_path": paths[2],
        }, paths

    def test_fixed_snapshot_is_non_authorizing_and_original_tokens_recheck(self):
        self.assertEqual(self.snapshot["schema"], _SNAPSHOT_SCHEMA)
        self.assertEqual(self.snapshot["snapshotSha256"], _SNAPSHOT_SHA256)
        self.assertEqual(
            set(self.snapshot),
            set(self.caller.HISTORICAL_TOOL_SNAPSHOT_FIELDS + ("snapshotSha256",)))
        for field in (
                "captureReceiptBindingEstablished", "priorRunBindingEstablished",
                "fixedRoleReadAuthorized", "priorQualificationEstablished"):
            self.assertIs(self.snapshot[field], False)

        self.assertIs(type(self.original_inputs), tuple)
        self.assertEqual(len(self.original_inputs), 3)
        self.assertIsNone(self.caller._recheck_historical_tool_inputs(
            self.reader, self.original_inputs))
        path_names = ("historical_config_path", "historical_source_manifest_path",
                      "historical_preflight_path")
        for index, (item, expected) in enumerate(zip(self.original_inputs, _METADATA)):
            role, _filename, maximum, byte_count, digest, _error = expected
            self.assertTrue(type(item) is tuple and len(item) == 7,
                            "input record shape")
            expected_path = self.input_paths[path_names[index]]
            self.assertTrue(
                os.path.normcase(os.path.abspath(item[0]))
                == os.path.normcase(os.path.abspath(expected_path)),
                "input path binding")
            self.assertEqual(item[1], maximum)
            self.assertEqual(item[2], byte_count)
            self.assertEqual(item[3], digest)
            self.assertEqual(item[4], role)
            self.assertTrue(type(item[5]) is bytes, "authenticated input bytes")
            self.assertEqual(len(item[5]), byte_count)
            self.assertEqual(hashlib.sha256(item[5]).hexdigest(), digest)
            token = item[6]
            self.assertTrue(type(token) is tuple and len(token) == 4,
                            "original reader token shape")
            self.assertTrue(type(token[2]) is tuple and type(token[3]) is tuple
                            and token[2] == token[3], "original handle token stability")

    def test_all_three_authenticated_reads_precede_metadata_decode(self):
        events = []
        read_with_reader = self.caller._read_with_reader
        strict_json = self.caller._strict_json
        strict_json_with_floats = self.caller._strict_json_with_finite_floats

        def observed_read(*args, **kwargs):
            result = read_with_reader(*args, **kwargs)
            events.append(("read", args[5], args[2], args[3], args[4]))
            return result

        def observed_json(*args, **kwargs):
            events.append(("decode", "json"))
            return strict_json(*args, **kwargs)

        def observed_json_with_floats(*args, **kwargs):
            events.append(("decode", "finite-json"))
            return strict_json_with_floats(*args, **kwargs)

        with mock.patch.object(self.caller, "_read_with_reader",
                               side_effect=observed_read), \
                mock.patch.object(self.caller, "_strict_json",
                                  side_effect=observed_json), \
                mock.patch.object(self.caller, "_strict_json_with_finite_floats",
                                  side_effect=observed_json_with_floats):
            snapshot, original_inputs = self.caller._read_historical_tool_inputs(
                self.reader, **self.input_paths)

        self.assertEqual(snapshot["snapshotSha256"], _SNAPSHOT_SHA256)
        self.assertEqual(
            [event[0] for event in events], ["read", "read", "read", "decode", "decode", "decode"])
        self.assertEqual(events[:3], [
            ("read", role, maximum, byte_count, digest)
            for role, _filename, maximum, byte_count, digest, _error in _METADATA
        ])
        self.assertIsNone(self.caller._recheck_historical_tool_inputs(
            self.reader, original_inputs))
        self._assert_originals_stable()

    def test_each_wrong_metadata_sha_is_typed_before_json_decode(self):
        for index, expected in enumerate(_METADATA):
            role, _filename, _maximum, _byte_count, _digest, error_code = expected
            with self.subTest(role=role):
                directory = self._owned_scratch("tsj-historical-input-bad-sha-")
                paths, ordered_paths = self._copy_originals(directory)
                original_raw = self.original_inputs[index][5]
                changed = bytearray(original_raw)
                changed[0] ^= 1
                Path(ordered_paths[index]).write_bytes(changed)
                with mock.patch.object(
                        self.caller, "_strict_json",
                        wraps=self.caller._strict_json) as json_decode, \
                        mock.patch.object(
                            self.caller, "_strict_json_with_finite_floats",
                            wraps=self.caller._strict_json_with_finite_floats) as finite_json_decode:
                    with self.assertRaises(self.caller.CallerError) as caught:
                        self.caller._read_historical_tool_inputs(
                            self.reader, **paths)
                self.assertEqual(caught.exception.code, error_code)
                self.assertEqual(json_decode.call_count, 0)
                self.assertEqual(finite_json_decode.call_count, 0)
        self._assert_originals_stable()

    def test_same_bytes_file_identity_replacement_is_rejected_for_each_input(self):
        for index, expected in enumerate(_METADATA):
            role = expected[0]
            with self.subTest(role=role):
                directory = self._owned_scratch("tsj-historical-input-identity-")
                paths, ordered_paths = self._copy_originals(directory)
                _snapshot, copied_inputs = self.caller._read_historical_tool_inputs(
                    self.reader, **paths)
                target = Path(ordered_paths[index])
                replacement = target.with_name("replacement-" + str(index) + ".tmp")
                preserved = target.with_name("original-preserved-" + str(index) + ".json")
                replacement.write_bytes(copied_inputs[index][5])
                target.rename(preserved)
                os.replace(str(replacement), str(target))
                with self.assertRaises(self.caller.CallerError) as caught:
                    self.caller._recheck_historical_tool_inputs(
                        self.reader, copied_inputs)
                self.assertEqual(
                    caught.exception.code,
                    role + "_IDENTITY_CHANGED_AFTER_WRITES")
        self._assert_originals_stable()


def main(argv=None):
    if sys.platform != "win32" or os.name != "nt":
        print("HISTORICAL_INPUT_TEST_WINDOWS_REQUIRED")
        return 2
    if not sys.flags.isolated or not sys.dont_write_bytecode:
        print("HISTORICAL_INPUT_TEST_REQUIRES_I_B")
        return 2
    try:
        args = _parse_args(argv)
        global _CONTEXT
        _CONTEXT = _load_context(args)
    except SystemExit:
        raise
    except Exception as exc:
        code = getattr(exc, "code", None)
        if type(code) is str and re.fullmatch(r"[A-Z0-9_]{1,96}", code):
            print("HISTORICAL_INPUT_TEST_SETUP_" + code)
        else:
            print("HISTORICAL_INPUT_TEST_SETUP_FAILED")
        return 2
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(
        HistoricalInputTransportTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print("HISTORICAL_INPUT_TEST_RETAINED_SCRATCH_COUNT="
          + str(_RETAINED_SCRATCH_COUNT))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
