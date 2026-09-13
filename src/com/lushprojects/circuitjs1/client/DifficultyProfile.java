package com.lushprojects.circuitjs1.client;

/** Current admission policy, independent of all electrical model parameters. */
enum DifficultyProfile {
    EASY, MEDIUM, HARD, PSYCHOTIC;

    static final int VERSION = 1;

    static DifficultyProfile parseAvailable(String value) {
        DifficultyProfile profile;
        try { profile = valueOf(value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Unknown difficulty profile"); }
        if (!profile.isAvailable()) throw new IllegalArgumentException("This difficulty profile is unavailable");
        return profile;
    }

    boolean isAvailable() { return this == EASY || this == MEDIUM; }
}
