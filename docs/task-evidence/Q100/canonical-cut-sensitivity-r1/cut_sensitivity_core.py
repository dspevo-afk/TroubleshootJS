"""Pure directed cut-sensitivity analysis over the pinned fixed-prefix graph."""

import hashlib
import time
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple


MAX_GROUP_NANOS = 5_000_000_000
MAX_WORK_NANOS = 90_000_000_000
MAX_GROUPS = 640
MAX_BATCH_ENTRIES = 4096
MAX_CLONE_ENTRIES = 1_048_576
MAX_OBSERVATION_UNITS = 32768

_DIRECTIONS = ((1, 0, -1), (2, 1, 0), (4, 0, 1), (8, -1, 0))
_CASE_SPECS = (
    ("CONTROL", ()),
    ("B_SENSE_CUT_ONLY", (0,)),
    ("B_REF_CUT_ONLY", (1,)),
    ("BOTH_OBSERVED_CUTS", (0, 1)),
)
_OPAQUE_PHASES = frozenset(("codec", "oracle", "decode", "hash", "serialization", "transport"))
_HEX = frozenset("0123456789abcdef")

# Kept as a module-level hook so bounded tests can supply a deterministic clock.
_clock_ns = time.perf_counter_ns


class SensitivityError(ValueError):
    """A sanitized analysis failure identified by one static code."""

    def __init__(self, code: str):
        if type(code) is not str or not code or any(
                not (char.isupper() or char.isdigit() or char == "_") for char in code):
            code = "ANALYSIS_INVALID"
        self.code = code
        super().__init__(code)


