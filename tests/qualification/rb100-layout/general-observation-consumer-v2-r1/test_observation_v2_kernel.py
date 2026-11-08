"""Literal independent observation V2 fixtures; no captured packets or producers.

Run with the selected Python using -I -B and optionally --kernel-source PATH.
The loader does not change sys.path and permits testing a reviewed source copy.
"""

import argparse
import copy
import importlib.util
from pathlib import Path
import types
import unittest


KERNEL_SOURCE = Path(__file__).with_name("observation_v2_kernel.py")


def context():
    return {
        "requested": ["NET_A", "NET_B", "NET_C"],
        "traversed": ["NET_A", "NET_B"],
        "pads_by_id": {
            "A0": ("NET_A", 0, 1), "A1": ("NET_A", 4, 5),
            "B0": ("NET_B", -5, 9), "B1": ("NET_B", 600, -8),
            "B2": ("NET_B", 12, 13), "C0": ("NET_C", 3, 4),
        },
        "pads_by_net": {"NET_A": {"A0", "A1"},
                        "NET_B": {"B0", "B1", "B2"}, "NET_C": {"C0"}},
        "grid": (20, 10),
        "outcome": {"disposition": "CAP_REJECTED",
                    "reason": "NET_B:BRANCH_SEARCH_LIMIT", "expanded": 100007},
        "terminal": {"failedNetId": "NET_B", "ownerReason": "NET_B:BRANCH_SEARCH_LIMIT",
                     "searchPass": 2},
    }


def snapshot():
    return {
        "pass": 2, "netId": "NET_B", "startPadId": "B0", "rootPadId": "B1",
        "startX": -5, "startY": 9, "rootX": 600, "rootY": -8,
        "gridWidth": 20, "gridHeight": 10, "goalsTop": 2, "goalsBottom": 1,
        "prefixViaCount": 3, "reachedPadCount": 3, "initialManhattanDistance": 17,
        "minimumExploredManhattanDistance": 0, "branchExpanded": 100000,
        "totalExpanded": 100007, "expandedTop": 60000, "expandedBottom": 40000,
        "frontierCount": 3, "bestStateCount": 9, "terminalReason": "BRANCH_SEARCH_LIMIT",
        "relaxedBoundStarted": False, "relaxedBoundComplete": False,
        "relaxedBoundObservationChecked": False, "relaxedBoundLiveLayoutStable": False,
        "relaxedBoundRouterStateStable": False, "relaxedBoundRouterEffectsStable": False,
        "relaxedBoundWorkUnits": 0, "relaxedBoundOrdinaryProbes": 0,
        "relaxedBoundViaProbes": 0, "relaxedBoundRefreshes": 0,
    }


def head():
    # Grid 200 cells: (((2*2+1)*5+4)*200)+8 = 5808.
    return {
        "branchOrdinal": 7, "cell": 8, "layer": 1, "direction": 4,
        "transitions": 2, "cost": 2147483647, "storedHeuristic": 1,
        "serial": 19, "key": 5808, "storedPriority": -2147483648,
    }


def branch_observation():
    # Two preceding FOUND callbacks, then the selected failure on net ordinal 2.
    # Independent expected totals: entries 109, chunks 4, units 207+200+21=428.
    return {
        "schema": "tsj-cohort-terminal-observation-v2", "mode": "OBSERVE",
        "state": "FIRST_NON_FOUND", "callbackCount": 3, "foundCallbackCount": 2,
        "nonFoundCallbackCount": 1, "firstNetOrdinal": 1, "lastNetOrdinal": 2,
        "lastCallbackTotalExpanded": 100007, "selectedCallbackOrdinal": 3,
        "transientTupleCopies": 4, "transientFieldCopies": 109,
        "validationCount": 200, "observerUnits": 428, "copiedEntries": 109,
        "copyChunks": 4, "selectedSnapshot": snapshot(), "selectedHead": head(),
    }


def empty_observation(mode="OBSERVE", state="NON_BRANCH_REJECTION"):
    return {
        "schema": "tsj-cohort-terminal-observation-v2", "mode": mode, "state": state,
        "callbackCount": 0, "foundCallbackCount": 0, "nonFoundCallbackCount": 0,
        "firstNetOrdinal": None, "lastNetOrdinal": None, "lastCallbackTotalExpanded": None,
        "selectedCallbackOrdinal": None, "transientTupleCopies": 0,
        "transientFieldCopies": 0, "validationCount": 0, "observerUnits": 0,
        "copiedEntries": 0, "copyChunks": 0, "selectedSnapshot": None, "selectedHead": None,
    }


