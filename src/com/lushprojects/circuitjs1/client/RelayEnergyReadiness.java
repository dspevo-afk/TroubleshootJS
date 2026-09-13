package com.lushprojects.circuitjs1.client;

/** Fail-closed, exact-owner residual coil-current guard for active instruments. */
final class RelayEnergyReadiness implements ActiveMeasurementReadinessCapability,
        ActiveMeasurementSettlingCapability,
        MeasurementReferenceCapability, PowerDomainContractProvider,
        PhysicalBoardRuntimePowerLifecycle, PhysicalBoardRuntimeLifecycle, PhysicalBoardInstallationProvider {
    static final String ID = "RELAY_ENERGY_READINESS";
    private CirSim sim;
    private final PowerDomainContract contract = RelayPowerDomains.create();
    private GeneratedBoardInstance owner;
    private SolverExecutionBoundary.Observation observed, invalidated;
    public String getCapabilityId() { return ID; }
    public PowerDomainContract getContract() { return contract; }
    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, double time) {
        if (owner.getPhysicalBoardRuntime().getCapability(ID) != this)
            throw new IllegalArgumentException("Foreign relay readiness policy");
        contract.requireSourceCoverage(owner.getBoard().getPowerInputIds());
        this.sim = sim; this.owner = owner; resetForBoardReset(); return null;
    }
    public void onBoardPowerStateChanged(BoardPowerState state) { invalidated = observed; observed = null; }
    public void resetForBoardReset() {
        invalidated = sim == null ? observed : sim.solverExecutor.observation(owner); observed = null;
    }
    public void synchronizeSimulationTime(double time) { observeSimulationTime(time); }
    public void observeSimulationTime(double time) {
        if (sim == null || sim.getGeneratedBoardInstance() != owner || time != sim.t) { observed = null; return; }
        SolverExecutionBoundary.Observation sample = sim.solverExecutor.observation(owner);
        if (sample != invalidated) observed = sample;
    }
    public ActiveMeasurementReadiness getActiveMeasurementReadiness(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black, BoardPowerState power, boolean allOff) {
        if (power != BoardPowerState.UNPOWERED || !allOff) return ActiveMeasurementReadiness.POWER_OFF;
        if (sim == null || sim.getGeneratedBoardInstance() != owner) return ActiveMeasurementReadiness.UNKNOWN;
        if (!sim.solverExecutor.isCurrent(observed, owner)) return ActiveMeasurementReadiness.WAITING;
        return RelayOutputBehavior.isDischarged(owner) ? ActiveMeasurementReadiness.READY : ActiveMeasurementReadiness.DISCHARGE;
    }
    public boolean usesLiveDcVoltage(CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) { return true; }

    public MeasurementReferencePolicy.Result assessReference(MeasurementReferencePolicy.Mode mode,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        if (sim == null || sim.getGeneratedBoardInstance() != owner)
            return new MeasurementReferencePolicy.Result(MeasurementReferencePolicy.Decision.UNPROVEN,"STALE_OWNER");
        // Only resolved board nets have a qualified voltage reference. Loose parts remain
        // measurable with the isolated active resistance/diode instruments.
        return MeasurementReferencePolicy.check(contract,mode,
            owner.getSimulationBindings().getNetIdForEndpoint(red),
            owner.getSimulationBindings().getNetIdForEndpoint(black),null);
    }

    public void settleMeasurement(CirSim selectedSim, GeneratedBoardInstance selectedOwner, boolean stimulusInstalled) {
        if (sim != selectedSim || owner != selectedOwner || sim.getGeneratedBoardInstance() != owner ||
                !sim.activeMeasurementOverlay || !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new IllegalStateException("Relay measurement settling requires its isolated owner");
        // The real meter energizes the RL winding. Allow >15 healthy-coil time constants
        // both before sampling and after source removal; never reset stored energy.
        sim.solverExecutor.advanceFor(RelayOutputBehavior.SAMPLE_SECONDS);
        observeSimulationTime(sim.t);
        if (!stimulusInstalled && !RelayOutputBehavior.isDischarged(owner))
            throw new IllegalStateException("Relay instrument energy did not discharge");
    }
}
