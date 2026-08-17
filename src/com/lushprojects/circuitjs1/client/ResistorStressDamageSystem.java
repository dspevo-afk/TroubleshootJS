package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Owns resistor service-time stress and secondary failure state.  It observes
 * solved ResistorElm power only after CircuitJS has stepped the real graph;
 * it does not infer stress from catalog values or renderer activity.
 */
class ResistorStressDamageSystem {
    private static final double FAILURE_DAMAGE = 1.0;

    private final CirSim sim;
    private final ReplaceableResistorBoardCapability capability;
    private final HashMap<String, ResistorStressState> states =
        new HashMap<String, ResistorStressState>();
    private double lastSimulationTime;

    ResistorStressDamageSystem(CirSim sim, ReplaceableResistorBoardCapability capability,
            double initialSimulationTime) {
        if (sim == null || capability == null)
            throw new IllegalArgumentException("Missing resistor stress owner");
        this.sim = sim;
        this.capability = capability;
        this.lastSimulationTime = initialSimulationTime;
        for (PhysicalResistorPart part : capability.getInventory().getAll())
            register(part);
    }

    void register(PhysicalResistorPart part) {
        if (part == null || states.containsKey(part.getId()))
            throw new IllegalArgumentException("Duplicate resistor stress part");
        states.put(part.getId(), new ResistorStressState(part));
    }

    ResistorStressState getState(String partId) {
        ResistorStressState state = states.get(partId);
        if (state == null)
            throw new IllegalArgumentException("Unknown resistor stress part: " + partId);
        return state;
    }

    Vector<ResistorStressState> getStates() {
        return new Vector<ResistorStressState>(states.values());
    }

    void observeSimulationTime(double simulationTime) {
        if (Double.isNaN(simulationTime) || Double.isInfinite(simulationTime))
            return;
        double delta = simulationTime - lastSimulationTime;
        lastSimulationTime = simulationTime;
        if (delta <= 0 || sim.activeMeasurementOverlay || !isBoardPowered())
            return;
        integrate(delta);
    }

    void synchronizeSimulationTime(double simulationTime) {
        if (!Double.isNaN(simulationTime) && !Double.isInfinite(simulationTime))
            lastSimulationTime = simulationTime;
    }

    void advanceServiceTimeForDeveloperVerification(double seconds) {
        if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds))
            throw new IllegalArgumentException("Service time must be finite and positive");
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("Resistor service time cannot advance during meter overlay");
        if (!isBoardPowered()) {
            synchronizeSimulationTime(sim.t);
            return;
        }
        refreshSolvedMeasurements();
        integrate(seconds);
        synchronizeSimulationTime(sim.t);
    }

    void refreshSolvedMeasurements() {
        for (ResistorStressState state : states.values()) {
            if (!isInstalled(state.getPart()))
                continue;
            double power = Math.abs(state.getPart().getElement().getPower());
            if (!Double.isNaN(power) && !Double.isInfinite(power)) {
                state.actualPower = power;
                state.stressRatio = power / state.getPart().getRatedWattage();
            }
        }
    }

    void resetForBoardReset() {
        for (ResistorStressState state : states.values()) {
            state.accumulatedDamage = 0;
            state.serviceTime = 0;
            state.failureServiceTime = Double.NaN;
            state.actualPower = 0;
            state.stressRatio = 0;
            state.failed = false;
            state.getPart().getSecondaryOpenPath().resetForBoardReset();
        }
        synchronizeSimulationTime(sim.t);
    }

    private void integrate(double seconds) {
        ReplaceableComponentSlot slot = capability.getSlot();
        PhysicalResistorPart installed = slot.isEmpty() ? null : slot.getInstalledPart();
        for (ResistorStressState state : states.values()) {
            PhysicalResistorPart part = state.getPart();
            if (part != installed || !part.isInstalled())
                continue;
            double power = Math.abs(part.getElement().getPower());
            if (Double.isNaN(power) || Double.isInfinite(power))
                continue;
            state.actualPower = power;
            state.stressRatio = power / part.getRatedWattage();
            state.serviceTime += seconds;
            if (state.failed || state.stressRatio <= 1)
                continue;
            double excess = state.stressRatio - 1;
            double damageRate = excess * excess / 2;
            double priorDamage = state.accumulatedDamage;
            state.accumulatedDamage += damageRate * seconds;
            if (state.accumulatedDamage >= FAILURE_DAMAGE) {
                double timeToFailure = (FAILURE_DAMAGE - priorDamage) / damageRate;
                state.failureServiceTime = state.serviceTime - seconds + timeToFailure;
                state.failed = true;
                part.getSecondaryOpenPath().open();
                sim.needAnalyze();
            }
        }
    }

    private boolean isInstalled(PhysicalResistorPart part) {
        ReplaceableComponentSlot slot = capability.getSlot();
        return !slot.isEmpty() && slot.getInstalledPart() == part &&
            part.isInstalled();
    }

    private boolean isBoardPowered() {
        return sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
            !sim.getBoardPowerController().isElectricallyUnpowered();
    }
}

class ResistorStressState {
    private final PhysicalResistorPart part;
    double actualPower;
    double stressRatio;
    double accumulatedDamage;
    double serviceTime;
    double failureServiceTime = Double.NaN;
    boolean failed;

    ResistorStressState(PhysicalResistorPart part) { this.part = part; }

    PhysicalResistorPart getPart() { return part; }
    double getActualPower() { return actualPower; }
    double getStressRatio() { return stressRatio; }
    double getAccumulatedDamage() { return accumulatedDamage; }
    double getServiceTime() { return serviceTime; }
    double getFailureServiceTime() { return failureServiceTime; }
    boolean isFailed() { return failed; }
}
