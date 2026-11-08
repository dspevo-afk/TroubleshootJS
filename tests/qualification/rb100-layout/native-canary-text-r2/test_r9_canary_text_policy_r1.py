"""Focused R9 canary text-policy boundaries."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest
from unittest import mock


POLICY_PATH = Path(__file__).with_name("r9_canary_text_policy_r1.py")
SPEC = importlib.util.spec_from_file_location("r9_canary_text_policy_r1", POLICY_PATH)
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)


def geometry_arm(geometry):
    return {
        "freshRun": {
            "layout": {
                "components": [{"geometryCanonical": geometry}],
            },
        },
    }


def nested_lists(count, leaf=None):
    value = leaf
    for _ in range(count):
        value = [value]
    return value


class R9CanaryTextPolicyTests(unittest.TestCase):
    def assert_code(self, expected, value):
        with self.assertRaises(policy.CanaryTextPolicyError) as caught:
            policy.validate_canary_text_tree(value)
        self.assertEqual("BF_" + expected, caught.exception.code)

    def test_exact_root_arm_paths_allow_actual_1083_character_geometry(self):
        document = {
            "complete": geometry_arm("G" * 1083),
            "blocked": geometry_arm("B" * 1083),
            "nonBranchQuality": geometry_arm("Q" * 1083),
        }
        summary = policy.validate_canary_text_tree(document)
        self.assertEqual(policy.SUMMARY_SCHEMA, summary["schema"])
        self.assertEqual(3, summary["extendedGeometryCanonicalValues"])
        self.assertTrue(all(type(value) in (str, int)
                            for value in summary.values()))
        self.assertNotIn("status", summary)
        self.assertNotIn("runId", summary)

    def test_geometry_canonical_4096_and_4097_boundaries(self):
        accepted = policy.validate_canary_text_tree(
            {"complete": geometry_arm("x" * 4096)})
        self.assertEqual(1, accepted["extendedGeometryCanonicalValues"])
        self.assert_code("R9_GEOMETRY_CANONICAL_LENGTH",
                         {"complete": geometry_arm("x" * 4097)})

    def test_ordinary_string_and_key_256_257_boundaries(self):
        self.assertEqual(1, policy.validate_canary_text_tree(
            {"ordinary": "v" * 256})["stringValues"])
        self.assert_code("R9_TEXT_LENGTH", {"ordinary": "v" * 257})
        self.assertEqual(1, policy.validate_canary_text_tree(
            {"k" * 256: None})["visitedKeys"])
        self.assert_code("R9_KEY_LENGTH", {"k" * 257: None})

    def test_key_name_or_wrong_path_does_not_extend_leaf_limit(self):
        self.assert_code("R9_TEXT_LENGTH", {
            "other": {"geometryCanonical": "x" * 257},
        })
        self.assert_code("R9_ARM_TYPE", {
            "complete": [{"freshRun": {"layout": {
                "components": [{"geometryCanonical": "x" * 257}],
            }}}],
        })
        self.assert_code("R9_COMPONENTS_TYPE", {
            "complete": {"freshRun": {"layout": {
                "components": {"0": {"geometryCanonical": "x" * 257}},
            }}},
        })
        self.assert_code("R9_TEXT_LENGTH", {
            "complete": {"freshRun": {"layout": {
                "components": [{"nested": {"geometryCanonical": "x" * 257}}],
            }}},
        })

    def test_integer_boolean_null_ascii_and_exact_json_types(self):
        accepted = {
            "min": policy.SIGNED_LONG_MIN,
            "max": policy.SIGNED_LONG_MAX,
            "yes": True,
            "no": False,
            "null": None,
            "text": "ASCII ~",
        }
        policy.validate_canary_text_tree(accepted)
        self.assert_code("R9_INTEGER_RANGE", {"large": policy.SIGNED_LONG_MAX + 1})
        self.assert_code("R9_INTEGER_RANGE", {"small": policy.SIGNED_LONG_MIN - 1})
        self.assert_code("R9_NODE_TYPE", {"float": 1.0})
        self.assert_code("R9_NODE_TYPE", {"bytes": b"ascii"})
        self.assert_code("R9_TEXT_ASCII", {"text": "café"})
        self.assert_code("R9_KEY_ASCII", {"café": 1})

    def test_integer_object_keys_are_rejected_without_numeric_key_escape(self):
        self.assert_code("R9_KEY_TYPE", {"complete": {0: {
            "geometryCanonical": "x" * 4096,
        }}})

    def test_known_root_path_container_types_are_enforced(self):
        invalid = (
            ("R9_ROOT_TYPE", []),
            ("R9_ARM_TYPE", {"complete": None}),
            ("R9_ARM_TYPE", {"blocked": []}),
            ("R9_QUALITY_TYPE", {"nonBranchQuality": []}),
            ("R9_FRESH_RUN_TYPE", {"complete": {"freshRun": []}}),
            ("R9_LAYOUT_TYPE", {"blocked": {"freshRun": {"layout": []}}}),
            ("R9_COMPONENTS_TYPE", {"complete": {"freshRun": {
                "layout": {"components": {}},
            }}}),
            ("R9_COMPONENT_TYPE", {"complete": {"freshRun": {
                "layout": {"components": [[]]},
            }}}),
            ("R9_GEOMETRY_CANONICAL_TYPE", {"blocked": {"freshRun": {
                "layout": {"components": [{"geometryCanonical": []}]},
            }}}),
        )
        for expected, document in invalid:
            with self.subTest(expected=expected):
                self.assert_code(expected, document)

        policy.validate_canary_text_tree({"complete": {
            "freshRun": {"layout": None},
        }, "nonBranchQuality": None})

    def test_depth_and_visit_budgets_are_independent(self):
        self.assertEqual(64, policy.MAX_DEPTH)
        self.assertEqual(1_048_576, policy.MAX_VISITS)
        policy.validate_canary_text_tree({"depth": nested_lists(63)})
        policy.validate_canary_text_tree({"depth": nested_lists(63, leaf=[])})
        policy.validate_canary_text_tree({"depth": nested_lists(63, leaf={})})
        self.assert_code("R9_DEPTH_LIMIT", {"depth": nested_lists(64)})

        with mock.patch.object(policy, "MAX_DEPTH", 100), \
                mock.patch.object(policy, "MAX_VISITS", 5):
            summary = policy.validate_canary_text_tree(
                {"a": 1, "b": 2, "c": 3, "d": 4})
            self.assertEqual(4, summary["visitedKeys"])
            with self.assertRaises(policy.CanaryTextPolicyError) as caught:
                policy.validate_canary_text_tree(
                    {"a": 1, "b": 2, "c": 3, "d": 4, "e": 5})
            self.assertEqual("BF_R9_VISIT_LIMIT", caught.exception.code)


if __name__ == "__main__":
    unittest.main()
