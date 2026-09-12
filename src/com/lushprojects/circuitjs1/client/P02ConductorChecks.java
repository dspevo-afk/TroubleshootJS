package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

/** Bounded layer/identity contracts plus an independent integer-lattice contact oracle. */
final class P02ConductorChecks {
    private static int assertions;
    static int verify() {
        assertions=0;
        sharedTrunk(); partialSharedTrunks(); surfaceContracts(); layersAndHoles(); smdPoses(); fullLayerLayout(); oracleCases(); ownership();
        return assertions;
    }
    static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new IllegalStateException("P02 " + message);
    }
    static void rejects(Runnable operation, String message) {
        boolean rejected=false;
        try { operation.run(); } catch (RuntimeException expected) { rejected=true; }
        check(rejected,message);
    }
    private static Vector<String> names(String name) {
        Vector<String> result=new Vector<String>(); result.add(name); return result;
    }
    /** Literal conductor-only lands; the separate SMD matrix uses complete real P01 footprints. */
    private static final class Fixture {
        final TroubleshootBoard board=new TroubleshootBoard("P02_FIXTURE");
        final PcbBoardLayout layout=new PcbBoardLayout(1200,1000,new Rectangle(0,0,850,850),new Rectangle(900,20,200,200));
        final PhysicalPackage point=PhysicalPackage.developerPackageWithGenericGeometry(
            "P02_POINT",names("1"),new Vector<String>(),false);
        void pad(String id, String net, int x, int y, PcbBoardSide face, boolean plated) {
            if (board.getNet(net) == null) board.addNet(new BoardNet(net));
            board.addComponent(new BoardComponent(id,"P02 point",point));
            board.addPad(new BoardPad(id+".1",id,"1",net));
            layout.addPad(new PcbPadPlacement(id+".1",x,y,0,0,0,new Rectangle(x-6,y-6,12,12),
                new Rectangle(x-8,y-8,16,16),plated ? PcbTerminalAttachment.PLATED_THROUGH_HOLE :
                PcbTerminalAttachment.SURFACE_PAD,face));
        }
        void route(String id, String net, String a, String b, PcbCopperLayer layer, int[] x, int[] y) {
            layout.addTrace(new PcbTraceGeometry(id,net,a == null ? null : a+".1",b == null ? null : b+".1",
                layer,PcbCopperAccess.Exposure.EXPOSED,x,y));
        }
        PcbConductorGraph graph() {
            board.validate(); PcbConductorGraph graph=layout.captureConductorGraph(board);
            noOverlappingTraceEdges(graph); return graph;
        }
    }
    private static Fixture branch() {
        Fixture f=new Fixture();
        f.pad("A","N",100,100,PcbBoardSide.TOP,false);
        f.pad("B","N",260,100,PcbBoardSide.TOP,false);
        f.pad("C","N",260,220,PcbBoardSide.TOP,false);
        f.route("a-b","N","A","B",PcbCopperLayer.TOP,new int[]{100,180,260},new int[]{100,100,100});
        f.route("a-c","N","A","C",PcbCopperLayer.TOP,new int[]{100,180,180,260},new int[]{100,100,220,220});
        return f;
    }
    private static String trunk(PcbConductorGraph graph) {
        String id=null;
        for (PcbConductorGraph.Edge edge : graph.getEdges().values())
            if (edge.kind == PcbConductorGraph.Kind.TRACE && edge.getSources().size() == 2) {
                check(id == null,"one normalized shared trunk"); id=edge.id;
            }
        check(id != null,"shared source provenance retained"); return id;
    }
    private static void sharedTrunk() {
        final Fixture f=branch(); final PcbConductorGraph graph=f.graph();
        final String edge=trunk(graph);
        PcbConductorGraph.Snapshot before=graph.pristine(), cut=before.withCut(edge,true);
        check(before.padsConnected("A.1","C.1"),"original branch connected");
        check(!cut.padsConnected("A.1","B.1") && !cut.padsConnected("A.1","C.1"),"shared metal actually removed");
        check(cut.padsConnected("B.1","C.1"),"cut does not delete the entire logical bus");
        check(f.board.getNet("N").getPadIds().size() == 3,"logical net labels survive a physical split");
        check(before.hasEdge(edge) && !cut.hasEdge(edge),"pristine snapshot remains unchanged");
        String encoded=PcbConductorState.encode(cut);
        check(encoded.equals(PcbConductorState.encode(PcbConductorState.decode(graph,encoded))),"cut state round trip");
        check(PcbConductorState.encode(before).equals(PcbConductorState.encode(cut.withCut(edge,false))),"restoration round trip");
        rejects(new Runnable(){ public void run(){ graph.pristine().withCut("a-b",true); }},"route provenance is not a cut identity");
        rejects(new Runnable(){ public void run(){ graph.getEdges().clear(); }},"edge map immutable");
        rejects(new Runnable(){ public void run(){ graph.getEdges().get(edge).getSources().clear(); }},"provenance immutable");
        Rectangle copy=graph.getSurfaces().get(0).getBounds(); copy.x+=50;
        check(!copy.equals(graph.getSurfaces().get(0).getBounds()),"surface bounds copied");
        f.layout.seal();
        rejects(new Runnable(){ public void run(){ f.layout.compactToContent(30,30,10); }},"sealed realization cannot mutate behind proof");
    }
    /** Review F1: finite-width contact must subdivide every source covering the same metal. */
    private static Fixture partialSharedCopper(boolean vertical, PcbBoardSide side,
            int[] order, boolean reverseAndSubdivide) {
        Fixture f=new Fixture();
        f.pad("A","N",vertical ? 100 : 50,vertical ? 50 : 100,side,false);
        f.pad("B","N",vertical ? 100 : 200,vertical ? 200 : 100,side,false);
        String[] sources={"main","shared","branch"};
        int[][] x={{50,200},{100,150},{153,153}};
        int[][] y={{100,100},{100,100},{40,180}};
        for (int index : order) {
            int[] px=x[index], py=y[index];
            if (vertical) { int[] swap=px; px=py; py=swap; }
            if (reverseAndSubdivide) {
                px=new int[]{px[1],px[0]+(px[1]-px[0])/2,px[0]};
                py=new int[]{py[1],py[0]+(py[1]-py[0])/2,py[0]};
            }
            f.route(sources[index],"N",null,null,PcbCopperLayer.forFace(side),px,py);
        }
        return f;
    }
    private static String sharedInterval(PcbConductorGraph graph, boolean vertical) {
        String found=null;
        for (PcbConductorGraph.Edge edge : graph.getEdges().values()) {
            if (edge.kind != PcbConductorGraph.Kind.TRACE) continue;
            PcbConductorGraph.Junction a=graph.getJunctions().get(edge.first);
            PcbConductorGraph.Junction b=graph.getJunctions().get(edge.second);
            int start=vertical ? Math.min(a.y,b.y) : Math.min(a.x,b.x);
            int end=vertical ? Math.max(a.y,b.y) : Math.max(a.x,b.x);
            boolean onLine=vertical ? a.x==100 && b.x==100 : a.y==100 && b.y==100;
            if (onLine && start==100 && end==149) {
                check(found==null,"review F1 physical interval exists only once");
                check(edge.getSources().size()==2 && edge.getSources().contains("main") &&
                    edge.getSources().contains("shared"),"review F1 shared interval retains both sources");
                found=edge.id;
            }
        }
        check(found!=null,"review F1 finite-width contact retains its interior split");
        return found;
    }
    private static void noOverlappingTraceEdges(PcbConductorGraph graph) {
        List<PcbConductorGraph.Edge> traces=new ArrayList<PcbConductorGraph.Edge>();
        for (PcbConductorGraph.Edge edge : graph.getEdges().values())
            if (edge.kind==PcbConductorGraph.Kind.TRACE) traces.add(edge);
        for (int i=0; i<traces.size(); i++) for (int j=i+1; j<traces.size(); j++) {
            PcbConductorGraph.Edge a=traces.get(i), b=traces.get(j);
            PcbConductorGraph.Junction a1=graph.getJunctions().get(a.first), a2=graph.getJunctions().get(a.second);
            PcbConductorGraph.Junction b1=graph.getJunctions().get(b.first), b2=graph.getJunctions().get(b.second);
            if (a1.layer!=b1.layer) continue;
            if (a1.y==a2.y && a1.y==b1.y && b1.y==b2.y)
                check(Math.min(Math.max(a1.x,a2.x),Math.max(b1.x,b2.x))<=
                    Math.max(Math.min(a1.x,a2.x),Math.min(b1.x,b2.x)),"no duplicate horizontal copper intervals");
            if (a1.x==a2.x && a1.x==b1.x && b1.x==b2.x)
                check(Math.min(Math.max(a1.y,a2.y),Math.max(b1.y,b2.y))<=
                    Math.max(Math.min(a1.y,a2.y),Math.min(b1.y,b2.y)),"no duplicate vertical copper intervals");
        }
    }
    private static void partialSharedTrunks() {
        int[][] orders={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        for (PcbBoardSide side : PcbBoardSide.values()) for (boolean vertical : new boolean[]{false,true}) {
            PcbConductorGraph reference=partialSharedCopper(vertical,side,orders[0],false).graph();
            String expectedEdge=sharedInterval(reference,vertical);
            for (int[] order : orders) for (boolean reversed : new boolean[]{false,true}) {
                Fixture fixture=partialSharedCopper(vertical,side,order,reversed);
                PcbConductorGraph graph=fixture.graph();
                String edge=sharedInterval(graph,vertical);
                check(expectedEdge.equals(edge) && reference.toCanonical().equals(graph.toCanonical()),
                    "review F1 insertion order, reversal and subdivision preserve physical identity");
                PcbConductorGraph.Snapshot before=graph.pristine(), cut=before.withCut(edge,true);
                check(before.padsConnected("A.1","B.1"),"review F1 pristine endpoints connected");
                check(!cut.padsConnected("A.1","B.1"),"review F1 one physical cut removes every redundant route");
                check(before.hasEdge(edge) && !cut.hasEdge(edge),"review F1 cut leaves pristine snapshot immutable");
                check(fixture.board.getNet("N").getPadIds().size()==2,"review F1 logical net survives cut");
                String saved=PcbConductorState.encode(cut);
                PcbConductorGraph.Snapshot decoded=PcbConductorState.decode(reference,saved);
                check(saved.equals(PcbConductorState.encode(decoded)) && !decoded.padsConnected("A.1","B.1"),
                    "review F1 equivalent realization round trip preserves cut");
                check(PcbConductorState.encode(before).equals(PcbConductorState.encode(cut.withCut(edge,false))),
                    "review F1 restoring physical copper restores pristine encoding");
            }
        }
    }
    private static Fixture crossing(boolean bottom, boolean sameNet) {
        Fixture f=new Fixture(); String vertical=sameNet ? "N" : "M";
        PcbBoardSide side=bottom ? PcbBoardSide.BOTTOM : PcbBoardSide.TOP;
        f.pad("A","N",60,140,PcbBoardSide.TOP,false); f.pad("B","N",260,140,PcbBoardSide.TOP,false);
        f.pad("C",vertical,160,40,side,false); f.pad("D",vertical,160,240,side,false);
        f.route("horizontal","N","A","B",PcbCopperLayer.TOP,new int[]{60,260},new int[]{140,140});
        f.route("vertical",vertical,"C","D",PcbCopperLayer.forFace(side),new int[]{160,160},new int[]{40,240});
        return f;
    }
    private static Fixture via(PcbBoardHole.Kind kind, boolean bottomRoute) {
        Fixture f=new Fixture();
        f.pad("A","N",100,100,PcbBoardSide.TOP,false); f.pad("B","N",260,100,PcbBoardSide.BOTTOM,false);
        f.route("top","N","A",null,PcbCopperLayer.TOP,new int[]{100,180},new int[]{100,100});
        if (bottomRoute) f.route("bottom","N",null,"B",PcbCopperLayer.BOTTOM,new int[]{180,260},new int[]{100,100});
        boolean plated=kind != PcbBoardHole.Kind.NON_PLATED;
        f.layout.addHole(new PcbBoardHole("via",kind,plated ? "N" : null,180,100,4,plated ? 12 : 4,
            plated ? PcbCopperLayer.TOP : null,plated ? PcbCopperLayer.BOTTOM : null,PcbCopperAccess.Exposure.EXPOSED));
        return f;
    }
    private static void layersAndHoles() {
        PcbConductorGraph.Snapshot crossed=crossing(true,false).graph().pristine();
        check(crossed.padsConnected("A.1","B.1") && crossed.padsConnected("C.1","D.1"),"each layer connected");
        check(!crossed.padsConnected("A.1","C.1"),"projected crossing does not short layers");
        rejects(new Runnable(){ public void run(){ crossing(false,false).graph(); }},"same-layer unrelated crossing rejected");
        final Fixture labels=crossing(true,true);
        check(!labels.graph().pristine().padsConnected("A.1","C.1"),"same net label cannot invent a bridge");
        rejects(new Runnable(){ public void run(){ labels.graph().pristine().requirePristineNetConnectivity(labels.board); }},"hidden same-net disconnection rejected");
        check(via(PcbBoardHole.Kind.VIA,true).graph().pristine().padsConnected("A.1","B.1"),"via joins valid layer lands");
        check(via(PcbBoardHole.Kind.PLATED,true).graph().pristine().padsConnected("A.1","B.1"),"plated hole joins faces");
        rejects(new Runnable(){ public void run(){ via(PcbBoardHole.Kind.VIA,false).graph(); }},"via requires both physical layer endpoints");
        rejects(new Runnable(){ public void run(){ via(PcbBoardHole.Kind.NON_PLATED,true).graph(); }},"NPTH cannot carry routed copper");
        rejects(new Runnable(){ public void run(){ new PcbBoardHole("bad",PcbBoardHole.Kind.VIA,"N",80,80,4,12,
            PcbCopperLayer.TOP,PcbCopperLayer.TOP,PcbCopperAccess.Exposure.EXPOSED); }},"invalid via layer pair rejected");
        rejects(new Runnable(){ public void run(){ new PcbBoardHole("bad",PcbBoardHole.Kind.NON_PLATED,"N",80,80,4,4,
            null,null,PcbCopperAccess.Exposure.EXPOSED); }},"NPTH cannot declare a net");
        Fixture clear=branch(); int edges=clear.graph().getEdges().size();
        clear.layout.addHole(new PcbBoardHole("mount",PcbBoardHole.Kind.NON_PLATED,null,500,500,5,5,
            null,null,PcbCopperAccess.Exposure.EXPOSED));
        check(edges == clear.graph().getEdges().size(),"clear NPTH creates no conductive edge");
        PcbBoardHole nonPlated=clear.layout.getHoles().firstElement();
        check(PcbCopperAccess.blocksProbeAt(nonPlated,nonPlated.getBounds(),500,500),"NPTH refuses a nearby pad probe halo");
        check(!PcbCopperAccess.blocksProbeAt(nonPlated,nonPlated.getBounds(),510,510),"hole rejection stays inside its bounds");
        PcbBoardHole plated=via(PcbBoardHole.Kind.VIA,true).layout.getHoles().firstElement();
        check(!PcbCopperAccess.blocksProbeAt(plated,plated.getBounds(),180,100),"plated bore retains legitimate barrel access");
        final PcbConductorGraph original=branch().graph();
        final String saved=PcbConductorState.encode(original.pristine().withCut(trunk(original),true));
        final Fixture rerouted=new Fixture();
        rerouted.pad("A","N",100,100,PcbBoardSide.TOP,false); rerouted.pad("B","N",260,100,PcbBoardSide.TOP,false);
        rerouted.pad("C","N",260,220,PcbBoardSide.TOP,false);
        rerouted.route("a-b","N","A","B",PcbCopperLayer.TOP,new int[]{100,100,260,260},new int[]{100,60,60,100});
        rerouted.route("a-c","N","A","C",PcbCopperLayer.TOP,new int[]{100,180,180,260},new int[]{100,100,220,220});
        rejects(new Runnable(){ public void run(){ PcbConductorState.decode(rerouted.graph(),saved); }},"reroute rejects old current-state artifacts");
        rejects(new Runnable(){ public void run(){ PcbConductorState.decode(original,saved+"edge/999999\n"); }},"unknown cut identity rejected");
        final Map<String,Integer> merged=new HashMap<String,Integer>();
        for (String pad : labels.board.getPadIds()) merged.put(pad,Integer.valueOf(7));
        rejects(new Runnable(){ public void run(){ PcbConductorProjection.requireObservation(labels.graph().pristine(),merged); }},"solver alias cannot bridge isolated same-net copper");
    }
    private static void smdPoses() {
        for (PhysicalPackage physical : new PhysicalPackage[]{PhysicalPackages.DEV_SMD_0805,PhysicalPackages.DEV_SMD_SOT23})
            for (PcbRotation rotation : physical.getAllowedRotations()) for (PcbBoardSide side : physical.getAllowedMountingSides()) {
                TroubleshootBoard board=new TroubleshootBoard("P02_SMD");
                BoardComponent component=new BoardComponent("U",physical.getId(),physical); board.addComponent(component);
                for (String terminal : physical.getTerminalIds()) {
                    board.addNet(new BoardNet("N"+terminal));
                    board.addPad(new BoardPad("U."+terminal,"U",terminal,"N"+terminal));
                }
                PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,new PcbPackagePose(150,150,rotation,side),physical.getGeometry());
                PcbBoardLayout layout=new PcbBoardLayout(1000,800,new Rectangle(20,20,600,600),new Rectangle(700,20,200,200));
                layout.addComponent(footprint.getPlacement());
                for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
                PcbConductorGraph graph=layout.captureConductorGraph(board); String canonical=graph.toCanonical();
                check(graph.getEdges().isEmpty(),"surface package invents no plated barrel or internal short");
                for (PcbPadPlacement pad : footprint.getPads()) {
                    check(graph.getPadJunction(pad.getPadId(),PcbCopperLayer.forFace(side)) != null,"mounting layer owns land");
                    check(graph.getPadJunction(pad.getPadId(),PcbCopperLayer.forFace(side.opposite())) == null,"opposite layer has no SMD land");
                    for (PcbBoardSide face : PcbBoardSide.values()) {
                        check(PcbCopperAccess.canProbe(pad,face) == (face == side),"face-specific pad access");
                        PcbBoardViewTransform view=new PcbBoardViewTransform(layout.getBoardOutline(),face);
                        Point point=new Point(pad.getX(),pad.getY());
                        check(point.equals(view.viewToBoard(view.boardToView(point))),"view round trip preserves terminal");
                    }
                }
                check(canonical.equals(graph.toCanonical()),"view changes cannot rename copper");
            }
        PcbPadPlacement covered=new PcbPadPlacement("masked",100,100,0,0,0,
            new Rectangle(94,94,12,12),new Rectangle(92,92,16,16),
            PcbTerminalAttachment.SURFACE_PAD,PcbBoardSide.TOP,PcbCopperAccess.Exposure.COVERED);
        check(PcbCopperAccess.hasCopper(covered,PcbCopperLayer.TOP),"mask does not remove copper");
        check(!PcbCopperAccess.canProbe(covered,PcbBoardSide.TOP),"covered copper is not a probe target");
        Fixture plated=new Fixture(); plated.pad("P","N",100,100,PcbBoardSide.TOP,true);
        PcbConductorGraph graph=plated.graph();
        check(graph.pristine().connected(graph.getPadJunction("P.1",PcbCopperLayer.TOP),
            graph.getPadJunction("P.1",PcbCopperLayer.BOTTOM)),"one plated pad joins both faces");
        for (PcbBoardSide face : PcbBoardSide.values())
            check(PcbCopperAccess.canProbe(plated.layout.getPad("P.1"),face),"exposed plated pad accessible on both faces");
    }

    /** Primitive rectangles painted onto an integer lattice, independent of graph-builder math. */
    private static final class RasterOracle {
        final List<Integer> parent=new ArrayList<Integer>();
        final Map<String,Integer> occupied=new HashMap<String,Integer>();
        final Map<String,Integer> padNodes=new HashMap<String,Integer>();
        int root(int i) { while (parent.get(i).intValue()!=i) i=parent.get(i).intValue(); return i; }
        void join(int a, int b) { a=root(a); b=root(b); if (a!=b) parent.set(b,Integer.valueOf(a)); }
        int paint(PcbCopperLayer layer, int left, int top, int right, int bottom) {
            int id=parent.size(); parent.add(Integer.valueOf(id));
            for (int x=left; x<=right; x++) for (int y=top; y<=bottom; y++) {
                String key=layer+"/"+x+"/"+y;
                Integer previous=occupied.put(key,Integer.valueOf(id));
                if (previous!=null) join(id,previous.intValue());
            }
            return id;
        }
        RasterOracle(Fixture fixture) {
            for (PcbPadPlacement pad : fixture.layout.getPads()) {
                Rectangle r=pad.getPadBounds(); Integer first=null;
                for (PcbCopperLayer layer : PcbCopperLayer.values()) {
                    boolean plated=pad.getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE;
                    if (!plated && layer.getFace()!=pad.getMountingSide()) continue;
                    int id=paint(layer,r.x,r.y,r.x+r.width,r.y+r.height);
                    if (first==null) { first=Integer.valueOf(id); padNodes.put(pad.getPadId(),first); }
                    else join(first.intValue(),id);
                }
            }
            for (PcbTraceGeometry route : fixture.layout.getTraces()) {
                int[] x=route.getXPoints(), y=route.getYPoints();
                for (int i=1; i<x.length; i++) {
                    if (x[i]==x[i-1] && y[i]==y[i-1]) continue;
                    paint(route.getLayer(),Math.min(x[i-1],x[i])-4,Math.min(y[i-1],y[i])-4,
                        Math.max(x[i-1],x[i])+5,Math.max(y[i-1],y[i])+5);
                }
            }
        }
        boolean connected(String a, String b) { return root(padNodes.get(a))==root(padNodes.get(b)); }
    }
    private static void oracleCases() {
        Random random=new Random(0x502L);
        for (int trial=0; trial<40; trial++) {
            Fixture f=new Fixture();
            int[] x=new int[6], y=new int[6];
            for (int i=0; i<x.length; i++) {
                x[i]=40+(i%3)*60; y[i]=40+(i/3)*60;
                f.pad("P"+i,"N",x[i],y[i],PcbBoardSide.TOP,true);
            }
            for (int i=0; i<7; i++) {
                int a=random.nextInt(6), b=random.nextInt(6);
                if (a==b) continue;
                f.route("route-"+i,"N","P"+a,"P"+b,random.nextBoolean() ? PcbCopperLayer.TOP : PcbCopperLayer.BOTTOM,
                    new int[]{x[a],x[b],x[b]},new int[]{y[a],y[a],y[b]});
            }
            PcbConductorGraph graph=f.graph(); RasterOracle expected=new RasterOracle(f);
            for (String a : f.board.getPadIds()) for (String b : f.board.getPadIds())
                check(graph.pristine().padsConnected(a,b)==expected.connected(a,b),"independent lattice contact oracle trial "+trial);
            PcbBoardLayout subdivided=new PcbBoardLayout(1200,1000,f.layout.getBoardOutline(),f.layout.getPartsTray());
            for (PcbPadPlacement pad : f.layout.getPads()) subdivided.addPad(pad);
            Vector<PcbTraceGeometry> routes=f.layout.getTraces(); Collections.reverse(routes);
            for (PcbTraceGeometry route : routes) {
                int[] px=route.getXPoints(), py=route.getYPoints();
                int[] sx=new int[px.length*2-1], sy=new int[py.length*2-1];
                for (int i=0; i<px.length; i++) {
                    sx[i*2]=px[i]; sy[i*2]=py[i];
                    if (i>0) { sx[i*2-1]=(px[i-1]+px[i])/2; sy[i*2-1]=(py[i-1]+py[i])/2; }
                }
                subdivided.addTrace(route.withPath(sx,sy));
            }
            check(graph.toCanonical().equals(subdivided.captureConductorGraph(f.board).toCanonical()),
                "subdivision and insertion order preserve conductor IDs trial "+trial);
        }
    }
    /** A transactional projection double, not a claim of live CircuitJS trace-cut gameplay. */
    private static final class ProjectionDouble implements PcbConductorState.Projection {
        PcbConductorGraph.Snapshot electrical;
        boolean failApply, failRollback;
        int writes;
        ProjectionDouble(PcbConductorGraph.Snapshot initial) { electrical=initial; }
        public PcbConductorState.Prepared prepare(final PcbConductorGraph.Snapshot before,
                final PcbConductorGraph.Snapshot next) {
            return new PcbConductorState.Prepared() {
                public void apply() {
                    electrical=next; writes++;
                    if (failApply) throw new IllegalStateException("injected projection failure");
                }
                public void verify(PcbConductorGraph.Snapshot expected) {
                    if (!PcbConductorState.encode(electrical).equals(PcbConductorState.encode(expected)))
                        throw new IllegalStateException("projection mismatch");
                }
                public void rollback() {
                    if (failRollback) throw new IllegalStateException("injected rollback failure");
                    electrical=before;
                }
            };
        }
    }
    private static void ownership() {
        final PcbConductorGraph graph=branch().graph(); final String id=trunk(graph);
        final PcbConductorState disabled=new PcbConductorState(graph,null);
        rejects(new Runnable(){ public void run(){ disabled.replace(disabled.getCurrent(),disabled.previewCut(id,true)); }},
            "live metadata-only cut refuses absent solver adapter");
        final ProjectionDouble projection=new ProjectionDouble(graph.pristine());
        final PcbConductorState state=new PcbConductorState(graph,projection);
        final PcbConductorGraph.Snapshot old=state.getCurrent();
        final PcbConductorGraph.Snapshot next=state.previewCut(id,true);
        state.replace(old,next);
        check(state.getCurrent()==next && !projection.electrical.padsConnected("A.1","B.1"),"publish only verified projection");
        rejects(new Runnable(){ public void run(){ state.replace(old,next); }},"stale current owner rejected");
        final PcbConductorGraph.Snapshot foreign=branch().graph().pristine();
        rejects(new Runnable(){ public void run(){ state.replace(next,foreign); }},"foreign realization rejected");
        int writes=projection.writes;
        check(writes==1,"stale and foreign mutations made no writes");
        projection.failApply=true;
        rejects(new Runnable(){ public void run(){ state.replace(next,next.withCut(id,false)); }},"failed application rejected");
        check(state.getCurrent()==next && projection.electrical==next,"failed write compensated before publication");
        check(!state.isQuarantined(),"successful compensation does not quarantine");
        projection.failRollback=true;
        rejects(new Runnable(){ public void run(){ state.replace(next,next.withCut(id,false)); }},"failed compensation rejected");
        check(state.isQuarantined(),"unverified electrical rollback quarantines current owner");
        rejects(new Runnable(){ public void run(){ state.getCurrent(); }},"quarantined state cannot expose stale electrical truth");
        final String encoded=PcbConductorState.encode(next);
        rejects(new Runnable(){ public void run(){ PcbConductorState.decode(graph,encoded+id+"\n"); }},"duplicate persisted cut rejected");
        rejects(new Runnable(){ public void run(){ PcbConductorState.decode(graph,encoded.replace("P02-CUTS/1","P02-CUTS/0")); }},"retired copper format rejected");
        check(graph.pristine().padsConnected("A.1","B.1"),"mutation never changes frozen pristine copper");
    }
    private static void fullLayerLayout() {
        TroubleshootBoard board=new TroubleshootBoard("P02_TWO_SIDED_FOOTPRINTS");
        PcbBoardLayout layout=new PcbBoardLayout(1000,800,new Rectangle(20,20,600,600),new Rectangle(700,20,200,200));
        board.addNet(new BoardNet("LEFT")); board.addNet(new BoardNet("RIGHT"));
        PhysicalPackage p=PhysicalPackages.DEV_SMD_0805;
        for (PcbBoardSide side : PcbBoardSide.values()) {
            String id=side==PcbBoardSide.TOP ? "T" : "B";
            BoardComponent component=new BoardComponent(id,p.getId(),p); board.addComponent(component);
            board.addPad(new BoardPad(id+".1",id,"1",side==PcbBoardSide.TOP ? "LEFT" : "RIGHT"));
            board.addPad(new BoardPad(id+".2",id,"2",side==PcbBoardSide.TOP ? "RIGHT" : "LEFT"));
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,new PcbPackagePose(200,200,PcbRotation.DEG_0,side),p.getGeometry());
            layout.addComponent(footprint.getPlacement());
            layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:"+id,id,new Rectangle(220,side==PcbBoardSide.TOP ? 150 : 120,30,16),12,false,null));
            for (PcbPadPlacement pad : footprint.getPads()) {
                layout.addPad(pad);
                boolean left=board.getPad(pad.getPadId()).getNetId().equals("LEFT");
                layout.addTrace(new PcbTraceGeometry(id+pad.getPadId(),left ? "LEFT" : "RIGHT",pad.getPadId(),null,
                    PcbCopperLayer.forFace(side),PcbCopperAccess.Exposure.EXPOSED,
                    new int[]{pad.getX(),left ? 160 : 340},new int[]{pad.getY(),230}));
            }
        }
        layout.addHole(new PcbBoardHole("LEFT_VIA",PcbBoardHole.Kind.VIA,"LEFT",160,230,4,12,
            PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM,PcbCopperAccess.Exposure.EXPOSED));
        layout.addHole(new PcbBoardHole("RIGHT_VIA",PcbBoardHole.Kind.VIA,"RIGHT",340,230,4,12,
            PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM,PcbCopperAccess.Exposure.EXPOSED));
        layout.validateGeometry(board);
        PcbConductorGraph graph=layout.captureConductorGraph(board);
        check(graph.pristine().padsConnected("T.1","B.2"),"full package validator admits a legal two-sided via connection");
        check(!graph.pristine().padsConnected("T.1","T.2"),"two-sided package layout preserves separate nets");
        layout.compactToContent(50,50,30); layout.validateGeometry(board);
        PcbConductorGraph moved=layout.captureConductorGraph(board);
        check(moved.pristine().padsConnected("T.1","B.2"),"compaction preserves vias and layer mapping");
        check(!graph.toCanonical().equals(moved.toCanonical()),"moved realization has distinct geometry snapshot");
    }
    private static void surfaceContracts() {
        final PcbConductorGraph graph=branch().graph();
        final PcbConductorGraph.Edge edge=graph.getEdges().get(trunk(graph));
        final PcbConductorGraph.Junction first=graph.getJunctions().get(edge.first);
        PcbConductorGraph.Junction foreign=null;
        for (PcbConductorGraph.Junction j : graph.getJunctions().values())
            if (!j.id.equals(edge.first) && !j.id.equals(edge.second)) { foreign=j; break; }
        final PcbConductorGraph.Junction wrong=foreign;
        rejects(new Runnable(){ public void run(){ constructSurface(graph,edge,wrong,
            PcbConductorBuilder.stroke(first.x,first.y,graph.getJunctions().get(edge.second).x,graph.getJunctions().get(edge.second).y)); }},
            "surface cannot refer to another conductor junction");
        rejects(new Runnable(){ public void run(){ constructSurface(graph,edge,first,new Rectangle(400,400,4,4)); }},
            "surface cannot invent unrelated edge geometry");
    }
    private static void constructSurface(PcbConductorGraph graph, PcbConductorGraph.Edge edge,
            PcbConductorGraph.Junction anchor, Rectangle bounds) {
        PcbConductorGraph.Surface surface=new PcbConductorGraph.Surface("malformed",anchor.id,edge.id,null,
            anchor.layer,PcbCopperAccess.Exposure.EXPOSED,bounds);
        new PcbConductorGraph(new ArrayList<PcbConductorGraph.Junction>(graph.getJunctions().values()),
            new ArrayList<PcbConductorGraph.Edge>(graph.getEdges().values()),Collections.singletonList(surface),
            Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),"");
    }
    private P02ConductorChecks() { }
}
