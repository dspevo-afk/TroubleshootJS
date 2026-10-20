package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Unit contracts for the generic informed14 selected-frame adapter and its
 * observer-owned cohort entrypoint.  The fixture uses package-backed literal
 * geometry and never starts the seeded router.
 */
public final class PcbRoutedSlideCohortContractTest {
    private static final int VERSION = 15;
    private static final long PLACEMENT_SEED = 1357913579L;
    private static final long ROUTING_SEED = -2468024680L;
    private static int assertions;
    private static int fixtures;

    private PcbRoutedSlideCohortContractTest() { }

    public static void main(String[] args) {
        genericFrameAndMetadata();
        genericFrameRejectionAndImmutability();
        cohortObserverCleanup();
        check(fixtures == 3, "exactly three named routed-slide cohort fixture groups");
        System.out.println("PASS: PCB routed slide cohort contracts assertions=" +
            assertions + " fixtures=" + fixtures +
            " evaluator=selectedRoutedSlideCohort");
    }

    private static void genericFrameAndMetadata() {
        Fixture fixture = literalFixture();
        String geometryBefore = fixture.layout.geometryFingerprint();
        String componentBefore = fixture.plan.footprints.get(0).geometryFingerprint();
        PcbRoutedSlideCompactionContractTest.verifyInformedSelectedFrameForDescent(
            14L, fixture.board, fixture.layout, fixture.plan, fixture.attempt,
            PLACEMENT_SEED, ROUTING_SEED);
        check(sameRect(fixture.plan.outline, fixture.layout.getBoardOutline()),
            "generic frame verifier accepts the actual selected outline");
        check(fixture.plan.candidate == fixture.attempt &&
                fixture.plan.courtyardArea == 123456L &&
                fixture.plan.demandArea == 789012L && fixture.plan.evaluations == 37,
            "generic frame verifier retains selected plan metadata independently");
        check(fixture.layout.getComponents().size() == 2 &&
                fixture.layout.getPads().size() == 4 &&
                fixture.layout.getRegions().size() == 1,
            "generic frame verifier accepts complete literal component pad region inventory");
        check(fixture.layout.getGenerationPlacementAttempts() == 6 &&
                fixture.layout.getGenerationRoutingAttempts() == 2 &&
                fixture.layout.getGenerationRoutingExpansions() == 7 &&
                fixture.layout.getGenerationRoutingMillis() == 19L,
            "generic frame verifier retains the assigned generation receipt");
        check(geometryBefore.equals(fixture.layout.geometryFingerprint()) &&
                componentBefore.equals(fixture.plan.footprints.get(0).geometryFingerprint()),
            "generic frame verification leaves literal source geometry unchanged");
        fixture("genericFrameAndMetadata");
    }

    private static void genericFrameRejectionAndImmutability() {
        Fixture fixture = literalFixture();
        String layoutBefore = fixture.layout.geometryFingerprint();
        String planBefore = fixture.plan.footprints.get(0).geometryFingerprint();
        check(rejects(fixture, wrongOutline(fixture.plan)),
            "generic frame verifier rejects an outline mutation");
        check(rejects(fixture, wrongCoordinates(fixture.plan)),
            "generic frame verifier rejects a component and pad coordinate mutation");
        check(rejects(fixture, wrongCandidate(fixture.plan)),
            "generic frame verifier rejects selected-attempt metadata drift");
        check(rejects(fixture, wrongRegion(fixture.plan)),
            "generic frame verifier rejects source-declared region metadata drift");
        check(rejectsWithSeeds(fixture, fixture.plan, PLACEMENT_SEED + 1L,
                ROUTING_SEED),
            "generic frame verifier rejects a source generation-owner mismatch");
        check(layoutBefore.equals(fixture.layout.geometryFingerprint()) &&
                planBefore.equals(fixture.plan.footprints.get(0).geometryFingerprint()),
            "generic frame rejection paths leave the source owners immutable");
        fixture("genericFrameRejectionAndImmutability");
    }

    private static void cohortObserverCleanup() {
        final int[] callbacks = new int[] {0};
        SeededPcbLayoutGenerator.SelectedSlideObserver sentinel =
            new SeededPcbLayoutGenerator.SelectedSlideObserver() {
                public void onSelected(TroubleshootBoard board, PcbBoardLayout layout,
                        PcbPlacementPlanner.Plan plan, String route, int attempt) {
                    callbacks[0]++;
                }
            };
        SeededPcbLayoutGenerator.SelectedSlideObserver previous =
            SeededPcbLayoutGenerator.setSelectedSlideObserver(sentinel);
        boolean rejected = false;
        try {
            PcbRoutedSlideDescentContractTest.observeInformedCohort(
                987654321L, "invalid-cohort-root");
        } catch (AssertionError expected) {
            rejected = true;
        }
        check(rejected,
            "informed cohort entrypoint propagates the fixed-root rejection before generation");
        SeededPcbLayoutGenerator.SelectedSlideObserver restored =
            SeededPcbLayoutGenerator.setSelectedSlideObserver(null);
        check(restored == sentinel && callbacks[0] == 0,
            "informed cohort entrypoint restores the previous observer without inspection");
        SeededPcbLayoutGenerator.setSelectedSlideObserver(previous);
        fixture("cohortObserverCleanup");
    }

    private static boolean rejects(Fixture fixture, PcbPlacementPlanner.Plan plan) {
        return rejectsWithSeeds(fixture, plan, PLACEMENT_SEED, ROUTING_SEED);
    }

