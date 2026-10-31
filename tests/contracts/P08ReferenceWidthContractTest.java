package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/** Independent P08 oracle contracts for finite trace width and two-face ROLE routing. */
public final class P08ReferenceWidthContractTest {
    private static int checks;

    private P08ReferenceWidthContractTest() { }

    public static void main(String[] args) {
        malformedDeclaredWidths();
        literalWidthStrokesContactsAndGraphs();
        independentClearanceBoundaries();
        actualRoleWidthRouteAndRejections();
        System.out.println("PASS: P08 reference width contracts assertions=" + checks);
    }

    private static void malformedDeclaredWidths() {
        for(int invalid:new int[] {8,10,64,65}) {
            final int width=invalid;
            reject(new Runnable() { public void run() {
                line("invalid-width","N",width,new int[] {100,200},new int[] {100,100});
            }},"finite odd trace width rejects " + width);
        }
    }

    private static void literalWidthStrokesContactsAndGraphs() {
        TroubleshootBoard board = new TroubleshootBoard("P08_REFERENCE_WIDTHS");
        board.addNet(new BoardNet("N9", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("N11", BoardNet.RoutingRole.SUPPLY));
        board.addNet(new BoardNet("N13", BoardNet.RoutingRole.HIGH_CURRENT));
        board.setPlacementConstraints(new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
            PcbTraceWidthProfile.ROLE_WIDTHS));
        PcbBoardLayout layout = new PcbBoardLayout(1000,800,
            new Rectangle(20,20,800,700),new Rectangle(850,40,120,300));

        addTouchingPair(layout,"N9",9,100);
        addTouchingPair(layout,"N11",11,300);
        addTouchingPair(layout,"N13",13,500);

        PcbConductorGraph production = layout.captureConductorGraph(board);
        PcbConductorGraph reference = P08ReferenceConductorBuilder.capture(board,layout);
        require(production.toCanonical().equals(reference.toCanonical()),
            "independent all-pairs graph retains exact width-bearing graph parity");
        require(P08ReferenceConductorBuilder.sourceSegmentCount==6 &&
            P08ReferenceConductorBuilder.sourceLandCount==0 &&
            P08ReferenceConductorBuilder.segmentPairs==15 &&
            P08ReferenceConductorBuilder.segmentLandPairs==0 &&
            P08ReferenceConductorBuilder.landPairs==0,
            "reference source census and all-pairs counts are captured independently");

        int[] widths={9,11,13};
        int[] ys={100,300,500};
        String[] nets={"N9","N11","N13"};
        for(int index=0;index<widths.length;index++) {
            int width=widths[index], y=ys[index];
            String first="contact/"+nets[index]+"/first";
            String second="contact/"+nets[index]+"/second";
            requireTraceSurfaces(reference,first,width,new Rectangle[] {
                literalStroke(100,y,200,y,width) });
            // The two closed-envelope contact corners project to y and
            // y+ceil(width/2) on the vertical centerline, splitting that edge.
            int split=y+(width+1)/2;
            requireTraceSurfaces(reference,second,width,new Rectangle[] {
                literalStroke(200+width,y,200+width,split,width),
                literalStroke(200+width,split,200+width,y+100,width) });
            int contacts=0;
            for(PcbConductorGraph.Edge edge:reference.getEdges().values())
                if(edge.netId.equals(nets[index]) && edge.kind==PcbConductorGraph.Kind.CONTACT)
                    contacts++;
            require(contacts==2,
                "the two projected closed-envelope corners create two same-net contacts at width"+width);
            require(P08ReferenceConductorBuilder.stroke(100,y,200,y,width).equals(
                    literalStroke(100,y,200,y,width)),
                "independent literal stroke helper matches width"+width);
        }
        require(reference.toCanonical().contains("TRACEW11:") &&
            reference.toCanonical().contains("TRACEW13:") &&
            !reference.toCanonical().contains("TRACEW9:"),
            "canonical graph preserves implicit width9 and records only wider trace edges");
    }

