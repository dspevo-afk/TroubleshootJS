package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * The bounded functional role shared by the NMOS and NPN implementations.
 *
 * <p>This class owns only role admission and deterministic implementation
 * selection.  A provider still owns its descriptor and local recipes; the
 * generic assembler consumes the selected provider through this small typed
 * seam.</p>
 */
final class LowSideRoleFamily {
    static final int VERSION = 1;
    static final String ROLE_ID = "low-side-driver";
    static final int TOPOLOGY_REVISION = 1;
    static final String TOPOLOGY_KEY = "low-side-implementation";

    static final double SUPPLY_MINIMUM_VOLTS = 4.75;
    static final double SUPPLY_MAXIMUM_VOLTS = 5.25;
    static final double CONTROL_HIGH_MINIMUM_VOLTS = 4.75;
    static final double CONTROL_LOW_MAXIMUM_VOLTS = 0.1;
    static final double CONTROL_CAPACITY_AMPS = 0.002;
    static final double SINK_CAPACITY_AMPS = 0.020;
    static final double LOAD_DEMAND_AMPS = 0.016;
    static final double ON_SINK_MAXIMUM_VOLTS = 0.8;
    static final double OFF_SINK_MAXIMUM_VOLTS = 5.5;
    static final double OFF_LEAKAGE_MAXIMUM_AMPS = 0.000001;

    /** Typed operating evidence supplied by the device owner. */
    static final class Envelope {
        private final boolean lowSide;
        private final double supplyMinimumVolts;
        private final double supplyMaximumVolts;
        private final double controlHighMinimumVolts;
        private final double controlLowMaximumVolts;
        private final double controlCapacityAmps;
        private final double sinkCapacityAmps;
        private final double loadDemandAmps;

        Envelope(boolean lowSide, double supplyMinimumVolts,
                double supplyMaximumVolts, double controlHighMinimumVolts,
                double controlLowMaximumVolts, double controlCapacityAmps,
                double sinkCapacityAmps, double loadDemandAmps) {
            requireFinite(supplyMinimumVolts, "supplyMinimumVolts");
            requireFinite(supplyMaximumVolts, "supplyMaximumVolts");
            requireFinite(controlHighMinimumVolts, "controlHighMinimumVolts");
            requireFinite(controlLowMaximumVolts, "controlLowMaximumVolts");
            requireFinitePositive(controlCapacityAmps, "controlCapacityAmps");
            requireFinitePositive(sinkCapacityAmps, "sinkCapacityAmps");
            requireFinitePositive(loadDemandAmps, "loadDemandAmps");
            if (supplyMinimumVolts > supplyMaximumVolts ||
                    controlLowMaximumVolts >= controlHighMinimumVolts)
                throw new IllegalArgumentException("Malformed low-side envelope");
            this.lowSide = lowSide;
            this.supplyMinimumVolts = supplyMinimumVolts;
            this.supplyMaximumVolts = supplyMaximumVolts;
            this.controlHighMinimumVolts = controlHighMinimumVolts;
            this.controlLowMaximumVolts = controlLowMaximumVolts;
            this.controlCapacityAmps = controlCapacityAmps;
            this.sinkCapacityAmps = sinkCapacityAmps;
            this.loadDemandAmps = loadDemandAmps;
        }

        static Envelope standard() {
            return new Envelope(true, SUPPLY_MINIMUM_VOLTS,
                    SUPPLY_MAXIMUM_VOLTS, CONTROL_HIGH_MINIMUM_VOLTS,
                    CONTROL_LOW_MAXIMUM_VOLTS, CONTROL_CAPACITY_AMPS,
                    SINK_CAPACITY_AMPS, LOAD_DEMAND_AMPS);
        }

        boolean isLowSide() { return lowSide; }
        double getSupplyMinimumVolts() { return supplyMinimumVolts; }
        double getSupplyMaximumVolts() { return supplyMaximumVolts; }
        double getControlHighMinimumVolts() { return controlHighMinimumVolts; }
        double getControlLowMaximumVolts() { return controlLowMaximumVolts; }
        double getControlCapacityAmps() { return controlCapacityAmps; }
        double getSinkCapacityAmps() { return sinkCapacityAmps; }
        double getLoadDemandAmps() { return loadDemandAmps; }
    }

