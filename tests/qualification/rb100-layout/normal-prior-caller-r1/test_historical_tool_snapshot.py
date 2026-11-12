"""Synthetic metadata-only tests for the accepted SOURCE tool snapshot."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
import types
import unittest


MAX_CALLER_SOURCE_BYTES = 4 * 1024 * 1024
SNAPSHOT_INPUTS = (
    ("config", 4894, "c8e937946f93f62801450eb989e73016d26736d53e1d2487cb8d8843f0add6eb", 1024 * 1024),
    ("source manifest", 2124, "e5ea5ac6bd85ffdb0be078895e87b1da4d3d9488469029d8971751ec4846e80d", 1024 * 1024),
    ("preflight evidence", 1595, "8244eb5c9e5e2ee6c78ed305262dad46c4a0c5857202d3e6c6d9fa6f99241837", 64 * 1024),
)
TOOL_ROLES = (
    "qualificationPython",
    "rootCallPowerShell",
    "nativeJava",
    "rootCallHarness",
    "processModule",
)
DTO_KEYS = (
    "schema",
    "origin",
    "acceptedSourceCommit",
    "configSha256",
    "sourceManifestSha256",
    "preflightEvidenceSha256",
    "allowlistSha256",
    "rootsSha256",
    "toolFacts",
    "captureReceiptBindingEstablished",
    "priorRunBindingEstablished",
    "fixedRoleReadAuthorized",
    "priorQualificationEstablished",
    "snapshotSha256",
)

_CONTEXT = None


def _read_bounded(path, limit):
    if type(path) is not str or not path or type(limit) is not int or limit <= 0:
        raise RuntimeError("SNAPSHOT_INPUT_ARGUMENT")
    try:
        with open(path, "rb") as stream:
            raw = stream.read(limit + 1)
    except OSError:
        raise RuntimeError("SNAPSHOT_INPUT_READ") from None
    if len(raw) > limit:
        raise RuntimeError("SNAPSHOT_INPUT_LIMIT")
    return raw


def _read_pinned_metadata(path, declared):
    role, expected_bytes, expected_sha256, limit = declared
    raw = _read_bounded(path, limit)
    if (len(raw) != expected_bytes or
            hashlib.sha256(raw).hexdigest() != expected_sha256):
        raise RuntimeError("SNAPSHOT_" + role.upper().replace(" ", "_") + "_PIN")
    return raw


def _load_context(argv):
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--caller-source", required=True)
    parser.add_argument("--caller-sha", required=True)
    parser.add_argument("--snapshot-config", required=True)
    parser.add_argument("--snapshot-source-manifest", required=True)
    parser.add_argument("--snapshot-preflight", required=True)
    args, unittest_args = parser.parse_known_args(argv)
    if not re.fullmatch(r"[0-9a-f]{64}", args.caller_sha):
        raise RuntimeError("CALLER_SOURCE_SHA_ARGUMENT")

    caller_raw = _read_bounded(args.caller_source, MAX_CALLER_SOURCE_BYTES)
    if hashlib.sha256(caller_raw).hexdigest() != args.caller_sha:
        raise RuntimeError("CALLER_SOURCE_SHA256")
    module = types.ModuleType("_tsj_test_authenticated_historical_snapshot_caller")
    module.__file__ = args.caller_source
    sys.modules[module.__name__] = module
    try:
        code = compile(caller_raw, args.caller_source, "exec")
        exec(code, module.__dict__)
    except Exception:
        sys.modules.pop(module.__name__, None)
        raise RuntimeError("CALLER_SOURCE_LOAD") from None
    if (not isinstance(getattr(module, "CallerError", None), type) or
            not callable(getattr(module, "_historical_tool_snapshot_bytes", None)) or
            not callable(getattr(module, "_historical_tool_snapshot_values", None))):
        raise RuntimeError("CALLER_SNAPSHOT_API")

    raw_inputs = (
        _read_pinned_metadata(args.snapshot_config, SNAPSHOT_INPUTS[0]),
        _read_pinned_metadata(args.snapshot_source_manifest, SNAPSHOT_INPUTS[1]),
        _read_pinned_metadata(args.snapshot_preflight, SNAPSHOT_INPUTS[2]),
    )
    try:
        decoded_inputs = tuple(json.loads(raw.decode("utf-8")) for raw in raw_inputs)
    except (UnicodeError, json.JSONDecodeError):
        raise RuntimeError("SNAPSHOT_METADATA_JSON") from None
    return module, raw_inputs, decoded_inputs, unittest_args


def _canonical_snapshot_digest(snapshot):
    material = {key: value for key, value in snapshot.items()
                if key != "snapshotSha256"}
    canonical = json.dumps(
        material, ensure_ascii=True, sort_keys=False,
        separators=(",", ":"), allow_nan=False).encode("ascii")
    return hashlib.sha256(canonical).hexdigest()


class HistoricalToolSnapshotTests(unittest.TestCase):
    """Pure checks over pinned SOURCE metadata and synthetic semantic mutations."""

    @classmethod
    def setUpClass(cls):
        if _CONTEXT is None:
            raise RuntimeError("SNAPSHOT_CONTRACT_ARGUMENTS_REQUIRED")
        cls.caller, cls.raw_inputs, cls.decoded_inputs, _ = _CONTEXT

    def _assert_code(self, expected, callable_, *args):
        with self.assertRaises(self.caller.CallerError) as caught:
            callable_(*args)
        self.assertEqual(caught.exception.code, expected)

    def _values_snapshot(self, decoded_inputs):
        return self.caller._historical_tool_snapshot_values(*decoded_inputs)

    def test_source_snapshot_is_literal_metadata_and_matches_declared_tool_specs(self):
        original_raw = tuple(self.raw_inputs)
        snapshot = self.caller._historical_tool_snapshot_bytes(*self.raw_inputs)
        repeated = self.caller._historical_tool_snapshot_bytes(*self.raw_inputs)
        self.assertTrue(snapshot == repeated, "snapshot output is not stable")
        self.assertEqual(tuple(snapshot.keys()), DTO_KEYS)
        self.assertEqual(snapshot["schema"], "tsj-q100-source-tool-snapshot-metadata-v1")
        self.assertEqual(snapshot["origin"], "ACCEPTED_SOURCE_PREFLIGHT_ONLY")
        self.assertEqual(snapshot["acceptedSourceCommit"],
                         "5c745c3ff78cc16e19220b6c4a46ce541bdb58ad")
        self.assertEqual(snapshot["configSha256"],
                         "c8e937946f93f62801450eb989e73016d26736d53e1d2487cb8d8843f0add6eb")
        self.assertEqual(snapshot["sourceManifestSha256"],
                         "e5ea5ac6bd85ffdb0be078895e87b1da4d3d9488469029d8971751ec4846e80d")
        self.assertEqual(snapshot["preflightEvidenceSha256"],
                         "8244eb5c9e5e2ee6c78ed305262dad46c4a0c5857202d3e6c6d9fa6f99241837")
        self.assertEqual(tuple(snapshot["toolFacts"].keys()), TOOL_ROLES)
        self.assertTrue(all(snapshot[name] is False for name in (
            "captureReceiptBindingEstablished",
            "priorRunBindingEstablished",
            "fixedRoleReadAuthorized",
            "priorQualificationEstablished",
        )), "snapshot asserted authority beyond accepted source preflight")
        self.assertEqual(snapshot["snapshotSha256"],
                         "a6cdc79ba35bc4f65e462a81ebd35b7d97260580fed5d27b75b506a9a02cc9e3")
        self.assertEqual(snapshot["snapshotSha256"],
                         _canonical_snapshot_digest(snapshot))

        config = self.decoded_inputs[0]
        declared_tools = config["toolSpecs"]
        for role in TOOL_ROLES:
            fact = snapshot["toolFacts"][role]
            spec = declared_tools[role]
            self.assertEqual(tuple(fact.keys()), ("path", "bytes", "sha256"))
            expected = {
                "path": spec["path"],
                "bytes": spec["expectedBytes"],
                "sha256": spec["sha256"],
            }
            self.assertTrue(fact == expected, "tool fact mismatch for " + role)
        self.assertEqual(self.raw_inputs, original_raw)

        decoded_copies = copy.deepcopy(self.decoded_inputs)
        before = copy.deepcopy(decoded_copies)
        semantic_snapshot = self._values_snapshot(decoded_copies)
        self.assertTrue(decoded_copies == before, "semantic helper mutated its inputs")
        self.assertTrue(semantic_snapshot == snapshot,
                        "raw and semantic snapshot helpers disagree")

    def test_each_raw_metadata_sha_is_checked_before_json_decode(self):
        expected_codes = (
            "HISTORICAL_SNAPSHOT_CONFIG_SHA256",
            "HISTORICAL_SNAPSHOT_SOURCE_MANIFEST_SHA256",
            "HISTORICAL_SNAPSHOT_PREFLIGHT_SHA256",
        )
        for index, expected_code in enumerate(expected_codes):
            changed = list(self.raw_inputs)
            raw = changed[index]
            changed[index] = bytes((raw[0] ^ 1,)) + raw[1:]
            self._assert_code(expected_code,
                              self.caller._historical_tool_snapshot_bytes,
                              *changed)
        malformed = (b"{", self.raw_inputs[1], self.raw_inputs[2])
        self._assert_code("HISTORICAL_SNAPSHOT_CONFIG_SHA256",
                          self.caller._historical_tool_snapshot_bytes,
                          *malformed)

    def test_cross_document_tool_and_digest_bindings_reject_mutations(self):
        manifest = copy.deepcopy(self.decoded_inputs[1])
        manifest["toolFacts"][0]["bytes"] += 1
        self._assert_code("HISTORICAL_SNAPSHOT_BINDING", self._values_snapshot,
                          (self.decoded_inputs[0], manifest, self.decoded_inputs[2]))

        manifest = copy.deepcopy(self.decoded_inputs[1])
        manifest["configSha256"] = "f" * 64
        self._assert_code("HISTORICAL_SNAPSHOT_BINDING", self._values_snapshot,
                          (self.decoded_inputs[0], manifest, self.decoded_inputs[2]))

        preflight = copy.deepcopy(self.decoded_inputs[2])
        preflight["configSha256"] = "f" * 64
        self._assert_code("HISTORICAL_SNAPSHOT_BINDING", self._values_snapshot,
                          (self.decoded_inputs[0], self.decoded_inputs[1], preflight))

    def test_source_only_claims_reject_any_prior_body_resource_or_data_activity(self):
        mutations = (
            ("actualPriorBodiesRead", 1),
            ("actualPriorResourcesObserved", True),
            ("operationalJournalCreated", True),
            ("normalDataPhaseEnabled", True),
        )
        for field, value in mutations:
            preflight = copy.deepcopy(self.decoded_inputs[2])
            preflight[field] = value
            self._assert_code(
                "HISTORICAL_SNAPSHOT_NOT_SOURCE_ONLY", self._values_snapshot,
                (self.decoded_inputs[0], self.decoded_inputs[1], preflight))

    def test_semantic_helper_rejects_missing_and_extra_structure(self):
        config = copy.deepcopy(self.decoded_inputs[0])
        del config["toolSpecs"]
        with self.assertRaises(self.caller.CallerError):
            self._values_snapshot((config, self.decoded_inputs[1], self.decoded_inputs[2]))

        manifest = copy.deepcopy(self.decoded_inputs[1])
        manifest["syntheticUnexpectedField"] = True
        with self.assertRaises(self.caller.CallerError):
            self._values_snapshot((self.decoded_inputs[0], manifest, self.decoded_inputs[2]))


def main():
    global _CONTEXT
    try:
        _CONTEXT = _load_context(sys.argv[1:])
    except Exception as error:
        code = str(error) if str(error).startswith(("CALLER_", "SNAPSHOT_")) else "SNAPSHOT_SETUP"
        raise SystemExit(code) from None
    unittest.main(argv=[sys.argv[0], *_CONTEXT[3]])


if __name__ == "__main__":
    main()