"""Pure in-memory source adaptation for the private two-pad span probe.

The adapter deliberately operates on source strings only.  It does not read or
write files, import project code, or execute the Java sources.  Every edit is a
single exact replacement recorded in the returned change ledger so the caller
can apply, audit, and reverse the private copies without touching the
repository checkout.
"""


def _newline_style(source: str) -> str:
    if "\r\n" in source:
        return "\r\n"
    if "\r" in source:
        return "\r"
    return "\n"


def _with_newlines(text: str, newline: str) -> str:
    return text.replace("\n", newline)


def _replace_once(source: str, old: str, new: str, owner: str,
                  label: str, changes: list) -> str:
    count = source.count(old)
    if count != 1:
        raise ValueError("%s replacement count is %d, expected 1" % (label, count))
    changes.append({"owner": owner, "label": label, "old": old, "new": new})
    return source.replace(old, new, 1)


_SPAN_API = """    static java.util.TreeMap<String,Long> twoPadSpanBounds(
            TroubleshootBoard board, Vector<PcbFootprint> footprints) {
        if (board == null || footprints == null)
            throw new IllegalArgumentException("Missing two-pad span inputs");
        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        return twoPadSpanBounds(board, footprints, topology);
    }

    static boolean twoPadSpansFit(TroubleshootBoard board,
            Vector<PcbFootprint> footprints, java.util.Map<String,Long> caps) {
        if (caps == null) return true;
        if (board == null || footprints == null)
            throw new IllegalArgumentException("Missing two-pad span inputs");
        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        return twoPadSpansFit(board, footprints, caps, topology);
    }

    private static boolean twoPadSpansFit(TroubleshootBoard board,
            Vector<PcbFootprint> footprints, java.util.Map<String,Long> caps,
            TopologyPlacementGraph topology) {
        if (caps == null) return true;
        java.util.TreeMap<String,Long> spans = twoPadSpanBounds(board, footprints, topology);
        validateTwoPadSpanCaps(spans, caps);
        for (String netId : spans.keySet())
            if (spans.get(netId).longValue() > caps.get(netId).longValue()) return false;
        return true;
    }

    private static java.util.TreeMap<String,Long> twoPadSpanBounds(
            TroubleshootBoard board, Vector<PcbFootprint> footprints,
            TopologyPlacementGraph topology) {
        if (board == null || footprints == null || topology == null)
            throw new IllegalArgumentException("Missing two-pad span inputs");
        java.util.TreeMap<String,PcbFootprint> byComponent =
            new java.util.TreeMap<String,PcbFootprint>();
        java.util.TreeMap<String,PcbPadPlacement> byPad =
            new java.util.TreeMap<String,PcbPadPlacement>();
        for (PcbFootprint footprint : footprints) {
            if (footprint == null || footprint.getPlacement() == null)
                throw new IllegalArgumentException("Missing two-pad span footprint");
            String componentId = footprint.getPlacement().getComponentId();
            if (componentId == null || board.getComponent(componentId) == null ||
                    byComponent.put(componentId, footprint) != null)
                throw new IllegalArgumentException("Invalid two-pad span footprint: " + componentId);
            for (PcbPadPlacement pad : footprint.getPads()) {
                BoardPad boardPad = pad == null ? null : board.getPad(pad.getPadId());
                if (boardPad == null || !componentId.equals(boardPad.getComponentId()) ||
                        byPad.put(pad.getPadId(), pad) != null)
                    throw new IllegalArgumentException("Invalid two-pad span footprint pad");
            }
        }
        Vector<String> componentIds = board.getComponentIds();
        for (String componentId : componentIds)
            if (!byComponent.containsKey(componentId))
                throw new IllegalArgumentException("Missing two-pad span footprint: " + componentId);
        Vector<String> boardPadIds = board.getPadIds();
        java.util.Collections.sort(boardPadIds);
        for (String padId : boardPadIds)
            if (!byPad.containsKey(padId))
                throw new IllegalArgumentException("Missing two-pad span pad placement: " + padId);

        java.util.TreeMap<String,Long> result = new java.util.TreeMap<String,Long>();
        Vector<String> netIds = board.getNetIds();
        java.util.Collections.sort(netIds);
        for (String netId : netIds) {
            BoardNet net = board.getNet(netId);
            if (net == null) throw new IllegalArgumentException("Missing two-pad span net: " + netId);
            Vector<String> padIds = net.getPadIds();
            java.util.Collections.sort(padIds);
            if (padIds.size() != 2) continue;
            BoardPad first = board.getPad(padIds.get(0));
            BoardPad second = board.getPad(padIds.get(1));
            if (first == null || second == null || !netId.equals(first.getNetId()) ||
                    !netId.equals(second.getNetId()))
                throw new IllegalArgumentException("Missing two-pad span board pad: " + netId);
            PcbPadPlacement firstPlacement = placedTwoPad(first, byComponent.get(
                first.getComponentId()));
            PcbPadPlacement secondPlacement = placedTwoPad(second, byComponent.get(
                second.getComponentId()));
            if (!first.getComponentId().equals(second.getComponentId()) &&
                    !hasTwoPadLink(topology, first, second, netId))
                throw new IllegalArgumentException("Topology graph missing two-pad link: " + netId);
            long span = Math.abs((long) firstPlacement.getX() - secondPlacement.getX()) +
                Math.abs((long) firstPlacement.getY() - secondPlacement.getY());
            result.put(netId, Long.valueOf(span));
        }
        return result;
    }

    private static PcbPadPlacement placedTwoPad(BoardPad boardPad,
            PcbFootprint footprint) {
        if (boardPad == null || footprint == null)
            throw new IllegalArgumentException("Missing two-pad span pad placement");
        String componentId = boardPad.getComponentId();
        if (!componentId.equals(footprint.getPlacement().getComponentId()))
            throw new IllegalArgumentException("Two-pad span component mismatch: " +
                boardPad.getId());
        for (PcbPadPlacement pad : footprint.getPads())
            if (boardPad.getId().equals(pad.getPadId())) return pad;
        throw new IllegalArgumentException("Missing two-pad span pad placement: " +
            boardPad.getId());
    }

    private static boolean hasTwoPadLink(TopologyPlacementGraph topology,
            BoardPad first, BoardPad second, String netId) {
        for (TopologyPlacementGraph.PadLink link :
                topology.getLinksFor(first.getComponentId()))
            if (netId.equals(link.getNetId()) && first.getId().equals(link.getPadId()) &&
                    second.getComponentId().equals(link.getOtherComponentId()) &&
                    second.getId().equals(link.getOtherPadId())) return true;
        return false;
    }

    private static void validateTwoPadSpanCaps(
            java.util.TreeMap<String,Long> spans, java.util.Map<String,Long> caps) {
        if (spans == null || caps == null)
            throw new IllegalArgumentException("Missing two-pad span caps");
        if (caps.size() != spans.size())
            throw new IllegalArgumentException("Two-pad span cap inventory mismatch");
        for (String netId : spans.keySet()) {
            if (!caps.containsKey(netId))
                throw new IllegalArgumentException("Missing two-pad span cap: " + netId);
            Long cap = caps.get(netId);
            if (cap == null || cap.longValue() < 0L)
                throw new IllegalArgumentException("Invalid two-pad span cap: " + netId);
        }
        for (String netId : caps.keySet())
            if (netId == null || !spans.containsKey(netId))
                throw new IllegalArgumentException("Extra two-pad span cap: " + netId);
    }

"""


