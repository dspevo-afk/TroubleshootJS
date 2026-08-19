package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Admission boundary for physical ownership and reachable service actions. */
final class GeneratedFaultServiceabilityAdmission {
    private GeneratedFaultServiceabilityAdmission() { }

    static boolean isAdmitted(GeneratedFaultCandidate candidate) {
        return candidate != null && candidate.isCompatible() &&
            candidate.getServiceability() != null &&
            candidate.getServiceability().isAdmissible();
    }

    static void validateCandidate(GeneratedFaultCandidate candidate) {
        if (candidate == null || candidate.getServiceability() == null)
            throw new IllegalArgumentException("Fault candidate has no physical serviceability contract");
        GeneratedFaultServiceability serviceability = candidate.getServiceability();
        if (!serviceability.isAdmissible())
            throw new IllegalArgumentException("Fault candidate has no reachable repair primitive: " +
                candidate.getFault().getId());
        validateLocusIdentity(serviceability.getLocus());
    }

    static void validate(GeneratedBoardInstance instance, GeneratedFaultBinding binding) {
        if (instance == null || binding == null)
            throw new IllegalArgumentException("Missing physical fault admission context");
        GeneratedFaultServiceability serviceability = binding.getServiceability();
        if (serviceability == null)
            throw new IllegalArgumentException("Selected fault has no physical serviceability contract");
        validateCandidate(new GeneratedFaultCandidate(binding, true));
        GeneratedFaultLocus locus = serviceability.getLocus();
        validateLocusIdentity(locus);
        if (locus.getType() == GeneratedFaultLocusType.TRACE_SEGMENT)
            throw new IllegalArgumentException("Trace fault has no admitted physical path owner: " +
                locus.getPathId());
        BoardComponent component = instance.getBoard().getComponent(locus.getComponentId());
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(locus.getComponentId());
        if (component == null || slot == null || slot.getInstalledPart() == null)
            throw new IllegalArgumentException("Fault physical owner is not installed: " +
                locus.getOwnerId());
        PhysicalPart<?> installed = slot.getInstalledPart();
        if (!(installed instanceof GeneratedFaultOwningPart) ||
                !((GeneratedFaultOwningPart) installed).ownsGeneratedFault(binding))
            throw new IllegalArgumentException("Fault physical owner is not the original bound part: " +
                locus.getOwnerId());
        if (locus.getType() == GeneratedFaultLocusType.TERMINAL_ATTACHMENT ||
                locus.getType() == GeneratedFaultLocusType.CONNECTOR_CONTACT) {
            if (!slot.getTerminalIds().contains(locus.getTerminalId()) ||
                    instance.getConnectionBindings().getForComponentOrEmpty(
                        locus.getComponentId()).isEmpty())
                throw new IllegalArgumentException("Fault terminal has no physical connection binding: " +
                    locus.getOwnerId());
        }
        if (locus.getType() == GeneratedFaultLocusType.CONNECTOR_CONTACT &&
                !slot.getPhysicalPackage().isConnector())
            throw new IllegalArgumentException("Connector fault owner is not a connector: " +
                locus.getComponentId());
        if (serviceability.getRepairActionIds().contains(WorkbenchOperation.CATALOG_INSTALL) &&
                instance.getPhysicalBoardRuntime().getWorkbenchPartsProvider(
                    locus.getComponentId()) == null)
            throw new IllegalArgumentException("Fault owner has no replacement provider: " +
                locus.getComponentId());
        if (!GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(
                serviceability.getCustomerRetestOperationId()) ||
                instance.getOperationCatalog().find(GeneratedBoardOperationIds.CUSTOMER_RETEST) == null)
            throw new IllegalArgumentException("Fault has no Task 39 customer retest operation");
        validateExecutableRuntimeContracts(instance, binding, serviceability);
    }

