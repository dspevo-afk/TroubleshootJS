package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Native placement-only contracts for private21 candidate-2 local pocket descent.
 * The plans are unrouted; the existing package, pad-access and domain validators
 * remain authoritative.
 */
public final class PcbLocalPocketDescentContractTest {
    private static final long[] RAW_ROOTS = {-1017L, 42L, 4L};
    private static final int[] PRESERVED_CANDIDATES = {0, 1, 3, 4, 5};
    private static int assertions;
    private static int fixtures;

    private PcbLocalPocketDescentContractTest() { }

    public static void main(String[] args) {
        verifyVersionAndReplayIdentity();
        for (long seed : RAW_ROOTS) verifyRawRoot(seed);
        verifyBudgetAt59999();
        verifyBudgetAt60000();
        verifyIsolationEligibility();
        verifyPolicySelectionGuard();
        verifyFourPartMove();
        verifyLowerScoreAccessEnvelopeRejected();
        verifyEqualScoreRetainsIncumbent();
        System.out.println("PASS: PCB local pocket descent contracts assertions=" + assertions +
            " fixtures=" + fixtures + " evaluator=localPocketDescent");
    }

    private static void verifyVersionAndReplayIdentity() {
        check(Rb56Placement.CURRENT_VERSION == 15 &&
                Rb56Placement.densityVersion(Rb56Placement.CURRENT_VERSION) == 14,
            "private21 leaves current private15 density unchanged");
        check(Rb56Placement.supportsVersion(Rb56Placement.LOCAL_POCKET_VERSION21) &&
                Rb56Placement.densityVersion(Rb56Placement.LOCAL_POCKET_VERSION21) == 15,
            "private21 maps to density15");
        check(!Rb56Placement.supportsVersion(20), "private20 remains unsupported");
        check("ROLE_TRACE@1".equals(Rb56Placement.widthProfile(
                Rb56Placement.LOCAL_POCKET_VERSION21).getIdentity()),
            "private21 keeps ROLE_TRACE@1");

        String expected = "tsj-private-session/21/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-21/RB56_CONTROL/-9223372036854775808";
        PlayerSessionReplay parsed = PlayerSessionReplay.parse(expected);
        check(expected.equals(parsed.canonical()) && parsed.isPrivateQualification() &&
                parsed.rb56PlacementInterpretationVersion() ==
                    Rb56Placement.LOCAL_POCKET_VERSION21 &&
                parsed.seed() == Long.MIN_VALUE,
            "private21 replay round-trips the exact signed-long seed");
        rejectPrivateReplay("tsj-private-session/20/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-20/RB56_CONTROL/1", "private20 replay is unsupported");
    }

