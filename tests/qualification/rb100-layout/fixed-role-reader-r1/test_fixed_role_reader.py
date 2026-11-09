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
    def test_handle_identity_ignores_permission_bits_but_keeps_file_identity(self):
        base_snapshot = (1, 2, stat.S_IFREG | 0o666, 5, 11, 17, 0x20)
        base_handle_identity = reader._handle_identity(base_snapshot)

        permission_changed = list(base_snapshot)
        permission_changed[2] = stat.S_IFREG | 0o777
        permission_changed = tuple(permission_changed)
        self.assertEqual(base_handle_identity, reader._handle_identity(permission_changed))
        # Path before/after comparison retains full mode equality.
        self.assertNotEqual(base_snapshot, permission_changed)

        changes = (
            (0, 3),
            (1, 4),
            (2, stat.S_IFDIR | 0o666),
            (3, 6),
            (4, 12),
            (6, 0x21),
        )
        for index, replacement in changes:
            changed = list(base_snapshot)
            changed[index] = replacement
            with self.subTest(snapshot_index=index):
                self.assertNotEqual(base_handle_identity,
                                    reader._handle_identity(tuple(changed)))

    def test_path_projection_ignores_only_ancestor_size_and_clocks(self):
        ancestor = os.path.abspath(os.path.join(os.sep, "reader-projection-parent"))
        leaf_path = os.path.join(ancestor, "fixture.bin")
        original = (
            (ancestor, (1, 10, stat.S_IFDIR | 0o755, 4096, 100, 200, 0x10)),
            (leaf_path, (1, 11, stat.S_IFREG | 0o644, 5, 300, 400, 0x20)),
        )
        changed_ancestor_clock_and_size = (
            (ancestor, (1, 10, stat.S_IFDIR | 0o755, 8192, 101, 201, 0x10)),
            original[-1],
        )
        self.assertEqual(reader._path_identity(original),
                         reader._path_identity(changed_ancestor_clock_and_size))

        ancestor_identity_changes = (
            (0, 2),
            (1, 12),
            (2, stat.S_IFDIR | 0o700),
            (6, 0x11),
        )
        for index, replacement in ancestor_identity_changes:
            changed = list(original)
            ancestor_snapshot = list(changed[0][1])
            ancestor_snapshot[index] = replacement
            changed[0] = (ancestor, tuple(ancestor_snapshot))
            with self.subTest(ancestor_snapshot_index=index):
                self.assertNotEqual(reader._path_identity(original),
                                    reader._path_identity(tuple(changed)))

        leaf_changes = (
            (0, 2),
            (1, 12),
            (2, stat.S_IFREG | 0o600),
            (3, 6),
            (4, 301),
            (5, 401),
            (6, 0x21),
        )
        for index, replacement in leaf_changes:
            changed = list(original)
            leaf_snapshot = list(changed[-1][1])
            leaf_snapshot[index] = replacement
            changed[-1] = (leaf_path, tuple(leaf_snapshot))
            with self.subTest(leaf_snapshot_index=index):
                self.assertNotEqual(reader._path_identity(original),
                                    reader._path_identity(tuple(changed)))
        changed_leaf_path = ((ancestor, original[0][1]),
                             (os.path.join(ancestor, "other.bin"), original[-1][1]))
        self.assertNotEqual(reader._path_identity(original),
                            reader._path_identity(changed_leaf_path))

        # When the root itself is the inspected object, its sole row is a leaf
        # and all seven snapshot fields remain significant.
        root_identity = ((ancestor, original[0][1]),)
        changed_root_clock = ((ancestor, changed_ancestor_clock_and_size[0][1]),)
        self.assertNotEqual(reader._path_identity(root_identity),
                            reader._path_identity(changed_root_clock))

    def test_token_projection_keeps_handle_identities_exact(self):
        parent = os.path.abspath(os.path.join(os.sep, "reader-token-parent"))
        leaf_path = os.path.join(parent, "fixture.bin")
        snapshots = (
            (parent, (1, 20, stat.S_IFDIR | 0o755, 4096, 100, 200, 0x10)),
            (leaf_path, (1, 21, stat.S_IFREG | 0o644, 5, 300, 400, 0x20)),
        )
        handle = (1, 21, stat.S_IFREG, 5, 300, 0x20)
        token = (os.path.normcase(leaf_path), snapshots, handle, handle)
        changed_parent_clock = (
            (parent, (1, 20, stat.S_IFDIR | 0o755, 8192, 101, 201, 0x10)),
            snapshots[-1],
        )
        equivalent = (os.path.normcase(leaf_path), changed_parent_clock, handle, handle)
        self.assertEqual(reader._token_identity(token), reader._token_identity(equivalent))

        changed_handle = (1, 22, stat.S_IFREG, 5, 300, 0x20)
        different = (os.path.normcase(leaf_path), changed_parent_clock, changed_handle, handle)
        self.assertNotEqual(reader._token_identity(token), reader._token_identity(different))
        different_after_handle = (os.path.normcase(leaf_path), changed_parent_clock,
                                  handle, changed_handle)
        self.assertNotEqual(reader._token_identity(token),
                            reader._token_identity(different_after_handle))

    def test_path_projection_rejects_malformed_raw_snapshots(self):
        malformed = (
            (),
            (("C:\\fixture", (1, 2, 3)),),
            ((None, (1, 2, 3, 4, 5, 6, 7)),),
        )
        for snapshots in malformed:
            with self.subTest(snapshots=snapshots):
                with self.assertRaises(reader.ReaderError) as invalid:
                    reader._path_identity(snapshots)
                self.assertEqual(invalid.exception.code, "FILE_CHANGED")

    @unittest.skipUnless(sys.platform == "win32" and os.name == "nt",
                         "Windows extension-inferred stat mode required")
    def test_windows_exe_fixture_is_read_as_ordinary_bytes_only(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            payload = b"synthetic fixture bytes; never launched"
            executable_named_file = n_root / "fixture.exe"
            executable_named_file.write_bytes(payload)
            row = _row(path="fixture.exe", limit=len(payload), expected=len(payload),
                       digest=hashlib.sha256(payload).hexdigest())

            data, evidence = reader._read_synthetic_roles(
                roots=roots, rows=[row], synthetic_mode="SYNTHETIC")

            self.assertEqual(data, {"N_PRIOR_OPERATION1": payload})
            self.assertEqual(evidence["scope"], "SYNTHETIC")
            self.assertEqual(evidence["finalSha256Recheck"], "PASS")

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

    def test_read_tolerates_ancestor_clock_change_with_leaf_unchanged(self):
        with _owned_temp_directory() as temp_root:
            roots, n_root, _d_root = _roots(temp_root)
            fixture = n_root / "fixture.bin"
            payload = b"alpha"
            fixture.write_bytes(payload)
            original = reader._inspect_path
            calls = [0]
            raw_before = [None]
            raw_after = [None]
            parent_before = [None]

            def change_ancestor_clock(raw_path, expected_kind):
                result = original(raw_path, expected_kind)
                calls[0] += 1
                if calls[0] == 1:
                    raw_before[0] = result[2]
                    parent_before[0] = os.stat(str(n_root)).st_mtime_ns
                    (n_root / "unrelated-sibling.bin").write_bytes(b"x")
                    parent_stat = os.stat(str(n_root))
                    os.utime(str(n_root), ns=(
                        parent_stat.st_atime_ns,
                        parent_before[0] + 5_000_000_000,
                    ))
                else:
                    raw_after[0] = result[2]
                return result

            with mock.patch.object(reader, "_inspect_path", side_effect=change_ancestor_clock):
                data, digest, _token = reader._read_file_once(
                    str(fixture), len(payload), len(payload),
                    hashlib.sha256(payload).hexdigest(), "N_PRIOR_OPERATION1")

            self.assertEqual(data, payload)
            self.assertEqual(digest, hashlib.sha256(payload).hexdigest())
            self.assertEqual(calls[0], 2)
            self.assertIsNotNone(raw_before[0])
            self.assertIsNotNone(raw_after[0])
            self.assertNotEqual(parent_before[0], os.stat(str(n_root)).st_mtime_ns)
            self.assertEqual(reader._path_identity(raw_before[0]),
                             reader._path_identity(raw_after[0]))
            self.assertEqual(fixture.read_bytes(), payload)

    def test_read_rejects_parent_replacement_with_same_leaf_identity(self):
        with _owned_temp_directory() as temp_root:
            _roots_map, n_root, _d_root = _roots(temp_root)
            fixture = n_root / "fixture.bin"
            payload = b"alpha"
            fixture.write_bytes(payload)
            moved_root = temp_root / "N-moved"
            original = reader._inspect_path
            calls = [0]
            raw_before = [None]
            raw_after = [None]

            def replace_parent_on_final_inspection(raw_path, expected_kind):
                calls[0] += 1
                if calls[0] == 1:
                    result = original(raw_path, expected_kind)
                    raw_before[0] = result[2]
                    return result
                n_root.rename(moved_root)
                n_root.mkdir()
                try:
                    os.link(str(moved_root / "fixture.bin"), str(n_root / "fixture.bin"))
                except OSError:
                    self.skipTest("same-volume hard links are unavailable")
                result = original(raw_path, expected_kind)
                raw_after[0] = result[2]
                return result

            with mock.patch.object(reader, "_inspect_path",
                                   side_effect=replace_parent_on_final_inspection):
                with self.assertRaises(reader.ReaderError) as changed:
                    reader._read_file_once(
                        str(fixture), len(payload), len(payload),
                        hashlib.sha256(payload).hexdigest(), "N_PRIOR_OPERATION1")

            self.assertEqual(changed.exception.code, "FILE_CHANGED")
            self.assertEqual(calls[0], 2)
            self.assertIsNotNone(raw_before[0])
            self.assertIsNotNone(raw_after[0])
            self.assertEqual(reader._handle_identity(raw_before[0][-1][1]),
                             reader._handle_identity(raw_after[0][-1][1]))
            self.assertEqual(raw_before[0][-1][1], raw_after[0][-1][1])
            self.assertNotEqual(reader._path_identity(raw_before[0]),
                                reader._path_identity(raw_after[0]))
            self.assertEqual((n_root / "fixture.bin").read_bytes(), payload)

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
