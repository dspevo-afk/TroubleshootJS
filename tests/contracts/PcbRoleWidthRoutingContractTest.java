package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

/** Focused route-search contract for the bounded role-specific copper profile. */
public final class PcbRoleWidthRoutingContractTest {
    private static int assertions;
    private PcbRoleWidthRoutingContractTest() { }

    public static void main(String[] args) {
        singleLayerProducersUseNetWidths();
        roleWidthsProtectForeignPadLands();
        profiledTracePadContactUsesExactStroke();
        profiledSignal9PadTouchUsesExactStroke();
        roleWidthCourtyardGoalUsesExactStrokeEdge();
        twoFaceProducerKeepsWidthAndEndpointIdentity();
        roleProfileSessionResumesExactly();
        System.out.println("PASS: PCB role width routing contracts assertions="+assertions);
    }

    private static void singleLayerProducersUseNetWidths() {
        RouteFixture profiled=parallelFixture(PcbTraceWidthProfile.ROLE_WIDTHS);
        String placementBefore=placementSignature(profiled.layout);
        PcbNetRouter.route(profiled.layout,profiled.board,profiled.layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,true,0L,0);
        profiled.layout.validateRoutingGeometry(profiled.board);
        require(placementBefore.equals(placementSignature(profiled.layout)),
            "single-layer routing leaves component, pad and label placement unchanged");
        requireRoleWidthsAndEndpoints(profiled);

        RouteFixture uniform=parallelFixture(PcbTraceWidthProfile.UNIFORM);
        PcbNetRouter.route(uniform.layout,uniform.board,uniform.layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,true,0L,0);
        uniform.layout.validateRoutingGeometry(uniform.board);
        for(PcbTraceGeometry trace:uniform.layout.getTraces())
            require(trace.getWidth()==9,"uniform routing preserves legacy width9 for "+trace.getNetId());
        require(pathSignature(profiled.layout).equals(pathSignature(uniform.layout)),
            "clear parallel legacy and role-aware routes retain the same search path");
    }

    private static void roleWidthsProtectForeignPadLands() {
        for(PcbCopperLayer face:new PcbCopperLayer[]{PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM}) {
            PcbPadPlacement start=manualPad("S1.1",200,220);
            PcbPadPlacement end=manualPad("S2.1",300,220);
            PcbNetRouter.Router profiled=padLandRouter(PcbTraceWidthProfile.ROLE_WIDTHS,face);
            require(profiled.permitsLayerStep(220,220,230,220,"SIGNAL",start,end) &&
                profiled.permitsLayerStep(230,220,220,220,"SIGNAL",start,end),
                "role SIGNAL9 keeps the non-touching two-unit PTH gap on "+face);

            PcbNetRouter.Router uniform=padLandRouter(PcbTraceWidthProfile.UNIFORM,face);
            require(uniform.permitsLayerStep(220,220,230,220,"SIGNAL",start,end) &&
                uniform.permitsLayerStep(230,220,220,220,"SIGNAL",start,end),
                "uniform9 keeps its legacy decision at the same non-touching gap on "+face);
        }
    }

    private static void profiledTracePadContactUsesExactStroke() {
        for(PcbCopperLayer face:new PcbCopperLayer[]{PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM})
            for(boolean vertical:new boolean[]{false,true}) {
                checkTracePadCase(PcbTraceWidthProfile.ROLE_WIDTHS,face,vertical,0,false,true,
                    "role13 rejects exact foreign PTH contact");
                checkTracePadCase(PcbTraceWidthProfile.UNIFORM,face,vertical,0,true,false,
                    "uniform9 preserves its clear physical gap");
                checkTracePadCase(PcbTraceWidthProfile.ROLE_WIDTHS,face,vertical,1,true,false,
                    "role13 allows the one-unit separated PTH land");
            }
        profiledNonadjacentPadTouchUsesExactStroke();
    }

