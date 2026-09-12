package com.lushprojects.circuitjs1.client;

/** Physical copper layers, independent of package mounting and screen projection. */
enum PcbCopperLayer {
    TOP, BOTTOM;

    static PcbCopperLayer forFace(PcbBoardSide face) {
        if (face == null) throw new IllegalArgumentException("Missing copper face");
        return face == PcbBoardSide.TOP ? TOP : BOTTOM;
    }

    PcbBoardSide getFace() {
        return this == TOP ? PcbBoardSide.TOP : PcbBoardSide.BOTTOM;
    }
}
