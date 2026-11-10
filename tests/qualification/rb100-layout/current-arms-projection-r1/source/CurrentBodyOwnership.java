package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Point-in-time bounded ownership check for the borrowed current result body. */
public final class CurrentBodyOwnership {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_VISITS = 1000000;

    private CurrentBodyOwnership() { }

    public static void validate(CohortTerminalTelemetryStudy.SetupMeter meter,
            Object body) throws Exception {
        if (meter == null) throw new IOException("BODY_OWNERSHIP_ARGUMENT");
        meter.charge(2);
        Traversal traversal = new Traversal(meter);
        traversal.visit(body, 0, false);
    }

    /** The only retained traversal state: borrowed identities and the shared meter. */
    private static final class Traversal {
        private final CohortTerminalTelemetryStudy.SetupMeter meter;
        private final IdentityHashMap<Object, Boolean> containers;
        private int visits;

        Traversal(CohortTerminalTelemetryStudy.SetupMeter meter) {
            this.meter = meter;
            this.containers = new IdentityHashMap<Object, Boolean>();
            this.visits = 0;
        }

        void visit(Object value, int depth, boolean mapKey) throws Exception {
            meter.charge(1);
            if (depth > MAX_DEPTH) throw new IOException("BODY_OWNERSHIP_DEPTH");

            meter.charge(1);
            if (visits >= MAX_VISITS) throw new IOException("BODY_OWNERSHIP_VISITS");
            visits++;

            meter.charge(1);
            if (mapKey) {
                if (!(value instanceof String)) throw new IOException("BODY_OWNERSHIP_KEY");
                return;
            }
            if (value == null || value instanceof Boolean || value instanceof Integer ||
                    value instanceof Long || value instanceof String) return;
            if (value instanceof Map) {
                visitMap((Map<?, ?>) value, depth);
                return;
            }
            if (value instanceof List) {
                visitList((List<?>) value, depth);
                return;
            }
            throw new IOException("BODY_OWNERSHIP_VALUE");
        }

        private void register(Object container) throws Exception {
            meter.copy(1);
            meter.charge(1);
            Object previous = containers.put(container, Boolean.TRUE);
            meter.charge(1);
            if (previous != null) throw new IOException("BODY_OWNERSHIP_ALIAS");
        }

        private void visitMap(Map<?, ?> map, int depth) throws Exception {
            register(map);

            meter.charge(1);
            Set<? extends Map.Entry<?, ?>> entries = map.entrySet();
            meter.charge(1);
            Iterator<? extends Map.Entry<?, ?>> iterator = entries.iterator();
            while (true) {
                meter.charge(1);
                if (!iterator.hasNext()) break;
                meter.charge(1);
                Map.Entry<?, ?> entry = iterator.next();
                meter.charge(1);
                Object key = entry.getKey();
                visit(key, depth + 1, true);
                meter.charge(1);
                Object child = entry.getValue();
                visit(child, depth + 1, false);
            }
        }

        private void visitList(List<?> list, int depth) throws Exception {
            register(list);

            meter.charge(1);
            Iterator<?> iterator = list.iterator();
            while (true) {
                meter.charge(1);
                if (!iterator.hasNext()) break;
                meter.charge(1);
                Object child = iterator.next();
                visit(child, depth + 1, false);
            }
        }
    }
}
