package com.lushprojects.circuitjs1.client;

/**
 * Bounded solver-time reacquisition policy shared by finite-load DC and AC
 * voltage instruments.  It deliberately owns no solver operation: modes use
 * the result only to queue one transaction after an accepted solver callback.
 */
final class VoltageMeasurementReacquisitionPolicy {
    private VoltageMeasurementReacquisitionPolicy() { }

    /**
     * Schedule another observation only after the same bounded 50 ms period
     * used for an AC acquisition.  A reference failure or unavailable owner
     * needs a lifecycle change, not polling; that lifecycle already refreshes
     * the active instrument explicitly.
     */
    static double nextDueAt(VoltageMeasurementResult result, double currentSolverTime) {
        if (!shouldReacquire(result) || !finite(currentSolverTime))
            return Double.NaN;
        double dueAt = currentSolverTime + CirSim.AC_VOLTAGE_CAPTURE_SECONDS;
        return finite(dueAt) ? dueAt : Double.NaN;
    }

    static boolean isDue(double nextDueAt, double currentSolverTime) {
        return finite(nextDueAt) && finite(currentSolverTime) &&
            currentSolverTime >= nextDueAt;
    }

    static boolean shouldReacquire(VoltageMeasurementResult result) {
        if (result == null)
            return false;
        switch (result.getStatus()) {
        case REFERENCE_REJECTED:
        case REFERENCE_UNPROVEN:
        case UNAVAILABLE:
            return false;
        default:
            return true;
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
