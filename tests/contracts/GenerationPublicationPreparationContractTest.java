package com.lushprojects.circuitjs1.client;

/** Pure/native contracts for bounded generation publication preparation. */
public final class GenerationPublicationPreparationContractTest {
    private static int assertions;

    private GenerationPublicationPreparationContractTest() { }

    public static void main(String[] args) {
        ordinaryServicesKeepOneAtomicPublishUnit();
        preparationYieldsBeforeOneTerminalPublication();
        dependencyTamperingFailsClosed();
        preparationFailuresAbortOnce();
        cancellationRestoresAndReleasesTheFakeOwner();
        preparationDeadlinesUseBothExistingCaps();
        productionCapsRemainUnchanged();
        additionalMandatoryWorkIsReservedExactly();
        minimumReservationAdmitsOnlyTheActualProofThatFits();
        proofWorkCannotExceedItsDeclaredMaximum();
        invalidProofWorkRangesFailBeforeProof();
        rejectionAfterPreparationResetsTheNextCandidate();
        proofReservationResetsForTheNextCandidate();
        foreignReceiptsAndRepeatedAdvanceCannotRepublish();
        System.out.println("PASS: Generation publication preparation contracts assertions=" +
            assertions);
    }

    private static void ordinaryServicesKeepOneAtomicPublishUnit() {
        FakeServices service = new FakeServices(new String[] { "ordinary" });
        service.publishAdvanceMillis = 13;
        GenerationJob job = new GenerationJob(service, 1000, 40, 20);

        for (int i = 0; i < 5; i++)
            check(job.advance(), "ordinary service completes each of its five prepublication stages");
        check(job.getStage() == GenerationJob.Stage.PUBLISH && service.publishCalls == 0 &&
                job.getReceipt() == null,
            "ordinary service reaches publication without publishing early");
        check(!job.advance() && job.getOutcome() == GenerationJob.Outcome.PASS,
            "ordinary service retains its single final publication unit");

        GenerationReceipt receipt = job.getReceipt();
        check(service.publishCalls == 1 && service.publishedReceipt == receipt,
            "ordinary service publishes exactly the job-issued receipt once");
        check(job.getStepCount() == 6 && receipt.getWorkCount() == 6 &&
                job.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 1 &&
                receipt.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 1,
            "ordinary service work accounting remains six total units and one publish unit");
        check(job.getStageWorkCount(GenerationJob.Stage.RESOLVE) == 1 &&
                job.getStageWorkCount(GenerationJob.Stage.HEALTHY) == 1 &&
                job.getStageWorkCount(GenerationJob.Stage.PHYSICAL) == 1 &&
                job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 1 &&
                job.getStageWorkCount(GenerationJob.Stage.SYMPTOM) == 1,
            "ordinary stage ledger remains one unit per original prepublication stage");
        check(receipt.getStageElapsedMillis(GenerationJob.Stage.PUBLISH) == 0 &&
                job.getStageElapsedMillis(GenerationJob.Stage.PUBLISH) == 13,
            "ordinary receipt keeps its prepublication snapshot while job timing includes commit");
        check(service.currentChecksAfterPublish == 0 && service.abortCalls == 0,
            "successful atomic publication does not recheck or abort the transitioned owner");

        check(!job.advance() && service.publishCalls == 1 && service.abortCalls == 0,
            "repeated terminal callback is inert for an ordinary service");
    }

    private static void preparationYieldsBeforeOneTerminalPublication() {
        PreparedServices service = new PreparedServices(new String[] { "prepared" }, 3);
        service.prepareAdvanceMillis = 2;
        service.publishAdvanceMillis = 7;
        GenerationJob job = new GenerationJob(service, 1000, 40, 20);
        advanceToPublish(job, service.proofUnits);

        for (int i = 1; i <= 3; i++) {
            check(job.advance(), "preparation unit " + i + " yields to a later PUBLISH turn");
            check(job.getStage() == GenerationJob.Stage.PUBLISH &&
                    service.publishCalls == 0 && job.getReceipt() == null,
                "preparation unit " + i + " cannot publish or issue a receipt");
        }
        check(service.candidatePreparationCalls == 3 &&
                service.candidateRestorationUnits == 1 &&
                BASE_DEPENDENCIES.equals(service.dependency) &&
                !service.temporaryContextActive,
            "last restoration unit yields after restoring the captured context");

        check(!job.advance() && job.getOutcome() == GenerationJob.Outcome.PASS,
            "separate ready call performs the final atomic publication");
        GenerationReceipt receipt = job.getReceipt();
        check(service.candidatePreparationCalls == 4 && service.guardCalls == 1 &&
                service.lastGuardSawRestoredContext,
            "publication guard runs once after all preparation calls see restored context");
        check(service.publishCalls == 1 && service.publishedReceipt == receipt &&
                job.getStepCount() == 9 && receipt.getWorkCount() == 9 &&
                job.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 4 &&
                receipt.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 4,
            "three yielded preparation units plus the commit are all included in receipt work");
        check(receipt.getStageElapsedMillis(GenerationJob.Stage.PUBLISH) == 6 &&
                job.getStageElapsedMillis(GenerationJob.Stage.PUBLISH) == 13,
            "preparation receipt snapshots prior work while final commit time remains job telemetry");
        check(service.currentChecksAfterPublish == 0 && service.abortCalls == 0,
            "completed preparation crosses one atomic owner boundary without stale rechecks");
    }

