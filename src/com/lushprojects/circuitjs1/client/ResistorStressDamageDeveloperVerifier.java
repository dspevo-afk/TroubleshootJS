package com.lushprojects.circuitjs1.client;

/** Deterministic developer proof for the bounded Task 34 resistor target. */
class ResistorStressDamageDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady(),
            "Stress verification requires a ready challenge");
        require(instance.getCircuitFamilyId().equals("LED_INDICATOR") && instance.getSeed() == 3,
            "Stress verification requires LED seed 3");
        require("R1".equals(instance.getFaultBinding().getFault().getTargetComponentId()),
            "Stress verification requires the original R1 fault: " +
            instance.getFaultBinding().getFault().getId() + "/" +
            instance.getFaultBinding().getFault().getType());

        LedIndicatorFamilyState family = LedIndicatorFamilyState.require(instance);
        ResistorSlotController slots = sim.getResistorSlotController();
        ResistorStressDamageSystem damage = sim.getResistorStressDamageSystem();
        PhysicalResistorPart original = family.getResistorInventory().get("R1_ORIGINAL");
        String originalId = original.getId();
        GeneratedFaultBinding originalFault = instance.getFaultBinding();
        StringBuilder report = new StringBuilder();
        challenge.beginDeveloperVerificationScope();
        try {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle(sim);
            require(slots.removeInstalledPart(), "Could not remove original for stress proof");

            ResistorCatalogEntry severeCatalog = family.getResistorCatalog().get("R_CATALOG_220");
            require(slots.installNewFromCatalog(severeCatalog.getId()),
                "Could not install severe lower-value replacement");
            PhysicalResistorPart severe = family.getR1Slot().getInstalledPart();
            require(severe != null && severe != original && severe.getId().equals("R1_CATALOG_PART_0"),
                "Severe replacement did not acquire a distinct physical identity");
            requireAuxiliaryBinding(instance, severe, "catalog severe install");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            damage.refreshSolvedMeasurements();
            ResistorStressState severeState = damage.getState(severe.getId());
            require(severe.getElement().getResistance() == 220,
                "Severe replacement was not backed by its catalog resistance");
            require(severeState.getActualPower() > severe.getRatedWattage() * 1.5 &&
                getLedCurrent(instance) > .001,
                "Severe replacement did not initially operate above its solver-derived rating");
            double severePower = severeState.getActualPower();
            double severeRatio = severeState.getStressRatio();
            double severeDamageBeforePause = severeState.getAccumulatedDamage();
            double severeServiceBeforePause = severeState.getServiceTime();
            sim.advanceResistorServiceTimeForDeveloperVerification(.5);
            double severeDamageAfterPowered = severeState.getAccumulatedDamage();
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle(sim);
            sim.advanceResistorServiceTimeForDeveloperVerification(5);
            require(severeState.getAccumulatedDamage() == severeDamageAfterPowered &&
                severeState.getServiceTime() == severeServiceBeforePause + .5,
                "Powered-off service time changed resistor damage or service time");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            sim.advanceResistorServiceTimeForDeveloperVerification(3);
            require(severeState.isFailed() && severe.getSecondaryOpenPath().isOpen(),
                "Severe overload did not produce the owned resistor open failure");
            settle(sim);
            damage.refreshSolvedMeasurements();
            double postFailureCurrent = getLedCurrent(instance);
            boolean postFailureIlluminated = instance.getOperationalStates().isIlluminated("LED1");
            require(severeState.getFailureServiceTime() > 0 && postFailureCurrent < .000001 &&
                !postFailureIlluminated && severe.getId().equals("R1_CATALOG_PART_0") &&
                original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted() &&
                instance.getFaultBinding() == originalFault,
                "Secondary failure did not preserve identity/original-fault ownership or LED behavior");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(slots.removeInstalledPart() && severe.getLocation() == ResistorPartLocation.LOOSE &&
                severe.getSecondaryOpenPath().isOpen(),
                "Failed resistor could not be removed without healing its secondary open");
            require(slots.install(severe.getId()),
                "Failed resistor could not be reinstalled as the same physical part");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            damage.refreshSolvedMeasurements();
            require(severeState.isFailed() && severe.getSecondaryOpenPath().isOpen() &&
                getLedCurrent(instance) < .000001,
                "Removing and reinstalling the failed resistor healed its secondary open");
            requireAuxiliaryBinding(instance, severe, "failed resistor reinstall");
            report.append("B{id=").append(severe.getId()).append(",R=").append(
                severe.getElement().getResistance()).append(",ratedW=").append(
                severe.getRatedWattage()).append(",solvedW=").append(severePower).append(
                ",ratio=").append(severeRatio).append(",damage=").append(
                severeState.getAccumulatedDamage()).append(",service=").append(
                severeState.getServiceTime()).append(",failureTime=").append(
                severeState.getFailureServiceTime()).append(",postCurrent=").append(
                postFailureCurrent).append(",illuminated=").append(postFailureIlluminated).append(
                ",reinstallOpen=true,auxiliaryBinding=true").append(
                ",original=").append(originalId).append("/faultOwned=").append(
                original.isFaulted()).append("/fault=").append(originalFault.getFault().getId()).append(
                "/").append(originalFault.getFault().getType()).append("}");

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.resetAction();
            settle(sim);
            require(severeState.getAccumulatedDamage() == 0 && !severeState.isFailed() &&
                !severe.getSecondaryOpenPath().isOpen() && original.isFaulted(),
                "Reset did not clear failed-part secondary damage while preserving original fault");
            require(slots.removeInstalledPart() && severe.getLocation() == ResistorPartLocation.LOOSE &&
                !severe.getSecondaryOpenPath().isOpen(),
                "Failed severe part could not be removed");
            require(slots.install(original.getId()) && original.getLocation() == ResistorPartLocation.INSTALLED,
                "Catalog-to-original installation did not restore the original physical part");
            requireAuxiliaryBinding(instance, original, "catalog-to-original install");
            require(!instance.getComponentBindings().isElementBoundToComponent("R1",
                severe.getSecondaryOpenPath().getSimulationElement()),
                "Catalog-to-original installation retained stale catalog auxiliary binding");
            require(slots.removeInstalledPart() && original.getLocation() == ResistorPartLocation.LOOSE,
                "Original part could not be removed after catalog-to-original binding check");
            require(slots.installNewFromCatalog("R_CATALOG_330"),
                "Could not install modest-overload catalog replacement");
            PhysicalResistorPart mild = family.getR1Slot().getInstalledPart();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            damage.refreshSolvedMeasurements();
            ResistorStressState mildState = damage.getState(mild.getId());
            require(mildState.getActualPower() > mild.getRatedWattage() &&
                mildState.getStressRatio() < 1.5 && getLedCurrent(instance) > .001,
                "Mild catalog case was not classified from modest solved power overload: P=" +
                mildState.getActualPower() + " rated=" + mild.getRatedWattage() + " ratio=" +
                mildState.getStressRatio() + " current=" + getLedCurrent(instance));
            double mildDamageBeforeMeter = mildState.getAccumulatedDamage();
            double mildServiceBeforeMeter = mildState.getServiceTime();
            CircuitPostMeasurementEndpoint vin = getPost(instance, "J1.1");
            CircuitPostMeasurementEndpoint ground = getPost(instance, "J1.2");
            double meterVoltage = sim.measureDcVoltage(vin, ground);
            require(!Double.isNaN(meterVoltage) && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "Powered DC meter stimulus did not complete cleanly");
            require(mildState.getAccumulatedDamage() == mildDamageBeforeMeter &&
                mildState.getServiceTime() == mildServiceBeforeMeter,
                "Active meter stimulus advanced persistent resistor damage");
            sim.advanceResistorServiceTimeForDeveloperVerification(10);
            require(!mildState.isFailed() && mildState.getAccumulatedDamage() < 1,
                "Mild overload failed inside the bounded safe service window");
            report.append(";C{id=").append(mild.getId()).append(",R=").append(
                mild.getElement().getResistance()).append(",ratedW=").append(mild.getRatedWattage()).append(
                ",solvedW=").append(mildState.getActualPower()).append(",ratio=").append(
                mildState.getStressRatio()).append(",damage=").append(mildState.getAccumulatedDamage()).append(
                ",service=").append(mildState.getServiceTime()).append(",survived=true,meterV=").append(
                meterVoltage).append(",meterDamageUnchanged=true}");

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(slots.removeInstalledPart(), "Could not remove mild part before correct replacement");
            require(slots.installNewFromCatalog("R_CATALOG_1000"),
                "Could not install correct catalog replacement");
            PhysicalResistorPart correct = family.getR1Slot().getInstalledPart();
            String correctId = correct.getId();
            ResistorElm correctElement = correct.getElement();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            damage.refreshSolvedMeasurements();
            ResistorStressState correctState = damage.getState(correctId);
            require(correct.getElement().getResistance() == 1000 &&
                correctState.getActualPower() <= correct.getRatedWattage() &&
                getLedCurrent(instance) >= .005 && getLedCurrent(instance) <= .015 &&
                !correctState.isFailed(),
                "Correct replacement was not inside its solver-derived power rating");
            double correctPower = correctState.getActualPower();
            double correctRatio = correctState.getStressRatio();
            sim.advanceResistorServiceTimeForDeveloperVerification(10);
            require(correctState.getPart() == correct && correct.getElement() == correctElement &&
                !correctState.isFailed() && !correct.getSecondaryOpenPath().isOpen() &&
                instance.getOperationalStates().isIlluminated("LED1"),
                "Correct replacement did not survive the same bounded service window");
            challenge.endDeveloperVerificationScope();
            challenge.verifyReadyState();
            require(challenge.isCompleted(), "Correct replacement did not complete the solver-backed repair: current=" +
                getLedCurrent(instance) + " status=" + challenge.getDefinition().getBehaviorContract()
                    .getRepairStatus(instance, sim.getBoardModificationController(), BoardPowerState.POWERED, false));
            report.append(";A{id=").append(correctId).append(",R=").append(
                correct.getElement().getResistance()).append(",ratedW=").append(correct.getRatedWattage()).append(
                ",solvedW=").append(correctPower).append(",ratio=").append(correctRatio).append(
                ",damage=").append(correctState.getAccumulatedDamage()).append(",service=").append(
                correctState.getServiceTime()).append(",survived=true,backingStable=true,led=true}");

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.resetAction();
            settle(sim);
            require(correctState.getAccumulatedDamage() == 0 && !correctState.isFailed() &&
                !correct.getSecondaryOpenPath().isOpen() && original.isFaulted() &&
                original.getLocation() == ResistorPartLocation.LOOSE &&
                correct.getRatedWattage() == family.getResistorCatalog().get("R_CATALOG_1000")
                    .getNameplate().getRatedWattage(),
                "Reset did not deterministically clear secondary damage while preserving original fault");
            report.append(";D{poweredOffPaused=true,activeMeterPaused=true};F{resetDamage=0,failedPartReset=true,catalogToOriginalAuxiliary=true,originalFaultOwned=true,ratingsReproduced=true}");
        } finally {
            challenge.endDeveloperVerificationScope();
        }
        String result = report.toString();
        System.out.println("TASK34_STRESS_REPORT " + result);
        sim.publishStressReportForDeveloperVerification(result);
    }

    private static CircuitPostMeasurementEndpoint getPost(GeneratedBoardInstance instance, String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Stress proof requires post endpoint: " + padId);
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static double getLedCurrent(GeneratedBoardInstance instance) {
        return Math.abs(((LEDElm) instance.getComponentBindings().getSingleElement("LED1")).getCurrent());
    }

    private static void requireAuxiliaryBinding(GeneratedBoardInstance instance,
            PhysicalResistorPart part, String context) {
        require(instance.getComponentBindings().isElementBoundToComponent("R1",
            part.getSecondaryOpenPath().getSimulationElement()),
            "Auxiliary resistor binding did not follow installed part: " + context + "/" + part.getId());
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.getResistorStressDamageSystem().refreshSolvedMeasurements();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
