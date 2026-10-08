package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.Scheduler;

/** Compiled-only mechanics and owner proof for the two private E06 candidates. */
final class E06ConverterDeveloperVerifier {
    private static final int BATCH_STEPS = 128;
    private static final double ENABLE_SECONDS = .05, END_SECONDS = .2;
    private final CirSim sim;
    private final boolean forcedFailure;
    private final long started = System.currentTimeMillis();
    private final StringBuilder rows = new StringBuilder();
    private int assertions, caseCount;
    private boolean finished, allRestored = true, allDisposed = true, factoryExecuted;

    private E06ConverterDeveloperVerifier(CirSim sim, boolean forcedFailure) {
        this.sim = sim; this.forcedFailure = forcedFailure;
    }

    static void start(final CirSim sim, final boolean forcedFailure) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                E06ConverterDeveloperVerifier verifier = new E06ConverterDeveloperVerifier(sim, forcedFailure);
                try {
                    verifier.require(sim.troubleshootDebug && sim.troubleshootE06Verification &&
                        sim.developerVerifierRunning, "E06 requires its explicit developer route");
                    verifier.beginCase(false);
                } catch (Throwable failure) { verifier.finish(failure); }
            }
        });
    }

    private void beginCase(boolean averaged) {
        final Case run = new Case(averaged);
        try {
            run.snapshot = Task41SimulationSnapshot.capture(sim);
            run.proof = PrivateSolverContext.open(sim);
            sim.timeStep = sim.minTimeStep = sim.maxTimeStep = run.dt;
            sim.adjustTimeStep = false; sim.a01MeasurementRunning = true;
            factoryParity();
            run.fixture = averaged ? E06ConverterFixtures.averaged() : E06ConverterFixtures.detailed();
            require(run.fixture.opto.compElmList.size() == 3 &&
                run.fixture.opto.compElmList.get(0) instanceof DiodeElm &&
                run.fixture.opto.compElmList.get(1) instanceof CCCSElm &&
                run.fixture.opto.compElmList.get(2) instanceof NTransistorElm,
                "actual optocoupler retains the production factory composition");
            require("default".equals(run.fixture.opto.diode.modelName) &&
                "default".equals(run.fixture.opto.transistor.modelName), "declared optocoupler models");
            run.parameters = run.fixture.getParameterSummary();
            run.proof.install(run.fixture.elements); run.installed = true;
            require(sim.generatedBoardInstance == null &&
                sim.elmList.contains(run.fixture.opto), "fixture owns the private installed graph");
            run.proof.analyze(); run.analyzed = true;
            require(run.fixture.opto.getNode(1) != run.fixture.opto.getNode(3),
                "primary and secondary returns remain distinct solved nodes");
            run.defer();
        } catch (Throwable failure) { completeCase(run, failure); }
    }

    private void factoryParity() {
        String[] names = {"DiodeElm", "CCCSElm", "NTransistorElm"};
        for (int n = 0; n < names.length; n++) {
            CircuitElm element = CirSim.constructElement(new String(names[n]), 0, 0);
            try {
                require(n == 0 ? element instanceof DiodeElm : n == 1 ? element instanceof CCCSElm :
                    element instanceof NTransistorElm, "unmodified compiled factory dispatch for " + names[n]);
                if (n == 1) require(((CCCSElm)element).csize ==
                    (sim.smallGridCheckItem.getState() ? 1 : 2), "real GWT chip menu sizing");
            } finally { if (element != null) element.delete(); }
        }
        require(CirSim.constructElement(null, 0, 0) == null &&
            CirSim.constructElement(new String("UnknownE06Element"), 0, 0) == null,
            "compiled factory rejects null and unknown names");
        factoryExecuted = true;
    }

    private void advance(Case run) {
        try {
            if (run.acceptedSteps == run.enableStep) {
                require(Math.abs(sim.t - ENABLE_SECONDS) <= 1e-12,
                    "enable changes after exactly 50 ms accepted solver time");
                run.fixture.enable.setVoltage(5); run.proof.analyze(); run.enableChanged = true;
            }
            int boundary = run.enableChanged ? run.totalSteps : run.enableStep;
            int count = Math.min(BATCH_STEPS, boundary - run.acceptedSteps);
            run.proof.advanceSteps(count); run.acceptedSteps += count; run.batches++;
            run.sample();
            if (run.acceptedSteps < run.totalSteps) { run.defer(); return; }
            require(sim.a01AcceptedStepCount - run.previousCounters[6] == run.totalSteps &&
                Math.abs(sim.t - END_SECONDS) <= 1e-12, "exact 200 ms accepted execution");
            require(run.disabledActiveSamples == 0 && run.enabledActiveSamples > 0,
                "actual low and high enable causally control activation");
            if (run.averaged) require(run.deliverySamples > 0,
                "averaged accepted stage delivers actual electrical current");
            else require(run.disabledPulseSamples == 0 && run.enabledPulseSamples > 0 &&
                run.disabledGateMax < E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS &&
                run.highGateMax > E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS &&
                run.lowGateMax > E06ConverterContract.POWER_SWITCH_THRESHOLD_VOLTS,
                "detailed accepted powered gates follow actual enable");
            require(sim.stopMessage == null, "no unexpected CircuitJS stop");
            run.completed = true;
            if (forcedFailure && run.averaged) {
                run.forcedAfterAcceptedSolve = true;
                throw new IllegalStateException("e06-explicit-failure-after-install-and-accepted-solve");
            }
            completeCase(run, null);
        } catch (Throwable failure) { completeCase(run, failure); }
    }

    private void completeCase(Case run, Throwable failure) {
        if (run.closed) return;
        run.closed = true;
        run.elapsedSeconds = sim.t;
        run.trials = sim.a01SubIterationCount - run.previousCounters[5];
        run.observedAcceptedSteps = sim.a01AcceptedStepCount - run.previousCounters[6];
        run.stop = sim.stopMessage;
        long cleanupStarted = System.currentTimeMillis();
        Throwable cleanupFailure = null;
        try {
            sim.a01MeasurementRunning = run.previousMeasurement;
            if (run.proof != null) {
                run.proof.close(); run.privateCloseReturned = true;
                // close() restores the owner, then calls every installed element's delete().
                run.disposed = run.installed;
            }
            if (run.fixture != null && !run.installed) {
                for (CircuitElm element : run.fixture.elements) element.delete();
                run.disposed = true;
            }
        } catch (Throwable cleanup) { cleanupFailure = cleanup; }
        finally {
            DiodeElm.lastModelName = run.previousDiode; ZenerElm.lastZenerModelName = run.previousZener;
            TransistorElm.lastModelName = run.previousTransistor;
            MosfetElm.globalFlags = run.previousMosfetFlags; MosfetElm.lastBeta = run.previousBeta;
            sim.a01MeasurementRunning = run.previousMeasurement;
            sim.a01AnalysisCount = run.previousCounters[0]; sim.a01StampCount = run.previousCounters[1];
            sim.a01FactorizationCount = run.previousCounters[2]; sim.a01SolveCount = run.previousCounters[3];
            sim.a01IterationCount = run.previousCounters[4]; sim.a01SubIterationCount = run.previousCounters[5];
            sim.a01AcceptedStepCount = run.previousCounters[6];
        }
        try {
            if (run.snapshot != null) {
                run.snapshot.assertRestored(sim);
                require(!sim.solverExecutor.isUnavailable() && sim.isGeneratedRuntimeSettled(),
                    "private permit released to the settled exact player owner");
                if (run.fixture != null) for (CircuitElm element : run.fixture.elements)
                    require(!sim.elmList.contains(element), "private fixture element retired from player graph");
                run.restored = true;
            }
        } catch (Throwable cleanup) { cleanupFailure = retain(cleanupFailure, cleanup); }
        run.cleanupMs = System.currentTimeMillis() - cleanupStarted;
        run.wallMs = System.currentTimeMillis() - run.started;
        allRestored = allRestored && run.restored; allDisposed = allDisposed && run.disposed;
        failure = retain(failure, cleanupFailure);
        if (caseCount++ > 0) rows.append(',');
        rows.append(run.json(failure, cleanupFailure));
        run.proof = null; run.fixture = null;
        if (failure == null && !run.averaged) beginCase(true); else finish(failure);
    }

    private void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        String report = "{\"schema\":\"e06-compiled-mechanics-v1\",\"status\":" +
            quote(failure == null ? "PASS" : "FAIL") + ",\"fidelity\":\"NOT_QUALIFIED\"," +
            "\"execution\":\"UNMODIFIED_GWT_FACTORY_PRIVATE_PLAYER_PROOF\",\"forcedFailure\":" + forcedFailure +
            ",\"factoryExecuted\":" + factoryExecuted + ",\"assertions\":" + assertions +
            ",\"wallMs\":" + (System.currentTimeMillis() - started) + ",\"playerOwnerRestored\":" +
            (caseCount > 0 && allRestored) + ",\"candidateDisposed\":" + (caseCount > 0 && allDisposed) +
            ",\"cleanup\":" + quote(caseCount > 0 && allRestored && allDisposed ? "PASS" : "FAIL") +
            ",\"failure\":" + quote(failure == null ? null : failure.getMessage()) + ",\"cases\":[" + rows +
            "],\"limits\":[\"mechanics proof only; native energy/fidelity evidence remains separate\"," +
            "\"terminal metrics sample each deferred batch endpoint; solver validates all accepted node voltages\"," +
            "\"128-step deferred batches retain existing 500 ms operation and step-attempt guards\"," +
            "\"detailed switching is developer-only; averaged switching waveforms unsupported\"]}";
        sim.finishE06Verification(report, failure);
    }

    private void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new IllegalStateException(label);
    }
    private static Throwable retain(Throwable first, Throwable next) {
        if (next == null) return first;
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }
    private static double diff(CircuitElm element, int a, int b) {
        return element.getPostVoltage(a) - element.getPostVoltage(b);
    }
    private static String quote(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int n = 0; n < value.length(); n++) {
            char c = value.charAt(n);
            if (c == '\\' || c == '"') result.append('\\').append(c);
            else if (c < 32) {
                String hex = Integer.toHexString(c);
                result.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) result.append('0');
                result.append(hex);
            } else result.append(c);
        }
        return result.append('"').toString();
    }

    private final class Case {
        final boolean averaged;
        final double dt;
        final int enableStep, totalSteps;
        final long started = System.currentTimeMillis();
        final boolean previousMeasurement = sim.a01MeasurementRunning;
        final long[] previousCounters = {sim.a01AnalysisCount, sim.a01StampCount, sim.a01FactorizationCount,
            sim.a01SolveCount, sim.a01IterationCount, sim.a01SubIterationCount, sim.a01AcceptedStepCount};
        final String previousDiode = DiodeElm.lastModelName, previousZener = ZenerElm.lastZenerModelName;
        final String previousTransistor = TransistorElm.lastModelName;
        final int previousMosfetFlags = MosfetElm.globalFlags;
        final double previousBeta = MosfetElm.lastBeta;
        Task41SimulationSnapshot snapshot;
        PrivateSolverContext proof;
        E06ConverterFixtures.Fixture fixture;
        String parameters, stop;
        boolean installed, analyzed, enableChanged, completed, closed, restored, disposed, privateCloseReturned;
        boolean forcedAfterAcceptedSolve;
        int acceptedSteps, batches, samples, terminalSamples, sampledMaxSubIterations;
        int disabledActiveSamples, enabledActiveSamples, disabledPulseSamples, enabledPulseSamples, deliverySamples;
        double elapsedSeconds, outputMin, outputMax, outputSum, loadSum, finalBulk, finalFeedback;
        double disabledGateMax, highGateMax, lowGateMax;
        long trials, observedAcceptedSteps, wallMs, cleanupMs;
        Case(boolean averaged) {
            this.averaged = averaged;
            dt = averaged ? E06ConverterContract.MAX_AVERAGED_STEP_SECONDS : E06ConverterContract.MAX_DETAILED_STEP_SECONDS;
            enableStep = (int)Math.round(ENABLE_SECONDS / dt); totalSteps = (int)Math.round(END_SECONDS / dt);
        }
        void defer() {
            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                public void execute() { advance(Case.this); }
            });
        }
        void sample() {
            require(sim.stopMessage == null && sim.timeStep == dt, "finite accepted fixed-step graph has no stop");
            for (CircuitElm element : fixture.elements) for (int post = 0; post < element.getPostCount(); post++) {
                require(E06ConverterContract.finite(element.getPostVoltage(post)) &&
                    E06ConverterContract.finite(element.getCurrentIntoNode(post)), "finite actual accepted terminal V/I");
                terminalSamples++;
            }
            boolean active = averaged ? fixture.averaged.isActive() : fixture.pwm.isActive();
            double duty = averaged ? fixture.averaged.getDuty() : fixture.pwm.getDuty();
            double integral = averaged ? fixture.averaged.getIntegral() : fixture.pwm.getIntegral();
            require(E06ConverterContract.finite(duty) && duty >= 0 && duty <= E06ConverterContract.MAX_DUTY &&
                E06ConverterContract.finite(integral) && Math.abs(integral) <= E06ConverterContract.MAX_DUTY,
                "accepted control remains within its declared bounds");
            double output = fixture.load.getVoltageDiff();
            if (samples++ == 0) outputMin = outputMax = output;
            outputMin = Math.min(outputMin, output); outputMax = Math.max(outputMax, output);
            outputSum += output; loadSum += fixture.load.getCurrent();
            finalBulk = fixture.front.bulk.getVoltageDiff(); finalFeedback = diff(fixture.opto, 2, 3);
            sampledMaxSubIterations = Math.max(sampledMaxSubIterations, sim.subIterations);
            if (enableChanged) { if (active) enabledActiveSamples++; }
            else if (active) disabledActiveSamples++;
            if (averaged) {
                if (enableChanged && fixture.averaged.getOutputCurrent() > 1e-9) deliverySamples++;
            } else {
                boolean pulse = fixture.pwm.isPulseOn();
                double high = diff(fixture.highSwitch, 0, 1), low = diff(fixture.lowSwitch, 0, 1);
                if (enableChanged) {
                    if (pulse) enabledPulseSamples++;
                    highGateMax = Math.max(highGateMax, high); lowGateMax = Math.max(lowGateMax, low);
                } else {
                    if (pulse) disabledPulseSamples++;
                    disabledGateMax = Math.max(disabledGateMax, Math.max(high, low));
                }
            }
        }
        String json(Throwable failure, Throwable cleanupFailure) {
            return "{\"model\":" + quote(averaged ? "AVERAGED" : "DETAILED") + ",\"status\":" +
                quote(failure == null ? "PASS" : "FAIL") + ",\"parameters\":" + quote(parameters) +
                ",\"timestepSeconds\":" + dt + ",\"installed\":" + installed + ",\"analyzed\":" + analyzed +
                ",\"completed200ms\":" + completed + ",\"acceptedSteps\":" + observedAcceptedSteps +
                ",\"elapsedSeconds\":" + elapsedSeconds + ",\"trials\":" + trials + ",\"batches\":" + batches +
                ",\"sampledAcceptedSteps\":" + samples + ",\"finiteTerminalSamples\":" + terminalSamples +
                ",\"sampledMaxSubIterations\":" + sampledMaxSubIterations +
                ",\"enableAtSeconds\":0.05,\"enableChanged\":" + enableChanged +
                ",\"disabledActiveSamples\":" + disabledActiveSamples + ",\"enabledActiveSamples\":" + enabledActiveSamples +
                ",\"disabledPulseSamples\":" + (averaged ? "null" : "" + disabledPulseSamples) +
                ",\"enabledPulseSamples\":" + (averaged ? "null" : "" + enabledPulseSamples) +
                ",\"enabledDeliverySamples\":" + deliverySamples +
                ",\"disabledGateMaxVolts\":" + (averaged ? "null" : "" + disabledGateMax) +
                ",\"highGateMaxVolts\":" + (averaged ? "null" : "" + highGateMax) +
                ",\"lowGateMaxVolts\":" + (averaged ? "null" : "" + lowGateMax) +
                ",\"sampledOutputMinVolts\":" + (samples == 0 ? "null" : "" + outputMin) +
                ",\"sampledOutputMaxVolts\":" + (samples == 0 ? "null" : "" + outputMax) +
                ",\"sampledOutputMeanVolts\":" + (samples == 0 ? "null" : "" + outputSum / samples) +
                ",\"sampledLoadMeanAmps\":" + (samples == 0 ? "null" : "" + loadSum / samples) +
                ",\"finalBulkVolts\":" + finalBulk + ",\"finalFeedbackVolts\":" + finalFeedback +
                ",\"stopMessage\":" + quote(stop) + ",\"forcedAfterAcceptedSolve\":" + forcedAfterAcceptedSolve +
                ",\"privateCloseReturned\":" + privateCloseReturned + ",\"candidateDisposed\":" + disposed +
                ",\"playerOwnerRestored\":" + restored + ",\"wallMs\":" + wallMs + ",\"cleanupMs\":" + cleanupMs +
                ",\"failure\":" + quote(failure == null ? null : failure.getMessage()) +
                ",\"cleanupFailure\":" + quote(cleanupFailure == null ? null : cleanupFailure.getMessage()) + "}";
        }
    }
}