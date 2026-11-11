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

/** Synthetic-only canary for mapping parsed arguments into the runtime carrier. */
public final class RuntimeInputsCanary {
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String NATIVE_INPUTS =
            "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy$Inputs";
    private static final String PROFILE = "COHORT_TERMINAL_TELEMETRY_V2";
    private static final String RUN_A = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String RUN_B = "bbbbbbbb-0000-4000-8000-000000000002";
    private static final String SHA_A = "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb" +
            "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb";
    private static final String SHA_C = "cccccccccccccccc" + "cccccccccccccccc" +
            "cccccccccccccccc" + "cccccccccccccccc";
    private static final String SHA_D = "dddddddddddddddd" + "dddddddddddddddd" +
            "dddddddddddddddd" + "dddddddddddddddd";
    private static final String SHA_E = "eeeeeeeeeeeeeeee" + "eeeeeeeeeeeeeeee" +
            "eeeeeeeeeeeeeeee" + "eeeeeeeeeeeeeeee";
    private static final String SHA_F = "ffffffffffffffff" + "ffffffffffffffff" +
            "ffffffffffffffff" + "ffffffffffffffff";
    private static final String[] INPUT_FIELD_NAMES = {
        "runId", "manifestSha", "wireSha", "data"
    };
    private static int cases;
    private static int checks;
    private static final List<String> failures = new ArrayList<String>();

