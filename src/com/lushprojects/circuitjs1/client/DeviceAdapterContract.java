package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Domain;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Requirement;

/**
 * A device-owned external source adapter.  Adapters are request-level
 * declarations: they participate in namespace and electrical preflight, but
 * are not counted as contributed functional blocks and do not own a board.
 */
final class DeviceAdapterContract {
    static final int VERSION = 1;
    static final String POWER_ADAPTER_KEY = "power-adapter";
    static final String CONTROL_ADAPTER_KEY = "control-adapter";
    static final String POWER_COMPONENT_ID = "J1";
    static final String CONTROL_COMPONENT_ID = "J2";
    static final String POWER_EXTERNAL_INPUT_ID = "LOAD_VIN_INPUT";
    static final String CONTROL_EXTERNAL_INPUT_ID = "CONTROL_VIN_INPUT";
    static final String POWER_OUTPUT_PORT_ID = "POWER_OUT";
    static final String CONTROL_OUTPUT_PORT_ID = "CONTROL_OUT";
    static final String RETURN_PORT_ID = "RETURN";

    private final String key;
    private final FunctionalBlockDescriptor descriptor;
    private final ElectricalBlockContract electricalContract;
    private final String componentLocalId;
    private final String externalInputId;
    private final String outputPortId;
    private final boolean control;

    private DeviceAdapterContract(String key,
            FunctionalBlockDescriptor descriptor,
            ElectricalBlockContract electricalContract,
            String componentLocalId, String externalInputId,
            String outputPortId, boolean control) {
        this.key = FunctionalBlockDescriptor.requireId(key, "adapter.key");
        this.descriptor = descriptor;
        this.electricalContract = electricalContract;
        this.componentLocalId = FunctionalBlockDescriptor.requireId(
                componentLocalId, "adapter.componentLocalId");
        this.externalInputId = FunctionalBlockDescriptor.requireId(
                externalInputId, "adapter.externalInputId");
        this.outputPortId = FunctionalBlockDescriptor.requireId(outputPortId,
                "adapter.outputPortId");
        this.control = control;
    }

    static DeviceAdapterContract power() {
        FunctionalBlockDescriptor descriptor = descriptor(
                POWER_ADAPTER_KEY, POWER_COMPONENT_ID, POWER_OUTPUT_PORT_ID,
                POWER_EXTERNAL_INPUT_ID);
        return new DeviceAdapterContract(POWER_ADAPTER_KEY, descriptor,
                electrical(descriptor, false), POWER_COMPONENT_ID,
                POWER_EXTERNAL_INPUT_ID, POWER_OUTPUT_PORT_ID, false);
    }

    static DeviceAdapterContract control() {
        return control(CONTROL_ADAPTER_KEY, CONTROL_COMPONENT_ID, CONTROL_EXTERNAL_INPUT_ID);
    }

    static DeviceAdapterContract control(String key, String componentId, String inputId) {
        FunctionalBlockDescriptor descriptor = descriptor(
                key, componentId, CONTROL_OUTPUT_PORT_ID, inputId);
        return new DeviceAdapterContract(key, descriptor,
                electrical(descriptor, true), componentId,
                inputId, CONTROL_OUTPUT_PORT_ID, true);
    }

