package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.TreeMap;
import java.util.Vector;

/** Actual mediumPlace loop contracts for policy4's paired pose evaluation. */
public final class PcbLocalPoseChoiceContractTest {
    private static int assertions;
    private static final int POLICY = PcbPassivePosePolicy.LOCAL_POSE_CHOICE;
    private static final int RING_EVALUATIONS = 252;
    private static final Rectangle LARGE_OUTLINE = new Rectangle(20, 20, 1800, 1800);
    private static final int ACCESS_MARGIN = 16;

    private PcbLocalPoseChoiceContractTest() { }

    public static void main(String[] args) throws Exception {
        advisoryOnlyNinetyFit();
        hashedFallbackOnlyNinetyFit();
        sourceWinsByWholeContentScore();
        equalScoreKeepsFirstSourcePose();
        budgetRejectsBeforeSecondPose();
        System.out.println("PASS: PCB local pose choice contracts assertions=" +
            assertions + " fixtures=5 evaluator=mediumPlace");
    }

    private static void advisoryOnlyNinetyFit() throws Exception {
        Fixture fixture = advisoryFixture();
        Vector<Point> vacancies=advisoryVacancies(fixture);
        check(vacancies.size()>4 && vacancies.get(4).x==240 && vacancies.get(4).y==345,
            "canonical source-envelope vacancy target supplies the expected raw slot");
        final boolean[] sourceFailedAtSlot=new boolean[] {false};
        final boolean[] alternativeFitAtSlot=new boolean[] {false};
        final boolean[] alternativeScoredAtSlot=new boolean[] {false};
        PcbPlacementPlanner.setLocalPoseEvaluationObserver(
            new PcbPlacementPlanner.LocalPoseEvaluationObserver() {
                public void evaluated(int rawX,int rawY,int actualX,int actualY,
                        PcbRotation rotation,boolean fits,double score) {
                    if(rawX!=240 || rawY!=345)return;
                    if(rotation==PcbRotation.DEG_0 && actualX==240 && actualY==350 &&
                            !fits && Double.isNaN(score))sourceFailedAtSlot[0]=true;
                    if(rotation==PcbRotation.DEG_90 && actualX==240 && actualY==350 &&
                            fits) {
                        alternativeFitAtSlot[0]=true;
                        alternativeScoredAtSlot[0]=!Double.isNaN(score) &&
                            !Double.isInfinite(score);
                    }
                }
            });
        try {
            PcbFootprint selected=place(fixture,false,0);
            check(selected!=null,"advisory fixture returns a real mediumPlace choice");
        } finally {
            PcbPlacementPlanner.setLocalPoseEvaluationObserver(null);
        }
        check(sourceFailedAtSlot[0],
            "actual mediumPlace loop evaluates TOP0 and records the advisory-slot fit failure");
        check(alternativeFitAtSlot[0] && alternativeScoredAtSlot[0],
            "actual mediumPlace loop fits and scores TOP90 at the same advisory raw slot");
    }

    private static void hashedFallbackOnlyNinetyFit() throws Exception {
        Fixture fixture = fallbackFixture();
        PcbFootprint selected = place(fixture, false, 0);
        check(selected != null && selected.getPlacement().getRotation() ==
                PcbRotation.DEG_90,
            "hashed fallback finds the only fitting canonical TOP90 pose");
        check(selected.getPlacement().getX()==60 && selected.getPlacement().getY()==5010,
            "seeded fallback selects the literal first legal paired origin");
        check(fixture.evaluations[0]==260,
            "paired fallback visits the first hash escape after 252 ring pose checks");
    }

