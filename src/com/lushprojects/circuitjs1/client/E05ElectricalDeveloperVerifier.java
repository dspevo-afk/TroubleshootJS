package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.core.client.Scheduler;

/** Fixed electrical acceptance cases on the existing CircuitJS execution owner. */
final class E05ElectricalDeveloperVerifier {
    private final CirSim sim;
    private final boolean nativeGraph;
    private int assertions;
    private final StringBuilder rows = new StringBuilder();

    private E05ElectricalDeveloperVerifier(CirSim sim, boolean nativeGraph) {
        this.sim = sim; this.nativeGraph = nativeGraph;
    }

    static void start(final CirSim sim, final boolean forcedFailure) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    if (!sim.troubleshootDebug || !sim.troubleshootE05Verification ||
                            !sim.developerVerifierRunning)
                        throw new IllegalStateException("E05 requires the explicit developer route");
                    final long started = System.currentTimeMillis();
                    final String reference = E05ReferenceIsolationPilot.verify(sim, false);
                    final E05ElectricalDeveloperVerifier verifier =
                        new E05ElectricalDeveloperVerifier(sim, false);
                    final String electrical = verifier.run();
                    E05InstalledRuntimeVerifier.start(sim, sim.troubleshootE05VisualHold,
                        new E05InstalledRuntimeVerifier.Completion() {
                            public void complete(String installed, Throwable failure) {
                                String report = "{\"schema\":\"e05-electrical-verification-v1\"," +
                                    "\"status\":\"" + (failure == null ? "PASS" : "FAIL") +
                                    "\",\"assertions\":" + verifier.assertions +
                                    ",\"wallMs\":" + (System.currentTimeMillis() - started) +
                                    ",\"reference\":" + reference + ",\"electrical\":" + electrical +
                                    ",\"installedRuntime\":" + installed + "}";
                                sim.finishE05Verification(report, failure);
                            }
                        });
                } catch (Throwable failure) { sim.finishE05Verification(null, failure); }
            }
        });
    }

    /** Native contracts use only the same guarded empty, unowned JVM graph as the pilot. */
    static String verifyNative(CirSim sim) {
        E05ReferenceIsolationPilot.requireNativeGraph(sim);
        return new E05ElectricalDeveloperVerifier(sim, true).run();
    }

    private String run() {
        GeneratedBoardInstance player = sim.getGeneratedBoardInstance();
        long started = System.currentTimeMillis();
        double[] directMeans = new double[2], secondaryMeans = new double[2];
        double[] directOffRatios = new double[2], secondaryOffRatios = new double[2];
        for (int pass = 0; pass < 2; pass++) {
            double dt = pass == 0 ? .00005 : .000025;
            directMeans[pass] = rectification(dt, false);
            secondaryMeans[pass] = rectification(dt, true);
            directOffRatios[pass] = discharge(dt, false, false);
            secondaryOffRatios[pass] = discharge(dt, true, false);
            require(discharge(dt, false, true) > directOffRatios[pass] * 1.03,
                "direct bleeder causally accelerates OFF decay");
            require(discharge(dt, true, true) > secondaryOffRatios[pass] * 1.03,
                "secondary bleeder causally accelerates OFF decay");
            unfiltered(dt);
            fuse(dt);
            transformerLoadAndIsolation(dt);
        }
        near(directMeans[0], directMeans[1], .3, "direct DC timestep refinement");
        near(secondaryMeans[0], secondaryMeans[1], .08, "secondary DC timestep refinement");
        near(directOffRatios[0], directOffRatios[1], .002, "direct decay timestep refinement");
        near(secondaryOffRatios[0], secondaryOffRatios[1], .002,
            "secondary decay timestep refinement");
        if (nativeGraph) require(sim.elmList.isEmpty(), "native acceptance raw graph retired");
        else require(sim.getGeneratedBoardInstance() == player, "exact player owner restored");
        return "{\"schema\":\"e05-electrical-acceptance-v1\",\"status\":\"PASS\"," +
            "\"execution\":\"" + (nativeGraph ? "NATIVE_RAW_GRAPH" : "PRIVATE_PLAYER_PROOF") +
            "\",\"playerOwnerRestored\":" + (nativeGraph ? "null" : "true") +
            ",\"assertions\":" + assertions + ",\"wallMs\":" +
            (System.currentTimeMillis() - started) + ",\"rows\":[" + rows + "],\"limits\":[" +
            "\"fixed 120 V RMS 60 Hz simulator fixtures; accepted dt at most 50 us\"," +
            "\"linear transformer and simulated I-squared-t fuse; no physical certification\"," +
            "\"isolated bridge uses backward Euler with numerical damping; no efficiency or physical winding-loss claim\"," +
            "\"reference and transformer-load energy oracles retain trapezoidal integration\"]}";
    }

    private double rectification(double dt, boolean secondary) {
        Graph graph = new Graph(dt);
        try {
            E05ElectricalFixtures.Fixture f = fixture(secondary);
            graph.install(f);
            graph.advance(.3);
            require(!f.fuse.blown, "healthy cold rectifier startup survives declared fuse");
            Stats healthy = sample(graph, .1, true);
            require(healthy.mean > (secondary ? 13 : 150) &&
                healthy.max < (secondary ? 18 : 169.7057), "bounded solved DC rectification");
            require(healthy.min > 0 && healthy.ripple() > (secondary ? .05 : .5),
                "bulk has real positive DC and finite ripple");
            near(healthy.chargeFrequency(), 120, 2, "full-wave accepted charging frequency");
            f.bridge.openDiode.toggle(); graph.analyze(); graph.advance(.3);
            Stats open = sample(graph, .1, true);
            near(open.chargeFrequency(), 60, 1, "one real open diode gives half-wave charging");
            require(open.ripple() > healthy.ripple() * 1.5 && open.mean < healthy.mean,
                "open diode causes increased ripple and lower DC");
            f.bridge.openDiode.toggle();
            f.load.setResistance(secondary ? 500 : 10000);
            graph.analyze(); graph.advance(.3);
            Stats loaded = sample(graph, .1, true);
            require(loaded.loadMean > healthy.loadMean * 1.8 &&
                loaded.mean < healthy.mean - (secondary ? .02 : .1) &&
                loaded.ripple() > healthy.ripple() * 1.35,
                "real heavier load raises current and lowers bulk voltage");
            require(!f.fuse.blown, "declared fuse survives normal load variants");
            row("rectifier", dt, secondary, "\"healthy\":" + healthy.json() +
                ",\"diodeOpen\":" + open.json() + ",\"doubleLoad\":" + loaded.json() +
                ",\"fuseHeat\":" + f.fuse.heat);
            return healthy.mean;
        } finally { graph.close(); }
    }

    private double discharge(double dt, boolean secondary, boolean openBleed) {
        Graph graph = new Graph(dt);
        try {
            E05ElectricalFixtures.Fixture f = fixture(secondary);
            if (openBleed) f.bleed.setResistance(1e12);
            f.input.binding.setConnected(false);
            graph.install(f); graph.advance(.1);
            near(f.bulk.getVoltageDiff(), 0, .00001, "cold isolated output has no stored energy");
            f.input.binding.setConnected(true); graph.analyze(); graph.advance(.3);
            double before = f.bulk.getVoltageDiff(), stored = f.bulk.voltdiff;
            double beforeEnergy = .5 * (secondary ? .000470 : .000047) * before * before;
            double magnetic = secondary ? magneticEnergy(f.transformer) : 0;
            require(before > (secondary ? 13 : 150), "actual source charged real storage");
            long revision = f.input.binding.getConnectionRevision();
            f.input.binding.setConnected(false);
            require(f.input.linePole.position == 1 && f.input.returnPole.position == 1 &&
                f.input.binding.getConnectionRevision() == revision + 1,
                "OFF opens both actual input poles through one source binding");
            near(f.bulk.voltdiff, stored, 0, "OFF command does not reset capacitor storage");
            graph.analyze();
            near(f.bulk.voltdiff, stored, 0, "OFF reanalysis preserves capacitor storage");
            graph.step();
            double first = f.bulk.getVoltageDiff();
            require(first > before * .95 && .5 * f.bulk.capacitance * first * first <=
                beforeEnergy + magnetic + .0001, "first OFF step preserves bounded stored energy");
            // Let the existing coupled magnetic storage settle before the independent RC oracle.
            if (secondary) graph.advance(.05);
            double initial = f.bulk.getVoltageDiff(), initialTime = sim.t;
            double previous = initial, dissipated = 0;
            double capacitance = secondary ? .000470 : .000047;
            double load = secondary ? 1000 : 20000;
            double bleed = openBleed ? 1e12 : (secondary ? 10000 : 100000);
            double tau = capacitance / (1 / load + 1 / bleed);
            int count = (int)Math.round(.5 / dt);
            for (int n = 0; n < count; n++) {
                graph.step();
                double voltage = f.bulk.getVoltageDiff();
                require(voltage > 0 && voltage <= previous + .00001,
                    "source OFF has monotonic passive storage decay");
                dissipated += .5 * (voltage * voltage + previous * previous) *
                    (1 / load + 1 / bleed) * dt;
                previous = voltage;
            }
            double elapsed = sim.t - initialTime, after = f.bulk.getVoltageDiff();
            double expected = initial * Math.exp(-elapsed / tau);
            near(after, expected, initial * .003 + .0002, "independent passive RC discharge");
            double released = .5 * capacitance * (initial * initial - after * after);
            near(dissipated, released, .001 * (.5 * capacitance * initial * initial) + .00001,
                "lost capacitor energy becomes real load and bleeder dissipation");
            double ratio = after / initial;
            if (!openBleed) {
                // Fixed chunks stay within the existing per-operation step and wall budgets.
                for (int n = 0; n < (secondary ? 5 : 10); n++) graph.advance(.5);
                require(f.bulk.getVoltageDiff() < .25 && !f.input.binding.isConnected(),
                    "accepted physical discharge reaches existing active-test voltage threshold");
                double discharged = Math.abs(f.bulk.getVoltageDiff());
                graph.advance(.1);
                require(Math.abs(f.bulk.getVoltageDiff()) <= discharged + .00001,
                    "disconnected discharged output cannot sustain new energy");
            }
            row("storage-off", dt, secondary, "\"bleederOpen\":" + openBleed +
                ",\"beforeOffVolts\":" + before + ",\"firstOffVolts\":" + first +
                ",\"rcStartVolts\":" + initial + ",\"rcEndVolts\":" + after +
                ",\"elapsedSeconds\":" + elapsed + ",\"expectedTauSeconds\":" + tau +
                ",\"expectedEndVolts\":" + expected + ",\"releasedJoules\":" + released +
                ",\"dissipatedJoules\":" + dissipated +
                ",\"finalVolts\":" + f.bulk.getVoltageDiff());
            return ratio;
        } finally { graph.close(); }
    }

    private void unfiltered(double dt) {
        Graph graph = new Graph(dt);
        try {
            E05ElectricalFixtures.Fixture f = fixture(false);
            f.elements.remove(f.bulk); f.bulk.delete(); f.bulk = null;
            graph.install(f); graph.advance(.2);
            Stats raw = sample(graph, .1, true);
            require(raw.min < .1 && raw.max > 160 && raw.ripple() > 150 &&
                raw.mean > 95 && raw.mean < 115, "removing real storage removes bulk filtering");
            f.input.binding.setConnected(false); graph.analyze(); graph.advance(.1);
            near(f.load.getVoltageDiff(), 0, .00001, "no source and no capacitor cannot power load");
            row("no-storage", dt, false, "\"unfiltered\":" + raw.json());
        } finally { graph.close(); }
    }

    private void fuse(double dt) {
        Graph graph = new Graph(dt);
        try {
            E05ElectricalFixtures.Fixture f = fixture(false);
            graph.install(f); graph.advance(.3);
            require(!f.fuse.blown, "fuse healthy after cold capacitor inrush");
            f.load.setResistance(.1); graph.analyze();
            double predictedHeat = f.fuse.heat, heatBeforeTrials = f.fuse.heat;
            for (int n = 0; n < 32; n++) f.fuse.startIteration();
            near(f.fuse.heat, heatBeforeTrials, 0, "unaccepted trial iterations cannot age fuse");
            double overloadStarted = sim.t;
            int limit = (int)Math.round(.2 / dt), accepted = 0;
            while (!f.fuse.blown && accepted < limit) {
                graph.step(); accepted++;
                double current = f.fuse.getCurrent();
                predictedHeat = Math.max(0, predictedHeat + (current * current - .1 / 3) * dt);
                near(f.fuse.heat, predictedHeat, 1e-9, "accepted independent I-squared-t accumulation");
            }
            require(f.fuse.blown && f.fuse.heat >= .1 && accepted > 0 && accepted < limit,
                "real overload crosses the declared fuse damage threshold");
            double tripSeconds = sim.t - overloadStarted, tripHeat = f.fuse.heat;
            graph.advance(.15);
            Stats failed = sample(graph, .1, true);
            require(failed.sourceRms < 1e-6 && failed.mean < .001,
                "blown physical fuse collapses source current and overloaded output");
            f.load.setResistance(20000);
            f.input.binding.setConnected(false); f.fuse.reset();
            require(f.fuse.blown && f.fuse.heat == tripHeat, "OFF and reset cannot heal fuse damage");
            f.input.binding.setConnected(true); graph.analyze(); graph.advance(.2);
            require(f.fuse.blown && f.bulk.getVoltageDiff() < .01,
                "power cycling damaged fuse cannot recharge storage");
            ProtectionFuseElm old = f.fuse;
            ProtectionFuseElm replacement = E05AcInputModel.fuse(old.getPost(0), old.getPost(1));
            int liveIndex = sim.elmList.indexOf(old), fixtureIndex = f.elements.indexOf(old);
            require(liveIndex >= 0 && fixtureIndex >= 0, "replacement targets exact failed owner");
            sim.elmList.set(liveIndex, replacement);
            if (sim.elmList != f.elements) f.elements.set(fixtureIndex, replacement);
            f.fuse = replacement; old.delete();
            graph.analyze(); graph.advance(.3);
            require(!replacement.blown && replacement != old && f.bulk.getVoltageDiff() > 150,
                "fresh physical replacement restores actual rectifier energy delivery");
            row("fuse", dt, false, "\"acceptedOverloadSteps\":" + accepted +
                ",\"tripSeconds\":" + tripSeconds + ",\"tripHeat\":" + tripHeat +
                ",\"failedSourceRmsAmps\":" + failed.sourceRms + ",\"failedRectifier\":" + failed.json() +
                ",\"replacementBulkVolts\":" + f.bulk.getVoltageDiff());
        } finally { graph.close(); }
    }

    private void transformerLoadAndIsolation(double dt) {
        Graph graph = new Graph(dt);
        try {
            E05ElectricalFixtures.Fixture f = E05ElectricalFixtures.transformerReference(0, 0, false);
            graph.install(f); graph.advance(.3);
            Stats normal = transformerSample(graph, .1);
            f.load.setResistance(500); graph.analyze(); graph.advance(.3);
            Stats loaded = transformerSample(graph, .1);
            require(loaded.loadRms > normal.loadRms * 1.9 &&
                loaded.loadEnergy > normal.loadEnergy * 1.8,
                "transformer load current and real delivered energy respond to load");
            require(f.transformer.getNode(2) != f.transformer.getNode(3),
                "loaded transformer preserves separate actual returns");
            f.input.linePole.toggle(); graph.analyze(); graph.advance(.2);
            Stats lineOpen = transformerSample(graph, .1);
            require(lineOpen.loadRms < .000001 && lineOpen.sourceRms < .000001,
                "opening actual line pole prevents sustained isolated output");
            f.input.linePole.toggle(); f.input.returnPole.toggle();
            graph.analyze(); graph.advance(.2);
            Stats returnOpen = transformerSample(graph, .1);
            require(returnOpen.loadRms < .000001 && returnOpen.sourceRms < .000001,
                "opening actual return pole prevents sustained isolated output");
            row("transformer-load-isolation", dt, true, "\"normal\":" + normal.json() +
                ",\"doubleLoad\":" + loaded.json() +
                ",\"lineOpenLoadRmsAmps\":" + lineOpen.loadRms +
                ",\"returnOpenLoadRmsAmps\":" + returnOpen.loadRms);
        } finally { graph.close(); }
    }

    private Stats transformerSample(Graph graph, double duration) {
        E05ElectricalFixtures.Fixture f = graph.fixture;
        Stats stats = new Stats();
        double startEnergy = magneticEnergy(f.transformer);
        double previousSource = -f.input.source.getPower();
        double previousLoss = square(f.input.impedance.getCurrent()) * 22;
        double previousLoad = square(f.load.getVoltageDiff()) / f.load.getResistance();
        int count = (int)Math.round(duration / sim.timeStep);
        for (int n = 0; n < count; n++) {
            graph.step();
            double source = -f.input.source.getPower();
            double loss = square(f.input.impedance.getCurrent()) * 22;
            double voltage = f.load.getVoltageDiff(), loadCurrent = f.load.getCurrent();
            double load = square(voltage) / f.load.getResistance();
            stats.mean += voltage; stats.loadMean += loadCurrent;
            stats.min = Math.min(stats.min, voltage); stats.max = Math.max(stats.max, voltage);
            stats.sourceEnergy += .5 * (source + previousSource) * sim.timeStep;
            stats.seriesEnergy += .5 * (loss + previousLoss) * sim.timeStep;
            stats.loadEnergy += .5 * (load + previousLoad) * sim.timeStep;
            stats.sourceSquare += square(f.input.source.getCurrent());
            stats.loadSquare += square(f.load.getCurrent());
            previousSource = source; previousLoss = loss; previousLoad = load;
        }
        stats.mean /= count; stats.loadMean /= count;
        stats.sourceRms = Math.sqrt(stats.sourceSquare / count);
        stats.loadRms = Math.sqrt(stats.loadSquare / count);
        stats.magneticChange = magneticEnergy(f.transformer) - startEnergy;
        near(stats.sourceEnergy - stats.seriesEnergy - stats.loadEnergy, stats.magneticChange,
            .0001, "independent source/load/series/magnetic energy balance");
        return stats;
    }

    private Stats sample(Graph graph, double duration, boolean checkRectifier) {
        E05ElectricalFixtures.Fixture f = graph.fixture;
        Stats stats = new Stats();
        int count = (int)Math.round(duration / sim.timeStep);
        double previousCharging = f.bulk == null ? 0 : f.bulk.getCurrent();
        double previousTime = sim.t;
        for (int n = 0; n < count; n++) {
            graph.step();
            double voltage = f.load.getVoltageDiff(), load = f.load.getCurrent();
            require(PowerDomainContract.finite(voltage) && PowerDomainContract.finite(load),
                "finite accepted rectifier sample");
            stats.mean += voltage; stats.loadMean += load;
            stats.min = Math.min(stats.min, voltage); stats.max = Math.max(stats.max, voltage);
            stats.sourceSquare += square(f.input.source.getCurrent());
            if (checkRectifier) {
                double capacitor = f.bulk == null ? 0 : f.bulk.getCurrent();
                double delivered = f.bridge.diodes[0].getCurrent() + f.bridge.diodes[1].getCurrent();
                // The solver accepts <=.01 V junction updates before another tangent solve.
                // Published Shockley currents can differ from that solved tangent by this
                // independent exact exponential remainder. Never alter the actual currents.
                require(sim.converged && sim.subIterations <= 100,
                    "ordinary accepted diode convergence excludes escalated gmin");
                double residual = Math.abs(delivered - capacitor - load - f.bleed.getCurrent());
                double a = .01 / .05173;
                double remainder = 1 - Math.exp(a) * (1 - a);
                double bound = (Math.max(0, f.bridge.diodes[0].getCurrent() +
                    1.7143528192808883e-7) + Math.max(0, f.bridge.diodes[1].getCurrent() +
                    1.7143528192808883e-7)) * remainder + 1e-9;
                stats.maxKclResidual = Math.max(stats.maxKclResidual, residual);
                stats.maxKclBound = Math.max(stats.maxKclBound, bound);
                stats.maxNormalizedKcl = Math.max(stats.maxNormalizedKcl, residual / bound);
                stats.maxSubIterations = Math.max(stats.maxSubIterations, sim.subIterations);
                if (residual > 1e-6) stats.overOriginalMicroampSamples++;
                require(PowerDomainContract.finite(residual) && residual <= bound,
                    "published-current KCL is within the declared nonlinear approximation: " +
                    residual + " A, analytic bound " + bound + " A");
                double ac = f.bridge.diodes[2].getPostVoltage(1) -
                    f.bridge.diodes[3].getPostVoltage(1);
                double primary = f.transformer == null ? ac :
                    f.transformer.getPostVoltage(0) - f.transformer.getPostVoltage(2);
                near(f.input.source.getVoltageDiff(), f.input.impedance.getVoltageDiff() +
                    f.fuse.getVoltageDiff() + primary, .000001,
                    "source impedance and actual input winding satisfy voltage balance");
                if (f.bridge.diodes[0].getCurrent() > .0001)
                    near(ac, f.bridge.diodes[0].getVoltageDiff() + voltage +
                        f.bridge.diodes[3].getVoltageDiff(), .000001,
                        "positive conducting bridge loop has two real diode drops");
                if (f.bridge.diodes[1].getCurrent() > .0001)
                    near(-ac, f.bridge.diodes[1].getVoltageDiff() + voltage +
                        f.bridge.diodes[2].getVoltageDiff(), .000001,
                        "negative conducting bridge loop has two real diode drops");
                if (f.bulk != null && previousCharging < .0001 && capacitor >= .0001) {
                    double crossing = previousTime + (.0001 - previousCharging) *
                        (sim.t - previousTime) / (capacitor - previousCharging);
                    if (stats.pulses == 0) stats.firstPulse = crossing;
                    stats.lastPulse = crossing; stats.pulses++;
                }
                previousCharging = capacitor;
            }
            previousTime = sim.t;
        }
        stats.mean /= count; stats.loadMean /= count;
        stats.sourceRms = Math.sqrt(stats.sourceSquare / count);
        return stats;
    }

    private E05ElectricalFixtures.Fixture fixture(boolean secondary) {
        return secondary ? E05ElectricalFixtures.isolatedTransformerBulk() :
            E05ElectricalFixtures.rectifierBulk();
    }

    private void row(String name, double dt, boolean secondary, String details) {
        if (rows.length() > 0) rows.append(',');
        rows.append("{\"case\":\"").append(name).append("\",\"timeStep\":").append(dt)
            .append(",\"isolatedSecondary\":").append(secondary).append(',').append(details).append('}');
    }

    private static double square(double value) { return value * value; }
    private static double magneticEnergy(TransformerElm transformer) {
        double i1 = transformer.current[0], i2 = transformer.current[1];
        return 2 * i1 * i1 + .02 * i2 * i2 + .3996 * i1 * i2;
    }
    private void require(boolean passed, String label) {
        assertions++; if (!passed) throw new AssertionError("E05 electrical: " + label);
    }
    private void near(double actual, double expected, double tolerance, String label) {
        require(PowerDomainContract.finite(actual) && Math.abs(actual - expected) <= tolerance,
            label + ": " + actual + " expected " + expected);
    }

    private static final class Stats {
        double mean, loadMean, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        double sourceSquare, loadSquare, sourceRms, loadRms;
        double firstPulse, lastPulse, sourceEnergy, seriesEnergy, loadEnergy, magneticChange;
        double maxKclResidual, maxKclBound, maxNormalizedKcl;
        int pulses, maxSubIterations, overOriginalMicroampSamples;
        double ripple() { return max - min; }
        double chargeFrequency() { return pulses < 2 ? 0 : (pulses - 1) / (lastPulse - firstPulse); }
        String json() {
            return "{\"meanVolts\":" + mean + ",\"rippleVolts\":" + ripple() +
                ",\"loadMeanAmps\":" + loadMean + ",\"chargePulses\":" + pulses +
                ",\"chargeFrequencyHz\":" + chargeFrequency() +
                ",\"sourceRmsAmps\":" + sourceRms + ",\"loadRmsAmps\":" + loadRms +
                ",\"sourceJoules\":" + sourceEnergy + ",\"seriesJoules\":" + seriesEnergy +
                ",\"loadJoules\":" + loadEnergy + ",\"magneticChangeJoules\":" + magneticChange +
                ",\"maxPublishedKclResidualAmps\":" + maxKclResidual +
                ",\"maxAnalyticKclBoundAmps\":" + maxKclBound +
                ",\"maxNormalizedKclResidual\":" + maxNormalizedKcl +
                ",\"maxSubIterations\":" + maxSubIterations +
                ",\"overOriginalOneMicroampSamples\":" + overOriginalMicroampSamples + "}";
        }
    }

    /** Small adapter solely for these cases; compiled execution retains the exact private permit. */
    private final class Graph {
        private final Vector<CircuitElm> original;
        private final PrivateSolverContext proof;
        private final boolean previousMeasurement;
        E05ElectricalFixtures.Fixture fixture;
        Graph(double dt) {
            E05AcInputModel.requireTimeStep(dt);
            original = nativeGraph ? sim.elmList : null;
            proof = nativeGraph ? null : PrivateSolverContext.open(sim);
            previousMeasurement = sim.a01MeasurementRunning;
            if (nativeGraph) {
                sim.t = 0; sim.timeStepAccum = 0; sim.timeStepCount = 0;
                sim.lastIterTime = 0; sim.stopMessage = null;
                sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
            }
            sim.timeStep = sim.minTimeStep = sim.maxTimeStep = dt;
            sim.adjustTimeStep = false; sim.a01MeasurementRunning = true;
        }
        void install(E05ElectricalFixtures.Fixture fixture) {
            this.fixture = fixture;
            if (fixture.bridge != null) for (DiodeElm diode : fixture.bridge.diodes) {
                near(diode.model.saturationCurrent, 1.7143528192808883e-7, 0,
                    "declared default diode saturation current");
                near(diode.model.vscale, .05173, 1e-12, "declared default diode scale voltage");
                require(diode.model.seriesResistance == 0 && diode.model.breakdownVoltage == 0,
                    "KCL approximation contract excludes series resistance and breakdown");
            }
            if (nativeGraph) sim.elmList = fixture.elements; else proof.install(fixture.elements);
            analyze();
        }
        void analyze() { if (nativeGraph) sim.solverExecutor.analyze(); else proof.analyze(); }
        void step() { if (nativeGraph) sim.solverExecutor.advanceSteps(1); else proof.advanceSteps(1); }
        void advance(double duration) {
            if (nativeGraph) sim.solverExecutor.advanceFor(duration); else proof.advanceFor(duration);
        }
        void close() {
            sim.a01MeasurementRunning = previousMeasurement;
            if (proof != null) proof.close();
            else {
                sim.elmList = original; sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
                if (fixture != null) for (CircuitElm element : fixture.elements) element.delete();
            }
        }
    }
}
