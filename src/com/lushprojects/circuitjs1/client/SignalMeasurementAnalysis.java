package com.lushprojects.circuitjs1.client;

/**
 * Pure analysis of a bounded {@link SolverTimeWindow}.
 *
 * <p>All temporal calculations use the timestamps carried by the samples.
 * There is no fixed-timestep assumption, component metadata lookup, or UI
 * behavior here.  The accepted-step owner is responsible for deciding when to
 * append samples and when to clear a stale window.</p>
 */
final class SignalMeasurementAnalysis {
    enum Status {
        OK,
        INSUFFICIENT_WINDOW,
        GAP,
        BANDWIDTH_LIMITED,
        ALIASED,
        OVER_RANGE,
        NO_TRIGGER,
        NO_SIGNAL,
        NUMERICAL_LIMITED
    }

    enum Trigger { RISING, FALLING, ANY }

    /**
     * Separates what the accepted timestamps can support from what the
     * observed waveform actually demonstrated.  In particular, a dense set
     * of samples can be adequate for a 200 Hz instrument while still proving
     * that the input contains faster zero-crossing content that the declared
     * passband does not qualify.
     */
    enum SignalBandwidthStatus {
        NOT_APPLICABLE,
        UNDECLARED,
        WITHIN_DECLARED_BAND,
        EXCEEDS_DECLARED_BAND,
        UNRESOLVED
    }

    /** Immutable caller-declared measurement policy. */
    static final class Policy {
        static final double UNDECLARED = Double.NaN;
        static final double UNLIMITED = Double.POSITIVE_INFINITY;

        final int minimumSamples;
        final double minimumDuration;
        final double maximumGap;
        final double declaredBandwidthHz;
        final double maximumAbsVoltage;
        final double signalThreshold;
        final Trigger trigger;

        Policy() {
            this(2, 0, UNDECLARED, UNDECLARED, UNLIMITED, 1e-12,
                Trigger.RISING);
        }

        Policy(int minimumSamples, double minimumDuration, double maximumGap,
                double declaredBandwidthHz, double maximumAbsVoltage) {
            this(minimumSamples, minimumDuration, maximumGap,
                declaredBandwidthHz, maximumAbsVoltage, 1e-12,
                Trigger.RISING);
        }

        Policy(int minimumSamples, double minimumDuration, double maximumGap,
                double declaredBandwidthHz, double maximumAbsVoltage,
                double signalThreshold, Trigger trigger) {
            if (minimumSamples < 1 || !finite(minimumDuration) || minimumDuration < 0)
                throw new IllegalArgumentException("Invalid measurement sample or duration policy");
            if (!undeclared(maximumGap) && (!finite(maximumGap) || maximumGap <= 0))
                throw new IllegalArgumentException("Invalid measurement maximum gap policy");
            if (!undeclared(declaredBandwidthHz) &&
                    (!finite(declaredBandwidthHz) || declaredBandwidthHz <= 0))
                throw new IllegalArgumentException("Invalid measurement bandwidth policy");
            if (Double.isNaN(maximumAbsVoltage) || maximumAbsVoltage < 0 ||
                    maximumAbsVoltage == Double.NEGATIVE_INFINITY)
                throw new IllegalArgumentException("Invalid measurement voltage range policy");
            if (!finite(signalThreshold) || signalThreshold < 0)
                throw new IllegalArgumentException("Invalid measurement signal threshold policy");
            this.minimumSamples = minimumSamples;
            this.minimumDuration = minimumDuration;
            this.maximumGap = maximumGap;
            this.declaredBandwidthHz = declaredBandwidthHz;
            this.maximumAbsVoltage = maximumAbsVoltage;
            this.signalThreshold = signalThreshold;
            this.trigger = trigger;
        }

        static Policy defaults() {
            return new Policy();
        }

        boolean hasVoltageRange() {
            return finite(maximumAbsVoltage);
        }

        boolean hasDeclaredBandwidth() {
            return finite(declaredBandwidthHz) && declaredBandwidthHz > 0;
        }

