"""Pure projection and bounded-filesystem tests; no pinned CLI positive run."""

import hashlib
import importlib.util
import stat
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().with_name("export_review_sources.py")
SPEC = importlib.util.spec_from_file_location("export_review_sources_tested", MODULE_PATH)
EXPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EXPORTER)


def _is_reparse(info):
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


class ProjectionTests(unittest.TestCase):
    def test_review_normalizes_terminal_empty_lines_preserving_original_source(self):
        for ending in (b"\n", b"\r\n"):
            with self.subTest(ending=ending):
                source = b"class ReviewFixture {" + ending + b"}" + ending * 3
                original_sha = hashlib.sha256(source).hexdigest()
                review, rows, next_token = EXPORTER.project_source("study", source)
                self.assertTrue(review.endswith(b"}" + ending))
                self.assertFalse(review.endswith(ending * 2))
                self.assertIn(original_sha.encode("ascii"), review)
                self.assertEqual(hashlib.sha256(source).hexdigest(), original_sha)
                self.assertEqual((rows, next_token), ([], 1))

    def test_redacts_path_byte_literals_preserving_literal_type_and_other_bytes(self):
        source = (
            b'GUARD = b"C:\\\\Users\\\\"\n'
            b'GUARD_SLASH = b"C:/Users/"\n'
            b'KEEP = b"ordinary\\\\value\\x00"\n'
        )
        projected, rows, next_token = EXPORTER.redact_python_path_literals(source)
        self.assertEqual(projected, (
            b'GUARD = b"<PATH_0001>"\n'
            b'GUARD_SLASH = b"<PATH_0002>"\n'
            b'KEEP = b"ordinary\\\\value\\x00"\n'
        ))
        self.assertEqual(len(rows), 2)
        self.assertEqual(next_token, 3)

    def test_redacts_only_python_absolute_path_string_literals(self):
        source = (
            b'ABS = r"C:\\Users\\reviewer\\private\\source.py"\n'
            b'UNC = r"\\\\server\\share\\review.py"\n'
            b'REL = "folder\\\\child"\n'
            b'DRIVE_RELATIVE = "C:relative"\n'
            br'REPLACE = path.replace("\\", "/")' + b"\n"
            br'WINDOWS_RE = re.compile(r"[A-Z]:[\\/]")' + b"\n"
            b'# C: appears here without an absolute path\n'
        )
        projected, rows, next_token = EXPORTER.redact_python_path_literals(source)
        self.assertEqual(projected, (
            b'ABS = "<PATH_0001>"\n'
            b'UNC = "<PATH_0002>"\n'
            b'REL = "folder\\\\child"\n'
            b'DRIVE_RELATIVE = "C:relative"\n'
            br'REPLACE = path.replace("\\", "/")' + b"\n"
            br'WINDOWS_RE = re.compile(r"[A-Z]:[\\/]")' + b"\n"
            b'# C: appears here without an absolute path\n'
        ))
        self.assertEqual(next_token, 3)
        self.assertEqual(len(rows), 2)
        self.assertEqual(set(rows[0]), {"token", "literalSha256", "line", "column"})
        self.assertEqual(rows[0]["token"], "<PATH_0001>")
        self.assertEqual(rows[0]["literalSha256"], hashlib.sha256(
            b'r"C:\\Users\\reviewer\\private\\source.py"').hexdigest())
        self.assertEqual(rows[1]["token"], "<PATH_0002>")
        self.assertEqual(rows[1]["literalSha256"], hashlib.sha256(
            b'r"\\\\server\\share\\review.py"').hexdigest())
        self.assertNotIn(b"reviewer", projected)

    def test_rejects_absolute_path_left_in_python_comment(self):
        with self.assertRaises(EXPORTER.ExportError) as raised:
            EXPORTER.project_source(
                "reader", b'# C:\\Users\\reviewer\\private\n')
        self.assertEqual(raised.exception.code, "ABSOLUTE_PATH_REMAINS")

    def test_rejects_absolute_path_in_unchanged_java_body(self):
        with self.assertRaises(EXPORTER.ExportError) as raised:
            EXPORTER.project_source(
                "study", b'// C:\\Users\\reviewer\\private\n')
        self.assertEqual(raised.exception.code, "ABSOLUTE_PATH_REMAINS")

    def test_cli_parser_rejects_duplicate_unknown_and_abbreviated_flags(self):
        args = [
            "--study", "study", "--reader", "reader", "--builder", "builder",
            "--descriptor-helper", "descriptor", "--output", "out",
        ]
        duplicate = list(args)
        duplicate[2] = "--study"
        unknown = list(args)
        unknown[0] = "--extra"
        abbreviated = list(args)
        abbreviated[0] = "--stud"
        for invalid in (duplicate, unknown, abbreviated):
            with self.subTest(argv=invalid):
                with self.assertRaises(EXPORTER.ExportError) as raised:
                    EXPORTER.parse_cli_args(invalid)
                self.assertEqual(raised.exception.code, "CLI_ARGUMENTS")


