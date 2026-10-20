package com.lushprojects.circuitjs1.client;

/**
 * Immutable evidence for the one post-selection normal packing probe.
 *
 * <p>The probe is a separate bounded phase.  Its receipt cannot be used to
 * rewrite the primary placement or route counters, and a selected geometry is
 * always rebound to the actual routed layout before admission.</p>
 */
final class PcbPackingProbeReceipt {
    static final int MAX_EXPANSIONS = PcbRoutingWork.Limits.MAX_EXPANSIONS;

    final int attempts;
    final int orderings;
    final int expansions;
    final long millis;
    final String routePolicy;
    final String disposition;
    final String baselineGeometry;
    final String selectedGeometry;

    PcbPackingProbeReceipt(int attempts, int orderings, int expansions, long millis,
            String routePolicy, String disposition, String baselineGeometry,
            String selectedGeometry) {
        if (attempts != 0 && attempts != 1)
            throw new IllegalArgumentException("Invalid normal packing probe attempt count");
        if (orderings < 0 || orderings > SupportedEnvelope.MAX_ROUTE_ATTEMPTS)
            throw new IllegalArgumentException("Normal packing probe orderings exceed its bound");
        if (expansions < 0 || expansions > MAX_EXPANSIONS)
            throw new IllegalArgumentException("Normal packing probe expansions exceed its bound");
        if (millis < 0)
            throw new IllegalArgumentException("Normal packing probe time is negative");
        if (attempts == 0 && (orderings != 0 || expansions != 0 || millis != 0))
            throw new IllegalArgumentException("A skipped normal packing probe has work");
        if (!MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(routePolicy) &&
                !MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(routePolicy))
            throw new IllegalArgumentException("Unknown normal packing probe route policy");
        if (!"NO_CHANGE".equals(disposition) && !"NO_HEADROOM".equals(disposition) &&
                !"KEPT".equals(disposition) && !"SELECTED".equals(disposition) &&
                !"ROUTE_REJECTED".equals(disposition))
            throw new IllegalArgumentException("Unknown normal packing probe disposition");
        if (attempts == 0) {
            if (!"NO_CHANGE".equals(disposition) && !"NO_HEADROOM".equals(disposition))
                throw new IllegalArgumentException("A zero-attempt probe has an invalid disposition");
        } else if (!"KEPT".equals(disposition) && !"SELECTED".equals(disposition) &&
                !"ROUTE_REJECTED".equals(disposition)) {
            throw new IllegalArgumentException("An attempted probe has an invalid disposition");
        }
        requireHash(baselineGeometry, "baseline geometry");
        requireHash(selectedGeometry, "selected geometry");
        if ("SELECTED".equals(disposition)) {
            if (baselineGeometry.equals(selectedGeometry))
                throw new IllegalArgumentException("A selected probe must change geometry");
        } else if (!baselineGeometry.equals(selectedGeometry)) {
            throw new IllegalArgumentException("A non-selected probe must retain baseline geometry");
        }
        this.attempts = attempts;
        this.orderings = orderings;
        this.expansions = expansions;
        this.millis = millis;
        this.routePolicy = routePolicy;
        this.disposition = disposition;
        this.baselineGeometry = baselineGeometry;
        this.selectedGeometry = selectedGeometry;
    }

    String toCanonical() {
        return "normal-packing@2" +
            ";attempts=" + attempts +
            ";orderings=" + orderings +
            ";expansions=" + expansions +
            ";millis=" + millis +
            ";route=" + routePolicy +
            ";disposition=" + disposition +
            ";baseline=" + baselineGeometry +
            ";selected=" + selectedGeometry;
    }

    void requireGeometry(PcbBoardLayout layout) {
        if (layout == null)
            throw new IllegalArgumentException("Normal packing probe requires a routed layout");
        String actual = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        if (!selectedGeometry.equals(actual))
            throw new IllegalArgumentException("Normal packing probe geometry does not match the routed layout");
    }

    private static void requireHash(String value, String label) {
        if (value == null || value.length() != 64)
            throw new IllegalArgumentException("Invalid " + label + " hash");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
                throw new IllegalArgumentException("Invalid " + label + " hash");
        }
    }
}
