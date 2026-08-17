package com.lushprojects.circuitjs1.client;

/** The family and generator seed selected for one Quick Play board. */
final class QuickPlaySelection {
    private final String familyId;
    private final long seed;

    QuickPlaySelection(String familyId, long seed) {
        if (familyId == null || familyId.length() == 0)
            throw new IllegalArgumentException("Missing Quick Play family");
        this.familyId = familyId;
        this.seed = seed;
    }

    String getFamilyId() { return familyId; }
    long getSeed() { return seed; }
}