    /**
     * Admission is allowed to inspect the installed runtime registry, but it
     * must never treat a provider/catalog name as an executable operation.
     * The Task 40 verifier additionally invokes these operations through the
     * real PcbWorkbenchController once the challenge is ready.
     */
    private static void validateExecutableRuntimeContracts(GeneratedBoardInstance instance,
            GeneratedFaultBinding binding, GeneratedFaultServiceability serviceability) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        if (runtime.getWorkbenchCapabilityRegistry().getRuntimeCapabilities().isEmpty())
            throw new IllegalArgumentException("Fault owner has no installed runtime capability");
        GeneratedFaultLocus locus = serviceability.getLocus();
        String componentId = locus.getComponentId();
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        PhysicalPart<?> installed = slot == null ? null : slot.getInstalledPart();
        validateObservationActions(instance, binding, serviceability, installed);
        validateWorkbenchActions(instance, serviceability, installed);
    }

    private static void validateObservationActions(GeneratedBoardInstance instance,
            GeneratedFaultBinding binding, GeneratedFaultServiceability serviceability,
            PhysicalPart<?> installed) {
        String componentId = serviceability.getLocus().getComponentId();
        Vector<GeneratedComponentConnectionBinding> connections =
            instance.getConnectionBindings().getForComponentOrEmpty(componentId);
        if (connections.isEmpty())
            throw new IllegalArgumentException("Fault observation has no physical terminals: " +
                componentId);
        for (String actionId : serviceability.getObservationActionIds()) {
            if (GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS.equals(actionId))
                continue;
            if (!GeneratedFaultServiceability.OBSERVE_PUBLIC_TERMINALS.equals(actionId))
                throw new IllegalArgumentException("Unknown fault observation action: " + actionId);
            if (installed == null)
                throw new IllegalArgumentException("Public fault observation has no installed part: " +
                    componentId);
            CircuitElm backing = instance.getComponentBindings().getSingleElement(componentId);
            for (GeneratedComponentConnectionBinding connection : connections) {
                if (!(connection.getComponentEndpoint() instanceof CircuitPostMeasurementEndpoint))
                    throw new IllegalArgumentException("Fault observation endpoint is not a public post: " +
                        connection.getPadId());
                CircuitPostMeasurementEndpoint endpoint =
                    (CircuitPostMeasurementEndpoint) connection.getComponentEndpoint();
                boolean publicTerminal = false;
                for (int terminal = 0; terminal < backing.getPostCount(); terminal++)
                    if (binding.isPublicTerminal(backing, endpoint, terminal)) {
                        publicTerminal = true;
                        break;
                    }
                if (!publicTerminal)
                    throw new IllegalArgumentException("Fault observation is not public at: " +
                        connection.getPadId());
            }
        }
    }

    private static void validateWorkbenchActions(GeneratedBoardInstance instance,
            GeneratedFaultServiceability serviceability, PhysicalPart<?> installed) {
        String componentId = serviceability.getLocus().getComponentId();
        WorkbenchCapabilityRegistry registry = instance.getPhysicalBoardRuntime()
            .getWorkbenchCapabilityRegistry();
        for (String actionId : serviceability.getIsolationActionIds())
            requireWorkbenchSupport(instance, registry, installed, componentId, actionId);
        for (String actionId : serviceability.getRepairActionIds())
            requireWorkbenchSupport(instance, registry, installed, componentId, actionId);
    }

    private static void requireWorkbenchSupport(GeneratedBoardInstance instance,
            WorkbenchCapabilityRegistry registry, PhysicalPart<?> installed, String componentId,
            String actionId) {
        WorkbenchOperation operation;
        if (WorkbenchOperation.CATALOG_INSTALL.equals(actionId)) {
            WorkbenchPartsProvider provider = instance.getPhysicalBoardRuntime()
                .getWorkbenchPartsProvider(componentId);
            if (provider == null || provider.getCatalogEntries().isEmpty())
                throw new IllegalArgumentException("No executable catalog replacement action: " +
                    componentId);
            operation = WorkbenchOperation.forCatalog(componentId,
                provider.getCatalogEntries().firstElement().getId());
        } else if (WorkbenchOperation.INSTALL.equals(actionId)) {
            operation = WorkbenchOperation.forPartAtSlot(actionId, installed, componentId);
        } else if (WorkbenchOperation.REMOVE.equals(actionId)) {
            operation = WorkbenchOperation.forPart(actionId, installed);
        } else if (WorkbenchOperation.LIFT_LEAD.equals(actionId) ||
                WorkbenchOperation.RECONNECT_LEAD.equals(actionId)) {
            String terminalId = instance.getFaultLocus().getTerminalId();
            if (terminalId == null)
                throw new IllegalArgumentException("Lead action has no stable terminal: " +
                    actionId);
            operation = WorkbenchOperation.forPartLead(actionId, installed, componentId,
                componentId + "." + terminalId);
        } else {
            throw new IllegalArgumentException("Unknown fault workbench action: " + actionId);
        }
        WorkbenchCapabilityStrategy capability = WorkbenchCapabilityDiscovery.find(installed,
            operation, registry);
        if (capability == null || !capability.supports(operation))
            throw new IllegalArgumentException("Fault action has no executable provider: " +
                componentId + "/" + actionId);
    }

    static void validateExecutableRuntime(CirSim sim, GeneratedBoardInstance instance,
            GeneratedFaultBinding binding) {
        if (sim == null || instance == null || sim.getGeneratedBoardInstance() != instance ||
                sim.pcbWorkbenchController == null)
            throw new IllegalArgumentException("Task 40 runtime workbench is not installed");
        validate(instance, binding);
        GeneratedFaultServiceability serviceability = binding.getServiceability();
        requireControllerObservationSupport(sim.pcbWorkbenchController, instance,
            serviceability);
        for (String actionId : serviceability.getIsolationActionIds())
            requireControllerSupport(sim.pcbWorkbenchController, instance, actionId);
        for (String actionId : serviceability.getRepairActionIds())
            requireControllerSupport(sim.pcbWorkbenchController, instance, actionId);
    }

    private static void requireControllerObservationSupport(PcbWorkbenchController controller,
            GeneratedBoardInstance instance, GeneratedFaultServiceability serviceability) {
        String componentId = serviceability.getLocus().getComponentId();
        for (GeneratedComponentConnectionBinding connection : instance.getConnectionBindings()
                .getForComponentOrEmpty(componentId)) {
            PcbWorkbenchRenderer renderer = controller.getRenderer();
            Point point = renderer.getPadPoint(connection.getPadId());
            ProbeTarget target = controller.findProbeTarget(point.x, point.y);
            if (!(target instanceof BoardPadProbeTarget) || !target.isValid() ||
                    !connection.getPadId().equals(((BoardPadProbeTarget) target).getPadId()))
                throw new IllegalArgumentException("Fault observation has no executable PCB probe: " +
                    connection.getPadId());
        }
    }

    private static void requireControllerSupport(PcbWorkbenchController controller,
            GeneratedBoardInstance instance, String actionId) {
        GeneratedFaultLocus locus = instance.getFaultLocus();
        PhysicalPart<?> installed = instance.getPhysicalBoardRuntime()
            .getInstalledPart(locus.getComponentId());
        WorkbenchOperation operation;
        if (WorkbenchOperation.CATALOG_INSTALL.equals(actionId)) {
            WorkbenchPartsProvider provider = instance.getPhysicalBoardRuntime()
                .getWorkbenchPartsProvider(locus.getComponentId());
            operation = WorkbenchOperation.forCatalog(locus.getComponentId(),
                provider.getCatalogEntries().firstElement().getId());
        } else if (WorkbenchOperation.INSTALL.equals(actionId)) {
            operation = WorkbenchOperation.forPartAtSlot(actionId, installed,
                locus.getComponentId());
        } else if (WorkbenchOperation.REMOVE.equals(actionId)) {
            operation = WorkbenchOperation.forPart(actionId, installed);
        } else {
            operation = WorkbenchOperation.forPartLead(actionId, installed,
                locus.getComponentId(), locus.getComponentId() + "." + locus.getTerminalId());
        }
        if (WorkbenchCapabilityDiscovery.find(installed, operation,
                instance.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry()) == null)
            throw new IllegalArgumentException("Controller has no fault action provider: " +
                actionId);
    }

    static Vector<String> getPhysicalOwnerIds(Vector<GeneratedFaultCandidate> candidates) {
        Vector<String> result = new Vector<String>();
        if (candidates != null)
            for (GeneratedFaultCandidate candidate : candidates)
                if (isAdmitted(candidate)) {
                    String ownerId = candidate.getServiceability().getLocus().getOwnerId();
                    if (!result.contains(ownerId)) result.add(ownerId);
                }
        Collections.sort(result);
        return result;
    }

    static int getPhysicalOwnerCount(Vector<GeneratedFaultCandidate> candidates) {
        return getPhysicalOwnerIds(candidates).size();
    }

    private static void validateLocusIdentity(GeneratedFaultLocus locus) {
        if (locus == null || locus.getOwnerId() == null || locus.getOwnerId().length() == 0)
            throw new IllegalArgumentException("Fault locus has no stable physical owner");
        if (locus.getType() == GeneratedFaultLocusType.TRACE_SEGMENT &&
                locus.getPathId() == null)
            throw new IllegalArgumentException("Trace locus has no stable path ID");
        if ((locus.getType() == GeneratedFaultLocusType.TERMINAL_ATTACHMENT ||
                locus.getType() == GeneratedFaultLocusType.CONNECTOR_CONTACT) &&
                locus.getTerminalId() == null)
            throw new IllegalArgumentException("Terminal locus has no stable terminal ID");
    }
}