    private static void dependencyTamperingFailsClosed() {
        PreparedServices beforePreparation = new PreparedServices(
            new String[] { "tamper-before-preparation" }, 2);
        GenerationJob beforeJob = new GenerationJob(beforePreparation, 1000, 40, 20);
        advanceToPublish(beforeJob, beforePreparation.proofUnits);
        beforePreparation.tamperDependency("external-change-before-preparation");
        runToTerminal(beforeJob);
        check(beforeJob.getOutcome() == GenerationJob.Outcome.STALE &&
                beforePreparation.guardCalls == 1 && !beforePreparation.lastGuardSawRestoredContext,
            "dependency tampering before preparation is rejected at the publication guard");
        check(!beforePreparation.published && beforePreparation.abortCalls == 1 &&
                "external-change-before-preparation".equals(beforePreparation.dependency),
            "preparation cannot publish or overwrite the externally changed context");

        PreparedServices afterLastYield = new PreparedServices(
            new String[] { "tamper-after-last-yield" }, 2);
        GenerationJob afterJob = new GenerationJob(afterLastYield, 1000, 40, 20);
        advanceToPublish(afterJob, afterLastYield.proofUnits);
        check(afterJob.advance(), "first preparation unit yields before final restoration");
        check(afterJob.advance() && afterLastYield.candidateRestorationUnits == 1 &&
                BASE_DEPENDENCIES.equals(afterLastYield.dependency),
            "final restoration unit yields before the distinct commit callback");
        afterLastYield.tamperDependency("external-change-after-last-yield");
        check(!afterJob.advance() && afterJob.getOutcome() == GenerationJob.Outcome.STALE,
            "dependency tampering after the final yield is rejected before publication");
        check(afterLastYield.guardCalls == 1 && !afterLastYield.lastGuardSawRestoredContext &&
                !afterLastYield.published && afterLastYield.abortCalls == 1 &&
                "external-change-after-last-yield".equals(afterLastYield.dependency),
            "failed final guard preserves the outside change and aborts once");
    }

    private static void preparationFailuresAbortOnce() {
        PreparedServices stale = new PreparedServices(new String[] { "stale-preparation" }, 2);
        stale.throwStaleAtCandidateCall = 1;
        GenerationJob staleJob = new GenerationJob(stale, 1000, 40, 20);
        advanceToPublish(staleJob, stale.proofUnits);
        check(!staleJob.advance() && staleJob.getOutcome() == GenerationJob.Outcome.STALE,
            "typed preparation failure retains its stale classification");
        check(stale.abortCalls == 1 && stale.releases == 1 && !stale.ownerHeld &&
                BASE_DEPENDENCIES.equals(stale.dependency) && !stale.published &&
                staleJob.getReceipt() == null,
            "typed preparation failure restores temporary context and aborts its owner once");

        PreparedServices thrown = new PreparedServices(new String[] { "throwing-preparation" }, 2);
        thrown.throwRuntimeAtCandidateCall = 1;
        GenerationJob thrownJob = new GenerationJob(thrown, 1000, 40, 20);
        advanceToPublish(thrownJob, thrown.proofUnits);
        check(!thrownJob.advance() &&
                thrownJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE,
            "unexpected preparation exception is terminal programming failure");
        check(thrown.abortCalls == 1 && thrown.releases == 1 && !thrown.ownerHeld &&
                BASE_DEPENDENCIES.equals(thrown.dependency) && !thrown.published &&
                thrownJob.getReceipt() == null,
            "thrown preparation failure restores temporary context and aborts its owner once");
        check(!thrownJob.advance() && thrown.abortCalls == 1 && thrown.releases == 1,
            "callback after preparation failure cannot repeat cleanup");
    }

