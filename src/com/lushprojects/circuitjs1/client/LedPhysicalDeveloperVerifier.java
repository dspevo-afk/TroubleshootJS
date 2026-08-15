package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class LedPhysicalDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && "LED_INDICATOR".equals(instance.getCircuitFamilyId()) &&
            challenge != null && challenge.isReady(),
            "LED physical verification requires a ready LED challenge");
        LedIndicatorFamilyState state = LedIndicatorFamilyState.require(instance);
        LedSlotController leds = sim.getLedSlotController();
        PhysicalLedPart original = state.getLedInventory().get("LED1_ORIGINAL");
        int catalogSize = state.getLedCatalog().getEntries().size();
        Vector<LedCatalogEntry> catalogEntries = state.getLedCatalog().getEntries();
        require(state.getLedInventory().getAll().size() == 1 &&
            state.getLedInventory().getLooseParts().isEmpty() &&
            state.getLed1Slot().getInstalledPart() == original &&
            original.getLocation() == LedPartLocation.INSTALLED,
            "Initial LED1 physical state is incorrect");
        verifyPartTopology(sim, instance);

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyHealthyDiode(sim, boardProbe(sim, instance, "LED1.A"),
            boardProbe(sim, instance, "LED1.K"), "installed original LED1");
        require(leds.removeInstalledPart(), "Could not remove original LED1");
        require(state.getLed1Slot().isEmpty() && state.getLedInventory().getLooseParts().size() == 1 &&
            state.getLedInventory().getLooseParts().get(0) == original,
            "Removing LED1 did not preserve its physical identity");
        verifyHealthyDiode(sim, looseProbe(sim, instance, original, 0),
            looseProbe(sim, instance, original, 1), "loose original LED1");
        verifyPartTopology(sim, instance);

        replaceR1WithHealthyPart(sim, instance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(!challenge.isCompleted() && !instance.getOperationalStates().isIlluminated("LED1"),
            "Correct R1 completed repair while LED1 was missing");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);

        require(leds.installNewFromCatalog(LedReplacementCatalog.REVERSED),
            "Could not acquire reversed LED replacement");
        PhysicalLedPart reversed = state.getLed1Slot().getInstalledPart();
        require(reversed != original && reversed.isReversedInstallation(),
            "Reversed LED acquisition lost identity or polarity");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(!challenge.isCompleted() && !instance.getOperationalStates().isIlluminated("LED1") &&
            Math.abs(reversed.getElement().getCurrent()) < .000001,
            "Reversed LED behaved as a repaired forward indicator");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(leds.removeInstalledPart(), "Could not remove reversed LED");
        verifyHealthyDiode(sim, looseProbe(sim, instance, reversed, 0),
            looseProbe(sim, instance, reversed, 1), "loose reversed-installation LED");

        require(leds.installNewFromCatalog(LedReplacementCatalog.CORRECT),
            "Could not acquire correct LED replacement");
        PhysicalLedPart healthy = state.getLed1Slot().getInstalledPart();
        require(healthy != original && healthy != reversed &&
            !healthy.getId().equals(original.getId()) && !healthy.getId().equals(reversed.getId()) &&
            healthy.getElement() != original.getElement() && healthy.getElement() != reversed.getElement(),
            "Repeated LED acquisition reused physical identity or backing LEDElm");
        require(leds.removeInstalledPart(), "Could not isolate correct LED replacement");
        verifyHealthyDiode(sim, looseProbe(sim, instance, healthy, 0),
            looseProbe(sim, instance, healthy, 1), "loose correct LED replacement");
        require(leds.install(healthy.getId()), "Could not reinstall measured LED replacement");
        require(sim.pcbWorkbenchController.getRenderer().getLooseLedLeadPoint(healthy.getId(), 0) == null,
            "Installed LED was exposed as a loose tray target");
        require(state.getLedCatalog().getEntries().size() == catalogSize,
            "LED catalog depleted or changed after acquisition");
        require(state.getLedCatalog().getEntries().equals(catalogEntries),
            "LED catalog entries changed after acquisition");
        verifyPartTopology(sim, instance);

        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double ledCurrent = Math.abs(healthy.getElement().getCurrent());
        require(ledCurrent >= .005 && ledCurrent <= .015 &&
            instance.getOperationalStates().isIlluminated("LED1") && challenge.isCompleted(),
            "Correct physical LED did not restore solved operation and challenge completion");
        require(original.getLocation() == LedPartLocation.LOOSE &&
            reversed.getLocation() == LedPartLocation.LOOSE &&
            state.getLedInventory().getLooseParts().size() == 2,
            "Installed and loose LED inventory locations disagree");
        sim.setCircuitTitle("LED physical verification passed");
    }

    private static void replaceR1WithHealthyPart(CirSim sim, GeneratedBoardInstance instance) {
        ResistorSlotController resistors = sim.getResistorSlotController();
        require(resistors.removeInstalledPart(), "Could not remove failed R1 before LED checks");
        double expected = instance.getPhysicalSpecifications().getResistorNameplate("R1")
            .getNominalResistanceOhms();
        String catalogId = null;
        for (ResistorCatalogEntry entry : LedIndicatorFamilyState.require(instance)
                .getResistorCatalog().getEntries())
            if (Math.abs(entry.getNameplate().getNominalResistanceOhms() - expected) < .001)
                catalogId = entry.getId();
        require(catalogId != null && resistors.installNewFromCatalog(catalogId),
            "Could not install correct R1 before LED checks");
    }

    private static void verifyHealthyDiode(CirSim sim, ProbeTarget anode, ProbeTarget cathode,
            String label) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(anode, cathode);
        double voltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        double current = sim.instrumentController.getLatestDiodeCurrentForDeveloperVerification();
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            voltage >= 1.2 && voltage < InstrumentController.DIODE_COMPLIANCE_THRESHOLD &&
            current >= InstrumentController.DIODE_MINIMUM_CURRENT,
            label + " forward result was not solver-derived: " + voltage + " V, " + current + " A");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            label + " reverse result was not OL");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static ProbeTarget boardProbe(CirSim sim, GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return new CircuitPostProbeTarget(sim, endpoint.getElement(), endpoint.getPostIndex());
    }

    private static ProbeTarget looseProbe(CirSim sim, GeneratedBoardInstance instance,
            PhysicalLedPart part, int terminal) {
        return new PhysicalLedPartProbeTarget(sim, instance, part.getId(), terminal,
            sim.pcbWorkbenchController.getRenderer());
    }

    private static void verifyPartTopology(CirSim sim, GeneratedBoardInstance instance) {
        LedIndicatorFamilyState state = LedIndicatorFamilyState.require(instance);
        int installed = 0;
        for (PhysicalLedPart part : state.getLedInventory().getAll()) {
            if (part.getLocation() == LedPartLocation.INSTALLED) installed++;
            Point anode = part.getElement().getPost(0);
            Point cathode = part.getElement().getPost(1);
            require(part.getTerminal(0) != part.getTerminal(1) &&
                (anode.x != cathode.x || anode.y != cathode.y),
                "LED measurement endpoints were not distinct: " + part.getId());
            for (CircuitElm element : part.getBackingElements())
                require(count(sim.elmList, element) == 1,
                    "LED backing missing or duplicated: " + part.getId());
        }
        Vector<PhysicalLedPart> parts = state.getLedInventory().getAll();
        for (int left = 0; left < parts.size(); left++)
            for (int right = left + 1; right < parts.size(); right++)
                require(parts.get(left).getElement() != parts.get(right).getElement(),
                    "Physical LEDs shared a backing LEDElm");
        require((state.getLed1Slot().isEmpty() && installed == 0) ||
            (!state.getLed1Slot().isEmpty() && installed == 1 &&
                state.getLed1Slot().getInstalledPart().getLocation() == LedPartLocation.INSTALLED),
            "LED slot and physical locations disagree");
        if (!state.getLed1Slot().isEmpty())
            require(instance.getComponentBindings().getSingleElement("LED1") ==
                state.getLed1Slot().getInstalledPart().getElement(),
                "LED component binding does not follow the installed physical part");
    }

    private static int count(Vector<CircuitElm> elements, CircuitElm target) {
        int count = 0;
        for (CircuitElm element : elements) if (element == target) count++;
        return count;
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        for (int index = 0; index < 8; index++) sim.runCircuit(true);
        sim.verifyGeneratedBoard();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
