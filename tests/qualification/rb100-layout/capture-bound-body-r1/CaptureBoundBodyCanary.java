package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic-only canary for capture-bound cohort/body composition. */
public final class CaptureBoundBodyCanary {
    private static final String RUN_ID = "synthetic-capture-bound-run";
    private static final String SOURCE_SHA = "synthetic-source-sha";
    private static final String CONTEXT_SHA = "synthetic-context-sha";
    private static final String[] EXPECTED_REQUESTED = {
        "n43", "n48", "n00", "n01", "n02", "n03", "n04", "n05", "n06", "n07",
        "n08", "n09", "n10", "n11", "n12", "n13", "n14", "n15", "n16", "n17",
        "n18", "n19", "n20", "n21", "n22", "n23", "n24", "n25", "n26", "n27",
        "n28", "n29", "n30", "n31", "n32", "n33", "n34", "n35", "n36", "n37",
        "n38", "n39", "n40", "n41", "n42", "n44", "n45", "n46", "n47",
        "n49", "n50", "n51", "n52", "n53", "n54", "n55", "n56", "n57", "n58",
        "n59"
    };

    private static int assertions;
    private static int cases;

    private CaptureBoundBodyCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testCaptureBoundSuccessAndFees();
        testNullArguments();
        testCaptureNetIdsRejections();
        testCohortFailures();
        testThrowingCaptureGet();
        testClosedGroupGuard();

        System.out.println("SYNTHETIC_CAPTURE_BOUND_BODY_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testCaptureBoundSuccessAndFees() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA,
                CONTEXT_SHA, fixture.capture, fixture.arms);
        Map<String, Object> body = attempt.body;
        check(attempt.failure == null && body != null, "VALID_COMPOSITION_RETURNS_BODY");
        check(body.get("originalCapture") == fixture.capture &&
                body.get("arms") == fixture.arms, "CAPTURE_AND_ARMS_MAPS_BORROWED_BY_IDENTITY");
        check(body.get("qualifiedPrior") == null && body.get("work") == null &&
                body.get("cleanup") == null, "PRIOR_WORK_CLEANUP_SLOTS_REMAIN_PENDING");

