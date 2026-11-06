"""Literal independent tests for the canonical-prefix reader.

Fixtures are synthetic in-memory values. These tests never read run output,
source roots, protected data, or start a native process.
"""
import base64
import hashlib
import json
from pathlib import Path
import sys
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import read_capture as reader


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def line(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("ascii")


def make_transport(graph, facts):
    payloads = (("GRAPH", graph), ("FACTS", facts))
    desc = [
        {"kind": kind, "bytes": len(raw), "sha256": sha(raw),
         "chunks": (len(raw) + reader.LIMITS["chunkBytes"] - 1) //
                   reader.LIMITS["chunkBytes"]}
        for kind, raw in payloads
    ]
    header = {
        "record": "HEADER", "schema": reader.TRANSPORT_SCHEMA,
        "runId": "00000000-0000-4000-8000-000000000001",
        "sourceManifestSha256": "1" * 64, "wireSha256": reader.WIRE,
        "scope": reader.SCOPE, "payloads": desc,
        "aggregateCapBytes": reader.LIMITS["aggregateBytes"],
        "chunkBytes": reader.LIMITS["chunkBytes"],
    }
    rows = [line(header)]
    for kind, raw in payloads:
        offset = 0
        index = 0
        while offset < len(raw):
            chunk = raw[offset:offset + reader.LIMITS["chunkBytes"]]
            rows.append(line({
                "record": "CHUNK", "kind": kind, "index": index,
                "offset": offset, "bytes": len(chunk),
                "data": base64.b64encode(chunk).decode("ascii"),
            }))
            offset += len(chunk)
            index += 1
    rows.append(line({
        "record": "FOOTER", "complete": True,
        "graphChunks": desc[0]["chunks"], "factChunks": desc[1]["chunks"],
        "payloadBytes": len(graph) + len(facts),
    }))
    return b"\n".join(rows) + b"\n"


def synthetic_graph(*, planar=True, via_needed=False):
    # Two cells on each face. The direct top path costs zero VIA; the alternate
    # fixture requires one allowed face toggle before reaching its bottom goal.
    if via_needed:
        steps = [0, 0, 2, 0]
        goals = [False, False, False, True]
    else:
        steps = [2, 0, 2, 0]
        goals = [False, True, False, False]
    return reader.graph_oracle.validate_payload({
        "protocol": "p07-fixed-prefix-graph-v1",
        "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
        "caseId": "LITERAL_ZERO_VIA_CASE",
        "width": 2, "height": 1, "primary": 0,
        "steps": steps, "vias": [True, False], "goals": goals, "starts": [0],
    })


class TransportTests(unittest.TestCase):
    def test_multichunk_payload_sequence_and_hashes(self):
        graph = b"G" * (reader.LIMITS["chunkBytes"] + 1)
        facts = b"F"
        decoded = reader.decode_transport(make_transport(graph, facts), False)
        self.assertEqual(decoded["graphBytes"], graph)
        self.assertEqual(decoded["factsBytes"], facts)
        self.assertEqual(decoded["header"]["payloads"][0]["chunks"], 2)

    def test_rejects_duplicate_json_fields(self):
        raw = make_transport(b"G", b"F")
        raw = raw.replace(b'"record":"HEADER"', b'"record":"HEADER","record":"HEADER"', 1)
        with self.assertRaisesRegex(reader.Rejected, "JSON_DUPLICATE_KEY"):
            reader.decode_transport(raw, False)

    def test_rejects_noncanonical_base64(self):
        raw = make_transport(b"G", b"x")
        raw = raw.replace(b'"data":"eA=="', b'"data":"eB=="')
        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_BASE64_CANONICAL"):
            reader.decode_transport(raw, False)

    def test_rejects_declared_aggregate_over_cap_before_chunks(self):
        maximum = reader.LIMITS["aggregateBytes"]
        header = {
            "record": "HEADER", "schema": reader.TRANSPORT_SCHEMA,
            "runId": "00000000-0000-4000-8000-000000000001",
            "sourceManifestSha256": "1" * 64, "wireSha256": reader.WIRE,
            "scope": reader.SCOPE,
            "payloads": [
                {"kind": "GRAPH", "bytes": maximum, "sha256": "2" * 64, "chunks": 256},
                {"kind": "FACTS", "bytes": 1, "sha256": "3" * 64, "chunks": 1},
            ],
            "aggregateCapBytes": maximum, "chunkBytes": reader.LIMITS["chunkBytes"],
        }
        raw = line(header) + b"\n" + line({
            "record": "FOOTER", "complete": True,
            "graphChunks": 256, "factChunks": 1, "payloadBytes": maximum + 1,
        }) + b"\n"
        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_AGGREGATE_CAP"):
            reader.decode_transport(raw, False)

    def test_rejects_oversized_line(self):
        with self.assertRaisesRegex(reader.Rejected, "TRANSPORT_LINE_SIZE"):
            reader.decode_transport(b" " * (reader.LIMITS["lineBytes"] + 1) + b"\n", False)


class IdentityAndGraphTests(unittest.TestCase):
    def test_global_receipt_ordinal_is_not_pass_local_lineage_ordinal(self):
        branches = []
        for pass_number, count in ((0, 114), (1, 117), (2, 77)):
            for _ in range(count):
                branches.append({"ordinal": len(branches),
                                 "snapshot": {"pass": pass_number}})
        self.assertEqual(len(branches), 308)
        self.assertEqual(reader.select_lineage(branches, 77)["ordinal"], 307)
        self.assertIsNone(reader.select_lineage(branches, 307))
        with self.assertRaisesRegex(reader.Rejected, "SELECTOR_LINEAGE_ORDINAL"):
            reader.select_lineage(branches, True)

    def test_zero_capacity_scope_distinguishes_zero_and_positive_via_minima(self):
        zero = reader.zero_via_result(synthetic_graph())
        self.assertEqual(zero["minimumViaCount"], 0)
        self.assertEqual(zero["status"], "ZERO_VIA_CONNECTED")
        self.assertEqual(zero["claim"], "ZERO_VIA_CONNECTIVITY_ONLY")
        self.assertEqual(zero["productionFeasibility"], "UNKNOWN")
        self.assertFalse(zero["acceptance"]["route"])

        one = reader.zero_via_result(synthetic_graph(via_needed=True))
        self.assertEqual(one["minimumViaCount"], 1)
        self.assertEqual(one["status"], "ZERO_VIA_ROUTE_EXCLUDED")
        self.assertEqual(one["claim"], "ZERO_VIA_CONNECTIVITY_ONLY")
        self.assertEqual(one["productionFeasibility"], "UNKNOWN")
        self.assertFalse(one["acceptance"]["route"])

    def test_rejects_boolean_where_integer_required(self):
        with self.assertRaisesRegex(reader.Rejected, "INTEGER_INVALID"):
            reader._int(True, 0, 10)


if __name__ == "__main__":
    unittest.main()
