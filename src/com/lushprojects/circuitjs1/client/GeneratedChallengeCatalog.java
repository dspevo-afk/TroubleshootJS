package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedChallengeCatalog {
    private final Vector<GeneratedChallengeDefinition> candidates =
        new Vector<GeneratedChallengeDefinition>();

    void addCandidate(GeneratedChallengeDefinition definition) {
        if (definition == null)
            throw new IllegalArgumentException("Missing challenge candidate");
        candidates.add(definition);
    }

    GeneratedChallengeDefinition select(long seed) {
        if (candidates.isEmpty())
            throw new IllegalStateException("No compatible generated challenge candidates");
        int index = (int) (seed % candidates.size());
        if (index < 0)
            index += candidates.size();
        return candidates.elementAt(index);
    }
}