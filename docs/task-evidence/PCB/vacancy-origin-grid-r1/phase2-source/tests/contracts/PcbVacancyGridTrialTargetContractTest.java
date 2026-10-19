package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;

/** Native source-only boundary checks for the explicit vacancy-grid trial. */
public final class PcbVacancyGridTrialTargetContractTest {
    private static final int SAVED_VERSION = 14;
    private static final int CURRENT_VERSION = 15;
    private static final int TRIAL_VERSION = 16;
    private static final long[] RAW_ROOTS = { -1022L, 42L, 4L };
    private static final int[] RAW_CANDIDATES = { 0, 2, 3, 4, 5 };
    private static int assertions;

    private PcbVacancyGridTrialTargetContractTest() { }

    public static void main(String[] args) {
        verifyInterpretationAndReplayBoundary();
        for (long seed : RAW_ROOTS) verifyRawCandidateBindings(seed);
        System.out.println("PASS: PCB vacancy grid trial target contracts roots=" +
            RAW_ROOTS.length + " rawCandidates=" + (RAW_ROOTS.length *
            RAW_CANDIDATES.length) + " assertions=" + assertions +
            " routedRuntime=NOT_USED");
    }

    private static void verifyInterpretationAndReplayBoundary() {
        check(Rb56Placement.CURRENT_VERSION == CURRENT_VERSION &&
                Rb56Placement.supportsVersion(CURRENT_VERSION),
            "current RB56 placement remains explicit version15");
        check(Rb56Placement.supportsVersion(TRIAL_VERSION),
            "native-only RB56 placement version16 is explicitly supported");
        check(Rb56Placement.densityVersion(CURRENT_VERSION) == 14 &&
                Rb56Placement.densityVersion(SAVED_VERSION) == 13 &&
                Rb56Placement.densityVersion(TRIAL_VERSION) == 15,
            "saved14/current15/trial16 retain density13/14/15 mappings");
        check(Rb56Placement.widthProfile(CURRENT_VERSION) ==
                PcbTraceWidthProfile.ROLE_WIDTHS &&
                Rb56Placement.widthProfile(TRIAL_VERSION) ==
                PcbTraceWidthProfile.ROLE_WIDTHS,
            "current15 and trial16 retain ROLE_TRACE@1");

        expectIllegalArgument(new Runnable() {
            public void run() {
                new PcbPlacementConstraints(
                    new java.util.Vector<PcbPlacementConstraints.Part>(),
                    new java.util.Vector<PcbPlacementConstraints.Barrier>(),
                    PcbCopperLayer.BOTTOM, MediumBoardPhysicalPolicy.ID,
                    MediumBoardPhysicalPolicy.VERSION, 16, true,
                    PcbTraceWidthProfile.ROLE_WIDTHS);
            }
        }, "unknown density16 remains rejected after explicit density15 registration");

        long seed = 42L;
        Rb56Plan legacyDefault = Rb56Plan.resolve(seed);
        check(legacyDefault.placementInterpretationVersion == Rb56Placement.LEGACY_VERSION,
            "direct Rb56Plan.resolve(seed) keeps its historical legacy default");
        GenerationRequest privateDefault = GenerationRequest.forFamilyQualification(
            Rb56Plan.FAMILY_ID, seed);
        check(privateDefault.getRb56PlacementInterpretationVersion() == CURRENT_VERSION,
            "default private qualification continues to select current15");

        Rb56Plan saved14 = Rb56Plan.resolve(seed, SAVED_VERSION);
        Rb56Plan current15 = Rb56Plan.resolve(seed, CURRENT_VERSION);
        Rb56Plan trial16 = Rb56Plan.resolve(seed, TRIAL_VERSION);
        check(saved14.placementInterpretationVersion == SAVED_VERSION &&
                current15.placementInterpretationVersion == CURRENT_VERSION &&
                trial16.placementInterpretationVersion == TRIAL_VERSION,
            "explicit saved14/current15/trial16 plans retain their identities");

        GenerationRequest saved14Request = privateRequest(SAVED_VERSION, seed);
        GenerationRequest current15Request = privateRequest(CURRENT_VERSION, seed);
        final GenerationRequest trial16Request = privateRequest(TRIAL_VERSION, seed);
        PlayerSessionReplay saved14Replay = PlayerSessionReplay.forQualification(
            saved14Request);
        PlayerSessionReplay current15Replay = PlayerSessionReplay.forQualification(
            current15Request);
        final String epoch = GenerationDependencyContext.INTERPRETATION_EPOCH;
        check(saved14Replay.rb56PlacementInterpretationVersion() == SAVED_VERSION &&
                saved14Replay.canonical().equals("tsj-private-session/14/" + epoch +
                    "/rb56-placement-14/RB56_CONTROL/42"),
            "saved14 replay identity and epoch remain exact");
        check(current15Replay.rb56PlacementInterpretationVersion() == CURRENT_VERSION &&
                current15Replay.canonical().equals("tsj-private-session/15/" + epoch +
                    "/rb56-placement-15/RB56_CONTROL/42"),
            "current15 replay identity and epoch remain exact");
        check(PlayerSessionReplay.parse(saved14Replay.canonical())
                .rb56PlacementInterpretationVersion() == SAVED_VERSION &&
                PlayerSessionReplay.parse(current15Replay.canonical())
                .rb56PlacementInterpretationVersion() == CURRENT_VERSION,
            "existing saved14 and current15 replay identities still parse");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PlayerSessionReplay.forQualification(trial16Request);
            }
        }, "trial16 cannot be serialized as a replay identity");
        final String trial16Identity = "tsj-private-session/16/" + epoch +
            "/rb56-placement-16/RB56_CONTROL/42";
        expectIllegalArgument(new Runnable() {
            public void run() { PlayerSessionReplay.parse(trial16Identity); }
        }, "future trial16 replay identity remains rejected");
    }

    private static GenerationRequest privateRequest(int version, long seed) {
        return GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, seed)
            .withRb56PlacementInterpretationVersion(version);
    }

    private static void verifyRawCandidateBindings(long seed) {
        Rb56Plan savedPlan = Rb56Plan.resolve(seed, CURRENT_VERSION);
        Rb56Plan trialPlan = Rb56Plan.resolve(seed, TRIAL_VERSION);
        TroubleshootBoard savedBoard = savedPlan.board();
        TroubleshootBoard trialBoard = trialPlan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);

        check(savedPlan.seed == trialPlan.seed && savedPlan.seed == seed &&
                savedPlan.layoutSeed == trialPlan.layoutSeed &&
                savedPlan.routingSeed == trialPlan.routingSeed,
            "raw candidate pairs reuse exact signed seeds: " + seed);
        check(savedBoard.getComponentIds().size() == trialBoard.getComponentIds().size() &&
                savedBoard.getPadIds().size() == trialBoard.getPadIds().size() &&
                new TreeSet<String>(savedBoard.getComponentIds()).equals(
                    new TreeSet<String>(trialBoard.getComponentIds())) &&
                new TreeSet<String>(savedBoard.getPadIds()).equals(
                    new TreeSet<String>(trialBoard.getPadIds())),
            "raw candidate pair retains exact source inventory and pad IDs: " + seed);
        check(savedBoard.getPlacementConstraints().placementDensityVersion == 14 &&
                trialBoard.getPlacementConstraints().placementDensityVersion == 15,
            "raw candidate pair uses current density14 and trial density15: " + seed);
        check(PcbPostSelectionEdgeTrimContractTest.sourceIntentSha(savedPlan).equals(
                PcbPostSelectionEdgeTrimContractTest.sourceIntentSha(trialPlan)) &&
                PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard).equals(
                    PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),
            "raw candidate pair retains source intent and graph identity: " + seed);

        String savedSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(savedPlan);
        String trialSourceBefore = PcbCanvasBoundaryContractTest.sourcePlanSha256(trialPlan);
        String savedGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        check(!savedSourceBefore.equals(trialSourceBefore),
            "raw trial source plan carries its explicit interpretation metadata: " + seed);
        PcbPlacementPlanner planner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        for (int candidate : RAW_CANDIDATES) {
            RawBinding saved = rawBinding(planner, savedBoard, savedPlan, candidate);
            RawBinding trial = rawBinding(planner, trialBoard, trialPlan, candidate);
            boolean same = saved.canonical != null && trial.canonical != null ?
                saved.canonical.equals(trial.canonical) : saved.canonical == null &&
                    trial.canonical == null && saved.reason != null &&
                    saved.reason.equals(trial.reason);
            String status = saved.canonical == null && trial.canonical == null && same ?
                "same-rejection" : saved.canonical == null || trial.canonical == null ?
                    "different-outcome" : same ? "same-plan" : "different-plan";
            String savedHash = saved.canonical == null ? "rejected:" + saved.reason :
                PlayerSessionFingerprint.of(saved.canonical);
            String trialHash = trial.canonical == null ? "rejected:" + trial.reason :
                PlayerSessionFingerprint.of(trial.canonical);
            System.out.println("PCB_VACANCY_GRID_RAW {\"seed\":\"" +
                Long.toString(seed) + "\",\"candidate\":" + candidate +
                ",\"status\":\"" + status + "\",\"current15\":\"" +
                savedHash + "\",\"trial16\":\"" + trialHash +
                "\",\"same\":" + same + "}");
            check(same, "raw requested candidate plan/canonical binding is exact: seed=" +
                seed + " candidate=" + candidate + " saved=" + savedHash +
                " trial=" + trialHash);
        }

        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedPlan, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialPlan, trialBoard);
        check(savedSourceBefore.equals(
                PcbCanvasBoundaryContractTest.sourcePlanSha256(savedPlan)) &&
                trialSourceBefore.equals(
                    PcbCanvasBoundaryContractTest.sourcePlanSha256(trialPlan)),
            "raw candidate planning preserves both source plans: " + seed);
        check(savedGraphBefore.equals(
                PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard)) &&
                trialGraphBefore.equals(
                    PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),
            "raw candidate planning preserves both live source graphs: " + seed);
    }

    private static RawBinding rawBinding(PcbPlacementPlanner planner,
            TroubleshootBoard board, Rb56Plan plan, int candidate) {
        try {
            PcbPlacementPlanner.Plan placed = planner.plan(board,
                board.getPlacementConstraints(), plan.layoutSeed, candidate);
            String canonical = "candidate=" + placed.candidate + ";outline=" +
                placed.outline.x + "," + placed.outline.y + "," +
                placed.outline.width + "," + placed.outline.height +
                ";demand=" + placed.demandArea + ";courtyard=" +
                placed.courtyardArea + ";evaluations=" + placed.evaluations +
                ";geometry=" + placed.materialize().geometryFingerprint();
            return new RawBinding(canonical, null);
        } catch (PcbPlacementPlanner.Rejected rejected) {
            return new RawBinding(null, rejected.reason);
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, label);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class RawBinding {
        final String canonical;
        final String reason;
        RawBinding(String canonical, String reason) {
            this.canonical = canonical;
            this.reason = reason;
        }
    }
}
