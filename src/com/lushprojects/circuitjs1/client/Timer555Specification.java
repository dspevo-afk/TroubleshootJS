package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** One stock CircuitJS 555 timer in the E07 SIP-8 package, serviceable as a whole chip. */
final class Timer555Specification implements PhysicalSpecification {
    static final Timer555Specification STANDARD = new Timer555Specification();
    /** RESET, VCC, DIS, THRES, TRIG, GND, CTRL, OUT package order to TimerElm post index. */
    private static final int[] POST_FOR_TERMINAL = { 6, 3, 0, 2, 1, 7, 4, 5 };
    /** TimerElm default flags: RESET pin present, GND pin present, full-size chip. */
    static final int FLAGS = 6;

    private Timer555Specification() { }
    public String getSpecificationId() { return "TIMER_555_CIRCUITJS_DEFAULT_E07_V1"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String catalogLabel() { return "555 timer IC (SIP-8)"; }

    static int postForTerminal(int terminal) { return POST_FOR_TERMINAL[terminal]; }

    /** Dump-form construction never reads editor menu state; the new chip's latch starts low. */
    static TimerElm createTimer(int x, int y) {
        TimerElm timer = new TimerElm(x, y, x, y, FLAGS, new StringTokenizer("0"));
        timer.setPoints();
        return timer;
    }

    Vector<CircuitElm> createBacking(int x, int y) {
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(createTimer(x, y));
        return elements;
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage pkg, Vector<CircuitElm> elements) {
        if (spec != STANDARD || pkg != PhysicalPackages.TIMER_SIP_8 || elements == null ||
                elements.size() != 1 || elements.get(0) == null || elements.get(0).getClass() != TimerElm.class)
            throw new IllegalArgumentException("555 timer requires its declared single TimerElm backing");
        TimerElm timer = (TimerElm)elements.get(0);
        if (timer.getPostCount() != 8 || timer.flags != FLAGS)
            throw new IllegalArgumentException("555 timer standard pin configuration changed");
        for (int i = 0; i < 8; i++) for (int j = i + 1; j < 8; j++)
            if (timer.getPost(i).equals(timer.getPost(j)))
                throw new IllegalArgumentException("555 timer external posts overlap");
    }
}