        private static boolean undeclared(double value) {
            return Double.isNaN(value) || value == Double.POSITIVE_INFINITY;
        }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    /** Immutable result shared by DC, AC-RMS, and frequency measurements. */
    static final class Result {
        private final Status status;
        private final double value;
        private final double dcMean;
        private final double frequencyHz;
        private final SolverTimeWindow.Assessment window;
        private final SignalBandwidthStatus signalBandwidthStatus;

        private Result(Status status, double value, double dcMean,
                double frequencyHz, SolverTimeWindow.Assessment window) {
            this(status, value, dcMean, frequencyHz, window,
                SignalBandwidthStatus.NOT_APPLICABLE);
        }

        private Result(Status status, double value, double dcMean,
                double frequencyHz, SolverTimeWindow.Assessment window,
                SignalBandwidthStatus signalBandwidthStatus) {
            this.status = status;
            this.value = value;
            this.dcMean = dcMean;
            this.frequencyHz = frequencyHz;
            this.window = window;
            this.signalBandwidthStatus = signalBandwidthStatus;
        }

        Status getStatus() {
            return status;
        }

        boolean isOk() {
            return status == Status.OK;
        }

        double getValue() {
            return value;
        }

        /** Signed time-weighted mean, also exposed for AC-coupled readings. */
        double getDcMean() {
            return dcMean;
        }

        double getMean() {
            return dcMean;
        }

        double getFrequencyHz() {
            return frequencyHz;
        }

        SolverTimeWindow.Assessment getWindowAssessment() {
            return window;
        }

        int getSampleCount() {
            return window.getSampleCount();
        }

        double getDuration() {
            return window.getDuration();
        }

        double getMaxGap() {
            return window.getMaxGap();
        }

        SolverTimeWindow.SampleStatus getSampleStatus() {
            return window.getSampleStatus();
        }

        SolverTimeWindow.DurationStatus getDurationStatus() {
            return window.getDurationStatus();
        }

        SolverTimeWindow.MaxGapStatus getMaxGapStatus() {
            return window.getMaxGapStatus();
        }

        SolverTimeWindow.BandwidthStatus getBandwidthStatus() {
            return window.getBandwidthStatus();
        }

        /**
         * Timestamp/sample-spacing adequacy is exposed separately above;
         * this reports the content qualification used by AC RMS.
         */
        SignalBandwidthStatus getSignalBandwidthStatus() {
            return signalBandwidthStatus;
        }
    }

    private static final class Statistics {
        final double mean;
        final double acRms;

        Statistics(double mean, double acRms) {
            this.mean = mean;
            this.acRms = acRms;
        }
    }

    private static final class CrossingSet {
        final double[] times;
        final int count;

        CrossingSet(double[] times, int count) {
            this.times = times;
            this.count = count;
        }
    }

    private SignalMeasurementAnalysis() { }

    static Result measureDcMean(SolverTimeWindow window) {
        return measureDcMean(window, Policy.defaults());
    }

    static Result measureDcMean(SolverTimeWindow window, Policy policy) {
        Policy actual = requirePolicy(policy);
        SolverTimeWindow.Assessment assessment = assess(window, actual);
        if (hasOverRange(window, actual))
            return failure(Status.OVER_RANGE, assessment);
        Status windowFailure = windowFailure(assessment);
        if (windowFailure != null)
            return failure(windowFailure, assessment);
        Statistics stats = statistics(window.snapshot(), assessment.getDuration());
        if (stats == null)
            return failure(Status.NUMERICAL_LIMITED, assessment);
        return new Result(Status.OK, stats.mean, stats.mean, Double.NaN, assessment);
    }

    static Result measureAcRms(SolverTimeWindow window) {
        return measureAcRms(window, Policy.defaults());
    }

