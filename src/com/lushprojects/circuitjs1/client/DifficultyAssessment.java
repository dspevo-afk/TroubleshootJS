package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable, private aggregate over a complete current diagnostic proof.
 * No selected fault or owner determines the profile. Physics/tolerances are inputs only. */
final class DifficultyAssessment {
    static final int VERSION = 1;
    final int components, hypotheses, repairClasses, physicalOwners, readings, bestSingleRemaining;
    final int minExecutedEvidenceDepth, maxExecutedEvidenceDepth, minWitnessActions, maxWitnessActions;
    final int meterModes, inputTransitions, declaredRailTargets, temporalSamples;
    final boolean parallelPaths;
    final DifficultyProfile profile;
    final String programIdentity;

    private DifficultyAssessment(GeneratedBoardInstance board, GeneratedDiagnosticProofReceipt receipt) {
        receipt.requireAssessmentOwner(board);
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
        if (evidence.isEmpty() || evidence.size() > 12) throw new IllegalArgumentException("Uncalibrated hypothesis population");
        GeneratedDiagnosticProgram program = board.getDiagnosticProvider().getObservationProgram();
        if (!receipt.getProgramIdentity().equals(program.canonical())) throw new IllegalArgumentException("Stale difficulty program");
        GeneratedDiagnosticObservationExecutor.validateAvailability(board, program);
        GeneratedDiagnosticSolvabilityEvidence first = evidence.firstElement();
        hypotheses = evidence.size(); components = board.getBoard().getComponentIds().size();
        physicalOwners = first.getAdmittedPhysicalOwnerCount();
        int low = Integer.MAX_VALUE, high = 0;
        int shortest = Integer.MAX_VALUE, longest = 0;
        int expectedWitnessActions = 5; // isolate, remove, catalog replacement, power on, customer retest
        for (GeneratedDiagnosticProgram.Step step : program.getSteps())
            if (step.kind != GeneratedDiagnosticProgram.Kind.SETTLE) expectedWitnessActions++;
        int[] repairs = new int[hypotheses];
        int distinct = 0;
        for (int a = 0; a < hypotheses; a++) {
            GeneratedDiagnosticSolvabilityEvidence proof = evidence.get(a);
            if (!proof.isRepairReachable() || !proof.isCustomerRetestPassed() || !proof.isStateIsolated() ||
                    proof.getSeed() != board.getSeed() || !proof.getFamilyId().equals(board.getCircuitFamilyId()) ||
                    proof.getAdmittedCandidateCount() != hypotheses || proof.getAdmittedPhysicalOwnerCount() != physicalOwners)
                throw new IllegalArgumentException("Incomplete or foreign difficulty evidence");
            low = Math.min(low, proof.getMeasuredExecutionDepth()); high = Math.max(high, proof.getMeasuredExecutionDepth());
            if (proof.getCompletedSemanticActions() != expectedWitnessActions)
                throw new IllegalArgumentException("Incomplete executed legal-action witness");
            shortest = Math.min(shortest, proof.getCompletedSemanticActions());
            longest = Math.max(longest, proof.getCompletedSemanticActions());
            repairs[a] = distinct;
            for (int b = 0; b < a; b++) if (proof.getRepairSemantics().isEquivalentTo(evidence.get(b).getRepairSemantics())) {
                repairs[a] = repairs[b]; break;
            }
            if (repairs[a] == distinct) distinct++;
        }
        repairClasses = distinct; minExecutedEvidenceDepth = low; maxExecutedEvidenceDepth = high;
        minWitnessActions = shortest; maxWitnessActions = longest;
        meterModes = first.getExecutedMeterModeIds().size(); inputTransitions = first.getExecutedInputPowerTransitions().size();
        declaredRailTargets = first.getDeclaredRailDomainIds().size(); temporalSamples = first.getExecutedTemporalWaitSamples().size();
        parallelPaths = first.hasDeclaredParallelPathAmbiguity(); programIdentity = program.canonical();
        Vector<int[]> measurements = new Vector<int[]>();
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            switch (step.kind) {
            case DC_VOLTAGE: case RESISTANCE: case CONTINUITY:
                measurements.add(new int[] {sampleIndex(first, step.id)}); break;
            case DIODE:
                measurements.add(new int[] {sampleIndex(first, step.id + "_VOLTAGE"), sampleIndex(first, step.id + "_CURRENT")}); break;
            default: break;
            }
        }
        boolean[][][] comparisons = new boolean[measurements.size()][hypotheses][hypotheses];
        Vector<Vector<GeneratedDiagnosticSample>> observations = new Vector<Vector<GeneratedDiagnosticSample>>();
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) observations.add(proof.getSolverSamples());
        for (int m = 0; m < measurements.size(); m++) for (int a = 0; a < hypotheses; a++) for (int b = 0; b < hypotheses; b++) {
            boolean same = true;
            for (int sample : measurements.get(m)) same &= sameSample(observations.get(a).get(sample), observations.get(b).get(sample));
            comparisons[m][a][b] = same;
        }
        DiagnosticReduction reduction = new DiagnosticReduction(comparisons, repairs);
        readings = reduction.readings; bestSingleRemaining = reduction.bestSingleRemaining;
        profile = classify(components, physicalOwners, repairClasses, readings, minExecutedEvidenceDepth,
            meterModes, inputTransitions, declaredRailTargets, temporalSamples, parallelPaths);
    }

    static DifficultyAssessment assess(GeneratedBoardInstance board, GeneratedDiagnosticProofReceipt receipt) {
        if (board == null || receipt == null || board.isDeveloperOnlyFaultRoute())
            throw new IllegalArgumentException("Difficulty requires a current normal proof");
        return new DifficultyAssessment(board, receipt);
    }
    static DifficultyProfile classify(int parts, int owners, int repairs, int readings, int depth,
            int modes, int inputs, int railTargets, int temporal, boolean parallel) {
        if (parts < 1 || parts > 20 || owners < 1 || repairs < 1 || readings < 0 || depth < 1 || modes < 1)
            throw new IllegalArgumentException("Outside the calibrated small-board envelope");
        // Initial alpha bands: a simple DC measurement/repair versus interacting
        // input, timing or parallel-path reasoning across multiple possible owners.
        // The component envelope limits scope; it never adds difficulty points.
        boolean interacting = temporal > 0 || (parallel && modes >= 2);
        return readings >= 2 && owners >= 2 && repairs >= 2 && interacting ? DifficultyProfile.MEDIUM : DifficultyProfile.EASY;
    }
    void require(DifficultyProfile requested) {
        if (requested == null || !requested.isAvailable() || requested != profile)
            throw new IllegalArgumentException("The proved board does not match the requested difficulty");
    }
    String canonical() {
        return "difficulty/" + VERSION + ";profile=" + profile + ";parts=" + components + ";hypotheses=" + hypotheses +
            ";repairs=" + repairClasses + ";owners=" + physicalOwners + ";readings=" + readings +
            ";singleRemaining=" + bestSingleRemaining + ";executedEvidenceDepth=" + minExecutedEvidenceDepth + "-" + maxExecutedEvidenceDepth +
            ";legalWitnessActions=" + minWitnessActions + "-" + maxWitnessActions +
            ";modes=" + meterModes + ";inputs=" + inputTransitions + ";railTargets=" + declaredRailTargets +
            ";temporal=" + temporalSamples + ";parallel=" + parallelPaths;
    }
    private static int sampleIndex(GeneratedDiagnosticSolvabilityEvidence proof, String id) {
        Vector<GeneratedDiagnosticSample> samples = proof.getSolverSamples();
        for (int i = 0; i < samples.size(); i++) if (id.equals(samples.get(i).getSampleId())) return i;
        throw new IllegalArgumentException("Difficulty measurement is absent from the proof");
    }
    private static boolean sameSample(GeneratedDiagnosticSample a, GeneratedDiagnosticSample b) {
        if (!a.getSampleId().equals(b.getSampleId())) throw new IllegalArgumentException("Difficulty sample schema differs");
        return a.getOutcome() == b.getOutcome() && (a.isOverRange() || Math.abs(a.getValue() - b.getValue()) <=
            Math.max(a.getComparisonTolerance(), b.getComparisonTolerance()));
    }
}
