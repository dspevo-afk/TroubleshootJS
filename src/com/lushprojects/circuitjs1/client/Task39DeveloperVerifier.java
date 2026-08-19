package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused developer proof for the Task 39 public operation/retest boundary. */
final class Task39DeveloperVerifier {
    private Task39DeveloperVerifier() { }

    static void verify(CirSim sim) {
        verifyRegistryProfiles();
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        require(instance != null, "Task 39 verifier requires a generated board");
        require(instance.getCustomerRetestProfile() != null &&
            instance.getOperationCatalog().find(GeneratedBoardOperationIds.CUSTOMER_RETEST) != null,
            "Current board is missing its customer retest operation");
        verifyPrivacy(sim);
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()))
            verifyNpnOperations(sim, instance);
        else if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()))
            verifyNmosOperations(sim, instance);
        else if (QuickPlayFamilyRegistry.RC_DELAY.equals(instance.getCircuitFamilyId()))
            verifyRcOperation(sim, instance);
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Task 39 verifier changed board power state");
    }

    private static void verifyRegistryProfiles() {
        Vector<String> familyIds = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        require(familyIds.size() == 6, "Quick Play family registry changed unexpectedly");
        for (int index = 0; index < familyIds.size(); index++) {
            GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(
                familyIds.elementAt(index), 0);
            GeneratedCustomerRetestProfile profile = instance.getCustomerRetestProfile();
            require(profile != null && profile.getStableId().indexOf('.') < 0 &&
                profile.getRequiredPowerTransition().length() != 0 &&
                profile.getObservableOutput().length() != 0 &&
                profile.getTimingAndRepetition().length() != 0 &&
                profile.getUnaffectedFunctions().length() != 0,
                "Family is missing a complete customer retest profile: " + familyIds.elementAt(index));
            for (GeneratedBoardOperation operation : instance.getOperationCatalog().getAll()) {
                String id = operation.getStableId();
                require(id.indexOf('.') < 0 && id.indexOf("J2") < 0 && id.indexOf("NODE") < 0 &&
                    id.indexOf("PAD") < 0, "Operation identity is not semantic: " + id);
            }
        }
    }

    private static void verifyNpnOperations(CirSim sim, GeneratedBoardInstance instance) {
        boolean priorCommand = ((NpnLowSideSwitchFamilyState) instance.getFamilyState())
            .isCommandedOn();
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        boolean priorPhysicalState = sim.getBoardModificationController().isFullyRestored();
        try {
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
            require(NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
                NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2") > 4.0,
                "NPN HIGH operation did not produce solver-backed J2.1 voltage");
            double highLoad = NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            require(NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
                NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2") < .1,
                "NPN LOW operation did not produce solver-backed J2.1 voltage");
            double lowLoad = NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
            require(highLoad > .005 && lowLoad < .000001,
                "NPN HIGH/LOW operation did not switch the solver-backed load");
        } finally {
            try {
                instance.invokeOperation(priorCommand ? GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            } finally {
                try {
                    GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                } finally {
                    require(priorPhysicalState == sim.getBoardModificationController()
                            .isFullyRestored(), "NPN operation changed physical board state");
                }
            }
        }
    }

    private static void verifyNmosOperations(CirSim sim, GeneratedBoardInstance instance) {
        boolean priorCommand = ((NmosLowSideSwitchFamilyState) instance.getFamilyState())
            .isCommandedOn();
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        boolean priorPhysicalState = sim.getBoardModificationController().isFullyRestored();
        try {
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
            require(NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) > 3,
                "NMOS HIGH operation did not produce solver-backed gate voltage");
            double highLoad = NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            require(NmosLowSideSwitchGeneratedBoardValidator.gateSourceVoltage(instance) < .1,
                "NMOS LOW operation did not produce solver-backed gate voltage");
            double lowLoad = NmosLowSideSwitchGeneratedBoardValidator.loadCurrent(instance);
            require(highLoad > .005 && lowLoad < .000001,
                "NMOS HIGH/LOW operation did not switch the solver-backed load");
        } finally {
            try {
                instance.invokeOperation(priorCommand ? GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            } finally {
                try {
                    GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
                } finally {
                    require(priorPhysicalState == sim.getBoardModificationController()
                            .isFullyRestored(), "NMOS operation changed physical board state");
                }
            }
        }
    }

    private static void verifyRcOperation(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedCustomerRetestProfile profile = instance.getCustomerRetestProfile();
        require(profile.getRequiredPowerTransition().indexOf("discharged") >= 0,
            "RC profile does not document stored-energy safety");
        if (sim.getGeneratedChallengeController() == null)
            return;
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        boolean priorPhysicalState = sim.getBoardModificationController().isFullyRestored();
        try {
            if (priorPower == BoardPowerState.POWERED) {
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                GeneratedCustomerRetestResult unpoweredResult = instance.invokeOperation(
                    GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
                require(unpoweredResult != null && !unpoweredResult.isPassed(),
                    "RC customer retest passed while Board Power was OFF");
                require(unpoweredResult.getPlayerMessage().indexOf("Power the board ON") >= 0,
                    "RC unpowered customer retest did not explain the required power state");
                GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
            }
            GeneratedCustomerRetestResult result = instance.invokeOperation(
                GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
            require(result != null, "RC customer retest did not return a player-safe result");
        } finally {
            GeneratedCustomerRetestSupport.restorePower(sim, priorPower);
            require(priorPower == sim.getBoardPowerController().getState() &&
                priorPhysicalState == sim.getBoardModificationController().isFullyRestored(),
                "RC customer retest did not restore power/physical state");
        }
    }

    private static void verifyPrivacy(CirSim sim) {
        if (sim.pcbWorkbenchController == null)
            return;
        String text = sim.pcbWorkbenchController.getPlayerFacingTextForDeveloperVerification()
            .toLowerCase();
        require(text.indexOf("generatedfaulttype") < 0 && text.indexOf("fault binding") < 0 &&
            text.indexOf("random seed") < 0 && text.indexOf("solver node") < 0,
            "Task 39 player UI exposes developer/fault metadata");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
