"""Supplemental literal controls for canonical-prefix transport and bindings.

These fixtures are synthetic. They do not read captured runs or start native
processes.
"""
import copy
import json
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import read_capture as reader
from test_read_capture import line, make_transport


def rewrite_transport(raw, mutate):
    rows = [json.loads(row) for row in raw.splitlines()]
    mutate(rows)
    return b"\n".join(line(row) for row in rows) + b"\n"


def literal_graph_bytes():
    graph = {
        "protocol": "p07-fixed-prefix-graph-v1",
        "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
        "caseId": "LITERAL_ZERO_VIA_CASE",
        "width": 2,
        "height": 1,
        "primary": 0,
        "steps": [2, 0, 2, 0],
        "vias": [True, False],
        "goals": [False, True, False, False],
        "starts": [0],
    }
    return json.dumps(graph, sort_keys=True, separators=(",", ":")).encode("utf-8")


def literal_selector_operation():
    branches = []
    for pass_number, count in ((0, 114), (1, 117), (2, 77)):
        for _ in range(count):
            snapshot = {"pass": pass_number}
            if pass_number == 2 and len(branches) == 307:
                snapshot.update({
                    "netId": "A_COIL_LOW",
                    "startPadId": "QA.C",
                    "rootPadId": "DA.A",
                    "branchExpanded": 25,
                    "totalExpanded": 918222,
                    "terminalReason": "NO_PATH",
                    "frontierCount": 0,
                })
            branches.append({"ordinal": len(branches), "snapshot": snapshot})
    return {"branches": branches}


def literal_operation_entry():
    return {
        "operation": 1,
        "armId": "OBSERVED_DEFAULT_NEAREST",
        "receipt": {"path": "op-1.jsonl", "bytes": 1, "sha256": "a" * 64},
        "elapsedToCloseNanos": 10,
        "maxFullGroupNanos": 5,
        "cleanup": {
            "status": "PASS",
            "elapsedNanos": 0,
            "ownersReleased": True,
            "contextRestored": True,
        },
    }


class TransportControlTests(unittest.TestCase):
    def test_rejects_chunk_index_offset_length_and_order_mutations(self):
        raw = make_transport(b"G", b"F")
        mutations = (
            ("TRANSPORT_CHUNK_INDEX", lambda rows: rows[1].update(index=1)),
            ("TRANSPORT_CHUNK_OFFSET", lambda rows: rows[1].update(offset=1)),
            ("TRANSPORT_CHUNK_BYTES", lambda rows: rows[1].update(bytes=2)),
            ("TRANSPORT_CHUNK_ORDER", lambda rows: rows.__setitem__(slice(1, 3), rows[2:0:-1])),
        )
        for code, mutate in mutations:
            with self.subTest(code=code):
                malformed = rewrite_transport(raw, mutate)
                with self.assertRaisesRegex(reader.Rejected, code):
                    reader.decode_transport(malformed, False)

    def test_rejects_missing_or_trailing_footer_and_payload_hash_mismatch(self):
        raw = make_transport(b"G", b"F")
        missing_footer = rewrite_transport(raw, lambda rows: rows.pop())
        trailing_record = rewrite_transport(
            raw, lambda rows: rows.append({"record": "TRAILER"}))
        bad_payload_hash = rewrite_transport(
            raw, lambda rows: rows[0]["payloads"][0].update(sha256="0" * 64))

        for malformed in (missing_footer, trailing_record):
            with self.subTest(shape="footer closure"):
                with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_FOOTER_FIELDS"):
                    reader.decode_transport(malformed, False)
        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_PAYLOAD_HASH"):
            reader.decode_transport(bad_payload_hash, False)

    def test_rejects_boolean_values_in_integer_and_boolean_fields(self):
        raw = make_transport(b"G", b"F")
        bool_payload_size = rewrite_transport(
            raw, lambda rows: rows[0]["payloads"][0].update(bytes=True))
        integer_footer_boolean = rewrite_transport(
            raw, lambda rows: rows[-1].update(complete=1))

        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_PAYLOAD_BYTES"):
            reader.decode_transport(bool_payload_size, False)
        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_FOOTER"):
            reader.decode_transport(integer_footer_boolean, False)

    def test_parsed_graph_and_facts_payloads_and_rejects_malformed_payloads(self):
        graph = literal_graph_bytes()
        facts = json.dumps({
            "schema": reader.FACTS_SCHEMA,
            "caseId": "LITERAL_ZERO_VIA_CASE",
        }, sort_keys=True, separators=(",", ":")).encode("utf-8")
        decoded = reader.decode_transport(make_transport(graph, facts), True)
        self.assertEqual(decoded["graph"].case_id, "LITERAL_ZERO_VIA_CASE")
        self.assertEqual(decoded["facts"]["schema"], reader.FACTS_SCHEMA)

        with self.assertRaisesRegex(reader.Rejected, "GRAPH_BOM"):
            reader.decode_transport(make_transport(b"\xef\xbb\xbf" + graph, facts), True)
        with self.assertRaisesRegex(reader.Rejected, "GRAPH_PAYLOAD_INVALID"):
            reader.decode_transport(make_transport(b"{", facts), True)
        with self.assertRaisesRegex(reader.Rejected, "JSON_INVALID"):
            reader.decode_transport(make_transport(graph, b"{"), True)


