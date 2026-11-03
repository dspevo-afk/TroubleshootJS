package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Vector;

/** Bounded, source-bound capture of one accepted P07 branch before and after normal publication. */
final class P07AcceptedBranchCaptureDiagnostics {
    private static final int MAX_ADVANCE_WORK=32768;
    private static final long MAX_TOTAL_WORK=640L*MAX_ADVANCE_WORK;
    private static final int MAX_PATH_NODES=4096;
    private static final int MAX_GRID_CELLS=250000;
    private static final int MAX_HOLES=48;
    private static final int MAX_TRACES=100000;
    private static final int MAX_TRACE_POINTS=4096;
    private static final int MAX_ID_LENGTH=4096;
    private static final int TIME_WIDTH=19;
    private static final int UNITS_WIDTH=19;
    private static final int WORK_WIDTH=19;
    private static final int CALL_WIDTH=19;
    private static final int GRID=PcbLayerRoutingPrototype.GRID;
    private static final String JSON_HEX="0123456789abcdef";

    private P07AcceptedBranchCaptureDiagnostics() { }

    static SeedSnapshot captureInitialSeeds(Object search,Object branch) {
        long started=System.nanoTime();
        SeedSnapshot result=new SeedSnapshot(search,branch);
        result.captureNanos=Math.max(0L,System.nanoTime()-started);
        return result;
    }

    static final class SeedSnapshot {
        final Object searchRef,branchRef,routeSessionRef,layoutRef,queueRef;
        final Object[] nodeRefs;
        final int[][] nodeValues;
        final int pass,netIndexNextFetch,branchOrdinal,branches,traceCount,traceModCount,faceMask;
        final String currentNet;
        final String[] netOrder;
        final PcbBoardHole[] prefixHoleRefs;
        final Object padsMap,componentsMap,labelsMap,regionsMap,holesMap,tracesVector,outlineRef,partsTrayRef,
            netOrderVectorRef;
        final int width,height,layoutAlgorithmVersion,outlineX,outlineY,outlineWidth,outlineHeight;
        final int entryCaptureWork;
        long captureNanos;
        private final String json;

        private SeedSnapshot(Object search,Object branch) {
            if(search==null || branch==null)
                throw new IllegalArgumentException("Missing P07 branch-entry source");
            searchRef=search; branchRef=branch;
            routeSessionRef=field(search,"owner");
            layoutRef=field(search,"layout");
            queueRef=field(branch,"queue");
            if(routeSessionRef==null || layoutRef==null || queueRef==null ||
                    field(search,"branch")!=branch || field(search,"publisher")!=null)
                throw new IllegalArgumentException("P07 seed capture is not at branch entry");
            pass=intField(search,"pass");
            netIndexNextFetch=intField(search,"netIndex");
            branchOrdinal=intField(branch,"branchOrdinal");
            branches=intField(search,"branches");
            currentNet=stringField(search,"currentNet");
            if(pass<0 || pass>=PcbLayerRoutingPrototype.ORDERINGS || branchOrdinal<1 ||
                    branchOrdinal!=branches || currentNet==null || netIndexNextFetch<1)
                throw new IllegalArgumentException("Invalid P07 branch-entry metadata");
            Object nets=field(search,"nets");
            if(!(nets instanceof Vector)) throw new IllegalArgumentException("Missing P07 source net order");
            netOrderVectorRef=nets;
            Vector<?> sourceNets=(Vector<?>)nets;
            if(sourceNets.size()<1 || sourceNets.size()>256 || netIndexNextFetch>sourceNets.size())
                throw new IllegalArgumentException("P07 source net order exceeds capture bounds");
            netOrder=new String[sourceNets.size()];
            int captureWork=2;
            for(int i=0;i<netOrder.length;i++) {
                Object value=sourceNets.get(i);
                if(!(value instanceof String)) throw new IllegalArgumentException("Invalid P07 source net ID");
                netOrder[i]=validString((String)value,"source net");
                captureWork++;
            }
            if(!currentNet.equals(netOrder[netIndexNextFetch-1]))
                throw new IllegalArgumentException("P07 branch net does not match next-fetch position");
            Object rawHoles=field(branch,"holes");
            if(!(rawHoles instanceof PcbBoardHole[]))
                throw new IllegalArgumentException("Missing immutable P07 branch hole prefix");
            prefixHoleRefs=(PcbBoardHole[])rawHoles;
            if(prefixHoleRefs.length>MAX_HOLES)
                throw new IllegalArgumentException("P07 branch prefix exceeds the via inventory cap");

            int queueSize=intField(queueRef,"size");
            Object queueValues=field(queueRef,"values");
            if(queueSize!=2 || queueValues==null || Array.getLength(queueValues)<queueSize)
                throw new IllegalArgumentException("P07 branch entry must retain both actual face seeds");
            nodeRefs=new Object[queueSize]; nodeValues=new int[queueSize][8];
            int seedMask=0;
            for(int i=0;i<queueSize;i++) {
                Object node=Array.get(queueValues,i);
                if(node==null || field(node,"previous")!=null)
                    throw new IllegalArgumentException("P07 face seed is not an actual root Node");
                int[] values=readNode(node);
                if(values[0]<0 || values[0]>=intField(search,"cells") ||
                        values[1]<0 || values[1]>1 || values[2]!=4 || values[3]!=0 ||
                        values[7]!=nodeKey(values[0],values[1],values[2],values[3],intField(search,"cells")))
                    throw new IllegalArgumentException("Malformed P07 face seed Node");
                int bit=1<<values[1];
                if((seedMask&bit)!=0) throw new IllegalArgumentException("Duplicate P07 seed face");
                seedMask|=bit; nodeRefs[i]=node; nodeValues[i]=values; captureWork++;
            }
            if(seedMask!=3) throw new IllegalArgumentException("P07 seed queue does not cover both copper faces");
            faceMask=seedMask;

            int gridWidth=intField(search,"width"),gridHeight=intField(search,"height");
            long gridCells=(long)gridWidth*gridHeight;
            if(gridWidth<1 || gridHeight<1 || gridCells>MAX_GRID_CELLS ||
                    gridCells!=intField(search,"cells"))
                throw new IllegalArgumentException("Invalid P07 route grid at branch entry");
            Object layout=layoutRef;
            padsMap=field(layout,"pads"); componentsMap=field(layout,"components");
            labelsMap=field(layout,"silkscreenLabels"); regionsMap=field(layout,"regions");
            holesMap=field(layout,"holes"); tracesVector=field(layout,"traces");
            outlineRef=field(layout,"boardOutline"); partsTrayRef=field(layout,"partsTray");
            if(!(tracesVector instanceof Vector) || !(holesMap instanceof Map) ||
                    !(outlineRef instanceof Rectangle) || !(partsTrayRef instanceof Rectangle))
                throw new IllegalArgumentException("P07 branch-entry layout authority is incomplete");
            traceCount=((Vector<?>)tracesVector).size();
            if(traceCount>MAX_TRACES) throw new IllegalArgumentException("P07 prefix trace count exceeds cap");
            traceModCount=vectorModCount((Vector<?>)tracesVector);
            width=intField(layout,"width"); height=intField(layout,"height");
            layoutAlgorithmVersion=intField(layout,"layoutAlgorithmVersion");
            Rectangle outline=(Rectangle)outlineRef;
            outlineX=outline.x; outlineY=outline.y; outlineWidth=outline.width; outlineHeight=outline.height;
            int queueFaceMask=seedMask;
            StringBuilder encoded=new StringBuilder(256);
            encoded.append("{\"schema\":\"p07-accepted-branch-seeds-r1\",\"queueSize\":")
                .append(queueSize).append(",\"faceMask\":").append(queueFaceMask).append(",\"nodes\":[");
            for(int i=0;i<queueSize;i++) {
                if(i>0) encoded.append(',');
                int[] v=nodeValues[i];
                encoded.append("{\"heapIndex\":").append(i).append(",\"cell\":").append(v[0])
                    .append(",\"x\":").append(gridX(search,v[0])).append(",\"y\":")
                    .append(gridY(search,v[0])).append(",\"layer\":").append(v[1])
                    .append(",\"direction\":").append(v[2]).append(",\"transitions\":").append(v[3])
                    .append(",\"cost\":").append(v[4]).append(",\"heuristic\":").append(v[5])
                    .append(",\"serial\":").append(v[6]).append(",\"key\":").append(v[7]).append('}');
            }
            encoded.append("]}");
            json=encoded.toString();
            entryCaptureWork=captureWork;
        }

        int nodeCount() { return nodeRefs.length; }
        boolean containsNodeIdentity(Object node) {
            for(Object seed:nodeRefs) if(seed==node) return true;
            return false;
        }
        String toJson() { return json; }
    }

    static final class Context {
        final Object routeSession,search,branch,publisher;
        final SeedSnapshot seeds;
        final PcbBoardLayout layout;
        final TroubleshootBoard board;
        final PcbBoardHole[] prefixHoles;
        final Object reachedPadIdsRef;
        final String[] netOrder;
        final String netId;
        final int pass,netIndexNextFetch,netPosition,branchOrdinal,maximumVias,maximumTransitions,
            entryVerificationWork;
        final PcbPadPlacement start,root;
        final PcbLayerRoutingPrototype.Policy policy;
        final Thread ownerThread;
        final EntryAuthority entry;
        int preReachedStartCount;
        long contextNanos;
        PreResult preResult;
        boolean postStarted;
        int postTraceCount=-1,postTraceModCount=-1,postHoleCount=-1;

        Context(Object routeSession,Object search,Object branch,Object publisher,SeedSnapshot seeds) {
            long started=System.nanoTime();
            if(routeSession==null || search==null || branch==null || publisher==null || seeds==null)
                throw new IllegalArgumentException("Missing P07 branch capture context");
            this.routeSession=routeSession; this.search=search; this.branch=branch;
            this.publisher=publisher; this.seeds=seeds; ownerThread=Thread.currentThread();
            if(routeSession!=seeds.routeSessionRef || search!=seeds.searchRef || branch!=seeds.branchRef ||
                    field(search,"owner")!=routeSession || field(search,"layout")!=seeds.layoutRef ||
                    field(routeSession,"search")!=search || field(search,"branch")!=null ||
                    field(search,"publisher")!=publisher || field(publisher,"end")!=field(branch,"found") ||
                    field(publisher,"endTerminal")!=field(branch,"foundTerminal"))
                throw new IllegalArgumentException("P07 context does not bind the retained source branch");
            Object searchPolicy=field(search,"policy");
            if(!(searchPolicy instanceof PcbLayerRoutingPrototype.Policy))
                throw new IllegalArgumentException("Missing P07 actual routing policy");
            policy=(PcbLayerRoutingPrototype.Policy)searchPolicy;
            if(policy!=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER)
                throw new IllegalArgumentException("Accepted branch capture requires FULLER_TWO_LAYER");
            layout=(PcbBoardLayout)field(search,"layout");
            board=(TroubleshootBoard)field(search,"board");
            if(board==null) throw new IllegalArgumentException("Missing actual P07 source board");
            pass=intField(search,"pass"); netId=stringField(search,"currentNet");
            netIndexNextFetch=intField(search,"netIndex"); netPosition=netIndexNextFetch-1;
            branchOrdinal=intField(branch,"branchOrdinal"); maximumVias=intField(search,"maximumVias");
            maximumTransitions=policy.transitions;
            Object sourceNets=field(search,"nets");
            if(sourceNets!=seeds.netOrderVectorRef || vectorSize(sourceNets)!=seeds.netOrder.length)
                throw new IllegalArgumentException("P07 source net vector changed after branch entry");
            for(int i=0;i<seeds.netOrder.length;i++) {
                Object value=((Vector<?>)sourceNets).get(i);
                if(!(value instanceof String) || !seeds.netOrder[i].equals(value))
                    throw new IllegalArgumentException("P07 source net order changed after branch entry");
            }
            netOrder=seeds.netOrder;
            entryVerificationWork=2*(netOrder.length+seeds.prefixHoleRefs.length);
            if(pass!=seeds.pass || !netId.equals(seeds.currentNet) ||
                    netIndexNextFetch!=seeds.netIndexNextFetch || branchOrdinal!=seeds.branchOrdinal ||
                    intField(search,"branches")!=seeds.branches ||
                    netPosition<0 || netPosition>=netOrder.length || !netOrder[netPosition].equals(netId) ||
                    maximumVias<1 || maximumVias>48 || maximumTransitions!=4)
                throw new IllegalArgumentException("P07 current source metadata changed after branch entry");
            prefixHoles=(PcbBoardHole[])field(branch,"holes");
            if(prefixHoles!=seeds.prefixHoleRefs)
                throw new IllegalArgumentException("P07 branch prefix object changed after entry");
            start=(PcbPadPlacement)field(branch,"start"); root=(PcbPadPlacement)field(branch,"root");
            if(start==null || root==null || !netId.equals(stringField(branch,"net")))
                throw new IllegalArgumentException("Invalid P07 branch endpoint metadata");
            reachedPadIdsRef=field(search,"reachedPadIds");
            if(!(reachedPadIdsRef instanceof Vector))
                throw new IllegalArgumentException("Missing P07 ordered reached-pad source");
            if(field(publisher,"start")!=start || field(publisher,"root")!=root ||
                    field(branch,"found")!=field(publisher,"end") ||
                    intField(branch,"branchOrdinal")!=branchOrdinal ||
                    intField(publisher,"stage")!=1 ||
                    intField(publisher,"qualityIndex")!=vectorSize(field(publisher,"reversed")))
                throw new IllegalArgumentException("P07 capture is not at the quiescent Publisher quality boundary");
            if(start.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    !layout.getHoles().equals(java.util.Arrays.asList(prefixHoles)))
                throw new IllegalArgumentException("P07 branch prefix differs from its actual layout");
            Object template=field(routeSession,"template");
            if(!(template instanceof PcbBoardLayout) || ((PcbBoardLayout)template).getHoleCount()!=0)
                throw new IllegalArgumentException("P07 route Session template is not an empty-hole input");
            entry=new EntryAuthority(seeds);
            verifyEntryToPre();
            contextNanos=Math.max(0L,System.nanoTime()-started);
        }

