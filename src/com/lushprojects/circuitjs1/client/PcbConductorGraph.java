package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable physical copper. Net labels validate connections, never create them. */
final class PcbConductorGraph {
    enum Kind { TRACE, CONTACT, PLATED_BARREL, VIA, PACKAGE_INTERNAL }

    static String requireId(String id) {
        if (id == null || id.length() == 0 || id.length() > 4096)
            throw new IllegalArgumentException("Invalid copper identity");
        for (int i = 0; i < id.length(); i++)
            if (id.charAt(i) < 32 || id.charAt(i) == 127)
                throw new IllegalArgumentException("Control character in copper identity");
        return id;
    }

    static String field(String text) {
        return text == null ? "-1:" : text.length() + ":" + text;
    }

    static final class Junction {
        final String id, netId;
        final PcbCopperLayer layer;
        final int x, y;
        Junction(String id, String net, PcbCopperLayer layer, int x, int y) {
            this.id = requireId(id); this.netId = requireId(net);
            if (layer == null) throw new IllegalArgumentException("Missing junction layer");
            PcbCoordinateSystem.requireBoardCoordinate(x);
            PcbCoordinateSystem.requireBoardCoordinate(y);
            this.layer = layer; this.x = x; this.y = y;
        }
    }
    static final class Edge {
        final String id, first, second, netId;
        final Kind kind;
        private final List<String> sources;
        Edge(String id, String first, String second, String net, Kind kind, Iterable<String> provenance) {
            this.id = requireId(id); this.first = requireId(first);
            this.second = requireId(second); this.netId = requireId(net);
            if (first.equals(second) || kind == null || provenance == null)
                throw new IllegalArgumentException("Invalid conductor edge");
            this.kind = kind;
            TreeSet<String> copy = new TreeSet<String>();
            for (String source : provenance) copy.add(requireId(source));
            if (copy.isEmpty()) throw new IllegalArgumentException("Missing copper provenance");
            sources = Collections.unmodifiableList(new ArrayList<String>(copy));
        }
        List<String> getSources() { return sources; }
    }

    static final class Surface {
        final String id, junctionId, edgeId, padId;
        final PcbCopperLayer layer;
        final PcbCopperAccess.Exposure exposure;
        private final Rectangle bounds;
        Surface(String id, String junction, String edge, String pad, PcbCopperLayer layer,
                PcbCopperAccess.Exposure exposure, Rectangle bounds) {
            this.id = requireId(id); this.junctionId = requireId(junction);
            if (edge != null) requireId(edge);
            if (pad != null) requireId(pad);
            if (layer == null || exposure == null || bounds == null ||
                    bounds.width <= 0 || bounds.height <= 0)
                throw new IllegalArgumentException("Invalid copper surface");
            PcbCoordinateSystem.requireBoardRectangle(bounds);
            this.edgeId = edge; this.padId = pad; this.layer = layer;
            this.exposure = exposure; this.bounds = new Rectangle(bounds);
        }
        Rectangle getBounds() { return new Rectangle(bounds); }
        boolean canProbe(PcbBoardSide face) { return PcbCopperAccess.canProbe(layer, exposure, face); }
    }

    private final Map<String, Junction> junctions;
    private final Map<String, Edge> edges;
    private final List<Surface> surfaces;
    private final Map<String, String> padFaces;
    private final Map<String, String> terminals;
    private final String canonical;

