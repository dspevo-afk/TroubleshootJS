package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Method;
import java.util.TreeSet;
import java.util.Vector;

public final class P04RoutingContractTest {
    private static int assertions,branches;
    private static void require(boolean ok,String why) { assertions++; if(!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        for(String family:new String[]{"led","diode","parallel"}) for(long seed:new long[]{0,3,-17}) {
            TroubleshootBoard board=board(family); PcbPlacementPlanner planner=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbBoardLayout layout=null;long start=System.nanoTime();int attempts=0;
            for(int candidate=0;candidate<12;candidate++) try {
                attempts++;
                PcbPlacementPlanner.Plan plan=planner.plan(board,board.getPlacementConstraints(),seed,candidate);
                PcbBoardLayout test=plan.materialize();
                PcbNetRouter.route(test,board,plan.outline,candidate,new SeededPcbLayoutGenerator.AttemptObserver(){public void check(int attempt){}},false);
                Method labels=SeededPcbLayoutGenerator.class.getDeclaredMethod("placeSilkscreen",PcbBoardLayout.class,TroubleshootBoard.class,Rectangle.class);
                labels.setAccessible(true);labels.invoke(new SeededPcbLayoutGenerator(),test,board,plan.outline);
                test.validateGeometry(board);layout=test;break;
            }catch(PcbNetRouter.Rejected expected){System.out.println("REJECT "+family+" "+seed+" "+candidate+" "+expected.getMessage());} catch(PcbPlacementPlanner.Rejected expected){System.out.println("REJECT "+expected.getMessage());} catch(PcbBoardLayout.RouteQualityRejectedException expected){System.out.println("REJECT "+expected.getMessage());}
            require(layout!=null,"matched corpus routed "+family+" "+seed);
            TreeSet<String> raw=raster(layout);String graph=layout.captureConductorGraph(board).toCanonical();
            PcbRouteMetrics before=PcbRouteMetrics.measure(layout.getTraces());
            PcbRouteCanonicalizer.canonicalize(layout);layout.validateGeometry(board);
            PcbRouteMetrics after=PcbRouteMetrics.measure(layout.getTraces());
            require(raw.equals(raster(layout)),"independent unit raster copper equality");
            require(graph.equals(layout.captureConductorGraph(board).toCanonical()),"canonicalization preserves conductor IDs/contact/provenance");
            require(before.bends==after.bends&&before.uniqueLength==after.uniqueLength,"literal bend and length preservation");
            require(after.segments<before.segments,"raw vertices reduced");
            for(PcbTraceGeometry trace:layout.getTraces()) {
                if(trace.getEndPadId()==null) { branches++;int[] x=trace.getXPoints(),y=trace.getYPoints();boolean witness=false;
                    for(PcbTraceGeometry trunk:layout.getTraces()) if(trunk!=trace&&trunk.getNetId().equals(trace.getNetId())) {
                        int[] tx=trunk.getXPoints(),ty=trunk.getYPoints();for(int i=0;i<tx.length;i++)if(tx[i]==x[x.length-1]&&ty[i]==y[y.length-1])witness=true;
                    }
                    require(witness,"explicit trunk/branch contact witness retained");
                }
                escapes(layout,trace,trace.getStartPadId()); if(trace.getEndPadId()!=null)escapes(layout,trace,trace.getEndPadId());
            }
            System.out.println("P04_CORPUS {\"family\":\""+family+"\",\"seed\":\""+seed+"\",\"attempts\":"+attempts+
                ",\"rawSegments\":"+before.segments+",\"canonicalSegments\":"+after.segments+",\"expansions\":"+layout.getRoutingExpansions()+
                ",\"length\":"+after.length+",\"uniqueLength\":"+after.uniqueLength+",\"reusedLength\":"+after.reusedLength+",\"bends\":"+after.bends+
                ",\"congestionRejections\":"+layout.getRoutingCongestionRejections()+
                ",\"elapsedMs\":"+((System.nanoTime()-start)/1000000.0)+"}");
            Vector<PcbTraceGeometry> connected=layout.getTraces();
            for(PcbTraceGeometry branch:connected) if(branch.getEndPadId()==null) {
                Vector<PcbTraceGeometry> orphan=new Vector<PcbTraceGeometry>(connected);orphan.remove(branch);
                layout.replaceTraces(orphan);boolean rejected=false;
                try {layout.captureConductorGraph(board).pristine().requirePristineNetConnectivity(board);}
                catch(IllegalStateException expected){rejected=true;}
                require(rejected,"orphan branch cannot satisfy connectivity by net label");
                layout.replaceTraces(connected);break;
            }
        }
        require(branches>0,"actual multi-terminal tree branches exercised"); negatives(); heuristicOracle();
        System.out.println("PASS: P04 routing contracts assertions="+assertions+" branches="+branches);
    }
    static TroubleshootBoard board(String family) throws Exception {
        if(family.equals("led"))return TroubleshootBoardFixtures.createLedIndicatorBoard();
        Object generator=family.equals("diode")?new DiodeProtectedIndicatorGenerator():new ParallelDualIndicatorGenerator();
        Method method=generator.getClass().getDeclaredMethod("createBoard");method.setAccessible(true);return (TroubleshootBoard)method.invoke(generator);
    }
    private static TreeSet<String> raster(PcbBoardLayout layout) {
        TreeSet<String> result=new TreeSet<String>();
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) {
                int dx=Integer.signum(x[i]-x[i-1]),dy=Integer.signum(y[i]-y[i-1]);
                int n=Math.abs(x[i]-x[i-1])+Math.abs(y[i]-y[i-1]);
                for(int step=0;step<=n;step++)result.add(trace.getNetId()+"/"+trace.getLayer()+"/"+(x[i-1]+dx*step)+","+(y[i-1]+dy*step));
            }
        }
        return result;
    }
    private static void escapes(PcbBoardLayout layout,PcbTraceGeometry trace,String padId) {
        PcbPadPlacement pad=layout.getPad(padId);int ex=pad.getX()+pad.getEscapeDx()*pad.getEscapeLength(),ey=pad.getY()+pad.getEscapeDy()*pad.getEscapeLength();
        int[] x=trace.getXPoints(),y=trace.getYPoints();boolean found=false;
        for(int i=0;i<x.length;i++)if(x[i]==ex&&y[i]==ey)found=true;
        require(found,"literal declared escape tip retained "+padId);
    }
    private static PcbTraceGeometry line(String source,int[] x,int[] y) {
        return new PcbTraceGeometry(source,"N",null,null,PcbCopperLayer.TOP,PcbCopperAccess.Exposure.EXPOSED,x,y);
    }
    private static void negatives() {
        Vector<PcbTraceGeometry> traces=new Vector<PcbTraceGeometry>();
        traces.add(line("a",new int[]{0,100},new int[]{0,0}));
        traces.add(line("b",new int[]{0,100},new int[]{0,0}));
        traces.add(line("c",new int[]{20,80},new int[]{0,0}));
        PcbRouteMetrics m=PcbRouteMetrics.measure(traces);
        require(m.uniqueLength==100&&m.reusedLength==100,"triple overlap counted once, no pairwise duplicate reward");
        traces.clear();traces.add(line("a",new int[]{0,100},new int[]{0,0}));traces.add(traces.get(0));
        m=PcbRouteMetrics.measure(traces);require(m.uniqueLength==100&&m.reusedLength==0,"duplicate source cannot manufacture reuse");
        TroubleshootBoard names=new TroubleshootBoard("role-negative");names.addNet(new BoardNet("GND"));names.addNet(new BoardNet("not-a-rail",BoardNet.RoutingRole.RETURN));
        require(PcbNetRouter.priority(names,"GND")==4&&PcbNetRouter.priority(names,"not-a-rail")==0,"typed role, not net spelling");
        require(PcbNetRouter.MINIMUM_STEP_COST==2,"heuristic uses actual discounted step floor");
        PcbBoardLayout empty=new PcbBoardLayout(500,400,new Rectangle(0,0,300,300),new Rectangle(330,20,150,250));
        empty.addTrace(line("zero",new int[]{50,50,100},new int[]{50,50,50}));
        boolean rejected=false;try{PcbRouteCanonicalizer.canonicalize(empty);}catch(IllegalArgumentException e){rejected=true;}
        require(rejected,"zero-length segment rejected before replacement");
        PcbBoardLayout detour=new PcbBoardLayout(600,500,new Rectangle(0,0,400,400),new Rectangle(450,20,120,300));
        detour.addTrace(line("around-body",new int[]{100,100,100,200,300,300,300},new int[]{150,100,50,50,50,100,150}));
        TreeSet<String> original=raster(detour);PcbRouteCanonicalizer.canonicalize(detour);
        require(original.equals(raster(detour)),"simplification cannot replace bends with a courtyard shortcut");
        TreeSet<String> copper=raster(detour);
        for(int x=180;x<=240;x++)for(int y=100;y<=200;y++)
            require(!copper.contains("N/TOP/"+x+","+y),"literal protected courtyard stays empty");
    }
    private static void heuristicOracle() throws Exception {
        // Invoke the selected router's actual distance field. An independent
        // weighted Dijkstra oracle includes discounted copper, costs 7/10 and walls.
        Class<?> type=Class.forName("com.lushprojects.circuitjs1.client.PcbNetRouter$Router");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Rectangle outline=new Rectangle(0,0,140,100);int width=13,height=9,n=width*height;
        Object router=constructor.newInstance(new PcbBoardLayout(400,300,outline,new Rectangle(180,20,150,200)),
            new TroubleshootBoard("heuristic"),outline,0,new SeededPcbLayoutGenerator.AttemptObserver(){public void check(int attempt){}});
        boolean[] goals=new boolean[n];goals[0]=true;goals[n-1]=true;
        Method method=type.getDeclaredMethod("distanceField",boolean[].class);method.setAccessible(true);
        int[] distance=(int[])method.invoke(router,(Object)goals);
        double[] exact=new double[n];boolean[] settled=new boolean[n];java.util.Arrays.fill(exact,Double.POSITIVE_INFINITY);
        exact[0]=exact[n-1]=0;
        for(int step=0;step<n;step++) {
            int at=-1;for(int i=0;i<n;i++)if(!settled[i]&&(at<0||exact[i]<exact[at]))at=i;
            if(at<0||Double.isInfinite(exact[at]))break;settled[at]=true;
            int x=at%width,y=at/width;
            for(int next:new int[]{x>0?at-1:-1,x+1<width?at+1:-1,y>0?at-width:-1,y+1<height?at+width:-1}) {
                if(next<0||next%width==6&&next/width>1&&next/width<7)continue;
                int cost=y==0||y==8?2:(x%3==0?7:10);
                exact[next]=Math.min(exact[next],exact[at]+cost);
            }
        }
        boolean oldOverestimated=false;
        for(int i=0;i<n;i++)if(!Double.isInfinite(exact[i])) {
            require(distance[i]*PcbNetRouter.MINIMUM_STEP_COST<=exact[i],"actual heuristic is a lower bound with reuse and obstacles");
            oldOverestimated|=distance[i]*10>exact[i];
        }
        require(oldOverestimated,"independent oracle detects former grid-cost overestimate");
        boolean rejected=false;
        try{method.invoke(router,(Object)new boolean[n]);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof PcbNetRouter.Rejected;}
        require(rejected,"no connected tree goal is a typed orphan rejection");
    }
}
