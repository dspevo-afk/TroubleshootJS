package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Bounded prepare/commit/abort scope for one replaceable resistor slot.
 *
 * <p>The scope intentionally owns only the board graph, the selected
 * component's bindings and attachments, and append-only catalog state.  It
 * does not snapshot the simulator, challenge, power controller, or any other
 * physical capability.  Those owners must remain untouched while this scope
 * is active.</p>
 */
final class ResistorMutationScope {
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
    private final ReplaceableComponentSlot slot;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalBoardRuntime runtime;
    private final String componentId;
    private final String operation;

    private final Vector<CircuitElm> activeElementsBefore;
    private final HashMap<String, Boolean> connectedBefore;
    private final Vector<CircuitElm> primaryBindingBefore;
    private final Vector<CircuitElm> auxiliaryBindingBefore;
    private final Vector<CircuitMeasurementEndpoint> componentEndpointsBefore =
        new Vector<CircuitMeasurementEndpoint>();
    private final ReplaceableComponentSlot.AttachmentState attachmentBefore;
    private final PhysicalPart<?> installedPartBefore;
    private final Vector<CircuitElm> appendedCanonicalElements = new Vector<CircuitElm>();

    private PhysicalPartInventory<?> acquiredInventory;
    private String acquiredInventoryId;
    private String acquiredIdNamespace;
    private String acquiredPartId;
    private PhysicalPart<?> acquiredPart;
    private Integer acquiredPreviousSerial;
    private boolean acquiredInventoryExisted;
    private ResistorStressDamageSystem registeredStress;
    private PhysicalResistorPart registeredStressPart;

    private boolean committed;
    private boolean closed;

