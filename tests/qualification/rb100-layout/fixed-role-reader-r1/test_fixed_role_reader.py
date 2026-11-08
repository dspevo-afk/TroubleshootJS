"""Source tests for the fixed-role byte reader; no oracle DATA is opened."""

import hashlib
import json
import os
import shutil
import stat
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock


_READER_SOURCE = Path(__file__).with_name("fixed_role_reader.py")
_READER_MODULE = types.ModuleType("fixed_role_reader")
_READER_MODULE.__file__ = str(_READER_SOURCE)
sys.modules[_READER_MODULE.__name__] = _READER_MODULE
exec(compile(_READER_SOURCE.read_bytes(), str(_READER_SOURCE), "exec"),
     _READER_MODULE.__dict__)
reader = _READER_MODULE


class _OwnedTempDirectory:
    """Unique OS-temp tree, removed only after ownership and safety checks."""

    def __enter__(self):
        self.path = Path(tempfile.mkdtemp(prefix="fixed-role-reader-test-")).resolve(strict=True)
        self.temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
        try:
            common = os.path.normcase(os.path.commonpath((str(self.temp_root), str(self.path))))
        except (OSError, ValueError):
            raise RuntimeError("temporary directory escaped OS temp") from None
        if common != os.path.normcase(str(self.temp_root)):
            raise RuntimeError("temporary directory escaped OS temp")
        _path, _real, identity = reader._inspect_path(str(self.path), "directory")
        self.object_identity = tuple(identity[-1][1][index] for index in (0, 1, 2, 6))
        return self.path

    def __exit__(self, exc_type, _exc, _traceback):
        # Preserve a failing fixture for diagnosis. On success, validate the
        # exact owned tree and every descendant before recursive cleanup.
        if (exc_type is not None and
                not issubclass(exc_type, unittest.SkipTest)):
            return False
        path, real, identity = reader._inspect_path(str(self.path), "directory")
        current_identity = tuple(identity[-1][1][index] for index in (0, 1, 2, 6))
        if (os.path.normcase(str(path.resolve(strict=True))) != os.path.normcase(str(self.path)) or
                os.path.normcase(str(real)) != os.path.normcase(str(self.path)) or
                current_identity != self.object_identity):
            raise RuntimeError("temporary directory identity changed")
        for current, directories, files in os.walk(str(self.path), followlinks=False):
            for name in directories + files:
                item_path = Path(current) / name
                try:
                    common = os.path.normcase(os.path.commonpath((
                        str(self.path), str(item_path.absolute()))))
                    item_stat = os.lstat(str(item_path))
                except (OSError, ValueError):
                    raise RuntimeError("temporary tree changed before cleanup") from None
                if (common != os.path.normcase(str(self.path)) or
                        stat.S_ISLNK(item_stat.st_mode) or
                        bool(getattr(item_stat, "st_file_attributes", 0) & reader.REPARSE_POINT)):
                    raise RuntimeError("temporary tree contains an unsafe path")
        shutil.rmtree(str(self.path))
        return False


def _owned_temp_directory():
    return _OwnedTempDirectory()


def _roots(temp_root):
    n_root = temp_root / "N"
    d_root = temp_root / "D"
    n_root.mkdir()
    d_root.mkdir()
    return {"N": str(n_root), "D": str(d_root)}, n_root, d_root


def _row(role="N_PRIOR_OPERATION1", path="fixture.bin", *, root="N", limit=32,
         expected=5, digest=None):
    data = b"alpha"
    if digest is None:
        digest = hashlib.sha256(data).hexdigest()
    return {
        "role": role,
        "root": root,
        "path": path,
        "maxBytes": limit,
        "expectedBytes": expected,
        "sha256": digest,
    }


