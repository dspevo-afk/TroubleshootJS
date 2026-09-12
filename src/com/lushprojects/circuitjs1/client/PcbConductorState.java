package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;

/** Sole current-copper owner. A metadata-only cut cannot masquerade as an electrical mutation. */
final class PcbConductorState {
    interface Projection {
        Prepared prepare(PcbConductorGraph.Snapshot before, PcbConductorGraph.Snapshot next);
    }
    interface Prepared {
        void apply();
        void verify(PcbConductorGraph.Snapshot expected);
        void rollback();
    }
    private final PcbConductorGraph graph;
    private final Projection projection;
    private PcbConductorGraph.Snapshot current;
    private boolean changing, quarantined;

    PcbConductorState(PcbConductorGraph graph, Projection projection) {
        if (graph == null) throw new IllegalArgumentException("Missing pristine copper");
        this.graph = graph; this.projection = projection; this.current = graph.pristine();
    }
    PcbConductorGraph getPristine() { return graph; }
    PcbConductorGraph.Snapshot getCurrent() {
        if (quarantined) throw new IllegalStateException("Copper state is quarantined");
        return current;
    }
    boolean isQuarantined() { return quarantined; }
    PcbConductorGraph.Snapshot previewCut(String id, boolean cut) { return getCurrent().withCut(id,cut); }
    void replace(PcbConductorGraph.Snapshot expected, PcbConductorGraph.Snapshot next) {
        if (changing || quarantined || expected != current || next == null || next.getGraph() != graph)
            throw new IllegalStateException("Stale, foreign or unavailable copper mutation owner");
        if (projection == null)
            throw new UnsupportedOperationException("No live solver copper-mutation adapter is installed");
        changing = true;
        Prepared prepared = null;
        try {
            // Preparation must be side-effect free. All electrical writes belong to apply/rollback.
            prepared = projection.prepare(expected,next);
            if (prepared == null) throw new IllegalStateException("Missing prepared copper projection");
            prepared.apply();
            prepared.verify(next);
            current = next;
        } catch (Throwable failure) {
            if (prepared != null) {
                try { prepared.rollback(); prepared.verify(expected); }
                catch (Throwable cleanup) {
                    quarantined = true;
                    throw new IllegalStateException("Copper projection rollback could not be verified",cleanup);
                }
            }
            throw new IllegalStateException("Copper projection rejected; previous state retained",failure);
        } finally {
            changing = false;
        }
    }
    static String encode(PcbConductorGraph.Snapshot snapshot) {
        StringBuilder out = new StringBuilder(prefix(snapshot.getGraph()));
        for (String id : snapshot.getCutEdgeIds()) out.append(id).append('\n');
        return out.toString();
    }
    static PcbConductorGraph.Snapshot decode(PcbConductorGraph graph, String text) {
        if (graph == null || text == null || text.length() > 4194304)
            throw new IllegalArgumentException("Invalid current copper encoding");
        String prefix = prefix(graph);
        if (!text.startsWith(prefix)) throw new IllegalArgumentException("Foreign or retired copper realization");
        String tail = text.substring(prefix.length());
        TreeSet<String> cuts = new TreeSet<String>();
        if (!tail.isEmpty()) {
            if (!tail.endsWith("\n")) throw new IllegalArgumentException("Incomplete copper state");
            for (String id : tail.substring(0,tail.length()-1).split("\n",-1))
                if (!cuts.add(PcbConductorGraph.requireId(id)))
                    throw new IllegalArgumentException("Duplicate copper cut");
        }
        PcbConductorGraph.Snapshot result = new PcbConductorGraph.Snapshot(graph,cuts);
        if (!encode(result).equals(text)) throw new IllegalArgumentException("Noncanonical copper state");
        return result;
    }
    private static String prefix(PcbConductorGraph graph) {
        return "P02-CUTS/1\n" + PcbConductorGraph.field(graph.toCanonical()) + "\n";
    }
}
