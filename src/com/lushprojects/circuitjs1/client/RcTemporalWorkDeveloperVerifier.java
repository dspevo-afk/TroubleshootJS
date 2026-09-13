package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent pre-split sequence oracle and real-graph cancellation canaries. */
final class RcTemporalWorkDeveloperVerifier {
    private static int assertions;
    private RcTemporalWorkDeveloperVerifier() { }

    static String verify(CirSim sim) {
        assertions = 0;
        for (long seed : new long[] {0, 3}) {
            String reference = profile(sim, seed, false);
            String resumed = profile(sim, seed, true);
            require(reference.equals(resumed), "phase samples, accepted state, events and counters agree: " + seed);
        }
        for (int boundary = 0; boundary < 4; boundary++) {
            cancel(sim, boundary, 0);
            cancel(sim, boundary, 1);
            cancel(sim, boundary, 2);
        }
        for (int boundary = 0; boundary <= 8; boundary++) stagedAbort(sim, boundary);
        repairPowerGuard(sim);
        return "{\"assertions\":" + assertions + ",\"comparedProfiles\":2,\"phases\":4," +
            "\"cancelBoundaries\":4,\"staleGraphBoundaries\":4,\"sourceChangeBoundaries\":4," +
            "\"exactStateSamplesEventsCounters\":true,\"partialPublicationRejected\":true," +
            "\"repairPowerGuard\":true,\"stagedAbortBoundaries\":9,\"abortedSourcesDisconnected\":true}";
    }

    /** Exercise the production owner which cancels work and then deletes a candidate. */
    private static void stagedAbort(CirSim sim, int boundary) {
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        Vector<CircuitElm> originalGraph = sim.elmList;
        GeneratedChallengeController originalController = sim.getGeneratedChallengeController();
        GeneratedExternalPowerBindings.SavedControls originalControls = original.getExternalPowerBindings().saveControls();
        GeneratedBoardInstance candidate = new RcDelayGenerator().generate(3);
        FreshGeneratedRuntimeInstallation.Staged staged = new FreshGeneratedRuntimeInstallation.Staged(sim, candidate);
        try {
            if (boundary > 0) {
                staged.prepare(false);
                for (int phase = 1; phase < boundary; phase++) staged.finishPreparation();
                require(sim.getGeneratedChallengeController().getTemporalPreparationUnits() == boundary,
                    "staged abort reaches exact healthy/faulted phase " + boundary);
            }
            staged.abort(); staged.abort();
            require(candidate.getExternalPowerBindings().areAllDisconnected(),
                "aborted candidate stays isolated after owned power restoration " + boundary);
            require(sim.getGeneratedBoardInstance() == original && sim.elmList == originalGraph &&
                sim.getGeneratedChallengeController() == originalController && originalControls.matches(),
                "staged abort restores the exact protected owner " + boundary);
            require(!FreshGeneratedRuntimeInstallation.isInProgress(sim) && !sim.activeMeasurementOverlay,
                "staged abort releases its installation and overlay " + boundary);
            for (CircuitElm element : candidate.getSimulationElements())
                require(!sim.elmList.contains(element), "aborted elements are detached from the live graph");
        } finally { staged.abort(); }
    }

    private static void repairPowerGuard(CirSim sim) {
        Fixture fixture = new Fixture(sim, 3);
        try {
            sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
            double before = sim.t;
            GeneratedRepairStatus status = GeneratedWork.complete(fixture.owner.getTemporalBehavior()
                .beginProfile(sim, fixture.owner, GeneratedTemporalBehavior.Profile.REPAIR));
            require(status == GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL &&
                bits(sim.t) == bits(before) && fixture.owner.getExternalPowerBindings().areAllDisconnected() &&
                sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED,
                "repair status preserves the real OFF command without starting a profile");
        } finally { fixture.close(); }
    }

