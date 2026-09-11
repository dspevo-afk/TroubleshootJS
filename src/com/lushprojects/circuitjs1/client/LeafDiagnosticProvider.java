package com.lushprojects.circuitjs1.client;

/** Immutable leaf recipe context; no selected fault or mutable board is retained. */
abstract class LeafDiagnosticProvider implements GeneratedDiagnosticProvider {
    final long seed;
    LeafDiagnosticProvider(long seed) { this.seed = seed; }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        PhysicalSpecification specification = instance.getPhysicalSpecifications()
            .getSpecification(componentId);
        if (!(specification instanceof ResistorNameplate))
            throw new IllegalArgumentException(
                "No declared diagnostic replacement for " + componentId);
        return "R_CATALOG_" + (long)((ResistorNameplate)specification).getNominalResistanceOhms();
    }

    static void dcSweep(GeneratedDiagnosticProgram.Builder program,
            GeneratedDiagnosticPlan plan, String prefix) {
        for (String pad : plan.getProbeTargetIds())
            if (!pad.equals(plan.getReferenceTargetId()))
                program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                    prefix + "_DC_" + pad, pad, plan.getReferenceTargetId());
    }

    static void passivePair(GeneratedDiagnosticProgram.Builder program,
            String red, String black) {
        program.measure(GeneratedDiagnosticProgram.Kind.RESISTANCE,
            "OHM_" + red + "_" + black, red, black);
        program.measure(GeneratedDiagnosticProgram.Kind.CONTINUITY,
            "CONTINUITY_" + red + "_" + black, red, black);
    }
}
