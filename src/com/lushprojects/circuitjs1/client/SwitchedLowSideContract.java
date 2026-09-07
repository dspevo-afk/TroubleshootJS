package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Evidence carried by a typed low-side switched-load relationship.  The
 * relationship remains a physical two-port connection; these fields state
 * the separately declared supply, control, reference and bounded operating
 * evidence required to admit it.
 */
final class SwitchedLowSideContract {
    static final int VERSION = 1;
    static final double SUPPLY_GUARANTEED_MIN_VOLTS = 4.75;
    static final double SUPPLY_GUARANTEED_MAX_VOLTS = 5.25;
    static final double LOAD_ALLOWED_MIN_VOLTS = 4.5;
    static final double LOAD_ALLOWED_MAX_VOLTS = 5.5;
    static final double MAX_LOAD_DEMAND_AMPS = 0.016;
    static final double MIN_SINK_CAPACITY_AMPS = 0.020;
    static final double ON_CLAMP_MIN_VOLTS = 0.0;
    static final double ON_CLAMP_MAX_VOLTS = 0.8;
    static final double OFF_DRAIN_MIN_VOLTS = 0.0;
    static final double OFF_DRAIN_MAX_VOLTS = 5.5;
    static final double CONTROL_LOW_MAX_VOLTS = 0.1;
    static final double CONTROL_HIGH_MIN_VOLTS = 4.75;
    static final double INPUT_LOW_MAX_VOLTS = 0.2;
    static final double INPUT_HIGH_MIN_VOLTS = 4.0;

    private final ElectricalConnection.PortRef sinkPort;
    private final ElectricalConnection.PortRef loadPort;
    private final ElectricalConnection.PortRef supplyPort;
    private final ElectricalConnection.PortRef controlPort;
    private final List<ElectricalConnection.PortRef> returnPorts;
    private final String referenceNetId;
    private final String isolationId;
    private final boolean activeHigh;
    private final double sinkCapacityAmps;
    private final double loadDemandAmps;

    SwitchedLowSideContract(ElectricalConnection.PortRef sinkPort,
            ElectricalConnection.PortRef loadPort,
            ElectricalConnection.PortRef supplyPort,
            ElectricalConnection.PortRef controlPort,
            Collection<ElectricalConnection.PortRef> returnPorts) {
        this(sinkPort, loadPort, supplyPort, controlPort, returnPorts,
                "RETURN", "shared-return", true, MIN_SINK_CAPACITY_AMPS,
                MAX_LOAD_DEMAND_AMPS);
    }

    SwitchedLowSideContract(ElectricalConnection.PortRef sinkPort,
            ElectricalConnection.PortRef loadPort,
            ElectricalConnection.PortRef supplyPort,
            ElectricalConnection.PortRef controlPort,
            Collection<ElectricalConnection.PortRef> returnPorts,
            String referenceNetId, String isolationId, boolean activeHigh,
            double sinkCapacityAmps, double loadDemandAmps) {
        this.sinkPort = required(sinkPort, "sinkPort");
        this.loadPort = required(loadPort, "loadPort");
        this.supplyPort = required(supplyPort, "supplyPort");
        this.controlPort = required(controlPort, "controlPort");
        if (returnPorts == null || returnPorts.isEmpty())
            throw new IllegalArgumentException("returnPorts are required");
        ArrayList<ElectricalConnection.PortRef> returns =
                new ArrayList<ElectricalConnection.PortRef>();
        for (ElectricalConnection.PortRef value : returnPorts)
            returns.add(required(value, "returnPort"));
        this.returnPorts = Collections.unmodifiableList(returns);
        this.referenceNetId = FunctionalBlockDescriptor.requireId(
                referenceNetId, "referenceNetId");
        this.isolationId = FunctionalBlockDescriptor.requireId(isolationId,
                "isolationId");
        if (!finitePositive(sinkCapacityAmps) || !finitePositive(loadDemandAmps))
            throw new IllegalArgumentException("Switching bounds must be finite and positive");
        this.activeHigh = activeHigh;
        this.sinkCapacityAmps = sinkCapacityAmps;
        this.loadDemandAmps = loadDemandAmps;
    }

    static SwitchedLowSideContract forControlledIndicator() {
        return new SwitchedLowSideContract(
                new ElectricalConnection.PortRef(
                        "driver",
                        "SWITCHED_SINK"),
                new ElectricalConnection.PortRef(
                        "load",
                        "SWITCHED_LOAD"),
                new ElectricalConnection.PortRef(
                        "power-adapter", "POWER_OUT"),
                new ElectricalConnection.PortRef(
                        "control-adapter", "CONTROL_OUT"),
                Arrays.asList(
                        new ElectricalConnection.PortRef(
                                "driver", "RETURN"),
                        new ElectricalConnection.PortRef(
                                "load", "RETURN"),
                        new ElectricalConnection.PortRef(
                                "power-adapter", "RETURN"),
                        new ElectricalConnection.PortRef(
                                "control-adapter",
                                "RETURN")));
    }

    ElectricalConnection.PortRef getSinkPort() { return sinkPort; }
    ElectricalConnection.PortRef getDriverPort() { return sinkPort; }
    ElectricalConnection.PortRef getLoadPort() { return loadPort; }
    ElectricalConnection.PortRef getSwitchedLoadPort() { return loadPort; }
    ElectricalConnection.PortRef getSupplyPort() { return supplyPort; }
    ElectricalConnection.PortRef getControlPort() { return controlPort; }
    List<ElectricalConnection.PortRef> getReturnPorts() { return returnPorts; }
    String getReferenceNetId() { return referenceNetId; }
    String getIsolationId() { return isolationId; }
    boolean isActiveHigh() { return activeHigh; }
    boolean getActiveHigh() { return activeHigh; }
    double getSinkCapacityAmps() { return sinkCapacityAmps; }
    double getLoadDemandAmps() { return loadDemandAmps; }
    double getSupplyGuaranteedMinimumVolts() { return SUPPLY_GUARANTEED_MIN_VOLTS; }
    double getSupplyGuaranteedMaximumVolts() { return SUPPLY_GUARANTEED_MAX_VOLTS; }
    double getLoadAllowedMinimumVolts() { return LOAD_ALLOWED_MIN_VOLTS; }
    double getLoadAllowedMaximumVolts() { return LOAD_ALLOWED_MAX_VOLTS; }
    double getOnClampMinimumVolts() { return ON_CLAMP_MIN_VOLTS; }
    double getOnClampMaximumVolts() { return ON_CLAMP_MAX_VOLTS; }
    double getOffDrainMinimumVolts() { return OFF_DRAIN_MIN_VOLTS; }
    double getOffDrainMaximumVolts() { return OFF_DRAIN_MAX_VOLTS; }
    double getControlLowMaximumVolts() { return CONTROL_LOW_MAX_VOLTS; }
    double getControlHighMinimumVolts() { return CONTROL_HIGH_MIN_VOLTS; }
    double getInputLowMaximumVolts() { return INPUT_LOW_MAX_VOLTS; }
    double getInputHighMinimumVolts() { return INPUT_HIGH_MIN_VOLTS; }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static boolean finitePositive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }
}
