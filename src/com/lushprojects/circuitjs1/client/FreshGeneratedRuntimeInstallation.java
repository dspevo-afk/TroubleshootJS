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

    /**
     * A10 keeps one disjoint candidate private across browser turns. The saved
     * original is never stepped or modified. Only publish attaches the candidate;
     * abort restores the existing exact snapshot boundary.
     */
    static final class Staged {
        enum PreparationPoint { BEFORE_DETACH, AFTER_DETACH, AFTER_FLAGS, AFTER_GRAPH, AFTER_INSTALL }
        private static PreparationPoint injectedPreparationFailure;
        private static boolean injectedCleanupFailure;
        private final CirSim sim;
        private final GeneratedBoardInstance candidate, original;
        private final Vector<CircuitElm> originalGraph;
        private final GeneratedChallengeController originalController;
        private final Task41SimulationSnapshot snapshot;
        private final boolean originalDeveloperScope, originalRunning, originalQuickPlay;
        private final QuickPlaySession originalSession;
        private Vector<CircuitElm> privateGraph;
        private final Vector<CircuitElm> disposedElements = new Vector<CircuitElm>();
        private GeneratedChallengeController candidateController;
        private boolean begun, preparing, finished, preparationComplete, restoring;

        static void setPreparationFailureForDeveloperVerification(PreparationPoint point) {
            injectedPreparationFailure = point;
        }
        static void setCleanupFailureForDeveloperVerification(boolean fail) {
            injectedCleanupFailure = fail;
        }
        private void preparationReached(PreparationPoint point) {
            if (point == injectedPreparationFailure)
                throw new IllegalStateException("Injected generation preparation failure after " + point);
        }

        Staged(CirSim sim, GeneratedBoardInstance candidate) {
            if (sim == null || candidate == null || active != null ||
                    !sim.isGeneratedRuntimeSettled() || candidate.isDeveloperOnlyFaultRoute())
                throw new IllegalStateException("Staged installation requires a settled normal owner");
            this.sim = sim; this.candidate = candidate;
            original = sim.getGeneratedBoardInstance();
            originalGraph = sim.elmList;
            originalController = sim.getGeneratedChallengeController();
            if (original != null) requireDisjoint(original, candidate);
            for (CircuitElm element : candidate.getSimulationElements())
                if (sim.elmList.contains(element))
                    throw new IllegalArgumentException("Generation reused a current solver element");
            snapshot = Task41SimulationSnapshot.captureForFreshInstallation(sim);
            originalDeveloperScope = sim.developerVerifierRunning;
            originalRunning = sim.simIsRunning();
            originalQuickPlay = sim.quickPlayActive;
            originalSession = sim.quickPlaySession;
        }

        void prepare(boolean quickPlay) {
            if (begun || finished || active != null || sim.getGeneratedBoardInstance() != original)
                throw new IllegalStateException("Stale generation preparation");
            active = sim; begun = true; preparing = true;
            preparationReached(PreparationPoint.BEFORE_DETACH);
            snapshot.beginProof(sim);
            preparationReached(PreparationPoint.AFTER_DETACH);
            sim.generatedRuntimeInstallationInProgress = true;
            sim.developerVerifierRunning = true;
            sim.setSimRunning(false);
            sim.quickPlayActive = quickPlay;
            sim.quickPlaySession = null;
            preparationReached(PreparationPoint.AFTER_FLAGS);
            if (original != null) {
                original.getExternalPowerBindings().setConnected(false);
                if (!original.getExternalPowerBindings().areAllDisconnected())
                    throw new IllegalStateException("Protected generation owner retained external power");
            }
            privateGraph = new Vector<CircuitElm>();
            sim.elmList = privateGraph;
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.scopes = new Scope[20]; sim.scopeColCount = new int[20]; sim.scopeCount = 0;
            sim.dragElm = null; sim.menuElm = null; sim.heldSwitchElm = null;
            preparationReached(PreparationPoint.AFTER_GRAPH);
            sim.installGeneratedChallengeForDeveloperVerification(candidate);
            candidateController = sim.getGeneratedChallengeController();
            preparing = false;
            preparationReached(PreparationPoint.AFTER_INSTALL);
            sim.getGeneratedChallengeController().deferGenerationPresentation();
            enterStep();
            if (candidate.getTemporalBehavior() != null) {
                GeneratedRuntimeDeveloperSettlement.settleHealthy(sim, candidate, "generation-healthy-profile");
                return;
            }
            finishPreparation();
        }
        boolean isPreparationComplete() { return preparationComplete; }
        void finishPreparation() {
            if (preparationComplete) throw new IllegalStateException("Generation preparation is already complete");
            enterStep();
            GeneratedRuntimeDeveloperSettlement.settle(sim, candidate, "generation-healthy-and-faulted");
            sim.getGeneratedChallengeController().prepareGenerationInputState();
            if (!sim.isGeneratedRuntimeSettled())
                GeneratedRuntimeDeveloperSettlement.settle(sim, candidate, "generation-scenario-inputs");
            preparationComplete = true;
        }

        boolean ownsCandidate() {
            return begun && !finished && active == sim && sim.getGeneratedBoardInstance() == candidate &&
                sim.elmList == privateGraph && (preparing ||
                sim.getGeneratedChallengeController() == candidateController);
        }
        void enterStep() {
            if (!ownsCandidate()) throw new IllegalStateException("Stale generation stage owner");
            sim.generatedRuntimeInstallationInProgress = false;
            sim.developerVerifierRunning = true;
        }
        void pause() {
            if (ownsCandidate()) sim.generatedRuntimeInstallationInProgress = true;
        }
        void validatePhysical() {
            enterStep();
            candidate.getBoard().validate();
            candidate.getPhysicalBoardRuntime().validateCommittedState(candidate,
                sim.getBoardModificationController(), sim.elmList);
            candidate.getPcbLayout().validateGeometry(candidate.getBoard());
            PcbConductorProjection.audit(candidate, sim.elmList);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, candidate);
        }
        void publish() {
            enterStep();
            if (sim.getGeneratedChallengeController().getDiagnosticProofReceipt() == null ||
                    sim.getGeneratedChallengeController().getScenario() == null ||
                    !sim.isGeneratedRuntimeSettled())
                throw new IllegalStateException("Generation publication has incomplete qualification");
            validatePhysical();
            reached(sim, Stage.VALIDATION);
            sim.pcbWorkbenchController.attachToSidebar(sim.verticalPanel);
            reached(sim, Stage.WORKBENCH);
            sim.generatedRuntimeInstallationInProgress = false;
            sim.developerVerifierRunning = originalDeveloperScope;
            sim.setSimRunning(originalRunning);
            sim.refreshChallengeInteractionState();
            if (!sim.isGeneratedRuntimeSettled())
                throw new IllegalStateException("Published generation is not actionable");
            if (original != null) {
                // Reversible until commit: abort restores the saved power state.
                original.getExternalPowerBindings().setConnected(false);
                if (!original.getExternalPowerBindings().areAllDisconnected())
                    throw new IllegalStateException("Retired generation owner retained external power");
            }
            finished = true; active = null;
        }
        void abort() {
            if (finished) return;
            if (injectedCleanupFailure)
                throw new IllegalStateException("Injected staged cleanup failure");
            if (!begun) { disposeCandidate(); finished = true; return; }
            boolean originalPreparation = preparing && sim.getGeneratedBoardInstance() == original &&
                sim.getGeneratedChallengeController() == originalController &&
                (sim.elmList == originalGraph || sim.elmList == privateGraph);
            boolean originalRestoration = restoring && sim.getGeneratedBoardInstance() == original &&
                sim.getGeneratedChallengeController() == originalController && sim.elmList == originalGraph;
            if (active != sim || (!ownsCandidate() && !originalPreparation && !originalRestoration))
                throw new IllegalStateException("Refusing to restore generation over a successor owner");
            Throwable failure = null;
            try { candidate.getExternalPowerBindings().setConnected(false); }
            catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            try { sim.invalidateGeneratedOwnerWork(); }
            catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            try { sim.setSimRunning(false); }
            catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            try { disposeCandidate(); }
            catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            // Retain the exact graph until all owned resources are disposed.
            // A later guarded retry may finish cleanup; never orphan a graph.
            rethrowCleanup(failure);
            try {
                restoring = true;
                sim.quickPlayActive = originalQuickPlay;
                sim.quickPlaySession = originalSession;
                snapshot.restore(sim); snapshot.assertRestored(sim);
            } catch (Throwable restoration) {
                failure = retain(failure, restoration);
                sim.markGeneratedRuntimeFailure(sim.getGeneratedBoardInstance(), failure);
            }
            rethrowCleanup(failure);
            finished = true; active = null;
        }
        private void rethrowCleanup(Throwable failure) {
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null) throw new IllegalStateException("Generation cleanup failed", failure);
        }
        private void disposeCandidate() {
            Throwable failure = null;
            for (CircuitElm element : candidate.getSimulationElements()) {
                if (disposedElements.contains(element)) continue;
                if (originalGraph.contains(element)) {
                    failure = retain(failure, new IllegalStateException("Candidate cleanup reached the protected graph"));
                    continue;
                }
                try { element.delete(); disposedElements.add(element); }
                catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null) throw new IllegalStateException("Candidate disposal failed", failure);
        }
    }

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
