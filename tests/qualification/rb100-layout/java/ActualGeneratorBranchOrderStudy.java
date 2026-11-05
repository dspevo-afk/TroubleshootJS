package com.lushprojects.circuitjs1.client;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.Base64;
import java.lang.reflect.Modifier;
import java.util.regex.Pattern;

/** Bounded producer for the frozen actual-generator branch-order report. */
public final class ActualGeneratorBranchOrderStudy {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SCHEMA = "p07-actual-branch-order-study-v5";
    private static final String INDEX_SCHEMA = "tsj-branch-order-index-v1";
    private static final String DEPENDENCY_EPOCH = "tsj-generation-dependencies-v28";
    private static final long AUTHORED_ROOT = 9007199254740993L;
    private static final long AUTHORED_PLACEMENT = -6587958367974952701L;
    private static final long AUTHORED_ROUTING = 8445301674744508279L;
    private static final long TINY_ROOT = 42L;
    private static final int TINY_PLACEMENT_VERSION = 12;
    private static final long TINY_PLACEMENT = 5526715536693200536L;
    private static final long TINY_ROUTING = 4493419427463069983L;
    private static final int MAX_VIAS = 48;
    private static final long OPERATION_LIMIT_NANOS = 90000000000L;
    private static final long GROUP_LIMIT_NANOS = 5000000000L;
    private static final long CLEANUP_LIMIT_NANOS = 120000000000L;
    private static final int GROUP_LIMIT = 640;
    private static final int STEP_LIMIT = 32768;
    private static final int RECEIPT_LIMIT_BYTES = 8 * 1024 * 1024;
    private static final int STUDY_LIMIT_BYTES = 32 * 1024 * 1024;
    private static final int LINE_LIMIT_BYTES = 65536;
    private static final int PAD_LIMIT = 1024;
    private static final int PREFIX_LIMIT_BYTES = 1024 * 1024;
    private static final int PUBLISHER_PATH_NODE_LIMIT = 4096;
    private static final int PUBLISHER_SYNC_COPY_LIMIT = 1024 * 1024;
    private static final int PUBLISHER_PAYLOAD_LIMIT = 2 * 1024 * 1024;
    private static final int PUBLISHER_CHUNK_BYTES = 4096;
    private static final int DIAGNOSTIC_GROUP_UNITS = 32768;
    private static final int PUBLISHER_FOUND_METADATA_ENTRIES = 35;
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private static final String[] ARM_IDS = {
        "PLAIN_ENTRY_RECEIPT_CONTROL", "OBSERVED_DEFAULT_NEAREST",
        "OBSERVED_EXPLICIT_NEAREST", "SECOND_ORDER_MINIMUM_PAIR",
        "MINIMUM_PAIR_PUBLISHER_CAPTURE"
    };
    private static final String[] ARM_APIS = {
        "PLAIN_BEGIN", "BOUNDARY_CONVENIENCE", "BOUNDARY_EXPLICIT", "BOUNDARY_EXPLICIT",
        "BOUNDARY_EXPLICIT"
    };
    private static final String[] ARM_STRATEGIES = {
        "CANONICAL_NEAREST", "CANONICAL_NEAREST", "CANONICAL_NEAREST", "CANONICAL_NEAREST",
        "CANONICAL_NEAREST"
    };
    private static final String[] ARM_NET_ORDER_STRATEGIES = {
        "CANONICAL", "CANONICAL", "CANONICAL", "SECOND_ORDER_MINIMUM_PAIR",
        "SECOND_ORDER_MINIMUM_PAIR"
    };
    private static final String[] SNAPSHOT_INTS = {
        "pass", "startX", "startY", "rootX", "rootY", "gridWidth", "gridHeight",
        "goalsTop", "goalsBottom", "prefixViaCount", "reachedPadCount",
        "initialManhattanDistance", "minimumExploredManhattanDistance", "branchExpanded",
        "totalExpanded", "expandedTop", "expandedBottom", "frontierCount", "bestStateCount",
        "relaxedBoundWorkUnits", "relaxedBoundOrdinaryProbes", "relaxedBoundViaProbes",
        "relaxedBoundRefreshes"
    };
    private static final String[] SNAPSHOT_BOOLS = {
        "relaxedBoundStarted", "relaxedBoundComplete", "relaxedBoundObservationChecked",
        "relaxedBoundLiveLayoutStable", "relaxedBoundRouterStateStable",
        "relaxedBoundRouterEffectsStable"
    };
    private static final String[] SNAPSHOT_STRINGS = {
        "netId", "startPadId", "rootPadId", "terminalReason"
    };

