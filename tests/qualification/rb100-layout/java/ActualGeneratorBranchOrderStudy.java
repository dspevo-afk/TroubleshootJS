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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Pattern;

/** Bounded producer for the frozen actual-generator branch-order report. */
public final class ActualGeneratorBranchOrderStudy {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SCHEMA = "p07-actual-branch-order-study-v4";
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
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private static final String[] ARM_IDS = {
        "PLAIN_ENTRY_RECEIPT_CONTROL", "OBSERVED_DEFAULT_NEAREST",
        "OBSERVED_EXPLICIT_NEAREST", "SECOND_ORDER_MINIMUM_PAIR"
    };
    private static final String[] ARM_APIS = {
        "PLAIN_BEGIN", "BOUNDARY_CONVENIENCE", "BOUNDARY_EXPLICIT", "BOUNDARY_EXPLICIT"
    };
    private static final String[] ARM_STRATEGIES = {
        "CANONICAL_NEAREST", "CANONICAL_NEAREST", "CANONICAL_NEAREST", "CANONICAL_NEAREST"
    };
    private static final String[] ARM_NET_ORDER_STRATEGIES = {
        "CANONICAL", "CANONICAL", "CANONICAL", "SECOND_ORDER_MINIMUM_PAIR"
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
            for (int operation = 0; operation < 4; operation++) {
                ArmResult result = new ArmRun(this, operation).execute();
                if (receiptBytes > STUDY_LIMIT_BYTES - result.receiptBytes)
                    throw new IOException("Study JSONL receipts exceeded 32 MiB");
                receiptBytes += result.receiptBytes;
                if (cleanupNanos > CLEANUP_LIMIT_NANOS - result.cleanupNanos)
                    throw new IOException("Cumulative context cleanup exceeded 120 seconds");
                cleanupNanos += result.cleanupNanos;
                arms.add(result);
            }
            if (arms.size() != 4 || receiptBytes > STUDY_LIMIT_BYTES ||
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
                finishGroup(advance);
                currentGroup = null;
                advance.routeForSlice = null;
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
            if (operation < 1 || operation > 3 || group == null ||
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
            byte[] prefix = utf8(liveLayout.geometryFingerprint());
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
            branchCount++;
            group.callbacks++;
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
            row.number("hashInputBytes", group.hashInputBytes);
            row.number("serializedBeforeOwnLineBytes", group.serializedBeforeOwnLineBytes);
            return row.finish();
        }
        private void appendRecord(GroupLedger group, String json) throws IOException {
            if (group == null || group != currentGroup)
                throw new IllegalStateException("JSONL record has no current owner group");
            group.serializedBeforeOwnLineBytes += sink.writeLine(json);
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
        long hashInputBytes;
        long serializedBeforeOwnLineBytes;
        Object routeForSlice;
        GroupLedger(int ordinal, String phase, long startedNanos) {
            this.ordinal = ordinal;
            this.phase = phase;
            this.startedNanos = startedNanos;
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