    static Result measureAcRms(SolverTimeWindow window, Policy policy) {
        Policy actual = requirePolicy(policy);
        SolverTimeWindow.Assessment assessment = assess(window, actual);
        if (hasOverRange(window, actual))
            return failure(Status.OVER_RANGE, assessment);
        Status windowFailure = windowFailure(assessment);
        if (windowFailure != null)
            return failure(windowFailure, assessment);
        Statistics stats = statistics(window.snapshot(), assessment.getDuration());
        if (stats == null)
            return failure(Status.NUMERICAL_LIMITED, assessment);
        if (stats.acRms <= actual.signalThreshold)
            return new Result(Status.NO_SIGNAL, 0, stats.mean, Double.NaN, assessment,
                actual.hasDeclaredBandwidth() ? SignalBandwidthStatus.WITHIN_DECLARED_BAND :
                    SignalBandwidthStatus.UNDECLARED);

        SignalBandwidthStatus signalBandwidth = assessObservedAcBandwidth(
            window.snapshot(), stats.mean, actual);
        if (signalBandwidth == SignalBandwidthStatus.EXCEEDS_DECLARED_BAND)
            return failure(Status.BANDWIDTH_LIMITED, assessment, signalBandwidth);
        /* A finite time-window RMS is only published when it contains enough
         * observed alternating behavior to establish the AC-coupled reading.
         * This makes low-frequency/one-shot captures honest rather than
         * showing a plausible number from an arbitrarily short fragment. */
        if (signalBandwidth == SignalBandwidthStatus.UNRESOLVED)
            return failure(Status.INSUFFICIENT_WINDOW, assessment, signalBandwidth);
        return new Result(Status.OK, stats.acRms, stats.mean, Double.NaN, assessment,
            signalBandwidth);
    }

    static Result measureFrequency(SolverTimeWindow window) {
        return measureFrequency(window, Policy.defaults());
    }

    static Result measureFrequency(SolverTimeWindow window, Policy policy) {
        Policy actual = requirePolicy(policy);
        SolverTimeWindow.Assessment assessment = assess(window, actual);
        if (hasOverRange(window, actual))
            return failure(Status.OVER_RANGE, assessment);
        Status windowFailure = windowFailure(assessment);
        if (windowFailure != null)
            return failure(windowFailure, assessment);
        Statistics stats = statistics(window.snapshot(), assessment.getDuration());
        if (stats == null)
            return failure(Status.NUMERICAL_LIMITED, assessment);
        if (stats.acRms <= actual.signalThreshold)
            return new Result(Status.NO_SIGNAL, Double.NaN, stats.mean,
                Double.NaN, assessment);
        if (actual.trigger == null)
            return failure(Status.NO_TRIGGER, assessment);

        CrossingSet crossings = zeroCrossings(window.snapshot(), stats.mean,
            actual.trigger);
        if (crossings.count == 0)
            return failure(Status.NO_TRIGGER, assessment);
        if (crossings.count < 2)
            return failure(Status.INSUFFICIENT_WINDOW, assessment);

        double crossingPeriod = (crossings.times[crossings.count - 1] -
            crossings.times[0]) / (crossings.count - 1);
        double period = actual.trigger == Trigger.ANY ? crossingPeriod * 2 : crossingPeriod;
        if (!finite(period) || period <= 0)
            return failure(Status.NUMERICAL_LIMITED, assessment);
        double frequency = 1 / period;
        if (!finite(frequency) || frequency <= 0)
            return failure(Status.NUMERICAL_LIMITED, assessment);

        double nyquist = .5 / assessment.getMaxGap();
        if (!finite(nyquist) || frequency >= nyquist ||
                (actual.hasDeclaredBandwidth() && frequency > actual.declaredBandwidthHz))
            return failure(Status.ALIASED, assessment);
        return new Result(Status.OK, frequency, stats.mean, frequency, assessment);
    }

    /** Convenience aliases for callers that prefer the shorter analysis names. */
    static Result dcMean(SolverTimeWindow window, Policy policy) {
        return measureDcMean(window, policy);
    }

    static Result acRms(SolverTimeWindow window, Policy policy) {
        return measureAcRms(window, policy);
    }

    static Result frequency(SolverTimeWindow window, Policy policy) {
        return measureFrequency(window, policy);
    }