    private static void profiledNonadjacentPadTouchUsesExactStroke() {
        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_NONADJACENT_PAD_TOUCH");
        board.addNet(new BoardNet("HIGH",BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("SIGNAL",BoardNet.RoutingRole.SIGNAL));
        PhysicalPackage pkg=PhysicalPackage.developerPackageWithGenericGeometry(
            "ROLE_WIDTH_NONADJACENT_PAD_TOUCH_PACKAGE",singleton("1"),new Vector<String>(),false);
        addBarePad(board,"H1","HIGH",pkg);
        addBarePad(board,"H2","HIGH",pkg);
        addBarePad(board,"S","SIGNAL",pkg);
        setProfile(board,PcbTraceWidthProfile.ROLE_WIDTHS);
        board.validate();
        PcbBoardLayout layout=new PcbBoardLayout(600,450,new Rectangle(100,100,400,300),
            new Rectangle(520,120,60,250));
        PcbPadPlacement start=manualPad("H1.1",200,220);
        PcbPadPlacement end=manualPad("H2.1",300,220);
        layout.addPad(start);
        layout.addPad(end);
        layout.addPad(manualPad("S.1",260,240));
        Rectangle contact=PcbConductorBuilder.stroke(220,220,240,220,13);
        requireRectangle(contact,214,214,33,13,"literal long width13 stroke");
        require(PcbConductorBuilder.touch(contact,manualPad("S.1",260,240).getPadBounds()),
            "long width13 stroke touches the foreign pad only at its closed corner");
        PcbNetRouter.Router router=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP);
        require(!router.permitsLayerStep(220,220,240,220,"HIGH",start,end),
            "role search checks actual copper contact for a nonadjacent Manhattan step");

        PcbBoardLayout separatedLayout=new PcbBoardLayout(600,450,new Rectangle(100,100,400,300),
            new Rectangle(520,120,60,250));
        separatedLayout.addPad(start);
        separatedLayout.addPad(end);
        PcbPadPlacement separated=manualPad("S.1",261,241);
        separatedLayout.addPad(separated);
        Rectangle separatedStroke=PcbConductorBuilder.stroke(220,220,240,220,13);
        require(!PcbConductorBuilder.touch(separatedStroke,separated.getPadBounds()),
            "long width13 stroke has a real one-unit gap to the moved foreign pad");
        PcbNetRouter.Router separatedRouter=new PcbNetRouter.Router(separatedLayout,board,
            separatedLayout.getBoardOutline(),0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP);
        require(separatedRouter.permitsLayerStep(220,220,240,220,"HIGH",start,end),
            "role search allows the one-unit nonadjacent stroke-to-pad gap");
    }

