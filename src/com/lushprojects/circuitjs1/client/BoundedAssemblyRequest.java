package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.Parameter;

/**
 * Immutable device-owned request for the bounded composed resistive fixture.
 * The request contains declarations and proposed connections only; it does
 * not allocate a graph, board, physical runtime, or challenge owner.
 */
final class BoundedAssemblyRequest {
    static final String GENERATOR_ID = "bounded-assembler";
    static final int GENERATOR_VERSION = 4;
    static final String INTENT_ID = "resistive-coupling";
    static final int INTENT_VERSION = 1;
    static final String PROFILE_ID = "developer-canary";
    static final int PROFILE_VERSION = 1;
    static final int GEOMETRY_VERSION = 3;
    static final int SUPPORTED_BLOCK_COUNT = 2;
    static final int SUPPORTED_COMPONENT_COUNT = 3;
    static final int SUPPORTED_DOMAIN_COUNT = 1;

    static final String CONTROLLED_INTENT_ID = "controlled-indicator";
    static final int CONTROLLED_INTENT_VERSION = 1;
    static final String CONTROLLED_PROFILE_ID = "controlled-indicator";
    static final int CONTROLLED_PROFILE_VERSION = 1;
    static final int CONTROLLED_SUPPORTED_BLOCK_COUNT = 2;
    static final int CONTROLLED_SUPPORTED_COMPONENT_COUNT = 7;
    static final int CONTROLLED_SUPPORTED_DOMAIN_COUNT = 1;

    private final ChallengeDescriptor descriptor;
    private final List<ElectricalBlockContract> blocks;
    private final List<ElectricalConnection> connections;
    private final List<DeviceAdapterContract> deviceAdapters;