class WorkBudget:
    """Charge complete copy, BFS, codec, oracle, and preparation units."""

    def __init__(self):
        self._started = _clock_ns()
        if type(self._started) is not int:
            raise SensitivityError("BUDGET_CLOCK_INVALID")
        self._last_clock = self._started
        self._active = None
        self._groups = 0
        self._maximum_group_nanos = 0
        self._copy_entries = 0
        self._copy_units = 0
        self._bfs_pops = 0
        self._bfs_units = 0
        self._opaque_calls = 0
        self._violations = []

    def call(self, phase: str, action: Callable[[], Any], entries: int = 0) -> Any:
        if self._active is not None:
            self._violations.append("BUDGET_NESTED_CALL")
            raise SensitivityError("BUDGET_NESTED_CALL")
        if type(phase) is not str or not phase or not callable(action):
            raise SensitivityError("BUDGET_CALL_INVALID")
        if type(entries) is not int or entries < 0:
            raise SensitivityError("BUDGET_ENTRIES_INVALID")
        entry_phase = phase in ("copy", "bfs")
        if entry_phase:
            if entries < 1 or entries > MAX_BATCH_ENTRIES:
                raise SensitivityError("BUDGET_BATCH_INVALID")
        elif entries != 0:
            raise SensitivityError("BUDGET_OPAQUE_ENTRIES")
        started = self._read_clock()
        self._active = {
            "phase": phase,
            "declared": entries,
            "recorded": False,
            "actual": 0,
        }
        result = None
        failure = None
        try:
            result = action()
        except BaseException as error:
            failure = error
        ended = self._read_clock()

        active = self._active
        self._active = None
        duration = ended - started
        self._groups += 1
        if duration > self._maximum_group_nanos:
            self._maximum_group_nanos = duration
        if entry_phase:
            if phase == "copy":
                self._copy_units += 1
                if active["recorded"]:
                    self._copy_entries += active["actual"]
            else:
                self._bfs_units += 1
                if active["recorded"]:
                    self._bfs_pops += active["actual"]
        elif phase in _OPAQUE_PHASES:
            self._opaque_calls += 1

        if not active["recorded"] and entry_phase and failure is None:
            failure = SensitivityError("BUDGET_ENTRIES_UNRECORDED")
        if duration > MAX_GROUP_NANOS:
            self._violations.append("BUDGET_GROUP_LIMIT")
        if self._groups > MAX_GROUPS:
            self._violations.append("BUDGET_GROUP_COUNT")
        if self._elapsed_now() > MAX_WORK_NANOS:
            self._violations.append("BUDGET_WORK_LIMIT")
        if self._groups > MAX_OBSERVATION_UNITS:
            self._violations.append("BUDGET_OBSERVATION_UNITS")

        if self._violations:
            raise SensitivityError(self._violations[0]) from None
        if failure is not None:
            raise failure
        return result

    def record_entries(self, kind: str, count: int) -> None:
        active = self._active
        if (type(kind) is not str or kind not in ("copy", "bfs") or
                active is None or active["phase"] != kind or active["recorded"]):
            self._violations.append("BUDGET_ENTRY_RECORD_INVALID")
            raise SensitivityError("BUDGET_ENTRY_RECORD_INVALID")
        if (type(count) is not int or count < 1 or
                count > active["declared"] or count > MAX_BATCH_ENTRIES):
            self._violations.append("BUDGET_ENTRY_COUNT_INVALID")
            raise SensitivityError("BUDGET_ENTRY_COUNT_INVALID")
        active["recorded"] = True
        active["actual"] = count

    def finish(self) -> dict:
        if self._active is not None:
            raise SensitivityError("BUDGET_ACTIVE_CALL")
        elapsed = self._elapsed_now()
        if elapsed > MAX_WORK_NANOS:
            self._violations.append("BUDGET_WORK_LIMIT")
        if self._groups > MAX_GROUPS:
            self._violations.append("BUDGET_GROUP_COUNT")
        if self._maximum_group_nanos > MAX_GROUP_NANOS:
            self._violations.append("BUDGET_GROUP_LIMIT")
        if self._groups > MAX_OBSERVATION_UNITS:
            self._violations.append("BUDGET_OBSERVATION_UNITS")
        if self._violations:
            raise SensitivityError(self._violations[0]) from None
        return {
            "groups": self._groups,
            "elapsedNanos": elapsed,
            "maximumGroupNanos": self._maximum_group_nanos,
            "copyEntries": self._copy_entries,
            "copyUnits": self._copy_units,
            "bfsPops": self._bfs_pops,
            "bfsUnits": self._bfs_units,
            "opaqueCalls": self._opaque_calls,
            "totalObservationUnits": self._groups,
            "actualNewP07Steps": 0,
            "actualNewP07Advances": 0,
            "actualNewP07Expansions": 0,
        }

    def _elapsed_now(self) -> int:
        now = self._read_clock()
        if now < self._started:
            self._violations.append("BUDGET_CLOCK_INVALID")
            return 0
        return now - self._started

    def _read_clock(self) -> int:
        now = _clock_ns()
        if type(now) is not int or now < self._last_clock:
            self._violations.append("BUDGET_CLOCK_INVALID")
            return self._last_clock
        self._last_clock = now
        return now


