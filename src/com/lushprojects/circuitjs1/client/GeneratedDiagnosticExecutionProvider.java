package com.lushprojects.circuitjs1.client;

/** Construction-backed diagnostic contribution for composed devices. */
interface GeneratedDiagnosticExecutionProvider extends GeneratedDiagnosticProvider {
    BoundedAssemblyPlan getAssemblyPlan();
    ConstructionReceipt getConstructionReceipt();
    String[][] getIsolationPairs();
    String getProbeLabel(String qualifiedPadId);
    boolean isHealthySupport(GeneratedBoardInstance instance);
}
