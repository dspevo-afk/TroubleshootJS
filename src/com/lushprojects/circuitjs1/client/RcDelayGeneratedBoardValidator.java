package com.lushprojects.circuitjs1.client;

/** Basic healthy graph check; the transient sequence performs temporal proof. */
final class RcDelayGeneratedBoardValidator implements GeneratedBoardValidator {
    public void verify(GeneratedBoardInstance instance, BoardPowerState powerState) {
        CircuitElm c1 = instance.getComponentBindings().getSingleElement("C1");
        CircuitElm c2 = instance.getComponentBindings().getSingleElement("C2");
        if (instance.getComponentBindings().getSingleElement("R1") == null ||
                instance.getComponentBindings().getSingleElement("R2") == null ||
                c1 == null || c1.getClass() != CapacitorElm.class || c2 == null ||
                c2.getClass() != CapacitorElm.class)
            throw new IllegalStateException("RC board is missing its real passive elements");
        // The board contract intentionally states the complete electrical
        // topology, not merely a decorative family of components.
        requirePadNet(instance, "R1.1", "VIN");
        requirePadNet(instance, "R1.2", "RC_OUT");
        requirePadNet(instance, "C1.+", "RC_OUT");
        requirePadNet(instance, "C1.-", "GND");
        requirePadNet(instance, "R2.1", "RC_OUT");
        requirePadNet(instance, "R2.2", "GND");
        requirePadNet(instance, "C2.1", "VIN");
        requirePadNet(instance, "C2.2", "GND");
        for (String netId : instance.getBoard().getNetIds())
            for (CircuitMeasurementEndpoint endpoint :
                    instance.getSimulationBindings().getEndpointsForNet(netId)) {
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                double voltage = post.getElement().getPostVoltage(post.getPostIndex());
                if (Double.isNaN(voltage) || Double.isInfinite(voltage))
                    throw new IllegalStateException("RC board has non-finite net " + netId);
            }
    }

    private void requirePadNet(GeneratedBoardInstance instance, String padId, String netId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        if (pad == null || !netId.equals(pad.getNetId()))
            throw new IllegalStateException("RC topology mismatch at " + padId);
    }
}
