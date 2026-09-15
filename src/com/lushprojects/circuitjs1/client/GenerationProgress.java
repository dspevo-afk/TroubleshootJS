package com.lushprojects.circuitjs1.client;

/** Public progress reports completed work, never a guessed completion time. */
final class GenerationProgress {
    static final String[] LABELS = { "Resolve circuit", "Build and test healthy circuit",
        "Check the physical board", "Verify measurements and repairs",
        "Prepare the service ticket", "Open the workbench" };
    static int percent(GenerationJob.Stage stage, int completedProofUnits, int expectedProofUnits,
            boolean complete) {
        if (complete) return 100;
        if (stage == null) return 0;
        double fraction = stage.ordinal();
        if (stage == GenerationJob.Stage.HYPOTHESES && expectedProofUnits > 0)
            fraction += Math.min(.99, Math.max(0, completedProofUnits) / (double)expectedProofUnits);
        return Math.min(99, (int)(fraction * 100 / LABELS.length));
    }
    static String label(GenerationJob.Stage stage) {
        return stage == null ? LABELS[0] : LABELS[stage.ordinal()];
    }
    private GenerationProgress() { }
}
