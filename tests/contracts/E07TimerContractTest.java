package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * E07 timer family on native CircuitJS: period and pulse width come from the
 * solved 555 OUT pin, the trigger is a real contact closure, faults give their
 * symptoms, and edges faster than the sample slice are not reported as timing.
 * Independent oracles are the textbook 555 equations, never product code.
 */
public final class E07TimerContractTest {
    private static int assertions;
    private static final double C1 = 33e-6;

    private E07TimerContractTest() { }

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim;
        try {
            verifyPhysicalAdmission();
            verifyDependencyCapture(0L);
            verifyDependencyCapture(1L);
            Fixture flasher = install(0L, null);
            try { verifyFreeRunning(flasher); } finally { detach(flasher, prior); }
            Fixture pulse = install(1L, null);
            try { verifyTriggered(pulse); } finally { detach(pulse, prior); }
            verifyFaultSymptoms(0L);
            verifyFaultSymptoms(1L);
        } finally { CircuitElm.sim = prior; }
        System.out.println("PASS: E07 timer contracts assertions=" + assertions);
    }

    /**
     * Generation publishes a dependency fingerprint of the board. The 555
     * latch (live OUT voltage) is restart state and must not change it.
     */
    private static void verifyDependencyCapture(long seed) {
        CirSim prior = CircuitElm.sim;
        Fixture f = install(seed, null);
        try {
            run(f, GeneratedTemporalBehavior.Profile.HEALTHY); // production captures after HEALTHY
            String before = GenerationDependencyContext.capture(f.sim, f.owner,
                "e07-request", "e07-realization").canonical();
            f.sim.advanceGeneratedTemporalProfile(.6);
            String after = GenerationDependencyContext.capture(f.sim, f.owner,
                "e07-request", "e07-realization").canonical();
            check(before.equals(after),
                "timer dependency capture is stable across the live 555 latch, seed " + seed);
        } finally { detach(f, prior); }
    }

    private static void verifyPhysicalAdmission() {
        configure(new Q30ServiceFlowContractTest.NativeServiceCirSim());
        for (long seed = 0; seed < 4; seed++) {
            PlayerLaunchRequest exact = PlayerLaunchRequest.parse(
                "tsj-alpha/7/EASY/" + Timer555Generator.FAMILY_ID + "/" + seed);
            GeneratedBoardInstance board = exact.generation()
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            board.requireNormalPhysicalAdmission();
            board.getPcbLayout().validateGeometry(board.getBoard());
            check(board.getSeed() == seed && Timer555Generator.FAMILY_ID.equals(board.getCircuitFamilyId()),
                "exact replay keeps the timer family and seed " + seed);
            check(board.getTopologyVariantId().equals(seed % 2 == 0 ?
                Timer555Generator.FREE_RUNNING_VARIANT : Timer555Generator.TRIGGERED_VARIANT),
                "seed parity selects the timer variant");
            check(board.getPcbLayout().getPad("U1.OUT") != null &&
                board.getPcbLayout().getPad("U1.THRES") != null, "timer pins are routed pads");
        }
    }

    private static void verifyFreeRunning(Fixture f) {
        Timer555TemporalBehavior timer = f.timer;
        check(timer.getMode() == Timer555TemporalBehavior.Mode.FREE_RUNNING, "seed 0 is free-running");
        check(f.owner.getOperationCatalog().find(GeneratedBoardOperationIds.START_BUTTON_PRESS) == null,
            "free-running board exposes no START operation");
        run(f, GeneratedTemporalBehavior.Profile.HEALTHY);
        double expected = .693 * (4700 + 2 * 8200) * C1;
        double healthy = timer.getHealthyTimingSecondsForDeveloperVerification();
        check(timer.getObservedBehavior() == GeneratedObservedBehavior.TIMER_HEALTHY &&
            Math.abs(healthy / expected - 1) < .10,
            "healthy flash period " + healthy + " s matches 0.693(RA+2RB)C = " + expected);

        // Same graph, same recipe: the solver-time measurement is repeatable.
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "unchanged healthy graph passes its own repair profile");
        double again = timer.getMeasuredTimingSecondsForDeveloperVerification();
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) == GeneratedRepairStatus.CORRECTLY_RESTORED &&
            Math.abs(again - timer.getMeasuredTimingSecondsForDeveloperVerification()) <=
                2 * Timer555TemporalBehavior.SAMPLE_SLICE_SECONDS,
            "measured period repeats within two solver-time sample slices: " + again + " vs " +
                timer.getMeasuredTimingSecondsForDeveloperVerification());

        // Negative: the configured value is not the measurement. Change only the
        // solver element; the RB nameplate still says 8.2 k.
        ResistorElm rb = (ResistorElm) f.owner.getComponentBindings().getSingleElement("RB");
        double nameplate = ((ResistorNameplate) f.owner.getPhysicalSpecifications()
            .getSpecification("RB")).getNominalResistanceOhms();
        rb.setResistance(33000);
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) ==
                GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
            timer.getObservedBehavior() == GeneratedObservedBehavior.TIMER_TOO_SLOW && nameplate == 8200,
            "a slower solved circuit is too slow even though the configured value is unchanged");
        rb.setResistance(8200);

        // Negative: edges faster than the sample slice are unsupported, never a frequency.
        CapacitorElm c1 = (CapacitorElm) f.owner.getComponentBindings().getSingleElement("C1");
        c1.setCapacitance(1e-6);
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) ==
                GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
            timer.getObservedBehavior() == GeneratedObservedBehavior.TIMER_TOO_FAST &&
            Double.isNaN(timer.getMeasuredTimingSecondsForDeveloperVerification()),
            "a ~70 Hz flasher is reported unresolved, with no measured period");
        c1.setCapacitance(C1);
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) == GeneratedRepairStatus.CORRECTLY_RESTORED &&
            timer.getHealthyTimingSecondsForDeveloperVerification() == healthy,
            "restored graph passes and the healthy reference survives later profiles");
    }

    private static void verifyTriggered(Fixture f) {
        Timer555TemporalBehavior timer = f.timer;
        check(timer.getMode() == Timer555TemporalBehavior.Mode.TRIGGERED, "seed 1 is triggered");
        check(f.owner.getOperationCatalog().find(GeneratedBoardOperationIds.START_BUTTON_PRESS) != null,
            "triggered board exposes the START operation");
        run(f, GeneratedTemporalBehavior.Profile.HEALTHY);
        double expected = 1.1 * 15000 * C1;
        double healthy = timer.getHealthyTimingSecondsForDeveloperVerification();
        check(timer.getObservedBehavior() == GeneratedObservedBehavior.TIMER_HEALTHY &&
            Math.abs(healthy / expected - 1) < .10,
            "healthy pulse " + healthy + " s matches 1.1 RA C = " + expected);
        check(timer.isStartButtonReleased(), "profile leaves START released");

        // No hidden trigger: power-up alone never starts a pulse.
        cyclePower(f);
        f.sim.advanceGeneratedTemporalProfile(.75);
        check(out(f) < 1, "OUT stays low after power-up without a START press");

        // The player operation is a real contact closure that starts the pulse.
        f.owner.invokeOperation(GeneratedBoardOperationIds.START_BUTTON_PRESS, f.sim);
        check(timer.isStartButtonReleased() && out(f) > supply(f) * .8,
            "START press drives OUT high and releases the contact");
        f.sim.advanceGeneratedTemporalProfile(.3);
        check(out(f) > supply(f) * .8, "pulse persists after release");
        f.sim.advanceGeneratedTemporalProfile(.5);
        check(out(f) < 1, "pulse ends by itself");

        // Removing power mid-pulse really ends it; repower does not resume it.
        f.owner.invokeOperation(GeneratedBoardOperationIds.START_BUTTON_PRESS, f.sim);
        f.sim.advanceGeneratedTemporalProfile(.1);
        cyclePower(f);
        f.sim.advanceGeneratedTemporalProfile(.5);
        check(out(f) < 1, "power cycle ends the pulse rather than restoring it");

        ResistorElm ra = (ResistorElm) f.owner.getComponentBindings().getSingleElement("RA");
        ra.setResistance(47000);
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) ==
                GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
            timer.getObservedBehavior() == GeneratedObservedBehavior.TIMER_TOO_SLOW &&
            timer.getMeasuredTimingSecondsForDeveloperVerification() > healthy * 2,
            "a longer solved RC gives a measured long pulse");
        ra.setResistance(15000);
        check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "restored pulse passes");
    }

    private static void verifyFaultSymptoms(long seed) {
        GeneratedFaultType[] types = { GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
            GeneratedFaultType.CAPACITOR_SHORT, GeneratedFaultType.RESISTOR_OPEN };
        GeneratedObservedBehavior[] symptoms = { GeneratedObservedBehavior.TIMER_TOO_SLOW,
            GeneratedObservedBehavior.TIMER_STUCK_ON, GeneratedObservedBehavior.TIMER_STUCK_ON };
        for (int i = 0; i < types.length; i++) {
            Fixture f = install(seed, types[i]);
            try {
                run(f, GeneratedTemporalBehavior.Profile.HEALTHY);
                f.owner.getFaultBinding().setApplied(true);
                run(f, GeneratedTemporalBehavior.Profile.FAULTED);
                check(f.timer.getObservedBehavior() == symptoms[i],
                    "seed " + seed + " " + types[i] + " shows " + symptoms[i] + ", saw " +
                        f.timer.getObservedBehavior());
                check(f.owner.getChallengeDefinition().getScenarioCatalog() != null,
                    "fault has a customer scenario catalog");
                f.owner.getFaultBinding().setApplied(false);
                check(run(f, GeneratedTemporalBehavior.Profile.REPAIR) == GeneratedRepairStatus.CORRECTLY_RESTORED,
                    "clearing the fault restores normal timing");
            } finally { detach(f, null); }
        }
    }

    private static GeneratedRepairStatus run(Fixture f, GeneratedTemporalBehavior.Profile profile) {
        if (f.sim.getBoardPowerController().getState() != BoardPowerState.POWERED)
            setPower(f, BoardPowerState.POWERED);
        GeneratedWork<GeneratedRepairStatus> work = f.timer.beginProfile(f.sim, f.owner, profile);
        check(work.getWorkUnits() == 4, "timer profile declares four bounded work units");
        return GeneratedWork.complete(work);
    }

    private static void cyclePower(Fixture f) {
        setPower(f, BoardPowerState.UNPOWERED);
        f.sim.advanceGeneratedTemporalProfile(.75);
        check(out(f) < .1, "OUT is low while unpowered");
        setPower(f, BoardPowerState.POWERED);
    }

    private static void setPower(Fixture f, BoardPowerState state) {
        f.sim.setBoardPowerStateForGeneratedTemporalProfile(state);
    }

    private static double out(Fixture f) {
        CircuitMeasurementEndpoint o = f.owner.getSimulationBindings().getEndpoint("U1.OUT");
        CircuitMeasurementEndpoint g = f.owner.getSimulationBindings().getEndpoint("J1.2");
        CircuitPostMeasurementEndpoint op = (CircuitPostMeasurementEndpoint) o;
        CircuitPostMeasurementEndpoint gp = (CircuitPostMeasurementEndpoint) g;
        return op.getElement().getPostVoltage(op.getPostIndex()) -
            gp.getElement().getPostVoltage(gp.getPostIndex());
    }

    private static double supply(Fixture f) {
        return f.owner.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT").getNominalVoltage();
    }

    private static CirSim configure(CirSim sim) {
        Q30ServiceFlowContractTest.configureSimulator(sim);
        CircuitElm.sim = sim;
        return sim;
    }

    /** The simulator is installed first: CircuitJS models capture it when constructed. */
    private static Fixture install(long seed, GeneratedFaultType type) {
        Q30ServiceFlowContractTest.NativeServiceCirSim sim =
            new Q30ServiceFlowContractTest.NativeServiceCirSim();
        configure(sim);
        GeneratedBoardInstance owner = type == null ? new Timer555Generator().generate(seed) :
            new Timer555Generator().generateForFaultVerification(seed, type);
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        BoardModificationController modifications = new BoardModificationController(sim, owner);
        sim.boardModificationController = modifications;
        owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner, modifications, 0);
        sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
        owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(BoardPowerState.POWERED);
        final GeneratedScenario<GeneratedObservedBehavior> scenario = owner.getChallengeDefinition()
            .getScenarioCatalog().getCandidates().firstElement();
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
            @Override boolean allowsWorkbenchInteraction() { return true; }
            @Override GeneratedScenario<GeneratedObservedBehavior> getScenario() { return scenario; }
        };
        owner.getFaultBinding().setApplied(false);
        sim.analyzeCircuit();
        sim.solverExecutor.advanceSteps(8);
        return new Fixture(sim, owner, (Timer555TemporalBehavior) owner.getTemporalBehavior());
    }

    private static void detach(Fixture f, CirSim prior) {
        f.owner.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : f.owner.getSimulationElements()) {
            f.sim.elmList.remove(element);
            element.delete();
        }
        check(f.sim.elmList.isEmpty(), "timer fixture detaches every solver element");
        if (prior != null) CircuitElm.sim = prior;
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static final class Fixture {
        final CirSim sim;
        final GeneratedBoardInstance owner;
        final Timer555TemporalBehavior timer;
        Fixture(CirSim sim, GeneratedBoardInstance owner, Timer555TemporalBehavior timer) {
            this.sim = sim; this.owner = owner; this.timer = timer;
        }
    }
}
