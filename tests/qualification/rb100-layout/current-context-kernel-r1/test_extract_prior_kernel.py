"""Synthetic-only tests for the pinned prior-kernel source extractor."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import types
import unittest


_SIBLING = Path(__file__).with_name("extract_prior_kernel.py")
_SOURCE = _SIBLING.read_bytes()
_LOADED = types.ModuleType("_extract_prior_kernel_test_subject")
_LOADED.__file__ = str(_SIBLING)
sys.modules[_LOADED.__name__] = _LOADED
exec(compile(_SOURCE, str(_SIBLING), "exec"), _LOADED.__dict__)

ExtractionError = _LOADED.ExtractionError
ModuleInput = _LOADED.ModuleInput
_extract_bundle = _LOADED._extract_bundle
extract_prior_kernel = _LOADED.extract_prior_kernel


N_SOURCE = b'''\
import hashlib
BASE = 7
class NError(ValueError):
    pass
def need(condition, code):
    if not condition:
        raise NError(code)
def operation(value):
    need(type(value) is int and value > BASE, "N_VALUE")
    return hashlib.sha256(str(value).encode("ascii")).hexdigest()
def ignored():
    return open("must-not-enter-the-closure")
'''

P07_SOURCE = b'''\
from collections import Counter
BASE = 2
def need(value):
    if not value:
        raise ValueError("P07_VALUE")
def operation(value):
    need(type(value) is str and len(value) >= BASE)
    return Counter(value).most_common(1)
'''


def _synthetic_artifact():
    modules = (
        ModuleInput("N", N_SOURCE, ("operation",)),
        ModuleInput("P07", P07_SOURCE, ("operation",)),
    )
    return _extract_bundle(modules)


class ExtractPriorKernelTests(unittest.TestCase):
    def test_synthetic_closures_are_namespaced_and_preserved(self):
        artifact = _synthetic_artifact()
        namespace = {"__name__": "_synthetic_prior_kernel"}
        exec(compile(artifact.kernel_source, "<synthetic-kernel>", "exec"), namespace)

        self.assertEqual(namespace["N_operation"](8), hashlib.sha256(b"8").hexdigest())
        self.assertEqual(namespace["P07_operation"]("aab"), [("a", 2)])
        self.assertIn("N_BASE", namespace)
        self.assertIn("P07_BASE", namespace)
        self.assertIn("N_need", namespace)
        self.assertIn("P07_need", namespace)
        self.assertIn("N_NError", namespace)
        self.assertNotIn("ignored", namespace)
        self.assertNotIn("operation", namespace)

        proof = json.loads(artifact.provenance_json)
        self.assertEqual(proof["schema"], "tsj-prior-kernel-extraction-v1")
        self.assertEqual([row["label"] for row in proof["modules"]], ["N", "P07"])
        self.assertIn("BASE", proof["modules"][0]["closure"])
        self.assertIn("Counter", proof["modules"][1]["closure"])
        self.assertIn({"kind": "class", "name": "NError"},
                      proof["modules"][0]["closureSymbols"])
        self.assertIn(["operation", "N_operation"], proof["modules"][0]["rename"])
        self.assertIn(["operation", "P07_operation"], proof["modules"][1]["rename"])

    def test_output_is_byte_deterministic(self):
        first = _synthetic_artifact()
        second = _synthetic_artifact()
        self.assertEqual(first.kernel_source, second.kernel_source)
        self.assertEqual(first.provenance_json, second.provenance_json)

    def test_unreferenced_unsafe_code_is_not_copied(self):
        artifact = _synthetic_artifact()
        self.assertNotIn(b"must-not-enter-the-closure", artifact.kernel_source)
        proof = json.loads(artifact.provenance_json)
        self.assertNotIn("ignored", proof["modules"][0]["closure"])

    def test_unpinned_source_mutation_is_rejected(self):
        with self.assertRaisesRegex(ExtractionError, "SHA-256 does not match its pin"):
            extract_prior_kernel(N_SOURCE, P07_SOURCE)

    def test_non_pure_import_is_rejected(self):
        source = b"import os\ndef operation(value):\n    return os.getcwd()\n"
        with self.assertRaisesRegex(ExtractionError, "non-pure or non-stdlib import"):
            _extract_bundle((ModuleInput("N", source, ("operation",)),))

    def test_unexpected_free_name_is_rejected(self):
        source = b"def operation(value):\n    return missing_dependency(value)\n"
        with self.assertRaisesRegex(ExtractionError, "unexpected free name"):
            _extract_bundle((ModuleInput("N", source, ("operation",)),))

    def test_lambda_nested_and_all_comprehension_forms_preserve_shadowing(self):
        source = b'''\
VALUE = "outer"
SOURCE_VALUES = ("first", "second")
def operation(rows):
    outer = VALUE
    choose = lambda items: [VALUE for VALUE in items]
    listed = choose(rows[0])
    same_name = [SOURCE_VALUES for SOURCE_VALUES in SOURCE_VALUES]
    setted = {VALUE for VALUE in rows[0]}
    mapped = {str(index): VALUE for index, VALUE in enumerate(rows[0])}
    generated = list(VALUE for VALUE in rows[0])
    nested = [[VALUE for VALUE in row] for row in rows]
    paired = [(left, right) for left in rows[0] for right in (left.upper(),)]
    return outer, listed, same_name, setted, mapped, generated, nested, paired
'''
        artifact = _extract_bundle((ModuleInput("N", source, ("operation",)),))
        namespace = {"__name__": "_synthetic_comprehension_kernel"}
        exec(compile(artifact.kernel_source, "<synthetic-comprehension-kernel>", "exec"),
             namespace)
        self.assertEqual(namespace["N_operation"]([["a", "b"], ["c"]]),
                         ("outer", ["a", "b"], ["first", "second"], {"a", "b"},
                          {"0": "a", "1": "b"}, ["a", "b"],
                          [["a", "b"], ["c"]],
                          [("a", "A"), ("b", "B")]))

    def test_generated_namespace_cannot_capture_an_original_local(self):
        source = b"VALUE = 1\ndef operation(N_VALUE):\n    return VALUE + N_VALUE\n"
        with self.assertRaisesRegex(ExtractionError, "collides with original local binding"):
            _extract_bundle((ModuleInput("N", source, ("operation",)),))

    def test_dynamic_execution_and_file_io_are_rejected(self):
        cases = (
            (b"def operation(value):\n    return eval(value)\n", "eval"),
            (b"def operation(value):\n    return open(value).read()\n", "open"),
        )
        for source, expected in cases:
            with self.subTest(expected=expected):
                with self.assertRaisesRegex(ExtractionError,
                                            "dynamic execution or I/O call|file or process operation"):
                    _extract_bundle((ModuleInput("N", source, ("operation",)),))


if __name__ == "__main__":
    unittest.main()
