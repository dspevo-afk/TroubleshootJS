package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

/** Bounded optimal adaptive reading count over a certified observation population.
 * Overlapping tolerance neighborhoods are conservative outcomes, not forced partitions.
 * This describes the declared measurement set, never every possible human strategy. */
final class DiagnosticReduction {
    private final boolean[][][] same;
    private final int[] repairs;
    private final HashMap<Integer, Integer> memo = new HashMap<Integer, Integer>();
    final int readings;
    final int bestSingleRemaining;

    DiagnosticReduction(boolean[][][] observations, int[] repairClasses) {
        if (repairClasses == null || repairClasses.length < 1 || repairClasses.length > 12 ||
                observations == null || observations.length == 0 || observations.length > 512)
            throw new IllegalArgumentException("Difficulty observation population is outside its bound");
        repairs = new int[repairClasses.length];
        System.arraycopy(repairClasses, 0, repairs, 0, repairs.length);
        same = new boolean[observations.length][repairs.length][repairs.length];
        for (int m = 0; m < same.length; m++) {
            if (observations[m] == null || observations[m].length != repairs.length)
                throw new IllegalArgumentException("Difficulty observation schema mismatch");
            for (int a = 0; a < repairs.length; a++) {
                if (observations[m][a] == null || observations[m][a].length != repairs.length)
                    throw new IllegalArgumentException("Difficulty observation schema mismatch");
                for (int b = 0; b < repairs.length; b++) same[m][a][b] = observations[m][a][b];
            }
            for (int a = 0; a < repairs.length; a++) for (int b = 0; b < repairs.length; b++)
                if (!same[m][a][a] || same[m][a][b] != same[m][b][a])
                    throw new IllegalArgumentException("Invalid observation comparison");
        }
        int all = (1 << repairs.length) - 1;
        int remaining = repairs.length;
        for (int m = 0; m < same.length; m++) {
            int worst = 0;
            for (int a = 0; a < repairs.length; a++) worst = Math.max(worst, classCount(neighbors(all, m, a)));
            remaining = Math.min(remaining, worst);
        }
        bestSingleRemaining = remaining;
        readings = depth(all);
        if (readings > repairs.length) throw new IllegalArgumentException("Legal repairs cannot be distinguished by the proved readings");
    }

    private int neighbors(int subset, int meter, int hypothesis) {
        int result = 0;
        for (int b = 0; b < repairs.length; b++)
            if ((subset & (1 << b)) != 0 && same[meter][hypothesis][b]) result |= 1 << b;
        return result;
    }
    private int classCount(int subset) {
        int count = 0;
        for (int a = 0; a < repairs.length; a++) if ((subset & (1 << a)) != 0) {
            boolean seen = false;
            for (int b = 0; b < a; b++) if ((subset & (1 << b)) != 0 && repairs[a] == repairs[b]) seen = true;
            if (!seen) count++;
        }
        return count;
    }
    private int depth(int subset) {
        if (classCount(subset) <= 1) return 0;
        Integer cached = memo.get(subset);
        if (cached != null) return cached.intValue();
        int best = repairs.length + 1;
        for (int m = 0; m < same.length; m++) {
            int worst = 0;
            for (int a = 0; a < repairs.length; a++) if ((subset & (1 << a)) != 0) {
                int next = neighbors(subset, m, a);
                if (next == subset) { worst = repairs.length; break; }
                worst = Math.max(worst, depth(next));
            }
            best = Math.min(best, 1 + worst);
        }
        memo.put(subset, Integer.valueOf(best)); return best;
    }
}