    PcbConductorGraph(List<Junction> junctions, List<Edge> edges, List<Surface> surfaces,
            Map<String,String> padFaces, Map<String,String> terminals, String holeRecord) {
        TreeMap<String,Junction> js = new TreeMap<String,Junction>();
        for (Junction j : junctions)
            if (j == null || js.put(j.id, j) != null)
                throw new IllegalArgumentException("Duplicate conductor junction");
        TreeMap<String,Edge> es = new TreeMap<String,Edge>();
        for (Edge e : edges) {
            if (e == null || es.put(e.id, e) != null)
                throw new IllegalArgumentException("Duplicate conductor edge");
            Junction a = js.get(e.first), b = js.get(e.second);
            if (a == null || b == null || !a.netId.equals(e.netId) || !b.netId.equals(e.netId))
                throw new IllegalArgumentException("Conductor endpoint/net mismatch");
            boolean barrel = e.kind == Kind.VIA || e.kind == Kind.PLATED_BARREL;
            if (barrel && (a.layer == b.layer || a.x != b.x || a.y != b.y))
                throw new IllegalArgumentException("Invalid plated layer endpoints");
            if (!barrel && e.kind != Kind.PACKAGE_INTERNAL && a.layer != b.layer)
                throw new IllegalArgumentException("Copper cannot bridge unrelated layers");
        }
        ArrayList<Surface> ss = new ArrayList<Surface>();
        HashSet<String> ids = new HashSet<String>();
        for (Surface s : surfaces) {
            if (s == null || !ids.add(s.id) || !js.containsKey(s.junctionId) ||
                    js.get(s.junctionId).layer != s.layer ||
                    (s.edgeId != null && !es.containsKey(s.edgeId)))
                throw new IllegalArgumentException("Invalid surface correspondence");
            if (s.edgeId != null) {
                Edge edge = es.get(s.edgeId);
                Junction a = js.get(edge.first), b = js.get(edge.second);
                if (edge.kind != Kind.TRACE || s.padId != null ||
                        (!edge.first.equals(s.junctionId) && !edge.second.equals(s.junctionId)) ||
                        !s.bounds.equals(PcbConductorBuilder.stroke(a.x,a.y,b.x,b.y)))
                    throw new IllegalArgumentException("Surface does not match its physical copper edge");
            }
            if (s.padId != null && !s.junctionId.equals(padFaces.get(faceKey(s.padId,s.layer))))
                throw new IllegalArgumentException("Surface does not match its physical pad face");
            ss.add(s);
        }
        Collections.sort(ss,new java.util.Comparator<Surface>() {
            public int compare(Surface a, Surface b) { return a.id.compareTo(b.id); }
        });
        this.junctions = Collections.unmodifiableMap(js);
        this.edges = Collections.unmodifiableMap(es);
        this.surfaces = Collections.unmodifiableList(ss);
        this.padFaces = freezeReferences(padFaces, js);
        this.terminals = freezeReferences(terminals, js);
        StringBuilder out = new StringBuilder("P02-COPPER/1\n");
        for (Junction j : js.values()) out.append("J").append(field(j.id)).append(field(j.netId))
            .append(j.layer).append(':').append(j.x).append(',').append(j.y).append('\n');
        for (Edge e : es.values()) {
            out.append("E").append(field(e.id)).append(field(e.first)).append(field(e.second))
                .append(field(e.netId)).append(e.kind).append(':');
            for (String source : e.sources) out.append(field(source));
            out.append('\n');
        }
        for (Surface s : ss) {
            Rectangle r = s.bounds;
            out.append("S").append(field(s.id)).append(field(s.junctionId)).append(field(s.edgeId))
                .append(field(s.padId)).append(s.layer).append(':').append(s.exposure).append(':')
                .append(r.x).append(',').append(r.y).append(',').append(r.width).append(',')
                .append(r.height).append('\n');
        }
        appendReferences(out, "P", this.padFaces);
        appendReferences(out, "T", this.terminals);
        canonical = out.append("H").append(field(holeRecord)).toString();
    }
    private static void appendReferences(StringBuilder out, String prefix, Map<String,String> values) {
        for (Map.Entry<String,String> e : values.entrySet())
            out.append(prefix).append(field(e.getKey())).append(field(e.getValue())).append('\n');
    }
    private static Map<String,String> freezeReferences(Map<String,String> input,
            Map<String,Junction> junctions) {
        TreeMap<String,String> result = new TreeMap<String,String>();
        for (Map.Entry<String,String> item : input.entrySet()) {
            requireId(item.getKey());
            if (!junctions.containsKey(item.getValue()))
                throw new IllegalArgumentException("Unknown pad conductor junction");
            result.put(item.getKey(), item.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
    static String faceKey(String padId, PcbCopperLayer layer) {
        if (layer == null) throw new IllegalArgumentException("Missing copper layer");
        return field(requireId(padId)) + layer;
    }
    Map<String,Junction> getJunctions() { return junctions; }
    Map<String,Edge> getEdges() { return edges; }
    List<Surface> getSurfaces() { return surfaces; }
    Map<String,String> getTerminalJunctions() { return terminals; }
    String getPadJunction(String pad, PcbCopperLayer layer) { return padFaces.get(faceKey(pad, layer)); }
    String toCanonical() { return canonical; }
    Snapshot pristine() { return new Snapshot(this, Collections.<String>emptySet()); }

    /** Immutable connectivity. Island numbers are scratch, not saved or exposed identities. */
    static final class Snapshot {
        private final PcbConductorGraph graph;
        private final Set<String> cuts;
        private final Map<String,Integer> islands = new HashMap<String,Integer>();
        Snapshot(PcbConductorGraph graph, Set<String> cutIds) {
            this.graph = graph;
            TreeSet<String> copy = new TreeSet<String>();
            for (String id : cutIds) {
                Edge edge = graph.edges.get(id);
                if (edge == null || edge.kind != Kind.TRACE)
                    throw new IllegalArgumentException("Unknown or non-cuttable copper edge: " + id);
                copy.add(id);
            }
            cuts = Collections.unmodifiableSet(copy);
            Map<String,List<String>> adjacent = new HashMap<String,List<String>>();
            for (String id : graph.junctions.keySet()) adjacent.put(id, new ArrayList<String>());
            for (Edge edge : graph.edges.values()) if (!cuts.contains(edge.id)) {
                adjacent.get(edge.first).add(edge.second);
                adjacent.get(edge.second).add(edge.first);
            }
            int island = 0;
            for (String id : graph.junctions.keySet()) {
                if (islands.containsKey(id)) continue;
                ArrayList<String> queue = new ArrayList<String>(); queue.add(id);
                islands.put(id, Integer.valueOf(island++));
                for (int i=0; i<queue.size(); i++) for (String next : adjacent.get(queue.get(i))) {
                    if (!islands.containsKey(next)) {
                        islands.put(next, islands.get(id)); queue.add(next);
                    }
                }
            }
        }
        PcbConductorGraph getGraph() { return graph; }
        Set<String> getCutEdgeIds() { return cuts; }
        boolean hasEdge(String id) {
            if (!graph.edges.containsKey(id)) throw new IllegalArgumentException("Unknown copper edge");
            return !cuts.contains(id);
        }
        boolean connected(String first, String second) {
            if (!islands.containsKey(first) || !islands.containsKey(second))
                throw new IllegalArgumentException("Unknown copper junction");
            return islands.get(first).equals(islands.get(second));
        }
        boolean padsConnected(String first, String second) {
            return connected(graph.terminals.get(first), graph.terminals.get(second));
        }
        Snapshot withCut(String id, boolean cut) {
            TreeSet<String> next = new TreeSet<String>(cuts);
            Edge edge = graph.edges.get(id);
            if (edge == null || edge.kind != Kind.TRACE) throw new IllegalArgumentException("Invalid copper cut");
            if (cut) next.add(id); else next.remove(id);
            return new Snapshot(graph, next);
        }
        void requirePristineNetConnectivity(TroubleshootBoard board) {
            for (String net : board.getNetIds()) {
                List<String> pads = board.getNet(net).getPadIds();
                for (int i=1; i<pads.size(); i++) if (!padsConnected(pads.get(0), pads.get(i)))
                    throw new IllegalStateException("PCB net is electrically disconnected: " + net);
            }
        }
    }
}
