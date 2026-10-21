/** Task-only observer appended after the unchanged normal exporter. */
final class TaskPackingObservation {
    private TaskPackingObservation() { }

    static void emit(String event, String phase, PcbPlacementPlanner.Plan plan,
            long seed, long routingSeed, String baseline, String policy,
            RuntimeException failure, String kind, String reason, String blockedNet,
            PcbRoutingWork.Statistics recovery) {
        Rectangle r = plan.outline;
        StringBuilder canonical = new StringBuilder("task-packing-plan@1;outline=");
        canonical.append(r.x).append(',').append(r.y).append(',')
            .append(r.width).append(',').append(r.height).append(';');
        for (PcbFootprint footprint : plan.footprints)
            canonical.append(footprint.geometryFingerprint()).append('\n');
        String input = canonical.toString();
        StringBuilder out = new StringBuilder("{");
        field(out, "schema", "packing-rejection-diagnostic@1", false);
        field(out, "event", event, true);
        field(out, "phase", phase, true);
        field(out, "inputPlanCanonical", input, true);
        field(out, "inputPlanSha256", PlayerSessionFingerprint.of(input), true);
        out.append(",\"planCandidate\":").append(plan.candidate);
        out.append(",\"planOutline\":[").append(r.x).append(',').append(r.y)
            .append(',').append(r.width).append(',').append(r.height).append(']');
        field(out, "layoutSeed", Long.toString(seed), true);
        field(out, "routingSeed", Long.toString(routingSeed), true);
        field(out, "routePolicy", policy, true);
        field(out, "baselineGeometrySha256", baseline, true);
        field(out, "exceptionType", failure.getClass().getName(), true);
        field(out, "kind", kind, true);
        field(out, "reason", reason, true);
        field(out, "blockedNet", blockedNet, true);
        field(out, "message", failure.getMessage(), true);
        out.append(",\"recovery\":");
        if (recovery == null) out.append("null");
        else {
            out.append('{');
            field(out, "canonical", recovery.toCanonical(), false);
            out.append(",\"orderingPasses\":").append(recovery.orderingPasses);
            out.append(",\"expansions\":").append(recovery.expansions);
            field(out, "outcome", recovery.outcome.name(), true);
            field(out, "rejectionReason", recovery.rejectionReason == null ? null :
                recovery.rejectionReason.name(), true);
            out.append('}');
        }
        out.append('}');
        System.out.println("PCB_PACKING_REJECTION_DIAGNOSTIC " + out.toString());
    }

    private static void field(StringBuilder out, String name, String value, boolean comma) {
        if (comma) out.append(',');
        quote(out, name);
        out.append(':');
        quote(out, value);
    }

    private static void quote(StringBuilder out, String text) {
        if (text == null) { out.append("null"); return; }
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        out.append('"');
    }
}
