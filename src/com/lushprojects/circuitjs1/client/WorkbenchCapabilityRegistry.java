package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Runtime/provider registry for executable workbench capabilities. */
final class WorkbenchCapabilityRegistry {
    private final Vector<WorkbenchCapabilityStrategy> runtimeCapabilities =
        new Vector<WorkbenchCapabilityStrategy>();
    private final Vector<WorkbenchCapabilityStrategy> developerCapabilities =
        new Vector<WorkbenchCapabilityStrategy>();

    void register(WorkbenchCapabilityStrategy capability) {
        if (capability == null || capability.getMetadata() == null ||
                capability.getMetadata().getId() == null ||
                capability.getMetadata().getId().length() == 0)
            throw new IllegalArgumentException("Invalid runtime workbench capability");
        for (WorkbenchCapabilityStrategy existing : runtimeCapabilities)
            if (existing.getMetadata().getId().equals(capability.getMetadata().getId()))
                throw new IllegalArgumentException("Duplicate runtime workbench capability: " +
                    capability.getMetadata().getId());
        runtimeCapabilities.add(capability);
    }

    void clearRuntimeCapabilities() { runtimeCapabilities.clear(); }

    Vector<WorkbenchCapabilityStrategy> getRuntimeCapabilities() {
        return new Vector<WorkbenchCapabilityStrategy>(runtimeCapabilities);
    }

    void registerDeveloperOnly(WorkbenchCapabilityStrategy capability) {
        if (capability == null || capability.getMetadata() == null ||
                capability.getMetadata().getId() == null ||
                !capability.getMetadata().getId().startsWith("DEVELOPER_"))
            throw new IllegalArgumentException("Invalid developer workbench capability");
        for (WorkbenchCapabilityStrategy existing : developerCapabilities)
            if (existing.getMetadata().getId().equals(capability.getMetadata().getId()))
                throw new IllegalArgumentException("Duplicate developer workbench capability: " +
                    capability.getMetadata().getId());
        developerCapabilities.add(capability);
    }

    Vector<WorkbenchCapabilityStrategy> getDeveloperCapabilities() {
        return new Vector<WorkbenchCapabilityStrategy>(developerCapabilities);
    }

    Vector<WorkbenchCapabilityStrategy> getAllCapabilities() {
        Vector<WorkbenchCapabilityStrategy> result = getRuntimeCapabilities();
        result.addAll(developerCapabilities);
        return result;
    }
}
