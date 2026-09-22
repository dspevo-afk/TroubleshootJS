package com.lushprojects.circuitjs1.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable current native request. Mutable circuits are always constructed afresh. */
final class GenerationRequest {
    private final ChallengeDescriptor descriptor;
    private final boolean composition;
    private final boolean quickPlay;
    private final DifficultyProfile difficulty;
    private final boolean candidateSearch;

    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay) {
        this(descriptor, composition, quickPlay, null);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay, DifficultyProfile difficulty) {
        this(descriptor, composition, quickPlay, difficulty, false);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay,
            DifficultyProfile difficulty, boolean search) {
        if (descriptor == null) throw new IllegalArgumentException("Missing generation descriptor");
        this.descriptor = descriptor;
        this.composition = composition;
        this.quickPlay = quickPlay;
        this.difficulty = difficulty;
        this.candidateSearch = search;
    }

    static GenerationRequest leaf(String familyId, long seed, boolean quickPlay) {
        return new GenerationRequest(ChallengeDescriptor.current(familyId, seed), false, quickPlay, null,
            quickPlay && QuickPlayAdmission.supports(familyId));
    }
    static GenerationRequest controlled(long seed) {
        return new GenerationRequest(BoundedAssemblyRequest.controlledDescriptor(seed), true, false);
    }
    static GenerationRequest player(PlayerLaunchRequest request) {
        if (request == null) throw new IllegalArgumentException("Missing player launch");
        boolean composed = ControlledIndicatorBlockContributions.FAMILY_ID.equals(request.familyId);
        return new GenerationRequest(composed ? BoundedAssemblyRequest.controlledDescriptor(request.seed) :
            ChallengeDescriptor.current(request.familyId, request.seed), composed, false, request.profile, request.candidateSearch);
    }
    DifficultyProfile getDifficulty() { return difficulty; }
    boolean isCandidateSearch() { return candidateSearch; }
    int candidateCount() { return candidateSearch ? QuickPlayAdmission.MAX_CANDIDATES : 1; }
    GenerationRequest candidate(int ordinal) {
        if (ordinal < 0 || ordinal >= candidateCount()) throw new IllegalArgumentException("Unknown candidate");
        if (!candidateSearch) return this;
        long seed = QuickPlayAdmission.candidateSeed(descriptor.getRootSeed(), ordinal);
        ChallengeDescriptor candidateDescriptor = composition ?
            BoundedAssemblyRequest.controlledDescriptor(seed) :
            ChallengeDescriptor.current(descriptor.getDeviceIntent().getId(), seed);
        return new GenerationRequest(candidateDescriptor, composition, quickPlay, difficulty, false);
    }
    String candidateManifest(int ordinal) {
        GenerationRequest exact = candidate(ordinal);
        // The existing generic scheduler sorts manifests. Prefix the bounded ordinal,
        // not the signed seed's lexical value, to preserve the declared retry order.
        return candidateSearch ? "candidate=0" + ordinal + ";" + exact.canonical() : exact.canonical();
    }
    SupportedEnvelope getSupportedEnvelope() { return SupportedEnvelope.current(); }
    String canonical() {
        return "tsj-generation-request/3;native;" + descriptor.toCanonical() +
            ";quickPlay=" + quickPlay + ";layout=" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ";physicalEnvelope=" + getSupportedEnvelope().identity() +
            ";admission=" + QuickPlayAdmission.VERSION + ";search=" + candidateSearch +
            (difficulty == null ? "" : ";difficulty=" + difficulty + "@" + DifficultyProfile.VERSION + ";assessment=" + DifficultyAssessment.VERSION);
    }
    ChallengeDescriptor getDescriptor() { return descriptor; }
    boolean isQuickPlay() { return quickPlay; }
    boolean requiresExplicitCompletion() { return quickPlay || difficulty != null; }
    boolean isComposition() { return composition; }

    Prepared resolve(PlanCache cache) {
        if (candidateSearch) throw new IllegalStateException("Resolve an exact candidate through the admission coordinator");
        if (cache == null) throw new IllegalArgumentException("Missing generation plan cache");
        String key = canonical();
        Prepared cached = cache.get(key);
        if (cached != null) return cached;
        BoundedAssemblyPlan plan = null;
        Rb15Plan rb15 = null;
        if (composition) {
            plan = BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(descriptor));
        } else {
            LeafChallengeReplay.requireSupported(descriptor);
            if(Rb15Plan.FAMILY_ID.equals(descriptor.getDeviceIntent().getId())) rb15=Rb15Plan.resolve(descriptor.getRootSeed());
        }
        Prepared result = new Prepared(this, plan, rb15);
        cache.put(key, result);
        return result;
    }

    static final class Prepared {
        private final GenerationRequest request;
        private final BoundedAssemblyPlan plan;
        private final Rb15Plan rb15;
        private Prepared(GenerationRequest request, BoundedAssemblyPlan plan, Rb15Plan rb15) {
            this.request = request; this.plan = plan; this.rb15=rb15;
        }
        Construction construct() {
            return construct((PcbBoardLayout) null);
        }
        ConstructionSession beginConstruction() {return new ConstructionSession(this);}
        private Construction construct(BoundedGeneratedBoardAssembler.PreparedLayout layout) {
            BoundedGeneratedBoardAssembler.Result result = BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan, layout);
            return new Construction(result.getInstance(), result.getRealizationManifest().toCanonical());
        }
        private Construction construct(BoundedGeneratedBoardAssembler.PreparedLayout layout,
                PhysicalConstructionMetadata initialMetadata) {
            BoundedGeneratedBoardAssembler.Result result =
                BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan, layout, initialMetadata);
            return new Construction(result.getInstance(), result.getRealizationManifest().toCanonical());
        }
        private Construction construct(PcbBoardLayout layout) {
            if(rb15 != null) return new Construction(new RelayOutputGenerator().generateResolved(rb15.seed,null,
                layout==null?rb15:rb15.withRoutedLayout(layout)),rb15.canonical());
            if (plan == null)
                return new Construction(LeafChallengeReplay.generate(request.descriptor), request.canonical());
            BoundedGeneratedBoardAssembler.Result result = BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan);
            return new Construction(result.getInstance(), result.getRealizationManifest().toCanonical());
        }
        String canonical() { return request.canonical(); }
    }

    /** Mutable work belongs to one job, outside the immutable resolution cache. */
    static final class ConstructionSession {
        private final Prepared prepared;
        private final SeededPcbLayoutGenerator.Session routing;
        private final BoundedGeneratedBoardAssembler.LayoutSession compositionRouting;
        private Construction result;
        ConstructionSession(Prepared prepared) {
            this.prepared=prepared;
            Rb15Plan plan=prepared.rb15;
            routing=plan==null?null:new SeededPcbLayoutGenerator().begin(plan.board(),plan.layoutSeed,plan.routingSeed,prepared.request.getSupportedEnvelope());
            compositionRouting=prepared.plan != null && prepared.plan.isControlledIndicator() ?
                BoundedGeneratedBoardAssembler.beginLayout(prepared.plan) : null;
        }
        boolean advance() {
            if(result!=null)throw new IllegalStateException("Construction already completed");
            if (compositionRouting != null) {
                if (!compositionRouting.advance()) return false;
                result=prepared.construct(compositionRouting.result(),
                    compositionRouting.initialMetadata()); return true;
            }
            if(routing!=null && !routing.advance())return false;
            result=prepared.construct(routing==null?(PcbBoardLayout)null:routing.result());return true;
        }
        Construction result() {
            if(result==null)throw new IllegalStateException("Construction is incomplete");return result;
        }
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
