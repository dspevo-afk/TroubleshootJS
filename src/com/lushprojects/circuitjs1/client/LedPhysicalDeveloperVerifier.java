package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class LedPhysicalDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && "LED_INDICATOR".equals(instance.getCircuitFamilyId()) &&
            challenge != null && challenge.isReady(),
            "LED physical verification requires a ready LED challenge");
        WorkbenchCapabilityDeveloperVerifier.verifyRegisteredProviders(sim);
        ReplaceableLedBoardCapability state = ReplaceableLedBoardCapability.require(instance);
        LedSlotController leds = sim.getLedSlotController();
        PhysicalLedPart original = state.getInventory().get("LED1_ORIGINAL");
        int catalogSize = state.getCatalog().getEntries().size();
        Vector<LedCatalogEntry> catalogEntries = state.getCatalog().getEntries();
        require(state.getInventory().getAll().size() == 1 &&
            state.getInventory().getLooseParts().isEmpty() &&
            state.getSlot().getInstalledPart() == original &&
            original.getLocation() == LedPartLocation.INSTALLED,
            "Initial LED1 physical state is incorrect");
        verifyPartTopology(sim, instance);

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyHealthyDiode(sim, boardProbe(sim, instance, "LED1.A"),
            boardProbe(sim, instance, "LED1.K"), "installed original LED1");
        require(leds.removeInstalledPart(), "Could not remove original LED1");
        require(state.getSlot().isEmpty() && state.getInventory().getLooseParts().size() == 1 &&
            state.getInventory().getLooseParts().get(0) == original,
            "Removing LED1 did not preserve its physical identity");
        verifyHealthyDiode(sim, looseProbe(sim, instance, original, 0),
            looseProbe(sim, instance, original, 1), "loose original LED1");
        verifyPartTopology(sim, instance);

        PhysicalResistorPart replacementResistor = replaceR1WithHealthyPart(sim, instance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(!challenge.isCompleted() && !instance.getOperationalStates().isIlluminated("LED1"),
            "Correct R1 completed repair while LED1 was missing");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);

        LedCatalogEntry reversedCatalog = state.getCatalog().get(LedReplacementCatalog.REVERSED);
        require(leds.installNewFromCatalog(reversedCatalog.getId()),
            "Could not acquire reversed LED replacement");
        PhysicalLedPart reversed = state.getSlot().getInstalledPart();
        verifyCatalogAcquisition(reversed, reversedCatalog);
        require(reversed != original && reversed.isReversedInstallation(),
            "Reversed LED acquisition lost identity or polarity");
        verifyRuntimeAllocatedIdentities(instance, replacementResistor, reversed);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(!challenge.isCompleted() && !instance.getOperationalStates().isIlluminated("LED1") &&
            Math.abs(reversed.getElement().getCurrent()) < .000001,
            "Reversed LED behaved as a repaired forward indicator");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(leds.removeInstalledPart(), "Could not remove reversed LED");
        verifyHealthyDiode(sim, looseProbe(sim, instance, reversed, 0),
            looseProbe(sim, instance, reversed, 1), "loose reversed-installation LED");

        LedCatalogEntry correctCatalog = state.getCatalog().get(LedReplacementCatalog.CORRECT);
        require(leds.installNewFromCatalog(correctCatalog.getId()),
            "Could not acquire correct LED replacement");
        PhysicalLedPart healthy = state.getSlot().getInstalledPart();
        verifyCatalogAcquisition(healthy, correctCatalog);
        require(healthy != original && healthy != reversed &&
            !healthy.getId().equals(original.getId()) && !healthy.getId().equals(reversed.getId()) &&
            healthy.getElement() != original.getElement() && healthy.getElement() != reversed.getElement(),
            "Repeated LED acquisition reused physical identity or backing LEDElm");
        require(leds.removeInstalledPart(), "Could not isolate correct LED replacement");
        verifyHealthyDiode(sim, looseProbe(sim, instance, healthy, 0),
            looseProbe(sim, instance, healthy, 1), "loose correct LED replacement");
        require(leds.install(healthy.getId()), "Could not reinstall measured LED replacement");
        verifyCatalogAcquisition(healthy, correctCatalog);
        require(leds.removeInstalledPart(),
            "Could not remove healthy LED before repeated catalog acquisition");
        require(leds.installNewFromCatalog(correctCatalog.getId()),
            "Could not acquire the healthy LED catalog entry twice");
        PhysicalLedPart secondHealthy = state.getSlot().getInstalledPart();
        verifyCatalogAcquisition(secondHealthy, correctCatalog);
        PhysicalCatalogAcquisitionDeveloperVerifier.verifySameSpecification(healthy, secondHealthy);
        require(leds.removeInstalledPart(), "Could not remove second healthy LED acquisition");
        verifyHealthyDiode(sim, looseProbe(sim, instance, secondHealthy, 0),
            looseProbe(sim, instance, secondHealthy, 1), "loose second correct LED replacement");
        require(leds.install(secondHealthy.getId()),
            "Could not reinstall second correct LED acquisition");
        verifyCatalogAcquisition(secondHealthy, correctCatalog);
        require(sim.pcbWorkbenchController.getRenderer().getLooseTerminalPoint(
                secondHealthy.getId(), 0) == null,
            "Installed LED was exposed as a loose tray target");
        require(state.getCatalog().getEntries().size() == catalogSize,
            "LED catalog depleted or changed after acquisition");
        require(state.getCatalog().getEntries().equals(catalogEntries),
            "LED catalog entries changed after acquisition");
        verifyPartTopology(sim, instance);

        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double ledCurrent = Math.abs(secondHealthy.getElement().getCurrent());
        require(ledCurrent >= .005 && ledCurrent <= .015 &&
            instance.getOperationalStates().isIlluminated("LED1") && challenge.isCompleted(),
            "Correct physical LED did not restore solved operation and challenge completion");
        require(original.getLocation() == LedPartLocation.LOOSE &&
            reversed.getLocation() == LedPartLocation.LOOSE &&
            healthy.getLocation() == LedPartLocation.LOOSE &&
            state.getInventory().getLooseParts().size() == 3,
            "Installed and loose LED inventory locations disagree");
        sim.setCircuitTitle("LED physical verification passed");
    }

    private static PhysicalResistorPart replaceR1WithHealthyPart(CirSim sim,
            GeneratedBoardInstance instance) {
        ResistorSlotController resistors = sim.getResistorSlotController();
        require(resistors.removeInstalledPart(), "Could not remove failed R1 before LED checks");
        double expected = StandardPhysicalDefinitionProviders.RESISTOR.require(
            instance.getPhysicalSpecifications(), "R1").getNominalResistanceOhms();
        String catalogId = null;
        for (ResistorCatalogEntry entry : ReplaceableResistorBoardCapability.require(instance)
                .getCatalog().getEntries())
            if (Math.abs(entry.getNameplate().getNominalResistanceOhms() - expected) < .001)
                catalogId = entry.getId();
        require(catalogId != null && resistors.installNewFromCatalog(catalogId),
            "Could not install correct R1 before LED checks");
        return ReplaceableResistorBoardCapability.require(instance).getSlot().getInstalledPart();
    }

    private static void verifyRuntimeAllocatedIdentities(GeneratedBoardInstance instance,
            PhysicalPart<?> resistor, PhysicalPart<?> led) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        WorkbenchPartsProvider resistorProvider =
            runtime.getWorkbenchPartsProviderForPart(resistor.getId());
        WorkbenchPartsProvider ledProvider = runtime.getWorkbenchPartsProviderForPart(led.getId());
        require("R1_CATALOG_PART_0".equals(resistor.getId()) &&
                "LED1_CATALOG_PART_0".equals(led.getId()) && !resistor.getId().equals(led.getId()),
            "Runtime allocator did not preserve deterministic provider namespaces");
        require(resistorProvider != null && ledProvider != null &&
                resistorProvider != ledProvider && resistorProvider.ownsPart(resistor.getId()) &&
                ledProvider.ownsPart(led.getId()),
            "Different provider views did not retain their runtime-allocated parts");
        require(runtime.getPart(resistor.getId()) == resistor &&
                runtime.getPart(resistor.getId()) == resistor &&
                runtime.getPart(led.getId()) == led && runtime.getPart(led.getId()) == led &&
                resistorProvider.getPart(resistor.getId()) == resistor &&
                ledProvider.getPart(led.getId()) == led,
            "Runtime-allocated physical identities were not registered and stable");
    }

    private static void verifyCatalogAcquisition(PhysicalLedPart part, LedCatalogEntry entry) {
        PhysicalCatalogAcquisitionDeveloperVerifier.verify(part, entry, "LED1");
        require(part.getNameplate() == entry.getSpecification() &&
                "default-led".equals(part.getNameplate().getModelName()),
            "LED acquisition discarded selected technical specification identity");
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
        ReplaceableLedBoardCapability state = ReplaceableLedBoardCapability.require(instance);
        int installed = 0;
        for (PhysicalLedPart part : state.getInventory().getAll()) {
            if (part.getLocation() == LedPartLocation.INSTALLED) installed++;
            Point anode = part.getElement().getPost(0);
            Point cathode = part.getElement().getPost(1);
            require(part.getTerminal(0) != part.getTerminal(1) &&
                (anode.x != cathode.x || anode.y != cathode.y),
                "LED measurement endpoints were not distinct: " + part.getId());
            for (CircuitElm element : part.getElectricalBacking().getCircuitElements())
                require(count(sim.elmList, element) == 1,
                    "LED backing missing or duplicated: " + part.getId());
        }
        Vector<PhysicalLedPart> parts = state.getInventory().getAll();
        for (int left = 0; left < parts.size(); left++)
            for (int right = left + 1; right < parts.size(); right++)
                require(parts.get(left).getElement() != parts.get(right).getElement(),
                    "Physical LEDs shared a backing LEDElm");
        require((state.getSlot().isEmpty() && installed == 0) ||
            (!state.getSlot().isEmpty() && installed == 1 &&
                state.getSlot().getInstalledPart().getLocation() == LedPartLocation.INSTALLED),
            "LED slot and physical locations disagree");
        if (!state.getSlot().isEmpty())
            require(instance.getComponentBindings().getSingleElement("LED1") ==
                state.getSlot().getInstalledPart().getElement(),
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
