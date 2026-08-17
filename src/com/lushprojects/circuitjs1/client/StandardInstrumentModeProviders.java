package com.lushprojects.circuitjs1.client;

/** Default bootstrap composition for the built-in production providers. */
final class StandardInstrumentModeProviders {
    private StandardInstrumentModeProviders() { }

    static InstrumentModeRegistry createRegistry() {
        return new InstrumentModeRegistry(
            new NoneInstrumentMode(),
            new DcVoltageInstrumentMode(),
            new ResistanceInstrumentMode("RESISTANCE", "OHM", 2, false),
            new ResistanceInstrumentMode("CONTINUITY", "CONT", 3, true),
            new DiodeTestInstrumentMode());
    }
}
