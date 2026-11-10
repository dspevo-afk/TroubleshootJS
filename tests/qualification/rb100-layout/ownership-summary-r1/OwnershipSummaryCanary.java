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

/** Synthetic-only canary for ownership summary correlations. */
public final class OwnershipSummaryCanary {
    private static final String[] OWNERSHIP_KEYS = {
        "ownersReleased", "originalCaptureOwnersReleased",
        "adapterSessionAliasDropped", "adapterSearchAliasDropped",
        "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
        "adapterBoardTemplateAliasesDropped", "nullArmAliasesDropped",
        "adapterSessionAliasDropped", "adapterSearchAliasDropped",
        "adapterCandidateAliasDropped", "adapterRouteResultAliasDropped",
        "adapterBoardTemplateAliasesDropped", "observeArmAliasesDropped"
    };

    private static int assertions;
    private static int cases;

    private OwnershipSummaryCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testCaptureOwnership();
        testArmTruthTables();
        testLeafTypesAndPrecedence();
        testEarlierFailurePrecedence();
        testCorrelationHooks();
        testSinkAdmissionsAndGets();
        testWideR5MeterEquivalence();

        System.out.println("SYNTHETIC_OWNERSHIP_SUMMARY_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testCaptureOwnership() throws Exception {
        for (int value = 0; value < 2; value++) {
            boolean released = value != 0;
            Map<String, Object> body = BodyCorrelationOracle.body();
            setLeaf(body, 0, Boolean.valueOf(released));
            setLeaf(body, 1, Boolean.valueOf(released));
            accept(body, "CAPTURE_" + value + "_MATCHING_COPY");

            body = BodyCorrelationOracle.body();
            setLeaf(body, 0, Boolean.valueOf(released));
            setLeaf(body, 1, Boolean.valueOf(!released));
            expectFailure("BODY_OWNERSHIP_CAPTURE",
                    "CAPTURE_" + value + "_FLIPPED_COPY", body);
        }
    }

    private static void testArmTruthTables() throws Exception {
        for (int arm = 0; arm < 2; arm++) {
            int first = arm == 0 ? 2 : 8;
            int summary = arm == 0 ? 7 : 13;
            String name = arm == 0 ? "NULL" : "OBSERVE";
            String code = arm == 0 ? "BODY_OWNERSHIP_NULL" :
                    "BODY_OWNERSHIP_OBSERVE";
            for (int mask = 0; mask < 32; mask++) {
                boolean aggregate = mask == 31;
                Map<String, Object> body = BodyCorrelationOracle.body();
                setLeaf(body, 0, Boolean.TRUE);
                setLeaf(body, 1, Boolean.TRUE);
                setArmMask(body, first, summary, mask, aggregate);
                accept(body, name + "_MASK_" + mask + "_AGGREGATE_" + aggregate);

                body = BodyCorrelationOracle.body();
                setLeaf(body, 0, Boolean.TRUE);
                setLeaf(body, 1, Boolean.TRUE);
                setArmMask(body, first, summary, mask, !aggregate);
                expectFailure(code, name + "_MASK_" + mask + "_TAMPERED_SUMMARY",
                        body);
            }
        }
    }

    private static void testLeafTypesAndPrecedence() throws Exception {
        for (int field = 0; field < OWNERSHIP_KEYS.length; field++) {
            for (int kind = 0; kind < 4; kind++) {
                Map<String, Object> body = BodyCorrelationOracle.body();
                if (kind == 0) removeLeaf(body, field);
                else if (kind == 1) setLeaf(body, field, null);
                else if (kind == 2) setLeaf(body, field, "wrong-type");
                else setLeaf(body, field, Integer.valueOf(7));
                expectFailure("BODY_OWNERSHIP_BOOLEAN",
                        "LEAF_" + field + "_KIND_" + kind, body);
            }
        }

        Map<String, Object> typeBeforeComparison = BodyCorrelationOracle.body();
        setLeaf(typeBeforeComparison, 1, Boolean.FALSE);
        setLeaf(typeBeforeComparison, 13, "wrong-type");
        expectFailure("BODY_OWNERSHIP_BOOLEAN",
                "ALL_TYPES_PRECEDE_CAPTURE_COMPARISON", typeBeforeComparison);

        Map<String, Object> captureFirst = BodyCorrelationOracle.body();
        setLeaf(captureFirst, 1, Boolean.FALSE);
        setLeaf(captureFirst, 7, Boolean.FALSE);
        setLeaf(captureFirst, 13, Boolean.FALSE);
        expectFailure("BODY_OWNERSHIP_CAPTURE",
                "CAPTURE_PRECEDES_NULL_AND_OBSERVE", captureFirst);

        Map<String, Object> nullFirst = BodyCorrelationOracle.body();
        setLeaf(nullFirst, 7, Boolean.FALSE);
        setLeaf(nullFirst, 13, Boolean.FALSE);
        expectFailure("BODY_OWNERSHIP_NULL",
                "NULL_PRECEDES_OBSERVE", nullFirst);

        Map<String, Object> observeOnly = BodyCorrelationOracle.body();
        setLeaf(observeOnly, 13, Boolean.FALSE);
        expectFailure("BODY_OWNERSHIP_OBSERVE", "OBSERVE_SUMMARY_MISMATCH",
                observeOnly);
    }

