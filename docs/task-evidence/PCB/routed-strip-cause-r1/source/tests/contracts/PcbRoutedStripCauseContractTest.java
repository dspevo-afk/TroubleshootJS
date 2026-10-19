package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native-only read-only observation of existing routed compactor outcomes. */
public final class PcbRoutedStripCauseContractTest {
    private static final long MAIN_ROOT = 42L;
    private static final long GROUP2_ROOT = -1022L;
    private static final String LEGACY_CASE_PREFIX = "PCB_EDGE_TRIM_CASE ";
    private static final String EXPECTED_SIGNATURE_CANONICAL =
        "24:routed-physical-layout@1;2:R1;1:2;2:R1;1:0;1:0;3:220;2:70;5:DEG_0;3:TOP;" +
        "76:AXIAL_RESISTOR|variant=SPAN_220|transform=IDENTITY|version=3|geometry=220x70;" +
        "2:R2;3:300;3:300;3:220;2:70;5:DEG_0;3:TOP;" +
        "76:AXIAL_RESISTOR|variant=SPAN_220|transform=IDENTITY|version=3|geometry=220x70;" +
        "4:pads;1:2;4:R1.1;2:31;2:11;" +
        "2:26;1:6;2:10;2:10;2:21;1:1;2:20;2:20;1:1;1:0;2:12;19:PLATED_THROUGH_HOLE;" +
        "3:TOP;7:EXPOSED;4:R2.1;3:331;3:311;3:326;3:306;2:10;2:10;3:321;3:301;" +
        "2:20;2:20;1:1;1:0;2:12;19:PLATED_THROUGH_HOLE;3:TOP;7:EXPOSED;6:traces;1:1;" +
        "9:sig-r1-r2;4:NET1;3:TOP;7:EXPOSED;1:9;4:R1.1;4:R2.1;1:4;2:31;2:11;" +
        "3:200;2:11;3:200;3:311;3:331;3:311;5:holes;1:2;10:hole-mount;10:NON_PLATED;" +
        "-1:;3:500;3:500;1:4;1:4;7:EXPOSED;3:496;3:496;1:8;1:8;10:via-signal;3:VIA;" +
        "4:NET1;3:200;3:200;1:2;1:6;7:EXPOSED;3:194;3:194;2:12;2:12;";
    private static int assertions;

    private PcbRoutedStripCauseContractTest() { }

    public static void main(String[] args) {
        runSeed(MAIN_ROOT, "PCB routed strip cause contracts ");
    }

    static void runSeed(long seed, String marker) {
        check(seed == MAIN_ROOT || seed == GROUP2_ROOT,
            "root is one of the fixed informed current15 cases: " + seed);
        observerLifecycleFixtures(seed);
        signatureFixtures();
        Baseline baseline = baseline(seed);
        Rb56Plan plan = Rb56Plan.resolve(seed, 15);
        TroubleshootBoard board = plan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        check(plan.seed == seed && plan.placementInterpretationVersion == 15,
            "explicit current15 plan before routing: " + seed);
        check(board.getPlacementConstraints().placementDensityVersion == 14,
            "current15 maps to density14 before routing: " + seed);
        String sourceIntentSha = PcbPostSelectionEdgeTrimContractTest
            .savedIntentShaForCurrent15(plan);
        String sourcePlanSha = PcbCanvasBoundaryContractTest.sourcePlanSha256(plan);
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(board.getComponentIds().size() == baseline.parts &&
                board.getPadIds().size() == baseline.pads,
            "literal component and pad inventory before routing: " + seed);
        check(sourcePlanSha.equals(baseline.sourcePlanSha),
            "sealed source-plan fingerprint before routing: " + seed);
        check(graphBefore.equals(baseline.graphSha),
            "sealed live-graph fingerprint before routing: " + seed);
        check(sourceIntentSha.equals(baseline.sourceIntentSha),
            "sealed source-intent fingerprint before routing: " + seed);
        check(Long.toString(plan.layoutSeed).equals(baseline.layoutSeed),
            "sealed exact signed-long layout seed before routing: " + seed);
        check(Long.toString(plan.routingSeed).equals(baseline.routingSeed),
            "sealed exact signed-long routing seed before routing: " + seed);

        PcbPlacementCompactor.RoutedObservation observer =
            PcbPlacementCompactor.installRoutedObserver(seed);
        PcbPostSelectionEdgeTrimContractTest.Current15Observation routed = null;
        String selectedSignature = null;
        boolean observerCleared = false;
        try {
            check(PcbPlacementCompactor.isRoutedObserverInstalled(observer),
                "scoped compactor observer is installed for the one generation: " + seed);
            routed = PcbPostSelectionEdgeTrimContractTest.routeCurrent15(seed, plan,
                board, sourceIntentSha, sourcePlanSha, graphBefore);
            check(routed != null && routed.layout != null && routed.json != null,
                "current15 adapter returns the unchanged route observation: " + seed);
            selectedSignature = PcbPlacementCompactor.routedPhysicalSignature(routed.layout);
        } finally {
            observerCleared = PcbPlacementCompactor.clearRoutedObserver(observer);
            emitObserver(observer, selectedSignature);
            check(observerCleared && !PcbPlacementCompactor.isRoutedObserverInstalled(),
                "observer is released after generation, including failure paths: " + seed);
            check(observer.getSeed().equals(Long.toString(seed)),
                "observer retains canonical signed-long seed text: " + seed);
            PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
            check(sourcePlanSha.equals(PcbCanvasBoundaryContractTest.sourcePlanSha256(plan)),
                "source plan remains unchanged after routed generation: " + seed);
            check(graphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(board)),
                "live source graph remains unchanged after routed generation: " + seed);
        }

