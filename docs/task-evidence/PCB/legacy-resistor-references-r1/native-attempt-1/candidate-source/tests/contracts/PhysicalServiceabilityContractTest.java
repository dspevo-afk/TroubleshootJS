package com.lushprojects.circuitjs1.client;

/** Construction/identity oracle. Actual electrical consequences have a separate compiled solver gate. */
public final class PhysicalServiceabilityContractTest {
    private static int assertions;
    public static void main(String[] args) {
        CirSim sim = new CirSim(); sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        verifyNormalResistorReferenceFactory();
        for (String family : PlayerFamilyCatalog.registeredFamilies()) {
            // Q30 seed 0 is a retained route rejection; seed 13 is a qualified accepted route.
            GeneratedBoardInstance instance = construct(family,
                Rb30Plan.FAMILY_ID.equals(family) ? 13L : 0L);
            instance.requireNormalPhysicalAdmission();
            verifyNormalResistorReferences(instance);
            PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
            for (String id : instance.getBoard().getComponentIds())
                if (runtime.getWorkbenchPartsProvider(id) == null)
                    System.out.println("SERVICE_MISSING " + family + "/" + id +
                        " part=" + runtime.getInstalledPart(id).getClass().getName());
            check(runtime.getWorkbenchPartsProviders().size() == instance.getBoard().getComponentIds().size(),
                family + " every physical position has a real catalog/service owner");
            for (String id : instance.getBoard().getComponentIds()) {
                PhysicalPart<?> part = runtime.getInstalledPart(id);
                PhysicalBoardInstallationProvider.Scoped capability = runtime.getScopedMutationCapability(id);
                check(capability != null && capability.getMutationSlot().getInstalledPart() == part,
                    family + "/" + id + " exact service slot");
                check(runtime.getInventoryIdForPart(part.getId()) != null, id + " original inventory provenance");
                WorkbenchOperation inspect = WorkbenchOperation.forPart(WorkbenchOperation.INSPECT_LOOSE, part);
                check(WorkbenchCapabilityDiscovery.find(part, inspect, runtime.getWorkbenchCapabilityRegistry()) != null,
                    family + "/" + id + " intrinsic loose inspection is available after removal");
                if (part instanceof PhysicalServicePart) check(part.getRenderMetadata().getLooseProbeProvider() != null,
                    family + "/" + id + " service part declares real loose-terminal projection");
                WorkbenchPartsProvider catalog = runtime.getWorkbenchPartsProvider(id);
                check(catalog != null && !catalog.getCatalogEntries().isEmpty(), family + "/" + id + " replacement available");
                check(instance.getConnectionBindings().getForComponent(id).size() == part.getTerminalCount(), id + " separable terminals");
                for (String padId : instance.getBoard().getComponent(id).getPadIds()) {
                    GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().get(id, padId);
                    check(GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(),
                        capability.getMutationSlot().getExpectedEndpoint(part, instance.getBoard().getPad(padId))), id + " actual terminal");
                    CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint)binding.getBoardEndpoint();
                    CircuitPostMeasurementEndpoint p = (CircuitPostMeasurementEndpoint)binding.getComponentEndpoint();
                    check(!b.getElement().getPost(b.getPostIndex()).equals(p.getElement().getPost(p.getPostIndex())), id + " no direct copper bypass");
                }
            }
            verifyLiftedRenderEnvelopes(sim, instance);
            System.out.println("SERVICE_COVERAGE " + family + " parts=" + runtime.getSlots().size());
        }
        System.out.println("PASS: physical serviceability contracts assertions=" + assertions);
    }
    private static void verifyNormalResistorReferenceFactory() {
        PhysicalPackage physical = PhysicalPackages.AXIAL_RESISTOR;
        String type = "FACTORY_TEST_RESISTOR";
        TroubleshootBoard freshOne = new TroubleshootBoard("REFERENCE_FACTORY_ONE");
        TroubleshootBoard freshTwo = new TroubleshootBoard("REFERENCE_FACTORY_TWO");
        BoardComponent first = NormalResistorReferences.create(freshOne, "R_TARGET", type, physical);
        BoardComponent independent = NormalResistorReferences.create(freshTwo, "R_TARGET", type, physical);
        check("R1".equals(first.getDisplayName()) && "R1".equals(independent.getDisplayName()),
            "independent boards start their resistor references at R1");
        checkFactoryResult(first, "R_TARGET", type, physical, "fresh factory result");
        checkFactoryResult(independent, "R_TARGET", type, physical, "independent factory result");
        check(freshOne.getComponentIds().isEmpty() && freshTwo.getComponentIds().isEmpty(),
            "factory creation does not add components to either board");

        TroubleshootBoard ascending = ordinalReferenceBoard(false);
        TroubleshootBoard descending = ordinalReferenceBoard(true);
        int ascendingCount = ascending.getComponentIds().size();
        int descendingCount = descending.getComponentIds().size();
        BoardComponent gapAscending = NormalResistorReferences.create(ascending, "R_TARGET", type, physical);
        BoardComponent gapDescending = NormalResistorReferences.create(descending, "R_TARGET", type, physical);
        check("R2".equals(gapAscending.getDisplayName()) && "R2".equals(gapDescending.getDisplayName()),
            "R1 and R3 leave R2 available regardless of board insertion order");
        check(ascending.getComponentIds().size() == ascendingCount &&
                descending.getComponentIds().size() == descendingCount &&
                ascending.getComponent("R_TARGET") == null && descending.getComponent("R_TARGET") == null,
            "a successful factory call leaves occupied boards unchanged");
        ascending.addComponent(gapAscending);
        BoardComponent afterCallerAdd = NormalResistorReferences.create(ascending, "R_NEXT", type, physical);
        check("R4".equals(afterCallerAdd.getDisplayName()),
            "the next declaration observes a caller-added R2 and selects R4");
        check(ascending.getComponentIds().size() == ascendingCount + 1 &&
                ascending.getComponent("R_NEXT") == null,
            "only the caller's explicit add changes the board");
        BoardComponent semantic = new BoardComponent("RLOAD", "RESISTOR", physical);
        check("RLOAD".equals(semantic.getDisplayName()) &&
                semantic.getReferenceLabelCharacters() == "RLOAD".length(),
            "the existing default constructor retains semantic names and reservation width");

        TroubleshootBoard invalid = new TroubleshootBoard("REFERENCE_FACTORY_INVALID");
        expectFactoryRejection(null, "R_VALID", type, physical, "null board");
        expectFactoryRejection(invalid, null, type, physical, "null component ID");
        expectFactoryRejection(invalid, "", type, physical, "empty component ID");
        expectFactoryRejection(invalid, " ", type, physical, "blank component ID");
        expectFactoryRejection(invalid, "R_VALID", null, physical, "null component type");
        expectFactoryRejection(invalid, "R_VALID", "", physical, "empty component type");
        expectFactoryRejection(invalid, "R_VALID", " ", physical, "blank component type");
        expectFactoryRejection(invalid, "R_VALID", type, null, "null physical package");
        check(invalid.getComponentIds().isEmpty(), "invalid factory calls leave the board unchanged");

        TroubleshootBoard insufficientReservation = ordinalReferenceBoard(false);
        int beforeCapacityFailure = insufficientReservation.getComponentIds().size();
        expectFactoryRejection(insufficientReservation, "X", type, physical,
            "display name exceeding the original ID reservation");
        check(insufficientReservation.getComponentIds().size() == beforeCapacityFailure &&
                insufficientReservation.getComponent("X") == null &&
                "R1".equals(insufficientReservation.getComponent("OWNER_R1").getDisplayName()) &&
                "R3".equals(insufficientReservation.getComponent("OWNER_R3").getDisplayName()),
            "capacity rejection is atomic and preserves occupied references");
    }
    private static TroubleshootBoard ordinalReferenceBoard(boolean reverseInsertion) {
        TroubleshootBoard board = new TroubleshootBoard(reverseInsertion ?
            "REFERENCE_FACTORY_REVERSED" : "REFERENCE_FACTORY_ORDERED");
        BoardComponent r1 = new BoardComponent("OWNER_R1", "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR, "R1");
        BoardComponent r3 = new BoardComponent("OWNER_R3", "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR, "R3");
        if (reverseInsertion) {
            board.addComponent(r3);
            board.addComponent(r1);
        } else {
            board.addComponent(r1);
            board.addComponent(r3);
        }
        return board;
    }
    private static void checkFactoryResult(BoardComponent component, String id, String type,
            PhysicalPackage physical, String context) {
        check(id.equals(component.getId()) && type.equals(component.getType()) &&
                component.getPhysicalPackage() == physical &&
                component.getReferenceLabelCharacters() == id.length(),
            context + " preserves original identity, package, and ID-length reservation");
    }
    private static void expectFactoryRejection(TroubleshootBoard board, String id, String type,
            PhysicalPackage physical, String context) {
        int before = board == null ? -1 : board.getComponentIds().size();
        boolean rejected = false;
        try {
            NormalResistorReferences.create(board, id, type, physical);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, context + " is rejected");
        if (board != null)
            check(board.getComponentIds().size() == before &&
                    (id == null || board.getComponent(id) == null),
                context + " rejection leaves the board unchanged");
    }
    private static void verifyNormalResistorReferences(GeneratedBoardInstance instance) {
        String family = instance.getCircuitFamilyId();
        String[][] expected;
        String[] serviceComponents;
        if (SensorControlGenerator.FAMILY_ID.equals(family)) {
            if (instance.getBoard().getComponent("RFB_HYST") == null) {
                expected = new String[][] {
                    { "RBIAS", "SENSOR_SOURCE_RESISTOR", "AXIAL_RESISTOR", "R1" },
                    { "RREF", "REFERENCE_HIGH_RESISTOR", "AXIAL_RESISTOR", "R2" },
                    { "RFB", "OUTPUT_SERIES_RESISTOR", "AXIAL_RESISTOR", "R3" },
                    { "RREF_LOW", "REFERENCE_RETURN_RESISTOR", "AXIAL_RESISTOR", "R4" }
                };
            } else {
                expected = new String[][] {
                    { "RBIAS", "SENSOR_SOURCE_RESISTOR", "AXIAL_RESISTOR", "R1" },
                    { "RREF", "REFERENCE_HIGH_RESISTOR", "AXIAL_RESISTOR", "R2" },
                    { "RFB", "OUTPUT_SERIES_RESISTOR", "AXIAL_RESISTOR", "R3" },
                    { "RREF_LOW", "REFERENCE_RETURN_RESISTOR", "AXIAL_RESISTOR", "R4" },
                    { "RFB_HYST", "HYSTERESIS_FEEDBACK_RESISTOR", "AXIAL_RESISTOR", "R5" }
                };
            }
            serviceComponents = instance.getBoard().getComponent("RFB_HYST") == null ?
                new String[] { "RBIAS", "RREF", "RFB", "RREF_LOW" } :
                new String[] { "RBIAS", "RREF", "RFB", "RREF_LOW", "RFB_HYST" };
        } else if (NmosLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            expected = new String[][] {
                { "RLOAD", "RESISTOR", "AXIAL_RESISTOR", "R1" },
                { "RPD", "RESISTOR", "AXIAL_RESISTOR", "R2" }
            };
            serviceComponents = new String[0];
        } else if (NpnLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            expected = new String[][] {
                { "RLOAD", "RESISTOR", "AXIAL_RESISTOR", "R1" },
                { "RB", "RESISTOR", "AXIAL_RESISTOR", "R2" },
                { "RPD", "RESISTOR", "AXIAL_RESISTOR", "R3" }
            };
            serviceComponents = new String[] { "RLOAD", "RB" };
        } else if (RelayOutputGenerator.FAMILY_ID.equals(family)) {
            expected = new String[][] {
                { "RDRIVE", "RESISTOR", "AXIAL_RESISTOR", "R1" },
                { "RPD", "RESISTOR", "AXIAL_RESISTOR", "R2" }
            };
            serviceComponents = new String[] { "RDRIVE" };
        } else if (Rb15Plan.FAMILY_ID.equals(family)) {
            if (instance.getBoard().getComponent("RBIAS") == null) {
                expected = new String[][] {
                    { "RBLEED", "RESISTOR", "AXIAL_RESISTOR", "R1" },
                    { "RIN", "RESISTOR", "AXIAL_RESISTOR", "R2" },
                    { "RDRIVE", "RESISTOR", "AXIAL_RESISTOR", "R3" },
                    { "RPD", "RESISTOR", "AXIAL_RESISTOR", "R4" },
                    { "RLED", "RESISTOR", "AXIAL_RESISTOR", "R5" }
                };
            } else {
                expected = new String[][] {
                    { "RBLEED", "RESISTOR", "AXIAL_RESISTOR", "R1" },
                    { "RIN", "RESISTOR", "AXIAL_RESISTOR", "R2" },
                    { "RBIAS", "RESISTOR", "AXIAL_RESISTOR", "R3" },
                    { "RDRIVE", "RESISTOR", "AXIAL_RESISTOR", "R4" },
                    { "RPD", "RESISTOR", "AXIAL_RESISTOR", "R5" },
                    { "RLED", "RESISTOR", "AXIAL_RESISTOR", "R6" }
                };
            }
            serviceComponents = new String[] { "RDRIVE" };
        } else {
            return;
        }

        TroubleshootBoard board = instance.getBoard();
        PcbBoardLayout layout = instance.getPcbLayout();
        java.util.HashSet<String> expectedIds = new java.util.HashSet<String>();
        java.util.HashSet<String> seenResistors = new java.util.HashSet<String>();
        java.util.HashSet<String> publicNames = new java.util.HashSet<String>();
        for (String[] row : expected) expectedIds.add(row[0]);
        int resistorCount = 0;
        int componentLabelCount = 0;
        for (String id : board.getComponentIds()) {
            BoardComponent component = board.getComponent(id);
            boolean resistor = component.getType().endsWith("RESISTOR");
            if (resistor) {
                resistorCount++;
                int expectedIndex = findReference(expected, id);
                check(expectedIndex >= 0, family + "/" + id + " is in the literal resistor inventory");
                if (expectedIndex >= 0) {
                    String[] row = expected[expectedIndex];
                    check(row[0].equals(component.getId()) && row[1].equals(component.getType()) &&
                            row[2].equals(component.getPhysicalPackage().getId()) &&
                            row[3].equals(component.getDisplayName()) &&
                            component.getReferenceLabelCharacters() == id.length(),
                        family + "/" + id + " retains identity/package/reservation with its literal reference");
                    seenResistors.add(id);
                }
            } else {
                check(id.equals(component.getDisplayName()) &&
                        component.getReferenceLabelCharacters() == id.length(),
                    family + "/" + id + " retains its original nonresistor reference and reservation");
            }
            check(publicNames.add(component.getDisplayName()),
                family + "/" + id + " has a unique public component reference");
            PcbSilkscreenLabel label = layout.getSilkscreenLabel("component:" + id);
            check(label != null, family + "/" + id + " has its actual component label in the layout");
            if (label != null) {
                Rectangle bounds = label.getBounds();
                check(component.getDisplayName().equals(label.getText()) && bounds != null &&
                        bounds.width > 0 && bounds.height > 0,
                    family + "/" + id + " label text and nonempty bounds come from the actual layout");
            }
        }
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels())
            if (label.getId().startsWith("component:")) componentLabelCount++;
        check(resistorCount == expected.length && seenResistors.size() == expected.length,
            family + " complete literal resistor inventory is present");
        check(componentLabelCount == board.getComponentIds().size(),
            family + " actual component-label count remains one per board component");
        for (String id : expectedIds)
            check(seenResistors.contains(id), family + "/" + id + " expected resistor is present");

        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        java.util.HashSet<String> seenServiceComponents = new java.util.HashSet<String>();
        int resistorCapabilities = 0;
        for (PhysicalBoardRuntimeCapability value : runtime.getCapabilities()) {
            if (!(value instanceof ReplaceableResistorBoardCapability)) continue;
            ReplaceableResistorBoardCapability capability = (ReplaceableResistorBoardCapability)value;
            String id = capability.getComponentId();
            String expectedLabel = SensorControlGenerator.FAMILY_ID.equals(family) ?
                board.getComponent(id).getDisplayName() + " service resistor" :
                board.getComponent(id).getDisplayName();
            String expectedCapabilityId = SensorControlGenerator.FAMILY_ID.equals(family) ?
                "SENSOR_REPLACEABLE_" + id :
                NpnLowSideSwitchGenerator.FAMILY_ID.equals(family) ?
                    "REPLACEABLE_RESISTOR_" + id : ReplaceableResistorBoardCapability.ID;
            check(id.equals(capability.getSlot().getComponentId()) &&
                    id.equals(capability.getMutationSlot().getComponentId()) &&
                    expectedCapabilityId.equals(capability.getCapabilityId()),
                family + "/" + id + " retains its primary capability and slot identity");
            check(expectedLabel.equals(capability.getPlayerComponentLabel()),
                family + "/" + id + " service caption matches its public resistor reference");
            check(contains(serviceComponents, id) && seenServiceComponents.add(id),
                family + "/" + id + " is an expected unique primary resistor service position");
            resistorCapabilities++;
        }
        check(resistorCapabilities == serviceComponents.length &&
                seenServiceComponents.size() == serviceComponents.length,
            family + " primary resistor service positions remain unchanged");
        for (String id : serviceComponents)
            check(seenServiceComponents.contains(id), family + "/" + id + " retains resistor service ownership");
    }
    private static int findReference(String[][] expected, String id) {
        for (int index = 0; index < expected.length; index++)
            if (expected[index][0].equals(id)) return index;
        return -1;
    }
    private static boolean contains(String[] values, String value) {
        for (String candidate : values)
            if (candidate.equals(value)) return true;
        return false;
    }
    private static GeneratedBoardInstance construct(String family, long seed) {
        // Held families remain private qualification cases, never public difficulty launches.
        GenerationRequest request = PlayerFamilyCatalog.isNormalPlayerEnabled(family)
            ? new PlayerLaunchRequest(family, Long.toString(seed),
                PlayerFamilyCatalog.candidateProfile(family).name()).generation()
            : GenerationRequest.forFamilyQualification(family, seed);
        GenerationRequest.Prepared prepared = request.resolve(new GenerationRequest.PlanCache());
        if (request.getExecutionPolicy() != GenerationExecutionPolicy.NORMAL_MEDIUM &&
                !request.isPrivateDiagnosticQualification())
            return prepared.construct().instance;
        GenerationRequest.ConstructionSession session = prepared.beginConstruction();
        int steps = 0;
        while (!session.advance()) {
            if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError(family + " route exceeded the unchanged shared step bound");
        }
        return session.result().instance;
    }
    private static void verifyLiftedRenderEnvelopes(CirSim sim, GeneratedBoardInstance instance) {
        BoardModificationController modifications = new BoardModificationController(sim, instance);
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(instance, modifications, instance.getPcbLayout());
        PhysicalPartRenderRegistry registry = StandardPhysicalPartRenderProviders.createRegistry();
        for (String id : instance.getBoard().getComponentIds()) {
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(id);
            java.util.HashMap<String, Boolean> states = modifications.captureConnectionStates(id);
            for (String pad : instance.getBoard().getComponent(id).getPadIds()) {
                states.put(pad, Boolean.FALSE); modifications.restoreConnectionStatesForMutation(states);
                PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer,
                    instance.getPcbLayout().getComponent(id), part, part.getPackage(), -1, false);
                PhysicalPartRenderGeometry geometry = registry.requireRenderer(part.getPackage(), part).getInstalledGeometry(context);
                check(geometry != null && geometry.getTerminals().size() == part.getTerminalCount(),
                    instance.getCircuitFamilyId() + "/" + id + "/" + pad + " lifted renderer envelope is complete");
                states.put(pad, Boolean.TRUE); modifications.restoreConnectionStatesForMutation(states);
            }
        }
    }
    private static void check(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