class IdentityAndCleanupControlTests(unittest.TestCase):
    def test_literal_selector_binds_global_307_to_pass_local_77(self):
        operation = literal_selector_operation()
        selected = reader.validate_target_selector(operation)
        self.assertEqual(selected["branch"]["ordinal"], 307)
        self.assertEqual(selected["lineageBranchOrdinal"], 77)
        self.assertEqual(selected["snapshot"]["netId"], "A_COIL_LOW")

        duplicate_global_ordinal = copy.deepcopy(operation)
        duplicate_global_ordinal["branches"][0]["ordinal"] = 307
        with self.assertRaisesRegex(reader.Rejected, "SELECTOR_ORDINAL_BINDING"):
            reader.validate_target_selector(duplicate_global_ordinal)

        shifted_global_ordinal = copy.deepcopy(operation)
        shifted_global_ordinal["branches"][306]["ordinal"] = 307
        shifted_global_ordinal["branches"][307]["ordinal"] = 306
        with self.assertRaisesRegex(reader.Rejected, "SELECTOR_ORDINAL_BINDING"):
            reader.validate_target_selector(shifted_global_ordinal)

        wrong_target = copy.deepcopy(operation)
        wrong_target["branches"][307]["snapshot"]["rootPadId"] = "DB.A"
        with self.assertRaisesRegex(reader.Rejected, "SELECTOR_TARGET_SNAPSHOT"):
            reader.validate_target_selector(wrong_target)

    def test_cleanup_entry_and_missing_target_marker_require_clean_release(self):
        entry = literal_operation_entry()
        self.assertIs(reader._entry(entry), entry)

        for field in ("status", "ownersReleased", "contextRestored"):
            broken = copy.deepcopy(entry)
            broken["cleanup"][field] = "FAIL" if field == "status" else False
            with self.subTest(field=field):
                with self.assertRaisesRegex(reader.Rejected, "ENTRY_CLEANUP"):
                    reader._entry(broken)

        late_cleanup = copy.deepcopy(entry)
        late_cleanup["cleanup"]["elapsedNanos"] = 120_000_000_001
        with self.assertRaisesRegex(reader.Rejected, "ENTRY_CLEANUP_NANOS"):
            reader._entry(late_cleanup)

        run_id = "00000000-0000-4000-8000-000000000001"
        input_sha = "1" * 64
        marker = {
            "schema": "tsj-canonical-prefix-missing-target-v1",
            "mode": "MISSING_TARGET",
            "runId": run_id,
            "sourceManifestSha256": input_sha,
            "wireSha256": reader.WIRE,
            "operation": entry,
            "selection": {
                "calls": 2, "selected": 0, "callbacks": 0,
                "snapshotDropped": True,
            },
            "workNanos": 10,
            "scope": reader.SCOPE,
            "layoutQualification": False,
        }
        native = {
            "ExitCode": 2,
            "Stdout": "",
            "Stderr": "CANONICAL_PREFIX_MISSING_TARGET " +
                json.dumps(marker, sort_keys=True, separators=(",", ":")) + "\n",
        }
        self.assertEqual(reader._missing(native, run_id, input_sha)["selection"]["selected"], 0)

        marker["operation"] = late_cleanup
        native["Stderr"] = "CANONICAL_PREFIX_MISSING_TARGET " + json.dumps(
            marker, sort_keys=True, separators=(",", ":")) + "\n"
        with self.assertRaisesRegex(reader.Rejected, "ENTRY_CLEANUP_NANOS"):
            reader._missing(native, run_id, input_sha)


