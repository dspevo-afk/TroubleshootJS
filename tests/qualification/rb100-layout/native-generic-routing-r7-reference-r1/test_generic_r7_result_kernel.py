"""Pure semantic tests for the current generic R7 result/marker reader.

The optional reference packet is read only when the root runs this suite. Its
preserved T references are projected into explicitly synthetic setup/metadata
unit phases for semantic coverage; those values are never native counter proof.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import types
import unittest

RUN_ID = "12345678-1234-4234-8234-123456789abc"
SOURCE_SHA = "a" * 64
WIRE_SHA = "b" * 64


def load_source_namespace(t_reader_path, kernel_path):
    """Load the pinned T source and kernel into one isolated generated namespace."""
    namespace = {"__name__": "_generic_r7_reader_test_namespace",
                 "__file__": "<source-only-r7-test>"}
    t_raw = Path(t_reader_path).read_bytes()
    exec(compile(t_raw, str(t_reader_path), "exec"), namespace, namespace)
    kernel_raw = Path(kernel_path).read_bytes()
    exec(compile(kernel_raw, str(kernel_path), "exec"), namespace, namespace)
    return namespace


def canonical_raw(value):
    return (json.dumps(value, ensure_ascii=True, allow_nan=False,
                       separators=(",", ":")) + "\n").encode("ascii")


def make_projected_result(namespace, reference_packet):
    """Preserve T references while labeling added phase units as synthetic."""
    spec = namespace["SOURCE_FIXTURE_SPEC"]
    references = copy.deepcopy(reference_packet["references"])
    open_run_work = references["OPEN"]["run"]["work"]
    blocked_run_work = references["BLOCKED"]["run"]["work"]

    def run_phase(run_work):
        return {"units": run_work["ownerUnits"] + run_work["wrapperUnits"],
                "groups": run_work["groups"],
                "copiedEntries": run_work["copyEntries"],
                "copyChunks": run_work["copyChunks"]}

    setup = {"units": 3, "groups": 1, "copiedEntries": 0, "copyChunks": 0}
    open_phase = run_phase(open_run_work)
    blocked_phase = run_phase(blocked_run_work)
    metadata = {"units": 5, "groups": 1, "copiedEntries": 0, "copyChunks": 0}
    phases = (setup, open_phase, blocked_phase, metadata)
    pre_export = {
        "units": sum(phase["units"] for phase in phases),
        "groups": sum(phase["groups"] for phase in phases),
        "copiedEntries": sum(phase["copiedEntries"] for phase in phases),
        "copyChunks": sum(phase["copyChunks"] for phase in phases),
        "maxGroupNanos": max(open_run_work["maxGroupNanos"],
                              blocked_run_work["maxGroupNanos"]),
        "maximumUnitsPerGroup": max(5, open_run_work["maximumUnitsPerGroup"],
                                     blocked_run_work["maximumUnitsPerGroup"]),
        "sessionConstructorEntries": 2,
        "elapsedNanos": 1_000_000,
    }
    limits = {name: value for name, value in namespace["GR7_LIMIT_VALUES"]}
    result = {
        "schema": namespace["GR7_RESULT_SCHEMA"],
        "status": namespace["GR7_RESULT_STATUS"],
        "runId": RUN_ID,
        "sourceManifestSha256": SOURCE_SHA,
        "wireContractSha256": WIRE_SHA,
        "references": references,
        "sourceReview": {
            "scope": namespace["GR7_SCOPE"],
            "physicalOwnerSha256": spec["physicalOwnerSha256"],
            "studyParentSha256": namespace["GR7_STUDY_PARENT_SHA256"],
            "runOwnerSha256": spec["runOwnerSha256"],
            "fixtureSourceSha256": spec["fixtureSourceSha256"],
            "fixtureOracleSha256": namespace["FIXTURE_SPEC_SHA256"],
            "sessionConstructorEntries": 2,
        },
        "work": {
            "setup": setup,
            "open": open_phase,
            "blocked": blocked_phase,
            "metadata": metadata,
            "preExport": pre_export,
            "limits": limits,
        },
    }
    return result


def make_marker(namespace, result, result_raw, overrides=None):
    pre = result["work"]["preExport"]
    demand = namespace["gr7_export_demand"](len(result_raw), 1)
    final_export_nanos = 1_000
    marker = {
        "schema": namespace["GR7_MARKER_SCHEMA"],
        "status": namespace["GR7_MARKER_STATUS"],
        "runId": result["runId"],
        "sourceManifestSha256": result["sourceManifestSha256"],
        "wireContractSha256": result["wireContractSha256"],
        "fixtureOracleSha256": namespace["FIXTURE_SPEC_SHA256"],
        "resultSha256": hashlib.sha256(result_raw).hexdigest(),
        "resultBytes": len(result_raw),
        "elapsedNanos": pre["elapsedNanos"] + final_export_nanos,
        "finalExportNanos": final_export_nanos,
        "totalGroups": pre["groups"] + demand["groups"],
        "totalUnits": pre["units"] + demand["units"],
        "maxGroupNanos": max(pre["maxGroupNanos"], final_export_nanos),
        "maximumUnitsPerGroup": max(pre["maximumUnitsPerGroup"], demand["units"]),
    }
    if overrides:
        marker.update(overrides)
    return marker


def build_projected_case(namespace, reference_packet, fixture_raw):
    """Create a clearly synthetic unit ledger around preserved T reference rows."""
    result = make_projected_result(namespace, reference_packet)
    result_raw = namespace["canonical_line"](result)
    marker = make_marker(namespace, result, result_raw)
    marker_raw = namespace["canonical_line"](marker)
    return result, result_raw, marker, marker_raw


class KernelUnitTests(unittest.TestCase):
    def test_export_fee_and_chunk_boundaries_use_literal_expectations(self):
        demand = NS["gr7_export_demand"]
        self.assertEqual(demand(1, 666),
                         {"units": 8, "copiedEntries": 700,
                          "copyChunks": 4, "groups": 1})
        self.assertEqual(demand(4096, 666),
                         {"units": 8, "copiedEntries": 8890,
                          "copyChunks": 4, "groups": 1})
        self.assertEqual(demand(4097, 666),
                         {"units": 13, "copiedEntries": 8892,
                          "copyChunks": 6, "groups": 1})

    def test_export_fee_rejects_bool_float_and_out_of_bound_lengths(self):
        for result_bytes, marker_bytes in ((True, 1), (2.0, 1), (2, False),
                                           (NS["GR7_MAX_BYTES"] + 1, 1),
                                           (2, NS["GR7_MAX_MARKER_BYTES"] + 1)):
            with self.subTest(result_bytes=result_bytes, marker_bytes=marker_bytes):
                with self.assertRaises(NS["GR7_ReaderError"]) as caught:
                    NS["gr7_export_demand"](result_bytes, marker_bytes)
                self.assertEqual(caught.exception.code, "GR7_EXPORT_LENGTH")

    def test_synthetic_phase_ledger_equations_and_scalar_guards(self):
        work = {
            "setup": {"units": 7, "groups": 1, "copiedEntries": 2, "copyChunks": 1},
            "open": {"units": 14, "groups": 1, "copiedEntries": 0, "copyChunks": 0},
            "blocked": {"units": 17, "groups": 2, "copiedEntries": 4097, "copyChunks": 2},
            "metadata": {"units": 3, "groups": 1, "copiedEntries": 0, "copyChunks": 0},
            "preExport": {"units": 41, "groups": 5, "copiedEntries": 4099,
                          "copyChunks": 3, "maxGroupNanos": 30,
                          "maximumUnitsPerGroup": 19,
                          "sessionConstructorEntries": 2, "elapsedNanos": 1000},
            "limits": {name: value for name, value in NS["GR7_LIMIT_VALUES"]},
        }
        open_run = {"ownerUnits": 10, "wrapperUnits": 4, "groups": 1,
                    "copyEntries": 0, "copyChunks": 0,
                    "maxGroupNanos": 20, "maximumUnitsPerGroup": 14}
        blocked_run = {"ownerUnits": 8, "wrapperUnits": 9, "groups": 2,
                       "copyEntries": 4097, "copyChunks": 2,
                       "maxGroupNanos": 30, "maximumUnitsPerGroup": 17}
        scalar_copy = NS["gr7_validate_work_ledger"](
            work, open_run, blocked_run, 2)
        self.assertEqual(scalar_copy["units"], 41)
        self.assertEqual(scalar_copy["copiedEntries"], 4099)
        self.assertEqual(tuple(scalar_copy), NS["GR7_PRE_EXPORT_KEYS"])

        bad_bool = copy.deepcopy(work)
        bad_bool["setup"]["units"] = True
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_work_ledger"](bad_bool, open_run, blocked_run, 2)
        self.assertEqual(caught.exception.code, "GR7_WORK_SETUP_INTEGER")

        bad_sum = copy.deepcopy(work)
        bad_sum["metadata"]["units"] -= 1
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_work_ledger"](bad_sum, open_run, blocked_run, 2)
        self.assertEqual(caught.exception.code, "GR7_WORK_PHASE_SUM")

        impossible_maximum = copy.deepcopy(work)
        impossible_maximum["metadata"]["units"] = 100
        impossible_maximum["preExport"]["units"] = 138
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_work_ledger"](
                impossible_maximum, open_run, blocked_run, 2)
        self.assertEqual(caught.exception.code,
                         "GR7_WORK_PRE_EXPORT_MAXIMUM_CAPACITY")

        bad_limits = copy.deepcopy(work)
        bad_limits["limits"]["maximumGroups"] = True
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_work_ledger"](bad_limits, open_run, blocked_run, 2)
        self.assertEqual(caught.exception.code, "GR7_WORK_LIMIT_VALUE")

    def test_marker_copy_capacity_canary_uses_scalar_helper(self):
        result_raw = b"{}\n"
        marker_raw = b"m" * 100
        pre = {"units": 100, "groups": 2,
               "copiedEntries": NS["GR7_MAX_COPY_ENTRIES"] - 135,
               "copyChunks": 1, "maxGroupNanos": 100,
               "maximumUnitsPerGroup": 50, "elapsedNanos": 1000}
        marker = {
            "schema": NS["GR7_MARKER_SCHEMA"],
            "status": NS["GR7_MARKER_STATUS"],
            "runId": RUN_ID,
            "sourceManifestSha256": SOURCE_SHA,
            "wireContractSha256": WIRE_SHA,
            "fixtureOracleSha256": NS["FIXTURE_SPEC_SHA256"],
            "resultSha256": hashlib.sha256(result_raw).hexdigest(),
            "resultBytes": len(result_raw),
            "elapsedNanos": 1200,
            "finalExportNanos": 200,
            "totalGroups": 3,
            "totalUnits": 108,
            "maxGroupNanos": 200,
            "maximumUnitsPerGroup": 50,
        }
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_marker_scalars"](
                marker, marker_raw, result_raw, RUN_ID, SOURCE_SHA, WIRE_SHA, pre)
        self.assertEqual(caught.exception.code, "GR7_MARKER_COPY_CAPACITY")


    def test_marker_total_cannot_exceed_reported_group_maximum(self):
        result_raw = b"{}\n"
        marker_raw = b"m" * 100
        pre = {"units": 100, "groups": 2, "copiedEntries": 0,
               "copyChunks": 0, "maxGroupNanos": 100,
               "maximumUnitsPerGroup": 10, "elapsedNanos": 1000}
        marker = {
            "schema": NS["GR7_MARKER_SCHEMA"],
            "status": NS["GR7_MARKER_STATUS"],
            "runId": RUN_ID,
            "sourceManifestSha256": SOURCE_SHA,
            "wireContractSha256": WIRE_SHA,
            "fixtureOracleSha256": NS["FIXTURE_SPEC_SHA256"],
            "resultSha256": hashlib.sha256(result_raw).hexdigest(),
            "resultBytes": len(result_raw),
            "elapsedNanos": 1200,
            "finalExportNanos": 200,
            "totalGroups": 3,
            "totalUnits": 108,
            "maxGroupNanos": 200,
            "maximumUnitsPerGroup": 10,
        }
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            NS["gr7_validate_marker_scalars"](
                marker, marker_raw, result_raw, RUN_ID, SOURCE_SHA, WIRE_SHA, pre)
        self.assertEqual(caught.exception.code, "GR7_MARKER_TOTAL_WORK")


class ProjectedReferenceTests(unittest.TestCase):
    def setUp(self):
        if REFERENCE_PACKET is None or FIXTURE_RAW is None:
            self.skipTest("Root did not supply source-frozen fixture and T reference packet")
        self.result, self.result_raw, self.marker, self.marker_raw = build_projected_case(
            NS, REFERENCE_PACKET, FIXTURE_RAW)

    def admit(self, result_raw=None, marker_raw=None, fixture_raw=None,
              expected_run_id=RUN_ID, expected_source_sha=SOURCE_SHA,
              expected_wire_sha=WIRE_SHA):
        return NS["validate_result_and_marker"](
            self.result_raw if result_raw is None else result_raw,
            self.marker_raw if marker_raw is None else marker_raw,
            expected_run_id, expected_source_sha, expected_wire_sha,
            FIXTURE_RAW if fixture_raw is None else fixture_raw)

    def raw_with_result(self, result):
        return canonical_raw(result)

    def marker_for(self, result, result_raw, overrides=None):
        marker = make_marker(NS, result, result_raw, overrides)
        return marker, NS["canonical_line"](marker)

    def assert_gr7_rejects(self, result, expected_code):
        result_raw = self.raw_with_result(result)
        marker, marker_raw = self.marker_for(result, result_raw)
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            self.admit(result_raw, marker_raw)
        self.assertEqual(caught.exception.code, "GR7_" + expected_code)

    def assert_t_rejects(self, result, marker_raw=None):
        result_raw = self.raw_with_result(result)
        if marker_raw is None:
            _marker, marker_raw = self.marker_for(result, result_raw)
        with self.assertRaises(NS["ReaderError"]) as caught:
            self.admit(result_raw, marker_raw)
        self.assertTrue(caught.exception.code.startswith("SW_"))

    def test_summary_is_fresh_scalar_and_explicitly_derived_only(self):
        saved_result = self.result_raw
        saved_marker = self.marker_raw
        saved_fixture = FIXTURE_RAW
        summary = self.admit()
        self.assertEqual(tuple(summary), (
            "schema", "status", "runId", "sourceManifestSha256",
            "wireContractSha256", "fixtureOracleSha256", "resultSha256",
            "resultBytes", "markerBytes", "references", "found", "noPath",
            "derivedExportUnits", "derivedExportCopyEntries",
            "derivedExportCopyChunks", "derivedExportGroups", "fullD",
            "R7actualCounterProof"))
        self.assertEqual(summary["schema"], NS["GR7_SUMMARY_SCHEMA"])
        self.assertEqual(summary["status"], NS["GR7_SUMMARY_STATUS"])
        self.assertEqual((summary["references"], summary["found"], summary["noPath"]),
                         (2, 1, 1))
        self.assertFalse(summary["fullD"])
        self.assertFalse(summary["R7actualCounterProof"])
        self.assertTrue(all(type(value) in (str, int, bool) for value in summary.values()))
        self.assertEqual((self.result_raw, self.marker_raw, FIXTURE_RAW),
                         (saved_result, saved_marker, saved_fixture))
        summary["runId"] = "changed"
        next_summary = self.admit()
        self.assertIsNot(summary, next_summary)
        self.assertEqual(next_summary["runId"], RUN_ID)

    def test_result_exact_header_identity_and_no_legacy_fields(self):
        reversed_result = dict(reversed(tuple(self.result.items())))
        self.assert_gr7_rejects(reversed_result, "RESULT_KEYS")

        wrong_status = copy.deepcopy(self.result)
        wrong_status["status"] = "GENERIC_NATURAL_R7_REFERENCE_EXPORTED"
        self.assert_gr7_rejects(wrong_status, "RESULT_IDENTITY")

        extra = copy.deepcopy(self.result)
        extra["originalArm"] = {}
        self.assert_gr7_rejects(extra, "RESULT_KEYS")

        work_extra = copy.deepcopy(self.result)
        work_extra["work"]["postExport"] = {"units": 0}
        self.assert_gr7_rejects(work_extra, "WORK_KEYS")

    def test_reference_and_review_order_identity_and_construction_guards(self):
        wrong_reference_order = copy.deepcopy(self.result)
        refs = wrong_reference_order["references"]
        wrong_reference_order["references"] = dict(reversed(tuple(refs.items())))
        self.assert_gr7_rejects(wrong_reference_order, "REFERENCES_KEYS")

        wrong_context = copy.deepcopy(self.result)
        wrong_context["references"]["OPEN"]["fixtureContextSha256"] = "0" * 64
        self.assert_gr7_rejects(wrong_context, "REFERENCE_IDENTITY")

        wrong_constructor = copy.deepcopy(self.result)
        wrong_constructor["references"]["BLOCKED"]["construction"]["after"] = True
        self.assert_gr7_rejects(wrong_constructor, "CONSTRUCTION_BINDING")

        wrong_review = copy.deepcopy(self.result)
        wrong_review["sourceReview"]["studyParentSha256"] = "0" * 64
        self.assert_gr7_rejects(wrong_review, "SOURCE_REVIEW_BINDING")

    def test_marker_exact_header_identity_digest_length_and_strict_types(self):
        reversed_marker = dict(reversed(tuple(self.marker.items())))
        reversed_marker_raw = NS["canonical_line"](reversed_marker)
        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            self.admit(marker_raw=reversed_marker_raw)
        self.assertEqual(caught.exception.code, "GR7_MARKER_KEYS")

        for key, bad_value, expected_code in (
                ("status", "GENERIC_NATURAL_R7_REFERENCE_RECORDED_ONLY", "MARKER_IDENTITY"),
                ("fixtureOracleSha256", "0" * 64, "MARKER_IDENTITY"),
                ("resultSha256", "0" * 64, "MARKER_RESULT_BINDING"),
                ("resultBytes", self.marker["resultBytes"] + 1, "MARKER_RESULT_BINDING"),
                ("totalGroups", True, "MARKER_TOTAL_WORK")):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.marker)
                changed[key] = bad_value
                changed_raw = NS["canonical_line"](changed)
                with self.assertRaises(NS["GR7_ReaderError"]) as caught:
                    self.admit(marker_raw=changed_raw)
                self.assertEqual(caught.exception.code, "GR7_" + expected_code)

    def test_phase_ledger_rejects_boolean_underflow_and_wrong_run_delta(self):
        bad_bool = copy.deepcopy(self.result)
        bad_bool["work"]["setup"]["units"] = True
        self.assert_gr7_rejects(bad_bool, "WORK_SETUP_INTEGER")

        bad_metadata = copy.deepcopy(self.result)
        bad_metadata["work"]["metadata"]["units"] -= 1
        self.assert_gr7_rejects(bad_metadata, "WORK_PHASE_SUM")

        bad_delta = copy.deepcopy(self.result)
        bad_delta["work"]["open"]["groups"] += 1
        self.assert_gr7_rejects(bad_delta, "WORK_OPEN_RUN_DELTA")

        bad_limits = copy.deepcopy(self.result)
        bad_limits["work"]["limits"]["producerNanos"] = 90_000_000_001
        self.assert_gr7_rejects(bad_limits, "WORK_LIMIT_VALUE")

    def test_marker_durations_totals_and_maxima_are_derived_from_pre_export(self):
        for key, value, expected_code in (
                ("elapsedNanos", self.result["work"]["preExport"]["elapsedNanos"] - 1,
                 "MARKER_DURATION"),
                ("finalExportNanos", NS["GR7_MAX_GROUP_NS"] + 1, "MARKER_DURATION"),
                ("totalGroups", self.marker["totalGroups"] + 1, "MARKER_TOTAL_WORK"),
                ("totalUnits", self.marker["totalUnits"] + 1, "MARKER_TOTAL_WORK"),
                ("maxGroupNanos", self.marker["maxGroupNanos"] + 1, "MARKER_MAXIMA"),
                ("maximumUnitsPerGroup", self.marker["maximumUnitsPerGroup"] + 1,
                 "MARKER_MAXIMA")):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.marker)
                changed[key] = value
                changed_raw = NS["canonical_line"](changed)
                with self.assertRaises(NS["GR7_ReaderError"]) as caught:
                    self.admit(marker_raw=changed_raw)
                self.assertEqual(caught.exception.code, "GR7_" + expected_code)

    def test_terminal_failure_and_physical_geometry_remain_t_owned_oracles(self):
        bad_terminal = copy.deepcopy(self.result)
        bad_terminal["references"]["BLOCKED"]["run"]["terminalFailure"][
            "failedNetId"] = "NET_B"
        self.assert_t_rejects(bad_terminal)

        bad_pad = copy.deepcopy(self.result)
        bad_pad["references"]["OPEN"]["run"]["layout"]["pads"][0]["x"] += 1
        self.assert_t_rejects(bad_pad)

    def test_geometry_text_policy_uses_exact_typed_path(self):
        too_long = copy.deepcopy(self.result)
        too_long["references"]["OPEN"]["run"]["layout"]["components"][0][
            "geometryCanonical"] += "x" * 4097
        self.assert_t_rejects(too_long)

        wrong_type = copy.deepcopy(self.result)
        wrong_type["references"]["OPEN"]["run"]["layout"]["components"][0][
            "geometryCanonical"] = 1
        self.assert_t_rejects(wrong_type)

    def test_fixture_replacement_and_external_context_are_rejected_by_owner(self):
        with self.assertRaises(NS["ReaderError"]) as caught:
            self.admit(fixture_raw=FIXTURE_RAW + b" ")
        self.assertTrue(caught.exception.code.startswith("SW_"))

        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            self.admit(expected_run_id="not-a-uuid")
        self.assertEqual(caught.exception.code, "GR7_EXPECTED_CONTEXT")

        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            self.admit(expected_source_sha="0" * 64)
        self.assertEqual(caught.exception.code, "GR7_RESULT_IDENTITY")

        with self.assertRaises(NS["GR7_ReaderError"]) as caught:
            self.admit(expected_wire_sha="0" * 64)
        self.assertEqual(caught.exception.code, "GR7_RESULT_IDENTITY")

    def test_noncanonical_result_and_marker_bytes_keep_t_parser_owner(self):
        noncanonical_result = self.result_raw[:-1] + b" \n"
        with self.assertRaises(NS["ReaderError"]) as caught:
            self.admit(result_raw=noncanonical_result)
        self.assertTrue(caught.exception.code.startswith("SW_"))

        noncanonical_marker = self.marker_raw[:-1] + b" \n"
        with self.assertRaises(NS["ReaderError"]) as caught:
            self.admit(marker_raw=noncanonical_marker)
        self.assertTrue(caught.exception.code.startswith("SW_"))


NS = None
REFERENCE_PACKET = None
FIXTURE_RAW = None


def main(argv=None):
    global NS, REFERENCE_PACKET, FIXTURE_RAW
    parser = argparse.ArgumentParser()
    parser.add_argument("--t-reader-source", required=True)
    parser.add_argument("--kernel-source", required=True)
    parser.add_argument("--fixture-spec")
    parser.add_argument("--reference-packet")
    options = parser.parse_args(argv)
    NS = load_source_namespace(options.t_reader_source, options.kernel_source)
    if options.fixture_spec is not None:
        FIXTURE_RAW = Path(options.fixture_spec).read_bytes()
        NS["load_fixture_spec"](FIXTURE_RAW)
    if options.reference_packet is not None:
        if FIXTURE_RAW is None:
            parser.error("--reference-packet requires --fixture-spec")
        packet_raw = Path(options.reference_packet).read_bytes()
        REFERENCE_PACKET = NS["parse_packet"](packet_raw)
    program = unittest.main(argv=[sys.argv[0]], exit=False)
    return 0 if program.result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
