package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Developer-only A01 measurement route.
 *
 * The route constructs a deterministic resistor ladder in the existing
 * CircuitJS graph, runs the real analysis and bounded solver steps, and then
 * restores the exact player owner through Task41SimulationSnapshot.  It does
 * not create a second physics engine or participate in normal admission.
 */
final class A01MeasurementVerifier {
    private static final String PROTOCOL = "TSJ-A01-REPORT-1";
    private static final String BUDGET_PROTOCOL = "TSJ-A01-BUDGET-1";
    private static final String FIXTURE_VERSION = "a01-series-ladder-v1";
    private static final int[] SIZES = { 20, 40, 60, 100 };
    private static final long[] PILOT_SEEDS = { 0L, 1L };
    private static final long[] HOLDOUT_SEEDS = { 2L, 3L };
    private static final int STEPS_PER_ATTEMPT = 2;
    private static final long MAX_ATTEMPT_MILLIS = 5000L;
    private static final long MAX_TOTAL_MILLIS = 30000L;
    private static final int MAX_SOLVER_ELEMENTS = 102;
    private static final int MAX_MATRIX_SIZE = 103;
    /* A corpus contains 16 attempts (four sizes x two seeds x cold/warm),
     * with two accepted steps per attempt.  Pilot + holdout together therefore
     * contain 64 accepted steps. */
    private static final int EXPECTED_ACCEPTED_STEPS = 32;
    private static final int EXPECTED_COMBINED_ACCEPTED_STEPS = 64;

    private A01MeasurementVerifier() { }

    static String measureSolverScaleForA07(CirSim sim) {
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        StringBuilder result = new StringBuilder("[");
        int[] sizes = {20, 40, 60, 100};
        for (int size : sizes) for (int seed = 0; seed < 2; seed++) for (int replicate = 1; replicate <= 2; replicate++) {
            if (result.length() > 1) result.append(",");
            result.append(runAttempt(sim, original, "a07", replicate, size, seed, "fresh"));
        }
        original.assertRestored(sim);
        return result.append("]").toString();
    }