        void verifyThread() {
            if(Thread.currentThread()!=ownerThread)
                throw new IllegalStateException("P07 capture cursor changed exclusive source thread");
        }
        void verifyEntryToPre() {
            verifyThread();
            if(field(routeSession,"search")!=search || field(search,"owner")!=routeSession ||
                    field(search,"layout")!=layout || field(search,"branch")!=null ||
                    field(search,"publisher")!=publisher || field(branch,"holes")!=seeds.prefixHoleRefs ||
                    field(publisher,"endTerminal")!=field(branch,"foundTerminal") ||
                    intField(search,"pass")!=seeds.pass || intField(search,"netIndex")!=seeds.netIndexNextFetch ||
                    intField(search,"branches")!=seeds.branches || intField(branch,"branchOrdinal")!=branchOrdinal ||
                    !netId.equals(stringField(search,"currentNet")) ||
                    field(search,"nets")!=seeds.netOrderVectorRef ||
                    vectorSize(field(search,"nets"))!=seeds.netOrder.length)
                throw new IllegalStateException("P07 source branch-entry binding changed before capture");
            entry.verify(layout,seeds);
            if(intField(publisher,"stage")!=1 ||
                    intField(publisher,"qualityIndex")!=vectorSize(field(publisher,"reversed")) ||
                    field(publisher,"end")!=field(branch,"found"))
                throw new IllegalStateException("P07 Publisher left its quiescent pre-publication boundary");
        }
        void verifyPost(PreResult pre) {
            verifyThread();
            if(pre==null || pre!=preResult || field(routeSession,"search")!=search ||
                    field(search,"owner")!=routeSession || field(search,"layout")!=layout ||
                    field(search,"branch")!=null || field(search,"publisher")!=null ||
                    field(search,"reachedPadIds")!=reachedPadIdsRef || field(branch,"holes")!=seeds.prefixHoleRefs ||
                    intField(search,"pass")!=pass || intField(search,"netIndex")!=netIndexNextFetch ||
                    intField(search,"branches")!=branchOrdinal ||
                    !netId.equals(stringField(search,"currentNet")) ||
                    field(search,"nets")!=seeds.netOrderVectorRef ||
                    vectorSize(field(search,"nets"))!=netOrder.length ||
                    intField(publisher,"stage")!=6 || field(publisher,"end")!=field(branch,"found") ||
                    field(publisher,"endTerminal")!=field(branch,"foundTerminal") ||
                    field(publisher,"start")!=start || field(publisher,"root")!=root ||
                    field(branch,"start")!=start || field(branch,"root")!=root)
                throw new IllegalStateException("P07 source is not at same-branch normal-publication completion");
            Object liveTraces=field(layout,"traces");
            if(liveTraces!=seeds.tracesVector || !(liveTraces instanceof Vector))
                throw new IllegalStateException("P07 publication replaced the source trace inventory");
            int tracesNow=((Vector<?>)liveTraces).size();
            int traceModNow=vectorModCount((Vector<?>)liveTraces);
            int holesNow=layout.getHoleCount();
            if(postTraceCount<0) {
                postTraceCount=tracesNow; postTraceModCount=traceModNow; postHoleCount=holesNow;
            } else if(tracesNow!=postTraceCount || traceModNow!=postTraceModCount || holesNow!=postHoleCount)
                throw new IllegalStateException("P07 post-publication inventory changed during capture");
            entry.verifyStaticLayout(layout);
        }
    }

    private static final class EntryAuthority {
        final Object pads,components,labels,regions,holes,traces,outline,partsTray;
        final int width,height,version,outlineX,outlineY,outlineWidth,outlineHeight;
        final int holeCount,traceCount,traceModCount;
        EntryAuthority(SeedSnapshot s) {
            pads=s.padsMap; components=s.componentsMap; labels=s.labelsMap; regions=s.regionsMap;
            holes=s.holesMap; traces=s.tracesVector; outline=s.outlineRef; partsTray=s.partsTrayRef;
            width=s.width; height=s.height; version=s.layoutAlgorithmVersion;
            outlineX=s.outlineX; outlineY=s.outlineY; outlineWidth=s.outlineWidth; outlineHeight=s.outlineHeight;
            holeCount=((Map<?,?>)holes).size(); traceCount=s.traceCount; traceModCount=s.traceModCount;
        }
        void verify(PcbBoardLayout layout,SeedSnapshot s) {
            Rectangle r=(Rectangle)field(layout,"boardOutline");
            if(field(layout,"pads")!=pads || field(layout,"components")!=components ||
                    field(layout,"silkscreenLabels")!=labels || field(layout,"regions")!=regions ||
                    field(layout,"holes")!=holes || field(layout,"traces")!=traces ||
                    field(layout,"boardOutline")!=outline || field(layout,"partsTray")!=partsTray ||
                    intField(layout,"width")!=width || intField(layout,"height")!=height ||
                    intField(layout,"layoutAlgorithmVersion")!=version || r.x!=outlineX || r.y!=outlineY ||
                    r.width!=outlineWidth || r.height!=outlineHeight || ((Map<?,?>)holes).size()!=holeCount ||
                    ((Vector<?>)traces).size()!=traceCount || vectorModCount((Vector<?>)traces)!=traceModCount ||
                    s.prefixHoleRefs.length!=holeCount)
                throw new IllegalStateException("P07 branch-entry geometry/prefix/trace authority changed before pre capture");
        }
        void verifyStaticLayout(PcbBoardLayout layout) {
            Rectangle r=(Rectangle)field(layout,"boardOutline");
            if(field(layout,"pads")!=pads || field(layout,"components")!=components ||
                    field(layout,"silkscreenLabels")!=labels || field(layout,"regions")!=regions ||
                    field(layout,"boardOutline")!=outline || field(layout,"partsTray")!=partsTray ||
                    field(layout,"traces")!=traces ||
                    intField(layout,"width")!=width || intField(layout,"height")!=height ||
                    intField(layout,"layoutAlgorithmVersion")!=version || r.x!=outlineX || r.y!=outlineY ||
                    r.width!=outlineWidth || r.height!=outlineHeight)
                throw new IllegalStateException("P07 immutable placement geometry changed during publication");
        }
    }

    static PreCursor beginPre(Context context) { return new PreCursor(context); }
    static PostCursor beginPost(Context context,PreResult preResult) {
        return new PostCursor(context,preResult);
    }

    static final class PreResult implements P07ZeroViaGraphDiagnostics.GraphInput {
        private final String bodyJson;
        private final PathNode[] nodes;
        private final Vector<RunData> runs;
        private final Vector<TraceData> prefixTraces;
        private final PcbBoardHole[] prefixHoleRefs;
        private final P07PrefixAllocationDiagnostics.Row[] prefixRows;
        private final String netId,pathTupleSha256;
        private final String[] netOrder;
        private final int branchOrdinal,transitionCount,workSteps,advanceCalls;
        private final int faceBitsOffset,faceBitCells,goalCellCount,seedLayerMask;
        private final String goalFaceBitsSha256;
        private final String prefixGeometrySha256;
        private PreResult(String bodyJson,PathNode[] nodes,Vector<RunData> runs,Vector<TraceData> prefixTraces,
                PcbBoardHole[] prefixHoleRefs,String[] netOrder,
                P07PrefixAllocationDiagnostics.Row[] prefixRows,String netId,int branchOrdinal,
                int transitionCount,String pathTupleSha256,int workSteps,int advanceCalls,
                int faceBitsOffset,int faceBitCells,String goalFaceBitsSha256,int goalCellCount,
                int seedLayerMask,String prefixGeometrySha256) {
            this.bodyJson=bodyJson; this.nodes=nodes; this.runs=runs; this.prefixTraces=prefixTraces;
            this.prefixHoleRefs=prefixHoleRefs; this.netOrder=netOrder; this.prefixRows=prefixRows;
            this.netId=netId; this.branchOrdinal=branchOrdinal;
            this.transitionCount=transitionCount; this.pathTupleSha256=pathTupleSha256;
            this.workSteps=workSteps; this.advanceCalls=advanceCalls;
            if(bodyJson==null || faceBitsOffset<0 || faceBitCells<1 ||
                    faceBitsOffset+faceBitCells>bodyJson.length() || goalFaceBitsSha256==null ||
                    goalCellCount<1 || seedLayerMask<1 || seedLayerMask>3)
                throw new IllegalArgumentException("Incomplete immutable P07 graph input snapshot");
            this.faceBitsOffset=faceBitsOffset; this.faceBitCells=faceBitCells;
            this.goalFaceBitsSha256=goalFaceBitsSha256; this.goalCellCount=goalCellCount;
            this.seedLayerMask=seedLayerMask;
            if(prefixGeometrySha256==null || prefixGeometrySha256.length()!=64)
                throw new IllegalArgumentException("Missing immutable P07 prefix geometry digest");
            this.prefixGeometrySha256=prefixGeometrySha256;
        }
        public int pathNodeCount() { return nodes.length; }
        int transitionCount() { return transitionCount; }
        int prefixHoleCount() { return prefixRows.length; }
        String netId() { return netId; }
        int branchOrdinal() { return branchOrdinal; }
        String prefixHoleIdAt(int index) { return prefixRows[index].id; }
        String prefixHoleFingerprintAt(int index) { return prefixRows[index].fingerprint; }
        public String pathTupleSha256() { return pathTupleSha256; }
        public String sourceKind() { return "LOAD12_ACCEPTED_PRE_RESULT"; }
        public int seedLayerMask() { return seedLayerMask; }
        public int pathCellAt(int index) { return pathAt(index).cell; }
        public int pathXAt(int index) { return pathAt(index).x; }
        public int pathYAt(int index) { return pathAt(index).y; }
        public int pathLayerAt(int index) { return pathAt(index).layer; }
        public int pathDirectionAt(int index) { return pathAt(index).direction; }
        public int pathTransitionsAt(int index) { return pathAt(index).transitions; }
        public int pathCostAt(int index) { return pathAt(index).cost; }
        public int pathHeuristicAt(int index) { return pathAt(index).heuristic; }
        public int pathSerialAt(int index) { return pathAt(index).serial; }
        public int pathKeyAt(int index) { return pathAt(index).key; }
        public String goalFaceBitsSha256() { return goalFaceBitsSha256; }
        String prefixGeometrySha256() { return prefixGeometrySha256; }
        public int goalCellCount() { return goalCellCount; }
        public int expectedGoalFaceMaskAt(int cell) {
            if(bodyJson==null || cell<0 || cell>=faceBitCells)
                throw new IndexOutOfBoundsException("P07 expected goal-face cell");
            char value=bodyJson.charAt(faceBitsOffset+cell);
            if(value<'0' || value>'3') throw new IllegalStateException("Malformed immutable P07 goal-face nibble");
            return value-'0';
        }
        private PathNode pathAt(int index) {
            if(nodes==null || index<0 || index>=nodes.length)
                throw new IndexOutOfBoundsException("P07 graph path Node");
            return nodes[index];
        }
        String toJson() { if(bodyJson==null) throw new IllegalStateException("Incomplete P07 pre result"); return bodyJson; }
    }