def analyze_graph_cases(base: Any, openings: Tuple[tuple, ...], owner_order: Tuple[str, str],
                        codec: Any, via_oracle: Any, budget: WorkBudget) -> List[dict]:
    """Analyze CONTROL and three owner-selected directed-bit variants."""
    if not isinstance(budget, WorkBudget):
        raise SensitivityError("BUDGET_TYPE_INVALID")
    base_snapshot = budget.call(
        "validation", lambda: _validate_base(base, codec))
    normalized_openings, validated_owners = budget.call(
        "validation", lambda: _validate_opening_inputs(openings, owner_order))

    case_results = []
    control_started = _clock_ns()
    control_counters = _budget_counters(budget)
    reached_mask = None
    try:
        control_bfs, reached_mask = _bfs_full(base, budget)
        cut_edges = budget.call(
            "cut_validation", lambda: _derive_outcut(base, reached_mask))
        checked_openings, selected_by_owner = budget.call(
            "cut_validation", lambda: _bind_openings_to_cut(
                normalized_openings, validated_owners, cut_edges, base))
        control_oracle = _run_via_oracle(base, via_oracle, budget)
        control_minimum = _validated_minimum_count(control_oracle, base)
        control_agreement = control_bfs["reachable"] == (control_minimum == 0)
        if not control_agreement:
            raise SensitivityError("ORACLE_DISAGREEMENT")
        control_work = _case_work(budget, control_started, control_counters, 0, 1)
        case_results.append(_case_result(
            "CONTROL", [], [], False, base.graph_sha256, 0,
            _preserved_true(), control_bfs, control_oracle, control_agreement, control_work))
    finally:
        reached_mask = None

    case_results.extend(_analyze_counterfactuals(
        base, checked_openings, validated_owners, base_snapshot, codec, via_oracle, budget))
    budget.call("base_proof", lambda: _verify_base_unchanged(base, base_snapshot))
    if len(case_results) != len(_CASE_SPECS):
        raise SensitivityError("CASE_CENSUS_INCOMPLETE")
    budget.finish()
    return case_results


def _analyze_counterfactuals(base: Any, openings: Tuple[tuple, ...],
                             owners: Tuple[str, str], base_snapshot: Tuple[Any, ...],
                             codec: Any, via_oracle: Any,
                             budget: WorkBudget) -> List[dict]:
    results = []
    shared_payload = None
    try:
        for case_name, owner_indexes in _CASE_SPECS[1:]:
            case_started = _clock_ns()
            case_counters = _budget_counters(budget)
            opened_owners = [owners[index] for index in owner_indexes]
            opened_rows, open_masks = budget.call(
                "variant_selection", lambda: _select_case_openings(openings, opened_owners))
            payload = None
            steps = vias = goals = starts = None
            variant = None
            variant_mask = None
            try:
                if shared_payload is None:
                    steps, vias, goals, starts = _build_payload_arrays(base, open_masks, budget)
                    shared_payload = (vias, goals, starts)
                else:
                    steps = _build_steps(base, open_masks, budget)
                    vias, goals, starts = shared_payload
                payload = {
                    "protocol": base.protocol,
                    "model": base.model,
                    "caseId": base.case_id,
                    "width": base.width,
                    "height": base.height,
                    "primary": base.primary,
                    "steps": steps,
                    "vias": vias,
                    "goals": goals,
                    "starts": starts,
                }
                try:
                    variant = budget.call("codec", lambda: codec.validate_payload(payload))
                except SensitivityError:
                    raise
                except Exception:
                    raise SensitivityError("CODEC_REJECTED_VARIANT") from None
                changed_bits, preserved = budget.call(
                    "variant_proof", lambda: _prove_variant(
                        base, variant, open_masks, len(opened_rows), base_snapshot,
                        codec, shared_payload))
                payload = None
                steps = vias = goals = starts = None
                variant_bfs, variant_mask = _bfs_full(variant, budget)
                via_record = _run_via_oracle(variant, via_oracle, budget)
                minimum_count = _validated_minimum_count(via_record, variant)
                agreement = variant_bfs["reachable"] == (minimum_count == 0)
                if not agreement:
                    raise SensitivityError("ORACLE_DISAGREEMENT")
                case_work = _case_work(budget, case_started, case_counters, 1, 1)
                opened_ordinals = [row[0] for row in opened_rows]
                opened_edges = [_opened_edge_json(row) for row in opened_rows]
                results.append(_case_result(
                    case_name, opened_owners, opened_ordinals, True,
                    variant.graph_sha256, changed_bits, preserved, variant_bfs,
                    via_record, agreement, case_work, opened_edges))
            finally:
                payload = None
                steps = vias = goals = starts = None
                variant_mask = None
                variant = None
                open_masks = None
        return results
    finally:
        shared_payload = None


