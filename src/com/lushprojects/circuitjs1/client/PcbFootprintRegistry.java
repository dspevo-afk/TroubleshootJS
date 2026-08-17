package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Random;
import java.util.Vector;

/** Typed registry for package providers. Component families register types here once. */
class PcbFootprintRegistry {
    private final HashMap<String, PcbFootprintProvider> providers =
        new HashMap<String, PcbFootprintProvider>();
    private final HashMap<String, PhysicalPackage> packages =
        new HashMap<String, PhysicalPackage>();

    void register(PhysicalPackage physicalPackage, PcbFootprintProvider provider) {
        if (physicalPackage == null || provider == null)
            throw new IllegalArgumentException("Invalid PCB footprint provider registration");
        registerDefinition(physicalPackage, provider);
    }

    /** Compatibility registration for a package ID, never for a BoardComponent type. */
    void register(String packageId, PcbFootprintProvider provider) {
        if (packageId == null || packageId.length() == 0 || provider == null)
            throw new IllegalArgumentException("Invalid PCB footprint provider registration");
        if (providers.containsKey(packageId))
            throw new IllegalArgumentException("Duplicate PCB footprint provider: " + packageId);
        providers.put(packageId, provider);
    }

    PcbFootprint create(BoardComponent component, int x, int y, Random random,
            Rectangle workingOutline) {
        if (component == null)
            throw new IllegalArgumentException("Missing board component for footprint");
        PhysicalPackage physicalPackage = component.getPhysicalPackage();
        PcbFootprintProvider provider = getProvider(physicalPackage);
        if (provider == null)
            throw new IllegalStateException("No PCB footprint provider for package: " +
                (physicalPackage == null ? "null" : physicalPackage.getId()));
        PcbFootprint footprint = provider.create(component, x, y, random, workingOutline);
        if (footprint == null || !component.getId().equals(footprint.getPlacement().getComponentId()))
            throw new IllegalStateException("PCB footprint provider returned the wrong component");
        VectorPadValidator.validate(component, footprint);
        return footprint;
    }

    PcbFootprintProvider getProvider(String packageId) {
        return providers.get(packageId);
    }

    PcbFootprintProvider getProvider(PhysicalPackage physicalPackage) {
        if (physicalPackage == null)
            return null;
        PhysicalPackage registered = packages.get(physicalPackage.getId());
        if (registered != null && !registered.isEquivalentTo(physicalPackage))
            throw new IllegalArgumentException("Conflicting PCB package definition: " +
                physicalPackage.getId());
        return providers.get(physicalPackage.getId());
    }

    private void registerDefinition(PhysicalPackage physicalPackage,
            PcbFootprintProvider provider) {
        String packageId = physicalPackage.getId();
        if (providers.containsKey(packageId)) {
            PhysicalPackage registered = packages.get(packageId);
            if (registered != null && !registered.isEquivalentTo(physicalPackage))
                throw new IllegalArgumentException("Conflicting PCB package definition: " +
                    packageId);
            throw new IllegalArgumentException("Duplicate PCB footprint provider: " + packageId);
        }
        packages.put(packageId, physicalPackage);
        providers.put(packageId, provider);
    }

    private static class VectorPadValidator {
        static void validate(BoardComponent component, PcbFootprint footprint) {
            java.util.Vector<String> expected = component.getPadIds();
            java.util.Vector<PcbPadPlacement> actual = footprint.getPads();
            if (expected.size() != actual.size())
                throw new IllegalStateException("PCB footprint terminal count mismatch for " +
                    component.getId() + ": expected " + expected.size() + ", got " + actual.size());
            for (String padId : expected)
                footprint.getPad(padId);
        }
    }
}