    static final class PostResult {
        private final String bodyJson;
        private final Vector<P07PrefixAllocationDiagnostics.Row> postRows;
        private final Vector<String> createdHoleIds;
        private final int createdTraceCount;
        private final String postGeometrySha256;
        private PostResult(String bodyJson,Vector<P07PrefixAllocationDiagnostics.Row> postRows,
                Vector<String> createdHoleIds,int createdTraceCount,String postGeometrySha256) {
            this.bodyJson=bodyJson; this.postRows=postRows;
            this.createdHoleIds=createdHoleIds; this.createdTraceCount=createdTraceCount;
            if(postGeometrySha256==null || postGeometrySha256.length()!=64)
                throw new IllegalArgumentException("Missing immutable P07 post geometry digest");
            this.postGeometrySha256=postGeometrySha256;
        }
        String postGeometrySha256() { return postGeometrySha256; }
        int postHoleCount() { return postRows.size(); }
        int createdHoleCount() { return createdHoleIds.size(); }
        int createdTraceCount() { return createdTraceCount; }
        String postHoleIdAt(int index) { return postRows.get(index).id; }
        String postHoleFingerprintAt(int index) { return postRows.get(index).fingerprint; }
        String createdHoleIdAt(int index) { return createdHoleIds.get(index); }
        String toJson() { if(bodyJson==null) throw new IllegalStateException("Incomplete P07 post result"); return bodyJson; }
    }

    private static final class PathNode {
        final Object node;
        final int index,parentIndex,cell,x,y,layer,direction,transitions,cost,heuristic,serial,key;
        final String tuple;
        PathNode(Object node,int index,int parentIndex,int cell,int x,int y,int layer,
                int direction,int transitions,int cost,int heuristic,int serial,int key) {
            this.node=node; this.index=index; this.parentIndex=parentIndex; this.cell=cell;
            this.x=x; this.y=y; this.layer=layer; this.direction=direction;
            this.transitions=transitions; this.cost=cost; this.heuristic=heuristic;
            this.serial=serial; this.key=key;
            tuple=index+","+parentIndex+","+cell+","+x+","+y+","+layer+","+
                direction+","+transitions+","+cost+","+heuristic+","+serial+","+key;
        }
        JsonObject toJsonValue() {
            return object("index",index,"parentIndex",parentIndex,"cell",cell,"x",x,"y",y,
                "layer",layer,"direction",direction,"transitions",transitions,"cost",cost,
                "heuristic",heuristic,"serial",serial,"key",key);
        }
    }

    private static final class RunData {
        final int layer,firstNodeIndex;
        final Vector<Point> points=new Vector<Point>();
        RunData(int layer,int firstNodeIndex) { this.layer=layer; this.firstNodeIndex=firstNodeIndex; }
        int mutableLastNodeIndex;
        void add(int nodeIndex,int x,int y,int direction) {
            mutableLastNodeIndex=nodeIndex;
            Point p=new Point(x,y);
            if(points.size()>=2) {
                Point a=points.get(points.size()-2),b=points.lastElement();
                boolean sameX=a.x==b.x && b.x==x;
                boolean sameY=a.y==b.y && b.y==y;
                if(sameX || sameY) {
                    int oldStep=sameX?b.y-a.y:b.x-a.x;
                    int newStep=sameX?y-b.y:x-b.x;
                    if(oldStep==0 || newStep==0 || (oldStep<0)!=(newStep<0))
                        throw new IllegalStateException("P07 path run contains a collinear reversal");
                    points.set(points.size()-1,p);
                    return;
                }
            }
            points.add(p);
        }
        JsonObject toJsonValue() {
            final Vector<Point> source=points;
            return object("layer",layer,"firstNodeIndex",firstNodeIndex,
                "lastNodeIndex",mutableLastNodeIndex,
                "xPoints",new JsonArray(source.size(),new JsonArraySource() {
                    public Object get(int index) { return Integer.valueOf(source.get(index).x); }
                }),
                "yPoints",new JsonArray(source.size(),new JsonArraySource() {
                    public Object get(int index) { return Integer.valueOf(source.get(index).y); }
                }));
        }
    }

    private static final class TraceData {
        final PcbTraceGeometry source;
        final int index,width;
        final String sourceId,netId,startPadId,endPadId,layer,exposure;
        final int[] xPoints,yPoints;
        int pointIndex;
        String fingerprint,currentHashText;
        String[] hashHeaderParts;
        MessageDigest fingerprintDigest;
        int hashHeaderIndex,hashCharIndex,hashSection,hashCoordinate,hashByteIndex,hashByteCount;
        boolean pointsFinished,hashCommaPending;
        final byte[] hashBytes=new byte[4];
        TraceData(PcbTraceGeometry source,int index) {
            if(source==null) throw new IllegalArgumentException("Missing P07 trace source");
            this.source=source; this.index=index;
            sourceId=source.getSourceId(); netId=validString(source.getNetId(),"trace net");
            startPadId=source.getStartPadId(); endPadId=source.getEndPadId();
            layer=source.getLayer().name(); exposure=source.getExposure().name(); width=source.getWidth();
            xPoints=(int[])field(source,"xPoints"); yPoints=(int[])field(source,"yPoints");
            if(xPoints==null || yPoints==null || xPoints.length<2 || xPoints.length!=yPoints.length ||
                    xPoints.length>MAX_TRACE_POINTS)
                throw new IllegalArgumentException("P07 trace points exceed capture bounds");
            validNullableString(sourceId,"trace source ID");
            validNullableString(startPadId,"trace start pad"); validNullableString(endPadId,"trace end pad");
        }
        void capturePoint(int at) {
            if(at!=pointIndex || at<0 || at>=xPoints.length)
                throw new IllegalStateException("P07 trace point cursor lost source order");
            pointIndex++;
        }
        void finishPoints() {
            if(pointIndex!=xPoints.length) throw new IllegalStateException("Incomplete P07 trace point capture");
            hashHeaderParts=new String[] {fieldPrefix(sourceId),sourceId,fieldPrefix(netId),netId,
                fieldPrefix(startPadId),startPadId,fieldPrefix(endPadId),endPadId,
                layer,"\n",exposure,"\n",Integer.toString(width),"\n"};
            fingerprintDigest=messageDigest();
            pointsFinished=true;
        }
        boolean hashOne() {
            if(!pointsFinished || fingerprintDigest==null)
                throw new IllegalStateException("P07 trace fingerprint has no source text");
            if(hashByteIndex<hashByteCount) {
                fingerprintDigest.update(hashBytes[hashByteIndex++]);
                return true;
            }
            int codePoint=nextHashCodePoint();
            if(codePoint<0) return false;
            if(codePoint<=0x7f) { hashBytes[0]=(byte)codePoint; hashByteCount=1; }
            else if(codePoint<=0x7ff) {
                hashBytes[0]=(byte)(0xc0|(codePoint>>>6));
                hashBytes[1]=(byte)(0x80|(codePoint&0x3f)); hashByteCount=2;
            } else if(codePoint<=0xffff) {
                hashBytes[0]=(byte)(0xe0|(codePoint>>>12));
                hashBytes[1]=(byte)(0x80|((codePoint>>>6)&0x3f));
                hashBytes[2]=(byte)(0x80|(codePoint&0x3f)); hashByteCount=3;
            } else {
                hashBytes[0]=(byte)(0xf0|(codePoint>>>18));
                hashBytes[1]=(byte)(0x80|((codePoint>>>12)&0x3f));
                hashBytes[2]=(byte)(0x80|((codePoint>>>6)&0x3f));
                hashBytes[3]=(byte)(0x80|(codePoint&0x3f)); hashByteCount=4;
            }
            hashByteIndex=1;
            fingerprintDigest.update(hashBytes[0]);
            return true;
        }
        boolean hashComplete() {
            return pointsFinished && hashByteIndex>=hashByteCount && currentHashText==null &&
                hashHeaderIndex>=hashHeaderParts.length && hashSection>=2;
        }
        private int nextHashCodePoint() {
            while(true) {
                if(currentHashText!=null && hashCharIndex<currentHashText.length()) {
                    char first=currentHashText.charAt(hashCharIndex++);
                    if(Character.isHighSurrogate(first)) {
                        if(hashCharIndex<currentHashText.length() &&
                                Character.isLowSurrogate(currentHashText.charAt(hashCharIndex)))
                            return Character.toCodePoint(first,currentHashText.charAt(hashCharIndex++));
                        return '?';
                    }
                    return Character.isLowSurrogate(first)?'?':first;
                }
                currentHashText=nextHashText(); hashCharIndex=0;
                if(currentHashText==null) return -1;
            }
        }
        private String nextHashText() {
            while(hashHeaderIndex<hashHeaderParts.length) {
                String part=hashHeaderParts[hashHeaderIndex++];
                if(part!=null && part.length()>0) return part;
            }
            if(hashSection==0) {
                if(hashCoordinate>=xPoints.length) {
                    hashSection=1; hashCoordinate=0; hashCommaPending=false; return "\n";
                }
                if(hashCommaPending) { hashCommaPending=false; return ","; }
                String value=Integer.toString(xPoints[hashCoordinate++]);
                hashCommaPending=hashCoordinate<xPoints.length;
                return value;
            }
            if(hashSection==1) {
                if(hashCoordinate>=yPoints.length) { hashSection=2; return null; }
                if(hashCommaPending) { hashCommaPending=false; return ","; }
                String value=Integer.toString(yPoints[hashCoordinate++]);
                hashCommaPending=hashCoordinate<yPoints.length;
                return value;
            }
            return null;
        }
        void finishHash() {
            if(!hashComplete()) throw new IllegalStateException("Incomplete P07 trace hash");
            fingerprint=hex(fingerprintDigest.digest());
        }
        JsonObject toJsonValue() {
            final TraceData trace=this;
            return object("index",index,"sourceId",sourceId,"netId",netId,
                "startPadId",startPadId,"endPadId",endPadId,"layer",layer,
                "exposure",exposure,"width",width,
                "xPoints",new JsonArray(xPoints.length,new JsonArraySource() {
                    public Object get(int at) { return Integer.valueOf(trace.xPoints[at]); }
                }),
                "yPoints",new JsonArray(yPoints.length,new JsonArraySource() {
                    public Object get(int at) { return Integer.valueOf(trace.yPoints[at]); }
                }),"traceFingerprint",fingerprint);
        }
    }

    static final class PreCursor {
        private final Context context;
        private final P07PrefixAllocationDiagnostics.Cursor prefixCursor;
        private P07PrefixAllocationDiagnostics.Result prefixResult;
        private String prefixBody;
        private final Vector<Object> reversedNodes=new Vector<Object>();
        private PathNode[] pathNodes;
        private P07PrefixAllocationDiagnostics.Row[] prefixRows;
        private final Vector<RunData> planarRuns=new Vector<RunData>();
        private RunData activeRun;
        private final Vector<TraceData> prefixTraces=new Vector<TraceData>();
        private TraceData traceDraft;
        private Object traceVector;
        private Object collectNode;
        private boolean[] goals;
        private StringBuilder faceBits;
        private int stage,pathIndex,tupleNode,tupleChar,goalCell,traceIndex,
            tracePoint,advanceCalls,lastWork,workSteps,prefixWorkSteps,
            reachedStartCount,
            prefixRowCopyIndex,
            serializedUtf16Units,gridSteps,length,bends,transitionCount,lastDirection=4,
            goalsTop,goalsBottom,goalsBoth,goalCellCount,startCell,endCell,directDistance,
            qualityLength,qualityBends,qualityIndex,endLayer,endTransitions;
        private String endTerminalPadId,pathTupleSha256;
        private MessageDigest tupleDigest,goalDigest;
        private long activeNanos;
        private JsonStream stream;
        private JsonString faceBitsJson;
        private JsonCounter activeMillisCounter,serializedUnitsCounter,workStepsCounter,advanceCallsCounter;
        private String goalFaceBitsSha256;
        private PreResult result;
        private long materializationCredits;
        private boolean complete;

