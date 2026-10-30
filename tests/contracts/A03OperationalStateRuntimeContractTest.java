package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Native runtime boundary for the current resistive A03 producer. It exercises
 * the real CircuitJS elements created by direct generation and manifest replay,
 * then captures the same physical scene consumed by the workbench renderer.
 */
public final class A03OperationalStateRuntimeContractTest {
    private static final long[] SEEDS = {
        0L, 1L, 9007199254740993L, Long.MIN_VALUE
    };
    private static int assertions;

    private A03OperationalStateRuntimeContractTest() { }

    public static void main(String[] args) {
        CirSim previousSingleton = CirSim.theSim;
        CirSim previousCircuitSim = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        try {
            for (long seed : SEEDS) verifySeed(sim, seed);
            System.out.println("PASS: A03 operational-state runtime contracts assertions=" +
                assertions + " seeds=" + SEEDS.length +
                " scope=ACTUAL_ASSEMBLY_REPLAY_AND_PHYSICAL_SCENE");
        } finally {
            CircuitElm.sim = previousCircuitSim;
            CirSim.theSim = previousSingleton;
        }
    }

    private static void verifySeed(CirSim sim, long seed) {
        BoundedGeneratedBoardAssembler.Result direct = null;
        BoundedGeneratedBoardAssembler.Result replay = null;
        try {
            BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(seed);
            require(!BoundedAssemblyPlan.resolve(request).isControlledIndicator(),
                "representative seed selects the current resistive composition");

            direct = BoundedGeneratedBoardAssembler.assemble(request);
            GeneratedBoardInstance directInstance = direct.getInstance();
            require(directInstance.getSeed() == seed,
                "direct actual assembly preserves the exact signed seed");
            require(BoundedGeneratedBoardAssembler.FAMILY_ID.equals(
                    directInstance.getCircuitFamilyId()),
                "direct actual assembly returns the bounded resistive family");
            requireDirectCircuitElements(directInstance);

            String canonical = direct.getRealizationManifest().toCanonical();
            RealizationManifest parsed = RealizationManifest.parse(canonical);
            require(canonical.equals(parsed.toCanonical()),
                "direct runtime manifest survives canonical parse and encode");
            replay = BoundedGeneratedBoardAssembler.replay(parsed);
            GeneratedBoardInstance replayInstance = replay.getInstance();
            require(replayInstance.getSeed() == seed,
                "actual manifest replay preserves the exact signed seed");
            require(canonical.equals(replay.getRealizationManifest().toCanonical()),
                "actual replay retains the original manifest bytes");
            FreshGeneratedRuntimeInstallation.requireDisjoint(directInstance,
                replayInstance);
            requireDirectCircuitElements(replayInstance);

            verifyOperationalScene(sim, directInstance, "direct seed=" + seed);
            verifyOperationalScene(sim, replayInstance, "replay seed=" + seed);
            if (seed == 1L) verifyMissingStateRejection(directInstance);
        } finally {
            dispose(replay == null ? null : replay.getInstance());
            dispose(direct == null ? null : direct.getInstance());
        }
    }

    private static void requireDirectCircuitElements(GeneratedBoardInstance instance) {
        Vector<CircuitElm> elements = instance.getSimulationElements();
        int resistors = 0;
        for (CircuitElm element : elements)
            if (element instanceof ResistorElm) resistors++;
        require(resistors >= 2,
            "actual resistive composition owns at least two CircuitJS resistor elements");

        for (String padId : instance.getBoard().getPadIds()) {
            CircuitMeasurementEndpoint endpoint =
                instance.getBoard().getSimulationBindings().getEndpoint(padId);
            require(endpoint instanceof CircuitPostMeasurementEndpoint,
                "generated pad resolves to an actual CircuitJS post: " + padId);
            CircuitElm element = ((CircuitPostMeasurementEndpoint)endpoint).getElement();
            require(elements.contains(element),
                "generated pad post belongs to the live assembled element vector: " + padId);
        }
    }

