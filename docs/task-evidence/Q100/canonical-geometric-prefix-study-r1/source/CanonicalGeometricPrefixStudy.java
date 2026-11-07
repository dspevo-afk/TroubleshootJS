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
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Native adapter for the one immutable canonical BASE request-zero prefix. */
public final class CanonicalGeometricPrefixStudy {
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

    private CanonicalGeometricPrefixStudy() { }

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
        int exitStatus = 0;
        try {
            exitStatus = p8Run(args, wrapperStarted, boundedOut, boundedErr, outputBudget);
        } catch (P8PreflightFailure rejection) {
            try {
                if (outputBudget.bytesUsed() != 0)
                    throw new IllegalStateException("P8 preflight rejection followed output");
                byte[] frame = utf8("P8_GEOMETRIC_PREFIX_PREFLIGHT_REJECT " +
                    rejection.json() + "\n");
                boundedErr.write(frame, 0, frame.length);
                boundedErr.flush();
                if (outputBudget.bytesUsed() != frame.length)
                    throw new IllegalStateException("P8 preflight frame was not exact");
            } catch (Throwable outputFailure) {
                failure = outputFailure;
            }
            exitStatus = 1;
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

    private static int p8Run(String[] args, long wrapperStarted, PrintStream boundedOut,
            PrintStream boundedErr, SharedByteBudget outputBudget) throws Exception {
        P8Arguments arguments = p8Preflight(args);
        String mode = arguments.mode;
        String runId = arguments.runId;
        String inputSha256 = arguments.inputSha256;
        String wireSha256 = arguments.wireSha256;
        String sourceSha256 = arguments.sourceSha256;
        Path outputDirectory = arguments.outputDirectory;
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (!"BASE".equals(PrefixViaRampSupport.mode()))
            throw new IllegalStateException("Geometric prefix observation requires immutable BASE profile");

        OneShotObserver observer = null;
        CanonicalPrefixBlockerInspector blocker = null;
        CanonicalPrefixStageInspector stages = null;
        CanonicalGeometricReasonInspector reasons = null;
        ActualGeneratorBranchOrderStudy.TerminalPrefixInspector terminalInspector = null;
        P8CompositeInspector p8Composite = null;
        Object study = null, armRun = null, armResult = null;
        byte[] graphBytes = null, factsBytes = null, transportBytes = null;
        byte[] blockerBytes = null, stageBytes = null, p8SidecarBytes = null;
        byte[] parentIndexBytes = null, p8IndexBytes = null;
        String graphPayload = null, factsPayload = null;
        try {
            observer = new OneShotObserver();
            blocker = new CanonicalPrefixBlockerInspector(runId, inputSha256,
                wireSha256, false);
            stages = new CanonicalPrefixStageInspector(runId, inputSha256, wireSha256,
                !"NULL".equals(mode), false);
            reasons = new CanonicalGeometricReasonInspector(runId, inputSha256,
                sourceSha256, wireSha256, mode);
            p8Composite = new P8CompositeInspector(blocker, stages, reasons,
                "REASON_THROW".equals(mode));
            terminalInspector = p8Composite;

            Class<?> modeClass = Class.forName(OWNER + "$Mode");
            Method parseMode = modeClass.getDeclaredMethod("parse", String.class);
            parseMode.setAccessible(true);
            Object authoredMode = invoke(parseMode, null, "AUTHORED");
            Class<?> studyClass = Class.forName(OWNER + "$Study");
            Constructor<?> studyConstructor = studyClass.getDeclaredConstructor(modeClass,
                String.class, String.class, String.class, Path.class);
            studyConstructor.setAccessible(true);
            study = construct(studyConstructor, authoredMode, runId, inputSha256,
                wireSha256, outputDirectory);

            Class<?> armRunClass = Class.forName(OWNER + "$ArmRun");
            Constructor<?> armRunConstructor = armRunClass.getDeclaredConstructor(studyClass,
                Integer.TYPE, PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
            armRunConstructor.setAccessible(true);
            armRun = construct(armRunConstructor, study, Integer.valueOf(1), observer,
                terminalInspector);
            Method execute = armRunClass.getDeclaredMethod("execute");
            execute.setAccessible(true);
            long preparationNanos = elapsed(wrapperStarted, System.nanoTime());
            requireGroupTime(preparationNanos, "P8 preparation");

            try {
                armResult = invoke(execute, armRun);
            } catch (Throwable error) {
                long exceptionProofStarted = System.nanoTime();
                String exceptionGraphReferenceSha256 = null;
                Throwable observerProofFailure = null;
                try {
                    if ("REASON_THROW".equals(mode)) {
                        exceptionGraphReferenceSha256 = expectedP8ReasonThrowParentGraphSha256(
                            armRun, observer, p8Composite);
                    } else {
                        exceptionGraphReferenceSha256 = expectedP8ExceptionGraphSha256(
                            armRun, observer);
                    }
                } catch (Throwable proofFailure) {
                    observerProofFailure = proofFailure;
                }
                boolean outerObserverReferencesDropped = false;
                if (observer != null) {
                    try {
                        observer.drop();
                        outerObserverReferencesDropped = observer.referencesDropped();
                    } catch (Throwable dropFailure) {
                        observerProofFailure = appendP8Failure(observerProofFailure, dropFailure);
                    }
                }
                if (!outerObserverReferencesDropped) {
                    observerProofFailure = appendP8Failure(observerProofFailure,
                        new IllegalStateException("P8 outer observer retained graph references"));
                }
                try {
                    requireGroupTime(elapsed(exceptionProofStarted, System.nanoTime()),
                        "P8 exception proof/release");
                } catch (Throwable groupFailure) {
                    observerProofFailure = appendP8Failure(observerProofFailure, groupFailure);
                }
                if (observerProofFailure != null) {
                    if (error != observerProofFailure) error.addSuppressed(observerProofFailure);
                    rethrow(error);
                    throw new AssertionError("Unreachable");
                }
                if (!"REASON_THROW".equals(mode) || reasons == null ||
                        !reasons.isExpectedReasonThrowable(error) ||
                        blocker == null || stages == null ||
                        !blocker.referencesDropped() || !stages.referencesDropped() ||
                        !outerObserverReferencesDropped) {
                    rethrow(error);
                    throw new AssertionError("Unreachable");
                }
                return emitExpectedP8ReasonThrow(runId, inputSha256, sourceSha256,
                    wireSha256, outputDirectory, wrapperStarted, preparationNanos,
                    exceptionProofStarted, exceptionGraphReferenceSha256,
                    outerObserverReferencesDropped, error, blocker, stages, reasons,
                    boundedOut, boundedErr, outputBudget);
            }
            if ("REASON_THROW".equals(mode))
                throw new IllegalStateException("Expected geometric reason exception did not occur");

            long graphExportStarted = System.nanoTime();
            ArmEvidence arm = readArmEvidence(armResult);
            if (!arm.ownersReleased || !arm.contextRestored || arm.elapsedToCloseNanos <= 0 ||
                    arm.elapsedToCloseNanos > OPERATION_LIMIT_NANOS ||
                    arm.maxFullGroupNanos < 0 || arm.maxFullGroupNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                    arm.cleanupNanos < 0 || arm.cleanupNanos > CLEANUP_LIMIT_NANOS)
                throw new IOException("Actual P8 operation or cleanup exceeded its frozen bounds");
            Path receiptPath = outputDirectory.resolve("op-1.jsonl");
            FileEvidence receipt = requireRegularFile(receiptPath, "actual op-1 receipt");
            if (receipt.bytes != arm.receiptBytes || !receipt.sha256.equals(arm.receiptSha256) ||
                    receipt.bytes < 1 || receipt.bytes > OUTPUT_LIMIT_BYTES)
                throw new IOException("P8 operation receipt differs from its ArmResult identity");
            ReceiptEvidence receiptEvidence = scanReceipt(receiptPath, receipt.bytes);
            if (receiptEvidence.groupCount + 3 > 640)
                throw new IOException("Actual groups plus three wrapper groups exceed 640");
            String operationJson = operationJson(arm, receipt);

            CandidateEvidence candidate = readAndValidateCandidate(armRun);
            CapturedOwnerContext ownerContext = readAndValidateOwnerContext(armRun,
                candidate, observer);
            observer.requireOneCapture();
            PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot = observer.snapshot;
            PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage = observer.selectedLineage;
            validateCapture(candidate, ownerContext, lineage, snapshot);
            graphPayload = Rb100PhysicalDiagnostics.canonicalFixedPrefixGraphPayloadForStudy(
                snapshot, CASE_ID);
            graphBytes = utf8(graphPayload);
            graphPayload = null;
            if (graphBytes.length < 1 || graphBytes.length > OUTPUT_LIMIT_BYTES)
                throw new IOException("Canonical graph payload is outside its native bound");
            if (!CanonicalPrefixCutQueries.GRAPH_SHA.equals(sha256(graphBytes)))
                throw new IOException("Canonical graph payload differs from the frozen parent graph");
            factsPayload = factsJson(candidate, ownerContext, lineage, snapshot, runId,
                inputSha256, wireSha256);
            factsBytes = utf8(factsPayload);
            factsPayload = null;
            if (factsBytes.length < 1 || factsBytes.length > OUTPUT_LIMIT_BYTES)
                throw new IOException("Canonical facts payload is outside its native bound");
            String actualGraphSha256 = sha256(graphBytes);
            snapshot = null; lineage = null; ownerContext = null;

            String blockerPayload = blocker.completedJson(actualGraphSha256);
            String stagePayload = stages.completedJson(actualGraphSha256);
            String reasonPayload = reasons.completedSidecar();
            if (blockerPayload.indexOf('\r') >= 0 || blockerPayload.indexOf('\n') >= 0 ||
                    stagePayload.indexOf('\r') >= 0 || stagePayload.indexOf('\n') >= 0 ||
                    reasonPayload.indexOf('\r') >= 0)
                throw new IOException("Native observer payload framing is not canonical");
            blockerBytes = utf8(blockerPayload + "\n");
            blockerPayload = null;
            stageBytes = utf8(stagePayload + "\n");
            stagePayload = null;
            p8SidecarBytes = utf8(reasonPayload);
            reasonPayload = null;
            if (blockerBytes.length < 1 || blockerBytes.length > INDEX_LIMIT_BYTES ||
                    stageBytes.length < 1 || stageBytes.length > INDEX_LIMIT_BYTES ||
                    p8SidecarBytes.length < 1 || p8SidecarBytes.length > OUTPUT_LIMIT_BYTES ||
                    !blocker.referencesDropped() || !stages.referencesDropped() ||
                    !reasons.referencesDropped())
                throw new IOException("Native inspector payload or source-release bound failed");

            long availableTransport = OUTPUT_LIMIT_BYTES - receipt.bytes - blockerBytes.length -
                stageBytes.length - p8SidecarBytes.length - 2L * INDEX_LIMIT_BYTES;
            if (availableTransport < 1)
                throw new IOException("Parent artifacts leave no bounded transport capacity");
            transportBytes = transportBytes(runId, inputSha256, wireSha256,
                graphBytes, factsBytes, availableTransport);
            if (transportBytes.length < 1 || transportBytes.length > availableTransport)
                throw new IOException("Canonical transport exceeds the P8 aggregate capacity");
            observer.drop();

            Path transportPath = outputDirectory.resolve("fixed-prefix.jsonl");
            writeNewFile(transportPath, transportBytes);
            FileEvidence transport = requireRegularFile(transportPath, "canonical fixed-prefix transport");
            if (transport.bytes != transportBytes.length ||
                    !transport.sha256.equals(sha256(transportBytes)))
                throw new IOException("Canonical transport changed after CREATE_NEW write");
            String transportJson = transportJson(transport, graphBytes, factsBytes);

            Path blockerPath = outputDirectory.resolve("prefix-blockers.json");
            writeNewFile(blockerPath, blockerBytes);
            FileEvidence blockers = requireRegularFile(blockerPath, "blocker attribution");
            if (blockers.bytes != blockerBytes.length || !blockers.sha256.equals(sha256(blockerBytes)))
                throw new IOException("Blocker projection changed after CREATE_NEW write");
            String blockerRef = fileReferenceJson("prefix-blockers.json", blockers);

            Path stagePath = outputDirectory.resolve("prefix-stages.json");
            writeNewFile(stagePath, stageBytes);
            FileEvidence stageFile = requireRegularFile(stagePath, "predicate stages");
            if (stageFile.bytes != stageBytes.length || !stageFile.sha256.equals(sha256(stageBytes)))
                throw new IOException("Stage projection changed after CREATE_NEW write");
            String stageRef = fileReferenceJson("prefix-stages.json", stageFile);

            Path reasonPath = outputDirectory.resolve("prefix-geometric-reasons.jsonl");
            writeNewFile(reasonPath, p8SidecarBytes);
            FileEvidence reasonFile = requireRegularFile(reasonPath, "P8 reason sidecar");
            if (reasonFile.bytes != p8SidecarBytes.length ||
                    !reasonFile.sha256.equals(sha256(p8SidecarBytes)))
                throw new IOException("P8 reason sidecar changed after CREATE_NEW write");

            long graphExportNanos = elapsed(graphExportStarted, System.nanoTime());
            requireGroupTime(graphExportNanos, "P8 graph/artifact export");
            long postStarted = System.nanoTime();
            String parentIndexText = indexJson(mode, "BASE", runId, inputSha256,
                wireSha256, operationJson, transportJson, blockerRef, stageRef,
                observer.calls, 1, 1);
            parentIndexBytes = utf8(parentIndexText + "\n");
            if (parentIndexBytes.length < 1 || parentIndexBytes.length > INDEX_LIMIT_BYTES)
                throw new IOException("Canonical P6 parent index exceeded 64 KiB");
            Path parentIndexPath = outputDirectory.resolve("canonical-stage-index.json");
            writeNewFile(parentIndexPath, parentIndexBytes);
            FileEvidence parentIndex = requireRegularFile(parentIndexPath, "canonical P6 parent index");
            if (parentIndex.bytes != parentIndexBytes.length ||
                    !parentIndex.sha256.equals(sha256(parentIndexBytes)))
                throw new IOException("P6 parent index changed after CREATE_NEW write");

            long p8Aggregate = receipt.bytes + transport.bytes + blockers.bytes +
                stageFile.bytes + parentIndex.bytes + reasonFile.bytes;
            String p8IndexText = p8IndexJson(mode, runId, inputSha256, sourceSha256,
                wireSha256, receiptEvidence, parentIndex, reasonFile, p8Aggregate, reasons);
            p8IndexBytes = utf8(p8IndexText + "\n");
            if (p8IndexBytes.length < 1 || p8IndexBytes.length > INDEX_LIMIT_BYTES)
                throw new IOException("P8 index exceeded 64 KiB");
            Path p8IndexPath = outputDirectory.resolve("canonical-geometric-prefix-index.json");
            writeNewFile(p8IndexPath, p8IndexBytes);
            FileEvidence p8Index = requireRegularFile(p8IndexPath, "canonical P8 index");
            if (p8Index.bytes != p8IndexBytes.length || !p8Index.sha256.equals(sha256(p8IndexBytes)))
                throw new IOException("P8 index changed after CREATE_NEW write");
            requireP8OutputNames(outputDirectory, "op-1.jsonl", "fixed-prefix.jsonl",
                "prefix-blockers.json", "prefix-stages.json", "canonical-stage-index.json",
                "prefix-geometric-reasons.jsonl", "canonical-geometric-prefix-index.json");
            long aggregateBytes = p8Aggregate + p8Index.bytes;
            if (aggregateBytes > OUTPUT_LIMIT_BYTES)
                throw new IOException("All seven P8 operation files exceed 8 MiB");
            zero(graphBytes); zero(factsBytes); zero(transportBytes); zero(blockerBytes);
            zero(stageBytes); zero(p8SidecarBytes); zero(parentIndexBytes); zero(p8IndexBytes);
            graphBytes = null; factsBytes = null; transportBytes = null;
            blockerBytes = null; stageBytes = null; p8SidecarBytes = null;
            parentIndexBytes = null; p8IndexBytes = null;
            return p8PostAndEmit(mode, runId, inputSha256, sourceSha256, wireSha256,
                p8Index, aggregateBytes, preparationNanos, graphExportNanos, postStarted,
                wrapperStarted, arm.cleanupNanos, boundedOut, boundedErr, outputBudget);
        } finally {
            if (observer != null) observer.drop();
            zero(graphBytes); zero(factsBytes); zero(transportBytes); zero(blockerBytes);
            zero(stageBytes); zero(p8SidecarBytes); zero(parentIndexBytes); zero(p8IndexBytes);
            graphBytes = null; factsBytes = null; transportBytes = null;
            blockerBytes = null; stageBytes = null; p8SidecarBytes = null;
            parentIndexBytes = null; p8IndexBytes = null;
            graphPayload = null; factsPayload = null; armResult = null; armRun = null;
            study = null; terminalInspector = null; reasons = null; stages = null; blocker = null;
        }
    }

    private static P8Arguments p8Preflight(String[] args) throws P8PreflightFailure {
        if (args == null || args.length != 6)
            throw new P8PreflightFailure("ARGUMENT_COUNT", null);
        String mode = args[0], runId = args[1], inputSha = args[2];
        String wireSha = args[3], sourceSha = args[4], outputValue = args[5];
        if (!"NULL".equals(mode) && !"OBSERVE".equals(mode) && !"REASON_THROW".equals(mode))
            throw new P8PreflightFailure("INVALID_MODE", Integer.valueOf(0));
        if (runId == null || !UUID_PATTERN.matcher(runId).matches())
            throw new P8PreflightFailure("INVALID_RUN_UUID", Integer.valueOf(1));
        if (inputSha == null || !SHA_PATTERN.matcher(inputSha).matches())
            throw new P8PreflightFailure("INVALID_INPUT_SHA256", Integer.valueOf(2));
        if (wireSha == null || !SHA_PATTERN.matcher(wireSha).matches())
            throw new P8PreflightFailure("INVALID_WIRE_SHA256", Integer.valueOf(3));
        if (sourceSha == null || !SHA_PATTERN.matcher(sourceSha).matches())
            throw new P8PreflightFailure("INVALID_SOURCE_SHA256", Integer.valueOf(4));
        Path outputDirectory = p8OutputDirectory(outputValue);
        return new P8Arguments(mode, runId, inputSha, wireSha, sourceSha, outputDirectory);
    }

    private static Path p8OutputDirectory(String value) throws P8PreflightFailure {
        try {
            if (value == null) throw new IOException("Missing output directory");
            Path path = Paths.get(value);
            if (!path.isAbsolute() || !path.normalize().equals(path) ||
                    !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(path))
                throw new IOException("Output path is not an absolute real directory");
            BasicFileAttributes attributes = Files.readAttributes(path,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isOther() || !path.toRealPath().equals(path))
                throw new IOException("Output path resolves through a special entry");
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                if (entries.iterator().hasNext())
                    throw new P8PreflightFailure("OUTPUT_DIRECTORY_NOT_EMPTY", Integer.valueOf(5));
            }
            return path;
        } catch (P8PreflightFailure rejection) {
            throw rejection;
        } catch (Exception invalid) {
            throw new P8PreflightFailure("INVALID_OUTPUT_DIRECTORY", Integer.valueOf(5));
        }
    }

    private static final class P8Arguments {
        final String mode, runId, inputSha256, wireSha256, sourceSha256;
        final Path outputDirectory;
        P8Arguments(String modeValue, String run, String input, String wire,
                String source, Path directory) {
            mode = modeValue; runId = run; inputSha256 = input; wireSha256 = wire;
            sourceSha256 = source; outputDirectory = directory;
        }
    }

    private static final class P8PreflightFailure extends Exception {
        final String code;
        final Integer argumentIndex;
        P8PreflightFailure(String rejectionCode, Integer index) {
            super(rejectionCode);
            code = rejectionCode; argumentIndex = index;
        }
        String json() {
            StringBuilder json = new StringBuilder(200);
            json.append("{\"schema\":\"tsj-canonical-geometric-prefix-preflight-v1\",")
                .append("\"phase\":\"PREFLIGHT\",\"code\":");
            appendString(json, code);
            json.append(",\"argIndex\":")
                .append(argumentIndex == null ? "null" : argumentIndex.toString()).append('}');
            return json.toString();
        }
    }

    private static final class P8CompositeInspector
            implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
        final CanonicalPrefixBlockerInspector blocker;
        final CanonicalPrefixStageInspector stages;
        final CanonicalGeometricReasonInspector reasons;
        final boolean reasonThrow;
        private int reasonThrowTerminalProofCount;
        private P8ReasonThrowTerminalProof reasonThrowTerminalProof;
        P8CompositeInspector(CanonicalPrefixBlockerInspector p5,
                CanonicalPrefixStageInspector p6, CanonicalGeometricReasonInspector p8,
                boolean reasonThrow) {
            if (p5 == null || p6 == null || p8 == null)
                throw new IllegalArgumentException("Missing P8 observer-chain owner");
            blocker = p5; stages = p6; reasons = p8; this.reasonThrow = reasonThrow;
        }
        public void onTerminalPrefix(Object search, PcbBoardLayout layout,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot, String fingerprint,
                String prefixSha, int prefixBytes, int receiptOrdinal, int groupOrdinal) {
            blocker.onTerminalPrefix(search, layout, snapshot, fingerprint, prefixSha,
                prefixBytes, receiptOrdinal, groupOrdinal);
            if (receiptOrdinal == RECEIPT_BRANCH_ORDINAL) {
                long p5Units = counter(blocker, "copyUnits") + counter(blocker, "comparisonUnits") +
                    counter(blocker, "queryUnits") + counter(blocker, "geometryRowUnits");
                if (p5Units > 32768L) throw new IllegalStateException("P5 observation unit bound");
                stages.setPriorObservationUnits(p5Units);
            }
            stages.onTerminalPrefix(search, layout, snapshot, fingerprint, prefixSha,
                prefixBytes, receiptOrdinal, groupOrdinal);
            if (receiptOrdinal == RECEIPT_BRANCH_ORDINAL) {
                reasons.setPriorObservationUnits(p6TotalUnits(stages));
                if (reasonThrow) {
                    long reasonThrowProofStarted = System.nanoTime();
                    final PcbLayerRoutingPrototype.BranchSnapshot proofSnapshot = snapshot;
                    final String proofFingerprint = fingerprint;
                    final String proofPrefixSha = prefixSha;
                    final int proofPrefixBytes = prefixBytes;
                    final int proofReceiptOrdinal = receiptOrdinal;
                    final int proofGroupOrdinal = groupOrdinal;
                    reasons.recordReasonThrowTerminalProof(reasonThrowProofStarted, new Runnable() {
                        public void run() {
                            P8CompositeInspector.this.captureReasonThrowTerminalProof(
                                proofSnapshot, proofFingerprint, proofPrefixSha,
                                proofPrefixBytes, proofReceiptOrdinal, proofGroupOrdinal);
                        }
                    });
                }
            }
            reasons.onTerminalPrefix(search, layout, snapshot, fingerprint, prefixSha,
                prefixBytes, receiptOrdinal, groupOrdinal);
        }
        private void captureReasonThrowTerminalProof(
                PcbLayerRoutingPrototype.BranchSnapshot snapshot, String fingerprint,
                String prefixSha, int prefixBytes, int receiptOrdinal, int groupOrdinal) {
            if (!reasonThrow || reasonThrowTerminalProofCount != 0 ||
                    reasonThrowTerminalProof != null)
                throw new IllegalStateException("P8 expected-throw terminal branch proof was duplicated");
            P8ReasonThrowTerminalProof proof = P8ReasonThrowTerminalProof.capture(
                snapshot, fingerprint, prefixSha, prefixBytes, receiptOrdinal, groupOrdinal);
            reasonThrowTerminalProofCount++;
            reasonThrowTerminalProof = proof;
        }
        void requireAndDropReasonThrowTerminalProof() {
            P8ReasonThrowTerminalProof proof = reasonThrowTerminalProof;
            try {
                if (!reasonThrow || reasonThrowTerminalProofCount != 1 || proof == null ||
                        !proof.matchesFrozenParent() || !reasons.expectedReasonThrowObserved())
                    throw new IllegalStateException(
                        "P8 expected throw lacks actual terminal branch/prefix proof");
            } finally {
                dropReasonThrowTerminalProof();
            }
        }
        void dropReasonThrowTerminalProof() {
            if (reasonThrowTerminalProof != null) reasonThrowTerminalProof.clear();
            reasonThrowTerminalProof = null;
            reasonThrowTerminalProofCount = 0;
        }
        private static long counter(Object owner, String name) {
            try {
                long value = longField(owner, name);
                if (value < 0 || value > 32768L)
                    throw new IllegalStateException("P5/P6 prior counter bound");
                return value;
            } catch (Exception error) {
                throw new IllegalStateException("P5/P6 prior counter is unavailable", error);
            }
        }
        private static long p6TotalUnits(CanonicalPrefixStageInspector inspector) {
            try {
                Method method = inspector.getClass().getDeclaredMethod("totalObservationUnits");
                method.setAccessible(true);
                Object value = invoke(method, inspector);
                if (!(value instanceof Number)) throw new IllegalStateException("Missing hierarchical P6 unit total");
                long units = ((Number)value).longValue();
                if (units < 0 || units > 32768L)
                    throw new IllegalStateException("Hierarchical P6 observation unit bound");
                return units;
            } catch (Exception error) {
                throw new IllegalStateException("Could not bind the actual P6 hierarchical work total", error);
            }
        }
        public void afterOwnersClosed(boolean released, boolean restored) {
            Throwable failure = null;
            try { blocker.afterOwnersClosed(released, restored); }
            catch (Throwable error) { failure = error; }
            try { stages.afterOwnersClosed(released, restored); }
            catch (Throwable error) { failure = appendP8Failure(failure, error); }
            try { reasons.afterOwnersClosed(released, restored); }
            catch (Throwable error) { failure = appendP8Failure(failure, error); }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null) throw new IllegalStateException("P5/P6/P8 cleanup", failure);
        }
    }

    private static Throwable appendP8Failure(Throwable prior, Throwable next) {
        if (next == null) return prior;
        if (prior == null) return next;
        if (prior != next) prior.addSuppressed(next);
        return prior;
    }

    private static final class P8ReasonThrowTerminalProof {
        private int receiptOrdinal, groupOrdinal, prefixBytes, pass, branchExpanded;
        private int totalExpanded, frontierCount, prefixViaCount, gridWidth, gridHeight;
        private String netId, startPadId, rootPadId, terminalReason, prefixSha256;

        private P8ReasonThrowTerminalProof(
                PcbLayerRoutingPrototype.BranchSnapshot snapshot, int receiptOrdinal,
                int groupOrdinal, String prefixSha, int prefixBytes) {
            this.receiptOrdinal = receiptOrdinal; this.groupOrdinal = groupOrdinal;
            this.prefixBytes = prefixBytes; pass = snapshot.pass;
            branchExpanded = snapshot.branchExpanded; totalExpanded = snapshot.totalExpanded;
            frontierCount = snapshot.frontierCount; prefixViaCount = snapshot.prefixViaCount;
            gridWidth = snapshot.gridWidth; gridHeight = snapshot.gridHeight;
            netId = snapshot.netId; startPadId = snapshot.startPadId;
            rootPadId = snapshot.rootPadId; terminalReason = snapshot.terminalReason;
            prefixSha256 = prefixSha;
        }

        static P8ReasonThrowTerminalProof capture(
                PcbLayerRoutingPrototype.BranchSnapshot snapshot, String fingerprint,
                String prefixSha, int prefixBytes, int receiptOrdinal, int groupOrdinal) {
            if (snapshot == null || fingerprint == null || prefixSha == null ||
                    receiptOrdinal != RECEIPT_BRANCH_ORDINAL || groupOrdinal < 0)
                throw new IllegalStateException("P8 expected-throw callback omitted its actual terminal identity");
            byte[] prefix = utf8(fingerprint);
            try {
                if (prefix.length != prefixBytes || prefixBytes != EXPECTED_PREFIX_BYTES ||
                        !EXPECTED_PREFIX_SHA256.equals(prefixSha) ||
                        !prefixSha.equals(sha256(prefix)))
                    throw new IllegalStateException("P8 expected-throw prefix differs from the actual parent branch");
            } finally {
                zero(prefix);
            }
            P8ReasonThrowTerminalProof proof = new P8ReasonThrowTerminalProof(
                snapshot, receiptOrdinal, groupOrdinal, prefixSha, prefixBytes);
            if (!proof.matchesFrozenParent()) {
                proof.clear();
                throw new IllegalStateException("P8 expected-throw BranchSnapshot differs from the frozen parent");
            }
            return proof;
        }

        boolean matchesFrozenParent() {
            return receiptOrdinal == RECEIPT_BRANCH_ORDINAL && groupOrdinal >= 0 &&
                prefixBytes == EXPECTED_PREFIX_BYTES &&
                EXPECTED_PREFIX_SHA256.equals(prefixSha256) && pass == PASS &&
                branchExpanded == EXPECTED_BRANCH_EXPANDED &&
                totalExpanded == EXPECTED_TOTAL_EXPANSIONS && frontierCount == 0 &&
                prefixViaCount == MAX_VIAS && gridWidth == 478 && gridHeight == 309 &&
                "A_COIL_LOW".equals(netId) && "QA.C".equals(startPadId) &&
                "DA.A".equals(rootPadId) && "NO_PATH".equals(terminalReason);
        }

        void clear() {
            receiptOrdinal = -1; groupOrdinal = -1; prefixBytes = 0; pass = -1;
            branchExpanded = 0; totalExpanded = 0; frontierCount = 0;
            prefixViaCount = 0; gridWidth = 0; gridHeight = 0;
            netId = null; startPadId = null; rootPadId = null;
            terminalReason = null; prefixSha256 = null;
        }
    }

    private static String expectedP8ReasonThrowParentGraphSha256(Object armRun,
            OneShotObserver observer, P8CompositeInspector terminalInspector) throws Exception {
        CandidateEvidence candidate = null;
        try {
            if (observer == null || terminalInspector == null)
                throw new IllegalStateException("P8 reason throw omitted an actual observer-chain owner");
            // Diagnostics run before this target lineage is offered; prior rejected offers are valid.
            observer.requireNoSelectedCapture();
            candidate = readAndValidateCandidate(armRun);
            terminalInspector.requireAndDropReasonThrowTerminalProof();
            // The callback throws before P07 selects lineage or captures a graph. This is only
            // the qualified NULL parent reference; the reader binds it to that actual capture.
            return CanonicalPrefixCutQueries.GRAPH_SHA;
        } finally {
            if (terminalInspector != null) terminalInspector.dropReasonThrowTerminalProof();
            candidate = null;
        }
    }

    private static String expectedP8ExceptionGraphSha256(Object armRun,
            OneShotObserver observer) throws Exception {
        CandidateEvidence candidate = null;
        CapturedOwnerContext ownerContext = null;
        PcbLayerRoutingPrototype.FixedPrefixGraphSnapshot snapshot = null;
        PcbLayerRoutingPrototype.FixedPrefixBranchLineage lineage = null;
        String graphPayload = null;
        byte[] graphBytes = null;
        try {
            if (observer == null) throw new IllegalStateException("P8 exception omitted its outer observer");
            observer.requireOneCapture();
            candidate = readAndValidateCandidate(armRun);
            ownerContext = readAndValidateOwnerContext(armRun, candidate, observer);
            snapshot = observer.snapshot;
            lineage = observer.selectedLineage;
            validateCapture(candidate, ownerContext, lineage, snapshot);
            graphPayload = Rb100PhysicalDiagnostics.canonicalFixedPrefixGraphPayloadForStudy(
                snapshot, CASE_ID);
            graphBytes = utf8(graphPayload);
            graphPayload = null;
            String graphSha256 = sha256(graphBytes);
            if (graphBytes.length < 1 || graphBytes.length > OUTPUT_LIMIT_BYTES ||
                    !CanonicalPrefixCutQueries.GRAPH_SHA.equals(graphSha256))
                throw new IOException("P8 exception graph differs from the frozen parent graph");
            return graphSha256;
        } finally {
            zero(graphBytes);
            graphBytes = null;
            graphPayload = null;
            snapshot = null;
            lineage = null;
            ownerContext = null;
            candidate = null;
        }
    }

    private static int emitExpectedP8ReasonThrow(String runId, String inputSha256,
            String sourceSha256, String wireSha256, Path outputDirectory,
            long wrapperStarted, long preparationNanos, long combinedExportStarted,
            String parentGraphReferenceSha256, boolean outerObserverReferencesDropped,
            Throwable expectedThrowable, CanonicalPrefixBlockerInspector blocker,
            CanonicalPrefixStageInspector stages, CanonicalGeometricReasonInspector reasons,
            PrintStream boundedOut, PrintStream boundedErr,
            SharedByteBudget outputBudget) throws Exception {
        if (outputBudget.bytesUsed() != 0 || !reasons.expectedReasonThrowObserved() ||
                !outerObserverReferencesDropped || parentGraphReferenceSha256 == null ||
                !CanonicalPrefixCutQueries.GRAPH_SHA.equals(parentGraphReferenceSha256))
            throw new IllegalStateException("P8 expected reason throw lost its output or cleanup proof");
        requireGroupTime(preparationNanos, "P8 expected-throw preparation");
        String blockerPayload = blocker.completedJson(parentGraphReferenceSha256);
        String stagePayload = stages.completedJson(parentGraphReferenceSha256);
        String reasonPayload = reasons.expectedReasonSidecar();
        if (blockerPayload.indexOf('\r') >= 0 || blockerPayload.indexOf('\n') >= 0 ||
                stagePayload.indexOf('\r') >= 0 || stagePayload.indexOf('\n') >= 0 ||
                reasonPayload.indexOf('\r') >= 0)
            throw new IOException("P8 exception projections are not bare canonical JSON lines");
        byte[] blockerBytes = utf8(blockerPayload + "\n");
        byte[] stageBytes = utf8(stagePayload + "\n");
        byte[] reasonBytes = utf8(reasonPayload);
        blockerPayload = null; stagePayload = null; reasonPayload = null;
        if (blockerBytes.length < 1 || blockerBytes.length > INDEX_LIMIT_BYTES ||
                stageBytes.length < 1 || stageBytes.length > INDEX_LIMIT_BYTES ||
                reasonBytes.length < 1 || reasonBytes.length > INDEX_LIMIT_BYTES ||
                !blocker.referencesDropped() || !stages.referencesDropped() ||
                !reasons.expectedReasonThrowObserved())
            throw new IOException("P8 exception output exceeds its proof or line bounds");

        Path receiptPath = outputDirectory.resolve("op-1.jsonl");
        FileEvidence receipt = requireRegularFile(receiptPath, "partial P8 operation receipt");
        if (receipt.bytes < 1 || receipt.bytes > OUTPUT_LIMIT_BYTES)
            throw new IOException("Partial P8 receipt exceeds the native file bound");
        ReceiptEvidence partialReceipt = scanPartialReceipt(receiptPath, receipt.bytes);
        if (partialReceipt.groupCount < 1 || partialReceipt.groupCount + 3 > 640)
            throw new IOException("Partial operation groups plus three wrapper groups exceed 640");
        FileEvidence receiptAfterScan = requireRegularFile(receiptPath, "partial P8 operation receipt recheck");
        if (receiptAfterScan.bytes != receipt.bytes || !receiptAfterScan.sha256.equals(receipt.sha256))
            throw new IOException("Partial operation receipt changed during its GROUP scan");
        requireOnlyReceipt(outputDirectory, receiptPath);
        Path blockerPath = outputDirectory.resolve("prefix-blockers.json");
        Path stagePath = outputDirectory.resolve("prefix-stages.json");
        Path reasonPath = outputDirectory.resolve("prefix-geometric-reasons.jsonl");
        writeNewFile(blockerPath, blockerBytes);
        FileEvidence blockerFile = requireRegularFile(blockerPath, "completed P5 blocker projection");
        if (blockerFile.bytes != blockerBytes.length ||
                !blockerFile.sha256.equals(sha256(blockerBytes)))
            throw new IOException("P5 exception parent changed after write");
        writeNewFile(stagePath, stageBytes);
        FileEvidence stageFile = requireRegularFile(stagePath, "completed P6 stage projection");
        if (stageFile.bytes != stageBytes.length || !stageFile.sha256.equals(sha256(stageBytes)))
            throw new IOException("P6 exception parent changed after write");
        writeNewFile(reasonPath, reasonBytes);
        FileEvidence reasonFile = requireRegularFile(reasonPath, "partial P8 reason sidecar");
        if (reasonFile.bytes != reasonBytes.length || !reasonFile.sha256.equals(sha256(reasonBytes)))
            throw new IOException("P8 partial sidecar changed after write");
        requireP8OutputNames(outputDirectory, "op-1.jsonl", "prefix-blockers.json",
            "prefix-stages.json", "prefix-geometric-reasons.jsonl");
        long aggregateBytes = receipt.bytes + blockerFile.bytes + stageFile.bytes + reasonFile.bytes;
        if (aggregateBytes > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 expected-throw files exceed the native 8 MiB aggregate");
        long graphExportNanos = elapsed(combinedExportStarted, System.nanoTime());
        try {
            requireGroupTime(graphExportNanos, "P8 exception graph/artifact export");
        } catch (Throwable groupFailure) {
            if (expectedThrowable != groupFailure) expectedThrowable.addSuppressed(groupFailure);
            rethrow(expectedThrowable);
            throw new AssertionError("Unreachable");
        }
        long postStarted = System.nanoTime();
        long workNanos = elapsed(wrapperStarted, System.nanoTime());
        if (workNanos > OPERATION_LIMIT_NANOS)
            throw new IOException("P8 expected exception exceeded its 90-second native bound");
        StringBuilder marker = new StringBuilder(1800);
        marker.append("{\"schema\":\"tsj-canonical-geometric-prefix-exception-v1\",")
            .append("\"runId\":"); appendString(marker, runId);
        marker.append(",\"mode\":\"REASON_THROW\",\"inputSha256\":");
        appendString(marker, inputSha256);
        marker.append(",\"sourceSha256\":"); appendString(marker, sourceSha256);
        marker.append(",\"wireSha256\":"); appendString(marker, wireSha256);
        marker.append(",\"queryOrdinal\":0,\"parentOrdinal\":0")
            .append(",\"sentinel\":\"FIRST_GEOMETRIC_REASON_CALLBACK\"")
            .append(",\"exceptionClass\":\"java.lang.IllegalStateException\"")
            .append(",\"exceptionMessage\":\"FIRST_GEOMETRIC_REASON_CALLBACK\"")
            .append(",\"suppressedCount\":0")
            .append(",\"cleanupKind\":\"FULL_NATIVE_WALL_PLUS_POST_SOURCE\"")
            .append(",\"workNanos\":").append(workNanos)
            .append(",\"cleanupComplete\":true,\"sourcePreserved\":true")
            .append(",\"observersDetached\":").append(outerObserverReferencesDropped)
            .append(",\"referencesDropped\":").append(outerObserverReferencesDropped)
            .append(",\"ownersReleased\":true,\"contextRestored\":true")
            .append(",\"partialSidecar\":").append(fileReferenceJson(
                "prefix-geometric-reasons.jsonl", reasonFile))
            .append(",\"partialOperationReceipt\":").append(fileReferenceJson(
                "op-1.jsonl", receipt))
            .append(",\"parentBlockers\":").append(fileReferenceJson(
                "prefix-blockers.json", blockerFile))
            .append(",\"parentStages\":").append(fileReferenceJson(
                "prefix-stages.json", stageFile)).append('}');
        byte[] frame = utf8("CANONICAL_GEOMETRIC_PREFIX_EXPECTED_REASON_THROW " +
            marker.toString() + "\n");
        if (frame.length > LINE_LIMIT_BYTES || outputBudget.bytesUsed() != 0 ||
                aggregateBytes + frame.length > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 expected exception marker exceeds its line/output bounds");
        requireGroupTime(elapsed(postStarted, System.nanoTime()), "P8 expected exception marker preparation");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        boundedErr.write(frame, 0, frame.length);
        boundedErr.flush();
        reasons.dropExpectedReasonThrowable();
        zero(blockerBytes); zero(stageBytes); zero(reasonBytes); zero(frame);
        long finalElapsed = elapsed(wrapperStarted, System.nanoTime());
        requireGroupTime(elapsed(postStarted, System.nanoTime()), "P8 expected exception marker");
        if (finalElapsed > OPERATION_LIMIT_NANOS ||
                aggregateBytes + outputBudget.bytesUsed() > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 expected exception final output exceeded its bound");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != frame.length)
            throw new IOException("P8 expected exception wrote an unexpected output stream");
        return 1;
    }

    private static int p8PostAndEmit(String mode, String runId, String inputSha256,
            String sourceSha256, String wireSha256, FileEvidence index, long aggregateBytes,
            long preparationNanos, long graphExportNanos, long postStarted,
            long wrapperStarted, long cleanupNanos, PrintStream boundedOut,
            PrintStream boundedErr, SharedByteBudget outputBudget) throws IOException {
        long postNanos = elapsed(postStarted, System.nanoTime());
        long elapsedNanos = elapsed(wrapperStarted, System.nanoTime());
        requireGroupTime(postNanos, "P8 post-index/output");
        long workNanos = subtractCleanup(elapsedNanos, cleanupNanos);
        if (workNanos > OPERATION_LIMIT_NANOS ||
                preparationNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                graphExportNanos > WRAPPER_GROUP_LIMIT_NANOS ||
                aggregateBytes > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 wrapper work or aggregate exceeds its bound");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != 0)
            throw new IOException("P8 wrote output before its terminal success marker");
        StringBuilder json = new StringBuilder(900);
        json.append("{\"schema\":\"tsj-canonical-geometric-prefix-complete-v1\",")
            .append("\"mode\":"); appendString(json, mode);
        json.append(",\"runId\":"); appendString(json, runId);
        json.append(",\"inputSha256\":"); appendString(json, inputSha256);
        json.append(",\"sourceSha256\":"); appendString(json, sourceSha256);
        json.append(",\"wireSha256\":"); appendString(json, wireSha256);
        json.append(",\"index\":").append(fileReferenceJson(
            "canonical-geometric-prefix-index.json", index))
            .append(",\"aggregateBytes\":").append(aggregateBytes)
            .append(",\"workNanos\":").append(workNanos).append('}');
        byte[] trailer = utf8("CANONICAL_GEOMETRIC_PREFIX_COMPLETE " + json.toString() + "\n");
        if (trailer.length > LINE_LIMIT_BYTES || aggregateBytes + trailer.length > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 completion marker exceeds 64 KiB");
        boundedOut.write(trailer, 0, trailer.length);
        boundedOut.flush();
        long finalElapsed = elapsed(wrapperStarted, System.nanoTime());
        long finalPost = elapsed(postStarted, System.nanoTime());
        requireGroupTime(finalPost, "P8 post-index/output including final flush");
        if (subtractCleanup(finalElapsed, cleanupNanos) > OPERATION_LIMIT_NANOS ||
                aggregateBytes + outputBudget.bytesUsed() > OUTPUT_LIMIT_BYTES)
            throw new IOException("P8 final wrapper output exceeded its native bound");
        requireOutputHealthy(boundedOut, boundedErr, outputBudget);
        if (outputBudget.bytesUsed() != trailer.length)
            throw new IOException("P8 success output was not exactly one stdout marker");
        zero(trailer);
        return 0;
    }

    private static String p8IndexJson(String mode, String runId, String inputSha256,
            String sourceSha256, String wireSha256, ReceiptEvidence receipt,
            FileEvidence parentIndex, FileEvidence reasonFile, long aggregateBytes,
            CanonicalGeometricReasonInspector reasons) {
        StringBuilder json = new StringBuilder(1900);
        json.append("{\"schema\":\"tsj-canonical-geometric-prefix-index-v1\",")
            .append("\"mode\":"); appendString(json, mode);
        json.append(",\"runId\":"); appendString(json, runId);
        json.append(",\"inputSha256\":"); appendString(json, inputSha256);
        json.append(",\"sourceSha256\":"); appendString(json, sourceSha256);
        json.append(",\"wireSha256\":"); appendString(json, wireSha256);
        json.append(",\"parentPrefixSha256\":\"")
            .append("6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb\"")
            .append(",\"parentGraphSha256\":\"")
            .append("ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a\"")
            .append(",\"selectedQueryOrdinals\":[0,1,2,3,4,5,6,7,8,9,11,15,17,20]")
            .append(",\"parentStageIndex\":")
            .append(fileReferenceJson("canonical-stage-index.json", parentIndex))
            .append(",\"reasonSidecar\":")
            .append(fileReferenceJson("prefix-geometric-reasons.jsonl", reasonFile))
            .append(",\"queryCount\":").append(reasons.queryCountP8())
            .append(",\"returnedQueryCount\":").append(reasons.returnedQueryCountP8())
            .append(",\"fineEventCount\":").append(reasons.fineEventCountP8())
            .append(",\"coarseEventCount\":").append(reasons.coarseEventCountP8())
            .append(",\"aggregateBytes\":").append(aggregateBytes)
            .append(",\"priorObservationUnits\":").append(reasons.priorObservationUnits())
            .append(",\"p8ObservationUnits\":").append(reasons.p8ObservationUnits())
            .append(",\"totalObservationUnits\":").append(
                reasons.priorObservationUnits() + reasons.p8ObservationUnits())
            .append(",\"cleanupKind\":\"FULL_NATIVE_WALL_PLUS_POST_SOURCE\"")
            .append(",\"cleanupComplete\":true}");
        return json.toString();
    }

    private static void requireP8OutputNames(Path directory, String... names) throws IOException {
        Set<String> expected = new HashSet<String>(Arrays.asList(names));
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                count++;
                if (!expected.remove(entry.getFileName().toString()))
                    throw new IOException("P8 output directory contains an unapproved artifact");
            }
        }
        if (count != names.length || !expected.isEmpty())
            throw new IOException("P8 output directory is missing an exact artifact");
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

    private static ReceiptEvidence scanReceipt(Path path, long expectedBytes) throws IOException {
        ReceiptEvidence result = new ReceiptEvidence();
        ClearableByteArrayOutputStream lineBytes = new ClearableByteArrayOutputStream(4096);
        long totalBytes = 0;
        boolean endedWithLf = false;
        int actualGroups = 0;
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
                    if (line.startsWith("{\"record\":\"GROUP\",")) actualGroups++;
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
        if (result.groupCount != actualGroups)
            throw new IOException("Operation footer group count differs from actual GROUP rows");
        result.groupCount = actualGroups;
        return result;
    }

    private static ReceiptEvidence scanPartialReceipt(Path path, long expectedBytes) throws IOException {
        ReceiptEvidence result = new ReceiptEvidence();
        ClearableByteArrayOutputStream lineBytes = new ClearableByteArrayOutputStream(4096);
        long totalBytes = 0;
        boolean endedWithLf = false;
        int actualGroups = 0;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            int value;
            while ((value = input.read()) >= 0) {
                totalBytes++;
                if (totalBytes > expectedBytes || totalBytes > OUTPUT_LIMIT_BYTES)
                    throw new IOException("Partial operation receipt exceeded its bounded identity");
                if (value == '\r') throw new IOException("Partial operation receipt contains CR bytes");
                if (value == '\n') {
                    if (lineBytes.size() + 1 > LINE_LIMIT_BYTES)
                        throw new IOException("Partial operation receipt line exceeds 64 KiB");
                    byte[] raw = lineBytes.toByteArray();
                    String line;
                    try { line = decodeUtf8(raw); }
                    finally { zero(raw); }
                    if (line.length() == 0)
                        throw new IOException("Partial operation receipt has an empty row");
                    if (line.startsWith("{\"record\":\"GROUP\",")) actualGroups++;
                    if (line.startsWith("{\"record\":\"FOOTER\",")) {
                        result.footerCount++;
                        if (result.footerCount > 1)
                            throw new IOException("Partial operation receipt has duplicate FOOTER rows");
                        requireJsonNumber(line, "operation", 1);
                        long declaredGroups = jsonNumber(line, "groupCount");
                        if (declaredGroups < 1 || declaredGroups > 640)
                            throw new IOException("Partial operation receipt footer group count is out of bounds");
                        result.groupCount = (int)declaredGroups;
                    }
                    lineBytes.clear();
                    endedWithLf = true;
                } else {
                    endedWithLf = false;
                    if (lineBytes.size() + 1 >= LINE_LIMIT_BYTES)
                        throw new IOException("Partial operation receipt line exceeds 64 KiB");
                    lineBytes.write(value);
                }
            }
        } finally {
            lineBytes.clear();
        }
        if (!endedWithLf || lineBytes.size() != 0 || totalBytes != expectedBytes || actualGroups < 1)
            throw new IOException("Partial operation receipt framing or group census is incomplete");
        if (result.footerCount > 0 && result.groupCount != actualGroups)
            throw new IOException("Partial operation receipt footer differs from actual GROUP rows");
        result.groupCount = actualGroups;
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

        void requireNoSelectedCapture() {
            if (calls < 0 || selected != 0 || callbacks != 0 ||
                    selectedLineage != null || snapshot != null)
                throw new IllegalStateException("Expected early P8 throw must precede target selection/capture");
        }

        void drop() {
            snapshot = null;
            selectedLineage = null;
        }

        boolean referencesDropped() {
            return snapshot == null && selectedLineage == null;
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