        private PreCursor(Context context) {
            if(context==null) throw new IllegalArgumentException("Missing P07 pre cursor context");
            long setupStarted=System.nanoTime();
            this.context=context;
            context.verifyEntryToPre();
            workSteps=context.seeds.entryCaptureWork+context.entryVerificationWork;
            Object search=context.search,branch=context.branch;
            String[] netOrder=context.netOrder;
            prefixCursor=P07PrefixAllocationDiagnostics.begin(
                new P07PrefixAllocationDiagnostics.Context(context.layout,context.prefixHoles,
                    netOrder,context.netIndexNextFetch,context.netId,context.branchOrdinal,
                    context.pass,context.maximumVias));
            traceVector=field(context.layout,"traces");
            collectNode=field(context.publisher,"end");
            goals=(boolean[])field(branch,"goal");
            if(goals==null || goals.length!=intField(search,"cells" )*2)
                throw new IllegalArgumentException("P07 branch goal mask has a wrong grid size");
            tupleDigest=messageDigest(); goalDigest=messageDigest();
            stage=0;
            activeNanos=context.seeds.captureNanos+context.contextNanos+
                Math.max(0L,System.nanoTime()-setupStarted);
        }

        boolean advance(int maximumWork) {
            if(maximumWork<1 || maximumWork>MAX_ADVANCE_WORK)
                throw new IllegalArgumentException("P07 pre quantum is outside 1..32768");
            if(complete) { lastWork=0; return true; }
            if(advanceCalls>=640) throw new IllegalStateException("P07 accepted-branch total advance-call cap exceeded");
            long callStarted=System.nanoTime();
            context.verifyEntryToPre();
            advanceCalls++; lastWork=0;
            while(lastWork<maximumWork && !complete) {
                context.verifyThread();
                switch(stage) {
                case 0: {
                    int remain=maximumWork-lastWork;
                    boolean done=prefixCursor.advance(remain);
                    int used=prefixCursor.lastWork();
                    debit(used,maximumWork);
                    prefixWorkSteps+=used;
                    if(done) {
                        prefixBody=prefixCursor.bodyJson(); prefixResult=prefixCursor.result();
                        stage=1;
                    }
                    if(used==0 && !done) throw new IllegalStateException("P07 nested prefix cursor made no progress");
                    break;
                }
                case 1: {
                    if(collectNode!=null) {
                        if(reversedNodes.size()>=MAX_PATH_NODES)
                            throw new IllegalStateException("P07 accepted path exceeds 4096 Nodes");
                        reversedNodes.add(collectNode);
                        collectNode=field(collectNode,"previous");
                        debit(1,maximumWork);
                    } else {
                        if(reversedNodes.isEmpty()) throw new IllegalStateException("Empty P07 Publisher path");
                        pathNodes=new PathNode[reversedNodes.size()];
                        pathIndex=0; stage=2; debit(1,maximumWork);
                    }
                    break;
                }
                case 2: {
                    if(pathIndex<pathNodes.length) {
                        int reverseAt=pathNodes.length-1-pathIndex;
                        Object node=reversedNodes.get(reverseAt);
                        Object actualPrevious=field(node,"previous");
                        int[] v=readNode(node);
                        int cells=intField(context.search,"cells");
                        int width=intField(context.search,"width"),height=intField(context.search,"height");
                        int cell=v[0],layer=v[1],direction=v[2],transitions=v[3];
                        if(cell<0 || cell>=cells || layer<0 || layer>1 || direction<0 || direction>4 ||
                                transitions<0 || transitions>context.maximumTransitions ||
                                v[7]!=nodeKey(cell,layer,direction,transitions,cells))
                            throw new IllegalArgumentException("Malformed source Node/key/grid in accepted path");
                        int parent=pathIndex==0?-1:pathIndex-1;
                        if(pathIndex==0) {
                            if(!context.seeds.containsNodeIdentity(node) || actualPrevious!=null ||
                                    cell!=cell(context.search,context.start))
                                throw new IllegalStateException("Accepted path does not begin at an actual branch seed");
                            startCell=cell;
                        } else {
                            if(actualPrevious!=pathNodes[pathIndex-1].node)
                                throw new IllegalStateException("Accepted path Node parent identity is discontinuous");
                            PathNode before=pathNodes[pathIndex-1];
                            if(layer==before.layer) {
                                int distance=Math.abs(cell%width-before.cell%width)+
                                    Math.abs(cell/width-before.cell/width);
                                if(distance!=1 || transitions!=before.transitions ||
                                        direction!=directionBetween(before.cell,cell,width))
                                    throw new IllegalStateException("Accepted path has a malformed planar grid edge");
                                gridSteps++; length+=GRID;
                                if(lastDirection!=4 && direction!=lastDirection) bends++;
                                lastDirection=direction;
                            } else {
                                if(cell!=before.cell || transitions!=before.transitions+1 || direction!=4)
                                    throw new IllegalStateException("Accepted path has a malformed face transition");
                                transitionCount++; lastDirection=4;
                                if(activeRun!=null) { if(activeRun.points.size()>=2) planarRuns.add(activeRun); activeRun=null; }
                            }
                        }
                        int x=intField(context.search,"minX")+(cell%width)*GRID;
                        int y=intField(context.search,"minY")+(cell/width)*GRID;
                        PathNode row=new PathNode(node,pathIndex,parent,cell,x,y,layer,direction,
                            transitions,v[4],v[5],v[6],v[7]);
                        pathNodes[pathIndex]=row;
                        if(activeRun==null) activeRun=new RunData(layer,pathIndex);
                        activeRun.add(pathIndex,x,y,direction);
                        pathIndex++; debit(1,maximumWork);
                    } else {
                        if(activeRun!=null && activeRun.points.size()>=2) planarRuns.add(activeRun);
                        if(pathNodes[pathNodes.length-1].node!=field(context.publisher,"end"))
                            throw new IllegalStateException("Accepted path does not terminate at actual Publisher Node");
                        endCell=pathNodes[pathNodes.length-1].cell;
                        endLayer=pathNodes[pathNodes.length-1].layer;
                        endTransitions=pathNodes[pathNodes.length-1].transitions;
                        int dx=context.start.getX()-(intField(context.search,"minX")+(endCell%intField(context.search,"width"))*GRID);
                        int dy=context.start.getY()-(intField(context.search,"minY")+(endCell/intField(context.search,"width"))*GRID);
                        directDistance=Math.abs(dx)+Math.abs(dy);
                        Object publisher=context.publisher;
                        qualityIndex=intField(publisher,"qualityIndex");
                        qualityLength=intField(publisher,"length"); qualityBends=intField(publisher,"bends");
                        Object endTerminal=field(publisher,"endTerminal");
                        if(endTerminal==null) endTerminalPadId=null;
                        else if(!(endTerminal instanceof PcbPadPlacement) ||
                                endTerminal!=field(context.branch,"foundTerminal"))
                            throw new IllegalStateException("P07 Publisher end terminal identity changed");
                        else endTerminalPadId=validNullableString(
                            ((PcbPadPlacement)endTerminal).getPadId(),"Publisher end terminal pad ID");
                        if(qualityIndex!=pathNodes.length || directDistance==0 || length>directDistance*3 ||
                                bends>16 || qualityLength!=length || qualityBends!=bends)
                            throw new IllegalStateException("Accepted path differs from Publisher quality boundary");
                        stage=3; debit(1,maximumWork);
                    }
                    break;
                }
                case 3: {
                    if(tupleNode<pathNodes.length) {
                        String tuple=pathNodes[tupleNode].tuple;
                        if(tupleChar<tuple.length()) {
                            char c=tuple.charAt(tupleChar++);
                            tupleDigest.update((byte)c); debit(1,maximumWork);
                        } else if(tupleNode+1<pathNodes.length) {
                            tupleNode++; tupleChar=0; tupleDigest.update((byte)'\n'); debit(1,maximumWork);
                        } else {
                            tupleNode++; tupleChar=0; pathTupleSha256=hex(tupleDigest.digest());
                            stage=4; debit(1,maximumWork);
                        }
                    } else { stage=4; debit(1,maximumWork); }
                    break;
                }
                case 4: {
                    if(goalCell<goals.length/2) {
                        boolean top=goals[goalCell],bottom=goals[goals.length/2+goalCell];
                        int bits=(top?1:0)|(bottom?2:0);
                        char face=(char)('0'+bits);
                        if(faceBits==null) faceBits=new StringBuilder(goals.length/2);
                        faceBits.append(face);
                        goalDigest.update((byte)face);
                        if(bits!=0) goalCellCount++;
                        if(top) goalsTop++; if(bottom) goalsBottom++; if(top&&bottom) goalsBoth++;
                        goalCell++; debit(1,maximumWork);
                    } else {
                        int foundIndex=endLayer*(goals.length/2)+endCell;
                        if(foundIndex<0 || foundIndex>=goals.length || !goals[foundIndex])
                            throw new IllegalStateException("Publisher endpoint is not a source BranchSearch goal");
                        goalFaceBitsSha256=hex(goalDigest.digest());
                        stage=5; debit(1,maximumWork);
                    }
                    break;
                }
                case 5: {
                    int count=((Vector<?>)traceVector).size();
                    if(count>MAX_TRACES || count!=context.seeds.traceCount ||
                            vectorModCount((Vector<?>)traceVector)!=context.seeds.traceModCount)
                        throw new IllegalStateException("P07 prefix traces changed before path publication");
                    if(traceDraft==null && traceIndex<count)
                        traceDraft=new TraceData((PcbTraceGeometry)((Vector<?>)traceVector).get(traceIndex),traceIndex);
                    if(traceDraft!=null && tracePoint<traceDraft.xPoints.length) {
                        traceDraft.capturePoint(tracePoint++); debit(1,maximumWork);
                    } else if(traceDraft!=null && !traceDraft.pointsFinished) {
                        traceDraft.finishPoints();
                        debit(1,maximumWork);
                    } else if(traceDraft!=null && !traceDraft.hashComplete()) {
                        traceDraft.hashOne(); debit(1,maximumWork);
                    } else if(traceDraft!=null) {
                        traceDraft.finishHash(); prefixTraces.add(traceDraft);
                        traceIndex++; tracePoint=0; traceDraft=null; debit(1,maximumWork);
                    } else {
                        context.verifyEntryToPre();
                        stage=6; debit(1,maximumWork);
                    }
                    break;
                }
                case 6: {
                    faceBitsJson=new JsonString(faceBits,true);
                    stream=new JsonStream(buildPreModel(this));
                    stage=7; debit(1,maximumWork); break;
                }
                case 7: {
                    if(stream.pumpOne()) { debit(1,maximumWork); }
                    else {
                        context.verifyEntryToPre();
                        serializedUtf16Units=stream.builder.length();
                        prefixRows=new P07PrefixAllocationDiagnostics.Row[prefixResult.rowCount()];
                        stage=8; debit(1,maximumWork);
                    }
                    break;
                }
                case 8: {
                    if(prefixRowCopyIndex<prefixRows.length) {
                        prefixRows[prefixRowCopyIndex]=prefixResult.rowAt(prefixRowCopyIndex);
                        prefixRowCopyIndex++; debit(1,maximumWork);
                    } else if(materializationCredits<(long)serializedUtf16Units*2L) {
                        materializationCredits++; debit(1,maximumWork);
                    } else {
                        if(lastWork>=maximumWork) break;
                        debit(1,maximumWork);
                        context.verifyEntryToPre();
                        if(reachedStartCount!=0)
                            throw new IllegalStateException("P07 branch start was already in the reached-pad tree before publication");
                        context.preReachedStartCount=reachedStartCount;
                        stream.patch(serializedUnitsCounter,serializedUtf16Units);
                        stream.patch(workStepsCounter,workSteps);
                        stream.patch(advanceCallsCounter,advanceCalls);
                        String placeholder=stream.builder.toString();
                        if(placeholder.length()!=serializedUtf16Units)
                            throw new IllegalStateException("P07 pre payload materialization changed its UTF-16 length");
                        activeNanos+=Math.max(0L,System.nanoTime()-callStarted);
                        callStarted=0;
                        long millis=activeNanos/1000000L;
                        stream.patch(activeMillisCounter,millis);
                        long finalCopyStarted=System.nanoTime();
                        String body=stream.builder.toString();
                        if(body.length()!=serializedUtf16Units)
                            throw new IllegalStateException("P07 final pre payload changed its UTF-16 length");
                        activeNanos+=Math.max(0L,System.nanoTime()-finalCopyStarted);
                        result=new PreResult(body,pathNodes,planarRuns,
                            prefixTraces,context.prefixHoles,context.netOrder,
                            prefixRows,context.netId,context.branchOrdinal,transitionCount,pathTupleSha256,
                            workSteps,advanceCalls,faceBitsJson.contentOffset,goals.length/2,
                            goalFaceBitsSha256,goalCellCount,context.seeds.faceMask,prefixCursor.prefixGeometrySha256());
                        if(context.preResult!=null) throw new IllegalStateException("P07 Context already has a pre result");
                        context.preResult=result; complete=true;
                    }
                    break;
                }
                default: throw new IllegalStateException("Unknown P07 pre capture stage");
                }
            }
            if(callStarted!=0) {
                context.verifyEntryToPre();
                activeNanos+=Math.max(0L,System.nanoTime()-callStarted);
            }
            return complete;
        }

