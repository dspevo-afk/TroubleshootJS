package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable declaration of the bounded construction-provider surface.
 *
 * <p>Each entry joins one electrical adapter with its physical adapter and,
 * where applicable, the role contribution and its versioned observation.
 * Package catalogs are dependencies of the declaration and are checked while
 * the registry is built.  No provider lookup is performed while standard
 * entries are being assembled, which keeps bootstrap independent of the
 * registry itself.</p>
 */
final class ConstructionProviderRegistry {
    private final List<Entry> entries;
    private final Map<Key, Entry> byKey;
    private final List<LowSideRoleFamily.Provider> lowSideProviders;
    private final String canonicalFingerprint;

    ConstructionProviderRegistry(Collection<Entry> values,
            PcbFootprintRegistry footprints, PhysicalPartRenderRegistry renderers) {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("Construction provider entries are required");
        if (footprints == null || renderers == null)
            throw new IllegalArgumentException("Construction provider package registries are required");

        TreeMap<Key, Entry> ordered = new TreeMap<Key, Entry>();
        for (Entry entry : values) {
            if (entry == null)
                throw new IllegalArgumentException("Construction provider entry is required");
            entry.validatePackages(footprints, renderers);
            Key key = new Key(entry.getProviderId(), entry.getVersion());
            if (ordered.put(key, entry) != null)
                throw new IllegalArgumentException("Duplicate construction provider " + key);
        }
        ArrayList<Entry> orderedEntries = new ArrayList<Entry>(ordered.values());
        this.entries = Collections.unmodifiableList(orderedEntries);
        this.byKey = Collections.unmodifiableMap(ordered);

        ArrayList<LowSideRoleFamily.Provider> roleProviders =
            new ArrayList<LowSideRoleFamily.Provider>();
        for (Entry entry : orderedEntries) {
            ControlledIndicatorBlockContributions.Provider contribution =
                entry.getControlledContribution();
            if (contribution != null && contribution.isLowSide())
                roleProviders.add(contribution);
        }
        this.lowSideProviders = roleProviders.isEmpty() ?
            Collections.<LowSideRoleFamily.Provider>emptyList() :
            LowSideRoleFamily.canonicalProviders(roleProviders);
        this.canonicalFingerprint = fingerprint(orderedEntries);
    }

    /** Lazily initialized standard registry; its factories never call back here. */
    static ConstructionProviderRegistry standard() {
        return StandardHolder.INSTANCE;
    }

    Entry get(String providerId, int version) {
        FunctionalBlockDescriptor.requireId(providerId, "provider.typeId");
        Entry result = byKey.get(new Key(providerId, version));
        if (result == null)
            throw new IllegalArgumentException("Unknown construction provider " +
                providerId + "@" + version);
        return result;
    }

    List<Entry> entries() { return entries; }

    List<LowSideRoleFamily.Provider> lowSideProviders() { return lowSideProviders; }

    /** Stable declaration string for generation dependency and conformance reports. */
    String canonicalFingerprint() { return canonicalFingerprint; }

    void validatePhysicalContribution(String providerId, int version,
            PhysicalConstructionContribution contribution) {
        validatePhysicalContribution(get(providerId, version), contribution);
    }

    void validatePhysicalContribution(Entry entry,
            PhysicalConstructionContribution contribution) {
        if (entry == null || contribution == null)
            throw new IllegalArgumentException("Physical provider contribution is required");
        if (contribution.getParts().isEmpty())
            throw new IllegalStateException("Physical provider returned no parts: " + entry);
        for (PhysicalConstructionPartDeclaration part : contribution.getParts()) {
            if (part == null)
                throw new IllegalStateException("Physical provider returned a null part: " + entry);
            if (!entry.getProviderId().equals(part.getProviderId()) ||
                    entry.getVersion() != part.getProviderVersion())
                throw new IllegalStateException("Physical contribution identity mismatch: " +
                    entry + " / " + part.getProviderId() + "@" + part.getProviderVersion());
            entry.requirePackage(part.getPhysicalPackage());
        }
    }

