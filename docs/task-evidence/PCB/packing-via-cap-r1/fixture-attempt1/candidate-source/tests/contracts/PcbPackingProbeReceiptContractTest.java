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
    private static final String REASON_160 =
        "1234567890" + "1234567890" + "1234567890" + "1234567890" +
        "1234567890" + "1234567890" + "1234567890" + "1234567890" +
        "1234567890" + "1234567890" + "1234567890" + "1234567890" +
        "1234567890" + "1234567890" + "1234567890" + "1234567890";
    private static final String REASON_161 = REASON_160 + "x";
    private static final PcbPackingMetrics BASELINE_METRICS =
        new PcbPackingMetrics(1000L, 200L, 3, 500);
    private static final PcbPackingMetrics IMPROVED_METRICS =
        new PcbPackingMetrics(900L, 200L, 3, 500);
    private static final String CANDIDATE_UNAVAILABLE_REASON =
        "CANDIDATE_METRIC_UNAVAILABLE";

    public static void main(String[] args) {
        verifyZeroAttemptReceipts();
        verifyAttemptedReceiptsAndBounds();
        verifyInvalidShapes();
        verifyGeometryBinding();
        verifyStatisticsCompatibility();
        verifyMetricEvidence();
        System.out.println("PASS: PCB packing probe receipt contracts assertions=" + assertions +
            " fixtures=" + fixtures);
    }

    private static void verifyZeroAttemptReceipts() {
        fixtures++;
        PcbPackingProbeReceipt noChange = new PcbPackingProbeReceipt(0, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        PcbPackingProbeReceipt noHeadroom = new PcbPackingProbeReceipt(0, 0, 0,
            0L, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER,
            "NO_HEADROOM", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        check(noChange.attempts == 0 && noChange.orderings == 0 &&
                noChange.expansions == 0 && noChange.baselineGeometry.equals(BASELINE) &&
                noChange.selectedGeometry.equals(BASELINE),
            "zero-attempt no-change receipt preserves its typed fields");
        check(noHeadroom.millis == 0L &&
                noHeadroom.disposition.equals("NO_HEADROOM"),
            "zero-attempt no-headroom receipt has no routing time");
        check(noChange.toCanonical().startsWith("normal-packing@3") &&
                noHeadroom.toCanonical().startsWith("normal-packing@3"),
            "probe canonical receipts carry the r2 identity prefix");
    }

    private static void verifyAttemptedReceiptsAndBounds() {
        fixtures++;
        PcbPackingProbeReceipt kept = new PcbPackingProbeReceipt(1, 1, 1, 7L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "KEPT", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        PcbPackingProbeReceipt selected = new PcbPackingProbeReceipt(1,
            SupportedEnvelope.MAX_ROUTE_ATTEMPTS, PcbPackingProbeReceipt.MAX_EXPANSIONS,
            Long.MAX_VALUE, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "SELECTED",
            BASELINE, CHANGED,
            PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                IMPROVED_METRICS));
        PcbPackingProbeReceipt rejected = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "ROUTE_REJECTED", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.baselineOnly(BASELINE_METRICS));
        check(kept.attempts == 1 && kept.disposition.equals("KEPT") &&
                kept.baselineGeometry.equals(kept.selectedGeometry),
            "attempted kept receipt retains the incumbent geometry");
        check(selected.orderings == SupportedEnvelope.MAX_ROUTE_ATTEMPTS &&
                selected.expansions == PcbPackingProbeReceipt.MAX_EXPANSIONS &&
                selected.millis == Long.MAX_VALUE &&
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
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "negative attempts accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(2, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "attempt count above one accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 1, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "zero-attempt ordering work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 1L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "zero-attempt elapsed work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, -1, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "negative ordering work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, SupportedEnvelope.MAX_ROUTE_ATTEMPTS + 1,
                0, 0L, MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "ordering work above aggregate bound accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, -1, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "negative expansion work accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, PcbPackingProbeReceipt.MAX_EXPANSIONS + 1,
                0L, MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "expansion work above per-request bound accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, -1L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "negative probe time accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L, "P99", "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "unknown route policy accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L, null, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "null route policy accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, null, BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "null disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "UNKNOWN", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "unknown disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, CHANGED,
                PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                    IMPROVED_METRICS));
        } }, "zero-attempt selected disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "attempted no-change disposition accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                    IMPROVED_METRICS));
        } }, "selected receipt with unchanged geometry accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, CHANGED,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "kept receipt with changed geometry accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE.toUpperCase(), BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "uppercase geometry hash accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE.substring(1), BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "short geometry hash accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", null, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "null geometry hash accepted");
    }

    private static void verifyGeometryBinding() {
        fixtures++;
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
            new Rectangle(0, 0, 200, 200), new Rectangle(250, 20, 100, 200),
            SeededPcbLayoutGenerator.CURRENT_VERSION);
        String actual = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        final PcbPackingProbeReceipt receipt = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "SELECTED", BASELINE, actual,
            PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                IMPROVED_METRICS));
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
        Vector<String> routes = new Vector<String>();
        MediumBoardPhysicalPolicy.Statistics legacy = statistics(routes, null);
        check(legacy.packingProbe == null &&
                legacy.toCanonical().indexOf("normal-packing@3") < 0,
            "legacy statistics retain a null optional probe and old canonical body");
        PcbPackingProbeReceipt probe = new PcbPackingProbeReceipt(0, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER, "NO_CHANGE", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        MediumBoardPhysicalPolicy.Statistics current = statistics(routes, probe);
        check(current.packingProbe == probe &&
                current.toCanonical().indexOf("packingProbe=normal-packing@3") >= 0,
            "receipt statistics append the probe canonical only when present");
        String expectedProbeCanonical =
            "normal-packing@3;attempts=0;orderings=0;expansions=0;millis=0" +
            ";route=P07_FULLER_TWO_LAYER;disposition=NO_CHANGE;baseline=" +
            BASELINE + ";selected=" + BASELINE;
        String expectedLegacyCanonical =
            "MEDIUM_BOARD@1;outcome=SUCCESS;selected=P07_FULLER_TWO_LAYER;" +
            "placements=1;placementRejects=0;placementEvaluations=0;routes=1;" +
            "routingWork=0/0;p05=0/0;p07=1/1;selectedAttempt=0;ranked=[];" +
            "placementScores=[];selectedScore=0;selectedQualityMilli=0;" +
            "routeOutcomes=[]";
        String expectedCurrentCanonical = expectedLegacyCanonical +
            ";packingProbe=" + expectedProbeCanonical;
        String currentCanonical = current.toCanonical();
        check(probe.toCanonical().equals(expectedProbeCanonical) &&
                legacy.toCanonical().equals(expectedLegacyCanonical) &&
                currentCanonical.equals(expectedCurrentCanonical),
            "literal receipt and statistics canonical bodies remain unchanged");
        check(currentCanonical.indexOf("metricEvidence") < 0 &&
                currentCanonical.indexOf("area=") < 0 &&
                currentCanonical.indexOf("copper=") < 0 &&
                currentCanonical.indexOf("vias=") < 0 &&
                currentCanonical.indexOf("protectedVoid=") < 0 &&
                currentCanonical.indexOf("unavailableReason") < 0,
            "diagnostic metric fields stay absent from canonical statistics");
    }

    private static void verifyMetricEvidence() {
        PcbPackingProbeReceipt.MetricEvidence notMeasured =
            PcbPackingProbeReceipt.MetricEvidence.notMeasured();
        check(notMeasured.state == PcbPackingProbeReceipt.MetricState.NOT_MEASURED &&
                notMeasured.baseline == null && notMeasured.candidate == null &&
                notMeasured.unavailableReason == null,
            "not-measured evidence keeps every optional field absent");

        PcbPackingProbeReceipt.MetricEvidence baselineUnavailable =
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable("GRID10_UNAVAILABLE");
        check(baselineUnavailable.state == PcbPackingProbeReceipt.MetricState.BASELINE_UNAVAILABLE &&
                baselineUnavailable.baseline == null && baselineUnavailable.candidate == null &&
                baselineUnavailable.unavailableReason.equals("GRID10_UNAVAILABLE"),
            "baseline-unavailable evidence retains its literal reason only");

        final PcbPackingMetrics observedBaseline = new PcbPackingMetrics(1400L, 260L, 5, 700);
        PcbPackingProbeReceipt.MetricEvidence baselineOnly =
            PcbPackingProbeReceipt.MetricEvidence.baselineOnly(observedBaseline);
        check(baselineOnly.state == PcbPackingProbeReceipt.MetricState.BASELINE_ONLY &&
                baselineOnly.baseline == observedBaseline &&
                baselineOnly.baseline.area == 1400L &&
                baselineOnly.baseline.copper == 260L &&
                baselineOnly.baseline.vias == 5 &&
                baselineOnly.baseline.protectedVoid == 700 &&
                baselineOnly.candidate == null && baselineOnly.unavailableReason == null,
            "baseline-only evidence retains its complete literal baseline tuple");

        PcbPackingProbeReceipt.MetricEvidence candidateUnavailable =
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                observedBaseline, "CANDIDATE_GRID10_UNAVAILABLE");
        check(candidateUnavailable.state ==
                PcbPackingProbeReceipt.MetricState.CANDIDATE_UNAVAILABLE &&
                candidateUnavailable.baseline == observedBaseline &&
                candidateUnavailable.candidate == null &&
                candidateUnavailable.unavailableReason.equals("CANDIDATE_GRID10_UNAVAILABLE"),
            "candidate-unavailable evidence retains baseline and reason without a candidate");

        final PcbPackingMetrics observedCandidate = new PcbPackingMetrics(1200L, 260L, 5, 700);
        PcbPackingProbeReceipt.MetricEvidence compared =
            PcbPackingProbeReceipt.MetricEvidence.compared(observedBaseline, observedCandidate);
        check(compared.state == PcbPackingProbeReceipt.MetricState.COMPARED &&
                compared.baseline == observedBaseline && compared.candidate == observedCandidate &&
                compared.candidate.area == 1200L && compared.candidate.copper == 260L &&
                compared.candidate.vias == 5 && compared.candidate.protectedVoid == 700 &&
                compared.unavailableReason == null,
            "compared evidence retains both complete literal metric tuples");

        PcbPackingProbeReceipt.MetricEvidence boundedReason =
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable(REASON_160);
        check(boundedReason.unavailableReason.equals(REASON_160),
            "a 160-character metric reason remains intact");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable(null);
        } }, "null unavailable reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable("");
        } }, "empty unavailable reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable(" \t ");
        } }, "blank unavailable reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable(REASON_161);
        } }, "oversized unavailable reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.baselineOnly(null);
        } }, "missing baseline metric accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                null, "candidate unavailable");
        } }, "candidate-unavailable evidence without baseline accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                observedBaseline, null);
        } }, "candidate-unavailable evidence without reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                observedBaseline, " \t ");
        } }, "candidate-unavailable evidence with blank reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                observedBaseline, REASON_161);
        } }, "candidate-unavailable evidence with oversized reason accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.compared(null, observedCandidate);
        } }, "compared evidence without baseline accepted");
        reject(new Attempt() { public void run() {
            PcbPackingProbeReceipt.MetricEvidence.compared(observedBaseline, null);
        } }, "compared evidence without candidate accepted");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                null);
        } }, "null metric evidence accepted");

        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_CHANGE", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable("baseline unavailable"));
        } }, "no-change receipt accepted baseline-unavailable evidence");
        PcbPackingProbeReceipt noHeadroomUnavailable = new PcbPackingProbeReceipt(0, 0, 0,
            0L, MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_HEADROOM", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.baselineUnavailable("baseline unavailable"));
        check(noHeadroomUnavailable.metricEvidence.state ==
                PcbPackingProbeReceipt.MetricState.BASELINE_UNAVAILABLE,
            "no-headroom receipt preserves baseline-unavailable evidence");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(0, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "NO_HEADROOM", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.baselineOnly(BASELINE_METRICS));
        } }, "no-headroom receipt accepted measured baseline evidence");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "ROUTE_REJECTED", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.notMeasured());
        } }, "route-rejected receipt accepted unmeasured evidence");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.baselineOnly(BASELINE_METRICS));
        } }, "kept receipt accepted baseline-only evidence");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, CHANGED,
                PcbPackingProbeReceipt.MetricEvidence.candidateUnavailable(
                    BASELINE_METRICS, CANDIDATE_UNAVAILABLE_REASON));
        } }, "selected receipt accepted unavailable candidate evidence");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
                PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                    IMPROVED_METRICS));
        } }, "kept receipt accepted a strictly improving measured candidate");

        PcbPackingMetrics strictCandidate = new PcbPackingMetrics(900L, 200L, 3, 500);
        PcbPackingProbeReceipt selected = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, CHANGED,
            PcbPackingProbeReceipt.MetricEvidence.compared(
                new PcbPackingMetrics(1000L, 200L, 3, 500), strictCandidate));
        check(selected.metricEvidence.state == PcbPackingProbeReceipt.MetricState.COMPARED &&
                selected.metricEvidence.candidate == strictCandidate,
            "selected receipt accepts a strict measured improvement");

        verifyMetricRegression(new PcbPackingMetrics(1000L, 200L, 3, 500),
            "selected receipt accepted an area-tie candidate");
        verifyMetricRegression(new PcbPackingMetrics(900L, 201L, 3, 500),
            "selected receipt accepted a copper-regression candidate");
        verifyMetricRegression(new PcbPackingMetrics(900L, 200L, 4, 500),
            "selected receipt accepted a via-regression candidate");
        verifyMetricRegression(new PcbPackingMetrics(900L, 200L, 3, 501),
            "selected receipt accepted a protected-void regression candidate");
    }

    private static void verifyMetricRegression(final PcbPackingMetrics candidate,
            final String message) {
        PcbPackingProbeReceipt kept = new PcbPackingProbeReceipt(1, 0, 0, 0L,
            MediumBoardPhysicalPolicy.P05_ONE_FACE, "KEPT", BASELINE, BASELINE,
            PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS, candidate));
        check(kept.metricEvidence.state == PcbPackingProbeReceipt.MetricState.COMPARED &&
                kept.metricEvidence.candidate == candidate,
            "kept receipt retains a measured candidate that fails one strict predicate");
        reject(new Attempt() { public void run() {
            new PcbPackingProbeReceipt(1, 0, 0, 0L,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, "SELECTED", BASELINE, CHANGED,
                PcbPackingProbeReceipt.MetricEvidence.compared(BASELINE_METRICS,
                    candidate));
        } }, message);
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