    private static String profile(final CirSim sim, long seed, boolean resumed) {
        Fixture fixture = new Fixture(sim, seed);
        GeneratedWork<GeneratedRepairStatus> work = null;
        try {
            final GeneratedBoardInstance owner = fixture.owner;
            final RcDelayTemporalBehavior temporal = (RcDelayTemporalBehavior) owner.getTemporalBehavior();
            final StringBuilder out = new StringBuilder();
            for (double offset : new double[] {.01, .751, 1.05, 1.7}) {
                sim.solverExecutor.events(null).schedule(sim.t + offset, new SolverEventQueue.Action() {
                    public void fire(double due, double accepted) {
                        out.append('E'); append(out, due); append(out, accepted); append(out, voltage(owner));
                    }
                });
            }
            if (resumed) {
                work = temporal.beginProfile(sim, owner, GeneratedTemporalBehavior.Profile.HEALTHY);
                require(work.getWorkUnits() == 4, "RC declares its four existing solver calls");
            }
            double residual = 0, early = 0, late = 0;
            for (int phase = 0; phase < 4; phase++) {
                if (resumed) {
                    boolean more = work.step();
                    require(more == (phase < 3), "exact phase boundary " + phase);
                    require(temporal.getObservedBehavior() == null, "profile cannot publish before finish");
                    if (phase < 3) requireIncomplete(work);
                } else {
                    // Literal calls from the original samplePowerCycle sequence.
                    // This oracle deliberately does not use the rewritten sync wrapper.
                    if (phase == 0) {
                        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
                        sim.advanceGeneratedTemporalProfile(.750);
                    } else if (phase == 1) sim.advanceGeneratedTemporalProfile(.250);
                    else if (phase == 2) {
                        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
                        sim.advanceGeneratedTemporalProfile(.100);
                    } else sim.advanceGeneratedTemporalProfile(.700);
                }
                if (phase == 1) residual = Math.abs(voltage(owner));
                if (phase == 2) early = voltage(owner);
                if (phase == 3) late = voltage(owner);
                capture(out, sim, owner);
                require(!sim.activeMeasurementOverlay && !sim.solverExecutor.isUnavailable(),
                    "phase returned the public solver without an overlay");
            }
            if (resumed) {
                require(work.finish() == GeneratedRepairStatus.CORRECTLY_RESTORED, "healthy result");
                require(bits(temporal.getHealthyResidualVoltageForDeveloperVerification()) == bits(residual) &&
                    bits(temporal.getHealthyEarlyVoltageForDeveloperVerification()) == bits(early) &&
                    bits(temporal.getHealthyLateVoltageForDeveloperVerification()) == bits(late),
                    "published samples are the actual phase endpoint readings");
                String before = out.toString();
                require(!work.step() && work.finish() == GeneratedRepairStatus.CORRECTLY_RESTORED,
                    "completed work is idempotent");
                StringBuilder after = new StringBuilder(); capture(after, sim, owner);
                require(before.endsWith(after.toString()), "completed work performs no further solver call");
            }
            append(out, residual); append(out, early); append(out, late);
            return out.toString();
        } finally {
            try { if (work != null) work.cancel(); }
            finally { fixture.close(); }
        }
    }