    BoundedAssemblyRequest(ChallengeDescriptor descriptor,
            Collection<ElectricalBlockContract> blocks,
            Collection<ElectricalConnection> connections) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Assembly descriptor is required");
        }
        this.descriptor = descriptor;
        this.blocks = immutableBlocks(blocks);
        this.connections = immutableConnections(connections);
        this.deviceAdapters = Collections.unmodifiableList(
                new ArrayList<DeviceAdapterContract>());
    }

    BoundedAssemblyRequest(ChallengeDescriptor descriptor,
            Collection<ElectricalBlockContract> blocks,
            Collection<ElectricalConnection> connections,
            Collection<DeviceAdapterContract> deviceAdapters) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Assembly descriptor is required");
        }
        this.descriptor = descriptor;
        this.blocks = immutableBlocks(blocks);
        this.connections = immutableConnections(connections);
        this.deviceAdapters = immutableAdapters(deviceAdapters);
    }

    /** Pure reorder fixture used by the contract oracle; sorting is semantic. */
    static BoundedAssemblyRequest reorderedInputs(ChallengeDescriptor descriptor,
            Collection<ElectricalBlockContract> blocks,
            Collection<ElectricalConnection> connections,
            Collection<DeviceAdapterContract> deviceAdapters) {
        return new BoundedAssemblyRequest(descriptor, blocks, connections,
                deviceAdapters);
    }

    ChallengeDescriptor getDescriptor() { return descriptor; }
    List<ElectricalBlockContract> getBlocks() { return blocks; }
    List<ElectricalConnection> getConnections() { return connections; }
    List<DeviceAdapterContract> getDeviceAdapters() { return deviceAdapters; }

    /** All electrical contracts, including request-level device adapters. */
    List<ElectricalBlockContract> getAllElectricalContracts() {
        ArrayList<ElectricalBlockContract> result =
                new ArrayList<ElectricalBlockContract>();
        for (ElectricalBlockContract block : blocks) result.add(block);
        for (DeviceAdapterContract adapter : deviceAdapters)
            result.add(adapter.getElectricalContract());
        Collections.sort(result, new Comparator<ElectricalBlockContract>() {
            @Override public int compare(ElectricalBlockContract first,
                    ElectricalBlockContract second) {
                return first.getDescriptor().getInstanceKey().compareTo(
                        second.getDescriptor().getInstanceKey());
            }
        });
        return Collections.unmodifiableList(result);
    }

    /** Descriptors used by the complete device namespace. */
    List<FunctionalBlockDescriptor> getNamespaceDescriptors() {
        ArrayList<FunctionalBlockDescriptor> result =
                new ArrayList<FunctionalBlockDescriptor>();
        for (ElectricalBlockContract block : getAllElectricalContracts())
            result.add(block.getDescriptor());
        return Collections.unmodifiableList(result);
    }

    /** Build the canonical supported descriptor for one complete signed seed. */
    static ChallengeDescriptor descriptor(long seed,
            GenerationConstraints constraints) {
        if (constraints == null) {
            throw new IllegalArgumentException("Generation constraints are required");
        }
        return new ChallengeDescriptor(
                ChallengeDescriptor.SCHEMA_VERSION,
                seed,
                new ChallengeDescriptor.VersionedId(GENERATOR_ID,
                        GENERATOR_VERSION),
                new ChallengeDescriptor.VersionedId(INTENT_ID,
                        INTENT_VERSION),
                new ChallengeDescriptor.VersionedId(PROFILE_ID,
                        PROFILE_VERSION),
                new PcbGeometryContractVersion(GEOMETRY_VERSION),
                constraints);
    }

    static ChallengeDescriptor descriptor(long seed) {
        return descriptor(seed, GenerationConstraints.unspecified());
    }

    /**
     * Build the two stock typed declarations and the two explicit device
     * joins.  The descriptor itself remains untouched so unsupported requests
     * can be handed to the pure resolver and rejected there.
     */
    static BoundedAssemblyRequest forCanary(ChallengeDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Assembly descriptor is required");
        }
        long seed = descriptor.getRootSeed();
        ComposedBlockContribution source = ResistiveBlockContributions.source()
                .create(ResistiveBlockContributions.SOURCE_BLOCK_KEY,
                        ResistiveBlockContributions.chooseValue(seed,
                                ResistiveBlockContributions.SOURCE_BLOCK_KEY));
        ComposedBlockContribution load = ResistiveBlockContributions.load()
                .create(ResistiveBlockContributions.LOAD_BLOCK_KEY,
                        ResistiveBlockContributions.chooseValue(seed,
                                ResistiveBlockContributions.LOAD_BLOCK_KEY));
        List<ElectricalBlockContract> blocks = new ArrayList<ElectricalBlockContract>();
        blocks.add(source.getElectricalContract());
        blocks.add(load.getElectricalContract());
        List<ElectricalConnection> connections = new ArrayList<ElectricalConnection>();
        connections.add(new ElectricalConnection(
                ResistiveBlockContributions.SIGNAL_CONNECTION_ID,
                java.util.Arrays.asList(
                        new ElectricalConnection.PortRef(
                                ResistiveBlockContributions.SOURCE_BLOCK_KEY, "OUT"),
                        new ElectricalConnection.PortRef(
                                ResistiveBlockContributions.LOAD_BLOCK_KEY, "IN"))));
        connections.add(new ElectricalConnection(
                ResistiveBlockContributions.RETURN_CONNECTION_ID,
                java.util.Arrays.asList(
                        new ElectricalConnection.PortRef(
                                ResistiveBlockContributions.SOURCE_BLOCK_KEY, "RETURN"),
                        new ElectricalConnection.PortRef(
                                ResistiveBlockContributions.LOAD_BLOCK_KEY, "RETURN"))));
        return new BoundedAssemblyRequest(descriptor, blocks, connections);
    }

    static BoundedAssemblyRequest forCanary(long seed) {
        return forCanary(descriptor(seed));
    }

    static ChallengeDescriptor controlledDescriptor(long seed,
            GenerationConstraints constraints) {
        if (constraints == null)
            throw new IllegalArgumentException("Generation constraints are required");
        return new ChallengeDescriptor(ChallengeDescriptor.SCHEMA_VERSION, seed,
                new ChallengeDescriptor.VersionedId(GENERATOR_ID,
                        GENERATOR_VERSION),
                new ChallengeDescriptor.VersionedId(CONTROLLED_INTENT_ID,
                        CONTROLLED_INTENT_VERSION),
                new ChallengeDescriptor.VersionedId(CONTROLLED_PROFILE_ID,
                        CONTROLLED_PROFILE_VERSION),
                new PcbGeometryContractVersion(GEOMETRY_VERSION), constraints);
    }

    static ChallengeDescriptor controlledDescriptor(long seed) {
        return controlledDescriptor(seed, GenerationConstraints.unspecified());
    }

    static BoundedAssemblyRequest forControlledIndicator(long seed) {
        return forControlledIndicator(controlledDescriptor(seed));
    }

    static BoundedAssemblyRequest forControlledIndicator(
            ChallengeDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("Assembly descriptor is required");
        ComposedBlockContribution driver = ControlledIndicatorBlockContributions
                .driver().create(ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY);
        ComposedBlockContribution load = ControlledIndicatorBlockContributions
                .load().create(ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY);
        ArrayList<ElectricalBlockContract> blocks =
                new ArrayList<ElectricalBlockContract>();
        blocks.add(driver.getElectricalContract());
        blocks.add(load.getElectricalContract());
        ArrayList<DeviceAdapterContract> adapters =
                new ArrayList<DeviceAdapterContract>();
        adapters.add(DeviceAdapterContract.power());
        adapters.add(DeviceAdapterContract.control());
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>();
        connections.add(new ElectricalConnection(
                ControlledIndicatorBlockContributions.POWER_CONNECTION_ID,
                Arrays.asList(new ElectricalConnection.PortRef(
                                DeviceAdapterContract.POWER_ADAPTER_KEY,
                                DeviceAdapterContract.POWER_OUTPUT_PORT_ID),
                        new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                                "SUPPLY"))));
        connections.add(new ElectricalConnection(
                ControlledIndicatorBlockContributions.CONTROL_CONNECTION_ID,
                Arrays.asList(new ElectricalConnection.PortRef(
                                DeviceAdapterContract.CONTROL_ADAPTER_KEY,
                                DeviceAdapterContract.CONTROL_OUTPUT_PORT_ID),
                        new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                                "CONTROL"))));
        connections.add(new ElectricalConnection(
                ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID,
                Arrays.asList(new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                                "SWITCHED_SINK"),
                        new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                                "SWITCHED_LOAD")),
                SwitchedLowSideContract.forControlledIndicator()));
        connections.add(new ElectricalConnection(
                ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID,
                Arrays.asList(new ElectricalConnection.PortRef(
                                DeviceAdapterContract.CONTROL_ADAPTER_KEY,
                                DeviceAdapterContract.RETURN_PORT_ID),
                        new ElectricalConnection.PortRef(
                                DeviceAdapterContract.POWER_ADAPTER_KEY,
                                DeviceAdapterContract.RETURN_PORT_ID),
                        new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY,
                                "RETURN"),
                        new ElectricalConnection.PortRef(
                                ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY,
                                "RETURN"))));
        return new BoundedAssemblyRequest(descriptor, blocks, connections, adapters);
    }

    private static List<ElectricalBlockContract> immutableBlocks(
            Collection<ElectricalBlockContract> values) {
        if (values == null) {
            throw new IllegalArgumentException("Block declarations are required");
        }
        ArrayList<ElectricalBlockContract> copy =
                new ArrayList<ElectricalBlockContract>();
        for (ElectricalBlockContract value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Block declaration is required");
            }
            copy.add(value);
        }
        Collections.sort(copy, new Comparator<ElectricalBlockContract>() {
            @Override
            public int compare(ElectricalBlockContract first,
                    ElectricalBlockContract second) {
                return first.getDescriptor().getInstanceKey().compareTo(
                        second.getDescriptor().getInstanceKey());
            }
        });
        for (int index = 1; index < copy.size(); index++) {
            String previous = copy.get(index - 1).getDescriptor().getInstanceKey();
            String current = copy.get(index).getDescriptor().getInstanceKey();
            if (previous.equals(current)) {
                throw new IllegalArgumentException(
                        "Duplicate block instance key " + current);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<ElectricalConnection> immutableConnections(
            Collection<ElectricalConnection> values) {
        if (values == null) {
            throw new IllegalArgumentException("Electrical connections are required");
        }
        ArrayList<ElectricalConnection> copy =
                new ArrayList<ElectricalConnection>();
        for (ElectricalConnection value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Electrical connection is required");
            }
            copy.add(value);
        }
        Collections.sort(copy, new Comparator<ElectricalConnection>() {
            @Override
            public int compare(ElectricalConnection first,
                    ElectricalConnection second) {
                return first.getId().compareTo(second.getId());
            }
        });
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).getId().equals(copy.get(index).getId())) {
                throw new IllegalArgumentException("Duplicate connection ID "
                        + copy.get(index).getId());
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<DeviceAdapterContract> immutableAdapters(
            Collection<DeviceAdapterContract> values) {
        if (values == null)
            throw new IllegalArgumentException("Device adapters are required");
        ArrayList<DeviceAdapterContract> copy =
                new ArrayList<DeviceAdapterContract>();
        for (DeviceAdapterContract value : values) {
            if (value == null)
                throw new IllegalArgumentException("Device adapter is required");
            copy.add(value);
        }
        Collections.sort(copy, new Comparator<DeviceAdapterContract>() {
            @Override public int compare(DeviceAdapterContract first,
                    DeviceAdapterContract second) {
                return first.getKey().compareTo(second.getKey());
            }
        });
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).getKey().equals(copy.get(index).getKey()))
                throw new IllegalArgumentException("Duplicate adapter "
                        + copy.get(index).getKey());
        }
        return Collections.unmodifiableList(copy);
    }
}
