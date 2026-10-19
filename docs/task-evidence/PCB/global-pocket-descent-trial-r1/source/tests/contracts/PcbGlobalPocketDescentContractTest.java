package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Bounded private22 global-score descent and source-identity contracts. */
public final class PcbGlobalPocketDescentContractTest {
    private static final int FIXTURE_COUNT = 11;
    private static final long[] RAW_ROOTS = {-1017L, 42L, 4L};
    private static int assertions;
    private static int fixtures;

    private PcbGlobalPocketDescentContractTest() { }

    public static void main(String[] args) {
        private22IdentityAndDensity();
        rawRepresentativeCandidateParity();
        fourPartNoLinkDescent();
        linkedPassiveDescent();
        eligibilityAndVersionGuards();
        isolationPackageExclusion();
        budget59999StopsAtCap();
        budget60000ReturnsImmediately();
        equalGlobalScoreRetainsIncumbent();
        accessEnvelopeNegativeBoundary();
        scoreAdapterRejectsMissingInputs();
        check(fixtures == FIXTURE_COUNT, "exact named fixture count");
        System.out.println("PASS: PCB global pocket descent contracts assertions=" +
            assertions + " fixtures=" + fixtures + " evaluator=globalPocketDescent");
    }

    private static void private22IdentityAndDensity() {
        fixtures++;
        check(Rb56Placement.supportsVersion(15) && Rb56Placement.supportsVersion(22),
            "current15 and private22 remain supported");
        for (int version = 16; version <= 21; version++)
            check(!Rb56Placement.supportsVersion(version),
                "unqualified private placement version remains unsupported: " + version);
        check(Rb56Placement.densityVersion(15) == 14 &&
                Rb56Placement.densityVersion(22) == 15,
            "private22 alone selects density15 while current15 remains density14");
        check(Rb56Placement.widthProfile(22) == PcbTraceWidthProfile.ROLE_WIDTHS &&
                SeededPcbLayoutGenerator.CURRENT_VERSION == 16,
            "private22 retains role widths and general algorithm16 stays current");

        GenerationRequest request = privateRequest(22, 42L);
        PlayerSessionReplay replay = PlayerSessionReplay.forQualification(request);
        String expected = "tsj-private-session/22/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-22/RB56_CONTROL/42";
        PlayerSessionReplay parsed = PlayerSessionReplay.parse(expected);
        check(expected.equals(replay.canonical()) && expected.equals(parsed.canonical()) &&
                parsed.rb56PlacementInterpretationVersion() == 22 &&
                parsed.matches(request),
            "private22 replay is exact, canonical, and version-bound");
        check(!parsed.matches(privateRequest(15, 42L)),
            "private22 replay cannot match current15 request");
        for (long exactSeed : new long[] {Long.MIN_VALUE, Long.MAX_VALUE,
                9007199254740993L, -9007199254740993L}) {
            GenerationRequest exactRequest = privateRequest(22, exactSeed);
            String exactText = privateReplay(22, exactSeed);
            PlayerSessionReplay exactReplay = PlayerSessionReplay.parse(exactText);
            check(exactText.equals(PlayerSessionReplay.forQualification(exactRequest).canonical()) &&
                    exactText.equals(exactReplay.canonical()) && exactReplay.seed() == exactSeed &&
                    exactReplay.matches(exactRequest),
                "private22 preserves the exact signed-long replay seed: " + exactSeed);
        }
        for (int version = 16; version <= 21; version++) {
            final int unsupported = version;
            expectIllegal(new Runnable() {
                public void run() { privateRequest(unsupported, 42L); }
            }, "private request rejects unqualified version " + version);
            expectIllegal(new Runnable() {
                public void run() { PlayerSessionReplay.parse(privateReplay(unsupported, 42L)); }
            }, "replay parser rejects unqualified version " + version);
        }
    }