    private ActualGeneratorBranchOrderStudy() { }
    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 5)
            throw new IllegalArgumentException("Expected exactly five positional arguments");
        Mode mode = Mode.parse(args[0]);
        String runId = args[1];
        String sourceManifestSha256 = args[2];
        String wireSha256 = args[3];
        if (!UUID_PATTERN.matcher(runId).matches())
            throw new IllegalArgumentException("runId must be a lowercase UUID");
        requireSha(sourceManifestSha256, "sourceManifestSHA256");
        requireSha(wireSha256, "wireSHA256");
        Path outputDirectory = Paths.get(args[4]);
        if (!outputDirectory.isAbsolute())
            throw new IllegalArgumentException("outputDirectory must be absolute");
        outputDirectory = outputDirectory.normalize();
        if (!Files.isDirectory(outputDirectory, LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(outputDirectory))
            throw new IllegalArgumentException("outputDirectory must be an existing real directory");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(outputDirectory)) {
            if (entries.iterator().hasNext())
                throw new IllegalArgumentException("outputDirectory must be empty before Java writes");
        }
        new Study(mode, runId, sourceManifestSha256, wireSha256, outputDirectory).run();
        System.out.println("BRANCH_ORDER_STUDY_COMPLETE");
    }

    private enum Mode {
        TINY_CANARY, AUTHORED;
        static Mode parse(String value) {
            if ("TINY_CANARY".equals(value)) return TINY_CANARY;
            if ("AUTHORED".equals(value)) return AUTHORED;
            throw new IllegalArgumentException("mode must be TINY_CANARY or AUTHORED");
        }
        String reportMode() { return this == AUTHORED ? "AUTHORED" : "TINY_CANARY"; }
        String indexKind() { return this == AUTHORED ? "AUTHORED" : "TINY_NATIVE"; }
    }

    private static final class Study {
        final Mode mode;
        final String runId;
        final String sourceManifestSha256;
        final String wireSha256;
        final Path outputDirectory;
        final List<ArmResult> arms = new ArrayList<ArmResult>();
        long receiptBytes;
        long cleanupNanos;

        Study(Mode mode, String runId, String sourceManifestSha256,
                String wireSha256, Path outputDirectory) {
            this.mode = mode;
            this.runId = runId;
            this.sourceManifestSha256 = sourceManifestSha256;
            this.wireSha256 = wireSha256;
            this.outputDirectory = outputDirectory;
        }

        void run() throws Exception {
            for (int operation = 0; operation < 5; operation++) {
                ArmResult result = new ArmRun(this, operation).execute();
                if (receiptBytes > STUDY_LIMIT_BYTES - result.receiptBytes)
                    throw new IOException("Study JSONL receipts exceeded 32 MiB");
                receiptBytes += result.receiptBytes;
                if (cleanupNanos > CLEANUP_LIMIT_NANOS - result.cleanupNanos)
                    throw new IOException("Cumulative context cleanup exceeded 120 seconds");
                cleanupNanos += result.cleanupNanos;
                arms.add(result);
            }
            if (arms.size() != 5 || receiptBytes > STUDY_LIMIT_BYTES ||
                    cleanupNanos > CLEANUP_LIMIT_NANOS)
                throw new IOException("Study accounting did not close within its frozen bounds");
            writeIndex();
        }

        private void writeIndex() throws IOException {
            long started = System.nanoTime();
            StringBuilder operations = new StringBuilder("[");
            for (int index = 0; index < arms.size(); index++) {
                if (index != 0) operations.append(',');
                ArmResult arm = arms.get(index);
                JsonObject cleanup = new JsonObject();
                cleanup.string("status", "PASS");
                cleanup.number("elapsedNanos", arm.cleanupNanos);
                cleanup.bool("ownersReleased", true);
                cleanup.bool("contextRestored", true);
                JsonObject receipt = new JsonObject();
                receipt.string("path", "op-" + index + ".jsonl");
                receipt.number("bytes", arm.receiptBytes);
                receipt.string("sha256", arm.receiptSha256);
                JsonObject operation = new JsonObject();
                operation.number("operation", index);
                operation.string("armId", ARM_IDS[index]);
                operation.raw("receipt", receipt.finish());
                operation.number("elapsedToCloseNanos", arm.elapsedToCloseNanos);
                operation.number("maxFullGroupNanos", arm.maxFullGroupNanos);
                operation.raw("cleanup", cleanup.finish());
                operations.append(operation.finish());
            }
            operations.append(']');
            JsonObject index = new JsonObject();
            index.string("schema", INDEX_SCHEMA);
            index.string("runId", runId);
            index.string("kind", mode.indexKind());
            index.string("sourceManifestSha256", sourceManifestSha256);
            index.string("wireSha256", wireSha256);
            index.raw("operations", operations.toString());
            byte[] bytes = utf8(index.finish() + "\n");
            if (bytes.length > 65536) throw new IOException("StudyIndex exceeded 64 KiB");
            Path path = outputDirectory.resolve("study-index.json");
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                output.write(bytes);
                output.flush();
            }
            long indexNanos = elapsed(started, System.nanoTime());
            if (cleanupNanos > CLEANUP_LIMIT_NANOS - indexNanos)
                throw new IOException("Cumulative cleanup and terminal index exceeded 120 seconds");
        }
    }
    private static final class ArmRun {
        final Study study;
        final int operation;
        final long operationStarted = System.nanoTime();
        JsonlSink sink;
        NativeContextFacade nativeContext;
        Rb100ElectricalPilot.Candidate electricalCandidate;
        Rb56Plan tinyPlan;
        Rb100Plan authoredPlan;
        TroubleshootBoard board;
        SeededPcbLayoutGenerator generator;
        SeededPcbLayoutGenerator.Session ownerSession;
        GroupLedger currentGroup;
        Object lastActualRoute;
        Object validatedDiagnosticRoute;
        BranchTracking branchTracking;
        String justTerminalBranchKey;
        final Set<String> diagnosticEventKeys = new HashSet<String>();
        final Set<String> padNetIds = new HashSet<String>();
        Object capturedNetOrderSearch;
        final IdentityHashMap<Object, Integer> publisherIdentityTokens =
            new IdentityHashMap<Object, Integer>();
        int nextPublisherIdentityToken = 1;
        int publisherPollSequence;
        PublisherCapture publisherCapture;
        String pass0TailNet;
        String pass0TailReason;
        int pass0TerminalCount;
        int pass1TerminalCount;
        int netOrderRecordCount;
        boolean pass0AllFound = true;
        boolean pass0EarlierAllFound = true;
        boolean netOrderCaptured;
        long diagnosticCloneSteps;
        CapturedContext capturedContext;
        CandidateHeader candidateHeader;
        RequestObservation request;
        long maxFullGroupNanos;
        int nextGroupOrdinal;
        int branchCount;
        int padCount;
        long measuredSlices;
        long ownerSteps;
        long generatorAdvances;

        ArmRun(Study study, int operation) {
            this.study = study;
            this.operation = operation;
        }

        ArmResult execute() throws Exception {
            Throwable failure = null;
            ArmResult result = null;
            try {
                executeData();
                long operationNanos = elapsed(operationStarted, System.nanoTime());
                if (operationNanos <= 0 || operationNanos > OPERATION_LIMIT_NANOS)
                    throw new IOException("Arm exceeded the 90 second operation clock");
                result = new ArmResult(sink.byteCount(), sink.finishSha256(), operationNanos,
                    maxFullGroupNanos);
            } catch (Throwable error) {
                failure = error;
            } finally {
                long cleanupStarted = System.nanoTime();
                if (sink != null && !sink.isClosed()) {
                    try { sink.closeAfterFailure(); }
                    catch (Throwable closeError) { failure = appendFailure(failure, closeError); }
                }
                boolean ownersReleased = false;
                boolean contextRestored = false;
                try {
                    releaseRouteOwners();
                    ownersReleased = true;
                } catch (Throwable releaseError) {
                    failure = appendFailure(failure, releaseError);
                }
                if (nativeContext != null) {
                    try {
                        nativeContext.close(electricalCandidate);
                        contextRestored = true;
                    } catch (Throwable closeError) {
                        failure = appendFailure(failure, closeError);
                    }
                }
                electricalCandidate = null;
                tinyPlan = null;
                authoredPlan = null;
                board = null;
                long armCleanup = elapsed(cleanupStarted, System.nanoTime());
                if (result != null) {
                    result.cleanupNanos = armCleanup;
                    result.ownersReleased = ownersReleased;
                    result.contextRestored = contextRestored;
                }
                if (!ownersReleased || !contextRestored)
                    failure = appendFailure(failure,
                        new IllegalStateException("Generator owners or native context were not released"));
            }
            if (failure != null) rethrow(failure);
            if (result == null || !result.ownersReleased || !result.contextRestored)
                throw new IllegalStateException("Arm did not close its data and native context");
            return result;
        }

        private void executeData() throws Exception {
            GroupLedger setup = newGroup("SETUP", operationStarted);
            currentGroup = setup;
            sink = new JsonlSink(study.outputDirectory.resolve("op-" + operation + ".jsonl"),
                study.receiptBytes);
            prepareCandidateAndContext();
            byte[] boardIdentity = utf8(boardIdentityPreimage(board, planCanonical()));
            if (boardIdentity.length < 1 || boardIdentity.length > PREFIX_LIMIT_BYTES)
                throw new IOException("Board identity preimage is outside the 1 MiB bound");
            candidateHeader.boardSha256 = sha256(boardIdentity);
            addHashInput(setup, boardIdentity.length);
            createGeneratorAndSession();
            appendRecord(setup, headerJson());
            finishGroup(setup);
            currentGroup = null;

            boolean requestComplete = false;
            while (!requestComplete) {
                if (nextGroupOrdinal + 3 > GROUP_LIMIT)
                    throw new IOException("P07 request did not retire within the 640-group bound");
                GroupLedger advance = newGroup("ADVANCE", System.nanoTime());
                currentGroup = advance;
                advance.generatorAdvances = 1;
                Object routeBefore = readField(ownerSession, "mediumTwoLayerRoute");
                if (routeBefore != null) advance.routeForSlice = routeBefore;
                boolean complete = ownerSession.advance();
                if (complete)
                    throw new IllegalStateException("Actual generator completed before one request-zero boundary");
                Object routeForSlice = advance.routeForSlice;
                if (routeForSlice == null) routeForSlice = routeBefore;
                if (routeForSlice != null) {
                    if (!(routeForSlice instanceof PcbLayerRoutingPrototype.Session))
                        throw new IllegalStateException("Actual route owner has an unexpected type");
                    PcbLayerRoutingPrototype.Session route =
                        (PcbLayerRoutingPrototype.Session)routeForSlice;
                    advance.p07Slices = 1;
                    advance.p07Steps = route.lastWork();
                    if (advance.p07Steps < 0 || advance.p07Steps > STEP_LIMIT)
                        throw new IOException("Actual P07 slice exceeded 32768 owner steps");
                    if (advance.observerChecks < 1)
                        throw new IllegalStateException("Measured P07 slice omitted its owner observer check");
                    measuredSlices++;
                    ownerSteps += advance.p07Steps;
                    lastActualRoute = routeForSlice;
                } else {
                    advance.p07Slices = 0;
                    advance.p07Steps = null;
                }
                generatorAdvances++;
                requestComplete = isRequestZeroComplete();
                if (requestComplete) request = observeCompletedRequest();
                if (operation == 4) {
                    pollPublisherCapture(advance, true);
                }
                finishGroup(advance);
                currentGroup = null;
                advance.routeForSlice = null;
                if (operation == 4 && publisherCapture != null &&
                        publisherCapture.orderingResult != null &&
                        !publisherCapture.sourceReferencesReleased)
                    writePublisherCapture();
                if (operation == 4 && requestComplete)
                    closePublisherCaptureAfterRequest(advance);
            }

            if (capturedContext == null)
                throw new IllegalStateException("Actual route template was not captured at its first owner check");
            GroupLedger physical = newGroup("PHYSICAL", System.nanoTime());
            currentGroup = physical;
            String physicalJson = "\"connectivity\":\"NOT_RUN\",\"clearanceKeepout\":\"NOT_RUN\"," +
                "\"layeredRaster\":\"NOT_RUN\",\"access\":\"NOT_RUN\",\"functional\":\"NOT_RUN\"";
            String winnerJson = "null";
            Object winnerValue = readField(ownerSession, "mediumTwoLayerLayout");
            if (request.receipt.finalizedOrderings > 0) {
                if (!(winnerValue instanceof PcbBoardLayout))
                    throw new IllegalStateException("Finalized P07 request lost the actual medium winner");
                PcbBoardLayout winner = (PcbBoardLayout)winnerValue;
                int winnerAttempt = intField(ownerSession, "mediumTwoLayerPlacementAttempt");
                double quality = doubleField(ownerSession, "mediumTwoLayerQuality");
                if (winnerAttempt != request.receipt.attempt || request.receipt.selectedOrdering < 0 ||
                        Double.isNaN(quality) || Double.isInfinite(quality))
                    throw new IllegalStateException("Actual winner disagrees with its request receipt");
                String fingerprint = winner.geometryFingerprint();
                byte[] geometry = utf8(fingerprint);
                if (geometry.length < 1 || geometry.length > PREFIX_LIMIT_BYTES)
                    throw new IOException("Winner geometry preimage is outside the 1 MiB bound");
                addHashInput(physical, geometry.length);
                String winnerSha = sha256(geometry);
                verifyWinnerPhysical(winner);
                physicalJson = "\"connectivity\":\"PASS\",\"clearanceKeepout\":\"PASS\"," +
                    "\"layeredRaster\":\"PASS\",\"access\":\"NOT_RUN\",\"functional\":\"NOT_RUN\"";
                JsonObject value = new JsonObject();
                value.string("geometrySha256", winnerSha);
                value.number("geometryBytes", geometry.length);
                value.number("attempt", winnerAttempt);
                value.number("ordering", request.receipt.selectedOrdering);
                value.string("quality", Double.toString(quality));
                winnerJson = value.finish();
            } else if (winnerValue != null || request.receipt.selectedOrdering != -1) {
                throw new IllegalStateException("Rejected request retained a false winner");
            }
            finishGroup(physical);
            currentGroup = null;

            GroupLedger report = newGroup("REPORT", System.nanoTime());
            currentGroup = report;
            appendRecord(report, resultJson(report, physicalJson, winnerJson));
            finishReportGroupAndFooter(report);
            currentGroup = null;
        }

        private void prepareCandidateAndContext() throws Exception {
            nativeContext = NativeContextFacade.create();
            nativeContext.activate();
            if (study.mode == Mode.AUTHORED) {
                authoredPlan = Rb100Plan.create(AUTHORED_ROOT,
                    Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING);
                electricalCandidate = Rb100ElectricalPilot.construct(authoredPlan);
                board = electricalCandidate.board();
                ProceduralPcbLayout.declare(board);
                candidateHeader = CandidateHeader.authored(authoredPlan, board);
            } else {
                tinyPlan = Rb56Plan.resolve(TINY_ROOT, TINY_PLACEMENT_VERSION);
                if (tinyPlan.seed != TINY_ROOT ||
                        tinyPlan.placementInterpretationVersion != TINY_PLACEMENT_VERSION ||
                        tinyPlan.layoutSeed != TINY_PLACEMENT || tinyPlan.routingSeed != TINY_ROUTING)
                    throw new IllegalStateException("Saved structural RB56 root-42 candidate identity changed");
                board = tinyPlan.board();
                candidateHeader = CandidateHeader.tiny(tinyPlan, board);
            }
            board.validate();
            PcbPlacementConstraints constraints = board.getPlacementConstraints();
            if (!MediumBoardPhysicalPolicy.selected(constraints) || !constraints.requiresTwoLayerRouting ||
                    constraints.getTraceWidthProfile() != PcbTraceWidthProfile.ROLE_WIDTHS ||
                    SeededPcbLayoutGenerator.CURRENT_VERSION != 26 ||
                    !DEPENDENCY_EPOCH.equals(GenerationDependencyContext.INTERPRETATION_EPOCH))
                throw new IllegalStateException("Candidate left the frozen layout26/dependency-v28 ROLE_WIDTHS contract");
            if (study.mode == Mode.AUTHORED &&
                    (candidateHeader.componentCount != 100 || candidateHeader.padCount != 246 ||
                        candidateHeader.rootSeed != AUTHORED_ROOT ||
                        candidateHeader.placementSeed != AUTHORED_PLACEMENT ||
                        candidateHeader.routingSeed != AUTHORED_ROUTING))
                throw new IllegalStateException("Authored candidate does not match the frozen root/seed/count identity");
            if (candidateHeader.padCount > PAD_LIMIT || candidateHeader.padCount < 2 ||
                    candidateHeader.netCount < 1 || candidateHeader.componentCount < 1)
                throw new IllegalStateException("Candidate census exceeds the frozen transport domain");
        }

        private String planCanonical() {
            return study.mode == Mode.AUTHORED ? authoredPlan.canonical() : tinyPlan.canonical();
        }

        private void createGeneratorAndSession() {
            final ArmRun self = this;
            SeededPcbLayoutGenerator.AttemptObserver observer =
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) { self.onAttemptObserverCheck(attempt); }
                };
            generator = new SeededPcbLayoutGenerator(
                StandardPcbFootprintProviders.createRegistry(), observer);
            if (operation == 0) {
                ownerSession = generator.begin(board, candidateHeader.placementSeed,
                    candidateHeader.routingSeed);
                return;
            }
            final PcbLayerRoutingPrototype.NetOrderDiagnostics diagnostics =
                new PcbLayerRoutingPrototype.NetOrderDiagnostics() {
                    public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                        self.onTerminalBranch(snapshot);
                    }
                    public void onNetOrderReady(int pass) {
                        self.onNetOrderReady(pass);
                    }
                };
            if (operation == 1) {
                SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration configuration =
                    new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(diagnostics);
                ownerSession = generator.beginWithP07RequestBoundary(board,
                    candidateHeader.placementSeed, candidateHeader.routingSeed, configuration);
            } else {
                PcbLayerRoutingPrototype.BranchOrderStrategy strategy =
                    PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST;
                PcbLayerRoutingPrototype.NetOrderStrategy netOrderStrategy = operation == 2 ?
                    PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL :
                    PcbLayerRoutingPrototype.NetOrderStrategy.SECOND_ORDER_MINIMUM_PAIR;
                SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration configuration =
                    new SeededPcbLayoutGenerator.P07RequestBoundaryConfiguration(
                        strategy, netOrderStrategy, diagnostics);
                ownerSession = generator.beginWithP07RequestBoundary(board,
                    candidateHeader.placementSeed, candidateHeader.routingSeed, configuration);
            }
        }

        private void onAttemptObserverCheck(int attempt) {
            checkOperationDeadline();
            GroupLedger group = currentGroup;
            if (group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("Actual AttemptObserver ran outside an owner advance group");
            group.observerChecks++;
            if (ownerSession == null) return;
            Object route = readField(ownerSession, "mediumTwoLayerRoute");
            if (route == null) return;
            if (group.routeForSlice == null) group.routeForSlice = route;
            else if (group.routeForSlice != route)
                throw new IllegalStateException("One generator advance entered multiple P07 route owners");
            lastActualRoute = route;
            if (capturedContext == null) captureActualTemplate(route, group);
            observeDiagnosticClone(route, group);
            if (operation == 4) pollPublisherCapture(group, false);
        }

        private void onNetOrderReady(int pass) {
            checkOperationDeadline();
            if (pass < 0 || pass >= 3)
                throw new IllegalStateException("P07 NET_ORDER callback used an unsupported pass");
            if (operation == 0 || request != null)
                throw new IllegalStateException("P07 NET_ORDER callback escaped an observed live request");
            if (pass != 1) return;
            GroupLedger group = currentGroup;
            if (group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("P07 NET_ORDER callback ran outside an ADVANCE group");
            if (ownerSession == null)
                throw new IllegalStateException("P07 NET_ORDER callback lost its generator owner");
            Object route = readField(ownerSession, "mediumTwoLayerRoute");
            if (!(route instanceof PcbLayerRoutingPrototype.Session))
                throw new IllegalStateException("P07 NET_ORDER callback lost its actual route owner");
            Object search = readField(route, "search");
            if (search == null || intField(search, "pass") != pass)
                throw new IllegalStateException("P07 NET_ORDER callback does not own the actual pass-1 Search");
            captureActualNetOrder(route, search, group);
        }

        private void observeDiagnosticClone(Object route, GroupLedger group) {
            if (route != validatedDiagnosticRoute) {
                boolean hasDiagnostics = readField(route, "diagnostics") != null;
                if (hasDiagnostics != (operation != 0))
                    throw new IllegalStateException("Actual P07 diagnostics presence disagrees with the arm");
                if (!boolField(route, "roleWidths") || intField(route, "maximumVias") != MAX_VIAS ||
                        board == null || board.getPlacementConstraints().getTraceWidthProfile() !=
                            PcbTraceWidthProfile.ROLE_WIDTHS)
                    throw new IllegalStateException("Actual P07 route profile or via cap changed");
                validatedDiagnosticRoute = route;
            }
            if (operation == 0) return;
            if (group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("Diagnostic clone observer ran outside an ADVANCE group");
            Object search = readField(route, "search");
            if (search == null) {
                if (branchTracking != null)
                    throw new IllegalStateException("Actual P07 Search disappeared before its branch terminal");
                justTerminalBranchKey = null;
                return;
            }
            Object branch = readField(search, "branch");
            if (branch == null) {
                if (branchTracking != null)
                    throw new IllegalStateException("Actual P07 branch disappeared without its terminal snapshot");
                justTerminalBranchKey = null;
                return;
            }
            if (justTerminalBranchKey != null) {
                String currentKey = branchIdentityKey(search, branch);
                if (justTerminalBranchKey.equals(currentKey)) {
                    if (boolField(branch, "relaxedBoundStarted") &&
                            boolField(branch, "relaxedBoundComplete")) return;
                    throw new IllegalStateException("Terminal P07 branch changed after its snapshot");
                }
                justTerminalBranchKey = null;
            }
            boolean started = boolField(branch, "relaxedBoundStarted");
            if (!started) {
                if (branchTracking != null)
                    throw new IllegalStateException("Tracked P07 branch lost its started relaxed bound");
                return;
            }
            boolean complete = boolField(branch, "relaxedBoundComplete");
            if (branchTracking == null) {
                if (complete)
                    throw new IllegalStateException("Completed P07 bound arrived without observed clone transitions");
                if (intField(search, "maximumVias") != MAX_VIAS)
                    throw new IllegalStateException("Actual P07 Search via cap changed");
                branchTracking = makeBranchTracking(search, branch);
            } else if (branchTracking.branch != branch) {
                throw new IllegalStateException("Actual P07 branch changed before its terminal snapshot");
            }
            Object facesValue = readField(branch, "relaxedPristineFaces");
            if (complete) {
                if (branchTracking.transitionMask != 3 || facesValue != null)
                    throw new IllegalStateException("Completed P07 bound did not expose both clone transitions and release its slots");
                branchTracking.releasePristineRouters();
                return;
            }
            if (!(facesValue instanceof Object[]))
                throw new IllegalStateException("Active P07 diagnostic bound omitted its actual pristine face slots");
            Object[] faces = (Object[])facesValue;
            if (faces.length != 2)
                throw new IllegalStateException("Active P07 diagnostic bound has an unexpected face count");
            for (int face = 0; face < faces.length; face++) {
                int bit = 1 << face;
                Object pristine = faces[face];
                if (pristine == null) {
                    if ((branchTracking.transitionMask & bit) != 0)
                        throw new IllegalStateException("Actual P07 pristine face slot returned to null before completion");
                    branchTracking.nullObservedMask |= bit;
                    continue;
                }
                if ((branchTracking.transitionMask & bit) != 0) {
                    if (branchTracking.pristineRouters[face] != pristine)
                        throw new IllegalStateException("Actual P07 pristine face slot was reassigned");
                    continue;
                }
                if ((branchTracking.nullObservedMask & bit) == 0)
                    throw new IllegalStateException("Actual P07 clone assignment lacked an observed null slot");
                branchTracking.pristineRouters[face] = pristine;
                branchTracking.transitionMask |= bit;
                if (branchTracking.prefixViaCount < MAX_VIAS)
                    appendDiagnosticClone(group, branchTracking, face);
            }
        }
        private String branchIdentityKey(Object search, Object branch) {
            return intField(search, "pass") + "\t" + requiredStringField(branch, "net") +
                "\t" + padIdField(branch, "start") + "\t" + padIdField(branch, "root") +
                "\t" + prefixViaCount(branch);
        }

        private BranchTracking makeBranchTracking(Object search, Object branch) {
            int pass = intField(search, "pass");
            String netId = requiredStringField(branch, "net");
            String startPadId = padIdField(branch, "start");
            String rootPadId = padIdField(branch, "root");
            int prefixViaCount = prefixViaCount(branch);
            return new BranchTracking(branch, pass, netId, startPadId, rootPadId, prefixViaCount);
        }

        private void verifyBranchTracking(BranchTracking tracked, Object search, Object branch) {
            if (tracked.branch != branch || tracked.pass != intField(search, "pass") ||
                    !tracked.netId.equals(requiredStringField(branch, "net")) ||
                    !tracked.startPadId.equals(padIdField(branch, "start")) ||
                    !tracked.rootPadId.equals(padIdField(branch, "root")) ||
                    tracked.prefixViaCount != prefixViaCount(branch))
                throw new IllegalStateException("Actual P07 branch identity or stable key changed during observation");
        }

        private String padIdField(Object branch, String field) {
            Object value = readField(branch, field);
            if (!(value instanceof PcbPadPlacement))
                throw new IllegalStateException("Actual P07 branch omitted its " + field + " pad");
            String id = ((PcbPadPlacement)value).getPadId();
            if (!token(id)) throw new IllegalStateException("Actual P07 branch pad ID is outside the wire domain");
            return id;
        }

        private int prefixViaCount(Object branch) {
            Object holesValue = readField(branch, "holes");
            if (!(holesValue instanceof Object[]))
                throw new IllegalStateException("Actual P07 branch omitted its immutable prefix holes");
            int count = ((Object[])holesValue).length;
            if (count < 0 || count > MAX_VIAS)
                throw new IllegalStateException("Actual P07 prefix via count exceeds its source cap");
            return count;
        }

        private String requiredStringField(Object target, String name) {
            Object value = readField(target, name);
            if (!(value instanceof String) || !token((String)value))
                throw new IllegalStateException("Actual P07 branch token is outside the wire domain: " + name);
            return (String)value;
        }

        private void appendDiagnosticClone(GroupLedger group, BranchTracking tracked, int face) {
            if (operation < 1 || operation > 4 || group == null ||
                    !"ADVANCE".equals(group.phase) || face < 0 || face > 1)
                throw new IllegalStateException("Diagnostic clone event has invalid arm, group, or face");
            long eventLimit = 6L * (candidateHeader.padCount - candidateHeader.netCount);
            if (diagnosticCloneSteps >= eventLimit || group.diagnosticCloneSteps >= STEP_LIMIT)
                throw new IllegalStateException("Diagnostic clone events exceeded their frozen cap");
            if (face == 1 && (tracked.eventMask & 1) == 0)
                throw new IllegalStateException("P07 face one clone preceded face zero");
            String key = tracked.pass + "\t" + tracked.netId + "\t" + tracked.startPadId +
                "\t" + tracked.rootPadId + "\t" + face;
            if (diagnosticEventKeys.contains(key))
                throw new IllegalStateException("Actual diagnostic clone event key was duplicated");
            JsonObject row = new JsonObject();
            row.string("record", "DIAGNOSTIC_CLONE");
            row.number("operation", operation);
            row.number("ordinal", diagnosticCloneSteps);
            row.number("group", group.ordinal);
            row.number("pass", tracked.pass);
            row.string("netId", tracked.netId);
            row.string("startPadId", tracked.startPadId);
            row.string("rootPadId", tracked.rootPadId);
            row.number("faceIndex", face);
            row.number("prefixViaCount", tracked.prefixViaCount);
            row.number("ownerSteps", 1);
            appendCallbackRecord(group, row.finish());
            diagnosticEventKeys.add(key);
            tracked.eventMask |= 1 << face;
            diagnosticCloneSteps++;
            group.diagnosticCloneSteps++;
        }

        private void validateTerminalClonePair(Object search,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            Object branch = readField(search, "branch");
            if (branch == null)
                throw new IllegalStateException("Terminal P07 snapshot lost its live branch");
            int pass = intField(search, "pass");
            String netId = requiredStringField(branch, "net");
            String startPadId = padIdField(branch, "start");
            String rootPadId = padIdField(branch, "root");
            int prefixViaCount = prefixViaCount(branch);
            if (intField(snapshot, "pass") != pass ||
                    !requiredStringField(snapshot, "netId").equals(netId) ||
                    !requiredStringField(snapshot, "startPadId").equals(startPadId) ||
                    !requiredStringField(snapshot, "rootPadId").equals(rootPadId) ||
                    intField(snapshot, "prefixViaCount") != prefixViaCount)
                throw new IllegalStateException("Terminal P07 snapshot disagrees with its live branch key");
            String[] proofFlags = {"relaxedBoundStarted", "relaxedBoundComplete",
                "relaxedBoundObservationChecked", "relaxedBoundLiveLayoutStable",
                "relaxedBoundRouterStateStable", "relaxedBoundRouterEffectsStable"};
            for (String name : proofFlags)
                if (boolField(snapshot, name) != boolField(branch, name))
                    throw new IllegalStateException("Terminal snapshot differs from live branch proof flag " + name);
            boolean started = boolField(snapshot, "relaxedBoundStarted");
            boolean complete = boolField(snapshot, "relaxedBoundComplete");
            if (!started) {
                if (branchTracking != null || readField(branch, "relaxedPristineFaces") != null)
                    throw new IllegalStateException("Unstarted P07 branch retained diagnostic clone state");
                justTerminalBranchKey = null;
                return;
            }
            if (branchTracking == null || branchTracking.branch != branch)
                throw new IllegalStateException("Started P07 bound has no matching observed branch identity");
            BranchTracking tracked = branchTracking;
            verifyBranchTracking(tracked, search, branch);
            if (!complete)
                throw new IllegalStateException("Started but incomplete relaxed bound is unsupported in v3");
            if (tracked.transitionMask != 3)
                throw new IllegalStateException("Completed observed bound omitted one pristine clone transition");
            if (readField(branch, "relaxedPristineFaces") != null)
                throw new IllegalStateException("Completed P07 bound retained pristine face slots");
            if (tracked.prefixViaCount < MAX_VIAS) {
                if (!boolField(snapshot, "relaxedBoundObservationChecked") ||
                        !boolField(snapshot, "relaxedBoundLiveLayoutStable") ||
                        !boolField(snapshot, "relaxedBoundRouterStateStable") ||
                        !boolField(snapshot, "relaxedBoundRouterEffectsStable") || tracked.eventMask != 3)
                    throw new IllegalStateException("Nonzero-capacity bound lacks complete clone-pair proof");
            } else if (tracked.eventMask != 0) {
                throw new IllegalStateException("Zero-capacity common clone was classified as diagnostic-only");
            }
            justTerminalBranchKey = tracked.identityKey();
            tracked.releasePristineRouters();
            branchTracking = null;
        }
        private void captureActualTemplate(Object route, GroupLedger group) {
            if (capturedContext != null)
                throw new IllegalStateException("Actual route template was censused more than once");
            Object templateValue = readField(route, "template");
            if (!(templateValue instanceof PcbBoardLayout))
                throw new IllegalStateException("Initial P07 owner check omitted its actual template");
            PcbBoardLayout template = (PcbBoardLayout)templateValue;
            String pristine = template.geometryFingerprint();
            byte[] pristineBytes = utf8(pristine);
            if (pristineBytes.length < 1 || pristineBytes.length > PREFIX_LIMIT_BYTES)
                throw new IllegalStateException("Pristine geometry preimage is outside the 1 MiB bound");
            addHashInput(group, pristineBytes.length);
            String pristineSha = sha256(pristineBytes);
            List<PadRow> rows = censusActualTemplate(template, board);
            if (rows.size() != candidateHeader.padCount || rows.size() > PAD_LIMIT)
                throw new IllegalStateException("Actual P07 template pad census is incomplete");
            StringBuilder census = new StringBuilder();
            for (PadRow row : rows)
                census.append(row.padId).append('\t').append(row.netId).append('\t')
                    .append(row.componentId).append('\t').append(row.x).append('\t')
                    .append(row.y).append('\t').append(row.role).append('\n');
            byte[] censusBytes = utf8(census.toString());
            addHashInput(group, censusBytes.length);
            String censusSha = sha256(censusBytes);
            padNetIds.clear();
            for (PadRow row : rows) {
                if (!token(row.netId))
                    throw new IllegalStateException("Actual PAD census contains an invalid net identity");
                padNetIds.add(row.netId);
            }
            if (padNetIds.size() != candidateHeader.netCount)
                throw new IllegalStateException("Actual PAD census does not cover every candidate net");
            int requestIndex = intField(ownerSession, "mediumTwoLayerAttempts");
            Object selectedValue = readField(ownerSession, "mediumTwoLayerCandidate");
            if (!(selectedValue instanceof MediumBoardPhysicalPolicy.PlacementCandidate))
                throw new IllegalStateException("Initial P07 owner check omitted the actual selected placement");
            int selectedAttempt = ((MediumBoardPhysicalPolicy.PlacementCandidate)selectedValue).attempt;
            int[] eligibleAttempts = eligibleRouteAttempts(ownerSession);
            if (requestIndex != 0 || eligibleAttempts.length < 1 || eligibleAttempts.length > 6 ||
                    eligibleAttempts[0] != selectedAttempt)
                throw new IllegalStateException("Initial live route did not identify actual P07 request zero");
            capturedContext = new CapturedContext(requestIndex, selectedAttempt,
                eligibleAttempts, pristine, pristineSha, pristineBytes.length,
                censusSha);
            for (PadRow row : rows) {
                appendCallbackRecord(group, row.toJson(group.ordinal));
                group.censusRows++;
                padCount++;
            }
        }

        private void captureActualNetOrder(Object route, Object search, GroupLedger group) {
            if (operation == 0)
                throw new IllegalStateException("Plain operation received a P07 NET_ORDER callback");
            if (request != null)
                throw new IllegalStateException("P07 NET_ORDER observation arrived after the request result");
            if (group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("P07 NET_ORDER observation ran outside an ADVANCE group");
            if (search == null || intField(search, "pass") != 1)
                throw new IllegalStateException("P07 NET_ORDER callback lost its actual pass-1 Search");
            if (netOrderCaptured || netOrderRecordCount != 0)
                throw new IllegalStateException("Actual pass-1 NET_ORDER callback was duplicated");
            Object netsValue = readField(search, "nets");
            if (netsValue == null)
                throw new IllegalStateException("P07 NET_ORDER callback arrived before the full net vector was installed");
            if (!(netsValue instanceof Vector))
                throw new IllegalStateException("Actual pass-1 Search.nets has an unexpected type");
            if (readField(search, "netOrderPreparation") != null ||
                    readField(search, "currentNet") != null || intField(search, "netIndex") != 0 ||
                    readField(search, "branch") != null || readField(search, "publisher") != null)
                throw new IllegalStateException("Actual pass-1 net order was partial or observed after routing began");
            if (capturedContext == null || padCount != candidateHeader.padCount ||
                    padNetIds.size() != candidateHeader.netCount)
                throw new IllegalStateException("Actual pass-1 net order preceded its complete PAD census");

            Object actualBranchStrategy = readField(route, "branchOrderStrategy");
            Object actualNetOrderStrategy = readField(route, "netOrderStrategy");
            if (actualBranchStrategy == null || actualNetOrderStrategy == null ||
                    !ARM_STRATEGIES[operation].equals(actualBranchStrategy.toString()) ||
                    !ARM_NET_ORDER_STRATEGIES[operation].equals(actualNetOrderStrategy.toString()))
                throw new IllegalStateException("Actual P07 owner strategies disagree with the declared arm");

            int completedOrderings = intField(route, "completedOrderings");
            int yieldedOrderings = intField(route, "yieldedOrderings");
            if (completedOrderings != 1 || (yieldedOrderings != 0 && yieldedOrderings != 1))
                throw new IllegalStateException("Actual pass-1 ordering counters are outside the frozen boundary");
            Object promotedValue = readField(search, "promotedNet");
            String promotedNet;
            if (promotedValue == null) promotedNet = null;
            else if (promotedValue instanceof String) promotedNet = (String)promotedValue;
            else throw new IllegalStateException("Actual pass-1 promotion has an unexpected type");
            if (yieldedOrderings == 1) {
                if (promotedNet != null || !pass0AllFound ||
                        pass0TerminalCount != candidateHeader.padCount - candidateHeader.netCount)
                    throw new IllegalStateException("Yielded pass 0 lacks complete all-FOUND terminal authority");
            } else {
                if (pass0TerminalCount < 1 || !pass0EarlierAllFound || pass0TailNet == null ||
                        !("NO_PATH".equals(pass0TailReason) ||
                            "BRANCH_SEARCH_LIMIT".equals(pass0TailReason)) ||
                        promotedNet == null || !promotedNet.equals(pass0TailNet))
                    throw new IllegalStateException("Non-yielded pass 0 lacks closed failed-net promotion authority");
            }
            if (promotedNet != null && (!token(promotedNet) || !padNetIds.contains(promotedNet)))
                throw new IllegalStateException("Actual pass-1 promotion is outside the PAD census");

            Object layoutValue = readField(search, "layout");
            if (!(layoutValue instanceof PcbBoardLayout))
                throw new IllegalStateException("Actual pass-1 Search omitted its live layout");
            String prefixFingerprint = ((PcbBoardLayout)layoutValue).geometryFingerprint();
            byte[] prefix = utf8(prefixFingerprint);
            if (prefix.length < 1 || prefix.length > PREFIX_LIMIT_BYTES)
                throw new IllegalStateException("Actual pass-1 ordering prefix exceeded the 1 MiB bound");
            String prefixSha = sha256(prefix);
            if (!prefixFingerprint.equals(capturedContext.pristineGeometry) ||
                    prefix.length != capturedContext.pristineBytes ||
                    !prefixSha.equals(capturedContext.pristineSha256))
                throw new IllegalStateException("Actual pass-1 Search.layout differs from its pristine request placement");

            Vector<?> actualNets = (Vector<?>)netsValue;
            int netCount = actualNets.size();
            if (netCount != candidateHeader.netCount || netCount != padNetIds.size())
                throw new IllegalStateException("Actual pass-1 net vector does not match the PAD census count");
            Set<String> seenNets = new HashSet<String>();
            StringBuilder netIds = new StringBuilder("[");
            for (int index = 0; index < netCount; index++) {
                Object value = actualNets.get(index);
                if (!(value instanceof String))
                    throw new IllegalStateException("Actual pass-1 net vector contains a non-string identity");
                String netId = (String)value;
                if (!token(netId) || !padNetIds.contains(netId) || !seenNets.add(netId))
                    throw new IllegalStateException("Actual pass-1 net vector has an invalid, unknown, or duplicate identity");
                if (index != 0) netIds.append(',');
                netIds.append(quote(netId));
            }
            netIds.append(']');
            if (seenNets.size() != padNetIds.size())
                throw new IllegalStateException("Actual pass-1 net vector omitted a PAD census net");

            JsonObject row = new JsonObject();
            row.string("record", "NET_ORDER");
            row.number("operation", operation);
            row.number("group", group.ordinal);
            row.number("pass", 1);
            row.string("strategy", ARM_NET_ORDER_STRATEGIES[operation]);
            if (promotedNet == null) row.raw("promotedNet", "null");
            else row.string("promotedNet", promotedNet);
            row.number("completedOrderings", completedOrderings);
            row.number("yieldedOrderings", yieldedOrderings);
            row.string("prefixSha256", prefixSha);
            row.number("prefixBytes", prefix.length);
            row.raw("netIds", netIds.toString());
            addHashInput(group, prefix.length);
            appendCallbackRecord(group, row.finish());
            group.netOrderRows++;
            netOrderRecordCount++;
            netOrderCaptured = true;
            capturedNetOrderSearch = search;
        }
        @SuppressWarnings("unchecked")
        private int[] eligibleRouteAttempts(Object owner) {
            Object rankedValue = readField(owner, "mediumPlacements");
            Object eligibleValue = readField(owner, "mediumP07EligiblePlacements");
            if (!(rankedValue instanceof Vector))
                throw new IllegalStateException("Actual generator omitted ranked placement owners");
            if (eligibleValue != null && !(eligibleValue instanceof Vector))
                throw new IllegalStateException("Actual generator P07 eligibility owner has an unexpected type");
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ranked =
                (Vector<MediumBoardPhysicalPolicy.PlacementCandidate>)rankedValue;
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> eligible =
                (Vector<MediumBoardPhysicalPolicy.PlacementCandidate>)eligibleValue;
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> routeCandidates =
                SeededPcbLayoutGenerator.mediumRouteCandidates(ranked, eligible, true,
                    board.getPlacementConstraints().requiresTwoLayerRouting);
            int[] attempts = new int[routeCandidates.size()];
            for (int index = 0; index < routeCandidates.size(); index++)
                attempts[index] = routeCandidates.get(index).attempt;
            return attempts;
        }

        private List<PadRow> censusActualTemplate(PcbBoardLayout template,
                TroubleshootBoard logicalBoard) {
            Vector<PcbPadPlacement> placements = template.getPads();
            if (placements == null || placements.size() > PAD_LIMIT)
                throw new IllegalStateException("Actual P07 template has no bounded pad census");
            List<PcbPadPlacement> sorted = new ArrayList<PcbPadPlacement>(placements);
            Collections.sort(sorted, new Comparator<PcbPadPlacement>() {
                public int compare(PcbPadPlacement left, PcbPadPlacement right) {
                    return left.getPadId().compareTo(right.getPadId());
                }
            });
            List<PadRow> result = new ArrayList<PadRow>(sorted.size());
            Set<String> seen = new HashSet<String>();
            for (PcbPadPlacement placement : sorted) {
                String padId = placement.getPadId();
                if (!token(padId) || !seen.add(padId))
                    throw new IllegalStateException("Actual template pad IDs are invalid or duplicated");
                BoardPad logicalPad = logicalBoard.getPad(padId);
                if (logicalPad == null)
                    throw new IllegalStateException("Actual template pad has no logical board identity");
                BoardNet net = logicalBoard.getNet(logicalPad.getNetId());
                if (net == null || net.getRoutingRole() == null)
                    throw new IllegalStateException("Actual template pad has no authoritative routing role");
                int x = placement.getX();
                int y = placement.getY();
                if (x < -1000000 || x > 1000000 || y < -1000000 || y > 1000000)
                    throw new IllegalStateException("Actual template pad is outside the wire coordinate domain");
                result.add(new PadRow(padId, logicalPad.getNetId(), logicalPad.getComponentId(),
                    x, y, net.getRoutingRole().name()));
            }
            return result;
        }

        private void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            checkOperationDeadline();
            GroupLedger group = currentGroup;
            if (group == null || !"ADVANCE".equals(group.phase) || operation == 0 ||
                    capturedContext == null)
                throw new IllegalStateException("Branch callback was outside its live observed owner group");
            Object route = readField(ownerSession, "mediumTwoLayerRoute");
            if (route == null)
                throw new IllegalStateException("Terminal branch callback lost the live outer route");
            Object search = readField(route, "search");
            if (search == null)
                throw new IllegalStateException("Terminal branch callback lost its live Search");
            Object layoutValue = readField(search, "layout");
            if (!(layoutValue instanceof PcbBoardLayout))
                throw new IllegalStateException("Terminal branch callback lost Search.layout");
            int pass = intField(snapshot, "pass");
            Object netValue = readField(snapshot, "netId");
            Object reasonValue = readField(snapshot, "terminalReason");
            if (pass < 0 || pass >= 3 || !(netValue instanceof String) ||
                    !(reasonValue instanceof String))
                throw new IllegalStateException("Actual terminal branch omitted its bounded pass identity");
            String terminalNet = (String)netValue;
            String terminalReason = (String)reasonValue;
            if (!token(terminalNet) || !padNetIds.contains(terminalNet) ||
                    !("FOUND".equals(terminalReason) || "NO_PATH".equals(terminalReason) ||
                        "TOTAL_SEARCH_LIMIT".equals(terminalReason) ||
                        "BRANCH_SEARCH_LIMIT".equals(terminalReason)))
                throw new IllegalStateException("Actual terminal branch escaped the census or reason domain");
            if (pass == 0) {
                if (pass0TerminalCount > 0 && !"FOUND".equals(pass0TailReason))
                    pass0EarlierAllFound = false;
                pass0TerminalCount++;
                pass0TailNet = terminalNet;
                pass0TailReason = terminalReason;
                if (!"FOUND".equals(terminalReason)) pass0AllFound = false;
            } else if (pass == 1) {
                if (!netOrderCaptured || search != capturedNetOrderSearch)
                    throw new IllegalStateException("Pass-1 branch advanced without its actual NET_ORDER row");
                pass1TerminalCount++;
            }
            validateTerminalClonePair(search, snapshot);
            PcbBoardLayout liveLayout = (PcbBoardLayout)layoutValue;
            String prefixFingerprint = liveLayout.geometryFingerprint();
            byte[] prefix = utf8(prefixFingerprint);
            if (prefix.length < 1 || prefix.length > PREFIX_LIMIT_BYTES)
                throw new IllegalStateException("Live branch-prefix geometry exceeded the 1 MiB bound");
            addHashInput(group, prefix.length);
            String prefixSha = sha256(prefix);
            if (branchCount >= 3 * (candidateHeader.padCount - candidateHeader.netCount))
                throw new IllegalStateException("Actual branch callbacks exceeded the frozen per-arm cap");
            JsonObject branch = new JsonObject();
            branch.string("record", "BRANCH");
            branch.number("operation", operation);
            branch.number("ordinal", branchCount);
            branch.number("group", group.ordinal);
            branch.string("prefixSha256", prefixSha);
            branch.number("prefixBytes", prefix.length);
            branch.raw("snapshot", snapshotJson(snapshot));
            appendCallbackRecord(group, branch.finish());
            if (operation == 4 && pass == 1 && "FOUND".equals(terminalReason))
                captureSelectedFoundBranch(route, search, liveLayout, snapshot,
                    prefixFingerprint, prefix, prefixSha, branchCount, group);
            branchCount++;
            group.callbacks++;
        }

        private void captureSelectedFoundBranch(Object route, Object search,
                PcbBoardLayout layout, PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                String prefixFingerprint, byte[] prefixBytes, String prefixSha, int branchRecordOrdinal,
                GroupLedger group) {
            String netId = stringField(snapshot, "netId");
            String startPadId = stringField(snapshot, "startPadId");
            String rootPadId = stringField(snapshot, "rootPadId");
            boolean authoredTarget = study.mode == Mode.AUTHORED &&
                "OUT_F".equals(netId) && "RSH_F.1".equals(startPadId) &&
                "CSTORE_F.2".equals(rootPadId);
            if (publisherCapture != null) {
                if (study.mode == Mode.TINY_CANARY) return;
                if (authoredTarget)
                    throw new IllegalStateException("Authored P07 publisher target FOUND more than once");
                return;
            }
            if (study.mode == Mode.AUTHORED && !authoredTarget) return;

            if (ownerSession == null || readField(ownerSession, "mediumTwoLayerRoute") != route ||
                    readField(search, "owner") != route)
                throw new IllegalStateException("P07 FOUND did not belong to the active request owner's actual route");

            Object branch = readField(search, "branch");
            Object found = branch == null ? null : readField(branch, "found");
            Object terminal = branch == null ? null : readField(branch, "foundTerminal");
            Object branchNet = branch == null ? null : readField(branch, "net");
            Object start = branch == null ? null : readField(branch, "start");
            Object root = branch == null ? null : readField(branch, "root");
            if (branch == null || found == null || !(branchNet instanceof String) ||
                    !netId.equals(branchNet) || start == null || root == null ||
                    !startPadId.equals(padId(start)) || !rootPadId.equals(padId(root)) ||
                    branch != readField(search, "branch"))
                throw new IllegalStateException("P07 FOUND callback did not retain its actual BranchSearch and Node");
            verifyImmutableNodeType(found);
            Object reachedValue = readField(search, "reachedPadIds");
            Object netsValue = readField(search, "nets");
            if (!(reachedValue instanceof Vector) || !(netsValue instanceof Vector))
                throw new IllegalStateException("P07 FOUND callback omitted its actual reached/net vectors");
            Vector<?> reached = (Vector<?>)reachedValue;
            Vector<?> nets = (Vector<?>)netsValue;
            int netVectorPosition = nets.indexOf(netId);
            int cells = intField(search, "cells");
            int width = intField(search, "width");
            int height = intField(search, "height");
            int minX = intField(search, "minX");
            int minY = intField(search, "minY");
            int nodeCell = intField(found, "cell");
            int nodeLayer = intField(found, "layer");
            Object goalValue = readField(branch, "goal");
            if (!(goalValue instanceof boolean[]) || cells != width * height || cells < 1 ||
                    netVectorPosition < 0 || !netId.equals(nets.get(netVectorPosition)) ||
                    nodeCell < 0 || nodeCell >= cells || nodeLayer < 0 || nodeLayer > 1 ||
                    ((boolean[])goalValue).length != cells * 2 ||
                    !((boolean[])goalValue)[nodeLayer * cells + nodeCell])
                throw new IllegalStateException("P07 FOUND Node is not an actual goal in its captured Search");

            String[] reachedPadIds = copyReachedPadIds(reached, netId);
            if (java.util.Arrays.binarySearch(reachedPadIds, startPadId) >= 0)
                throw new IllegalStateException("P07 FOUND start pad was already in the retained reached-pad vector");
            int copied = reachedPadIds.length * 2 + PUBLISHER_FOUND_METADATA_ENTRIES;
            group.diagnosticCopiedEntries += reachedPadIds.length * 2L;
            group.diagnosticCopiedEntries += PUBLISHER_FOUND_METADATA_ENTRIES;
            GeometryCapture before = copyGeometry(layout, group, prefixFingerprint, false);
            copied += before.copiedEntries;
            if (copied > PUBLISHER_SYNC_COPY_LIMIT)
                throw new IllegalStateException("Synchronous P07 Publisher snapshot exceeded its frozen entry cap");
            if (!prefixFingerprint.equals(before.sourceFingerprint))
                throw new IllegalStateException("P07 Publisher before-geometry differs from its BRANCH prefix");
            before.geometrySha256 = prefixSha;
            before.geometryBytes = prefixBytes.length;
            if (group.diagnosticCopiedEntries > PUBLISHER_SYNC_COPY_LIMIT)
                throw new IllegalStateException("Synchronous P07 Publisher snapshot exceeded its frozen entry cap");

            int startSegments = intField(search, "segments");
            int ordinal = branchRecordOrdinal;
            int searchBranchCount = intField(search, "branches");
            if (intField(branch, "branchOrdinal") != searchBranchCount)
                throw new IllegalStateException("P07 target branch ordinal differs from its source Search counter");
            int maximumVias = intField(search, "maximumVias");
            int primaryLayer = intField(search, "primary");
            int startX = intField(start, "x");
            int startY = intField(start, "y");
            int rootX = intField(root, "x");
            int rootY = intField(root, "y");
            BoardNet targetNet = board.getNet(netId);
            if (targetNet == null)
                throw new IllegalStateException("P07 FOUND net disappeared from its candidate board");
            int traceWidth = board.getPlacementConstraints().getTraceWidthProfile().widthFor(
                targetNet.getRoutingRole());
            int pitch = PcbLayerRoutingPrototype.GRID;
            publisherCapture = new PublisherCapture(
                publisherToken(ownerSession), publisherToken(route), publisherToken(search),
                publisherToken(branch), publisherToken(layout), publisherToken(found),
                terminal == null ? null : Integer.valueOf(publisherToken(terminal)),
                group.ordinal, nextPublisherSequence(group), 1, ordinal, netVectorPosition,
                netId, startPadId, rootPadId, reachedPadIds,
                terminal == null ? null : padId(terminal), group.ordinal,
                width, height, cells, minX, minY, pitch, primaryLayer, maximumVias,
                startX, startY, rootX, rootY, traceWidth,
                nodeLayer * cells + nodeCell, startSegments, true, reached, ownerSession,
                branch, search, route, layout, found, terminal, before, prefixSha,
                prefixBytes.length, searchBranchCount);
        }

        private void pollPublisherCapture(GroupLedger group, boolean afterParentAdvance) {
            if (operation != 4 || publisherCapture == null ||
                    publisherCapture.sourceReferencesReleased) return;
            if (group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("P07 Publisher capture poll escaped an ADVANCE group");
            PublisherCapture capture = publisherCapture;
            int sequence = nextPublisherSequence(group);
            Object liveRoute = ownerSession == null ? null : readField(ownerSession, "mediumTwoLayerRoute");
            if (liveRoute != null && liveRoute != capture.route)
                throw new IllegalStateException("P07 Publisher capture route changed before its ordering boundary");

            if (capture.publisher == null) {
                Object livePublisher = readField(capture.search, "publisher");
                if (livePublisher != null) {
                    verifyPublisherBinding(capture, livePublisher);
                    capture.publisher = livePublisher;
                    capture.publisherToken = publisherToken(livePublisher);
                    capture.publisherSeen = new CaptureCheckpoint(group.ordinal, sequence);
                    capture.ownerBindingVerified = true;
                    group.diagnosticCopiedEntries += 4;
                }
            }
            if (capture.publisher != null && capture.outcome == null)
                observePublisherTerminal(capture, group, sequence);

            if (capture.outcome != null && capture.routerStatus == null)
                observeOrderingBoundary(capture, group, sequence);

            if (afterParentAdvance && "READY".equals(capture.routerStatus) &&
                    capture.orderingResult == null)
                observeOrderingResult(capture, group, sequence);
            if (group.diagnosticCopiedEntries > PUBLISHER_SYNC_COPY_LIMIT)
                throw new IllegalStateException("P07 ADVANCE diagnostic snapshot exceeded its frozen entry cap");
        }

        private void observeOrderingBoundary(PublisherCapture capture, GroupLedger group,
                int sequence) {
            Object ready = readField(capture.route, "readyOrdering");
            if (ready != null) {
                int readyPass = intField(ready, "orderings") - 1;
                if (readyPass == capture.pass) {
                    Object readyLayout = readField(ready, "layout");
                    if (!(readyLayout instanceof PcbBoardLayout) || readyLayout != capture.layout)
                        throw new IllegalStateException("ROUTE_READY omitted the captured pass layout identity");
                    int finalized = intField(ownerSession, "mediumTwoLayerFinalizedOrderings");
                    if (finalized < 0 || finalized > 3)
                        throw new IllegalStateException("ROUTE_READY finalizer count is outside its source bound");
                    capture.routerStatus = "READY";
                    capture.routerReason = stringField(capture.route, "reason");
                    capture.readyLayoutToken = publisherToken(readyLayout);
                    capture.readyPass = readyPass;
                    capture.finalizedBefore = finalized;
                    capture.orderingReady = new CaptureCheckpoint(group.ordinal, sequence);
                    group.diagnosticCopiedEntries += 7;
                    return;
                }
            }
            Object currentSearch = readField(capture.route, "search");
            int currentPass = intField(capture.route, "pass");
            if (currentSearch != capture.search && currentPass > capture.pass) {
                capture.routerStatus = "ROUTER_REJECTED";
                capture.routerReason = stringField(capture.route, "reason");
                int finalized = intField(ownerSession, "mediumTwoLayerFinalizedOrderings");
                if (finalized < 0 || finalized > 3)
                    throw new IllegalStateException("Pre-ready P07 rejection count is outside its source bound");
                capture.finalizedBefore = finalized;
                capture.finalizedAfter = finalized;
                capture.finalizerStatus = "NOT_REACHED";
                capture.lastFailureDetail = readNullableString(ownerSession,
                    "mediumTwoLayerLastFailureDetail");
                capture.orderingResult = new CaptureCheckpoint(group.ordinal, sequence);
                group.diagnosticCopiedEntries += 8;
            } else if (currentSearch != capture.search && boolField(capture.route, "complete")) {
                capture.routerStatus = "ROUTER_REJECTED";
                capture.routerReason = stringField(capture.route, "reason");
                int finalized = intField(ownerSession, "mediumTwoLayerFinalizedOrderings");
                if (finalized < 0 || finalized > 3)
                    throw new IllegalStateException("Completed pre-ready P07 count is outside its source bound");
                capture.finalizedBefore = finalized;
                capture.finalizedAfter = finalized;
                capture.finalizerStatus = "NOT_REACHED";
                capture.lastFailureDetail = readNullableString(ownerSession,
                    "mediumTwoLayerLastFailureDetail");
                capture.orderingResult = new CaptureCheckpoint(group.ordinal, sequence);
                group.diagnosticCopiedEntries += 8;
            }
        }

        private void observePublisherTerminal(PublisherCapture capture, GroupLedger group,
                int sequence) {
            Object livePublisher = readField(capture.search, "publisher");
            if (livePublisher != null && livePublisher != capture.publisher)
                throw new IllegalStateException("P07 target Search rebound to a successor Publisher");
            int stage = intField(capture.publisher, "stage");
            if (stage == 6) {
                Object reachedValue = capture.reachedVector;
                if (!(reachedValue instanceof Vector))
                    throw new IllegalStateException("P07 target lost its source reached-pad vector");
                Vector<?> reached = (Vector<?>)reachedValue;
                int afterSegments = intField(capture.search, "segments");
                int publisherSegments = intField(capture.publisher, "segments");
                boolean reachedStart = reached.contains(capture.startPadId);
                boolean publisherCleared = readField(capture.search, "publisher") == null;
                if (!publisherCleared || !reachedStart ||
                        afterSegments - capture.startSegments != publisherSegments)
                    return;
                verifyPublishedPublisher(capture, stage);
                capture.outcome = "PUBLISHED";
                capture.stage = stage;
                copyPublisherTerminalNumbers(capture, publisherSegments, group);
                group.diagnosticCopiedEntries += 5;
                capture.reason = null;
                capture.sourceHandoff = true;
                capture.startReachedAfter = true;
                String afterFingerprint = capture.layout.geometryFingerprint();
                capture.after = copyGeometry(capture.layout, group, afterFingerprint, true);
                capture.publisherTerminal = new CaptureCheckpoint(group.ordinal, sequence);
                group.diagnosticCopiedEntries += 2;
                verifyGeometryBoundary(capture);
                return;
            }
            if (stage == 1 && capture.publisher == readField(capture.search, "publisher")) {
                int qualityIndex = intField(capture.publisher, "qualityIndex");
                int reversedSize = vectorSize(readField(capture.publisher, "reversed"));
                if (qualityIndex != reversedSize) return;
                String expected = capture.netId + ":BRANCH_QUALITY_LIMIT";
                Object reasonValue = readField(capture.route, "reason");
                if (!(reasonValue instanceof String) || !expected.equals(reasonValue)) return;
                capture.outcome = "REJECTED";
                capture.stage = stage;
                copyPublisherTerminalNumbers(capture,
                    intField(capture.publisher, "segments"), group);
                group.diagnosticCopiedEntries += 5;
                capture.reason = (String)reasonValue;
                capture.sourceHandoff = false;
                capture.startReachedAfter = ((Vector<?>)capture.reachedVector).contains(capture.startPadId);
                String afterFingerprint = capture.layout.geometryFingerprint();
                capture.after = copyGeometry(capture.layout, group, afterFingerprint, true);
                capture.publisherTerminal = new CaptureCheckpoint(group.ordinal, sequence);
                group.diagnosticCopiedEntries += 2;
                verifyGeometryBoundary(capture);
            }
        }

        private void copyPublisherTerminalNumbers(PublisherCapture capture, int segments,
                GroupLedger group) {
            int qualityIndex = intField(capture.publisher, "qualityIndex");
            int reversedSize = vectorSize(readField(capture.publisher, "reversed"));
            int length = intField(capture.publisher, "length");
            int bends = intField(capture.publisher, "bends");
            int lastDirection = intField(capture.publisher, "lastDirection");
            if (qualityIndex < 0 || qualityIndex > PUBLISHER_PATH_NODE_LIMIT ||
                    reversedSize < 1 || reversedSize > PUBLISHER_PATH_NODE_LIMIT ||
                    length < 0 || length > PUBLISHER_PATH_NODE_LIMIT * 10 ||
                    bends < 0 || bends > PUBLISHER_PATH_NODE_LIMIT ||
                    lastDirection < 0 || lastDirection > 4 || segments < 0 || segments > 4095)
                throw new IllegalStateException("P07 Publisher terminal scalars exceed the frozen wire domain");
            capture.qualityIndex = qualityIndex;
            capture.reversedSize = reversedSize;
            capture.storedLength = length;
            capture.storedBends = bends;
            capture.storedLastDirection = lastDirection;
            capture.segments = segments;
            group.diagnosticCopiedEntries += 6;
        }

        private void observeOrderingResult(PublisherCapture capture, GroupLedger group,
                int sequence) {
            int finalizedAfter;
            String detail;
            if (request != null) {
                finalizedAfter = request.receipt.finalizedOrderings;
                detail = readNullableString(ownerSession, "mediumTwoLayerLastFailureDetail");
            } else if (readField(ownerSession, "mediumTwoLayerRoute") == capture.route) {
                finalizedAfter = intField(ownerSession, "mediumTwoLayerFinalizedOrderings");
                detail = readNullableString(ownerSession, "mediumTwoLayerLastFailureDetail");
            } else {
                return;
            }
            if (finalizedAfter < 0 || finalizedAfter > 3)
                throw new IllegalStateException("P07 ordering-result finalizer count is outside its source bound");
            if ("READY".equals(capture.routerStatus)) {
                if (capture.orderingReady == null || finalizedAfter < capture.finalizedBefore ||
                        finalizedAfter > capture.finalizedBefore + 1 ||
                        readField(capture.route, "readyOrdering") != null)
                    throw new IllegalStateException("Ready P07 parent advance lost its finalizer counter join");
                if (finalizedAfter == capture.finalizedBefore + 1) {
                    capture.finalizerStatus = "FINALIZED";
                } else if (detail != null && detail.length() > 0) {
                    capture.finalizerStatus = "REJECTED";
                } else {
                    throw new IllegalStateException("Ready P07 parent advance omitted its finalizer result");
                }
            } else {
                if (capture.orderingReady != null ||
                        finalizedAfter != capture.finalizedBefore)
                    throw new IllegalStateException("Rejected P07 route unexpectedly reached its generator finalizer");
                capture.finalizerStatus = "NOT_REACHED";
            }
            capture.finalizedAfter = finalizedAfter;
            capture.lastFailureDetail = detail;
            capture.orderingResult = new CaptureCheckpoint(group.ordinal, sequence);
            group.diagnosticCopiedEntries += 6;
        }

        private void closePublisherCaptureAfterRequest(GroupLedger group) {
            PublisherCapture capture = publisherCapture;
            if (capture == null || request == null || group == null ||
                    !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("P07 Publisher capture did not reach its request receipt");
            if (capture.outcome == null || capture.publisherSeen == null ||
                    capture.publisherTerminal == null || capture.orderingResult == null ||
                    capture.routerStatus == null || capture.finalizerStatus == null ||
                    !capture.sourceReferencesReleased || capture.payloadSha256 == null ||
                    capture.payloadBytes < 1 || capture.payloadChunkIndex < 1 ||
                    capture.reason == null && "REJECTED".equals(capture.outcome) ||
                    capture.reason != null && "PUBLISHED".equals(capture.outcome))
                throw new IllegalStateException("P07 Publisher boundary is missing source terminal evidence");
            if (("READY".equals(capture.routerStatus)) != (capture.orderingReady != null) ||
                    ("READY".equals(capture.routerStatus) &&
                        (capture.readyPass != capture.pass || capture.readyLayoutToken <= 0)) ||
                    ("ROUTER_REJECTED".equals(capture.routerStatus) &&
                        (capture.readyPass != -1 || capture.readyLayoutToken != -1 ||
                            !"NOT_REACHED".equals(capture.finalizerStatus))) ||
                    (!"READY".equals(capture.routerStatus) &&
                        !"ROUTER_REJECTED".equals(capture.routerStatus)))
                throw new IllegalStateException("P07 Publisher ordering checkpoint combination is invalid");
            if (request.context.requestIndex != 0 || request.receipt.attempt != capturedContext.selectedAttempt ||
                    request.receipt.startedOrderings <= capture.pass ||
                    request.receipt.completedOrderings <= capture.pass ||
                    request.receipt.finalizedOrderings < capture.finalizedAfter ||
                    candidateHeader.rootSeed != (study.mode == Mode.AUTHORED ?
                        AUTHORED_ROOT : TINY_ROOT))
                throw new IllegalStateException("P07 Publisher target does not join the complete request receipt");
            if (capture.outcome.equals("PUBLISHED") &&
                    (!capture.sourceHandoff || !capture.startReachedAfter || capture.stage != 6))
                throw new IllegalStateException("P07 Publisher publication lacks its actual stage-6 Search handoff");
            if (capture.outcome.equals("REJECTED") &&
                    (!capture.reason.equals(capture.netId + ":BRANCH_QUALITY_LIMIT") ||
                        capture.stage != 1 || capture.qualityIndex != capture.reversedSize ||
                        capture.sourceHandoff || capture.startReachedAfter))
                throw new IllegalStateException("P07 Publisher quality rejection lacks its source stage/reason");
        }

        private void writePublisherCapture() throws Exception {
            PublisherCapture capture = publisherCapture;
            if (capture == null || capture.after == null || capture.publisher == null ||
                    !capture.ownerBindingVerified || capture.orderingResult == null)
                throw new IllegalStateException("P07 Publisher capture is NOT_OBSERVED or incomplete");
            while (!capture.pathCopyComplete) {
                GroupLedger group = newGroup("CAPTURE", System.nanoTime());
                currentGroup = group;
                copyPublisherPath(capture, group);
                finishGroup(group);
                currentGroup = null;
            }
            while (!capture.pathAuditComplete) {
                GroupLedger verifyPath = newGroup("CAPTURE", System.nanoTime());
                currentGroup = verifyPath;
                advancePublisherPathVerification(capture, verifyPath);
                finishGroup(verifyPath);
                currentGroup = null;
            }
            GeometryPreparation geometry = null;
            while (geometry == null || !geometry.complete) {
                GroupLedger group = newGroup("CAPTURE", System.nanoTime());
                currentGroup = group;
                if (geometry == null) geometry = new GeometryPreparation(capture);
                geometry.advance(group);
                finishGroup(group);
                currentGroup = null;
            }
            capture.staticGeometryEqual = geometry.staticGeometryEqual;
            if (!capture.staticGeometryEqual)
                throw new IllegalStateException("P07 before/after static geometry prefix changed");
            while (!capture.geometryDeltaComplete) {
                GroupLedger verifyGeometry = newGroup("CAPTURE", System.nanoTime());
                currentGroup = verifyGeometry;
                advancePublisherGeometryDelta(capture, verifyGeometry);
                finishGroup(verifyGeometry);
                currentGroup = null;
            }
            capture.prefixRowsConserved = true;

            GroupLedger identity = newGroup("CAPTURE", System.nanoTime());
            currentGroup = identity;
            verifyPublisherIdentityTokens(capture, identity);
            finishGroup(identity);
            currentGroup = null;

            JsonUtf8Stream payload;
            GroupLedger release = newGroup("CAPTURE", System.nanoTime());
            currentGroup = release;
            releasePublisherReferences(capture, release);
            MetadataCopyLedger metadataCopies = new MetadataCopyLedger();
            Object targetValue = publisherTargetValue(capture, metadataCopies);
            release.diagnosticCopiedEntries += metadataCopies.entries;
            payload = new JsonUtf8Stream(targetValue);
            finishGroup(release);
            currentGroup = null;

            MessageDigest payloadDigest = null;
            boolean resultWritten = false;
            while (!resultWritten) {
                GroupLedger group = newGroup("CAPTURE", System.nanoTime());
                currentGroup = group;
                if (payloadDigest == null) payloadDigest = sha256Digest();
                byte[] chunk = payload.nextChunk(PUBLISHER_CHUNK_BYTES);
                if (chunk.length > 0) {
                    if (capture.payloadBytes > PUBLISHER_PAYLOAD_LIMIT - chunk.length)
                        throw new IOException("P07 Publisher target exceeded the 2 MiB payload bound");
                    capture.payloadBytes += chunk.length;
                    capture.payloadOffset += chunk.length;
                    capture.payloadChunkIndex++;
                    group.diagnosticSerializedBytes += chunk.length;
                    group.diagnosticHashBytes += chunk.length;
                    group.diagnosticCopiedEntries += chunk.length;
                    group.hashInputBytes += chunk.length;
                    payloadDigest.update(chunk);
                    JsonObject row = new JsonObject();
                    row.string("record", "PUB_CHUNK");
                    row.number("operation", operation);
                    row.number("group", group.ordinal);
                    row.string("targetId", "op4/pass1/target0");
                    row.number("index", capture.payloadChunkIndex - 1);
                    row.number("offset", capture.payloadOffset - chunk.length);
                    row.number("bytes", chunk.length);
                    row.string("data", Base64.getEncoder().encodeToString(chunk));
                    appendPublisherRecord(group, row.finish(), true);
                }
                if (chunk.length == 0) {
                    if (capture.payloadBytes < 1 || capture.payloadBytes > PUBLISHER_PAYLOAD_LIMIT)
                        throw new IOException("P07 Publisher target payload is outside its frozen size bound");
                    capture.payloadSha256 = hex(payloadDigest.digest());
                    JsonObject result = new JsonObject();
                    result.string("record", "PUB_RESULT");
                    result.number("operation", operation);
                    result.number("group", group.ordinal);
                    result.string("targetId", "op4/pass1/target0");
                    result.number("payloadBytes", capture.payloadBytes);
                    result.string("payloadSha256", capture.payloadSha256);
                    result.number("chunkCount", capture.payloadChunkIndex);
                    appendPublisherRecord(group, result.finish(), false);
                    resultWritten = true;
                }
                if (group.diagnosticUnits() > DIAGNOSTIC_GROUP_UNITS)
                    throw new IOException("P07 Publisher CAPTURE group exceeded 32768 diagnostic units");
                finishGroup(group);
                currentGroup = null;
            }
        }

        private void copyPublisherPath(PublisherCapture capture, GroupLedger group) {
            if (group == null || !"CAPTURE".equals(group.phase) ||
                    capture.pathCopyComplete || capture.pathNodes.isEmpty() && capture.foundCursor == null)
                throw new IllegalStateException("P07 path copy has no live immutable Node chain");
            int budget = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
            while (capture.foundCursor != null && budget >= 15) {
                if (capture.pathNodes.size() >= PUBLISHER_PATH_NODE_LIMIT)
                    throw new IllegalStateException("P07 Publisher path exceeded 4096 Node rows");
                Object node = capture.foundCursor;
                verifyImmutableNodeType(node);
                if (capture.pathSeen.put(node, Boolean.TRUE) != null)
                    throw new IllegalStateException("P07 Node.previous chain contains a repeated identity");
                int cell = intField(node, "cell");
                int layer = intField(node, "layer");
                int direction = intField(node, "direction");
                int transitions = intField(node, "transitions");
                int cost = intField(node, "cost");
                int heuristic = intField(node, "heuristic");
                int serial = intField(node, "serial");
                int key = intField(node, "key");
                long x = (long)capture.originX + (long)(cell % capture.width) * capture.pitch;
                long y = (long)capture.originY + (long)(cell / capture.width) * capture.pitch;
                if (cell < 0 || cell >= capture.cells || x < -1000000L || x > 1000000L ||
                        y < -1000000L || y > 1000000L)
                    throw new IllegalStateException("P07 Node is outside its captured raster domain");
                int[] tuple = {cell, layer, (int)x, (int)y, direction, transitions,
                    cost, heuristic, serial, key, 0};
                int nodeToken = publisherToken(node);
                capture.pathNodes.add(new PathCapture(tuple, nodeToken));
                capture.pathNodeRefs.add(node);
                capture.foundCursor = readField(node, "previous");
                group.diagnosticCopiedEntries += 15;
                budget -= 15;
            }
            if (capture.foundCursor != null) return;
            if (capture.pathNodes.isEmpty() || capture.pathNodes.size() > PUBLISHER_PATH_NODE_LIMIT)
                throw new IllegalStateException("P07 Publisher path is empty or outside its frozen row limit");
            int remaining = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
            while (!capture.pathReversed && remaining >= 4) {
                int last = capture.pathNodes.size() - 1 - capture.pathReverseIndex;
                if (capture.pathReverseIndex >= last) {
                    capture.pathReversed = true;
                    break;
                }
                PathCapture pathTemp = capture.pathNodes.get(capture.pathReverseIndex);
                capture.pathNodes.set(capture.pathReverseIndex, capture.pathNodes.get(last));
                capture.pathNodes.set(last, pathTemp);
                Object refTemp = capture.pathNodeRefs.get(capture.pathReverseIndex);
                capture.pathNodeRefs.set(capture.pathReverseIndex, capture.pathNodeRefs.get(last));
                capture.pathNodeRefs.set(last, refTemp);
                capture.pathReverseIndex++;
                group.diagnosticCopiedEntries += 4;
                remaining -= 4;
            }
            if (!capture.pathReversed && capture.pathReverseIndex >= capture.pathNodes.size() / 2)
                capture.pathReversed = true;
            while (capture.pathReversed && capture.pathParentIndex < capture.pathNodes.size() && remaining > 0) {
                int index = capture.pathParentIndex++;
                capture.pathNodes.get(index).tuple[10] = index == 0 ? -1 : index - 1;
                group.diagnosticCopiedEntries++;
                remaining--;
            }
            if (!capture.pathReversed || capture.pathParentIndex < capture.pathNodes.size()) return;
            Object first = capture.pathNodeRefs.get(0);
            if (readField(first, "previous") != null ||
                    capture.pathNodeRefs.get(capture.pathNodeRefs.size() - 1) != capture.found ||
                    capture.pathNodes.get(capture.pathNodes.size() - 1).nodeToken != capture.foundToken)
                throw new IllegalStateException("P07 copied Node chain is not the actual start-to-found path");
            Object reversedValue = readField(capture.publisher, "reversed");
            if (!(reversedValue instanceof Vector) ||
                    ((Vector<?>)reversedValue).size() != capture.pathNodeRefs.size())
                throw new IllegalStateException("P07 Publisher reversed path differs from its found Node chain");
            Vector<?> reversed = (Vector<?>)reversedValue;
            int remainingComparisons = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
            while (capture.pathVerifyIndex < capture.pathNodeRefs.size() && remainingComparisons >= 2) {
                int index = capture.pathVerifyIndex++;
                Object[] references = {reversed.get(reversed.size() - 1 - index),
                    capture.pathNodeRefs.get(index)};
                group.diagnosticCopiedEntries += 2;
                remainingComparisons -= 2;
                if (references[0] != references[1])
                    throw new IllegalStateException("P07 Publisher path is not the captured Node.previous chain");
            }
            if (capture.pathVerifyIndex == capture.pathNodeRefs.size()) {
                capture.immutablePathVerified = true;
                capture.pathCopyComplete = true;
            }
        }

        private void advancePublisherPathVerification(PublisherCapture capture, GroupLedger group) {
            if (!capture.pathCopyComplete || capture.pathNodes.size() != capture.reversedSize ||
                    capture.pathNodes.size() < 1 || capture.pathNodes.size() > PUBLISHER_PATH_NODE_LIMIT)
                throw new IllegalStateException("P07 Publisher path/reversed-size join is incomplete");
            int budget = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
            while (capture.pathAuditIndex < capture.pathNodes.size() && budget >= 11) {
                int index = capture.pathAuditIndex++;
                int[] node = capture.pathNodes.get(index).tuple;
                if (node[0] < 0 || node[0] >= capture.cells || node[1] < 0 || node[1] > 1 ||
                        node[4] < 0 || node[4] > 4 || node[5] < 0 || node[5] > 4 ||
                        node[10] != (index == 0 ? -1 : index - 1))
                    throw new IllegalStateException("P07 Publisher path contains an invalid grid tuple");
                int expectedCell = ((node[3] - capture.originY) / capture.pitch) * capture.width +
                    (node[2] - capture.originX) / capture.pitch;
                long key = (((long)node[5] * 2L + node[1]) * 5L + node[4]) * capture.cells + node[0];
                if (node[2] != capture.originX + (node[0] % capture.width) * capture.pitch ||
                        node[3] != capture.originY + (node[0] / capture.width) * capture.pitch ||
                        expectedCell != node[0] || key != node[9])
                    throw new IllegalStateException("P07 Publisher path cell/key disagrees with its captured grid");
                if (index == 0) {
                    if (node[4] != 4 || node[5] != 0)
                        throw new IllegalStateException("P07 Publisher start Node has invalid direction/transitions");
                } else {
                    if (node[1] == capture.pathAuditPriorLayer) {
                        int dx = node[2] - capture.pathAuditPriorX;
                        int dy = node[3] - capture.pathAuditPriorY;
                    int expectedDirection = dx == capture.pitch && dy == 0 ? 1 :
                        dx == 0 && dy == capture.pitch ? 2 :
                        dx == -capture.pitch && dy == 0 ? 3 :
                        dx == 0 && dy == -capture.pitch ? 0 : -1;
                        if (expectedDirection < 0 || node[4] != expectedDirection ||
                                node[5] != capture.pathAuditPriorTransitions)
                            throw new IllegalStateException("P07 Publisher path has a broken same-layer step");
                        capture.pathAuditLength += capture.pitch;
                        if (capture.pathAuditLastDirection != 4 &&
                                capture.pathAuditLastDirection != node[4]) capture.pathAuditBends++;
                        capture.pathAuditLastDirection = node[4];
                    } else {
                        if (node[0] != capture.pathAuditPriorCell || node[4] != 4 ||
                                node[5] != capture.pathAuditPriorTransitions + 1)
                            throw new IllegalStateException("P07 Publisher path has a broken layer transition");
                        capture.pathAuditTransitions++;
                        capture.pathAuditLastDirection = 4;
                    }
                }
                capture.pathAuditPriorCell = node[0];
                capture.pathAuditPriorLayer = node[1];
                capture.pathAuditPriorX = node[2];
                capture.pathAuditPriorY = node[3];
                capture.pathAuditPriorTransitions = node[5];
                group.diagnosticCopiedEntries += 11;
                budget -= 11;
            }
            if (capture.pathAuditIndex < capture.pathNodes.size()) return;
            if (DIAGNOSTIC_GROUP_UNITS - group.diagnosticUnits() < 2) return;
            int[] found = capture.pathNodes.get(capture.pathNodes.size() - 1).tuple;
            group.diagnosticCopiedEntries += 2;
            if (!capture.foundGoal || found[5] != capture.pathAuditTransitions ||
                    capture.storedLength != capture.pathAuditLength ||
                    capture.storedBends != capture.pathAuditBends ||
                    capture.storedLastDirection != capture.pathAuditLastDirection ||
                    capture.storedLastDirection < 0 || capture.storedLastDirection > 4)
                throw new IllegalStateException("P07 Publisher stored guard operands differ from its actual path");
            int direct = Math.abs(capture.startX - found[2]) +
                Math.abs(capture.startY - found[3]);
            boolean qualityRejected = direct == 0 || capture.pathAuditLength > direct * 3 ||
                capture.pathAuditBends > 16;
            if (("REJECTED".equals(capture.outcome) && !qualityRejected) ||
                    ("PUBLISHED".equals(capture.outcome) && qualityRejected))
                throw new IllegalStateException("P07 Publisher source outcome contradicts its independent OR guard");
            capture.pathAuditComplete = true;
        }

        private void advancePublisherGeometryDelta(PublisherCapture capture, GroupLedger group) {
            if (capture == null || group == null || !"CAPTURE".equals(group.phase) ||
                    capture.geometryDeltaComplete)
                throw new IllegalStateException("P07 geometry-delta audit has no unique CAPTURE owner");
            verifyPublisherGeometryDelta(capture, group);
        }

        private void verifyPublisherGeometryDelta(PublisherCapture capture, GroupLedger group) {
            if (capture.outcome.equals("REJECTED")) {
                if (!capture.before.geometrySha256.equals(capture.after.geometrySha256) ||
                        !sameGeometryRows(capture.before, capture.after))
                    throw new IllegalStateException("Rejected P07 Publisher changed raw geometry rows");
                capture.geometryDeltaComplete = true;
                return;
            }
            if (capture.geometryDeltaExpectedBuilt) {
                int traceWords = (capture.after.traces.size() + 63) >>> 6;
                int holeWords = (capture.after.holes.size() + 63) >>> 6;
                long bitmapEntries = (long)traceWords + holeWords + 2L;
                if (bitmapEntries > DIAGNOSTIC_GROUP_UNITS - group.diagnosticUnits())
                    throw new IllegalStateException("P07 geometry row-match bitmap exceeds its CAPTURE group bound");
                BitSet traceUsed = new BitSet(capture.after.traces.size());
                BitSet holeUsed = new BitSet(capture.after.holes.size());
                group.diagnosticCopiedEntries += bitmapEntries;
                if (!prefixGeometryAndDeltaMatch(capture.before, capture.after,
                        capture.expectedDeltaTraces, capture.expectedDeltaHoles, traceUsed, holeUsed))
                    throw new IllegalStateException("P07 Publisher geometry delta does not equal its captured path pieces");
                capture.geometryDeltaComplete = true;
                return;
            }
            List<TraceCapture> expectedTraces = new ArrayList<TraceCapture>();
            List<HoleCapture> expectedHoles = new ArrayList<HoleCapture>();
            int pieceStart = 0, pieceIndex = 0;
            int rootX = capture.rootX, rootY = capture.rootY;
            int rootCell = ((rootY - capture.originY) / capture.pitch) * capture.width +
                (rootX - capture.originX) / capture.pitch;
            int width = capture.traceWidth;
            while (pieceStart < capture.pathNodes.size()) {
                int pieceScan = pieceStart + 1;
                while (pieceScan < capture.pathNodes.size() &&
                        capture.pathNodes.get(pieceScan).tuple[1] ==
                            capture.pathNodes.get(pieceScan - 1).tuple[1]) pieceScan++;
                int pieceEnd = pieceScan - 1;
                boolean transition = pieceScan < capture.pathNodes.size();
                int pathCount = pieceEnd - pieceStart + 1;
                if (pathCount > 1) {
                    List<int[]> compact = new ArrayList<int[]>();
                    for (int index = pieceStart; index <= pieceEnd; index++) {
                        int[] node = capture.pathNodes.get(index).tuple;
                        int count = compact.size();
                        if (count >= 2) {
                            int[] a = compact.get(count - 2), b = compact.get(count - 1);
                            if ((a[0] == b[0] && b[0] == node[2]) ||
                                    (a[1] == b[1] && b[1] == node[3])) compact.remove(count - 1);
                        }
                        compact.add(new int[] {node[2], node[3]});
                        group.diagnosticCopiedEntries += 3;
                    }
                    int[] x = new int[compact.size()], y = new int[compact.size()];
                    for (int index = 0; index < compact.size(); index++) {
                        x[index] = compact.get(index)[0]; y[index] = compact.get(index)[1];
                        group.diagnosticCopiedEntries += 2;
                    }
                    int[] first = capture.pathNodes.get(pieceStart).tuple;
                    int[] last = capture.pathNodes.get(pieceEnd).tuple;
                    String endPadId = null;
                    if (pieceEnd == capture.pathNodes.size() - 1) {
                        if (capture.foundTerminalPadId != null) endPadId = capture.foundTerminalPadId;
                        else if (last[0] == rootCell) endPadId = capture.rootPadId;
                    }
                    expectedTraces.add(new TraceCapture(
                        "p07/" + capture.netId + "/" + capture.searchBranchCount + "/" + pieceIndex++,
                        capture.netId, pieceStart == 0 ? capture.startPadId : null,
                        endPadId, first[1], "EXPOSED", width, x, y));
                    group.diagnosticCopiedEntries += 7;
                }
                if (transition) {
                    int[] last = capture.pathNodes.get(pieceEnd).tuple;
                    boolean existing = false;
                    for (HoleCapture hole : capture.before.holes)
                        if (hole.x == last[2] && hole.y == last[3]) existing = true;
                    if (!existing)
                        expectedHoles.add(new HoleCapture(
                            "p07-via/" + capture.netId + "/" + capture.searchBranchCount + "/" + pieceIndex,
                            "VIA", capture.netId, last[2], last[3], 2, 6, "EXPOSED"));
                    if (!existing) group.diagnosticCopiedEntries += 8;
                }
                pieceStart = pieceScan;
            }
            capture.expectedDeltaTraces.addAll(expectedTraces);
            capture.expectedDeltaHoles.addAll(expectedHoles);
            group.diagnosticCopiedEntries += expectedTraces.size() + expectedHoles.size();
            if (group.diagnosticUnits() > DIAGNOSTIC_GROUP_UNITS)
                throw new IllegalStateException("P07 Publisher geometry verification exceeded its CAPTURE group bound");
            capture.geometryDeltaExpectedBuilt = true;
        }

        private boolean prefixGeometryAndDeltaMatch(GeometryCapture before, GeometryCapture after,
                List<TraceCapture> expectedTraces, List<HoleCapture> expectedHoles,
                BitSet traceUsed, BitSet holeUsed) {
            for (TraceCapture row : before.traces) {
                int match = findTrace(after.traces, row, traceUsed);
                if (match < 0) return false;
                traceUsed.set(match);
            }
            for (HoleCapture row : before.holes) {
                int match = findHole(after.holes, row, holeUsed);
                if (match < 0) return false;
                holeUsed.set(match);
            }
            for (TraceCapture row : expectedTraces) {
                int match = findTrace(after.traces, row, traceUsed);
                if (match < 0) return false;
                traceUsed.set(match);
            }
            for (HoleCapture row : expectedHoles) {
                int match = findHole(after.holes, row, holeUsed);
                if (match < 0) return false;
                holeUsed.set(match);
            }
            return traceUsed.nextClearBit(0) >= after.traces.size() &&
                holeUsed.nextClearBit(0) >= after.holes.size();
        }

        private final class GeometryPreparation {
            private final PublisherCapture capture;
            private int stage;
            private int staticCompareIndex;
            private Utf8HashCursor beforeHash;
            private Utf8HashCursor afterHash;
            boolean complete;
            boolean staticGeometryEqual;
            GeometryPreparation(PublisherCapture capture) { this.capture = capture; }

            void advance(GroupLedger group) {
                if (group == null || !"CAPTURE".equals(group.phase) || complete)
                    throw new IllegalStateException("P07 geometry preparation has no CAPTURE owner");
                while (!complete) {
                    if (stage == 0) {
                        if (!advanceSuffix(capture.before, group)) return;
                        stage = 1;
                    } else if (stage == 1) {
                        if (!verifySuffix(capture.before, group)) return;
                        stage = 2;
                    } else if (stage == 2) {
                        if (!advanceSuffix(capture.after, group)) return;
                        stage = 3;
                    } else if (stage == 3) {
                        if (!verifySuffix(capture.after, group)) return;
                        stage = 4;
                    } else if (stage == 4) {
                        if (!compareStaticPrefixes(group)) return;
                        stage = 5;
                    } else if (stage == 5) {
                        if (beforeHash == null)
                            beforeHash = new Utf8HashCursor(capture.before.sourceFingerprint,
                                sha256Digest());
                        int available = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
                        int hashed = beforeHash.advance(available);
                        group.diagnosticHashBytes += hashed;
                        group.hashInputBytes += hashed;
                        if (beforeHash.isDone()) {
                            capture.before.geometrySha256 = beforeHash.finishSha256();
                            capture.before.geometryBytes = beforeHash.byteCount();
                            if (!capture.before.geometrySha256.equals(capture.beforeSha256) ||
                                    capture.before.geometryBytes != capture.beforeBytes)
                                throw new IllegalStateException("P07 captured BRANCH geometry hash did not reproduce");
                            stage = 6;
                        }
                        if (stage != 6) return;
                    } else if (stage == 6) {
                        if (afterHash == null)
                            afterHash = new Utf8HashCursor(capture.after.sourceFingerprint,
                                sha256Digest());
                        int available = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
                        int hashed = afterHash.advance(available);
                        group.diagnosticHashBytes += hashed;
                        group.hashInputBytes += hashed;
                        if (afterHash.isDone()) {
                            capture.after.geometryBytes = afterHash.byteCount();
                            if (capture.after.geometryBytes < 1 ||
                                    capture.after.geometryBytes > PREFIX_LIMIT_BYTES)
                                throw new IllegalStateException("P07 after-geometry fingerprint exceeded its 1 MiB bound");
                            capture.after.geometrySha256 = afterHash.finishSha256();
                            complete = true;
                        }
                        if (!complete) return;
                    } else {
                        throw new IllegalStateException("Invalid P07 geometry-preparation state");
                    }
                    if (group.diagnosticUnits() >= DIAGNOSTIC_GROUP_UNITS) return;
                }
                staticGeometryEqual = true;
            }

            private boolean advanceSuffix(GeometryCapture geometry, GroupLedger group) {
                if (!geometry.suffixBuilt) {
                    if (geometry.suffixCursor == null) {
                        geometry.suffixCursor = new GeometrySuffixCursor(geometry);
                        geometry.suffixBuilder = new StringBuilder();
                    }
                    int available = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
                    int maxChars = available / 2;
                    if (maxChars < 1) return false;
                    String chunk = geometry.suffixCursor.nextChunk(maxChars);
                    if (chunk.length() > 0) {
                        geometry.suffixBuilder.append(chunk);
                        group.diagnosticCopiedEntries += (long)chunk.length() * 2L;
                    }
                    if (geometry.suffixCursor.isDone()) {
                        geometry.staticPrefixLength = geometry.sourceFingerprint.length() -
                            geometry.suffixBuilder.length();
                        if (geometry.staticPrefixLength < 0)
                            throw new IllegalStateException("P07 geometry suffix exceeds its source fingerprint");
                        geometry.suffixBuilt = true;
                    }
                    return geometry.suffixBuilt;
                }
                return true;
            }

            private boolean verifySuffix(GeometryCapture geometry, GroupLedger group) {
                if (geometry.suffixVerified) return true;
                int remaining = geometry.suffixBuilder.length() - geometry.suffixCompareIndex;
                if (remaining <= 0) {
                    geometry.suffixVerified = true;
                    geometry.suffixBuilder = null;
                    geometry.suffixCursor = null;
                    return true;
                }
                int available = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
                int count = Math.min(remaining, available / 2);
                if (count < 1) return false;
                char[] source = new char[count];
                char[] expected = new char[count];
                int sourceAt = geometry.staticPrefixLength + geometry.suffixCompareIndex;
                for (int index = 0; index < count; index++) {
                    source[index] = geometry.sourceFingerprint.charAt(sourceAt + index);
                    expected[index] = geometry.suffixBuilder.charAt(geometry.suffixCompareIndex + index);
                }
                group.diagnosticCopiedEntries += (long)count * 2L;
                for (int index = 0; index < count; index++)
                    if (source[index] != expected[index])
                        throw new IllegalStateException("P07 raw trace/hole rows do not reconstruct source geometry");
                geometry.suffixCompareIndex += count;
                if (geometry.suffixCompareIndex == geometry.suffixBuilder.length()) {
                    geometry.suffixVerified = true;
                    geometry.suffixBuilder = null;
                    geometry.suffixCursor = null;
                }
                return geometry.suffixVerified;
            }

            private boolean compareStaticPrefixes(GroupLedger group) {
                GeometryCapture before = capture.before, after = capture.after;
                if (before.staticPrefixLength != after.staticPrefixLength)
                    throw new IllegalStateException("P07 before/after static geometry prefix length changed");
                int remaining = before.staticPrefixLength - staticCompareIndex;
                if (remaining <= 0) {
                    staticGeometryEqual = true;
                    return true;
                }
                int available = DIAGNOSTIC_GROUP_UNITS - (int)group.diagnosticUnits();
                int count = Math.min(remaining, available / 2);
                if (count < 1) return false;
                char[] left = new char[count], right = new char[count];
                for (int index = 0; index < count; index++) {
                    left[index] = before.sourceFingerprint.charAt(staticCompareIndex + index);
                    right[index] = after.sourceFingerprint.charAt(staticCompareIndex + index);
                }
                group.diagnosticCopiedEntries += (long)count * 2L;
                for (int index = 0; index < count; index++)
                    if (left[index] != right[index])
                        throw new IllegalStateException("P07 before/after static geometry prefix changed");
                staticCompareIndex += count;
                staticGeometryEqual = staticCompareIndex == before.staticPrefixLength;
                return staticGeometryEqual;
            }
        }

        private int findTrace(List<TraceCapture> rows, TraceCapture expected, BitSet used) {
            for (int index = 0; index < rows.size(); index++)
                if (!used.get(index) && sameTrace(rows.get(index), expected)) return index;
            return -1;
        }

        private int findHole(List<HoleCapture> rows, HoleCapture expected, BitSet used) {
            for (int index = 0; index < rows.size(); index++)
                if (!used.get(index) && sameHole(rows.get(index), expected)) return index;
            return -1;
        }

        private void verifyPublisherBinding(PublisherCapture capture, Object publisher) {
            if (readField(capture.generatorOwner, "mediumTwoLayerRoute") != capture.route ||
                    readField(capture.search, "owner") != capture.route)
                throw new IllegalStateException("P07 Publisher source route/Search binding changed");
            Object publisherNet = readField(publisher, "net");
            if (!(publisherNet instanceof String) || !capture.netId.equals(publisherNet) ||
                    readField(publisher, "start") != readField(capture.branch, "start") ||
                    readField(publisher, "root") != readField(capture.branch, "root") ||
                    readField(publisher, "end") != capture.found ||
                    readField(publisher, "endTerminal") != capture.terminal)
                throw new IllegalStateException("P07 Publisher does not own the captured FOUND path and endpoints");
        }

        private void verifyPublisherIdentityTokens(PublisherCapture capture, GroupLedger group) {
            if (capture == null || group == null || !"CAPTURE".equals(group.phase) ||
                    capture.publisher == null || capture.pathNodeRefs.size() != capture.pathNodes.size())
                throw new IllegalStateException("P07 identity-token verification lacks its captured graph");
            requirePublisherToken(capture.generatorOwner, capture.generatorToken);
            requirePublisherToken(capture.route, capture.routeToken);
            requirePublisherToken(capture.search, capture.searchToken);
            requirePublisherToken(capture.branch, capture.branchToken);
            requirePublisherToken(capture.layout, capture.layoutToken);
            requirePublisherToken(capture.found, capture.foundToken);
            requirePublisherToken(capture.publisher, capture.publisherToken);
            group.diagnosticCopiedEntries += 7;
            if (capture.terminal == null) {
                if (capture.terminalToken != null)
                    throw new IllegalStateException("P07 null terminal has a non-null identity token");
            } else {
                if (capture.terminalToken == null)
                    throw new IllegalStateException("P07 terminal identity token is absent");
                requirePublisherToken(capture.terminal, capture.terminalToken.intValue());
                group.diagnosticCopiedEntries++;
            }
            for (int index = 0; index < capture.pathNodes.size(); index++) {
                PathCapture row = capture.pathNodes.get(index);
                requirePublisherToken(capture.pathNodeRefs.get(index), row.nodeToken);
                group.diagnosticCopiedEntries++;
            }
            if (group.diagnosticUnits() > DIAGNOSTIC_GROUP_UNITS)
                throw new IllegalStateException("P07 identity-token verification exceeded its CAPTURE bound");
        }

        private void requirePublisherToken(Object source, int expected) {
            Integer actual = source == null ? null : publisherIdentityTokens.get(source);
            if (expected <= 0 || actual == null || actual.intValue() != expected)
                throw new IllegalStateException("P07 source object identity token changed before closure");
        }

        private void releasePublisherReferences(PublisherCapture capture, GroupLedger group) {
            if (capture == null || group == null || !"CAPTURE".equals(group.phase) ||
                    capture.sourceReferencesReleased || publisherCapture != capture)
                throw new IllegalStateException("P07 source graph release has no unique CAPTURE owner");
            long references = publisherIdentityTokens.size() + capture.pathNodeRefs.size() +
                capture.pathSeen.size() + 10L;
            if (references > DIAGNOSTIC_GROUP_UNITS - group.diagnosticUnits())
                throw new IllegalStateException("P07 source-reference release exceeded its CAPTURE budget");
            publisherIdentityTokens.clear();
            capture.releaseGraphReferences();
            group.diagnosticCopiedEntries += references;
            if (!publisherIdentityTokens.isEmpty() || capture.hasGraphReferences())
                throw new IllegalStateException("P07 source graph references survived capture closure");
            capture.sourceReferencesReleased = true;
        }

        private void verifyPublishedPublisher(PublisherCapture capture, int stage) {
            if (stage != 6 || intField(capture.publisher, "stage") != 6)
                throw new IllegalStateException("P07 published target changed Publisher identity");
            if (readField(capture.publisher, "end") != capture.found ||
                    readField(capture.publisher, "endTerminal") != capture.terminal)
                throw new IllegalStateException("P07 completed Publisher changed its found endpoint");
        }

        private void verifyGeometryBoundary(PublisherCapture capture) {
            if (capture.after == null || capture.before == null ||
                    capture.before.sourceFingerprint == null ||
                    capture.after.sourceFingerprint == null ||
                    capture.after.traces == null || capture.after.holes == null)
                throw new IllegalStateException("P07 geometry snapshot is incomplete");
        }

        private int publisherToken(Object value) {
            if (value == null) throw new IllegalArgumentException("Cannot tokenize a null P07 owner");
            Integer token = publisherIdentityTokens.get(value);
            if (token != null) return token.intValue();
            if (nextPublisherIdentityToken <= 0 || nextPublisherIdentityToken == Integer.MAX_VALUE)
                throw new IllegalStateException("P07 per-operation identity token space is exhausted");
            int created = nextPublisherIdentityToken++;
            publisherIdentityTokens.put(value, Integer.valueOf(created));
            return created;
        }

        private int nextPublisherSequence(GroupLedger group) {
            if (group == null || !"ADVANCE".equals(group.phase) ||
                    publisherPollSequence == Integer.MAX_VALUE)
                throw new IllegalStateException("P07 Publisher checkpoint has no bounded live poll");
            return ++publisherPollSequence;
        }

        private String[] copyReachedPadIds(Vector<?> reached, String netId) {
            if (reached == null || reached.size() > PAD_LIMIT)
                throw new IllegalStateException("P07 reached-pad vector is outside the captured domain");
            String[] result = new String[reached.size()];
            Set<String> unique = new HashSet<String>();
            for (int index = 0; index < result.length; index++) {
                Object value = reached.get(index);
                if (!(value instanceof String) || !token((String)value))
                    throw new IllegalStateException("P07 reached-pad vector contains an invalid pad ID");
                BoardPad logical = board.getPad((String)value);
                if (logical == null || !netId.equals(logical.getNetId()))
                    throw new IllegalStateException("P07 reached-pad vector crossed its source net");
                result[index] = (String)value;
                if (!unique.add(result[index]))
                    throw new IllegalStateException("P07 reached-pad vector contains duplicate pad IDs");
            }
            java.util.Arrays.sort(result);
            return result;
        }

        private GeometryCapture copyGeometry(PcbBoardLayout layout, GroupLedger group,
                String sourceFingerprint, boolean countFingerprintEntries) {
            if (layout == null || group == null || !"ADVANCE".equals(group.phase))
                throw new IllegalStateException("P07 geometry snapshot escaped its actual ADVANCE owner");
            Vector<PcbTraceGeometry> traces = layout.getTraces();
            Vector<PcbBoardHole> holes = layout.getHoles();
            List<TraceCapture> traceRows = new ArrayList<TraceCapture>(traces.size());
            List<HoleCapture> holeRows = new ArrayList<HoleCapture>(holes.size());
            int copied = 0;
            for (PcbTraceGeometry trace : traces) {
                if (trace == null) throw new IllegalStateException("P07 layout contains a null trace");
                int[] x = trace.getXPoints();
                int[] y = trace.getYPoints();
                if (x == null || y == null || x.length != y.length || trace.getNetId() == null)
                    throw new IllegalStateException("P07 trace coordinates are incomplete");
                copied += 8 + x.length + y.length;
                if (copied > PUBLISHER_SYNC_COPY_LIMIT)
                    throw new IllegalStateException("Synchronous P07 geometry snapshot exceeded its frozen entry cap");
                traceRows.add(new TraceCapture(trace.getSourceId(), trace.getNetId(),
                    trace.getStartPadId(), trace.getEndPadId(), trace.getLayer().ordinal(),
                    trace.getExposure().name(), trace.getWidth(), x, y));
            }
            Collections.sort(traceRows, new Comparator<TraceCapture>() {
                public int compare(TraceCapture a, TraceCapture b) {
                    int result = a.netId.compareTo(b.netId);
                    if (result != 0) return result;
                    result = String.valueOf(a.startPadId).compareTo(String.valueOf(b.startPadId));
                    if (result != 0) return result;
                    result = String.valueOf(a.endPadId).compareTo(String.valueOf(b.endPadId));
                    if (result != 0) return result;
                    result = a.layer < b.layer ? -1 : a.layer == b.layer ? 0 : 1;
                    if (result != 0) return result;
                    result = a.width < b.width ? -1 : a.width == b.width ? 0 : 1;
                    return result != 0 ? result :
                        String.valueOf(a.sourceId).compareTo(String.valueOf(b.sourceId));
                }
            });
            for (PcbBoardHole hole : holes) {
                if (hole == null) throw new IllegalStateException("P07 layout contains a null hole");
                copied += 9;
                if (copied > PUBLISHER_SYNC_COPY_LIMIT)
                    throw new IllegalStateException("Synchronous P07 geometry snapshot exceeded its frozen entry cap");
                holeRows.add(new HoleCapture(hole.id, hole.kind.name(), hole.netId,
                    hole.x, hole.y, hole.drillRadius, hole.landRadius, hole.exposure.name()));
            }
            if (countFingerprintEntries) {
                if (sourceFingerprint == null)
                    throw new IllegalStateException("P07 after-geometry fingerprint snapshot is missing");
                copied += sourceFingerprint.length();
            }
            group.diagnosticCopiedEntries += copied;
            if (group.diagnosticCopiedEntries > PUBLISHER_SYNC_COPY_LIMIT)
                throw new IllegalStateException("Synchronous P07 geometry snapshot exceeded its frozen entry cap");
            return new GeometryCapture(sourceFingerprint, traceRows, holeRows, copied);
        }

        private boolean sameGeometryRows(GeometryCapture first, GeometryCapture second) {
            if (first.traces.size() != second.traces.size() ||
                    first.holes.size() != second.holes.size()) return false;
            for (int index = 0; index < first.traces.size(); index++) {
                TraceCapture row = first.traces.get(index);
                boolean checked = false;
                for (int prior = 0; prior < index; prior++)
                    if (sameTrace(row, first.traces.get(prior))) { checked = true; break; }
                if (checked) continue;
                int firstMatches = 0, secondMatches = 0;
                for (TraceCapture candidate : first.traces)
                    if (sameTrace(row, candidate)) firstMatches++;
                for (TraceCapture candidate : second.traces)
                    if (sameTrace(row, candidate)) secondMatches++;
                if (firstMatches != secondMatches) return false;
            }
            for (int index = 0; index < first.holes.size(); index++) {
                HoleCapture row = first.holes.get(index);
                boolean checked = false;
                for (int prior = 0; prior < index; prior++)
                    if (sameHole(row, first.holes.get(prior))) { checked = true; break; }
                if (checked) continue;
                int firstMatches = 0, secondMatches = 0;
                for (HoleCapture candidate : first.holes)
                    if (sameHole(row, candidate)) firstMatches++;
                for (HoleCapture candidate : second.holes)
                    if (sameHole(row, candidate)) secondMatches++;
                if (firstMatches != secondMatches) return false;
            }
            return true;
        }

        private boolean sameTrace(TraceCapture a, TraceCapture b) {
            return equalNullable(a.sourceId, b.sourceId) && equalNullable(a.netId, b.netId) &&
                equalNullable(a.startPadId, b.startPadId) && equalNullable(a.endPadId, b.endPadId) &&
                a.layer == b.layer && a.exposure.equals(b.exposure) && a.width == b.width &&
                java.util.Arrays.equals(a.xPoints, b.xPoints) &&
                java.util.Arrays.equals(a.yPoints, b.yPoints);
        }

        private boolean sameHole(HoleCapture a, HoleCapture b) {
            return a.id.equals(b.id) && a.kind.equals(b.kind) && equalNullable(a.netId, b.netId) &&
                a.x == b.x && a.y == b.y && a.drillRadius == b.drillRadius &&
                a.landRadius == b.landRadius && a.exposure.equals(b.exposure);
        }

        private boolean equalNullable(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }

        private void verifyImmutableNodeType(Object node) {
            if (node == null || !Modifier.isFinal(node.getClass().getModifiers()) ||
                    !"com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype$Node".
                        equals(node.getClass().getName()))
                throw new IllegalStateException("P07 captured found object is not the immutable source Node type");
            String[] names = {"cell", "layer", "direction", "transitions", "cost",
                "heuristic", "serial", "key", "previous"};
            for (String name : names) {
                try {
                    Field field = node.getClass().getDeclaredField(name);
                    if (!Modifier.isFinal(field.getModifiers()))
                        throw new IllegalStateException("P07 Node path field is not immutable: " + name);
                } catch (NoSuchFieldException absent) {
                    throw new IllegalStateException("P07 immutable Node path field disappeared: " + name, absent);
                }
            }
        }

        private int vectorSize(Object value) {
            if (!(value instanceof Vector)) throw new IllegalStateException("P07 Publisher vector changed type");
            return ((Vector<?>)value).size();
        }

        private String padId(Object value) {
            if (value == null) throw new IllegalArgumentException("Missing P07 pad endpoint");
            Object id = readField(value, "padId");
            if (!(id instanceof String) || !token((String)id))
                throw new IllegalStateException("P07 pad endpoint has an invalid identity");
            return (String)id;
        }

        private String stringField(Object value, String name) {
            Object field = readField(value, name);
            if (!(field instanceof String))
                throw new IllegalStateException("P07 source field is not a string: " + name);
            return (String)field;
        }

        private String readNullableString(Object value, String name) {
            Object field = readField(value, name);
            if (field != null && !(field instanceof String))
                throw new IllegalStateException("P07 nullable source field is not a string: " + name);
            return (String)field;
        }

        private boolean isRequestZeroComplete() {
            Object event = ownerSession.p07RequestBoundaryEvent();
            if (operation != 0 && event != null) return true;
            Object receipts = readField(ownerSession, "mediumOrderingSearchReceipts");
            if (!(receipts instanceof Vector))
                throw new IllegalStateException("Actual generator omitted its typed receipt owner");
            Vector<?> rows = (Vector<?>)receipts;
            return rows.size() == 1 && intField(ownerSession, "mediumTwoLayerAttempts") == 1 &&
                readField(ownerSession, "mediumTwoLayerRoute") == null;
        }

        private RequestObservation observeCompletedRequest() {
            if (request != null) throw new IllegalStateException("Request-zero outcome was observed twice");
            if (capturedContext == null)
                throw new IllegalStateException("Completed request has no earlier actual template capture");
            Object receiptValue = readField(ownerSession, "mediumOrderingSearchReceipts");
            if (!(receiptValue instanceof Vector))
                throw new IllegalStateException("Completed request omitted the actual receipt vector");
            Vector<?> receiptRows = (Vector<?>)receiptValue;
            if (receiptRows.size() != 1 ||
                    !(receiptRows.firstElement() instanceof MediumBoardPhysicalPolicy.OrderingSearchReceipt))
                throw new IllegalStateException("Expected exactly one actual request-zero receipt");
            MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt =
                (MediumBoardPhysicalPolicy.OrderingSearchReceipt)receiptRows.firstElement();
            Object eventValue = ownerSession.p07RequestBoundaryEvent();
            String routeOutcome;
            if (operation == 0) {
                if (eventValue != null)
                    throw new IllegalStateException("Plain begin unexpectedly claimed a request-boundary event");
                routeOutcome = findActualRouteOutcome(receipt);
            } else {
                if (!(eventValue instanceof SeededPcbLayoutGenerator.P07RequestBoundaryEvent))
                    throw new IllegalStateException("Observed arm omitted its immutable request event");
                SeededPcbLayoutGenerator.P07RequestBoundaryEvent event =
                    (SeededPcbLayoutGenerator.P07RequestBoundaryEvent)eventValue;
                if (event.receipt() != receipt || event.context() == null ||
                        event.routeOutcome() == null || event.routeOutcome().length() == 0)
                    throw new IllegalStateException("Immutable event disagrees with its actual receipt");
                PcbLayerRoutingPrototype.FixedPrefixCaptureContext context = event.context();
                if (context.routeRequestIndex() != capturedContext.requestIndex ||
                        context.layoutSeed() != candidateHeader.placementSeed ||
                        context.routingSeed() != candidateHeader.routingSeed ||
                        context.selectedPlacementAttempt() != capturedContext.selectedAttempt ||
                        !context.rawPlacementFingerprint().equals(capturedContext.pristineGeometry) ||
                        !sameInts(context.eligiblePlacementAttempts(), capturedContext.eligibleAttempts))
                    throw new IllegalStateException("Immutable event context differs from the first actual owner capture");
                if (ownerSession.p07RequestBoundaryEvent() != event)
                    throw new IllegalStateException("Request-boundary event changed after its owner returned");
                routeOutcome = event.routeOutcome();
            }
            if (receipt.attempt != capturedContext.selectedAttempt ||
                    receipt.attempt != capturedContext.eligibleAttempts[0] ||
                    (study.mode == Mode.AUTHORED && receipt.attempt != 4))
                throw new IllegalStateException("Actual receipt does not select its first eligible request-zero attempt");
            int outcomeBytes = utf8(routeOutcome).length;
            if (outcomeBytes < 1 || outcomeBytes > 2048)
                throw new IllegalStateException("Actual route outcome is outside its wire bound");
            if (intField(ownerSession, "mediumTwoLayerAttempts") != 1 ||
                    readField(ownerSession, "mediumTwoLayerRoute") != null ||
                    readField(ownerSession, "mediumTwoLayerCandidate") != null ||
                    readField(ownerSession, "mediumTwoLayerRequestContext") != null ||
                    boolField(ownerSession, "complete") ||
                    readField(ownerSession, "mediumPolicyResult") != null ||
                    ownerSession.mediumResult() != null)
                throw new IllegalStateException("Request-zero owner was not retired while the generator stayed incomplete");
            int matchingOutcomes = 0;
            Object outcomesValue = readField(ownerSession, "mediumRouteOutcomes");
            if (!(outcomesValue instanceof Vector))
                throw new IllegalStateException("Actual generator omitted route outcomes");
            for (Object value : (Vector<?>)outcomesValue)
                if (routeOutcome.equals(value)) matchingOutcomes++;
            if (matchingOutcomes != 1)
                throw new IllegalStateException("Request outcome is not present exactly once in the generator owner");
            if (branchTracking != null || diagnosticEventKeys.size() != diagnosticCloneSteps)
                throw new IllegalStateException("Completed request has unfinished or uncounted diagnostic clone evidence");
            if (operation == 0 && diagnosticCloneSteps != 0)
                throw new IllegalStateException("Plain begin reported observed diagnostic clone work");
            return new RequestObservation(receipt, routeOutcome, capturedContext,
                operation != 0, generatorAdvances, measuredSlices, ownerSteps, diagnosticCloneSteps);
        }

        private String findActualRouteOutcome(MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt) {
            Object outcomesValue = readField(ownerSession, "mediumRouteOutcomes");
            if (!(outcomesValue instanceof Vector))
                throw new IllegalStateException("Plain generator omitted its route outcome owner");
            String prefix = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@" + receipt.attempt + "=";
            String found = null;
            int matches = 0;
            for (Object value : (Vector<?>)outcomesValue) {
                if (value instanceof String && ((String)value).startsWith(prefix)) {
                    found = (String)value;
                    matches++;
                }
            }
            if (matches != 1)
                throw new IllegalStateException("Plain begin did not retain exactly one actual request-zero outcome");
            return found;
        }

        private void verifyWinnerPhysical(PcbBoardLayout winner) throws Exception {
            if (study.mode == Mode.AUTHORED) {
                P07OrderingCandidatesContractTest.verifyAuthored100PhysicalSubsets(board, winner);
                return;
            }
            Class<?> owner = Class.forName(
                "com.lushprojects.circuitjs1.client.P07OrderingCandidatesContractTest");
            Method method = owner.getDeclaredMethod("validateAcceptedOutput",
                TroubleshootBoard.class, PcbBoardLayout.class);
            method.setAccessible(true);
            try {
                Object evidence = method.invoke(null, board, winner);
                if (evidence == null)
                    throw new IllegalStateException("Existing tiny physical oracle returned no accepted evidence");
            } catch (InvocationTargetException failure) {
                rethrow(failure.getCause());
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Could not call the existing tiny physical oracle", failure);
            }
        }

        private void releaseRouteOwners() {
            if (currentGroup != null) currentGroup.routeForSlice = null;
            currentGroup = null;
            if (branchTracking != null) branchTracking.release();
            branchTracking = null;
            justTerminalBranchKey = null;
            diagnosticEventKeys.clear();
            validatedDiagnosticRoute = null;
            capturedNetOrderSearch = null;
            pass0TailNet = null;
            pass0TailReason = null;
            padNetIds.clear();
            capturedContext = null;
            lastActualRoute = null;
            if (publisherCapture != null && !publisherCapture.sourceReferencesReleased) {
                publisherCapture.releaseGraphReferences();
                publisherCapture.sourceReferencesReleased = true;
            }
            publisherIdentityTokens.clear();
            if (!publisherIdentityTokens.isEmpty() ||
                    publisherCapture != null && publisherCapture.hasGraphReferences())
                throw new IllegalStateException("P07 source graph references survived owner cleanup");
            ownerSession = null;
            generator = null;
            if (sink != null && !sink.isClosed())
                throw new IllegalStateException("Data receipt remained open during owner cleanup");
        }

        private String headerJson() {
            JsonObject arm = new JsonObject();
            arm.string("id", ARM_IDS[operation]);
            arm.string("api", ARM_APIS[operation]);
            arm.string("strategy", ARM_STRATEGIES[operation]);
            arm.string("netOrderStrategy", ARM_NET_ORDER_STRATEGIES[operation]);
            arm.bool("observed", operation != 0);
            arm.bool("publisherCapture", operation == 4);
            JsonObject candidate = new JsonObject();
            candidate.string("rootSeed", Long.toString(candidateHeader.rootSeed));
            candidate.string("placementSeed", Long.toString(candidateHeader.placementSeed));
            candidate.string("routingSeed", Long.toString(candidateHeader.routingSeed));
            candidate.string("variant", candidateHeader.variant);
            candidate.number("layoutVersion", 26);
            candidate.string("dependencyEpoch", DEPENDENCY_EPOCH);
            candidate.number("componentCount", candidateHeader.componentCount);
            candidate.number("padCount", candidateHeader.padCount);
            candidate.number("netCount", candidateHeader.netCount);
            candidate.string("traceWidthProfile", "ROLE_WIDTHS");
            JsonObject limits = new JsonObject();
            limits.number("operationNanos", OPERATION_LIMIT_NANOS);
            limits.number("groups", GROUP_LIMIT);
            limits.number("groupNanos", GROUP_LIMIT_NANOS);
            limits.number("p07Steps", STEP_LIMIT);
            limits.number("receiptBytes", RECEIPT_LIMIT_BYTES);
            limits.number("lineBytes", LINE_LIMIT_BYTES);
            limits.number("padRows", PAD_LIMIT);
            limits.number("prefixBytes", PREFIX_LIMIT_BYTES);
            limits.number("viaCost", 60);
            limits.number("holeCap", 48);
            limits.number("transitionCap", 4);
            limits.number("orderings", 3);
            limits.number("requestExpansions", 1000000);
            limits.number("branchExpansions", 100000);
            limits.number("cells", 250000);
            limits.number("pathNodes", PUBLISHER_PATH_NODE_LIMIT);
            limits.number("snapshotEntries", PUBLISHER_SYNC_COPY_LIMIT);
            limits.number("publisherPayloadBytes", PUBLISHER_PAYLOAD_LIMIT);
            limits.number("publisherChunkBytes", PUBLISHER_CHUNK_BYTES);
            JsonObject header = new JsonObject();
            header.string("record", "HEADER");
            header.number("group", 0);
            header.string("schema", SCHEMA);
            header.string("runId", study.runId);
            header.string("mode", study.mode.reportMode());
            header.number("operation", operation);
            header.raw("arm", arm.finish());
            header.string("sourceManifestSha256", study.sourceManifestSha256);
            header.string("wireSha256", study.wireSha256);
            header.raw("candidate", candidate.finish());
            header.raw("limits", limits.finish());
            return header.finish();
        }

        private String resultJson(GroupLedger report, String physicalValues,
                String winnerJson) {
            JsonObject context = new JsonObject();
            context.number("requestIndex", request.context.requestIndex);
            context.number("selectedAttempt", request.context.selectedAttempt);
            context.raw("eligibleAttempts", intsJson(request.context.eligibleAttempts));
            context.string("pristineSha256", request.context.pristineSha256);
            context.number("pristineBytes", request.context.pristineBytes);
            context.string("padCensusSha256", request.context.padCensusSha256);
            context.string("boardSha256", candidateHeader.boardSha256);
            JsonObject receipt = receiptJson(request.receipt);
            JsonObject work = new JsonObject();
            work.number("generatorAdvances", request.generatorAdvances);
            work.number("p07Slices", request.p07Slices);
            work.number("p07OwnerSteps", request.p07OwnerSteps);
            work.number("diagnosticCloneSteps", request.diagnosticCloneSteps);
            long routingOwnerSteps = request.p07OwnerSteps - request.diagnosticCloneSteps;
            if (routingOwnerSteps < 0)
                throw new IllegalStateException("Verified diagnostic clone work exceeds raw P07 owner work");
            work.number("routingOwnerSteps", routingOwnerSteps);
            work.number("expansions", request.receipt.expansions);
            JsonObject boundary = new JsonObject();
            boundary.number("requestCount", 1);
            boundary.number("outcomeCount", 1);
            boundary.bool("routeRetired", true);
            boundary.bool("candidateRetired", true);
            boundary.bool("generatorComplete", false);
            boundary.bool("eventObserved", request.eventObserved);
            boundary.number("successorAdvanceCalls", 0);
            JsonObject result = new JsonObject();
            result.string("record", "RESULT");
            result.number("operation", operation);
            result.number("group", report.ordinal);
            result.string("proof", "COMPLETE");
            result.string("route", request.receipt.finalizedOrderings == 0 ?
                "TYPED_REQUEST_REJECTION" : "REQUEST_FINALIZED");
            result.raw("context", context.finish());
            result.raw("receipt", receipt.finish());
            result.string("outcome", request.routeOutcome);
            result.raw("winner", winnerJson);
            result.raw("physical", "{" + physicalValues + "}");
            result.raw("logicalWork", work.finish());
            result.raw("boundary", boundary.finish());
            return result.finish();
        }

        private Object publisherTargetValue(final PublisherCapture capture,
                MetadataCopyLedger metadataCopies) {
            if (capture.payloadBytes != 0 || capture.payloadChunkIndex != 0 ||
                    capture.payloadSha256 != null || !capture.sourceReferencesReleased ||
                    !capture.ownerBindingVerified || !capture.immutablePathVerified ||
                    !capture.staticGeometryEqual || !capture.prefixRowsConserved)
                throw new IllegalStateException("P07 Publisher target closed before its evidence and references");
            StreamObject context = new StreamObject(
                new String[] {"sourceManifestSha256","operation","rootSeed","placementSeed",
                    "routingSeed","attempt","request","pass","placementSha256"},
                new Object[] {study.sourceManifestSha256, Integer.valueOf(operation),
                    Long.toString(candidateHeader.rootSeed), Long.toString(candidateHeader.placementSeed),
                    Long.toString(candidateHeader.routingSeed), Integer.valueOf(capturedContext.selectedAttempt),
                    Integer.valueOf(capturedContext.requestIndex), Integer.valueOf(capture.pass),
                    capturedContext.pristineSha256}, metadataCopies);
            StreamObject objects = new StreamObject(
                new String[] {"generator","route","search","branch","layout","publisher","found","terminal"},
                new Object[] {Integer.valueOf(capture.generatorToken), Integer.valueOf(capture.routeToken),
                    Integer.valueOf(capture.searchToken), Integer.valueOf(capture.branchToken),
                    Integer.valueOf(capture.layoutToken), Integer.valueOf(capture.publisherToken),
                    Integer.valueOf(capture.foundToken), capture.terminalToken}, metadataCopies);
            StreamObject branch = new StreamObject(
                new String[] {"ordinal","netVectorPosition","netId","startPadId","rootPadId",
                    "reachedPadIds","foundTerminalPadId","branchRecordGroup"},
                new Object[] {Integer.valueOf(capture.ordinal), Integer.valueOf(capture.netVectorPosition),
                    capture.netId, capture.startPadId, capture.rootPadId,
                    stringArray(capture.reachedPadIds), capture.foundTerminalPadId,
                    Integer.valueOf(capture.branchRecordGroup)}, metadataCopies);
            StreamObject grid = new StreamObject(
                new String[] {"width","height","cells","originX","originY","pitch",
                    "primaryLayer","maximumVias"},
                new Object[] {Integer.valueOf(capture.width), Integer.valueOf(capture.height),
                    Integer.valueOf(capture.cells), Integer.valueOf(capture.originX),
                    Integer.valueOf(capture.originY), Integer.valueOf(capture.pitch),
                    Integer.valueOf(capture.primaryLayer), Integer.valueOf(capture.maximumVias)},
                metadataCopies);
            StreamObject checkpoints = new StreamObject(
                new String[] {"found","publisherSeen","publisherTerminal","orderingReady","orderingResult"},
                new Object[] {checkpointValue(capture.foundCheckpoint, metadataCopies),
                    checkpointValue(capture.publisherSeen, metadataCopies),
                    checkpointValue(capture.publisherTerminal, metadataCopies),
                    checkpointValue(capture.orderingReady, metadataCopies),
                    checkpointValue(capture.orderingResult, metadataCopies)}, metadataCopies);
            StreamArray nodes = new StreamArray(new JsonArrayAccess() {
                public int size() { return capture.pathNodes.size(); }
                public Object value(int index) {
                    final int[] tuple = capture.pathNodes.get(index).tuple;
                    return new StreamArray(new JsonArrayAccess() {
                        public int size() { return 11; }
                        public Object value(int item) { return Integer.valueOf(tuple[item]); }
                    });
                }
            });
            StreamArray nodeTokens = new StreamArray(new JsonArrayAccess() {
                public int size() { return capture.pathNodes.size(); }
                public Object value(int index) {
                    return Integer.valueOf(capture.pathNodes.get(index).nodeToken);
                }
            });
            StreamObject path = new StreamObject(
                new String[] {"encoding","nodes","nodeTokens","foundGoal"},
                new Object[] {"cell,layer,x,y,direction,transitions,cost,heuristic,serial,key,parentRow",
                    nodes, nodeTokens, Boolean.valueOf(capture.foundGoal)}, metadataCopies);
            StreamObject publisher = new StreamObject(
                new String[] {"outcome","stage","qualityIndex","reversedSize","storedLength",
                    "storedBends","storedLastDirection","segments","reason","sourceHandoff",
                    "startReachedAfter"},
                new Object[] {capture.outcome, Integer.valueOf(capture.stage),
                    Integer.valueOf(capture.qualityIndex), Integer.valueOf(capture.reversedSize),
                    Integer.valueOf(capture.storedLength), Integer.valueOf(capture.storedBends),
                    Integer.valueOf(capture.storedLastDirection), Integer.valueOf(capture.segments),
                    capture.reason, Boolean.valueOf(capture.sourceHandoff),
                    Boolean.valueOf(capture.startReachedAfter)}, metadataCopies);
            Object readyLayout = "READY".equals(capture.routerStatus) ?
                Integer.valueOf(capture.readyLayoutToken) : null;
            Object readyPass = "READY".equals(capture.routerStatus) ?
                Integer.valueOf(capture.readyPass) : null;
            StreamObject ordering = new StreamObject(
                new String[] {"routerStatus","routerReason","readyLayoutToken","readyPass",
                    "finalizerStatus","finalizedBefore","finalizedAfter","lastFailureDetail"},
                new Object[] {capture.routerStatus, capture.routerReason, readyLayout, readyPass,
                    capture.finalizerStatus, Integer.valueOf(capture.finalizedBefore),
                    Integer.valueOf(capture.finalizedAfter), capture.lastFailureDetail}, metadataCopies);
            StreamObject before = geometryValue(capture.before, metadataCopies);
            StreamObject after = geometryValue(capture.after, metadataCopies);
            StreamObject integrity = new StreamObject(
                new String[] {"ownerBindingVerified","immutablePathVerified","staticGeometryEqual",
                    "prefixRowsConserved","sourceReferencesReleased"},
                new Object[] {Boolean.valueOf(capture.ownerBindingVerified),
                    Boolean.valueOf(capture.immutablePathVerified),
                    Boolean.valueOf(capture.staticGeometryEqual),
                    Boolean.valueOf(capture.prefixRowsConserved),
                    Boolean.valueOf(capture.sourceReferencesReleased)}, metadataCopies);
            StreamObject limitations = new StreamObject(
                new String[] {"routeAdoption","layoutQualification","playable100","fullPhysicalProof"},
                new Object[] {Boolean.FALSE, Boolean.FALSE, Integer.valueOf(0), "NOT_RUN"},
                metadataCopies);
            return new StreamObject(
                new String[] {"schema","context","objects","branch","grid","checkpoints","path",
                    "publisher","ordering","before","after","integrity","limitations"},
                new Object[] {"p07-publisher-target-v1", context, objects, branch, grid, checkpoints,
                    path, publisher, ordering, before, after, integrity, limitations}, metadataCopies);
        }

        private StreamObject geometryValue(final GeometryCapture geometry,
                MetadataCopyLedger metadataCopies) {
            StreamArray traces = new StreamArray(new JsonArrayAccess() {
                public int size() { return geometry.traces.size(); }
                public Object value(int index) {
                    final TraceCapture row = geometry.traces.get(index);
                    return new StreamArray(new JsonArrayAccess() {
                        public int size() { return 9; }
                        public Object value(int item) {
                            switch (item) {
                            case 0: return row.sourceId;
                            case 1: return row.netId;
                            case 2: return row.startPadId;
                            case 3: return row.endPadId;
                            case 4: return Integer.valueOf(row.layer);
                            case 5: return row.exposure;
                            case 6: return Integer.valueOf(row.width);
                            case 7: return intArray(row.xPoints);
                            default: return intArray(row.yPoints);
                            }
                        }
                    });
                }
            });
            StreamArray holes = new StreamArray(new JsonArrayAccess() {
                public int size() { return geometry.holes.size(); }
                public Object value(int index) {
                    final HoleCapture row = geometry.holes.get(index);
                    return new StreamArray(new JsonArrayAccess() {
                        public int size() { return 8; }
                        public Object value(int item) {
                            switch (item) {
                            case 0: return row.id;
                            case 1: return row.kind;
                            case 2: return row.netId;
                            case 3: return Integer.valueOf(row.x);
                            case 4: return Integer.valueOf(row.y);
                            case 5: return Integer.valueOf(row.drillRadius);
                            case 6: return Integer.valueOf(row.landRadius);
                            default: return row.exposure;
                            }
                        }
                    });
                }
            });
            return new StreamObject(
                new String[] {"staticPrefix","traces","holes","geometrySha256","geometryBytes"},
                new Object[] {new StreamString(geometry.sourceFingerprint, geometry.staticPrefixLength),
                    traces, holes, geometry.geometrySha256, Integer.valueOf(geometry.geometryBytes)},
                metadataCopies);
        }

        private StreamObject checkpointValue(CaptureCheckpoint checkpoint,
                MetadataCopyLedger metadataCopies) {
            if (checkpoint == null) return null;
            return new StreamObject(new String[] {"group","sequence"},
                new Object[] {Integer.valueOf(checkpoint.group), Integer.valueOf(checkpoint.sequence)},
                metadataCopies);
        }

        private void finishReportGroupAndFooter(GroupLedger report) throws IOException {
            if (padCount != candidateHeader.padCount || request == null)
                throw new IllegalStateException("Final report census is incomplete");
            if (netOrderRecordCount < 0 || netOrderRecordCount > 1 ||
                    netOrderCaptured != (netOrderRecordCount == 1) ||
                    (operation == 0 && netOrderRecordCount != 0) ||
                    (pass1TerminalCount > 0 && netOrderRecordCount != 1))
                throw new IllegalStateException("NET_ORDER event count does not close against the actual pass-1 trace");
            long beforeOwnLine = elapsed(report.startedNanos, System.nanoTime());
            sink.writeLine(groupJson(report, beforeOwnLine));
            sink.flush();
            JsonObject footer = new JsonObject();
            footer.string("record", "FOOTER");
            footer.number("group", report.ordinal);
            footer.number("operation", operation);
            footer.bool("complete", true);
            footer.number("padCount", padCount);
            footer.number("branchCount", branchCount);
            footer.number("groupCount", nextGroupOrdinal);
            sink.writeLine(footer.finish());
            sink.flush();
            sink.closeComplete();
            long fullGroup = elapsed(report.startedNanos, System.nanoTime());
            maxFullGroupNanos = Math.max(maxFullGroupNanos, fullGroup);
            if (fullGroup > GROUP_LIMIT_NANOS)
                throw new IOException("Final REPORT group including footer and receipt close exceeded five seconds");
            checkOperationDeadline();
        }

        private GroupLedger newGroup(String phase, long started) {
            checkOperationDeadline();
            if (nextGroupOrdinal >= GROUP_LIMIT)
                throw new IllegalStateException("Actual study exceeded 640 total groups");
            return new GroupLedger(nextGroupOrdinal++, phase, started);
        }

        private void finishGroup(GroupLedger group) throws IOException {
            if (group == null || group != currentGroup)
                throw new IllegalStateException("GROUP ledger does not own its records");
            if (group.netOrderRows < 0 || group.netOrderRows > 1 ||
                    (group.netOrderRows > 0 && (operation == 0 || !"ADVANCE".equals(group.phase) ||
                        group.p07Slices != 1 || group.observerChecks < 1)))
                throw new IllegalStateException("NET_ORDER row lacks its measured ADVANCE group owner");
            if (group.diagnosticCloneSteps < 0 ||
                    (group.diagnosticCloneSteps > 0 && (group.p07Slices != 1 ||
                        group.p07Steps == null || group.diagnosticCloneSteps > group.p07Steps.intValue())))
                throw new IllegalStateException("Group diagnostic clone units do not reconcile to measured raw P07 work");
            if (group.publisherChunkRows < 0 || group.publisherResultRows < 0 ||
                    group.diagnosticCopiedEntries < 0 || group.diagnosticSerializedBytes < 0 ||
                    group.diagnosticHashBytes < 0 ||
                    group.diagnosticUnits() > Long.MAX_VALUE ||
                    (group.publisherChunkRows > 0 || group.publisherResultRows > 0) && operation != 4)
                throw new IllegalStateException("P07 Publisher diagnostic GROUP accounting is invalid");
            if ("CAPTURE".equals(group.phase) &&
                    (group.p07Slices != 0 || group.p07Steps != null ||
                        group.diagnosticUnits() > DIAGNOSTIC_GROUP_UNITS))
                throw new IllegalStateException("P07 CAPTURE group exceeded its owner-free 32768-unit bound");
            long beforeOwnLine = elapsed(group.startedNanos, System.nanoTime());
            sink.writeLine(groupJson(group, beforeOwnLine));
            sink.flush();
            long fullGroup = elapsed(group.startedNanos, System.nanoTime());
            maxFullGroupNanos = Math.max(maxFullGroupNanos, fullGroup);
            if (fullGroup > GROUP_LIMIT_NANOS)
                throw new IOException("GROUP write/flush exceeded five seconds");
            checkOperationDeadline();
        }

        private void checkOperationDeadline() {
            long elapsedNanos = elapsed(operationStarted, System.nanoTime());
            if (elapsedNanos <= 0 || elapsedNanos > OPERATION_LIMIT_NANOS)
                throw new IllegalStateException("Arm exceeded the 90 second operation clock");
        }

        private String groupJson(GroupLedger group, long beforeOwnLine) {
            JsonObject row = new JsonObject();
            row.string("record", "GROUP");
            row.number("operation", operation);
            row.number("ordinal", group.ordinal);
            row.string("phase", group.phase);
            row.number("elapsedBeforeOwnLineNanos", beforeOwnLine);
            row.number("generatorAdvances", group.generatorAdvances);
            row.number("p07Slices", group.p07Slices);
            if (group.p07Slices == 1) row.number("p07Steps", group.p07Steps.intValue());
            else row.raw("p07Steps", "null");
            row.number("diagnosticCloneSteps", group.diagnosticCloneSteps);
            row.string("accounting", group.p07Slices == 1 ? "MEASURED" : "NOT_APPLICABLE");
            row.number("observerChecks", group.observerChecks);
            row.number("callbacks", group.callbacks);
            row.number("netOrderRows", group.netOrderRows);
            row.number("censusRows", group.censusRows);
            row.number("publisherChunkRows", group.publisherChunkRows);
            row.number("publisherResultRows", group.publisherResultRows);
            row.number("diagnosticCopiedEntries", group.diagnosticCopiedEntries);
            row.number("diagnosticSerializedBytes", group.diagnosticSerializedBytes);
            row.number("diagnosticHashBytes", group.diagnosticHashBytes);
            row.number("diagnosticUnits", group.diagnosticUnits());
            row.number("hashInputBytes", group.hashInputBytes);
            row.number("serializedBeforeOwnLineBytes", group.serializedBeforeOwnLineBytes);
            return row.finish();
        }
        private void appendRecord(GroupLedger group, String json) throws IOException {
            if (group == null || group != currentGroup)
                throw new IllegalStateException("JSONL record has no current owner group");
            group.serializedBeforeOwnLineBytes += sink.writeLine(json);
        }

        private void appendPublisherRecord(GroupLedger group, String json, boolean chunk)
                throws IOException {
            if (operation != 4 || group == null || group != currentGroup ||
                    !"CAPTURE".equals(group.phase))
                throw new IllegalStateException("P07 Publisher record has no CAPTURE group owner");
            int bytes = sink.writeLine(json);
            group.serializedBeforeOwnLineBytes += bytes;
            group.diagnosticSerializedBytes += bytes;
            group.diagnosticHashBytes += bytes;
            group.hashInputBytes += bytes;
            if (chunk) group.publisherChunkRows++;
            else group.publisherResultRows++;
        }

        private void appendCallbackRecord(GroupLedger group, String json) {
            try {
                appendRecord(group, json);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not write a live owner observation", failure);
            }
        }

        private void addHashInput(GroupLedger group, int bytes) {
            if (group == null || bytes < 0 || group.hashInputBytes > Long.MAX_VALUE - bytes)
                throw new IllegalStateException("Invalid hash-input accounting");
            group.hashInputBytes += bytes;
        }
    }

    private static final class GroupLedger {
        final int ordinal;
        final String phase;
        final long startedNanos;
        long generatorAdvances;
        long p07Slices;
        Integer p07Steps;
        long diagnosticCloneSteps;
        long observerChecks;
        long callbacks;
        long netOrderRows;
        long censusRows;
        long publisherChunkRows;
        long publisherResultRows;
        long diagnosticCopiedEntries;
        long diagnosticSerializedBytes;
        long diagnosticHashBytes;
        long hashInputBytes;
        long serializedBeforeOwnLineBytes;
        Object routeForSlice;
        GroupLedger(int ordinal, String phase, long startedNanos) {
            this.ordinal = ordinal;
            this.phase = phase;
            this.startedNanos = startedNanos;
        }
        long diagnosticUnits() {
            if (diagnosticCopiedEntries < 0 || diagnosticSerializedBytes < 0 ||
                    diagnosticHashBytes < 0 ||
                    diagnosticCopiedEntries > Long.MAX_VALUE - diagnosticSerializedBytes ||
                    diagnosticCopiedEntries + diagnosticSerializedBytes > Long.MAX_VALUE - diagnosticHashBytes)
                throw new IllegalStateException("P07 diagnostic unit sum overflowed");
            return diagnosticCopiedEntries + diagnosticSerializedBytes + diagnosticHashBytes;
        }
    }

    private static final class CaptureCheckpoint {
        final int group;
        final int sequence;
        CaptureCheckpoint(int group, int sequence) {
            if (group < 1 || sequence < 1)
                throw new IllegalArgumentException("Invalid P07 Publisher checkpoint");
            this.group = group;
            this.sequence = sequence;
        }
    }

    private static final class TraceCapture {
        final String sourceId, netId, startPadId, endPadId, exposure;
        final int layer, width;
        final int[] xPoints, yPoints;
        TraceCapture(String sourceId, String netId, String startPadId, String endPadId,
                int layer, String exposure, int width, int[] xPoints, int[] yPoints) {
            this.sourceId = sourceId; this.netId = netId;
            this.startPadId = startPadId; this.endPadId = endPadId;
            this.layer = layer; this.exposure = exposure; this.width = width;
            this.xPoints = xPoints; this.yPoints = yPoints;
        }
    }

    private static final class HoleCapture {
        final String id, kind, netId, exposure;
        final int x, y, drillRadius, landRadius;
        HoleCapture(String id, String kind, String netId, int x, int y,
                int drillRadius, int landRadius, String exposure) {
            this.id = id; this.kind = kind; this.netId = netId;
            this.x = x; this.y = y; this.drillRadius = drillRadius;
            this.landRadius = landRadius; this.exposure = exposure;
        }
    }

    private static final class GeometryCapture {
        final String sourceFingerprint;
        final List<TraceCapture> traces;
        final List<HoleCapture> holes;
        final int copiedEntries;
        int staticPrefixLength = -1;
        int geometryBytes = -1;
        String geometrySha256;
        StringBuilder suffixBuilder;
        GeometrySuffixCursor suffixCursor;
        int suffixCompareIndex;
        boolean suffixBuilt, suffixVerified;
        GeometryCapture(String sourceFingerprint, List<TraceCapture> traces,
                List<HoleCapture> holes, int copiedEntries) {
            this.sourceFingerprint = sourceFingerprint;
            this.traces = traces; this.holes = holes;
            this.copiedEntries = copiedEntries;
        }
    }

    private static final class GeometrySuffixCursor {
        private final GeometryCapture geometry;
        private int traceIndex, tracePart, pointIndex;
        private int holeIndex, holePart;
        private String part;
        private int partOffset;
        GeometrySuffixCursor(GeometryCapture geometry) { this.geometry = geometry; }
        String nextChunk(int maxChars) {
            if (maxChars < 1) return "";
            StringBuilder result = new StringBuilder(maxChars);
            while (result.length() < maxChars) {
                if (part == null || partOffset >= part.length()) {
                    part = nextPart();
                    partOffset = 0;
                    if (part == null) break;
                    if (part.length() == 0) continue;
                }
                int count = Math.min(maxChars - result.length(), part.length() - partOffset);
                result.append(part, partOffset, partOffset + count);
                partOffset += count;
            }
            return result.toString();
        }
        boolean isDone() {
            return (part == null || partOffset >= part.length()) &&
                traceIndex >= geometry.traces.size() && holeIndex >= geometry.holes.size();
        }
        private String nextPart() {
            while (traceIndex < geometry.traces.size()) {
                TraceCapture row = geometry.traces.get(traceIndex);
                switch (tracePart) {
                case 0: tracePart++; return "T:";
                case 1: tracePart++; return fingerprintField(row.sourceId);
                case 2: tracePart++; return PcbCopperLayer.values()[row.layer].toString();
                case 3: tracePart++; return ":";
                case 4: tracePart++; return row.exposure;
                case 5: tracePart++; return ":";
                case 6: tracePart++; return String.valueOf(row.netId);
                case 7: tracePart++; return ":";
                case 8: tracePart++; return String.valueOf(row.startPadId);
                case 9: tracePart++; return "-";
                case 10: tracePart++; return String.valueOf(row.endPadId);
                case 11: tracePart++; return "@";
                case 12:
                    tracePart++;
                    return row.width == PcbTraceRules.TRACE_WIDTH ?
                        "" : "W" + row.width + ":";
                default:
                    if (pointIndex < row.xPoints.length) {
                        int index = pointIndex++;
                        return row.xPoints[index] + "," + row.yPoints[index] + ";";
                    }
                    traceIndex++; tracePart=0; pointIndex=0;
                    break;
                }
            }
            while (holeIndex < geometry.holes.size()) {
                HoleCapture row = geometry.holes.get(holeIndex);
                switch (holePart) {
                case 0: holePart++; return "H:";
                case 1: holePart++; return fingerprintField(row.id);
                case 2: holePart++; return row.kind;
                case 3: holePart++; return fingerprintField(row.netId);
                default:
                    holeIndex++; holePart=0;
                    return row.x + "," + row.y + "," + row.drillRadius + "," +
                        row.landRadius + "," + row.exposure;
                }
            }
            return null;
        }
    }

    private static String fingerprintField(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static final class Utf8HashCursor {
        private final String value;
        private final MessageDigest digest;
        private int charIndex, byteCount, pendingOffset;
        private byte[] pending;
        private String result;
        Utf8HashCursor(String value, MessageDigest digest) {
            if (value == null || digest == null) throw new IllegalArgumentException("Missing UTF8 hash input");
            this.value = value; this.digest = digest;
        }
        int advance(int maxBytes) {
            if (maxBytes < 0 || result != null) throw new IllegalArgumentException("Invalid UTF8 hash budget");
            int updated = 0;
            while (updated < maxBytes) {
                if (pending == null || pendingOffset == pending.length) {
                    pending = null; pendingOffset = 0;
                    if (charIndex >= value.length()) break;
                    int current = value.charAt(charIndex++);
                    if (current < 0x80) pending = new byte[] {(byte)current};
                    else if (current < 0x800)
                        pending = new byte[] {(byte)(0xc0 | (current >> 6)),
                            (byte)(0x80 | (current & 0x3f))};
                    else if (Character.isHighSurrogate((char)current)) {
                        if (charIndex >= value.length() || !Character.isLowSurrogate(value.charAt(charIndex)))
                            throw new IllegalArgumentException("Unpaired high surrogate in P07 geometry");
                        int codePoint = Character.toCodePoint((char)current, value.charAt(charIndex++));
                        pending = new byte[] {(byte)(0xf0 | (codePoint >> 18)),
                            (byte)(0x80 | ((codePoint >> 12) & 0x3f)),
                            (byte)(0x80 | ((codePoint >> 6) & 0x3f)),
                            (byte)(0x80 | (codePoint & 0x3f))};
                    } else if (Character.isLowSurrogate((char)current))
                        throw new IllegalArgumentException("Unpaired low surrogate in P07 geometry");
                    else pending = new byte[] {(byte)(0xe0 | (current >> 12)),
                        (byte)(0x80 | ((current >> 6) & 0x3f)),
                        (byte)(0x80 | (current & 0x3f))};
                }
                int count = Math.min(maxBytes - updated, pending.length - pendingOffset);
                digest.update(pending, pendingOffset, count);
                pendingOffset += count; updated += count; byteCount += count;
            }
            return updated;
        }
        boolean isDone() {
            return charIndex >= value.length() && (pending == null || pendingOffset == pending.length);
        }
        String finishSha256() {
            if (!isDone() || result != null) throw new IllegalStateException("UTF8 hash is not terminal");
            result = hex(digest.digest());
            return result;
        }
        int byteCount() { return byteCount; }
    }

    private static final class PathCapture {
        final int[] tuple;
        final int nodeToken;
        PathCapture(int[] tuple, int nodeToken) {
            this.tuple = tuple; this.nodeToken = nodeToken;
        }
    }

    private static final class PublisherCapture {
        final int generatorToken, routeToken, searchToken, branchToken, layoutToken, foundToken;
        final Integer terminalToken;
        final int foundGroup, foundSequence, pass, ordinal, netVectorPosition;
        final String netId, startPadId, rootPadId;
        final String[] reachedPadIds;
        final String foundTerminalPadId;
        final int branchRecordGroup;
        final int width, height, cells, originX, originY, pitch, primaryLayer, maximumVias;
        final int startX, startY, rootX, rootY, traceWidth;
        final int foundGridIndex, startSegments, searchBranchCount;
        final boolean foundGoal;
        final GeometryCapture before;
        final String beforeSha256;
        final int beforeBytes;
        final List<PathCapture> pathNodes = new ArrayList<PathCapture>();
        final List<Object> pathNodeRefs = new ArrayList<Object>();
        final List<TraceCapture> expectedDeltaTraces = new ArrayList<TraceCapture>();
        final List<HoleCapture> expectedDeltaHoles = new ArrayList<HoleCapture>();
        final IdentityHashMap<Object, Boolean> pathSeen = new IdentityHashMap<Object, Boolean>();
        Object generatorOwner, route, search, branch, found, terminal, publisher, foundCursor;
        PcbBoardLayout layout;
        Vector<?> reachedVector;
        int publisherToken;
        String outcome, reason, routerStatus, routerReason, finalizerStatus, lastFailureDetail;
        int stage=-1, qualityIndex=-1, reversedSize=-1, storedLength=-1, storedBends=-1,
            storedLastDirection=-1, segments=-1, finalizedBefore=-1, finalizedAfter=-1,
            readyPass=-1, readyLayoutToken=-1;
        boolean sourceHandoff, startReachedAfter, ownerBindingVerified,
            immutablePathVerified, staticGeometryEqual, prefixRowsConserved,
            sourceReferencesReleased;
        GeometryCapture after;
        CaptureCheckpoint foundCheckpoint, publisherSeen, publisherTerminal,
            orderingReady, orderingResult;
        boolean pathCopyComplete, pathReversed;
        boolean pathAuditComplete, geometryDeltaComplete, geometryDeltaExpectedBuilt;
        int pathReverseIndex, pathParentIndex;
        int pathVerifyIndex=0;
        int pathAuditIndex, pathAuditLength, pathAuditBends, pathAuditTransitions;
        int pathAuditLastDirection=4, pathAuditPriorCell=-1, pathAuditPriorLayer=-1;
        int pathAuditPriorX, pathAuditPriorY, pathAuditPriorTransitions;
        int payloadChunkIndex, payloadOffset, payloadBytes;
        String payloadSha256;

        PublisherCapture(int generatorToken, int routeToken, int searchToken,
                int branchToken, int layoutToken, int foundToken, Integer terminalToken,
                int foundGroup, int foundSequence, int pass, int ordinal,
                int netVectorPosition, String netId, String startPadId,
                String rootPadId, String[] reachedPadIds, String foundTerminalPadId,
                int branchRecordGroup, int width, int height, int cells,
                int originX, int originY, int pitch, int primaryLayer, int maximumVias,
                int startX, int startY, int rootX, int rootY, int traceWidth,
                int foundGridIndex, int startSegments, boolean foundGoal,
                Vector<?> reachedVector, Object generatorOwner, Object branch, Object search, Object route,
                PcbBoardLayout layout, Object found, Object terminal, GeometryCapture before,
                String beforeSha256, int beforeBytes, int searchBranchCount) {
            this.generatorToken=generatorToken; this.routeToken=routeToken;
            this.searchToken=searchToken; this.branchToken=branchToken;
            this.layoutToken=layoutToken; this.foundToken=foundToken;
            this.terminalToken=terminalToken; this.foundGroup=foundGroup;
            this.foundSequence=foundSequence; this.pass=pass; this.ordinal=ordinal;
            this.netVectorPosition=netVectorPosition; this.netId=netId;
            this.startPadId=startPadId; this.rootPadId=rootPadId;
            this.reachedPadIds=reachedPadIds; this.foundTerminalPadId=foundTerminalPadId;
            this.branchRecordGroup=branchRecordGroup; this.width=width;
            this.height=height; this.cells=cells; this.originX=originX;
            this.originY=originY; this.pitch=pitch; this.primaryLayer=primaryLayer;
            this.maximumVias=maximumVias; this.foundGridIndex=foundGridIndex;
            this.startX=startX; this.startY=startY; this.rootX=rootX; this.rootY=rootY;
            this.traceWidth=traceWidth;
            this.startSegments=startSegments; this.searchBranchCount=searchBranchCount;
            this.foundGoal=foundGoal;
            this.reachedVector=reachedVector; this.generatorOwner=generatorOwner;
            this.branch=branch; this.search=search;
            this.route=route; this.layout=layout; this.found=found;
            this.terminal=terminal; this.before=before; this.beforeSha256=beforeSha256;
            this.beforeBytes=beforeBytes; this.foundCursor=found;
            this.foundCheckpoint=new CaptureCheckpoint(foundGroup, foundSequence);
            this.ownerBindingVerified=false;
        }
        void releaseGraphReferences() {
            generatorOwner=null; route=null; search=null; branch=null; layout=null; found=null;
            terminal=null; publisher=null; foundCursor=null; reachedVector=null;
            pathNodeRefs.clear(); pathSeen.clear();
        }
        boolean hasGraphReferences() {
            return generatorOwner != null || route != null || search != null || branch != null || layout != null ||
                found != null || terminal != null || publisher != null || foundCursor != null ||
                reachedVector != null || !pathNodeRefs.isEmpty() || !pathSeen.isEmpty();
        }
    }

    private static final class BranchTracking {
        final Object branch;
        final int pass;
        final String netId;
        final String startPadId;
        final String rootPadId;
        final int prefixViaCount;
        final Object[] pristineRouters = new Object[2];
        int nullObservedMask;
        int transitionMask;
        int eventMask;
        BranchTracking(Object branch, int pass, String netId, String startPadId,
                String rootPadId, int prefixViaCount) {
            this.branch = branch;
            this.pass = pass;
            this.netId = netId;
            this.startPadId = startPadId;
            this.rootPadId = rootPadId;
            this.prefixViaCount = prefixViaCount;
        }
        String identityKey() {
            return pass + "\t" + netId + "\t" + startPadId + "\t" + rootPadId + "\t" + prefixViaCount;
        }
        void releasePristineRouters() {
            pristineRouters[0] = null;
            pristineRouters[1] = null;
        }
        void release() { releasePristineRouters(); }
    }
    private static final class CandidateHeader {
        final long rootSeed;
        final long placementSeed;
        final long routingSeed;
        final String variant;
        final int componentCount;
        final int padCount;
        final int netCount;
        String boardSha256;
        CandidateHeader(long rootSeed, long placementSeed, long routingSeed, String variant,
                TroubleshootBoard board) {
            this.rootSeed = rootSeed;
            this.placementSeed = placementSeed;
            this.routingSeed = routingSeed;
            this.variant = variant;
            componentCount = board.getComponentIds().size();
            padCount = board.getPadIds().size();
            netCount = board.getNetIds().size();
        }
        static CandidateHeader tiny(Rb56Plan plan, TroubleshootBoard board) {
            if (plan == null || plan.seed != TINY_ROOT ||
                    plan.placementInterpretationVersion != TINY_PLACEMENT_VERSION)
                throw new IllegalStateException("Tiny plan is not saved root-42 interpretation 12");
            return new CandidateHeader(plan.seed, plan.layoutSeed, plan.routingSeed, "TINY", board);
        }
        static CandidateHeader authored(Rb100Plan plan, TroubleshootBoard board) {
            if (plan == null || plan.seed != AUTHORED_ROOT ||
                    plan.variant != Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING)
                throw new IllegalStateException("Authored plan is not the frozen separate-direct-switching variant");
            long placement = ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID,
                AUTHORED_ROOT, NamedRandomStreams.Concern.PLACEMENT);
            long routing = ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID,
                AUTHORED_ROOT, NamedRandomStreams.Concern.ROUTING);
            if (placement != AUTHORED_PLACEMENT || routing != AUTHORED_ROUTING)
                throw new IllegalStateException("Authored named placement/routing seed identity changed");
            return new CandidateHeader(plan.seed, placement, routing, "SEPARATE", board);
        }
    }

    private static final class PadRow {
        final String padId;
        final String netId;
        final String componentId;
        final int x;
        final int y;
        final String role;
        PadRow(String padId, String netId, String componentId, int x, int y, String role) {
            if (!token(padId) || !token(netId) || !token(componentId) || !token(role))
                throw new IllegalArgumentException("Actual PAD token is outside the frozen ASCII domain");
            this.padId = padId;
            this.netId = netId;
            this.componentId = componentId;
            this.x = x;
            this.y = y;
            this.role = role;
        }
        String toJson(int group) {
            JsonObject row = new JsonObject();
            row.string("record", "PAD");
            row.number("group", group);
            row.string("padId", padId);
            row.string("netId", netId);
            row.string("componentId", componentId);
            row.number("x", x);
            row.number("y", y);
            row.string("role", role);
            return row.finish();
        }
    }

    private static final class CapturedContext {
        final int requestIndex;
        final int selectedAttempt;
        final int[] eligibleAttempts;
        final String pristineGeometry;
        final String pristineSha256;
        final int pristineBytes;
        final String padCensusSha256;
        CapturedContext(int requestIndex, int selectedAttempt, int[] eligibleAttempts,
                String pristineGeometry, String pristineSha256, int pristineBytes,
                String padCensusSha256) {
            this.requestIndex = requestIndex;
            this.selectedAttempt = selectedAttempt;
            this.eligibleAttempts = eligibleAttempts.clone();
            this.pristineGeometry = pristineGeometry;
            this.pristineSha256 = pristineSha256;
            this.pristineBytes = pristineBytes;
            this.padCensusSha256 = padCensusSha256;
        }
    }

    private static final class RequestObservation {
        final MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt;
        final String routeOutcome;
        final CapturedContext context;
        final boolean eventObserved;
        final long generatorAdvances;
        final long p07Slices;
        final long p07OwnerSteps;
        final long diagnosticCloneSteps;
        RequestObservation(MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt,
                String routeOutcome, CapturedContext context, boolean eventObserved,
                long generatorAdvances, long p07Slices, long p07OwnerSteps,
                long diagnosticCloneSteps) {
            this.receipt = receipt;
            this.routeOutcome = routeOutcome;
            this.context = context;
            this.eventObserved = eventObserved;
            this.generatorAdvances = generatorAdvances;
            this.p07Slices = p07Slices;
            this.p07OwnerSteps = p07OwnerSteps;
            this.diagnosticCloneSteps = diagnosticCloneSteps;
        }
    }

    private static final class ArmResult {
        final long receiptBytes;
        final String receiptSha256;
        final long elapsedToCloseNanos;
        final long maxFullGroupNanos;
        long cleanupNanos;
        boolean ownersReleased;
        boolean contextRestored;
        ArmResult(long receiptBytes, String receiptSha256, long elapsedToCloseNanos,
                long maxFullGroupNanos) {
            this.receiptBytes = receiptBytes;
            this.receiptSha256 = receiptSha256;
            this.elapsedToCloseNanos = elapsedToCloseNanos;
            this.maxFullGroupNanos = maxFullGroupNanos;
        }
    }
    private static final class JsonlSink {
        private final OutputStream output;
        private final MessageDigest digest;
        private final long priorStudyBytes;
        private long bytes;
        private boolean closed;
        private String sha256;
        JsonlSink(Path path, long priorStudyBytes) throws IOException {
            this.priorStudyBytes = priorStudyBytes;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
            output = new BufferedOutputStream(Files.newOutputStream(path,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        }
        int writeLine(String line) throws IOException {
            if (closed) throw new IOException("JSONL receipt is already closed");
            if (line == null || line.length() == 0 || line.indexOf('\r') >= 0 ||
                    line.indexOf('\n') >= 0)
                throw new IOException("JSONL record is blank or contains a raw line break");
            byte[] raw = utf8(line + "\n");
            if (raw.length > LINE_LIMIT_BYTES)
                throw new IOException("JSONL line exceeded 64 KiB");
            if (bytes > RECEIPT_LIMIT_BYTES - raw.length)
                throw new IOException("Per-arm JSONL receipt exceeded 8 MiB");
            if (priorStudyBytes > STUDY_LIMIT_BYTES - bytes - raw.length)
                throw new IOException("Four-arm JSONL receipts exceeded 32 MiB");
            output.write(raw);
            digest.update(raw);
            bytes += raw.length;
            return raw.length;
        }
        void flush() throws IOException {
            if (closed) throw new IOException("Cannot flush a closed JSONL receipt");
            output.flush();
        }
        void closeComplete() throws IOException {
            if (closed) throw new IOException("JSONL receipt was closed more than once");
            try {
                output.flush();
            } finally {
                output.close();
                closed = true;
            }
            sha256 = hex(digest.digest());
        }
        void closeAfterFailure() throws IOException {
            if (closed) return;
            try {
                output.flush();
            } finally {
                output.close();
                closed = true;
            }
        }
        boolean isClosed() { return closed; }
        long byteCount() {
            if (!closed || sha256 == null) throw new IllegalStateException("Receipt did not close completely");
            return bytes;
        }
        String finishSha256() {
            if (!closed || sha256 == null) throw new IllegalStateException("Receipt hash is unavailable before close");
            return sha256;
        }
    }

    private static final class NativeContextFacade {
        private final Object context;
        private final Method activate;
        private final Method close;
        private NativeContextFacade(Object context, Method activate, Method close) {
            this.context = context;
            this.activate = activate;
            this.close = close;
        }
        static NativeContextFacade create() throws Exception {
            Class<?> type = Class.forName(
                "com.lushprojects.circuitjs1.client.Rb100PhysicalDiagnostics$NativeContext");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object context = constructor.newInstance();
            Method activate = type.getDeclaredMethod("activate");
            Method close = type.getDeclaredMethod("close", Rb100ElectricalPilot.Candidate.class);
            activate.setAccessible(true);
            close.setAccessible(true);
            return new NativeContextFacade(context, activate, close);
        }
        void activate() throws Exception { invoke(activate, context); }
        void close(Rb100ElectricalPilot.Candidate candidate) throws Exception {
            invoke(close, context, candidate);
        }
        private static void invoke(Method method, Object target, Object... args) throws Exception {
            try {
                method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                rethrow(failure.getCause());
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Could not invoke the existing native-context owner", failure);
            }
        }
    }

    private static final class JsonObject {
        private final StringBuilder value = new StringBuilder("{");
        private final Set<String> names = new HashSet<String>();
        private boolean first = true;
        void string(String name, String text) {
            field(name);
            value.append(quote(text));
        }
        void number(String name, long number) {
            field(name);
            value.append(Long.toString(number));
        }
        void bool(String name, boolean flag) {
            field(name);
            value.append(flag ? "true" : "false");
        }
        void raw(String name, String rawJson) {
            if (rawJson == null || rawJson.length() == 0)
                throw new IllegalArgumentException("Missing nested JSON value");
            field(name);
            value.append(rawJson);
        }
        String finish() { return value.append('}').toString(); }
        private void field(String name) {
            if (name == null || !names.add(name))
                throw new IllegalStateException("Duplicate or missing JSON field: " + name);
            if (!first) value.append(',');
            first = false;
            value.append(quote(name)).append(':');
        }
    }

    private interface JsonArrayAccess {
        int size();
        Object value(int index);
    }

    private static final class StreamArray {
        final JsonArrayAccess access;
        StreamArray(JsonArrayAccess access) {
            if (access == null || access.size() < 0)
                throw new IllegalArgumentException("Invalid streamed JSON array");
            this.access = access;
        }
    }

    private static final class MetadataCopyLedger {
        long entries;
        void add(int keyCount, int valueCount) {
            if (keyCount < 0 || valueCount < 0 || entries > Long.MAX_VALUE - keyCount - valueCount)
                throw new IllegalStateException("P07 target metadata copy count overflowed");
            entries += (long)keyCount + valueCount;
        }
    }

    private static final class StreamObject {
        final String[] keys;
        final Object[] values;
        StreamObject(String[] keys, Object[] values) {
            this(keys, values, null);
        }
        StreamObject(String[] keys, Object[] values, MetadataCopyLedger metadataCopies) {
            if (keys == null || values == null || keys.length != values.length)
                throw new IllegalArgumentException("Invalid streamed JSON object");
            this.keys = keys; this.values = values;
            if (metadataCopies != null) metadataCopies.add(keys.length, values.length);
        }
    }

    private static final class StreamString {
        final String value;
        final int length;
        StreamString(String value, int length) {
            if (value == null || length < 0 || length > value.length())
                throw new IllegalArgumentException("Invalid streamed JSON string slice");
            this.value = value; this.length = length;
        }
    }

    private abstract static class JsonFrame {
        abstract String advance(JsonUtf8Stream stream);
    }

    private static final class JsonUtf8Stream {
        private final ArrayList<JsonFrame> frames = new ArrayList<JsonFrame>();
        private final Object root;
        private String pendingFragment;
        private int pendingFragmentOffset;
        private boolean rootPending = true;
        private boolean terminalEmptyPending;
        private boolean done;
        JsonUtf8Stream(Object root) {
            if (root == null) throw new IllegalArgumentException("Missing streamed JSON root");
            this.root = root;
        }
        byte[] nextChunk(int maximumBytes) {
            if (maximumBytes < 1 || done)
                throw new IllegalStateException("P07 target JSON stream is already complete");
            if (terminalEmptyPending) {
                terminalEmptyPending = false;
                done = true;
                return new byte[0];
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(maximumBytes);
            while (output.size() < maximumBytes) {
                if (pendingFragment == null) {
                    pendingFragment = nextFragment();
                    pendingFragmentOffset = 0;
                    if (pendingFragment == null) {
                        if (output.size() == 0) done = true;
                        else terminalEmptyPending = true;
                        break;
                    }
                    if (pendingFragment.length() == 0) {
                        pendingFragment = null;
                        continue;
                    }
                }
                char current = pendingFragment.charAt(pendingFragmentOffset);
                int codePoint;
                int consumedChars = 1;
                if (Character.isHighSurrogate(current)) {
                    if (pendingFragmentOffset + 1 >= pendingFragment.length() ||
                            !Character.isLowSurrogate(pendingFragment.charAt(pendingFragmentOffset + 1)))
                        throw new IllegalArgumentException("Unpaired high surrogate in streamed P07 JSON");
                    codePoint = Character.toCodePoint(current,
                        pendingFragment.charAt(pendingFragmentOffset + 1));
                    consumedChars = 2;
                } else if (Character.isLowSurrogate(current)) {
                    throw new IllegalArgumentException("Unpaired low surrogate in streamed P07 JSON");
                } else {
                    codePoint = current;
                }
                int encodedBytes = codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 :
                    codePoint <= 0xffff ? 3 : 4;
                if (output.size() + encodedBytes > maximumBytes) {
                    if (output.size() == 0)
                        throw new IllegalArgumentException("UTF-8 chunk bound cannot hold the next code point");
                    break;
                }
                if (encodedBytes == 1) {
                    output.write(codePoint);
                } else if (encodedBytes == 2) {
                    output.write(0xc0 | (codePoint >>> 6));
                    output.write(0x80 | (codePoint & 0x3f));
                } else if (encodedBytes == 3) {
                    output.write(0xe0 | (codePoint >>> 12));
                    output.write(0x80 | ((codePoint >>> 6) & 0x3f));
                    output.write(0x80 | (codePoint & 0x3f));
                } else {
                    output.write(0xf0 | (codePoint >>> 18));
                    output.write(0x80 | ((codePoint >>> 12) & 0x3f));
                    output.write(0x80 | ((codePoint >>> 6) & 0x3f));
                    output.write(0x80 | (codePoint & 0x3f));
                }
                pendingFragmentOffset += consumedChars;
                if (pendingFragmentOffset == pendingFragment.length()) {
                    pendingFragment = null;
                    pendingFragmentOffset = 0;
                }
            }
            return output.toByteArray();
        }
        private String nextFragment() {
            if (rootPending) {
                rootPending = false;
                pushValue(root);
            }
            while (!frames.isEmpty()) {
                JsonFrame frame = frames.get(frames.size() - 1);
                String fragment = frame.advance(this);
                if (fragment != null) return fragment;
            }
            return null;
        }
        private void pushValue(Object value) {
            if (value instanceof StreamObject) frames.add(new ObjectFrame((StreamObject)value));
            else if (value instanceof StreamArray) frames.add(new ArrayFrame((StreamArray)value));
            else if (value instanceof StreamString) frames.add(new StringFrame((StreamString)value));
            else if (value instanceof String) frames.add(new StringFrame(new StreamString(
                (String)value, ((String)value).length())));
            else if (value == null || value instanceof Number || value instanceof Boolean)
                frames.add(new ScalarFrame(value));
            else throw new IllegalArgumentException("Unsupported streamed JSON value: " +
                value.getClass().getName());
        }
        private void popFrame() { frames.remove(frames.size() - 1); }
    }

    private static final class ObjectFrame extends JsonFrame {
        final StreamObject object;
        int index;
        boolean opened, awaitingChild, commaSent;
        ObjectFrame(StreamObject object) { this.object = object; }
        String advance(JsonUtf8Stream stream) {
            if (!opened) { opened = true; return "{"; }
            if (awaitingChild) awaitingChild = false;
            if (index >= object.keys.length) { stream.popFrame(); return "}"; }
            if (index > 0 && !commaSent) { commaSent = true; return ","; }
            commaSent = false;
            int current = index++;
            awaitingChild = true;
            stream.pushValue(object.values[current]);
            return quote(object.keys[current]) + ":";
        }
    }

    private static final class ArrayFrame extends JsonFrame {
        final StreamArray array;
        int index;
        boolean opened, awaitingChild, commaSent;
        ArrayFrame(StreamArray array) { this.array = array; }
        String advance(JsonUtf8Stream stream) {
            if (!opened) { opened = true; return "["; }
            if (awaitingChild) awaitingChild = false;
            if (index >= array.access.size()) { stream.popFrame(); return "]"; }
            if (index > 0 && !commaSent) { commaSent = true; return ","; }
            commaSent = false;
            Object value = array.access.value(index++);
            awaitingChild = true;
            stream.pushValue(value);
            return null;
        }
    }

    private static final class ScalarFrame extends JsonFrame {
        final String value;
        boolean emitted;
        ScalarFrame(Object value) { this.value = value == null ? "null" : value.toString(); }
        String advance(JsonUtf8Stream stream) {
            if (!emitted) { emitted = true; return value; }
            stream.popFrame();
            return null;
        }
    }

    private static final class StringFrame extends JsonFrame {
        private static final char[] HEX = "0123456789abcdef".toCharArray();
        final StreamString value;
        int index, state;
        StringFrame(StreamString value) { this.value = value; }
        String advance(JsonUtf8Stream stream) {
            if (state == 0) { state = 1; return "\""; }
            if (index < value.length) {
                char current = value.value.charAt(index++);
                if (current == '"' || current == '\\') return "\\" + current;
                if (current < 0x20)
                    return "\\u00" + HEX[(current >>> 4) & 15] + HEX[current & 15];
                if (Character.isHighSurrogate(current)) {
                    if (index >= value.length || !Character.isLowSurrogate(value.value.charAt(index)))
                        throw new IllegalArgumentException("Unpaired high surrogate in streamed P07 JSON");
                    char[] pair = {current, value.value.charAt(index++)};
                    return new String(pair);
                }
                if (Character.isLowSurrogate(current))
                    throw new IllegalArgumentException("Unpaired low surrogate in streamed P07 JSON");
                return String.valueOf(current);
            }
            if (state == 1) { state = 2; return "\""; }
            stream.popFrame();
            return null;
        }
    }

    private static StreamArray intArray(final int[] values) {
        return new StreamArray(new JsonArrayAccess() {
            public int size() { return values.length; }
            public Object value(int index) { return Integer.valueOf(values[index]); }
        });
    }

    private static StreamArray stringArray(final String[] values) {
        return new StreamArray(new JsonArrayAccess() {
            public int size() { return values.length; }
            public Object value(int index) { return values[index]; }
        });
    }
    private static String snapshotJson(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        if (snapshot == null) throw new IllegalStateException("Missing immutable P07 BranchSnapshot");
        JsonObject json = new JsonObject();
        for (String name : SNAPSHOT_INTS) {
            Object value = readField(snapshot, name);
            if (!(value instanceof Integer))
                throw new IllegalStateException("BranchSnapshot integer field changed: " + name);
            json.number(name, ((Integer)value).intValue());
        }
        for (String name : SNAPSHOT_BOOLS) {
            Object value = readField(snapshot, name);
            if (!(value instanceof Boolean))
                throw new IllegalStateException("BranchSnapshot boolean field changed: " + name);
            json.bool(name, ((Boolean)value).booleanValue());
        }
        for (String name : SNAPSHOT_STRINGS) {
            Object value = readField(snapshot, name);
            if (!(value instanceof String))
                throw new IllegalStateException("BranchSnapshot string field changed: " + name);
            json.string(name, (String)value);
        }
        return json.finish();
    }

    private static JsonObject receiptJson(MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt) {
        if (receipt == null) throw new IllegalStateException("Missing actual typed P07 receipt");
        JsonObject result = new JsonObject();
        result.number("attempt", receipt.attempt);
        result.number("startedOrderings", receipt.startedOrderings);
        result.number("completedOrderings", receipt.completedOrderings);
        result.number("yieldedOrderings", receipt.yieldedOrderings);
        result.number("finalizedOrderings", receipt.finalizedOrderings);
        result.number("selectedOrdering", receipt.selectedOrdering);
        result.number("expansions", receipt.expansions);
        result.bool("guidanceStarted", receipt.guidanceStarted);
        result.bool("comparisonComplete", receipt.comparisonComplete);
        result.string("termination", receipt.termination);
        return result;
    }

    private static String boardIdentityPreimage(TroubleshootBoard board, String planCanonical) {
        StringBuilder text = new StringBuilder();
        appendRow(text, "BOARD", board.getId(), board.getSilkscreenTitle());
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String id : netIds) {
            BoardNet net = board.getNet(id);
            if (net == null) throw new IllegalStateException("Board identity lost a net");
            appendRow(text, "NET", id, net.getRoutingRole().name());
        }
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String id : componentIds) {
            BoardComponent component = board.getComponent(id);
            if (component == null || component.getPhysicalPackage() == null)
                throw new IllegalStateException("Board identity lost a component package");
            PhysicalPackage physicalPackage = component.getPhysicalPackage();
            StringBuilder terminals = new StringBuilder();
            for (String terminal : physicalPackage.getTerminalIds()) appendField(terminals, terminal);
            appendRow(text, "COMPONENT", component.getId(), component.getType(),
                physicalPackage.getId(), Integer.toString(physicalPackage.getTerminalCount()),
                component.getDisplayName(), Integer.toString(component.getReferenceLabelCharacters()),
                terminals.toString());
        }
        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        for (String id : padIds) {
            BoardPad pad = board.getPad(id);
            if (pad == null) throw new IllegalStateException("Board identity lost a pad");
            appendRow(text, "PAD", pad.getId(), pad.getComponentId(), pad.getTerminalId(), pad.getNetId());
        }
        Vector<String> powerIds = board.getPowerInputIds();
        Collections.sort(powerIds);
        for (String id : powerIds) {
            ExternalBoardPowerInput input = board.getPowerInput(id);
            if (input == null) throw new IllegalStateException("Board identity lost an external input");
            appendRow(text, "POWER", input.getId(), input.getPositivePadId(), input.getReturnPadId(),
                input.getPositiveNetId(), input.getReturnNetId());
        }
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        appendRow(text, "PHYSICAL", constraints.routingLayer.name(),
            constraints.getPhysicalPolicyIdentity(), Integer.toString(constraints.placementDensityVersion),
            Integer.toString(constraints.placementPackingVersion),
            Boolean.toString(constraints.requiresTwoLayerRouting),
            constraints.getTraceWidthProfile().getIdentity());
        appendRow(text, "PLAN", planCanonical);
        return text.toString();
    }

    private static void appendRow(StringBuilder out, String... values) {
        for (String value : values) appendField(out, value);
        out.append('\n');
    }

    private static void appendField(StringBuilder out, String value) {
        if (value == null) {
            out.append("-1:");
            return;
        }
        out.append(value.length()).append(':').append(value);
    }

    private static String intsJson(int[] values) {
        if (values == null) throw new IllegalArgumentException("Missing eligible attempt census");
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index != 0) result.append(',');
            result.append(values[index]);
        }
        return result.append(']').toString();
    }

    private static boolean sameInts(int[] left, int[] right) {
        if (left == null || right == null || left.length != right.length) return false;
        for (int index = 0; index < left.length; index++)
            if (left[index] != right[index]) return false;
        return true;
    }
    private static Object readField(Object target, String name) {
        if (target == null) throw new IllegalStateException("Cannot read " + name + " from a null owner");
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException absent) {
                type = type.getSuperclass();
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot read named owner field " + name, failure);
            }
        }
        throw new IllegalStateException("Actual owner omitted named field " +
            target.getClass().getName() + "#" + name);
    }

    private static int intField(Object target, String name) {
        Object value = readField(target, name);
        if (!(value instanceof Integer)) throw new IllegalStateException("Named owner field is not int: " + name);
        return ((Integer)value).intValue();
    }

    private static double doubleField(Object target, String name) {
        Object value = readField(target, name);
        if (!(value instanceof Double)) throw new IllegalStateException("Named owner field is not double: " + name);
        return ((Double)value).doubleValue();
    }

    private static boolean boolField(Object target, String name) {
        Object value = readField(target, name);
        if (!(value instanceof Boolean)) throw new IllegalStateException("Named owner field is not boolean: " + name);
        return ((Boolean)value).booleanValue();
    }

    private static boolean token(String value) {
        if (value == null || value.length() < 1 || value.length() > 128) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!(c == '-' || c == '+' || c == '_' || c == '.' || c == ':' ||
                    (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    private static void requireSha(String value, String name) {
        if (value == null || !SHA_PATTERN.matcher(value).matches())
            throw new IllegalArgumentException(name + " must be lowercase SHA-256 text");
    }

    private static byte[] utf8(String text) {
        if (text == null) throw new IllegalArgumentException("Cannot encode a null string");
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1)))
                    throw new IllegalArgumentException("Unpaired high surrogate in report text");
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Unpaired low surrogate in report text");
            }
        }
        return text.getBytes(UTF8);
    }

    private static String quote(String text) {
        if (text == null) throw new IllegalArgumentException("JSON string value is null");
        StringBuilder result = new StringBuilder(text.length() + 2);
        result.append('"');
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '"' || c == '\\') {
                result.append('\\').append(c);
            } else if (c < 0x20) {
                result.append("\\u00").append(hex[(c >>> 4) & 15]).append(hex[c & 15]);
            } else if (Character.isHighSurrogate(c)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1)))
                    throw new IllegalArgumentException("Unpaired high surrogate in JSON string");
                result.append(c).append(text.charAt(++index));
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired low surrogate in JSON string");
            } else {
                result.append(c);
            }
        }
        return result.append('"').toString();
    }
    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    private static String sha256(byte[] value) {
        if (value == null) throw new IllegalArgumentException("Missing SHA-256 preimage");
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] value) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[value.length * 2];
        for (int index = 0; index < value.length; index++) {
            int b = value[index] & 255;
            result[index * 2] = digits[b >>> 4];
            result[index * 2 + 1] = digits[b & 15];
        }
        return new String(result);
    }

    private static long elapsed(long started, long ended) {
        long value = ended - started;
        return value < 0 ? Long.MAX_VALUE : value;
    }

    private static Throwable appendFailure(Throwable primary, Throwable later) {
        if (primary == null) return later;
        if (later != null && later != primary) primary.addSuppressed(later);
        return primary;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) throw (Exception)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new RuntimeException(failure);
    }
}
