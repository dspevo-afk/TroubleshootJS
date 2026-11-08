"""Independent, profile-neutral observation V2 validation.

This source is embedded verbatim in standalone consumers. It owns no I/O,
producer imports, retained input references, or mutable accounting state.
"""

import types as _ov2_types


_ov2_MAX_INT = 2147483647
_ov2_MIN_INT = -2147483648
_ov2_OBSERVATION_KEYS = (
    "schema", "mode", "state", "callbackCount", "foundCallbackCount",
    "nonFoundCallbackCount", "firstNetOrdinal", "lastNetOrdinal",
    "lastCallbackTotalExpanded", "selectedCallbackOrdinal",
    "transientTupleCopies", "transientFieldCopies", "validationCount",
    "observerUnits", "copiedEntries", "copyChunks", "selectedSnapshot",
    "selectedHead",
)
_ov2_SNAPSHOT_KEYS = (
    "pass", "netId", "startPadId", "rootPadId", "startX", "startY",
    "rootX", "rootY", "gridWidth", "gridHeight", "goalsTop",
    "goalsBottom", "prefixViaCount", "reachedPadCount",
    "initialManhattanDistance", "minimumExploredManhattanDistance",
    "branchExpanded", "totalExpanded", "expandedTop", "expandedBottom",
    "frontierCount", "bestStateCount", "terminalReason",
    "relaxedBoundStarted", "relaxedBoundComplete",
    "relaxedBoundObservationChecked", "relaxedBoundLiveLayoutStable",
    "relaxedBoundRouterStateStable", "relaxedBoundRouterEffectsStable",
    "relaxedBoundWorkUnits", "relaxedBoundOrdinaryProbes",
    "relaxedBoundViaProbes", "relaxedBoundRefreshes",
)
_ov2_HEAD_KEYS = (
    "branchOrdinal", "cell", "layer", "direction", "transitions", "cost",
    "storedHeuristic", "serial", "key", "storedPriority",
)


class ObservationV2Error(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def _ov2_need(condition, code):
    if not condition:
        raise ObservationV2Error(code)


def _ov2_int(value, low=0, high=_ov2_MAX_INT):
    return type(value) is int and low <= value <= high


def _ov2_keys(value, expected, code):
    _ov2_need(type(value) is dict and tuple(value) == expected, code)


def _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid):
    # Shape admission precedes every state return and all membership/index use.
    _ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple)
              and all(type(net) is str and bool(net) for net in requested)
              and all(type(net) is str and bool(net) for net in traversed),
              "OBSERVATION_CONTEXT_ORDERING")
    _ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict,
              "OBSERVATION_CONTEXT_PADS")
    _ov2_need(all(type(pad) is str and bool(pad) and type(row) is tuple
                  and len(row) == 3 and type(row[0]) is str and bool(row[0])
                  and _ov2_int(row[1], _ov2_MIN_INT) and _ov2_int(row[2], _ov2_MIN_INT)
                  for pad, row in pads_by_id.items()), "OBSERVATION_CONTEXT_PAD_ROWS")
    _ov2_need(all(type(net) is str and bool(net) and type(members) is set
                  and all(type(pad) is str and bool(pad) for pad in members)
                  for net, members in pads_by_net.items()), "OBSERVATION_CONTEXT_MEMBERSHIP")
    _ov2_need(type(grid) is tuple and len(grid) == 2
              and _ov2_int(grid[0], 1, 250000) and _ov2_int(grid[1], 1, 250000)
              and grid[0] * grid[1] <= 250000, "PRIOR_GRID_CONTEXT")


