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

/** Synthetic-only canary for the ordered two-arm projection preflight. */
public final class ArmProjectionCanary {
    private static int assertions;
    private static int cases;

    private ArmProjectionCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testPositiveShapes();
        testOuterArmMap();
        testInnerArmKeys();
        testOldRoleAndCleanupPrecedence();
        testArmIdDomain();
        testNestedMapTypes();
        testEarlierPrecedence();

        System.out.println("SYNTHETIC_ARM_PROJECTION_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testPositiveShapes() throws Exception {
        accept(validBody(), "EMPTY_IDS_AND_NESTED_MAPS");

        Map<String, Object> body = validBody();
        String nullId = new String("same-synthetic-id");
        String observeId = new String("same-synthetic-id");
        setArmField(body, "null", "armId", nullId);
        setArmField(body, "observe", "armId", observeId);
        check(nullId != observeId && nullId.equals(observeId),
                "EQUAL_CONTENT_IDS_ARE_DISTINCT_REFERENCES");
        accept(body, "EQUAL_ID_CONTENT_SUPPORTED");

        body = validBody();
        String boundary = repeat('x', 256);
        setArmField(body, "null", "armId", new String(boundary));
        setArmField(body, "observe", "armId", new String(boundary));
        accept(body, "ID_LENGTH_256_SUPPORTED");
    }

    private static void testOuterArmMap() throws Exception {
        Map<String, Object> body = validBody();
        arms(body).remove("null");
        expectFailure("BODY_CORRELATION_MAP", "OUTER_NULL_ARM_MISSING", body);

        body = validBody();
        arms(body).put("null", null);
        expectFailure("BODY_CORRELATION_MAP", "OUTER_NULL_ARM_NULL", body);

        body = validBody();
        arms(body).remove("observe");
        expectFailure("BODY_CORRELATION_MAP", "OUTER_OBSERVE_ARM_MISSING", body);

        body = validBody();
        arms(body).put("observe", null);
        expectFailure("BODY_CORRELATION_MAP", "OUTER_OBSERVE_ARM_NULL", body);

        body = validBody();
        arms(body).put("synthetic-extra", new LinkedHashMap<String, Object>());
        expectFailure("BODY_ARM_PROJECTION_ORDER", "OUTER_EXTRA_KEY", body);

        body = validBody();
        Map<Object, Object> original = arms(body);
        Map<Object, Object> reversed = new LinkedHashMap<Object, Object>();
        reversed.put("observe", original.get("observe"));
        reversed.put("null", original.get("null"));
        body.put("arms", reversed);
        expectFailure("BODY_ARM_PROJECTION_ORDER", "OUTER_REVERSED_KEYS", body);

        body = validBody();
        original = arms(body);
        Map<Object, Object> nonString = new LinkedHashMap<Object, Object>();
        nonString.put(Integer.valueOf(7), "synthetic-extra");
        nonString.putAll(original);
        body.put("arms", nonString);
        expectFailure("BODY_ARM_PROJECTION_ORDER", "OUTER_NONSTRING_KEY", body);
    }

    private static void testInnerArmKeys() throws Exception {
        String[] arms = {"null", "observe"};
        String[] newFields = {"armId", "freshRun", "observation"};
        for (String name : arms) {
            for (String field : newFields) {
                Map<String, Object> body = validBody();
                removeArmField(body, name, field);
                expectFailure("BODY_ARM_PROJECTION_ORDER",
                        name.toUpperCase() + "_MISSING_" + field.toUpperCase(), body);
            }

            Map<String, Object> body = validBody();
            Map<Object, Object> extra = copyArm(body, name);
            extra.put("synthetic-extra", "unused");
            replaceArm(body, name, extra);
            expectFailure("BODY_ARM_PROJECTION_ORDER",
                    name.toUpperCase() + "_EXTRA_KEY", body);

            body = validBody();
            Map<Object, Object> original = copyArm(body, name);
            Map<Object, Object> reordered = new LinkedHashMap<Object, Object>();
            reordered.put("armRole", original.get("armRole"));
            reordered.put("freshRun", original.get("freshRun"));
            reordered.put("armId", original.get("armId"));
            reordered.put("observation", original.get("observation"));
            reordered.put("cleanup", original.get("cleanup"));
            replaceArm(body, name, reordered);
            expectFailure("BODY_ARM_PROJECTION_ORDER",
                    name.toUpperCase() + "_REORDERED_KEYS", body);

            body = validBody();
            original = copyArm(body, name);
            Map<Object, Object> nonString = new LinkedHashMap<Object, Object>();
            nonString.put(Integer.valueOf(7), "synthetic-extra");
            nonString.putAll(original);
            replaceArm(body, name, nonString);
            expectFailure("BODY_ARM_PROJECTION_ORDER",
                    name.toUpperCase() + "_NONSTRING_KEY", body);
        }
    }

