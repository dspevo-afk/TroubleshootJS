package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.core.client.GWT;

/** Run first, through the maintained explicit developer route, before any reference fix. */
final class E05ReferenceIsolationPilot {
    private static int assertions;
    private E05ReferenceIsolationPilot() { }

    static String verify(CirSim sim, boolean forced) {
        if (sim == null || !sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("E05 requires an explicit developer route");
        if (forced) throw new AssertionError("e05-explicit-failure-canary");
        return verifyAll(sim, false);
    }

    /** Empty JVM contract graph only; this does not claim player-owner lifecycle evidence. */
    static String verifyNative(CirSim sim) {
        requireNativeGraph(sim);
        return verifyAll(sim, true);
    }

    static void requireNativeGraph(CirSim sim) {
        if (GWT.isScript() || sim == null || CircuitElm.sim != sim || sim.elmList == null ||
                !sim.elmList.isEmpty() || sim.generatedBoardInstance != null ||
                sim.generatedChallengeController != null || sim.boardModificationController != null ||
                sim.pcbWorkbenchController != null || sim.instrumentController != null ||
                sim.activeMeasurementOverlay || sim.solverExecutor.isUnavailable())
            throw new IllegalStateException("Native E05 pilot requires an empty unowned JVM graph");
    }

    private static String verifyAll(CirSim sim, boolean nativeGraph) {
        GeneratedBoardInstance player = sim.getGeneratedBoardInstance();
        assertions = 0;
        StringBuilder rows = new StringBuilder();
        double[] ratios = new double[2];
        for (int pass = 0; pass < 2; pass++) {
            double dt = pass == 0 ? .00005 : .000025;
            ratios[pass] = run(sim, dt, 0, 0, false, rows, nativeGraph);
            near(run(sim, dt, 1, 0, false, rows, nativeGraph), ratios[pass], .00002,
                "secondary numerical reference preserves differential ratio");
            near(run(sim, dt, 2, 0, false, rows, nativeGraph), ratios[pass], .00002,
                "automatic numerical reference preserves differential ratio");
            near(run(sim, dt, 0, 0, true, rows, nativeGraph), ratios[pass], .00002,
                "element order preserves differential ratio");
            run(sim, dt, 0, 1, false, rows, nativeGraph);
            run(sim, dt, 0, 2, false, rows, nativeGraph);
        }
        near(ratios[0], ratios[1], .00002, "transformer timestep sensitivity");
        if (!nativeGraph) require(sim.getGeneratedBoardInstance() == player, "exact player owner restored");
        else require(sim.elmList.isEmpty(), "native raw graph elements retired");
        return "{\"schema\":\"e05-reference-pilot-v1\",\"status\":\"PASS\",\"assertions\":" +
            assertions + ",\"execution\":\"" + (nativeGraph ? "NATIVE_RAW_GRAPH" : "PRIVATE_PLAYER_PROOF") + "\",\"playerOwnerRestored\":" + (nativeGraph ? "null" : "true") + ",\"rows\":[" + rows + "],\"limits\":[\"solver-only reference pilot\"," +
            "\"linear coupled inductance; no saturation, thermal, parasitic capacitance or certification\"," +
            "\"rectifier/storage/player instrument qualification remains separate\"]}";
    }

    private static double run(CirSim sim, double dt, int reference, int bonds,
            boolean reverseOrder, StringBuilder rows, boolean nativeGraph) {
        Vector<CircuitElm> originalGraph = nativeGraph ? sim.elmList : null;
        PrivateSolverContext proof = nativeGraph ? null : PrivateSolverContext.open(sim);
        E05ElectricalFixtures.Fixture f = null;
        boolean previousMeasurement = sim.a01MeasurementRunning;
        long started = System.currentTimeMillis();
        try {
            E05AcInputModel.requireTimeStep(dt);
            if (nativeGraph) {
                sim.t = 0; sim.timeStepAccum = 0; sim.timeStepCount = 0;
                sim.lastIterTime = 0; sim.stopMessage = null;
                sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
            }
            sim.timeStep = sim.minTimeStep = sim.maxTimeStep = dt;
            sim.adjustTimeStep = false; sim.a01MeasurementRunning = true;
            long acceptedAtStart = sim.a01AcceptedStepCount;
            f =
                E05ElectricalFixtures.transformerReference(reference, bonds, reverseOrder);
            if (nativeGraph) {
                sim.elmList = f.elements; sim.solverExecutor.analyze();
                sim.solverExecutor.advanceFor(.2);
            } else {
                proof.install(f.elements); proof.analyze(); proof.advanceFor(.2);
            }
            require(f.transformer.getNode(2) != f.transformer.getNode(3),
                "primary and secondary returns remain distinct solver nodes");
            require(!f.transformer.getConnection(0, 1) &&
                !f.transformer.getConnection(2, 3), "transformer has no galvanic cross-winding connection");

            int count = (int)Math.round(.1 / dt);
            double sourceSquare = 0, primarySquare = 0, secondarySquare = 0;
            double bondSquare = 0, sourcePeak = 0;
            double primaryEnergy = 0, secondaryEnergy = 0;
            double initialEnergy = magneticEnergy(f.transformer);
            double previousPrimary = windingVoltage(f.transformer, 0, 2);
            double previousSecondary = windingVoltage(f.transformer, 1, 3);
            double previousPrimaryCurrent = f.transformer.current[0];
            double previousSecondaryCurrent = f.transformer.current[1];
            double previousSource = f.input.source.getVoltageDiff();
            double previousTime = sim.t, firstCrossing = 0, lastCrossing = 0;
            int crossings = 0;
            for (int step = 0; step < count; step++) {
                if (nativeGraph) sim.solverExecutor.advanceSteps(1); else proof.advanceSteps(1);
                double source = f.input.source.getVoltageDiff();
                double primary = windingVoltage(f.transformer, 0, 2);
                double secondary = windingVoltage(f.transformer, 1, 3);
                require(PowerDomainContract.finite(source) && PowerDomainContract.finite(primary) &&
                    PowerDomainContract.finite(secondary), "finite accepted reference observation");
                double elapsed = sim.t - previousTime;
                primaryEnergy += .25 * (primary + previousPrimary) *
                    (f.transformer.current[0] + previousPrimaryCurrent) * elapsed;
                secondaryEnergy += .25 * (secondary + previousSecondary) *
                    (f.transformer.current[1] + previousSecondaryCurrent) * elapsed;
                sourceSquare += source * source;
                primarySquare += primary * primary; secondarySquare += secondary * secondary;
                sourcePeak = Math.max(sourcePeak, Math.abs(source));
                if (f.bondA != null) bondSquare += f.bondA.getCurrent() * f.bondA.getCurrent();
                if (previousSource < 0 && source >= 0) {
                    double crossing = previousTime - previousSource * elapsed / (source - previousSource);
                    if (crossings == 0) firstCrossing = crossing;
                    lastCrossing = crossing; crossings++;
                }
                previousPrimary = primary; previousSecondary = secondary;
                previousPrimaryCurrent = f.transformer.current[0];
                previousSecondaryCurrent = f.transformer.current[1];
                previousSource = source; previousTime = sim.t;
            }
            double sourceRms = Math.sqrt(sourceSquare / count);
            double primaryRms = Math.sqrt(primarySquare / count);
            double secondaryRms = Math.sqrt(secondarySquare / count);
            double ratio = secondaryRms / primaryRms;
            double bondRms = Math.sqrt(bondSquare / count);
            require(crossings >= 4, "enough actual sine crossings");
            double frequency = (crossings - 1) / (lastCrossing - firstCrossing);
            near(sourceRms, 120.0, .02, "independent source RMS convention");
            near(sourcePeak, 120.0 * Math.sqrt(2.0), .05, "independent source peak convention");
            near(frequency, 60.0, .05, "independent source frequency");
            near(ratio, expectedRatio(), .001, "independent loaded linear transformer ratio");
            double energyChange = magneticEnergy(f.transformer) - initialEnergy;
            near(primaryEnergy + secondaryEnergy, energyChange, .00001,
                "coupled magnetic-energy identity");
            if (bonds == 1) require(bondRms < 1e-9,
                "one actual bond has no closed common-mode return");
            if (bonds == 2) require(bondRms > .00004 && bondRms < .00008,
                "two actual bonds create a causal common-mode current");

            if (rows.length() > 0) rows.append(',');
            rows.append("{\"timeStep\":").append(dt)
                .append(",\"numericalReference\":").append(reference)
                .append(",\"reverseElementOrder\":").append(reverseOrder)
                .append(",\"actualCrossDomainBonds\":").append(bonds)
                .append(",\"sourceRmsVolts\":").append(sourceRms)
                .append(",\"sourcePeakVolts\":").append(sourcePeak)
                .append(",\"sourceFrequencyHz\":").append(frequency)
                .append(",\"primaryRmsVolts\":").append(primaryRms)
                .append(",\"secondaryRmsVolts\":").append(secondaryRms)
                .append(",\"secondaryOverPrimaryRatio\":").append(ratio)
                .append(",\"bondRmsAmps\":").append(bondRms)
                .append(",\"primaryEnergyJoules\":").append(primaryEnergy)
                .append(",\"secondaryEnergyJoules\":").append(secondaryEnergy)
                .append(",\"magneticEnergyChangeJoules\":").append(energyChange)
                .append(",\"numericalAnchors\":").append(sim.unconnectedNodes.size())
                .append(",\"matrixFull\":").append(sim.circuitMatrixFullSize)
                .append(",\"matrixReduced\":").append(sim.circuitMatrixSize)
                .append(",\"acceptedSteps\":").append(sim.a01AcceptedStepCount - acceptedAtStart)
                .append(",\"simulatedSeconds\":").append(sim.t)
                .append(",\"wallMs\":").append(System.currentTimeMillis() - started).append('}');
            return ratio;
        } finally {
            sim.a01MeasurementRunning = previousMeasurement;
            if (proof != null) proof.close();
            else {
                sim.elmList = originalGraph; sim.solverExecutor.invalidate();
                sim.circuitMatrix = null;
                if (f != null) for (CircuitElm element : f.elements) element.delete();
            }
        }
    }

    private static double expectedRatio() {
        // Harmonic coupled-inductor equations with an independently specified resistive load.
        double lp = 4.0, ls = .04, mutual = .3996, load = 1000.0;
        double leakage = ls - mutual * mutual / lp;
        double omega = 2 * Math.PI * 60;
        return (mutual / lp) / Math.sqrt(1 + omega * omega * leakage * leakage / (load * load));
    }

    private static double magneticEnergy(TransformerElm transformer) {
        double i1 = transformer.current[0], i2 = transformer.current[1];
        // Independent declared Lp=4 H, Ls=.04 H, M=.3996 H; PSD coupled magnetic storage.
        return 2.0 * i1 * i1 + .02 * i2 * i2 + .3996 * i1 * i2;
    }

    private static double windingVoltage(TransformerElm transformer, int a, int b) {
        return transformer.getPostVoltage(a) - transformer.getPostVoltage(b);
    }

    private static void require(boolean passed, String label) {
        assertions++; if (!passed) throw new AssertionError("E05 reference pilot: " + label);
    }

    private static void near(double actual, double expected, double tolerance, String label) {
        require(PowerDomainContract.finite(actual) && Math.abs(actual - expected) <= tolerance,
            label + ": " + actual + " expected " + expected);
    }
}
