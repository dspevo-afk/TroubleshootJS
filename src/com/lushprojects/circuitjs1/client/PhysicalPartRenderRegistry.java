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

    /** Materialized-part admission, also used for every later rendering dispatch. */
    PhysicalPartRenderer requireRenderer(PhysicalPackage physicalPackage, PhysicalPart<?> part) {
        if (physicalPackage == null || part == null)
            throw new IllegalArgumentException("Physical renderer requires an actual package and part");
        if (!physicalPackage.isEquivalentTo(part.getPackage()))
            throw new IllegalArgumentException("Physical render part package mismatch: " + part.getId());
        PhysicalPartRenderProvider provider = getProvider(physicalPackage);
        if (provider == null)
            throw new IllegalStateException("No physical render provider for package: " + physicalPackage.getId());
        PhysicalPartRenderer renderer = provider.getRenderer(part);
        if (renderer == null)
            throw new IllegalStateException("Physical render provider returned no renderer: " + physicalPackage.getId());
        return renderer;
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