def _validate_base(base: Any, codec: Any) -> Tuple[Any, ...]:
    graph_type = getattr(codec, "FixedPrefixGraph", None)
    if graph_type is None or type(base) is not graph_type:
        raise SensitivityError("BASE_GRAPH_TYPE_INVALID")
    try:
        protocol = base.protocol
        model = base.model
        case_id = base.case_id
        width = base.width
        height = base.height
        primary = base.primary
        steps = base.steps
        vias = base.vias
        goals = base.goals
        starts = base.starts
        graph_sha256 = base.graph_sha256
    except Exception:
        raise SensitivityError("BASE_GRAPH_INVALID") from None

    max_cells = getattr(codec, "MAX_CELLS", None)
    if (type(protocol) is not str or protocol != getattr(codec, "GRAPH_PROTOCOL", None) or
            type(model) is not str or model != getattr(codec, "GRAPH_MODEL", None) or
            type(case_id) is not str or not 1 <= len(case_id) <= 128 or
            type(width) is not int or type(height) is not int or
            width < 1 or height < 1 or type(max_cells) is not int or
            width * height > max_cells or type(primary) is not int or primary not in (0, 1) or
            type(steps) is not tuple or type(vias) is not tuple or
            type(goals) is not tuple or type(starts) is not tuple or
            type(graph_sha256) is not str or len(graph_sha256) != 64 or
            any(char not in _HEX for char in graph_sha256)):
        raise SensitivityError("BASE_GRAPH_INVALID")
    cells = width * height
    state_count = cells * 2
    if (len(steps) != state_count or len(vias) != cells or len(goals) != state_count or
            len(starts) not in (1, 2) or
            state_count + cells + state_count + len(starts) > MAX_CLONE_ENTRIES):
        raise SensitivityError("BASE_GRAPH_SIZE_INVALID")
    for state, mask in enumerate(steps):
        if type(mask) is not int or mask < 0 or mask > 15:
            raise SensitivityError("BASE_GRAPH_STEPS_INVALID")
        cell = state % cells
        x, y = cell % width, cell // width
        if ((y == 0 and mask & 1) or (x == width - 1 and mask & 2) or
                (y == height - 1 and mask & 4) or (x == 0 and mask & 8)):
            raise SensitivityError("BASE_GRAPH_STEPS_INVALID")
    if any(type(value) is not bool for value in vias):
        raise SensitivityError("BASE_GRAPH_VIAS_INVALID")
    if any(type(value) is not bool for value in goals):
        raise SensitivityError("BASE_GRAPH_GOALS_INVALID")
    if any(type(state) is not int or state < 0 or state >= state_count for state in starts):
        raise SensitivityError("BASE_GRAPH_STARTS_INVALID")
    if len(set(starts)) != len(starts):
        raise SensitivityError("BASE_GRAPH_STARTS_INVALID")
    return (protocol, model, case_id, width, height, primary,
            steps, vias, goals, starts, graph_sha256)


