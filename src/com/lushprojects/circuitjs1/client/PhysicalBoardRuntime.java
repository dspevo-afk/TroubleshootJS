package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Collections;
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
    /**
     * A resistor mutation is the only mutable composition transaction currently
     * admitted by the composed developer route.  The flag is deliberately
     * runtime-owned so every public provider can reject a re-entrant operation
     * without introducing a second global transaction owner.
     */
    private boolean mutationInProgress;

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

    TroubleshootBoard getBoard() { return board; }

    boolean isMutationInProgress() { return mutationInProgress; }

    void beginMutation() {
        if (mutationInProgress)
            throw new BoardModificationRejectedException("A physical board mutation is already in progress");
        mutationInProgress = true;
    }

    void endMutation() {
        if (!mutationInProgress)
            throw new IllegalStateException("No physical board mutation is in progress");
        mutationInProgress = false;
    }

    /** Binds each package-backed board slot to its already-selected layout realization. */
    void bindGeometryRealizations(PcbBoardLayout layout) {
        if (layout == null)
            throw new IllegalArgumentException("Missing PCB board layout");
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            PhysicalBoardSlot slot = slotsByComponent.get(componentId);
            PcbComponentPlacement placement = layout.getComponent(componentId);
            if (component == null || component.getPhysicalPackage() == null || slot == null)
                throw new IllegalStateException("Missing package-backed physical slot: " +
                    componentId);
            if (placement == null)
                throw new IllegalStateException("Missing PCB placement for physical slot: " +
                    componentId);
            if (placement.getPhysicalPackage() == null ||
                    !component.getPhysicalPackage().isEquivalentTo(
                        placement.getPhysicalPackage()))
                throw new IllegalStateException("PCB placement package mismatch: " + componentId);
            slot.bindGeometryRealization(placement);
        }
    }

    Vector<PhysicalBoardSlot> getSlots() {
        Vector<PhysicalBoardSlot> result = new Vector<PhysicalBoardSlot>();
        for (String id : slotOrder) result.add(slotsByComponent.get(id));
        return result;
    }

    Vector<String> getSlotOrder() { return new Vector<String>(slotOrder); }

    Vector<PhysicalPart> getPhysicalParts() {
        Vector<PhysicalPart> result = new Vector<PhysicalPart>();
        for (String id : partOrder) result.add(partsById.get(id));
        return result;
    }

    Vector<String> getPartOrder() { return new Vector<String>(partOrder); }

    Vector<String> getPartIds() {
        Vector<String> result = new Vector<String>();
        result.addAll(partsById.keySet());
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

    Vector<String> getInventoryIds() {
        Vector<String> result = new Vector<String>();
        result.addAll(inventoryPartIds.keySet());
        return result;
    }

    Vector<String> getInventoryPartIds(String inventoryId) {
        Vector<String> partIds = inventoryPartIds.get(inventoryId);
        return partIds == null ? new Vector<String>() : new Vector<String>(partIds);
    }

    String getInventoryIdForPart(String partId) { return inventoryByPartId.get(partId); }

    boolean hasInventory(String inventoryId) { return inventoryPartIds.containsKey(inventoryId); }

    Integer getNextPartSerial(String idNamespace) { return nextPartSerials.get(idNamespace); }

    /**
     * Reverses one append-only catalog acquisition.  The caller must first
     * detach the part from any slot and unregister any canonical/stress views.
     * Every identity and cardinality check fails closed so an abort cannot
     * silently remove an unrelated inventory item.
     */
    void rollbackAcquiredInventoryPart(String inventoryId, String idNamespace,
            String partId, PhysicalPart<?> part, Integer previousSerial,
            boolean inventoryExistedBefore) {
        if (inventoryId == null || idNamespace == null || partId == null || part == null)
            throw new IllegalArgumentException("Invalid physical inventory rollback");
        if (part.isInstalled())
            throw new IllegalStateException("Cannot rollback an installed physical part: " + partId);
        if (partsById.get(partId) != part || inventoryByPartId.get(partId) == null ||
                !inventoryId.equals(inventoryByPartId.get(partId)))
            throw new IllegalStateException("Physical inventory rollback lost part ownership: " + partId);
        Vector<String> ids = inventoryPartIds.get(inventoryId);
        if (ids == null || count(ids, partId) != 1 || count(partOrder, partId) != 1)
            throw new IllegalStateException("Physical inventory rollback has non-unique part registration: " +
                partId);
        ids.remove(partId);
        if (!inventoryExistedBefore && ids.isEmpty())
            inventoryPartIds.remove(inventoryId);
        inventoryByPartId.remove(partId);
        partsById.remove(partId);
        partOrder.remove(partId);
        if (previousSerial == null)
            nextPartSerials.remove(idNamespace);
        else
            nextPartSerials.put(idNamespace, previousSerial);
    }

    private static int count(Vector<String> values, String expected) {
        int count = 0;
        for (String value : values)
            if (expected.equals(value)) count++;
        return count;
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

    Vector<String> getCapabilityOrder() { return new Vector<String>(capabilityOrder); }

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
            result = ActiveMeasurementReadiness.combine(result, readiness);
        }
        return result;
    }

    MeasurementReferencePolicy.Result assessMeasurementReference(MeasurementReferencePolicy.Mode mode,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        MeasurementReferencePolicy.Result result = MeasurementReferencePolicy.notApplicable();
        for (PhysicalBoardRuntimeCapability capability : getCapabilities())
            if (capability instanceof MeasurementReferenceCapability)
                result = MeasurementReferencePolicy.combine(result,
                    ((MeasurementReferenceCapability) capability).assessReference(mode, red, black));
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

    Vector<PhysicalSlotMutationProvider> getMutationProviders() {
        Vector<PhysicalSlotMutationProvider> result =
            new Vector<PhysicalSlotMutationProvider>();
        result.addAll(mutationProviders.values());
        return result;
    }

    /**
     * Admission check for the bounded composed consumer.  Fixed physical
     * parts and resistor slot providers are supported.  Other mutable
     * workbench providers remain valid for their historical leaf routes but
     * are rejected before this runtime is assembled into the selected
     * two-block composition.  Each resistor slot needs its own typed view;
     * the underlying runtime registry is intentionally shared.
     */
    void validateSupportedCompositionProviders() {
        Vector<PhysicalPartInventory<?>> resistorInventories =
            new Vector<PhysicalPartInventory<?>>();
        for (PhysicalBoardRuntimeCapability capability : getCapabilities()) {
            if (capability instanceof ReplaceableResistorBoardCapability) {
                ReplaceableResistorBoardCapability resistor =
                    (ReplaceableResistorBoardCapability) capability;
                if (resistor.getSlot() == null || resistor.getSlot().getPhysicalSlot() == null ||
                        resistor.getSlot().getPhysicalSlot().getRuntime() != this ||
                        resistor.getInventory() == null || resistor.getInventory().getRuntime() != this)
                    throw new IllegalStateException("Resistor provider is not owned by physical runtime");
                if (containsIdentity(resistorInventories, resistor.getInventory()))
                    throw new IllegalStateException("Resistor providers share an ambiguous inventory view");
                resistorInventories.add(resistor.getInventory());
                continue;
            }
            if (capability instanceof WorkbenchPartsProvider)
                throw new IllegalStateException("Composition does not support mutable provider: " +
                    capability.getCapabilityId());
        }
        for (PhysicalSlotMutationProvider provider : mutationProviders.values()) {
            if (!(provider instanceof ResistorSlotController))
                throw new IllegalStateException("Composition does not support mutation provider: " +
                    provider.getComponentId());
            PhysicalBoardSlot slot = getSlot(provider.getComponentId());
            if (slot == null || slot.getRuntime() != this)
                throw new IllegalStateException("Mutation provider is not owned by physical slot: " +
                    provider.getComponentId());
        }
    }

    private static boolean containsIdentity(Vector<?> values, Object expected) {
        for (Object value : values)
            if (value == expected) return true;
        return false;
    }

    void validateCommittedState(GeneratedBoardInstance instance,
            BoardModificationController modifications, Vector<CircuitElm> activeElements) {
        GeneratedRuntimeInvariant.verify(instance, modifications, activeElements);
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
