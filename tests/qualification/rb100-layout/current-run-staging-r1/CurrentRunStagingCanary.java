package com.lushprojects.circuitjs1.client;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-only R1 input and pre-body staging canary. */
public final class CurrentRunStagingCanary {
    private static final String SCHEMA = "TSJ_CURRENT_RUN_STAGING_CANARY_R1";
    private static final String CONTEXT_SHA =
        "b18d9b6169cfe1ed846402a6a7784fb49f008624a00bc8aa535fa73e444e984d";
    private static final String[] CASES = {
        "positive", "wrong_capture_order", "duplicate_capture", "missing_capture",
        "requested_mismatch", "prior_false", "capture_false", "null_arguments",
        "group_guard_before_read"
    };
    private static final String[] PAIR = {"A_COIL_LOW", "B_COIL_LOW"};
    private static final String[] EXPECTED_CAPTURED = {
        "NET_000", "NET_001", "NET_002", "NET_003", "NET_004", "NET_005",
        "NET_006", "NET_007", "NET_008", "NET_009", "D_SENSE", "NET_011",
        "NET_012", "NET_013", "NET_014", "NET_015", "NET_016", "NET_017",
        "NET_018", "NET_019", "NET_020", "NET_021", "NET_022", "NET_023",
        "NET_024", "NET_025", "NET_026", "NET_027", "NET_028", "NET_029",
        "NET_030", "NET_031", "NET_032", "NET_033", "NET_034", "NET_035",
        "NET_036", "NET_037", "NET_038", "NET_039", "NET_040", "NET_041",
        "NET_042", "A_COIL_LOW", "NET_044", "NET_045", "NET_046", "NET_047",
        "NET_048", "NET_049", "B_COIL_LOW", "NET_051", "NET_052", "NET_053",
        "NET_054", "NET_055", "NET_056", "NET_057", "NET_058", "NET_059"
    };
    private static final String[] EXPECTED_REQUESTED = {
        "A_COIL_LOW", "B_COIL_LOW", "NET_000", "NET_001", "NET_002", "NET_003",
        "NET_004", "NET_005", "NET_006", "NET_007", "NET_008", "NET_009",
        "D_SENSE", "NET_011", "NET_012", "NET_013", "NET_014", "NET_015",
        "NET_016", "NET_017", "NET_018", "NET_019", "NET_020", "NET_021",
        "NET_022", "NET_023", "NET_024", "NET_025", "NET_026", "NET_027",
        "NET_028", "NET_029", "NET_030", "NET_031", "NET_032", "NET_033",
        "NET_034", "NET_035", "NET_036", "NET_037", "NET_038", "NET_039",
        "NET_040", "NET_041", "NET_042", "NET_044", "NET_045", "NET_046",
        "NET_047", "NET_048", "NET_049", "NET_051", "NET_052", "NET_053",
        "NET_054", "NET_055", "NET_056", "NET_057", "NET_058", "NET_059"
    };
    private static int parentConstructions;

    private CurrentRunStagingCanary() { }

    public static void main(String[] args) {
        boolean valid = args != null && args.length == 1 && "SUITE".equals(args[0]);
        List<String> names = new ArrayList<String>();
        List<Map<String,Object>> cases = new ArrayList<Map<String,Object>>();
        boolean pass = valid;
        if (valid) {
            for (String name : CASES) {
                names.add(name);
                Map<String,Object> result = runCase(name);
                cases.add(result);
                if (!"PASS".equals(result.get("status"))) pass = false;
            }
        }
        Map<String,Object> output = new LinkedHashMap<String,Object>();
        output.put("schema", SCHEMA);
        output.put("mode", valid ? "SUITE" : "INVALID");
        output.put("status", pass ? "PASS" : "FAIL");
        output.put("names", names);
        output.put("cases", cases);
        output.put("parentConstructions", Integer.valueOf(parentConstructions));
        output.put("error", valid ? (pass ? "" : "CANARY_CASE_FAILED") : "CANARY_ARGUMENTS");
        try {
            System.out.println(json(output));
        } catch (Throwable failure) {
            System.out.println("{\"schema\":\"TSJ_CURRENT_RUN_STAGING_CANARY_R1\",\"mode\":\"INVALID\",\"status\":\"FAIL\",\"names\":[],\"cases\":[],\"parentConstructions\":0,\"error\":\"CANARY_SUMMARY_FAILURE\"}");
            pass = false;
        }
        if (!pass) System.exit(1);
    }

