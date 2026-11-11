import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Isolated laboratory canary for the test-only typed caller prefix. */
public final class CurrentCallerPrefixCanary {
    private static final String PREFIX =
            "com.lushprojects.circuitjs1.client.CurrentCallerPrefix";
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String ROUTING =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String METER = ROUTING + "$Meter";
    private static final String INPUT_SETUP =
            "com.lushprojects.circuitjs1.client.CurrentV2InputSetup";
    private static final String NO_DATA =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInputV2";
    private static final String OWNER_PREFIX =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String DATA_PREFIX =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInput";
    private static final String PROFILE = "COHORT_TERMINAL_TELEMETRY_V2";
    private static final String RUN_ID = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String SHA_A = "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb" +
            "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb";
    private static final String SHA_C = "cccccccccccccccc" + "cccccccccccccccc" +
            "cccccccccccccccc" + "cccccccccccccccc";
    private static final long COPY_LIMIT = 1048576L;
    private static int cases;
    private static int checks;
    private static int deniedFactoryLoads;
    private static int deniedOwnerLoads;
    private static final List<String> failures = new ArrayList<String>();

    private CurrentCallerPrefixCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 4 || args[1] == null ||
                args[2] == null || args[3] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        Path empty = Paths.get(args[1]);
        Path nonempty = Paths.get(args[2]);
        if (!empty.isAbsolute() || !nonempty.isAbsolute())
            throw new IllegalArgumentException("CANARY_OUTPUT_NOT_ABSOLUTE");
        empty = empty.toRealPath();
        nonempty = nonempty.toRealPath();
        URL[] urls = classpathUrls(args[3]);
        if ("SMOKE".equals(args[0])) {
            runCase("SMOKE_TYPED_NO_DATA_STOP", 1, empty, nonempty, urls);
        } else {
            runCase("SUITE_TYPED_NO_DATA_STOP", 1, empty, nonempty, urls);
            runCase("SUITE_WRONG_PROFILE", 2, empty, nonempty, urls);
            runCase("SUITE_NULL_RAW", 3, empty, nonempty, urls);
            runCase("SUITE_MALFORMED_UUID", 4, empty, nonempty, urls);
            runCase("SUITE_UPPERCASE_CONTEXT", 5, empty, nonempty, urls);
            runCase("SUITE_CONTEXT_SOURCE_SWAP", 6, empty, nonempty, urls);
            runCase("SUITE_NONEMPTY_DIRECTORY", 7, empty, nonempty, urls);
            runCase("SUITE_WRONG_START_CONTROL", 8, empty, nonempty, urls);
            runCase("SUITE_FOREIGN_PARENT_CARRIER", 9, empty, nonempty, urls);
            runCase("SUITE_EXTRA_TIMESTAMP_SLOT", 10, empty, nonempty, urls);
        }
        printSummary(args[0]);
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void runCase(String label, int id, Path empty, Path nonempty,
            URL[] urls) throws Exception {
        cases++;
        boolean denyOwner = id == 2 || id == 3 || id == 4 || id == 5 ||
                id == 7 || id == 10;
        boolean allowStub = id == 1 || id == 6;
        GuardedLoader loader = new GuardedLoader(urls, denyOwner, allowStub);
        try {
            switch (id) {
                case 1: testTypedNoDataStop(label, loader, empty); break;
                case 2: testParseFailure(label, loader,
                        raw(empty, "NOT_THE_V2_PROFILE", RUN_ID, SHA_A, SHA_B, SHA_C),
                        "ARGS"); break;
                case 3: testParseFailure(label, loader, null, "ARGS"); break;
                case 4: testParseFailure(label, loader,
                        raw(empty, PROFILE, "not-a-uuid", SHA_A, SHA_B, SHA_C),
                        "UUID"); break;
                case 5: testParseFailure(label, loader,
                        raw(empty, PROFILE, RUN_ID, SHA_A, SHA_B,
                                SHA_C.toUpperCase(Locale.ROOT)),
                        "SHA"); break;
                case 6: testContextSourceSwap(label, loader, empty); break;
                case 7: testParseFailure(label, loader,
                        raw(nonempty, PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C),
                        "OUTPUT_NONEMPTY"); break;
                case 8: testWrongStartControl(label, loader); break;
                case 9: testForeignParentCarrier(label, loader, empty); break;
                case 10: testParseFailure(label, loader, extraTimestamp(empty),
                        "ARGS"); break;
                default: throw new AssertionError("CASE_ID");
            }
            check(loader.deniedDataAttempts.isEmpty(),
                    label + "_NO_DENIED_GENERATED_DATA_LOAD");
            check(loader.deniedOwnerAttempts.isEmpty(),
                    label + "_NO_DENIED_NATIVE_OWNER_LOAD");
            deniedFactoryLoads += loader.deniedDataAttempts.size();
            deniedOwnerLoads += loader.deniedOwnerAttempts.size();
        } finally {
            loader.close();
        }
    }