    private static void cancellationRestoresAndReleasesTheFakeOwner() {
        PreparedServices service = new PreparedServices(new String[] { "cancel-preparation" }, 3);
        GenerationJob job = new GenerationJob(service, 1000, 40, 20);
        advanceToPublish(job, service.proofUnits);
        check(job.advance() && service.temporaryContextActive && service.ownerHeld,
            "first preparation unit leaves a bounded temporary context under the held owner");

        job.cancel();
        check(job.getOutcome() == GenerationJob.Outcome.CANCELLED && !job.isRunning(),
            "cancellation during preparation is terminal");
        check(BASE_DEPENDENCIES.equals(service.dependency) &&
                !service.temporaryContextActive && service.abortRestorations == 1 &&
                !service.ownerHeld && service.releases == 1,
            "cancellation restores the fake context and releases the owner exactly once");
        check(service.abortCalls == 1 && !service.published && job.getReceipt() == null,
            "cancelled preparation aborts without issuing or publishing a receipt");
        check(!job.advance() && service.candidatePreparationCalls == 1 &&
                service.abortCalls == 1 && service.releases == 1 && !service.published,
            "late callback after cancellation cannot resume preparation or cleanup");
    }

    private static void preparationDeadlinesUseBothExistingCaps() {
        PreparedServices unitBounded = new PreparedServices(
            new String[] { "unit-deadline" }, 2);
        unitBounded.prepareAdvanceMillis = 5;
        GenerationJob unitJob = new GenerationJob(unitBounded, 1000, 40, 5);
        advanceToPublish(unitJob, unitBounded.proofUnits);
        check(!unitJob.advance() && unitJob.getOutcome() == GenerationJob.Outcome.TIMEOUT,
            "publication preparation obeys the existing per-unit wall deadline");
        check(unitJob.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 1 &&
                unitBounded.abortCalls == 1 && unitBounded.releases == 1 &&
                BASE_DEPENDENCIES.equals(unitBounded.dependency) && !unitBounded.published,
            "unit deadline accounts one failed preparation unit and restores its owner");

        PreparedServices jobBounded = new PreparedServices(
            new String[] { "job-deadline" }, 4);
        jobBounded.prepareAdvanceMillis = 4;
        GenerationJob jobDeadline = new GenerationJob(jobBounded, 10, 40, 20);
        advanceToPublish(jobDeadline, jobBounded.proofUnits);
        runToTerminal(jobDeadline);
        check(jobDeadline.getOutcome() == GenerationJob.Outcome.TIMEOUT &&
                jobDeadline.getStage() == GenerationJob.Stage.PUBLISH &&
                jobBounded.candidatePreparationCalls == 3,
            "preparation yields remain inside the unchanged cumulative job deadline");
        check(jobDeadline.getStageWorkCount(GenerationJob.Stage.PUBLISH) == 3 &&
                jobDeadline.getElapsedMillis() == 12 && jobBounded.abortCalls == 1 &&
                jobBounded.releases == 1 && BASE_DEPENDENCIES.equals(jobBounded.dependency) &&
                !jobBounded.published,
            "cumulative timeout retains attempted work and cleans up without publishing");
    }