    private static void addTouchingPair(PcbBoardLayout layout,String net,int width,int y) {
        layout.addTrace(line("contact/"+net+"/first",net,width,
            new int[] {100,200},new int[] {y,y}));
        layout.addTrace(line("contact/"+net+"/second",net,width,
            new int[] {200+width,200+width},new int[] {y,y+100}));
    }

    private static void independentClearanceBoundaries() {
        int[][] cases={{9,9,15},{9,11,16},{9,13,17},{11,11,17},{11,13,18},{13,13,19}};
        for(int[] item:cases) {
            int first=item[0],second=item[1],clearance=item[2];
            for(int offset=-1;offset<=1;offset++) {
                final List<PcbTraceGeometry> traces=clearancePair(first,second,
                    clearance+offset);
                boolean expected=offset>=0;
                require(acceptsReferenceClearance(traces)==expected,
                    "independent width"+first+"/"+second+" clearance boundary offset="+offset);
                require(acceptsProductionClearance(traces)==expected,
                    "production width"+first+"/"+second+" clearance boundary offset="+offset);
            }
        }
    }

    private static List<PcbTraceGeometry> clearancePair(int firstWidth,int secondWidth,int gap) {
        ArrayList<PcbTraceGeometry> traces=new ArrayList<PcbTraceGeometry>();
        traces.add(line("clearance/first","A",firstWidth,
            new int[] {100,300},new int[] {100,100}));
        traces.add(line("clearance/second","B",secondWidth,
            new int[] {100,300},new int[] {100+gap,100+gap}));
        return traces;
    }

    private static boolean acceptsReferenceClearance(List<PcbTraceGeometry> traces) {
        try { P08ScalabilityContractTest.referenceClearance(traces); return true; }
        catch(IllegalArgumentException expected) { return false; }
        catch(IllegalStateException expected) { return false; }
    }

    private static boolean acceptsProductionClearance(List<PcbTraceGeometry> traces) {
        try { PcbTraceClearance.validate(traces); return true; }
        catch(IllegalArgumentException expected) { return false; }
        catch(IllegalStateException expected) { return false; }
    }

