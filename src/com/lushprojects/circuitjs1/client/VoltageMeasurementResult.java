package com.lushprojects.circuitjs1.client;

/**
 * A typed outcome for a differential voltage observation.  A nonnumeric
 * result is deliberately not coerced to zero or a cached prior reading.
 */
final class VoltageMeasurementResult {
    enum Status {
        OK,
        OVER_RANGE,
        UNAVAILABLE,
        REFERENCE_REJECTED,
        REFERENCE_UNPROVEN,
        INSUFFICIENT_WINDOW,
        GAP,
        BANDWIDTH_LIMITED,
        ALIASED,
        NO_SIGNAL,
        NUMERICAL_LIMITED
    }

    private final Status status;
    private final double value;
    private final MeasurementReferencePolicy.Result reference;
    private final SignalMeasurementAnalysis.Result signal;

    private VoltageMeasurementResult(Status status, double value,
            MeasurementReferencePolicy.Result reference,
            SignalMeasurementAnalysis.Result signal) {
        if (status == null)
            throw new IllegalArgumentException("Voltage measurement status is required");
        this.status = status;
        this.value = value;
        this.reference = reference;
        this.signal = signal;
    }

    static VoltageMeasurementResult numeric(double value,
            MeasurementReferencePolicy.Result reference, double maximumAbsVoltage) {
        if (!finite(value))
            return unavailable(reference);
        if (finite(maximumAbsVoltage) && Math.abs(value) > maximumAbsVoltage)
            return new VoltageMeasurementResult(Status.OVER_RANGE, Double.NaN, reference, null);
        return new VoltageMeasurementResult(Status.OK, value, reference, null);
    }

    static VoltageMeasurementResult unavailable(MeasurementReferencePolicy.Result reference) {
        return new VoltageMeasurementResult(Status.UNAVAILABLE, Double.NaN, reference, null);
    }

    static VoltageMeasurementResult reference(MeasurementReferencePolicy.Result reference) {
        if (reference == null)
            return unavailable(null);
        if (reference.getDecision() == MeasurementReferencePolicy.Decision.REJECTED)
            return new VoltageMeasurementResult(Status.REFERENCE_REJECTED, Double.NaN, reference, null);
        return new VoltageMeasurementResult(Status.REFERENCE_UNPROVEN, Double.NaN, reference, null);
    }

    static VoltageMeasurementResult signal(SignalMeasurementAnalysis.Result signal,
            MeasurementReferencePolicy.Result reference) {
        if (signal == null)
            return unavailable(reference);
        Status status;
        switch (signal.getStatus()) {
        case OK: status = Status.OK; break;
        case OVER_RANGE: status = Status.OVER_RANGE; break;
        case GAP: status = Status.GAP; break;
        case BANDWIDTH_LIMITED: status = Status.BANDWIDTH_LIMITED; break;
        case ALIASED: status = Status.ALIASED; break;
        case NO_SIGNAL: status = Status.NO_SIGNAL; break;
        case NUMERICAL_LIMITED: status = Status.NUMERICAL_LIMITED; break;
        case INSUFFICIENT_WINDOW:
        case NO_TRIGGER:
        default: status = Status.INSUFFICIENT_WINDOW; break;
        }
        return new VoltageMeasurementResult(status,
            status == Status.OK || status == Status.NO_SIGNAL ? signal.getValue() : Double.NaN,
            reference, signal);
    }

    Status getStatus() { return status; }
    boolean isNumeric() { return status == Status.OK || status == Status.NO_SIGNAL; }
    double getValue() { return value; }
    MeasurementReferencePolicy.Result getReference() { return reference; }
    SignalMeasurementAnalysis.Result getSignal() { return signal; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