    private static void additionalMandatoryWorkIsReservedExactly() {
        FakeServices oldOverload = new FakeServices(new String[] { "one-argument-preflight" });
        oldOverload.proofUnits = 2;
        oldOverload.preflightProofUnits = 2;
        GenerationJob oldJob = new GenerationJob(oldOverload, 1000, 7, 20);
        oldOverload.job = oldJob;
        runToTerminal(oldJob);
        check(oldJob.getOutcome() == GenerationJob.Outcome.PASS && oldJob.getStepCount() == 7 &&
                oldOverload.proofCalls == 2 && oldOverload.publishCalls == 1,
            "one-argument preflight still reserves proof plus its original two mandatory units");

        PreparedServices exact = new PreparedServices(new String[] { "exact-extra-reservation" }, 3);
        exact.proofUnits = 2;
        exact.preflightProofUnits = 2;
        exact.preflightAdditionalUnits = 3;
        exact.useAdditionalPreflight = true;
        GenerationJob exactJob = new GenerationJob(exact, 1000, 10, 20);
        exact.job = exactJob;
        runToTerminal(exactJob);
        check(exactJob.getOutcome() == GenerationJob.Outcome.PASS && exactJob.getStepCount() == 10 &&
                exact.proofCalls == 2 && exact.candidatePreparationCalls == 4 &&
                exact.publishCalls == 1,
            "two-argument preflight admits the exact proof, preparation, and commit reserve");

        PreparedServices shortBudget = new PreparedServices(
            new String[] { "short-extra-reservation" }, 3);
        shortBudget.proofUnits = 2;
        shortBudget.preflightProofUnits = 2;
        shortBudget.preflightAdditionalUnits = 3;
        shortBudget.useAdditionalPreflight = true;
        GenerationJob shortJob = new GenerationJob(shortBudget, 1000, 9, 20);
        shortBudget.job = shortJob;
        runToTerminal(shortJob);
        check(shortJob.getOutcome() == GenerationJob.Outcome.WORK_EXHAUSTED &&
                shortJob.getStage() == GenerationJob.Stage.PHYSICAL && shortJob.getStepCount() == 3,
            "one unit below the additional reserve is rejected before diagnostic proof");
        check(shortBudget.proofCalls == 0 && shortBudget.candidatePreparationCalls == 0 &&
                shortBudget.publishCalls == 0 && shortBudget.abortCalls == 1,
            "under-reserved work never enters proof or publication and aborts once");

        PreparedServices negative = new PreparedServices(new String[] { "negative-extra" }, 1);
        negative.preflightProofUnits = 1;
        negative.preflightAdditionalUnits = -1;
        negative.useAdditionalPreflight = true;
        GenerationJob negativeJob = new GenerationJob(negative, 1000, 20, 20);
        negative.job = negativeJob;
        runToTerminal(negativeJob);
        check(negativeJob.getOutcome() == GenerationJob.Outcome.PROGRAMMING_FAILURE &&
                negativeJob.getStage() == GenerationJob.Stage.PHYSICAL &&
                negative.proofCalls == 0 && negative.publishCalls == 0 &&
                negative.abortCalls == 1,
            "negative additional mandatory work is rejected before proof and publication");

        PreparedServices large = new PreparedServices(new String[] { "long-reservation" }, 1);
        large.preflightProofUnits = Integer.MAX_VALUE;
        large.preflightAdditionalUnits = Integer.MAX_VALUE;
        large.useAdditionalPreflight = true;
        GenerationJob largeJob = new GenerationJob(large, 1000, Integer.MAX_VALUE, 20);
        large.job = largeJob;
        runToTerminal(largeJob);
        check(largeJob.getOutcome() == GenerationJob.Outcome.WORK_EXHAUSTED &&
                largeJob.getStage() == GenerationJob.Stage.PHYSICAL && large.proofCalls == 0 &&
                large.publishCalls == 0 && large.abortCalls == 1,
            "large reserve arithmetic cannot wrap and admit an impossible proof plan");
    }

    private static void minimumReservationAdmitsOnlyTheActualProofThatFits() {
        PreparedServices minimum = new PreparedServices(new String[] { "minimum-proof-fits" }, 3);
        minimum.proofUnits = 2;
        minimum.useProofRangePreflight = true;
        minimum.preflightMinimumProofUnits = 2;
        minimum.preflightMaximumProofUnits = 7;
        minimum.preflightAdditionalUnits = 3;
        GenerationJob minimumJob = new GenerationJob(minimum, 1000, 10, 20);
        minimum.job = minimumJob;
        runToTerminal(minimumJob);
        check(7 + 2 + 3 > 10 - 3 && minimumJob.getOutcome() == GenerationJob.Outcome.PASS &&
                minimumJob.getStepCount() == 10 && minimumJob.getReceipt() != null,
            "minimum proof reserve admits a passing two-unit proof although its declared maximum cannot fit");
        check(minimum.proofCalls == 2 && minimumJob.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 2 &&
                minimum.candidatePreparationCalls == 4 && minimum.publishCalls == 1 &&
                minimum.symptomCalls == 1 && minimum.abortCalls == 0,
            "actual minimum proof completes every mandatory tail unit exactly once");

        PreparedServices longerExact = new PreparedServices(new String[] { "longer-proof-exact-fit" }, 3);
        longerExact.proofUnits = 3;
        longerExact.useProofRangePreflight = true;
        longerExact.preflightMinimumProofUnits = 2;
        longerExact.preflightMaximumProofUnits = 7;
        longerExact.preflightAdditionalUnits = 3;
        GenerationJob longerJob = new GenerationJob(longerExact, 1000, 11, 20);
        longerExact.job = longerJob;
        runToTerminal(longerJob);
        check(longerJob.getOutcome() == GenerationJob.Outcome.PASS && longerJob.getStepCount() == 11 &&
                longerExact.proofCalls == 3 && longerExact.publishCalls == 1,
            "a longer actual proof succeeds when its complete mandatory tail exactly fits");

        PreparedServices shortBudget = new PreparedServices(new String[] { "minimum-proof-short-tail" }, 3);
        shortBudget.proofUnits = 3;
        shortBudget.useProofRangePreflight = true;
        shortBudget.preflightMinimumProofUnits = 2;
        shortBudget.preflightMaximumProofUnits = 7;
        shortBudget.preflightAdditionalUnits = 3;
        GenerationJob shortJob = new GenerationJob(shortBudget, 1000, 10, 20);
        shortBudget.job = shortJob;
        runToTerminal(shortJob);
        check(shortJob.getOutcome() == GenerationJob.Outcome.WORK_EXHAUSTED &&
                shortJob.getStage() == GenerationJob.Stage.HYPOTHESES && shortJob.getStepCount() == 5 &&
                shortJob.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 2,
            "minimum reserve admits proof start but blocks a third unit before it consumes the mandatory tail");
        check(shortBudget.proofCalls == 2 && shortBudget.symptomCalls == 0 &&
                shortBudget.candidatePreparationCalls == 0 && shortBudget.publishCalls == 0 &&
                shortBudget.abortCalls == 1 && shortJob.getReceipt() == null,
            "under-budget actual proof cannot enter symptom or publication and aborts once");
    }

