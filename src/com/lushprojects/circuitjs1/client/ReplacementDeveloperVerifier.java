package com.lushprojects.circuitjs1.client;

class ReplacementDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady(),
            "Replacement verification requires a ready challenge");
        verifyInventoryMetadata();
        ResistorSlotController slots = sim.getResistorSlotController();
        PhysicalResistorPart original = instance.getResistorInventory().get("R1_ORIGINAL");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && instance.getR1Slot().isEmpty() &&
            original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Removing original failed R1 did not empty slot and preserve fault");
        verifyResistance(sim, instance, original, true);
        require(slots.install(original.getId()), "Original failed R1 did not reinstall");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(!challenge.isCompleted() && !instance.getOperationalStates().isIlluminated("LED1"),
            "Reinstalled failed R1 unexpectedly repaired challenge");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        slots.removeInstalledPart();
        verifyReplacement(sim, instance, challenge, slots, "R1_REPLACEMENT_0", false);
        verifyReplacement(sim, instance, challenge, slots, "R1_REPLACEMENT_2", false);
        require(slots.install("R1_REPLACEMENT_1"), "Correct replacement did not install");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(challenge.isCompleted() && instance.getOperationalStates().isIlluminated("LED1"),
            "Correct replacement did not complete solved repair");
        require(original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Completion altered original failed part");
        sim.setCircuitTitle("Replacement verification passed");
    }

    private static void verifyReplacement(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, ResistorSlotController slots, String partId,
            boolean expectedCompletion) {
        PhysicalResistorPart part = instance.getResistorInventory().get(partId);
        verifyResistance(sim, instance, part, false);
        require(slots.install(partId), "Replacement did not install: " + partId);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double current = Math.abs(((LEDElm) instance.getComponentBindings().getSingleElement("LED1"))
            .getCurrent());
        require((current >= .005 && current <= .015) == expectedCompletion,
            "Unexpected functional current for " + partId + ": " + current);
        require(challenge.isCompleted() == expectedCompletion,
            "Unexpected completion state for " + partId);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Replacement did not remove: " + partId);
    }

    private static void verifyResistance(CirSim sim, GeneratedBoardInstance instance,
            PhysicalResistorPart part, boolean expectedOpen) {
        ProbeTarget first = new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), 0,
            sim.pcbWorkbenchController.getRenderer());
        ProbeTarget second = new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), 1,
            sim.pcbWorkbenchController.getRenderer());
        sim.instrumentController.setResistanceProbesForDeveloperVerification(first, second);
        String forward = sim.instrumentController.getReadingForDeveloperVerification();
        sim.instrumentController.setResistanceProbesForDeveloperVerification(second, first);
        String reverse = sim.instrumentController.getReadingForDeveloperVerification();
        if (expectedOpen)
            require("OL".equals(forward) && "OL".equals(reverse), "Failed original was not OL");
        else
            require(!"OL".equals(forward) && !"OL".equals(reverse),
                "Healthy replacement was not measurable: " + part.getId());
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyInventoryMetadata() {
        verifySeed(0, new double[] { 100, 330, 4700 });
        verifySeed(2, new double[] { 220, 680, 10000 });
        verifySeed(3, new double[] { 330, 1000, 15000 });
    }

    private static void verifySeed(long seed, double[] expected) {
        GeneratedBoardInstance instance = new LedIndicatorGenerator().generate(seed);
        for (int index = 0; index < expected.length; index++) {
            PhysicalResistorPart part = instance.getResistorInventory().get("R1_REPLACEMENT_" + index);
            require(part.getLocation() == ResistorPartLocation.LOOSE &&
                part.getNameplate().getNominalResistanceOhms() == expected[index] &&
                ResistorColorCode.getFourBandCode(part.getNameplate()).length == 4,
                "Invalid deterministic inventory for seed " + seed);
        }
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.verifyGeneratedBoard();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}