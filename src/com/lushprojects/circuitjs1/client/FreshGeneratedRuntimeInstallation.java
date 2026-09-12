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

    /** Player publication always runs the nonempty, executable diagnostic proof. */
    static void installNormalComposition(CirSim sim, GeneratedBoardInstance candidate,
            boolean attachWorkbench) {
        if (sim == null || candidate == null || candidate.isDeveloperOnlyFaultRoute() ||
                GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning())
            throw new IllegalArgumentException("Normal composition requires an independent player candidate");
        candidate.getPhysicalBoardRuntime().validateSupportedCompositionProviders();
        GeneratedDiagnosticSolvabilityAdmission.validate(candidate);
        install(sim, candidate, attachWorkbench, true);
    }

    static boolean isInProgress(CirSim sim) { return active == sim; }

    static void install(CirSim sim, GeneratedBoardInstance candidate, boolean attachWorkbench) {
        install(sim, candidate, attachWorkbench, false);
    }

    private static void install(CirSim sim, GeneratedBoardInstance candidate,
            boolean attachWorkbench, boolean normalAdmission) {
        if (sim == null || candidate == null || active != null ||
                !sim.isGeneratedRuntimeSettled() ||
                ((!normalAdmission || sim.getGeneratedBoardInstance() != null) &&
                (sim.getGeneratedChallengeController() == null ||
                !sim.getGeneratedChallengeController().isReady())))
            throw new IllegalStateException("Fresh installation requires a settled generated owner");
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        if (original != null)
            requireDisjoint(original, candidate);
        for (CircuitElm element : candidate.getSimulationElements())
            if (sim.elmList.contains(element))
                throw new IllegalArgumentException("Fresh installation reused a live solver element");
        candidate.getBoard().validate();
        candidate.getPhysicalBoardRuntime().validate();
        if (candidate.getPcbLayout() != null)
            candidate.getPcbLayout().validateGeometry(candidate.getBoard());
        Task41SimulationSnapshot snapshot = normalAdmission ?
            Task41SimulationSnapshot.captureForFreshInstallation(sim) :
            Task41SimulationSnapshot.capture(sim);
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
            if (normalAdmission) {
                // The candidate is still detached. Task 41 owns a synchronous
                // fresh-proof graph and must execute real guarded workbench
                // operations there. The outer active installation prevents
                // nested publication; stale player handlers retain old owners.
                sim.generatedRuntimeInstallationInProgress = false;
                sim.developerVerifierRunning = originalDeveloperScope;
                try {
                    GeneratedDiagnosticSolvabilityAdmission.validateLive(sim, candidate,
                        sim.getGeneratedChallengeController());
                    if (sim.getGeneratedBoardInstance() != candidate ||
                            !sim.isGeneratedRuntimeSettled())
                        throw new IllegalStateException("Diagnostic admission did not restore the candidate");
                } finally {
                    sim.generatedRuntimeInstallationInProgress = true;
                    sim.developerVerifierRunning = true;
                }
                candidate.getPhysicalBoardRuntime().validateCommittedState(candidate,
                    sim.getBoardModificationController(), sim.elmList);
            }
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
                    if (sim.getGeneratedBoardInstance() == null)
                        sim.generatedRuntimeInstallationInProgress = true;
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
                original.getFamilyState() == candidate.getFamilyState() ||
                shares(original.getPcbLayout(), candidate.getPcbLayout()) ||
                shares(original.getOperationalStates(), candidate.getOperationalStates()) ||
                shares(original.getTemporalBehavior(), candidate.getTemporalBehavior()) ||
                shares(original.getChallengeDefinition(), candidate.getChallengeDefinition()))
            throw new IllegalArgumentException("Fresh installation cannot reuse mutable owners");
        if (candidate.getOperationalStates() != null)
            candidate.getOperationalStates().requireOwnedBy(candidate.getSimulationElements());
        if (candidate.getChallengeDefinition() != null &&
                candidate.getChallengeDefinition().getFaultBinding() != candidate.getFaultBinding())
            throw new IllegalArgumentException("Fresh challenge references a foreign fault owner");
        requireSeparateExecutionOwners(original, candidate);
        if (candidate.getFamilyState() != null) candidate.getFamilyState().requireOwnedBy(candidate);
        if (candidate.getTemporalBehavior() != null) candidate.getTemporalBehavior().requireOwnedBy(candidate);
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

    private static void requireSeparateExecutionOwners(GeneratedBoardInstance original,
            GeneratedBoardInstance candidate) {
        GeneratedChallengeDefinition definition = candidate.getChallengeDefinition();
        if (definition != null) {
            if (definition.getBehaviorContract() != candidate.getBehaviorContract())
                throw new IllegalArgumentException("Fresh definition references a foreign behavior owner");
            definition.getScenarioCatalog().requireExecutionOwner(candidate.getBehaviorContract());
        }
        Vector<Object> prior = executionOwners(original);
        for (Object current : executionOwners(candidate))
            for (Object old : prior)
                if (shares(current, old))
                    throw new IllegalArgumentException("Fresh installation reused a behavior callback owner");
        if (candidate.getDiagnosticProvider() instanceof GeneratedDiagnosticExecutionProvider) {
            ConstructionReceipt receipt = ((GeneratedDiagnosticExecutionProvider)candidate.getDiagnosticProvider())
                .getConstructionReceipt();
            if (receipt == null || !receipt.belongsToFinishedContext(receipt.getSpec(), candidate.getBoard()) ||
                    receipt.getComponentBindings() != candidate.getComponentBindings() ||
                    receipt.getPowerBindings() != candidate.getExternalPowerBindings() ||
                    receipt.getConnectionBindings() != candidate.getConnectionBindings())
                throw new IllegalArgumentException("Fresh diagnostic behavior references foreign construction");
        }
    }

    /** Current executable entrypoints, not immutable diagnostic plans or descriptive metadata. */
    private static Vector<Object> executionOwners(GeneratedBoardInstance board) {
        Vector<Object> owners = new Vector<Object>();
        owners.add(board.getBehaviorContract()); owners.add(board.getDiagnosticProvider());
        GeneratedChallengeDefinition definition = board.getChallengeDefinition();
        if (definition != null) {
            owners.add(definition); owners.add(definition.getBehaviorContract());
            definition.getScenarioCatalog().appendExecutionOwners(owners);
        }
        GeneratedBoardOperationCatalog operations = board.getOperationCatalog();
        if (operations != null) {
            owners.add(operations);
            for (GeneratedBoardOperation operation : operations.getAll()) operation.appendExecutionOwners(owners);
        }
        GeneratedCustomerRetestProfile retest = board.getCustomerRetestProfile();
        if (retest != null) retest.appendExecutionOwners(owners);
        if (board.getDiagnosticProvider() instanceof GeneratedDiagnosticExecutionProvider)
            owners.add(((GeneratedDiagnosticExecutionProvider)board.getDiagnosticProvider()).getConstructionReceipt());
        return owners;
    }

    private static boolean shares(Object first, Object second) {
        return first != null && first == second;
    }

    private static Throwable retain(Throwable original, Throwable cleanup) {
        if (original == null) return cleanup;
        if (cleanup != original) original.addSuppressed(cleanup);
        return original;
    }
}