    private static void sourceWinsByWholeContentScore() throws Exception {
        Fixture fixture = sourceWinFixture();
        final double[] bestScores=new double[] {Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY};
        PcbPlacementPlanner.setLocalPoseEvaluationObserver(
            new PcbPlacementPlanner.LocalPoseEvaluationObserver() {
                public void evaluated(int rawX,int rawY,int actualX,int actualY,
                        PcbRotation rotation,boolean fits,double score) {
                    if(!fits || Double.isNaN(score) || Double.isInfinite(score))return;
                    int index=rotation==PcbRotation.DEG_0?0:1;
                    bestScores[index]=Math.min(bestScores[index],score);
                }
            });
        PcbFootprint selected;
        try {
            selected=place(fixture,true,0);
        } finally {
            PcbPlacementPlanner.setLocalPoseEvaluationObserver(null);
        }
        check(selected != null && selected.getPlacement().getRotation() ==
                PcbRotation.DEG_0,
            "real whole-content score selects the source pose when it adds less area");
        double selectedScore=actualScore(selected,fixture,true);
        check(!Double.isNaN(bestScores[0]) && !Double.isInfinite(bestScores[0]) &&
                !Double.isNaN(bestScores[1]) && !Double.isInfinite(bestScores[1]),
            "actual evaluation trace contains legal finite scores for both poses");
        check(bestScores[0]<bestScores[1],
            "actual whole-content scores give TOP0 a lower legal score than TOP90");
        PcbFootprint sourceAtDeclaredOrigin=fixture.source.translated(
            clampedOrigin(fixture.source,930,fixture),
            clampedOriginY(fixture.source,1100,fixture));
        PcbFootprint alternativeAtDeclaredOrigin=fixture.alternative.translated(
            clampedOrigin(fixture.alternative,930,fixture),
            clampedOriginY(fixture.alternative,1100,fixture));
        check(fits(sourceAtDeclaredOrigin,fixture) &&
                fits(alternativeAtDeclaredOrigin,fixture),
            "both poses fit at the declared source-win comparison origin");
        check(actualScore(sourceAtDeclaredOrigin,fixture,true)<
                actualScore(alternativeAtDeclaredOrigin,fixture,true),
            "actual scores favor TOP0 at the declared raw origin (930,1100)");
        check(selectedScore==bestScores[0],
            "mediumPlace returns the TOP0 trial with the minimum observed actual score");
        check(fixture.evaluations[0] == RING_EVALUATIONS,
            "source score winner uses the unchanged paired ring origins");
    }

    private static void equalScoreKeepsFirstSourcePose() throws Exception {
        Fixture fixture = remotePackageFixture();
        PcbFootprint selected = place(fixture, false, 0);
        // Independent literal first source ring slot for LARGE_OUTLINE is (930,890).
        Point first=new Point(930,890);
        check(selected != null && selected.getPlacement().getRotation() ==
                PcbRotation.DEG_0,
            "strict score comparison keeps TOP0 when both actual scores tie");
        check(selected.getPlacement().getX()==first.x &&
                selected.getPlacement().getY()==first.y,
            "equal-score tie keeps TOP0 at the first ring origin");
        check(actualScore(fixture.source.translated(first.x,first.y),fixture,false)==
                actualScore(fixture.alternative.translated(first.x,first.y),fixture,false),
            "actual mediumPlacementScore values tie at the expected first origin");
        check(fixture.evaluations[0] == RING_EVALUATIONS,
            "equal-score check evaluated TOP0 and TOP90 at every old ring origin");
    }

