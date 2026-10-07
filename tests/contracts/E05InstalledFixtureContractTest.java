package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native constructed-graph proof; installed UI/instrument/lifecycle claims remain compiled checks. */
public final class E05InstalledFixtureContractTest {
    private static int assertions;
    private E05InstalledFixtureContractTest() { }

    public static void main(String[] args) {
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        TestCirSim sim = new TestCirSim();
        CircuitElm.sim = sim;
        E05InstalledFixture fixture = null;
        try {
            fixture = new E05InstalledFixture();
            check(!fixture.electrical.transformer.isTrapezoidal(),
                "actual constructed isolated bridge selects backward Euler");
            GeneratedBoardInstance owner = fixture.instance;
            declaredLiveBehavior(sim, fixture);
            TroubleshootBoard board = owner.getBoard();
            PcbBoardLayout layout = owner.getPcbLayout();
            board.validate();
            layout.validateGeometry(board);
            check(board.getComponentIds().size() == 11 && board.getPadIds().size() == 24,
                "bounded real fixture packages and terminal identities");
            check(owner.isDeveloperOnlyFaultRoute() && owner.getChallengeDefinition() == null &&
                owner.getFaultCandidates().isEmpty(), "fixed bench remains developer-only");
            PcbRoutingWork.Statistics work = layout.getRoutingRecoveryStatistics();
            check(work != null && work.outcome == PcbRoutingWork.Outcome.SUCCESS,
                "actual fixed fixture routed successfully");
            check(work.expansions > 0 && work.expansions <= PcbRoutingWork.Limits.DEFAULT.expansions,
                "actual construction retains the product routing budget");
            check(!layout.getTraces().isEmpty(), "fixture publishes physical copper");
            for (String component : board.getComponentIds())
                check(layout.getSilkscreenLabel("component:" + component) != null,
                    "actual visible reference label: " + component);
            for (String pad : board.getPadIds()) {
                CircuitPostMeasurementEndpoint endpoint = fixture.endpoint(pad);
                check(owner.getSimulationElements().contains(endpoint.getElement()) &&
                    endpoint.getPostIndex() >= 0 && endpoint.getPostIndex() < endpoint.getElement().getPostCount(),
                    "physical pad binds a current owned CircuitJS post: " + pad);
            }
            for (int i = 0; i < 4; i++) {
                String pad = new String[] {"T1.P1", "T1.P2", "T1.S1", "T1.S2"}[i];
                CircuitPostMeasurementEndpoint endpoint = fixture.endpoint(pad);
                check(endpoint.getElement() == fixture.electrical.transformer &&
                    endpoint.getPostIndex() == new int[] {0, 2, 1, 3}[i],
                    "transformer winding identity survives physical construction: " + pad);
            }
            PowerInputNameplate nameplate = owner.getPhysicalSpecifications()
                .getPowerInputNameplate(E05InstalledFixture.INPUT);
            check(nameplate.getConvention() == PowerInputNameplate.Convention.AC_RMS &&
                nameplate.getNominalVoltage() == 120 && nameplate.getFrequencyHz() == 60,
                "physical input carries the explicit AC RMS convention");
            PhysicalNameplate inputMarking = owner.getPhysicalBoardRuntime().getInstalledPart("JAC")
                .getPlayerVisibleNameplate();
            check(inputMarking.hasWorkbenchDetail() && "Marking".equals(inputMarking.getWorkbenchDetailLabel()) &&
                (nameplate.getDisplayLabel() + " input").equals(inputMarking.getWorkbenchDetailValue()),
                "ordinary component inspector exposes the exact source RMS/frequency marking");
            check(sim.elmList.isEmpty(), "native fixture construction does not install a player graph");
            constructedDischarge(sim, fixture);
            System.out.println("E05_INSTALLED_FIXTURE_ROUTING " + work.toCanonical());
            System.out.println("PASS: E05 installed fixture contracts assertions=" + assertions);
        } finally {
            if (fixture != null) {
                fixture.electrical.input.binding.setConnected(false);
                for (CircuitElm element : fixture.instance.getSimulationElements()) element.delete();
            }
            CircuitElm.sim = previous;
            CirSim.theSim = previousSingleton;
        }
    }

