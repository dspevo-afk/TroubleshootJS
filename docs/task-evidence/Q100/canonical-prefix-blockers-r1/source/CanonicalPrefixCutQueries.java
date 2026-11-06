package com.lushprojects.circuitjs1.client;

/** Frozen observation coordinates; every predicate still uses the actual Router. */
final class CanonicalPrefixCutQueries {
    static final int COUNT = 22;
    static final int WIDTH = 478;
    static final int HEIGHT = 309;
    static final int CELLS = 147702;
    static final int PITCH = 10;
    static final int ORIGIN_X = 30;
    static final int ORIGIN_Y = 30;
    static final int PRIMARY = 1;
    static final String CUT_INPUT_SHA = "1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5";
    static final String GRAPH_SHA = "ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a";
    static final String PREFIX_SHA = "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final int[] FROM = {114148, 114148, 114148, 114626, 114626, 115104, 115104, 115582, 115582, 115582, 261848, 261848, 261849, 261850, 261851, 261851, 262326, 262326, 262327, 262328, 262329, 262329};
    private static final int[] TO = {113670, 114149, 114147, 114627, 114625, 115105, 115103, 115583, 116060, 115581, 261370, 261847, 261371, 261372, 261373, 261852, 262804, 262325, 262805, 262806, 262330, 262807};
    private static final int[] BITS = {1, 2, 8, 2, 8, 2, 8, 2, 4, 8, 1, 8, 1, 1, 1, 2, 4, 8, 4, 4, 2, 4};
    private CanonicalPrefixCutQueries() { }
    static int fromState(int index) { return FROM[checked(index)]; }
    static int toState(int index) { return TO[checked(index)]; }
    static int bit(int index) { return BITS[checked(index)]; }
    private static int checked(int index) {
        if (index < 0 || index >= COUNT) throw new IllegalArgumentException("Cut query index");
        return index;
    }
}