def _validate_opening_inputs(openings: Any, owner_order: Any) -> Tuple[Tuple[tuple, ...], Tuple[str, str]]:
    if type(owner_order) is not tuple or len(owner_order) != 2:
        raise SensitivityError("OWNER_ORDER_INVALID")
    if any(type(owner) is not str or not owner for owner in owner_order):
        raise SensitivityError("OWNER_ORDER_INVALID")
    if owner_order[0] == owner_order[1]:
        raise SensitivityError("OWNER_ORDER_DUPLICATE")
    if type(openings) is not tuple or not openings:
        raise SensitivityError("OPENINGS_INVALID")
    normalized = []
    previous_ordinal = -1
    seen_ordinals = set()
    seen_edges = set()
    for row in openings:
        if type(row) is not tuple or len(row) != 7:
            raise SensitivityError("OPENING_ROW_INVALID")
        ordinal, layer, from_state, to_state, bit, owner, selected = row
        if (type(ordinal) is not int or ordinal < 0 or ordinal > 1_000_000 or
                ordinal in seen_ordinals or
                ordinal <= previous_ordinal):
            raise SensitivityError("OPENING_ORDINAL_INVALID")
        if (type(layer) is not int or layer not in (0, 1) or
                type(from_state) is not int or type(to_state) is not int or
                type(bit) is not int or bit not in (1, 2, 4, 8) or
                type(selected) is not bool):
            raise SensitivityError("OPENING_ROW_INVALID")
        if owner is not None and (type(owner) is not str or not owner):
            raise SensitivityError("OPENING_OWNER_INVALID")
        edge_identity = (layer, from_state, to_state, bit)
        if edge_identity in seen_edges:
            raise SensitivityError("OPENING_EDGE_DUPLICATE")
        if selected and owner not in owner_order:
            raise SensitivityError("OPENING_OWNER_UNKNOWN")
        if not selected and owner is None:
            pass
        seen_ordinals.add(ordinal)
        seen_edges.add(edge_identity)
        previous_ordinal = ordinal
        normalized.append(row)
    if not any(row[6] for row in normalized):
        raise SensitivityError("OPENING_OWNER_SET_EMPTY")
    for owner in owner_order:
        if not any(row[6] and row[5] == owner for row in normalized):
            raise SensitivityError("OPENING_OWNER_SET_EMPTY")
    return tuple(normalized), owner_order


def _bfs_full(graph: Any, budget: WorkBudget) -> Tuple[dict, bytearray]:
    cells = graph.width * graph.height
    state_count = cells * 2
    reached = None
    queue = []
    head = 0
    pops_total = 0
    edges_examined = 0
    goal_hits = 0

    while reached is None or head < len(queue):
        def batch() -> None:
            nonlocal reached, queue, head, pops_total, edges_examined, goal_hits
            if reached is None:
                reached = bytearray(state_count)
                queue = list(graph.starts)
                for start in queue:
                    reached[start] = 1
            batch_pops = 0
            try:
                while head < len(queue) and batch_pops < MAX_BATCH_ENTRIES:
                    state = queue[head]
                    head += 1
                    batch_pops += 1
                    pops_total += 1
                    if graph.goals[state]:
                        goal_hits += 1
                    layer = state // cells
                    cell = state % cells
                    x, y = cell % graph.width, cell // graph.width
                    mask = graph.steps[state]
                    for bit, dx, dy in _DIRECTIONS:
                        if not mask & bit:
                            continue
                        nx = x + dx
                        ny = y + dy
                        if nx < 0 or nx >= graph.width or ny < 0 or ny >= graph.height:
                            raise SensitivityError("BFS_EDGE_OUT_OF_BOUNDS")
                        edges_examined += 1
                        neighbor = layer * cells + ny * graph.width + nx
                        if not reached[neighbor]:
                            reached[neighbor] = 1
                            queue.append(neighbor)
            finally:
                if batch_pops:
                    budget.record_entries("bfs", batch_pops)

        budget.call("bfs", batch, entries=MAX_BATCH_ENTRIES)

    if reached is None:
        raise SensitivityError("BFS_CENSUS_EMPTY")
    digest = budget.call("hash", lambda: hashlib.sha256(reached).hexdigest())
    if type(digest) is not str or len(digest) != 64:
        raise SensitivityError("BFS_DIGEST_INVALID")
    result = {
        "reachable": goal_hits > 0,
        "reachedStates": pops_total,
        "edgesExamined": edges_examined,
        "goalHits": goal_hits,
        "reachedMaskSha256": digest,
        "fullCensus": True,
    }
    return result, reached


