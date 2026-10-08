package com.lushprojects.circuitjs1.client;

/** Fixed electrical tangent expectations for local controlled-source baseline reuse. */
public final class ControlledSourceDerivativeContractTest {
    private static int assertions;

    public static void main(String[] args) {
        System.out.println("PASS: controlled source derivative contracts " + run());
    }

    public static int run() {
        assertions = 0;
        CirSim saved = CircuitElm.sim;
        try {
            both("2*a", new double[] { .5 }, new double[] { 0 },
                1, new double[] { 2 }, 0, 0);
            both("3+2*a+4*b", new double[] { 1, 2 }, new double[] { 0, 0 },
                13, new double[] { 2, 4 }, 3, 0);
            both("1+2*a+4*b+8*c+16*d", new double[] { .5, 1, 1.5, 2 },
                new double[] { 0, 0, 0, 0 }, 50, new double[] { 2, 4, 8, 16 }, 1, 0);

            both("a*a+2*b+t", new double[] { 3, 2 }, new double[] { 1, 1 },
                13.25, new double[] { 4, 2 }, -2.75, 0);
            one(true, "a*a+2*b+i+t", new double[] { 3, 2 }, new double[] { 1, 1 },
                16.25, new double[] { 5, 2 }, -2.75, 0);

            // The minimum displacement and negative displacement retain their tangents.
            both("2*a", new double[] { 0 }, new double[] { 0 },
                0, new double[] { 2 }, 0, 1e-12);
            both("2*a", new double[] { .5 }, new double[] { 1 },
                1, new double[] { 2 }, 0, 0);
            one(true, "2*a+i+t", new double[] { .5 }, new double[] { 0 },
                1.75, new double[] { 3 }, .25, 0);
            one(false, "2*a+i+t", new double[] { .5 }, new double[] { 0 },
                1.25, new double[] { -2 }, -.25, 0);

            // These expectations come from the declared piecewise-linear functions.
            // A genuinely flat tangent keeps the existing negative 1e-6 slope guard.
            one(true, "clamp(2*a,0,1)", new double[] { .75 }, new double[] { .5 },
                1, new double[] { -1e-6 }, 1.00000075, 1e-12);
            one(false, "clamp(2*a,0,1)", new double[] { .75 }, new double[] { .5 },
                1, new double[] { -1e-6 }, -.99999925, 1e-12);
            both("select(a-1,2*a,4*a)", new double[] { 1.5 }, new double[] { 1.25 },
                6, new double[] { 4 }, 0, 0);
            both("select(a-1,2*a,4*a)", new double[] { .5 }, new double[] { .25 },
                1, new double[] { 2 }, 0, 0);
            one(true, "min(2*a,4*b)", new double[] { .5, 1 }, new double[] { .25, .5 },
                1, new double[] { 2, -1e-6 }, 1e-6, 1e-12);
            one(false, "min(2*a,4*b)", new double[] { .5, 1 }, new double[] { .25, .5 },
                1, new double[] { -2, -1e-6 }, 1e-6, 1e-12);
            both("pwl(a,0,0,1,2,2,4)", new double[] { .75 }, new double[] { .5 },
                1.5, new double[] { 2 }, 0, 0);

            // Nonfinite stamps remain observable for the solver's existing finite guards.
            both("0/0", new double[] { 0 }, new double[] { 0 },
                Double.NaN, new double[] { Double.NaN }, Double.NaN, 0);
            both("1/0", new double[] { 0 }, new double[] { 0 },
                Double.POSITIVE_INFINITY, new double[] { Double.NaN }, Double.NaN, 0);
            both("-1/0", new double[] { 0 }, new double[] { 0 },
                Double.NEGATIVE_INFINITY, new double[] { Double.NaN }, Double.NaN, 0);
            both("a", new double[] { Double.NaN }, new double[] { 0 },
                Double.NaN, new double[] { Double.NaN }, Double.NaN, 0);
            both("a", new double[] { Double.POSITIVE_INFINITY }, new double[] { 0 },
                Double.POSITIVE_INFINITY, new double[] { Double.NaN }, Double.NaN, 0);
            RecordingCirSim negativeCurrent = one(true, "-0", new double[] { -0.0 },
                new double[] { 0 }, -0.0, new double[] { -1e-6 }, -0.0, 0);
            RecordingCirSim negativeVoltage = one(false, "-0", new double[] { -0.0 },
                new double[] { 0 }, -0.0, new double[] { -1e-6 }, 0.0, 0);
            check(sameBits(negativeCurrent.rhs, -0.0), "CCCS preserves negative-zero RHS");
            check(sameBits(negativeVoltage.rhs, 0.0), "VCCS preserves positive-zero RHS");
        } finally {
            CircuitElm.sim = saved;
        }
        check(CircuitElm.sim == saved, "controlled-source fixture restores the singleton");
        return assertions;
    }

    /** Expected slopes/intercepts use output f; VCCS stamps the negative equivalent. */
    private static void both(String expression, double[] input, double[] previous,
            double output, double[] slopes, double intercept, double tolerance) {
        one(true, expression, input, previous, output, slopes, intercept, tolerance);
        double[] reversed = new double[slopes.length];
        for (int i = 0; i < slopes.length; i++) reversed[i] = -slopes[i];
        one(false, expression, input, previous, output, reversed, -intercept, tolerance);
    }