class CaptureComparisonControlTests(unittest.TestCase):
    def test_route_comparison_keeps_semantics_and_retains_capture_work(self):
        rows = [
            {"record": "HEADER", "group": 0, "runId": "CONTROL",
             "sourceManifestSha256": "a" * 64, "arm": {"graphCapture": False}},
            {"record": "PAD", "group": 0, "padId": "QA.C", "netId": "A_COIL_LOW"},
            {"record": "NET_ORDER", "group": 1, "netIds": ["A", "B"]},
            {"record": "BRANCH", "ordinal": 307, "group": 1,
             "prefixSha256": "b" * 64, "snapshot": {"branchExpanded": 25}},
            {"record": "GROUP", "ordinal": 1, "elapsedBeforeOwnLineNanos": 8},
            {"record": "RESULT", "group": 2, "outcome": "NO_PATH",
             "logicalWork": {"generatorAdvances": 3, "p07Slices": 2,
                             "p07OwnerSteps": 100, "routingOwnerSteps": 92,
                             "diagnosticCloneSteps": 8, "expansions": 25}},
            {"record": "FOOTER", "group": 2, "complete": True,
             "padCount": 1, "branchCount": 1, "groupCount": 3},
        ]
        actual = copy.deepcopy(rows)
        actual[0]["runId"] = "CAPTURE"
        actual[0]["sourceManifestSha256"] = "c" * 64
        for row in actual:
            if "group" in row:
                row["group"] += 1
        actual[4]["elapsedBeforeOwnLineNanos"] = 9
        actual[-2]["logicalWork"].update(generatorAdvances=5, p07Slices=4,
                                        p07OwnerSteps=107, routingOwnerSteps=99)
        actual[-1]["groupCount"] = 5
        encode = lambda value: b"\n".join(line(row) for row in value) + b"\n"
        ledger = reader._compare_capture(encode(actual), encode(rows), reader)
        self.assertEqual(ledger["p07OwnerStepsDelta"], 7)
        self.assertEqual(ledger["control"]["groupCount"], 3)
        self.assertEqual(ledger["capture"]["logicalWork"]["p07OwnerSteps"], 107)
        for index, field, value in ((0, "arm", {"graphCapture": True}),
                                     (1, "netId", "WRONG"),
                                     (2, "netIds", ["B", "A"]),
                                     (3, "snapshot", {"branchExpanded": 24})):
            broken = copy.deepcopy(actual)
            broken[index][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(
                    reader.Rejected, "CAPTURE_CONTROL_ROUTE_PARITY"):
                reader._compare_capture(encode(broken), encode(rows), reader)

    def test_index_selection_counts_reject_boolean_integer_substitution(self):
        run_id = "00000000-0000-4000-8000-000000000001"
        input_sha = "1" * 64
        value = {"schema": reader.INDEX_SCHEMA, "mode": "CONTROL", "profile": "BASE",
                 "kind": "AUTHORED", "runId": run_id,
                 "sourceManifestSha256": input_sha, "wireSha256": reader.WIRE,
                 "scope": reader.SCOPE, "layoutQualification": False,
                 "target": {"receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77},
                 "operation": literal_operation_entry(), "transport": None,
                 "selection": {"calls": 0, "selected": 0, "callbacks": 0,
                               "snapshotDropped": True}}
        reader._index(b"{}", value, "CONTROL", run_id, input_sha)
        for key, replacement in (("calls", False), ("selected", False),
                                 ("callbacks", False), ("snapshotDropped", 1)):
            broken = copy.deepcopy(value)
            broken["selection"][key] = replacement
            with self.subTest(key=key), self.assertRaisesRegex(reader.Rejected, "INDEX_SELECTION"):
                reader._index(b"{}", broken, "CONTROL", run_id, input_sha)


if __name__ == "__main__":
    unittest.main()
