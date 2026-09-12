package com.lushprojects.circuitjs1.client;

/** Pure/native contracts for the A10 staged job ledger. */
public final class A10GenerationContractTest {
    private static int assertions;

    private A10GenerationContractTest() { }

    public static void main(String[] args) {
        literalStageOrderingAndAtomicPublish();
        cancellationAtEveryStage();
        staleCallback();
        reorderedCandidateCollection();
        sameIdentityDespiteClocks();
        healthyPartialUnits();
        unitClockStartsAfterYield();
        failedStageTelemetryAndBudgetIdentity();
        expectedAndUnexpectedFailure();
        missingReceiptAndChangedContext();
        deadlineAndAbortFailure();
        workCaps();
        synchronousWorkScope();
        infrastructureBoundary();
        System.out.println("PASS: A10 generation contracts assertions=" + assertions);
    }

    private static void synchronousWorkScope() {
        Harness service = new Harness(new String[] { "scope" });
        GenerationJob job = new GenerationJob(service, 100000, 40, 5000);
        GenerationWorkScope.check();
        GenerationWorkScope.enter(job);
        try {
            GenerationWorkScope.check();
            boolean nestedRejected = false;
            try { GenerationWorkScope.enter(job); }
            catch (IllegalStateException expected) { nestedRejected = true; }
            check(nestedRejected, "nested synchronous work cannot replace its owner");
            service.current = false;
            boolean staleRejected = false;
            try { GenerationWorkScope.check(); }
            catch (GenerationJob.Stale expected) { staleRejected = true; }
            check(staleRejected, "routing and solver checkpoints detect a stale owner");
        } finally { GenerationWorkScope.exit(job); }
        GenerationWorkScope.check();
        check(job.getOutcome() == GenerationJob.Outcome.STALE, "scope reports terminal stale outcome");
        job.advance();
        check(service.aborts == 1, "terminal work-scope owner receives exact cleanup");
    }

    private static void infrastructureBoundary() {
        Harness paused = new Harness(new String[] { "pause" });
        GenerationJob pausedJob = new GenerationJob(paused, 100000, 40, 5000);
        check(pausedJob.advance(), "unit completes before the pause failure");
        RuntimeException pauseFailure = new IllegalStateException("pause-failed");
        pausedJob.failInfrastructure(pauseFailure);
        check(pausedJob.getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE,
            "pause failure becomes infrastructure failure");
        check(pausedJob.getFailure() == pauseFailure,
            "pause failure remains the infrastructure primary");
        check(paused.aborts == 1, "pause failure aborts exactly once");
        check(!pausedJob.isRunning(), "pause failure cannot leave a busy job");
        pausedJob.failInfrastructure(new IllegalStateException("duplicate-pause-failure"));
        check(paused.aborts == 1, "repeated infrastructure failure does not abort twice");

        Harness ownerService = new Harness(new String[] { "owner" });
        Harness foreignService = new Harness(new String[] { "foreign" });
        GenerationJob owner = new GenerationJob(ownerService, 100000, 40, 5000);
        GenerationJob foreign = new GenerationJob(foreignService, 100000, 40, 5000);
        GenerationWorkScope.enter(owner);
        try {
            boolean rejected = false;
            try { GenerationWorkScope.exit(foreign); }
            catch (IllegalStateException expected) { rejected = true; }
            check(rejected, "foreign scope exit is rejected");
            foreign.failInfrastructure(new IllegalStateException("foreign-scope-failure"));
            check(foreign.getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE,
                "foreign job records its scope failure");
            check(foreignService.aborts == 1, "foreign scope failure aborts its job once");
            GenerationWorkScope.check();
            check(owner.getOutcome() == GenerationJob.Outcome.RUNNING,
                "foreign scope failure does not poison the active owner");
        } finally { GenerationWorkScope.exit(owner); }

        Harness primaryService = new Harness(new String[] { "primary", "alternate" });
        primaryService.failureStage = "resolve";
        GenerationJob primaryJob = new GenerationJob(primaryService, 100000, 40, 5000);
        check(!primaryJob.advance() &&
                primaryJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "prepublish primary failure is terminal");
        Throwable primary = primaryJob.getFailure();
        RuntimeException coordinatorFailure = new IllegalStateException("scope-exit-failed");
        primaryJob.failInfrastructure(coordinatorFailure);
        check(primaryJob.getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE,
            "terminal scope failure upgrades the final outcome");
        check(primaryJob.getFailure() == primary,
            "terminal primary failure remains the failure owner");
        check(containsSuppressed(primary, coordinatorFailure),
            "terminal infrastructure failure is retained as a secondary");
        check(primaryService.aborts == 1, "terminal primary cleanup remains exactly once");

        Harness committedService = new Harness(new String[] { "committed" });
        GenerationJob committed = new GenerationJob(committedService, 100000, 40, 5000);
        runToTerminal(committed);
        GenerationReceipt committedReceipt = committed.getReceipt();
        RuntimeException postCommitFailure = new IllegalStateException("post-commit-failure");
        boolean propagated = false;
        try { committed.failInfrastructure(postCommitFailure); }
        catch (RuntimeException actual) { propagated = actual == postCommitFailure; }
        check(propagated, "post-commit infrastructure failure propagates unchanged");
        check(committed.getOutcome() == GenerationJob.Outcome.PASS &&
                committed.getReceipt() == committedReceipt,
            "post-commit infrastructure failure cannot undo publication");
        check(committedService.aborts == 0, "post-commit failure does not abort a committed job");
    }