    private static void proofWorkCannotExceedItsDeclaredMaximum() {
        PreparedServices overMaximum = new PreparedServices(new String[] { "proof-over-maximum" }, 1);
        overMaximum.proofUnits = 3;
        overMaximum.useProofRangePreflight = true;
        overMaximum.preflightMinimumProofUnits = 2;
        overMaximum.preflightMaximumProofUnits = 2;
        overMaximum.preflightAdditionalUnits = 0;
        GenerationJob job = new GenerationJob(overMaximum, 1000, 18, 20);
        overMaximum.job = job;
        runToTerminal(job);
        check(job.getOutcome() == GenerationJob.Outcome.WORK_EXHAUSTED &&
                job.getStage() == GenerationJob.Stage.HYPOTHESES && job.getStepCount() == 5 &&
                job.getStageWorkCount(GenerationJob.Stage.HYPOTHESES) == 2,
            "declared proof maximum rejects an extra actual unit before increment despite spare total budget");
        check(job.getRemainingStepCount() > 0 && overMaximum.proofCalls == 2 &&
                overMaximum.symptomCalls == 0 && overMaximum.candidatePreparationCalls == 0 &&
                overMaximum.publishCalls == 0 && overMaximum.abortCalls == 1,
            "proof maximum violation is not mistaken for total-budget exhaustion or allowed to publish");
    }

    private static void invalidProofWorkRangesFailBeforeProof() {
        assertInvalidProofRange("zero-minimum", 0, 7, 0, 20,
            GenerationJob.Outcome.PROGRAMMING_FAILURE);
        assertInvalidProofRange("maximum-below-minimum", 3, 2, 0, 20,
            GenerationJob.Outcome.PROGRAMMING_FAILURE);
        assertInvalidProofRange("negative-tail", 1, 7, -1, 20,
            GenerationJob.Outcome.PROGRAMMING_FAILURE);
        assertInvalidProofRange("overflowing-reserve", 1, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, GenerationJob.Outcome.WORK_EXHAUSTED);
        assertInvalidProofRange("minimum-exceeds-remaining", 4, 7, 0, 6,
            GenerationJob.Outcome.WORK_EXHAUSTED);
    }

    private static void assertInvalidProofRange(String name, int minimum, int maximum,
            int additional, int maxSteps, GenerationJob.Outcome expected) {
        PreparedServices service = new PreparedServices(new String[] { name }, 1);
        service.useProofRangePreflight = true;
        service.preflightMinimumProofUnits = minimum;
        service.preflightMaximumProofUnits = maximum;
        service.preflightAdditionalUnits = additional;
        GenerationJob job = new GenerationJob(service, 1000, maxSteps, 20);
        service.job = job;
        runToTerminal(job);
        check(job.getOutcome() == expected && job.getStage() == GenerationJob.Stage.PHYSICAL &&
                job.getStepCount() == 3,
            name + " range is rejected at PHYSICAL before consuming proof work");
        check(service.proofCalls == 0 && service.symptomCalls == 0 &&
                service.candidatePreparationCalls == 0 && service.publishCalls == 0 &&
                service.abortCalls == 1 && job.getReceipt() == null,
            name + " range cannot enter proof, symptom, or publication and aborts once");
    }

    private static void proofReservationResetsForTheNextCandidate() {
        RetryAfterRangePreflightServices service = new RetryAfterRangePreflightServices(
            new String[] { "a-range-retry", "z-range-retry" }, 1, 0);
        GenerationJob job = new GenerationJob(service, 1000, 30, 20);
        service.job = job;
        runToTerminal(job);
        java.util.List<GenerationJob.Attempt> attempts = job.getAttempts();
        check(job.getOutcome() == GenerationJob.Outcome.PASS && job.getCandidateIndex() == 1 &&
                attempts.size() == 2 && attempts.get(0).outcome ==
                    GenerationJob.Outcome.EXPECTED_REJECTION && attempts.get(1).outcome ==
                    GenerationJob.Outcome.PASS,
            "range-reserved proof can retry after a publication rejection on its first candidate");
        check(attempts.get(0).proofUnits == 1 && attempts.get(1).proofUnits == 2 &&
                service.firstDeclaredMaximum == 1 && service.secondDeclaredMaximum == 2 &&
                service.proofCalls == 3,
            "retry clears the first candidate's completed proof and maximum before adopting the next range");
        check(service.beginCalls == 2 && service.abortCalls == 1 && service.publishCalls == 1 &&
                job.getReceipt() != null && job.getStepCount() == 15,
            "range retry retains one cleanup, one final publication, and shared work accounting");
    }

