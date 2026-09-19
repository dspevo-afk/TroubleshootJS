package com.lushprojects.circuitjs1.client;

/** Relay content uses the shared placer/router, never authored component or trace coordinates. */
final class RelayOutputPcbLayoutFactory {
    private RelayOutputPcbLayoutFactory() { }
    static PcbBoardLayout create(TroubleshootBoard board, long seed) {
        return ProceduralPcbLayout.generate(board, seed, QuickPlayFamilyRegistry.RELAY_OUTPUT);
    }
}