    private static void literalStageOrderingAndAtomicPublish() {
        final Harness service = new Harness(new String[] { "manifest/z", "manifest/a" });
        GenerationJob job = new GenerationJob(service, 100000, 40, 5000);
        runToTerminal(job);
        check(job.getOutcome() == GenerationJob.Outcome.PASS, "normal generation publishes");
        check(service.publishedReceipt == job.getReceipt(), "published receipt is job receipt");
        check(service.trace.indexOf("begin:1") >= 0, "canonical first source is index 1");
        assertBefore(service.trace, "begin:1", "resolve", "resolve follows begin");
        assertBefore(service.trace, "resolve", "healthy", "healthy follows resolve");
        assertBefore(service.trace, "healthy", "physical", "physical follows healthy");
        assertBefore(service.trace, "physical", "dependencies:load=1", "dependencies follows physical");
        assertBefore(service.trace, "dependencies:load=1", "prove", "proof follows dependencies");
        assertBefore(service.trace, "prove", "symptom", "symptom follows completed proof");
        assertBefore(service.trace, "symptom", "publish", "publish follows symptom");
        check(service.currentChecksAfterPublish == 0,
            "publication does not demand the intentionally transitioned owner");
        GenerationReceipt receipt = job.getReceipt();
        check(receipt.getStageCount() == 6, "receipt binds every literal stage");
        check(receipt.getWorkCount() == job.getStepCount(), "receipt records consumed work");
        check(receipt.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 1,
            "single hypothesis chunk is recorded");
        check(receipt.canonical().indexOf("manifest/a") >= 0,
            "receipt canonical binds the selected manifest");
        check(receipt.canonical().indexOf("load=1") >= 0,
            "receipt canonical binds the full dependency context");
    }

    private static void cancellationAtEveryStage() {
        for (int completed = 0; completed < 6; completed++) {
            Harness service = new Harness(new String[] { "only" });
            GenerationJob job = new GenerationJob(service, 100000, 40, 5000);
            for (int i = 0; i < completed; i++)
                check(job.advance(), "stage " + completed + " remains live before cancellation");
            job.cancel();
            check(job.getOutcome() == GenerationJob.Outcome.CANCELLED,
                "cancel at stage " + completed + " is terminal");
            check(!job.isRunning(), "cancel at stage " + completed + " stops work");
            check(service.aborts == 1, "cancel at stage " + completed + " aborts once");
            check(!job.advance(), "cancelled callback at stage " + completed + " is inert");
            check(service.aborts == 1, "cancelled callback does not abort twice");
            check(!service.published, "cancelled stage " + completed + " never publishes");
        }
    }

