package com.lushprojects.circuitjs1.client;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Focused matched electrical matrix; native results do not admit a physical/player family. */
public final class E06ConverterPilotContractTest {
    private static final double DETAILED_DT = .000005, AVERAGED_DT = .0002;
    private static final String FIDELITY = "NOMINAL_FIDELITY_NOT_QUALIFIED";
    private static final String[] CHANNELS = {"sourceV", "sourceI", "bulkV", "outputV", "loadI",
        "primaryV", "primaryI", "secondaryV", "secondaryI", "ledI", "collectorI", "feedbackV",
        "highVgs", "lowVgs", "duty", "enableV", "biasInputI", "pwmInputI", "activeSeconds", "phaseSeconds",
        "outputCapV", "preLV", "outputInductorI", "resetLowerI", "resetUpperI", "forwardI", "freewheelI",
        "highDrainI", "highBodyToDrainI", "highChannelI", "lowDrainI", "lowBodyToDrainI", "lowChannelI", "biasOutputI", "controlIntegral",
        "stageInputI", "stageOutputI", "stageInputPower", "stageOutputPower", "averagedInputI"};
    // Exact criteria frozen before the matrix: task e06/fidelity-criteria-v1.json.
    private static final double DT_MEAN_REL = .02, MODEL_MEAN_REL = .05, MODEL_INPUT_REL = .15;
    private static final double STARTUP_DELTA = .010, CLOSURE_REL = .02, CLOSURE_J = .000010;
    private static final double REF_V = .001, REF_POWER_REL = .001, REF_POWER_W = .000001;
    private static final double GMIN_MAX = 1.0001e-12, SINGLE_BOND_MAX_A = 1e-9;
    private static final String[] REFERENCE_NAMES = {"IN_MINUS", "OUT_MINUS", "AUTOMATIC", "REVERSED_IN_MINUS",
        "PRIMARY_PLUS_10V", "SECONDARY_PLUS_10V", "ONE_BOND_NO_RETURN", "TWO_BONDS_CLOSED_RETURN"};
    private static final double SETTLED_MIN = 10.64, SETTLED_MAX = 11.76, RIPPLE_REL = .05, DRIFT_REL = .02;
    private static final String[] PHASE_NAMES = {"nominal150to200ms", "loaded300to350ms", "reducedLine450to500ms",
        "disabled500to600ms", "reenabled600to700ms", "isolated700to1200ms", "faultSettled350to400ms", "faultTail200to400ms"};
    private static final double[] PHASE_START = {.15, .30, .45, .50, .60, .70, .35, .20};
    private static final double[] PHASE_END = {.20, .35, .50, .60, .70, 1.20, .40, .40};
    private static int assertions;

    private E06ConverterPilotContractTest() { }

    public static void main(String[] args) {
        PrintStream originalError = System.err;
        RoutineConvergenceStream routine = new RoutineConvergenceStream(originalError);
        try {
            System.setErr(routine);
            runMatrix();
        } finally {
            System.setErr(originalError); // Restore before any uncaught failure prints its stack trace.
            routine.report();
        }
    }

    private static void runMatrix() {
        Diagnostics[][] sequence = new Diagnostics[2][2];
        for (int model = 0; model < 2; model++) for (int fine = 0; fine < 2; fine++)
            sequence[model][fine] = runCase(new Case(model == 1, fine == 1, "SEQUENCE", 0, 1.2));
        for (int model = 0; model < 2; model++) {
            compare(sequence[model][0], sequence[model][1], true);
            runCase(new Case(model == 1, false, "SENSE_OPEN", 0, .4));
            runCase(new Case(model == 1, false, "FUSE_BLOWN", 0, .4));
            for (int reference = 0; reference < 8; reference++) {
                Diagnostics candidate = runCase(new Case(model == 1, false, "REFERENCE", reference, .2));
                if (reference < 6) compareReference(sequence[model][0], candidate);
            }
        }
        compare(sequence[0][0], sequence[1][0], false);
        compare(sequence[0][1], sequence[1][1], false);
        System.out.println("PASS: E06 nominal pilot mechanics assertions=" + assertions + " fidelity=" + FIDELITY +
            " cases=24 scope=FOCUSED_ELECTRICAL_MATRIX criteria=e06-fidelity-criteria-v1");
    }