        private void debit(int amount,int maximumWork) {
            if(amount<0 || amount>maximumWork-lastWork || amount>MAX_TOTAL_WORK-workSteps)
                throw new IllegalStateException("P07 accepted-branch pre work cap exceeded");
            lastWork+=amount; workSteps+=amount;
        }
        int lastWork() { return lastWork; }
        boolean isComplete() { return complete; }
        PreResult result() {
            if(!complete || result==null) throw new IllegalStateException("P07 pre cursor is incomplete");
            return result;
        }
    }

    private static final class HoleBinding {
        final String id,preFingerprint,postFingerprint;
        final int preIndex,postIndex;
        HoleBinding(String id,String before,String after,int preIndex,int postIndex) {
            this.id=id; preFingerprint=before; postFingerprint=after;
            this.preIndex=preIndex; this.postIndex=postIndex;
        }
        JsonObject toJsonValue() {
            return object("id",id,"preFingerprint",preFingerprint,"postFingerprint",postFingerprint,
                "preIndex",preIndex,"postIndex",postIndex,"sameObject",Boolean.TRUE);
        }
    }
    private static final class TraceBinding {
        final String sourceId,preFingerprint,postFingerprint;
        final int preIndex,postIndex;
        TraceBinding(String sourceId,String before,String after,int preIndex,int postIndex) {
            this.sourceId=sourceId; preFingerprint=before; postFingerprint=after;
            this.preIndex=preIndex; this.postIndex=postIndex;
        }
        JsonObject toJsonValue() {
            return object("sourceId",sourceId,"preFingerprint",preFingerprint,
                "postFingerprint",postFingerprint,"preIndex",preIndex,
                "postIndex",postIndex,"sameObject",Boolean.TRUE);
        }
    }
    private static final class TransitionBinding {
        final int pathNodeIndex,fromLayer,toLayer,cell,x,y,creatorNetPosition,creatorBranchOrdinal,piece;
        final String netId,viaId,viaFingerprint;
        final boolean createdInBranch;
        TransitionBinding(int pathNodeIndex,PathNode before,PathNode after,String netId,
                P07PrefixAllocationDiagnostics.Row via,boolean created) {
            this.pathNodeIndex=pathNodeIndex; fromLayer=before.layer; toLayer=after.layer;
            cell=after.cell; x=after.x; y=after.y; this.netId=netId;
            viaId=via.id; viaFingerprint=via.fingerprint; creatorNetPosition=via.creatorNetPosition;
            creatorBranchOrdinal=via.creatorBranchOrdinal; piece=via.piece; createdInBranch=created;
        }
        JsonObject toJsonValue() {
            return object("pathNodeIndex",pathNodeIndex,"fromLayer",fromLayer,"toLayer",toLayer,
                "cell",cell,"x",x,"y",y,"netId",netId,"viaId",viaId,
                "viaFingerprint",viaFingerprint,"creatorNetPosition",creatorNetPosition,
                "creatorBranchOrdinal",creatorBranchOrdinal,"piece",piece,
                "createdInBranch",Boolean.valueOf(createdInBranch));
        }
    }

    static final class PostCursor {
        private final Context context;
        private final PreResult pre;
        private final Vector<PcbBoardHole> postHoleRefs=new Vector<PcbBoardHole>();
        private final Vector<P07PrefixAllocationDiagnostics.Row> postRows=
            new Vector<P07PrefixAllocationDiagnostics.Row>();
        private final Vector<String> createdHoleIds=new Vector<String>();
        private final Vector<HoleBinding> retainedHoleBindings=new Vector<HoleBinding>();
        private final Vector<TraceData> newTraces=new Vector<TraceData>();
        private final Vector<TraceBinding> retainedTraceBindings=new Vector<TraceBinding>();
        private final Vector<TransitionBinding> transitionBindings=new Vector<TransitionBinding>();
        private final Map<String,Integer> preHoleIndexes=new HashMap<String,Integer>();
        private final Map<String,Integer> postHoleIndexes=new HashMap<String,Integer>();
        private final Map<String,PcbBoardHole> preHoleObjects=new HashMap<String,PcbBoardHole>();
        private final IdentityHashMap<PcbTraceGeometry,TraceData> preTraceByIdentity=
            new IdentityHashMap<PcbTraceGeometry,TraceData>();
        private final IdentityHashMap<PcbTraceGeometry,Boolean> seenPreTraces=
            new IdentityHashMap<PcbTraceGeometry,Boolean>();
        private Object traceVector;
        private TraceData traceDraft;
        private PcbBoardHole[] currentHoles;
        private final P07PrefixAllocationDiagnostics.Cursor postInventoryCursor;
        private P07PrefixAllocationDiagnostics.Result postInventoryResult;
        private int postInventoryBodyUnits;
        private long postInventoryBodyCredits,materializationCredits;
        private int stage,reachedIndex,reachedStartCount,netOrderCheckIndex,preTraceSetupIndex,
            holeIndex,retainedHoleIndex,traceIndex,tracePoint,
            transitionNode,transitionSearchIndex,transitionMatches,transitionHitCount,traceMatchRun,
            advanceCalls,lastWork,workSteps,aggregateWorkSteps,retainedHoleCount,
            retainedTraceCount,createdTraceCount,serializedUtf16Units;
        private P07PrefixAllocationDiagnostics.Row transitionCandidate;
        private long activeNanos;
        private JsonStream stream;
        private JsonCounter activeMillisCounter,serializedUnitsCounter,workStepsCounter,
            aggregateWorkCounter,advanceCallsCounter;
        private Vector<?> reachedPadIds;
        private int traceCompareIndex,traceComparePoint,tracePointCompareIndex,
            traceCandidateCount,traceCandidateIndex;
        private Vector<Point> traceCompactPoints;
        private boolean traceCandidateEqual;
        private PostResult result;
        private boolean complete;

        private PostCursor(Context context,PreResult pre) {
            if(context==null || pre==null || context.preResult!=pre || context.postStarted)
                throw new IllegalArgumentException("P07 post cursor requires its completed pre result");
            long setupStarted=System.nanoTime();
            this.context=context; this.pre=pre;
            context.postStarted=true;
            for(int i=0;i<pre.prefixRows.length;i++) {
                P07PrefixAllocationDiagnostics.Row row=pre.prefixRows[i];
                if(preHoleIndexes.put(row.id,Integer.valueOf(i))!=null)
                    throw new IllegalArgumentException("Duplicate P07 pre hole identity");
                preHoleObjects.put(row.id,pre.prefixHoleRefs[i]);
            }
            context.verifyPost(pre);
            reachedPadIds=(Vector<?>)context.reachedPadIdsRef;
            traceVector=field(context.layout,"traces");
            currentHoles=context.layout.getHoles().toArray(new PcbBoardHole[0]);
            if(currentHoles.length>MAX_HOLES)
                throw new IllegalArgumentException("P07 post hole inventory exceeds the via cap");
            if(context.branchOrdinal==Integer.MAX_VALUE)
                throw new IllegalArgumentException("P07 post validation horizon overflows");
            postInventoryCursor=P07PrefixAllocationDiagnostics.begin(
                new P07PrefixAllocationDiagnostics.Context(context.layout,currentHoles,pre.netOrder,
                    context.netIndexNextFetch,context.netId,context.branchOrdinal+1,
                    context.pass,context.maximumVias));
            workSteps=pre.prefixRows.length+currentHoles.length;
            activeNanos=Math.max(0L,System.nanoTime()-setupStarted);
        }

