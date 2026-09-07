package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickHandler;

/** Bounded compiled qualification. Player evidence is collected separately. */
final class Task48DeveloperVerifier {
    private static final long[] SEEDS = { 0, 1, 2, 3, Long.MIN_VALUE,
        Long.MAX_VALUE, 9007199254740993L, -9007199254740993L };
    private static final Vector<GeneratedBoardInstance> candidates = new Vector<GeneratedBoardInstance>();
    private static final Vector<GeneratedBoardInstance> disposed = new Vector<GeneratedBoardInstance>();
    private static int assertions;
    private static int measurements;

    private Task48DeveloperVerifier() { }

    static String verify(CirSim sim, String seedText, boolean forcedFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route is required");
        if (forcedFailure) throw new AssertionError("task48-explicit-assertion-canary");
        assertions = measurements = 0;
        candidates.clear();
        disposed.clear();
        String canonicalSeed = seedText == null ? "0" : seedText;
        long requestedSeed = Long.parseLong(canonicalSeed);
        require(Long.toString(requestedSeed).equals(canonicalSeed), "noncanonical signed seed");
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        require(originalOwner != null && sim.getGeneratedChallengeController().isReady() &&
            sim.isGeneratedRuntimeSettled(), "settled original owner is required");
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        boolean priorQuickPlay = sim.quickPlayActive;
        QuickPlaySession priorSession = sim.quickPlaySession;
        StringBuilder cases = new StringBuilder();
        HashSet<String> repaired = new HashSet<String>();
        Throwable primary = null;
        try {
            for (long seed : SEEDS) {
                restore(sim, original, originalOwner, priorQuickPlay, priorSession);
                if (cases.length() != 0) cases.append(',');
                cases.append(verifySeed(sim, seed, repaired));
            }
            require(repaired.size() == 2, "finite replay corpus did not repair both block owners");
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            verifyAdmissionRejections(sim);
            String construction = verifyConstructionFailures(sim, requestedSeed);
            String installation = verifyInstallationFailures(sim, requestedSeed, original);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String detached = verifyDetachedInitialOwner(sim, original, requestedSeed);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String succession = verifySuccession(sim, requestedSeed);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            return "{\"protocol\":\"TSJ-TASK48-1\",\"status\":\"PASS\",\"requestedSeed\":" +
                q(canonicalSeed) + ",\"cases\":[" + cases + "],\"construction\":" + construction +
                ",\"installation\":" + installation + ",\"detachedInitialOwner\":" + detached +
                ",\"succession\":" + succession + ",\"assertions\":" + assertions +
                ",\"measurementCases\":" + measurements + ",\"repairedOwners\":" + repaired.size() +
                ",\"originalOwnerRestored\":true,\"candidateCleanup\":\"PASS\"}";
        } catch (Throwable problem) {
            primary = problem;
            problem.printStackTrace();
            rethrow(problem);
            return null;
        } finally {
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            ResistorMutationScope.clearFailureHookForDeveloperVerification();
            ResistorMutationScope.clearAbortFailureHookForDeveloperVerification();
            try {
                restore(sim, original, originalOwner, priorQuickPlay, priorSession);
                for (GeneratedBoardInstance candidate : candidates) dispose(sim, candidate, originalOwner);
                original.assertRestored(sim);
            } catch (Throwable cleanup) {
                if (primary != null) throw new IllegalStateException("Task48 original failure: " +
                    primary.getMessage() + "; cleanup failure: " + cleanup.getMessage(), primary);
                rethrow(cleanup);
            } finally {
                candidates.clear();
                disposed.clear();
            }
        }
    }

