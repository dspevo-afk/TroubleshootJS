package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Literal placement-only contracts for the normal connected-pocket pass. */
public final class PcbConnectedPocketPackingContractTest {
    private static final Rectangle LED_ENVELOPE = new Rectangle(-19, -36, 123, 171);
    private static final Rectangle E04_ENVELOPE = new Rectangle(-15, -35, 290, 275);
    private static final Rectangle MIRRORED_CONNECTOR_ENVELOPE =
        new Rectangle(-50, -46, 176, 202);
    private static int assertions;
    private static int fixtures;
    private static final Vector<String> fixtureNames = new Vector<String>();

    private PcbConnectedPocketPackingContractTest() { }

    public static void main(String[] args) {
        literalConnectedPocketPositive();
        literalConnectedPairPositive();
        pairedEligibilityExclusions();
        pairedEvaluationBoundAndCancellation();
        countedNoOpAndLegacyFlag();
        evaluationCeiling();
        cancellationAndScopeCleanup();
        singleFacePackingFlagDoesNotPrecompact();
        croppedDisplayFrameIsNotRerouted();
        System.out.println("PASS: PCB connected pocket packing contracts assertions=" +
            assertions + " fixtures=" + fixtures);
    }

    private static void literalConnectedPocketPositive() {
        Fixture fixture = positiveFixture();
        String boardBefore = graphSnapshot(fixture.board);
        String inputBefore = planSnapshot(fixture.plan);
        long inputArea = literalEnvelopeArea(fixture.plan);
        long inputWire = literalWireDemand(fixture.plan);
        check(fixture.board.getComponentIds().size() == 4 &&
                fixture.board.getPadIds().size() == 8 &&
                fixture.constraints.getParts().size() == 4,
            "literal fixture contains all four components, eight pads, and four demands");
        for (String id : fixture.board.getComponentIds())
            check(fixture.constraints.get(id) != null &&
                    fixture.constraints.get(id).accessMargin == 20 &&
                    "all".equals(fixture.constraints.get(id).regionId) &&
                    "board".equals(fixture.constraints.get(id).domainId),
                id + " retains its declared region, domain, and 20-unit access margin");
        check(inputArea == 726414L,
            "literal source envelopes give bbox area 726414");
        check(inputWire == 11040L,
            "two parallel three-node nets give literal source wire demand 11040");
        check(PcbPlacementPlanner.occupiedEnvelopeArea(fixture.plan.footprints,
                fixture.constraints) == inputArea,
            "planner source bbox metric agrees with independent literal envelopes");
        check(PcbPlacementPlanner.totalWireDemand(fixture.plan.footprints,
                new TopologyPlacementGraph(fixture.board)) == (double) inputWire,
            "planner source wire metric agrees with independent parallel-net distances");

        PcbPlacementPlanner.Plan first = PcbPlacementPlanner.compactConnectedVacancies(
            fixture.board, fixture.constraints, fixture.plan);
        String firstResult = planSnapshot(first);
        PcbPlacementPlanner.Plan repeated = PcbPlacementPlanner.compactConnectedVacancies(
            fixture.board, fixture.constraints, fixture.plan);

        check(first != fixture.plan && first.evaluations > fixture.plan.evaluations &&
                first.evaluations <= fixture.plan.evaluations + 15,
            "one canonical pass counts no more than five trials per movable LED");
        check(firstResult.equals(planSnapshot(repeated)),
            "the same immutable input produces a deterministic placement and count");
        check(inputBefore.equals(planSnapshot(fixture.plan)),
            "connected-pocket trials leave the source plan and footprints unchanged");
        check(boardBefore.equals(graphSnapshot(fixture.board)),
            "connected-pocket trials leave source component, pad, and net identity unchanged");

        PcbFootprint a = footprint(first, "A");
        PcbFootprint b = footprint(first, "B");
        PcbFootprint c = footprint(first, "C");
        PcbFootprint j = footprint(first, "J");
        check(a.getPlacement().getX() == 120 && a.getPlacement().getY() == 100,
            "A stays at its literal coordinatewise wire-median origin");
        check(b.getPlacement().getX() == 270 && b.getPlacement().getY() == 90,
            "largest free-cell pocket moves B to independently derived origin 270,90");
        check(c.getPlacement().getX() == 430 && c.getPlacement().getY() == 90,
            "updated largest free-cell pocket moves C to independently derived origin 430,90");
        check(j.getPlacement().getX() == 120 && j.getPlacement().getY() == 400 &&
                j.getPlacement().getPhysicalPackage().isConnector() &&
                "MIRROR_X".equals(j.getPlacement().getGeometryTransformKey()) &&
                fixture.constraints.get("J").anchor == PcbPlacementConstraints.Anchor.LEFT,
            "the real left-anchored connector is preserved as a topology and obstacle anchor");
        assertLedPads(a, 120, 100);
        assertLedPads(b, 270, 90);
        assertLedPads(c, 430, 90);
        check(inputArea > literalEnvelopeArea(first) &&
                literalEnvelopeArea(first) == 232928L,
            "literal endpoint envelopes shrink bbox area from 726414 to 232928");
        check(inputWire > literalWireDemand(first) &&
                literalWireDemand(first) == 2560L,
            "literal parallel-net distances shrink weighted wire demand from 11040 to 2560");
        check(PcbPlacementPlanner.occupiedEnvelopeArea(first.footprints,
                fixture.constraints) == literalEnvelopeArea(first),
            "planner result bbox metric matches independently computed final envelopes");
        check(PcbPlacementPlanner.totalWireDemand(first.footprints,
                new TopologyPlacementGraph(fixture.board)) ==
                (double) literalWireDemand(first),
            "planner result wire metric matches independently computed final distances");
        namedFixture("literalConnectedPocketPositive");
    }

