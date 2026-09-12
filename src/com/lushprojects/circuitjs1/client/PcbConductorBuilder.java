package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Normalizes current Manhattan copper before assigning immutable graph identities. */
final class PcbConductorBuilder {
    private final TroubleshootBoard board;
    private final PcbBoardLayout layout;
    private final List<Segment> segments = new ArrayList<Segment>();
    private final TreeMap<String,Node> nodes = new TreeMap<String,Node>();
    private final TreeMap<String,Link> links = new TreeMap<String,Link>();
    private final TreeMap<String,Land> lands = new TreeMap<String,Land>();
    private final TreeMap<String,String> padFaces = new TreeMap<String,String>();
    private final TreeMap<String,String> terminals = new TreeMap<String,String>();
    private PcbConductorBuilder(TroubleshootBoard board, PcbBoardLayout layout) {
        if (board == null || layout == null) throw new IllegalArgumentException("Missing copper input");
        this.board = board; this.layout = layout;
    }
    static PcbConductorGraph capture(TroubleshootBoard board, PcbBoardLayout layout) {
        return new PcbConductorBuilder(board, layout).build();
    }
    private static String key(PcbCopperLayer layer, int x, int y) { return layer + ":" + x + ":" + y; }
    private static final class Node {
        final String key, net;
        final PcbCopperLayer layer;
        final int x, y;
        String id;
        Node(String net, PcbCopperLayer layer, int x, int y) {
            this.net = net; this.layer = layer; this.x = x; this.y = y; this.key = key(layer,x,y);
        }
    }
    private static final class Segment {
        final String source, net;
        final PcbCopperLayer layer;
        final PcbCopperAccess.Exposure exposure;
        final int x1, y1, x2, y2;
        final boolean horizontal;
        final TreeSet<Integer> splits = new TreeSet<Integer>();
        Segment(PcbTraceGeometry trace, int ax, int ay, int bx, int by) {
            if ((ax == bx) == (ay == by)) throw new IllegalArgumentException("Invalid copper segment");
            source = trace.getSourceId(); net = trace.getNetId(); layer = trace.getLayer();
            exposure = trace.getExposure(); horizontal = ay == by;
            x1 = Math.min(ax,bx); x2 = Math.max(ax,bx); y1 = Math.min(ay,by); y2 = Math.max(ay,by);
            splits.add(horizontal ? x1 : y1); splits.add(horizontal ? x2 : y2);
        }
        int project(int x, int y) {
            return horizontal ? Math.max(x1,Math.min(x2,x)) : Math.max(y1,Math.min(y2,y));
        }
        String supportingLine() { return layer + (horizontal ? ":H:" + y1 : ":V:" + x1); }
        int x(int value) { return horizontal ? value : x1; }
        int y(int value) { return horizontal ? y1 : value; }
        Rectangle bounds() { return stroke(x1,y1,x2,y2); }
        boolean contains(int x, int y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }
    private static final class Link {
        final String first, second, net;
        final PcbConductorGraph.Kind kind;
        final TreeSet<String> sources = new TreeSet<String>();
        PcbCopperAccess.Exposure exposure;
        Link(Node a, Node b, PcbConductorGraph.Kind kind, PcbCopperAccess.Exposure exposure) {
            first = a.key.compareTo(b.key) < 0 ? a.key : b.key;
            second = a.key.compareTo(b.key) < 0 ? b.key : a.key;
            net = a.net; this.kind = kind; this.exposure = exposure;
        }
    }
    private static final class Land {
        final String id, pad;
        final Node node;
        final Rectangle bounds;
        final PcbCopperAccess.Exposure exposure;
        boolean externalContact;
        Land(String id, String pad, Node node, Rectangle bounds, PcbCopperAccess.Exposure exposure) {
            this.id = id; this.pad = pad; this.node = node;
            this.bounds = new Rectangle(bounds); this.exposure = exposure;
        }
    }
    private Node node(String net, PcbCopperLayer layer, int x, int y) {
        String key = key(layer,x,y); Node prior = nodes.get(key);
        if (prior != null) { sameNet(prior.net,net); return prior; }
        Node created = new Node(PcbConductorGraph.requireId(net),layer,x,y);
        nodes.put(key,created); return created;
    }
    private void link(Node a, Node b, PcbConductorGraph.Kind kind, String source,
            PcbCopperAccess.Exposure exposure) {
        sameNet(a.net,b.net);
        if (a == b) return;
        Link next = new Link(a,b,kind,exposure);
        String key = kind + PcbConductorGraph.field(next.first) + PcbConductorGraph.field(next.second);
        Link old = links.get(key);
        if (old == null) { old = next; links.put(key,old); }
        if (old.exposure != exposure) throw new IllegalArgumentException("Conflicting copper exposure");
        old.sources.add(source);
    }
    private static void sameNet(String a, String b) {
        if (!a.equals(b)) throw new IllegalStateException("Unrelated PCB conductors share copper: " + a + " / " + b);
    }
    private PcbConductorGraph build() {
        layout.validateAgainst(board);
        addPadsAndHoles(); addRoutes();
        for (int i=0; i<segments.size(); i++) {
            Segment a = segments.get(i);
            for (int j=i+1; j<segments.size(); j++) contact(a,segments.get(j));
            for (Land land : lands.values()) contact(a,land);
        }
        normalizeCollinearSplits();
        ArrayList<Land> all = new ArrayList<Land>(lands.values());
        for (int i=0; i<all.size(); i++) for (int j=i+1; j<all.size(); j++) {
            Land a=all.get(i), b=all.get(j);
            if (a.node.layer == b.node.layer && touch(a.bounds,b.bounds)) {
                link(a.node,b.node,PcbConductorGraph.Kind.CONTACT,a.id + "/" + b.id,
                    PcbCopperAccess.Exposure.EXPOSED);
                a.externalContact = true; b.externalContact = true;
            }
        }
        for (Segment s : segments) {
            Integer previous = null;
            for (Integer at : s.splits) {
                if (previous != null) link(node(s.net,s.layer,s.x(previous),s.y(previous)),
                    node(s.net,s.layer,s.x(at),s.y(at)),PcbConductorGraph.Kind.TRACE,s.source,s.exposure);
                previous = at;
            }
        }
        for (PcbBoardHole hole : layout.getHoles()) {
            if (hole.kind == PcbBoardHole.Kind.NON_PLATED) {
                for (Segment segment : segments) if (touch(hole.getBounds(),segment.bounds()))
                    throw new IllegalStateException("Non-plated hole intersects copper");
                for (Land land : lands.values()) if (touch(hole.getBounds(),land.bounds))
                    throw new IllegalStateException("Non-plated hole intersects land");
            } else for (PcbCopperLayer layer : PcbCopperLayer.values())
                if (!lands.get("hole/" + PcbConductorGraph.faceKey(hole.id,layer)).externalContact)
                    throw new IllegalStateException("Plated hole has an unattached layer endpoint");
        }
        addInternalConnections();
        return freeze();
    }
    /** Collect first, then distribute: pairwise copying misses later and transitive contacts. */
    private void normalizeCollinearSplits() {
        TreeMap<String,TreeSet<Integer>> byLine = new TreeMap<String,TreeSet<Integer>>();
        for (Segment segment : segments) {
            String line = segment.supportingLine();
            TreeSet<Integer> points = byLine.get(line);
            if (points == null) {
                points = new TreeSet<Integer>(); byLine.put(line,points);
            }
            points.addAll(segment.splits);
        }
        for (Segment segment : segments) {
            int first = segment.horizontal ? segment.x1 : segment.y1;
            int last = segment.horizontal ? segment.x2 : segment.y2;
            // Endpoints already belong to every segment. Share interior breakpoints
            // only inside its coverage; disjoint copper and other layers stay separate.
            segment.splits.addAll(byLine.get(segment.supportingLine()).subSet(first,last));
        }
    }
    private void addPadsAndHoles() {
        for (String padId : new TreeSet<String>(board.getPadIds())) {
            BoardPad logical = board.getPad(padId);
            PcbPadPlacement pad = layout.getPad(padId);
            for (PcbCopperLayer layer : PcbCopperLayer.values()) if (PcbCopperAccess.hasCopper(pad,layer)) {
                Node n = node(logical.getNetId(),layer,pad.getX(),pad.getY());
                String face = PcbConductorGraph.faceKey(padId,layer);
                padFaces.put(face,n.key);
                lands.put("pad/" + face,new Land("pad/" + face,padId,n,pad.getPadBounds(),pad.getExposure()));
                if (layer == PcbCopperLayer.forFace(pad.getMountingSide())) terminals.put(padId,n.key);
            }
            if (pad.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE)
                link(node(logical.getNetId(),PcbCopperLayer.TOP,pad.getX(),pad.getY()),
                    node(logical.getNetId(),PcbCopperLayer.BOTTOM,pad.getX(),pad.getY()),
                    PcbConductorGraph.Kind.PLATED_BARREL,"pad/" + padId,PcbCopperAccess.Exposure.EXPOSED);
        }
        for (PcbBoardHole hole : layout.getHoles()) {
            if (hole.kind == PcbBoardHole.Kind.NON_PLATED) continue;
            if (board.getNet(hole.netId) == null) throw new IllegalArgumentException("Unknown hole net");
            for (PcbCopperLayer layer : PcbCopperLayer.values()) {
                Node n = node(hole.netId,layer,hole.x,hole.y);
                String id = "hole/" + PcbConductorGraph.faceKey(hole.id,layer);
                lands.put(id,new Land(id,null,n,hole.getBounds(),hole.exposure));
            }
            link(node(hole.netId,PcbCopperLayer.TOP,hole.x,hole.y),
                node(hole.netId,PcbCopperLayer.BOTTOM,hole.x,hole.y),
                hole.kind == PcbBoardHole.Kind.VIA ? PcbConductorGraph.Kind.VIA : PcbConductorGraph.Kind.PLATED_BARREL,
                "hole/" + hole.id,PcbCopperAccess.Exposure.EXPOSED);
        }
    }
    private void addRoutes() {
        TreeSet<String> sources = new TreeSet<String>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            PcbConductorGraph.requireId(trace.getSourceId());
            if (!sources.add(trace.getSourceId())) throw new IllegalArgumentException("Duplicate route source identity");
            int[] xs = trace.getXPoints(), ys = trace.getYPoints();
            checkEndpoint(trace.getStartPadId(),trace,xs[0],ys[0]);
            checkEndpoint(trace.getEndPadId(),trace,xs[xs.length-1],ys[ys.length-1]);
            ArrayList<Point> path = new ArrayList<Point>();
            for (int i=0; i<xs.length; i++) {
                Point c = new Point(xs[i],ys[i]);
                if (!path.isEmpty() && path.get(path.size()-1).equals(c)) continue;
                if (path.size() >= 2) {
                    Point a = path.get(path.size()-2), b = path.get(path.size()-1);
                    if ((a.x == b.x && b.x == c.x && (long)(b.y-a.y)*(c.y-b.y) > 0) ||
                        (a.y == b.y && b.y == c.y && (long)(b.x-a.x)*(c.x-b.x) > 0))
                        path.remove(path.size()-1);
                }
                path.add(c);
            }
            if (path.size() < 2) throw new IllegalArgumentException("Empty physical route");
            for (int i=1; i<path.size(); i++) {
                Point a=path.get(i-1), b=path.get(i);
                segments.add(new Segment(trace,a.x,a.y,b.x,b.y));
            }
        }
    }
    private void checkEndpoint(String padId, PcbTraceGeometry trace, int x, int y) {
        if (padId == null) return; // Explicit provenance permits pad-to-via or via-to-via routes.
        PcbPadPlacement pad = layout.getPad(padId);
        BoardPad logical = board.getPad(padId);
        if (logical == null || !PcbCopperAccess.hasCopper(pad,trace.getLayer()) ||
                pad.getX() != x || pad.getY() != y || !logical.getNetId().equals(trace.getNetId()))
            throw new IllegalArgumentException("Trace endpoint has no matching physical layer land");
    }
    private void contact(Segment a, Segment b) {
        if (a.layer != b.layer || !touch(a.bounds(),b.bounds())) return;
        sameNet(a.net,b.net);
        if (a.horizontal == b.horizontal && (a.horizontal ? a.y1 == b.y1 : a.x1 == b.x1)) {
            int left = Math.max(a.horizontal ? a.x1 : a.y1,b.horizontal ? b.x1 : b.y1);
            int right = Math.min(a.horizontal ? a.x2 : a.y2,b.horizontal ? b.x2 : b.y2);
            if (left <= right) {
                a.splits.add(left); a.splits.add(right); b.splits.add(left); b.splits.add(right); return;
            }
        }
        if (a.horizontal != b.horizontal) {
            Segment h = a.horizontal ? a : b, v = a.horizontal ? b : a;
            if (h.contains(v.x1,h.y1) && v.contains(v.x1,h.y1)) {
                h.splits.add(v.x1); v.splits.add(h.y1); return;
            }
        }
        Rectangle ar = a.bounds(), br = b.bounds();
        joinProjected(a,b,Math.max(ar.x,br.x),Math.max(ar.y,br.y));
        joinProjected(a,b,Math.min(ar.x+ar.width,br.x+br.width),Math.min(ar.y+ar.height,br.y+br.height));
    }
    private void joinProjected(Segment a, Segment b, int x, int y) {
        int av=a.project(x,y), bv=b.project(x,y); a.splits.add(av); b.splits.add(bv);
        String provenance = a.source.compareTo(b.source) <= 0 ?
            PcbConductorGraph.field(a.source)+PcbConductorGraph.field(b.source) :
            PcbConductorGraph.field(b.source)+PcbConductorGraph.field(a.source);
        link(node(a.net,a.layer,a.x(av),a.y(av)),node(b.net,b.layer,b.x(bv),b.y(bv)),
            PcbConductorGraph.Kind.CONTACT,provenance,PcbCopperAccess.Exposure.EXPOSED);
    }
    private void contact(Segment segment, Land land) {
        if (segment.layer != land.node.layer || !touch(segment.bounds(),land.bounds)) return;
        sameNet(segment.net,land.node.net);
        int at = segment.project(land.node.x,land.node.y); segment.splits.add(at);
        link(node(segment.net,segment.layer,segment.x(at),segment.y(at)),land.node,
            PcbConductorGraph.Kind.CONTACT,segment.source+"/"+land.id,PcbCopperAccess.Exposure.EXPOSED);
        land.externalContact = true;
    }
    private void addInternalConnections() {
        for (String id : new TreeSet<String>(board.getComponentIds())) {
            BoardComponent component = board.getComponent(id);
            PhysicalPackage physical = component.getPhysicalPackage();
            TreeMap<String,String> byTerminal = new TreeMap<String,String>();
            for (String pad : component.getPadIds()) byTerminal.put(board.getPad(pad).getTerminalId(),pad);
            for (String a : byTerminal.keySet()) for (String b : byTerminal.keySet())
                if (a.compareTo(b) < 0 && physical.isInternallyConnected(a,b))
                    link(nodes.get(terminals.get(byTerminal.get(a))),nodes.get(terminals.get(byTerminal.get(b))),
                        PcbConductorGraph.Kind.PACKAGE_INTERNAL,"package/"+id,PcbCopperAccess.Exposure.COVERED);
        }
    }
    private PcbConductorGraph freeze() {
        ArrayList<PcbConductorGraph.Junction> js = new ArrayList<PcbConductorGraph.Junction>();
        ArrayList<PcbConductorGraph.Edge> es = new ArrayList<PcbConductorGraph.Edge>();
        TreeMap<String,PcbConductorGraph.Surface> surfaces = new TreeMap<String,PcbConductorGraph.Surface>();
        int serial=0;
        for (Node n : nodes.values()) {
            n.id="junction/"+(++serial);
            js.add(new PcbConductorGraph.Junction(n.id,n.net,n.layer,n.x,n.y));
        }
        serial=0;
        for (Link link : links.values()) {
            String id="edge/"+(++serial); Node a=nodes.get(link.first), b=nodes.get(link.second);
            es.add(new PcbConductorGraph.Edge(id,a.id,b.id,link.net,link.kind,link.sources));
            if (link.kind == PcbConductorGraph.Kind.TRACE) {
                String surfaceId="copper/"+id;
                surfaces.put(surfaceId,new PcbConductorGraph.Surface(surfaceId,a.id,id,null,
                    a.layer,link.exposure,stroke(a.x,a.y,b.x,b.y)));
            }
        }
        for (Land land : lands.values()) surfaces.put(land.id,new PcbConductorGraph.Surface(
            land.id,land.node.id,null,land.pad,land.node.layer,land.exposure,land.bounds));
        StringBuilder holes=new StringBuilder();
        for (PcbBoardHole hole : layout.getHoles()) holes.append(PcbConductorGraph.field(hole.fingerprint()));
        return new PcbConductorGraph(js,es,new ArrayList<PcbConductorGraph.Surface>(surfaces.values()),
            mapped(padFaces),mapped(terminals),holes.toString());
    }
    private Map<String,String> mapped(Map<String,String> input) {
        TreeMap<String,String> result=new TreeMap<String,String>();
        for (String id : input.keySet()) result.put(id,nodes.get(input.get(id)).id);
        return result;
    }
    /** Same closed, finite-width Manhattan copper envelope as the current layout validator. */
    static Rectangle stroke(int x1, int y1, int x2, int y2) {
        if ((x1 == x2) == (y1 == y2)) throw new IllegalArgumentException("Invalid copper stroke");
        int half=PcbTraceRules.TRACE_WIDTH/2;
        Rectangle r=new Rectangle(PcbCoordinateSystem.checkedInt((long)Math.min(x1,x2)-half),
            PcbCoordinateSystem.checkedInt((long)Math.min(y1,y2)-half),
            PcbCoordinateSystem.checkedInt(Math.abs((long)x2-x1)+PcbTraceRules.TRACE_WIDTH),
            PcbCoordinateSystem.checkedInt(Math.abs((long)y2-y1)+PcbTraceRules.TRACE_WIDTH));
        PcbCoordinateSystem.requireBoardRectangle(r); return r;
    }
    static boolean touch(Rectangle a, Rectangle b) {
        return (long)a.x+a.width >= b.x && (long)b.x+b.width >= a.x &&
            (long)a.y+a.height >= b.y && (long)b.y+b.height >= a.y;
    }
}
