package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Vector;

/** Actual fixed seeded geometry canaries; no electrical or player qualification. */
public final class Rb56GeometryContractTest {
    private static int assertions, acceptedRoutes, admittedRoutes;

    public static void main(String[] args) throws Exception {
        String legacyCanonical = null;
        for (String arg : args) {
            if (!arg.startsWith("--legacy-canonical=") || legacyCanonical != null ||
                    arg.length() == "--legacy-canonical=".length())
                throw new IllegalArgumentException("Expected at most one --legacy-canonical=<frozen UTF-8 file>");
            legacyCanonical = arg.substring("--legacy-canonical=".length());
        }
        System.out.println("RB56_GEOMETRY_CRITERIA {\"cases\":3,\"rootSeeds\":[\"17\",\"77\",\"42\"]," +
            "\"packages\":[40,55,60],\"pads\":[96,133,143],\"routingPolicy\":\"MEDIUM_BOARD@1\"," +
            "\"admission\":\"RB56_BOARD_NORMAL@1\",\"corridorDrawingUnits\":80," +
            "\"routeCallsPerCase\":1,\"seedSearch\":false,\"scope\":\"FIXED_SEEDED_PHYSICAL_CANARIES_ONLY\"}");
        Vector<Throwable> failures = new Vector<Throwable>();
        runCase(Rb56Plan.configured(17L, 1, Rb56Plan.ReferenceArrangement.SHARED_DIRECT,
            true, false, 1, 0, false), 40, 96, failures);
        runCase(Rb56Plan.reference(77L), 55, 133, failures);
        runCase(Rb56Plan.configured(42L, 2, Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC,
            true, true, 3, 3, true), 60, 143, failures);
        if (legacyCanonical != null) {
            try { compareLegacyCanonical(legacyCanonical); }
            catch (Exception failure) { failures.add(failure); failure.printStackTrace(System.err); }
            catch (AssertionError failure) { failures.add(failure); failure.printStackTrace(System.err); }
        }
        if (!failures.isEmpty()) {
            System.out.println("RB56_GEOMETRY_SUMMARY {\"status\":\"FAIL\",\"cases\":3,\"acceptedRoutes\":" +
                acceptedRoutes + ",\"admittedRoutes\":" + admittedRoutes + ",\"failures\":" + failures.size() + "}");
            AssertionError combined = new AssertionError("RB56 fixed geometry canaries failed; route rejects are not qualified");
            for (Throwable failure : failures) combined.addSuppressed(failure);
            throw combined;
        }
        System.out.println("PASS: RB56 geometry contracts " + assertions + " assertions cases=3 acceptedRoutes=" +
            acceptedRoutes + " admittedRoutes=" + admittedRoutes + " scope=FIXED_SEEDED_PHYSICAL_CANARIES_ONLY");
    }

