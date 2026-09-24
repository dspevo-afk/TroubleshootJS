package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Versioned physical-policy checks for the future RB30 provider boundary. */
public final class Q30EnvelopeContractTest {
    private static int checks;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = GenerationRequest.leaf(
            QuickPlayFamilyRegistry.LED_INDICATOR, 0, false)
            .resolve(new GenerationRequest.PlanCache()).construct().instance;
        try {
            verifyVersionedPolicies(owner);
            verifyProviderSelection();
            verifyP09Negatives(owner);
            verifyRequestOwnership(owner);
        } finally {
            for (CircuitElm element : owner.getSimulationElements()) element.delete();
        }
        System.out.println("PASS: Q30 envelope contracts assertions=" + checks);
    }

    private static void verifyVersionedPolicies(GeneratedBoardInstance owner) {
        SupportedEnvelope v1 = SupportedEnvelope.current();
        SupportedEnvelope v2 = SupportedEnvelope.resolve(SupportedEnvelope.ID,
            SupportedEnvelope.Q30_VERSION);
        require(v1 == SupportedEnvelope.resolve(SupportedEnvelope.ID,
            SupportedEnvelope.VERSION), "P09 registry identity changed");
        require(v1.getVersion() == 1 && v1.isQualified(), "P09 qualification changed");
        require(v1.getMaxParts() == 16 && v1.getMaxArea() == 2250000L &&
            v1.getMaxCopperLength() == 16000L, "P09 numeric limits changed");
        require(v1.canonical().contains(";parts=16;") &&
            !v1.canonical().contains(";parts=1..16;"), "P09 population canonical changed");
        require(v2 != v1 && v2.getVersion() == SupportedEnvelope.Q30_VERSION &&
            !v2.isQualified(), "Q30 candidate was silently qualified");
        require(v2.getMinParts() == 20 && v2.getMaxParts() == 40,
            "Q30 candidate part range changed");
        require(v2.getMaxArea() == 5062500L && v2.getMaxCopperLength() == 20380L,
            "Q30 candidate scale evidence changed");
        require(v2.canonical().contains(";parts=20..40;") &&
            v2.canonical().contains(";qualification=UNQUALIFIED-Q30-CORPUS"),
            "Q30 candidate qualification is not explicit");
        require(!v1.allowsVias() && !v1.allowsLinks() && !v2.allowsVias() &&
            !v2.allowsLinks(), "single-face link/hole policy broadened");
        require(owner.getCircuitFamilyId().equals(QuickPlayFamilyRegistry.LED_INDICATOR),
            "fixture family changed");
        checks += 11;
    }

    private static void verifyProviderSelection() {
        SupportedEnvelope v2 = SupportedEnvelope.forFamily(QuickPlayFamilyRegistry.RB30_CONTROL);
        require(v2 == SupportedEnvelope.resolve(SupportedEnvelope.ID,
            SupportedEnvelope.Q30_VERSION), "RB30 provider did not select v2");
        require(!QuickPlayFamilyRegistry.isNormalPlayerEligible(
            QuickPlayFamilyRegistry.RB30_CONTROL), "RB30 became normal-player eligible");
        require(!QuickPlayFamilyRegistry.getNormalPlayerFamilyIds().contains(
            QuickPlayFamilyRegistry.RB30_CONTROL), "RB30 entered the normal family menu");
        checks += 3;
    }

    private static void verifyP09Negatives(final GeneratedBoardInstance owner) {
        final SupportedEnvelope v1 = SupportedEnvelope.current();
        final TroubleshootBoard board = owner.getBoard();
        final PcbBoardLayout layout = owner.getPcbLayout();

        PcbComponentPlacement originalComponent = layout.getComponents().firstElement();
        PhysicalPackage sourcePackage = originalComponent.getPhysicalPackage();
        PhysicalPackage forgedPackage = new PhysicalPackage(sourcePackage.getId(),
            sourcePackage.getTerminalIds(), new Vector<String>(), sourcePackage.isConnector(),
            sourcePackage.getGeometry(), sourcePackage.getGeometryVariants(),
            sourcePackage.getDefaultLooseGeometryVariantKey(),
            sourcePackage.getGeometryVariantSelection(), sourcePackage.getAllowedRotations(),
            sourcePackage.getAllowedMountingSides());
        final PcbComponentPlacement forgedPlacement = PcbComponentPlacement.fromPhysicalGeometry(
            originalComponent.getComponentId(), originalComponent.getPose(), forgedPackage,
            originalComponent.getPhysicalGeometry());
        rejects(SupportedEnvelope.Reason.PACKAGE_MIX, new Runnable() {
            public void run() {
                v1.requireBounds(board, P09EnvelopeChecks.copy(layout, forgedPlacement, null, false));
            }
        });

        final PcbCopperLayer declared = board.getPlacementConstraints().routingLayer;
        final PcbCopperLayer other = declared == PcbCopperLayer.TOP ?
            PcbCopperLayer.BOTTOM : PcbCopperLayer.TOP;
        final PcbTraceGeometry trace = layout.getTraces().firstElement();
        final PcbBoardLayout wrongLayer = P09EnvelopeChecks.copy(layout, null, null, false);
        Vector<PcbTraceGeometry> layerTraces = new Vector<PcbTraceGeometry>(wrongLayer.getTraces());
        layerTraces.set(0, new PcbTraceGeometry(trace.getSourceId(), trace.getNetId(),
            trace.getStartPadId(), trace.getEndPadId(), other, trace.getExposure(),
            trace.getXPoints(), trace.getYPoints()));
        wrongLayer.replaceTraces(layerTraces);
        rejects(SupportedEnvelope.Reason.LAYER_POLICY, new Runnable() {
            public void run() { v1.requireBounds(board, wrongLayer); }
        });

        final PcbBoardLayout withVia = P09EnvelopeChecks.copy(layout, null, null, false);
        withVia.addHole(PcbTwoLayerRules.via("q30-unqualified-via",
            board.getNetIds().firstElement(), 60, 60));
        rejects(SupportedEnvelope.Reason.HOLE_POLICY, new Runnable() {
            public void run() { v1.requireBounds(board, withVia); }
        });

        final PcbPadPlacement pad = layout.getPads().firstElement();
        final PcbBoardLayout invalidCount = P09EnvelopeChecks.copy(layout, null, null, false);
        invalidCount.addPad(new PcbPadPlacement("q30-forged-pad", pad.getX(), pad.getY(),
            pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength(), pad.getPadBounds(),
            pad.getProbeBounds(), pad.getAttachment(), pad.getMountingSide(), pad.getExposure()));
        rejects(SupportedEnvelope.Reason.POPULATION, new Runnable() {
            public void run() { v1.requireBounds(board, invalidCount); }
        });

        rejects(SupportedEnvelope.Reason.POPULATION, new Runnable() {
            public void run() { SupportedEnvelope.resolve(SupportedEnvelope.ID,
                SupportedEnvelope.Q30_VERSION).requireBounds(board, layout); }
        });
        checks += 5;
    }

    private static void verifyRequestOwnership(final GeneratedBoardInstance owner) {
        final SupportedEnvelope v1 = SupportedEnvelope.current();
        final SupportedEnvelope v2 = SupportedEnvelope.resolve(SupportedEnvelope.ID,
            SupportedEnvelope.Q30_VERSION);
        final GenerationRequest p09 = GenerationRequest.leaf(
            QuickPlayFamilyRegistry.LED_INDICATOR, 0, false);
        final GenerationRequest rb30 = GenerationRequest.leaf(
            QuickPlayFamilyRegistry.RB30_CONTROL, 0, false);
        v1.requireRequest(p09, owner);
        checks++;
        rejects(SupportedEnvelope.Reason.REQUEST_POLICY, new Runnable() {
            public void run() { v2.requireRequest(p09, owner); }
        });
        rejects(SupportedEnvelope.Reason.REQUEST_POLICY, new Runnable() {
            public void run() { v1.requireRequest(rb30, owner); }
        });
        rejects(SupportedEnvelope.Reason.REQUEST_POLICY, new Runnable() {
            public void run() { v2.requireRequest(rb30, owner); }
        });
        checks += 3;
    }

    private static void rejects(SupportedEnvelope.Reason reason, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Accepted negative " + reason);
        } catch (SupportedEnvelope.Rejected expected) {
            require(expected.reason == reason, "wrong rejection reason: " + expected.reason);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