def _derive_outcut(base: Any, reached: bytearray) -> Tuple[Tuple[int, int, int, int], ...]:
    cells = base.width * base.height
    state_count = cells * 2
    if type(reached) is not bytearray or len(reached) != state_count:
        raise SensitivityError("CONTROL_CENSUS_INVALID")
    edges = []
    for state in range(state_count):
        if not reached[state]:
            continue
        layer = state // cells
        cell = state % cells
        x, y = cell % base.width, cell // base.width
        mask = base.steps[state]
        for bit, dx, dy in _DIRECTIONS:
            nx = x + dx
            ny = y + dy
            if nx < 0 or nx >= base.width or ny < 0 or ny >= base.height:
                continue
            neighbor = layer * cells + ny * base.width + nx
            enabled = bool(mask & bit)
            if enabled:
                if not reached[neighbor]:
                    raise SensitivityError("CONTROL_CENSUS_INCOMPLETE")
                continue
            if not reached[neighbor]:
                edges.append((layer, state, neighbor, bit))
    return tuple(edges)


def _bind_openings_to_cut(openings: Tuple[tuple, ...], owner_order: Tuple[str, str],
                          cut_edges: Tuple[Tuple[int, int, int, int], ...],
                          base: Any) -> Tuple[Tuple[tuple, ...], Dict[str, int]]:
    if len(openings) != len(cut_edges):
        raise SensitivityError("OPENING_CUT_CENSUS_MISMATCH")
    cells = base.width * base.height
    owner_counts = {owner: 0 for owner in owner_order}
    for row, edge in zip(openings, cut_edges):
        ordinal, layer, from_state, to_state, bit, owner, selected = row
        expected_layer, expected_from, expected_to, expected_bit = edge
        if ((layer, from_state, to_state, bit) != edge or
                layer != from_state // cells or to_state // cells != layer or
                not (0 <= from_state < len(base.steps)) or
                not (0 <= to_state < len(base.steps))):
            raise SensitivityError("OPENING_CUT_EDGE_MISMATCH")
        from_cell = from_state % cells
        to_cell = to_state % cells
        from_x, from_y = from_cell % base.width, from_cell // base.width
        to_x, to_y = to_cell % base.width, to_cell // base.width
        if (to_x - from_x, to_y - from_y) != next(
                (dx, dy) for direction, dx, dy in _DIRECTIONS if direction == bit):
            raise SensitivityError("OPENING_EDGE_ADJACENCY")
        if base.steps[from_state] & bit:
            raise SensitivityError("OPENING_EDGE_ALREADY_ENABLED")
        if selected:
            owner_counts[owner] += 1
    if len(set(edge for edge in cut_edges)) != len(cut_edges):
        raise SensitivityError("OPENING_EDGE_DUPLICATE")
    return openings, owner_counts


def _open_masks(opened_rows: Sequence[tuple]) -> Dict[int, int]:
    result = {}
    for row in opened_rows:
        state = row[2]
        bit = row[4]
        if result.get(state, 0) & bit:
            raise SensitivityError("OPENING_EDGE_DUPLICATE")
        result[state] = result.get(state, 0) | bit
    return result


def _select_case_openings(openings: Tuple[tuple, ...], owners: Sequence[str]):
    opened_rows = [row for row in openings if row[6] and row[5] in owners]
    if not opened_rows:
        raise SensitivityError("OPENING_OWNER_SET_EMPTY")
    return opened_rows, _open_masks(opened_rows)


def _build_payload_arrays(base: Any, open_masks: Dict[int, int], budget: WorkBudget):
    if _logical_clone_entries(base) > MAX_CLONE_ENTRIES:
        raise SensitivityError("GRAPH_CLONE_LIMIT")
    return (_copy_tuple_to_list(base.steps, budget, open_masks),
            _copy_tuple_to_list(base.vias, budget),
            _copy_tuple_to_list(base.goals, budget),
            _copy_tuple_to_list(base.starts, budget))


def _build_steps(base: Any, open_masks: Dict[int, int], budget: WorkBudget) -> list:
    if _logical_clone_entries(base) > MAX_CLONE_ENTRIES:
        raise SensitivityError("GRAPH_CLONE_LIMIT")
    return _copy_tuple_to_list(base.steps, budget, open_masks)


def _logical_clone_entries(base: Any) -> int:
    return len(base.steps) + len(base.vias) + len(base.goals) + len(base.starts)


