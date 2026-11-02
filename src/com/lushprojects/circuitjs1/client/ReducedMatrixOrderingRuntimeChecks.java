package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Vector;

/** Real CircuitJS transient and parent-isolation checks for reduced matrix ordering. */
final class ReducedMatrixOrderingRuntimeChecks {
    private static final double DT = .00001;
    private static final int STEPS = 64;
    private static final int RC_BRANCHES = 8;
    private static final double RESISTANCE = 1000;
    private static final double CAPACITANCE = .000001;

    private ReducedMatrixOrderingRuntimeChecks() { }

    static int run() {
        Trace baseline = observe(true);
        Trace ordered = observe(false);
        require(baseline.nonlinear && ordered.nonlinear,
            "both detached fixtures reach the real nonlinear CircuitJS path");
        require(baseline.matrixSize >= 16 && ordered.matrixSize == baseline.matrixSize &&
                baseline.matrixSize <= 128,
            "fixture remains inside the production ordering size bounds");
        require(!baseline.nontrivialMapping && ordered.nontrivialMapping,
            "only the enabled real analysis applies a nontrivial reduced mapping");
        require(Arrays.equals(baseline.nodeIds, ordered.nodeIds),
            "ordering preserves every fixture element node identity");
        require(baseline.events == STEPS && ordered.events == baseline.events &&
                baseline.samples.length == ordered.samples.length,
            "both variants deliver the same accepted-step event sequence");
        int width = baseline.samples.length / STEPS;
        for (int sample = 0; sample < baseline.samples.length; sample++) {
            double limit = sample % width == 0 ? 1e-12 : 1e-9;
            require(close(baseline.samples[sample], ordered.samples[sample], limit),
                "ordered transient stays within the independent numerical comparison bound at " + sample);
        }
        require(Arrays.equals(baseline.work, ordered.work),
            "ordering preserves analysis, stamping, factor, solve, iteration, and accepted-step work");
        require(baseline.sourceRange > .01 && ordered.sourceRange > .01,
            "AC-driven RC branches receive changing source values");
        return baseline.assertions + ordered.assertions + 7 + baseline.samples.length;
    }

    /** Called by the compiled A07 verifier with its real settled player owner. */
    static int verifyParentIsolation(CirSim parent) {
        require(parent != null && parent.isGeneratedRuntimeSettled() &&
                parent.generatedBoardInstance != null &&
                !parent.reducedOrderingDisabledForChecks,
            "parent isolation requires the actual settled player owner");
        require(CircuitElm.sim == parent && CirSim.theSim == parent,
            "parent isolation begins with the active CircuitJS owner");
        ParentSnapshot before = new ParentSnapshot(parent);
        Task41SimulationSnapshot fullState = Task41SimulationSnapshot.capture(parent);
        PrivateSolverContext proof = null;
        Throwable failure = null;
        int assertions = 0;
        try {
            proof = PrivateSolverContext.open(parent);
            Fixture fixture = fixture(parent);
            proof.install(fixture.graph);
            proof.analyze();
            require(parent.generatedBoardInstance == null && parent.elmList != null &&
                    parent.elmList != before.elements,
                "private analysis owns a detached graph while the player is suspended");
            require(parent.circuitNonLinear && parent.circuitMatrixSize >= 16 &&
                    parent.circuitMatrixSize <= 128,
                "private parent proof analyzes a real nonlinear matrix in the eligible size range");
            require(hasNontrivialMapping(parent.circuitRowInfo, parent.circuitMatrixSize),
                "private parent proof actually applies reduced ordering");
            assertions += 4;
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            if (proof != null) {
                try {
                    proof.close();
                } catch (Throwable cleanup) {
                    if (failure == null) failure = cleanup;
                    else if (failure != cleanup) failure.addSuppressed(cleanup);
                }
            }
        }
        if (failure == null) {
            try {
                fullState.assertRestored(parent);
                before.assertRestored(parent);
                require(CircuitElm.sim == parent && CirSim.theSim == parent,
                    "private ordering proof restores both CircuitJS singleton references");
                assertions += 3;
            } catch (Throwable problem) {
                failure = problem;
            }
        }
        if (failure != null) rethrow(failure);
        return assertions;
    }

