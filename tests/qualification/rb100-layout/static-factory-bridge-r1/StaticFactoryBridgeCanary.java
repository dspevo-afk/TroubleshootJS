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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only proof of the current fixed static factory bridge. */
public final class StaticFactoryBridgeCanary {
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String ROUTING =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String INPUTS = ROUTING + "$Inputs";
    private static final String METER = ROUTING + "$Meter";
    private static final String INPUT_SETUP =
            "com.lushprojects.circuitjs1.client.CurrentV2InputSetup";
    private static final String FACTORY = INPUT_SETUP + "$Factory";
    private static final String DATA_PREFIX =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInput";
    private static final String NO_DATA =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInputV2";
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
    private static final List<String> failures = new ArrayList<String>();

    private StaticFactoryBridgeCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3 || args[1] == null || args[2] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        Path suppliedOutput = Paths.get(args[1]);
        if (!suppliedOutput.isAbsolute())
            throw new IllegalArgumentException("CANARY_OUTPUT_NOT_ABSOLUTE");
        Path output = suppliedOutput.toRealPath();
        URL[] urls = classpathUrls(args[2]);
        if ("SMOKE".equals(args[0])) {
            runCase("SMOKE_FRESH_CURRENT_STOP", 1, true, output, urls);
        } else {
            runCase("SUITE_FRESH_CURRENT_STOP", 1, true, output, urls);
            runCase("SUITE_CARRIER_BEFORE_CURRENT", 2, false, output, urls);
            runCase("SUITE_WRONG_PRODUCER_START", 3, false, output, urls);
            runCase("SUITE_NULL_METER", 4, false, output, urls);
            runCase("SUITE_NULL_CONTEXT", 5, false, output, urls);
            runCase("SUITE_MALFORMED_FIRST_HEX", 6, false, output, urls);
            runCase("SUITE_WRONG_VALID_CONTEXT", 7, true, output, urls);
            runCase("SUITE_FAILED_METER_REENTRY", 8, true, output, urls);
            runCase("SUITE_INJECTED_READ_NO_DATA", 9, false, output, urls);
            runCase("SUITE_ANONYMOUS_FACADE_LAYOUT", 10, false, output, urls);
        }
        printSummary(args[0]);
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void runCase(String label, int id, boolean allowNoDataStub,
            Path output, URL[] urls) throws Exception {
        cases++;
        GuardedLoader loader = new GuardedLoader(urls, allowNoDataStub);
        try {
            Harness harness = new Harness(loader, output);
            check(harness.parent != null, label + "_ACTUAL_PARENT");
            check(harness.arguments != null &&
                    harness.arguments.getClass() == harness.argumentsType,
                    label + "_V2_ARGUMENTS_PARSED");
            if (harness.arguments != null) runBody(id, harness);
            check(loader.deniedAttempts.isEmpty(),
                    label + "_NO_UNAUTHORIZED_GENERATED_CLASS_LOAD");
            deniedFactoryLoads += loader.deniedAttempts.size();
        } finally {
            loader.close();
        }
    }

    private static void runBody(int id, Harness harness) throws Exception {
        switch (id) {
            case 1: testFreshCurrentStop(harness); return;
            case 2: testCarrierBeforeCurrent(harness); return;
            case 3: testWrongProducerStart(harness); return;
            case 4: testNullMeter(harness); return;
            case 5: testNullContext(harness); return;
            case 6: testMalformedFirstHex(harness); return;
            case 7: testWrongValidContext(harness); return;
            case 8: testFailedMeterReentry(harness); return;
            case 9: testInjectedReadNoData(harness); return;
            case 10: testFacadeLayout(harness); return;
            default: throw new AssertionError("CASE_ID");
        }
    }

    private static void testFreshCurrentStop(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        Attempt result = harness.readCurrent(setupMeter, before.started, SHA_C);
        check(exactIo(result.failure, "NO_DATA_FIXTURE_STOP") && result.value == null,
                "FRESH_CURRENT_STOPS_AT_EXPLICIT_NO_DATA_FIXTURE");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(652L, 271L, 1, 0, true, 652, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "FRESH_CURRENT_LITERAL_REFUSAL_PRICE_AND_OPEN_GROUP");
        StubCounts counts = stubCounts(harness.loader);
        check(counts.equals(1, 1, 1, 1),
                "FRESH_CURRENT_CALLS_EACH_STATIC_STUB_ONCE");
    }

