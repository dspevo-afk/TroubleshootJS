package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

/** Typed package registry for physical-part rendering providers. */
final class PhysicalPartRenderRegistry {
    private final HashMap<PhysicalPackage, PhysicalPartRenderProvider> providers =
        new HashMap<PhysicalPackage, PhysicalPartRenderProvider>();

    void register(PhysicalPackage physicalPackage, PhysicalPartRenderProvider provider) {
        if (physicalPackage == null || provider == null || providers.containsKey(physicalPackage))
            throw new IllegalArgumentException("Duplicate or invalid physical render provider");
        providers.put(physicalPackage, provider);
    }

    PhysicalPartRenderProvider getProvider(PhysicalPackage physicalPackage) {
        return physicalPackage == null ? null : providers.get(physicalPackage);
    }

    PhysicalPartRenderer getRenderer(PhysicalPackage physicalPackage, PhysicalPart<?> part) {
        PhysicalPartRenderProvider provider = getProvider(physicalPackage);
        return provider == null ? null : provider.getRenderer(part);
    }

    boolean hasProvider(PhysicalPackage physicalPackage) {
        return getProvider(physicalPackage) != null;
    }
}