    private static String fingerprint(List<Entry> values) {
        StringBuilder result = new StringBuilder();
        for (Entry entry : values) {
            if (result.length() > 0) result.append(';');
            result.append(entry.getProviderId()).append('@').append(entry.getVersion())
                .append("|electrical=").append(identity(entry.getElectrical()))
                .append("|physical=").append(identity(entry.getPhysical()))
                .append("|contribution=").append(identity(entry.getControlledContribution()))
                .append("|observation=").append(identity(entry.getDriverObservation()))
                .append("|join=").append(entry.isDeviceJoin())
                .append("|packages=");
            boolean first = true;
            for (PhysicalPackage physicalPackage : entry.getPackages()) {
                if (!first) result.append(',');
                first = false;
                result.append(physicalPackage.getId());
            }
        }
        return result.toString();
    }

    private static String identity(ElectricalConstructionProvider provider) {
        return provider == null ? "-" : provider.getProviderId() + "@" + provider.getVersion();
    }

    private static String identity(PhysicalConstructionProvider provider) {
        return provider == null ? "-" : provider.getProviderId() + "@" + provider.getVersion();
    }

    private static String identity(ControlledIndicatorBlockContributions.Provider provider) {
        return provider == null ? "-" : provider.getTypeId() + "@" + provider.getVersion();
    }

    private static String identity(ControlledIndicatorDriverObservation observation) {
        return observation == null ? "-" : observation.getProviderId() + "@" +
            observation.getVersion();
    }

    static final class Entry {
        private final String providerId;
        private final int version;
        private final ElectricalConstructionProvider electrical;
        private final PhysicalConstructionProvider physical;
        private final ControlledIndicatorBlockContributions.Provider contribution;
        private final ControlledIndicatorDriverObservation observation;
        private final List<PhysicalPackage> packages;
        private final boolean deviceJoin;

        Entry(ElectricalConstructionProvider electrical,
                PhysicalConstructionProvider physical,
                ControlledIndicatorBlockContributions.Provider contribution,
                ControlledIndicatorDriverObservation observation,
                Collection<PhysicalPackage> packages, boolean deviceJoin) {
            if (physical == null)
                throw new IllegalArgumentException("Physical construction provider is required");
            this.providerId = FunctionalBlockDescriptor.requireId(physical.getProviderId(),
                "physical.providerId");
            if (physical.getVersion() < 1)
                throw new IllegalArgumentException("Invalid physical construction provider version");
            this.version = physical.getVersion();
            this.physical = physical;
            this.electrical = electrical;
            this.contribution = contribution;
            this.observation = observation;
            this.deviceJoin = deviceJoin;
            this.packages = immutablePackages(packages);
            validateCapabilities();
        }

        static Entry deviceJoin(PhysicalConstructionProvider physical,
                Collection<PhysicalPackage> packages) {
            return new Entry(null, physical, null, null, packages, true);
        }

        String getProviderId() { return providerId; }
        int getVersion() { return version; }
        ElectricalConstructionProvider getElectrical() { return electrical; }
        PhysicalConstructionProvider getPhysical() { return physical; }
        ControlledIndicatorBlockContributions.Provider getControlledContribution() {
            return contribution;
        }
        ControlledIndicatorDriverObservation getDriverObservation() { return observation; }
        List<PhysicalPackage> getPackages() { return packages; }
        boolean isDeviceJoin() { return deviceJoin; }

        ElectricalConstructionProvider requireElectrical() {
            if (electrical == null)
                throw new IllegalStateException("Construction provider has no electrical adapter: " +
                    this);
            return electrical;
        }

        ControlledIndicatorBlockContributions.Provider requireControlledContribution() {
            if (contribution == null)
                throw new IllegalStateException("Construction provider has no controlled contribution: " +
                    this);
            return contribution;
        }

        ControlledIndicatorDriverObservation requireDriverObservation() {
            if (observation == null)
                throw new IllegalStateException("Construction provider has no driver observation: " +
                    this);
            return observation;
        }

