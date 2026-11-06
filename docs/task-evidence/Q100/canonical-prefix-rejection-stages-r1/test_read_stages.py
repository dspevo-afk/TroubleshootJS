"""Literal contract fixtures for the P6 stage payload reader."""
from __future__ import annotations

import copy
import hashlib
import json
import unittest

import read_stages as reader


class BranchOrder:
    @staticmethod
    def java_lexical_key(value):
        return tuple(ord(char) for char in value)


class ParentRouterRoleTests(unittest.TestCase):
    def setUp(self):
        self.accepted_path = (
            r"C:\study-fixture\prefix-via-ramp-r1\overlays-r5\BASE"
            r"\PcbLayerRoutingPrototype.java"
        )
        self.p5_pins = {
            "modeFiles": {"router": self.accepted_path},
            "files": [{"path": self.accepted_path,
                       "bytes": reader.P5_ROUTER_OVERLAY_BYTES,
                       "sha256": reader.P5_ROUTER_OVERLAY_SHA}],
        }

    def test_accepts_exact_parent_pinned_base_router_overlay(self):
        row = reader._validate_parent_router_role(
            {"router": self.accepted_path}, self.p5_pins,
        )
        self.assertEqual(row["sha256"], reader.P5_ROUTER_OVERLAY_SHA)

    def test_rejects_repository_source_instead_of_parent_overlay(self):
        mode = {"router": r"C:\repo\src\com\lushprojects\circuitjs1\client\PcbLayerRoutingPrototype.java"}
        with self.assertRaises(reader.Rejected):
            reader._validate_parent_router_role(mode, self.p5_pins)

    def test_rejects_parent_overlay_with_wrong_hash_or_size(self):
        for field, value in (("sha256", "0" * 64), ("bytes", reader.P5_ROUTER_OVERLAY_BYTES + 1)):
            p5_pins = copy.deepcopy(self.p5_pins)
            p5_pins["files"][0][field] = value
            with self.subTest(field=field), self.assertRaises(reader.Rejected):
                reader._validate_parent_router_role({"router": self.accepted_path}, p5_pins)


class SourceParentChainTests(unittest.TestCase):
    def setUp(self):
        self.pins = {"historicalRoot": r"C:\temp\p5-current"}
        self.p5_pins = {
            "repositoryRoot": r"C:\temp\p5-current",
            "historicalRoot": r"C:\temp\p4-current",
        }
        self.p6_baseline = {"repositoryRoot": r"C:\temp\p5-current"}
        self.p5_baseline = {"repositoryRoot": r"C:\temp\p4-current"}

    def test_accepts_distinct_p6_and_p5_baseline_roots(self):
        reader._validate_source_parent_chain(
            self.pins, self.p5_pins, self.p6_baseline, self.p5_baseline,
        )

    def test_rejects_using_p6_baseline_as_p5_historical_root(self):
        p5_pins = dict(self.p5_pins)
        p5_pins["historicalRoot"] = self.p6_baseline["repositoryRoot"]
        with self.assertRaises(reader.Rejected):
            reader._validate_source_parent_chain(
                self.pins, p5_pins, self.p6_baseline, self.p5_baseline,
            )


class RunIdShapeTests(unittest.TestCase):
    def test_accepts_standard_qualified_uuid_and_rejects_missing_group(self):
        self.assertIsNotNone(reader.UUID.fullmatch("3f6a28cd-03a9-4a5f-ac70-7bbb02db0156"))
        self.assertIsNone(reader.UUID.fullmatch("3f6a28cd-03a9-4a5f-7bbb02db0156"))


