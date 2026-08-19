package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Stable physical repair semantics used only for developer evidence. */
final class GeneratedDiagnosticRepairSemantics {
    private final String ownerId;
    private final String locusTypeId;
    private final String terminalId;
    private final String pathId;
    private final Vector<String> faultClearingActionIds;
    private final Vector<String> workflowActionIds;

    private GeneratedDiagnosticRepairSemantics(String ownerId, String locusTypeId,
            String terminalId, String pathId, Vector<String> faultClearingActionIds,
            Vector<String> workflowActionIds) {
        this.ownerId = ownerId;
        this.locusTypeId = locusTypeId;
        this.terminalId = terminalId;
        this.pathId = pathId;
        this.faultClearingActionIds = canonicalCopy(faultClearingActionIds);
        this.workflowActionIds = canonicalCopy(workflowActionIds);
    }

    static GeneratedDiagnosticRepairSemantics forServiceability(
            GeneratedFaultServiceability serviceability) {
        if (serviceability == null || serviceability.getLocus() == null)
            throw new IllegalArgumentException("Missing repair semantics serviceability");
        GeneratedFaultLocus locus = serviceability.getLocus();
        return new GeneratedDiagnosticRepairSemantics(locus.getOwnerId(),
            locus.getType().name(), locus.getTerminalId(), locus.getPathId(),
            serviceability.getFaultClearingRepairActionIds(),
            serviceability.getWorkflowActionIds());
    }

    String getOwnerId() { return ownerId; }
    String getLocusTypeId() { return locusTypeId; }
    String getTerminalId() { return terminalId; }
    String getPathId() { return pathId; }
    Vector<String> getFaultClearingActionIds() {
        return new Vector<String>(faultClearingActionIds);
    }
    Vector<String> getWorkflowActionIds() {
        return new Vector<String>(workflowActionIds);
    }

    boolean isEquivalentTo(GeneratedDiagnosticRepairSemantics other) {
        return other != null && equal(ownerId, other.ownerId) &&
            equal(locusTypeId, other.locusTypeId) && equal(terminalId, other.terminalId) &&
            equal(pathId, other.pathId) && faultClearingActionIds.equals(
                other.faultClearingActionIds) && workflowActionIds.equals(
                other.workflowActionIds);
    }

    String stableDescription() {
        return ownerId + ":" + locusTypeId + ":" + String.valueOf(terminalId) + ":" +
            String.valueOf(pathId) + ":clear=" + join(faultClearingActionIds) +
            ":workflow=" + join(workflowActionIds);
    }

    private static Vector<String> canonicalCopy(Vector<String> values) {
        Vector<String> result = values == null ? new Vector<String>() :
            new Vector<String>(values);
        Collections.sort(result);
        return result;
    }

    private static boolean equal(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String join(Vector<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) result.append(",");
            result.append(value);
        }
        return result.toString();
    }
}

/** Immutable record of operations actually exercised by the live verifier. */
final class GeneratedDiagnosticExecutionTrace {
    private final Vector<String> executedActionIds;
    private final Vector<String> executedRepairActionIds;
    private final Vector<String> executedMeterModeIds;
    private final Vector<String> executedInputPowerTransitions;
    private final Vector<String> executedIsolationActionIds;
    private final Vector<String> executedTemporalWaitSamples;
    private final int measuredDiagnosticDepth;
    private final GeneratedDiagnosticRepairSemantics repairSemantics;

    private GeneratedDiagnosticExecutionTrace(Builder builder,
            GeneratedDiagnosticRepairSemantics semantics) {
        executedActionIds = new Vector<String>(builder.executedActionIds);
        executedRepairActionIds = new Vector<String>(builder.executedRepairActionIds);
        executedMeterModeIds = new Vector<String>(builder.executedMeterModeIds);
        executedInputPowerTransitions = new Vector<String>(builder.executedInputPowerTransitions);
        executedIsolationActionIds = new Vector<String>(builder.executedIsolationActionIds);
        executedTemporalWaitSamples = new Vector<String>(builder.executedTemporalWaitSamples);
        measuredDiagnosticDepth = deriveMeasuredDiagnosticDepth(executedMeterModeIds,
            executedInputPowerTransitions, executedIsolationActionIds,
            executedTemporalWaitSamples);
        repairSemantics = semantics;
    }

