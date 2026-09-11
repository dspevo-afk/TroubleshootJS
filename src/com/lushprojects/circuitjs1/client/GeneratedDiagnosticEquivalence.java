package com.lushprojects.circuitjs1.client;

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
            requireSchema(evidence.firstElement().getSolverSamples(), proof.getSolverSamples());
        }
        for (int a = 0; a < groups.length; a++)
            for (int b = a + 1; b < groups.length; b++)
                if (sameObservations(evidence.get(a).getSolverSamples(), evidence.get(b).getSolverSamples())) {
                    require(evidence.get(a).getRepairSemantics().isEquivalentTo(evidence.get(b).getRepairSemantics()),
                        "REPAIR_EQUIVALENCE_REJECTED: identical solver observations have different legal physical repair semantics");
                    int left = root(groups, a), right = root(groups, b);
                    if (left != right) groups[right] = left;
                }
        for (int a = 0; a < groups.length; a++)
            for (int b = a + 1; b < groups.length; b++)
                if (root(groups, a) == root(groups, b))
                    require(sameObservations(evidence.get(a).getSolverSamples(), evidence.get(b).getSolverSamples()),
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
        for (int i = 0; i < first.size(); i++) {
            GeneratedDiagnosticSample a = first.get(i), b = second.get(i);
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
        Vector<String> ids = new Vector<String>();
        for (int i = 0; i < first.size(); i++) {
            GeneratedDiagnosticSample a = first.get(i), b = second.get(i);
            require(a != null && b != null && a.getSampleId().equals(b.getSampleId()) &&
                    !ids.contains(a.getSampleId()),
                "Production diagnostic observation schema depends on the selected hypothesis");
            ids.add(a.getSampleId());
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
