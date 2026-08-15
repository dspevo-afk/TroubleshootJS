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

    boolean isComponentInstalled(String componentId) {
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent(componentId))
            if (!connected.get(binding.getPadId()).booleanValue())
                return false;
        return true;
    }

    boolean isFullyRestored() {
        for (Boolean state : connected.values())
            if (!state.booleanValue())
                return false;
        return true;
    }

    void verifyStructuralState() {
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getAll()) {
            boolean present = sim.elmList.contains(binding.getConnectionElement());
            if (present != connected.get(binding.getPadId()).booleanValue())
                throw new IllegalStateException("Connection state disagrees with graph: " + binding.getPadId());
        }
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
            sim.elmList.add(binding.getConnectionElement());
        else
            sim.elmList.remove(binding.getConnectionElement());
        connected.put(binding.getPadId(), Boolean.valueOf(shouldConnect));
        return true;
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new IllegalStateException("Board modification requires electrically unpowered generated board");
    }

    private void finishMutation(boolean changed) {
        if (!changed)
            return;
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
	sim.refreshBoardModificationControls();
    }
}