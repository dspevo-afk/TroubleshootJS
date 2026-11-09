package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic-only native component canary. This main never invokes an owner arm. */
public final class QualifiedMetadataCanary {
    private static final String CONTEXT =
            "ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f";
    private static final String SCHEMA = "tsj-cohort-terminal-qualified-prior-v2";
    private static final String[] PRIOR_KEYS = {
        "sourceParent", "manifestSha256", "sourceManifestSha256", "readerSha256",
        "resultSha256", "operation1Sha256", "parentReceiptSha256", "priorRunId",
        "priorOriginalReceipt", "trialDisposition"
    };
    private static final String[] SMOKE = {
        "receipt_1_byte", "receipt_4096_bytes", "receipt_4097_bytes",
        "receipt_sha_mismatch", "receipt_size_mismatch", "wrong_started",
        "repeat_slot_rejected"
    };
    private static final String[] SUITE = {
        "null_arguments", "body_wrong_class", "body_missing_slot", "body_nonnull_slot",
        "context_short", "context_uppercase", "context_nonhex",
        "input_data_null", "input_data_wrong_class", "prior_null", "prior_wrong_class",
        "prior_wrong_order", "prior_nonstring_key", "prior_missing_key", "prior_extra_key",
        "receipt_changed_after_close", "different_valid_context_is_caller_precondition"
    };
    private static final int JSON_LIMIT = 1048576;
    private QualifiedMetadataCanary() { }

    public static void main(String[] args) {
        String requested = args != null && args.length > 0 ? args[0] : "INVALID";
        String mode = "SMOKE".equals(requested) || "SUITE".equals(requested) ? requested : "INVALID";
        List<String> names = new ArrayList<String>();
        List<Map<String,Object>> records = new ArrayList<Map<String,Object>>();
        boolean pass = true;
        try {
            if (args == null || args.length != 2 ||
                    !("SMOKE".equals(mode) || "SUITE".equals(mode)))
                throw new IOException("CANARY_ARGUMENTS");
            names.addAll(Arrays.asList("SMOKE".equals(mode) ? SMOKE : SUITE));
            Path root = emptyRoot(args[1]);
            for (String name : names) {
                Map<String,Object> record = run(root, name);
                records.add(record);
                if (!"PASS".equals(record.get("status"))) pass = false;
            }
        } catch (Throwable failure) {
            pass = false;
            Map<String,Object> record = new LinkedHashMap<String,Object>();
            record.put("name", "protocol");
            record.put("status", "FAIL");
            record.put("phase", "protocol");
            record.put("reason", reason(failure));
            records.add(record);
        }
        Map<String,Object> out = new LinkedHashMap<String,Object>();
        out.put("mode", mode);
        out.put("status", pass ? "PASS" : "FAIL");
        out.put("names", names);
        out.put("cases", records);
        System.out.println(json(out));
        if (!pass) System.exit(1);
    }

    private static Path emptyRoot(String raw) throws Exception {
        if (java.io.File.separatorChar != '\\') throw new IOException("WINDOWS_REQUIRED");
        Path root = Paths.get(raw);
        if (!root.isAbsolute() || !root.normalize().equals(root) ||
                !root.toRealPath().equals(root) || Files.isSymbolicLink(root) ||
                !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("CANARY_ROOT");
        for (Path p = root; p != null; p = p.getParent())
            new CohortTerminalTelemetryStudy.R5FileIdentity(p, true);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            if (entries.iterator().hasNext()) throw new IOException("CANARY_ROOT_NOT_EMPTY");
        }
        return root;
    }