    private static String verifySeed(CirSim sim, long seed, HashSet<String> repaired) {
        GeneratedBoardInstance before = sim.getGeneratedBoardInstance();
        String circuitBefore = sim.dumpCircuit();
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forControlledIndicator(seed);
        ChallengeDescriptor descriptor = ChallengeDescriptor.parse(request.getDescriptor().toCanonical());
        require(descriptor.getRootSeed() == seed, "descriptor lost signed-long seed");
        BoundedGeneratedBoardAssembler.Result direct = assemble(request);
        BoundedGeneratedBoardAssembler.Result replay = assemble(BoundedAssemblyRequest.forControlledIndicator(descriptor));
        List<ElectricalBlockContract> blocks = new ArrayList<ElectricalBlockContract>(request.getBlocks());
        List<ElectricalConnection> connections = new ArrayList<ElectricalConnection>(request.getConnections());
        List<DeviceAdapterContract> adapters = new ArrayList<DeviceAdapterContract>(request.getDeviceAdapters());
        Collections.reverse(blocks);
        Collections.reverse(connections);
        Collections.reverse(adapters);
        BoundedGeneratedBoardAssembler.Result reversed = assemble(new BoundedAssemblyRequest(
            descriptor, blocks, connections, adapters));
        require(before == sim.getGeneratedBoardInstance() && circuitBefore.equals(sim.dumpCircuit()),
            "private composition changed the live owner");
        FreshGeneratedRuntimeInstallation.requireDisjoint(direct.getInstance(), replay.getInstance());
        FreshGeneratedRuntimeInstallation.requireDisjoint(direct.getInstance(), reversed.getInstance());
        require(snapshot(direct).equals(snapshot(replay)) && snapshot(direct).equals(snapshot(reversed)),
            "descriptor replay or reordered inputs changed the runtime construction");
        install(sim, direct.getInstance(), false);
        verifyNormalAdmission(sim, direct.getInstance());
        double originalGate = voltage(direct.getInstance(), "driver", "Q1.G");
        install(sim, replay.getInstance(), false);
        verifyNormalAdmission(sim, replay.getInstance());
        String admission = admissionReceipt();
        GeneratedBoardInstance board = replay.getInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        challenge.beginDeveloperVerificationScope();
        command(sim, board, true);
        require(close(voltage(board, "driver", "Q1.G"), originalGate, .00001),
            "fresh descriptor replay changed solved gate voltage");
        verifyFault(sim, replay);
        require(!retest(sim), "unrepaired indicator passed customer retest");
        require(challenge.getFaultController().clearForDeveloperVerification(), "selected fault did not clear");
        settle(sim);
        verifyHealthy(sim, board);
        String physical = Task43PPhysicalTruthDeveloperVerifier.verifyControlledIndicatorComposition(sim);
        String mutation = "null";
        if (seed == 0) {
            verifyMeasurements(sim, board);
            mutation = Task48MutationDeveloperVerifier.verify(sim);
            verifyHealthy(sim, board);
        }
        require(challenge.getFaultController().apply(), "selected fault did not reapply");
        settle(sim);
        verifyFault(sim, replay);
        boolean repairExecuted = repaired.add(replay.getPlan().getFaultBlockKey());
        if (repairExecuted) verifyRepair(sim, replay);
        return "{\"seed\":" + q(Long.toString(seed)) + ",\"canonical\":" + q(descriptor.toCanonical()) +
            ",\"faultDecision\":" + q(replay.getPlan().getFaultDecisionKey()) +
            ",\"faultGateVolts\":" + originalGate + ",\"freshReplay\":true,\"inputOrderIndependent\":true," +
            "\"normalAdmission\":" + admission + ",\"physicalCorrespondence\":" + physical +
            ",\"mutations\":" + mutation + ",\"repairExecuted\":" + repairExecuted + "}";
    }

