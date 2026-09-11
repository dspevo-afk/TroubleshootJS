package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Bounded prepare/commit/abort scope for one mutable physical board slot.
 *
 * <p>This scope deliberately owns only the active generated graph, the
 * selected component's bindings and attachments, the selected slot, and
 * append-only inventory/canonical registrations made by the operation.  It
 * is not a simulator snapshot and it is not a general undo registry.</p>
 */
final class PhysicalMutationScope {
    enum FailureStage {
        AFTER_GRAPH_DISCONNECT,
        AFTER_GRAPH_CONNECT,
        AFTER_GRAPH_APPEND,
        AFTER_SLOT_CLEAR,
        AFTER_PRIMARY_BINDING,
        AFTER_AUXILIARY_BINDING,
        AFTER_ENDPOINT_RETARGET,
        AFTER_INVENTORY_ACQUIRE,
        AFTER_CANONICAL_REGISTER,
        AFTER_STRESS_REGISTER,
        AFTER_ATTACHMENT,
        AFTER_SLOT_MOUNT,
        AFTER_GRAPH_RESTORE,
        AFTER_COMMIT
    }

    interface FailureHook {
        void afterStage(FailureStage stage);
    }

    /** Developer-only hook for exercising a failed active-graph compensator. */
    interface AbortFailureHook {
        void afterActiveGraphClear();
    }

    /** One provider-owned compensation action; the scope admits only one. */
    interface ProviderCompensation {
        void compensate();
    }

    private static FailureHook failureHook;
    private static AbortFailureHook abortFailureHook;

    static void setFailureHookForDeveloperVerification(FailureHook hook) {
        failureHook = hook;
    }

    static void clearFailureHookForDeveloperVerification() {
        failureHook = null;
    }

    static void setAbortFailureHookForDeveloperVerification(AbortFailureHook hook) {
        abortFailureHook = hook;
    }

    static void clearAbortFailureHookForDeveloperVerification() {
        abortFailureHook = null;
    }

    static void checkpoint(FailureStage stage) {
        FailureHook hook = failureHook;
        if (hook != null)
            hook.afterStage(stage);
    }

    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PhysicalMutationSlot slot;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalBoardRuntime runtime;
    private final PhysicalMutationIntent intent;

    /** Exact active graph object captured at preparation; identity is part of ownership. */
    private final Vector<CircuitElm> activeGraphBefore;
    private final Vector<CircuitElm> activeElementsBefore;
    private final HashMap<String, Boolean> connectedBefore;
    private final Vector<CircuitElm> primaryBindingBefore;
    private final Vector<CircuitElm> auxiliaryBindingBefore;
    private final Vector<CircuitMeasurementEndpoint> componentEndpointsBefore =
        new Vector<CircuitMeasurementEndpoint>();
    private final PhysicalMutationSlot.AttachmentState attachmentBefore;
    private final PhysicalPart<?> installedPartBefore;
    private final Vector<CircuitElm> appendedCanonicalElements = new Vector<CircuitElm>();

    private PhysicalPartInventory<?> acquiredInventory;
    private String acquiredInventoryId;
    private String acquiredIdNamespace;
    private String acquiredPartId;
    private PhysicalPart<?> acquiredPart;
    private Integer acquiredPreviousSerial;
    private boolean acquiredInventoryExisted;
    private ProviderCompensation providerCompensation;
    private boolean committed;
    private boolean closed;
    private boolean validationPassed;
    private boolean staleOwner;
    private boolean acquisitionAttempted;
    private boolean acquisitionAmbiguous;

    PhysicalMutationScope(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PhysicalMutationIntent intent) {
        if (sim == null || instance == null || modifications == null || intent == null)
            throw new IllegalArgumentException("Invalid physical mutation scope");
        if (sim.getGeneratedBoardInstance() != instance ||
                instance.getPhysicalBoardRuntime() != intent.getRuntime() ||
                intent.getModifications() != modifications)
            throw new IllegalStateException("Physical mutation scope has a foreign runtime owner");
        sim.solverExecutor.requirePublicAccess();
        if (sim.getBoardModificationController() != modifications || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException("Physical mutation requires its settled isolated owner");
        intent.validateCurrentOwner();
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.intent = intent;
        this.slot = intent.getSlot();
        this.physicalSlot = intent.getPhysicalSlot();
        this.runtime = intent.getRuntime();
        this.activeElementsBefore = new Vector<CircuitElm>(sim.elmList);
        this.activeGraphBefore = sim.elmList;
        this.connectedBefore = modifications.captureConnectionStates(intent.getComponentId());
        this.primaryBindingBefore = instance.getComponentBindings().getElements(
            intent.getComponentId());
        this.auxiliaryBindingBefore = instance.getComponentBindings().getAuxiliaryElements(
            intent.getComponentId());
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(intent.getComponentId()))
            componentEndpointsBefore.add(binding.getComponentEndpoint());
        this.attachmentBefore = slot.captureAttachmentState();
        this.installedPartBefore = intent.getInstalledPart();
        // Preparation has validated the exact runtime/slot/part ownership.
        // Receipts report that validation independently of whether a later
        // write-stage failure requires compensation.
        this.validationPassed = true;
        runtime.beginMutation(this);
    }

