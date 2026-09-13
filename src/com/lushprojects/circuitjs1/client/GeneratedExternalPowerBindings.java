package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class GeneratedExternalPowerBindings {
    private final TroubleshootBoard board;
    private boolean constructionAborted;
    private final HashMap<String, ExternalPowerSimulationBinding> powerBindings =
        new HashMap<String, ExternalPowerSimulationBinding>();

    GeneratedExternalPowerBindings(TroubleshootBoard board) {
        this.board = board;
    }

    TroubleshootBoard getBoardForRuntimeValidation() { return board; }

    void bindPowerInput(String powerInputId, ExternalPowerSimulationBinding binding) {
        if (constructionAborted)
            throw new IllegalStateException("Construction bindings were revoked");
        if (board.getPowerInput(powerInputId) == null)
            throw new IllegalArgumentException("Unknown board power input: " + powerInputId);
        if (binding == null)
            throw new IllegalArgumentException("Missing external power simulation binding: " + powerInputId);
        if (powerBindings.containsKey(powerInputId))
            throw new IllegalArgumentException("Duplicate external power simulation binding: " + powerInputId);
        powerBindings.put(powerInputId, binding);
    }

    ExternalPowerSimulationBinding getBinding(String powerInputId) {
        ExternalPowerSimulationBinding binding = powerBindings.get(powerInputId);
        if (binding == null)
            throw new IllegalArgumentException("Unknown external power simulation binding: " + powerInputId);
        return binding;
    }

    boolean hasControlsForAllInputs() {
    for (String powerInputId : board.getPowerInputIds()) {
        ExternalPowerSimulationBinding binding = powerBindings.get(powerInputId);
        if (binding == null || !binding.hasControl())
        return false;
    }
    return !board.getPowerInputIds().isEmpty();
    }

    void setConnected(boolean connected) {
    if (!hasControlsForAllInputs())
        throw new IllegalStateException("Generated board power inputs are not fully controllable");
    for (String powerInputId : board.getPowerInputIds())
        powerBindings.get(powerInputId).setConnected(connected);
    }

    boolean areAllConnected() {
    if (!hasControlsForAllInputs())
        return false;
    for (String powerInputId : board.getPowerInputIds()) {
        if (!powerBindings.get(powerInputId).isConnected())
        return false;
    }
    return true;
    }

    boolean areAllDisconnected() {
    if (!hasControlsForAllInputs())
        return false;
    for (String powerInputId : board.getPowerInputIds()) {
        if (powerBindings.get(powerInputId).isConnected())
        return false;
    }
    return true;
    }

    boolean hasNominalBenchSettings() {
        if (!areAllConnected()) return false;
        for (ExternalPowerSimulationBinding binding : powerBindings.values()) {
            LimitedDcSupplyElm supply = binding.getLimitedSupply();
            if (supply != null && supply.getLimitAmps() != LowVoltageSourceModel.DEFAULT_LIMIT_AMPS)
                return false;
        }
        return true;
    }

    /** Exact source commands for fresh-owner rollback; observation revisions stay monotonic. */
    final class SavedControls {
        private final Vector<String> ids = board.getPowerInputIds();
        private final ExternalPowerSimulationBinding[] bindings = new ExternalPowerSimulationBinding[ids.size()];
        private final boolean[] connected = new boolean[ids.size()];
        private final double[] limits = new double[ids.size()];
        SavedControls() {
            for(int i=0;i<ids.size();i++) {
                bindings[i]=getBinding(ids.get(i)); connected[i]=bindings[i].isConnected();
                LimitedDcSupplyElm supply=bindings[i].getLimitedSupply();
                limits[i]=supply==null?Double.NaN:supply.getLimitAmps();
            }
        }
        void restore(GeneratedExternalPowerBindings target) {
            if(target!=GeneratedExternalPowerBindings.this || target.constructionAborted)
                throw new IllegalArgumentException("Foreign or retired source snapshot");
            for(int i=0;i<ids.size();i++)
                if(target.getBinding(ids.get(i))!=bindings[i]) throw new IllegalStateException("Source binding replaced");
            for(int i=0;i<ids.size();i++) {
                LimitedDcSupplyElm supply=bindings[i].getLimitedSupply();
                if(supply!=null && supply.getLimitAmps()!=limits[i]) bindings[i].setCurrentLimit(limits[i]);
                bindings[i].setConnected(connected[i]);
            }
        }
        boolean matches() {
            for(int i=0;i<ids.size();i++) {
                if(getBinding(ids.get(i))!=bindings[i] || bindings[i].isConnected()!=connected[i]) return false;
                LimitedDcSupplyElm supply=bindings[i].getLimitedSupply();
                if(supply!=null && supply.getLimitAmps()!=limits[i]) return false;
            }
            return true;
        }
    }
    SavedControls saveControls() { return new SavedControls(); }

    /** Current control observations; the aggregate OFF command is not an energy assessment. */
    java.util.Map<String, PowerOperatingAssessment.SourceState> getSourceStates() {
        java.util.TreeMap<String, PowerOperatingAssessment.SourceState> result =
            new java.util.TreeMap<String, PowerOperatingAssessment.SourceState>();
        for (String id : board.getPowerInputIds()) {
            ExternalPowerSimulationBinding binding = powerBindings.get(id);
            result.put(id, constructionAborted || binding == null || !binding.hasControl() ?
                PowerOperatingAssessment.SourceState.unknown() : binding.isConnected() ?
                PowerOperatingAssessment.SourceState.connected() : PowerOperatingAssessment.SourceState.isolated());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    String controlSignature() {
        StringBuilder out = new StringBuilder();
        for (String id : new java.util.TreeSet<String>(board.getPowerInputIds())) {
            ExternalPowerSimulationBinding binding = powerBindings.get(id);
            PowerDomainContract.token(out, id);
            PowerDomainContract.token(out, constructionAborted || binding == null || !binding.hasControl() ?
                "UNKNOWN" : (binding.isConnected() ? "CONNECTED:" : "ISOLATED:") + binding.getConnectionRevision());
        }
        return out.toString();
    }

    /** Allocation-free validation of the exact control states captured at operation entry. */
    final class ControlObservation {
        private final String[] ids;
        private final ExternalPowerSimulationBinding[] bindings;
        private final long[] revisions;
        private final boolean[] connected, controlled;
        private ControlObservation() {
            Vector<String> inputIds = board.getPowerInputIds();
            ids = new String[inputIds.size()]; bindings = new ExternalPowerSimulationBinding[ids.length];
            revisions = new long[ids.length]; connected = new boolean[ids.length]; controlled = new boolean[ids.length];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = inputIds.get(i); bindings[i] = powerBindings.get(ids[i]);
                ExternalPowerSimulationBinding binding = bindings[i];
                if (binding != null) {
                    revisions[i] = binding.getConnectionRevision();
                    controlled[i] = binding.hasControl(); connected[i] = binding.isConnected();
                }
            }
        }
        boolean isCurrent() {
            if (constructionAborted || powerBindings.size() != ids.length) return false;
            for (int i = 0; i < ids.length; i++) {
                ExternalPowerSimulationBinding binding = powerBindings.get(ids[i]);
                if (binding != bindings[i] || binding == null || binding.getConnectionRevision() != revisions[i] ||
                        binding.hasControl() != controlled[i] || binding.isConnected() != connected[i]) return false;
            }
            return true;
        }
    }
    ControlObservation observeControls() { return new ControlObservation(); }

    boolean isBackingElement(CircuitElm element) {
        for (ExternalPowerSimulationBinding binding : powerBindings.values()) {
            if (binding.getBackingElements().contains(element))
                return true;
        }
        return false;
    }

    void validateElementsAreOwnedBy(Vector<CircuitElm> simulationElements) {
        for (String powerInputId : powerBindings.keySet()) {
            for (CircuitElm element : powerBindings.get(powerInputId).getBackingElements()) {
                if (!simulationElements.contains(element))
                    throw new IllegalStateException("Power binding is not owned by generated board: " + powerInputId);
            }
        }
    }

    /** Clears only this exact private candidate owner after failed construction. */
    void clearForAbortedConstruction(TroubleshootBoard expectedBoard) {
        if (expectedBoard == null || board != expectedBoard)
            throw new IllegalArgumentException("Foreign construction binding owner");
        constructionAborted = true;
        powerBindings.clear();
    }
}
