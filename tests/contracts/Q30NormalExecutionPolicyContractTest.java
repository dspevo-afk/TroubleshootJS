package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

/** Production capability, physical binding and cumulative deadline falsifiers. */
public final class Q30NormalExecutionPolicyContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        requestContracts();
        preMutationGuards();
        physicalBindings();
        for (GenerationExecutionPolicy policy : new GenerationExecutionPolicy[] {
                GenerationExecutionPolicy.SMALL_BOARD, GenerationExecutionPolicy.NORMAL_MEDIUM }) {
            cumulativeRetryDeadline(policy);
            foregroundDeadline(policy);
        }
        System.out.println("PASS: Q30 normal execution policy contracts " + assertions + " assertions");
    }

    private static GenerationRequest medium(long seed) {
        return new PlayerLaunchRequest(Rb30Plan.FAMILY_ID, Long.toString(seed), "MEDIUM").generation();
    }

    private static void requestContracts() throws Exception {
        GenerationRequest small = new PlayerLaunchRequest("LED_INDICATOR", "3", "EASY").generation();
        GenerationRequest composed = new PlayerLaunchRequest(
            "COMPOSED_CONTROLLED_INDICATOR", "3", "MEDIUM").generation();
        for (GenerationRequest request : new GenerationRequest[] {small, composed}) {
            require(request.getExecutionPolicy() == GenerationExecutionPolicy.SMALL_BOARD &&
                request.getExecutionPolicy().maximumJobMillis == 90000L,
                "small and composed requests retain the exact original deadline");
            request.getExecutionPolicy().requireRequest(request);
            require(request.canonical().contains(request.getExecutionPolicy().canonical()),
                "the small-board allowance also binds normal cache and receipt lineage");
        }
        require(GenerationCoordinator.MAX_JOB_STEPS == 640 && GenerationCoordinator.MAX_STEP_MILLIS == 5000L,
            "normal content retains both independent work guards");
        for (long seed : new long[] {13L, Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L}) {
            GenerationRequest exact = medium(seed);
            GenerationExecutionPolicy policy = exact.getExecutionPolicy();
            policy.requireRequest(exact);
            require(policy == GenerationExecutionPolicy.NORMAL_MEDIUM && policy.maximumJobMillis == 90000L,
                "normal medium retains the frozen 90-second cumulative deadline");
            require(exact.requiresExplicitCompletion() && !exact.isPrivateDiagnosticQualification(),
                "normal requests require real customer completion without verifier capability");
            require(exact.candidateCount() == 1 && exact.candidate(0) == exact &&
                exact.getDescriptor().getRootSeed() == seed, "exact replay cannot substitute another seed");
            require(exact.canonical().contains(policy.canonical()) &&
                exact.canonical().contains(Long.toString(seed)), "policy and exact seed bind cache/receipt lineage");
            require(!exact.canonical().equals(GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, seed).canonical()),
                "private measurements cannot share normal request proof identities");
        }
        GenerationRequest search = PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
            "9223372036854775807", "MEDIUM").generation();
        require(search.candidateCount() == 4, "normal medium retains the four-candidate bound");
        for (int i = 0; i < 4; i++) {
            GenerationRequest exact = search.candidate(i);
            require(exact.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                exact.getDescriptor().getRootSeed() == QuickPlayAdmission.candidateSeed(Long.MAX_VALUE, i),
                "candidate derivation preserves exact modular signed-long seeds and policy");
        }
        boolean rejected = false;
        try { GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(small); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "a small request cannot claim a medium allowance");
        rejected = false;
        try { GenerationExecutionPolicy.NORMAL_MEDIUM.requireRequest(GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, 13)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "private measurement capability is not a normal-medium capability");
        Constructor<GenerationExecutionPolicy> constructor = GenerationExecutionPolicy.class.getDeclaredConstructor(
            String.class, String.class, long.class);
        constructor.setAccessible(true);
        GenerationExecutionPolicy forged = constructor.newInstance("NORMAL_MEDIUM_EXECUTION@0",
            MediumBoardNormalAdmission.IDENTITY, 90000L);
        rejected = false;
        try { forged.requireRequest(medium(13)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "matching numeric budgets cannot authorize a foreign policy epoch");
    }

    private static void preMutationGuards() throws Exception {
        CirSim sim = new CirSim(); sim.elmList = new Vector<CircuitElm>();
        GenerationCoordinator coordinator = new GenerationCoordinator(sim);
        sim.generationCoordinator = coordinator;
        DeadlineServices service = new DeadlineServices(0);
        GenerationJob sentinel = new GenerationJob(service, 90000L, 640, 5000L);
        Field job = GenerationCoordinator.class.getDeclaredField("job");
        job.setAccessible(true); job.set(coordinator, sentinel);
        Method start = GenerationCoordinator.class.getDeclaredMethod("start", GenerationRequest.class,
            GenerationCoordinator.Completion.class, boolean.class, boolean.class, long.class, boolean.class,
            GenerationCoordinator.ControlledP09NegativeForDeveloperVerification.class);
        start.setAccessible(true);
        try {
            for (GenerationRequest request : new GenerationRequest[] {
                    GenerationRequest.leaf("LED_INDICATOR", 3L, false), medium(13),
                    GenerationRequest.forFamilyQualification(Rb30Plan.FAMILY_ID, 13) }) {
                long wrong = 300000L;
                boolean rejected = false;
                try { start.invoke(coordinator, request, null, false, true, wrong, false, null); }
                catch (InvocationTargetException expected) {
                    rejected = expected.getCause() instanceof IllegalStateException ||
                        expected.getCause() instanceof IllegalArgumentException;
                }
                require(rejected && coordinator.getJob() == sentinel && sentinel.isRunning() && service.aborts == 0,
                    "mismatched cap or private capability rejects before cancelling an active job");
                require(sim.elmList.isEmpty() && sim.getGeneratedBoardInstance() == null,
                    "rejected policy leaves graph and board state untouched");
            }
            boolean unsupportedRejected = false;
            try {
                coordinator.start(GenerationRequest.leaf("unsupported-generation-family", 0, false), null, false);
            } catch (ChallengeContractException expected) {
                unsupportedRejected = expected.getCode() == ChallengeContractException.Code.UNSUPPORTED_ID;
            }
            require(unsupportedRejected && coordinator.getJob() == sentinel && sentinel.isRunning() && service.aborts == 0,
                "unsupported leaf identity rejects before cancelling an active coordinator job");
            // Q30 is now published. Capture its valid request, then explicitly
            // hold that exact registration to exercise the pre-mutation gate.
            GenerationRequest blockedRequest = medium(13);
            Field currentCatalog = PlayerFamilyCatalog.class.getDeclaredField("CURRENT");
            currentCatalog.setAccessible(true);
            PlayerFamilyCatalog.RegistrationBoundary registration =
                (PlayerFamilyCatalog.RegistrationBoundary) currentCatalog.get(null);
            boolean previouslyEnabled = registration.isNormalPlayerEnabled(Rb30Plan.FAMILY_ID);
            boolean blocked = false;
            registration.setNormalPlayerEnabled(Rb30Plan.FAMILY_ID, false);
            try { coordinator.start(blockedRequest, null, false); }
            catch (IllegalArgumentException expected) { blocked = true; }
            finally { registration.setNormalPlayerEnabled(Rb30Plan.FAMILY_ID, previouslyEnabled); }
            require(blocked && coordinator.getJob() == sentinel && sentinel.isRunning() && service.aborts == 0,
                "registered but unqualified content cannot cancel a predecessor or begin normal publication");
            require(coordinator.getDiagnosticProofCacheSize() == 0 &&
                coordinator.getDiagnosticMeasurementProofCacheSize() == 0 && sim.elmList.isEmpty(),
                "blocked publication leaves both caches and the live graph unchanged");
        } finally { job.set(coordinator, null); }
    }

    private static void physicalBindings() {
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim(); sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        GeneratedBoardInstance small = null, medium = null;
        try {
            small = new LedIndicatorGenerator().generate(3L);
            GenerationRequest.ConstructionSession construction = medium(13).resolve(
                new GenerationRequest.PlanCache()).beginConstruction();
            int units = 0;
            while (!construction.advance()) require(++units < 640, "physical request stays inside charged routing bound");
            medium = construction.result().instance;
            GenerationExecutionPolicy.SMALL_BOARD.requireOwner(small);
            GenerationExecutionPolicy.NORMAL_MEDIUM.requireOwner(medium);
            require(sim.elmList.isEmpty(), "physical qualification does not install detached candidates");
            boolean rejected = false;
            try { GenerationExecutionPolicy.NORMAL_MEDIUM.requireOwner(small); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "a P09 token cannot satisfy a medium execution contract");
            rejected = false;
            try { GenerationExecutionPolicy.SMALL_BOARD.requireOwner(medium); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "an accepted medium route cannot escape through the small-board contract");
        } finally {
            for (GeneratedBoardInstance owner : new GeneratedBoardInstance[] {small, medium}) {
                if (owner == null) continue;
                owner.getExternalPowerBindings().setConnected(false);
                for (CircuitElm element : owner.getSimulationElements()) element.delete();
            }
            CircuitElm.sim = prior;
        }
    }

    private static void cumulativeRetryDeadline(GenerationExecutionPolicy policy) {
        DeadlineServices service = new DeadlineServices(policy.maximumJobMillis / 100);
        GenerationJob job = new GenerationJob(service, policy.maximumJobMillis, 640, 5000);
        int advances = 0;
        while (job.isRunning()) {
            job.advance(); require(++advances < 640, "deadline cannot be reset by canonical retry");
        }
        require(job.getOutcome() == GenerationJob.Outcome.TIMEOUT && service.begins == 4,
            "four canonical candidates share one cumulative deadline");
        require(service.aborts == 4 && service.publishes == 0 && job.getReceipt() == null,
            "timeout is terminal and each started candidate receives cleanup without publication");
    }

    private static void foregroundDeadline(GenerationExecutionPolicy policy) {
        DeadlineServices service = new DeadlineServices(0);
        service.foreground = new ForegroundGenerationClock(0, false);
        GenerationJob job = new GenerationJob(service, policy.maximumJobMillis, 640, 5000);
        job.advance();
        service.wall = 100; service.foreground.setPaused(service.wall, true);
        service.wall += 600000;
        require(service.foreground.elapsedMillis(service.wall) == 100,
            "hidden time consumes none of either qualified foreground allowance");
        service.foreground.setPaused(service.wall, false);
        service.wall += policy.maximumJobMillis - 101;
        require(service.foreground.elapsedMillis(service.wall) == policy.maximumJobMillis - 1,
            "resuming preserves previously consumed time rather than resetting the deadline");
        job.advance(); require(job.isRunning(), "the last permitted foreground millisecond is still within budget");
        service.wall++;
        job.advance();
        require(job.getOutcome() == GenerationJob.Outcome.TIMEOUT && service.aborts == 1 && service.publishes == 0,
            "both exact deadlines expire after hidden-tab resume and clean up without publication");
    }

    private static final class DeadlineServices implements GenerationJob.Services {
        final long quantum;
        long wall;
        int begins, aborts, publishes, healthyUnits;
        ForegroundGenerationClock foreground;
        DeadlineServices(long quantum) { this.quantum = quantum; }
        public int candidateCount() { return 4; }
        public String manifest(int index) { return "candidate=0" + index; }
        public void beginCandidate(int index) { begins++; healthyUnits = 0; }
        public String resolve() { return "resolved"; }
        public String healthy() {
            wall += quantum;
            if (++healthyUnits == 30) throw new GenerationJob.Rejected("retained deterministic routing rejection");
            return null;
        }
        public String physical() { throw new AssertionError("unused"); }
        public boolean proveNext() { throw new AssertionError("unused"); }
        public String symptom() { throw new AssertionError("unused"); }
        public String dependencies() { throw new AssertionError("unused"); }
        public void publish(GenerationReceipt receipt) { publishes++; }
        public void abort() { aborts++; }
        public boolean isCurrent() { return true; }
        public long nowMillis() { return foreground == null ? wall : foreground.nowMillis(wall); }
    }

    private static void require(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