    private static void budgetRejectsBeforeSecondPose() throws Exception {
        Fixture fixture = remotePackageFixture();
        final Vector<PcbRotation> observed=new Vector<PcbRotation>();
        PcbPlacementPlanner.setLocalPoseEvaluationObserver(
            new PcbPlacementPlanner.LocalPoseEvaluationObserver() {
                public void evaluated(int rawX,int rawY,int actualX,int actualY,
                        PcbRotation rotation,boolean fits,double score) {
                    observed.add(rotation);
                }
            });
        try {
            place(fixture, false, 59999);
            throw new AssertionError("budget boundary accepted a partial pose pair");
        } catch (PcbPlacementPlanner.Rejected rejected) {
            check("MEDIUM_PLACEMENT_BUDGET".equals(rejected.reason),
                "unavailable second pose rejects with the existing medium budget reason");
        } finally {
            PcbPlacementPlanner.setLocalPoseEvaluationObserver(null);
        }
        check(fixture.evaluations[0] == 60000,
            "TOP0 consumes the final evaluation before TOP90 is rejected");
        check(observed.size()==1 && observed.get(0)==PcbRotation.DEG_0,
            "budget rejection observes exactly the final TOP0 evaluation and no TOP90 fit/score");
    }

    private static PcbFootprint place(Fixture fixture, boolean scoreWholeContent,
            long initialCount) throws Exception {
        fixture.evaluations[0] = (int)initialCount;
        Method method = mediumPlaceMethod();
        try {
            return (PcbFootprint)method.invoke(null, fixture.item, fixture.placed,
                fixture.topology, new TreeMap<String,Object>(), fixture.board,
                fixture.constraints, fixture.outline, 0L, 0, Boolean.FALSE,
                Boolean.FALSE, Boolean.FALSE, scoreWholeContent, Boolean.FALSE,
                Boolean.TRUE, Boolean.FALSE, -1, fixture.evaluations);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw failure;
        }
    }

    private static boolean fits(PcbFootprint footprint,Fixture fixture) throws Exception {
        Method method=PcbPlacementPlanner.class.getDeclaredMethod("fits",PcbFootprint.class,
            Rectangle.class,Vector.class,Integer.TYPE,PcbPlacementConstraints.class);
        method.setAccessible(true);
        return ((Boolean)method.invoke(null,footprint,fixture.outline,fixture.placed,-1,
            fixture.constraints)).booleanValue();
    }

    private static double actualScore(PcbFootprint footprint,Fixture fixture,
            boolean scoreWholeContent) throws Exception {
        Method method=PcbPlacementPlanner.class.getDeclaredMethod("mediumPlacementScore",
            PcbFootprint.class,Class.forName(PcbPlacementPlanner.class.getName()+"$Item"),
            Vector.class,TopologyPlacementGraph.class,TroubleshootBoard.class,
            Class.forName(PcbPlacementPlanner.class.getName()+"$RegionHint"),
            Rectangle.class,Boolean.TYPE,Boolean.TYPE,Rectangle.class);
        method.setAccessible(true);
        return ((Double)method.invoke(null,footprint,fixture.item,fixture.placed,
            fixture.topology,fixture.board,null,fixture.outline,Boolean.FALSE,
            Boolean.valueOf(scoreWholeContent),null)).doubleValue();
    }

    private static int clampedOrigin(PcbFootprint template,int x,Fixture fixture)
            throws Exception {
        return gridOrigin(template,x,fixture,false);
    }

    private static int clampedOriginY(PcbFootprint template,int y,Fixture fixture)
            throws Exception {
        return gridOrigin(template,y,fixture,true);
    }

    private static int gridOrigin(PcbFootprint template,int origin,Fixture fixture,
            boolean vertical) throws Exception {
        Method method=PcbPlacementPlanner.class.getDeclaredMethod("mediumGridOrigin",
            PcbFootprint.class,Integer.TYPE,Rectangle.class,Integer.TYPE,Boolean.TYPE);
        method.setAccessible(true);
        return ((Integer)method.invoke(null,template,origin,fixture.outline,
            fixture.constraints.get(template.getPlacement().getComponentId()).accessMargin,
            Boolean.valueOf(vertical))).intValue();
    }

