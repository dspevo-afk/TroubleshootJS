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
            for (long seed : SEEDS) {
                cases.add(verifyCase(sim, original, originalOwner, seed,
                    BoundedAssemblyRequest.forCanary(seed), "resistive", candidates));
                cases.add(verifyCase(sim, original, originalOwner, seed,
                    BoundedAssemblyRequest.forControlledIndicator(seed),
                    "controlled", candidates));
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
            GeneratedBoardInstance originalOwner, long seed,
            BoundedAssemblyRequest request, String route,
            Vector<GeneratedBoardInstance> candidates) {
        long start = System.currentTimeMillis();
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        verifyPlanContract(plan, route, seed);
        String canonical = A03RealizationReplay.capture(plan).toCanonical();
        RealizationManifest parsed = RealizationManifest.parse(canonical);
        contract(canonical.equals(parsed.toCanonical()),
            "A03 manifest round-trip changed for current " + route);
        BoundedAssemblyPlan replay = A03RealizationReplay.resolve(parsed);
        contract(plan.getSemanticSignature().equals(replay.getSemanticSignature()),
            "A03 replay changed construction semantics for current " + route);
        contract(parsed.getDescriptor().getGenerator().getVersion() ==
            BoundedAssemblyRequest.GENERATOR_VERSION,
            "current generator version changed for " + route);
        contract(parsed.getDescriptor().getRootSeed() == seed,
            "A03 exact seed changed for current " + route);

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
            "a04-construction-" + route);
        runtime(sim.getGeneratedBoardInstance() == candidate,
            "candidate was not published as the active owner");
        runtime(sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady() &&
            sim.isGeneratedRuntimeSettled(), "candidate did not settle through CircuitJS");
        verifyRuntimeCorrespondence(sim, candidate, plan, route);

        original.restore(sim);
        original.assertRestored(sim);
        runtime(originalOwner == sim.getGeneratedBoardInstance(),
            "original owner was not restored after candidate " + route);
        runtime(originalDump.equals(firstDump(sim)),
            "original solver element changed after candidate " + route);
        return new CaseResult(route, Long.toString(seed),
            plan.getRequest().getDescriptor().getGenerator().getVersion(),
            elapsed(start), assemblyElapsed, true, true);
    }

    private static void verifyPlanContract(BoundedAssemblyPlan plan, String route,
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
        if ("resistive".equals(route)) {
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
            for (ControlledIndicatorChannel channel : plan.getChannels()) {
                String driverOwner = channel.getDriverKey();
                String loadOwner = channel.getLoadKey();
                ComposedBlockContribution driver = plan.getBlocks().get(driverOwner);
                ComposedBlockContribution load = plan.getBlocks().get(loadOwner);
                ElectricalRealizationSpec.ProviderDeclaration driverDeclaration =
                    spec.getProviderDeclaration(driverOwner);
                ElectricalRealizationSpec.ProviderDeclaration loadDeclaration =
                    spec.getProviderDeclaration(loadOwner);
                contract(driver != null && load != null && driverDeclaration != null &&
                    loadDeclaration != null && driverDeclaration.getContribution() == driver &&
                    loadDeclaration.getContribution() == load,
                    "controlled channel contribution identity changed: " + channel.getKey());
                provider(spec, driverOwner, driver.getProviderTypeId(),
                    driver.getProviderVersion());
                provider(spec, loadOwner, ControlledIndicatorBlockContributions.LOAD_TYPE_ID,
                    ControlledIndicatorBlockContributions.LOAD_VERSION);
                verifyDeclaredElements(spec, driverOwner, driverDeclaration);
                verifyDeclaredElements(spec, loadOwner, loadDeclaration);
                verifyDeclaredMappings(spec, plan, driverOwner);
                verifyDeclaredMappings(spec, plan, loadOwner);
                contract(plan.netForPort(loadOwner, "SUPPLY").equals(
                    plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY, "POWER_OUT")) &&
                    plan.netForPort(driverOwner, "CONTROL").equals(
                        plan.netForPort(channel.getControlAdapterKey(), "CONTROL_OUT")) &&
                    plan.netForPort(driverOwner, "SWITCHED_SINK").equals(
                        plan.netForPort(loadOwner, "SWITCHED_LOAD")) &&
                    plan.netForPort(driverOwner, "RETURN").equals(
                        plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY, "RETURN")),
                    "controlled channel joins changed: " + channel.getKey());
                contract(load.getResolvedValueRecipe() != null &&
                    loadDeclaration.getContribution().getResolvedValueRecipe() ==
                        load.getResolvedValueRecipe(),
                    "resolved channel value identity changed: " + channel.getKey());
            }
            ElectricalRealizationSpec.ProviderDeclaration support =
                spec.getProviderDeclaration(plan.getSupportBlockKey());
            contract(support != null &&
                SupplyPresentBlockContributions.TYPE_ID.equals(support.getProviderId()) &&
                support.getContribution() == plan.getBlocks().get(plan.getSupportBlockKey()),
                "controlled support provider declaration changed");
            recipeIdentityProof = true;
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
            GeneratedBoardInstance instance, BoundedAssemblyPlan plan, String route) {
        runtime(sim.nodeList != null && sim.nodeList.size() > 0,
            "settled candidate has no CircuitJS node graph");
        TroubleshootBoard board = instance.getBoard();
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        if ("resistive".equals(route)) {
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
            for (ControlledIndicatorChannel channel : plan.getChannels())
                verifyControlledChannelRuntime(instance, board, plan, spec, channel);
        }
        terminalCorrespondenceProof = true;
    }

    private static void verifyControlledChannelRuntime(GeneratedBoardInstance instance,
            TroubleshootBoard board, BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, ControlledIndicatorChannel channel) {
        String driverOwner = channel.getDriverKey();
        String loadOwner = channel.getLoadKey();
        ComposedBlockContribution driver = plan.getBlocks().get(driverOwner);
        ComposedBlockContribution load = plan.getBlocks().get(loadOwner);
        ElectricalRealizationSpec.ElementDeclaration q1Declaration =
            spec.getElementDeclaration(driverOwner, "Q1");
        ElectricalRealizationSpec.ElementDeclaration ledDeclaration =
            spec.getElementDeclaration(loadOwner, "LED1");
        CircuitElm q1 = instance.getComponentBindings().getSingleElement(
            plan.idFor(driverOwner, FunctionalBlockDescriptor.EntityKind.COMPONENT, "Q1"));
        LEDElm led = (LEDElm) instance.getComponentBindings().getSingleElement(
            plan.idFor(loadOwner, FunctionalBlockDescriptor.EntityKind.COMPONENT, "LED1"));
        runtime(q1.getPostCount() == 3 && led.getPostCount() == 2,
            "controlled component terminal cardinality changed: " + channel.getKey());
        for (Map.Entry<String, Integer> terminal : q1Declaration.getPostIndexByTerminal().entrySet())
            verifyComponentEndpoint(instance, plan, driverOwner, "Q1", terminal.getKey(),
                terminal.getValue().intValue(), false);
        verifyComponentEndpoint(instance, plan, loadOwner, "LED1", "A",
            ledDeclaration.getPostIndex("A"), false);
        verifyComponentEndpoint(instance, plan, loadOwner, "LED1", "K",
            ledDeclaration.getPostIndex("K"), false);
        for (ComposedBlockContribution.ResistorRecipe recipe : driver.getResistors().values()) {
            verifyComponentEndpoint(instance, plan, driverOwner,
                recipe.getComponentLocalId(), "1", 0, false);
            verifyComponentEndpoint(instance, plan, driverOwner,
                recipe.getComponentLocalId(), "2", 1, recipe.isMutable());
        }
        ComposedBlockContribution.ResistorRecipe loadRecipe = load.getResistor("RLOAD");
        verifyComponentEndpoint(instance, plan, loadOwner, "RLOAD", "1", 0, false);
        verifyComponentEndpoint(instance, plan, loadOwner, "RLOAD", "2", 1,
            loadRecipe.isMutable());

        int control = node(board, plan.idFor(driverOwner,
            FunctionalBlockDescriptor.EntityKind.PAD,
            driver.getResistors().containsKey("RG") ? "RG.1" : "RB.1"));
        DeviceAdapterContract controlAdapter = channel.controlAdapter();
        int controlInput = node(board, plan.idFor(channel.getControlAdapterKey(),
            FunctionalBlockDescriptor.EntityKind.PAD,
            controlAdapter.getComponentLocalId() + ".1"));
        int commonReturn = node(board, plan.idFor(DeviceAdapterContract.POWER_ADAPTER_KEY,
            FunctionalBlockDescriptor.EntityKind.PAD, "J1.2"));
        int driverReturn = node(board, padForNet(spec, plan, driverOwner, "RETURN"));
        int controlReturn = node(board, padForNet(spec, plan,
            channel.getControlAdapterKey(), "RETURN"));
        int supportReturn = node(board, padForNet(spec, plan,
            plan.getSupportBlockKey(), "RETURN"));
        verifyControlledReturnAlias(plan, channel);
        String declaredReturn = plan.netForPort(driverOwner, "RETURN");
        String loadDeclaredReturn = plan.netForPort(loadOwner, "RETURN");
        int supply = node(board, plan.idFor(DeviceAdapterContract.POWER_ADAPTER_KEY,
            FunctionalBlockDescriptor.EntityKind.PAD, "J1.1"));
        int loadSupply = node(board, padForNet(spec, plan, loadOwner, "SUPPLY"));
        int switched = node(board, padForNet(spec, plan, driverOwner, "SWITCHED_SINK"));
        int switchedLoad = node(board, padForNet(spec, plan, loadOwner, "SWITCHED_LOAD"));
        runtime(control == controlInput && commonReturn == driverReturn &&
            commonReturn == controlReturn && commonReturn == supportReturn &&
            declaredReturn.equals(loadDeclaredReturn) &&
            supply == loadSupply && switched == switchedLoad,
            "controlled channel joins do not match declared nets: " + channel.getKey());
        boolean nmosKind = "NMOS".equals(q1Declaration.getKind());
        int controlTerminal = q1.getNode(q1Declaration.getPostIndex(nmosKind ? "G" : "B"));
        int outputTerminal = q1.getNode(q1Declaration.getPostIndex(nmosKind ? "D" : "C"));
        int returnTerminal = q1.getNode(q1Declaration.getPostIndex(nmosKind ? "S" : "E"));
        int ledAnode = led.getNode(ledDeclaration.getPostIndex("A"));
        int ledCathode = led.getNode(ledDeclaration.getPostIndex("K"));
        runtime(outputTerminal == switched && returnTerminal == commonReturn &&
            ledCathode == switched,
            "driver/load terminals lost their electrical joins: " + channel.getKey());
        verifyDistinctChannelNodes(new int[] { supply, control, controlTerminal,
            ledAnode, outputTerminal, returnTerminal }, channel.getKey());

        CircuitElm driverElement = q1;
        ElectricalRealizationSpec.ElementDeclaration driverElementSpec = q1Declaration;
        if (driverElement instanceof NMosfetElm) {
            NMosfetElm nmos = (NMosfetElm) driverElement;
            runtime(nmos.vt == driverElementSpec.getParameter("threshold") &&
                nmos.beta == driverElementSpec.getParameter("beta"),
                "constructed NMOS model choices changed: " + channel.getKey());
        } else {
            runtime(driverElement instanceof NTransistorElm &&
                ((NTransistorElm) driverElement).beta == driverElementSpec.getParameter("beta") &&
                driverElementSpec.getModelId().equals(((NTransistorElm) driverElement).modelName),
                "constructed NPN model choices changed: " + channel.getKey());
        }
        runtime(ledDeclaration.getModelId().equals(led.modelName),
            "constructed LED model choice changed: " + channel.getKey());
        ResistorElm actualLoad = (ResistorElm) instance.getComponentBindings().getSingleElement(
            plan.idFor(loadOwner, FunctionalBlockDescriptor.EntityKind.COMPONENT, "RLOAD"));
        runtime(actualLoad.getResistance() == loadRecipe.getResistanceOhms(),
            "constructed resistor does not consume the resolved channel value recipe: " +
                channel.getKey());
    }

    /** Independent solver-node oracle, also exercised with deliberate shorts natively. */
    static void verifyDistinctChannelNodes(int[] nodes, String channel) {
        runtime(nodes != null && nodes.length == 6, "incomplete channel node oracle");
        for (int i = 0; i < nodes.length; i++)
            for (int j = 0; j < i; j++)
                runtime(nodes[i] != nodes[j],
                    "controlled channel shorted distinct nodes " + j + "/" + i + ": " + channel);
    }

    private static String padForNet(ElectricalRealizationSpec spec, BoundedAssemblyPlan plan,
            String owner, String localNet) {
        String net = plan.netFor(owner, localNet);
        for (ElectricalRealizationSpec.TerminalMapping mapping : spec.getTerminalMappings().values())
            if (owner.equals(mapping.getOwnerKey()) && net.equals(mapping.getNetId()))
                return spec.getPadId(owner, mapping.getLocalId() + "." + mapping.getTerminalId());
        throw new IllegalStateException("No pad for declared net " + owner + "/" + localNet);
    }

    /**
     * The controlled load's RETURN is a logical port, not a load-local pad.
     * Keep that alias tied to the same declared bus used by the physical
     * driver return (and reject a fictitious load RETURN pad).
     */
    static void verifyControlledReturnAlias(BoundedAssemblyPlan plan,
            ControlledIndicatorChannel channel) {
        if (plan == null || channel == null)
            throw new IllegalArgumentException("Controlled return inputs are required");
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        String driverReturn = plan.netForPort(channel.getDriverKey(), "RETURN");
        String loadReturn = plan.netForPort(channel.getLoadKey(), "RETURN");
        String powerReturn = plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY,
            "RETURN");
        runtime(driverReturn.equals(powerReturn) && loadReturn.equals(powerReturn) &&
            !spec.hasPad(channel.getLoadKey(), "RETURN"),
            "controlled load RETURN must alias the shared bus without a physical pad: " +
                channel.getKey());
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
        if (spec == null) throw new IllegalArgumentException("Electrical spec is required");
        TroubleshootBoard board = new TroubleshootBoard(
            spec.getProviderDeclaration("source") != null
                ? BoundedGeneratedBoardAssembler.FAMILY_ID
                : ControlledIndicatorDeviceBehavior.FAMILY_ID);
        for (Map.Entry<String, PhysicalPackage> entry : spec.getPackageMap().getPackages().entrySet())
            board.addComponent(new BoardComponent(entry.getKey(), "A04_CONTEXT",
                entry.getValue(), entry.getKey()));
        Set<String> netIds = new HashSet<String>();
        for (ElectricalRealizationSpec.PadBindingSpec binding :
                spec.getPadBindings().values()) {
            netIds.add(binding.getNetId());
        }
        for (String netId : netIds) board.addNet(new BoardNet(netId));
        for (ElectricalRealizationSpec.PadBindingSpec binding :
                spec.getPadBindings().values()) {
            board.addPad(new BoardPad(binding.getPadId(), binding.getComponentId(),
                binding.getTerminalId(), binding.getNetId()));
        }
        for (ElectricalRealizationSpec.PowerInputSpec input :
                spec.getPowerInputs().values()) {
            board.addPowerInput(new ExternalBoardPowerInput(input.getInputId(),
                input.getPositivePadId(), input.getReturnPadId(),
                input.getPositiveNetId(), input.getReturnNetId()));
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
        PhysicalPackage shared = PhysicalPackage.developerPackageWithGenericGeometry(
            "A04_SHARED_PACKAGE", terminals, new Vector<String>(), false);
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

    static void verifyDeclaredElements(ElectricalRealizationSpec spec, String owner,
            ElectricalRealizationSpec.ProviderDeclaration provider) {
        contract(spec != null && owner != null && provider != null,
            "element declaration inputs are incomplete");
        contract(owner.equals(provider.getOwnerKey()),
            "provider declaration owner changed: " + owner);

        Set<String> providerIds = new HashSet<String>(provider.getElementIds());
        Set<String> providerElementIds = new HashSet<String>(provider.getElements().keySet());
        Set<String> specElementIds = new HashSet<String>();
        for (ElectricalRealizationSpec.ElementDeclaration element :
                spec.getElementDeclarations().values()) {
            if (owner.equals(element.getOwnerKey()))
                specElementIds.add(element.getElementId());
        }
        contract(providerIds.equals(providerElementIds) && providerIds.equals(specElementIds),
            "provider/spec element membership changed: " + owner);

        for (ElectricalRealizationSpec.ElementDeclaration element : provider.getElements().values()) {
            String elementId = element.getElementId();
            ElectricalRealizationSpec.ElementDeclaration declared =
                spec.getElementDeclaration(owner, elementId);
            contract(owner.equals(element.getOwnerKey()) && declared == element,
                "element ownership changed: " + owner + "/" + elementId);
            verifyElementShapeAssociation(spec, owner, provider, element);
        }
    }

    /** Pure changed predicate; caller separately establishes exact declaration identity. */
    static void verifyElementShapeAssociation(ElectricalRealizationSpec spec, String owner,
            ElectricalRealizationSpec.ProviderDeclaration provider,
            ElectricalRealizationSpec.ElementDeclaration element) {
        contract(element != null && owner.equals(element.getOwnerKey()),
            "element shape owner changed");
        String elementId = element.getElementId();
        verifyPrimitivePostShape(element.getKind(), element.getPostIndexByTerminal());
        String componentId = element.getComponentId();
        if (componentId != null)
            verifyDeclaredComponentAssociation(spec, owner, provider, elementId, componentId);
    }

    static void verifyPrimitivePostShape(String kind, Map<String, Integer> actualPosts) {
        contract(expectedPrimitivePosts(kind).equals(actualPosts),
            "primitive post declaration changed: " + kind);
    }

    private static Map<String, Integer> posts(String first, int firstPost) {
        Map<String, Integer> result = new java.util.TreeMap<String, Integer>();
        result.put(first, firstPost);
        return result;
    }

    private static Map<String, Integer> posts(String first, int firstPost,
            String second, int secondPost) {
        Map<String, Integer> result = posts(first, firstPost);
        result.put(second, secondPost);
        return result;
    }

    private static Map<String, Integer> posts(String first, int firstPost,
            String second, int secondPost, String third, int thirdPost) {
        Map<String, Integer> result = posts(first, firstPost, second, secondPost);
        result.put(third, thirdPost);
        return result;
    }

    private static Map<String, Integer> expectedPrimitivePosts(String kind) {
        if ("NMOS".equals(kind)) return posts("G", 0, "D", 2, "S", 1);
        if ("NPN".equals(kind)) return posts("B", 0, "C", 1, "E", 2);
        if ("LED".equals(kind)) return posts("A", 0, "K", 1);
        if ("VOLTAGE".equals(kind)) return posts("+", 1, "-", 0);
        if ("GROUND".equals(kind)) return posts("1", 0);
        if ("RESISTOR".equals(kind) || "WIRE".equals(kind) ||
                "SWITCH".equals(kind) || "FAULT_HELPER".equals(kind))
            return posts("1", 0, "2", 1);
        throw new IllegalStateException("Unknown declared primitive " + kind);
    }

    private static void verifyDeclaredComponentAssociation(ElectricalRealizationSpec spec,
            String owner, ElectricalRealizationSpec.ProviderDeclaration provider,
            String elementId, String componentId) {
        PhysicalPackage physicalPackage = spec.getPackageMap().getPackages().get(componentId);
        String packageOwner = spec.getPackageMap().getPackageOwners().get(componentId);
        contract(physicalPackage != null && packageOwner != null,
            "element component has no declared package owner: " + owner + "/" + elementId);
        if (!provider.isDeviceOwner())
            contract(owner.equals(packageOwner),
                "element component belongs to a foreign provider: " + owner + "/" + elementId);

        boolean packageUnitFound = false;
        for (ElectricalUnitPackageMap.Unit unit : spec.getPackageMap().getUnits().values()) {
            if (!componentId.equals(unit.getComponentId())) continue;
            packageUnitFound = true;
            contract(packageOwner.equals(unit.getOwnerKey()),
                "element component package/unit owner changed: " + owner + "/" + elementId);
        }
        contract(packageUnitFound,
            "element component has no declared package unit: " + owner + "/" + elementId);

        boolean physicalUnitFound = false;
        for (ElectricalRealizationSpec.PhysicalUnitSpec unit :
                spec.getPhysicalUnits().values()) {
            if (!componentId.equals(unit.getComponentId())) continue;
            physicalUnitFound = true;
            contract(packageOwner.equals(unit.getOwnerKey()) &&
                physicalPackage.getId().equals(unit.getPackageId()),
                "element component physical unit changed: " + owner + "/" + elementId);
        }
        contract(physicalUnitFound,
            "element component has no declared physical unit: " + owner + "/" + elementId);
    }

    private static void verifyDeclaredMappings(ElectricalRealizationSpec spec,
            BoundedAssemblyPlan plan, String owner) {
        for (ElectricalRealizationSpec.TerminalMapping terminal : spec.getTerminalMappings().values()) {
            if (!owner.equals(terminal.getOwnerKey())) continue;
            mapping(spec, plan, owner, terminal.getLocalId(), terminal.getTerminalId(),
                terminal.getPackageTerminalId(), terminal.getNetId());
        }
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
        final String route;
        final String seed;
        final int generatorVersion;
        final long elapsedMs;
        final long assemblyMs;
        final boolean replayVerified;
        final boolean ownerRestored;

        CaseResult(String route, String seed, int generatorVersion,
                long elapsedMs, long assemblyMs, boolean replayVerified,
                boolean ownerRestored) {
            this.route = route;
            this.seed = seed;
            this.generatorVersion = generatorVersion;
            this.elapsedMs = elapsedMs;
            this.assemblyMs = assemblyMs;
            this.replayVerified = replayVerified;
            this.ownerRestored = ownerRestored;
        }

        String toJson() {
            return "{\"route\":" + q(route) +
                ",\"seed\":" + q(seed) +
                ",\"generatorVersion\":" + generatorVersion +
                ",\"replayVerified\":" + (replayVerified ? "true" : "false") +
                ",\"ownerRestored\":" + (ownerRestored ? "true" : "false") +
                ",\"elapsedMs\":" + elapsedMs +
                ",\"assemblyMs\":" + assemblyMs + "}";
        }
    }
}
