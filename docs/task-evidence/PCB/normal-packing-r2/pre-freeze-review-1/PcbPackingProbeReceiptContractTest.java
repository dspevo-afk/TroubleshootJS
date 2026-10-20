package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused contract for the bounded post-selection normal packing receipt. */
public final class PcbPackingProbeReceiptContractTest {
    private static int assertions;
    private static int fixtures;
    private static final String BASELINE =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String CHANGED =
        "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    public static void main(String[] args) {
        verifyZeroAttemptReceipts();
        verifyAttemptedReceiptsAndBounds();
        verifyInvalidShapes();
        verifyGeometryBinding();
        verifyStatisticsCompatibility();
        System.out.println("PASS: PCB packing probe receipt contracts assertions=" + assertions +
            " fixtures=" + fixtures);
    }

    private static void verifyZeroAttemptReceipts() {
        fixtures++;
        PcbPackingProbeReceipt noChange = new PcbPackingProbeReceipt(0, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        PcbPackingProbeReceipt noHeadroom = new PcbPackingProbeReceipt(0, 0, 0,
            Long.MAX_VALUE, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER,
            "NO_HEADROOM", BASELINE, BASELINE);
        check(noChange.attempts == 0 && noChange.orderings == 0 &&
                noChange.expansions == 0 && noChange.baselineGeometry.equals(BASELINE) &&
                noChange.selectedGeometry.equals(BASELINE),
            "zero-attempt no-change receipt preserves its typed fields");
        check(noHeadroom.millis == Long.MAX_VALUE &&
                noHeadroom.disposition.equals("NO_HEADROOM"),
            "zero-attempt no-headroom receipt accepts the nonnegative time boundary");
        check(noChange.toCanonical().startsWith("normal-packing@2") &&
                noHeadroom.toCanonical().startsWith("normal-packing@2"),
            "probe canonical receipts carry the r2 identity prefix");
    }

    private static void verifyAttemptedReceiptsAndBounds() {
        fixtures++;
        PcbPackingProbeReceipt kept = new PcbPackingProbeReceipt(1, 1, 1, 7L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "KEPT", BASELINE, BASELINE);
        PcbPackingProbeReceipt selected = new PcbPackingProbeReceipt(1,
            SupportedEnvelope.MAX_ROUTE_ATTEMPTS, PcbPackingProbeReceipt.MAX_EXPANSIONS,
            11L, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "SELECTED",
            BASELINE, CHANGED);
        PcbPackingProbeReceipt rejected = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "ROUTE_REJECTED", BASELINE, BASELINE);
        check(kept.attempts == 1 && kept.disposition.equals("KEPT") &&
                kept.baselineGeometry.equals(kept.selectedGeometry),
            "attempted kept receipt retains the incumbent geometry");
        check(selected.orderings == SupportedEnvelope.MAX_ROUTE_ATTEMPTS &&
                selected.expansions == PcbPackingProbeReceipt.MAX_EXPANSIONS &&
                !selected.baselineGeometry.equals(selected.selectedGeometry),
            "selected receipt accepts the exact work bounds and changed geometry");
        check(rejected.routePolicy.equals(MediumBoardPhysicalPolicy.P05_ONE_FACE) &&
                rejected.disposition.equals("ROUTE_REJECTED"),
            "route rejection remains a typed non-selected disposition");
    }

    private static void verifyInvalidShapes() {
        fixtures++;
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(-1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        } }, "negative attempts accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(2, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        } }, "attempt count above one accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 1, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        } }, "zero-attempt ordering work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 1L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        } }, "zero-attempt elapsed work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, -1, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE);
        } }, "negative ordering work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, SupportedEnvelope.MAX_ROUTE_ATTEMPTS + 1,
                0, 0L, MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE);
        } }, "ordering work above aggregate bound accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, -1, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE);
        } }, "negative expansion work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, PcbPackingProbeReceipt.MAX_EXPANSIONS + 1,
                0L, MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE);
        } }, "expansion work above per-request bound accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, -1L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE);
        } }, "negative probe time accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L, "P99", "KEPT", BASELINE, BASELINE);
        } }, "unknown route policy accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L, null, "KEPT", BASELINE, BASELINE);
        } }, "null route policy accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, null, BASELINE, BASELINE);
        } }, "null disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "UNKNOWN", BASELINE, BASELINE);
        } }, "unknown disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, CHANGED);
        } }, "zero-attempt selected disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE);
        } }, "attempted no-change disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, BASELINE);
        } }, "selected receipt with unchanged geometry accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, CHANGED);
        } }, "kept receipt with changed geometry accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE.toUpperCase(), BASELINE);
        } }, "uppercase geometry hash accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE.substring(1), BASELINE);
        } }, "short geometry hash accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", null, BASELINE);
        } }, "null geometry hash accepted");
    }

    private static void verifyGeometryBinding() {
        fixtures++;
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
            new Rectangle(0, 0, 200, 200), new Rectangle(250, 20, 100, 200),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        String actual = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        final PcbPackingProbeReceipt receipt = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "SELECTED", BASELINE, actual);
        receipt.requireGeometry(layout);
        check(actual.length() == 64 && actual.equals(receipt.selectedGeometry),
            "selected geometry binds to the current empty layout fingerprint");
        final PcbBoardLayout stale = new PcbBoardLayout(401, 300,
            new Rectangle(0, 0, 200, 200), new Rectangle(250, 20, 100, 200),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        reject(new Attempt() { public void run() {
            receipt.requireGeometry(stale);
        } }, "stale layout geometry accepted by the probe receipt");
        reject(new Attempt() { public void run() {
            receipt.requireGeometry(null);
        } }, "null layout accepted by the probe receipt");
    }

    private static void verifyStatisticsCompatibility() {
        fixtures++;
        Vector<Integer> ranked = new Vector<Integer>();
        Vector<String> routes = new Vector<String>();
        MediumBoardPhysicalPolicy.Statistics legacy = statistics(routes, null);
        check(legacy.packingProbe == null &&
                legacy.toCanonical().indexOf("normal-packing@2") < 0,
            "legacy statistics retain a null optional probe and old canonical body");
        PcbPackingProbeReceipt probe = new PcbPackingProbeReceipt(0, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "NO_CHANGE", BASELINE, BASELINE);
        MediumBoardPhysicalPolicy.Statistics current = statistics(ranked, probe);
        check(current.packingProbe == probe &&
                current.toCanonical().indexOf("packingProbe=normal-packing@2") >= 0,
            "receipt statistics append the probe canonical only when present");
    }

    private static MediumBoardPhysicalPolicy.Statistics statistics(Vector<String> routes,
            PcbPackingProbeReceipt probe) {
        return new MediumBoardPhysicalPolicy.Statistics("SUCCESS",
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, 1, 0, 1,
            0, 0, 1, 1, 0, new Vector<Integer>(), new Vector<String>(), routes,
            0L, 0, 0, 0L, 0L, probe);
    }

    private interface Attempt { void run(); }

    private static void reject(Attempt attempt, String message) {
        assertions++;
        try { attempt.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