    private static Method mediumPlaceMethod() throws Exception {
        Class<?> itemType = Class.forName(PcbPlacementPlanner.class.getName() + "$Item");
        Method method = PcbPlacementPlanner.class.getDeclaredMethod("mediumPlace",
            itemType, Vector.class, TopologyPlacementGraph.class, TreeMap.class,
            TroubleshootBoard.class, PcbPlacementConstraints.class, Rectangle.class,
            Long.TYPE, Integer.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE,
            Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, Boolean.TYPE, Integer.TYPE,
            int[].class);
        method.setAccessible(true);
        return method;
    }

    private static Fixture remotePackageFixture() throws Exception {
        BoardBuilder builder = new BoardBuilder("LOCAL_POSE_REMOTE_SCORE");
        BoardComponent target = builder.component("R", "OTHER", PhysicalPackages.AXIAL_RESISTOR,
            "PRIMARY", "candidate", ACCESS_MARGIN);
        builder.terminals(target, "R-N1", "R-N2");
        BoardComponent remote = builder.component("CTRL", "CONTROL",
            PhysicalPackages.E04_DECISION_CONTROL_5, "SECONDARY", "remote");
        builder.terminals(remote, "CTRL-N1", "CTRL-N2", "CTRL-N3", "CTRL-N4", "CTRL-N5");
        BoardComponent connector = builder.component("J", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, "PRIMARY", "connector");
        builder.terminals(connector, "J-N1", "J-N2");
        Fixture fixture = builder.finish(new PcbPlacementConstraints.Barrier(
            "PRIMARY", "SECONDARY", 80), LARGE_OUTLINE, "R");
        fixture.placed.add(PcbFootprint.fromPhysicalPackage(remote,
            new PcbPackagePose(790, 200, PcbRotation.DEG_0, PcbBoardSide.TOP),
            remote.getPhysicalPackage().getGeometry()));
        return fixture;
    }

