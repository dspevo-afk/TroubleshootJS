package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.ActiveLevel;
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

/** Typed registry for the Task 48 NMOS driver and LED load contributions. */
final class ControlledIndicatorBlockContributions {
    static final int VERSION = 1;
    static final String FAMILY_ID = "COMPOSED_CONTROLLED_INDICATOR";
    static final String DRIVER_TYPE_ID = "nmos-low-side-driver";
    static final String LOAD_TYPE_ID = "resistor-led-load";
    static final String DRIVER_BLOCK_KEY = "driver";
    static final String LOAD_BLOCK_KEY = "load";
    static final String DRIVER_FAULT_DECISION_KEY = "driver-rg-open";
    static final String LOAD_FAULT_DECISION_KEY = "load-rload-open";
    static final String POWER_CONNECTION_ID = "power-supply";
    static final String CONTROL_CONNECTION_ID = "control-input";
    static final String SWITCHED_CONNECTION_ID = "switched-low-side";
    static final String RETURN_CONNECTION_ID = "common-return";

    static final double RG_OHMS = 1000.0;
    static final double RPD_OHMS = 100000.0;
    static final double RLOAD_OHMS = 330.0;

    /** Local fault vocabulary kept independent of generated challenge faults. */
    static final class FaultSpec extends ComposedBlockContribution.FaultSpec {
        FaultSpec(Kind kind, String targetComponentLocalId,
                double effectiveResistanceOhms) {
            super(kind, targetComponentLocalId, effectiveResistanceOhms);
        }
        static FaultSpec open(String componentLocalId) {
            return new FaultSpec(Kind.OPEN, componentLocalId,
                    ComposedBlockContribution.FAULT_RESISTANCE_OHMS);
        }
        static FaultSpec incorrectResistance(String componentLocalId,
                double effectiveResistanceOhms) {
            return new FaultSpec(Kind.INCORRECT_RESISTANCE,
                    componentLocalId, effectiveResistanceOhms);
        }
    }

    interface Provider {
        String getFamilyId();
        String getTypeId();
        int getVersion();
        ComposedBlockContribution create(String blockKey);
        ComposedBlockContribution create(String blockKey, FaultSpec fault);
    }

    private static final Provider DRIVER = new ProviderImpl(true);
    private static final Provider LOAD = new ProviderImpl(false);

    private ControlledIndicatorBlockContributions() { }

    static Provider driver() { return DRIVER; }
    static Provider load() { return LOAD; }