    private static void verifyRawRoot(long seed) {
        fixtures++;
        Rb56Plan current = Rb56Plan.resolve(seed, Rb56Placement.CURRENT_VERSION);
        Rb56Plan trial = Rb56Plan.resolve(seed, Rb56Placement.LOCAL_POCKET_VERSION21);
        check(current.seed == seed && trial.seed == seed &&
                current.placementInterpretationVersion == 15 &&
                trial.placementInterpretationVersion == 21,
            "raw root retains source seed and exact identities: " + seed);
        check(sourceIntent(current).equals(sourceIntent(trial)),
            "private21 keeps ordered source intent: " + seed);

        TroubleshootBoard currentBoard = current.board();
        TroubleshootBoard trialBoard = trial.board();
        PcbPlacementConstraints currentConstraints = currentBoard.getPlacementConstraints();
        PcbPlacementConstraints trialConstraints = trialBoard.getPlacementConstraints();
        check(currentConstraints.placementDensityVersion == 14 &&
                trialConstraints.placementDensityVersion == 15,
            "raw root uses density14 versus density15: " + seed);
        check("ROLE_TRACE@1".equals(currentConstraints.getTraceWidthProfile().getIdentity()) &&
                "ROLE_TRACE@1".equals(trialConstraints.getTraceWidthProfile().getIdentity()) &&
                trialConstraints.requiresTwoLayerRouting,
            "raw root retains role widths and required two-layer policy: " + seed);
        String currentGraph = PcbCanvasBoundaryContractTest.liveGraphSha256(currentBoard);
        String trialGraph = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        check(currentGraph.equals(trialGraph),
            "raw root preserves net, pad and component identity: " + seed);
        String currentIntentBefore = sourceIntent(current);
        String trialIntentBefore = sourceIntent(trial);

        PcbPlacementPlanner planner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        for (int candidate : PRESERVED_CANDIDATES) {
            PcbPlacementPlanner.Plan before = planner.plan(currentBoard,currentConstraints,
                current.layoutSeed,candidate);
            PcbPlacementPlanner.Plan after = planner.plan(trialBoard,trialConstraints,
                trial.layoutSeed,candidate);
            PcbPlacementPlanner.validate(currentBoard,currentConstraints,before.outline,
                before.footprints);
            PcbPlacementPlanner.validate(trialBoard,trialConstraints,after.outline,
                after.footprints);
            check(planFingerprint(before).equals(planFingerprint(after)),
                "raw private15/private21 candidate " + candidate + " is exact: " + seed);
        }

        PcbPlacementPlanner.Plan incumbent = planner.plan(currentBoard,currentConstraints,
            current.layoutSeed,2);
        String incumbentFingerprint = planFingerprint(incumbent);
        PcbPlacementPlanner.Plan direct = PcbPlacementPlanner.relaxMediumPockets(
            trialBoard,trialConstraints,incumbent);
        PcbPlacementPlanner.Plan repeated = PcbPlacementPlanner.relaxMediumPockets(
            trialBoard,trialConstraints,incumbent);
        PcbPlacementPlanner.Plan selected = planner.plan(trialBoard,trialConstraints,
            trial.layoutSeed,2);
        PcbPlacementPlanner.validate(trialBoard,trialConstraints,direct.outline,direct.footprints);
        check(planFingerprint(direct).equals(planFingerprint(repeated)) &&
                planFingerprint(direct).equals(planFingerprint(selected)),
            "candidate2 seam and deterministic replay match helper: " + seed);
        check(direct.candidate == incumbent.candidate &&
                direct.courtyardArea == incumbent.courtyardArea &&
                direct.demandArea == incumbent.demandArea &&
                sameRectangle(direct.outline,incumbent.outline),
            "candidate2 descent preserves candidate metadata and outline: " + seed);
        int eligible = eligibleCount(incumbent,trialConstraints);
        int added = direct.evaluations - incumbent.evaluations;
        check(eligible > 0 && added >= 0 && added <= eligible * 5 &&
                direct.evaluations <= 60000,
            "candidate2 trials count at most five snapped origins per eligible passive: " + seed);
        check(incumbentFingerprint.equals(planFingerprint(incumbent)),
            "candidate2 helper leaves incumbent unchanged: " + seed);
        assertRigidPoseIdentity(incumbent,direct,"raw root " + seed);
        check(currentIntentBefore.equals(sourceIntent(current)) &&
                trialIntentBefore.equals(sourceIntent(trial)) &&
                currentGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(currentBoard)) &&
                trialGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),
            "candidate planning preserves source intent and live graph: " + seed);
    }

    private static void verifyBudgetAt59999() {
        fixtures++;
        Fixture fixture = fourPartFixture(true);
        PcbPlacementPlanner.Plan input = withEvaluations(fixture.plan,59999);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,input.outline,input.footprints);
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,input);
        check(output.evaluations == 60000,
            "one snapped trial fills the last available evaluation at 59999");
        check(output.candidate == 2 && output.evaluations <= 60000,
            "budget stop retains legal candidate2 without an n+1 rejection");
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,output.outline,output.footprints);
        assertRigidPoseIdentity(input,output,"budget 59999");
    }

    private static void verifyBudgetAt60000() {
        fixtures++;
        Fixture fixture = fourPartFixture(true);
        PcbPlacementPlanner.Plan input = withEvaluations(fixture.plan,60000);
        String before = planFingerprint(input);
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,input);
        check(output == input && output.evaluations == 60000 &&
                before.equals(planFingerprint(output)),
            "a plan at 60000 returns before local-pocket setup and stays untouched");
    }

    private static void verifyIsolationEligibility() {
        fixtures++;
        Fixture fixture = isolatedFixture();
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,fixture.plan.outline,
            fixture.plan.footprints);
        String before = planFingerprint(fixture.plan);
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,fixture.plan);
        check(output == fixture.plan && before.equals(planFingerprint(output)),
            "an isolation-body package is not admitted as a local passive");
    }

    private static void verifyPolicySelectionGuard() {
        fixtures++;
        Fixture fixture = fourPartFixture(false);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,fixture.plan.outline,
            fixture.plan.footprints);
        check(fixture.constraints.placementDensityVersion == 15 &&
                !MediumBoardPhysicalPolicy.selected(fixture.constraints),
            "policy guard is density15 outside the selected medium owner");
        String before = planFingerprint(fixture.plan);
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,fixture.plan);
        check(output == fixture.plan && before.equals(planFingerprint(output)),
            "local descent is a no-op outside the selected medium policy");
    }

    private static void verifyFourPartMove() {
        fixtures++;
        Fixture fixture = fourPartFixture(true);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,fixture.plan.outline,
            fixture.plan.footprints);
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(fixture.board);
        String before = planFingerprint(fixture.plan);
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,fixture.plan);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,output.outline,output.footprints);
        check(output.evaluations > fixture.plan.evaluations &&
                output.evaluations <= fixture.plan.evaluations + 5,
            "four-part fixture counts only bounded local origins");
        check(!before.equals(planFingerprint(output)),
            "four-part fixture accepts a passive move");
        check(output.candidate == 2 && sameRectangle(output.outline,fixture.plan.outline) &&
                output.courtyardArea == fixture.plan.courtyardArea &&
                output.demandArea == fixture.plan.demandArea,
            "local move leaves outline and demand metrics unchanged");
        int moved = assertRigidPoseIdentity(fixture.plan,output,"four-part positive fixture");
        check(moved == 1 && "R1".equals(movedComponentId(fixture.plan,output)),
            "only the unanchored axial passive moves; unanchored and anchored connectors plus the nonpassive stay fixed");
        check(independentLocalScore(output,"R1",fixture.constraints) <
                independentLocalScore(fixture.plan,"R1",fixture.constraints),
            "accepted stock passive move lowers existing no-link score terms");
        check(graphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(fixture.board)),
            "local move preserves source net and pad identity");
    }

    private static void verifyEqualScoreRetainsIncumbent() {
        fixtures++;
        Fixture fixture = singlePassiveFixture();
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,fixture.plan.outline,
            fixture.plan.footprints);
        PcbFootprint incumbent = fixture.plan.footprints.firstElement();
        PcbFootprint alternativeFootprint = incumbent.translated(
            incumbent.getPlacement().getX()+300,incumbent.getPlacement().getY());
        Vector<PcbFootprint> alternative = new Vector<PcbFootprint>();
        alternative.add(alternativeFootprint);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,fixture.plan.outline,
            alternative);
        check(sameSinglePassiveScore(incumbent,alternativeFootprint),
            "a distinct legal one-passive origin has exactly equal score");
        check(new TopologyPlacementGraph(fixture.board).getLinksFor(
                incumbent.getPlacement().getComponentId()).isEmpty(),
            "tie fixture has no linked-pad term");
        PcbPlacementPlanner.Plan output = PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,fixture.plan);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,output.outline,
            output.footprints);
        check(samePlanGeometry(fixture.plan,output) &&
                output.footprints.firstElement().getPlacement().getX()==
                    incumbent.getPlacement().getX() &&
                output.footprints.firstElement().getPlacement().getY()==
                    incumbent.getPlacement().getY(),
            "strict descent retains incumbent when candidate scores tie");
    }

    /** Negative validator boundary, not a claim this origin is emitted by the helper. */
    private static void verifyLowerScoreAccessEnvelopeRejected() {
        fixtures++;
        Fixture fixture=fourPartFixture(true);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,
            fixture.plan.outline,fixture.plan.footprints);
        PcbFootprint current=find(fixture.plan,"R1");
        PcbFootprint peer=find(fixture.plan,"Q1");
        PcbFootprint colliding=current.translated(peer.getPlacement().getX(),
            peer.getPlacement().getY());
        Vector<PcbFootprint> candidateFootprints=
            new Vector<PcbFootprint>(fixture.plan.footprints);
        int ownIndex=-1;
        for(int index=0;index<candidateFootprints.size();index++)
            if("R1".equals(candidateFootprints.get(index).getPlacement().getComponentId())) {
                ownIndex=index;break;
            }
        check(ownIndex>=0,"access-envelope negative fixture has its passive incumbent");
        candidateFootprints.set(ownIndex,colliding);
        PcbPlacementPlanner.Plan lowerScore=new PcbPlacementPlanner.Plan(
            fixture.plan.outline,candidateFootprints,fixture.plan.regions,
            fixture.plan.courtyardArea,fixture.plan.demandArea,fixture.plan.candidate,
            fixture.plan.evaluations);
        check(independentLocalScore(lowerScore,"R1",fixture.constraints) <
                independentLocalScore(fixture.plan,"R1",fixture.constraints),
            "the rejected access-envelope alternative has lower independently computed cost");

        Rectangle passiveEnvelope=PcbPlacementPlanner.envelope(colliding,
            fixture.constraints.get("R1").accessMargin);
        Rectangle peerEnvelope=PcbPlacementPlanner.envelope(peer,
            fixture.constraints.get("Q1").accessMargin);
        check(overlapsWithPositiveArea(passiveEnvelope,peerEnvelope),
            "independent access-envelope arithmetic proves the hypothetical alternative collides");
        String rejection=null;
        try {
            PcbPlacementPlanner.validate(fixture.board,fixture.constraints,
                lowerScore.outline,lowerScore.footprints);
        } catch(PcbPlacementPlanner.Rejected expected) {
            rejection=expected.reason;
        }
        check("ENVELOPE_ACCESS_OR_DOMAIN".equals(rejection),
            "full placement validator reports the exact access-envelope rejection");

        PcbPlacementPlanner.Plan output=PcbPlacementPlanner.relaxMediumPockets(
            fixture.board,fixture.constraints,fixture.plan);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,
            output.outline,output.footprints);
        check(output.candidate==fixture.plan.candidate &&
                output.evaluations<=60000,
            "the actual helper remains bounded and returns a fully validated candidate");
    }

    private static Fixture fourPartFixture(boolean selectedPolicy) {
        TroubleshootBoard board = new TroubleshootBoard("LOCAL_POCKET_FOUR_PARTS");
        add(board,"J1","CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new String[] {"J1_1","J1_2"});
        add(board,"R1","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,
            new String[] {"R1_1","R1_2"});
        add(board,"J2","CONNECTOR",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            new String[] {"J2_1","J2_2"});
        add(board,"Q1","TRANSISTOR",PhysicalPackages.TO92_NPN,
            new String[] {"Q1_1","Q1_2","Q1_3"});
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("J1","medium","Medium","BOARD",
            PcbPlacementConstraints.Anchor.NONE,20));
        parts.add(new PcbPlacementConstraints.Part("R1","medium","Medium","BOARD",
            PcbPlacementConstraints.Anchor.NONE,20));
        parts.add(new PcbPlacementConstraints.Part("J2","medium","Medium","BOARD",
            PcbPlacementConstraints.Anchor.RIGHT,20));
        parts.add(new PcbPlacementConstraints.Part("Q1","medium","Medium","BOARD",
            PcbPlacementConstraints.Anchor.NONE,20));
        PcbPlacementConstraints constraints = constraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(),
            selectedPolicy ? MediumBoardPhysicalPolicy.ID :
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID);
        board.setPlacementConstraints(constraints);
        board.validate();
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("J2"),800,300));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("R1"),4000,2200));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("J1"),300,300));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("Q1"),300,900));
        return fixture(board,constraints,new Rectangle(0,0,5000,3000),footprints,17);
    }

    private static Fixture singlePassiveFixture() {
        TroubleshootBoard board = new TroubleshootBoard("LOCAL_POCKET_TIE");
        add(board,"R1","RESISTOR",PhysicalPackages.AXIAL_RESISTOR,
            new String[] {"R1_1","R1_2"});
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("R1","medium","Medium","BOARD",
            PcbPlacementConstraints.Anchor.NONE,20));
        PcbPlacementConstraints constraints = constraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(),MediumBoardPhysicalPolicy.ID);
        board.setPlacementConstraints(constraints);
        board.validate();
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("R1"),1000,600));
        return fixture(board,constraints,new Rectangle(0,0,5000,3000),footprints,9);
    }

    private static Fixture isolatedFixture() {
        TroubleshootBoard board = new TroubleshootBoard("LOCAL_POCKET_ISOLATION_EXCLUSION");
        PhysicalPackage physical = PhysicalPackages.OPTOCOUPLER_4;
        String[] terminals = {"A","K","C","E"};
        for (String terminal : terminals) board.addNet(new BoardNet("ISO_"+terminal));
        board.addComponent(new BoardComponent("ISO1","OPTOCOUPLER",physical));
        for (String terminal : terminals)
            board.addPad(new BoardPad("ISO1."+terminal,"ISO1",terminal,"ISO_"+terminal));
        Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains =
            new Vector<PcbPlacementConstraints.TerminalDomain>();
        terminalDomains.add(new PcbPlacementConstraints.TerminalDomain("A","PRIMARY"));
        terminalDomains.add(new PcbPlacementConstraints.TerminalDomain("K","PRIMARY"));
        terminalDomains.add(new PcbPlacementConstraints.TerminalDomain("C","SECONDARY"));
        terminalDomains.add(new PcbPlacementConstraints.TerminalDomain("E","SECONDARY"));
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("ISO1","medium","Medium","PRIMARY",
            PcbPlacementConstraints.Anchor.NONE,20,terminalDomains));
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY","SECONDARY",80));
        PcbPlacementConstraints constraints = constraints(parts,barriers,MediumBoardPhysicalPolicy.ID);
        board.setPlacementConstraints(constraints);
        board.validate();
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("ISO1"),800,400));
        return fixture(board,constraints,new Rectangle(0,0,2200,1400),footprints,4);
    }

    private static PcbPlacementConstraints constraints(
            Vector<PcbPlacementConstraints.Part> parts,
            Vector<PcbPlacementConstraints.Barrier> barriers,String policyId) {
        return new PcbPlacementConstraints(parts,barriers,PcbCopperLayer.BOTTOM,policyId,
            MediumBoardPhysicalPolicy.VERSION,15,true,PcbTraceWidthProfile.ROLE_WIDTHS);
    }

    private static void add(TroubleshootBoard board,String id,String type,
            PhysicalPackage physical,String[] netIds) {
        board.addComponent(new BoardComponent(id,type,physical));
        Vector<String> terminals = physical.getTerminalIds();
        check(terminals.size() == netIds.length,"fixture package terminal count: "+id);
        for (int index=0;index<netIds.length;index++) {
            board.addNet(new BoardNet(netIds[index]));
            board.addPad(new BoardPad(id+"."+terminals.get(index),id,
                terminals.get(index),netIds[index]));
        }
    }

    private static Fixture fixture(TroubleshootBoard board,
            PcbPlacementConstraints constraints,Rectangle outline,
            Vector<PcbFootprint> footprints,int evaluations) {
        constraints.validate(board);
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(outline,footprints,
            new Vector<PcbLayoutRegion>(),0,0,2,evaluations);
        return new Fixture(board,constraints,plan);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan plan;
        Fixture(TroubleshootBoard board,PcbPlacementConstraints constraints,
                PcbPlacementPlanner.Plan plan) {
            this.board=board;this.constraints=constraints;this.plan=plan;
        }
    }

    private static PcbPlacementPlanner.Plan withEvaluations(
            PcbPlacementPlanner.Plan input,int evaluations) {
        return new PcbPlacementPlanner.Plan(input.outline,input.footprints,input.regions,
            input.courtyardArea,input.demandArea,input.candidate,evaluations);
    }

    private static int assertRigidPoseIdentity(PcbPlacementPlanner.Plan before,
            PcbPlacementPlanner.Plan after,String label) {
        check(sameRectangle(before.outline,after.outline),label+" outline stays fixed");
        check(before.candidate==after.candidate &&
                before.courtyardArea==after.courtyardArea &&
                before.demandArea==after.demandArea,
            label+" sizing metadata stays fixed");
        check(before.footprints.size()==after.footprints.size(),label+" footprint count stays fixed");
        int moved=0;
        for(int index=0;index<before.footprints.size();index++) {
            PcbFootprint oldFoot=before.footprints.get(index),newFoot=after.footprints.get(index);
            PcbComponentPlacement oldPose=oldFoot.getPlacement(),newPose=newFoot.getPlacement();
            check(oldPose.getComponentId().equals(newPose.getComponentId()),
                label+" keeps original footprint vector order");
            int dx=newPose.getX()-oldPose.getX(),dy=newPose.getY()-oldPose.getY();
            if(dx!=0 || dy!=0)moved++;
            check(oldPose.getPhysicalPackage()==newPose.getPhysicalPackage() &&
                    oldPose.getPhysicalGeometry()==newPose.getPhysicalGeometry() &&
                    oldPose.getGeometryRealization()==newPose.getGeometryRealization() &&
                    oldPose.getGeometryVariantKey().equals(newPose.getGeometryVariantKey()) &&
                    oldPose.getGeometryTransformKey().equals(newPose.getGeometryTransformKey()) &&
                    oldPose.getGeometryContractVersionValue()==newPose.getGeometryContractVersionValue() &&
                    oldPose.getRotation()==newPose.getRotation() &&
                    oldPose.getMountingSide()==newPose.getMountingSide() &&
                    oldPose.getWidth()==newPose.getWidth() && oldPose.getHeight()==newPose.getHeight(),
                label+" retains package, geometry, variant, rotation and side: "+
                    oldPose.getComponentId());
            Vector<PcbPadPlacement> oldPads=oldFoot.getPads(),newPads=newFoot.getPads();
            check(oldPads.size()==newPads.size(),label+" keeps package pad count");
            for(int padIndex=0;padIndex<oldPads.size();padIndex++) {
                PcbPadPlacement oldPad=oldPads.get(padIndex),newPad=newPads.get(padIndex);
                check(oldPad.getPadId().equals(newPad.getPadId()) &&
                        newPad.getX()-oldPad.getX()==dx && newPad.getY()-oldPad.getY()==dy &&
                        sameTranslatedRectangle(oldPad.getPadBounds(),newPad.getPadBounds(),dx,dy) &&
                        sameTranslatedRectangle(oldPad.getProbeBounds(),newPad.getProbeBounds(),dx,dy) &&
                        oldPad.getEscapeDx()==newPad.getEscapeDx() &&
                        oldPad.getEscapeDy()==newPad.getEscapeDy() &&
                        oldPad.getEscapeLength()==newPad.getEscapeLength() &&
                        oldPad.getAttachment()==newPad.getAttachment() &&
                        oldPad.getMountingSide()==newPad.getMountingSide() &&
                        oldPad.getExposure()==newPad.getExposure(),
                    label+" rigidly translates pad, probe and escape identity: "+oldPad.getPadId());
            }
        }
        return moved;
    }

    private static String movedComponentId(PcbPlacementPlanner.Plan before,
            PcbPlacementPlanner.Plan after) {
        for(int index=0;index<before.footprints.size();index++) {
            PcbComponentPlacement first=before.footprints.get(index).getPlacement();
            PcbComponentPlacement second=after.footprints.get(index).getPlacement();
            if(first.getX()!=second.getX() || first.getY()!=second.getY())
                return first.getComponentId();
        }
        return null;
    }

    /** Independently recomputes existing score terms for the no-link fixture. */
    private static double independentLocalScore(PcbPlacementPlanner.Plan plan,
            String componentId,PcbPlacementConstraints constraints) {
        PcbFootprint own=find(plan,componentId);
        PcbPlacementConstraints.Part demand=constraints.get(componentId);
        Rectangle group=null,occupied=null;
        for(PcbFootprint other:plan.footprints) {
            if(other.getPlacement().getComponentId().equals(componentId))continue;
            PcbPlacementConstraints.Part peer=constraints.get(
                other.getPlacement().getComponentId());
            Rectangle court=other.getPlacement().getRoutingCourtyard();
            if(peer.domainId.equals(demand.domainId) && peer.regionId.equals(demand.regionId))
                group=union(group,court);
            if(!other.getPlacement().getPhysicalPackage().isConnector())
                occupied=union(occupied,court);
        }
        Rectangle ownCourt=own.getPlacement().getRoutingCourtyard();
        double score=occupied==null ? area(ownCourt)/800.0 :
            (area(union(occupied,ownCourt))-area(occupied))/800.0;
        if(group!=null) {
            score+=(area(union(group,ownCourt))-area(group))/800.0;
            score+=(Math.abs((ownCourt.x+ownCourt.width/2.0)-(group.x+group.width/2.0))+
                Math.abs((ownCourt.y+ownCourt.height/2.0)-(group.y+group.height/2.0)))*.12;
        }
        return score;
    }

    private static boolean sameSinglePassiveScore(PcbFootprint first,PcbFootprint second) {
        Rectangle a=first.getPlacement().getRoutingCourtyard();
        Rectangle b=second.getPlacement().getRoutingCourtyard();
        return a.width==b.width && a.height==b.height &&
            area(a)/800.0==area(b)/800.0;
    }

    private static boolean samePlanGeometry(PcbPlacementPlanner.Plan first,
            PcbPlacementPlanner.Plan second) {
        if(!sameRectangle(first.outline,second.outline) ||
                first.footprints.size()!=second.footprints.size())return false;
        for(int index=0;index<first.footprints.size();index++)
            if(!first.footprints.get(index).geometryFingerprint().equals(
                    second.footprints.get(index).geometryFingerprint()))return false;
        return true;
    }

    private static int eligibleCount(PcbPlacementPlanner.Plan plan,
            PcbPlacementConstraints constraints) {
        int count=0;
        for(PcbFootprint footprint:plan.footprints) {
            PcbComponentPlacement placement=footprint.getPlacement();
            String id=placement.getPhysicalPackage().getId();
            PcbPlacementConstraints.Part part=constraints.get(placement.getComponentId());
            if(!placement.getPhysicalPackage().isConnector() &&
                    part.anchor==PcbPlacementConstraints.Anchor.NONE &&
                    placement.getPhysicalGeometry().getIsolationBody()==null &&
                    ("AXIAL_RESISTOR".equals(id) || "AXIAL_DIODE".equals(id) ||
                    "RADIAL_ELECTROLYTIC_CAPACITOR".equals(id) ||
                    "RADIAL_CERAMIC_CAPACITOR".equals(id)))count++;
        }
        return count;
    }

    private static PcbFootprint find(PcbPlacementPlanner.Plan plan,String componentId) {
        for(PcbFootprint footprint:plan.footprints)
            if(componentId.equals(footprint.getPlacement().getComponentId()))return footprint;
        throw new AssertionError("missing fixture footprint: "+componentId);
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value=new StringBuilder();
        appendRectangle(value,plan.outline);
        value.append("candidate=").append(plan.candidate).append(";evaluations=")
            .append(plan.evaluations).append(";courtyard=").append(plan.courtyardArea)
            .append(";demand=").append(plan.demandArea).append(';');
        for(PcbFootprint footprint:plan.footprints)
            value.append(footprint.geometryFingerprint()).append('\n');
        value.append("materialized=").append(plan.materialize().geometryFingerprint());
        return PlayerSessionFingerprint.of(value.toString());
    }

    /** Same ordered RB56 source intent with only placement metadata omitted. */
    private static String sourceIntent(Rb56Plan plan) {
        StringBuilder value=new StringBuilder();
        atom(value,Long.toString(plan.seed));
        atom(value,Long.toString(plan.layoutSeed));
        atom(value,Long.toString(plan.routingSeed));
        atom(value,plan.topology());
        atom(value,Integer.toString(plan.channelCount));
        atom(value,Integer.toString(plan.sensorInputFilterMask));
        atom(value,Integer.toString(plan.outputIndicatorMask));
        atom(value,plan.referenceArrangement.name());
        atom(value,Boolean.toString(plan.driverABjt));
        atom(value,Boolean.toString(plan.driverBBjt));
        atom(value,Boolean.toString(plan.hasFiveVoltIndicator));
        atom(value,Boolean.toString(plan.hasTwelveVoltIndicator));
        atom(value,Boolean.toString(plan.hasFiveVoltBleeder));
        Vector<Rb56Plan.Part> parts=plan.parts();
        value.append(parts.size()).append('[');
        for(Rb56Plan.Part part:parts)atom(value,part.canonical());
        value.append(']');
        return PlayerSessionFingerprint.of(value.toString());
    }

    private static void atom(StringBuilder value,String text) {
        value.append(text.length()).append(':').append(text).append(';');
    }

    private static void appendRectangle(StringBuilder value,Rectangle rectangle) {
        value.append(rectangle.x).append(',').append(rectangle.y).append(',')
            .append(rectangle.width).append(',').append(rectangle.height).append(';');
    }

    private static long area(Rectangle rectangle) {
        return rectangle==null?0:(long)rectangle.width*rectangle.height;
    }

    private static Rectangle union(Rectangle first,Rectangle second) {
        if(first==null)return new Rectangle(second);
        int left=Math.min(first.x,second.x),top=Math.min(first.y,second.y);
        long right=Math.max((long)first.x+first.width,(long)second.x+second.width);
        long bottom=Math.max((long)first.y+first.height,(long)second.y+second.height);
        return new Rectangle(left,top,(int)(right-left),(int)(bottom-top));
    }

    private static boolean sameRectangle(Rectangle first,Rectangle second) {
        return first!=null && second!=null && first.x==second.x && first.y==second.y &&
            first.width==second.width && first.height==second.height;
    }

    private static boolean sameTranslatedRectangle(Rectangle first,Rectangle second,
            int dx,int dy) {
        return first.width==second.width && first.height==second.height &&
            (long)second.x-first.x==dx && (long)second.y-first.y==dy;
    }

    private static boolean overlapsWithPositiveArea(Rectangle first,Rectangle second) {
        return (long)first.x+first.width>second.x &&
            (long)second.x+second.width>first.x &&
            (long)first.y+first.height>second.y &&
            (long)second.y+second.height>first.y;
    }

    private static boolean rejectPrivateReplay(String text,String why) {
        boolean rejected=false;
        try { PlayerSessionReplay.parse(text); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,why);
        return rejected;
    }

    private static void check(boolean condition,String why) {
        assertions++;
        if(!condition)throw new AssertionError(why);
    }
}