    private static Map<String,Object> run(Path root, String name) {
        Map<String,Object> record = new LinkedHashMap<String,Object>();
        record.put("name", name);
        String phase = "fixture";
        long operationStarted = System.nanoTime();
        long operationNanos;
        long cleanupNanos = 0L;
        Throwable failure = null;
        Path directory = null;
        Path receipt = null;
        boolean createdDirectory = false;
        boolean createdReceipt = false;
        CohortTerminalTelemetryStudy.SetupMeter meter = null;
        try {
            directory = root.resolve(name);
            Files.createDirectory(directory);
            createdDirectory = true;
            receipt = directory.resolve("op-1.jsonl");
            int size = "receipt_1_byte".equals(name) ? 1 :
                    "receipt_4097_bytes".equals(name) ? 4097 : 4096;
            byte[] bytes = new byte[size];
            Arrays.fill(bytes, (byte)'x');
            bytes[size - 1] = (byte)'\n';
            Files.createFile(receipt);
            createdReceipt = true;
            Files.write(receipt, bytes, StandardOpenOption.WRITE);
            // Fixture I/O and the independent P/B oracle precede the producer clock.
            String sha = sha256(Files.readAllBytes(receipt));
            Path canonical = receipt.toRealPath();
            require(canonical.equals(receipt), "fixture_canonical_path");
            int paths = canonical.getNameCount() + 1;
            long blocks = (((long)size) + 4095L) / 4096L;
            FileTime modified = Files.getLastModifiedTime(receipt, LinkOption.NOFOLLOW_LINKS);
            CohortTerminalTelemetryStudy.Parent parent = new CohortTerminalTelemetryStudy.Parent();
            long started = System.nanoTime();
            meter = new CohortTerminalTelemetryStudy.SetupMeter(parent, parent.meter(started));
            phase = "input_setup";
            CohortTerminalTelemetryStudy.SetupResult setup = CurrentV2InputSetup.read(
                    meter, started, CONTEXT, new CurrentV2InputSetup.Factory() {
                        public String schemaTag() { return CohortTerminalTelemetryInputV2.schemaTag(); }
                        public String contextSha256() { return CohortTerminalTelemetryInputV2.contextSha256(); }
                        public int dataEntries() { return CohortTerminalTelemetryInputV2.dataEntries(); }
                        public Map<String,Object> dataJson() { return CohortTerminalTelemetryInputV2.dataJson(); }
                    });
            checkSetup(meter, setup, started);
            long setupMaxNanos = number(meter, "maxGroupNanos");
            phase = "component";
            Map<String,Object> body = body();
            if ("receipt_1_byte".equals(name) || "receipt_4096_bytes".equals(name) ||
                    "receipt_4097_bytes".equals(name) ||
                    "repeat_slot_rejected".equals(name) ||
                    "different_valid_context_is_caller_precondition".equals(name) ||
                    "receipt_changed_after_close".equals(name)) {
                String context = "different_valid_context_is_caller_precondition".equals(name)
                        ? repeat('0', 64) : CONTEXT;
                String before = json(setup.data);
                Map<?,?> prior = (Map<?,?>)setup.data.get("prior");
                Object[] values = new Object[10];
                for (int i = 0; i < values.length; i++) values[i] = prior.get(PRIOR_KEYS[i]);
                ReceiptContentBinding ticket = CurrentAssemblyMetadata.prepare(
                        meter, started, setup, context, receipt, size, sha, body);
                require(ticket != null && ticket.getClass() == ReceiptContentBinding.class, "actual_ticket");
                long deltaUnits = 268L + 10L * paths + 6L * blocks;
                long deltaCopies = 2L * paths + 21L;
                checkOpen(meter, setup, started, setupMaxNanos, deltaUnits, deltaCopies);
                checkQualified(body, prior, values, context);
                require(before.equals(json(setup.data)), "source_input_unchanged");
                record.put("pathEntries", Integer.valueOf(paths));
                record.put("hashBlocks", Long.valueOf(blocks));
                record.put("deltaUnits", Long.valueOf(number(meter, "units") - setup.units));
                record.put("deltaCopiedEntries", Long.valueOf(number(meter, "copiedEntries") - setup.copiedEntries));
                record.put("deltaCopyChunks", Long.valueOf(number(meter, "copyChunks") - setup.copyChunks));
                record.put("metadataOpen", Boolean.TRUE);
                if ("repeat_slot_rejected".equals(name)) {
                    expect(meter, started, setup, context, receipt, size, sha, body,
                            "METADATA_BODY_SLOT", false);
                }
                // The caller, never the component, closes metadata and consumes the ticket.
                phase = "caller_finish";
                meter.finish();
                checkClosed(meter, setup, started, deltaUnits, deltaCopies,
                        "repeat_slot_rejected".equals(name) ? 4L : 0L);
                long units = number(meter, "units");
                long copies = number(meter, "copiedEntries");
                long chunks = number(meter, "copyChunks");
                phase = "final_ticket_verification";
                if ("receipt_changed_after_close".equals(name)) {
                    bytes[0] = (byte)'y';
                    Files.write(receipt, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    Files.setLastModifiedTime(receipt, modified);
                    try {
                        ticket.verifyFinal(meter, started, receipt);
                        throw new AssertionError("changed_receipt_accepted");
                    } catch (ReceiptContentBinding.BindingFailure expected) {
                        require("FINAL_SHA_MISMATCH".equals(expected.code), "changed_receipt_code");
                        record.put("expectedFailure", expected.code);
                    }
                } else {
                    ticket.verifyFinal(meter, started, receipt);
                }
                require(units == number(meter, "units") && copies == number(meter, "copiedEntries") &&
                        chunks == number(meter, "copyChunks") && !flag(meter, "open"),
                        "final_verification_no_meter_work");
                if ("receipt_4096_bytes".equals(name)) {
                    phase = "synthetic_json_fixture";
                    byte[] output = (json(body.get("qualifiedPrior")) + "\n").getBytes("US-ASCII");
                    require(output.length <= JSON_LIMIT, "fixture_bound");
                    Files.write(root.resolve("qualified-prior.json"), output,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    record.put("qualifiedPriorBytes", Integer.valueOf(output.length));
                    record.put("qualifiedPriorSha256", sha256(output));
                }
            } else if ("null_arguments".equals(name)) {
                for (int i = 0; i < 6; i++) {
                    long units = number(meter, "units");
                    expect(i == 0 ? null : meter, started, i == 1 ? null : setup,
                            i == 2 ? null : CONTEXT, i == 3 ? null : receipt, size,
                            i == 4 ? null : sha, i == 5 ? null : body,
                            "METADATA_ARGUMENTS", false);
                    require(units == number(meter, "units") && !flag(meter, "open"),
                            "null_rejected_before_source");
                }
            } else {
                CohortTerminalTelemetryStudy.SetupResult supplied = setup;
                String context = CONTEXT;
                String expected = "METADATA_PRIOR_SHAPE";
                boolean binding = false;
                long expectedBytes = size;
                long suppliedStarted = started;
                if ("receipt_sha_mismatch".equals(name)) {
                    sha = repeat('0', 64); expected = "INITIAL_SHA_MISMATCH"; binding = true;
                } else if ("receipt_size_mismatch".equals(name)) {
                    expectedBytes++; expected = "INITIAL_LENGTH_MISMATCH"; binding = true;
                } else if ("wrong_started".equals(name)) {
                    suppliedStarted++; expected = "PREPARE_AUTHORITY_MISMATCH"; binding = true;
                } else if ("body_wrong_class".equals(name)) {
                    body = new HashMap<String,Object>(body); expected = "METADATA_BODY_SLOT";
                } else if ("body_missing_slot".equals(name)) {
                    body.remove("qualifiedPrior"); expected = "METADATA_BODY_SLOT";
                } else if ("body_nonnull_slot".equals(name)) {
                    body.put("qualifiedPrior", "occupied"); expected = "METADATA_BODY_SLOT";
                } else if ("context_short".equals(name)) {
                    context = "abc"; expected = "METADATA_CONTEXT_SHA";
                } else if ("context_uppercase".equals(name)) {
                    context = "A" + CONTEXT.substring(1); expected = "METADATA_CONTEXT_SHA";
                } else if ("context_nonhex".equals(name)) {
                    context = "g" + CONTEXT.substring(1); expected = "METADATA_CONTEXT_SHA";
                } else if ("input_data_null".equals(name)) {
                    supplied = malformedSetup(null, setup); expected = "METADATA_INPUT_DATA";
                } else if ("input_data_wrong_class".equals(name)) {
                    supplied = malformedSetup(new HashMap<String,Object>(setup.data), setup);
                    expected = "METADATA_INPUT_DATA";
                } else {
                    Map<String,Object> data = new LinkedHashMap<String,Object>(setup.data);
                    Map<?,?> prior = (Map<?,?>)setup.data.get("prior");
                    if ("prior_null".equals(name)) data.put("prior", null);
                    else if ("prior_wrong_class".equals(name))
                        data.put("prior", new HashMap<Object,Object>(prior));
                    else {
                        Map<Object,Object> bad = new LinkedHashMap<Object,Object>();
                        if ("prior_wrong_order".equals(name)) {
                            bad.put(PRIOR_KEYS[1], prior.get(PRIOR_KEYS[1]));
                            bad.put(PRIOR_KEYS[0], prior.get(PRIOR_KEYS[0]));
                            for (int i = 2; i < 10; i++) bad.put(PRIOR_KEYS[i], prior.get(PRIOR_KEYS[i]));
                        } else {
                            for (int i = 0; i < 10; i++)
                                bad.put("prior_nonstring_key".equals(name) && i == 0
                                        ? Integer.valueOf(0) : PRIOR_KEYS[i], prior.get(PRIOR_KEYS[i]));
                            if ("prior_missing_key".equals(name)) bad.remove(PRIOR_KEYS[9]);
                            else if ("prior_extra_key".equals(name)) bad.put("extra", null);
                            else require("prior_nonstring_key".equals(name), "known_negative_case");
                        }
                        data.put("prior", bad);
                    }
                    supplied = malformedSetup(data, setup);
                }
                String inputBefore = json(setup.data);
                expect(meter, suppliedStarted, supplied, context, receipt, expectedBytes,
                        sha, body, expected, binding);
                require(inputBefore.equals(json(setup.data)), "validated_input_unchanged");
                record.put("expectedFailure", expected);
            }
        } catch (Throwable caught) {
            failure = caught;
        }
        operationNanos = System.nanoTime() - operationStarted;
        long cleanupStarted = System.nanoTime();
        try {
            if (meter != null) meter.finish();
        } catch (Throwable cleanup) {
            if (failure == null) { failure = cleanup; phase = "cleanup"; }
            else failure.addSuppressed(cleanup);
        }
        // Exact successfully created leaves only. No traversal or recursive cleanup.
        // File cleanup is attempted even if the caller's Meter finish failed.
        try {
            if (createdReceipt) Files.deleteIfExists(receipt);
        } catch (Throwable cleanup) {
            if (failure == null) { failure = cleanup; phase = "cleanup"; }
            else failure.addSuppressed(cleanup);
        }
        try {
            if (createdDirectory) Files.deleteIfExists(directory);
        } catch (Throwable cleanup) {
            if (failure == null) { failure = cleanup; phase = "cleanup"; }
            else failure.addSuppressed(cleanup);
        }
        cleanupNanos = System.nanoTime() - cleanupStarted;
        record.put("status", failure == null ? "PASS" : "FAIL");
        record.put("phase", failure == null ? "complete" : phase);
        record.put("operationNanos", Long.valueOf(operationNanos));
        record.put("cleanupNanos", Long.valueOf(cleanupNanos));
        record.put("reason", failure == null ? "OK" : reason(failure));
        record.put("suppressedFailures", Integer.valueOf(failure == null ? 0 : failure.getSuppressed().length));
        return record;
    }

    private static CohortTerminalTelemetryStudy.SetupResult malformedSetup(
            Map<String,Object> data, CohortTerminalTelemetryStudy.SetupResult actual) {
        // Negative guard probe only; never used as a successful validated setup.
        return new CohortTerminalTelemetryStudy.SetupResult(data, actual.units,
                actual.copiedEntries, actual.groups, actual.copyChunks);
    }

    private static void expect(CohortTerminalTelemetryStudy.SetupMeter meter, long started,
            CohortTerminalTelemetryStudy.SetupResult setup, String context, Path receipt,
            long bytes, String sha, Map<String,Object> body, String code, boolean binding)
            throws Exception {
        Map<String,Object> before = body == null ? null : new LinkedHashMap<String,Object>(body);
        String beforeJson = body == null ? null : json(body);
        try {
            CurrentAssemblyMetadata.prepare(meter, started, setup, context, receipt, bytes, sha, body);
            throw new AssertionError("expected_rejection_" + code);
        } catch (ReceiptContentBinding.BindingFailure rejected) {
            require(binding && code.equals(rejected.code), "binding_failure_propagation");
        } catch (IOException rejected) {
            require(!binding && code.equals(rejected.getMessage()), "metadata_failure_code");
        }
        if (body != null) {
            require(new ArrayList<String>(before.keySet()).equals(new ArrayList<String>(body.keySet())),
                    "failure_body_order_unchanged");
            for (String key : before.keySet()) require(before.get(key) == body.get(key),
                    "failure_body_values_unchanged");
            require(beforeJson.equals(json(body)), "failure_body_nested_content_unchanged");
        }
    }

    private static Map<String,Object> body() {
        Map<String,Object> body = new LinkedHashMap<String,Object>();
        body.put("before", "sentinel-before");
        body.put("qualifiedPrior", null);
        body.put("after", "sentinel-after");
        return body;
    }

    private static void checkQualified(Map<String,Object> body, Map<?,?> prior,
            Object[] values, String context) {
        require(Arrays.asList("before", "qualifiedPrior", "after").equals(
                new ArrayList<String>(body.keySet())), "body_key_order");
        require("sentinel-before".equals(body.get("before")) &&
                "sentinel-after".equals(body.get("after")), "body_sentinels");
        Object value = body.get("qualifiedPrior");
        require(value != prior && value.getClass() == LinkedHashMap.class, "new_qualified_map");
        Map<?,?> qualified = (Map<?,?>)value;
        List<String> keys = new ArrayList<String>();
        keys.add("schema"); keys.add("inputContextSha256"); keys.addAll(Arrays.asList(PRIOR_KEYS));
        require(qualified.size() == 12 && keys.equals(new ArrayList<Object>(qualified.keySet())),
                "qualified_key_order");
        require(SCHEMA.equals(qualified.get("schema")) &&
                SCHEMA.equals(CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA), "schema_literal");
        require(context.equals(qualified.get("inputContextSha256")), "context_literal");
        require(prior.size() == 10 && Arrays.asList(PRIOR_KEYS).equals(
                new ArrayList<Object>(prior.keySet())), "input_prior_ten_unchanged");
        for (int i = 0; i < 10; i++) require(values[i] == qualified.get(PRIOR_KEYS[i]) &&
                values[i] == prior.get(PRIOR_KEYS[i]), "prior_value_identity");
    }

    private static void checkSetup(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, long started) throws Exception {
        require(setup.units == 10007L && setup.copiedEntries == 478L &&
                setup.copyChunks == 3 && setup.groups == 1, "input_setup_fixed_oracle");
        require(number(meter, "units") == setup.units && number(meter, "copiedEntries") == 478L &&
                number(meter, "copyChunks") == 3L && number(meter, "groups") == 1L &&
                number(meter, "groupUnits") == 10007L && number(meter, "maxGroupUnits") == 10007L &&
                !flag(meter, "open"), "input_setup_closed_counters");
        commonMeter(meter, started);
        checkMeasures(meter, 1, 10007L);
    }

    private static void checkOpen(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, long started, long maxNanos,
            long units, long copies) throws Exception {
        require(number(meter, "units") == setup.units + units &&
                number(meter, "copiedEntries") == setup.copiedEntries + copies &&
                number(meter, "copyChunks") == setup.copyChunks + 5L &&
                number(meter, "groups") == setup.groups && flag(meter, "open") &&
                number(meter, "groupUnits") == units &&
                number(meter, "maxGroupUnits") == 10007L &&
                number(meter, "maxGroupNanos") == maxNanos, "component_exact_meter_delta");
        commonMeter(meter, started);
        checkMeasures(meter, 1, 10007L);
    }

    private static void checkClosed(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, long started, long units,
            long copies, long extra) throws Exception {
        require(!flag(meter, "open") && number(meter, "groups") == 2L &&
                number(meter, "units") == setup.units + units + extra &&
                number(meter, "copiedEntries") == setup.copiedEntries + copies &&
                number(meter, "copyChunks") == setup.copyChunks + 5L &&
                number(meter, "groupUnits") == units + extra &&
                number(meter, "maxGroupUnits") == Math.max(10007L, units + extra),
                "caller_finish_counters");
        commonMeter(meter, started);
        checkMeasures(meter, 2, units + extra);
    }

    private static void commonMeter(CohortTerminalTelemetryStudy.SetupMeter meter,
            long started) throws Exception {
        require(number(meter, "started") == started && number(meter, "copyAllowance") == 1048576L &&
                number(meter, "priorGroups") == 0L && number(meter, "inFlightOwnerGroups") == 0L &&
                number(meter, "sessionConstructorEntries") == 0L &&
                !flag(meter, "originalOwnerStarted") && !flag(meter, "originalOwnerActive") &&
                !flag(meter, "originalOwnerAdopted"), "meter_owner_flags_and_allowance");
        require(number(meter, "groupStarted") >= started &&
                number(meter, "maxGroupNanos") >= 0L &&
                number(meter, "maxGroupNanos") <= 5000000000L, "meter_clocks");
    }

    private static void checkMeasures(CohortTerminalTelemetryStudy.SetupMeter meter,
            int count, long lastUnits) throws Exception {
        List<?> measures = (List<?>)CohortTerminalTelemetryStudy.field(meter.meter, "groupMeasures");
        require(measures.size() == count, "actual_group_measure_count");
        long max = 0L;
        for (int i = 0; i < measures.size(); i++) {
            Object measure = measures.get(i);
            long duration = ((Number)CohortTerminalTelemetryStudy.field(measure, "nanos")).longValue();
            long units = ((Number)CohortTerminalTelemetryStudy.field(measure, "units")).longValue();
            require(duration >= 0L && duration <= 5000000000L &&
                    units == (i == count - 1 ? lastUnits : 10007L), "actual_group_measure");
            max = Math.max(max, duration);
        }
        require(number(meter, "maxGroupNanos") == max, "actual_max_group_nanos");
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter, String field) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(meter.meter, field)).longValue();
    }
    private static boolean flag(CohortTerminalTelemetryStudy.SetupMeter meter, String field) throws Exception {
        return ((Boolean)CohortTerminalTelemetryStudy.field(meter.meter, field)).booleanValue();
    }
    private static void require(boolean ok, String code) {
        if (!ok) throw new AssertionError(code);
    }
    private static String repeat(char ch, int n) {
        char[] chars = new char[n]; Arrays.fill(chars, ch); return new String(chars);
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest) {
            out.append("0123456789abcdef".charAt((b & 255) >>> 4));
            out.append("0123456789abcdef".charAt(b & 15));
        }
        return out.toString();
    }
    private static String reason(Throwable failure) {
        String message = failure.getMessage();
        String text = failure.getClass().getSimpleName() + ":" + (message == null ? "" : message);
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < text.length() && i < 240; i++) {
            char ch = text.charAt(i); clean.append(ch >= 32 && ch <= 126 ? ch : '?');
        }
        return clean.toString();
    }

    /** Test-only bounded ASCII serializer. Never replaces or exercises a product codec. */
    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, new IdentityHashMap<Object,Boolean>(), 0);
        require(out.length() <= JSON_LIMIT, "test_json_bound");
        return out.toString();
    }
    private static void write(Object value, StringBuilder out,
            IdentityHashMap<Object,Boolean> active, int depth) {
        require(depth <= 32 && out.length() <= JSON_LIMIT, "test_json_bound");
        if (value == null) out.append("null");
        else if (value instanceof String) {
            out.append('"');
            String string = (String)value;
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                require(c >= 32 && c <= 126, "test_json_ascii");
                if (c == '"' || c == '\\') out.append('\\');
                out.append(c);
                require(out.length() <= JSON_LIMIT, "test_json_bound");
            }
            out.append('"');
        } else if (value instanceof Integer || value instanceof Long || value instanceof Boolean)
            out.append(value.toString());
        else {
            require(active.put(value, Boolean.TRUE) == null, "test_json_cycle");
            if (value instanceof Map) {
                out.append('{'); boolean first = true;
                for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) {
                    require(entry.getKey() instanceof String, "test_json_key");
                    if (!first) out.append(','); first = false;
                    write(entry.getKey(), out, active, depth + 1); out.append(':');
                    write(entry.getValue(), out, active, depth + 1);
                }
                out.append('}');
            } else if (value instanceof List) {
                out.append('['); boolean first = true;
                for (Object item : (List<?>)value) {
                    if (!first) out.append(','); first = false;
                    write(item, out, active, depth + 1);
                }
                out.append(']');
            } else throw new AssertionError("test_json_type");
            active.remove(value);
        }
        require(out.length() <= JSON_LIMIT, "test_json_bound");
    }
}