class FixedRoleReaderTests(unittest.TestCase):
    def test_synthetic_read_is_bounded_and_never_follows_manifest_rows(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            fixture = n_root / "fixture.bin"
            fixture.write_bytes(b"alpha")
            outside = temp_root / "outside.bin"
            outside.write_bytes(b"secret")
            (n_root / "manifest.json").write_text(
                json.dumps({"path": str(outside)}), encoding="ascii")

            opened = []
            original = reader._read_file_once

            def track_reads(path, *args, **kwargs):
                opened.append(os.path.normcase(os.path.abspath(str(path))))
                return original(path, *args, **kwargs)

            with mock.patch.object(reader, "_read_file_once", side_effect=track_reads):
                data, evidence = reader._read_synthetic_roles(
                    roots=roots, rows=[_row()], synthetic_mode="SYNTHETIC")

            self.assertEqual(data, {"N_PRIOR_OPERATION1": b"alpha"})
            self.assertEqual(evidence["scope"], "SYNTHETIC")
            self.assertEqual(evidence["roleCount"], 1)
            self.assertEqual(evidence["semanticValidation"], "NOT_PERFORMED")
            self.assertEqual(len(opened), 2)
            self.assertEqual(set(opened), {os.path.normcase(os.path.abspath(str(fixture)))})
            self.assertNotIn(str(temp_root), repr(evidence))

    def test_sha_size_maximum_and_zero_role_guards(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            fixture = n_root / "fixture.bin"
            fixture.write_bytes(b"alpha")

            with self.assertRaises(reader.ReaderError) as bad_sha:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(digest="0" * 64)], synthetic_mode="SYNTHETIC")
            self.assertEqual(bad_sha.exception.code, "SHA256")

            with self.assertRaises(reader.ReaderError) as wrong_length:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(expected=6)], synthetic_mode="SYNTHETIC")
            self.assertEqual(wrong_length.exception.code, "EXPECTED_BYTES")

            with self.assertRaises(reader.ReaderError) as over_maximum:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(limit=4, expected=None)], synthetic_mode="SYNTHETIC")
            self.assertEqual(over_maximum.exception.code, "SIZE")

            with self.assertRaises(reader.ReaderError) as bool_limit:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(limit=True)], synthetic_mode="SYNTHETIC")
            self.assertEqual(bool_limit.exception.code, "SIZE")

            with self.assertRaises(reader.ReaderError) as bool_expected:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(expected=True)], synthetic_mode="SYNTHETIC")
            self.assertEqual(bool_expected.exception.code, "EXPECTED_BYTES")

            empty_file = n_root / "empty.bin"
            empty_file.write_bytes(b"")
            zero_row = _row(role="N_PRIOR_CALL_STDERR_TXT", path="empty.bin", expected=0,
                            digest=reader.EMPTY_SHA256)
            data, evidence = reader._read_synthetic_roles(
                roots=roots, rows=[zero_row], synthetic_mode="SYNTHETIC")
            self.assertEqual(data, {"N_PRIOR_CALL_STDERR_TXT": b""})
            self.assertEqual(evidence["scope"], "SYNTHETIC")

            forbidden_zero_row = _row(expected=0, digest=reader.EMPTY_SHA256)
            with self.assertRaises(reader.ReaderError) as forbidden_zero:
                reader._read_synthetic_roles(
                    roots=roots, rows=[forbidden_zero_row], synthetic_mode="SYNTHETIC")
            self.assertEqual(forbidden_zero.exception.code, "ZERO_ROLE")

    def test_unknown_role_extra_root_noncanonical_root_and_path_escape_reject(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            (n_root / "fixture.bin").write_bytes(b"alpha")

            unknown = _row(role="UNLISTED_ROLE")
            with self.assertRaises(reader.ReaderError) as unknown_role:
                reader._read_synthetic_roles(
                    roots=roots, rows=[unknown], synthetic_mode="SYNTHETIC")
            self.assertEqual(unknown_role.exception.code, "UNKNOWN_ROLE")

            with self.assertRaises(reader.ReaderError) as extra_root:
                reader._read_synthetic_roles(
                    roots={**roots, "X": str(temp_root)}, rows=[_row()],
                    synthetic_mode="SYNTHETIC")
            self.assertEqual(extra_root.exception.code, "ROOTS")

            noncanonical = dict(roots)
            noncanonical["N"] = str(n_root) + "\\."
            with self.assertRaises(reader.ReaderError) as bad_root:
                reader._read_synthetic_roles(
                    roots=noncanonical, rows=[_row()], synthetic_mode="SYNTHETIC")
            self.assertEqual(bad_root.exception.code, "NONCANONICAL")

            with self.assertRaises(reader.ReaderError) as escaped:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row(path="../outside.bin")],
                    synthetic_mode="SYNTHETIC")
            self.assertEqual(escaped.exception.code, "ROLE_PATH")

    def test_reparse_ancestor_rejects_when_symlink_creation_is_available(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            target_dir = temp_root / "target"
            target_dir.mkdir()
            (target_dir / "fixture.bin").write_bytes(b"alpha")
            alias = n_root / "alias"
            try:
                os.symlink(str(target_dir), str(alias), target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("directory symlink creation is unavailable")
            link_stat = os.lstat(str(alias))
            link_identity = (
                link_stat.st_dev, link_stat.st_ino, link_stat.st_mode, link_stat.st_size,
                getattr(link_stat, "st_mtime_ns", int(link_stat.st_mtime * 1000000000)),
                getattr(link_stat, "st_ctime_ns", int(link_stat.st_ctime * 1000000000)),
                getattr(link_stat, "st_file_attributes", 0),
            )
            link_target = os.path.normcase(str(alias.resolve(strict=True)))
            try:
                with self.assertRaises(reader.ReaderError) as reparse:
                    reader._read_synthetic_roles(
                        roots=roots, rows=[_row(path="alias/fixture.bin")],
                        synthetic_mode="SYNTHETIC")
                self.assertEqual(reparse.exception.code, "REPARSE")
            finally:
                if os.path.lexists(str(alias)):
                    current = os.lstat(str(alias))
                    current_identity = (
                        current.st_dev, current.st_ino, current.st_mode, current.st_size,
                        getattr(current, "st_mtime_ns", int(current.st_mtime * 1000000000)),
                        getattr(current, "st_ctime_ns", int(current.st_ctime * 1000000000)),
                        getattr(current, "st_file_attributes", 0),
                    )
                    if (current_identity != link_identity or
                            os.path.normcase(str(alias.resolve(strict=True))) != link_target):
                        raise RuntimeError("test symlink identity changed before removal")
                    os.rmdir(str(alias))

    def test_file_changed_between_read_and_final_recheck_rejects(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            fixture = n_root / "fixture.bin"
            fixture.write_bytes(b"alpha")
            original = reader._read_file_once
            count = [0]

            def replace_after_first_read(path, *args, **kwargs):
                result = original(path, *args, **kwargs)
                count[0] += 1
                if count[0] == 1:
                    original_mtime = fixture.stat().st_mtime_ns
                    fixture.write_bytes(b"bravo")
                    os.utime(str(fixture), ns=(fixture.stat().st_atime_ns, original_mtime))
                return result

            with mock.patch.object(reader, "_read_file_once", side_effect=replace_after_first_read):
                with self.assertRaises(reader.ReaderError) as changed:
                    reader._read_synthetic_roles(
                        roots=roots, rows=[_row()], synthetic_mode="SYNTHETIC")
            self.assertIn(changed.exception.code, ("SHA256", "FILE_CHANGED"))

    def test_pinned_contract_fixture_is_exact_and_contract_mutations_reject(self):
        raw = reader._expected_allowlist_bytes_for_test_fixture()
        self.assertEqual(hashlib.sha256(raw).hexdigest(), reader.ALLOWLIST_SHA256)
        with _owned_temp_directory() as temp_root:
            contract = temp_root / "NORMAL_ORACLE_ALLOWLIST_R1.json"
            contract.write_bytes(raw)
            specs, evidence = reader._load_pinned_allowlist(str(contract))
            self.assertEqual(len(specs), 20)
            self.assertEqual(evidence["scope"], "PINNED_ALLOWLIST_METADATA_ONLY")
            self.assertEqual(evidence["allowlistSha256"], reader.ALLOWLIST_SHA256)

            mutated = json.loads(raw.decode("ascii"))
            mutated["rows"].append(dict(mutated["rows"][-1]))
            contract.write_text(json.dumps(mutated, ensure_ascii=True, indent=2) + "\n",
                                encoding="ascii")
            with self.assertRaises(reader.ReaderError) as extra_row:
                reader._load_pinned_allowlist(str(contract))
            self.assertEqual(extra_row.exception.code, "CONTRACT_SHA256")

            mutated = json.loads(raw.decode("ascii"))
            mutated["rows"].pop()
            contract.write_text(json.dumps(mutated, ensure_ascii=True, indent=2) + "\n",
                                encoding="ascii")
            with self.assertRaises(reader.ReaderError) as missing_row:
                reader._load_pinned_allowlist(str(contract))
            self.assertEqual(missing_row.exception.code, "CONTRACT_SHA256")

    def test_synthetic_mode_is_mandatory_and_cannot_claim_normal_admission(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            (n_root / "fixture.bin").write_bytes(b"alpha")
            with self.assertRaises(reader.ReaderError) as not_synthetic:
                reader._read_synthetic_roles(
                    roots=roots, rows=[_row()], synthetic_mode="NORMAL")
            self.assertEqual(not_synthetic.exception.code, "SYNTHETIC_MODE")

            _data, evidence = reader._read_synthetic_roles(
                roots=roots, rows=[_row()], synthetic_mode="SYNTHETIC")
            self.assertEqual(evidence["scope"], "SYNTHETIC")
            self.assertEqual(evidence["qualification"], "NOT_CLAIMED")
            self.assertEqual(evidence["fullD"], "NOT_CLAIMED")


if __name__ == "__main__":
    unittest.main()