    private static void actualRoleWidthRouteAndRejections() {
        RoleRouteFixture fixture=new RoleRouteFixture();
        PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(
            fixture.board,fixture.placement,PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        require(result.accepted() && result.layout!=null && result.vias>0,
            "actual ROLE_WIDTHS two-layer routing produces a route and explicit via");
        PcbBoardLayout routed=result.layout;
        routed.validateRoutingGeometry(fixture.board);
        routed.validateTraceClearance();

        int platedPads=0,viaHoles=0;
        for(String padId:fixture.board.getPadIds()) {
            require(routed.getPad(padId).getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE,
                "role-profile fixture preserves plated-through-hole pads");
            platedPads++;
        }
        for(PcbBoardHole hole:routed.getHoles())
            if(hole.kind==PcbBoardHole.Kind.VIA) viaHoles++;
        require(platedPads>0 && viaHoles==result.vias,
            "routed geometry retains actual PTH lands and declared via holes");

        boolean top=false,bottom=false,width11=false,width13=false;
        for(PcbTraceGeometry trace:routed.getTraces()) {
            int expected=PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(
                fixture.board.getNet(trace.getNetId()).getRoutingRole());
            require(trace.getWidth()==expected,
                "actual router emits its declared role width on each trace");
            top|=trace.getLayer()==PcbCopperLayer.TOP;
            bottom|=trace.getLayer()==PcbCopperLayer.BOTTOM;
            width11|=trace.getWidth()==11;
            width13|=trace.getWidth()==13;
        }
        require(top && bottom && width11 && width13,
            "actual ROLE_WIDTHS route exercises both faces and widths11/13");

        PcbConductorGraph production=routed.captureConductorGraph(fixture.board);
        PcbConductorGraph reference=P08ReferenceConductorBuilder.capture(fixture.board,routed);
        require(production.toCanonical().equals(reference.toCanonical()),
            "actual two-face ROLE_WIDTHS graph matches the independent all-pairs oracle");
        reference.pristine().requirePristineNetConnectivity(fixture.board);
        require(P08ReferenceConductorBuilder.sourceSegmentCount>0 &&
            P08ReferenceConductorBuilder.sourceLandCount>=platedPads*2+viaHoles*2,
            "actual reference census includes route segments, both PTH faces and via lands");
        require(P08ReferenceConductorBuilder.segmentPairs==
                P08ReferenceConductorBuilder.sourceSegmentCount*
                    (P08ReferenceConductorBuilder.sourceSegmentCount-1)/2 &&
            P08ReferenceConductorBuilder.segmentLandPairs==
                P08ReferenceConductorBuilder.sourceSegmentCount*
                    P08ReferenceConductorBuilder.sourceLandCount &&
            P08ReferenceConductorBuilder.landPairs==
                P08ReferenceConductorBuilder.sourceLandCount*
                    (P08ReferenceConductorBuilder.sourceLandCount-1)/2,
            "actual reference comparison counts equal independent S/L all-pairs formulas");

        rejectBoth(fixture.board,wrongNetRoute(fixture,routed),
            "wrong-net route endpoint is rejected by production and reference builders");
        rejectMissingVias(fixture,routed);
        rejectBoth(fixture.board,sameNetWidthMismatch(fixture,routed),
            "same-net role-width mismatch is rejected before reference normalization");
    }

    private static PcbBoardLayout wrongNetRoute(RoleRouteFixture fixture,PcbBoardLayout routed) {
        PcbBoardLayout wrong=routed.copyForCompaction();
        Vector<PcbTraceGeometry> traces=wrong.getTraces();
        int selected=-1;
        for(int index=0;index<traces.size();index++)
            if("OVER".equals(traces.get(index).getNetId())) { selected=index; break; }
        require(selected>=0,"actual route contains the width11 trace used for wrong-net rejection");
        PcbTraceGeometry original=traces.get(selected);
        traces.set(selected,new PcbTraceGeometry(original.getSourceId()+"/wrong-net","GND",
            original.getStartPadId(),original.getEndPadId(),original.getLayer(),
            original.getExposure(),original.getXPoints(),original.getYPoints(),
            original.getWidth()));
        wrong.replaceTraces(traces);
        return wrong;
    }

    private static PcbBoardLayout sameNetWidthMismatch(RoleRouteFixture fixture,
            PcbBoardLayout routed) {
        PcbBoardLayout wrong=routed.copyForCompaction();
        Vector<PcbTraceGeometry> traces=wrong.getTraces();
        PcbTraceGeometry original=null;
        for(PcbTraceGeometry trace:traces)
            if("UNDER".equals(trace.getNetId())) { original=trace; break; }
        require(original!=null,"actual route contains the HIGH_CURRENT trace used for width rejection");
        traces.add(new PcbTraceGeometry(original.getSourceId()+"/wrong-width",original.getNetId(),
            original.getStartPadId(),original.getEndPadId(),original.getLayer(),
            original.getExposure(),original.getXPoints(),original.getYPoints(),9));
        wrong.replaceTraces(traces);
        return wrong;
    }

    private static void rejectMissingVias(final RoleRouteFixture fixture,PcbBoardLayout routed) {
        final PcbBoardLayout missing=fixture.placement.copyForRouting();
        missing.replaceTraces(routed.getTraces());
        final PcbConductorGraph production=missing.captureConductorGraph(fixture.board);
        final PcbConductorGraph reference=P08ReferenceConductorBuilder.capture(fixture.board,missing);
        require(production.toCanonical().equals(reference.toCanonical()),
            "missing-via graph structure remains visible to both independent builders");
        reject(new Runnable() { public void run() {
            missing.validateRoutingGeometry(fixture.board);
        }},"production geometry rejects route connectivity after all generated vias are absent");
        reject(new Runnable() { public void run() {
            production.pristine().requirePristineNetConnectivity(fixture.board);
        }},"production conductor partition rejects missing-via route connectivity");
        reject(new Runnable() { public void run() {
            reference.pristine().requirePristineNetConnectivity(fixture.board);
        }},"independent conductor partition rejects missing-via route connectivity");
    }

    private static void rejectBoth(final TroubleshootBoard board,
            final PcbBoardLayout malformed,String message) {
        reject(new Runnable() { public void run() {
            malformed.captureConductorGraph(board);
        }},message+" (production)");
        reject(new Runnable() { public void run() {
            P08ReferenceConductorBuilder.capture(board,malformed);
        }},message+" (reference)");
    }

    private static final class RoleRouteFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout placement;

        RoleRouteFixture() {
            P06FactoryLinkFixtures.Fixture source=
                new P06FactoryLinkFixtures.Fixture(true,0,0,true);
            board=new TroubleshootBoard("P08_ROLE_WIDTH_ROUTED");
            for(String netId:source.board.getNetIds()) board.addNet(new BoardNet(netId,
                "OVER".equals(netId)?BoardNet.RoutingRole.SUPPLY:
                "UNDER".equals(netId)?BoardNet.RoutingRole.HIGH_CURRENT:
                BoardNet.RoutingRole.RETURN));
            for(String componentId:source.board.getComponentIds()) {
                BoardComponent original=source.board.getComponent(componentId);
                board.addComponent(new BoardComponent(componentId,original.getType(),
                    original.getPhysicalPackage(),original.getDisplayName(),
                    original.getReferenceLabelCharacters()));
            }
            for(String padId:source.board.getPadIds()) {
                BoardPad original=source.board.getPad(padId);
                board.addPad(new BoardPad(original.getId(),original.getComponentId(),
                    original.getTerminalId(),original.getNetId()));
            }
            for(String inputId:source.board.getPowerInputIds()) {
                ExternalBoardPowerInput original=source.board.getPowerInput(inputId);
                board.addPowerInput(new ExternalBoardPowerInput(original.getId(),
                    original.getPositivePadId(),original.getReturnPadId(),
                    original.getPositiveNetId(),original.getReturnNetId()));
            }
            Vector<PcbPlacementConstraints.Part> demands=
                new Vector<PcbPlacementConstraints.Part>();
            for(String componentId:board.getComponentIds())
                demands.add(new PcbPlacementConstraints.Part(componentId,"prototype",
                    "Factory-link fixture","low-voltage",PcbPlacementConstraints.Anchor.NONE,16));
            board.setPlacementConstraints(new PcbPlacementConstraints(demands,
                new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,
                PcbTraceWidthProfile.ROLE_WIDTHS));
            board.validate();
            placement=source.layout.copyForRouting();
        }
    }