    private static void staleCallback() {
        Harness service = new Harness(new String[] { "only" });
        GenerationJob job = new GenerationJob(service, 100000, 40, 5000);
        check(job.advance(), "first stage accepts the current owner");
        service.current = false;
        check(!job.advance(), "stale callback stops the job");
        check(job.getOutcome() == GenerationJob.Outcome.STALE, "stale callback is classified");
        check(job.getFailure() instanceof GenerationJob.Stale, "stale callback has typed failure");
        check(service.aborts == 1, "stale callback aborts the candidate");
        check(!service.published, "stale callback cannot publish");
    }

    private static void reorderedCandidateCollection() {
        Harness service = new Harness(new String[] { "z", "a" });
        service.rejectSource = 1;
        GenerationJob job = new GenerationJob(service, 100000, 80, 5000);
        runToTerminal(job);
        check(job.getOutcome() == GenerationJob.Outcome.PASS,
            "expected rejection retries the next canonical candidate");
        check(service.begunSources.size() == 2, "both candidate sources were attempted");
        check(service.begunSources.get(0).intValue() == 1 && service.begunSources.get(1).intValue() == 0,
            "manifest order freezes a source-index mapping");
        check(job.getCandidateIndex() == 1, "candidate index is canonical position");
        check(job.getStageWorkCount(GenerationJob.Stage.RESOLVE) == 2,
            "retry work telemetry remains in the bounded job ledger");
        check("z".equals(job.getReceipt().getManifest()), "receipt uses the winning manifest");
    }

    private static void sameIdentityDespiteClocks() {
        Harness cold = new Harness(new String[] { "same" });
        cold.clockStep = 1;
        cold.healthyNullUnits = 1;
        Harness warm = new Harness(new String[] { "same" });
        warm.clockStep = 7;
        warm.healthyNullUnits = 1;
        GenerationJob first = new GenerationJob(cold, 100000, 40, 5000);
        GenerationJob second = new GenerationJob(warm, 100000, 40, 5000);
        runToTerminal(first);
        runToTerminal(second);
        check(first.getOutcome() == GenerationJob.Outcome.PASS &&
                second.getOutcome() == GenerationJob.Outcome.PASS, "clock variants publish");
        check(first.getReceipt().canonical().equals(second.getReceipt().canonical()),
            "clock telemetry is excluded from canonical identity");
        check(first.getReceipt().getElapsedMillis() != second.getReceipt().getElapsedMillis(),
            "clock telemetry remains observable");
        check(first.getReceipt().getStageElapsedMillis(GenerationJob.Stage.RESOLVE) !=
                second.getReceipt().getStageElapsedMillis(GenerationJob.Stage.RESOLVE),
            "per-stage elapsed telemetry remains observable");
        check(first.getStageWorkCount(GenerationJob.Stage.HEALTHY) == 2 &&
                second.getStageWorkCount(GenerationJob.Stage.HEALTHY) == 2,
            "canonical repeat uses exactly two healthy work units");
    }

