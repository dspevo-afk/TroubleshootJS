package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only canary for cleanup restoration summary correlations. */
public final class CleanupRestorationCanary {
    private static final boolean[] EXPECTED_COPY = {
        false, false, false, false, true, true, true, true
    };
    private static final boolean[] EXPECTED_TOTAL = {
        false, false, false, false, false, false, false, true
    };

    private static int assertions;
    private static int cases;

    private CleanupRestorationCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testTruthTableAndSummaryTampering();
        testLeafTypesAndComparisonPrecedence();
        testNestedCleanupMapTypes();
        testPreviousFailurePrecedence();
        testCorrelationHookBudget();
        testSinkAdmissionAndFreshMapGets();
        testWideR5MeterEquivalence();

        System.out.println("SYNTHETIC_CLEANUP_RESTORATION_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testTruthTableAndSummaryTampering() throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            boolean capture = (mask & 4) != 0;
            boolean nullCleanup = (mask & 2) != 0;
            boolean observeCleanup = (mask & 1) != 0;
            boolean expectedCopy = EXPECTED_COPY[mask];
            boolean expectedTotal = EXPECTED_TOTAL[mask];
            Map<String, Object> body = BodyCorrelationOracle.body();
            setRestoration(body, capture, nullCleanup, observeCleanup,
                    expectedCopy, expectedTotal);
            TreeSnapshot before = new TreeSnapshot(body);
            MemorySink transport = new MemorySink();
            MemorySink correlated = new MemorySink();

            CurrentV2CanonicalCodec.writeBody(body, transport);
            CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
            check(leaf(body, 0).equals(Boolean.valueOf(capture)),
                    "TRUTH_" + mask + "_CAPTURE_INPUT");
            check(leaf(body, 1).equals(Boolean.valueOf(nullCleanup)),
                    "TRUTH_" + mask + "_NULL_INPUT");
            check(leaf(body, 2).equals(Boolean.valueOf(observeCleanup)),
                    "TRUTH_" + mask + "_OBSERVE_INPUT");
            check(leaf(body, 3).equals(Boolean.valueOf(expectedCopy)),
                    "TRUTH_" + mask + "_MANUAL_CAPTURE_SUMMARY");
            check(leaf(body, 4).equals(Boolean.valueOf(expectedTotal)),
                    "TRUTH_" + mask + "_MANUAL_TOTAL_SUMMARY");
            check(Arrays.equals(transport.bytes(), correlated.bytes()),
                    "TRUTH_" + mask + "_SAME_AS_LOWER_WRITE_BODY");
            check(oneFinalLf(correlated.bytes()), "TRUTH_" + mask + "_FINAL_LF");
            check(before.unchanged(body), "TRUTH_" + mask + "_TREE_UNCHANGED");
            cases++;

            Map<String, Object> badCapture = BodyCorrelationOracle.body();
            setRestoration(badCapture, capture, nullCleanup, observeCleanup,
                    !expectedCopy, expectedTotal);
            expectFailure("BODY_RESTORATION_CAPTURE",
                    "TRUTH_" + mask + "_CAPTURE_SUMMARY_TAMPER", badCapture);

            Map<String, Object> badTotal = BodyCorrelationOracle.body();
            setRestoration(badTotal, capture, nullCleanup, observeCleanup,
                    expectedCopy, !expectedTotal);
            expectFailure("BODY_RESTORATION_TOTAL",
                    "TRUTH_" + mask + "_TOTAL_SUMMARY_TAMPER", badTotal);
        }
    }

    private static void testLeafTypesAndComparisonPrecedence() throws Exception {
        for (int field = 0; field < 5; field++) {
            for (int kind = 0; kind < 4; kind++) {
                Map<String, Object> body = BodyCorrelationOracle.body();
                setRestoration(body, true, true, true, true, true);
                if (kind == 0) removeLeaf(body, field);
                else if (kind == 1) setLeaf(body, field, null);
                else if (kind == 2) setLeaf(body, field, "wrong-type");
                else setLeaf(body, field, Integer.valueOf(7));
                expectFailure("BODY_RESTORATION_BOOLEAN",
                        "LEAF_" + field + "_KIND_" + kind, body);
            }

            Map<String, Object> preempt = BodyCorrelationOracle.body();
            setRestoration(preempt, true, true, true, false, false);
            setLeaf(preempt, field, "wrong-type");
            expectFailure("BODY_RESTORATION_BOOLEAN",
                    "LEAF_" + field + "_TYPE_BEFORE_SUMMARY_COMPARISONS", preempt);
        }

        Map<String, Object> captureBeforeTotal = BodyCorrelationOracle.body();
        setRestoration(captureBeforeTotal, true, true, true, false, false);
        expectFailure("BODY_RESTORATION_CAPTURE",
                "CAPTURE_SUMMARY_PRECEDES_TOTAL_SUMMARY", captureBeforeTotal);
    }

    private static void testNestedCleanupMapTypes() throws Exception {
        String[] armNames = {"null", "observe"};
        for (int arm = 0; arm < armNames.length; arm++) {
            for (int kind = 0; kind < 4; kind++) {
                Map<String, Object> body = BodyCorrelationOracle.body();
                setRestoration(body, true, true, true, true, true);
                if (kind == 0) setArmCleanup(body, armNames[arm], null, true);
                else if (kind == 1) setArmCleanup(body, armNames[arm], null, false);
                else if (kind == 2)
                    setArmCleanup(body, armNames[arm], "wrong-map-type", false);
                else setArmCleanup(body, armNames[arm], new ArrayList<Object>(), false);
                expectFailure("BODY_CORRELATION_MAP",
                        armNames[arm].toUpperCase() + "_CLEANUP_MAP_KIND_" + kind,
                        body);
            }
        }
    }

    private static void testPreviousFailurePrecedence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        body.put("schema", "wrong-schema");
        expectFailure("BODY_SCHEMA", "HEADER_PRECEDES_RESTORATION", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        body.put("inputContextSha256", BodyCorrelationOracle.OTHER_CONTEXT_SHA);
        expectFailure("BODY_PRIOR_CONTEXT", "PRIOR_PRECEDES_RESTORATION", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        child(child(body, "arms"), "null").put("armRole", "OBSERVE");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_PRECEDES_RESTORATION", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        child(child(body, "arms"), "observe").put("armRole", "NULL");
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_PRECEDES_RESTORATION", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        child(body, "cleanup").put("cleanupNanos", Integer.valueOf(1));
        expectFailure("BODY_CLEANUP_NANOS", "NANOS_PRECEDES_RESTORATION", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 4, "bad-restoration-leaf");
        body.put("cleanup", null);
        expectFailure("BODY_ROOT_CONTAINER", "ROOT_CONTAINER_PRECEDES_RESTORATION", body);
    }

    private static void testCorrelationHookBudget() throws Exception {
        Method validate = CurrentV2CanonicalCodec.class.getDeclaredMethod(
                "validateCorrelations", Map.class,
                CohortTerminalTelemetryStudy.CanonicalJsonSink.class);
        validate.setAccessible(true);
        HookCountSink sink = new HookCountSink();
        validate.invoke(null, BodyCorrelationOracle.body(), sink);
        check(sink.hooks == 4162, "CORRELATION_HOOK_BUDGET_4162");
        cases++;
    }
    private static void testSinkAdmissionAndFreshMapGets() throws Exception {
        PermitState directState = new PermitState();
        Map<String, Object> direct = guardedTree(
                BodyCorrelationOracle.body(), directState);
        boolean blocked = false;
        try {
            direct.get("originalCapture");
        } catch (IllegalStateException expected) {
            blocked = true;
        }
        check(blocked && directState.rejectedGets == 1,
                "MAP_GET_REQUIRES_FRESH_SINK_ADMISSION");
        cases++;

        PermitState deniedState = new PermitState();
        Map<String, Object> denied = guardedTree(
                BodyCorrelationOracle.body(), deniedState);
        TreeSnapshot deniedBefore = new TreeSnapshot(denied);
        PermitSink deniedSink = new PermitSink(deniedState, true);
        boolean admissionDenied = false;
        try {
            CurrentV2CanonicalCodec.writeCorrelatedBody(denied, deniedSink);
        } catch (AdmissionDenied expected) {
            admissionDenied = true;
        }
        check(admissionDenied, "SINK_DENIAL_PRECEDES_OBSERVATIONS");
        check(deniedState.successfulGets == 0 && deniedState.rejectedGets == 0,
                "DENIED_ADMISSION_HAS_NO_MAP_GET");
        check(deniedSink.bytes().length == 0, "DENIED_ADMISSION_ZERO_BYTES");
        check(deniedBefore.unchanged(denied), "DENIED_ADMISSION_TREE_UNCHANGED");
        cases++;

        PermitState admittedState = new PermitState();
        Map<String, Object> admitted = guardedTree(
                BodyCorrelationOracle.body(), admittedState);
        TreeSnapshot admittedBefore = new TreeSnapshot(admitted);
        PermitSink admittedSink = new PermitSink(admittedState, false);
        CurrentV2CanonicalCodec.writeCorrelatedBody(admitted, admittedSink);
        check(admittedState.successfulGets == 65,
                "SIXTY_FIVE_CORRELATION_GETS_HAVE_FRESH_ADMISSION");
        check(admittedState.rejectedGets == 0 && !admittedState.permitOutstanding,
                "NO_REJECTED_OR_STALE_MAP_ADMISSION");
        check(admittedSink.bytes().length > 0 && oneFinalLf(admittedSink.bytes()),
                "ADMITTED_SYNTHETIC_BODY_COMPLETE");
        check(admittedBefore.unchanged(admitted), "ADMITTED_TREE_UNCHANGED");
        cases++;
    }

    private static void testWideR5MeterEquivalence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.wideBody();
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink transport = new MemorySink();
        MemorySink correlated = new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body, transport);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(transport.bytes().length > 4096 && transport.bytes().length <= 8192,
                "WIDE_LITERAL_BODY_SPANS_TWO_BLOCKS");
        check(Arrays.equals(transport.bytes(), correlated.bytes()),
                "WIDE_CORRELATED_BYTES_EQUAL_LOWER_WRITE_BODY");
        check(before.unchanged(body), "WIDE_BODY_UNCHANGED_AFTER_MEMORY_PASSES");

        CountMetrics oldMetrics = countWithR5Sink(body, transport.bytes().length, false);
        CountMetrics newMetrics = countWithR5Sink(body, correlated.bytes().length, true);
        check(oldMetrics.bytes == transport.bytes().length &&
                newMetrics.bytes == correlated.bytes().length,
                "R5_COUNT_BYTES_MATCH_ENCODED_BODY");
        check(oldMetrics.units == newMetrics.units, "R5_UNITS_UNCHANGED");
        check(oldMetrics.copiedEntries == newMetrics.copiedEntries,
                "R5_COPIED_ENTRIES_UNCHANGED");
        check(oldMetrics.copyChunks == newMetrics.copyChunks,
                "R5_COPY_CHUNKS_UNCHANGED");
        check(oldMetrics.groups == newMetrics.groups, "R5_GROUPS_UNCHANGED");
        check(newMetrics.units == 2L, "R5_TWO_BLOCK_UNIT_COUNT");
        check(newMetrics.copiedEntries == 0L && newMetrics.copyChunks == 0L,
                "R5_COUNT_ADDS_NO_COPY_METRICS");
        check(before.unchanged(body), "WIDE_BODY_UNCHANGED_AFTER_R5_PASSES");
        cases++;
    }

    private static CountMetrics countWithR5Sink(Map<String, Object> body,
            int expectedBytes, boolean correlated) throws Exception {
        MeterFixture fixture = new MeterFixture();
        fixture.setup.charge(1L);
        long beforeUnits = number(fixture.setup.meter, "units");
        long beforeCopies = number(fixture.setup.meter, "copiedEntries");
        long beforeChunks = number(fixture.setup.meter, "copyChunks");
        long beforeGroups = number(fixture.setup.meter, "groups");
        long exportStarted = number(fixture.setup.meter, "groupStarted");
        CohortTerminalTelemetryStudy.R5CountSink sink =
                new CohortTerminalTelemetryStudy.R5CountSink(
                        fixture.setup, fixture.started, exportStarted);
        if (correlated) CurrentV2CanonicalCodec.writeCorrelatedBody(body, sink);
        else CurrentV2CanonicalCodec.writeBody(body, sink);
        sink.complete((long) expectedBytes);
        fixture.setup.finish();
        return new CountMetrics(
                number(fixture.setup.meter, "units") - beforeUnits,
                number(fixture.setup.meter, "copiedEntries") - beforeCopies,
                number(fixture.setup.meter, "copyChunks") - beforeChunks,
                number(fixture.setup.meter, "groups") - beforeGroups,
                sink.position);
    }

    private static void expectFailure(String expectedCode, String label,
            Map<String, Object> body) throws Exception {
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink sink = new MemorySink();
        CurrentV2CanonicalCodec.CodecFailure failure = null;
        try {
            CurrentV2CanonicalCodec.writeCorrelatedBody(body, sink);
        } catch (CurrentV2CanonicalCodec.CodecFailure rejected) {
            failure = rejected;
        }
        check(failure != null && expectedCode.equals(failure.code),
                label + "_EXACT_FAILURE");
        check(sink.bytes().length == 0, label + "_ZERO_ENCODED_BYTES");
        check(before.unchanged(body), label + "_TREE_UNCHANGED");
        cases++;
    }

    private static void setRestoration(Map<String, Object> body,
            boolean capture, boolean nullCleanup, boolean observeCleanup,
            boolean copiedCapture, boolean total) {
        setLeaf(body, 0, Boolean.valueOf(capture));
        setLeaf(body, 1, Boolean.valueOf(nullCleanup));
        setLeaf(body, 2, Boolean.valueOf(observeCleanup));
        setLeaf(body, 3, Boolean.valueOf(copiedCapture));
        setLeaf(body, 4, Boolean.valueOf(total));
    }

    private static Object leaf(Map<String, Object> body, int field) {
        return leafMap(body, field).get(leafKey(field));
    }

    private static void setLeaf(Map<String, Object> body, int field, Object value) {
        leafMap(body, field).put(leafKey(field), value);
    }

    private static void removeLeaf(Map<String, Object> body, int field) {
        leafMap(body, field).remove(leafKey(field));
    }

    private static Map<String, Object> leafMap(Map<String, Object> body, int field) {
        if (field == 0) return child(body, "originalCapture");
        if (field == 1)
            return child(child(child(body, "arms"), "null"), "cleanup");
        if (field == 2)
            return child(child(child(body, "arms"), "observe"), "cleanup");
        return child(body, "cleanup");
    }

    private static String leafKey(int field) {
        if (field == 0 || field == 1 || field == 2) return "contextRestored";
        if (field == 3) return "originalCaptureContextRestored";
        return "contextRestored";
    }

    private static void setArmCleanup(Map<String, Object> body, String arm,
            Object value, boolean remove) {
        Map<String, Object> armMap = child(child(body, "arms"), arm);
        if (remove) armMap.remove("cleanup");
        else armMap.put("cleanup", value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    private static boolean oneFinalLf(byte[] bytes) {
        if (bytes.length == 0 || bytes[bytes.length - 1] != (byte) 10) return false;
        int lineFeeds = 0;
        for (int index = 0; index < bytes.length; index++) {
            if ((bytes[index] & 255) > 127) return false;
            if (bytes[index] == (byte) 10) {
                lineFeeds++;
                if (index != bytes.length - 1) return false;
            }
        }
        return lineFeeds == 1;
    }

    private static long number(Object owner, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(owner, name);
        if (!(value instanceof Number)) throw new AssertionError("METER_FIELD_" + name);
        return ((Number) value).longValue();
    }

    private static void check(boolean condition, String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private static Object guardedValue(Object value, PermitState state) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                copy.put((String) entry.getKey(), guardedValue(entry.getValue(), state));
            return new PermitGetMap(copy, state);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object child : (List<?>) value) copy.add(guardedValue(child, state));
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> guardedTree(Map<String, Object> body,
            PermitState state) {
        return (Map<String, Object>) guardedValue(body, state);
    }

    private static final class HookCountSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        int hooks;

        public void beforeDataOperation() { hooks++; }

        public void writeByte(int ascii) throws Exception {
            throw new IOException("UNEXPECTED_CORRELATION_OUTPUT");
        }
    }
    private static final class MemorySink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        public void beforeDataOperation() { }

        public void writeByte(int ascii) throws Exception {
            if (ascii < 0 || ascii > 127) throw new IOException("SINK_ASCII");
            output.write(ascii);
        }

        byte[] bytes() { return output.toByteArray(); }
    }

    private static final class PermitState {
        int serial;
        int consumedSerial;
        int successfulGets;
        int rejectedGets;
        boolean permitOutstanding;

        void grant() {
            serial++;
            permitOutstanding = true;
        }

        void consumeGet() {
            if (!permitOutstanding || serial <= consumedSerial) {
                rejectedGets++;
                throw new IllegalStateException("MAP_GET_WITHOUT_FRESH_SINK_PERMIT");
            }
            consumedSerial = serial;
            successfulGets++;
            permitOutstanding = false;
        }

        void revoke() { permitOutstanding = false; }
    }

    private static final class PermitGetMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final PermitState state;

        PermitGetMap(Map<String, Object> delegate, PermitState state) {
            this.delegate = delegate;
            this.state = state;
        }

        public Object get(Object key) {
            state.consumeGet();
            return delegate.get(key);
        }

        public Set<Map.Entry<String, Object>> entrySet() {
            final Set<Map.Entry<String, Object>> entries = delegate.entrySet();
            return new AbstractSet<Map.Entry<String, Object>>() {
                public int size() {
                    state.revoke();
                    return entries.size();
                }

                public Iterator<Map.Entry<String, Object>> iterator() {
                    final Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
                    return new Iterator<Map.Entry<String, Object>>() {
                        public boolean hasNext() {
                            state.revoke();
                            return iterator.hasNext();
                        }

                        public Map.Entry<String, Object> next() {
                            state.revoke();
                            return iterator.next();
                        }

                        public void remove() {
                            state.revoke();
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        }
    }

    private static final class PermitSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final PermitState state;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean denyNext;

        PermitSink(PermitState state, boolean denyFirstAdmission) {
            this.state = state;
            denyNext = denyFirstAdmission;
        }

        public void beforeDataOperation() {
            if (denyNext) {
                denyNext = false;
                state.revoke();
                throw new AdmissionDenied();
            }
            state.grant();
        }

        public void writeByte(int ascii) throws Exception {
            state.revoke();
            if (ascii < 0 || ascii > 127) throw new IOException("PERMIT_SINK_ASCII");
            output.write(ascii);
        }

        byte[] bytes() { return output.toByteArray(); }
    }

    private static final class AdmissionDenied extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class TreeSnapshot {
        private final IdentityHashMap<Object, List<Object[]>> contents =
                new IdentityHashMap<Object, List<Object[]>>();

        TreeSnapshot(Object root) { capture(root); }

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

        boolean unchanged(Object root) { return matches(root); }

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
                for (int index = 0; index < expected.size(); index++) {
                    Object child = expected.get(index)[0];
                    if (actual.get(index) != child || !matches(child)) return false;
                }
                return true;
            }
            return false;
        }
    }

    private static final class MeterFixture {
        final long started;
        final CohortTerminalTelemetryStudy.Parent parent;
        final CohortTerminalTelemetryStudy.SetupMeter setup;

        MeterFixture() throws Exception {
            started = System.nanoTime();
            parent = new CohortTerminalTelemetryStudy.Parent();
            setup = new CohortTerminalTelemetryStudy.SetupMeter(
                    parent, parent.meter(started));
        }
    }

    private static final class CountMetrics {
        final long units;
        final long copiedEntries;
        final long copyChunks;
        final long groups;
        final long bytes;

        CountMetrics(long units, long copiedEntries, long copyChunks,
                long groups, long bytes) {
            this.units = units;
            this.copiedEntries = copiedEntries;
            this.copyChunks = copyChunks;
            this.groups = groups;
            this.bytes = bytes;
        }
    }
}