    private static void declaredLiveBehavior(TestCirSim sim, E05InstalledFixture fixture) {
        GeneratedTemporalBehavior temporal = fixture.instance.getTemporalBehavior();
        check(temporal instanceof GeneratedLiveTemporalSimulation,
            "actual fixture declares the existing ordinary live solver contract");
        temporal.requireOwnedBy(fixture.instance);
        double liveSeconds = ((GeneratedLiveTemporalSimulation)temporal).getLiveSolverAdvanceSeconds();
        check(liveSeconds == .005 && liveSeconds > 0 && liveSeconds <= .100,
            "developer live increment retains the existing bounded 5 ms recipe");
        check(E05AcInputModel.MAX_TIME_STEP_SECONDS == .00005,
            "live pacing preserves the declared 50 us accepted timestep");
        check(temporal.getObservedBehavior() == null,
            "live bench does not invent a customer temporal classification");
        GeneratedTemporalDependency dependency = temporal.getDependency(fixture.instance);
        check(dependency.getBehaviorId().equals("E05_FIXED_BENCH_LIVE") &&
            dependency.getInitialStateContract().equals(GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1) &&
            dependency.getOutputEndpointId().equals("JOUT.1") && dependency.getGroundEndpointId().equals("JOUT.2"),
            "owned live dependency identifies its real install and output endpoints");
        String originalRecipe = dependency.canonical();
        double frequency = fixture.electrical.input.source.frequency;
        try {
            fixture.electrical.input.source.frequency = frequency + 1;
            check(!originalRecipe.equals(temporal.getDependency(fixture.instance).canonical()),
                "live dependency binds the actual source recipe");
            check(dependency.canonical().equals(originalRecipe),
                "captured live dependency retains no mutable source reference");
        } finally {
            fixture.electrical.input.source.frequency = frequency;
        }
        boolean foreignRejected = false;
        try { temporal.requireOwnedBy(null); }
        catch (IllegalArgumentException expected) { foreignRejected = true; }
        check(foreignRejected, "live behavior rejects foreign owner context");
        for (GeneratedTemporalBehavior.Profile profile : GeneratedTemporalBehavior.Profile.values()) {
            boolean unsupported = false;
            try { temporal.beginProfile(sim, fixture.instance, profile); }
            catch (UnsupportedOperationException expected) { unsupported = true; }
            check(unsupported, "fixed developer bench refuses customer profile: " + profile);
        }
    }

