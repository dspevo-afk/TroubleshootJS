"""Synthetic-only tests for the current authored context codec.

Load the adjacent implementation explicitly so this file also works under
Python isolated mode, where the script directory is not added to sys.path.
"""

import copy
import importlib.util
import unittest
from pathlib import Path


_SOURCE = Path(__file__).resolve().with_name("context_codec.py")
_SPEC = importlib.util.spec_from_file_location("_tsj_context_codec_under_test", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("CONTEXT_CODEC_SOURCE_LOAD")
_CODEC = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_CODEC)


def _synthetic_context():
    captured = ["NET_%03d" % index for index in range(60)]
    captured[10] = "D_SENSE"
    captured[43] = "A_COIL_LOW"
    captured[50] = "B_COIL_LOW"
    requested = list(captured)
    trial = {
        "status": "RUN",
        "reason": "TRIAL_EXECUTED",
        "capturedFailedNetId": "A_COIL_LOW",
        "controlFailedNetId": "B_COIL_LOW",
        "cohortNetIds": ["A_COIL_LOW", "B_COIL_LOW"],
        "requestedNetIds": requested,
        "orderChanged": False,
        "feedbackValidated": True,
    }
    prior = {
        "sourceParent": "d" * 40,
        "manifestSha256": "1" * 64,
        "sourceManifestSha256": "2" * 64,
        "readerSha256": "3" * 64,
        "resultSha256": "4" * 64,
        "operation1Sha256": "5" * 64,
        "parentReceiptSha256": "6" * 64,
        "priorRunId": "12345678-1234-4234-8234-123456789abc",
        "priorOriginalReceipt": {
            "receiptPath": "op-1.jsonl",
            "receiptBytes": 4096,
            "receiptSha256": "5" * 64,
            "receiptOrdinal": 307,
            "lineageOrdinal": 77,
        },
        "trialDisposition": copy.deepcopy(trial),
    }
    capture = {
        "cohortNetIds": ["A_COIL_LOW", "B_COIL_LOW"],
        "capturedNetIds": captured,
        "trialDisposition": trial,
        "controlDisposition": "NO_PATH",
        "trialOutcome": "CAP_REJECTED",
        "passIndex": 2,
        "failedNetId": "A_COIL_LOW",
        "priorPromotedNetId": "D_SENSE",
        "liveSearchIndex": 44,
    }
    owner = {
        "boardMode": "FULLER_TWO_LAYER",
        "maxExpansions": 1_000_000,
        "expandedAtFailure": 918_222,
        "maxVias": 48,
        "viaCost": 60,
        "maxTransitions": 4,
        "branchStrategy": "CANONICAL_NEAREST",
        "netStrategy": "CANONICAL",
        "roleWidthsEnabled": True,
        "orderingStreamEnabled": True,
    }
    seeds = {
        "rootSeed": "9007199254740993",
        "placementSeed": "-6587958367974952701",
        "routingSeed": "8445301674744508279",
    }
    source_identity = {
        "geometryBeforeSha256": "8" * 64,
        "geometryAfterSha256": "9" * 64,
        "detachedGeometrySha256": "a" * 64,
        "effectsBeforeSha256": "b" * 64,
        "effectsAfterSha256": "c" * 64,
        "traceCount": 0,
        "holeCount": 0,
        "padLandIdentity": {
            "format": "TSJ_PAD_LAND_ASCII_V1",
            "rowCount": 246,
            "sha256": "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0",
        },
    }

    pad_lands = []
    p07_pads = []
    for index in range(246):
        pad_id = "PAD_%03d" % index
        net_id = captured[index % len(captured)]
        x = index % 100
        y = index // 100
        pad_lands.append({
            "padId": pad_id,
            "netId": net_id,
            "x": x,
            "y": y,
            "bounds": [x, y, 1, 1],
            "attachment": "PLATED_THROUGH_HOLE",
            "layer": "TOP+BOTTOM",
        })
        p07_pads.append({
            "record": "PAD",
            "group": 0,
            "padId": pad_id,
            "netId": net_id,
            "componentId": "COMP_%03d" % index,
            "x": x,
            "y": y,
            "role": "SYNTHETIC_ROLE",
        })

    return {
        "schema": _CODEC.CONTEXT_SCHEMA,
        "nativeProjection": {
            "prior": prior,
            "capture": capture,
            "ownerSettings": owner,
            "seedValues": seeds,
            "sourceIdentity": source_identity,
        },
        "physicalContext": {
            "gridWidth": 100,
            "gridHeight": 100,
            "padLands": pad_lands,
            "qualifiedP07Pads": p07_pads,
        },
    }