    /** Convenience constructor for callers that have not built an intent yet. */
    PhysicalMutationScope(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PhysicalMutationSlot slot,
            String operation) {
        this(sim, instance, modifications, PhysicalMutationIntent.prepare(
            instance == null ? null : instance.getPhysicalBoardRuntime(), instance,
            modifications, slot, operation));
    }

    boolean owns(PhysicalMutationSlot candidate) {
        return !closed && candidate == slot && runtime.ownsMutation(this) &&
            sim.getGeneratedBoardInstance() == instance && sim.elmList == activeGraphBefore;
    }

    boolean owns(BoardModificationController candidate) {
        return !closed && candidate == modifications && runtime.ownsMutation(this) &&
            sim.getGeneratedBoardInstance() == instance && sim.elmList == activeGraphBefore;
    }

    boolean ownsPart(PhysicalPart<?> candidate) {
        if (candidate == null || candidate.getId() == null ||
                runtime.getPart(candidate.getId()) != candidate ||
                runtime.getInventoryIdForPart(candidate.getId()) == null)
            return false;
        if (intent.getRequestedPart() != null)
            return candidate == intent.getRequestedPart();
        return acquiredPart == candidate;
    }

    boolean isClosed() { return closed; }
    PhysicalMutationIntent getIntent() { return intent; }

    void afterGraphWrite(boolean connected) {
        requireOpen();
        checkpoint(connected ? FailureStage.AFTER_GRAPH_CONNECT :
            FailureStage.AFTER_GRAPH_DISCONNECT);
    }

    void afterGraphAppend() {
        requireOpen();
        checkpoint(FailureStage.AFTER_GRAPH_APPEND);
    }

    void afterAttachmentWrite() {
        requireOpen();
        checkpoint(FailureStage.AFTER_ATTACHMENT);
    }

    void afterSlotClearWrite() {
        requireOpen();
        checkpoint(FailureStage.AFTER_SLOT_CLEAR);
    }

    void afterSlotMountWrite() {
        requireOpen();
        checkpoint(FailureStage.AFTER_SLOT_MOUNT);
    }

    /** Provider-owned stress registration checkpoint (resistor only). */
    void afterStressRegister() {
        requireOpen();
        checkpoint(FailureStage.AFTER_STRESS_REGISTER);
    }

    void replacePrimaryBinding(CircuitElm element) {
        requireOpen();
        requireOwnedPartElement(element);
        instance.getComponentBindings().replaceSingleElement(intent.getComponentId(), element);
        checkpoint(FailureStage.AFTER_PRIMARY_BINDING);
    }

    void replaceAuxiliaryBinding(CircuitElm element) {
        requireOpen();
        requireOwnedPartElement(element);
        instance.getComponentBindings().replaceAuxiliaryComponentElement(
            intent.getComponentId(), element);
        checkpoint(FailureStage.AFTER_AUXILIARY_BINDING);
    }

    void retargetEndpoint(GeneratedComponentConnectionBinding binding,
            CircuitMeasurementEndpoint endpoint) {
        requireOpen();
        if (binding == null || !intent.getComponentId().equals(binding.getComponentId()) ||
                instance.getConnectionBindings().get(binding.getComponentId(), binding.getPadId()) != binding ||
                endpoint == null || !endpointBelongsToMutationPart(endpoint))
            throw new IllegalArgumentException("Physical mutation endpoint belongs to another owner");
        binding.setComponentEndpoint(endpoint);
        checkpoint(FailureStage.AFTER_ENDPOINT_RETARGET);
    }

    void installPart(PhysicalPart<?> part) {
        requireOpen();
        if (!ownsPart(part))
            throw new IllegalArgumentException("Physical mutation install part is foreign");
        validateCandidateGeometry(part);
        slot.installForMutation(part, this);
    }

