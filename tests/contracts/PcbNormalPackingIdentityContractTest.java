package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused identity contract for the explicit normal connected-pocket policy. */
public final class PcbNormalPackingIdentityContractTest {
    private static int assertions;

    public static void main(String[] args) {
        check(PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION == 4,
            "normal paired packing explicitly identifies interpretation 4");
        verifyImmutableCopyAndPreservation();
        verifyProceduralDeclarationAndFaceConversion();
        verifyNormalAndPrivateProviders();
        verifyPublicReplayEpochAndSignedSeeds();
        check(SeededPcbLayoutGenerator.CURRENT_VERSION == 19,
            "current layout algorithm identity is not 19");
        System.out.println("PASS: PCB normal packing identity contracts assertions=" + assertions +
            " packing=" + PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION +
            " replay=" + PlayerLaunchRequest.EPOCH);
    }

    private static void verifyImmutableCopyAndPreservation() {
        final Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        PcbPlacementConstraints.Part resistor = new PcbPlacementConstraints.Part(
            "R1", "regulation", "Regulation", "board",
            PcbPlacementConstraints.Anchor.NONE, 24);
        parts.add(resistor);
        final Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("board", "return", 80));
        PcbPlacementConstraints legacy = new PcbPlacementConstraints(parts, barriers,
            PcbCopperLayer.TOP, "TEST_POLICY", 7, 9, true,
            PcbTraceWidthProfile.ROLE_WIDTHS);
        PcbPlacementConstraints normal = legacy.withConnectedPocketPacking();
        check(legacy != normal && legacy.getPlacementPackingVersion() == 1 &&
            normal.getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION,
            "legacy constraints remain immutable while normal copy opts into packing 4");
        check(normal.routingLayer == legacy.routingLayer &&
            normal.getPhysicalPolicyId().equals(legacy.getPhysicalPolicyId()) &&
            normal.getPhysicalPolicyVersion() == legacy.getPhysicalPolicyVersion() &&
            normal.placementDensityVersion == legacy.placementDensityVersion &&
            normal.requiresTwoLayerRouting == legacy.requiresTwoLayerRouting &&
            normal.getTraceWidthProfile() == legacy.getTraceWidthProfile() &&
            normal.get("R1") == resistor && normal.getBarriers().size() == 1,
            "packing copy preserves parts, barrier, face, policy, density, routing and trace profile");
        PcbPlacementConstraints repeated = normal.withConnectedPocketPacking();
        check(repeated == normal && repeated.getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION &&
                repeated.get("R1") == resistor && repeated.getBarriers().size() == 1,
            "repeated normal opt-in is idempotent and preserves the current copy");
        reject(new Attempt() { public void run() {
            new PcbPlacementConstraints(parts, barriers, PcbCopperLayer.TOP,
                "TEST_POLICY", 7, 9, true, PcbTraceWidthProfile.ROLE_WIDTHS, 5);
        } }, "unknown packing interpretation accepted");
        reject(new Attempt() { public void run() {
            new PcbPlacementConstraints(parts, barriers, PcbCopperLayer.TOP,
                "TEST_POLICY", 7, 9, true, PcbTraceWidthProfile.ROLE_WIDTHS, 3);
        } }, "obsolete single-part packing interpretation accepted");
        reject(new Attempt() { public void run() {
            new PcbPlacementConstraints(parts, barriers, PcbCopperLayer.TOP,
                "TEST_POLICY", 7, 9, true, PcbTraceWidthProfile.ROLE_WIDTHS, 2);
        } }, "stale packing interpretation accepted");
    }

    private static void verifyProceduralDeclarationAndFaceConversion() {
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("R1", "r", "R", "board",
            PcbPlacementConstraints.Anchor.NONE, 20));
        PcbPlacementConstraints top = new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            "TEST_POLICY", 3, 8, true, PcbTraceWidthProfile.ROLE_WIDTHS)
            .withConnectedPocketPacking();
        PcbPlacementConstraints bottom = ProceduralPcbLayout.toBottomRoutingConstraints(top);
        check(bottom.routingLayer == PcbCopperLayer.BOTTOM &&
            bottom.getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION &&
            bottom.getPhysicalPolicyIdentity().equals(top.getPhysicalPolicyIdentity()) &&
            bottom.placementDensityVersion == top.placementDensityVersion &&
            bottom.requiresTwoLayerRouting == top.requiresTwoLayerRouting &&
            bottom.getTraceWidthProfile() == top.getTraceWidthProfile() &&
            bottom.get("R1") == top.get("R1"),
            "bottom conversion preserves every physical property and packing interpretation");

        TroubleshootBoard topBoard = new TroubleshootBoard("NORMAL_TOP");
        ProceduralPcbLayout.declare(topBoard);
        check(topBoard.getPlacementConstraints().routingLayer == PcbCopperLayer.BOTTOM &&
            topBoard.getPlacementConstraints().getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION,
            "shared normal declaration explicitly selects packing 4 before routing");

        TroubleshootBoard bottomBoard = new TroubleshootBoard("NORMAL_BOTTOM");
        bottomBoard.setPlacementConstraints(new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM)
            .withConnectedPocketPacking());
        ProceduralPcbLayout.declare(bottomBoard);
        check(bottomBoard.getPlacementConstraints().getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION,
            "already-bottom normal declaration retains its explicit packing 4");

        final TroubleshootBoard legacyBottomBoard = new TroubleshootBoard("LEGACY_BOTTOM");
        legacyBottomBoard.setPlacementConstraints(new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM));
        rejectExact(new Attempt() { public void run() {
            ProceduralPcbLayout.declare(legacyBottomBoard);
        } }, IllegalStateException.class,
            "already-bottom legacy packing is rejected before mutation");
    }

    private static void verifyNormalAndPrivateProviders() {
        PcbPlacementConstraints normal = Rb30Plan.reference(13L).board()
            .getPlacementConstraints();
        check(normal.routingLayer == PcbCopperLayer.BOTTOM &&
            normal.getPlacementPackingVersion() ==
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION &&
            MediumBoardPhysicalPolicy.selected(normal),
            "RB30 normal provider explicitly opts into connected-pocket packing");
        PcbPlacementConstraints privateLegacy = Rb56Plan.resolve(0L,
            GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT).board()
            .getPlacementConstraints();
        check(privateLegacy.getPlacementPackingVersion() == 1,
            "private RB56 retains legacy packing interpretation 1");
    }

    private static void verifyPublicReplayEpochAndSignedSeeds() {
        for (final String seed : new String[] {
                "-9223372036854775808", "-9007199254740993", "0",
                "9007199254740993", "9223372036854775807" }) {
            PlayerLaunchRequest request = PlayerLaunchRequest.parse(
                PlayerLaunchRequest.EPOCH + "/EASY/LED_INDICATOR/" + seed);
            check(Long.toString(request.seed).equals(seed) &&
                request.replay().equals(PlayerLaunchRequest.EPOCH +
                    "/EASY/LED_INDICATOR/" + seed),
                "current public epoch preserves exact signed seed " + seed);
        }
        for (final String stale : new String[] {
                "tsj-alpha/5/EASY/LED_INDICATOR/0",
                "tsj-alpha/7/EASY/LED_INDICATOR/0",
                "tsj-alpha/6/EASY/LED_INDICATOR/+1",
                "tsj-alpha/6/EASY/LED_INDICATOR/01",
                "tsj-alpha/6/EASY/LED_INDICATOR/9223372036854775808" }) {
            reject(new Attempt() { public void run() {
                PlayerLaunchRequest.parse(stale);
            } }, "stale, unknown or malformed public replay accepted: " + stale);
        }
    }

    private interface Attempt { void run(); }
    private static void reject(Attempt attempt, String message) {
        assertions++;
        try { attempt.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private static void rejectExact(Attempt attempt, Class<?> expected, String message) {
        assertions++;
        try { attempt.run(); }
        catch (Throwable failure) {
            if (failure.getClass() == expected) return;
            throw new AssertionError(message + ": " + failure.getClass().getName());
        }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
