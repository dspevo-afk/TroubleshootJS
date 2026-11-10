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

/** Synthetic-only canary for the qualified-prior wrapper envelope. */
public final class PriorEnvelopeCanary {
    private static final String[] PRIOR_KEYS = {
        "schema", "inputContextSha256", "sourceParent", "manifestSha256",
        "sourceManifestSha256", "readerSha256", "resultSha256", "operation1Sha256",
        "parentReceiptSha256", "priorRunId", "priorOriginalReceipt", "trialDisposition"
    };
    private static final String[] MISSING_CODES = {
        "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_CONTEXT", "BODY_PRIOR_ENVELOPE_ORDER",
        "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_ENVELOPE_ORDER",
        "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_ENVELOPE_ORDER",
        "BODY_PRIOR_ENVELOPE_ORDER", "BODY_PRIOR_ENVELOPE_ORDER", "BODY_CORRELATION_MAP"
    };
    private static final String[] UNUSED_KEYS = {
        "sourceParent", "manifestSha256", "sourceManifestSha256", "readerSha256",
        "resultSha256", "operation1Sha256", "parentReceiptSha256", "priorRunId",
        "priorOriginalReceipt"
    };

    private static int assertions;
    private static int cases;

    private PriorEnvelopeCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testValidEnvelope();
        testSchemaValuesAndPrecedence();
        testMissingKeysKeepEarlierErrors();
        testEnvelopeShape();
        testPriorChecksPrecedeEnvelope();

        System.out.println("SYNTHETIC_PRIOR_ENVELOPE_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testValidEnvelope() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        Map<String, Object> prior = qualifiedPrior(body);
        String suppliedSchema = new String(CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA);
        prior.put("schema", suppliedSchema);
        check(suppliedSchema != CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA &&
                suppliedSchema.equals(CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA),
                "EQUAL_CONTENT_SCHEMA_IS_DISTINCT_STRING");
        check(hasExactPriorOrder(prior), "TWELVE_KEYS_IN_PRODUCER_ORDER");
        check(unusedValuesAreNull(prior), "NINE_UNUSED_VALUES_MAY_BE_NULL");
        accept(body, "VALID_ENVELOPE_AND_NULL_UNUSED_FIELDS");
    }

    private static void testSchemaValuesAndPrecedence() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("schema", null);
        expectFailure("BODY_PRIOR_ENVELOPE_SCHEMA", "SCHEMA_NULL", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("schema", Integer.valueOf(7));
        expectFailure("BODY_PRIOR_ENVELOPE_SCHEMA", "SCHEMA_WRONG_TYPE", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("schema", "wrong-qualified-prior-schema");
        expectFailure("BODY_PRIOR_ENVELOPE_SCHEMA", "SCHEMA_WRONG_CONTENT", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("schema", "wrong-qualified-prior-schema");
        swapAdjacentPriorKeys(body, 2);
        expectFailure("BODY_PRIOR_ENVELOPE_SCHEMA",
                "BAD_SCHEMA_PRECEDES_LATER_KEY_ORDER", body);
    }

    private static void testMissingKeysKeepEarlierErrors() throws Exception {
        for (int index = 0; index < PRIOR_KEYS.length; index++) {
            Map<String, Object> body = BodyCorrelationOracle.body();
            qualifiedPrior(body).remove(PRIOR_KEYS[index]);
            expectFailure(MISSING_CODES[index],
                    "MISSING_" + PRIOR_KEYS[index].toUpperCase(), body);
        }
    }

    private static void testEnvelopeShape() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        expectFailure("BODY_PRIOR_ENVELOPE_ORDER", "EXTRA_PRIOR_KEY", body);

        body = BodyCorrelationOracle.body();
        Map<Object, Object> nonString = new LinkedHashMap<Object, Object>();
        nonString.put(Integer.valueOf(7), "synthetic-extra");
        nonString.putAll(qualifiedPrior(body));
        body.put("qualifiedPrior", nonString);
        expectFailure("BODY_PRIOR_ENVELOPE_ORDER", "NONSTRING_PRIOR_KEY", body);

        for (int index = 0; index < PRIOR_KEYS.length - 1; index++) {
            body = BodyCorrelationOracle.body();
            swapAdjacentPriorKeys(body, index);
            expectFailure("BODY_PRIOR_ENVELOPE_ORDER",
                    "ADJACENT_KEY_SWAP_" + index, body);
        }
    }

    private static void testPriorChecksPrecedeEnvelope() throws Exception {
        Map<String, Object> body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        body.put("inputContextSha256", BodyCorrelationOracle.OTHER_CONTEXT_SHA);
        expectFailure("BODY_PRIOR_CONTEXT", "CONTEXT_PRECEDES_ENVELOPE", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        qualifiedPrior(body).remove("trialDisposition");
        expectFailure("BODY_CORRELATION_MAP", "TRIAL_MAP_PRECEDES_ENVELOPE", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        child(body, "cohort").remove("netIds");
        expectFailure("BODY_COHORT_ORDER", "COHORT_PRECEDES_ENVELOPE", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        child(child(body, "arms"), "null").put("projectionExtra", "unused");
        expectFailure("BODY_ARM_PROJECTION_ORDER", "ARM_SHAPE_PRECEDES_ENVELOPE", body);

        body = BodyCorrelationOracle.body();
        qualifiedPrior(body).put("synthetic-extra", "unused");
        child(body, "cleanup").put("synthetic-extra", "unused");
        expectFailure("BODY_CLEANUP_PROJECTION_ORDER",
                "CLEANUP_SHAPE_PRECEDES_ENVELOPE", body);
    }

    private static void swapAdjacentPriorKeys(Map<String, Object> body,
            int leftIndex) {
        Map<String, Object> original = qualifiedPrior(body);
        Object[] keys = original.keySet().toArray();
        Map<Object, Object> swapped = new LinkedHashMap<Object, Object>();
        for (int index = 0; index < keys.length; index++) {
            int source = index;
            if (index == leftIndex) source = leftIndex + 1;
            else if (index == leftIndex + 1) source = leftIndex;
            swapped.put(keys[source], original.get(keys[source]));
        }
        body.put("qualifiedPrior", swapped);
    }

    private static boolean hasExactPriorOrder(Map<String, Object> prior) {
        if (prior.size() != PRIOR_KEYS.length) return false;
        Iterator<String> keys = prior.keySet().iterator();
        for (String expected : PRIOR_KEYS) {
            if (!keys.hasNext() || !expected.equals(keys.next())) return false;
        }
        return !keys.hasNext();
    }

    private static boolean unusedValuesAreNull(Map<String, Object> prior) {
        for (String key : UNUSED_KEYS) {
            if (prior.get(key) != null) return false;
        }
        return true;
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

    private static Map<String, Object> qualifiedPrior(Map<String, Object> body) {
        return child(body, "qualifiedPrior");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<?, ?> values, String key) {
        return (Map<String, Object>) values.get(key);
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
