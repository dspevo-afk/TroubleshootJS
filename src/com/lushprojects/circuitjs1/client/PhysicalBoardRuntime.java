package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Composition-root registry for physical board design, slots, and installed parts. */
final class PhysicalBoardRuntime {
    private final TroubleshootBoard board;
    private final HashMap<String, PhysicalBoardSlot> slotsByComponent =
        new HashMap<String, PhysicalBoardSlot>();
    private final Vector<String> slotOrder = new Vector<String>();
    private final HashMap<String, PhysicalPart<?>> partsById =
        new HashMap<String, PhysicalPart<?>>();
    private final Vector<String> partOrder = new Vector<String>();
    private final HashMap<String, Vector<String>> inventoryPartIds =
        new HashMap<String, Vector<String>>();
    private final HashMap<String, String> inventoryByPartId =
        new HashMap<String, String>();
    private final HashMap<String, Integer> nextPartSerials =
        new HashMap<String, Integer>();
    private final HashMap<String, PhysicalBoardRuntimeCapability> capabilities =
        new HashMap<String, PhysicalBoardRuntimeCapability>();
    private final Vector<String> capabilityOrder = new Vector<String>();
    private final HashMap<String, PhysicalSlotMutationProvider> mutationProviders =
        new HashMap<String, PhysicalSlotMutationProvider>();
    private final WorkbenchCapabilityRegistry workbenchCapabilityRegistry =
        new WorkbenchCapabilityRegistry();

    PhysicalBoardRuntime(TroubleshootBoard board) {
        if (board == null)
            throw new IllegalArgumentException("Missing physical board");
        this.board = board;
    }

    PhysicalBoardSlot createSlot(String componentId) {
        if (slotsByComponent.containsKey(componentId))
            throw new IllegalArgumentException("Duplicate physical board slot: " + componentId);
        PhysicalBoardSlot slot = new PhysicalBoardSlot(board, componentId);
        slot.setRuntime(this);
        slotsByComponent.put(componentId, slot);
        slotOrder.add(componentId);
        return slot;
    }

    void registerSlot(PhysicalBoardSlot slot) {
        if (slot == null || slotsByComponent.containsKey(slot.getComponentId()))
            throw new IllegalArgumentException("Duplicate physical board slot");
        slot.setRuntime(this);
        slotsByComponent.put(slot.getComponentId(), slot);
        slotOrder.add(slot.getComponentId());
    }

    PhysicalBoardSlot getSlot(String componentId) { return slotsByComponent.get(componentId); }

    Vector<PhysicalBoardSlot> getSlots() {
        Vector<PhysicalBoardSlot> result = new Vector<PhysicalBoardSlot>();
        for (String id : slotOrder) result.add(slotsByComponent.get(id));
        return result;
    }

    Vector<PhysicalPart> getPhysicalParts() {
        Vector<PhysicalPart> result = new Vector<PhysicalPart>();
        for (String id : partOrder) result.add(partsById.get(id));
        return result;
    }

    PhysicalPart<?> getInstalledPart(String componentId) {
        PhysicalBoardSlot slot = getSlot(componentId);
        return slot == null ? null : slot.getInstalledPart();
    }

    PhysicalPart<?> getPart(String partId) {
        return partsById.get(partId);
    }

    void registerPart(PhysicalPart<?> part) {
        validatePartIdentity(part);
        PhysicalPart<?> existing = partsById.get(part.getId());
        if (existing == null) {
            partsById.put(part.getId(), part);
            partOrder.add(part.getId());
        }
    }

    void addInventoryPart(String inventoryId, PhysicalPart<?> part) {
        if (inventoryId == null || inventoryId.length() == 0)
            throw new IllegalArgumentException("Missing physical inventory identity");
        validatePartIdentity(part);
        Vector<String> existingPartIds = inventoryPartIds.get(inventoryId);
        if (existingPartIds != null && existingPartIds.contains(part.getId()))
            throw new IllegalArgumentException("Duplicate inventory part: " + part.getId());
        String existingInventoryId = inventoryByPartId.get(part.getId());
        if (existingInventoryId != null)
            throw new IllegalArgumentException("Physical part already belongs to inventory " +
                existingInventoryId + ": " + part.getId());
        Vector<String> updatedPartIds = existingPartIds == null ? new Vector<String>() :
            new Vector<String>(existingPartIds);
        updatedPartIds.add(part.getId());
        PhysicalPart<?> existingPart = partsById.get(part.getId());
        if (existingPart == null) {
            partsById.put(part.getId(), part);
            partOrder.add(part.getId());
        }
        inventoryPartIds.put(inventoryId, updatedPartIds);
        inventoryByPartId.put(part.getId(), inventoryId);
    }

