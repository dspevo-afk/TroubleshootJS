package com.lushprojects.circuitjs1.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

/** Native adapter for the one immutable canonical BASE request-zero prefix. */
public final class CanonicalPrefixStageStudy {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final String OWNER =
        "com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy";
    private static final String SCOPE = "CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY";
    private static final String CASE_ID = "CANONICAL_SEPARATE_REQ0_PASS2_A_COIL_LOW";
    private static final String EXPECTED_PREFIX_SHA256 =
        "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final long EXPECTED_ROOT_SEED = 9007199254740993L;
    private static final long EXPECTED_PLACEMENT_SEED = -6587958367974952701L;
    private static final long EXPECTED_ROUTING_SEED = 8445301674744508279L;
    private static final int EXPECTED_PREFIX_BYTES = 265894;
    private static final int EXPECTED_BRANCH_EXPANDED = 25;
    private static final int EXPECTED_TOTAL_EXPANSIONS = 918222;
    private static final int RECEIPT_BRANCH_ORDINAL = 307;
    private static final int LINEAGE_BRANCH_ORDINAL = 77;
    private static final int PASS = 2;
    private static final int MAX_VIAS = 48;
    private static final long OUTPUT_LIMIT_BYTES = 8L * 1024L * 1024L;
    private static final long WRAPPER_GROUP_LIMIT_NANOS = 5000000000L;
    private static final long OPERATION_LIMIT_NANOS = 90000000000L;
    private static final long CLEANUP_LIMIT_NANOS = 120000000000L;
    private static final int INDEX_LIMIT_BYTES = 65536;
    private static final int CHUNK_BYTES = 32768;
    private static final int LINE_LIMIT_BYTES = 65536;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private CanonicalPrefixStageStudy() { }

