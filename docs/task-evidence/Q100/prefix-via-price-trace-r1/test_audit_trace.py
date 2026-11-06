import unittest

from audit_trace import Rejected, cap_facts, compare_traces, parse_records, summarize


def branch(ordinal, *, pass_no=0, net="N0", start="P0", root="P1",
           via=0, expanded=0, total=0, frontier=0, terminal="CAP",
           digest=None, byte_count=None, **metrics):
    snapshot = {
        "pass": pass_no, "netId": net, "startPadId": start, "rootPadId": root,
        "prefixViaCount": via, "branchExpanded": expanded,
        "totalExpanded": total, "frontierCount": frontier,
        "terminalReason": terminal,
    }
    snapshot.update(metrics)
    return {
        "record": "BRANCH", "ordinal": ordinal,
        "prefixSha256": digest or ("%064x" % (ordinal + 1)),
        "prefixBytes": byte_count if byte_count is not None else ordinal + 1,
        "snapshot": snapshot,
    }


class AuditTraceTests(unittest.TestCase):
    def test_rejected_is_value_error_and_jsonl_is_strict(self):
        self.assertTrue(issubclass(Rejected, ValueError))
        valid = b'{"record":"BRANCH"}\n'
        self.assertEqual(parse_records(valid), [{"record": "BRANCH"}])
        invalid = (b'{"record":"BRANCH"}', b'{"record":"BRANCH"}\r\n',
                   b'{"record":"BRANCH","record":"RESULT"}\n')
        for raw in invalid:
            with self.subTest(raw=raw), self.assertRaises(Rejected):
                parse_records(raw)

    def test_jsonl_rejects_oversized_line_and_document(self):
        oversized = b"{}" + b" " * (64 * 1024 - 1) + b"\n"
        with self.assertRaises(Rejected):
            parse_records(oversized)
        # Each line is below 64 KiB; the complete input exceeds 8 MiB.
        short = b"{}" + b" " * (64 * 1024 - 4) + b"\n"
        self.assertLess(len(short), 64 * 1024)
        with self.assertRaises(Rejected):
            parse_records(short * 129)

    def test_summary_counts_literal_rows_without_deduplicating(self):
        rows = [branch(0, via=2, expanded=2, total=2, frontier=9),
                branch(1, via=5, expanded=5, total=7, frontier=4),
                branch(2, via=1, expanded=1, total=8, terminal="EXHAUSTED",
                       note="last")]
        result = summarize(rows, 8)
        self.assertEqual(result["branchCount"], 3)
        self.assertEqual(result["prefixViaCountHistogram"], {"2": 1, "5": 1, "1": 1})
        self.assertEqual(result["sumBranchExpanded"], 8)
        self.assertEqual(result["totalExpanded"], 8)
        self.assertEqual(result["lastBranch"], rows[-1]["snapshot"])

    def test_summary_rejects_boolean_counts_and_bad_cumulative_totals(self):
        cases = (
            ([branch(0, via=True, expanded=1, total=1)], 1),
            ([branch(0, expanded=True, total=1)], 1),
            ([branch(0, expanded=1, total=1)], True),
            ([branch(0, expanded=2, total=3), branch(1, expanded=5, total=7)], 7),
            ([branch(0, expanded=2, total=2), branch(1, expanded=5, total=8)], 8),
        )
        for rows, expected in cases:
            with self.subTest(expected=expected), self.assertRaises(Rejected):
                summarize(rows, expected)

    def test_ordered_alignment_preserves_repeated_identities_and_types(self):
        base = [branch(0, net="A"), branch(1, net="A", metric=1),
                branch(2, net="B")]
        ramp = [branch(0, net="A"), branch(1, net="A", metric=True),
                branch(2, net="B")]
        result = compare_traces(base, ramp)
        self.assertEqual(result["equalIdentityPrefix"], 3)
        self.assertIsNone(result["firstIdentityDifference"])
        difference = result["firstAlignedSnapshotDifference"]
        self.assertEqual(difference["index"], 1)
        self.assertEqual(difference["identity"], [0, "A", "P0", "P1"])
        self.assertEqual(difference["fields"], [{"field": "metric", "base": 1,
                                                 "ramp": True}])

    def test_identity_mismatch_stops_metric_and_digest_alignment(self):
        base = [branch(0, net="A"),
                branch(1, net="B", metric="base", digest="b" * 64),
                branch(2, net="C", metric="tail-base", digest="c" * 64)]
        ramp = [branch(0, net="A"),
                branch(1, net="X", metric="ramp", digest="x" * 64),
                branch(2, net="B", metric="tail-ramp", digest="y" * 64),
                branch(3, net="C", digest="z" * 64)]
        result = compare_traces(base, ramp)
        self.assertEqual(result["equalIdentityPrefix"], 1)
        self.assertEqual(result["firstIdentityDifference"], {
            "index": 1, "baseIdentity": [0, "B", "P0", "P1"],
            "rampIdentity": [0, "X", "P0", "P1"],
        })
        self.assertIsNone(result["firstAlignedSnapshotDifference"])
        self.assertIsNone(result["firstGeometryDigestDifference"])

    def test_one_sided_identity_tails_are_reported(self):
        base, ramp = [branch(0, net="A"), branch(1, net="B")], [branch(0, net="A")]
        self.assertEqual(compare_traces(base, ramp)["firstIdentityDifference"], {
            "index": 1, "baseIdentity": [0, "B", "P0", "P1"], "rampIdentity": None,
        })
        self.assertEqual(compare_traces(ramp, base)["firstIdentityDifference"], {
            "index": 1, "baseIdentity": None, "rampIdentity": [0, "B", "P0", "P1"],
        })

    def test_geometry_digest_difference_at_matched_identity(self):
        base = [branch(0, net="A", digest="a" * 64, byte_count=10)]
        ramp = [branch(0, net="A", digest="b" * 64, byte_count=11)]
        result = compare_traces(base, ramp)
        self.assertEqual(result["equalIdentityPrefix"], 1)
        self.assertIsNone(result["firstAlignedSnapshotDifference"])
        self.assertEqual(result["firstGeometryDigestDifference"], {
            "index": 0, "base": {"sha256": "a" * 64, "bytes": 10},
            "ramp": {"sha256": "b" * 64, "bytes": 11},
        })

    def test_cap_facts_for_zero_partial_and_full_via_capacity(self):
        cases = ((0, "BASE", 60, 48, 4), (38, "RAMP", 107, 10, 4),
                 (48, "RAMP", 120, 0, 0))
        for holes, profile, price, remaining, transitions in cases:
            snapshot = {"pass": 0, "netId": "N0", "startPadId": "P0", "rootPadId": "P1",
                        "prefixViaCount": holes, "branchExpanded": 12,
                        "totalExpanded": 31, "frontierCount": 7,
                        "terminalReason": "CAP"}
            expected = {
                "prefixViaCount": holes, "remainingHoleSlots": remaining,
                "maximumBranchTransitions": 4, "remainingBranchTransitions": transitions,
                "expectedBranchPrice": price, "priceObservation": "DERIVED_NOT_EXPORTED",
                "geometricFeasibility": "NOT_PROVEN", "terminalReason": "CAP",
                "branchExpanded": 12, "totalExpanded": 31, "frontierCount": 7,
            }
            with self.subTest(holes=holes, profile=profile):
                self.assertEqual(cap_facts(snapshot, profile), expected)


if __name__ == "__main__":
    unittest.main()
