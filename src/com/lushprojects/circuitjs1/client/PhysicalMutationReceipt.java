package com.lushprojects.circuitjs1.client;

/** Immutable result of one bounded physical mutation attempt. */
final class PhysicalMutationReceipt {
    enum Outcome {
        COMMITTED,
        COMPENSATED,
        ISOLATED
    }

    private final Outcome outcome;
    private final String operation;
    private final String componentId;
    private final boolean validationPassed;
    private final boolean compensationComplete;
    private final String diagnostic;

    private PhysicalMutationReceipt(Outcome outcome, String operation,
            String componentId, boolean validationPassed, boolean compensationComplete,
            String diagnostic) {
        if (outcome == null)
            throw new IllegalArgumentException("Missing physical mutation outcome");
        this.outcome = outcome;
        this.operation = operation == null ? "physical" : operation;
        this.componentId = componentId;
        this.validationPassed = validationPassed;
        this.compensationComplete = compensationComplete;
        this.diagnostic = diagnostic;
    }

    static PhysicalMutationReceipt committed(PhysicalMutationIntent intent) {
        return new PhysicalMutationReceipt(Outcome.COMMITTED, intent.getOperation(),
            intent.getComponentId(), true, true, null);
    }

    static PhysicalMutationReceipt compensated(PhysicalMutationIntent intent,
            Throwable original, boolean validationPassed) {
        return new PhysicalMutationReceipt(Outcome.COMPENSATED, intent.getOperation(),
            intent.getComponentId(), validationPassed, true, message(original));
    }

    static PhysicalMutationReceipt isolated(PhysicalMutationIntent intent,
            Throwable failure, boolean validationPassed) {
        return new PhysicalMutationReceipt(Outcome.ISOLATED, intent.getOperation(),
            intent.getComponentId(), validationPassed, false, message(failure));
    }

    private static String message(Throwable failure) {
        return failure == null ? null : failure.getClass().getName() + ": " +
            String.valueOf(failure.getMessage());
    }

    Outcome getOutcome() { return outcome; }
    String getOperation() { return operation; }
    String getComponentId() { return componentId; }
    boolean isValidationPassed() { return validationPassed; }
    boolean isCompensationComplete() { return compensationComplete; }
    boolean isCommitted() { return outcome == Outcome.COMMITTED; }
    boolean isCompensated() { return outcome == Outcome.COMPENSATED; }
    boolean isIsolated() { return outcome == Outcome.ISOLATED; }
    String getDiagnostic() { return diagnostic; }
}
