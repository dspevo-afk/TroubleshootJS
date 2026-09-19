package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Allocates roots distinct from recent New Board launches at the player boundary. */
final class QuickPlayIdentityAllocator {
    private final Vector<Long> recent = new Vector<Long>();

    PlayerLaunchRequest allocate(PlayerLaunchRequest request) {
        if (request == null) throw new IllegalArgumentException("Missing player launch");
        if (!request.candidateSearch) return request;
        long root = request.seed;
        while (recent.contains(Long.valueOf(root)))
            root = QuickPlayAdmission.candidateSeed(root, 1);
        if (recent.size() == QuickPlayBoardHistory.CAPACITY) recent.remove(0);
        recent.add(Long.valueOf(root));
        return root == request.seed ? request :
            PlayerLaunchRequest.random(request.familyId, Long.toString(root), request.profile.name());
    }
}