    /** Aggregate only recognized informational solver lines; all other output uses the original stream. */
    private static final class RoutineConvergenceStream extends PrintStream {
        private final PrintStream original;
        private final Pattern pattern = Pattern.compile("^converged after ([0-9]+) iterations, timeStep = ([0-9.E+-]+)$");
        private final Map<String, Long> counts = new LinkedHashMap<String, Long>();
        private long total;
        RoutineConvergenceStream(PrintStream original) { super(original, true); this.original = original; }
        @Override public synchronized void println(String value) {
            Matcher match = value == null ? null : pattern.matcher(value);
            if (match != null && match.matches()) try {
                int iterations = Integer.parseInt(match.group(1));
                double dt = Double.parseDouble(match.group(2));
                if (iterations >= 1 && iterations <= 5000 && (dt == DETAILED_DT || dt == DETAILED_DT / 2 ||
                        dt == AVERAGED_DT || dt == AVERAGED_DT / 2)) {
                    Long prior = counts.get(value);
                    counts.put(value, prior == null ? 1L : prior + 1); total++; return;
                }
            } catch (NumberFormatException unknownFormat) { /* Preserve unknown messages verbatim. */ }
            original.println(value);
        }
        synchronized void report() {
            StringBuilder b = new StringBuilder("E06_ROUTINE_CONVERGENCE {\"aggregatedRoutineLineCount\":")
                .append(total).append(",\"droppedRawRoutineLineCount\":").append(total).append(",\"exactMessageCounts\":{");
            boolean comma = false;
            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                if (comma) b.append(','); comma = true;
                b.append(quote(entry.getKey())).append(':').append(entry.getValue());
            }
            original.println(b.append("}}").toString());
        }
    }

    private static final class Case {
        final boolean averaged, fine;
        final String scenario, identity;
        final int reference;
        final double dt, end;
        ResistorElm bondA, bondB;
        VoltageElm offset;
        Case(boolean averaged, boolean fine, String scenario, int reference, double end) {
            this.averaged = averaged; this.fine = fine; this.scenario = scenario;
            this.reference = reference; this.end = end;
            dt = (averaged ? AVERAGED_DT : DETAILED_DT) / (fine ? 2 : 1);
            identity = (averaged ? "AVERAGED" : "DETAILED") + "_" + scenario + "_" +
                (fine ? "FINE" : "COARSE") + "_REF" + reference;
        }
        int step(double time) { return (int)Math.round(time / dt); }
        boolean sequence() { return "SEQUENCE".equals(scenario); }
        boolean fault() { return "SENSE_OPEN".equals(scenario) || "FUSE_BLOWN".equals(scenario); }
    }

    private static Diagnostics runCase(Case c) {
        boolean averaged = c.averaged;
        double dt = c.dt;
        int steps = c.step(c.end);
        String model = averaged ? "AVERAGED" : "DETAILED";
        long startedNanos = System.nanoTime();
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        String previousDiode = DiodeElm.lastModelName, previousZener = ZenerElm.lastZenerModelName;
        String previousTransistor = TransistorElm.lastModelName;
        int previousMosfetFlags = MosfetElm.globalFlags;
        double previousBeta = MosfetElm.lastBeta;
        TestCirSim sim = new TestCirSim(dt);
        Vector<CircuitElm> original = sim.elmList;
        E06ConverterFixtures.Fixture fixture = null;
        Diagnostics diagnostics = null;
        Throwable failure = null;
        boolean disposed = true;
        CircuitElm.sim = sim;
        try {
            constructionParity();
            TransistorElm.lastModelName = "spice-default";
            fixture = averaged ? E06ConverterFixtures.averaged() : E06ConverterFixtures.detailed();
            check("default".equals(fixture.opto.transistor.modelName),
                "actual opto BJT model is independent of prior caller selection");
            check("spice-default".equals(TransistorElm.lastModelName),
                "fixture construction preserves the caller BJT model selection");
            configureReference(fixture, c);
            diagnostics = new Diagnostics(fixture, c);
            diagnostics.domainsConnected = connected(fixture.elements, fixture.inMinus, fixture.outMinus);
            check(diagnostics.domainsConnected == (c.reference >= 6),
                "actual terminal graph has exactly the declared inter-domain bonds");
            sim.elmList = fixture.elements;
            sim.a01MeasurementRunning = true;
            sim.solverExecutor.analyze();
            diagnostics.initialize();
            sample(sim, fixture, diagnostics);
            check(sim.a01AcceptedStepCount == steps, "exact accepted-step count");
            check(Math.abs(sim.t - c.end) <= 1e-10, "accepted time reaches declared physical endpoint");
            check(diagnostics.disabledActiveSamples == 0, "actual low enable prevents converter activation");
            if (averaged) {
                check(diagnostics.enabledActiveSamples > 0 && diagnostics.enabledStageDeliverySamples > 0,
                    "actual enable causes averaged activation and signed electrical delivery");
            } else {
                check(diagnostics.disabledPulseSamples == 0, "actual low enable prevents PWM pulses");
                check(diagnostics.disabledHighGateMax < 3 && diagnostics.disabledLowGateMax < 3,
                    "actual low-enable gate voltages remain below frozen NMOS threshold");
                check(diagnostics.enabledActiveSamples > 0 && diagnostics.enabledPulseSamples > 0 &&
                    diagnostics.enabledHighGateMax > 3 && diagnostics.enabledLowGateMax > 3,
                    "actual enabled powered controller produces both electrical gate drives");
            }

            validateCase(diagnostics, fixture);
            diagnostics.wallSeconds = (System.nanoTime() - startedNanos) / 1e9;
            check(sim.stopMessage == null && !sim.solverExecutor.isUnavailable(),
                "production solver completes with its execution owner available");
            System.out.println("E06_CONVERTER_PILOT " + diagnostics.report(sim, fixture, "COMPLETE"));
            return diagnostics;
        } catch (RuntimeException exception) {
            failure = exception;
            if (diagnostics != null) diagnostics.wallSeconds = (System.nanoTime() - startedNanos) / 1e9;
            if (diagnostics != null) System.out.println("E06_CONVERTER_PILOT " +
                diagnostics.report(sim, fixture, "FAILED"));
            throw exception;
        } catch (Error error) {
            failure = error;
            if (diagnostics != null) diagnostics.wallSeconds = (System.nanoTime() - startedNanos) / 1e9;
            if (diagnostics != null) System.out.println("E06_CONVERTER_PILOT " +
                diagnostics.report(sim, fixture, "FAILED"));
            throw error;
        } finally {
            Throwable cleanupFailure = null;
            try {
                sim.a01MeasurementRunning = false;
                sim.elmList = original;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException cleanup) { cleanupFailure = cleanup; }
                catch (Error cleanup) { cleanupFailure = cleanup; }
                sim.circuitMatrix = null;
                if (fixture != null) for (CircuitElm element : fixture.elements) {
                    try { element.delete(); }
                    catch (RuntimeException cleanup) {
                        disposed = false;
                        if (cleanupFailure == null) cleanupFailure = cleanup;
                        else cleanupFailure.addSuppressed(cleanup);
                    } catch (Error cleanup) {
                        disposed = false;
                        if (cleanupFailure == null) cleanupFailure = cleanup;
                        else cleanupFailure.addSuppressed(cleanup);
                    }
                }
            } finally {
                DiodeElm.lastModelName = previousDiode; ZenerElm.lastZenerModelName = previousZener;
                TransistorElm.lastModelName = previousTransistor;
                MosfetElm.globalFlags = previousMosfetFlags; MosfetElm.lastBeta = previousBeta;
                CircuitElm.sim = previous; CirSim.theSim = previousSingleton;
            }
            boolean clean = cleanupFailure == null && sim.elmList == original && original.isEmpty() && disposed &&
                sim.circuitMatrix == null && !sim.solverExecutor.isUnavailable() &&
                CircuitElm.sim == previous && CirSim.theSim == previousSingleton;
            System.out.println("E06_CONVERTER_CLEANUP {\"case\":" + quote(c.identity) + ",\"model\":" + quote(model) + ",\"rawGraphRestored\":" +
                (sim.elmList == original && original.isEmpty()) + ",\"candidateDisposed\":" + disposed +
                ",\"executorAvailable\":" + !sim.solverExecutor.isUnavailable() +
                ",\"singletonRestored\":" + (CircuitElm.sim == previous && CirSim.theSim == previousSingleton) + "}");
            if (failure != null) {
                if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
                if (!clean) failure.addSuppressed(new AssertionError("E06 pilot cleanup failed"));
            } else {
                if (cleanupFailure != null) throw new AssertionError("E06 pilot cleanup failed", cleanupFailure);
                check(clean, "raw graph, elements, executor and singleton cleanup");
            }
        }
    }

    private static void configureReference(E06ConverterFixtures.Fixture f, Case c) {
        // Existing fixture ground is removed, not supplemented by another domain reference.
        for (int n = f.elements.size() - 1; n >= 0; n--) if (f.elements.get(n) instanceof GroundElm) {
            CircuitElm ground = f.elements.remove(n); ground.delete();
        }
        Point reference = c.reference == 1 || c.reference == 5 ? f.outMinus : f.inMinus;
        if (c.reference == 4 || c.reference == 5) {
            Point groundAt = new Point(-4096, -4096);
            c.offset = end(new VoltageElm(groundAt.x, groundAt.y, VoltageElm.WF_DC), reference);
            c.offset.maxVoltage = 10; c.offset.bias = 0;
            f.elements.add(c.offset);
            f.elements.add(end(new GroundElm(groundAt.x, groundAt.y), new Point(groundAt.x, groundAt.y + 32)));
        } else if (c.reference != 2)
            f.elements.add(end(new GroundElm(reference.x, reference.y), new Point(reference.x, reference.y + 32)));
        if (c.reference >= 6) {
            c.bondA = end(new ResistorElm(f.inPlus.x, f.inPlus.y), f.outputPlus);
            c.bondA.setResistance(1000000); f.elements.add(c.bondA);
        }
        if (c.reference == 7) {
            c.bondB = end(new ResistorElm(f.inMinus.x, f.inMinus.y), f.outMinus);
            c.bondB.setResistance(1000000); f.elements.add(c.bondB);
        }
        int grounds = 0;
        for (CircuitElm element : f.elements) if (element instanceof GroundElm) grounds++;
        check(grounds == (c.reference == 2 ? 0 : 1), "exactly one selected-domain reference, or automatic reference");
        if (c.reference == 3) Collections.reverse(f.elements);
        Vector<CircuitElm> withoutBonds = new Vector<CircuitElm>(f.elements);
        withoutBonds.remove(c.bondA); withoutBonds.remove(c.bondB);
        check(!connected(withoutBonds, f.inMinus, f.outMinus),
            "actual transformer/module/opto terminal declarations leave the two domains separate");
    }

    private static <T extends CircuitElm> T end(T element, Point at) {
        element.x2 = at.x; element.y2 = at.y; element.setPoints(); return element;
    }

    /** Independent coordinate BFS using actual posts and each real element's connection edges. */
    private static boolean connected(Vector<CircuitElm> elements, Point start, Point target) {
        Vector<Point> queue = new Vector<Point>(); HashSet<String> seen = new HashSet<String>();
        queue.add(start); seen.add(pointKey(start));
        for (int next = 0; next < queue.size(); next++) {
            Point at = queue.get(next);
            if (at.x == target.x && at.y == target.y) return true;
            for (CircuitElm element : elements) for (int a = 0; a < element.getPostCount(); a++) {
                Point post = element.getPost(a);
                if (post.x != at.x || post.y != at.y) continue;
                for (int b = 0; b < element.getPostCount(); b++) if (element.getConnection(a, b)) {
                    Point reached = element.getPost(b);
                    if (seen.add(pointKey(reached))) queue.add(reached);
                }
            }
        }
        return false;
    }
    private static String pointKey(Point point) { return point.x + "," + point.y; }

    private static void validateCase(Diagnostics d, E06ConverterFixtures.Fixture f) {
        check(d.acceptedOptoGminEscalatedSamples == 0 && d.maxAcceptedOptoTransistorGmin <= GMIN_MAX,
            "accepted optocoupler BJT never requires elevated numerical gmin");
        check(finite(d.startup10p8Seconds), "actual filtered output reaches 10.8 V during first nominal interval");
        double residual = d.sourceEnergy - d.absorptionEnergy - (d.storedEnd - d.storedStart) - d.beEnergy - d.trapEnergy;
        double allowance = CLOSURE_REL * Math.max(Math.abs(d.stageInputEnergy), Math.abs(d.stageOutputEnergy)) + CLOSURE_J;
        check(Math.abs(residual) <= allowance, "full signed source/storage/loss energy closes within frozen band");
        for (int n = 0; n < d.phases.length; n++) {
            Window phase = d.phases[n];
            if (phase.count == 0) continue;
            check(Math.abs(phase.residual()) <= phase.closureAllowance(),
                "physical-time phase source/storage/loss closure: " + PHASE_NAMES[n]);
            if (n == 0 || d.c.sequence() && (n == 1 || n == 2)) {
                double mean = phase.mean(3);
                check(mean >= SETTLED_MIN && mean <= SETTLED_MAX,
                    "healthy settled output mean meets frozen feedback envelope: " + PHASE_NAMES[n]);
                check((phase.max[3] - phase.min[3]) / Math.abs(mean) <= RIPPLE_REL,
                    "healthy settled output ripple meets frozen bound: " + PHASE_NAMES[n]);
                check(phase.halfDrift() <= DRIFT_REL,
                    "healthy settled output half-window drift meets frozen bound: " + PHASE_NAMES[n]);
            }
        }
        if (d.c.sequence()) {
            Window tail = d.phases[5];
            check(tail.storageStart == d.isolationStorage, "OFF retains the accepted storage at the same physical boundary");
            check(tail.storageEnd + tail.absorption + tail.be + tail.trap <=
                d.isolationStorage + tail.source + tail.closureAllowance(),
                "remaining accepted storage and measured source energy bound OFF tail consumption");
        }
        if ("SENSE_OPEN".equals(d.c.scenario)) {
            Window before = d.nominal, after = d.phases[6];
            check(Math.abs(after.mean(9)) < Math.abs(before.mean(9)), "actual sense open reduces LED current");
            check(Math.abs(after.mean(10)) < Math.abs(before.mean(10)), "actual sense open reduces collector feedback current");
            check(after.mean(11) > before.mean(11) && after.mean(14) > before.mean(14) &&
                after.mean(3) > before.mean(3), "actual feedback loss changes feedback, duty and filtered output causally");
        }
        if ("FUSE_BLOWN".equals(d.c.scenario)) {
            Window tail = d.phases[7];
            check(f.front.fuse.blown, "actual sacrificial input fuse remains blown");
            // The existing model has finite 1 Gohm blown resistance, not an invented ideal open.
            check(tail.ac <= square(E05AcInputModel.PEAK_VOLTS) / f.front.fuse.blownResistance * .2 + CLOSURE_J,
                "actual blown fuse cannot provide fresh AC energy beyond its declared leakage and frozen floor");
            check(tail.storageStart == d.faultStorage, "fuse fault retains accepted storage without reset");
            check(tail.storageEnd + tail.absorption + tail.be + tail.trap <=
                d.faultStorage + tail.source + tail.closureAllowance(),
                "retained storage and actual finite leakage bound post-fuse consumption");
        }
        // Eight binary ULPs admit only arithmetic roundoff in this imposed ideal-source equation.
        if (d.c.offset != null) check(Math.abs(diff(d.c.offset, 1, 0) - 10) <= 8 * Math.ulp(10.0),
            "actual DC reference source applies the requested single-domain +10 V offset");
        if (d.c.bondB != null) check(d.bondSamples > 0 && Math.abs(d.bondCurrentSum) > 0,
            "two actual domain bonds carry a nonzero closed-loop current");
    }

    private static void compare(Diagnostics first, Diagnostics second, boolean timestep) {
        double meanBand = timestep ? DT_MEAN_REL : MODEL_MEAN_REL;
        for (int phase = 0; phase < 3; phase++) {
            Window a = first.phases[phase], b = second.phases[phase];
            double denominator = Math.abs(timestep ? b.mean(3) : a.mean(3));
            double delta = Math.abs(b.mean(3) - a.mean(3)) / denominator;
            double inputDelta = Math.abs(b.stageInput - a.stageInput) / Math.abs(a.stageInput);
            System.out.println("E06_CONVERTER_COMPARISON {\"kind\":" + quote(timestep ? "COARSE_FINE" : "DETAILED_AVERAGED") +
                ",\"firstCase\":" + quote(first.c.identity) + ",\"secondCase\":" + quote(second.c.identity) +
                ",\"phase\":" + quote(PHASE_NAMES[phase]) + ",\"outputMeanRelativeDelta\":" + delta +
                ",\"stageInputEnergyRelativeDelta\":" + inputDelta + ",\"meanBand\":" + meanBand +
                ",\"inputEnergyBand\":" + (timestep ? "null" : Double.toString(MODEL_INPUT_REL)) +
                ",\"startupCrossingDeltaSeconds\":" + Math.abs(first.startup10p8Seconds - second.startup10p8Seconds) +
                ",\"startupCrossingBandSeconds\":" + STARTUP_DELTA + "}");
            check(delta <= meanBand, "filtered output means meet frozen " + (timestep ? "timestep" : "paired-model") + " band");
            if (!timestep) check(inputDelta <= MODEL_INPUT_REL, "signed stage input energy meets frozen paired-model band");
        }
        check(Math.abs(first.startup10p8Seconds - second.startup10p8Seconds) <= STARTUP_DELTA,
            "actual startup crossing difference meets frozen time band");
    }

    private static void compareReference(Diagnostics baseline, Diagnostics candidate) {
        Window a = baseline.nominal, b = candidate.nominal;
        double voltageDelta = Math.abs(b.mean(3) - a.mean(3));
        double inputDelta = Math.abs(b.mean(37) - a.mean(37)), outputDelta = Math.abs(b.mean(38) - a.mean(38));
        System.out.println("E06_CONVERTER_COMPARISON {\"kind\":\"REFERENCE\",\"firstCase\":" + quote(baseline.c.identity) +
            ",\"secondCase\":" + quote(candidate.c.identity) + ",\"outputMeanDeltaV\":" + voltageDelta +
            ",\"stageInputPowerDeltaW\":" + inputDelta + ",\"stageOutputPowerDeltaW\":" + outputDelta + "}");
        check(voltageDelta <= REF_V, "numerical reference/order/offset preserves filtered differential output");
        check(inputDelta <= REF_POWER_REL * Math.abs(a.mean(37)) + REF_POWER_W,
            "numerical reference/order/offset preserves signed stage input power");
        check(outputDelta <= REF_POWER_REL * Math.abs(a.mean(38)) + REF_POWER_W,
            "numerical reference/order/offset preserves signed stage output power");
    }

    private static void constructionParity() {
        // The native runner replaces only ChipElm's DOM menu read with setSize(2).
        // This matches grid16 / Small Grid false; the compiled GWT factory remains required.
        check(CircuitElm.sim.gridSize == 16, "native chip fixture retains full-grid16 parity");
        String token = new StringTokenizer(new String("DiodeElm 6 1")).nextToken();
        check("DiodeElm".equals(token) && token != "DiodeElm",
            "raw composite token has equal value and distinct JVM identity");
        String[] names = {"DiodeElm", "CCCSElm", "NTransistorElm"};
        for (int n = 0; n < names.length; n++) {
            CircuitElm element = CirSim.constructElement(new String(names[n]), 0, 0);
            try {
                check(n == 0 ? element instanceof DiodeElm : n == 1 ? element instanceof CCCSElm :
                    element instanceof NTransistorElm, "native construction parity for " + names[n]);
                if (n == 1) check(((CCCSElm)element).csize == 2,
                    "actual CCCS constructor retains full-grid chip size2");
            } finally { if (element != null) element.delete(); }
        }
        check(CirSim.constructElement(null, 0, 0) == null &&
            CirSim.constructElement(new String("UnknownE06Element"), 0, 0) == null,
            "null and unknown element names remain rejected");
    }

    private static void sample(TestCirSim sim, E06ConverterFixtures.Fixture f, Diagnostics d) {
        Case c = d.c;
        int steps = c.step(c.end), enableStep = c.step(.05), windowSteps = c.step(.02);
        double dt = d.dt;
        Window window = new Window();
        double[] nodeCurrents = new double[sim.nodeList.size()];
        for (int step = 0; step < steps; step++) {
            boolean changed = false;
            if (step == enableStep) { f.enable.setVoltage(5); changed = true; }
            if (c.sequence()) {
                if (step == c.step(.2)) { f.load.setResistance(24); changed = true; }
                if (step == c.step(.35)) { f.front.input.source.maxVoltage = 90 * Math.sqrt(2); changed = true; }
                if (step == c.step(.5)) { f.enable.setVoltage(0); changed = true; }
                if (step == c.step(.6)) { f.enable.setVoltage(5); changed = true; }
                if (step == c.step(.7)) {
                    d.isolationStorage = d.storedEnd;
                    f.front.input.binding.setConnected(false);
                    check(f.front.input.linePole.position == 1 && f.front.input.returnPole.position == 1 &&
                        !f.front.input.binding.isConnected(), "one actual source owner opens both input poles");
                    changed = true;
                }
            }
            if (c.fault() && step == c.step(.2)) {
                d.faultStorage = d.storedEnd;
                if ("SENSE_OPEN".equals(c.scenario)) {
                    check(f.elements.remove(f.senseResistor), "remove the actual sense resistor from solver graph");
                    for (Record record : d.records) if (record.element == f.senseResistor) {
                        record.retired = true; d.retiredSenseEnergy = record.terminalEnergy;
                    }
                    f.senseResistor.delete();
                    check(!sim.elmList.contains(f.senseResistor), "sense fault has no retained electrical stand-in");
                } else f.front.fuse.blown = true;
                changed = true;
            }
            if (changed) {
                // No reset: invalidate/reanalyze the same owner and retain accepted physical histories.
                sim.solverExecutor.invalidate(); sim.solverExecutor.analyze();
                nodeCurrents = new double[sim.nodeList.size()];
            }
            sim.solverExecutor.advanceSteps(1);
            double time = (step + 1) * dt;
            double previousStored = d.storedEnd;
            double[] values = channels(sim, f, d.averaged);
            for (int n = 0; n < values.length; n++) if (d.supported[n])
                check(finite(values[n]), "finite accepted supported channel sample");
            long cycle = d.averaged ? f.averaged.getCycleIndex() : f.pwm.getCycleIndex();
            boolean active = d.averaged ? f.averaged.isActive() : f.pwm.isActive();
            boolean pulse = !d.averaged && f.pwm.isPulseOn();
            check(values[14] >= 0 && values[14] <= E06ConverterContract.MAX_DUTY && values[18] >= 0 &&
                values[19] >= 0 && values[19] < E06ConverterContract.PERIOD_SECONDS && cycle >= 0,
                "accepted control state stays within declared ranges");
            check(Math.abs(values[34]) <= E06ConverterContract.MAX_DUTY,
                "accepted PI integral stays within its symmetric declared duty bound");
            if (d.averaged) {
                double actualDrive = values[36] + values[21] / E06ConverterContract.OUTPUT_LEAK_OHMS;
                check(actualDrive >= -1e-8 && actualDrive <= E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS + 1e-8,
                    "actual averaged drive respects its existing accepted current tolerance and ceiling");
            }
            if (c.sequence() && step == c.step(.5)) {
                d.disableTransitionDuty = values[14]; d.disableTransitionStagePower = values[38];
            }
            if (c.sequence() && step > c.step(.5) && step < c.step(.6)) {
                check(!active && values[14] == 0, "disable stops prepared drive after one accepted-step latency");
                if (d.averaged) check(values[36] + values[21] / E06ConverterContract.OUTPUT_LEAK_OHMS == 0,
                    "disabled averaged stage has no delivered drive beyond passive output leak");
                else check(!pulse && values[12] < E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS &&
                    values[13] < E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS,
                    "disabled detailed gates and pulses cease after one accepted-step latency");
            }
            if (c.sequence() && step >= c.step(.7)) {
                check(f.front.input.linePole.getCurrent() == 0 && f.front.input.returnPole.getCurrent() == 0,
                    "open actual input poles deliver no fresh mains current into the board");
            }
            Arrays.fill(nodeCurrents, 0);
            double source = 0, absorption = 0, stored = 0, be = 0, trap = 0, allPower = 0;
            for (Record record : d.records) {
                if (record.retired) continue;
                CircuitElm element = record.element;
                double terminalSum = 0;
                for (int post = 0; post < element.getPostCount(); post++) {
                    double current = element.getCurrentIntoNode(post), voltage = element.getPostVoltage(post);
                    check(finite(current) && finite(voltage), "finite actual terminal V/I");
                    terminalSum += current; nodeCurrents[element.getNode(post)] += current;
                }
                if (element.getPostCount() > 1)
                    d.maxElementCurrentSum = Math.max(d.maxElementCurrentSum, Math.abs(terminalSum));
                double power = absorbedPower(element);
                check(finite(power), "finite independent signed terminal power");
                if (element instanceof ResistorElm || element instanceof FuseElm)
                    check(power >= 0, "actual resistive element remains passive");
                record.terminalEnergy += dt * power; allPower += power;
                if (record.source) source -= power;
                else if (record.storage) {
                    double energy = record.storageEnergy(), damping = record.beDamping();
                    double quadrature = record.trapDefect(dt);
                    check(finite(energy) && energy >= 0 && finite(damping) && damping >= 0 && finite(quadrature),
                        "finite positive storage and declared numerical terms");
                    record.beEnergy += damping; record.trapEnergy += quadrature;
                    stored += energy; be += damping; trap += quadrature; record.commitStorage(energy);
                } else absorption += power;
            }
            for (double residual : nodeCurrents)
                d.maxExternalNodeKcl = Math.max(d.maxExternalNodeKcl, Math.abs(residual));
            double ac = dt * values[0] * values[1];
            d.acSourceEnergy += ac;
            d.enableTerminalEnergy += dt * diff(f.enable, 1, 0) * f.enable.getCurrent();
            d.enableEmfEnergy += dt * f.enable.getSourceVoltage() * f.enable.getCurrent();
            d.enableSeriesEnergy += dt * square(f.enable.getCurrent()) * f.enable.getResistance();
            d.sourceEnergy += dt * source; d.absorptionEnergy += dt * absorption;
            d.stageInputEnergy += dt * values[37]; d.stageOutputEnergy += dt * values[38];
            d.allTerminalEnergy += dt * allPower; d.storedEnd = stored;
            d.beEnergy += be; d.trapEnergy += trap;
            d.maxSubIterations = Math.max(d.maxSubIterations, sim.subIterations);
            d.maxAcceptedOptoTransistorGmin = Math.max(d.maxAcceptedOptoTransistorGmin, f.opto.transistor.gmin);
            if (f.opto.transistor.gmin > GMIN_MAX) d.acceptedOptoGminEscalatedSamples++;
            if (Double.isNaN(d.startup10p8Seconds) && time >= .05 && time <= .2 && values[3] >= 10.8)
                d.startup10p8Seconds = time;
            if (step >= enableStep) {
                if (active) d.enabledActiveSamples++;
                if (pulse) d.enabledPulseSamples++;
                if (values[36] > 0 && values[38] > 0) d.enabledStageDeliverySamples++;
                if (!d.averaged) {
                    d.enabledHighGateMax = Math.max(d.enabledHighGateMax, values[12]);
                    d.enabledLowGateMax = Math.max(d.enabledLowGateMax, values[13]);
                }
            } else {
                if (active) d.disabledActiveSamples++;
                if (pulse) d.disabledPulseSamples++;
                if (!d.averaged) {
                    d.disabledHighGateMax = Math.max(d.disabledHighGateMax, values[12]);
                    d.disabledLowGateMax = Math.max(d.disabledLowGateMax, values[13]);
                }
            }
            if (c.bondA != null) {
                double current = c.bondA.getCurrent();
                d.maxBondCurrent = Math.max(d.maxBondCurrent, Math.abs(current));
                if (step >= c.step(.15)) {
                    d.maxNominalBondCurrent = Math.max(d.maxNominalBondCurrent, Math.abs(current));
                    if (c.bondB == null) check(Math.abs(current) < SINGLE_BOND_MAX_A,
                        "one actual inter-domain bond has no finite return current in the nominal interval");
                }
                if (c.bondB != null) {
                    double expected = (values[2] - values[3]) / 2000000;
                    double error = Math.abs(current - expected);
                    d.maxBondFormulaError = Math.max(d.maxBondFormulaError, error);
                    if (step >= c.step(.15)) {
                        double differential = Math.abs(values[2] - values[3]);
                        check(error * differential <= REF_POWER_REL * Math.abs(expected * differential) + REF_POWER_W,
                            "two actual bonds realize independently calculated closed-loop current");
                        d.bondCurrentSum += current; d.bondExpectedSum += expected; d.bondSamples++;
                    }
                }
            }
            d.completedSteps++; d.lastCycle = cycle; d.lastValues = values;
            window.previousStorage = previousStored;
            window.add(values, d.supported, dt * source, dt * absorption, be, trap,
                dt * values[37], dt * values[38], active, pulse, stored, ac);
            for (int phase = 0; phase < PHASE_NAMES.length; phase++) if (step >= c.step(PHASE_START[phase]) &&
                    step < c.step(PHASE_END[phase]) && (phase == 0 || c.sequence() && phase >= 1 && phase <= 5 ||
                    c.fault() && phase >= 6)) {
                Window w = d.phases[phase]; w.previousStorage = previousStored;
                w.add(values, d.supported, dt * source, dt * absorption, be, trap,
                    dt * values[37], dt * values[38], active, pulse, stored, ac);
                if (step < c.step((PHASE_START[phase] + PHASE_END[phase]) / 2)) {
                    w.firstHalfOutputSum += values[3]; w.firstHalfSamples++;
                } else { w.secondHalfOutputSum += values[3]; w.secondHalfSamples++; }
            }
            if ((step + 1) % windowSteps == 0) { d.appendWindow(window, sim.t, stored); window = new Window(); }
        }
    }

    private static double[] channels(CirSim sim, E06ConverterFixtures.Fixture f, boolean averaged) {
        double[] v = new double[CHANNELS.length]; Arrays.fill(v, Double.NaN);
        v[0] = diff(f.front.input.source, 1, 0); v[1] = f.front.input.source.getCurrent();
        v[2] = diff(f.front.bulk, 0, 1); v[3] = diff(f.load, 0, 1); v[4] = f.load.getCurrent();
        v[9] = -f.opto.getCurrentIntoNode(0); v[10] = -f.opto.getCurrentIntoNode(2); v[11] = diff(f.opto, 2, 3);
        v[14] = averaged ? f.averaged.getDuty() : f.pwm.getDuty(); v[15] = diff(f.enable, 1, 0);
        v[16] = f.bias.getInputCurrent(); v[18] = averaged ? f.averaged.getActiveSeconds() : f.pwm.getActiveSeconds();
        // Averaged phase identifies control timing only; it is not a switch waveform.
        v[19] = averaged ? E06ConverterContract.cyclePhaseSeconds(sim.t - sim.timeStep) : f.pwm.getPhaseSeconds();
        v[20] = diff(f.outputCap, 0, 1);
        v[21] = f.outputInductor.getPostVoltage(0) - f.outputCap.getPostVoltage(1);
        v[22] = f.outputInductor.getCurrent(); v[26] = f.freewheel.getCurrent(); v[33] = f.bias.getOutputCurrent();
        v[34] = averaged ? f.averaged.getIntegral() : f.pwm.getIntegral();
        if (averaged) {
            v[39] = f.averaged.getInputCurrent();
            v[35] = f.averaged.getInputCurrent() + f.bias.getInputCurrent();
            v[36] = f.averaged.getCurrentIntoNode(E06ConverterContract.PRE_L_PLUS);
        } else {
            v[5] = diff(f.transformer, 0, 2); v[6] = f.transformer.current[0];
            v[7] = diff(f.transformer, 1, 3); v[8] = f.transformer.current[1];
            v[12] = diff(f.highSwitch, 0, 1); v[13] = diff(f.lowSwitch, 0, 1); v[17] = f.pwm.getInputCurrent();
            v[23] = f.resetLower.getCurrent(); v[24] = f.resetUpper.getCurrent(); v[25] = f.forward.getCurrent();
            v[27] = -f.highSwitch.getCurrentIntoNode(2); v[28] = f.highSwitch.diodeCurrent2; v[29] = f.highSwitch.getCurrent();
            v[30] = -f.lowSwitch.getCurrentIntoNode(2); v[31] = f.lowSwitch.diodeCurrent2; v[32] = f.lowSwitch.getCurrent();
            v[35] = -f.highSwitch.getCurrentIntoNode(2) - f.resetUpper.getCurrent() +
                f.pwm.getInputCurrent() + f.bias.getInputCurrent();
            v[36] = f.forward.getCurrent();
        }
        // Signed instantaneous boundary products; never multiply means.
        v[37] = v[2] * v[35]; v[38] = v[21] * v[36];
        return v;
    }
    /** getCurrentIntoNode is element-to-node injection; negate V*I for absorption. */
    private static double absorbedPower(CircuitElm element) {
        if (element instanceof VoltageElm || element instanceof E02FiniteSourceElm)
            return -diff(element, 1, 0) * element.getCurrent();
        if (element instanceof TransformerElm)
            return diff(element, 0, 2) * ((TransformerElm)element).current[0] +
                diff(element, 1, 3) * ((TransformerElm)element).current[1];
        if (element instanceof OptocouplerElm)
            return -diff(element, 0, 1) * element.getCurrentIntoNode(0) -
                diff(element, 2, 3) * element.getCurrentIntoNode(2);
        if (element instanceof E06AveragedConverterElm)
            return -diff(element, 0, 1) * element.getCurrentIntoNode(0) -
                diff(element, 2, 3) * element.getCurrentIntoNode(2) -
                diff(element, 4, 1) * element.getCurrentIntoNode(4) -
                diff(element, 5, 1) * element.getCurrentIntoNode(5);
        int reference = element.getPostCount() > 2 ? 1 : 0;
        double result = 0;
        for (int post = 0; post < element.getPostCount(); post++)
            result -= diff(element, post, reference) * element.getCurrentIntoNode(post);
        return result;
    }

    private static final class Record {
        final CircuitElm element;
        final String name;
        final boolean source, storage, backwardEuler;
        final double c, lp, ls, mutual;
        double initialStorage, lastStorage, previousV, previousI, previousV2, previousI2;
        double terminalEnergy, beEnergy, trapEnergy;
        boolean retired;
        Record(CircuitElm element, int index) {
            this.element = element; name = element.getClass().getSimpleName() + "#" + index;
            source = element instanceof VoltageElm || element instanceof E02FiniteSourceElm;
            storage = element instanceof CapacitorElm || element instanceof InductorElm || element instanceof TransformerElm;
            c = element instanceof CapacitorElm ? ((CapacitorElm)element).capacitance : 0;
            lp = element instanceof InductorElm ? ((InductorElm)element).inductance :
                element instanceof TransformerElm ? ((TransformerElm)element).inductance : 0;
            ls = element instanceof TransformerElm ? lp * square(((TransformerElm)element).ratio) : 0;
            mutual = element instanceof TransformerElm ? ((TransformerElm)element).couplingCoef * Math.sqrt(lp * ls) : 0;
            backwardEuler = storage && (element.flags & (element instanceof CapacitorElm ?
                CapacitorElm.FLAG_BACK_EULER : Inductor.FLAG_BACK_EULER)) != 0;
        }
        double v() { return diff(element, 0, element instanceof TransformerElm ? 2 : 1); }
        double i() { return element instanceof TransformerElm ? ((TransformerElm)element).current[0] : element.getCurrent(); }
        double v2() { return element instanceof TransformerElm ? diff(element, 1, 3) : 0; }
        double i2() { return element instanceof TransformerElm ? ((TransformerElm)element).current[1] : 0; }
        double storageEnergy() {
            if (c != 0) return .5 * c * square(v());
            return .5 * lp * square(i()) + .5 * ls * square(i2()) + mutual * i() * i2();
        }
        double incrementEnergy() {
            if (c != 0) return .5 * c * square(v() - previousV);
            double a = i() - previousI, b = i2() - previousI2;
            return .5 * lp * a * a + .5 * ls * b * b + mutual * a * b;
        }
        double beDamping() { return backwardEuler ? incrementEnergy() : 0; }
        // Signed right-endpoint integration defect for trapezoidal companions.
        double trapDefect(double dt) {
            if (backwardEuler) return 0;
            return incrementEnergy() + .5 * dt * (c != 0 ? v() * (i() - previousI) :
                i() * (v() - previousV) + i2() * (v2() - previousV2));
        }
        void commitStorage(double energy) {
            lastStorage = energy; previousV = v(); previousI = i(); previousV2 = v2(); previousI2 = i2();
        }
    }

    private static final class Window {
        int count, active, pulses;
        final int[] samples = new int[CHANNELS.length];
        final double[] sum = new double[CHANNELS.length], square = new double[CHANNELS.length];
        final double[] min = new double[CHANNELS.length], max = new double[CHANNELS.length];
        double source, absorption, be, trap, stageInput, stageOutput, ac;
        double storageStart, storageEnd, previousStorage;
        double firstHalfOutputSum, secondHalfOutputSum;
        int firstHalfSamples, secondHalfSamples;
        double mean(int channel) { return sum[channel] / samples[channel]; }
        double residual() { return source - absorption - (storageEnd - storageStart) - be - trap; }
        double closureAllowance() { return CLOSURE_REL * Math.max(Math.abs(stageInput), Math.abs(stageOutput)) + CLOSURE_J; }
        double halfDrift() { return Math.abs(firstHalfOutputSum / firstHalfSamples -
            secondHalfOutputSum / secondHalfSamples) / Math.abs(mean(3)); }
        Window() { Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY); }
        void add(double[] values, boolean[] supported, double source, double absorption, double be, double trap,
                double stageInput, double stageOutput, boolean active, boolean pulse, double stored, double ac) {
            if (count == 0) storageStart = previousStorage;
            storageEnd = previousStorage = stored;
            count++; if (active) this.active++; if (pulse) pulses++;
            for (int n = 0; n < values.length; n++) if (supported[n]) {
                samples[n]++; sum[n] += values[n]; square[n] += values[n] * values[n];
                min[n] = Math.min(min[n], values[n]); max[n] = Math.max(max[n], values[n]);
            }
            this.source += source; this.absorption += absorption; this.be += be; this.trap += trap;
            this.stageInput += stageInput; this.stageOutput += stageOutput; this.ac += ac;
        }
    }

    private static final class Diagnostics {
        final Case c;
        final boolean averaged;
        final double dt;
        final boolean[] supported = new boolean[CHANNELS.length];
        final Vector<Record> records = new Vector<Record>();
        final StringBuilder windows = new StringBuilder();
        final Window[] phases = new Window[PHASE_NAMES.length];
        final Window nominal;
        boolean domainsConnected;
        double wallSeconds, startup10p8Seconds = Double.NaN, isolationStorage, faultStorage, retiredSenseEnergy;
        double disableTransitionDuty, disableTransitionStagePower;
        double maxBondCurrent, maxNominalBondCurrent, maxBondFormulaError, bondCurrentSum, bondExpectedSum;
        int bondSamples;
        int completedSteps, maxSubIterations, disabledActiveSamples, disabledPulseSamples, enabledActiveSamples, enabledPulseSamples;
        long lastCycle;
        int acceptedOptoGminEscalatedSamples, enabledStageDeliverySamples;
        double maxAcceptedOptoTransistorGmin;
        double storedStart, storedEnd, previousWindowStorage, sourceEnergy, absorptionEnergy, allTerminalEnergy, beEnergy, trapEnergy;
        double acSourceEnergy, enableTerminalEnergy, enableEmfEnergy, enableSeriesEnergy, stageInputEnergy, stageOutputEnergy;
        double maxExternalNodeKcl, maxElementCurrentSum, disabledHighGateMax, disabledLowGateMax, enabledHighGateMax, enabledLowGateMax;
        double[] lastValues;
        Diagnostics(E06ConverterFixtures.Fixture f, Case c) {
            this.c = c; dt = c.dt; averaged = c.averaged;
            for (int n = 0; n < phases.length; n++) phases[n] = new Window();
            nominal = phases[0]; Arrays.fill(supported, true);
            if (averaged) for (int n : new int[] {5, 6, 7, 8, 12, 13, 17, 23, 24, 25, 27, 28, 29, 30, 31, 32})
                supported[n] = false;
            else supported[39] = false;
            for (int n = 0; n < f.elements.size(); n++) records.add(new Record(f.elements.get(n), n));
        }
        void initialize() {
            for (Record record : records) if (record.storage) {
                record.initialStorage = record.storageEnergy(); record.commitStorage(record.initialStorage);
                storedStart += record.initialStorage;
            }
            storedEnd = previousWindowStorage = storedStart;
            for (Window phase : phases) phase.previousStorage = storedStart;
        }
        void appendWindow(Window w, double time, double stored) {
            if (windows.length() != 0) windows.append(',');
            windows.append("{\"endSeconds\":").append(time).append(",\"acceptedSteps\":").append(w.count)
                .append(",\"activeSamples\":").append(w.active)
                .append(",\"pulseSamples\":").append(averaged ? "null" : Integer.toString(w.pulses))
                .append(",\"outputMeanV\":").append(w.mean(3)).append(",\"outputMinimumV\":").append(w.min[3])
                .append(",\"outputMaximumV\":").append(w.max[3]).append(",\"bulkMeanV\":").append(w.mean(2))
                .append(",\"dutyMean\":").append(w.mean(14))
                .append(",\"stageInputJ\":").append(w.stageInput).append(",\"stageOutputJ\":").append(w.stageOutput)
                .append(",\"storageJ\":").append(stored).append(",\"storageChangeJ\":").append(stored - previousWindowStorage)
                .append(",\"beNumericalDampingJ\":").append(w.be)
                .append(",\"trapRightEndpointDefectJ\":").append(w.trap).append('}');
            previousWindowStorage = stored;
        }
        private void appendChannels(StringBuilder output, Window w) {
            if (w.count == 0) return;
            for (int n = 0; n < CHANNELS.length; n++) {
                if (n != 0) output.append(',');
                if (w.samples[n] == 0) { output.append(quote(CHANNELS[n])).append(":null"); continue; }
                output.append(quote(CHANNELS[n])).append(":{\"mean\":").append(w.sum[n] / w.samples[n])
                    .append(",\"rms\":").append(Math.sqrt(w.square[n] / w.samples[n]))
                    .append(",\"min\":").append(w.min[n]).append(",\"max\":").append(w.max[n]).append('}');
            }
        }
        String report(CirSim sim, E06ConverterFixtures.Fixture f, String result) {
            StringBuilder b = new StringBuilder("{\"result\":").append(quote(result))
                .append(",\"model\":").append(quote(averaged ? "AVERAGED" : "DETAILED"))
                .append(",\"case\":").append(quote(c.identity)).append(",\"scenario\":").append(quote(c.scenario))
                .append(",\"fidelity\":").append(quote(FIDELITY)).append(",\"execution\":\"NATIVE_RAW_GRAPH\"")
                .append(",\"playerOwnerRestored\":null,\"integration\":\"SIGNED_RIGHT_ENDPOINT_V_I\"")
                .append(",\"numericalDampingIsPhysicalLoss\":false,\"criteria\":\"e06-fidelity-criteria-v1\"")
                .append(",\"domainsConnectedByActualGraph\":").append(domainsConnected)
                .append(",\"referenceVariant\":").append(c.reference).append(",\"referenceName\":").append(quote(REFERENCE_NAMES[c.reference]))
                .append(",\"actualEndLoadOhms\":").append(f.load.resistance)
                .append(",\"actualEndSourceRmsV\":").append(f.front.input.source.maxVoltage / Math.sqrt(2))
                .append(",\"actualInputConnected\":").append(f.front.input.binding.isConnected())
                .append(",\"actualFuseBlown\":").append(f.front.fuse.blown)
                .append(",\"actualSensePresent\":").append(f.elements.contains(f.senseResistor))
                .append(",\"wallSeconds\":").append(wallSeconds)
                .append(",\"startup10p8Seconds\":").append(number(startup10p8Seconds))
                .append(",\"disableTransitionDuty\":").append(disableTransitionDuty)
                .append(",\"disableTransitionStagePowerW\":").append(disableTransitionStagePower)
                .append(",\"storageAtInputIsolationJ\":").append(isolationStorage)
                .append(",\"storageAtFaultJ\":").append(faultStorage)
                .append(",\"retiredSenseSignedAbsorbedJ\":").append(retiredSenseEnergy)
                .append(",\"maxBondCurrentA\":").append(maxBondCurrent)
                .append(",\"maxNominalBondCurrentA\":").append(maxNominalBondCurrent)
                .append(",\"singleBondNoReturnBoundA\":").append(SINGLE_BOND_MAX_A)
                .append(",\"maxTwoBondFormulaErrorA\":").append(maxBondFormulaError)
                .append(",\"twoBondMeanCurrentA\":").append(bondSamples == 0 ? "null" : number(bondCurrentSum / bondSamples))
                .append(",\"twoBondMeanExpectedCurrentA\":").append(bondSamples == 0 ? "null" : number(bondExpectedSum / bondSamples))
                .append(",\"parameters\":").append(quote(f.getParameterSummary()))
                .append(",\"timeStepSeconds\":").append(dt).append(",\"acceptedSteps\":").append(sim.a01AcceptedStepCount)
                .append(",\"sampledSteps\":").append(completedSteps).append(",\"acceptedTimeSeconds\":").append(sim.t)
                .append(",\"analyses\":").append(sim.a01AnalysisCount)
                .append(",\"nonlinearTrials\":").append(sim.a01SubIterationCount).append(",\"solves\":").append(sim.a01SolveCount)
                .append(",\"maxSubIterations\":").append(maxSubIterations).append(",\"cycleIndex\":").append(lastCycle)
                .append(",\"originalStopMessage\":").append(sim.stopMessage == null ? "null" : quote(sim.stopMessage))
                .append(",\"lastSolverSubIteration\":").append(sim.subIterations)
                .append(",\"maxAcceptedOptoTransistorGmin\":").append(number(maxAcceptedOptoTransistorGmin))
                .append(",\"acceptedOptoGminEscalatedSamples\":").append(acceptedOptoGminEscalatedSamples)
                .append(",\"optoGminBaselineThreshold\":1.0001e-12")
                .append(",\"sourceTerminalJ\":").append(sourceEnergy).append(",\"acSourceJ\":").append(acSourceEnergy)
                .append(",\"enableTerminalJ\":").append(enableTerminalEnergy).append(",\"enableInternalEmfJ\":").append(enableEmfEnergy)
                .append(",\"enableInternalSeriesLossJ\":").append(enableSeriesEnergy)
                .append(",\"otherTerminalAbsorptionJ\":").append(absorptionEnergy)
                .append(",\"stageInputIncludesBias\":true,\"phaseIsControlMetadata\":true")
                .append(",\"stageInputJ\":").append(stageInputEnergy).append(",\"stageOutputJ\":").append(stageOutputEnergy)
                .append(",\"enabledStageDeliverySamples\":").append(enabledStageDeliverySamples)
                .append(",\"storageStartJ\":").append(storedStart).append(",\"storageEndJ\":").append(storedEnd)
                .append(",\"beNumericalDampingJ\":").append(beEnergy).append(",\"trapRightEndpointDefectJ\":").append(trapEnergy)
                .append(",\"unqualifiedEnergyResidualJ\":").append(sourceEnergy - absorptionEnergy - (storedEnd - storedStart) - beEnergy - trapEnergy)
                .append(",\"fullEnergyClosureAllowanceJ\":").append(CLOSURE_REL *
                    Math.max(Math.abs(stageInputEnergy), Math.abs(stageOutputEnergy)) + CLOSURE_J)
                .append(",\"allElementSignedTerminalJ\":").append(allTerminalEnergy)
                .append(",\"maxExternalPostNodeKclA\":").append(maxExternalNodeKcl)
                .append(",\"maxElementExternalCurrentSumA\":").append(maxElementCurrentSum)
                .append(",\"numericalAnchors\":").append(sim.unconnectedNodes == null ? 0 : sim.unconnectedNodes.size())
                .append(",\"devices\":[");
            for (int n = 0; n < records.size(); n++) {
                if (n != 0) b.append(','); Record r = records.get(n);
                b.append("{\"name\":").append(quote(r.name)).append(",\"role\":")
                    .append(quote(r.source ? "SOURCE_TERMINAL" : r.storage ? "STORAGE" : "OTHER_TERMINAL"))
                    .append(",\"signedAbsorbedJ\":").append(r.terminalEnergy).append(",\"removedAt200ms\":").append(r.retired);
                if (r.storage) b.append(",\"capFarads\":").append(r.c).append(",\"primaryHenries\":").append(r.lp)
                    .append(",\"secondaryHenries\":").append(r.ls).append(",\"mutualHenries\":").append(r.mutual)
                    .append(",\"backwardEuler\":").append(r.backwardEuler).append(",\"storageStartJ\":").append(r.initialStorage)
                    .append(",\"storageEndJ\":").append(r.lastStorage).append(",\"beNumericalDampingJ\":").append(r.beEnergy)
                    .append(",\"trapRightEndpointDefectJ\":").append(r.trapEnergy);
                b.append('}');
            }
            b.append("],\"endpointChannels\":{");
            if (lastValues != null) for (int n = 0; n < CHANNELS.length; n++) {
                if (n != 0) b.append(','); b.append(quote(CHANNELS[n])).append(':')
                    .append(supported[n] ? number(lastValues[n]) : "null");
            }
            // The full nominal aggregate is presented once in phases, alongside all other full phase channels.
            b.append("},\"nominalPhase\":\"nominal150to200ms\",\"phases\":{");
            for (int n = 0; n < phases.length; n++) {
                if (n != 0) b.append(','); Window w = phases[n];
                b.append(quote(PHASE_NAMES[n])).append(':');
                if (w.count == 0) { b.append("null"); continue; }
                b.append("{\"sampledSteps\":").append(w.count).append(",\"sourceJ\":").append(w.source)
                    .append(",\"acSourceJ\":").append(w.ac).append(",\"stageInputJ\":").append(w.stageInput)
                    .append(",\"stageOutputJ\":").append(w.stageOutput).append(",\"terminalAbsorptionJ\":").append(w.absorption)
                    .append(",\"storageStartJ\":").append(w.storageStart).append(",\"storageEndJ\":").append(w.storageEnd)
                    .append(",\"beNumericalDampingJ\":").append(w.be).append(",\"trapRightEndpointDefectJ\":").append(w.trap)
                    .append(",\"closureResidualJ\":").append(w.residual()).append(",\"closureAllowanceJ\":").append(w.closureAllowance())
                    .append(",\"outputRippleOverMean\":").append(number((w.max[3] - w.min[3]) / Math.abs(w.mean(3))))
                    .append(",\"outputHalfWindowDriftRelative\":").append(w.firstHalfSamples == 0 || w.secondHalfSamples == 0 ?
                        "null" : number(w.halfDrift())).append(",\"channels\":{");
                appendChannels(b, w); b.append("}}");
            }
            b.append("},\"windows\":[").append(windows).append(']');
            if ("FAILED".equals(result)) appendFailureBranches(b, f);
            return b.append('}').toString();
        }
    }

    /** One bounded failure record: field reads only, without stepping or evaluating expressions. */
    private static void appendFailureBranches(StringBuilder b, E06ConverterFixtures.Fixture f) {
        b.append(",\"failureBranchState\":{");
        if (f.averaged == null) {
            appendMosfet(b, "highSwitch", f.highSwitch); b.append(',');
            appendMosfet(b, "lowSwitch", f.lowSwitch);
            b.append(",\"transformer\":{\"volts\":"); appendArray(b, f.transformer.volts, 4);
            b.append(",\"windingCurrents\":"); appendArray(b, f.transformer.current, 2);
            b.append(",\"primaryCompanionSourceA\":").append(number(f.transformer.curSourceValue1))
                .append(",\"secondaryCompanionSourceA\":").append(number(f.transformer.curSourceValue2)).append('}');
        } else b.append("\"highSwitch\":null,\"lowSwitch\":null,\"transformer\":null");
        TransistorElm transistor = f.opto.transistor;
        b.append(",\"optoTransistor\":{\"voltsBCE\":"); appendArray(b, transistor.volts, 3);
        b.append(",\"lastvbc\":").append(number(transistor.lastvbc))
            .append(",\"lastvbe\":").append(number(transistor.lastvbe))
            .append(",\"gmin\":").append(number(transistor.gmin))
            .append(",\"ic\":").append(number(transistor.ic))
            .append(",\"ib\":").append(number(transistor.ib))
            .append(",\"ie\":").append(number(transistor.ie)).append('}');
        CircuitElm internal = f.opto.compElmList.get(1);
        b.append(",\"optoCCCS\":{");
        if (internal instanceof CCCSElm) {
            CCCSElm cccs = (CCCSElm)internal;
            b.append("\"inputCount\":").append(cccs.inputCount)
                .append(",\"inputPairCount\":").append(cccs.inputPairCount)
                .append(",\"broken\":").append(cccs.broken)
                .append(",\"expression\":").append(quote(cccs.exprString))
                .append(",\"storedExpressionCurrentA\":").append(number(cccs.pins[cccs.inputCount].current))
                .append(",\"volts\":"); appendArray(b, cccs.volts, cccs.getPostCount());
            b.append(",\"pinCurrentsInputThenOutput\":[");
            for (int n = 0; n < cccs.pins.length; n++) {
                if (n != 0) b.append(','); b.append(number(cccs.pins[n].current));
            }
            b.append("],\"lastInputCurrents\":"); appendArray(b, cccs.lastCurrents, cccs.inputPairCount);
            b.append(",\"expressionStateValues\":");
            appendArray(b, cccs.exprState == null ? null : cccs.exprState.values, 16);
            b.append(",\"expressionStateTime\":").append(cccs.exprState == null ? "null" : number(cccs.exprState.t));
        } else b.append("\"available\":false");
        b.append("}}");
    }

    private static void appendMosfet(StringBuilder b, String name, MosfetElm q) {
        b.append(quote(name)).append(":{\"voltsGSD\":"); appendArray(b, q.volts, 3);
        b.append(",\"lastv0\":").append(number(q.lastv0))
            .append(",\"lastv1\":").append(number(q.lastv1))
            .append(",\"lastv2\":").append(number(q.lastv2))
            .append(",\"mode\":").append(q.mode).append(",\"gm\":").append(number(q.gm))
            .append(",\"ids\":").append(number(q.ids)).append(",\"bodyTerminal\":").append(q.bodyTerminal)
            .append(",\"diodeCurrent1\":").append(number(q.diodeCurrent1))
            .append(",\"diodeCurrent2\":").append(number(q.diodeCurrent2))
            .append(",\"bodyDiode1LastVoltdiff\":").append(q.diodeB1 == null ? "null" : number(q.diodeB1.lastvoltdiff))
            .append(",\"bodyDiode2LastVoltdiff\":").append(q.diodeB2 == null ? "null" : number(q.diodeB2.lastvoltdiff)).append('}');
    }

    private static void appendArray(StringBuilder b, double[] values, int limit) {
        if (values == null) { b.append("null"); return; }
        b.append('[');
        for (int n = 0; n < Math.min(values.length, limit); n++) {
            if (n != 0) b.append(','); b.append(number(values[n]));
        }
        b.append(']');
    }

    private static String number(double value) {
        return finite(value) ? Double.toString(value) : quote(Double.toString(value));
    }
    private static double diff(CircuitElm element, int a, int b) { return element.getPostVoltage(a) - element.getPostVoltage(b); }
    private static double square(double value) { return value * value; }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
    private static void check(boolean passed, String label) {
        assertions++; if (!passed) throw new AssertionError("E06 pilot mechanics: " + label);
    }

    /** UI-free adapter; all numerical analysis, stepping and state are production owners. */
    private static final class TestCirSim extends CirSim {
        TestCirSim(double dt) {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = dt; adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            // Mirror the production stop state; localization and DOM UI are unavailable here.
            // The production executor then throws its original stopped/nonconvergence failure.
            stopMessage = message; circuitMatrix = null; stopElm = element;
            setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