    private static void testTypedNoDataStop(String label, GuardedLoader loader,
            Path empty) throws Exception {
        long before = System.nanoTime();
        PrefixInvocation invocation = invokePrefix(loader, raw(empty, PROFILE, RUN_ID,
                SHA_A, SHA_B, SHA_C));
        long after = System.nanoTime();
        check(exactIo(invocation.result.failure, "NO_DATA_FIXTURE_STOP") &&
                invocation.result.value == null,
                label + "_STOPS_AT_EXPLICIT_NO_DATA_FIXTURE");
        Object nativeMeter = staticReference(invocation.prefix, "observedNativeMeter");
        Snapshot meter = new Snapshot(loader, nativeMeter);
        check(meter.matches(652L, 271L, 1, 0, true, 652, 0,
                (int)COPY_LIMIT, 0) && meter.noOwnerWork(),
                label + "_LITERAL_652_271_1_OPEN_STOP");
        check(prefixStagesAndIdentity(invocation.prefix),
                label + "_ONE_PARENT_NATIVE_METER_WRAPPER_AND_INPUT_STAGE");
        long observedStarted = staticLong(invocation.prefix, "observedStarted");
        check(observedStarted - before >= 0L && after - observedStarted >= 0L &&
                meter.started == observedStarted,
                label + "_LOCAL_CLOCK_IS_PASSED_TO_NATIVE_METER");
        Object arguments = staticReference(invocation.prefix, "observedArguments");
        check(argumentRoles(loader, arguments, empty, SHA_C) &&
            distinctArgumentHashes(loader, arguments),
                label + "_DISTINCT_ARGUMENT_ROLES_AND_OUTPUT_PATH");
        check(stubCounts(loader).equals(1, 1, 1, 1),
                label + "_STATIC_STUB_CALLBACKS_ONCE");
    }

    private static void testParseFailure(String label, GuardedLoader loader,
            String[] arguments, String expectedCode) throws Exception {
        long before = System.nanoTime();
        PrefixInvocation invocation = invokePrefix(loader, arguments);
        long after = System.nanoTime();
        long observed = staticLong(invocation.prefix, "observedStarted");
        check(observed - before >= 0L && after - observed >= 0L,
                label + "_LOCAL_CLOCK_PRECEDES_ARGUMENT_REFUSAL");
        check(exactIo(invocation.result.failure, expectedCode) &&
                invocation.result.value == null,
                label + "_EXACT_ARGUMENT_PREFLIGHT_ERROR");
        check(prefixStoppedBeforeParent(invocation.prefix),
                label + "_NO_ARGUMENT_OBJECT_PARENT_METER_OR_READER_STAGE");
    }

