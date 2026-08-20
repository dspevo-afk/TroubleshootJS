package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Collections;
import java.util.Vector;

/** Typed package registry for physical-part rendering providers. */
final class PhysicalPartRenderRegistry {
    private final HashMap<String, PhysicalPartRenderProvider> providers =
        new HashMap<String, PhysicalPartRenderProvider>();
    private final HashMap<String, PhysicalPackage> packages =
        new HashMap<String, PhysicalPackage>();

    void register(PhysicalPackage physicalPackage, PhysicalPartRenderProvider provider) {
        if (physicalPackage == null || provider == null)
            throw new IllegalArgumentException("Duplicate or invalid physical render provider");
        String packageId = physicalPackage.getId();
        if (providers.containsKey(packageId)) {
            PhysicalPackage registered = packages.get(packageId);
            if (registered != null && !registered.isEquivalentTo(physicalPackage))
                throw new IllegalArgumentException("Conflicting physical render package: " +
                    packageId);
            throw new IllegalArgumentException("Duplicate physical render provider: " + packageId);
        }
        packages.put(packageId, physicalPackage);
        providers.put(packageId, provider);
    }

    PhysicalPartRenderProvider getProvider(PhysicalPackage physicalPackage) {
        if (physicalPackage == null)
            return null;
        PhysicalPackage registered = packages.get(physicalPackage.getId());
        if (registered != null && !registered.isEquivalentTo(physicalPackage))
            throw new IllegalArgumentException("Conflicting physical render package: " +
                physicalPackage.getId());
        return providers.get(physicalPackage.getId());
    }

    PhysicalPartRenderer getRenderer(PhysicalPackage physicalPackage, PhysicalPart<?> part) {
        PhysicalPartRenderProvider provider = getProvider(physicalPackage);
        return provider == null ? null : provider.getRenderer(part);
    }

    boolean hasProvider(PhysicalPackage physicalPackage) {
        return getProvider(physicalPackage) != null;
    }

    Vector<PhysicalPackage> getRegisteredPackages() {
        Vector<String> ids = new Vector<String>(packages.keySet());
        Collections.sort(ids);
        Vector<PhysicalPackage> result = new Vector<PhysicalPackage>();
        for (String id : ids)
            result.add(packages.get(id));
        return result;
    }
}
