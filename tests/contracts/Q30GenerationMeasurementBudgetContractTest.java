package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Guards the private extended-deadline entry point before it can cancel work. */
public final class Q30GenerationMeasurementBudgetContractTest {
    private static int assertions;

    public static void main(String[] args) {
        verifyNormalBudgetsRemainFixed();
        GenerationRequest privateRequest = GenerationRequest.forQ30Qualification(13L);
        require(privateRequest.isPrivateDiagnosticQualification() &&
            privateRequest.requiresExplicitCompletion(),
            "only the immutable private request carries measurement capability");
        GenerationRequest ordinaryRequest = GenerationRequest.leaf(Rb15Plan.FAMILY_ID, 13L, false);
        require(!ordinaryRequest.isPrivateDiagnosticQualification(),
            "ordinary requests do not carry private measurement capability");

        rejectedWithoutCancellation(ordinaryRequest, true, true, 300000L,
            IllegalArgumentException.class, "ordinary request");
        rejectedWithoutCancellation(privateRequest, true, true, 89999L,
            IllegalArgumentException.class, "deadline below minimum");
        rejectedWithoutCancellation(privateRequest, true, true, 300001L,
            IllegalArgumentException.class, "deadline above maximum");
        rejectedWithoutCancellation(privateRequest, false, true, 300000L,
            IllegalStateException.class, "missing debug scope");
        rejectedWithoutCancellation(privateRequest, true, false, 300000L,
            IllegalStateException.class, "missing developer-verifier scope");

        System.out.println("PASS: Q30 generation measurement budget contracts " +
            assertions + " assertions");
    }

    private static void verifyNormalBudgetsRemainFixed() {
        require(GenerationCoordinator.MAX_JOB_MILLIS == 90000L,
            "normal generation keeps its 90000 ms deadline");
        require(GenerationCoordinator.MAX_DIAGNOSTIC_MEASUREMENT_JOB_MILLIS == 300000L,
            "private measurement maximum is exactly 300000 ms");
        require(GenerationCoordinator.MAX_STEP_MILLIS == 5000L &&
            GenerationCoordinator.MAX_JOB_STEPS == 640,
            "measurement leaves normal per-unit and work-count limits unchanged");
    }

    private static void rejectedWithoutCancellation(GenerationRequest request,
            boolean debug, boolean verifier, long deadline,
            Class<? extends RuntimeException> expectedType, String label) {
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.troubleshootDebug = debug;
        sim.developerVerifierRunning = verifier;
        CircuitElm.sim = sim;
        GenerationCoordinator coordinator = new GenerationCoordinator(sim);
        sim.generationCoordinator = coordinator;
        ProbeServices services = new ProbeServices();
        GenerationJob sentinel = new GenerationJob(services,
            GenerationCoordinator.MAX_JOB_MILLIS, GenerationCoordinator.MAX_JOB_STEPS,
            GenerationCoordinator.MAX_STEP_MILLIS);
        Field jobField = null;
        try {
            jobField = GenerationCoordinator.class.getDeclaredField("job");
            jobField.setAccessible(true);
            jobField.set(coordinator, sentinel);
            require(coordinator.isRunning(), label + " fixture begins with active coordinator work");

            RuntimeException observed = null;
            try {
                coordinator.startForDiagnosticCacheVerification(request, deadline);
            } catch (RuntimeException rejected) {
                observed = rejected;
            }
            require(observed != null && expectedType.isInstance(observed),
                label + " is rejected with the expected exception");
            require(coordinator.getJob() == sentinel && sentinel.isRunning() &&
                services.abortCalls == 0,
                label + " is rejected before canceling or replacing active work");
            require(sim.getGeneratedBoardInstance() == null && sim.elmList != null &&
                sim.elmList.isEmpty(),
                label + " rejection leaves simulator ownership and graph unchanged");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to install active coordinator guard fixture", failure);
        } finally {
            if (jobField != null) {
                try { jobField.set(coordinator, null); }
                catch (IllegalAccessException failure) {
                    throw new AssertionError("Unable to clear active coordinator guard fixture", failure);
                }
            }
            CircuitElm.sim = prior;
        }
    }

    private static final class ProbeServices implements GenerationJob.Services {
        int abortCalls;
        public int candidateCount() { return 1; }
        public String manifest(int candidate) { return "measurement-guard-probe"; }
        public void beginCandidate(int index) { throw new AssertionError("unused"); }
        public String resolve() { throw new AssertionError("unused"); }
        public String healthy() { throw new AssertionError("unused"); }
        public String physical() { throw new AssertionError("unused"); }
        public boolean proveNext() { throw new AssertionError("unused"); }
        public String symptom() { throw new AssertionError("unused"); }
        public String dependencies() { throw new AssertionError("unused"); }
        public void publish(GenerationReceipt receipt) { throw new AssertionError("unused"); }
        public void abort() { abortCalls++; }
        public boolean isCurrent() { return true; }
        public long nowMillis() { return System.currentTimeMillis(); }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