    public static void main(String[] args) {
        final long wrapperStarted = System.nanoTime();
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final SharedByteBudget outputBudget = new SharedByteBudget(OUTPUT_LIMIT_BYTES);
        final PrintStream boundedOut;
        final PrintStream boundedErr;
        try {
            boundedOut = new PrintStream(new CappedOutputStream(originalOut, outputBudget),
                true, "UTF-8");
            boundedErr = new PrintStream(new CappedOutputStream(originalErr, outputBudget),
                true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 is unavailable", impossible);
        }
        System.setOut(boundedOut);
        System.setErr(boundedErr);

        Throwable failure = null;
        boolean overflowProbe = args != null && args.length == 1 &&
            "--capture-overflow".equals(args[0]);
        int exitStatus = 0;
        try {
            if (overflowProbe) {
                try {
                    System.out.write(new byte[(int)OUTPUT_LIMIT_BYTES + 1], 0,
                        (int)OUTPUT_LIMIT_BYTES + 1);
                    System.out.flush();
                    throw new AssertionError("Combined output cap accepted an oversized write");
                } catch (CaptureLimitError expected) {
                    if (!outputBudget.overflowed() || outputBudget.bytesUsed() != 0)
                        throw new IllegalStateException(
                            "Output overflow canary did not reject before forwarding bytes");
                    exitStatus = 1;
                }
            } else if (args != null && args.length == 1 && "--canary".equals(args[0])) {
                runCanary(wrapperStarted);
            } else {
                exitStatus = run(args, wrapperStarted, boundedOut, boundedErr, outputBudget);
            }
        } catch (Throwable problem) {
            failure = problem;
        }

        if (failure != null && !outputBudget.overflowed()) {
            try {
                failure.printStackTrace(boundedErr);
                boundedErr.flush();
            } catch (Throwable ignored) {
                // A failed bounded diagnostic still exits unsuccessfully.
            }
        }
        boolean captureFailed = outputBudget.overflowed() || failure instanceof CaptureLimitError;
        if (captureFailed) {
            try {
                boundedErr.println("CANONICAL_PREFIX_CAPTURE_LIMIT");
                boundedErr.flush();
            } catch (Throwable ignored) {
                // Never bypass the shared byte budget, even for failure diagnostics.
            }
        }
        try { boundedOut.flush(); } catch (Throwable ignored) { }
        try { boundedErr.flush(); } catch (Throwable ignored) { }
        System.setOut(originalOut);
        System.setErr(originalErr);

        if (captureFailed || failure != null || exitStatus != 0) System.exit(1);
    }

    private static int run(String[] args, long wrapperStarted, PrintStream boundedOut,
            PrintStream boundedErr, SharedByteBudget outputBudget) throws Exception {
        if (args == null || args.length != 5)
            throw new IllegalArgumentException(
                "Expected MODE RUN_UUID SOURCE_MANIFEST_SHA256 WIRE_SHA256 EMPTY_ABSOLUTE_DATA_DIR");
        String mode = args[0];
        if (!"NULL".equals(mode) && !"OBSERVE".equals(mode) &&
                !"CALLBACK_THROW".equals(mode))
            throw new IllegalArgumentException("MODE must be NULL, OBSERVE, or CALLBACK_THROW");
        String runId = args[1];
        String sourceManifestSha256 = args[2];
        String wireSha256 = args[3];
        if (!UUID_PATTERN.matcher(runId).matches())
            throw new IllegalArgumentException("runId must be a lowercase UUID");
        requireSha(sourceManifestSha256, "SOURCE_MANIFEST_SHA256");
        requireSha(wireSha256, "WIRE_SHA256");
        if (!"BASE".equals(PrefixViaRampSupport.mode()))
            throw new IllegalStateException("Canonical prefix capture requires the immutable BASE profile");
        Path outputDirectory = requireEmptyRealDirectory(args[4]);
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);

        OneShotObserver observer = null;
        CanonicalPrefixStageInspector inspector = null;
        CanonicalPrefixBlockerInspector blocker = null;
        ActualGeneratorBranchOrderStudy.TerminalPrefixInspector terminalInspector = null;
        Object study = null;
        Object armRun = null;
        Object armResult = null;
        byte[] graphBytes = null;
        byte[] factsBytes = null;
        byte[] transportBytes = null;
        byte[] indexBytes = null;
        byte[] attributionBytes = null;
        byte[] stageBytes = null;
        String graphPayload = null;
        String factsPayload = null;
        Path transportPath = null;
        Path indexPath = null;
        try {
            final boolean inspectorThrow = "CALLBACK_THROW".equals(mode);
            observer = new OneShotObserver();
            inspector = new CanonicalPrefixStageInspector(runId, sourceManifestSha256,
                wireSha256, !"NULL".equals(mode), inspectorThrow);
            if (inspectorThrow) terminalInspector = inspector;
            else {
                blocker = new CanonicalPrefixBlockerInspector(runId,
                    sourceManifestSha256, wireSha256, false);
                terminalInspector = new CompositeInspector(blocker, inspector);
            }

            Class<?> modeClass = Class.forName(OWNER + "$Mode");
            Method parseMode = modeClass.getDeclaredMethod("parse", String.class);
            parseMode.setAccessible(true);
            Object authoredMode = invoke(parseMode, null, "AUTHORED");
            Class<?> studyClass = Class.forName(OWNER + "$Study");
            Constructor<?> studyConstructor = studyClass.getDeclaredConstructor(modeClass,
                String.class, String.class, String.class, Path.class);
            studyConstructor.setAccessible(true);
            study = construct(studyConstructor, authoredMode, runId, sourceManifestSha256,
                wireSha256, outputDirectory);

            Class<?> armRunClass = Class.forName(OWNER + "$ArmRun");
            Constructor<?> armRunConstructor;
            Object[] armArguments;
            armRunConstructor = armRunClass.getDeclaredConstructor(studyClass,
                Integer.TYPE, PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
            armArguments = new Object[] {study, Integer.valueOf(1), observer, terminalInspector};
            armRunConstructor.setAccessible(true);
            Method execute = armRunClass.getDeclaredMethod("execute");
            execute.setAccessible(true);
            long preparationNanos = elapsed(wrapperStarted, System.nanoTime());
            requireGroupTime(preparationNanos, "preparation");
            armRun = construct(armRunConstructor, armArguments);
            try {
                armResult = invoke(execute, armRun);
            } catch (Throwable error) {
                if (!inspectorThrow || error.getClass() != IllegalStateException.class ||
                        !"CANONICAL_PREFIX_STAGE_CALLBACK_THROW".equals(error.getMessage()) ||
                        error.getSuppressed().length != 0 || inspector == null ||
                        !inspector.expectedExceptionObserved() || !inspector.referencesDropped()) {
                    rethrow(error);
                    throw new AssertionError("Unreachable");
                }
                long exceptionPostStarted = System.nanoTime();
                Path partialPath = outputDirectory.resolve("op-1.jsonl");
                FileEvidence partial = requireRegularFile(partialPath, "partial operation receipt");
                requireOnlyReceipt(outputDirectory, partialPath);
                String inspection = inspector.expectedExceptionJson();
                if (utf8(inspection).length > INDEX_LIMIT_BYTES)
                    throw new IOException("Expected inspector exception metadata exceeded64KiB");
                long inclusiveWork = elapsed(wrapperStarted, System.nanoTime());
                if (inclusiveWork > OPERATION_LIMIT_NANOS)
                    throw new IOException("Expected inspector exception exceeded90s inclusive clock");
                String marker = expectedExceptionJson(runId, sourceManifestSha256,
                    wireSha256, partial, inspection, inclusiveWork);
                if (marker.indexOf('\r') >= 0 || marker.indexOf('\n') >= 0 ||
                        utf8("CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW " + marker + "\n").length > LINE_LIMIT_BYTES)
                    throw new IOException("Expected inspector exception marker exceeded its line bound");
                if (outputBudget.bytesUsed() != 0)
                    throw new IOException("Unexpected output before inspector exception marker");
                boundedErr.print("CANONICAL_PREFIX_STAGE_EXPECTED_CALLBACK_THROW " + marker + "\n");
                boundedErr.flush();
                requireOutputHealthy(boundedOut, boundedErr, outputBudget);
                requireGroupTime(elapsed(exceptionPostStarted, System.nanoTime()),
                    "expected inspector exception export");
                if (elapsed(wrapperStarted, System.nanoTime()) > OPERATION_LIMIT_NANOS)
                    throw new IOException("Expected inspector exception final output exceeded90s");
                return 1;
            }
            if (inspectorThrow)
                throw new IllegalStateException("Expected inspector exception did not occur");
            long afterOwner = System.nanoTime();
            long graphExportStarted = afterOwner;
            Object actualResult = armResult;
            ArmEvidence arm = readArmEvidence(actualResult);
            if (!arm.ownersReleased || !arm.contextRestored)
                throw new IllegalStateException(
                    "Actual ArmRun did not release owners and restore its native context");
            if (arm.elapsedToCloseNanos <= 0 || arm.elapsedToCloseNanos > OPERATION_LIMIT_NANOS ||
                    arm.maxFullGroupNanos < 0 || arm.maxFullGroupNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                    arm.cleanupNanos < 0 || arm.cleanupNanos > CLEANUP_LIMIT_NANOS)
                throw new IOException("Actual operation or cleanup exceeded its frozen limit");

            Path receiptPath = outputDirectory.resolve("op-1.jsonl");
            FileEvidence receipt = requireRegularFile(receiptPath, "actual op-1 receipt");
            if (receipt.bytes != arm.receiptBytes || !receipt.sha256.equals(arm.receiptSha256) ||
                    receipt.bytes < 1 || receipt.bytes > OUTPUT_LIMIT_BYTES)
                throw new IOException("Actual operation receipt differs from its ArmResult identity");
            if (receipt.bytes > OUTPUT_LIMIT_BYTES - INDEX_LIMIT_BYTES)
                throw new IOException("Actual receipt leaves no reserved space for its index");
            ReceiptEvidence receiptEvidence = scanReceipt(receiptPath, receipt.bytes);
            if (receiptEvidence.groupCount + 3 > 640)
                throw new IOException("Actual groups plus three wrapper groups exceed 640");
            String operationJson = operationJson(arm, receipt);

            String profile = PrefixViaRampSupport.mode();
            if (!"BASE".equals(profile))
                throw new IllegalStateException("Capture profile changed from immutable BASE");
            CandidateEvidence candidate = readAndValidateCandidate(armRun);
            CapturedOwnerContext ownerContext = readAndValidateOwnerContext(armRun,
                candidate, observer);

            {
                observer.requireOneCapture();
                PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot = observer.snapshot;
                PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage = observer.selectedLineage;
                validateCapture(candidate, ownerContext, lineage, snapshot);
                graphPayload = Rb100PhysicalDiagnostics.canonicalFixedPrefixGraphPayloadForStudy(
                    snapshot, CASE_ID);
                graphBytes = utf8(graphPayload);
                graphPayload = null;
                if (graphBytes.length < 1 || graphBytes.length > OUTPUT_LIMIT_BYTES)
                    throw new IOException("Canonical serializer graph is outside the frozen bounds");
                factsPayload = factsJson(candidate, ownerContext, lineage, snapshot, runId,
                    sourceManifestSha256, wireSha256);
                factsBytes = utf8(factsPayload);
                factsPayload = null;
                if (factsBytes.length < 1 || factsBytes.length > OUTPUT_LIMIT_BYTES)
                    throw new IOException("Canonical facts payload is outside the frozen transport bounds");
                snapshot = null;
                lineage = null;
                ownerContext = null;

                String attributionPayload = blocker.completedJson(sha256(graphBytes));
                if (attributionPayload.indexOf('\r') >= 0 || attributionPayload.indexOf('\n') >= 0)
                    throw new IOException("Inspector payload must be one bare JSON object");
                attributionBytes = utf8(attributionPayload + "\n");
                attributionPayload = null;
                if (attributionBytes.length < 1 || attributionBytes.length > INDEX_LIMIT_BYTES ||
                        !blocker.referencesDropped())
                    throw new IOException("Inspector payload or source release exceeded its contract");
                String stagePayload = inspector.completedJson(sha256(graphBytes));
                if (stagePayload.indexOf('\r') >= 0 || stagePayload.indexOf('\n') >= 0)
                    throw new IOException("Stage helper emitted a framed JSON string");
                stageBytes = utf8(stagePayload + "\n");
                stagePayload = null;
                if (stageBytes.length < 1 || stageBytes.length > INDEX_LIMIT_BYTES ||
                        !inspector.referencesDropped())
                    throw new IOException("Stage payload or references exceeded their bounds");
                long availableTransport = OUTPUT_LIMIT_BYTES - receipt.bytes -
                    INDEX_LIMIT_BYTES - attributionBytes.length - stageBytes.length;
                transportBytes = transportBytes(runId, sourceManifestSha256, wireSha256,
                    graphBytes, factsBytes, availableTransport);
                if (transportBytes.length < 1 || transportBytes.length > availableTransport)
                    throw new IOException("Canonical transport exceeds aggregate capacity before write");
                observer.drop();

                transportPath = outputDirectory.resolve("fixed-prefix.jsonl");
                writeNewFile(transportPath, transportBytes);
                FileEvidence transport = requireRegularFile(transportPath,
                    "canonical fixed-prefix transport");
                if (transport.bytes != transportBytes.length ||
                        !transport.sha256.equals(sha256(transportBytes)))
                    throw new IOException("Canonical transport file changed after CREATE_NEW write");
                String transportJson = transportJson(transport, graphBytes, factsBytes);
                Path attributionPath = outputDirectory.resolve("prefix-blockers.json");
                writeNewFile(attributionPath, attributionBytes);
                FileEvidence attribution = requireRegularFile(attributionPath, "blocker attribution");
                if (attribution.bytes != attributionBytes.length ||
                        !attribution.sha256.equals(sha256(attributionBytes)))
                    throw new IOException("Attribution changed after CREATE_NEW write");
                String attributionJson = fileReferenceJson("prefix-blockers.json", attribution);
                Path stagePath = outputDirectory.resolve("prefix-stages.json");
                writeNewFile(stagePath, stageBytes);
                FileEvidence stages = requireRegularFile(stagePath, "predicate stages");
                if (stages.bytes != stageBytes.length || !stages.sha256.equals(sha256(stageBytes)))
                    throw new IOException("Stage payload changed after CREATE_NEW write");
                String stagesJson = fileReferenceJson("prefix-stages.json", stages);

                long graphExportNanos = elapsed(graphExportStarted, System.nanoTime());
                requireGroupTime(graphExportNanos, "graph export");
                long postStarted = System.nanoTime();
                String indexText = indexJson(mode, profile, runId, sourceManifestSha256,
                    wireSha256, operationJson, transportJson, attributionJson, stagesJson, observer.calls, 1, 1);
                indexBytes = utf8(indexText + "\n");
                if (indexBytes.length > INDEX_LIMIT_BYTES)
                    throw new IOException("Canonical prefix index exceeded its 64 KiB bound");
                long aggregateBytes = receipt.bytes + transport.bytes + attribution.bytes + stages.bytes + indexBytes.length;
                if (aggregateBytes > OUTPUT_LIMIT_BYTES)
                    throw new IOException("Operation, transport and index exceed aggregate 8 MiB");
                indexPath = outputDirectory.resolve("canonical-stage-index.json");
                writeNewFile(indexPath, indexBytes);
                FileEvidence index = requireRegularFile(indexPath, "canonical prefix index");
                if (index.bytes != indexBytes.length || !index.sha256.equals(sha256(indexBytes)))
                    throw new IOException("Canonical index changed after CREATE_NEW write");
                zero(graphBytes); zero(factsBytes); zero(transportBytes); zero(indexBytes); zero(attributionBytes); zero(stageBytes);
                graphBytes = null; factsBytes = null; transportBytes = null; indexBytes = null; attributionBytes = null; stageBytes = null;
                postAndEmit(mode, runId, sourceManifestSha256, wireSha256, index,
                    preparationNanos, graphExportNanos, postStarted, wrapperStarted,
                    arm.cleanupNanos, aggregateBytes, boundedOut, boundedErr, outputBudget);
            }
            return 0;
        } finally {
            if (observer != null) observer.drop();
            zero(graphBytes);
            zero(factsBytes);
            zero(transportBytes);
            zero(indexBytes);
            zero(attributionBytes);
            zero(stageBytes);
            inspector = null;
            blocker = null;
            terminalInspector = null;
            stageBytes = null;
            attributionBytes = null;
            graphBytes = null;
            factsBytes = null;
            transportBytes = null;
            indexBytes = null;
            graphPayload = null;
            factsPayload = null;
            armResult = null;
            armRun = null;
            study = null;
        }
    }

    private static final class CompositeInspector
            implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
        final CanonicalPrefixBlockerInspector blocker;
        final CanonicalPrefixStageInspector stages;
        CompositeInspector(CanonicalPrefixBlockerInspector blocker, CanonicalPrefixStageInspector stages) {
            if (blocker == null || stages == null) throw new IllegalArgumentException("Missing inspector");
            this.blocker = blocker; this.stages = stages;
        }
        public void onTerminalPrefix(Object search, PcbBoardLayout layout,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot, String fingerprint,
                String prefixSha, int prefixBytes, int receiptOrdinal, int groupOrdinal) {
            blocker.onTerminalPrefix(search, layout, snapshot, fingerprint, prefixSha,
                prefixBytes, receiptOrdinal, groupOrdinal);
            if (receiptOrdinal == RECEIPT_BRANCH_ORDINAL) {
                long units = counter("copyUnits") + counter("comparisonUnits") +
                    counter("queryUnits") + counter("geometryRowUnits");
                if (units > 32768L) throw new IllegalStateException("Prior observation unit bound");
                stages.setPriorObservationUnits(units);
            }
            stages.onTerminalPrefix(search, layout, snapshot, fingerprint, prefixSha,
                prefixBytes, receiptOrdinal, groupOrdinal);
        }
        private long counter(String name) {
            try {
                long value = longField(blocker, name);
                if (value < 0 || value > 32768L) throw new IllegalStateException("Prior counter bound");
                return value;
            } catch (Exception error) { throw new IllegalStateException("Prior observation counter", error); }
        }
        public void afterOwnersClosed(boolean released, boolean restored) {
            Throwable failure = null;
            try { blocker.afterOwnersClosed(released, restored); }
            catch (Throwable error) { failure = error; }
            try { stages.afterOwnersClosed(released, restored); }
            catch (Throwable error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null) throw new IllegalStateException("Inspector cleanup", failure);
        }
    }

