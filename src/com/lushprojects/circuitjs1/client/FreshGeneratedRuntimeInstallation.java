package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Bounded fresh-owner publication. The old workbench is detached and none of
 * its physical/solver objects may be reused by the candidate. Task 41 restores
 * that untouched owner on failure; it is not a live same-owner transaction.
 */
final class FreshGeneratedRuntimeInstallation {
    enum Stage { CAPABILITIES, POWER, CHALLENGE, VALIDATION, WORKBENCH }
    private static CirSim active;
    private static Stage injectedFailure;

    private FreshGeneratedRuntimeInstallation() { }

    static void setFailureForDeveloperVerification(Stage stage) { injectedFailure = stage; }

    static void reached(CirSim sim, Stage stage) {
        if (active == sim && stage == injectedFailure)
            throw new IllegalStateException("Injected fresh installation failure after " + stage);
    }

    /** Composition entry: unsupported mutable providers reject before publication. */
    static void installComposition(CirSim sim, GeneratedBoardInstance candidate,
            boolean attachWorkbench) {
        if (sim == null || candidate == null)
            throw new IllegalArgumentException("Missing composition installation context");
        candidate.getPhysicalBoardRuntime().validateSupportedCompositionProviders();
        install(sim, candidate, attachWorkbench);
    }

    static void install(CirSim sim, GeneratedBoardInstance candidate, boolean attachWorkbench) {
        if (sim == null || candidate == null || active != null ||
                !sim.isGeneratedRuntimeSettled() || sim.getGeneratedChallengeController() == null ||
                !sim.getGeneratedChallengeController().isReady())
            throw new IllegalStateException("Fresh installation requires a settled generated owner");
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        requireDisjoint(original, candidate);
        candidate.getBoard().validate();
        candidate.getPhysicalBoardRuntime().validate();
        if (candidate.getPcbLayout() != null)
            candidate.getPcbLayout().validateGeometry(candidate.getBoard());
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        boolean originalDeveloperScope = sim.developerVerifierRunning;
        boolean originalRunning = sim.simIsRunning();
        boolean committed = false;
        Throwable failure = null;
        active = sim;
        try {
            snapshot.beginProof(sim);
            sim.generatedRuntimeInstallationInProgress = true;
            sim.developerVerifierRunning = true;
            sim.setSimRunning(false);
            // The legacy attachment path clears these vectors and deletes
            // their elements. Give it private containers before invoking it.
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.installGeneratedChallengeForDeveloperVerification(candidate);
            sim.setSimRunning(true);
            for (int attempt = 0; attempt < 20; attempt++) {
                sim.updateCircuit();
                if (sim.getGeneratedChallengeController().isReady() &&
                        !sim.generatedBoardVerificationPending && !sim.analyzeFlag)
                    break;
            }
            if (!sim.getGeneratedChallengeController().isReady() ||
                    sim.generatedBoardVerificationPending || sim.analyzeFlag ||
                    sim.activeMeasurementOverlay || sim.stopMessage != null)
                throw new IllegalStateException("Fresh candidate did not settle through CircuitJS");
            candidate.getPhysicalBoardRuntime().validateCommittedState(candidate,
                sim.getBoardModificationController(), sim.elmList);
            reached(sim, Stage.VALIDATION);
            if (attachWorkbench && sim.pcbWorkbenchController != null)
                sim.pcbWorkbenchController.attachToSidebar(sim.verticalPanel);
            reached(sim, Stage.WORKBENCH);
            sim.generatedRuntimeInstallationInProgress = false;
            sim.developerVerifierRunning = originalDeveloperScope;
            sim.setSimRunning(originalRunning);
            sim.refreshChallengeInteractionState();
            if (!sim.isGeneratedRuntimeSettled())
                throw new IllegalStateException("Fresh candidate publication is not actionable");
            committed = true;
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            active = null;
            if (!committed) {
                // Candidate objects are disjoint, so disposal never deletes
                // the player's original graph or clears its inventory/faults.
                try {
                    candidate.getExternalPowerBindings().setConnected(false);
                    sim.invalidateGeneratedOwnerWork();
                    sim.setSimRunning(false);
                    for (CircuitElm element : candidate.getSimulationElements()) element.delete();
                } catch (Throwable cleanup) {
                    failure = retain(failure, cleanup);
                }
                try {
                    snapshot.restore(sim);
                    snapshot.assertRestored(sim);
                } catch (Throwable restoration) {
                    failure = retain(failure, restoration);
                    sim.markGeneratedRuntimeFailure(sim.getGeneratedBoardInstance(), failure);
                }
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw new IllegalStateException("Fresh installation failed", failure);
    }

    static void requireDisjoint(GeneratedBoardInstance original, GeneratedBoardInstance candidate) {
        if (original == null || candidate == null || original == candidate ||
                original.getBoard() == candidate.getBoard() ||
                original.getPhysicalBoardRuntime() == candidate.getPhysicalBoardRuntime() ||
                original.getSimulationBindings() == candidate.getSimulationBindings() ||
                original.getComponentBindings() == candidate.getComponentBindings() ||
                original.getConnectionBindings() == candidate.getConnectionBindings() ||
                original.getExternalPowerBindings() == candidate.getExternalPowerBindings() ||
                original.getFaultBinding() == candidate.getFaultBinding() ||
                original.getFamilyState() == candidate.getFamilyState())
            throw new IllegalArgumentException("Fresh installation cannot reuse mutable owners");
        for (CircuitElm element : candidate.getSimulationElements())
            if (original.getSimulationElements().contains(element))
                throw new IllegalArgumentException("Fresh installation reused a solver element");
        for (PhysicalPart<?> part : candidate.getPhysicalBoardRuntime().getPhysicalParts())
            for (PhysicalPart<?> old : original.getPhysicalBoardRuntime().getPhysicalParts()) {
                if (old == part || old.getMountState() == part.getMountState())
                    throw new IllegalArgumentException("Fresh installation reused a physical part");
                for (PhysicalPartTerminal terminal : part.getTerminals())
                    for (PhysicalPartTerminal prior : old.getTerminals())
                        if (terminal == prior || terminal.getEndpoint() == prior.getEndpoint())
                            throw new IllegalArgumentException("Fresh installation reused a terminal endpoint");
            }
        for (PhysicalBoardRuntimeCapability capability : candidate.getPhysicalBoardRuntime().getCapabilities())
            if (original.getPhysicalBoardRuntime().getCapabilities().contains(capability))
                throw new IllegalArgumentException("Fresh installation reused a mutable capability");
    }

    private static Throwable retain(Throwable original, Throwable cleanup) {
        if (original == null) return cleanup;
        if (cleanup != original) original.addSuppressed(cleanup);
        return original;
    }
}