        Map<String, Object> cohort = child(body, "cohort");
        List<?> pair = (List<?>) cohort.get("netIds");
        List<?> requested = (List<?>) cohort.get("requestedNetIds");
        check(Arrays.asList("n43", "n48").equals(pair) &&
                Arrays.asList(EXPECTED_REQUESTED).equals(requested) &&
                !requested.equals(fixture.decoyIds),
                "NONPREFIX_PAIR_AND_LITERAL_REQUESTED_ORDER_FROM_CAPTURE_IDS");
        check(pair != requested && pair != fixture.captureIds &&
                requested != fixture.captureIds && pair != fixture.priorPair &&
                requested != fixture.priorRequested,
                "DERIVED_COHORT_LISTS_DETACHED_FROM_CAPTURE_AND_PRIOR_LISTS");
        check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                fixture.data.getCount == 1,
                "ONE_CAPTURE_GET_AND_ONE_SETUP_PRIOR_GET");
        check(debitDelta(beforeDebit, debit(meter), 279L, 138L, 65L),
                "COMPOSITION_FEE_279_138_65");
        check(before.unchanged(), "VALID_SETUP_CAPTURE_AND_ARMS_UNCHANGED");
        cases++;
    }

    private static void testNullArguments() throws Exception {
        Fixture fixture = fixture(false);
        for (int slot = 0; slot < 7; slot++) {
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = invokeNullArgument(slot, meter, fixture);
            assertFailure(attempt, IOException.class, "METADATA_ARGUMENTS",
                    "NULL_ARGUMENT_" + slot, before);
            check(fixture.capture.getCount == 0 && fixture.data.getCount == 0,
                    "NULL_ARGUMENT_" + slot + "_NO_INPUT_READS");
            if (slot != 0)
                check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L),
                        "NULL_ARGUMENT_" + slot + "_ZERO_METER_COST");
            cases++;
        }
    }

    private static void testCaptureNetIdsRejections() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            Fixture fixture = fixture(true);
            if (kind == 0) fixture.capture.remove("netIds");
            else if (kind == 1) fixture.capture.put("netIds", null);
            else fixture.capture.put("netIds", "not-a-list");
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA,
                    CONTEXT_SHA, fixture.capture, fixture.arms);
            assertFailure(attempt, IOException.class, "METADATA_CAPTURE_NET_IDS",
                    "CAPTURE_NET_IDS_KIND_" + kind, before);
            check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                    fixture.data.getCount == 0,
                    "MALFORMED_CAPTURE_PRECEDES_SETUP_ACCESS_" + kind);
            check(debitDelta(beforeDebit, debit(meter), 1L, 0L, 0L),
                    "BAD_CAPTURE_VALUE_COSTS_ONE_UNIT_" + kind);
            cases++;
        }
    }

    private static void testCohortFailures() throws Exception {
        for (int kind = 0; kind < 5; kind++) {
            Fixture fixture = fixture(false);
            String error;
            if (kind == 0) {
                fixture.captureIds.remove(fixture.captureIds.size() - 1);
                error = "COHORT_INPUT";
            } else if (kind == 1) {
                fixture.captureIds.set(59, "n00");
                error = "COHORT_VECTOR_DUPLICATE";
            } else if (kind == 2) {
                fixture.captureIds.set(43, "n60");
                error = "COHORT_NOT_IN_CAPTURE";
            } else if (kind == 3) {
                fixture.priorRequested.set(2, "n60");
                error = "COHORT_REQUESTED_VECTOR";
            } else {
                fixture.disposition.put("feedbackValidated", Boolean.FALSE);
                error = "COHORT_DISPOSITION";
            }
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA,
                    CONTEXT_SHA, fixture.capture, fixture.arms);
            assertFailure(attempt, IOException.class, error,
                    "COHORT_FAILURE_KIND_" + kind, before);
            check(fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                    "COHORT_FAILURE_READS_CAPTURE_AND_SETUP_ONCE_" + kind);
            cases++;
        }
    }

    private static void testThrowingCaptureGet() throws Exception {
        Fixture fixture = fixture(false);
        RuntimeException sentinel = new IllegalStateException("SYNTHETIC_NETIDS_GET");
        fixture.capture.getFailure = sentinel;
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA,
                CONTEXT_SHA, fixture.capture, fixture.arms);
        check(attempt.body == null && attempt.failure == sentinel,
                "CAPTURE_GET_RUNTIME_EXCEPTION_PROPAGATES_BY_IDENTITY");
        check(before.unchanged(), "THROWING_CAPTURE_INPUTS_UNCHANGED");
        check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                fixture.data.getCount == 0, "THROWING_GET_PRECEDES_SETUP_READ");
        check(debitDelta(beforeDebit, debit(meter), 1L, 0L, 0L),
                "THROWING_GET_COSTS_ONE_UNIT");
        cases++;
    }

    private static void testClosedGroupGuard() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        for (int index = 0; index < 640; index++) {
            meter.charge(1);
            meter.method("finishGroup", new Class<?>[0]);
        }
        check(number(meter, "units") == 640L && number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 1L && isClosed(meter) &&
                groupMeasureCount(meter) == 640,
                "REAL_METER_640_GROUPS_CLOSED_BEFORE_CALL");

        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA,
                CONTEXT_SHA, fixture.capture, fixture.arms);
        assertFailure(attempt, IOException.class,
                "native wrapper exceeded 640 measured groups",
                "CLOSED_GROUP_GUARD", before);
        check(fixture.capture.getCount == 0 && fixture.data.getCount == 0,
                "GROUP_GUARD_PRECEDES_CAPTURE_AND_SETUP_GETS");
        check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L) &&
                number(meter, "units") == 640L && number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 1L && isClosed(meter) &&
                groupMeasureCount(meter) == 640,
                "GROUP_GUARD_REJECTS_BEFORE_UNITS_OR_COPIES");
        cases++;
    }

    private static Fixture fixture(boolean failSetupRead) throws Exception {
        List<String> captureIds = captureIds();
        List<String> decoyIds = new ArrayList<String>();
        for (int index = 59; index >= 0; index--) decoyIds.add(id(index));

        List<String> pair = new ArrayList<String>();
        pair.add("n43");
        pair.add("n48");
        List<String> priorRequested = new ArrayList<String>(
                Arrays.asList(EXPECTED_REQUESTED));
        Map<String, Object> disposition = new LinkedHashMap<String, Object>();
        disposition.put("status", "RUN");
        disposition.put("reason", "TRIAL_EXECUTED");
        disposition.put("capturedFailedNetId", "n43");
        disposition.put("controlFailedNetId", "n48");
        disposition.put("cohortNetIds", pair);
        disposition.put("requestedNetIds", priorRequested);
        disposition.put("orderChanged", Boolean.TRUE);
        disposition.put("feedbackValidated", Boolean.TRUE);
        Map<String, Object> prior = new LinkedHashMap<String, Object>();
        prior.put("trialDisposition", disposition);
        TrackingMap data = new TrackingMap();
        data.put("prior", prior);
        data.failAnyGet = failSetupRead;
        CohortTerminalTelemetryStudy.SetupResult setup =
                new CohortTerminalTelemetryStudy.SetupResult(data, 0L, 0L, 0, 0);

        TrackingMap capture = new TrackingMap();
        capture.put("netIds", captureIds);
        capture.put("decoyNetIds", decoyIds);
        Map<String, Object> arms = new LinkedHashMap<String, Object>();
        arms.put("synthetic-arm-map", new LinkedHashMap<String, Object>());
        return new Fixture(data, setup, capture, captureIds, decoyIds, arms,
                pair, priorRequested, disposition);
    }

    private static List<String> captureIds() {
        List<String> ids = new ArrayList<String>();
        for (int index = 0; index < 60; index++) ids.add(id(index));
        return ids;
    }

    private static String id(int index) {
        return "n" + (index < 10 ? "0" : "") + Integer.toString(index);
    }

    private static Attempt invokeNullArgument(int slot,
            CohortTerminalTelemetryStudy.SetupMeter meter, Fixture fixture) {
        switch (slot) {
        case 0:
            return invoke(null, fixture.setup, RUN_ID, SOURCE_SHA, CONTEXT_SHA,
                    fixture.capture, fixture.arms);
        case 1:
            return invoke(meter, null, RUN_ID, SOURCE_SHA, CONTEXT_SHA,
                    fixture.capture, fixture.arms);
        case 2:
            return invoke(meter, fixture.setup, null, SOURCE_SHA, CONTEXT_SHA,
                    fixture.capture, fixture.arms);
        case 3:
            return invoke(meter, fixture.setup, RUN_ID, null, CONTEXT_SHA,
                    fixture.capture, fixture.arms);
        case 4:
            return invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA, null,
                    fixture.capture, fixture.arms);
        case 5:
            return invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA, CONTEXT_SHA,
                    null, fixture.arms);
        default:
            return invoke(meter, fixture.setup, RUN_ID, SOURCE_SHA, CONTEXT_SHA,
                    fixture.capture, null);
        }
    }

    private static Attempt invoke(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, String runId,
            String sourceSha, String contextSha, Map<String, Object> capture,
            Map<String, Object> arms) {
        try {
            return new Attempt(CurrentAssemblyMetadata.captureBoundBody(meter,
                    setup, runId, sourceSha, contextSha, capture, arms), null);
        } catch (Throwable failure) {
            while (failure instanceof InvocationTargetException && failure.getCause() != null)
                failure = failure.getCause();
            return new Attempt(null, failure);
        }
    }

    private static void assertFailure(Attempt attempt, Class<?> type,
            String message, String label, InputSnapshot before) {
        check(attempt.body == null && attempt.failure != null &&
                attempt.failure.getClass() == type &&
                message.equals(attempt.failure.getMessage()), label + "_EXACT_ERROR_NO_BODY");
        check(before.unchanged(), label + "_INPUTS_UNCHANGED");
    }

    private static CohortTerminalTelemetryStudy.SetupMeter newMeter() throws Exception {
        CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
        return new CohortTerminalTelemetryStudy.SetupMeter(parent,
                parent.meter(System.nanoTime()));
    }

    private static long[] debit(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return new long[] {number(meter, "units"), number(meter, "copiedEntries"),
                number(meter, "copyChunks")};
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter,
            String name) throws Exception {
        return ((Number) CohortTerminalTelemetryStudy.field(meter.meter, name)).longValue();
    }

    private static boolean debitDelta(long[] before, long[] after,
            long units, long copies, long chunks) {
        return after[0] - before[0] == units && after[1] - before[1] == copies &&
                after[2] - before[2] == chunks;
    }

    private static boolean isClosed(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        return Boolean.FALSE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open"));
    }

    private static int groupMeasureCount(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        Object measures = CohortTerminalTelemetryStudy.field(meter.meter, "groupMeasures");
        return measures instanceof List ? ((List<?>) measures).size() : -1;
    }

    private static Map<String, Object> child(Map<?, ?> values, String key) {
        return castMap(values.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class Fixture {
        final TrackingMap data;
        final CohortTerminalTelemetryStudy.SetupResult setup;
        final TrackingMap capture;
        final List<String> captureIds;
        final List<String> decoyIds;
        final Map<String, Object> arms;
        final List<String> priorPair;
        final List<String> priorRequested;
        final Map<String, Object> disposition;

        Fixture(TrackingMap data, CohortTerminalTelemetryStudy.SetupResult setup,
                TrackingMap capture, List<String> captureIds, List<String> decoyIds,
                Map<String, Object> arms, List<String> priorPair,
                List<String> priorRequested, Map<String, Object> disposition) {
            this.data = data;
            this.setup = setup;
            this.capture = capture;
            this.captureIds = captureIds;
            this.decoyIds = decoyIds;
            this.arms = arms;
            this.priorPair = priorPair;
            this.priorRequested = priorRequested;
            this.disposition = disposition;
        }
    }

    private static final class TrackingMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        int getCount;
        int netIdsGets;
        boolean failAnyGet;
        RuntimeException getFailure;

        public Object get(Object key) {
            getCount++;
            if ("netIds".equals(key)) netIdsGets++;
            if (getFailure != null) throw getFailure;
            if (failAnyGet) throw new IllegalStateException("SETUP_GET_BEFORE_CAPTURE_GUARD");
            return super.get(key);
        }
    }

    private static final class Attempt {
        final Map<String, Object> body;
        final Throwable failure;

        Attempt(Map<String, Object> body, Throwable failure) {
            this.body = body;
            this.failure = failure;
        }
    }

    private static final class InputSnapshot {
        private final TreeSnapshot data;
        private final TreeSnapshot capture;
        private final TreeSnapshot arms;

        InputSnapshot(Fixture fixture) {
            data = new TreeSnapshot(fixture.data);
            capture = new TreeSnapshot(fixture.capture);
            arms = new TreeSnapshot(fixture.arms);
        }

        boolean unchanged() {
            return data.unchanged(data.root) && capture.unchanged(capture.root) &&
                    arms.unchanged(arms.root);
        }
    }

    private static final class TreeSnapshot {
        final Object root;
        private final IdentityHashMap<Object, List<Object[]>> contents =
                new IdentityHashMap<Object, List<Object[]>>();

        TreeSnapshot(Object root) {
            this.root = root;
            capture(root);
        }

        private void capture(Object value) {
            if (value instanceof Map) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    Object key = entry.getKey();
                    Object child = entry.getValue();
                    entries.add(new Object[]{key, child});
                    capture(child);
                }
            } else if (value instanceof List) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Object child : (List<?>) value) {
                    entries.add(new Object[]{child});
                    capture(child);
                }
            }
        }

        boolean unchanged(Object currentRoot) { return matches(currentRoot); }

        private boolean matches(Object value) {
            List<Object[]> expected = contents.get(value);
            if (expected == null) return true;
            if (value instanceof Map) {
                Iterator<? extends Map.Entry<?, ?>> actual =
                        ((Map<?, ?>) value).entrySet().iterator();
                if (((Map<?, ?>) value).size() != expected.size()) return false;
                for (int index = 0; index < expected.size(); index++) {
                    if (!actual.hasNext()) return false;
                    Map.Entry<?, ?> entry = actual.next();
                    Object[] pair = expected.get(index);
                    if (entry.getKey() != pair[0] || entry.getValue() != pair[1] ||
                            !matches(pair[1])) return false;
                }
                return !actual.hasNext();
            }
            if (value instanceof List) {
                List<?> actual = (List<?>) value;
                if (actual.size() != expected.size()) return false;
                Iterator<?> iterator = actual.iterator();
                for (int index = 0; index < expected.size(); index++) {
                    if (!iterator.hasNext()) return false;
                    Object child = expected.get(index)[0];
                    if (iterator.next() != child || !matches(child)) return false;
                }
                return !iterator.hasNext();
            }
            return false;
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
