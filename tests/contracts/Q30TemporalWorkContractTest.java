package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native CircuitJS contract for bounded Q30 temporal profiles and retest. */
public final class Q30TemporalWorkContractTest {
    private static int assertions;

    private Q30TemporalWorkContractTest() { }

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim;
        Fixture fixture = install(13L); // Accepted epoch3 filtered topology.
        try {
            verifyHealthyProfile(fixture);
            verifyFaultedProfile(fixture);
            verifyResumableRetest(fixture);
            verifyCancellationRestoresOnlyForCurrentOwner(fixture);
            verifyChangedInputIsNotOverwritten(fixture);
            verifyPowerOffIsNotUndone(fixture);
            verifyNewerOffStateControlRejectsRollback(fixture);
            verifySuccessorOwnerIsUntouched(fixture);
        } finally {
            try {
                fixture.owner.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : fixture.owner.getSimulationElements()) {
                    fixture.sim.elmList.remove(element);
                    element.delete();
                }
                check(fixture.sim.elmList.isEmpty(), "temporal fixture detaches every private solver element");
            } finally { CircuitElm.sim = prior; }
        }
        System.out.println("PASS: Q30 temporal work contracts " + assertions +
            " assertions profileUnits=5 retestUnits=5");
    }

    private static void verifyHealthyProfile(Fixture f) {
        Rb30Behavior behavior = f.behavior;
        GeneratedWork<GeneratedRepairStatus> work = behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.HEALTHY);
        check(work.getWorkUnits() == 5, "healthy profile declares five bounded units");
        checkSteps(work, f.sim, new int[] { 1, 1, 1, 1, 1 }, new boolean[] {
            true, true, true, true, false
        });
        check(work.finish() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "healthy profile finishes after four real conditions and LOW");
        check(behavior.getInputs() == 0 && behavior.healthy(f.owner, 0) &&
                Math.abs(Rb30Behavior.voltage(f.owner, "JOA.1", "JOA.2")) <= .05 &&
                Math.abs(Rb30Behavior.voltage(f.owner, "JOB.1", "JOB.2")) <= .05,
            "healthy profile leaves real outputs LOW at the final input state");
        check(f.sim.maxTimeStep == Rb30Behavior.SOLVER_MAX_STEP_SECONDS &&
                f.sim.minTimeStep == Rb30Behavior.SOLVER_MIN_STEP_SECONDS && f.sim.adjustTimeStep,
            "healthy profile applies the declared adaptive 5us/50ps CircuitJS recipe");
    }

    private static void verifyFaultedProfile(Fixture f) {
        f.owner.getFaultBinding().setApplied(true);
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.FAULTED);
        check(work.getWorkUnits() == 5, "faulted profile retains the fixed five-unit declaration");
        checkSteps(work, f.sim, new int[] { 1, 0, 0, 0, 0 }, new boolean[] {
            true, true, true, true, false
        });
        check(work.finish() == GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
                f.behavior.getInputs() == 3 && f.behavior.getObservedBehavior() ==
                    GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING && !f.behavior.healthy(f.owner, 3),
            "faulted profile records the real HIGH symptom after one solver interval");
        f.owner.getFaultBinding().setApplied(false);
        settle(f.sim);
    }

    private static void verifyResumableRetest(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 1);
        GeneratedBoardOperation operation = f.owner.getOperationCatalog().find(
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        check(operation != null && operation.getWorkUnits(f.owner) == 5,
            "customer retest operation declares five bounded units");
        GeneratedWork<GeneratedCustomerRetestResult> work = operation.begin(f.sim, f.owner);
        check(work.getWorkUnits() == 5, "customer retest work has the declared unit count");
        checkSteps(work, f.sim, new int[] { 1, 1, 1, 1, 1 }, new boolean[] {
            true, true, true, true, false
        });
        GeneratedCustomerRetestResult result = work.finish();
        check(result != null && result.isPassed(), "resumable customer retest passes the real circuit");
        check(f.behavior.getInputs() == 1 && f.behavior.healthy(f.owner, 1) &&
                Rb30Behavior.voltage(f.owner, "JOA.1", "JOA.2") >= 10.8 &&
                Math.abs(Rb30Behavior.voltage(f.owner, "JOB.1", "JOB.2")) <= .05,
            "customer retest restores its prior inputs and leaves matching real outputs");
    }

    private static void verifyCancellationRestoresOnlyForCurrentOwner(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 3);
        BoardPowerState powerBefore = f.sim.getBoardPowerController().getState();
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.REPAIR);
        int before = f.sim.temporalAdvanceCount;
        check(work.step(), "repair cursor yields after its first condition");
        check(f.sim.temporalAdvanceCount == before + 1 && f.behavior.getInputs() == 0,
            "one repair work step performs only its own 30ms input interval");
        before = f.sim.temporalAdvanceCount;
        work.cancel();
        check(f.sim.temporalAdvanceCount == before + 1 && f.behavior.getInputs() == 3 &&
                f.sim.getBoardPowerController().getState() == powerBefore,
            "authorized cancellation restores prior inputs without changing board power");
    }

    private static void verifyChangedInputIsNotOverwritten(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 3);
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.REPAIR);
        check(work.step(), "input-change cursor begins");
        f.behavior.setInputs(f.sim, f.owner, 1);
        int before = f.sim.temporalAdvanceCount;
        boolean rejected = false;
        try { work.step(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "profile detects an input-source command changed between steps");
        work.cancel();
        check(f.sim.temporalAdvanceCount == before && f.behavior.getInputs() == 1 &&
                f.sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "stale cursor cancellation preserves the newer input command and power state");
    }

    private static void verifyPowerOffIsNotUndone(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 3);
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.REPAIR);
        check(work.step(), "power-change cursor begins");
        setPower(f, BoardPowerState.UNPOWERED);
        int before = f.sim.temporalAdvanceCount;
        boolean rejected = false;
        try { work.step(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "profile detects board power and external control changes");
        work.cancel();
        check(f.sim.temporalAdvanceCount == before &&
                f.behavior.getInputs() == 3 &&
                sensorMaximum(f.owner, "SENSOR_A") == 5 &&
                sensorMaximum(f.owner, "SENSOR_B") == 5 &&
                f.sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED,
            "off-state cancel restores captured sensor commands without advancing or repowering");
        setPower(f, BoardPowerState.POWERED);
        settle(f.sim);
    }

    private static void verifySuccessorOwnerIsUntouched(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 3);
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.REPAIR);
        check(work.step(), "owner-change cursor begins");
        int before = f.sim.temporalAdvanceCount;
        f.sim.generatedBoardInstance = null;
        boolean rejected = false;
        try { work.step(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "profile rejects work after its live board owner changes");
        work.cancel();
        check(f.sim.temporalAdvanceCount == before && f.sim.generatedBoardInstance == null &&
                f.sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "cancel cannot write inputs, advance, or repower through a successor owner");
        f.sim.generatedBoardInstance = f.owner;
    }

    private static void verifyNewerOffStateControlRejectsRollback(Fixture f) {
        f.behavior.setInputs(f.sim, f.owner, 3);
        GeneratedWork<GeneratedRepairStatus> work = f.behavior.beginProfile(f.sim, f.owner,
            GeneratedTemporalBehavior.Profile.REPAIR);
        check(work.step(), "external-control-change cursor begins");
        setPower(f, BoardPowerState.UNPOWERED);
        Vector<String> inputs = f.owner.getBoard().getPowerInputIds();
        ExternalPowerSimulationBinding binding = f.owner.getExternalPowerBindings()
            .getBinding(inputs.get(0));
        LimitedDcSupplyElm supply = binding.getLimitedSupply();
        check(supply != null, "native fixture exposes a real controlled supply for revision check");
        binding.setCurrentLimit(supply.getLimitAmps());
        int before = f.sim.temporalAdvanceCount;
        boolean rejected = false;
        try { work.step(); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "profile rejects an external control revision changed after power-off");
        work.cancel();
        check(f.sim.temporalAdvanceCount == before && f.behavior.getInputs() == 0 &&
                f.sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED,
            "off-state rollback refuses to overwrite newer controls or sensor commands");
        setPower(f, BoardPowerState.POWERED);
        settle(f.sim);
    }

    private static void checkSteps(GeneratedWork<?> work, WorkCirSim sim,
            int[] expectedAdvances, boolean[] expectedMore) {
        for (int unit = 0; unit < expectedAdvances.length; unit++) {
            int before = sim.temporalAdvanceCount;
            boolean more = work.step();
            int advances = sim.temporalAdvanceCount - before;
            check(advances <= 1, "work unit " + unit + " has at most one solver advance");
            check(advances == expectedAdvances[unit], "work unit " + unit +
                " uses the expected solver interval count");
            if (advances != 0)
                check(sim.lastTemporalAdvanceSeconds == Rb30Behavior.SAMPLE_SECONDS,
                    "work unit " + unit + " advances the unchanged 30ms sample");
            check(more == expectedMore[unit], "work unit " + unit + " returns the exact cursor boundary");
        }
    }

    private static Fixture install(long seed) {
        WorkCirSim sim = new WorkCirSim();
        Q30ServiceFlowContractTest.configureSimulator(sim);
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = new Rb30Generator().generate(seed);
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        BoardModificationController modifications = new BoardModificationController(sim, owner);
        sim.boardModificationController = modifications;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, owner, modifications, 0);
        sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
            @Override boolean allowsWorkbenchInteraction() { return true; }
        };
        owner.getFaultBinding().setApplied(false);
        settle(sim);
        return new Fixture(sim, owner, (Rb30Behavior) owner.getFamilyState());
    }

    private static void setPower(Fixture f, BoardPowerState state) {
        if (f.sim.getBoardPowerController().getState() != state) {
            f.sim.getBoardPowerController().setState(state);
            f.owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(state);
        }
    }

    private static void settle(WorkCirSim sim) {
        sim.analyzeCircuit();
        sim.solverExecutor.advanceSteps(8);
    }

    private static double sensorMaximum(GeneratedBoardInstance owner, String id) {
        return owner.getExternalPowerBindings().getBinding(id).getLimitedSupply().maxVoltage;
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class Fixture {
        final WorkCirSim sim;
        final GeneratedBoardInstance owner;
        final Rb30Behavior behavior;
        Fixture(WorkCirSim sim, GeneratedBoardInstance owner, Rb30Behavior behavior) {
            this.sim = sim;
            this.owner = owner;
            this.behavior = behavior;
        }
    }

    private static final class WorkCirSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        int temporalAdvanceCount;
        double lastTemporalAdvanceSeconds;
        @Override void advanceGeneratedTemporalProfile(double seconds) {
            temporalAdvanceCount++;
            lastTemporalAdvanceSeconds = seconds;
            super.advanceGeneratedTemporalProfile(seconds);
        }
    }
}