    ResistorMutationScope(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, ReplaceableComponentSlot slot,
            String operation) {
        if (sim == null || instance == null || modifications == null || slot == null)
            throw new IllegalArgumentException("Invalid resistor mutation scope");
        if (instance.getPhysicalBoardRuntime() == null ||
                instance.getPhysicalBoardRuntime().getBoard() == null)
            throw new IllegalArgumentException("Resistor mutation has no physical runtime");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.slot = slot;
        this.physicalSlot = slot.getPhysicalSlot();
        this.runtime = instance.getPhysicalBoardRuntime();
        this.componentId = slot.getComponentId();
        this.operation = operation == null ? "resistor" : operation;
        this.activeElementsBefore = new Vector<CircuitElm>(sim.elmList);
        this.connectedBefore = modifications.captureConnectionStates(componentId);
        this.primaryBindingBefore = instance.getComponentBindings().getElements(componentId);
        this.auxiliaryBindingBefore = instance.getComponentBindings().getAuxiliaryElements(componentId);
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId))
            componentEndpointsBefore.add(binding.getComponentEndpoint());
        this.attachmentBefore = slot.captureAttachmentState();
        this.installedPartBefore = physicalSlot.getInstalledPart();
        runtime.beginMutation();
    }

    boolean owns(ReplaceableComponentSlot candidate) {
        return !closed && candidate == slot;
    }

    boolean owns(BoardModificationController candidate) {
        return !closed && candidate == modifications;
    }

    boolean isClosed() { return closed; }

    void afterGraphWrite(boolean connected) {
        checkpoint(connected ? FailureStage.AFTER_GRAPH_CONNECT : FailureStage.AFTER_GRAPH_DISCONNECT);
    }

    void afterGraphAppend() { checkpoint(FailureStage.AFTER_GRAPH_APPEND); }

    void afterAttachmentWrite() { checkpoint(FailureStage.AFTER_ATTACHMENT); }

    void afterSlotClearWrite() { checkpoint(FailureStage.AFTER_SLOT_CLEAR); }

    void afterSlotMountWrite() { checkpoint(FailureStage.AFTER_SLOT_MOUNT); }

    void replacePrimaryBinding(CircuitElm element) {
        requireOpen();
        instance.getComponentBindings().replaceSingleElement(componentId, element);
        checkpoint(FailureStage.AFTER_PRIMARY_BINDING);
    }

    void replaceAuxiliaryBinding(CircuitElm element) {
        requireOpen();
        instance.getComponentBindings().replaceAuxiliaryComponentElement(componentId, element);
        checkpoint(FailureStage.AFTER_AUXILIARY_BINDING);
    }

    void retargetEndpoint(GeneratedComponentConnectionBinding binding,
            CircuitMeasurementEndpoint endpoint) {
        requireOpen();
        if (binding == null || !componentId.equals(binding.getComponentId()))
            throw new IllegalArgumentException("Resistor mutation endpoint belongs to another component");
        binding.setComponentEndpoint(endpoint);
        checkpoint(FailureStage.AFTER_ENDPOINT_RETARGET);
    }

    void installPart(PhysicalResistorPart part) {
        requireOpen();
        slot.installForMutation(part, this);
    }

    PhysicalPart<?> clearPart() {
        requireOpen();
        return slot.clearForMutation(this);
    }

    void registerCanonicalElement(CircuitElm element) {
        requireOpen();
        instance.registerRuntimeSimulationElement(element);
        appendedCanonicalElements.add(element);
        checkpoint(FailureStage.AFTER_CANONICAL_REGISTER);
    }

    void appendActiveElement(CircuitElm element) {
        requireOpen();
        if (element == null)
            throw new IllegalArgumentException("Missing active resistor element");
        sim.elmList.add(element);
        afterGraphAppend();
    }

    <P extends PhysicalPart<?>> P acquire(PhysicalPartInventory<P> inventory,
            String idNamespace, PhysicalPartIdentityFactory<P> factory) {
        requireOpen();
        if (inventory == null)
            throw new IllegalArgumentException("Missing resistor inventory");
        acquiredInventory = inventory;
        acquiredInventoryId = inventory.getInventoryId();
        acquiredIdNamespace = idNamespace;
        acquiredPreviousSerial = runtime.getNextPartSerial(idNamespace);
        acquiredInventoryExisted = runtime.hasInventory(acquiredInventoryId);
        P part = inventory.acquire(idNamespace, factory);
        acquiredPart = part;
        acquiredPartId = part.getId();
        checkpoint(FailureStage.AFTER_INVENTORY_ACQUIRE);
        return part;
    }

    void registerStress(ResistorStressDamageSystem stress, PhysicalResistorPart part) {
        requireOpen();
        if (stress == null || part == null)
            throw new IllegalArgumentException("Missing resistor stress registration");
        stress.register(part);
        registeredStress = stress;
        registeredStressPart = part;
        checkpoint(FailureStage.AFTER_STRESS_REGISTER);
    }

    void restoreComponentGraph() {
        requireOpen();
        boolean changed = modifications.restoreComponentForMutation(this, componentId);
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
        instance.getPhysicalBoardRuntime().validateCommittedState(instance, modifications,
            sim.elmList);
        committed = true;
        checkpoint(FailureStage.AFTER_COMMIT);
    }

    void closeAfterCommit() {
        if (!committed)
            throw new IllegalStateException("Resistor mutation closed before commit: " + operation);
        closeInternal();
    }

    /**
     * Restores the captured state and always attempts every independent
     * compensating action.  The original throwable remains the primary error;
     * cleanup and validation errors are attached as suppressed failures.
     */
    void abort(Throwable original) {
        if (closed)
            return;
        Throwable failure = original == null ?
            new IllegalStateException("Resistor mutation aborted without an error") : original;
        FailureHook savedHook = failureHook;
        failureHook = null;
        try {
            restorePart(failure);
            restoreBindings(failure);
            restoreStress(failure);
            restoreCanonicalElements(failure);
            restoreInventory(failure);
            restoreActiveGraph(failure);
            restoreConnections(failure);
            restoreAttachments(failure);
            try {
                GeneratedRuntimeInvariant.verify(instance, modifications, sim.elmList);
            } catch (Throwable validationFailure) {
                failure.addSuppressed(validationFailure);
            }
        } finally {
            failureHook = savedHook;
            closeInternal();
        }
        if (hasAbortFailure(failure, original))
            markRuntimeFailure(failure);
    }

    private boolean hasAbortFailure(Throwable failure, Throwable original) {
        if (original == null)
            return true;
        return failure.getSuppressed() != null && failure.getSuppressed().length > 0;
    }

    private void restorePart(Throwable failure) {
        try {
            physicalSlot.restoreInstalledPartForMutation(installedPartBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreBindings(Throwable failure) {
        try {
            instance.getComponentBindings().restoreForMutation(componentId,
                primaryBindingBefore, auxiliaryBindingBefore);
            Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings()
                .getForComponent(componentId);
            if (bindings.size() != componentEndpointsBefore.size())
                throw new IllegalStateException("Resistor connection binding count changed during abort");
            for (int index = 0; index < bindings.size(); index++)
                bindings.get(index).setComponentEndpoint(componentEndpointsBefore.get(index));
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreCanonicalElements(Throwable failure) {
        for (int index = appendedCanonicalElements.size() - 1; index >= 0; index--) {
            CircuitElm element = appendedCanonicalElements.get(index);
            try {
                instance.unregisterRuntimeSimulationElement(element);
            } catch (Throwable restoreFailure) {
                failure.addSuppressed(restoreFailure);
                break;
            }
        }
    }

    private void restoreStress(Throwable failure) {
        if (registeredStressPart == null)
            return;
        try {
            registeredStress.unregister(registeredStressPart);
            registeredStressPart = null;
            registeredStress = null;
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreInventory(Throwable failure) {
        if (acquiredPart == null)
            return;
        try {
            runtime.rollbackAcquiredInventoryPart(acquiredInventoryId, acquiredIdNamespace,
                acquiredPartId, acquiredPart, acquiredPreviousSerial, acquiredInventoryExisted);
            acquiredPart = null;
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreActiveGraph(Throwable failure) {
        try {
            sim.elmList.clear();
            AbortFailureHook hook = abortFailureHook;
            if (hook != null)
                hook.afterActiveGraphClear();
            sim.elmList.addAll(activeElementsBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreConnections(Throwable failure) {
        try {
            modifications.restoreConnectionStatesForMutation(connectedBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void restoreAttachments(Throwable failure) {
        try {
            slot.restoreAttachmentState(attachmentBefore);
        } catch (Throwable restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private void markRuntimeFailure(Throwable failure) {
        try {
            sim.markGeneratedRuntimeFailure(instance, failure);
        } catch (Throwable markerFailure) {
            failure.addSuppressed(markerFailure);
        }
    }

    private void closeInternal() {
        if (closed)
            return;
        closed = true;
        if (runtime.isMutationInProgress())
            runtime.endMutation();
    }

    private void requireOpen() {
        if (closed)
            throw new IllegalStateException("Resistor mutation scope is closed");
    }

    static void rethrow(Throwable failure) {
        if (failure instanceof Error)
            throw (Error) failure;
        if (failure instanceof RuntimeException)
            throw (RuntimeException) failure;
        throw new IllegalStateException("Resistor mutation failed", failure);
    }
}