    static String verify(CirSim sim, String corpus, int round, String sourceFingerprint,
            String buildFingerprint, boolean forcedFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning &&
            sim.troubleshootA01Measurement, "explicit A01 developer route is required");
        require("pilot".equals(corpus) || "holdout".equals(corpus),
            "A01 corpus must be pilot or holdout");
        require(round >= 0 && round <= 9, "A01 round is outside the bounded range");
        require(isFingerprint(sourceFingerprint) && isFingerprint(buildFingerprint),
            "A01 source/build identity must be a bound lowercase SHA-256 digest");
        String executedSourceDigest = servedArtifactDigest("source");
        String executedScriptDigest = servedArtifactDigest("script");
        String executedWebDigest = servedArtifactDigest("web");
        String executedDigest = servedArtifactDigest("execution");
        String executedFileCount = servedArtifactFileCount();
        require(isFingerprint(executedSourceDigest) && isFingerprint(executedScriptDigest) &&
            isFingerprint(executedWebDigest) && isFingerprint(executedDigest) &&
            isPositiveInteger(executedFileCount),
            "A01 executed artifact provenance is missing from the loaded preview");
        String[] seeds = seedStrings(corpus);
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        require(owner != null && sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady() && sim.isGeneratedRuntimeSettled(),
            "A01 requires a settled generated owner");
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        boolean priorA01Running = sim.a01MeasurementRunning;
        long priorA01Analysis = sim.a01AnalysisCount;
        long priorA01Stamp = sim.a01StampCount;
        long priorA01Factorization = sim.a01FactorizationCount;
        long priorA01Solve = sim.a01SolveCount;
        long priorA01Iteration = sim.a01IterationCount;
        long priorA01SubIteration = sim.a01SubIterationCount;
        long priorA01Accepted = sim.a01AcceptedStepCount;
        String baseline = baselineJson(sim, owner);
        List<String> attempts = new ArrayList<String>();
        Throwable primary = null;
        boolean restored = false;
        long totalStart = System.currentTimeMillis();
        try {
            if (forcedFailure) {
                try {
                    runForcedFailureCanary(sim, original);
                    throw new IllegalStateException("A01 forced-failure canary did not throw");
                } catch (Throwable failure) {
                    attempts.add(failureAttemptJson(corpus, round, 20, 0L, "canary",
                        failure, "PASS"));
                    throwFailure(failure);
                }
            }
            boolean attemptFailure = false;
            Throwable firstAttemptFailure = null;
            for (int sizeIndex = 0; sizeIndex < SIZES.length; sizeIndex++) {
                int size = SIZES[sizeIndex];
                for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
                    long seed = Long.parseLong(seeds[seedIndex]);
                    String cold = null;
                    try {
                        cold = runAttempt(sim, original, corpus, round, size, seed, "cold");
                        attempts.add(cold);
                    } catch (Throwable failure) {
                        attemptFailure = true;
                        if (firstAttemptFailure == null)
                            firstAttemptFailure = failure;
                        attempts.add(failureAttemptJson(corpus, round, size, seed, "cold",
                            failure, cleanupOf(failure)));
                    }
                    String warm = null;
                    try {
                        warm = runAttempt(sim, original, corpus, round, size, seed, "warm");
                        attempts.add(warm);
                    } catch (Throwable failure) {
                        attemptFailure = true;
                        if (firstAttemptFailure == null)
                            firstAttemptFailure = failure;
                        attempts.add(failureAttemptJson(corpus, round, size, seed, "warm",
                            failure, cleanupOf(failure)));
                    }
                    if (cold != null && warm != null) {
                        try {
                            require(identityOf(cold).equals(identityOf(warm)),
                                "warm construction changed deterministic fixture identity");
                            require(countersOf(cold).equals(countersOf(warm)),
                                "warm construction changed solver work identity");
                            attempts.set(attempts.size() - 2,
                                addAttemptFlag(cold, "identityMatchesWarm", true));
                            attempts.set(attempts.size() - 1,
                                addAttemptFlag(warm, "identityMatchesWarm", true));
                        } catch (Throwable failure) {
                            attemptFailure = true;
                            if (firstAttemptFailure == null)
                                firstAttemptFailure = failure;
                            attempts.set(attempts.size() - 2,
                                addAttemptFlag(attempts.get(attempts.size() - 2), "identityMatchesWarm", false));
                            attempts.set(attempts.size() - 1,
                                addAttemptFlag(attempts.get(attempts.size() - 1), "identityMatchesWarm", false));
                        }
                    } else {
                        attemptFailure = true;
                    }
                }
            }
            long totalElapsed = System.currentTimeMillis() - totalStart;
            require(attempts.size() == 16, "A01 corpus is incomplete");
            require(!attemptFailure && acceptedStepsIn(attempts) == EXPECTED_ACCEPTED_STEPS,
                "A01 corpus retained one or more failed attempts" +
                    (firstAttemptFailure == null ? "" : ": " + safeMessage(firstAttemptFailure)));
            require(totalElapsed >= 0 && totalElapsed <= MAX_TOTAL_MILLIS,
                "A01 total measurement exceeded its frozen budget");
            original.assertRestored(sim);
            restored = true;
            sim.publishA01CleanupForDeveloperVerification("PASS");
            return reportJson(corpus, round, sourceFingerprint, buildFingerprint, baseline,
                attempts, totalElapsed, false, executedSourceDigest, executedScriptDigest,
                executedWebDigest, executedDigest, executedFileCount);
        } catch (Throwable failure) {
            primary = failure;
            throwFailure(failure);
            return null;
        } finally {
            sim.a01MeasurementRunning = false;
            Throwable cleanupFailure = null;
            try {
                original.restore(sim);
                original.assertRestored(sim);
                restored = true;
            } catch (Throwable cleanup) {
                cleanupFailure = cleanup;
                restored = false;
            } finally {
                sim.a01MeasurementRunning = priorA01Running;
                sim.a01AnalysisCount = priorA01Analysis;
                sim.a01StampCount = priorA01Stamp;
                sim.a01FactorizationCount = priorA01Factorization;
                sim.a01SolveCount = priorA01Solve;
                sim.a01IterationCount = priorA01Iteration;
                sim.a01SubIterationCount = priorA01SubIteration;
                sim.a01AcceptedStepCount = priorA01Accepted;
            }
            String cleanupStatus = restored && cleanupFailure == null ? "PASS" : "FAIL";
            if (primary != null || cleanupFailure != null) {
                sim.publishA01EvidenceForDeveloperVerification(failureReportJson(corpus, round,
                    sourceFingerprint, buildFingerprint, baseline, attempts,
                    System.currentTimeMillis() - totalStart, forcedFailure, restored,
                    cleanupStatus, primary == null ? cleanupFailure : primary,
                    executedSourceDigest, executedScriptDigest, executedWebDigest,
                    executedDigest, executedFileCount));
            }
            sim.publishA01CleanupForDeveloperVerification(cleanupStatus);
            if (cleanupFailure != null && primary == null)
                throwFailure(cleanupFailure);
            if (cleanupFailure != null && primary != null)
                throw new IllegalStateException("A01 measurement failure: " +
                    safeMessage(primary) + "; cleanup failure: " + safeMessage(cleanupFailure),
                    primary);
        }
    }

    private static void runForcedFailureCanary(CirSim sim, Task41SimulationSnapshot original) {
        Fixture fixture = null;
        PrivateSolverContext proof = null;
        try {
            proof = PrivateSolverContext.open(sim);
            fixture = createFixture(20, 0L);
            proof.install(fixture.elements);
            sim.a01MeasurementRunning = true;
            sim.a01AnalysisCount = 0;
            sim.a01StampCount = 0;
            sim.a01FactorizationCount = 0;
            sim.a01SolveCount = 0;
            sim.a01IterationCount = 0;
            sim.a01SubIterationCount = 0;
            sim.a01AcceptedStepCount = 0;
            sim.t = 0;
            sim.lastIterTime = 0;
            sim.stopMessage = null;
            proof.analyze();
            require(sim.circuitMatrix != null && sim.stopMessage == null,
                "forced-failure fixture did not install");
            throw new AssertionError("a01-explicit-forced-failure");
        } finally {
            sim.a01MeasurementRunning = false;
            if (proof != null)
                proof.close();
            original.restore(sim);
            original.assertRestored(sim);
        }
    }

    private static String runAttempt(CirSim sim, Task41SimulationSnapshot original,
            String corpus, int round, int size, long seed, String temperature) {
        require("cold".equals(temperature) || "warm".equals(temperature) ||
            ("a07".equals(corpus) && "fresh".equals(temperature)),
            "invalid A01 temperature");
        Fixture fixture = null;
        PrivateSolverContext proof = null;
        long start = System.currentTimeMillis();
        StringBuilder trace = new StringBuilder("[");
        boolean firstTrace = true;
        String result = null;
        Throwable primary = null;
        try {
            proof = PrivateSolverContext.open(sim);
            fixture = createFixture(size, seed);
            proof.install(fixture.elements);
            firstTrace = traceEvent(trace, firstTrace, "constructed", start);
            sim.a01MeasurementRunning = true;
            sim.a01AnalysisCount = 0;
            sim.a01StampCount = 0;
            sim.a01FactorizationCount = 0;
            sim.a01SolveCount = 0;
            sim.a01IterationCount = 0;
            sim.a01SubIterationCount = 0;
            sim.a01AcceptedStepCount = 0;
            sim.t = 0;
            sim.timeStepAccum = 0;
            sim.timeStepCount = 0;
            sim.lastIterTime = 0;
            sim.stopMessage = null;
            proof.analyze();
            require(sim.stopMessage == null && sim.circuitMatrix != null,
                "A01 fixture analysis stopped");
            firstTrace = traceEvent(trace, firstTrace, "analyzed", start);
            for (int step = 0; step < STEPS_PER_ATTEMPT; step++) {
                proof.advanceSteps(1);
                require(sim.stopMessage == null, "A01 solver step stopped: " + sim.stopMessage);
                firstTrace = traceEvent(trace, firstTrace, "step" + (step + 1), start);
            }
            firstTrace = traceEvent(trace, firstTrace, "finished", start);
            trace.append("]");
            result = attemptJson(sim, fixture, corpus, round, temperature, seed, size,
                start, trace.toString());
        } catch (Throwable failure) {
            primary = failure;
        }
        Throwable cleanupFailure = null;
        try {
            sim.a01MeasurementRunning = false;
            if (proof != null)
                proof.close();
        } catch (Throwable cleanup) {
            cleanupFailure = cleanup;
        }
        try {
            original.restore(sim);
            original.assertRestored(sim);
        } catch (Throwable cleanup) {
            if (cleanupFailure == null)
                cleanupFailure = cleanup;
        }
        if (primary != null || cleanupFailure != null)
            throw new AttemptFailure(primary, cleanupFailure);
        return result;
    }

    private static Fixture createFixture(int size, long seed) {
        require(size == 20 || size == 40 || size == 60 || size == 100,
            "unsupported A01 fixture size");
        int top = 64;
        int spacing = 32;
        int bottom = top + spacing * size;
        DCVoltageElm source = new DCVoltageElm(64, bottom);
        source.maxVoltage = 10;
        source.bias = 0;
        source.drag(64, top);
        GroundElm ground = new GroundElm(64, bottom);
        ground.drag(64, bottom + spacing);
        Vector<ResistorElm> resistors = new Vector<ResistorElm>();
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(source);
        elements.add(ground);
        for (int index = 0; index < size; index++) {
            ResistorElm resistor = new ResistorElm(64, top + spacing * index);
            resistor.drag(64, top + spacing * (index + 1));
            resistor.setResistance(resistance(seed, index));
            resistors.add(resistor);
            elements.add(resistor);
        }
        return new Fixture(size, seed, source, ground, resistors, elements);
    }

    private static double resistance(long seed, int index) {
        return 100.0 + 10.0 * ((index + seed) % 7L);
    }

    private static String attemptJson(CirSim sim, Fixture fixture, String corpus, int round,
            String temperature, long seed, int size, long start, String trace) {
        double totalResistance = 0;
        for (int index = 0; index < fixture.resistors.size(); index++)
            totalResistance += fixture.resistors.get(index).getResistance();
        double expectedCurrent = 10.0 / totalResistance;
        double observedCurrent = fixture.resistors.get(0).getCurrent();
        double[] expectedNodes = new double[size + 1];
        double[] observedNodes = new double[size + 1];
        expectedNodes[0] = 10.0;
        observedNodes[0] = fixture.resistors.get(0).getPostVoltage(0);
        double running = 10.0;
        for (int index = 0; index < size; index++) {
            running -= expectedCurrent * fixture.resistors.get(index).getResistance();
            expectedNodes[index + 1] = running;
            observedNodes[index + 1] = fixture.resistors.get(index).getPostVoltage(1);
        }
        for (int index = 0; index < expectedNodes.length; index++)
            require(close(expectedNodes[index], observedNodes[index], 0.000001),
                "A01 node-voltage oracle failed at " + index + " for size " + size +
                " observed=" + observedNodes[index] + " expected=" + expectedNodes[index]);
        require(close(observedCurrent, expectedCurrent, 0.000000001),
            "A01 current oracle failed for size " + size);
        require(close(fixture.source.getPostVoltage(0), 0.0, 0.000001) &&
            close(fixture.source.getPostVoltage(1), 10.0, 0.000001),
            "A01 source endpoints failed");
        require(sim.a01AnalysisCount > 0 && sim.a01StampCount > 0 &&
            sim.a01FactorizationCount > 0 && sim.a01SolveCount >= STEPS_PER_ATTEMPT &&
            sim.a01IterationCount >= STEPS_PER_ATTEMPT &&
            sim.a01SubIterationCount >= STEPS_PER_ATTEMPT &&
            sim.a01AcceptedStepCount == STEPS_PER_ATTEMPT,
            "A01 owner-side work counters are incomplete");
        require(sim.circuitMatrixFullSize == size + 3 &&
            sim.circuitMatrixFullSize <= MAX_MATRIX_SIZE &&
            sim.elmList.size() == size + 2 &&
            sim.elmList.size() <= MAX_SOLVER_ELEMENTS,
            "A01 matrix/solver inventory changed: matrixFull=" +
            sim.circuitMatrixFullSize + " matrixReduced=" + sim.circuitMatrixSize +
            " elements=" + sim.elmList.size() + " voltageSources=" + sim.voltageSourceCount);
        long elapsed = System.currentTimeMillis() - start;
        require(elapsed >= 0 && elapsed <= MAX_ATTEMPT_MILLIS,
            "A01 attempt exceeded its frozen budget");
        String identity = identity(fixture);
        StringBuilder json = new StringBuilder();
        json.append("{\"attemptId\":").append(q(corpus + "-r" + round + "-" +
            temperature + "-" + size + "-" + seed));
        json.append(",\"corpus\":").append(q(corpus));
        json.append(",\"round\":").append(round);
        if ("a07".equals(corpus))
            json.append(",\"replicate\":").append(round).append(",\"contextReuse\":false");
        else
            json.append(",\"temperature\":").append(q(temperature));
        json.append(",\"size\":").append(size);
        json.append(",\"seed\":").append(seed);
        json.append(",\"status\":\"PASS\"");
        json.append(",\"fixtureVersion\":").append(q(FIXTURE_VERSION));
        json.append(",\"fixtureIdentity\":").append(q(identity));
        json.append(",\"identityIndependentOfTiming\":true");
        json.append(",\"timingIndependentIdentity\":true");
        json.append(",\"physicalPackages\":null,\"pads\":null,\"nets\":null");
        json.append(",\"rawSegments\":null,\"canonicalSegments\":null,\"hypothesisCount\":null");
        json.append(",\"unsupportedStages\":[");
        json.append("{\"stage\":\"physicalPackages\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no PCB packages\"},");
        json.append("{\"stage\":\"pads\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no board pads\"},");
        json.append("{\"stage\":\"nets\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no board net registry\"},");
        json.append("{\"stage\":\"rawSegments\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no raw PCB segments\"},");
        json.append("{\"stage\":\"canonicalSegments\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no canonical PCB segments\"},");
        json.append("{\"stage\":\"hypothesisCount\",\"status\":\"UNSUPPORTED\",\"reason\":\"solver-only synthetic ladder has no diagnostic hypotheses\"},");
        json.append("{\"stage\":\"routing\",\"status\":\"UNSUPPORTED\",\"reason\":\"routing is outside the synthetic solver pilot\"},");
        json.append("{\"stage\":\"diagnostic\",\"status\":\"UNSUPPORTED\",\"reason\":\"synthetic fixture has no player fault hypothesis\"},");
        json.append("{\"stage\":\"playable\",\"status\":\"UNSUPPORTED\",\"reason\":\"synthetic fixture is not a playable board\"}");
        json.append("]");
        json.append(",\"solverElements\":").append(sim.elmList.size());
        json.append(",\"voltageSourceCount\":").append(sim.voltageSourceCount);
        json.append(",\"matrixFullSize\":").append(sim.circuitMatrixFullSize);
        json.append(",\"matrixReducedSize\":").append(sim.circuitMatrixSize);
        json.append(",\"analysisCount\":").append(sim.a01AnalysisCount);
        json.append(",\"stampCount\":").append(sim.a01StampCount);
        json.append(",\"factorizationCount\":").append(sim.a01FactorizationCount);
        json.append(",\"solveCount\":").append(sim.a01SolveCount);
        json.append(",\"iterationCount\":").append(sim.a01IterationCount);
        json.append(",\"subIterationCount\":").append(sim.a01SubIterationCount);
        json.append(",\"acceptedStepCount\":").append(sim.a01AcceptedStepCount);
        json.append(",\"sourceVoltage\":10");
        json.append(",\"totalResistance\":").append(totalResistance);
        json.append(",\"expectedCurrent\":").append(expectedCurrent);
        json.append(",\"observedCurrent\":").append(observedCurrent);
        json.append(",\"expectedNodeVoltages\":").append(values(expectedNodes));
        json.append(",\"nodeVoltages\":").append(values(observedNodes));
        json.append(",\"elapsedMs\":").append(elapsed);
        json.append(",\"simulatedSeconds\":").append(sim.t);
        json.append(",\"matrixDoubleStorageProxyBytes\":").append(matrixDoubleStorageProxy(sim));
        json.append(",\"timingTrace\":").append(trace);
        json.append(",\"stageStatus\":\"SOLVER_PASS\"");
        json.append("}");
        return json.toString();
    }

    private static long matrixDoubleStorageProxy(CirSim sim) {
        long values = 0;
        if (sim.circuitMatrix != null)
            for (double[] row : sim.circuitMatrix) if (row != null) values += row.length;
        if (sim.origMatrix != null && sim.origMatrix != sim.circuitMatrix)
            for (double[] row : sim.origMatrix) if (row != null) values += row.length;
        return values * 8; // Matrix numeric slots only, not a browser heap measurement.
    }

    private static String reportJson(String corpus, int round, String sourceFingerprint,
            String buildFingerprint, String baseline, List<String> attempts, long elapsed,
            boolean forced, String executedSourceDigest, String executedScriptDigest,
            String executedWebDigest, String executedDigest, String executedFileCount) {
        return reportJsonWithStatus(corpus, round, sourceFingerprint, buildFingerprint, baseline,
            attempts, elapsed, forced, "PASS", true, "PASS", null, executedSourceDigest,
            executedScriptDigest, executedWebDigest, executedDigest, executedFileCount);
    }

    private static String failureReportJson(String corpus, int round, String sourceFingerprint,
            String buildFingerprint, String baseline, List<String> attempts, long elapsed,
            boolean forced, boolean restored, String cleanup, Throwable failure,
            String executedSourceDigest, String executedScriptDigest, String executedWebDigest,
            String executedDigest, String executedFileCount) {
        return reportJsonWithStatus(corpus, round, sourceFingerprint, buildFingerprint, baseline,
            attempts, elapsed, forced, "FAIL", restored, cleanup, failure, executedSourceDigest,
            executedScriptDigest, executedWebDigest, executedDigest, executedFileCount);
    }

    private static String reportJsonWithStatus(String corpus, int round, String sourceFingerprint,
            String buildFingerprint, String baseline, List<String> attempts, long elapsed,
            boolean forced, String status, boolean restored, String cleanup, Throwable failure,
            String executedSourceDigest, String executedScriptDigest, String executedWebDigest,
            String executedDigest, String executedFileCount) {
        StringBuilder json = new StringBuilder();
        json.append("{\"protocol\":").append(q(PROTOCOL));
        json.append(",\"status\":").append(q(status));
        json.append(",\"corpus\":").append(q(corpus));
        json.append(",\"round\":").append(round);
        json.append(",\"fixtureVersion\":").append(q(FIXTURE_VERSION));
        json.append(",\"sourceFingerprint\":").append(q(sourceFingerprint));
        json.append(",\"buildFingerprint\":").append(q(buildFingerprint));
        json.append(",\"executedArtifact\":{\"protocol\":\"troubleshootjs-execution-provenance-v1\"");
        json.append(",\"sourceDigest\":").append(q(executedSourceDigest));
        json.append(",\"scriptDigest\":").append(q(executedScriptDigest));
        json.append(",\"webDigest\":").append(q(executedWebDigest));
        json.append(",\"executionDigest\":").append(q(executedDigest));
        json.append(",\"fileCount\":").append(executedFileCount).append('}');
        json.append(",\"architectureManifest\":\"tests/benchmarks/a01-reference-boards.json\"");
        json.append(",\"referenceHost\":\"docs/task-evidence/A01/reference-host.json\"");
        json.append(",\"baseline\":").append(baseline);
        json.append(",\"attempts\":[");
        for (int index = 0; index < attempts.size(); index++) {
            if (index != 0) json.append(',');
            json.append(attempts.get(index));
        }
        json.append("]");
        json.append(",\"acceptedSteps\":").append(acceptedStepsIn(attempts));
        json.append(",\"attemptCount\":").append(attempts.size());
        json.append(",\"budget\":{\"protocol\":").append(q(BUDGET_PROTOCOL));
        json.append(",\"version\":\"a01-budget-v1\",\"frozen\":true");
        json.append(",\"frozenBeforeHoldout\":true,\"maxAttemptElapsedMs\":").append(MAX_ATTEMPT_MILLIS);
        json.append(",\"maxTotalElapsedMs\":").append(MAX_TOTAL_MILLIS);
        json.append(",\"maxSolverElements\":").append(MAX_SOLVER_ELEMENTS);
        json.append(",\"maxMatrixFullSize\":").append(MAX_MATRIX_SIZE);
        json.append(",\"requiredAcceptedSteps\":").append(EXPECTED_ACCEPTED_STEPS);
        json.append(",\"combinedRequiredAcceptedSteps\":").append(EXPECTED_COMBINED_ACCEPTED_STEPS);
        json.append(",\"cancellation\":\"bounded-two-steps-per-attempt\"}");
        json.append(",\"unsupportedStagePolicy\":\"explicit-null-with-reason\"");
        json.append(",\"coldWarmProtocol\":\"cold-first-then-warm-in-one-loaded-application\"");
        json.append(",\"timingCannotAffectIdentity\":true");
        json.append(",\"allOutcomesRetained\":true");
        json.append(",\"originalOwnerRestored\":").append(restored);
        json.append(",\"cleanup\":").append(q(cleanup));
        json.append(",\"performance\":").append(performanceJson(attempts));
        if (failure != null)
            json.append(",\"failure\":").append(q(safeMessage(failure)));
        json.append(",\"forcedFailure\":").append(forced);
        json.append(",\"totalElapsedMs\":").append(elapsed);
        json.append("}");
        return json.toString();
    }

    private static String performanceJson(List<String> attempts) {
        List<Long> elapsed = new ArrayList<Long>();
        int failures = 0;
        for (String attempt : attempts) {
            String status = field(attempt, "status");
            if (!"PASS".equals(status)) {
                failures++;
                continue;
            }
            try {
                elapsed.add(Long.valueOf(field(attempt, "elapsedMs")));
            } catch (NumberFormatException ignored) { }
        }
        long p50 = percentile(elapsed, 50);
        long p95 = percentile(elapsed, 95);
        long worst = 0;
        for (Long value : elapsed)
            worst = Math.max(worst, value.longValue());
        return "{\"sampleCount\":" + attempts.size() +
            ",\"passedAttempts\":" + elapsed.size() +
            ",\"failedAttempts\":" + failures +
            ",\"p50AttemptElapsedMs\":" + p50 +
            ",\"p95AttemptElapsedMs\":" + p95 +
            ",\"worstAttemptElapsedMs\":" + worst +
            ",\"memory\":{\"status\":\"UNAVAILABLE\",\"reason\":\"browser collector records performance.memory when supported\"}," +
            "\"cancellation\":\"bounded-two-steps-per-attempt\"}";
    }

    private static long percentile(List<Long> values, int percentile) {
        if (values.isEmpty())
            return 0;
        List<Long> sorted = new ArrayList<Long>(values);
        for (int left = 0; left < sorted.size(); left++) {
            for (int right = left + 1; right < sorted.size(); right++) {
                if (sorted.get(right).longValue() < sorted.get(left).longValue()) {
                    Long swap = sorted.get(left);
                    sorted.set(left, sorted.get(right));
                    sorted.set(right, swap);
                }
            }
        }
        int index = (percentile * sorted.size() + 99) / 100 - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index).longValue();
    }

    private static int acceptedStepsIn(List<String> attempts) {
        int total = 0;
        for (String attempt : attempts) {
            try {
                total += Integer.parseInt(field(attempt, "acceptedStepCount"));
            } catch (NumberFormatException ignored) { }
        }
        return total;
    }

    private static String failureAttemptJson(String corpus, int round, int size, long seed,
            String temperature, Throwable failure, String cleanup) {
        return "{\"attemptId\":" + q(corpus + "-r" + round + "-" + temperature + "-" +
            size + "-" + seed) + ",\"corpus\":" + q(corpus) + ",\"round\":" + round +
            ",\"temperature\":" + q(temperature) + ",\"size\":" + size +
            ",\"seed\":" + seed + ",\"status\":\"FAIL\",\"stageStatus\":\"SOLVER_FAIL\"" +
            ",\"cleanup\":" + q(cleanup) + ",\"elapsedMs\":0,\"acceptedStepCount\":0" +
            ",\"error\":" + q(safeMessage(failure)) + "}";
    }

    private static String statusOf(String attempt) {
        return field(attempt, "status");
    }

    private static String cleanupOf(Throwable failure) {
        return failure instanceof AttemptFailure &&
            ((AttemptFailure) failure).cleanupFailure == null ? "PASS" : "FAIL";
    }

    private static String baselineJson(CirSim sim, GeneratedBoardInstance owner) {
        TroubleshootBoard board = owner.getBoard();
        PcbBoardLayout layout = owner.getPcbLayout();
        int rawSegments = 0;
        int canonicalSegments = 0;
        if (layout != null) {
            Vector<PcbTraceGeometry> traces = layout.getTraces();
            for (PcbTraceGeometry trace : traces) {
                int segments = trace.getXPoints().length - 1;
                rawSegments += segments;
                canonicalSegments += segments;
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"familyId\":").append(q(owner.getCircuitFamilyId()));
        json.append(",\"topologyVariantId\":").append(q(owner.getTopologyVariantId()));
        json.append(",\"seed\":").append(owner.getSeed());
        json.append(",\"stage\":\"IMPLEMENTED_SMALL_BASELINE\"");
        json.append(",\"physicalPackages\":").append(board.getComponentIds().size());
        json.append(",\"solverElements\":").append(sim.elmList.size());
        json.append(",\"pads\":").append(board.getPadIds().size());
        json.append(",\"nets\":").append(board.getNetIds().size());
        json.append(",\"rawSegments\":").append(rawSegments);
        json.append(",\"canonicalSegments\":").append(canonicalSegments);
        json.append(",\"hypothesisCount\":").append(
            GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(owner.getFaultCandidates()));
        json.append(",\"matrixFullSize\":").append(sim.circuitMatrixFullSize);
        json.append(",\"matrixReducedSize\":").append(sim.circuitMatrixSize);
        json.append(",\"unsupportedStages\":[]");
        json.append(",\"identity\":").append(q(owner.getCircuitFamilyId() + "/" +
            owner.getTopologyVariantId() + "@" + owner.getSeed()));
        json.append("}");
        return json.toString();
    }

    private static String[] seedStrings(String corpus) {
        long[] values = "pilot".equals(corpus) ? PILOT_SEEDS : HOLDOUT_SEEDS;
        String[] result = new String[values.length];
        for (int index = 0; index < values.length; index++)
            result[index] = Long.toString(values[index]);
        return result;
    }

    private static String identity(Fixture fixture) {
        StringBuilder value = new StringBuilder(FIXTURE_VERSION);
        value.append("|size=").append(fixture.size).append("|seed=").append(fixture.seed);
        value.append("|source=10");
        for (int index = 0; index < fixture.resistors.size(); index++)
            value.append("|r").append(index).append('=')
                .append(Double.toString(fixture.resistors.get(index).getResistance()));
        return value.toString();
    }

    private static String identityOf(String attempt) {
        return field(attempt, "fixtureIdentity");
    }

    private static String countersOf(String attempt) {
        return field(attempt, "analysisCount") + "/" + field(attempt, "stampCount") + "/" +
            field(attempt, "factorizationCount") + "/" + field(attempt, "solveCount") + "/" +
            field(attempt, "iterationCount") + "/" + field(attempt, "subIterationCount") + "/" +
            field(attempt, "acceptedStepCount");
    }

    private static String field(String json, String name) {
        String marker = "\"" + name + "\":";
        int start = json.indexOf(marker);
        if (start < 0)
            return "";
        start += marker.length();
        int end = start;
        boolean quoted = start < json.length() && json.charAt(start) == '"';
        if (quoted) {
            end = json.indexOf('"', start + 1);
            return end < 0 ? "" : json.substring(start + 1, end);
        }
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}')
            end++;
        return json.substring(start, end);
    }

    private static String addAttemptFlag(String attempt, String name, boolean value) {
        int end = attempt.lastIndexOf('}');
        return attempt.substring(0, end) + ",\"" + name + "\":" + value + "}";
    }

    private static boolean traceEvent(StringBuilder trace, boolean first, String name, long start) {
        if (!first) trace.append(',');
        trace.append("{\"event\":").append(q(name)).append(",\"ms\":")
            .append(Math.max(0L, System.currentTimeMillis() - start)).append('}');
        return false;
    }

    private static String values(double[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index != 0) result.append(',');
            result.append(Double.toString(values[index]));
        }
        return result.append(']').toString();
    }

    private static boolean close(double actual, double expected, double tolerance) {
        return !Double.isNaN(actual) && !Double.isInfinite(actual) &&
            Math.abs(actual - expected) <= tolerance;
    }

    private static native String servedArtifactDigest(String kind) /*-{
        var name = "data-tsj-preview-" + kind + "-digest";
        return $doc.documentElement.getAttribute(name) || "";
    }-*/;

    private static native String servedArtifactFileCount() /*-{
        return $doc.documentElement.getAttribute("data-tsj-preview-file-count") || "";
    }-*/;

    private static boolean isFingerprint(String value) {
        if (value == null || value.length() != 64)
            return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
                return false;
        }
        return true;
    }

    private static boolean isPositiveInteger(String value) {
        if (value == null || value.length() == 0)
            return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c < '0' || c > '9')
                return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String safeMessage(Throwable failure) {
        if (failure == null)
            return "unknown failure";
        String message = failure.getMessage();
        return message == null || message.length() == 0 ? failure.getClass().getName() : message;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException("A01: " + message);
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw new IllegalStateException("A01 measurement failure", failure);
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static final class AttemptFailure extends RuntimeException {
        final Throwable primaryFailure;
        final Throwable cleanupFailure;

        AttemptFailure(Throwable primaryFailure, Throwable cleanupFailure) {
            super("A01 attempt failed: " +
                (primaryFailure == null ? "cleanup" : safeMessage(primaryFailure)) +
                (cleanupFailure == null ? "" : "; cleanup: " + safeMessage(cleanupFailure)),
                primaryFailure == null ? cleanupFailure : primaryFailure);
            this.primaryFailure = primaryFailure;
            this.cleanupFailure = cleanupFailure;
        }
    }

    private static final class Fixture {
        final int size;
        final long seed;
        final DCVoltageElm source;
        final GroundElm ground;
        final Vector<ResistorElm> resistors;
        final Vector<CircuitElm> elements;

        Fixture(int size, long seed, DCVoltageElm source, GroundElm ground,
                Vector<ResistorElm> resistors, Vector<CircuitElm> elements) {
            this.size = size;
            this.seed = seed;
            this.source = source;
            this.ground = ground;
            this.resistors = resistors;
            this.elements = elements;
        }

    }
}
