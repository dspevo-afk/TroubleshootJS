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
    private final boolean q30Qualification;
    private final GenerationExecutionPolicy normalExecutionPolicy;

    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay) {
        this(descriptor, composition, quickPlay, null, false, false,
            GenerationExecutionPolicy.SMALL_BOARD);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay, DifficultyProfile difficulty) {
        this(descriptor, composition, quickPlay, difficulty, false, false,
            GenerationExecutionPolicy.SMALL_BOARD);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay,
            DifficultyProfile difficulty, boolean search) {
        this(descriptor, composition, quickPlay, difficulty, search, false,
            GenerationExecutionPolicy.SMALL_BOARD);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay,
            DifficultyProfile difficulty, boolean search, boolean qualification) {
        this(descriptor, composition, quickPlay, difficulty, search, qualification,
            GenerationExecutionPolicy.SMALL_BOARD);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay,
            DifficultyProfile difficulty, boolean search, boolean qualification,
            GenerationExecutionPolicy normalExecutionPolicy) {
        if (descriptor == null) throw new IllegalArgumentException("Missing generation descriptor");
        if (normalExecutionPolicy != GenerationExecutionPolicy.SMALL_BOARD &&
                normalExecutionPolicy != GenerationExecutionPolicy.NORMAL_MEDIUM)
            throw new IllegalArgumentException("Missing or unknown generation execution policy");
        if (qualification && (composition || quickPlay || difficulty != null || search ||
                normalExecutionPolicy != GenerationExecutionPolicy.SMALL_BOARD ||
                !Rb30Plan.FAMILY_ID.equals(descriptor.getDeviceIntent().getId())))
            throw new IllegalArgumentException("Q30 qualification is a private exact normal-medium request");
        if (!qualification) {
            PlayerFamilyCatalog.ExecutionDeclaration declaration =
                PlayerFamilyCatalog.executionDeclaration(composition ?
                    ControlledIndicatorBlockContributions.FAMILY_ID : descriptor.getDeviceIntent().getId());
            if (declaration.policy != normalExecutionPolicy ||
                    (normalExecutionPolicy == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                        (composition || difficulty != declaration.candidateProfile)))
                throw new IllegalArgumentException("Generation request differs from its catalog execution declaration");
        }
        this.descriptor = descriptor;
        this.composition = composition;
        this.quickPlay = quickPlay;
        this.difficulty = difficulty;
        this.candidateSearch = search;
        this.q30Qualification = qualification;
        this.normalExecutionPolicy = normalExecutionPolicy;
    }

    static GenerationRequest leaf(String familyId, long seed, boolean quickPlay) {
        ChallengeDescriptor descriptor = ChallengeDescriptor.current(familyId, seed);
        LeafChallengeReplay.requireSupported(descriptor);
        return new GenerationRequest(descriptor, false, quickPlay, null,
            quickPlay && QuickPlayFamilyRegistry.isNormalPlayerEligible(familyId));
    }
    /** Internal D01 qualification only. This does not register a player family. */
    static GenerationRequest forQ30Qualification(long seed) {
        return new GenerationRequest(ChallengeDescriptor.current(Rb30Plan.FAMILY_ID, seed),
            false, false, null, false, true);
    }
    static GenerationRequest controlled(long seed) {
        return new GenerationRequest(BoundedAssemblyRequest.controlledDescriptor(seed), true, false);
    }
    static GenerationRequest player(PlayerLaunchRequest request) {
        if (request == null) throw new IllegalArgumentException("Missing player launch");
        PlayerFamilyCatalog.ExecutionDeclaration declaration =
            PlayerFamilyCatalog.executionDeclaration(request.familyId);
        if (declaration.policy == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                request.profile != declaration.candidateProfile)
            throw new IllegalArgumentException("Player profile differs from its catalog execution declaration");
        boolean composed = ControlledIndicatorBlockContributions.FAMILY_ID.equals(request.familyId);
        return new GenerationRequest(playerDescriptor(request.familyId, request.seed, composed),
            composed, false, request.profile, request.candidateSearch, false, declaration.policy);
    }
    /** Stages every catalog family through the coordinator; synchronous generation remains leaf-only. */
    static GenerationRequest stagedQuickPlay(QuickPlaySelection selection) {
        if (selection == null || !PlayerFamilyCatalog.contains(selection.getFamilyId()))
            throw new IllegalArgumentException("Invalid staged Quick Play selection");
        String familyId = selection.getFamilyId();
        PlayerFamilyCatalog.ExecutionDeclaration declaration =
            PlayerFamilyCatalog.executionDeclaration(familyId);
        DifficultyProfile profile = declaration.candidateProfile;
        boolean composed = ControlledIndicatorBlockContributions.FAMILY_ID.equals(familyId);
        return new GenerationRequest(playerDescriptor(familyId, selection.getSeed(), composed),
            composed, true, profile, QuickPlayAdmission.supports(familyId, profile), false,
            declaration.policy);
    }
    private static ChallengeDescriptor playerDescriptor(String familyId, long seed, boolean composed) {
        return composed ? BoundedAssemblyRequest.controlledDescriptor(seed) :
            ChallengeDescriptor.current(familyId, seed);
    }
    DifficultyProfile getDifficulty() { return difficulty; }
    GenerationExecutionPolicy getExecutionPolicy() {
        if (q30Qualification) return normalExecutionPolicy;
        declaredExecution();
        return normalExecutionPolicy;
    }
    String getRequiredPhysicalAdmissionIdentity() {
        return q30Qualification ? MediumBoardNormalAdmission.IDENTITY :
            declaredExecution().physicalAdmissionIdentity;
    }
    private PlayerFamilyCatalog.ExecutionDeclaration declaredExecution() {
        PlayerFamilyCatalog.ExecutionDeclaration declaration =
            PlayerFamilyCatalog.executionDeclaration(composition ?
                ControlledIndicatorBlockContributions.FAMILY_ID : descriptor.getDeviceIntent().getId());
        if (declaration.policy != normalExecutionPolicy ||
                (normalExecutionPolicy == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                    difficulty != null && declaration.candidateProfile != difficulty))
            throw new IllegalArgumentException("Generation request differs from its catalog execution declaration");
        return declaration;
    }
    boolean isCandidateSearch() { return candidateSearch; }
    int candidateCount() { return candidateSearch ? QuickPlayAdmission.MAX_CANDIDATES : 1; }
    GenerationRequest candidate(int ordinal) {
        if (ordinal < 0 || ordinal >= candidateCount()) throw new IllegalArgumentException("Unknown candidate");
        if (!candidateSearch) return this;
        long seed = QuickPlayAdmission.candidateSeed(descriptor.getRootSeed(), ordinal);
        ChallengeDescriptor candidateDescriptor = composition ?
            BoundedAssemblyRequest.controlledDescriptor(seed) :
            ChallengeDescriptor.current(descriptor.getDeviceIntent().getId(), seed);
        return new GenerationRequest(candidateDescriptor, composition, quickPlay, difficulty,
            false, false, normalExecutionPolicy);
    }
    String candidateManifest(int ordinal) {
        GenerationRequest exact = candidate(ordinal);
        // The existing generic scheduler sorts manifests. Prefix the bounded ordinal,
        // not the signed seed's lexical value, to preserve the declared retry order.
        return candidateSearch ? "candidate=0" + ordinal + ";" + exact.canonical() : exact.canonical();
    }
    SupportedEnvelope getSupportedEnvelope() { return SupportedEnvelope.current(); }
    String canonical() {
        if (q30Qualification) {
            Rb30Plan plan = Rb30Plan.resolve(descriptor.getRootSeed());
            return "tsj-generation-request/4;native;qualification-only;" +
                descriptor.toCanonical() + ";quickPlay=false;layout=" +
                SeededPcbLayoutGenerator.CURRENT_VERSION +
                ";qualification=true;explicitCompletion=true;planEpoch=" + Rb30Plan.PLAN_VERSION +
                ";plan=" + plan.canonical() +
                ";physicalPolicy=" + MediumBoardPhysicalPolicy.identity() +
                ";physicalAdmission=" + MediumBoardNormalAdmission.IDENTITY;
        }
        if (normalExecutionPolicy == GenerationExecutionPolicy.NORMAL_MEDIUM) {
            Rb30Plan plan = Rb30Plan.resolve(descriptor.getRootSeed());
            PlayerFamilyCatalog.ExecutionDeclaration declaration = declaredExecution();
            return "tsj-generation-request/5;normal-medium@1;native;" +
                descriptor.toCanonical() + ";replay=" + PlayerLaunchRequest.EPOCH + "/" +
                difficulty.name() + "/" + descriptor.getDeviceIntent().getId() + "/" +
                Long.toString(descriptor.getRootSeed()) + ";quickPlay=" + quickPlay +
                ";layout=" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ";planEpoch=" + Rb30Plan.PLAN_VERSION + ";plan=" + plan.canonical() +
                ";route=" + MediumBoardPhysicalPolicy.identity() +
                ";physicalAdmission=" + getRequiredPhysicalAdmissionIdentity() +
                ";executionPolicy=" + getExecutionPolicy().canonical() +
                ";familyExecution=" + declaration.canonical() +
                ";admission=" + QuickPlayAdmission.VERSION + ";search=" + candidateSearch +
                ";difficulty=" + difficulty.name() + "@" + DifficultyProfile.VERSION +
                ";assessment=" + DifficultyAssessment.VERSION;
        }
        PlayerFamilyCatalog.ExecutionDeclaration declaration = declaredExecution();
        return "tsj-generation-request/6;native;" + descriptor.toCanonical() +
            ";quickPlay=" + quickPlay + ";layout=" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ";physicalEnvelope=" + getSupportedEnvelope().identity() +
            ";executionPolicy=" + getExecutionPolicy().canonical() +
            ";familyExecution=" + declaration.canonical() +
            ";admission=" + QuickPlayAdmission.VERSION + ";search=" + candidateSearch +
            (difficulty == null ? "" : ";difficulty=" + difficulty + "@" + DifficultyProfile.VERSION + ";assessment=" + DifficultyAssessment.VERSION);
    }
    ChallengeDescriptor getDescriptor() { return descriptor; }
    boolean isQuickPlay() { return quickPlay; }
    boolean requiresExplicitCompletion() { return q30Qualification || quickPlay || difficulty != null; }
    /** Immutable capability for private diagnostic-cache measurement requests. */
    boolean isPrivateDiagnosticQualification() {
        return q30Qualification && requiresExplicitCompletion();
    }
    boolean isComposition() { return composition; }

    Prepared resolve(PlanCache cache) {
        if (candidateSearch) throw new IllegalStateException("Resolve an exact candidate through the admission coordinator");
        if (cache == null) throw new IllegalArgumentException("Missing generation plan cache");
        String key = canonical();
        Prepared cached = cache.get(key);
        if (cached != null) return cached;
        BoundedAssemblyPlan plan = null;
        Rb15Plan rb15 = null;
        Rb30Plan rb30 = null;
        if (q30Qualification || normalExecutionPolicy == GenerationExecutionPolicy.NORMAL_MEDIUM) {
            if (!Rb30Plan.FAMILY_ID.equals(descriptor.getDeviceIntent().getId()))
                throw new IllegalStateException("Q30 request descriptor changed after request creation");
            rb30 = Rb30Plan.resolve(descriptor.getRootSeed());
        } else if (composition) {
            plan = BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(descriptor));
        } else {
            LeafChallengeReplay.requireSupported(descriptor);
            if(Rb15Plan.FAMILY_ID.equals(descriptor.getDeviceIntent().getId())) rb15=Rb15Plan.resolve(descriptor.getRootSeed());
        }
        Prepared result = new Prepared(this, plan, rb15, rb30);
        cache.put(key, result);
        return result;
    }

    static final class Prepared {
        private final GenerationRequest request;
        private final BoundedAssemblyPlan plan;
        private final Rb15Plan rb15;
        private final Rb30Plan rb30;
        private Prepared(GenerationRequest request, BoundedAssemblyPlan plan, Rb15Plan rb15,
                Rb30Plan rb30) {
            this.request = request; this.plan = plan; this.rb15=rb15; this.rb30=rb30;
        }
        Construction construct() {
            if (rb30 != null)
                throw new IllegalStateException("Q30 construction requires an accepted physical route before owner construction");
            return construct((PcbBoardLayout) null);
        }
        ConstructionSession beginConstruction() {return new ConstructionSession(this);}
        private Construction construct(MediumBoardPhysicalPolicy.Result route) {
            if (rb30 == null)
                throw new IllegalStateException("No normal-medium plan owns this route result");
            return new Rb30Generator().constructFromAcceptedRoute(rb30, route);
        }
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
            if (rb30 != null)
                throw new IllegalStateException("Q30 construction requires its accepted medium route receipt");
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
            Rb30Plan q30 = prepared.rb30;
            routing = q30 != null ? new SeededPcbLayoutGenerator().begin(q30.board(),
                q30.layoutSeed, q30.routingSeed, null) :
                plan == null ? null : new SeededPcbLayoutGenerator().begin(plan.board(),
                    plan.layoutSeed, plan.routingSeed, prepared.request.getSupportedEnvelope());
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
            if (prepared.rb30 != null) {
                MediumBoardPhysicalPolicy.Result accepted = routing.mediumResult();
                if (accepted == null || !accepted.accepted())
                    throw new IllegalStateException("Q30 qualification completed without an accepted medium route");
                result = prepared.construct(accepted);
            } else {
                result=prepared.construct(routing==null?(PcbBoardLayout)null:routing.result());
            }
            return true;
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