    private static void productionCapsRemainUnchanged() {
        check(GenerationCoordinator.MAX_JOB_MILLIS == 90000L &&
                GenerationCoordinator.MAX_JOB_STEPS == 640 &&
                GenerationCoordinator.MAX_STEP_MILLIS == 5000L,
            "production generation caps remain 90 seconds, 640 work units, and 5 seconds per unit");
    }

    private static void rejectionAfterPreparationResetsTheNextCandidate() {
        RetryAfterPreparationServices service = new RetryAfterPreparationServices(
            new String[] { "z-manifest", "a-manifest" }, 2, 1);
        GenerationJob job = new GenerationJob(service, 1000, 40, 20);
        runToTerminal(job);

        check(job.getOutcome() == GenerationJob.Outcome.PASS && job.getCandidateIndex() == 1,
            "a typed rejection after full preparation retries the next canonical candidate");
        java.util.List<GenerationJob.Attempt> attempts = job.getAttempts();
        check(attempts.size() == 2 && attempts.get(0).outcome ==
                GenerationJob.Outcome.EXPECTED_REJECTION &&
                attempts.get(0).stage == GenerationJob.Stage.PUBLISH &&
                attempts.get(1).outcome == GenerationJob.Outcome.PASS,
            "preparation rejection retains its publication-stage attempt boundary");
        check(service.totalPreparationCalls == 6 && service.candidatePreparationCalls == 3 &&
                service.guardCalls == 2 && service.lastGuardSawRestoredContext,
            "rejected candidate cleanup resets preparation completion for its successor");
        check(service.beginCalls == 2 && service.abortCalls == 1 &&
                service.publishCalls == 1 && job.getStepCount() == 16,
            "retry preserves one cleanup, one atomic publish, and shared total work accounting");
        check(attempts.get(0).workUnits == 8 && attempts.get(1).workUnits == 8,
            "both candidate attempts include their own bounded preparation and commit units");
    }

    private static void foreignReceiptsAndRepeatedAdvanceCannotRepublish() {
        PreparedServices issuer = new PreparedServices(new String[] { "same-manifest" }, 2);
        GenerationJob issuingJob = new GenerationJob(issuer, 1000, 40, 20);
        runToTerminal(issuingJob);
        check(issuingJob.getOutcome() == GenerationJob.Outcome.PASS &&
                issuingJob.getReceipt() != null && issuer.publishCalls == 1,
            "first job creates one complete preparation-bound receipt");

        PreparedServices foreign = new PreparedServices(new String[] { "same-manifest" }, 2);
        GenerationJob foreignJob = new GenerationJob(foreign, 1000, 40, 20);
        boolean rejected = false;
        try { foreignJob.validateReceipt(issuingJob.getReceipt()); }
        catch (GenerationJob.Stale expected) { rejected = true; }
        check(rejected && foreignJob.getOutcome() == GenerationJob.Outcome.RUNNING &&
                foreign.publishCalls == 0,
            "equal-context foreign issuer receipt cannot enter another live job");
        runToTerminal(foreignJob);
        check(issuingJob.getReceipt().canonical().equals(foreignJob.getReceipt().canonical()),
            "independent jobs may issue equal canonical content with distinct authority");

        rejected = false;
        try { foreignJob.validateReceipt(issuingJob.getReceipt()); }
        catch (GenerationJob.Stale expected) { rejected = true; }
        check(rejected && foreign.publishCalls == 1,
            "foreign receipt remains rejected after the receiving job has passed");
        foreignJob.validateReceipt(foreignJob.getReceipt());

        int preparationCalls = foreign.totalPreparationCalls;
        check(!foreignJob.advance() && foreignJob.getOutcome() == GenerationJob.Outcome.PASS &&
                foreign.publishCalls == 1 && foreign.totalPreparationCalls == preparationCalls &&
                foreign.abortCalls == 0,
            "repeated terminal advance cannot prepare, publish, or abort again");
    }

    private static void advanceToPublish(GenerationJob job, int proofUnits) {
        int unitsBeforePublish = 3 + proofUnits + 1;
        for (int i = 0; i < unitsBeforePublish; i++)
            check(job.advance(), "prepublication stage unit " + (i + 1) + " yields normally");
        check(job.getStage() == GenerationJob.Stage.PUBLISH,
            "fixture reaches PUBLISH before exercising publication preparation");
    }