    PhysicalPart<?> clearPart() {
        requireOpen();
        if (slot.getInstalledPart() != installedPartBefore)
            throw new IllegalStateException("Physical mutation slot owner changed before clear");
        return slot.clearForMutation(this);
    }

    void registerCanonicalElement(CircuitElm element) {
        requireOpen();
        if (element == null || instance.ownsRuntimeSimulationElement(element) ||
                !isElementOwnedByMutationPart(element))
            throw new IllegalArgumentException("Invalid duplicate runtime generated element");
        instance.registerRuntimeSimulationElement(element);
        appendedCanonicalElements.add(element);
        checkpoint(FailureStage.AFTER_CANONICAL_REGISTER);
    }

    void appendActiveElement(CircuitElm element) {
        requireOpen();
        if (element == null || !instance.ownsRuntimeSimulationElement(element) ||
                sim.elmList.contains(element) || !isElementOwnedByMutationPart(element))
            throw new IllegalArgumentException("Invalid active physical mutation element");
        sim.elmList.add(element);
        afterGraphAppend();
    }

    <P extends PhysicalPart<?>> P acquire(PhysicalPartInventory<P> inventory,
            String idNamespace, PhysicalPartIdentityFactory<P> factory) {
        requireOpen();
        PhysicalBoardInstallationProvider.Scoped declaration = runtime.getScopedMutationCapability(intent.getComponentId());
        if (acquisitionAttempted)
            throw new IllegalStateException("A physical mutation admits only one acquisition");
        if (inventory == null || inventory.getRuntime() != runtime || declaration == null ||
                declaration.getMutationInventory() != inventory ||
                idNamespace == null || idNamespace.length() == 0 || factory == null)
            throw new IllegalArgumentException("Invalid physical mutation acquisition context");
        acquisitionAttempted = true;
        acquiredInventory = inventory;
        acquiredInventoryId = inventory.getInventoryId();
        acquiredIdNamespace = idNamespace;
        acquiredPreviousSerial = runtime.getNextPartSerial(idNamespace);
        acquiredInventoryExisted = runtime.hasInventory(acquiredInventoryId);
        Vector<String> inventoryIdsBefore = runtime.getInventoryPartIds(acquiredInventoryId);
        P part;
        try {
            part = inventory.acquire(idNamespace, factory);
        } catch (Throwable acquisitionFailure) {
            // A typed inventory adapter can reject after the runtime has
            // appended a candidate.  Recover that exact delta so abort can
            // still remove it; factory failures before registration produce
            // no delta and remain untouched.
            capturePartAppendedDuringFailedAcquisition(inventoryIdsBefore);
            PhysicalMutationScope.rethrow(acquisitionFailure);
            return null;
        }
        // Record the returned candidate before any compatibility/geometry
        // checks.  A rejected candidate was already appended by the runtime
        // inventory and must therefore be rolled back by abort as well.
        acquiredPart = part;
        if (part != null) {
            acquiredPartId = part.getId();
        }
        if (part == null || !slot.acceptsPart(part) || runtime.getPart(part.getId()) != part ||
                !acquiredInventoryId.equals(runtime.getInventoryIdForPart(part.getId())))
            throw new IllegalStateException("Physical mutation acquisition lost inventory ownership");
        validateCandidateGeometry(part);
        checkpoint(FailureStage.AFTER_INVENTORY_ACQUIRE);
        return part;
    }

    private void capturePartAppendedDuringFailedAcquisition(Vector<String> before) {
        Vector<String> after = runtime.getInventoryPartIds(acquiredInventoryId);
        String candidateId = null;
        for (String id : after) {
            if (before.contains(id))
                continue;
            if (candidateId != null) {
                // More than one delta cannot belong to this bounded
                // acquisition; leave the scope without guessing an owner.
                acquiredPart = null;
                acquiredPartId = null;
                acquisitionAmbiguous = true;
                return;
            }
            candidateId = id;
        }
        if (candidateId == null)
            return;
        PhysicalPart<?> candidate = runtime.getPart(candidateId);
        if (candidate != null) {
            acquiredPart = candidate;
            acquiredPartId = candidateId;
        }
    }

    /** Stores a single provider-owned bounded compensator, such as resistor stress. */
    void setProviderCompensation(ProviderCompensation compensation) {
        requireOpen();
        if (compensation == null || providerCompensation != null)
            throw new IllegalStateException("Physical mutation provider compensation is not bounded");
        providerCompensation = compensation;
    }

