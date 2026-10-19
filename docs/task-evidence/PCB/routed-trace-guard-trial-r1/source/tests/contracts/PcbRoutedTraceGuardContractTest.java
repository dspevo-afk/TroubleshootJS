package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Vector;

/** Finite contract for the private RB56 density15 routed-vertex guard trial. */
public final class PcbRoutedTraceGuardContractTest {
    private static int assertions;

    public static void main(String[] args) {
        checkInterpretationAndReplayIdentity();
        checkIndependentGuardArithmetic();
        checkActualRoutedCompaction();
        System.out.println("PASS: PCB routed trace guard contracts assertions=" + assertions +
            " fixtures=3 evaluator=compactRouted");
    }

    private static void checkInterpretationAndReplayIdentity() {
        Rb56Plan explicit15 = Rb56Plan.resolve(Long.MIN_VALUE,
            Rb56Placement.CURRENT_VERSION);
        Rb56Plan private20 = Rb56Plan.resolve(Long.MIN_VALUE,
            Rb56Placement.TRACE_GUARD_VERSION20);
        check(Rb56Placement.supportsVersion(15) && Rb56Placement.supportsVersion(20),
            "explicit15 and private20 are supported interpretation identities");
        check(!Rb56Placement.supportsVersion(16) && !Rb56Placement.supportsVersion(17) &&
                !Rb56Placement.supportsVersion(18) && !Rb56Placement.supportsVersion(19),
            "reserved placement interpretations16..19 remain rejected");
        check(explicit15.canonical().replace("placement-interpretation=15;",
                "placement-interpretation=20;").equals(private20.canonical()),
            "explicit15/private20 retain the exact seeded inventory, poses, and source choices");

        PcbPlacementConstraints saved15 = Rb56Placement.create(explicit15,
            Rb56Placement.CURRENT_VERSION);
        PcbPlacementConstraints trial20 = Rb56Placement.create(private20,
            Rb56Placement.TRACE_GUARD_VERSION20);
        check(saved15.placementDensityVersion == 14 &&
                trial20.placementDensityVersion == 15,
            "explicit15 maps to density14 and private20 maps to density15");
        check(Rb56Placement.densityVersion(Rb56Placement.CANVAS_OWNER_VERSION14) == 13 &&
                Rb56Placement.densityVersion(Rb56Placement.CURRENT_VERSION) == 14 &&
                Rb56Placement.densityVersion(Rb56Placement.TRACE_GUARD_VERSION20) == 15,
            "density13/14 ownership and the new density15 mapping are explicit");
        check(saved15.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS &&
                trial20.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS &&
                saved15.routingLayer == PcbCopperLayer.BOTTOM &&
                trial20.routingLayer == PcbCopperLayer.BOTTOM &&
                saved15.requiresTwoLayerRouting && trial20.requiresTwoLayerRouting &&
                saved15.getPhysicalPolicyIdentity().equals(trial20.getPhysicalPolicyIdentity()),
            "private20 preserves the explicit15 routing profile and physical policy");

        GenerationRequest request20 = privateRequest(
            Rb56Placement.TRACE_GUARD_VERSION20, Long.MIN_VALUE);
        String expected = "tsj-private-session/20/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-20-trace-guard/RB56_CONTROL/-9223372036854775808";
        PlayerSessionReplay replay20 = PlayerSessionReplay.forQualification(request20);
        PlayerSessionReplay parsed20 = PlayerSessionReplay.parse(expected);
        check(expected.equals(replay20.canonical()) && replay20.matches(request20) &&
                parsed20.matches(request20) &&
                parsed20.rb56PlacementInterpretationVersion() ==
                    Rb56Placement.TRACE_GUARD_VERSION20,
            "private20 replay identity round-trips the exact signed-long seed");

        GenerationRequest request15 = privateRequest(Rb56Placement.CURRENT_VERSION,
            Long.MIN_VALUE);
        String expected15 = "tsj-private-session/15/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH +
            "/rb56-placement-15/RB56_CONTROL/-9223372036854775808";
        PlayerSessionReplay replay15 = PlayerSessionReplay.forQualification(request15);
        check(expected15.equals(replay15.canonical()) && replay15.matches(request15) &&
                !replay15.matches(request20) && !replay20.matches(request15),
            "explicit15 private replay remains byte-exact and distinct from private20");
        reject(new Attempt() { public void run() {
            PlayerSessionReplay.parse("tsj-private-session/20/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-15/RB56_CONTROL/0");
        } }, "private20 rejects the explicit15 token");
        reject(new Attempt() { public void run() {
            PlayerSessionReplay.parse("tsj-private-session/20/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH +
                "/rb56-placement-20-trace-guard/RB56_CONTROL/0/extra");
        } }, "private20 rejects a noncanonical extra replay field");
        reject(new Attempt() { public void run() {
            privateRequest(16, 0L);
        } }, "generation requests continue to reject reserved placement16");
    }