    private static Map<String,Object> runCase(String name) {
        Map<String,Object> r = record(name);
        CohortTerminalTelemetryStudy.SetupMeter meter = null;
        FixtureFactory factory = null;
        String phase = "parent";
        try {
            long started = System.nanoTime();
            CohortTerminalTelemetryStudy.Parent parent = new CohortTerminalTelemetryStudy.Parent();
            parentConstructions++;
            meter = new CohortTerminalTelemetryStudy.SetupMeter(parent, parent.meter(started));
            if ("group_guard_before_read".equals(name)) {
                phase = "group_guard";
                groupGuard(meter, r);
            } else {
                factory = new FixtureFactory(name);
                phase = "native_setup";
                CohortTerminalTelemetryStudy.SetupResult setup = readSetup(name, factory, meter, r);
                if (setup != null) {
                    phase = "fixture_checks";
                    verifySetup(name, setup, factory, meter);
                    if ("null_arguments".equals(name)) {
                        phase = "argument_guards";
                        nullArguments(setup, meter, factory, r);
                    } else {
                        phase = "stage";
                        stageCase(name, setup, meter, factory, r);
                    }
                }
            }
            r.put("status", "PASS");
            if ("EXPECTED_REJECTION".equals(r.get("reason"))) r.put("phase", phase);
            else { r.put("phase", "complete"); r.put("reason", "OK"); }
        } catch (Throwable failure) {
            r.put("status", "FAIL");
            r.put("phase", phase);
            r.put("reason", safeDescribe(failure));
            if ("".equals(r.get("observedError"))) r.put("observedError", errorText(failure));
        } finally {
            if (factory != null) {
                r.put("sourceReads", Integer.valueOf(factory.reads));
                boolean unchanged = factory.data != null && factory.before != null &&
                    factory.before.equals(snapshot(factory.data));
                r.put("inputUnchanged", Boolean.valueOf(unchanged));
                if (!unchanged && "PASS".equals(r.get("status"))) {
                    r.put("status", "FAIL");
                    r.put("phase", "input_snapshot");
                    r.put("reason", "INPUT_CHANGED");
                }
            }
            if (meter != null) {
                try {
                    meter.method("check", new Class<?>[0]);
                    nativeFields(meter, r);
                }
                catch (Throwable failure) {
                    r.put("status", "FAIL");
                    r.put("phase", "native_fields");
                    r.put("reason", safeDescribe(failure));
                }
                try { r.put("totalElapsedNanos", Long.valueOf(elapsed(number(meter, "started"), System.nanoTime()))); }
                catch (Throwable failure) {
                    r.put("status", "FAIL");
                    r.put("reason", safeDescribe(failure));
                }
            }
            r.put("discarded", Boolean.valueOf(!("positive".equals(name) && "PASS".equals(r.get("status")))));
        }
        return r;
    }

    private static CohortTerminalTelemetryStudy.SetupResult readSetup(String name, FixtureFactory factory,
            CohortTerminalTelemetryStudy.SetupMeter meter, Map<String,Object> r) throws Exception {
        long[] before = debit(meter);
        long started = System.nanoTime();
        CohortTerminalTelemetryStudy.SetupResult setup = null;
        Throwable failure = null;
        try {
            setup = CurrentV2InputSetup.read(meter, number(meter, "started"), CONTEXT_SHA, factory);
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            r.put("operationNanos", Long.valueOf(elapsed(started, System.nanoTime())));
            delta(r, "setupDelta", before, debit(meter));
        }
        r.put("sourceReads", Integer.valueOf(factory.reads));
        if ("prior_false".equals(name) || "capture_false".equals(name)) {
            expectError(r, failure, "IOException:V2_SETUP_POLICY");
            r.put("rejections", Long.valueOf(1));
            r.put("reason", "EXPECTED_REJECTION");
            check(setup == null, "FALSE_POLICY_SETUP_RETURNED");
            check(factory.reads == 4, "NATIVE_FACTORY_READS");
            assertPartialSetupBound(meter);
            return null;
        }
        if (failure != null) {
            r.put("observedError", errorText(failure));
            throw new AssertionError("UNEXPECTED_NATIVE_SETUP:" + safeDescribe(failure));
        }
        check(setup != null, "NATIVE_SETUP_NULL");
        r.put("reason", "OK");
        return setup;
    }

