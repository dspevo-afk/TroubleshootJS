package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Provider-owned, bounded readiness work before a physical service action. */
final class GeneratedDiagnosticServicePreparation {
    private static final int MAX_WORK_UNITS = 64;
    private static final double MAX_UNIT_SECONDS = 1.0;

    private GeneratedDiagnosticServicePreparation() { }

    /** Optional provider surface; providers without it retain their old proof path. */
    interface Provider {
        Policy getServicePreparationPolicy();
    }

    interface Checkpoint {
        void check();
    }

    /** Immutable declared cap for answer-blind work before REMOVE. */
    static final class Policy {
        private final int workUnits;
        private final double maximumAdvanceSeconds;

        Policy(int workUnits, double maximumAdvanceSeconds) {
            if (workUnits < 1 || workUnits > MAX_WORK_UNITS ||
                    !finite(maximumAdvanceSeconds) || maximumAdvanceSeconds <= 0 ||
                    maximumAdvanceSeconds > MAX_UNIT_SECONDS ||
                    workUnits * maximumAdvanceSeconds > 10.0)
                throw new IllegalArgumentException("Invalid diagnostic service preparation bound");
            this.workUnits = workUnits;
            this.maximumAdvanceSeconds = maximumAdvanceSeconds;
        }

        int getWorkUnits() { return workUnits; }
        double getMaximumAdvanceSeconds() { return maximumAdvanceSeconds; }

        String canonical() {
            return "action=REMOVE;units=" + workUnits +
                ";maximumAdvanceSeconds=" + Double.toString(maximumAdvanceSeconds) +
                ";readiness=exact-current-owner";
        }
    }

    /** A narrow boundary which keeps tests independent of solver and UI state. */
    interface Host {
        void checkCurrent();
        boolean isAvailable();
        void advance(double seconds);
    }

    /**
     * Captures the exact service context and executes one readiness work unit
     * per step. It retains handles only for the lifetime of the proof cursor.
     */
    static Cursor begin(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController challenge, String componentId,
            PhysicalPart<?> target, Policy policy, Checkpoint checkpoint) {
        return new Cursor(policy, new OwnerHost(sim, owner, challenge,
            componentId, target, checkpoint));
    }

    /** One bounded, resumable readiness cursor. */
    static final class Cursor {
        private final Policy policy;
        private final Host host;
        private int completedUnits;
        private boolean complete;
        private boolean closed;

        Cursor(Policy policy, Host host) {
            if (policy == null || host == null)
                throw new IllegalArgumentException("Missing diagnostic service preparation context");
            this.policy = policy;
            this.host = host;
            host.checkCurrent();
        }

        /** Executes one declared unit and returns true while more units remain. */
        boolean step() {
            ensureOpen();
            if (complete) return false;
            host.checkCurrent();
            boolean available = currentAvailability();
            if (!available) {
                host.advance(policy.maximumAdvanceSeconds);
                host.checkCurrent();
                available = currentAvailability();
            }
            completedUnits++;
            if (completedUnits == policy.workUnits) {
                require(available,
                    "Physical REMOVE did not become available within the declared service bound");
                complete = true;
            }
            host.checkCurrent();
            return !complete;
        }

        /** Final gate repeats the real readiness query immediately before REMOVE. */
        void finish() {
            ensureOpen();
            require(complete && completedUnits == policy.workUnits,
                "Diagnostic service preparation cursor is incomplete");
            host.checkCurrent();
            require(currentAvailability(),
                "Physical REMOVE readiness expired before service dispatch");
            closed = true;
        }

        /**
         * No temporary graph state belongs to this read-only cursor. Closing
         * it must remain possible after a monotonic source revision changes;
         * cancellation touches only this cursor, never its host or successor.
         */
        void cancel() {
            if (closed) return;
            closed = true;
        }

        int getCompletedUnits() { return completedUnits; }
        boolean isComplete() { return complete; }

        private boolean currentAvailability() {
            host.checkCurrent();
            boolean available = host.isAvailable();
            host.checkCurrent();
            return available;
        }

        private void ensureOpen() {
            if (closed)
                throw new IllegalStateException("Diagnostic service preparation cursor is closed");
        }
    }

    /** Exact production owner/workbench snapshot used by the proof cursor. */
    private static final class OwnerHost implements Host {
        private final CirSim sim;
        private final GeneratedBoardInstance owner;
        private final GeneratedChallengeController challenge;
        private final Vector<CircuitElm> graph;
        private final Vector<CircuitElm> graphContents;
        private final PcbWorkbenchController workbench;
        private final BoardModificationController modifications;
        private final PhysicalBoardRuntime runtime;
        private final PhysicalSlotMutationProvider mutationProvider;
        private final PhysicalMutationReceipt mutationReceipt;
        private final GeneratedPhysicalAdmission physicalAdmission;
        private final GeneratedExternalPowerBindings bindings;
        private final GeneratedExternalPowerBindings.ControlObservation controls;
        private final String[] sourceIds;
        private final LimitedDcSupplyElm[] sources;
        private final double[] sourceVoltages;
        private final double[] sourceLimits;
        private final BoardPowerController powerController;
        private final PhysicalPart<?> target;
        private final PhysicalBoardSlot slot;
        private final String componentId;
        private final WorkbenchOperation operation;
        private final CircuitSolverExecutor solverExecutor;
        private final double maximumTimeStep;
        private final double minimumTimeStep;
        private final boolean adaptiveTimeStep;
        private final Checkpoint checkpoint;

