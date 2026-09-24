package com.lushprojects.circuitjs1.client;

/** Scratch-only Newton trace. Never compiled into production. */
final class Q30DebugTrace {
    static boolean enabled;
    static String scenario;
    static String phase;

    private static boolean sample(CirSim sim) {
        if (!enabled) return false;
        if ("cold".equals(scenario))
            return sim.t == 0.0 && (sim.subIterations < 18 ||
                sim.subIterations % 500 == 0);
        return "transition".equals(phase) && sim.t >= .03182 &&
            sim.t <= .03190 && (sim.subIterations < 18 ||
                sim.subIterations % 500 == 0);
    }

    static void setter(CirSim sim, CircuitElm element) {
        if (sample(sim)) System.out.println("SETTER t=" + sim.t +
            " dt=" + sim.timeStep + " iter=" + sim.subIterations +
            " element=" + element.getClass().getSimpleName());
    }

    static void rail(CirSim sim, double vin, double vout, double enable,
            double target, double evalDrop, double requestedDrop,
            double delivered, double conductance, double targetInputSlope,
            double targetEnableSlope, double inputCurrent, double inputGain,
            double outputGain, double enableGain) {
        if (sample(sim)) System.out.println("RAIL t=" + sim.t +
            " dt=" + sim.timeStep + " iter=" + sim.subIterations +
            " vin=" + vin + " vout=" + vout + " enable=" + enable +
            " target=" + target + " drop=" + evalDrop +
            " requested=" + requestedDrop + " delivered=" + delivered +
            " g=" + conductance + " dtarget_dvin=" + targetInputSlope +
            " dtarget_den=" + targetEnableSlope + " iin=" + inputCurrent +
            " diin_dvin=" + inputGain + " diin_dvout=" + outputGain +
            " diin_den=" + enableGain);
    }

    static void decision(CirSim sim, CircuitElm element, double sensor,
            double reference, double rail, Object previousState, Object state,
            boolean previousHigh, boolean high, double previousOutput,
            double output) {
        if (sample(sim)) System.out.println("DECISION t=" + sim.t +
            " dt=" + sim.timeStep + " iter=" + sim.subIterations +
            " id=" + element.x + " sensor=" + sensor +
            " ref=" + reference + " rail=" + rail +
            " before=" + previousState + "/" + previousHigh +
            " after=" + state + "/" + high +
            " output=" + previousOutput + "->" + output);
    }
}
