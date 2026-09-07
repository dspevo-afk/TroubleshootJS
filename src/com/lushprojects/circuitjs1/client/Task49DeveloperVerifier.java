package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickHandler;

/** Bounded compiled qualification. Player evidence is collected separately. */
final class Task49DeveloperVerifier {
    private static final long[] SEEDS = { 0, 1, 2, 3, Long.MIN_VALUE,
        Long.MAX_VALUE, 9007199254740993L, -9007199254740993L };
    private static final Vector<GeneratedBoardInstance> candidates = new Vector<GeneratedBoardInstance>();
    private static final Vector<GeneratedBoardInstance> disposed = new Vector<GeneratedBoardInstance>();
    private static int assertions;
    private static int measurements;
    private static String phase;

    private Task49DeveloperVerifier() { }

    static String verify(CirSim sim, String seedText, boolean forcedFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route is required");
        if (forcedFailure) throw new AssertionError("task49-explicit-assertion-canary");
        assertions = measurements = 0;
        phase = "entry";
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
            require(repaired.size() == 4 && repaired.contains("R_CATALOG_330|driver") &&
                repaired.contains("R_CATALOG_330|load") &&
                repaired.contains("R_CATALOG_270|driver") &&
                repaired.contains("R_CATALOG_270|load"),
                "finite replay corpus did not repair all four value/owner pairs");
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            verifyAdmissionRejections(sim);
            String construction = verifyConstructionFailures(sim, requestedSeed);
            String installation = verifyInstallationFailures(sim, requestedSeed, original);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String detached = verifyDetachedInitialOwner(sim, original, requestedSeed);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String succession = verifySuccession(sim, 1L, 2L);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            return "{\"protocol\":\"TSJ-TASK49-1\",\"status\":\"PASS\",\"requestedSeed\":" +
                q(canonicalSeed) + ",\"cases\":[" + cases + "],\"construction\":" + construction +
                ",\"installation\":" + installation + ",\"detachedInitialOwner\":" + detached +
                ",\"succession\":" + succession + ",\"assertions\":" + assertions +
                ",\"measurementCases\":" + measurements + ",\"repairedPairs\":" + repaired.size() +
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
                if (primary != null) throw new IllegalStateException("Task49 original failure: " +
                    primary.getMessage() + "; cleanup failure: " + cleanup.getMessage(), primary);
                rethrow(cleanup);
            } finally {
                candidates.clear();
                disposed.clear();
            }
        }
    }

    private static String verifySeed(CirSim sim, long seed, HashSet<String> repaired) {
        phase = "seed " + seed + " construction/admission";
        int assertionStart = assertions;
        GeneratedBoardInstance before = sim.getGeneratedBoardInstance();
        String circuitBefore = sim.dumpCircuit();
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forControlledIndicatorValues(seed);
        ChallengeDescriptor descriptor = ChallengeDescriptor.parse(request.getDescriptor().toCanonical());
        require(descriptor.getRootSeed() == seed, "descriptor lost signed-long seed");
        BoundedGeneratedBoardAssembler.Result direct = assemble(request);
        BoundedGeneratedBoardAssembler.Result replay = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(descriptor));
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
        FreshGeneratedRuntimeInstallation.requireDisjoint(replay.getInstance(), reversed.getInstance());
        String directSnapshot = snapshot(direct);
        String replaySnapshot = snapshot(replay);
        String reversedSnapshot = snapshot(reversed);
        require(directSnapshot.equals(replaySnapshot) && directSnapshot.equals(reversedSnapshot),
            "descriptor replay or reordered inputs changed the runtime construction");
        ControlledIndicatorValueSynthesis.ResolvedRecipe directRecipe =
            direct.getPlan().getResolvedLoadRecipe();
        require(directRecipe != null && expectedCatalogId(seed).equals(
            directRecipe.getCatalogEntryId()) && directRecipe.getResistanceOhms() ==
            expectedCatalogOhms(seed), "Task 49 selected catalog value changed for seed " + seed);
        install(sim, direct.getInstance(), false);
        verifyNormalAdmission(sim, direct.getInstance());
        double originalGate = voltage(direct.getInstance(), "driver", "Q1.G");
        install(sim, replay.getInstance(), false);
        verifyNormalAdmission(sim, replay.getInstance());
        String admission = admissionReceipt();
        GeneratedBoardInstance board = replay.getInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        challenge.beginDeveloperVerificationScope();
        if (seed == 1L)
            require("driver".equals(replay.getPlan().getFaultBlockKey()),
                "seed 1 did not exercise the driver fault owner");
        if (seed == 2L)
            require("load".equals(replay.getPlan().getFaultBlockKey()),
                "seed 2 did not exercise the load fault owner");
        command(sim, board, true);
        require(close(voltage(board, "driver", "Q1.G"), originalGate, .00001),
            "fresh descriptor replay changed solved gate voltage");
        verifyFault(sim, replay);
        require(!retest(sim), "unrepaired indicator passed customer retest");
        require(challenge.getFaultController().clearForDeveloperVerification(), "selected fault did not clear");
        settle(sim);
        verifyHealthy(sim, board);
        phase = "seed " + seed + " physical correspondence";
        String physical = Task43PPhysicalTruthDeveloperVerifier.verifyControlledIndicatorComposition(sim);
        String mutation = "null";
            if (seed == 0) {
                phase = "seed " + seed + " measurements";
                verifyMeasurements(sim, board);
                phase = "seed " + seed + " mutation";
                mutation = Task48MutationDeveloperVerifier.verify(sim);
                verifyHealthy(sim, board);
            }
            if (seed == 1 || seed == 2) {
                phase = "seed " + seed + " value envelope/mutation";
                verifyValueEnvelopeAndMutation(sim, board,
                    replay.getPlan().getResolvedLoadRecipe());
            }
        phase = "seed " + seed + " fault reapply/repair";
        require(challenge.getFaultController().apply(), "selected fault did not reapply");
        settle(sim);
        verifyFault(sim, replay);
        String repairKey = directRecipe.getCatalogEntryId() + "|" +
            replay.getPlan().getFaultBlockKey();
        boolean repairExecuted = repaired.add(repairKey);
        if (repairExecuted) verifyRepair(sim, replay);
        return "{\"seed\":" + q(Long.toString(seed)) + ",\"catalog\":" +
            q(directRecipe.getCatalogEntryId()) + ",\"resistanceOhms\":" +
            directRecipe.getResistanceOhms() + ",\"tolerancePercent\":" +
            directRecipe.getTolerancePercent() + ",\"canonical\":" + q(descriptor.toCanonical()) +
            ",\"faultDecision\":" + q(replay.getPlan().getFaultDecisionKey()) +
            ",\"faultGateVolts\":" + originalGate + ",\"freshReplay\":true,\"inputOrderIndependent\":true," +
            "\"normalAdmission\":" + admission + ",\"physicalCorrespondence\":" + physical +
            ",\"nominalHealthyLowHigh\":true,\"valueCorners\":" +
            ((seed == 1L || seed == 2L) ? "true" : "false") +
            ",\"mutations\":" + mutation + ",\"repairKey\":" + q(repairKey) +
            ",\"repairExecuted\":" + repairExecuted +
            ",\"assertions\":" + (assertions - assertionStart) + "}";
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
        ControlledIndicatorDeviceBehavior behavior =
            (ControlledIndicatorDeviceBehavior) board.getBehaviorContract();
        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
            behavior.getPlan().getResolvedLoadRecipe();
        require(recipe != null, "Task 49 healthy proof requires a resolved recipe");
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
        double minimumCurrent = recipe.getIntent().getTargetMinimumCurrentAmps();
        double maximumCurrent = recipe.getIntent().getTypedDemandAmps();
        require(led >= minimumCurrent && led <= maximumCurrent &&
            load >= minimumCurrent && load <= maximumCurrent && close(led, load, .0005) &&
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

    /**
     * Exercise the selected 330-ohm and 270-ohm recipes through the real
     * graph at both typed supply limits and both resistor tolerance corners.
     * The same pass performs a catalog replacement and restores the original
     * physical owner by identity, so the value route proves electrical,
     * physical, isolation, and restoration behavior together.
     */
    private static void verifyValueEnvelopeAndMutation(CirSim sim,
            GeneratedBoardInstance board,
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe) {
        require(recipe != null, "Task 49 value envelope requires a resolved recipe");
        ControlledIndicatorDeviceBehavior behavior =
            (ControlledIndicatorDeviceBehavior) board.getBehaviorContract();
        ResistorElm load = (ResistorElm) element(board, "load", "RLOAD");
        ExternalPowerSimulationBinding loadPower = board.getExternalPowerBindings()
            .getBinding(ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID);
        ExternalPowerSimulationBinding controlPower = board.getExternalPowerBindings()
            .getBinding(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID);
        Vector<VoltageElm> loadSources = voltageSources(loadPower);
        Vector<VoltageElm> controlSources = voltageSources(controlPower);
        require(loadSources.size() == 1 && controlSources.size() == 1,
            "Task 49 value route lost one source per external power input");
        double oldLoadSupply = loadSources.firstElement().maxVoltage;
        double oldControlSupply = controlSources.firstElement().maxVoltage;
        double oldResistance = load.getResistance();
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        boolean priorCommand = behavior.isCommandedOn();
        ReplaceableResistorBoardCapability capability = capability(board, "load");
        PhysicalResistorPart original = capability.getSlot().getInstalledPart();
        require(original != null && original.isOriginal() && original.isInstalled(),
            "Task 49 value route requires the original load owner");
        Throwable primary = null;
        try {
            double[] supplies = { recipe.getIntent().getSourceMinimumVolts(),
                recipe.getIntent().getSourceMaximumVolts() };
            double[] resistances = { recipe.getResistanceOhms() *
                (1.0 - recipe.getToleranceFraction()), recipe.getResistanceOhms() *
                (1.0 + recipe.getToleranceFraction()) };
            int cornerCount = 0;
            for (double supply : supplies) {
                loadSources.firstElement().maxVoltage = supply;
                controlSources.firstElement().maxVoltage = supply;
                for (double resistance : resistances) {
                    load.setResistance(resistance);
                    sim.needAnalyze();
                    settle(sim);
                    command(sim, board, true);
                    require(behavior.isHealthyOn(board),
                        "Task 49 healthy HIGH failed at supply/resistance corner " +
                        supply + "/" + resistance);
                    command(sim, board, false);
                    require(behavior.isHealthyOff(board),
                        "Task 49 healthy LOW failed at supply/resistance corner " +
                        supply + "/" + resistance);
                    cornerCount++;
                }
            }
            require(cornerCount == 4, "Task 49 did not execute all value corners");

            // Restore the nominal electrical state and settle it before any
            // controller mutation. The controller may replace the bound
            // resistor element, so later assertions reacquire that binding.
            loadSources.firstElement().maxVoltage = oldLoadSupply;
            controlSources.firstElement().maxVoltage = oldControlSupply;
            ResistorElm currentLoad = (ResistorElm) board.getComponentBindings()
                .getSingleElement(component("load", "RLOAD"));
            currentLoad.setResistance(oldResistance);
            sim.needAnalyze();
            settle(sim);
            require(((ResistorElm) board.getComponentBindings().getSingleElement(
                component("load", "RLOAD"))).getResistance() == oldResistance,
                "Task 49 nominal state was not restored before physical mutation");

            power(sim, BoardPowerState.UNPOWERED);
            require(capability.getController().removeInstalledPart(),
                "Task 49 selected-value removal was rejected");
            settle(sim);
            require(capability.getSlot().isEmpty() &&
                board.getPhysicalBoardRuntime().getInstalledPart(
                    component("load", "RLOAD")) == null,
                "Task 49 empty value slot was not isolated");
            require(capability.getController().installNewFromCatalog(
                    recipe.getCatalogEntryId()),
                "Task 49 selected-value catalog install was rejected");
            settle(sim);
            PhysicalResistorPart replacement = capability.getSlot().getInstalledPart();
            ResistorElm replacementElement = (ResistorElm) board.getComponentBindings()
                .getSingleElement(component("load", "RLOAD"));
            require(replacement != null && replacement != original &&
                replacement.getNameplate().getNominalResistanceOhms() ==
                    recipe.getResistanceOhms() && replacement.isInstalled() &&
                replacementElement.getResistance() == recipe.getResistanceOhms(),
                "Task 49 catalog value did not create a new physical owner");
            power(sim, BoardPowerState.POWERED);
            verifyHealthy(sim, board);
            power(sim, BoardPowerState.UNPOWERED);
            require(capability.getController().removeInstalledPart(),
                "Task 49 selected-value replacement removal was rejected");
            settle(sim);
            require(capability.getController().install(original.getId()),
                "Task 49 original value owner restore was rejected");
            settle(sim);
            ResistorElm restoredElement = (ResistorElm) board.getComponentBindings()
                .getSingleElement(component("load", "RLOAD"));
            require(capability.getSlot().getInstalledPart() == original &&
                original.isInstalled() && !replacement.isInstalled() &&
                restoredElement.getResistance() == recipe.getResistanceOhms(),
                "Task 49 value mutation did not restore exact owner/value identity");
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
        } finally {
            try {
            // Restore every temporary electrical edit before the enclosing
            // verifier snapshot is checked. Any failure is allowed to escape.
            if (sim.getBoardPowerController().getState() != BoardPowerState.UNPOWERED)
                power(sim, BoardPowerState.UNPOWERED);
            if (capability.getSlot().isEmpty())
                require(capability.getController().install(original.getId()),
                    "Task 49 cleanup could not reinstall original load owner");
            else if (capability.getSlot().getInstalledPart() != original) {
                require(capability.getController().removeInstalledPart(),
                    "Task 49 cleanup could not remove temporary load owner");
                settle(sim);
                require(capability.getController().install(original.getId()),
                    "Task 49 cleanup could not restore original load owner");
            }
            ResistorElm currentLoad = (ResistorElm) board.getComponentBindings()
                .getSingleElement(component("load", "RLOAD"));
            currentLoad.setResistance(oldResistance);
            loadSources.firstElement().maxVoltage = oldLoadSupply;
            controlSources.firstElement().maxVoltage = oldControlSupply;
            sim.needAnalyze();
            settle(sim);
            if (priorPower == BoardPowerState.POWERED)
                power(sim, BoardPowerState.POWERED);
            else
                power(sim, BoardPowerState.UNPOWERED);
            command(sim, board, priorCommand);
            settle(sim);
            ResistorElm restoredElement = (ResistorElm) board.getComponentBindings()
                .getSingleElement(component("load", "RLOAD"));
            require(restoredElement.getResistance() == oldResistance &&
                loadSources.firstElement().maxVoltage == oldLoadSupply &&
                controlSources.firstElement().maxVoltage == oldControlSupply &&
                capability.getSlot().getInstalledPart() == original,
                "Task 49 value corner cleanup changed electrical or physical state");
            } catch (Throwable cleanup) {
                if (primary != null)
                    throw new IllegalStateException(primary.getMessage() +
                        "; value cleanup failure: " + cleanup.getMessage(), primary);
                rethrow(cleanup);
            }
        }
    }

    private static Vector<VoltageElm> voltageSources(ExternalPowerSimulationBinding binding) {
        Vector<VoltageElm> result = new Vector<VoltageElm>();
        for (CircuitElm element : binding.getBackingElements())
            if (element instanceof VoltageElm) result.add((VoltageElm) element);
        return result;
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
        require(healthy.getController().installNewFromCatalog(catalog(result, other, false)), "wrong-owner replacement rejected");
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
        require(target.getController().installNewFromCatalog(catalog(result, block, false)), "correct-value replacement rejected");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        verifyHealthy(sim, board);
        power(sim, BoardPowerState.UNPOWERED);
        require(target.getController().removeInstalledPart(), "correct repair removal rejected");
        settle(sim);
        require(target.getController().installNewFromCatalog(catalog(result, block, true)), "alternative repair rejected");
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
            double expectedResistance = "driver".equals(block) ? 1000 :
                ((ControlledIndicatorDeviceBehavior) board.getBehaviorContract())
                    .getPlan().getResolvedLoadRecipe().getResistanceOhms();
            double measured = sim.measureResistance(red, black);
            require(close(measured, expectedResistance, 2),
                "isolated resistor measurement is not solver-backed: " + block + "=" + measured);
            require(before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() && bindings.areAllDisconnected(),
                "active measurement left source/solver residue");
            final RuntimeException failure = new IllegalStateException("task49-reader-" + block);
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
            final RuntimeException injected = new IllegalStateException("task49-private-" + target);
            boolean caught = false;
            try {
                BoundedGeneratedBoardAssembler.assemble(BoundedAssemblyRequest.forControlledIndicatorValues(seed),
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
        GeneratedBoardInstance normal = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(0L)).getInstance();
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
            GeneratedBoardInstance candidate = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(seed)).getInstance();
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
            installed = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(seed)).getInstance();
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

    private static String verifySuccession(CirSim sim, long firstSeed, long secondSeed) {
        GeneratedBoardInstance first = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(firstSeed)).getInstance();
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
            GeneratedBoardInstance second = assemble(BoundedAssemblyRequest.forControlledIndicatorValues(secondSeed)).getInstance();
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
            return "{\"firstSeed\":" + q(Long.toString(firstSeed)) +
                ",\"secondSeed\":" + q(Long.toString(secondSeed)) +
                ",\"firstCatalog\":\"R_CATALOG_330\",\"secondCatalog\":\"R_CATALOG_270\"," +
                "\"settledPublicControls\":true,\"supersededRetest\":true,\"staleWorkbench\":true,\"staleRepaint\":true,\"staleCompletion\":true}";
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
        value.append("\n/graph/components=").append(board.getBoard().getComponentIds());
        value.append("\n/graph/pads=").append(board.getBoard().getPadIds());
        value.append("\n/graph/nets=").append(board.getBoard().getNetIds());
        for (String id : board.getBoard().getComponentIds()) {
            PhysicalBoardSlot slot = board.getPhysicalBoardRuntime().getSlot(id);
            PhysicalSpecification specification = board.getPhysicalSpecifications().getSpecification(id);
            value.append('\n').append(id).append('|').append(slot.getId()).append('|')
                .append(slot.getInstalledPart() == null ? "empty" : slot.getInstalledPart().getId())
                .append('|').append(specification == null ? "missing" : specification.getSpecificationId());
            if (specification instanceof ResistorNameplate) {
                ResistorNameplate resistor = (ResistorNameplate) specification;
                value.append('|').append(resistor.getNominalResistanceOhms())
                    .append('|').append(resistor.getTolerancePercent())
                    .append('|').append(resistor.getRatedWattage());
            }
        }
        value.append("\n/fault=").append(board.getFaultBinding().getFault().getId())
            .append('|').append(board.getFaultBinding().getFault().getTargetComponentId())
            .append('|').append(board.getFaultBinding().isApplied())
            .append("/owners=").append(board.getAdmittedFaultPhysicalOwnerIds());
        for (GeneratedFaultCandidate candidate : board.getFaultCandidates())
            value.append("\n/candidate=").append(candidate.getFault().getId())
                .append('|').append(candidate.getFault().getTargetComponentId());
        return value.toString();
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
        boolean interactionBefore = sim.isChallengeInteractionEnabled();
        sim.setBoardPowerState(state);
        settle(sim);
        require(sim.getBoardPowerController().getState() == state,
            phase + ": power operation failed to settle; requested=" + state +
            ", actual=" + sim.getBoardPowerController().getState() +
            ", interactionBefore=" + interactionBefore +
            ", challenge=" + sim.getGeneratedChallengeController().getState());
    }
    private static void settle(CirSim sim) {
            GeneratedRuntimeDeveloperSettlement.settle(sim, "task49-dependent-operation");
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
    private static String catalog(BoundedGeneratedBoardAssembler.Result result,
            String block, boolean alternative) {
        if ("load".equals(block)) {
            if (!alternative)
                return result.getPlan().getResolvedLoadRecipe().getCatalogEntryId();
            return "R_CATALOG_220";
        }
        require("driver".equals(block), "unsupported repair block");
        return alternative ? "R_CATALOG_2200" : "R_CATALOG_" +
            (long) result.getPlan().getDriver().getResistor("RG").getResistanceOhms();
    }
    private static String expectedCatalogId(long seed) {
        return seed == 0L || seed == 1L || seed == 9007199254740993L ?
            "R_CATALOG_330" : "R_CATALOG_270";
    }
    private static double expectedCatalogOhms(long seed) {
        return seed == 0L || seed == 1L || seed == 9007199254740993L ? 330.0 : 270.0;
    }
    private static boolean close(double actual, double expected, double tolerance) {
        return !Double.isNaN(actual) && !Double.isInfinite(actual) && Math.abs(actual - expected) <= tolerance;
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Task49: " + message);
    }
    private static void rethrow(Throwable problem) {
        if (problem instanceof Error) throw (Error) problem;
        if (problem instanceof RuntimeException) throw (RuntimeException) problem;
        throw new IllegalStateException("Task49 failure", problem);
    }
    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
