package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Production serial admission. Provider recipes own replay, observations and
 * replacement selection; this service owns the invariant proof/restore boundary.
 * It has no family dispatch and is not a developer-verifier entrypoint.
 */
final class GeneratedDiagnosticProofService {
    private GeneratedDiagnosticProofService() { }

    static GeneratedDiagnosticProofReceipt prove(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController controller) {
        require(sim != null && owner != null && controller != null &&
                sim.getGeneratedBoardInstance() == owner && sim.getGeneratedChallengeController() == controller &&
                !owner.isDeveloperOnlyFaultRoute(), "Production proof requires its current normal owner");
        GeneratedDiagnosticSolvabilityAdmission.validate(sim, owner);
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        require(provider != null && provider.getProviderId() != null && provider.getProviderId().length() > 0,
            "Production proof has no identified diagnostic provider");
        GeneratedDiagnosticPlan plan = provider.getDiagnosticPlan();
        GeneratedDiagnosticProgram program = provider.getObservationProgram();
        require(program != null, "Production diagnostic provider has no executable observations");
        program.validatePlan(plan);
        require(owner.getDiagnosticSolvabilityContract().getPlans().size() == 1 &&
                GeneratedDiagnosticProgram.describePlan(plan).equals(GeneratedDiagnosticProgram.describePlan(
                    owner.getDiagnosticSolvabilityContract().getPlans().firstElement())),
            "Production provider plan differs from the admitted contract");
        Vector<GeneratedFaultCandidate> candidates = hypothesesFor(owner);
        Object attempt = controller.beginDiagnosticAdmission();
        long started = System.currentTimeMillis();
        try {
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = runIsolated(sim, owner,
                provider, plan, program, candidates);
            GeneratedDiagnosticProofReceipt receipt = new GeneratedDiagnosticProofReceipt(owner,
                controller, attempt, program, evidence, System.currentTimeMillis() - started);
            controller.completeDiagnosticAdmission(receipt, attempt);
            return receipt;
        } finally {
            controller.abortDiagnosticAdmission(attempt);
        }
    }

    static Vector<GeneratedFaultCandidate> hypothesesFor(GeneratedBoardInstance owner) {
        if (owner == null) throw new IllegalArgumentException("Missing diagnostic owner");
        Vector<GeneratedFaultCandidate> candidates = GeneratedFaultServiceabilityAdmission
            .getAdmittedCandidates(owner.getFaultCandidates());
        require(!candidates.isEmpty(), "Production diagnostic live owner has no admitted hypotheses");
        Collections.sort(candidates, new Comparator<GeneratedFaultCandidate>() {
            public int compare(GeneratedFaultCandidate a, GeneratedFaultCandidate b) {
                return a.getHypothesisKey().compareTo(b.getHypothesisKey());
            }
        });
        for (GeneratedFaultCandidate candidate : candidates)
            GeneratedFaultServiceabilityAdmission.validateCandidate(candidate);
        return candidates;
    }

