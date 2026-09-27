package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Private Q30 qualification request and coordinator construction contract. */
public final class Q30GenerationRequestContractTest {
    private static int assertions;

    public static void main(String[] args) {
        verifyOrdinaryLeafStillRejectsQ30();
        verifyNormalPlayerRequestsAndExactReplay();
        verifyPrivatePlanCacheAndExactSeeds();
        verifyAcceptedRouteConstruction();
        System.out.println("PASS: q30 generation request contracts " + assertions +
            " assertions");
    }

    private static void verifyOrdinaryLeafStillRejectsQ30() {
        boolean rejected = false;
        try {
            GenerationRequest.leaf(Rb30Plan.FAMILY_ID, 0L, false).resolve(
                new GenerationRequest.PlanCache());
        } catch (ChallengeContractException expected) {
            rejected = expected.getCode() == ChallengeContractException.Code.UNSUPPORTED_ID;
        }
        require(rejected, "ordinary leaf resolution does not admit Q30");
    }

    private static void verifyPrivatePlanCacheAndExactSeeds() {
        GenerationRequest.PlanCache cache = new GenerationRequest.PlanCache();
        GenerationRequest minimum = GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, Long.MIN_VALUE);
        require(minimum.requiresExplicitCompletion(),
            "private normal qualification uses the player explicit customer-retest boundary");
        require(minimum.isPrivateDiagnosticQualification() &&
            minimum.getExecutionPolicy() == GenerationExecutionPolicy.SMALL_BOARD &&
            MediumBoardNormalAdmission.IDENTITY.equals(
                minimum.getRequiredPhysicalAdmissionIdentity()),
            "private Q30 qualification keeps its distinct request budget and physical contract");
        String minimumCanonical = minimum.canonical();
        GenerationRequest.Prepared first = minimum.resolve(cache);
        GenerationRequest.Prepared repeated = GenerationRequest.forFamilyQualification(
            Rb30Plan.FAMILY_ID, Long.MIN_VALUE).resolve(cache);
        require(first == repeated, "identical private request reuses only its immutable prepared plan");
        require(minimumCanonical.equals(repeated.canonical()),
            "repeated qualification canonical is deterministic");
        require(minimumCanonical.contains("root-seed=-9223372036854775808"),
            "signed-long minimum remains exact");
        require(minimumCanonical.contains("rb30-plan@" + Rb30Plan.PLAN_VERSION),
            "request canonical identifies the current Q30 plan epoch");
        require(minimumCanonical.contains("qualification=true") &&
            minimumCanonical.contains("physicalPolicy=" + MediumBoardPhysicalPolicy.identity()) &&
            minimumCanonical.contains("physicalAdmission=" + MediumBoardNormalAdmission.IDENTITY),
            "qualification request is isolated from normal player cache identities");