    private static void verifyNormalAdmission(CirSim sim, GeneratedBoardInstance board) {
        require(!board.isDeveloperOnlyFaultRoute() &&
            !board.getDiagnosticSolvabilityContract().isDeveloperFixture() &&
            !board.getDiagnosticSolvabilityContract().getPlans().isEmpty(), "normal route used a developer fixture");
        require(board.getFaultCandidates().size() == 2 &&
            GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(board.getFaultCandidates()) == 2,
            "normal route lacks two distinct physical fault owners");
        require(board.getBoard().getComponentIds().size() == 7 && board.getBoard().getPadIds().size() == 15 &&
            board.getBoard().getNetIds().size() == 6 && board.getBoard().getPowerInputIds().size() == 2,
            "device inventory or explicit external power mapping changed");
        require(!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
            !FreshGeneratedRuntimeInstallation.isInProgress(sim), "private admission guard leaked");
        Vector<GeneratedDiagnosticSolvabilityEvidence> executed =
            Task41DeveloperVerifier.getLastControlledAdmissionEvidenceForDeveloperVerification();
        require(executed.size() == 2 && !executed.get(0).getRouteId().equals(executed.get(1).getRouteId()),
            "normal admission did not execute two distinct candidate routes");
        HashSet<String> proofOwners = new HashSet<String>();
        for (GeneratedDiagnosticSolvabilityEvidence proof : executed) {
            for (String owner : new String[] { component("driver", "RG"), component("load", "RLOAD") })
                if (proof.getRouteId().endsWith("/" + owner)) proofOwners.add(owner);
        }
        require(proofOwners.size() == 2, "diagnostic receipts did not retain both qualified physical owners");
        for (GeneratedDiagnosticSolvabilityEvidence proof : executed)
            require(proof.getSeed() == board.getSeed() && proof.getFamilyId().equals(board.getCircuitFamilyId()) &&
                proof.getMeasuredExecutionDepth() > 0 && !proof.getSolverSamples().isEmpty() &&
                proof.getExecutedMeterModeIds().contains("DC_VOLTAGE") &&
                proof.getExecutedMeterModeIds().contains("RESISTANCE") &&
                proof.getExecutedMeterModeIds().contains("CONTINUITY") &&
                proof.isRepairReachable() && proof.isCustomerRetestPassed() && proof.isStateIsolated() &&
                proof.hasUnaffectedFunctionRetestObservation(),
                "normal admission receipt is stale, declared-only, or lacks executed measurement/repair/retest");
        GeneratedChallengeLifecycleEvidence evidence = sim.getGeneratedChallengeController().getLifecycleEvidence();
        require(evidence.healthyGenerationInstalled && evidence.healthyGraphAnalyzedAfterTimeAdvance &&
            evidence.healthyFamilyValidated && evidence.selectedFaultApplied && evidence.selectedFaultValidated &&
            evidence.faultedGraphAnalyzedAfterTimeAdvance && evidence.scenarioCompatibilityValidated &&
            evidence.readyAfterValidation,
            "normal lifecycle omitted healthy or faulted solver verification");
        board.getPhysicalBoardRuntime().validateSupportedCompositionProviders();
        invariant(sim);
    }

    private static void verifyFault(CirSim sim, BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        command(sim, board, true);
        require(board.getOperationalStates() != null &&
            !board.getOperationalStates().isIlluminated(component("load", "LED1")),
            "faulted LED observer is absent or falsely illuminated");
        require(Math.abs(element(board, "load", "LED1").getCurrent()) < .000001 &&
            Math.abs(element(board, "load", "RLOAD").getCurrent()) < .000001,
            "admitted fault did not prevent the indicator from turning on");
        double gate = voltage(board, "driver", "Q1.G") - voltage(board, "driver", "Q1.S");
        require("driver".equals(result.getPlan().getFaultBlockKey()) ? gate < .1 : gate > 3,
            "gate voltage cannot distinguish the two physical fault owners");
        command(sim, board, false);
        require(voltage(board, "driver", "Q1.G") < .1 &&
            Math.abs(element(board, "load", "LED1").getCurrent()) < .000001,
            "faulted OFF command did not solve electrically");
        command(sim, board, true);
    }

    private static String admissionReceipt() {
        StringBuilder result = new StringBuilder("{\"status\":\"EXECUTED\",\"routes\":[");
        for (GeneratedDiagnosticSolvabilityEvidence proof :
                Task41DeveloperVerifier.getLastControlledAdmissionEvidenceForDeveloperVerification()) {
            if (result.charAt(result.length() - 1) != '[') result.append(',');
            result.append("{\"route\":").append(q(proof.getRouteId()))
                .append(",\"measuredDepth\":").append(proof.getMeasuredExecutionDepth())
                .append(",\"samples\":[");
            boolean first = true;
            for (GeneratedDiagnosticSample sample : proof.getSolverSamples()) {
                if (!first) result.append(',');
                first = false;
                result.append("{\"id\":").append(q(sample.getSampleId())).append(",\"value\":")
                    .append(sample.getValue()).append(",\"tolerance\":").append(sample.getComparisonTolerance()).append('}');
            }
            result.append("],\"physicalRepair\":true,\"customerRetest\":true,\"stateRestored\":true}");
        }
        return result.append("]}").toString();
    }