    void restoreComponentGraph() {
        requireOpen();
        boolean changed = modifications.restoreComponentForMutation(this,
            intent.getComponentId());
        if (changed)
            afterGraphRestoreWrite();
    }

    /** Failure-injection seam for callers that own the restore loop. */
    void afterGraphRestoreWrite() {
        requireOpen();
        checkpoint(FailureStage.AFTER_GRAPH_RESTORE);
    }

    void commit() {
        requireOpen();
        intent.validateCommitOwner(slot.getInstalledPart(), acquiredPart);
        try {
            runtime.validateCommittedState(instance, modifications, sim.elmList);
        } catch (Throwable validationFailure) {
            validationPassed = false;
            PhysicalMutationScope.rethrow(validationFailure);
        }
        checkpoint(FailureStage.AFTER_COMMIT);
        committed = true;
    }

    void closeAfterCommit() {
        if (!committed)
            throw new IllegalStateException("Physical mutation closed before commit: " +
                intent.getOperation());
        closeInternal();
        runtime.recordMutationReceipt(PhysicalMutationReceipt.committed(intent));
    }

    /**
     * Compensates every independent owned write.  The original throwable is
     * retained as the primary failure; cleanup failures isolate this exact
     * runtime and are never allowed to overwrite a successor simulation owner.
     */
    void abort(Throwable original) {
        if (closed)
            return;
        Throwable failure = original == null ?
            new IllegalStateException("Physical mutation aborted without an error") : original;
        FailureHook savedHook = failureHook;
        failureHook = null;
        final boolean[] cleanupFailed = { acquisitionAmbiguous };
        if (acquisitionAmbiguous)
            failure.addSuppressed(new IllegalStateException("Acquisition changed more than one physical owner"));
        try {
            restorePart(failure, cleanupFailed);
            restoreProviderState(failure, cleanupFailed);
            restoreBindings(failure, cleanupFailed);
            restoreCanonicalElements(failure, cleanupFailed);
            restoreInventory(failure, cleanupFailed);
            restoreActiveGraph(failure, cleanupFailed);
            restoreConnections(failure, cleanupFailed);
            restoreAttachments(failure, cleanupFailed);
            if (!staleOwner) {
                try {
                    GeneratedRuntimeInvariant.verify(instance, modifications, sim.elmList);
                } catch (Throwable validationFailure) {
                    failure.addSuppressed(validationFailure);
                    cleanupFailed[0] = true;
                }
            }
        } finally {
            failureHook = savedHook;
            try {
                closeInternal();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
                cleanupFailed[0] = true;
                closed = true;
            }
        }
        if (cleanupFailed[0] || staleOwner) {
            runtime.quarantineMutationOwner(intent.getComponentId(), failure);
            if (sim.getGeneratedBoardInstance() == instance)
                markRuntimeFailure(failure);
            runtime.recordMutationReceipt(PhysicalMutationReceipt.isolated(intent, failure,
                validationPassed));
        } else {
            runtime.recordMutationReceipt(PhysicalMutationReceipt.compensated(intent, failure,
                validationPassed));
        }
    }