    /** kind: ordinary cancel, same-owner graph replacement, changed source command. */
    private static void cancel(CirSim sim, int boundary, int kind) {
        Fixture fixture = new Fixture(sim, 3);
        GeneratedWork<GeneratedRepairStatus> work = null;
        Vector<CircuitElm> graph = sim.elmList;
        try {
            RcDelayTemporalBehavior temporal = (RcDelayTemporalBehavior)fixture.owner.getTemporalBehavior();
            work = temporal.beginProfile(sim, fixture.owner, GeneratedTemporalBehavior.Profile.HEALTHY);
            for (int phase = 0; phase < boundary; phase++) require(work.step(), "partial phase remains open");
            requireIncomplete(work);
            require(temporal.getObservedBehavior() == null, "cancelled profile has no published observation");
            boolean dependencyRejected = false;
            try { temporal.getDependency(fixture.owner); }
            catch (IllegalStateException expected) { dependencyRejected = true; }
            require(dependencyRejected, "partial healthy reference cannot produce dependency evidence");
            if (kind == 1) sim.elmList = new Vector<CircuitElm>(graph);
            if (kind == 2) {
                // Also invalidate phases which are already OFF: OFF -> ON -> OFF
                // changes the connection revision while preserving the final command.
                sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
                sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
            }
            double beforeTime = sim.t;
            GeneratedExternalPowerBindings.SavedControls changed = fixture.owner.getExternalPowerBindings().saveControls();
            if (kind != 0) {
                boolean rejected = false;
                try { work.step(); } catch (IllegalStateException expected) { rejected = true; }
                require(rejected, "stale work rejects before another solver phase");
            }
            work.cancel(); work.cancel();
            require(bits(sim.t) == bits(beforeTime), "cancel never advances the solver");
            requireIncomplete(work);
            if (kind == 0) require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                fixture.owner.getExternalPowerBindings().areAllConnected(), "owned cancel restores prior power");
            else require(changed.matches(), "stale cancel preserves the successor source commands");
            require(temporal.getObservedBehavior() == null &&
                sim.getGeneratedChallengeController().getDiagnosticProofReceipt() == null,
                "cancel cannot publish a partial profile or admission receipt");
        } finally {
            try { if (work != null) work.cancel(); }
            finally { sim.elmList = graph; fixture.close(); }
        }
    }

    private static void requireIncomplete(GeneratedWork<?> work) {
        boolean rejected = false;
        try { work.finish(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "incomplete work cannot finish");
    }

    private static double voltage(GeneratedBoardInstance owner) {
        CircuitPostMeasurementEndpoint positive = (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint("J2.1");
        CircuitPostMeasurementEndpoint negative = (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint("J2.2");
        return positive.getElement().getPostVoltage(positive.getPostIndex()) -
            negative.getElement().getPostVoltage(negative.getPostIndex());
    }

    private static void capture(StringBuilder out, CirSim sim, GeneratedBoardInstance owner) {
        out.append('P').append(sim.getBoardPowerController().getState()).append(':');
        append(out, sim.t); append(out, sim.timeStep); append(out, sim.timeStepAccum);
        out.append(sim.timeStepCount).append(':');
        for (CircuitElm element : owner.getSimulationElements()) {
            append(out, element.getCurrent());
            for (int post = 0; post < element.getPostCount(); post++) append(out, element.getPostVoltage(post));
        }
        for (long counter : counters(sim)) out.append(counter).append(':');
    }
    private static long bits(double value) { return Double.doubleToLongBits(value); }
    private static void append(StringBuilder out, double value) { out.append(bits(value)).append(':'); }
    private static long[] counters(CirSim sim) {
        return new long[] {sim.a01AnalysisCount, sim.a01StampCount, sim.a01FactorizationCount,
            sim.a01SolveCount, sim.a01IterationCount, sim.a01SubIterationCount, sim.a01AcceptedStepCount, sim.steps};
    }
    private static void restoreCounters(CirSim sim, long[] values) {
        sim.a01AnalysisCount=values[0]; sim.a01StampCount=values[1]; sim.a01FactorizationCount=values[2];
        sim.a01SolveCount=values[3]; sim.a01IterationCount=values[4]; sim.a01SubIterationCount=values[5];
        sim.a01AcceptedStepCount=values[6]; sim.steps=(int)values[7];
    }
    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new IllegalStateException("RC temporal work: " + message);
    }

    private static final class Fixture {
        final CirSim sim;
        final Task41SimulationSnapshot snapshot;
        final GeneratedBoardInstance original, owner;
        final Vector<CircuitElm> originalGraph;
        Vector<CircuitElm> privateGraph;
        final long[] priorCounters;
        final boolean measuring;
        Fixture(CirSim sim, long seed) {
            this.sim=sim; original=sim.getGeneratedBoardInstance();
            originalGraph=sim.elmList;
            snapshot=Task41SimulationSnapshot.capture(sim);
            priorCounters=counters(sim); measuring=sim.a01MeasurementRunning;
            owner=new RcDelayGenerator().generate(seed);
            try {
                snapshot.beginProof(sim);
                sim.setSimRunning(false); sim.developerVerifierRunning=true;
                sim.generatedRuntimeInstallationInProgress=true;
                original.getExternalPowerBindings().setConnected(false);
                privateGraph=new Vector<CircuitElm>(); sim.elmList=privateGraph;
                sim.adjustables=new Vector<Adjustable>();
                sim.undoStack=new Vector<String>(); sim.redoStack=new Vector<String>();
                sim.scopes=new Scope[20]; sim.scopeColCount=new int[20]; sim.scopeCount=0;
                sim.dragElm=null; sim.menuElm=null; sim.heldSwitchElm=null;
                sim.installGeneratedChallengeForDeveloperVerification(owner);
                sim.generatedRuntimeInstallationInProgress=false;
                // Same cold real graph and initial solver state for both independent runs.
                sim.solverExecutor.analyze(); sim.solverExecutor.advanceSteps(1);
                sim.a01MeasurementRunning=true; restoreCounters(sim, new long[8]);
            } catch (RuntimeException failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            } catch (Error failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        void close() {
            try {
                require((sim.getGeneratedBoardInstance()==owner && sim.elmList==privateGraph) ||
                    (sim.getGeneratedBoardInstance()==original &&
                        (sim.elmList==originalGraph || sim.elmList==privateGraph)),
                    "fixture retains exact private owner or partial installation for cleanup");
                sim.invalidateGeneratedOwnerWork();
                owner.getExternalPowerBindings().setConnected(false);
                snapshot.restore(sim); snapshot.assertRestored(sim);
                for (CircuitElm element : owner.getSimulationElements()) {
                    require(!sim.elmList.contains(element) && !original.getSimulationElements().contains(element),
                        "fixture deletes only its detached private elements");
                    element.delete();
                }
            } finally { sim.a01MeasurementRunning=measuring; restoreCounters(sim, priorCounters); }
        }
    }
}
