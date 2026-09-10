package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * RC-owned stored-energy policy.  It only sees typed physical parts and
 * measurement endpoints; instrument modes never inspect capacitor classes.
 */
final class StoredEnergyMeasurementReadinessCapability implements
        ActiveMeasurementReadinessCapability, PhysicalBoardRuntimePowerLifecycle,
        PhysicalBoardRuntimeLifecycle, PhysicalBoardInstallationProvider {
    static final String CAPABILITY_ID = "STORED_ENERGY_MEASUREMENT_READINESS";
    private final ReplaceableCapacitorBoardCapability replaceable;
    private final PhysicalCapacitorPart fixedCapacitor;
    private final BoardSimulationBindings boardBindings;
    private boolean awaitingSolverSample;
    private CirSim sim;
    private GeneratedBoardInstance instance;
    private SolverExecutionBoundary.Observation observed, invalidated;

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

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double time) {
        if (sim == null || instance == null || instance.getSimulationBindings() != boardBindings ||
                instance.getPhysicalBoardRuntime().getCapability(CAPABILITY_ID) != this)
            throw new IllegalArgumentException("Foreign stored-energy installation");
        this.sim = sim; this.instance = instance; resetForBoardReset(); return null;
    }
    public void onBoardPowerStateChanged(BoardPowerState state) {
        awaitingSolverSample = true; invalidated = observed;
    }
    public void observeSimulationTime(double simulationTime) {
        if (sim == null || sim.getGeneratedBoardInstance() != instance ||
                !PowerDomainContract.finite(simulationTime) || simulationTime != sim.t) {
            resetForBoardReset(); return;
        }
        SolverExecutionBoundary.Observation sample = sim.solverExecutor.observation(instance);
        if (sample != null && sample != invalidated) {
            observed = sample; awaitingSolverSample = false;
        }
    }
    public void synchronizeSimulationTime(double simulationTime) {
        observeSimulationTime(simulationTime);
    }
    public void resetForBoardReset() {
        invalidated = sim == null ? observed : sim.solverExecutor.observation(instance);
        observed = null; awaitingSolverSample = true;
    }

    public ActiveMeasurementReadiness getActiveMeasurementReadiness(
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
            BoardPowerState powerState, boolean electricallyUnpowered) {
        if (!isRelevant(red, black))
            return ActiveMeasurementReadiness.READY;
        if (powerState != BoardPowerState.UNPOWERED || !electricallyUnpowered)
            return ActiveMeasurementReadiness.POWER_OFF;
        if (sim == null || sim.getGeneratedBoardInstance() != instance)
            return ActiveMeasurementReadiness.UNKNOWN;
        if (awaitingSolverSample || !sim.solverExecutor.isCurrent(observed, instance))
            return ActiveMeasurementReadiness.WAITING;
        return storageReadiness(red, black);
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

    private ActiveMeasurementReadiness storageReadiness(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        boolean boardMeasurement = isBoardEndpoint(red) || isBoardEndpoint(black);
        ActiveMeasurementReadiness result = ActiveMeasurementReadiness.READY;
        PhysicalCapacitorPart installed = replaceable.getSlot().getInstalledPart();
        if (boardMeasurement || isPartTerminal(installed, red) || isPartTerminal(installed, black))
            result = ActiveMeasurementReadiness.combine(result, partReadiness(installed));
        if (boardMeasurement || isPartTerminal(fixedCapacitor, red) || isPartTerminal(fixedCapacitor, black))
            result = ActiveMeasurementReadiness.combine(result, partReadiness(fixedCapacitor));
        for (PhysicalCapacitorPart part : replaceable.getInventory().getLooseParts())
            if (isPartTerminal(part, red) || isPartTerminal(part, black))
                result = ActiveMeasurementReadiness.combine(result, partReadiness(part));
        return result;
    }

    private ActiveMeasurementReadiness partReadiness(PhysicalCapacitorPart part) {
        if (part == null || !part.hasAccessibleStoredEnergyTerminals()) return ActiveMeasurementReadiness.READY;
        return voltageReadiness(part.getElement().getVoltageDiff());
    }

    static ActiveMeasurementReadiness voltageReadiness(double volts) {
        if (!PowerDomainContract.finite(volts)) return ActiveMeasurementReadiness.UNKNOWN;
        return Math.abs(volts) > ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS ?
            ActiveMeasurementReadiness.DISCHARGE : ActiveMeasurementReadiness.READY;
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
