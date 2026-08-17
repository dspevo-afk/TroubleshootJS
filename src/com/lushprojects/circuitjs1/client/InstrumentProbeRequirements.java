package com.lushprojects.circuitjs1.client;

/** Probe requirements for one mode; it is not tied to a particular mode ID. */
final class InstrumentProbeRequirements {
    private final boolean requiresTwoProbes;
    private final InstrumentProbePolarity redPolarity;
    private final InstrumentProbePolarity blackPolarity;

    InstrumentProbeRequirements(boolean requiresTwoProbes,
            InstrumentProbePolarity redPolarity, InstrumentProbePolarity blackPolarity) {
        this.requiresTwoProbes = requiresTwoProbes;
        this.redPolarity = redPolarity == null ? InstrumentProbePolarity.UNSPECIFIED : redPolarity;
        this.blackPolarity = blackPolarity == null ? InstrumentProbePolarity.UNSPECIFIED : blackPolarity;
    }

    boolean requiresTwoProbes() {
        return requiresTwoProbes;
    }

    InstrumentProbePolarity getRedPolarity() {
        return redPolarity;
    }

    InstrumentProbePolarity getBlackPolarity() {
        return blackPolarity;
    }
}
