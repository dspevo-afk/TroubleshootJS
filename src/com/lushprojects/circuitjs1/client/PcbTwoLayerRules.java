package com.lushprojects.circuitjs1.client;

import java.util.TreeMap;
import java.util.Vector;

/** Independent P07 checks. Drawing units, not manufacturing safety ratings. */
final class PcbTwoLayerRules {
    static final int VIA_DRILL = 2, VIA_LAND = 6;
    private final TroubleshootBoard board;
    private final TreeMap<String,java.util.TreeSet<String>> netDomains=new TreeMap<String,java.util.TreeSet<String>>();
    private final Vector<Boundary> boundaries = new Vector<Boundary>();
    private static final class Boundary {
        final String low, high;
        final boolean horizontal;
        final int lowLimit, highLimit;
        Boundary(String low, String high, boolean horizontal, int a, int b) {
            this.low=low; this.high=high; this.horizontal=horizontal;
            lowLimit=a; highLimit=b;
        }
    }
    PcbTwoLayerRules(TroubleshootBoard board, PcbBoardLayout layout) {
        this.board=board;
        PcbPlacementConstraints demands=board.getPlacementConstraints();
        demands.validate(board);
        for(String net:board.getNetIds()) {
            java.util.TreeSet<String> domainsForNet=new java.util.TreeSet<String>();
            for(String pad:board.getNet(net).getPadIds())
                domainsForNet.add(demands.domainForPad(board,pad));
            netDomains.put(net,domainsForNet);
        }
        TreeMap<String,Rectangle> domains=new TreeMap<String,Rectangle>();
        for(PcbPlacementConstraints.Part part:demands.getParts()) {
            PcbComponentPlacement placement = layout.getComponent(part.componentId);
            if (placement == null) throw new IllegalArgumentException("Missing domain package placement");
            for (java.util.Map.Entry<String,Rectangle> entry : demands.domainCourtyards(placement).entrySet()) {
                Rectangle r=entry.getValue(),old=domains.get(entry.getKey());
                if(old==null) domains.put(entry.getKey(),r);
                else {
                    int right=Math.max(old.x+old.width,r.x+r.width);
                    int bottom=Math.max(old.y+old.height,r.y+r.height);
                    old.x=Math.min(old.x,r.x); old.y=Math.min(old.y,r.y);
                    old.width=right-old.x; old.height=bottom-old.y;
                }
            }
        }
        for(PcbPlacementConstraints.Barrier barrier:demands.getBarriers()) {
            String first=barrier.firstDomain,second=barrier.secondDomain;
            Rectangle a=domains.get(first), b=domains.get(second);
            int gap=barrier.clearance;
            if(a.x+a.width+gap<=b.x) add(first,second,true,a.x+a.width,b.x,gap);
            else if(b.x+b.width+gap<=a.x) add(second,first,true,b.x+b.width,a.x,gap);
            else if(a.y+a.height+gap<=b.y) add(first,second,false,a.y+a.height,b.y,gap);
            else if(b.y+b.height+gap<=a.y) add(second,first,false,b.y+b.height,a.y,gap);
            else throw new IllegalArgumentException("P07 domain barrier has no separated corridor");
        }
    }
    private void add(String low,String high,boolean horizontal,int a,int b,int gap) {
        int middle=a+(b-a)/2;
        boundaries.add(new Boundary(low,high,horizontal,middle-(gap+1)/2,middle+gap/2));
    }
    /** Isolation corridors span the whole board and BOTH copper faces. */
    boolean permits(String net,Rectangle copper) {
        java.util.Set<String> domains=netDomains.get(net);
        if(domains==null) return false;
        return permitsDomains(domains,copper);
    }
    private boolean permitsDomain(String domain,Rectangle copper) {
        java.util.TreeSet<String> domains=new java.util.TreeSet<String>();
        domains.add(domain);
        return permitsDomains(domains,copper);
    }
    private boolean permitsDomains(java.util.Set<String> domains,Rectangle copper) {
        for(Boundary boundary:boundaries) {
            int first=boundary.horizontal?copper.x:copper.y;
            int last=first+(boundary.horizontal?copper.width:copper.height);
            if(last>boundary.lowLimit && first<boundary.highLimit) return false;
            if(domains.contains(boundary.low) && last>boundary.lowLimit ||
                    domains.contains(boundary.high) && first<boundary.highLimit) return false;
        }
        return true;
    }
    /** P07 is evidence, not normal customer-challenge admission; policy adoption belongs to P09. */
    static void requireDeveloperAdmission(PcbBoardLayout layout,boolean developerOnly) {
        if(layout==null || developerOnly) return;
        boolean top=false,bottom=false;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            top|=trace.getLayer()==PcbCopperLayer.TOP;
            bottom|=trace.getLayer()==PcbCopperLayer.BOTTOM;
        }
        boolean via=false;
        for(PcbBoardHole hole:layout.getHoles()) via|=hole.kind==PcbBoardHole.Kind.VIA;
        if(top && bottom || via)
            throw new IllegalArgumentException("Two-layer routing requires developer-only construction outside the P09 production envelope");
    }

    /**
     * Physical rules for the separately versioned normal-medium admission.
     * This does not modify P09's single-face envelope or developer routing.
     */
    static void requireNormalMediumGeometry(TroubleshootBoard board,
            PcbBoardLayout layout, int maximumVias) {
        requireNormalGeometry(board, layout, maximumVias, MediumBoardNormalAdmission.Contract.MEDIUM);
    }

    static void requireNormalRb56Geometry(TroubleshootBoard board,
            PcbBoardLayout layout, int maximumVias) {
        requireNormalGeometry(board, layout, maximumVias, MediumBoardNormalAdmission.Contract.RB56);
    }

    /** One exact geometry owner with a finite, explicitly selected population contract. */
    static void requireNormalGeometry(TroubleshootBoard board, PcbBoardLayout layout,
            int maximumVias, MediumBoardNormalAdmission.Contract contract) {
        if (contract == null) throw new IllegalArgumentException("Missing normal physical contract");
        if (board == null || layout == null || maximumVias < 0)
            throw new IllegalArgumentException("Normal medium physical admission requires a board and layout");
        PcbPlacementConstraints demands = board.getPlacementConstraints();
        if (!MediumBoardPhysicalPolicy.selected(demands))
            throw new IllegalArgumentException("Normal medium physical admission requires MEDIUM_BOARD@1 constraints");
        board.validate();
        demands.validate(board);
        int parts = board.getComponentIds().size();
        int pads = board.getPadIds().size();
        if (contract == MediumBoardNormalAdmission.Contract.MEDIUM) {
            if (parts < 20 || parts > 40 || layout.getComponents().size() != parts ||
                    pads < parts * 2 || pads > parts * 5 || layout.getPads().size() != pads ||
                    board.getNetIds().isEmpty() || board.getNetIds().size() > pads)
                throw new IllegalArgumentException("Normal medium physical population is outside 20..40 supported footprints");
        } else {
            if (parts < contract.minimumParts || parts > contract.maximumParts ||
                    layout.getComponents().size() != parts || pads < parts * 2 ||
                    pads > parts * contract.maximumTerminals || layout.getPads().size() != pads ||
                    board.getNetIds().isEmpty() || board.getNetIds().size() > pads)
                throw new IllegalArgumentException("Normal RB56 physical population is outside 40..60 supported footprints");
            requireRb56Declarations(board, layout, demands);
        }
        for (PcbComponentPlacement placement : layout.getComponents()) {
            PhysicalPackage physical = placement.getPhysicalPackage();
            if (!isSupportedProductionPackage(physical, contract) || physical.isDeveloperGeneric() ||
                    placement.getPhysicalGeometry().getRaisedCrossover() != null)
                throw new IllegalArgumentException("Normal medium physical admission rejected an unsupported footprint or factory link");
        }

        layout.validateGeometry(board);
        if (PcbFactoryLinkPolicy.validateLayout(layout) != 0)
            throw new IllegalArgumentException("Normal medium physical admission does not allow factory links");
        if (PcbTraceRules.TRACE_WIDTH < 9 || PcbTraceRules.MIN_VISIBLE_CLEARANCE < 6 ||
                PcbTraceRules.MIN_CENTERLINE_CLEARANCE < PcbTraceRules.TRACE_WIDTH + 6)
            throw new IllegalArgumentException("Normal medium trace access floors are not enabled");

        boolean top = false, bottom = false;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if (trace.getExposure() != PcbCopperAccess.Exposure.EXPOSED)
                throw new IllegalArgumentException("Normal medium copper must remain exposed for inspection");
            top |= trace.getLayer() == PcbCopperLayer.TOP;
            bottom |= trace.getLayer() == PcbCopperLayer.BOTTOM;
        }
        if (!top || !bottom)
            throw new IllegalArgumentException("Normal medium admission requires routed copper on both faces");

        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            if (hole.kind != PcbBoardHole.Kind.VIA)
                throw new IllegalArgumentException("Normal medium admission does not allow extra drills");
            vias++;
        }
        if (vias < 1 || vias > maximumVias)
            throw new IllegalArgumentException("Normal medium via count is outside the qualified P07 bound");

        for (PcbPadPlacement pad : layout.getPads()) {
            Rectangle land = pad.getPadBounds(), probe = pad.getProbeBounds();
            if (pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    !PcbCopperAccess.canProbe(pad, PcbBoardSide.TOP) ||
                    !PcbCopperAccess.canProbe(pad, PcbBoardSide.BOTTOM) ||
                    land.width < SupportedEnvelope.MIN_PAD ||
                    land.height < SupportedEnvelope.MIN_PAD ||
                    probe.width < SupportedEnvelope.MIN_PROBE ||
                    probe.height < SupportedEnvelope.MIN_PROBE)
                throw new IllegalArgumentException("Normal medium terminal lacks the qualified two-face probe access floor");
        }

        new PcbTwoLayerRules(board, layout).validate(layout);
    }

    /** RB56 is additive; the old single-argument allowlist remains unchanged. */
    private static boolean isSupportedProductionPackage(PhysicalPackage physical,
            MediumBoardNormalAdmission.Contract contract) {
        return isSupportedProductionPackage(physical) ||
            contract == MediumBoardNormalAdmission.Contract.RB56 &&
                (physical == PhysicalPackages.ISOLATED_CONVERTER_7 ||
                 physical == PhysicalPackages.OPTOCOUPLER_4 ||
                 physical == PhysicalPackages.RADIAL_INDUCTOR_2);
    }

    /** Drawing-space recipe only; its corridor conveys no manufacturing safety rating. */
    private static void requireRb56Declarations(TroubleshootBoard board, PcbBoardLayout layout,
            PcbPlacementConstraints demands) {
        if (!"RB56_CONTROL_BOARD".equals(board.getId()) || demands.routingLayer != PcbCopperLayer.BOTTOM)
            throw new IllegalArgumentException("Normal RB56 requires its exact declared board and routing face");
        Vector<PcbPlacementConstraints.Barrier> barriers = demands.getBarriers();
        if (barriers.size() != 1 || !"PRIMARY".equals(barriers.get(0).firstDomain) ||
                !"SECONDARY".equals(barriers.get(0).secondDomain) || barriers.get(0).clearance != 80)
            throw new IllegalArgumentException("Normal RB56 requires the declared PRIMARY/SECONDARY 80-unit corridor");
        int declaredPads = 0;
        for (String id : board.getComponentIds()) {
            BoardComponent component = board.getComponent(id);
            PhysicalPackage physical = component.getPhysicalPackage();
            PcbPlacementConstraints.Part demand = demands.get(id);
            Vector<String> terminals = physical.getTerminalIds();
            if (terminals.size() < 2 || terminals.size() > 7 ||
                    component.getPadIds().size() != terminals.size())
                throw new IllegalArgumentException("Normal RB56 package has an unsupported terminal census: " + id);
            declaredPads += terminals.size();
            boolean mixed = "UAC".equals(id) || "UFB".equals(id);
            if (mixed) {
                PhysicalPackage expected = "UAC".equals(id) ? PhysicalPackages.ISOLATED_CONVERTER_7 : PhysicalPackages.OPTOCOUPLER_4;
                if (physical != expected || !"PRIMARY".equals(demand.domainId) ||
                        demand.anchor != PcbPlacementConstraints.Anchor.NONE ||
                        demand.getTerminalDomains().size() != terminals.size())
                    throw new IllegalArgumentException("Normal RB56 mixed package lost its exact declaration: " + id);
            } else {
                if ((!"PRIMARY".equals(demand.domainId) && !"SECONDARY".equals(demand.domainId)) ||
                        !demand.getTerminalDomains().isEmpty() || physical.getGeometry().getIsolationBody() != null)
                    throw new IllegalArgumentException("Normal RB56 ordinary package has a foreign domain: " + id);
                PcbPlacementConstraints.Anchor expected = !physical.isConnector() ? PcbPlacementConstraints.Anchor.NONE :
                    "PRIMARY".equals(demand.domainId) ? PcbPlacementConstraints.Anchor.LEFT : PcbPlacementConstraints.Anchor.RIGHT;
                if (demand.anchor != expected)
                    throw new IllegalArgumentException("Normal RB56 connector or package has a foreign anchor: " + id);
            }
            for (String terminal : terminals) {
                BoardPad pad = board.getPad(id + "." + terminal);
                if (pad == null || !id.equals(pad.getComponentId()) ||
                        !terminal.equals(pad.getTerminalId()) || !component.getPadIds().contains(pad.getId()))
                    throw new IllegalArgumentException("Normal RB56 package lost a declared terminal pad: " + id + "." + terminal);
                String netDomain = Rb56Placement.domain(pad.getNetId());
                if (!netDomain.equals(demand.terminalDomain(terminal)))
                    throw new IllegalArgumentException("Normal RB56 terminal domain disagrees with its declared net: " + pad.getId());
            }
        }
        if (declaredPads != board.getPadIds().size())
            throw new IllegalArgumentException("Normal RB56 pad census differs from declared package terminals");
        requireRb56MixedPose(board, layout, demands, "UAC", PhysicalPackages.ISOLATED_CONVERTER_7,
            PcbRotation.DEG_0, new String[] {"IN+", "IN-", "EN", "FB", "BIAS"}, new String[] {"PRE_L+", "OUT-"});
        requireRb56MixedPose(board, layout, demands, "UFB", PhysicalPackages.OPTOCOUPLER_4,
            PcbRotation.DEG_180, new String[] {"C", "E"}, new String[] {"A", "K"});
        ExternalBoardPowerInput mains = board.getPowerInput("MAINAC");
        PcbPlacementConstraints.Part entry = demands.get("JAC");
        if (mains == null || !"JAC.1".equals(mains.getPositivePadId()) || !"JAC.2".equals(mains.getReturnPadId()) ||
                entry == null || !"PRIMARY".equals(entry.domainId) || entry.anchor != PcbPlacementConstraints.Anchor.LEFT)
            throw new IllegalArgumentException("Normal RB56 mains connector lost its primary entry declaration");
    }

    private static void requireRb56MixedPose(TroubleshootBoard board, PcbBoardLayout layout,
            PcbPlacementConstraints demands, String id, PhysicalPackage physical, PcbRotation rotation,
            String[] primary, String[] secondary) {
        BoardComponent component = board.getComponent(id);
        PcbComponentPlacement placement = layout.getComponent(id);
        PcbPlacementConstraints.Part demand = demands.get(id);
        if (component == null || component.getPhysicalPackage() != physical || placement == null ||
                placement.getPhysicalPackage() != physical || placement.getMountingSide() != PcbBoardSide.TOP ||
                placement.getRotation() != rotation || demand == null)
            throw new IllegalArgumentException("Normal RB56 mixed package lost its exact physical pose: " + id);
        for (String terminal : primary) if (!"PRIMARY".equals(demand.terminalDomain(terminal)))
            throw new IllegalArgumentException("Normal RB56 mixed package lost a primary terminal: " + id + "." + terminal);
        for (String terminal : secondary) if (!"SECONDARY".equals(demand.terminalDomain(terminal)))
            throw new IllegalArgumentException("Normal RB56 mixed package lost a secondary terminal: " + id + "." + terminal);
    }

    private static boolean isSupportedProductionPackage(PhysicalPackage physical) {
        return physical == PhysicalPackages.RELAY_SPDT ||
            physical == PhysicalPackages.AXIAL_RESISTOR ||
            physical == PhysicalPackages.AXIAL_FUSE ||
            physical == PhysicalPackages.AXIAL_DIODE ||
            physical == PhysicalPackages.THROUGH_HOLE_LED ||
            physical == PhysicalPackages.TO92_NPN ||
            physical == PhysicalPackages.TO92_NMOS ||
            physical == PhysicalPackages.TO220_REGULATOR_4 ||
            physical == PhysicalPackages.E04_DECISION_CONTROL_5 ||
            physical == PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR ||
            physical == PhysicalPackages.RADIAL_CERAMIC_CAPACITOR ||
            physical == PhysicalPackages.THROUGH_HOLE_CONNECTOR_2 ||
            physical == PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2;
    }

    static PcbBoardHole via(String id,String net,int x,int y) {
        return new PcbBoardHole(id,PcbBoardHole.Kind.VIA,net,x,y,VIA_DRILL,VIA_LAND,
            PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM,PcbCopperAccess.Exposure.EXPOSED);
    }
    static Rectangle expand(Rectangle r,int d) {
        return new Rectangle(r.x-d,r.y-d,r.width+2*d,r.height+2*d);
    }
    static boolean inside(Rectangle a,Rectangle b) {
        return b.x>=a.x && b.y>=a.y && (long)b.x+b.width<=(long)a.x+a.width &&
            (long)b.y+b.height<=(long)a.y+a.height;
    }
    static boolean contains(Rectangle r,int x,int y) {
        return x>=r.x && y>=r.y && x<=r.x+r.width && y<=r.y+r.height;
    }
    private boolean permitsDrill(Rectangle drill) {
        for (Boundary boundary : boundaries) {
            long first=boundary.horizontal?drill.x:drill.y;
            long last=first+(boundary.horizontal?drill.width:drill.height);
            if(last>boundary.lowLimit && first<boundary.highLimit) return false;
        }
        return true;
    }
    private void validateIsolationBodies(PcbBoardLayout layout) {
        PcbPlacementConstraints demands=board.getPlacementConstraints();
        for (PcbComponentPlacement part : layout.getComponents()) {
            PhysicalPackageGeometry geometry=part.getPhysicalGeometry();
            PhysicalPackageGeometry.IsolationBody body=geometry.getIsolationBody();
            if (body==null) continue;
            PcbPlacementConstraints.Part demand=demands.get(part.getComponentId());
            Rectangle span=geometry.placedAt(part.getPose()).getIsolationBodySpan();
            Rectangle bounds=part.getBodyBounds();
            for (Boundary boundary : boundaries) {
                int first=boundary.horizontal?bounds.x:bounds.y;
                int last=first+(boundary.horizontal?bounds.width:bounds.height);
                int start=Math.max(first,boundary.lowLimit),end=Math.min(last,boundary.highLimit);
                if (end<=start) continue;
                String a=body.domain(demand,true),b=body.domain(demand,false);
                if (!(boundary.low.equals(a)&&boundary.high.equals(b) ||
                        boundary.low.equals(b)&&boundary.high.equals(a)))
                    throw new IllegalStateException("Insulating body crosses an unrelated barrier");
                Rectangle crossing=boundary.horizontal?
                    new Rectangle(start,bounds.y,end-start,bounds.height):
                    new Rectangle(bounds.x,start,bounds.width,end-start);
                if (!inside(span,crossing))
                    throw new IllegalStateException("Package body crosses outside its declared insulating span");
            }
        }
    }
    void validate(PcbBoardLayout layout) {
        PcbPlacementConstraints demands=board.getPlacementConstraints();
        validateIsolationBodies(layout);
        for (PcbPadPlacement pad : layout.getPads()) {
            BoardPad logical=board.getPad(pad.getPadId());
            if (logical==null || !permits(logical.getNetId(),pad.getPadBounds()))
                throw new IllegalStateException("Component land crosses a physical domain barrier");
        }
        for (PcbComponentPlacement part : layout.getComponents()) {
            Vector<String> terminals=part.getPhysicalPackage().getTerminalIds();
            PcbPlacementConstraints.Part demand=demands.get(part.getComponentId());
            for(int index=0;index<terminals.size();index++)
                if (!permitsDomain(demand.terminalDomain(terminals.get(index)),part.getLeadBounds(index)) ||
                        !permitsDomain(demand.terminalDomain(terminals.get(index)),part.getLeadBounds(index,true)))
                    throw new IllegalStateException("Component lead crosses a physical domain barrier");
        }
        for(PcbTraceGeometry trace:layout.getTraces()) {
            int[] x=trace.getXPoints(),y=trace.getYPoints();
            for(int i=1;i<x.length;i++) if(!permits(trace.getNetId(),PcbConductorBuilder.stroke(x[i-1],y[i-1],x[i],y[i])))
                throw new IllegalStateException("P07 copper crosses a physical domain barrier");
        }
        for(PcbBoardHole hole:layout.getHoles()) {
            if (!permitsDrill(hole.getBounds()))
                throw new IllegalStateException("Drill or land crosses a physical domain barrier");
            if(hole.kind!=PcbBoardHole.Kind.NON_PLATED &&
                    (board.getNet(hole.netId)==null || !permits(hole.netId,hole.getBounds())))
                throw new IllegalStateException("P07 via crosses a domain barrier or has an unknown net");
            Rectangle clearance=expand(hole.getBounds(),PcbTraceRules.MIN_VISIBLE_CLEARANCE);
            for(PcbComponentPlacement part:layout.getComponents())
                if(PcbConductorBuilder.touch(hole.getBounds(),part.getRoutingCourtyard()))
                    throw new IllegalStateException("P07 via drills through a package courtyard");
            for(PcbPadPlacement pad:layout.getPads())
                if(PcbConductorBuilder.touch(clearance,pad.getPadBounds()))
                    throw new IllegalStateException("P07 via overlaps a component pad or its clearance");
            for(PcbTraceGeometry trace:layout.getTraces()) {
                if(hole.netId!=null && hole.netId.equals(trace.getNetId())) continue;
                int[] x=trace.getXPoints(),y=trace.getYPoints();
                for(int i=1;i<x.length;i++) if(PcbConductorBuilder.touch(clearance,
                        PcbConductorBuilder.stroke(x[i-1],y[i-1],x[i],y[i])))
                    throw new IllegalStateException("P07 via violates foreign copper clearance");
            }
            for(PcbBoardHole other:layout.getHoles()) if(!hole.id.equals(other.id) &&
                    PcbConductorBuilder.touch(clearance,other.getBounds()))
                throw new IllegalStateException("P07 overlapping drill/land clearance");
        }
    }
}
