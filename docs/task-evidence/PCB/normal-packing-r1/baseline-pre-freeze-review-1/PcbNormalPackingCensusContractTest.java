package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Actual normal-player placement census.  The wrappers partition the same
 * ordered family/root matrix so each JVM retains the ordinary sixty-second
 * verifier bound; this class owns the request, admission, and receipt logic.
 */
public final class PcbNormalPackingCensusContractTest {
    static final String[] FAMILIES = {
        QuickPlayFamilyRegistry.LED_INDICATOR,
        QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
        QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR,
        QuickPlayFamilyRegistry.RC_DELAY,
        QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
        QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH,
        QuickPlayFamilyRegistry.RELAY_OUTPUT,
        QuickPlayFamilyRegistry.SENSOR_CONTROL,
        Rb15Plan.FAMILY_ID,
        ControlledIndicatorBlockContributions.FAMILY_ID,
        Rb30Plan.FAMILY_ID
    };
    static final long[] ROOTS = {0L, 17L, 13L, Long.MIN_VALUE};
    private static int assertions;

    private PcbNormalPackingCensusContractTest() { }

    /** First of the eight bounded suites: the first three catalog families. */
    public static void main(String[] args) {
        runRange(0, 3, 0, 4, "base-families-0-2");
    }

    /** Run a contiguous family range and a contiguous exact root range. */
    static void runRange(int firstFamily, int familyCount, int firstRoot,
            int rootCount, String suite) {
        if (firstFamily < 0 || familyCount < 1 || firstFamily + familyCount > FAMILIES.length)
            throw new IllegalArgumentException("Normal census family range is outside the catalog");
        if (firstRoot < 0 || rootCount < 1 || firstRoot + rootCount > ROOTS.length)
            throw new IllegalArgumentException("Normal census root range is outside the fixed catalog");
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        int cases = 0;
        int accepted = 0;
        int rejected = 0;
        try {
            verifyCatalog();
            for (int familyIndex = firstFamily;
                    familyIndex < firstFamily + familyCount; familyIndex++) {
                String family = FAMILIES[familyIndex];
                DifficultyProfile profile = PlayerFamilyCatalog.candidateProfile(family);
                require(profile != null && profile.isAvailable(),
                    "normal family has an available candidate profile: " + family);
                require(PlayerFamilyCatalog.isNormalPlayerEnabled(family),
                    "normal census family is enabled: " + family);
                require(QuickPlayAdmission.supports(family, profile),
                    "normal family has the current Quick Play admission: " + family);
                for (int rootIndex = firstRoot; rootIndex < firstRoot + rootCount; rootIndex++) {
                    long root = rootSelector(rootIndex);
                    cases++;
                    CaseResult result = runCase(family, profile, root);
                    if (result.accepted) accepted++; else rejected++;
                }
            }
        } finally {
            CircuitElm.sim = prior;
        }
        int expectedCases = familyCount * rootCount;
        require(cases == expectedCases, "normal census emitted the exact requested case count");
        System.out.println("PASS: PCB normal packing census cases=" + cases +
            " accepted=" + accepted + " rejected=" + rejected +
            " assertions=" + assertions + " suite=" + jsonQuote(suite));
    }

    static long rootSelector(int ordinal) {
        if (ordinal < 0 || ordinal >= ROOTS.length)
            throw new IllegalArgumentException("Normal census root ordinal is outside the fixed catalog");
        return ROOTS[ordinal];
    }

