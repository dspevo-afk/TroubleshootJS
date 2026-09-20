package com.lushprojects.circuitjs1.client;

/**
 * One immutable value observed at an accepted solver time.
 *
 * <p>This type deliberately contains no CircuitJS element, graph, or UI
 * identity.  The solver-time observation owner supplies those identities at a
 * higher layer; this value only records the two numbers needed by signal
 * analysis.</p>
 */
final class SolverTimeSample {
    private final double time;
    private final double value;

    SolverTimeSample(double time, double value) {
        if (!finite(time) || !finite(value))
            throw new IllegalArgumentException("Solver sample time and value must be finite");
        this.time = time;
        this.value = value;
    }

    double getTime() {
        return time;
    }

    double getValue() {
        return value;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