    <P extends PhysicalPart<?>> P acquireInventoryPart(String inventoryId, String idNamespace,
            PhysicalPartIdentityFactory<P> factory) {
        if (inventoryId == null || inventoryId.length() == 0 || idNamespace == null ||
                idNamespace.length() == 0 || factory == null)
            throw new IllegalArgumentException("Invalid physical part acquisition context");
        Integer next = nextPartSerials.get(idNamespace);
        int serial = next == null ? 0 : next.intValue();
        String partId = idNamespace + "_" + serial;
        if (partsById.containsKey(partId))
            throw new IllegalStateException("Allocated physical part identity already exists: " +
                partId);
        Vector<String> existingPartIds = inventoryPartIds.get(inventoryId);
        if (existingPartIds != null && existingPartIds.contains(partId))
            throw new IllegalStateException("Allocated inventory identity already exists: " + partId);
        if (inventoryByPartId.containsKey(partId))
            throw new IllegalStateException("Allocated physical part already belongs to inventory: " +
                partId);

        P part = factory.create(partId);
        if (part == null || !partId.equals(part.getId()))
            throw new IllegalStateException("Physical part factory changed its allocated identity");
        validatePartIdentity(part);

        Vector<String> updatedPartIds = existingPartIds == null ? new Vector<String>() :
            new Vector<String>(existingPartIds);
        updatedPartIds.add(partId);
        partsById.put(partId, part);
        partOrder.add(partId);
        inventoryPartIds.put(inventoryId, updatedPartIds);
        inventoryByPartId.put(partId, inventoryId);
        nextPartSerials.put(idNamespace, Integer.valueOf(serial + 1));
        return part;
    }

    PhysicalPart<?> getInventoryPart(String inventoryId, String partId) {
        Vector<String> partIds = inventoryPartIds.get(inventoryId);
        if (partIds == null || !partIds.contains(partId))
            throw new IllegalArgumentException("Unknown physical inventory part: " + partId);
        return partsById.get(partId);
    }

    Vector<PhysicalPart<?>> getInventoryParts(String inventoryId) {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        Vector<String> partIds = inventoryPartIds.get(inventoryId);
        if (partIds == null)
            return result;
        for (String partId : partIds)
            result.add(partsById.get(partId));
        return result;
    }

    boolean inventoryContains(String inventoryId, String partId) {
        Vector<String> partIds = inventoryPartIds.get(inventoryId);
        return partIds != null && partIds.contains(partId);
    }

    void registerCapability(PhysicalBoardRuntimeCapability capability) {
        if (capability == null || capability.getCapabilityId() == null ||
                capability.getCapabilityId().length() == 0 ||
                capabilities.containsKey(capability.getCapabilityId()))
            throw new IllegalArgumentException("Duplicate or missing physical runtime capability");
        capabilities.put(capability.getCapabilityId(), capability);
        capabilityOrder.add(capability.getCapabilityId());
    }

    PhysicalBoardRuntimeCapability getCapability(String capabilityId) {
        return capabilities.get(capabilityId);
    }

    Vector<PhysicalBoardRuntimeCapability> getCapabilities() {
        Vector<PhysicalBoardRuntimeCapability> result =
            new Vector<PhysicalBoardRuntimeCapability>();
        for (String capabilityId : capabilityOrder)
            result.add(capabilities.get(capabilityId));
        return result;
    }

    void installRegisteredCapabilities(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double initialSimulationTime) {
        if (sim == null || instance == null || instance.getPhysicalBoardRuntime() != this ||
                modifications == null)
            throw new IllegalArgumentException("Invalid physical capability installation context");
        clearMutationProviders();
        for (PhysicalBoardRuntimeCapability capability : getCapabilities()) {
            if (!(capability instanceof PhysicalBoardInstallationProvider))
                continue;
            PhysicalSlotMutationProvider provider =
                ((PhysicalBoardInstallationProvider) capability).install(sim, instance,
                    modifications, initialSimulationTime);
            if (provider != null)
                registerMutationProvider(provider);
        }
    }

