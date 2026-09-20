package com.lushprojects.circuitjs1.client;

/**
 * Bounded chronological ring of immutable solver-time samples.
 *
 * <p>Appending is intentionally strict: every accepted time must be finite
 * and greater than the preceding accepted time.  When the capacity is full,
 * the oldest sample is retired and the chronological invariant is retained.
 * A caller must clear the window when its solver owner, graph, power state, or
 * observation epoch changes.</p>
 */
final class SolverTimeWindow {
    enum SampleStatus { OK, INSUFFICIENT }
    enum DurationStatus { OK, INSUFFICIENT }
    enum MaxGapStatus { OK, UNDECLARED, UNKNOWN, EXCEEDED }
    enum BandwidthStatus { OK, UNDECLARED, UNKNOWN, LIMITED }

    /** Immutable quality report for one requested observation window. */
    static final class Assessment {
        private final int sampleCount;
        private final double duration;
        private final double maxGap;
        private final double declaredMaximumGap;
        private final double declaredBandwidthHz;
        private final int minimumSamples;
        private final double minimumDuration;
        private final SampleStatus sampleStatus;
        private final DurationStatus durationStatus;
        private final MaxGapStatus maxGapStatus;
        private final BandwidthStatus bandwidthStatus;

        private Assessment(int sampleCount, double duration, double maxGap,
                int minimumSamples, double minimumDuration,
                double declaredMaximumGap, double declaredBandwidthHz,
                SampleStatus sampleStatus, DurationStatus durationStatus,
                MaxGapStatus maxGapStatus, BandwidthStatus bandwidthStatus) {
            this.sampleCount = sampleCount;
            this.duration = duration;
            this.maxGap = maxGap;
            this.minimumSamples = minimumSamples;
            this.minimumDuration = minimumDuration;
            this.declaredMaximumGap = declaredMaximumGap;
            this.declaredBandwidthHz = declaredBandwidthHz;
            this.sampleStatus = sampleStatus;
            this.durationStatus = durationStatus;
            this.maxGapStatus = maxGapStatus;
            this.bandwidthStatus = bandwidthStatus;
        }

        int getSampleCount() {
            return sampleCount;
        }

        double getDuration() {
            return duration;
        }

        double getMaxGap() {
            return maxGap;
        }

        int getMinimumSamples() {
            return minimumSamples;
        }

        double getMinimumDuration() {
            return minimumDuration;
        }

        double getDeclaredMaximumGap() {
            return declaredMaximumGap;
        }

        double getDeclaredBandwidthHz() {
            return declaredBandwidthHz;
        }

        SampleStatus getSampleStatus() {
            return sampleStatus;
        }

        DurationStatus getDurationStatus() {
            return durationStatus;
        }

        MaxGapStatus getMaxGapStatus() {
            return maxGapStatus;
        }

        BandwidthStatus getBandwidthStatus() {
            return bandwidthStatus;
        }

        boolean hasEnoughSamples() {
            return sampleStatus == SampleStatus.OK;
        }

        boolean hasEnoughDuration() {
            return durationStatus == DurationStatus.OK;
        }

        boolean hasAcceptableGap() {
            return maxGapStatus != MaxGapStatus.EXCEEDED;
        }

        boolean hasAcceptableBandwidth() {
            return bandwidthStatus != BandwidthStatus.LIMITED;
        }

        boolean isUsable() {
            return hasEnoughSamples() && hasEnoughDuration() &&
                maxGapStatus != MaxGapStatus.UNKNOWN &&
                hasAcceptableGap() && bandwidthStatus != BandwidthStatus.UNKNOWN &&
                hasAcceptableBandwidth();
        }
    }

    private final SolverTimeSample[] ring;
    private int first;
    private int count;
    private double latestTime = Double.NaN;

    SolverTimeWindow(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("Solver sample window capacity must be positive");
        ring = new SolverTimeSample[capacity];
    }

    int getCapacity() {
        return ring.length;
    }

    int getSampleCount() {
        return count;
    }

