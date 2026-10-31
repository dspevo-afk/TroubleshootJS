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

        Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias) {
            this(board,placement,policy,observer,maximum,maximumVias,null,false);
        }
        Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
                Diagnostics diagnostics) {
            this(board,placement,policy,observer,maximum,maximumVias,diagnostics,false);
        }
        private Session(TroubleshootBoard board,PcbBoardLayout placement,Policy policy,
                SeededPcbLayoutGenerator.AttemptObserver observer,int maximum,int maximumVias,
                Diagnostics diagnostics,boolean orderingStream) {
            if(board==null || placement==null || policy==null || observer==null ||
                    maximum<1 || maximum>MAX_EXPANSIONS || maximumVias<0 || maximumVias>policy.vias)
                throw new IllegalArgumentException("Invalid P07 request");
            if(orderingStream && policy!=Policy.FULLER_TWO_LAYER)
                throw new IllegalArgumentException("P07 ordering stream requires FULLER_TWO_LAYER");
            this.board=board; this.policy=policy; this.observer=observer;
            this.maximumVias=maximumVias;
            this.diagnostics=diagnostics;
            this.orderingStream=orderingStream;
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
                    reason=failure.getMessage();
                    boolean totalSearchLimit=orderingStream && reason!=null &&
                        reason.endsWith(":TOTAL_SEARCH_LIMIT");
                    previousFailedNet=roleWidths && search!=null?search.currentNet:null;
                    candidate=null; search=null; pass++;
                    if(orderingStream) {
                        turnBoundWorkspace=null;
                        completedOrderings++;
                    }
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
            BranchSearch(String net,PcbPadPlacement start,PcbPadPlacement root) {
                this.net=net; this.start=start; this.root=root;
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
            private void emitTerminal(String terminalReason) {
                if(owner.diagnostics==null) return;
                if(terminalEmitted)
                    throw new IllegalStateException("P07 branch terminal was already observed");
                terminalEmitted=true;
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
            private boolean shouldStartRelaxedBound() {
                return policy==Policy.FULLER_TWO_LAYER && !relaxedBoundStarted &&
                    expanded>=MAX_BRANCH_EXPANSIONS-WORK_QUANTUM && queue.size()>0 &&
                    budget.expanded<budget.maximum && expanded<MAX_BRANCH_EXPANSIONS;
            }
            private void beginRelaxedBound() {
                if(owner.orderingStream) owner.guidanceStarted=true;
                if(owner.diagnostics!=null) {
                    observer.check(pass);
                    relaxedLayoutGeometryBefore=layout.geometryFingerprint();
                    observer.check(pass);
                    relaxedLiveEffectsBefore=new String[2];
                }
                relaxedBoundStarted=true;
                relaxedQueryFaces=new PcbNetRouter.Router[2];
                if(owner.diagnostics!=null) relaxedPristineFaces=new PcbNetRouter.Router[2];
                relaxedCloneFace=0; relaxedPostVerifyFace=0;
            }
            private void advanceRelaxedBoundBuild() {
                observer.check(pass);
                if(relaxedCloneFace<2) {
                    int layer=relaxedCloneFace;
                    PcbNetRouter.Router live=faces[layer];
                    if(live==null)
                        throw new IllegalStateException("P07 relaxed bound is missing a live router face");
                    if(owner.diagnostics!=null) {
                        if(relaxedPristineFaces[layer]==null) {
                            // Keep an untouched diagnostic reference; the later
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
                    if(owner.diagnostics!=null) {
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
                    if(owner.diagnostics!=null) {
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
                        if(owner.diagnostics!=null) {
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
                        return Search.this.relaxedViaFits(net,x(cell),y(cell),holes,holeBounds,
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
                    emitTerminal("NO_PATH");
                    throw new Exhausted(net+":NO_PATH");
                }
                if(best.get(current.key)!=current) return;
                if((expanded%128)==0) observer.check(pass);
                if(budget.expanded>=budget.maximum) {
                    emitTerminal("TOTAL_SEARCH_LIMIT");
                    throw new Exhausted(net+":TOTAL_SEARCH_LIMIT");
                }
                if(expanded>=MAX_BRANCH_EXPANSIONS) {
                    emitTerminal("BRANCH_SEARCH_LIMIT");
                    throw new Exhausted(net+":BRANCH_SEARCH_LIMIT");
                }
                if(relaxedBoundComplete) {
                    int stronger=relaxedDistanceBound.value(current.cell,current.layer);
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