    private static Vector<GeneratedDiagnosticSolvabilityEvidence> runIsolated(CirSim sim,
            GeneratedBoardInstance owner, GeneratedDiagnosticProvider provider,
            GeneratedDiagnosticPlan plan, GeneratedDiagnosticProgram program,
            Vector<GeneratedFaultCandidate> candidates) {
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        Vector<GeneratedBoardInstance> privateBoards = new Vector<GeneratedBoardInstance>();
        boolean guarded = false;
        Throwable failure = null;
        try {
            snapshot.beginProof(sim);
            GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
            guarded = true;
            for (GeneratedFaultCandidate hypothesis : candidates) {
                GeneratedBoardInstance candidate = provider.generateHypothesis(hypothesis);
                require(candidate != null && !candidate.isDeveloperOnlyFaultRoute(),
                    "Diagnostic provider replayed a missing or developer-only hypothesis");
                // Reusing any earlier physical graph can carry repair/damage state into
                // a later hypothesis. Check the player AND every earlier proof owner.
                FreshGeneratedRuntimeInstallation.requireDisjoint(owner, candidate);
                for (GeneratedBoardInstance previous : privateBoards)
                    FreshGeneratedRuntimeInstallation.requireDisjoint(previous, candidate);
                privateBoards.add(candidate);
                validateReplay(owner, candidate, provider, program, hypothesis);
                sim.installGeneratedChallengeForDeveloperVerification(candidate);
                require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                    "Production diagnostic proof attached a private board to the player UI");
                GeneratedRuntimeDeveloperSettlement.settle(sim, candidate, "production-diagnostic-candidate");
                GeneratedDiagnosticSolvabilityAdmission.validate(sim, candidate);
                GeneratedFaultServiceabilityAdmission.validateExecutableRuntime(sim, candidate, candidate.getFaultBinding());
                GeneratedDiagnosticExecutionTrace.Builder trace = GeneratedDiagnosticExecutionTrace.builder();
                Vector<GeneratedDiagnosticSample> samples = GeneratedDiagnosticObservationExecutor.collect(
                    sim, candidate, program, trace);
                sim.instrumentController.clearTargets();
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                GeneratedRuntimeDeveloperSettlement.settle(sim, candidate, "production-diagnostic-repair-start");
                performRepairAndRetest(sim, candidate, trace);
                GeneratedDiagnosticRepairSemantics semantics =
                    GeneratedDiagnosticRepairSemantics.forServiceability(candidate.getFaultServiceability());
                evidence.add(new GeneratedDiagnosticSolvabilityEvidence(
                    candidate.getCircuitFamilyId() + "/" + hypothesis.getFault().getType().name() + "/" +
                        hypothesis.getFault().getTargetComponentId(),
                    candidate.getCircuitFamilyId(), candidate.getSeed(), hypothesis.getHypothesisKey(),
                    candidates.size(), owner.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount(),
                    plan, samples, trace.freeze(semantics), true, "NONE", "PASS", "NONE", true, true,
                    !sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored() &&
                    sim.getBoardPowerController().getState() == BoardPowerState.POWERED));
            }
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            try {
                sim.instrumentController.clearTargets();
                sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                require(!sim.activeMeasurementOverlay, "Production diagnostic overlay cleanup failed");
            } catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            try {
                snapshot.restore(sim);
                snapshot.assertRestored(sim);
            } catch (Throwable restoration) {
                failure = retain(failure, restoration);
                sim.markGeneratedRuntimeFailure(sim.getGeneratedBoardInstance(), failure);
            } finally {
                if (guarded) GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        if (failure instanceof Error) throw (Error)failure;
        if (failure != null) throw new IllegalStateException("Production diagnostic proof failed", failure);
        require(sim.getGeneratedBoardInstance() == owner, "Production proof did not restore its exact original owner");
        return evidence;
    }

    private static void validateReplay(GeneratedBoardInstance owner, GeneratedBoardInstance candidate,
            GeneratedDiagnosticProvider provider, GeneratedDiagnosticProgram program,
            GeneratedFaultCandidate hypothesis) {
        GeneratedDiagnosticProvider replay = candidate.getDiagnosticProvider();
        require(owner.getCircuitFamilyId().equals(candidate.getCircuitFamilyId()) &&
                owner.getTopologyVariantId().equals(candidate.getTopologyVariantId()) &&
                owner.getSeed() == candidate.getSeed() && owner.getPcbLayout().geometryFingerprint().equals(
                    candidate.getPcbLayout().geometryFingerprint()),
            "Diagnostic hypothesis replay changed the recipe, topology or physical layout");
        require(replay != null && provider.getProviderId().equals(replay.getProviderId()) &&
                program.canonical().equals(replay.getObservationProgram().canonical()),
            "Diagnostic observation program depends on the selected hypothesis");
        require(candidate.getFaultBinding() != null && hypothesis.getHypothesisKey().equals(
                candidate.getFaultBinding().getFault().getHypothesisKey()),
            "Diagnostic replay selected a different canonical hypothesis");
        GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(candidate.getFaultCandidates(),
            owner.getDiagnosticSolvabilityContract().getHypothesisKeys());
        require(GeneratedDiagnosticRepairSemantics.forServiceability(hypothesis.getServiceability())
                .isEquivalentTo(GeneratedDiagnosticRepairSemantics.forServiceability(candidate.getFaultServiceability())),
            "Diagnostic replay changed the legal physical repair semantics");
    }

    private static void performRepairAndRetest(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticExecutionTrace.Builder trace) {
        String componentId = instance.getFaultLocus().getComponentId();
        PhysicalPart<?> originalPart = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        require(originalPart != null && originalPart.isInstalled(), "Diagnostic repair owner is not installed");
        require(instance.getFaultServiceability().getFaultClearingRepairActionIds()
                .contains(WorkbenchOperation.CATALOG_INSTALL),
            "Diagnostic provider has no supported fault-clearing replacement action");
        String catalogId = instance.getDiagnosticProvider().getCorrectCatalogId(instance, componentId);
        require(catalogId != null && catalogId.length() > 0, "Diagnostic provider has no legal replacement recipe");
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, originalPart));
        trace.recordRepairAction(WorkbenchOperation.REMOVE);
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "production-diagnostic-remove");
        dispatch(sim, WorkbenchOperation.forCatalog(componentId, catalogId));
        trace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "production-diagnostic-replace");
        require(instance.getPhysicalBoardRuntime().getInstalledPart(componentId) != originalPart,
            "Diagnostic replacement reused the original fault owner");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        trace.recordInputPowerTransition("BOARD_POWER_ON_RETEST");
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "production-diagnostic-retest-power");
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Diagnostic replacement failed the device's electrical repair behavior");
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "production-diagnostic-retest-ready");
        GeneratedCustomerRetestResult retest = challenge.performCustomerRetest();
        require(retest != null && retest.isPassed() && challenge.getCustomerRetestResult() == retest,
            "Diagnostic repair did not publish a passed real customer retest");
        trace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
        require(sim.getGeneratedBoardInstance() == instance && !sim.activeMeasurementOverlay &&
                sim.getBoardModificationController().isFullyRestored() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Diagnostic retest lost its owner, unaffected function or restored physical state");
    }

    private static void dispatch(CirSim sim, WorkbenchOperation operation) {
        require(sim.pcbWorkbenchController.isAvailable(operation) && sim.pcbWorkbenchController.dispatch(operation),
            "Diagnostic repair action is unavailable: " + operation.getId());
    }
    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
