package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Production comparison of solver observations and independently reachable repairs. */
final class GeneratedDiagnosticEquivalence {
    private GeneratedDiagnosticEquivalence() { }

    static Vector<GeneratedDiagnosticSolvabilityEvidence> classify(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence, String family, long seed) {
        if (evidence == null || evidence.isEmpty())
            throw new IllegalArgumentException("Production diagnostic proof is empty");
        int[] groups = new int[evidence.size()];
        Vector<String> keys = new Vector<String>();
        GeneratedDiagnosticSolvabilityEvidence reference = evidence.firstElement();
        for (int i = 0; i < groups.length; i++) {
            groups[i] = i;
            GeneratedDiagnosticSolvabilityEvidence proof = evidence.get(i);
            require(proof != null && family.equals(proof.getFamilyId()) && proof.getSeed() == seed &&
                proof.getHypothesisKey() != null && !keys.contains(proof.getHypothesisKey()) &&
                proof.getAdmittedCandidateCount() == evidence.size(),
                "Production diagnostic proof has a foreign, missing or duplicate hypothesis");
            keys.add(proof.getHypothesisKey());
            require(proof.isRepairReachable() && proof.isCustomerRetestPassed() &&
                proof.isStateIsolated() && proof.hasUnaffectedFunctionRetestObservation() &&
                "PASS".equals(proof.getDeterministicResult()) &&
                "NONE".equals(proof.getDeterministicRejectionReason()) &&
                proof.getRepairSemantics() != null && proof.getMeasuredExecutionDepth() > 0 &&
                proof.getExecutedRepairActionIds().contains(WorkbenchOperation.CATALOG_INSTALL) &&
                proof.getExecutedActionIds().contains(GeneratedBoardOperationIds.CUSTOMER_RETEST),
                "Production diagnostic proof has no executed reachable repair/retest");
            /* Validate every complete solver schema once before the pairwise
             * equivalence relation. The relation below is then value-only and
             * allocation-free, which keeps exhaustive large-program proofs
             * bounded without accepting a changed sample schema. */
            requireSchema(reference, proof);
        }
        for (int a = 0; a < groups.length; a++)
            for (int b = a + 1; b < groups.length; b++)
                if (sameValidatedObservations(evidence.get(a), evidence.get(b))) {
                    require(evidence.get(a).getRepairSemantics().isEquivalentTo(evidence.get(b).getRepairSemantics()),
                        "REPAIR_EQUIVALENCE_REJECTED: identical solver observations have different legal physical repair semantics");
                    int left = root(groups, a), right = root(groups, b);
                    if (left != right) groups[right] = left;
                }
        for (int a = 0; a < groups.length; a++)
            for (int b = a + 1; b < groups.length; b++)
                if (root(groups, a) == root(groups, b))
                    require(sameValidatedObservations(evidence.get(a), evidence.get(b)),
                        "Production diagnostic observations have non-transitive tolerance equivalence");
        Vector<GeneratedDiagnosticSolvabilityEvidence> result = new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        for (int i = 0; i < groups.length; i++) {
            boolean shared = false;
            for (int j = 0; j < groups.length; j++)
                if (i != j && root(groups, i) == root(groups, j)) shared = true;
            result.add(evidence.get(i).withEquivalentRepairClass(shared ?
                "EQUIVALENT_REPAIR_" + family + "_" + seed + "_CLASS_" + root(groups, i) : "NONE"));
        }
        return result;
    }

    static boolean sameObservations(Vector<GeneratedDiagnosticSample> first,
            Vector<GeneratedDiagnosticSample> second) {
        requireSchema(first, second);
        return sameValidatedObservations(first, second);
    }

    /** Same comparison over retained immutable evidence, without vector copies. */
    static boolean sameObservations(GeneratedDiagnosticSolvabilityEvidence first,
            GeneratedDiagnosticSolvabilityEvidence second) {
        requireSchema(first, second);
        return sameValidatedObservations(first, second);
    }

    /** Caller has already checked non-null, matching, duplicate-free schemas. */
    private static boolean sameValidatedObservations(Vector<GeneratedDiagnosticSample> first,
            Vector<GeneratedDiagnosticSample> second) {
        for (int i = 0; i < first.size(); i++) {
            GeneratedDiagnosticSample a = first.get(i), b = second.get(i);
            if (a.getOutcome() != b.getOutcome()) return false;
            if (a.isOverRange()) continue;
            if (Math.abs(a.getValue() - b.getValue()) >
                    Math.max(a.getComparisonTolerance(), b.getComparisonTolerance())) return false;
        }
        return true;
    }

    /** Caller has already checked non-null, matching, duplicate-free schemas. */
    private static boolean sameValidatedObservations(GeneratedDiagnosticSolvabilityEvidence first,
            GeneratedDiagnosticSolvabilityEvidence second) {
        for (int i = 0; i < first.getStaticProofSampleCount(); i++) {
            GeneratedDiagnosticSample a = first.getStaticProofSample(i);
            GeneratedDiagnosticSample b = second.getStaticProofSample(i);
            if (a.getOutcome() != b.getOutcome()) return false;
            if (a.isOverRange()) continue;
            if (Math.abs(a.getValue() - b.getValue()) >
                    Math.max(a.getComparisonTolerance(), b.getComparisonTolerance())) return false;
        }
        return true;
    }

    private static void requireSchema(Vector<GeneratedDiagnosticSample> first,
            Vector<GeneratedDiagnosticSample> second) {
        require(first != null && second != null && !first.isEmpty() && first.size() == second.size(),
            "Production diagnostic observation schema is empty or changed between hypotheses");
        HashSet<String> ids = new HashSet<String>();
        for (int i = 0; i < first.size(); i++) {
            GeneratedDiagnosticSample a = first.get(i), b = second.get(i);
            require(a != null && b != null && a.getSampleId().equals(b.getSampleId()) &&
                    ids.add(a.getSampleId()),
                "Production diagnostic observation schema depends on the selected hypothesis");
        }
    }
    private static void requireSchema(GeneratedDiagnosticSolvabilityEvidence first,
            GeneratedDiagnosticSolvabilityEvidence second) {
        require(first != null && second != null && first.getStaticProofSampleCount() > 0 &&
                first.getStaticProofSampleCount() == second.getStaticProofSampleCount(),
            "Production diagnostic observation schema is empty or changed between hypotheses");
        HashSet<String> ids = new HashSet<String>();
        for (int i = 0; i < first.getStaticProofSampleCount(); i++) {
            GeneratedDiagnosticSample a = first.getStaticProofSample(i);
            GeneratedDiagnosticSample b = second.getStaticProofSample(i);
            require(a != null && b != null && a.getSampleId().equals(b.getSampleId()) &&
                    ids.add(a.getSampleId()),
                "Production diagnostic observation schema depends on the selected hypothesis");
        }
    }
    private static int root(int[] groups, int i) {
        while (groups[i] != i) i = groups[i];
        return i;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
