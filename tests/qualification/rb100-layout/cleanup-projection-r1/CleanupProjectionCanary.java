package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic-only canary for the ordered cleanup projection map. */
public final class CleanupProjectionCanary {
    private static final String[] CLEANUP_KEYS = {
        "schema", "originalCaptureOwnersReleased", "originalCaptureContextRestored",
        "nullArmAliasesDropped", "observeArmAliasesDropped", "sourceGeometryUnchanged",
        "effectsScope", "originalCaptureCopyEffectsUnchanged", "contextRestored",
        "cleanupNanos"
    };
    private static final String[] MISSING_CODES = {
        "BODY_CLEANUP_SCHEMA", "BODY_OWNERSHIP_BOOLEAN", "BODY_RESTORATION_BOOLEAN",
        "BODY_OWNERSHIP_BOOLEAN", "BODY_OWNERSHIP_BOOLEAN", "BODY_GEOMETRY_BOOLEAN",
        "BODY_EFFECTS_SCOPE", "BODY_EFFECTS_BOOLEAN", "BODY_RESTORATION_BOOLEAN",
        "BODY_CLEANUP_NANOS"
    };

    private static int assertions;
    private static int cases;

    private CleanupProjectionCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testValidProducerOrder();
        testEveryMissingKeyKeepsOldError();
        testLateShapeFailures();
        testEarlierPrecedence();

        System.out.println("SYNTHETIC_CLEANUP_PROJECTION_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testValidProducerOrder() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        check(hasProducerOrder(cleanup(body)), "PRODUCER_ORDER_TEN_KEYS");
        accept(body, "ORDERED_CLEANUP_MAP_ACCEPTED");
    }

    private static void testEveryMissingKeyKeepsOldError() throws Exception {
        for (int index = 0; index < CLEANUP_KEYS.length; index++) {
            Map<String, Object> body = BodyCorrelationOracle.body();
            cleanup(body).remove(CLEANUP_KEYS[index]);
            expectFailure(MISSING_CODES[index],
                    "MISSING_" + CLEANUP_KEYS[index].toUpperCase(), body);
        }
    }

    private static void testLateShapeFailures() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        expectFailure("BODY_CLEANUP_PROJECTION_ORDER", "EXTRA_CLEANUP_KEY", body);

        body = BodyCorrelationOracle.body();
        Map<Object, Object> nonString = new LinkedHashMap<Object, Object>();
        nonString.put(Integer.valueOf(7), "synthetic-extra");
        nonString.putAll(cleanup(body));
        body.put("cleanup", nonString);
        expectFailure("BODY_CLEANUP_PROJECTION_ORDER", "NONSTRING_CLEANUP_KEY", body);

        for (int index = 0; index < CLEANUP_KEYS.length - 1; index++) {
            body = BodyCorrelationOracle.body();
            swapAdjacentCleanupKeys(body, index);
            expectFailure("BODY_CLEANUP_PROJECTION_ORDER",
                    "ADJACENT_KEY_SWAP_" + index, body);
        }
    }

    private static void testEarlierPrecedence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        child(body, "cohort").remove("netIds");
        expectFailure("BODY_COHORT_ORDER", "COHORT_PRECEDES_CLEANUP_SHAPE", body);

        body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        child(child(body, "arms"), "null").put("projectionExtra", "unused");
        expectFailure("BODY_ARM_PROJECTION_ORDER",
                "ARM_SHAPE_PRECEDES_CLEANUP_SHAPE", body);

        body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        child(child(body, "arms"), "null").put("armRole", "OBSERVE");
        expectFailure("BODY_NULL_ROLE", "ROLE_PRECEDES_CLEANUP_SHAPE", body);

        body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        cleanup(body).put("effectsScope", "wrong-scope");
        expectFailure("BODY_EFFECTS_SCOPE",
                "EFFECTS_SCOPE_VALUE_PRECEDES_CLEANUP_SHAPE", body);

        body = BodyCorrelationOracle.body();
        cleanup(body).put("synthetic-extra", "unused");
        cleanup(body).put("schema", "wrong-schema");
        expectFailure("BODY_CLEANUP_SCHEMA",
                "CLEANUP_SCHEMA_VALUE_PRECEDES_CLEANUP_SHAPE", body);
    }

    private static void swapAdjacentCleanupKeys(Map<String, Object> body,
            int leftIndex) {
        Map<String, Object> original = cleanup(body);
        Object[] keys = original.keySet().toArray();
        Map<Object, Object> swapped = new LinkedHashMap<Object, Object>();
        for (int index = 0; index < keys.length; index++) {
            int source = index;
            if (index == leftIndex) source = leftIndex + 1;
            else if (index == leftIndex + 1) source = leftIndex;
            swapped.put(keys[source], original.get(keys[source]));
        }
        body.put("cleanup", swapped);
    }

    private static boolean hasProducerOrder(Map<String, Object> cleanup) {
        if (cleanup.size() != CLEANUP_KEYS.length) return false;
        Iterator<String> keys = cleanup.keySet().iterator();
        for (String expected : CLEANUP_KEYS) {
            if (!keys.hasNext() || !expected.equals(keys.next())) return false;
        }
        return !keys.hasNext();
    }

    private static void accept(Map<String, Object> body, String label)
            throws Exception {
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink lower = new MemorySink();
        MemorySink correlated = new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body, lower);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(lower.bytes(), correlated.bytes()),
                label + "_MATCHES_LOWER_WRITE_BODY");
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

    private static Map<String, Object> cleanup(Map<String, Object> body) {
        return child(body, "cleanup");
    }

    private static Map<String, Object> child(Map<?, ?> values, String key) {
        return castMap(values.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void check(boolean condition, String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
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
}