    static DeviceAdapterContract resolve(String key, int version) {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported device adapter version "
                    + key + "@" + version);
        }
        if (POWER_ADAPTER_KEY.equals(key)) return power();
        if (CONTROL_ADAPTER_KEY.equals(key)) return control();
        throw new IllegalArgumentException("Unknown device adapter " + key);
    }

    String getKey() { return key; }
    String getId() { return key; }
    int getVersion() { return VERSION; }
    FunctionalBlockDescriptor getDescriptor() { return descriptor; }
    ElectricalBlockContract getElectricalContract() { return electricalContract; }
    String getComponentLocalId() { return componentLocalId; }
    String getExternalInputId() { return externalInputId; }
    String getOutputPortId() { return outputPortId; }
    String getReturnPortId() { return RETURN_PORT_ID; }
    boolean isControl() { return control; }
    String getRoleId() { return control ? "control-input" : "power-input"; }

    /** Stable namespace address for a local adapter component. */
    String componentId(BlockNamespace namespace) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(key, EntityKind.COMPONENT, componentLocalId);
    }
    String getComponentId(BlockNamespace namespace) { return componentId(namespace); }
    String getNamespacedComponentId(BlockNamespace namespace) {
        return componentId(namespace);
    }
    String padId(BlockNamespace namespace, String localPadId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(key, EntityKind.PAD, localPadId);
    }
    String getPadId(BlockNamespace namespace, String localPadId) {
        return padId(namespace, localPadId);
    }

    private static FunctionalBlockDescriptor descriptor(String key,
            String componentId, String outputPortId, String externalInputId) {
        String outputNet = "OUTPUT";
        String returnNet = "RETURN";
        String outputRole = "output";
        return new FunctionalBlockDescriptor("external-adapter", VERSION, key,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Parameter("external-input",
                                FunctionalBlockDescriptor.Value.ofText(externalInputId))),
                Arrays.asList(new FunctionalBlockDescriptor.Component(componentId,
                        "CONNECTOR_2", Arrays.asList("1", "2"))),
                Arrays.asList(outputNet, returnNet),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint(componentId + "_1",
                                componentId, "1"),
                        new FunctionalBlockDescriptor.Endpoint(componentId + "_2",
                                componentId, "2")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Pad(componentId + ".1",
                                componentId + "_1", outputNet),
                        new FunctionalBlockDescriptor.Pad(componentId + ".2",
                                componentId + "_2", returnNet)),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Role(outputRole,
                                Requirement.REQUIRED, Arrays.asList(
                                        ref(EntityKind.NET, outputNet),
                                        ref(EntityKind.PAD, componentId + ".1"),
                                        ref(EntityKind.ENDPOINT, componentId + "_1"))),
                        new FunctionalBlockDescriptor.Role("return",
                                Requirement.REQUIRED, Arrays.asList(
                                        ref(EntityKind.NET, returnNet),
                                        ref(EntityKind.PAD, componentId + ".2"),
                                        ref(EntityKind.ENDPOINT, componentId + "_2")))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port(outputPortId, outputRole,
                                ref(EntityKind.PAD, componentId + ".1")),
                        new FunctionalBlockDescriptor.Port(RETURN_PORT_ID, "return",
                                ref(EntityKind.NET, returnNet))));
    }

    private static ElectricalBlockContract electrical(
            FunctionalBlockDescriptor descriptor, boolean control) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract output;
        if (control) {
            output = new ElectricalPortContract(CONTROL_OUTPUT_PORT_ID,
                    Role.CONTROL, Direction.OUTPUT, Behavior.SOURCE,
                    Drive.PUSH_PULL, domain, Scalar.known(5.0),
                    Range.known(0.0, 5.0), Range.known(0.0, 5.0),
                    Loading.NONE, Scalar.known(0.002), Scalar.notApplicable(),
                    new ElectricalPortContract.Digital(
                            ElectricalPortContract.ActiveLevel.HIGH,
                            Scalar.known(0.1), Scalar.known(4.75),
                            Scalar.notApplicable(), Scalar.notApplicable()),
                    MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                    AccessProvision.CONNECTABLE);
        } else {
            output = new ElectricalPortContract(POWER_OUTPUT_PORT_ID,
                    Role.RAIL, Direction.OUTPUT, Behavior.SOURCE,
                    Drive.STIFF_VOLTAGE, domain, Scalar.known(5.0),
                    Range.known(4.75, 5.25), Range.known(4.5, 5.5),
                    Loading.NONE, Scalar.known(0.050), Scalar.notApplicable(),
                    ElectricalPortContract.Digital.notApplicable(),
                    MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                    AccessProvision.CONNECTABLE);
        }
        ElectricalPortContract returned = new ElectricalPortContract(
                RETURN_PORT_ID, Role.RETURN, Direction.BIDIRECTIONAL,
                Behavior.PASSIVE, Drive.NONE, domain, Scalar.notApplicable(),
                Range.notApplicable(), Range.notApplicable(), Loading.NONE,
                Scalar.notApplicable(), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(),
                MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                AccessProvision.CONNECTABLE);
        return new ElectricalBlockContract(descriptor,
                Arrays.asList(output, returned),
                Collections.<ElectricalBlockContract.Adapter>emptyList());
    }

    private static LocalRef ref(EntityKind kind, String id) {
        return new LocalRef(kind, id);
    }
}
