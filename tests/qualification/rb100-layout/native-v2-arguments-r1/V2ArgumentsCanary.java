import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Synthetic-only canary for the checked V2 Arguments parser. */
public final class V2ArgumentsCanary {
    private static final String STUDY =
            "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy";
    private static final String PROFILE = "COHORT_TERMINAL_TELEMETRY_V2";
    private static final String RUN_ID = "00000000-0000-4000-8000-000000000001";
    private static final String UPPERCASE_UUID =
            "aaaaaaaa-0000-4000-8000-000000000001".toUpperCase(Locale.ROOT);
    private static final String SHA_A = "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa" +
            "aaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb" +
            "bbbbbbbbbbbbbbbb" + "bbbbbbbbbbbbbbbb";
    private static final String SHA_C = "cccccccccccccccc" + "cccccccccccccccc" +
            "cccccccccccccccc" + "cccccccccccccccc";
    private static final String[] FIELD_NAMES = {
        "runId", "sourceManifestSha256", "wireContractSha256",
        "inputContextSha256", "outputDir"
    };
    private static int cases;
    private static int checks;
    private static int blocked;
    private static boolean symlinkBlocked;
    private static final List<String> failures = new ArrayList<String>();

    private V2ArgumentsCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 4 ||
                args[1] == null || args[2] == null || args[3] == null ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        Path rootInput = Paths.get(args[1]);
        if (!rootInput.isAbsolute())
            throw new IllegalArgumentException("CANARY_FIXTURE_ROOT_NOT_ABSOLUTE");
        Path readOnlySymlink = Paths.get(args[3]);
        if (!readOnlySymlink.isAbsolute())
            throw new IllegalArgumentException("CANARY_SYMLINK_NOT_ABSOLUTE");
        Path fixtureRoot = rootInput.toRealPath();
        URL[] classpath = classpathUrls(args[2]);
        GuardedLoader loader = new GuardedLoader(classpath);
        try {
            Class<?> argumentsClass = Class.forName(STUDY + "$Arguments", false, loader);
            Method parse = argumentsClass.getDeclaredMethod("parse", String[].class);
            parse.setAccessible(true);
            Path empty = fixtureRoot.resolve("empty").toRealPath();
            if ("SMOKE".equals(args[0])) runSmoke(argumentsClass, parse, empty);
            else runSuite(argumentsClass, parse, fixtureRoot, empty, readOnlySymlink);
            check(loader.deniedAttempts.isEmpty(), "DENIED_CLASS_LOAD_ATTEMPTS_ZERO");
            printSummary(args[0], loader.deniedAttempts.size());
        } finally {
            loader.close();
        }