    private static void verifyOperationalScene(CirSim sim,
            GeneratedBoardInstance instance, String label) {
        GeneratedComponentOperationalStates states = instance.getOperationalStates();
        require(states != null, label + " owns a non-null operational-state map");

        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        CircuitPostMeasurementEndpoint j1BeforeScene = requireJ1EndpointOracle(
            instance, label, "before physical-scene construction");
        Vector<CircuitElm> canonicalElementsBefore = instance.getSimulationElements();
        BoardModificationController modifications = new BoardModificationController(sim, instance);
        WorkbenchPhysicalSceneSource source = new WorkbenchPhysicalSceneSource(instance, modifications);
        WorkbenchPhysicalScene scene = source.capture(PcbBoardSide.TOP, 0);
        CircuitPostMeasurementEndpoint j1AfterScene = requireJ1EndpointOracle(
            instance, label, "after physical-scene capture");
        Vector<CircuitElm> canonicalElementsAfter = instance.getSimulationElements();
        require(sameIdentities(canonicalElementsBefore, canonicalElementsAfter),
            label + " scene/controller construction preserves canonical element order and membership");
        require(j1BeforeScene == j1AfterScene &&
                j1BeforeScene.getElement() == j1AfterScene.getElement() &&
                j1BeforeScene.getPostIndex() == j1AfterScene.getPostIndex(),
            label + " scene/controller construction preserves the J1.1 endpoint identity");
        int mountedCount = 0;
        for (PhysicalBoardSlot slot : runtime.getSlots()) {
            PhysicalPart<?> installed = slot.getInstalledPart();
            require(installed != null,
                label + " has a mounted part for slot " + slot.getComponentId());
            require(!(installed instanceof PhysicalLedPart),
                label + " resistive fixture unexpectedly includes an LED slot");
            require(!states.isIlluminated(slot.getComponentId()),
                label + " reports a non-LED mounted component dark: " +
                    slot.getComponentId());
            WorkbenchPhysicalScene.Part appearance =
                scene.installed(slot.getComponentId());
            require(appearance != null && appearance.mounted,
                label + " scene contains the mounted physical part: " +
                    slot.getComponentId());
            require(!appearance.illuminated,
                label + " scene reports the non-LED part dark: " +
                    slot.getComponentId());
            mountedCount++;
        }
        require(mountedCount > 0,
            label + " captures at least one mounted part in the physical scene");
    }

