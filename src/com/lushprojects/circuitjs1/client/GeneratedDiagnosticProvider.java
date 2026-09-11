package com.lushprojects.circuitjs1.client;

/** Production contribution for one supported device/block variant. */
interface GeneratedDiagnosticProvider {
    String getProviderId();
    GeneratedDiagnosticPlan getDiagnosticPlan();
    /** Data only, built without a selected hypothesis or live solver context. */
    GeneratedDiagnosticProgram getObservationProgram();
    /** Construct disjoint owners with the same immutable recipe and one exact hypothesis. */
    GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis);
    /** Resolve a legal replacement from the physical specification of this owner. */
    String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId);
}
