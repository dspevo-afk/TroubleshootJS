package com.lushprojects.circuitjs1.client;

/** Qualified broad-seed families, not a seed whitelist or a proof substitute. */
final class QuickPlayAdmission {
    static final int VERSION = 1, MAX_CANDIDATES = 4;
    private static final long STRIDE = 0x9e3779b97f4a7c15L;
    private QuickPlayAdmission() { }

    static boolean supports(String family, DifficultyProfile profile) {
        return Rb15Plan.FAMILY_ID.equals(family) && profile == DifficultyProfile.EASY;
    }
    static boolean supports(String family) {
        return supports(family, PlayerFamilyCatalog.candidateProfile(family));
    }
    static long candidateSeed(long entropy, int ordinal) {
        if (ordinal < 0 || ordinal >= MAX_CANDIDATES)
            throw new IllegalArgumentException("Candidate ordinal outside the qualified bound");
        // Odd modular stride: distinct candidates, all 64 input bits retained.
        // Named construction streams independently mix each exact root seed.
        return entropy + STRIDE * ordinal;
    }
    static String canonical() {
        return "quick-play-admission@" + VERSION + ";family=RB15_CONTROL;profile=EASY" +
            ";envelope=" + SupportedEnvelope.current().identity() +
            ";candidates=4;order=ordinal;seed=entropy+golden-odd-stride-mod2^64" +
            ";budgets=shared;exactReplayRetries=0;referenceFamilies=curated";
    }
}