    /** Persistent SUPPLY_TRACE copper, derived from construction rather than
     * the composed report producer or the live binding under test. */
    private static CircuitPostMeasurementEndpoint requireJ1EndpointOracle(
            GeneratedBoardInstance instance, String label, String phase) {
        CircuitMeasurementEndpoint actual = instance.getBoard().getSimulationBindings()
            .getEndpoint("J1.1");
        require(actual instanceof CircuitPostMeasurementEndpoint,
            label + " " + phase + " retains J1.1 as an actual CircuitJS post");
        if (!(actual instanceof CircuitPostMeasurementEndpoint)) return null;
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint) actual;
        CircuitElm element = endpoint.getElement();
        require(element != null && element.getClass() == WireElm.class,
            label + " " + phase + " J1.1 expected persistent WireElm, actual " +
                (element == null ? "<null>" : element.getClass().getSimpleName()) +
                "/" + endpoint.getPostIndex());
        require(endpoint.getPostIndex() == 0,
            label + " " + phase + " J1.1 has the literal solver post index 0");
        require(containsIdentity(instance.getSimulationElements(), element),
            label + " " + phase + " J1.1 element belongs by identity to the canonical instance elements");
        require(GeneratedComponentConnectionBindings.sameEndpoint(endpoint,
                instance.getDeveloperBoardEndpointOracle().getEndpoint("J1.1")),
            label + " " + phase + " J1.1 agrees with the retained board endpoint");
        CircuitPostMeasurementEndpoint[] harness = instance.getConnectionBindings()
            .getConnectorHarness("J1");
        require(harness != null && harness.length == 2 &&
                !GeneratedComponentConnectionBindings.sameEndpoint(endpoint, harness[0]),
            label + " " + phase + " J1.1 is separated from the movable contact harness");
        CircuitMeasurementEndpoint returned = instance.getSimulationBindings().getEndpoint("J1.2");
        require(returned instanceof CircuitPostMeasurementEndpoint &&
                ((CircuitPostMeasurementEndpoint)returned).getElement().getClass() == GroundElm.class &&
                ((CircuitPostMeasurementEndpoint)returned).getPostIndex() == 0,
            label + " " + phase + " J1.2 retains the independent GroundElm/post0 endpoint");
        return endpoint;
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        if (target == null) return false;
        for (CircuitElm element : elements)
            if (element == target) return true;
        return false;
    }

    private static void verifyMissingStateRejection(GeneratedBoardInstance owner) {
        MaterializationSnapshot before = new MaterializationSnapshot(owner);
        Vector<CircuitElm> constructorElements = owner.getSimulationElements();
        boolean rejected = false;
        try {
            new GeneratedBoardInstance(owner.getBoard(), constructorElements,
                owner.getSeed(), owner.getCircuitFamilyId(),
                owner.getTopologyVariantId(), owner.getDescription(),
                owner.getComponentBindings(), owner.getExternalPowerBindings(),
                owner.getConnectionBindings(), owner.getBehaviorContract(),
                owner.getPcbLayout(), owner.getPhysicalSpecifications(),
                owner.getFaultBinding(), null, owner.getChallengeDefinition(),
                owner.getFamilyState(), owner.getPhysicalBoardRuntime(),
                owner.getTemporalBehavior(), owner.isDeveloperOnlyFaultRoute(),
                owner.getFaultCandidates(), owner.getDiagnosticSolvabilityContract(),
                owner.getDiagnosticProvider(), owner.getPhysicalAdmission());
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            rejected = message != null &&
                message.toLowerCase(java.util.Locale.ROOT).contains("operational") &&
                message.toLowerCase(java.util.Locale.ROOT).contains("state");
        }
        require(rejected,
            "constructor rejects missing operational states with valid actual owner inputs");
        before.requireUnchanged(owner, constructorElements);
    }

    private static final class MaterializationSnapshot {
        private final Vector<CircuitElm> elements;
        private final Vector<PhysicalBoardSlot> slots;
        private final Vector<PhysicalPart> parts;
        private final Vector<PhysicalPart> installedParts =
            new Vector<PhysicalPart>();
        private final Vector<PhysicalBoardRuntimeCapability> capabilities;
        private final Vector<WorkbenchPartsProvider> providers;
        private final Vector<GeneratedComponentConnectionBinding> connections;
        private final int[] elementCoordinates;

        MaterializationSnapshot(GeneratedBoardInstance owner) {
            elements = owner.getSimulationElements();
            slots = owner.getPhysicalBoardRuntime().getSlots();
            parts = owner.getPhysicalBoardRuntime().getPhysicalParts();
            capabilities = owner.getPhysicalBoardRuntime().getCapabilities();
            providers = owner.getPhysicalBoardRuntime().getWorkbenchPartsProviders();
            connections = owner.getConnectionBindings().getAll();
            for (PhysicalBoardSlot slot : slots)
                installedParts.add(slot.getInstalledPart());
            elementCoordinates = coordinates(elements);
        }

        void requireUnchanged(GeneratedBoardInstance owner,
                Vector<CircuitElm> constructorElements) {
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            require(sameIdentities(elements, constructorElements),
                "rejected construction leaves its actual CircuitJS element vector unchanged");
            require(sameIdentities(slots, runtime.getSlots()) &&
                    sameIdentities(parts, runtime.getPhysicalParts()) &&
                    sameIdentities(capabilities, runtime.getCapabilities()) &&
                    sameIdentities(providers, runtime.getWorkbenchPartsProviders()),
                "rejected construction leaves physical runtime owners unchanged");
            require(sameIdentities(connections,
                    owner.getConnectionBindings().getAll()),
                "rejected construction leaves physical connection bindings unchanged");
            require(sameCoordinates(elementCoordinates, coordinates(constructorElements)),
                "rejected construction leaves actual element geometry unchanged");
            Vector<PhysicalBoardSlot> afterSlots = runtime.getSlots();
            require(afterSlots.size() == installedParts.size(),
                "rejected construction leaves the slot census unchanged");
            for (int index = 0; index < afterSlots.size(); index++)
                require(afterSlots.get(index).getInstalledPart() ==
                        installedParts.get(index),
                    "rejected construction leaves installed part identity unchanged");
        }
    }

    private static int[] coordinates(Vector<CircuitElm> elements) {
        int[] result = new int[elements.size() * 4];
        for (int index = 0; index < elements.size(); index++) {
            CircuitElm element = elements.get(index);
            result[index * 4] = element.x;
            result[index * 4 + 1] = element.y;
            result[index * 4 + 2] = element.x2;
            result[index * 4 + 3] = element.y2;
        }
        return result;
    }

    private static boolean sameCoordinates(int[] first, int[] second) {
        if (first.length != second.length) return false;
        for (int index = 0; index < first.length; index++)
            if (first[index] != second[index]) return false;
        return true;
    }

    private static boolean sameIdentities(Vector<?> first, Vector<?> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++)
            if (first.get(index) != second.get(index)) return false;
        return true;
    }

    private static void dispose(GeneratedBoardInstance instance) {
        if (instance == null) return;
        instance.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : instance.getSimulationElements()) element.delete();
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
