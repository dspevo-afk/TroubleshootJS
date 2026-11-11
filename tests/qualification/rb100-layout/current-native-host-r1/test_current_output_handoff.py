"""Actual Windows five-file CODE/NO_DATA closure, never normal qualification.

The root's frozen driver authenticates this test and its existing fixture driver.
That maintained driver authenticates reader/caller/stager and explicit NO_DATA.
No prior-input PASS record or actual normal-input invocation is manufactured.
"""
import hashlib
import json
import os
from pathlib import Path
import sys
import types
import unittest


def load_fixture_driver():
    cfg = json.loads(Path(os.environ["TSJ_STAGING_CONFIG"]).read_bytes())
    row = cfg["tests"]
    raw = Path(row["path"]).read_bytes()
    assert len(raw) == row["bytes"]
    assert hashlib.sha256(raw).hexdigest() == row["sha256"]
    module = types.ModuleType("_output_handoff_fixture_driver")
    module.__file__ = row["path"]
    exec(compile(raw, row["path"], "exec", dont_inherit=True), module.__dict__)
    return module


BASE = load_fixture_driver()


class HandoffTests(unittest.TestCase):
    setUpClass = classmethod(BASE.StageTests.setUpClass.__func__)
    tearDownClass = classmethod(BASE.StageTests.tearDownClass.__func__)
    bind = BASE.StageTests.bind
    refresh = BASE.StageTests.refresh
    run_stage = BASE.StageTests.run_stage

    def setUp(self):
        BASE.StageTests.setUp(self)
        self.source.unlink()  # Exact ordinary CODE fixture created by that setup.
        self.names = (self.caller.ATTEMPT_FILE,) + tuple(
            name for name, _ in self.caller.ROLE_OUTPUT_NAMES)
        self.assertEqual(5, len(self.names))
        self.raws = {name: ("// OWNED_CODE_ONLY_CLOSURE:" + name + "\n").encode("ascii")
                     for name in self.names}
        self.raws["current-input.java"] = self.fixture
        self.tokens = {}
        for name in self.names:
            fact = self.caller._write_exclusive(
                self.reader, self.sc, str(self.source_dir / name), self.raws[name],
                self.caller.MAX_OUTPUT_BYTES, str(self.scratch), self.ss,
                "code-only-five-file-write", with_readback_token=True)
            self.tokens[name] = fact["readbackToken"]
        self.refresh()

    def closure(self, **changes):
        args = dict(reader=self.reader, config=self.sc, expected_files=self.raws,
                    expected_roots_sha=self.ss, expected_write_tokens=self.tokens,
                    include_source_token=True)
        return self.caller._validate_output_closure(**dict(args, **changes))

    def rejected(self, code, **changes):
        with self.assertRaises(self.caller.CallerError) as caught:
            self.closure(**changes)
        self.assertEqual(code, caught.exception.code)
        self.assertEqual([], list(self.dest_dir.iterdir()))

    def replace_same_bytes(self, name):
        path = self.source_dir / name
        moved = self.scratch / ("preserved-original-" + name)
        path.rename(moved)
        path.write_bytes(self.raws[name])
        self.assertNotEqual(path.stat().st_ino, moved.stat().st_ino)

    def test_00_exact_five_file_handoff_to_stager(self):
        result = self.closure()
        self.assertEqual({"fileCount", "files", "producerSourceToken"}, set(result))
        self.assertEqual(5, result["fileCount"])
        self.assertEqual(set(self.names), {r["name"] for r in result["files"]})
        for row in result["files"]:
            self.assertEqual(len(self.raws[row["name"]]), row["bytes"])
            self.assertEqual(hashlib.sha256(self.raws[row["name"]]).hexdigest(), row["sha256"])
            self.assertIs(True, row["identityBound"])
        source_identity = self.reader._token_identity(result["producerSourceToken"])
        self.assertEqual(self.reader._token_identity(self.tokens["current-input.java"]), source_identity)
        fact = self.run_stage(expected_source_token=result["producerSourceToken"])
        self.assertEqual(self.fixture, self.dest.read_bytes())
        self.assertEqual(hashlib.sha256(self.fixture).hexdigest(), fact["sha256"])
        self.assertEqual(set(self.names), {p.name for p in self.source_dir.iterdir()})
        self.assertNotIn("status", result)

    def test_01_same_content_source_replacement_rejected(self):
        self.replace_same_bytes("current-input.java")
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES")
        self.assertEqual(self.fixture, self.source.read_bytes())

    def test_attempt_replacement_rejected(self):
        self.replace_same_bytes(self.caller.ATTEMPT_FILE)
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES")

    def test_context_replacement_rejected(self):
        self.replace_same_bytes("context.json")
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES")

    def test_projection_replacement_rejected(self):
        self.replace_same_bytes("native-projection.json")
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES")

    def test_pending_evidence_replacement_rejected(self):
        self.replace_same_bytes("qualification-evidence.json")
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES")

    def test_same_shape_other_file_token_rejected(self):
        tokens = dict(self.tokens)
        tokens["current-input.java"] = tokens["context.json"]
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES", expected_write_tokens=tokens)

    def test_missing_token_rejected(self):
        tokens = dict(self.tokens)
        del tokens["native-projection.json"]
        self.rejected("OUTPUT_CLOSURE_TOKEN_KEYS", expected_write_tokens=tokens)

    def test_extra_token_rejected(self):
        tokens = dict(self.tokens, extra=self.tokens["current-input.java"])
        self.rejected("OUTPUT_CLOSURE_TOKEN_KEYS", expected_write_tokens=tokens)

    def test_no_token_map_cannot_request_source_token(self):
        self.rejected("OUTPUT_CLOSURE_TOKEN_REQUIRED", expected_write_tokens=None)

    def test_malformed_tokens_rejected(self):
        cases = [(None, "OUTPUT_CLOSURE_CONTROL_TOKEN"),
                 ((), "OUTPUT_CLOSURE_CONTROL_TOKEN"),
                 ([], "OUTPUT_CLOSURE_CONTROL_TOKEN"),
                 (("x", (), (), ()), "READER_FILE_CHANGED"),
                 (True, "OUTPUT_CLOSURE_CONTROL_TOKEN")]
        for value, code in cases:
            with self.subTest(value=value):
                tokens = dict(self.tokens)
                tokens["current-input.java"] = value
                self.rejected(code, expected_write_tokens=tokens)

    def test_malformed_option_rejected(self):
        for value in (1, 0, "yes", None, []):
            with self.subTest(value=value):
                self.rejected("OUTPUT_CLOSURE_TOKEN_OPTION", include_source_token=value)

    def test_stronger_closure_keeps_default_fact_shape(self):
        result = self.closure(include_source_token=False)
        self.assertEqual({"fileCount", "files"}, set(result))
        self.assertEqual(5, result["fileCount"])
        self.replace_same_bytes("context.json")
        self.rejected("OUTPUT_CLOSURE_IDENTITY_CHANGED_AFTER_WRITES", include_source_token=False)

    def test_existing_tokenless_helper_fact_unchanged(self):
        result = self.closure(expected_write_tokens=None, include_source_token=False)
        self.assertEqual({"fileCount", "files"}, set(result))
        self.assertEqual(5, result["fileCount"])

    def test_wrong_bytes_reject_with_original_tokens(self):
        self.source.write_bytes(bytes([self.fixture[0] ^ 1]) + self.fixture[1:])
        self.rejected("READER_SHA256")

    def test_extra_file_rejects_before_handoff(self):
        (self.source_dir / "extra-code.txt").write_bytes(b"OWNED_EXTRA_CODE\n")
        self.rejected("OUTPUT_CLOSURE_NAMES")

    def test_missing_file_rejects_before_handoff(self):
        (self.source_dir / "context.json").rename(self.scratch / "preserved-context.json")
        self.rejected("OUTPUT_CLOSURE_NAMES")

    def test_root_replacement_rejects_before_handoff(self):
        self.source_dir.rename(self.scratch / "sealed-upstream-preserved")
        self.source_dir.mkdir()
        self.rejected("ROOTS_CHANGED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