        check(routed.json.startsWith(LEGACY_CASE_PREFIX),
            "adapter preserves the original current15 metric row: " + seed);
        System.out.println("PCB_ROUTED_STRIP_CASE " +
            routed.json.substring(LEGACY_CASE_PREFIX.length()));
        System.out.println("PASS: " + marker + "roots=1 rows=1 assertions=" +
            assertions + " diagnosticOnly=true policyAcceptance=NOT_CLAIMED");
    }

    private static void observerLifecycleFixtures(long seed) {
        check(!PcbPlacementCompactor.isRoutedObserverInstalled(),
            "observer lifecycle fixture starts uninstalled");
        PcbPlacementCompactor.RoutedObservation first =
            PcbPlacementCompactor.installRoutedObserver(seed);
        check(PcbPlacementCompactor.isRoutedObserverInstalled(first),
            "observer owner installs successfully");
        boolean nestedRejected = false;
        try {
            PcbPlacementCompactor.installRoutedObserver(seed);
        } catch (IllegalStateException expected) {
            nestedRejected = true;
        }
        check(nestedRejected && PcbPlacementCompactor.isRoutedObserverInstalled(first),
            "nested install is rejected without displacing the current owner");
        check(PcbPlacementCompactor.clearRoutedObserver(first) &&
                !PcbPlacementCompactor.isRoutedObserverInstalled(),
            "current observer owner clears successfully");

        PcbPlacementCompactor.RoutedObservation successor =
            PcbPlacementCompactor.installRoutedObserver(seed);
        check(PcbPlacementCompactor.isRoutedObserverInstalled(successor),
            "successor observer owner installs successfully");
        check(!PcbPlacementCompactor.clearRoutedObserver(first) &&
                PcbPlacementCompactor.isRoutedObserverInstalled(successor),
            "stale owner cannot clear or displace its successor");
        RuntimeException sentinel = new RuntimeException("observer lifecycle sentinel");
        boolean sentinelPropagated = false;
        try {
            throw sentinel;
        } catch (RuntimeException observed) {
            sentinelPropagated = observed == sentinel;
        } finally {
            check(PcbPlacementCompactor.clearRoutedObserver(successor),
                "owner clears from finally after a thrown sentinel");
        }
        check(sentinelPropagated && !PcbPlacementCompactor.isRoutedObserverInstalled(),
            "sentinel path leaves no routed observer installed");
    }

    private static void signatureFixtures() {
        PcbBoardLayout baseline = signatureFixture(0, 0, 0);
        check(EXPECTED_SIGNATURE_CANONICAL.equals(
                PcbPlacementCompactor.routedPhysicalCanonicalSignature(baseline)),
            "physical signature matches independent literal canonical fixture");
        String expectedSignature = PlayerSessionFingerprint.of(EXPECTED_SIGNATURE_CANONICAL);
        check(expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(baseline)),
            "physical signature digest derives from literal canonical fixture");
        check(expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(0, 53, -27))),
            "uniform translation preserves component, pad, copper and hole signature");
        check(!expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(1, 0, 0))),
            "nonuniform component-origin pose changes physical signature");
        check(!expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(2, 0, 0))),
            "nonuniform pad position changes physical signature");
        check(!expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(3, 0, 0))),
            "full trace vertex sequence, including collinear vertices, is retained");
        check(!expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(4, 0, 0))),
            "via position and bounds change physical signature");
        check(!expectedSignature.equals(PcbPlacementCompactor.routedPhysicalSignature(
                signatureFixture(5, 0, 0))),
            "non-plated hole metadata and bounds change physical signature");
    }

    private static PcbBoardLayout signatureFixture(int change, int dx, int dy) {
        PcbPackagePose firstPose = new PcbPackagePose(100 + dx, 200 + dy,
            PcbRotation.DEG_0, PcbBoardSide.TOP);
        PcbPackagePose secondPose = new PcbPackagePose(
            400 + dx + (change == 1 ? 5 : 0), 500 + dy,
            PcbRotation.DEG_0, PcbBoardSide.TOP);
        PcbComponentPlacement first = PcbComponentPlacement.fromPhysicalGeometry("R1",
            firstPose, PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR.getGeometry());
        PcbComponentPlacement second = PcbComponentPlacement.fromPhysicalGeometry("R2",
            secondPose, PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_RESISTOR.getGeometry());
        int boardWidth = dx == 0 && dy == 0 ? 2200 : 2400;
        int boardHeight = dx == 0 && dy == 0 ? 1600 : 1800;
        PcbBoardLayout layout = new PcbBoardLayout(boardWidth, boardHeight,
            new Rectangle(20 + dx, 30 + dy, 1400, 1000),
            new Rectangle(1600 + dx, 100 + dy, 300, 200));
        layout.addComponent(first);
        layout.addComponent(second);
        int firstX = 131 + dx;
        int firstY = 211 + dy;
        int secondX = 431 + dx + (change == 2 ? 5 : 0);
        int secondY = 511 + dy;
        layout.addPad(signaturePad("R1.1", firstX, firstY));
        layout.addPad(signaturePad("R2.1", secondX, secondY));
        if (change == 3) {
            layout.addTrace(new PcbTraceGeometry("sig-r1-r2", "NET1", "R1.1", "R2.1",
                PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { firstX, 200 + dx, 300 + dx, 300 + dx, 431 + dx },
                new int[] { firstY, 211 + dy, 211 + dy, 511 + dy, 511 + dy }, 9));
        } else {
            layout.addTrace(new PcbTraceGeometry("sig-r1-r2", "NET1", "R1.1", "R2.1",
                PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { firstX, 300 + dx, 300 + dx, 431 + dx },
                new int[] { firstY, 211 + dy, 511 + dy, 511 + dy }, 9));
        }
        layout.addHole(new PcbBoardHole("via-signal", PcbBoardHole.Kind.VIA, "NET1",
            300 + dx + (change == 4 ? 5 : 0), 400 + dy, 2, 6,
            PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM, PcbCopperAccess.Exposure.EXPOSED));
        int holeRadius = change == 5 ? 5 : 4;
        layout.addHole(new PcbBoardHole("hole-mount", PcbBoardHole.Kind.NON_PLATED,
            null, 600 + dx, 700 + dy, holeRadius, holeRadius, null, null,
            PcbCopperAccess.Exposure.EXPOSED));
        layout.addSilkscreenLabel(new PcbSilkscreenLabel("label-r1", "R1",
            new Rectangle(140 + dx, 240 + dy, 32, 10), 10, false, null));
        return layout;
    }

    private static PcbPadPlacement signaturePad(String id, int x, int y) {
        return new PcbPadPlacement(id, x, y, 1, 0, 12,
            new Rectangle(x - 5, y - 5, 10, 10),
            new Rectangle(x - 10, y - 10, 20, 20),
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP,
            PcbCopperAccess.Exposure.EXPOSED);
    }

    private static void emitObserver(PcbPlacementCompactor.RoutedObservation observer,
            String selectedSignature) {
        for (String event : observer.getEventJsonLines())
            System.out.println("PCB_ROUTED_STRIP_EVENT " + event);
        Vector<Integer> matches = observer.matchingCompletedCallOrdinals(selectedSignature);
        Vector<Integer> incomplete = observer.incompleteCallOrdinals();
        String status;
        if (observer.isTruncated() || !incomplete.isEmpty() ||
                selectedSignature == null || matches.size() != 1) {
            if (!observer.isTruncated() && incomplete.isEmpty() &&
                    selectedSignature != null && matches.isEmpty()) status = "NO_MATCH";
            else if (!observer.isTruncated() && incomplete.isEmpty() &&
                    selectedSignature != null && matches.size() > 1) status = "AMBIGUOUS";
            else status = "INCONCLUSIVE";
        } else {
            status = "MATCHED";
        }
        String caseStatus = "INCONCLUSIVE";
        StringBuilder json = new StringBuilder(320);
        json.append("{\"schema\":\"PCB_ROUTED_STRIP_BINDING@1\",\"seed\":");
        appendJsonString(json, observer.getSeed());
        json.append(",\"selectedSignature\":");
        appendJsonStringOrNull(json, selectedSignature);
        json.append(",\"matchingCallOrdinals\":");
        appendIntegers(json, matches);
        json.append(",\"incompleteCallOrdinals\":");
        appendIntegers(json, incomplete);
        json.append(",\"truncated\":").append(observer.isTruncated());
        json.append(",\"status\":\"").append(status).append('"');
        json.append(",\"caseStatus\":\"").append(caseStatus).append("\"}");
        System.out.println("PCB_ROUTED_STRIP_BINDING " + json.toString());
    }

    private static void appendIntegers(StringBuilder json, Vector<Integer> values) {
        json.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) json.append(',');
            json.append(values.get(index).intValue());
        }
        json.append(']');
    }

    private static void appendJsonStringOrNull(StringBuilder json, String value) {
        if (value == null) json.append("null");
        else appendJsonString(json, value);
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '"' || ch == '\\') json.append('\\').append(ch);
            else if (ch == '\n') json.append("\\n");
            else if (ch == '\r') json.append("\\r");
            else if (ch == '\t') json.append("\\t");
            else if (ch < 0x20) {
                json.append("\\u00").append(Character.forDigit((ch >> 4) & 0xf, 16))
                    .append(Character.forDigit(ch & 0xf, 16));
            } else json.append(ch);
        }
        json.append('"');
    }

    private static Baseline baseline(long seed) {
        if (seed == MAIN_ROOT) {
            return new Baseline(54, 131, "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59",
                "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a",
                "5be52fd06e5788bbd64793059c26b198d57a4feda89c5c1e8e29a63c110622f7",
                "5526715536693200536", "4493419427463069983");
        }
        if (seed == GROUP2_ROOT) {
            return new Baseline(60, 143, "bb68d08e1d9074488e051aad5e9a3a41dd46e844713b52c4bbf4d593a8feba77",
                "17552a33d0687f7a126b2be05dce5ec7083d3b48ea0ba4389f226f3968ccc7e4",
                "8869adea6a77467d2cbbbe7d5efafe3b86aa71b687d6bd13d0ca0fdfac86002c",
                "-5634176660316136309", "-3812824227946713686");
        }
        throw new AssertionError("Unexpected fixed seed: " + seed);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Baseline {
        final int parts;
        final int pads;
        final String sourcePlanSha;
        final String graphSha;
        final String sourceIntentSha;
        final String layoutSeed;
        final String routingSeed;
        Baseline(int parts, int pads, String sourcePlanSha, String graphSha,
                String sourceIntentSha, String layoutSeed, String routingSeed) {
            this.parts = parts;
            this.pads = pads;
            this.sourcePlanSha = sourcePlanSha;
            this.graphSha = graphSha;
            this.sourceIntentSha = sourceIntentSha;
            this.layoutSeed = layoutSeed;
            this.routingSeed = routingSeed;
        }
    }
}
