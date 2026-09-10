package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Device-owned diagnostic execution seam used by the generic admission proof.
 *
 * <p>The verifier owns the measurement transaction and repair workflow.  A
 * composed device owns the things that depend on its repeated instances:
 * command execution, probe grouping, semantic labels, and the catalog value
 * selected for a physical owner.  Keeping this contract beside the common
 * diagnostic types prevents the verifier from dispatching on a board-family
 * or topology-variant string.</p>
 */
interface GeneratedDiagnosticExecutionProvider {
    BoundedAssemblyPlan getAssemblyPlan();
    ConstructionReceipt getConstructionReceipt();
    GeneratedDiagnosticPlan getDiagnosticPlan();

    /** Exercise each independently controlled channel and append live samples. */
    void collectDcSamples(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticPlan plan, Vector<GeneratedDiagnosticSample> samples,
            GeneratedDiagnosticExecutionTrace.Builder trace,
            GeneratedDiagnosticSampleSink sink);

    /** Public probe pairs used to isolate serviceable owners with the meter. */
    String[][] getIsolationPairs();

    /** Maps a qualified board probe to a player-safe evidence label. */
    String getProbeLabel(String qualifiedPadId);

    /** Resolves the provider-owned replacement catalog entry for one owner. */
    String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId);

    /** Proves that the shared healthy support contribution is operating. */
    boolean isHealthySupport(GeneratedBoardInstance instance);
}

/** Small callback owned by the generic verifier's actual instrument path. */
interface GeneratedDiagnosticSampleSink {
    double measureDc(CirSim sim, GeneratedBoardInstance instance,
            String redId, String blackId,
            GeneratedDiagnosticExecutionTrace.Builder trace);

    void addSample(Vector<GeneratedDiagnosticSample> samples, String sampleId,
            double value);
}
