/** Independent task-only endpoint observations; does not call the span guard. */
final class TaskPackingSpanObservation {
    private TaskPackingSpanObservation() { }

    static void emit(String phase, TroubleshootBoard board, PcbPlacementPlanner.Plan plan,
            long seed, long routingSeed) {
        java.util.TreeMap<String,PcbPadPlacement> pads = new java.util.TreeMap<String,PcbPadPlacement>();
        for (PcbFootprint footprint : plan.footprints)
            for (PcbPadPlacement pad : footprint.getPads()) {
                if (pads.put(pad.getPadId(), pad) != null)
                    throw new AssertionError("Duplicate task-observer pad");
            }
        Rectangle r = plan.outline;
        StringBuilder canonical = new StringBuilder("task-packing-plan@1;outline=");
        canonical.append(r.x).append(',').append(r.y).append(',')
            .append(r.width).append(',').append(r.height).append(';');
        for (PcbFootprint footprint : plan.footprints)
            canonical.append(footprint.geometryFingerprint()).append('\n');
        String input = canonical.toString();
        StringBuilder out = new StringBuilder("{");
        field(out, "schema", "packing-span-checkpoint@1", false);
        field(out, "phase", phase, true);
        field(out, "inputPlanCanonical", input, true);
        field(out, "inputPlanSha256", PlayerSessionFingerprint.of(input), true);
        field(out, "layoutSeed", Long.toString(seed), true);
        field(out, "routingSeed", Long.toString(routingSeed), true);
        out.append(",\"planCandidate\":").append(plan.candidate);
        out.append(",\"evaluations\":").append(plan.evaluations);
        out.append(",\"planOutline\":[").append(r.x).append(',').append(r.y)
            .append(',').append(r.width).append(',').append(r.height).append(']');
        out.append(",\"netSpans\":[");
        java.util.Vector<String> ids = new java.util.Vector<String>(board.getNetIds());
        java.util.Collections.sort(ids);
        int count = 0;
        for (String id : ids) {
            BoardNet net = board.getNet(id);
            java.util.Vector<String> ends = new java.util.Vector<String>(net.getPadIds());
            if (ends.size() != 2) continue;
            java.util.Collections.sort(ends);
            PcbPadPlacement a = pads.get(ends.get(0)), b = pads.get(ends.get(1));
            if (a == null || b == null) throw new AssertionError("Missing task-observer net endpoint");
            long span = Math.abs((long)a.getX() - b.getX()) + Math.abs((long)a.getY() - b.getY());
            if (count++ > 0) out.append(',');
            out.append('{');
            field(out, "netId", id, false);
            out.append(",\"padIds\":[");quote(out, ends.get(0));out.append(',');quote(out, ends.get(1));out.append(']');
            out.append(",\"centers\":[[").append(a.getX()).append(',').append(a.getY())
                .append("],[").append(b.getX()).append(',').append(b.getY()).append("]]");
            out.append(",\"span\":").append(span).append('}');
        }
        out.append("]}");
        System.out.println("PCB_PACKING_SPAN_CHECKPOINT " + out.toString());
    }

    private static void field(StringBuilder out, String name, String value, boolean comma) {
        if (comma) out.append(',');
        quote(out, name);out.append(':');quote(out, value);
    }

    private static void quote(StringBuilder out, String text) {
        if (text == null) { out.append("null"); return; }
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) {
                String hex = Integer.toHexString(c);out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        out.append('"');
    }
}
