package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickHandler;

/** Executes the real compiled assembler; never a normal challenge entry. */
final class Task47AssemblyDeveloperVerifier {
    private static final long[] SEEDS = { 0, 1, 2, 3, Long.MIN_VALUE,
        Long.MAX_VALUE, 9007199254740993L, -9007199254740993L };
    private static int assertions;
    private static int repairedOwners;
    private static int measurementCases;
    private static final Vector<GeneratedBoardInstance> candidates =
        new Vector<GeneratedBoardInstance>();
    private static final Vector<GeneratedBoardInstance> disposed =
        new Vector<GeneratedBoardInstance>();

    private Task47AssemblyDeveloperVerifier() { }

    static String verify(CirSim sim, String seedText, boolean forcedFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "Task47 requires the explicit developer route");
        assertions = repairedOwners = measurementCases = 0;
        candidates.clear();
        disposed.clear();
        if (forcedFailure)
            throw new AssertionError("task47-explicit-assertion-canary");
        long requestedSeed = parseSeed(seedText == null ? "0" : seedText);
        final GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController originalChallenge = sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null &&
            originalChallenge.isReady() && sim.isGeneratedRuntimeSettled(),
            "Task47 requires a settled original owner");
        final Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        final boolean quickPlay = sim.quickPlayActive;
        final QuickPlaySession quickPlaySession = sim.quickPlaySession;
        StringBuilder cases = new StringBuilder();
        HashSet<String> repaired = new HashSet<String>();
        Throwable primary = null;
        try {
            for (long seed : SEEDS) {
                restore(sim, original, originalOwner, quickPlay, quickPlaySession);
                if (cases.length() != 0) cases.append(',');
                cases.append(verifySeed(sim, seed, repaired));
            }
            require(repaired.size() == 2, "finite seeds did not execute both repair owners");
            restore(sim, original, originalOwner, quickPlay, quickPlaySession);
            String construction = verifyConstructionFailures(sim, requestedSeed);
            String installation = verifyInstallationFailures(sim, requestedSeed, original);
            restore(sim, original, originalOwner, quickPlay, quickPlaySession);
            String succession = verifySuccession(sim, requestedSeed);
            restore(sim, original, originalOwner, quickPlay, quickPlaySession);
            original.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                sim.getGeneratedChallengeController() == originalChallenge,
                "Task47 changed the original owner");
            return "{\"protocol\":\"TSJ-TASK47-ASSEMBLY-1\",\"status\":\"PASS\"," +
                "\"requestedSeed\":" + q(Long.toString(requestedSeed)) +
                ",\"cases\":[" + cases + "],\"construction\":" + construction +
                ",\"installation\":" + installation + ",\"succession\":" + succession +
                ",\"repairedOwners\":" + repairedOwners + ",\"assertions\":" + assertions +
                ",\"measurementCases\":" + measurementCases +
                ",\"candidateCleanup\":\"PASS\",\"originalOwnerRestored\":true," +
                "\"playerAdmission\":\"REJECTED\"}";
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
            return null;
        } finally {
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            try {
                restore(sim, original, originalOwner, quickPlay, quickPlaySession);
                for (GeneratedBoardInstance candidate : candidates)
                    disposeDetached(sim, candidate, originalOwner);
                original.assertRestored(sim);
            } catch (Throwable cleanup) {
                if (primary != null)
                    throw new IllegalStateException("Task47 original failure: " + primary.getMessage() +
                        "; cleanup failure: " + cleanup.getMessage(), primary);
                rethrow(cleanup);
            } finally {
                candidates.clear();
                disposed.clear();
            }
        }
    }

    private static String verifySeed(CirSim sim, long seed, HashSet<String> repaired) {
        GeneratedBoardInstance before = sim.getGeneratedBoardInstance();
        String beforeCircuit = sim.dumpCircuit();
        BoundedAssemblyRequest request = request(seed);
        ChallengeDescriptor descriptor = ChallengeDescriptor.parse(request.getDescriptor().toCanonical());
        require(descriptor.getRootSeed() == seed, "canonical replay lost signed 64-bit seed");
        BoundedGeneratedBoardAssembler.Result direct = assemble(request);
        BoundedGeneratedBoardAssembler.Result replay = assemble(BoundedAssemblyRequest.forCanary(descriptor));
        List<ElectricalBlockContract> reversedBlocks =
            new ArrayList<ElectricalBlockContract>(request.getBlocks());
        List<ElectricalConnection> reversedConnections =
            new ArrayList<ElectricalConnection>(request.getConnections());
        Collections.reverse(reversedBlocks);
        Collections.reverse(reversedConnections);
        BoundedGeneratedBoardAssembler.Result reversed = assemble(new BoundedAssemblyRequest(
            descriptor, reversedBlocks, reversedConnections));
        require(before == sim.getGeneratedBoardInstance() && beforeCircuit.equals(sim.dumpCircuit()),
            "private construction touched the live graph");
        assertFresh(direct.getInstance(), replay.getInstance());
        assertFresh(direct.getInstance(), reversed.getInstance());
        String semantic = generationSnapshot(direct);
        require(semantic.equals(generationSnapshot(replay)) &&
            semantic.equals(generationSnapshot(reversed)), "input order or replay changed assembly");
        verifyMappings(direct);
        verifyAdmissionBoundary(direct.getInstance(), before.getDiagnosticSolvabilityContract());
        FreshGeneratedRuntimeInstallation.installComposition(sim, direct.getInstance(), false);
        invariant(sim);
        verifyLifecycle(sim);
        double firstFaultVoltage = outputVoltage(direct.getInstance());
        String faultKey = direct.getPlan().getFaultDecisionKey();
        FreshGeneratedRuntimeInstallation.installComposition(sim, replay.getInstance(), false);
        invariant(sim);
        require(close(firstFaultVoltage, outputVoltage(replay.getInstance()), .00001),
            "fresh replay changed real solved fault behavior");
        GeneratedBoardInstance board = replay.getInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        challenge.beginDeveloperVerificationScope();
        verifySolvedFault(sim, replay);
        GeneratedCustomerRetestResult unrepaired = sim.performCustomerRetest();
        require(unrepaired != null && !unrepaired.isPassed(), "unrepaired composed retest passed");
        settle(sim);
        require(challenge.getFaultController().clearForDeveloperVerification(),
            "actual fault engine did not clear selected contribution");
        settle(sim);
        verifySolvedHealthy(sim, replay);
        String physical = Task43PPhysicalTruthDeveloperVerifier.verifyResistiveComposition(sim);
        if (seed == 0) verifyMeasurementCleanup(sim, replay);
        verifyInvariantNegatives(sim, board);
        require(challenge.getFaultController().apply(), "actual fault engine did not restore selected fault");
        settle(sim);
        verifySolvedFault(sim, replay);
        boolean repairedHere = repaired.add(replay.getPlan().getFaultBlockKey());
        if (repairedHere) verifyPhysicalRepair(sim, replay);
        return "{\"seed\":" + q(Long.toString(seed)) +
            ",\"sourceOhms\":" + replay.getPlan().getBlocks().get("source").getResistanceOhms() +
            ",\"loadOhms\":" + replay.getPlan().getBlocks().get("load").getResistanceOhms() +
            ",\"fault\":" + q(faultKey) + ",\"faultOutputVolts\":" + firstFaultVoltage +
            ",\"canonical\":" + q(descriptor.toCanonical()) +
            ",\"physicalCorrespondence\":" + physical +
            ",\"freshOwners\":true,\"inputOrderIndependent\":true," +
            "\"mapsAndSolver\":\"PASS\",\"physicalRepairExecuted\":" + repairedHere + "}";
    }

    private static void verifyMappings(BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        BoundedAssemblyPlan plan = result.getPlan();
        require(board.getBoard().getComponentIds().size() == 3 &&
            board.getBoard().getPadIds().size() == 6 && board.getBoard().getNetIds().size() == 3,
            "global fixture inventory is not three components/six pads/three nets");
        require(plan.getNetAliases().size() == 5, "local net provenance was lost");
        String powerSupplyBus = plan.getDeviceBuses().getBusId("power-supply");
        String signalBus = plan.getDeviceBuses().getBusId("signal");
        String powerReturnBus = plan.getDeviceBuses().getBusId("power-return");
        require(powerSupplyBus.equals(plan.netFor("source", "SUPPLY")) &&
            signalBus.equals(plan.netFor("source", "OUT")) &&
            signalBus.equals(plan.netFor("load", "SUPPLY")) &&
            powerReturnBus.equals(plan.netFor("source", "RETURN")) &&
            powerReturnBus.equals(plan.netFor("load", "RETURN")) &&
            !powerSupplyBus.equals(signalBus) &&
            !powerSupplyBus.equals(powerReturnBus) &&
            !signalBus.equals(powerReturnBus),
            "explicit namespace merge result differs from resolved fixture wiring");
        require(signalBus.equals(plan.netForPort("source", "OUT")) &&
            signalBus.equals(plan.netForPort("load", "IN")) &&
            powerReturnBus.equals(plan.netForPort("source", "RETURN")) &&
            powerReturnBus.equals(plan.netForPort("load", "RETURN")),
            "endpoint/pad port aliases lost their explicit net");
        ExternalBoardPowerInput input = board.getBoard().getPowerInput("VIN_INPUT");
        require(board.getBoard().getPowerInputIds().size() == 1 && input != null &&
            input.getPositivePadId().equals("J1.1") && input.getReturnPadId().equals("J1.2") &&
            input.getPositiveNetId().equals(powerSupplyBus) &&
            input.getReturnNetId().equals(powerReturnBus) &&
            board.getExternalPowerBindings().hasControlsForAllInputs(),
            "device external power escaped its explicit connector/reference mapping");
        PhysicalBoardRuntime runtime = board.getPhysicalBoardRuntime();
        require(runtime.getBoard() == board.getBoard() && runtime.getSlots().size() == 3 &&
            runtime.getPhysicalParts().size() == 3, "second physical inventory/envelope detected");
        require(result.getRuntimeTargets().size() == 2 && result.getEndpointManifest().size() == 6,
            "physical/repair or electrical endpoint mapping is incomplete");
        HashSet<String> inventoryIds = new HashSet<String>();
        for (String block : new String[] { "source", "load" }) {
            String component = component(board, block);
            require(plan.idFor(block, FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1")
                .equals(component), "component namespace changed");
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(runtime, component);
            require(capability != null && capability.getInventory().getRuntime() == runtime &&
                capability.getSlot().getPhysicalSlot() == runtime.getSlot(component),
                "resistor capability has foreign runtime or slot ownership");
            require(inventoryIds.add(capability.getInventory().getInventoryId()),
                "two resistor providers claim the same inventory view");
            require(capability.getInventory().getAll().size() == 1 &&
                capability.getInventory().getAll().get(0) == runtime.getInstalledPart(component),
                "original part is not the runtime-owned inventory object");
            GeneratedFaultCandidate componentFault = null;
            for (GeneratedFaultCandidate candidate : board.getFaultCandidates()) {
                if (component.equals(candidate.getFault().getTargetComponentId())) {
                    require(componentFault == null, "duplicate physical fault owner");
                    componentFault = candidate;
                }
            }
            require(componentFault != null, "physical component has no declared fault candidate");
            BoundedGeneratedBoardAssembler.RuntimeTarget target = result.getRuntimeTargets().get(block);
            require(target != null && target.getBlockKey().equals(block) &&
                target.getQualifiedComponentId().equals(component) &&
                target.getSlotId().equals("RESISTIVE_COUPLING.SLOT." + component) &&
                target.getSlotId().equals(capability.getSlot().getPhysicalSlot().getId()) &&
                target.getOriginalPartId().equals(component + "/part/original") &&
                target.getOriginalPartId().equals(runtime.getInstalledPart(component).getId()) &&
                target.getInventoryViewId().equals(component + "/inventory/replacements") &&
                target.getInventoryViewId().equals(capability.getInventory().getInventoryId()) &&
                target.getCapabilityId().equals(component + "/capability/replaceable-resistor") &&
                runtime.getCapability(target.getCapabilityId()) == capability &&
                target.getProviderId().equals("resistive-" + block) &&
                target.getFaultId().equals(componentFault.getFault().getId()) &&
                target.getRepairComponentId().equals(component) &&
                target.getRepairSlotId().equals(target.getSlotId()),
                "lossless physical/provider/fault/repair target map is inconsistent");
            for (int terminal = 1; terminal <= 2; terminal++) {
                String pad = pad(board, block, terminal);
                FunctionalBlockDescriptor blockDescriptor = plan.getBlocks().get(block)
                    .getDescriptor();
                FunctionalBlockDescriptor.Endpoint declaredEndpoint =
                    blockDescriptor.getEndpoints().get("R1_" + terminal);
                FunctionalBlockDescriptor.Pad declaredPad =
                    blockDescriptor.getPads().get("R1." + terminal);
                require(plan.idFor(block, FunctionalBlockDescriptor.EntityKind.PAD,
                    "R1." + terminal).equals(pad), "qualified pad identity changed");
                BoardPad actual = board.getBoard().getPad(pad);
                require(actual != null && actual.getComponentId().equals(component) &&
                    actual.getTerminalId().equals(Integer.toString(terminal)) &&
                    declaredEndpoint != null &&
                    declaredEndpoint.getComponentId().equals("R1") &&
                    declaredEndpoint.getTerminalId().equals(Integer.toString(terminal)) &&
                    declaredPad != null &&
                    declaredPad.getEndpointId().equals(declaredEndpoint.getId()) &&
                    plan.netFor(block, declaredPad.getNetId()).equals(actual.getNetId()),
                    "pad/component/terminal mapping differs from literal declaration");
                require(board.getConnectionBindings().get(component, pad).getBoardEndpoint() ==
                    board.getSimulationBindings().getEndpoint(pad),
                    "board and connection endpoint references disagree");
                BoundedGeneratedBoardAssembler.EndpointManifest endpointMap =
                    result.getEndpointManifest().get(block + "/R1_" + terminal);
                CircuitPostMeasurementEndpoint actualEndpoint = endpoint(board, pad);
                require(endpointMap != null && endpointMap.getBlockKey().equals(block) &&
                    endpointMap.getLocalEndpointId().equals("R1_" + terminal) &&
                    endpointMap.getQualifiedPadId().equals(pad) &&
                    endpointMap.getNetId().equals(actual.getNetId()) &&
                    endpointMap.getElement() == actualEndpoint.getElement() &&
                    endpointMap.getPostIndex() == actualEndpoint.getPostIndex(),
                    "local endpoint alias does not map to its globally owned solver endpoint");
            }
        }
        require(board.getFaultCandidates().size() == 2, "missing local fault contribution");
        for (GeneratedFaultCandidate candidate : board.getFaultCandidates()) {
            require(candidate.getFault().getType() == GeneratedFaultType.RESISTOR_INCORRECT_VALUE &&
                candidate.getFault().getEffectiveValue() == 100000 && candidate.isServiceable(),
                "local fault is not the existing serviceable resistor effect");
            require(candidate.getBinding().getEffect().getValueMutationTarget() ==
                board.getComponentBindings().getSingleElement(candidate.getFault().getTargetComponentId()),
                "fault target escaped the global component owner");
        }
        require(plan.getDecisionOwners().get(plan.getFaultDecisionKey())
            .equals(board.getFaultBinding().getFault().getTargetComponentId()),
            "lossless decision owner mapping differs from selected physical target");
    }

    private static void verifyAdmissionBoundary(final GeneratedBoardInstance board,
            final GeneratedDiagnosticSolvabilityContract normalContract) {
        final GeneratedDiagnosticSolvabilityContract contract = board.getDiagnosticSolvabilityContract();
        require(board.isDeveloperOnlyFaultRoute() && contract.isDeveloperFixture() &&
            contract.getPlans().isEmpty(), "fixture entered normal diagnostic mode");
        contract.validateDeveloperFixture(board);
        reject(new Runnable() { public void run() { contract.validate(board); }},
            "Task 41", "direct fixture diagnostic admission");
        reject(new Runnable() { public void run() {
            GeneratedDiagnosticSolvabilityAdmission.validate(board);
        }}, "Task 41", "generic fixture diagnostic admission");
        final GeneratedDiagnosticSolvabilityContract foreign =
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(board.getCircuitFamilyId(),
                board.getTopologyVariantId(), board.getSeed(), board.getFaultCandidates());
        reject(new Runnable() { public void run() { foreign.validateDeveloperFixture(board); }},
            "owned", "foreign fixture contract attachment");
        reject(new Runnable() { public void run() {
            rejectSuppliedContract(false, contract);
        }}, "Supplied diagnostic contract", "fixture without developer-only constructor flag");
        reject(new Runnable() { public void run() {
            rejectSuppliedContract(true, normalContract);
        }}, "Supplied diagnostic contract", "normal contract in explicit fixture constructor");
    }

    private static void rejectSuppliedContract(boolean developerOnly,
            GeneratedDiagnosticSolvabilityContract contract) {
        // The admission guard must reject before reading or changing any owner.
        new GeneratedBoardInstance(null, null, 0L, "fixture", "fixture", "",
            null, null, null, null, null, null, null, null, null, null, null,
            null, developerOnly, null, contract);
    }

    private static void verifyLifecycle(CirSim sim) {
        GeneratedChallengeLifecycleEvidence evidence = sim.getGeneratedChallengeController().getLifecycleEvidence();
        require(evidence.healthyGenerationInstalled && evidence.healthyGraphAnalyzedAfterTimeAdvance &&
            evidence.healthyFamilyValidated && evidence.selectedFaultApplied &&
            evidence.faultedGraphAnalyzedAfterTimeAdvance && evidence.selectedFaultValidated &&
            evidence.scenarioCompatibilityValidated && evidence.readyAfterValidation,
            "actual challenge lifecycle skipped healthy/faulted CircuitJS validation");
    }

    private static void verifySolvedHealthy(CirSim sim, BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        double source = result.getPlan().getBlocks().get("source").getResistanceOhms();
        double load = result.getPlan().getBlocks().get("load").getResistanceOhms();
        assertDivider(board, source, load);
        for (String block : new String[] { "source", "load" }) {
            ComposedBlockContribution contribution = result.getPlan().getBlocks().get(block);
            LocalObservation observations = new LocalObservation(board, block);
            contribution.verifyHealthy(observations);
            require(contribution.observe(observations) ==
                ComposedBlockContribution.ObservationResult.CONDUCTING,
                "local solved observation did not execute healthy contribution");
        }
        board.getBehaviorContract().verifyHealthy(board, BoardPowerState.POWERED);
        CircuitPostMeasurementEndpoint input = endpoint(board, pad(board, "source", 1));
        CircuitPostMeasurementEndpoint output = endpoint(board, pad(board, "load", 1));
        CircuitPostMeasurementEndpoint returned = endpoint(board, pad(board, "load", 2));
        require(input.getElement().getNode(input.getPostIndex()) !=
            output.getElement().getNode(output.getPostIndex()) &&
            output.getElement().getNode(output.getPostIndex()) !=
            returned.getElement().getNode(returned.getPostIndex()) &&
            input.getElement().getNode(input.getPostIndex()) !=
            returned.getElement().getNode(returned.getPostIndex()),
            "same local labels shorted distinct actual solver nodes");
        require(close(voltage(board, pad(board, "source", 2)),
            voltage(board, pad(board, "load", 1)), .00001),
            "explicit endpoint-to-pad coupling is not electrical");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        ProbeTarget red = new BoardPadProbeTarget(sim, board, pad(board, "load", 1), renderer);
        ProbeTarget black = new BoardPadProbeTarget(sim, board, pad(board, "load", 2), renderer);
        require(red.isValid() && black.isValid(), "qualified composed probe target is invalid");
        double measured = sim.instrumentController.measureDcVoltageForStrategy(red, black);
        require(close(measured, 5 * load / (source + load), .001),
            "existing meter adapter lost composed solver correspondence");
        verifyRenderedTerminals(sim, board);
        invariant(sim);
    }

    private static void verifySolvedFault(CirSim sim, BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        String faultBlock = result.getPlan().getFaultBlockKey();
        double source = "source".equals(faultBlock) ? 100000 :
            result.getPlan().getBlocks().get("source").getResistanceOhms();
        double load = "load".equals(faultBlock) ? 100000 :
            result.getPlan().getBlocks().get("load").getResistanceOhms();
        assertDivider(board, source, load);
        require(Math.abs(resistor(board, "load").getCurrent()) < .0001,
            "high resistance did not cause actual insufficient load current");
        if ("source".equals(faultBlock))
            require(outputVoltage(board) < .12, "source fault did not lower actual load voltage");
        else
            require(outputVoltage(board) > 4.98, "load fault did not change actual coupled output");
        for (String block : new String[] { "source", "load" })
            require(result.getPlan().getBlocks().get(block).observe(new LocalObservation(board, block)) ==
                ComposedBlockContribution.ObservationResult.LOW_CURRENT,
                "local fault-observation contribution did not execute");
        board.getBehaviorContract().verifyFaulted(board, sim.getBoardModificationController(),
            BoardPowerState.POWERED);
        invariant(sim);
    }

    private static void assertDivider(GeneratedBoardInstance board, double source, double load) {
        double expectedCurrent = 5 / (source + load);
        require(close(voltage(board, pad(board, "source", 1)), 5, .001), "device external power mapping is wrong");
        require(close(voltage(board, pad(board, "load", 2)), 0, .00001), "explicit reference mapping is wrong");
        require(close(outputVoltage(board), expectedCurrent * load, .001),
            "real CircuitJS output differs from independent divider equation");
        require(close(Math.abs(resistor(board, "source").getCurrent()), expectedCurrent, .000001) &&
            close(Math.abs(resistor(board, "load").getCurrent()), expectedCurrent, .000001),
            "real coupled branch currents differ from independent Kirchhoff expectation");
    }

    private static void verifyPhysicalRepair(CirSim sim, BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        String block = result.getPlan().getFaultBlockKey();
        ReplaceableResistorBoardCapability capability = ReplaceableResistorBoardCapability.find(
            board.getPhysicalBoardRuntime(), component(board, block));
        PhysicalResistorPart original = capability.getSlot().getInstalledPart();
        GeneratedFaultBinding originalFault = board.getFaultBinding();
        power(sim, BoardPowerState.UNPOWERED);
        require(capability.getController().removeInstalledPart(), "remove target contribution");
        settle(sim);
        require(capability.getController().installNewFromCatalog("R_CATALOG_100000"),
            "install actual wrong replacement");
        settle(sim);
        PhysicalResistorPart wrong = capability.getSlot().getInstalledPart();
        power(sim, BoardPowerState.POWERED);
        GeneratedCustomerRetestResult rejected = sim.performCustomerRetest();
        require(rejected != null && !rejected.isPassed(), "incorrect physical replacement passed retest");
        settle(sim);
        power(sim, BoardPowerState.UNPOWERED);
        require(capability.getController().removeInstalledPart(), "remove wrong replacement");
        settle(sim);
        PhysicalResistorPart conductingWrong = null;
        if ("source".equals(block)) {
            require(capability.getController().installNewFromCatalog("R_CATALOG_2200"),
                "install conducting but insufficient-output replacement");
            settle(sim);
            conductingWrong = capability.getSlot().getInstalledPart();
            power(sim, BoardPowerState.POWERED);
            require(Math.abs(resistor(board, "load").getCurrent()) > .001 &&
                outputVoltage(board) < 3.9,
                "conducting wrong-repair canary did not isolate the device output requirement");
            GeneratedCustomerRetestResult lowOutput = sim.performCustomerRetest();
            require(lowOutput != null && !lowOutput.isPassed(),
                "a conducting divider outside the customer voltage range passed retest");
            settle(sim);
            power(sim, BoardPowerState.UNPOWERED);
            require(capability.getController().removeInstalledPart(),
                "remove conducting wrong replacement");
            settle(sim);
        }
        double intended = result.getPlan().getBlocks().get(block).getResistanceOhms();
        long alternative = "source".equals(block) ? (intended == 100 ? 220 : 100) :
            (intended == 1000 ? 2200 : 1000);
        require(capability.getController().installNewFromCatalog("R_CATALOG_" + alternative),
            "install supported alternative electrical repair");
        settle(sim);
        power(sim, BoardPowerState.POWERED);
        double source = "source".equals(block) ? alternative :
            result.getPlan().getBlocks().get("source").getResistanceOhms();
        double load = "load".equals(block) ? alternative :
            result.getPlan().getBlocks().get("load").getResistanceOhms();
        assertDivider(board, source, load);
        GeneratedCustomerRetestResult passed = sim.performCustomerRetest();
        require(passed != null && passed.isPassed(), "functional alternative repair did not pass device retest");
        settle(sim);
        require(capability.getInventory().contains(original.getId()) && !original.isInstalled() &&
            capability.getInventory().contains(wrong.getId()) && !wrong.isInstalled() &&
            original.getFaultBinding() == originalFault && originalFault.isApplied() &&
            capability.getSlot().getInstalledPart().getFaultBinding() == null,
            "repair lost original fault causality, inventory or current-part ownership");
        require(conductingWrong == null ||
            (capability.getInventory().contains(conductingWrong.getId()) && !conductingWrong.isInstalled()),
            "repair discarded the conducting wrong replacement");
        invariant(sim);
        repairedOwners++;
    }

    private static void verifyMeasurementCleanup(CirSim sim,
            BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        power(sim, BoardPowerState.UNPOWERED);
        for (String block : new String[] { "source", "load" }) {
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
            ProbeTarget red = new BoardPadProbeTarget(sim, board, pad(board, block, 1), renderer);
            ProbeTarget black = new BoardPadProbeTarget(sim, board, pad(board, block, 2), renderer);
            Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList);
            double reading = sim.instrumentController.measureResistanceForStrategy(red, black);
            require(close(reading, result.getPlan().getBlocks().get(block).getResistanceOhms(), 1.0),
                "actual active resistance adapter lost qualified block target: " + block);
            require(before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                board.getExternalPowerBindings().areAllDisconnected(),
                "composed resistance measurement left an overlay or reconnected power");
            measurementCases++;
            final RuntimeException primary = new IllegalStateException("task47-reader-" + block);
            ResistanceMeasurementStimulus stimulus = new ResistanceMeasurementStimulus(sim,
                endpoint(board, pad(board, block, 1)), endpoint(board, pad(board, block, 2)));
            Throwable caught = null;
            try {
                sim.runTemporaryActiveMeasurementForDeveloperVerification(stimulus,
                    new ActiveMeasurementResultReader() {
                        public double readResult() { throw primary; }
                    });
            } catch (Throwable failure) { caught = failure; }
            require(caught == primary && before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                board.getExternalPowerBindings().areAllDisconnected(),
                "composed forced reader failure lost primary error or solver/power cleanup");
            for (CircuitElm temporary : stimulus.getTemporaryElements())
                require(!sim.elmList.contains(temporary), "temporary source survived composed cleanup");
            measurementCases++;
            invariant(sim);
        }
        power(sim, BoardPowerState.POWERED);
        verifySolvedHealthy(sim, result);
    }

    private static void verifyRenderedTerminals(CirSim sim, GeneratedBoardInstance board) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        for (String block : new String[] { "source", "load" }) {
            PhysicalPartRenderGeometry geometry =
                renderer.getInstalledGeometryForDeveloperVerification(component(board, block));
            require(geometry != null && geometry.getTerminals().size() == 2,
                "actual resistor renderer has no two-terminal geometry");
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                int terminalNumber = terminal.getTerminalIndex() + 1;
                require(pad(board, block, terminalNumber).equals(terminal.getBoardPadId()),
                    "rendered terminal points to another qualified block pad");
                Point point = renderer.getPadPoint(pad(board, block, terminalNumber));
                ProbeTarget hit = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
                require(hit instanceof BoardPadProbeTarget && hit.isValid() &&
                    ((BoardPadProbeTarget) hit).getPadId().equals(pad(board, block, terminalNumber)),
                    "rendered qualified board pad does not resolve to its real probe");
            }
        }
    }

    private static void verifyInvariantNegatives(final CirSim sim, final GeneratedBoardInstance board) {
        final Vector<CircuitElm> duplicate = new Vector<CircuitElm>(sim.elmList);
        duplicate.add(duplicate.firstElement());
        reject(new Runnable() { public void run() {
            GeneratedRuntimeInvariant.verify(board, sim.getBoardModificationController(), duplicate);
        }}, "duplicate", "duplicate solver owner");
        final GeneratedComponentConnectionBinding binding = board.getConnectionBindings().get(
            component(board, "source"), pad(board, "source", 1));
        CircuitMeasurementEndpoint retained = binding.getComponentEndpoint();
        try {
            binding.setComponentEndpoint(((PhysicalResistorPart) board.getPhysicalBoardRuntime()
                .getInstalledPart(component(board, "load"))).getPublicTerminal(0));
            reject(new Runnable() { public void run() { invariant(sim); }}, null,
                "real wrong component-terminal binding");
        } finally {
            binding.setComponentEndpoint(retained);
        }
        invariant(sim);
    }

    private static String verifyConstructionFailures(final CirSim sim, long seed) {
        final GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        final String circuit = sim.dumpCircuit();
        StringBuilder stages = new StringBuilder();
        for (final BoundedGeneratedBoardAssembler.Stage target : BoundedGeneratedBoardAssembler.Stage.values()) {
            boolean caught = false;
            final IllegalStateException injected = new IllegalStateException("task47-private-" + target);
            try {
                BoundedGeneratedBoardAssembler.assemble(request(seed),
                    new BoundedGeneratedBoardAssembler.FailureProbe() {
                        public void after(BoundedGeneratedBoardAssembler.Stage stage) {
                            if (stage == target) throw injected;
                        }
                    });
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure expected) {
                require(expected.getStage() == target && expected.isCleanupSucceeded(),
                    "private failure receipt lost its stage or safe cleanup");
                require(expected.getOriginalFailure() == injected && expected.getCause() == injected,
                    "private cleanup replaced the original construction failure");
                boolean hasAllocatedElements = expected.getAllocatedElementCount() > 0;
                boolean hasMappedIdentities = expected.getMappedIdentityCount() > 0;
                boolean hasRegisteredParts = expected.getRegisteredPartCount() > 0;
                require(expected.getAllocatedElementCount() >= 0 &&
                    expected.getRegisteredPartCount() >= 0 &&
                    expected.getMappedIdentityCount() >= 0,
                    "failure probe returned a negative private progress value: " + target);
                if (target == BoundedGeneratedBoardAssembler.Stage.MAPPING) {
                    require(!hasAllocatedElements && !hasRegisteredParts && hasMappedIdentities,
                        "mapping failure receipt crossed a mutable construction boundary");
                } else if (target == BoundedGeneratedBoardAssembler.Stage.ELECTRICAL ||
                        target == BoundedGeneratedBoardAssembler.Stage.LAYOUT) {
                    require(hasAllocatedElements && hasMappedIdentities && !hasRegisteredParts,
                        "pre-registration failure receipt lost current construction progress: " + target);
                } else {
                    require(hasAllocatedElements && hasMappedIdentities && hasRegisteredParts,
                        "post-registration failure receipt lost current construction progress: " + target);
                }
                if (stages.length() != 0) stages.append(',');
                stages.append("{\"phase\":").append(q(target.name()))
                    .append(",\"originalFailureRetained\":true,\"cleanup\":\"PASS\"}");
                caught = true;
            }
            require(caught, "private construction stage was not reached: " + target);
            require(owner == sim.getGeneratedBoardInstance() && challenge == sim.getGeneratedChallengeController() &&
                circuit.equals(sim.dumpCircuit()) && sim.isGeneratedRuntimeSettled(),
                "private failure changed the old active challenge: " + target);
        }
        return "{\"failedPhases\":[" + stages +
            "],\"originalOwnerRestored\":true,\"cleanup\":\"PASS\"}";
    }

    private static String verifyInstallationFailures(CirSim sim, long seed,
            Task41SimulationSnapshot original) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        for (FreshGeneratedRuntimeInstallation.Stage stage : FreshGeneratedRuntimeInstallation.Stage.values()) {
            GeneratedBoardInstance candidate = assemble(request(seed)).getInstance();
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(stage);
            boolean caught = false;
            try {
                FreshGeneratedRuntimeInstallation.installComposition(sim, candidate, true);
            } catch (RuntimeException expected) {
                caught = ("Injected fresh installation failure after " + stage).equals(expected.getMessage());
            } finally {
                FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            }
            require(caught && sim.getGeneratedBoardInstance() == owner,
                "installation failure did not preserve prior owner: " + stage);
            original.assertRestored(sim);
            disposed.add(candidate); // The accepted installation abort owns candidate disposal.
        }
        return "{\"originalOwnerRestored\":true,\"cleanup\":\"PASS\"}";
    }

    private static String verifySuccession(CirSim sim, long seed) {
        GeneratedBoardInstance first = assemble(request(seed)).getInstance();
        FreshGeneratedRuntimeInstallation.installComposition(sim, first, true);
        final GeneratedChallengeController oldChallenge = sim.getGeneratedChallengeController();
        final Vector<Runnable> completions = new Vector<Runnable>();
        oldChallenge.setRetestCompletionDispatchForDeveloperVerification(
            new GeneratedChallengeController.RetestCompletionDispatch() {
                public void dispatch(Runnable completion) { completions.add(completion); }
            });
        try {
            GeneratedCustomerRetestResult firstRetest = sim.performCustomerRetest();
            settle(sim);
            GeneratedCustomerRetestResult latestRetest = sim.performCustomerRetest();
            settle(sim);
            require(firstRetest != null && latestRetest != null && firstRetest != latestRetest &&
                completions.size() == 2, "actual retest scheduler did not capture two requests");
            completions.get(1).run();
            completions.get(0).run();
            require(oldChallenge.getCustomerRetestResult() == latestRetest,
                "superseded composed retest replaced newer result");
            sim.performCustomerRetest();
            settle(sim);
            require(completions.size() == 3, "old owner completion not captured");
            ClickHandler oldRetest = sim.pcbWorkbenchController.getCustomerRetestHandlerForDeveloperVerification();
            sim.repaint();
            Scheduler.RepeatingCommand oldRepaint = sim.pendingGeneratedRepaint;
            require(oldRetest != null && oldRepaint != null, "actual old owner dispatch paths missing");
            GeneratedBoardInstance second = assemble(request(seed)).getInstance();
            assertFresh(first, second);
            FreshGeneratedRuntimeInstallation.installComposition(sim, second, true);
            GeneratedChallengeController nextChallenge = sim.getGeneratedChallengeController();
            Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
            Scheduler.RepeatingCommand currentRepaint = sim.pendingGeneratedRepaint;
            oldRepaint.execute();
            oldRetest.onClick(null);
            completions.get(2).run();
            require(sim.getGeneratedBoardInstance() == second &&
                sim.getGeneratedChallengeController() == nextChallenge && graph.equals(sim.elmList) &&
                sim.pendingGeneratedRepaint == currentRepaint &&
                nextChallenge.getCustomerRetestResult() == null && second.getFaultBinding().isApplied(),
                "stale work crossed to fresh replay owner with identical semantic IDs");
            invariant(sim);
            return "{\"sameSeedFreshSuccessor\":true,\"capturedRepaint\":true," +
                "\"capturedWorkbenchHandler\":true,\"capturedRetestCompletion\":true," +
                "\"supersededRequestRejected\":true,\"staleOwnerRejected\":true}";
        } finally {
            oldChallenge.setRetestCompletionDispatchForDeveloperVerification(null);
        }
    }

    private static BoundedAssemblyRequest request(long seed) {
        return BoundedAssemblyRequest.forCanary(BoundedAssemblyRequest.descriptor(seed,
            GenerationConstraints.unspecified()));
    }

    private static BoundedGeneratedBoardAssembler.Result assemble(BoundedAssemblyRequest request) {
        BoundedGeneratedBoardAssembler.Result result = BoundedGeneratedBoardAssembler.assemble(request);
        candidates.add(result.getInstance());
        return result;
    }

    private static void assertFresh(GeneratedBoardInstance first, GeneratedBoardInstance second) {
        FreshGeneratedRuntimeInstallation.requireDisjoint(first, second);
        require(first != second && first.getBoard() != second.getBoard() &&
            first.getSimulationBindings() != second.getSimulationBindings() &&
            first.getComponentBindings() != second.getComponentBindings() &&
            first.getConnectionBindings() != second.getConnectionBindings() &&
            first.getExternalPowerBindings() != second.getExternalPowerBindings() &&
            first.getPhysicalBoardRuntime() != second.getPhysicalBoardRuntime() &&
            first.getChallengeDefinition() != second.getChallengeDefinition() &&
            first.getFaultBinding() != second.getFaultBinding() &&
            first.getFamilyState() != second.getFamilyState(), "replay reused a mutable envelope owner");
        for (PhysicalPart<?> part : first.getPhysicalBoardRuntime().getPhysicalParts())
            for (PhysicalPart<?> other : second.getPhysicalBoardRuntime().getPhysicalParts())
                require(part != other, "replay reused a mutable physical part");
    }

    private static String generationSnapshot(BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance instance = result.getInstance();
        StringBuilder value = new StringBuilder(result.getPlan().getSemanticSignature());
        for (CircuitElm element : instance.getSimulationElements()) value.append('\n').append(element.dump());
        for (String componentId : instance.getBoard().getComponentIds()) {
            PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
            value.append('\n').append(componentId).append('|').append(slot.getId()).append('|')
                .append(slot.getInstalledPart().getId()).append('|').append(slot.getPadIds());
        }
        value.append('\n').append(instance.getFaultBinding().getFault().getId());
        return value.toString();
    }

    private static void restore(CirSim sim, Task41SimulationSnapshot original,
            GeneratedBoardInstance owner, boolean quickPlay, QuickPlaySession quickPlaySession) {
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        sim.quickPlayActive = quickPlay;
        sim.quickPlaySession = quickPlaySession;
        if (current != owner) {
            original.restore(sim);
            disposeDetached(sim, current, owner);
        }
        original.assertRestored(sim);
    }

    private static void disposeDetached(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedBoardInstance protectedOwner) {
        if (candidate == null || disposed.contains(candidate)) return;
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(),
            "refusing to dispose an active or protected generated owner");
        candidate.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : candidate.getSimulationElements()) element.delete();
        disposed.add(candidate);
    }

    private static void power(CirSim sim, BoardPowerState state) {
        sim.setBoardPowerState(state);
        settle(sim);
        require(sim.getBoardPowerController().getState() == state, "power operation did not settle");
    }

    private static void settle(CirSim sim) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 30; attempt++) {
            sim.updateCircuit();
            if (sim.isGeneratedRuntimeSettled()) break;
        }
        require(sim.isGeneratedRuntimeSettled() && sim.stopMessage == null &&
            !sim.activeMeasurementOverlay, "composed runtime did not settle safely");
    }

    private static void invariant(CirSim sim) {
        GeneratedRuntimeInvariant.verify(sim, sim.getGeneratedBoardInstance(),
            sim.getBoardModificationController(), sim.elmList);
    }

    private static BoundedAssemblyPlan plan(GeneratedBoardInstance board) {
        GeneratedChallengeBehaviorContract behavior = board.getBehaviorContract();
        require(behavior instanceof ComposedResistiveDeviceBehavior,
            "resolved resistive behavior is missing its assembly plan");
        return ((ComposedResistiveDeviceBehavior) behavior).getPlan();
    }
    private static String component(GeneratedBoardInstance board, String block) {
        return plan(board).idFor(block, FunctionalBlockDescriptor.EntityKind.COMPONENT, "R1");
    }
    private static String pad(GeneratedBoardInstance board, String block, int terminal) {
        return plan(board).idFor(block, FunctionalBlockDescriptor.EntityKind.PAD,
            "R1." + terminal);
    }
    private static ResistorElm resistor(GeneratedBoardInstance board, String block) {
        return (ResistorElm) board.getComponentBindings().getSingleElement(component(board, block));
    }
    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance board, String padId) {
        CircuitMeasurementEndpoint result = board.getSimulationBindings().getEndpoint(padId);
        require(result instanceof CircuitPostMeasurementEndpoint, "pad has no real CircuitJS endpoint");
        return (CircuitPostMeasurementEndpoint) result;
    }
    private static double voltage(GeneratedBoardInstance board, String padId) {
        CircuitPostMeasurementEndpoint result = endpoint(board, padId);
        return result.getElement().volts[result.getPostIndex()];
    }
    private static double outputVoltage(GeneratedBoardInstance board) {
        return voltage(board, pad(board, "load", 1));
    }
    private static boolean close(double actual, double expected, double tolerance) {
        return !Double.isNaN(actual) && !Double.isInfinite(actual) && Math.abs(actual - expected) <= tolerance;
    }

    private static final class LocalObservation implements ComposedBlockContribution.Observation {
        private final GeneratedBoardInstance board;
        private final String block;
        LocalObservation(GeneratedBoardInstance board, String block) { this.board = board; this.block = block; }
        public double voltage(String endpointId) {
            if ("R1_1".equals(endpointId)) return Task47AssemblyDeveloperVerifier.voltage(board,
                pad(board, block, 1));
            if ("R1_2".equals(endpointId)) return Task47AssemblyDeveloperVerifier.voltage(board,
                pad(board, block, 2));
            throw new IllegalArgumentException("Unknown literal local endpoint");
        }
        public double current(String componentId) {
            require("R1".equals(componentId), "unknown local component observation");
            return resistor(board, block).getCurrent();
        }
        public double resistance(String componentId) {
            require("R1".equals(componentId), "unknown local resistance observation");
            return resistor(board, block).resistance;
        }
    }

    private static long parseSeed(String value) {
        long result = Long.parseLong(value);
        if (!Long.toString(result).equals(value))
            throw new IllegalArgumentException("Task47 seed must be a canonical signed 64-bit decimal");
        return result;
    }
    private static void reject(Runnable action, String requiredMessage, String context) {
        boolean rejected = false;
        try { action.run(); }
        catch (RuntimeException expected) {
            rejected = requiredMessage == null || (expected.getMessage() != null &&
                expected.getMessage().indexOf(requiredMessage) >= 0);
        }
        require(rejected, "negative was not rejected: " + context);
    }
    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new IllegalStateException("Task47: " + message);
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("Task47 verification failure", failure);
    }
    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
