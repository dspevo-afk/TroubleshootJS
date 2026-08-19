package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable developer-only evidence with declared plan and live trace split. */
final class GeneratedDiagnosticSolvabilityEvidence {
    private final String routeId;
    private final String familyId;
    private final long seed;
    private final int admittedCandidateCount;
    private final int admittedPhysicalOwnerCount;

    private final int declaredPlanDepth;
    private final Vector<String> declaredTemplateIds;
    private final Vector<String> declaredProbeTargetIds;
    private final Vector<String> declaredInputPowerTransitions;
    private final Vector<String> declaredIsolationActionIds;
    private final Vector<String> declaredRepairActionIds;
    private final Vector<String> declaredWorkflowActionIds;
    private final Vector<String> declaredPlayerOperationIds;
    private final Vector<String> declaredMeterModeIds;
    private final Vector<String> declaredTemporalWaitSampleIds;
    private final Vector<String> declaredRailDomainIds;
    private final boolean declaredParallelPathAmbiguity;

    private final Vector<GeneratedDiagnosticSample> solverSamples;
    private final Vector<String> executedActionIds;
    private final Vector<String> executedRepairActionIds;
    private final Vector<String> executedMeterModeIds;
    private final Vector<String> executedInputPowerTransitions;
    private final Vector<String> executedIsolationActionIds;
    private final Vector<String> executedTemporalWaitSamples;
    private final int measuredExecutionDepth;
    private final GeneratedDiagnosticRepairSemantics repairSemantics;
    private final boolean unaffectedFunctionRetestObservation;
    private final String equivalentRepairClass;
    private final String deterministicResult;
    private final String deterministicRejectionReason;
    private final boolean repairReachable;
    private final boolean customerRetestPassed;
    private final boolean stateIsolated;

    GeneratedDiagnosticSolvabilityEvidence(String routeId, String familyId, long seed,
            int admittedCandidateCount, int admittedPhysicalOwnerCount,
            GeneratedDiagnosticPlan declaredPlan, Vector<GeneratedDiagnosticSample> samples,
            GeneratedDiagnosticExecutionTrace executionTrace,
            boolean unaffectedFunctionRetestObservation, String equivalentRepairClass,
            String deterministicResult, String deterministicRejectionReason,
            boolean repairReachable, boolean customerRetestPassed, boolean stateIsolated) {
        if (routeId == null || familyId == null || declaredPlan == null ||
                samples == null || executionTrace == null || equivalentRepairClass == null ||
                deterministicResult == null || deterministicRejectionReason == null)
            throw new IllegalArgumentException("Incomplete diagnostic solvability evidence");
        if (!executionTrace.hasConsistentMeasuredDepth())
            throw new IllegalArgumentException("Diagnostic execution depth is not trace-derived");
        this.routeId = routeId;
        this.familyId = familyId;
        this.seed = seed;
        this.admittedCandidateCount = admittedCandidateCount;
        this.admittedPhysicalOwnerCount = admittedPhysicalOwnerCount;
        declaredPlanDepth = declaredPlan.getDepth();
        declaredTemplateIds = singleton(declaredPlan.getTemplateId());
        declaredProbeTargetIds = declaredPlan.getProbeTargetIds();
        declaredInputPowerTransitions = declaredPlan.getInputPowerTransitions();
        declaredIsolationActionIds = declaredPlan.getIsolationActionIds();
        declaredRepairActionIds = declaredPlan.getRepairActionIds();
        declaredWorkflowActionIds = declaredPlan.getWorkflowActionIds();
        declaredPlayerOperationIds = declaredPlan.getPlayerOperationIds();
        declaredMeterModeIds = declaredPlan.getMeterModeIds();
        declaredTemporalWaitSampleIds = declaredPlan.getTemporalWaitSampleIds();
        declaredRailDomainIds = declaredPlan.getRailDomainIds();
        declaredParallelPathAmbiguity = declaredPlan.hasParallelPathAmbiguity();
        solverSamples = new Vector<GeneratedDiagnosticSample>(samples);
        executedActionIds = executionTrace.getExecutedActionIds();
        executedRepairActionIds = executionTrace.getExecutedRepairActionIds();
        executedMeterModeIds = executionTrace.getExecutedMeterModeIds();
        executedInputPowerTransitions = executionTrace.getExecutedInputPowerTransitions();
        executedIsolationActionIds = executionTrace.getExecutedIsolationActionIds();
        executedTemporalWaitSamples = executionTrace.getExecutedTemporalWaitSamples();
        measuredExecutionDepth = executionTrace.getMeasuredDiagnosticDepth();
        repairSemantics = executionTrace.getRepairSemantics();
        this.unaffectedFunctionRetestObservation = unaffectedFunctionRetestObservation;
        this.equivalentRepairClass = equivalentRepairClass;
        this.deterministicResult = deterministicResult;
        this.deterministicRejectionReason = deterministicRejectionReason;
        this.repairReachable = repairReachable;
        this.customerRetestPassed = customerRetestPassed;
        this.stateIsolated = stateIsolated;
    }

