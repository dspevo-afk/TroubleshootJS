package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Synthetic-only canary for the nested cleanup schema discriminator. */
public final class CleanupSchemaCanary {
    private static final String CLEANUP_SCHEMA =
            "tsj-cohort-terminal-cleanup-v1";

    private static int assertions;
    private static int cases;

    private CleanupSchemaCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testFreshEqualTag();
        testInvalidTags();
        testEarlierFailurePrecedence();

        System.out.println("SYNTHETIC_CLEANUP_SCHEMA_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testFreshEqualTag() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        String supplied = new String(CLEANUP_SCHEMA);
        setCleanupSchema(body, supplied);
        Object observed = cleanup(body).get("schema");
        check(observed != CLEANUP_SCHEMA && CLEANUP_SCHEMA.equals(observed),
                "NEW_STRING_WITH_EQUAL_SCHEMA_CONTENT");

        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink lower = new MemorySink();
        MemorySink correlated = new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body, lower);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(lower.bytes(), correlated.bytes()),
                "FRESH_EQUAL_SCHEMA_ACCEPTED_WITH_LOWER_BODY_BYTES");
        check(before.unchanged(body), "FRESH_EQUAL_SCHEMA_TREE_UNCHANGED");
        cases++;
    }

    private static void testInvalidTags() throws Exception {
        for (int kind = 0; kind < 9; kind++) {
            Map<String, Object> body = BodyCorrelationOracle.body();
            if (kind == 0) removeCleanupSchema(body);
            else if (kind == 1) setCleanupSchema(body, null);
            else if (kind == 2) setCleanupSchema(body, Integer.valueOf(7));
            else if (kind == 3) setCleanupSchema(body, Boolean.TRUE);
            else if (kind == 4) setCleanupSchema(body, "TSJ-Cohort-terminal-cleanup-v1");
            else if (kind == 5) setCleanupSchema(body, "tsj-cohort-terminal-cleanup-v0");
            else if (kind == 6) setCleanupSchema(body, "other-cleanup-schema");
            else if (kind == 7) setCleanupSchema(body, "");
            else setCleanupSchema(body, repeat('x', 257));
            expectFailure("BODY_CLEANUP_SCHEMA", "CLEANUP_SCHEMA_KIND_" + kind,
                    body);
        }
    }

    private static void testEarlierFailurePrecedence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        body.put("schema", "wrong-root-schema");
        expectFailure("BODY_SCHEMA", "HEADER_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        body.put("inputContextSha256", BodyCorrelationOracle.OTHER_CONTEXT_SHA);
        expectFailure("BODY_PRIOR_CONTEXT", "CONTEXT_PRECEDES_CLEANUP_SCHEMA",
                body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        child(child(body, "arms"), "null").put("armRole", "OBSERVE");
        expectFailure("BODY_NULL_ROLE", "ROLE_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("cleanupNanos", Integer.valueOf(1));
        expectFailure("BODY_CLEANUP_NANOS", "DURATION_PRECEDES_CLEANUP_SCHEMA",
                body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("originalCaptureContextRestored", Boolean.FALSE);
        expectFailure("BODY_RESTORATION_CAPTURE",
                "RESTORATION_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("originalCaptureOwnersReleased", Boolean.FALSE);
        expectFailure("BODY_OWNERSHIP_CAPTURE",
                "OWNERSHIP_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("sourceGeometryUnchanged", Boolean.FALSE);
        expectFailure("BODY_GEOMETRY_SUMMARY",
                "GEOMETRY_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        template(body).put("effectsAfterSha256", Integer.valueOf(7));
        expectFailure("BODY_EFFECTS_STRING",
                "EFFECTS_STRING_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("originalCaptureCopyEffectsUnchanged", "wrong-type");
        expectFailure("BODY_EFFECTS_BOOLEAN",
                "EFFECTS_BOOLEAN_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        cleanup(body).put("effectsScope", "wrong-scope");
        expectFailure("BODY_EFFECTS_SCOPE",
                "EFFECTS_SCOPE_PRECEDES_CLEANUP_SCHEMA", body);

        body = BodyCorrelationOracle.body();
        invalidateCleanupSchema(body);
        template(body).put("effectsBeforeSha256", "6666666666666666666666666666666666666666666666666666666666666666");
        template(body).put("effectsAfterSha256", "7777777777777777777777777777777777777777777777777777777777777777");
        expectFailure("BODY_EFFECTS_SUMMARY",
                "EFFECTS_SUMMARY_PRECEDES_CLEANUP_SCHEMA", body);
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

    private static void setCleanupSchema(Map<String, Object> body, Object value) {
        cleanup(body).put("schema", value);
    }

    private static void removeCleanupSchema(Map<String, Object> body) {
        cleanup(body).remove("schema");
    }

    private static void invalidateCleanupSchema(Map<String, Object> body) {
        setCleanupSchema(body, Integer.valueOf(7));
    }

    private static Map<String, Object> cleanup(Map<String, Object> body) {
        return child(body, "cleanup");
    }

    private static Map<String, Object> template(Map<String, Object> body) {
        return child(child(body, "originalCapture"), "template");
    }

    private static String repeat(char value, int length) {
        char[] chars = new char[length];
        for (int index = 0; index < chars.length; index++) chars[index] = value;
        return new String(chars);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
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