    private static void requireTraceSurfaces(PcbConductorGraph graph,String source,int width,
            Rectangle[] expected) {
        Vector<Rectangle> actual=new Vector<Rectangle>();
        for(PcbConductorGraph.Edge edge:graph.getEdges().values())
            if(edge.kind==PcbConductorGraph.Kind.TRACE && edge.getSources().contains(source)) {
                require(edge.getWidth()==width,"reference edge retains declared width"+width);
                actual.add(surface(graph,edge.id).getBounds());
            }
        require(actual.size()==expected.length,"literal split-edge census for "+source);
        for(Rectangle bounds:expected)
            require(actual.remove(bounds),"literal finite-width split surface "+bounds+" for "+source);
        require(actual.isEmpty(),"all split surfaces were independently matched for "+source);
    }

    private static PcbConductorGraph.Surface surface(PcbConductorGraph graph,String edgeId) {
        for(PcbConductorGraph.Surface surface:graph.getSurfaces())
            if(edgeId.equals(surface.edgeId)) return surface;
        throw new AssertionError("Missing trace surface for " + edgeId);
    }

    private static PcbTraceGeometry line(String source,String net,int width,int[] x,int[] y) {
        return new PcbTraceGeometry(source,net,null,null,PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,x,y,width);
    }

    private static Rectangle literalStroke(int x1,int y1,int x2,int y2,int width) {
        int half=width/2;
        return new Rectangle(Math.min(x1,x2)-half,Math.min(y1,y2)-half,
            Math.abs(x2-x1)+width,Math.abs(y2-y1)+width);
    }

    private static void reject(Runnable action,String message) {
        try { action.run(); }
        catch(IllegalArgumentException expected) { checks++; return; }
        catch(IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected rejection: " + message);
    }

    private static void require(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
}