    private static void healthyPartialUnits() {
        Harness partial = new Harness(new String[] { "partial" });
        partial.healthyNullUnits = 1;
        GenerationJob partialJob = new GenerationJob(partial, 100000, 40, 5000);
        check(partialJob.advance(), "resolve completes before healthy qualification");
        check(partialJob.getStage() == GenerationJob.Stage.HEALTHY,
            "healthy qualification begins at the healthy stage");
        check(partialJob.advance(), "first healthy work unit yields privately");
        check(partialJob.getStage() == GenerationJob.Stage.HEALTHY &&
                partialJob.getStageWorkCount(GenerationJob.Stage.HEALTHY) == 1,
            "partial healthy work keeps the stage and records one unit");
        check(partial.trace.indexOf("physical;") < 0 && partialJob.getReceipt() == null,
            "partial healthy work cannot expose physical or publication receipt");
        check(partialJob.advance(), "second healthy work unit completes qualification");
        check(partialJob.getStage() == GenerationJob.Stage.PHYSICAL &&
                partialJob.getStageWorkCount(GenerationJob.Stage.HEALTHY) == 2,
            "healthy completion advances only after exactly two units");
        check(partial.trace.indexOf("physical;") < 0,
            "physical work does not begin in the healthy completion unit");
        check(partialJob.advance(), "physical work follows healthy completion");
        check(partial.trace.indexOf("physical;") >= 0,
            "physical work begins after healthy qualification completes");

        Harness cancelled = new Harness(new String[] { "cancel-healthy" });
        cancelled.healthyNullUnits = 1;
        GenerationJob cancelledJob = new GenerationJob(cancelled, 100000, 40, 5000);
        check(cancelledJob.advance(), "cancellation fixture reaches healthy stage");
        check(cancelledJob.advance(), "cancellation fixture yields first healthy unit");
        cancelledJob.cancel();
        check(cancelledJob.getOutcome() == GenerationJob.Outcome.CANCELLED &&
                cancelledJob.getStage() == GenerationJob.Stage.HEALTHY,
            "cancellation between healthy units is terminal at healthy stage");
        check(cancelled.aborts == 1 && cancelled.trace.indexOf("physical;") < 0,
            "healthy cancellation aborts its exact owner once");
        cancelledJob.advance();
        check(cancelled.aborts == 1, "cancelled healthy callback does not abort twice");

        Harness deadline = new Harness(new String[] { "deadline-healthy" });
        deadline.healthyNullUnits = 1;
        deadline.times = new long[] { 0, 1, 2, 3, 4, 5, 10 };
        GenerationJob deadlineJob = new GenerationJob(deadline, 10, 40, 5000);
        check(deadlineJob.advance(), "deadline fixture reaches healthy stage");
        check(deadlineJob.advance(), "deadline fixture yields first healthy unit");
        check(!deadlineJob.advance(), "deadline between healthy units stops the job");
        check(deadlineJob.getOutcome() == GenerationJob.Outcome.TIMEOUT &&
                deadlineJob.getStage() == GenerationJob.Stage.HEALTHY,
            "healthy inter-unit deadline retains the healthy stage");
        check(deadline.healthyCalls == 1 && deadline.aborts == 1 &&
                deadline.trace.indexOf("physical;") < 0,
            "healthy inter-unit deadline aborts the exact owner once");

        Harness empty = new Harness(new String[] { "empty-healthy" });
        empty.healthyOutput = "";
        GenerationJob emptyJob = new GenerationJob(empty, 100000, 40, 5000);
        check(emptyJob.advance(), "empty healthy fixture reaches healthy stage");
        check(!emptyJob.advance() &&
                emptyJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "empty healthy output remains a programming failure");
        check(empty.aborts == 1, "empty healthy output aborts its owner once");
    }

    private static void unitClockStartsAfterYield() {
        Harness service = new Harness(new String[] { "yield" });
        GenerationJob job = new GenerationJob(service, 1000, 40, 10);
        check(job.advance(), "first unit completes before the simulated yield");
        service.clock = 100;
        check(job.advance(), "cheap next unit survives a long inactive yield gap");
        check(job.getOutcome() == GenerationJob.Outcome.RUNNING,
            "inactive yield time consumes only the total wall budget");
        check(job.getStage() == GenerationJob.Stage.PHYSICAL,
            "post-yield unit advances the literal stage sequence");
    }

