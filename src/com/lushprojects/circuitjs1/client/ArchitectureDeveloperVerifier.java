package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Developer-only contract checks for the Task 34(A) architecture seams. */
final class ArchitectureDeveloperVerifier {
    private ArchitectureDeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("Architecture verification requires a generated board");
        verifyFootprintCanaries(sim);
        verifyInstrumentModes(sim);
        verifyWorkbenchCapabilityCanary(sim);
        WorkbenchCapabilityDeveloperVerifier.verifyRegisteredProviders(sim);
        verifyPhysicalParts(sim);
        verifyRuntimePhysicalOwnership(sim.getGeneratedBoardInstance());
        verifyPhysicalDefinitionProviders(sim.getGeneratedBoardInstance());
        PhysicalFoundationDeveloperVerifier.verify(sim);
        PhysicalPartRenderDeveloperVerifier.verify(sim);
        // This is intentionally part of the architecture route: migrating the
        // provider boundary must preserve the established legacy geometry.
        PcbLayoutDeveloperVerifier.verify(sim);
    }

    private static void verifyFootprintCanaries(CirSim sim) {
        SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry());
        for (int pinCount = 3; pinCount <= 6; pinCount++) {
            boolean internalConnectivityCanary = pinCount == 3 || pinCount == 4;
            TroubleshootBoard board = internalConnectivityCanary ?
                createInternalConnectivityCanary(pinCount) : createMultiTerminalCanary(pinCount);
            long canarySeed = 3400 + pinCount * 4;
            PcbBoardLayout first = generator.generate(board, canarySeed);
            PcbBoardLayout second = generator.generate(board, canarySeed);
            first.validateGeometry(board);
            second.validateGeometry(board);
            require(first.geometryFingerprint().equals(second.geometryFingerprint()),
                "canary layout is not deterministic: " + pinCount);
            BoardComponent multiTerminal = board.getComponent("U" + pinCount);
            int expectedTraceCount = internalConnectivityCanary ? pinCount - 1 : pinCount;
            require(first.getComponents().size() == board.getComponentIds().size() &&
                    first.getPads().size() == board.getPadIds().size() &&
                    first.getTraces().size() == expectedTraceCount,
                "canary geometry omitted logical components or pads: " + pinCount);
            Vector<String> terminalIds = multiTerminal.getPhysicalPackage().getTerminalIds();
            for (int terminal = 0; terminal < terminalIds.size(); terminal++) {
                String padId = multiTerminal.getId() + "." + terminalIds.get(terminal);
                BoardPad boardPad = board.getPad(padId);
                require(boardPad != null && terminalIds.get(terminal).equals(
                        boardPad.getTerminalId()) && first.getPad(padId) != null,
                    "canary terminal-to-footprint mapping failed: " + padId);
            }
            if (internalConnectivityCanary)
                verifyInternalConnectivityRouting(first, board, pinCount);
            else
                verifyCanaryTraceOwnership(first, board, pinCount);

            require(first.getComponent(multiTerminal.getId()) != null,
                "canary package placement is missing: " + pinCount);
        }
    }

    private static void verifyInternalConnectivityRouting(PcbBoardLayout layout,
            TroubleshootBoard board, int pinCount) {
        String componentId = "U" + pinCount;
        BoardComponent component = board.getComponent(componentId);
        PhysicalPackage physicalPackage = component.getPhysicalPackage();
        require(physicalPackage.isInternallyConnected("1", "2"),
            "canary package lost declared internal pair: " + pinCount);
        require(!physicalPackage.isInternallyConnected("2", "3"),
            "canary package connected an undeclared pair: " + pinCount);

        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        require(!hasTopologyLink(topology, componentId, componentId + ".1",
                componentId + ".2", "Z_CANARY_POSITIVE"),
            "placement graph retained a declared internal package link: " + pinCount);
        if (pinCount == 4)
            require(hasTopologyLink(topology, componentId, componentId + ".3",
                    componentId + ".4", "Z_CANARY_NEGATIVE"),
                "placement graph omitted an undeclared package link");

        HashMap<String, Integer> endpointCounts = new HashMap<String, Integer>();
        int positiveTraces = 0;
        int negativeTraces = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardPad start = board.getPad(trace.getStartPadId());
            BoardPad end = board.getPad(trace.getEndPadId());
            require(start != null && end != null && trace.getNetId().equals(start.getNetId()) &&
                    trace.getNetId().equals(end.getNetId()),
                "internal-connectivity canary trace escaped its net: " + trace.getNetId());
            require(("PWR_IN".equals(start.getComponentId()) &&
                    componentId.equals(end.getComponentId())) ||
                    (componentId.equals(start.getComponentId()) &&
                    "PWR_IN".equals(end.getComponentId())),
                "internal-connectivity canary trace has unexpected endpoints: " +
                    trace.getNetId());
            if ("Z_CANARY_POSITIVE".equals(trace.getNetId()))
                positiveTraces++;
            else if ("Z_CANARY_NEGATIVE".equals(trace.getNetId()))
                negativeTraces++;
            else
                throw new IllegalStateException("Unexpected internal-connectivity canary net: " +
                    trace.getNetId());
            increment(endpointCounts, trace.getStartPadId());
            increment(endpointCounts, trace.getEndPadId());
        }
        require(positiveTraces == 1 && negativeTraces == pinCount - 2 &&
                layout.getTraces().size() == pinCount - 1,
            "internal-connectivity canary routing decision changed: " + pinCount);
        require(Integer.valueOf(1).equals(endpointCounts.get(componentId + ".1")) &&
                endpointCounts.get(componentId + ".2") == null,
            "declared internal package pad was not the sole omitted copper endpoint: " + pinCount);
        for (int terminal = 3; terminal <= pinCount; terminal++)
            require(Integer.valueOf(1).equals(endpointCounts.get(componentId + "." + terminal)),
                "undeclared package pair lost required copper: " + componentId + "." + terminal);
    }

    private static boolean hasTopologyLink(TopologyPlacementGraph topology, String componentId,
            String padId, String otherPadId, String netId) {
        for (TopologyPlacementGraph.PadLink link : topology.getLinksFor(componentId))
            if (padId.equals(link.getPadId()) && otherPadId.equals(link.getOtherPadId()) &&
                    netId.equals(link.getNetId()))
                return true;
        return false;
    }

    private static void verifyCanaryTraceOwnership(PcbBoardLayout layout,
            TroubleshootBoard board, int pinCount) {
        String multiTerminalId = "U" + pinCount;
        HashMap<String, Integer> endpointCounts = new HashMap<String, Integer>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            require(trace.getNetId().startsWith("Z_CANARY_NET_"),
                "canary trace has an unexpected net identity: " + trace.getNetId());
            BoardPad start = board.getPad(trace.getStartPadId());
            BoardPad end = board.getPad(trace.getEndPadId());
            require(start != null && end != null && trace.getNetId().equals(start.getNetId()) &&
                    trace.getNetId().equals(end.getNetId()),
                "canary trace endpoints do not belong to its logical net: " +
                    trace.getNetId());
            require(start.getTerminalId().equals(end.getTerminalId()) &&
                    (("PWR_IN".equals(start.getComponentId()) &&
                        multiTerminalId.equals(end.getComponentId())) ||
                    (multiTerminalId.equals(start.getComponentId()) &&
                        "PWR_IN".equals(end.getComponentId()))),
                "canary trace does not connect the matching external/package pin: " +
                    trace.getNetId());
            increment(endpointCounts, trace.getStartPadId());
            increment(endpointCounts, trace.getEndPadId());
        }
        require(layout.getTraces().size() == pinCount,
            "canary trace count does not match pin count: " + pinCount);
        for (String padId : board.getPadIds())
            require(Integer.valueOf(1).equals(endpointCounts.get(padId)),
                "canary pad does not have exactly one owned trace endpoint: " + padId);
    }

    private static void increment(HashMap<String, Integer> counts, String id) {
        Integer count = counts.get(id);
        counts.put(id, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }

    private static TroubleshootBoard createMultiTerminalCanary(int pinCount) {
        TroubleshootBoard board = new TroubleshootBoard("DEV_MULTI_TERMINAL_" + pinCount);
        for (int index = 1; index <= pinCount; index++)
            board.addNet(new BoardNet("Z_CANARY_NET_" + index));
        board.addComponent(new BoardComponent("PWR_IN", "CONNECTOR",
            PhysicalPackages.developerConnectorForCount(pinCount)));
        BoardComponent multiTerminal = new BoardComponent("U" + pinCount,
            "DEV_CANARY_" + pinCount);
        board.addComponent(multiTerminal);
        for (int index = 1; index <= pinCount; index++)
            board.addPad(new BoardPad("PWR_IN." + index, "PWR_IN", String.valueOf(index),
                "Z_CANARY_NET_" + index));
        for (int index = 1; index <= pinCount; index++)
            board.addPad(new BoardPad(multiTerminal.getId() + "." + index,
                multiTerminal.getId(), String.valueOf(index), "Z_CANARY_NET_" + index));
        board.validate();
        return board;
    }

    private static TroubleshootBoard createInternalConnectivityCanary(int pinCount) {
        TroubleshootBoard board = new TroubleshootBoard(
            "DEV_INTERNAL_CONNECTIVITY_" + pinCount);
        board.addNet(new BoardNet("Z_CANARY_POSITIVE"));
        board.addNet(new BoardNet("Z_CANARY_NEGATIVE"));
        board.addComponent(new BoardComponent("PWR_IN", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        PhysicalPackage physicalPackage = pinCount == 3 ? PhysicalPackages.DEV_CANARY_3 :
            PhysicalPackages.DEV_CANARY_4;
        BoardComponent multiTerminal = new BoardComponent("U" + pinCount,
            "DEV_CANARY_" + pinCount, physicalPackage);
        board.addComponent(multiTerminal);
        board.addPad(new BoardPad("PWR_IN.1", "PWR_IN", "1", "Z_CANARY_POSITIVE"));
        board.addPad(new BoardPad("PWR_IN.2", "PWR_IN", "2", "Z_CANARY_NEGATIVE"));
        board.addPad(new BoardPad(multiTerminal.getId() + ".1", multiTerminal.getId(), "1",
            "Z_CANARY_POSITIVE"));
        board.addPad(new BoardPad(multiTerminal.getId() + ".2", multiTerminal.getId(), "2",
            "Z_CANARY_POSITIVE"));
        for (int terminal = 3; terminal <= pinCount; terminal++)
            board.addPad(new BoardPad(multiTerminal.getId() + "." + terminal,
                multiTerminal.getId(), String.valueOf(terminal), "Z_CANARY_NEGATIVE"));
        board.validate();
        return board;
    }

    private static void verifyInstrumentModes(CirSim sim) {
        InstrumentModeRegistry registry = StandardInstrumentModeProviders.createRegistry();
        require(registry.get("NONE").getDeveloperCode() == 0,
            "instrument NONE code changed");
        require(registry.get("DC_VOLTAGE").getProbeRequirements().requiresTwoProbes() &&
                registry.get("RESISTANCE").getPowerPolicy() == InstrumentPowerPolicy.UNPOWERED_ONLY &&
                registry.get("CONTINUITY").getPowerPolicy() == InstrumentPowerPolicy.UNPOWERED_ONLY &&
                registry.get("DIODE").getProbeRequirements().getRedPolarity() ==
                    InstrumentProbePolarity.POSITIVE &&
                registry.get("DIODE").getProbeRequirements().getBlackPolarity() ==
                    InstrumentProbePolarity.NEGATIVE,
            "instrument mode strategy metadata is incomplete");

        final int[] registration = new int[] { 0 };
        final int[] selection = new int[] { 0 };
        final int[] probe = new int[] { 0 };
        final int[] refresh = new int[] { 0 };
        final int[] measure = new int[] { 0 };
        final int[] display = new int[] { 0 };
        final int[] exit = new int[] { 0 };
        InstrumentModeProvider fake = new AbstractInstrumentModeStrategy(
                "DEVELOPER_INSTRUMENT_CANARY", "CANARY", "---", 99,
                new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                    InstrumentProbePolarity.NEGATIVE),
                InstrumentPowerPolicy.POWERED_OR_UNPOWERED, false) {
            public void refresh(InstrumentController controller) { refresh[0]++; }
            public void measure(InstrumentController controller) {
                measure[0]++;
                probe[0]++;
            }
            public void display(InstrumentController controller) {
                display[0]++;
                controller.setDeveloperReadingForVerification("CANARY");
            }
            public void deactivate(InstrumentController controller) { exit[0]++; }
        };
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.instrumentController.registerDeveloperInstrumentModeForVerification(fake);
        registration[0]++;
        sim.instrumentController.activateDeveloperInstrumentModeForVerification(
            "DEVELOPER_INSTRUMENT_CANARY");
        selection[0]++;
        Vector<String> padIds = sim.getGeneratedBoardInstance().getBoard().getPadIds();
        require(!padIds.isEmpty(), "instrument canary has no probeable pad");
        ProbeTarget target = new BoardPadProbeTarget(sim, sim.getGeneratedBoardInstance(),
            padIds.firstElement(), sim.pcbWorkbenchController.getRenderer());
        sim.instrumentController.handlePointerInput(
            com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT, target);
        String canaryReading = sim.instrumentController.getReadingForDeveloperVerification();
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(registration[0] == 1 && selection[0] == 1 && probe[0] == 1 &&
                refresh[0] == 1 && measure[0] == 1 && display[0] == 1 && exit[0] == 1 &&
                "CANARY".equals(canaryReading) &&
                sim.instrumentController.getActiveModeForDeveloperVerification() == 0,
                "instrument strategy canary did not delegate each operation exactly once");

        final int[] visibleActivation = new int[] { 0 };
        final int[] visibleRefresh = new int[] { 0 };
        final int[] visibleMeasure = new int[] { 0 };
        final int[] visibleDisplay = new int[] { 0 };
        final int[] visibleExit = new int[] { 0 };
        InstrumentModeProvider visibleProvider = new AbstractInstrumentModeStrategy(
                "CANARY_VISIBLE_INSTRUMENT", "CANARY VISIBLE", "VISIBLE", 101,
                new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                    InstrumentProbePolarity.NEGATIVE),
                InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true) {
            public void activate(InstrumentController controller) { visibleActivation[0]++; }
            public void refresh(InstrumentController controller) { visibleRefresh[0]++; }
            public void measure(InstrumentController controller) { visibleMeasure[0]++; }
            public void display(InstrumentController controller) {
                visibleDisplay[0]++;
                controller.setInstrumentDisplayForStrategy("VISIBLE");
            }
            public void deactivate(InstrumentController controller) { visibleExit[0]++; }
        };
        sim.instrumentController.registerInstrumentModeProvider(visibleProvider);
        require(sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "CANARY_VISIBLE_INSTRUMENT") &&
                sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "DC_VOLTAGE") &&
                sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "RESISTANCE") &&
                sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "CONTINUITY") &&
                sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "DIODE") &&
                registry.getPlayerVisibleModes().size() == 4,
            "production visible instrument provider was not registered generically");
        sim.instrumentController.clearTargets();
        sim.instrumentController.clickPlayerVisibleModeButtonForDeveloperVerification(
            "CANARY_VISIBLE_INSTRUMENT");
        selection[0]++;
        sim.instrumentController.handlePointerInput(
            com.google.gwt.dom.client.NativeEvent.BUTTON_LEFT, target);
        String visibleReading = sim.instrumentController.getReadingForDeveloperVerification();
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(visibleActivation[0] == 1 && visibleRefresh[0] >= 2 &&
                visibleMeasure[0] >= 2 && visibleDisplay[0] >= 2 && visibleExit[0] == 1 &&
                "VISIBLE".equals(visibleReading),
            "production visible instrument provider did not execute through controller: " +
                visibleActivation[0] + "/" + visibleRefresh[0] + "/" + visibleMeasure[0] +
                "/" + visibleDisplay[0] + "/" + visibleExit[0] + "/" + visibleReading);

        boolean duplicateRejected = false;
        try {
            sim.instrumentController.registerInstrumentModeProvider(visibleProvider);
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        require(duplicateRejected &&
                sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "CANARY_VISIBLE_INSTRUMENT"),
            "production duplicate instrument provider was not rejected safely");

        InstrumentModeProvider developerOnlyProvider = new AbstractInstrumentModeStrategy(
                "DEVELOPER_PRODUCTION_INSTRUMENT_CANARY", "Developer", "---", 102,
                new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                    InstrumentProbePolarity.NEGATIVE),
                InstrumentPowerPolicy.POWERED_OR_UNPOWERED, false) { };
        boolean developerOnlyRejected = false;
        try {
            sim.instrumentController.registerInstrumentModeProvider(developerOnlyProvider);
        } catch (IllegalArgumentException expected) {
            developerOnlyRejected = true;
        }
        require(developerOnlyRejected &&
                !sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                    "DEVELOPER_PRODUCTION_INSTRUMENT_CANARY"),
            "production registration accepted a developer-only instrument provider");

        boolean invalidRejected = false;
        try {
            sim.instrumentController.registerInstrumentModeProvider(null);
        } catch (IllegalArgumentException expected) {
            invalidRejected = true;
        }
        require(invalidRejected,
            "production registration accepted an invalid instrument provider");

        InstrumentModeStrategy invalidVisibleProvider = new AbstractInstrumentModeStrategy(
                "DEVELOPER_INVALID_INSTRUMENT_CANARY", "Invalid", "---", 100,
                new InstrumentProbeRequirements(true, InstrumentProbePolarity.POSITIVE,
                    InstrumentProbePolarity.NEGATIVE),
                InstrumentPowerPolicy.POWERED_OR_UNPOWERED, true) { };
        boolean rejected = false;
        try {
            registry.registerDeveloperOnly(invalidVisibleProvider);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "instrument registry accepted a player-visible developer provider");
    }

    private static void verifyWorkbenchCapabilityCanary(CirSim sim) {
        Vector<PhysicalPart> parts = sim.getGeneratedBoardInstance().getPhysicalBoardRuntime()
            .getPhysicalParts();
        require(!parts.isEmpty(), "capability canary has no physical part");
        final PhysicalPart part = parts.firstElement();
        final WorkbenchOperation operation = WorkbenchOperation.forPart(
            "DEVELOPER_CAPABILITY_CANARY", part);
        final int[] availability = new int[] { 0 };
        final int[] invocation = new int[] { 0 };
        final int[] contextAvailability = new int[] { 0 };
        final int[] contextDispatch = new int[] { 0 };
        WorkbenchCapabilityStrategy fake = new WorkbenchCapabilityStrategy() {
            private final WorkbenchCapabilityMetadata metadata =
                new WorkbenchCapabilityMetadata("DEVELOPER_CAPABILITY_CANARY", "Canary",
                    "DEVELOPER_CAPABILITY_CANARY");
            public WorkbenchCapabilityMetadata getMetadata() { return metadata; }
            public String getOperationLabel(WorkbenchOperation candidate) { return "Canary"; }
            public boolean supports(WorkbenchOperation candidate) {
                return operation == candidate;
            }
            public boolean isAvailable(WorkbenchOperation candidate,
                    WorkbenchCapabilityContext context) {
                availability[0]++;
                return supports(candidate) && context != null && context.isAvailable(candidate);
            }
            public boolean invoke(WorkbenchOperation candidate,
                    WorkbenchCapabilityContext context) {
                invocation[0]++;
                return supports(candidate) && context != null && context.dispatch(candidate);
            }
        };
        WorkbenchCapabilityRegistry registry = new WorkbenchCapabilityRegistry();
        registry.registerDeveloperOnly(fake);
        final WorkbenchCapabilityContext context = new WorkbenchCapabilityContext() {
            public boolean isAvailable(WorkbenchOperation candidate) {
                contextAvailability[0]++;
                return operation == candidate;
            }
            public boolean dispatch(WorkbenchOperation candidate) {
                contextDispatch[0]++;
                return operation == candidate;
            }
        };
        Vector<WorkbenchCapabilityStrategy> discovered =
            WorkbenchCapabilityDiscovery.discover(part, operation, registry);
        require(discovered.size() == 1 && discovered.firstElement() == fake,
            "developer capability was not discovered exactly once");
        require(fake.isAvailable(operation, context),
            "developer capability availability was not executable");
        require(fake.invoke(operation, context),
            "developer capability invocation was not executable");
        require(availability[0] == 1 && invocation[0] == 1 &&
                contextAvailability[0] == 1 && contextDispatch[0] == 1,
            "developer capability canary did not dispatch exactly once");
        verifyFutureComponentCapabilityMetadata();
    }

    private static void verifyFutureComponentCapabilityMetadata() {
        final WorkbenchOperation futureOperation = WorkbenchOperation.forComponent(
            "DEVELOPER_3PIN_INSTALL", "U3");
        WorkbenchCapabilityStrategy future = new WorkbenchCapabilityStrategy() {
            private final WorkbenchCapabilityMetadata metadata =
                new WorkbenchCapabilityMetadata("DEVELOPER_3PIN_COMPONENT",
                    "3-pin component workbench", "DEVELOPER_3PIN_INSTALL");
            public WorkbenchCapabilityMetadata getMetadata() { return metadata; }
            public String getOperationLabel(WorkbenchOperation operation) {
                return "Install 3-pin component";
            }
            public boolean supports(WorkbenchOperation operation) {
                return futureOperation.getId().equals(operation == null ? null : operation.getId()) &&
                    futureOperation.getComponentId().equals(operation.getComponentId());
            }
            public boolean isAvailable(WorkbenchOperation operation,
                    WorkbenchCapabilityContext context) {
                return supports(operation) && context != null && context.isAvailable(operation);
            }
            public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
                return isAvailable(operation, context) && context.dispatch(operation);
            }
        };
        WorkbenchCapabilityRegistry registry = new WorkbenchCapabilityRegistry();
        registry.registerDeveloperOnly(future);
        Vector<WorkbenchCapabilityStrategy> discovered =
            WorkbenchCapabilityDiscovery.discover(null, futureOperation, registry);
        require(discovered.size() == 1 && discovered.firstElement() == future &&
                "3-pin component workbench".equals(future.getMetadata().getDisplayName()) &&
                "Install 3-pin component".equals(future.getOperationLabel(futureOperation)),
            "future component capability metadata was not registry-discoverable");
    }

    private static void verifyPhysicalParts(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        Vector<PhysicalPart> parts = instance.getPhysicalBoardRuntime().getPhysicalParts();
        require(!parts.isEmpty(), "generated board has no physical parts");
        HashMap<String, PhysicalPart> identities = new HashMap<String, PhysicalPart>();
        for (PhysicalPart part : parts) {
            require(part != null && part.getId() != null && part.getId().length() > 0,
                "physical part identity is missing");
            require(identities.put(part.getId(), part) == null,
                "physical part identity is duplicated: " + part.getId());
            require(part.getPackage() != null && part.getSpecification() != null,
                "physical part metadata is incomplete: " + part.getId());
            require(part.getTerminalCount() == part.getPackage().getTerminalCount(),
                "physical package terminal count mismatch: " + part.getId());
            new CirSimTroubleshootSimulationFacade(sim).validateBacking(part);
            HashMap<CircuitMeasurementEndpoint, Boolean> endpoints =
                new HashMap<CircuitMeasurementEndpoint, Boolean>();
            for (int terminal = 0; terminal < part.getTerminalCount(); terminal++) {
                PhysicalPartTerminal first = part.getTerminal(terminal);
                require(first != null && first.getEndpoint() != null &&
                        first.getId().startsWith(part.getId() + "."),
                    "physical part terminal is not stable/probeable: " + part.getId());
                require(endpoints.put(first.getEndpoint(), Boolean.TRUE) == null,
                    "physical part terminals share an endpoint: " + part.getId());
            }
            if (part.getProvenance() == null ||
                    !PhysicalPartProvenance.FIXED_GENERATED.equals(part.getProvenance().getKind())) {
                require(!WorkbenchCapabilityDiscovery.discover(part).isEmpty(),
                    "workbench capability discovery returned no capabilities: " + part.getId());
                String componentId = part.getBoardSlot() == null ? null :
                    part.getBoardSlot().getComponentId();
                require(componentId != null,
                    "replaceable production part is not mounted: " + part.getId());
                WorkbenchOperation remove = WorkbenchOperation.forPart(
                    WorkbenchOperation.REMOVE, part);
                WorkbenchCapabilityStrategy removeCapability =
                    WorkbenchCapabilityDiscovery.find(part, remove,
                        instance.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry());
                require(removeCapability instanceof PhysicalSlotMutationProvider &&
                        removeCapability.getMetadata() != null &&
                        removeCapability.getOperationLabel(remove).length() > 0,
                    "workbench provider operation was not discovered: " + part.getId());
            } else {
                String componentId = part.getBoardSlot() == null ? null :
                    part.getBoardSlot().getComponentId();
                require(part.getCapabilities().isEmpty() && componentId != null,
                    "fixed production part exposes mutation capabilities: " + part.getId());
                WorkbenchOperation remove = WorkbenchOperation.forPart(
                    WorkbenchOperation.REMOVE, part);
                WorkbenchOperation lift = WorkbenchOperation.forPartLead(
                    WorkbenchOperation.LIFT_LEAD, part, componentId,
                    part.getBoardSlot().getPadIds().firstElement());
                require(WorkbenchCapabilityDiscovery.find(part, remove,
                        instance.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry()) == null &&
                    WorkbenchCapabilityDiscovery.find(part, lift,
                        instance.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry()) == null,
                    "fixed production part has a false mutation operation: " + part.getId());
            }
        }
    }

    private static void verifyRuntimePhysicalOwnership(GeneratedBoardInstance instance) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        for (PhysicalBoardSlot slot : runtime.getSlots()) {
            require(runtime.getSlot(slot.getComponentId()) == slot,
                "runtime slot identity changed: " + slot.getComponentId());
            PhysicalPart<?> installed = slot.getInstalledPart();
            if (installed != null)
                require(runtime.getPart(installed.getId()) == installed &&
                        installed.getBoardSlot() == slot && installed.isInstalled(),
                    "runtime does not own installed occupancy: " + slot.getComponentId());
        }
        for (WorkbenchPartsProvider partsProvider : runtime.getWorkbenchPartsProviders()) {
            String componentId = partsProvider.getComponentId();
            require(runtime.getWorkbenchPartsProvider(componentId) == partsProvider &&
                    runtime.getSlot(componentId) != null,
                "workbench provider does not adapt a runtime-owned slot: " + componentId);
            PhysicalSlotMutationProvider mutationProvider =
                runtime.getMutationProvider(componentId);
            require(mutationProvider != null && componentId.equals(
                    mutationProvider.getComponentId()),
                "workbench provider has no registered mutation provider: " + componentId);
            PhysicalPart<?> installed = runtime.getInstalledPart(componentId);
            if (installed != null)
                verifyRuntimeProviderPart(runtime, partsProvider, mutationProvider, installed);
            for (PhysicalPart<?> part : partsProvider.getLooseParts())
                verifyRuntimeProviderPart(runtime, partsProvider, mutationProvider, part);
        }
    }

    private static void verifyRuntimeProviderPart(PhysicalBoardRuntime runtime,
            WorkbenchPartsProvider partsProvider, PhysicalSlotMutationProvider mutationProvider,
            PhysicalPart<?> part) {
        require(runtime.getPart(part.getId()) == part && partsProvider.ownsPart(part.getId()) &&
                partsProvider.getPart(part.getId()) == part && mutationProvider.ownsPart(part.getId()) &&
                runtime.getWorkbenchPartsProviderForPart(part.getId()) == partsProvider,
            "provider view escaped runtime-owned physical identity: " + part.getId());
    }

    private static void verifyPhysicalDefinitionProviders(GeneratedBoardInstance instance) {
        require(StandardPhysicalDefinitionProviders.get("RESISTOR") ==
                StandardPhysicalDefinitionProviders.RESISTOR &&
                StandardPhysicalDefinitionProviders.get("DIODE") ==
                    StandardPhysicalDefinitionProviders.DIODE &&
                StandardPhysicalDefinitionProviders.get("LED") ==
                    StandardPhysicalDefinitionProviders.LED,
            "typed physical-definition providers are not registered");
        BoardPhysicalSpecifications definitions = instance.getPhysicalSpecifications();
        for (String componentId : definitions.getPhysicalComponentIds()) {
            BoardPhysicalDefinition definition = definitions.getPhysicalDefinition(componentId);
            require(definition != null && componentId.equals(definition.getComponentId()) &&
                    definition.getSpecification() == definitions.getSpecification(componentId) &&
                    definition.getNameplate() == definitions.getNameplate(componentId) &&
                    definition.getPhysicalPackage() == definitions.getPackage(componentId),
                "generic physical definition lost component identity: " + componentId);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
