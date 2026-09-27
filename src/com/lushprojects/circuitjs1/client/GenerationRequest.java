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
    private final boolean privateQualification;
    private final GenerationExecutionPolicy normalExecutionPolicy;
    private final StagedFamilyCapability stagedFamily;
    private final PlayerFamilyCatalog.ExecutionDeclaration executionDeclaration;

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
        this(descriptor, composition, quickPlay, difficulty, search, qualification,
            normalExecutionPolicy, null, null);
    }
    private GenerationRequest(ChallengeDescriptor descriptor, boolean composition, boolean quickPlay,
            DifficultyProfile difficulty, boolean search, boolean qualification,
            GenerationExecutionPolicy normalExecutionPolicy, StagedFamilyCapability stagedFamily,
            PlayerFamilyCatalog.ExecutionDeclaration requestedExecutionDeclaration) {
        if (descriptor == null) throw new IllegalArgumentException("Missing generation descriptor");
        if (normalExecutionPolicy != GenerationExecutionPolicy.SMALL_BOARD &&
                normalExecutionPolicy != GenerationExecutionPolicy.NORMAL_MEDIUM)
            throw new IllegalArgumentException("Missing or unknown generation execution policy");
        String familyId = composition ? ControlledIndicatorBlockContributions.FAMILY_ID :
            descriptor.getDeviceIntent().getId();
        if (qualification && (composition || quickPlay || difficulty != null || search ||
                normalExecutionPolicy != GenerationExecutionPolicy.SMALL_BOARD || stagedFamily == null ||
                !stagedFamily.supportsPrivateQualification() || !stagedFamily.familyId().equals(familyId)))
            throw new IllegalArgumentException("Private family qualification requires an exact registered capability");
        if (stagedFamily != null && !stagedFamily.familyId().equals(familyId))
            throw new IllegalArgumentException("Staged family capability differs from its request identity");
        PlayerFamilyCatalog.ExecutionDeclaration declaration = requestedExecutionDeclaration;
        if (!qualification) {
            if (declaration == null) declaration = PlayerFamilyCatalog.executionDeclaration(familyId);
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
        this.privateQualification = qualification;
        this.normalExecutionPolicy = normalExecutionPolicy;
        this.stagedFamily = stagedFamily;
        this.executionDeclaration = declaration;
    }

    static GenerationRequest leaf(String familyId, long seed, boolean quickPlay) {
        ChallengeDescriptor descriptor = ChallengeDescriptor.current(familyId, seed);
        LeafChallengeReplay.requireSupported(descriptor);
        return new GenerationRequest(descriptor, false, quickPlay, null,
            quickPlay && QuickPlayFamilyRegistry.isNormalPlayerEligible(familyId));
    }
    /** Internal private diagnostic qualification through an existing family provider. */
    static GenerationRequest forFamilyQualification(String familyId, long seed) {
        return forFamilyQualification(familyId, seed,
            PlayerFamilyCatalog.newRegistrationBoundary());
    }
    static GenerationRequest forFamilyQualification(String familyId, long seed,
            PlayerFamilyCatalog.RegistrationBoundary registration) {
        if (registration == null) throw new IllegalArgumentException("Missing family registration boundary");
        StagedFamilyCapability capability = registration.stagedCapability(familyId);
        if (capability == null || !capability.supportsPrivateQualification())
            throw new IllegalArgumentException("Family does not provide private diagnostic qualification");
        return new GenerationRequest(ChallengeDescriptor.current(familyId, seed), false, false,
            null, false, true, GenerationExecutionPolicy.SMALL_BOARD, capability, null);
    }
    static GenerationRequest controlled(long seed) {
        return new GenerationRequest(BoundedAssemblyRequest.controlledDescriptor(seed), true, false);
    }
    static GenerationRequest player(PlayerLaunchRequest request) {
        return player(request, PlayerFamilyCatalog.newRegistrationBoundary());
    }
    static GenerationRequest player(PlayerLaunchRequest request,
            PlayerFamilyCatalog.RegistrationBoundary registration) {
        if (request == null) throw new IllegalArgumentException("Missing player launch");
        if (registration == null) throw new IllegalArgumentException("Missing family registration boundary");
        PlayerFamilyCatalog.ExecutionDeclaration declaration = registration.executionDeclaration(request.familyId);
        if (declaration.policy == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                request.profile != declaration.candidateProfile)
            throw new IllegalArgumentException("Player profile differs from its catalog execution declaration");
        boolean composed = ControlledIndicatorBlockContributions.FAMILY_ID.equals(request.familyId);
        StagedFamilyCapability capability = registration.stagedCapability(request.familyId);
        return new GenerationRequest(playerDescriptor(request.familyId, request.seed, composed),
            composed, false, request.profile, request.candidateSearch, false, declaration.policy,
            capability, declaration);
    }
    /** Stages a selected registered family through the coordinator; synchronous generation remains leaf-only. */
    static GenerationRequest stagedQuickPlay(QuickPlaySelection selection) {
        return stagedQuickPlay(selection, PlayerFamilyCatalog.newRegistrationBoundary());
    }
    static GenerationRequest stagedQuickPlay(QuickPlaySelection selection,
            PlayerFamilyCatalog.RegistrationBoundary registration) {
        if (selection == null || registration == null ||
                !registration.isRegistered(selection.getFamilyId()))
            throw new IllegalArgumentException("Invalid staged Quick Play selection");
        String familyId = selection.getFamilyId();
        if (!QuickPlayAdmission.supports(familyId,
                registration.executionDeclaration(familyId).candidateProfile, registration))
            throw new IllegalArgumentException("Family has no normal-player Quick Play admission");
        PlayerFamilyCatalog.ExecutionDeclaration declaration = registration.executionDeclaration(familyId);
        DifficultyProfile profile = declaration.candidateProfile;
        boolean composed = ControlledIndicatorBlockContributions.FAMILY_ID.equals(familyId);
        StagedFamilyCapability capability = registration.stagedCapability(familyId);
        return new GenerationRequest(playerDescriptor(familyId, selection.getSeed(), composed),
            composed, true, profile, QuickPlayAdmission.supports(familyId, profile, registration), false,
            declaration.policy, capability, declaration);
    }
    private static ChallengeDescriptor playerDescriptor(String familyId, long seed, boolean composed) {
        return composed ? BoundedAssemblyRequest.controlledDescriptor(seed) :
            ChallengeDescriptor.current(familyId, seed);
    }
    DifficultyProfile getDifficulty() { return difficulty; }
    GenerationExecutionPolicy getExecutionPolicy() {
        if (privateQualification) return normalExecutionPolicy;
        declaredExecution();
        return normalExecutionPolicy;
    }
    String getRequiredPhysicalAdmissionIdentity() {
        if (stagedFamily != null) return stagedFamily.physicalAdmissionIdentity();
        return declaredExecution().physicalAdmissionIdentity;
    }
    private PlayerFamilyCatalog.ExecutionDeclaration declaredExecution() {
        PlayerFamilyCatalog.ExecutionDeclaration declaration = executionDeclaration != null ?
            executionDeclaration : PlayerFamilyCatalog.executionDeclaration(getFamilyId());
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
            false, false, normalExecutionPolicy, stagedFamily, executionDeclaration);
    }
    String candidateManifest(int ordinal) {
        GenerationRequest exact = candidate(ordinal);
        // The existing generic scheduler sorts manifests. Prefix the bounded ordinal,
        // not the signed seed's lexical value, to preserve the declared retry order.
        return candidateSearch ? "candidate=0" + ordinal + ";" + exact.canonical() : exact.canonical();
    }
    SupportedEnvelope getSupportedEnvelope() { return SupportedEnvelope.current(); }
    String canonical() {
        if (stagedFamily != null) {
            PlayerFamilyCatalog.ExecutionDeclaration declaration = privateQualification ? null :
                declaredExecution();
            StagedFamilyCapability.RequestIdentity identity = new StagedFamilyCapability.RequestIdentity(
                getFamilyId(), descriptor.getRootSeed(), descriptor.toCanonical(), privateQualification,
                quickPlay, candidateSearch, difficulty, getExecutionPolicy().canonical(),
                getRequiredPhysicalAdmissionIdentity(), declaration == null ? "" : declaration.canonical(),
                Integer.toString(SeededPcbLayoutGenerator.CURRENT_VERSION), PlayerLaunchRequest.EPOCH,
                Integer.toString(QuickPlayAdmission.VERSION), Integer.toString(DifficultyProfile.VERSION),
                Integer.toString(DifficultyAssessment.VERSION));
            return stagedFamily.canonicalRequest(stagedFamily.resolve(descriptor.getRootSeed()), identity);
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
    String getFamilyId() {
        return composition ? ControlledIndicatorBlockContributions.FAMILY_ID :
            descriptor.getDeviceIntent().getId();
    }
    boolean isQuickPlay() { return quickPlay; }
    boolean requiresExplicitCompletion() { return privateQualification || quickPlay || difficulty != null; }
    /** Immutable capability for private diagnostic-cache measurement requests. */
    boolean isPrivateDiagnosticQualification() {
        return privateQualification && requiresExplicitCompletion();
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
        StagedFamilyCapability.Plan stagedPlan = stagedFamily == null ? null :
            stagedFamily.resolve(descriptor.getRootSeed());
        if (stagedFamily == null) {
            if (composition) {
                plan = BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(descriptor));
            } else {
                LeafChallengeReplay.requireSupported(descriptor);
                if(Rb15Plan.FAMILY_ID.equals(descriptor.getDeviceIntent().getId())) rb15=Rb15Plan.resolve(descriptor.getRootSeed());
            }
        }
        Prepared result = new Prepared(this, plan, rb15, stagedFamily, stagedPlan);
        cache.put(key, result);
        return result;
    }

    static final class Prepared {
        private final GenerationRequest request;
        private final BoundedAssemblyPlan plan;
        private final Rb15Plan rb15;
        private final StagedFamilyCapability stagedFamily;
        private final StagedFamilyCapability.Plan stagedPlan;
        private Prepared(GenerationRequest request, BoundedAssemblyPlan plan, Rb15Plan rb15,
                StagedFamilyCapability stagedFamily, StagedFamilyCapability.Plan stagedPlan) {
            this.request = request; this.plan = plan; this.rb15=rb15;
            this.stagedFamily = stagedFamily; this.stagedPlan = stagedPlan;
        }
        Construction construct() {
            if (stagedFamily != null)
                throw new IllegalStateException("Staged family construction requires its owned admission session");
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
            if (stagedFamily != null)
                throw new IllegalStateException("Staged family construction requires its owned admission session");
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
        private final StagedFamilyCapability.ConstructionSession stagedConstruction;
        private Construction result;
        ConstructionSession(Prepared prepared) {
            this.prepared=prepared;
            Rb15Plan plan=prepared.rb15;
            stagedConstruction = prepared.stagedFamily == null ? null :
                prepared.stagedFamily.beginConstruction(prepared.stagedPlan);
            routing = stagedConstruction != null || plan == null ? null :
                new SeededPcbLayoutGenerator().begin(plan.board(),
                    plan.layoutSeed, plan.routingSeed, prepared.request.getSupportedEnvelope());
            compositionRouting=prepared.plan != null && prepared.plan.isControlledIndicator() ?
                BoundedGeneratedBoardAssembler.beginLayout(prepared.plan) : null;
        }
        boolean advance() {
            if(result!=null)throw new IllegalStateException("Construction already completed");
            if (stagedConstruction != null) {
                if (!stagedConstruction.advance()) return false;
                result = stagedConstruction.result();
                return true;
            }
            if (compositionRouting != null) {
                if (!compositionRouting.advance()) return false;
                result=prepared.construct(compositionRouting.result(),
                    compositionRouting.initialMetadata()); return true;
            }
            if(routing!=null && !routing.advance())return false;
            result=prepared.construct(routing==null?(PcbBoardLayout)null:routing.result());
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