    private static void failedStageTelemetryAndBudgetIdentity() {
        Harness timeout = new Harness(new String[] { "timeout" });
        timeout.times = new long[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 };
        GenerationJob timeoutJob = new GenerationJob(timeout, 100, 40, 4);
        check(timeoutJob.advance(), "failed-stage fixture reaches healthy stage");
        check(timeoutJob.advance(), "failed-stage fixture reaches physical stage");
        check(timeoutJob.advance(), "failed-stage fixture reaches hypotheses stage");
        check(!timeoutJob.advance(), "work-unit deadline stops the hypotheses stage");
        check(timeoutJob.getOutcome() == GenerationJob.Outcome.TIMEOUT,
            "failed hypotheses unit is classified as timeout");
        check(timeoutJob.getStage() == GenerationJob.Stage.HYPOTHESES,
            "failed hypotheses unit retains its stage");
        check(timeoutJob.getStageElapsedMillis(GenerationJob.Stage.HYPOTHESES) > 0,
            "failed hypotheses unit retains elapsed telemetry");
        check(timeoutJob.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 1,
            "failed hypotheses unit retains work telemetry");

        Harness jobBudgetService = new Harness(new String[] { "budget" });
        final GenerationJob jobBudget = new GenerationJob(jobBudgetService, 100000, 40, 5000);
        runToTerminal(jobBudget);
        Harness stepBudgetService = new Harness(new String[] { "budget" });
        GenerationJob stepBudget = new GenerationJob(stepBudgetService, 100000, 40, 6000);
        runToTerminal(stepBudget);
        check(!jobBudget.getReceipt().canonical().equals(stepBudget.getReceipt().canonical()),
            "different work-unit budgets have different receipt identity");
        check(jobBudget.getReceipt().canonical().indexOf("maxJobMillis=100000") >= 0,
            "receipt identity includes the total wall budget");
        check(jobBudget.getReceipt().canonical().indexOf("maxStepMillis=5000") >= 0,
            "receipt identity includes the work-unit budget");

        Harness totalBudgetService = new Harness(new String[] { "budget" });
        final GenerationJob totalBudget = new GenerationJob(totalBudgetService, 100001, 40, 5000);
        runToTerminal(totalBudget);
        check(!jobBudget.getReceipt().canonical().equals(totalBudget.getReceipt().canonical()),
            "different total budgets have different receipt identity");
        expectIllegalState(new Runnable() { public void run() {
            totalBudget.validateReceipt(jobBudget.getReceipt());
        }}, "cross-budget receipt is rejected");
    }

    private static void expectedAndUnexpectedFailure() {
        Harness retry = new Harness(new String[] { "first", "second" });
        retry.rejectSource = 0;
        GenerationJob retryJob = new GenerationJob(retry, 100000, 80, 5000);
        runToTerminal(retryJob);
        check(retryJob.getOutcome() == GenerationJob.Outcome.PASS,
            "typed rejection is retriable");
        check(retry.aborts == 1, "typed rejection aborts before retry");

        Harness allRejected = new Harness(new String[] { "first", "second" });
        allRejected.rejectAll = true;
        GenerationJob rejectedJob = new GenerationJob(allRejected, 100000, 80, 5000);
        runToTerminal(rejectedJob);
        check(rejectedJob.getOutcome() == GenerationJob.Outcome.EXPECTED_REJECTION,
            "all typed rejections are expected rejection");
        check(allRejected.begunSources.size() == 2 && allRejected.aborts == 2,
            "all candidates reject in canonical order with cleanup");

        Harness runtimeFailure = new Harness(new String[] { "first", "second" });
        runtimeFailure.failureStage = "resolve";
        GenerationJob runtimeJob = new GenerationJob(runtimeFailure, 100000, 80, 5000);
        check(!runtimeJob.advance(), "unexpected runtime failure is terminal");
        check(runtimeJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "unexpected runtime exception is not a rejection");
        check(runtimeFailure.begunSources.size() == 1, "unexpected runtime failure never retries");

        Harness errorFailure = new Harness(new String[] { "first", "second" });
        errorFailure.failureStage = "resolve-error";
        GenerationJob errorJob = new GenerationJob(errorFailure, 100000, 80, 5000);
        check(!errorJob.advance() && errorJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "unexpected Error is terminal and non-retriable");
        check(errorFailure.begunSources.size() == 1, "unexpected Error never retries");

        Harness delayedFailure = new Harness(new String[] { "first", "second" });
        delayedFailure.failureStage = "delayed-resolve";
        delayedFailure.delayedFailureMillis = 25;
        GenerationJob delayedJob = new GenerationJob(delayedFailure, 100000, 80, 5000);
        check(!delayedJob.advance(), "delayed unexpected failure is terminal");
        check(delayedJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "delayed unexpected failure keeps programming outcome");
        check(delayedJob.getStageElapsedMillis(GenerationJob.Stage.RESOLVE) == 25,
            "delayed unexpected failure samples stage elapsed time");
        check(delayedJob.getElapsedMillis() == 25,
            "delayed unexpected failure samples total elapsed time");
        check(delayedFailure.aborts == 1, "delayed unexpected failure still cleans up once");
    }