        boolean advance(int maximumWork) {
            if(maximumWork<1 || maximumWork>MAX_ADVANCE_WORK)
                throw new IllegalArgumentException("P07 post quantum is outside 1..32768");
            if(complete) { lastWork=0; return true; }
            if(pre.advanceCalls+advanceCalls>=640)
                throw new IllegalStateException("P07 accepted-branch total advance-call cap exceeded");
            long callStarted=System.nanoTime(); advanceCalls++; lastWork=0;
            context.verifyPost(pre);
            while(lastWork<maximumWork && !complete) {
                context.verifyThread();
                switch(stage) {
                case 0: {
                    if(netOrderCheckIndex<context.netOrder.length) {
                        Object value=((Vector<?>)field(context.search,"nets")).get(netOrderCheckIndex);
                        if(!(value instanceof String) || !context.netOrder[netOrderCheckIndex].equals(value))
                            throw new IllegalStateException("P07 source net order changed before post inventory capture");
                        netOrderCheckIndex++; debit(1,maximumWork);
                    } else if(reachedIndex<reachedPadIds.size()) {
                        Object value=reachedPadIds.get(reachedIndex++);
                        if(!(value instanceof String)) throw new IllegalStateException("P07 reached-pad inventory contains a non-string");
                        if(context.start.getPadId().equals(value)) reachedStartCount++;
                        debit(1,maximumWork);
                    } else {
                        if(context.preReachedStartCount!=0 || reachedStartCount!=1)
                            throw new IllegalStateException("P07 publication did not add the branch start exactly once");
                        stage=1; debit(1,maximumWork);
                    }
                    break;
                }
                case 1: {
                    if(preTraceSetupIndex<pre.prefixTraces.size()) {
                        TraceData row=pre.prefixTraces.get(preTraceSetupIndex++);
                        if(preTraceByIdentity.put(row.source,row)!=null)
                            throw new IllegalStateException("Duplicate pre-publication trace identity");
                        debit(1,maximumWork);
                    } else { stage=2; debit(1,maximumWork); }
                    break;
                }
                case 2: {
                    if(postInventoryResult==null) {
                        boolean done=postInventoryCursor.advance(maximumWork-lastWork);
                        int used=postInventoryCursor.lastWork();
                        debit(used,maximumWork);
                        if(done) {
                            postInventoryResult=postInventoryCursor.result();
                            postInventoryBodyUnits=postInventoryCursor.bodyJson().length();
                            postInventoryBodyCredits=0;
                        } else if(used==0) {
                            throw new IllegalStateException("P07 nested post inventory cursor made no progress");
                        }
                    } else if(postInventoryBodyCredits<postInventoryBodyUnits) {
                        postInventoryBodyCredits++; debit(1,maximumWork);
                    } else if(holeIndex<currentHoles.length) {
                        PcbBoardHole hole=currentHoles[holeIndex];
                        P07PrefixAllocationDiagnostics.Row row=postInventoryResult.rowAt(holeIndex);
                        if(row.index!=holeIndex || !row.id.equals(hole.id))
                            throw new IllegalStateException("P07 post cursor row differs from its captured source order");
                        postRows.add(row); postHoleRefs.add(hole);
                        Integer oldIndex=preHoleIndexes.get(row.id);
                        if(oldIndex==null) {
                            createdHoleIds.add(row.id);
                            if(row.creatorBranchOrdinal!=context.branchOrdinal || !row.netId.equals(context.netId))
                                throw new IllegalStateException("P07 created hole lineage is not the current branch owner");
                        } else if(preHoleObjects.get(row.id)!=hole ||
                                !pre.prefixRows[oldIndex.intValue()].fingerprint.equals(row.fingerprint))
                            throw new IllegalStateException("P07 retained hole ID/fingerprint/object changed at publication");
                        if(postHoleIndexes.put(row.id,Integer.valueOf(holeIndex))!=null)
                            throw new IllegalStateException("Duplicate P07 post hole ID");
                        holeIndex++; debit(1,maximumWork);
                    } else {
                        if(postRows.size()<pre.prefixRows.length)
                            throw new IllegalStateException("P07 post inventory lost a prefix hole");
                        retainedHoleIndex=0; stage=3; debit(1,maximumWork);
                    }
                    break;
                }
                case 3: {
                    if(retainedHoleIndex<pre.prefixRows.length) {
                        P07PrefixAllocationDiagnostics.Row before=pre.prefixRows[retainedHoleIndex];
                        Integer afterIndex=postHoleIndexes.get(before.id);
                        if(afterIndex==null || preHoleObjects.get(before.id)!=postHoleRefs.get(afterIndex.intValue()))
                            throw new IllegalStateException("P07 post capture lost a retained prefix hole identity");
                        P07PrefixAllocationDiagnostics.Row after=postRows.get(afterIndex.intValue());
                        retainedHoleBindings.add(new HoleBinding(before.id,before.fingerprint,
                            after.fingerprint,retainedHoleIndex,afterIndex.intValue()));
                        retainedHoleCount++; retainedHoleIndex++; debit(1,maximumWork);
                    } else { stage=4; traceIndex=0; debit(1,maximumWork); }
                    break;
                }
                case 4: {
                    int count=((Vector<?>)traceVector).size();
                    if(count>MAX_TRACES) throw new IllegalStateException("P07 post trace inventory exceeds cap");
                    if(traceIndex<count) {
                        PcbTraceGeometry trace=(PcbTraceGeometry)((Vector<?>)traceVector).get(traceIndex);
                        TraceData retained=preTraceByIdentity.get(trace);
                        if(retained!=null) {
                            if(seenPreTraces.put(trace,Boolean.TRUE)!=null)
                                throw new IllegalStateException("Duplicate retained P07 trace object");
                            retainedTraceBindings.add(new TraceBinding(retained.sourceId,retained.fingerprint,
                                retained.fingerprint,retained.index,traceIndex));
                            retainedTraceCount++; traceIndex++; debit(1,maximumWork);
                        } else {
                            if(traceDraft==null) traceDraft=new TraceData(trace,traceIndex);
                            if(tracePoint<traceDraft.xPoints.length) {
                                traceDraft.capturePoint(tracePoint++); debit(1,maximumWork);
                            } else if(!traceDraft.pointsFinished) {
                                traceDraft.finishPoints(); debit(1,maximumWork);
                            } else if(!traceDraft.hashComplete()) {
                                traceDraft.hashOne(); debit(1,maximumWork);
                            } else {
                                traceDraft.finishHash();
                                if(traceDraft.sourceId==null || !context.netId.equals(traceDraft.netId))
                                    throw new IllegalStateException("New P07 routed trace has no current-net stable source ID");
                                int expectedWidth=context.board.getPlacementConstraints()
                                    .getTraceWidthProfile().widthFor(context.board.getNet(context.netId).getRoutingRole());
                                if(traceDraft.width!=expectedWidth)
                                    throw new IllegalStateException("New P07 trace width differs from actual board net role");
                                newTraces.add(traceDraft); createdTraceCount++;
                                traceDraft=null; tracePoint=0; traceIndex++; debit(1,maximumWork);
                            }
                        }
                    } else {
                        if(seenPreTraces.size()!=pre.prefixTraces.size() ||
                                count!=pre.prefixTraces.size()+createdTraceCount)
                            throw new IllegalStateException("P07 trace inventory did not retain the pre branch prefix");
                        traceMatchRun=0; stage=5; debit(1,maximumWork);
                    }
                    break;
                }
                case 5: {
                    if(transitionNode+1<pre.nodes.length) {
                        PathNode before=pre.nodes[transitionNode],after=pre.nodes[transitionNode+1];
                        if(before.layer!=after.layer) {
                            if(transitionSearchIndex<postRows.size()) {
                                P07PrefixAllocationDiagnostics.Row via=postRows.get(transitionSearchIndex);
                                if(via.kind==PcbBoardHole.Kind.VIA && via.netId.equals(context.netId) &&
                                        via.x==after.x && via.y==after.y) {
                                    if(++transitionHitCount>1)
                                        throw new IllegalStateException("Duplicate VIA owner at P07 transition coordinate");
                                    transitionCandidate=via;
                                }
                                transitionSearchIndex++; debit(1,maximumWork); break;
                            }
                            if(transitionHitCount!=1 || transitionCandidate==null)
                                throw new IllegalStateException("P07 face transition has no unique same-net post VIA");
                            boolean created=!preHoleIndexes.containsKey(transitionCandidate.id);
                            if((created && transitionCandidate.creatorBranchOrdinal!=context.branchOrdinal) ||
                                    (!created && transitionCandidate.creatorBranchOrdinal>=context.branchOrdinal) ||
                                    transitionCandidate.creatorNetPosition>context.netPosition)
                                throw new IllegalStateException("P07 transition VIA has invalid creator chronology");
                            transitionBindings.add(new TransitionBinding(transitionNode+1,before,after,
                                context.netId,transitionCandidate,created));
                            transitionMatches++; transitionNode++; transitionSearchIndex=0;
                            transitionHitCount=0; transitionCandidate=null;
                            debit(1,maximumWork); break;
                        }
                        transitionNode++; transitionSearchIndex=0; debit(1,maximumWork);
                    } else {
                        if(transitionMatches!=pre.transitionCount)
                            throw new IllegalStateException("P07 transition bindings differ from the accepted Node path");
                        if(traceMatchRun<pre.runs.size()) {
                            RunData run=pre.runs.get(traceMatchRun);
                            if(traceCompareIndex>=newTraces.size()) {
                                if(traceCandidateCount!=1)
                                    throw new IllegalStateException("P07 path run does not have one unique published trace");
                                markTraceUsed(traceCandidateIndex); traceMatchRun++;
                                traceCompareIndex=0; traceCandidateCount=0; traceCandidateIndex=-1;
                                traceCompactPoints=null; traceComparePoint=0; tracePointCompareIndex=0;
                                debit(1,maximumWork);
                            } else {
                                TraceData candidate=newTraces.get(traceCompareIndex);
                                if(traceCompactPoints==null) {
                                    if(traceUsed(traceCompareIndex) || !runCandidateEligible(run,candidate)) {
                                        traceCompareIndex++; debit(1,maximumWork);
                                    } else {
                                        traceCompactPoints=new Vector<Point>();
                                        traceComparePoint=0; tracePointCompareIndex=0; traceCandidateEqual=true;
                                        debit(1,maximumWork);
                                    }
                                } else if(traceComparePoint<candidate.xPoints.length) {
                                    Point next=new Point(candidate.xPoints[traceComparePoint],candidate.yPoints[traceComparePoint]);
                                    if(traceCompactPoints.size()>=2) {
                                        Point a=traceCompactPoints.get(traceCompactPoints.size()-2);
                                        Point b=traceCompactPoints.lastElement();
                                        boolean sameX=a.x==b.x && b.x==next.x;
                                        boolean sameY=a.y==b.y && b.y==next.y;
                                        if(sameX || sameY) {
                                            int oldStep=sameX?b.y-a.y:b.x-a.x;
                                            int newStep=sameX?next.y-b.y:next.x-b.x;
                                            if(oldStep==0 || newStep==0 || (oldStep<0)!=(newStep<0))
                                                throw new IllegalStateException("P07 trace piece contains a collinear reversal");
                                            traceCompactPoints.set(traceCompactPoints.size()-1,next);
                                        } else traceCompactPoints.add(next);
                                    } else traceCompactPoints.add(next);
                                    traceComparePoint++; debit(1,maximumWork);
                                } else if(!traceCandidateEqual || traceCompactPoints.size()!=run.points.size()) {
                                    traceCompactPoints=null; traceCompareIndex++;
                                    tracePointCompareIndex=0; debit(1,maximumWork);
                                } else if(tracePointCompareIndex<run.points.size()) {
                                    Point actual=traceCompactPoints.get(tracePointCompareIndex);
                                    Point expected=run.points.get(tracePointCompareIndex);
                                    if(actual.x!=expected.x || actual.y!=expected.y) traceCandidateEqual=false;
                                    tracePointCompareIndex++; debit(1,maximumWork);
                                } else {
                                    if(++traceCandidateCount>1)
                                        throw new IllegalStateException("Ambiguous P07 path trace run match");
                                    traceCandidateIndex=traceCompareIndex;
                                    traceCompactPoints=null; traceCompareIndex++;
                                    tracePointCompareIndex=0; debit(1,maximumWork);
                                }
                            }
                        } else {
                            if(newTraces.size()!=pre.runs.size())
                                throw new IllegalStateException("P07 new trace pieces do not match actual planar path runs");
                            context.verifyPost(pre);
                            stage=6; debit(1,maximumWork);
                        }
                    }
                    break;
                }
                case 6: {
                    stream=new JsonStream(buildPostModel(this));
                    stage=7; debit(1,maximumWork); break;
                }
                case 7: {
                    if(stream.pumpOne()) debit(1,maximumWork);
                    else {
                        serializedUtf16Units=stream.builder.length();
                        context.verifyPost(pre);
                        long materializationTarget=(long)serializedUtf16Units*2L;
                        if(materializationCredits<materializationTarget) {
                            int amount=(int)Math.min((long)(maximumWork-lastWork),
                                materializationTarget-materializationCredits);
                            materializationCredits+=amount;
                            debit(amount,maximumWork);
                            if(materializationCredits<materializationTarget) break;
                        }
                        if(lastWork>=maximumWork) break;
                        debit(1,maximumWork);
                        aggregateWorkSteps=pre.workSteps+workSteps;
                        context.verifyPost(pre);
                        stream.patch(serializedUnitsCounter,serializedUtf16Units);
                        stream.patch(workStepsCounter,workSteps);
                        stream.patch(aggregateWorkCounter,aggregateWorkSteps);
                        stream.patch(advanceCallsCounter,advanceCalls);
                        String placeholder=stream.builder.toString();
                        if(placeholder.length()!=serializedUtf16Units)
                            throw new IllegalStateException("P07 post payload materialization changed its UTF-16 length");
                        activeNanos+=Math.max(0L,System.nanoTime()-callStarted); callStarted=0;
                        long millis=activeNanos/1000000L;
                        stream.patch(activeMillisCounter,millis);
                        long finalCopyStarted=System.nanoTime();
                        String body=stream.builder.toString();
                        if(body.length()!=serializedUtf16Units)
                            throw new IllegalStateException("P07 final post payload changed its UTF-16 length");
                        activeNanos+=Math.max(0L,System.nanoTime()-finalCopyStarted);
                        result=new PostResult(body,postRows,createdHoleIds,createdTraceCount,
                            postInventoryCursor.prefixGeometrySha256());
                        complete=true;
                    }
                    break;
                }
                default: throw new IllegalStateException("Unknown P07 post capture stage");
                }
            }
            if(callStarted!=0) {
                context.verifyPost(pre);
                activeNanos+=Math.max(0L,System.nanoTime()-callStarted);
            }
            return complete;
        }