        void requirePackage(PhysicalPackage physicalPackage) {
            if (physicalPackage == null)
                throw new IllegalStateException("Physical provider returned no package: " + this);
            for (PhysicalPackage required : packages) {
                if (required.getId().equals(physicalPackage.getId())) {
                    if (required.isEquivalentTo(physicalPackage)) return;
                    throw new IllegalStateException("Physical package definition mismatch for " +
                        this + ": " + physicalPackage.getId());
                }
            }
            throw new IllegalStateException("Physical provider consumed undeclared package " +
                physicalPackage.getId() + " for " + this);
        }

        private void validatePackages(PcbFootprintRegistry footprints,
                PhysicalPartRenderRegistry renderers) {
            for (PhysicalPackage physicalPackage : packages) {
                if (!containsEquivalent(footprints.getRegisteredPackages(), physicalPackage) ||
                        footprints.getProvider(physicalPackage) == null)
                    throw new IllegalArgumentException("Missing PCB footprint provider for " +
                        physicalPackage.getId() + " in " + this);
                if (!containsEquivalent(renderers.getRegisteredPackages(), physicalPackage) ||
                        renderers.getProvider(physicalPackage) == null ||
                        renderers.getRenderer(physicalPackage, null) == null)
                    throw new IllegalArgumentException("Missing physical renderer for " +
                        physicalPackage.getId() + " in " + this);
            }
        }

        private void validateCapabilities() {
            if (deviceJoin) {
                if (electrical != null || contribution != null || observation != null)
                    throw new IllegalArgumentException("Device join cannot declare construction capabilities: " +
                        providerId);
                return;
            }
            if (electrical == null)
                throw new IllegalArgumentException("Paired construction provider has no electrical adapter: " +
                    providerId);
            requireIdentity(electrical.getProviderId(), electrical.getVersion(),
                "electrical adapter");
            if (contribution == null && observation != null)
                throw new IllegalArgumentException("Driver observation has no contribution: " +
                    providerId);
            if (contribution != null) {
                requireIdentity(contribution.getTypeId(), contribution.getVersion(),
                    "controlled contribution");
                boolean lowSide = contribution.isLowSide() ||
                    LowSideRoleFamily.ROLE_ID.equals(contribution.getRoleId());
                if (lowSide) {
                    if (!contribution.isLowSide() ||
                            !LowSideRoleFamily.ROLE_ID.equals(contribution.getRoleId()))
                        throw new IllegalArgumentException("Low-side contribution role is inconsistent: " +
                            providerId);
                    if (observation == null)
                        throw new IllegalArgumentException("Low-side contribution requires a driver observation: " +
                            providerId);
                } else if (observation != null) {
                    throw new IllegalArgumentException("Non-driver contribution cannot declare a driver observation: " +
                        providerId);
                }
            }
            if (observation != null)
                requireIdentity(observation.getProviderId(), observation.getVersion(),
                    "driver observation");
        }

        private void requireIdentity(String id, int candidateVersion, String capability) {
            if (!providerId.equals(id) || version != candidateVersion)
                throw new IllegalArgumentException("Mismatched " + capability + " for " +
                    providerId + "@" + version);
        }

        private static List<PhysicalPackage> immutablePackages(
                Collection<PhysicalPackage> values) {
            if (values == null || values.isEmpty())
                throw new IllegalArgumentException("Required physical packages are required");
            ArrayList<PhysicalPackage> sorted = new ArrayList<PhysicalPackage>();
            for (PhysicalPackage physicalPackage : values) {
                if (physicalPackage == null)
                    throw new IllegalArgumentException("Required physical package is missing");
                FunctionalBlockDescriptor.requireId(physicalPackage.getId(),
                    "required.packageId");
                for (PhysicalPackage previous : sorted)
                    if (previous.getId().equals(physicalPackage.getId()))
                        throw new IllegalArgumentException("Duplicate required physical package " +
                            physicalPackage.getId());
                sorted.add(physicalPackage);
            }
            Collections.sort(sorted, new Comparator<PhysicalPackage>() {
                @Override public int compare(PhysicalPackage first, PhysicalPackage second) {
                    return first.getId().compareTo(second.getId());
                }
            });
            return Collections.unmodifiableList(sorted);
        }