class QualifiedSidecarRefTests(unittest.TestCase):
    def setUp(self):
        self.sha = "a" * 64
        self.run = {"transport": {"path": "capture-r3/data/fixed-prefix.jsonl",
                                   "bytes": 5138203, "sha256": self.sha}}
        self.index = {"transport": {"path": "fixed-prefix.jsonl", "bytes": 5138203,
                                     "sha256": self.sha,
                                     "graph": {"bytes": 1, "sha256": "b" * 64},
                                     "facts": {"bytes": 1, "sha256": "c" * 64}}}

    def test_binds_qualification_ref_to_index_sidecar_not_operation_entry(self):
        ref = reader._validate_qualified_sidecar_ref(
            self.run, self.index, "transport", "capture-r3/data/fixed-prefix.jsonl",
            "TEST_TRANSPORT",
        )
        self.assertEqual(ref["sha256"], self.sha)

    def test_rejects_absent_index_sidecar(self):
        with self.assertRaises(reader.Rejected):
            reader._validate_qualified_sidecar_ref(
                self.run, {"operation": {}}, "transport",
                "capture-r3/data/fixed-prefix.jsonl", "TEST_TRANSPORT",
            )


class StageIndexProjectionTests(unittest.TestCase):
    class P5IndexSchema:
        INDEX_SCHEMA = "tsj-canonical-prefix-blocker-index-v1"

    def test_validates_p6_scope_and_projects_the_pinned_p5_scope(self):
        value = {"schema": reader.INDEX_SCHEMA, "mode": "OBSERVE",
                 "scope": reader.SCOPE, "stages": {"path": "prefix-stages.json"},
                 "runId": "run", "operation": {}}
        projection = reader._p5_index_projection(value, self.P5IndexSchema)
        self.assertEqual(value["scope"], "CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY")
        self.assertEqual(projection["scope"], "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY")
        self.assertEqual(projection["schema"], self.P5IndexSchema.INDEX_SCHEMA)
        self.assertEqual(projection["mode"], "CAPTURE")
        self.assertNotIn("stages", projection)

    def test_rejects_p5_scope_in_p6_outer_index(self):
        value = {"scope": reader.P5_INDEX_SCOPE, "stages": {}}
        with self.assertRaises(reader.Rejected):
            reader._p5_index_projection(value, self.P5IndexSchema)