def adapt(planner: str, compactor: str) -> dict:
    """Return transformed private sources and an exact reversible change ledger."""
    if not isinstance(planner, str) or not isinstance(compactor, str):
        raise TypeError("planner and compactor must be source strings")

    changes = []
    planner_newline = _newline_style(planner)
    compactor_newline = _newline_style(compactor)

    planner_anchor = _with_newlines(
        "    static double totalWireDemand(Vector<PcbFootprint> placed,\n"
        "            TopologyPlacementGraph topology) {", planner_newline)
    planner = _replace_once(
        planner, planner_anchor,
        _with_newlines(_SPAN_API, planner_newline) + planner_anchor,
        "planner", "add_two_pad_span_api", changes)

    planner_header = _with_newlines(
        "    static Plan compactConnectedVacancies(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, Plan input) {", planner_newline)
    planner_header_new = _with_newlines(
        "    static Plan compactConnectedVacancies(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, Plan input) {\n"
        "        return compactConnectedVacancies(board, constraints, input, null);\n"
        "    }\n\n"
        "    static Plan compactConnectedVacancies(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, Plan input,\n"
        "            java.util.Map<String,Long> caps) {", planner_newline)
    planner = _replace_once(
        planner, planner_header, planner_header_new,
        "planner", "overload_connected_vacancies_with_caps", changes)

    planner_setup = _with_newlines(
        "        int evaluations = input.evaluations;\n"
        "        for (PcbPlacementConstraints.Part demand : constraints.getParts()) {",
        planner_newline)
    planner_setup_new = _with_newlines(
        "        int evaluations = input.evaluations;\n"
        "        if (caps != null) validateTwoPadSpanCaps(\n"
        "            twoPadSpanBounds(board, placed, topology), caps);\n"
        "        for (PcbPlacementConstraints.Part demand : constraints.getParts()) {",
        planner_newline)
    planner = _replace_once(
        planner, planner_setup, planner_setup_new,
        "planner", "validate_span_cap_inventory_before_trials", changes)

    planner_candidate = _with_newlines(
        "                candidate.set(index, trial);\n", planner_newline)
    planner_candidate_new = _with_newlines(
        "                candidate.set(index, trial);\n"
        "                if (caps != null && !twoPadSpansFit(board, candidate, caps, topology))\n"
        "                    continue;\n", planner_newline)
    planner = _replace_once(
        planner, planner_candidate, planner_candidate_new,
        "planner", "filter_vacancy_candidates_by_two_pad_spans", changes)

    compactor_header = _with_newlines(
        "    static PcbPlacementPlanner.Plan compact(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan input) {",
        compactor_newline)
    compactor_header_new = _with_newlines(
        "    static PcbPlacementPlanner.Plan compact(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan input) {\n"
        "        return compact(board, constraints, input, null);\n"
        "    }\n\n"
        "    static PcbPlacementPlanner.Plan compact(TroubleshootBoard board,\n"
        "            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan input,\n"
        "            java.util.Map<String,Long> caps) {",
        compactor_newline)
    compactor = _replace_once(
        compactor, compactor_header, compactor_header_new,
        "compactor", "overload_compactor_with_caps", changes)

    compactor_call = _with_newlines(
        "            result = PcbPlacementPlanner.compactConnectedVacancies(board, constraints, result);",
        compactor_newline)
    compactor_call_new = _with_newlines(
        "            result = PcbPlacementPlanner.compactConnectedVacancies(\n"
        "                board, constraints, result, caps);",
        compactor_newline)
    compactor = _replace_once(
        compactor, compactor_call, compactor_call_new,
        "compactor", "pass_caps_to_optional_vacancy_pass", changes)

    return {"planner": planner, "compactor": compactor, "changes": changes}
