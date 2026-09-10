package com.lushprojects.circuitjs1.client;

import java.util.Arrays;

/** Device-owned instance addresses, independent of provider or allocation order. */
final class ControlledIndicatorChannel {
    private final String key;
    private final String label;
    private final String connectorId;

    ControlledIndicatorChannel(String key, String label, String connectorId) {
        this.key = FunctionalBlockDescriptor.requireId(key, "channel.key");
        this.label = FunctionalBlockDescriptor.requireId(label, "channel.label");
        this.connectorId = FunctionalBlockDescriptor.requireId(connectorId, "channel.connector");
    }

    String getKey() { return key; }
    String getLabel() { return label; }
    String getDriverKey() { return key + "-driver"; }
    String getLoadKey() { return key + "-load"; }
    String getControlAdapterKey() { return key + "-control"; }
    String getControlJoinId() { return key + "-control-input"; }
    String getSwitchedJoinId() { return key + "-switched-output"; }
    String getControlPowerInputId() { return key + "-control-power"; }
    String getHighOperationId() { return key.toUpperCase().replace('-', '_') + "_HIGH"; }
    String getLowOperationId() { return key.toUpperCase().replace('-', '_') + "_LOW"; }

    String getSampleId(boolean high) {
        return (high ? getHighOperationId() : getLowOperationId()) + "_SAMPLE";
    }

    DeviceAdapterContract controlAdapter() {
        return DeviceAdapterContract.control(getControlAdapterKey(), connectorId,
                getControlPowerInputId());
    }

    SwitchedLowSideContract switched() {
        return new SwitchedLowSideContract(
                new ElectricalConnection.PortRef(getDriverKey(), "SWITCHED_SINK"),
                new ElectricalConnection.PortRef(getLoadKey(), "SWITCHED_LOAD"),
                new ElectricalConnection.PortRef(DeviceAdapterContract.POWER_ADAPTER_KEY,
                        DeviceAdapterContract.POWER_OUTPUT_PORT_ID),
                new ElectricalConnection.PortRef(getControlAdapterKey(),
                        DeviceAdapterContract.CONTROL_OUTPUT_PORT_ID),
                new ElectricalConnection.PortRef(getDriverKey(), "CONTROL"),
                new ElectricalConnection.PortRef(getLoadKey(), "SUPPLY"),
                Arrays.asList(new ElectricalConnection.PortRef(getDriverKey(), "RETURN"),
                        new ElectricalConnection.PortRef(getLoadKey(), "RETURN"),
                        new ElectricalConnection.PortRef(getControlAdapterKey(), "RETURN"),
                        new ElectricalConnection.PortRef(DeviceAdapterContract.POWER_ADAPTER_KEY, "RETURN")));
    }
}
