package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Developer-only proof that production slot providers own generic workbench actions. */
final class WorkbenchCapabilityDeveloperVerifier {
    private WorkbenchCapabilityDeveloperVerifier() { }

    static void verifyRegisteredProviders(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("Workbench capability verification requires a board");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<WorkbenchPartsProvider> providers = runtime.getWorkbenchPartsProviders();
        require(!providers.isEmpty(), "No production workbench providers were registered");
        BoardPowerState priorPower = sim.getBoardPowerController().getState();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (challenge != null)
            challenge.beginDeveloperVerificationScope();
        try {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            WorkbenchCapabilityContext context = sim.pcbWorkbenchController;
            for (WorkbenchPartsProvider provider : providers)
                exerciseProvider(instance, runtime, provider, context);
        } finally {
            if (priorPower == BoardPowerState.POWERED)
                sim.setBoardPowerState(BoardPowerState.POWERED);
            if (challenge != null)
                challenge.endDeveloperVerificationScope();
        }
    }

    private static void exerciseProvider(GeneratedBoardInstance instance,
            PhysicalBoardRuntime runtime, WorkbenchPartsProvider provider,
            WorkbenchCapabilityContext context) {
        String componentId = provider.getComponentId();
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        require(slot != null && slot.getInstalledPart() != null,
            "Production workbench provider has no installed part: " + componentId);
        PhysicalPart<?> original = slot.getInstalledPart();
        WorkbenchOperation remove = WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original);
        WorkbenchCapabilityStrategy removeCapability = WorkbenchCapabilityDiscovery.find(original,
            remove, runtime.getWorkbenchCapabilityRegistry());
        require(removeCapability instanceof PhysicalSlotMutationProvider &&
                removeCapability.getMetadata() != null &&
                removeCapability.getOperationLabel(remove).length() > 0,
            "Registered provider did not expose generic remove metadata: " + componentId);
        require(removeCapability.isAvailable(remove, context) &&
                removeCapability.invoke(remove, context) && slot.getInstalledPart() == null,
            "Registered provider did not execute generic remove: " + componentId);

        Vector<WorkbenchCatalogEntry> catalogEntries = provider.getCatalogEntries();
        require(!catalogEntries.isEmpty(), "Production provider has no catalog metadata: " +
            componentId);
        WorkbenchOperation catalog = WorkbenchOperation.forCatalog(componentId,
            catalogEntries.firstElement().getId());
        WorkbenchCapabilityStrategy catalogCapability = WorkbenchCapabilityDiscovery.find(null,
            catalog, runtime.getWorkbenchCapabilityRegistry());
        require(catalogCapability instanceof PhysicalSlotMutationProvider &&
                catalogCapability.getOperationLabel(catalog).length() > 0 &&
                catalogCapability.isAvailable(catalog, context),
            "Registered provider did not expose generic catalog action: " + componentId);

        PhysicalPart<?> loose = provider.getPart(original.getId());
        require(loose == original && !loose.isInstalled(),
            "Generic remove did not preserve the loose physical part: " + componentId);
        WorkbenchOperation inspect = WorkbenchOperation.forPart(
            WorkbenchOperation.INSPECT_LOOSE, loose);
        WorkbenchCapabilityStrategy inspectCapability = WorkbenchCapabilityDiscovery.find(loose,
            inspect, runtime.getWorkbenchCapabilityRegistry());
        require(inspectCapability != null && inspectCapability.getOperationLabel(inspect).length() > 0 &&
                inspectCapability.isAvailable(inspect, context) &&
                inspectCapability.invoke(inspect, context),
            "Production loose-part inspection capability was not executable: " + componentId);

        WorkbenchOperation install = WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL,
            loose, componentId);
        WorkbenchCapabilityStrategy installCapability = WorkbenchCapabilityDiscovery.find(loose,
            install, runtime.getWorkbenchCapabilityRegistry());
        require(installCapability instanceof PhysicalSlotMutationProvider &&
                installCapability.getOperationLabel(install).length() > 0 &&
                installCapability.isAvailable(install, context) &&
                installCapability.invoke(install, context) && slot.getInstalledPart() == original,
            "Registered provider did not execute generic reinstall: " + componentId);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