def found_observation():
    return {
        "schema": "tsj-cohort-terminal-observation-v2", "mode": "OBSERVE", "state": "NO_FAILURE",
        "callbackCount": 2, "foundCallbackCount": 2, "nonFoundCallbackCount": 0,
        "firstNetOrdinal": 1, "lastNetOrdinal": 2, "lastCallbackTotalExpanded": 19,
        "selectedCallbackOrdinal": None, "transientTupleCopies": 2,
        "transientFieldCopies": 66, "validationCount": 182, "observerUnits": 320,
        "copiedEntries": 66, "copyChunks": 2, "selectedSnapshot": None, "selectedHead": None,
    }


class ObservationV2KernelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("literal_observation_kernel", KERNEL_SOURCE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        cls.kernel = module.ObservationV2Kernel
        cls.error = module.ObservationV2Error

    def validate(self, o, c=None, opportunities=3, role="OBSERVE"):
        c = context() if c is None else c
        return self.kernel.validate_observation(
            o, role, opportunities, c["requested"], c["traversed"], c["pads_by_id"],
            c["pads_by_net"], c["grid"], c["outcome"], c["terminal"])

    def validate_snapshot(self, s, c=None):
        c = context() if c is None else c
        return self.kernel.validate_snapshot(s, c["requested"], c["traversed"],
                                             c["pads_by_id"], c["pads_by_net"], c["grid"])

    def rejected(self, operation, code=None):
        with self.assertRaises(self.error) as caught:
            operation()
        if code is not None:
            self.assertEqual(caught.exception.code, code)
        self.assertEqual(str(caught.exception), caught.exception.code)

    def ordinary_failure(self, reason="NO_PATH"):
        o, c = branch_observation(), context()
        s = o["selectedSnapshot"]
        s.update(branchExpanded=5, totalExpanded=19, expandedTop=3, expandedBottom=2,
                 minimumExploredManhattanDistance=3, frontierCount=0,
                 terminalReason=reason)
        if reason == "TOTAL_SEARCH_LIMIT":
            s["totalExpanded"] = 1000000
        o.update(lastCallbackTotalExpanded=s["totalExpanded"], selectedHead=None,
                 transientTupleCopies=3, transientFieldCopies=99, validationCount=12,
                 observerUnits=219, copiedEntries=99, copyChunks=3)
        c["outcome"].update(disposition="NO_PATH" if reason == "NO_PATH" else "CAP_REJECTED",
                            reason="NET_B:" + reason, expanded=s["totalExpanded"])
        c["terminal"]["ownerReason"] = "NET_B:" + reason
        return o, c

    def test_multinet_preceding_found_signed_world_coordinates_and_immutable_result(self):
        o, c = branch_observation(), context()
        before_o, before_c = copy.deepcopy(o), copy.deepcopy(c)
        self.assertEqual(self.validate_snapshot(o["selectedSnapshot"], c), 2)
        facts = self.validate(o, c)
        self.assertIsInstance(facts, types.MappingProxyType)
        self.assertEqual(dict(facts), {"observerUnits": 428, "copiedEntries": 109,
                                       "copyChunks": 4, "state": "FIRST_NON_FOUND"})
        self.assertEqual(o, before_o)
        self.assertEqual(c, before_c)
        with self.assertRaises(TypeError):
            facts["observerUnits"] = 0
        o["observerUnits"] = 0
        o["selectedSnapshot"]["netId"] = "CHANGED"
        c["requested"].clear()
        self.assertEqual(facts["observerUnits"], 428)
        self.assertEqual(set(facts), {"observerUnits", "copiedEntries", "copyChunks", "state"})

    def test_null_does_not_require_fresh_run_not_run(self):
        facts = self.validate(empty_observation("NULL", "NOT_ENABLED"), role="NULL")
        self.assertEqual(dict(facts), {"observerUnits": 0, "copiedEntries": 0,
                                       "copyChunks": 0, "state": "NOT_ENABLED"})
        self.assertIsInstance(facts, types.MappingProxyType)

    def test_public_context_shapes_reject_with_owned_errors_in_every_state(self):
        found_context = context()
        found_context["outcome"] = {"disposition": "FOUND", "reason": "FOUND", "expanded": 25}
        found_context["terminal"] = None
        quality_context = context()
        quality_context["outcome"] = {"disposition": "QUALITY_REJECTED", "reason": "QUALITY_LIMIT", "expanded": 0}
        quality_context["terminal"] = None
        cases = ((branch_observation(), context(), "OBSERVE"),
                 (found_observation(), found_context, "OBSERVE"),
                 (empty_observation(), quality_context, "OBSERVE"),
                 (empty_observation("NULL", "NOT_ENABLED"), context(), "NULL"))
        mutations = [
            ("requested", None, "OBSERVATION_CONTEXT_ORDERING"),
            ("requested", {"NET_A"}, "OBSERVATION_CONTEXT_ORDERING"),
            ("requested", ["NET_A", True], "OBSERVATION_CONTEXT_ORDERING"),
            ("traversed", [""], "OBSERVATION_CONTEXT_ORDERING"),
            ("pads_by_id", None, "OBSERVATION_CONTEXT_PADS"),
            ("pads_by_net", [], "OBSERVATION_CONTEXT_PADS"),
            ("pads_by_id", {"B0": ["NET_B", 1, 2]}, "OBSERVATION_CONTEXT_PAD_ROWS"),
            ("pads_by_id", {"B0": ("NET_B", True, 2)}, "OBSERVATION_CONTEXT_PAD_ROWS"),
            ("pads_by_id", {"B0": (True, 1, 2)}, "OBSERVATION_CONTEXT_PAD_ROWS"),
            ("pads_by_id", {False: ("NET_B", 1, 2)}, "OBSERVATION_CONTEXT_PAD_ROWS"),
            ("grid", [20, 10], "PRIOR_GRID_CONTEXT"),
            ("grid", (True, 10), "PRIOR_GRID_CONTEXT"),
            ("grid", (250000, 2), "PRIOR_GRID_CONTEXT"),
        ]
        for members in (None, 7, ["B0"], {"B0": 1}, "B0", frozenset({"B0"}), {"B0", True}):
            mutations.append(("pads_by_net", {"NET_B": members}, "OBSERVATION_CONTEXT_MEMBERSHIP"))
        for observation, base, role in cases:
            self.validate(observation, base, role=role)
            for name, value, code in mutations:
                with self.subTest(state=observation["state"], field=name, value=value):
                    changed = copy.deepcopy(base)
                    changed[name] = value
                    self.rejected(lambda: self.validate(observation, changed, role=role), code)
                    self.rejected(lambda: self.validate_snapshot(snapshot(), changed), code)

    def test_success_and_empty_success_preserve_general_domain(self):
        c = context()
        c["outcome"] = {"disposition": "FOUND", "reason": "FOUND", "expanded": 25}
        c["terminal"] = None
        self.assertEqual(self.validate(found_observation(), c)["observerUnits"], 320)
        # A zero-opportunity owner can finish without a callback.
        self.assertEqual(self.validate(empty_observation(state="NO_FAILURE"), c,
                                       opportunities=0)["copyChunks"], 0)

    def test_all_other_terminal_reasons_and_quality_disposition(self):
        for reason in ("NO_PATH", "TOTAL_SEARCH_LIMIT", "GRID_LIMIT"):
            with self.subTest(reason=reason):
                o, c = self.ordinary_failure(reason)
                self.assertEqual(self.validate(o, c)["observerUnits"], 219)
        o, c = self.ordinary_failure("GRID_LIMIT")
        c["outcome"]["disposition"] = "QUALITY_REJECTED"
        self.assertEqual(self.validate(o, c)["state"], "FIRST_NON_FOUND")

    def test_unscoped_quality_with_zero_or_preceding_found_callbacks(self):
        c = context()
        c["outcome"] = {"disposition": "QUALITY_REJECTED", "reason": "QUALITY_LIMIT", "expanded": 25}
        c["terminal"] = None
        self.assertEqual(self.validate(empty_observation(), c, opportunities=0)["observerUnits"], 0)
        o = found_observation()
        o["state"] = "NON_BRANCH_REJECTION"
        self.assertEqual(self.validate(o, c)["observerUnits"], 320)

    def test_scoped_quality_final_net_and_total(self):
        o, c = found_observation(), context()
        o["state"] = "NON_BRANCH_REJECTION"
        c["outcome"] = {"disposition": "QUALITY_REJECTED", "reason": "NET_B:BRANCH_QUALITY_LIMIT",
                        "expanded": 19}
        c["terminal"]["ownerReason"] = "NET_B:BRANCH_QUALITY_LIMIT"
        self.assertEqual(self.validate(o, c)["state"], "NON_BRANCH_REJECTION")
        for mutate in (lambda x: x["traversed"].append("NET_C"),
                       lambda x: x["terminal"].update(failedNetId="NET_A"),
                       lambda x: x["outcome"].update(expanded=20),
                       lambda x: x.update(terminal=None)):
            bad = copy.deepcopy(c)
            mutate(bad)
            self.rejected(lambda: self.validate(o, bad), "OBSERVATION_SCOPED_QUALITY_BINDING")

    def test_relaxed_complete_unchecked_and_checked_variants(self):
        for prefix, checked in ((0, False), (47, False), (48, True)):
            with self.subTest(prefix=prefix):
                s = snapshot()
                s.update(prefixViaCount=prefix, relaxedBoundStarted=True, relaxedBoundComplete=True,
                         relaxedBoundObservationChecked=checked, relaxedBoundLiveLayoutStable=checked,
                         relaxedBoundRouterStateStable=checked, relaxedBoundRouterEffectsStable=checked,
                         relaxedBoundWorkUnits=80, relaxedBoundOrdinaryProbes=31,
                         relaxedBoundViaProbes=3, relaxedBoundRefreshes=1)
                self.assertEqual(self.validate_snapshot(s), 2)

    def test_relaxed_invalid_states(self):
        mutations = (
            {"relaxedBoundStarted": True}, {"relaxedBoundComplete": True},
            {"relaxedBoundWorkUnits": 1}, {"relaxedBoundStarted": 0},
            {"relaxedBoundWorkUnits": True}, {"relaxedBoundViaProbes": -1},
            {"relaxedBoundRefreshes": 2147483648},
            {"relaxedBoundStarted": True, "relaxedBoundComplete": True,
             "prefixViaCount": 48},
            {"relaxedBoundLiveLayoutStable": True},
        )
        for changes in mutations:
            with self.subTest(changes=changes):
                s = snapshot()
                s.update(changes)
                self.rejected(lambda: self.validate_snapshot(s))

    def test_selected_branch_with_completed_relaxed_observation(self):
        o = branch_observation()
        o["selectedSnapshot"].update(
            prefixViaCount=48, relaxedBoundStarted=True, relaxedBoundComplete=True,
            relaxedBoundObservationChecked=True, relaxedBoundLiveLayoutStable=True,
            relaxedBoundRouterStateStable=True, relaxedBoundRouterEffectsStable=True,
            relaxedBoundWorkUnits=80, relaxedBoundOrdinaryProbes=31,
            relaxedBoundViaProbes=3, relaxedBoundRefreshes=1)
        o["selectedHead"].update(transitions=0, key=1808)
        self.assertEqual(self.validate(o)["observerUnits"], 428)

    def test_per_net_pad_bound_and_source_identity(self):
        # NET_B permits 3 reached pads even though NET_A has only 2.
        self.assertEqual(self.validate_snapshot(snapshot()), 2)
        s = snapshot()
        s["reachedPadCount"] = 4
        self.rejected(lambda: self.validate_snapshot(s), "SNAPSHOT_COUNTS")
        for mutate in (lambda c: c["pads_by_net"]["NET_B"].remove("B0"),
                       lambda c: c["pads_by_id"].update(B0=("NET_A", -5, 9)),
                       lambda c: c["pads_by_id"].update(B1=("NET_B", 601, -8))):
            c = context()
            mutate(c)
            self.rejected(lambda: self.validate_snapshot(snapshot(), c))
        c = context()
        c["traversed"] = ["NET_B", "NET_A"]
        self.rejected(lambda: self.validate_snapshot(snapshot(), c), "SNAPSHOT_TRAVERSAL_BINDING")

    def test_single_pad_and_same_endpoint_are_not_fixture_restrictions(self):
        c, s = context(), snapshot()
        c["requested"], c["traversed"] = ["NET_C"], ["NET_C"]
        s.update(netId="NET_C", startPadId="C0", rootPadId="C0", startX=3, startY=4,
                 rootX=3, rootY=4, reachedPadCount=1)
        self.assertEqual(self.validate_snapshot(s, c), 1)

    def test_snapshot_zero_expansion_and_found_domain(self):
        s = snapshot()
        s.update(branchExpanded=0, totalExpanded=9, expandedTop=0, expandedBottom=0,
                 minimumExploredManhattanDistance=-1, frontierCount=0, terminalReason="NO_PATH")
        self.assertEqual(self.validate_snapshot(s), 2)
        s["minimumExploredManhattanDistance"] = 0
        self.rejected(lambda: self.validate_snapshot(s), "SNAPSHOT_MINIMUM_DISTANCE")
        s = snapshot()
        s["terminalReason"] = "FOUND"
        self.assertEqual(self.validate_snapshot(s), 2)

    def test_snapshot_numeric_and_terminal_limits(self):
        changes = (
            {"pass": True}, {"pass": 1}, {"startX": -2147483649}, {"rootY": True},
            {"gridWidth": 0}, {"gridWidth": 250001}, {"gridHeight": 20000},
            {"goalsTop": 201}, {"goalsTop": 0, "goalsBottom": 0},
            {"prefixViaCount": 49}, {"reachedPadCount": 0},
            {"initialManhattanDistance": 2147483648}, {"initialManhattanDistance": True},
            {"branchExpanded": 100001}, {"totalExpanded": 1000001},
            {"totalExpanded": 99999}, {"expandedBottom": 39999},
            {"minimumExploredManhattanDistance": -1}, {"minimumExploredManhattanDistance": 18},
            {"frontierCount": 2147483648}, {"bestStateCount": False},
            {"terminalReason": "UNKNOWN"}, {"terminalReason": "NO_PATH"},
            {"terminalReason": "TOTAL_SEARCH_LIMIT"}, {"branchExpanded": 99999,
             "expandedTop": 59999},
        )
        for change in changes:
            with self.subTest(change=change):
                s = snapshot()
                s.update(change)
                self.rejected(lambda: self.validate_snapshot(s))
        c = context()
        c["grid"] = (21, 10)
        self.rejected(lambda: self.validate_snapshot(snapshot(), c), "SNAPSHOT_GRID_BINDING")
        for grid in ([20, 10], (True, 10), (250000, 2)):
            c = context()
            c["grid"] = grid
            self.rejected(lambda: self.validate_snapshot(snapshot(), c), "PRIOR_GRID_CONTEXT")

    def test_snapshot_java_integer_boundaries_and_boolean_fields(self):
        c, s = context(), snapshot()
        c["pads_by_id"]["B0"] = ("NET_B", -2147483648, 2147483647)
        s.update(startX=-2147483648, startY=2147483647,
                 initialManhattanDistance=2147483647, frontierCount=2147483647,
                 bestStateCount=2147483647, relaxedBoundStarted=True, relaxedBoundComplete=True,
                 relaxedBoundWorkUnits=2147483647, relaxedBoundOrdinaryProbes=2147483647,
                 relaxedBoundViaProbes=2147483647, relaxedBoundRefreshes=2147483647)
        self.assertEqual(self.validate_snapshot(s, c), 2)
        for field in ("pass", "startX", "startY", "rootX", "rootY", "gridWidth", "gridHeight",
                      "goalsTop", "goalsBottom", "prefixViaCount", "reachedPadCount",
                      "initialManhattanDistance", "minimumExploredManhattanDistance",
                      "branchExpanded", "totalExpanded", "expandedTop", "expandedBottom",
                      "frontierCount", "bestStateCount", "relaxedBoundWorkUnits",
                      "relaxedBoundOrdinaryProbes", "relaxedBoundViaProbes", "relaxedBoundRefreshes"):
            with self.subTest(field=field):
                bad = snapshot()
                bad[field] = True
                self.rejected(lambda: self.validate_snapshot(bad))

    def test_exact_ordered_18_33_10_key_domains(self):
        for factory, validate in ((branch_observation, self.validate),
                                  (snapshot, self.validate_snapshot),
                                  (head, lambda h: self.kernel.validate_head(h, snapshot()))):
            original = factory()
            for kind in ("missing", "extra", "reordered", "list"):
                with self.subTest(factory=factory.__name__, kind=kind):
                    value = factory()
                    if kind == "missing":
                        del value[next(iter(value))]
                    elif kind == "extra":
                        value["extra"] = 0
                    elif kind == "reordered":
                        value = dict(reversed(list(value.items())))
                    else:
                        value = list(value.items())
                    self.rejected(lambda: validate(value))
            self.assertEqual(factory(), original)

    def test_observation_version_mode_state_and_boolean_counters(self):
        for change in ({"schema": "tsj-cohort-terminal-observation-v1"},
                       {"mode": "NULL"}, {"state": "NOT_ENABLED"},
                       {"callbackCount": True}, {"foundCallbackCount": True},
                       {"nonFoundCallbackCount": True}, {"firstNetOrdinal": True},
                       {"lastNetOrdinal": True}, {"lastCallbackTotalExpanded": True},
                       {"selectedCallbackOrdinal": True}):
            with self.subTest(change=change):
                o = branch_observation()
                o.update(change)
                self.rejected(lambda: self.validate(o))
        for opportunities in (True, -1, 2147483648, 2):
            self.rejected(lambda: self.validate(branch_observation(), opportunities=opportunities))
        self.rejected(lambda: self.validate(branch_observation(), role="OTHER"))

    def test_callback_247_bound_and_numeric_accounting_extremes(self):
        o = found_observation()
        o.update(callbackCount=247, foundCallbackCount=247, validationCount=23218,
                 transientTupleCopies=247, transientFieldCopies=8151,
                 copiedEntries=8151, copyChunks=247, observerUnits=40261)
        c = context()
        c["outcome"] = {"disposition": "FOUND", "reason": "FOUND", "expanded": 25}
        c["terminal"] = None
        self.assertEqual(self.validate(o, c, opportunities=1000)["observerUnits"], 40261)
        o["callbackCount"] = 248
        self.rejected(lambda: self.validate(o, c, opportunities=1000), "OBSERVATION_ACCOUNTING")
        o = branch_observation()
        o.update(callbackCount=247, foundCallbackCount=246, selectedCallbackOrdinal=247,
                 validationCount=23230, transientTupleCopies=248, transientFieldCopies=8161,
                 copiedEntries=8161, copyChunks=248, observerUnits=40294)
        self.assertEqual(self.validate(o, opportunities=247)["observerUnits"], 40294)
        o["validationCount"] = 23231
        self.rejected(lambda: self.kernel.accounting(o), "VALIDATION_BOUND")

    def test_copy_unit_equations_and_validation_lower_bound_independently(self):
        self.assertEqual(self.kernel.accounting(branch_observation()), (109, 4, 428))
        o = branch_observation()
        o.update(validationCount=3, observerUnits=231)
        self.assertEqual(self.kernel.accounting(o), (109, 4, 231))
        for field in ("transientTupleCopies", "transientFieldCopies", "copyChunks",
                      "copiedEntries", "observerUnits", "validationCount"):
            for bad in (True, -1, 2147483648):
                with self.subTest(field=field, bad=bad):
                    o = branch_observation()
                    o[field] = bad
                    self.rejected(lambda: self.kernel.accounting(o))
            o = branch_observation()
            o[field] += 1
            self.rejected(lambda: self.kernel.accounting(o))
        o = branch_observation()
        o.update(validationCount=2, observerUnits=230)
        self.rejected(lambda: self.kernel.accounting(o), "VALIDATION_BOUND")
        o = empty_observation()
        o["selectedHead"] = head()
        self.rejected(lambda: self.kernel.accounting(o), "HEAD_CALLBACK_BOUND")

    def test_callback_finality_ordinal_and_terminal_binding(self):
        for change in ({"selectedCallbackOrdinal": 2}, {"nonFoundCallbackCount": 2,
                                                        "foundCallbackCount": 1},
                       {"selectedSnapshot": None}, {"lastNetOrdinal": 1},
                       {"firstNetOrdinal": 3}, {"lastNetOrdinal": 3},
                       {"lastCallbackTotalExpanded": 100006}):
            with self.subTest(change=change):
                o = branch_observation()
                o.update(change)
                self.rejected(lambda: self.validate(o))
        for mutate in (lambda c: c.update(terminal=None),
                       lambda c: c["terminal"].update(failedNetId="NET_A"),
                       lambda c: c["terminal"].update(ownerReason="NET_B:NO_PATH"),
                       lambda c: c["terminal"].update(searchPass=1),
                       lambda c: c["outcome"].update(reason="BRANCH_SEARCH_LIMIT"),
                       lambda c: c["outcome"].update(expanded=100008)):
            c = context()
            mutate(c)
            self.rejected(lambda: self.validate(branch_observation(), c))
        o, c = self.ordinary_failure("FOUND")
        self.rejected(lambda: self.validate(o, c), "OBSERVATION_FAILURE_BINDING")

    def test_no_head_without_selected_branch_and_required_with_branch(self):
        o = branch_observation()
        o.update(selectedHead=None, transientTupleCopies=3, transientFieldCopies=99,
                 copiedEntries=99, copyChunks=3, observerUnits=407)
        self.rejected(lambda: self.validate(o), "OBSERVATION_HEAD_PRESENCE")
        o, c = self.ordinary_failure()
        o.update(selectedHead=head(), transientTupleCopies=4, transientFieldCopies=109,
                 copiedEntries=109, copyChunks=4, observerUnits=240)
        self.rejected(lambda: self.validate(o, c), "OBSERVATION_HEAD_PRESENCE")
        o, c = found_observation(), context()
        c["outcome"] = {"disposition": "FOUND", "reason": "FOUND", "expanded": 25}
        c["terminal"] = None
        o.update(selectedHead=head(), transientTupleCopies=3, transientFieldCopies=76,
                 copiedEntries=76, copyChunks=3, observerUnits=341)
        self.rejected(lambda: self.validate(o, c), "OBSERVATION_HEAD_PRESENCE")
        o["state"] = "NON_BRANCH_REJECTION"
        c["outcome"].update(disposition="QUALITY_REJECTED", reason="QUALITY_LIMIT")
        self.rejected(lambda: self.validate(o, c), "OBSERVATION_HEAD_PRESENCE")
        o = empty_observation("NULL", "NOT_ENABLED")
        o["selectedHead"] = head()
        self.rejected(lambda: self.validate(o, role="NULL"), "NULL_OBSERVATION")

    def test_head_priority_wrap_both_extremes_and_literal_key(self):
        self.kernel.validate_head(head(), snapshot())
        h = head()
        h.update(cost=2147483647, storedHeuristic=2147483647, storedPriority=-2)
        self.kernel.validate_head(h, snapshot())
        h.update(cost=0, storedHeuristic=0, storedPriority=0)
        self.kernel.validate_head(h, snapshot())
        h.update(transitions=45, layer=0, direction=0, cell=199, key=90199)
        self.kernel.validate_head(h, snapshot())

    def test_head_negative_scalar_identity_cap_and_priority_binding(self):
        for change in ({"branchOrdinal": 0}, {"cell": 200}, {"cell": -1},
                       {"layer": 2}, {"direction": 5}, {"transitions": 46},
                       {"cost": -1}, {"storedHeuristic": -1}, {"serial": -1},
                       {"key": 5809}, {"storedPriority": 2147483648},
                       {"storedPriority": -2147483647}, {"cost": True}):
            with self.subTest(change=change):
                h = head()
                h.update(change)
                self.rejected(lambda: self.kernel.validate_head(h, snapshot()))
        for field in head():
            for value in (False, 2147483648, -2147483649):
                h = head()
                h[field] = value
                self.rejected(lambda: self.kernel.validate_head(h, snapshot()), "HEAD_INTS")
        for change in ({"totalExpanded": 1000000}, {"branchExpanded": 99999},
                       {"bestStateCount": 0}):
            s = snapshot()
            s.update(change)
            self.rejected(lambda: self.kernel.validate_head(head(), s), "HEAD_SNAPSHOT")

    def test_empty_ordinal_selection_and_quality_terminal_rejections(self):
        c = context()
        c["outcome"] = {"disposition": "QUALITY_REJECTED", "reason": "QUALITY_LIMIT", "expanded": 0}
        c["terminal"] = None
        for change in ({"firstNetOrdinal": 1}, {"lastNetOrdinal": 1},
                       {"lastCallbackTotalExpanded": 0}, {"selectedCallbackOrdinal": 1},
                       {"selectedSnapshot": snapshot()}, {"state": "NO_FAILURE"}):
            o = empty_observation()
            o.update(change)
            self.rejected(lambda: self.validate(o, c, opportunities=0))
        c["terminal"] = context()["terminal"]
        self.rejected(lambda: self.validate(empty_observation(), c), "OBSERVATION_QUALITY_TERMINAL")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--kernel-source", "--kernel", type=Path, default=KERNEL_SOURCE)
    options, unittest_args = parser.parse_known_args()
    KERNEL_SOURCE = options.kernel_source
    unittest.main(argv=[str(Path(__file__)), *unittest_args])