    /** A provider-owned implementation of the role. */
    interface Provider {
        String getTypeId();
        int getVersion();
        String getRoleId();
        boolean isCompatible(Envelope envelope);
        boolean isLowSide();
        double getSupplyMinimumVolts();
        double getSupplyMaximumVolts();
        double getControlHighMinimumVolts();
        double getControlLowMaximumVolts();
        double getControlDemandAmps();
        double getRequiredBaseDriveAmps();
        double getSinkCapacityAmps();
        double getLoadDemandAmps();
        double getOnSinkMaximumVolts();
        double getOffSinkMaximumVolts();
        double getOffLeakageMaximumAmps();
        String getModelId();
        ComposedBlockContribution create(String instanceKey);
        ComposedBlockContribution create(String instanceKey,
                ControlledIndicatorBlockContributions.FaultSpec fault);
    }

    private LowSideRoleFamily() { }

    /** Canonically registered role implementations. */
    static List<Provider> providers() {
        return canonicalProviders(Arrays.asList(
                ControlledIndicatorBlockContributions.nmosDriver(),
                ControlledIndicatorBlockContributions.npnDriver()));
    }

    /** Validate and canonically order a caller-supplied registration set. */
    static List<Provider> canonicalProviders(Collection<? extends Provider> values) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("Low-side providers are required");
        TreeMap<String, Provider> byType = new TreeMap<String, Provider>();
        for (Provider provider : values) {
            if (provider == null)
                throw new IllegalArgumentException("Low-side provider is required");
            String typeId = FunctionalBlockDescriptor.requireId(
                    provider.getTypeId(), "provider.typeId");
            if (provider.getVersion() != VERSION)
                throw new IllegalArgumentException("Unsupported low-side provider version "
                        + typeId + "@" + provider.getVersion());
            if (!ROLE_ID.equals(provider.getRoleId()))
                throw new IllegalArgumentException("Provider has the wrong functional role "
                        + typeId);
            if (byType.put(typeId, provider) != null)
                throw new IllegalArgumentException("Duplicate low-side provider " + typeId);
        }
        return Collections.unmodifiableList(
                new ArrayList<Provider>(byType.values()));
    }

    static Provider resolve(String typeId, int version) {
        FunctionalBlockDescriptor.requireId(typeId, "provider.typeId");
        for (Provider provider : providers()) {
            if (typeId.equals(provider.getTypeId()) &&
                    version == provider.getVersion()) return provider;
        }
        throw new IllegalArgumentException("Unsupported low-side provider "
                + typeId + "@" + version);
    }

    static Provider select(long seed, String instanceKey, Envelope envelope) {
        return select(seed, instanceKey, envelope, providers());
    }

    /**
     * Select from only compatible providers, after canonical registration.
     * The stream tuple is deliberately instance scoped and uses TOPOLOGY only;
     * no fault decision can influence the healthy implementation choice.
     */
    static Provider select(long seed, String instanceKey, Envelope envelope,
            Collection<? extends Provider> candidates) {
        String key = FunctionalBlockDescriptor.requireId(instanceKey,
                "provider.instanceKey");
        if (envelope == null)
            throw new IllegalArgumentException("Low-side envelope is required");
        List<Provider> registered = canonicalProviders(candidates);
        ArrayList<Provider> compatible = new ArrayList<Provider>();
        ArrayList<String> compatibleIds = new ArrayList<String>();
        for (Provider provider : registered) {
            if (provider.isCompatible(envelope)) {
                compatible.add(provider);
                compatibleIds.add(provider.getTypeId());
            }
        }
        if (compatible.isEmpty())
            throw new IllegalArgumentException("No compatible low-side implementation");
        NamedRandomStreams streams = new NamedRandomStreams(1, seed,
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION);
        String selected = NamedRandomStreams.select(streams.blockSeed(key,
                NamedRandomStreams.Concern.TOPOLOGY, TOPOLOGY_REVISION,
                TOPOLOGY_KEY), compatibleIds);
        for (Provider provider : compatible)
            if (selected.equals(provider.getTypeId())) return provider;
        throw new IllegalStateException("Selected low-side provider disappeared");
    }

    private static void requireFinite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException(field + " must be finite");
    }

    private static void requireFinitePositive(double value, String field) {
        requireFinite(value, field);
        if (value <= 0.0)
            throw new IllegalArgumentException(field + " must be positive");
    }
}
