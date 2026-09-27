package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Registration, family-owned resolution and staged construction contract. */
public final class StagedFamilyRegistrationContractTest {
    private static int assertions;
    private static final String SECOND_FAMILY = "STAGED_REGISTRATION_FIXTURE";

    public static void main(String[] args) {
        verifyRegisteredAndEnabledAreSeparate();
        verifySecondProviderUsesTheGenericRequestAndSession();
        System.out.println("PASS: staged family registration contracts " + assertions + " assertions");
    }

    private static void verifyRegisteredAndEnabledAreSeparate() {
        require(PlayerFamilyCatalog.isRegistered(Rb30Plan.FAMILY_ID) &&
            PlayerFamilyCatalog.contains(Rb30Plan.FAMILY_ID),
            "registered family identity remains available to exact replay and private work");
        require(!PlayerFamilyCatalog.isNormalPlayerEnabled(Rb30Plan.FAMILY_ID) &&
            !PlayerFamilyCatalog.families().contains(Rb30Plan.FAMILY_ID) &&
            PlayerFamilyCatalog.registeredFamilies().contains(Rb30Plan.FAMILY_ID),
            "disabled normal family is registered but absent from the player catalog");
        require(!PlayerFamilyCatalog.executionDeclaration(Rb30Plan.FAMILY_ID).normalPlayerEnabled,
            "execution declaration carries normal admission independently of family registration");

        boolean rejected = false;
        try { PlayerFamilyCatalog.requireNormalPlayerEnabled(Rb30Plan.FAMILY_ID); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "normal admission rejects a registered but disabled family");

        rejected = false;
        try { PlayerFamilyCatalog.fromRoute("multirail-control"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "disabled family has no normal-player route");

        GenerationRequest privateRequest = GenerationRequest.forFamilyQualification(
            Rb30Plan.FAMILY_ID, 13L);
        require(privateRequest.isPrivateDiagnosticQualification() &&
            privateRequest.getExecutionPolicy() == GenerationExecutionPolicy.SMALL_BOARD &&
            privateRequest.getRequiredPhysicalAdmissionIdentity().equals(
                MediumBoardNormalAdmission.IDENTITY),
            "private qualification reuses the registered family with its isolated request policy");

        String title = Rb30Plan.resolve(13L).board().getSilkscreenTitle();
        require("TSJ MULTI-RAIL CONTROL".equals(title),
            "family board metadata owns its silkscreen title");
    }

    private static void verifySecondProviderUsesTheGenericRequestAndSession() {
        PlayerFamilyCatalog.RegistrationBoundary registry =
            PlayerFamilyCatalog.newRegistrationBoundary();
        registry.registerStagedFamily(new FixtureFamilyCapability(), true);
        require(registry.isRegistered(SECOND_FAMILY) && registry.isNormalPlayerEnabled(SECOND_FAMILY) &&
            registry.families().contains(SECOND_FAMILY),
            "a second staged family registers through the same catalog boundary");

        final long seed = -9007199254740993L;
        GenerationRequest search = GenerationRequest.stagedQuickPlay(
            new QuickPlaySelection(SECOND_FAMILY, seed), registry);
        require(search.getFamilyId().equals(SECOND_FAMILY) && !search.isPrivateDiagnosticQualification() &&
            search.isCandidateSearch() && search.getDifficulty() == DifficultyProfile.MEDIUM &&
            search.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            search.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES,
            "generic staged request selects a second normal-medium provider without a family branch");
        GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(search);
        GenerationRequest exact = search.candidate(2);
        long candidateSeed = QuickPlayAdmission.candidateSeed(seed, 2);
        require(!exact.isCandidateSearch() && exact.getFamilyId().equals(SECOND_FAMILY) &&
            exact.getDescriptor().getRootSeed() == candidateSeed &&
            exact.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            exact.getDifficulty() == DifficultyProfile.MEDIUM &&
            search.candidateManifest(2).startsWith("candidate=02;") &&
            search.candidateManifest(2).contains("fixture-plan@1;seed=" + Long.toString(candidateSeed)),
            "candidate derivation retains the provider, exact seed and family-owned identity");
        GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(exact);

        GenerationRequest.PlanCache cache = new GenerationRequest.PlanCache();
        GenerationRequest.Prepared prepared = exact.resolve(cache);
        GenerationRequest.Prepared repeated = search.candidate(2).resolve(cache);
        require(prepared == repeated && cache.getHits() == 1 && cache.getMisses() == 1,
            "family-owned plan resolution participates in the immutable request cache");
        require(prepared.canonical().contains("fixture-plan@1;seed=" + Long.toString(candidateSeed)) &&
            prepared.canonical().contains("root-seed=" + Long.toString(candidateSeed)),
            "provider identity retains an exact signed-long seed");

        CirSim previous = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = null;
        try {
            GenerationRequest.ConstructionSession session = prepared.beginConstruction();
            require(!session.advance(), "provider session can yield before construction");
            require(session.advance(), "generic request scheduler completes the provider session");
            GenerationRequest.Construction construction = session.result();
            owner = construction.instance;
            require(owner != null && construction.realizationManifest.equals(
                "fixture-realization@1;seed=" + Long.toString(candidateSeed)),
                "provider returns the constructed owner and its realization receipt");
        } finally {
            if (owner != null) {
                if (owner.getExternalPowerBindings() != null)
                    owner.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : owner.getSimulationElements()) element.delete();
            }
            CircuitElm.sim = previous;
        }
    }

