package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Selects full catalog inputs; direct synchronous generation remains leaf-only. */
final class QuickPlaySelector {
    private final QuickPlayRandomSource randomSource;

    QuickPlaySelector() {
        this(new QuickPlayRandomSource() {
            private final Random random = new Random();

            public long nextLong() { return random.nextLong(); }
        });
    }

    QuickPlaySelector(QuickPlayRandomSource randomSource) {
        if (randomSource == null)
            throw new IllegalArgumentException("Missing Quick Play selection source");
        this.randomSource = randomSource;
    }

    QuickPlaySelection select() {
        return selectFrom(PlayerFamilyCatalog.families(), false);
    }

    /** Selection boundary for retained synchronous tests and leaf-only callers. */
    QuickPlaySelection selectSynchronousLeaf() {
        return selectFrom(QuickPlayFamilyRegistry.getNormalPlayerFamilyIds(), true);
    }

    private QuickPlaySelection selectFrom(Vector<String> familyIds, boolean synchronousLeaf) {
        long familyValue = randomSource.nextLong();
        long seedValue = randomSource.nextLong();
        int familyIndex = (int) (familyValue % familyIds.size());
        if (familyIndex < 0)
            familyIndex += familyIds.size();
        String familyId = familyIds.elementAt(familyIndex);
        return new QuickPlaySelection(familyId,
            synchronousLeaf ? QuickPlayFamilyRegistry.selectNormalPlayerSeed(familyId, seedValue) :
                PlayerFamilyCatalog.selectNormalPlayerSeed(familyId, seedValue));
    }

    /** Retained synchronous path. Staged catalog families are intentionally rejected here. */
    GeneratedBoardInstance generate(QuickPlaySelection selection) {
        if (selection == null || !QuickPlayFamilyRegistry.isNormalPlayerEligible(
                selection.getFamilyId()))
            throw new IllegalArgumentException("Invalid Quick Play selection");
        return QuickPlayFamilyRegistry.generate(selection.getFamilyId(), selection.getSeed());
    }
}