    private static Fixture sourceWinFixture() throws Exception {
        BoardBuilder builder=new BoardBuilder("LOCAL_POSE_SOURCE_SCORE");
        BoardComponent target=builder.component("R","OTHER",PhysicalPackages.AXIAL_RESISTOR,
            "PRIMARY","candidate");
        builder.terminals(target,"R-N1","R-N2");
        BoardComponent connector=builder.component("J","CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,"PRIMARY","connector");
        builder.terminals(connector,"J-N1","J-N2");
        Vector<BoardComponent> peers=new Vector<BoardComponent>();
        int[] x=new int[] {60,1400};
        for(int index=0;index<x.length;index++) {
            String id="C"+index;
            BoardComponent peer=builder.component(id,"OTHER",
                PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,"SECONDARY","remote");
            builder.terminals(peer,id+"-N1",id+"-N2");
            peers.add(peer);
        }
        Fixture fixture=builder.finish(new PcbPlacementConstraints.Barrier(
            "PRIMARY","SECONDARY",80),new Rectangle(20,20,1600,1800),"R");
        for(int index=0;index<peers.size();index++)
            fixture.placed.add(PcbFootprint.fromPhysicalPackage(peers.get(index),
                new PcbPackagePose(x[index],800,PcbRotation.DEG_0,PcbBoardSide.TOP),
                peers.get(index).getPhysicalPackage().getGeometry()));
        checkPeerEnvelopesAreLegal(fixture);
        return fixture;
    }

    private static Fixture fallbackFixture() throws Exception {
        Rectangle outline = new Rectangle(0, 0, 220, 6000);
        BoardBuilder builder = new BoardBuilder("LOCAL_POSE_HASHED_FALLBACK");
        BoardComponent target = builder.component("R", "OTHER", PhysicalPackages.AXIAL_RESISTOR,
            "PRIMARY", "candidate", ACCESS_MARGIN);
        builder.terminals(target, "R-N1", "R-N2");
        BoardComponent connector = builder.component("J", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, "PRIMARY", "connector", ACCESS_MARGIN);
        builder.terminals(connector, "J-N1", "J-N2");
        Vector<BoardComponent> caps = new Vector<BoardComponent>();
        for (int index = 0; index < 16; index++) {
            String id = "C" + index;
            BoardComponent cap = builder.component(id, "OTHER",
                PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR, "PRIMARY", "cap", ACCESS_MARGIN);
            builder.terminals(cap, id + "-N1", id + "-N2");
            caps.add(cap);
        }
        Fixture fixture = builder.finish(null, outline, "R");
        for (int index = 0; index < caps.size(); index++)
            fixture.placed.add(PcbFootprint.fromPhysicalPackage(caps.get(index),
                new PcbPackagePose(30, 1800 + 200 * index, PcbRotation.DEG_0,
                    PcbBoardSide.TOP), caps.get(index).getPhysicalPackage().getGeometry()));
        checkPeerEnvelopesAreLegal(fixture);
        return fixture;
    }

    private static Fixture advisoryFixture() throws Exception {
        Rectangle outline=new Rectangle(20,20,500,780);
        BoardBuilder builder=new BoardBuilder("LOCAL_POSE_ADVISORY");
        BoardComponent target=builder.component("R","OTHER",PhysicalPackages.AXIAL_RESISTOR,
            "PRIMARY","candidate");
        builder.terminals(target,"R-N1","R-N2");
        BoardComponent blocker=builder.component("B","OTHER",PhysicalPackages.AXIAL_RESISTOR,
            "PRIMARY","blocker");
        builder.terminals(blocker,"B-N1","B-N2");
        Fixture fixture=builder.finish(null,outline,"R");
        fixture.placed.add(PcbFootprint.fromPhysicalPackage(blocker,
            new PcbPackagePose(355,500,PcbRotation.DEG_90,PcbBoardSide.TOP),
            blocker.getPhysicalPackage().getGeometryVariant("SPAN_220").getGeometry()));
        checkPeerEnvelopesAreLegal(fixture);
        return fixture;
    }

    private static Vector<Point> advisoryVacancies(Fixture fixture) {
        Rectangle allowed=PcbPlacementPlanner.vacancyDomainBox(fixture.source,fixture.placed,
            fixture.constraints,fixture.outline);
        check(allowed!=null,"advisory fixture has a real source-domain vacancy box");
        Vector<Rectangle> obstacles=new Vector<Rectangle>();
        for(PcbFootprint peer:fixture.placed)
            obstacles.add(PcbPlacementPlanner.envelope(peer,
                fixture.constraints.get(peer.getPlacement().getComponentId()).accessMargin));
        return PcbVacancyTargets.targets(allowed,obstacles,
            PcbPlacementPlanner.envelope(fixture.source,
                fixture.constraints.get(fixture.source.getPlacement().getComponentId()).accessMargin),
            new Point(fixture.source.getPlacement().getX(),
                fixture.source.getPlacement().getY()));
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final Object item;
        final PcbFootprint source;
        final PcbFootprint alternative;
        final TopologyPlacementGraph topology;
        final Vector<PcbFootprint> placed = new Vector<PcbFootprint>();
        final Rectangle outline;
        final int[] evaluations = new int[] {0};

        Fixture(TroubleshootBoard board, PcbPlacementConstraints constraints,
                Object item,PcbFootprint source,PcbFootprint alternative,
                TopologyPlacementGraph topology, Rectangle outline) {
            this.board = board;
            this.constraints = constraints;
            this.item = item;
            this.source = source;
            this.alternative = alternative;
            this.topology = topology;
            this.outline = outline;
        }
    }

    private static final class BoardBuilder {
        final TroubleshootBoard board;
        final Vector<PcbPlacementConstraints.Part> declarations =
            new Vector<PcbPlacementConstraints.Part>();
        BoardBuilder(String id) { board = new TroubleshootBoard(id); }

        BoardComponent component(String id, String type, PhysicalPackage physicalPackage,
                String domain, String region) {
            return component(id,type,physicalPackage,domain,region,20);
        }

        BoardComponent component(String id, String type, PhysicalPackage physicalPackage,
                String domain, String region,int accessMargin) {
            BoardComponent component = new BoardComponent(id, type, physicalPackage, id);
            board.addComponent(component);
            declarations.add(new PcbPlacementConstraints.Part(id, region, region, domain,
                PcbPlacementConstraints.Anchor.NONE, accessMargin));
            return component;
        }

        void terminals(BoardComponent component, String... netIds) {
            Vector<String> terminalIds = component.getPhysicalPackage().getTerminalIds();
            if (terminalIds.size() != netIds.length)
                throw new IllegalArgumentException("Fixture terminal count differs from package");
            for (int index = 0; index < terminalIds.size(); index++) {
                String netId = netIds[index];
                board.addNet(new BoardNet(netId));
                String terminal = terminalIds.get(index);
                board.addPad(new BoardPad(component.getId() + "." + terminal,
                    component.getId(), terminal, netId));
            }
        }

        Fixture finish(PcbPlacementConstraints.Barrier barrier, Rectangle outline,
                String targetId) throws Exception {
            Vector<PcbPlacementConstraints.Barrier> barriers =
                new Vector<PcbPlacementConstraints.Barrier>();
            if (barrier != null) barriers.add(barrier);
            PcbPlacementConstraints constraints = new PcbPlacementConstraints(declarations,
                barriers, PcbCopperLayer.BOTTOM, MediumBoardPhysicalPolicy.ID,
                MediumBoardPhysicalPolicy.VERSION, 14, true,
                PcbTraceWidthProfile.ROLE_WIDTHS, POLICY);
            board.setPlacementConstraints(constraints);
            BoardComponent target = board.getComponent(targetId);
            PcbFootprint source = PcbFootprint.fromPhysicalPackage(target,
                new PcbPackagePose(0, 0, PcbRotation.DEG_0, PcbBoardSide.TOP),
                PhysicalPackages.AXIAL_RESISTOR.getGeometryVariant("SPAN_220").getGeometry());
            PcbPlacementConstraints.Part demand = constraints.get(targetId);
            Class<?> itemType = Class.forName(PcbPlacementPlanner.class.getName() + "$Item");
            Constructor<?> constructor = itemType.getDeclaredConstructor(PcbFootprint.class,
                PcbPlacementConstraints.Part.class, BoardComponent.class, Integer.TYPE,
                Boolean.TYPE);
            constructor.setAccessible(true);
            Object item = constructor.newInstance(source, demand, target, POLICY, Boolean.TRUE);
            PcbFootprint alternative=PcbPassivePosePolicy.alternativeFor(target,source,
                POLICY,true);
            TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
            return new Fixture(board,constraints,item,source,alternative,topology,outline);
        }
    }

    private static void checkPeerEnvelopesAreLegal(Fixture fixture) {
        for(int index=0;index<fixture.placed.size();index++) {
            PcbFootprint peer=fixture.placed.get(index);
            PcbPlacementConstraints.Part demand=fixture.constraints.get(
                peer.getPlacement().getComponentId());
            Rectangle envelope=PcbPlacementPlanner.envelope(peer,demand.accessMargin);
            check(envelope.x>=fixture.outline.x+10 && envelope.y>=fixture.outline.y+10 &&
                    envelope.x+envelope.width<=fixture.outline.x+fixture.outline.width-10 &&
                    envelope.y+envelope.height<=fixture.outline.y+fixture.outline.height-10,
                "fallback peer envelope lies inside the board outline");
            for(int previous=0;previous<index;previous++) {
                PcbFootprint other=fixture.placed.get(previous);
                PcbPlacementConstraints.Part otherDemand=fixture.constraints.get(
                    other.getPlacement().getComponentId());
                check(!envelope.intersects(PcbPlacementPlanner.envelope(other,
                        otherDemand.accessMargin)),
                    "fallback peer envelopes do not overlap");
            }
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