    private static void testCarrierBeforeCurrent(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Attempt carrier = harness.runtimeInputs(setupMeter);
        Snapshot afterCarrier = new Snapshot(harness, setupMeter);
        check(carrier.failure == null && carrier.value != null &&
                carrier.value.getClass() == harness.inputsType &&
                afterCarrier.matches(10L, 4L, 1, 1, false, 10, 10,
                        (int)COPY_LIMIT, 1) && afterCarrier.noOwnerWork(),
                "CARRIER_PREPAYMENT_AND_CLOSED_GROUP");
        Attempt result = harness.readCurrent(setupMeter, afterCarrier.started, SHA_C);
        check(exactIo(result.failure, "V2_SETUP_METER_STATE") && result.value == null,
                "CURRENT_READER_REJECTS_CARRIER_BEFORE_FACADE");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(28L, 4L, 1, 1, true, 18, 10,
                (int)COPY_LIMIT, 1) && after.noOwnerWork(),
                "CARRIER_REJECTION_RETAINS_FRESH_EIGHTEEN");
    }

    private static void testWrongProducerStart(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        Attempt result = harness.readCurrent(setupMeter, before.started ^ 1L, SHA_C);
        check(exactIo(result.failure, "V2_SETUP_START") && result.value == null,
                "WRONG_START_REJECTS_AFTER_FRESH_CHARGE");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(18L, 0L, 0, 0, true, 18, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "WRONG_START_RETURNS_OPEN_EIGHTEEN_GROUP");
    }

    private static void testNullMeter(Harness harness) throws Exception {
        Attempt result = harness.readCurrent(null, 0L, SHA_C);
        check(exactIo(result.failure, "V2_SETUP_ARGUMENTS") && result.value == null,
                "NULL_METER_REJECTS_BEFORE_FRESH_OR_FACTORY");
    }

    private static void testNullContext(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        Attempt result = harness.readCurrent(setupMeter, before.started, null);
        check(exactIo(result.failure, "V2_SETUP_ARGUMENTS") && result.value == null,
                "NULL_CONTEXT_REJECTS_BEFORE_FRESH_OR_FACTORY");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(before.sameState(after),
                "NULL_CONTEXT_LEAVES_ACTUAL_METER_PRISTINE");
    }

    private static void testMalformedFirstHex(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        String malformed = "g" + SHA_C.substring(1);
        Attempt result = harness.readCurrent(setupMeter, before.started, malformed);
        check(exactIo(result.failure, "V2_SETUP_CONTEXT_SHA") && result.value == null,
                "MALFORMED_FIRST_HEX_REJECTS_BEFORE_HEADER");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(21L, 0L, 0, 0, true, 21, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "MALFORMED_FIRST_HEX_LITERAL_PRICE");
    }

    private static void testWrongValidContext(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        Attempt result = harness.readCurrent(setupMeter, before.started, SHA_B);
        check(exactIo(result.failure, "V2_SETUP_CONTEXT_SHA") && result.value == null,
                "VALID_BUT_WRONG_CONTEXT_REJECTS");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(88L, 0L, 0, 0, true, 88, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "WRONG_CONTEXT_LITERAL_HEADER_PRICE");
        StubCounts counts = stubCounts(harness.loader);
        check(counts.equals(1, 1, 0, 0),
                "WRONG_CONTEXT_STOPS_BEFORE_ENTRY_AND_DATA_CALLBACKS");
    }

    private static void testFailedMeterReentry(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        Attempt first = harness.readCurrent(setupMeter, before.started, SHA_C);
        Attempt second = harness.readCurrent(setupMeter, before.started, SHA_C);
        check(exactIo(first.failure, "NO_DATA_FIXTURE_STOP") &&
                first.value == null &&
                exactIo(second.failure, "V2_SETUP_METER_STATE") && second.value == null,
                "FAILED_ATTEMPT_REENTRY_IS_REJECTED");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(670L, 271L, 1, 0, true, 670, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "FAILED_ATTEMPT_REENTRY_RETAINS_ONLY_FRESH_EIGHTEEN");
        StubCounts counts = stubCounts(harness.loader);
        check(counts.equals(1, 1, 1, 1),
                "FAILED_ATTEMPT_REENTRY_DOES_NOT_REPEAT_FACTORY_CALLBACKS");
    }

    private static void testInjectedReadNoData(Harness harness) throws Exception {
        Object setupMeter = harness.newSetupMeter();
        Snapshot before = new Snapshot(harness, setupMeter);
        FactoryCallLog calls = new FactoryCallLog();
        Object factory = Proxy.newProxyInstance(harness.loader,
                new Class<?>[] {harness.factoryType()}, calls);
        Attempt result = harness.readInjected(setupMeter, before.started, SHA_C, factory);
        check(exactIo(result.failure, "INJECTED_NO_DATA_FIXTURE_STOP") &&
                result.value == null,
                "INJECTED_READ_PRESERVES_EXISTING_DATA_REFUSAL");
        check(calls.inExpectedOrder(),
                "INJECTED_READ_CALLS_FOUR_CALLBACKS_ONCE_IN_ORDER");
        Snapshot after = new Snapshot(harness, setupMeter);
        check(after.matches(651L, 271L, 1, 0, true, 651, 0,
                (int)COPY_LIMIT, 0) && after.noOwnerWork(),
                "INJECTED_READ_HAS_NO_FACADE_UNIT");
    }

    private static void testFacadeLayout(Harness harness) throws Exception {
        Class<?> facade = Class.forName(INPUT_SETUP + "$1", false, harness.loader);
        Class<?> factory = harness.factoryType();
        check(exactFacade(facade, factory, harness.loader),
                "ANONYMOUS_FACADE_IS_FIELDLESS_AND_HAS_FOUR_INSTANCE_CALLBACKS");
    }

    private static boolean exactFacade(Class<?> facade, Class<?> factory,
            ClassLoader expectedLoader) {
        try {
            if (facade.getClassLoader() != expectedLoader ||
                    facade.getSuperclass() != Object.class ||
                    !factory.isAssignableFrom(facade) ||
                    facade.getDeclaredFields().length != 0) return false;
            Constructor<?>[] constructors = facade.getDeclaredConstructors();
            if (constructors.length != 1 ||
                    constructors[0].getParameterTypes().length != 0) return false;
            Method[] methods = facade.getDeclaredMethods();
            if (methods.length != 4) return false;
            Set<String> expected = new HashSet<String>(Arrays.asList(
                    "schemaTag", "contextSha256", "dataEntries", "dataJson"));
            Set<String> actual = new HashSet<String>();
            for (Method method : methods) {
                if (!Modifier.isPublic(method.getModifiers()) ||
                        Modifier.isStatic(method.getModifiers()) ||
                        method.getParameterTypes().length != 0) return false;
                actual.add(method.getName());
                Class<?> returnType;
                if ("schemaTag".equals(method.getName()) ||
                        "contextSha256".equals(method.getName())) {
                    returnType = String.class;
                } else if ("dataEntries".equals(method.getName())) {
                    returnType = int.class;
                } else if ("dataJson".equals(method.getName())) {
                    returnType = Map.class;
                } else {
                    return false;
                }
                if (method.getReturnType() != returnType) return false;
            }
            return expected.equals(actual);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static StubCounts stubCounts(GuardedLoader loader) throws Exception {
        Class<?> type = Class.forName(NO_DATA, true, loader);
        return new StubCounts(staticInt(type, "schemaCalls"),
                staticInt(type, "contextCalls"), staticInt(type, "entryCalls"),
                staticInt(type, "dataCalls"));
    }

    private static int staticInt(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
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

    private static void printSummary(String mode) {
        System.out.println("SYNTHETIC_STATIC_FACTORY_BRIDGE_COMPONENT_ONLY mode=" + mode +
                " cases=" + cases + " checks=" + checks +
                " deniedFactoryLoads=" + deniedFactoryLoads +
                " failures=" + failures.size() +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
        for (int i = 0; i < failures.size(); i++)
            System.out.println("failure" + i + "=" + failures.get(i));
    }

    private static final class Attempt {
        final Object value;
        final Throwable failure;
        Attempt(Object value, Throwable failure) {
            this.value = value;
            this.failure = failure;
        }
    }

    private static final class Harness {
        final GuardedLoader loader;
        final Path output;
        final Class<?> studyType;
        final Class<?> inputSetupType;
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
        final Method currentRead;
        final Object parent;
        final Object arguments;

        Harness(GuardedLoader loader, Path output) throws Exception {
            this.loader = loader;
            this.output = output;
            studyType = Class.forName(STUDY, false, loader);
            inputSetupType = Class.forName(INPUT_SETUP, false, loader);
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
            Method meterMethod = parentType.getDeclaredMethod("meter", long.class);
            meterMethod.setAccessible(true);
            parentMeter = meterMethod;
            Method carrier = parentType.getDeclaredMethod("runtimeInputs",
                    argumentsType, setupMeterType);
            carrier.setAccessible(true);
            runtimeInputs = carrier;
            Method current = inputSetupType.getDeclaredMethod("readCurrent",
                    setupMeterType, long.class, String.class);
            current.setAccessible(true);
            currentRead = current;
            parent = parentConstructor.newInstance();
            Attempt parsed = invoke(parse, null,
                    new Object[] {validArguments(output.toString())});
            arguments = parsed.failure == null ? parsed.value : null;
        }

        Object newSetupMeter() throws Exception {
            Object raw = invokeValue(parentMeter, parent,
                    new Object[] {Long.valueOf(System.nanoTime())});
            return setupMeterConstructor.newInstance(parent, raw);
        }

        Object nativeMeter(Object setupMeter) {
            return field(setupMeterType, setupMeter, "meter");
        }

        Attempt runtimeInputs(Object setupMeter) throws Exception {
            return invoke(runtimeInputs, parent, new Object[] {arguments, setupMeter});
        }

        Attempt readCurrent(Object setupMeter, long started, String expectedContext)
                throws Exception {
            return invoke(currentRead, null,
                    new Object[] {setupMeter, Long.valueOf(started), expectedContext});
        }

        Attempt readInjected(Object setupMeter, long started, String expectedContext,
                Object factory) throws Exception {
            Class<?> factoryType = factoryType();
            Method injected = inputSetupType.getDeclaredMethod("read",
                    setupMeterType, long.class, String.class, factoryType);
            injected.setAccessible(true);
            return invoke(injected, null,
                    new Object[] {setupMeter, Long.valueOf(started), expectedContext, factory});
        }

        Class<?> factoryType() throws Exception {
            return Class.forName(FACTORY, false, loader);
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

        Snapshot(Harness harness, Object setupMeter) {
            meter = harness.nativeMeter(setupMeter);
            Class<?> type = harness.nativeMeterType;
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

    private static final class FactoryCallLog implements InvocationHandler {
        final List<String> order = new ArrayList<String>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments)
                throws Throwable {
            String name = method.getName();
            order.add(name);
            if ("schemaTag".equals(name))
                return "tsj-cohort-terminal-input-context-v2";
            if ("contextSha256".equals(name)) return SHA_C;
            if ("dataEntries".equals(name)) return Integer.valueOf(271);
            if ("dataJson".equals(name))
                throw new IOException("INJECTED_NO_DATA_FIXTURE_STOP");
            throw new AssertionError("UNEXPECTED_FACTORY_METHOD:" + name);
        }

        boolean inExpectedOrder() {
            return order.equals(Arrays.asList(
                    "schemaTag", "contextSha256", "dataEntries", "dataJson"));
        }
    }

    private static final class GuardedLoader extends URLClassLoader {
        final List<String> deniedAttempts = new ArrayList<String>();
        final boolean allowNoDataStub;

        GuardedLoader(URL[] urls, boolean allowNoDataStub) {
            super(urls, null);
            this.allowNoDataStub = allowNoDataStub;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith(DATA_PREFIX) &&
                    !(allowNoDataStub && NO_DATA.equals(name))) {
                deniedAttempts.add(name);
                throw new ClassNotFoundException("DENIED_GENERATED_INPUT_CLASS");
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                boolean childFirst = name.equals(STUDY) || name.startsWith(STUDY + "$") ||
                        name.equals(ROUTING) || name.startsWith(ROUTING + "$") ||
                        name.equals(INPUT_SETUP) || name.startsWith(INPUT_SETUP + "$") ||
                        (allowNoDataStub && NO_DATA.equals(name));
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
