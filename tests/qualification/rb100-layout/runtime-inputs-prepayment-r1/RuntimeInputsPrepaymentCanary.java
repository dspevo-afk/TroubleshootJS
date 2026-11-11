import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Synthetic-only prepayment and Meter-boundary canary for runtime inputs. */
public final class RuntimeInputsPrepaymentCanary {
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String INPUTS =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy$Inputs";
    private static final String METER =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy$Meter";
    private static final String PROFILE = "COHORT_TERMINAL_TELEMETRY_V2";
    private static final String RUN_ID = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String SHA_A = "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb" +
            "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb";
    private static final String SHA_C = "cccccccccccccccc" + "cccccccccccccccc" +
            "cccccccccccccccc" + "cccccccccccccccc";
    private static final String[] INPUT_FIELDS = {
        "runId", "manifestSha", "wireSha", "data"
    };
    private static int cases;
    private static int checks;
    private static final List<String> failures = new ArrayList<String>();

    private RuntimeInputsPrepaymentCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3 || args[1] == null || args[2] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        Path outputInput = Paths.get(args[1]);
        if (!outputInput.isAbsolute())
            throw new IllegalArgumentException("CANARY_OUTPUT_NOT_ABSOLUTE");
        Path output = outputInput.toRealPath();
        GuardedLoader loader = new GuardedLoader(classpathUrls(args[2]));
        try {
            Harness harness = new Harness(loader);
            check(harness.parent != null, "ACTUAL_PARENT_CONSTRUCTED");
            Attempt parsed = invoke(harness.parse, null,
                    new Object[] {validArguments(output.toString())});
            check(parsed.failure == null && parsed.value != null &&
                    parsed.value.getClass() == harness.argumentsType,
                    "ACTUAL_V2_ARGUMENTS_READY");
            if ("SMOKE".equals(args[0])) runSmoke(harness, parsed.value, output);
            else runSuite(harness, parsed.value, output);
            check(loader.deniedAttempts.isEmpty(), "GENERATED_FACTORY_LOAD_ATTEMPTS_ZERO");
            printSummary(args[0], loader.deniedAttempts.size());
        } finally {
            loader.close();
        }
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void runSmoke(Harness harness, Object arguments, Path output)
            throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        mappingChecks(harness, arguments, result, output, "SMOKE");
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(after.validClosed(10L, 4L, 1, 1, 10, 10, 1) && after.noOwnerWork(),
                "SMOKE_PREPAYMENT_CLOSED_PRICE_AND_FLAGS");
    }

    private static void runSuite(Harness harness, Object arguments, Path output)
            throws Exception {
        testNullMeter(harness, arguments);
        testForeignParentMeter(harness, arguments);
        testNullRawMeter(harness, arguments);
        testWrongRawMeter(harness, arguments);
        testNullArgumentsPrepayment(harness);
        testCopyAllowanceFailure(harness, arguments);
        testValidMeterPrice(harness, arguments, output);
        testRepeatedSameMeter(harness, arguments, output);
        testGroupRollover(harness, arguments, output);
        testGroup640Limit(harness, arguments);
    }

    private static void testNullMeter(Harness harness, Object arguments) throws Exception {
        cases++;
        Attempt result = invokeRuntime(harness, harness.parent, arguments, null);
        check(exactIo(result.failure, "RUNTIME_INPUT_METER") && result.value == null,
                "SUITE_NULL_METER_REJECTED_BEFORE_DEBIT");
    }

    private static void testForeignParentMeter(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Object foreignParent = harness.newParent();
        Object foreignSetup = harness.newSetupMeter(foreignParent);
        MeterSnapshot before = new MeterSnapshot(harness, foreignSetup);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, foreignSetup);
        MeterSnapshot after = new MeterSnapshot(harness, foreignSetup);
        check(exactIo(result.failure, "RUNTIME_INPUT_METER") && result.value == null,
                "SUITE_FOREIGN_PARENT_WRAPPER_REJECTED");
        check(before.sameState(after) && after.noOwnerWork(),
                "SUITE_FOREIGN_PARENT_REJECTION_DOES_NOT_DEBIT");
    }

    private static void testNullRawMeter(Harness harness, Object arguments) throws Exception {
        cases++;
        Object setup = harness.newSetupWrapper(harness.parent, null);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        check(exactIo(result.failure, "RUNTIME_INPUT_METER") && result.value == null,
                "SUITE_NULL_RAW_METER_REJECTED_BEFORE_DEBIT");
    }

    private static void testWrongRawMeter(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Object setup = harness.newSetupWrapper(harness.parent, new Object());
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        check(exactIo(result.failure, "RUNTIME_INPUT_METER") && result.value == null,
                "SUITE_PLAIN_OBJECT_RAW_METER_REJECTED_BEFORE_DEBIT");
    }

    private static void testNullArgumentsPrepayment(Harness harness) throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        Object suppliedMeter = harness.nativeMeter(setup);
        Attempt result = invokeRuntime(harness, harness.parent, null, setup);
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(exactIo(result.failure, "RUNTIME_INPUT_ARGUMENTS") && result.value == null,
                "SUITE_NULL_ARGUMENTS_REJECTED_AFTER_PREPAYMENT");
        check(after.meter == suppliedMeter &&
                after.openPrice(2L, 0, 0, 0, 2, 0, 1048576) && after.noOwnerWork(),
                "SUITE_NULL_ARGUMENTS_PAYS_TWO_AND_LEAVES_GROUP_OPEN");
    }

    private static void testCopyAllowanceFailure(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        harness.setCopyAllowance(setup, 3);
        Object suppliedMeter = harness.nativeMeter(setup);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(exactIo(result.failure,
                "copy exceeds its prebounded chunk or aggregate allowance") &&
                result.value == null, "SUITE_COPY_ALLOWANCE_FAILURE_EXACT");
        check(after.meter == suppliedMeter &&
                after.openPrice(6L, 0, 0, 0, 6, 0, 3) && after.noOwnerWork(),
                "SUITE_COPY_FAILURE_PAYS_PREPAY_AND_FOUR_READS_ONLY");
    }

    private static void testValidMeterPrice(Harness harness, Object arguments,
            Path output) throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        Object suppliedMeter = harness.nativeMeter(setup);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        mappingChecks(harness, arguments, result, output, "SUITE_VALID");
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(after.meter == suppliedMeter &&
                after.validClosed(10L, 4L, 1, 1, 10, 10, 1) && after.noOwnerWork(),
                "SUITE_VALID_PREPAYMENT_CLOSED_PRICE_AND_FLAGS");
    }

    private static void testRepeatedSameMeter(Harness harness, Object arguments,
            Path output) throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        Object suppliedMeter = harness.nativeMeter(setup);
        Attempt first = invokeRuntime(harness, harness.parent, arguments, setup);
        Attempt second = invokeRuntime(harness, harness.parent, arguments, setup);
        mappingChecks(harness, arguments, first, output, "SUITE_REPEATED_FIRST");
        mappingChecks(harness, arguments, second, output, "SUITE_REPEATED_SECOND");
        check(first.value != null && second.value != null && first.value != second.value,
                "SUITE_REPEATED_METER_RETURNS_DISTINCT_CARRIERS");
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(after.meter == suppliedMeter &&
                after.validClosed(20L, 8L, 2, 2, 10, 10, 2) && after.noOwnerWork(),
                "SUITE_REPEATED_METER_CUMULATIVE_PRICE_AND_FLAGS");
    }

    private static void testGroupRollover(Harness harness, Object arguments,
            Path output) throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        harness.charge(setup, 32765L);
        MeterSnapshot before = new MeterSnapshot(harness, setup);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        mappingChecks(harness, arguments, result, output, "SUITE_ROLLOVER");
        check(before.units == 32765L && before.groupUnits == 32765 && before.open &&
                after.units - before.units == 10L &&
                after.copiedEntries - before.copiedEntries == 4L &&
                after.copyChunks - before.copyChunks == 1 &&
                after.validClosed(32775L, 4L, 1, 2, 32768, 7, 2) &&
                after.noOwnerWork(), "SUITE_ROLLOVER_PREPAYMENT_DELTA_AND_GROUPS");
    }

    private static void testGroup640Limit(Harness harness, Object arguments)
            throws Exception {
        cases++;
        Object setup = harness.newSetupMeter(harness.parent);
        for (int i = 0; i < 640; i++) {
            harness.charge(setup, 1L);
            harness.finish(setup);
        }
        MeterSnapshot before = new MeterSnapshot(harness, setup);
        Attempt result = invokeRuntime(harness, harness.parent, arguments, setup);
        MeterSnapshot after = new MeterSnapshot(harness, setup);
        check(exactIo(result.failure, "native wrapper exceeded 640 measured groups") &&
                result.value == null,
                "SUITE_GROUP_640_LIMIT_REJECTS");
        check(before.units == 640L && before.groups == 640 &&
                before.groupUnits == 1 && !before.open &&
                before.copiedEntries == 0L && before.copyChunks == 0 &&
                before.maxGroupUnits == 1 && before.groupMeasureCount == 640 &&
                before.sameState(after) &&
                after.noOwnerWork(), "SUITE_GROUP_640_REJECTION_COUNTERS_UNCHANGED");
    }

    private static void mappingChecks(Harness harness, Object arguments, Attempt result,
            Path output, String label) {
        check(result.failure == null && result.value != null &&
                result.value.getClass() == harness.inputsType,
                label + "_RETURNS_EXACT_NATIVE_INPUTS_TYPE");
        check(exactInputsFields(harness.inputsType),
                label + "_EXACT_FOUR_FINAL_INPUT_FIELDS");
        check(mappedField(harness.inputsType, result.value, "runId", String.class,
                harness.argumentsType, arguments, "runId", RUN_ID),
                label + "_RUN_ID_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, result.value, "manifestSha", String.class,
                harness.argumentsType, arguments, "sourceManifestSha256", SHA_A),
                label + "_MANIFEST_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, result.value, "wireSha", String.class,
                harness.argumentsType, arguments, "wireContractSha256", SHA_B),
                label + "_WIRE_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, result.value, "data", Path.class,
                harness.argumentsType, arguments, "outputDir", output),
                label + "_PATH_REFERENCE_MAPPED");
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

    private static boolean exactInputsFields(Class<?> inputsType) {
        try {
            Field[] fields = inputsType.getDeclaredFields();
            if (fields.length != INPUT_FIELDS.length) return false;
            Set<String> expected = new HashSet<String>(Arrays.asList(INPUT_FIELDS));
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

    private static boolean exactIo(Throwable failure, String message) {
        return failure != null && failure.getClass() == IOException.class &&
                message.equals(failure.getMessage());
    }

    private static Attempt invokeRuntime(Harness harness, Object parent,
            Object arguments, Object setupMeter) throws Exception {
        return invoke(harness.runtimeInputs, parent,
                new Object[] {arguments, setupMeter});
    }

    private static Attempt invoke(Method method, Object receiver, Object[] arguments)
            throws Exception {
        try {
            return new Attempt(method.invoke(receiver, arguments), null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof InvocationTargetException && cause.getCause() != null)
                cause = cause.getCause();
            return new Attempt(null, cause);
        }
    }

    private static String[] validArguments(String output) {
        return new String[] {PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C, output};
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
        return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
    }

    private static int integer(Class<?> type, Object object, String name) {
        Object value = field(type, object, name);
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static boolean bool(Class<?> type, Object object, String name) {
        return Boolean.TRUE.equals(field(type, object, name));
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) failures.add(label);
    }

    private static void printSummary(String mode, int denied) {
        System.out.println("SYNTHETIC_RUNTIME_INPUTS_PREPAYMENT_COMPONENT_ONLY mode=" + mode +
                " cases=" + cases + " checks=" + checks +
                " deniedFactoryLoads=" + denied + " failures=" + failures.size() +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
        for (int i = 0; i < failures.size(); i++)
            System.out.println("failure" + i + "=" + failures.get(i));
    }

    private static final class Harness {
        final Class<?> argumentsType;
        final Class<?> inputsType;
        final Class<?> parentType;
        final Class<?> setupMeterType;
        final Class<?> nativeMeterType;
        final Constructor<?> parentConstructor;
        final Constructor<?> setupMeterConstructor;
        final Method parse;
        final Method parentMeter;
        final Method runtimeInputs;
        final Method setupCharge;
        final Method setupFinish;
        final Object parent;

        Harness(GuardedLoader loader) throws Exception {
            argumentsType = Class.forName(STUDY + "$Arguments", false, loader);
            inputsType = Class.forName(INPUTS, false, loader);
            parentType = Class.forName(STUDY + "$Parent", false, loader);
            setupMeterType = Class.forName(STUDY + "$SetupMeter", false, loader);
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
            Method parentMeterMethod = parentType.getDeclaredMethod("meter", long.class);
            parentMeterMethod.setAccessible(true);
            parentMeter = parentMeterMethod;
            Method runtime = parentType.getDeclaredMethod("runtimeInputs", argumentsType,
                    setupMeterType);
            runtime.setAccessible(true);
            runtimeInputs = runtime;
            Method charge = setupMeterType.getDeclaredMethod("charge", long.class);
            charge.setAccessible(true);
            setupCharge = charge;
            Method finish = setupMeterType.getDeclaredMethod("finish");
            finish.setAccessible(true);
            setupFinish = finish;
            parent = newParent();
        }

        Object newParent() throws Exception {
            return parentConstructor.newInstance();
        }

        Object newSetupMeter(Object parent) throws Exception {
            Object nativeMeter = parentMeter.invoke(parent,
                    new Object[] {Long.valueOf(System.nanoTime())});
            return setupMeterConstructor.newInstance(parent, nativeMeter);
        }

        Object newSetupWrapper(Object parent, Object rawMeter) throws Exception {
            return setupMeterConstructor.newInstance(parent, rawMeter);
        }

        Object nativeMeter(Object setup) {
            return field(setupMeterType, setup, "meter");
        }

        void setCopyAllowance(Object setup, int value) throws Exception {
            Object meter = nativeMeter(setup);
            Method setter = nativeMeterType.getDeclaredMethod("setCopyAllowance", int.class);
            setter.setAccessible(true);
            setter.invoke(meter, Integer.valueOf(value));
        }

        void charge(Object setup, long value) throws Exception {
            setupCharge.invoke(setup, Long.valueOf(value));
        }

        void finish(Object setup) throws Exception {
            setupFinish.invoke(setup);
        }
    }

    private static final class MeterSnapshot {
        final long units, copiedEntries;
        final int copyChunks, groups, groupUnits, maxGroupUnits, copyAllowance;
        final int inFlightOwnerGroups, sessionConstructorEntries, groupMeasureCount;
        final boolean open, ownerStarted, ownerActive, ownerAdopted;
        final Object groupMeasures, meter;

        MeterSnapshot(Harness harness, Object setup) {
            meter = harness.nativeMeter(setup);
            Class<?> type = harness.nativeMeterType;
            units = number(type, meter, "units");
            copiedEntries = number(type, meter, "copiedEntries");
            copyChunks = integer(type, meter, "copyChunks");
            groups = integer(type, meter, "groups");
            groupUnits = integer(type, meter, "groupUnits");
            maxGroupUnits = integer(type, meter, "maxGroupUnits");
            copyAllowance = integer(type, meter, "copyAllowance");
            inFlightOwnerGroups = integer(type, meter, "inFlightOwnerGroups");
            sessionConstructorEntries = integer(type, meter, "sessionConstructorEntries");
            open = bool(type, meter, "open");
            ownerStarted = bool(type, meter, "originalOwnerStarted");
            ownerActive = bool(type, meter, "originalOwnerActive");
            ownerAdopted = bool(type, meter, "originalOwnerAdopted");
            groupMeasures = field(type, meter, "groupMeasures");
            groupMeasureCount = groupMeasures instanceof List ?
                    ((List<?>) groupMeasures).size() : -1;
        }

        boolean noOwnerWork() {
            return !ownerStarted && !ownerActive && !ownerAdopted &&
                    inFlightOwnerGroups == 0 && sessionConstructorEntries == 0;
        }

        boolean validClosed(long expectedUnits, long expectedCopies, int expectedChunks,
                int expectedGroups, int expectedMaxUnits, int expectedGroupUnits,
                int expectedMeasureCount) {
            return units == expectedUnits && copiedEntries == expectedCopies &&
                    copyChunks == expectedChunks && groups == expectedGroups && !open &&
                    maxGroupUnits == expectedMaxUnits && groupUnits == expectedGroupUnits &&
                    groupMeasureCount == expectedMeasureCount &&
                    groupMeasures instanceof List && meter != null;
        }

        boolean openPrice(long expectedUnits, long expectedCopies, int expectedChunks,
                int expectedGroups, int expectedGroupUnits, int expectedMaxUnits,
                int expectedCopyAllowance) {
            return units == expectedUnits && copiedEntries == expectedCopies &&
                    copyChunks == expectedChunks && groups == expectedGroups && open &&
                    groupUnits == expectedGroupUnits && maxGroupUnits == expectedMaxUnits &&
                    copyAllowance == expectedCopyAllowance && groupMeasureCount == 0 &&
                    groupMeasures instanceof List && meter != null;
        }

        boolean sameState(MeterSnapshot other) {
            return meter == other.meter && units == other.units &&
                    copiedEntries == other.copiedEntries && copyChunks == other.copyChunks &&
                    groups == other.groups && groupUnits == other.groupUnits &&
                    maxGroupUnits == other.maxGroupUnits && copyAllowance == other.copyAllowance &&
                    inFlightOwnerGroups == other.inFlightOwnerGroups &&
                    sessionConstructorEntries == other.sessionConstructorEntries &&
                    open == other.open && ownerStarted == other.ownerStarted &&
                    ownerActive == other.ownerActive && ownerAdopted == other.ownerAdopted &&
                    groupMeasures == other.groupMeasures &&
                    groupMeasureCount == other.groupMeasureCount;
        }

        boolean unchangedAccounting(MeterSnapshot other) {
            return meter == other.meter && units == other.units &&
                    copiedEntries == other.copiedEntries && copyChunks == other.copyChunks &&
                    groups == other.groups && maxGroupUnits == other.maxGroupUnits &&
                    groupMeasures == other.groupMeasures &&
                    groupMeasureCount == other.groupMeasureCount;
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

    private static final class GuardedLoader extends URLClassLoader {
        final List<String> deniedAttempts = new ArrayList<String>();

        GuardedLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInput")) {
                deniedAttempts.add(name);
                throw new ClassNotFoundException("DENIED_GENERATED_INPUT_CLASS");
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (name.equals(STUDY) || name.startsWith(STUDY + "$")) {
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