        int status = failures.isEmpty() ? (blocked == 0 ? 0 : 2) : 1;
        if (status != 0) System.exit(status);
    }

    private static void runSmoke(Class<?> argumentsClass, Method parse, Path empty)
            throws Exception {
        positive(argumentsClass, parse, empty, "SMOKE");
    }

    private static void runSuite(Class<?> argumentsClass, Method parse,
            Path root, Path empty, Path readOnlySymlink) throws Exception {
        Path file = root.resolve("file.txt");
        Path nonempty = root.resolve("nonempty");
        Path junction = root.resolve("junction");
        Path missing = root.resolve("missing");

        check(Files.isDirectory(root.resolve("empty"), LinkOption.NOFOLLOW_LINKS),
                "FIXTURE_EMPTY_DIRECTORY_PRESENT");
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS),
                "FIXTURE_REGULAR_FILE_PRESENT");
        check(Files.isDirectory(nonempty, LinkOption.NOFOLLOW_LINKS),
                "FIXTURE_NONEMPTY_DIRECTORY_PRESENT");
        check(!Files.exists(missing, LinkOption.NOFOLLOW_LINKS),
                "FIXTURE_MISSING_PATH_ABSENT");
        BasicFileAttributes junctionAttrs = Files.readAttributes(junction,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        check(junctionAttrs.isDirectory() && !junctionAttrs.isSymbolicLink() &&
                junctionAttrs.isOther(), "FIXTURE_JUNCTION_NOFOLLOW_KIND");

        positive(argumentsClass, parse, empty, "SUITE");
        argumentFailures(parse, empty);
        identityFailures(parse, empty);

        String canonical = empty.toString();
        String rootText = root.toString();
        expectCode(parse, "PATH_NULL", "OUTPUT_PATH", withPath(empty, null));
        expectCode(parse, "PATH_EMPTY", "OUTPUT_PATH", withPath(empty, ""));
        expectCode(parse, "PATH_RELATIVE", "OUTPUT_PATH",
                withPath(empty, "relative\\output"));
        expectCode(parse, "PATH_FORWARD_SLASH", "OUTPUT_PATH",
                withPath(empty, canonical.replace('\\', '/')));
        expectCode(parse, "PATH_DOT_COMPONENT", "OUTPUT_PATH",
                withPath(empty, canonical + "\\."));
        expectCode(parse, "PATH_DOTDOT_COMPONENT", "OUTPUT_PATH",
                withPath(empty, canonical + "\\.."));
        expectCode(parse, "PATH_TRAILING_SEPARATOR", "OUTPUT_PATH",
                withPath(empty, canonical + "\\"));
        expectCode(parse, "PATH_DOUBLE_SEPARATOR", "OUTPUT_PATH",
                withPath(empty, rootText + "\\\\empty"));
        String alteredCase = flipFirstPathComponentLetter(canonical);
        String resolvedAlteredCase = resolvedSpelling(alteredCase);
        check(resolvedAlteredCase != null && !resolvedAlteredCase.equals(alteredCase),
                "FIXTURE_CASE_VARIANT_RESOLVES_TO_DIFFERENT_CANONICAL_SPELLING");
        expectCode(parse, "PATH_NONCANONICAL_CASE", "OUTPUT_PATH",
                withPath(empty, alteredCase));
        expectCode(parse, "PATH_MISSING", "OUTPUT_PATH",
                withPath(empty, missing.toString()));
        expectCode(parse, "PATH_REGULAR_FILE", "OUTPUT_PATH",
                withPath(empty, file.toString()));
        expectCode(parse, "PATH_NONEMPTY_DIRECTORY", "OUTPUT_NONEMPTY",
                withPath(empty, nonempty.toString()));
        expectCode(parse, "PATH_JUNCTION", "OUTPUT_PATH",
                withPath(empty, junction.toString()));
        expectCode(parse, "PATH_NUL", "OUTPUT_PATH",
                withPath(empty, canonical + Character.toString((char) 0)));

        boolean actualSymlink = false;
        try {
            BasicFileAttributes symlinkAttrs = Files.readAttributes(readOnlySymlink,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            actualSymlink = symlinkAttrs.isSymbolicLink();
        } catch (IOException absent) {
            actualSymlink = false;
        }
        if (actualSymlink) {
            expectCode(parse, "PATH_TRUE_SYMLINK", "OUTPUT_PATH",
                    withPath(empty, readOnlySymlink.toString()));
        } else {
            cases++;
            blocked++;
            symlinkBlocked = true;
        }
    }

    private static void positive(Class<?> argumentsClass, Method parse, Path empty,
            String label) throws Exception {
        cases++;
        String[] arguments = validArguments(empty.toString());
        Attempt result = invoke(parse, arguments);
        check(result.failure == null && result.value != null,
                label + "_POSITIVE_PARSE_SUCCEEDS");
        check(exactFields(argumentsClass), label + "_EXACT_FIVE_FIELDS_NO_LEGACY_FIELDS");
        check(fieldMatches(argumentsClass, result.value, "runId", String.class, RUN_ID),
                label + "_RUN_ID_FIELD_ROLE");
        check(fieldMatches(argumentsClass, result.value, "sourceManifestSha256",
                String.class, SHA_A), label + "_SOURCE_MANIFEST_FIELD_ROLE");
        check(fieldMatches(argumentsClass, result.value, "wireContractSha256",
                String.class, SHA_B), label + "_WIRE_CONTRACT_FIELD_ROLE");
        check(fieldMatches(argumentsClass, result.value, "inputContextSha256",
                String.class, SHA_C), label + "_INPUT_CONTEXT_FIELD_ROLE");
        check(fieldMatches(argumentsClass, result.value, "outputDir", Path.class, empty),
                label + "_OUTPUT_PATH_FIELD_ROLE");
    }

    private static void argumentFailures(Method parse, Path empty) throws Exception {
        expectCode(parse, "ARGS_NULL", "ARGS", null);
        String[] valid = validArguments(empty.toString());
        expectCode(parse, "ARGS_COUNT_ZERO", "ARGS", new String[0]);
        expectCode(parse, "ARGS_COUNT_SHORT", "ARGS", Arrays.copyOf(valid, 5));
        expectCode(parse, "ARGS_COUNT_LONG", "ARGS", Arrays.copyOf(valid, 7));

        expectCode(parse, "PROFILE_NULL", "ARGS", withArgument(empty, 0, null));
        expectCode(parse, "PROFILE_GENERIC_LEGACY", "ARGS",
                withArgument(empty, 0, "COHORT_TERMINAL_TELEMETRY"));
        expectCode(parse, "PROFILE_CANARY_LEGACY", "ARGS",
                withArgument(empty, 0, "COHORT_TERMINAL_CANARIES"));
        expectCode(parse, "PROFILE_OLD_VERSION", "ARGS",
                withArgument(empty, 0, "COHORT_TERMINAL_TELEMETRY_V1"));
        expectCode(parse, "PROFILE_BOGUS", "ARGS",
                withArgument(empty, 0, "COHORT_TERMINAL_TELEMETRY_V3"));
    }

    private static void identityFailures(Method parse, Path empty) throws Exception {
        expectCode(parse, "UUID_NULL", "UUID", withArgument(empty, 1, null));
        expectCode(parse, "UUID_MALFORMED", "UUID",
                withArgument(empty, 1, "not-a-uuid"));
        expectCode(parse, "UUID_UPPERCASE", "UUID",
                withArgument(empty, 1, UPPERCASE_UUID));

        String[] shaInvalid = {
            null,
            SHA_A.substring(1),
            "g" + SHA_A.substring(1),
            SHA_A.toUpperCase(Locale.ROOT)
        };
        for (int field = 2; field <= 4; field++) {
            for (int form = 0; form < shaInvalid.length; form++) {
                String[] arguments = validArguments(empty.toString());
                arguments[field] = shaInvalid[form];
                expectCode(parse, "SHA_FIELD_" + field + "_FORM_" + form,
                        "SHA", arguments);
            }
        }

        String nulPath = empty.toString() + Character.toString((char) 0);
        String[] badProfileBeforePath = validArguments(nulPath);
        badProfileBeforePath[0] = "COHORT_TERMINAL_TELEMETRY";
        expectCode(parse, "PROFILE_PRECEDES_NUL_PATH", "ARGS",
                badProfileBeforePath);
        String[] badUuidBeforePath = validArguments(nulPath);
        badUuidBeforePath[1] = "invalid";
        expectCode(parse, "UUID_PRECEDES_NUL_PATH", "UUID", badUuidBeforePath);
        String[] badShaBeforePath = validArguments(nulPath);
        badShaBeforePath[2] = "invalid";
        expectCode(parse, "SHA_PRECEDES_NUL_PATH", "SHA", badShaBeforePath);
    }

    private static void expectCode(Method parse, String label, String code,
            String[] arguments) throws Exception {
        cases++;
        Attempt result = invoke(parse, arguments);
        check(result.failure != null && result.failure.getClass() == IOException.class &&
                code.equals(result.failure.getMessage()), label + "_EXACT_" + code);
    }

    private static Attempt invoke(Method parse, String[] arguments) throws Exception {
        try {
            return new Attempt(parse.invoke(null, new Object[] {arguments}), null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            while (cause instanceof InvocationTargetException && cause.getCause() != null)
                cause = cause.getCause();
            return new Attempt(null, cause);
        }
    }

    private static boolean exactFields(Class<?> argumentsClass) {
        try {
            Field[] fields = argumentsClass.getDeclaredFields();
            if (fields.length != FIELD_NAMES.length) return false;
            Set<String> expected = new HashSet<String>(Arrays.asList(FIELD_NAMES));
            Set<String> actual = new HashSet<String>();
            for (Field field : fields) {
                if (!Modifier.isFinal(field.getModifiers())) return false;
                actual.add(field.getName());
                Class<?> expectedType = "outputDir".equals(field.getName()) ?
                        Path.class : String.class;
                if (field.getType() != expectedType) return false;
            }
            return expected.equals(actual);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean fieldMatches(Class<?> argumentsClass, Object value,
            String name, Class<?> expectedType, Object expectedValue) {
        if (value == null) return false;
        try {
            Field field = argumentsClass.getDeclaredField(name);
            field.setAccessible(true);
            Object actual = field.get(value);
            return field.getType() == expectedType && expectedValue.equals(actual);
        } catch (ReflectiveOperationException failure) {
            return false;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static String[] validArguments(String outputPath) {
        return new String[] {PROFILE, RUN_ID, SHA_A, SHA_B, SHA_C, outputPath};
    }

    private static String[] withPath(Path empty, String outputPath) {
        String[] arguments = validArguments(empty.toString());
        arguments[5] = outputPath;
        return arguments;
    }

    private static String[] withArgument(Path empty, int index, String value) {
        String[] arguments = validArguments(empty.toString());
        arguments[index] = value;
        return arguments;
    }

    private static String flipFirstPathComponentLetter(String value) {
        char[] chars = value.toCharArray();
        int start = chars.length >= 3 && isAsciiLetter(chars[0]) &&
                chars[1] == ':' && (chars[2] == '\\' || chars[2] == '/') ? 3 : 0;
        for (int i = start; i < chars.length; i++) {
            if (chars[i] == '\\' || chars[i] == '/') continue;
            if (chars[i] >= 'a' && chars[i] <= 'z') {
                chars[i] = (char) (chars[i] - ('a' - 'A'));
                return new String(chars);
            }
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] = (char) (chars[i] + ('a' - 'A'));
                return new String(chars);
            }
        }
        return value;
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private static String resolvedSpelling(String value) {
        try {
            return Paths.get(value).toRealPath().toString();
        } catch (IOException failure) {
            return null;
        } catch (RuntimeException failure) {
            return null;
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
        int completed = cases - blocked;
        System.out.println("SYNTHETIC_V2_ARGUMENTS_COMPONENT_ONLY mode=" + mode +
                " cases=" + cases + " completedCases=" + completed +
                " checks=" + checks + " blockedCases=" + blocked +
                " blockedSymlink=" + (symlinkBlocked ? "UNAVAILABLE" : "NONE") +
                " deniedAttempts=" + deniedAttempts + " failures=" + failures.size() +
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

    private static final class GuardedLoader extends URLClassLoader {
        final List<String> deniedAttempts = new ArrayList<String>();

        GuardedLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (denied(name)) {
                deniedAttempts.add(name);
                throw new ClassNotFoundException("DENIED_CLASS_LOAD");
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (studyOwned(name)) {
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

        private static boolean studyOwned(String name) {
            return name.equals(STUDY) || name.startsWith(STUDY + "$");
        }

        private static boolean denied(String name) {
            String routing = "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
            String input = "com.lushprojects.circuitjs1.client.CohortTerminalTelemetryInput";
            return name.equals(routing) || name.startsWith(routing + "$") ||
                    name.startsWith(input) || name.equals(STUDY + "$Parent");
        }
    }
}
