package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Bounded page-session history of successful New Board physical realizations. */
final class QuickPlayBoardHistory {
    static final int CAPACITY = 16;
    private final Vector<String> recent = new Vector<String>();

    void requireNovel(String fingerprint) {
        if (fingerprint == null || fingerprint.length() == 0)
            throw new IllegalArgumentException("Missing physical board fingerprint");
        if (recent.contains(fingerprint))
            throw new GenerationJob.Rejected("Physical board repeats a recent New Board layout");
    }

    void published(String fingerprint) {
        requireNovel(fingerprint);
        if (recent.size() == CAPACITY) recent.remove(0);
        recent.add(fingerprint);
    }

    int size() { return recent.size(); }
}