    private static void verifyHealthy(CirSim sim, GeneratedBoardInstance board) {
        command(sim, board, false);
        require(board.getOperationalStates() != null &&
            !board.getOperationalStates().isIlluminated(component("load", "LED1")),
            "healthy OFF LED observer is absent or illuminated");
        require(Math.abs(element(board, "load", "LED1").getCurrent()) < .000001 &&
            voltage(board, "driver", "Q1.G") < .1, "healthy OFF still conducts");
        command(sim, board, true);
        double led = Math.abs(element(board, "load", "LED1").getCurrent());
        double load = Math.abs(element(board, "load", "RLOAD").getCurrent());
        require(board.getOperationalStates().isIlluminated(component("load", "LED1")),
            "healthy ON current was not visible through the LED observer");
        require(led >= .008 && led <= .020 && close(led, load, .0005) &&
            voltage(board, "driver", "Q1.G") > 3 && voltage(board, "driver", "Q1.D") < .8,
            "healthy ON does not flow through real NMOS/LED path");
        // Exercise the real profile without publishing terminal completion
        // while this candidate still has mutation and failure checks to run.
        GeneratedCustomerRetestResult profile = board.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        settle(sim);
        require(profile != null && profile.isPassed(), "healthy complete OFF/ON customer retest failed");
        invariant(sim);
    }

    private static void verifyRepair(CirSim sim, BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        String block = result.getPlan().getFaultBlockKey();
        String other = "driver".equals(block) ? "load" : "driver";
        ReplaceableResistorBoardCapability target = capability(board, block);
        ReplaceableResistorBoardCapability healthy = capability(board, other);
        PhysicalResistorPart original = target.getSlot().getInstalledPart();
        GeneratedFaultBinding retainedFault = original.getFaultBinding();
        power(sim, BoardPowerState.UNPOWERED);
        require(healthy.getController().removeInstalledPart(), "wrong-owner physical removal rejected");
        settle(sim);
        require(healthy.getController().installNewFromCatalog(catalog(other, false)), "wrong-owner replacement rejected");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        require(!retest(sim), "replacement of healthy owner passed unrepaired customer fault");
        power(sim, BoardPowerState.UNPOWERED);
        require(target.getController().removeInstalledPart(), "faulty physical removal rejected");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        require(!retest(sim), "empty target slot passed retest");
        power(sim, BoardPowerState.UNPOWERED);
        // 1 MOhm RG cannot establish the required gate drive; 100 kOhm RLOAD cannot light the LED.
        require(target.getController().installNewFromCatalog("driver".equals(block) ?
            "R_CATALOG_1000000" : "R_CATALOG_100000"), "wrong-value replacement rejected");
        settle(sim);
        PhysicalResistorPart wrong = target.getSlot().getInstalledPart();
        power(sim, BoardPowerState.POWERED);
        require(!retest(sim), "wrong-value physical repair passed end-to-end retest");
        power(sim, BoardPowerState.UNPOWERED);
        require(target.getController().removeInstalledPart(), "wrong-value removal rejected");
        settle(sim);
        require(target.getController().installNewFromCatalog(catalog(block, false)), "correct-value replacement rejected");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        verifyHealthy(sim, board);
        power(sim, BoardPowerState.UNPOWERED);
        require(target.getController().removeInstalledPart(), "correct repair removal rejected");
        settle(sim);
        require(target.getController().installNewFromCatalog(catalog(block, true)), "alternative repair rejected");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        verifyHealthy(sim, board);
        require(target.getInventory().contains(original.getId()) && !original.isInstalled() &&
            target.getInventory().contains(wrong.getId()) && !wrong.isInstalled() &&
            retainedFault == board.getFaultBinding() && retainedFault.isApplied() &&
            target.getSlot().getInstalledPart().getFaultBinding() == null,
            "replacement lost retained original-fault causality or inventory ownership");
        require(retest(sim) && sim.getGeneratedChallengeController().getState() ==
            GeneratedChallengeState.COMPLETED,
            "final alternative repair did not publish successful public customer completion");
    }