    private static Trace observe(boolean disableOrdering) {
        CirSim priorElementSim = CircuitElm.sim;
        CirSim priorSingleton = CirSim.theSim;
        FixtureSim sim = null;
        Fixture fixture = null;
        try {
            sim = new FixtureSim();
            CircuitElm.sim = sim;
            fixture = fixture(sim);
            sim.elmList = fixture.graph;
            sim.reducedOrderingDisabledForChecks = disableOrdering;
            sim.a01MeasurementRunning = true;
            sim.solverExecutor.analyze();
            require(sim.circuitNonLinear && sim.circuitMatrixSize >= 16 &&
                    sim.circuitMatrixSize <= 128,
                "fixture has an eligible reduced nonlinear matrix");
            boolean nontrivial = hasNontrivialMapping(sim.circuitRowInfo, sim.circuitMatrixSize);
            require(disableOrdering ? !nontrivial : nontrivial,
                "reduced RowInfo mapping follows the requested ordering mode");

            sim.solverExecutor.advanceFor(32 * DT);
            Trace trace = new Trace(sim, fixture, nontrivial);
            final double startedAt = sim.t;
            final Fixture measuredFixture = fixture;
            final double[] previousCapacitorVoltage = new double[RC_BRANCHES];
            for (int branch = 0; branch < RC_BRANCHES; branch++)
                previousCapacitorVoltage[branch] = fixture.capacitors[branch].getVoltageDiff();
            resetCounters(sim);

            final FixtureSim active = sim;
            final Trace observed = trace;
            final int[] eventCount = {0};
            SolverEventQueue queue = sim.solverExecutor.events(null);
            for (int step = 0; step < STEPS; step++) {
                final int index = step;
                queue.schedule(startedAt + (step + .75) * DT, new SolverEventQueue.Action() {
                    public void fire(double due, double accepted) {
                        require(eventCount[0]++ == index,
                            "accepted-step events retain their serial order");
                        require(Math.abs(accepted - startedAt - (index + 1) * DT) < 1e-12 &&
                                accepted == active.t && active.a01AcceptedStepCount == index + 1,
                            "event observes the corresponding accepted solver step");
                        observeAcceptedStep(active, measuredFixture, observed, index,
                            previousCapacitorVoltage, accepted);
                    }
                });
            }
            sim.solverExecutor.advanceFor(STEPS * DT);
            require(eventCount[0] == STEPS &&
                    Math.abs(sim.t - startedAt - STEPS * DT) < 1e-12,
                "all declared accepted steps and observations complete");
            trace.events = eventCount[0];
            trace.work = workCounters(sim);
            require(trace.work[2] > 0 && trace.work[3] > 0 &&
                    trace.work[5] > 0 && trace.work[6] == STEPS,
                "measured trace records real factor, solve, nonlinear, and accepted-step work");
            for (int branch = 0; branch < RC_BRANCHES; branch++)
                trace.sourceRange = Math.max(trace.sourceRange,
                    trace.sourceMaximum[branch] - trace.sourceMinimum[branch]);
            trace.assertions += 2;
            if (!disableOrdering) verifyOrderedResetReplay(sim, fixture, trace);
            return trace;
        } finally {
            try {
                if (sim != null) {
                    sim.a01MeasurementRunning = false;
                    sim.reducedOrderingDisabledForChecks = false;
                    sim.elmList = new Vector<CircuitElm>();
                    sim.solverExecutor.retire();
                    sim.circuitMatrix = null;
                    if (fixture != null)
                        for (CircuitElm element : fixture.graph) element.delete();
                    require(!sim.solverExecutor.isUnavailable(),
                        "detached runtime fixture releases solver ownership");
                }
            } finally {
                CircuitElm.sim = priorElementSim;
                CirSim.theSim = priorSingleton;
            }
        }
    }

