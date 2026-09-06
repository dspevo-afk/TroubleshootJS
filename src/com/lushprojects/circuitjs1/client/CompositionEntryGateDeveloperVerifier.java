package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.event.dom.client.ClickHandler;

/**
 * Developer-only lifecycle and fresh-owner proof for the composition entry
 * boundary.  This class deliberately observes the existing simulator,
 * workbench, instrument, challenge, and fresh-installation seams; it does not
 * introduce a second owner or a test-only electrical model.
 */
final class CompositionEntryGateDeveloperVerifier {
    private static final String LED = QuickPlayFamilyRegistry.LED_INDICATOR;
    private static final String NPN = QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH;
    private static final String RC = QuickPlayFamilyRegistry.RC_DELAY;

    private static int assertions;

    private CompositionEntryGateDeveloperVerifier() { }

    static String verify(final CirSim sim) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "developer entry");
        assertions = 0;

        final GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController originalChallenge =
            sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null &&
            originalChallenge.isReady() && sim.isGeneratedRuntimeSettled(),
            "original owner must be ready and settled");
        final boolean originalQuickPlayActive = sim.quickPlayActive;
        final QuickPlaySession originalQuickPlaySession = sim.quickPlaySession;
        final boolean originalHold = sim.holdCompositionGateVerification;
        final Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);

        try {
            String a = verifyActionableSettlement(sim, original, originalOwner,
                originalChallenge, originalQuickPlayActive, originalQuickPlaySession);
            restoreOriginal(sim, original, originalOwner, originalChallenge,
                originalQuickPlayActive, originalQuickPlaySession);
            String b = verifyOwnerAndRequestSuccession(sim, original, originalOwner,
                originalChallenge, originalQuickPlayActive, originalQuickPlaySession);
            restoreOriginal(sim, original, originalOwner, originalChallenge,
                originalQuickPlayActive, originalQuickPlaySession);
            String c = verifyFreshOwnerBoundary(sim, original, originalOwner,
                originalChallenge, originalQuickPlayActive, originalQuickPlaySession);
            restoreOriginal(sim, original, originalOwner, originalChallenge,
                originalQuickPlayActive, originalQuickPlaySession);
            String g = verifyResetAndFaultCausality(sim, original, originalOwner,
                originalChallenge, originalQuickPlayActive, originalQuickPlaySession);
            restoreOriginal(sim, original, originalOwner, originalChallenge,
                originalQuickPlayActive, originalQuickPlaySession);
            return "{\"protocol\":\"TSJ-COMPOSITION-ENTRY-GATE-1\",\"status\":\"OBSERVED\"," +
                "\"A\":" + a + ",\"B\":" + b + ",\"C\":" + c + ",\"G\":" + g +
                ",\"assertions\":" + assertions +
                ",\"originalOwnerRestored\":true,\"disposition\":\"CLOSED\"}";
        } finally {
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            sim.holdCompositionGateVerification = originalHold;
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            if (sim.getGeneratedBoardInstance() != originalOwner ||
                    sim.getGeneratedChallengeController() != originalChallenge)
                original.restore(sim);
            original.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == originalOwner &&
                sim.getGeneratedChallengeController() == originalChallenge,
                "final original owner restoration");
        }
    }

    private static String verifyActionableSettlement(CirSim sim,
            Task41SimulationSnapshot original, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge, boolean originalQuickPlayActive,
            QuickPlaySession originalQuickPlaySession) {
        QuickPlaySession session = freshSession(0, 3);
        installFresh(sim, session, true, "A-LED3");
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        PcbWorkbenchController workbench = sim.pcbWorkbenchController;
        require(owner == session.getInstance() && challenge != null && challenge.isReady(),
            "A fresh LED owner did not reach READY");
        require(workbench != null && workbench.isAttachedToSidebarForDeveloperVerification(),
            "A player workbench is not attached");

        ClickHandler modeHandler = sim.instrumentController
            .getPlayerModeHandlerForDeveloperVerification("DC_VOLTAGE");
        ClickHandler retestHandler = workbench.getCustomerRetestHandlerForDeveloperVerification();
        ClickHandler finishHandler = workbench.getFinishHandlerForDeveloperVerification();
        require(modeHandler != null && retestHandler != null && finishHandler != null,
            "A required player handlers are missing");
        String visible = workbench.getPlayerFacingTextForDeveloperVerification();
        require(visible.indexOf("Customer retest") >= 0 && visible.indexOf("Finish Job") >= 0,
            "A required player controls are not visible");
        require(sim.instrumentController.isPlayerVisibleModeButtonRegisteredForDeveloperVerification(
                "DC_VOLTAGE"), "A DC voltage mode is not player-visible");

        int modeBefore = sim.instrumentController.getActiveModeForDeveloperVerification();
        modeHandler.onClick(null);
        require(sim.instrumentController.getActiveModeForDeveloperVerification() != modeBefore,
            "A real instrument mode handler did not dispatch");
        modeHandler.onClick(null);
        require(sim.instrumentController.getActiveModeForDeveloperVerification() == modeBefore,
            "A real instrument mode handler did not toggle back");

        GeneratedCustomerRetestResult failed = sim.performCustomerRetest();
        require(failed != null && !failed.isPassed() &&
            challenge.getCustomerRetestResult() == failed && !challenge.isCompleted(),
            "A actual unrepaired retest did not fail");
        /* The retained widget handler is the actual player dispatch path. */
        retestHandler.onClick(null);
        require(challenge.getCustomerRetestResult() != null &&
            !challenge.getCustomerRetestResult().isPassed() && !challenge.isCompleted(),
            "A actual Retest Customer handler changed the unrepaired outcome");
        finishHandler.onClick(null);
        require(!challenge.isCompleted() && sim.getGeneratedBoardInstance() == owner,
            "A Finish Job handler completed an unrepaired board");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        settle(sim);
        require(sim.getBoardPowerController().isElectricallyUnpowered(),
            "A pending measurement fixture must be unpowered");
        GeneratedCustomerRetestResult resultBeforeHold = challenge.getCustomerRetestResult();
        ProbeTargetPair pendingMeasurementProbes = attachResistorProbes(sim, owner);
        Vector<CircuitElm> elementsBeforePendingMeasurement =
            new Vector<CircuitElm>(sim.elmList);
        ActiveMeasurementStimulus stimulusBeforePendingMeasurement =
            sim.lastActiveMeasurementStimulus;
        int modeBeforeHold = sim.instrumentController.getActiveModeForDeveloperVerification();
        sim.holdCompositionGateVerification = true;
        sim.requestGeneratedBoardVerification();
        require(!sim.isGeneratedRuntimeSettled() && !sim.isChallengeInteractionEnabled() &&
            !sim.isGeneratedSemanticInteractionEnabled(),
            "A pending verification remained actionable");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        boolean mutationRejected = false;
        try { sim.getBoardModificationController().liftLead("R1", "R1.1"); }
        catch (BoardModificationRejectedException expected) { mutationRejected = true; }
        boolean removalRejected = false;
        try { ReplaceableResistorBoardCapability.find(owner).getController().removeInstalledPart(); }
        catch (BoardModificationRejectedException expected) { removalRejected = true; }
        require(mutationRejected && removalRejected &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            elementsBeforePendingMeasurement.equals(sim.elmList),
            "A rapid pending power, lead and removal operations were not rejected");
        ActiveMeasurementReadiness pendingReadiness = sim.instrumentController
            .getActiveMeasurementReadinessForStrategy(pendingMeasurementProbes.red,
                pendingMeasurementProbes.black);
        double pendingDcVoltage = sim.instrumentController.measureDcVoltageForStrategy(
            pendingMeasurementProbes.red, pendingMeasurementProbes.black);
        require(pendingReadiness == ActiveMeasurementReadiness.WAITING &&
            Double.isNaN(pendingDcVoltage) && !sim.activeMeasurementOverlay &&
            sim.lastActiveMeasurementStimulus == stimulusBeforePendingMeasurement &&
            elementsBeforePendingMeasurement.equals(sim.elmList),
            "A pending adapter measurement was not rejected before stimulus install: readiness=" +
            pendingReadiness + ", dc=" + pendingDcVoltage + ", overlay=" +
            sim.activeMeasurementOverlay);
        modeHandler.onClick(null);
        retestHandler.onClick(null);
        finishHandler.onClick(null);
        require(sim.instrumentController.getActiveModeForDeveloperVerification() == modeBeforeHold &&
            challenge.getCustomerRetestResult() == resultBeforeHold && !challenge.isCompleted(),
            "A stale real player handler changed pending state");

        boolean analyzedWhileHeld = false;
        sim.updateCircuit();
        analyzedWhileHeld = sim.generatedBoardVerificationAnalyzed;
        require(analyzedWhileHeld && sim.generatedBoardVerificationPending &&
            !sim.isGeneratedRuntimeSettled(),
            "A hold did not expose pending analyzed state through CircuitJS");
        sim.holdCompositionGateVerification = false;
        settle(sim);
        require(sim.isGeneratedRuntimeSettled() && sim.isChallengeInteractionEnabled() &&
            sim.isGeneratedSemanticInteractionEnabled() &&
            !sim.generatedBoardVerificationPending,
            "A pending verification did not re-enable controls after settlement");
        modeHandler.onClick(null);
        require(sim.instrumentController.getActiveModeForDeveloperVerification() != modeBeforeHold,
            "A mode handler did not re-enable after settlement");

        return "{\"family\":\"LED_INDICATOR\",\"seed\":3," +
            "\"actualHandlers\":true,\"pendingAnalyzedWhileHeld\":" + analyzedWhileHeld +
            ",\"retestFailure\":true,\"controlsReenabled\":true,\"disposition\":\"CLOSED\"}";
    }

    private static String verifyOwnerAndRequestSuccession(CirSim sim,
            Task41SimulationSnapshot original, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge, boolean originalQuickPlayActive,
            QuickPlaySession originalQuickPlaySession) {
        QuickPlaySession firstSession = freshSession(0, 3);
        installFresh(sim, firstSession, true, "B-LED3");
        final GeneratedBoardInstance firstOwner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController firstChallenge = sim.getGeneratedChallengeController();
        final PcbWorkbenchController firstWorkbench = sim.pcbWorkbenchController;
        require(firstWorkbench != null && firstChallenge != null && firstChallenge.isReady(),
            "B first owner did not reach READY");
        final ClickHandler oldRetest = firstWorkbench
            .getCustomerRetestHandlerForDeveloperVerification();
        final ClickHandler oldFinish = firstWorkbench
            .getFinishHandlerForDeveloperVerification();
        final ClickHandler oldSemantic = firstWorkbench
            .getLastSemanticOperationHandlerForDeveloperVerification();
        final ClickHandler oldPhysical = firstWorkbench
            .getPhysicalActionHandlerForDeveloperVerification();
        require(oldRetest != null && oldFinish != null,
            "B first owner retained handlers are missing");

        sim.repaint();
        final com.google.gwt.core.client.Scheduler.RepeatingCommand staleRepaint =
            sim.pendingGeneratedRepaint;
        require(staleRepaint != null, "B real repaint was not scheduled");

        QuickPlaySession secondSession = freshSession(4, 0);
        installFresh(sim, secondSession, true, "B-NPN0");
        final GeneratedBoardInstance secondOwner = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController secondChallenge = sim.getGeneratedChallengeController();
        final PcbWorkbenchController secondWorkbench = sim.pcbWorkbenchController;
        require(secondOwner == secondSession.getInstance() && secondOwner != firstOwner &&
            secondChallenge != null && secondChallenge.isReady() && secondWorkbench != null,
            "B second owner did not replace the first owner");
        final com.google.gwt.core.client.Scheduler.RepeatingCommand currentRepaint =
            sim.pendingGeneratedRepaint;
        require(currentRepaint != null && currentRepaint != staleRepaint,
            "B second owner did not retain its own repaint request");
        Vector<CircuitElm> activeBeforeStaleWork = new Vector<CircuitElm>(sim.elmList);
        GeneratedCustomerRetestResult retestBeforeStaleWork =
            secondChallenge.getCustomerRetestResult();
        boolean faultAppliedBeforeStaleWork = secondOwner.getFaultBinding().isApplied();
        GeneratedChallengeState stateBeforeStaleWork = secondChallenge.getState();

        /* This is the actual one-shot command captured from owner A. */
        staleRepaint.execute();
        require(sim.getGeneratedBoardInstance() == secondOwner &&
            sim.getGeneratedChallengeController() == secondChallenge &&
            sim.pendingGeneratedRepaint == currentRepaint &&
            activeBeforeStaleWork.equals(sim.elmList) &&
            secondChallenge.getCustomerRetestResult() == retestBeforeStaleWork &&
            secondOwner.getFaultBinding().isApplied() == faultAppliedBeforeStaleWork &&
            secondChallenge.getState() == stateBeforeStaleWork,
            "B stale repaint changed the replacement owner");

        oldRetest.onClick(null);
        oldFinish.onClick(null);
        if (oldSemantic != null) oldSemantic.onClick(null);
        if (oldPhysical != null) oldPhysical.onClick(null);
        require(sim.getGeneratedBoardInstance() == secondOwner &&
            sim.getGeneratedChallengeController() == secondChallenge &&
            sim.pendingGeneratedRepaint == currentRepaint &&
            activeBeforeStaleWork.equals(sim.elmList) &&
            secondChallenge.getCustomerRetestResult() == retestBeforeStaleWork &&
            secondOwner.getFaultBinding().isApplied() == faultAppliedBeforeStaleWork &&
            secondChallenge.getState() == stateBeforeStaleWork,
            "B retained workbench handlers changed the replacement owner");

        /* First exercise the current player Retest Customer widget itself. */
        ClickHandler currentRetest = secondWorkbench
            .getCustomerRetestHandlerForDeveloperVerification();
        require(currentRetest != null, "B replacement retest handler is missing");
        currentRetest.onClick(null);
        require(secondChallenge.getCustomerRetestResult() != null &&
            !secondChallenge.getCustomerRetestResult().isPassed(),
            "B current Retest Customer handler did not execute");
        settle(sim);
        secondChallenge.invalidateCustomerRetest();

        final Vector<Runnable> completions = new Vector<Runnable>();
        secondChallenge.setRetestCompletionDispatchForDeveloperVerification(
            new GeneratedChallengeController.RetestCompletionDispatch() {
                public void dispatch(Runnable completion) {
                    require(completion != null, "B missing retest completion");
                    completions.add(completion);
                }
            });
        GeneratedCustomerRetestResult firstResult = null;
        GeneratedCustomerRetestResult secondResult = null;
        try {
            firstResult = sim.performCustomerRetest();
            settle(sim);
            secondResult = sim.performCustomerRetest();
            require(firstResult != null && secondResult != null &&
                !firstResult.isPassed() && !secondResult.isPassed() &&
                firstResult != secondResult && completions.size() == 2,
                "B did not capture two real retest completions: count=" + completions.size());
            settle(sim);
            completions.get(1).run();
            require(secondChallenge.getCustomerRetestResult() == secondResult,
                "B newest retest completion was not published");
            completions.get(0).run();
            require(secondChallenge.getCustomerRetestResult() == secondResult,
                "B superseded retest completion overwrote newest result");
            sim.performCustomerRetest();
            settle(sim);
            require(completions.size() == 3, "B missing owner-specific retest completion");
            QuickPlaySession nextSession = freshSession(0, 3);
            installFresh(sim, nextSession, true, "B-stale-retest-replacement");
            GeneratedChallengeController nextChallenge = sim.getGeneratedChallengeController();
            Vector<CircuitElm> replacementGraph = new Vector<CircuitElm>(sim.elmList);
            completions.get(2).run();
            require(sim.getGeneratedBoardInstance() == nextSession.getInstance() &&
                sim.getGeneratedChallengeController() == nextChallenge &&
                nextChallenge.getCustomerRetestResult() == null &&
                replacementGraph.equals(sim.elmList) &&
                secondChallenge.getCustomerRetestResult() == null,
                "B prior-owner retest completion changed a replaced challenge");
        } finally {
            secondChallenge.setRetestCompletionDispatchForDeveloperVerification(null);
        }

        return "{\"firstFamily\":\"LED_INDICATOR\",\"firstSeed\":3," +
            "\"secondFamily\":\"NPN_LOW_SIDE_SWITCH\",\"secondSeed\":0," +
            "\"actualScheduledRepaint\":true,\"staleRepaintDiscarded\":true," +
            "\"staleWorkbenchHandlersDiscarded\":true,\"supersededRetestDiscarded\":true," +
            "\"staleRetestOwnerDiscarded\":true," +
            "\"disposition\":\"CLOSED\"}";
    }

    private static String verifyFreshOwnerBoundary(CirSim sim,
            Task41SimulationSnapshot original, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge, boolean originalQuickPlayActive,
            QuickPlaySession originalQuickPlaySession) {
        OwnerRefs before = new OwnerRefs(sim);
        boolean unsupportedRejected = false;
        try {
            FreshGeneratedRuntimeInstallation.installComposition(sim,
                QuickPlayFamilyRegistry.generate(LED, 3), false);
        } catch (IllegalStateException expected) {
            unsupportedRejected = "Composition does not support mutable provider: REPLACEABLE_LED"
                .equals(expected.getMessage());
        }
        require(unsupportedRejected, "C composition entry accepted an unsupported LED provider");
        before.assertSame(sim, "C unsupported composition provider");
        boolean rejected = false;
        try {
            FreshGeneratedRuntimeInstallation.install(sim, originalOwner, false);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "C current-instance installation was accepted");
        before.assertSame(sim, "C current-instance rejection");

        QuickPlaySession successSession = freshSession(0, 3);
        installFresh(sim, successSession, true, "C-success");
        GeneratedBoardInstance successOwner = sim.getGeneratedBoardInstance();
        require(successOwner == successSession.getInstance() && successOwner != originalOwner &&
            sim.getGeneratedChallengeController() != originalChallenge &&
            sim.getGeneratedChallengeController().isReady() && sim.isGeneratedRuntimeSettled(),
            "C fresh candidate was not published as a distinct owner");
        restoreOriginal(sim, original, originalOwner, originalChallenge,
            originalQuickPlayActive, originalQuickPlaySession);

        FreshGeneratedRuntimeInstallation.Stage[] stages =
            FreshGeneratedRuntimeInstallation.Stage.values();
        int injected = 0;
        for (FreshGeneratedRuntimeInstallation.Stage stage : stages) {
            restoreOriginal(sim, original, originalOwner, originalChallenge,
                originalQuickPlayActive, originalQuickPlaySession);
            QuickPlaySession candidate = freshSession(0, 3);
            sim.quickPlayActive = true;
            sim.quickPlaySession = candidate;
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(stage);
            boolean threw = false;
            String failureMessage = null;
            try {
                FreshGeneratedRuntimeInstallation.install(sim, candidate.getInstance(), true);
            } catch (RuntimeException expected) {
                threw = true;
                failureMessage = expected.getMessage();
            } finally {
                FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            }
            require(threw && ("Injected fresh installation failure after " + stage)
                .equals(failureMessage), "C injected fresh stage did not fail exactly: " + stage);
            sim.quickPlayActive = originalQuickPlayActive;
            sim.quickPlaySession = originalQuickPlaySession;
            original.assertRestored(sim);
            before.assertSame(sim, "C injected stage " + stage);
            injected++;
        }
        return "{\"currentInstanceRejected\":true,\"unsupportedCompositionProviderRejected\":true," +
            "\"freshCandidatePublished\":true," +
            "\"injectedStages\":" + injected +
            ",\"originalReferencesRestored\":true,\"disposition\":\"CLOSED\"}";
    }

    private static String verifyResetAndFaultCausality(CirSim sim,
            Task41SimulationSnapshot original, GeneratedBoardInstance originalOwner,
            GeneratedChallengeController originalChallenge, boolean originalQuickPlayActive,
            QuickPlaySession originalQuickPlaySession) {
        final String[] families = { LED, NPN, RC };
        final int[] familyIndexes = { 0, 4, 3 };
        final long[] seeds = { 3, 0, 2 };
        final Vector<GObservation> observations = new Vector<GObservation>();

        for (int index = 0; index < families.length; index++) {
            QuickPlaySession session = freshSession(familyIndexes[index], seeds[index]);
            installFresh(sim, session, true, "G-" + families[index]);
            GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            require(owner == session.getInstance() && challenge != null && challenge.isReady(),
                "G candidate did not reach READY: " + families[index]);

            GeneratedFaultBinding binding = owner.getFaultBinding();
            require(binding != null && binding.getFault() != null,
                "G candidate has no generated fault binding: " + families[index]);
            GeneratedFault fault = binding.getFault();
            PhysicalPart<?> originalPart = owner.getPhysicalBoardRuntime().getInstalledPart(
                fault.getTargetComponentId());
            require(binding != null && fault != null && originalPart != null,
                "G candidate fault has no installed physical owner: " + families[index]);
            require(binding.isApplied() && originalPart.isFaulted(),
                "G selected fault was not owned by original physical part: " + families[index]);

            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            ProbeTargetPair probes = attachResistorProbes(sim, owner);
            require(probes.red != null && probes.black != null &&
                sim.instrumentController.getRedProbeForStrategy() == probes.red &&
                sim.instrumentController.getBlackProbeForStrategy() == probes.black,
                "G real DC probes were not attached: " + families[index]);

            final Vector<Runnable> staleCompletions = new Vector<Runnable>();
            challenge.setRetestCompletionDispatchForDeveloperVerification(
                new GeneratedChallengeController.RetestCompletionDispatch() {
                    public void dispatch(Runnable completion) {
                        require(completion != null, "G missing queued retest completion");
                        staleCompletions.add(completion);
                    }
                });
            GeneratedCustomerRetestResult failed;
            try {
                failed = sim.performCustomerRetest();
                require(failed != null && !failed.isPassed() && staleCompletions.size() == 1,
                    "G actual powered fault retest did not fail: " + families[index]);
                /* RC's real customer retest performs a power cycle and can
                 * queue verification; settle that actual request before reset. */
                settle(sim);
                require(sim.isGeneratedRuntimeSettled(),
                    "G could not settle before reset: " + families[index]);
                sim.resetAction();
                require(sim.getGeneratedBoardInstance() == owner &&
                    sim.getGeneratedChallengeController() == challenge &&
                    challenge.getCustomerRetestResult() == null,
                    "G reset did not invalidate the pending retest: " + families[index]);
                settle(sim);
                staleCompletions.get(0).run();
                require(sim.getGeneratedBoardInstance() == owner &&
                    sim.getGeneratedChallengeController() == challenge &&
                    challenge.getCustomerRetestResult() == null &&
                    sim.instrumentController.getRedProbeForStrategy() == null &&
                    sim.instrumentController.getBlackProbeForStrategy() == null,
                    "G stale pre-reset completion changed reset state: " + families[index]);
            } finally {
                challenge.setRetestCompletionDispatchForDeveloperVerification(null);
            }
            require(binding == owner.getFaultBinding() && fault == binding.getFault() &&
                binding.isApplied() && originalPart.isFaulted() && originalPart.isInstalled() &&
                sim.getGeneratedBoardInstance() == owner &&
                sim.getGeneratedChallengeController() == challenge &&
                challenge.isReady() && sim.isGeneratedRuntimeSettled(),
                "G reset changed fault causality or owner: " + families[index]);
            observations.add(new GObservation(session, owner, challenge, binding, fault,
                originalPart, failed));
        }

        for (int left = 0; left < observations.size(); left++) {
            for (int right = left + 1; right < observations.size(); right++) {
                GObservation a = observations.get(left);
                GObservation b = observations.get(right);
                require(a.session != b.session && a.owner != b.owner &&
                    a.owner.getBoard() != b.owner.getBoard() &&
                    a.owner.getPhysicalBoardRuntime() != b.owner.getPhysicalBoardRuntime() &&
                    a.challenge != b.challenge && a.binding != b.binding &&
                    a.fault != b.fault && a.originalPart != b.originalPart,
                    "G fresh succession reused mutable identity");
            }
        }
        return "{\"families\":3,\"resetOwnerPreserved\":true," +
            "\"preResetRetestInvalidated\":true,\"staleCompletionDiscarded\":true," +
            "\"faultCausalityPreserved\":true,\"freshOwners\":true," +
            "\"disposition\":\"CLOSED\"}";
    }

    private static ProbeTargetPair attachResistorProbes(CirSim sim, GeneratedBoardInstance owner) {
        String component = owner.getPhysicalBoardRuntime().getInstalledPart("R1") != null ?
            "R1" : "RLOAD";
        CircuitElm resistor = owner.getComponentBindings().getSingleElement(component);
        if (resistor == null || resistor.getPostCount() < 2)
            throw new IllegalStateException("G no resistor probe source for " + component);
        CircuitPostProbeTarget red = new CircuitPostProbeTarget(sim, resistor, 0);
        CircuitPostProbeTarget black = new CircuitPostProbeTarget(sim, resistor, 1);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(red, black);
        return new ProbeTargetPair(red, black);
    }

    private static QuickPlaySession freshSession(long familyIndex, long seed) {
        return QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { familyIndex, seed }));
    }

    private static void installFresh(CirSim sim, QuickPlaySession session,
            boolean attachWorkbench, String label) {
        require(session != null && session.getInstance() != null &&
            session.getSelection() != null, "missing fresh session " + label);
        sim.quickPlayActive = true;
        sim.quickPlaySession = session;
        FreshGeneratedRuntimeInstallation.install(sim, session.getInstance(), attachWorkbench);
        require(sim.getGeneratedBoardInstance() == session.getInstance() &&
            sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady() && sim.isGeneratedRuntimeSettled(),
            "fresh session is not actionable: " + label);
    }

    private static void settle(CirSim sim) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 20; attempt++) {
            sim.updateCircuit();
            if (sim.isGeneratedRuntimeSettled()) return;
        }
        throw new IllegalStateException("Composition entry gate did not settle through CircuitJS");
    }

    private static void restoreOriginal(CirSim sim, Task41SimulationSnapshot snapshot,
            GeneratedBoardInstance owner, GeneratedChallengeController challenge,
            boolean quickPlayActive, QuickPlaySession quickPlaySession) {
        sim.quickPlayActive = quickPlayActive;
        sim.quickPlaySession = quickPlaySession;
        snapshot.restore(sim);
        snapshot.assertRestored(sim);
        require(sim.getGeneratedBoardInstance() == owner &&
            sim.getGeneratedChallengeController() == challenge,
            "original owner was not restored");
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Composition entry gate: " + message);
    }

    private static final class OwnerRefs {
        private final GeneratedBoardInstance owner;
        private final GeneratedChallengeController challenge;
        private final BoardModificationController modifications;
        private final PcbWorkbenchController workbench;
        private final Vector<CircuitElm> elements;
        private final Vector<CircuitNode> nodes;
        private final GeneratedFaultBinding faultBinding;
        private final GeneratedFault fault;

        OwnerRefs(CirSim sim) {
            owner = sim.getGeneratedBoardInstance();
            challenge = sim.getGeneratedChallengeController();
            modifications = sim.getBoardModificationController();
            workbench = sim.pcbWorkbenchController;
            elements = new Vector<CircuitElm>(sim.elmList);
            nodes = new Vector<CircuitNode>(sim.nodeList);
            faultBinding = owner.getFaultBinding();
            fault = faultBinding == null ? null : faultBinding.getFault();
        }

        void assertSame(CirSim sim, String label) {
            require(sim.getGeneratedBoardInstance() == owner &&
                sim.getGeneratedChallengeController() == challenge &&
                sim.getBoardModificationController() == modifications &&
                sim.pcbWorkbenchController == workbench &&
                sim.elmList.equals(elements) && sim.nodeList.equals(nodes) &&
                sim.getGeneratedBoardInstance().getFaultBinding() == faultBinding &&
                (faultBinding == null || faultBinding.getFault() == fault), label);
        }
    }

    private static final class ProbeTargetPair {
        final ProbeTarget red;
        final ProbeTarget black;
        ProbeTargetPair(ProbeTarget red, ProbeTarget black) {
            this.red = red;
            this.black = black;
        }
    }

    private static final class GObservation {
        final QuickPlaySession session;
        final GeneratedBoardInstance owner;
        final GeneratedChallengeController challenge;
        final GeneratedFaultBinding binding;
        final GeneratedFault fault;
        final PhysicalPart<?> originalPart;
        final GeneratedCustomerRetestResult failed;

        GObservation(QuickPlaySession session, GeneratedBoardInstance owner,
                GeneratedChallengeController challenge, GeneratedFaultBinding binding,
                GeneratedFault fault, PhysicalPart<?> originalPart,
                GeneratedCustomerRetestResult failed) {
            this.session = session;
            this.owner = owner;
            this.challenge = challenge;
            this.binding = binding;
            this.fault = fault;
            this.originalPart = originalPart;
            this.failed = failed;
        }
    }
}
