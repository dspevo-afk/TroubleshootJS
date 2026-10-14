package com.lushprojects.circuitjs1.client;

final class PcbTraceRules {
    static final int TRACE_WIDTH = 9;
    /** Nominal thickness envelope for raised-clearance checks; drawing units, not mm. */
    static final int COPPER_HEIGHT = 2;
    static final int MIN_VISIBLE_CLEARANCE = 6;
    static final int MIN_CENTERLINE_CLEARANCE = TRACE_WIDTH + MIN_VISIBLE_CLEARANCE;
    static final int ROUTING_GRID_CLEARANCE_CELLS = 1;

    static void requireWidth(int width) {
        if (width < TRACE_WIDTH || width > 63 || (width & 1) == 0)
            throw new IllegalArgumentException("Invalid PCB trace width: " + width);
    }

    static int clearance(int firstWidth, int secondWidth) {
        requireWidth(firstWidth);
        requireWidth(secondWidth);
        return (firstWidth + secondWidth + 1) / 2 + MIN_VISIBLE_CLEARANCE;
    }

    private PcbTraceRules() { }
}