    private static void runCase(final Rb56Plan plan, int packages, int pads, Vector<Throwable> failures) {
        String phase = "DECLARATIONS";
        MediumBoardPhysicalPolicy.Result routed = null;
        System.out.println("RB56_GEOMETRY_BEGIN " + identity(plan, packages, pads));
        try {
            final TroubleshootBoard board = plan.board();
            final String planBefore = plan.canonical();
            declarations(plan, board, packages, pads);
            packageProviders(board);
            phase = "SEEDED_ROUTE";
            System.out.println("RB56_GEOMETRY_PHASE {\"rootSeed\":\"" + plan.seed + "\",\"phase\":\"" + phase + "\"}");
            routed = new SeededPcbLayoutGenerator().generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
            System.out.println("RB56_GEOMETRY_ROUTE {\"rootSeed\":\"" + plan.seed + "\",\"accepted\":" +
                (routed != null && routed.accepted()) + ",\"receipt\":" + json(routed == null ? null : routed.toCanonical()) + "}");
            require(routed != null && routed.accepted() && routed.getFailure() == null && routed.getLayout() != null,
                "fixed root " + plan.seed + " requires an actual accepted route: " + (routed == null ? "null result" : routed.toCanonical()));
            acceptedRoutes++;
            final MediumBoardPhysicalPolicy.Result accepted = routed;
            final PcbBoardLayout layout = routed.getLayout();
            phase = "ROUTED_GEOMETRY";
            require(planBefore.equals(plan.canonical()), "routing preserves the exact immutable plan and named seeds");
            declarations(plan, board, packages, pads);
            require(layout.matchesGenerationSeeds(plan.layoutSeed, plan.routingSeed), "actual route binds both declared seeds");
            require(layout.getComponents().size() == packages && layout.getPads().size() == pads,
                "actual seeded route retains the independent package/pad census");
            layout.validateGeometry(board);
            new PcbTwoLayerRules(board, layout).validate(layout);
            mixedGeometry(layout);
            require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(routed.getStatistics().selectedRoutePolicy),
                "normal RB56 canary needs the actual bounded P07 route; a one-face result does not qualify");
            phase = "RB56_ADMISSION";
            final GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
                board, accepted, plan.layoutSeed, plan.routingSeed);
            require("RB56_BOARD_NORMAL@1".equals(admission.identity()) &&
                    !admission.identity().equals(MediumBoardNormalAdmission.IDENTITY) &&
                    !admission.identity().equals(MediumBoardPhysicalPolicy.identity()),
                "RB56 admission has a separate identity from legacy admission and structural routing");
            require(admission.canonical().contains("parts=40..60") &&
                    admission.canonical().contains("manufacturing-rating=NONE"),
                "admission identifies the bounded drawing-space RB56 contract");
            admission.requireConstruction(board, layout);
            admittedRoutes++;
            phase = "NEGATIVE_CONTRACTS";
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRoute(board, accepted, plan.layoutSeed, plan.routingSeed);
            } }, "Normal medium", "legacy 20..40 admission never accepts an RB56 realization");
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board, accepted, plan.layoutSeed ^ 1L, plan.routingSeed);
            } }, "seeds do not match", "foreign placement seed cannot acquire the accepted receipt");
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board,
                    new MediumBoardPhysicalPolicy.Result(null, accepted.getStatistics(), "negative rejected route"),
                    plan.layoutSeed, plan.routingSeed);
            } }, "requires an accepted routed candidate", "a rejected route cannot issue admission");
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board,
                    new MediumBoardPhysicalPolicy.Result(layout, withoutSelectedSuccess(accepted.getStatistics()), null),
                    plan.layoutSeed, plan.routingSeed);
            } }, "absent from its accepted receipt", "selected route must belong to its receipt");
            final Vector<PcbPlacementConstraints.Barrier> narrow = new Vector<PcbPlacementConstraints.Barrier>();
            narrow.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 79));
            final TroubleshootBoard alteredCorridor = copyDeclaration(board, board.getId(),
                new PcbPlacementConstraints(board.getPlacementConstraints().getParts(), narrow, PcbCopperLayer.BOTTOM,
                    MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION));
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRb56Route(alteredCorridor, accepted, plan.layoutSeed, plan.routingSeed);
            } }, "80-unit corridor", "a narrower declaration cannot borrow actual accepted copper");
            final PcbBoardLayout wrongOpto = withUnrotatedOptocoupler(board, layout, plan.layoutSeed, plan.routingSeed);
            reject(new Runnable() { public void run() {
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board,
                    new MediumBoardPhysicalPolicy.Result(wrongOpto, accepted.getStatistics(), null),
                    plan.layoutSeed, plan.routingSeed);
            } }, "exact physical pose: UFB", "legal package TOP/0 is not the declared RB56 UFB pose");
            final TroubleshootBoard foreignOwner = copyDeclaration(board, "FOREIGN_RB56_OWNER", board.getPlacementConstraints());
            reject(new Runnable() { public void run() { admission.requireConstruction(foreignOwner, layout); } },
                "does not match its board realization", "an otherwise identical foreign board cannot consume the token");
            reject(new Runnable() { public void run() { admission.requireNormal(null); } },
                "not attached to this player owner", "construction eligibility alone is not an attached normal player owner");
            reject(new Runnable() { public void run() { PcbTwoLayerRules.requireDeveloperAdmission(layout, false); } },
                "requires developer-only construction", "the unchanged P09 guard still rejects an unadmitted two-face layout");
            layout.seal();
            admission.requireConstruction(board, layout.copySealed());
            require(layout.isSealed(), "accepted geometry can be frozen without changing admission identity");
            System.out.println("RB56_GEOMETRY_CASE {\"rootSeed\":\"" + plan.seed + "\",\"status\":\"PASS\"," +
                "\"packages\":" + packages + ",\"pads\":" + pads + ",\"admission\":" + json(admission.identity()) + "}");
        } catch (RuntimeException failure) { recordFailure(plan, phase, routed, failure, failures);
        } catch (Error failure) { recordFailure(plan, phase, routed, failure, failures); }
    }

    private static void declarations(Rb56Plan plan, TroubleshootBoard board, int packages, int pads) {
        board.validate();
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        constraints.validate(board);
        require("RB56_CONTROL_BOARD".equals(board.getId()) && plan.physicalPackageCount() == packages &&
                board.getComponentIds().size() == packages && board.getPadIds().size() == pads &&
                plan.powerParts().size() == 20 && board.getPowerInputIds().size() == plan.channelCount + 2,
            "fixed representative has its independent board/package/pad/source census");
        require(constraints.getParts().size() == packages && constraints.routingLayer == PcbCopperLayer.BOTTOM &&
                "MEDIUM_BOARD@1".equals(constraints.getPhysicalPolicyIdentity()), "one structural demand per actual package");
        Vector<PcbPlacementConstraints.Barrier> barriers = constraints.getBarriers();
        require(barriers.size() == 1 && "PRIMARY".equals(barriers.get(0).firstDomain) &&
                "SECONDARY".equals(barriers.get(0).secondDomain) && barriers.get(0).clearance == 80,
            "one exact 80 drawing-unit PRIMARY/SECONDARY corridor");
        HashSet<String> seen = new HashSet<String>();
        for (Rb56Plan.Part part : plan.parts()) {
            BoardComponent component = board.getComponent(part.id);
            PcbPlacementConstraints.Part demand = constraints.get(part.id);
            String[] terminals = part.terminalIds(), nets = part.netIds();
            require(seen.add(part.id) && component != null && component.getPhysicalPackage() == part.physicalPackage &&
                    component.getType().equals(part.type) && component.getPadIds().size() == terminals.length && demand != null,
                "plan is the sole package inventory: " + part.id);
            boolean mixed = "UAC".equals(part.id) || "UFB".equals(part.id);
            require((demand.getTerminalDomains().size() == terminals.length) == mixed,
                "only converter and optocoupler declare mixed-domain terminals: " + part.id);
            for (int index = 0; index < terminals.length; index++) {
                BoardPad pad = board.getPad(part.id + "." + terminals[index]);
                require(pad != null && part.id.equals(pad.getComponentId()) && terminals[index].equals(pad.getTerminalId()) &&
                        nets[index].equals(pad.getNetId()) && component.getPadIds().contains(pad.getId()),
                    "each declared terminal retains its exact pad/net identity: " + part.id + "." + terminals[index]);
            }
        }
        pins(board, "UAC", new String[] {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"},
            new String[] {"HV_POS", "HV_RETURN", "PRE_L", "CTRL_RETURN", "ENABLE_AC", "FB_AC", "BIAS_AC"},
            new String[] {"PRIMARY", "PRIMARY", "SECONDARY", "SECONDARY", "PRIMARY", "PRIMARY", "PRIMARY"});
        pins(board, "UFB", new String[] {"A", "K", "C", "E"},
            new String[] {"SENSE_LED", "CTRL_RETURN", "FB_AC", "HV_RETURN"},
            new String[] {"SECONDARY", "SECONDARY", "PRIMARY", "PRIMARY"});
        require(constraints.get("JAC").anchor == PcbPlacementConstraints.Anchor.LEFT &&
                "PRIMARY".equals(constraints.get("JAC").domainId) &&
                constraints.get("JLOAD").anchor == PcbPlacementConstraints.Anchor.RIGHT,
            "actual source connectors retain the declared primary entry and secondary load edges");
    }

    private static void pins(TroubleshootBoard board, String id, String[] terminals, String[] nets, String[] domains) {
        require(Arrays.equals(terminals, board.getComponent(id).getPhysicalPackage().getTerminalIds().toArray(new String[0])),
            "independent terminal order for " + id);
        for (int index = 0; index < terminals.length; index++) {
            String pad = id + "." + terminals[index];
            require(nets[index].equals(board.getPad(pad).getNetId()) &&
                    domains[index].equals(board.getPlacementConstraints().domainForPad(board, pad)),
                "independent isolated pin/net/domain expectation: " + pad);
        }
    }

    private static void packageProviders(TroubleshootBoard board) {
        PhysicalPackage opto = PhysicalPackages.OPTOCOUPLER_4;
        require(opto.supportsPose(new PcbPackagePose(100, 100, PcbRotation.DEG_0, PcbBoardSide.TOP)) &&
                opto.supportsPose(new PcbPackagePose(100, 100, PcbRotation.DEG_180, PcbBoardSide.TOP)) &&
                !opto.supportsPose(new PcbPackagePose(100, 100, PcbRotation.DEG_90, PcbBoardSide.TOP)) &&
                !opto.supportsPose(new PcbPackagePose(100, 100, PcbRotation.DEG_180, PcbBoardSide.BOTTOM)),
            "OPTO4 declares TOP/0 and TOP/180 only");
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        PcbFootprint converter = registry.create(board.getComponent("UAC"), 100, 100, new Random(0L), new Rectangle(0, 0, 4000, 4000));
        PcbFootprint feedback = registry.create(board.getComponent("UFB"), 100, 100, new Random(0L), new Rectangle(0, 0, 4000, 4000));
        mixedFootprint(converter.getPlacement(), converter.getPads());
        mixedFootprint(feedback.getPlacement(), feedback.getPads());
    }

    private static void mixedGeometry(PcbBoardLayout layout) {
        for (String id : new String[] {"UAC", "UFB"}) {
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String terminal : layout.getComponent(id).getPhysicalPackage().getTerminalIds()) pads.add(layout.getPad(id + "." + terminal));
            mixedFootprint(layout.getComponent(id), pads);
        }
    }

    private static void mixedFootprint(PcbComponentPlacement placement, Vector<PcbPadPlacement> pads) {
        boolean converter = "UAC".equals(placement.getComponentId());
        String[] terminals = converter ? new String[] {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"} :
            new String[] {"A", "K", "C", "E"};
        int[] x = converter ? new int[] {30, 30, 390, 390, 30, 30, 30} : new int[] {310, 310, 30, 30};
        int[] y = converter ? new int[] {50, 100, 110, 210, 150, 200, 250} : new int[] {150, 70, 150, 70};
        require(placement.getPhysicalPackage() == (converter ? PhysicalPackages.ISOLATED_CONVERTER_7 : PhysicalPackages.OPTOCOUPLER_4) &&
                placement.getMountingSide() == PcbBoardSide.TOP &&
                placement.getRotation() == (converter ? PcbRotation.DEG_0 : PcbRotation.DEG_180) && pads.size() == terminals.length,
            "provider and actual route retain UAC TOP/0 and UFB TOP/180");
        for (int index = 0; index < terminals.length; index++) {
            PcbPadPlacement pad = pads.get(index);
            require(pad != null && (placement.getComponentId() + "." + terminals[index]).equals(pad.getPadId()) &&
                    pad.getX() - placement.getX() == x[index] && pad.getY() - placement.getY() == y[index] &&
                    pad.getEscapeDx() == (x[index] == 30 ? -1 : 1) && pad.getEscapeDy() == 0 &&
                    pad.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                    PcbCopperAccess.canProbe(pad, PcbBoardSide.TOP) && PcbCopperAccess.canProbe(pad, PcbBoardSide.BOTTOM),
                "independent placed terminal identity/coordinates/escape/access: " + placement.getComponentId() + "." + terminals[index]);
        }
    }

    /** Deliberate receipt mutant of the actual accepted result; never a positive fixture. */
    private static MediumBoardPhysicalPolicy.Statistics withoutSelectedSuccess(MediumBoardPhysicalPolicy.Statistics source) {
        Vector<String> outcomes = new Vector<String>(source.routeOutcomes);
        String selected = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@" + source.selectedPlacementAttempt + "=SUCCESS";
        require(outcomes.remove(selected), "actual selected P07 receipt row exists before the negative mutation");
        return new MediumBoardPhysicalPolicy.Statistics(source.outcome, source.selectedRoutePolicy,
            source.placementCandidates, source.placementRejections, source.routeAttempts,
            source.oneFaceAttempts, source.oneFaceSuccesses, source.twoLayerAttempts, source.twoLayerSuccesses,
            source.selectedPlacementAttempt, source.rankedPlacementAttempts, source.placementScores, outcomes,
            source.placementEvaluations, source.routingOrders, source.routingExpansions,
            source.selectedPlacementScore, source.selectedRouteQualityMilli);
    }

    /** Copies an actual declaration only to exercise rejected corridor/owner mutations. */
    private static TroubleshootBoard copyDeclaration(TroubleshootBoard source, String id, PcbPlacementConstraints constraints) {
        TroubleshootBoard copy = new TroubleshootBoard(id);
        if (source.getSilkscreenTitle() != null) copy.setSilkscreenTitle(source.getSilkscreenTitle());
        for (String net : source.getNetIds()) copy.addNet(new BoardNet(net, source.getNet(net).getRoutingRole()));
        for (String componentId : source.getComponentIds()) {
            BoardComponent component = source.getComponent(componentId);
            copy.addComponent(new BoardComponent(componentId, component.getType(), component.getPhysicalPackage(), component.getDisplayName()));
        }
        for (String padId : source.getPadIds()) {
            BoardPad pad = source.getPad(padId);
            copy.addPad(new BoardPad(padId, pad.getComponentId(), pad.getTerminalId(), pad.getNetId()));
        }
        for (String inputId : source.getPowerInputIds()) {
            ExternalBoardPowerInput input = source.getPowerInput(inputId);
            copy.addPowerInput(new ExternalBoardPowerInput(inputId, input.getPositivePadId(), input.getReturnPadId(),
                input.getPositiveNetId(), input.getReturnNetId()));
        }
        copy.setPlacementConstraints(constraints);
        copy.validate();
        return copy;
    }

    /** Deliberate pose mutant; preserves every other actual route/receipt input. */
    private static PcbBoardLayout withUnrotatedOptocoupler(TroubleshootBoard board, PcbBoardLayout source,
            long placementSeed, long routingSeed) {
        PcbComponentPlacement original = source.getComponent("UFB");
        PcbFootprint wrong = PcbFootprint.fromPhysicalPackage(board.getComponent("UFB"),
            PcbPackagePose.top(original.getX(), original.getY()), original.getPhysicalGeometry());
        PcbBoardLayout copy = new PcbBoardLayout(source.getWidth(), source.getHeight(), source.getBoardOutline(), source.getPartsTray());
        for (PcbComponentPlacement component : source.getComponents())
            copy.addComponent("UFB".equals(component.getComponentId()) ? wrong.getPlacement() : component);
        for (PcbPadPlacement pad : source.getPads())
            copy.addPad(pad.getPadId().startsWith("UFB.") ? wrong.getPad(pad.getPadId()) : pad);
        for (PcbLayoutRegion region : source.getRegions()) copy.addRegion(region);
        for (PcbSilkscreenLabel label : source.getSilkscreenLabels()) copy.addSilkscreenLabel(label);
        for (PcbBoardHole hole : source.getHoles()) copy.addHole(hole);
        for (PcbTraceGeometry trace : source.getTraces()) copy.addTrace(trace);
        copy.setRoutingStatistics(source.getRoutingExpansions(), source.getRawRoutingSegments(), source.getRoutingCongestionRejections());
        copy.setRoutingRecoveryStatistics(source.getRoutingRecoveryStatistics());
        copy.setGenerationStatistics(source.getGenerationPlacementAttempts(), source.getGenerationRoutingAttempts(),
            source.getGenerationRoutingExpansions(), source.getGenerationRoutingMillis(),
            placementSeed, routingSeed);
        return copy;
    }

    private static void compareLegacyCanonical(String frozenPath) throws Exception {
        byte[] expected = Files.readAllBytes(Paths.get(frozenPath));
        require(expected.length > 0, "legacy oracle is a supplied nonempty pre-change canonical byte artifact");
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName, priorZener = ZenerElm.lastZenerModelName, priorTransistor = TransistorElm.lastModelName;
        int priorMosFlags = MosfetElm.globalFlags; double priorBeta = MosfetElm.lastBeta;
        Rb30Generator.Candidate candidate = null;
        try {
            CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
            Rb30Plan plan = Rb30Plan.resolve(13L);
            candidate = new Rb30Generator().construct(plan);
            MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator().generateWithPolicyResult(
                candidate.board(), plan.layoutSeed, plan.routingSeed);
            System.out.println("RB56_GEOMETRY_LEGACY_ROUTE {\"rootSeed\":\"13\",\"receipt\":" + json(result.toCanonical()) + "}");
            require(result.accepted(), "unchanged actual legacy seed13 factory still has its accepted route");
            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRoute(candidate.board(), result, plan.layoutSeed, plan.routingSeed);
            byte[] actual = admission.canonical().getBytes(StandardCharsets.UTF_8);
            require("MEDIUM_BOARD_NORMAL@1".equals(admission.identity()), "legacy factory retains its exact admission identity");
            System.out.println("RB56_GEOMETRY_LEGACY_BYTES {\"expectedSha256\":" + json(sha256(expected)) +
                ",\"actualSha256\":" + json(sha256(actual)) + ",\"expectedBytes\":" + expected.length + ",\"actualBytes\":" + actual.length + "}");
            require(Arrays.equals(expected, actual), "entire legacy canonical UTF-8 bytes equal the frozen pre-change oracle");
        } finally {
            try { if (candidate != null) for (CircuitElm element : candidate.elements()) element.delete(); }
            finally {
                DiodeElm.lastModelName = priorDiode; ZenerElm.lastZenerModelName = priorZener; TransistorElm.lastModelName = priorTransistor;
                MosfetElm.globalFlags = priorMosFlags; MosfetElm.lastBeta = priorBeta; CircuitElm.sim = prior; CirSim.theSim = priorSingleton;
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder value = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format("%02x", b & 255));
        return value.toString();
    }

    private static String identity(Rb56Plan plan, int packages, int pads) {
        return "{\"rootSeed\":\"" + plan.seed + "\",\"placementSeed\":\"" + plan.layoutSeed +
            "\",\"routingSeed\":\"" + plan.routingSeed + "\",\"packages\":" + packages + ",\"pads\":" + pads +
            ",\"topology\":" + json(plan.topology()) + "}";
    }

    private static void recordFailure(Rb56Plan plan, String phase, MediumBoardPhysicalPolicy.Result routed,
            Throwable failure, Vector<Throwable> failures) {
        failures.add(failure);
        System.out.println("RB56_GEOMETRY_CASE {\"rootSeed\":\"" + plan.seed + "\",\"status\":\"FAIL\",\"phase\":" +
            json(phase) + ",\"failureType\":" + json(failure.getClass().getName()) + ",\"reason\":" + json(failure.getMessage()) +
            ",\"receipt\":" + json(routed == null ? null : routed.toCanonical()) + "}");
        failure.printStackTrace(System.err);
    }

    private static void reject(Runnable action, String expectedMessage, String why) {
        try { action.run(); }
        catch (IllegalArgumentException rejected) {
            require(rejected.getMessage() != null && rejected.getMessage().contains(expectedMessage),
                why + "; exact rejection boundary, actual=" + rejected.getMessage());
            return;
        }
        throw new AssertionError(why + "; unexpectedly accepted");
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }

    private static String json(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
