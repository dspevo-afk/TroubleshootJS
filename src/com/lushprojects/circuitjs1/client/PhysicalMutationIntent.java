package com.lushprojects.circuitjs1.client;

/**
 * Immutable preparation record for one physical mutation.
 *
 * <p>Preparation captures the exact runtime, slot and currently installed
 * owner before the transaction obtains its permit.  A later commit therefore
 * cannot accidentally apply a stale operation to a successor slot or to a
 * same-ID foreign part.</p>
 */
final class PhysicalMutationIntent {
    private final PhysicalBoardRuntime runtime;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PhysicalMutationSlot slot;
    private final PhysicalBoardSlot physicalSlot;
    private final String componentId;
    private final String operation;
    private final String padId;
    private final String catalogEntryId;
    private final PhysicalPart<?> installedPart;
    private final PhysicalPart<?> requestedPart;

    private PhysicalMutationIntent(PhysicalBoardRuntime runtime,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            PhysicalMutationSlot slot, String operation, String padId,
            String catalogEntryId, PhysicalPart<?> requestedPart) {
        this.runtime = runtime;
        this.instance = instance;
        this.modifications = modifications;
        this.slot = slot;
        this.physicalSlot = slot.getPhysicalSlot();
        this.componentId = slot.getComponentId();
        this.operation = operation;
        this.padId = padId;
        this.catalogEntryId = catalogEntryId;
        this.installedPart = slot.getInstalledPart();
        this.requestedPart = requestedPart;
    }

    static PhysicalMutationIntent prepare(PhysicalBoardRuntime runtime,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            PhysicalMutationSlot slot, String operation) {
        return prepare(runtime, instance, modifications, slot, operation, null, null, null);
    }

    static PhysicalMutationIntent prepare(PhysicalBoardRuntime runtime,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            PhysicalMutationSlot slot, String operation, PhysicalPart<?> requestedPart) {
        return prepare(runtime, instance, modifications, slot, operation, null, null,
            requestedPart);
    }

    static PhysicalMutationIntent prepare(PhysicalBoardRuntime runtime,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            PhysicalMutationSlot slot, String operation, String padId,
            String catalogEntryId, PhysicalPart<?> requestedPart) {
        if (runtime == null || instance == null || modifications == null || slot == null ||
                operation == null || operation.length() == 0)
            throw new IllegalArgumentException("Invalid physical mutation intent");
        if (instance.getPhysicalBoardRuntime() != runtime ||
                modifications.getInstanceForRuntimeValidation() != instance)
            throw new IllegalStateException("Physical mutation owners do not agree");
        PhysicalBoardSlot physicalSlot = slot.getPhysicalSlot();
        if (physicalSlot == null || physicalSlot.getRuntime() != runtime ||
                runtime.getSlot(slot.getComponentId()) != physicalSlot ||
                !slot.getComponentId().equals(physicalSlot.getComponentId()))
            throw new IllegalStateException("Physical mutation slot is foreign to runtime");
        requireDeclaredProvider(runtime, slot);
        if (padId != null) {
            BoardPad pad = instance.getBoard().getPad(padId);
            if (pad == null || !slot.getComponentId().equals(pad.getComponentId()))
                throw new IllegalArgumentException("Physical mutation pad is foreign to slot");
        }
        if (runtime.isMutationInProgress())
            throw new BoardModificationRejectedException(
                "A physical board mutation is already in progress");
        PhysicalPart<?> installed = slot.getInstalledPart();
        if (installed != null &&
                (!slot.acceptsPart(installed) || installed.getBoardSlot() != physicalSlot ||
                 !installed.isInstalled()))
            throw new IllegalStateException("Physical slot owner mount state is inconsistent");
        if (requestedPart != null) {
            if (!slot.acceptsPart(requestedPart))
                throw new IllegalArgumentException("Physical mutation candidate type does not fit slot");
            PhysicalSlotMutationProvider provider = runtime.getMutationProvider(
                slot.getComponentId());
            if (requestedPart.getId() == null || runtime.getPart(requestedPart.getId()) != requestedPart ||
                    (provider != null && !provider.ownsPart(requestedPart.getId())) ||
                    runtime.getInventoryIdForPart(requestedPart.getId()) == null)
                throw new IllegalArgumentException("Physical mutation candidate is foreign to inventory");
            if (requestedPart.isInstalled())
                throw new BoardModificationRejectedException(
                    "A mounted physical part cannot be installed as a replacement");
            validateGeometry(physicalSlot, requestedPart);
        }
        return new PhysicalMutationIntent(runtime, instance, modifications, slot, operation,
            padId, catalogEntryId, requestedPart);
    }

