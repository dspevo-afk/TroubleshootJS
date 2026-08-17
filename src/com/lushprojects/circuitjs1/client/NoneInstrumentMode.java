package com.lushprojects.circuitjs1.client;

final class NoneInstrumentMode extends AbstractInstrumentModeStrategy {
    NoneInstrumentMode() {
        super("NONE", "", "--- V", 0,
            new InstrumentProbeRequirements(false, InstrumentProbePolarity.UNSPECIFIED,
                InstrumentProbePolarity.UNSPECIFIED),
            InstrumentPowerPolicy.POWERED_OR_UNPOWERED, false);
    }
}