    static Builder builder() { return new Builder(); }
    Vector<String> getExecutedActionIds() { return new Vector<String>(executedActionIds); }
    Vector<String> getExecutedRepairActionIds() {
        return new Vector<String>(executedRepairActionIds);
    }
    Vector<String> getExecutedMeterModeIds() {
        return new Vector<String>(executedMeterModeIds);
    }
    Vector<String> getExecutedInputPowerTransitions() {
        return new Vector<String>(executedInputPowerTransitions);
    }
    Vector<String> getExecutedIsolationActionIds() {
        return new Vector<String>(executedIsolationActionIds);
    }
    Vector<String> getExecutedTemporalWaitSamples() {
        return new Vector<String>(executedTemporalWaitSamples);
    }
    int getMeasuredDiagnosticDepth() { return measuredDiagnosticDepth; }
    boolean hasConsistentMeasuredDepth() {
        return measuredDiagnosticDepth == deriveMeasuredDiagnosticDepth(executedMeterModeIds,
            executedInputPowerTransitions, executedIsolationActionIds,
            executedTemporalWaitSamples);
    }
    GeneratedDiagnosticRepairSemantics getRepairSemantics() { return repairSemantics; }

    /**
     * Measured diagnostic depth is the number of distinct live evidence steps:
     * one per observed meter mode, one per input/power transition, one per
     * executed isolation action, and one per temporal wait/sample.  Repair and
     * retest operations are reachability evidence, so they are intentionally
     * excluded.  Deriving this from the frozen trace prevents a hand-assigned
     * catalog value or a drifting increment counter from becoming difficulty.
     */
    private static int deriveMeasuredDiagnosticDepth(Vector<String> meterModes,
            Vector<String> transitions, Vector<String> isolations,
            Vector<String> temporalSamples) {
        if (meterModes == null || transitions == null || isolations == null ||
                temporalSamples == null)
            throw new IllegalArgumentException("Incomplete diagnostic execution trace");
        return meterModes.size() + transitions.size() + isolations.size() +
            temporalSamples.size();
    }

    static final class Builder {
        private final Vector<String> executedActionIds = new Vector<String>();
        private final Vector<String> executedRepairActionIds = new Vector<String>();
        private final Vector<String> executedMeterModeIds = new Vector<String>();
        private final Vector<String> executedInputPowerTransitions = new Vector<String>();
        private final Vector<String> executedIsolationActionIds = new Vector<String>();
        private final Vector<String> executedTemporalWaitSamples = new Vector<String>();

        void recordAction(String actionId) {
            addUnique(executedActionIds, actionId);
        }

        void recordRepairAction(String actionId) {
            recordAction(actionId);
            addUnique(executedRepairActionIds, actionId);
        }

        void recordMeterMode(String modeId) {
            addUnique(executedMeterModeIds, modeId);
        }

        void recordInputPowerTransition(String transitionId) {
            addUnique(executedInputPowerTransitions, transitionId);
        }

        void recordIsolationAction(String actionId) {
            addUnique(executedIsolationActionIds, actionId);
        }

        void recordTemporalWaitSample(String sampleId, double seconds) {
            if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0)
                throw new IllegalArgumentException("Invalid diagnostic temporal trace value");
            addUnique(executedTemporalWaitSamples, sampleId + "@" + seconds);
        }

        GeneratedDiagnosticExecutionTrace freeze(
                GeneratedDiagnosticRepairSemantics semantics) {
            if (semantics == null)
                throw new IllegalArgumentException("Missing diagnostic repair semantics");
            return new GeneratedDiagnosticExecutionTrace(this, semantics);
        }

        private static void addUnique(Vector<String> values, String value) {
            if (value == null || value.length() == 0)
                throw new IllegalArgumentException("Missing diagnostic execution ID");
            if (!values.contains(value)) values.add(value);
        }
    }
}