    private static void testOldRoleAndCleanupPrecedence() throws Exception {
        Map<String, Object> body = validBody();
        removeArmField(body, "null", "armRole");
        removeArmField(body, "null", "armId");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_PRECEDES_NEW_KEY_ORDER", body);

        body = validBody();
        removeArmField(body, "observe", "armRole");
        removeArmField(body, "observe", "armId");
        expectFailure("BODY_OBSERVE_ROLE",
                "OBSERVE_ROLE_PRECEDES_NEW_KEY_ORDER", body);

        body = validBody();
        removeArmField(body, "null", "cleanup");
        removeArmField(body, "null", "armId");
        expectFailure("BODY_CORRELATION_MAP",
                "NULL_CLEANUP_MAP_PRECEDES_NEW_KEY_ORDER", body);

        body = validBody();
        removeArmField(body, "observe", "cleanup");
        removeArmField(body, "observe", "armId");
        expectFailure("BODY_CORRELATION_MAP",
                "OBSERVE_CLEANUP_MAP_PRECEDES_NEW_KEY_ORDER", body);
    }

    private static void testArmIdDomain() throws Exception {
        String[] names = {"null", "observe"};
        for (String name : names) {
            Map<String, Object> body = validBody();
            setArmField(body, name, "armId", null);
            expectFailure("BODY_ARM_PROJECTION_ID", name.toUpperCase() + "_ID_NULL", body);

            body = validBody();
            setArmField(body, name, "armId", Integer.valueOf(7));
            expectFailure("BODY_ARM_PROJECTION_ID",
                    name.toUpperCase() + "_ID_WRONG_TYPE", body);

            body = validBody();
            setArmField(body, name, "armId", repeat('x', 257));
            expectFailure("BODY_ARM_PROJECTION_ID",
                    name.toUpperCase() + "_ID_LENGTH_257", body);
        }
    }

    private static void testNestedMapTypes() throws Exception {
        String[] names = {"null", "observe"};
        String[] fields = {"freshRun", "observation"};
        for (String name : names) {
            for (String field : fields) {
                Map<String, Object> body = validBody();
                setArmField(body, name, field, null);
                expectFailure("BODY_CORRELATION_MAP",
                        name.toUpperCase() + "_" + field.toUpperCase() + "_NULL", body);

                body = validBody();
                setArmField(body, name, field, "not-a-map");
                expectFailure("BODY_CORRELATION_MAP",
                        name.toUpperCase() + "_" + field.toUpperCase() + "_WRONG_TYPE",
                        body);
            }
        }
    }

    private static void testEarlierPrecedence() throws Exception {
        Map<String, Object> body = validBody();
        cohort(body).remove("netIds");
        removeArmField(body, "null", "armId");
        expectFailure("BODY_COHORT_ORDER", "COHORT_PRECEDES_ARM_PROJECTION", body);

        body = validBody();
        setArmField(body, "null", "armRole", "OBSERVE");
        removeArmField(body, "null", "armId");
        expectFailure("BODY_NULL_ROLE", "NULL_ROLE_VALUE_PRECEDES_ARM_PROJECTION", body);

        body = validBody();
        setArmField(body, "observe", "armRole", "NULL");
        removeArmField(body, "observe", "armId");
        expectFailure("BODY_OBSERVE_ROLE",
                "OBSERVE_ROLE_VALUE_PRECEDES_ARM_PROJECTION", body);

        body = validBody();
        setArmField(body, "null", "cleanup", null);
        removeArmField(body, "null", "armId");
        expectFailure("BODY_CORRELATION_MAP",
                "CLEANUP_GUARD_PRECEDES_ARM_PROJECTION", body);
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

    private static Map<String, Object> validBody() {
        Map<String, Object> body = BodyCorrelationOracle.body();
        Map<String, Object> emptyRun = new LinkedHashMap<String, Object>();
        Map<String, Object> emptyObservation = new LinkedHashMap<String, Object>();
        setArmProjection(body, "null", "", emptyRun, emptyObservation);
        setArmProjection(body, "observe", "", new LinkedHashMap<String, Object>(),
                new LinkedHashMap<String, Object>());
        return body;
    }

    private static void setArmProjection(Map<String, Object> body, String name,
            Object id, Object freshRun, Object observation) {
        Map<String, Object> old = arm(body, name);
        Map<Object, Object> replacement = new LinkedHashMap<Object, Object>();
        replacement.put("armRole", old.get("armRole"));
        replacement.put("armId", id);
        replacement.put("freshRun", freshRun);
        replacement.put("observation", observation);
        replacement.put("cleanup", old.get("cleanup"));
        replaceArm(body, name, replacement);
    }

    private static void setArmField(Map<String, Object> body, String name,
            String key, Object value) {
        Map<Object, Object> replacement = copyArm(body, name);
        replacement.put(key, value);
        replaceArm(body, name, replacement);
    }

    private static void removeArmField(Map<String, Object> body, String name,
            String key) {
        Map<Object, Object> replacement = copyArm(body, name);
        replacement.remove(key);
        replaceArm(body, name, replacement);
    }

    private static Map<Object, Object> copyArm(Map<String, Object> body,
            String name) {
        Map<Object, Object> copy = new LinkedHashMap<Object, Object>();
        copy.putAll(arm(body, name));
        return copy;
    }

    private static void replaceArm(Map<String, Object> body, String name,
            Map<?, ?> replacement) {
        arms(body).put(name, replacement);
    }

    private static Map<Object, Object> arms(Map<String, Object> body) {
        return castMap(child(body, "arms"));
    }

    private static Map<String, Object> arm(Map<String, Object> body, String name) {
        return child(arms(body), name);
    }

    private static Map<String, Object> cohort(Map<String, Object> body) {
        return child(body, "cohort");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<?, ?> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(Object value) {
        return (Map<Object, Object>) value;
    }
}