    private RuntimeInputsCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 3 || args[1] == null || args[2] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        Path emptyInput = Paths.get(args[1]);
        if (!emptyInput.isAbsolute())
            throw new IllegalArgumentException("CANARY_OUTPUT_NOT_ABSOLUTE");
        Path empty = emptyInput.toRealPath();
        URL[] urls = classpathUrls(args[2]);
        GuardedLoader loader = new GuardedLoader(urls);
        try {
            Harness harness = new Harness(loader);
            check(harness.parent != null, "ACTUAL_PARENT_CONSTRUCTED");
            if ("SMOKE".equals(args[0])) runSmoke(harness, empty);
            else runSuite(harness, empty);
            check(loader.deniedAttempts.isEmpty(), "GENERATED_INPUT_LOAD_ATTEMPTS_ZERO");
            printSummary(args[0], loader.deniedAttempts.size());
        } finally {
            loader.close();
        }
        if (!failures.isEmpty()) System.exit(1);
    }

    private static void runSmoke(Harness harness, Path empty) throws Exception {
        mappingCase(harness, empty, RUN_A, SHA_A, SHA_B, SHA_C, "SMOKE");
    }

    private static void runSuite(Harness harness, Path empty) throws Exception {
        nullRuntimeArguments(harness);

        Mapping first = mappingCase(harness, empty, RUN_A, SHA_A, SHA_B, SHA_C,
                "SUITE_FIRST");
        Mapping second = mappingCase(harness, empty, RUN_B, SHA_D, SHA_E, SHA_F,
                "SUITE_SECOND");
        check(first != null && second != null && first.arguments != second.arguments &&
                first.inputs != second.inputs &&
                field(first.inputType, first.inputs, "runId") !=
                        field(second.inputType, second.inputs, "runId") &&
                field(first.inputType, first.inputs, "manifestSha") !=
                        field(second.inputType, second.inputs, "manifestSha") &&
                field(first.inputType, first.inputs, "wireSha") !=
                        field(second.inputType, second.inputs, "wireSha") &&
                equalField(first.inputType, first.inputs, second.inputs, "data"),
                "SUITE_SECOND_CARRIER_AND_MAPPED_IDENTITIES_INDEPENDENT");
        check(first != null && first.before.sameReferences(first.inputType, first.inputs),
                "SUITE_FIRST_CARRIER_UNCHANGED_AFTER_SECOND");

        String[] firstValues = validArguments(empty.toString(), RUN_A, SHA_A, SHA_B, SHA_C);
        String[] contextChanged = firstValues.clone();
        contextChanged[4] = SHA_D;
        Mapping third = mappingCase(harness, empty, contextChanged, "SUITE_CONTEXT_ONLY");
        check(first != null && third != null && first.inputs != third.inputs &&
                field(first.arguments.getClass(), first.arguments, "runId") ==
                        field(third.arguments.getClass(), third.arguments, "runId") &&
                field(first.arguments.getClass(), first.arguments,
                        "sourceManifestSha256") ==
                        field(third.arguments.getClass(), third.arguments,
                                "sourceManifestSha256") &&
                field(first.arguments.getClass(), first.arguments,
                        "wireContractSha256") ==
                        field(third.arguments.getClass(), third.arguments,
                                "wireContractSha256") &&
                differentField(first.arguments.getClass(), first.arguments, third.arguments,
                        "inputContextSha256") &&
                equalField(first.arguments.getClass(), first.arguments, third.arguments,
                        "outputDir") &&
                field(first.inputType, first.inputs, "runId") ==
                        field(third.inputType, third.inputs, "runId") &&
                field(first.inputType, first.inputs, "manifestSha") ==
                        field(third.inputType, third.inputs, "manifestSha") &&
                field(first.inputType, first.inputs, "wireSha") ==
                        field(third.inputType, third.inputs, "wireSha") &&
                equalField(first.inputType, first.inputs, third.inputs, "data"),
                "SUITE_CONTEXT_CHANGE_IS_NOT_STORED_OR_MAPPED");
        check(first != null && first.before.sameReferences(first.inputType, first.inputs),
                "SUITE_FIRST_CARRIER_UNCHANGED_AFTER_CONTEXT_VARIANT");
    }

    private static Mapping mappingCase(Harness harness, Path empty, String runId,
            String manifest, String wire, String context, String label) throws Exception {
        String[] args = validArguments(empty.toString(), runId, manifest, wire, context);
        return mappingCase(harness, empty, args, label);
    }

    private static Mapping mappingCase(Harness harness, Path empty, String[] args,
            String label) throws Exception {
        cases++;
        Attempt parsed = invoke(harness.parse, null, new Object[] {args});
        check(parsed.failure == null && parsed.value != null &&
                parsed.value.getClass() == harness.argumentsType,
                label + "_ACTUAL_ARGUMENTS_PARSE_SUCCEEDS");
        Attempt mapped = parsed.failure == null ?
                invoke(harness.runtimeInputs, harness.parent, new Object[] {parsed.value}) :
                new Attempt(null, parsed.failure);
        check(mapped.failure == null && mapped.value != null &&
                mapped.value.getClass() == harness.inputsType,
                label + "_RETURNS_EXACT_NATIVE_INPUTS_TYPE");
        check(exactInputsFields(harness.inputsType),
                label + "_EXACT_FOUR_FINAL_FIELDS_NO_CONTEXT_FIELD");
        check(mappedField(harness.inputsType, mapped.value, "runId", String.class,
                harness.argumentsType, parsed.value, "runId", runIdFrom(args)),
                label + "_RUN_ID_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, mapped.value, "manifestSha", String.class,
                harness.argumentsType, parsed.value, "sourceManifestSha256",
                manifestFrom(args)), label + "_SOURCE_MANIFEST_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, mapped.value, "wireSha", String.class,
                harness.argumentsType, parsed.value, "wireContractSha256", wireFrom(args)),
                label + "_WIRE_REFERENCE_MAPPED");
        check(mappedField(harness.inputsType, mapped.value, "data", Path.class,
                harness.argumentsType, parsed.value, "outputDir", empty),
                label + "_PATH_REFERENCE_MAPPED_AND_VALUE_PRESERVED");
        if (parsed.value == null || mapped.value == null) return null;
        return new Mapping(parsed.value, mapped.value, harness.inputsType);
    }

    private static void nullRuntimeArguments(Harness harness) throws Exception {
        cases++;
        Attempt result = invoke(harness.runtimeInputs, harness.parent, new Object[] {null});
        check(result.failure != null && result.failure.getClass() == IOException.class &&
                "RUNTIME_INPUT_ARGUMENTS".equals(result.failure.getMessage()),
                "SUITE_NULL_ARGUMENTS_EXACT_RUNTIME_INPUT_ARGUMENTS");
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

    private static Object field(Class<?> type, Object value, String name) {
        if (type == null || value == null) return null;
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(value);
        } catch (ReflectiveOperationException failure) {
            return null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static boolean equalField(Class<?> type, Object first, Object second,
            String name) {
        Object left = field(type, first, name);
        Object right = field(type, second, name);
        return left != null && right != null && left.equals(right);
    }

    private static boolean differentField(Class<?> type, Object first, Object second,
            String name) {
        Object left = field(type, first, name);
        Object right = field(type, second, name);
        return left != null && right != null && !left.equals(right);
    }

    private static String runIdFrom(String[] args) { return args[1]; }
    private static String manifestFrom(String[] args) { return args[2]; }
    private static String wireFrom(String[] args) { return args[3]; }

    private static String[] validArguments(String outputPath, String runId,
            String manifest, String wire, String context) {
        return new String[] {PROFILE, runId, manifest, wire, context, outputPath};
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

    private static void printSummary(String mode, int deniedAttempts) {
        System.out.println("SYNTHETIC_RUNTIME_INPUTS_COMPONENT_ONLY mode=" + mode +
                " cases=" + cases + " checks=" + checks +
                " deniedFactoryLoads=" + deniedAttempts +
                " failures=" + failures.size() +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
        for (int i = 0; i < failures.size(); i++)
            System.out.println("failure" + i + "=" + failures.get(i));
    }

    private static final class Harness {
        final Class<?> argumentsType;
        final Class<?> inputsType;
        final Object parent;
        final Method parse;
        final Method runtimeInputs;

        Harness(GuardedLoader loader) throws Exception {
            argumentsType = Class.forName(STUDY + "$Arguments", false, loader);
            inputsType = Class.forName(NATIVE_INPUTS, false, loader);
            Class<?> parentType = Class.forName(STUDY + "$Parent", false, loader);
            Method parseMethod = argumentsType.getDeclaredMethod("parse", String[].class);
            parseMethod.setAccessible(true);
            parse = parseMethod;
            Constructor<?> constructor = parentType.getDeclaredConstructor();
            constructor.setAccessible(true);
            parent = constructor.newInstance();
            Method runtime = parentType.getDeclaredMethod("runtimeInputs", argumentsType);
            runtime.setAccessible(true);
            runtimeInputs = runtime;
        }
    }

    private static final class Mapping {
        final Object arguments;
        final Object inputs;
        final Class<?> inputType;
        final CarrierSnapshot before;

        Mapping(Object arguments, Object inputs, Class<?> inputType) {
            this.arguments = arguments;
            this.inputs = inputs;
            this.inputType = inputType;
            before = new CarrierSnapshot(inputType, inputs);
        }
    }

    private static final class CarrierSnapshot {
        final Object runId, manifest, wire, data;

        CarrierSnapshot(Class<?> type, Object carrier) {
            runId = field(type, carrier, "runId");
            manifest = field(type, carrier, "manifestSha");
            wire = field(type, carrier, "wireSha");
            data = field(type, carrier, "data");
        }

        boolean sameReferences(Class<?> type, Object carrier) {
            return runId == field(type, carrier, "runId") &&
                    manifest == field(type, carrier, "manifestSha") &&
                    wire == field(type, carrier, "wireSha") &&
                    data == field(type, carrier, "data");
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
                throw new ClassNotFoundException("DENIED_FACTORY_INPUT_CLASS");
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
