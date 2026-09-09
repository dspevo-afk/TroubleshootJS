package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collection;
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
 * Typed registry for the two local resistive contributions used by the
 * bounded composed fixture.  A provider creates declarations and local rules;
 * it never allocates a CircuitJS element or owns a physical part.
 */
final class ResistiveBlockContributions {
    static final int VERSION = 1;
    static final String SOURCE_TYPE_ID = "resistive-source";
    static final String LOAD_TYPE_ID = "resistive-load";
    static final String SOURCE_BLOCK_KEY = "source";
    static final String LOAD_BLOCK_KEY = "load";
    static final String RESISTANCE_PARAMETER_ID = "resistance-ohms";
    static final String FAULT_LOCAL_ID = "high-resistance";
    static final String SOURCE_FAULT_DECISION_KEY = "source-high-resistance";
    static final String LOAD_FAULT_DECISION_KEY = "load-high-resistance";
    static final String SIGNAL_CONNECTION_ID = "signal";
    static final String RETURN_CONNECTION_ID = "return";

    interface Provider {
        String getTypeId();
        int getVersion();
        ComposedBlockContribution create(String blockKey,
                double resistanceOhms);
    }

    private static final Provider SOURCE = new ProviderImpl(
            SOURCE_TYPE_ID, true);
    private static final Provider LOAD = new ProviderImpl(LOAD_TYPE_ID, false);

    private ResistiveBlockContributions() { }

    static Provider source() { return SOURCE; }
    static Provider load() { return LOAD; }

    static Provider resolve(String typeId, int version) {
        if (SOURCE_TYPE_ID.equals(typeId) && version == VERSION) {
            return SOURCE;
        }
        if (LOAD_TYPE_ID.equals(typeId) && version == VERSION) {
            return LOAD;
        }
        throw new IllegalArgumentException("Unsupported resistive provider "
                + typeId + "@" + version);
    }

    /** Deterministically choose a supported resistor value from a named stream. */
    static double chooseValue(long rootSeed, String blockKey) {
        String key = chooseValueKey(rootSeed, blockKey);
        if ("r100".equals(key)) return 100.0;
        if ("r220".equals(key)) return 220.0;
        if ("r1000".equals(key)) return 1000.0;
        if ("r2200".equals(key)) return 2200.0;
        throw new IllegalArgumentException("Unknown resistive value key " + key);
    }

    static String chooseValueKey(long rootSeed, String blockKey) {
        NamedRandomStreams streams = new NamedRandomStreams(1, rootSeed,
                BoundedAssemblyRequest.INTENT_ID,
                BoundedAssemblyRequest.INTENT_VERSION);
        if (SOURCE_BLOCK_KEY.equals(blockKey)) {
            return NamedRandomStreams.select(streams.blockSeed(blockKey,
                    NamedRandomStreams.Concern.VALUES, 1, "resistance"),
                    Arrays.asList("r100", "r220"));
        }
        if (LOAD_BLOCK_KEY.equals(blockKey)) {
            return NamedRandomStreams.select(streams.blockSeed(blockKey,
                    NamedRandomStreams.Concern.VALUES, 1, "resistance"),
                    Arrays.asList("r1000", "r2200"));
        }
        throw new IllegalArgumentException("Unknown resistive block key "
                + blockKey);
    }

    /** Deterministically choose the selected fault from the device FAULT stream. */
    static String chooseFaultDecision(long rootSeed) {
        NamedRandomStreams streams = new NamedRandomStreams(1, rootSeed,
                BoundedAssemblyRequest.INTENT_ID,
                BoundedAssemblyRequest.INTENT_VERSION);
        return NamedRandomStreams.select(streams.deviceSeed(
                NamedRandomStreams.Concern.FAULT, 1, "selected-fault"),
                Arrays.asList(SOURCE_FAULT_DECISION_KEY,
                        LOAD_FAULT_DECISION_KEY));
    }

    private static final class ProviderImpl implements Provider {
        private final String typeId;
        private final boolean source;