    private static void verifySetup(String name, CohortTerminalTelemetryStudy.SetupResult setup,
            FixtureFactory factory, CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        check(setup.data == factory.data, "SETUP_DATA_IDENTITY");
        check(setup.units == 10007L && setup.copiedEntries == 478L &&
                setup.copyChunks == 3 && setup.groups == 1, "SETUP_RESULT_CENSUS");
        check(number(meter, "units") == 10007L && number(meter, "copiedEntries") == 478L &&
                number(meter, "copyChunks") == 3L && number(meter, "groups") == 1L &&
                !Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open")),
                "SETUP_METER_CENSUS");
        check(factory.reads == 4, "NATIVE_FACTORY_READS");
        Map<String,Object> root = asMap(setup.data);
        Map<String,Object> prior = asMap(root.get("prior"));
        Map<String,Object> priorTrial = asMap(prior.get("trialDisposition"));
        Map<String,Object> capture = asMap(root.get("capture"));
        Map<String,Object> captureTrial = asMap(capture.get("trialDisposition"));
        List<?> captured = asList(capture.get("capturedNetIds"));
        List<?> requested = asList(captureTrial.get("requestedNetIds"));
        List<?> priorRequested = asList(priorTrial.get("requestedNetIds"));
        check(listEquals(captured, EXPECTED_CAPTURED), "FIXTURE_CAPTURED_LITERAL");
        String[] expectedRequested = requestedFor(name);
        check(listEquals(requested, expectedRequested) && listEquals(priorRequested, expectedRequested),
                "FIXTURE_REQUESTED_LITERAL");
        check(listEquals(asList(capture.get("cohortNetIds")), PAIR), "FIXTURE_CAPTURE_COHORT");
        check(listEquals(asList(captureTrial.get("cohortNetIds")), PAIR) &&
                listEquals(asList(priorTrial.get("cohortNetIds")), PAIR), "FIXTURE_TRIAL_COHORT");
        check(Boolean.TRUE.equals(priorTrial.get("orderChanged")) &&
                Boolean.TRUE.equals(captureTrial.get("orderChanged")), "FIXTURE_RUN_FLAGS");
        check("RUN".equals(priorTrial.get("status")) && "TRIAL_EXECUTED".equals(priorTrial.get("reason")) &&
                "RUN".equals(captureTrial.get("status")) && "TRIAL_EXECUTED".equals(captureTrial.get("reason")),
                "FIXTURE_TRIAL_DISPOSITION");
        IdentityHashMap<Object,Boolean> containers = new IdentityHashMap<Object,Boolean>();
        collectDistinct(setup.data, containers);
    }

