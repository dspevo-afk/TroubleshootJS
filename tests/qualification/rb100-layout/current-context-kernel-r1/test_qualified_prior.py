"""Synthetic-only tests; execution is a separate root-owned gate.

Only the pinned legacy test module's literal fixture FunctionDef is compiled.
Its module body, imports, loader and test suite are never executed. No controlled
DATA files, normalizer, kernel or native classes are consumed.
"""

import argparse
import ast
import copy
import hashlib
import importlib.util
import sys
import unittest
from pathlib import Path


_OPTIONS = argparse.ArgumentParser(add_help=False)
_OPTIONS.add_argument("--source-root", type=Path)
_ARGS, _UNITTEST_ARGS = _OPTIONS.parse_known_args()
_HERE = Path(__file__).resolve().parent
_SOURCE_ROOT = (_ARGS.source_root or _HERE / "source").resolve()
_BASELINE_CODEC_SHA = "c1f27377d5d7a6c83384e34cc2ded334b0b022cfb68722265533976c0661ba27"
_BASELINE_FIXTURE_SHA = "2a9479e97393273341eb44b71aa6870cb9c0661c87e8985e2bd4c83c193f7f36"

# Independent literals from COMPONENT_CONTRACT_R1.md, not codec constants.
_SCHEMA = "tsj-cohort-terminal-qualified-prior-v2"
_KEYS = (
    "schema", "inputContextSha256", "sourceParent", "manifestSha256",
    "sourceManifestSha256", "readerSha256", "resultSha256", "operation1Sha256",
    "parentReceiptSha256", "priorRunId", "priorOriginalReceipt", "trialDisposition",
)
_CONTEXT_BYTES = 66485
_CONTEXT_SHA = "ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f"
_FACTORY_BYTES = 21404
_FACTORY_SHA = "c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089"


def _pinned_source(name, digest):
    source = (_SOURCE_ROOT / name).read_bytes()
    if hashlib.sha256(source).hexdigest() != digest:
        raise RuntimeError("PINNED_SOURCE_HASH_" + name)
    return source


def _load_codec(path, module_name):
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError("CONTEXT_CODEC_SOURCE_LOAD")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_BASELINE_SOURCE = _pinned_source("context_codec.py", _BASELINE_CODEC_SHA)
_FIXTURE_SOURCE = _pinned_source("test_context_codec.py", _BASELINE_FIXTURE_SHA)
_CODEC = _load_codec(_HERE / "context_codec.py", "_qualified_prior_candidate")
_BASELINE = _load_codec(_SOURCE_ROOT / "context_codec.py", "_qualified_prior_baseline")
_FIXTURE_AST = ast.parse(_FIXTURE_SOURCE, filename="pinned/test_context_codec.py")
_FUNCTIONS = [node for node in _FIXTURE_AST.body
              if isinstance(node, ast.FunctionDef) and node.name == "_synthetic_context"]
if len(_FUNCTIONS) != 1 or _FUNCTIONS[0].decorator_list:
    raise RuntimeError("PINNED_SYNTHETIC_FUNCTION")
_FIXTURE_NAMESPACE = {"copy": copy, "_CODEC": _CODEC}
exec(compile(ast.Module(body=_FUNCTIONS, type_ignores=[]),
             "pinned/_synthetic_context", "exec"), _FIXTURE_NAMESPACE)
_synthetic_context = _FIXTURE_NAMESPACE["_synthetic_context"]