        private static boolean containsEquivalent(Collection<PhysicalPackage> values,
                PhysicalPackage expected) {
            for (PhysicalPackage value : values)
                if (expected.getId().equals(value.getId())) {
                    if (!expected.isEquivalentTo(value))
                        throw new IllegalArgumentException("Conflicting physical package definition: " +
                            expected.getId());
                    return true;
                }
            return false;
        }

        @Override public String toString() { return providerId + "@" + version; }
    }

    private static final class Key implements Comparable<Key> {
        private final String providerId;
        private final int version;

        Key(String providerId, int version) {
            this.providerId = providerId;
            this.version = version;
        }

        @Override public int compareTo(Key other) {
            int byId = providerId.compareTo(other.providerId);
            return byId != 0 ? byId : version < other.version ? -1 :
                version == other.version ? 0 : 1;
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return version == other.version && providerId.equals(other.providerId);
        }

        @Override public int hashCode() {
            return 31 * providerId.hashCode() + version;
        }

        @Override public String toString() { return providerId + "@" + version; }
    }

    private static final class StandardHolder {
        private static final ConstructionProviderRegistry INSTANCE = createStandard();

        private static ConstructionProviderRegistry createStandard() {
            List<Entry> values = Arrays.asList(
                new Entry(StandardElectricalConstructionProviders.createResistiveSource(),
                    StandardPhysicalConstructionProviders.createResistiveSource(), null, null,
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR), false),
                new Entry(StandardElectricalConstructionProviders.createResistiveLoad(),
                    StandardPhysicalConstructionProviders.createResistiveLoad(), null, null,
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR), false),
                new Entry(StandardElectricalConstructionProviders.createNmosDriver(
                        NmosDriverProfile.standard()),
                    StandardPhysicalConstructionProviders.createNmosDriver(
                        NmosDriverProfile.standard()),
                    ControlledIndicatorBlockContributions.nmosDriver(),
                    ControlledIndicatorDriverObservations.createNmos(
                        NmosDriverProfile.standard().getProviderId(),
                        NmosDriverProfile.standard().getVersion()),
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR,
                        PhysicalPackages.TO92_NMOS), false),
                new Entry(StandardElectricalConstructionProviders.createNpnDriver(),
                    StandardPhysicalConstructionProviders.createNpnDriver(),
                    ControlledIndicatorBlockContributions.npnDriver(),
                    ControlledIndicatorDriverObservations.createNpn(
                        ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID,
                        ControlledIndicatorBlockContributions.NPN_VERSION),
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR,
                        PhysicalPackages.TO92_NPN), false),
                new Entry(StandardElectricalConstructionProviders.createControlledLoad(),
                    StandardPhysicalConstructionProviders.createControlledLoad(),
                    ControlledIndicatorBlockContributions.load(), null,
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR,
                        PhysicalPackages.THROUGH_HOLE_LED), false),
                new Entry(StandardElectricalConstructionProviders.createSupplyPresent(),
                    StandardPhysicalConstructionProviders.createSupplyPresent(), null, null,
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR,
                        PhysicalPackages.THROUGH_HOLE_LED), false),
                Entry.deviceJoin(StandardPhysicalConstructionProviders.createResistiveDevice(),
                    Arrays.asList(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2)),
                Entry.deviceJoin(StandardPhysicalConstructionProviders.createControlledDevice(),
                    Arrays.asList(PhysicalPackages.THROUGH_HOLE_CONNECTOR_2)),
                new Entry(StandardElectricalConstructionProviders.createNmosDriver(
                        NmosDriverProfile.alternate()),
                    StandardPhysicalConstructionProviders.createNmosDriver(
                        NmosDriverProfile.alternate()),
                    AlternateNmosDriverProvider.create(),
                    ControlledIndicatorDriverObservations.createNmos(
                        NmosDriverProfile.alternate().getProviderId(),
                        NmosDriverProfile.alternate().getVersion()),
                    Arrays.asList(PhysicalPackages.AXIAL_RESISTOR,
                        PhysicalPackages.TO92_NMOS), false));
            return new ConstructionProviderRegistry(values,
                StandardPcbFootprintProviders.createRegistry(),
                StandardPhysicalPartRenderProviders.createRegistry());
        }
    }
}