    private static void missingReceiptAndChangedContext() {
        Harness missing = new Harness(new String[] { "only" });
        final GenerationJob missingJob = new GenerationJob(missing, 100000, 40, 5000);
        expectIllegalState(new Runnable() { public void run() {
            missingJob.validateReceipt(null);
        }}, "missing receipt fails closed");

        Harness changed = new Harness(new String[] { "only" });
        changed.changeDependenciesAfterFirstRead = true;
        GenerationJob changedJob = new GenerationJob(changed, 100000, 40, 5000);
        runToTerminal(changedJob);
        check(changedJob.getOutcome() == GenerationJob.Outcome.STALE,
            "changed load context invalidates publication");
        check(!changed.published, "changed load context cannot publish");
    }

    private static void deadlineAndAbortFailure() {
        Harness deadline = new Harness(new String[] { "first", "second" });
        deadline.times = new long[] { 0, 1, 4, 4, 4, 4, 4, 4, 4 };
        GenerationJob deadlineJob = new GenerationJob(deadline, 3, 40, 5000);
        check(!deadlineJob.advance(), "deadline stops an in-flight stage");
        check(deadlineJob.getOutcome() == GenerationJob.Outcome.TIMEOUT,
            "wall deadline has timeout outcome");
        check(deadline.begunSources.size() == 1, "deadline never selects an alternate candidate");

        Harness abortFailure = new Harness(new String[] { "only" });
        abortFailure.failureStage = "resolve";
        abortFailure.abortFailure = new IllegalStateException("abort-failed");
        GenerationJob abortJob = new GenerationJob(abortFailure, 100000, 40, 5000);
        check(!abortJob.advance(), "abort failure is terminal");
        check(abortJob.getOutcome() == GenerationJob.Outcome.INFRASTRUCTURE_FAILURE,
            "abort failure is infrastructure failure");
        check(abortJob.getFailure() == abortFailure.abortFailure,
            "abort failure remains the primary failure");
        check(abortFailure.abortFailure.getSuppressed().length == 1,
            "original failure is suppressed on abort failure");
    }

    private static void workCaps() {
        Harness service = new Harness(new String[] { "only" });
        GenerationJob job = new GenerationJob(service, 100000, 5, 5000);
        for (int i = 0; i < 5; i++)
            check(job.advance(), "five bounded stages execute before the cap");
        check(!job.advance(), "sixth stage is blocked by the work cap");
        check(job.getOutcome() == GenerationJob.Outcome.WORK_EXHAUSTED,
            "work cap has explicit outcome");
        check(job.getStepCount() == 5, "work cap does not overrun its step count");
        check(!service.published, "work cap cannot publish a partial lineage");
    }

    private static void runToTerminal(GenerationJob job) {
        int guard = 200;
        while (job.isRunning() && guard-- > 0)
            job.advance();
        check(guard > 0, "generation reaches a terminal state within the bounded test loop");
    }