    private static void runToTerminal(GenerationJob job) {
        int remaining = 200;
        while (job.isRunning() && remaining-- > 0)
            job.advance();
        check(!job.isRunning() && remaining > 0,
            "generation job reaches a terminal result within the bounded fixture loop");
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition)
            throw new AssertionError("Generation publication preparation: " + label);
    }

    private static final String BASE_DEPENDENCIES = "graph=physical-snapshot";

    private static class FakeServices implements GenerationJob.Services {
        final String[] manifests;
        int beginCalls;
        int currentSource = -1;
        int resolveCalls;
        int healthyCalls;
        int physicalCalls;
        int symptomCalls;
        int proofUnits = 1;
        int proofCalls;
        int preflightProofUnits = -1;
        int preflightMinimumProofUnits = -1;
        int preflightMaximumProofUnits = -1;
        int preflightAdditionalUnits;
        boolean useProofRangePreflight;
        boolean useAdditionalPreflight;
        String dependency = BASE_DEPENDENCIES;
        boolean current = true;
        boolean ownerHeld;
        int releases;
        int abortCalls;
        int publishCalls;
        int currentChecksAfterPublish;
        boolean published;
        GenerationReceipt publishedReceipt;
        long clock;
        long publishAdvanceMillis;
        GenerationJob job;

        FakeServices(String[] manifests) {
            this.manifests = manifests;
        }

        public int candidateCount() { return manifests.length; }

        public String manifest(int candidate) { return manifests[candidate]; }

        public void beginCandidate(int index) {
            beginCalls++;
            currentSource = index;
            current = true;
            ownerHeld = true;
        }

        public String resolve() {
            resolveCalls++;
            return "resolve-contract";
        }

        public String healthy() {
            healthyCalls++;
            return "healthy-contract";
        }

        public String physical() {
            physicalCalls++;
            if (useProofRangePreflight) {
                job.preflightProofWork(preflightMinimumProofUnits,
                    preflightMaximumProofUnits, preflightAdditionalUnits);
            } else if (preflightProofUnits >= 0) {
                if (useAdditionalPreflight)
                    job.preflightProofWork(preflightProofUnits, preflightAdditionalUnits);
                else
                    job.preflightProofWork(preflightProofUnits);
            }
            return "physical-contract";
        }

        public boolean proveNext() {
            proofCalls++;
            return proofCalls < proofUnits;
        }

        public String symptom() {
            symptomCalls++;
            return "symptom-contract";
        }

        public String dependencies() { return dependency; }

        public void publish(GenerationReceipt receipt) {
            publishCalls++;
            published = true;
            publishedReceipt = receipt;
            clock += publishAdvanceMillis;
            current = false;
            releaseOwner();
        }

        public void abort() {
            abortCalls++;
            releaseOwner();
        }

        private void releaseOwner() {
            if (ownerHeld) {
                ownerHeld = false;
                releases++;
            }
        }

        public boolean isCurrent() {
            if (published)
                currentChecksAfterPublish++;
            return current;
        }

        public long nowMillis() { return clock; }
    }

    private static class PreparedServices extends FakeServices
            implements GenerationJob.PublicationPreparation {
        final int preparationUnits;
        int totalPreparationCalls;
        int candidatePreparationCalls;
        int guardCalls;
        int totalRestorationUnits;
        int candidateRestorationUnits;
        int abortRestorations;
        int dependenciesReads;
        long prepareAdvanceMillis;
        int throwStaleAtCandidateCall = -1;
        int throwRuntimeAtCandidateCall = -1;
        String physicalDependencySnapshot;
        boolean temporaryContextActive;
        boolean externalTamper;
        boolean lastGuardSawRestoredContext;

        PreparedServices(String[] manifests, int preparationUnits) {
            super(manifests);
            if (preparationUnits < 0)
                throw new IllegalArgumentException("Preparation units cannot be negative");
            this.preparationUnits = preparationUnits;
        }

        public void beginCandidate(int index) {
            super.beginCandidate(index);
            dependency = BASE_DEPENDENCIES;
            physicalDependencySnapshot = null;
            candidatePreparationCalls = 0;
            candidateRestorationUnits = 0;
            temporaryContextActive = false;
            externalTamper = false;
            lastGuardSawRestoredContext = false;
        }

        public String dependencies() {
            dependenciesReads++;
            if (physicalDependencySnapshot == null)
                physicalDependencySnapshot = dependency;
            return dependency;
        }

        public boolean preparePublication() {
            totalPreparationCalls++;
            candidatePreparationCalls++;
            if (!ownerHeld || !current)
                throw new GenerationJob.Stale("Preparation lost its exact fake owner");

            advanceIntentionalPreparationContext();
            boolean more = candidatePreparationCalls <= preparationUnits;
            if (more)
                clock += prepareAdvanceMillis;

            if (candidatePreparationCalls == throwStaleAtCandidateCall)
                throw new GenerationJob.Stale("Injected stale publication preparation");
            if (candidatePreparationCalls == throwRuntimeAtCandidateCall)
                throw new IllegalStateException("Injected publication preparation failure");

            // Even the last restoration unit yields. The next call is the
            // separate false/ready result that allows atomic publication.
            return more;
        }

        private void advanceIntentionalPreparationContext() {
            if (externalTamper || physicalDependencySnapshot == null)
                return;
            if (candidatePreparationCalls < preparationUnits) {
                dependency = "temporary-preparation-context-" + candidatePreparationCalls;
                temporaryContextActive = true;
            } else if (candidatePreparationCalls == preparationUnits && preparationUnits > 0) {
                dependency = physicalDependencySnapshot;
                temporaryContextActive = false;
                candidateRestorationUnits++;
                totalRestorationUnits++;
            }
        }

        public void requirePublicationDependencies(String expected) {
            guardCalls++;
            lastGuardSawRestoredContext = publicationContextRestored(expected);
            if (!lastGuardSawRestoredContext)
                throw new GenerationJob.Stale("Prepared publication dependency context changed");
        }

        boolean publicationContextRestored(String expected) {
            return candidatePreparationCalls == preparationUnits + 1 &&
                expected != null && expected.equals(physicalDependencySnapshot) &&
                expected.equals(dependency) && !temporaryContextActive && !externalTamper;
        }

        void tamperDependency(String replacement) {
            dependency = replacement;
            externalTamper = true;
        }

        public void abort() {
            if (temporaryContextActive) {
                if (!externalTamper && physicalDependencySnapshot != null) {
                    dependency = physicalDependencySnapshot;
                    abortRestorations++;
                }
                temporaryContextActive = false;
            }
            super.abort();
        }
    }

    private static final class RetryAfterPreparationServices extends PreparedServices {
        final int rejectGuardSource;
        boolean rejectionUsed;

        RetryAfterPreparationServices(String[] manifests, int preparationUnits,
                int rejectGuardSource) {
            super(manifests, preparationUnits);
            this.rejectGuardSource = rejectGuardSource;
        }

        public void requirePublicationDependencies(String expected) {
            if (currentSource == rejectGuardSource && !rejectionUsed) {
                guardCalls++;
                lastGuardSawRestoredContext = publicationContextRestored(expected);
                if (!lastGuardSawRestoredContext)
                    throw new AssertionError("Candidate rejection must follow complete restoration");
                rejectionUsed = true;
                throw new GenerationJob.Rejected("Injected candidate-specific publication rejection");
            }
            super.requirePublicationDependencies(expected);
        }
    }

    private static final class RetryAfterRangePreflightServices extends PreparedServices {
        final int rejectGuardSource;
        private boolean rejectionUsed;
        private int candidateProofCalls;
        int firstDeclaredMaximum = -1;
        int secondDeclaredMaximum = -1;

        RetryAfterRangePreflightServices(String[] manifests, int preparationUnits,
                int rejectGuardSource) {
            super(manifests, preparationUnits);
            this.rejectGuardSource = rejectGuardSource;
        }

        public void beginCandidate(int index) {
            super.beginCandidate(index);
            candidateProofCalls = 0;
        }

        public String physical() {
            useProofRangePreflight = true;
            preflightAdditionalUnits = 0;
            if (currentSource == 0) {
                proofUnits = 1;
                preflightMinimumProofUnits = 1;
                preflightMaximumProofUnits = 1;
                firstDeclaredMaximum = preflightMaximumProofUnits;
            } else {
                proofUnits = 2;
                preflightMinimumProofUnits = 2;
                preflightMaximumProofUnits = 2;
                secondDeclaredMaximum = preflightMaximumProofUnits;
            }
            return super.physical();
        }

        public boolean proveNext() {
            proofCalls++;
            candidateProofCalls++;
            return candidateProofCalls < proofUnits;
        }

        public void requirePublicationDependencies(String expected) {
            if (currentSource == rejectGuardSource && !rejectionUsed) {
                guardCalls++;
                lastGuardSawRestoredContext = publicationContextRestored(expected);
                if (!lastGuardSawRestoredContext)
                    throw new AssertionError("Candidate rejection must follow complete restoration");
                rejectionUsed = true;
                throw new GenerationJob.Rejected("Injected first-candidate range retry rejection");
            }
            super.requirePublicationDependencies(expected);
        }
    }
}
