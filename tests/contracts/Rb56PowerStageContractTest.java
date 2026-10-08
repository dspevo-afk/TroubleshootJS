package com.lushprojects.circuitjs1.client;

import java.io.PrintStream;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Actual stage graph only; this does not admit a physical board or bypass runtime readiness. */
public final class Rb56PowerStageContractTest {
    private static final double DT = .00005, POWERED_SECONDS = .4, OFF_SECONDS = 3;
    private static final double HEALTHY_MIN = 10.64, HEALTHY_MAX = 11.76, RIPPLE = .05, DRIFT = .02;
    private static final double RESIDUAL_LIMIT = .25, GMIN_MAX = 1.0001e-12;
    private static int assertions;

    public static void main(String[] args) {
        PrintStream originalError = System.err;
        RoutineConvergenceStream routine = new RoutineConvergenceStream(originalError);
        try {
            System.setErr(routine);
            System.out.println("RB56_POWER_STAGE_CRITERIA {\"dt\":0.00005,\"poweredSeconds\":0.4,\"offSeconds\":3," +
                "\"healthyWindows\":[[0.15,0.20],[0.35,0.40]],\"meanMin\":10.64,\"meanMax\":11.76," +
                "\"rippleMax\":0.05,\"halfDriftMax\":0.02,\"residualLimit\":0.25," +
                "\"primaryBleedOhms\":4700,\"primaryBleedMinWatts\":10,\"outputBleedOhms\":1000,\"outputBleedMinWatts\":0.25," +
                "\"externalLoadsOhms\":[240,48,24],\"noExternalLoad\":true,\"scope\":\"NATIVE_POWER_GRAPH_ONLY\"}");
            for (double rms : new double[] {120, 90}) for (double ohms : new double[] {240, 48, 24})
                runCase(rms, ohms, "HEALTHY");
            for (double rms : new double[] {120, 90}) runCase(rms, 0, "NO_EXTERNAL_LOAD");
            runCase(120, 48, "ENABLE_LEAD_OPEN");
            runCase(120, 48, "PRIMARY_BLEED_REMOVED");
            System.out.println("PASS: RB56 power-stage contracts assertions=" + assertions + " cases=10 scope=NATIVE_POWER_GRAPH_ONLY");
        } finally { System.setErr(originalError); routine.report(); }
    }

