package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
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
 * Fixed healthy support for the bounded device: an always-connected supply
 * indicator that draws a real LED current from SUPPLY to RETURN.
 */
final class SupplyPresentBlockContributions {
    static final int VERSION = 1;
    static final String TYPE_ID = "supply-present-indicator";
    static final String ROLE_ID = "supply-indicator";
    static final String RSUP_COMPONENT_ID = "RSUP";
    static final String LED_COMPONENT_ID = "LED1";
    static final double RSUP_OHMS = 1000.0;
    static final double SUPPLY_DEMAND_MAX_AMPS = 0.005;
    static final double HEALTHY_CURRENT_MIN_AMPS = 0.002;
    static final double HEALTHY_CURRENT_MAX_AMPS = 0.004;
    static final String MODEL_ID = "default-led";

    private SupplyPresentBlockContributions() { }

    static ComposedBlockContribution create(String instanceKey) {
        String key = FunctionalBlockDescriptor.requireId(instanceKey,
                "supplyIndicator.instanceKey");
        FunctionalBlockDescriptor descriptor = descriptor(key);
        ElectricalBlockContract electrical = electrical(descriptor);
        TreeMap<String, ComposedBlockContribution.ResistorRecipe> resistors =
                new TreeMap<String, ComposedBlockContribution.ResistorRecipe>();
        resistors.put(RSUP_COMPONENT_ID, new ComposedBlockContribution.ResistorRecipe(
                RSUP_COMPONENT_ID, "RSUP_1", "RSUP_2", "RSUP.1", "RSUP.2",
                RSUP_OHMS, ComposedBlockContribution.RATED_WATTS,
                PhysicalPackages.AXIAL_RESISTOR.getId(), false));
        return new ComposedBlockContribution(TYPE_ID, VERSION, descriptor,
                electrical, resistors,
                Collections.<ComposedBlockContribution.NmosRecipe>emptyList(),
                Arrays.asList(new ComposedBlockContribution.LedRecipe(
                        LED_COMPONENT_ID, "LED1_A", "LED1_K", "LED1.A",
                        "LED1.K", MODEL_ID)),
                null, null,
                Arrays.asList("BOARD_POWER"),
                Arrays.asList("STEADY_DC_POWERED", "CUSTOMER_RETEST"));
    }

    static String componentId(BlockNamespace namespace, String instanceKey,
            String componentLocalId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(instanceKey, EntityKind.COMPONENT, componentLocalId);
    }

    static String padId(BlockNamespace namespace, String instanceKey,
            String padLocalId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(instanceKey, EntityKind.PAD, padLocalId);
    }

    private static FunctionalBlockDescriptor descriptor(String key) {
        return new FunctionalBlockDescriptor(TYPE_ID, VERSION, key,
                Arrays.asList(
                        parameter("functional-role", ROLE_ID),
                        parameter("model", MODEL_ID),
                        parameter("series-resistance-ohms", RSUP_OHMS),
                        parameter("supply-demand-max-amps", SUPPLY_DEMAND_MAX_AMPS),
                        parameter("healthy-current-min-amps", HEALTHY_CURRENT_MIN_AMPS),
                        parameter("healthy-current-max-amps", HEALTHY_CURRENT_MAX_AMPS)),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component(RSUP_COMPONENT_ID,
                                "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component(LED_COMPONENT_ID,
                                "LED", Arrays.asList("A", "K"))),
                Arrays.asList("SUPPLY", "LED_NODE", "RETURN"),
                Arrays.asList(
                        endpoint("RSUP_1", RSUP_COMPONENT_ID, "1"),
                        endpoint("RSUP_2", RSUP_COMPONENT_ID, "2"),
                        endpoint("LED1_A", LED_COMPONENT_ID, "A"),
                        endpoint("LED1_K", LED_COMPONENT_ID, "K")),
                Arrays.asList(
                        pad("RSUP.1", "RSUP_1", "SUPPLY"),
                        pad("RSUP.2", "RSUP_2", "LED_NODE"),
                        pad("LED1.A", "LED1_A", "LED_NODE"),
                        pad("LED1.K", "LED1_K", "RETURN")),
                Arrays.asList(
                        role("supply", "SUPPLY", "RSUP.1"),
                        new FunctionalBlockDescriptor.Role("ledNode",
                                Requirement.REQUIRED, Arrays.asList(
                                        ref(EntityKind.NET, "LED_NODE"),
                                        ref(EntityKind.PAD, "RSUP.2"),
                                        ref(EntityKind.PAD, "LED1.A"))),
                        role("return", "RETURN", "LED1.K")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("SUPPLY", "supply",
                                ref(EntityKind.PAD, "RSUP.1")),
                        new FunctionalBlockDescriptor.Port("RETURN", "return",
                                ref(EntityKind.NET, "RETURN"))));
    }

    private static ElectricalBlockContract electrical(
            FunctionalBlockDescriptor descriptor) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract supply = new ElectricalPortContract("SUPPLY",
                Role.LOAD, Direction.INPUT, Behavior.SINK, Drive.NONE, domain,
                Scalar.known(5.0), Range.known(4.75, 5.25),
                Range.known(4.5, 5.5), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(SUPPLY_DEMAND_MAX_AMPS),
                ElectricalPortContract.Digital.notApplicable(),
                MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                AccessProvision.BOTH);
        ElectricalPortContract returned = new ElectricalPortContract("RETURN",
                Role.RETURN, Direction.BIDIRECTIONAL, Behavior.PASSIVE,
                Drive.NONE, domain, Scalar.notApplicable(),
                Range.notApplicable(), Range.notApplicable(), Loading.NONE,
                Scalar.notApplicable(), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(),
                MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                AccessProvision.BOTH);
        return new ElectricalBlockContract(descriptor,
                Arrays.asList(supply, returned),
                Collections.<ElectricalBlockContract.Adapter>emptyList());
    }

    private static FunctionalBlockDescriptor.Parameter parameter(String id,
            double value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofDecimal(value));
    }

    private static FunctionalBlockDescriptor.Parameter parameter(String id,
            String value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofText(value));
    }

    private static FunctionalBlockDescriptor.Endpoint endpoint(String id,
            String component, String terminal) {
        return new FunctionalBlockDescriptor.Endpoint(id, component, terminal);
    }

    private static FunctionalBlockDescriptor.Pad pad(String id, String endpoint,
            String net) {
        return new FunctionalBlockDescriptor.Pad(id, endpoint, net);
    }

    private static FunctionalBlockDescriptor.Role role(String id, String net,
            String pad) {
        return new FunctionalBlockDescriptor.Role(id, Requirement.REQUIRED,
                Arrays.asList(ref(EntityKind.NET, net),
                        ref(EntityKind.PAD, pad)));
    }

    private static LocalRef ref(EntityKind kind, String id) {
        return new LocalRef(kind, id);
    }
}