    private static void constructedDischarge(TestCirSim sim, E05InstalledFixture fixture) {
        Vector<CircuitElm> previousGraph = sim.elmList;
        boolean previousMeasurement = sim.a01MeasurementRunning;
        try {
            sim.a01MeasurementRunning = true;
            sim.elmList = fixture.instance.getSimulationElements();
            PhysicalBoardRuntime physical = fixture.instance.getPhysicalBoardRuntime();
            check(((PhysicalCapacitorPart)physical.getInstalledPart("C1")).getElement() == fixture.electrical.bulk &&
                ((PhysicalResistorPart)physical.getInstalledPart("RBLEED")).getElement() == fixture.electrical.bleed &&
                ((PhysicalResistorPart)physical.getInstalledPart("RLOAD")).getElement() == fixture.electrical.load,
                "native discharge observes current typed construction aliases");
            for (int phase = 0; phase < 2; phase++) {
                for (CircuitElm element : sim.elmList) element.reset();
                // Retire accepted-state events before starting this separate native timeline.
                sim.solverExecutor.retire();
                sim.t = 0; sim.timeStepAccum = 0; sim.timeStepCount = 0; sim.lastIterTime = 0;
                sim.stopMessage = null; sim.analyzeFlag = sim.dcAnalysisFlag = false;
                fixture.electrical.input.binding.setConnected(true);
                sim.solverExecutor.invalidate(); sim.solverExecutor.analyze();
                double liveSeconds = ((GeneratedLiveTemporalSimulation)fixture.instance.getTemporalBehavior())
                    .getLiveSolverAdvanceSeconds();
                double poweredStart = sim.t;
                sim.solverExecutor.advanceFor(liveSeconds);
                check(Math.abs(sim.t - poweredStart - .005) < 1e-10 && sim.stopMessage == null &&
                    sim.timeStep <= .00005 && sim.maxTimeStep <= .00005,
                    "declared powered live segment advances actual bounded accepted solver time");
                check(fixture.electrical.input.linePole.position == 0 &&
                    fixture.electrical.input.returnPole.position == 0,
                    "powered live segment leaves both real source poles connected");
                sim.solverExecutor.advanceFor(.6 + phase / 240.0 - liveSeconds);
                double charged = fixture.electrical.bulk.getVoltageDiff(), offTime = sim.t;
                check(charged > 12, "actual constructed graph charges real bulk storage");
                fixture.electrical.input.binding.setConnected(false);
                check(fixture.electrical.bulk.getVoltageDiff() == charged,
                    "constructed graph OFF isolates without clearing stored energy");
                sim.solverExecutor.analyze(); sim.solverExecutor.advanceSteps(1);
                double first = fixture.electrical.bulk.getVoltageDiff(), firstTime = sim.t;
                double firstMagnetic = magneticEnergy(fixture.electrical.transformer);
                double firstPrimary = fixture.electrical.transformer.current[0];
                double firstSecondary = fixture.electrical.transformer.current[1];
                double offLiveSeconds = ((GeneratedLiveTemporalSimulation)fixture.instance.getTemporalBehavior())
                    .getLiveSolverAdvanceSeconds();
                sim.solverExecutor.advanceFor(offLiveSeconds);
                check(offLiveSeconds == liveSeconds && Math.abs(sim.t - firstTime - .005) < 1e-10 &&
                    sim.stopMessage == null && sim.timeStep <= .00005 && sim.maxTimeStep <= .00005,
                    "declared OFF live segment advances actual bounded accepted solver time");
                check(fixture.electrical.input.linePole.position == 1 &&
                    fixture.electrical.input.returnPole.position == 1,
                    "OFF live segment preserves actual two-pole source isolation");
                sim.solverExecutor.advanceFor(.05 - offLiveSeconds);
                double initial = fixture.electrical.bulk.getVoltageDiff(), initialTime = sim.t;
                double settledMagnetic = magneticEnergy(fixture.electrical.transformer);
                double settledPrimary = fixture.electrical.transformer.current[0];
                double settledSecondary = fixture.electrical.transformer.current[1];
                sim.solverExecutor.advanceFor(.2);
                double earlyEnd = fixture.electrical.bulk.getVoltageDiff(), earlyElapsed = sim.t - firstTime;
                sim.solverExecutor.advanceFor(.05);
                double later = fixture.electrical.bulk.getVoltageDiff(), elapsed = sim.t - initialTime;
                double tau = .000470 / (1 / 10000.0 + 1 / 1000.0);
                double expected = Math.exp(-elapsed / tau);
                String evidence = "phase=" + phase + ";offTime=" + offTime + ";firstTime=" + firstTime +
                    ";firstVolts=" + first + ";firstPrimaryAmps=" + firstPrimary +
                    ";firstSecondaryAmps=" + firstSecondary + ";firstMagneticJoules=" + firstMagnetic +
                    ";rcStartTime=" + initialTime + ";rcEndTime=" + sim.t + ";rcStartVolts=" + initial +
                    ";rcEndVolts=" + later + ";elapsed=" + elapsed + ";observedRatio=" + (later / initial) +
                    ";expectedRatio=" + expected + ";settledPrimaryAmps=" + settledPrimary +
                    ";settledSecondaryAmps=" + settledSecondary + ";settledMagneticJoules=" + settledMagnetic +
                    ";earlyObservedRatio=" + (earlyEnd / first) + ";earlyExpectedRatio=" + Math.exp(-earlyElapsed / tau) +
                    ";capacitance=" + fixture.electrical.bulk.capacitance +
                    ";bleedOhms=" + fixture.electrical.bleed.resistance + ";loadOhms=" + fixture.electrical.load.resistance;
                System.out.println("E05_CONSTRUCTED_DISCHARGE " + evidence);
                check(later > 0 && later < initial && Math.abs(later / initial - expected) < .04,
                    "constructed graph settles to independent parallel-RC discharge: " + evidence);
                settledOffRails(sim, fixture, phase, offTime);
                check(sim.generatedBoardInstance == null, "native graph is not an installed player lifecycle claim");
            }
        } finally {
            sim.elmList = previousGraph; sim.a01MeasurementRunning = previousMeasurement;
            sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
        }
    }