    private static void testEarlierFailurePrecedence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        setLeaf(body, 7, Boolean.FALSE);
        body.put("schema", "wrong-schema");
        expectFailure("BODY_SCHEMA", "HEADER_PRECEDES_OWNERSHIP", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 7, Boolean.FALSE);
        body.put("inputContextSha256", BodyCorrelationOracle.OTHER_CONTEXT_SHA);
        expectFailure("BODY_PRIOR_CONTEXT", "CONTEXT_PRECEDES_OWNERSHIP", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 7, Boolean.FALSE);
        child(child(body, "arms"), "null").put("armRole", "OBSERVE");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_PRECEDES_OWNERSHIP", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 7, Boolean.FALSE);
        child(child(body, "arms"), "observe").put("armRole", "NULL");
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_PRECEDES_OWNERSHIP", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 7, Boolean.FALSE);
        child(body, "cleanup").put("cleanupNanos", Integer.valueOf(1));
        expectFailure("BODY_CLEANUP_NANOS", "NANOS_PRECEDE_OWNERSHIP", body);

        String[] restorationCodes = {
            "BODY_RESTORATION_BOOLEAN", "BODY_RESTORATION_BOOLEAN",
            "BODY_RESTORATION_BOOLEAN", "BODY_RESTORATION_BOOLEAN",
            "BODY_RESTORATION_BOOLEAN"
        };
        for (int field = 0; field < restorationCodes.length; field++) {
            body = BodyCorrelationOracle.body();
            setLeaf(body, 0, "wrong-ownership-type");
            setRestorationLeaf(body, field, "wrong-restoration-type");
            expectFailure(restorationCodes[field],
                    "RESTORATION_TYPE_" + field + "_PRECEDES_OWNERSHIP", body);
        }

        body = BodyCorrelationOracle.body();
        setLeaf(body, 0, Boolean.FALSE);
        setRestorationLeaf(body, 3, Boolean.FALSE);
        expectFailure("BODY_RESTORATION_CAPTURE",
                "RESTORATION_CAPTURE_PRECEDES_OWNERSHIP", body);

        body = BodyCorrelationOracle.body();
        setLeaf(body, 0, Boolean.FALSE);
        setRestorationLeaf(body, 4, Boolean.FALSE);
        expectFailure("BODY_RESTORATION_TOTAL",
                "RESTORATION_TOTAL_PRECEDES_OWNERSHIP", body);
    }

    private static void testCorrelationHooks() throws Exception {
        Method validate = CurrentV2CanonicalCodec.class.getDeclaredMethod(
                "validateCorrelations", Map.class,
                CohortTerminalTelemetryStudy.CanonicalJsonSink.class);
        validate.setAccessible(true);
        HookCountSink sink = new HookCountSink();
        validate.invoke(null, BodyCorrelationOracle.body(), sink);
        check(sink.hooks == 4052, "CORRELATION_HOOK_BUDGET_4052");
        cases++;
    }

    private static void testSinkAdmissionsAndGets() throws Exception {
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
        check(admissionDenied, "SINK_DENIAL_PRECEDES_OWNERSHIP_READS");
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
        check(admittedState.successfulGets == 59,
                "FIFTY_NINE_CORRELATION_GETS_HAVE_FRESH_ADMISSION");
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

        CountMetrics oldMetrics = countWithR5Sink(
                body, transport.bytes().length, false);
        CountMetrics newMetrics = countWithR5Sink(
                body, correlated.bytes().length, true);
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

    private static void accept(Map<String, Object> body, String label)
            throws Exception {
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink transport = new MemorySink();
        MemorySink correlated = new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body, transport);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(transport.bytes(), correlated.bytes()),
                label + "_SAME_AS_LOWER_WRITE_BODY");
        check(before.unchanged(body), label + "_TREE_UNCHANGED");
        cases++;
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

    private static void setArmMask(Map<String, Object> body, int first,
            int summary, int mask, boolean aggregate) {
        for (int offset = 0; offset < 5; offset++)
            setLeaf(body, first + offset,
                    Boolean.valueOf((mask & (1 << offset)) != 0));
        setLeaf(body, summary, Boolean.valueOf(aggregate));
    }

    private static void setRestorationLeaf(Map<String, Object> body, int field,
            Object value) {
        if (field == 0) child(body, "originalCapture").put("contextRestored", value);
        else if (field == 1)
            armCleanup(body, "null").put("contextRestored", value);
        else if (field == 2)
            armCleanup(body, "observe").put("contextRestored", value);
        else if (field == 3)
            child(body, "cleanup").put("originalCaptureContextRestored", value);
        else child(body, "cleanup").put("contextRestored", value);
    }

    private static Object leaf(Map<String, Object> body, int field) {
        return leafMap(body, field).get(OWNERSHIP_KEYS[field]);
    }

    private static void setLeaf(Map<String, Object> body, int field, Object value) {
        leafMap(body, field).put(OWNERSHIP_KEYS[field], value);
    }

    private static void removeLeaf(Map<String, Object> body, int field) {
        leafMap(body, field).remove(OWNERSHIP_KEYS[field]);
    }

    private static Map<String, Object> leafMap(Map<String, Object> body, int field) {
        if (field == 0) return child(body, "originalCapture");
        if (field == 1 || field == 7 || field == 13)
            return child(body, "cleanup");
        return armCleanup(body, field < 7 ? "null" : "observe");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> armCleanup(Map<String, Object> body,
            String arm) {
        return child(child(child(body, "arms"), arm), "cleanup");
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
