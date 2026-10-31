package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;

/** Bounded single-layer tree routing with private, ownership-safe congestion recovery. */
final class PcbNetRouter {
    private static final int GRID=10, ROUTING_CHECK_INTERVAL=128;
    static final int MINIMUM_STEP_COST=2;
    private static final int FRESH_STEP_COST=10, CLEARANCE_STEP_COST=7, TURN_COST=35;

    /** Cost lower bound for the two bounded route shapes used by the search. */
    static int admissibleLowerBound(int distance, int minimumStepCost, int minimumTurns) {
        if (distance < 0 || (minimumStepCost != MINIMUM_STEP_COST &&
                minimumStepCost != CLEARANCE_STEP_COST && minimumStepCost != FRESH_STEP_COST) ||
                minimumTurns < 0)
            throw new IllegalArgumentException("Invalid routing lower-bound inputs");
        long total = (long) distance * minimumStepCost + (long) minimumTurns * TURN_COST;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /** Admissible directional lower bound shared by routing and focused contract oracles. */
    static final class GoalTurnBounds {
        private final boolean[] goals;
        private final int gridWidth;
        private final int gridHeight;
        private final int[] rowPrefix;
        private final int[] columnPrefix;
        private final int[] rowTotals;
        private final int[] columnTotals;

        GoalTurnBounds(boolean[] goals, int gridWidth, int gridHeight) {
            if (goals == null || gridWidth < 1 || gridHeight < 1 ||
                    (long) gridWidth * gridHeight != goals.length)
                throw new IllegalArgumentException("Invalid routing goal grid");
            this.goals = goals;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            rowPrefix = new int[gridHeight * (gridWidth + 1)];
            columnPrefix = new int[gridWidth * (gridHeight + 1)];
            rowTotals = new int[gridHeight + 1];
            columnTotals = new int[gridWidth + 1];
            for (int y = 0; y < gridHeight; y++) {
                int offset = y * (gridWidth + 1);
                for (int x = 0; x < gridWidth; x++)
                    rowPrefix[offset + x + 1] = rowPrefix[offset + x] +
                        (goals[y * gridWidth + x] ? 1 : 0);
                rowTotals[y + 1] = rowTotals[y] + rowPrefix[offset + gridWidth];
            }
            for (int x = 0; x < gridWidth; x++) {
                int offset = x * (gridHeight + 1);
                for (int y = 0; y < gridHeight; y++)
                    columnPrefix[offset + y + 1] = columnPrefix[offset + y] +
                        (goals[y * gridWidth + x] ? 1 : 0);
                columnTotals[x + 1] = columnTotals[x] +
                    columnPrefix[offset + gridHeight];
            }
        }

        int minimumTurns(int x, int y, int direction) {
            if (x < 0 || y < 0 || x >= gridWidth || y >= gridHeight ||
                    direction < 0 || direction > 4)
                throw new IllegalArgumentException("Invalid routing turn-bound state");
            int cell = y * gridWidth + x;
            if (goals[cell] || direction == 4) return 0;
            int rowOffset = y * (gridWidth + 1);
            int columnOffset = x * (gridHeight + 1);
            int rowCount = rowPrefix[rowOffset + gridWidth];
            int columnCount = columnPrefix[columnOffset + gridHeight];
            switch (direction) {
            case 0: {
                int forwardAxis = columnPrefix[columnOffset + y];
                int forwardDiagonal = rowTotals[y] - forwardAxis;
                return forwardAxis > 0 ? 0 :
                    (forwardDiagonal > 0 || rowCount + columnCount > 0 ? 1 : 2);
            }
            case 1: {
                int forwardAxis = rowPrefix[rowOffset + gridWidth] -
                    rowPrefix[rowOffset + x + 1];
                int forwardDiagonal = columnTotals[gridWidth] - columnTotals[x + 1] -
                    forwardAxis;
                return forwardAxis > 0 ? 0 :
                    (forwardDiagonal > 0 || rowCount + columnCount > 0 ? 1 : 2);
            }
            case 2: {
                int forwardAxis = columnPrefix[columnOffset + gridHeight] -
                    columnPrefix[columnOffset + y + 1];
                int forwardDiagonal = rowTotals[gridHeight] - rowTotals[y + 1] -
                    forwardAxis;
                return forwardAxis > 0 ? 0 :
                    (forwardDiagonal > 0 || rowCount + columnCount > 0 ? 1 : 2);
            }
            case 3: {
                int forwardAxis = rowPrefix[rowOffset + x];
                int forwardDiagonal = columnTotals[x] - forwardAxis;
                return forwardAxis > 0 ? 0 :
                    (forwardDiagonal > 0 || rowCount + columnCount > 0 ? 1 : 2);
            }
            default:
                throw new IllegalArgumentException("Unknown routing direction");
            }
        }
    }
    enum Reason { NO_PATH, GRID_LIMIT, SEARCH_LIMIT, RECOVERY_LIMIT, QUALITY_LIMIT }
    static final class Rejected extends RuntimeException {
        final Reason reason;
        final String blockedNet;
        final PcbRoutingWork.Statistics statistics;
        Rejected(String message) { this(Reason.NO_PATH,message,null); }
        Rejected(Reason reason,String message,String blockedNet) { this(reason,message,blockedNet,null); }
        Rejected(Reason reason,String message,String blockedNet,PcbRoutingWork.Statistics statistics) {
            super(message);
            if(reason==null) throw new IllegalArgumentException("Missing routing rejection reason");
            this.reason=reason; this.blockedNet=blockedNet; this.statistics=statistics;
        }
    }
    private static Rejected reject(PcbRoutingRejectedException.Kind kind,String message) {
        return new Rejected(Reason.NO_PATH,message,null);
    }
    private static Rectangle traceCollisionEnvelope(Rectangle r,int width) {
        PcbTraceRules.requireWidth(width);
        int half=width/2;
        // Saved uniform routes keep the original symmetric inclusive envelope.
        if(width==PcbTraceRules.TRACE_WIDTH)
            return new Rectangle(r.x-half,r.y-half,r.width+half*2,r.height+half*2);
        // This rectangle is consumed as an inclusive range of centerline
        // coordinates. Wider odd strokes overlap [a,b) exactly when their
        // centerline lies in [a-half,b+half-1].
        int spanExpansion=2*half-1;
        return new Rectangle(r.x-half,r.y-half,r.width+spanExpansion,
            r.height+spanExpansion);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer) {
        route(layout,board,outline,attempt,observer,true);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical) {
        route(layout,board,outline,attempt,observer,canonical,0);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed) {
        route(layout,board,outline,attempt,observer,canonical,routingSeed,0);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed,int treeVariant) {
        route(layout,board,outline,attempt,observer,canonical,routingSeed,treeVariant,null);
    }
    static void route(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed,
            int treeVariant,Vector<String> blocked) {
        routeWithLimits(layout,board,outline,attempt,observer,canonical,routingSeed,treeVariant,blocked,
            PcbRoutingWork.Limits.DEFAULT);
    }
    /** Tighter limits qualify ablation/exhaustion without a global mutable mode. */
    static void routeWithLimits(PcbBoardLayout output,TroubleshootBoard board,Rectangle outline,int attempt,
            SeededPcbLayoutGenerator.AttemptObserver observer,boolean canonical,long routingSeed,
            int treeVariant,Vector<String> blocked,PcbRoutingWork.Limits limits) {
        if(output==null || board==null || outline==null || observer==null || attempt<0 || treeVariant<0 || treeVariant>=PcbRoutingWork.Limits.MAX_ORDERINGS)
            throw new IllegalArgumentException("Invalid routing request");
        outline=new Rectangle(outline);
        blocked=blocked==null?null:new Vector<String>(blocked);
        PcbBoardLayout template=output.copyForRouting();
        output.validateAgainst(board);
        PcbFactoryLinkPolicy.validateLayout(output);
        Rectangle actual=template.getBoardOutline();
        if(actual.x!=outline.x || actual.y!=outline.y || actual.width!=outline.width || actual.height!=outline.height)
            throw new IllegalArgumentException("Routing outline differs from the placement");
        Vector<NetRequest> requests=new Vector<NetRequest>();
        for(String net:board.getNetIds()) requests.add(new NetRequest(board,template,net));
        final PcbRoutingWork work=new PcbRoutingWork(limits,requests.size());
        String promoted=null;
        Rejected last=null;
        try {
            for(int pass=0;pass<limits.orderings;pass++) {
                observer.check(attempt);
                Vector<NetRequest> order=ordered(requests,board,attempt,routingSeed,treeVariant,pass,promoted,blocked);
                // Retain P04's useful bounded tree candidates inside this one budget.
                int treeChoice=(treeVariant+pass)%PcbRoutingWork.Limits.MAX_ORDERINGS;
                String key=orderKey(order)+"tree="+treeChoice;
                work.orderingPasses++;
                work.decisions.append("order[").append(pass).append("]=").append(key).append(';');
                PcbBoardLayout candidate=template.copyForRouting();
                Router router=new Router(candidate,board,outline,attempt,observer);
                router.work=work;
                HashSet<String> completed=new HashSet<String>();
                try {
                    for(NetRequest net:order) {
                        if(completed.contains(net.id)) continue;
                        try {
                            routeNet(router,net,attempt,routingSeed,treeChoice,false);
                            completed.add(net.id);
                        } catch(Rejected failure) {
                            if(failure.reason!=Reason.NO_PATH) throw failure;
                            Vector<String> victims=router.victims(completed,limits.victimsPerPass);
                            if(victims.isEmpty() || work.ripUpPasses>=limits.ripUpPasses ||
                                    work.reroutedNets+victims.size()+1>limits.reroutedNets) throw failure;
                            work.ripUpPasses++;
                            work.rippedNets+=victims.size();
                            work.decisions.append("rip[").append(net.id).append("]=").append(victims).append(';');
                            router.removeNets(victims);
                            completed.removeAll(victims);
                            // Failed net gets its constrained route first; no partial
                            // copper or board mutation can escape this private attempt.
                            routeNet(router,net,attempt,routingSeed,treeChoice,true);
                            completed.add(net.id);
                            for(String victim:victims) {
                                NetRequest request=find(requests,victim);
                                routeNet(router,request,attempt,routingSeed,treeChoice,true);
                                completed.add(victim);
                            }
                            work.decisions.append("recovered[").append(net.id).append("];");
                        }
                    }
                    router.requireExactOccupancy();
                    if(canonical) PcbRouteCanonicalizer.canonicalize(candidate);
                    router.requireExactOccupancy();
                    candidate.validateRoutingGeometry(board);
                    candidate.validateRouteQuality();
                    // Last cancellation checkpoint is BEFORE the only publication.
                    observer.check(attempt);
                    output.replaceTraces(candidate.getTraces());
                    work.outcome=PcbRoutingWork.Outcome.SUCCESS;
                    return;
                } catch(Rejected failure) {
                    last=failure; promoted=failure.blockedNet;
                    work.candidateFailures++;
                    work.decisions.append("reject[").append(failure.reason).append(':').append(promoted).append("];");
                    if(failure.reason!=Reason.NO_PATH) break;
                } catch(PcbBoardLayout.RouteQualityRejectedException quality) {
                    last=new Rejected(Reason.QUALITY_LIMIT,quality.getMessage(),null);
                    promoted=null; work.candidateFailures++;
                    work.decisions.append("reject[QUALITY:").append(quality.getKind()).append("];");
                }
            }
            if(last==null) throw new IllegalStateException("Routing stopped without a candidate outcome");
            work.outcome=PcbRoutingWork.Outcome.EXHAUSTED;
            work.rejectionReason=last.reason;
            throw new Rejected(last.reason,last.getMessage(),last.blockedNet,work.snapshot());
        } catch(Rejected failure) {
            work.outcome=PcbRoutingWork.Outcome.EXHAUSTED;
            work.rejectionReason=failure.reason;
            if(work.candidateFailures==0) work.candidateFailures++;
            if(failure.statistics!=null) throw failure;
            throw new Rejected(failure.reason,failure.getMessage(),failure.blockedNet,work.snapshot());
        } finally {
            output.setRoutingStatistics(work.expansions,work.rawSegments,work.congestionRejections);
            output.setRoutingRecoveryStatistics(work.snapshot());
        }
    }
    /** Snapshot canonical pad membership and typed demand once, before any route. */
    private static final class NetRequest {
        final String id;
        final Vector<String> pads;
        final int priority,degree;
        final long span;
        NetRequest(TroubleshootBoard board,PcbBoardLayout layout,String id) {
            this.id=id; pads=board.getNet(id).getPadIds(); Collections.sort(pads);
            if(pads.isEmpty()) throw new IllegalStateException("Cannot route a net without pads: "+id);
            priority=priority(board,id); degree=pads.size();
            int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
            for(String pad:pads) {
                PcbPadPlacement p=layout.getPad(pad);
                minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY());
                maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY());
            }
            span=(long)maxX-minX+(long)maxY-minY;
        }
    }
    private static NetRequest find(Vector<NetRequest> requests,String id) {
        for(NetRequest request:requests) if(request.id.equals(id)) return request;
        throw new IllegalStateException("Unknown routing owner: "+id);
    }
    private static Vector<NetRequest> ordered(Vector<NetRequest> requests,TroubleshootBoard board,
            int attempt,long routingSeed,int treeVariant,final int pass,String promoted,Vector<String> blocked) {
        Vector<NetRequest> nets=new Vector<NetRequest>(requests);
        final int initial=board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM?(attempt/2)%4:0;
        Collections.sort(nets,new Comparator<NetRequest>() { public int compare(NetRequest a,NetRequest b) {
            if(pass==2 || pass==3) {
                if(pass==3 && a.span!=b.span) return a.span<b.span?-1:1;
                if(a.degree!=b.degree) return a.degree>b.degree?-1:1;
                if(a.priority!=b.priority) return a.priority<b.priority?-1:1;
                if(a.span!=b.span) return a.span>b.span?-1:1;
                return b.id.compareTo(a.id);
            }
            int ap=a.priority,bp=b.priority;
            if(initial==1) { ap=-ap; bp=-bp; }
            if(initial>=2) { ap=a.degree; bp=b.degree; if(initial==2) {ap=-ap;bp=-bp;} }
            return ap==bp?a.id.compareTo(b.id):ap<bp?-1:1;
        }});
        // Preserve the existing seeded initial order for hard bottom-layer placements.
        // New recovery alternatives are conflict/demand informed, not random retries.
        if(pass!=2 && pass!=3 && board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM && attempt>=8)
            NamedRandomStreams.shuffle(nets,new java.util.Random(routingSeed ^ (0x632be59bd9b4e019L*(attempt+1))));
        if(pass==4 || pass<2 && treeVariant>=2) Collections.reverse(nets);
        if(pass==0 && blocked!=null) for(String id:blocked) {
            NetRequest net=find(requests,id); nets.remove(net); nets.add(0,net);
        }
        if(pass==1 && promoted!=null) {
            NetRequest net=find(requests,promoted); nets.remove(net); nets.add(0,net);
        }
        return nets;
    }
    private static String orderKey(Vector<NetRequest> order) {
        StringBuilder key=new StringBuilder();
        for(NetRequest net:order) key.append(net.id.length()).append(':').append(net.id).append(',');
        return key.toString();
    }
    private static void routeNet(Router router,NetRequest net,int attempt,long seed,int treeVariant,boolean reroute) {
        if(net.degree==1) return;
        router.work.beginNet(net.id,reroute);
        router.conflicts.clear();
        Vector<String> remaining=new Vector<String>(net.pads),reached=new Vector<String>();
        String root=chooseRoot(router.layout,remaining,attempt%2);
        remaining.remove(root); reached.add(root);
        java.util.Random branchOrder=new java.util.Random(seed ^ net.id.hashCode() ^ (0x9e3779b97f4a7c15L*(attempt+1)));
        boolean trunk=false;
        try {
            while(!remaining.isEmpty()) {
                String next=treeVariant%2==1?remaining.get(branchOrder.nextInt(remaining.size())):
                    treeVariant>=2?chooseFarthest(router.layout,remaining,reached):chooseNext(router.layout,remaining,reached);
                remaining.remove(next);
                if(internallyReached(router.board,next,reached)) { reached.add(next); continue; }
                PcbTraceGeometry trace=router.route(net.id,next,trunk?null:root);
                router.layout.addTrace(trace); reached.add(next); trunk=true;
            }
        } catch(Rejected failure) {
            Vector<String> partial=new Vector<String>(); partial.add(net.id);
            router.removeNets(partial);
            throw new Rejected(failure.reason,failure.getMessage(),net.id);
        }
    }
    static int priority(TroubleshootBoard board,String net) {
        for(String id:board.getPowerInputIds()) {
            ExternalBoardPowerInput input=board.getPowerInput(id);
            if(net.equals(input.getReturnNetId())) return 0;
            if(net.equals(input.getPositiveNetId())) return 1;
        }
        return board.getNet(net).getRoutingRole().priority;
    }
    private static String chooseRoot(PcbBoardLayout layout,Vector<String> pads,int variant) {
        String best=pads.get(0); long bestCost=variant==0?Long.MAX_VALUE:Long.MIN_VALUE;
        for(String id:pads) {
            PcbPadPlacement a=layout.getPad(id); long sum=0;
            for(String other:pads) { PcbPadPlacement b=layout.getPad(other);sum+=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()); }
            if(variant==0?sum<bestCost:sum>bestCost) { best=id;bestCost=sum; }
        }
        return best;
    }
    /** Canonical nearest-to-reached branch selection shared by both route owners. */
    static String chooseNext(PcbBoardLayout layout,Vector<String> remaining,Vector<String> reached) {
        String best=remaining.get(0); long distance=Long.MAX_VALUE;
        for(String id:remaining) for(String other:reached) {
            PcbPadPlacement a=layout.getPad(id),b=layout.getPad(other);
            long d=Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY());
            if(d<distance) {best=id;distance=d;}
        }
        return best;
    }
    private static String chooseFarthest(PcbBoardLayout layout,Vector<String> remaining,Vector<String> reached) {
        String best=remaining.get(0);long farthest=-1;
        for(String id:remaining) {
            long nearest=Long.MAX_VALUE;PcbPadPlacement a=layout.getPad(id);
            for(String other:reached) {
                PcbPadPlacement b=layout.getPad(other);
                nearest=Math.min(nearest,Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY()));
            }
            if(nearest>farthest) {best=id;farthest=nearest;}
        }
        return best;
    }
    private static boolean internallyReached(TroubleshootBoard board,String id,Vector<String> reached) {
        BoardPad pad=board.getPad(id);
        for(String other:reached) {
            BoardPad previous=board.getPad(other);
            if(pad.getComponentId().equals(previous.getComponentId()) && board.getComponent(pad.getComponentId()).getPhysicalPackage()
                    .isInternallyConnected(pad.getTerminalId(),previous.getTerminalId())) return true;
        }
        return false;
    }
    static final class Router {
        PcbRoutingWork work;
        final TreeMap<String,Integer> conflicts=new TreeMap<String,Integer>();
        int expansions,rawSegments,congestionRejections;
        private final PcbBoardLayout layout;
        private final TroubleshootBoard board;
        private final boolean checkProfiledPadTouches;
        private final Rectangle outline;
        private final int minX;
        private final int minY;
        private final int gridWidth;
        private final int gridHeight;
        private final String[][] occupiedNet;
        private final String[][] clearanceNet;
        // Placement is immutable for this routing attempt. Take one private
        // snapshot instead of copying collections and physical bounds per edge.
        private final PcbPadPlacement[] pads;
        private final String[] padNets;
        private final int[] padWidths;
        private final Rectangle[] padBounds;
        private final Rectangle[] escapeCorridors;
        private final Rectangle[] escapeReservations;
        private final PcbComponentPlacement[] components;
        private final Rectangle[] courtyards;
        private final int[] allComponentIndexes;
        private final int[][] componentCandidatesByColumn;
        private Rectangle[] collisionCourtyards;
        private int activeTraceWidth=-1;
        private String activeNetId;
        private final int attempt;
        private final SeededPcbLayoutGenerator.AttemptObserver observer;
        private final PcbCopperLayer layer;
        private final PcbTwoLayerRules domainRules;
        private boolean emptyComponentFace=true;
        boolean factoryUnderpasses=true;
        private String[][] horizontalReservation,verticalReservation,padAt;

        Router(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
                int attempt, SeededPcbLayoutGenerator.AttemptObserver observer) {
            this(layout,board,outline,attempt,observer,board.getPlacementConstraints().routingLayer);
        }
        Router(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
                SeededPcbLayoutGenerator.AttemptObserver observer,PcbCopperLayer selectedLayer) {
            this(layout,board,outline,attempt,observer,selectedLayer,0);
        }
        Router(PcbBoardLayout layout,TroubleshootBoard board,Rectangle outline,int attempt,
                SeededPcbLayoutGenerator.AttemptObserver observer,PcbCopperLayer selectedLayer,int escapeOutletLand) {
            this.layout = layout;
            work=new PcbRoutingWork(PcbRoutingWork.Limits.DEFAULT,board.getNetIds().size());
            this.board = board;
            checkProfiledPadTouches=!board.getPlacementConstraints().getTraceWidthProfile().isLegacyUniform();
            this.outline = outline;
            this.attempt = attempt;
            this.observer = observer;
            layer=selectedLayer;
            domainRules=board.getPlacementConstraints().getBarriers().isEmpty()?null:
                new PcbTwoLayerRules(board,layout);
            minX = outline.x + GRID;
            minY = outline.y + GRID;
            gridWidth = (outline.width - 2 * GRID) / GRID + 1;
            gridHeight = (outline.height - 2 * GRID) / GRID + 1;
            if (gridWidth < 1 || gridHeight < 1 || (long)gridWidth*gridHeight > PcbRoutingWork.MAX_GRID_CELLS)
                throw new Rejected(Reason.GRID_LIMIT,"ROUTING_GRID_BUDGET",null);
            occupiedNet = new String[gridWidth][gridHeight];
            clearanceNet = new String[gridWidth][gridHeight];
            int maximumWidth=board.getPlacementConstraints().getTraceWidthProfile().maximumWidth();
            if(PcbTraceRules.clearance(maximumWidth,maximumWidth)>2*GRID)
                throw new IllegalArgumentException("PCB role trace width exceeds the bounded routing grid clearance");
            pads = layout.getPads().toArray(new PcbPadPlacement[0]);
            padNets = new String[pads.length];
            padWidths = new int[pads.length];
            padBounds = new Rectangle[pads.length];
            escapeCorridors = new Rectangle[pads.length];
            escapeReservations = new Rectangle[pads.length];
            for (int index = 0; index < pads.length; index++) {
                PcbPadPlacement pad = pads[index];
                if(!PcbCopperAccess.hasCopper(pad,layer))
                    throw new IllegalArgumentException("Requested routing layer has no pad land: "+pad.getPadId());
                padNets[index] = board.getPad(pad.getPadId()).getNetId();
                padWidths[index] = traceWidth(padNets[index]);
                padBounds[index] = pad.getPadBounds();
                int length = ((pad.getEscapeLength() + GRID - 1) / GRID) * GRID;
                if(escapeOutletLand>0 && pad.getEscapeLength()>0 && pad.getMountingSide()==layer.getFace()) {
                    // A P07 via land must clear the owning courtyard before earlier copper can use this outlet.
                    Rectangle owner=layout.getComponent(board.getPad(pad.getPadId()).getComponentId()).getRoutingCourtyard();
                    int dx=pad.getEscapeDx(),dy=pad.getEscapeDy();
                    int edge=dx!=0?(dx>0?owner.x+owner.width:owner.x):(dy>0?owner.y+owner.height:owner.y);
                    int distance=dx!=0?(edge-pad.getX())*dx:(edge-pad.getY())*dy;
                    int outlet=distance+escapeOutletLand+1; // Via contact with the courtyard boundary is forbidden.
                    length=Math.max(length,((outlet+GRID-1)/GRID)*GRID);
                }
                int ex = pad.getX() + pad.getEscapeDx() * length;
                int ey = pad.getY() + pad.getEscapeDy() * length;
                escapeCorridors[index] = new Rectangle(Math.min(pad.getX(), ex),
                    Math.min(pad.getY(), ey), Math.abs(pad.getX() - ex),
                    Math.abs(pad.getY() - ey));
                if(pad.getMountingSide()!=layer.getFace())
                    escapeCorridors[index]=new Rectangle(pad.getX(),pad.getY(),0,0);
            }
            components = layout.getComponents().toArray(new PcbComponentPlacement[0]);
            courtyards = new Rectangle[components.length];
            for (int index = 0; index < components.length; index++) {
                courtyards[index] = components[index].getRoutingCourtyard();
                if(components[index].getMountingSide()==layer.getFace()) emptyComponentFace=false;
            }
            allComponentIndexes = new int[components.length];
            for (int index = 0; index < components.length; index++)
                allComponentIndexes[index] = index;
            componentCandidatesByColumn = buildComponentCandidates(maximumWidth);
            // The private placement snapshot is fixed for this routing attempt.
            // Reuse its exact escape-step reservations on either component face.
            horizontalReservation=new String[gridWidth][gridHeight];verticalReservation=new String[gridWidth][gridHeight];
            if(emptyComponentFace) padAt=new String[gridWidth][gridHeight];
            for(int i=0;i<pads.length;i++) {
                int px=gridX(pads[i].getX()),py=gridY(pads[i].getY());
                if(emptyComponentFace && px>=0&&py>=0&&px<gridWidth&&py<gridHeight)padAt[px][py]=pads[i].getPadId();
            }
        }
        /** Conservative x-only broad phase; exact, width-specific geometry stays at each query. */
        private int[][] buildComponentCandidates(int maximumWidth) {
            int[][] result = new int[gridWidth][];
            int[] scratch = new int[components.length];
            int faceCount = 0;
            boolean safeCourtyardExtents = true;
            for (int index = 0; index < components.length; index++) {
                if (components[index].getMountingSide() != layer.getFace()) continue;
                scratch[faceCount++] = index;
                long right = (long)courtyards[index].x + courtyards[index].width;
                if (right < Integer.MIN_VALUE || right > Integer.MAX_VALUE)
                    safeCourtyardExtents = false;
            }
            int[] faceIndexes = new int[faceCount];
            System.arraycopy(scratch, 0, faceIndexes, 0, faceCount);
            for (int column = 0; column < gridWidth; column++) {
                if (observer != null && column != 0 && (column & 1023) == 0)
                    observer.check(attempt);
                long centerX = (long)minX + (long)column * GRID;
                long left = centerX - GRID - maximumWidth;
                long right = centerX + GRID + maximumWidth;
                int legacyCenterX = minX + column * GRID;
                if (!safeCourtyardExtents || centerX != legacyCenterX ||
                        left < Integer.MIN_VALUE || right > Integer.MAX_VALUE) {
                    result[column] = faceIndexes;
                    continue;
                }
                int count = 0;
                for (int faceIndex = 0; faceIndex < faceIndexes.length; faceIndex++) {
                    int index = faceIndexes[faceIndex];
                    Rectangle courtyard = courtyards[index];
                    long courtyardLeft = courtyard.x;
                    long courtyardRight = (long)courtyard.x + courtyard.width;
                    if (courtyardRight >= left && courtyardLeft <= right)
                        scratch[count++] = index;
                }
                int[] candidates = new int[count];
                System.arraycopy(scratch, 0, candidates, 0, count);
                result[column] = candidates;
            }
            return result;
        }
        /** Prepare exact role-width geometry once per routed net, outside its bounded search loop. */
        private void prepareNet(String netId) {
            if(netId!=null && netId.equals(activeNetId)) return;
            int width=traceWidth(netId);
            activeNetId=netId;
            if(activeTraceWidth==width) return;
            activeTraceWidth=width;
            collisionCourtyards=new Rectangle[components.length];
            for(int index=0;index<components.length;index++)
                collisionCourtyards[index]=traceCollisionEnvelope(courtyards[index],width);
            for(int x=0;x<gridWidth;x++) {
                java.util.Arrays.fill(horizontalReservation[x],null);
                java.util.Arrays.fill(verticalReservation[x],null);
            }
            for(int i=0;i<pads.length;i++) {
                int margin=PcbTraceRules.clearance(width,padWidths[i])-1;
                Rectangle corridor=escapeCorridors[i];
                Rectangle r=new Rectangle(corridor.x-margin,corridor.y-margin,
                    corridor.width+margin*2,corridor.height+margin*2);
                escapeReservations[i]=r;
                reserveRectangle(r,padNets[i]);
                if(checkProfiledPadTouches)
                    reservePadTouchSteps(i,width);
            }
        }
        /** Reserve only adjacent grid edges whose actual finite-width stroke touches a foreign land. */
        private void reservePadTouchSteps(int padIndex,int width) {
            Rectangle bounds=padBounds[padIndex];
            int half=width/2;
            int horizontalX0=clampGridStart((long)bounds.x-GRID-half-1,minX,gridWidth);
            int horizontalX1=clampGridEnd((long)bounds.x+bounds.width+half,minX,gridWidth);
            int horizontalY0=clampGridStart((long)bounds.y-half-1,minY,gridHeight);
            int horizontalY1=clampGridEnd((long)bounds.y+bounds.height+half,minY,gridHeight);
            for(int x=horizontalX0;x<=horizontalX1;x++)
                for(int y=horizontalY0;y<=horizontalY1;y++) {
                    int firstX=minX+x*GRID,firstY=minY+y*GRID;
                    if(PcbConductorBuilder.touch(traceStroke(firstX,firstY,firstX+GRID,firstY,width),bounds))
                        reserve(horizontalReservation,x,y,padNets[padIndex]);
                }

            int verticalX0=clampGridStart((long)bounds.x-half-1,minX,gridWidth);
            int verticalX1=clampGridEnd((long)bounds.x+bounds.width+half,minX,gridWidth);
            int verticalY0=clampGridStart((long)bounds.y-GRID-half-1,minY,gridHeight);
            int verticalY1=clampGridEnd((long)bounds.y+bounds.height+half,minY,gridHeight);
            for(int x=verticalX0;x<=verticalX1;x++)
                for(int y=verticalY0;y<=verticalY1;y++) {
                    int firstX=minX+x*GRID,firstY=minY+y*GRID;
                    if(PcbConductorBuilder.touch(traceStroke(firstX,firstY,firstX,firstY+GRID,width),bounds))
                        reserve(verticalReservation,x,y,padNets[padIndex]);
                }
        }
        private int clampGridStart(long physical,int origin,int size) {
            long delta=physical-origin;
            long index=delta/GRID;
            if(delta>0 && delta%GRID!=0) index++;
            return (int)Math.max(0,Math.min((long)size,index));
        }
        private int clampGridEnd(long physical,int origin,int size) {
            long delta=physical-origin;
            long index=delta/GRID;
            if(delta<0 && delta%GRID!=0) index--;
            return (int)Math.max(-1,Math.min((long)size-1,index));
        }
        private void reserveRectangle(Rectangle r,String net) {
            int x0=Math.max(0,(r.x-minX)/GRID-1),x1=Math.min(gridWidth-1,(r.x+r.width-minX)/GRID+1);
            int y0=Math.max(0,(r.y-minY)/GRID-1),y1=Math.min(gridHeight-1,(r.y+r.height-minY)/GRID+1);
            for(int x=x0;x<=x1;x++) for(int y=y0;y<=y1;y++) {
                if(r.intersects(new Rectangle(minX+x*GRID,minY+y*GRID,GRID,1))) reserve(horizontalReservation,x,y,net);
                if(r.intersects(new Rectangle(minX+x*GRID,minY+y*GRID,1,GRID))) reserve(verticalReservation,x,y,net);
            }
        }
        private int traceWidth(String netId) {
            BoardNet net=board.getNet(netId);
            if(net==null) throw new IllegalArgumentException("Unknown PCB routing net: "+netId);
            return board.getPlacementConstraints().getTraceWidthProfile().widthFor(net.getRoutingRole());
        }
        private void reserve(String[][] cells,int x,int y,String net) {
            cells[x][y]=cells[x][y]==null || cells[x][y].equals(net)?net:"";
        }

        PcbTraceGeometry route(String netId, String startPadId, String endPadId) {
            prepareNet(netId);
            work.branchSearches++;
            PcbPadPlacement startPad = layout.getPad(startPadId);
            PcbPadPlacement endPad = layout.getPad(endPadId);
            int startX = gridX(startPad.getX());
            int startY = gridY(startPad.getY());
            int endX = endPad == null ? -1 : gridX(endPad.getX());
            int endY = endPad == null ? -1 : gridY(endPad.getY());
            if (startX < 0 || startY < 0 || startX >= gridWidth || startY >= gridHeight ||
                    (endPad != null && (endX < 0 || endY < 0 || endX >= gridWidth || endY >= gridHeight)))
                throw new IllegalStateException("Pad is not aligned to PCB routing grid: " +
                    netId);
            boolean[] goals=new boolean[gridWidth*gridHeight];
            if(endPad!=null) goals[endY*gridWidth+endX]=true;
            else for(int gx=0;gx<gridWidth;gx++) for(int gy=0;gy<gridHeight;gy++)
                goals[gy*gridWidth+gx]=netId.equals(occupiedNet[gx][gy]) && canJoinTree(gx,gy,netId);
            int[] distances=distanceField(goals);
            GoalTurnBounds turnBounds=new GoalTurnBounds(goals,gridWidth,gridHeight);
            // A tree join may reach non-joinable own copper on the way to an
            // admissible trunk cell; keep the global minimum in that shape.
            int minimumStepCost=endPad==null ? MINIMUM_STEP_COST : minimumStepCost(netId);

            final int noneDirection = 4;
            final int[] directionX = { 0, 1, 0, -1 };
            final int[] directionY = { -1, 0, 1, 0 };
            // Flat, zero-initialized storage avoids four forests of per-cell
            // arrays in JavaScript. Previous uses zero for unseen, -1 for the
            // root, and prior state + 1 for a discovered predecessor.
            double[] bestCost = new double[gridWidth*gridHeight*5];
            int[] previous = new int[bestCost.length];
            SearchQueue open = new SearchQueue();
            int sequence = 0;
            previous[stateKey(startX,startY,noneDirection)] = -1;
            open.add(new SearchNode(startX, startY, noneDirection, 0,
                lowerBound(distances[startY*gridWidth+startX],minimumStepCost,
                    turnBounds.minimumTurns(startX,startY,noneDirection)), sequence++));
            SearchNode goal = null;
            int expanded = 0;
            while (!open.isEmpty()) {
                if ((expanded++ % ROUTING_CHECK_INTERVAL) == 0)
                    observer.check(attempt);
                if (expanded > (long)gridWidth*gridHeight*PcbRoutingWork.SEARCH_EXPANSIONS_PER_CELL)
                    throw new Rejected(Reason.SEARCH_LIMIT,"SEARCH_WORK_BUDGET",netId);
                work.expand(netId);
                SearchNode current = open.poll();
                expansions++;
                int currentKey=stateKey(current.x,current.y,current.direction);
                if (current.cost != bestCost[currentKey])
                    continue;
                if (goals[current.y*gridWidth+current.x]) {
                    goal = current;
                    break;
                }
                for (int direction = 0; direction < 4; direction++) {
                    int nextX = current.x + directionX[direction];
                    int nextY = current.y + directionY[direction];
                    if (nextX < 0 || nextY < 0 || nextX >= gridWidth || nextY >= gridHeight ||
                            !isLegalMove(current.x, current.y, nextX, nextY, direction, startX, startY,
                                endX, endY, startPad, endPad) ||
                            !canTraverse(current.x, current.y, nextX, nextY, startPad, endPad,netId) ||
                            !canOccupy(nextX, nextY, netId, startPad, endPad))
                        continue;
                    double stepCost = GRID;
                    if (netId.equals(occupiedNet[nextX][nextY]))
                        stepCost = MINIMUM_STEP_COST;
                    else if (netId.equals(clearanceNet[nextX][nextY]))
                        stepCost = 7;
                    double cost = current.cost + stepCost;
                    if (current.direction != noneDirection && current.direction != direction)
                        cost += 35;
                    int nextKey=stateKey(nextX,nextY,direction);
                    if (previous[nextKey]!=0 && cost >= bestCost[nextKey])
                        continue;
                    bestCost[nextKey] = cost;
                    previous[nextKey] = currentKey+1;
                    open.add(new SearchNode(nextX, nextY, direction, cost,
                        lowerBound(distances[nextY*gridWidth+nextX],minimumStepCost,
                            turnBounds.minimumTurns(nextX,nextY,direction)),
                        sequence++));
                }
            }
            if (goal == null)
                throw reject(PcbRoutingRejectedException.Kind.ROUTING,
                    "Unable to route net " + netId + " from " +
                    startPadId + "@" + startPad.getX() + "," + startPad.getY() + " to " +
                    (endPad==null ? "connected tree" : endPadId + "@" + endPad.getX() + "," + endPad.getY()));

            Vector<Point> points = new Vector<Point>();
            int currentX = goal.x;
            int currentY = goal.y;
            int currentDirection = goal.direction;
            while (currentX >= 0 && currentY >= 0) {
                points.add(new Point(minX + currentX * GRID, minY + currentY * GRID));
                int prior=previous[stateKey(currentX,currentY,currentDirection)];
                if(prior==-1) break;
                if(prior==0) throw new IllegalStateException("Missing routing predecessor");
                prior--;
                currentDirection=prior%5;
                currentX=(prior/5)%gridWidth;
                currentY=(prior/5)/gridWidth;
            }
            Collections.reverse(points);
            if(points.size()<2) throw new Rejected("ZERO_LENGTH_BRANCH");
            rawSegments+=points.size()-1;
            work.rawSegments+=points.size()-1;
            markCopper(points, netId);
            // Keep the searched path for independent comparison. The canonicalizer
            // removes only collinear subdivisions and retains physical witnesses.
            Vector<Point> routedPoints = points;
            int[] xPoints = new int[routedPoints.size()];
            int[] yPoints = new int[routedPoints.size()];
            for (int index = 0; index < routedPoints.size(); index++) {
                xPoints[index] = routedPoints.get(index).x;
                yPoints[index] = routedPoints.get(index).y;
            }
            return new PcbTraceGeometry(endPadId==null ? "tree/"+netId+"/branch/"+startPadId :
                PcbTraceGeometry.defaultSourceId(startPadId,endPadId,layer),netId,
                startPadId,endPadId,layer,PcbCopperAccess.Exposure.EXPOSED,xPoints,yPoints,
                activeTraceWidth);
        }

        private boolean canJoinTree(int x,int y,String netId) {
            prepareNet(netId);
            if(emptyComponentFace) return true;
            int px=minX+x*GRID,py=minY+y*GRID;
            for(int i=0;i<collisionCourtyards.length;i++)
                if(components[i].getMountingSide()==layer.getFace() &&
                        containsInclusive(collisionCourtyards[i],px,py) &&
                        !(factoryUnderpasses && components[i].permitsUnderpass(layer,
                            traceStroke(px,py,px,py,activeTraceWidth)))) return false;
            return true;
        }
        private int lowerBound(int distance,int minimumStepCost,int minimumTurns) {
            return admissibleLowerBound(distance,minimumStepCost,minimumTurns);
        }
        private int minimumStepCost(String netId) {
            boolean sameNetClearanceExists = false;
            for(int x=0;x<gridWidth;x++) for(int y=0;y<gridHeight;y++) {
                if(netId.equals(occupiedNet[x][y])) return MINIMUM_STEP_COST;
                if(netId.equals(clearanceNet[x][y])) sameNetClearanceExists = true;
            }
            return sameNetClearanceExists ? CLEARANCE_STEP_COST : FRESH_STEP_COST;
        }
        /** Manhattan lower bound to ANY connected trunk cell, multiplied by the true minimum step cost. */
        private int[] distanceField(boolean[] goals) {
            int[] distance=new int[goals.length],queue=new int[goals.length]; int head=0,tail=0;
            for(int i=0;i<distance.length;i++) { distance[i]=Integer.MAX_VALUE; if(goals[i]) { distance[i]=0;queue[tail++]=i; } }
            if(tail==0) throw new Rejected("ORPHAN_TREE");
            while(head<tail) {
                int key=queue[head++],x=key%gridWidth,y=key/gridWidth;
                if(x>0) { int next=key-1; if(distance[next]==Integer.MAX_VALUE) { distance[next]=distance[key]+1;queue[tail++]=next; } }
                if(x+1<gridWidth) { int next=key+1; if(distance[next]==Integer.MAX_VALUE) { distance[next]=distance[key]+1;queue[tail++]=next; } }
                if(y>0) { int next=key-gridWidth; if(distance[next]==Integer.MAX_VALUE) { distance[next]=distance[key]+1;queue[tail++]=next; } }
                if(y+1<gridHeight) { int next=key+gridWidth; if(distance[next]==Integer.MAX_VALUE) { distance[next]=distance[key]+1;queue[tail++]=next; } }
            }
            return distance;
        }

        /**
         * Exact obstacle-free lower bound on turns to any goal. Prefix counts
         * answer row, column, and forward-quadrant queries in constant time.
         */
        private boolean isLegalMove(int fromX, int fromY, int nextX, int nextY, int direction,
                int startX, int startY, int endX, int endY, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            int startDx=fromX-startX,startDy=fromY-startY;
            int startDistance=(startDx*startPad.getEscapeDx()+startDy*startPad.getEscapeDy())*GRID;
            if (startPad.getMountingSide()==layer.getFace() && startPad.getEscapeLength() > 0 && startDistance>=0 && startDistance<startPad.getEscapeLength() &&
                    startDx*startPad.getEscapeDy()==startDy*startPad.getEscapeDx() &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy()))
                return false;
            if (endPad!=null && endPad.getMountingSide()==layer.getFace() && endPad.getEscapeLength() > 0 &&
                    (nextX-endX)*endPad.getEscapeDy()==(nextY-endY)*endPad.getEscapeDx() &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID>=0 &&
                    ((nextX-endX)*endPad.getEscapeDx()+(nextY-endY)*endPad.getEscapeDy())*GRID<endPad.getEscapeLength() &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy()))
                return false;
            return true;
        }

        private boolean canOccupy(int x, int y, String netId, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            prepareNet(netId);
            int physicalX = minX + x * GRID;
            int physicalY = minY + y * GRID;
            if ((occupiedNet[x][y] != null && !netId.equals(occupiedNet[x][y])) ||
                    (clearanceNet[x][y] != null && !netId.equals(clearanceNet[x][y]))) {
                congestionRejections++; work.congestionRejections++;
                recordConflicts(x,y,netId);
                return false;
            }
            if(emptyComponentFace) return padAt[x][y]==null || padAt[x][y].equals(startPad.getPadId()) ||
                endPad!=null && padAt[x][y].equals(endPad.getPadId());
            if (isPadAtOtherNet(physicalX, physicalY, startPad.getPadId(),
                        endPad==null?null:endPad.getPadId()))
                return false;
            if ((physicalX == startPad.getX() && physicalY == startPad.getY()) ||
                    (endPad!=null && physicalX == endPad.getX() && physicalY == endPad.getY()))
                return true;
            int[] candidateIndexes = componentCandidatesByColumn[x];
            for (int candidate = 0; candidate < candidateIndexes.length; candidate++) {
                int index = candidateIndexes[candidate];
                PcbComponentPlacement component = components[index];
                if(component.getMountingSide()!=layer.getFace()) continue;
                if (!containsInclusive(courtyards[index], physicalX, physicalY))
                    continue;
                boolean startEscape = component.getComponentId().equals(
                    board.getPad(startPad.getPadId()).getComponentId()) &&
                    startPad.isInEscapeCorridor(physicalX, physicalY);
                boolean endEscape = endPad!=null && component.getComponentId().equals(
                    board.getPad(endPad.getPadId()).getComponentId()) &&
                    endPad.isInEscapeCorridor(physicalX, physicalY);
                if (!startEscape && !endEscape && !(factoryUnderpasses && component.permitsUnderpass(layer,
                        traceStroke(physicalX,physicalY,physicalX,physicalY,activeTraceWidth))))
                    return false;
            }
            return true;
        }

        private boolean canTraverse(int fromX, int fromY, int toX, int toY,
                PcbPadPlacement startPad, PcbPadPlacement endPad,String net) {
            prepareNet(net);
            Rectangle stroke = null;
            // The same corridor owner rejects both copper faces before route publication.
            if (domainRules!=null) {
                stroke = traceStroke(minX+fromX*GRID,minY+fromY*GRID,
                    minX+toX*GRID,minY+toY*GRID,activeTraceWidth);
                if (!domainRules.permits(net,stroke)) return false;
            }
            // Cache the exact existing rectangle/step intersection predicate on
            // the unpopulated face, avoiding a per-search-edge scan of every pad.
            boolean adjacentStep=Math.abs(fromX-toX)+Math.abs(fromY-toY)==1;
            if(emptyComponentFace && (adjacentStep || !checkProfiledPadTouches)) {
                String reserved=fromY==toY?horizontalReservation[Math.min(fromX,toX)][fromY]:verticalReservation[fromX][Math.min(fromY,toY)];
                return reserved==null || reserved.equals(net);
            }
            int startPhysicalX = minX + fromX * GRID;
            int startPhysicalY = minY + fromY * GRID;
            int endPhysicalX = minX + toX * GRID;
            int endPhysicalY = minY + toY * GRID;
            if (stroke == null)
                stroke = traceStroke(startPhysicalX,startPhysicalY,endPhysicalX,endPhysicalY,
                    activeTraceWidth);
            String startComponentId = board.getPad(startPad.getPadId()).getComponentId();
            String endComponentId = endPad==null?null:board.getPad(endPad.getPadId()).getComponentId();
            if(net!=null && adjacentStep) {
                String reserved=fromY==toY?horizontalReservation[Math.min(fromX,toX)][fromY]:verticalReservation[fromX][Math.min(fromY,toY)];
                // Stored board net IDs are nonempty; the empty string means mixed owners.
                if(reserved!=null && (reserved.length()==0 || !reserved.equals(net))) return false;
            } else {
                // Retain the exact scan for nonadjacent, diagonal or zero-length calls.
                Rectangle move = new Rectangle(Math.min(startPhysicalX,endPhysicalX),Math.min(startPhysicalY,endPhysicalY),
                    Math.max(1,Math.abs(startPhysicalX-endPhysicalX)),Math.max(1,Math.abs(startPhysicalY-endPhysicalY)));
                for(int index = 0; index < pads.length; index++) {
                    if(net.equals(padNets[index])) continue;
                    // Reserve every terminal's escape before routing any net. An early rail
                    // cannot occupy the only way out of a later signal's courtyard.
                    if(escapeReservations[index].intersects(move)) return false;
                    if(checkProfiledPadTouches && PcbConductorBuilder.touch(stroke,padBounds[index]))
                        return false;
                }
            }
            int[] candidateIndexes = adjacentStep ? componentCandidatesByColumn[fromX] :
                allComponentIndexes;
            for (int candidate = 0; candidate < candidateIndexes.length; candidate++) {
                int index = candidateIndexes[candidate];
                PcbComponentPlacement component = components[index];
                if(component.getMountingSide()!=layer.getFace()) continue;
                if (!courtyards[index].intersects(stroke))
                    continue;
                boolean startEscape = component.getComponentId().equals(startComponentId) &&
                    courtyardIntersectionIsEscape(collisionCourtyards[index], startPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                boolean endEscape = component.getComponentId().equals(endComponentId) &&
                    courtyardIntersectionIsEscape(collisionCourtyards[index], endPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                if (!startEscape && !endEscape && !(factoryUnderpasses && component.permitsUnderpass(layer, stroke)))
                    return false;
            }
            return true;
        }

        private boolean courtyardIntersectionIsEscape(Rectangle courtyard,
                PcbPadPlacement pad, int x1, int y1, int x2, int y2) {
            if (pad.getEscapeLength() > 0 &&
                    ((x1 == pad.getX() && y1 == pad.getY() &&
                        pad.isInEscapeCorridor(x2, y2)) ||
                    (x2 == pad.getX() && y2 == pad.getY() &&
                        pad.isInEscapeCorridor(x1, y1))))
                return true;
            if (y1 == y2) {
                int left = Math.max(Math.min(x1, x2), courtyard.x);
                int right = Math.min(Math.max(x1, x2), courtyard.x + courtyard.width);
                return left <= right && pad.isInEscapeCorridor(left, y1) &&
                    pad.isInEscapeCorridor(right, y1);
            }
            if (x1 == x2) {
                int top = Math.max(Math.min(y1, y2), courtyard.y);
                int bottom = Math.min(Math.max(y1, y2), courtyard.y + courtyard.height);
                return top <= bottom && pad.isInEscapeCorridor(x1, top) &&
                    pad.isInEscapeCorridor(x1, bottom);
            }
            return false;
        }

        /** Attribute rejected cells to actual copper owners, including shared clearance halos. */
        private void recordConflicts(int x,int y,String net) {
            int radius=PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS;
            for(int nx=Math.max(0,x-radius);nx<=Math.min(gridWidth-1,x+radius);nx++)
                for(int ny=Math.max(0,y-radius);ny<=Math.min(gridHeight-1,y+radius);ny++) {
                    String owner=occupiedNet[nx][ny];
                    if(owner==null || owner.equals(net)) continue;
                    Integer count=conflicts.get(owner);
                    conflicts.put(owner,count==null?1:count+1);
                }
        }
        Vector<String> victims(java.util.Set<String> completed,int limit) {
            Vector<String> result=new Vector<String>();
            for(String owner:conflicts.keySet()) if(completed.contains(owner)) result.add(owner);
            Collections.sort(result,new Comparator<String>() { public int compare(String a,String b) {
                int ac=conflicts.get(a),bc=conflicts.get(b);
                return ac==bc?a.compareTo(b):ac>bc?-1:1;
            }});
            while(result.size()>limit) result.remove(result.size()-1);
            return result;
        }
        /** Traces own copper. Rebuild derived occupancy rather than subtract shared halo cells. */
        void removeNets(Vector<String> owners) {
            if(owners==null) throw new IllegalArgumentException("Missing rip-up owners");
            for(String owner:owners) if(board.getNet(owner)==null)
                throw new IllegalArgumentException("Unknown rip-up owner: "+owner);
            Vector<PcbTraceGeometry> survivors=new Vector<PcbTraceGeometry>();
            boolean changed=false;
            for(PcbTraceGeometry trace:layout.getTraces()) {
                if(owners.contains(trace.getNetId())) changed=true;
                else survivors.add(trace);
            }
            if(!changed) return;
            layout.replaceTraces(survivors);
            for(int x=0;x<gridWidth;x++) {
                java.util.Arrays.fill(occupiedNet[x],null);
                java.util.Arrays.fill(clearanceNet[x],null);
            }
            for(PcbTraceGeometry trace:survivors) markCopper(gridPoints(trace),trace.getNetId());
        }
        private Vector<Point> gridPoints(PcbTraceGeometry trace) {
            if(trace.getLayer()!=layer || board.getNet(trace.getNetId())==null)
                throw new IllegalStateException("Foreign copper in routing state");
            Vector<Point> result=new Vector<Point>();
            int[] xs=trace.getXPoints(),ys=trace.getYPoints();
            for(int i=1;i<xs.length;i++) {
                boolean horizontal=ys[i]==ys[i-1];
                if(horizontal?xs[i]==xs[i-1]:xs[i]!=xs[i-1])
                    throw new IllegalStateException("Routing copper has a zero/diagonal segment");
                int fixed=horizontal?gridY(ys[i]):gridX(xs[i]);
                int low=horizontal?Math.min(xs[i-1],xs[i]):Math.min(ys[i-1],ys[i]);
                int high=horizontal?Math.max(xs[i-1],xs[i]):Math.max(ys[i-1],ys[i]);
                int origin=horizontal?minX:minY,count=horizontal?gridWidth:gridHeight;
                if(fixed<0 || fixed>=(horizontal?gridHeight:gridWidth) || low<origin || high>origin+(count-1)*GRID)
                    throw new IllegalStateException("Routing copper is outside its grid");
                // Canonicalization inserts exact escape/contact witnesses along
                // existing segments; collinear witness coordinates need not be grid vertices.
                for(int coordinate=origin+((low-origin+GRID-1)/GRID)*GRID;coordinate<=high;coordinate+=GRID)
                    result.add(horizontal?new Point(coordinate,ys[i]):new Point(xs[i],coordinate));
            }
            return result;
        }
        /** Compare, never repair: stale or missing cells fail before publication. */
        void requireExactOccupancy() {
            String[][] expected=new String[gridWidth][gridHeight];
            for(PcbTraceGeometry trace:layout.getTraces()) for(Point point:gridPoints(trace)) {
                int x=gridX(point.x),y=gridY(point.y);
                String previous=expected[x][y];
                if(previous!=null && !previous.equals(trace.getNetId()))
                    throw new IllegalStateException("Two copper owners occupy one routing cell");
                expected[x][y]=trace.getNetId();
            }
            int radius=PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS;
            for(int x=0;x<gridWidth;x++) for(int y=0;y<gridHeight;y++) {
                if(!equal(expected[x][y],occupiedNet[x][y]))
                    throw new IllegalStateException("Stale/missing routed occupancy");
                String halo=null;
                for(int nx=Math.max(0,x-radius);nx<=Math.min(gridWidth-1,x+radius);nx++)
                    for(int ny=Math.max(0,y-radius);ny<=Math.min(gridHeight-1,y+radius);ny++) {
                        String owner=expected[nx][ny];
                        if(owner!=null) halo=halo==null || halo.equals(owner)?owner:"";
                    }
                if(!equal(halo,clearanceNet[x][y]))
                    throw new IllegalStateException("Stale/missing routing clearance");
                if(expected[x][y]!=null && !expected[x][y].equals(halo))
                    throw new IllegalStateException("Routed copper violates another owner's clearance");
            }
        }
        private boolean equal(String a,String b) { return a==null?b==null:a.equals(b); }

        private void markCopper(Vector<Point> points, String netId) {
            for (Point point : points) {
                int centerX = gridX(point.x);
                int centerY = gridY(point.y);
                if((occupiedNet[centerX][centerY]!=null && !netId.equals(occupiedNet[centerX][centerY])) ||
                        (clearanceNet[centerX][centerY]!=null && !netId.equals(clearanceNet[centerX][centerY])))
                    throw new IllegalStateException("Routing attempted cross-owner copper publication");
                occupiedNet[centerX][centerY] = netId;
                for (int x = Math.max(0, centerX - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                        x <= Math.min(gridWidth - 1,
                            centerX + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); x++) {
                    for (int y = Math.max(0, centerY - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                            y <= Math.min(gridHeight - 1,
                                centerY + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); y++)
                        reserve(clearanceNet,x,y,netId);
                }
            }
        }

        private boolean isPadAtOtherNet(int x, int y, String startPadId, String endPadId) {
            for (PcbPadPlacement pad : pads) {
                if (pad.getX() != x || pad.getY() != y)
                    continue;
                if (pad.getPadId().equals(startPadId) || pad.getPadId().equals(endPadId))
                    continue;
                return true;
            }
            return false;
        }

        private int directionX(int direction) {
            return direction == 1 ? 1 : direction == 3 ? -1 : 0;
        }

        private int directionY(int direction) {
            return direction == 0 ? -1 : direction == 2 ? 1 : 0;
        }

        private int manhattan(int x, int y, int otherX, int otherY) {
            return Math.abs(x - otherX) + Math.abs(y - otherY);
        }

        /** Shared exact per-face predicates for the developer-only layered search. */
        boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,
                PcbPadPlacement start,PcbPadPlacement end) {
            return permitsLayerStep(ax,ay,bx,by,net,start,end,null);
        }
        /** P07 may terminate only at these already-published plated pads, never net peers. */
        boolean permitsLayerStep(int ax,int ay,int bx,int by,String net,
                PcbPadPlacement start,PcbPadPlacement end,PcbPadPlacement[] reachedTerminals) {
            int x=gridX(ax),y=gridY(ay),nx=gridX(bx),ny=gridY(by);
            if(x<0 || y<0 || nx<0 || ny<0 || x>=gridWidth || nx>=gridWidth ||
                    y>=gridHeight || ny>=gridHeight) return false;
            int direction=nx>x?1:nx<x?3:ny>y?2:0;
            if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,end)) return true;
            if(reachedTerminals==null) return false;
            for(PcbPadPlacement reached:reachedTerminals) {
                if(reached==null) throw new IllegalArgumentException("Missing reached P07 terminal");
                if(reached.getPadId().equals(start.getPadId()) ||
                        end!=null && reached.getPadId().equals(end.getPadId())) continue;
                if(permitsLayerStepToEndpoint(x,y,nx,ny,direction,net,start,reached)) return true;
            }
            return false;
        }
        private boolean permitsLayerStepToEndpoint(int x,int y,int nx,int ny,int direction,
                String net,PcbPadPlacement start,PcbPadPlacement end) {
            return isLegalMove(x,y,nx,ny,direction,gridX(start.getX()),gridY(start.getY()),
                end==null?-1:gridX(end.getX()),end==null?-1:gridY(end.getY()),start,end) &&
                canTraverse(x,y,nx,ny,start,end,net) && canOccupy(nx,ny,net,start,end);
        }
        // The layered router shares this exact outline, origin and grid size.
        boolean ownsLayerGridCell(int x,int y,String net) {
            return x>=0 && y>=0 && x<gridWidth && y<gridHeight &&
                net.equals(occupiedNet[x][y]) && canJoinTree(x,y,net);
        }
        boolean freeLayerCell(int px,int py,String net) {
            prepareNet(net);
            int x=gridX(px),y=gridY(py);
            return x>=0 && y>=0 && x<gridWidth && y<gridHeight &&
                (occupiedNet[x][y]==null || net.equals(occupiedNet[x][y])) &&
                (clearanceNet[x][y]==null || net.equals(clearanceNet[x][y]));
        }
        void occupyLayerPath(Vector<Point> path,String net) { markCopper(path,net); }
        /** Native fixed-graph observation copy; mutable counters/conflicts/work are deliberately fresh. */
        Router copyForFixedGraphObservation() {
            observationCheckpoint(observer,attempt);
            Router copy=new Router(layout,board,new Rectangle(outline),attempt,observer,layer,0);
            observationCheckpoint(observer,attempt);
            PcbRoutingWork sourceWork=work;
            if(sourceWork==null) copy.work=null;
            else {
                int requestCount=sourceWork.netAttemptLimit-sourceWork.limits.reroutedNets;
                if(requestCount<0 || requestCount%sourceWork.limits.orderings!=0)
                    throw new IllegalStateException("P07 fixed observation work limits are inconsistent");
                copy.work=new PcbRoutingWork(sourceWork.limits,
                    requestCount/sourceWork.limits.orderings);
            }
            copy.factoryUnderpasses=factoryUnderpasses;
            copy.emptyComponentFace=emptyComponentFace;
            copy.activeTraceWidth=activeTraceWidth;
            copy.activeNetId=activeNetId;
            copyStringGridInto(occupiedNet,copy.occupiedNet,observer,attempt);
            copyStringGridInto(clearanceNet,copy.clearanceNet,observer,attempt);
            copy.horizontalReservation=copyStringGrid(horizontalReservation,observer,attempt);
            copy.verticalReservation=copyStringGrid(verticalReservation,observer,attempt);
            copy.padAt=copyStringGrid(padAt,observer,attempt);
            copyStringArrayInto(padNets,copy.padNets,observer,attempt);
            copyIntArrayInto(padWidths,copy.padWidths,observer,attempt);
            copyRectanglesInto(padBounds,copy.padBounds,observer,attempt);
            copyRectanglesInto(escapeCorridors,copy.escapeCorridors,observer,attempt);
            copyRectanglesInto(escapeReservations,copy.escapeReservations,observer,attempt);
            copyRectanglesInto(courtyards,copy.courtyards,observer,attempt);
            copy.collisionCourtyards=copyRectangles(collisionCourtyards,observer,attempt);
            copyIntArrayInto(allComponentIndexes,copy.allComponentIndexes,observer,attempt);
            copyIntGridInto(componentCandidatesByColumn,copy.componentCandidatesByColumn,observer,attempt);
            copyReferences(pads,copy.pads,observer,attempt);
            copyReferences(components,copy.components,observer,attempt);
            observationCheckpoint(observer,attempt);
            return copy;
        }

        /** Exact elementwise equality for fixed route-predicate state; query effects are excluded. */
        boolean sameFixedGraphObservation(Router other) {
            if(other==null || board!=other.board || layout!=other.layout || attempt!=other.attempt ||
                    layer!=other.layer || checkProfiledPadTouches!=other.checkProfiledPadTouches ||
                    !sameRectangle(outline,other.outline) || minX!=other.minX || minY!=other.minY ||
                    gridWidth!=other.gridWidth || gridHeight!=other.gridHeight ||
                    emptyComponentFace!=other.emptyComponentFace ||
                    factoryUnderpasses!=other.factoryUnderpasses || activeTraceWidth!=other.activeTraceWidth ||
                    !sameString(activeNetId,other.activeNetId) || (domainRules==null)!=(other.domainRules==null) ||
                    !sameWorkConfiguration(work,other.work))
                return false;
            return sameStringGrid(occupiedNet,other.occupiedNet) &&
                sameStringGrid(clearanceNet,other.clearanceNet) &&
                sameStringGrid(horizontalReservation,other.horizontalReservation) &&
                sameStringGrid(verticalReservation,other.verticalReservation) &&
                sameStringGrid(padAt,other.padAt) &&
                sameStrings(padNets,other.padNets) && sameInts(padWidths,other.padWidths) &&
                sameRectangles(padBounds,other.padBounds) &&
                sameRectangles(escapeCorridors,other.escapeCorridors) &&
                sameRectangles(escapeReservations,other.escapeReservations) &&
                sameReferences(pads,other.pads) && sameReferences(components,other.components) &&
                sameRectangles(courtyards,other.courtyards) &&
                sameRectangles(collisionCourtyards,other.collisionCourtyards) &&
                sameInts(allComponentIndexes,other.allComponentIndexes) &&
                sameIntGrid(componentCandidatesByColumn,other.componentCandidatesByColumn);
        }

        /** Pure, deterministic side-effect stamp for before/after live-state checks. */
        String fixedGraphObservationEffects() {
            StringBuilder stamp=new StringBuilder("fixed-graph-router-effects@1");
            stamp.append(";expansions=").append(expansions);
            stamp.append(";rawSegments=").append(rawSegments);
            stamp.append(";congestionRejections=").append(congestionRejections);
            stamp.append(";conflictCount=").append(conflicts.size());
            for(java.util.Map.Entry<String,Integer> entry:conflicts.entrySet())
                stamp.append(";conflict=").append(entry.getKey().length()).append(':')
                    .append(entry.getKey()).append('=').append(entry.getValue());
            String workStamp=work==null?null:work.snapshot().toCanonical();
            stamp.append(";workLength=").append(workStamp==null?-1:workStamp.length());
            stamp.append(";work=");
            if(workStamp==null) stamp.append("null");
            else stamp.append(workStamp);
            return stamp.toString();
        }

        private static final int OBSERVATION_COPY_CHUNK=4096;
        private static void observationCheckpoint(SeededPcbLayoutGenerator.AttemptObserver observer,
                int attempt) {
            if(observer!=null) observer.check(attempt);
        }
        private static void copyStringChunk(String[] source,int sourceStart,String[] target,int targetStart,
                int length,SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            int offset=0;
            observationCheckpoint(observer,attempt);
            while(offset<length) {
                int count=Math.min(OBSERVATION_COPY_CHUNK,length-offset);
                System.arraycopy(source,sourceStart+offset,target,targetStart+offset,count);
                offset+=count;
                observationCheckpoint(observer,attempt);
            }
        }
        private static String[][] copyStringGrid(String[][] source,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null) return null;
            String[][] result=new String[source.length][];
            for(int x=0;x<source.length;x++) {
                observationCheckpoint(observer,attempt);
                if(source[x]==null) result[x]=null;
                else {
                    result[x]=new String[source[x].length];
                    copyStringChunk(source[x],0,result[x],0,source[x].length,observer,attempt);
                }
            }
            observationCheckpoint(observer,attempt);
            return result;
        }
        private static void copyStringGridInto(String[][] source,String[][] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation grid dimensions differ");
            for(int x=0;x<source.length;x++) {
                observationCheckpoint(observer,attempt);
                if(source[x]==null) { target[x]=null; continue; }
                if(target[x]==null || source[x].length!=target[x].length)
                    target[x]=new String[source[x].length];
                copyStringChunk(source[x],0,target[x],0,source[x].length,observer,attempt);
            }
            observationCheckpoint(observer,attempt);
        }
        private static void copyStringArrayInto(String[] source,String[] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation string arrays differ");
            copyStringChunk(source,0,target,0,source.length,observer,attempt);
        }
        private static void copyIntArrayInto(int[] source,int[] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation integer arrays differ");
            copyIntChunk(source,0,target,0,source.length,observer,attempt);
        }
        private static void copyIntChunk(int[] source,int sourceStart,int[] target,int targetStart,
                int length,SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            int offset=0;
            observationCheckpoint(observer,attempt);
            while(offset<length) {
                int count=Math.min(OBSERVATION_COPY_CHUNK,length-offset);
                System.arraycopy(source,sourceStart+offset,target,targetStart+offset,count);
                offset+=count;
                observationCheckpoint(observer,attempt);
            }
        }
        private static void copyIntGridInto(int[][] source,int[][] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation index grids differ");
            for(int x=0;x<source.length;x++) {
                observationCheckpoint(observer,attempt);
                if(source[x]==null) { target[x]=null; continue; }
                if(target[x]==null || source[x].length!=target[x].length)
                    target[x]=new int[source[x].length];
                copyIntChunk(source[x],0,target[x],0,source[x].length,observer,attempt);
            }
            observationCheckpoint(observer,attempt);
        }
        private static void copyRectanglesInto(Rectangle[] source,Rectangle[] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation rectangle arrays differ");
            for(int i=0;i<source.length;i++) {
                if((i%OBSERVATION_COPY_CHUNK)==0) observationCheckpoint(observer,attempt);
                target[i]=copyRectangle(source[i]);
            }
            observationCheckpoint(observer,attempt);
        }
        private static Rectangle[] copyRectangles(Rectangle[] source,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null) return null;
            Rectangle[] result=new Rectangle[source.length];
            for(int i=0;i<source.length;i++) {
                if((i%OBSERVATION_COPY_CHUNK)==0) observationCheckpoint(observer,attempt);
                result[i]=copyRectangle(source[i]);
            }
            observationCheckpoint(observer,attempt);
            return result;
        }
        private static void copyReferences(Object[] source,Object[] target,
                SeededPcbLayoutGenerator.AttemptObserver observer,int attempt) {
            observationCheckpoint(observer,attempt);
            if(source==null || target==null || source.length!=target.length)
                throw new IllegalStateException("P07 fixed observation reference arrays differ");
            for(int start=0;start<source.length;start+=OBSERVATION_COPY_CHUNK) {
                observationCheckpoint(observer,attempt);
                int count=Math.min(OBSERVATION_COPY_CHUNK,source.length-start);
                System.arraycopy(source,start,target,start,count);
            }
            observationCheckpoint(observer,attempt);
        }
        private static Rectangle copyRectangle(Rectangle value) {
            return value==null?null:new Rectangle(value);
        }
        private static boolean sameString(String a,String b) {
            return a==null?b==null:a.equals(b);
        }
        private static boolean sameWorkConfiguration(PcbRoutingWork a,PcbRoutingWork b) {
            if(a==null || b==null) return a==b;
            PcbRoutingWork.Limits x=a.limits,y=b.limits;
            return x.orderings==y.orderings && x.ripUpPasses==y.ripUpPasses &&
                x.victimsPerPass==y.victimsPerPass && x.reroutedNets==y.reroutedNets &&
                x.expansions==y.expansions && a.netAttemptLimit==b.netAttemptLimit;
        }
        private static boolean sameStrings(String[] a,String[] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(!sameString(a[i],b[i])) return false;
            return true;
        }
        private static boolean sameInts(int[] a,int[] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(a[i]!=b[i]) return false;
            return true;
        }
        private static boolean sameIntGrid(int[][] a,int[][] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(!sameInts(a[i],b[i])) return false;
            return true;
        }
        private static boolean sameStringGrid(String[][] a,String[][] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(!sameStrings(a[i],b[i])) return false;
            return true;
        }
        private static boolean sameRectangle(Rectangle a,Rectangle b) {
            return a==null || b==null?a==b:
                a.x==b.x && a.y==b.y && a.width==b.width && a.height==b.height;
        }
        private static boolean sameRectangles(Rectangle[] a,Rectangle[] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(!sameRectangle(a[i],b[i])) return false;
            return true;
        }
        private static boolean sameReferences(Object[] a,Object[] b) {
            if(a==null || b==null) return a==b;
            if(a.length!=b.length) return false;
            for(int i=0;i<a.length;i++) if(a[i]!=b[i]) return false;
            return true;
        }

        private int gridX(int x) {
            return (x - minX) % GRID == 0 ? (x - minX) / GRID : -1;
        }
        private int stateKey(int x,int y,int direction) { return (y*gridWidth+x)*5+direction; }
        private int gridY(int y) {
            return (y - minY) % GRID == 0 ? (y - minY) / GRID : -1;
        }

        private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
            return x >= rectangle.x && y >= rectangle.y &&
                x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
        }

        private static Rectangle traceStroke(int firstX,int firstY,int secondX,int secondY,int width) {
            int half = width / 2;
            if (firstX == secondX)
                return new Rectangle(firstX - half, Math.min(firstY, secondY) - half,
                    width,Math.abs(secondY-firstY)+width);
            if (firstY == secondY)
                return new Rectangle(Math.min(firstX, secondX) - half, firstY - half,
                    Math.abs(secondX-firstX)+width,width);
            throw new IllegalStateException("PCB router encountered a non-Manhattan move");
        }

        /** Typed heap avoids GWT's generic queue casts without changing A* ordering. */
        private static final class SearchQueue {
            private SearchNode[] values = new SearchNode[128];
            private int size;
            boolean isEmpty() { return size == 0; }
            void add(SearchNode value) {
                if (value == null) throw new IllegalArgumentException("Missing search node");
                if (size == values.length) {
                    SearchNode[] larger = new SearchNode[values.length * 2];
                    System.arraycopy(values, 0, larger, 0, size);
                    values = larger;
                }
                int at = size++;
                while (at > 0) {
                    int parent = (at - 1) >>> 1;
                    SearchNode above = values[parent];
                    if (value.compareTo(above) >= 0) break;
                    values[at] = above; at = parent;
                }
                values[at] = value;
            }
            SearchNode poll() {
                if (size == 0) return null;
                SearchNode result = values[0], value = values[--size];
                values[size] = null;
                if (size > 0) {
                    int at = 0, half = size >>> 1;
                    while (at < half) {
                        int child = (at << 1) + 1, right = child + 1;
                        if (right < size && values[right].compareTo(values[child]) < 0) child = right;
                        if (value.compareTo(values[child]) <= 0) break;
                        values[at] = values[child]; at = child;
                    }
                    values[at] = value;
                }
                return result;
            }
        }

        private static class SearchNode {
            private final int x;
            private final int y;
            private final int direction;
            private final double cost;
            private final double heuristic;
            private final int sequence;

            SearchNode(int x, int y, int direction, double cost, double heuristic,
                    int sequence) {
                this.x = x;
                this.y = y;
                this.direction = direction;
                this.cost = cost;
                this.heuristic = heuristic;
                this.sequence = sequence;
            }

            int compareTo(SearchNode other) {
                double total = cost + heuristic;
                double otherTotal = other.cost + other.heuristic;
                if (total < otherTotal)
                    return -1;
                if (total > otherTotal)
                    return 1;
                if (heuristic < other.heuristic)
                    return -1;
                if (heuristic > other.heuristic)
                    return 1;
                if (y != other.y)
                    return y - other.y;
                if (x != other.x)
                    return x - other.x;
                if (direction != other.direction)
                    return direction - other.direction;
                return sequence - other.sequence;
            }
        }
    }
}
