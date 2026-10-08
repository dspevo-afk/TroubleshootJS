package com.lushprojects.circuitjs1.client;

/** Immutable candidate declaration for one native model comparison, not a qualified supply. */
final class E06ConverterContract {
    static final int IN_PLUS = 0, IN_MINUS = 1, PRE_L_PLUS = 2, OUT_MINUS = 3, ENABLE = 4, FEEDBACK = 5;
    static final double SWITCHING_HZ = 2000, PERIOD_SECONDS = 1 / SWITCHING_HZ, MAX_DUTY = .45;
    static final double BIAS_VOLTS = 10, BIAS_RESISTANCE_OHMS = 100, GATE_RESISTANCE_OHMS = 10;
    static final double POWER_SWITCH_THRESHOLD_VOLTS = 3;
    static final double POWER_SWITCH_ON_OHMS = 1 / (.5 * (BIAS_VOLTS - POWER_SWITCH_THRESHOLD_VOLTS));
    static final double POWER_SWITCH_OFF_OHMS = 100000000;
    static final double SOFT_START_SECONDS = .020, UVLO_ON_VOLTS = 95, UVLO_OFF_VOLTS = 90;
    static final double ENABLE_HIGH_VOLTS = 3, ENABLE_LOW_VOLTS = 2;
    static final double FEEDFORWARD_VOLTS = 12, FEEDBACK_REFERENCE_VOLTS = 6;
    static final double CONTROL_KP = .00025, CONTROL_KI = .025;
    static final double SECONDARY_OVER_PRIMARY_TURNS = .25, PRIMARY_HENRIES = .2, COUPLING = .999;
    static final double OUTPUT_RESISTANCE_OHMS = .5, MAX_OUTPUT_CURRENT_AMPS = 1, EFFICIENCY = .85;
    static final double QUIESCENT_AMPS = .00015;
    static final double INPUT_LEAK_OHMS = 1000000, CONTROL_LEAK_OHMS = 1000000, OUTPUT_LEAK_OHMS = 1000000;
    static final double MAX_DETAILED_STEP_SECONDS = .000005, MAX_AVERAGED_STEP_SECONDS = .00005;
    static final double OUTPUT_HENRIES = .020, WINDING_OHMS = .5, OUTPUT_FARADS = .000470;
    static final double ESR_OHMS = .1, LOAD_OHMS = 48, BLEED_OHMS = 10000;
    static final double SENSE_OHMS = 680, ZENER_VOLTS = 10.7, PULLUP_OHMS = 10000, COMPENSATION_FARADS = .000001;

    E06ConverterContract() { }

    static boolean nextActive(boolean prior, double input, double enable) {
        requireFinite(input); requireFinite(enable);
        return prior ? input > UVLO_OFF_VOLTS && enable > ENABLE_LOW_VOLTS :
            input >= UVLO_ON_VOLTS && enable >= ENABLE_HIGH_VOLTS;
    }

    /** Shared pure control law; each element owns and commits its own integral. */
    static double nextIntegral(double previous, double input, double feedback,
            double activeSeconds, double dt, boolean active) {
        requireFinite(previous); requireFinite(input); requireFinite(feedback);
        requireFinite(activeSeconds); requireFinite(dt);
        if (dt <= 0) throw new IllegalArgumentException("Nonpositive E06 control timestep");
        if (!active || activeSeconds <= SOFT_START_SECONDS) return 0;
        double error = feedback - FEEDBACK_REFERENCE_VOLTS;
        double raw = unsaturatedDuty(input, feedback, previous);
        double increment = CONTROL_KI * error * dt;
        // Only integrate toward the admissible range when the duty is saturated.
        if (raw >= MAX_DUTY && increment > 0 || raw <= 0 && increment < 0) return previous;
        return clamp(previous + increment, -MAX_DUTY, MAX_DUTY);
    }

    static double dutyFor(double input, double feedback, double activeSeconds, double integral) {
        requireFinite(activeSeconds);
        return clamp(unsaturatedDuty(input, feedback, integral), 0, MAX_DUTY) *
            clamp(activeSeconds / SOFT_START_SECONDS, 0, 1);
    }

    private static double unsaturatedDuty(double input, double feedback, double integral) {
        requireFinite(input); requireFinite(feedback); requireFinite(integral);
        if (input <= 0) throw new IllegalArgumentException("Nonpositive active E06 input");
        // Feedforward requests duty from actual input; it never supplies output power.
        double result = FEEDFORWARD_VOLTS / (SECONDARY_OVER_PRIMARY_TURNS * input) +
            CONTROL_KP * (feedback - FEEDBACK_REFERENCE_VOLTS) + integral;
        requireFinite(result); return result;
    }

    static double quiescentFor(double input) {
        requireFinite(input);
        return QUIESCENT_AMPS * clamp(input / BIAS_VOLTS, 0, 1);
    }

    static long cycleIndex(double time) {
        requireFinite(time);
        if (time < 0) throw new IllegalArgumentException("Negative E06 solver time");
        return (long)Math.floor((time + 1e-12) / PERIOD_SECONDS);
    }

    static double cyclePhaseSeconds(double time) {
        return Math.max(0, time - cycleIndex(time) * PERIOD_SECONDS);
    }

    static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    static void requireFinite(double value) {
        if (!finite(value)) throw new IllegalArgumentException("Nonfinite E06 candidate value");
    }

    String canonical() {
        return "e06-native-candidate-v3;ports=IN+,IN-,PRE_L+,OUT-,EN,FB;control-reference=IN-;" +
            "uvlo=95/90;enable=3/2;hz=2000;control=PI-input-feedforward;FFcommand=12;FBref=6;Kp=.00025;Ki=.025;" +
            "integral-bound=plus-minus.45;integral-zero-disabled-or-softstart;conditional-antiwindup;softstart=.020;nominal-output-not-prescribed;" +
            "accepted-cycle-latch;bias=10V/100ohm-linear-powered;gate=10V/10ohm;Iq=.00015*clamp(Vin/10);" +
            "Lp=.2;n=.25;k=.999;Lout=.020;Rw=.5;Cout=.000470;ESR=.1;load=48;bleed=10000;" +
            "detailed-transformer-integration=backward-euler;output-L-integration=trapezoidal;BE-damping=numerical-not-physical;" +
            "detailed-channel=finite-Ron-Roff,differential-Vgs-threshold3;Ron=2/7-ohm-derived-beta.5-drive10-threshold3;Roff=1e8-ohm;actual-body-junctions;" +
            "body-current=existing-raw-Shockley-estimate;body-stamp=existing-limited-tangent-plus-gmin;" +
            "sense=680;zener=10.7;opto=default-junction,default-BJT,beta700,existing-empirical-transfer;pullup=10000;Cfb=.000001;" +
            "averaged-emf=d*n*Vin;Rout=.5;Imax=1;eta=.85-positive-terminal-power;I2R-once;negative-power-absorbed;" +
            "candidate-no-nominal-qualification;averaged-omits-junction-drop-average-and-switching-waveforms;" +
            "detailed-omits-MOSFET-transfer,saturation,avalanche,gate-charge,switching-loss,EMI,thermal;2kHz-solver-only-player-limit250Hz";
    }
}
