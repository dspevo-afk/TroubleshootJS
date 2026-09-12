package com.lushprojects.circuitjs1.client;

/** Immutable, issuer-bound lineage for one atomically published generation. */
final class GenerationReceipt {
    private final Object issuerToken;
    private final String manifest;
    private final String dependencies;
    private final String[] stageReceipts;
    private final int workCount;
    private final long elapsedMillis;
    private final long[] stageElapsedMillis;
    private final int[] stageWorkCounts;
    private final long maxJobMillis;
    private final long maxStepMillis;
    private final int maxSteps;
    private final boolean complete;
    private final String canonical;

    private GenerationReceipt(Object issuerToken, String manifest, String dependencies,
            String[] stageReceipts, int workCount, long elapsedMillis,
            long[] stageElapsedMillis, int[] stageWorkCounts, long maxJobMillis,
            long maxStepMillis, int maxSteps) {
        if (issuerToken == null || manifest == null || manifest.trim().length() == 0 ||
                dependencies == null || dependencies.trim().length() == 0 ||
                stageReceipts == null || stageReceipts.length != 6 ||
                stageElapsedMillis == null || stageElapsedMillis.length != 6 ||
                stageWorkCounts == null || stageWorkCounts.length != 6 || maxJobMillis <= 0 ||
                maxStepMillis <= 0 || maxSteps <= 0)
            throw new IllegalArgumentException("Cannot issue an incomplete generation receipt");
        this.issuerToken = issuerToken;
        this.manifest = manifest;
        this.dependencies = dependencies;
        this.stageReceipts = copy(stageReceipts);
        for (int i = 0; i < this.stageReceipts.length; i++) {
            if (this.stageReceipts[i] == null || this.stageReceipts[i].trim().length() == 0)
                throw new IllegalArgumentException("Cannot issue a receipt with an empty stage");
        }
        this.workCount = workCount;
        this.elapsedMillis = elapsedMillis < 0 ? 0 : elapsedMillis;
        this.stageElapsedMillis = copy(stageElapsedMillis);
        this.stageWorkCounts = copy(stageWorkCounts);
        this.maxJobMillis = maxJobMillis;
        this.maxStepMillis = maxStepMillis;
        this.maxSteps = maxSteps;
        this.complete = true;
        this.canonical = buildCanonical();
    }

    static GenerationReceipt issue(Object issuerToken, String manifest, String dependencies,
            String[] stageReceipts, int workCount, long elapsedMillis,
            long[] stageElapsedMillis, int[] stageWorkCounts, long maxJobMillis,
            long maxStepMillis, int maxSteps) {
        return new GenerationReceipt(issuerToken, manifest, dependencies, stageReceipts,
            workCount, elapsedMillis, stageElapsedMillis, stageWorkCounts, maxJobMillis,
            maxStepMillis, maxSteps);
    }

    boolean belongsTo(Object token) {
        return issuerToken == token;
    }

    boolean isCompleteInternal() {
        return complete;
    }

    int getMaxStepsInternal() {
        return maxSteps;
    }

    long getMaxJobMillisInternal() {
        return maxJobMillis;
    }

    long getMaxStepMillisInternal() {
        return maxStepMillis;
    }

    String getStageReceiptInternal(int index) {
        return stageReceipts[index];
    }

    String canonical() {
        return canonical;
    }

    String getManifest() {
        return manifest;
    }

    String getDependencies() {
        return dependencies;
    }

    int getStageCount() {
        return stageReceipts.length;
    }

    int getWorkCount() {
        return workCount;
    }

    long getElapsedMillis() {
        return elapsedMillis;
    }

    long getStageElapsedMillis(GenerationJob.Stage stage) {
        if (stage == null)
            throw new IllegalArgumentException("Missing generation stage");
        return stageElapsedMillis[stage.ordinal()];
    }

    int getStageWorkCount(GenerationJob.Stage stage) {
        if (stage == null)
            throw new IllegalArgumentException("Missing generation stage");
        return stageWorkCounts[stage.ordinal()];
    }

    String getStageReceipt(GenerationJob.Stage stage) {
        if (stage == null)
            throw new IllegalArgumentException("Missing generation stage");
        return stageReceipts[stage.ordinal()];
    }

    private String buildCanonical() {
        StringBuilder result = new StringBuilder();
        result.append("TSJ-A10-GENERATION-1");
        appendField(result, "manifest", manifest);
        appendField(result, "dependencies", dependencies);
        result.append(";maxJobMillis=").append(maxJobMillis);
        result.append(";maxStepMillis=").append(maxStepMillis);
        result.append(";maxSteps=").append(maxSteps);
        for (int i = 0; i < stageReceipts.length; i++) {
            result.append(";stage").append(i).append('=');
            appendValue(result, stageReceipts[i]);
        }
        return result.toString();
    }

    private static void appendField(StringBuilder result, String name, String value) {
        result.append(';').append(name).append('=');
        appendValue(result, value);
    }

    private static void appendValue(StringBuilder result, String value) {
        result.append(value.length()).append(':').append(value);
    }

    private static String[] copy(String[] values) {
        String[] copy = new String[values.length];
        for (int i = 0; i < values.length; i++)
            copy[i] = values[i];
        return copy;
    }

    private static long[] copy(long[] values) {
        long[] copy = new long[values.length];
        for (int i = 0; i < values.length; i++)
            copy[i] = values[i];
        return copy;
    }

    private static int[] copy(int[] values) {
        int[] copy = new int[values.length];
        for (int i = 0; i < values.length; i++)
            copy[i] = values[i];
        return copy;
    }
}
