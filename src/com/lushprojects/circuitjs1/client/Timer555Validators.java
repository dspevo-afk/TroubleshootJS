package com.lushprojects.circuitjs1.client;

/** Healthy-graph topology check; the temporal profile performs the timing proof. */
final class Timer555GeneratedBoardValidator implements GeneratedBoardValidator {
    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        CircuitElm timer = instance.getComponentBindings().getSingleElement("U1");
        CircuitElm c1 = instance.getComponentBindings().getSingleElement("C1");
        if (!(timer instanceof TimerElm) || c1 == null || c1.getClass() != CapacitorElm.class ||
                instance.getComponentBindings().getSingleElement("RA") == null ||
                instance.getComponentBindings().getSingleElement("R3") == null ||
                instance.getComponentBindings().getSingleElement("LED1") == null)
            throw new IllegalStateException("Timer board is missing its real timing elements");
        boolean freeRunning = Timer555Generator.FREE_RUNNING_VARIANT.equals(instance.getTopologyVariantId());
        requirePadNet(instance, "U1.VCC", "VCC");
        requirePadNet(instance, "U1.RESET", "VCC");
        requirePadNet(instance, "U1.GND", "GND");
        requirePadNet(instance, "U1.THRES", "TIMING");
        requirePadNet(instance, "U1.OUT", "OUT");
        requirePadNet(instance, "C1.+", "TIMING");
        requirePadNet(instance, "C1.-", "GND");
        requirePadNet(instance, "RA.1", "VCC");
        if (freeRunning) {
            requirePadNet(instance, "U1.TRIG", "TIMING");
            requirePadNet(instance, "U1.DIS", "DISCHARGE");
            requirePadNet(instance, "RA.2", "DISCHARGE");
            requirePadNet(instance, "RB.1", "DISCHARGE");
            requirePadNet(instance, "RB.2", "TIMING");
        } else {
            requirePadNet(instance, "U1.TRIG", "TRIGGER");
            requirePadNet(instance, "U1.DIS", "TIMING");
            requirePadNet(instance, "RA.2", "TIMING");
            requirePadNet(instance, "R4.2", "TRIGGER");
            requirePadNet(instance, "J2.1", "TRIGGER");
        }
        for (String netId : instance.getBoard().getNetIds())
            for (CircuitMeasurementEndpoint endpoint :
                    instance.getSimulationBindings().getEndpointsForNet(netId)) {
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                double voltage = post.getElement().getPostVoltage(post.getPostIndex());
                if (Double.isNaN(voltage) || Double.isInfinite(voltage))
                    throw new IllegalStateException("Timer board has non-finite net " + netId);
            }
    }

    private void requirePadNet(GeneratedBoardInstance instance, String padId, String netId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw new IllegalStateException("Timer topology mismatch at " + padId);
    }
}

/** Fault validation consumes only the observed temporal symptom. */
final class Timer555FaultValidator implements GeneratedFaultValidator {
    public void verify(GeneratedBoardInstance instance, BoardModificationController modifications,
            BoardPowerState powerState) {
        GeneratedTemporalBehavior temporal = instance.getTemporalBehavior();
        if (temporal == null || !Timer555TemporalBehavior.isSymptom(temporal.getObservedBehavior()))
            throw new IllegalStateException("Timer fault lacks an observed timing symptom");
    }
}

/** The generic controller delegates timer repair proof to the temporal contract. */
final class Timer555RepairValidator implements GeneratedRepairValidator {
    public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
    }
    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) { return false; }
}