    private static void assertBefore(StringBuilder trace, String first, String second, String label) {
        int left = trace.indexOf(first);
        int right = trace.indexOf(second);
        check(left >= 0 && right > left, label);
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new AssertionError(label);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition)
            throw new AssertionError(label);
    }

    private static boolean containsSuppressed(Throwable owner, Throwable expected) {
        if (owner == null || expected == null)
            return false;
        Throwable[] suppressed = owner.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            if (suppressed[i] == expected)
                return true;
        }
        return false;
    }

    private static final class Harness implements GenerationJob.Services {
        final String[] manifests;
        final StringBuilder trace = new StringBuilder();
        final java.util.ArrayList<Integer> begunSources = new java.util.ArrayList<Integer>();
        String dependency = "load=1";
        boolean current = true;
        boolean changeDependenciesAfterFirstRead;
        boolean dependenciesChanged;
        boolean publishMakesStale = true;
        boolean published;
        GenerationReceipt publishedReceipt;
        int currentChecksAfterPublish;
        int aborts;
        int rejectSource = Integer.MIN_VALUE;
        boolean rejectAll;
        String failureStage;
        long delayedFailureMillis;
        int healthyNullUnits;
        int healthyCalls;
        String healthyOutput;
        Throwable abortFailure;
        long clock;
        long clockStep;
        long[] times;
        int timeIndex;

        Harness(String[] manifests) {
            this.manifests = manifests;
        }

        public int candidateCount() { return manifests.length; }

        public String manifest(int candidate) { return manifests[candidate]; }

        public void beginCandidate(int index) {
            begunSources.add(Integer.valueOf(index));
            trace.append("begin:").append(index).append(';');
        }

        public String resolve() {
            trace.append("resolve;");
            int source = begunSources.get(begunSources.size() - 1).intValue();
            if (rejectAll || rejectSource == source)
                throw new GenerationJob.Rejected("typed candidate rejection");
            if (failureStage != null && failureStage.equals("delayed-resolve")) {
                clock = delayedFailureMillis;
                throw new IllegalStateException("delayed generation failure");
            }
            if (failureStage == null || !failureStage.equals("resolve")) {
                if (failureStage != null && failureStage.equals("resolve-error"))
                    throw new AssertionError("unexpected generation error");
                return "resolve=ok";
            }
            throw new IllegalStateException("unexpected generation failure");
        }

        public String healthy() {
            trace.append("healthy;");
            healthyCalls++;
            if (healthyCalls <= healthyNullUnits)
                return null;
            return healthyOutput == null ? "healthy=ok" : healthyOutput;
        }

        public String physical() {
            trace.append("physical;");
            return "physical=ok";
        }

        public boolean proveNext() {
            trace.append("prove;");
            return false;
        }

        public String symptom() {
            trace.append("symptom;");
            return "symptom=ok";
        }

        public String dependencies() {
            String value = dependenciesChanged ? "load=2" : dependency;
            trace.append("dependencies:").append(value).append(';');
            if (changeDependenciesAfterFirstRead) {
                if (dependenciesChanged)
                    return value;
                dependenciesChanged = true;
            }
            return value;
        }

        public void publish(GenerationReceipt receipt) {
            trace.append("publish;");
            published = true;
            publishedReceipt = receipt;
            if (publishMakesStale) {
                current = false;
                publishMakesStale = false;
            }
        }

        public void abort() {
            trace.append("abort;");
            aborts++;
            if (abortFailure != null)
                throw (RuntimeException)abortFailure;
        }

        public boolean isCurrent() {
            if (published)
                currentChecksAfterPublish++;
            return current;
        }

        public long nowMillis() {
            if (times != null) {
                int index = timeIndex < times.length ? timeIndex : times.length - 1;
                timeIndex++;
                return times[index];
            }
            long value = clock;
            clock += clockStep;
            return value;
        }
    }
}
