package com.lushprojects.circuitjs1.client;

/** Every selectable family/profile pair uses bounded procedural candidates. */
final class QuickPlayAdmission {
    static final int VERSION = 4, MAX_CANDIDATES = 4;
    private static final long STRIDE = 0x9e3779b97f4a7c15L;
    private QuickPlayAdmission() { }
    static boolean supports(String family, DifficultyProfile profile) {
        return PlayerFamilyCatalog.contains(family) && profile != null && profile.isAvailable() &&
            profile == PlayerFamilyCatalog.candidateProfile(family);
    }
    static boolean supports(String family) {
        return PlayerFamilyCatalog.contains(family);
    }
    static long candidateSeed(long entropy, int ordinal) {
        if (ordinal < 0 || ordinal >= MAX_CANDIDATES)
            throw new IllegalArgumentException("Candidate ordinal outside the qualified bound");
        return entropy + STRIDE * ordinal;
    }
    static String canonical() {
        return "quick-play-admission@" + VERSION +
            ";families=all-current;profiles=menu-qualified;referenceFamilies=none" +
            ";envelope=" + SupportedEnvelope.current().identity() +
            ";candidates=4;order=ordinal;seed=entropy+golden-odd-stride-mod2^64" +
            ";budgets=shared;exactReplayRetries=0;recentPhysicalBoards=16;duplicate=reject-before-publish";
    }
}