    private static void verifyOrderedResetReplay(final FixtureSim sim, final Fixture fixture,
            Trace first) {
        RowInfo[] oldInfo = first.rowInfoObjects;
        sim.resetAction();
        require(sim.t == 0 && sim.elmList == fixture.graph,
            "detached CircuitJS reset returns the same physical graph to its initial time");
        sim.solverExecutor.analyze();
        require(sim.circuitRowInfo != first.rowInfoArray &&
                sim.circuitRowInfo.length == oldInfo.length &&
                sim.circuitMatrixSize == first.matrixSize && sim.circuitNonLinear &&
                hasNontrivialMapping(sim.circuitRowInfo, sim.circuitMatrixSize),
            "reset analysis creates fresh RowInfo objects and reapplies a nontrivial mapping");
        for (int index = 0; index < oldInfo.length; index++)
            if (oldInfo[index] != null)
                require(sim.circuitRowInfo[index] != oldInfo[index],
                    "reset analysis does not retain stale RowInfo objects at " + index);
        require(Arrays.equals(first.rowMappings, rowMappings(sim.circuitRowInfo)),
            "deterministic reset reproduces every reduced row and column map");
        require(Arrays.equals(first.nodeIds, nodeIds(fixture)),
            "reset and reanalysis preserve all physical node identities");

        sim.solverExecutor.advanceFor(32 * DT);
        final Trace replay = new Trace(sim, fixture,
            hasNontrivialMapping(sim.circuitRowInfo, sim.circuitMatrixSize));
        require(Math.abs(replay.startedAt - first.startedAt) < 1e-12,
            "reset replay returns to the same independently observed start time");
        final double[] previousCapacitorVoltage = new double[RC_BRANCHES];
        for (int branch = 0; branch < RC_BRANCHES; branch++)
            previousCapacitorVoltage[branch] = fixture.capacitors[branch].getVoltageDiff();
        resetCounters(sim);
        final int[] eventCount = {0};
        final FixtureSim active = sim;
        SolverEventQueue queue = sim.solverExecutor.events(null);
        queue.schedule(replay.startedAt + .75 * DT, new SolverEventQueue.Action() {
            public void fire(double due, double accepted) {
                require(eventCount[0]++ == 0 && Math.abs(accepted - replay.startedAt - DT) < 1e-12 &&
                        accepted == active.t && active.a01AcceptedStepCount == 1,
                    "reset replay emits the same first accepted-step event");
                observeAcceptedStep(active, fixture, replay, 0,
                    previousCapacitorVoltage, accepted);
            }
        });
        sim.solverExecutor.advanceFor(DT);
        require(eventCount[0] == 1 && replay.samples.length == first.samples.length &&
                Arrays.equals(first.rowMappings, replay.rowMappings),
            "reset replay completes one bounded step with the same reduced maps");
        for (int sample = 0; sample < first.sampleWidth; sample++) {
            double limit = sample == 0 ? 1e-12 : 1e-9;
            require(close(first.samples[sample], replay.samples[sample], limit),
                "reset replay reproduces the independent first-step trace at " + sample);
        }
        first.assertions += 5 + oldInfo.length + first.sampleWidth;
    }

    private static void observeAcceptedStep(FixtureSim sim, Fixture fixture, Trace trace,
            int step, double[] previousCapacitorVoltage, double accepted) {
        double diodeVoltage = fixture.diode.getVoltageDiff();
        double diodeCurrent = fixture.diode.getCurrent();
        double loadCurrent = fixture.diodeLoad.getCurrent();
        require(diodeVoltage > .3 && diodeVoltage < 1 && diodeCurrent > .004 &&
                diodeCurrent < .0047 && Math.abs(diodeCurrent - loadCurrent) < 1e-9 &&
                Math.abs(5 - diodeVoltage - RESISTANCE * loadCurrent) < 1e-9,
            "real forward diode satisfies independent current, KCL, KVL, and envelope checks");

        int offset = step * trace.sampleWidth;
        trace.samples[offset++] = accepted;
        trace.samples[offset++] = diodeVoltage;
        trace.samples[offset++] = diodeCurrent;
        trace.samples[offset++] = loadCurrent;
        for (int branch = 0; branch < RC_BRANCHES; branch++) {
            VoltageElm source = fixture.sources[branch];
            ResistorElm resistor = fixture.resistors[branch];
            CapacitorElm capacitor = fixture.capacitors[branch];
            double sourceVoltage = source.getVoltageDiff();
            double resistorVoltage = resistor.getVoltageDiff();
            double resistorCurrent = resistor.getCurrent();
            double capacitorVoltage = capacitor.getVoltageDiff();
            double capacitorCurrent = capacitor.getCurrent();
            require(Math.abs(sourceVoltage - resistorVoltage - capacitorVoltage) < 1e-9 &&
                    Math.abs(resistorVoltage - RESISTANCE * resistorCurrent) < 1e-9,
                "real RC branch satisfies independent source KVL and resistor Ohm law");
            require(Math.abs(resistorCurrent - capacitorCurrent) < 1e-9 &&
                    Math.abs(capacitorCurrent - CAPACITANCE *
                        (capacitorVoltage - previousCapacitorVoltage[branch]) / DT) < 1e-8,
                "real RC branch satisfies KCL and backward-Euler capacitor recurrence");
            trace.sourceMinimum[branch] = Math.min(trace.sourceMinimum[branch], sourceVoltage);
            trace.sourceMaximum[branch] = Math.max(trace.sourceMaximum[branch], sourceVoltage);
            trace.samples[offset++] = sourceVoltage;
            trace.samples[offset++] = resistorVoltage;
            trace.samples[offset++] = resistorCurrent;
            trace.samples[offset++] = capacitorVoltage;
            trace.samples[offset++] = capacitorCurrent;
            previousCapacitorVoltage[branch] = capacitorVoltage;
            trace.assertions += 2;
        }
        for (int index = 0; index < sim.nodeVoltages.length; index++) {
            double voltage = sim.nodeVoltages[index];
            require(SolverExecutionBoundary.finite(voltage),
                "full solved node and source-current vector stays finite");
            trace.samples[offset++] = voltage;
        }
        require(offset == (step + 1) * trace.sampleWidth,
            "each accepted step records its complete node and electrical sample");
        trace.assertions += 2;
    }

