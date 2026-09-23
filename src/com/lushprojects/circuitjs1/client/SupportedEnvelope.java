package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;

/** Physical admission limits, NOT a substitute for geometry or live CircuitJS proof. */
final class SupportedEnvelope {
    static final String ID="THT_SINGLE_FACE";
    static final int VERSION=1;
    private static final SupportedEnvelope CURRENT=new SupportedEnvelope();
    static final int MIN_EDGE=128, MAX_EDGE=2048, MAX_ASPECT=3;
    static final long MAX_AREA=2250000L, MAX_COPPER_LENGTH=16000L;
    static final int MAX_PARTS=16, MAX_PADS=40, MAX_NETS=16, MAX_NET_DEGREE=10;
    static final int MAX_SEGMENTS=160, MIN_PAD=26, MIN_PROBE=30;
    static final double MAX_ROUTE_SCORE=70000;
    static final int MAX_PLACEMENTS=80, MAX_ROUTE_ATTEMPTS=800, MAX_EXPANSIONS=160000000;
    enum Reason { UNKNOWN_ENVELOPE, MISSING_LAYOUT, BOARD_SIZE, POPULATION, PACKAGE_MIX,
        POSE, DOMAINS, NET_DEGREE, LAYER_POLICY, HOLE_POLICY, LINK_POLICY,
        ACCESS_FLOOR, ROUTE_BUDGET, WORK_BUDGET, DEVELOPER_ONLY }
    static final class Rejected extends GenerationJob.Rejected {
        final Reason reason;
        Rejected(Reason reason,String detail) {
            super("Unsupported physical envelope "+ID+"@"+VERSION+" ["+reason+"]: "+detail);
            this.reason=reason;
        }
    }
    private SupportedEnvelope() { }
    static SupportedEnvelope current() { return CURRENT; }
    static SupportedEnvelope resolve(String id,int version) {
        if(!ID.equals(id) || version!=VERSION) reject(Reason.UNKNOWN_ENVELOPE,"unknown id/version");
        return CURRENT;
    }
    String identity() { return ID+"@"+VERSION; }
    void requireNormal(GeneratedBoardInstance owner) {
        if(owner==null) reject(Reason.MISSING_LAYOUT,"missing generated owner");
        if(owner.isDeveloperOnlyFaultRoute()) reject(Reason.DEVELOPER_ONLY,"prototype is not normal admission");
        requireBounds(owner.getBoard(),owner.getPcbLayout());
    }
    void requireBoardSize(Rectangle r) {
        if(r==null || r.width<MIN_EDGE || r.height<MIN_EDGE || r.width>MAX_EDGE || r.height>MAX_EDGE ||
                (long)r.width*r.height>MAX_AREA || (long)Math.max(r.width,r.height)>MAX_ASPECT*(long)Math.min(r.width,r.height))
            reject(Reason.BOARD_SIZE,"outline size, area or aspect exceeds the qualified bound");
    }
    void requireAccess(PcbPadPlacement pad) {
        if(pad==null || pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                pad.getMountingSide()!=PcbBoardSide.TOP || !PcbCopperAccess.canProbe(pad,PcbBoardSide.BOTTOM))
            reject(Reason.ACCESS_FLOOR,"every terminal needs an exposed through-hole solder target");
        Rectangle land=pad.getPadBounds(), probe=pad.getProbeBounds();
        if(land.width<MIN_PAD || land.height<MIN_PAD || probe.width<MIN_PROBE || probe.height<MIN_PROBE)
            reject(Reason.ACCESS_FLOOR,"pad/probe target is below the physical access floor");
    }
    /** Necessary bounds only. Callers MUST still validate geometry, correspondence and diagnostics. */
    void requireBounds(TroubleshootBoard board,PcbBoardLayout layout) {
        if(board==null || layout==null) reject(Reason.MISSING_LAYOUT,"no board/layout");
        requireBoardSize(layout.getBoardOutline());
        int parts=board.getComponentIds().size(), pads=board.getPadIds().size(), nets=board.getNetIds().size();
        if(parts<1 || parts>MAX_PARTS || pads<1 || pads>MAX_PADS || nets<1 || nets>MAX_NETS ||
                layout.getComponents().size()!=parts || layout.getPads().size()!=pads)
            reject(Reason.POPULATION,"component, pad or net inventory exceeds the declared bounds");
        int[] counts=new int[PACKAGES.length];
        for(PcbComponentPlacement part:layout.getComponents()) {
            if(part.getPhysicalGeometry().getRaisedCrossover()!=null)
                reject(Reason.LINK_POLICY,"zero factory links are qualified for production");
            int index=packageIndex(part.getPhysicalPackage());
            if(index<0 || ++counts[index]>PACKAGE_CAPS[index])
                reject(Reason.PACKAGE_MIX,"unqualified package or package multiplicity");
            if(part.getMountingSide()!=PcbBoardSide.TOP || part.getRotation()!=PcbRotation.DEG_0)
                reject(Reason.POSE,"only the qualified top-mounted catalog variants are admitted");
        }
        PcbPlacementConstraints demands=board.getPlacementConstraints();
        if(demands.routingLayer==null) reject(Reason.LAYER_POLICY,"one declared copper face is required");
        TreeSet<String> domains=new TreeSet<String>();
        for(PcbPlacementConstraints.Part demand:demands.getParts()) domains.add(demand.domainId);
        if(domains.size()!=1 || !demands.getBarriers().isEmpty())
            reject(Reason.DOMAINS,"isolated placement domains are not yet qualified");
        for(String id:board.getNetIds()) if(board.getNet(id).getPadIds().size()>MAX_NET_DEGREE)
            reject(Reason.NET_DEGREE,"net terminal degree exceeds the bound");
        for(PcbPadPlacement pad:layout.getPads()) requireAccess(pad);
        if(!layout.getHoles().isEmpty()) reject(Reason.HOLE_POLICY,"no vias or extra drills are qualified");
        requireRoutes(board,layout);
        requireWork(layout);
    }
    private void requireRoutes(TroubleshootBoard board,PcbBoardLayout layout) {
        if(PcbTraceRules.TRACE_WIDTH<9 || PcbTraceRules.MIN_VISIBLE_CLEARANCE<6 ||
                PcbTraceRules.MIN_CENTERLINE_CLEARANCE<PcbTraceRules.TRACE_WIDTH+6)
            reject(Reason.ACCESS_FLOOR,"display/clearance floor cannot be disabled by a profile");
        long segments=0;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            if(trace.getLayer()!=board.getPlacementConstraints().routingLayer)
                reject(Reason.LAYER_POLICY,"copper differs from the one declared routing face");
            if(trace.getExposure()!=PcbCopperAccess.Exposure.EXPOSED)
                reject(Reason.ACCESS_FLOOR,"covered trace surfaces are not qualified for normal inspection");
            segments+=trace.getXPoints().length-1;
            if(segments>MAX_SEGMENTS) reject(Reason.ROUTE_BUDGET,"too many routed segments");
        }
        PcbRouteMetrics metrics=PcbRouteMetrics.measure(layout.getTraces());
        if(metrics.uniqueLength>MAX_COPPER_LENGTH || !(layout.getRouteQualityScore(board)<=MAX_ROUTE_SCORE))
            reject(Reason.ROUTE_BUDGET,"copper length or route quality cost exceeds the bound");
    }
    private void requireWork(PcbBoardLayout layout) {
        if(layout.getGenerationPlacementAttempts()<0 || layout.getGenerationPlacementAttempts()>MAX_PLACEMENTS ||
                layout.getGenerationRoutingAttempts()<0 || layout.getGenerationRoutingAttempts()>MAX_ROUTE_ATTEMPTS ||
                layout.getGenerationRoutingExpansions()<0 || layout.getGenerationRoutingExpansions()>MAX_EXPANSIONS)
            reject(Reason.WORK_BUDGET,"generation work exceeds existing deterministic limits");
        PcbRoutingWork.Statistics work=layout.getRoutingRecoveryStatistics();
        if(work!=null && (work.outcome!=PcbRoutingWork.Outcome.SUCCESS || work.expansions<0 ||
                work.expansions>PcbRoutingWork.Limits.MAX_EXPANSIONS || work.orderingPasses>PcbRoutingWork.Limits.MAX_ORDERINGS ||
                work.ripUpPasses>PcbRoutingWork.Limits.MAX_RIP_PASSES || work.reroutedNets>PcbRoutingWork.Limits.MAX_REROUTED_NETS))
            reject(Reason.WORK_BUDGET,"route recovery is incomplete or exceeds its bounded work");
    }
    private static void reject(Reason reason,String detail) { throw new Rejected(reason,detail); }
    private static final PhysicalPackage[] PACKAGES={PhysicalPackages.AXIAL_RESISTOR,
        PhysicalPackages.AXIAL_FUSE,PhysicalPackages.AXIAL_DIODE,PhysicalPackages.THROUGH_HOLE_LED,
        PhysicalPackages.TO92_NPN,PhysicalPackages.TO92_NMOS,PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
        PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
        PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,PhysicalPackages.RELAY_SPDT,
        PhysicalPackages.TO220_REGULATOR_4,PhysicalPackages.E04_DECISION_CONTROL_5};
    private static final int[] PACKAGE_CAPS={8,1,2,3,2,2,1,2,4,1,1,1,1};
    private static int packageIndex(PhysicalPackage physical) {
        for(int i=0;i<PACKAGES.length;i++) if(PACKAGES[i]==physical) return i;
        return -1; // A forged equal ID does not qualify an untested footprint.
    }
    String canonical() {
        StringBuilder out=new StringBuilder(identity());
        out.append(";edge=").append(MIN_EDGE).append("..").append(MAX_EDGE).append(";aspect=").append(MAX_ASPECT)
            .append(";area=").append(MAX_AREA).append(";parts=").append(MAX_PARTS).append(";pads=").append(MAX_PADS)
            .append(";nets=").append(MAX_NETS).append(";degree=").append(MAX_NET_DEGREE)
            .append(";domains=1;barriers=0;mount=TOP;rotation=DEG_0;attachment=PLATED_THROUGH_HOLE;layer=single-declared-TOP-or-BOTTOM;vias=0;links=0;extraDrills=0")
            .append(";padFloor=").append(MIN_PAD).append(";probeFloor=").append(MIN_PROBE)
            .append(";traceExposure=EXPOSED;traceFloor=9;clearanceFloor=6;traceActual=").append(PcbTraceRules.TRACE_WIDTH)
            .append(";clearanceActual=").append(PcbTraceRules.MIN_VISIBLE_CLEARANCE)
            .append(";segments=").append(MAX_SEGMENTS).append(";uniqueCopper=").append(MAX_COPPER_LENGTH)
            .append(";score=").append(PcbRouteMetrics.SCORE_VERSION).append(':').append(MAX_ROUTE_SCORE)
            .append(";placements=").append(MAX_PLACEMENTS).append(";routeAttempts=").append(MAX_ROUTE_ATTEMPTS)
            .append(";expansions=").append(MAX_EXPANSIONS);
        for(int i=0;i<PACKAGES.length;i++) out.append(';').append(PACKAGES[i].getId()).append('=').append(PACKAGE_CAPS[i]);
        out.append(";gridCells=").append(PcbRoutingWork.MAX_GRID_CELLS)
            .append(";searchPerCell=").append(PcbRoutingWork.SEARCH_EXPANSIONS_PER_CELL)
            .append(";routeOrders=").append(PcbRoutingWork.Limits.MAX_ORDERINGS)
            .append(";ripPasses=").append(PcbRoutingWork.Limits.MAX_RIP_PASSES)
            .append(";victims=").append(PcbRoutingWork.Limits.MAX_VICTIMS)
            .append(";reroutes=").append(PcbRoutingWork.Limits.MAX_REROUTED_NETS)
            .append(";requestExpansions=").append(PcbRoutingWork.Limits.MAX_EXPANSIONS)
            .append(";jobMillis=").append(GenerationCoordinator.MAX_JOB_MILLIS)
            .append(";unitMillis=").append(GenerationCoordinator.MAX_STEP_MILLIS)
            .append(";jobUnits=").append(GenerationCoordinator.MAX_JOB_STEPS)
            .append(";view=U01-side-aware-pan-zoom;hiddenCopperSelectable=false;inspectionZoomRequired=true")
            .append(";qualification=geometry+live-correspondence+diagnostic-proof;proceduralPopulation=NOT_QUALIFIED");
        return out.toString();
    }
}
