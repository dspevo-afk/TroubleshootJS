package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Focused deterministic checks for the Task 37 player-facing route. */
final class NpnLowSideSwitchDeveloperVerifier {
    private NpnLowSideSwitchDeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady() &&
            NpnLowSideSwitchGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()),
            "NPN challenge did not become ready");
        require(instance.getBoard().getPad("Q1.B") != null &&
            instance.getBoard().getPad("Q1.C") != null &&
            instance.getBoard().getPad("Q1.E") != null,
            "NPN board did not expose stable B/C/E pads");
        CircuitElm qElement = instance.getComponentBindings().getSingleElement("Q1");
        require(qElement instanceof NTransistorElm && qElement.getPostCount() == 3,
            "NPN board is not backed by a real three-post transistor");
        PhysicalPart<?> qPart = instance.getPhysicalBoardRuntime().getInstalledPart("Q1");
        require(qPart instanceof PhysicalNpnPart && qPart.getTerminalCount() == 3,
            "NPN installed part identity or terminal count is not physical");
        PhysicalNpnPart npn = (PhysicalNpnPart) qPart;
        require("B".equals(npn.getTerminal(0).getTerminalName()) &&
            "C".equals(npn.getTerminal(1).getTerminalName()) &&
            "E".equals(npn.getTerminal(2).getTerminalName()),
            "NPN physical terminal order is not B/C/E");
        require(sameEndpoint(npn.getPublicTerminal(0), componentEndpoint(instance, "Q1.B")) &&
            sameEndpoint(npn.getPublicTerminal(1), componentEndpoint(instance, "Q1.C")) &&
            sameEndpoint(npn.getPublicTerminal(2), componentEndpoint(instance, "Q1.E")),
            "NPN pad-to-terminal mapping changed");
        verifyProviderFootprint(instance);
        verifyConnectorMarkings(sim, instance);
        verifyNpnGroundTree(instance);
        GeneratedFaultBinding binding = instance.getFaultBinding();
        require(binding != null && binding.isApplied(),
            "NPN selected fault is not applied");
        String faultTarget = instance.getChallengeDefinition().getFault().getTargetComponentId();
        if ("Q1".equals(faultTarget))
            require(npn.ownsGeneratedFault(binding),
                "NPN selected fault is not privately owned by original Q1");
        else {
            ReplaceableResistorBoardCapability faultCapability =
                ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), faultTarget);
            require(faultCapability != null && faultCapability.getSlot().getInstalledPart() != null &&
                faultCapability.getSlot().getInstalledPart().ownsGeneratedFault(binding),
                "NPN selected fault is not privately owned by original " + faultTarget);
        }
        require(!sim.activeMeasurementOverlay &&
            sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "NPN verifier entered with an invalid power or overlay state");
        verifySolvedFault(instance, challenge, sim);
        verifyDeterministicNameplateEnvelope();
        require(challenge.getScenario() != null && challenge.getScenario().isCompatible(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED),
            "NPN complaint is not backed by the observed solved behavior");
        verifyScenarioPresentation(instance, challenge);
        verifyScenarioCompatibilityPurity(sim, instance, challenge);
        verifyStableDcMeasurements(sim, instance, challenge);
        verifyHealthyReference(instance, challenge, sim);
        verifyStatePreservingChecks(instance, challenge, sim);
        verifyPhysicalRepairLifecycle(instance, challenge, sim);
        verifyCorrectRepairStatusPreservesState(instance, challenge);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "NPN repair lifecycle did not finish in a functional state");
        verifyDeterministicEnvelope();
        sim.setCircuitTitle("NPN low-side verification passed");
    }

    private static void verifyDeterministicEnvelope() {
        NpnLowSideSwitchGenerator generator = new NpnLowSideSwitchGenerator();
        GeneratedFaultType[] types = {
            GeneratedFaultType.TRANSISTOR_CE_OPEN, GeneratedFaultType.TRANSISTOR_CE_SHORT,
            GeneratedFaultType.BASE_RESISTOR_OPEN, GeneratedFaultType.LOAD_PATH_OPEN
        };
        for (int index = 0; index < types.length; index++) {
            GeneratedBoardInstance first = generator.generateForFaultVerification(index, types[index]);
            GeneratedBoardInstance second = generator.generateForFaultVerification(index, types[index]);
            require(first.getFaultBinding().getFault().getType() == types[index] &&
                first.getPcbLayout().geometryFingerprint().equals(
                    second.getPcbLayout().geometryFingerprint()),
                "NPN seeded fault envelope is not deterministic: " + types[index]);
        }
    }

    private static void verifyProviderFootprint(GeneratedBoardInstance instance) {
        PcbBoardLayout layout = instance.getPcbLayout();
        PcbComponentPlacement actualPlacement = layout.getComponent("Q1");
        require(actualPlacement != null, "NPN layout omitted the provider-owned Q1 placement");
        PcbFootprint expected = StandardPcbFootprintProviders.createRegistry().create(
            instance.getBoard().getComponent("Q1"), actualPlacement.getX(),
            actualPlacement.getY(), new Random(instance.getSeed()), layout.getBoardOutline());
        PcbComponentPlacement expectedPlacement = expected.getPlacement();
        require(actualPlacement.getX() == expectedPlacement.getX() &&
            actualPlacement.getY() == expectedPlacement.getY() &&
            actualPlacement.getWidth() == expectedPlacement.getWidth() &&
            actualPlacement.getHeight() == expectedPlacement.getHeight() &&
            sameRectangle(actualPlacement.getKeepOut(), expectedPlacement.getKeepOut()) &&
            sameRectangle(actualPlacement.getRoutingCourtyard(),
                expectedPlacement.getRoutingCourtyard()),
            "Generated Q1 placement/body/courtyard diverged from the TO-92 provider");
        String[] terminalIds = { "Q1.B", "Q1.C", "Q1.E" };
        Vector<String> boardPadIds = instance.getBoard().getComponent("Q1").getPadIds();
        require(boardPadIds.size() == terminalIds.length,
            "NPN Q1 board terminal count changed");
        for (int index = 0; index < terminalIds.length; index++) {
            require(terminalIds[index].equals(boardPadIds.get(index)),
                "NPN board pad order is not B/C/E at index " + index);
            PcbPadPlacement actual = layout.getPad(terminalIds[index]);
            PcbPadPlacement provider = expected.getPad(terminalIds[index]);
            require(actual != null && samePad(actual, provider),
                "Generated Q1 pad or escape diverged from the TO-92 provider: " +
                    terminalIds[index]);
        }
    }

    private static void verifyConnectorMarkings(CirSim sim, GeneratedBoardInstance instance) {
        BoardPhysicalSpecifications specifications = instance.getPhysicalSpecifications();
        PowerInputNameplate load = specifications.getPowerInputNameplate("LOAD_VIN_INPUT");
        PowerInputNameplate control = specifications.getPowerInputNameplate("CONTROL_VIN_INPUT");
        require(load != null && control != null,
            "NPN physical specifications omitted an external power input nameplate");
        require(sim.pcbWorkbenchController != null,
            "NPN connector marking verification has no workbench renderer");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        require(instance.getPcbLayout().getSilkscreenLabel("net:J1.1") != null &&
            load.getDisplayLabel().equals(instance.getPcbLayout().getSilkscreenLabel("net:J1.1")
                .getText()) && load.getDisplayLabel().equals(
                    renderer.getRenderedSilkscreenLabelTextForDeveloperVerification("net:J1.1")),
            "NPN J1 marking does not match the authoritative load nameplate");
        require(instance.getPcbLayout().getSilkscreenLabel("net:J2.1") != null &&
            control.getDisplayLabel().equals(instance.getPcbLayout().getSilkscreenLabel("net:J2.1")
                .getText()) && control.getDisplayLabel().equals(
                    renderer.getRenderedSilkscreenLabelTextForDeveloperVerification("net:J2.1")),
            "NPN J2 marking does not match the authoritative control nameplate");
        require("GND".equals(renderer.getRenderedSilkscreenLabelTextForDeveloperVerification(
                "net:J1.2")) && "GND".equals(
                renderer.getRenderedSilkscreenLabelTextForDeveloperVerification("net:J2.2")),
            "NPN targeted return-pad markings did not resolve to GND");
    }

    private static void verifyDeterministicNameplateEnvelope() {
        long[] seeds = { 0, 1, 2, 3 };
        double[] expectedLoadVoltages = { 9, 12, 5, 9 };
        for (int index = 0; index < seeds.length; index++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { 4, seeds[index] }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(selection.getFamilyId()) &&
                selection.getSeed() == seeds[index] && generated.getSeed() == seeds[index],
                "NPN nameplate check did not use the ordinary Quick Play route for seed " +
                    seeds[index]);
            verifyGeneratedConnectorMarkings(generated, expectedLoadVoltages[index]);
        }
    }

    private static void verifyGeneratedConnectorMarkings(GeneratedBoardInstance instance,
            double expectedLoadVoltage) {
        BoardPhysicalSpecifications specifications = instance.getPhysicalSpecifications();
        PowerInputNameplate load = specifications.getPowerInputNameplate("LOAD_VIN_INPUT");
        PowerInputNameplate control = specifications.getPowerInputNameplate("CONTROL_VIN_INPUT");
        PcbSilkscreenLabel loadLabel = instance.getPcbLayout().getSilkscreenLabel("net:J1.1");
        PcbSilkscreenLabel controlLabel = instance.getPcbLayout().getSilkscreenLabel("net:J2.1");
        require(load != null && control != null && loadLabel != null && controlLabel != null,
            "NPN deterministic nameplate check omitted a power input or label");
        requireApproximately(expectedLoadVoltage, load.getNominalVoltage(), .0001,
            "NPN generated load nameplate voltage changed for seed " + instance.getSeed());
        requireApproximately(5, control.getNominalVoltage(), .0001,
            "NPN generated control nameplate voltage changed for seed " + instance.getSeed());
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(instance,
            new BoardModificationController(null, instance), instance.getPcbLayout());
        require(load.getDisplayLabel().equals(loadLabel.getText()) &&
            load.getDisplayLabel().equals(renderer.getRenderedSilkscreenLabelTextForDeveloperVerification(
                "net:J1.1")),
            "NPN J1.1 raw/rendered label diverged from the generated load nameplate for seed " +
                instance.getSeed());
        require(control.getDisplayLabel().equals(controlLabel.getText()) &&
            control.getDisplayLabel().equals(renderer.getRenderedSilkscreenLabelTextForDeveloperVerification(
                "net:J2.1")),
            "NPN J2.1 raw/rendered label diverged from the generated control nameplate for seed " +
                instance.getSeed());
    }

    private static void verifyNpnGroundTree(GeneratedBoardInstance instance) {
        PcbBoardLayout layout = instance.getPcbLayout();
        layout.validateGeometry(instance.getBoard());
        String[] groundPads = { "J1.2", "J2.2", "RPD.2", "Q1.E" };
        for (String padId : groundPads)
            require(instance.getBoard().getPad(padId) != null &&
                "GND".equals(instance.getBoard().getPad(padId).getNetId()),
                "NPN ground pad lost its stable GND net identity: " + padId);
        int shift = (int) (((instance.getSeed() % 4) + 4) % 4) * 10;
        int groundTraceCount = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if (!"GND".equals(trace.getNetId()))
                continue;
            groundTraceCount++;
            require("J1.2".equals(trace.getStartPadId()) &&
                ("J2.2".equals(trace.getEndPadId()) || "RPD.2".equals(trace.getEndPadId()) ||
                    "Q1.E".equals(trace.getEndPadId())),
                "NPN ground tree did not retain stable trace endpoints");
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int index = 0; index < x.length; index++) {
                require(!(x[index] == 50 + shift && y[index] == 700),
                    "NPN ground tree retained the old loop/detour geometry");
                for (int prior = 0; prior < index; prior++)
                    require(x[index] != x[prior] || y[index] != y[prior],
                        "NPN ground trace repeats a point: " + trace.getEndPadId());
            }
            if ("J2.2".equals(trace.getEndPadId()))
                require(hasPoint(trace, 60 + shift, 600),
                    "NPN J2.2 ground branch did not use the shared trunk");
            else {
                require(hasPoint(trace, 60 + shift, 430),
                    "NPN ground branch did not leave the shared y=430 trunk");
                if ("RPD.2".equals(trace.getEndPadId()))
                    require(hasPoint(trace, 520 + shift, 430),
                        "NPN RPD.2 branch did not use its deterministic tree escape");
                else {
                    PcbPadPlacement emitter = layout.getPad("Q1.E");
                    require(hasPoint(trace, emitter.getX() + emitter.getEscapeDx() *
                            emitter.getEscapeLength(), emitter.getY() + emitter.getEscapeDy() *
                            emitter.getEscapeLength()),
                        "NPN Q1.E branch did not use the provider escape");
                }
            }
        }
        require(groundTraceCount == 3,
            "NPN GND tree did not expose exactly three stable branch traces");
    }

    private static void verifyHealthyElectricalEnvelope(CirSim sim, GeneratedBoardInstance instance) {
        PowerInputNameplate loadInput = instance.getPhysicalSpecifications()
            .getPowerInputNameplate("LOAD_VIN_INPUT");
        PowerInputNameplate controlInput = instance.getPhysicalSpecifications()
            .getPowerInputNameplate("CONTROL_VIN_INPUT");
        ResistorNameplate loadNameplate = StandardPhysicalDefinitionProviders.RESISTOR.require(
            instance.getPhysicalSpecifications(), "RLOAD");
        ResistorElm loadResistor = (ResistorElm) instance.getComponentBindings()
            .getSingleElement("RLOAD");
        double loadSupply = NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J1.1") -
            NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J1.2");
        double controlSupply = NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
            NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2");
        double loadCurrent = NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
        double ledCurrent = NpnLowSideSwitchGeneratedBoardValidator.ledCurrent(instance);
        double baseCurrent = NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance);
        double collectorCurrent = NpnLowSideSwitchGeneratedBoardValidator.collectorCurrent(instance);
        double resistorVoltage = NpnLowSideSwitchGeneratedBoardValidator.voltage(instance,
            "RLOAD.1") - NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "RLOAD.2");
        double calculatedPower = Math.abs(resistorVoltage * loadResistor.getCurrent());
        double resistorPower = loadResistor.getPower();
        double solverPower = Math.abs(resistorPower);
        sim.publishNpnElectricalReportForDeveloperVerification(
            "seed=" + instance.getSeed() +
            ";loadLabel=" + loadInput.getDisplayLabel() +
            ";loadNominalV=" + loadInput.getNominalVoltage() +
            ";controlLabel=" + controlInput.getDisplayLabel() +
            ";controlNominalV=" + controlInput.getNominalVoltage() +
            ";loadSolverV=" + loadSupply +
            ";controlSolverV=" + controlSupply +
            ";rloadOhms=" + loadResistor.getResistance() +
            ";rloadRatingW=" + loadNameplate.getRatedWattage() +
            ";loadCurrentA=" + loadCurrent +
            ";ledCurrentA=" + ledCurrent +
            ";baseCurrentA=" + baseCurrent +
            ";collectorCurrentA=" + collectorCurrent +
            ";rloadPowerCalculatedW=" + calculatedPower +
            ";rloadPowerSolverW=" + resistorPower);
        require(loadInput != null && controlInput != null && loadInput.getNominalVoltage() > 4 &&
            loadInput.getNominalVoltage() <= 12 && controlInput.getNominalVoltage() == 5,
            "NPN connector nameplate left the intended voltage envelope");
        requireApproximately(loadInput.getNominalVoltage(), loadSupply, .1,
            "NPN healthy load connector solver voltage");
        requireApproximately(controlInput.getNominalVoltage(), controlSupply, .1,
            "NPN healthy control connector solver voltage");
        requireApproximately(loadNameplate.getNominalResistanceOhms(), loadResistor.getResistance(),
            .001, "NPN RLOAD solver resistance");
        require(loadCurrent > .008 && loadCurrent < .05 && ledCurrent > .005 && ledCurrent < .05 &&
            baseCurrent > .00002 && baseCurrent < .01 && collectorCurrent > .005 &&
            collectorCurrent < .05 && calculatedPower > 0 && calculatedPower <=
            loadNameplate.getRatedWattage() && solverPower <= loadNameplate.getRatedWattage() &&
            Math.abs(calculatedPower - solverPower) < .0001,
            "NPN healthy ON electrical envelope invalid: supply=" + loadSupply +
                " RLOAD=" + loadResistor.getResistance() + " loadI=" + loadCurrent +
                " ledI=" + ledCurrent + " baseI=" + baseCurrent + " collectorI=" +
                collectorCurrent + " power=" + calculatedPower + "/" + solverPower +
                " rating=" + loadNameplate.getRatedWattage());
    }

    private static void verifyScenarioPresentation(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        GeneratedObservedBehavior observed = challenge.getScenario().getObservedBehavior();
        boolean expectedOn = observed == GeneratedObservedBehavior.NPN_LOAD_NOT_SWITCHING;
        require(observed == GeneratedObservedBehavior.NPN_LOAD_NOT_SWITCHING ||
                observed == GeneratedObservedBehavior.NPN_LOAD_STUCK_ACTIVE,
            "NPN challenge selected a non-NPN presentation behavior");
        require(familyState(instance).isCommandedOn() == expectedOn,
            "NPN selected complaint did not establish its deliberate command presentation");
        double control = NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
            NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2");
        double load = NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
        if (expectedOn)
            require(control > 3 && load < .000001,
                "NPN not-switching presentation is not commanded ON/high with an inactive load");
        else
            require(control < 1 && load > .005,
                "NPN stuck-active presentation is not commanded OFF/low with an active load");
    }

    private static void verifyScenarioCompatibilityPurity(CirSim sim,
            GeneratedBoardInstance instance, GeneratedChallengeController challenge) {
        GeneratedScenarioCatalog<GeneratedObservedBehavior> catalog =
            challenge.getDefinition().getScenarioCatalog();
        GeneratedScenarioCatalog<GeneratedObservedBehavior> reversed =
            catalog.reversedForDeveloperVerification();
        String beforeCircuit = simulationTopologyFingerprint(sim);
        String beforePhysical = physicalStateFingerprint(instance);
        String beforeModifications = modificationStateFingerprint(sim);
        boolean beforeCommand = familyState(instance).isCommandedOn();
        BoardPowerState beforePower = sim.getBoardPowerController().getState();
        boolean beforeOverlay = sim.activeMeasurementOverlay;
        boolean beforeFaultApplied = instance.getFaultBinding().isApplied();
        Vector<String> expected = catalog.getCompatibleScenarioIdsForDeveloperVerification(
            instance, sim.getBoardModificationController(), beforePower);
        require(!expected.isEmpty(), "NPN compatibility did not produce a compatible set");
        requireSnapshotUnchanged(sim, instance, beforeCircuit, beforePhysical, beforeModifications,
            beforeCommand,
            beforePower, beforeOverlay, beforeFaultApplied,
            "first NPN compatibility evaluation");
        Vector<String> reversedSet = reversed.getCompatibleScenarioIdsForDeveloperVerification(
            instance, sim.getBoardModificationController(), beforePower);
        require(expected.equals(reversedSet),
            "NPN compatible scenario set depended on candidate order");
        GeneratedScenario<GeneratedObservedBehavior> first = catalog.select(
            challenge.getDefinition().getSelectionSeed(), instance,
            sim.getBoardModificationController(), beforePower);
        requireSnapshotUnchanged(sim, instance, beforeCircuit, beforePhysical, beforeModifications,
            beforeCommand,
            beforePower, beforeOverlay, beforeFaultApplied,
            "NPN ordered compatibility selection");
        GeneratedScenario<GeneratedObservedBehavior> second = reversed.select(
            challenge.getDefinition().getSelectionSeed(), instance,
            sim.getBoardModificationController(), beforePower);
        require(first.getScenarioId().equals(second.getScenarioId()),
            "NPN selected scenario depended on candidate order");
        requireSnapshotUnchanged(sim, instance, beforeCircuit, beforePhysical, beforeModifications,
            beforeCommand,
            beforePower, beforeOverlay, beforeFaultApplied,
            "NPN reversed compatibility selection");
        Vector<String> repeated = catalog.getCompatibleScenarioIdsForDeveloperVerification(
            instance, sim.getBoardModificationController(), beforePower);
        require(expected.equals(repeated), "NPN repeated compatibility was not idempotent");
        requireSnapshotUnchanged(sim, instance, beforeCircuit, beforePhysical, beforeModifications,
            beforeCommand,
            beforePower, beforeOverlay, beforeFaultApplied,
            "repeated NPN compatibility evaluation");
        require(!sim.isObservationalValidationActiveForDeveloperVerification(),
            "NPN compatibility left an observational transaction open");
    }

    private static void verifyStableDcMeasurements(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        require(sim.pcbWorkbenchController != null,
            "NPN DC stability verification has no PCB renderer");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        ProbeTarget ground = probeForPad(sim, renderer, "J2.2");
        ProbeTarget control = probeForPad(sim, renderer, "J2.1");
        ProbeTarget collector = probeForPad(sim, renderer, "Q1.C");
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(control, ground);
        requireStableDcSeries(sim, challenge, familyState(instance).isCommandedOn(),
            "NPN control-to-ground");
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(collector, ground);
        requireStableDcSeries(sim, challenge, familyState(instance).isCommandedOn(),
            "NPN collector-to-ground");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void requireStableDcSeries(CirSim sim,
            GeneratedChallengeController challenge, boolean expectedCommand, String label) {
        String initialText = sim.instrumentController.getReadingForDeveloperVerification();
        double initialVoltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(!"--- V".equals(initialText) && finite(initialVoltage),
            label + " did not produce an initial real DC display");
        int placeholderBefore = sim.instrumentController
            .getDcVoltagePlaceholderDisplayCountForDeveloperVerification();
        int measurementBefore = sim.instrumentController
            .getDcVoltageMeasurementCountForDeveloperVerification();
        int analysisBefore = sim.getAnalysisCountForDeveloperVerification();
        int verificationBefore = sim.getGeneratedVerificationCountForDeveloperVerification();
        boolean commandBefore = expectedCommand;
        GeneratedRepairStatus status = challenge.getRepairStatus();
        require(status != GeneratedRepairStatus.CORRECTLY_RESTORED,
            label + " unexpectedly reported a faulted board as repaired");
        require(familyState(sim.getGeneratedBoardInstance()).isCommandedOn() == commandBefore,
            label + " repair validation changed the live command");
        require(sim.instrumentController.getReadingForDeveloperVerification().equals(initialText) &&
                sim.instrumentController.getDcVoltagePlaceholderDisplayCountForDeveloperVerification() ==
                    placeholderBefore &&
                sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification() ==
                    measurementBefore &&
                sim.getGeneratedVerificationCountForDeveloperVerification() == verificationBefore,
            label + " internal validation changed the stable instrument display or count");
        require(sim.getAnalysisCountForDeveloperVerification() > analysisBefore &&
                !sim.isObservationalValidationActiveForDeveloperVerification(),
            label + " did not complete its observational analysis transaction");
        sim.updateCircuit();
        double settledVoltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        int settledPlaceholders = sim.instrumentController
            .getDcVoltagePlaceholderDisplayCountForDeveloperVerification();
        for (int cycle = 0; cycle < 12; cycle++) {
            sim.updateCircuit();
            String text = sim.instrumentController.getReadingForDeveloperVerification();
            double voltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
            require(!"--- V".equals(text) && finite(voltage) &&
                    Math.abs(voltage - settledVoltage) < .0001,
                label + " flickered or changed without a real voltage change at cycle " + cycle);
        }
        require(sim.instrumentController.getDcVoltagePlaceholderDisplayCountForDeveloperVerification() ==
                settledPlaceholders,
            label + " entered the placeholder display during ordinary stable updates");
    }

    private static ProbeTarget probeForPad(CirSim sim, PcbWorkbenchRenderer renderer,
            String padId) {
        Point point = renderer.getPadPoint(padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target != null && target.isValid(), "NPN probe target was not valid: " + padId);
        return target;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String physicalStateFingerprint(GeneratedBoardInstance instance) {
        String result = "";
        for (PhysicalBoardSlot slot : instance.getPhysicalBoardRuntime().getSlots()) {
            PhysicalPart<?> part = slot.getInstalledPart();
            result += slot.getId() + "=" + (part == null ? "" : part.getId()) + ";";
        }
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts())
            result += part.getId() + "@installed=" + part.isInstalled() + "@faulted=" +
                part.isFaulted() + ";";
        return result;
    }

    private static void requireSnapshotUnchanged(CirSim sim, GeneratedBoardInstance instance,
            String circuit, String physical, String modifications, boolean command,
            BoardPowerState power,
            boolean overlay, boolean faultApplied, String operation) {
        require(circuit.equals(simulationTopologyFingerprint(sim)),
            operation + " changed topology/elements");
        require(physical.equals(physicalStateFingerprint(instance)),
            operation + " changed physical parts");
        require(modifications.equals(modificationStateFingerprint(sim)),
            operation + " changed board modifications");
        require(familyState(instance).isCommandedOn() == command,
            operation + " changed the command");
        require(sim.getBoardPowerController().getState() == power,
            operation + " changed board power");
        require(sim.activeMeasurementOverlay == overlay,
            operation + " changed the active measurement overlay");
        require(instance.getFaultBinding().isApplied() == faultApplied,
            operation + " changed fault application");
    }

    private static String modificationStateFingerprint(CirSim sim) {
        String result = "";
        for (String componentId : new String[] { "Q1", "RB", "RLOAD" })
            result += componentId + "=" + sim.getBoardModificationController()
                .getComponentState(componentId) + ";";
        return result;
    }

    private static String simulationTopologyFingerprint(CirSim sim) {
        String result = "";
        for (int index = 0; index < sim.elmList.size(); index++) {
            CircuitElm element = sim.getElm(index);
            result += index + ":" + System.identityHashCode(element) + ":" +
                element.getClass().getName() + ":" + element.x + ":" + element.y + ":" +
                element.x2 + ":" + element.y2;
            if (element instanceof SwitchElm)
                result += ":switch=" + ((SwitchElm) element).position;
            result += ";";
        }
        return result;
    }

    private static void verifySolvedFault(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        NpnLowSideSwitchFamilyState state = familyState(instance);
        if (type == GeneratedFaultType.TRANSISTOR_CE_SHORT) {
            state.setCommandedOn(sim, false);
            require(NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) > .005 &&
                NpnLowSideSwitchGeneratedBoardValidator.collectorVoltage(instance) < 1.0,
                "NPN C-E short did not remain active with low control");
            return;
        }
        state.setCommandedOn(sim, true);
        require(NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) < .000001,
            "NPN open fault still drove the load in its commanded-on state");
        if (type == GeneratedFaultType.TRANSISTOR_CE_OPEN)
            require(NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance) > .00002,
                "NPN C-E open did not preserve independent base drive");
        else if (type == GeneratedFaultType.BASE_RESISTOR_OPEN)
            require(NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance) < .000001,
                "NPN base-path open did not remove base drive");
        else if (type == GeneratedFaultType.LOAD_PATH_OPEN)
            require(NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance) > .00002,
                "NPN load-path open masqueraded as a base fault");
        else
            throw new IllegalStateException("Unsupported NPN solved fault: " + type);
    }

    private static void verifyHealthyReference(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        GeneratedFaultController faults = challenge.getFaultController();
        NpnLowSideSwitchFamilyState state = familyState(instance);
        challenge.beginDeveloperVerificationScope();
        try {
            require(faults.clearForDeveloperVerification(),
                "NPN developer fault clear was ignored");
            state.setCommandedOn(sim, true);
            require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
                "NPN healthy reference did not switch on in CircuitJS");
            verifyHealthyElectricalEnvelope(sim, instance);
            state.setCommandedOn(sim, false);
            require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                "NPN healthy reference did not switch off in CircuitJS");
            require(faults.apply(), "NPN developer fault reapply was ignored");
        } finally {
            challenge.endDeveloperVerificationScope();
        }
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        state.setCommandedOn(sim, type == GeneratedFaultType.TRANSISTOR_CE_SHORT ? false : true);
    }

    private static void verifyStatePreservingChecks(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        NpnLowSideSwitchFamilyState state = familyState(instance);
        boolean prior = state.isCommandedOn();
        NpnSwitchObservation before = observeSwitch(instance);
        challenge.getRepairStatus();
        require(state.isCommandedOn() == prior,
            "NPN repair-status check persisted its temporary command");
        NpnSwitchObservation afterRepairStatus = observeSwitch(instance);
        requireObservationUnchanged(before, afterRepairStatus,
            "NPN repair-status check changed the live faulted solver state");
        GeneratedFaultType faultType = instance.getFaultBinding().getFault().getType();
        if (faultType == GeneratedFaultType.TRANSISTOR_CE_OPEN) {
            require(prior && before.controlVoltage > 3 && before.loadCurrent < .000001 &&
                before.baseCurrent > .00002,
                "NPN C-E open state-preservation case did not start commanded ON/high");
        } else if (faultType == GeneratedFaultType.TRANSISTOR_CE_SHORT) {
            require(!prior && before.controlVoltage < 1 && before.loadCurrent > .005 &&
                before.collectorVoltage < 1,
                "NPN C-E short state-preservation case did not start commanded OFF/low");
        }
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "NPN faulted state was incorrectly accepted as restored");
        require(challenge.getScenario() != null && challenge.getScenario().isCompatible(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED),
            "NPN scenario compatibility check failed during state-preservation regression");
        require(state.isCommandedOn() == prior,
            "NPN scenario compatibility check persisted its temporary command");
        requireObservationUnchanged(before, observeSwitch(instance),
            "NPN scenario compatibility check changed the live faulted solver state");
    }

    private static void verifyPhysicalRepairLifecycle(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        String target = challenge.getDefinition().getFault().getTargetComponentId();
        BoardModificationController modifications = sim.getBoardModificationController();
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        NpnLowSideSwitchFamilyState state = familyState(instance);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        if ("Q1".equals(target)) {
            ReplaceableNpnBoardCapability capability =
                ReplaceableNpnBoardCapability.require(instance);
            PhysicalNpnPart original = capability.getSlot().getInstalledPart();
            NpnSlotController controller = sim.getNpnSlotController();
            require(original != null && original.isFaulted() &&
                original.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN original fault identity was not installed");
            require(controller.removeInstalledPart() && instance.getFaultBinding().isApplied() &&
                !original.isInstalled(), "Removing original NPN lost its fault ownership");
            if (instance.getSeed() == 1 && instance.getFaultBinding().getFault().getType() ==
                    GeneratedFaultType.TRANSISTOR_CE_SHORT)
                verifyLooseCeShortMeasurement(sim, instance, original);
            require(controller.install(original.getId()) && original.isInstalled() &&
                original.ownsGeneratedFault(instance.getFaultBinding()),
                "Reinstalling original NPN changed its fault identity");
            verifyNpnTerminalIdentity(instance, original);
            require(controller.removeInstalledPart() &&
                controller.installNewFromCatalog(NpnReplacementCatalog.WRONG_LOW_BETA),
                "Wrong NPN catalog replacement was not accepted");
            PhysicalNpnPart wrong = capability.getSlot().getInstalledPart();
            require(wrong != original && wrong.getFaultBinding() == null &&
                wrong.getElement() != original.getElement() &&
                !wrong.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN catalog replacement inherited original identity or fault");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            if (type == GeneratedFaultType.TRANSISTOR_CE_OPEN ||
                    type == GeneratedFaultType.TRANSISTOR_CE_SHORT) {
                boolean expectedCommandedOn = type == GeneratedFaultType.TRANSISTOR_CE_OPEN;
                state.setCommandedOn(sim, expectedCommandedOn);
                verifyWrongNpnReplacementStatus(instance, challenge, state, type);
            } else {
                state.setCommandedOn(sim, type != GeneratedFaultType.TRANSISTOR_CE_SHORT);
                require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
                    "Wrong NPN replacement incorrectly completed repair");
            }
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(controller.removeInstalledPart() &&
                controller.installNewFromCatalog(NpnReplacementCatalog.CORRECT),
                "Correct NPN catalog replacement was not accepted");
            PhysicalNpnPart correct = capability.getSlot().getInstalledPart();
            require(correct != original && correct != wrong && correct.getFaultBinding() == null &&
                correct.getElement() != original.getElement() &&
                correct.getElement() != wrong.getElement(),
                "Correct NPN replacement did not receive a distinct physical backing");
            verifyNpnTerminalIdentity(instance, correct);
        } else {
            ReplaceableResistorBoardCapability capability =
                ReplaceableResistorBoardCapability.find(instance.getPhysicalBoardRuntime(), target);
            ResistorSlotController controller = sim.getResistorSlotController(target);
            require(capability != null && controller != null,
                "NPN resistor target has no replaceable capability");
            PhysicalResistorPart original = capability.getSlot().getInstalledPart();
            require(original != null && original.isFaulted() &&
                original.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN resistor original fault identity was not installed");
            require(controller.removeInstalledPart() && instance.getFaultBinding().isApplied() &&
                !original.isInstalled() && controller.install(original.getId()) &&
                original.isInstalled() && original.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN resistor original remove/reinstall changed fault identity");
            require(controller.removeInstalledPart() &&
                controller.installNewFromCatalog("R_CATALOG_10000000"),
                "Wrong NPN resistor replacement was not accepted");
            PhysicalResistorPart wrong = capability.getSlot().getInstalledPart();
            require(wrong != original && wrong.getFaultBinding() == null &&
                !wrong.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN resistor replacement inherited original identity or fault");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            state.setCommandedOn(sim, type != GeneratedFaultType.TRANSISTOR_CE_SHORT);
            require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
                "Wrong NPN resistor replacement incorrectly completed repair");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            String correctId = "RB".equals(target) ? "R_CATALOG_1000" : "R_CATALOG_330";
            require(controller.removeInstalledPart() && controller.installNewFromCatalog(correctId),
                "Correct NPN resistor replacement was not accepted");
        }
        sim.setBoardPowerState(BoardPowerState.POWERED);
        if (instance.getSeed() == 1 && type == GeneratedFaultType.TRANSISTOR_CE_SHORT) {
            state.setCommandedOn(sim, true);
            require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
                "Natural seed-1 correct NPN did not switch on after CE-short repair");
            state.setCommandedOn(sim, false);
            require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                "Natural seed-1 correct NPN did not switch off after CE-short repair");
        }
        state.setCommandedOn(sim, type == GeneratedFaultType.TRANSISTOR_CE_SHORT ? false : true);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correct NPN physical repair did not restore functional switching");
        require(modifications.isFullyRestored() && !sim.activeMeasurementOverlay,
            "NPN repair verifier left modification or measurement residue");
        if (instance.getSeed() == 1 && type == GeneratedFaultType.TRANSISTOR_CE_SHORT)
            require(challenge.finishJob() && challenge.isCompleted(),
                "Natural seed-1 correct NPN did not pass generic Finish Job readiness");
    }

    private static void verifyWrongNpnReplacementStatus(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, NpnLowSideSwitchFamilyState state,
            GeneratedFaultType type) {
        boolean expectedCommandedOn = type == GeneratedFaultType.TRANSISTOR_CE_OPEN;
        require(state.isCommandedOn() == expectedCommandedOn,
            "Wrong NPN replacement did not retain the expected command before status check");
        NpnSwitchObservation before = observeSwitch(instance);
        GeneratedRepairStatus status = challenge.getRepairStatus();
        require(state.isCommandedOn() == expectedCommandedOn,
            "Wrong NPN replacement status check persisted a temporary command");
        requireObservationUnchanged(before, observeSwitch(instance),
            "Wrong NPN replacement status check changed the live solver state");
        require(status != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Wrong NPN replacement incorrectly completed repair");
    }

    private static void verifyCorrectRepairStatusPreservesState(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        GeneratedFaultType faultType = instance.getFaultBinding().getFault().getType();
        if (faultType != GeneratedFaultType.TRANSISTOR_CE_OPEN &&
                faultType != GeneratedFaultType.TRANSISTOR_CE_SHORT)
            return;
        NpnLowSideSwitchFamilyState state = familyState(instance);
        boolean prior = state.isCommandedOn();
        NpnSwitchObservation before = observeSwitch(instance);
        require(faultType == GeneratedFaultType.TRANSISTOR_CE_OPEN ? prior : !prior,
            "NPN repaired-state command did not retain the meaningful initial condition");
        GeneratedRepairStatus status = challenge.getRepairStatus();
        require(status == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "NPN correctly repaired state did not pass the functional status proof");
        require(state.isCommandedOn() == prior,
            "NPN repaired-state status check persisted its temporary command");
        NpnSwitchObservation after = observeSwitch(instance);
        requireObservationUnchanged(before, after,
            "NPN repaired-state status check changed the live solver state");
        if (faultType == GeneratedFaultType.TRANSISTOR_CE_OPEN)
            require(prior && NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance) &&
                after.controlVoltage > 3 && after.loadCurrent > .005 &&
                after.baseCurrent > .00002,
                "NPN C-E open repair did not restore live commanded-ON behavior");
        else
            require(!prior && NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance) &&
                after.controlVoltage < 1 && after.loadCurrent < .000001 &&
                after.baseCurrent < .000001,
                "NPN C-E short repair did not restore live commanded-OFF behavior");
    }

    private static NpnSwitchObservation observeSwitch(GeneratedBoardInstance instance) {
        return new NpnSwitchObservation(
            NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
                NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2"),
            NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance),
            NpnLowSideSwitchGeneratedBoardValidator.baseCurrent(instance),
            NpnLowSideSwitchGeneratedBoardValidator.collectorCurrent(instance),
            NpnLowSideSwitchGeneratedBoardValidator.collectorVoltage(instance));
    }

    private static void requireObservationUnchanged(NpnSwitchObservation expected,
            NpnSwitchObservation actual, String message) {
        requireApproximately(expected.controlVoltage, actual.controlVoltage, .0001,
            message + ": control voltage");
        requireApproximately(expected.loadCurrent, actual.loadCurrent, .00001,
            message + ": load current");
        requireApproximately(expected.baseCurrent, actual.baseCurrent, .00001,
            message + ": base current");
        requireApproximately(expected.collectorCurrent, actual.collectorCurrent, .00001,
            message + ": collector current");
        requireApproximately(expected.collectorVoltage, actual.collectorVoltage, .0001,
            message + ": collector voltage");
    }

    private static final class NpnSwitchObservation {
        final double controlVoltage;
        final double loadCurrent;
        final double baseCurrent;
        final double collectorCurrent;
        final double collectorVoltage;

        NpnSwitchObservation(double controlVoltage, double loadCurrent, double baseCurrent,
                double collectorCurrent, double collectorVoltage) {
            this.controlVoltage = controlVoltage;
            this.loadCurrent = loadCurrent;
            this.baseCurrent = baseCurrent;
            this.collectorCurrent = collectorCurrent;
            this.collectorVoltage = collectorVoltage;
        }
    }

    private static void verifyLooseCeShortMeasurement(CirSim sim, GeneratedBoardInstance instance,
            PhysicalNpnPart original) {
        require(sim.pcbWorkbenchController != null,
            "Natural seed-1 loose NPN verification has no renderer");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        ProbeTarget collector = new PhysicalNpnPartProbeTarget(sim, instance, original.getId(), 1,
            renderer);
        ProbeTarget emitter = new PhysicalNpnPartProbeTarget(sim, instance, original.getId(), 2,
            renderer);
        require(collector.isValid() && emitter.isValid(),
            "Removed original NPN did not expose valid loose B/C/E probe targets");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(collector, emitter);
        double resistance = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        require(!Double.isNaN(resistance) && !Double.isInfinite(resistance) && resistance < 1,
            "Natural seed-1 loose CE resistance did not reflect the real short backing: " +
                resistance);
        sim.instrumentController.setContinuityProbesForDeveloperVerification(collector, emitter);
        require(sim.instrumentController.isContinuityDetectedForDeveloperVerification() &&
            !"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Natural seed-1 loose CE continuity did not reflect the real short backing");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyNpnTerminalIdentity(GeneratedBoardInstance instance,
            PhysicalNpnPart part) {
        require("B".equals(part.getTerminal(0).getTerminalName()) &&
            "C".equals(part.getTerminal(1).getTerminalName()) &&
            "E".equals(part.getTerminal(2).getTerminalName()) &&
            sameEndpoint(part.getPublicTerminal(0), componentEndpoint(instance, "Q1.B")) &&
            sameEndpoint(part.getPublicTerminal(1), componentEndpoint(instance, "Q1.C")) &&
            sameEndpoint(part.getPublicTerminal(2), componentEndpoint(instance, "Q1.E")),
            "Installed NPN replacement did not preserve Q1 B/C/E terminal identities");
    }

    private static NpnLowSideSwitchFamilyState familyState(GeneratedBoardInstance instance) {
        if (!(instance.getFamilyState() instanceof NpnLowSideSwitchFamilyState))
            throw new IllegalStateException("NPN family state is missing");
        return (NpnLowSideSwitchFamilyState) instance.getFamilyState();
    }

    private static CircuitMeasurementEndpoint componentEndpoint(GeneratedBoardInstance instance,
            String padId) {
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().get("Q1", padId);
        CircuitMeasurementEndpoint endpoint = binding.getComponentEndpoint();
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("NPN component terminal is not CircuitJS-backed: " + padId);
        return endpoint;
    }

    private static boolean sameEndpoint(CircuitMeasurementEndpoint first,
            CircuitMeasurementEndpoint second) {
        if (!(first instanceof CircuitPostMeasurementEndpoint) ||
                !(second instanceof CircuitPostMeasurementEndpoint))
            return false;
        CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint) first;
        CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint) second;
        return a.getElement() == b.getElement() && a.getPostIndex() == b.getPostIndex();
    }

    private static boolean sameRectangle(Rectangle first, Rectangle second) {
        return first != null && second != null && first.x == second.x && first.y == second.y &&
            first.width == second.width && first.height == second.height;
    }

    private static boolean samePad(PcbPadPlacement first, PcbPadPlacement second) {
        return first.getPadId().equals(second.getPadId()) && first.getX() == second.getX() &&
            first.getY() == second.getY() && first.getEscapeDx() == second.getEscapeDx() &&
            first.getEscapeDy() == second.getEscapeDy() &&
            first.getEscapeLength() == second.getEscapeLength();
    }

    private static boolean hasPoint(PcbTraceGeometry trace, int x, int y) {
        int[] xPoints = trace.getXPoints();
        int[] yPoints = trace.getYPoints();
        for (int index = 0; index < xPoints.length; index++)
            if (xPoints[index] == x && yPoints[index] == y)
                return true;
        return false;
    }

    private static void requireApproximately(double expected, double actual, double tolerance,
            String message) {
        if (Double.isNaN(actual) || Double.isInfinite(actual) ||
                Math.abs(expected - actual) > tolerance)
            throw new IllegalStateException(message + ": expected=" + expected + " actual=" +
                actual + " tolerance=" + tolerance);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
