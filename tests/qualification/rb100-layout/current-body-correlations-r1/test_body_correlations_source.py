"""Finite Source-only tests; no builders, data, class loading, or producer."""
import hashlib
from pathlib import Path
import sys
import types
import unittest

R = Path(__file__).resolve().parent
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
owner = types.ModuleType("_body_correlation_source")
exec(compile((R / "derive_body_correlations.py").read_bytes(),
             "derive_body_correlations.py", "exec", dont_inherit=True, optimize=0),
     owner.__dict__)


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.study = STUDY.read_bytes()
        cls.codec = CODEC.read_bytes()
        cls.fragment = (R / "BodyCorrelations.java.fragment").read_bytes()

    def derive(self, **kw):
        values = dict(study=self.study, codec=self.codec, fragment=self.fragment)
        values.update(kw)
        return owner.derive(**values)

    def reject(self, code, **kw):
        with self.assertRaisesRegex(owner.SourceError, "^" + code + "$"):
            self.derive(**kw)

    def test_exact_pinned_inputs(self):
        for raw, size, pin in ((self.study, 236944, owner.STUDY_SHA),
                               (self.codec, 20119, owner.CODEC_SHA),
                               (self.fragment, 3350, owner.FRAGMENT_SHA)):
            self.assertEqual(len(raw), size)
            self.assertEqual(hashlib.sha256(raw).hexdigest(), pin)

    def test_inverse_conserves_all_other_bytes(self):
        study, codec = self.derive()
        self.assertEqual(study.replace(owner.NEW_CALL, owner.OLD_CALL), self.study)
        self.assertEqual(codec.replace(self.fragment, b"", 1), self.codec)
        self.assertEqual(codec.count(self.fragment), 1)

    def test_all_three_current_callers_migrate(self):
        study, codec = self.derive()
        self.assertEqual(study.count(owner.NEW_CALL), 3)
        self.assertNotIn(owner.OLD_CALL, study)
        for sink in (b"count", b"writer", b"reader"):
            self.assertEqual(study.count(owner.NEW_CALL + sink + b");"), 1)
        self.assertEqual(codec.count(b"static void writeCorrelatedBody("), 1)
        self.assertEqual(codec.count(b"static void writeBody("), 1)

    def test_deterministic_output(self):
        self.assertEqual(self.derive(), self.derive())

    def test_changed_study(self):
        self.reject("STUDY_SOURCE", study=b"x" + self.study[1:])

    def test_changed_codec(self):
        self.reject("CODEC_SOURCE", codec=b"x" + self.codec[1:])

    def test_changed_fragment(self):
        self.reject("FRAGMENT_SOURCE", fragment=b"x" + self.fragment[1:])

    def test_truncation(self):
        for name, code in (("study", "STUDY_SOURCE"), ("codec", "CODEC_SOURCE"),
                           ("fragment", "FRAGMENT_SOURCE")):
            self.reject(code, **{name: getattr(self, name)[:-1]})

    def test_oversize(self):
        self.reject("STUDY_SOURCE", study=b"x" * (owner.SOURCE_CAP + 1))

    def test_mutable_inputs_rejected(self):
        self.reject("CODEC_SOURCE", codec=bytearray(self.codec))

    def test_already_derived_input_rejected(self):
        study, codec = self.derive()
        self.reject("STUDY_SOURCE", study=study)
        self.reject("CODEC_SOURCE", codec=codec)

    def test_no_io_or_dynamic_execution_in_owner(self):
        import ast
        tree = ast.parse((R / "derive_body_correlations.py").read_bytes())
        self.assertEqual([x.names[0].name for x in ast.walk(tree)
                          if isinstance(x, ast.Import)], ["hashlib"])
        self.assertFalse(any(isinstance(x, ast.ImportFrom) for x in ast.walk(tree)))
        denied = {"open", "exec", "eval", "compile", "__import__"}
        self.assertFalse(any(isinstance(x, ast.Call) and isinstance(x.func, ast.Name)
                             and x.func.id in denied for x in ast.walk(tree)))


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--study-source", required=True, type=Path)
    parser.add_argument("--codec-source", required=True, type=Path)
    args, remaining = parser.parse_known_args()
    STUDY, CODEC = args.study_source, args.codec_source
    unittest.main(argv=[sys.argv[0]] + remaining)
