package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable developer-only evidence for one deterministic diagnostic route. */
final class GeneratedDiagnosticSolvabilityEvidence {
    private final String routeId;
    private final String familyId;
    private final long seed;
    private final int admittedCandidateCount;
    private final int admittedPhysicalOwnerCount;
    private final int minimumPlanDepth;
    private final int worstPlanDepth;
    private final Vector<String> templatesConsidered;
    private final Vector<GeneratedDiagnosticSample> solverSamples;
    private final Vector<String> inputPowerTransitions;
    private final Vector<String> isolationActions;
    private final Vector<String> meterModes;
    private final Vector<String> temporalWaitsSamples;
    private final Vector<String> railsDomains;
    private final boolean parallelPathAmbiguity;
    private final boolean unaffectedFunctionRetestObservation;
    private final String equivalentRepairClass;
    private final String deterministicResult;
    private final String deterministicRejectionReason;
    private final boolean repairReachable;
    private final boolean customerRetestPassed;
    private final boolean stateIsolated;

    GeneratedDiagnosticSolvabilityEvidence(String routeId, String familyId, long seed,
            int admittedCandidateCount, int admittedPhysicalOwnerCount, int minimumPlanDepth,
            int worstPlanDepth, Vector<String> templatesConsidered,
            Vector<GeneratedDiagnosticSample> solverSamples,
            Vector<String> inputPowerTransitions, Vector<String> isolationActions,
            Vector<String> meterModes, Vector<String> temporalWaitsSamples,
            Vector<String> railsDomains, boolean parallelPathAmbiguity,
            boolean unaffectedFunctionRetestObservation, String equivalentRepairClass,
            String deterministicResult, String deterministicRejectionReason,
            boolean repairReachable, boolean customerRetestPassed, boolean stateIsolated) {
        this.routeId = routeId;
        this.familyId = familyId;
        this.seed = seed;
        this.admittedCandidateCount = admittedCandidateCount;
        this.admittedPhysicalOwnerCount = admittedPhysicalOwnerCount;
        this.minimumPlanDepth = minimumPlanDepth;
        this.worstPlanDepth = worstPlanDepth;
        this.templatesConsidered = copyStrings(templatesConsidered);
        this.solverSamples = copySamples(solverSamples);
        this.inputPowerTransitions = copyStrings(inputPowerTransitions);
        this.isolationActions = copyStrings(isolationActions);
        this.meterModes = copyStrings(meterModes);
        this.temporalWaitsSamples = copyStrings(temporalWaitsSamples);
        this.railsDomains = copyStrings(railsDomains);
        this.parallelPathAmbiguity = parallelPathAmbiguity;
        this.unaffectedFunctionRetestObservation = unaffectedFunctionRetestObservation;
        this.equivalentRepairClass = equivalentRepairClass;
        this.deterministicResult = deterministicResult;
        this.deterministicRejectionReason = deterministicRejectionReason;
        this.repairReachable = repairReachable;
        this.customerRetestPassed = customerRetestPassed;
        this.stateIsolated = stateIsolated;
    }

    String getRouteId() { return routeId; }
    String getFamilyId() { return familyId; }
    long getSeed() { return seed; }
    int getAdmittedCandidateCount() { return admittedCandidateCount; }
    int getAdmittedPhysicalOwnerCount() { return admittedPhysicalOwnerCount; }
    int getMinimumPlanDepth() { return minimumPlanDepth; }
    int getWorstPlanDepth() { return worstPlanDepth; }
    Vector<String> getTemplatesConsidered() { return new Vector<String>(templatesConsidered); }
    Vector<GeneratedDiagnosticSample> getSolverSamples() {
        return new Vector<GeneratedDiagnosticSample>(solverSamples);
    }
    Vector<String> getInputPowerTransitions() {
        return new Vector<String>(inputPowerTransitions);
    }
    Vector<String> getIsolationActions() { return new Vector<String>(isolationActions); }
    Vector<String> getMeterModes() { return new Vector<String>(meterModes); }
    Vector<String> getTemporalWaitsSamples() { return new Vector<String>(temporalWaitsSamples); }
    Vector<String> getRailsDomains() { return new Vector<String>(railsDomains); }
    boolean hasParallelPathAmbiguity() { return parallelPathAmbiguity; }
    boolean hasUnaffectedFunctionRetestObservation() {
        return unaffectedFunctionRetestObservation;
    }
    String getEquivalentRepairClass() { return equivalentRepairClass; }
    String getDeterministicResult() { return deterministicResult; }
    String getDeterministicRejectionReason() { return deterministicRejectionReason; }
    boolean isRepairReachable() { return repairReachable; }
    boolean isCustomerRetestPassed() { return customerRetestPassed; }
    boolean isStateIsolated() { return stateIsolated; }

    private static Vector<String> copyStrings(Vector<String> values) {
        return values == null ? new Vector<String>() : new Vector<String>(values);
    }

    private static Vector<GeneratedDiagnosticSample> copySamples(
            Vector<GeneratedDiagnosticSample> values) {
        return values == null ? new Vector<GeneratedDiagnosticSample>() :
            new Vector<GeneratedDiagnosticSample>(values);
    }
}