    private static GenerationRequest privateRequest(int placementVersion, long seed) {
        return GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, seed)
            .withRb56PlacementInterpretationVersion(placementVersion);
    }

    private static void checkIndependentGuardArithmetic() {
        int[] widths = { 9, 11, 13, 63 };
        int[] expectedHalfGuards = { 8, 9, 10, 35 };
        for (int index = 0; index < widths.length; index++) {
            int width = widths[index];
            int literalSelfClearance = width + 6;
            int guard = (literalSelfClearance + 1) / 2;
            check(PcbTraceRules.clearance(width, width) == literalSelfClearance,
                "width" + width + " retains its existing self-clearance arithmetic");
            check(guard == expectedHalfGuards[index],
                "width" + width + " has independently tabulated split reserve " +
                    expectedHalfGuards[index]);
        }
        for (int first : widths) for (int second : widths) {
            int firstGuard = (first + 7) / 2;
            int secondGuard = (second + 7) / 2;
            int physicalPairClearance = (first + second) / 2 + 6;
            check(firstGuard + secondGuard + 20 - physicalPairClearance == 21,
                "width" + first + "/" + second +
                    " retains a21-unit centerline reserve above physical clearance");
        }
        reject(new Attempt() { public void run() {
            PcbTraceRules.clearance(8, 9);
        } }, "existing trace-width owner rejects width8");
        reject(new Attempt() { public void run() {
            PcbTraceRules.clearance(10, 9);
        } }, "existing trace-width owner rejects even width10");
        reject(new Attempt() { public void run() {
            PcbTraceRules.clearance(65, 9);
        } }, "existing trace-width owner rejects width65");
    }

    private static void checkActualRoutedCompaction() {
        Fixture explicit15 = fixture(14, true);
        Fixture private20 = fixture(15, true);
        Fixture nonmedium15 = fixture(15, false);
        validateInput(explicit15);
        validateInput(private20);
        validateInput(nonmedium15);

        String explicitBefore = inputFingerprint(explicit15);
        String privateBefore = inputFingerprint(private20);
        String nonmediumBefore = inputFingerprint(nonmedium15);
        PcbPlacementCompactor.Routed saved = compact(explicit15);
        PcbPlacementCompactor.Routed trial = compact(private20);
        PcbPlacementCompactor.Routed nonmedium = compact(nonmedium15);
        check(explicitBefore.equals(inputFingerprint(explicit15)) &&
                privateBefore.equals(inputFingerprint(private20)) &&
                nonmediumBefore.equals(inputFingerprint(nonmedium15)),
            "all actual-compactor runs leave their input plans and routed layouts immutable");

        assertCompactedFixture(explicit15, saved, 14, 900, 200, 250);
        assertCompactedFixture(private20, trial, 15, 880, 190, 230);
        assertCompactedFixture(nonmedium15, nonmedium, 15, 900, 200, 250);
        check(resultFingerprint(saved).equals(resultFingerprint(nonmedium)),
            "density15 under nonmedium policy retains the full historical vertex guard");

        Fixture replayFixture = fixture(15, true);
        validateInput(replayFixture);
        PcbPlacementCompactor.Routed replay = compact(replayFixture);
        check(resultFingerprint(trial).equals(resultFingerprint(replay)),
            "private20 actual compaction replays exact geometry and plan fields");
        check(privateBefore.equals(inputFingerprint(private20)),
            "deterministic replay still leaves the original private20 input unchanged");
    }

    private static void assertCompactedFixture(Fixture fixture,
            PcbPlacementCompactor.Routed result, int density, int expectedWidth,
            int firstRailX, int secondRailX) {
        check(fixture.constraints.placementDensityVersion == density &&
                result.plan.outline.x == 20 && result.plan.outline.width == expectedWidth &&
                result.layout.getBoardOutline().width == expectedWidth,
            "density" + density + " literal x-outline result is " + expectedWidth);
        check(result.layout.getTraces().size() == 2 && result.layout.getHoles().isEmpty(),
            "routed fixture retains exactly two traces and its zero-hole inventory");
        assertTrace(fixture, result, "GUARD_WIDTH9", "NET9", "A.1", "B.1", 9,
            new int[] { 100, firstRailX, firstRailX, 100 });
        assertTrace(fixture, result, "GUARD_WIDTH11", "NET11", "C.1", "D.1", 11,
            new int[] { 100, secondRailX, secondRailX, 100 });
        for (String id : new String[] { "A", "B", "C", "D" })
            check(result.layout.getComponent(id) != null,
                "full package identity survives compaction: " + id);
        for (String id : fixture.board.getPadIds())
            check(result.layout.getPad(id) != null,
                "declared escape pad survives compaction: " + id);
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            result.plan.outline, result.plan.footprints);
        result.layout.validateRoutingGeometry(fixture.board);
        result.layout.validateRouteQuality();
        check(true,
            "actual result passes package/access, copper-clearance, connectivity and route-quality validators");
    }

    private static void assertTrace(Fixture fixture, PcbPlacementCompactor.Routed result,
            String sourceId, String netId, String startPad, String endPad, int width,
            int[] expectedX) {
        PcbTraceGeometry input = trace(fixture.routed, sourceId);
        PcbTraceGeometry output = trace(result.layout, sourceId);
        check(output != null && sourceId.equals(output.getSourceId()) &&
                netId.equals(output.getNetId()) && startPad.equals(output.getStartPadId()) &&
                endPad.equals(output.getEndPadId()),
            sourceId + " retains source, net and declared endpoint identities");
        check(output.getWidth() == width && output.getLayer() == PcbCopperLayer.TOP &&
                output.getExposure() == PcbCopperAccess.Exposure.EXPOSED &&
                output.getWidth() == input.getWidth() && output.getLayer() == input.getLayer() &&
                output.getExposure() == input.getExposure(),
            sourceId + " retains its literal width, layer and exposure");
        check(Arrays.equals(output.getXPoints(), expectedX),
            sourceId + " retains literal straight/piecewise x-vertex contraction");
    }

    private static Fixture fixture(int densityVersion, boolean mediumPolicy) {
        TroubleshootBoard board = new TroubleshootBoard("ROUTED_TRACE_GUARD_TRIAL");
        board.addNet(new BoardNet("NET9", BoardNet.RoutingRole.SIGNAL));
        board.addNet(new BoardNet("NET11", BoardNet.RoutingRole.SUPPLY));
        PhysicalPackage physical = escapePointPackage();
        Vector<PcbFootprint> footprints = new Vector<PcbFootprint>();
        footprints.add(addPoint(board, "A", "NET9", 100, 200,
            PcbRotation.DEG_180, physical));
        footprints.add(addPoint(board, "B", "NET9", 100, 700,
            PcbRotation.DEG_180, physical));
        footprints.add(addPoint(board, "C", "NET11", 100, 850,
            PcbRotation.DEG_180, physical));
        footprints.add(addPoint(board, "D", "NET11", 100, 1350,
            PcbRotation.DEG_180, physical));
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        for (String id : new String[] { "A", "B", "C", "D" })
            parts.add(new PcbPlacementConstraints.Part(id, "trace-guard", "Trace guard",
                "BOARD", PcbPlacementConstraints.Anchor.NONE, 20));
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        String policyId = mediumPolicy ? MediumBoardPhysicalPolicy.ID :
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID;
        int policyVersion = mediumPolicy ? MediumBoardPhysicalPolicy.VERSION :
            PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION;
        PcbPlacementConstraints constraints = new PcbPlacementConstraints(parts, barriers,
            PcbCopperLayer.TOP, policyId, policyVersion, densityVersion, false,
            PcbTraceWidthProfile.ROLE_WIDTHS);
        board.setPlacementConstraints(constraints);
        board.validate();
        PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner.Plan(
            new Rectangle(20, 20, 1200, 1400), footprints,
            new Vector<PcbLayoutRegion>(), 0, 0, 41, 0);
        PcbBoardLayout routed = plan.materialize();
        routed.addTrace(new PcbTraceGeometry("GUARD_WIDTH9", "NET9", "A.1", "B.1",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] { 100, 500, 500, 100 }, new int[] { 200, 200, 700, 700 }, 9));
        routed.addTrace(new PcbTraceGeometry("GUARD_WIDTH11", "NET11", "C.1", "D.1",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] { 100, 550, 550, 100 }, new int[] { 850, 850, 1350, 1350 }, 11));
        return new Fixture(board, constraints, plan, routed);
    }

    private static PcbFootprint addPoint(TroubleshootBoard board, String id, String net,
            int x, int y, PcbRotation rotation, PhysicalPackage physical) {
        BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
        board.addComponent(component);
        board.addPad(new BoardPad(id + ".1", id, "1", net));
        PcbPackagePose reference = new PcbPackagePose(100, 100, rotation, PcbBoardSide.TOP);
        Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
        PcbPackagePose pose = new PcbPackagePose(100 + x - local.x,
            100 + y - local.y, rotation, PcbBoardSide.TOP);
        return PcbFootprint.fromPhysicalPackage(component, pose, physical.getGeometry());
    }

    private static PhysicalPackage escapePointPackage() {
        PhysicalPackage template = P06FactoryLinkFixtures.TEST_POINT;
        PhysicalPackageGeometry source = template.getGeometry();
        PhysicalPackageGeometry.Terminal original = source.getTerminal(0);
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal(original.getTerminalId(),
            original.getPadCenter(), original.getPadBounds(), original.getBoardPadProbeCenter(),
            original.getBoardPadProbeBounds(), original.getConnectedLead(),
            original.getLiftedLead(), -1, 0, 30, original.getAttachment()));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(source.getWidth(),
            source.getHeight(), terminals, source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), source.getGeometryContractVersion());
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0);
        rotations.add(PcbRotation.DEG_180);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP);
        return new PhysicalPackage("ROUTED_TRACE_GUARD_TEST_POINT", template.getTerminalIds(),
            new Vector<String>(), false, geometry, variants, "DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT, rotations, sides);
    }

    private static void validateInput(Fixture fixture) {
        PcbPlacementPlanner.validate(fixture.board, fixture.constraints,
            fixture.plan.outline, fixture.plan.footprints);
        fixture.routed.validateRoutingGeometry(fixture.board);
        fixture.routed.validateRouteQuality();
    }

    private static PcbPlacementCompactor.Routed compact(Fixture fixture) {
        return PcbPlacementCompactor.compactRouted(fixture.board,
            fixture.constraints, fixture.plan, fixture.routed);
    }

    private static PcbTraceGeometry trace(PcbBoardLayout layout, String sourceId) {
        for (PcbTraceGeometry trace : layout.getTraces())
            if (sourceId.equals(trace.getSourceId())) return trace;
        return null;
    }

    private static String inputFingerprint(Fixture fixture) {
        return planFingerprint(fixture.plan) + fixture.routed.geometryFingerprint();
    }

    private static String resultFingerprint(PcbPlacementCompactor.Routed result) {
        return planFingerprint(result.plan) + result.layout.geometryFingerprint() + ":" +
            result.layout.getRoutingExpansions() + ":" + result.layout.getRawRoutingSegments() +
            ":" + result.layout.getRoutingCongestionRejections();
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value = new StringBuilder();
        value.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|')
            .append(plan.candidate).append('|').append(plan.evaluations).append('|')
            .append(plan.courtyardArea).append('|').append(plan.demandArea).append('|')
            .append(plan.footprints.size()).append('|').append(plan.regions.size()).append(';');
        for (PcbFootprint footprint : plan.footprints)
            value.append(footprint.geometryFingerprint()).append(';');
        return value.toString();
    }

    private static void reject(Attempt attempt, String message) {
        boolean rejected = false;
        try { attempt.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Attempt { void run(); }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbPlacementConstraints constraints;
        final PcbPlacementPlanner.Plan plan;
        final PcbBoardLayout routed;
        Fixture(TroubleshootBoard board, PcbPlacementConstraints constraints,
                PcbPlacementPlanner.Plan plan, PcbBoardLayout routed) {
            this.board = board; this.constraints = constraints;
            this.plan = plan; this.routed = routed;
        }
    }

    private PcbRoutedTraceGuardContractTest() { }
}