    private static void postAndEmit(String mode, String runId, String sourceManifestSha256,
            String wireSha256, FileEvidence index, long preparationNanos,
            long graphExportNanos, long postStarted, long wrapperStarted, long cleanupNanos,
            long aggregateBytes, PrintStream boundedOut, PrintStream boundedErr,
            SharedByteBudget outputBudget) throws IOException {
        long postNanos = elapsed(postStarted, System.nanoTime());
        long elapsedNanos = elapsed(wrapperStarted, System.nanoTime());
        requireGroupTime(postNanos, "post-index");
        long workNanos = subtractCleanup(elapsedNanos, cleanupNanos);
        if (workNanos > OPERATION_LIMIT_NANOS)
            throw new IOException("Wrapper work excluding actual ArmResult cleanup exceeded 90 seconds");
        if (preparationNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                graphExportNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                postNanos > WRAPPER_GROUP_LIMIT_NANOS)
            throw new IOException("One or more canonical wrapper groups exceeded five seconds");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != 0)
            throw new IOException("Producer emitted output before the required success trailer");
        StringBuilder json = new StringBuilder(1100);
        json.append("{\"schema\":\"tsj-canonical-prefix-stage-native-v1\",\"mode\":");
        appendString(json, mode);
        json.append(",\"runId\":");
        appendString(json, runId);
        json.append(",\"sourceManifestSha256\":");
        appendString(json, sourceManifestSha256);
        json.append(",\"wireSha256\":");
        appendString(json, wireSha256);
        json.append(",\"scope\":");
        appendString(json, SCOPE);
        json.append(",\"layoutQualification\":false,\"index\":{\"path\":\"canonical-stage-index.json\",\"bytes\":");
        json.append(index.bytes).append(",\"sha256\":");
        appendString(json, index.sha256);
        json.append("},\"wrapper\":{\"preparationNanos\":").append(preparationNanos);
        json.append(",\"graphExportNanos\":").append(graphExportNanos);
        json.append(",\"postNanos\":").append(postNanos);
        json.append(",\"elapsedNanos\":").append(elapsedNanos);
        json.append(",\"workNanos\":").append(workNanos);
        json.append(",\"groups\":3},\"aggregateBytes\":").append(aggregateBytes).append('}');
        byte[] trailer = utf8("CANONICAL_PREFIX_STAGE_COMPLETE " + json.toString() + "\n");
        boundedOut.write(trailer, 0, trailer.length);
        boundedOut.flush();
        long finalElapsed = elapsed(wrapperStarted, System.nanoTime());
        long finalPost = elapsed(postStarted, System.nanoTime());
        requireGroupTime(finalPost, "post-index and output");
        if (subtractCleanup(finalElapsed, cleanupNanos) > OPERATION_LIMIT_NANOS)
            throw new IOException("Final wrapper work excluding actual cleanup exceeded 90 seconds");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != trailer.length)
            throw new IOException("Success output was not exactly one bounded trailer line");
    }

    private static void runCanary(long started) throws Exception {
        long maximumGroupNanos = 0;
        int groups = 0;
        int assertions = 0;
        P07FixedPrefixObservationContractTest.main(new String[0]);
        long fixtureNanos = elapsed(started, System.nanoTime());
        requireGroupTime(fixtureNanos, "P07 fixed-prefix contract canary");
        maximumGroupNanos = Math.max(maximumGroupNanos, fixtureNanos);
        groups++;

        long guardStarted = System.nanoTime();
        Class<?> studyClass = Class.forName(OWNER + "$Study");
        Class<?> armRunClass = Class.forName(OWNER + "$ArmRun");
        Constructor<?> defaultConstructor = armRunClass.getDeclaredConstructor(studyClass,
            Integer.TYPE);
        Constructor<?> observerConstructor = armRunClass.getDeclaredConstructor(studyClass,
            Integer.TYPE, PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class);
        defaultConstructor.setAccessible(true);
        observerConstructor.setAccessible(true);
        construct(defaultConstructor, null, Integer.valueOf(4));
        assertions++;
        construct(observerConstructor, null, Integer.valueOf(1), null);
        assertions++;
        PcbLayerRoutingPrototype.FixedPrefixGraphObserver noop =
            new PcbLayerRoutingPrototype.FixedPrefixGraphObserver() {
                public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) {
                    return false;
                }
                public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot) { }
            };
        boolean rejectedNonRequestOne = false;
        try {
            construct(observerConstructor, null, Integer.valueOf(2), noop);
        } catch (IllegalArgumentException expected) {
            rejectedNonRequestOne = true;
        }
        require(rejectedNonRequestOne,
            "Observer-injected ArmRun constructor must reject operations other than one");
        assertions++;

        require(matchesCaptureTarget(PASS, LINEAGE_BRANCH_ORDINAL, "A_COIL_LOW", "QA.C",
                "DA.A", "NO_PATH", EXPECTED_BRANCH_EXPANDED, EXPECTED_TOTAL_EXPANSIONS),
            "Canonical lineage selector must match the frozen within-pass target");
        require(!matchesCaptureTarget(PASS, RECEIPT_BRANCH_ORDINAL, "A_COIL_LOW", "QA.C",
                "DA.A", "NO_PATH", EXPECTED_BRANCH_EXPANDED, EXPECTED_TOTAL_EXPANSIONS),
            "Canonical selector must not conflate receipt ordinal 307 with lineage ordinal 77");
        require(!matchesCaptureTarget(1, LINEAGE_BRANCH_ORDINAL, "A_COIL_LOW", "QA.C",
                "DA.A", "NO_PATH", EXPECTED_BRANCH_EXPANDED, EXPECTED_TOTAL_EXPANSIONS),
            "Canonical selector must pin pass two");
        assertions += 3;

        byte[] base64Canary = new byte[] {0, 1, 2, 3, (byte)255};
        String encoded = Base64.getEncoder().encodeToString(base64Canary);
        require("AAECA/8=".equals(encoded) &&
                Arrays.equals(base64Canary, Base64.getDecoder().decode(encoded)),
            "Transport must use strict standard Base64 without payload mutation");
        assertions++;
        require("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".
                equals(sha256(utf8("abc"))), "SHA-256 guard must match the independent literal vector");
        assertions++;
        String literalChunk = chunkLine("GRAPH", 0, 0, base64Canary);
        require(lineBytes(literalChunk) <= LINE_LIMIT_BYTES &&
                literalChunk.indexOf('\r') < 0 && literalChunk.indexOf('\n') < 0 &&
                literalChunk.contains("\"offset\":0") &&
                literalChunk.contains("\"bytes\":5"),
            "Chunk guard must preserve raw offsets, byte lengths, and LF-only framing");
        assertions++;
        byte[] literalTransport = transportBytes("00000000-0000-4000-8000-000000000001",
            "1111111111111111111111111111111111111111111111111111111111111111",
            "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb",
            new byte[] {71}, new byte[] {70}, OUTPUT_LIMIT_BYTES);
        require(new String(literalTransport, StandardCharsets.UTF_8).endsWith(
                "{\"record\":\"FOOTER\",\"complete\":true,\"graphChunks\":1," +
                "\"factChunks\":1,\"payloadBytes\":2}\n"),
            "Transport footer must match the frozen independent protocol literal");
        zero(literalTransport);
        assertions++;
        require(CHUNK_BYTES == 32768 && INDEX_LIMIT_BYTES == 65536 &&
                OUTPUT_LIMIT_BYTES == 8388608L,
            "Transport canary must retain the frozen chunk, index, and aggregate caps");
        assertions++;

        Method serializer = Rb100PhysicalDiagnostics.class.getDeclaredMethod(
            "canonicalFixedPrefixGraphPayloadForStudy",
            PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot.class, String.class);
        serializer.setAccessible(true);
        require(Modifier.isStatic(serializer.getModifiers()) &&
                !Modifier.isPublic(serializer.getModifiers()),
            "Canonical graph serializer bridge must remain narrow and package-private");
        boolean nullSnapshotRejected = false;
        try {
            serializer.invoke(null, new Object[] {null, CASE_ID});
        } catch (InvocationTargetException expected) {
            nullSnapshotRejected = expected.getCause() instanceof IllegalArgumentException;
        }
        require(nullSnapshotRejected, "Canonical graph serializer must reject a missing snapshot");
        assertions += 2;
        require("BASE".equals(PrefixViaRampSupport.mode()) &&
                PrefixViaRampSupport.effectivePrice(60, 48, 48) == 60,
            "Canary must run the immutable unmodified BASE treatment");
        require(SeededPcbLayoutGenerator.CURRENT_VERSION == 26 &&
                "tsj-generation-dependencies-v28".equals(
                    GenerationDependencyContext.INTERPRETATION_EPOCH),
            "Canary must bind current layout and dependency interpretation");
        assertions += 2;

        CanonicalPrefixBlockerInspector canaryInspector = new CanonicalPrefixBlockerInspector(
            "00000000-0000-4000-8000-000000000001",
            "1111111111111111111111111111111111111111111111111111111111111111",
            "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb", false);
        Constructor<?> inspectorConstructor = armRunClass.getDeclaredConstructor(studyClass,
            Integer.TYPE, PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
            ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
        inspectorConstructor.setAccessible(true);
        construct(inspectorConstructor, null, Integer.valueOf(1), null, null);
        boolean rejectedInspectorArm = false;
        try { construct(inspectorConstructor, null, Integer.valueOf(2), null, canaryInspector); }
        catch (IllegalArgumentException expected) { rejectedInspectorArm = true; }
        require(rejectedInspectorArm, "Native inspector must reject non-operation1 callers");
        assertions += 2;
        int inspectorAssertions = canaryInspector.runCanaries();
        if (inspectorAssertions <= 0 || !canaryInspector.referencesDropped())
            throw new IllegalStateException("Inspector literal canaries or release checks failed");
        assertions += inspectorAssertions;
        CanonicalPrefixStageInspector stageCanary = new CanonicalPrefixStageInspector(
            "00000000-0000-4000-8000-000000000001",
            "1111111111111111111111111111111111111111111111111111111111111111",
            "0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb", true, false);
        int stageAssertions = stageCanary.runCanaries();
        require(stageAssertions > 0 && stageCanary.referencesDropped(),
            "Stage canaries and detached references must pass");
        assertions += stageAssertions;
        long guardNanos = elapsed(guardStarted, System.nanoTime());
        requireGroupTime(guardNanos, "selector/transport/constructor/helper guards");
        maximumGroupNanos = Math.max(maximumGroupNanos, guardNanos);
        groups++;
        long elapsedNanos = elapsed(started, System.nanoTime());
        if (elapsedNanos > OPERATION_LIMIT_NANOS || groups > 640)
            throw new IOException("Canary work exceeded its frozen operation or group bound");
        System.out.print("CANONICAL_PREFIX_STAGE_CANARY_COMPLETE {\"schema\":\"tsj-canonical-prefix-stage-canary-v1\",\"status\":\"PASS\",\"assertions\":" +
            assertions + ",\"blockerAssertions\":" + inspectorAssertions + ",\"stageAssertions\":" + stageAssertions + ",\"elapsedNanos\":" + elapsedNanos + ",\"groups\":" + groups +
            ",\"maximumGroupNanos\":" + maximumGroupNanos +
            ",\"layoutQualification\":false}\n");
        System.out.flush();
        requireGroupTime(elapsed(guardStarted, System.nanoTime()), "canary guards and output");
        if (elapsed(started, System.nanoTime()) > OPERATION_LIMIT_NANOS ||
                System.out.checkError() || System.err.checkError())
            throw new IOException("Canary final output failed its work or stream checks");
    }

    private static CandidateEvidence readAndValidateCandidate(Object armRun) throws Exception {
        Object value = field(armRun, "candidateHeader");
        if (value == null) throw new IllegalStateException("Actual ArmRun omitted CandidateHeader");
        CandidateEvidence candidate = new CandidateEvidence(longField(value, "rootSeed"),
            longField(value, "placementSeed"), longField(value, "routingSeed"),
            stringField(value, "variant"), intField(value, "componentCount"),
            intField(value, "padCount"), intField(value, "netCount"));
        if (candidate.rootSeed != EXPECTED_ROOT_SEED ||
                candidate.placementSeed != EXPECTED_PLACEMENT_SEED ||
                candidate.routingSeed != EXPECTED_ROUTING_SEED ||
                !"SEPARATE".equals(candidate.variant) || candidate.componentCount != 100 ||
                candidate.padCount != 246 || candidate.netCount != 60)
            throw new IllegalStateException("Actual candidate header differs from frozen AUTHORED BASE identity");
        return candidate;
    }

    private static CapturedOwnerContext readAndValidateOwnerContext(Object armRun,
            CandidateEvidence candidate, OneShotObserver observer) throws Exception {
        // execute() releases the live owner capture. Its completed immutable
        // RequestObservation retains the same proven context for evidence only.
        Object request = field(armRun, "request");
        if (request == null) throw new IllegalStateException("Actual request omitted its observation");
        Object value = field(request, "context");
        if (value == null) throw new IllegalStateException("Actual request omitted its observed context");
        int requestIndex = intField(value, "requestIndex");
        int selectedAttempt = intField(value, "selectedAttempt");
        Object attemptsValue = field(value, "eligibleAttempts");
        if (!(attemptsValue instanceof int[]))
            throw new IllegalStateException("Actual captured context omitted eligible placement attempts");
        int[] attempts = ((int[])attemptsValue).clone();
        String pristineGeometry = stringField(value, "pristineGeometry");
        String pristineSha256 = stringField(value, "pristineSha256");
        int pristineBytes = intField(value, "pristineBytes");
        byte[] pristine = utf8(pristineGeometry);
        boolean contextValid;
        try {
            contextValid = requestIndex == 0 && selectedAttempt == 4 &&
                Arrays.equals(attempts, new int[] {4, 2, 3, 0, 1}) &&
                pristine.length == pristineBytes && pristineSha256.equals(sha256(pristine));
        } finally {
            zero(pristine);
            Arrays.fill(attempts, 0);
        }
        if (!contextValid)
            throw new IllegalStateException("Actual request-zero placement context differs from the frozen prefix");
        if (candidate.placementSeed != EXPECTED_PLACEMENT_SEED)
            throw new IllegalStateException("Actual layout seed is not the authored placement seed");
        if (observer != null && observer.selectedLineage != null) {
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext context =
                observer.selectedLineage.context();
            int[] observerAttempts = context.eligiblePlacementAttempts();
            boolean observerContextValid = context.layoutSeed() == candidate.placementSeed &&
                    context.routingSeed() == candidate.routingSeed &&
                    context.routeRequestIndex() == requestIndex &&
                    context.selectedPlacementIndex() == 0 &&
                    context.selectedPlacementAttempt() == selectedAttempt &&
                    Arrays.equals(observerAttempts, new int[] {4, 2, 3, 0, 1}) &&
                    context.rawPlacementFingerprint().equals(pristineGeometry);
            Arrays.fill(observerAttempts, 0);
            if (!observerContextValid)
                throw new IllegalStateException("Actual fixed-prefix context differs from the owner request context");
        }
        return new CapturedOwnerContext(requestIndex, selectedAttempt, pristineGeometry,
            pristineBytes, pristineSha256);
    }

    private static void validateCapture(CandidateEvidence candidate,
            CapturedOwnerContext ownerContext,
            PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage,
            PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) throws IOException {
        if (lineage == null || graph == null || graph.lineage() != lineage ||
                graph.context() != lineage.context())
            throw new IllegalStateException("Captured graph does not retain the selected immutable lineage/context");
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context = lineage.context();
        int[] eligibleAttempts = context.eligiblePlacementAttempts();
        boolean contextMatches = context.routeRequestIndex() == 0 &&
            context.layoutSeed() == candidate.placementSeed &&
            context.routingSeed() == candidate.routingSeed && context.selectedPlacementIndex() == 0 &&
            context.selectedPlacementAttempt() == 4 &&
            Arrays.equals(eligibleAttempts, new int[] {4, 2, 3, 0, 1}) &&
            context.rawPlacementFingerprint().equals(ownerContext.pristineGeometry);
        Arrays.fill(eligibleAttempts, 0);
        if (!contextMatches ||
                ownerContext.requestIndex != 0 || ownerContext.selectedAttempt != 4)
            throw new IllegalStateException("Actual fixed-prefix graph context changed its canonical identity");
        if (!matchesCaptureTarget(lineage.pass(), lineage.branchOrdinal(), lineage.netId(),
                lineage.startPadId(), lineage.rootPadId(), lineage.terminalReason(),
                lineage.branchExpanded(), lineage.totalRouteExpansions()))
            throw new IllegalStateException("Selected lineage differs from the exact canonical pass-two branch");
        byte[] prefix = utf8(lineage.prefixGeometryFingerprint());
        try {
            if (prefix.length != EXPECTED_PREFIX_BYTES ||
                    !EXPECTED_PREFIX_SHA256.equals(sha256(prefix)))
                throw new IOException("Actual fixed-prefix geometry fingerprint differs from the frozen identity");
        } finally {
            zero(prefix);
        }
        if (lineage.prefixHoleCount() != MAX_VIAS || lineage.prefixViaCount() != MAX_VIAS ||
                graph.initialFacesMask() != 3 ||
                graph.cellCount() != graph.gridWidth() * graph.gridHeight() ||
                graph.gridWidth() < 1 || graph.gridHeight() < 1 ||
                graph.primaryLayer() < 0 || graph.primaryLayer() > 1 ||
                graph.startCell() < 0 || graph.startCell() >= graph.cellCount())
            throw new IllegalStateException("Actual graph dimensions or H48 prefix capacity differ from the contract");
        if (SeededPcbLayoutGenerator.CURRENT_VERSION != 26 ||
                !"tsj-generation-dependencies-v28".
                    equals(GenerationDependencyContext.INTERPRETATION_EPOCH))
            throw new IllegalStateException("Current production layout/dependency epoch changed");
    }

    private static String factsJson(CandidateEvidence candidate, CapturedOwnerContext ownerContext,
            PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage,
            PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph, String runId,
            String sourceManifestSha256, String wireSha256) {
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context = lineage.context();
        byte[] rawPlacement = utf8(context.rawPlacementFingerprint());
        byte[] prefixGeometry = utf8(lineage.prefixGeometryFingerprint());
        int[] eligible = null;
        int[] holeCounts = null;
        String[] reached = null;
        String[] reachedPth = null;
        try {
            String rawSha = sha256(rawPlacement);
            String prefixSha = sha256(prefixGeometry);
            if (rawPlacement.length != ownerContext.pristineBytes ||
                    !rawSha.equals(ownerContext.pristineSha256) ||
                    !context.rawPlacementFingerprint().equals(ownerContext.pristineGeometry))
                throw new IllegalStateException(
                    "Serialized canonical facts differ from captured owner placement bytes");
            eligible = context.eligiblePlacementAttempts();
            holeCounts = lineage.prefixHoleCountsByKind();
            reached = lineage.reachedPadIds();
            reachedPth = lineage.reachedPthPadIds();
            StringBuilder json = new StringBuilder(rawPlacement.length + prefixGeometry.length + 2048);
            json.append("{\"schema\":\"tsj-canonical-prefix-facts-v1\",\"runId\":");
            appendString(json, runId);
            json.append(",\"sourceManifestSha256\":");
            appendString(json, sourceManifestSha256);
            json.append(",\"wireSha256\":");
            appendString(json, wireSha256);
            json.append(",\"caseId\":");
            appendString(json, CASE_ID);
            json.append(",\"rootSeed\":");
            appendString(json, Long.toString(candidate.rootSeed));
            json.append(",\"placementSeed\":");
            appendString(json, Long.toString(candidate.placementSeed));
            json.append(",\"routingSeed\":");
            appendString(json, Long.toString(candidate.routingSeed));
            json.append(",\"variant\":");
            appendString(json, candidate.variant);
            json.append(",\"layoutVersion\":").append(SeededPcbLayoutGenerator.CURRENT_VERSION);
            json.append(",\"dependencyEpoch\":");
            appendString(json, GenerationDependencyContext.INTERPRETATION_EPOCH);
            json.append(",\"componentCount\":").append(candidate.componentCount);
            json.append(",\"padCount\":").append(candidate.padCount);
            json.append(",\"netCount\":").append(candidate.netCount);
            json.append(",\"routeRequestIndex\":").append(context.routeRequestIndex());
            json.append(",\"selectedPlacementIndex\":").append(context.selectedPlacementIndex());
            json.append(",\"selectedPlacementAttempt\":").append(context.selectedPlacementAttempt());
            json.append(",\"eligiblePlacementAttempts\":").append(intArrayJson(eligible));
            json.append(",\"receiptBranchOrdinal\":").append(RECEIPT_BRANCH_ORDINAL);
            json.append(",\"lineageBranchOrdinal\":").append(lineage.branchOrdinal());
            json.append(",\"pass\":").append(lineage.pass());
            json.append(",\"netId\":");
            appendString(json, lineage.netId());
            json.append(",\"startPadId\":");
            appendString(json, lineage.startPadId());
            json.append(",\"rootPadId\":");
            appendString(json, lineage.rootPadId());
            json.append(",\"startX\":").append(lineage.startX());
            json.append(",\"startY\":").append(lineage.startY());
            json.append(",\"rootX\":").append(lineage.rootX());
            json.append(",\"rootY\":").append(lineage.rootY());
            json.append(",\"branchExpanded\":").append(lineage.branchExpanded());
            json.append(",\"totalRouteExpansions\":").append(lineage.totalRouteExpansions());
            json.append(",\"terminalReason\":");
            appendString(json, lineage.terminalReason());
            json.append(",\"prefixHoleCount\":").append(lineage.prefixHoleCount());
            json.append(",\"prefixViaCount\":").append(lineage.prefixViaCount());
            json.append(",\"prefixHoleCountsByKind\":{\"VIA\":")
                .append(holeCounts[PcbBoardHole.Kind.VIA.ordinal()])
                .append(",\"PLATED\":").append(holeCounts[PcbBoardHole.Kind.PLATED.ordinal()])
                .append(",\"NON_PLATED\":")
                .append(holeCounts[PcbBoardHole.Kind.NON_PLATED.ordinal()]).append('}');
            json.append(",\"reachedPadIds\":").append(stringArrayJson(reached));
            json.append(",\"reachedPthPadIds\":").append(stringArrayJson(reachedPth));
            json.append(",\"rawPlacementFingerprint\":");
            appendString(json, context.rawPlacementFingerprint());
            json.append(",\"rawPlacementFingerprintUtf8Bytes\":").append(rawPlacement.length);
            json.append(",\"rawPlacementSha256\":");
            appendString(json, rawSha);
            json.append(",\"prefixGeometryFingerprint\":");
            appendString(json, lineage.prefixGeometryFingerprint());
            json.append(",\"prefixGeometryFingerprintUtf8Bytes\":").append(prefixGeometry.length);
            json.append(",\"prefixGeometrySha256\":");
            appendString(json, prefixSha);
            json.append(",\"gridWidth\":").append(graph.gridWidth());
            json.append(",\"gridHeight\":").append(graph.gridHeight());
            json.append(",\"gridPitch\":").append(graph.gridPitch());
            json.append(",\"originX\":").append(graph.originX());
            json.append(",\"originY\":").append(graph.originY());
            json.append(",\"primaryLayer\":").append(graph.primaryLayer());
            json.append(",\"startCell\":").append(graph.startCell());
            json.append(",\"initialFacesMask\":").append(graph.initialFacesMask());
            json.append(",\"cellCount\":").append(graph.cellCount());
            json.append(",\"captureWorkUnits\":").append(graph.captureWorkUnits());
            json.append(",\"copyEntries\":").append(graph.copyEntries());
            json.append(",\"goalProbes\":").append(graph.goalProbes());
            json.append(",\"ordinaryEdgeProbes\":").append(graph.ordinaryEdgeProbes());
            json.append(",\"viaProbes\":").append(graph.viaProbes());
            json.append(",\"comparisonEntries\":").append(graph.comparisonEntries());
            json.append(",\"captureActiveElapsedMillis\":").append(graph.captureActiveElapsedMillis());
            json.append(",\"viaMaskSemantics\":\"RELAXED_GEOMETRY_OMITS_REMAINING_HOLE_CAP\"");
            json.append(",\"maximumHoles\":").append(MAX_VIAS);
            json.append(",\"remainingHoles\":").append(MAX_VIAS - lineage.prefixViaCount());
            json.append(",\"maximumBranchTransitions\":4,\"remainingBranchTransitionsFromPrefix\":0}");
            return json.toString();
        } finally {
            zero(rawPlacement);
            zero(prefixGeometry);
            if (eligible != null) Arrays.fill(eligible, 0);
            if (holeCounts != null) Arrays.fill(holeCounts, 0);
            if (reached != null) Arrays.fill(reached, null);
            if (reachedPth != null) Arrays.fill(reachedPth, null);
        }
    }

    private static byte[] transportBytes(String runId, String sourceSha, String wireSha,
            byte[] graph, byte[] facts, long maximumBytes) throws IOException {
        if (maximumBytes < 0 || graph == null || facts == null || graph.length < 1 || facts.length < 1)
            throw new IOException("Missing or unbounded canonical transport payload");
        int graphChunks = chunkCount(graph.length);
        int factsChunks = chunkCount(facts.length);
        ClearableByteArrayOutputStream output = new ClearableByteArrayOutputStream(
            Math.min((int)Math.min(maximumBytes, 1048576L), 1048576));
        try {
            StringBuilder header = new StringBuilder(600);
            header.append("{\"record\":\"HEADER\",\"schema\":\"tsj-canonical-prefix-transport-v1\",\"runId\":");
            appendString(header, runId);
            header.append(",\"sourceManifestSha256\":");
            appendString(header, sourceSha);
            header.append(",\"wireSha256\":");
            appendString(header, wireSha);
            header.append(",\"scope\":");
            appendString(header, "CANONICAL_BASE_REQUEST0_ONLY");
            header.append(",\"payloads\":[");
            appendPayloadDescriptor(header, "GRAPH", graph, graphChunks);
            header.append(',');
            appendPayloadDescriptor(header, "FACTS", facts, factsChunks);
            header.append("],\"aggregateCapBytes\":8388608,\"chunkBytes\":32768}");
            appendLine(output, header.toString(), maximumBytes);

            appendChunks(output, "GRAPH", graph, maximumBytes);
            appendChunks(output, "FACTS", facts, maximumBytes);
            StringBuilder footer = new StringBuilder(180);
            footer.append("{\"record\":\"FOOTER\",\"complete\":true,\"graphChunks\":");
            footer.append(graphChunks).append(",\"factChunks\":").append(factsChunks);
            footer.append(",\"payloadBytes\":").append((long)graph.length + facts.length).append('}');
            appendLine(output, footer.toString(), maximumBytes);
            return output.toByteArray();
        } finally {
            output.clear();
        }
    }

    private static void appendPayloadDescriptor(StringBuilder json, String kind,
            byte[] payload, int chunks) {
        json.append("{\"kind\":");
        appendString(json, kind);
        json.append(",\"bytes\":").append(payload.length).append(",\"sha256\":");
        appendString(json, sha256(payload));
        json.append(",\"chunks\":").append(chunks).append('}');
    }

    private static void appendChunks(ByteArrayOutputStream output, String kind,
            byte[] payload, long maximumBytes) throws IOException {
        int index = 0;
        for (int offset = 0; offset < payload.length; offset += CHUNK_BYTES) {
            int length = Math.min(CHUNK_BYTES, payload.length - offset);
            byte[] chunk = Arrays.copyOfRange(payload, offset, offset + length);
            try {
                appendLine(output, chunkLine(kind, index, offset, chunk), maximumBytes);
            } finally {
                zero(chunk);
            }
            index++;
        }
    }

    private static String chunkLine(String kind, int index, int offset, byte[] raw) {
        if (!"GRAPH".equals(kind) && !"FACTS".equals(kind))
            throw new IllegalArgumentException("Chunk kind must be GRAPH or FACTS");
        if (index < 0 || offset < 0 || raw == null || raw.length < 1 || raw.length > CHUNK_BYTES)
            throw new IllegalArgumentException("Invalid canonical payload chunk coordinates");
        String encoded = Base64.getEncoder().encodeToString(raw);
        StringBuilder json = new StringBuilder(encoded.length() + 120);
        json.append("{\"record\":\"CHUNK\",\"kind\":");
        appendString(json, kind);
        json.append(",\"index\":").append(index).append(",\"offset\":").append(offset);
        json.append(",\"bytes\":").append(raw.length).append(",\"data\":");
        appendString(json, encoded);
        json.append('}');
        return json.toString();
    }

    private static void appendLine(ByteArrayOutputStream output, String line,
            long maximumBytes) throws IOException {
        byte[] bytes = utf8(line);
        try {
            if (bytes.length < 2 || bytes.length + 1 > LINE_LIMIT_BYTES ||
                    line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0)
                throw new IOException("Canonical transport line exceeds its LF-only 64 KiB bound");
            if ((long)output.size() + bytes.length + 1 > maximumBytes)
                throw new IOException("Canonical transport exceeded reserved aggregate capacity");
            output.write(bytes, 0, bytes.length);
            output.write('\n');
        } finally {
            zero(bytes);
        }
    }

    private static int chunkCount(int bytes) {
        if (bytes < 1) throw new IllegalArgumentException("Empty payload has no canonical chunks");
        return (bytes + CHUNK_BYTES - 1) / CHUNK_BYTES;
    }

    private static String transportJson(FileEvidence transport, byte[] graph, byte[] facts) {
        StringBuilder json = new StringBuilder(500);
        json.append("{\"path\":\"fixed-prefix.jsonl\",\"bytes\":");
        json.append(transport.bytes).append(",\"sha256\":");
        appendString(json, transport.sha256);
        json.append(",\"graph\":{\"bytes\":").append(graph.length).append(",\"sha256\":");
        appendString(json, sha256(graph));
        json.append("},\"facts\":{\"bytes\":").append(facts.length).append(",\"sha256\":");
        appendString(json, sha256(facts));
        json.append("}}");
        return json.toString();
    }

    private static String indexJson(String mode, String profile, String runId,
            String sourceSha, String wireSha, String operation, String transport,
            String attribution, String stages, int selectionCalls, int selected, int callbacks) {
        StringBuilder json = new StringBuilder(1600);
        json.append("{\"schema\":\"tsj-canonical-prefix-stage-index-v1\",\"mode\":");
        appendString(json, mode);
        json.append(",\"profile\":");
        appendString(json, profile);
        json.append(",\"kind\":\"AUTHORED\",\"runId\":");
        appendString(json, runId);
        json.append(",\"sourceManifestSha256\":");
        appendString(json, sourceSha);
        json.append(",\"wireSha256\":");
        appendString(json, wireSha);
        json.append(",\"scope\":");
        appendString(json, SCOPE);
        json.append(",\"layoutQualification\":false,\"target\":{\"receiptBranchOrdinal\":307,\"lineageBranchOrdinal\":77}");
        json.append(",\"operation\":").append(operation);
        json.append(",\"transport\":").append(transport == null ? "null" : transport);
        json.append(",\"attribution\":").append(attribution == null ? "null" : attribution);
        json.append(",\"stages\":").append(stages == null ? "null" : stages);
        json.append(",\"selection\":{\"calls\":").append(selectionCalls);
        json.append(",\"selected\":").append(selected).append(",\"callbacks\":").append(callbacks);
        json.append(",\"snapshotDropped\":true}}");
        return json.toString();
    }

    private static String operationJson(ArmEvidence arm, FileEvidence receipt) {
        StringBuilder json = new StringBuilder(500);
        json.append("{\"operation\":1,\"armId\":\"OBSERVED_DEFAULT_NEAREST\",\"receipt\":{\"path\":\"op-1.jsonl\",\"bytes\":");
        json.append(receipt.bytes).append(",\"sha256\":");
        appendString(json, receipt.sha256);
        json.append("},\"elapsedToCloseNanos\":").append(arm.elapsedToCloseNanos);
        json.append(",\"maxFullGroupNanos\":").append(arm.maxFullGroupNanos);
        json.append(",\"cleanup\":{\"status\":\"PASS\",\"elapsedNanos\":");
        json.append(arm.cleanupNanos).append(",\"ownersReleased\":true,\"contextRestored\":true}}");
        return json.toString();
    }

    private static String fileReferenceJson(String name, FileEvidence file) {
        StringBuilder json = new StringBuilder(200);
        json.append("{\"path\":"); appendString(json, name);
        json.append(",\"bytes\":").append(file.bytes).append(",\"sha256\":");
        appendString(json, file.sha256); json.append('}');
        return json.toString();
    }

    private static String expectedExceptionJson(String runId, String sourceSha,
            String wireSha, FileEvidence partial, String inspection, long work) {
        StringBuilder json = new StringBuilder(4096);
        json.append("{\"schema\":\"tsj-canonical-prefix-stage-exception-v1\",\"mode\":\"CALLBACK_THROW\",\"runId\":");
        appendString(json, runId);
        json.append(",\"sourceManifestSha256\":"); appendString(json, sourceSha);
        json.append(",\"wireSha256\":"); appendString(json, wireSha);
        json.append(",\"scope\":"); appendString(json, SCOPE);
        json.append(",\"layoutQualification\":false,\"exceptionType\":\"java.lang.IllegalStateException\",\"exceptionMessage\":\"CANONICAL_PREFIX_STAGE_CALLBACK_THROW\",\"partialReceipt\":");
        json.append(fileReferenceJson("op-1.jsonl", partial));
        json.append(",\"stages\":").append(inspection);
        json.append(",\"inclusiveWorkNanos\":").append(work);
        json.append(",\"cleanupAccounting\":\"FULL_NATIVE_WALL_PLUS_POST_SOURCE\"}");
        return json.toString();
    }

    private static ReceiptEvidence scanReceipt(Path path, long expectedBytes) throws IOException {
        ReceiptEvidence result = new ReceiptEvidence();
        ClearableByteArrayOutputStream lineBytes = new ClearableByteArrayOutputStream(4096);
        long totalBytes = 0;
        boolean endedWithLf = false;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int value;
            while ((value = input.read()) >= 0) {
                totalBytes++;
                if (totalBytes > expectedBytes || totalBytes > OUTPUT_LIMIT_BYTES)
                    throw new IOException("Actual operation receipt exceeded its bounded byte identity");
                if (value == '\r') throw new IOException("Actual operation receipt contains CR bytes");
                if (value == '\n') {
                    if (lineBytes.size() + 1 > LINE_LIMIT_BYTES)
                        throw new IOException("Actual operation receipt line exceeds 64 KiB");
                    byte[] rawLine = lineBytes.toByteArray();
                    String line;
                    try { line = decodeUtf8(rawLine); }
                    finally { zero(rawLine); }
                    processReceiptLine(result, line);
                    lineBytes.clear();
                    endedWithLf = true;
                } else {
                    endedWithLf = false;
                    if (lineBytes.size() + 1 >= LINE_LIMIT_BYTES)
                        throw new IOException("Actual operation receipt line exceeds 64 KiB");
                    lineBytes.write(value);
                }
            }
        } finally {
            lineBytes.clear();
        }
        if (!endedWithLf || lineBytes.size() != 0 || totalBytes != expectedBytes ||
                result.footerCount != 1 || result.groupCount < 1)
            throw new IOException("Actual operation receipt is incomplete or has no unique footer");
        return result;
    }

    private static void processReceiptLine(ReceiptEvidence result, String line) throws IOException {
        if (line.length() == 0) throw new IOException("Actual operation receipt contains an empty JSONL row");
        if (line.startsWith("{\"record\":\"FOOTER\",")) {
            result.footerCount++;
            if (result.footerCount > 1)
                throw new IOException("Actual operation receipt contains duplicate FOOTER rows");
            requireJsonBoolean(line, "complete", true);
            requireJsonNumber(line, "operation", 1);
            long groups = jsonNumber(line, "groupCount");
            if (groups < 1 || groups > 640)
                throw new IOException("Actual operation receipt group count is out of bounds");
            result.groupCount = (int)groups;
        }
    }

    private static void requireOnlyReceipt(Path directory, Path receipt) throws IOException {
        int entries = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                entries++;
                if (!entry.equals(receipt))
                    throw new IOException("INSPECTOR_THROW wrote a sidecar or index artifact");
            }
        }
        if (entries != 1) throw new IOException("INSPECTOR_THROW did not leave exactly its actual op-1 receipt");
    }

    private static FileEvidence requireRegularFile(Path path, String description) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path,
            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isOther() || Files.isSymbolicLink(path))
            throw new IOException(description + " is absent, non-regular, or a reparse entry");
        long bytes = attributes.size();
        return new FileEvidence(bytes, sha256(path));
    }

    private static void writeNewFile(Path path, byte[] bytes) throws IOException {
        if (path == null || bytes == null) throw new IllegalArgumentException("Missing CREATE_NEW output");
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            output.write(bytes);
            output.flush();
        }
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
            throw new IllegalArgumentException("output directory resolves through a reparse point");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
            if (entries.iterator().hasNext())
                throw new IllegalArgumentException("output directory must be empty before Java writes");
        }
        return realPath;
    }

    private static ArmEvidence readArmEvidence(Object result) throws Exception {
        if (result == null || !"ArmResult".equals(result.getClass().getSimpleName()))
            throw new IllegalStateException("ArmRun did not return the actual ArmResult");
        return new ArmEvidence(longField(result, "receiptBytes"),
            stringField(result, "receiptSha256"), longField(result, "elapsedToCloseNanos"),
            longField(result, "maxFullGroupNanos"), longField(result, "cleanupNanos"),
            booleanField(result, "ownersReleased"), booleanField(result, "contextRestored"));
    }

    private static boolean matchesCaptureTarget(int pass, int branchOrdinal, String netId,
            String startPadId, String rootPadId, String terminalReason,
            int branchExpanded, int totalRouteExpansions) {
        return pass == PASS && branchOrdinal == LINEAGE_BRANCH_ORDINAL &&
            "A_COIL_LOW".equals(netId) && "QA.C".equals(startPadId) &&
            "DA.A".equals(rootPadId) && "NO_PATH".equals(terminalReason) &&
            branchExpanded == EXPECTED_BRANCH_EXPANDED &&
            totalRouteExpansions == EXPECTED_TOTAL_EXPANSIONS;
    }

    private static void requireJsonNumber(String json, String name, long expected) throws IOException {
        long actual = jsonNumber(json, name);
        if (actual != expected)
            throw new IOException("Receipt JSON numeric field differs: " + name);
    }

    private static long jsonNumber(String json, String name) throws IOException {
        String token = "\"" + name + "\":";
        int first = json.indexOf(token);
        if (first < 0 || json.indexOf(token, first + token.length()) >= 0)
            throw new IOException("JSON field is absent or duplicated: " + name);
        int start = first + token.length();
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') end++;
        while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
        if (end == start || (end == start + 1 && json.charAt(start) == '-') ||
                end >= json.length() || (json.charAt(end) != ',' && json.charAt(end) != '}'))
            throw new IOException("JSON numeric field is malformed: " + name);
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException invalid) { throw new IOException("JSON number is out of range: " + name); }
    }

    private static void requireJsonBoolean(String json, String name, boolean expected) throws IOException {
        String token = "\"" + name + "\":" + (expected ? "true" : "false");
        int first = json.indexOf(token);
        if (first < 0 || json.indexOf("\"" + name + "\":", first + token.length()) >= 0)
            throw new IOException("JSON boolean field differs or is duplicated: " + name);
        int end = first + token.length();
        if (end >= json.length() || (json.charAt(end) != ',' && json.charAt(end) != '}'))
            throw new IOException("JSON boolean field is malformed: " + name);
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        CharsetDecoder decoder = UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try { return decoder.decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException malformed) { throw new IOException("Receipt contains malformed UTF-8"); }
    }

    private static String intArrayJson(int[] values) {
        if (values == null) throw new IllegalArgumentException("Missing integer array");
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i != 0) json.append(',');
            json.append(values[i]);
        }
        return json.append(']').toString();
    }

    private static String stringArrayJson(String[] values) {
        if (values == null) throw new IllegalArgumentException("Missing string array");
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i != 0) json.append(',');
            appendString(json, values[i]);
        }
        return json.append(']').toString();
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
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
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

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[32768];
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        } finally {
            zero(buffer);
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] value) {
        return hex(newDigest().digest(value));
    }

    private static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] digest) {
        char[] result = new char[digest.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < digest.length; index++) {
            int value = digest[index] & 255;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 15];
        }
        return new String(result);
    }

    private static void requireSha(String value, String name) {
        if (value == null || !SHA_PATTERN.matcher(value).matches())
            throw new IllegalArgumentException(name + " must be lowercase SHA-256 text");
    }

    private static void requireOutputHealthy(PrintStream out, PrintStream err,
            SharedByteBudget budget) {
        if (budget.overflowed() || out.checkError() || err.checkError())
            throw new CaptureLimitError();
    }

    private static void requireGroupTime(long nanos, String group) throws IOException {
        if (nanos < 0 || nanos > WRAPPER_GROUP_LIMIT_NANOS)
            throw new IOException("Wrapper " + group + " group exceeded five seconds");
    }

    private static long subtractCleanup(long elapsedNanos, long cleanupNanos) {
        if (elapsedNanos < cleanupNanos)
            throw new IllegalStateException("Wrapper elapsed time is smaller than actual cleanup");
        return elapsedNanos - cleanupNanos;
    }

    private static long elapsed(long started, long ended) {
        long value = ended - started;
        return value < 0 ? Long.MAX_VALUE : value;
    }

    private static int lineBytes(String value) { return utf8(value).length; }

    private static byte[] utf8(String value) { return value.getBytes(UTF8); }

    private static void zero(byte[] value) {
        if (value != null) Arrays.fill(value, (byte)0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Object construct(Constructor<?> constructor, Object... arguments)
            throws Exception {
        try { return constructor.newInstance(arguments); }
        catch (InvocationTargetException failure) {
            rethrow(failure.getCause());
            throw new AssertionError("Unreachable constructor failure");
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments)
            throws Exception {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) {
            rethrow(failure.getCause());
            throw new AssertionError("Unreachable invocation failure");
        }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new RuntimeException(failure);
    }

    private static Object field(Object target, String name) throws Exception {
        if (target == null) throw new IllegalStateException("Missing actual owner object for " + name);
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException("Actual owner omitted field " + name);
    }

    private static long longField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof Long))
            throw new IllegalStateException("Actual owner field is not long: " + name);
        return ((Long)value).longValue();
    }

    private static int intField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof Integer))
            throw new IllegalStateException("Actual owner field is not int: " + name);
        return ((Integer)value).intValue();
    }

    private static String stringField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof String))
            throw new IllegalStateException("Actual owner field is not String: " + name);
        return (String)value;
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Object value = field(target, name);
        if (!(value instanceof Boolean))
            throw new IllegalStateException("Actual owner field is not boolean: " + name);
        return ((Boolean)value).booleanValue();
    }

    private static final class OneShotObserver
            implements PcbLayerRoutingPrototype.FixedPrefixGraphObserver {
        int calls;
        int selected;
        int callbacks;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage selectedLineage;
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot;

        OneShotObserver() { }

        public boolean select(PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) {
            calls++;
            if (lineage == null) throw new IllegalStateException("Observer received a missing lineage");
            boolean target = lineage.pass() == PASS &&
                lineage.branchOrdinal() == LINEAGE_BRANCH_ORDINAL;
            if (target && !matchesCaptureTarget(lineage.pass(), lineage.branchOrdinal(),
                    lineage.netId(), lineage.startPadId(), lineage.rootPadId(),
                    lineage.terminalReason(), lineage.branchExpanded(),
                    lineage.totalRouteExpansions()))
                throw new IllegalStateException("Canonical selector target identity changed");
            if (target) validateLineageSelection(lineage);
            if (!target) return false;
            if (selected != 0 || selectedLineage != null)
                throw new IllegalStateException("Fixed-prefix observer selected more than once");
            selected++;
            selectedLineage = lineage;
            return true;
        }

        public void onGraph(PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot graph) {
            callbacks++;
            if (selected != 1 || callbacks != 1 || graph == null ||
                    graph.lineage() != selectedLineage ||
                    graph.context() != selectedLineage.context())
                throw new IllegalStateException("Unexpected, duplicate, or mismatched graph callback");
            snapshot = graph;
        }

        void requireOneCapture() {
            if (calls < 1 || selected != 1 || callbacks != 1 ||
                    selectedLineage == null || snapshot == null)
                throw new IllegalStateException("Actual fixed-prefix observer did not capture exactly once");
        }

        void drop() {
            snapshot = null;
            selectedLineage = null;
        }
    }

    private static void validateLineageSelection(
            PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage) {
        PcbLayerRoutingPrototype.FixedPrefixCaptureContext context = lineage.context();
        int[] attempts = context.eligiblePlacementAttempts();
        byte[] rawPlacement = utf8(context.rawPlacementFingerprint());
        byte[] prefix = utf8(lineage.prefixGeometryFingerprint());
        try {
            if (context.routeRequestIndex() != 0 ||
                    context.layoutSeed() != EXPECTED_PLACEMENT_SEED ||
                    context.routingSeed() != EXPECTED_ROUTING_SEED ||
                    context.selectedPlacementIndex() != 0 ||
                    context.selectedPlacementAttempt() != 4 ||
                    !Arrays.equals(attempts, new int[] {4, 2, 3, 0, 1}) ||
                    rawPlacement.length < 1 || rawPlacement.length > 1048576 ||
                    prefix.length != EXPECTED_PREFIX_BYTES ||
                    !EXPECTED_PREFIX_SHA256.equals(sha256(prefix)))
                throw new IllegalStateException("Canonical selector lineage context/fingerprint changed");
        } finally {
            Arrays.fill(attempts, 0);
            zero(rawPlacement);
            zero(prefix);
        }
    }

    private static final class CandidateEvidence {
        final long rootSeed, placementSeed, routingSeed;
        final String variant;
        final int componentCount, padCount, netCount;
        CandidateEvidence(long rootSeed, long placementSeed, long routingSeed, String variant,
                int componentCount, int padCount, int netCount) {
            this.rootSeed = rootSeed;
            this.placementSeed = placementSeed;
            this.routingSeed = routingSeed;
            this.variant = variant;
            this.componentCount = componentCount;
            this.padCount = padCount;
            this.netCount = netCount;
        }
    }

    private static final class CapturedOwnerContext {
        final int requestIndex, selectedAttempt;
        final String pristineGeometry, pristineSha256;
        final int pristineBytes;
        CapturedOwnerContext(int requestIndex, int selectedAttempt, String pristineGeometry,
                int pristineBytes, String pristineSha256) {
            this.requestIndex = requestIndex;
            this.selectedAttempt = selectedAttempt;
            this.pristineGeometry = pristineGeometry;
            this.pristineBytes = pristineBytes;
            this.pristineSha256 = pristineSha256;
        }
    }

    private static final class ArmEvidence {
        final long receiptBytes, elapsedToCloseNanos, maxFullGroupNanos, cleanupNanos;
        final String receiptSha256;
        final boolean ownersReleased, contextRestored;
        ArmEvidence(long receiptBytes, String receiptSha256, long elapsedToCloseNanos,
                long maxFullGroupNanos, long cleanupNanos, boolean ownersReleased,
                boolean contextRestored) {
            this.receiptBytes = receiptBytes;
            this.receiptSha256 = receiptSha256;
            this.elapsedToCloseNanos = elapsedToCloseNanos;
            this.maxFullGroupNanos = maxFullGroupNanos;
            this.cleanupNanos = cleanupNanos;
            this.ownersReleased = ownersReleased;
            this.contextRestored = contextRestored;
        }
    }

    private static final class FileEvidence {
        final long bytes;
        final String sha256;
        FileEvidence(long bytes, String sha256) { this.bytes = bytes; this.sha256 = sha256; }
    }

    private static final class ReceiptEvidence {
        int footerCount;
        int groupCount;
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
        synchronized long bytesUsed() { return used; }
        synchronized boolean overflowed() { return overflowed; }
    }

    private static final class ClearableByteArrayOutputStream extends ByteArrayOutputStream {
        ClearableByteArrayOutputStream(int size) { super(size); }
        void clear() {
            Arrays.fill(buf, 0, count, (byte)0);
            reset();
        }
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
        CaptureLimitError() { super("CANONICAL_PREFIX_CAPTURE_LIMIT"); }
    }
}
