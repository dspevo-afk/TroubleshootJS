package com.lushprojects.circuitjs1.client;

final class PcbTraceRules {
    static final int TRACE_WIDTH = 9;
    static final int MIN_VISIBLE_CLEARANCE = 6;
    static final int MIN_CENTERLINE_CLEARANCE = TRACE_WIDTH + MIN_VISIBLE_CLEARANCE;
    static final int ROUTING_GRID_CLEARANCE_CELLS = 1;

    private PcbTraceRules() { }
}