    private static void runCase(double rms, double ohms, String variant) {
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        String previousDiode = DiodeElm.lastModelName, previousZener = ZenerElm.lastZenerModelName;
        String previousTransistor = TransistorElm.lastModelName;
        TestCirSim sim = new TestCirSim(DT);
        Vector<CircuitElm> original = sim.elmList, owned = new Vector<CircuitElm>();
        Rb56PowerStage stage = null;
        Result result = new Result(rms, ohms, variant);
        Throwable failure = null;
        CircuitElm.sim = sim;
        try {
            stage = new Rb56PowerStage(0, 0); owned.addAll(stage.elements());
            Vector<CircuitElm> graph = new Vector<CircuitElm>(owned);
            // Zero selects an absent external test load, never an infinite resistor.
            if (ohms > 0) {
                ResistorElm load = end(new ResistorElm(2048, 160), new Point(2048, 384));
                load.setResistance(ohms); graph.add(load); owned.add(load);
                addWire(graph, owned, stage.outputPlus, load.getPost(0));
                addWire(graph, owned, stage.outMinus, load.getPost(1));
            }
            GroundElm ground = end(new GroundElm(-256, 256), new Point(-256, 288));
            graph.add(ground); owned.add(ground);
            stage.input.source.maxVoltage = rms * Math.sqrt(2);
            check(stage.input.impedance.resistance == 22 && !stage.boardElements().contains(stage.input.impedance),
                "22 ohms is actual external source impedance, excluded from the board");
            check(stage.module.elements().size() == 4 && stage.module.elements() != stage.module.elements(),
                "module returns a fresh four-element backing vector");
            for (int n = 0; n < 7; n++) {
                CircuitPostMeasurementEndpoint terminal = stage.module.terminal(n);
                check(terminal.getElement() == (n == 6 ? stage.module.bias : stage.module.converter) &&
                    terminal.getPostIndex() == (n == 6 ? 2 : n), "module terminal has the actual declared owner/post");
                Point actual = terminal.getElement().getPost(terminal.getPostIndex());
                Point leadEnd = stage.moduleLeads[n].getPost(1), anchor = stage.moduleLeads[n].getPost(0);
                check(actual.x == leadEnd.x && actual.y == leadEnd.y &&
                    (anchor.x != actual.x || anchor.y != actual.y), "separate actual module lead binds the service terminal");
            }
            check(stage.primaryBleed.resistance == 4700 && Rb56PowerStage.PRIMARY_BLEED_MIN_WATTS >= 10,
                "primary bleeder is the predeclared E12/10W candidate");
            check(stage.outputBleed.resistance == 1000 && Rb56PowerStage.OUTPUT_BLEED_MIN_WATTS >= .25,
                "output bleeder is the predeclared 1k/.25W component independent of external loading");
            if ("ENABLE_LEAD_OPEN".equals(variant)) check(graph.remove(stage.enableLead), "remove actual EN lead");
            if ("PRIMARY_BLEED_REMOVED".equals(variant)) check(graph.remove(stage.primaryBleed), "remove actual primary bleeder");
            sim.elmList = graph; sim.a01MeasurementRunning = true; sim.solverExecutor.analyze();
            int poweredSteps = steps(POWERED_SECONDS);
            for (int n = 0; n < poweredSteps; n++) {
                sim.solverExecutor.advanceSteps(1); observeSolver(sim, stage, result);
                double output = outputVoltage(stage), input = diff(stage.bulk, 0, 1);
                double bias = diff(stage.module.bias, 2, 1), enable = stage.module.converter.getEnableVoltage();
                check(finite(output) && finite(input) && finite(bias) && finite(enable), "finite accepted power/control voltages");
                result.outputMax = Math.max(result.outputMax, output);
                double biasIn = stage.module.bias.getInputCurrent(), biasOut = stage.module.bias.getOutputCurrent();
                check(finite(biasIn) && finite(biasOut) && biasIn >= -1e-9 &&
                    Math.abs(biasIn - Math.max(0, biasOut)) <= 1e-9, "actual powered bias draws its positive delivered current");
                check(input * biasIn - bias * biasOut >= -1e-9, "bias is passive at the accepted terminal boundary");
                double inputPower = input * (stage.module.converter.getInputCurrent() + biasIn);
                double outputPower = stage.module.converter.getOutputVoltage() *
                    stage.module.converter.getCurrentIntoNode(E06ConverterContract.PRE_L_PLUS);
                check(finite(inputPower) && finite(outputPower), "finite independent instantaneous stage power products");
                result.stageInputJ += inputPower * DT; result.preLDeliveredJ += outputPower * DT;
                result.biasInputJ += input * biasIn * DT;
                if (stage.module.converter.isActive()) {
                    result.activeSamples++;
                    if (Double.isNaN(result.firstActiveTime)) {
                        result.firstActiveTime = sim.t; result.firstActiveBulkV = input;
                        result.firstActiveBiasV = bias; result.firstActiveEnableV = enable;
                        check(input >= E06ConverterContract.UVLO_OFF_VOLTS && enable > E06ConverterContract.ENABLE_LOW_VOLTS && bias > 3,
                            "accepted startup is caused by actual bulk-powered bias/enable");
                    }
                }
                if (n >= steps(.15) && n < steps(.20)) result.early.add(output);
                if (n >= steps(.35) && n < steps(.40)) result.late.add(output);
            }
            check(sim.a01AcceptedStepCount == poweredSteps && Math.abs(sim.t - POWERED_SECONDS) < 1e-10,
                "exact bounded accepted startup timeline");
            result.poweredBulkV = diff(stage.bulk, 0, 1);
            if ("ENABLE_LEAD_OPEN".equals(variant)) {
                check(result.activeSamples == 0 && result.outputMax <= RESIDUAL_LIMIT,
                    "actual EN open prevents startup despite powered bias");
                check(diff(stage.module.bias, 2, 1) > 3 && stage.module.converter.getEnableVoltage() < 2,
                    "open enable separates a powered bias from the actual control input");
            } else {
                check(result.activeSamples > 0 && result.stageInputJ > 0 && result.preLDeliveredJ > 0 && result.biasInputJ > 0,
                    "actual module and external powered bias deliver energy after startup");
                result.early.validate("150-200 ms"); result.late.validate("350-400 ms");
            }
            stage.input.binding.setConnected(false);
            check(!stage.input.binding.isConnected() && stage.input.linePole.position == 1 && stage.input.returnPole.position == 1,
                "one external source control opens both actual poles");
            double retainedBulk = diff(stage.bulk, 0, 1), retainedOutput = diff(stage.outputCap, 0, 1);
            sim.solverExecutor.invalidate(); sim.solverExecutor.analyze();
            check(diff(stage.bulk, 0, 1) == retainedBulk && diff(stage.outputCap, 0, 1) == retainedOutput,
                "source OFF and reanalysis retain capacitor history without reset");
            for (int n = 0; n < steps(OFF_SECONDS); n++) {
                sim.solverExecutor.advanceSteps(1); observeSolver(sim, stage, result);
                double residual = residualVoltage(stage);
                check(finite(residual), "finite accepted OFF residual voltage");
                if (residual <= RESIDUAL_LIMIT && Double.isNaN(result.firstVoltageReadyTime))
                    result.firstVoltageReadyTime = sim.t - POWERED_SECONDS;
                if (!Double.isNaN(result.firstVoltageReadyTime)) result.afterReadyMax = Math.max(result.afterReadyMax, residual);
            }
            result.finalResidualV = residualVoltage(stage); result.finalBulkV = diff(stage.bulk, 0, 1);
            result.finalOutputV = outputVoltage(stage); result.finalInductorA = stage.outputInductor.getCurrent();
            result.finalTime = sim.t; check(finite(result.finalInductorA), "finite actual OFF inductor current");
            if ("PRIMARY_BLEED_REMOVED".equals(variant)) {
                check(result.finalBulkV > RESIDUAL_LIMIT && Double.isNaN(result.firstVoltageReadyTime),
                    "missing actual primary bleeder retains charge and prevents measured voltage readiness");
            } else {
                check(finite(result.firstVoltageReadyTime) && result.finalResidualV <= RESIDUAL_LIMIT &&
                    result.afterReadyMax <= RESIDUAL_LIMIT, "accepted discharge reaches and retains the unchanged voltage-ready bound");
            }
            check(sim.a01AcceptedStepCount == poweredSteps + steps(OFF_SECONDS) &&
                Math.abs(sim.t - POWERED_SECONDS - OFF_SECONDS) < 1e-9 && sim.stopMessage == null,
                "fixed physical tail completes with no reset or hidden extra advancement");
            System.out.println("RB56_POWER_STAGE " + result.report("PASS"));
        } catch (RuntimeException error) { failure = error; System.out.println("RB56_POWER_STAGE " + result.report("FAIL")); throw error;
        } catch (Error error) { failure = error; System.out.println("RB56_POWER_STAGE " + result.report("FAIL")); throw error;
        } finally {
            Throwable cleanup = null; boolean disposed = true;
            try {
                sim.a01MeasurementRunning = false; sim.elmList = original;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; } catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null;
                for (CircuitElm element : owned) try { element.delete(); }
                catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = previousDiode; ZenerElm.lastZenerModelName = previousZener;
                TransistorElm.lastModelName = previousTransistor; CircuitElm.sim = previous; CirSim.theSim = previousSingleton;
            }
            boolean clean = cleanup == null && disposed && sim.elmList == original && original.isEmpty() &&
                sim.circuitMatrix == null && !sim.solverExecutor.isUnavailable() && CircuitElm.sim == previous && CirSim.theSim == previousSingleton;
            System.out.println("RB56_POWER_STAGE_CLEANUP {\"case\":\"" + result.id + "\",\"rawGraphRestored\":" +
                (sim.elmList == original && original.isEmpty()) + ",\"candidateDisposed\":" + disposed +
                ",\"singletonRestored\":" + (CircuitElm.sim == previous && CirSim.theSim == previousSingleton) + ",\"clean\":" + clean + "}");
            if (failure != null) {
                if (cleanup != null) failure.addSuppressed(cleanup);
                if (!clean) failure.addSuppressed(new AssertionError("RB56 stage cleanup failed"));
            } else { if (cleanup != null) throw new AssertionError("RB56 stage cleanup failed", cleanup); check(clean, "exact graph/singleton disposal cleanup"); }
        }
    }

    private static void observeSolver(CirSim sim, Rb56PowerStage stage, Result result) {
        result.maxSubIterations = Math.max(result.maxSubIterations, sim.subIterations);
        double gmin = stage.opto.transistor.gmin;
        result.maxOptoGmin = Math.max(result.maxOptoGmin, gmin);
        if (gmin > GMIN_MAX) result.elevatedOptoGminSamples++;
        check(finite(gmin) && gmin <= GMIN_MAX && result.elevatedOptoGminSamples == 0,
            "accepted optocoupler never requires elevated numerical gmin");
    }
    private static double residualVoltage(Rb56PowerStage s) {
        double reference = s.bulk.getPostVoltage(1);
        double result = Math.max(Math.abs(diff(s.bulk, 0, 1)), Math.abs(outputVoltage(s)));
        result = Math.max(result, Math.abs(s.input.linePole.getPostVoltage(1) - reference));
        result = Math.max(result, Math.abs(s.input.returnPole.getPostVoltage(1) - reference));
        result = Math.max(result, Math.abs(s.fuse.getPostVoltage(1) - reference));
        result = Math.max(result, Math.abs(diff(s.biasCap, 0, 1)));
        result = Math.max(result, Math.abs(diff(s.feedbackComp, 0, 1)));
        result = Math.max(result, Math.abs(diff(s.outputCap, 0, 1)));
        result = Math.max(result, Math.abs(s.module.converter.getEnableVoltage()));
        result = Math.max(result, Math.abs(s.module.converter.getFeedbackVoltage()));
        result = Math.max(result, Math.abs(diff(s.module.bias, 2, 1)));
        result = Math.max(result, Math.abs(s.module.converter.getOutputVoltage()));
        return result;
    }
    private static double outputVoltage(Rb56PowerStage s) { return s.capacitorEsr.getPostVoltage(0) - s.outputCap.getPostVoltage(1); }
    private static double diff(CircuitElm e, int a, int b) { return e.getPostVoltage(a) - e.getPostVoltage(b); }
    private static int steps(double seconds) { return (int)Math.round(seconds / DT); }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static String number(double value) { return finite(value) ? Double.toString(value) : "null"; }
    private static void check(boolean passed, String message) { assertions++; if (!passed) throw new AssertionError("RB56 power-stage: " + message); }
    private static <T extends CircuitElm> T end(T e, Point to) { e.x2 = to.x; e.y2 = to.y; e.setPoints(); return e; }
    private static void addWire(Vector<CircuitElm> graph, Vector<CircuitElm> owned, Point from, Point to) {
        WireElm w = end(new WireElm(from.x, from.y), to); graph.add(w); owned.add(w);
    }

    private static final class Window {
        int count; double sum, first, second, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        void add(double voltage) { if (count < 500) first += voltage; else second += voltage; count++; sum += voltage; min = Math.min(min, voltage); max = Math.max(max, voltage); }
        double mean() { return sum / count; }
        double ripple() { return (max - min) / Math.abs(mean()); }
        double drift() { return Math.abs(first / 500 - second / 500) / Math.abs(mean()); }
        void validate(String name) {
            check(count == 1000, "exact accepted healthy window samples: " + name);
            check(mean() >= HEALTHY_MIN && mean() <= HEALTHY_MAX, "predeclared healthy output mean: " + name);
            check(ripple() <= RIPPLE, "predeclared healthy ripple: " + name);
            check(drift() <= DRIFT, "predeclared healthy drift: " + name);
        }
        String report() { return "{\"meanV\":" + number(mean()) + ",\"minV\":" + number(min) + ",\"maxV\":" + number(max) + ",\"ripple\":" + number(ripple()) + ",\"drift\":" + number(drift()) + "}"; }
    }
    private static final class Result {
        final String id; final Window early = new Window(), late = new Window();
        int activeSamples, maxSubIterations, elevatedOptoGminSamples;
        double outputMax, stageInputJ, preLDeliveredJ, biasInputJ, maxOptoGmin;
        double firstActiveTime = Double.NaN, firstActiveBulkV = Double.NaN, firstActiveBiasV = Double.NaN, firstActiveEnableV = Double.NaN;
        double firstVoltageReadyTime = Double.NaN, afterReadyMax, poweredBulkV = Double.NaN;
        double finalResidualV = Double.NaN, finalBulkV = Double.NaN, finalOutputV = Double.NaN, finalInductorA = Double.NaN, finalTime = Double.NaN;
        Result(double rms, double load, String variant) { id = variant + "_" + (int)rms + "VRMS_" + (load > 0 ? (int)load + "OHMS" : "NO_LOAD"); }
        String report(String status) { return "{\"case\":\"" + id + "\",\"status\":\"" + status + "\",\"early\":" + early.report() + ",\"late\":" + late.report() +
            ",\"firstActiveTime\":" + number(firstActiveTime) + ",\"firstActiveBulkV\":" + number(firstActiveBulkV) + ",\"firstActiveBiasV\":" + number(firstActiveBiasV) +
            ",\"firstActiveEnableV\":" + number(firstActiveEnableV) + ",\"activeSamples\":" + activeSamples + ",\"outputMaxV\":" + number(outputMax) +
            ",\"maxSubIterations\":" + maxSubIterations + ",\"maxOptoGmin\":" + number(maxOptoGmin) + ",\"elevatedOptoGminSamples\":" + elevatedOptoGminSamples +
            ",\"stageInputJ\":" + number(stageInputJ) + ",\"preLDeliveredJ\":" + number(preLDeliveredJ) + ",\"biasInputJ\":" + number(biasInputJ) +
            ",\"poweredBulkV\":" + number(poweredBulkV) + ",\"firstVoltageReadyAfterOffSeconds\":" + number(firstVoltageReadyTime) +
            ",\"finalResidualV\":" + number(finalResidualV) + ",\"finalBulkV\":" + number(finalBulkV) + ",\"finalOutputV\":" + number(finalOutputV) +
            ",\"finalInductorA\":" + number(finalInductorA) + ",\"finalTime\":" + number(finalTime) + "}"; }
    }
    private static final class RoutineConvergenceStream extends PrintStream {
        private final PrintStream original; private long count; private int max;
        private final Pattern pattern = Pattern.compile("^converged after ([0-9]+) iterations, timeStep = ([0-9.E+-]+)$");
        RoutineConvergenceStream(PrintStream original) { super(original, true); this.original = original; }
        @Override public synchronized void println(String line) {
            Matcher m = line == null ? null : pattern.matcher(line);
            if (m != null && m.matches()) try {
                int iterations = Integer.parseInt(m.group(1)); double dt = Double.parseDouble(m.group(2));
                if (iterations >= 1 && iterations <= 5000 && dt == DT) { count++; max = Math.max(max, iterations); return; }
            } catch (NumberFormatException unknown) { /* Preserve unknown messages. */ }
            original.println(line);
        }
        void report() { original.println("RB56_ROUTINE_CONVERGENCE {\"count\":" + count + ",\"maxIterations\":" + max + "}"); }
    }
    /** Exact production executor with UI-only native adaptation. */
    private static final class TestCirSim extends CirSim {
        TestCirSim(double dt) {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = dt; adjustTimeStep = false;
        }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm e) { stopMessage = message; circuitMatrix = null; stopElm = e; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}