import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only actual-Meter prepayment canary; no owner result is constructed. */
public final class CaptureAllowanceCanary {
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String ROUTING =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String INPUTS = ROUTING + "$Inputs";
    private static final String METER = ROUTING + "$Meter";
    private static final String INPUT_SETUP =
            "com.lushprojects.circuitjs1.client.CurrentV2InputSetup";
    private static final String PROFILE = "COHORT_TERMINAL_TELEMETRY_V2";
    private static final String RUN_ID = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String SHA_A = "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb" +
            "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb";
    private static final String SHA_C = "cccccccccccccccc" + "cccccccccccccccc" +
            "cccccccccccccccc" + "cccccccccccccccc";
    private static final String[] INPUT_FIELD_NAMES = {
        "runId", "manifestSha", "wireSha", "data"
    };
    private static final long COPY_LIMIT = 1048576L;
    private static int cases;
    private static int checks;
    private static final List<String> failures = new ArrayList<String>();

    private CaptureAllowanceCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3 || args[1] == null || args[2] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        Path suppliedOutput = Paths.get(args[1]);
        if (!suppliedOutput.isAbsolute())
            throw new IllegalArgumentException("CANARY_OUTPUT_NOT_ABSOLUTE");
        Path output = suppliedOutput.toRealPath();
        GuardedLoader loader = new GuardedLoader(classpathUrls(args[2]));
        try {
            Harness harness = new Harness(loader, output);
            check(harness.parent != null, "ACTUAL_PARENT_CONSTRUCTED");
            Attempt parsed = invoke(harness.parse, null,
                    new Object[] {validArguments(output.toString())});
            check(parsed.failure == null && parsed.value != null &&
                    parsed.value.getClass() == harness.argumentsType,
                    "ACTUAL_V2_ARGUMENTS_PARSED");
            if (parsed.failure == null && parsed.value != null) {
                if ("SMOKE".equals(args[0]))
                    testZeroCopySuccess(harness, parsed.value, "SMOKE");
                else
                    runSuite(harness, parsed.value);
            }
            check(loader.deniedAttempts.isEmpty(),
                    "GENERATED_FACTORY_LOAD_ATTEMPTS_ZERO");
            printSummary(args[0], loader.deniedAttempts.size());
        } finally {
            loader.close();
        }
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void runSuite(Harness harness, Object arguments) throws Exception {
        testZeroCopySuccess(harness, arguments, "SUITE_ZERO_COPY");
        testThreeCopySuccess(harness, arguments);
        testNullMeterBegin(harness);
        testNullRawMeterBegin(harness);
        testWrongRawMeterBegin(harness);
        testNullSetupBegin(harness, arguments);
        testNoCarrierBegin(harness, arguments);
        testExtraUnitBegin(harness, arguments);
        testReducedAllowanceBegin(harness, arguments);
        testRepeatedBegin(harness, arguments);
        testWrongRemainingMerge(harness, arguments);
        testWrongCaptureAllowanceMerge(harness, arguments);
        testOwnerNotAdoptedMerge(harness, arguments);
        testMissingReadCreditMerge(harness, arguments);
        testMismatchedOwnerCopiesMerge(harness, arguments);
        testCarrierBeforeFreshInputReader(harness, arguments);
        testGroup640BeginRefusal(harness, arguments);
        testZeroOpenBegin(harness, arguments);
        testNullSetupMerge(harness, arguments);
        testForeignCaptureBinding(harness);
    }

