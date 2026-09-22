package com.lushprojects.circuitjs1.client;

/** Deterministic physical realization for the E04 sensor/control leaf. */
final class SensorControlPcbLayoutFactory {
    private SensorControlPcbLayoutFactory() { }

    static PcbBoardLayout create(TroubleshootBoard board, long seed) {
        return ProceduralPcbLayout.generate(board, seed, QuickPlayFamilyRegistry.SENSOR_CONTROL);
    }
}