    private GeneratedDiagnosticSolvabilityEvidence(
            GeneratedDiagnosticSolvabilityEvidence source, String classId) {
        this.routeId = source.routeId;
        this.familyId = source.familyId;
        this.seed = source.seed;
        this.admittedCandidateCount = source.admittedCandidateCount;
        this.admittedPhysicalOwnerCount = source.admittedPhysicalOwnerCount;
        this.declaredPlanDepth = source.declaredPlanDepth;
        this.declaredTemplateIds = copy(source.declaredTemplateIds);
        this.declaredProbeTargetIds = copy(source.declaredProbeTargetIds);
        this.declaredInputPowerTransitions = copy(source.declaredInputPowerTransitions);
        this.declaredIsolationActionIds = copy(source.declaredIsolationActionIds);
        this.declaredRepairActionIds = copy(source.declaredRepairActionIds);
        this.declaredWorkflowActionIds = copy(source.declaredWorkflowActionIds);
        this.declaredPlayerOperationIds = copy(source.declaredPlayerOperationIds);
        this.declaredMeterModeIds = copy(source.declaredMeterModeIds);
        this.declaredTemporalWaitSampleIds = copy(source.declaredTemporalWaitSampleIds);
        this.declaredRailDomainIds = copy(source.declaredRailDomainIds);
        this.declaredParallelPathAmbiguity = source.declaredParallelPathAmbiguity;
        this.solverSamples = new Vector<GeneratedDiagnosticSample>(source.solverSamples);
        this.executedActionIds = copy(source.executedActionIds);
        this.executedRepairActionIds = copy(source.executedRepairActionIds);
        this.executedMeterModeIds = copy(source.executedMeterModeIds);
        this.executedInputPowerTransitions = copy(source.executedInputPowerTransitions);
        this.executedIsolationActionIds = copy(source.executedIsolationActionIds);
        this.executedTemporalWaitSamples = copy(source.executedTemporalWaitSamples);
        this.measuredExecutionDepth = source.measuredExecutionDepth;
        this.repairSemantics = source.repairSemantics;
        this.unaffectedFunctionRetestObservation = source.unaffectedFunctionRetestObservation;
        this.equivalentRepairClass = classId;
        this.deterministicResult = source.deterministicResult;
        this.deterministicRejectionReason = source.deterministicRejectionReason;
        this.repairReachable = source.repairReachable;
        this.customerRetestPassed = source.customerRetestPassed;
        this.stateIsolated = source.stateIsolated;
    }

    GeneratedDiagnosticSolvabilityEvidence withEquivalentRepairClass(String classId) {
        return new GeneratedDiagnosticSolvabilityEvidence(this, classId);
    }

    String getRouteId() { return routeId; }
    String getFamilyId() { return familyId; }
    long getSeed() { return seed; }
    int getAdmittedCandidateCount() { return admittedCandidateCount; }
    int getAdmittedPhysicalOwnerCount() { return admittedPhysicalOwnerCount; }
    int getDeclaredPlanDepth() { return declaredPlanDepth; }
    int getMeasuredExecutionDepth() { return measuredExecutionDepth; }
    Vector<String> getDeclaredTemplateIds() { return copy(declaredTemplateIds); }
    Vector<String> getDeclaredProbeTargetIds() { return copy(declaredProbeTargetIds); }
    Vector<String> getDeclaredInputPowerTransitions() {
        return copy(declaredInputPowerTransitions);
    }
    Vector<String> getDeclaredIsolationActionIds() {
        return copy(declaredIsolationActionIds);
    }
    Vector<String> getDeclaredRepairActionIds() { return copy(declaredRepairActionIds); }
    Vector<String> getDeclaredWorkflowActionIds() { return copy(declaredWorkflowActionIds); }
    Vector<String> getDeclaredPlayerOperationIds() { return copy(declaredPlayerOperationIds); }
    Vector<String> getDeclaredMeterModeIds() { return copy(declaredMeterModeIds); }
    Vector<String> getDeclaredTemporalWaitSampleIds() {
        return copy(declaredTemporalWaitSampleIds);
    }
    Vector<String> getDeclaredRailDomainIds() { return copy(declaredRailDomainIds); }
    Vector<GeneratedDiagnosticSample> getSolverSamples() {
        return new Vector<GeneratedDiagnosticSample>(solverSamples);
    }
    Vector<String> getExecutedActionIds() { return copy(executedActionIds); }
    Vector<String> getExecutedRepairActionIds() { return copy(executedRepairActionIds); }
    Vector<String> getExecutedMeterModeIds() { return copy(executedMeterModeIds); }
    Vector<String> getExecutedInputPowerTransitions() {
        return copy(executedInputPowerTransitions);
    }
    Vector<String> getExecutedIsolationActionIds() {
        return copy(executedIsolationActionIds);
    }
    Vector<String> getExecutedTemporalWaitSamples() {
        return copy(executedTemporalWaitSamples);
    }
    GeneratedDiagnosticRepairSemantics getRepairSemantics() { return repairSemantics; }
    boolean hasDeclaredParallelPathAmbiguity() { return declaredParallelPathAmbiguity; }
    boolean hasUnaffectedFunctionRetestObservation() {
        return unaffectedFunctionRetestObservation;
    }
    String getEquivalentRepairClass() { return equivalentRepairClass; }
    String getDeterministicResult() { return deterministicResult; }
    String getDeterministicRejectionReason() { return deterministicRejectionReason; }
    boolean isRepairReachable() { return repairReachable; }
    boolean isCustomerRetestPassed() { return customerRetestPassed; }
    boolean isStateIsolated() { return stateIsolated; }

    private static Vector<String> singleton(String value) {
        Vector<String> result = new Vector<String>();
        result.add(value);
        return result;
    }

    private static Vector<String> copy(Vector<String> values) {
        return new Vector<String>(values);
    }
}
