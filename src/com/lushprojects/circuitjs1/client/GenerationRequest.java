package com.lushprojects.circuitjs1.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable current native request. Mutable circuits are always constructed afresh. */
final class GenerationRequest {
    private final ChallengeDescriptor descriptor;
    private final boolean composition;
    private final boolean quickPlay;

    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay) {
        if (descriptor == null) throw new IllegalArgumentException("Missing generation descriptor");
        this.descriptor = descriptor;
        this.composition = composition;
        this.quickPlay = quickPlay;
    }

    static GenerationRequest leaf(String familyId, long seed, boolean quickPlay) {
        return new GenerationRequest(ChallengeDescriptor.current(familyId, seed), false, quickPlay);
    }
    static GenerationRequest controlled(long seed) {
        return new GenerationRequest(BoundedAssemblyRequest.controlledDescriptor(seed), true, false);
    }
    String canonical() {
        return "tsj-generation-request/1;native;" + descriptor.toCanonical() +
            ";quickPlay=" + quickPlay + ";layout=" + SeededPcbLayoutGenerator.CURRENT_VERSION;
    }
    ChallengeDescriptor getDescriptor() { return descriptor; }
    boolean isQuickPlay() { return quickPlay; }
    boolean isComposition() { return composition; }

    Prepared resolve(PlanCache cache) {
        if (cache == null) throw new IllegalArgumentException("Missing generation plan cache");
        String key = canonical();
        Prepared cached = cache.get(key);
        if (cached != null) return cached;
        BoundedAssemblyPlan plan = null;
        if (composition) {
            plan = BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(descriptor));
        } else {
            LeafChallengeReplay.requireSupported(descriptor);
        }
        Prepared result = new Prepared(this, plan);
        cache.put(key, result);
        return result;
    }

    static final class Prepared {
        private final GenerationRequest request;
        private final BoundedAssemblyPlan plan;
        private Prepared(GenerationRequest request, BoundedAssemblyPlan plan) {
            this.request = request; this.plan = plan;
        }
        Construction construct() {
            if (plan == null)
                return new Construction(LeafChallengeReplay.generate(request.descriptor), request.canonical());
            BoundedGeneratedBoardAssembler.Result result = BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan);
            return new Construction(result.getInstance(), result.getRealizationManifest().toCanonical());
        }
        String canonical() { return request.canonical(); }
    }

    static final class Construction {
        final GeneratedBoardInstance instance;
        final String realizationManifest;
        Construction(GeneratedBoardInstance instance, String realizationManifest) {
            if (instance == null || realizationManifest == null || realizationManifest.length() == 0)
                throw new IllegalArgumentException("Missing constructed generation provenance");
            this.instance = instance; this.realizationManifest = realizationManifest;
        }
    }

    /** Page-lifetime, bounded cache of immutable resolution only. No proof or graph cache. */
    static final class PlanCache {
        private static final int CAPACITY = 16;
        private final Map<String, Prepared> entries = new LinkedHashMap<String, Prepared>();
        private int hits, misses;
        Prepared get(String key) {
            Prepared value = entries.get(key);
            if (value == null) misses++; else hits++;
            return value;
        }
        void put(String key, Prepared value) {
            if (entries.size() >= CAPACITY && !entries.containsKey(key))
                entries.remove(entries.keySet().iterator().next());
            entries.put(key, value);
        }
        int getHits() { return hits; }
        int getMisses() { return misses; }
        int size() { return entries.size(); }
    }
}
