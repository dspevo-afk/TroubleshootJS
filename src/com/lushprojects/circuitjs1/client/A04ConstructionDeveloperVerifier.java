package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Developer-only A04 conformance proof for the constrained electrical
 * construction boundary.  The proof owns detached candidates only; the
 * existing fresh-installation and Task 41 snapshot boundaries own publication
 * and restoration of the player's graph.
 */
final class A04ConstructionDeveloperVerifier {
    private static final String PROTOCOL = "TSJ-A04-CONSTRUCTION-1";
    private static final long[] SEEDS = { 1L, 2L, 3L };

    private static int contractAssertions;
    private static int runtimeAssertions;
    private static boolean coordinateIsolationProof;
    private static boolean explicitJoinsProof;
    private static boolean terminalCorrespondenceProof;
    private static boolean failureIsolationProof;
    private static boolean recipeIdentityProof;
    private static boolean multiUnitPackageProof;

    private A04ConstructionDeveloperVerifier() { }

    static String verify(CirSim sim, boolean forceFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route required");
        final GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController originalChallenge =
            sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null &&
            originalChallenge.isReady() && sim.isGeneratedRuntimeSettled(),
            "A04 requires a settled original owner");

        contractAssertions = 0;
        runtimeAssertions = 0;
        coordinateIsolationProof = false;
        explicitJoinsProof = false;
        terminalCorrespondenceProof = false;
        failureIsolationProof = false;
        recipeIdentityProof = false;
        multiUnitPackageProof = false;
        final long started = System.currentTimeMillis();
        final Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        final Vector<GeneratedBoardInstance> candidates =
            new Vector<GeneratedBoardInstance>();
        final Vector<GeneratedBoardInstance> disposed =
            new Vector<GeneratedBoardInstance>();
        final Vector<CaseResult> cases = new Vector<CaseResult>();
        Throwable primary = null;
        try {
            if (forceFailure)
                throw new AssertionError("a04-explicit-failure-canary");

            verifyConstructionContext(sim, originalOwner);
            for (int index = 0; index < SEEDS.length; index++) {
                CaseResult sample = verifyCase(sim, original, originalOwner,
                    SEEDS[index], index + 1, candidates);
                cases.add(sample);
            }
            require(coordinateIsolationProof && explicitJoinsProof &&
                terminalCorrespondenceProof && failureIsolationProof &&
                recipeIdentityProof && multiUnitPackageProof,
                "A04 proof flags were not established by production-path checks");
            original.restore(sim);
            original.assertRestored(sim);
            runtime(originalOwner == sim.getGeneratedBoardInstance(),
                "A04 changed the original owner after the corpus");
            runtime(originalChallenge == sim.getGeneratedChallengeController(),
                "A04 changed the original challenge controller");
            // Publish only after the finally block has completed all cleanup checks.
        } catch (Throwable failure) {
            primary = failure;
        } finally {
            Throwable cleanupFailure = null;
            try {
                original.restore(sim);
            } catch (Throwable cleanup) {
                cleanupFailure = cleanup;
            }
            for (GeneratedBoardInstance candidate : candidates) {
                try {
                    dispose(sim, candidate, originalOwner, disposed);
                } catch (Throwable cleanup) {
                    cleanupFailure = retain(cleanupFailure, cleanup);
                }
            }
            try {
                original.assertRestored(sim);
                runtime(originalOwner == sim.getGeneratedBoardInstance(),
                    "A04 cleanup lost the original owner");
                runtime(originalChallenge == sim.getGeneratedChallengeController(),
                    "A04 cleanup lost the original challenge controller");
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure, cleanup);
            }
            if (cleanupFailure != null && primary == null)
                primary = cleanupFailure;
            else if (cleanupFailure != null && primary != null)
                primary = new IllegalStateException("A04 verification failed: " +
                    message(primary) + "; cleanup failed: " + message(cleanupFailure), primary);
        }
        if (primary != null)
            rethrow(primary);
        return report(cases, started, true);
    }

    private static CaseResult verifyCase(CirSim sim, Task41SimulationSnapshot original,
            GeneratedBoardInstance originalOwner, long seed, int version,
            Vector<GeneratedBoardInstance> candidates) {
        BoundedAssemblyRequest request = requestFor(version, seed);
        long start = System.currentTimeMillis();
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        verifyPlanContract(plan, version, seed);
        String canonical = A03RealizationReplay.capture(plan).toCanonical();
        RealizationManifest parsed = RealizationManifest.parse(canonical);
        contract(canonical.equals(parsed.toCanonical()),
            "A03 manifest round-trip changed for construction version " + version);
        BoundedAssemblyPlan replay = A03RealizationReplay.resolve(parsed);
        contract(plan.getSemanticSignature().equals(replay.getSemanticSignature()),
            "A03 replay changed construction semantics for version " + version);
        contract(parsed.getDescriptor().getGenerator().getVersion() == version,
            "A03 generator version changed for construction version " + version);
        contract(parsed.getDescriptor().getRootSeed() == seed,
            "A03 exact seed changed for construction version " + version);

        long assemblyStart = System.currentTimeMillis();
        BoundedGeneratedBoardAssembler.Result result =
            BoundedGeneratedBoardAssembler.assemble(request);
        long assemblyElapsed = elapsed(assemblyStart);
        GeneratedBoardInstance candidate = result.getInstance();
        candidates.add(candidate);
        contract(result.getPlan().getSemanticSignature().equals(plan.getSemanticSignature()),
            "assembled plan changed the resolved semantic signature");
        contract(canonical.equals(result.getRealizationManifest().toCanonical()),
            "assembled result changed the canonical A03 manifest");
        contract(candidate != originalOwner && candidate.getBoard() != originalOwner.getBoard(),
            "candidate reused the original board owner");
        contract(candidate.getSimulationElements().size() ==
            plan.getElectricalRealizationSpec().getElementDeclarations().size(),
            "candidate element count does not match declared electrical elements");
        verifyDetachedPhysicalOwnership(candidate, plan);

        original.restore(sim);
        original.assertRestored(sim);
        String originalDump = firstDump(sim);
        FreshGeneratedRuntimeInstallation.installComposition(sim, candidate, false);
        GeneratedRuntimeDeveloperSettlement.settle(sim, candidate,
            "a04-construction-v" + Integer.toString(version));
        runtime(sim.getGeneratedBoardInstance() == candidate,
            "candidate was not published as the active owner");
        runtime(sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady() &&
            sim.isGeneratedRuntimeSettled(), "candidate did not settle through CircuitJS");
        verifyRuntimeCorrespondence(sim, candidate, plan, version);

        original.restore(sim);
        original.assertRestored(sim);
        runtime(originalOwner == sim.getGeneratedBoardInstance(),
            "original owner was not restored after candidate " + version);
        runtime(originalDump.equals(firstDump(sim)),
            "original solver element changed after candidate " + version);
        return new CaseResult(version, Long.toString(seed),
            candidate.getSimulationElements().size(),
            plan.getElectricalRealizationSpec().getPackageMap().getPackageCount(),
            plan.getElectricalRealizationSpec().getPackageMap().getUnitCount(),
            elapsed(start), assemblyElapsed, canonical.length());
    }

    private static void verifyPlanContract(BoundedAssemblyPlan plan, int version,
            long seed) {
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        contract(spec != null && spec.getVersion() == ElectricalRealizationSpec.VERSION,
            "missing electrical realization schema");
        contract(!spec.getProviderDeclarations().isEmpty() &&
            !spec.getElementDeclarations().isEmpty() &&
            !spec.getPhysicalUnits().isEmpty() && !spec.getTerminalMappings().isEmpty(),
            "electrical realization declarations are incomplete");
        contract(spec.getPackageMap().getPackageCount() > 0 &&
            spec.getPackageMap().getUnitCount() > 0,
            "electrical realization package map is empty");
        for (Map.Entry<String, ElectricalRealizationSpec.PhysicalUnitSpec> entry :
                spec.getPhysicalUnits().entrySet()) {
            ElectricalRealizationSpec.PhysicalUnitSpec unit = entry.getValue();
            contract(spec.getPackageMap().getPackages().containsKey(unit.getComponentId()),
                "physical unit has no package: " + entry.getKey());
            contract(unit.getPackageTerminalByUnitTerminal().size() > 0,
                "physical unit has no terminal map: " + entry.getKey());
        }
        if (version == 1) {
            provider(spec, "source", ResistiveBlockContributions.SOURCE_TYPE_ID, 1);
            provider(spec, "load", ResistiveBlockContributions.LOAD_TYPE_ID, 1);
            element(spec, "source", "R1", "RESISTOR", "1", 0, "2", 1);
            element(spec, "load", "R1", "RESISTOR", "1", 0, "2", 1);
            mapping(spec, plan, "source", "R1", "1", "1", plan.netFor("source", "SUPPLY"));
            mapping(spec, plan, "source", "R1", "2", "2", plan.netFor("source", "OUT"));
            mapping(spec, plan, "load", "R1", "1", "1", plan.netFor("load", "SUPPLY"));
            mapping(spec, plan, "load", "R1", "2", "2", plan.netFor("load", "RETURN"));
            contract(plan.netFor("source", "OUT").equals(plan.netFor("load", "SUPPLY")),
                "resistive signal is not the explicit join");
            contract(plan.netFor("source", "RETURN").equals(plan.netFor("load", "RETURN")),
                "resistive return is not the explicit common net");
        } else {
            provider(spec, "driver", ControlledIndicatorBlockContributions.DRIVER_TYPE_ID, 1);
            provider(spec, "load", ControlledIndicatorBlockContributions.LOAD_TYPE_ID,
                version == 3 ? ControlledIndicatorBlockContributions.VALUE_LOAD_VERSION : 1);
            element(spec, "driver", "RG", "RESISTOR", "1", 0, "2", 1);
            element(spec, "driver", "RPD", "RESISTOR", "1", 0, "2", 1);
            element(spec, "driver", "Q1", "NMOS", "G", 0, "S", 1, "D", 2);
            element(spec, "load", "RLOAD", "RESISTOR", "1", 0, "2", 1);
            element(spec, "load", "LED1", "LED", "A", 0, "K", 1);
            mapping(spec, plan, "driver", "RG", "1", "1", plan.netFor("driver", "CONTROL"));
            mapping(spec, plan, "driver", "RG", "2", "2", plan.netFor("driver", "GATE"));
            mapping(spec, plan, "driver", "RPD", "1", "1", plan.netFor("driver", "GATE"));
            mapping(spec, plan, "driver", "RPD", "2", "2", plan.netFor("driver", "RETURN"));
            mapping(spec, plan, "driver", "Q1", "G", "G", plan.netFor("driver", "GATE"));
            mapping(spec, plan, "driver", "Q1", "D", "D", plan.netFor("driver", "SWITCHED_SINK"));
            mapping(spec, plan, "driver", "Q1", "S", "S", plan.netFor("driver", "RETURN"));
            mapping(spec, plan, "load", "RLOAD", "1", "1", plan.netFor("load", "SUPPLY"));
            mapping(spec, plan, "load", "RLOAD", "2", "2", plan.netFor("load", "LED_NODE"));
            mapping(spec, plan, "load", "LED1", "A", "A", plan.netFor("load", "LED_NODE"));
            mapping(spec, plan, "load", "LED1", "K", "K", plan.netFor("load", "SWITCHED_LOAD"));
            contract(plan.netFor("load", "SWITCHED_LOAD").equals(
                plan.netFor("driver", "SWITCHED_SINK")), "controlled drain join changed");
            contract(spec.getElementDeclaration("driver", "Q1").getPostIndex("G") == 0 &&
                spec.getElementDeclaration("driver", "Q1").getPostIndex("S") == 1 &&
                spec.getElementDeclaration("driver", "Q1").getPostIndex("D") == 2,
                "NMOS post order changed");
            contract(spec.getElementDeclaration("load", "LED1").getPostIndex("A") == 0 &&
                spec.getElementDeclaration("load", "LED1").getPostIndex("K") == 1,
                "LED polarity changed");
            if (version == 3) {
                contract(plan.getResolvedLoadRecipe() != null &&
                    spec.getResolvedLoadRecipe() == plan.getResolvedLoadRecipe(),
                    "v3 recipe identity was not preserved");
                recipeIdentityProof = true;
            } else {
                contract(spec.getResolvedLoadRecipe() == null,
                    "non-v3 construction synthesized a recipe");
            }
        }
        contract(seed == plan.getRequest().getDescriptor().getRootSeed(),
            "resolved plan seed changed");
    }

    private static void verifyDetachedPhysicalOwnership(GeneratedBoardInstance instance,
            BoundedAssemblyPlan plan) {
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        runtime(instance.getPhysicalBoardRuntime().getSlots().size() ==
            spec.getPackageMap().getPackageCount(),
            "runtime slot count does not match package count");
        for (Map.Entry<String, PhysicalPackage> entry : spec.getPackageMap().getPackages().entrySet()) {
            BoardComponent component = instance.getBoard().getComponent(entry.getKey());
            runtime(component != null, "package has no board component: " + entry.getKey());
            runtime(component.getPhysicalPackage().isEquivalentTo(entry.getValue()),
                "board package differs from the electrical package map: " + entry.getKey());
            runtime(instance.getPhysicalBoardRuntime().getSlot(entry.getKey()) != null,
                "package has no runtime slot: " + entry.getKey());
            runtime(instance.getPhysicalBoardRuntime().getInstalledPart(entry.getKey()) != null,
                "runtime slot has no installed physical part: " + entry.getKey());
        }
    }

    private static void verifyRuntimeCorrespondence(CirSim sim,
            GeneratedBoardInstance instance, BoundedAssemblyPlan plan, int version) {
        runtime(sim.nodeList != null && sim.nodeList.size() > 0,
            "settled candidate has no CircuitJS node graph");
        TroubleshootBoard board = instance.getBoard();
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        if (version == 1) {
            int sourceSupply = node(board, plan.idFor("source", FunctionalBlockDescriptor.EntityKind.PAD, "R1.1"));
            int sourceSignal = node(board, plan.idFor("source", FunctionalBlockDescriptor.EntityKind.PAD, "R1.2"));
            int loadSignal = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "R1.1"));
            int loadReturn = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "R1.2"));
            runtime(sourceSignal == loadSignal, "resistive signal endpoints are not one solver node");
            runtime(sourceSupply == node(board, "J1.1"), "resistive source is not on connector positive");
            runtime(loadReturn == node(board, "J1.2"), "resistive load return is not on connector return");
            runtime(sourceSupply != loadReturn && sourceSignal != loadReturn,
                "resistive supply, signal and return must remain distinct");
            runtime(sourceSupply != sourceSignal, "resistive supply and signal were shorted");
            explicitJoinsProof = true;
            verifyComponentEndpoint(instance, plan, "source", "R1", "1", 0, false);
            verifyComponentEndpoint(instance, plan, "source", "R1", "2", 1, true);
            verifyComponentEndpoint(instance, plan, "load", "R1", "1", 0, false);
            verifyComponentEndpoint(instance, plan, "load", "R1", "2", 1, true);
        } else {
            String q1Id = plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.COMPONENT, "Q1");
            String ledId = plan.idFor("load", FunctionalBlockDescriptor.EntityKind.COMPONENT, "LED1");
            NMosfetElm q1 = (NMosfetElm) instance.getComponentBindings().getSingleElement(q1Id);
            LEDElm led = (LEDElm) instance.getComponentBindings().getSingleElement(ledId);
            runtime(q1.getPostCount() == 3 && led.getPostCount() == 2,
                "controlled component terminal cardinality changed");
            int rgControl = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "RG.1"));
            int rgGate = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "RG.2"));
            int qGate = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "Q1.G"));
            int qDrain = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "Q1.D"));
            int qSource = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "Q1.S"));
            int loadSupply = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "RLOAD.1"));
            int ledAnode = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "LED1.A"));
            int ledCathode = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "LED1.K"));
            int loadNode = node(board, plan.idFor("load", FunctionalBlockDescriptor.EntityKind.PAD, "RLOAD.2"));
            int inputSupply = node(board, plan.idFor("power-adapter", FunctionalBlockDescriptor.EntityKind.PAD, "J1.1"));
            int inputReturn = node(board, plan.idFor("power-adapter", FunctionalBlockDescriptor.EntityKind.PAD, "J1.2"));
            int controlInput = node(board, plan.idFor("control-adapter", FunctionalBlockDescriptor.EntityKind.PAD, "J2.1"));
            int controlReturn = node(board, plan.idFor("control-adapter", FunctionalBlockDescriptor.EntityKind.PAD, "J2.2"));
            int pullDownGate = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "RPD.1"));
            int pullDownReturn = node(board, plan.idFor("driver", FunctionalBlockDescriptor.EntityKind.PAD, "RPD.2"));
            runtime(inputSupply == loadSupply && controlInput == rgControl,
                "device input bridges do not reach their declared local loads");
            runtime(inputReturn == controlReturn && inputReturn == qSource &&
                inputReturn == pullDownReturn, "explicit common-return wiring changed");
            runtime(pullDownGate == rgGate, "pull-down is not on the driver gate net");
            int[] distinct = { loadSupply, rgControl, rgGate, ledAnode, qDrain, qSource };
            for (int first = 0; first < distinct.length; first++)
                for (int second = first + 1; second < distinct.length; second++)
                    runtime(distinct[first] != distinct[second],
                        "controlled construction shorted distinct declared nets");
            runtime("default-led".equals(led.modelName) && q1.vt == 1.5 && q1.beta == 10.0,
                "constructed model choices differ from the pinned bounded realization");
            ResistorElm actualLoad = (ResistorElm) instance.getComponentBindings().getSingleElement(
                plan.idFor("load", FunctionalBlockDescriptor.EntityKind.COMPONENT, "RLOAD"));
            runtime(actualLoad.getResistance() == plan.getLoad().getResistor("RLOAD").getResistanceOhms(),
                "constructed resistor does not consume the resolved value recipe");
            runtime(rgGate == qGate, "controlled gate endpoints are not one solver node");
            runtime(ledCathode == qDrain, "controlled LED cathode is not on Q1 drain");
            runtime(loadNode == ledAnode, "controlled RLOAD/LED anode are not one solver node");
            runtime(rgControl != rgGate && qGate != qSource && qDrain != qSource,
                "controlled local nodes were shorted");
            runtime(q1.getPost(0) != null && q1.getPost(1) != null && q1.getPost(2) != null,
                "NMOS G/S/D posts are not present");
            runtime(led.getPost(0) != null && led.getPost(1) != null,
                "LED A/K posts are not present");
            verifyComponentEndpoint(instance, plan, "driver", "RG", "1", 0, false);
            verifyComponentEndpoint(instance, plan, "driver", "RG", "2", 1, true);
            verifyComponentEndpoint(instance, plan, "driver", "RPD", "1", 0, false);
            verifyComponentEndpoint(instance, plan, "driver", "RPD", "2", 1, false);
            verifyComponentEndpoint(instance, plan, "driver", "Q1", "G", 0, false);
            verifyComponentEndpoint(instance, plan, "driver", "Q1", "D", 2, false);
            verifyComponentEndpoint(instance, plan, "driver", "Q1", "S", 1, false);
            verifyComponentEndpoint(instance, plan, "load", "RLOAD", "1", 0, false);
            verifyComponentEndpoint(instance, plan, "load", "RLOAD", "2", 1, true);
            verifyComponentEndpoint(instance, plan, "load", "LED1", "A", 0, false);
            verifyComponentEndpoint(instance, plan, "load", "LED1", "K", 1, false);
            runtime(spec.getElementDeclaration("driver", "Q1").getPostIndex("G") == 0 &&
                spec.getElementDeclaration("driver", "Q1").getPostIndex("S") == 1 &&
                spec.getElementDeclaration("driver", "Q1").getPostIndex("D") == 2,
                "settled Q1 post correspondence changed");
            runtime(spec.getElementDeclaration("load", "LED1").getPostIndex("A") == 0 &&
                spec.getElementDeclaration("load", "LED1").getPostIndex("K") == 1,
                "settled LED post correspondence changed");
        }
        terminalCorrespondenceProof = true;
    }

    /**
     * Check the actual component-side CircuitJS post behind a board pad.  A
     * public faultable resistor terminal intentionally terminates at its
     * owned secondary open-path element; all other terminals must terminate
     * at the declared primary element/post.
     */
    private static void verifyComponentEndpoint(GeneratedBoardInstance instance,
            BoundedAssemblyPlan plan, String owner, String local, String terminal,
            int expectedPost, boolean secondary) {
        String componentId = plan.idFor(owner,
            FunctionalBlockDescriptor.EntityKind.COMPONENT, local);
        String padId = plan.idFor(owner, FunctionalBlockDescriptor.EntityKind.PAD,
            local + "." + terminal);
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().getOrNull(padId);
        CircuitMeasurementEndpoint endpoint = binding == null ?
            instance.getSimulationBindings().getEndpoint(padId) : binding.getComponentEndpoint();
        runtime(endpoint instanceof CircuitPostMeasurementEndpoint,
            "component endpoint is not a CircuitJS post: " + owner + "/" + local + "." + terminal);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        CircuitElm primary = instance.getComponentBindings().getSingleElement(componentId);
        runtime(post.getPostIndex() == expectedPost,
            "component post index changed: " + owner + "/" + local + "." + terminal);
        if (secondary) {
            runtime(post.getElement() != primary &&
                instance.getComponentBindings().isElementBoundToComponent(componentId,
                    post.getElement()),
                "secondary endpoint escaped its component owner: " + owner + "/" + local);
        } else {
            runtime(post.getElement() == primary,
                "component endpoint bypassed its declared primary: " + owner + "/" + local + "." + terminal);
        }
    }

    /** Separate independent clients prove allocation, real joins and revocation. */
    private static void verifyConstructionContext(CirSim sim,
            GeneratedBoardInstance originalOwner) {
        A04ConstructionContextCanaries.Result result = A04ConstructionContextCanaries.verify(sim);
        contract(result.contractAssertions > 0 && result.solverAssertions >= 5 &&
            result.negativeCases >= 10, "independent context corpus was incomplete");
        contractAssertions += result.contractAssertions;
        runtimeAssertions += result.solverAssertions;
        runtime(originalOwner == sim.getGeneratedBoardInstance(),
            "context corpus did not restore its original owner");
        coordinateIsolationProof = true;
        explicitJoinsProof = true;
        failureIsolationProof = true;
        verifySharedPackageCanary();
    }

    static TroubleshootBoard contextBoard(ElectricalRealizationSpec spec) {
        TroubleshootBoard board = new TroubleshootBoard(spec.getProviderDeclaration("source") != null ?
            "RESISTIVE_COUPLING" : ControlledIndicatorDeviceBehavior.FAMILY_ID);
        for (Map.Entry<String, PhysicalPackage> entry : spec.getPackageMap().getPackages().entrySet())
            board.addComponent(new BoardComponent(entry.getKey(), "A04_CONTEXT",
                entry.getValue(), entry.getKey()));

        Map<String, PadDefinition> definitions = new HashMap<String, PadDefinition>();
        Set<String> netIds = new HashSet<String>();
        for (ElectricalRealizationSpec.TerminalMapping mapping :
                spec.getTerminalMappings().values()) {
            String local = mapping.getLocalId() + "." + mapping.getTerminalId();
            if (!spec.hasPad(mapping.getOwnerKey(), local)) continue;
            String padId = spec.getPadId(mapping.getOwnerKey(), local);
            addPadDefinition(definitions, netIds, padId,
                spec.getComponentId(mapping.getOwnerKey(), mapping.getLocalId()),
                mapping.getTerminalId(), mapping.getNetId());
        }
        addDevicePad(spec, definitions, netIds, "device", "J1", "1");
        addDevicePad(spec, definitions, netIds, "device", "J1", "2");
        addDevicePad(spec, definitions, netIds, "power-adapter", "J1", "1");
        addDevicePad(spec, definitions, netIds, "power-adapter", "J1", "2");
        addDevicePad(spec, definitions, netIds, "control-adapter", "J2", "1");
        addDevicePad(spec, definitions, netIds, "control-adapter", "J2", "2");
        for (String netId : netIds) board.addNet(new BoardNet(netId));
        for (PadDefinition definition : definitions.values())
            board.addPad(new BoardPad(definition.padId, definition.componentId,
                definition.terminalId, definition.netId));

        if (spec.hasPad("device", "J1.1")) {
            String positive = spec.getPadId("device", "J1.1");
            String returned = spec.getPadId("device", "J1.2");
            board.addPowerInput(new ExternalBoardPowerInput(
                BoundedGeneratedBoardAssembler.POWER_INPUT_ID, positive, returned,
                definitions.get(positive).netId, definitions.get(returned).netId));
        }
        if (spec.hasPad("power-adapter", "J1.1")) {
            String loadPositive = spec.getPadId("power-adapter", "J1.1");
            String loadReturn = spec.getPadId("power-adapter", "J1.2");
            board.addPowerInput(new ExternalBoardPowerInput(
                ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID,
                loadPositive, loadReturn, definitions.get(loadPositive).netId,
                definitions.get(loadReturn).netId));
            String controlPositive = spec.getPadId("control-adapter", "J2.1");
            String controlReturn = spec.getPadId("control-adapter", "J2.2");
            board.addPowerInput(new ExternalBoardPowerInput(
                ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID,
                controlPositive, controlReturn, definitions.get(controlPositive).netId,
                definitions.get(controlReturn).netId));
        }
        board.validate();
        return board;
    }

    private static void addDevicePad(ElectricalRealizationSpec spec,
            Map<String, PadDefinition> definitions, Set<String> netIds,
            String owner, String local, String terminal) {
        String localPad = local + "." + terminal;
        if (!spec.hasPad(owner, localPad)) return;
        String padId = spec.getPadId(owner, localPad);
        boolean resistive = spec.getProviderDeclaration("source") != null;
        String referenceOwner = "1".equals(terminal) ?
            (resistive ? "source" : ("J1".equals(local) ? "load" : "driver")) :
            (resistive ? "load" : "driver");
        String referencePart = resistive ? "R1" :
            ("1".equals(terminal) ? ("J1".equals(local) ? "RLOAD" : "RG") : "RPD");
        ElectricalRealizationSpec.TerminalMapping reference =
            spec.getTerminalMapping(referenceOwner, referencePart, terminal);
        require(reference != null, "fixture external input has no declared reference");
        String netId = reference.getNetId();
        addPadDefinition(definitions, netIds, padId,
            spec.getComponentId(owner, local), terminal, netId);
    }

    private static void addPadDefinition(Map<String, PadDefinition> definitions,
            Set<String> netIds, String padId, String componentId,
            String terminalId, String netId) {
        PadDefinition prior = definitions.get(padId);
        if (prior != null) {
            if (!prior.componentId.equals(componentId) || !prior.netId.equals(netId))
                throw new IllegalStateException("Conflicting context pad definition " + padId);
            return;
        }
        definitions.put(padId, new PadDefinition(padId, componentId, terminalId, netId));
        netIds.add(netId);
    }

    private static void verifySharedPackageCanary() {
        Vector<String> terminals = new Vector<String>();
        terminals.add("P1");
        terminals.add("P2");
        terminals.add("VCC");
        terminals.add("GND");
        PhysicalPackage shared = new PhysicalPackage("A04_SHARED_PACKAGE",
            terminals, new Vector<String>());
        Map<String, PhysicalPackage> packages = new HashMap<String, PhysicalPackage>();
        packages.put("board/U1", shared);
        Map<String, String> owners = new HashMap<String, String>();
        owners.put("board/U1", "multi-unit");
        Map<String, String> firstMap = new HashMap<String, String>();
        firstMap.put("IN", "P1");
        firstMap.put("OUT", "P2");
        firstMap.put("VCC", "VCC");
        firstMap.put("GND", "GND");
        Map<String, String> secondMap = new HashMap<String, String>();
        secondMap.put("A", "P2");
        secondMap.put("K", "P1");
        secondMap.put("VCC", "VCC");
        secondMap.put("GND", "GND");
        Vector<String> firstTerminals = new Vector<String>();
        firstTerminals.addAll(firstMap.keySet());
        Vector<String> secondTerminals = new Vector<String>();
        secondTerminals.addAll(secondMap.keySet());
        ElectricalUnitPackageMap.Unit first = new ElectricalUnitPackageMap.Unit(
            "multi-unit", "analog", "board/U1", firstTerminals, firstMap);
        ElectricalUnitPackageMap.Unit second = new ElectricalUnitPackageMap.Unit(
            "multi-unit", "indicator", "board/U1", secondTerminals, secondMap);
        Vector<ElectricalUnitPackageMap.Unit> units =
            new Vector<ElectricalUnitPackageMap.Unit>();
        units.add(first);
        units.add(second);
        ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
            ElectricalUnitPackageMap.VERSION, packages, owners, units);
        contract(map.getPackageCount() == 1 && map.getUnitCount() == 2,
            "same-package logical units were split into separate packages");
        contract("VCC".equals(first.getPackageTerminalByUnitTerminal().get("VCC")) &&
            "VCC".equals(second.getPackageTerminalByUnitTerminal().get("VCC")) &&
            "GND".equals(first.getPackageTerminalByUnitTerminal().get("GND")) &&
            "GND".equals(second.getPackageTerminalByUnitTerminal().get("GND")),
            "shared package supply pins were not explicit");
        multiUnitPackageProof = true;
    }

    private static int node(TroubleshootBoard board, String padId) {
        CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("No CircuitJS endpoint for " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().getNode(post.getPostIndex());
    }

    private static boolean samePost(Point first, Point second) {
        return first != null && first.equals(second);
    }

    private static void provider(ElectricalRealizationSpec spec, String owner,
            String providerId, int version) {
        ElectricalRealizationSpec.ProviderDeclaration declaration =
            spec.getProviderDeclaration(owner);
        contract(declaration != null && providerId.equals(declaration.getProviderId()) &&
            version == declaration.getProviderVersion(), "provider declaration changed: " + owner);
    }

    private static void element(ElectricalRealizationSpec spec, String owner, String id,
            String kind, String first, int firstPost, String second, int secondPost) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
            spec.getElementDeclaration(owner, id);
        contract(declaration != null && kind.equals(declaration.getKind()) &&
            declaration.getPostIndex(first) == firstPost &&
            declaration.getPostIndex(second) == secondPost,
            "element declaration changed: " + owner + "/" + id);
    }

    private static void element(ElectricalRealizationSpec spec, String owner, String id,
            String kind, String first, int firstPost, String second, int secondPost,
            String third, int thirdPost) {
        element(spec, owner, id, kind, first, firstPost, second, secondPost);
        contract(spec.getElementDeclaration(owner, id).getPostIndex(third) == thirdPost,
            "element third terminal changed: " + owner + "/" + id);
    }

    private static void mapping(ElectricalRealizationSpec spec, BoundedAssemblyPlan plan,
            String owner, String local, String terminal, String packageTerminal, String net) {
        ElectricalRealizationSpec.TerminalMapping mapping =
            spec.getTerminalMapping(owner, local, terminal);
        contract(mapping != null && owner.equals(mapping.getOwnerKey()) &&
            local.equals(mapping.getLocalId()) && terminal.equals(mapping.getTerminalId()) &&
            packageTerminal.equals(mapping.getPackageTerminalId()) &&
            net.equals(mapping.getNetId()) && mapping.getComponentId().equals(
                spec.getComponentId(owner, local)),
            "terminal/package/net mapping changed: " + owner + "/" + local + "." + terminal);
    }

    private static BoundedAssemblyRequest requestFor(int version, long seed) {
        if (version == 1) return BoundedAssemblyRequest.forCanary(seed);
        if (version == 2) return BoundedAssemblyRequest.forControlledIndicator(seed);
        if (version == 3) return BoundedAssemblyRequest.forControlledIndicatorValues(seed);
        throw new IllegalArgumentException("Unsupported A04 construction version " + version);
    }

    private static void dispose(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedBoardInstance protectedOwner, Vector<GeneratedBoardInstance> disposed) {
        if (candidate == null || disposed.contains(candidate)) return;
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(),
            "A04 refused to dispose an active/protected owner");
        candidate.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : candidate.getSimulationElements()) element.delete();
        disposed.add(candidate);
    }

    private static String report(Vector<CaseResult> cases, long started,
            boolean ownerRestored) {
        StringBuilder result = new StringBuilder();
        result.append("{\"protocol\":").append(q(PROTOCOL))
            .append(",\"status\":\"PASS\"")
            .append(",\"contractAssertions\":").append(contractAssertions)
            .append(",\"runtimeAssertions\":").append(runtimeAssertions)
            .append(",\"coordinateIsolation\":").append(coordinateIsolationProof ? "true" : "false")
            .append(",\"explicitJoins\":").append(explicitJoinsProof ? "true" : "false")
            .append(",\"terminalCorrespondence\":").append(terminalCorrespondenceProof ? "true" : "false")
            .append(",\"failureIsolation\":").append(failureIsolationProof ? "true" : "false")
            .append(",\"recipeIdentity\":").append(recipeIdentityProof ? "true" : "false")
            .append(",\"multiUnitPackage\":").append(multiUnitPackageProof ? "true" : "false")
            .append(",\"originalOwnerRestored\":").append(ownerRestored ? "true" : "false")
            .append(",\"candidateCleanup\":\"PASS\",\"cases\":[");
        for (int index = 0; index < cases.size(); index++) {
            if (index != 0) result.append(',');
            result.append(cases.get(index).toJson());
        }
        return result.append("],\"elapsedMs\":").append(elapsed(started)).append("}").toString();
    }

    private static String firstDump(CirSim sim) {
        return sim.dumpCircuit();
    }

    private static String originalDump(CirSim sim) { return firstDump(sim); }

    private static long elapsed(long start) {
        long result = System.currentTimeMillis() - start;
        return result < 0 ? 0 : result;
    }

    private static Throwable retain(Throwable original, Throwable extra) {
        if (original == null) return extra;
        if (extra != original) original.addSuppressed(extra);
        return original;
    }

    private static String message(Throwable failure) {
        return failure == null ? "unknown" :
            (failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("A04 verification failed", failure);
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            contract(true, label);
            return;
        }
        throw new IllegalStateException("A04 accepted " + label);
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            contract(true, label);
            return;
        }
        throw new IllegalStateException("A04 accepted " + label);
    }

    private static void contract(boolean condition, String label) {
        contractAssertions++;
        if (!condition) throw new IllegalStateException("A04 contract: " + label);
    }

    private static void runtime(boolean condition, String label) {
        runtimeAssertions++;
        if (!condition) throw new IllegalStateException("A04 runtime: " + label);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("A04: " + label);
    }

    private static String q(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '\\' || c == '"') result.append('\\').append(c);
            else if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else result.append(c);
        }
        return result.append('"').toString();
    }

    private static final class PadDefinition {
        final String padId;
        final String componentId;
        final String terminalId;
        final String netId;

        PadDefinition(String padId, String componentId, String terminalId, String netId) {
            this.padId = padId;
            this.componentId = componentId;
            this.terminalId = terminalId;
            this.netId = netId;
        }
    }

    private static final class CaseResult {
        final int generatorVersion;
        final String seed;
        final int elementCount;
        final int packageCount;
        final int unitCount;
        final long elapsedMs;
        final long assemblyMs;
        final int manifestBytes;

        CaseResult(int generatorVersion, String seed, int elementCount,
                int packageCount, int unitCount, long elapsedMs,
                long assemblyMs, int manifestBytes) {
            this.generatorVersion = generatorVersion;
            this.seed = seed;
            this.elementCount = elementCount;
            this.packageCount = packageCount;
            this.unitCount = unitCount;
            this.elapsedMs = elapsedMs;
            this.assemblyMs = assemblyMs;
            this.manifestBytes = manifestBytes;
        }

        String toJson() {
            return "{\"generatorVersion\":" + generatorVersion +
                ",\"seed\":" + q(seed) +
                ",\"elementCount\":" + elementCount +
                ",\"packageCount\":" + packageCount +
                ",\"unitCount\":" + unitCount +
                ",\"elapsedMs\":" + elapsedMs +
                ",\"assemblyMs\":" + assemblyMs +
                ",\"manifestBytes\":" + manifestBytes + "}";
        }
    }
}