    static boolean hasVoltageOverRange(SolverTimeWindow window,
            double maximumAbsVoltage) {
        if (window == null)
            throw new IllegalArgumentException("Solver sample window is required");
        if (Double.isNaN(maximumAbsVoltage) || maximumAbsVoltage < 0 ||
                maximumAbsVoltage == Double.NEGATIVE_INFINITY)
            throw new IllegalArgumentException("Invalid declared voltage range");
        if (Double.isInfinite(maximumAbsVoltage)) return false;
        SolverTimeSample[] samples = window.snapshot();
        for (int i = 0; i < samples.length; i++)
            if (Math.abs(samples[i].getValue()) > maximumAbsVoltage) return true;
        return false;
    }

    private static Policy requirePolicy(Policy policy) {
        if (policy == null) throw new IllegalArgumentException("Measurement policy is required");
        return policy;
    }

    private static SolverTimeWindow.Assessment assess(SolverTimeWindow window,
            Policy policy) {
        if (window == null) throw new IllegalArgumentException("Solver sample window is required");
        return window.assess(policy.minimumSamples, policy.minimumDuration,
            policy.maximumGap, policy.declaredBandwidthHz);
    }

    private static Status windowFailure(SolverTimeWindow.Assessment assessment) {
        if (assessment.getSampleStatus() != SolverTimeWindow.SampleStatus.OK ||
                assessment.getDurationStatus() != SolverTimeWindow.DurationStatus.OK ||
                assessment.getSampleCount() < 2)
            return Status.INSUFFICIENT_WINDOW;
        if (assessment.getMaxGapStatus() == SolverTimeWindow.MaxGapStatus.EXCEEDED)
            return Status.GAP;
        if (assessment.getBandwidthStatus() == SolverTimeWindow.BandwidthStatus.LIMITED)
            return Status.BANDWIDTH_LIMITED;
        return null;
    }

    private static boolean hasOverRange(SolverTimeWindow window, Policy policy) {
        return policy.hasVoltageRange() && hasVoltageOverRange(window,
            policy.maximumAbsVoltage);
    }

    private static Result failure(Status status,
            SolverTimeWindow.Assessment assessment) {
        return failure(status, assessment, SignalBandwidthStatus.NOT_APPLICABLE);
    }

    private static Result failure(Status status,
            SolverTimeWindow.Assessment assessment,
            SignalBandwidthStatus signalBandwidthStatus) {
        return new Result(status, Double.NaN, Double.NaN, Double.NaN, assessment,
            signalBandwidthStatus);
    }

    /**
     * Qualifies the input from accepted CircuitJS observations alone.  It is
     * deliberately not a source-frequency lookup: all mean-centered crossing
     * intervals are reconstructed from solver timestamps.  Three alternating
     * crossings provide one complete observed cycle; fewer cannot establish a
     * low-frequency AC RMS window.
     *
     * <p>This is a conservative rejection policy, rather than a simulated
     * analog filter.  A pair of successive mean crossings closer than the
     * half-period of the declared passband proves out-of-band content.  A
     * waveform whose crossings never reveal such content is qualified only
     * with respect to observed crossing behavior; hidden sub-threshold or
     * unsampled components are not claimed to be measured.</p>
     */
    private static SignalBandwidthStatus assessObservedAcBandwidth(
            SolverTimeSample[] samples, double mean, Policy policy) {
        if (!policy.hasDeclaredBandwidth())
            return SignalBandwidthStatus.UNDECLARED;
        CrossingSet crossings = zeroCrossings(samples, mean, Trigger.ANY);
        if (crossings.count < 3)
            return SignalBandwidthStatus.UNRESOLVED;
        double shortestAllowedHalfPeriod = 1 / (2 * policy.declaredBandwidthHz);
        for (int i = 1; i < crossings.count; i++) {
            double interval = crossings.times[i] - crossings.times[i - 1];
            if (!finite(interval) || interval <= 0)
                return SignalBandwidthStatus.UNRESOLVED;
            if (interval + 1e-12 < shortestAllowedHalfPeriod)
                return SignalBandwidthStatus.EXCEEDS_DECLARED_BAND;
        }
        return SignalBandwidthStatus.WITHIN_DECLARED_BAND;
    }