    private static void stageCase(String name, CohortTerminalTelemetryStudy.SetupResult setup,
            CohortTerminalTelemetryStudy.SetupMeter meter, FixtureFactory factory,
            Map<String,Object> r) throws Exception {
        Map<String,Object> capture = asMap(asMap(setup.data).get("capture"));
        List<String> captured = copyStrings(asList(capture.get("capturedNetIds")));
        check(captured != capture.get("capturedNetIds"), "CAPTURE_COPY_ALIAS");
        if ("wrong_capture_order".equals(name)) {
            String value = captured.get(0); captured.set(0, captured.get(1)); captured.set(1, value);
        } else if ("duplicate_capture".equals(name)) {
            captured.set(20, captured.get(19));
        } else if ("missing_capture".equals(name)) {
            captured.set(43, "SYNTH_MISSING");
        }

        long[] before = debit(meter);
        Object[] inputIdentityBefore = inputIdentity(setup);
        long started = System.nanoTime();
        Map<String,Object> payload = null;
        Throwable failure = null;
        try {
            payload = CurrentAssemblyMetadata.cohortBeforeBody(meter, setup, captured);
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            r.put("operationNanos", Long.valueOf(elapsed(started, System.nanoTime())));
            delta(r, "stageDelta", before, debit(meter));
        }
        check(sameIdentity(inputIdentityBefore, inputIdentity(setup)), "INPUT_IDENTITY_CHANGED_AFTER_STAGE");
        r.put("sourceReadCensus", Integer.valueOf(1));
        String expected = expectedError(name);
        if ("positive".equals(name)) {
            check(failure == null, "POSITIVE_STAGE_THROW:" + (failure == null ? "" : safeDescribe(failure)));
            check(deltaValue(r, "stageDeltaUnits") == 264L &&
                    deltaValue(r, "stageDeltaCopies") == 125L &&
                    deltaValue(r, "stageDeltaChunks") == 64L, "POSITIVE_STAGE_DEBIT");
            verifyPayload(payload);
            IdentityHashMap<Object,Boolean> inputContainers = containers(setup.data);
            check(!inputContainers.containsKey(captured), "CAPTURE_COPY_INPUT_ALIAS");
            inputContainers.put(captured, Boolean.TRUE);
            boolean detached = !retains(payload, inputContainers);
            check(detached, "OUTPUT_RETAINS_INPUT");
            r.put("payload", payload);
            r.put("outputDetached", Boolean.TRUE);
            r.put("rejections", Long.valueOf(0));
            r.put("reason", "OK");
            // Close the caller-owned staging group only after the successful projection.
            r.put("finishCalls", Integer.valueOf(1));
            meter.finish();
            check(number(meter, "units") == 10271L && number(meter, "copiedEntries") == 603L &&
                    number(meter, "copyChunks") == 67L && number(meter, "groups") == 2L &&
                    !Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open")),
                    "COMBINED_METER_CENSUS");
        } else {
            expectError(r, failure, expected);
            check(payload == null, "REJECTED_STAGE_RETURNED");
            boolean debitMatches = "wrong_capture_order".equals(name) || "requested_mismatch".equals(name) ?
                deltaValue(r, "stageDeltaUnits") == 259L && deltaValue(r, "stageDeltaCopies") == 123L && deltaValue(r, "stageDeltaChunks") == 63L :
                "duplicate_capture".equals(name) ? deltaValue(r, "stageDeltaUnits") == 141L && deltaValue(r, "stageDeltaCopies") == 84L && deltaValue(r, "stageDeltaChunks") == 24L :
                deltaValue(r, "stageDeltaUnits") == 258L && deltaValue(r, "stageDeltaCopies") == 123L && deltaValue(r, "stageDeltaChunks") == 63L;
            check(debitMatches, "REJECTION_STAGE_DEBIT");
            r.put("rejections", Long.valueOf(1));
            r.put("reason", "EXPECTED_REJECTION");
            check(((Number)r.get("finishCalls")).intValue() == 0, "FAILED_METER_FINISHED");
        }
        check(factory.before.equals(snapshot(factory.data)), "INPUT_CHANGED_AFTER_STAGE");
    }