class ObservationV2Kernel:
    @staticmethod
    def validate_snapshot(s, requested, traversed, pads_by_id, pads_by_net, grid):
        _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        _ov2_keys(s, _ov2_SNAPSHOT_KEYS, "SNAPSHOT_SCHEMA")
        net = s["netId"]
        _ov2_need(type(requested) in (list, tuple)
                  and type(traversed) in (list, tuple)
                  and type(net) is str and net in requested, "SNAPSHOT_NET")
        ordinal = requested.index(net) + 1
        _ov2_need(ordinal <= len(traversed) and traversed[ordinal - 1] == net,
                  "SNAPSHOT_TRAVERSAL_BINDING")
        start, root = s["startPadId"], s["rootPadId"]
        _ov2_need(type(start) is str and bool(start)
                  and type(root) is str and bool(root), "SNAPSHOT_PAD_IDENTITY")
        _ov2_need(all(_ov2_int(s[name], _ov2_MIN_INT) for name in
                      ("startX", "startY", "rootX", "rootY")),
                  "SNAPSHOT_PAD_COORDINATES")
        _ov2_need(type(pads_by_id) is dict and type(pads_by_net) is dict
                  and start in pads_by_id and root in pads_by_id
                  and start in pads_by_net.get(net, ())
                  and root in pads_by_net.get(net, ()), "SNAPSHOT_PAD_IDENTITY")
        start_row, root_row = pads_by_id[start], pads_by_id[root]
        _ov2_need(type(start_row) is tuple and len(start_row) == 3
                  and type(root_row) is tuple and len(root_row) == 3,
                  "SNAPSHOT_PAD_IDENTITY")
        _ov2_need(start_row[0] == root_row[0] == net
                  and all(_ov2_int(coordinate, _ov2_MIN_INT)
                          for coordinate in (start_row[1], start_row[2], root_row[1], root_row[2]))
                  and s["startX"] == start_row[1] and s["startY"] == start_row[2]
                  and s["rootX"] == root_row[1] and s["rootY"] == root_row[2],
                  "SNAPSHOT_PAD_COORDINATES")
        _ov2_need(_ov2_int(s["pass"], 2, 2), "SNAPSHOT_PASS")
        _ov2_need(_ov2_int(s["gridWidth"], 1, 250000)
                  and _ov2_int(s["gridHeight"], 1, 250000)
                  and s["gridWidth"] * s["gridHeight"] <= 250000, "SNAPSHOT_GRID")
        _ov2_need(type(grid) is tuple and len(grid) == 2
                  and _ov2_int(grid[0], 1, 250000)
                  and _ov2_int(grid[1], 1, 250000)
                  and grid[0] * grid[1] <= 250000, "PRIOR_GRID_CONTEXT")
        _ov2_need((s["gridWidth"], s["gridHeight"]) == grid, "SNAPSHOT_GRID_BINDING")
        cells = s["gridWidth"] * s["gridHeight"]
        _ov2_need(_ov2_int(s["goalsTop"], 0, cells)
                  and _ov2_int(s["goalsBottom"], 0, cells)
                  and s["goalsTop"] + s["goalsBottom"] > 0, "SNAPSHOT_GOALS")
        _ov2_need(_ov2_int(s["prefixViaCount"], 0, 48)
                  and _ov2_int(s["reachedPadCount"], 1, len(pads_by_net.get(net, ())))
                  and _ov2_int(s["initialManhattanDistance"]), "SNAPSHOT_COUNTS")
        branch, total = s["branchExpanded"], s["totalExpanded"]
        _ov2_need(_ov2_int(branch, 0, 100000) and _ov2_int(total, branch, 1000000)
                  and _ov2_int(s["expandedTop"], 0, branch)
                  and _ov2_int(s["expandedBottom"], 0, branch)
                  and s["expandedTop"] + s["expandedBottom"] == branch,
                  "SNAPSHOT_EXPANSIONS")
        minimum = s["minimumExploredManhattanDistance"]
        _ov2_need(_ov2_int(minimum, -1, -1) if branch == 0 else
                  _ov2_int(minimum, 0, s["initialManhattanDistance"]),
                  "SNAPSHOT_MINIMUM_DISTANCE")
        _ov2_need(_ov2_int(s["frontierCount"]) and _ov2_int(s["bestStateCount"]),
                  "SNAPSHOT_STATE_COUNTS")
        reason = s["terminalReason"]
        _ov2_need(type(reason) is str and reason in
                  ("NO_PATH", "BRANCH_SEARCH_LIMIT", "TOTAL_SEARCH_LIMIT", "GRID_LIMIT", "FOUND"),
                  "SNAPSHOT_TERMINAL_REASON")
        if reason == "NO_PATH":
            _ov2_need(s["frontierCount"] == 0, "SNAPSHOT_NO_PATH_FRONTIER")
        elif reason == "BRANCH_SEARCH_LIMIT":
            _ov2_need(branch == 100000, "SNAPSHOT_BRANCH_LIMIT")
        elif reason == "TOTAL_SEARCH_LIMIT":
            _ov2_need(total == 1000000, "SNAPSHOT_TOTAL_LIMIT")
        elif reason == "FOUND":
            _ov2_need(branch > 0, "SNAPSHOT_FOUND_EXPANSION")
        flags, work = _ov2_SNAPSHOT_KEYS[23:29], _ov2_SNAPSHOT_KEYS[29:33]
        _ov2_need(all(type(s[name]) is bool for name in flags), "SNAPSHOT_RELAXED_FLAGS")
        _ov2_need(all(_ov2_int(s[name]) for name in work), "SNAPSHOT_RELAXED_WORK")
        started, complete, checked = (s[name] for name in flags[:3])
        expected_checked = complete and s["prefixViaCount"] == 48
        _ov2_need((not started or complete) and checked is expected_checked
                  and all(s[name] is checked for name in flags[3:]),
                  "SNAPSHOT_RELAXED_STATE")
        if not started:
            _ov2_need(not complete and not checked and all(s[name] == 0 for name in work),
                      "SNAPSHOT_RELAXED_ZERO_WORK")
        return ordinal

    @staticmethod
    def validate_head(h, s):
        _ov2_keys(h, _ov2_HEAD_KEYS, "HEAD_KEYS")
        _ov2_need(all(_ov2_int(h[name], _ov2_MIN_INT) for name in _ov2_HEAD_KEYS),
                  "HEAD_INTS")
        _ov2_keys(s, _ov2_SNAPSHOT_KEYS, "SNAPSHOT_SCHEMA")
        _ov2_need(_ov2_int(s["gridWidth"], 1, 250000)
                  and _ov2_int(s["gridHeight"], 1, 250000)
                  and s["gridWidth"] * s["gridHeight"] <= 250000
                  and _ov2_int(s["prefixViaCount"], 0, 48), "HEAD_SNAPSHOT")
        cells = s["gridWidth"] * s["gridHeight"]
        _ov2_need(h["branchOrdinal"] >= 1 and 0 <= h["cell"] < cells
                  and 0 <= h["layer"] <= 1 and 0 <= h["direction"] <= 4
                  and 0 <= h["transitions"] <= 48 - s["prefixViaCount"]
                  and h["cost"] >= 0 and h["storedHeuristic"] >= 0 and h["serial"] >= 0,
                  "HEAD_DOMAIN")
        expected_key = (((h["transitions"] * 2 + h["layer"]) * 5 + h["direction"])
                        * cells) + h["cell"]
        priority = ((h["cost"] + h["storedHeuristic"] + 2147483648) % 4294967296) - 2147483648
        _ov2_need(h["key"] == expected_key and h["storedPriority"] == priority,
                  "HEAD_EQUATIONS")
        _ov2_need(_ov2_int(s["branchExpanded"], 100000, 100000)
                  and _ov2_int(s["totalExpanded"], 100000, 999999)
                  and _ov2_int(s["bestStateCount"], 1), "HEAD_SNAPSHOT")

    @staticmethod
    def accounting(o):
        _ov2_keys(o, _ov2_OBSERVATION_KEYS, "OBSERVATION_SCHEMA")
        d = o["callbackCount"]
        _ov2_need(_ov2_int(d, 0, 247), "CALLBACK_BOUND")
        h = int(o["selectedHead"] is not None)
        _ov2_need(h <= d, "HEAD_CALLBACK_BOUND")
        v = o["validationCount"]
        _ov2_need(_ov2_int(v, d, 94 * d + 12 * h), "VALIDATION_BOUND")
        entries, chunks, units = 33 * d + 10 * h, d + h, 69 * d + v + 21 * h
        _ov2_need(_ov2_int(o["transientTupleCopies"], chunks, chunks)
                  and _ov2_int(o["copyChunks"], chunks, chunks)
                  and _ov2_int(o["transientFieldCopies"], entries, entries)
                  and _ov2_int(o["copiedEntries"], entries, entries)
                  and _ov2_int(o["observerUnits"], units, units)
                  and units <= 163 * d + 33 * h, "OBSERVER_ACCOUNTING")
        return entries, chunks, units

    @staticmethod
    def validate_observation(o, role, opportunities, requested, traversed,
                             pads_by_id, pads_by_net, grid, outcome, terminal):
        _ov2_context(requested, traversed, pads_by_id, pads_by_net, grid)
        _ov2_keys(o, _ov2_OBSERVATION_KEYS, "OBSERVATION_SCHEMA")
        _ov2_need(o["schema"] == "tsj-cohort-terminal-observation-v2", "OBSERVATION_IDENTITY")
        _ov2_need(_ov2_int(opportunities), "CALLBACK_OPPORTUNITIES")
        if role == "NULL":
            _ov2_need(o["mode"] == "NULL" and o["state"] == "NOT_ENABLED"
                      and all(_ov2_int(o[name], 0, 0) for name in (
                          "callbackCount", "foundCallbackCount", "nonFoundCallbackCount",
                          "transientTupleCopies", "transientFieldCopies", "validationCount",
                          "observerUnits", "copiedEntries", "copyChunks"))
                      and all(o[name] is None for name in (
                          "firstNetOrdinal", "lastNetOrdinal", "lastCallbackTotalExpanded",
                          "selectedCallbackOrdinal", "selectedSnapshot", "selectedHead")),
                      "NULL_OBSERVATION")
            return _ov2_types.MappingProxyType({
                "observerUnits": 0, "copiedEntries": 0, "copyChunks": 0, "state": "NOT_ENABLED"})
        _ov2_need(role == "OBSERVE" and o["mode"] == "OBSERVE"
                  and o["state"] in ("NO_FAILURE", "FIRST_NON_FOUND", "NON_BRANCH_REJECTION"),
                  "OBSERVATION_STATE")
        d, found, nonfound = (o[name] for name in
                              ("callbackCount", "foundCallbackCount", "nonFoundCallbackCount"))
        _ov2_need(_ov2_int(d, 0, min(247, opportunities))
                  and _ov2_int(found, 0, d) and _ov2_int(nonfound, 0, d)
                  and found + nonfound == d, "OBSERVATION_ACCOUNTING")
        entries, chunks, units = ObservationV2Kernel.accounting(o)
        _ov2_need(type(requested) in (list, tuple) and type(traversed) in (list, tuple),
                  "OBSERVATION_ORDINALS")
        _ov2_need(type(outcome) is dict
                  and type(outcome.get("disposition")) is str
                  and type(outcome.get("reason")) is str
                  and _ov2_int(outcome.get("expanded"), 0, 1000000), "OBSERVATION_OUTCOME")
        first, last, last_total = (o[name] for name in
                                   ("firstNetOrdinal", "lastNetOrdinal", "lastCallbackTotalExpanded"))
        if d == 0:
            _ov2_need(first is None and last is None and last_total is None,
                      "OBSERVATION_EMPTY_ORDINALS")
        else:
            _ov2_need(_ov2_int(first, 1, len(requested))
                      and _ov2_int(last, first, len(requested)) and last <= len(traversed)
                      and _ov2_int(last_total, 0, outcome["expanded"]), "OBSERVATION_ORDINALS")
        selected, selected_ordinal, head = (o[name] for name in
                                            ("selectedSnapshot", "selectedCallbackOrdinal", "selectedHead"))
        disposition, reason, state = outcome["disposition"], outcome["reason"], o["state"]
        if state == "NO_FAILURE":
            _ov2_need(disposition == "FOUND" and nonfound == 0 and found == d
                      and selected is None and selected_ordinal is None and terminal is None,
                      "OBSERVATION_SUCCESS_STATE")
        elif state == "FIRST_NON_FOUND":
            _ov2_need(disposition in ("NO_PATH", "CAP_REJECTED", "QUALITY_REJECTED")
                      and d >= 1 and nonfound == 1 and found + 1 == d
                      and _ov2_int(selected_ordinal, d, d)
                      and type(selected) is dict and type(terminal) is dict,
                      "OBSERVATION_FAILURE_STATE")
            net_ordinal = ObservationV2Kernel.validate_snapshot(
                selected, requested, traversed, pads_by_id, pads_by_net, grid)
            _ov2_need(net_ordinal == last and selected["totalExpanded"] == last_total
                      == outcome["expanded"]
                      and selected["netId"] == terminal.get("failedNetId")
                      and _ov2_int(terminal.get("searchPass"), 2, 2)
                      and selected["terminalReason"] != "FOUND"
                      and selected["terminalReason"] == reason.rsplit(":", 1)[-1]
                      and reason == selected["netId"] + ":" + selected["terminalReason"]
                      and terminal.get("ownerReason") == reason, "OBSERVATION_FAILURE_BINDING")
        else:
            _ov2_need(disposition == "QUALITY_REJECTED" and nonfound == 0
                      and found == d and selected is None and selected_ordinal is None
                      and (reason == "QUALITY_LIMIT" or reason.endswith(":BRANCH_QUALITY_LIMIT")),
                      "OBSERVATION_QUALITY_STATE")
            if reason == "QUALITY_LIMIT":
                _ov2_need(terminal is None, "OBSERVATION_QUALITY_TERMINAL")
            else:
                failed = reason.split(":", 1)[0]
                _ov2_need(d > 0 and type(terminal) is dict
                          and terminal.get("failedNetId") == failed
                          and terminal.get("ownerReason") == reason
                          and requested[last - 1] == failed and last == len(traversed)
                          and last_total == outcome["expanded"], "OBSERVATION_SCOPED_QUALITY_BINDING")
        has_branch_head = (state == "FIRST_NON_FOUND"
                           and selected["terminalReason"] == "BRANCH_SEARCH_LIMIT")
        _ov2_need((head is not None) is has_branch_head, "OBSERVATION_HEAD_PRESENCE")
        if has_branch_head:
            ObservationV2Kernel.validate_head(head, selected)
        return _ov2_types.MappingProxyType({
            "observerUnits": units, "copiedEntries": entries, "copyChunks": chunks, "state": state})
