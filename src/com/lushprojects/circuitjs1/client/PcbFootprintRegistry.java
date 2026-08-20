package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Collections;
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
        if (footprint.getPlacement().getPhysicalPackage() == null ||
                !physicalPackage.isEquivalentTo(footprint.getPlacement().getPhysicalPackage()) ||
                footprint.getPlacement().getPhysicalGeometry() == null ||
                !physicalPackage.acceptsGeometry(footprint.getPlacement().getPhysicalGeometry()))
            throw new IllegalStateException("PCB footprint provider did not consume package geometry: " +
                physicalPackage.getId());
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

    Vector<PhysicalPackage> getRegisteredPackages() {
        Vector<String> ids = new Vector<String>(packages.keySet());
        Collections.sort(ids);
        Vector<PhysicalPackage> result = new Vector<PhysicalPackage>();
        for (String id : ids)
            result.add(packages.get(id));
        return result;
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
            for (int index = 0; index < expected.size(); index++) {
                String padId = expected.get(index);
                PcbPadPlacement pad = footprint.getPad(padId);
                int separator = padId.lastIndexOf('.');
                String terminalId = separator < 0 ? null : padId.substring(separator + 1);
                PhysicalPackageGeometry.Terminal terminal =
                    footprint.getPlacement().getPhysicalGeometry().getTerminal(index);
                if (terminalId == null || terminal == null ||
                        !terminal.getTerminalId().equals(terminalId))
                    throw new IllegalStateException("PCB footprint terminal order mismatch for " +
                        component.getId() + ": " + pad.getPadId());
            }
        }
    }
}