def _copy_tuple_to_list(source: tuple, budget: WorkBudget,
                        open_masks: Optional[Dict[int, int]] = None) -> list:
    output = []
    for start in range(0, len(source), MAX_BATCH_ENTRIES):
        stop = min(len(source), start + MAX_BATCH_ENTRIES)
        count = stop - start

        def copy_batch(start=start, stop=stop, output=output, source=source,
                       open_masks=open_masks):
            before_count = len(output)
            try:
                if open_masks is None:
                    for index in range(start, stop):
                        output.append(source[index])
                else:
                    for index in range(start, stop):
                        output.append(source[index] | open_masks.get(index, 0))
            finally:
                actual = len(output) - before_count
                if actual:
                    budget.record_entries("copy", actual)

        budget.call("copy", copy_batch, entries=count)
    return output


def _prove_variant(base: Any, variant: Any, open_masks: Dict[int, int],
                   expected_changed: int, base_snapshot: Tuple[Any, ...],
                   codec: Any, shared_payload: Tuple[list, list, list]) -> Tuple[int, dict]:
    graph_type = getattr(codec, "FixedPrefixGraph", None)
    if graph_type is None or type(variant) is not graph_type:
        raise SensitivityError("CODEC_GRAPH_TYPE_INVALID")
    for field in ("protocol", "model", "case_id", "width", "height", "primary"):
        if getattr(variant, field, None) != getattr(base, field, None):
            raise SensitivityError("VARIANT_BASE_FIELD_CHANGED")
    for field in ("vias", "goals", "starts"):
        if getattr(variant, field, None) != getattr(base, field, None):
            raise SensitivityError("VARIANT_BASE_FIELD_CHANGED")
    if (type(variant.steps) is not tuple or len(variant.steps) != len(base.steps) or
            type(variant.graph_sha256) is not str or len(variant.graph_sha256) != 64 or
            any(char not in _HEX for char in variant.graph_sha256)):
        raise SensitivityError("VARIANT_GRAPH_INVALID")
    changed_bits = 0
    for state, original in enumerate(base.steps):
        expected_bits = open_masks.get(state, 0)
        actual = variant.steps[state]
        if type(actual) is not int or actual != (original | expected_bits):
            raise SensitivityError("VARIANT_STEP_DELTA_INVALID")
        difference = actual ^ original
        if difference != expected_bits:
            raise SensitivityError("VARIANT_STEP_DELTA_INVALID")
        while difference:
            changed_bits += 1
            difference &= difference - 1
    if changed_bits != expected_changed:
        raise SensitivityError("VARIANT_CHANGED_BIT_COUNT")
    if not _base_matches_snapshot(base, base_snapshot):
        raise SensitivityError("BASE_GRAPH_MUTATED")
    _verify_shared_payload(base, shared_payload)
    if changed_bits and variant.graph_sha256 == base.graph_sha256:
        raise SensitivityError("VARIANT_DIGEST_UNCHANGED")
    return changed_bits, _preserved_true()


def _verify_shared_payload(base: Any, shared_payload: Tuple[list, list, list]) -> None:
    if type(shared_payload) is not tuple or len(shared_payload) != 3:
        raise SensitivityError("SHARED_PAYLOAD_INVALID")
    for actual_values, expected_values in zip(
            shared_payload, (base.vias, base.goals, base.starts)):
        if type(actual_values) is not list or len(actual_values) != len(expected_values):
            raise SensitivityError("SHARED_PAYLOAD_MUTATED")
        for index, expected in enumerate(expected_values):
            actual = actual_values[index]
            if type(actual) is not type(expected) or actual != expected:
                raise SensitivityError("SHARED_PAYLOAD_MUTATED")