    private void restorePart(Throwable failure, boolean[] cleanupFailed) {
        try {
            PhysicalPart<?> current = physicalSlot.getInstalledPart();
            PhysicalPart<?> requested = intent.getRequestedPart();
            if (current != null && current != installedPartBefore &&
                    current != acquiredPart && current != requested)
                throw new IllegalStateException(
                    "Physical mutation slot was occupied by a foreign part during abort");
            PhysicalPart<?> transactionPart = acquiredPart != null ? acquiredPart :
                intent.getRequestedPart();
            physicalSlot.restoreInstalledPartForMutation(installedPartBefore, transactionPart);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreProviderState(Throwable failure, boolean[] cleanupFailed) {
        if (providerCompensation == null)
            return;
        try {
            providerCompensation.compensate();
            providerCompensation = null;
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreBindings(Throwable failure, boolean[] cleanupFailed) {
        try {
            instance.getComponentBindings().restoreForMutation(intent.getComponentId(),
                primaryBindingBefore, auxiliaryBindingBefore);
            Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings()
                .getForComponent(intent.getComponentId());
            if (bindings.size() != componentEndpointsBefore.size())
                throw new IllegalStateException("Physical connection binding count changed during abort");
            for (int index = 0; index < bindings.size(); index++)
                bindings.get(index).setComponentEndpoint(componentEndpointsBefore.get(index));
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreCanonicalElements(Throwable failure, boolean[] cleanupFailed) {
        for (int index = appendedCanonicalElements.size() - 1; index >= 0; index--) {
            CircuitElm element = appendedCanonicalElements.get(index);
            try {
                instance.unregisterRuntimeSimulationElement(element);
            } catch (Throwable restoreFailure) {
                failure.addSuppressed(restoreFailure);
                cleanupFailed[0] = true;
                break;
            }
        }
    }

    private void restoreInventory(Throwable failure, boolean[] cleanupFailed) {
        if (acquiredPart == null)
            return;
        try {
            runtime.rollbackAcquiredInventoryPart(acquiredInventoryId, acquiredIdNamespace,
                acquiredPartId, acquiredPart, acquiredPreviousSerial, acquiredInventoryExisted);
            acquiredPart = null;
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreActiveGraph(Throwable failure, boolean[] cleanupFailed) {
        if (sim.getGeneratedBoardInstance() != instance || sim.elmList != activeGraphBefore) {
            // A stale scope must not clear or repopulate a successor board or
            // a replacement graph, even when the board object is unchanged.
            staleOwner = true;
            return;
        }
        try {
            sim.elmList.clear();
            AbortFailureHook hook = abortFailureHook;
            if (hook != null)
                hook.afterActiveGraphClear();
            sim.elmList.addAll(activeElementsBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreConnections(Throwable failure, boolean[] cleanupFailed) {
        try {
            modifications.restoreConnectionStatesForMutation(connectedBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void restoreAttachments(Throwable failure, boolean[] cleanupFailed) {
        try {
            slot.restoreAttachmentState(attachmentBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
            cleanupFailed[0] = true;
        }
    }

    private void markRuntimeFailure(Throwable failure) {
        try {
            sim.markGeneratedRuntimeFailure(instance, failure);
        } catch (Throwable markerFailure) {
            failure.addSuppressed(markerFailure);
        }
    }

    private void requireOwnedPartElement(CircuitElm element) {
        PhysicalPart<?> part = mutationPart();
        if (element == null || part == null || !containsIdentity(
                part.getElectricalBacking().getCircuitElements(), element))
            throw new IllegalArgumentException("Physical mutation binding element is foreign");
        if (!instance.ownsRuntimeSimulationElement(element))
            throw new IllegalArgumentException("Physical mutation binding is outside canonical runtime");
    }

    private boolean isElementOwnedByMutationPart(CircuitElm element) {
        PhysicalPart<?> part = mutationPart();
        return part != null && part.getElectricalBacking() != null &&
            containsIdentity(part.getElectricalBacking().getCircuitElements(), element);
    }

    private boolean endpointBelongsToMutationPart(CircuitMeasurementEndpoint endpoint) {
        PhysicalPart<?> part = mutationPart();
        if (part == null || part.getElectricalBacking() == null)
            return false;
        for (int index = 0; index < part.getElectricalBacking().getTerminalCount(); index++)
            if (GeneratedComponentConnectionBindings.sameEndpoint(
                    part.getElectricalBacking().getTerminalEndpoint(index), endpoint))
                return true;
        return false;
    }

    private PhysicalPart<?> mutationPart() {
        if (intent.getRequestedPart() != null)
            return intent.getRequestedPart();
        if (acquiredPart != null)
            return acquiredPart;
        return installedPartBefore;
    }

    private void validateCandidateGeometry(PhysicalPart<?> part) {
        PhysicalGeometryRealization expected = physicalSlot.getGeometryRealization();
        if (expected == null)
            return;
        PhysicalGeometryRealization actual = part == null ? null : part.getGeometryRealization();
        if (actual == null || !expected.isEquivalentTo(actual))
            throw new IllegalArgumentException(
                "Physical mutation candidate is not bound to the exact board geometry");
    }

    private void closeInternal() {
        if (closed)
            return;
        if (runtime.ownsMutation(this))
            runtime.endMutation(this);
        closed = true;
    }

    private void requireOpen() {
        if (closed || !runtime.ownsMutation(this) ||
                sim.getGeneratedBoardInstance() != instance || sim.elmList != activeGraphBefore)
            throw new IllegalStateException("Physical mutation scope is closed or unowned");
    }

    private static boolean containsIdentity(Vector<CircuitElm> values, CircuitElm expected) {
        for (CircuitElm value : values)
            if (value == expected) return true;
        return false;
    }

    static void rethrow(Throwable failure) {
        if (failure instanceof Error)
            throw (Error) failure;
        if (failure instanceof RuntimeException)
            throw (RuntimeException) failure;
        throw new IllegalStateException("Physical mutation failed", failure);
    }
}
