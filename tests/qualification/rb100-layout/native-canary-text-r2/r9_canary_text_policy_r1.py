"""Narrow R9 canary JSON text-domain policy, independent of semantic checks."""
from __future__ import annotations

import sys


MAX_STRING_CHARS = 256
MAX_GEOMETRY_CANONICAL_CHARS = 4096
MAX_DEPTH = 64
MAX_VISITS = 1_048_576
SIGNED_LONG_MIN = -(2**63)
SIGNED_LONG_MAX = 2**63 - 1
CANARY_ARMS = ("complete", "blocked", "nonBranchQuality")
SUMMARY_SCHEMA = "tsj-r9-canary-text-policy-summary-v1"


class CanaryTextPolicyError(ValueError):
    """Validation failure with a stable, value-free diagnostic code."""

    def __init__(self, code: str):
        self.code = code if code.startswith("BF_") else "BF_" + code
        super().__init__(self.code)


def _need(condition: bool, code: str) -> None:
    if not condition:
        raise CanaryTextPolicyError(code)


def _printable_ascii(value: str) -> bool:
    return all(0x20 <= ord(char) <= 0x7e for char in value)


def _is_geometry_canonical_path(path: tuple[object, ...]) -> bool:
    return (
        len(path) == 6
        and path[0] in CANARY_ARMS
        and path[1] == "freshRun"
        and path[2] == "layout"
        and path[3] == "components"
        and type(path[4]) is int
        and path[5] == "geometryCanonical"
    )


def _validate_path_type(value, path: tuple[object, ...]) -> None:
    if not path:
        _need(type(value) is dict, "R9_ROOT_TYPE")
        return

    if len(path) == 1:
        if path[0] in ("complete", "blocked"):
            _need(type(value) is dict, "R9_ARM_TYPE")
        elif path[0] == "nonBranchQuality":
            _need(value is None or type(value) is dict, "R9_QUALITY_TYPE")
        return

    if path[0] not in CANARY_ARMS:
        return
    if len(path) == 2 and path[1] == "freshRun":
        _need(type(value) is dict, "R9_FRESH_RUN_TYPE")
    elif len(path) == 3 and path[1] == "freshRun" and path[2] == "layout":
        _need(value is None or type(value) is dict, "R9_LAYOUT_TYPE")
    elif (len(path) == 4 and path[1] == "freshRun"
          and path[2] == "layout" and path[3] == "components"):
        _need(type(value) is list, "R9_COMPONENTS_TYPE")
    elif (len(path) == 5 and path[1] == "freshRun"
          and path[2] == "layout" and path[3] == "components"
          and type(path[4]) is int):
        _need(type(value) is dict, "R9_COMPONENT_TYPE")
    elif _is_geometry_canonical_path(path):
        _need(type(value) is str, "R9_GEOMETRY_CANONICAL_TYPE")


def validate_canary_text_tree(doc):
    """Validate a parsed tree's text/type/depth/visit policy only.

    The only expanded string limit applies to a string at the exact component
    geometryCanonical path in one of the three root canary arms. This function
    does not validate canary identity or semantic outcomes.
    """
    stack = [(doc, (), 0)]
    visited_values = 0
    visited_keys = 0
    string_values = 0
    extended_geometry_values = 0

    while stack:
        value, path, depth = stack.pop()
        visited_values += 1
        _need(visited_values <= MAX_VISITS, "R9_VISIT_LIMIT")
        _need(depth <= MAX_DEPTH, "R9_DEPTH_LIMIT")
        _validate_path_type(value, path)

        value_type = type(value)
        if value is None or value_type is bool:
            continue
        if value_type is int:
            _need(SIGNED_LONG_MIN <= value <= SIGNED_LONG_MAX,
                  "R9_INTEGER_RANGE")
            continue
        if value_type is str:
            string_values += 1
            _need(_printable_ascii(value), "R9_TEXT_ASCII")
            limit = (MAX_GEOMETRY_CANONICAL_CHARS
                     if _is_geometry_canonical_path(path)
                     else MAX_STRING_CHARS)
            _need(len(value) <= limit,
                  "R9_GEOMETRY_CANONICAL_LENGTH"
                  if limit == MAX_GEOMETRY_CANONICAL_CHARS
                  else "R9_TEXT_LENGTH")
            if limit == MAX_GEOMETRY_CANONICAL_CHARS and len(value) > MAX_STRING_CHARS:
                extended_geometry_values += 1
            continue
        if value_type is list:
            for index in range(len(value) - 1, -1, -1):
                stack.append((value[index], path + (index,), depth + 1))
            continue
        if value_type is dict:
            for key, child in value.items():
                visited_keys += 1
                _need(type(key) is str, "R9_KEY_TYPE")
                _need(_printable_ascii(key), "R9_KEY_ASCII")
                _need(len(key) <= MAX_STRING_CHARS, "R9_KEY_LENGTH")
                stack.append((child, path + (key,), depth + 1))
            continue
        raise CanaryTextPolicyError("R9_NODE_TYPE")

    return {
        "schema": SUMMARY_SCHEMA,
        "visitedValues": visited_values,
        "visitedKeys": visited_keys,
        "stringValues": string_values,
        "extendedGeometryCanonicalValues": extended_geometry_values,
    }


def main(argv=None) -> int:
    """Keep direct execution fail-closed; the API is intended for import use."""
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("BF_R9_ISOLATED_I_B_REQUIRED\n")
        return 2
    sys.stderr.write("BF_R9_POLICY_API_ONLY\n")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