        GenerationRequest maximum = GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, Long.MAX_VALUE);
        GenerationRequest.Prepared different = maximum.resolve(cache);
        require(different != first && different.canonical().contains(
            "root-seed=9223372036854775807"), "signed-long maximum resolves distinctly");
        require(cache.getHits() == 1 && cache.getMisses() == 2,
            "plan cache distinguishes exact qualification seeds");
    }

    private static void verifyNormalPlayerRequestsAndExactReplay() {
        long[] roots = { Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L,
            9007199254740993L, -1L, 0L, 1L };
        for (long root : roots) {
            PlayerLaunchRequest launch = PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
                Long.toString(root), "MEDIUM");
            GenerationRequest search = launch.generation();
            require(launch.seed == root && launch.profile == DifficultyProfile.MEDIUM &&
                launch.candidateSearch && search.candidateCount() == 4 &&
                search.getDescriptor().getRootSeed() == root && !search.isComposition() &&
                !search.isQuickPlay() && search.requiresExplicitCompletion(),
                "normal Q30 launch lost its exact signed-long medium identity");
            require(search.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                MediumBoardNormalAdmission.IDENTITY.equals(
                    search.getRequiredPhysicalAdmissionIdentity()),
                "normal Q30 launch lacks its versioned execution and physical capabilities");
            GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(search);

            Vector<Long> candidates = new Vector<Long>();
            String previousManifest = "";
            for (int ordinal = 0; ordinal < search.candidateCount(); ordinal++) {
                GenerationRequest candidate = search.candidate(ordinal);
                long expectedSeed = QuickPlayAdmission.candidateSeed(root, ordinal);
                long candidateSeed = candidate.getDescriptor().getRootSeed();
                String manifest = search.candidateManifest(ordinal);
                require(candidateSeed == expectedSeed && candidate.candidateCount() == 1 &&
                    candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                    MediumBoardNormalAdmission.IDENTITY.equals(
                        candidate.getRequiredPhysicalAdmissionIdentity()) &&
                    candidate.getDifficulty() == DifficultyProfile.MEDIUM &&
                    manifest.compareTo(previousManifest) > 0,
                    "normal Q30 candidate changed its exact seed, profile, or immutable capability");
                require(!candidates.contains(Long.valueOf(candidateSeed)),
                    "normal Q30 bounded candidates repeated an exact signed-long seed");
                candidates.add(Long.valueOf(candidateSeed));
                previousManifest = manifest;

                PlayerLaunchRequest accepted = launch.accepted(candidateSeed);
                PlayerLaunchRequest replay = PlayerLaunchRequest.parse(accepted.replay());
                GenerationRequest exact = replay.generation();
                require(!accepted.candidateSearch && !replay.candidateSearch &&
                    replay.familyId.equals(Rb30Plan.FAMILY_ID) &&
                    replay.profile == DifficultyProfile.MEDIUM && replay.seed == candidateSeed &&
                    replay.replay().equals(accepted.replay()) && exact.candidateCount() == 1 &&
                    exact.getDescriptor().getRootSeed() == candidateSeed &&
                    exact.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                    MediumBoardNormalAdmission.IDENTITY.equals(
                        exact.getRequiredPhysicalAdmissionIdentity()),
                    "accepted Q30 replay retried or rounded its medium candidate identity");
                GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(exact);
            }
            require(candidates.size() == QuickPlayAdmission.MAX_CANDIDATES,
                "normal Q30 search did not retain its four-candidate bound");
        }

        QuickPlaySelection selected = new QuickPlaySelection(Rb30Plan.FAMILY_ID,
            Long.MIN_VALUE);
        GenerationRequest staged = GenerationRequest.stagedQuickPlay(selected);
        require(staged.isQuickPlay() && staged.getDifficulty() == DifficultyProfile.MEDIUM &&
            staged.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            MediumBoardNormalAdmission.IDENTITY.equals(
                staged.getRequiredPhysicalAdmissionIdentity()) &&
            staged.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES &&
            staged.getDescriptor().getRootSeed() == Long.MIN_VALUE,
            "legacy Quick Play did not stage Q30 through the normal medium request capability");
    }

    private static void verifyAcceptedRouteConstruction() {
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = null;
        try {
            GenerationRequest.Prepared prepared = GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, 13L)
                .resolve(new GenerationRequest.PlanCache());
            GenerationRequest.ConstructionSession session = prepared.beginConstruction();
            int steps = 0;
            while (!session.advance()) {
                steps++;
                require(steps < GenerationCoordinator.MAX_JOB_STEPS,
                    "medium routing yields within the unchanged job-step budget");
            }
            GenerationRequest.Construction construction = session.result();
            owner = construction.instance;
            require(owner != null && !owner.isDeveloperOnlyFaultRoute(),
                "accepted private route constructs a normal physical owner");
            require(owner.getPhysicalAdmission() != null &&
                MediumBoardNormalAdmission.IDENTITY.equals(
                    owner.getPhysicalAdmission().identity()),
                "constructed owner carries the versioned physical token");
            require(owner.getPcbLayout() != null && owner.getPcbLayout().isSealed() &&
                owner.getPcbLayout().matchesGenerationSeeds(
                    Rb30Plan.resolve(13L).layoutSeed, Rb30Plan.resolve(13L).routingSeed),
                "constructed owner retains the exact sealed seeded route");
            for (CircuitElm element : owner.getSimulationElements())
                require(!sim.elmList.contains(element),
                    "private request leaves its solver elements detached");
        } finally {
            if (owner != null) {
                owner.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : owner.getSimulationElements()) element.delete();
            }
            CircuitElm.sim = prior;
        }
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }
}