    private static void literalConnectedPairPositive() {
        Fixture fixture = pairedFixture();
        String boardBefore = graphSnapshot(fixture.board);
        String inputBefore = planSnapshot(fixture.plan);
        long inputArea = literalEnvelopeArea(fixture.plan);
        long inputWire = literalPairWireDemand(fixture.plan);
        check(fixture.board.getComponentIds().size() == 4 &&
                fixture.board.getPadIds().size() == 11 &&
                fixture.board.getNetIds().size() == 8 &&
                fixture.constraints.getParts().size() == 4 &&
                fixture.board.getComponent("J").getPhysicalPackage().isConnector() &&
                fixture.constraints.get("J").anchor == PcbPlacementConstraints.Anchor.LEFT,
            "literal pair fixture contains two linked two-terminal parts, one fixed multi-terminal part, and one singleton connector");
        check(fixture.board.getNet("PAIR_LINK").getPadIds().size() == 2 &&
                fixture.board.getNet("PAIR_LINK").getPadIds().contains("A.A") &&
                fixture.board.getNet("PAIR_LINK").getPadIds().contains("B.A") &&
                fixture.board.getComponent("A").getPhysicalPackage().getTerminalIds().size() == 2 &&
                fixture.board.getComponent("B").getPhysicalPackage().getTerminalIds().size() == 2 &&
                fixture.board.getComponent("U").getPhysicalPackage().getTerminalIds().size() == 5,
            "the positive net is exact-two-pad and the linked pair is distinct from its five-terminal endpoint");
        check(fixture.constraints.get("A").anchor == PcbPlacementConstraints.Anchor.NONE &&
                fixture.constraints.get("B").anchor == PcbPlacementConstraints.Anchor.NONE &&
                fixture.constraints.get("A").regionId.equals(fixture.constraints.get("B").regionId) &&
                fixture.constraints.get("A").domainId.equals(fixture.constraints.get("B").domainId) &&
                fixture.constraints.get("A").terminalDomain("A").equals(
                    fixture.constraints.get("B").terminalDomain("A")),
            "the linked pair has matching non-anchor region, domain, and terminal-domain declarations");
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            fixture.plan.outline, fixture.plan.footprints);
        check(inputArea == 900130L,
            "independent expanded package envelopes give literal pair source area 900130");
        check(inputWire == 18900L,
            "independent pad distances give literal linked-pair source wire demand 18900");
        check(PcbPlacementPlanner.occupiedEnvelopeArea(fixture.plan.footprints,
                fixture.constraints) == inputArea,
            "planner source envelope metric agrees with the independent pair envelope oracle");
        check(PcbPlacementPlanner.totalWireDemand(fixture.plan.footprints,
                new TopologyPlacementGraph(fixture.board)) == (double) inputWire,
            "planner source wire metric agrees with the independent pair pad oracle");