        ProviderImpl(String typeId, boolean source) {
            this.typeId = typeId;
            this.source = source;
        }

        @Override
        public String getTypeId() { return typeId; }

        @Override
        public int getVersion() { return VERSION; }

        @Override
        public ComposedBlockContribution create(String blockKey,
                double resistanceOhms) {
            String expectedKey = source ? SOURCE_BLOCK_KEY : LOAD_BLOCK_KEY;
            if (!expectedKey.equals(blockKey)) {
                throw new IllegalArgumentException("Provider " + typeId
                        + " requires block key " + expectedKey);
            }
            validateValue(resistanceOhms, source);
            FunctionalBlockDescriptor descriptor = descriptor(blockKey,
                    resistanceOhms, source);
            ElectricalBlockContract electrical = electricalContract(descriptor,
                    source);
            ComposedBlockContribution.ResistorRecipe recipe =
                    new ComposedBlockContribution.ResistorRecipe(
                            "R1", "R1_1", "R1_2", "R1.1", "R1.2",
                            resistanceOhms, ComposedBlockContribution.RATED_WATTS,
                            PhysicalPackages.AXIAL_RESISTOR.getId(), true);
            return new ComposedBlockContribution(typeId, VERSION, descriptor,
                    electrical, Collections.singletonMap("R1", recipe),
                    Collections.<ComposedBlockContribution.NmosRecipe>emptyList(),
                    Collections.<ComposedBlockContribution.LedRecipe>emptyList(),
                    new ComposedBlockContribution.FaultSpec(
                            ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE,
                            "R1", ComposedBlockContribution.FAULT_RESISTANCE_OHMS), "R1",
                    Arrays.asList("BOARD_POWER"),
                    Arrays.asList("STEADY_DC_POWERED"));
        }
    }

    private static FunctionalBlockDescriptor descriptor(String blockKey,
            double resistanceOhms, boolean source) {
        String typeId = source ? SOURCE_TYPE_ID : LOAD_TYPE_ID;
        List<String> nets = source
                ? Arrays.asList("SUPPLY", "OUT", "RETURN")
                : Arrays.asList("SUPPLY", "RETURN");
        List<FunctionalBlockDescriptor.Role> roles = source
                ? sourceRoles() : loadRoles();
        List<FunctionalBlockDescriptor.Port> ports = source
                ? sourcePorts() : loadPorts();
        List<FunctionalBlockDescriptor.Pad> pads = source
                ? sourcePads() : loadPads();
        return new FunctionalBlockDescriptor(typeId, VERSION, blockKey,
                Arrays.asList(new FunctionalBlockDescriptor.Parameter(
                        RESISTANCE_PARAMETER_ID,
                        FunctionalBlockDescriptor.Value.ofInteger(
                                (int) resistanceOhms))),
                Arrays.asList(new FunctionalBlockDescriptor.Component(
                        "R1", "RESISTOR", Arrays.asList("1", "2"))),
                nets,
                Arrays.asList(
                        new FunctionalBlockDescriptor.Endpoint("R1_1", "R1", "1"),
                        new FunctionalBlockDescriptor.Endpoint("R1_2", "R1", "2")),
                pads, roles, ports);
    }