    int size() {
        return count;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /**
     * Appends one accepted solver sample.  The ring can overwrite its oldest
     * entry, but a timestamp can never move backwards or repeat.
     */
    void append(double time, double value) {
        append(new SolverTimeSample(time, value));
    }

    void append(SolverTimeSample sample) {
        if (sample == null)
            throw new IllegalArgumentException("Solver sample is required");
        double time = sample.getTime();
        if (count > 0) {
            double gap = time - latestTime;
            if (!(time > latestTime) || !finite(gap) || gap <= 0)
                throw new IllegalArgumentException("Solver sample time must increase finitely");
        }
        if (count < ring.length) {
            ring[(first + count) % ring.length] = sample;
            count++;
        } else {
            ring[first] = sample;
            first = (first + 1) % ring.length;
        }
        latestTime = time;
    }

    /** Removes all samples, including the remembered chronological cursor. */
    void clear() {
        for (int i = 0; i < ring.length; i++) ring[i] = null;
        first = 0;
        count = 0;
        latestTime = Double.NaN;
    }

    /** Returns a sample by chronological index, oldest first. */
    SolverTimeSample get(int chronologicalIndex) {
        if (chronologicalIndex < 0 || chronologicalIndex >= count)
            throw new IndexOutOfBoundsException("Solver sample index outside window");
        return ring[(first + chronologicalIndex) % ring.length];
    }

    /** Returns a defensive chronological copy of the bounded ring. */
    SolverTimeSample[] snapshot() {
        SolverTimeSample[] result = new SolverTimeSample[count];
        for (int i = 0; i < count; i++) result[i] = get(i);
        return result;
    }

    double getDuration() {
        return count < 2 ? 0 : get(count - 1).getTime() - get(0).getTime();
    }

    double getMaxGap() {
        if (count < 2) return 0;
        double result = 0;
        for (int i = 1; i < count; i++) {
            double gap = get(i).getTime() - get(i - 1).getTime();
            if (gap > result) result = gap;
        }
        return result;
    }

    /**
     * Evaluates this data against a caller-declared sample, duration, gap, and
     * acquisition support for a declared signal bandwidth.  NaN maximum-gap
     * or bandwidth declarations mean that the caller did not declare a bound;
     * no bound is inferred silently.  This assessment intentionally does not
     * assert that the waveform itself is inside that band; instrument analysis
     * must make that separate observed-signal decision.
     */
    Assessment assess(int minimumSamples, double minimumDuration,
            double maximumGap, double declaredBandwidthHz) {
        validateRequirements(minimumSamples, minimumDuration, maximumGap,
            declaredBandwidthHz);
        double duration = getDuration();
        double maxGap = getMaxGap();
        SampleStatus sampleStatus = count >= minimumSamples ?
            SampleStatus.OK : SampleStatus.INSUFFICIENT;
        DurationStatus durationStatus = finite(duration) &&
            duration >= minimumDuration ? DurationStatus.OK : DurationStatus.INSUFFICIENT;

        MaxGapStatus maxGapStatus;
        if (count < 2) maxGapStatus = MaxGapStatus.UNKNOWN;
        else if (isUndeclared(maximumGap)) maxGapStatus = MaxGapStatus.UNDECLARED;
        else maxGapStatus = maxGap <= maximumGap ? MaxGapStatus.OK : MaxGapStatus.EXCEEDED;

        BandwidthStatus bandwidthStatus;
        if (count < 2) bandwidthStatus = BandwidthStatus.UNKNOWN;
        else if (isUndeclared(declaredBandwidthHz)) bandwidthStatus = BandwidthStatus.UNDECLARED;
        else {
            double nyquist = .5 / maxGap;
            bandwidthStatus = finite(nyquist) && declaredBandwidthHz <= nyquist ?
                BandwidthStatus.OK : BandwidthStatus.LIMITED;
        }
        return new Assessment(count, duration, maxGap, minimumSamples,
            minimumDuration, maximumGap, declaredBandwidthHz, sampleStatus,
            durationStatus, maxGapStatus, bandwidthStatus);
    }

    private static void validateRequirements(int minimumSamples,
            double minimumDuration, double maximumGap, double declaredBandwidthHz) {
        if (minimumSamples < 1 || !finite(minimumDuration) || minimumDuration < 0)
            throw new IllegalArgumentException("Invalid solver window sample or duration requirement");
        if (!isUndeclared(maximumGap) &&
                (!finite(maximumGap) || maximumGap <= 0))
            throw new IllegalArgumentException("Invalid solver window maximum gap");
        if (!isUndeclared(declaredBandwidthHz) &&
                (!finite(declaredBandwidthHz) || declaredBandwidthHz <= 0))
            throw new IllegalArgumentException("Invalid solver window bandwidth");
    }

    private static boolean isUndeclared(double value) {
        return Double.isNaN(value) || value == Double.POSITIVE_INFINITY;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