    private static void nullArguments(CohortTerminalTelemetryStudy.SetupResult setup,
            CohortTerminalTelemetryStudy.SetupMeter meter, FixtureFactory factory,
            Map<String,Object> r) throws Exception {
        List<String> captured = copyStrings(asList(asMap(asMap(setup.data).get("capture")).get("capturedNetIds")));
        long[] before = debit(meter);
        long started = System.nanoTime();
        Throwable[] observed = new Throwable[3];
        try {
            try { CurrentAssemblyMetadata.cohortBeforeBody(null, setup, captured); }
            catch (Throwable failure) { observed[0] = failure; }
            try { CurrentAssemblyMetadata.cohortBeforeBody(meter, null, captured); }
            catch (Throwable failure) { observed[1] = failure; }
            try { CurrentAssemblyMetadata.cohortBeforeBody(meter, setup, null); }
            catch (Throwable failure) { observed[2] = failure; }
        } finally {
            r.put("operationNanos", Long.valueOf(elapsed(started, System.nanoTime())));
            delta(r, "stageDelta", before, debit(meter));
        }
        for (Throwable failure : observed) expectError(r, failure, "IOException:METADATA_ARGUMENTS");
        check(deltaValue(r, "stageDeltaUnits") == 0L && deltaValue(r, "stageDeltaCopies") == 0L &&
                deltaValue(r, "stageDeltaChunks") == 0L, "NULL_GUARD_DEBIT");
        r.put("sourceReadCensus", Integer.valueOf(0));
        r.put("rejections", Long.valueOf(3));
        r.put("reason", "EXPECTED_REJECTION");
        check(factory.before.equals(snapshot(factory.data)), "NULL_GUARD_INPUT_CHANGED");
    }

