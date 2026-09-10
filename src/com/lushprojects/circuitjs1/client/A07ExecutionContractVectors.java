package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.*;

/** Handwritten lease/state/event oracles shared by JVM and compiled GWT checks. */
final class A07ExecutionContractVectors {
    static int assertions;
    static int run() {
        assertions = 0;
        final SolverExecutionBoundary b = new SolverExecutionBoundary();
        final Object owner = new Object(), graph = new Object(), other = new Object();
        b.bind(owner, graph);
        long generation = b.getGeneration(); b.bind(owner, graph);
        require(generation == b.getGeneration(), "identity is not a repaint counter");
        final Operation first = b.begin(2, 5, 100, 100, 0);
        require(b.observation() == null, "active work cannot publish");
        expect(Outcome.BUSY, new Runnable() { public void run() { b.begin(1, 1, 101, 100, 0); }});
        b.beginTrial(first, 101); b.accepted(first, .1, 102);
        require(b.observation() == null, "accepted intermediate step is still private");
        b.beginTrial(first, 103); b.accepted(first, .2, 104);
        require(b.finish(first, Outcome.COMPLETE) == Outcome.COMPLETE, "bounded successful operation");
        Observation sample = b.observation();
        require(sample != null && sample.acceptedStep == 2 && sample.operation == first.id, "accepted serial identity");
        require(b.canPublish(first, sample), "exact completed result can publish");
        require(!b.isCurrent(sample, owner, graph, .1), "timestamp rebase cannot refresh sample");
        require(!b.isCurrent(sample, other, graph, .2), "foreign owner rejected");
        Operation idle = b.begin(1, 1, 200, 100, .2);
        b.finish(idle, Outcome.COMPLETE);
        require(b.observation() == sample && !b.canPublish(idle, sample), "no-work tick retains without minting");
        b.invalidate();
        require(!b.isCurrent(sample, owner, graph, .2) && b.observation() == null, "mutation invalidates same-time sample");
        final Operation before = b.begin(1, 1, 300, 100, .2);
        b.cancel(before);
        expect(Outcome.CANCELLED, new Runnable() { public void run() { b.beginTrial(before, 301); }});
        require(before.acceptedSteps == 0 && b.finish(before, Outcome.COMPLETE) == Outcome.CANCELLED,
            "cancel before execution cannot advance or turn green");
        final Operation after = b.begin(2, 3, 400, 100, .2);
        b.beginTrial(after, 401); b.accepted(after, .3, 402); b.cancel(after);
        expect(Outcome.CANCELLED, new Runnable() { public void run() { b.beginTrial(after, 403); }});
        require(after.acceptedSteps == 1 && b.finish(after, Outcome.COMPLETE) == Outcome.CANCELLED,
            "cancel after accepted state blocks more work and final publication");
        require(b.observation() == null && !b.canPublish(after, sample), "cancelled candidate is not a sample");
        final Operation trials = b.begin(2, 1, 500, 100, .3);
        b.beginTrial(trials, 501);
        expect(Outcome.WORK_EXHAUSTED, new Runnable() { public void run() { b.beginTrial(trials, 502); }});
        require(b.finish(trials, Outcome.COMPLETE) == Outcome.WORK_EXHAUSTED, "nonlinear trials have an independent budget");
        final Operation steps = b.begin(1, 3, 600, 100, .3);
        b.beginTrial(steps, 601); b.accepted(steps, .4, 602);
        expect(Outcome.WORK_EXHAUSTED, new Runnable() { public void run() { b.beginTrial(steps, 603); }});
        require(b.finish(steps, Outcome.COMPLETE) == Outcome.WORK_EXHAUSTED, "accepted steps have their own budget");
        final Operation deadline = b.begin(1, 3, 700, 100, .4);
        expect(Outcome.DEADLINE, new Runnable() { public void run() { b.check(deadline, 800); }});
        b.finish(deadline, Outcome.COMPLETE);
        final Operation regressed = b.begin(1, 3, 900, 100, .4);
        expect(Outcome.DEADLINE, new Runnable() { public void run() { b.check(regressed, 899); }});
        b.finish(regressed, Outcome.COMPLETE);
        final Operation nan = b.begin(1, 3, 1000, 100, .4); b.beginTrial(nan, 1001);
        expect(Outcome.NUMERICAL_FAILURE, new Runnable() { public void run() { b.accepted(nan, Double.NaN, 1002); }});
        require(b.finish(nan, Outcome.COMPLETE) == Outcome.NUMERICAL_FAILURE && b.observation() == null,
            "nonfinite state cannot publish");
        final Operation fabricated = b.begin(1, 3, 1100, 100, .4);
        expect(Outcome.NUMERICAL_FAILURE, new Runnable() { public void run() { b.accepted(fabricated, .5, 1101); }});
        b.finish(fabricated, Outcome.COMPLETE);
        Operation unfinished = b.begin(1, 3, 1200, 100, .4); b.beginTrial(unfinished, 1201);
        require(b.finish(unfinished, Outcome.COMPLETE) == Outcome.NONCONVERGENCE,
            "unfinished trial cannot manufacture success");
        final Operation stale = b.begin(1, 3, 1300, 100, .4);
        b.retire(); b.bind(other, graph);
        Operation successor = b.begin(1, 3, 1400, 100, .4);
        require(b.finish(stale, Outcome.COMPLETE) == Outcome.STALE_OWNER && b.isBusy(),
            "old cleanup cannot release successor lease");
        expect(Outcome.STALE_OWNER, new Runnable() { public void run() { b.beginTrial(stale, 1401); }});
        b.beginTrial(successor, 1401); b.accepted(successor, .5, 1402); b.finish(successor, Outcome.COMPLETE);
        require(b.canPublish(successor, b.observation()) && !b.canPublish(first, sample), "successor is sole result owner");
        events();
        return assertions;
    }
    private static void events() {
        require(eventTrace(false).equals("ABDC"), "chronological and FIFO tie ordering");
        require(eventTrace(false).equals(eventTrace(true)), "render/yield batches do not choose event order");
        final SolverEventQueue queue = new SolverEventQueue(8, 4, 0);
        final int[] fired = {0};
        final SolverEventQueue.Action[] feedback = new SolverEventQueue.Action[1];
        feedback[0] = new SolverEventQueue.Action() {
            public void fire(double due, double accepted) { fired[0]++; queue.schedule(due, feedback[0]); }
        };
        queue.schedule(.1, feedback[0]);
        expect(Outcome.WORK_EXHAUSTED, new Runnable() { public void run() { queue.dispatchAccepted(.1); }});
        require(fired[0] == 4, "same-time feedback has finite committed work");
        queue.reset(3);
        final int[] reset = {0};
        SolverEventQueue.Event cancelled = queue.schedule(3, new SolverEventQueue.Action() {
            public void fire(double due, double accepted) { reset[0]++; }
        });
        cancelled.cancel(); queue.dispatchAccepted(3);
        require(reset[0] == 0, "cancelled event does not execute at startup");
        queue.schedule(3.1, new SolverEventQueue.Action() {
            public void fire(double due, double accepted) { require(due == 3.1, "explicit startup phase"); reset[0]++; }
        });
        queue.dispatchAccepted(3.1); queue.dispatchAccepted(3.1);
        require(reset[0] == 1, "repeated accepted boundary does not duplicate committed state");
        boolean oldPhase = false;
        try { queue.schedule(2, feedback[0]); } catch (IllegalArgumentException expected) { oldPhase = true; }
        require(oldPhase, "past-phase scheduling rejected");
        queue.cancel();
        boolean stopped = false;
        try { queue.dispatchAccepted(4); } catch (IllegalStateException expected) { stopped = true; }
        require(stopped && reset[0] == 1, "retired queue requires explicit reset");
    }
    private static String eventTrace(boolean combined) {
        final SolverEventQueue queue = new SolverEventQueue(8, 8, 0);
        final StringBuilder result = new StringBuilder();
        queue.schedule(.1, new SolverEventQueue.Action() { public void fire(double due, double accepted) {
            result.append("A");
            queue.schedule(due, new SolverEventQueue.Action() { public void fire(double d, double a) { result.append("D"); }});
        }});
        queue.schedule(.2, new SolverEventQueue.Action() { public void fire(double d, double a) { result.append("C"); }});
        queue.schedule(.1, new SolverEventQueue.Action() { public void fire(double d, double a) { result.append("B"); }});
        if (!combined) { queue.dispatchAccepted(.05); queue.dispatchAccepted(.1); }
        queue.dispatchAccepted(.2);
        return result.toString();
    }
    private static void expect(Outcome outcome, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (Failure failure) { rejected = failure.outcome == outcome; }
        require(rejected, "expected failure " + outcome);
    }
    private static void require(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError("A07 execution contract: " + message);
    }
}