        private boolean traceUsed(int index) {
            return traceMatches.contains(Integer.valueOf(index));
        }
        private final HashSet<Integer> traceMatches=new HashSet<Integer>();
        private void markTraceUsed(int index) {
            if(!traceMatches.add(Integer.valueOf(index)))
                throw new IllegalStateException("Duplicate P07 path trace run match");
        }
        private boolean runCandidateEligible(RunData run,TraceData trace) {
            if(trace.netId==null || !context.netId.equals(trace.netId) ||
                    !runLayerName(run.layer).equals(trace.layer) ||
                    trace.width!=context.board.getPlacementConstraints().getTraceWidthProfile()
                        .widthFor(context.board.getNet(context.netId).getRoutingRole()) ||
                    run.points.size()<2 || trace.xPoints.length<2) return false;
            return true;
        }
        private void debit(int amount,int maximumWork) {
            if(amount<0 || amount>maximumWork-lastWork || amount>MAX_TOTAL_WORK-workSteps ||
                    (long)pre.workSteps+workSteps+amount>MAX_TOTAL_WORK)
                throw new IllegalStateException("P07 accepted-branch post work cap exceeded");
            lastWork+=amount; workSteps+=amount;
        }
        int lastWork() { return lastWork; }
        boolean isComplete() { return complete; }
        PostResult result() {
            if(!complete || result==null) throw new IllegalStateException("P07 post cursor is incomplete");
            return result;
        }
    }

    private static JsonObject buildPreModel(final PreCursor p) {
        final Context c=p.context;
        p.advanceCallsCounter=new JsonCounter(CALL_WIDTH);
        p.workStepsCounter=new JsonCounter(WORK_WIDTH);
        p.serializedUnitsCounter=new JsonCounter(UNITS_WIDTH);
        p.activeMillisCounter=new JsonCounter(TIME_WIDTH);
        final Vector<?> reached=(Vector<?>)field(c.search,"reachedPadIds");
        final Object reachedPth=field(c.branch,"reachedTerminals");
        final int reachedPthLength=Array.getLength(reachedPth);
        final Vector<TraceData> traces=p.prefixTraces;
        final Vector<RunData> runs=p.planarRuns;
        final PathNode[] nodes=p.pathNodes;
        final String[] nets=c.netOrder;
        return object("schema","p07-accepted-branch-pre-r1",
            "source",object("pass",c.pass,"netId",c.netId,
                "netIndexNextFetch",c.netIndexNextFetch,"netPosition",c.netPosition,
                "netOrder",stringArray(nets),"branchOrdinal",c.branchOrdinal,
                "policy",c.policy.name(),"templateHoleCount",
                    ((PcbBoardLayout)field(c.routeSession,"template")).getHoleCount(),
                "startPadAttachment",c.start.getAttachment().name(),
                "startCopperFaceMask",copperFaceMask(c.layout.getPad(c.start.getPadId())),
                "rootCopperFaceMask",copperFaceMask(c.layout.getPad(c.root.getPadId())),
                "startPadId",c.start.getPadId(),"rootPadId",c.root.getPadId(),
                "startX",c.start.getX(),"startY",c.start.getY(),
                "rootX",c.root.getX(),"rootY",c.root.getY(),
                "reachedPadIds",new JsonArray(reached.size(),new JsonArraySource() {
                    public Object get(int index) {
                        if(field(c.search,"reachedPadIds")!=reached || reached.size()>100000)
                            throw new IllegalStateException("P07 reached-pad source changed during pre serialization");
                        Object value=reached.get(index);
                        if(!(value instanceof String)) throw new IllegalArgumentException("Invalid P07 reached-pad row");
                        if(c.start.getPadId().equals(value)) p.reachedStartCount++;
                        return validString((String)value,"reached pad ID");
                    }
                }),
                "reachedPthPadIds",padArray(reachedPth,reachedPthLength),
                "grid",object("minX",intField(c.search,"minX"),"minY",intField(c.search,"minY"),
                    "width",intField(c.search,"width"),"height",intField(c.search,"height"),
                    "cells",intField(c.search,"cells"),"primaryLayer",
                    PcbCopperLayer.values()[intField(c.search,"primary")].name(),
                    "maximumVias",c.maximumVias,"maximumTransitions",c.maximumTransitions)),
            "seeds",new JsonRaw(c.seeds.toJson()),
            "prefixInventory",new JsonRaw(p.prefixBody),
            "prefixTraces",object("count",traces.size(),"rows",vectorArray(traces,
                new JsonArrayValue() { public Object get(Object value,int index) {
                    return ((TraceData)value).toJsonValue();
                }})),
            "path",object("nodeCount",nodes.length,"gridSteps",p.gridSteps,
                "length",p.length,"bends",p.bends,"transitionCount",p.transitionCount,
                "directDistance",p.directDistance,"tupleSha256",p.pathTupleSha256,
                "nodes",new JsonArray(nodes.length,new JsonArraySource() {
                    public Object get(int index) { return nodes[index].toJsonValue(); }
                }),
                "planarRuns",vectorArray(runs,new JsonArrayValue() {
                    public Object get(Object value,int index) { return ((RunData)value).toJsonValue(); }
                })),
            "goal",object("cellCount",p.goalCellCount,"topCount",p.goalsTop,
                "bottomCount",p.goalsBottom,"bothCount",p.goalsBoth,
                "faceBits",p.faceBitsJson,"faceBitsSha256",p.goalFaceBitsSha256,
                "foundGoal",Boolean.TRUE),
            "nativePublisher",object("stage",intField(c.publisher,"stage"),
                "qualityIndex",p.qualityIndex,"nodeCount",nodes.length,
                "length",p.qualityLength,"bends",p.qualityBends,
                "directDistance",p.directDistance,"endCell",p.endCell,
                "endX",gridX(c.search,p.endCell),"endY",gridY(c.search,p.endCell),
                "endLayer",p.endLayer,"endTransitions",p.endTransitions,
                "endTerminalPadId",p.endTerminalPadId),
            "work",object("advanceCalls",p.advanceCallsCounter,"workSteps",p.workStepsCounter,
                "prefixWorkSteps",p.prefixWorkSteps,"seedEntries",c.seeds.nodeCount(),
                "pathNodes",nodes.length,"goalCells",p.goals.length/2,
                "traceRows",traces.size(),"serializedUtf16Units",p.serializedUnitsCounter,
                "activeMillis",p.activeMillisCounter),
            "stability",object("liveBeforeVerified",Boolean.TRUE,"liveAfterVerified",Boolean.TRUE,
                "seedIdentityVerified",Boolean.TRUE,"pathChainVerified",Boolean.TRUE,
                "prefixLineageVerified",Boolean.TRUE,"goalMaskVerified",Boolean.TRUE,
                "traceIdentityVerified",Boolean.TRUE));
    }

    private static JsonObject buildPostModel(final PostCursor p) {
        final Context c=p.context;
        p.advanceCallsCounter=new JsonCounter(CALL_WIDTH);
        p.workStepsCounter=new JsonCounter(WORK_WIDTH);
        p.aggregateWorkCounter=new JsonCounter(WORK_WIDTH);
        p.serializedUnitsCounter=new JsonCounter(UNITS_WIDTH);
        p.activeMillisCounter=new JsonCounter(TIME_WIDTH);
        final Vector<P07PrefixAllocationDiagnostics.Row> rows=p.postRows;
        final Vector<String> created=p.createdHoleIds;
        final Vector<HoleBinding> holeBindings=p.retainedHoleBindings;
        final Vector<TraceData> traces=p.newTraces;
        final Vector<TraceBinding> traceBindings=p.retainedTraceBindings;
        final Vector<TransitionBinding> transitionBindings=p.transitionBindings;
        return object("schema","p07-accepted-branch-post-r1",
            "source",object("pass",c.pass,"netId",c.netId,"branchOrdinal",c.branchOrdinal,
                "searchBranches",intField(c.search,"branches"),
                "currentNet",stringField(c.search,"currentNet"),
                "searchBranchIsNull",Boolean.TRUE,"searchPublisherIsNull",Boolean.TRUE,
                "publisherStage",intField(c.publisher,"stage"),"startPadId",c.start.getPadId(),
                "reachedStartCount",p.reachedStartCount,"preHoleCount",p.pre.prefixRows.length,
                "preTraceCount",p.pre.prefixTraces.size()),
            "post",object("holeCount",rows.size(),"createdHoleCount",created.size(),
                "createdTraceCount",p.createdTraceCount,
                "holeRows",vectorArray(rows,new JsonArrayValue() {
                    public Object get(Object value,int index) { return holeRow((P07PrefixAllocationDiagnostics.Row)value); }
                }),
                "createdHoleIds",vectorArray(created,new JsonArrayValue() {
                    public Object get(Object value,int index) { return value; }
                }),
                "retainedHoleBindings",vectorArray(holeBindings,new JsonArrayValue() {
                    public Object get(Object value,int index) { return ((HoleBinding)value).toJsonValue(); }
                }),
                "createdTraceRows",vectorArray(traces,new JsonArrayValue() {
                    public Object get(Object value,int index) { return ((TraceData)value).toJsonValue(); }
                }),
                "retainedTraceBindings",vectorArray(traceBindings,new JsonArrayValue() {
                    public Object get(Object value,int index) { return ((TraceBinding)value).toJsonValue(); }
                }),
                "transitionBindings",vectorArray(transitionBindings,new JsonArrayValue() {
                    public Object get(Object value,int index) { return ((TransitionBinding)value).toJsonValue(); }
                })),
            "work",object("advanceCalls",p.advanceCallsCounter,"workSteps",p.workStepsCounter,
                "aggregateWorkSteps",p.aggregateWorkCounter,"holeRows",rows.size(),
                "traceRows",p.retainedTraceCount+p.createdTraceCount,
                "transitionMatches",transitionBindings.size(),
                "serializedUtf16Units",p.serializedUnitsCounter,"activeMillis",p.activeMillisCounter),
            "stability",object("liveBeforeVerified",Boolean.TRUE,"liveAfterVerified",Boolean.TRUE,
                "searchIdentityVerified",Boolean.TRUE,"publisherCompleted",Boolean.TRUE,
                "reachedStartVerified",Boolean.TRUE,"holeIdentityVerified",Boolean.TRUE,
                "traceIdentityVerified",Boolean.TRUE,"transitionBindingsVerified",Boolean.TRUE,
                "traceRunsVerified",Boolean.TRUE),
            "claims",object("avoidability","UNKNOWN","chronologyProof",Boolean.FALSE,
                "routeAdoption",Boolean.FALSE,"layoutQualification",Boolean.FALSE,"playable100",0));
    }

    private interface JsonArraySource { Object get(int index); }
    private interface JsonArrayValue { Object get(Object value,int index); }
    private static final class JsonObject {
        final Object[] pairs;
        JsonObject(Object[] pairs) {
            if(pairs==null || (pairs.length&1)!=0) throw new IllegalArgumentException("Malformed P07 JSON object");
            this.pairs=pairs;
        }
        int size() { return pairs.length/2; }
        String keyAt(int index) { return (String)pairs[index*2]; }
        Object valueAt(int index) { return pairs[index*2+1]; }
    }
    private static final class JsonArray {
        final int size;
        final JsonArraySource source;
        JsonArray(int size,JsonArraySource source) {
            if(size<0 || source==null) throw new IllegalArgumentException("Malformed P07 JSON array");
            this.size=size; this.source=source;
        }
    }
    private static final class JsonRaw {
        final String value;
        JsonRaw(String value) { if(value==null) throw new IllegalArgumentException("Missing P07 raw JSON"); this.value=value; }
    }
    private static final class JsonString {
        final CharSequence value;
        final boolean captureOffset;
        int contentOffset=-1;
        JsonString(CharSequence value) { this(value,false); }
        JsonString(CharSequence value,boolean captureOffset) {
            if(value==null) throw new IllegalArgumentException("Missing P07 JSON string");
            this.value=value; this.captureOffset=captureOffset;
        }
    }
    private static final class JsonCounter {
        final int width;
        int offset=-1;
        JsonCounter(int width) { this.width=width; }
    }
    private static JsonObject object(Object... pairs) { return new JsonObject(pairs); }
    private static JsonArray array(final Object[] values) {
        return new JsonArray(values.length,new JsonArraySource() {
            public Object get(int index) { return values[index]; }
        });
    }
    private static JsonArray stringArray(final String[] values) {
        return new JsonArray(values.length,new JsonArraySource() {
            public Object get(int index) { return values[index]; }
        });
    }
    private static JsonArray vectorArray(final Vector<?> values,final JsonArrayValue converter) {
        final int size=values.size();
        return new JsonArray(size,new JsonArraySource() {
            public Object get(int index) {
                if(values.size()!=size) throw new IllegalStateException("P07 JSON source vector changed during capture");
                return converter.get(values.get(index),index);
            }
        });
    }
    private static JsonArray padArray(final Object source,final int length) {
        if(source==null || !source.getClass().isArray() || length>100000 || Array.getLength(source)!=length)
            throw new IllegalArgumentException("Invalid P07 reached-PTH array");
        return new JsonArray(length,new JsonArraySource() {
            public Object get(int index) {
                Object pad=Array.get(source,index);
                if(!(pad instanceof PcbPadPlacement)) throw new IllegalArgumentException("Invalid P07 reached-PTH row");
                return validString(((PcbPadPlacement)pad).getPadId(),"reached PTH ID");
            }
        });
    }
    private static JsonObject holeRow(P07PrefixAllocationDiagnostics.Row row) {
        return object("index",row.index,"id",row.id,"netId",row.netId,"kind",row.kind.name(),
            "x",row.x,"y",row.y,"drillRadius",row.drillRadius,"landRadius",row.landRadius,
            "exposure",row.exposure.name(),"fingerprint",row.fingerprint,
            "creatorNetPosition",row.creatorNetPosition,"creatorBranchOrdinal",row.creatorBranchOrdinal,
            "piece",row.piece);
    }

