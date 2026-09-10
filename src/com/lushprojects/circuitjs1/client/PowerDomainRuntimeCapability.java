package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** On-demand assessment using the installed owner's real controls and solved posts. */
final class PowerDomainRuntimeCapability implements ActiveMeasurementReadinessCapability,
        MeasurementReferenceCapability, PhysicalBoardInstallationProvider,
        PhysicalBoardRuntimeLifecycle, PhysicalBoardRuntimePowerLifecycle {
    static final String CAPABILITY_ID = "POWER_DOMAIN_ASSESSMENT";
    private final PowerDomainContract contract;
    private final TroubleshootBoard board;
    private final GeneratedExternalPowerBindings bindings;
    private CirSim sim;
    private GeneratedBoardInstance instance;
    private String observedSignature;
    private SolverExecutionBoundary.Observation observed;
    private SolverExecutionBoundary.Observation invalidated;
    private boolean awaitingSample = true;
    PowerDomainRuntimeCapability(PowerDomainContract contract, TroubleshootBoard board,
            GeneratedExternalPowerBindings bindings) {
        if (contract == null || board == null || bindings == null ||
                bindings.getBoardForRuntimeValidation() != board)
            throw new IllegalArgumentException("Mismatched power-domain owner");
        contract.requireSourceCoverage(board.getPowerInputIds());
        for (PowerDomainContract.Rail rail : contract.getRails().values())
            if (board.getNet(rail.getId()) == null || board.getNet(rail.getReferenceId()) == null)
                throw new IllegalArgumentException("Power declaration is not mapped to the board");
        this.contract = contract; this.board = board; this.bindings = bindings;
    }
    public String getCapabilityId() { return CAPABILITY_ID; }
    PowerDomainContract getContract() { return contract; }
    public PhysicalSlotMutationProvider install(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, double time) {
        if (sim == null || instance == null || instance.getBoard() != board ||
                instance.getExternalPowerBindings() != bindings ||
                instance.getPhysicalBoardRuntime().getCapability(CAPABILITY_ID) != this)
            throw new IllegalArgumentException("Foreign power-domain installation");
        this.sim = sim; this.instance = instance; resetForBoardReset();
        return null;
    }
    private boolean isCurrentOwner() {
        return sim != null && instance != null && CircuitElm.sim == sim &&
            sim.getGeneratedBoardInstance() == instance && instance.getBoard() == board &&
            sim.getBoardPowerController().getBindingsForDeveloperVerification() == bindings &&
            instance.getPhysicalBoardRuntime().getCapability(CAPABILITY_ID) == this;
    }
    public void onBoardPowerStateChanged(BoardPowerState state) {
        awaitingSample = true; invalidated = observed;
    }
    public void resetForBoardReset() {
        invalidated = sim == null ? observed : sim.solverExecutor.observation(instance);
        awaitingSample = true; observed = null; observedSignature = null;
    }
    public void observeSimulationTime(double time) {
        if (!isCurrentOwner() || !PowerDomainContract.finite(time) || time != sim.t) {
            resetForBoardReset(); return;
        }
        SolverExecutionBoundary.Observation sample = sim.solverExecutor.observation(instance);
        if (sample != null && sample != invalidated) {
            observed = sample; observedSignature = bindings.controlSignature(); awaitingSample = false;
        }
    }
    public void synchronizeSimulationTime(double time) {
        // Only an existing accepted solver receipt can qualify. A time rebase cannot mint one.
        observeSimulationTime(time);
    }
    PowerOperatingAssessment assessPower() {
        Map<String,Double> volts = new TreeMap<String,Double>();
        boolean current = isCurrentOwner() && sim.isGeneratedRuntimeSettled() &&
            !awaitingSample && sim.solverExecutor.isCurrent(observed, instance) &&
            bindings.controlSignature().equals(observedSignature);
        if (current)
            for (PowerDomainContract.Rail rail : contract.getRails().values())
                volts.put(rail.getId(), readNet(rail.getId()) - readNet(rail.getReferenceId()));
        return PowerOperatingAssessment.assess(contract, bindings.getSourceStates(), volts, current);
    }
    private double readNet(String net) {
        Vector<CircuitMeasurementEndpoint> endpoints = board.getSimulationBindings().getEndpointsForNet(net);
        if (endpoints.isEmpty()) return Double.NaN;
        Double value = null;
        for (CircuitMeasurementEndpoint endpoint : endpoints) {
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint)) return Double.NaN;
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            if (!sim.containsElement(post.getElement()) || !instance.ownsRuntimeSimulationElement(post.getElement()) ||
                    post.getPostIndex() < 0 || post.getPostIndex() >= post.getElement().getPostCount()) return Double.NaN;
            double sample = post.getElement().getPostVoltage(post.getPostIndex());
            if (!PowerDomainContract.finite(sample)) return Double.NaN;
            if (value != null && Math.abs(value - sample) > 1e-6) return Double.NaN;
            value = sample;
        }
        return value == null ? Double.NaN : value;
    }
    public ActiveMeasurementReadiness getActiveMeasurementReadiness(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black, BoardPowerState state, boolean unpowered) {
        if (!isCurrentOwner()) return ActiveMeasurementReadiness.UNKNOWN;
        if (state != BoardPowerState.UNPOWERED || !unpowered) return ActiveMeasurementReadiness.POWER_OFF;
        switch (assessPower().getReadiness()) {
        case POWER_OFF: return ActiveMeasurementReadiness.POWER_OFF;
        case WAITING: return ActiveMeasurementReadiness.WAITING;
        case DISCHARGE: return ActiveMeasurementReadiness.DISCHARGE;
        case READY: return ActiveMeasurementReadiness.READY;
        default: return ActiveMeasurementReadiness.UNKNOWN;
        }
    }
    public boolean usesLiveDcVoltage(CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        return false;
    }
    public MeasurementReferencePolicy.Result assessReference(MeasurementReferencePolicy.Mode mode,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        if (!isCurrentOwner()) return new MeasurementReferencePolicy.Result(
            MeasurementReferencePolicy.Decision.UNPROVEN, "STALE_OWNER");
        String r = board.getSimulationBindings().getNetIdForEndpoint(red);
        String b = board.getSimulationBindings().getNetIdForEndpoint(black);
        if ((r == null || b == null) && mode == MeasurementReferencePolicy.Mode.DIFFERENTIAL)
            return MeasurementReferencePolicy.notApplicable();
        return MeasurementReferencePolicy.check(contract, mode, r, b, null);
    }
}
