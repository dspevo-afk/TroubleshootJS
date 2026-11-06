"""Literal tests for the pure P7 cut-sensitivity core.

Invoke with --oracle-directory pointing at the pinned P4 oracle directory. The
oracle files are hash-checked before either module is imported. Graph fixtures are
small in-memory literals; this suite does not load captured board data.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
from pathlib import Path
import stat
import sys
import unittest
from unittest import mock


GRAPH_ORACLE_SHA256 = "c146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00"
VIA_ORACLE_SHA256 = "924edc27078935e18da981117a09011989233afafff2345402c7bd5c1105d9b1"
MAX_GROUP_NANOS = 5_000_000_000
MAX_WORK_NANOS = 90_000_000_000
MAX_GROUPS = 640
MAX_BATCH_ENTRIES = 4096
MAX_CLONE_ENTRIES = 1_048_576
P07_CASES = ("CONTROL", "B_SENSE_CUT_ONLY", "B_REF_CUT_ONLY",
             "BOTH_OBSERVED_CUTS")
CASE_KEYS = {
    "case", "owners", "openedOrdinals", "openedEdges", "counterfactual",
    "actualCaptureAttested", "canonicalGraphSha256", "changedBitCount",
    "preserved", "bfs", "minimumVias", "agreement", "work",
}
PRESERVED_KEYS = {
    "dimensions", "protocol", "model", "caseId", "primary", "starts",
    "goals", "vias", "base",
}
BFS_KEYS = {
    "reachable", "reachedStates", "edgesExamined", "goalHits",
    "reachedMaskSha256", "fullCensus",
}
CASE_WORK_KEYS = {
    "buildEntries", "buildUnits", "bfsPops", "bfsUnits", "codecCalls",
    "oracleCalls", "elapsedNanos",
}
BUDGET_KEYS = {
    "groups", "elapsedNanos", "maximumGroupNanos", "copyEntries",
    "copyUnits", "bfsPops", "bfsUnits", "opaqueCalls",
    "totalObservationUnits", "actualNewP07Steps", "actualNewP07Advances",
    "actualNewP07Expansions",
}


def _no_reparse(path: Path) -> Path:
    absolute = Path(path.absolute())
    for item in (absolute, *absolute.parents):
        try:
            info = item.lstat()
        except OSError as exc:
            raise RuntimeError("oracle path unavailable") from exc
        if stat.S_ISLNK(info.st_mode) or bool(
                getattr(info, "st_file_attributes", 0) & 0x400):
            raise RuntimeError("oracle path contains a reparse point")
    return absolute


def _verified_bytes(path: Path, expected_sha256: str) -> bytes:
    path = _no_reparse(path)
    if not path.is_file():
        raise RuntimeError("oracle source is not a regular file")
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_sha256:
        raise RuntimeError("oracle source hash mismatch")
    return raw


def _load_hashed_module(name: str, path: Path, expected_sha256: str):
    # Verify source bytes before execution and bind the imported module to the
    # explicit oracle-directory path, preserving FixedPrefixGraph class identity.
    _verified_bytes(path, expected_sha256)
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError("oracle module loader unavailable")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    if Path(module.__file__).resolve(strict=True) != path.resolve(strict=True):
        raise RuntimeError("oracle module path mismatch")
    _verified_bytes(path, expected_sha256)
    return module


_cli = argparse.ArgumentParser(add_help=False)
_cli.add_argument("--oracle-directory", required=True)
_cli_args, _unittest_args = _cli.parse_known_args()
_ORACLE_DIRECTORY = _no_reparse(Path(_cli_args.oracle_directory))
if not _ORACLE_DIRECTORY.is_dir():
    raise RuntimeError("oracle directory is unavailable")

# Load the codec first under its canonical import name so the VIA oracle and core
# receive the exact same FixedPrefixGraph class object.
codec = _load_hashed_module(
    "read_fixed_prefix_graph",
    _ORACLE_DIRECTORY / "read_fixed_prefix_graph.py",
    GRAPH_ORACLE_SHA256,
)
via_oracle = _load_hashed_module(
    "fixed_prefix_minimum_vias",
    _ORACLE_DIRECTORY / "fixed_prefix_minimum_vias.py",
    VIA_ORACLE_SHA256,
)

_HERE_ARGUMENT = _no_reparse(Path(__file__).parent)
_HERE = _HERE_ARGUMENT.resolve(strict=True)
if _HERE != _HERE_ARGUMENT:
    raise RuntimeError("P7 test directory is not canonical")
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))
_core_path = _HERE / "cut_sensitivity_core.py"
_core_spec = importlib.util.spec_from_file_location("cut_sensitivity_core", _core_path)
if _core_spec is None or _core_spec.loader is None:
    raise RuntimeError("P7 pure-analysis module unavailable")
core = importlib.util.module_from_spec(_core_spec)
sys.modules["cut_sensitivity_core"] = core
_core_spec.loader.exec_module(core)
if Path(core.__file__).resolve(strict=True) != _core_path.resolve(strict=True):
    raise RuntimeError("P7 pure-analysis module path mismatch")


# Independent direction constants and a Floyd-Warshall closure: this oracle does
# not copy the implementation BFS's queue or traversal order.
_TINY_DIRECTIONS = ((1, 0, -1), (2, 1, 0), (4, 0, 1), (8, -1, 0))
_OWNER_ORDER = ("A", "B")
_OPENINGS = (
    # Complete disabled outgoing planar cut, in state then N/E/S/W order.
    (0, 0, 0, 3, 4, None, False),    # top start -> top cell 3, geometric
    (1, 0, 1, 2, 2, "A", True),      # owner A edge -> top goal
    (2, 0, 1, 4, 4, None, False),    # geometric edge remains closed
    (3, 1, 6, 7, 2, "B", True),      # owner B edge -> bottom goal
    (4, 1, 6, 9, 4, None, False),    # geometric edge remains closed
)
_START_GOAL_OPENINGS = (
    (0, 0, 0, 1, 2, "A", True),
    (1, 1, 2, 3, 2, "B", True),
)
_NO_PATH_OPENINGS = (
    (0, 0, 0, 1, 2, "A", True),
    (1, 1, 2, 3, 2, "B", True),
)


def _base_graph():
    # Two starts encode the actual two-face mask. Top states 0 and 1 form the
    # planar closure; bottom state 6 is the second start. Goals are distinct
    # captured per-face states. VIA bits are deliberately present but never affect
    # the independent planar closure.
    return codec.validate_payload({
        "protocol": "p07-fixed-prefix-graph-v1",
        "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
        "caseId": "LITERAL_OWNER_CUT",
        "width": 3,
        "height": 2,
        "primary": 0,
        "steps": [2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
        "vias": [True, True, True, True, True, True],
        "goals": [False, False, True, False, False, False,
                  False, True, False, False, False, False],
        "starts": [0, 6],
    })


def _graph_with_owners(base, openings, owners):
    steps = list(base.steps)
    for _ordinal, _layer, from_state, _to_state, bit, owner, selected in openings:
        if selected and owner in owners:
            steps[from_state] |= bit
    return codec.validate_payload({
        "protocol": base.protocol,
        "model": base.model,
        "caseId": base.case_id,
        "width": base.width,
        "height": base.height,
        "primary": base.primary,
        "steps": steps,
        "vias": list(base.vias),
        "goals": list(base.goals),
        "starts": list(base.starts),
    })


def _floyd_expectation(graph):
    states = graph.state_count
    reach = [[False] * states for _ in range(states)]
    for start in graph.starts:
        reach[start][start] = True
    for state, mask in enumerate(graph.steps):
        layer, cell = divmod(state, graph.cells)
        x, y = cell % graph.width, cell // graph.width
        for bit, dx, dy in _TINY_DIRECTIONS:
            if mask & bit:
                nx, ny = x + dx, y + dy
                if 0 <= nx < graph.width and 0 <= ny < graph.height:
                    target = layer * graph.cells + ny * graph.width + nx
                    reach[state][target] = True
    for pivot in range(states):
        for source in range(states):
            if reach[source][pivot]:
                row = reach[pivot]
                source_row = reach[source]
                for target in range(states):
                    source_row[target] = source_row[target] or row[target]

    reached = [
        any(reach[start][state] for start in graph.starts)
        for state in range(states)
    ]
    reached_count = sum(reached)
    edge_count = 0
    for state, mask in enumerate(graph.steps):
        if reached[state]:
            edge_count += sum(1 for bit, _dx, _dy in _TINY_DIRECTIONS if mask & bit)
    goal_hits = sum(1 for state, is_reached in enumerate(reached)
                    if is_reached and graph.goals[state])
    mask = bytes(1 if value else 0 for value in reached)
    return {
        "reachable": goal_hits > 0,
        "reachedStates": reached_count,
        "edgesExamined": edge_count,
        "goalHits": goal_hits,
        "reachedMaskSha256": hashlib.sha256(mask).hexdigest(),
        "fullCensus": True,
    }


def _opened_edges(openings, owners):
    result = []
    for ordinal, layer, from_state, to_state, bit, owner, selected in openings:
        if selected and owner in owners:
            result.append({
                "ordinal": ordinal, "layer": layer, "fromState": from_state,
                "toState": to_state, "bit": bit, "ownerNetId": owner,
            })
    return result


def _analyze(openings=_OPENINGS, owners=_OWNER_ORDER, base=None, budget=None):
    base = _base_graph() if base is None else base
    budget = core.WorkBudget() if budget is None else budget
    return core.analyze_graph_cases(base, openings, owners, codec, via_oracle, budget), budget


class LiteralCaseTests(unittest.TestCase):
    def test_four_exact_owner_subsets_match_independent_full_closure(self):
        base = _base_graph()
        frozen = (base.protocol, base.model, base.case_id, base.width, base.height,
                  base.primary, base.steps, base.vias, base.goals, base.starts,
                  base.graph_sha256)
        cases, budget = _analyze(base=base)
        summary = budget.finish()
        self.assertEqual([case["case"] for case in cases], list(P07_CASES))
        owner_sets = ((), ("A",), ("B",), ("A", "B"))
        changed_bits = (0, 1, 1, 2)
        expected_build = ((0, 0), (32, 4), (12, 1), (12, 1))
        expected_pop_counts = (3, 4, 4, 5)

        for case, owners, count, build, bfs_pops in zip(
                cases, owner_sets, changed_bits, expected_build,
                expected_pop_counts):
            self.assertEqual(set(case), CASE_KEYS)
            expected_graph = _graph_with_owners(base, _OPENINGS, owners)
            expected_bfs = _floyd_expectation(expected_graph)
            expected_edges = _opened_edges(_OPENINGS, owners)
            self.assertEqual(case["owners"], list(owners))
            self.assertEqual(case["openedEdges"], expected_edges)
            self.assertEqual(case["openedOrdinals"],
                             [row["ordinal"] for row in expected_edges])
            self.assertEqual(case["changedBitCount"], count)
            self.assertEqual(case["canonicalGraphSha256"],
                             expected_graph.graph_sha256)
            self.assertEqual(case["counterfactual"], case["case"] != "CONTROL")
            self.assertIs(case["actualCaptureAttested"], False)

            preserved = case["preserved"]
            self.assertEqual(set(preserved), PRESERVED_KEYS)
            self.assertTrue(all(value is True for value in preserved.values()))
            self.assertEqual(case["bfs"].keys(), BFS_KEYS)
            self.assertEqual(case["bfs"], expected_bfs)
            self.assertTrue(case["agreement"])
            self.assertEqual(
                case["minimumVias"],
                via_oracle.analyze_minimum_vias(expected_graph),
            )
            self.assertEqual(
                case["bfs"]["reachable"],
                case["minimumVias"]["minimumViaCount"] == 0,
            )
            self.assertEqual(set(case["work"]), CASE_WORK_KEYS)
            self.assertEqual(case["work"]["buildEntries"], build[0])
            self.assertEqual(case["work"]["buildUnits"], build[1])
            self.assertEqual(case["work"]["bfsPops"], bfs_pops)
            self.assertEqual(case["work"]["bfsUnits"], 1)
            self.assertEqual(case["work"]["codecCalls"], 0 if case["case"] == "CONTROL" else 1)
            self.assertEqual(case["work"]["oracleCalls"], 1)
            self.assertLessEqual(case["work"]["buildEntries"], MAX_CLONE_ENTRIES)
            self.assertLessEqual(case["work"]["bfsUnits"], MAX_GROUPS)
            self.assertLessEqual(case["work"]["elapsedNanos"], MAX_WORK_NANOS)

        self.assertFalse(cases[0]["bfs"]["reachable"])
        self.assertEqual(cases[0]["minimumVias"]["minimumViaCount"], 1)
        self.assertTrue(cases[1]["bfs"]["reachable"])
        self.assertEqual(cases[1]["openedOrdinals"], [1])
        self.assertTrue(cases[2]["bfs"]["reachable"])
        self.assertEqual(cases[2]["openedOrdinals"], [3])
        self.assertTrue(cases[3]["bfs"]["reachable"])
        self.assertEqual(cases[3]["openedOrdinals"], [1, 3])
        self.assertEqual(cases[3]["bfs"]["goalHits"], 2)
        self.assertEqual(cases[3]["bfs"]["reachedStates"], 5)
        self.assertEqual(summary["actualNewP07Steps"], 0)
        self.assertEqual(summary["actualNewP07Advances"], 0)
        self.assertEqual(summary["actualNewP07Expansions"], 0)
        self.assertEqual(set(summary), BUDGET_KEYS)
        self.assertEqual(summary["copyEntries"], 56)
        self.assertEqual(summary["copyUnits"], 6)
        self.assertEqual(summary["bfsPops"], 16)
        self.assertEqual(summary["bfsUnits"], 4)
        self.assertEqual(summary["opaqueCalls"], 11)
        self.assertGreaterEqual(
            summary["groups"],
            summary["copyUnits"] + summary["bfsUnits"] + summary["opaqueCalls"],
        )
        self.assertLessEqual(summary["groups"] + 1, MAX_GROUPS)
        self.assertEqual(summary["totalObservationUnits"], summary["groups"])
        self.assertTrue(all(summary[key] == 0 for key in
                            ("actualNewP07Steps", "actualNewP07Advances",
                             "actualNewP07Expansions")))
        after = (base.protocol, base.model, base.case_id, base.width, base.height,
                 base.primary, base.steps, base.vias, base.goals, base.starts,
                 base.graph_sha256)
        self.assertEqual(after, frozen)

    def test_complete_planar_closure_and_goal_at_start(self):
        base = codec.validate_payload({
            "protocol": "p07-fixed-prefix-graph-v1",
            "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
            "caseId": "LITERAL_GOAL_AT_START",
            "width": 2, "height": 1, "primary": 0,
            "steps": [0, 0, 0, 0], "vias": [False, False],
            "goals": [True, False, False, False], "starts": [0, 2],
        })
        cases, budget = _analyze(openings=_START_GOAL_OPENINGS, base=base)
        summary = budget.finish()
        for case, reached_count in zip(cases, (2, 3, 3, 4)):
            self.assertTrue(case["bfs"]["reachable"])
            self.assertEqual(case["bfs"]["reachedStates"], reached_count)
            self.assertEqual(case["bfs"]["goalHits"], 1)
            self.assertEqual(case["bfs"]["edgesExamined"], reached_count - 2)
            self.assertEqual(case["minimumVias"]["minimumViaCount"], 0)
            self.assertTrue(case["agreement"])
        self.assertEqual(cases[0]["openedEdges"], [])
        self.assertEqual(summary["actualNewP07Steps"], 0)

    def test_complete_no_path_after_opening_cut_edges(self):
        base = codec.validate_payload({
            "protocol": "p07-fixed-prefix-graph-v1",
            "model": "DIRECTED_TWO_FACE_HISTORY_RELAXED",
            "caseId": "LITERAL_NO_PATH",
            "width": 2, "height": 1, "primary": 0,
            "steps": [0, 0, 0, 0], "vias": [False, False],
            "goals": [False, False, False, False], "starts": [0, 2],
        })
        cases, budget = _analyze(openings=_NO_PATH_OPENINGS, base=base)
        budget.finish()
        for case, reached_count in zip(cases, (2, 3, 3, 4)):
            self.assertFalse(case["bfs"]["reachable"])
            self.assertEqual(case["bfs"]["reachedStates"], reached_count)
            self.assertEqual(case["bfs"]["goalHits"], 0)
            self.assertIsNone(case["minimumVias"]["minimumViaCount"])
            self.assertTrue(case["agreement"])


class OpeningValidationTests(unittest.TestCase):
    def assert_rejected(self, openings, owners=_OWNER_ORDER, base=None):
        with self.assertRaises(core.SensitivityError) as caught:
            _analyze(openings=openings, owners=owners,
                     base=_base_graph() if base is None else base)
        self.assertIs(type(caught.exception.code), str)
        self.assertTrue(caught.exception.code)

    def test_missing_cut_row_is_rejected(self):
        self.assert_rejected(_OPENINGS[:-1])

    def test_duplicate_query_ordinal_is_rejected(self):
        rows = list(_OPENINGS)
        row = rows[1]
        rows[1] = (rows[0][0],) + row[1:]
        self.assert_rejected(tuple(rows))

    def test_duplicate_edge_identity_is_rejected(self):
        rows = list(_OPENINGS)
        rows[2] = (rows[2][0],) + rows[1][1:]
        self.assert_rejected(tuple(rows))

    def test_out_of_bounds_or_nonadjacent_state_is_rejected(self):
        rows = list(_OPENINGS)
        rows[1] = (rows[1][0], 0, 1, -2, 1, "A", True)
        self.assert_rejected(tuple(rows))

    def test_already_enabled_base_bit_is_rejected(self):
        base = _base_graph()
        # An enabled edge cannot be in a legitimate outgoing cut. Exercise the
        # binding guard directly with a deliberately inconsistent literal cut.
        row = (0, 0, 0, 1, 2, "A", True)
        with self.assertRaises(core.SensitivityError) as caught:
            core._bind_openings_to_cut((row,), _OWNER_ORDER,
                                       ((0, 0, 1, 2),), base)
        self.assertEqual(caught.exception.code, "OPENING_EDGE_ALREADY_ENABLED")

    def test_unknown_selected_owner_is_rejected(self):
        rows = list(_OPENINGS)
        rows[1] = (rows[1][0],) + rows[1][1:5] + ("C", True)
        self.assert_rejected(tuple(rows))

    def test_boolean_does_not_satisfy_integer_ordinal(self):
        rows = list(_OPENINGS)
        rows[1] = (True,) + rows[1][1:]
        self.assert_rejected(tuple(rows))

    def test_reordered_cut_descriptors_are_rejected(self):
        self.assert_rejected(tuple(reversed(_OPENINGS)))

    def test_disabled_internal_edge_is_not_a_cut_descriptor(self):
        internal_rows = (
            (0, 0, 0, 3, 4, None, False),
            (1, 0, 1, 2, 2, "A", True),
            (2, 0, 1, 4, 4, None, False),
            (3, 0, 1, 0, 8, None, False),
            (4, 1, 6, 7, 2, "B", True),
        )
        rows = internal_rows
        self.assert_rejected(rows)

    def test_empty_or_duplicate_owner_order_is_rejected(self):
        self.assert_rejected(_OPENINGS, owners=())
        self.assert_rejected(_OPENINGS, owners=("A", "A"))

    def test_geometric_rows_are_present_but_never_opened(self):
        cases, budget = _analyze()
        budget.finish()
        geometric_ordinals = {0, 2, 4}
        for case in cases:
            self.assertTrue(geometric_ordinals.isdisjoint(case["openedOrdinals"]))
            self.assertTrue(all(row["ownerNetId"] in _OWNER_ORDER
                                for row in case["openedEdges"]))


class WorkBudgetTests(unittest.TestCase):
    def test_copy_and_bfs_tail_batches_count_actual_entries(self):
        budget = core.WorkBudget()
        copied = []
        queue = []

        def copy_tail():
            copied.extend((7, 8, 9))
            budget.record_entries("copy", len(copied))

        def bfs_tail():
            queue.extend((2, 5))
            budget.record_entries("bfs", len(queue))

        budget.call("copy", copy_tail, entries=MAX_BATCH_ENTRIES)
        budget.call("bfs", bfs_tail, entries=MAX_BATCH_ENTRIES)
        summary = budget.finish()
        self.assertEqual(summary["copyEntries"], 3)
        self.assertEqual(summary["copyUnits"], 1)
        self.assertEqual(summary["bfsPops"], 2)
        self.assertEqual(summary["bfsUnits"], 1)
        self.assertEqual(summary["totalObservationUnits"], summary["groups"])
        self.assertEqual(summary["actualNewP07Steps"], 0)
        self.assertEqual(summary["actualNewP07Advances"], 0)
        self.assertEqual(summary["actualNewP07Expansions"], 0)

    def test_copy_or_bfs_unit_cannot_declare_more_than_batch_cap(self):
        budget = core.WorkBudget()
        called = []
        for phase in ("copy", "bfs"):
            with self.subTest(phase=phase):
                with self.assertRaises(core.SensitivityError):
                    budget.call(phase, lambda: called.append(phase),
                                entries=MAX_BATCH_ENTRIES + 1)
        self.assertEqual(called, [])

    def test_actual_count_cannot_exceed_declared_bound(self):
        budget = core.WorkBudget()
        with self.assertRaises(core.SensitivityError):
            budget.call("copy", lambda: budget.record_entries("copy", 5), entries=4)

    def test_entry_accounting_is_once_and_inside_matching_unit(self):
        outside = core.WorkBudget()
        with self.assertRaises(core.SensitivityError):
            outside.record_entries("bfs", 1)

        duplicate = core.WorkBudget()

        def twice():
            duplicate.record_entries("bfs", 1)
            duplicate.record_entries("bfs", 1)

        with self.assertRaises(core.SensitivityError):
            duplicate.call("bfs", twice, entries=2)

    def test_opaque_calls_require_zero_entries(self):
        budget = core.WorkBudget()
        called = []
        with self.assertRaises(core.SensitivityError):
            budget.call("codec", lambda: called.append(True), entries=1)
        self.assertEqual(called, [])

    def test_opaque_group_over_five_seconds_fails_without_sleeping(self):
        now = [0]
        with mock.patch.object(core, "_clock_ns", side_effect=lambda: now[0]):
            budget = core.WorkBudget()

            def overlong_opaque_call():
                now[0] = MAX_GROUP_NANOS + 1

            with self.assertRaises(core.SensitivityError):
                budget.call("codec", overlong_opaque_call, entries=0)

    def test_group_and_total_work_caps_are_enforced(self):
        with self.subTest(cap="groups"):
            budget = core.WorkBudget()
            with self.assertRaises(core.SensitivityError):
                for _ in range(MAX_GROUPS + 1):
                    budget.call("codec", lambda: None, entries=0)
                budget.finish()

        with self.subTest(cap="total_work"):
            now = [0]
            with mock.patch.object(core, "_clock_ns", side_effect=lambda: now[0]):
                budget = core.WorkBudget()

                def measured_group():
                    now[0] += 4_900_000_000

                with self.assertRaises(core.SensitivityError):
                    for _ in range(19):
                        budget.call("codec", measured_group, entries=0)
                    budget.finish()


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *_unittest_args])