    private static final class FixtureFamilyCapability implements StagedFamilyCapability {
        public String familyId() { return SECOND_FAMILY; }
        public String displayName() { return "Staged registration fixture"; }
        public String routeId() { return "staged-registration-fixture"; }
        public DifficultyProfile candidateProfile() { return DifficultyProfile.MEDIUM; }
        public GenerationExecutionPolicy executionPolicy() { return GenerationExecutionPolicy.NORMAL_MEDIUM; }
        public String physicalAdmissionIdentity() { return MediumBoardNormalAdmission.IDENTITY; }
        public boolean supportsPrivateQualification() { return false; }

        public Plan resolve(long seed) { return new FixturePlan(seed); }
        public String canonicalIdentity(Plan plan) {
            return "fixture-plan@1;seed=" + Long.toString(fixturePlan(plan).seed);
        }
        public String canonicalRequest(Plan plan, RequestIdentity request) {
            return "staged-fixture-request@1;family=" + request.familyId + ";" +
                request.descriptorCanonical + ";profile=" + request.difficulty.name() +
                ";execution=" + request.executionPolicyCanonical +
                ";quickPlay=" + request.quickPlay + ";search=" + request.candidateSearch +
                ";plan=" + canonicalIdentity(plan);
        }
        public ConstructionSession beginConstruction(Plan plan) {
            final long seed = fixturePlan(plan).seed;
            return new ConstructionSession() {
                private boolean yielded;
                private GenerationRequest.Construction result;
                public boolean advance() {
                    if (result != null) throw new IllegalStateException("Construction already completed");
                    if (!yielded) { yielded = true; return false; }
                    // This LED is only a construction-dispatch sentinel, not admission proof.
                    GeneratedBoardInstance instance = LeafChallengeReplay.generate(
                        ChallengeDescriptor.current(QuickPlayFamilyRegistry.LED_INDICATOR, seed));
                    result = new GenerationRequest.Construction(instance,
                        "fixture-realization@1;seed=" + Long.toString(seed));
                    return true;
                }
                public GenerationRequest.Construction result() {
                    if (result == null) throw new IllegalStateException("Construction is incomplete");
                    return result;
                }
            };
        }

        private FixturePlan fixturePlan(Plan plan) {
            if (!(plan instanceof FixturePlan))
                throw new IllegalArgumentException("Unexpected fixture family plan");
            return (FixturePlan) plan;
        }
    }

    private static final class FixturePlan implements StagedFamilyCapability.Plan {
        final long seed;
        FixturePlan(long seed) { this.seed = seed; }
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }
}
