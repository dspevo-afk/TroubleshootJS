package com.lushprojects.circuitjs1.client;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Thin native adapter for one canonical request from the actual generator. */
public final class PrefixViaFirstRequestStudy {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String OWNER =
        "com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy";
    private static final long OUTPUT_LIMIT_BYTES = 8L * 1024L * 1024L;
    private static final long WRAPPER_GROUP_LIMIT_NANOS = 5000000000L;
    private static final long OPERATION_LIMIT_NANOS = 90000000000L;
    private static final long CLEANUP_LIMIT_NANOS = 120000000000L;
    private static final int INDEX_LIMIT_BYTES = 65536;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private PrefixViaFirstRequestStudy() { }

    public static void main(String[] args) {
        final long wrapperStarted = System.nanoTime();
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final SharedByteBudget outputBudget = new SharedByteBudget(OUTPUT_LIMIT_BYTES);
        final PrintStream boundedOut;
        final PrintStream boundedErr;
        try {
            boundedOut = new PrintStream(
                new CappedOutputStream(originalOut, outputBudget), true, "UTF-8");
            boundedErr = new PrintStream(
                new CappedOutputStream(originalErr, outputBudget), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
        System.setOut(boundedOut);
        System.setErr(boundedErr);

        Throwable failure = null;
        boolean captureOverflowReported = false;
        if (args != null && args.length == 1 && "--capture-overflow".equals(args[0])) {
            try {
                System.out.write(new byte[(int)OUTPUT_LIMIT_BYTES + 1], 0,
                    (int)OUTPUT_LIMIT_BYTES + 1);
                System.out.flush();
                throw new AssertionError("Oversized combined output was not rejected");
            } catch (CaptureLimitError expected) {
                if (!outputBudget.overflowed()) {
                    failure = new IllegalStateException(
                        "Output budget did not latch the rejected capture", expected);
                } else {
                    try {
                        System.err.println("PREFIX_VIA_CAPTURE_LIMIT");
                        System.err.flush();
                        captureOverflowReported = true;
                    } catch (Throwable markerFailure) {
                        failure = markerFailure;
                    }
                    if (failure == null) failure = expected;
                }
            } catch (Throwable unexpected) {
                failure = unexpected;
            }
        } else {
            try {
                run(args, wrapperStarted, outputBudget, boundedOut, boundedErr);
            } catch (Throwable problem) {
                failure = problem;
            }
        }

        if (failure != null && !captureOverflowReported) {
            try {
                if (failure instanceof CaptureLimitError || outputBudget.overflowed())
                    boundedErr.println("PREFIX_VIA_CAPTURE_LIMIT");
                else
                    failure.printStackTrace(boundedErr);
                boundedErr.flush();
            } catch (Throwable ignored) {
                // A failed bounded diagnostic still exits unsuccessfully.
            }
        }
        System.setOut(originalOut);
        System.setErr(originalErr);
        if (failure != null) System.exit(1);
    }

    private static void run(String[] args, long wrapperStarted, SharedByteBudget outputBudget,
            PrintStream boundedOut, PrintStream boundedErr) throws Exception {
        if (args == null || args.length != 5)
            throw new IllegalArgumentException(
                "Expected KIND RUN_UUID INPUT_SHA WIRE_SHA EMPTY_ABSOLUTE_DATA_DIR");

        String kind = args[0];
        if (!"TINY_CANARY".equals(kind) && !"AUTHORED".equals(kind))
            throw new IllegalArgumentException("KIND must be TINY_CANARY or AUTHORED");
        String indexKind = "TINY_CANARY".equals(kind) ? "TINY_NATIVE" : "AUTHORED";
        String runId = args[1];
        String sourceManifestSha256 = args[2];
        String wireSha256 = args[3];
        if (!UUID_PATTERN.matcher(runId).matches())
            throw new IllegalArgumentException("runId must be a lowercase UUID");
        requireSha(sourceManifestSha256, "INPUT_SHA");
        requireSha(wireSha256, "WIRE_SHA");

        Path outputDirectory = requireEmptyRealDirectory(args[4]);
        String profile = PrefixViaRampSupport.mode();
        if (!"BASE".equals(profile) && !"RAMP".equals(profile))
            throw new IllegalStateException("Immutable prefix VIA profile must be BASE or RAMP");

        Class<?> modeClass = Class.forName(OWNER + "$Mode");
        Method parseMode = modeClass.getDeclaredMethod("parse", String.class);
        parseMode.setAccessible(true);
        Object mode = invoke(parseMode, null, kind);

        Class<?> studyClass = Class.forName(OWNER + "$Study");
        Constructor<?> studyConstructor = studyClass.getDeclaredConstructor(modeClass,
            String.class, String.class, String.class, Path.class);
        studyConstructor.setAccessible(true);
        Object study = construct(studyConstructor, mode, runId, sourceManifestSha256,
            wireSha256, outputDirectory);

        Class<?> armRunClass = Class.forName(OWNER + "$ArmRun");
        Constructor<?> armRunConstructor =
            armRunClass.getDeclaredConstructor(studyClass, Integer.TYPE);
        armRunConstructor.setAccessible(true);
        Method execute = armRunClass.getDeclaredMethod("execute");
        execute.setAccessible(true);
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);

        long armRunStarted = System.nanoTime();
        long preparationNanos = elapsed(wrapperStarted, armRunStarted);
        requireGroupTime(preparationNanos, "preparation");
        Object armRun = construct(armRunConstructor, study, Integer.valueOf(1));
        Object armResult = invoke(execute, armRun);
        long postStarted = System.nanoTime();

        Class<?> resultClass = Class.forName(OWNER + "$ArmResult");
        if (!resultClass.isInstance(armResult))
            throw new IllegalStateException("ArmRun returned an unexpected actual result type");
        long receiptBytes = longField(armResult, "receiptBytes");
        String receiptSha256 = stringField(armResult, "receiptSha256");
        long elapsedToCloseNanos = longField(armResult, "elapsedToCloseNanos");
        long maxFullGroupNanos = longField(armResult, "maxFullGroupNanos");
        long cleanupNanos = longField(armResult, "cleanupNanos");
        boolean ownersReleased = booleanField(armResult, "ownersReleased");
        boolean contextRestored = booleanField(armResult, "contextRestored");
        if (!ownersReleased || !contextRestored)
            throw new IllegalStateException("Actual arm did not release owners and restore context");
        if (receiptBytes < 1 || receiptBytes > OUTPUT_LIMIT_BYTES ||
                !SHA_PATTERN.matcher(receiptSha256).matches())
            throw new IllegalStateException("Actual operation receipt is outside its frozen bounds");
        if (elapsedToCloseNanos <= 0 || elapsedToCloseNanos > OPERATION_LIMIT_NANOS ||
                maxFullGroupNanos < 0 || maxFullGroupNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                cleanupNanos < 0 || cleanupNanos > CLEANUP_LIMIT_NANOS)
            throw new IllegalStateException("Actual arm timings exceed the frozen limits");

        Path receiptPath = outputDirectory.resolve("op-1.jsonl");
        BasicFileAttributes receiptAttributes = Files.readAttributes(receiptPath,
            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!receiptAttributes.isRegularFile() || receiptAttributes.isOther() ||
                Files.isSymbolicLink(receiptPath) || Files.size(receiptPath) != receiptBytes)
            throw new IOException("Actual op-1 receipt is absent, non-regular, or size-mismatched");

        String descriptorText = descriptorJson(profile, indexKind, runId,
            sourceManifestSha256, wireSha256, receiptBytes, receiptSha256,
            elapsedToCloseNanos, maxFullGroupNanos, cleanupNanos);
        byte[] descriptorBytes = utf8(descriptorText + "\n");
        if (descriptorBytes.length > INDEX_LIMIT_BYTES)
            throw new IOException("One-arm descriptor exceeded 64 KiB");
        String descriptorSha256 = sha256(descriptorBytes);
        Path descriptorPath = outputDirectory.resolve("one-arm-index.json");
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(descriptorPath,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            output.write(descriptorBytes);
            output.flush();
        }

        long postNanos = elapsed(postStarted, System.nanoTime());
        long elapsedNanos = elapsed(wrapperStarted, System.nanoTime());
        requireGroupTime(postNanos, "post-execute descriptor");
        if (cleanupNanos > CLEANUP_LIMIT_NANOS)
            throw new IOException("Actual cleanup exceeded 120 seconds");
        long workNanos = subtractCleanup(elapsedNanos, cleanupNanos);
        if (workNanos > OPERATION_LIMIT_NANOS)
            throw new IOException("Wrapper work excluding actual cleanup exceeded 90 seconds");

        String trailerJson = trailerJson(profile, indexKind, runId, sourceManifestSha256,
            wireSha256, descriptorBytes.length, descriptorSha256, preparationNanos,
            postNanos, elapsedNanos, workNanos);
        byte[] trailer = utf8("PREFIX_VIA_FIRST_REQUEST_COMPLETE " + trailerJson + "\n");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != 0)
            throw new IOException("Producer emitted output before the required success trailer");
        boundedOut.write(trailer, 0, trailer.length);
        boundedOut.flush();

        long finalElapsedNanos = elapsed(wrapperStarted, System.nanoTime());
        long finalPostNanos = elapsed(postStarted, System.nanoTime());
        requireGroupTime(finalPostNanos, "post-execute descriptor and output");
        if (subtractCleanup(finalElapsedNanos, cleanupNanos) > OPERATION_LIMIT_NANOS)
            throw new IOException("Final wrapper work excluding actual cleanup exceeded 90 seconds");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != trailer.length)
            throw new IOException("Success output was not exactly one bounded trailer line");
    }

    private static Path requireEmptyRealDirectory(String value) throws IOException {
        Path path = Paths.get(value);
        if (!path.isAbsolute() || !path.normalize().equals(path))
            throw new IllegalArgumentException("output directory must be normalized and absolute");
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IllegalArgumentException("output directory must be an existing real directory");
        BasicFileAttributes attributes = Files.readAttributes(path,
            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isOther())
            throw new IllegalArgumentException("output directory must not be a reparse or special entry");
        Path realPath = path.toRealPath();
        if (!realPath.equals(path))
            throw new IllegalArgumentException("output directory path resolves through a reparse point");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
            if (entries.iterator().hasNext())
                throw new IllegalArgumentException("output directory must be empty before Java writes");
        }
        return realPath;
    }

    private static String descriptorJson(String profile, String kind, String runId,
            String sourceSha, String wireSha, long receiptBytes, String receiptSha,
            long elapsedToCloseNanos, long maxFullGroupNanos, long cleanupNanos) {
        StringBuilder json = new StringBuilder(900);
        json.append("{\"schema\":\"tsj-via-price-first-request-index-v1\",\"profile\":");
        appendString(json, profile);
        json.append(",\"kind\":");
        appendString(json, kind);
        json.append(",\"runId\":");
        appendString(json, runId);
        json.append(",\"sourceManifestSha256\":");
        appendString(json, sourceSha);
        json.append(",\"wireSha256\":");
        appendString(json, wireSha);
        json.append(",\"scope\":\"ONE_CANONICAL_REQUEST_ONLY\",\"layoutQualification\":false,");
        json.append("\"operations\":[{\"operation\":1,\"armId\":\"OBSERVED_DEFAULT_NEAREST\",");
        json.append("\"receipt\":{\"path\":\"op-1.jsonl\",\"bytes\":");
        json.append(receiptBytes).append(",\"sha256\":");
        appendString(json, receiptSha);
        json.append("},\"elapsedToCloseNanos\":").append(elapsedToCloseNanos);
        json.append(",\"maxFullGroupNanos\":").append(maxFullGroupNanos);
        json.append(",\"cleanup\":{\"status\":\"PASS\",\"elapsedNanos\":");
        json.append(cleanupNanos).append(",\"ownersReleased\":true,\"contextRestored\":true}}]}");
        return json.toString();
    }

    private static String trailerJson(String profile, String kind, String runId,
            String sourceSha, String wireSha, int descriptorBytes, String descriptorSha,
            long preparationNanos, long postNanos, long elapsedNanos, long workNanos) {
        StringBuilder json = new StringBuilder(700);
        json.append("{\"schema\":\"tsj-via-price-first-request-native-v1\",\"profile\":");
        appendString(json, profile);
        json.append(",\"kind\":");
        appendString(json, kind);
        json.append(",\"runId\":");
        appendString(json, runId);
        json.append(",\"sourceManifestSha256\":");
        appendString(json, sourceSha);
        json.append(",\"wireSha256\":");
        appendString(json, wireSha);
        json.append(",\"descriptor\":{\"path\":\"one-arm-index.json\",\"bytes\":");
        json.append(descriptorBytes).append(",\"sha256\":");
        appendString(json, descriptorSha);
        json.append("},\"wrapper\":{\"preparationNanos\":").append(preparationNanos);
        json.append(",\"postNanos\":").append(postNanos);
        json.append(",\"elapsedNanos\":").append(elapsedNanos);
        json.append(",\"workNanos\":").append(workNanos);
        json.append(",\"groups\":2},\"scope\":\"ONE_CANONICAL_REQUEST_ONLY\",");
        json.append("\"layoutQualification\":false}");
        return json.toString();
    }

    private static Object construct(Constructor<?> constructor, Object... arguments)
            throws Exception {
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException failure) {
            rethrow(failure.getCause());
            throw new AssertionError("Unreachable constructor failure");
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            rethrow(failure.getCause());
            throw new AssertionError("Unreachable invocation failure");
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new RuntimeException(failure);
    }

    private static long longField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof Long))
            throw new IllegalStateException("Actual ArmResult field is not long: " + name);
        return ((Long)value).longValue();
    }

    private static String stringField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof String))
            throw new IllegalStateException("Actual ArmResult field is not String: " + name);
        return (String)value;
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof Boolean))
            throw new IllegalStateException("Actual ArmResult field is not boolean: " + name);
        return ((Boolean)value).booleanValue();
    }

    private static Object field(Object target, String name) throws Exception {
        if (target == null) throw new IllegalStateException("Missing actual ArmResult");
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Actual owner omitted field " + name);
    }

    private static void requireOutputHealthy(PrintStream out, PrintStream err,
            SharedByteBudget budget) {
        if (budget.overflowed() || out.checkError() || err.checkError())
            throw new CaptureLimitError();
    }

    private static void requireSha(String value, String name) {
        if (value == null || !SHA_PATTERN.matcher(value).matches())
            throw new IllegalArgumentException(name + " must be lowercase SHA-256 text");
    }

    private static long subtractCleanup(long elapsedNanos, long cleanupNanos) {
        if (elapsedNanos < cleanupNanos)
            throw new IllegalStateException("Wrapper elapsed time is smaller than actual cleanup");
        return elapsedNanos - cleanupNanos;
    }

    private static void requireGroupTime(long nanos, String group) throws IOException {
        if (nanos < 0 || nanos > WRAPPER_GROUP_LIMIT_NANOS)
            throw new IOException("Wrapper " + group + " group exceeded five seconds");
    }

    private static long elapsed(long started, long ended) {
        long value = ended - started;
        return value < 0 ? Long.MAX_VALUE : value;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(UTF8);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            char[] hex = new char[digest.length * 2];
            final char[] digits = "0123456789abcdef".toCharArray();
            for (int index = 0; index < digest.length; index++) {
                int b = digest[index] & 255;
                hex[index * 2] = digits[b >>> 4];
                hex[index * 2 + 1] = digits[b & 15];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void appendString(StringBuilder json, String value) {
        if (value == null) throw new IllegalArgumentException("JSON string value is null");
        json.append('"');
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '"' || current == '\\') {
                json.append('\\').append(current);
            } else if (current < 0x20) {
                json.append("\\u00").append(hex[(current >>> 4) & 15])
                    .append(hex[current & 15]);
            } else if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() ||
                        !Character.isLowSurrogate(value.charAt(index + 1)))
                    throw new IllegalArgumentException("Unpaired high surrogate in JSON string");
                json.append(current).append(value.charAt(++index));
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Unpaired low surrogate in JSON string");
            } else {
                json.append(current);
            }
        }
        json.append('"');
    }

    private static final class SharedByteBudget {
        private final long maximum;
        private long used;
        private boolean overflowed;

        SharedByteBudget(long maximum) { this.maximum = maximum; }

        synchronized void reserve(int length) {
            if (length < 0 || (long)length > maximum - used) {
                overflowed = true;
                throw new CaptureLimitError();
            }
            used += length;
        }

        synchronized boolean overflowed() { return overflowed; }
        synchronized long bytesUsed() { return used; }
    }

    private static final class CappedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final SharedByteBudget budget;

        CappedOutputStream(OutputStream delegate, SharedByteBudget budget) {
            this.delegate = delegate;
            this.budget = budget;
        }

        public void write(int value) throws IOException {
            budget.reserve(1);
            delegate.write(value);
        }

        public void write(byte[] values, int offset, int length) throws IOException {
            if (values == null) throw new NullPointerException("values");
            if (offset < 0 || length < 0 || offset > values.length - length)
                throw new IndexOutOfBoundsException();
            budget.reserve(length);
            delegate.write(values, offset, length);
        }

        public void flush() throws IOException { delegate.flush(); }
        public void close() throws IOException { flush(); }
    }

    private static final class CaptureLimitError extends Error {
        private static final long serialVersionUID = 1L;
        CaptureLimitError() { super("PREFIX_VIA_CAPTURE_LIMIT"); }
    }
}