    Vector<WorkbenchPartsProvider> getWorkbenchPartsProviders() {
        Vector<WorkbenchPartsProvider> result = new Vector<WorkbenchPartsProvider>();
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof WorkbenchPartsProvider)
                result.add((WorkbenchPartsProvider) capability);
        return result;
    }

    WorkbenchPartsProvider getWorkbenchPartsProvider(String componentId) {
        if (componentId == null)
            return null;
        for (WorkbenchPartsProvider provider : getWorkbenchPartsProviders())
            if (componentId.equals(provider.getComponentId()))
                return provider;
        return null;
    }

    WorkbenchPartsProvider getWorkbenchPartsProviderForPart(String partId) {
        if (partId == null)
            return null;
        for (WorkbenchPartsProvider provider : getWorkbenchPartsProviders())
            if (provider.ownsPart(partId))
                return provider;
        return null;
    }

    void observeSimulationTime(double simulationTime) {
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof PhysicalBoardRuntimeLifecycle)
                ((PhysicalBoardRuntimeLifecycle) capability).observeSimulationTime(simulationTime);
    }

    void onBoardPowerStateChanged(BoardPowerState state) {
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof PhysicalBoardRuntimePowerLifecycle)
                ((PhysicalBoardRuntimePowerLifecycle) capability).onBoardPowerStateChanged(state);
    }

    ActiveMeasurementReadiness getActiveMeasurementReadiness(
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
            BoardPowerState powerState, boolean electricallyUnpowered) {
        ActiveMeasurementReadiness result = ActiveMeasurementReadiness.READY;
        for (PhysicalBoardRuntimeCapability capability : getCapabilities()) {
            if (!(capability instanceof ActiveMeasurementReadinessCapability))
                continue;
            ActiveMeasurementReadiness readiness =
                ((ActiveMeasurementReadinessCapability) capability).getActiveMeasurementReadiness(
                    red, black, powerState, electricallyUnpowered);
            if (!readiness.isReady())
                result = readiness;
        }
        return result;
    }

    boolean usesLiveDcVoltage(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof ActiveMeasurementReadinessCapability &&
                    ((ActiveMeasurementReadinessCapability) capability).usesLiveDcVoltage(red,
                        black))
                return true;
        return false;
    }

    void resetForBoardReset() {
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof PhysicalBoardRuntimeLifecycle)
                ((PhysicalBoardRuntimeLifecycle) capability).resetForBoardReset();
    }

    void synchronizeSimulationTime(double simulationTime) {
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof PhysicalBoardRuntimeLifecycle)
                ((PhysicalBoardRuntimeLifecycle) capability).synchronizeSimulationTime(
                    simulationTime);
    }

    void clearMutationProviders() {
        mutationProviders.clear();
        workbenchCapabilityRegistry.clearRuntimeCapabilities();
    }

    void registerMutationProvider(PhysicalSlotMutationProvider provider) {
        if (provider == null || provider.getComponentId() == null ||
                getSlot(provider.getComponentId()) == null ||
                mutationProviders.containsKey(provider.getComponentId()))
            throw new IllegalArgumentException("Duplicate or invalid physical mutation provider");
        mutationProviders.put(provider.getComponentId(), provider);
        workbenchCapabilityRegistry.register(provider);
    }

    WorkbenchCapabilityRegistry getWorkbenchCapabilityRegistry() {
        return workbenchCapabilityRegistry;
    }

    PhysicalSlotMutationProvider getMutationProvider(String componentId) {
        return mutationProviders.get(componentId);
    }

    PhysicalSlotMutationProvider getMutationProviderForPart(String partId) {
        if (partId == null)
            return null;
        for (PhysicalSlotMutationProvider provider : mutationProviders.values())
            if (provider.ownsPart(partId))
                return provider;
        return null;
    }

    void validatePartIdentity(PhysicalPart<?> part) {
        if (part == null || part.getId() == null || part.getId().length() == 0)
            throw new IllegalArgumentException("Missing physical part identity");
        PhysicalPart<?> existing = partsById.get(part.getId());
        if (existing != null && existing != part)
            throw new IllegalArgumentException("Duplicate physical part identity: " + part.getId());
    }

    void validate() {
        for (String componentId : board.getComponentIds()) {
            BoardComponent component = board.getComponent(componentId);
            PhysicalBoardSlot slot = slotsByComponent.get(componentId);
            if (slot == null)
                throw new IllegalStateException("Missing physical slot: " + componentId);
            if (!component.getPhysicalPackage().isEquivalentTo(slot.getPhysicalPackage()))
                throw new IllegalStateException("Slot package changed: " + componentId);
            if (!component.getPhysicalPackage().isConnector() && !slot.isOccupied())
                throw new IllegalStateException("Non-connector slot is empty: " + componentId);
            if (slot.isOccupied() && slot.getInstalledPart().getBoardSlot() != slot)
                throw new IllegalStateException("Part and slot mount state disagree: " + componentId);
        }
        for (Vector<String> partIds : inventoryPartIds.values())
            for (String partId : partIds)
                if (partsById.get(partId) == null ||
                        inventoryByPartId.get(partId) == null)
                    throw new IllegalStateException("Inventory references unknown part: " + partId);
        for (String partId : inventoryByPartId.keySet()) {
            Vector<String> partIds = inventoryPartIds.get(inventoryByPartId.get(partId));
            if (partIds == null || !partIds.contains(partId))
                throw new IllegalStateException("Physical part inventory ownership disagrees: " +
                    partId);
        }
    }
}
