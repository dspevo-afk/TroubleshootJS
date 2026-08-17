package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Typed-neutral capability lookup used by workbench surfaces. */
final class WorkbenchCapabilityDiscovery {
    private WorkbenchCapabilityDiscovery() { }

    static Vector<WorkbenchCapabilityStrategy> discover(PhysicalPart part) {
        if (part == null)
            return new Vector<WorkbenchCapabilityStrategy>();
        Vector<WorkbenchCapabilityStrategy> result = new Vector<WorkbenchCapabilityStrategy>();
        for (Object capabilityValue : part.getCapabilities()) {
            PhysicalPartCapability capability = (PhysicalPartCapability) capabilityValue;
            if (capability != null && capability.getMetadata() != null)
                result.add(capability);
        }
        return result;
    }

    static Vector<WorkbenchCapabilityStrategy> discover(PhysicalPart part,
            WorkbenchOperation operation, WorkbenchCapabilityRegistry registry) {
        Vector<WorkbenchCapabilityStrategy> result = new Vector<WorkbenchCapabilityStrategy>();
        if (registry != null)
            for (WorkbenchCapabilityStrategy capability : registry.getAllCapabilities())
                if (capability != null && capability.getMetadata() != null &&
                        capability.supports(operation))
                    result.add(capability);
        if (part != null)
            for (Object capabilityValue : part.getCapabilities()) {
                PhysicalPartCapability capability = (PhysicalPartCapability) capabilityValue;
                if (capability != null && capability.getMetadata() != null &&
                        capability.supports(operation))
                    result.add(capability);
            }
        return result;
    }

    static WorkbenchCapabilityStrategy find(PhysicalPart part, WorkbenchOperation operation,
            WorkbenchCapabilityRegistry registry) {
        for (WorkbenchCapabilityStrategy capability : discover(part, operation, registry))
            if (capability.supports(operation))
                return capability;
        return null;
    }

    static boolean supports(PhysicalPart part, String capabilityId) {
        if (part == null || capabilityId == null)
            return false;
        for (Object capabilityValue : part.getCapabilities()) {
            PhysicalPartCapability capability = (PhysicalPartCapability) capabilityValue;
            if (capabilityId.equals(capability.getMetadata().getId()))
                return true;
        }
        return false;
    }

    static boolean supportsOperation(PhysicalPart part, String operationId) {
        if (part == null || operationId == null)
            return false;
        for (WorkbenchCapabilityStrategy capability : discover(part))
            if (operationId.equals(capability.getMetadata().getOperationId()))
                return true;
        return false;
    }

    static WorkbenchCapabilityMetadata getMetadata(PhysicalPart part, String operationId) {
        if (part == null || operationId == null)
            return null;
        for (WorkbenchCapabilityStrategy capability : discover(part))
            if (operationId.equals(capability.getMetadata().getOperationId()))
                return capability.getMetadata();
        return null;
    }
}
