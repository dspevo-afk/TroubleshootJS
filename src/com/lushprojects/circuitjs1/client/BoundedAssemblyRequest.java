package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
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
    static final int VERSION = 1;
    static final String GENERATOR_ID = "bounded-assembler";
    static final int GENERATOR_VERSION = 1;
    static final String INTENT_ID = "resistive-coupling";
    static final int INTENT_VERSION = 1;
    static final String PROFILE_ID = "developer-canary";
    static final int PROFILE_VERSION = 1;
    static final int GEOMETRY_VERSION = 3;
    static final int SUPPORTED_BLOCK_COUNT = 2;
    static final int SUPPORTED_COMPONENT_COUNT = 3;
    static final int SUPPORTED_DOMAIN_COUNT = 1;

    private final ChallengeDescriptor descriptor;
    private final List<ElectricalBlockContract> blocks;
    private final List<ElectricalConnection> connections;

    BoundedAssemblyRequest(ChallengeDescriptor descriptor,
            Collection<ElectricalBlockContract> blocks,
            Collection<ElectricalConnection> connections) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Assembly descriptor is required");
        }
        this.descriptor = descriptor;
        this.blocks = immutableBlocks(blocks);
        this.connections = immutableConnections(connections);
    }

    ChallengeDescriptor getDescriptor() { return descriptor; }
    List<ElectricalBlockContract> getBlocks() { return blocks; }
    List<ElectricalConnection> getConnections() { return connections; }

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
}
