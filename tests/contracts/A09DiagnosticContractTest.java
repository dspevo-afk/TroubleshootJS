package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Pure/native production contracts. Synthetic comparator inputs are NOT live solver evidence. */
public final class A09DiagnosticContractTest {
    private static int assertions, replays;
    private interface Case { void run(); }

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        providers();
        programs();
        equivalence();
        resistanceObservations();
        System.out.println("PASS: A09 diagnostic contracts assertions=" + assertions + " replays=" + replays);
    }

    private static void providers() {
        String[] families = { "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR",
            "PARALLEL_DUAL_INDICATOR", "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH" };
        for (String family : families)
            for (long seed : new long[] { 0, 3 }) {
                GeneratedBoardInstance board = QuickPlayFamilyRegistry.generate(family, seed);
                try { replayEveryHypothesis(board); }
                finally { dispose(board); }
            }
        Vector<String> variants = new Vector<String>();
        for (long seed : new long[] { 0, 1, 3 }) {
            GeneratedBoardInstance board = BoundedGeneratedBoardAssembler.assemble(
                BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
            try {
                ControlledIndicatorDeviceBehavior behavior = (ControlledIndicatorDeviceBehavior)board.getBehaviorContract();
                for (ControlledIndicatorChannel channel : behavior.getPlan().getChannels()) {
                    String variant = behavior.getPlan().getBlocks().get(channel.getDriverKey()).getDescriptor().getTypeId();
                    if (!variants.contains(variant)) variants.add(variant);
                }
                replayEveryHypothesis(board);
            } finally { dispose(board); }
        }
        require(variants.size() >= 2, "production composed corpus must exercise two driver implementations");
    }

    private static void replayEveryHypothesis(GeneratedBoardInstance board) {
        GeneratedDiagnosticProvider provider = board.getDiagnosticProvider();
        GeneratedDiagnosticProgram program = provider.getObservationProgram();
        require(!program.getSteps().isEmpty(), "nonempty production observation program");
        require(program.canonical().equals(provider.getObservationProgram().canonical()), "deterministic program");
        GeneratedDiagnosticObservationExecutor.validateAvailability(board, program);
        Vector<GeneratedFaultCandidate> hypotheses = GeneratedDiagnosticProofService.hypothesesFor(board);
        require(!hypotheses.isEmpty(), "nonempty production hypotheses");
        String previous = "";
        for (GeneratedFaultCandidate hypothesis : hypotheses) {
            require(previous.compareTo(hypothesis.getHypothesisKey()) < 0, "canonical hypothesis ordering");
            previous = hypothesis.getHypothesisKey();
            GeneratedBoardInstance replay = provider.generateHypothesis(hypothesis);
            try {
                FreshGeneratedRuntimeInstallation.requireDisjoint(board, replay);
                require(replay.getFaultBinding().getFault().getHypothesisKey().equals(hypothesis.getHypothesisKey()),
                    "exact replayed hypothesis including mechanism and physical owner");
                require(replay.getPcbLayout().geometryFingerprint().equals(board.getPcbLayout().geometryFingerprint()),
                    "hypothesis replay preserves topology/layout realization");
                require(program.canonical().equals(replay.getDiagnosticProvider().getObservationProgram().canonical()),
                    "selected answer cannot change observation sequence");
                GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(replay.getFaultCandidates(),
                    board.getDiagnosticSolvabilityContract().getHypothesisKeys());
                require(replay.getDiagnosticProvider().getCorrectCatalogId(replay,
                    hypothesis.getFault().getTargetComponentId()) != null, "declared physical replacement recipe");
                replays++;
            } finally { dispose(replay); }
        }
        program.getSteps().clear();
        require(!program.getSteps().isEmpty(), "observation data is defensively immutable");
    }

    private static void programs() {
        final GeneratedDiagnosticPlan plan = new LedIndicatorDiagnosticProvider(0).getDiagnosticPlan();
        reject("empty program", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).build();
        }});
        reject("hidden sample", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                "ANSWER_R1", "R1.1", "R1.2").build();
        }});
        reject("undeclared instrument", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).measure(GeneratedDiagnosticProgram.Kind.DIODE,
                "DIODE_CHECK", "R1.1", "R1.2").build();
        }});
        reject("missing probe", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                "READING", "R404.1", "R1.2").build();
        }});
        reject("duplicate observations", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                "READING", "R1.1", "R1.2").measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                "READING", "R1.1", "R1.2").build();
        }});
        reject("unbounded wait", new Case() { public void run() {
            GeneratedDiagnosticProgram.builder(plan).waitSample("STEADY_STATE_SAMPLE", Double.POSITIVE_INFINITY)
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "READING", "R1.1", "R1.2").build();
        }});
        reject("step budget", new Case() { public void run() {
            GeneratedDiagnosticProgram.Builder builder = GeneratedDiagnosticProgram.builder(plan);
            for (int i = 0; i <= GeneratedDiagnosticProgram.MAX_STEPS; i++) builder.settle();
        }});
        final GeneratedBoardInstance led = new LedIndicatorGenerator().generate(0);
        try {
            final GeneratedDiagnosticPlan unavailable = inputPlan();
            final GeneratedDiagnosticProgram program = GeneratedDiagnosticProgram.builder(unavailable)
                .input(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH)
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "READING", "R1.1", "R1.2").build();
            reject("unavailable actual input", new Case() { public void run() {
                GeneratedDiagnosticObservationExecutor.validateAvailability(led, program);
            }});
            final GeneratedDiagnosticPlan custom = inputPlan("PUBLIC_HEATER_ENABLE");
            final GeneratedDiagnosticProgram customProgram = GeneratedDiagnosticProgram.builder(custom)
                .input("PUBLIC_HEATER_ENABLE")
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "READING", "R1.1", "R1.2").build();
            require(!customProgram.getSteps().isEmpty(), "provider input labels need no central family switch");
            reject("unavailable provider-defined input", new Case() { public void run() {
                GeneratedDiagnosticObservationExecutor.validateAvailability(led, customProgram);
            }});
            reject("changed declared program", new Case() { public void run() {
                program.validatePlan(plan);
            }});
        } finally { dispose(led); }
        reject("non-finite observation", new Case() { public void run() {
            new GeneratedDiagnosticSample("READING", Double.NaN, .02);
        }});
        reject("invalid tolerance", new Case() { public void run() {
            new GeneratedDiagnosticSample("READING", 1, -1);
        }});
    }

    private static GeneratedDiagnosticPlan inputPlan() {
        return inputPlan(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH);
    }

    private static GeneratedDiagnosticPlan inputPlan(String input) {
        return new GeneratedDiagnosticPlan("DECLARED_INPUT_CHECK", "R1.2",
            new String[] { "R1.1", "R1.2" }, new String[] { "DC_VOLTAGE" },
            new String[] { input },
            new String[] { WorkbenchOperation.REMOVE }, new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { input, GeneratedBoardOperationIds.CUSTOMER_RETEST },
            new String[] { "PUBLIC_INPUT_SAMPLE" }, new String[] { "VIN", "GND" }, 2, false, true, "NONE");
    }

    private static void equivalence() {
        Vector<GeneratedDiagnosticSolvabilityEvidence> sameOwner = pair(
            proof("OPEN", "R1", 1, .02, 2, true, true, "READING"),
            proof("INCORRECT", "R1", 1, .02, 2, true, true, "READING"));
        Vector<GeneratedDiagnosticSolvabilityEvidence> accepted = GeneratedDiagnosticEquivalence.classify(sameOwner, "FIXTURE", 0);
        require(accepted.size() == 2 && !accepted.get(0).getHypothesisKey().equals(accepted.get(1).getHypothesisKey()),
            "equivalent repair cannot collapse same-owner different mechanisms");
        require(!"NONE".equals(accepted.get(0).getEquivalentRepairClass()) &&
                accepted.get(0).getEquivalentRepairClass().equals(accepted.get(1).getEquivalentRepairClass()),
            "identical observations use an explicit reachable equivalent repair class");
        reject("empty proof", new Case() { public void run() {
            GeneratedDiagnosticEquivalence.classify(new Vector<GeneratedDiagnosticSolvabilityEvidence>(), "FIXTURE", 0);
        }});
        rejectPair("different repair same observation", proof("A", "R1", 1, .02, 2, true, true, "READING"),
            proof("B", "R2", 1, .02, 2, true, true, "READING"));
        rejectPair("missing repair", proof("A", "R1", 1, .02, 2, false, true, "READING"),
            proof("B", "R1", 2, .02, 2, true, true, "READING"));
        rejectPair("unexecuted repair", proof("A", "R1", 1, .02, 2, true, false, "READING"),
            proof("B", "R1", 2, .02, 2, true, true, "READING"));
        rejectPair("answer-dependent sample schema", proof("A", "R1", 1, .02, 2, true, true, "READING_A"),
            proof("B", "R1", 2, .02, 2, true, true, "READING_B"));
        rejectPair("duplicate hypothesis", proof("A", "R1", 1, .02, 2, true, true, "READING"),
            proof("A", "R1", 2, .02, 2, true, true, "READING"));
        rejectPair("incomplete proof population", proof("A", "R1", 1, .02, 3, true, true, "READING"),
            proof("B", "R1", 2, .02, 3, true, true, "READING"));
        final Vector<GeneratedDiagnosticSolvabilityEvidence> chained = pair(
            proof("A", "R1", 0, 1, 3, true, true, "READING"),
            proof("B", "R1", .75, 1, 3, true, true, "READING"));
        chained.add(proof("C", "R1", 1.5, 1, 3, true, true, "READING"));
        reject("nontransitive tolerance", new Case() { public void run() {
            GeneratedDiagnosticEquivalence.classify(chained, "FIXTURE", 0);
        }});
        final Vector<GeneratedDiagnosticSolvabilityEvidence> wrongSeed = sameOwner;
        reject("foreign seed proof", new Case() { public void run() {
            GeneratedDiagnosticEquivalence.classify(wrongSeed, "FIXTURE", 1);
        }});
    }

    private static void resistanceObservations() {
        final GeneratedDiagnosticSample twenty = resistance(20000000);
        final GeneratedDiagnosticSample forty = resistance(40000000);
        final GeneratedDiagnosticSample open = resistance(Double.POSITIVE_INFINITY);
        require(twenty.isOverRange() && forty.isOverRange() && open.isOverRange(),
            "finite over-range and open readings share the OL outcome");
        require(!resistance(10000000).isOverRange() && resistance(10000001).isOverRange(),
            "the player's 10 Mohm range boundary is inclusive");
        require(resistance(0).getValue() == 0 && resistance(20000).getValue() == 20000,
            "valid in-range numeric measurements are preserved");
        rejectPair("two OL readings cannot distinguish different repairs",
            proofWithSample("A", "R1", twenty, 2, true, true),
            proofWithSample("B", "R2", forty, 2, true, true));
        rejectPair("finite OL and open cannot distinguish different repairs",
            proofWithSample("A", "R1", twenty, 2, true, true),
            proofWithSample("B", "R2", open, 2, true, true));
        Vector<GeneratedDiagnosticSolvabilityEvidence> sameOwner = GeneratedDiagnosticEquivalence.classify(pair(
            proofWithSample("A", "R1", twenty, 2, true, true),
            proofWithSample("B", "R1", open, 2, true, true)), "FIXTURE", 0);
        require(sameOwner.size() == 2 && !"NONE".equals(sameOwner.get(0).getEquivalentRepairClass()) &&
            sameOwner.get(0).getEquivalentRepairClass().equals(sameOwner.get(1).getEquivalentRepairClass()),
            "equivalent OL repairs retain both mechanism hypotheses");
        for (double reading : new double[] {0, 20000, 10000000})
            require(GeneratedDiagnosticEquivalence.classify(pair(
                proofWithSample("A", "R1", resistance(reading), 2, true, true),
                proofWithSample("B", "R2", open, 2, true, true)), "FIXTURE", 0).size() == 2,
                "numeric and OL outcomes remain distinguishable");
        require(GeneratedDiagnosticEquivalence.classify(pair(
            proofWithSample("A", "R1", resistance(20000), 2, true, true),
            proofWithSample("B", "R2", resistance(40000), 2, true, true)), "FIXTURE", 0).size() == 2,
            "different in-range values remain distinguishable");
        rejectPair("numeric tolerance remains enforced",
            proofWithSample("A", "R1", resistance(20000), 2, true, true),
            proofWithSample("B", "R2", resistance(20100), 2, true, true));
        for (final double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, -1})
            reject("invalid or unavailable resistance is not OL", new Case() { public void run() {
                resistance(invalid);
            }});
        reject("OL has no numeric value", new Case() { public void run() { twenty.getValue(); }});
        reject("OL has no numeric tolerance", new Case() { public void run() { twenty.getComparisonTolerance(); }});
        reject("OL requires a valid sample ID", new Case() { public void run() { GeneratedDiagnosticSample.overRange(""); }});
    }
    private static GeneratedDiagnosticSample resistance(double reading) {
        return GeneratedDiagnosticObservationExecutor.resistanceObservation("READING", reading);
    }

    private static GeneratedDiagnosticSolvabilityEvidence proof(String key, String owner,
            double value, double tolerance, int count, boolean repaired, boolean executed, String sampleId) {
        return proofWithSample(key, owner, new GeneratedDiagnosticSample(sampleId, value, tolerance),
            count, repaired, executed);
    }
    private static GeneratedDiagnosticSolvabilityEvidence proofWithSample(String key, String owner,
            GeneratedDiagnosticSample sample, int count, boolean repaired, boolean executed) {
        GeneratedFaultServiceability serviceability = new GeneratedFaultServiceability(
            GeneratedFaultLocus.componentInternal(owner),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE }, new String[] { WorkbenchOperation.CATALOG_INSTALL },
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        GeneratedDiagnosticExecutionTrace.Builder trace = GeneratedDiagnosticExecutionTrace.builder();
        trace.recordMeterMode("DC_VOLTAGE");
        if (executed) {
            trace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
            trace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
        }
        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        samples.add(sample);
        return new GeneratedDiagnosticSolvabilityEvidence("FIXTURE/" + key, "FIXTURE", 0, key, count, 1,
            new LedIndicatorDiagnosticProvider(0).getDiagnosticPlan(), samples,
            trace.freeze(GeneratedDiagnosticRepairSemantics.forServiceability(serviceability)),
            true, "NONE", "PASS", "NONE", repaired, true, true);
    }
    private static Vector<GeneratedDiagnosticSolvabilityEvidence> pair(
            GeneratedDiagnosticSolvabilityEvidence a, GeneratedDiagnosticSolvabilityEvidence b) {
        Vector<GeneratedDiagnosticSolvabilityEvidence> values = new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        values.add(a); values.add(b); return values;
    }
    private static void rejectPair(String label, GeneratedDiagnosticSolvabilityEvidence a,
            GeneratedDiagnosticSolvabilityEvidence b) {
        final Vector<GeneratedDiagnosticSolvabilityEvidence> values = pair(a, b);
        reject(label, new Case() { public void run() { GeneratedDiagnosticEquivalence.classify(values, "FIXTURE", 0); }});
    }
    private static void reject(String label, Case action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Accepted invalid " + label);
    }
    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static void dispose(GeneratedBoardInstance board) {
        board.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : board.getSimulationElements()) element.delete();
    }
}
