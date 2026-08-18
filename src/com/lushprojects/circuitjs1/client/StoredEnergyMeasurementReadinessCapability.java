package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * RC-owned stored-energy policy.  It only sees typed physical parts and
 * measurement endpoints; instrument modes never inspect capacitor classes.
 */
final class StoredEnergyMeasurementReadinessCapability implements
        ActiveMeasurementReadinessCapability, PhysicalBoardRuntimePowerLifecycle,
        PhysicalBoardRuntimeLifecycle {
    static final String CAPABILITY_ID = "STORED_ENERGY_MEASUREMENT_READINESS";
    private final ReplaceableCapacitorBoardCapability replaceable;
    private final PhysicalCapacitorPart fixedCapacitor;
    private final BoardSimulationBindings boardBindings;
    private boolean awaitingSolverSample;
    private double lastObservedSimulationTime = Double.NaN;

    StoredEnergyMeasurementReadinessCapability(
            ReplaceableCapacitorBoardCapability replaceable,
            PhysicalCapacitorPart fixedCapacitor, BoardSimulationBindings boardBindings) {
        if (replaceable == null || fixedCapacitor == null || boardBindings == null)
            throw new IllegalArgumentException("Missing stored-energy readiness context");
        this.replaceable = replaceable;
        this.fixedCapacitor = fixedCapacitor;
        this.boardBindings = boardBindings;
    }

    public String getCapabilityId() { return CAPABILITY_ID; }

    public void onBoardPowerStateChanged(BoardPowerState state) {
        awaitingSolverSample = true;
    }

    public void observeSimulationTime(double simulationTime) {
        if (!Double.isNaN(simulationTime) && simulationTime != lastObservedSimulationTime) {
            lastObservedSimulationTime = simulationTime;
            awaitingSolverSample = false;
        }
    }

    public void synchronizeSimulationTime(double simulationTime) { observeSimulationTime(simulationTime); }
    public void resetForBoardReset() {
        awaitingSolverSample = true;
        lastObservedSimulationTime = Double.NaN;
    }

    public ActiveMeasurementReadiness getActiveMeasurementReadiness(
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
            BoardPowerState powerState, boolean electricallyUnpowered) {
        if (!isRelevant(red, black))
            return ActiveMeasurementReadiness.READY;
        if (powerState != BoardPowerState.UNPOWERED || !electricallyUnpowered)
            return ActiveMeasurementReadiness.POWER_OFF;
        if (awaitingSolverSample)
            return ActiveMeasurementReadiness.WAITING;
        return hasResidualEnergy(red, black) ? ActiveMeasurementReadiness.DISCHARGE :
            ActiveMeasurementReadiness.READY;
    }

    public boolean usesLiveDcVoltage(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        return isRelevant(red, black);
    }

    private boolean isRelevant(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        if (red == null || black == null)
            return false;
        return isBoardEndpoint(red) || isBoardEndpoint(black) ||
            isPartTerminal(replaceable.getSlot().getInstalledPart(), red) ||
            isPartTerminal(replaceable.getSlot().getInstalledPart(), black) ||
            isPartTerminal(fixedCapacitor, red) || isPartTerminal(fixedCapacitor, black) ||
            isLoosePartTerminal(red) || isLoosePartTerminal(black);
    }

    private boolean isBoardEndpoint(CircuitPostMeasurementEndpoint endpoint) {
        String netId = boardBindings.getNetIdForEndpoint(endpoint);
        return netId != null && (isInstalledStorageNet(replaceable.getSlot().getInstalledPart(),
                netId) || isInstalledStorageNet(fixedCapacitor, netId));
    }

    /**
     * Installed storage controls measurements on its whole exposed board net,
     * not merely the connector post chosen by a family.  Loose parts have no
     * board slot, so they remain relevant only when their own terminals are
     * directly selected below.
     */
    private boolean isInstalledStorageNet(PhysicalCapacitorPart part, String netId) {
        if (part == null || !part.isInstalled() || part.getBoardSlot() == null)
            return false;
        for (String partNetId : part.getBoardSlot().getNetIds())
            if (netId.equals(partNetId))
                return true;
        return false;
    }

    private boolean isLoosePartTerminal(CircuitPostMeasurementEndpoint endpoint) {
        for (PhysicalCapacitorPart part : replaceable.getInventory().getLooseParts())
            if (isPartTerminal(part, endpoint))
                return true;
        return false;
    }

    private boolean hasResidualEnergy(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        boolean boardMeasurement = isBoardEndpoint(red) || isBoardEndpoint(black);
        PhysicalCapacitorPart installed = replaceable.getSlot().getInstalledPart();
        if (hasResidual(installed) && (boardMeasurement || isPartTerminal(installed, red) ||
                isPartTerminal(installed, black)))
            return true;
        if (hasResidual(fixedCapacitor) && (boardMeasurement || isPartTerminal(fixedCapacitor, red) ||
                isPartTerminal(fixedCapacitor, black)))
            return true;
        for (PhysicalCapacitorPart part : replaceable.getInventory().getLooseParts())
            if ((isPartTerminal(part, red) || isPartTerminal(part, black)) && hasResidual(part))
                return true;
        return false;
    }

    private boolean hasResidual(PhysicalCapacitorPart part) {
        return part != null && part.hasAccessibleStoredEnergyTerminals() &&
            Math.abs(part.getElement().getVoltageDiff()) >
            ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS;
    }

    private boolean isPartTerminal(PhysicalCapacitorPart part,
            CircuitPostMeasurementEndpoint endpoint) {
        if (part == null || endpoint == null)
            return false;
        for (PhysicalPartTerminal terminal : part.getTerminals()) {
            CircuitMeasurementEndpoint partEndpoint = terminal.getEndpoint();
            if (partEndpoint instanceof CircuitPostMeasurementEndpoint && sameEndpoint(
                    endpoint, (CircuitPostMeasurementEndpoint) partEndpoint))
                return true;
        }
        return false;
    }

    private boolean sameEndpoint(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement() == second.getElement() &&
            first.getPostIndex() == second.getPostIndex();
    }
}