    private static void checkTracePadCase(PcbTraceWidthProfile profile,PcbCopperLayer face,
            boolean vertical,int padShift,boolean expectedAllowed,boolean expectedTouch,String label) {
        int sourceWidth=profile.widthFor(BoardNet.RoutingRole.HIGH_CURRENT);
        int firstX=220,firstY=220,secondX=vertical?220:230,secondY=vertical?230:220;
        int foreignX=vertical?240+padShift:250+padShift;
        int foreignY=vertical?250+padShift:240+padShift;
        PcbPadPlacement foreign=manualPad("S.1",foreignX,foreignY);
        Rectangle stroke=PcbConductorBuilder.stroke(firstX,firstY,secondX,secondY,sourceWidth);
        Rectangle foreignBounds=foreign.getPadBounds();
        if(sourceWidth==13) {
            if(vertical) requireRectangle(stroke,214,214,13,23,"literal vertical width13 stroke");
            else requireRectangle(stroke,214,214,23,13,"literal horizontal width13 stroke");
        } else {
            if(vertical) requireRectangle(stroke,216,216,9,19,"literal vertical width9 stroke");
            else requireRectangle(stroke,216,216,19,9,"literal horizontal width9 stroke");
        }
        require(PcbConductorBuilder.touch(stroke,foreignBounds)==expectedTouch,
            label+" agrees with the core closed-edge stroke/pad predicate");

        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_PAD_TOUCH_"+profile.getIdentity()+
            "_"+face+"_"+(vertical?"V":"H")+"_"+padShift);
        board.addNet(new BoardNet("HIGH",BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("SIGNAL",BoardNet.RoutingRole.SIGNAL));
        PhysicalPackage pkg=PhysicalPackage.developerPackageWithGenericGeometry(
            "ROLE_WIDTH_PAD_TOUCH_PACKAGE",singleton("1"),new Vector<String>(),false);
        addBarePad(board,"H1","HIGH",pkg);
        addBarePad(board,"H2","HIGH",pkg);
        addBarePad(board,"S","SIGNAL",pkg);
        setProfile(board,profile);
        board.validate();
        int startX=vertical?220:200,startY=vertical?200:220;
        int endX=vertical?220:300,endY=vertical?300:220;
        PcbBoardLayout layout=new PcbBoardLayout(600,450,new Rectangle(100,100,400,300),
            new Rectangle(520,120,60,250));
        PcbPadPlacement start=manualPad("H1.1",startX,startY);
        PcbPadPlacement end=manualPad("H2.1",endX,endY);
        layout.addPad(start);
        layout.addPad(end);
        layout.addPad(foreign);
        PcbNetRouter.Router router=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,face);
        require(router.permitsLayerStep(firstX,firstY,secondX,secondY,"HIGH",start,end)==expectedAllowed,
            label+" forward on "+face);
        require(router.permitsLayerStep(secondX,secondY,firstX,firstY,"HIGH",start,end)==expectedAllowed,
            label+" reverse on "+face);
    }

    private static void profiledSignal9PadTouchUsesExactStroke() {
        for(int translation:new int[]{0,-600})
            for(PcbCopperLayer face:new PcbCopperLayer[]{PcbCopperLayer.TOP,PcbCopperLayer.BOTTOM})
                for(boolean vertical:new boolean[]{false,true}) {
                    String location=translation<0?"negative-board":"positive-board";
                    checkSignal9PadTouchCase(face,vertical,-2,translation,false,true,
                        "role SIGNAL9 rejects exact foreign-land contact on "+location);
                    checkSignal9PadTouchCase(face,vertical,-1,translation,true,false,
                        "role SIGNAL9 allows one-unit foreign-land gap on "+location);
                }
    }

    private static void checkSignal9PadTouchCase(PcbCopperLayer face,boolean vertical,
            int padShift,int translation,boolean expectedAllowed,boolean expectedTouch,String label) {
        int width=PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(BoardNet.RoutingRole.SIGNAL);
        require(width==9,label+" uses the profiled SIGNAL9 width");
        int firstX=220+translation,firstY=220+translation;
        int secondX=(vertical?220:230)+translation;
        int secondY=(vertical?230:220)+translation;
        int foreignX=(vertical?240:250)+padShift+translation;
        int foreignY=(vertical?250:240)+padShift+translation;
        Rectangle stroke=PcbConductorBuilder.stroke(firstX,firstY,secondX,secondY,width);
        Rectangle foreignBounds=new Rectangle(foreignX-13,foreignY-13,26,26);
        if(vertical) {
            requireRectangle(stroke,216+translation,216+translation,9,19,
                label+" literal vertical width9 stroke");
            requireRectangle(foreignBounds,(padShift==-2?225:226)+translation,
                (padShift==-2?235:236)+translation,26,26,
                label+" literal vertical foreign land");
        } else {
            requireRectangle(stroke,216+translation,216+translation,19,9,
                label+" literal horizontal width9 stroke");
            requireRectangle(foreignBounds,(padShift==-2?235:236)+translation,
                (padShift==-2?225:226)+translation,26,26,
                label+" literal horizontal foreign land");
        }
        require(PcbConductorBuilder.touch(stroke,foreignBounds)==expectedTouch,
            label+" matches its independently fixed contact expectation");

        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_SIGNAL9_PAD_TOUCH_"+
            face+"_"+(vertical?"V":"H")+"_"+padShift+"_"+translation);
        board.addNet(new BoardNet("SIGNAL",BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("HIGH",BoardNet.RoutingRole.HIGH_CURRENT));
        PhysicalPackage pkg=PhysicalPackage.developerPackageWithGenericGeometry(
            "ROLE_WIDTH_SIGNAL9_PAD_TOUCH_PACKAGE",singleton("1"),new Vector<String>(),false);
        addBarePad(board,"S1","SIGNAL",pkg);
        addBarePad(board,"S2","SIGNAL",pkg);
        addBarePad(board,"H","HIGH",pkg);
        setProfile(board,PcbTraceWidthProfile.ROLE_WIDTHS);
        board.validate();
        PcbBoardLayout layout=new PcbBoardLayout(600,450,
            new Rectangle(100+translation,100+translation,400,300),
            new Rectangle(520+translation,120+translation,60,250));
        PcbPadPlacement start=manualPad("S1.1",(vertical?220:200)+translation,
            (vertical?200:220)+translation);
        PcbPadPlacement end=manualPad("S2.1",(vertical?220:300)+translation,
            (vertical?300:220)+translation);
        layout.addPad(start);
        layout.addPad(end);
        layout.addPad(manualPad("H.1",foreignX,foreignY));
        PcbNetRouter.Router router=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,face);
        require(router.permitsLayerStep(firstX,firstY,secondX,secondY,"SIGNAL",start,end)==expectedAllowed,
            label+" forward on "+face);
        require(router.permitsLayerStep(secondX,secondY,firstX,firstY,"SIGNAL",start,end)==expectedAllowed,
            label+" reverse on "+face);
    }

    private static void roleWidthCourtyardGoalUsesExactStrokeEdge() {
        GoalBoundaryMeasurement width11=measureCourtyardGoalBoundary(11,105,245);
        requireRectangle(width11.courtyard,115,110,130,40,"width11 courtyard");
        requireRectangle(width11.clearStroke,245,125,11,11,"width11 clear edge stroke");
        requireRectangle(width11.overlapStroke,235,125,11,11,"width11 overlapping stroke");
        require(width11.courtyard.x+width11.courtyard.width==245 &&
            !width11.clearPhysicalOverlap && width11.clearSearchFreeCell &&
            width11.clearSearchGoal,
            "width11 centerline at the exact far edge clears the courtyard and remains a P07 goal");
        require(width11.overlapPhysical && !width11.overlapSearchGoal,
            "width11 point whose actual stroke overlaps the courtyard remains blocked");

        GoalBoundaryMeasurement width13=measureCourtyardGoalBoundary(13,104,244);
        requireRectangle(width13.courtyard,114,110,130,40,"width13 courtyard");
        requireRectangle(width13.clearStroke,244,124,13,13,"width13 clear edge stroke");
        requireRectangle(width13.overlapStroke,234,124,13,13,"width13 overlapping stroke");
        require(width13.courtyard.x+width13.courtyard.width==244 &&
            !width13.clearPhysicalOverlap && width13.clearSearchFreeCell &&
            width13.clearSearchGoal,
            "width13 centerline at the exact far edge clears the courtyard and remains a P07 goal");
        require(width13.overlapPhysical && !width13.overlapSearchGoal,
            "width13 point whose actual stroke overlaps the courtyard remains blocked");

        GoalBoundaryMeasurement legacy9=measureCourtyardGoalBoundary(9,106,246);
        requireRectangle(legacy9.courtyard,116,110,130,40,"legacy9 courtyard");
        requireRectangle(legacy9.clearStroke,246,126,9,9,"legacy9 clear edge stroke");
        requireRectangle(legacy9.overlapStroke,236,126,9,9,"legacy9 overlapping stroke");
        require(!legacy9.clearPhysicalOverlap && !legacy9.clearSearchGoal,
            "legacy9 retains its historical inclusive courtyard goal boundary");
        require(legacy9.overlapPhysical && !legacy9.overlapSearchGoal,
            "legacy9 still blocks a point whose actual stroke overlaps the courtyard");
    }

    /** Shared literal point fixture for the diagnostic and maintained routing contract. */
    static GoalBoundaryMeasurement measureCourtyardGoalBoundary(int width,int originX,
            int courtRight) {
        BoardNet.RoutingRole role=width==13?BoardNet.RoutingRole.HIGH_CURRENT:
            width==11?BoardNet.RoutingRole.SUPPLY:BoardNet.RoutingRole.SIGNAL;
        PcbTraceWidthProfile profile=width==9?PcbTraceWidthProfile.UNIFORM:
            PcbTraceWidthProfile.ROLE_WIDTHS;
        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_GOAL_BOUNDARY_"+width);
        String net="TRACE";
        board.addNet(new BoardNet(net,role));
        PhysicalPackage pkg=PhysicalPackage.developerPackageWithGenericGeometry(
            "ROLE_WIDTH_GOAL_PACKAGE_"+width,singleton("1"),new Vector<String>(),false);
        BoardComponent component=new BoardComponent("BLOCK",pkg.getId(),pkg);
        board.addComponent(component);
        board.addPad(new BoardPad("BLOCK.1","BLOCK","1",net));
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("BLOCK","route","Route","board",
            PcbPlacementConstraints.Anchor.NONE,20));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,11,false,profile));
        PcbBoardLayout layout=new PcbBoardLayout(700,320,new Rectangle(20,20,600,230),
            new Rectangle(20,270,200,30));
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,originX,100,
            pkg.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
        board.validate();
        Rectangle courtyard=footprint.getPlacement().getRoutingCourtyard();
        if(courtyard.x+courtyard.width!=courtRight)
            throw new AssertionError("Boundary fixture courtyard edge changed: width="+width+
                " actual="+(courtyard.x+courtyard.width)+" expected="+courtRight);
        int clearX=250,pointY=130,overlapX=240;
        Rectangle clearStroke=pointStroke(clearX,pointY,width);
        Rectangle overlapStroke=pointStroke(overlapX,pointY,width);
        boolean clearPhysicalOverlap=courtyard.intersects(clearStroke);
        boolean overlapPhysical=courtyard.intersects(overlapStroke);
        PcbNetRouter.Router router=new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),
            0,P06FactoryLinkFixtures.OBSERVER,PcbCopperLayer.TOP,PcbTwoLayerRules.VIA_LAND);
        boolean clearFreeCell=router.freeLayerCell(clearX,pointY,net);
        Vector<Point> points=new Vector<Point>();
        points.add(new Point(clearX,pointY));
        points.add(new Point(overlapX,pointY));
        router.occupyLayerPath(points,net);
        Rectangle outline=layout.getBoardOutline();
        boolean clearGoal=router.ownsLayerGridCell((clearX-(outline.x+10))/10,
            (pointY-(outline.y+10))/10,net);
        boolean overlapGoal=router.ownsLayerGridCell((overlapX-(outline.x+10))/10,
            (pointY-(outline.y+10))/10,net);
        return new GoalBoundaryMeasurement(width,courtyard,clearStroke,overlapStroke,
            clearPhysicalOverlap,clearFreeCell,clearGoal,overlapPhysical,overlapGoal);
    }

    private static Rectangle pointStroke(int x,int y,int width) {
        int half=width/2;
        return new Rectangle(x-half,y-half,width,width);
    }

    private static void requireRectangle(Rectangle rectangle,int x,int y,int width,int height,
            String label) {
        require(rectangle.x==x && rectangle.y==y && rectangle.width==width &&
            rectangle.height==height,label+" has its literal measured coordinates");
    }

    private static void twoFaceProducerKeepsWidthAndEndpointIdentity() {
        RoleCrossingFixture crossing=typedCrossingFixture();
        PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(crossing.board,crossing.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        require(result.accepted(),"bounded role-aware two-face crossing fixture routes");
        result.layout.validateRoutingGeometry(crossing.board);
        new PcbTwoLayerRules(crossing.board,result.layout).validate(result.layout);
        requireWideCrossingConnectivity(result.layout,crossing.board,"blocking P07");
    }

    private static void roleProfileSessionResumesExactly() {
        RoleCrossingFixture slicedFixture=typedCrossingFixture();
        RoleCrossingFixture blockingFixture=typedCrossingFixture();
        String original=slicedFixture.layout.geometryFingerprint();
        require(original.equals(blockingFixture.layout.geometryFingerprint()),
            "sliced and blocking role-profile routes start from identical placements");
        PcbLayerRoutingPrototype.Session session=PcbLayerRoutingPrototype.begin(
            slicedFixture.board,slicedFixture.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        int slices=0;
        boolean complete=false;
        while(!complete) {
            int before=session.expansions();
            complete=session.advanceSlice(8);
            require(session.expansions()-before<=8,
                "role-profile resumption slice stays within its eight-unit work quantum");
            require(++slices<2000000,"role-profile crossing session makes bounded progress");
        }
        PcbLayerRoutingPrototype.Result sliced=session.result();
        PcbLayerRoutingPrototype.Result blocking=PcbLayerRoutingPrototype.route(
            blockingFixture.board,blockingFixture.layout,
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER);
        require(slices>1,"role-profile route actually resumes across multiple small work slices");
        require(sliced.accepted() && blocking.accepted(),
            "sliced and blocking role-profile crossing runs both produce accepted routes");
        require(sliced.outcome.equals(blocking.outcome) &&
            sliced.expansions==blocking.expansions && sliced.orderings==blocking.orderings &&
            sliced.vias==blocking.vias && sliced.links==blocking.links,
            "role-profile session preserves blocking route outcome and accounting");
        require(original.equals(slicedFixture.layout.geometryFingerprint()) &&
            original.equals(blockingFixture.layout.geometryFingerprint()),
            "role-profile P07 runs leave both source placements unchanged");
        if(sliced.accepted() && blocking.accepted()) {
            sliced.layout.validateRoutingGeometry(slicedFixture.board);
            blocking.layout.validateRoutingGeometry(blockingFixture.board);
            require(sliced.layout.traceGeometryFingerprint().equals(
                blocking.layout.traceGeometryFingerprint()),
                "sliced and blocking role-profile routes retain the exact trace fingerprint");
            require(sliced.layout.captureConductorGraph(slicedFixture.board).toCanonical().equals(
                blocking.layout.captureConductorGraph(blockingFixture.board).toCanonical()),
                "sliced and blocking role-profile routes retain exact canonical copper identity");
            requireWideCrossingConnectivity(sliced.layout,slicedFixture.board,"sliced P07");
        }
    }

    private static RoleCrossingFixture typedCrossingFixture() {
        P06FactoryLinkFixtures.Fixture fixture=
            new P06FactoryLinkFixtures.Fixture(true,0,0,true);
        return new RoleCrossingFixture(typedCopy(fixture.board),fixture.layout);
    }

    private static void requireWideCrossingConnectivity(PcbBoardLayout layout,
            TroubleshootBoard board,String label) {
        boolean wideTop=false,wideBottom=false,overTrace=false,underTrace=false;
        boolean highStart=false,highEnd=false,underStart=false,underEnd=false;
        for(PcbTraceGeometry trace:layout.getTraces()) {
            BoardNet net=board.getNet(trace.getNetId());
            require(trace.getWidth()==PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(net.getRoutingRole()),
                label+" copper has the declared width for "+trace.getNetId()+" on "+trace.getLayer());
            if(net.getRoutingRole()==BoardNet.RoutingRole.HIGH_CURRENT) {
                require(trace.getWidth()==13,label+" crossing high-current copper has width13");
                wideTop|=trace.getLayer()==PcbCopperLayer.TOP;
                wideBottom|=trace.getLayer()==PcbCopperLayer.BOTTOM;
            }
            if(trace.getStartPadId()!=null) {
                require(board.getPad(trace.getStartPadId())!=null &&
                    board.getPad(trace.getStartPadId()).getNetId().equals(trace.getNetId()),
                    label+" published terminal start retains its exact electrical pad");
                highStart|="OVER".equals(trace.getNetId());
                underStart|="UNDER".equals(trace.getNetId());
            }
            if(trace.getEndPadId()!=null) {
                require(board.getPad(trace.getEndPadId())!=null &&
                    board.getPad(trace.getEndPadId()).getNetId().equals(trace.getNetId()),
                    label+" published terminal end retains its exact electrical pad");
                highEnd|="OVER".equals(trace.getNetId());
                underEnd|="UNDER".equals(trace.getNetId());
            }
            if("OVER".equals(trace.getNetId())) {
                overTrace=true;
            }
            if("UNDER".equals(trace.getNetId())) underTrace=true;
        }
        require(wideTop && wideBottom,
            label+" crossing uses actual width13 copper on both faces in aggregate");
        require(overTrace && underTrace,label+" crossing retains both independent net copper routes");
        require(highStart && highEnd && underStart && underEnd,
            label+" via-split copper retains both true terminal pad identities while internal cuts stay padless");
        PcbConductorGraph.Snapshot copper=layout.captureConductorGraph(board).pristine();
        copper.requirePristineNetConnectivity(board);
        require(copper.padsConnected("L.1","R.1") && copper.padsConnected("A.1","B.1") &&
            !copper.padsConnected("L.1","A.1"),
            label+" width-aware copper preserves endpoint nets without a false cross-net join");
    }

    private static RouteFixture parallelFixture(PcbTraceWidthProfile profile) {
        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_PARALLEL_"+profile.getIdentity());
        board.addNet(new BoardNet("HIGH",BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("SIGNAL",BoardNet.RoutingRole.SIGNAL));
        PcbBoardLayout layout=new PcbBoardLayout(900,650,new Rectangle(100,100,420,400),
            new Rectangle(650,120,200,400));
        addPoint(board,layout,"H1","HIGH",140,200,PcbRotation.DEG_180);
        addPoint(board,layout,"H2","HIGH",460,200,PcbRotation.DEG_0);
        addPoint(board,layout,"S1","SIGNAL",140,400,PcbRotation.DEG_180);
        addPoint(board,layout,"S2","SIGNAL",460,400,PcbRotation.DEG_0);
        setProfile(board,profile);
        board.validate();
        layout.validateAgainst(board);
        return new RouteFixture(board,layout);
    }

    private static void addPoint(TroubleshootBoard board,PcbBoardLayout layout,String id,
            String net,int x,int y,PcbRotation rotation) {
        PhysicalPackage pkg=P06FactoryLinkFixtures.TEST_POINT;
        PcbPackagePose reference=new PcbPackagePose(100,100,rotation,PcbBoardSide.BOTTOM);
        Point local=pkg.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose=new PcbPackagePose(100+x-local.x,100+y-local.y,rotation,
            PcbBoardSide.BOTTOM);
        BoardComponent component=new BoardComponent(id,pkg.getId(),pkg,id);
        board.addComponent(component);
        board.addPad(new BoardPad(id+".1",id,"1",net));
        PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,pkg.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for(PcbPadPlacement pad:footprint.getPads()) layout.addPad(pad);
    }

    private static void setProfile(TroubleshootBoard board,PcbTraceWidthProfile profile) {
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        Vector<String> componentIds=board.getComponentIds();
        Collections.sort(componentIds);
        for(String id:componentIds)
            parts.add(new PcbPlacementConstraints.Part(id,"route-test","Route test","board",
                PcbPlacementConstraints.Anchor.NONE,20));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(),PcbCopperLayer.TOP,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,1,false,profile));
    }

    private static PcbNetRouter.Router padLandRouter(PcbTraceWidthProfile profile,PcbCopperLayer face) {
        TroubleshootBoard board=new TroubleshootBoard("ROLE_WIDTH_PAD_LAND_"+profile.getIdentity());
        board.addNet(new BoardNet("HIGH",BoardNet.RoutingRole.HIGH_CURRENT));
        board.addNet(new BoardNet("SIGNAL",BoardNet.RoutingRole.SIGNAL));
        PhysicalPackage pkg=PhysicalPackage.developerPackageWithGenericGeometry("ROLE_WIDTH_PAD",
            singleton("1"),new Vector<String>(),false);
        addBarePad(board,"H","HIGH",pkg);
        addBarePad(board,"S1","SIGNAL",pkg);
        addBarePad(board,"S2","SIGNAL",pkg);
        setProfile(board,profile);
        board.validate();
        PcbBoardLayout layout=new PcbBoardLayout(600,450,new Rectangle(100,100,400,300),
            new Rectangle(520,120,60,250));
        layout.addPad(manualPad("H.1",250,240));
        layout.addPad(manualPad("S1.1",200,220));
        layout.addPad(manualPad("S2.1",300,220));
        return new PcbNetRouter.Router(layout,board,layout.getBoardOutline(),0,
            P06FactoryLinkFixtures.OBSERVER,face);
    }

    private static void addBarePad(TroubleshootBoard board,String componentId,String net,
            PhysicalPackage pkg) {
        board.addComponent(new BoardComponent(componentId,pkg.getId(),pkg,componentId));
        board.addPad(new BoardPad(componentId+".1",componentId,"1",net));
    }

    private static PcbPadPlacement manualPad(String id,int x,int y) {
        return new PcbPadPlacement(id,x,y,0,0,0,new Rectangle(x-13,y-13,26,26),
            new Rectangle(x-17,y-17,34,34),PcbTerminalAttachment.PLATED_THROUGH_HOLE,
            PcbBoardSide.TOP);
    }

    private static TroubleshootBoard typedCopy(TroubleshootBoard source) {
        TroubleshootBoard board=new TroubleshootBoard(source.getId()+"_ROLE_WIDTH");
        Vector<String> nets=source.getNetIds();
        Collections.sort(nets);
        for(String id:nets) {
            BoardNet.RoutingRole role="GND".equals(id)?BoardNet.RoutingRole.RETURN:
                BoardNet.RoutingRole.HIGH_CURRENT;
            board.addNet(new BoardNet(id,role));
        }
        Vector<String> components=source.getComponentIds();
        Collections.sort(components);
        for(String id:components) {
            BoardComponent component=source.getComponent(id);
            board.addComponent(new BoardComponent(id,component.getType(),
                component.getPhysicalPackage(),component.getDisplayName()));
        }
        Vector<String> pads=source.getPadIds();
        Collections.sort(pads);
        for(String id:pads) {
            BoardPad pad=source.getPad(id);
            board.addPad(new BoardPad(id,pad.getComponentId(),pad.getTerminalId(),pad.getNetId()));
        }
        PcbPlacementConstraints constraints=source.getPlacementConstraints();
        Vector<PcbPlacementConstraints.Part> parts=new Vector<PcbPlacementConstraints.Part>();
        for(PcbPlacementConstraints.Part part:constraints.getParts())
            parts.add(new PcbPlacementConstraints.Part(part.componentId,part.regionId,
                part.regionLabel,part.domainId,part.anchor,part.accessMargin,
                part.getTerminalDomains()));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,constraints.getBarriers(),
            constraints.routingLayer,constraints.getPhysicalPolicyId(),
            constraints.getPhysicalPolicyVersion(),constraints.placementDensityVersion,
            constraints.requiresTwoLayerRouting,PcbTraceWidthProfile.ROLE_WIDTHS));
        Vector<String> inputs=source.getPowerInputIds();
        Collections.sort(inputs);
        for(String id:inputs) {
            ExternalBoardPowerInput input=source.getPowerInput(id);
            board.addPowerInput(new ExternalBoardPowerInput(input.getId(),input.getPositivePadId(),
                input.getReturnPadId(),input.getPositiveNetId(),input.getReturnNetId()));
        }
        board.validate();
        return board;
    }

    private static void requireRoleWidthsAndEndpoints(RouteFixture fixture) {
        boolean high=false,signal=false;
        for(PcbTraceGeometry trace:fixture.layout.getTraces()) {
            BoardNet net=fixture.board.getNet(trace.getNetId());
            require(trace.getWidth()==PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(net.getRoutingRole()),
                "P05 routed width follows "+net.getRoutingRole());
            require(trace.getStartPadId()!=null && trace.getEndPadId()!=null &&
                fixture.board.getPad(trace.getStartPadId()).getNetId().equals(trace.getNetId()) &&
                fixture.board.getPad(trace.getEndPadId()).getNetId().equals(trace.getNetId()),
                "P05 trace endpoints retain their authoritative pads");
            high|="HIGH".equals(trace.getNetId()) && trace.getWidth()==13;
            signal|="SIGNAL".equals(trace.getNetId()) && trace.getWidth()==9;
        }
        require(high && signal,"P05 publishes actual 13-unit power and 9-unit signal traces");
    }

    private static String pathSignature(PcbBoardLayout layout) {
        Vector<PcbTraceGeometry> traces=layout.getTraces();
        Collections.sort(traces,new java.util.Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry a,PcbTraceGeometry b) {
                return a.getSourceId().compareTo(b.getSourceId());
            }
        });
        StringBuilder out=new StringBuilder();
        for(PcbTraceGeometry trace:traces)
            out.append(trace.getSourceId()).append(':').append(trace.getNetId()).append(':')
                .append(trace.getLayer()).append(':').append(trace.getStartPadId()).append('-')
                .append(trace.getEndPadId()).append(':').append(Arrays.toString(trace.getXPoints()))
                .append(':').append(Arrays.toString(trace.getYPoints())).append(';');
        return out.toString();
    }

    private static String placementSignature(PcbBoardLayout layout) {
        StringBuilder out=new StringBuilder(layout.componentGeometryFingerprint());
        Vector<PcbPadPlacement> pads=layout.getPads();
        Collections.sort(pads,new java.util.Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                return a.getPadId().compareTo(b.getPadId());
            }
        });
        for(PcbPadPlacement pad:pads) out.append(pad.geometryFingerprint()).append(';');
        Vector<PcbSilkscreenLabel> labels=layout.getSilkscreenLabels();
        Collections.sort(labels,new java.util.Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel a,PcbSilkscreenLabel b) {
                return a.getId().compareTo(b.getId());
            }
        });
        for(PcbSilkscreenLabel label:labels) {
            Rectangle bounds=label.getBounds();
            out.append(label.getId()).append(':').append(label.getText()).append('@')
                .append(bounds.x).append(',').append(bounds.y).append(',')
                .append(bounds.width).append(',').append(bounds.height).append(';');
        }
        return out.toString();
    }

    private static Vector<String> singleton(String value) {
        Vector<String> result=new Vector<String>();
        result.add(value);
        return result;
    }

    private static void require(boolean condition,String message) {
        assertions++;
        if(!condition) throw new AssertionError(message);
    }

    static final class GoalBoundaryMeasurement {
        final int width;
        final Rectangle courtyard,clearStroke,overlapStroke;
        final boolean clearPhysicalOverlap,clearSearchFreeCell,clearSearchGoal;
        final boolean overlapPhysical,overlapSearchGoal;
        GoalBoundaryMeasurement(int width,Rectangle courtyard,Rectangle clearStroke,
                Rectangle overlapStroke,boolean clearPhysicalOverlap,boolean clearSearchFreeCell,
                boolean clearSearchGoal,boolean overlapPhysical,boolean overlapSearchGoal) {
            this.width=width; this.courtyard=courtyard; this.clearStroke=clearStroke;
            this.overlapStroke=overlapStroke; this.clearPhysicalOverlap=clearPhysicalOverlap;
            this.clearSearchFreeCell=clearSearchFreeCell;
            this.clearSearchGoal=clearSearchGoal; this.overlapPhysical=overlapPhysical;
            this.overlapSearchGoal=overlapSearchGoal;
        }
    }

    private static final class RouteFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        RouteFixture(TroubleshootBoard board,PcbBoardLayout layout) {
            this.board=board; this.layout=layout;
        }
    }

    private static final class RoleCrossingFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        RoleCrossingFixture(TroubleshootBoard board,PcbBoardLayout layout) {
            this.board=board; this.layout=layout;
        }
    }
}