class ExportFilesystemTests(unittest.TestCase):
    def setUp(self):
        self.temp_root = Path(tempfile.gettempdir()).resolve(strict=True)
        self.base = Path(tempfile.mkdtemp(prefix="tsj-source-review-r1-",
                                          dir=str(self.temp_root)))
        self._check_owned_dir(self.base, self.temp_root)
        self.input_dir = self.base / "inputs"
        self.output = self.base / "output"
        self.input_dir.mkdir()
        self.output.mkdir()
        self.addCleanup(self._cleanup_owned_tree)
        self.sources = {
            "study": self.input_dir / "study.java",
            "reader": self.input_dir / "reader.py",
            "builder": self.input_dir / "builder.py",
            "descriptor-helper": self.input_dir / "descriptor.py",
        }
        fixture_bytes = {
            "study": b"class ReviewFixture {\n}\n",
            "reader": b'ROOT = r"C:\\Users\\reviewer\\reader.py"\nKEEP = "relative"\n',
            "builder": b"BUILD_ROLE = \"fixture\"\n",
            "descriptor-helper": b"DESCRIPTOR_ROLE = \"fixture\"\n",
        }
        for role, path in self.sources.items():
            path.write_bytes(fixture_bytes[role])
        self.pins = {role: hashlib.sha256(fixture_bytes[role]).hexdigest()
                     for role in EXPORTER.SOURCE_ROLES}

    def _check_owned_dir(self, path, expected_parent):
        info = path.lstat()
        if (path.parent.resolve(strict=True) != expected_parent
                or path.resolve(strict=True) != path
                or not stat.S_ISDIR(info.st_mode) or _is_reparse(info)):
            raise AssertionError("UNSAFE_TEST_FIXTURE_DIRECTORY")

    def _cleanup_owned_tree(self):
        self._check_owned_dir(self.base, self.temp_root)
        permitted = {
            "study.java", "reader.py", "builder.py", "descriptor.py",
            "keep.txt", *EXPORTER.REVIEW_FILES.values(), "SOURCE_EXPORT.json",
        }
        for directory in (self.input_dir, self.output):
            self._check_owned_dir(directory, self.base)
            for child in tuple(directory.iterdir()):
                if child.parent != directory or child.name not in permitted:
                    raise RuntimeError("UNOWNED_TEST_FIXTURE_ENTRY")
                info = child.lstat()
                if _is_reparse(info) or not stat.S_ISREG(info.st_mode):
                    raise RuntimeError("UNSAFE_TEST_FIXTURE_ENTRY")
                child.unlink()
            directory.rmdir()
        self.base.rmdir()

    def test_exclusive_export_writes_exact_review_closure_and_readback(self):
        manifest = EXPORTER.export_review_sources(
            self.sources, self.output, expected_pins=self.pins)
        expected_names = {"SOURCE_EXPORT.json", *EXPORTER.REVIEW_FILES.values()}
        self.assertEqual({path.name for path in self.output.iterdir()}, expected_names)
        self.assertEqual(manifest["schema"], EXPORTER.SCHEMA)
        self.assertEqual(manifest["status"], EXPORTER.STATUS)
        self.assertEqual([row["role"] for row in manifest["sources"]],
                         list(EXPORTER.SOURCE_ROLES))
        for row in manifest["sources"]:
            review = (self.output / row["reviewFile"]).read_bytes()
            self.assertEqual(hashlib.sha256(review).hexdigest(), row["reviewSha256"])
            self.assertNotRegex(review.decode("utf-8"), EXPORTER.ABSOLUTE_PATH_START)
        manifest_raw = (self.output / "SOURCE_EXPORT.json").read_bytes()
        self.assertNotRegex(manifest_raw.decode("ascii"), EXPORTER.ABSOLUTE_PATH_START)

    def test_existing_output_entry_is_rejected_without_overwrite(self):
        keep = self.output / "keep.txt"
        keep.write_bytes(b"owned fixture\n")
        with self.assertRaises(EXPORTER.ExportError) as raised:
            EXPORTER.export_review_sources(
                self.sources, self.output, expected_pins=self.pins)
        self.assertEqual(raised.exception.code, "OUTPUT_NOT_EMPTY")
        self.assertEqual(keep.read_bytes(), b"owned fixture\n")

    def test_invalid_source_hash_rejects_before_any_output_write(self):
        bad_pins = dict(self.pins)
        bad_pins["builder"] = "0" * 64
        with self.assertRaises(EXPORTER.ExportError) as raised:
            EXPORTER.export_review_sources(
                self.sources, self.output, expected_pins=bad_pins)
        self.assertEqual(raised.exception.code, "SOURCE_HASH_BUILDER")
        self.assertEqual(list(self.output.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