    private static RecordingCirSim one(boolean currentControlled, String expression,
            double[] input, double[] previous, double output, double[] stampedSlopes,
            double stampedRhs, double tolerance) {
        String label = (currentControlled ? "CCCS " : "VCCS ") + expression;
        RecordingCirSim sim = new RecordingCirSim();
        sim.gridSize = 16;
        sim.t = .25;
        CircuitElm.sim = sim;
        VCCSElm source = currentControlled ? new CCCSElm(0, 0) : new VCCSElm(0, 0);
        try {
            source.inputCount = currentControlled ? input.length * 2 : input.length;
            source.setupPins();
            check(source.csize == 2, label + " native fixture uses the declared full grid");
            check(input.length == previous.length && input.length == stampedSlopes.length,
                label + " independent expectation dimensions");
            for (int i = 0; i < source.nodes.length; i++) source.nodes[i] = 101 + i;
            for (int i = 0; i < input.length; i++) {
                if (currentControlled) {
                    CCCSElm current = (CCCSElm) source;
                    current.pins[2 * i + 1].current = input[i];
                    current.pins[2 * i + 1].voltSource = 201 + i;
                    current.lastCurrents[i] = previous[i];
                } else {
                    source.volts[i] = input[i];
                    source.lastVolts[i] = previous[i];
                }
            }
            ExprParser parser = new ExprParser(expression);
            Expr parsed = parser.parseExpression();
            check(!parser.gotError(), label + " actual parser accepts the expression");
            CountingExpr counting = new CountingExpr(parsed);
            source.expr = counting;
            source.doStep();
            check(counting.evaluations == 1 + input.length,
                label + " one baseline plus one perturbed evaluation per input");
            check(sim.derivatives == input.length && sim.currentStamps == 1,
                label + " emits each derivative and exactly one source RHS");
            int outputPlus = source.nodes[source.inputCount];
            int outputMinus = source.nodes[source.inputCount + 1];
            check(sim.firstOutput == (currentControlled ? outputMinus : outputPlus) &&
                sim.secondOutput == (currentControlled ? outputPlus : outputMinus),
                label + " preserves source polarity");
            for (int i = 0; i < input.length; i++) {
                checkValue(sim.slopes[i], stampedSlopes[i], tolerance, label + " fixed tangent " + i);
                check(sim.controlIds[i] == (currentControlled ? 201 + i : source.nodes[i]),
                    label + " stable derivative control order " + i);
                if (!currentControlled) check(sim.controlReturns[i] == 0,
                    label + " voltage derivative keeps its ground reference");
                check(sameBits(source.exprState.values[i], input[i]),
                    label + " restores expression input " + i);
                double retained = currentControlled ? ((CCCSElm)source).lastCurrents[i] : source.lastVolts[i];
                check(sameBits(retained, input[i]), label + " retains current Newton history " + i);
            }
            if (currentControlled) check(sameBits(source.exprState.values[8], input[0]),
                label + " restores the historical i alias");
            check(sameBits(source.exprState.t, .25), label + " retains expression time");
            checkValue(sim.rhs, stampedRhs, tolerance, label + " fixed affine RHS");
            check(sameBits(source.pins[source.inputCount].current, output) &&
                sameBits(source.pins[source.inputCount + 1].current, -output),
                label + " output currents retain signs and nonfinite values");
            return sim;
        } finally {
            source.delete();
        }
    }

    private static final class CountingExpr extends Expr {
        private final Expr parsed;
        private int evaluations;
        CountingExpr(Expr parsed) { super(Expr.E_VAL, 0); this.parsed = parsed; }
        @Override double eval(ExprState state) { evaluations++; return parsed.eval(state); }
    }

    /** Records production doStep's real stamp calls, with no duplicated derivative code. */
    private static final class RecordingCirSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        final double[] slopes = new double[4];
        final int[] controlIds = new int[4], controlReturns = new int[4];
        int derivatives, currentStamps, firstOutput, secondOutput;
        double rhs;
        @Override void stampCCCS(int first, int second, int source, double gain) {
            derivative(first, second, source, 0, gain);
        }
        @Override void stampVCCurrentSource(int first, int second, int red, int black, double gain) {
            derivative(first, second, red, black, gain);
        }
        private void derivative(int first, int second, int control, int reference, double gain) {
            check(derivatives < slopes.length && currentStamps == 0,
                "derivatives precede source RHS without duplicates");
            if (derivatives == 0) { firstOutput = first; secondOutput = second; }
            else check(firstOutput == first && secondOutput == second, "derivative output polarity is stable");
            slopes[derivatives] = gain;
            controlIds[derivatives] = control;
            controlReturns[derivatives++] = reference;
        }
        @Override void stampCurrentSource(int first, int second, double value) {
            check(first == firstOutput && second == secondOutput, "source RHS preserves derivative polarity");
            currentStamps++;
            rhs = value;
        }
    }

    private static void checkValue(double actual, double expected, double tolerance, String label) {
        check(Double.isNaN(expected) ? Double.isNaN(actual) :
            Double.isInfinite(expected) ? actual == expected : Math.abs(actual - expected) <= tolerance, label);
    }
    private static boolean sameBits(double a, double b) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