class StagePayloadTests(unittest.TestCase):
    def setUp(self):
        self.branch = BranchOrder()
        self.facts = {
            "netId": "A_COIL_LOW", "startPadId": "QA.C", "rootPadId": "DA.A",
            "receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77, "pass": 2,
            "startX": 3870, "startY": 2410, "rootX": 3540, "rootY": 2700,
            "prefixGeometrySha256": reader.PREFIX_SHA,
            "reachedPadIds": ["DA.A"], "reachedPthPadIds": [],
            "gridWidth": 478, "gridHeight": 309, "gridPitch": 10,
            "originX": 30, "originY": 30, "cellCount": 147702,
        }
        pads = [
            {"padId": "QA.C", "netId": "A_COIL_LOW", "x": 3870, "y": 2410,
             "role": "HIGH_CURRENT"},
            {"padId": "DA.A", "netId": "A_COIL_LOW", "x": 3540, "y": 2700,
             "role": "HIGH_CURRENT"},
        ]
        for index in range(59):
            pads.append({"padId": "P%02d" % index, "netId": "N%02d" % index,
                         "x": 0, "y": 0, "role": "SIGNAL"})
        self.operation = {
            "header": {"candidate": {"padCount": len(pads), "netCount": 60}},
            "pads": pads,
            "branches": [{"ordinal": 307, "snapshot": {
                "pass": 2, "netId": "A_COIL_LOW", "startPadId": "QA.C",
                "rootPadId": "DA.A", "terminalReason": "NO_PATH", "reachedPadCount": 1,
            }}],
        }
        self.cut = {"cutQueries": self._cut_queries()}
        self.parent_attribution = self._parent_attribution()
        self.parent_totals = {
            "copyUnits": 2, "comparisonUnits": 3, "queryUnits": 22,
            "geometryRowUnits": 1,
        }

    def _cut_queries(self):
        rows = []
        for ordinal in range(22):
            layer = 0 if ordinal < 11 else 1
            from_cell = 20 + ordinal
            to_cell = from_cell + 1
            fx, fy = from_cell % 478, from_cell // 478
            tx, ty = to_cell % 478, to_cell // 478
            rows.append({
                "ordinal": ordinal, "layer": layer,
                "fromState": layer * 147702 + from_cell,
                "toState": layer * 147702 + to_cell, "bit": 2,
                "fromX": 30 + fx * 10, "fromY": 30 + fy * 10,
                "toX": 30 + tx * 10, "toY": 30 + ty * 10,
            })
        return rows

    def _parent_attribution(self):
        effects = "d" * 64
        return {
            "sourceProof": {"faces": [
                {"layer": 0, "sourceEffectsBeforeSha256": effects,
                 "sourceEffectsAfterSha256": effects},
                {"layer": 1, "sourceEffectsBeforeSha256": effects,
                 "sourceEffectsAfterSha256": effects},
            ]},
            "observations": [{
                "routerCongestionBefore": 0, "routerCongestionAfter": 0,
                "routerCongestionDelta": 0, "workCongestionBefore": 0,
                "workCongestionAfter": 0, "workCongestionDelta": 0,
                "conflictsBefore": [], "conflictsAfter": [], "conflictsDelta": [],
            } for _ in range(22)],
        }

    def _stage(self, mode):
        run_id = "12345678-1234-4234-8234-123456789abc"
        queries = []
        count = 1 if mode == "CALLBACK_THROW" else 22
        event_count = 0
        selected_ordinals = sorted(reader.SELECTED_ORDINALS)
        for ordinal in range(count):
            cut = self.cut["cutQueries"][ordinal]
            from_cell = cut["fromState"] % self.facts["cellCount"]
            to_cell = cut["toState"] % self.facts["cellCount"]
            fx, fy = from_cell % 478, from_cell // 478
            tx, ty = to_cell % 478, to_cell // 478
            selected = ordinal in reader.SELECTED_ORDINALS
            callback = mode == "OBSERVE" and selected or mode == "CALLBACK_THROW"
            events = []
            if callback:
                events.append({
                    "ordinal": 0, "netId": "A_COIL_LOW", "startPadId": "QA.C",
                    "endpointPadId": "DA.A", "fromGridX": fx, "fromGridY": fy,
                    "toGridX": tx, "toGridY": ty,
                    "direction": 1,
                    "stage": "TRAVERSE_REJECTED" if mode == "CALLBACK_THROW"
                    else "LEGAL_MOVE_REJECTED",
                })
                event_count += 1
            queries.append({
                "ordinal": ordinal, "layer": cut["layer"],
                "fromState": cut["fromState"], "toState": cut["toState"],
                "bit": cut["bit"], "selected": selected,
                "fromGridX": fx, "fromGridY": fy, "toGridX": tx, "toGridY": ty,
                "outcome": "EXPECTED_CALLBACK_THROW" if mode == "CALLBACK_THROW" else "RETURNED",
                "permitsStep": None if mode == "CALLBACK_THROW" else False,
                "routerCongestionBefore": 0, "routerCongestionAfter": 0,
                "routerCongestionDelta": 0, "workCongestionBefore": 0,
                "workCongestionAfter": 0, "workCongestionDelta": 0,
                "conflictsBefore": [], "conflictsAfter": [], "conflictsDelta": [],
                "events": events,
            })
        face_rows = []
        for layer in (0, 1):
            parent_face = self.parent_attribution["sourceProof"]["faces"][layer]
            face_rows.append({
                "layer": layer,
                "sourceEffectsBeforeSha256": parent_face["sourceEffectsBeforeSha256"],
                "sourceEffectsAfterSha256": parent_face["sourceEffectsAfterSha256"],
                "liveToPristineBeforeEqual": True, "queryToPristineBeforeEqual": True,
                "liveToPristineAfterEqual": True, "liveObserverNullBefore": True,
                "pristineObserverNullBefore": True, "liveObserverNullAfter": True,
                "pristineObserverNullAfter": True, "queryObserverNullAfter": True,
                "copyEntries": 8192, "comparisonEntries": 12288,
                "copyUnits": 4, "comparisonUnits": 6,
                "queryCount": sum(1 for row in queries if row["layer"] == layer),
            })
        prior = 0 if mode == "CALLBACK_THROW" else 28
        callbacks = 1 if mode == "CALLBACK_THROW" else 14 if mode == "OBSERVE" else 0
        query_count = len(queries)
        return {
            "schema": reader.STAGES_SCHEMA,
            "status": {"NULL": "COMPLETE_NULL_CONTROL_ONLY",
                       "OBSERVE": "COMPLETE_STAGE_OBSERVATIONS_ONLY",
                       "CALLBACK_THROW": "EXPECTED_CALLBACK_THROW_ONLY"}[mode],
            "mode": mode, "runId": run_id, "sourceManifestSha256": "a" * 64,
            "wireSha256": reader.WIRE_SHA, "scope": reader.SCOPE,
            "layoutQualification": False,
            "zeroDeltaInputSha256": reader.ZERO_DELTA_SHA,
            "fullCutInputSha256": reader.FULL_CUT_SHA,
            "graphSha256": reader.GRAPH_SHA,
            "prefixGeometrySha256": reader.PREFIX_SHA,
            "receiptBranchOrdinal": 307, "lineageBranchOrdinal": 77,
            "netId": "A_COIL_LOW", "startPadId": "QA.C", "rootPadId": "DA.A",
            "reachedPthPadIds": [],
            "lifecycle": {"offers": 308, "selected": 1,
                          "completed": 0 if mode == "CALLBACK_THROW" else 1,
                          "ownersReleased": True, "contextRestored": True,
                          "sourceRefsDropped": True, "observersDetached": True},
            "sourceProof": {"geometryBeforeSha256": reader.PREFIX_SHA,
                             "geometryAfterSha256": reader.PREFIX_SHA,
                             "faces": face_rows},
            "queries": queries,
            "totals": {
                "queryCount": query_count,
                "selectedQueryCount": 1 if mode == "CALLBACK_THROW" else 14,
                "callbackQueryCount": callbacks, "eventCount": event_count,
                "attachments": callbacks, "detachments": callbacks,
                "copyEntries": 16384, "comparisonEntries": 24576,
                "copyUnits": 8, "comparisonUnits": 12,
                "queryUnits": query_count, "priorObservationUnits": prior,
                "totalObservationUnits": prior + 8 + 12 + query_count,
                "elapsedNanos": 20000, "maximumUnitNanos": 1000,
            },
            "exceptionStage": "FIRST_ENDPOINT_CALLBACK" if mode == "CALLBACK_THROW" else None,
        }

    def _validate(self, mode, payload=None):
        payload = self._stage(mode) if payload is None else payload
        raw = (json.dumps(payload, ensure_ascii=True, separators=(",", ":")) + "\n").encode("ascii")
        return reader._validate_stage_payload(
            raw, payload, mode, payload["runId"], "a" * 64, self.facts,
            self.operation, self.parent_attribution, self.parent_totals, self.cut,
            100000, None, self.branch,
        )

    def test_null_control_has_no_observer_callbacks_or_events(self):
        totals = self._validate("NULL")
        self.assertEqual(totals["callbackQueryCount"], 0)
        self.assertEqual(totals["eventCount"], 0)

    def test_observe_events_follow_selected_cut_ordinals(self):
        totals = self._validate("OBSERVE")
        self.assertEqual(totals["selectedQueryCount"], 14)
        self.assertEqual(totals["callbackQueryCount"], 14)
        self.assertEqual(totals["eventCount"], 14)

    def test_callback_throw_keeps_full_two_face_cleanup_proof(self):
        payload = self._stage("CALLBACK_THROW")
        totals = self._validate("CALLBACK_THROW", payload)
        self.assertEqual(payload["lifecycle"]["completed"], 0)
        self.assertEqual([row["layer"] for row in payload["sourceProof"]["faces"]], [0, 1])
        self.assertEqual(totals["queryCount"], 1)

    def test_unknown_stage_name_is_rejected(self):
        payload = self._stage("OBSERVE")
        payload["queries"][0]["events"][0]["stage"] = "OCCUPANCY_REJECTED_EXTRA"
        with self.assertRaises(reader.Rejected):
            self._validate("OBSERVE", payload)

    def test_missing_selected_event_is_rejected(self):
        payload = self._stage("OBSERVE")
        payload["queries"][0]["events"] = []
        with self.assertRaises(reader.Rejected):
            self._validate("OBSERVE", payload)

    def test_duplicate_endpoint_event_is_rejected(self):
        payload = self._stage("OBSERVE")
        payload["queries"][0]["events"].append(copy.deepcopy(payload["queries"][0]["events"][0]))
        with self.assertRaises(reader.Rejected):
            self._validate("OBSERVE", payload)

    def test_null_mode_rejects_an_invented_event(self):
        payload = self._stage("NULL")
        event = self._stage("OBSERVE")["queries"][0]["events"][0]
        payload["queries"][0]["events"] = [event]
        payload["totals"]["eventCount"] = 1
        with self.assertRaises(reader.Rejected):
            self._validate("NULL", payload)

    def test_boolean_is_not_accepted_as_counter_integer(self):
        payload = self._stage("NULL")
        payload["queries"][0]["routerCongestionBefore"] = False
        with self.assertRaises(reader.Rejected):
            self._validate("NULL", payload)

    def test_duplicate_or_reordered_query_ordinal_is_rejected(self):
        payload = self._stage("OBSERVE")
        payload["queries"][1]["ordinal"] = 0
        with self.assertRaises(reader.Rejected):
            self._validate("OBSERVE", payload)

    def test_cleanup_flags_must_be_actual_booleans(self):
        payload = self._stage("CALLBACK_THROW")
        payload["lifecycle"]["observersDetached"] = 1
        with self.assertRaises(reader.Rejected):
            self._validate("CALLBACK_THROW", payload)

    def test_cross_mode_parity_accepts_valid_null_and_observe_payloads(self):
        null_payload = self._stage("NULL")
        observe_payload = self._stage("OBSERVE")
        self._validate("NULL", null_payload)
        self._validate("OBSERVE", observe_payload)
        reader._assert_stage_parity(null_payload, observe_payload)

    def test_mode_specific_observer_totals_remain_strict(self):
        fields = ("callbackQueryCount", "eventCount", "attachments", "detachments")
        for mode, change in (("NULL", 1), ("OBSERVE", -1)):
            for field in fields:
                with self.subTest(mode=mode, field=field):
                    payload = self._stage(mode)
                    payload["totals"][field] += change
                    with self.assertRaises(reader.Rejected):
                        self._validate(mode, payload)

    def test_cross_mode_parity_rejects_shared_query_copy_unit_proof_drift(self):
        null_payload = self._stage("NULL")
        observe_payload = self._stage("OBSERVE")
        self._validate("NULL", null_payload)
        self._validate("OBSERVE", observe_payload)

        def change_source_proof(payload):
            face = payload["sourceProof"]["faces"][0]
            before = face["sourceEffectsAfterSha256"]
            face["sourceEffectsAfterSha256"] = "e" * 64 if before != "e" * 64 else "f" * 64

        mutations = (
            ("query", lambda payload: payload["queries"][0].__setitem__(
                "routerCongestionBefore", payload["queries"][0]["routerCongestionBefore"] + 1)),
            ("counter-map", lambda payload: payload["queries"][0]["conflictsBefore"].append(
                {"netId": "N00", "count": 1})),
            ("copy-entry", lambda payload: payload["sourceProof"]["faces"][0].__setitem__(
                "copyEntries", payload["sourceProof"]["faces"][0]["copyEntries"] + 1)),
            ("unit-count", lambda payload: payload["totals"].__setitem__(
                "copyUnits", payload["totals"]["copyUnits"] + 1)),
            ("source-proof", change_source_proof),
        )
        for label, mutate in mutations:
            with self.subTest(drift=label):
                altered = copy.deepcopy(observe_payload)
                mutate(altered)
                self.assertNotEqual(reader._normalized_stage(null_payload),
                                    reader._normalized_stage(altered))
                with self.assertRaises(reader.Rejected):
                    reader._assert_stage_parity(null_payload, altered)


if __name__ == "__main__":
    unittest.main()