    private static boolean rejectsWithSeeds(Fixture fixture,
            PcbPlacementPlanner.Plan plan, long placementSeed, long routingSeed) {
        try {
            PcbRoutedSlideCompactionContractTest.verifyInformedSelectedFrameForDescent(
                14L, fixture.board, fixture.layout, plan, fixture.attempt,
                placementSeed, routingSeed);
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }

    private static PcbPlacementPlanner.Plan wrongOutline(
            PcbPlacementPlanner.Plan source) {
        return new PcbPlacementPlanner.Plan(
            new Rectangle(source.outline.x, source.outline.y,
                source.outline.width + 1, source.outline.height),
            source.footprints, source.regions, source.courtyardArea,
            source.demandArea, source.candidate, source.evaluations);
    }

    private static PcbPlacementPlanner.Plan wrongCoordinates(
            PcbPlacementPlanner.Plan source) {
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(PcbRoutedSlideCompactionContractTest
            .literalLedFootprintForCohort("COHORT_A", 121, 100));
        footprints.add(source.footprints.get(1));
        return new PcbPlacementPlanner.Plan(source.outline, footprints,
            source.regions, source.courtyardArea, source.demandArea,
            source.candidate, source.evaluations);
    }

    private static PcbPlacementPlanner.Plan wrongCandidate(
            PcbPlacementPlanner.Plan source) {
        return new PcbPlacementPlanner.Plan(source.outline, source.footprints,
            source.regions, source.courtyardArea, source.demandArea,
            source.candidate + 1, source.evaluations);
    }

    private static PcbPlacementPlanner.Plan wrongRegion(
            PcbPlacementPlanner.Plan source) {
        Vector<PcbLayoutRegion> regions = new Vector<PcbLayoutRegion>();
        Vector<String> members = new Vector<String>();
        members.add("COHORT_A");
        members.add("COHORT_B");
        regions.add(new PcbLayoutRegion("cohort-region", "Wrong source region", members));
        return new PcbPlacementPlanner.Plan(source.outline, source.footprints,
            regions, source.courtyardArea, source.demandArea, source.candidate,
            source.evaluations);
    }

    private static Fixture literalFixture() {
        TroubleshootBoard board = new TroubleshootBoard("COHORT_FRAME_FIXTURE");
        board.addNet(new BoardNet("COHORT_A_NET", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("COHORT_A_RETURN", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("COHORT_B_NET", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("COHORT_B_RETURN", BoardNet.RoutingRole.SIGNAL));
        board.addComponent(new BoardComponent("COHORT_A", "LED",
            PhysicalPackages.THROUGH_HOLE_LED));
        board.addComponent(new BoardComponent("COHORT_B", "LED",
            PhysicalPackages.THROUGH_HOLE_LED));
        board.addPad(new BoardPad("COHORT_A.A", "COHORT_A", "A", "COHORT_A_NET"));
        board.addPad(new BoardPad("COHORT_A.K", "COHORT_A", "K", "COHORT_A_RETURN"));
        board.addPad(new BoardPad("COHORT_B.A", "COHORT_B", "A", "COHORT_B_NET"));
        board.addPad(new BoardPad("COHORT_B.K", "COHORT_B", "K", "COHORT_B_RETURN"));
        Vector<PcbPlacementConstraints.Part> demands =
            new Vector<PcbPlacementConstraints.Part>();
        demands.add(new PcbPlacementConstraints.Part("COHORT_A", "cohort-region",
            "Cohort region", "fixture", PcbPlacementConstraints.Anchor.NONE, 20));
        demands.add(new PcbPlacementConstraints.Part("COHORT_B", "cohort-region",
            "Cohort region", "fixture", PcbPlacementConstraints.Anchor.NONE, 20));
        board.setPlacementConstraints(new PcbPlacementConstraints(demands,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            "COHORT_P07", 1, 14, true));

        PcbFootprint first = PcbRoutedSlideCompactionContractTest
            .literalLedFootprintForCohort("COHORT_A", 120, 100);
        PcbFootprint second = PcbRoutedSlideCompactionContractTest
            .literalLedFootprintForCohort("COHORT_B", 300, 220);
        Rectangle outline = new Rectangle(40, 40, 420, 300);
        PcbBoardLayout layout = new PcbBoardLayout(600, 500, outline,
            new Rectangle(480, 40, 100, 300),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        addFootprint(layout, first);
        addFootprint(layout, second);
        Vector<String> members = new Vector<String>();
        members.add("COHORT_A");
        members.add("COHORT_B");
        layout.addRegion(new PcbLayoutRegion("cohort-region", "Cohort region", members));
        layout.setGenerationStatistics(6, 2, 7, 19L,
            PLACEMENT_SEED, ROUTING_SEED);
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(first);
        footprints.add(second);
        Vector<PcbLayoutRegion> regions = new Vector<PcbLayoutRegion>();
        regions.add(new PcbLayoutRegion("cohort-region", "Cohort region", members));
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(outline,
            footprints, regions, 123456L, 789012L, 2, 37);
        return new Fixture(board, layout, plan, 2);
    }

    private static void addFootprint(PcbBoardLayout layout, PcbFootprint footprint) {
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static boolean sameRect(Rectangle first, Rectangle second) {
        return first != null && second != null && first.x == second.x &&
            first.y == second.y && first.width == second.width &&
            first.height == second.height;
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final PcbPlacementPlanner.Plan plan;
        final int attempt;

        Fixture(TroubleshootBoard board, PcbBoardLayout layout,
                PcbPlacementPlanner.Plan plan, int attempt) {
            this.board = board;
            this.layout = layout;
            this.plan = plan;
            this.attempt = attempt;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void fixture(String name) {
        if (name == null || name.length() == 0)
            throw new AssertionError("Named fixture required");
        fixtures++;
    }
}