    static Provider resolve(String typeId, int version) {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported controlled provider "
                    + typeId + "@" + version);
        }
        if (DRIVER_TYPE_ID.equals(typeId)) return DRIVER;
        if (LOAD_TYPE_ID.equals(typeId)) return LOAD;
        throw new IllegalArgumentException("Unknown controlled provider " + typeId);
    }

    static String componentId(BlockNamespace namespace, String blockKey,
            String localComponentId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(blockKey, EntityKind.COMPONENT, localComponentId);
    }

    static String componentId(String blockKey, String localComponentId,
            BlockNamespace namespace) {
        return componentId(namespace, blockKey, localComponentId);
    }

    static String padId(BlockNamespace namespace, String blockKey,
            String localPadId) {
        if (namespace == null) throw new IllegalArgumentException("Namespace is required");
        return namespace.idFor(blockKey, EntityKind.PAD, localPadId);
    }

    static String padId(String blockKey, String localPadId,
            BlockNamespace namespace) {
        return padId(namespace, blockKey, localPadId);
    }

    /** The complete device identity authority, including its declared adapters. */
    static BlockNamespace namespace() {
        return new BlockNamespace(BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION,
                BoundedAssemblyRequest.forControlledIndicator(0L).getNamespaceDescriptors());
    }

    static String chooseFaultDecision(long rootSeed) {
        NamedRandomStreams streams = new NamedRandomStreams(1, rootSeed,
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION);
        return NamedRandomStreams.select(streams.deviceSeed(
                NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                Arrays.asList(DRIVER_FAULT_DECISION_KEY,
                        LOAD_FAULT_DECISION_KEY));
    }

    static FaultSpec faultForDecision(String decisionKey) {
        if (DRIVER_FAULT_DECISION_KEY.equals(decisionKey)) return FaultSpec.open("RG");
        if (LOAD_FAULT_DECISION_KEY.equals(decisionKey)) return FaultSpec.open("RLOAD");
        throw new IllegalArgumentException("Unknown controlled fault decision " + decisionKey);
    }

    private static final class ProviderImpl implements Provider {
        private final boolean driver;
        ProviderImpl(boolean driver) { this.driver = driver; }
        @Override public String getFamilyId() { return FAMILY_ID; }
        @Override public String getTypeId() { return driver ? DRIVER_TYPE_ID : LOAD_TYPE_ID; }
        @Override public int getVersion() { return VERSION; }
        @Override public ComposedBlockContribution create(String blockKey) {
            return create(blockKey, driver ? FaultSpec.open("RG") : FaultSpec.open("RLOAD"));
        }
        @Override public ComposedBlockContribution create(String blockKey,
                FaultSpec fault) {
            String expected = driver ? DRIVER_BLOCK_KEY : LOAD_BLOCK_KEY;
            if (!expected.equals(blockKey))
                throw new IllegalArgumentException("Provider " + getTypeId()
                        + " requires block key " + expected);
            if (fault == null) throw new IllegalArgumentException("Fault is required");
            String expectedTarget = driver ? "RG" : "RLOAD";
            if (!expectedTarget.equals(fault.getTargetComponentLocalId()))
                throw new IllegalArgumentException("Controlled fault target must be "
                        + expectedTarget);
            FunctionalBlockDescriptor descriptor = driver
                    ? driverDescriptor(blockKey) : loadDescriptor(blockKey);
            ElectricalBlockContract electrical = driver
                    ? driverElectrical(descriptor) : loadElectrical(descriptor);
            TreeMap<String, ComposedBlockContribution.ResistorRecipe> resistors =
                    new TreeMap<String, ComposedBlockContribution.ResistorRecipe>();
            Collection<ComposedBlockContribution.NmosRecipe> nmos =
                    Collections.<ComposedBlockContribution.NmosRecipe>emptyList();
            Collection<ComposedBlockContribution.LedRecipe> leds =
                    Collections.<ComposedBlockContribution.LedRecipe>emptyList();
            if (driver) {
                resistors.put("RG", new ComposedBlockContribution.ResistorRecipe(
                        "RG", "RG_1", "RG_2", "RG.1", "RG.2", RG_OHMS,
                        ComposedBlockContribution.RATED_WATTS, true));
                resistors.put("RPD", new ComposedBlockContribution.ResistorRecipe(
                        "RPD", "RPD_1", "RPD_2", "RPD.1", "RPD.2", RPD_OHMS,
                        ComposedBlockContribution.RATED_WATTS, false));
                nmos = Arrays.asList(new ComposedBlockContribution.NmosRecipe(
                        "Q1", "Q1_G", "Q1_D", "Q1_S", "Q1.G", "Q1.D",
                        "Q1.S", "NMOS"));
            } else {
                resistors.put("RLOAD", new ComposedBlockContribution.ResistorRecipe(
                        "RLOAD", "RLOAD_1", "RLOAD_2", "RLOAD.1", "RLOAD.2",
                        RLOAD_OHMS, ComposedBlockContribution.RATED_WATTS, true));
                leds = Arrays.asList(new ComposedBlockContribution.LedRecipe(
                        "LED1", "LED1_A", "LED1_K", "LED1.A", "LED1.K", "LED"));
            }
            return new ComposedBlockContribution(FAMILY_ID, VERSION, descriptor,
                    electrical, resistors, nmos, leds, fault, fault.getTargetComponentLocalId(),
                    Arrays.asList("BOARD_POWER", "CONTROL_INPUT"),
                    Arrays.asList("STEADY_DC_POWERED", "CUSTOMER_RETEST"));
        }
    }

    private static FunctionalBlockDescriptor driverDescriptor(String key) {
        return new FunctionalBlockDescriptor(DRIVER_TYPE_ID, VERSION, key,
                Arrays.asList(
                        parameter("gate-resistance-ohms", 1000),
                        parameter("pull-down-ohms", 100000),
                        parameter("model", "NMOS")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component("RG", "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("RPD", "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("Q1", "NMOS", Arrays.asList("G", "D", "S"))),
                Arrays.asList("CONTROL", "GATE", "SWITCHED_SINK", "RETURN"),
                Arrays.asList(
                        endpoint("RG_1", "RG", "1"), endpoint("RG_2", "RG", "2"),
                        endpoint("RPD_1", "RPD", "1"), endpoint("RPD_2", "RPD", "2"),
                        endpoint("Q1_G", "Q1", "G"), endpoint("Q1_D", "Q1", "D"),
                        endpoint("Q1_S", "Q1", "S")),
                Arrays.asList(
                        pad("RG.1", "RG_1", "CONTROL"), pad("RG.2", "RG_2", "GATE"),
                        pad("RPD.1", "RPD_1", "GATE"), pad("RPD.2", "RPD_2", "RETURN"),
                        pad("Q1.G", "Q1_G", "GATE"), pad("Q1.D", "Q1_D", "SWITCHED_SINK"),
                        pad("Q1.S", "Q1_S", "RETURN")),
                Arrays.asList(
                        role("control", "CONTROL", "RG.1"),
                        new FunctionalBlockDescriptor.Role("gate", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "GATE"), ref(EntityKind.PAD, "RG.2"),
                                        ref(EntityKind.PAD, "RPD.1"), ref(EntityKind.PAD, "Q1.G"))),
                        role("switched", "SWITCHED_SINK", "Q1.D"),
                        new FunctionalBlockDescriptor.Role("return", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "RETURN"), ref(EntityKind.PAD, "RPD.2"),
                                        ref(EntityKind.PAD, "Q1.S")))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("CONTROL", "control", ref(EntityKind.PAD, "RG.1")),
                        new FunctionalBlockDescriptor.Port("SWITCHED_SINK", "switched", ref(EntityKind.PAD, "Q1.D")),
                        new FunctionalBlockDescriptor.Port("RETURN", "return", ref(EntityKind.NET, "RETURN"))));
    }

    private static FunctionalBlockDescriptor loadDescriptor(String key) {
        return new FunctionalBlockDescriptor(LOAD_TYPE_ID, VERSION, key,
                Arrays.asList(parameter("load-resistance-ohms", 330), parameter("model", "LED")),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Component("RLOAD", "RESISTOR", Arrays.asList("1", "2")),
                        new FunctionalBlockDescriptor.Component("LED1", "LED", Arrays.asList("A", "K"))),
                Arrays.asList("SUPPLY", "LED_NODE", "SWITCHED_LOAD", "RETURN"),
                Arrays.asList(endpoint("RLOAD_1", "RLOAD", "1"), endpoint("RLOAD_2", "RLOAD", "2"),
                        endpoint("LED1_A", "LED1", "A"), endpoint("LED1_K", "LED1", "K")),
                Arrays.asList(pad("RLOAD.1", "RLOAD_1", "SUPPLY"), pad("RLOAD.2", "RLOAD_2", "LED_NODE"),
                        pad("LED1.A", "LED1_A", "LED_NODE"), pad("LED1.K", "LED1_K", "SWITCHED_LOAD")),
                Arrays.asList(
                        role("supply", "SUPPLY", "RLOAD.1"),
                        new FunctionalBlockDescriptor.Role("ledNode", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "LED_NODE"), ref(EntityKind.PAD, "RLOAD.2"),
                                        ref(EntityKind.PAD, "LED1.A"))),
                        role("switched", "SWITCHED_LOAD", "LED1.K"),
                        new FunctionalBlockDescriptor.Role("return", Requirement.REQUIRED,
                                Arrays.asList(ref(EntityKind.NET, "RETURN")))),
                Arrays.asList(
                        new FunctionalBlockDescriptor.Port("SUPPLY", "supply", ref(EntityKind.PAD, "RLOAD.1")),
                        new FunctionalBlockDescriptor.Port("SWITCHED_LOAD", "switched", ref(EntityKind.PAD, "LED1.K")),
                        new FunctionalBlockDescriptor.Port("RETURN", "return", ref(EntityKind.NET, "RETURN"))));
    }

    private static ElectricalBlockContract driverElectrical(FunctionalBlockDescriptor descriptor) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract control = new ElectricalPortContract("CONTROL", Role.CONTROL,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(5.0),
                Range.known(0.0, 5.0), Range.known(0.0, 5.0), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(0.00005),
                new ElectricalPortContract.Digital(ActiveLevel.HIGH,
                        Scalar.notApplicable(), Scalar.notApplicable(), Scalar.known(0.2),
                        Scalar.known(4.0)), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        ElectricalPortContract switched = new ElectricalPortContract("SWITCHED_SINK", Role.LOAD,
                Direction.OUTPUT, Behavior.SINK, Drive.OPEN_DRAIN, domain, Scalar.known(0.0),
                Range.known(0.0, 0.8), Range.known(0.0, 5.5), Loading.NONE,
                Scalar.known(0.020), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        return contract(descriptor, Arrays.asList(control, switched, returned(domain)));
    }

    private static ElectricalBlockContract loadElectrical(FunctionalBlockDescriptor descriptor) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract supply = new ElectricalPortContract("SUPPLY", Role.LOAD,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(5.0),
                Range.known(4.5, 5.5), Range.known(4.5, 5.5), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(0.016),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        ElectricalPortContract switched = new ElectricalPortContract("SWITCHED_LOAD", Role.LOAD,
                Direction.INPUT, Behavior.SINK, Drive.NONE, domain, Scalar.known(0.0),
                Range.known(0.0, 5.5), Range.known(0.0, 5.5), Loading.BOUNDED_CURRENT,
                Scalar.notApplicable(), Scalar.known(0.016),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
        return contract(descriptor, Arrays.asList(supply, switched, returned(domain)));
    }

    private static ElectricalBlockContract contract(FunctionalBlockDescriptor descriptor,
            List<ElectricalPortContract> ports) {
        return new ElectricalBlockContract(descriptor, ports,
                Collections.<ElectricalBlockContract.Adapter>emptyList());
    }
    private static ElectricalPortContract returned(Domain domain) {
        return new ElectricalPortContract("RETURN", Role.RETURN, Direction.BIDIRECTIONAL,
                Behavior.PASSIVE, Drive.NONE, domain, Scalar.notApplicable(),
                Range.notApplicable(), Range.notApplicable(), Loading.NONE,
                Scalar.notApplicable(), Scalar.notApplicable(),
                ElectricalPortContract.Digital.notApplicable(), MergePolicy.ALLOW,
                AccessRequirement.CONNECTABLE, AccessProvision.BOTH);
    }
    private static FunctionalBlockDescriptor.Parameter parameter(String id, int value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofInteger(value));
    }
    private static FunctionalBlockDescriptor.Parameter parameter(String id, String value) {
        return new FunctionalBlockDescriptor.Parameter(id,
                FunctionalBlockDescriptor.Value.ofText(value));
    }
    private static FunctionalBlockDescriptor.Endpoint endpoint(String id, String component, String terminal) {
        return new FunctionalBlockDescriptor.Endpoint(id, component, terminal);
    }
    private static FunctionalBlockDescriptor.Pad pad(String id, String endpoint, String net) {
        return new FunctionalBlockDescriptor.Pad(id, endpoint, net);
    }
    private static FunctionalBlockDescriptor.Role role(String id, String net, String pad) {
        return new FunctionalBlockDescriptor.Role(id, Requirement.REQUIRED,
                Arrays.asList(ref(EntityKind.NET, net), ref(EntityKind.PAD, pad)));
    }
    private static LocalRef ref(EntityKind kind, String id) {
        return new LocalRef(kind, id);
    }
}
