package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused, developer-only proof for the Quick Play session boundary. */
final class QuickPlayDeveloperVerifier {
    private QuickPlayDeveloperVerifier() { }

    static void verify(CirSim sim) {
        require(sim.isQuickPlayMode(), "Quick Play verifier did not enter Quick Play mode");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        QuickPlaySelection selection = sim.getQuickPlaySelectionForDeveloperVerification();
        require(instance != null && challenge != null && challenge.isReady() && selection != null,
            "Quick Play challenge did not become ready");
        require(sim.pcbWorkbenchController != null &&
            sim.pcbWorkbenchController.getPlayerFacingTextForDeveloperVerification().indexOf(
                "Finish Job") >= 0,
            "Quick Play workbench did not expose the Finish Job boundary");
        verifyEligibleFamilies();
        require(selection.getFamilyId().equals(instance.getCircuitFamilyId()) &&
            selection.getSeed() == instance.getSeed() && instance.getSeed() ==
            instance.getChallengeDefinition().getSelectionSeed(),
            "Quick Play selection was not passed to the deterministic generator");
        verifyDeterministicFamilySelection();
        require(instance.getFaultBinding().getFault().getType() != GeneratedFaultType.DIODE_SHORT,
            "Quick Play selected the developer-only diode short fault");
        verifyFreshSessionBoundary();
        verifyUnrepairedFinishDoesNotAdvance(sim, challenge);
        verifyCorrectRepairCanFinish(sim, challenge, instance);
        verifyNormalPlayerPrivacy(sim);
        sim.publishQuickPlayVerificationReportForDeveloperVerification(
            "unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated");
        sim.setCircuitTitle("Quick Play verification passed");
    }

    static void verifyExplicitRoute(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(!sim.isQuickPlayMode() &&
            sim.getQuickPlaySelectionForDeveloperVerification() == null,
            "Explicit route was replaced by Quick Play selection");
        require(instance != null && challenge != null && challenge.isReady() &&
            "LED_INDICATOR".equals(instance.getCircuitFamilyId()) && instance.getSeed() == 3,
            "Explicit challenge route did not preserve its family and seed");
        sim.setCircuitTitle("Quick Play explicit-route verification passed");
    }

    private static void verifyEligibleFamilies() {
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        require(families.size() == 3 &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("LED_INDICATOR") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("DIODE_PROTECTED_INDICATOR") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("PARALLEL_DUAL_INDICATOR"),
            "Quick Play eligible-family registry changed");
        require(!QuickPlayFamilyRegistry.isNormalPlayerEligible("DIODE_SHORT") &&
            !QuickPlayFamilyRegistry.isNormalPlayerEligible("TASK_36_CAPACITOR"),
            "Quick Play registry admitted a developer or future family");
    }

    private static void verifyFreshSessionBoundary() {
        QuickPlaySession firstSession = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 0, 3 }));
        QuickPlaySession nextSession = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 1, 2 }));
        QuickPlaySelection first = firstSession.getSelection();
        QuickPlaySelection next = nextSession.getSelection();
        GeneratedBoardInstance firstBoard = firstSession.getInstance();
        GeneratedBoardInstance nextBoard = nextSession.getInstance();
        require(!first.getFamilyId().equals(next.getFamilyId()) &&
            first.getSeed() != next.getSeed() && firstBoard != nextBoard &&
            firstBoard.getBoard() != nextBoard.getBoard() &&
            firstBoard.getPhysicalBoardRuntime() != nextBoard.getPhysicalBoardRuntime(),
            "Quick Play next session reused board, runtime, family, or seed state");
        verifyFreshBoardState(firstBoard);
        verifyFreshBoardState(nextBoard);
    }

    private static void verifyFreshBoardState(GeneratedBoardInstance instance) {
        BoardModificationController modifications =
            new BoardModificationController(null, instance);
        require(modifications.isFullyRestored(),
            "Quick Play session did not create fresh modification state");
        Vector<PhysicalBoardSlot> slots = instance.getPhysicalBoardRuntime().getSlots();
        require(!slots.isEmpty() && !instance.getPhysicalBoardRuntime().getPhysicalParts().isEmpty(),
            "Quick Play session did not create fresh physical state");
        for (PhysicalBoardSlot slot : slots) {
            if (slot.isOccupied())
                require(isFreshPhysicalPart(slot.getInstalledPart()) &&
                    slot.getInstalledPart().isInstalled() &&
                    slot.getInstalledPart().getBoardSlot() == slot,
                    "Quick Play session retained a non-fresh physical slot state");
        }
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts())
            require(isFreshPhysicalPart(part) && (!part.isInstalled() || part.getBoardSlot() != null),
                "Quick Play session retained a replacement or detached physical part");
    }

    private static boolean isFreshPhysicalPart(PhysicalPart<?> part) {
        String kind = part.getProvenance().getKind();
        return PhysicalPartProvenance.GENERATED_ORIGINAL.equals(kind) ||
            PhysicalPartProvenance.FIXED_GENERATED.equals(kind);
    }

    private static void verifyDeterministicFamilySelection() {
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        for (int i = 0; i < families.size(); i++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { i, 3 }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(families.elementAt(i).equals(selection.getFamilyId()) &&
                generated.getCircuitFamilyId().equals(selection.getFamilyId()) &&
                generated.getSeed() == selection.getSeed() &&
                generated.getFaultBinding().getFault().getType() != GeneratedFaultType.DIODE_SHORT,
                "Quick Play family did not generate through its deterministic normal route");
        }
    }

    private static void verifyUnrepairedFinishDoesNotAdvance(CirSim sim,
            GeneratedChallengeController challenge) {
        require(challenge.getState() == GeneratedChallengeState.READY &&
            !sim.finishQuickPlayJob() && !challenge.isCompleted() &&
            challenge.getState() == GeneratedChallengeState.READY,
            "Finish Job advanced an unrepaired challenge");
    }

    private static void verifyCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        require("LED_INDICATOR".equals(instance.getCircuitFamilyId()) && instance.getSeed() == 3,
            "Quick Play verification selection is not the deterministic LED proof");
        ResistorSlotController slots = sim.getResistorSlotController();
        require(slots != null, "Quick Play LED proof has no resistor capability");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() &&
            slots.installNewFromCatalog("R_CATALOG_1000"),
            "Quick Play correct physical repair was not accepted");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.verifyGeneratedBoard();
        require(challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED, false) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED && sim.finishQuickPlayJob() &&
            challenge.isCompleted(),
            "Correctly restored Quick Play challenge did not finish through generic status");
    }

    private static void verifyNormalPlayerPrivacy(CirSim sim) {
        String text = sim.pcbWorkbenchController == null ? "" :
            sim.pcbWorkbenchController.getPlayerFacingTextForDeveloperVerification();
        String lower = text.toLowerCase();
        String[] hiddenTerms = { "fault", "stress", "damage", "rating",
            "specification", "answer" };
        String leakedTerms = "";
        for (String term : hiddenTerms)
            if ("rating".equals(term) ? lower.matches(".*\\brating\\b.*") :
                    lower.indexOf(term) >= 0)
                leakedTerms += term + " ";
        require(leakedTerms.length() == 0,
            "Quick Play normal-player UI exposed hidden metadata terms: " + leakedTerms);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