    private static final class JsonStream {
        final StringBuilder builder=new StringBuilder(16384);
        private final Vector<JsonFrame> frames=new Vector<JsonFrame>();
        JsonStream(Object root) { push(root); }
        boolean pumpOne() {
            while(!frames.isEmpty()) {
                JsonFrame frame=frames.lastElement();
                if(frame instanceof JsonRawFrame) {
                    JsonRawFrame raw=(JsonRawFrame)frame;
                    if(raw.index>=raw.value.length()) { frames.remove(frames.size()-1); continue; }
                    append(raw.value.charAt(raw.index++)); return true;
                }
                if(frame instanceof JsonCounterFrame) {
                    JsonCounterFrame counter=(JsonCounterFrame)frame;
                    if(counter.index>=counter.counter.width) { frames.remove(frames.size()-1); continue; }
                    char ch=counter.index++==counter.counter.width-1?'0':' ';
                    append(ch); return true;
                }
                if(frame instanceof JsonStringFrame) {
                    JsonStringFrame string=(JsonStringFrame)frame;
                    if(string.pending!=null && string.pendingIndex<string.pending.length()) {
                        append(string.pending.charAt(string.pendingIndex++)); return true;
                    }
                    string.pending=null; string.pendingIndex=0;
                    if(string.state==0) { string.state=1; append('"'); return true; }
                    if(string.state==2) { frames.remove(frames.size()-1); continue; }
                    if(string.index>=string.value.length()) { string.state=2; append('"'); return true; }
                    char ch=string.value.charAt(string.index++);
                    String escaped=jsonEscape(ch);
                    if(escaped==null) append(ch);
                    else { string.pending=escaped; append(string.pending.charAt(string.pendingIndex++)); }
                    return true;
                }
                if(frame instanceof JsonArrayFrame) {
                    JsonArrayFrame array=(JsonArrayFrame)frame;
                    if(array.state==0) { array.state=1; append('['); return true; }
                    if(array.state==1) {
                        if(array.index>=array.value.size) { array.state=3; continue; }
                        if(array.index>0) { array.state=2; append(','); return true; }
                        array.state=2; continue;
                    }
                    if(array.state==2) {
                        Object child=array.value.source.get(array.index++);
                        array.state=1; push(child); continue;
                    }
                    if(array.state==3) { array.state=4; append(']'); return true; }
                    frames.remove(frames.size()-1); continue;
                }
                if(frame instanceof JsonObjectFrame) {
                    JsonObjectFrame object=(JsonObjectFrame)frame;
                    if(object.state==0) { object.state=1; append('{'); return true; }
                    if(object.state==1) {
                        if(object.index>=object.value.size()) { object.state=5; continue; }
                        if(object.index>0) { object.state=2; append(','); return true; }
                        object.state=2; continue;
                    }
                    if(object.state==2) {
                        object.state=3; push(object.value.keyAt(object.index)); continue;
                    }
                    if(object.state==3) { object.state=4; append(':'); return true; }
                    if(object.state==4) {
                        Object child=object.value.valueAt(object.index++);
                        object.state=1; push(child); continue;
                    }
                    if(object.state==5) { object.state=6; append('}'); return true; }
                    frames.remove(frames.size()-1); continue;
                }
                throw new IllegalStateException("Unknown P07 JSON writer frame");
            }
            return false;
        }
        void patch(JsonCounter counter,long value) {
            if(counter==null || counter.offset<0 || value<0)
                throw new IllegalStateException("Missing P07 JSON work counter");
            String text=Long.toString(value);
            if(text.length()>counter.width || counter.offset+counter.width>builder.length())
                throw new IllegalStateException("P07 JSON work counter exceeds its reserved field");
            for(int i=0;i<counter.width;i++) builder.setCharAt(counter.offset+i,' ');
            int at=counter.offset+counter.width-text.length();
            for(int i=0;i<text.length();i++) builder.setCharAt(at+i,text.charAt(i));
        }
        private void push(Object value) {
            if(value==null || value instanceof Boolean || value instanceof Number) {
                String text=value==null?"null":String.valueOf(value);
                frames.add(new JsonRawFrame(text));
            } else if(value instanceof String) frames.add(new JsonStringFrame((String)value));
            else if(value instanceof JsonString) {
                JsonString string=(JsonString)value;
                if(string.captureOffset) {
                    if(string.contentOffset>=0) throw new IllegalStateException("P07 JSON string offset captured twice");
                    string.contentOffset=builder.length()+1;
                }
                frames.add(new JsonStringFrame(string.value));
            }
            else if(value instanceof JsonRaw) frames.add(new JsonRawFrame(((JsonRaw)value).value));
            else if(value instanceof JsonCounter) {
                JsonCounter counter=(JsonCounter)value;
                if(counter.offset>=0) throw new IllegalStateException("P07 JSON counter emitted more than once");
                counter.offset=builder.length(); frames.add(new JsonCounterFrame(counter));
            } else if(value instanceof JsonObject) frames.add(new JsonObjectFrame((JsonObject)value));
            else if(value instanceof JsonArray) frames.add(new JsonArrayFrame((JsonArray)value));
            else throw new IllegalArgumentException("Unsupported P07 JSON value: "+value.getClass().getName());
        }
        private void append(char ch) {
            if(builder.length()>=MAX_TOTAL_WORK)
                throw new IllegalStateException("P07 JSON payload exceeds capture bound");
            builder.append(ch);
        }
    }
    private abstract static class JsonFrame { }
    private static final class JsonRawFrame extends JsonFrame {
        final String value; int index;
        JsonRawFrame(String value) { this.value=value; }
    }
    private static final class JsonCounterFrame extends JsonFrame {
        final JsonCounter counter; int index;
        JsonCounterFrame(JsonCounter counter) { this.counter=counter; }
    }
    private static final class JsonStringFrame extends JsonFrame {
        final CharSequence value; int index,state,pendingIndex; String pending;
        JsonStringFrame(CharSequence value) { this.value=value; }
    }
    private static final class JsonArrayFrame extends JsonFrame {
        final JsonArray value; int index,state;
        JsonArrayFrame(JsonArray value) { this.value=value; }
    }
    private static final class JsonObjectFrame extends JsonFrame {
        final JsonObject value; int index,state;
        JsonObjectFrame(JsonObject value) { this.value=value; }
    }
    private static String jsonEscape(char ch) {
        if(ch=='"') return "\\\"";
        if(ch=='\\') return "\\\\";
        if(ch=='\b') return "\\b";
        if(ch=='\f') return "\\f";
        if(ch=='\n') return "\\n";
        if(ch=='\r') return "\\r";
        if(ch=='\t') return "\\t";
        if(ch<0x20 || ch==0x7f || Character.isSurrogate(ch)) {
            return new String(new char[] {'\\','u',JSON_HEX.charAt((ch>>>12)&15),
                JSON_HEX.charAt((ch>>>8)&15),JSON_HEX.charAt((ch>>>4)&15),JSON_HEX.charAt(ch&15)});
        }
        return null;
    }

    private static String fieldPrefix(String value) {
        if(value==null) return "-1:";
        validNullableString(value,"fingerprint string");
        return value.length()+":";
    }
    private static String validString(String value,String name) {
        if(value==null || value.length()==0 || value.length()>MAX_ID_LENGTH)
            throw new IllegalArgumentException("Invalid P07 "+name);
        return value;
    }
    private static String validNullableString(String value,String name) {
        if(value!=null && value.length()>MAX_ID_LENGTH)
            throw new IllegalArgumentException("Invalid P07 "+name);
        return value;
    }
    private static int copperFaceMask(PcbPadPlacement pad) {
        if(pad==null) throw new IllegalArgumentException("Missing P07 source pad");
        int mask=0;
        if(PcbCopperAccess.hasCopper(pad,PcbCopperLayer.TOP)) mask|=1;
        if(PcbCopperAccess.hasCopper(pad,PcbCopperLayer.BOTTOM)) mask|=2;
        return mask;
    }
    private static int cell(Object search,PcbPadPlacement pad) {
        int width=intField(search,"width"),minX=intField(search,"minX"),minY=intField(search,"minY");
        int dx=pad.getX()-minX,dy=pad.getY()-minY;
        if(dx<0 || dy<0 || dx%GRID!=0 || dy%GRID!=0 || dx/GRID>=width ||
                dy/GRID>=intField(search,"height"))
            throw new IllegalArgumentException("P07 source pad is outside its captured grid");
        return (dy/GRID)*width+dx/GRID;
    }
    private static int gridX(Object search,int cell) {
        return intField(search,"minX")+(cell%intField(search,"width"))*GRID;
    }
    private static int gridY(Object search,int cell) {
        return intField(search,"minY")+(cell/intField(search,"width"))*GRID;
    }
    private static int directionBetween(int from,int to,int width) {
        int dx=to%width-from%width,dy=to/width-from/width;
        if(dx==0 && dy==-1) return 0;
        if(dx==1 && dy==0) return 1;
        if(dx==0 && dy==1) return 2;
        if(dx==-1 && dy==0) return 3;
        throw new IllegalArgumentException("P07 path edge is not a cardinal grid step");
    }
    private static int nodeKey(int cell,int layer,int direction,int transitions,int cells) {
        return (((transitions*2+layer)*5+direction)*cells)+cell;
    }
    private static String runLayerName(int layer) {
        if(layer<0 || layer>=PcbCopperLayer.values().length)
            throw new IllegalArgumentException("Invalid P07 run layer");
        return PcbCopperLayer.values()[layer].name();
    }
    private static int vectorSize(Object value) {
        if(!(value instanceof Vector)) throw new IllegalArgumentException("Expected P07 vector source");
        return ((Vector<?>)value).size();
    }
    private static int vectorModCount(Vector<?> vector) {
        try {
            Field f=java.util.AbstractList.class.getDeclaredField("modCount");
            f.setAccessible(true); return f.getInt(vector);
        } catch(Exception failure) { throw new IllegalStateException("Cannot inspect P07 trace-vector generation",failure); }
    }
    private static int[] readNode(Object node) {
        return new int[] {intField(node,"cell"),intField(node,"layer"),intField(node,"direction"),
            intField(node,"transitions"),intField(node,"cost"),intField(node,"heuristic"),
            intField(node,"serial"),intField(node,"key")};
    }
    private static int intField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof Integer)) throw new IllegalArgumentException("Invalid P07 integer source field "+name);
        return ((Integer)value).intValue();
    }
    private static String stringField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof String)) throw new IllegalArgumentException("Invalid P07 string source field "+name);
        return validString((String)value,name);
    }
    private static Object field(Object target,String name) {
        if(target==null) throw new IllegalArgumentException("Cannot read P07 source field from null: "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
            } catch(NoSuchFieldException missing) { type=type.getSuperclass(); }
            catch(Exception failure) { throw new IllegalStateException("Cannot read P07 source field "+name,failure); }
        }
        throw new IllegalStateException("Missing P07 source field "+name);
    }
    private static MessageDigest messageDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }
    private static String hex(byte[] bytes) {
        char[] alphabet="0123456789abcdef".toCharArray();
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte value:bytes) { int b=value&255; out.append(alphabet[b>>>4]).append(alphabet[b&15]); }
        return out.toString();
    }
}