    private static void requireDeclaredProvider(PhysicalBoardRuntime runtime, PhysicalMutationSlot slot) {
        PhysicalBoardInstallationProvider.Scoped declaration = runtime.getScopedMutationCapability(slot.getComponentId());
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider(slot.getComponentId());
        if (declaration == null || declaration.getMutationSlot() != slot ||
                !(provider instanceof PhysicalSlotMutationProvider.Scoped) ||
                ((PhysicalSlotMutationProvider.Scoped) provider).getMutationSlot() != slot)
            throw new IllegalStateException("Physical mutation requires its exact declared installed provider");
    }

    private static void validateGeometry(PhysicalBoardSlot physicalSlot,
            PhysicalPart<?> part) {
        PhysicalGeometryRealization expected = physicalSlot.getGeometryRealization();
        if (expected == null)
            return;
        PhysicalGeometryRealization actual = part.getGeometryRealization();
        if (actual == null || !expected.isEquivalentTo(actual))
            throw new IllegalArgumentException(
                "Replacement geometry must be bound to the exact board realization before mutation");
    }

    void validateCurrentOwner() {
        requireDeclaredProvider(runtime, slot);
        if (runtime.getSlot(componentId) != physicalSlot ||
                slot.getPhysicalSlot() != physicalSlot || slot.getInstalledPart() != installedPart)
            throw new IllegalStateException("Physical mutation owner changed during preparation");
        if (installedPart != null &&
                (!slot.acceptsPart(installedPart) || installedPart.getBoardSlot() != physicalSlot ||
                 !installedPart.isInstalled()))
            throw new IllegalStateException("Captured physical owner is no longer mounted in its slot");
        if (requestedPart != null) {
            PhysicalSlotMutationProvider provider = runtime.getMutationProvider(componentId);
            if (!slot.acceptsPart(requestedPart) || runtime.getPart(requestedPart.getId()) != requestedPart ||
                    (provider != null && !provider.ownsPart(requestedPart.getId())) ||
                    runtime.getInventoryIdForPart(requestedPart.getId()) == null ||
                    requestedPart.isInstalled())
                throw new IllegalStateException("Physical mutation candidate changed ownership");
            validateGeometry(physicalSlot, requestedPart);
        }
    }

    /**
     * Validates the bounded state transition at commit.  A slot operation may
     * legitimately move from the captured owner to empty (removal), or to the
     * exact requested/acquired candidate (installation); a foreign transition
     * is never accepted.
     */
    void validateCommitOwner(PhysicalPart<?> currentInstalled,
            PhysicalPart<?> acquiredPart) {
        requireDeclaredProvider(runtime, slot);
        if (runtime.getSlot(componentId) != physicalSlot ||
                slot.getPhysicalSlot() != physicalSlot)
            throw new IllegalStateException("Physical mutation owner changed before commit");
        if (requestedPart != null) {
            if (currentInstalled != requestedPart || !requestedPart.isInstalled() ||
                    requestedPart.getBoardSlot() != physicalSlot)
                throw new IllegalStateException("Physical mutation candidate is not mounted in its slot");
            return;
        }
        if (acquiredPart != null) {
            if (currentInstalled != acquiredPart || !acquiredPart.isInstalled() ||
                    acquiredPart.getBoardSlot() != physicalSlot)
                throw new IllegalStateException("Acquired physical mutation candidate is not mounted");
            return;
        }
        if (currentInstalled != installedPart && currentInstalled != null)
            throw new IllegalStateException("Foreign physical part occupied mutation slot");
        if (currentInstalled != null &&
                (currentInstalled.getBoardSlot() != physicalSlot || !currentInstalled.isInstalled()))
            throw new IllegalStateException("Physical commit owner mount state is inconsistent");
    }

    PhysicalBoardRuntime getRuntime() { return runtime; }
    GeneratedBoardInstance getInstance() { return instance; }
    BoardModificationController getModifications() { return modifications; }
    PhysicalMutationSlot getSlot() { return slot; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    String getComponentId() { return componentId; }
    String getOperation() { return operation; }
    String getPadId() { return padId; }
    String getCatalogEntryId() { return catalogEntryId; }
    PhysicalPart<?> getInstalledPart() { return installedPart; }
    PhysicalPart<?> getRequestedPart() { return requestedPart; }
}
