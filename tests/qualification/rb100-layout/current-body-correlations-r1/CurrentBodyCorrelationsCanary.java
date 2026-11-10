package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Synthetic-only canary for the finite current-body correlation contract. */
public final class CurrentBodyCorrelationsCanary {
    private static final String[] ROOT_KEYS = {
        "schema", "mode", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "inputContextSha256", "qualifiedPrior",
        "originalCapture", "cohort", "arms", "work", "cleanup"
    };

    private static int assertions;
    private static int cases;

    private CurrentBodyCorrelationsCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testMinimalIndependentOracleAndTransportCompatibility();
        testValidIntegerAndLongBoundaries();
        testRelationFailuresAndHeaderPrecedence();
        testFreshSinkAdmissionAndZeroByteDenial();
        testWideTwoBlockMeterEquivalence();

        System.out.println("SYNTHETIC_BODY_CORRELATIONS_COMPONENT_ONLY cases=" + cases +
                " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testMinimalIndependentOracleAndTransportCompatibility()
            throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        ReferenceSnapshot before = new ReferenceSnapshot(body);
        byte[] expected = asciiBytes(BodyCorrelationOracle.MINIMAL_BODY_JSON);
        MemorySink transport = new MemorySink();
        MemorySink correlated = new MemorySink();

        check(hasRootOrder(body), "MINIMAL_ROOT_KEY_ORDER");
        CurrentV2CanonicalCodec.writeBody(body, transport);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(transport.bytes(), expected), "MINIMAL_LITERAL_OLD_TRANSPORT_BYTES");
        check(Arrays.equals(correlated.bytes(), expected), "MINIMAL_LITERAL_CORRELATED_BYTES");
        check(Arrays.equals(transport.bytes(), correlated.bytes()), "MINIMAL_OLD_NEW_BYTES_EQUAL");
        check(oneFinalLf(correlated.bytes()), "MINIMAL_ONE_FINAL_LF");
        check(before.unchanged(body), "MINIMAL_BODY_KEYS_REFS_SCALARS_UNCHANGED");
        cases++;
    }

    private static void testValidIntegerAndLongBoundaries() throws Exception {
        validNumericPair(Integer.valueOf(0), Long.valueOf(0L), 0L, 0L,
                "INTEGER_ZERO_LONG_ZERO");
        validNumericPair(Long.valueOf(0L), Integer.valueOf(0), 0L, 0L,
                "LONG_ZERO_INTEGER_ZERO");
        validNumericPair(Integer.valueOf(1), Long.valueOf(1L), 1L, 1L,
                "INTEGER_ONE_LONG_ONE");
        validNumericPair(Long.valueOf(1L), Integer.valueOf(1), 1L, 1L,
                "LONG_ONE_INTEGER_ONE");
        validNumericPair(Integer.valueOf(Integer.MAX_VALUE),
                Long.valueOf((long) Integer.MAX_VALUE),
                (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE,
                "INTEGER_MAX_LONG_EQUAL");
        validNumericPair(Long.valueOf((long) Integer.MAX_VALUE),
                Integer.valueOf(Integer.MAX_VALUE),
                (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE,
                "LONG_INTEGER_MAX_EQUAL");
        validNumericPair(Long.valueOf(BodyCorrelationOracle.MAX_CLEANUP_NANOS),
                Long.valueOf(BodyCorrelationOracle.MAX_CLEANUP_NANOS),
                BodyCorrelationOracle.MAX_CLEANUP_NANOS,
                BodyCorrelationOracle.MAX_CLEANUP_NANOS, "LONG_MAXIMUM_EQUAL");
    }

    private static void validNumericPair(Object workNanos, Object cleanupNanos,
            long workValue, long cleanupValue, String label) throws Exception {
        Map<String, Object> body =
                BodyCorrelationOracle.body(workNanos, cleanupNanos);
        ReferenceSnapshot before = new ReferenceSnapshot(body);
        byte[] expected = asciiBytes(
                BodyCorrelationOracle.bodyJson(workValue, cleanupValue));
        MemorySink transport = new MemorySink();
        MemorySink correlated = new MemorySink();

        CurrentV2CanonicalCodec.writeBody(body, transport);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(transport.bytes(), expected), label + "_OLD_LITERAL_BYTES");
        check(Arrays.equals(correlated.bytes(), expected), label + "_CORRELATED_LITERAL_BYTES");
        check(Arrays.equals(transport.bytes(), correlated.bytes()), label + "_OLD_NEW_EQUAL");
        check(oneFinalLf(correlated.bytes()), label + "_ONE_FINAL_LF");
        check(before.unchanged(body), label + "_BODY_UNCHANGED");
        cases++;
    }

    private static void testRelationFailuresAndHeaderPrecedence() throws Exception {
        Map<String, Object> body;

        body = BodyCorrelationOracle.body();
        body.put("inputContextSha256", BodyCorrelationOracle.OTHER_CONTEXT_SHA);
        expectFailure("BODY_PRIOR_CONTEXT", "PRIOR_CONTEXT_MISMATCH", body);
        body = BodyCorrelationOracle.body();
        child(body, "qualifiedPrior").remove("inputContextSha256");
        expectFailure("BODY_PRIOR_CONTEXT", "PRIOR_CONTEXT_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(body, "qualifiedPrior").put("inputContextSha256", Integer.valueOf(3));
        expectFailure("BODY_PRIOR_CONTEXT", "PRIOR_CONTEXT_INTEGER", body);
        body = BodyCorrelationOracle.body();
        child(body, "qualifiedPrior").put("inputContextSha256", null);
        expectFailure("BODY_PRIOR_CONTEXT", "PRIOR_CONTEXT_NULL", body);
        body = BodyCorrelationOracle.body();
        body.remove("qualifiedPrior");
        expectFailure("BODY_ROOT_ORDER", "PRIOR_ROOT_MISSING_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("qualifiedPrior", null);
        expectFailure("BODY_ROOT_CONTAINER", "PRIOR_ROOT_NULL_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("qualifiedPrior", new ArrayList<Object>());
        expectFailure("BODY_ROOT_CONTAINER", "PRIOR_ROOT_LIST_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("schema", "wrong-schema");
        body.remove("qualifiedPrior");
        expectFailure("BODY_SCHEMA", "HEADER_PRECEDES_RELATION_FAILURE", body);

        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "null").put("armRole", "OBSERVE");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_MISMATCH", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "null").remove("armRole");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "null").put("armRole", Integer.valueOf(1));
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_INTEGER", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "null").put("armRole", null);
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_NULL", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").remove("null");
        expectFailure("BODY_CORRELATION_MAP", "NULL_ARM_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").put("null", "not-a-map");
        expectFailure("BODY_CORRELATION_MAP", "NULL_ARM_STRING", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").put("null", new ArrayList<Object>());
        expectFailure("BODY_CORRELATION_MAP", "NULL_ARM_LIST", body);

        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "observe").put("armRole", "NULL");
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_MISMATCH", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "observe").remove("armRole");
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "observe").put("armRole", Boolean.TRUE);
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_BOOLEAN", body);
        body = BodyCorrelationOracle.body();
        child(child(body, "arms"), "observe").put("armRole", null);
        expectFailure("BODY_OBSERVE_ROLE", "OBSERVE_ROLE_NULL", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").remove("observe");
        expectFailure("BODY_CORRELATION_MAP", "OBSERVE_ARM_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").put("observe", "not-a-map");
        expectFailure("BODY_CORRELATION_MAP", "OBSERVE_ARM_STRING", body);
        body = BodyCorrelationOracle.body();
        child(body, "arms").put("observe", new ArrayList<Object>());
        expectFailure("BODY_CORRELATION_MAP", "OBSERVE_ARM_LIST", body);

        body = BodyCorrelationOracle.body(Integer.valueOf(0), Integer.valueOf(1));
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_MISMATCH", body);
        body = BodyCorrelationOracle.body();
        child(body, "work").remove("cleanupNanos");
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_MISSING", body);
        body = BodyCorrelationOracle.body();
        child(body, "cleanup").remove("cleanupNanos");
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_MISSING", body);
        body = BodyCorrelationOracle.body("0", Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_STRING", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(0), "0");
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_STRING", body);
        body = BodyCorrelationOracle.body(null, Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_NULL", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(0), null);
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_NULL", body);
        body = BodyCorrelationOracle.body(Short.valueOf((short) 0), Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_SHORT", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(0), Short.valueOf((short) 0));
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_SHORT", body);
        body = BodyCorrelationOracle.body(Double.valueOf(0.0), Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_DOUBLE", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(0), Double.valueOf(0.0));
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_DOUBLE", body);
        body = BodyCorrelationOracle.body(Boolean.FALSE, Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_BOOLEAN", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(0), Boolean.FALSE);
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_BOOLEAN", body);
        body = BodyCorrelationOracle.body(Integer.valueOf(-1), Integer.valueOf(0));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_NEGATIVE", body);
        body = BodyCorrelationOracle.body(Long.valueOf(0L), Long.valueOf(-1L));
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_NEGATIVE", body);
        body = BodyCorrelationOracle.body(
                Long.valueOf(BodyCorrelationOracle.MAX_CLEANUP_NANOS + 1L),
                Long.valueOf(0L));
        expectFailure("BODY_CLEANUP_NANOS", "WORK_NANOS_OVER_MAX", body);
        body = BodyCorrelationOracle.body(
                Long.valueOf(0L),
                Long.valueOf(BodyCorrelationOracle.MAX_CLEANUP_NANOS + 1L));
        expectFailure("BODY_CLEANUP_NANOS", "CLEANUP_NANOS_OVER_MAX", body);

        body = BodyCorrelationOracle.body();
        body.remove("arms");
        expectFailure("BODY_ROOT_ORDER", "ARMS_ROOT_MISSING_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("arms", null);
        expectFailure("BODY_ROOT_CONTAINER", "ARMS_ROOT_NULL_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("arms", new ArrayList<Object>());
        expectFailure("BODY_ROOT_CONTAINER", "ARMS_ROOT_LIST_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.remove("work");
        expectFailure("BODY_ROOT_ORDER", "WORK_ROOT_MISSING_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("work", null);
        expectFailure("BODY_ROOT_CONTAINER", "WORK_ROOT_NULL_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("work", new ArrayList<Object>());
        expectFailure("BODY_ROOT_CONTAINER", "WORK_ROOT_LIST_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.remove("cleanup");
        expectFailure("BODY_ROOT_ORDER", "CLEANUP_ROOT_MISSING_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("cleanup", null);
        expectFailure("BODY_ROOT_CONTAINER", "CLEANUP_ROOT_NULL_HEADER_FIRST", body);
        body = BodyCorrelationOracle.body();
        body.put("cleanup", new ArrayList<Object>());
        expectFailure("BODY_ROOT_CONTAINER", "CLEANUP_ROOT_LIST_HEADER_FIRST", body);
    }

    private static void expectFailure(String expectedCode, String label,
            Map<String, Object> body) throws Exception {
        ReferenceSnapshot before = new ReferenceSnapshot(body);
        MemorySink sink = new MemorySink();
        CurrentV2CanonicalCodec.CodecFailure failure = null;
        try {
            CurrentV2CanonicalCodec.writeCorrelatedBody(body, sink);
        } catch (CurrentV2CanonicalCodec.CodecFailure rejected) {
            failure = rejected;
        }
        check(failure != null && expectedCode.equals(failure.code), label + "_EXACT_FAILURE");
        check(sink.bytes().length == 0, label + "_ZERO_EMITTED_BYTES");
        check(before.unchanged(body), label + "_BODY_UNCHANGED");
        cases++;
    }

    private static void testFreshSinkAdmissionAndZeroByteDenial() throws Exception {
        PermitState directState = new PermitState();
        Map<String, Object> directGuard = guardedTree(
                BodyCorrelationOracle.body(), directState);
        boolean blocked = false;
        try {
            directGuard.get("inputContextSha256");
        } catch (IllegalStateException expected) {
            blocked = true;
        }
        check(blocked && directState.rejectedGets == 1,
                "MAP_GET_REQUIRES_FRESH_SINK_PERMIT");
        cases++;

        PermitState state = new PermitState();
        Map<String, Object> guarded = guardedTree(BodyCorrelationOracle.body(), state);
        ReferenceSnapshot before = new ReferenceSnapshot(guarded);
        PermitMemorySink admittedSink = new PermitMemorySink(state, false);
        CurrentV2CanonicalCodec.writeCorrelatedBody(guarded, admittedSink);
        check(Arrays.equals(admittedSink.bytes(),
                asciiBytes(BodyCorrelationOracle.MINIMAL_BODY_JSON)),
                "ADMITTED_LOOKUPS_PRESERVE_LITERAL_BYTES");
        check(oneFinalLf(admittedSink.bytes()), "ADMITTED_LOOKUPS_ONE_FINAL_LF");
        check(state.successfulGets == 59, "ALL_FIFTY_NINE_RELATION_GETS_ADMITTED");
        check(state.rejectedGets == 0, "NO_UNADMITTED_RELATION_GETS");
        check(!state.permitOutstanding, "NO_STALE_LOOKUP_PERMIT");
        check(before.unchanged(guarded), "GUARDED_BODY_UNCHANGED");
        cases++;

        PermitState deniedState = new PermitState();
        Map<String, Object> deniedBody = guardedTree(
                BodyCorrelationOracle.body(), deniedState);
        ReferenceSnapshot deniedBefore = new ReferenceSnapshot(deniedBody);
        PermitMemorySink deniedSink = new PermitMemorySink(deniedState, true);
        boolean denied = false;
        try {
            CurrentV2CanonicalCodec.writeCorrelatedBody(deniedBody, deniedSink);
        } catch (AdmissionDenied expected) {
            denied = true;
        }
        check(denied, "DENY_HOOK_FIRES_BEFORE_GET");
        check(deniedState.successfulGets == 0, "DENIED_PATH_HAS_NO_MAP_GET");
        check(deniedSink.bytes().length == 0, "DENIED_PATH_ZERO_EMITTED_BYTES");
        check(deniedBefore.unchanged(deniedBody), "DENIED_BODY_UNCHANGED");
        cases++;
    }

    private static void testWideTwoBlockMeterEquivalence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.wideBody();
        ReferenceSnapshot before = new ReferenceSnapshot(body);
        byte[] expected = asciiBytes(BodyCorrelationOracle.wideJson());
        MemorySink transport = new MemorySink();
        MemorySink correlated = new MemorySink();

        CurrentV2CanonicalCodec.writeBody(body, transport);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(expected.length > 4096 && expected.length <= 8192,
                "WIDE_ORACLE_IS_TWO_BLOCKS");
        check(Arrays.equals(transport.bytes(), expected), "WIDE_OLD_INDEPENDENT_BYTES");
        check(Arrays.equals(correlated.bytes(), expected), "WIDE_CORRELATED_INDEPENDENT_BYTES");
        check(Arrays.equals(transport.bytes(), correlated.bytes()), "WIDE_OLD_NEW_EQUAL");
        check(oneFinalLf(correlated.bytes()), "WIDE_ONE_FINAL_LF");
        check(before.unchanged(body), "WIDE_BODY_UNCHANGED_AFTER_MEMORY_PASSES");

        CountMetrics oldMetrics = countWithRealR5Sink(body, expected.length, false);
        CountMetrics newMetrics = countWithRealR5Sink(body, expected.length, true);
        check(oldMetrics.bytes == expected.length && newMetrics.bytes == expected.length,
                "REAL_R5_COUNT_BYTES_MATCH_ORACLE");
        check(oldMetrics.units == newMetrics.units, "REAL_R5_BLOCK_FEES_MATCH_OLD_PATH");
        check(oldMetrics.copiedEntries == newMetrics.copiedEntries,
                "REAL_R5_COPIES_MATCH_OLD_PATH");
        check(oldMetrics.copyChunks == newMetrics.copyChunks,
                "REAL_R5_CHUNKS_MATCH_OLD_PATH");
        check(oldMetrics.groups == newMetrics.groups, "REAL_R5_GROUPS_MATCH_OLD_PATH");
        check(newMetrics.units == 2L, "REAL_R5_EXACT_TWO_BLOCK_FEES");
        check(newMetrics.copiedEntries == 0L && newMetrics.copyChunks == 0L,
                "REAL_R5_COUNT_PATH_ADDS_NO_COPIES_OR_CHUNKS");
        check(before.unchanged(body), "WIDE_BODY_UNCHANGED_AFTER_R5_PASSES");
        cases++;
    }

    private static CountMetrics countWithRealR5Sink(Map<String, Object> body,
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

        if (correlated)
            CurrentV2CanonicalCodec.writeCorrelatedBody(body, sink);
        else
            CurrentV2CanonicalCodec.writeBody(body, sink);
        sink.complete((long) expectedBytes);
        fixture.setup.finish();

        return new CountMetrics(
                number(fixture.setup.meter, "units") - beforeUnits,
                number(fixture.setup.meter, "copiedEntries") - beforeCopies,
                number(fixture.setup.meter, "copyChunks") - beforeChunks,
                number(fixture.setup.meter, "groups") - beforeGroups,
                sink.position);
    }

    private static boolean hasRootOrder(Map<String, Object> body) {
        Iterator<String> keys = body.keySet().iterator();
        for (int index = 0; index < ROOT_KEYS.length; index++)
            if (!keys.hasNext() || !ROOT_KEYS[index].equals(keys.next())) return false;
        return !keys.hasNext();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    private static byte[] asciiBytes(String text) {
        for (int index = 0; index < text.length(); index++)
            if (text.charAt(index) > 127) throw new AssertionError("ORACLE_NON_ASCII");
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean oneFinalLf(byte[] bytes) {
        if (bytes.length == 0 || bytes[bytes.length - 1] != (byte) 10) return false;
        int lineFeeds = 0;
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 255;
            if (value == 10) {
                lineFeeds++;
                if (index != bytes.length - 1) return false;
            }
            if (value > 127) return false;
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

    private static final class MemorySink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        public void beforeDataOperation() { }

        public void writeByte(int ascii) throws Exception {
            if (ascii < 0 || ascii > 127) throw new IOException("SYNTHETIC_SINK_ASCII");
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
                        /* Revoke the header's last unused permit before any lookup. */
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

    private static final class PermitMemorySink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final PermitState state;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean denyNext;

        PermitMemorySink(PermitState state, boolean denyFirstAdmission) {
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

    private static final class ReferenceSnapshot {
        private final IdentityHashMap<Object, List<Object[]>> contents =
                new IdentityHashMap<Object, List<Object[]>>();

        ReferenceSnapshot(Object root) { capture(root); }

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