    private static void settledOffRails(TestCirSim sim, E05InstalledFixture fixture,
            int phase, double offTime) {
        // Existing-budget windows; inspect a run of accepted steps to expose alternating voltage modes.
        while (sim.t + 1e-10 < offTime + 3.3)
            sim.solverExecutor.advanceFor(Math.min(.5, offTime + 3.3 - sim.t));
        String[] names = {"PRIMARY_WINDING", "SECONDARY_WINDING", "PRI_LINE", "PRI_FUSED", "SEC_A", "DC_PLUS"};
        double[] maxima = new double[names.length];
        TransformerElm transformer = fixture.electrical.transformer;
        for (int sample = 0; sample < 16; sample++) {
            sim.solverExecutor.advanceSteps(1);
            double primaryReturn = padVoltage(fixture, "JAC.2");
            double secondaryReturn = padVoltage(fixture, "T1.S2");
            double[] actual = {
                transformer.getPostVoltage(0) - transformer.getPostVoltage(2),
                transformer.getPostVoltage(1) - transformer.getPostVoltage(3),
                padVoltage(fixture, "JAC.1") - primaryReturn,
                padVoltage(fixture, "F1.2") - primaryReturn,
                padVoltage(fixture, "T1.S1") - secondaryReturn,
                padVoltage(fixture, "JOUT.1") - padVoltage(fixture, "JOUT.2")
            };
            for (int n = 0; n < actual.length; n++) {
                check(PowerDomainContract.finite(actual[n]),
                    "finite actual OFF voltage: phase=" + phase + ";sample=" + sample + ";rail=" + names[n]);
                maxima[n] = Math.max(maxima[n], Math.abs(actual[n]));
            }
        }
        check(fixture.electrical.input.linePole.position == 1 &&
            fixture.electrical.input.returnPole.position == 1,
            "both actual source poles remain open throughout bounded OFF continuation");
        check(sim.t - offTime >= 3.3 - 1e-10,
            "actual OFF rail window follows the bounded 3.3 s discharge");
        StringBuilder evidence = new StringBuilder("{\"phase\":" + phase +
            ",\"timeStep\":" + sim.timeStep + ",\"offSeconds\":" + (sim.t - offTime) +
            ",\"samples\":16,\"maxVolts\":{");
        for (int n = 0; n < names.length; n++) {
            if (n > 0) evidence.append(',');
            evidence.append('"').append(names[n]).append("\":").append(maxima[n]);
            check(maxima[n] < .25,
                "actual accepted OFF voltage below unchanged .25 V threshold: phase=" + phase +
                ";rail=" + names[n] + ";maxVolts=" + maxima[n]);
        }
        evidence.append("}}");
        System.out.println("E05_CONSTRUCTED_OFF_RAILS " + evidence);
    }

    private static double padVoltage(E05InstalledFixture fixture, String pad) {
        CircuitPostMeasurementEndpoint endpoint = fixture.endpoint(pad);
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }

    private static double magneticEnergy(TransformerElm transformer) {
        double primary = transformer.current[0], secondary = transformer.current[1];
        return 2 * primary * primary + .02 * secondary * secondary + .3996 * primary * secondary;
    }
    private static void check(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }

    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = E05AcInputModel.MAX_TIME_STEP_SECONDS;
            adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
