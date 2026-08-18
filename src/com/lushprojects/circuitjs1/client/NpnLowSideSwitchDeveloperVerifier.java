package com.lushprojects.circuitjs1.client;

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
        require(challenge.getScenario() != null && challenge.getScenario().isCompatible(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED),
            "NPN complaint is not backed by the observed solved behavior");
        verifyHealthyReference(instance, challenge, sim);
        verifyPhysicalRepairLifecycle(instance, challenge, sim);
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

    private static void verifyPhysicalRepairLifecycle(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, CirSim sim) {
        String target = challenge.getDefinition().getFault().getTargetComponentId();
        BoardModificationController modifications = sim.getBoardModificationController();
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
            require(controller.install(original.getId()) && original.isInstalled() &&
                original.ownsGeneratedFault(instance.getFaultBinding()),
                "Reinstalling original NPN changed its fault identity");
            require(controller.removeInstalledPart() &&
                controller.installNewFromCatalog(NpnReplacementCatalog.WRONG_LOW_BETA),
                "Wrong NPN catalog replacement was not accepted");
            PhysicalNpnPart wrong = capability.getSlot().getInstalledPart();
            require(wrong != original && wrong.getFaultBinding() == null &&
                !wrong.ownsGeneratedFault(instance.getFaultBinding()),
                "NPN catalog replacement inherited original identity or fault");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            familyState(instance).setCommandedOn(sim, true);
            require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
                "Wrong NPN replacement incorrectly completed repair");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(controller.removeInstalledPart() &&
                controller.installNewFromCatalog(NpnReplacementCatalog.CORRECT),
                "Correct NPN catalog replacement was not accepted");
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
            familyState(instance).setCommandedOn(sim, true);
            require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
                "Wrong NPN resistor replacement incorrectly completed repair");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            String correctId = "RB".equals(target) ? "R_CATALOG_1000" : "R_CATALOG_330";
            require(controller.removeInstalledPart() && controller.installNewFromCatalog(correctId),
                "Correct NPN resistor replacement was not accepted");
        }
        sim.setBoardPowerState(BoardPowerState.POWERED);
        GeneratedFaultType type = instance.getFaultBinding().getFault().getType();
        familyState(instance).setCommandedOn(sim, type == GeneratedFaultType.TRANSISTOR_CE_SHORT ? false : true);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correct NPN physical repair did not restore functional switching");
        require(modifications.isFullyRestored() && !sim.activeMeasurementOverlay,
            "NPN repair verifier left modification or measurement residue");
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

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