    private static List<FunctionalBlockDescriptor.Role> sourceRoles() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Role("rail", Requirement.REQUIRED,
                        Arrays.asList(
                                ref(EntityKind.NET, "OUT"),
                                ref(EntityKind.PAD, "R1.2"),
                                ref(EntityKind.ENDPOINT, "R1_2"))),
                new FunctionalBlockDescriptor.Role("reference", Requirement.REQUIRED,
                        Arrays.asList(ref(EntityKind.NET, "RETURN"))));
    }

    private static List<FunctionalBlockDescriptor.Role> loadRoles() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Role("load", Requirement.REQUIRED,
                        Arrays.asList(
                                ref(EntityKind.NET, "SUPPLY"),
                                ref(EntityKind.PAD, "R1.1"),
                                ref(EntityKind.ENDPOINT, "R1_1"))),
                new FunctionalBlockDescriptor.Role("reference", Requirement.REQUIRED,
                        Arrays.asList(ref(EntityKind.NET, "RETURN"))));
    }

    private static List<FunctionalBlockDescriptor.Port> sourcePorts() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Port("OUT", "rail",
                        ref(EntityKind.ENDPOINT, "R1_2")),
                new FunctionalBlockDescriptor.Port("RETURN", "reference",
                        ref(EntityKind.NET, "RETURN")));
    }

    private static List<FunctionalBlockDescriptor.Port> loadPorts() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Port("IN", "load",
                        ref(EntityKind.PAD, "R1.1")),
                new FunctionalBlockDescriptor.Port("RETURN", "reference",
                        ref(EntityKind.NET, "RETURN")));
    }

    private static List<FunctionalBlockDescriptor.Pad> sourcePads() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Pad("R1.1", "R1_1", "SUPPLY"),
                new FunctionalBlockDescriptor.Pad("R1.2", "R1_2", "OUT"));
    }

    private static List<FunctionalBlockDescriptor.Pad> loadPads() {
        return Arrays.asList(
                new FunctionalBlockDescriptor.Pad("R1.1", "R1_1", "SUPPLY"),
                new FunctionalBlockDescriptor.Pad("R1.2", "R1_2", "RETURN"));
    }

    private static ElectricalBlockContract electricalContract(
            FunctionalBlockDescriptor descriptor, boolean source) {
        Domain domain = Domain.known("RETURN", "shared-return");
        ElectricalPortContract external = source
                ? new ElectricalPortContract("OUT", Role.RAIL,
                        Direction.OUTPUT, Behavior.SOURCE,
                        Drive.RESISTIVE_SOURCE, domain,
                        Scalar.known(5.0), Range.known(3.9, 5.0),
                        Range.known(3.9, 5.0), Loading.NONE,
                        Scalar.known(ComposedBlockContribution.MAX_OPERATING_CURRENT_AMPS),
                        Scalar.notApplicable(),
                        ElectricalPortContract.Digital.notApplicable(),
                        MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                        AccessProvision.CONNECTABLE)
                : new ElectricalPortContract("IN", Role.LOAD,
                        Direction.INPUT, Behavior.SINK, Drive.NONE, domain,
                        Scalar.known(5.0), Range.known(3.9, 5.0),
                        Range.known(3.9, 5.0), Loading.BOUNDED_CURRENT,
                        Scalar.notApplicable(),
                        Scalar.known(ComposedBlockContribution.MAX_OPERATING_CURRENT_AMPS),
                        ElectricalPortContract.Digital.notApplicable(),
                        MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                        AccessProvision.CONNECTABLE);
        ElectricalPortContract returned = new ElectricalPortContract("RETURN",
                Role.RETURN, Direction.BIDIRECTIONAL, Behavior.PASSIVE,
                Drive.NONE, domain, Scalar.notApplicable(), Range.notApplicable(),
                Range.notApplicable(), Loading.NONE, Scalar.notApplicable(),
                Scalar.notApplicable(), ElectricalPortContract.Digital.notApplicable(),
                MergePolicy.ALLOW, AccessRequirement.CONNECTABLE,
                AccessProvision.CONNECTABLE);
        return new ElectricalBlockContract(descriptor,
                source ? Arrays.asList(external, returned)
                        : Arrays.asList(external, returned),
                Collections.<ElectricalBlockContract.Adapter>emptyList());
    }

    private static void validateValue(double value, boolean source) {
        if (Double.isNaN(value) || Double.isInfinite(value)
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("Resistance must be an integer value");
        }
        if (source) {
            if (value != 100.0 && value != 220.0) {
                throw new IllegalArgumentException("Unsupported source resistance "
                        + value);
            }
        } else if (value != 1000.0 && value != 2200.0) {
            throw new IllegalArgumentException("Unsupported load resistance "
                    + value);
        }
    }

    private static LocalRef ref(EntityKind kind, String id) {
        return new LocalRef(kind, id);
    }
}