class ContextCodecTests(unittest.TestCase):
    def setUp(self):
        self.context = _synthetic_context()

    def test_context_order_canonical_bytes_and_round_trip(self):
        raw = _CODEC.canonical_context(self.context)
        self.assertTrue(raw.endswith(b"\n"))
        self.assertFalse(raw.endswith(b"\n\n"))
        self.assertLessEqual(len(raw), _CODEC.MAX_CONTEXT_BYTES)
        self.assertEqual(raw, _CODEC.canonical_json(self.context))
        self.assertEqual(_CODEC.parse_context(raw), self.context)
        self.assertEqual(
            tuple(_CODEC.parse_context(raw)),
            ("schema", "nativeProjection", "physicalContext"),
        )

    def test_reordered_and_unknown_context_keys_reject(self):
        reordered = {
            "nativeProjection": self.context["nativeProjection"],
            "schema": self.context["schema"],
            "physicalContext": self.context["physicalContext"],
        }
        with self.assertRaises(_CODEC.ContextCodecError):
            _CODEC.validate_context(reordered)
        unknown = copy.deepcopy(self.context)
        unknown["runtimeIdentity"] = {}
        with self.assertRaises(_CODEC.ContextCodecError):
            _CODEC.validate_context(unknown)

    def test_duplicate_float_and_noncanonical_json_reject(self):
        duplicate = (b'{"schema":"x","schema":"x",'
                     b'"nativeProjection":{},"physicalContext":{}}\n')
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_DUPLICATE_KEY"):
            _CODEC.parse_context(duplicate)
        raw = _CODEC.canonical_context(self.context).replace(
            b'"gridWidth":100', b'"gridWidth":100.0', 1)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_FLOAT"):
            _CODEC.parse_context(raw)
        spaced = _CODEC.canonical_context(self.context).replace(b'"schema":', b'"schema" :', 1)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "CONTEXT_NOT_CANONICAL"):
            _CODEC.parse_context(spaced)

    def test_context_document_size_and_framing_bounds(self):
        too_large = b" " * (_CODEC.MAX_CONTEXT_BYTES + 1)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "CONTEXT_DOCUMENT_SIZE"):
            _CODEC.parse_context(too_large)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "CONTEXT_FRAMING"):
            _CODEC.parse_context(b"{}")

    def test_bool_float_and_signed_seed_rules(self):
        bad_bool = copy.deepcopy(self.context)
        bad_bool["physicalContext"]["gridWidth"] = True
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "GRID_WIDTH"):
            _CODEC.validate_context(bad_bool)
        bad_seed = copy.deepcopy(self.context)
        bad_seed["nativeProjection"]["seedValues"]["rootSeed"] = 9007199254740993
        with self.assertRaises(_CODEC.ContextCodecError):
            _CODEC.validate_context(bad_seed)
        bad_seed = copy.deepcopy(self.context)
        bad_seed["nativeProjection"]["seedValues"]["rootSeed"] = "09007199254740993"
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "SEED_ROOTSEED_FORMAT"):
            _CODEC.validate_context(bad_seed)

    def test_pad_schemas_order_geometry_and_independent_binding(self):
        self.assertTrue(_CODEC.validate_context(self.context))
        bad = copy.deepcopy(self.context)
        bad["physicalContext"]["padLands"][0]["bounds"] = [0, 0, 0, 1]
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "PAD_LAND_BOUNDS_BINDING"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["physicalContext"]["qualifiedP07Pads"][0]["netId"] = "OTHER_NET"
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "PAD_P07_PHYSICAL_BINDING"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["physicalContext"]["padLands"][0]["extra"] = 1
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "PAD_LAND_KEYS"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["physicalContext"]["qualifiedP07Pads"][0]["group"] = 640
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "P07_PAD_GROUP"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["physicalContext"]["qualifiedP07Pads"][0]["group"] = 1
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "P07_PAD_GROUP_ORDER"):
            _CODEC.validate_context(bad)

    def test_depth_visits_alias_and_cycle_limits(self):
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_OBJECT_KEY_TYPE"):
            _CODEC.canonical_json({1: "not-a-json-object-key"})

        nested = None
        for _ in range(_CODEC.MAX_DEPTH + 1):
            nested = [nested]
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_DEPTH"):
            _CODEC.canonical_json(nested)

        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_VISITS"):
            _CODEC.canonical_json([None] * _CODEC.MAX_VISITS)

        shared = {"leaf": 1}
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_ALIAS"):
            _CODEC.data_entries([shared, shared])
        cycle = []
        cycle.append(cycle)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "JSON_ALIAS"):
            _CODEC.canonical_json(cycle)

    def test_entry_count_rule_cap_and_native_projection_copy(self):
        self.assertEqual(_CODEC.data_entries({"outer": [{"x": 1}, 2]}), 7)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "DATA_ENTRIES_LIMIT"):
            _CODEC.data_entries([0] * _CODEC.MAX_DATA_ENTRIES)

        projection = _CODEC.native_projection(self.context)
        self.assertIsNot(projection, self.context["nativeProjection"])
        self.assertIsNot(projection["prior"], self.context["nativeProjection"]["prior"])
        projection["prior"]["sourceParent"] = "changed-copy"
        self.assertEqual(
            self.context["nativeProjection"]["prior"]["sourceParent"],
            "d" * 40,
        )
        self.assertEqual(
            _CODEC.native_projection(self.context)["prior"]["sourceParent"],
            "d" * 40,
        )

    def test_java_literals_are_octal_and_source_safe(self):
        payload = '"; System.exit(9); \\u000a'
        literal = _CODEC.java_ascii_literal(payload)
        self.assertEqual(literal, '"' + "".join("\\%03o" % ord(ch) for ch in payload) + '"')
        self.assertNotIn(payload, literal)
        self.assertEqual(_CODEC.java_ascii_literal(""), '""')
        with self.assertRaises(_CODEC.ContextCodecError):
            _CODEC.java_ascii_literal("é")
        with self.assertRaises(_CODEC.ContextCodecError):
            _CODEC.java_ascii_literal("x" * (_CODEC.MAX_TEXT_LENGTH + 1))

    def test_generated_v2_api_hash_fresh_tree_and_no_runtime_identity(self):
        digest = _CODEC.context_sha256(self.context)
        source = _CODEC.generate_input_java(self.context, digest)
        self.assertLessEqual(len(source), _CODEC.MAX_GENERATED_BYTES)
        self.assertTrue(source.isascii())
        self.assertIn(b"class CohortTerminalTelemetryInputV2", source)
        self.assertIn(b"static String schemaTag()", source)
        self.assertIn(b"static String contextSha256()", source)
        self.assertIn(b"static int dataEntries()", source)
        self.assertIn(b"static java.util.Map<String,Object> dataJson()", source)
        self.assertIn(b"new java.util.LinkedHashMap<String,Object>()", source)
        self.assertIn(b"new java.util.ArrayList<Object>()", source)
        self.assertIn(b"public static String schemaTag()", source)
        self.assertNotIn(b"static String runId()", source)
        self.assertNotIn(b"static String sourceManifestSha256()", source)
        self.assertNotIn(b"physicalContext", source)
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "EXPECTED_CONTEXT_SHA256_MISMATCH"):
            _CODEC.generate_input_java(self.context, "0" * 64)

    def test_fixed_capture_seed_and_identity_rejections(self):
        bad = copy.deepcopy(self.context)
        bad["nativeProjection"]["prior"]["priorOriginalReceipt"]["receiptSha256"] = "e" * 64
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "PRIOR_RECEIPT_OPERATION_BINDING"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["nativeProjection"]["capture"]["liveSearchIndex"] = 45
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "CAPTURE_LIVE_SEARCH_INDEX"):
            _CODEC.validate_context(bad)
        bad = copy.deepcopy(self.context)
        bad["nativeProjection"]["sourceIdentity"]["padLandIdentity"]["rowCount"] = 245
        with self.assertRaisesRegex(_CODEC.ContextCodecError, "PAD_IDENTITY_COUNT"):
            _CODEC.validate_context(bad)


if __name__ == "__main__":
    unittest.main()