    private static void rawRepresentativeCandidateParity() {
        fixtures++;
        PcbPlacementPlanner planner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        for (long seed : RAW_ROOTS) {
            Rb56Plan saved = Rb56Plan.resolve(seed, 15);
            Rb56Plan trial = Rb56Plan.resolve(seed, 22);
            TroubleshootBoard savedBoard = saved.board();
            TroubleshootBoard trialBoard = trial.board();
            check(saved.seed == seed && trial.seed == seed &&
                    saved.layoutSeed == trial.layoutSeed && saved.routingSeed == trial.routingSeed,
                "same fixed signed-long source and random concerns: " + seed);
            check(saved.placementInterpretationVersion == 15 &&
                    trial.placementInterpretationVersion == 22 &&
                    savedBoard.getPlacementConstraints().placementDensityVersion == 14 &&
                    trialBoard.getPlacementConstraints().placementDensityVersion == 15,
                "exact current15/private22 placement and density mapping: " + seed);
            check(PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard).equals(
                    PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)) &&
                    saved.topology().equals(trial.topology()) &&
                    sameDeclaredParts(saved, trial),
                "source graph and declared package identities stay exact: " + seed);
            check(savedBoard.getPlacementConstraints().getTraceWidthProfile() ==
                    trialBoard.getPlacementConstraints().getTraceWidthProfile() &&
                    trialBoard.getPlacementConstraints().getTraceWidthProfile() ==
                        PcbTraceWidthProfile.ROLE_WIDTHS,
                "current and trial use the same source trace-width profile: " + seed);
            String savedGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
            String trialGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);

            for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                    candidate++) {
                PlanObservation before = observe(planner, savedBoard,
                    saved.layoutSeed, candidate);
                PlanObservation after = observe(planner, trialBoard,
                    trial.layoutSeed, candidate);
                if (candidate == 2) {
                    check(before.plan != null && after.plan != null,
                        "candidate2 raw root remains legal: " + seed);
                    long oldTotal = MediumBoardPhysicalPolicy.scorePlan(savedBoard,
                        before.plan).total;
                    long newTotal = MediumBoardPhysicalPolicy.scorePlan(trialBoard,
                        after.plan).total;
                    check(newTotal <= oldTotal &&
                            after.plan.evaluations >= before.plan.evaluations &&
                            after.plan.evaluations <= 60000,
                        "candidate2 global total is monotone with honest bounded evaluations: " +
                            seed);
                    if (seed == 42L) {
                        PlanObservation repeat = observe(planner, trialBoard,
                            trial.layoutSeed, candidate);
                        check(repeat.plan != null &&
                                planFingerprint(after.plan).equals(planFingerprint(repeat.plan)),
                            "candidate2 global descent deterministically replays: " + seed);
                    }
                } else {
                    check(before.plan != null && after.plan != null &&
                            planFingerprint(before.plan).equals(planFingerprint(after.plan)),
                        "candidate" + candidate + " remains exact under22: " + seed);
                }
            }
            check(savedGraphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard)) &&
                    trialGraphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),
                "raw planning leaves both live source graphs unchanged: " + seed);
        }
    }

    private static void fourPartNoLinkDescent() {
        fixtures++;
        Fixture fixture = fixture("global-no-link", new Rectangle(0, 0, 4000, 2400),
            new PartSpec[] {
                part("R1", PhysicalPackages.AXIAL_RESISTOR, 3200, 1500,
                    "component", PcbPlacementConstraints.Anchor.NONE),
                part("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 300, 300,
                    "input", PcbPlacementConstraints.Anchor.NONE),
                part("J2", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 3500, 250,
                    "output", PcbPlacementConstraints.Anchor.RIGHT),
                part("Q1", PhysicalPackages.TO92_NPN, 700, 850,
                    "component", PcbPlacementConstraints.Anchor.NONE)
            }, new Vector<PcbPlacementConstraints.Barrier>(), 2, 7);
        check(fixture.board.getComponentIds().size() == 4 &&
                fixture.constraints.get("R1").anchor == PcbPlacementConstraints.Anchor.NONE &&
                fixture.constraints.get("J1").anchor == PcbPlacementConstraints.Anchor.NONE &&
                fixture.constraints.get("J2").anchor == PcbPlacementConstraints.Anchor.RIGHT &&
                fixture.constraints.get("Q1").anchor == PcbPlacementConstraints.Anchor.NONE,
            "four-part fixture independently declares one movable passive, two connectors, and one nonpassive");

        long beforeOracle = independentNoLinkScore(fixture.board, fixture.plan);
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board, fixture.plan).total ==
                beforeOracle,
            "no-link whole-plan oracle includes fixed areas and region-center spread");
        String sourceGraph = PcbCanvasBoundaryContractTest.liveGraphSha256(fixture.board);
        String inputFingerprint = planFingerprint(fixture.plan);
        PcbPlacementPlanner.Plan result = PcbPlacementPlanner.relaxMediumGlobalPockets(
            fixture.board, fixture.constraints, fixture.plan);
        PcbPlacementPlanner.Plan repeat = PcbPlacementPlanner.relaxMediumGlobalPockets(
            fixture.board, fixture.constraints, fixture.plan);
        check(!sameOrigin(footprint(result, "R1"), footprint(fixture.plan, "R1")) &&
                result.evaluations > fixture.plan.evaluations &&
                result.evaluations <= 60000,
            "one eligible passive makes a charged, bounded strict global-score move");
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board, result).total ==
                independentNoLinkScore(fixture.board, result) &&
                MediumBoardPhysicalPolicy.scorePlan(fixture.board, result).total < beforeOracle,
            "global no-link independent oracle confirms region-spread reduction");
        check(planFingerprint(result).equals(planFingerprint(repeat)) &&
                inputFingerprint.equals(planFingerprint(fixture.plan)),
            "same input deterministically replays without mutating the source plan");
        check(sourceGraph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(fixture.board)),
            "source pads, nets, and component graph remain unchanged");
        assertOnlyR1Moved(fixture, result);
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            result.outline, result.footprints);
    }

    private static void linkedPassiveDescent() {
        fixtures++;
        Fixture fixture = linkedFixture("global-linked", 5);
        long before = independentLinkedScore(fixture.board, fixture.plan);
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board, fixture.plan).total == before,
            "linked-pair independent MST, connector, and fixed-area oracle matches whole score");
        PcbPlacementPlanner.Plan result = PcbPlacementPlanner.relaxMediumGlobalPockets(
            fixture.board, fixture.constraints, fixture.plan);
        long after = independentLinkedScore(fixture.board, result);
        check(!sameOrigin(footprint(result, "R1"), footprint(fixture.plan, "R1")) &&
                after < before && result.evaluations > fixture.plan.evaluations &&
                result.evaluations <= 60000,
            "linked passive moves and lowers the whole authoritative placement cost");
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board, result).total == after,
            "linked physical move agrees with independently measured net-span score");
        assertOnlyR1Moved(fixture, result);
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            result.outline, result.footprints);
    }

    private static void eligibilityAndVersionGuards() {
        fixtures++;
        Fixture noEligible = fixture("global-no-eligible", new Rectangle(0, 0, 4000, 2400),
            new PartSpec[] {
                part("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 500, 500,
                    "connector", PcbPlacementConstraints.Anchor.NONE),
                part("Q1", PhysicalPackages.TO92_NPN, 1400, 900,
                    "logic", PcbPlacementConstraints.Anchor.NONE)
            }, new Vector<PcbPlacementConstraints.Barrier>(), 2, 13);
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(noEligible.board,
                noEligible.constraints, noEligible.plan) == noEligible.plan,
            "no eligible package returns the exact legal incumbent without evaluations");

        Fixture eligible = fourPartGuardFixture();
        PcbPlacementPlanner.Plan otherCandidate = planWith(eligible.plan,
            eligible.plan.footprints, 1, eligible.plan.evaluations);
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(eligible.board,
                eligible.constraints, otherCandidate) == otherCandidate,
            "candidate1 is outside the candidate2-only pass");
        PcbPlacementConstraints density14 = constraintsLike(eligible.constraints, 14,
            MediumBoardPhysicalPolicy.ID);
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(eligible.board,
                density14, eligible.plan) == eligible.plan,
            "density14/current15 bypasses private density15 descent");
        PcbPlacementConstraints unselected = constraintsLike(eligible.constraints, 15,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID);
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(eligible.board,
                unselected, eligible.plan) == eligible.plan,
            "default physical policy remains outside medium global descent");
    }

    private static void isolationPackageExclusion() {
        fixtures++;
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80));
        PartSpec opto = new PartSpec("U1", PhysicalPackages.OPTOCOUPLER_4, 1600, 900,
            "isolation", PcbPlacementConstraints.Anchor.NONE, "PRIMARY",
            null, new String[] {"PRIMARY", "PRIMARY", "SECONDARY", "SECONDARY"});
        Fixture fixture = fixture("global-isolation-excluded", new Rectangle(0, 0, 4000, 2400),
            new PartSpec[] {opto}, barriers, 2, 3);
        check(opto.physical.getGeometry().getIsolationBody() != null &&
                fixture.constraints.get("U1").anchor == PcbPlacementConstraints.Anchor.NONE,
            "fixture is a valid mixed-domain isolating body with declared barrier");
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(fixture.board,
                fixture.constraints, fixture.plan) == fixture.plan &&
                fixture.plan.evaluations == 3,
            "isolation package is not treated as an eligible passive target");
    }

    private static void budget59999StopsAtCap() {
        fixtures++;
        Fixture tie = tieFixture("global-budget-59999", 59999);
        PcbPlacementPlanner.Plan result = PcbPlacementPlanner.relaxMediumGlobalPockets(
            tie.board, tie.constraints, tie.plan);
        check(result.evaluations == 60000 && result.evaluations <= 60000,
            "the final optional distinct origin consumes exactly the remaining evaluation");
        check(sameOrigin(footprint(result, "R1"), footprint(tie.plan, "R1")),
            "equal-cost budget-bound proposals retain the legal incumbent");
        PcbPlacementPlanner.validate(tie.board, tie.constraints,result.outline,result.footprints);
    }

    private static void budget60000ReturnsImmediately() {
        fixtures++;
        Fixture tie = tieFixture("global-budget-60000", 60000);
        check(PcbPlacementPlanner.relaxMediumGlobalPockets(tie.board,
                tie.constraints, tie.plan) == tie.plan && tie.plan.evaluations == 60000,
            "optional pass exits at an already exhausted shared planner budget");
    }

    private static void equalGlobalScoreRetainsIncumbent() {
        fixtures++;
        Fixture tie = tieFixture("global-neutral-connector-tie", 9);
        long independent = independentNoLinkScore(tie.board, tie.plan);
        check(MediumBoardPhysicalPolicy.scorePlan(tie.board,tie.plan).total == independent,
            "neutral connector tie uses the independent no-link global-score oracle");
        PcbPlacementPlanner.Plan result = PcbPlacementPlanner.relaxMediumGlobalPockets(
            tie.board, tie.constraints, tie.plan);
        check(result.evaluations > tie.plan.evaluations &&
                result.evaluations <= 60000 &&
                sameOrigin(footprint(result,"R1"),footprint(tie.plan,"R1")) &&
                MediumBoardPhysicalPolicy.scorePlan(tie.board,result).total == independent,
            "one eligible passive plus a neutral valid connector charges proposals but keeps ties");
        check(tie.constraints.get("J1").anchor == PcbPlacementConstraints.Anchor.NONE &&
                !tie.board.getComponent("J1").getPhysicalPackage().getId().equals("AXIAL_RESISTOR"),
            "tie peer is an actual unanchored connector in a separate semantic region");
    }

    private static void accessEnvelopeNegativeBoundary() {
        fixtures++;
        Fixture fixture = linkedPairFixture("global-access-negative", 4);
        PcbFootprint source = footprint(fixture.plan,"R1");
        PcbFootprint peer = footprint(fixture.plan,"R2");
        PcbFootprint colliding = source.translated(peer.getPlacement().getX(),
            peer.getPlacement().getY());
        Vector<PcbFootprint> hypotheticalFootprints = new Vector<PcbFootprint>(
            fixture.plan.footprints);
        hypotheticalFootprints.set(indexOf(hypotheticalFootprints,"R1"),colliding);
        PcbPlacementPlanner.Plan hypothetical = planWith(fixture.plan,
            hypotheticalFootprints,fixture.plan.candidate,fixture.plan.evaluations);
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board,hypothetical).total <
                MediumBoardPhysicalPolicy.scorePlan(fixture.board,fixture.plan).total,
            "explicit hypothetical collision has lower advisory global score");
        boolean rejected = false;
        try {
            PcbPlacementPlanner.validate(fixture.board,fixture.constraints,
                hypothetical.outline,hypothetical.footprints);
        } catch(PcbPlacementPlanner.Rejected failure) {
            rejected = "ENVELOPE_ACCESS_OR_DOMAIN".equals(failure.reason);
        }
        check(rejected,
            "unchanged full validator rejects the hypothetical colliding access envelope");
        PcbPlacementPlanner.Plan result = PcbPlacementPlanner.relaxMediumGlobalPockets(
            fixture.board,fixture.constraints,fixture.plan);
        PcbPlacementPlanner.validate(fixture.board,fixture.constraints,
            result.outline,result.footprints);
        check(MediumBoardPhysicalPolicy.scorePlan(fixture.board,result).total <=
                MediumBoardPhysicalPolicy.scorePlan(fixture.board,fixture.plan).total,
            "actual helper result remains legal and globally non-increasing");
        // The hypothetical collision is a validator-boundary canary only; this
        // fixture does not claim the advisory generator produced that origin.
    }

    private static void scoreAdapterRejectsMissingInputs() {
        fixtures++;
        final Fixture fixture = tieFixture("global-score-adapter-inputs", 1);
        expectIllegal(new Runnable() {
            public void run() { MediumBoardPhysicalPolicy.scorePlan(null,fixture.plan); }
        }, "score adapter requires the authoritative board");
        expectIllegal(new Runnable() {
            public void run() { MediumBoardPhysicalPolicy.scorePlan(fixture.board,null); }
        }, "score adapter requires a complete plan");
    }

    private static Fixture fourPartGuardFixture() {
        return fixture("global-guard", new Rectangle(0, 0, 4000, 2400),
            new PartSpec[] {
                part("R1", PhysicalPackages.AXIAL_RESISTOR, 3200, 1500,
                    "component", PcbPlacementConstraints.Anchor.NONE),
                part("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 300, 300,
                    "input", PcbPlacementConstraints.Anchor.NONE),
                part("J2", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 3500, 250,
                    "output", PcbPlacementConstraints.Anchor.RIGHT),
                part("Q1", PhysicalPackages.TO92_NPN, 700, 850,
                    "component", PcbPlacementConstraints.Anchor.NONE)
            }, new Vector<PcbPlacementConstraints.Barrier>(), 2, 2);
    }

    private static Fixture tieFixture(String id, int evaluations) {
        return fixture(id, new Rectangle(0, 0, 4000, 2400), new PartSpec[] {
            part("R1", PhysicalPackages.AXIAL_RESISTOR, 1300, 850,
                "passive", PcbPlacementConstraints.Anchor.NONE),
            part("J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, 400, 400,
                "connector", PcbPlacementConstraints.Anchor.NONE)
        }, new Vector<PcbPlacementConstraints.Barrier>(),2,evaluations);
    }

    private static Fixture linkedFixture(String id,int evaluations) {
        return fixture(id,new Rectangle(0,0,4000,2400),new PartSpec[] {
            new PartSpec("R1",PhysicalPackages.AXIAL_RESISTOR,3200,1500,
                "passive",PcbPlacementConstraints.Anchor.NONE,"board",
                new String[] {"LINK_1","LINK_2"},null),
            new PartSpec("J1",PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,400,400,
                "connector",PcbPlacementConstraints.Anchor.NONE,"board",
                new String[] {"LINK_1","LINK_2"},null)
        },new Vector<PcbPlacementConstraints.Barrier>(),2,evaluations);
    }

    private static Fixture linkedPairFixture(String id,int evaluations) {
        return fixture(id,new Rectangle(0,0,4000,2400),new PartSpec[] {
            new PartSpec("R1",PhysicalPackages.AXIAL_RESISTOR,3200,1500,
                "passive",PcbPlacementConstraints.Anchor.NONE,"board",
                new String[] {"PAIR_1","PAIR_2"},null),
            new PartSpec("R2",PhysicalPackages.AXIAL_RESISTOR,700,700,
                "passive",PcbPlacementConstraints.Anchor.NONE,"board",
                new String[] {"PAIR_1","PAIR_2"},null)
        },new Vector<PcbPlacementConstraints.Barrier>(),2,evaluations);
    }

    private static Fixture fixture(String id,Rectangle outline,PartSpec[] declarations,
            Vector<PcbPlacementConstraints.Barrier> barriers,int candidate,int evaluations) {
        TroubleshootBoard board=new TroubleshootBoard(id);
        for(PartSpec declaration:declarations) {
            board.addComponent(new BoardComponent(declaration.id,"global-pocket-fixture",
                declaration.physical));
            Vector<String> terminals=declaration.physical.getTerminalIds();
            for(int index=0;index<terminals.size();index++) {
                String terminal=terminals.get(index);
                String net=declaration.netIds==null ? declaration.id+"_"+terminal :
                    declaration.netIds[index];
                if(board.getNet(net)==null)board.addNet(new BoardNet(net));
                board.addPad(new BoardPad(declaration.id+"."+terminal,declaration.id,
                    terminal,net));
            }
        }
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for(PartSpec declaration:declarations) {
            Vector<PcbPlacementConstraints.TerminalDomain> domains =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            if(declaration.terminalDomains!=null) {
                Vector<String> terminals=declaration.physical.getTerminalIds();
                for(int index=0;index<terminals.size();index++)
                    domains.add(new PcbPlacementConstraints.TerminalDomain(
                        terminals.get(index),declaration.terminalDomains[index]));
            }
            parts.add(new PcbPlacementConstraints.Part(declaration.id,declaration.region,
                declaration.region,declaration.domain,declaration.anchor,20,domains));
        }
        PcbPlacementConstraints constraints=new PcbPlacementConstraints(parts,barriers,
            PcbCopperLayer.BOTTOM,MediumBoardPhysicalPolicy.ID,
            MediumBoardPhysicalPolicy.VERSION,15,true,PcbTraceWidthProfile.ROLE_WIDTHS);
        board.setPlacementConstraints(constraints);
        constraints.validate(board);

        Vector<PcbFootprint> footprints=new Vector<PcbFootprint>();
        long courtyardArea=0,demandArea=0;
        for(PartSpec declaration:declarations) {
            PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(
                board.getComponent(declaration.id),declaration.x,declaration.y);
            footprints.add(footprint);
            Rectangle courtyard=footprint.getPlacement().getRoutingCourtyard();
            courtyardArea+=(long)courtyard.width*courtyard.height;
            Rectangle access=PcbPlacementPlanner.envelope(footprint,20);
            demandArea+=(long)(access.width+PcbPlacementPlanner.CHANNEL)*
                (access.height+PcbPlacementPlanner.CHANNEL);
        }
        TreeMap<String,Vector<String>> byRegion=new TreeMap<String,Vector<String>>();
        for(PartSpec declaration:declarations) {
            Vector<String> members=byRegion.get(declaration.region);
            if(members==null) {members=new Vector<String>();byRegion.put(declaration.region,members);}
            members.add(declaration.id);
        }
        Vector<PcbLayoutRegion> regions=new Vector<PcbLayoutRegion>();
        for(Map.Entry<String,Vector<String>> entry:byRegion.entrySet())
            regions.add(new PcbLayoutRegion(entry.getKey(),entry.getKey(),entry.getValue()));
        PcbPlacementPlanner.Plan plan=new PcbPlacementPlanner.Plan(outline,footprints,regions,
            courtyardArea,demandArea,candidate,evaluations);
        PcbPlacementPlanner.validate(board,constraints,outline,footprints);
        board.validate();
        return new Fixture(board,constraints,plan);
    }

    private static PartSpec part(String id,PhysicalPackage physical,int x,int y,
            String region,PcbPlacementConstraints.Anchor anchor) {
        return new PartSpec(id,physical,x,y,region,anchor,"board",null,null);
    }

    private static PcbPlacementConstraints constraintsLike(PcbPlacementConstraints source,
            int density,String policyId) {
        return new PcbPlacementConstraints(source.getParts(),source.getBarriers(),
            source.routingLayer,policyId,MediumBoardPhysicalPolicy.VERSION,density,true,
            PcbTraceWidthProfile.ROLE_WIDTHS);
    }

    private static PcbPlacementPlanner.Plan planWith(PcbPlacementPlanner.Plan source,
            Vector<PcbFootprint> footprints,int candidate,int evaluations) {
        return new PcbPlacementPlanner.Plan(source.outline,footprints,source.regions,
            source.courtyardArea,source.demandArea,candidate,evaluations);
    }

    private static PlanObservation observe(PcbPlacementPlanner planner,TroubleshootBoard board,
            long seed,int candidate) {
        try {
            return new PlanObservation(planner.plan(board,board.getPlacementConstraints(),
                seed,candidate),null);
        } catch(PcbPlacementPlanner.Rejected rejected) {
            return new PlanObservation(null,rejected.reason);
        }
    }

    private static boolean sameDeclaredParts(Rb56Plan first,Rb56Plan second) {
        Vector<Rb56Plan.Part> a=first.parts(),b=second.parts();
        if(a.size()!=b.size())return false;
        for(int index=0;index<a.size();index++)
            if(!a.get(index).canonical().equals(b.get(index).canonical()))return false;
        return true;
    }

    private static long independentNoLinkScore(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String,int[]> bounds=new TreeMap<String,int[]>();
        for(String netId:board.getNetIds())
            check(board.getNet(netId).getPadIds().size()==1,
                "no-link oracle only includes singleton nets");
        for(String id:board.getComponentIds()) {
            PcbFootprint footprint=footprint(plan,id);
            PcbPlacementConstraints.Part demand=board.getPlacementConstraints().get(id);
            PcbComponentPlacement placement=footprint.getPlacement();
            int x=placement.getX()+placement.getWidth()/2;
            int y=placement.getY()+placement.getHeight()/2;
            int[] region=bounds.get(demand.regionId);
            if(region==null)bounds.put(demand.regionId,new int[] {x,y,x,y,1});
            else {
                region[0]=Math.min(region[0],x);region[1]=Math.min(region[1],y);
                region[2]=Math.max(region[2],x);region[3]=Math.max(region[3],y);
                region[4]++;
            }
        }
        long cohesion=0;
        for(int[] region:bounds.values())if(region[4]>1)
            cohesion+=(long)region[2]-region[0]+(long)region[3]-region[1];
        long fixedArea=((long)plan.outline.width*plan.outline.height)/1000L+
            plan.courtyardArea/1000L+plan.demandArea/1000L;
        return fixedArea+cohesion*4L;
    }

    private static long independentLinkedScore(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        long span=0;
        for(String netId:board.getNetIds()) {
            Vector<String> pads=board.getNet(netId).getPadIds();
            check(pads.size()==2,"linked oracle has exactly two endpoints per net");
            BoardPad first=board.getPad(pads.get(0)),second=board.getPad(pads.get(1));
            check(!first.getComponentId().equals(second.getComponentId()),
                "linked oracle endpoints belong to distinct components");
            PcbPadPlacement a=footprint(plan,first.getComponentId()).getPad(first.getId());
            PcbPadPlacement b=footprint(plan,second.getComponentId()).getPad(second.getId());
            span+=Math.abs((long)a.getX()-b.getX())+
                Math.abs((long)a.getY()-b.getY());
        }
        long area=((long)plan.outline.width*plan.outline.height)/1000L+
            plan.courtyardArea/1000L+plan.demandArea/1000L;
        // Both declared connector pads each see the sole nonconnector endpoint
        // on their own SIGNAL net; no other package can overlap either net path.
        return area+span*8L+span*6L;
    }

    private static void assertOnlyR1Moved(Fixture fixture,
            PcbPlacementPlanner.Plan result) {
        check(result.footprints.size()==fixture.plan.footprints.size(),
            "complete footprint vector length is preserved");
        for(int index=0;index<fixture.plan.footprints.size();index++) {
            PcbFootprint before=fixture.plan.footprints.get(index);
            PcbFootprint after=result.footprints.get(index);
            check(before.getPlacement().getComponentId().equals(
                    after.getPlacement().getComponentId()),
                "canonical footprint vector order is preserved at " + index);
            if(!"R1".equals(before.getPlacement().getComponentId()))
                check(before.geometryFingerprint().equals(after.geometryFingerprint()),
                    "noneligible peer is byte-stable: " + before.getPlacement().getComponentId());
        }
        PcbFootprint before=footprint(fixture.plan,"R1"),after=footprint(result,"R1");
        int dx=after.getPlacement().getX()-before.getPlacement().getX();
        int dy=after.getPlacement().getY()-before.getPlacement().getY();
        check(dx!=0 || dy!=0,"only the eligible passive has a changed origin");
        check(before.getPlacement().getPhysicalPackage()==after.getPlacement().getPhysicalPackage() &&
                before.getPlacement().getPhysicalGeometry()==after.getPlacement().getPhysicalGeometry() &&
                before.getPlacement().getGeometryRealization()==after.getPlacement().getGeometryRealization() &&
                before.getPlacement().getRotation()==after.getPlacement().getRotation() &&
                before.getPlacement().getMountingSide()==after.getPlacement().getMountingSide() &&
                before.getPlacement().getGeometryVariantKey().equals(
                    after.getPlacement().getGeometryVariantKey()) &&
                before.getPlacement().getGeometryTransformKey().equals(
                    after.getPlacement().getGeometryTransformKey()),
            "rigid passive move preserves package, variant, transform, pose orientation, and realization");
        Vector<PcbPadPlacement> padsBefore=before.getPads(),padsAfter=after.getPads();
        check(padsBefore.size()==padsAfter.size(),"all source terminals remain declared");
        for(int index=0;index<padsBefore.size();index++) {
            PcbPadPlacement a=padsBefore.get(index),b=padsAfter.get(index);
            check(a.getPadId().equals(b.getPadId()) && b.getX()-a.getX()==dx &&
                    b.getY()-a.getY()==dy && a.getEscapeDx()==b.getEscapeDx() &&
                    a.getEscapeDy()==b.getEscapeDy() &&
                    a.getEscapeLength()==b.getEscapeLength() &&
                    a.getAttachment()==b.getAttachment() &&
                    a.getMountingSide()==b.getMountingSide() &&
                    a.getExposure()==b.getExposure(),
                "terminal identity, escape, attachment, and probe surface move rigidly: " +
                    a.getPadId());
            check(rectangleMoved(a.getPadBounds(),b.getPadBounds(),dx,dy) &&
                    rectangleMoved(a.getProbeBounds(),b.getProbeBounds(),dx,dy),
                "pad/probe targets keep exact rigid translation: " + a.getPadId());
        }
    }

    private static boolean rectangleMoved(Rectangle before,Rectangle after,int dx,int dy) {
        return after.x-before.x==dx && after.y-before.y==dy &&
            before.width==after.width && before.height==after.height;
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder result=new StringBuilder();
        result.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|')
            .append(plan.candidate).append('|').append(plan.evaluations).append('|')
            .append(plan.courtyardArea).append('|').append(plan.demandArea).append('|');
        for(PcbFootprint footprint:plan.footprints)
            result.append(footprint.geometryFingerprint()).append("\n");
        for(PcbLayoutRegion region:plan.regions)
            result.append(region.id).append('=').append(region.getComponentIds()).append(';');
        return result.toString();
    }

    private static PcbFootprint footprint(PcbPlacementPlanner.Plan plan,String id) {
        return footprint(plan.footprints,id);
    }

    private static PcbFootprint footprint(Vector<PcbFootprint> footprints,String id) {
        int index=indexOf(footprints,id);
        if(index<0)throw new AssertionError("missing footprint " + id);
        return footprints.get(index);
    }

    private static int indexOf(Vector<PcbFootprint> footprints,String id) {
        for(int index=0;index<footprints.size();index++)
            if(id.equals(footprints.get(index).getPlacement().getComponentId()))return index;
        return -1;
    }

    private static boolean sameOrigin(PcbFootprint first,PcbFootprint second) {
        return first.getPlacement().getX()==second.getPlacement().getX() &&
            first.getPlacement().getY()==second.getPlacement().getY();
    }

    private static GenerationRequest privateRequest(int version,long seed) {
        return GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID,seed)
            .withRb56PlacementInterpretationVersion(version);
    }

    private static String privateReplay(int version,long seed) {
        return "tsj-private-session/" + version + "/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-" + version + "/RB56_CONTROL/" + seed;
    }

    private static void expectIllegal(Runnable action,String message) {
        boolean rejected=false;
        try {action.run();}
        catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected,message);
    }

    private static void check(boolean condition,String message) {
        assertions++;
        if(!condition)throw new AssertionError(message);
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

    private static final class PartSpec {
        final String id,region,domain;
        final PhysicalPackage physical;
        final PcbPlacementConstraints.Anchor anchor;
        final int x,y;
        final String[] netIds,terminalDomains;
        PartSpec(String id,PhysicalPackage physical,int x,int y,String region,
                PcbPlacementConstraints.Anchor anchor) {
            this(id,physical,x,y,region,anchor,"board",null,null);
        }
        PartSpec(String id,PhysicalPackage physical,int x,int y,String region,
                PcbPlacementConstraints.Anchor anchor,String domain,String[] netIds,
                String[] terminalDomains) {
            this.id=id;this.physical=physical;this.x=x;this.y=y;this.region=region;
            this.anchor=anchor;this.domain=domain;this.netIds=netIds;
            this.terminalDomains=terminalDomains;
        }
    }

    private static final class PlanObservation {
        final PcbPlacementPlanner.Plan plan;
        final String rejection;
        PlanObservation(PcbPlacementPlanner.Plan plan,String rejection) {
            this.plan=plan;this.rejection=rejection;
        }
    }
}