        PcbPlacementPlanner.Plan first = PcbPlacementPlanner.compactConnectedPairs(
            fixture.board, fixture.constraints, fixture.plan);
        PcbPlacementPlanner.Plan repeated = PcbPlacementPlanner.compactConnectedPairs(
            fixture.board, fixture.constraints, fixture.plan);
        check(first != fixture.plan && first.evaluations > fixture.plan.evaluations &&
                first.evaluations <= fixture.plan.evaluations + 5 &&
                first.evaluations <= PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS,
            "one linked pair consumes at most the five shared vacancy trials and stays within the 60000 budget");
        check(planSnapshot(first).equals(planSnapshot(repeated)),
            "the same immutable pair input produces a deterministic placement and count");
        check(inputBefore.equals(planSnapshot(fixture.plan)) &&
                boardBefore.equals(graphSnapshot(fixture.board)) &&
                fixture.board.getPlacementConstraints() == fixture.constraints,
            "paired trials leave the source plan, graph, and live constraints unchanged");
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            first.outline, first.footprints);

        PcbFootprint sourceA = footprint(fixture.plan, "A");
        PcbFootprint sourceB = footprint(fixture.plan, "B");
        PcbFootprint resultA = footprint(first, "A");
        PcbFootprint resultB = footprint(first, "B");
        int dxA = resultA.getPlacement().getX() - sourceA.getPlacement().getX();
        int dyA = resultA.getPlacement().getY() - sourceA.getPlacement().getY();
        int dxB = resultB.getPlacement().getX() - sourceB.getPlacement().getX();
        int dyB = resultB.getPlacement().getY() - sourceB.getPlacement().getY();
        check((dxA != 0 || dyA != 0) && dxA == dxB && dyA == dyB,
            "the positive result rigidly translates both linked two-terminal parts by one common displacement");
        check(resultA.getPlacement().getPhysicalPackage().getId().equals(
                    sourceA.getPlacement().getPhysicalPackage().getId()) &&
                resultB.getPlacement().getPhysicalPackage().getId().equals(
                    sourceB.getPlacement().getPhysicalPackage().getId()) &&
                resultA.getPlacement().getGeometryVariantKey().equals(
                    sourceA.getPlacement().getGeometryVariantKey()) &&
                resultB.getPlacement().getGeometryVariantKey().equals(
                    sourceB.getPlacement().getGeometryVariantKey()) &&
                resultA.getPlacement().getGeometryTransformKey().equals(
                    sourceA.getPlacement().getGeometryTransformKey()) &&
                resultB.getPlacement().getGeometryTransformKey().equals(
                    sourceB.getPlacement().getGeometryTransformKey()) &&
                resultA.getPlacement().getRotation() == sourceA.getPlacement().getRotation() &&
                resultB.getPlacement().getRotation() == sourceB.getPlacement().getRotation() &&
                resultA.getPlacement().getMountingSide() == sourceA.getPlacement().getMountingSide() &&
                resultB.getPlacement().getMountingSide() == sourceB.getPlacement().getMountingSide(),
            "pair translation preserves package identity, geometry variant, rotation, and mounting side");
        check(resultA.getPad("A.A").getPadId().equals(sourceA.getPad("A.A").getPadId()) &&
                resultA.getPad("A.K").getPadId().equals(sourceA.getPad("A.K").getPadId()) &&
                resultB.getPad("B.A").getPadId().equals(sourceB.getPad("B.A").getPadId()) &&
                resultB.getPad("B.K").getPadId().equals(sourceB.getPad("B.K").getPadId()),
            "pair translation preserves all electrical pad identities");
        long resultArea = literalEnvelopeArea(first);
        long resultWire = literalPairWireDemand(first);
        check(resultArea < inputArea && resultWire < inputWire,
            "the independently measured rigid pair result improves both envelope area and wire demand");
        check(PcbPlacementPlanner.occupiedEnvelopeArea(first.footprints,
                fixture.constraints) == resultArea &&
                PcbPlacementPlanner.totalWireDemand(first.footprints,
                    new TopologyPlacementGraph(fixture.board)) == (double) resultWire,
            "full candidate validation and planner metrics agree with independent pair result measurements");
        namedFixture("literalConnectedPairPositive");
    }

    private static void pairedEligibilityExclusions() {
        Fixture nonExact = nonExactPairFixture();
        PcbPlacementPlanner.validate(nonExact.board, nonExact.constraints,
            nonExact.plan.outline, nonExact.plan.footprints);
        check(nonExact.board.getNet("PAIR_LINK").getPadIds().size() == 3,
            "negative pair fixture uses a literal three-pad link instead of an exact two-pad net");
        check(PcbPlacementPlanner.compactConnectedPairs(nonExact.board,
                nonExact.constraints, nonExact.plan) == nonExact.plan,
            "a three-pad net cannot create a linked pair candidate");

        Fixture anchored = anchoredPairFixture();
        PcbPlacementPlanner.validate(anchored.board, anchored.constraints,
            anchored.plan.outline, anchored.plan.footprints);
        check(anchored.constraints.get("J1").anchor == PcbPlacementConstraints.Anchor.LEFT &&
                anchored.constraints.get("J2").anchor == PcbPlacementConstraints.Anchor.LEFT &&
                PcbPlacementPlanner.compactConnectedPairs(anchored.board,
                    anchored.constraints, anchored.plan) == anchored.plan,
            "edge-anchored two-terminal connectors are excluded without changing their source plan");

        Fixture domainMismatch = domainMismatchPairFixture();
        PcbPlacementPlanner.validate(domainMismatch.board, domainMismatch.constraints,
            domainMismatch.plan.outline, domainMismatch.plan.footprints);
        check(domainMismatch.board.getPlacementConstraints() == domainMismatch.constraints &&
                !domainMismatch.constraints.get("A").terminalDomain("A").equals(
                    domainMismatch.constraints.get("B").terminalDomain("A")) &&
                PcbPlacementPlanner.compactConnectedPairs(domainMismatch.board,
                    domainMismatch.constraints, domainMismatch.plan) == domainMismatch.plan,
            "a pair with mismatched linked terminal domains is excluded without inventing a connection");

        Fixture physicalMismatch = physicalEligibilityFixture();
        PcbPlacementPlanner.validate(physicalMismatch.board, physicalMismatch.constraints,
            physicalMismatch.plan.outline, physicalMismatch.plan.footprints);
        check(physicalMismatch.board.getComponent("U").getPhysicalPackage().getTerminalIds().size() != 2 &&
                physicalMismatch.board.getComponent("V").getPhysicalPackage().getTerminalIds().size() != 2 &&
                PcbPlacementPlanner.compactConnectedPairs(physicalMismatch.board,
                    physicalMismatch.constraints, physicalMismatch.plan) == physicalMismatch.plan,
            "exact-two-pad links between multi-terminal packages are excluded by physical eligibility");
        namedFixture("pairedEligibilityExclusions");
    }

    private static void pairedEvaluationBoundAndCancellation() {
        Fixture fixture = pairedFixture();
        PcbPlacementPlanner.Plan nearLimit = withEvaluations(fixture.plan,
            PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS - 1);
        PcbPlacementPlanner.Plan bounded = PcbPlacementPlanner.compactConnectedPairs(
            fixture.board, fixture.constraints, nearLimit);
        check(bounded.evaluations >= PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS - 1 &&
                bounded.evaluations <= PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS,
            "paired search shares the exact 60000 evaluation ceiling");
        PcbPlacementPlanner.Plan exhausted = withEvaluations(fixture.plan,
            PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS);
        check(PcbPlacementPlanner.compactConnectedPairs(fixture.board,
                fixture.constraints, exhausted) == exhausted,
            "an exhausted paired search returns the exact source plan without extra work");

        final Fixture canceledFixture = pairedFixture();
        final String before = planSnapshot(canceledFixture.plan);
        GenerationJob job = new GenerationJob(new ScopeService(), 100000L, 8, 5000L);
        job.cancel();
        GenerationWorkScope.enter(job);
        boolean propagated = false;
        try {
            try {
                PcbPlacementPlanner.compactConnectedPairs(canceledFixture.board,
                    canceledFixture.constraints, canceledFixture.plan);
            } catch (GenerationJob.Cancelled expected) {
                propagated = expected.getOutcome() == GenerationJob.Outcome.CANCELLED;
            }
        } finally {
            GenerationWorkScope.exit(job);
        }
        check(propagated && job.getOutcome() == GenerationJob.Outcome.CANCELLED &&
                before.equals(planSnapshot(canceledFixture.plan)),
            "paired cancellation propagates at entry and leaves the source plan untouched");
        boolean scopeCleared = false;
        try {
            GenerationWorkScope.check();
            scopeCleared = true;
        } catch (GenerationJob.Cancelled leakedScope) {
            // A cancelled owner surviving exit would make this fresh checkpoint fail.
        }
        check(scopeCleared,
            "paired cancellation cleanup removes the owner before the next checkpoint");
        namedFixture("pairedEvaluationBoundAndCancellation");
    }

    private static void countedNoOpAndLegacyFlag() {
        Fixture connected = noOpFixture(true);
        String boardBefore = graphSnapshot(connected.board);
        String inputBefore = planSnapshot(connected.plan);
        check(literalEnvelopeArea(connected.plan) == 98980L,
            "tight connector-led cluster has independently calculated area 98980");
        check(literalSingletonNetWireDemand(connected.board) == 0L &&
                PcbPlacementPlanner.totalWireDemand(connected.plan.footprints,
                    new TopologyPlacementGraph(connected.board)) == 0.0,
            "independent singleton pad nets have no pairwise wire demand");

        PcbPlacementPlanner.Plan counted = PcbPlacementCompactor.compact(
            connected.board, connected.constraints, connected.plan);
        check(counted != connected.plan &&
                counted.evaluations > connected.plan.evaluations &&
                counted.evaluations <= connected.plan.evaluations + 5,
            "connected orchestration records bounded rejected trials");
        check(PcbPlacementCompactor.samePlacement(connected.plan, counted),
            "evaluation-only no-op remains the same route placement");
        check(inputBefore.equals(planSnapshot(connected.plan)) &&
                boardBefore.equals(graphSnapshot(connected.board)),
            "no-op orchestration preserves both its source plan and source graph");

        Fixture legacy = noOpFixture(false);
        check(legacy.constraints.placementPackingVersion == 1,
            "legacy fixture keeps explicit placement packing version 1");
        check(PcbPlacementPlanner.compactConnectedVacancies(legacy.board,
                legacy.constraints, legacy.plan) == legacy.plan,
            "direct connected-pocket entry is an exact no-op for packing version 1");
        check(PcbPlacementCompactor.compact(legacy.board, legacy.constraints,
                legacy.plan) == legacy.plan,
            "legacy strip orchestration returns the exact source plan when no strip exists");
        namedFixture("countedNoOpAndLegacyFlag");
    }

    private static void evaluationCeiling() {
        Fixture fixture = noOpFixture(true);
        PcbPlacementPlanner.Plan nearLimit = withEvaluations(fixture.plan,
            PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS - 1);
        PcbPlacementPlanner.Plan lastTrial = PcbPlacementCompactor.compact(
            fixture.board, fixture.constraints, nearLimit);
        check(lastTrial.evaluations == PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS,
            "one remaining evaluation is consumed without crossing the 60000 ceiling");
        check(PcbPlacementCompactor.samePlacement(nearLimit, lastTrial),
            "the final rejected trial changes work count but not route geometry");

        PcbPlacementPlanner.Plan exhausted = withEvaluations(fixture.plan,
            PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS);
        PcbPlacementPlanner.Plan unchanged = PcbPlacementCompactor.compact(
            fixture.board, fixture.constraints, exhausted);
        check(unchanged == exhausted && unchanged.evaluations ==
                PcbPlacementPlanner.MEDIUM_MAX_EVALUATIONS,
            "an already exhausted placement pass returns the exact plan without extra work");
        namedFixture("evaluationCeiling");
    }

    private static void cancellationAndScopeCleanup() {
        final Fixture fixture = noOpFixture(true);
        final String before = planSnapshot(fixture.plan);
        GenerationJob job = new GenerationJob(new ScopeService(), 100000L, 8, 5000L);
        job.cancel();
        GenerationWorkScope.enter(job);
        boolean propagated = false;
        try {
            try {
                PcbPlacementPlanner.compactConnectedVacancies(fixture.board,
                    fixture.constraints, fixture.plan);
            } catch (GenerationJob.Cancelled expected) {
                propagated = expected.getOutcome() == GenerationJob.Outcome.CANCELLED;
            }
        } finally {
            GenerationWorkScope.exit(job);
        }
        check(propagated && job.getOutcome() == GenerationJob.Outcome.CANCELLED,
            "the connected-pocket entry checkpoint propagates a cancelled generation owner");
        check(before.equals(planSnapshot(fixture.plan)),
            "cancellation leaves the source plan untouched");
        boolean scopeCleared = false;
        try {
            GenerationWorkScope.check();
            scopeCleared = true;
        } catch (GenerationJob.Cancelled leakedScope) {
            // A cancelled owner surviving exit would make this fresh checkpoint fail.
        }
        check(scopeCleared,
            "scope cleanup removes the cancelled owner before the next checkpoint");
        namedFixture("cancellationAndScopeCleanup");
    }

    private static void singleFacePackingFlagDoesNotPrecompact() {
        TroubleshootBoard board = Rb30Plan.reference(13).board();
        PcbPlacementConstraints boardConstraints = board.getPlacementConstraints();
        check(board.getComponentIds().size() >= 20 &&
                boardConstraints.getParts().size() == board.getComponentIds().size(),
            "the real RB30 reference fixture exercises the medium planner inventory");
        check(MediumBoardPhysicalPolicy.ID.equals(boardConstraints.getPhysicalPolicyId()) &&
                boardConstraints.getPhysicalPolicyVersion() == MediumBoardPhysicalPolicy.VERSION &&
                boardConstraints.placementPackingVersion ==
                    PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION,
            "the source board retains its independent normal medium-policy declaration");

        PcbPlacementConstraints singleFaceLegacy = singleFaceCopy(boardConstraints, 1);
        PcbPlacementConstraints singleFacePacking2 = singleFaceCopy(boardConstraints,
            PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION);
        check(PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID.equals(
                    singleFaceLegacy.getPhysicalPolicyId()) &&
                singleFaceLegacy.getPhysicalPolicyVersion() ==
                    PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION &&
                singleFaceLegacy.placementPackingVersion == 1 &&
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION == 4 &&
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID.equals(
                    singleFacePacking2.getPhysicalPolicyId()) &&
                singleFacePacking2.getPhysicalPolicyVersion() ==
                    PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION &&
                singleFacePacking2.placementPackingVersion ==
                    PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION &&
                singleFaceLegacy.getParts().equals(singleFacePacking2.getParts()) &&
                singleFaceLegacy.getBarriers().equals(singleFacePacking2.getBarriers()) &&
                singleFaceLegacy.routingLayer == singleFacePacking2.routingLayer &&
                singleFaceLegacy.placementDensityVersion ==
                    singleFacePacking2.placementDensityVersion &&
                singleFaceLegacy.requiresTwoLayerRouting ==
                    singleFacePacking2.requiresTwoLayerRouting &&
                singleFaceLegacy.getTraceWidthProfile() ==
                    singleFacePacking2.getTraceWidthProfile(),
            "external single-face copies retain identical declarations and differ only by packing version");
        check(rejectsPackingVersion(boardConstraints, 2) &&
                rejectsPackingVersion(boardConstraints, 3) &&
                rejectsPackingVersion(boardConstraints, 5),
            "obsolete packing versions 2 and 3 and unknown version 5 are rejected while current version 4 remains explicit");

        String graphBefore = graphSnapshot(board);
        PcbPlacementPlanner planner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        PcbPlacementPlanner.Plan legacyPlan = planner.plan(board, singleFaceLegacy, 0L, 0);
        PcbPlacementPlanner.Plan packing2Plan = planner.plan(board, singleFacePacking2, 0L, 0);
        check(planSnapshot(legacyPlan).equals(planSnapshot(packing2Plan)),
            "actual candidate-0 planner output and evaluation count match under default single-face policy");
        check(graphBefore.equals(graphSnapshot(board)) &&
                board.getPlacementConstraints() == boardConstraints,
            "external planner constraints leave the RB30 source graph and live constraints untouched");
        namedFixture("singleFacePackingFlagDoesNotPrecompact");
    }

    /** Regression: this root's display crop is off the router's ten-unit phase. */
    private static void croppedDisplayFrameIsNotRerouted() {
        GeneratedBoardInstance first = null, repeated = null;
        CirSim prior = CircuitElm.sim;
        CirSim simulator = new CirSim();
        simulator.elmList = new Vector<CircuitElm>();
        simulator.gridSize = 16;
        simulator.gridMask = ~15;
        simulator.gridRound = 7;
        CircuitElm.sim = simulator;
        try {
            first = new DiodeProtectedIndicatorGenerator().generate(0L);
            PcbBoardLayout layout = first.getPcbLayout();
            String graph = graphSnapshot(first.getBoard());
            layout.validateGeometry(first.getBoard());
            check(graph.equals(graphSnapshot(first.getBoard())),
                "actual diode root0 final physical validation preserves its source graph");
            PcbPackingProbeReceipt receipt = layout.getPackingProbeReceipt();
            check(receipt != null && receipt.attempts == 1,
                "actual diode root0 completes its optional routed probe");
            receipt.requireGeometry(layout);
            Rectangle outline = layout.getBoardOutline();
            boolean offRoutingPhase = false;
            for (PcbPadPlacement pad : layout.getPads())
                if ((pad.getX() - outline.x) % 10 != 0 ||
                        (pad.getY() - outline.y) % 10 != 0) offRoutingPhase = true;
            check(offRoutingPhase,
                "the real finished display crop remains off-phase and is not a router input");
            repeated = new DiodeProtectedIndicatorGenerator().generate(0L);
            check(layout.geometryFingerprint().equals(repeated.getPcbLayout().geometryFingerprint()),
                "retained plan-frame routing gives exact repeated final geometry");
        } finally {
            try {
                dispose(first);
                dispose(repeated);
            } finally {
                CircuitElm.sim = prior;
            }
        }
        namedFixture("croppedDisplayFrameIsNotRerouted");
    }

    private static void dispose(GeneratedBoardInstance owner) {
        if (owner == null) return;
        owner.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : owner.getSimulationElements()) element.delete();
    }

    private static Fixture pairedFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pair-positive");
        for (String net : new String[] { "PAIR_LINK", "A_TO_U", "B_TO_U",
                "U_RAIL", "U_OUTPUT", "U_RETURN", "J_ONE", "J_TWO" })
            board.addNet(new BoardNet(net));
        addLed(board, "A", "PAIR_LINK", "A_TO_U");
        addLed(board, "B", "PAIR_LINK", "B_TO_U");
        addMultiTerminal(board, "U", "CONTROLLER", PhysicalPackages.E04_DECISION_CONTROL_5,
            "A_TO_U", "B_TO_U", "U_RAIL", "U_OUTPUT", "U_RETURN");
        addConnector(board, "J", "J_ONE", "J_TWO");
        PcbPlacementConstraints constraints = connectedConstraints(board);
        board.setPlacementConstraints(constraints);

        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("U"), 100, 100));
        footprints.add(mirroredConnector(board.getComponent("J"), 500, 130));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("A"), 900, 700));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("B"), 1150, 700));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1500, 1300), footprints, 0));
    }

    private static Fixture nonExactPairFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pair-non-exact");
        for (String net : new String[] { "PAIR_LINK", "A_RETURN", "B_RETURN", "C_RETURN",
                "J_ONE", "J_TWO" })
            board.addNet(new BoardNet(net));
        addLed(board, "A", "PAIR_LINK", "A_RETURN");
        addLed(board, "B", "PAIR_LINK", "B_RETURN");
        addLed(board, "C", "PAIR_LINK", "C_RETURN");
        addConnector(board, "J", "J_ONE", "J_TWO");
        PcbPlacementConstraints constraints = connectedConstraints(board);
        board.setPlacementConstraints(constraints);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("A"), 250, 200));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("B"), 600, 200));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("C"), 950, 200));
        footprints.add(mirroredConnector(board.getComponent("J"), 1000, 500));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 700), footprints, 0));
    }

    private static Fixture anchoredPairFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pair-anchored");
        for (String net : new String[] { "PAIR_LINK", "J1_RETURN", "J2_RETURN" })
            board.addNet(new BoardNet(net));
        addConnector(board, "J1", "PAIR_LINK", "J1_RETURN");
        addConnector(board, "J2", "PAIR_LINK", "J2_RETURN");
        PcbPlacementConstraints constraints = connectedConstraints(board);
        board.setPlacementConstraints(constraints);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(mirroredConnector(board.getComponent("J1"), 250, 200));
        footprints.add(mirroredConnector(board.getComponent("J2"), 700, 200));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 700), footprints, 0));
    }

    private static Fixture domainMismatchPairFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pair-domain-mismatch");
        for (String net : new String[] { "PAIR_LINK", "A_RETURN", "B_RETURN", "J_ONE", "J_TWO" })
            board.addNet(new BoardNet(net));
        addLed(board, "A", "PAIR_LINK", "A_RETURN");
        addLed(board, "B", "PAIR_LINK", "B_RETURN");
        addConnector(board, "J", "J_ONE", "J_TWO");
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("A", "all", "All", "board-a",
            PcbPlacementConstraints.Anchor.NONE, 20));
        parts.add(new PcbPlacementConstraints.Part("B", "all", "All", "board-b",
            PcbPlacementConstraints.Anchor.NONE, 20));
        parts.add(new PcbPlacementConstraints.Part("J", "all", "All", "board",
            PcbPlacementConstraints.Anchor.LEFT, 20));
        PcbPlacementConstraints constraints = new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>()).withConnectedPocketPacking();
        board.setPlacementConstraints(constraints);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("A"), 250, 200));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("B"), 700, 200));
        footprints.add(mirroredConnector(board.getComponent("J"), 1000, 500));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 700), footprints, 0));
    }

    private static Fixture physicalEligibilityFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pair-physical-eligibility");
        for (String net : new String[] { "MULTI_LINK", "U_REFERENCE", "U_RAIL",
                "U_OUTPUT", "U_RETURN", "V_OUTPUT", "V_RETURN", "V_ENABLE",
                "J_ONE", "J_TWO" })
            board.addNet(new BoardNet(net));
        addMultiTerminal(board, "U", "CONTROLLER", PhysicalPackages.E04_DECISION_CONTROL_5,
            "MULTI_LINK", "U_REFERENCE", "U_RAIL", "U_OUTPUT", "U_RETURN");
        addMultiTerminal(board, "V", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
            "MULTI_LINK", "V_OUTPUT", "V_RETURN", "V_ENABLE");
        addConnector(board, "J", "J_ONE", "J_TWO");
        PcbPlacementConstraints constraints = connectedConstraints(board);
        board.setPlacementConstraints(constraints);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("U"), 120, 180));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("V"), 700, 180));
        footprints.add(mirroredConnector(board.getComponent("J"), 1000, 500));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 700), footprints, 0));
    }

    private static Fixture positiveFixture() {
        TroubleshootBoard board = new TroubleshootBoard("connected-pocket-positive");
        board.addNet(new BoardNet("ONE"));
        board.addNet(new BoardNet("TWO"));
        board.addNet(new BoardNet("J_ONE"));
        board.addNet(new BoardNet("J_TWO"));
        addLed(board, "A", "ONE", "TWO");
        addLed(board, "B", "ONE", "TWO");
        addLed(board, "C", "ONE", "TWO");
        addConnector(board, "J", "J_ONE", "J_TWO");
        PcbPlacementConstraints constraints = connectedConstraints(board);
        board.setPlacementConstraints(constraints);

        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("A"), 120, 100));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("B"), 800, 100));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("C"), 120, 800));
        footprints.add(mirroredConnector(board.getComponent("J"), 120, 400));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 1200), footprints, 0));
    }

    private static Fixture noOpFixture(boolean connectedPocket) {
        TroubleshootBoard board = new TroubleshootBoard("connected-pocket-no-op");
        addUniqueNetLed(board, "L");
        addUniqueNetConnector(board, "J1");
        addUniqueNetConnector(board, "J2");
        PcbPlacementConstraints base = baseConstraints(board);
        PcbPlacementConstraints constraints = connectedPocket ?
            base.withConnectedPocketPacking() : base;
        board.setPlacementConstraints(constraints);

        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(mirroredConnector(board.getComponent("J1"), 100, 100));
        footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent("L"), 250, 100));
        footprints.add(mirroredConnector(board.getComponent("J2"), 414, 100));
        return new Fixture(board, constraints,
            plan(new Rectangle(40, 40, 1200, 1200), footprints, 0));
    }

    private static PcbPlacementConstraints connectedConstraints(TroubleshootBoard board) {
        return baseConstraints(board).withConnectedPocketPacking();
    }

    private static PcbPlacementConstraints singleFaceCopy(
            PcbPlacementConstraints source, int packingVersion) {
        return new PcbPlacementConstraints(source.getParts(), source.getBarriers(),
            source.routingLayer, PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION,
            source.placementDensityVersion, source.requiresTwoLayerRouting,
            source.getTraceWidthProfile(), packingVersion);
    }

    private static boolean rejectsPackingVersion(
            PcbPlacementConstraints source, int packingVersion) {
        try {
            singleFaceCopy(source, packingVersion);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static PcbPlacementConstraints baseConstraints(TroubleshootBoard board) {
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        Vector<String> ids = board.getComponentIds();
        Collections.sort(ids);
        for (String id : ids) {
            boolean connector = board.getComponent(id).getPhysicalPackage().isConnector();
            parts.add(new PcbPlacementConstraints.Part(id, "all", "All", "board",
                connector ? PcbPlacementConstraints.Anchor.LEFT :
                    PcbPlacementConstraints.Anchor.NONE, 20));
        }
        return new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>());
    }

    private static void addLed(TroubleshootBoard board, String id, String anodeNet,
            String cathodeNet) {
        board.addComponent(new BoardComponent(id, "LED", PhysicalPackages.THROUGH_HOLE_LED));
        board.addPad(new BoardPad(id + ".A", id, "A", anodeNet));
        board.addPad(new BoardPad(id + ".K", id, "K", cathodeNet));
    }

    private static void addConnector(TroubleshootBoard board, String id,
            String firstNet, String secondNet) {
        board.addComponent(new BoardComponent(id, "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        board.addPad(new BoardPad(id + ".1", id, "1", firstNet));
        board.addPad(new BoardPad(id + ".2", id, "2", secondNet));
    }

    private static void addMultiTerminal(TroubleshootBoard board, String id, String type,
            PhysicalPackage physical, String... nets) {
        Vector<String> terminals = physical.getTerminalIds();
        if (terminals.size() != nets.length)
            throw new IllegalArgumentException("Pair fixture terminal count: " + id);
        board.addComponent(new BoardComponent(id, type, physical));
        for (int index = 0; index < terminals.size(); index++)
            board.addPad(new BoardPad(id + "." + terminals.get(index), id,
                terminals.get(index), nets[index]));
    }

    private static void addUniqueNetLed(TroubleshootBoard board, String id) {
        String anode = id + "_A_NET", cathode = id + "_K_NET";
        board.addNet(new BoardNet(anode));
        board.addNet(new BoardNet(cathode));
        addLed(board, id, anode, cathode);
    }

    private static void addUniqueNetConnector(TroubleshootBoard board, String id) {
        String first = id + "_1_NET", second = id + "_2_NET";
        board.addNet(new BoardNet(first));
        board.addNet(new BoardNet(second));
        addConnector(board, id, first, second);
    }

    private static PcbFootprint mirroredConnector(BoardComponent component, int x, int y) {
        PhysicalPackageGeometry mirrored = component.getPhysicalPackage().getGeometryVariant(
            "DEFAULT_MIRRORED_X").getGeometry();
        return PcbFootprint.fromPhysicalPackage(component, x, y, mirrored);
    }

    private static PcbPlacementPlanner.Plan plan(Rectangle outline,
            Vector<PcbFootprint> footprints, int evaluations) {
        return new PcbPlacementPlanner.Plan(outline, footprints,
            new Vector<PcbLayoutRegion>(), 0L, 0L, 0, evaluations);
    }

    private static PcbPlacementPlanner.Plan withEvaluations(
            PcbPlacementPlanner.Plan source, int evaluations) {
        return new PcbPlacementPlanner.Plan(source.outline, source.footprints,
            source.regions, source.courtyardArea, source.demandArea, source.candidate,
            evaluations);
    }

    private static PcbFootprint footprint(PcbPlacementPlanner.Plan plan, String id) {
        for (PcbFootprint footprint : plan.footprints)
            if (id.equals(footprint.getPlacement().getComponentId())) return footprint;
        throw new AssertionError("Missing planned component " + id);
    }

    private static void assertLedPads(PcbFootprint footprint, int x, int y) {
        String id = footprint.getPlacement().getComponentId();
        check(footprint.getPlacement().getX() == x && footprint.getPlacement().getY() == y,
            id + " placement origin is retained literally");
        check(footprint.getPad(id + ".A").getX() == x + 20 &&
                footprint.getPad(id + ".A").getY() == y + 70,
            id + ".A remains the real package terminal at its translated pad coordinate");
        check(footprint.getPad(id + ".K").getX() == x + 60 &&
                footprint.getPad(id + ".K").getY() == y + 70,
            id + ".K remains the real package terminal at its translated pad coordinate");
    }

    private static long literalWireDemand(PcbPlacementPlanner.Plan plan) {
        PcbFootprint a = footprint(plan, "A");
        PcbFootprint b = footprint(plan, "B");
        PcbFootprint c = footprint(plan, "C");
        long pairDistance = manhattan(a, b) + manhattan(a, c) + manhattan(b, c);
        // Two distinct three-pad nets; each undirected pair appears from both endpoints.
        return pairDistance * 4L;
    }

    private static long literalPairWireDemand(PcbPlacementPlanner.Plan plan) {
        PcbFootprint a = footprint(plan, "A");
        PcbFootprint b = footprint(plan, "B");
        PcbFootprint u = footprint(plan, "U");
        long pair = padManhattan(a, "A.A", b, "B.A");
        long aEndpoint = padManhattan(a, "A.K", u, "U.SENSOR");
        long bEndpoint = padManhattan(b, "B.K", u, "U.REFERENCE");
        // Each exact two-pad net contributes one directed link per endpoint;
        // the topology weight for those links is the literal value three.
        return (pair + aEndpoint + bEndpoint) * 6L;
    }

    private static long padManhattan(PcbFootprint first, String firstPad,
            PcbFootprint second, String secondPad) {
        PcbPadPlacement a = first.getPad(firstPad);
        PcbPadPlacement b = second.getPad(secondPad);
        return Math.abs((long) a.getX() - b.getX()) +
            Math.abs((long) a.getY() - b.getY());
    }

    private static long literalSingletonNetWireDemand(TroubleshootBoard board) {
        Vector<String> netIds = board.getNetIds();
        for (String netId : netIds)
            if (board.getNet(netId).getPadIds().size() != 1)
                throw new AssertionError("No-op net must have one literal pad: " + netId);
        return 0L;
    }

    private static long manhattan(PcbFootprint first, PcbFootprint second) {
        long dx = (long) first.getPlacement().getX() - second.getPlacement().getX();
        long dy = (long) first.getPlacement().getY() - second.getPlacement().getY();
        return Math.abs(dx) + Math.abs(dy);
    }

    private static long literalEnvelopeArea(PcbPlacementPlanner.Plan plan) {
        long left = Long.MAX_VALUE, top = Long.MAX_VALUE;
        long right = Long.MIN_VALUE, bottom = Long.MIN_VALUE;
        for (PcbFootprint footprint : plan.footprints) {
            Rectangle local = literalEnvelope(footprint);
            long x = (long) footprint.getPlacement().getX() + local.x;
            long y = (long) footprint.getPlacement().getY() + local.y;
            left = Math.min(left, x);
            top = Math.min(top, y);
            right = Math.max(right, x + local.width);
            bottom = Math.max(bottom, y + local.height);
        }
        return (right - left) * (bottom - top);
    }

    private static Rectangle literalEnvelope(PcbFootprint footprint) {
        PcbComponentPlacement placement = footprint.getPlacement();
        String packageId = placement.getPhysicalPackage().getId();
        if ("THROUGH_HOLE_LED".equals(packageId)) return LED_ENVELOPE;
        if ("E04_DECISION_CONTROL_5".equals(packageId)) return E04_ENVELOPE;
        if ("THROUGH_HOLE_CONNECTOR_2".equals(packageId) &&
                "MIRROR_X".equals(placement.getGeometryTransformKey()))
            return MIRRORED_CONNECTOR_ENVELOPE;
        throw new AssertionError("Fixture package has no independent literal envelope: " +
            packageId + "/" + placement.getGeometryTransformKey());
    }

    private static String graphSnapshot(TroubleshootBoard board) {
        StringBuilder result = new StringBuilder();
        Vector<String> components = board.getComponentIds();
        Collections.sort(components);
        for (String id : components) {
            BoardComponent component = board.getComponent(id);
            result.append("C:").append(id).append(':')
                .append(component.getPhysicalPackage().getId()).append('|');
            Vector<String> pads = component.getPadIds();
            Collections.sort(pads);
            for (String padId : pads) {
                BoardPad pad = board.getPad(padId);
                result.append("P:").append(pad.getId()).append(':')
                    .append(pad.getComponentId()).append(':').append(pad.getTerminalId())
                    .append(':').append(pad.getNetId()).append('|');
            }
        }
        Vector<String> nets = board.getNetIds();
        Collections.sort(nets);
        for (String netId : nets) {
            Vector<String> pads = board.getNet(netId).getPadIds();
            Collections.sort(pads);
            result.append("N:").append(netId).append(':').append(pads).append('|');
        }
        result.append("packing=").append(
            board.getPlacementConstraints().getPlacementPackingVersion());
        return result.toString();
    }

    private static String planSnapshot(PcbPlacementPlanner.Plan plan) {
        StringBuilder result = new StringBuilder();
        result.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|')
            .append(plan.courtyardArea).append('|').append(plan.demandArea).append('|')
            .append(plan.candidate).append('|').append(plan.evaluations).append('|');
        for (PcbFootprint footprint : plan.footprints)
            result.append(footprint.geometryFingerprint()).append('\n');
        return result.toString();
    }

    private static void namedFixture(String name) {
        if (name == null || name.length() == 0 || fixtureNames.contains(name))
            throw new AssertionError("Named unique fixture group required: " + name);
        fixtureNames.add(name);
        fixtures++;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan plan;
        Fixture(TroubleshootBoard board, PcbPlacementConstraints constraints,
                PcbPlacementPlanner.Plan plan) {
            this.board = board;
            this.constraints = constraints;
            this.plan = plan;
        }
    }

    private static final class ScopeService implements GenerationJob.Services {
        public int candidateCount() { return 1; }
        public String manifest(int candidate) { return "connected-pocket-cancel"; }
        public void beginCandidate(int index) { }
        public String resolve() { return "resolved"; }
        public String healthy() { return "healthy"; }
        public String physical() { return "physical"; }
        public boolean proveNext() { return false; }
        public String symptom() { return "symptom"; }
        public String dependencies() { return "dependencies"; }
        public void publish(GenerationReceipt receipt) { }
        public void abort() { }
        public boolean isCurrent() { return true; }
        public long nowMillis() { return 0L; }
    }
}