    private static void testContextSourceSwap(String label, GuardedLoader loader,
            Path empty) throws Exception {
        PrefixInvocation invocation = invokePrefix(loader,
                raw(empty, PROFILE, RUN_ID, SHA_A, SHA_B, SHA_A));
        check(exactIo(invocation.result.failure, "V2_SETUP_CONTEXT_SHA") &&
                invocation.result.value == null,
                label + "_SOURCE_HASH_IS_NOT_INPUT_CONTEXT");
        Object nativeMeter = staticReference(invocation.prefix, "observedNativeMeter");
        Snapshot meter = new Snapshot(loader, nativeMeter);
        check(meter.matches(88L, 0L, 0, 0, true, 88, 0,
                (int)COPY_LIMIT, 0) && meter.noOwnerWork(),
                label + "_VALID_WRONG_CONTEXT_STOPS_AT_88");
        check(stubCounts(loader).equals(1, 1, 0, 0),
                label + "_HEADER_CALLBACKS_ONLY");
        Object arguments = staticReference(invocation.prefix, "observedArguments");
        check(argumentRoles(loader, arguments, empty, SHA_A) &&
                SHA_A.equals(field(loader.argumentsType(), arguments,
                        "inputContextSha256")) &&
                prefixStagesAndIdentity(invocation.prefix),
                label + "_CONTEXT_ROLE_REACHES_READER_UNCHANGED");
    }