    /**
     * Computes a piecewise-linear, timestamp-weighted mean and AC RMS.  The
     * trapezoid mean and the exact integral of the squared linear segment make
     * irregular accepted solver steps meaningful rather than treating their
     * ordinal positions as time.
     */
    private static Statistics statistics(SolverTimeSample[] samples,
            double duration) {
        if (samples == null || samples.length < 2 || !finite(duration) || duration <= 0)
            return null;
        double mean = 0;
        for (int i = 1; i < samples.length; i++) {
            double dt = samples[i].getTime() - samples[i - 1].getTime();
            double weight = dt / duration;
            double segmentMean = samples[i - 1].getValue() * .5 +
                samples[i].getValue() * .5;
            mean += weight * segmentMean;
        }
        if (!finite(mean)) return null;

        double scale = 0;
        for (int i = 0; i < samples.length; i++) {
            double centered = samples[i].getValue() - mean;
            if (!finite(centered)) return null;
            double magnitude = Math.abs(centered);
            if (magnitude > scale) scale = magnitude;
        }
        if (scale == 0) return new Statistics(mean, 0);

        double normalizedSquare = 0;
        for (int i = 1; i < samples.length; i++) {
            double dt = samples[i].getTime() - samples[i - 1].getTime();
            double weight = dt / duration;
            double a = (samples[i - 1].getValue() - mean) / scale;
            double b = (samples[i].getValue() - mean) / scale;
            double segmentSquare = (a * a + a * b + b * b) / 3;
            normalizedSquare += weight * segmentSquare;
        }
        if (!finite(normalizedSquare) || normalizedSquare < 0)
            return null;
        double rms = scale * Math.sqrt(normalizedSquare);
        return finite(rms) ? new Statistics(mean, rms) : null;
    }

    /**
     * Finds only sign-changing zero crossings of the centered, timestamped
     * signal.  A run of exact-zero samples is treated as one crossing only if
     * its nonzero neighbors have opposite signs.  Each crossing time is
     * linearly interpolated from its surrounding solver timestamps.
     */
    private static CrossingSet zeroCrossings(SolverTimeSample[] samples,
            double mean, Trigger trigger) {
        double[] times = new double[samples.length - 1];
        int count = 0;
        int i = 0;
        while (i < samples.length - 1) {
            double a = samples[i].getValue() - mean;
            double b = samples[i + 1].getValue() - mean;
            if (!finite(a) || !finite(b)) return new CrossingSet(times, count);
            if (a == 0) {
                i++;
                continue;
            }
            if (b == 0) {
                int zeroEnd = i + 1;
                while (zeroEnd < samples.length &&
                        samples[zeroEnd].getValue() - mean == 0) zeroEnd++;
                if (zeroEnd < samples.length) {
                    double right = samples[zeroEnd].getValue() - mean;
                    if (opposite(a, right)) {
                        int direction = right > 0 ? 1 : -1;
                        if (matches(trigger, direction)) {
                            times[count++] = samples[i + 1].getTime();
                        }
                    }
                }
                i = zeroEnd;
                continue;
            }
            if (opposite(a, b)) {
                int direction = b > 0 ? 1 : -1;
                if (matches(trigger, direction)) {
                    double fraction = (-a) / (b - a);
                    double time = samples[i].getTime() + fraction *
                        (samples[i + 1].getTime() - samples[i].getTime());
                    if (finite(time) && (count == 0 || time > times[count - 1]))
                        times[count++] = time;
                }
            }
            i++;
        }
        return new CrossingSet(times, count);
    }

    private static boolean matches(Trigger trigger, int direction) {
        return trigger == Trigger.ANY ||
            (trigger == Trigger.RISING && direction > 0) ||
            (trigger == Trigger.FALLING && direction < 0);
    }

    private static boolean opposite(double a, double b) {
        return (a < 0 && b > 0) || (a > 0 && b < 0);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
