package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Bounded two-face routing engine; adoption remains controlled by the physical-policy owner. */
final class PcbLayerRoutingPrototype {
    enum Policy {
        ONE_LAYER(0,0,0,0,false), SPARSE_LINK(0,0,0,0,true),
        RESTRICTED_TWO_LAYER(2,8,160,8,true), FULLER_TWO_LAYER(4,48,60,2,true);
        final int transitions, vias, viaCost, secondaryCost;
        final boolean underpasses;
        Policy(int transitions,int vias,int viaCost,int secondaryCost,boolean underpasses) {
            this.transitions=transitions; this.vias=vias; this.viaCost=viaCost;
            this.secondaryCost=secondaryCost; this.underpasses=underpasses;
        }
    }
    static final int GRID=10, TURN_COST=35, MAX_CELLS=250000, MAX_EXPANSIONS=1000000;
    static final int MAX_BRANCH_EXPANSIONS=100000, ORDERINGS=3;
    enum OrderingProgress { WORKING, ROUTE_READY, COMPLETE }
    /** The single production arithmetic used both by Search and pre-route admission. */
    static final class GridAdmission {
        final int width,height;
        final long cells;
        final boolean eligible;
        final String reason;
        private GridAdmission(int width,int height,long cells,boolean eligible,String reason) {
            this.width=width; this.height=height; this.cells=cells;
            this.eligible=eligible; this.reason=reason;
        }
    }
    static GridAdmission gridAdmission(Rectangle outline) {
        if(outline==null) throw new IllegalArgumentException("Missing P07 routing outline");
        int width=(outline.width-2*GRID)/GRID+1;
        int height=(outline.height-2*GRID)/GRID+1;
        long cells=(long)width*height;
        boolean eligible=width>=1 && height>=1 && cells<=MAX_CELLS;
        return new GridAdmission(width,height,cells,eligible,eligible?null:"GRID_LIMIT");
    }
    static final int WORK_QUANTUM=32768;
    private static final int PATH_OCCUPANCY_CHUNK=256;
    static final class Result {
        final PcbBoardLayout layout;
        final String outcome;
        final int expansions, orderings, vias, links;
        Result(PcbBoardLayout layout,String outcome,int expansions,int orderings,int links) {
            this.layout=layout; this.outcome=outcome; this.expansions=expansions;
            this.orderings=orderings; this.links=links; vias=layout==null?0:layout.getHoles().size();
        }
        boolean accepted() { return layout!=null; }
    }
    /** Terminal ordering-search facts; intentionally contains no retained route layout. */
    static final class OrderingSummary {
        final int expansions,orderings,completedOrderings,yieldedOrderings;
        final boolean guidanceStarted,comparisonComplete;
        final String termination;
        private OrderingSummary(int expansions,int orderings,int completedOrderings,int yieldedOrderings,
                boolean guidanceStarted,boolean comparisonComplete,String termination) {
            this.expansions=expansions; this.orderings=orderings;
            this.completedOrderings=completedOrderings; this.yieldedOrderings=yieldedOrderings;
            this.guidanceStarted=guidanceStarted; this.comparisonComplete=comparisonComplete;
            this.termination=termination;
        }
    }
    /** Optional per-request observations; absent from every production overload by default. */
    interface Diagnostics {
        void onTerminalBranch(BranchSnapshot snapshot);
    }
    /** One opt-in, per-request hook for a complete failed-prefix graph only. */
    interface FixedPrefixGraphObserver {
        boolean select(FixedPrefixBranchLineage lineage);
        void onGraph(FixedPrefixGraphSnapshot snapshot);
    }
    /** Immutable source identity supplied by the actual medium generator owner. */
    static final class FixedPrefixCaptureContext {
        private final long layoutSeed,routingSeed;
        private final int[] eligiblePlacementAttempts;
        private final int selectedPlacementIndex,routeRequestIndex;
        private final String rawPlacementFingerprint;
        FixedPrefixCaptureContext(long layoutSeed,long routingSeed,int[] eligiblePlacementAttempts,
                int selectedPlacementAttempt,int routeRequestIndex,String rawPlacementFingerprint) {
            if(eligiblePlacementAttempts==null || eligiblePlacementAttempts.length<1 ||
                    eligiblePlacementAttempts.length>6 || selectedPlacementAttempt<0 ||
                    routeRequestIndex<0 || routeRequestIndex>=MediumBoardPhysicalPolicy.ROUTING_CANDIDATES || rawPlacementFingerprint==null ||
                    rawPlacementFingerprint.length()==0 || rawPlacementFingerprint.length()>1048576)
                throw new IllegalArgumentException("Invalid actual fixed-prefix capture context");
            int selectedIndex=-1;
            for(int i=0;i<eligiblePlacementAttempts.length;i++) {
                int attempt=eligiblePlacementAttempts[i];
                if(attempt<0 || attempt>=PcbPlacementPlanner.OUTLINE_CANDIDATES)
                    throw new IllegalArgumentException("Invalid fixed-prefix eligible placement attempt");
                if(attempt==selectedPlacementAttempt) selectedIndex=i;
                for(int j=0;j<i;j++) if(eligiblePlacementAttempts[j]==attempt)
                    throw new IllegalArgumentException("Duplicate fixed-prefix eligible placement attempt");
            }
            if(selectedIndex<0) throw new IllegalArgumentException("Selected fixed-prefix placement is not eligible");
            this.layoutSeed=layoutSeed; this.routingSeed=routingSeed;
            this.eligiblePlacementAttempts=copyFixedPrefixInts(eligiblePlacementAttempts);
            this.selectedPlacementIndex=selectedIndex;
            this.routeRequestIndex=routeRequestIndex;
            this.rawPlacementFingerprint=rawPlacementFingerprint;
        }
        long layoutSeed() { return layoutSeed; }
        long routingSeed() { return routingSeed; }
        int[] eligiblePlacementAttempts() { return copyFixedPrefixInts(eligiblePlacementAttempts); }
        int selectedPlacementIndex() { return selectedPlacementIndex; }
        int selectedPlacementAttempt() { return eligiblePlacementAttempts[selectedPlacementIndex]; }
        int routeRequestIndex() { return routeRequestIndex; }
        String rawPlacementFingerprint() { return rawPlacementFingerprint; }
    }
    private static int[] copyFixedPrefixInts(int[] source) {
        int[] copy=new int[source.length];
        System.arraycopy(source,0,copy,0,source.length);
        return copy;
    }
    private static String[] copyFixedPrefixStrings(String[] source) {
        String[] copy=new String[source.length];
        System.arraycopy(source,0,copy,0,source.length);
        return copy;
    }
    /** Typed, immutable copy of a hole in the already-published routing prefix. */
    static final class FixedPrefixHole {
        private final String id,netId;
        private final PcbBoardHole.Kind kind;
        private final int x,y,drillRadius,landRadius;
        private final PcbCopperAccess.Exposure exposure;
        private FixedPrefixHole(PcbBoardHole hole) {
            if(hole==null) throw new IllegalArgumentException("Missing fixed-prefix hole");
            id=hole.id; netId=hole.netId; kind=hole.kind; x=hole.x; y=hole.y;
            drillRadius=hole.drillRadius; landRadius=hole.landRadius; exposure=hole.exposure;
        }
        String id() { return id; }
        String netId() { return netId; }
        PcbBoardHole.Kind kind() { return kind; }
        int x() { return x; }
        int y() { return y; }
        int drillRadius() { return drillRadius; }
        int landRadius() { return landRadius; }
        PcbCopperAccess.Exposure exposure() { return exposure; }
    }
    /** Immutable physical identity of the branch prefix at one terminal failure. */
    static final class FixedPrefixBranchLineage {
        private final FixedPrefixCaptureContext context;
        private final int pass,branchOrdinal,startX,startY,rootX,rootY,branchExpanded,totalRouteExpansions;
        private final String netId,startPadId,rootPadId,terminalReason,prefixGeometryFingerprint;
        private final String[] reachedPadIds,reachedPthPadIds;
        private final FixedPrefixHole[] prefixHoles;
        private final int[] prefixHoleCountsByKind;
        private final int prefixViaCount;
        private FixedPrefixBranchLineage(FixedPrefixCaptureContext context,int pass,int branchOrdinal,String netId,
                PcbPadPlacement start,PcbPadPlacement root,String[] reachedPadIds,String[] reachedPthPadIds,
                int branchExpanded,int totalRouteExpansions,
                PcbBoardHole[] holes,String terminalReason,String prefixGeometryFingerprint) {
            if(context==null || pass<0 || pass>=ORDERINGS || branchOrdinal<1 || netId==null || start==null ||
                    root==null || reachedPadIds==null || reachedPthPadIds==null || holes==null || terminalReason==null ||
                    prefixGeometryFingerprint==null || branchExpanded<0 || totalRouteExpansions<0)
                throw new IllegalArgumentException("Invalid fixed-prefix branch lineage");
            this.context=context;
            this.pass=pass; this.branchOrdinal=branchOrdinal; this.netId=netId;
            startPadId=start.getPadId(); rootPadId=root.getPadId();
            startX=start.getX(); startY=start.getY(); rootX=root.getX(); rootY=root.getY();
            this.branchExpanded=branchExpanded; this.totalRouteExpansions=totalRouteExpansions;
            this.reachedPadIds=copyFixedPrefixStrings(reachedPadIds);
            this.reachedPthPadIds=copyFixedPrefixStrings(reachedPthPadIds);
            this.prefixHoles=new FixedPrefixHole[holes.length];
            this.prefixHoleCountsByKind=new int[PcbBoardHole.Kind.values().length];
            int vias=0;
            for(int i=0;i<holes.length;i++) {
                this.prefixHoles[i]=new FixedPrefixHole(holes[i]);
                prefixHoleCountsByKind[holes[i].kind.ordinal()]++;
                if(holes[i].kind==PcbBoardHole.Kind.VIA) vias++;
            }
            prefixViaCount=vias; this.terminalReason=terminalReason;
            this.prefixGeometryFingerprint=prefixGeometryFingerprint;
        }
        FixedPrefixCaptureContext context() { return context; }
        int pass() { return pass; }
        int branchOrdinal() { return branchOrdinal; }
        String netId() { return netId; }
        String startPadId() { return startPadId; }
        String rootPadId() { return rootPadId; }
        int startX() { return startX; }
        int startY() { return startY; }
        int rootX() { return rootX; }
        int rootY() { return rootY; }
        int branchExpanded() { return branchExpanded; }
        int totalRouteExpansions() { return totalRouteExpansions; }
        String[] reachedPadIds() { return copyFixedPrefixStrings(reachedPadIds); }
        String[] reachedPthPadIds() { return copyFixedPrefixStrings(reachedPthPadIds); }
        int prefixHoleCount() { return prefixHoles.length; }
        FixedPrefixHole prefixHole(int index) {
            if(index<0 || index>=prefixHoles.length)
                throw new IllegalArgumentException("Invalid fixed-prefix hole index");
            return prefixHoles[index];
        }
        int[] prefixHoleCountsByKind() { return copyFixedPrefixInts(prefixHoleCountsByKind); }
        int prefixViaCount() { return prefixViaCount; }
        String terminalReason() { return terminalReason; }
        String prefixGeometryFingerprint() { return prefixGeometryFingerprint; }
    }
    /** Completed immutable mask data; no live router or mutable arrays escape. */
    static final class FixedPrefixGraphSnapshot {
        private final FixedPrefixCaptureContext context;
        private final FixedPrefixBranchLineage lineage;
        private final int gridWidth,gridHeight,originX,originY,primaryLayer,startCell,initialFacesMask;
        private final byte[] topStepMasks,bottomStepMasks;
        private final boolean[] goalFaces,viaAllowed;
        private final int captureWorkUnits,copyEntries,goalProbes,ordinaryEdgeProbes,viaProbes,
            comparisonEntries;
        private final long captureActiveElapsedMillis;
        private FixedPrefixGraphSnapshot(FixedPrefixCaptureContext context,
                FixedPrefixBranchLineage lineage,int gridWidth,int gridHeight,int originX,int originY,
                int primaryLayer,int startCell,int initialFacesMask,byte[] topStepMasks,
                byte[] bottomStepMasks,boolean[] goalFaces,boolean[] viaAllowed,int captureWorkUnits,
                int copyEntries,int goalProbes,int ordinaryEdgeProbes,int viaProbes,
                int comparisonEntries,long captureActiveElapsedMillis) {
            if(context==null || lineage==null || gridWidth<1 || gridHeight<1 || primaryLayer<0 ||
                    primaryLayer>1 || (long)gridWidth*gridHeight>MAX_CELLS || topStepMasks==null ||
                    bottomStepMasks==null || goalFaces==null || viaAllowed==null || captureWorkUnits<1 ||
                    copyEntries<0 || goalProbes<0 || ordinaryEdgeProbes<0 || viaProbes<0 ||
                    comparisonEntries<0 || captureActiveElapsedMillis<0)
                throw new IllegalArgumentException("Invalid completed fixed-prefix graph");
            int cells=gridWidth*gridHeight;
            if(lineage.context()!=context || startCell<0 || startCell>=cells || initialFacesMask!=3 ||
                    topStepMasks.length!=cells || bottomStepMasks.length!=cells ||
                    goalFaces.length!=cells*2 || viaAllowed.length!=cells)
                throw new IllegalArgumentException("Fixed-prefix graph mask dimensions differ");
            this.context=context; this.lineage=lineage;
            this.gridWidth=gridWidth; this.gridHeight=gridHeight; this.originX=originX; this.originY=originY;
            this.primaryLayer=primaryLayer; this.startCell=startCell; this.initialFacesMask=initialFacesMask;
            // Ownership transfers from the retired capture task; only scalar accessors are public.
            this.topStepMasks=topStepMasks; this.bottomStepMasks=bottomStepMasks;
            this.goalFaces=goalFaces; this.viaAllowed=viaAllowed;
            this.captureWorkUnits=captureWorkUnits; this.copyEntries=copyEntries;
            this.goalProbes=goalProbes; this.ordinaryEdgeProbes=ordinaryEdgeProbes; this.viaProbes=viaProbes;
            this.comparisonEntries=comparisonEntries;
            this.captureActiveElapsedMillis=captureActiveElapsedMillis;
        }
        FixedPrefixCaptureContext context() { return context; }
        FixedPrefixBranchLineage lineage() { return lineage; }
        int gridWidth() { return gridWidth; }
        int gridHeight() { return gridHeight; }
        int gridPitch() { return GRID; }
        int originX() { return originX; }
        int originY() { return originY; }
        int primaryLayer() { return primaryLayer; }
        int startCell() { return startCell; }
        int initialFacesMask() { return initialFacesMask; }
        int cellCount() { return viaAllowed.length; }
        int topStepMaskAt(int cell) {
            if(cell<0 || cell>=cellCount()) throw new IllegalArgumentException("Invalid fixed-prefix cell");
            return topStepMasks[cell]&15;
        }
        int bottomStepMaskAt(int cell) {
            if(cell<0 || cell>=cellCount()) throw new IllegalArgumentException("Invalid fixed-prefix cell");
            return bottomStepMasks[cell]&15;
        }
        boolean goalAt(int layer,int cell) {
            if(layer<0 || layer>1 || cell<0 || cell>=cellCount())
                throw new IllegalArgumentException("Invalid fixed-prefix goal state");
            return goalFaces[layer*cellCount()+cell];
        }
        boolean viaAllowedAt(int cell) {
            if(cell<0 || cell>=cellCount()) throw new IllegalArgumentException("Invalid fixed-prefix cell");
            return viaAllowed[cell];
        }
        int captureWorkUnits() { return captureWorkUnits; }
        int copyEntries() { return copyEntries; }
        int goalProbes() { return goalProbes; }
        int ordinaryEdgeProbes() { return ordinaryEdgeProbes; }
        int viaProbes() { return viaProbes; }
        int comparisonEntries() { return comparisonEntries; }
        long captureActiveElapsedMillis() { return captureActiveElapsedMillis; }
    }
    /** Immutable scalars captured once when one real branch terminates. */
    static final class BranchSnapshot {
        final int pass;
        final String netId,startPadId,rootPadId;
        final int startX,startY,rootX,rootY,gridWidth,gridHeight,goalsTop,goalsBottom;
        final int prefixViaCount,reachedPadCount;
        final int initialManhattanDistance,minimumExploredManhattanDistance;
        final int branchExpanded,totalExpanded,expandedTop,expandedBottom;
        final int frontierCount,bestStateCount;
        final String terminalReason;
        final boolean relaxedBoundStarted,relaxedBoundComplete;
        final boolean relaxedBoundObservationChecked,relaxedBoundLiveLayoutStable,
            relaxedBoundRouterStateStable,relaxedBoundRouterEffectsStable;
        final int relaxedBoundWorkUnits,relaxedBoundOrdinaryProbes,relaxedBoundViaProbes,
            relaxedBoundRefreshes;
        private BranchSnapshot(int pass,String netId,String startPadId,String rootPadId,
                int startX,int startY,int rootX,int rootY,int gridWidth,int gridHeight,
                int goalsTop,int goalsBottom,int prefixViaCount,int reachedPadCount,
                int initialManhattanDistance,
                int minimumExploredManhattanDistance,int branchExpanded,int totalExpanded,
                int expandedTop,int expandedBottom,int frontierCount,int bestStateCount,
                String terminalReason,boolean relaxedBoundStarted,boolean relaxedBoundComplete,
                boolean relaxedBoundObservationChecked,boolean relaxedBoundLiveLayoutStable,
                boolean relaxedBoundRouterStateStable,boolean relaxedBoundRouterEffectsStable,
                int relaxedBoundWorkUnits,int relaxedBoundOrdinaryProbes,int relaxedBoundViaProbes,
                int relaxedBoundRefreshes) {
            this.pass=pass; this.netId=netId; this.startPadId=startPadId; this.rootPadId=rootPadId;
            this.startX=startX; this.startY=startY; this.rootX=rootX; this.rootY=rootY;
            this.gridWidth=gridWidth; this.gridHeight=gridHeight;
            this.goalsTop=goalsTop; this.goalsBottom=goalsBottom;
            this.prefixViaCount=prefixViaCount; this.reachedPadCount=reachedPadCount;
            this.initialManhattanDistance=initialManhattanDistance;
            this.minimumExploredManhattanDistance=minimumExploredManhattanDistance;
            this.branchExpanded=branchExpanded; this.totalExpanded=totalExpanded;
            this.expandedTop=expandedTop; this.expandedBottom=expandedBottom;
            this.frontierCount=frontierCount; this.bestStateCount=bestStateCount;
            this.terminalReason=terminalReason;
            this.relaxedBoundStarted=relaxedBoundStarted;
            this.relaxedBoundComplete=relaxedBoundComplete;
            this.relaxedBoundObservationChecked=relaxedBoundObservationChecked;
            this.relaxedBoundLiveLayoutStable=relaxedBoundLiveLayoutStable;
            this.relaxedBoundRouterStateStable=relaxedBoundRouterStateStable;
            this.relaxedBoundRouterEffectsStable=relaxedBoundRouterEffectsStable;
            this.relaxedBoundWorkUnits=relaxedBoundWorkUnits;
            this.relaxedBoundOrdinaryProbes=relaxedBoundOrdinaryProbes;
            this.relaxedBoundViaProbes=relaxedBoundViaProbes;
            this.relaxedBoundRefreshes=relaxedBoundRefreshes;
        }
    }
    private static final class Exhausted extends RuntimeException {
        Exhausted(String reason) { super(reason); }
    }
    private static final class Budget {
        final int maximum;
        int expanded;
        Budget(int maximum) { this.maximum=maximum; }
    }
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return begin(board,placement,policy,observer,MAX_EXPANSIONS);
    }
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        return begin(board,placement,policy,observer,maximum,policy==null?0:policy.vias);
    }
    /** A request may reduce the policy's via allowance without changing the policy. */
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias) {
        return begin(board,placement,policy,observer,maximum,maximumVias,null);
    }
    /** Optional terminal-branch receipt for one owned request; no shared diagnostic state. */
    static Session begin(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
            Diagnostics diagnostics) {
        return new Session(board,placement,policy,observer,maximum,maximumVias,diagnostics);
    }
    /** Opt-in stream used only where stronger relaxed guidance began within the request. */
    static Session beginOrderingCandidates(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        if(policy!=Policy.FULLER_TWO_LAYER)
            throw new IllegalArgumentException("P07 ordering stream requires FULLER_TWO_LAYER");
        return beginOrderingCandidates(board,placement,policy,observer,MAX_EXPANSIONS,policy.vias,null);
    }
    /** The caller may reduce the existing expansion/via limits for bounded canaries. */
    static Session beginOrderingCandidates(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
            Diagnostics diagnostics) {
        if(policy!=Policy.FULLER_TWO_LAYER)
            throw new IllegalArgumentException("P07 ordering stream requires FULLER_TWO_LAYER");
        return new Session(board,placement,policy,observer,maximum,maximumVias,diagnostics,true);
    }
    /** Generator-owned, opt-in capture; its terminal hook is separate from ordinary diagnostics. */
    static Session beginOrderingCandidatesWithFixedPrefixObservation(TroubleshootBoard board,
            PcbBoardLayout placement,Policy policy,SeededPcbLayoutGenerator.AttemptObserver observer,
            int maximum,int maximumVias,Diagnostics diagnostics,FixedPrefixCaptureContext context,
            FixedPrefixGraphObserver captureObserver) {
        if(policy!=Policy.FULLER_TWO_LAYER || context==null || captureObserver==null)
            throw new IllegalArgumentException("Invalid actual P07 fixed-prefix observation request");
        return new Session(board,placement,policy,observer,maximum,maximumVias,diagnostics,true,
            context,captureObserver);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return route(board,placement,policy,observer,MAX_EXPANSIONS);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum) {
        return route(board,placement,policy,observer,maximum,policy==null?0:policy.vias);
    }
    static Result route(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias) {
        Session session=begin(board,placement,policy,observer,maximum,maximumVias);
        while(!session.advanceSlice()) { }
        return session.result();
    }
    private static final Diagnostics DETACHED_DIAGNOSTICS=new Diagnostics() {
        public void onTerminalBranch(BranchSnapshot snapshot) { }
    };
    private static final FixedPrefixGraphObserver DETACHED_PREFIX_OBSERVER=new FixedPrefixGraphObserver() {
        public boolean select(FixedPrefixBranchLineage lineage) { return false; }
        public void onGraph(FixedPrefixGraphSnapshot snapshot) { }
    };

    /** Opt-in diagnostic state copy. No normal route overload calls this method. */
    static DetachedBranchCopyTask beginDetachedBranchCopy(Session source,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        return beginDetachedBranchCopy(source,observer,source==null?0:source.maximumVias);
    }

    /** Opt-in copy with a bounded VIA allowance for the detached owner only. */
    static DetachedBranchCopyTask beginDetachedBranchCopy(Session source,
            SeededPcbLayoutGenerator.AttemptObserver observer,int maximumVias) {
        return new DetachedBranchCopyTask(source,observer,maximumVias);
    }

    /** Copies an actual initial branch; never computes or injects a found path. */
    static final class DetachedBranchCopyTask {
        private Session source;
        private Search original;
        private Search.BranchSearch branch;
        private TurnBoundWorkspace workspace;
        private Budget sourceBudget;
        private PcbBoardLayout sourceLayout,sourceTemplate;
        private SeededPcbLayoutGenerator.AttemptObserver observer;
        private PcbBoardLayout.CompactionCopyTask layoutTask,templateTask;
        private PcbNetRouter.Router.ChargedFixedGraphCopyTask routerTask;
        private PcbNetRouter.Router[] sourceFaces;
        private final long[] faceRevisions=new long[2];
        private long layoutRevision,templateRevision;
        private boolean[] sourceGoal,goal;
        private int[][] sourceInts,copiedInts;
        private Node firstSeed,secondSeed;
        private Vector<String> nets,pending,reached;
        private PcbPadPlacement[] pads,terminals;
        private PcbComponentPlacement[] parts;
        private PcbBoardHole[] holes;
        private Rectangle[] bounds;
        private Session owner;
        private Search copy;
        private Search.BranchSearch copiedBranch;
        private DetachedBranchReplay result;
        private int stage,array=-1,index,metaIndex,face,lastWork;
        private int serial,expanded,maximum,branches,segments,netIndex,pass;
        private final int maximumVias;
        private int netCount,pendingCount,reachedCount;
        private long credits,work,copied;
        private boolean cancelled,complete;

        private DetachedBranchCopyTask(Session source,SeededPcbLayoutGenerator.AttemptObserver observer,
                int maximumVias) {
            if(source==null || observer==null || observer==source.observer || source.complete ||
                    source.policy!=Policy.FULLER_TWO_LAYER || source.search==null ||
                    source.pendingFixedPrefixCapture!=null || source.pendingFixedPrefixFailure!=null)
                throw new IllegalArgumentException("Detached copy requires an independent initial FULLER owner");
            this.source=source; this.observer=observer; original=source.search; branch=original.branch;
            if(branch==null || original.publisher!=null || original.complete ||
                    source.candidate!=original.layout || original.owner!=source ||
                    original.budget!=source.budget || branch.expanded!=0 || branch.found!=null ||
                    branch.foundTerminal!=null || branch.terminalEmitted || branch.relaxedBoundStarted ||
                    branch.relaxedQueryFaces!=null || branch.relaxedPristineFaces!=null ||
                    branch.relaxedDistanceBound!=null || branch.queue.size()!=2 || branch.best.size()!=2 ||
                    branch.queue.values.length!=128 || branch.best.values.length!=128 ||
                    original.cells<1 || original.cells>MAX_CELLS || original.nets==null ||
                    original.currentPadIds==null || original.reachedPadIds==null ||
                    original.currentRoot==null || source.turnBoundWorkspace==null)
                throw new IllegalArgumentException("Detached copy requires the pre-first-work branch frontier");
            int prefixHoleCount=branch.holes.length;
            if(prefixHoleCount!=original.layout.getHoleCount() ||
                    maximumVias<prefixHoleCount || maximumVias>source.maximumVias ||
                    maximumVias>original.maximumVias || maximumVias>source.policy.vias)
                throw new IllegalArgumentException(
                    "Detached copy VIA cap must fit the source policy and initial prefix");
            this.maximumVias=maximumVias;
            firstSeed=branch.queue.values[0]; secondSeed=branch.queue.values[1];
            requireSeed(firstSeed); requireSeed(secondSeed);
            if(firstSeed.layer==secondSeed.layer || branch.best.get(firstSeed.key)!=firstSeed ||
                    branch.best.get(secondSeed.key)!=secondSeed)
                throw new IllegalArgumentException("Detached copy requires both original root seed states");
            workspace=source.turnBoundWorkspace;
            workspace.requireGrid(original.width,original.height,original.cells);
            sourceGoal=branch.goal;
            sourceInts=new int[][]{branch.distance,workspace.north,workspace.east,workspace.south,
                workspace.west,workspace.axisMin,workspace.rowMin};
            copiedInts=new int[7][];
            if(sourceGoal.length!=2L*original.cells || branch.turnLowerBound==null ||
                    branch.turnLowerBound.manhattan!=branch.distance ||
                    branch.turnLowerBound.north!=workspace.north || branch.turnLowerBound.east!=workspace.east ||
                    branch.turnLowerBound.south!=workspace.south || branch.turnLowerBound.west!=workspace.west ||
                    branch.turnLowerBound.axisMin!=workspace.axisMin || original.faces[0]==null || original.faces[1]==null)
                throw new IllegalArgumentException("Detached copy has incompatible initial arrays");
            for(int i=0;i<sourceInts.length;i++) if(sourceInts[i]==null || sourceInts[i].length!=original.cells)
                throw new IllegalArgumentException("Detached copy has incompatible turn workspace");
            if((long)original.pads.length+original.parts.length+branch.reachedTerminals.length+
                    branch.holes.length*5L+original.nets.size()+original.currentPadIds.size()+
                    original.reachedPadIds.size()>32768 || branch.holes.length>48)
                throw new IllegalArgumentException("Detached copy metadata exceeds its bounded envelope");
            sourceBudget=source.budget;
            serial=original.serial; expanded=sourceBudget.expanded; maximum=sourceBudget.maximum;
            branches=original.branches; segments=original.segments; netIndex=original.netIndex; pass=original.pass;
            if(maximum<1 || maximum>MAX_EXPANSIONS || expanded<0 || expanded>maximum)
                throw new IllegalArgumentException("Detached copy has an invalid consumed global budget");
            nets=original.nets; pending=original.currentPadIds; reached=original.reachedPadIds;
            netCount=nets.size(); pendingCount=pending.size(); reachedCount=reached.size();
            sourceLayout=original.layout; sourceTemplate=source.template;
            layoutRevision=sourceLayout.observationRevision(); templateRevision=sourceTemplate.observationRevision();
            sourceFaces=new PcbNetRouter.Router[]{original.faces[0],original.faces[1]};
            for(int i=0;i<2;i++) faceRevisions[i]=sourceFaces[i].observationRevision();
            layoutTask=sourceLayout.beginCompactionCopy();
            templateTask=sourceTemplate.beginCompactionCopy();
        }

        private void requireSeed(Node node) {
            if(node==null || node.previous!=null || node.direction!=4 || node.transitions!=0 ||
                    node.cell!=original.cell(branch.start) || node.layer<0 || node.layer>1 ||
                    node.cost!=(node.layer==original.primary?0:original.policy.secondaryCost))
                throw new IllegalArgumentException("Detached copy has a noninitial seed");
        }
        boolean advance(int maximumWork) {
            lastWork=0;
            if(maximumWork<1 || maximumWork>32768) throw new IllegalArgumentException("Detached copy quantum must be in 1..32768");
            if(complete) return true;
            if(cancelled) throw new IllegalStateException("Detached copy was cancelled");
            try {
                requireSource(); observer.check(pass); requireSource();
                while(lastWork<maximumWork && !complete) {
                    step(); lastWork++; work++;
                }
                if(!complete) requireSource();
                return complete;
            } catch(RuntimeException failure) { cancel(); throw failure;
            } catch(Error failure) { cancel(); throw failure; }
        }
        private void step() {
            if(stage==0) {
                long before=layoutTask.copiedEntries();
                boolean done=layoutTask.advance(1); copied+=layoutTask.copiedEntries()-before;
                if(done) stage=1;
            } else if(stage==1) {
                long before=templateTask.copiedEntries();
                boolean done=templateTask.advance(1); copied+=templateTask.copiedEntries()-before;
                if(done) {stage=2; array=-1; credits=0;}
            } else if(stage==2) {
                int length=array<0?sourceGoal.length:sourceInts[array].length;
                if(credits<length) {credits++; return;}
                if(array<0) goal=new boolean[length]; else copiedInts[array]=new int[length];
                credits=0;
                if(++array==7) {stage=3; array=-1; index=0;}
            } else if(stage==3) {
                int length=array<0?sourceGoal.length:sourceInts[array].length;
                if(index<length) {
                    if(array<0) goal[index]=sourceGoal[index]; else copiedInts[array][index]=sourceInts[array][index];
                    index++; copied++; return;
                }
                index=0;
                if(++array==7) {stage=4; credits=0;}
            } else if(stage==4) {
                long units=512L+original.pads.length+original.parts.length+branch.reachedTerminals.length+
                    branch.holes.length*5L+netCount+pendingCount+reachedCount;
                if(credits<units) {credits++; return;}
                createShells(); stage=5; metaIndex=0;
            } else if(stage==5) {
                if(copyMetadata(metaIndex++)) {stage=6; face=0;}
            } else if(stage==6) {
                if(routerTask==null) routerTask=sourceFaces[face].beginChargedFixedGraphObservationCopy(copy.layout,observer);
                int before=routerTask.copiedEntries(); boolean done=routerTask.advance(1);
                copied+=routerTask.copiedEntries()-before;
                if(done) {
                    copy.faces[face]=routerTask.result(); routerTask.cancel(); routerTask=null;
                    if(++face==2) {stage=7; array=-1; index=0;}
                }
            } else if(stage==7) {
                int length=array<0?sourceGoal.length:sourceInts[array].length;
                if(index<length) {
                    if(array<0?goal[index]!=sourceGoal[index]:copiedInts[array][index]!=sourceInts[array][index])
                        throw changed();
                    index++; return;
                }
                index=0;
                if(++array==7) {stage=8; metaIndex=0;}
            } else if(stage==8) {
                if(verifyMetadata(metaIndex++)) stage=9;
            } else if(stage==9) {
                requireSource();
                if(!layoutTask.sourceUnchanged() || !templateTask.sourceUnchanged()) throw changed();
                result=new DetachedBranchReplay(owner,copy,copiedBranch);
                complete=true; stage=10; dropBorrowed();
            } else throw new IllegalStateException("Unknown detached copy phase");
        }
        private void createShells() {
            PcbBoardLayout layout=layoutTask.result();
            owner=new Session(source,observer,templateTask.result(),layout,maximumVias);
            owner.turnBoundWorkspace=new TurnBoundWorkspace(workspace,copiedInts);
            pads=new PcbPadPlacement[original.pads.length]; parts=new PcbComponentPlacement[original.parts.length];
            terminals=new PcbPadPlacement[branch.reachedTerminals.length];
            holes=new PcbBoardHole[branch.holes.length]; bounds=new Rectangle[holes.length];
            copy=new Search(original,owner,layout,pads,parts,maximumVias);
            copy.nets=new Vector<String>(netCount); copy.currentPadIds=new Vector<String>(pendingCount);
            copy.reachedPadIds=new Vector<String>(reachedCount);
            copiedBranch=copy.new BranchSearch(branch,goal,copiedInts[0],terminals,holes,bounds);
            // Nodes are immutable. Copy only their initial heap/index containers, never a found chain.
            copy.offer(copiedBranch.queue,copiedBranch.best,firstSeed);
            copy.offer(copiedBranch.queue,copiedBranch.best,secondSeed);
            copy.branch=copiedBranch; owner.search=copy;
        }
        private boolean copyMetadata(int at) {
            if(at<pads.length) {pads[at]=original.pads[at]; return false;} at-=pads.length;
            if(at<parts.length) {parts[at]=original.parts[at]; return false;} at-=parts.length;
            if(at<terminals.length) {terminals[at]=branch.reachedTerminals[at]; return false;} at-=terminals.length;
            if(at<holes.length) {
                holes[at]=branch.holes[at]; Rectangle r=branch.holeBounds[at];
                bounds[at]=new Rectangle(r.x,r.y,r.width,r.height); return false;
            } at-=holes.length;
            if(at<netCount) {copy.nets.add(nets.get(at)); return false;} at-=netCount;
            if(at<pendingCount) {copy.currentPadIds.add(pending.get(at)); return false;} at-=pendingCount;
            if(at<reachedCount) {copy.reachedPadIds.add(reached.get(at)); return false;}
            return true;
        }
        private boolean verifyMetadata(int at) {
            if(at<pads.length) {if(pads[at]!=original.pads[at] || copy.layout.getPad(pads[at].getPadId())!=pads[at]) throw changed(); return false;} at-=pads.length;
            if(at<parts.length) {if(parts[at]!=original.parts[at]) throw changed(); return false;} at-=parts.length;
            if(at<terminals.length) {if(terminals[at]!=branch.reachedTerminals[at]) throw changed(); return false;} at-=terminals.length;
            if(at<holes.length) {
                Rectangle a=bounds[at],b=branch.holeBounds[at];
                if(holes[at]!=branch.holes[at] || a.x!=b.x || a.y!=b.y || a.width!=b.width || a.height!=b.height) throw changed();
                return false;
            } at-=holes.length;
            if(at<netCount) {if(!copy.nets.get(at).equals(nets.get(at))) throw changed(); return false;} at-=netCount;
            if(at<pendingCount) {if(!copy.currentPadIds.get(at).equals(pending.get(at))) throw changed(); return false;} at-=pendingCount;
            if(at<reachedCount) {if(!copy.reachedPadIds.get(at).equals(reached.get(at))) throw changed(); return false;}
            return true;
        }
        private void requireSource() {
            if(source==null || source.complete || source.search!=original || original.branch!=branch ||
                    original.publisher!=null || source.candidate!=sourceLayout || original.layout!=sourceLayout ||
                    source.template!=sourceTemplate || source.budget!=sourceBudget || original.budget!=sourceBudget ||
                    sourceBudget.maximum!=maximum || sourceBudget.expanded!=expanded ||
                    original.serial!=serial || original.branches!=branches || original.segments!=segments ||
                    original.netIndex!=netIndex || original.pass!=pass || original.nets!=nets || nets.size()!=netCount ||
                    original.currentPadIds!=pending || pending.size()!=pendingCount ||
                    original.reachedPadIds!=reached || reached.size()!=reachedCount ||
                    branch.expanded!=0 || branch.found!=null || branch.foundTerminal!=null || branch.terminalEmitted ||
                    branch.relaxedBoundStarted || branch.queue.size()!=2 || branch.best.size()!=2 ||
                    branch.queue.values[0]!=firstSeed || branch.queue.values[1]!=secondSeed ||
                    branch.best.get(firstSeed.key)!=firstSeed || branch.best.get(secondSeed.key)!=secondSeed ||
                    original.currentRoot!=branch.root || !branch.net.equals(original.currentNet) ||
                    branch.goal!=sourceGoal || branch.distance!=sourceInts[0] || source.turnBoundWorkspace!=workspace ||
                    original.faces[0]!=sourceFaces[0] || original.faces[1]!=sourceFaces[1] ||
                    sourceLayout.observationRevision()!=layoutRevision ||
                    sourceTemplate.observationRevision()!=templateRevision ||
                    sourceFaces[0].observationRevision()!=faceRevisions[0] || sourceFaces[1].observationRevision()!=faceRevisions[1])
                throw changed();
        }
        private static IllegalStateException changed() {return new IllegalStateException("Detached branch source changed");}
        private void dropBorrowed() {
            if(layoutTask!=null) layoutTask.cancel(); if(templateTask!=null) templateTask.cancel();
            if(routerTask!=null) routerTask.cancel();
            source=null; original=null; branch=null; workspace=null; sourceBudget=null; sourceLayout=null; sourceTemplate=null;
            observer=null; layoutTask=null; templateTask=null; routerTask=null; sourceFaces=null;
            sourceGoal=null; sourceInts=null; copiedInts=null; nets=null; pending=null; reached=null;
            owner=null; copy=null; copiedBranch=null; pads=null; parts=null; terminals=null; holes=null; bounds=null; goal=null;
        }
        void cancel() {
            lastWork=0;
            if(complete) {dropBorrowed(); return;}
            cancelled=true; result=null; dropBorrowed();
        }
        int lastWork() {return lastWork;}
        long workSteps() {return work;}
        long copiedEntries() {return copied;}
        boolean isComplete() {return complete;}
        String phase() {
            if(cancelled) return "CANCELLED";
            if(complete) return "COMPLETE";
            if(stage<2) return "COPY_LAYOUT";
            if(stage<6) return "COPY_ARRAYS";
            if(stage==6) return "COPY_ROUTERS";
            return "VERIFY_SOURCE";
        }
        DetachedBranchReplay result() {
            if(!complete || result==null) throw new IllegalStateException("Detached copy is incomplete");
            return result;
        }
    }

    /** Owns exactly one copied branch and its normal Publisher, never a subsequent route. */
    static final class DetachedBranchReplay {
        private Session owner;
        private Search search;
        private Search.BranchSearch branch;
        private Search.Publisher publisher;
        private PcbBoardLayout layout;
        private int lastWork;
        private boolean complete,released;
        private DetachedBranchReplay(Session owner,Search search,Search.BranchSearch branch) {
            this.owner=owner; this.search=search; this.branch=branch; layout=search.layout;
        }
        boolean advance(int maximumWork) {
            if(maximumWork<1 || maximumWork>32768) throw new IllegalArgumentException("Detached replay quantum must be in 1..32768");
            lastWork=0; requireLive(); if(complete) return true;
            owner.observer.check(search.pass);
            while(lastWork<maximumWork && !complete) {
                lastWork++; search.advanceOne();
                if(search.publisher!=null) publisher=search.publisher;
                if(publisher!=null && search.publisher==null && search.branch==null) complete=true;
            }
            return complete;
        }
        private void requireLive() {if(released) throw new IllegalStateException("Detached replay was released");}
        int lastWork() {return lastWork;}
        boolean isComplete() {return complete;}
        PcbBoardLayout getLayout() {requireLive(); return layout;}
        Object getSearchForDiagnostics() {requireLive(); return search;}
        Object getBranchForDiagnostics() {requireLive(); return branch;}
        Object getPublisherForDiagnostics() {requireLive(); return publisher;}
        void release() {released=true; owner=null; search=null; branch=null; publisher=null; layout=null; lastWork=0;}
    }

    /** Retains one route candidate and its search state across counted generation turns. */
    static final class Session {
        private final TroubleshootBoard board;
        private final Policy policy;
        private final SeededPcbLayoutGenerator.AttemptObserver observer;
        private final PcbBoardLayout template;
        private final int links;
        private final int maximumVias;
        private final Budget budget;
        private final Diagnostics diagnostics;
        private final boolean roleWidths;
        private final boolean orderingStream;
        private final FixedPrefixCaptureContext fixedPrefixContext;
        private final FixedPrefixGraphObserver fixedPrefixGraphObserver;
        private Search.BranchSearch.FixedPrefixCaptureTask pendingFixedPrefixCapture;
        private Exhausted pendingFixedPrefixFailure;
        private boolean fixedPrefixCaptureReserved;
        private int pass,passes;
        private int completedOrderings,yieldedOrderings;
        private String reason="NO_PATH";
        private String orderingTermination;
        private String previousFailedNet;
        private PcbBoardLayout candidate;
        private Search search;
        private TurnBoundWorkspace turnBoundWorkspace;
        private Result result;
        private Result readyOrdering;
        private boolean complete;
        private boolean guidanceStarted,comparisonComplete;
        private Throwable diagnosticFailure;

        /** Detached single-branch owner; large state is supplied by the charged copy cursor. */
        private Session(Session source,SeededPcbLayoutGenerator.AttemptObserver observer,
                PcbBoardLayout template,PcbBoardLayout candidate,int maximumVias) {
            board=source.board; policy=source.policy; this.observer=observer;
            this.template=template; this.candidate=candidate; links=source.links;
            this.maximumVias=maximumVias; budget=new Budget(source.budget.maximum);
            budget.expanded=source.budget.expanded;
            diagnostics=source.diagnostics==null?null:DETACHED_DIAGNOSTICS;
            roleWidths=source.roleWidths; orderingStream=source.orderingStream;
            fixedPrefixContext=source.fixedPrefixContext;
            fixedPrefixGraphObserver=source.fixedPrefixGraphObserver==null?null:DETACHED_PREFIX_OBSERVER;
            pass=source.pass; passes=source.passes;
            completedOrderings=source.completedOrderings; yieldedOrderings=source.yieldedOrderings;
            reason=source.reason; orderingTermination=source.orderingTermination;
            previousFailedNet=source.previousFailedNet;
            fixedPrefixCaptureReserved=source.fixedPrefixCaptureReserved;
            guidanceStarted=source.guidanceStarted; comparisonComplete=source.comparisonComplete;
        }

        Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias) {
            this(board,placement,policy,observer,maximum,maximumVias,null,false,null,null);
        }
        Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
                Diagnostics diagnostics) {
            this(board,placement,policy,observer,maximum,maximumVias,diagnostics,false,null,null);
        }
        private Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
                Diagnostics diagnostics,boolean orderingStream) {
            this(board,placement,policy,observer,maximum,maximumVias,diagnostics,orderingStream,null,null);
        }
        private Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
                Diagnostics diagnostics,boolean orderingStream,FixedPrefixCaptureContext context,
                FixedPrefixGraphObserver captureObserver) {
            if(board==null || placement==null || policy==null || observer==null ||
                    maximum<1 || maximum>MAX_EXPANSIONS || maximumVias<0 || maximumVias>policy.vias)
                throw new IllegalArgumentException("Invalid P07 request");
            if(orderingStream && policy!=Policy.FULLER_TWO_LAYER)
                throw new IllegalArgumentException("P07 ordering stream requires FULLER_TWO_LAYER");
            if((context==null)!=(captureObserver==null) || context!=null && !orderingStream)
                throw new IllegalArgumentException("Invalid fixed-prefix observation session");
            this.board=board; this.policy=policy; this.observer=observer;
            this.maximumVias=maximumVias;
            this.diagnostics=diagnostics;
            this.orderingStream=orderingStream;
            this.fixedPrefixContext=context;
            this.fixedPrefixGraphObserver=captureObserver;
            roleWidths=board.getPlacementConstraints().getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS;
            observer.check(0);
            if(!placement.getHoles().isEmpty()) throw new IllegalArgumentException("P07 requires an undrilled candidate");
            placement.validateAgainst(board);
            // The matched prototype qualifies plated-through-hole packages only. A surface
            // pad must never acquire a second land merely because the search has two faces.
            int checked=0;
            for(PcbPadPlacement pad:placement.getPads()) {
                if((checked++&127)==0) observer.check(0);
                if(pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE)
                    throw new IllegalArgumentException("P07 routing prototype requires plated-through-hole pads");
            }
            template=placement.copyForRouting();
            links=PcbFactoryLinkPolicy.validateLayout(template);
            budget=new Budget(maximum);
            observer.check(0);
        }
        boolean advanceSlice() { return advanceSlice(WORK_QUANTUM); }
        /** Each queue pop or bounded route-state operation consumes one slice unit. */
        boolean advanceSlice(int maximumWork) {
            rethrowDiagnosticFailureIfAny();
            if(orderingStream)
                throw new IllegalStateException("P07 ordering session requires advanceOrderingSlice");
            if(maximumWork<1) throw new IllegalArgumentException("P07 slice must make positive progress");
            if(complete) return true;
            return advanceWork(maximumWork);
        }
        /** Shared bounded search loop; only result/terminal transitions vary by session mode. */
        private boolean advanceWork(int maximumWork) {
            if(!orderingStream && complete) return true;
            observer.check(orderingStream?orderingObserverPass():pass);
            if(orderingStream && (readyOrdering!=null || complete)) return complete;
            int remaining=maximumWork;
            while(remaining>0 && !complete && (!orderingStream || readyOrdering==null)) {
                if(pendingFixedPrefixCapture!=null) {
                    // A selected failure owns this turn. Copying and scans return
                    // to the generator after a small bounded batch, never after
                    // consuming the complete graph in one call.
                    int chunks=Math.min(4,remaining);
                    for(int chunk=0;chunk<chunks;chunk++) {
                        observer.check(pass);
                        boolean captured=pendingFixedPrefixCapture.advanceObservationUnit();
                        remaining--;
                        if(!captured) continue;
                        FixedPrefixGraphSnapshot snapshot=pendingFixedPrefixCapture.finish();
                        observer.check(pass);
                        fixedPrefixGraphObserver.onGraph(snapshot);
                        observer.check(pass);
                        Exhausted terminal=pendingFixedPrefixFailure;
                        pendingFixedPrefixCapture=null;
                        pendingFixedPrefixFailure=null;
                        if(terminal==null)
                            throw new IllegalStateException("Fixed-prefix capture lost its deferred failure");
                        if(handleExhausted(terminal)) {
                            finishOrderingTerminal("TOTAL_SEARCH_LIMIT",false);
                            observer.check(orderingObserverPass());
                            return true;
                        }
                        return false;
                    }
                    observer.check(pass);
                    return false;
                }
                if(search==null) {
                    if(pass>=ORDERINGS || budget.expanded>=budget.maximum) {
                        if(orderingStream) {
                            if(pass>=ORDERINGS)
                                finishOrderingTerminal(yieldedOrderings>0?"ORDERINGS_COMPLETE":reason,true);
                            else finishOrderingTerminal("TOTAL_SEARCH_LIMIT",false);
                        } else finishRejected();
                        break;
                    }
                    observer.check(pass);
                    passes++;
                    String promotedNet=roleWidths?previousFailedNet:null;
                    previousFailedNet=null;
                    candidate=template.copyForRouting();
                    try {
                        search=new Search(this,board,candidate,policy,observer,budget,pass,promotedNet,maximumVias);
                    } catch(Exhausted failure) {
                        reason=failure.getMessage();
                        candidate=null;
                        if(orderingStream) {
                            search=null;
                            pass++;
                            completedOrderings++;
                        } else pass++;
                    }
                    observer.check(pass<ORDERINGS?pass:ORDERINGS-1);
                    remaining--;
                    continue;
                }
                try {
                    search.advanceOne();
                    remaining--;
                    if(search.complete) {
                        observer.check(pass);
                        candidate.validateRoutingGeometry(board);
                        search.rules.validate(candidate);
                        candidate.validateRouteQuality();
                        if(candidate.getHoleCount()>maximumVias)
                            throw new IllegalStateException("P07 route exceeded its request via allowance");
                        observer.check(pass); // No partial routes or holes can escape a failed/cancelled attempt.
                        candidate.setRoutingStatistics(budget.expanded,search.segments,0);
                        if(orderingStream) {
                            readyOrdering=new Result(candidate,"SUCCESS",budget.expanded,passes,links);
                            candidate=null;
                            search=null;
                            turnBoundWorkspace=null;
                            completedOrderings++;
                            yieldedOrderings++;
                            pass++;
                            if(pass>=ORDERINGS)
                                finishOrderingTerminal("ORDERINGS_COMPLETE",true);
                            else if(!guidanceStarted)
                                finishOrderingTerminal("FIRST_SUCCESS_UNGUIDED",false);
                            else if(budget.expanded>=budget.maximum)
                                finishOrderingTerminal("TOTAL_SEARCH_LIMIT",false);
                        } else {
                            result=new Result(candidate,"SUCCESS",budget.expanded,passes,links);
                            turnBoundWorkspace=null;
                            complete=true;
                        }
                    }
                } catch(Exhausted failure) {
                    boolean totalSearchLimit=handleExhausted(failure);
                    remaining--;
                    if(totalSearchLimit) finishOrderingTerminal("TOTAL_SEARCH_LIMIT",false);
                } catch(PcbBoardLayout.RouteQualityRejectedException quality) {
                    reason="QUALITY_LIMIT";
                    previousFailedNet=null;
                    candidate=null; search=null; pass++;
                    if(orderingStream) {
                        turnBoundWorkspace=null;
                        completedOrderings++;
                    }
                    remaining--;
                }
            }
            if(orderingStream && !complete && readyOrdering==null && search==null) {
                if(pass>=ORDERINGS)
                    finishOrderingTerminal(yieldedOrderings>0?"ORDERINGS_COMPLETE":reason,true);
                else if(budget.expanded>=budget.maximum)
                    finishOrderingTerminal("TOTAL_SEARCH_LIMIT",false);
            }
            if(orderingStream) {
                if(!complete && readyOrdering==null) observer.check(orderingObserverPass());
            } else if(!complete) observer.check(pass<ORDERINGS?pass:ORDERINGS-1);
            return complete;
        }
        private boolean handleExhausted(Exhausted failure) {
            reason=failure.getMessage();
            boolean totalSearchLimit=orderingStream && reason!=null &&
                reason.endsWith(":TOTAL_SEARCH_LIMIT");
            previousFailedNet=roleWidths && search!=null?search.currentNet:null;
            candidate=null; search=null; pass++;
            if(orderingStream) {
                turnBoundWorkspace=null;
                completedOrderings++;
            }
            return totalSearchLimit;
        }
        private boolean deferFixedPrefixCapture(Search.BranchSearch branch,
                FixedPrefixBranchLineage lineage) {
            if(fixedPrefixGraphObserver==null || fixedPrefixCaptureReserved) return false;
            if(pendingFixedPrefixCapture!=null || pendingFixedPrefixFailure!=null)
                throw new IllegalStateException("A fixed-prefix failure is already suspended");
            if(!fixedPrefixGraphObserver.select(lineage)) return false;
            fixedPrefixCaptureReserved=true;
            pendingFixedPrefixCapture=branch.new FixedPrefixCaptureTask(lineage);
            pendingFixedPrefixFailure=new Exhausted(lineage.netId()+":"+lineage.terminalReason());
            return true;
        }
        OrderingProgress advanceOrderingSlice() { return advanceOrderingSlice(WORK_QUANTUM); }
        /** Advances only the current pass; a ready route must be taken before successor work. */
        OrderingProgress advanceOrderingSlice(int maximumWork) {
            if(!orderingStream)
                throw new IllegalStateException("P07 legacy session does not expose ordering progress");
            rethrowDiagnosticFailureIfAny();
            if(maximumWork<1) throw new IllegalArgumentException("P07 slice must make positive progress");
            try {
                boolean terminal=advanceWork(maximumWork);
                OrderingProgress progress=readyOrdering!=null?OrderingProgress.ROUTE_READY:
                    terminal?OrderingProgress.COMPLETE:OrderingProgress.WORKING;
                if(progress!=OrderingProgress.WORKING) observer.check(orderingObserverPass());
                return progress;
            } catch(RuntimeException failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            } catch(Error failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            }
        }
        private int orderingObserverPass() {
            return pass<0?0:pass>=ORDERINGS?ORDERINGS-1:pass;
        }
        /** Consumes exactly one yielded route; no search work is performed here. */
        Result takeReadyOrdering() {
            if(!orderingStream)
                throw new IllegalStateException("P07 legacy session does not expose ordering candidates");
            rethrowDiagnosticFailureIfAny();
            if(readyOrdering==null)
                throw new IllegalStateException("P07 ordering route is not ready");
            try {
                observer.check(orderingObserverPass());
                Result ready=readyOrdering;
                readyOrdering=null;
                return ready;
            } catch(RuntimeException failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            } catch(Error failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            }
        }
        /** Available only after terminal search and after the caller takes any final yield. */
        OrderingSummary orderingSummary() {
            if(!orderingStream)
                throw new IllegalStateException("P07 legacy session does not expose ordering summary");
            rethrowDiagnosticFailureIfAny();
            if(!complete || readyOrdering!=null || orderingTermination==null)
                throw new IllegalStateException("P07 ordering summary is not terminal");
            try {
                observer.check(orderingObserverPass());
                return new OrderingSummary(budget.expanded,passes,completedOrderings,yieldedOrderings,
                    guidanceStarted,comparisonComplete,orderingTermination);
            } catch(RuntimeException failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            } catch(Error failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            }
        }
        Result result() {
            rethrowDiagnosticFailureIfAny();
            if(orderingStream)
                throw new IllegalStateException("P07 ordering session does not expose legacy result");
            if(!complete) throw new IllegalStateException("P07 route has not completed");
            return result;
        }
        int expansions() { return budget.expanded; }
        private void rethrowDiagnosticFailureIfAny() {
            if(diagnosticFailure instanceof RuntimeException)
                throw (RuntimeException)diagnosticFailure;
            if(diagnosticFailure instanceof Error) throw (Error)diagnosticFailure;
        }
        private void emitBranchSnapshot(BranchSnapshot snapshot) {
            if(diagnostics==null) return;
            try {
                diagnostics.onTerminalBranch(snapshot);
            } catch(RuntimeException failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            } catch(Error failure) {
                abortAfterDiagnosticFailure(failure);
                throw failure;
            }
        }
        private void abortAfterDiagnosticFailure(Throwable failure) {
            if(diagnosticFailure==null) diagnosticFailure=failure;
            candidate=null;
            search=null;
            turnBoundWorkspace=null;
            previousFailedNet=null;
            result=null;
            readyOrdering=null;
            orderingTermination=null;
            pendingFixedPrefixCapture=null;
            pendingFixedPrefixFailure=null;
            comparisonComplete=false;
            if(orderingStream) {
                complete=false;
                guidanceStarted=false;
                passes=0;
                completedOrderings=0;
                yieldedOrderings=0;
                reason="NO_PATH";
            }
        }
        private void finishOrderingTerminal(String termination,boolean comparisonComplete) {
            if(!orderingStream || termination==null)
                throw new IllegalStateException("Invalid P07 ordering terminal state");
            orderingTermination=termination;
            this.comparisonComplete=comparisonComplete;
            candidate=null;
            search=null;
            turnBoundWorkspace=null;
            previousFailedNet=null;
            result=null;
            complete=true;
        }
        private TurnBoundWorkspace turnBoundWorkspace(int width,int height,int cells) {
            if(turnBoundWorkspace==null) turnBoundWorkspace=new TurnBoundWorkspace(width,height);
            else if(turnBoundWorkspace.width!=width || turnBoundWorkspace.height!=height ||
                    turnBoundWorkspace.cells!=cells)
                throw new IllegalStateException("Routing session changed turn-bound grid dimensions");
            return turnBoundWorkspace;
        }
        private void finishRejected() {
            result=new Result(null,reason,budget.expanded,passes,links);
            turnBoundWorkspace=null;
            complete=true;
        }
    }
    private static final class Node implements Comparable<Node> {
        final int cell,layer,direction,transitions,cost,heuristic,serial,key;
        final Node previous;
        Node(int cell,int layer,int direction,int transitions,int cost,int heuristic,
                int serial,int cells,Node previous) {
            this.cell=cell; this.layer=layer; this.direction=direction; this.transitions=transitions;
            this.cost=cost; this.heuristic=heuristic; this.serial=serial; this.previous=previous;
            key=(((transitions*2+layer)*5+direction)*cells)+cell;
        }
        public int compareTo(Node other) {
            int total=cost+heuristic, theirs=other.cost+other.heuristic;
            if(total!=theirs) return total<theirs?-1:1;
            if(heuristic!=other.heuristic) return heuristic<other.heuristic?-1:1;
            if(key!=other.key) return key<other.key?-1:1;
            return serial<other.serial?-1:serial==other.serial?0:1;
        }
    }
    /** Typed heap uses Node.compareTo directly; each route Node serial breaks remaining ties. */
    private static final class NodeQueue {
        private Node[] values=new Node[128];
        private int size;
        int size() { return size; }
        void add(Node value) {
            if(value==null) throw new IllegalArgumentException("Missing search node");
            if(size==values.length) {
                Node[] larger=new Node[values.length*2];
                System.arraycopy(values,0,larger,0,size);
                values=larger;
            }
            int at=size++;
            while(at>0) {
                int parent=(at-1)>>>1;
                Node above=values[parent];
                if(value.compareTo(above)>=0) break;
                values[at]=above; at=parent;
            }
            values[at]=value;
        }
        Node poll() {
            if(size==0) return null;
            Node result=values[0],value=values[--size];
            values[size]=null;
            if(size>0) {
                int at=0,half=size>>>1;
                while(at<half) {
                    int child=(at<<1)+1,right=child+1;
                    if(right<size && values[right].compareTo(values[child])<0) child=right;
                    if(value.compareTo(values[child])<=0) break;
                    values[at]=values[child]; at=child;
                }
                values[at]=value;
            }
            return result;
        }
    }
    /** Branch-local primitive index; null values mark empty slots and entries never delete. */
    private static final class NodeIndex {
        private static final int INITIAL_CAPACITY=128;
        private int[] keys=new int[INITIAL_CAPACITY];
        private Node[] values=new Node[INITIAL_CAPACITY];
        private int size;
        int size() { return size; }
        Node get(int key) {
            int slot=findSlot(key);
            return values[slot];
        }
        void put(int key,Node value) {
            if(value==null) throw new IllegalArgumentException("Missing route state node");
            int slot=findSlot(key);
            if(values[slot]!=null) { values[slot]=value; return; }
            if(size>=values.length/2) {
                grow();
                slot=findSlot(key);
            }
            keys[slot]=key;
            values[slot]=value;
            size++;
        }
        private int findSlot(int key) {
            int mixed=key;
            mixed^=mixed>>>16;
            mixed^=mixed>>>8;
            mixed^=mixed>>>4;
            int mask=values.length-1,slot=mixed&mask;
            while(values[slot]!=null && keys[slot]!=key) slot=(slot+1)&mask;
            return slot;
        }
        private void grow() {
            int[] previousKeys=keys;
            Node[] previousValues=values;
            keys=new int[previousKeys.length*2];
            values=new Node[previousValues.length*2];
            for(int index=0;index<previousValues.length;index++) if(previousValues[index]!=null) {
                int slot=findSlot(previousKeys[index]);
                keys[slot]=previousKeys[index];
                values[slot]=previousValues[index];
            }
        }
    }
    private static boolean refreshNodeForBound(Node current,int stronger,NodeIndex best,
            NodeQueue queue,int cells) {
        if(current==null || best==null || queue==null || cells<1)
            throw new IllegalArgumentException("Invalid P07 relaxed-node refresh");
        if(best.get(current.key)!=current || stronger<=current.heuristic) return false;
        Node refreshed=new Node(current.cell,current.layer,current.direction,current.transitions,
            current.cost,stronger,current.serial,cells,current.previous);
        if(refreshed.key!=current.key)
            throw new IllegalStateException("P07 relaxed refresh changed the route-state key");
        best.put(current.key,refreshed);
        queue.add(refreshed);
        return true;
    }
    /** Scratch rays reused only between sequential branches in one routing session. */
    static final class TurnBoundWorkspace {
        final int width,height,cells;
        private final int[] north,east,south,west,axisMin,rowMin;
        TurnBoundWorkspace(int width,int height) {
            long cellCount=(long)width*height;
            if(width<1 || height<1 || cellCount>MAX_CELLS)
                throw new IllegalArgumentException("Invalid turn lower-bound workspace grid");
            this.width=width; this.height=height; cells=(int)cellCount;
            north=new int[cells]; east=new int[cells]; south=new int[cells];
            west=new int[cells]; axisMin=new int[cells]; rowMin=new int[cells];
        }
        private TurnBoundWorkspace(TurnBoundWorkspace source,int[][] copied) {
            width=source.width; height=source.height; cells=source.cells;
            north=copied[1]; east=copied[2]; south=copied[3]; west=copied[4];
            axisMin=copied[5]; rowMin=copied[6];
        }
        private void requireGrid(int requestedWidth,int requestedHeight,int requestedCells) {
            if(width!=requestedWidth || height!=requestedHeight || cells!=requestedCells)
                throw new IllegalArgumentException("Mismatched turn lower-bound workspace grid");
        }
    }
    /**
     * Turn-aware relaxed-plane lower bound for role-width routes and FULLER requests.
     * It depends on the grid, goals and movement/turn costs, not copper width.
     */
    static final class TurnLowerBound {
        private static final int INFINITY=Integer.MAX_VALUE/4;
        private final int cells;
        private final int[] manhattan,north,east,south,west,axisMin;
        private TurnLowerBound(TurnLowerBound source,int[] distance,TurnBoundWorkspace workspace) {
            cells=source.cells; manhattan=distance;
            north=workspace.north; east=workspace.east; south=workspace.south;
            west=workspace.west; axisMin=workspace.axisMin;
        }
        TurnLowerBound(int width,int height,boolean[] goals,int[] distance,
                SeededPcbLayoutGenerator.AttemptObserver observer,int pass) {
            this(width,height,goals,distance,observer,pass,null);
        }
        TurnLowerBound(int width,int height,boolean[] goals,int[] distance,
                SeededPcbLayoutGenerator.AttemptObserver observer,int pass,
                TurnBoundWorkspace workspace) {
            long cellCount=(long)width*height;
            if(width<1 || height<1 || cellCount>MAX_CELLS || goals==null ||
                    goals.length!=cellCount*2L || distance==null ||
                    distance.length!=cellCount || observer==null)
                throw new IllegalArgumentException("Invalid turn lower-bound grid");
            cells=(int)cellCount;
            if(workspace==null) workspace=new TurnBoundWorkspace(width,height);
            else workspace.requireGrid(width,height,cells);
            manhattan=distance;
            north=workspace.north; east=workspace.east;
            south=workspace.south; west=workspace.west;
            axisMin=workspace.axisMin;
            int scanned=0;
            for(int x=0;x<width;x++) {
                int nearest=-1;
                for(int y=0;y<height;y++) {
                    int cell=y*width+x;
                    if(goals[cell] || goals[cell+cells]) nearest=y;
                    north[cell]=nearest>=0?(y-nearest)*GRID:INFINITY;
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            for(int y=0;y<height;y++) {
                int nearest=-1;
                for(int x=width-1;x>=0;x--) {
                    int cell=y*width+x;
                    if(goals[cell] || goals[cell+cells]) nearest=x;
                    east[cell]=nearest>=0?(nearest-x)*GRID:INFINITY;
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            for(int x=0;x<width;x++) {
                int nearest=-1;
                for(int y=height-1;y>=0;y--) {
                    int cell=y*width+x;
                    if(goals[cell] || goals[cell+cells]) nearest=y;
                    south[cell]=nearest>=0?(nearest-y)*GRID:INFINITY;
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            int[] rowMin=workspace.rowMin;
            for(int y=0;y<height;y++) {
                int nearest=-1;
                for(int x=0;x<width;x++) {
                    int cell=y*width+x;
                    if(goals[cell] || goals[cell+cells]) nearest=x;
                    west[cell]=nearest>=0?(x-nearest)*GRID:INFINITY;
                    rowMin[cell]=Math.min(east[cell],west[cell]);
                    axisMin[cell]=Math.min(Math.min(north[cell],south[cell]),rowMin[cell]);
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }

            // East/west forward half-planes use old vertical rays at each
            // strict next column; preserve each current ray before overwriting it.
            for(int y=0;y<height;y++) {
                int forward=INFINITY;
                for(int x=width-1;x>=0;x--) {
                    int cell=y*width+x;
                    int dForward=add(forward,GRID);
                    int vertical=Math.min(north[cell],south[cell]);
                    int oldRay=east[cell];
                    east[cell]=cardinalValue(cell,dForward,oldRay);
                    forward=Math.min(vertical,dForward);
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            for(int y=0;y<height;y++) {
                int forward=INFINITY;
                for(int x=0;x<width;x++) {
                    int cell=y*width+x;
                    int dForward=add(forward,GRID);
                    int vertical=Math.min(north[cell],south[cell]);
                    int oldRay=west[cell];
                    west[cell]=cardinalValue(cell,dForward,oldRay);
                    forward=Math.min(vertical,dForward);
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }

            // North/south forward half-planes use rowMin from the old E/W rays.
            for(int x=0;x<width;x++) {
                int forward=INFINITY;
                for(int y=0;y<height;y++) {
                    int cell=y*width+x;
                    int dForward=add(forward,GRID);
                    int oldRay=north[cell];
                    north[cell]=cardinalValue(cell,dForward,oldRay);
                    forward=Math.min(rowMin[cell],dForward);
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            for(int x=0;x<width;x++) {
                int forward=INFINITY;
                for(int y=height-1;y>=0;y--) {
                    int cell=y*width+x;
                    int dForward=add(forward,GRID);
                    int oldRay=south[cell];
                    south[cell]=cardinalValue(cell,dForward,oldRay);
                    forward=Math.min(rowMin[cell],dForward);
                    if((scanned++&4095)==0) observer.check(pass);
                }
            }
            observer.check(pass);
        }
        int value(int cell,int direction) {
            if(cell<0 || cell>=cells || direction<0 || direction>4)
                throw new IllegalArgumentException("Invalid turn lower-bound state");
            int manhattanDistance=manhattan[cell];
            if(manhattanDistance<=0) return 0;
            if(direction==4) return Math.min(add(manhattanDistance,TURN_COST),axisMin[cell]);
            return direction==0?north[cell]:direction==1?east[cell]:
                direction==2?south[cell]:west[cell];
        }
        private int cardinalValue(int cell,int forward,int currentRay) {
            int distance=manhattan[cell];
            if(distance<=0) return 0;
            return Math.min(Math.min(add(distance,2*TURN_COST),add(forward,TURN_COST)),
                Math.min(add(axisMin[cell],TURN_COST),currentRay));
        }
        private static int add(int value,int amount) {
            return value<0 || value>=INFINITY-amount?INFINITY:value+amount;
        }
    }

    interface FixedGraphEdges {
        boolean permitsStep(int layer,int from,int to);
        boolean permitsVia(int cell);
    }
    /** Incremental Dijkstra field on a directed, fixed-prefix two-face relaxation. */
    static final class RelaxedDistanceBound {
        private static final int INFINITY=Integer.MAX_VALUE/4;
        private final int width,height,cells,stateCount,primary;
        private final Policy policy;
        private final boolean[] goals;
        private FixedGraphEdges edges;
        private final int[] distance,heapPosition;
        private int[] heap;
        private int initIndex,heapSize,workUnits,ordinaryProbes,viaProbes;
        private boolean complete;
        private Throwable failure;

        RelaxedDistanceBound(int width,int height,boolean[] goals,int primary,
                Policy policy,FixedGraphEdges edges) {
            long count=(long)width*height;
            if(width<1 || height<1 || count>MAX_CELLS || goals==null ||
                    goals.length!=count*2L || primary<0 || primary>1 ||
                    policy!=Policy.FULLER_TWO_LAYER || edges==null)
                throw new IllegalArgumentException("Invalid P07 relaxed-distance bound");
            this.width=width; this.height=height; cells=(int)count; stateCount=cells*2;
            this.goals=goals; this.primary=primary; this.policy=policy; this.edges=edges;
            distance=new int[stateCount]; heap=new int[stateCount];
            heapPosition=new int[stateCount];
        }
        /** Processes one bounded goal-initialization scan or Dijkstra pop. */
        boolean advanceOne() {
            rethrowFailure();
            if(complete) return true;
            workUnits++;
            try { return advanceOneCore(); }
            catch(RuntimeException problem) { failure=problem; throw problem; }
            catch(Error problem) { failure=problem; throw problem; }
        }
        private boolean advanceOneCore() {
            if(initIndex<stateCount) {
                int state=initIndex++;
                if(goals[state]) {
                    distance[state]=0;
                    heap[heapSize]=state;
                    heapPosition[state]=++heapSize;
                }
                if(initIndex==stateCount && heapSize==0) finish();
                return complete;
            }
            if(heapSize==0) { finish(); return true; }
            int current=popMinimum(),layer=current/cells,cell=current%cells;
            int x=cell%width,y=cell/width,baseDistance=distance[current];
            if(x>0) relaxPredecessor(layer,cell-1,cell,baseDistance);
            if(x+1<width) relaxPredecessor(layer,cell+1,cell,baseDistance);
            if(y>0) relaxPredecessor(layer,cell-width,cell,baseDistance);
            if(y+1<height) relaxPredecessor(layer,cell+width,cell,baseDistance);
            viaProbes++;
            if(edges.permitsVia(cell))
                relax((1-layer)*cells+cell,baseDistance,policy.viaCost);
            if(heapSize==0) finish();
            return complete;
        }
        boolean isComplete() { return complete; }
        int value(int cell,int layer) {
            rethrowFailure();
            if(!complete) throw new IllegalStateException("P07 relaxed-distance field is incomplete");
            if(cell<0 || cell>=cells || layer<0 || layer>1)
                throw new IllegalArgumentException("Invalid P07 relaxed-distance state");
            int state=layer*cells+cell;
            return heapPosition[state]==0?-1:distance[state];
        }
        private void rethrowFailure() {
            if(failure instanceof RuntimeException) throw (RuntimeException)failure;
            if(failure instanceof Error) throw (Error)failure;
            if(failure!=null) throw new IllegalStateException("P07 relaxed bound failed",failure);
        }
        int getWorkUnits() { return workUnits; }
        int getOrdinaryProbes() { return ordinaryProbes; }
        int getViaProbes() { return viaProbes; }
        private void relaxPredecessor(int layer,int from,int to,int baseDistance) {
            ordinaryProbes++;
            if(edges.permitsStep(layer,from,to))
                relax(layer*cells+from,baseDistance,GRID+
                    (layer==primary?0:policy.secondaryCost));
        }
        private void relax(int state,int baseDistance,int edgeCost) {
            if(baseDistance<0 || edgeCost<0 || edgeCost>=INFINITY ||
                    baseDistance>=INFINITY-edgeCost) return;
            int candidate=baseDistance+edgeCost,slot=heapPosition[state];
            if(slot<0) {
                if(candidate<distance[state])
                    throw new IllegalStateException("P07 relaxed Dijkstra improved a settled state");
                return;
            }
            if(slot==0 || candidate<distance[state]) {
                distance[state]=candidate;
                if(slot==0) {
                    heap[heapSize]=state;
                    heapPosition[state]=++heapSize;
                    siftUp(heapSize-1);
                } else siftUp(slot-1);
            }
        }
        private int popMinimum() {
            int result=heap[0],last=heap[--heapSize];
            heapPosition[result]=-1;
            if(heapSize>0) {
                heap[0]=last; heapPosition[last]=1; siftDown(0);
            }
            return result;
        }
        private void siftUp(int index) {
            int state=heap[index];
            while(index>0) {
                int parent=(index-1)>>>1,above=heap[parent];
                if(!less(state,above)) break;
                heap[index]=above; heapPosition[above]=index+1; index=parent;
            }
            heap[index]=state; heapPosition[state]=index+1;
        }
        private void siftDown(int index) {
            int state=heap[index],half=heapSize>>>1;
            while(index<half) {
                int child=(index<<1)+1,right=child+1;
                if(right<heapSize && less(heap[right],heap[child])) child=right;
                if(!less(heap[child],state)) break;
                int below=heap[child]; heap[index]=below; heapPosition[below]=index+1;
                index=child;
            }
            heap[index]=state; heapPosition[state]=index+1;
        }
        private boolean less(int first,int second) {
            return distance[first]<distance[second] ||
                distance[first]==distance[second] && first<second;
        }
        private void finish() { complete=true; edges=null; heap=null; }
    }
    private static final class Search {
        final Session owner;
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final Policy policy;
        final SeededPcbLayoutGenerator.AttemptObserver observer;
        final Budget budget;
        final int pass,minX,minY,width,height,cells,primary,maximumVias;
        final PcbTwoLayerRules rules;
        final PcbNetRouter.Router[] faces=new PcbNetRouter.Router[2];
        final PcbPadPlacement[] pads;
        final PcbComponentPlacement[] parts;
        int serial,branches,segments;
        private Vector<String> nets;
        private int netIndex;
        private String currentNet;
        private final String promotedNet;
        private Vector<String> currentPadIds;
        private Vector<String> reachedPadIds;
        private PcbPadPlacement currentRoot;
        private BranchSearch branch;
        private Publisher publisher;
        private boolean complete;
        private Search(Search source,Session owner,PcbBoardLayout layout,
                PcbPadPlacement[] pads,PcbComponentPlacement[] parts,int maximumVias) {
            this.owner=owner; board=source.board; this.layout=layout; policy=source.policy;
            observer=owner.observer; budget=owner.budget; pass=source.pass;
            minX=source.minX; minY=source.minY; width=source.width; height=source.height;
            cells=source.cells; primary=source.primary; this.maximumVias=maximumVias;
            // Rules retain only immutable board/domain constraints, never a mutable layout.
            rules=source.rules; this.pads=pads; this.parts=parts;
            serial=source.serial; branches=source.branches; segments=source.segments;
            promotedNet=source.promotedNet; netIndex=source.netIndex; currentNet=source.currentNet;
            currentRoot=layout.getPad(source.currentRoot.getPadId());
        }
        Search(Session owner,TroubleshootBoard board,PcbBoardLayout layout,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,Budget budget,int pass,String promotedNet,
                int maximumVias) {
            this.owner=owner;
            this.board=board; this.layout=layout; this.policy=policy;
            this.observer=observer; this.budget=budget; this.pass=pass;
            this.promotedNet=promotedNet;
            this.maximumVias=maximumVias;
            Rectangle r=layout.getBoardOutline(); minX=r.x+GRID; minY=r.y+GRID;
            GridAdmission grid=gridAdmission(r);
            width=grid.width; height=grid.height;
            if(!grid.eligible) throw new Exhausted(grid.reason);
            cells=(int)grid.cells; primary=board.getPlacementConstraints().routingLayer.ordinal();
            rules=new PcbTwoLayerRules(board,layout);
            pads=layout.getPads().toArray(new PcbPadPlacement[0]);
            parts=layout.getComponents().toArray(new PcbComponentPlacement[0]);
            for(int layer=0;layer<2;layer++) {
                faces[layer]=new PcbNetRouter.Router(layout,board,r,pass,observer,PcbCopperLayer.values()[layer],
                    policy.transitions>0?PcbTwoLayerRules.VIA_LAND:0);
                faces[layer].factoryUnderpasses=policy.underpasses;
            }
        }
        int x(int cell) { return minX+(cell%width)*GRID; }
        int y(int cell) { return minY+(cell/width)*GRID; }
        int cell(PcbPadPlacement pad) {
            int x=pad.getX()-minX,y=pad.getY()-minY;
            if(x<0 || y<0 || x%GRID!=0 || y%GRID!=0 || x/GRID>=width || y/GRID>=height)
                throw new IllegalArgumentException("P07 pad is outside its routing grid");
            return (y/GRID)*width+x/GRID;
        }
        void advanceOne() {
            if(complete) return;
            if(publisher!=null) {
                if(publisher.advanceOne()) {
                    segments+=publisher.segments;
                    reachedPadIds.add(publisher.start.getPadId());
                    publisher=null;
                }
                return;
            }
            if(branch!=null) {
                branch.advanceOne();
                if(branch.found!=null) {
                    publisher=new Publisher(branch.net,branch.start,branch.root,branch.found,
                        branch.foundTerminal);
                    branch=null;
                }
                return;
            }
            if(nets==null) {
                nets=board.getNetIds();
                Collections.sort(nets,new Comparator<String>() { public int compare(String a,String b) {
                    int ap=pass==1?-board.getNet(a).getPadIds().size():PcbNetRouter.priority(board,a);
                    int bp=pass==1?-board.getNet(b).getPadIds().size():PcbNetRouter.priority(board,b);
                    return ap==bp?a.compareTo(b):ap<bp?-1:1;
                }});
                if(pass==2) Collections.reverse(nets);
                if(promotedNet!=null) {
                    int failedIndex=nets.indexOf(promotedNet);
                    if(failedIndex<0)
                        throw new IllegalStateException("P07 failed-net promotion owner is not in canonical routing order");
                    if(failedIndex>0) nets.insertElementAt(nets.remove(failedIndex),0);
                }
                return;
            }
            if(currentPadIds!=null && !currentPadIds.isEmpty()) {
                // Preserve the legacy reverse-pass tree. Role-width nets keep
                // joining the nearest reached terminal so a distant lexical
                // endpoint does not consume the branch budget before a trunk exists.
                boolean lexical=pass==2 &&
                    board.getPlacementConstraints().getTraceWidthProfile().isLegacyUniform() &&
                    board.getNet(currentNet).getRoutingRole()!=BoardNet.RoutingRole.RETURN;
                String next=lexical?currentPadIds.firstElement():
                    PcbNetRouter.chooseNext(layout,currentPadIds,reachedPadIds);
                currentPadIds.remove(next);
                PcbPadPlacement start=layout.getPad(next);
                branches++;
                branch=new BranchSearch(currentNet,start,currentRoot);
                return;
            }
            if(netIndex<nets.size()) {
                currentNet=nets.get(netIndex++);
                currentPadIds=board.getNet(currentNet).getPadIds();
                Collections.sort(currentPadIds);
                if(currentPadIds.size()<2) {
                    currentPadIds=null; currentRoot=null; reachedPadIds=null;
                } else {
                    currentRoot=layout.getPad(currentPadIds.remove(0));
                    reachedPadIds=new Vector<String>();
                    reachedPadIds.add(currentRoot.getPadId());
                }
                return;
            }
            complete=true;
        }
        private PcbPadPlacement[] reachedPthTerminals(String net,PcbPadPlacement root) {
            Vector<PcbPadPlacement> values=new Vector<PcbPadPlacement>();
            for(String padId:reachedPadIds) {
                if(padId.equals(root.getPadId())) continue;
                BoardPad logical=board.getPad(padId);
                PcbPadPlacement placement=layout.getPad(padId);
                if(logical==null || placement==null || !net.equals(logical.getNetId()))
                    throw new IllegalStateException("P07 reached tree has an invalid plated terminal: "+padId);
                // A valid reached surface pad remains part of the published prefix, but it
                // cannot provide the opposite-face endpoint this PTH-only view represents.
                if(placement.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE) continue;
                values.add(placement);
            }
            Collections.sort(values,new Comparator<PcbPadPlacement>() {
                public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                    return a.getPadId().compareTo(b.getPadId());
                }
            });
            return values.toArray(new PcbPadPlacement[0]);
        }
        boolean[] goals(String net,PcbPadPlacement root,PcbPadPlacement[] reachedTerminals) {
            boolean[] result=new boolean[cells*2];
            for(int layer=0;layer<2;layer++) if(layer==primary || policy.transitions>0) {
                result[layer*cells+cell(root)]=true;
                for(int c=0;c<cells;c++) {
                    if((c&4095)==0) observer.check(pass);
                    if(faces[layer].ownsLayerGridCell(c%width,c/width,net)) result[layer*cells+c]=true;
                }
            }
            for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)
                for(PcbPadPlacement reached:reachedTerminals)
                    if(PcbCopperAccess.hasCopper(reached,PcbCopperLayer.values()[layer]))
                        result[layer*cells+cell(reached)]=true;
            if(policy==Policy.RESTRICTED_TWO_LAYER)
                result[(1-primary)*cells+cell(root)]=false;
            return result;
        }
        int[] lowerBound(boolean[] goals) {
            int[] distance=new int[cells],queue=new int[cells]; int head=0,tail=0;
            java.util.Arrays.fill(distance,-1);
            for(int c=0;c<cells;c++) {
                if((c&4095)==0) observer.check(pass);
                if(goals[c] || goals[c+cells]) { distance[c]=0; queue[tail++]=c; }
            }
            while(head<tail) {
                if((head&4095)==0) observer.check(pass);
                int c=queue[head++],cx=c%width,cy=c/width;
                if(cx>0) { int n=c-1; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cx+1<width) { int n=c+1; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cy>0) { int n=c-width; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
                if(cy+1<height) { int n=c+width; if(distance[n]<0) { distance[n]=distance[c]+GRID; queue[tail++]=n; } }
            }
            return distance;
        }
        private final class BranchSearch {
            final String net;
            final PcbPadPlacement start,root;
            final int branchOrdinal;
            final PcbPadPlacement[] reachedTerminals;
            final boolean[] goal;
            final int[] distance;
            final TurnLowerBound turnLowerBound;
            final PcbBoardHole[] holes;
            final Rectangle[] holeBounds;
            final NodeIndex best=new NodeIndex();
            final NodeQueue queue=new NodeQueue();
            int expanded;
            int expandedTop,expandedBottom;
            int minimumExploredManhattanDistance=Integer.MAX_VALUE;
            int goalsTop,goalsBottom;
            boolean terminalEmitted;
            boolean relaxedBoundStarted,relaxedBoundComplete;
            boolean relaxedBoundObservationChecked,relaxedBoundLiveLayoutStable,
                relaxedBoundRouterStateStable,relaxedBoundRouterEffectsStable;
            int relaxedBoundWorkUnits,relaxedBoundRefreshes,relaxedCloneFace,relaxedPostVerifyFace;
            PcbNetRouter.Router[] relaxedQueryFaces;
            PcbNetRouter.Router[] relaxedPristineFaces;
            String[] relaxedLiveEffectsBefore;
            String relaxedLayoutGeometryBefore;
            RelaxedDistanceBound relaxedDistanceBound;
            Node found;
            PcbPadPlacement foundTerminal;
            private BranchSearch(BranchSearch source,boolean[] goal,int[] distance,
                    PcbPadPlacement[] reached,PcbBoardHole[] holes,Rectangle[] bounds) {
                net=source.net; start=layout.getPad(source.start.getPadId());
                root=layout.getPad(source.root.getPadId()); branchOrdinal=source.branchOrdinal;
                reachedTerminals=reached; this.goal=goal; this.distance=distance;
                this.holes=holes; holeBounds=bounds;
                turnLowerBound=new TurnLowerBound(source.turnLowerBound,distance,owner.turnBoundWorkspace);
                goalsTop=source.goalsTop; goalsBottom=source.goalsBottom;
            }
            BranchSearch(String net,PcbPadPlacement start,PcbPadPlacement root) {
                this.net=net; this.start=start; this.root=root;
                branchOrdinal=branches;
                observer.check(pass);
                // Publisher finishes before the next branch is constructed.
                // Its holes are immutable throughout this branch, including
                // yields. Refresh from the owner for every new branch.
                holes=layout.getHoles().toArray(new PcbBoardHole[0]);
                holeBounds=new Rectangle[holes.length];
                for(int i=0;i<holes.length;i++) holeBounds[i]=holes[i].getBounds();
                reachedTerminals=reachedPthTerminals(net,root);
                goal=goals(net,root,reachedTerminals); distance=lowerBound(goal);
                if(owner.diagnostics!=null) for(int c=0;c<cells;c++) {
                    if(goal[c]) goalsTop++;
                    if(goal[c+cells]) goalsBottom++;
                }
                if(owner.roleWidths || policy==Policy.FULLER_TWO_LAYER) {
                    TurnBoundWorkspace workspace=owner.turnBoundWorkspace(width,height,cells);
                    turnLowerBound=new TurnLowerBound(width,height,goal,distance,observer,pass,workspace);
                } else turnLowerBound=null;
                for(int layer=0;layer<2;layer++) if(layer==primary || policy==Policy.FULLER_TWO_LAYER)
                    offer(queue,best,new Node(cell(start),layer,4,0,layer==primary?0:policy.secondaryCost,
                        heuristic(cell(start),4,layer),serial++,cells,null));
                observer.check(pass);
            }
            private boolean emitTerminal(String terminalReason) {
                if(terminalEmitted)
                    throw new IllegalStateException("P07 branch terminal was already observed");
                terminalEmitted=true;
                if(owner.diagnostics!=null)
                    owner.emitBranchSnapshot(new BranchSnapshot(pass,net,
                        start.getPadId(),root.getPadId(),start.getX(),start.getY(),
                        root.getX(),root.getY(),width,height,goalsTop,goalsBottom,
                        holes.length,reachedPadIds.size(),
                        distance[cell(start)],minimumExploredManhattanDistance==Integer.MAX_VALUE?
                            -1:minimumExploredManhattanDistance,expanded,budget.expanded,
                        expandedTop,expandedBottom,queue.size(),best.size(),terminalReason,
                        relaxedBoundStarted,relaxedBoundComplete,relaxedBoundObservationChecked,
                        relaxedBoundLiveLayoutStable,relaxedBoundRouterStateStable,
                        relaxedBoundRouterEffectsStable,relaxedBoundWorkUnits,
                        relaxedDistanceBound==null?0:relaxedDistanceBound.getOrdinaryProbes(),
                        relaxedDistanceBound==null?0:relaxedDistanceBound.getViaProbes(),
                        relaxedBoundRefreshes));
                if(owner.fixedPrefixGraphObserver==null || owner.fixedPrefixCaptureReserved ||
                        "FOUND".equals(terminalReason)) return false;
                String[] reachedIds=Search.this.reachedPadIds.toArray(new String[0]);
                Vector<String> reachedPthIds=new Vector<String>();
                for(PcbPadPlacement reached:reachedTerminals) reachedPthIds.add(reached.getPadId());
                FixedPrefixBranchLineage lineage=new FixedPrefixBranchLineage(owner.fixedPrefixContext,
                    pass,branchOrdinal,net,
                    start,root,reachedIds,reachedPthIds.toArray(new String[0]),expanded,budget.expanded,
                    holes,terminalReason,
                    layout.geometryFingerprint());
                return owner.deferFixedPrefixCapture(this,lineage);
            }
            private PcbPadPlacement terminalAt(int layer,int cell) {
                if(cell==cell(root) && PcbCopperAccess.hasCopper(root,PcbCopperLayer.values()[layer]))
                    return root;
                for(PcbPadPlacement reached:reachedTerminals)
                    if(PcbCopperAccess.hasCopper(reached,PcbCopperLayer.values()[layer]) &&
                            cell(reached)==cell)
                        return reached;
                return null;
            }
            int heuristic(int cell,int direction,int layer) {
                int value=turnLowerBound==null?distance[cell]:turnLowerBound.value(cell,direction);
                if(relaxedBoundComplete) {
                    int stronger=relaxedDistanceBound.value(cell,layer);
                    if(stronger>=0 && stronger>value) value=stronger;
                }
                return value;
            }
            private boolean zeroViaCapacity() {
                return policy==Policy.FULLER_TWO_LAYER && holes.length>=maximumVias;
            }
            private boolean relaxedBoundObservationRequired() {
                return owner.diagnostics!=null || zeroViaCapacity();
            }
            private boolean shouldStartRelaxedBound() {
                return policy==Policy.FULLER_TWO_LAYER && !relaxedBoundStarted &&
                    expanded>=MAX_BRANCH_EXPANSIONS-WORK_QUANTUM && queue.size()>0 &&
                    budget.expanded<budget.maximum && expanded<MAX_BRANCH_EXPANSIONS;
            }
            private void beginRelaxedBound() {
                if(owner.orderingStream) owner.guidanceStarted=true;
                if(relaxedBoundObservationRequired()) {
                    observer.check(pass);
                    relaxedLayoutGeometryBefore=layout.geometryFingerprint();
                    observer.check(pass);
                    relaxedLiveEffectsBefore=new String[2];
                }
                relaxedBoundStarted=true;
                relaxedQueryFaces=new PcbNetRouter.Router[2];
                if(relaxedBoundObservationRequired())
                    relaxedPristineFaces=new PcbNetRouter.Router[2];
                relaxedCloneFace=0; relaxedPostVerifyFace=0;
            }
            private void advanceRelaxedBoundBuild() {
                observer.check(pass);
                if(relaxedCloneFace<2) {
                    int layer=relaxedCloneFace;
                    PcbNetRouter.Router live=faces[layer];
                    if(live==null)
                        throw new IllegalStateException("P07 relaxed bound is missing a live router face");
                    if(relaxedBoundObservationRequired()) {
                        if(relaxedPristineFaces[layer]==null) {
                            // Keep an untouched comparison reference; the later
                            // query copy may prepare this face for the routed net.
                            relaxedLiveEffectsBefore[layer]=live.fixedGraphObservationEffects();
                            observer.check(pass);
                            PcbNetRouter.Router pristine=live.copyForFixedGraphObservation();
                            observer.check(pass);
                            if(!live.sameFixedGraphObservation(pristine))
                                throw new IllegalStateException("P07 relaxed bound pristine clone differs before scanning");
                            if(!relaxedLiveEffectsBefore[layer].equals(live.fixedGraphObservationEffects()))
                                throw new IllegalStateException("P07 relaxed bound pristine clone changed live router effects");
                            relaxedPristineFaces[layer]=pristine;
                            relaxedBoundWorkUnits++;
                            observer.check(pass);
                            return;
                        }
                    }
                    PcbNetRouter.Router query=live.copyForFixedGraphObservation();
                    observer.check(pass);
                    if(relaxedBoundObservationRequired()) {
                        if(!relaxedPristineFaces[layer].sameFixedGraphObservation(query))
                            throw new IllegalStateException("P07 relaxed bound query clone differs from pristine before scanning");
                        if(!relaxedLiveEffectsBefore[layer].equals(live.fixedGraphObservationEffects()))
                            throw new IllegalStateException("P07 relaxed bound query clone changed live router effects");
                    }
                    relaxedQueryFaces[layer]=query;
                    relaxedCloneFace++;
                    relaxedBoundWorkUnits++;
                    observer.check(pass);
                    return;
                }
                if(relaxedDistanceBound==null) {
                    observer.check(pass);
                    relaxedDistanceBound=new RelaxedDistanceBound(width,height,goal,primary,policy,
                        fixedGraphEdges());
                    relaxedBoundWorkUnits++;
                    observer.check(pass);
                    return;
                }
                if(!relaxedDistanceBound.isComplete()) {
                    observer.check(pass);
                    relaxedDistanceBound.advanceOne();
                    relaxedBoundWorkUnits++;
                    observer.check(pass);
                    return;
                }
                if(relaxedPostVerifyFace<2) {
                    int layer=relaxedPostVerifyFace;
                    PcbNetRouter.Router live=faces[layer];
                    if(relaxedBoundObservationRequired()) {
                        // The scan query may have warmed its local net preparation;
                        // compare live state only to the untouched pre-scan copy.
                        String effectsAfter=live.fixedGraphObservationEffects();
                        if(!live.sameFixedGraphObservation(relaxedPristineFaces[layer]))
                            throw new IllegalStateException("P07 relaxed bound scan changed live router fixed state");
                        if(!relaxedLiveEffectsBefore[layer].equals(effectsAfter))
                            throw new IllegalStateException("P07 relaxed bound scan changed live router effects");
                    }
                    relaxedPostVerifyFace++;
                    relaxedBoundWorkUnits++;
                    observer.check(pass);
                    if(relaxedPostVerifyFace==2) {
                        if(relaxedBoundObservationRequired()) {
                            observer.check(pass);
                            relaxedBoundLiveLayoutStable=relaxedLayoutGeometryBefore.equals(
                                layout.geometryFingerprint());
                            observer.check(pass);
                            if(!relaxedBoundLiveLayoutStable)
                                throw new IllegalStateException("P07 relaxed bound scan changed live layout geometry");
                            relaxedBoundRouterStateStable=true;
                            relaxedBoundRouterEffectsStable=true;
                            relaxedBoundObservationChecked=true;
                            relaxedLayoutGeometryBefore=null;
                        }
                        relaxedBoundComplete=true;
                        relaxedQueryFaces=null;
                        relaxedPristineFaces=null;
                        relaxedLiveEffectsBefore=null;
                    }
                    return;
                }
                throw new IllegalStateException("P07 relaxed bound completed without releasing query state");
            }
            private FixedGraphEdges fixedGraphEdges() {
                return new FixedGraphEdges() {
                    public boolean permitsStep(int layer,int from,int to) {
                        if(layer<0 || layer>1 || from<0 || from>=cells || to<0 || to>=cells)
                            throw new IllegalArgumentException("Invalid P07 relaxed ordinary edge");
                        PcbPadPlacement[] faceTargets=layer==primary ||
                            policy==Policy.FULLER_TWO_LAYER?reachedTerminals:null;
                        return relaxedQueryFaces[layer].permitsLayerStep(
                            x(from),y(from),x(to),y(to),net,start,root,faceTargets);
                    }
                    public boolean permitsVia(int cell) {
                        if(cell<0 || cell>=cells)
                            throw new IllegalArgumentException("Invalid P07 relaxed via cell");
                        return holes.length<maximumVias &&
                            Search.this.relaxedViaFits(net,x(cell),y(cell),holes,holeBounds,
                                relaxedQueryFaces);
                    }
                };
            }
            void offerStep(Node current,int n,int direction) {
                // The face predicate already checks this exact domain corridor.
                // The profile's widest 13-unit stroke still fits inside the 10-unit outline margin.
                PcbPadPlacement[] faceTargets=current.layer==primary ||
                    policy==Policy.FULLER_TWO_LAYER?reachedTerminals:null;
                if(!faces[current.layer].permitsLayerStep(x(current.cell),y(current.cell),x(n),y(n),
                        net,start,root,faceTargets)) return;
                int cost=current.cost+GRID+(current.layer==primary?0:policy.secondaryCost)+
                    (current.direction!=4 && current.direction!=direction?TURN_COST:0);
                offer(queue,best,new Node(n,current.layer,direction,current.transitions,cost,
                    heuristic(n,direction,current.layer),serial++,cells,current));
            }
            void advanceOne() {
                if(relaxedBoundStarted && !relaxedBoundComplete) {
                    advanceRelaxedBoundBuild();
                    return;
                }
                if(shouldStartRelaxedBound()) {
                    beginRelaxedBound();
                    relaxedBoundWorkUnits++;
                    observer.check(pass);
                    return;
                }
                Node current=queue.poll();
                if(current==null) {
                    if(emitTerminal("NO_PATH")) return;
                    throw new Exhausted(net+":NO_PATH");
                }
                if(best.get(current.key)!=current) return;
                if((expanded%128)==0) observer.check(pass);
                if(budget.expanded>=budget.maximum) {
                    if(emitTerminal("TOTAL_SEARCH_LIMIT")) return;
                    throw new Exhausted(net+":TOTAL_SEARCH_LIMIT");
                }
                if(expanded>=MAX_BRANCH_EXPANSIONS) {
                    if(emitTerminal("BRANCH_SEARCH_LIMIT")) return;
                    throw new Exhausted(net+":BRANCH_SEARCH_LIMIT");
                }
                if(relaxedBoundComplete) {
                    int stronger=relaxedDistanceBound.value(current.cell,current.layer);
                    if(zeroViaCapacity() && stronger<0) {
                        if(!relaxedBoundObservationChecked || !relaxedBoundLiveLayoutStable ||
                                !relaxedBoundRouterStateStable || !relaxedBoundRouterEffectsStable)
                            throw new IllegalStateException(
                                "P07 zero-capacity unreachable prune lacks completed fixed-graph comparisons");
                        observer.check(pass);
                        return;
                    }
                    if(stronger>=0 && stronger>current.heuristic &&
                            refreshNodeForBound(current,stronger,best,queue,cells)) {
                        relaxedBoundRefreshes++; relaxedBoundWorkUnits++;
                        return;
                    }
                }
                expanded++; budget.expanded++;
                if(owner.diagnostics!=null) {
                    minimumExploredManhattanDistance=Math.min(minimumExploredManhattanDistance,
                        distance[current.cell]);
                    if(current.layer==PcbCopperLayer.TOP.ordinal()) expandedTop++;
                    else expandedBottom++;
                }
                if(goal[current.layer*cells+current.cell]) {
                    found=current; foundTerminal=terminalAt(current.layer,current.cell);
                    emitTerminal("FOUND"); return;
                }
                int cx=current.cell%width,cy=current.cell/width;
                if(cy>0) offerStep(current,current.cell-width,0);
                if(cx+1<width) offerStep(current,current.cell+1,1);
                if(cy+1<height) offerStep(current,current.cell+width,2);
                if(cx>0) offerStep(current,current.cell-1,3);
                if(current.direction!=4 && current.transitions<policy.transitions &&
                        pathViaFits(current) && viaFits(net,x(current.cell),y(current.cell),holes,holeBounds))
                    offer(queue,best,new Node(current.cell,1-current.layer,4,current.transitions+1,
                        current.cost+policy.viaCost,heuristic(current.cell,4,1-current.layer),serial++,cells,current));
            }
            /** Resumable source-checked copy and physical-mask scan for one selected failure. */
            private final class FixedPrefixCaptureTask {
                private static final int CAPTURE_CHUNK=4096;
                private static final int COPY_CHUNK=WORK_QUANTUM;
                private final FixedPrefixBranchLineage lineage;
                private final PcbNetRouter.Router[] liveFaces=faces;
                private final PcbNetRouter.Router[] pristineFaces=new PcbNetRouter.Router[2];
                private final PcbNetRouter.Router[] queryFaces=new PcbNetRouter.Router[2];
                private final String[] liveEffectsBefore=new String[2];
                private final String prefixGeometryBefore;
                private final int startCell;
                private final int initialFacesMask;
                private final byte[] topStepMasks=new byte[cells];
                private final byte[] bottomStepMasks=new byte[cells];
                private final boolean[] goalFaces=new boolean[cells*2];
                private final boolean[] viaAllowed=new boolean[cells];
                private PcbNetRouter.Router.FixedGraphCopyTask copyTask;
                private PcbNetRouter.Router.FixedGraphComparisonTask comparisonTask;
                private int copyTaskEntryWatermark;
                private int comparisonMode,verificationFace,comparisonEntries;
                private int stage,copyIndex,goalLayer,goalCell,scanCell,scanLayer,scanDirection,viaCell;
                private int captureWorkUnits,copyEntries,goalProbes,ordinaryEdgeProbes,viaProbes;
                private long captureActiveElapsedMillis,currentUnitStartMillis;
                private boolean complete;
                private FixedPrefixGraphSnapshot snapshot;
                FixedPrefixCaptureTask(FixedPrefixBranchLineage lineage) {
                    if(lineage==null || owner.fixedPrefixContext==null || liveFaces[0]==null || liveFaces[1]==null)
                        throw new IllegalArgumentException("Invalid selected fixed-prefix capture");
                    this.lineage=lineage;
                    observer.check(pass);
                    startCell=cell(start);
                    initialFacesMask=(1<<primary)|(1<<(1-primary));
                    prefixGeometryBefore=layout.geometryFingerprint();
                    if(!lineage.prefixGeometryFingerprint().equals(prefixGeometryBefore))
                        throw new IllegalStateException("Fixed-prefix lineage changed before capture began");
                    for(int layer=0;layer<2;layer++)
                        liveEffectsBefore[layer]=liveFaces[layer].fixedGraphObservationEffects();
                    observer.check(pass);
                }
                boolean advanceObservationUnit() {
                    if(complete) return true;
                    currentUnitStartMillis=System.currentTimeMillis();
                    observer.check(pass);
                    captureWorkUnits++;
                    if(stage==0) advanceCopies();
                    else if(stage==1) advanceGoals();
                    else if(stage==2) advanceOrdinaryEdges();
                    else if(stage==3) advanceVias();
                    else if(stage==4) advanceFinalVerification();
                    else if(stage==5) finishCapture();
                    else throw new IllegalStateException("Unknown fixed-prefix capture stage");
                    observer.check(pass);
                    captureActiveElapsedMillis+=Math.max(0L,System.currentTimeMillis()-currentUnitStartMillis);
                    return complete;
                }
                private void advanceCopies() {
                    if(copyIndex>=4) { stage=1; return; }
                    if(comparisonTask!=null) {
                        advanceFixedGraphComparison();
                        if(comparisonTask.equal()) {
                            if(comparisonMode==1 &&
                                    !liveEffectsBefore[copyIndex/2].equals(
                                        liveFaces[copyIndex/2].fixedGraphObservationEffects()))
                                throw new IllegalStateException("Fixed-prefix router clone changed live router effects");
                            comparisonTask=null; comparisonMode=0;
                            copyTask=null; copyTaskEntryWatermark=0; copyIndex++;
                            if(copyIndex>=4) stage=1;
                        }
                        return;
                    }
                    int layer=copyIndex/2;
                    if(copyTask==null) {
                        copyTask=liveFaces[layer].beginFixedGraphObservationCopy();
                        copyTaskEntryWatermark=0;
                    }
                    boolean copied=copyTask.advanceOne(COPY_CHUNK);
                    int copiedTotal=copyTask.copiedEntries();
                    copyEntries+=copiedTotal-copyTaskEntryWatermark;
                    copyTaskEntryWatermark=copiedTotal;
                    if(copied) {
                        PcbNetRouter.Router clone=copyTask.result();
                        if((copyIndex&1)==0) {
                            pristineFaces[layer]=clone;
                            comparisonTask=clone.beginFixedGraphComparison(liveFaces[layer]);
                            comparisonMode=1;
                        } else {
                            if(pristineFaces[layer]==null)
                                throw new IllegalStateException("Fixed-prefix pristine router copy is missing");
                            queryFaces[layer]=clone;
                            comparisonTask=clone.beginFixedGraphComparison(pristineFaces[layer]);
                            comparisonMode=2;
                        }
                    }
                }
                private void advanceFixedGraphComparison() {
                    int compared=comparisonTask.comparedEntries();
                    boolean comparedComplete=comparisonTask.advanceOne(COPY_CHUNK);
                    int comparedTotal=comparisonTask.comparedEntries();
                    comparisonEntries+=comparedTotal-compared;
                    if(comparedComplete && !comparisonTask.equal()) {
                        String message=comparisonMode==2?
                            "Fixed-prefix query clone differs from pristine state":
                            comparisonMode==3?"Fixed-prefix capture changed live router fixed state":
                                "Fixed-prefix router clone differs from live source";
                        throw new IllegalStateException(message);
                    }
                }
                private void advanceGoals() {
                    int operations=0;
                    while(goalLayer<2 && operations<CAPTURE_CHUNK) {
                        if(goalLayer!=primary && policy.transitions==0) { goalLayer++; goalCell=0; continue; }
                        if(goalCell==0) goalFaces[goalLayer*cells+cell(root)]=true;
                        if(goalCell<cells) {
                            if(queryFaces[goalLayer].ownsLayerGridCell(goalCell%width,
                                    goalCell/width,net))
                                goalFaces[goalLayer*cells+goalCell]=true;
                            goalProbes++; goalCell++; operations++; continue;
                        }
                        for(PcbPadPlacement reached:reachedTerminals)
                            if(PcbCopperAccess.hasCopper(reached,PcbCopperLayer.values()[goalLayer]))
                                goalFaces[goalLayer*cells+cell(reached)]=true;
                        goalLayer++; goalCell=0;
                    }
                    if(goalLayer>=2) stage=2;
                }
                private void advanceOrdinaryEdges() {
                    int operations=0;
                    while(scanLayer<2 && operations<CAPTURE_CHUNK) {
                        int cx=scanCell%width,cy=scanCell/width;
                        int next=-1,bit=0;
                        if(scanDirection==0) { if(cy>0) { next=scanCell-width; bit=1; } }
                        else if(scanDirection==1) { if(cx+1<width) { next=scanCell+1; bit=2; } }
                        else if(scanDirection==2) { if(cy+1<height) { next=scanCell+width; bit=4; } }
                        else if(scanDirection==3) { if(cx>0) { next=scanCell-1; bit=8; } }
                        else throw new IllegalStateException("Invalid fixed-prefix direction");
                        if(next>=0) {
                            PcbPadPlacement[] faceTargets=scanLayer==primary ||
                                policy==Policy.FULLER_TWO_LAYER?reachedTerminals:null;
                            if(queryFaces[scanLayer].permitsLayerStep(x(scanCell),y(scanCell),
                                    x(next),y(next),net,start,root,faceTargets)) {
                                if(scanLayer==PcbCopperLayer.TOP.ordinal())
                                    topStepMasks[scanCell]=(byte)(topStepMasks[scanCell]|bit);
                                else bottomStepMasks[scanCell]=(byte)(bottomStepMasks[scanCell]|bit);
                            }
                            ordinaryEdgeProbes++;
                        }
                        operations++; scanDirection++;
                        if(scanDirection>=4) {
                            scanDirection=0; scanCell++;
                            if(scanCell>=cells) { scanCell=0; scanLayer++; }
                        }
                    }
                    if(scanLayer>=2) stage=3;
                }
                private void advanceVias() {
                    int operations=0;
                    while(viaCell<cells && operations<CAPTURE_CHUNK) {
                        viaAllowed[viaCell]=Search.this.relaxedViaFits(net,x(viaCell),
                            y(viaCell),holes,holeBounds,queryFaces);
                        viaCell++; viaProbes++; operations++;
                    }
                    if(viaCell>=cells) stage=4;
                }
                private void advanceFinalVerification() {
                    if(verificationFace>=2) { stage=5; return; }
                    if(comparisonTask==null) {
                        if(pristineFaces[verificationFace]==null)
                            throw new IllegalStateException("Fixed-prefix pristine router copy is missing at final verification");
                        comparisonTask=liveFaces[verificationFace].beginFixedGraphComparison(
                            pristineFaces[verificationFace]);
                        comparisonMode=3;
                    }
                    advanceFixedGraphComparison();
                    if(comparisonTask.equal()) {
                        int layer=verificationFace;
                        if(!liveEffectsBefore[layer].equals(liveFaces[layer].fixedGraphObservationEffects()))
                            throw new IllegalStateException("Fixed-prefix capture changed live router effects");
                        comparisonTask=null; comparisonMode=0;
                        verificationFace++;
                    }
                }
                private void finishCapture() {
                    if(!prefixGeometryBefore.equals(layout.geometryFingerprint()))
                        throw new IllegalStateException("Fixed-prefix capture changed layout geometry");
                    for(int layer=0;layer<2;layer++)
                        if(pristineFaces[layer]==null || queryFaces[layer]==null)
                            throw new IllegalStateException("Fixed-prefix capture lost a verified router copy");
                    snapshot=new FixedPrefixGraphSnapshot(owner.fixedPrefixContext,lineage,width,height,
                        minX,minY,primary,startCell,initialFacesMask,topStepMasks,bottomStepMasks,
                        goalFaces,viaAllowed,captureWorkUnits,copyEntries,goalProbes,
                        ordinaryEdgeProbes,viaProbes,comparisonEntries,captureActiveElapsedMillis+
                            Math.max(0L,System.currentTimeMillis()-currentUnitStartMillis));
                    // The immutable snapshot is the only retained owner of graph arrays.
                    complete=true;
                    comparisonTask=null; copyTask=null;
                    for(int layer=0;layer<2;layer++) {
                        pristineFaces[layer]=null; queryFaces[layer]=null;
                    }
                }
                FixedPrefixGraphSnapshot finish() {
                    if(!complete || snapshot==null)
                        throw new IllegalStateException("Fixed-prefix graph capture is incomplete");
                    return snapshot;
                }
            }
        }
        private final class Publisher {
            final String net;
            final PcbPadPlacement start,root;
            final PcbPadPlacement endTerminal;
            final Node end;
            final Vector<Node> reversed=new Vector<Node>();
            Node collect;
            int stage,qualityIndex=1,length,bends,lastDirection=4;
            int pieceStart,pieceScan=1,pieceEnd,pathIndex,pathCount,piece,compactIndex;
            boolean transition;
            Vector<Point> occupancy;
            Vector<Point> compact;
            int[] traceX,traceY;
            int segments;
            Publisher(String net,PcbPadPlacement start,PcbPadPlacement root,Node end,
                    PcbPadPlacement endTerminal) {
                this.net=net; this.start=start; this.root=root; this.end=end;
                this.endTerminal=endTerminal; collect=end;
            }
            Node nodeAt(int index) { return reversed.get(reversed.size()-1-index); }
            boolean advanceOne() {
                if(stage==0) {
                    if(collect!=null) {
                        reversed.add(collect); collect=collect.previous; return false;
                    }
                    stage=1; return false;
                }
                if(stage==1) {
                    if(qualityIndex<reversed.size()) {
                        Node a=nodeAt(qualityIndex-1),b=nodeAt(qualityIndex++);
                        if(a.layer!=b.layer) { lastDirection=4; return false; }
                        length+=GRID;
                        if(lastDirection!=4 && lastDirection!=b.direction) bends++;
                        lastDirection=b.direction;
                        return false;
                    }
                    int direct=Math.abs(start.getX()-x(end.cell))+Math.abs(start.getY()-y(end.cell));
                    if(direct==0 || length>direct*3 || bends>16) throw new Exhausted(net+":BRANCH_QUALITY_LIMIT");
                    stage=2; pieceStart=0; pieceScan=1; return false;
                }
                if(stage==2) {
                    if(pieceStart>=reversed.size()) { stage=6; return true; }
                    if(pieceScan<reversed.size() && nodeAt(pieceScan).layer==nodeAt(pieceScan-1).layer) {
                        pieceScan++; return false;
                    }
                    pieceEnd=pieceScan-1; transition=pieceScan<reversed.size();
                    pathIndex=pieceStart; pathCount=0; occupancy=new Vector<Point>(); compact=new Vector<Point>();
                    stage=3; return false;
                }
                if(stage==3) {
                    if(pathIndex<=pieceEnd) {
                        Node node=nodeAt(pathIndex++);
                        Point point=new Point(x(node.cell),y(node.cell));
                        occupancy.add(point); pathCount++;
                        int n=compact.size();
                        if(n>=2) {
                            Point a=compact.get(n-2),b=compact.get(n-1);
                            if(a.x==b.x && b.x==point.x || a.y==b.y && b.y==point.y)
                                compact.remove(n-1);
                        }
                        compact.add(point);
                        if(occupancy.size()>=PATH_OCCUPANCY_CHUNK) flushOccupancy();
                        return false;
                    }
                    flushOccupancy();
                    traceX=new int[compact.size()]; traceY=new int[compact.size()]; compactIndex=0;
                    stage=4; return false;
                }
                if(stage==4) {
                    if(compactIndex<compact.size()) {
                        Point point=compact.get(compactIndex);
                        traceX[compactIndex]=point.x; traceY[compactIndex]=point.y; compactIndex++;
                        return false;
                    }
                    stage=5; return false;
                }
                if(stage==5) {
                    Node begin=nodeAt(pieceStart),last=nodeAt(pieceEnd);
                    if(pathCount>1) {
                        String endPad=null;
                        if(pieceEnd==reversed.size()-1) {
                            PcbPadPlacement terminal=endTerminal;
                            if(terminal==null && end.cell==cell(root)) terminal=root;
                            if(terminal!=null) endPad=terminal.getPadId();
                        }
                        layout.addTrace(new PcbTraceGeometry("p07/"+net+"/"+branches+"/"+(piece++),net,
                            pieceStart==0?start.getPadId():null,endPad,PcbCopperLayer.values()[begin.layer],
                            PcbCopperAccess.Exposure.EXPOSED,traceX,traceY,
                            board.getPlacementConstraints().getTraceWidthProfile().widthFor(
                                board.getNet(net).getRoutingRole())));
                        segments+=pathCount-1;
                    }
                    if(transition) {
                        int vx=x(last.cell),vy=y(last.cell); boolean exists=false;
                        for(PcbBoardHole hole:layout.getHoles()) if(hole.x==vx && hole.y==vy) exists=true;
                        if(!exists) layout.addHole(PcbTwoLayerRules.via("p07-via/"+net+"/"+branches+"/"+piece,net,vx,vy));
                        Vector<Point> point=new Vector<Point>(); point.add(new Point(vx,vy));
                        faces[0].occupyLayerPath(point,net); faces[1].occupyLayerPath(point,net);
                        pieceStart=pieceScan; pieceScan=pieceStart+1; stage=2;
                        return false;
                    }
                    stage=6; return true;
                }
                return stage==6;
            }
            private void flushOccupancy() {
                if(occupancy.isEmpty()) return;
                faces[nodeAt(pieceStart).layer].occupyLayerPath(occupancy,net);
                occupancy.clear();
                observer.check(pass);
            }
        }
        void offer(NodeQueue queue,NodeIndex best,Node node) {
            Node previous=best.get(node.key);
            if(previous!=null && previous.cost<=node.cost) return;
            best.put(node.key,node); queue.add(node);
        }
        boolean viaFits(String net,int x,int y,PcbBoardHole[] holes,Rectangle[] holeBounds) {
            if(policy.transitions==0) return false;
            int existing=existingHoleViaResult(net,x,y,holes);
            if(existing>=0) return existing==1;
            if(holes.length>=maximumVias) return false;
            return newViaGeometryFits(net,x,y,holeBounds,faces);
        }
        private boolean relaxedViaFits(String net,int x,int y,PcbBoardHole[] holes,
                Rectangle[] holeBounds,PcbNetRouter.Router[] queryFaces) {
            if(policy.transitions==0) return false;
            int existing=existingHoleViaResult(net,x,y,holes);
            if(existing>=0) return existing==1;
            return newViaGeometryFits(net,x,y,holeBounds,queryFaces);
        }
        private int existingHoleViaResult(String net,int x,int y,PcbBoardHole[] holes) {
            for(PcbBoardHole hole:holes)
                if(hole.x==x && hole.y==y) return net.equals(hole.netId)?1:0;
            return -1;
        }
        private boolean newViaGeometryFits(String net,int x,int y,Rectangle[] holeBounds,
                PcbNetRouter.Router[] occupancyFaces) {
            Rectangle land=new Rectangle(x-PcbTwoLayerRules.VIA_LAND,y-PcbTwoLayerRules.VIA_LAND,
                PcbTwoLayerRules.VIA_LAND*2,PcbTwoLayerRules.VIA_LAND*2);
            if(!PcbTwoLayerRules.inside(layout.getBoardOutline(),land) || !rules.permits(net,land) ||
                    !occupancyFaces[0].freeLayerCell(x,y,net) ||
                    !occupancyFaces[1].freeLayerCell(x,y,net)) return false;
            for(PcbComponentPlacement part:parts)
                if(PcbConductorBuilder.touch(land,part.getRoutingCourtyard())) return false;
            Rectangle clearance=PcbTwoLayerRules.expand(land,PcbTraceRules.MIN_VISIBLE_CLEARANCE);
            for(PcbPadPlacement pad:pads)
                if(PcbConductorBuilder.touch(clearance,pad.getPadBounds())) return false;
            for(Rectangle bounds:holeBounds)
                if(PcbConductorBuilder.touch(clearance,bounds)) return false;
            return true;
        }
        boolean pathViaFits(Node current) {
            if(layout.getHoleCount()+current.transitions+1>maximumVias) return false;
            int pathIndex=0;
            for(Node node=current;node.previous!=null;node=node.previous) {
                if((pathIndex++&255)==0) observer.check(pass);
                if(node.layer!=node.previous.layer && Math.abs(x(node.cell)-x(current.cell))<20 &&
                        Math.abs(y(node.cell)-y(current.cell))<20) return false;
            }
            return true;
        }
    }
    private PcbLayerRoutingPrototype() { }
}