    private static void testWrongStartControl(String label, GuardedLoader loader)
            throws Exception {
        Harness harness = new Harness(loader, null);
        Object parent = harness.newParent();
        long started = System.nanoTime();
        Object rawMeter = harness.newNativeMeter(parent, started);
        Object wrapper = harness.newWrapper(parent, rawMeter);
        Snapshot before = new Snapshot(loader, rawMeter);
        Attempt result = harness.readCurrent(wrapper, started ^ 1L, SHA_C);
        check(exactIo(result.failure, "V2_SETUP_START") && result.value == null,
                label + "_EXISTING_SEAM_REJECTS_WRONG_START");
        Snapshot after = new Snapshot(loader, rawMeter);
        check(before.started == started && after.started == started &&
                after.matches(18L, 0L, 0, 0, true, 18, 0,
                        (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                label + "_WRONG_START_PREPAYMENT_ONLY");
    }

    private static void testForeignParentCarrier(String label, GuardedLoader loader,
            Path empty) throws Exception {
        Harness harness = new Harness(loader, empty);
        Attempt parsed = harness.parse(raw(empty, PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C));
        check(parsed.failure == null && parsed.value != null &&
                parsed.value.getClass() == harness.argumentsType,
                label + "_VALID_ARGUMENTS_CONTROL");
        Object firstParent = harness.newParent();
        Object foreignParent = harness.newParent();
        long started = System.nanoTime();
        Object nativeMeter = harness.newNativeMeter(firstParent, started);
        Object foreignWrapper = harness.newWrapper(foreignParent, nativeMeter);
        Snapshot before = new Snapshot(loader, nativeMeter);
        Attempt result = harness.runtimeInputs(firstParent, parsed.value, foreignWrapper);
        check(exactIo(result.failure, "RUNTIME_INPUT_METER") && result.value == null,
                label + "_FOREIGN_PARENT_WRAPPER_REJECTED");
        Snapshot after = new Snapshot(loader, nativeMeter);
        check(before.sameState(after) && after.noOwnerWork() &&
                after.matches(0L, 0L, 0, 0, false, 0, 0, (int)COPY_LIMIT, 0) &&
                field(loader.setupMeterType(), foreignWrapper, "parent") == foreignParent &&
                field(loader.setupMeterType(), foreignWrapper, "meter") == nativeMeter,
                label + "_REAL_METER_WRAPPER_BINDING_REJECTS_BEFORE_DEBIT");
    }

    private static PrefixInvocation invokePrefix(GuardedLoader loader, String[] raw)
            throws Exception {
        Class<?> type = Class.forName(PREFIX, false, loader);
        Method run = type.getDeclaredMethod("run", String[].class);
        run.setAccessible(true);
        Attempt result = invoke(run, null, new Object[] {raw});
        return new PrefixInvocation(type, result);
    }

    private static boolean prefixStagesAndIdentity(Class<?> prefix) throws Exception {
        Object arguments = staticReference(prefix, "observedArguments");
        Object parent = staticReference(prefix, "observedParent");
        Object nativeMeter = staticReference(prefix, "observedNativeMeter");
        Object wrapper = staticReference(prefix, "observedWrapper");
        Class<?> parentType = Class.forName(STUDY + "$Parent", false,
                prefix.getClassLoader());
        Class<?> setupType = Class.forName(STUDY + "$SetupMeter", false,
                prefix.getClassLoader());
        Class<?> meterType = Class.forName(METER, false, prefix.getClassLoader());
        Class<?> argumentsType = Class.forName(STUDY + "$Arguments", false,
                prefix.getClassLoader());
        return arguments != null && arguments.getClass() == argumentsType &&
                parent != null && parent.getClass() == parentType &&
                nativeMeter != null && meterType.isInstance(nativeMeter) &&
                wrapper != null && wrapper.getClass() == setupType &&
                field(setupType, wrapper, "parent") == parent &&
                field(setupType, wrapper, "meter") == nativeMeter &&
                staticInteger(prefix, "parsedStages") == 1 &&
                staticInteger(prefix, "parentStages") == 1 &&
                staticInteger(prefix, "nativeStages") == 1 &&
                staticInteger(prefix, "wrapperStages") == 1 &&
                staticInteger(prefix, "inputStages") == 1 &&
                staticInteger(prefix, "completionStages") == 0;
    }

    private static boolean prefixStoppedBeforeParent(Class<?> prefix) throws Exception {
        return staticReference(prefix, "observedArguments") == null &&
                staticReference(prefix, "observedParent") == null &&
                staticReference(prefix, "observedNativeMeter") == null &&
                staticReference(prefix, "observedWrapper") == null &&
                staticInteger(prefix, "parsedStages") == 0 &&
                staticInteger(prefix, "parentStages") == 0 &&
                staticInteger(prefix, "nativeStages") == 0 &&
                staticInteger(prefix, "wrapperStages") == 0 &&
                staticInteger(prefix, "inputStages") == 0 &&
                staticInteger(prefix, "completionStages") == 0;
    }

    private static boolean argumentRoles(GuardedLoader loader, Object arguments,
            Path expectedOutput, String expectedContext) throws Exception {
        if (arguments == null || arguments.getClass() != loader.argumentsType()) return false;
        Object run = field(loader.argumentsType(), arguments, "runId");
        Object source = field(loader.argumentsType(), arguments, "sourceManifestSha256");
        Object wire = field(loader.argumentsType(), arguments, "wireContractSha256");
        Object context = field(loader.argumentsType(), arguments, "inputContextSha256");
        Object output = field(loader.argumentsType(), arguments, "outputDir");
        return RUN_ID.equals(run) && SHA_A.equals(source) && SHA_B.equals(wire) &&
                expectedContext.equals(context) && expectedOutput.equals(output);
    }

    private static boolean distinctArgumentHashes(GuardedLoader loader,
            Object arguments) throws Exception {
        Object source = field(loader.argumentsType(), arguments,
                "sourceManifestSha256");
        Object wire = field(loader.argumentsType(), arguments,
                "wireContractSha256");
        Object context = field(loader.argumentsType(), arguments,
                "inputContextSha256");
        return source instanceof String && wire instanceof String &&
                context instanceof String && !source.equals(wire) &&
                !source.equals(context) && !wire.equals(context);
    }

    private static String[] raw(Path output, String profile, String runId,
            String source, String wire, String context) {
        return new String[] {profile, runId, source, wire, context, output.toString()};
    }

    private static String[] extraTimestamp(Path output) {
        String[] base = raw(output, PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C);
        String[] extended = new String[7];
        System.arraycopy(base, 0, extended, 0, base.length);
        extended[6] = "1730000000000";
        return extended;
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

    private static Object field(Class<?> type, Object object, String name) throws Exception {
        if (type == null || object == null) return null;
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object field(Object object, String name) throws Exception {
        return field(object.getClass(), object, name);
    }

    private static Object staticReference(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static long staticLong(Class<?> type, String name) throws Exception {
        Object value = staticReference(type, name);
        return value instanceof Number ? ((Number)value).longValue() : Long.MIN_VALUE;
    }

    private static int staticInteger(Class<?> type, String name) throws Exception {
        Object value = staticReference(type, name);
        return value instanceof Number ? ((Number)value).intValue() : Integer.MIN_VALUE;
    }

    private static long number(Class<?> type, Object object, String name) throws Exception {
        Object value = field(type, object, name);
        return value instanceof Number ? ((Number)value).longValue() : Long.MIN_VALUE;
    }

    private static int integer(Class<?> type, Object object, String name) throws Exception {
        Object value = field(type, object, name);
        return value instanceof Number ? ((Number)value).intValue() : Integer.MIN_VALUE;
    }

    private static boolean bool(Class<?> type, Object object, String name) throws Exception {
        return Boolean.TRUE.equals(field(type, object, name));
    }

    private static StubCounts stubCounts(GuardedLoader loader) throws Exception {
        Class<?> type = Class.forName(NO_DATA, true, loader);
        return new StubCounts(staticInteger(type, "schemaCalls"),
                staticInteger(type, "contextCalls"), staticInteger(type, "entryCalls"),
                staticInteger(type, "dataCalls"));
    }

    private static boolean exactIo(Throwable failure, String message) {
        return failure != null && failure.getClass() == IOException.class &&
                message.equals(failure.getMessage());
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

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) failures.add(label);
    }

    private static void printSummary(String mode) {
        System.out.println("SYNTHETIC_CURRENT_CALLER_PREFIX_ONLY mode=" + mode +
                " cases=" + cases + " checks=" + checks +
                " deniedFactoryLoads=" + deniedFactoryLoads +
                " deniedOwnerLoads=" + deniedOwnerLoads +
                " failures=" + failures.size() +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
        for (int i = 0; i < failures.size(); i++)
            System.out.println("failure" + i + "=" + failures.get(i));
    }

    private static final class Harness {
        final GuardedLoader loader;
        final Path output;
        final Class<?> studyType;
        final Class<?> argumentsType;
        final Class<?> parentType;
        final Class<?> setupMeterType;
        final Class<?> meterType;
        final Class<?> inputSetupType;
        final Constructor<?> parentConstructor;
        final Constructor<?> setupMeterConstructor;
        final Method parse;
        final Method parentMeter;
        final Method runtimeInputs;
        final Method currentRead;

        Harness(GuardedLoader loader, Path output) throws Exception {
            this.loader = loader;
            this.output = output;
            studyType = Class.forName(STUDY, false, loader);
            argumentsType = Class.forName(STUDY + "$Arguments", false, loader);
            parentType = Class.forName(STUDY + "$Parent", false, loader);
            setupMeterType = Class.forName(STUDY + "$SetupMeter", false, loader);
            meterType = Class.forName(METER, false, loader);
            inputSetupType = Class.forName(INPUT_SETUP, false, loader);
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
            Method meterMethod = parentType.getDeclaredMethod("meter", long.class);
            meterMethod.setAccessible(true);
            parentMeter = meterMethod;
            Method runtime = parentType.getDeclaredMethod("runtimeInputs",
                    argumentsType, setupMeterType);
            runtime.setAccessible(true);
            runtimeInputs = runtime;
            Method read = inputSetupType.getDeclaredMethod("readCurrent",
                    setupMeterType, long.class, String.class);
            read.setAccessible(true);
            currentRead = read;
        }

        Object newParent() throws Exception {
            return parentConstructor.newInstance();
        }

        Object newNativeMeter(Object parent, long started) throws Exception {
            return invokeValue(parentMeter, parent, new Object[] {Long.valueOf(started)});
        }

        Object newWrapper(Object parent, Object meter) throws Exception {
            return setupMeterConstructor.newInstance(parent, meter);
        }

        Attempt parse(String[] raw) throws Exception {
            return invoke(parse, null, new Object[] {raw});
        }

        Attempt runtimeInputs(Object parent, Object arguments, Object wrapper)
                throws Exception {
            return invoke(runtimeInputs, parent, new Object[] {arguments, wrapper});
        }

        Attempt readCurrent(Object wrapper, long started, String context) throws Exception {
            return invoke(currentRead, null,
                    new Object[] {wrapper, Long.valueOf(started), context});
        }

        Class<?> argumentsType() {
            return argumentsType;
        }

        Class<?> setupMeterType() {
            return setupMeterType;
        }
    }

    private static final class Snapshot {
        final Object meter;
        final Object groupMeasures;
        final long started;
        final long units;
        final long copiedEntries;
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

        Snapshot(GuardedLoader loader, Object meter) throws Exception {
            this.meter = meter;
            Class<?> type = loader.meterType();
            started = number(type, meter, "started");
            units = number(type, meter, "units");
            copiedEntries = number(type, meter, "copiedEntries");
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

        boolean matches(long expectedUnits, long expectedCopies, int expectedChunks,
                int expectedGroups, boolean expectedOpen, int expectedGroupUnits,
                int expectedMaxGroupUnits, int expectedAllowance, int expectedMeasures) {
            return meter != null && units == expectedUnits &&
                    copiedEntries == expectedCopies && copyChunks == expectedChunks &&
                    groups == expectedGroups && open == expectedOpen &&
                    groupUnits == expectedGroupUnits && maxGroupUnits == expectedMaxGroupUnits &&
                    copyAllowance == expectedAllowance &&
                    groupMeasureCount == expectedMeasures && groupMeasures instanceof List;
        }

        boolean noOwnerWork() {
            return priorGroups == 0 && !ownerStarted && !ownerActive && !ownerAdopted &&
                    inFlightOwnerGroups == 0 && sessionConstructorEntries == 0;
        }

        boolean sameState(Snapshot other) {
            return meter == other.meter && groupMeasures == other.groupMeasures &&
                    started == other.started && units == other.units &&
                    copiedEntries == other.copiedEntries && copyChunks == other.copyChunks &&
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

    private static final class PrefixInvocation {
        final Class<?> prefix;
        final Attempt result;
        PrefixInvocation(Class<?> prefix, Attempt result) {
            this.prefix = prefix;
            this.result = result;
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

    private static final class StubCounts {
        final int schema;
        final int context;
        final int entries;
        final int data;
        StubCounts(int schema, int context, int entries, int data) {
            this.schema = schema;
            this.context = context;
            this.entries = entries;
            this.data = data;
        }
        boolean equals(int expectedSchema, int expectedContext, int expectedEntries,
                int expectedData) {
            return schema == expectedSchema && context == expectedContext &&
                    entries == expectedEntries && data == expectedData;
        }
    }

    private static final class GuardedLoader extends URLClassLoader {
        final List<String> deniedDataAttempts = new ArrayList<String>();
        final List<String> deniedOwnerAttempts = new ArrayList<String>();
        final boolean denyOwner;
        final boolean allowNoData;

        GuardedLoader(URL[] urls, boolean denyOwner, boolean allowNoData) {
            super(urls, null);
            this.denyOwner = denyOwner;
            this.allowNoData = allowNoData;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith(DATA_PREFIX) &&
                    !(allowNoData && NO_DATA.equals(name))) {
                deniedDataAttempts.add(name);
                throw new ClassNotFoundException("DENIED_GENERATED_INPUT_CLASS");
            }
            if ((name.equals(OWNER_PREFIX) || name.startsWith(OWNER_PREFIX + "$")) &&
                    denyOwner) {
                deniedOwnerAttempts.add(name);
                throw new ClassNotFoundException("DENIED_NATIVE_OWNER_CLASS");
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                boolean childFirst = name.equals(PREFIX) ||
                        name.equals(STUDY) || name.startsWith(STUDY + "$") ||
                        name.equals(ROUTING) || name.startsWith(ROUTING + "$") ||
                        name.equals(INPUT_SETUP) || name.startsWith(INPUT_SETUP + "$") ||
                        (allowNoData && NO_DATA.equals(name));
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

        Class<?> argumentsType() throws ClassNotFoundException {
            return Class.forName(STUDY + "$Arguments", false, this);
        }

        Class<?> setupMeterType() throws ClassNotFoundException {
            return Class.forName(STUDY + "$SetupMeter", false, this);
        }

        Class<?> meterType() throws ClassNotFoundException {
            return Class.forName(METER, false, this);
        }
    }
}