    private static Fixture fixture(CirSim sim) {
        Vector<CircuitElm> graph = new Vector<CircuitElm>();
        try {
            DCVoltageElm bias = end(new DCVoltageElm(64, 320), 64, 128);
            bias.maxVoltage = 5;
            bias.bias = 0;
            ResistorElm diodeLoad = end(new ResistorElm(64, 128), 256, 128);
            diodeLoad.setResistance(RESISTANCE);
            DiodeElm diode = end(new DiodeElm(256, 128), 256, 320);
            graph.add(bias);
            graph.add(diodeLoad);
            graph.add(diode);
            ground(graph, bias.getPost(0));
            ground(graph, diode.getPost(1));

            VoltageElm[] sources = new VoltageElm[RC_BRANCHES];
            ResistorElm[] resistors = new ResistorElm[RC_BRANCHES];
            CapacitorElm[] capacitors = new CapacitorElm[RC_BRANCHES];
            for (int index = 0; index < RC_BRANCHES; index++) {
                int x = 640;
                int y = 64 + 128 * index;
                VoltageElm source = end(new VoltageElm(x, y + 64, VoltageElm.WF_AC), x, y);
                source.frequency = 31 + 3 * index;
                source.maxVoltage = .2 + .025 * index;
                source.bias = 1.5;
                source.phaseShift = .1 * index;
                source.reset();
                ResistorElm resistor = end(new ResistorElm(x, y), x + 192, y);
                resistor.setResistance(RESISTANCE);
                CapacitorElm capacitor = end(new CapacitorElm(x + 192, y), x + 192, y + 64);
                capacitor.capacitance = CAPACITANCE;
                capacitor.initialVoltage = 0;
                capacitor.flags = CapacitorElm.FLAG_BACK_EULER;
                capacitor.reset();
                sources[index] = source;
                resistors[index] = resistor;
                capacitors[index] = capacitor;
                graph.add(source);
                graph.add(resistor);
                graph.add(capacitor);
                ground(graph, source.getPost(0));
                ground(graph, capacitor.getPost(1));
            }
            return new Fixture(graph, bias, diodeLoad, diode, sources, resistors, capacitors);
        } catch (Throwable failure) {
            for (CircuitElm element : graph) {
                try { element.delete(); }
                catch (Throwable cleanup) {
                    if (failure != cleanup) failure.addSuppressed(cleanup);
                }
            }
            rethrow(failure);
            return null;
        }
    }

    private static boolean hasNontrivialMapping(RowInfo[] infos, int matrixSize) {
        if (infos == null || matrixSize < 0) return false;
        int expectedColumn = 0;
        int expectedRow = 0;
        boolean changed = false;
        for (int index = 0; index < infos.length; index++) {
            RowInfo info = infos[index];
            if (info == null) return false;
            if (info.type == RowInfo.ROW_NORMAL) {
                if (info.mapCol != expectedColumn) changed = true;
                expectedColumn++;
            }
            if (!info.dropRow) {
                if (info.mapRow != expectedRow) changed = true;
                expectedRow++;
            }
        }
        if (expectedColumn != matrixSize || expectedRow != matrixSize)
            throw new AssertionError("Reduced RowInfo maps do not cover the analyzed matrix");
        return changed;
    }

