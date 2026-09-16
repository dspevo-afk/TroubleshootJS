package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Vector;

/** Current-contract brute-force parity, hostile edits and physical-only scale measurements. */
public final class P08ScalabilityContractTest {
    private static int checks;
    private static void require(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    private static void reject(Runnable action,String message) {
        boolean rejected=false;
        try { action.run(); } catch(IllegalArgumentException expected) { rejected=true; }
        catch(IllegalStateException expected) { rejected=true; }
        require(rejected,message);
    }
    public static void main(String[] args) throws Exception {
        spatialParity();contactParity();cacheAndEditNegatives();
        for(int size:new int[]{15,30,56,100})benchmark(size);
        for(String id:new String[]{"RB15","RB30","RB56","RB100"}) {
            P08StructuralFixtures mixed=new P08StructuralFixtures(id);
            benchmark("mixed-"+id,new Fixture(mixed.board,mixed.layout));
        }
        denseBenchmark();
        System.out.println("PASS: P08 scalable physical contracts assertions="+checks);
    }
    private static void spatialParity() {
        PcbSpatialIndex empty=new PcbSpatialIndex(new ArrayList<PcbSpatialIndex.Box>(),new int[0]);
        require(empty.query(new PcbSpatialIndex.Box(0,0,0,0),3).length==0,"empty physical index");
        Random random=new Random(801);
        ArrayList<PcbSpatialIndex.Box> boxes=new ArrayList<PcbSpatialIndex.Box>();
        int[] masks=new int[180];
        for(int i=0;i<masks.length;i++) {
            long x=random.nextInt(2048)-1024,y=random.nextInt(2048)-1024;
            boxes.add(new PcbSpatialIndex.Box(x,y,x+(i%11==0?20000:random.nextInt(140)),
                y+random.nextInt(140)));masks[i]=1+random.nextInt(3);
        }
        boxes.set(0,new PcbSpatialIndex.Box(-64,-64,64,64));masks[0]=3;
        PcbSpatialIndex index=new PcbSpatialIndex(boxes,masks);
        for(int q=0;q<600;q++) {
            int x=q<20?(q-10)*64:random.nextInt(3000)-1500;
            int y=q<20?64:random.nextInt(3000)-1500;
            PcbSpatialIndex.Box area=new PcbSpatialIndex.Box(x,y,x+(q%31==0?24000:random.nextInt(160)),y+random.nextInt(160));
            int mask=1+random.nextInt(3);ArrayList<Integer> expected=new ArrayList<Integer>();
            for(int i=0;i<boxes.size();i++) {
                PcbSpatialIndex.Box b=boxes.get(i);
                if((mask&masks[i])!=0 && b.left<=area.right && b.right>=area.left &&
                        b.top<=area.bottom && b.bottom>=area.top)expected.add(i);
            }
            int[] actual=index.query(area,mask);
            require(actual.length==expected.size(),"closed-boundary/wide-interval candidate completeness");
            for(int i=0;i<actual.length;i++)require(actual[i]==expected.get(i),"canonical exact candidate order");
        }
        PcbSpatialIndex.Box boundary=new PcbSpatialIndex.Box(64,64,64,64);
        int[] frozen=index.query(boundary,3);boxes.clear();Arrays.fill(masks,0);
        require(Arrays.equals(frozen,index.query(boundary,3)),"caller arrays/list cannot mutate index");
        boxes=new ArrayList<PcbSpatialIndex.Box>();masks=new int[600];Arrays.fill(masks,3);
        for(int i=0;i<600;i++)boxes.add(new PcbSpatialIndex.Box(0,0,63,16319));
        PcbSpatialIndex capped=new PcbSpatialIndex(boxes,masks);
        require(capped.statistics.references==600 && capped.statistics.nodes==600,
            "wide/coincident bounds retain linear index storage");
        require(capped.query(new PcbSpatialIndex.Box(63,64,63,64),1).length==600,"wide intervals lose no boundary contacts");
    }
    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        Fixture(int size) { this(size,P06FactoryLinkFixtures.TEST_POINT); }
        Fixture(TroubleshootBoard board,PcbBoardLayout layout) { this.board=board;this.layout=layout; }
        Fixture(int size,PhysicalPackage physical) {
            board=new TroubleshootBoard("P08_STRUCTURAL_ONLY");
            int rows=(size+1)/2,height=Math.max(700,rows*80+360);
            layout=new PcbBoardLayout(1200,height,new Rectangle(100,100,600,height-200),new Rectangle(900,120,200,400));
            for(int i=0;i<size;i++) {
                String net="N"+(i/2),id="P"+i;
                if(board.getNet(net)==null)board.addNet(new BoardNet(net));
                BoardComponent part=new BoardComponent(id,"STRUCTURAL",physical);board.addComponent(part);
                board.addPad(new BoardPad(id+".1",id,"1",net));
                int x=i%2==0?200:600,y=200+(i/2)*80;
                PcbPackagePose reference=new PcbPackagePose(100,100,PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
                Point padPoint=physical.getGeometry().placedAt(reference).getPadPoint(0);
                PcbPackagePose pose=new PcbPackagePose(100+x-padPoint.x,100+y-padPoint.y,PcbRotation.DEG_0,PcbBoardSide.BOTTOM);
                PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(part,pose,physical.getGeometry());
                layout.addComponent(footprint.getPlacement());
                for(PcbPadPlacement pad:footprint.getPads())layout.addPad(pad);
                layout.addSilkscreenLabel(new PcbSilkscreenLabel("component:"+id,id,
                    new Rectangle(x-10,y-30,36,12),10,true,null));
                if(i%2==1)layout.addTrace(new PcbTraceGeometry("route/"+net,net,"P"+(i-1)+".1",id+".1",
                    PcbCopperLayer.TOP,PcbCopperAccess.Exposure.EXPOSED,new int[]{200,600},new int[]{y,y}));
            }
            board.validate();
        }
    }
    private static PcbConductorGraph capture(TroubleshootBoard board,PcbBoardLayout layout,boolean reference) {
        try { return reference?P08ReferenceConductorBuilder.capture(board,layout):layout.captureConductorGraph(board); }
        catch(IllegalArgumentException expected) { return null; }
        catch(IllegalStateException expected) { return null; }
    }
    private static void compare(TroubleshootBoard board,PcbBoardLayout layout) {
        PcbConductorGraph fast=capture(board,layout,false),reference=capture(board,layout,true);
        require((fast==null)==(reference==null),"fast/reference conductor acceptance parity");
        if(fast!=null)require(fast.toCanonical().equals(reference.toCanonical()),"exact graph/provenance parity");
        boolean a=true,b=true;
        try { layout.validateTraceClearance(); } catch(IllegalStateException expected) { a=false; }
        try { referenceClearance(layout.getTraces()); } catch(IllegalStateException expected) { b=false; }
        require(a==b,"independent exact-clearance parity");
    }
    private static void contactParity() {
        for(int seed=0;seed<140;seed++) {
            Random random=new Random(seed);TroubleshootBoard board=new TroubleshootBoard("P08_ADVERSARIAL");
            board.addNet(new BoardNet("A"));board.addNet(new BoardNet("B"));
            PcbBoardLayout layout=new PcbBoardLayout(1000,700,new Rectangle(100,100,600,400),new Rectangle(780,120,180,400));
            for(int i=0;i<24;i++) {
                int x=128+random.nextInt(20)*8,y=128+random.nextInt(20)*8,length=8+random.nextInt(16)*8;
                boolean horizontal=random.nextBoolean();
                layout.addTrace(new PcbTraceGeometry("s"+i,seed%3==0 && i%4==0?"B":"A",null,null,
                    i%3==0?PcbCopperLayer.BOTTOM:PcbCopperLayer.TOP,PcbCopperAccess.Exposure.EXPOSED,
                    new int[]{x,horizontal?x+length:x},new int[]{y,horizontal?y:y+length}));
            }
            compare(board,layout);
        }
        final Fixture diagonal=new Fixture(2);
        Vector<PcbTraceGeometry> bad=new Vector<PcbTraceGeometry>();
        bad.add(new PcbTraceGeometry("diagonal","N0",null,null,PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{128,192},new int[]{128,192}));
        diagonal.layout.replaceTraces(bad);compare(diagonal.board,diagonal.layout);
        reject(new Runnable(){public void run(){diagonal.layout.validateTraceClearance();}},"malformed lone segment cannot bypass broad phase");
        for(int offset:new int[]{-1,0,1}) {
            Fixture f=new Fixture(4);Vector<PcbTraceGeometry> traces=f.layout.getTraces();
            int y=200+PcbTraceRules.MIN_CENTERLINE_CLEARANCE+offset;
            traces.add(new PcbTraceGeometry("boundary","N1",null,null,PcbCopperLayer.TOP,
                PcbCopperAccess.Exposure.EXPOSED,new int[]{250,550},new int[]{y,y}));
            f.layout.replaceTraces(traces);compare(f.board,f.layout);
        }
    }
    private static long referenceClearance(List<PcbTraceGeometry> traces) {
        long pairs=0,minimum=(long)PcbTraceRules.MIN_CENTERLINE_CLEARANCE*PcbTraceRules.MIN_CENTERLINE_CLEARANCE;
        for(PcbTraceGeometry trace:traces) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++)if(x[i]!=x[i-1] && y[i]!=y[i-1])
                throw new IllegalStateException("Independent non-Manhattan rejection");
        }
        for(int a=0;a<traces.size();a++)for(int b=a+1;b<traces.size();b++) {
            PcbTraceGeometry first=traces.get(a),second=traces.get(b);
            if(first.getLayer()!=second.getLayer() || first.getNetId().equals(second.getNetId()))continue;
            int[] ax=first.getXPoints(),ay=first.getYPoints(),bx=second.getXPoints(),by=second.getYPoints();
            for(int i=1;i<ax.length;i++)for(int j=1;j<bx.length;j++) {
                pairs++;
                long dx=Math.max(0L,Math.max((long)Math.min(ax[i-1],ax[i])-Math.max(bx[j-1],bx[j]),
                    (long)Math.min(bx[j-1],bx[j])-Math.max(ax[i-1],ax[i])));
                long dy=Math.max(0L,Math.max((long)Math.min(ay[i-1],ay[i])-Math.max(by[j-1],by[j]),
                    (long)Math.min(by[j-1],by[j])-Math.max(ay[i-1],ay[i])));
                if(dx*dx+dy*dy<minimum)throw new IllegalStateException("Independent copper clearance rejection");
            }
        }
        return pairs;
    }
    private static PcbConductorGraph.Surface traceSurface(PcbConductorGraph graph) {
        for(PcbConductorGraph.Surface s:graph.getSurfaces())if(s.edgeId!=null)return s;
        throw new AssertionError("Fixture has no copper");
    }
    private static void cacheAndEditNegatives() {
        final Fixture f=new Fixture(2);f.layout.validateGeometry(f.board);
        int[] xs={200,600},ys={200,200};Vector<PcbTraceGeometry> traces=new Vector<PcbTraceGeometry>();
        PcbTraceGeometry trace=new PcbTraceGeometry("route/N0","N0","P0.1","P1.1",PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,xs,ys);traces.add(trace);f.layout.replaceTraces(traces);
        PcbConductorGraph graph=f.layout.captureConductorGraph(f.board);String canonical=graph.toCanonical();
        PcbConductorGraph.Snapshot copper=graph.pristine();
        require(copper==graph.pristine(),"immutable pristine partition reused");
        xs[1]=500;ys[1]=220;trace.getXPoints()[0]=999;
        require(canonical.equals(f.layout.captureConductorGraph(f.board).toCanonical()),"arrays cannot change geometry under cached identity");
        PcbCopperViewCache cache=new PcbCopperViewCache();
        PcbViewport.Transform view=new PcbViewport.Transform(1,5,7,800,false);
        List<PcbCopperViewCache.Entry> first=cache.entries(copper,view,PcbBoardSide.TOP,1);
        require(first.size()==1,"one exposed top copper projection");
        require(first==cache.entries(copper,new PcbViewport.Transform(1,5,7,800,false),PcbBoardSide.TOP,1),"equal view values reuse one cache entry");
        Rectangle original=first.get(0).bounds();first.get(0).bounds().x=999;
        Point marker=first.get(0).marker();first.get(0).marker().x=999;
        require(first.get(0).bounds().equals(original) && first.get(0).marker().equals(marker),"cached projection values are defensive");
        int builds=cache.getBuildCount();
        for(PcbViewport.Transform changed:new PcbViewport.Transform[]{
                new PcbViewport.Transform(2,5,7,800,false),new PcbViewport.Transform(1,6,7,800,false),
                new PcbViewport.Transform(1,5,8,800,false),new PcbViewport.Transform(1,5,7,801,false),
                new PcbViewport.Transform(1,5,7,800,true)}) {
            cache.entries(copper,changed,PcbBoardSide.TOP,1);
            require(cache.getBuildCount()==++builds,"every camera dependency invalidates projection");
        }
        require(cache.entries(copper,view,PcbBoardSide.BOTTOM,1).isEmpty(),"face change never exposes hidden copper");builds++;
        cache.entries(copper,view,PcbBoardSide.BOTTOM,2);
        require(cache.getBuildCount()==++builds,"policy epoch invalidates even identical empty output");
        PcbConductorGraph.Surface surface=traceSurface(graph);
        final PcbConductorGraph.Snapshot cut=copper.withCut(surface.edgeId,true);
        require(cache.entries(cut,view,PcbBoardSide.TOP,1).isEmpty(),"cut state invalidates painted/probe copper");
        require(!cut.padsConnected("P0.1","P1.1"),"full partition detects a topology split");
        require(!cut.firstPadAt(graph.getTerminalJunctions().get("P0.1")).equals(
            cut.firstPadAt(graph.getTerminalJunctions().get("P1.1"))),"probe cache follows both new islands");
        final Map<String,Integer> observed=new TreeMap<String,Integer>();observed.put("P0.1",0);observed.put("P1.1",0);
        PcbConductorProjection.requireObservation(copper,observed);
        reject(new Runnable(){public void run(){PcbConductorProjection.requireObservation(cut,observed);}},"local cut cannot retain old solver equivalence");
        require(cache.entries(cut.withCut(surface.edgeId,false),view,PcbBoardSide.TOP,1).size()==1,"restoration builds current projection");
        f.layout.compactToContent(140,140,24);
        PcbConductorGraph moved=f.layout.captureConductorGraph(f.board);builds=cache.getBuildCount();
        cache.entries(moved.pristine(),view,PcbBoardSide.TOP,1);
        require(cache.getBuildCount()==builds+1 && !canonical.equals(moved.toCanonical()),"changed pose cannot reuse old projection");
        Fixture other=new Fixture(2,alternatePoint());PcbConductorGraph repackaged=other.layout.captureConductorGraph(other.board);
        require(canonical.equals(repackaged.toCanonical()),"alternate package has intentionally identical copper for alias falsifier");
        builds=cache.getBuildCount();cache.entries(repackaged.pristine(),view,PcbBoardSide.TOP,1);
        require(cache.getBuildCount()==builds+1,"different package owner cannot hit on equal canonical/hash data");
        require(!PcbCopperProbeAccess.available(repackaged.pristine(),surface,PcbBoardSide.TOP),"foreign same-ID surface rejected");
        Vector<PcbTraceGeometry> bottom=new Vector<PcbTraceGeometry>();
        bottom.add(new PcbTraceGeometry("route/N0","N0","P0.1","P1.1",PcbCopperLayer.BOTTOM,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{200,600},new int[]{200,200}));other.layout.replaceTraces(bottom);
        PcbConductorGraph changedLayer=other.layout.captureConductorGraph(other.board);
        require(cache.entries(changedLayer.pristine(),view,PcbBoardSide.TOP,1).isEmpty(),"layer edit invalidates top projection");
        require(cache.entries(changedLayer.pristine(),view,PcbBoardSide.BOTTOM,1).size()==1,"layer edit rebuilds bottom projection");
        final Fixture nonlocal=new Fixture(4);nonlocal.layout.validateGeometry(nonlocal.board);
        nonlocal.layout.captureConductorGraph(nonlocal.board).pristine();
        nonlocal.layout.addTrace(new PcbTraceGeometry("outside-dirty-region","N0",null,null,PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{400,400},new int[]{200,280}));
        compare(nonlocal.board,nonlocal.layout);
        reject(new Runnable(){public void run(){nonlocal.layout.validateRoutingGeometry(nonlocal.board);}},"new remote cross-net contact requires global validation");
        viaDeletion();projectionParity();
    }
    private static PhysicalPackage alternatePoint() {
        PhysicalPackageGeometry geometry=P06FactoryLinkFixtures.TEST_POINT.getGeometry();
        Vector<PhysicalPackage.GeometryVariant> variants=new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT","IDENTITY",geometry));
        Vector<PcbRotation> rotations=new Vector<PcbRotation>();rotations.add(PcbRotation.DEG_0);
        Vector<PcbBoardSide> sides=new Vector<PcbBoardSide>();sides.add(PcbBoardSide.TOP);sides.add(PcbBoardSide.BOTTOM);
        return new PhysicalPackage("P08_ALTERNATE_POINT",P06FactoryLinkFixtures.TEST_POINT.getTerminalIds(),
            new Vector<String>(),false,geometry,variants,"DEFAULT",PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,rotations,sides);
    }
    private static void viaDeletion() {
        final P06FactoryLinkFixtures.Fixture f=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(f.board,f.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER);
        require(result.accepted() && result.vias==2,"real two-layer fixture");compare(f.board,result.layout);
        PcbConductorGraph.Snapshot original=result.layout.captureConductorGraph(f.board).pristine();
        PcbConductorGraph.Surface via=null;
        for(PcbConductorGraph.Surface s:original.getGraph().getSurfaces())if(s.id.startsWith("hole/")){via=s;break;}
        require(via!=null,"via surface present before deletion");
        PcbCopperViewCache cache=new PcbCopperViewCache();PcbViewport.Transform view=new PcbViewport.Transform(1,0,0,800,false);
        cache.entries(original,view,via.layer.getFace(),1);
        final PcbBoardLayout removed=f.layout.copyForRouting();
        for(PcbTraceGeometry trace:result.layout.getTraces())removed.addTrace(trace);
        PcbConductorGraph.Snapshot next=removed.captureConductorGraph(f.board).pristine();compare(f.board,removed);
        require(cache.get(next,view,via.layer.getFace(),1,via.id)==null,"deleted via evicted from projection cache");
        require(!PcbCopperProbeAccess.available(next,via,via.layer.getFace()),"stale deleted via cannot be probed");
        reject(new Runnable(){public void run(){removed.validateRoutingGeometry(f.board);}},"deleted via triggers global connectivity rejection");
    }
    private static void projectionParity() {
        Fixture f=new Fixture(4);PcbConductorGraph graph=f.layout.captureConductorGraph(f.board);
        String edge=traceSurface(graph).edgeId;
        for(PcbConductorGraph.Snapshot copper:new PcbConductorGraph.Snapshot[]{graph.pristine(),graph.pristine().withCut(edge,true)}) {
            List<String> pads=new ArrayList<String>(graph.getTerminalJunctions().keySet());
            for(int code=0;code<256;code++) {
                Map<String,Integer> observed=new TreeMap<String,Integer>();int value=code;
                for(String pad:pads){observed.put(pad,value%4);value/=4;}
                boolean expected=true,actual=true;
                for(int i=0;i<pads.size();i++)for(int j=i+1;j<pads.size();j++)
                    if(copper.padsConnected(pads.get(i),pads.get(j))!=observed.get(pads.get(i)).equals(observed.get(pads.get(j))))expected=false;
                try { PcbConductorProjection.requireObservation(copper,observed); }
                catch(IllegalStateException rejected) { actual=false; }
                require(actual==expected,"linear solver/physical bijection agrees with independent all-pairs oracle");
            }
        }
    }
    private static long allocated() {
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();
        if(!(base instanceof com.sun.management.ThreadMXBean))return -1;
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;
        if(!bean.isThreadAllocatedMemorySupported())return -1;
        if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        return bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
    private static long median(long[] values) { Arrays.sort(values);return values[values.length/2]; }
    private static long[] measure(Fixture f,boolean reference) {
        long memory=allocated(),started=System.nanoTime();
        PcbConductorGraph graph=reference?P08ReferenceConductorBuilder.capture(f.board,f.layout):f.layout.captureConductorGraph(f.board);
        graph.pristine().requirePristineNetConnectivity(f.board);
        if(reference)referenceClearance(f.layout.getTraces());else f.layout.validateTraceClearance();
        long nanos=System.nanoTime()-started,after=allocated();
        return new long[]{nanos,memory<0 || after<0?-1:after-memory};
    }
    private static void benchmark(int size) { benchmark("points-"+size,new Fixture(size)); }
    private static void benchmark(String profile,Fixture f) {
        int size=f.board.getComponentIds().size();
        f.layout.validateGeometry(f.board);compare(f.board,f.layout);
        for(int i=0;i<3;i++){measure(f,false);measure(f,true);}
        long[] fast=new long[7],reference=new long[7],fastBytes=new long[7],referenceBytes=new long[7];
        for(int i=0;i<7;i++) {
            long[] a,b;
            if(i%2==0){a=measure(f,false);b=measure(f,true);}else{b=measure(f,true);a=measure(f,false);}
            fast[i]=a[0];fastBytes[i]=a[1];reference[i]=b[0];referenceBytes[i]=b[1];
        }
        PcbConductorBuilder.Statistics stats=new PcbConductorBuilder.Statistics();
        PcbConductorGraph graph=PcbConductorBuilder.capture(f.board,f.layout,stats);
        PcbTraceClearance.Statistics clearance=PcbTraceClearance.validate(f.layout.getTraces());
        long referenceClearance=referenceClearance(f.layout.getTraces());
        require(stats.exactPairs()<stats.referencePairs/4,"sparse scale materially prunes contact candidates");
        PcbCopperViewCache cache=new PcbCopperViewCache();
        PcbBoardSide face=profile.startsWith("mixed-")?PcbBoardSide.BOTTOM:PcbBoardSide.TOP;
        PcbViewport.Transform view=new PcbViewport.Transform(.8,10,20,800,face==PcbBoardSide.BOTTOM);
        graph.pristine();
        long start=System.nanoTime();
        for(int i=0;i<1000;i++)cache.entries(graph.pristine(),view,face,1);
        long cachedNanos=System.nanoTime()-start;
        require(cache.getBuildCount()==1,"one retained projection for 1000 unchanged queries");
        start=System.nanoTime();
        for(int i=0;i<1000;i++)new PcbCopperViewCache().entries(graph.pristine(),view,face,1);
        long uncachedNanos=System.nanoTime()-start;long[] full=new long[7];
        for(int i=0;i<full.length;i++){start=System.nanoTime();f.layout.validateGeometry(f.board);full[i]=System.nanoTime()-start;}
        System.out.println("P08_SCALE profile="+profile+" components="+size+" segments="+stats.segments+" lands="+stats.lands+
            " exactContacts="+stats.exactPairs()+" referenceContacts="+stats.referencePairs+
            " broadPhaseBoxTests="+stats.broadPhaseBoxTests+" references="+stats.references+
            " indexNodes="+stats.nodes+" indexPrimitiveBytes="+stats.indexPayloadBytes+
            " exactClearance="+clearance.exactPairs+" referenceClearance="+referenceClearance+
            " fastMedianNanos="+median(fast)+" referenceMedianNanos="+median(reference)+
            " fastAllocatedBytes="+median(fastBytes)+" referenceAllocatedBytes="+median(referenceBytes)+
            " fullValidationMedianNanos="+median(full)+" cached1000Nanos="+cachedNanos+
            " uncached1000Nanos="+uncachedNanos+" retainedViewBuilds="+cache.getBuildCount());
    }
    private static void denseBenchmark() {
        TroubleshootBoard board=new TroubleshootBoard("P08_DENSE_ONLY");board.addNet(new BoardNet("A"));
        PcbBoardLayout layout=new PcbBoardLayout(1000,700,new Rectangle(100,100,600,400),new Rectangle(780,120,180,400));
        for(int i=0;i<128;i++)layout.addTrace(new PcbTraceGeometry("dense/"+i,"A",null,null,PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,new int[]{128,512},new int[]{200,200}));
        compare(board,layout);
        PcbConductorBuilder.Statistics stats=new PcbConductorBuilder.Statistics();long memory=allocated(),start=System.nanoTime();
        PcbConductorGraph fast=PcbConductorBuilder.capture(board,layout,stats);
        long fastNanos=System.nanoTime()-start,fastBytes=allocated()-memory;
        memory=allocated();start=System.nanoTime();
        PcbConductorGraph reference=P08ReferenceConductorBuilder.capture(board,layout);
        long referenceNanos=System.nanoTime()-start,referenceBytes=allocated()-memory;
        require(fast.toCanonical().equals(reference.toCanonical()),"dense shared provenance exact parity");
        require(stats.exactPairs()==8128 && stats.referencePairs==8128,"dense all-pairs worst case remains explicit");
        System.out.println("P08_DENSE segments=128 exactContacts="+stats.exactPairs()+" referenceContacts="+stats.referencePairs+
            " broadPhaseBoxTests="+stats.broadPhaseBoxTests+" references="+stats.references+
            " indexPrimitiveBytes="+stats.indexPayloadBytes+" fastNanos="+fastNanos+" referenceNanos="+referenceNanos+
            " fastAllocatedBytes="+fastBytes+" referenceAllocatedBytes="+referenceBytes);
    }
}