    private static void verifyMeasurements(CirSim sim, GeneratedBoardInstance board) {
        power(sim, BoardPowerState.UNPOWERED);
        GeneratedExternalPowerBindings bindings = board.getExternalPowerBindings();
        require(bindings.areAllDisconnected(), "power off did not isolate both sources");
        for (String block : new String[] { "driver", "load" }) {
            String local = "driver".equals(block) ? "RG" : "RLOAD";
            CircuitPostMeasurementEndpoint red = endpoint(board, block, local + ".1");
            CircuitPostMeasurementEndpoint black = endpoint(board, block, local + ".2");
            Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList);
            bindings.getBinding(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID).setConnected(true);
            require(!sim.getBoardPowerController().isElectricallyUnpowered() &&
                Double.isNaN(sim.measureResistance(red, black)) && before.equals(sim.elmList),
                "main-supply-only isolation falsely admitted active measurement");
            bindings.getBinding(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID).setConnected(false);
            sim.needAnalyze();
            settle(sim);
            double measured = sim.measureResistance(red, black);
            require(close(measured, "driver".equals(block) ? 1000 : 330, 2),
                "isolated resistor measurement is not solver-backed: " + block + "=" + measured);
            require(before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() && bindings.areAllDisconnected(),
                "active measurement left source/solver residue");
            final RuntimeException failure = new IllegalStateException("task48-reader-" + block);
            ResistanceMeasurementStimulus stimulus = new ResistanceMeasurementStimulus(sim, red, black);
            Throwable caught = null;
            try {
                sim.runTemporaryActiveMeasurementForDeveloperVerification(stimulus, new ActiveMeasurementResultReader() {
                    public double readResult() { throw failure; }
                });
            } catch (Throwable problem) { caught = problem; }
            require(caught == failure && before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                bindings.areAllDisconnected() && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "failed measurement lost primary error, cleanup or both-source isolation");
            for (CircuitElm temporary : stimulus.getTemporaryElements())
                require(!sim.elmList.contains(temporary), "temporary source survived cleanup");
            measurements += 3;
        }
        power(sim, BoardPowerState.POWERED);
        verifyHealthy(sim, board);
    }

    private static String verifyConstructionFailures(final CirSim sim, long seed) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        String circuit = sim.dumpCircuit();
        int count = 0;
        for (final BoundedGeneratedBoardAssembler.Stage target : BoundedGeneratedBoardAssembler.Stage.values()) {
            final RuntimeException injected = new IllegalStateException("task48-private-" + target);
            boolean caught = false;
            try {
                BoundedGeneratedBoardAssembler.assemble(BoundedAssemblyRequest.forControlledIndicator(seed),
                    new BoundedGeneratedBoardAssembler.FailureProbe() {
                        public void after(BoundedGeneratedBoardAssembler.Stage stage) { if (stage == target) throw injected; }
                    });
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure failure) {
                caught = failure.getStage() == target && failure.isCleanupSucceeded() &&
                    failure.getOriginalFailure() == injected && failure.getCause() == injected;
            }
            require(caught && owner == sim.getGeneratedBoardInstance() && circuit.equals(sim.dumpCircuit()) &&
                sim.isGeneratedRuntimeSettled(), "construction stage failed to preserve original owner: " + target);
            count++;
        }
        return "{\"injectedStages\":" + count + ",\"oldOwnerPreserved\":true,\"cleanup\":\"PASS\"}";
    }

    private static void verifyAdmissionRejections(CirSim sim) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        String circuit = sim.dumpCircuit();
        GeneratedBoardInstance fixture = assemble(BoundedAssemblyRequest.forCanary(
            BoundedAssemblyRequest.descriptor(0L))).getInstance();
        boolean rejected = false;
        try { FreshGeneratedRuntimeInstallation.installNormalComposition(sim, fixture, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && owner == sim.getGeneratedBoardInstance() && circuit.equals(sim.dumpCircuit()),
            "empty developer fixture entered normal player admission");
        GeneratedBoardInstance normal = assemble(BoundedAssemblyRequest.forControlledIndicator(0L)).getInstance();
        GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
        rejected = false;
        try { FreshGeneratedRuntimeInstallation.installNormalComposition(sim, normal, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        finally { GeneratedDiagnosticSolvabilityAdmission.endInternalProof(); }
        require(rejected && owner == sim.getGeneratedBoardInstance() && circuit.equals(sim.dumpCircuit()),
            "nested internal proof published a normal player challenge");
        Task41SimulationSnapshot prior = Task41SimulationSnapshot.capture(sim);
        try {
            // The candidate must satisfy the composition/provider contract so
            // this canary reaches the disjoint-owner boundary itself.
            install(sim, normal, false);
            rejected = false;
            try { FreshGeneratedRuntimeInstallation.installNormalComposition(sim, normal, true); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && normal == sim.getGeneratedBoardInstance(),
                "same-owner normal installation was admitted");
        } finally {
            prior.restore(sim);
            prior.assertRestored(sim);
        }
        require(owner == sim.getGeneratedBoardInstance() && circuit.equals(sim.dumpCircuit()),
            "same-owner rejection proof changed the original owner");
    }

    private static String verifyInstallationFailures(CirSim sim, long seed, Task41SimulationSnapshot snapshot) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        int count = 0;
        for (FreshGeneratedRuntimeInstallation.Stage stage : FreshGeneratedRuntimeInstallation.Stage.values()) {
            GeneratedBoardInstance candidate = assemble(BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(stage);
            boolean caught = false;
            try { install(sim, candidate, true); }
            catch (RuntimeException expected) {
                caught = ("Injected fresh installation failure after " + stage).equals(expected.getMessage());
            } finally { FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null); }
            require(caught && sim.getGeneratedBoardInstance() == owner, "installation did not restore old owner: " + stage);
            snapshot.assertRestored(sim);
            disposed.add(candidate);
            count++;
        }
        return "{\"injectedStages\":" + count + ",\"originalRestored\":true}";
    }

    private static String verifyDetachedInitialOwner(CirSim sim, Task41SimulationSnapshot original, long seed) {
        original.beginProof(sim);
        GeneratedBoardInstance installed = null;
        ResistorElm initial = null;
        try {
            // Only fresh private containers are touched; the original player's objects remain detached.
            sim.generatedBoardInstance = null;
            sim.generatedChallengeController = null;
            sim.boardModificationController = null;
            sim.pcbWorkbenchController = null;
            sim.getBoardPowerController().detach();
            sim.elmList = new Vector<CircuitElm>();
            initial = new ResistorElm(0, 0);
            initial.drag(64, 0);
            sim.elmList.add(initial);
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.generatedBoardVerificationPending = false;
            sim.generatedVerificationRunning = false;
            sim.generatedRuntimeInstallationInProgress = false;
            sim.pendingBoardPowerState = null;
            sim.activeMeasurementOverlay = false;
            sim.instrumentController.clearTargets();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.runCircuit(true);
            require(sim.stopMessage == null, "private initial schematic did not solve");
            Task41SimulationSnapshot detached = Task41SimulationSnapshot.captureForFreshInstallation(sim);
            verifyInstallationFailures(sim, seed, detached);
            installed = assemble(BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
            install(sim, installed, true);
            verifyNormalAdmission(sim, installed);
            require(!sim.elmList.contains(initial) && sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 1,
                "initial schematic was mixed into the published board");
            return "{\"priorSchematicRestoredAfterFiveFailures\":true,\"normalPublication\":true}";
        } finally {
            original.restore(sim);
            original.assertRestored(sim);
            dispose(sim, installed, sim.getGeneratedBoardInstance());
            if (initial != null) initial.delete();
        }
    }

    private static String verifySuccession(CirSim sim, long seed) {
        GeneratedBoardInstance first = assemble(BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
        install(sim, first, true);
        for (String command : new String[] { GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
                GeneratedBoardOperationIds.CONTROL_INPUT_HIGH }) {
            PcbWorkbenchController workbench = sim.pcbWorkbenchController;
            ClickHandler handler = workbench.getSemanticOperationHandlerForDeveloperVerification(command);
            require(handler != null, "actual controlled command handler is missing");
            handler.onClick(null);
            settle(sim);
            require(workbench.isSemanticOperationControlEnabledForDeveloperVerification(
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW) &&
                workbench.isSemanticOperationControlEnabledForDeveloperVerification(
                    GeneratedBoardOperationIds.CONTROL_INPUT_HIGH) &&
                workbench.isSemanticOperationControlEnabledForDeveloperVerification(
                    GeneratedBoardOperationIds.CUSTOMER_RETEST),
                "settled semantic command left actual ticket controls disabled");
            require(close(voltage(first, "control-adapter", "J2.1"),
                GeneratedBoardOperationIds.CONTROL_INPUT_HIGH.equals(command) ? 5 : 0, .01),
                "actual command handler did not change the external electrical input");
        }
        final GeneratedChallengeController old = sim.getGeneratedChallengeController();
        final Vector<Runnable> completions = new Vector<Runnable>();
        old.setRetestCompletionDispatchForDeveloperVerification(new GeneratedChallengeController.RetestCompletionDispatch() {
            public void dispatch(Runnable completion) { completions.add(completion); }
        });
        try {
            GeneratedCustomerRetestResult firstResult = sim.performCustomerRetest();
            settle(sim);
            GeneratedCustomerRetestResult latest = sim.performCustomerRetest();
            settle(sim);
            require(firstResult != null && latest != null && firstResult != latest && completions.size() == 2,
                "actual customer scheduler did not create distinct requests");
            completions.get(1).run();
            completions.get(0).run();
            require(old.getCustomerRetestResult() == latest, "superseded request overwrote latest retest");
            sim.performCustomerRetest();
            settle(sim);
            ClickHandler oldHandler = sim.pcbWorkbenchController.getCustomerRetestHandlerForDeveloperVerification();
            sim.repaint();
            Scheduler.RepeatingCommand oldRepaint = sim.pendingGeneratedRepaint;
            require(oldHandler != null && oldRepaint != null && completions.size() == 3, "real stale callbacks absent");
            GeneratedBoardInstance second = assemble(BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
            FreshGeneratedRuntimeInstallation.requireDisjoint(first, second);
            install(sim, second, true);
            GeneratedChallengeController current = sim.getGeneratedChallengeController();
            Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
            Scheduler.RepeatingCommand repaint = sim.pendingGeneratedRepaint;
            oldHandler.onClick(null);
            oldRepaint.execute();
            completions.get(2).run();
            require(sim.getGeneratedBoardInstance() == second && current == sim.getGeneratedChallengeController() &&
                graph.equals(sim.elmList) && repaint == sim.pendingGeneratedRepaint &&
                current.getCustomerRetestResult() == null && second.getFaultBinding().isApplied(),
                "stale work crossed to same-seed fresh owner");
            invariant(sim);
            return "{\"settledPublicControls\":true,\"supersededRetest\":true,\"staleWorkbench\":true,\"staleRepaint\":true,\"staleCompletion\":true}";
        } finally { old.setRetestCompletionDispatchForDeveloperVerification(null); }
    }

    private static BoundedGeneratedBoardAssembler.Result assemble(BoundedAssemblyRequest request) {
        BoundedGeneratedBoardAssembler.Result result = BoundedGeneratedBoardAssembler.assemble(request);
        candidates.add(result.getInstance());
        return result;
    }
    private static void install(CirSim sim, GeneratedBoardInstance candidate, boolean attach) {
        FreshGeneratedRuntimeInstallation.installNormalComposition(sim, candidate, attach);
        invariant(sim);
    }
    private static String snapshot(BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        StringBuilder value = new StringBuilder(result.getPlan().getSemanticSignature());
        for (CircuitElm element : board.getSimulationElements()) value.append('\n').append(element.dump());
        value.append('\n').append(board.getPcbLayout().geometryFingerprint());
        for (String id : board.getBoard().getComponentIds()) {
            PhysicalBoardSlot slot = board.getPhysicalBoardRuntime().getSlot(id);
            value.append('\n').append(id).append('|').append(slot.getId()).append('|').append(slot.getInstalledPart().getId());
        }
        return value.append('\n').append(board.getFaultBinding().getFault().getId()).toString();
    }
    private static void restore(CirSim sim, Task41SimulationSnapshot snapshot, GeneratedBoardInstance owner,
            boolean quickPlay, QuickPlaySession session) {
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        sim.quickPlayActive = quickPlay;
        sim.quickPlaySession = session;
        if (current != owner) {
            snapshot.restore(sim);
            dispose(sim, current, owner);
        }
        snapshot.assertRestored(sim);
    }
    private static void dispose(CirSim sim, GeneratedBoardInstance candidate, GeneratedBoardInstance protectedOwner) {
        if (candidate == null || disposed.contains(candidate)) return;
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(), "cannot dispose active owner");
        candidate.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : candidate.getSimulationElements()) element.delete();
        disposed.add(candidate);
    }
    private static void command(CirSim sim, GeneratedBoardInstance board, boolean on) {
        board.invokeOperation(on ? GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
            GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
        settle(sim);
    }
    private static boolean retest(CirSim sim) {
        GeneratedCustomerRetestResult result = sim.performCustomerRetest();
        require(result != null, "customer retest operation was not executed");
        settle(sim);
        return result.isPassed();
    }
    private static void power(CirSim sim, BoardPowerState state) {
        sim.setBoardPowerState(state);
        settle(sim);
        require(sim.getBoardPowerController().getState() == state, "power operation failed to settle");
    }
    private static void settle(CirSim sim) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, "task48-dependent-operation");
        require(sim.isGeneratedRuntimeSettled() && sim.stopMessage == null && !sim.activeMeasurementOverlay,
            "controlled runtime did not settle safely");
    }
    private static void invariant(CirSim sim) {
        GeneratedRuntimeInvariant.verify(sim, sim.getGeneratedBoardInstance(), sim.getBoardModificationController(), sim.elmList);
    }
    private static String component(String block, String local) {
        return ControlledIndicatorBlockContributions.componentId(
            ControlledIndicatorBlockContributions.namespace(), block, local);
    }
    private static CircuitElm element(GeneratedBoardInstance board, String block, String local) {
        return board.getComponentBindings().getSingleElement(component(block, local));
    }
    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance board, String block, String localPad) {
        CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(
            ControlledIndicatorBlockContributions.padId(
                ControlledIndicatorBlockContributions.namespace(), block, localPad));
        require(endpoint instanceof CircuitPostMeasurementEndpoint, "missing actual solver post endpoint");
        return (CircuitPostMeasurementEndpoint) endpoint;
    }
    private static double voltage(GeneratedBoardInstance board, String block, String localPad) {
        CircuitPostMeasurementEndpoint endpoint = endpoint(board, block, localPad);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
    private static ReplaceableResistorBoardCapability capability(GeneratedBoardInstance board, String block) {
        return ReplaceableResistorBoardCapability.find(board.getPhysicalBoardRuntime(),
            component(block, "driver".equals(block) ? "RG" : "RLOAD"));
    }
    private static String catalog(String block, boolean alternative) {
        return "R_CATALOG_" + ("driver".equals(block) ? (alternative ? "2200" : "1000") : (alternative ? "220" : "330"));
    }
    private static boolean close(double actual, double expected, double tolerance) {
        return !Double.isNaN(actual) && !Double.isInfinite(actual) && Math.abs(actual - expected) <= tolerance;
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Task48: " + message);
    }
    private static void rethrow(Throwable problem) {
        if (problem instanceof Error) throw (Error) problem;
        if (problem instanceof RuntimeException) throw (RuntimeException) problem;
        throw new IllegalStateException("Task48 failure", problem);
    }
    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
