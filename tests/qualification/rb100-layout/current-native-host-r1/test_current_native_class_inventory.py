"""Real Windows current CODE-only class closure; never normal DATA admission."""
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import types
import unittest


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def base_driver():
    cfg = json.loads(Path(os.environ["TSJ_STAGING_CONFIG"]).read_bytes())
    row = cfg["tests"]
    raw = Path(row["path"]).read_bytes()
    assert len(raw) == row["bytes"] and digest(raw) == row["sha256"]
    module = types.ModuleType("_class_inventory_fixture_driver")
    module.__file__ = row["path"]
    exec(compile(raw, row["path"], "exec", dont_inherit=True), module.__dict__)
    return module


BASE = base_driver()
PREFIX = "com/lushprojects/circuitjs1/client/"
STUB = PREFIX + "CohortTerminalTelemetryInputV2.class"


class InventoryTests(unittest.TestCase):
    tearDownClass = classmethod(BASE.StageTests.tearDownClass.__func__)

    @classmethod
    def setUpClass(cls):
        BASE.StageTests.setUpClass.__func__(cls)
        for role, name in (("invocations", "_class_inventory_invocations"),
                           ("inventory", "_class_inventory_component")):
            row = cls.cfg[role]
            raw, _, _ = cls.reader._read_file_once(row["path"], 4194304,
                row["bytes"], row["sha256"], "EXPLICIT_CURRENT_CODE")
            setattr(cls, role, cls.loader._exec_authenticated_module(name, row["path"], raw))
        cls.fresh_rows = cls.cfg["freshRows"]
        cls.inherited_rows = cls.cfg["inheritedRows"]
        cls.jars = frozenset(cls.cfg["jarClassNames"])
        # Source/metadata provenance selects these admitted CODE roots first.
        cls.actual = cls.inventory.observe_current_code_classes(
            reader=cls.reader, io_owner=cls.caller, invocation_owner=cls.invocations,
            fresh_root=cls.cfg["freshRoot"], inherited_root=cls.cfg["inheritedRoot"],
            fresh_rows=cls.fresh_rows, inherited_rows=cls.inherited_rows, jar_class_names=cls.jars)
        cls.fresh_bytes = {}
        for row in cls.fresh_rows:
            raw, _, _ = cls.reader._read_file_once(str(Path(cls.cfg["freshRoot"]) / row["path"]),
                cls.reader.MAX_ROLE_BYTES, row["bytes"], row["sha256"], "ADMITTED_CODE_COPY")
            cls.fresh_bytes[row["path"]] = raw
        cls.code_path = next(r["path"] for r in cls.fresh_rows if r["path"] != STUB)

    def setUp(self):
        self.root = self.base / self._testMethodName
        self.root.mkdir()
        self.fresh = self.root / "fresh-code"
        self.fresh.mkdir()
        for relative, raw in self.fresh_bytes.items():
            path = self.fresh / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open("xb") as output:
                output.write(raw)
        self.rows = [dict(row) for row in self.fresh_rows]

    def observe(self, **changes):
        args = dict(reader=self.reader, io_owner=self.caller, invocation_owner=self.invocations,
            fresh_root=str(self.fresh), inherited_root=self.cfg["inheritedRoot"],
            fresh_rows=self.rows, inherited_rows=self.inherited_rows, jar_class_names=self.jars)
        return self.inventory.observe_current_code_classes(**dict(args, **changes))

    def reject(self, code, before_reads=False, **changes):
        def profile(frame, event, arg):
            if (event == "call" and frame.f_code.co_name == "_read_file_once"
                    and frame.f_globals is self.reader.__dict__
                    and frame.f_locals.get("role") == "CODE_CLASS"):
                raise AssertionError("CLASS_CONTENT_READ_BEFORE_CLOSURE_REJECTION")
        if before_reads:
            sys.setprofile(profile)
        try:
            with self.assertRaises((self.inventory.ClassInventoryError,
                                    self.caller.CallerError, self.reader.ReaderError,
                                    self.invocations.InvocationError)) as caught:
                self.observe(**changes)
            self.assertEqual(code, getattr(caught.exception, "code", str(caught.exception)))
        finally:
            if before_reads:
                sys.setprofile(None)

    def row(self, relative):
        return next(r for r in self.rows if r["path"] == relative)

    def test_00_actual_code_inventory(self):
        self.assertEqual(141, len(self.actual["freshClasses"]))
        self.assertEqual(2396, len(self.actual["inheritedClasses"]))
        self.assertEqual(2537, self.actual["classCount"])
        self.assertEqual(sum(r["bytes"] for r in self.fresh_rows + self.inherited_rows), self.actual["classBytes"])
        self.assertEqual(sorted(self.fresh_rows, key=lambda r:r["path"]), self.actual["freshClasses"])
        self.assertNotIn("status", self.actual)

    def test_01_missing_file_before_reads(self):
        (self.fresh / self.code_path).rename(self.root / "preserved-code.class")
        self.reject("CLASS_DIRECTORY_CLOSURE", before_reads=True)

    def test_extra_nonclass_before_reads(self):
        (self.fresh / PREFIX / "unexpected.txt").write_bytes(b"OWNED_CODE_SENTINEL\n")
        self.reject("CLASS_DIRECTORY_CLOSURE", before_reads=True)

    def test_extra_class_before_reads(self):
        (self.fresh / PREFIX / "Unexpected.class").write_bytes(b"OWNED_CODE_SENTINEL\n")
        self.reject("CLASS_DIRECTORY_CLOSURE", before_reads=True)

    def test_directory_enumeration_is_bounded_before_reads(self):
        for name in ("UnexpectedOne.class", "UnexpectedTwo.class"):
            (self.fresh / PREFIX / name).write_bytes(b"OWNED_CODE_SENTINEL\n")
        self.reject("DIRECTORY_ENTRY_LIMIT", before_reads=True)

    def test_factory_name_denied_before_reads(self):
        (self.fresh / PREFIX / "CohortTerminalTelemetryInput.class").write_bytes(b"OWNED_CODE_SENTINEL_DO_NOT_OPEN\n")
        self.reject("CLIENT_PRODUCER_CLASS_DENIED", before_reads=True)

    def test_factory_metadata_denied_before_reads(self):
        self.rows[0] = dict(self.rows[0], path=PREFIX + "CohortTerminalTelemetryInput.class")
        self.reject("FRESH_PRODUCER_CLASS_DENIED", before_reads=True)

    def test_inherited_factory_metadata_before_reads(self):
        inherited = [dict(r) for r in self.inherited_rows]
        inherited[0]["path"] = PREFIX + "CohortTerminalTelemetryInputLegacy.class"
        self.reject("INHERITED_PRODUCER_CLASS_DENIED", before_reads=True, inherited_rows=inherited)

    def test_both_closures_complete_before_any_body(self):
        inherited = self.root / "inherited-empty"
        (inherited / PREFIX).mkdir(parents=True)
        self.reject("CLASS_DIRECTORY_CLOSURE", before_reads=True, inherited_root=str(inherited))

    def test_no_data_binding_cannot_be_replaced(self):
        self.row(STUB)["sha256"] = "0" * 64
        self.reject("FRESH_NODATA_BINDING", before_reads=True)

    def test_case_collision_metadata_rejected(self):
        other = self.rows[1]
        self.rows[2] = dict(other, path=other["path"][:-6].lower().replace(PREFIX.lower(), PREFIX) + ".class")
        self.reject("FRESH_DUPLICATE_PATH", before_reads=True)

    def test_wrong_relative_path_rejected(self):
        self.rows[1]["path"] = "../escape.class"
        self.reject("FRESH_PATH", before_reads=True)

    def test_wrong_metadata_count_rejected(self):
        self.reject("FRESH_ROWS", before_reads=True, fresh_rows=self.rows[:-1])

    def test_wrong_metadata_types_rejected(self):
        self.rows[1]["major"] = True
        self.reject("FRESH_MAJOR", before_reads=True)

    def test_aggregate_byte_cap_before_reads(self):
        for row in self.rows:
            if row["path"] != STUB:
                row["bytes"] = 8388608
        self.reject("INVENTORY_BUDGET", before_reads=True)

    def test_full_jar_class_path_shadow_rejected(self):
        self.reject("JAR_CLASS_INTERSECTION", before_reads=True, jar_class_names=self.jars | frozenset([self.code_path]))

    def test_fresh_inherited_shadow_rejected(self):
        inherited = [dict(r) for r in self.inherited_rows]
        inherited[0]["path"] = self.code_path
        self.reject("FRESH_INHERITED_INTERSECTION", before_reads=True, inherited_rows=inherited)

    def test_wrong_class_hash_rejected(self):
        self.row(self.code_path)["sha256"] = "0" * 64
        self.reject("READER_SHA256")

    def test_wrong_header_with_matching_fixture_digest(self):
        path = self.fresh / self.code_path
        raw = b"WRNG" + path.read_bytes()[4:]
        path.write_bytes(raw)
        self.row(self.code_path)["sha256"] = digest(raw)
        self.reject("CLASS_HEADER")

    def test_wrong_major_with_matching_fixture_digest(self):
        path = self.fresh / self.code_path
        raw = bytearray(path.read_bytes())
        raw[6:8] = b"\x00\x34"
        path.write_bytes(raw)
        self.row(self.code_path)["sha256"] = digest(raw)
        self.reject("CLASS_HEADER")

    def test_directory_instead_of_class_before_reads(self):
        path = self.fresh / self.code_path
        path.rename(self.root / "preserved-code.class")
        path.mkdir()
        self.reject("REGULAR", before_reads=True)

    def test_aliased_roots_rejected(self):
        self.reject("ROOT_ALIAS_OR_OVERLAP", before_reads=True, inherited_root=str(self.fresh))

    def test_real_package_junction_before_reads(self):
        import _winapi
        package = self.fresh / PREFIX
        moved = self.root / "preserved-client"
        self.assertEqual(os.path.commonpath((str(self.root.resolve()), str(package.resolve()))), str(self.root.resolve()))
        self.assertFalse(os.lstat(package).st_file_attributes & 0x400)
        os.rename(package, moved)
        _winapi.CreateJunction(str(moved), str(package))
        try:
            self.assertEqual(0xA0000003, os.lstat(package).st_reparse_tag)
            self.assertEqual(package.resolve(), moved.resolve())
            self.reject("REPARSE", before_reads=True)
        finally:
            assert os.lstat(package).st_reparse_tag == 0xA0000003 and package.resolve() == moved.resolve()
            start = time.monotonic_ns()
            os.rmdir(package)
            self.cleanups.append(dict(kind="JUNCTION_NODE_ONLY", tag="A0000003", exactOwnedTargetRevalidated=True,
                cleanupNanos=time.monotonic_ns()-start, nodeAbsent=not package.exists()))

    def test_same_content_replacement_at_recheck_rejected(self):
        path = self.fresh / self.code_path
        raw = path.read_bytes()
        def profile(frame, event, arg):
            if (event == "return" and frame.f_code.co_name == "_read_inventory"
                    and frame.f_globals is self.inventory.__dict__
                    and frame.f_locals.get("phase") == "class-inventory-first-read"):
                sys.setprofile(None)
                path.rename(self.root / "preserved-code.class")
                path.write_bytes(raw)
        sys.setprofile(profile)
        try:
            self.reject("CLASS_PATH_IDENTITY")
        finally:
            sys.setprofile(None)

    def test_parent_replacement_before_any_body_rejected(self):
        package = self.fresh / PREFIX
        moved = self.root / "preserved-client"
        changed = [False]
        def profile(frame, event, arg):
            if (event == "call" and frame.f_code.co_name == "_read_file_once"
                    and frame.f_globals is self.reader.__dict__
                    and frame.f_locals.get("role") == "CODE_CLASS"):
                raise AssertionError("CLASS_BODY_READ_AFTER_PARENT_SWAP")
            if (not changed[0] and event == "return" and frame.f_code.co_name == "_directory_names"
                    and frame.f_globals is self.inventory.__dict__
                    and os.path.normcase(frame.f_locals.get("path", "")) == os.path.normcase(str(package))):
                changed[0] = True
                assert os.path.commonpath((str(self.root.resolve()), str(package.resolve()))) == str(self.root.resolve())
                assert not (os.lstat(package).st_file_attributes & 0x400)
                os.rename(package, moved)
                package.mkdir()
                for relative, raw in self.fresh_bytes.items():
                    (package / Path(relative).name).write_bytes(raw)
        sys.setprofile(profile)
        try:
            self.reject("PARENT_IDENTITY")
            self.assertTrue(changed[0])
        finally:
            sys.setprofile(None)


if __name__ == "__main__":
    unittest.main(verbosity=2)
