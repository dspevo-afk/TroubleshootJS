package com.lushprojects.circuitjs1.client;

/** Owns one selected/generated board and is discarded on a page reload. */
final class QuickPlaySession {
    private final QuickPlaySelection selection;
    private final GeneratedBoardInstance instance;

    private QuickPlaySession(QuickPlaySelection selection, GeneratedBoardInstance instance) {
        this.selection = selection;
        this.instance = instance;
    }

    static QuickPlaySession create() {
        return create(new QuickPlaySelector());
    }

    static QuickPlaySession create(QuickPlayRandomSource randomSource) {
        return create(new QuickPlaySelector(randomSource));
    }

    private static QuickPlaySession create(QuickPlaySelector selector) {
        QuickPlaySelection selection = selector.select();
        return new QuickPlaySession(selection, selector.generate(selection));
    }

    QuickPlaySelection getSelection() { return selection; }
    GeneratedBoardInstance getInstance() { return instance; }

    private static boolean injectedCandidateFailure;
    static void setCandidateFailureForDeveloperVerification(boolean failure) {
        injectedCandidateFailure = failure;
    }
    static QuickPlaySession forCandidate(QuickPlaySelection selection, GeneratedBoardInstance instance) {
        if (injectedCandidateFailure)
            throw new IllegalStateException("Injected Quick Play candidate validation failure");
        if (selection == null || instance == null ||
                !selection.getFamilyId().equals(instance.getCircuitFamilyId()) ||
                selection.getSeed() != instance.getSeed())
            throw new IllegalArgumentException("Quick Play publication differs from its selected inputs");
        return new QuickPlaySession(selection, instance);
    }
}