    private static int[] nodeIds(Fixture fixture) {
        int count = 0;
        for (CircuitElm element : fixture.graph) count += element.getPostCount();
        int[] result = new int[count];
        int index = 0;
        for (CircuitElm element : fixture.graph)
            for (int post = 0; post < element.getPostCount(); post++)
                result[index++] = element.getNode(post);
        return result;
    }

    private static int[] rowMappings(RowInfo[] infos) {
        int[] result = new int[infos.length * 4];
        int offset = 0;
        for (RowInfo info : infos) {
            result[offset++] = info.type;
            result[offset++] = info.mapCol;
            result[offset++] = info.mapRow;
            result[offset++] = info.dropRow ? 1 : 0;
        }
        return result;
    }

    private static void resetCounters(CirSim sim) {
        sim.a01AnalysisCount = sim.a01StampCount = sim.a01FactorizationCount =
            sim.a01SolveCount = sim.a01IterationCount = sim.a01SubIterationCount =
            sim.a01AcceptedStepCount = 0;
    }

    private static long[] workCounters(CirSim sim) {
        return new long[] {sim.a01AnalysisCount, sim.a01StampCount,
            sim.a01FactorizationCount, sim.a01SolveCount, sim.a01IterationCount,
            sim.a01SubIterationCount, sim.a01AcceptedStepCount};
    }

    private static <T extends CircuitElm> T end(T element, int x, int y) {
        element.x2 = x;
        element.y2 = y;
        element.setPoints();
        return element;
    }

    private static void ground(Vector<CircuitElm> graph, Point at) {
        graph.add(end(new GroundElm(at.x, at.y), at.x, at.y + 32));
    }

