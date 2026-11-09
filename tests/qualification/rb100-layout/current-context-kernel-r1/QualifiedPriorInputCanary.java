package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Qualified prior generated-DATA factory only. No electrical graph, owner or Session. */
public final class QualifiedPriorInputCanary {
    private static int assertions;

    private static void need(boolean value, String code) {
        assertions++;
        if (!value) throw new AssertionError(code);
    }

    private static int count(Object value, IdentityHashMap<Object,Boolean> seen) {
        if (value instanceof Map) {
            need(seen.put(value, Boolean.TRUE) == null, "MAP_ALIAS");
            Map<?,?> map = (Map<?,?>) value;
            int result = 1 + map.size();
            for (Map.Entry<?,?> row : map.entrySet()) {
                need(row.getKey() instanceof String, "MAP_KEY");
                result += count(row.getValue(), seen);
            }
            return result;
        }
        if (value instanceof List) {
            need(seen.put(value, Boolean.TRUE) == null, "LIST_ALIAS");
            List<?> list = (List<?>) value;
            int result = 1 + list.size();
            for (Object child : list) result += count(child, seen);
            return result;
        }
        need(value == null || value instanceof String || value instanceof Integer
            || value instanceof Boolean, "SCALAR_TYPE");
        return 0;
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            need(c >= 32 && c <= 126, "PRINTABLE_ASCII");
            if (c == '\\' || c == '"') out.append('\\');
            out.append(c);
        }
        out.append('"');
    }

    private static void json(Object value, StringBuilder out) {
        if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?,?> row : ((Map<?,?>) value).entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote((String) row.getKey(), out);
                out.append(':');
                json(row.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object child : (List<?>) value) {
                if (!first) out.append(',');
                first = false;
                json(child, out);
            }
            out.append(']');
        } else if (value instanceof String) {
            quote((String) value, out);
        } else {
            out.append(value == null ? "null" : value.toString());
        }
    }

    private static String sha(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.US_ASCII));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b & 255));
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        need(args.length == 4, "ARGUMENTS");
        need(args[0].matches("[0-9a-f]{64}") && args[1].matches("[0-9a-f]{64}")
            && args[3].matches("[0-9a-f]{64}"),
             "EXPECTED_HASHES");
        int expectedEntries = Integer.parseInt(args[2]);
        need(expectedEntries > 0 && expectedEntries <= 4096, "ENTRY_BOUND");
        need(CohortTerminalTelemetryInputV2.schemaTag()
            .equals("tsj-cohort-terminal-input-context-v2"), "SCHEMA_TAG");
        need(CohortTerminalTelemetryInputV2.contextSha256().equals(args[0]), "CONTEXT_SHA");
        need(sha(LiteralInputCanary.value()).equals(args[3]), "LITERAL_RUNTIME_VALUE");
        need(LiteralInputCanary.empty().equals(""), "EMPTY_LITERAL_RUNTIME_VALUE");
        Set<String> methods = new TreeSet<String>();
        for (Method method : CohortTerminalTelemetryInputV2.class.getDeclaredMethods()) {
            need(Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers()), "METHOD_VISIBILITY");
            methods.add(method.getName());
            Class<?> expected = method.getName().equals("dataJson") ? Map.class
                : method.getName().equals("dataEntries") ? Integer.TYPE : String.class;
            need(method.getReturnType() == expected && method.getParameterTypes().length == 0,
                 "METHOD_SIGNATURE");
        }
        need(methods.equals(new TreeSet<String>(Arrays.asList(
            "schemaTag", "contextSha256", "dataEntries", "dataJson"))), "CURRENT_API_ONLY");
        Map<String,Object> first = CohortTerminalTelemetryInputV2.dataJson();
        Map<String,Object> second = CohortTerminalTelemetryInputV2.dataJson();
        IdentityHashMap<Object,Boolean> seen = new IdentityHashMap<Object,Boolean>();
        need(count(first, seen) == expectedEntries, "COUNT_FIRST");
        need(count(second, seen) == expectedEntries, "COUNT_SECOND_AND_NO_SHARED_CONTAINERS");
        need(CohortTerminalTelemetryInputV2.dataEntries() == expectedEntries, "ENTRY_METHOD");
        need(first.keySet().equals(new java.util.LinkedHashSet<String>(Arrays.asList(
            "prior", "capture", "ownerSettings", "seedValues", "sourceIdentity"))),
             "PROJECTION_KEYS");
        @SuppressWarnings("unchecked")
        Map<String,Object> seeds = (Map<String,Object>) first.get("seedValues");
        need(seeds.get("rootSeed").equals("9007199254740993"), "ROOT_SEED_EXACT");
        need(seeds.get("placementSeed").equals("-6587958367974952701"), "PLACEMENT_SEED_EXACT");
        need(seeds.get("routingSeed").equals("8445301674744508279"), "ROUTING_SEED_EXACT");
        StringBuilder text = new StringBuilder();
        json(first, text);
        text.append('\n');
        need(sha(text.toString()).equals(args[1]), "NATIVE_PROJECTION_BYTES");
        first.clear();
        need(second.size() == 5, "FRESH_FACTORY_AFTER_MUTATION");
        System.out.println("{\"status\":\"PASS_QUALIFIED_PRIOR_DATA_FACTORY_ONLY\","
            + "\"entries\":" + expectedEntries + ",\"assertions\":" + assertions
            + ",\"sessions\":0,\"originalCaptures\":0,\"routingRuns\":0}");
    }
}
