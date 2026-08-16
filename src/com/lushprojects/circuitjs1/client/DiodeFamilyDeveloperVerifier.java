package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class DiodeFamilyDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && DiodeProtectedIndicatorGenerator.FAMILY_ID.equals(
            instance.getCircuitFamilyId()) && challenge != null && challenge.isReady(),
            "Diode verification requires a ready diode challenge");
        DiodeProtectedIndicatorFamilyState state =
            DiodeProtectedIndicatorFamilyState.require(instance);
        require(instance.getBoard().getPad("D1.A") != null &&
            instance.getBoard().getPad("D1.K") != null &&
            instance.getPhysicalSpecifications().getDiodeNameplate("D1") != null,
            "D1 logical identity or nameplate is missing");
        require(state.getCatalog().getEntries().size() == 2,
            "Diode catalog choices changed unexpectedly");
        PhysicalDiodePart original = state.getInventory().get("D1_ORIGINAL");
        require(original.isFaulted() && original.getLocation() == DiodePartLocation.INSTALLED,
            "Faulted original D1 is not installed");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyOpen(sim, boardProbe(sim, instance, "D1.A"), boardProbe(sim, instance, "D1.K"),
            "installed faulted D1");
        require(sim.getDiodeSlotController().removeInstalledPart(), "Could not remove original D1");
        verifyTopology(sim, instance);
        verifyOpen(sim, looseProbe(sim, instance, original, 0),
            looseProbe(sim, instance, original, 1), "loose faulted original D1");

        require(sim.getDiodeSlotController().installNewFromCatalog(DiodeReplacementCatalog.REVERSED),
            "Could not install reversed diode");
        PhysicalDiodePart reversed = state.getD1Slot().getInstalledPart();
        require(reversed.isReversedInstallation(), "Reversed catalog diode lost orientation");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(Math.abs(current(instance, "D1")) < .000001 &&
            !instance.getOperationalStates().isIlluminated("LED1") && !challenge.isCompleted() &&
            !challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "Reversed diode conducted or completed the challenge");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getDiodeSlotController().removeInstalledPart(), "Could not remove reversed diode");
        verifyHealthy(sim, instance, reversed);

        require(sim.getDiodeSlotController().installNewFromCatalog(DiodeReplacementCatalog.CORRECT),
            "Could not install healthy diode");
        PhysicalDiodePart healthy = state.getD1Slot().getInstalledPart();
        require(healthy != original && healthy != reversed &&
            !healthy.getId().equals(original.getId()) && !healthy.getId().equals(reversed.getId()),
            "Repeated diode acquisition did not create distinct physical parts");
        verifyLiftedHealthy(sim, instance, healthy);
        require(sim.getDiodeSlotController().removeInstalledPart(),
            "Could not isolate healthy diode for meter test");
        verifyHealthy(sim, instance, healthy);
        verifyOpen(sim, looseProbe(sim, instance, original, 0),
            looseProbe(sim, instance, original, 1), "original after healthy acquisition");
        require(sim.getDiodeSlotController().install(healthy.getId()),
            "Could not reinstall measured healthy diode");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double diodeCurrent = Math.abs(current(instance, "D1"));
        double resistorCurrent = Math.abs(current(instance, "R1"));
        double ledCurrent = Math.abs(current(instance, "LED1"));
        require(diodeCurrent >= .005 && diodeCurrent <= .015 &&
            Math.abs(diodeCurrent - resistorCurrent) <= .0001 &&
            Math.abs(diodeCurrent - ledCurrent) <= .0001 &&
            instance.getOperationalStates().isIlluminated("LED1") && challenge.isCompleted() &&
            challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "Healthy diode did not produce solver-backed functional completion");
        require(original.getLocation() == DiodePartLocation.LOOSE && original.isFaulted() &&
            reversed.getLocation() == DiodePartLocation.LOOSE,
            "Repair changed loose diode identities or original fault");
        verifyTopology(sim, instance);
        sim.setCircuitTitle("Diode family verification passed");
    }

    private static void verifyLiftedHealthy(CirSim sim, GeneratedBoardInstance instance,
            PhysicalDiodePart part) {
        require("default".equals(part.getElement().modelName),
            "Healthy replacement does not use the declared default silicon model");
        require(sim.getBoardModificationController().liftLead("D1", "D1.K"),
            "Could not lift healthy D1 cathode");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        ProbeTarget anode = new ComponentLeadProbeTarget(sim, instance, "D1", "D1.A", renderer);
        ProbeTarget cathode = new ComponentLeadProbeTarget(sim, instance, "D1", "D1.K", renderer);
        sim.instrumentController.setDiodeProbesForDeveloperVerification(anode, cathode);
        double voltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        require(voltage >= .45 && voltage <= .95,
            "Lifted healthy D1 did not retain its forward diode response: " + voltage);
        sim.instrumentController.setDiodeProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Lifted healthy D1 reverse response was not OL");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(sim.getBoardModificationController().reconnectLead("D1", "D1.K"),
            "Could not reconnect healthy D1 cathode");
    }

    private static void verifyHealthy(CirSim sim, GeneratedBoardInstance instance,
            PhysicalDiodePart part) {
        ProbeTarget anode = looseProbe(sim, instance, part, 0);
        ProbeTarget cathode = looseProbe(sim, instance, part, 1);
        sim.instrumentController.setDiodeProbesForDeveloperVerification(anode, cathode);
        double voltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        double current = sim.instrumentController.getLatestDiodeCurrentForDeveloperVerification();
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            voltage >= .45 && voltage <= .95 && current >= InstrumentController.DIODE_MINIMUM_CURRENT,
            "Healthy loose diode forward test was not solver-backed: " + voltage + " V, " + current + " A");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Healthy loose diode reverse test was not OL");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyOpen(CirSim sim, ProbeTarget first, ProbeTarget second, String label) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(first, second);
        String forward = sim.instrumentController.getReadingForDeveloperVerification();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(second, first);
        String reverse = sim.instrumentController.getReadingForDeveloperVerification();
        require("OL".equals(forward) && "OL".equals(reverse), label + " was not OL both ways");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static ProbeTarget looseProbe(CirSim sim, GeneratedBoardInstance instance,
            PhysicalDiodePart part, int terminal) {
        return new PhysicalDiodePartProbeTarget(sim, instance, part.getId(), terminal,
            sim.pcbWorkbenchController.getRenderer());
    }

    private static ProbeTarget boardProbe(CirSim sim, GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return new CircuitPostProbeTarget(sim, endpoint.getElement(), endpoint.getPostIndex());
    }

    private static double current(GeneratedBoardInstance instance, String componentId) {
        return instance.getComponentBindings().getSingleElement(componentId).getCurrent();
    }

    private static void verifyTopology(CirSim sim, GeneratedBoardInstance instance) {
        DiodeProtectedIndicatorFamilyState state =
            DiodeProtectedIndicatorFamilyState.require(instance);
        int installed = 0;
        for (PhysicalDiodePart part : state.getInventory().getAll()) {
            if (part.getLocation() == DiodePartLocation.INSTALLED)
                installed++;
            for (CircuitElm element : part.getBackingElements())
                require(count(sim.elmList, element) == 1,
                    "Diode backing missing or duplicated: " + part.getId());
        }
        require((state.getD1Slot().isEmpty() && installed == 0) ||
            (!state.getD1Slot().isEmpty() && installed == 1),
            "Diode slot and physical locations disagree");
    }

    private static int count(Vector<CircuitElm> elements, CircuitElm target) {
        int count = 0;
        for (CircuitElm element : elements)
            if (element == target)
                count++;
        return count;
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        for (int index = 0; index < 8; index++)
            sim.runCircuit(true);
        sim.verifyGeneratedBoard();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