    private static boolean close(double left, double right, double limit) {
        return SolverExecutionBoundary.finite(left) && SolverExecutionBoundary.finite(right) &&
            Math.abs(left - right) <= limit;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("Reduced matrix ordering runtime: " + message);
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error)failure;
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        throw new AssertionError("Reduced matrix ordering parent proof failed", failure);
    }

    private static final class Fixture {
        final Vector<CircuitElm> graph;
        final DCVoltageElm bias;
        final ResistorElm diodeLoad;
        final DiodeElm diode;
        final VoltageElm[] sources;
        final ResistorElm[] resistors;
        final CapacitorElm[] capacitors;
        Fixture(Vector<CircuitElm> graph, DCVoltageElm bias, ResistorElm diodeLoad,
                DiodeElm diode, VoltageElm[] sources, ResistorElm[] resistors,
                CapacitorElm[] capacitors) {
            this.graph = graph;
            this.bias = bias;
            this.diodeLoad = diodeLoad;
            this.diode = diode;
            this.sources = sources;
            this.resistors = resistors;
            this.capacitors = capacitors;
        }
    }

    private static final class Trace {
        final int matrixSize;
        final boolean nonlinear, nontrivialMapping;
        final int[] nodeIds;
        final int[] rowMappings;
        final RowInfo[] rowInfoArray;
        final RowInfo[] rowInfoObjects;
        final int sampleWidth;
        final double[] samples;
        final double[] sourceMinimum = new double[RC_BRANCHES];
        final double[] sourceMaximum = new double[RC_BRANCHES];
        long[] work;
        int events, assertions;
        double sourceRange, startedAt;

        Trace(CirSim sim, Fixture fixture, boolean nontrivialMapping) {
            matrixSize = sim.circuitMatrixSize;
            nonlinear = sim.circuitNonLinear;
            this.nontrivialMapping = nontrivialMapping;
            nodeIds = nodeIds(fixture);
            if (sim.circuitRowInfo == null)
                throw new AssertionError("Reduced matrix ordering runtime: missing RowInfo map");
            rowInfoArray = sim.circuitRowInfo;
            rowInfoObjects = new RowInfo[sim.circuitRowInfo.length];
            System.arraycopy(sim.circuitRowInfo, 0, rowInfoObjects, 0,
                sim.circuitRowInfo.length);
            rowMappings = rowMappings(sim.circuitRowInfo);
            startedAt = sim.t;
            if (sim.nodeVoltages == null)
                throw new AssertionError("Reduced matrix ordering runtime: missing solved voltage vector");
            sampleWidth = 4 + 5 * RC_BRANCHES + sim.nodeVoltages.length;
            samples = new double[STEPS * sampleWidth];
            for (int branch = 0; branch < RC_BRANCHES; branch++) {
                sourceMinimum[branch] = Double.POSITIVE_INFINITY;
                sourceMaximum[branch] = Double.NEGATIVE_INFINITY;
            }
            assertions += 4;
        }
    }

    private static final class FixtureSim extends CirSim {
        FixtureSim() {
            gridSize = 16;
            gridMask = ~15;
            gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>();
            redoStack = new Vector<String>();
            scopes = new Scope[20];
            scopeColCount = new int[20];
            maxTimeStep = minTimeStep = timeStep = DT;
            adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message;
            circuitMatrix = null;
            stopElm = element;
            setSimRunning(false);
            analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }

    private static final class ParentSnapshot {
        final CirSim sim;
        final Vector<CircuitElm> elements;
        final Object wireInfo, adjustables, undo, redo, matrix, rightSide, origMatrix, origRightSide;
        final Object rowInfo, permutation, nodeVoltages, lastNodeVoltages, board, challenge;
        final Object modificationController, pcbController, instrumentController, scopes;
        final int[] scopeColumnValues;
        final Object powerController, bindings;
        final RowInfoSnapshot rowFields;
        final GeneratedExternalPowerBindings.SavedControls controls;
        final BoardPowerState powerState;
        final CirSim elementOwner, singletonOwner;
        final boolean orderingDisabled, simRunning, measurementOverlay, measurementSolverRestored;
        final boolean measurementRunning, analyzeFlag, dcAnalysisFlag;
        final long tBits, acceptedSteps, analyses, stamps, factors, solves, iterations, trials;

        ParentSnapshot(CirSim sim) {
            this.sim = sim;
            elements = sim.elmList;
            wireInfo = sim.wireInfoList;
            adjustables = sim.adjustables;
            undo = sim.undoStack;
            redo = sim.redoStack;
            matrix = sim.circuitMatrix;
            rightSide = sim.circuitRightSide;
            origMatrix = sim.origMatrix;
            origRightSide = sim.origRightSide;
            rowInfo = sim.circuitRowInfo;
            permutation = sim.circuitPermute;
            nodeVoltages = sim.nodeVoltages;
            lastNodeVoltages = sim.lastNodeVoltages;
            board = sim.generatedBoardInstance;
            challenge = sim.generatedChallengeController;
            modificationController = sim.boardModificationController;
            pcbController = sim.pcbWorkbenchController;
            instrumentController = sim.instrumentController;
            scopes = sim.scopes;
            scopeColumnValues = sim.scopeColCount == null ? null :
                new int[sim.scopeColCount.length];
            if (scopeColumnValues != null)
                System.arraycopy(sim.scopeColCount, 0, scopeColumnValues, 0,
                    sim.scopeColCount.length);
            powerController = sim.boardPowerController;
            powerState = sim.boardPowerController.getState();
            GeneratedExternalPowerBindings currentBindings =
                sim.boardPowerController.getBindingsForDeveloperVerification();
            bindings = currentBindings;
            controls = currentBindings == null ? null : currentBindings.saveControls();
            rowFields = new RowInfoSnapshot(sim.circuitRowInfo);
            elementOwner = CircuitElm.sim;
            singletonOwner = CirSim.theSim;
            orderingDisabled = sim.reducedOrderingDisabledForChecks;
            simRunning = sim.simRunning;
            measurementOverlay = sim.activeMeasurementOverlay;
            measurementSolverRestored = sim.activeMeasurementSolverRestored;
            measurementRunning = sim.a01MeasurementRunning;
            analyzeFlag = sim.analyzeFlag;
            dcAnalysisFlag = sim.dcAnalysisFlag;
            tBits = Double.doubleToLongBits(sim.t);
            acceptedSteps = sim.a01AcceptedStepCount;
            analyses = sim.a01AnalysisCount;
            stamps = sim.a01StampCount;
            factors = sim.a01FactorizationCount;
            solves = sim.a01SolveCount;
            iterations = sim.a01IterationCount;
            trials = sim.a01SubIterationCount;
        }

        void assertRestored(CirSim target) {
            require(target == sim && target.elmList == elements && target.wireInfoList == wireInfo &&
                    target.adjustables == adjustables && target.undoStack == undo &&
                    target.redoStack == redo && target.circuitMatrix == matrix &&
                    target.circuitRightSide == rightSide && target.origMatrix == origMatrix &&
                    target.origRightSide == origRightSide && target.circuitRowInfo == rowInfo &&
                    target.circuitPermute == permutation && target.nodeVoltages == nodeVoltages &&
                    target.lastNodeVoltages == lastNodeVoltages &&
                    target.generatedBoardInstance == board &&
                    target.generatedChallengeController == challenge &&
                    target.boardModificationController == modificationController &&
                    target.pcbWorkbenchController == pcbController &&
                    target.instrumentController == instrumentController && target.scopes == scopes &&
                    target.boardPowerController == powerController,
                "parent solver, board, instrument, and scope references are restored");
            require(Arrays.equals(target.scopeColCount, scopeColumnValues),
                "parent scope column values are restored");
            require(target.boardPowerController.getState() == powerState &&
                    target.boardPowerController.getBindingsForDeveloperVerification() == bindings &&
                    (controls == null || controls.matches()),
                "parent power state and all captured external controls are restored");
            require(target.reducedOrderingDisabledForChecks == orderingDisabled &&
                    target.simRunning == simRunning &&
                    target.activeMeasurementOverlay == measurementOverlay &&
                    target.activeMeasurementSolverRestored == measurementSolverRestored &&
                    target.a01MeasurementRunning == measurementRunning &&
                    target.analyzeFlag == analyzeFlag && target.dcAnalysisFlag == dcAnalysisFlag &&
                    Double.doubleToLongBits(target.t) == tBits &&
                    target.a01AcceptedStepCount == acceptedSteps && target.a01AnalysisCount == analyses &&
                    target.a01StampCount == stamps && target.a01FactorizationCount == factors &&
                    target.a01SolveCount == solves && target.a01IterationCount == iterations &&
                    target.a01SubIterationCount == trials,
                "parent simulation, measurement, counter, and ordering-check state is restored");
            require(CircuitElm.sim == elementOwner && CirSim.theSim == singletonOwner,
                "parent static solver owners are restored");
            rowFields.assertUnchanged(target.circuitRowInfo);
        }
    }

    private static final class RowInfoSnapshot {
        final RowInfo[] reference;
        final RowInfo[] entries;
        final int[] type, mapColumn, mapRow;
        final long[] value;
        final boolean[] rowChanges, leftChanges, dropped;

        RowInfoSnapshot(RowInfo[] reference) {
            this.reference = reference;
            if (reference == null) {
                entries = null;
                type = mapColumn = mapRow = null;
                value = null;
                rowChanges = leftChanges = dropped = null;
                return;
            }
            entries = new RowInfo[reference.length];
            type = new int[reference.length];
            mapColumn = new int[reference.length];
            mapRow = new int[reference.length];
            value = new long[reference.length];
            rowChanges = new boolean[reference.length];
            leftChanges = new boolean[reference.length];
            dropped = new boolean[reference.length];
            for (int index = 0; index < reference.length; index++) {
                RowInfo row = reference[index];
                entries[index] = row;
                if (row == null) continue;
                type[index] = row.type;
                mapColumn[index] = row.mapCol;
                mapRow[index] = row.mapRow;
                value[index] = Double.doubleToLongBits(row.value);
                rowChanges[index] = row.rsChanges;
                leftChanges[index] = row.lsChanges;
                dropped[index] = row.dropRow;
            }
        }

        void assertUnchanged(RowInfo[] current) {
            require(current == reference, "parent RowInfo array reference is restored");
            if (reference == null) return;
            require(current.length == entries.length, "parent RowInfo array size is restored");
            for (int index = 0; index < entries.length; index++) {
                RowInfo row = current[index];
                require(row == entries[index], "parent RowInfo object reference is restored at " + index);
                if (row == null) continue;
                require(row.type == type[index] && row.mapCol == mapColumn[index] &&
                        row.mapRow == mapRow[index] &&
                        Double.doubleToLongBits(row.value) == value[index] &&
                        row.rsChanges == rowChanges[index] && row.lsChanges == leftChanges[index] &&
                        row.dropRow == dropped[index],
                    "all parent RowInfo fields are unchanged at " + index);
            }
        }
    }
}
