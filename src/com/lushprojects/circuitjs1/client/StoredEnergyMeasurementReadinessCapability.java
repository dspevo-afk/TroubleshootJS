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
    private final PhysicalBoardRuntime runtime;
    private final BoardSimulationBindings boardBindings;
    private boolean awaitingSolverSample;
    private CirSim sim;
    private GeneratedBoardInstance instance;
    private SolverExecutionBoundary.Observation observed, invalidated;

    StoredEnergyMeasurementReadinessCapability(
            PhysicalBoardRuntime runtime, BoardSimulationBindings boardBindings) {
        if (runtime == null || boardBindings == null || runtime.getBoard().getSimulationBindings() != boardBindings)
            throw new IllegalArgumentException("Missing stored-energy readiness context");
        this.runtime = runtime;
        this.boardBindings = boardBindings;
    }

    public String getCapabilityId() { return CAPABILITY_ID; }

    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double time) {
        if (sim == null || instance == null || instance.getPhysicalBoardRuntime() != runtime ||
                instance.getSimulationBindings() != boardBindings ||
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
        if (isBoardEndpoint(red) || isBoardEndpoint(black)) return true;
        for (PhysicalCapacitorPart part : capacitors())
            if (isPartTerminal(part, red) || isPartTerminal(part, black)) return true;
        return false;
    }

    private boolean isBoardEndpoint(CircuitPostMeasurementEndpoint endpoint) {
        String netId = boardBindings.getNetIdForEndpoint(endpoint);
        if (netId != null) for (PhysicalCapacitorPart part : capacitors())
            if (isInstalledStorageNet(part, netId)) return true;
        return false;
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

    private Vector<PhysicalCapacitorPart> capacitors() {
        Vector<PhysicalCapacitorPart> result = new Vector<PhysicalCapacitorPart>();
        for (PhysicalPart<?> part : runtime.getPhysicalParts())
            if (part instanceof PhysicalCapacitorPart) result.add((PhysicalCapacitorPart)part);
        return result;
    }

    private ActiveMeasurementReadiness storageReadiness(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        boolean boardMeasurement = isBoardEndpoint(red) || isBoardEndpoint(black);
        ActiveMeasurementReadiness result = ActiveMeasurementReadiness.READY;
        for (PhysicalCapacitorPart part : capacitors())
            if ((boardMeasurement && part.isInstalled()) || isPartTerminal(part, red) || isPartTerminal(part, black))
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
