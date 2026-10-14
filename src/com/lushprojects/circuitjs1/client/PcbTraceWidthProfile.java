package com.lushprojects.circuitjs1.client;

/**
 * Finite drawing-space copper profiles; no current or manufacturing rating.
 * Edge clearance here is the trace-pair rule. Foreign-land contact and via
 * clearances retain their separate physical validation contracts.
 */
final class PcbTraceWidthProfile {
    static final PcbTraceWidthProfile UNIFORM = new PcbTraceWidthProfile("UNIFORM_TRACE", false);
    static final PcbTraceWidthProfile ROLE_WIDTHS = new PcbTraceWidthProfile("ROLE_TRACE", true);
    private final String id;
    private final boolean roleWidths;

    private PcbTraceWidthProfile(String id, boolean roleWidths) {
        this.id = id;
        this.roleWidths = roleWidths;
    }

    String getIdentity() { return id + "@1"; }
    boolean isLegacyUniform() { return !roleWidths; }
    int maximumWidth() { return roleWidths ? 13 : PcbTraceRules.TRACE_WIDTH; }

    int widthFor(BoardNet.RoutingRole role) {
        if (role == null) throw new IllegalArgumentException("Missing PCB net routing role");
        if (!roleWidths) return PcbTraceRules.TRACE_WIDTH;
        switch (role) {
        case HIGH_CURRENT: return 13;
        case SUPPLY:
        case RETURN: return 11;
        case CONTROL:
        case SIGNAL: return 9;
        default: throw new IllegalArgumentException("Unknown PCB net routing role");
        }
    }

    String canonical() {
        StringBuilder out = new StringBuilder(getIdentity());
        for (BoardNet.RoutingRole role : BoardNet.RoutingRole.values())
            out.append(';').append(role.name()).append('=').append(widthFor(role));
        return out.append(";units=drawing;edge-clearance=")
            .append(PcbTraceRules.MIN_VISIBLE_CLEARANCE).toString();
    }
}
