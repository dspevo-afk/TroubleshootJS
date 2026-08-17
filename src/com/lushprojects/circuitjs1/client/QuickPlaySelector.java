package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Selects only Quick Play inputs; family generators remain deterministic. */
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
        Vector<String> familyIds = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        long familyValue = randomSource.nextLong();
        long seedValue = randomSource.nextLong();
        int familyIndex = (int) (familyValue % familyIds.size());
        if (familyIndex < 0)
            familyIndex += familyIds.size();
        String familyId = familyIds.elementAt(familyIndex);
        return new QuickPlaySelection(familyId,
            QuickPlayFamilyRegistry.selectNormalPlayerSeed(familyId, seedValue));
    }

    GeneratedBoardInstance generate(QuickPlaySelection selection) {
        if (selection == null || !QuickPlayFamilyRegistry.isNormalPlayerEligible(
                selection.getFamilyId()))
            throw new IllegalArgumentException("Invalid Quick Play selection");
        return QuickPlayFamilyRegistry.generate(selection.getFamilyId(), selection.getSeed());
    }
}