class QualifiedPriorTests(unittest.TestCase):
    def setUp(self):
        self.context = _synthetic_context()
        self.prior = self.context["nativeProjection"]["prior"]
        self.value = _CODEC.qualified_prior(self.context)

    def reject(self, value, code=None, expected=_CONTEXT_SHA):
        if code is None:
            with self.assertRaises(_CODEC.ContextCodecError):
                _CODEC.validate_qualified_prior(value, expected)
        else:
            with self.assertRaises(_CODEC.ContextCodecError) as rejected:
                _CODEC.validate_qualified_prior(value, expected)
            self.assertEqual(rejected.exception.code, code)

    def test_constructs_independent_current_schema_and_order(self):
        self.assertEqual(_CODEC.QUALIFIED_PRIOR_SCHEMA, _SCHEMA)
        self.assertEqual(tuple(_CODEC.QUALIFIED_PRIOR_KEYS), _KEYS)
        self.assertEqual(type(self.value), dict)
        self.assertEqual(tuple(self.value), _KEYS)
        self.assertEqual(self.value["schema"], _SCHEMA)
        self.assertEqual(self.value["inputContextSha256"], _CONTEXT_SHA)
        for key in _KEYS[2:]:
            self.assertEqual(self.value[key], self.prior[key])
        self.assertNotEqual(self.value["inputContextSha256"], self.value["resultSha256"])

    def test_new_top_map_borrows_nested_prior_objects(self):
        second = _CODEC.qualified_prior(self.context)
        self.assertIsNot(self.value, self.prior)
        self.assertIsNot(second, self.value)
        for key in ("priorOriginalReceipt", "trialDisposition"):
            self.assertIs(self.value[key], self.prior[key])
            self.assertIs(second[key], self.prior[key])
        self.assertIs(self.value["trialDisposition"]["requestedNetIds"],
                      self.prior["trialDisposition"]["requestedNetIds"])
        self.assertIsNot(self.value["trialDisposition"],
                         self.context["nativeProjection"]["capture"]["trialDisposition"])

    def test_producer_preserves_input_bytes_and_container_identities(self):
        before = _CODEC.canonical_context(self.context)
        projection = self.context["nativeProjection"]
        physical = self.context["physicalContext"]
        receipt = self.prior["priorOriginalReceipt"]
        trial = self.prior["trialDisposition"]
        _CODEC.qualified_prior(self.context)
        self.assertEqual(_CODEC.canonical_context(self.context), before)
        self.assertIs(self.context["nativeProjection"], projection)
        self.assertIs(self.context["physicalContext"], physical)
        self.assertIs(projection["prior"], self.prior)
        self.assertIs(self.prior["priorOriginalReceipt"], receipt)
        self.assertIs(self.prior["trialDisposition"], trial)
        self.assertEqual(tuple(self.context), ("schema", "nativeProjection", "physicalContext"))
        self.assertNotIn("inputContextSha256", self.prior)

    def test_consumer_returns_original_and_preserves_valid_or_invalid_input(self):
        before = _CODEC.canonical_json(self.value)
        receipt = self.value["priorOriginalReceipt"]
        self.assertIs(_CODEC.validate_qualified_prior(self.value, _CONTEXT_SHA), self.value)
        self.assertEqual(_CODEC.canonical_json(self.value), before)
        self.assertIs(self.value["priorOriginalReceipt"], receipt)
        self.reject(self.value, "QUALIFIED_PRIOR_INPUT_CONTEXT_SHA256_MISMATCH", "0" * 64)
        self.assertEqual(_CODEC.canonical_json(self.value), before)
        self.assertIs(self.value["priorOriginalReceipt"], receipt)

    def test_rejects_top_shape_order_unknown_missing_and_legacy_prior(self):
        swapped = dict(self.value)
        swapped["schema"] = swapped.pop("schema")
        extra = dict(self.value, unknown=None)
        missing = dict(self.value)
        del missing["priorRunId"]
        for value in (None, [], self.prior, swapped, extra, missing):
            with self.subTest(value_type=type(value).__name__, keys=list(value) if type(value) is dict else None):
                self.reject(value, "QUALIFIED_PRIOR_KEYS")

    def test_rejects_legacy_or_foreign_schema_with_current_shape(self):
        for schema in ("tsj-cohort-terminal-qualified-prior-v1",
                       "tsj-cohort-terminal-input-context-v2", "foreign-schema", None, 2):
            with self.subTest(schema=schema):
                value = copy.deepcopy(self.value)
                value["schema"] = schema
                self.reject(value, "QUALIFIED_PRIOR_SCHEMA")

    def test_rejects_expected_current_sha_format(self):
        for expected in (None, True, 1, "", "a" * 63, "a" * 65,
                         "A" * 64, "g" * 64, "a" * 63 + "\n"):
            with self.subTest(expected=expected):
                self.reject(self.value, "EXPECTED_INPUT_CONTEXT_SHA256", expected)

    def test_rejects_result_current_sha_format_and_binding(self):
        for digest in (None, True, 1, "a" * 63, "a" * 65, "A" * 64, "g" * 64):
            with self.subTest(digest=digest):
                value = copy.deepcopy(self.value)
                value["inputContextSha256"] = digest
                self.reject(value, "QUALIFIED_PRIOR_INPUT_CONTEXT_SHA256")
        value = copy.deepcopy(self.value)
        value["inputContextSha256"] = "0" * 64
        self.reject(value, "QUALIFIED_PRIOR_INPUT_CONTEXT_SHA256_MISMATCH")

    def test_rejects_nonordinary_json_domain_and_mapping_subclasses(self):
        class DictSubclass(dict):
            pass

        self.reject(DictSubclass(self.value), "QUALIFIED_PRIOR_KEYS")
        for invalid in (1.0, float("nan"), object(), ("A_COIL_LOW", "B_COIL_LOW")):
            with self.subTest(kind=type(invalid).__name__):
                value = copy.deepcopy(self.value)
                value["trialDisposition"]["cohortNetIds"] = invalid
                self.reject(value, "JSON_VALUE_TYPE")
        value = copy.deepcopy(self.value)
        value["priorOriginalReceipt"]["receiptBytes"] = 2**31
        self.reject(value, "JSON_INTEGER_RANGE")
        value = copy.deepcopy(self.value)
        value["trialDisposition"]["reason"] = "nonascii_é"
        self.reject(value, "JSON_STRING")

    def test_rejects_nested_aliases_and_cycles(self):
        value = copy.deepcopy(self.value)
        trial = value["trialDisposition"]
        trial["requestedNetIds"] = trial["cohortNetIds"]
        self.reject(value, "JSON_ALIAS")
        value = copy.deepcopy(self.value)
        value["trialDisposition"]["cohortNetIds"].append(value)
        self.reject(value, "JSON_ALIAS")
        value = copy.deepcopy(self.value)
        value["priorOriginalReceipt"] = value["trialDisposition"]
        self.reject(value, "JSON_ALIAS")

    def test_receipt_and_trial_nested_key_order_remains_strict(self):
        for nested in ("priorOriginalReceipt", "trialDisposition"):
            for change in ("reorder", "extra", "missing"):
                with self.subTest(nested=nested, change=change):
                    value = copy.deepcopy(self.value)
                    target = value[nested]
                    first = next(iter(target))
                    if change == "reorder":
                        target[first] = target.pop(first)
                    elif change == "extra":
                        target["extra"] = None
                    else:
                        del target[first]
                    self.reject(value, "PRIOR_RECEIPT_KEYS" if nested == "priorOriginalReceipt"
                                else "PRIOR_TRIAL_KEYS")

    def test_receipt_semantics_remain_bound_to_prior_operation(self):
        cases = (
            ("receiptPath", "other.jsonl", "PRIOR_RECEIPT_PATH"),
            ("receiptBytes", 0, "PRIOR_RECEIPT_BYTES"),
            ("receiptBytes", True, "PRIOR_RECEIPT_BYTES"),
            ("receiptSha256", "e" * 64, "PRIOR_RECEIPT_OPERATION_BINDING"),
            ("receiptOrdinal", 306, "PRIOR_RECEIPT_ORDINALS"),
            ("receiptOrdinal", True, "PRIOR_RECEIPT_ORDINALS"),
            ("lineageOrdinal", 78, "PRIOR_RECEIPT_ORDINALS"),
        )
        for key, invalid, code in cases:
            with self.subTest(key=key, invalid=invalid):
                value = copy.deepcopy(self.value)
                value["priorOriginalReceipt"][key] = invalid
                self.reject(value, code)

    def test_prior_scalar_semantics_remain_current(self):
        for key in ("manifestSha256", "sourceManifestSha256", "readerSha256",
                    "resultSha256", "operation1Sha256", "parentReceiptSha256"):
            with self.subTest(key=key):
                value = copy.deepcopy(self.value)
                value[key] = "A" * 64
                self.reject(value, "PRIOR_" + key.upper())
        for key, invalid, code in (
            ("sourceParent", "d" * 39, "PRIOR_SOURCE_PARENT"),
            ("priorRunId", "legacy-run", "PRIOR_RUN_ID"),
        ):
            value = copy.deepcopy(self.value)
            value[key] = invalid
            self.reject(value, code)

    def test_trial_semantics_remain_current(self):
        for key, invalid, code in (
            ("status", "SKIPPED", "PRIOR_TRIAL_QUALIFIED"),
            ("reason", "OTHER", "PRIOR_TRIAL_QUALIFIED"),
            ("capturedFailedNetId", "B_COIL_LOW", "PRIOR_TRIAL_QUALIFIED"),
            ("controlFailedNetId", "A_COIL_LOW", "PRIOR_TRIAL_QUALIFIED"),
            ("feedbackValidated", False, "PRIOR_TRIAL_QUALIFIED"),
            ("feedbackValidated", 1, "PRIOR_TRIAL_FLAGS"),
            ("orderChanged", 0, "PRIOR_TRIAL_FLAGS"),
            ("cohortNetIds", ["B_COIL_LOW", "A_COIL_LOW"], "PRIOR_TRIAL_COHORT_BINDING"),
            ("cohortNetIds", ["A_COIL_LOW", "A_COIL_LOW"], "PRIOR_TRIAL_COHORT_DUPLICATE"),
            ("requestedNetIds", ["A_COIL_LOW"] * 60, "PRIOR_TRIAL_REQUESTED_DUPLICATE"),
        ):
            with self.subTest(key=key, invalid=invalid):
                value = copy.deepcopy(self.value)
                value["trialDisposition"][key] = invalid
                self.reject(value, code)
        value = copy.deepcopy(self.value)
        value["trialDisposition"]["orderChanged"] = True
        self.assertIs(_CODEC.validate_qualified_prior(value, _CONTEXT_SHA), value)

    def test_producer_rejects_invalid_context_without_mutation(self):
        invalid = copy.deepcopy(self.context)
        invalid["nativeProjection"]["prior"]["priorOriginalReceipt"]["receiptBytes"] = 0
        before = _CODEC.canonical_json(invalid)
        with self.assertRaises(_CODEC.ContextCodecError) as rejected:
            _CODEC.qualified_prior(invalid)
        self.assertEqual(rejected.exception.code, "PRIOR_RECEIPT_BYTES")
        self.assertEqual(_CODEC.canonical_json(invalid), before)
        aliased = copy.deepcopy(self.context)
        aliased["nativeProjection"]["capture"]["trialDisposition"] = aliased["nativeProjection"]["prior"]["trialDisposition"]
        with self.assertRaises(_CODEC.ContextCodecError) as rejected:
            _CODEC.qualified_prior(aliased)
        self.assertEqual(rejected.exception.code, "JSON_ALIAS")

    def test_canonical_context_bytes_match_pinned_baseline_and_literal_oracle(self):
        baseline = _BASELINE.canonical_context(self.context)
        candidate = _CODEC.canonical_context(self.context)
        self.assertEqual(candidate, baseline)
        self.assertEqual(len(candidate), _CONTEXT_BYTES)
        self.assertEqual(hashlib.sha256(candidate).hexdigest(), _CONTEXT_SHA)
        self.assertEqual(_CODEC.context_sha256(self.context), _CONTEXT_SHA)
        self.assertEqual(_CODEC.parse_context(candidate), self.context)
        self.assertEqual((_HERE / "context_codec.py").read_bytes()[:len(_BASELINE_SOURCE)],
                         _BASELINE_SOURCE)

    def test_factory_bytes_match_pinned_baseline_and_literal_oracle(self):
        baseline = _BASELINE.generate_input_java(self.context, _CONTEXT_SHA)
        candidate = _CODEC.generate_input_java(self.context, _CONTEXT_SHA)
        self.assertEqual(candidate, baseline)
        self.assertEqual(len(candidate), _FACTORY_BYTES)
        self.assertEqual(hashlib.sha256(candidate).hexdigest(), _FACTORY_SHA)
        self.assertEqual(_CODEC.native_projection(self.context),
                         _BASELINE.native_projection(self.context))

    def test_full_body_alias_validation_remains_a_separate_boundary(self):
        self.assertIs(_CODEC.validate_qualified_prior(self.value, _CONTEXT_SHA), self.value)
        with self.assertRaises(_CODEC.ContextCodecError) as rejected:
            _CODEC.canonical_json({"qualifiedPrior": self.value,
                                   "anotherReceipt": self.prior["priorOriginalReceipt"]})
        self.assertEqual(rejected.exception.code, "JSON_ALIAS")
        self.assertTrue(_CODEC.canonical_json({"qualifiedPrior": self.value}))


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0]] + _UNITTEST_ARGS)