        OwnerHost(final CirSim sim, final GeneratedBoardInstance owner,
                final GeneratedChallengeController challenge, String componentId,
                PhysicalPart<?> target, Checkpoint checkpoint) {
            if (sim == null || owner == null || challenge == null ||
                    componentId == null || componentId.length() == 0 || target == null ||
                    checkpoint == null || sim.pcbWorkbenchController == null ||
                    sim.boardModificationController == null || sim.elmList == null)
                throw new IllegalArgumentException("Missing current physical service context");
            this.sim = sim;
            this.owner = owner;
            this.challenge = challenge;
            this.componentId = componentId;
            this.target = target;
            this.checkpoint = checkpoint;
            graph = sim.elmList;
            graphContents = new Vector<CircuitElm>(graph);
            workbench = sim.pcbWorkbenchController;
            modifications = sim.boardModificationController;
            runtime = owner.getPhysicalBoardRuntime();
            mutationProvider = runtime == null ? null : runtime.getMutationProvider(componentId);
            mutationReceipt = runtime == null ? null : runtime.getLastMutationReceipt();
            physicalAdmission = owner.getPhysicalAdmission();
            bindings = owner.getExternalPowerBindings();
            controls = bindings == null ? null : bindings.observeControls();
            Vector<String> inputIds = owner.getBoard().getPowerInputIds();
            sourceIds = new String[inputIds.size()];
            sources = new LimitedDcSupplyElm[inputIds.size()];
            sourceVoltages = new double[inputIds.size()];
            sourceLimits = new double[inputIds.size()];
            for (int i = 0; i < sourceIds.length; i++) {
                sourceIds[i] = inputIds.get(i);
                ExternalPowerSimulationBinding binding = bindings == null ? null :
                    bindings.getBinding(sourceIds[i]);
                sources[i] = binding == null ? null : binding.getLimitedSupply();
                if (sources[i] != null) {
                    sourceVoltages[i] = sources[i].maxVoltage;
                    sourceLimits[i] = sources[i].getLimitAmps();
                }
            }
            powerController = sim.boardPowerController;
            solverExecutor = sim.solverExecutor;
            maximumTimeStep = sim.maxTimeStep;
            minimumTimeStep = sim.minTimeStep;
            adaptiveTimeStep = sim.adjustTimeStep;
            slot = target.getBoardSlot();
            operation = WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, target);
            checkCurrent();
        }

        public void checkCurrent() {
            checkpoint.check();
            require(sim.getGeneratedBoardInstance() == owner && sim.elmList == graph &&
                    sameElements(graph, graphContents) &&
                    sim.getGeneratedChallengeController() == challenge &&
                    sim.pcbWorkbenchController == workbench &&
                    sim.boardModificationController == modifications &&
                    owner.getPhysicalBoardRuntime() == runtime && runtime != null &&
                    runtime.getMutationProvider(componentId) == mutationProvider &&
                    runtime.getLastMutationReceipt() == mutationReceipt &&
                    owner.getPhysicalAdmission() == physicalAdmission &&
                    owner.getExternalPowerBindings() == bindings && bindings != null &&
                    controls != null && controls.isCurrent() && sourceCommandsCurrent() &&
                    sim.boardPowerController == powerController &&
                    powerController.getBindingsForDeveloperVerification() == bindings &&
                    powerController.getState() == BoardPowerState.UNPOWERED &&
                    powerController.isElectricallyUnpowered() && bindings.areAllDisconnected() &&
                    runtime.getInstalledPart(componentId) == target && target.isInstalled() &&
                    target.getBoardSlot() == slot && slot != null &&
                    slot.getInstalledPart() == target &&
                    sim.solverExecutor == solverExecutor && !solverExecutor.isUnavailable() &&
                    sim.maxTimeStep == maximumTimeStep && sim.minTimeStep == minimumTimeStep &&
                    sim.adjustTimeStep == adaptiveTimeStep,
                "Diagnostic service preparation lost its exact owner, controls, part, or solver recipe");
        }

        public boolean isAvailable() {
            return workbench.isAvailable(operation);
        }

        private boolean sourceCommandsCurrent() {
            for (int i = 0; i < sourceIds.length; i++) {
                ExternalPowerSimulationBinding binding = bindings.getBinding(sourceIds[i]);
                if (binding == null || binding.getLimitedSupply() != sources[i]) return false;
                if (sources[i] != null && (sources[i].maxVoltage != sourceVoltages[i] ||
                        sources[i].getLimitAmps() != sourceLimits[i])) return false;
            }
            return true;
        }

        public void advance(double seconds) {
            sim.advanceGeneratedTemporalProfile(seconds);
        }

        private static boolean sameElements(Vector<CircuitElm> current,
                Vector<CircuitElm> captured) {
            if (current.size() != captured.size()) return false;
            for (int i = 0; i < current.size(); i++)
                if (current.get(i) != captured.get(i)) return false;
            return true;
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
