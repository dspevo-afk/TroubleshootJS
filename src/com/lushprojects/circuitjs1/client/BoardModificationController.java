package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class BoardModificationController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final HashMap<String, Boolean> connected = new HashMap<String, Boolean>();

    BoardModificationController(CirSim sim, GeneratedBoardInstance instance) {
        this.sim = sim;
        this.instance = instance;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getAll())
            connected.put(binding.getPadId(), Boolean.TRUE);
    }

    boolean liftLead(String componentId, String padId) {
        return setLeadConnection(componentId, padId, false);
    }

    boolean reconnectLead(String componentId, String padId) {
        return setLeadConnection(componentId, padId, true);
    }

    GeneratedBoardInstance getInstanceForRuntimeValidation() { return instance; }

    boolean isOperationInProgress() {
        return instance.getPhysicalBoardRuntime().isMutationInProgress();
    }

    boolean removeComponent(String componentId) {
        return removeComponent(componentId, true);
    }

    boolean removeComponentDeferredRefresh(String componentId) {
        return removeComponent(componentId, false);
    }

    private boolean removeComponent(String componentId, boolean refreshControls) {
        requireSafeMutation();
        PhysicalMutationSlot mutationSlot = getScopedMutationSlot(componentId);
        if (mutationSlot != null)
            return removeScopedComponent(componentId, refreshControls, mutationSlot);
        boolean changed = false;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent(componentId)) {
            changed |= setConnection(binding, false);
        }
        finishMutation(changed, refreshControls);
        return changed;
    }

    boolean restoreComponent(String componentId) {
        requireSafeMutation();
        PhysicalMutationSlot mutationSlot = getScopedMutationSlot(componentId);
        if (mutationSlot != null)
            return restoreScopedComponent(componentId, mutationSlot);
        boolean changed = false;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent(componentId))
            changed |= setConnection(binding, true);
        finishMutation(changed);
        return changed;
    }

    boolean isLeadConnected(String componentId, String padId) {
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().get(componentId, padId);
        return connected.get(binding.getPadId()).booleanValue();
    }

    ComponentPhysicalState getComponentState(String componentId) {
        Vector<GeneratedComponentConnectionBinding> bindings =
            instance.getConnectionBindings().getForComponent(componentId);
        int connectedCount = 0;
        for (GeneratedComponentConnectionBinding binding : bindings) {
            if (connected.get(binding.getPadId()).booleanValue())
                connectedCount++;
        }
        if (connectedCount == bindings.size())
            return ComponentPhysicalState.INSTALLED;
        if (connectedCount == 0)
            return ComponentPhysicalState.REMOVED;
        return ComponentPhysicalState.LEAD_LIFTED;
    }

    boolean isComponentInstalled(String componentId) {
        return getComponentState(componentId) == ComponentPhysicalState.INSTALLED;
    }

    boolean isFullyRestored() {
        for (Boolean state : connected.values())
            if (!state.booleanValue())
                return false;
        return true;
    }

    HashMap<String, Boolean> captureConnectionStates(String componentId) {
        HashMap<String, Boolean> result = new HashMap<String, Boolean>();
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId)) {
            Boolean state = connected.get(binding.getPadId());
            if (state == null)
                throw new IllegalStateException("Missing connection state: " + binding.getPadId());
            result.put(binding.getPadId(), state);
        }
        return result;
    }

    HashMap<String, Boolean> getConnectionStatesForValidation() {
        return new HashMap<String, Boolean>(connected);
    }

    void verifyStructuralState() {
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getAll()) {
            int occurrences = countOccurrences(binding.getConnectionElement());
            int expected = connected.get(binding.getPadId()).booleanValue() ? 1 : 0;
            if (occurrences != expected)
                throw new IllegalStateException("Connection state disagrees with graph: " + binding.getPadId());
        }
        verifyCanonicalGeneratedElementOrder();
    }

    private boolean setLeadConnection(String componentId, String padId, boolean shouldConnect) {
        requireSafeMutation();
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings().get(componentId, padId);
        if (isConnectionState(binding) == shouldConnect)
            return false;
        PhysicalMutationSlot mutationSlot = getScopedMutationSlot(componentId);
        if (mutationSlot == null)
            return setLeadConnectionWithoutScope(binding, shouldConnect);

        PhysicalMutationScope scope = newScope(mutationSlot, "lead", null, padId, null);
        boolean changed = false;
        try {
            changed = setConnectionForMutation(scope, binding, shouldConnect);
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutationAfterCommittedScope(changed);
        return changed;
    }

    /** Runs a scoped provider graph removal without clearing its physical slot. */
    private boolean removeScopedComponent(String componentId, boolean refreshControls,
            PhysicalMutationSlot mutationSlot) {
        PhysicalMutationScope scope = newScope(mutationSlot, "graph-remove", null);
        boolean changed = false;
        try {
            changed = disconnectComponentForMutation(scope, componentId);
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutationAfterCommittedScope(changed, refreshControls);
        return changed;
    }

    /** Runs a scoped provider graph restoration. */
    private boolean restoreScopedComponent(String componentId,
            PhysicalMutationSlot mutationSlot) {
        PhysicalMutationScope scope = newScope(mutationSlot, "graph-restore", null);
        boolean changed = false;
        try {
            changed = restoreComponentForMutation(scope, componentId);
            if (changed)
                scope.afterGraphRestoreWrite();
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutationAfterCommittedScope(changed, true);
        return changed;
    }

    private boolean setLeadConnectionWithoutScope(
            GeneratedComponentConnectionBinding binding, boolean shouldConnect) {
        boolean changed = setConnection(binding, shouldConnect);
        finishMutation(changed);
        return changed;
    }

    private boolean setConnection(GeneratedComponentConnectionBinding binding, boolean shouldConnect) {
        boolean isConnected = connected.get(binding.getPadId()).booleanValue();
        if (isConnected == shouldConnect)
            return false;
        if (shouldConnect)
            insertInCanonicalOrder(binding.getConnectionElement());
        else
            removeAllOccurrences(binding.getConnectionElement());
        connected.put(binding.getPadId(), Boolean.valueOf(shouldConnect));
        return true;
    }

    boolean setConnectionForMutation(PhysicalMutationScope scope,
            GeneratedComponentConnectionBinding binding, boolean shouldConnect) {
        if (scope == null || !scope.owns(this) || binding == null ||
                !scope.getIntent().getComponentId().equals(binding.getComponentId()) ||
                instance.getConnectionBindings().get(binding.getComponentId(), binding.getPadId()) != binding)
            throw new IllegalStateException("Invalid physical mutation connection scope");
        boolean changed = setConnection(binding, shouldConnect);
        if (changed)
            scope.afterGraphWrite(shouldConnect);
        return changed;
    }

    boolean disconnectComponentForMutation(PhysicalMutationScope scope, String componentId) {
        if (scope == null || !scope.owns(this) ||
                !scope.getIntent().getComponentId().equals(componentId))
            throw new IllegalStateException("Invalid physical mutation connection scope");
        boolean changed = false;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId))
            changed |= setConnectionForMutation(scope, binding, false);
        return changed;
    }

    boolean restoreComponentForMutation(PhysicalMutationScope scope, String componentId) {
        if (scope == null || !scope.owns(this) ||
                !scope.getIntent().getComponentId().equals(componentId))
            throw new IllegalStateException("Invalid physical mutation connection scope");
        boolean changed = false;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId))
            changed |= setConnectionForMutation(scope, binding, true);
        return changed;
    }

    private boolean isConnectionState(GeneratedComponentConnectionBinding binding) {
        Boolean state = connected.get(binding.getPadId());
        if (state == null)
            throw new IllegalStateException("Missing connection state: " + binding.getPadId());
        return state.booleanValue();
    }

    void restoreConnectionStatesForMutation(HashMap<String, Boolean> states) {
        if (states == null)
            throw new IllegalArgumentException("Missing physical connection state");
        for (String padId : states.keySet()) {
            if (!connected.containsKey(padId) || states.get(padId) == null)
                throw new IllegalStateException("Unknown physical connection state: " + padId);
            connected.put(padId, states.get(padId));
        }
    }

    private void insertInCanonicalOrder(CircuitElm element) {
        removeAllOccurrences(element);
        Vector<CircuitElm> canonical = instance.getSimulationElements();
        int canonicalIndex = canonical.indexOf(element);
        for (int i = canonicalIndex + 1; i < canonical.size(); i++) {
            int activeIndex = sim.elmList.indexOf(canonical.get(i));
            if (activeIndex >= 0) {
                sim.elmList.add(activeIndex, element);
                return;
            }
        }
        sim.elmList.add(element);
    }

    private void removeAllOccurrences(CircuitElm element) {
        while (sim.elmList.remove(element)) {
        }
    }

    private int countOccurrences(CircuitElm element) {
        int count = 0;
        for (CircuitElm active : sim.elmList) {
            if (active == element)
                count++;
        }
        return count;
    }

    private void verifyCanonicalGeneratedElementOrder() {
        int priorIndex = -1;
        for (CircuitElm element : instance.getSimulationElements()) {
            int activeIndex = sim.elmList.indexOf(element);
            if (activeIndex < 0)
                continue;
            if (activeIndex <= priorIndex)
                throw new IllegalStateException("Generated element order differs from canonical order");
            priorIndex = activeIndex;
        }
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered() ||
                instance.getPhysicalBoardRuntime().isMutationInProgress() ||
                instance.getPhysicalBoardRuntime().isMutationQuarantined())
            throw new BoardModificationRejectedException(
                "Board modification requires electrically unpowered generated board");
    }

    private PhysicalMutationSlot getScopedMutationSlot(String componentId) {
        PhysicalSlotMutationProvider provider = instance.getPhysicalBoardRuntime()
            .getMutationProvider(componentId);
        if (!(provider instanceof PhysicalSlotMutationProvider.Scoped))
            return null;
        return ((PhysicalSlotMutationProvider.Scoped) provider).getMutationSlot();
    }

    private PhysicalMutationScope newScope(PhysicalMutationSlot mutationSlot,
            String operation, PhysicalPart<?> requestedPart) {
        return newScope(mutationSlot, operation, requestedPart, null, null);
    }

    private PhysicalMutationScope newScope(PhysicalMutationSlot mutationSlot,
            String operation, PhysicalPart<?> requestedPart, String padId,
            String catalogEntryId) {
        PhysicalMutationIntent intent = PhysicalMutationIntent.prepare(
            instance.getPhysicalBoardRuntime(), instance, this, mutationSlot,
            operation, padId, catalogEntryId, requestedPart);
        return new PhysicalMutationScope(sim, instance, this, intent);
    }

    private void finishMutationAfterCommittedScope(boolean changed) {
        finishMutationAfterCommittedScope(changed, true);
    }

    private void finishMutationAfterCommittedScope(boolean changed, boolean refreshControls) {
        try {
            finishMutation(changed, refreshControls);
        } catch (Throwable failure) {
            sim.markGeneratedRuntimeFailure(instance, failure);
            PhysicalMutationScope.rethrow(failure);
        }
    }

    private void finishMutation(boolean changed) {
        finishMutation(changed, true);
    }

    private void finishMutation(boolean changed, boolean refreshControls) {
        if (!changed)
            return;
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
	if (sim.getGeneratedChallengeController() != null)
	    sim.getGeneratedChallengeController().invalidateCustomerRetest();
	if (refreshControls)
	    sim.refreshBoardModificationControls();
    }
}