    private static void testZeroCopySuccess(Harness harness, Object arguments,
            String label) throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                label + "_BASELINE_AND_CARRIER_PRICE");
        Attempt begin = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        check(begin.failure == null && begin.value instanceof Integer &&
                ((Integer)begin.value).intValue() == remaining,
                label + "_RETURNS_EXACT_REMAINING_ALLOWANCE");
        syntheticOwnerBudget(harness, fixture.setupMeter);
        harness.charge(fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, fixture.setupMeter);
        Attempt merge = invoke(harness.merge, null,
                new Object[] {fixture.setupMeter, fixture.setupResult,
                    Integer.valueOf(remaining), Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance)});
        check(merge.failure == null && merge.value == null &&
                beforeMerge.copiedEntries == 0L && beforeMerge.copyChunks == 0 &&
                beforeMerge.priorGroups == 1 && beforeMerge.copyAllowance == remaining &&
                beforeMerge.open && beforeMerge.groupUnits == 5,
                label + "_ZERO_COPY_MERGE_SUCCEEDS");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(validMergedZeroCopyState(fixture, after) &&
                setupUnchanged(harness, fixture),
                label + "_CLOSED_COUNTER_REBASE_AND_INPUT_SNAPSHOT_UNCHANGED");
    }

    private static void testThreeCopySuccess(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_THREE_COPY_BASELINE_AND_CARRIER_PRICE");
        Attempt begin = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        check(begin.failure == null && begin.value instanceof Integer &&
                ((Integer)begin.value).intValue() == remaining,
                "SUITE_THREE_COPY_BEGIN_ALLOWANCE");
        harness.copy(fixture.setupMeter, 3);
        harness.finish(fixture.setupMeter);
        syntheticOwnerBudget(harness, fixture.setupMeter);
        harness.charge(fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, fixture.setupMeter);
        Attempt merge = invoke(harness.merge, null,
                new Object[] {fixture.setupMeter, fixture.setupResult,
                    Integer.valueOf(remaining), Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance)});
        check(merge.failure == null && merge.value == null &&
                beforeMerge.copiedEntries == 3L && beforeMerge.copyChunks == 1 &&
                beforeMerge.priorGroups == 1 && beforeMerge.copyAllowance == remaining &&
                beforeMerge.open && beforeMerge.groupUnits == 5,
                "SUITE_THREE_COPY_MERGE_SUCCEEDS");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(validMergedThreeCopyState(harness, fixture, after) &&
                setupUnchanged(harness, fixture),
                "SUITE_THREE_COPY_MERGED_PRICE_AND_OWNER_METADATA");
    }
    private static void testNullMeterBegin(Harness harness) throws Exception {
        cases++;
        Attempt result = invoke(harness.begin, null,
                new Object[] {null, null});
        check(exactIo(result.failure, "CAPTURE_ALLOWANCE_METER") &&
                result.value == null, "SUITE_NULL_METER_REJECTED_BEFORE_DEBIT");
    }

    private static void testNullRawMeterBegin(Harness harness) throws Exception {
        cases++;
        Object wrapper = harness.newSetupWrapper(harness.parent, null);
        Attempt result = invoke(harness.begin, null,
                new Object[] {wrapper, null});
        check(exactIo(result.failure, "CAPTURE_ALLOWANCE_METER") &&
                result.value == null, "SUITE_NULL_RAW_METER_REJECTED_BEFORE_DEBIT");
    }

    private static void testWrongRawMeterBegin(Harness harness) throws Exception {
        cases++;
        Object wrapper = harness.newSetupWrapper(harness.parent, new Object());
        Attempt result = invoke(harness.begin, null,
                new Object[] {wrapper, null});
        check(exactIo(result.failure, "CAPTURE_ALLOWANCE_METER") &&
                result.value == null, "SUITE_PLAIN_OBJECT_RAW_METER_REJECTED");
    }

    private static void testNullSetupBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_NULL_SETUP_CARRIER_PRICE");
        Attempt result = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, null});
        check(exactIo(result.failure, "SETUP_LEDGER_ARGUMENTS") &&
                result.value == null, "SUITE_NULL_SETUP_REJECTED_AFTER_THIRTY");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(openBeginFailureState(fixture, after, COPY_LIMIT) &&
                setupUnchanged(harness, fixture),
                "SUITE_NULL_SETUP_RETAINS_PREPAY_AND_COPY_BASE");
    }

    private static void testNoCarrierBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Snapshot before = new Snapshot(harness, fixture.setupMeter);
        check(baselineExact(fixture) && fixture.base.sameState(before) &&
                setupUnchanged(harness, fixture), "SUITE_NO_CARRIER_ACTUAL_BASELINE");
        Attempt result = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        check(exactIo(result.failure, "SETUP_LEDGER_CHANGED") &&
                result.value == null, "SUITE_ABSENT_CARRIER_REJECTED");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(after.meter == before.meter && after.units == 94L &&
                after.groups == 1 && after.copiedEntries == 7L && after.copyChunks == 3 &&
                after.copyAllowance == COPY_LIMIT && after.open && after.groupUnits == 30 &&
                after.maxGroupUnits == 64 && after.groupMeasureCount == 1 &&
                after.groupMeasures == before.groupMeasures && after.noOwnerWork() &&
                setupUnchanged(harness, fixture),
                "SUITE_ABSENT_CARRIER_PREPAY_WITHOUT_COUNTER_RESET");
    }

    private static void testExtraUnitBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_EXTRA_UNIT_CARRIER_PRICE");
        harness.charge(fixture.setupMeter, 1L);
        Attempt result = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        check(exactIo(result.failure, "SETUP_LEDGER_CHANGED") &&
                result.value == null, "SUITE_EXTRA_UNIT_AFTER_CARRIER_REJECTED");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(extraUnitBeginFailureState(fixture, after) &&
                setupUnchanged(harness, fixture),
                "SUITE_EXTRA_UNIT_RETAINED_WITHOUT_COUNTER_RESET");
    }

    private static void testReducedAllowanceBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_REDUCED_ALLOWANCE_CARRIER_PRICE");
        harness.setCopyAllowance(fixture.setupMeter, (int)COPY_LIMIT - 1);
        Attempt result = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        check(exactIo(result.failure, "SETUP_LEDGER_CHANGED") &&
                result.value == null, "SUITE_REDUCED_ALLOWANCE_REJECTED");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(reducedAllowanceFailureState(fixture, after) &&
                setupUnchanged(harness, fixture),
                "SUITE_REDUCED_ALLOWANCE_AND_COUNTERS_RETAINED");
    }

    private static void testRepeatedBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_REPEAT_BEGIN_CARRIER_PRICE");
        Attempt first = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        check(first.failure == null && first.value instanceof Integer &&
                ((Integer)first.value).intValue() == remaining,
                "SUITE_REPEAT_BEGIN_FIRST_PHASE_SUCCEEDS");
        Attempt second = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        check(exactIo(second.failure, "SETUP_LEDGER_CHANGED") &&
                second.value == null, "SUITE_REPEAT_BEGIN_SECOND_PHASE_REJECTED");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(repeatedBeginFailureState(fixture, after, remaining) &&
                setupUnchanged(harness, fixture),
                "SUITE_REPEAT_BEGIN_DOES_NOT_RESET_FIRST_REBASE");
    }

    private static void testWrongRemainingMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments, "SUITE_WRONG_REMAINING");
        syntheticOwnerBudget(harness, state.fixture.setupMeter);
        harness.charge(state.fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, state.fixture.setupMeter);
        Attempt result = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, state.fixture.setupResult,
                    Integer.valueOf(state.remaining - 1),
                    Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance)});
        check(exactIo(result.failure, "ORIGINAL_LEDGER") && result.value == null &&
                beforeMerge.ownerStarted && !beforeMerge.ownerActive &&
                beforeMerge.ownerAdopted && beforeMerge.priorGroups == 1 &&
                beforeMerge.copyAllowance == state.remaining && beforeMerge.groupUnits == 5,
                "SUITE_WRONG_REMAINING_MERGE_REJECTED");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(mergeFailureState(state.fixture, after, true, 5),
                "SUITE_WRONG_REMAINING_PRESERVES_PREPAID_OWNER_STATE");
    }

    private static void testWrongCaptureAllowanceMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments,
                "SUITE_WRONG_CAPTURE_ALLOWANCE");
        syntheticOwnerBudget(harness, state.fixture.setupMeter);
        harness.charge(state.fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, state.fixture.setupMeter);
        Attempt result = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, state.fixture.setupResult,
                    Integer.valueOf(state.remaining),
                    Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance - 1)});
        check(exactIo(result.failure, "ORIGINAL_LEDGER") && result.value == null &&
                beforeMerge.ownerStarted && !beforeMerge.ownerActive &&
                beforeMerge.ownerAdopted && beforeMerge.priorGroups == 1 &&
                beforeMerge.copyAllowance == state.remaining && beforeMerge.groupUnits == 5,
                "SUITE_WRONG_CAPTURE_ALLOWANCE_MERGE_REJECTED");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(mergeFailureState(state.fixture, after, true, 5),
                "SUITE_WRONG_CAPTURE_ALLOWANCE_PRESERVES_PREPAID_OWNER_STATE");
    }

    private static void testOwnerNotAdoptedMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments,
                "SUITE_OWNER_NOT_ADOPTED");
        harness.charge(state.fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, state.fixture.setupMeter);
        // Supply one expected group scalar to reach the live no-adoption guard; no
        // native owner-budget credit or owner result is created in this case.
        Attempt result = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, state.fixture.setupResult,
                    Integer.valueOf(state.remaining),
                    Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(1), Integer.valueOf(beforeMerge.copyAllowance)});
        check(exactIo(result.failure, "ORIGINAL_LEDGER") && result.value == null &&
                !beforeMerge.ownerStarted && !beforeMerge.ownerActive &&
                !beforeMerge.ownerAdopted && beforeMerge.priorGroups == 0 &&
                beforeMerge.groupUnits == 5,
                "SUITE_OWNER_NOT_ADOPTED_MERGE_REJECTED");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(mergeFailureState(state.fixture, after, false, 5),
                "SUITE_OWNER_NOT_ADOPTED_HAS_NO_SYNTHETIC_OWNER_FLAGS");
    }

    private static void testMissingReadCreditMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments,
                "SUITE_MISSING_READ_CREDIT");
        syntheticOwnerBudget(harness, state.fixture.setupMeter);
        Snapshot beforeMerge = new Snapshot(harness, state.fixture.setupMeter);
        Attempt result = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, state.fixture.setupResult,
                    Integer.valueOf(state.remaining),
                    Long.valueOf(beforeMerge.copiedEntries),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance)});
        check(exactIo(result.failure, "ORIGINAL_LEDGER") && result.value == null &&
                beforeMerge.ownerStarted && !beforeMerge.ownerActive &&
                beforeMerge.ownerAdopted && beforeMerge.priorGroups == 1 &&
                !beforeMerge.open && beforeMerge.groupUnits == 30,
                "SUITE_MISSING_FIVE_READ_CREDIT_REJECTED");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(mergeFailureState(state.fixture, after, true, 0),
                "SUITE_MISSING_FIVE_READ_CREDIT_RETAINS_THIRTY_TWO_GROUP");
    }

    private static void testMismatchedOwnerCopiesMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments,
                "SUITE_MISMATCHED_OWNER_COPIES");
        syntheticOwnerBudget(harness, state.fixture.setupMeter);
        harness.charge(state.fixture.setupMeter, 5L);
        Snapshot beforeMerge = new Snapshot(harness, state.fixture.setupMeter);
        Attempt result = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, state.fixture.setupResult,
                    Integer.valueOf(state.remaining),
                    Long.valueOf(beforeMerge.copiedEntries + 1L),
                    Integer.valueOf(beforeMerge.copyChunks),
                    Integer.valueOf(beforeMerge.priorGroups),
                    Integer.valueOf(beforeMerge.copyAllowance)});
        check(exactIo(result.failure, "ORIGINAL_LEDGER") && result.value == null &&
                beforeMerge.copiedEntries == 0L && beforeMerge.copyChunks == 0 &&
                beforeMerge.ownerStarted && !beforeMerge.ownerActive &&
                beforeMerge.ownerAdopted && beforeMerge.priorGroups == 1,
                "SUITE_MISMATCHED_OWNER_COPIES_REJECTED");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(mergeFailureState(state.fixture, after, true, 5),
                "SUITE_MISMATCHED_OWNER_COPIES_NOT_COMMITTED");
    }
    private static void testCarrierBeforeFreshInputReader(Harness harness,
            Object arguments) throws Exception {
        cases++;
        Object setupMeter = harness.newSetupMeter(harness.parent);
        Attempt carrier = invoke(harness.runtimeInputs, harness.parent,
                new Object[] {arguments, setupMeter});
        Snapshot afterCarrier = new Snapshot(harness, setupMeter);
        check(carrier.failure == null && carrier.value != null &&
                carrier.value.getClass() == harness.inputsType &&
                afterCarrier.units == 10L && afterCarrier.copiedEntries == 4L &&
                afterCarrier.copyChunks == 1 && afterCarrier.groups == 1 &&
                !afterCarrier.open && afterCarrier.groupUnits == 10 &&
                afterCarrier.maxGroupUnits == 10 && afterCarrier.copyAllowance == COPY_LIMIT &&
                afterCarrier.groupMeasureCount == 1 && afterCarrier.noOwnerWork(),
                "SUITE_INPUT_READER_STARTS_AFTER_CARRIER_ONLY");
        Object nativeMeter = harness.nativeMeter(setupMeter);
        long started = number(harness.nativeMeterType, nativeMeter, "started");
        Class<?> inputSetupType = Class.forName(INPUT_SETUP, false, harness.loader);
        Class<?> factoryType = Class.forName(INPUT_SETUP + "$Factory", false, harness.loader);
        FactoryCallLog calls = new FactoryCallLog();
        Object factory = Proxy.newProxyInstance(harness.loader,
                new Class<?>[] {factoryType}, calls);
        Method read = inputSetupType.getDeclaredMethod("read", harness.setupMeterType,
                long.class, String.class, factoryType);
        read.setAccessible(true);
        Attempt result = invoke(read, null,
                new Object[] {setupMeter, Long.valueOf(started), SHA_C, factory});
        check(exactIo(result.failure, "V2_SETUP_METER_STATE") &&
                result.value == null && calls.calls == 0,
                "SUITE_INPUT_READER_REJECTS_CARRIER_PREPAY_BEFORE_FACTORY");
        Snapshot afterRead = new Snapshot(harness, setupMeter);
        check(afterRead.units == 28L && afterRead.copiedEntries == 4L &&
                afterRead.copyChunks == 1 && afterRead.groups == 1 &&
                afterRead.open && afterRead.groupUnits == 18 &&
                afterRead.maxGroupUnits == 10 && afterRead.copyAllowance == COPY_LIMIT &&
                afterRead.groupMeasureCount == 1 && afterRead.noOwnerWork(),
                "SUITE_INPUT_READER_FAILURE_RETAINS_EIGHTEEN_PREPAY");
    }

    private static void testGroup640BeginRefusal(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 638);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments) &&
                fixture.base.units == 702L && fixture.base.groups == 639,
                "SUITE_GROUP_640_BASELINE_AND_CARRIER");
        Snapshot beforeBegin = new Snapshot(harness, fixture.setupMeter);
        Attempt begin = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        check(begin.failure != null && begin.failure.getClass() == IOException.class &&
                "native wrapper exceeded 640 measured groups".equals(
                        begin.failure.getMessage()) && begin.value == null,
                "SUITE_GROUP_640_BEGIN_REFUSES_NEXT_GROUP");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(beforeBegin.sameState(after) && setupUnchanged(harness, fixture) &&
                after.units == 712L && after.groups == 640 &&
                after.copiedEntries == 11L && after.copyChunks == 4 &&
                after.maxGroupUnits == 64 && !after.open && after.groupUnits == 10 &&
                after.copyAllowance == COPY_LIMIT && after.noOwnerWork(),
                "SUITE_GROUP_640_REFUSAL_PREDEBIT_STATE_UNCHANGED");
    }

    private static void testZeroOpenBegin(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        check(carrierReady(harness, fixture, carrier, arguments),
                "SUITE_ZERO_OPEN_CARRIER_PRICE");
        harness.nativeCall(fixture.setupMeter, "reserve",
                new Class<?>[] {long.class}, Long.valueOf(0L));
        Attempt result = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        check(result.failure == null && result.value instanceof Integer &&
                ((Integer)result.value).intValue() == remaining,
                "SUITE_ZERO_OPEN_BEGIN_ACCEPTS_EQUIVALENT_LEDGER");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(validBeginState(fixture, after, remaining) &&
                setupUnchanged(harness, fixture),
                "SUITE_ZERO_OPEN_BEGIN_CLOSES_WITH_STANDARD_PRICE");
    }

    private static void testNullSetupMerge(Harness harness, Object arguments)
            throws Exception {
        cases++;
        BeginFixture state = validBeginFixture(harness, arguments, "SUITE_NULL_MERGE_SETUP");
        Attempt merge = invoke(harness.merge, null,
                new Object[] {state.fixture.setupMeter, null, Integer.valueOf(state.remaining),
                    Long.valueOf(0L), Integer.valueOf(0), Integer.valueOf(1),
                    Integer.valueOf(state.remaining)});
        check(exactIo(merge.failure, "SETUP_LEDGER_ARGUMENTS") && merge.value == null,
                "SUITE_NULL_MERGE_SETUP_REJECTED_AFTER_THIRTY_TWO");
        Snapshot after = new Snapshot(harness, state.fixture.setupMeter);
        check(nullMergeFailureState(state.fixture, after, state.remaining),
                "SUITE_NULL_MERGE_SETUP_RETAINS_THIRTY_TWO_OPEN");
    }

    private static void testForeignCaptureBinding(Harness harness) throws Exception {
        cases++;
        Object foreignParent = harness.newParent();
        Fixture fixture = standardFixture(harness, foreignParent, 0);
        Snapshot before = new Snapshot(harness, fixture.setupMeter);
        Attempt result = invoke(harness.captureWithSetup, null,
                new Object[] {harness.parent, null, fixture.setupMeter, fixture.setupResult});
        check(exactIo(result.failure, "CAPTURE_ALLOWANCE_METER") &&
                result.value == null, "SUITE_CAPTURE_FOREIGN_PARENT_REJECTED");
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        check(before.sameState(after) && setupUnchanged(harness, fixture) &&
                after.noOwnerWork(), "SUITE_CAPTURE_FOREIGN_PARENT_BEFORE_DEBIT_OR_OWNER");
    }

    private static BeginFixture validBeginFixture(Harness harness, Object arguments,
            String label) throws Exception {
        Fixture fixture = standardFixture(harness, harness.parent, 0);
        Attempt carrier = carrier(harness, fixture, arguments);
        boolean carrierOk = carrierReady(harness, fixture, carrier, arguments);
        Attempt begin = invoke(harness.begin, null,
                new Object[] {fixture.setupMeter, fixture.setupResult});
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        check(carrierOk, label + "_BASELINE_AND_CARRIER");
        check(begin.failure == null && begin.value instanceof Integer &&
                ((Integer)begin.value).intValue() == remaining,
                label + "_BEGIN_ALLOWANCE");
        return new BeginFixture(fixture, remaining);
    }

    private static Fixture standardFixture(Harness harness, Object parent,
            int additionalOneUnitGroups) throws Exception {
        Object setupMeter = harness.newSetupMeter(parent);
        harness.copy(setupMeter, 2);
        harness.copy(setupMeter, 2);
        harness.copy(setupMeter, 3);
        harness.charge(setupMeter, 57L);
        harness.finish(setupMeter);
        for (int i = 0; i < additionalOneUnitGroups; i++) {
            harness.charge(setupMeter, 1L);
            harness.finish(setupMeter);
        }
        Snapshot base = new Snapshot(harness, setupMeter);
        Map<String,Object> data = new LinkedHashMap<String,Object>();
        Object setupResult = harness.newSetupResult(data, base.units,
                base.copiedEntries, base.groups, base.copyChunks);
        return new Fixture(parent, setupMeter, data, setupResult, base,
                additionalOneUnitGroups, harness.outputPath);
    }

    private static Attempt carrier(Harness harness, Fixture fixture, Object arguments)
            throws Exception {
        return invoke(harness.runtimeInputs, fixture.parent,
                new Object[] {arguments, fixture.setupMeter});
    }

    private static boolean carrierReady(Harness harness, Fixture fixture,
            Attempt result, Object arguments) {
        Snapshot after = new Snapshot(harness, fixture.setupMeter);
        return baselineExact(fixture) && result.failure == null &&
                result.value != null && result.value.getClass() == harness.inputsType &&
                exactInputsFields(harness.inputsType) &&
                mappedField(harness.inputsType, result.value, "runId", String.class,
                        harness.argumentsType, arguments, "runId", RUN_ID) &&
                mappedField(harness.inputsType, result.value, "manifestSha", String.class,
                        harness.argumentsType, arguments, "sourceManifestSha256", SHA_A) &&
                mappedField(harness.inputsType, result.value, "wireSha", String.class,
                        harness.argumentsType, arguments, "wireContractSha256", SHA_B) &&
                mappedField(harness.inputsType, result.value, "data", Path.class,
                        harness.argumentsType, arguments, "outputDir", fixture.outputPath) &&
                after.units == fixture.base.units + 10L &&
                after.copiedEntries == fixture.base.copiedEntries + 4L &&
                after.copyChunks == fixture.base.copyChunks + 1 &&
                after.groups == fixture.base.groups + 1 && !after.open &&
                after.groupUnits == 10 &&
                after.maxGroupUnits == Math.max(fixture.base.maxGroupUnits, 10) &&
                after.copyAllowance == COPY_LIMIT &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 1 &&
                after.groupMeasures == fixture.base.groupMeasures &&
                after.noOwnerWork() && setupUnchanged(harness, fixture);
    }

    private static boolean baselineExact(Fixture fixture) {
        long expectedUnits = 64L + fixture.additionalOneUnitGroups;
        int expectedGroups = 1 + fixture.additionalOneUnitGroups;
        return fixture.base.units == expectedUnits &&
                fixture.base.copiedEntries == 7L && fixture.base.copyChunks == 3 &&
                fixture.base.groups == expectedGroups &&
                fixture.base.groupUnits == (fixture.additionalOneUnitGroups == 0 ? 64 : 1) &&
                fixture.base.maxGroupUnits == 64 && !fixture.base.open &&
                fixture.base.copyAllowance == COPY_LIMIT &&
                fixture.base.groupMeasureCount == expectedGroups &&
                fixture.base.noOwnerWork();
    }

    private static boolean validBeginState(Fixture fixture, Snapshot after,
            int remaining) {
        return after.units == fixture.base.units + 40L &&
                after.copiedEntries == 0L && after.copyChunks == 0 &&
                after.groups == fixture.base.groups + 2 && !after.open &&
                after.groupUnits == 30 && after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == remaining &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 2 &&
                after.groupMeasures == fixture.base.groupMeasures &&
                after.noOwnerWork();
    }

    private static boolean openBeginFailureState(Fixture fixture, Snapshot after,
            long allowance) {
        return after.units == fixture.base.units + 40L &&
                after.copiedEntries == fixture.base.copiedEntries + 4L &&
                after.copyChunks == fixture.base.copyChunks + 1 &&
                after.groups == fixture.base.groups + 1 && after.open &&
                after.groupUnits == 30 &&
                after.maxGroupUnits == Math.max(fixture.base.maxGroupUnits, 10) &&
                after.copyAllowance == allowance &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 1 &&
                after.groupMeasures == fixture.base.groupMeasures && after.noOwnerWork();
    }

    private static boolean extraUnitBeginFailureState(Fixture fixture, Snapshot after) {
        return after.units == fixture.base.units + 41L &&
                after.copiedEntries == fixture.base.copiedEntries + 4L &&
                after.copyChunks == fixture.base.copyChunks + 1 &&
                after.groups == fixture.base.groups + 1 && after.open &&
                after.groupUnits == 31 &&
                after.maxGroupUnits == Math.max(fixture.base.maxGroupUnits, 10) &&
                after.copyAllowance == COPY_LIMIT &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 1 &&
                after.groupMeasures == fixture.base.groupMeasures && after.noOwnerWork();
    }

    private static boolean reducedAllowanceFailureState(Fixture fixture,
            Snapshot after) {
        return openBeginFailureState(fixture, after, COPY_LIMIT - 1L);
    }

    private static boolean repeatedBeginFailureState(Fixture fixture, Snapshot after,
            int remaining) {
        return after.units == fixture.base.units + 70L &&
                after.copiedEntries == 0L && after.copyChunks == 0 &&
                after.groups == fixture.base.groups + 2 && after.open &&
                after.groupUnits == 30 && after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == remaining &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 2 &&
                after.groupMeasures == fixture.base.groupMeasures && after.noOwnerWork();
    }

    private static boolean mergeFailureState(Fixture fixture, Snapshot after,
            boolean adopted, int readCredit) {
        int remaining = (int)(COPY_LIMIT - (fixture.base.copiedEntries + 4L));
        boolean priorOwner = adopted ? after.ownerStarted && !after.ownerActive &&
                after.ownerAdopted && after.priorGroups == 1 && after.inFlightOwnerGroups == 0 :
                !after.ownerStarted && !after.ownerActive && !after.ownerAdopted &&
                after.priorGroups == 0 && after.inFlightOwnerGroups == 0;
        return after.units == fixture.base.units + 40L + readCredit + 32L &&
                after.copiedEntries == 0L && after.copyChunks == 0 &&
                after.groups == fixture.base.groups + 2 && after.open &&
                after.groupUnits == readCredit + 32 &&
                after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == remaining &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 2 &&
                after.groupMeasures == fixture.base.groupMeasures && priorOwner &&
                after.sessionConstructorEntries == 0;
    }

    private static boolean validMergedZeroCopyState(Fixture fixture, Snapshot after) {
        return after.units == fixture.base.units + 77L &&
                after.copiedEntries == fixture.base.copiedEntries + 4L &&
                after.copyChunks == fixture.base.copyChunks + 1 &&
                after.groups == fixture.base.groups + 3 && !after.open &&
                after.groupUnits == 37 &&
                after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == (int)COPY_LIMIT &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 3 &&
                after.groupMeasures == fixture.base.groupMeasures &&
                after.ownerStarted && !after.ownerActive && after.ownerAdopted &&
                after.priorGroups == 1 && after.inFlightOwnerGroups == 0 &&
                after.sessionConstructorEntries == 0;
    }
    private static boolean validMergedThreeCopyState(Harness harness, Fixture fixture,
            Snapshot after) {
        return after.units == fixture.base.units + 80L &&
                after.copiedEntries == fixture.base.copiedEntries + 4L + 3L &&
                after.copyChunks == fixture.base.copyChunks + 1 + 1 &&
                after.groups == fixture.base.groups + 4 && !after.open &&
                after.groupUnits == 37 &&
                after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == (int)COPY_LIMIT &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 4 &&
                after.groupMeasures == fixture.base.groupMeasures &&
                after.ownerStarted && !after.ownerActive && after.ownerAdopted &&
                after.priorGroups == 1 && after.inFlightOwnerGroups == 0 &&
                after.sessionConstructorEntries == 0 &&
                setupUnchanged(harness, fixture);
    }

    private static boolean nullMergeFailureState(Fixture fixture, Snapshot after,
            int remaining) {
        return after.units == fixture.base.units + 72L &&
                after.copiedEntries == 0L && after.copyChunks == 0 &&
                after.groups == fixture.base.groups + 2 && after.open &&
                after.groupUnits == 32 && after.maxGroupUnits == fixture.base.maxGroupUnits &&
                after.copyAllowance == remaining &&
                after.groupMeasureCount == fixture.base.groupMeasureCount + 2 &&
                after.groupMeasures == fixture.base.groupMeasures && after.noOwnerWork();
    }

    private static boolean setupUnchanged(Harness harness, Fixture fixture) {
        return setupValues(harness, fixture.setupResult, fixture.data, fixture.base.units,
                fixture.base.copiedEntries, fixture.base.groups, fixture.base.copyChunks);
    }

    private static boolean setupValues(Harness harness, Object setupResult,
            Map<String,Object> expectedData, long units, long copies, int groups,
            int chunks) {
        return field(harness.setupResultType, setupResult, "data") == expectedData &&
                number(harness.setupResultType, setupResult, "units") == units &&
                number(harness.setupResultType, setupResult, "copiedEntries") == copies &&
                integer(harness.setupResultType, setupResult, "groups") == groups &&
                integer(harness.setupResultType, setupResult, "copyChunks") == chunks;
    }

    private static boolean exactInputsFields(Class<?> inputsType) {
        try {
            Field[] fields = inputsType.getDeclaredFields();
            if (fields.length != INPUT_FIELD_NAMES.length) return false;
            Set<String> expected = new HashSet<String>(Arrays.asList(INPUT_FIELD_NAMES));
            Set<String> actual = new HashSet<String>();
            for (Field field : fields) {
                if (!Modifier.isFinal(field.getModifiers())) return false;
                actual.add(field.getName());
                Class<?> expectedType = "data".equals(field.getName()) ?
                        Path.class : String.class;
                if (field.getType() != expectedType) return false;
            }
            return expected.equals(actual);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean mappedField(Class<?> inputsType, Object inputs,
            String inputName, Class<?> expectedType, Class<?> argsType, Object args,
            String argumentName, Object expectedValue) {
        if (inputs == null || args == null) return false;
        try {
            Field inputField = inputsType.getDeclaredField(inputName);
            Field argumentField = argsType.getDeclaredField(argumentName);
            inputField.setAccessible(true);
            argumentField.setAccessible(true);
            Object inputValue = inputField.get(inputs);
            Object argumentValue = argumentField.get(args);
            return inputField.getType() == expectedType &&
                    argumentField.getType() == expectedType &&
                    inputValue == argumentValue && expectedValue.equals(inputValue);
        } catch (ReflectiveOperationException failure) {
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static void syntheticOwnerBudget(Harness harness, Object setupMeter)
            throws Exception {
        harness.nativeCall(setupMeter, "beginOriginalOwner", new Class<?>[0]);
        harness.nativeCall(setupMeter, "admitOwnerGroup",
                new Class<?>[] {int.class}, Integer.valueOf(0));
        harness.nativeCall(setupMeter, "adoptOriginalOwner",
                new Class<?>[] {int.class}, Integer.valueOf(1));
    }

    private static boolean exactIo(Throwable failure, String message) {
        return failure != null && failure.getClass() == IOException.class &&
                message.equals(failure.getMessage());
    }

    private static Attempt invoke(Method method, Object receiver, Object[] arguments)
            throws Exception {
        try {
            return new Attempt(method.invoke(receiver, arguments), null);
        } catch (InvocationTargetException failure) {
            return new Attempt(null, unwrap(failure.getCause()));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof InvocationTargetException && cause.getCause() != null)
            cause = cause.getCause();
        return cause;
    }

    private static Object invokeValue(Method method, Object receiver, Object[] arguments)
            throws Exception {
        Attempt result = invoke(method, receiver, arguments);
        if (result.failure == null) return result.value;
        if (result.failure instanceof Exception) throw (Exception)result.failure;
        if (result.failure instanceof Error) throw (Error)result.failure;
        throw new RuntimeException(result.failure);
    }

    private static Object field(Class<?> type, Object object, String name) {
        if (type == null || object == null) return null;
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) {
            return null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static long number(Class<?> type, Object object, String name) {
        Object value = field(type, object, name);
        return value instanceof Number ? ((Number)value).longValue() : Long.MIN_VALUE;
    }

    private static int integer(Class<?> type, Object object, String name) {
        Object value = field(type, object, name);
        return value instanceof Number ? ((Number)value).intValue() : Integer.MIN_VALUE;
    }

    private static boolean bool(Class<?> type, Object object, String name) {
        return Boolean.TRUE.equals(field(type, object, name));
    }

    private static URL[] classpathUrls(String classpath) throws Exception {
        if (classpath == null || classpath.length() == 0)
            throw new IllegalArgumentException("CANARY_CLASSPATH_EMPTY");
        String[] entries = classpath.split(";", -1);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].length() == 0)
                throw new IllegalArgumentException("CANARY_CLASSPATH_EMPTY_ENTRY");
            urls[i] = Paths.get(entries[i]).toUri().toURL();
        }
        return urls;
    }

    private static String[] validArguments(String output) {
        return new String[] {PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C, output};
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) failures.add(label);
    }

    private static void printSummary(String mode, int deniedAttempts) {
        System.out.println("SYNTHETIC_CAPTURE_ALLOWANCE_PREPAYMENT_COMPONENT_ONLY mode=" +
                mode + " cases=" + cases + " checks=" + checks +
                " deniedFactoryLoads=" + deniedAttempts +
                " failures=" + failures.size() +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
        for (int i = 0; i < failures.size(); i++)
            System.out.println("failure" + i + "=" + failures.get(i));
    }

    private static final class Harness {
        final GuardedLoader loader;
        final Path outputPath;
        final Class<?> studyType;
        final Class<?> argumentsType;
        final Class<?> inputsType;
        final Class<?> parentType;
        final Class<?> setupMeterType;
        final Class<?> setupResultType;
        final Class<?> nativeMeterType;
        final Constructor<?> parentConstructor;
        final Constructor<?> setupMeterConstructor;
        final Constructor<?> setupResultConstructor;
        final Method parse;
        final Method parentMeter;
        final Method runtimeInputs;
        final Method setupCharge;
        final Method setupCopy;
        final Method setupFinish;
        final Method begin;
        final Method merge;
        final Method captureWithSetup;
        final Object parent;

        Harness(GuardedLoader loader, Path outputPath) throws Exception {
            this.loader = loader;
            this.outputPath = outputPath;
            studyType = Class.forName(STUDY, false, loader);
            argumentsType = Class.forName(STUDY + "$Arguments", false, loader);
            inputsType = Class.forName(INPUTS, false, loader);
            parentType = Class.forName(STUDY + "$Parent", false, loader);
            setupMeterType = Class.forName(STUDY + "$SetupMeter", false, loader);
            setupResultType = Class.forName(STUDY + "$SetupResult", false, loader);
            nativeMeterType = Class.forName(METER, false, loader);

            Method parseMethod = argumentsType.getDeclaredMethod("parse", String[].class);
            parseMethod.setAccessible(true);
            parse = parseMethod;
            Constructor<?> parentCtor = parentType.getDeclaredConstructor();
            parentCtor.setAccessible(true);
            parentConstructor = parentCtor;
            Constructor<?> wrapperCtor =
                    setupMeterType.getDeclaredConstructor(parentType, Object.class);
            wrapperCtor.setAccessible(true);
            setupMeterConstructor = wrapperCtor;
            Constructor<?> resultCtor = setupResultType.getDeclaredConstructor(
                    Map.class, long.class, long.class, int.class, int.class);
            resultCtor.setAccessible(true);
            setupResultConstructor = resultCtor;
            Method parentMeterMethod = parentType.getDeclaredMethod("meter", long.class);
            parentMeterMethod.setAccessible(true);
            parentMeter = parentMeterMethod;
            Method runtime = parentType.getDeclaredMethod("runtimeInputs",
                    argumentsType, setupMeterType);
            runtime.setAccessible(true);
            runtimeInputs = runtime;
            Method charge = setupMeterType.getDeclaredMethod("charge", long.class);
            charge.setAccessible(true);
            setupCharge = charge;
            Method copy = setupMeterType.getDeclaredMethod("copy", int.class);
            copy.setAccessible(true);
            setupCopy = copy;
            Method finish = setupMeterType.getDeclaredMethod("finish");
            finish.setAccessible(true);
            setupFinish = finish;
            Method beginMethod = studyType.getDeclaredMethod("beginCaptureAllowance",
                    setupMeterType, setupResultType);
            beginMethod.setAccessible(true);
            begin = beginMethod;
            Method mergeMethod = studyType.getDeclaredMethod("mergeCaptureAllowance",
                    setupMeterType, setupResultType, int.class, long.class, int.class,
                    int.class, int.class);
            mergeMethod.setAccessible(true);
            merge = mergeMethod;
            Method captureMethod = studyType.getDeclaredMethod("captureWithSetup",
                    parentType, Object.class, setupMeterType, setupResultType);
            captureMethod.setAccessible(true);
            captureWithSetup = captureMethod;
            parent = newParent();
        }

        Object newParent() throws Exception {
            return parentConstructor.newInstance();
        }

        Object newSetupMeter(Object parent) throws Exception {
            Object nativeMeter = invokeValue(parentMeter, parent,
                    new Object[] {Long.valueOf(System.nanoTime())});
            return setupMeterConstructor.newInstance(parent, nativeMeter);
        }

        Object newSetupWrapper(Object parent, Object rawMeter) throws Exception {
            return setupMeterConstructor.newInstance(parent, rawMeter);
        }

        Object nativeMeter(Object setupMeter) {
            return field(setupMeterType, setupMeter, "meter");
        }

        Object newSetupResult(Map<String,Object> data, long units, long copies,
                int groups, int chunks) throws Exception {
            return setupResultConstructor.newInstance(data, Long.valueOf(units),
                    Long.valueOf(copies), Integer.valueOf(groups), Integer.valueOf(chunks));
        }

        void charge(Object setupMeter, long units) throws Exception {
            invokeValue(setupCharge, setupMeter, new Object[] {Long.valueOf(units)});
        }

        void copy(Object setupMeter, int count) throws Exception {
            invokeValue(setupCopy, setupMeter, new Object[] {Integer.valueOf(count)});
        }

        void finish(Object setupMeter) throws Exception {
            invokeValue(setupFinish, setupMeter, new Object[0]);
        }

        void setCopyAllowance(Object setupMeter, int value) throws Exception {
            nativeCall(setupMeter, "setCopyAllowance",
                    new Class<?>[] {int.class}, Integer.valueOf(value));
        }

        Object nativeCall(Object setupMeter, String name, Class<?>[] parameterTypes,
                Object... arguments) throws Exception {
            Object raw = nativeMeter(setupMeter);
            Method method = nativeMeterType.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return invokeValue(method, raw, arguments);
        }
    }

    private static final class Snapshot {
        final Object meter;
        final Object groupMeasures;
        final long started;
        final long units;
        final long copiedEntries;
        final long maxGroupNanos;
        final int copyChunks;
        final int groups;
        final int groupUnits;
        final int maxGroupUnits;
        final int copyAllowance;
        final int priorGroups;
        final int inFlightOwnerGroups;
        final int sessionConstructorEntries;
        final int groupMeasureCount;
        final boolean open;
        final boolean ownerStarted;
        final boolean ownerActive;
        final boolean ownerAdopted;

        Snapshot(Harness harness, Object setupMeter) {
            meter = harness.nativeMeter(setupMeter);
            Class<?> type = harness.nativeMeterType;
            started = number(type, meter, "started");
            units = number(type, meter, "units");
            copiedEntries = number(type, meter, "copiedEntries");
            maxGroupNanos = number(type, meter, "maxGroupNanos");
            copyChunks = integer(type, meter, "copyChunks");
            groups = integer(type, meter, "groups");
            groupUnits = integer(type, meter, "groupUnits");
            maxGroupUnits = integer(type, meter, "maxGroupUnits");
            copyAllowance = integer(type, meter, "copyAllowance");
            priorGroups = integer(type, meter, "priorGroups");
            inFlightOwnerGroups = integer(type, meter, "inFlightOwnerGroups");
            sessionConstructorEntries = integer(type, meter, "sessionConstructorEntries");
            open = bool(type, meter, "open");
            ownerStarted = bool(type, meter, "originalOwnerStarted");
            ownerActive = bool(type, meter, "originalOwnerActive");
            ownerAdopted = bool(type, meter, "originalOwnerAdopted");
            groupMeasures = field(type, meter, "groupMeasures");
            groupMeasureCount = groupMeasures instanceof List ?
                    ((List<?>)groupMeasures).size() : -1;
        }

        boolean noOwnerWork() {
            return priorGroups == 0 && !ownerStarted && !ownerActive && !ownerAdopted &&
                    inFlightOwnerGroups == 0 && sessionConstructorEntries == 0;
        }

        boolean sameState(Snapshot other) {
            return meter == other.meter && groupMeasures == other.groupMeasures &&
                    started == other.started && units == other.units &&
                    copiedEntries == other.copiedEntries &&
                    maxGroupNanos == other.maxGroupNanos && copyChunks == other.copyChunks &&
                    groups == other.groups && groupUnits == other.groupUnits &&
                    maxGroupUnits == other.maxGroupUnits &&
                    copyAllowance == other.copyAllowance && priorGroups == other.priorGroups &&
                    inFlightOwnerGroups == other.inFlightOwnerGroups &&
                    sessionConstructorEntries == other.sessionConstructorEntries &&
                    groupMeasureCount == other.groupMeasureCount && open == other.open &&
                    ownerStarted == other.ownerStarted && ownerActive == other.ownerActive &&
                    ownerAdopted == other.ownerAdopted;
        }
    }

    private static final class Fixture {
        final Object parent;
        final Object setupMeter;
        final Map<String,Object> data;
        final Object setupResult;
        final Snapshot base;
        final int additionalOneUnitGroups;
        final Path outputPath;

        Fixture(Object parent, Object setupMeter, Map<String,Object> data,
                Object setupResult, Snapshot base, int additionalOneUnitGroups,
                Path outputPath) {
            this.parent = parent;
            this.setupMeter = setupMeter;
            this.data = data;
            this.setupResult = setupResult;
            this.base = base;
            this.additionalOneUnitGroups = additionalOneUnitGroups;
            this.outputPath = outputPath;
        }
    }

    private static final class BeginFixture {
        final Fixture fixture;
        final int remaining;
        BeginFixture(Fixture fixture, int remaining) {
            this.fixture = fixture;
            this.remaining = remaining;
        }
    }

    private static final class Attempt {
        final Object value;
        final Throwable failure;
        Attempt(Object value, Throwable failure) {
            this.value = value;
            this.failure = failure;
        }
    }

    private static final class FactoryCallLog implements InvocationHandler {
        int calls;
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            calls++;
            throw new AssertionError("FACTORY_MUST_NOT_BE_CALLED:" + method.getName());
        }
    }

    private static final class GuardedLoader extends URLClassLoader {
        final List<String> deniedAttempts = new ArrayList<String>();

        GuardedLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith(
                    "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInput")) {
                deniedAttempts.add(name);
                throw new ClassNotFoundException("DENIED_GENERATED_INPUT_CLASS");
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                boolean childFirst = name.equals(STUDY) || name.startsWith(STUDY + "$") ||
                        name.equals(ROUTING) || name.startsWith(ROUTING + "$") ||
                        name.equals(INPUT_SETUP) || name.startsWith(INPUT_SETUP + "$");
                if (childFirst) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notFound) {
                        loaded = super.loadClass(name, false);
                    }
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }
}
