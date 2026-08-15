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

    boolean removeComponent(String componentId) {
        requireSafeMutation();
        boolean changed = false;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent(componentId))
            changed |= setConnection(binding, false);
        finishMutation(changed);
        return changed;
    }

    boolean restoreComponent(String componentId) {
        requireSafeMutation();
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
        boolean changed = setConnection(instance.getConnectionBindings().get(componentId, padId), shouldConnect);
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
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException(
                "Board modification requires electrically unpowered generated board");
    }

    private void finishMutation(boolean changed) {
        if (!changed)
            return;
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
	sim.refreshBoardModificationControls();
    }
}