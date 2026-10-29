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
        Rb30Plan.FAMILY_ID,
        Rb56Plan.FAMILY_ID
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
        int[] acceptedByFamily = new int[familyCount];
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
                    if (result.accepted) {
                        accepted++;
                        acceptedByFamily[familyIndex - firstFamily]++;
                    } else rejected++;
                }
            }
        } finally {
            CircuitElm.sim = prior;
        }
        int expectedCases = familyCount * rootCount;
        require(cases == expectedCases, "normal census emitted the exact requested case count");
        for (int familyIndex = 0; familyIndex < familyCount; familyIndex++)
            require(acceptedByFamily[familyIndex] > 0,
                "each normal family has at least one accepted census case: " +
                    FAMILIES[firstFamily + familyIndex]);
        System.out.println("PASS: PCB normal packing census " + suite + " cases=" + cases +
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
            "normal catalog exposes exactly twelve active families");
        for (int i = 0; i < FAMILIES.length; i++)
            require(FAMILIES[i].equals(active.get(i)),
                "normal active family catalog order is stable at index " + i);
        require(registered.size() == 12 && registered.contains(Rb56Plan.FAMILY_ID),
            "registered catalog retains RB56 alongside all twelve active families");
        require(PlayerFamilyCatalog.isNormalPlayerEnabled(Rb56Plan.FAMILY_ID),
            "RB56 is enabled through normal-player admission");
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
            if (candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM ||
                    candidate.getExecutionPolicy() == GenerationExecutionPolicy.RB56) {
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
        PcbPackingProbeReceipt probe = layout.getPackingProbeReceipt();
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        boolean declaredRb56Layout = Rb56Plan.FAMILY_ID.equals(family) &&
            profile == DifficultyProfile.MEDIUM && policy == GenerationExecutionPolicy.RB56 &&
            owner.getPhysicalAdmission() != null &&
            MediumBoardNormalAdmission.RB56_IDENTITY.equals(owner.getPhysicalAdmission().identity()) &&
            constraints.placementPackingVersion == 1 && constraints.requiresTwoLayerRouting &&
            constraints.placementDensityVersion == Rb56Placement.densityVersion(Rb56Placement.CURRENT_VERSION) &&
            constraints.getTraceWidthProfile() == Rb56Placement.widthProfile(Rb56Placement.CURRENT_VERSION);
        String packingDisposition;
        if (declaredRb56Layout) {
            // RB56 preserves its independently admitted current layout. The
            // packing4 probe never runs for this declared packing1 contract.
            require(probe == null, "declared RB56 layout has no fabricated packing4 probe receipt");
            packingDisposition = "NOT_APPLICABLE_DECLARED_LAYOUT";
        } else {
            require(probe != null, "every packing4 normal board records its packing probe disposition");
            probe.requireGeometry(layout);
            packingDisposition = probe.disposition;
        }
        PcbPackingMetrics measured = PcbPackingMetrics.measure(board, layout);
        require(measured.area == (long) outline.width * outline.height &&
                measured.copper == copper && measured.vias == vias &&
                measured.protectedVoid == whitespace,
            "production packing metrics agree with the independent physical sampler");
        require(graphAfterSampler.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(board)) &&
                geometry.equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "production packing metrics preserve source graph and complete geometry");
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
            .append(",\"packingProbeDisposition\":").append(jsonQuote(packingDisposition))
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
        printSilkscreenObservation(family, root, layout);
        printReferenceObservation(family, root, owner);
        printLeadSilkscreenObservation(family, root, owner);
        return CaseResult.ACCEPTED;
    }

    /** Add public ink observations; preserve every existing full geometry/graph oracle. */
    private static void printLeadSilkscreenObservation(String family, long root,
            GeneratedBoardInstance owner) {
        PcbBoardLayout layout = owner.getPcbLayout();
        String full = layout.geometryFingerprint();
        String graph = PcbCanvasBoundaryContractTest.liveGraphSha256(owner.getBoard());
        String prefix = "layout=" + layout.getLayoutAlgorithmVersion() + "|";
        require(full.startsWith(prefix), "lead/silk observation frames only actual layout epoch");
        String normalized = "layout=#LEGEND_POLICY#|" + full.substring(prefix.length());
        Vector<String> ids = owner.getBoard().getComponentIds();
        java.util.Collections.sort(ids);
        StringBuilder components = new StringBuilder("[");
        for (int c = 0; c < ids.size(); c++) {
            String id = ids.get(c);
            PcbComponentPlacement placement = layout.getComponent(id);
            if (c > 0) components.append(',');
            components.append("{\"id\":").append(jsonQuote(id)).append(",\"leads\":[");
            int terminals = placement.getPhysicalGeometry().getTerminals().size();
            for (int i = 0; i < terminals; i++) {
                if (i > 0) components.append(',');
                appendLeadSilkRectangle(components, placement.getLeadBounds(i));
            }
            components.append("]}");
        }
        components.append(']');
        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        java.util.Collections.sort(labels, new java.util.Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel a, PcbSilkscreenLabel b) {
                return a.getId().compareTo(b.getId());
            }
        });
        StringBuilder ink = new StringBuilder("["), overlaps = new StringBuilder("[");
        int pairCount = 0;
        for (int l = 0; l < labels.size(); l++) {
            PcbSilkscreenLabel label = labels.get(l);
            Rectangle bounds = label.getBounds();
            String record = "L:" + label.getId() + "@" + label.getText() + ":" +
                bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height + ";";
            int offset = normalized.indexOf(record);
            require(offset >= 0 && normalized.indexOf(record, offset + record.length()) < 0,
                "normalize exactly one complete physical legend position");
            normalized = normalized.substring(0, offset) + "L:" + label.getId() + "@" +
                label.getText() + ":#LEGEND_POSITION#," + bounds.width + "," + bounds.height + ";" +
                normalized.substring(offset + record.length());
            if (l > 0) ink.append(',');
            ink.append("{\"id\":").append(jsonQuote(label.getId())).append(",\"bounds\":");
            appendLeadSilkRectangle(ink, bounds);
            ink.append('}');
            for (String id : ids) {
                PcbComponentPlacement placement = layout.getComponent(id);
                int terminals = placement.getPhysicalGeometry().getTerminals().size();
                for (int i = 0; i < terminals; i++) {
                    if (!bounds.intersects(placement.getLeadBounds(i))) continue;
                    if (pairCount++ > 0) overlaps.append(',');
                    overlaps.append("{\"label\":").append(jsonQuote(label.getId())).append(",\"component\":")
                        .append(jsonQuote(id)).append(",\"terminal\":").append(i).append('}');
                }
            }
        }
        ink.append(']'); overlaps.append(']');
        require(full.equals(layout.geometryFingerprint()) &&
            graph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(owner.getBoard())),
            "lead/silk observation preserves physical layout and live graph");
        System.out.println("PCB_LEAD_SILK_CASE {\"family\":" + jsonQuote(family) +
            ",\"root\":" + jsonQuote(Long.toString(root)) +
            ",\"geometryApartFromLegendPositionAndEpochSha256\":" +
            jsonQuote(PlayerSessionFingerprint.of(normalized)) + ",\"components\":" + components +
            ",\"labels\":" + ink + ",\"overlaps\":" + overlaps + "}");
    }

    private static void appendLeadSilkRectangle(StringBuilder out, Rectangle bounds) {
        out.append('[').append(bounds.x).append(',').append(bounds.y).append(',')
            .append(bounds.width).append(',').append(bounds.height).append(']');
    }

    private static void printReferenceObservation(String family, long root,
            GeneratedBoardInstance owner) {
        TroubleshootBoard board = owner.getBoard();
        PcbBoardLayout layout = owner.getPcbLayout();
        String full = layout.geometryFingerprint();
        String graph = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String physical = PhysicalBoardFingerprint.of(owner);
        String normalized = full;
        Vector<String> ids = board.getComponentIds();
        java.util.Collections.sort(ids);
        StringBuilder components = new StringBuilder("[");
        int componentCount = 0;
        for (String id : ids) {
            BoardComponent component = board.getComponent(id);
            PcbSilkscreenLabel label = layout.getSilkscreenLabel("component:" + id);
            require(component != null && label != null && component.getDisplayName().equals(label.getText()),
                "reference observation binds actual component and physical label");
            if (componentCount++ > 0) components.append(',');
            components.append("{\"id\":").append(jsonQuote(id)).append(",\"type\":")
                .append(jsonQuote(component.getType())).append(",\"name\":")
                .append(jsonQuote(component.getDisplayName())).append(",\"referenceLabelCharacters\":")
                .append(component.getReferenceLabelCharacters()).append('}');
            Rectangle b = label.getBounds();
            String prefix = "L:" + label.getId() + "@";
            String suffix = ":" + b.x + "," + b.y + "," + b.width + "," + b.height + ";";
            String record = prefix + label.getText() + suffix;
            int offset = normalized.indexOf(record);
            require(offset >= 0 && normalized.indexOf(record, offset + record.length()) < 0,
                "normalize only one complete uniquely framed component-label record");
            normalized = normalized.substring(0, offset) + prefix + "#PUBLIC_REFERENCE#" + suffix +
                normalized.substring(offset + record.length());
        }
        components.append(']');
        Vector<PcbSilkscreenLabel> labels = new Vector<PcbSilkscreenLabel>(layout.getSilkscreenLabels());
        java.util.Collections.sort(labels, new java.util.Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        StringBuilder ink = new StringBuilder("[");
        int labelCount = 0;
        for (PcbSilkscreenLabel label : labels) {
            if (labelCount++ > 0) ink.append(',');
            Rectangle b = label.getBounds();
            require(label.getBaselineX() == b.x && label.getBaselineY() == b.y + b.height - 3,
                "reference observation retains the immutable physical baseline");
            ink.append("{\"id\":").append(jsonQuote(label.getId())).append(",\"text\":")
                .append(jsonQuote(label.getText())).append(",\"bounds\":[")
                .append(b.x).append(',').append(b.y).append(',').append(b.width).append(',').append(b.height)
                .append("],\"fontSize\":").append(label.getFontSize()).append(",\"bold\":")
                .append(label.isBold()).append(",\"targetPadId\":").append(jsonQuote(label.getTargetPadId())).append('}');
        }
        ink.append(']');
        require(componentCount == board.getComponentIds().size() && labelCount == layout.getSilkscreenLabels().size(),
            "reference observer emits the complete actual component and label inventories");
        require(full.equals(layout.geometryFingerprint()) && physical.equals(PhysicalBoardFingerprint.of(owner)) &&
                graph.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(board)),
            "reference observer preserves full layout, physical identity and live graph");
        System.out.println("PCB_NORMAL_REFERENCE_CASE {\"family\":" + jsonQuote(family) +
            ",\"root\":" + jsonQuote(Long.toString(root)) + ",\"components\":" + components +
            ",\"labels\":" + ink + ",\"labelCount\":" + labelCount +
            ",\"physicalBoardSha256\":" + jsonQuote(PlayerSessionFingerprint.of(physical)) +
            ",\"geometryApartFromReferenceTextSha256\":" + jsonQuote(PlayerSessionFingerprint.of(normalized)) + "}");
    }

    /** Observe one text field separately without mutating authoritative geometry. */
    private static void printSilkscreenObservation(String family, long root,
            PcbBoardLayout layout) {
        PcbSilkscreenLabel title = layout.getSilkscreenLabel("board-title");
        require(title != null, "normal board has a physical title label");
        String geometry = layout.geometryFingerprint();
        String field = "L:board-title@" + title.getText() + ":";
        int offset = geometry.indexOf(field);
        require(offset >= 0 && geometry.indexOf(field, offset + field.length()) < 0,
            "exactly one board-title field is observed");
        String normalized = geometry.substring(0, offset) + "L:board-title@#PUBLIC_TITLE#:" +
            geometry.substring(offset + field.length());
        Rectangle bounds = title.getBounds();
        System.out.println("PCB_NORMAL_SILK_CASE {\"family\":" + jsonQuote(family) +
            ",\"root\":" + jsonQuote(Long.toString(root)) +
            ",\"title\":" + jsonQuote(title.getText()) +
            ",\"bounds\":[" + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height +
            "],\"geometryApartFromTitleSha256\":" +
            jsonQuote(PlayerSessionFingerprint.of(normalized)) + "}");
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
            else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                result.append("\\u");
                for (int padding = hex.length(); padding < 4; padding++) result.append('0');
                result.append(hex);
            }
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