    private static void groupGuard(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> r) throws Exception {
        for (int i = 0; i < 640; i++) {
            meter.charge(1);
            meter.method("finishGroup", new Class<?>[0]);
        }
        check(number(meter, "units") == 640L && number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 1L &&
                !Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open")),
                "GROUP_GUARD_SETUP_CENSUS");
        Object measures = CohortTerminalTelemetryStudy.field(meter.meter, "groupMeasures");
        check(measures instanceof List && ((List<?>)measures).size() == 640, "GROUP_GUARD_MEASURES");
        CountingMap data = new CountingMap();
        String before = snapshot(data);
        CohortTerminalTelemetryStudy.SetupResult synthetic =
            new CohortTerminalTelemetryStudy.SetupResult(data, 0L, 0L, 0, 0);
        long[] beforeStage = debit(meter);
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            CurrentAssemblyMetadata.cohortBeforeBody(meter, synthetic, new ArrayList<String>());
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            r.put("operationNanos", Long.valueOf(elapsed(started, System.nanoTime())));
            delta(r, "stageDelta", beforeStage, debit(meter));
        }
        expectError(r, failure, "IOException:native wrapper exceeded 640 measured groups");
        check(data.getCount == 0, "GROUP_GUARD_READ_SOURCE");
        check(before.equals(snapshot(data)), "GROUP_GUARD_INPUT_CHANGED");
        check(deltaValue(r, "stageDeltaUnits") == 0L && deltaValue(r, "stageDeltaCopies") == 0L &&
                deltaValue(r, "stageDeltaChunks") == 0L, "GROUP_GUARD_DEBIT");
        r.put("sourceReads", Integer.valueOf(0));
        r.put("sourceReadObserved", Integer.valueOf(data.getCount));
        r.put("sourceReadCensus", Integer.valueOf(0));
        r.put("inputUnchanged", Boolean.TRUE);
        r.put("rejections", Long.valueOf(1));
        r.put("reason", "EXPECTED_REJECTION");
    }

    private static final class FixtureFactory implements CurrentV2InputSetup.Factory {
        final String caseName;
        Map<String,Object> data;
        String before;
        int reads;
        FixtureFactory(String name) { caseName = name; }
        public String schemaTag() { reads++; return CohortTerminalTelemetryInputV2.schemaTag(); }
        public String contextSha256() { reads++; return CohortTerminalTelemetryInputV2.contextSha256(); }
        public int dataEntries() { reads++; return CohortTerminalTelemetryInputV2.dataEntries(); }
        public Map<String,Object> dataJson() {
            reads++;
            data = CohortTerminalTelemetryInputV2.dataJson();
            mutate(caseName, data);
            before = snapshot(data);
            return data;
        }
    }

    private static void mutate(String name, Map<String,Object> data) {
        if ("prior_false".equals(name)) {
            asMap(asMap(asMap(data).get("prior")).get("trialDisposition")).put("orderChanged", Boolean.FALSE);
        } else if ("capture_false".equals(name)) {
            asMap(asMap(asMap(data).get("capture")).get("trialDisposition")).put("orderChanged", Boolean.FALSE);
        } else if ("requested_mismatch".equals(name)) {
            Map<String,Object> priorTrial = asMap(asMap(asMap(data).get("prior")).get("trialDisposition"));
            Map<String,Object> captureTrial = asMap(asMap(asMap(data).get("capture")).get("trialDisposition"));
            List<?> prior = asList(priorTrial.get("requestedNetIds"));
            List<?> capture = asList(captureTrial.get("requestedNetIds"));
            check(prior != capture, "TRIAL_REQUESTED_ALIAS");
            swap(asMutableList(prior), 0, 1);
            swap(asMutableList(capture), 0, 1);
        }
    }

    private static Map<String,Object> record(String name) {
        Map<String,Object> r = new LinkedHashMap<String,Object>();
        r.put("name", name); r.put("status", "FAIL"); r.put("phase", "preflight");
        r.put("reason", "INCOMPLETE"); r.put("expectedError", expectedError(name));
        r.put("observedError", "");
        r.put("units", Long.valueOf(0)); r.put("copiedEntries", Long.valueOf(0));
        r.put("copyChunks", Long.valueOf(0)); r.put("groups", Long.valueOf(0));
        r.put("priorGroups", Long.valueOf(0)); r.put("groupUnits", Long.valueOf(0));
        r.put("maxGroupUnits", Long.valueOf(0)); r.put("maxGroupNanos", Long.valueOf(0));
        r.put("meterOpen", Boolean.FALSE);
        r.put("setupDeltaUnits", Long.valueOf(0)); r.put("setupDeltaCopies", Long.valueOf(0));
        r.put("setupDeltaChunks", Long.valueOf(0)); r.put("stageDeltaUnits", Long.valueOf(0));
        r.put("stageDeltaCopies", Long.valueOf(0)); r.put("stageDeltaChunks", Long.valueOf(0));
        r.put("operationNanos", Long.valueOf(0)); r.put("totalElapsedNanos", Long.valueOf(0));
        r.put("openGroupElapsedNanos", Long.valueOf(0)); r.put("inputUnchanged", Boolean.FALSE);
        r.put("outputDetached", Boolean.FALSE); r.put("rejections", Long.valueOf(0));
        r.put("sourceReads", Long.valueOf(0)); r.put("sourceReadObserved", null);
        r.put("sourceReadCensus", Long.valueOf(0)); r.put("payload", null);
        r.put("discarded", Boolean.FALSE); r.put("finishCalls", Long.valueOf(0));
        return r;
    }

    private static String expectedError(String name) {
        if ("wrong_capture_order".equals(name) || "requested_mismatch".equals(name))
            return "IOException:COHORT_REQUESTED_VECTOR";
        if ("duplicate_capture".equals(name)) return "IOException:COHORT_VECTOR_DUPLICATE";
        if ("missing_capture".equals(name)) return "IOException:COHORT_NOT_IN_CAPTURE";
        if ("prior_false".equals(name) || "capture_false".equals(name)) return "IOException:V2_SETUP_POLICY";
        if ("null_arguments".equals(name)) return "IOException:METADATA_ARGUMENTS";
        if ("group_guard_before_read".equals(name))
            return "IOException:native wrapper exceeded 640 measured groups";
        return "";
    }

    private static String[] requestedFor(String name) {
        String[] values = EXPECTED_REQUESTED.clone();
        if ("requested_mismatch".equals(name)) {
            String value = values[0]; values[0] = values[1]; values[1] = value;
        }
        return values;
    }

    private static void assertPartialSetupBound(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        check(number(meter, "units") <= 10007L && number(meter, "copiedEntries") <= 478L &&
                number(meter, "copyChunks") <= 3L && number(meter, "groups") <= 1L,
                "NATIVE_PARTIAL_SETUP_BOUND");
    }

    private static void verifyPayload(Map<String,Object> payload) {
        check(payload != null && payload.getClass() == LinkedHashMap.class && payload.size() == 2,
                "PROJECTION_SHAPE");
        check(listEquals(asList(payload.get("netIds")), PAIR), "PROJECTION_NET_IDS");
        check(listEquals(asList(payload.get("requestedNetIds")), EXPECTED_REQUESTED),
                "PROJECTION_REQUESTED_IDS");
    }

    private static void expectError(Map<String,Object> r, Throwable failure, String expected) {
        if (failure == null) throw new AssertionError("EXPECTED_ERROR_MISSING:" + expected);
        String observed = errorText(failure);
        r.put("observedError", observed);
        if (!expected.equals(observed)) throw new AssertionError("ERROR_MISMATCH:" + expected + ":" + observed);
    }

    private static void delta(Map<String,Object> r, String prefix, long[] before, long[] after) {
        check(after[0] >= before[0] && after[1] >= before[1] && after[2] >= before[2], "NEGATIVE_DELTA");
        r.put(prefix + "Units", Long.valueOf(after[0] - before[0]));
        r.put(prefix + "Copies", Long.valueOf(after[1] - before[1]));
        r.put(prefix + "Chunks", Long.valueOf(after[2] - before[2]));
    }

    private static long deltaValue(Map<String,Object> r, String key) {
        return ((Number)r.get(key)).longValue();
    }

    private static long[] debit(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        return new long[] {number(meter, "units"), number(meter, "copiedEntries"), number(meter, "copyChunks")};
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter, String name) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(meter.meter, name)).longValue();
    }

    private static void nativeFields(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> r) throws Exception {
        String[] keys = {"units", "copiedEntries", "copyChunks", "groups", "priorGroups",
            "groupUnits", "maxGroupUnits", "maxGroupNanos"};
        for (String key : keys) r.put(key, Long.valueOf(number(meter, key)));
        boolean open = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open"));
        r.put("meterOpen", Boolean.valueOf(open));
        long now = System.nanoTime();
        long totalElapsed = elapsed(number(meter, "started"), now);
        r.put("totalElapsedNanos", Long.valueOf(totalElapsed));
        long openElapsed = open ? elapsed(number(meter, "groupStarted"), now) : 0L;
        r.put("openGroupElapsedNanos", Long.valueOf(openElapsed));
        check(number(meter, "units") <= 10271L && number(meter, "copiedEntries") <= 1048576L &&
                number(meter, "copyChunks") <= 67L && number(meter, "groups") <= 640L &&
                number(meter, "priorGroups") <= 640L && number(meter, "groupUnits") <= 32768L &&
                number(meter, "maxGroupUnits") <= 32768L &&
                number(meter, "maxGroupNanos") <= 5000000000L && openElapsed <= 5000000000L &&
                ((Number)r.get("operationNanos")).longValue() <= 90000000000L &&
                totalElapsed <= 90000000000L,
                "NATIVE_TIMING_BOUND");
    }

    private static long elapsed(long start, long end) {
        long value = end - start;
        check(value >= 0L, "NEGATIVE_CLOCK");
        return value;
    }

    private static Map<String,Object> asMap(Object value) {
        check(value instanceof Map, "EXPECTED_MAP");
        @SuppressWarnings("unchecked") Map<String,Object> result = (Map<String,Object>)value;
        return result;
    }

    private static List<?> asList(Object value) {
        check(value instanceof List, "EXPECTED_LIST");
        return (List<?>)value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asMutableList(List<?> value) { return (List<Object>)value; }

    private static List<String> copyStrings(List<?> source) {
        List<String> copy = new ArrayList<String>(source.size());
        for (Object value : source) {
            check(value instanceof String, "CAPTURED_ID_TYPE");
            copy.add((String)value);
        }
        return copy;
    }

    private static void swap(List<Object> values, int first, int second) {
        Object value = values.get(first); values.set(first, values.get(second)); values.set(second, value);
    }

    private static boolean listEquals(List<?> actual, String[] expected) {
        if (actual == null || actual.size() != expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (!expected[i].equals(actual.get(i))) return false;
        return true;
    }

    private static IdentityHashMap<Object,Boolean> containers(Object value) {
        IdentityHashMap<Object,Boolean> found = new IdentityHashMap<Object,Boolean>();
        collect(value, found);
        return found;
    }

    private static Object[] inputIdentity(CohortTerminalTelemetryStudy.SetupResult setup) {
        Map<String,Object> root = asMap(setup.data);
        Map<String,Object> prior = asMap(root.get("prior"));
        Map<String,Object> priorTrial = asMap(prior.get("trialDisposition"));
        Map<String,Object> capture = asMap(root.get("capture"));
        Map<String,Object> captureTrial = asMap(capture.get("trialDisposition"));
        return new Object[] {setup.data, prior, priorTrial, priorTrial.get("cohortNetIds"),
            priorTrial.get("requestedNetIds"), capture, captureTrial, capture.get("cohortNetIds"),
            capture.get("capturedNetIds"), captureTrial.get("cohortNetIds"),
            captureTrial.get("requestedNetIds")};
    }

    private static boolean sameIdentity(Object[] before, Object[] after) {
        if (before.length != after.length) return false;
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) return false;
        return true;
    }

    private static void collectDistinct(Object value, IdentityHashMap<Object,Boolean> found) {
        if (!(value instanceof Map || value instanceof List)) return;
        check(!found.containsKey(value), "INPUT_CONTAINER_ALIAS");
        found.put(value, Boolean.TRUE);
        if (value instanceof Map) {
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) collectDistinct(entry.getValue(), found);
        } else {
            for (Object item : (List<?>)value) collectDistinct(item, found);
        }
    }

    private static void collect(Object value, IdentityHashMap<Object,Boolean> found) {
        if (!(value instanceof Map || value instanceof List) || found.put(value, Boolean.TRUE) != null) return;
        if (value instanceof Map) {
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) collect(entry.getValue(), found);
        } else {
            for (Object item : (List<?>)value) collect(item, found);
        }
    }

    private static boolean retains(Object value, IdentityHashMap<Object,Boolean> inputs) {
        if (!(value instanceof Map || value instanceof List)) return false;
        if (inputs.containsKey(value)) return true;
        if (value instanceof Map) {
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet())
                if (retains(entry.getValue(), inputs)) return true;
        } else {
            for (Object item : (List<?>)value) if (retains(item, inputs)) return true;
        }
        return false;
    }

    private static String snapshot(Object value) { return json(value); }

    private static String errorText(Throwable failure) {
        Throwable actual = failure;
        while (actual instanceof InvocationTargetException && ((InvocationTargetException)actual).getCause() != null)
            actual = ((InvocationTargetException)actual).getCause();
        return actual.getClass().getSimpleName() + ":" + String.valueOf(actual.getMessage());
    }

    private static String safeDescribe(Throwable failure) {
        String value = errorText(failure);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && i < 240; i++) {
            char c = value.charAt(i);
            out.append(c >= 32 && c <= 126 ? c : '?');
        }
        return out.toString();
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static final class CountingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = 1L;
        int getCount;
        @Override public Object get(Object key) { getCount++; return super.get(key); }
    }

    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        check(out.length() <= 1048576, "SUMMARY_BOUND");
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) {
        check(depth <= 6 && out.length() <= 1048576, "SUMMARY_BOUND");
        if (value == null) out.append("null");
        else if (value instanceof String) {
            out.append('"');
            String text = (String)value;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                check(c >= 32 && c <= 126, "SUMMARY_ASCII");
                if (c == '"' || c == '\\') out.append('\\');
                out.append(c);
            }
            out.append('"');
        } else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) {
                if (!first) out.append(','); first = false;
                write(entry.getKey(), out, depth + 1); out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('['); boolean first = true;
            for (Object item : (List<?>)value) {
                if (!first) out.append(','); first = false;
                write(item, out, depth + 1);
            }
            out.append(']');
        } else throw new AssertionError("SUMMARY_TYPE");
        check(out.length() <= 1048576, "SUMMARY_BOUND");
    }
}
