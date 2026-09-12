package com.lushprojects.circuitjs1.client;

/** Visibility is not electrical connectivity. Covered or opposite-face copper is not a probe target. */
final class PcbCopperAccess {
    enum Exposure { EXPOSED, COVERED }

    /** Bounds and pointer share the renderer's coordinate frame. A drill must not inherit a nearby pad's hit halo. */
    static boolean blocksProbeAt(PcbBoardHole hole, Rectangle bounds, int x, int y) {
        return hole != null && hole.kind == PcbBoardHole.Kind.NON_PLATED && bounds != null &&
            x >= bounds.x && (long)x <= (long)bounds.x + bounds.width &&
            y >= bounds.y && (long)y <= (long)bounds.y + bounds.height;
    }
    private PcbCopperAccess() { }

    static boolean hasCopper(PcbPadPlacement pad, PcbCopperLayer layer) {
        return pad != null && layer != null &&
            (pad.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
             PcbCopperLayer.forFace(pad.getMountingSide()) == layer);
    }

    static boolean canProbe(PcbPadPlacement pad, PcbBoardSide face) {
        return face != null && hasCopper(pad, PcbCopperLayer.forFace(face)) &&
            pad.getExposure() == Exposure.EXPOSED;
    }

    static boolean canProbe(PcbCopperLayer layer, Exposure exposure, PcbBoardSide face) {
        return layer != null && face != null && exposure == Exposure.EXPOSED &&
            layer == PcbCopperLayer.forFace(face);
    }
}