def _run_via_oracle(graph: Any, via_oracle: Any, budget: WorkBudget) -> dict:
    analyze = getattr(via_oracle, "analyze_minimum_vias", None)
    if not callable(analyze):
        raise SensitivityError("VIA_ORACLE_API_INVALID")
    try:
        result = budget.call("oracle", lambda: analyze(graph))
    except SensitivityError:
        raise
    except Exception:
        raise SensitivityError("VIA_ORACLE_REJECTED_GRAPH") from None
    if type(result) is not dict:
        raise SensitivityError("VIA_ORACLE_RESULT_INVALID")
    return result


def _validated_minimum_count(result: dict, graph: Any) -> Optional[int]:
    try:
        reachable = result["reachable"]
        minimum = result["minimumViaCount"]
        graph_sha = result["graphSha256"]
    except Exception:
        raise SensitivityError("VIA_ORACLE_RESULT_INVALID") from None
    if (type(reachable) is not bool or graph_sha != graph.graph_sha256 or
            (minimum is not None and (type(minimum) is not int or minimum < 0)) or
            (reachable != (minimum is not None))):
        raise SensitivityError("VIA_ORACLE_RESULT_INVALID")
    return minimum


def _budget_counters(budget: WorkBudget) -> Tuple[int, int, int, int]:
    return (budget._copy_entries, budget._copy_units,
            budget._bfs_pops, budget._bfs_units)


def _case_work(budget: WorkBudget, started: int, before: Tuple[int, int, int, int],
               codec_calls: int, oracle_calls: int) -> dict:
    elapsed = _clock_ns() - started
    if type(elapsed) is not int or elapsed < 0:
        raise SensitivityError("BUDGET_CLOCK_INVALID")
    return {
        "buildEntries": budget._copy_entries - before[0],
        "buildUnits": budget._copy_units - before[1],
        "bfsPops": budget._bfs_pops - before[2],
        "bfsUnits": budget._bfs_units - before[3],
        "codecCalls": codec_calls,
        "oracleCalls": oracle_calls,
        "elapsedNanos": elapsed,
    }


def _case_result(case: str, owners: List[str], opened_ordinals: List[int],
                 counterfactual: bool, graph_sha256: str, changed_bit_count: int,
                 preserved: dict, bfs: dict, minimum_vias: dict, agreement: bool,
                 work: dict, opened_edges: Optional[List[dict]] = None) -> dict:
    if opened_edges is None:
        opened_edges = []
    return {
        "case": case,
        "owners": list(owners),
        "openedOrdinals": list(opened_ordinals),
        "openedEdges": list(opened_edges),
        "counterfactual": counterfactual,
        "actualCaptureAttested": False,
        "canonicalGraphSha256": graph_sha256,
        "changedBitCount": changed_bit_count,
        "preserved": preserved,
        "bfs": bfs,
        "minimumVias": minimum_vias,
        "agreement": agreement,
        "work": work,
    }


def _opened_edge_json(row: tuple) -> dict:
    return {
        "ordinal": row[0],
        "layer": row[1],
        "fromState": row[2],
        "toState": row[3],
        "bit": row[4],
        "ownerNetId": row[5],
    }


def _preserved_true() -> dict:
    return {
        "dimensions": True,
        "protocol": True,
        "model": True,
        "caseId": True,
        "primary": True,
        "starts": True,
        "goals": True,
        "vias": True,
        "base": True,
    }


def _base_matches_snapshot(base: Any, snapshot: Tuple[Any, ...]) -> bool:
    if type(snapshot) is not tuple or len(snapshot) != 11:
        return False
    scalar_fields = (base.protocol, base.model, base.case_id, base.width,
                     base.height, base.primary)
    if scalar_fields != snapshot[:6]:
        return False
    return (base.steps is snapshot[6] and base.vias is snapshot[7] and
            base.goals is snapshot[8] and base.starts is snapshot[9] and
            base.graph_sha256 == snapshot[10])


def _verify_base_unchanged(base: Any, snapshot: Tuple[Any, ...]) -> None:
    if not _base_matches_snapshot(base, snapshot):
        raise SensitivityError("BASE_GRAPH_MUTATED")