    private static void verifyCatalog() {
        Vector<String> active = PlayerFamilyCatalog.families();
        Vector<String> registered = PlayerFamilyCatalog.registeredFamilies();
        require(active.size() == FAMILIES.length,
            "normal catalog exposes exactly eleven active families");
        for (int i = 0; i < FAMILIES.length; i++)
            require(FAMILIES[i].equals(active.get(i)),
                "normal active family catalog order is stable at index " + i);
        require(registered.size() == 12 && registered.contains(Rb56Plan.FAMILY_ID),
            "registered catalog retains the held RB56 provider alongside eleven active families");
        require(!PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID),
            "RB56 remains held outside normal-player admission");
    }

    private static CaseResult runCase(String family, DifficultyProfile profile, long root) {
        long started = System.nanoTime();
        GeneratedBoardInstance owner = null;
        long maxAdvanceNanos = -1L;
        String seedText = Long.toString(root);
        try {
            PlayerLaunchRequest launch = PlayerLaunchRequest.random(family, seedText, profile.name());
            GenerationRequest search = launch.generation();
            require(search.isCandidateSearch() && search.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES,
                "normal request retains the bounded candidate search: " + family + "/" + seedText);
            GenerationRequest candidate = search.candidate(0);
            require(candidate.getDescriptor().getRootSeed() == root,
                "candidate zero retains the exact signed root: " + family + "/" + seedText);
            GenerationRequest.Prepared prepared = candidate.resolve(new GenerationRequest.PlanCache());
            if (candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM) {
                GenerationRequest.ConstructionSession session = prepared.beginConstruction();
                int steps = 0;
                while (true) {
                    long advanceStarted = System.nanoTime();
                    boolean done = session.advance();
                    long advanceElapsed = System.nanoTime() - advanceStarted;
                    if (advanceElapsed > maxAdvanceNanos) maxAdvanceNanos = advanceElapsed;
                    if (done) break;
                    if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                        throw new AssertionError("normal-medium construction exceeded the unchanged shared step bound");
                }
                owner = session.result().instance;
            } else {
                owner = prepared.construct().instance;
                maxAdvanceNanos = 0L;
            }
            return acceptCase(family, profile, root, candidate.getExecutionPolicy(), owner,
                started, maxAdvanceNanos);
        } catch (PcbRoutingRejectedException rejectedFailure) {
            printRejected(family, profile, root, "ROUTING", rejectedFailure, started,
                maxAdvanceNanos);
            return CaseResult.REJECTED;
        } catch (SupportedEnvelope.Rejected rejectedFailure) {
            printRejected(family, profile, root, "ENVELOPE", rejectedFailure, started,
                maxAdvanceNanos);
            return CaseResult.REJECTED;
        } catch (GenerationJob.Rejected rejectedFailure) {
            if (!"Normal medium admission requires a selected P07 two-layer route".equals(
                    rejectedFailure.getMessage())) throw rejectedFailure;
            printRejected(family, profile, root, "NORMAL_POLICY", rejectedFailure, started,
                maxAdvanceNanos);
            return CaseResult.REJECTED;
        } finally {
            dispose(owner);
        }
    }

    private static CaseResult acceptCase(String family, DifficultyProfile profile, long root,
            GenerationExecutionPolicy policy, GeneratedBoardInstance owner, long started,
            long maxAdvanceNanos) {
        require(owner != null, "normal construction returned an owner: " + family + "/" + root);
        require(owner.getSeed() == root && family.equals(owner.getCircuitFamilyId()),
            "normal construction retains the requested family and exact root");
        TroubleshootBoard board = owner.getBoard();
        PcbBoardLayout layout = owner.getPcbLayout();
        require(board != null && layout != null, "normal construction returned board geometry");
        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        owner.requireNormalPhysicalAdmission();
        layout.validateGeometry(board);
        String graphAfter = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        require(graphBefore.equals(graphAfter),
            "normal physical admission and full geometry validation preserve the live graph");
        require(layout.getComponents().size() == board.getComponentIds().size() &&
                layout.getPads().size() == board.getPadIds().size(),
            "normal placement retains the complete logical component and pad inventory");
        int whitespace = PcbFunctionalGroupingContractTest.reportWhitespace(board, layout, root, 1);
        String graphAfterSampler = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        require(graphAfter.equals(graphAfterSampler),
            "normal whitespace sampling preserves the live graph");
        String geometry = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        Rectangle outline = layout.getBoardOutline();
        long copper = copperManhattanLength(layout);
        int vias = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        PcbRoutingWork.Statistics recovery = layout.getRoutingRecoveryStatistics();
        String admission = owner.getPhysicalAdmission() == null ?
            SupportedEnvelope.current().identity() : owner.getPhysicalAdmission().identity();
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        StringBuilder row = new StringBuilder();
        row.append("{\"family\":").append(jsonQuote(family))
            .append(",\"profile\":").append(jsonQuote(profile.name()))
            .append(",\"root\":").append(jsonQuote(Long.toString(root)))
            .append(",\"seed\":").append(jsonQuote(Long.toString(owner.getSeed())))
            .append(",\"status\":\"ACCEPTED\",\"phase\":\"FINALIZED\"")
            .append(",\"executionPolicy\":").append(jsonQuote(policy.identity()))
            .append(",\"physicalAdmission\":").append(jsonQuote(admission))
            .append(",\"elapsedMillis\":").append(elapsedMillis)
            .append(",\"maxAdvanceMillis\":").append(optionalMillis(maxAdvanceNanos))
            .append(",\"graphBefore\":").append(jsonQuote(graphBefore))
            .append(",\"graphAfter\":").append(jsonQuote(graphAfter))
            .append(",\"logicalParts\":").append(board.getComponentIds().size())
            .append(",\"logicalPads\":").append(board.getPadIds().size())
            .append(",\"logicalNets\":").append(board.getNetIds().size())
            .append(",\"layoutParts\":").append(layout.getComponents().size())
            .append(",\"layoutPads\":").append(layout.getPads().size())
            .append(",\"traceCount\":").append(layout.getTraces().size())
            .append(",\"geometrySha256\":").append(jsonQuote(geometry))
            .append(",\"layoutAlgorithmVersion\":").append(layout.getLayoutAlgorithmVersion())
            .append(",\"outline\":{\"x\":").append(outline.x)
            .append(",\"y\":").append(outline.y)
            .append(",\"width\":").append(outline.width)
            .append(",\"height\":").append(outline.height)
            .append(",\"area\":").append((long) outline.width * outline.height).append('}')
            .append(",\"copperManhattanLength\":").append(copper)
            .append(",\"holeCount\":").append(layout.getHoleCount())
            .append(",\"viaCount\":").append(vias)
            .append(",\"generationPlacementAttempts\":").append(layout.getGenerationPlacementAttempts())
            .append(",\"generationRoutingAttempts\":").append(layout.getGenerationRoutingAttempts())
            .append(",\"generationRoutingExpansions\":").append(layout.getGenerationRoutingExpansions())
            .append(",\"generationRoutingMillis\":").append(layout.getGenerationRoutingMillis())
            .append(",\"routingRecovery\":").append(recovery == null ? "null" : jsonQuote(recovery.toCanonical()))
            .append(",\"whitespace\":").append(whitespace)
            .append('}');
        System.out.println("PCB_NORMAL_PACKING_CASE " + row.toString());
        return CaseResult.ACCEPTED;
    }

    private static void printRejected(String family, DifficultyProfile profile, long root,
            String phase, RuntimeException failure, long started, long maxAdvanceNanos) {
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        StringBuilder row = new StringBuilder();
        row.append("{\"family\":").append(jsonQuote(family))
            .append(",\"profile\":").append(jsonQuote(profile.name()))
            .append(",\"root\":").append(jsonQuote(Long.toString(root)))
            .append(",\"seed\":").append(jsonQuote(Long.toString(root)))
            .append(",\"status\":\"REJECTED\",\"phase\":").append(jsonQuote(phase))
            .append(",\"type\":").append(jsonQuote(failure.getClass().getName()))
            .append(",\"message\":").append(jsonQuote(failure.getMessage()))
            .append(",\"elapsedMillis\":").append(elapsedMillis)
            .append(",\"maxAdvanceMillis\":").append(optionalMillis(maxAdvanceNanos))
            .append('}');
        System.out.println("PCB_NORMAL_PACKING_CASE " + row.toString());
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long result = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int i = 1; i < x.length; i++)
                result += Math.abs((long) x[i] - x[i - 1]) +
                    Math.abs((long) y[i] - y[i - 1]);
        }
        return result;
    }

    private static String optionalMillis(long nanos) {
        return nanos < 0 ? "null" : Long.toString(nanos / 1000000L);
    }

    private static String jsonQuote(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else if (c < 0x20) result.append('?');
            else result.append(c);
        }
        return result.append('"').toString();
    }

    private static void dispose(GeneratedBoardInstance owner) {
        if (owner == null) return;
        owner.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : owner.getSimulationElements()) element.delete();
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class CaseResult {
        static final CaseResult ACCEPTED = new CaseResult(true);
        static final CaseResult REJECTED = new CaseResult(false);
        final boolean accepted;
        private CaseResult(boolean accepted) { this.accepted = accepted; }
    }
}
