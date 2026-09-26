package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Installs one real Q30 assembly in the production PCB workbench for visual
 * developer inspection.  This route is deliberately separate from normal
 * challenge admission: it uses the provider's live CircuitJS assembly, the
 * selected medium physical policy, and the actual provider challenge. It
 * remains a developer qualification route, without normal admission.
 */
final class Q30DeveloperWorkbenchVerifier {
    private static int checks;
    private static long qualificationStarted, constructionMillis, routingMillis, serviceMillis;

    private static void require(boolean ok, String why) {
        checks++;
        if (!ok) throw new IllegalStateException("Q30 " + why);
    }

    static boolean verify(CirSim sim, String requestedSeed, boolean keepBench) {
        if (sim == null || !sim.troubleshootDebug || !sim.troubleshootQ30Verification ||
                !sim.developerVerifierRunning)
            throw new IllegalStateException("Q30 requires explicit developer verification");
        checks = 0;
        qualificationStarted = System.currentTimeMillis();
        constructionMillis = routingMillis = serviceMillis = 0;
        final long seed;
        try {
            seed = parseSeed(requestedSeed);
        } catch (Throwable invalidSeed) {
            Throwable failure = invalidSeed;
            try {
                publishCleanup(true, false);
            } catch (Throwable publication) {
                failure = retain(failure, publication);
            }
            try {
                publishFailure(requestedSeed, null, null, failure, "seed", true, false);
            } catch (Throwable publication) {
                failure = retain(failure, publication);
            }
            rethrow(failure);
            return false;
        }
        final Task41SimulationSnapshot saved = Task41SimulationSnapshot.capture(sim);
        final GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        boolean proofStarted = false;
        boolean retained = false;
        boolean restored = false;
        Rb30Plan plan = null;
        MediumBoardPhysicalPolicy.Result routed = null;
        GeneratedBoardInstance owner = null;
        PcbWorkbenchRenderer renderer = null;
        int topTargets = 0;
        int bottomTargets = 0;
        int topInspectable = 0;
        int bottomInspectable = 0;
        Vector<String> topPadLimits = new Vector<String>();
        Vector<String> bottomPadLimits = new Vector<String>();
        Vector<String> topOverviewLimits = new Vector<String>();
        Vector<String> bottomOverviewLimits = new Vector<String>();
        Vector<CircuitElm> q30Elements = null;
        Vector<Adjustable> q30Adjustables = null;
        Vector<String> q30Undo = null;
        Vector<String> q30Redo = null;
        CopperMetadata copperMetadata = null;
        Throwable failure = null;
        String phase = "snapshot";
        String failurePhase = null;
        try {
            phase = "plan";
            plan = Rb30Plan.resolve(seed);
            phase = "candidate-construction";
            long phaseStarted = System.currentTimeMillis();
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan,
                com.google.gwt.user.client.Window.Location.getParameter("tsjQ30Fault"));
            constructionMillis = System.currentTimeMillis() - phaseStarted;
            require(MediumBoardPhysicalPolicy.selected(
                candidate.board().getPlacementConstraints()),
                "candidate did not select the medium physical policy");

            phase = "route-selection";
            phaseStarted = System.currentTimeMillis();
            routed = new SeededPcbLayoutGenerator().generateWithPolicyResult(
                candidate.board(), plan.layoutSeed, plan.routingSeed);
            routingMillis = System.currentTimeMillis() - phaseStarted;
            if (!routed.accepted())
                throw new IllegalStateException("Q30 medium policy rejected seed " +
                    Long.toString(seed) + ": " + String.valueOf(routed.getFailure()));
            PcbBoardLayout layout = routed.getLayout();
            require(layout != null, "medium policy returned no routed layout");
            require(layout.matchesGenerationSeeds(plan.layoutSeed, plan.routingSeed),
                "routed layout seed identity changed");
            MediumBoardPhysicalPolicy.Statistics statistics = routed.getStatistics();
            require(statistics != null, "medium policy returned no statistics");
            require(MediumBoardPhysicalPolicy.identity().equals(statistics.policyIdentity),
                "medium policy statistics identity changed: " + statistics.policyIdentity);
            require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                statistics.selectedRoutePolicy),
                "medium policy selected " + String.valueOf(statistics.selectedRoutePolicy) +
                    "; Q30 requires " + MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER);

            /*
             * The assembly owns every element, endpoint and physical service
             * binding.  The constructor remains the authority for
             * ServiceableBoardConstruction and physical correspondence; this
             * verifier does not bypass or weaken those checks.
             */
            phase = "generated-board-constructor";
            owner = new Rb30Generator().assemble(candidate, layout);
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);

            phase = "fresh-owner-preflight";
            requireQ30Disjoint(originalOwner, owner);
            /* Any failure after this point must return the exact original owner. */
            proofStarted = true;
            phase = "snapshot-proof-start";
            saved.beginProof(sim);
            q30Elements = new Vector<CircuitElm>();
            q30Adjustables = new Vector<Adjustable>();
            q30Undo = new Vector<String>();
            q30Redo = new Vector<String>();
            sim.elmList = q30Elements;
            sim.adjustables = q30Adjustables;
            sim.undoStack = q30Undo;
            sim.redoStack = q30Redo;
            phase = "generated-runtime-install";
            // This stiff relay/regulator qualification uses ordinary CircuitJS
            // adaptive stepping. Never inherit the predecessor circuit's flags.
            // The captured snapshot restores these settings on owned cleanup.
            sim.timeStep = sim.maxTimeStep = 5e-6;
            sim.minTimeStep = 50e-12;
            sim.adjustTimeStep = true;
            sim.installGeneratedChallengeForDeveloperVerification(owner);
            sim.setSimRunning(true);
            phase = "solver-settle";
            settle(sim);
            require(sim.getGeneratedChallengeController().isReady(), "challenge is not ready");
            phase = "live-binding-check";
            verifyLiveBindings(owner);

            phase = "workbench-install";
            PcbWorkbenchController controller = sim.pcbWorkbenchController;
            require(controller != null, "production workbench was not installed");
            renderer = controller.getRenderer();
            if (keepBench) {
                boolean allPadsPlatedThroughHole =
                    verifyPadDeclarations(owner);
                phase = "workbench-sidebar-attach";
                controller.attachToSidebar(sim.verticalPanel);
                require(controller.isAttachedToSidebarForDeveloperVerification(),
                    "retained workbench did not attach");
                sim.refreshGeneratedUiForDeveloperVerification();
                phase = "workbench-target-scan";
                sim.updateCircuit();
                renderer.fitWorkbench();
                PadScan topScan = collectTargets(sim, controller, renderer,
                    owner, PcbBoardSide.TOP, topPadLimits, topOverviewLimits, true);
                PadScan bottomScan = collectTargets(sim, controller, renderer,
                    owner, PcbBoardSide.BOTTOM, bottomPadLimits, bottomOverviewLimits, true);
                topTargets = topScan.overviewTargets;
                bottomTargets = bottomScan.overviewTargets;
                topInspectable = topScan.inspectableTargets;
                bottomInspectable = bottomScan.inspectableTargets;
                require(topInspectable > 0,
                    "top face has no production-inspectable pad");
                require(bottomInspectable > 0,
                    "bottom face has no production-inspectable pad");
                if (allPadsPlatedThroughHole) {
                    int padCount = owner.getBoard().getPadIds().size();
                    require(topInspectable == padCount,
                        "top face fit/occlusion inspection did not resolve every plated/exposed pad: " +
                            topInspectable + "/" + padCount + " limits=" +
                            jsonStrings(topPadLimits));
                    require(bottomInspectable == padCount,
                        "bottom face fit/occlusion inspection did not resolve every plated/exposed pad: " +
                            bottomInspectable + "/" + padCount + " limits=" +
                            jsonStrings(bottomPadLimits));
                }
                phase = "copper-face-proof";
                copperMetadata = verifyCopperFaceTruth(sim, controller, renderer, owner);
                renderer.setViewingFace(PcbBoardSide.TOP);
                phase = "final-face-publish";
                settle(sim);
                renderer.fitWorkbench();
                publishFace(PcbBoardSide.TOP);
                sim.refreshGeneratedUiForDeveloperVerification();
                sim.updateCircuit();
                publishTargets(owner, renderer, topTargets, bottomTargets,
                    topInspectable, bottomInspectable);
                publishReport(sim, owner, plan, owner.getPcbLayout(),
                    statistics, renderer, false, true, "PASS", true,
                    topTargets, bottomTargets, topInspectable, bottomInspectable,
                    topPadLimits, bottomPadLimits, topOverviewLimits,
                    bottomOverviewLimits, copperMetadata);
                retained = true;
            }
        } catch (Throwable problem) {
            failure = problem;
            failurePhase = phase;
        }

        if (!retained && proofStarted) {
            phase = "cleanup";
            CleanupResult cleanup = cleanup(sim, saved, owner, originalOwner,
                q30Elements, q30Adjustables, q30Undo, q30Redo);
            restored = cleanup.restored;
            failure = retain(failure, cleanup.failure);
            if (failurePhase == null && cleanup.failure != null)
                failurePhase = "cleanup";
        }

        if (failure == null && !keepBench) {
            try {
                phase = "runtime-report";
                publishReport(sim, owner, plan, owner.getPcbLayout(),
                    routed.getStatistics(), renderer, restored, false,
                    "PASS_RUNTIME", false, topTargets, bottomTargets,
                    topInspectable, bottomInspectable, topPadLimits,
                    bottomPadLimits, topOverviewLimits, bottomOverviewLimits,
                    copperMetadata);
            } catch (Throwable publication) {
                failure = retain(failure, publication);
                if (failurePhase == null) failurePhase = phase;
            }
        }

        boolean reportRestored = !proofStarted || restored;
        try {
            phase = "cleanup-report";
            publishCleanup(reportRestored, retained);
        } catch (Throwable publication) {
            failure = retain(failure, publication);
            if (failurePhase == null) failurePhase = phase;
            if (retained) {
                retained = false;
                CleanupResult cleanup = cleanup(sim, saved, owner, originalOwner,
                    q30Elements, q30Adjustables, q30Undo, q30Redo);
                restored = cleanup.restored;
                failure = retain(failure, cleanup.failure);
                if (failurePhase == null && cleanup.failure != null)
                    failurePhase = "cleanup";
                try {
                    reportRestored = !proofStarted || restored;
                    publishCleanup(reportRestored, false);
                } catch (Throwable retryPublication) {
                    failure = retain(failure, retryPublication);
                    if (failurePhase == null) failurePhase = phase;
                }
            }
        }

        if (failure != null) {
            try {
                publishFailure(seed, plan, routed, failure,
                    failurePhase == null ? phase : failurePhase,
                    reportRestored, retained);
            } catch (Throwable publication) {
                failure = retain(failure, publication);
            }
            rethrow(failure);
        }
        if (retained && "true".equals(com.google.gwt.user.client.Window.Location.getParameter("tsjQ30Service")))
            scheduleService(sim, saved, owner, originalOwner, q30Elements,
                q30Adjustables, q30Undo, q30Redo, plan, routed);
        return retained;
    }

    /** Keep asynchronous qualification cleanup tied to its exact saved owner. */
    private static void scheduleService(final CirSim sim, final Task41SimulationSnapshot saved,
            final GeneratedBoardInstance owner, final GeneratedBoardInstance originalOwner,
            final Vector<CircuitElm> elements, final Vector<Adjustable> adjustables,
            final Vector<String> undo, final Vector<String> redo, final Rb30Plan plan,
            final MediumBoardPhysicalPolicy.Result routed) {
        final BoardModificationController modifications = sim.getBoardModificationController();
        final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        Q30ServiceDeveloperVerifier.Completion completion = new Q30ServiceDeveloperVerifier.Completion() {
            public void complete(Throwable failure, long elapsedMillis) {
                if (sim.getGeneratedBoardInstance() != owner || sim.elmList != elements ||
                        sim.getBoardModificationController() != modifications ||
                        sim.getGeneratedChallengeController() != challenge)
                    return; // A stale attempt cannot restore or publish over its successor.
                serviceMillis = elapsedMillis;
                if (failure == null) return;
                boolean wasVerifying = sim.developerVerifierRunning;
                boolean restored = false;
                try {
                    sim.developerVerifierRunning = true;
                    // Stale qualification must never replace a successor board.
                    if (sim.getGeneratedBoardInstance() == owner && sim.elmList == elements &&
                            sim.getBoardModificationController() == modifications &&
                            sim.getGeneratedChallengeController() == challenge) {
                        CleanupResult result = cleanup(sim, saved, owner, originalOwner,
                            elements, adjustables, undo, redo);
                        restored = result.restored;
                        failure = retain(failure, result.failure);
                    }
                    boolean stillRetained = sim.getGeneratedBoardInstance() == owner;
                    publishCleanup(restored, stillRetained);
                    publishFailure(owner.getSeed(), plan, routed, failure,
                        "diagnostic-service-retest", restored, stillRetained);
                } finally {
                    sim.developerVerifierRunning = wasVerifying;
                }
            }
        };
        try { Q30ServiceDeveloperVerifier.schedule(sim, owner, completion); }
        catch (Throwable failure) { completion.complete(failure, 0); }
    }

    /** Full challenge owners now use the shared disjoint-installation contract. */
    private static void requireQ30Disjoint(GeneratedBoardInstance original,
            GeneratedBoardInstance candidate) {
        FreshGeneratedRuntimeInstallation.requireDisjoint(original, candidate);
    }
    private static long parseSeed(String requestedSeed) {
        if (requestedSeed == null)
            return 0L;
        if (requestedSeed.length() == 0)
            throw new IllegalArgumentException("Q30 seed cannot be explicitly empty");
        final long seed;
        try {
            seed = Long.parseLong(requestedSeed);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Q30 seed is not canonical: " + requestedSeed);
        }
        if (!Long.toString(seed).equals(requestedSeed))
            throw new IllegalArgumentException("Q30 seed is not canonical: " + requestedSeed);
        if (seed != 0L && seed != 37L)
            throw new IllegalArgumentException("Q30 developer bench supports seed 0 or 37: " +
                Long.toString(seed));
        return seed;
    }

    private static final class CleanupResult {
        final boolean restored;
        final Throwable failure;

        CleanupResult(boolean restored, Throwable failure) {
            this.restored = restored;
            this.failure = failure;
        }
    }

    /**
     * Retire the Q30 graph while it is still the live owner, then restore the
     * captured customer graph.  A failed retirement leaves the stopped Q30
     * owner quarantined; swapping the snapshot over a partially retired live
     * owner would risk stale solver elements and power bindings.
     */
    private static CleanupResult cleanup(CirSim sim, Task41SimulationSnapshot saved,
            GeneratedBoardInstance owner, GeneratedBoardInstance originalOwner,
            Vector<CircuitElm> q30Elements, Vector<Adjustable> q30Adjustables,
            Vector<String> q30Undo, Vector<String> q30Redo) {
        Throwable failure = null;
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        if (current != null && current != owner && current != originalOwner) {
            failure = retain(failure, new IllegalStateException(
                "Q30 cleanup found a successor generated owner"));
            return new CleanupResult(false, failure);
        }
        boolean q30WasCurrent = current == owner;
        failure = retain(failure, retireQ30Owner(sim, owner, originalOwner,
            q30Elements, q30Adjustables, q30Undo, q30Redo));
        if (q30WasCurrent && failure != null) {
            failure = retain(failure, quarantineQ30Owner(sim, owner));
            return new CleanupResult(false, failure);
        }
        try {
            saved.restore(sim);
            saved.assertRestored(sim);
            return new CleanupResult(true, failure);
        } catch (Throwable restoration) {
            failure = retain(failure, restoration);
            failure = retain(failure, detachQ30PowerIfCurrent(sim, owner));
            if (sim.getGeneratedBoardInstance() == owner)
                failure = retain(failure, quarantineQ30Owner(sim, owner));
            return new CleanupResult(false, failure);
        }
    }

    private static Throwable retireQ30Owner(CirSim sim, GeneratedBoardInstance owner,
            GeneratedBoardInstance originalOwner, Vector<CircuitElm> q30Elements,
            Vector<Adjustable> q30Adjustables, Vector<String> q30Undo,
            Vector<String> q30Redo) {
        if (owner == null) {
            clearTemporarySources(q30Elements, q30Adjustables, q30Undo, q30Redo);
            return null;
        }
        if (owner == originalOwner)
            return new IllegalStateException("Q30 cleanup was given the original owner");
        if (sim.getGeneratedBoardInstance() != owner) {
            clearTemporarySources(q30Elements, q30Adjustables, q30Undo, q30Redo);
            return null;
        }

        Throwable failure = null;
        try {
            sim.invalidateGeneratedOwnerWork();
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        }
        try {
            sim.setSimRunning(false);
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        }
        PcbWorkbenchController workbench = sim.pcbWorkbenchController;
        try {
            if (workbench != null)
                workbench.disposeForDeveloperVerification();
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        } finally {
            if (sim.pcbWorkbenchController == workbench)
                sim.pcbWorkbenchController = null;
            if (workbench != null) {
                try {
                    sim.unregisterAttachedPcbWorkbenchForDeveloperVerification(workbench);
                } catch (Throwable cleanup) {
                    failure = retain(failure, cleanup);
                }
            }
        }
        try {
            sim.instrumentController.exitInstrumentModeIfActiveForDeveloperVerification();
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        }
        try {
            sim.instrumentController.clearTargets();
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        }
        if (sim.getAttachedPcbWorkbenchCountForDeveloperVerification() != 0)
            failure = retain(failure, new IllegalStateException(
                "Q30 cleanup retained an attached workbench"));

        GeneratedExternalPowerBindings power = owner.getExternalPowerBindings();
        if (power == null) {
            failure = retain(failure, new IllegalStateException(
                "Q30 owner has no external power bindings"));
        } else {
            for (String inputId : owner.getBoard().getPowerInputIds()) {
                try {
                    power.getBinding(inputId).setConnected(false);
                } catch (Throwable cleanup) {
                    failure = retain(failure, cleanup);
                }
            }
            try {
                if (!power.areAllDisconnected())
                    failure = retain(failure, new IllegalStateException(
                        "Q30 owner power remained connected"));
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
        }
        failure = retain(failure, detachQ30PowerIfCurrent(sim, owner));

        Vector<CircuitElm> originalElements = originalOwner == null ? null :
            originalOwner.getSimulationElements();
        Vector<CircuitElm> elements = owner.getSimulationElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            CircuitElm element = elements.get(index);
            if (containsIdentity(originalElements, element)) {
                failure = retain(failure, new IllegalStateException(
                    "Q30 owner reused an original solver element"));
                continue;
            }
            try {
                element.delete();
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
        }
        clearTemporarySources(q30Elements, q30Adjustables, q30Undo, q30Redo);
        return failure;
    }

    private static Throwable quarantineQ30Owner(CirSim sim, GeneratedBoardInstance owner) {
        Throwable failure = null;
        try {
            sim.setSimRunning(false);
        } catch (Throwable cleanup) {
            failure = retain(failure, cleanup);
        }
        failure = retain(failure, detachQ30PowerIfCurrent(sim, owner));
        if (sim.getGeneratedBoardInstance() == owner) {
            try {
                sim.markGeneratedRuntimeFailure(owner,
                    failure == null ? new IllegalStateException("Q30 cleanup failed") : failure);
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
        }
        return failure;
    }

    /** Detach only the retired owner's exact power binding; preserve any successor. */
    private static Throwable detachQ30PowerIfCurrent(CirSim sim,
            GeneratedBoardInstance owner) {
        if (sim == null || owner == null) return null;
        GeneratedExternalPowerBindings retired = owner.getExternalPowerBindings();
        if (retired == null || sim.getBoardPowerController().getBindingsForDeveloperVerification() !=
                retired) return null;
        try {
            sim.getBoardPowerController().detach();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void clearTemporarySources(Vector<CircuitElm> elements,
            Vector<Adjustable> adjustables, Vector<String> undo, Vector<String> redo) {
        if (elements != null) elements.removeAllElements();
        if (adjustables != null) adjustables.removeAllElements();
        if (undo != null) undo.removeAllElements();
        if (redo != null) redo.removeAllElements();
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        if (elements == null) return false;
        for (CircuitElm element : elements)
            if (element == target) return true;
        return false;
    }

    private static Throwable retain(Throwable primary, Throwable cleanup) {
        if (primary == null) return cleanup;
        if (cleanup != null && cleanup != primary) primary.addSuppressed(cleanup);
        return primary;
    }

    /** Verify the actual pad-to-CircuitJS endpoint ownership and live readings. */
    private static void verifyLiveBindings(GeneratedBoardInstance owner) {
        Vector<CircuitElm> elements = owner.getSimulationElements();
        for (String padId : owner.getBoard().getPadIds()) {
            CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings()
                .getEndpoint(padId);
            require(endpoint instanceof CircuitPostMeasurementEndpoint,
                "pad is not bound to a CircuitJS post: " + padId);
            CircuitPostMeasurementEndpoint post =
                (CircuitPostMeasurementEndpoint)endpoint;
            require(elements.contains(post.getElement()),
                "pad endpoint is not owned by the live assembly: " + padId);
            double voltage = post.getElement().getPostVoltage(post.getPostIndex());
            require(!Double.isNaN(voltage) && !Double.isInfinite(voltage),
                "pad voltage is non-finite: " + padId);
        }
    }

    private static final class PadScan {
        final int overviewTargets;
        final int inspectableTargets;

        PadScan(int overviewTargets, int inspectableTargets) {
            this.overviewTargets = overviewTargets;
            this.inspectableTargets = inspectableTargets;
        }
    }

    private static PadScan collectTargets(CirSim sim, PcbWorkbenchController controller,
            PcbWorkbenchRenderer renderer, GeneratedBoardInstance owner,
            PcbBoardSide face, Vector<String> padLimits,
            Vector<String> overviewLimits, boolean publish) {
        renderer.setViewingFace(face);
        settle(sim);
        publishFace(face);
        int overviewCount = 0;
        int inspectableCount = 0;
        StringBuilder targets = new StringBuilder("[");
        for (String padId : owner.getBoard().getPadIds()) {
            PcbPadPlacement pad = owner.getPcbLayout().getPad(padId);
            if (!PcbCopperAccess.canProbe(pad, face)) {
                padLimits.add(padId + "=" + padProbeLimit(pad, face));
                continue;
            }
            /*
             * This is the same fit/face/occlusion/hit path used by the
             * production renderer.  It restores the viewport and face before
             * returning, so an overview miss below cannot turn into a false
             * accessibility failure.
             */
            if (!renderer.canInspectPad(sim, padId)) {
                padLimits.add(padId + "=production-fit-occluded-or-hit");
                continue;
            }
            inspectableCount++;
            Point point = renderer.getPadPoint(padId);
            if (point == null) {
                overviewLimits.add(padId + "=overview-no-screen-point");
                continue;
            }
            ProbeTarget target = controller.findProbeTarget(point.x, point.y);
            if (!(target instanceof BoardPadProbeTarget)) {
                overviewLimits.add(padId + "=overview-hit-not-board-pad");
                continue;
            }
            BoardPadProbeTarget padTarget = (BoardPadProbeTarget)target;
            if (!target.isValid()) {
                overviewLimits.add(padId + "=overview-target-invalid");
                continue;
            }
            if (!padId.equals(padTarget.getPadId())) {
                overviewLimits.add(padId + "=overview-wrong-pad-" +
                    String.valueOf(padTarget.getPadId()));
                continue;
            }
            CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings()
                .getEndpoint(padId);
            if (target.getMeasurementEndpoint() != endpoint) {
                overviewLimits.add(padId + "=overview-endpoint-mismatch");
                continue;
            }
            if (overviewCount++ != 0) targets.append(',');
            targets.append("{\"pad\":\"").append(escape(padId))
                .append("\",\"face\":\"").append(face)
                .append("\",\"x\":").append(point.x)
                .append(",\"y\":").append(point.y).append('}');
        }
        targets.append(']');
        if (publish) {
            publishTargetsForFace(face, targets.toString());
            publishPadLimits(face, jsonStrings(padLimits));
            publishOverviewLimits(face, jsonStrings(overviewLimits));
        }
        return new PadScan(overviewCount, inspectableCount);
    }

    private static boolean verifyPadDeclarations(GeneratedBoardInstance owner) {
        boolean allPlatedThroughHole = true;
        for (String padId : owner.getBoard().getPadIds()) {
            PcbPadPlacement pad = owner.getPcbLayout().getPad(padId);
            require(pad != null, "missing routed pad placement: " + padId);
            if (pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    pad.getExposure() != PcbCopperAccess.Exposure.EXPOSED)
                allPlatedThroughHole = false;
        }
        return allPlatedThroughHole;
    }

    private static String padProbeLimit(PcbPadPlacement pad, PcbBoardSide face) {
        if (pad == null) return "missing-placement";
        PcbCopperLayer layer = PcbCopperLayer.forFace(face);
        if (!PcbCopperAccess.hasCopper(pad, layer))
            return "no-" + layer + "-copper";
        if (pad.getExposure() != PcbCopperAccess.Exposure.EXPOSED)
            return "exposure=" + pad.getExposure();
        return "production-hit-limit";
    }

    private static final class CopperMetadata {
        final String topTrace, bottomTrace, viaSurface, viaPad;

        CopperMetadata(String topTrace, String bottomTrace, String viaSurface,
                String viaPad) {
            this.topTrace = topTrace;
            this.bottomTrace = bottomTrace;
            this.viaSurface = viaSurface;
            this.viaPad = viaPad;
        }

        String toJson() {
            return "{\"topTrace\":\"" + escape(topTrace) +
                "\",\"bottomTrace\":\"" + escape(bottomTrace) +
                "\",\"viaSurface\":\"" + escape(viaSurface) +
                "\",\"viaPad\":\"" + escape(viaPad) + "\"}";
        }
    }

    private static final class ViaSelection {
        final String topSurface, bottomSurface;
        final String holeId;

        ViaSelection(String holeId, String topSurface, String bottomSurface) {
            this.holeId = holeId;
            this.topSurface = topSurface;
            this.bottomSurface = bottomSurface;
        }
    }

    /**
     * Exercise the selected conductor graph through the production renderer.
     * The checks deliberately use generic routed surfaces and plated holes;
     * no Q30 pad, net, or route identity is hardcoded here.
     */
    private static CopperMetadata verifyCopperFaceTruth(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            GeneratedBoardInstance owner) {
        PcbConductorGraph.Snapshot copper = owner.getCurrentConductorSnapshot();
        require(copper != null, "developer bench has no current conductor graph");
        PcbConductorGraph.Surface topTrace = findProbeableTrace(sim, controller,
            renderer, owner, copper, PcbBoardSide.TOP);
        PcbConductorGraph.Surface bottomTrace = findProbeableTrace(sim, controller,
            renderer, owner, copper, PcbBoardSide.BOTTOM);
        require(topTrace != null, "no exposed top-only trace is reachable through the production renderer");
        require(bottomTrace != null, "no exposed underside-only trace is reachable through the production renderer");
        verifyTraceSurface(sim, controller, renderer, owner, copper, topTrace,
            PcbBoardSide.TOP);
        verifyTraceSurface(sim, controller, renderer, owner, copper, bottomTrace,
            PcbBoardSide.BOTTOM);

        ViaSelection via = findProbeableVia(sim, controller, renderer, owner, copper);
        verifyViaSurface(sim, controller, renderer, owner, copper, via);
        return new CopperMetadata(topTrace.id, bottomTrace.id,
            via.topSurface + "/" + via.bottomSurface, viaPad(copper, via.topSurface));
    }

    private static PcbConductorGraph.Surface findProbeableTrace(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            GeneratedBoardInstance owner, PcbConductorGraph.Snapshot copper,
            PcbBoardSide face) {
        renderer.setViewingFace(face);
        settle(sim);
        for (PcbConductorGraph.Surface surface : copper.getGraph().getSurfaces()) {
            if (surface.edgeId == null || surface.padId != null ||
                    surface.layer != PcbCopperLayer.forFace(face) ||
                    !surface.canProbe(face)) continue;
            PcbConductorGraph.Edge edge = copper.getGraph().getEdges().get(surface.edgeId);
            if (edge == null || edge.kind != PcbConductorGraph.Kind.TRACE ||
                    !PcbCopperProbeAccess.available(copper, surface, face)) continue;
            Point point = renderer.getCopperPoint(surface.id);
            if (point == null) continue;
            ProbeTarget target = controller.findProbeTarget(point.x, point.y);
            if (target instanceof BoardCopperProbeTarget && target.isValid())
                return surface;
        }
        return null;
    }

    private static void verifyTraceSurface(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            GeneratedBoardInstance owner, PcbConductorGraph.Snapshot copper,
            PcbConductorGraph.Surface surface, PcbBoardSide face) {
        renderer.setViewingFace(face);
        settle(sim);
        require(PcbCopperProbeAccess.available(copper, surface, face),
            "selected trace is not available on its declared face: " + surface.id);
        Point point = renderer.getCopperPoint(surface.id);
        require(point != null, "selected trace has no visible copper marker: " + surface.id);
        ProbeTarget target = controller.findProbeTarget(point.x, point.y);
        require(target instanceof BoardCopperProbeTarget,
            "production hit did not resolve selected trace: " + surface.id);
        BoardCopperProbeTarget copperTarget = (BoardCopperProbeTarget)target;
        require(target.isValid(), "selected trace target is invalid: " + surface.id);
        require(surface.id.equals(copperTarget.getSurfaceId()),
            "production hit resolved the wrong trace: " + surface.id);
        String padId = viaPad(copper, surface.id);
        require(padId != null, "selected trace has no physical solver island pad: " + surface.id);
        require(target.getMeasurementEndpoint() == owner.getSimulationBindings()
            .getEndpoint(padId), "selected trace changed its solver endpoint: " + surface.id);
        ProbeTarget captured = target;
        renderer.setViewingFace(face.opposite());
        settle(sim);
        require(!renderer.canProbeCopper(surface.id),
            "selected trace remained probeable on the opposite face: " + surface.id);
        require(!captured.isValid(), "captured trace target remained valid after face flip: " +
            surface.id);
        require(captured.getMeasurementEndpoint() == null,
            "captured trace target retained a solver endpoint after face flip: " + surface.id);
    }

    private static ViaSelection findProbeableVia(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            GeneratedBoardInstance owner, PcbConductorGraph.Snapshot copper) {
        StringBuilder blocked = new StringBuilder();
        PcbViewport viewport = renderer.getViewport();
        PcbViewport.State savedViewport = viewport.capture();
        PcbBoardSide savedFace = renderer.getViewingFace();
        try {
            for (PcbBoardHole hole : owner.getPcbLayout().getHoles()) {
                if (hole.kind == PcbBoardHole.Kind.NON_PLATED ||
                        hole.exposure != PcbCopperAccess.Exposure.EXPOSED) continue;
                String top = "hole/" + PcbConductorGraph.faceKey(hole.id, PcbCopperLayer.TOP);
                String bottom = "hole/" + PcbConductorGraph.faceKey(hole.id, PcbCopperLayer.BOTTOM);
                PcbConductorGraph.Surface topSurface = copper.getGraph().getSurface(top);
                PcbConductorGraph.Surface bottomSurface = copper.getGraph().getSurface(bottom);
                BoardCopperProbeTarget topTarget = probeViaAtLocalFit(sim, controller,
                    renderer, copper, hole, topSurface, PcbBoardSide.TOP);
                if (topTarget == null) {
                    appendBlocked(blocked, hole.id + "=TOP-production-hit");
                    continue;
                }
                CircuitMeasurementEndpoint topEndpoint = topTarget.getMeasurementEndpoint();
                BoardCopperProbeTarget bottomTarget = probeViaAtLocalFit(sim, controller,
                    renderer, copper, hole, bottomSurface, PcbBoardSide.BOTTOM);
                if (bottomTarget == null) {
                    appendBlocked(blocked, hole.id + "=BOTTOM-production-hit");
                    continue;
                }
                String topPad = PcbCopperProbeAccess.connectedPad(copper, top);
                String bottomPad = PcbCopperProbeAccess.connectedPad(copper, bottom);
                require(topPad != null && topPad.equals(bottomPad),
                    "plated via does not resolve to one solver island: " + hole.id);
                CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings()
                    .getEndpoint(topPad);
                CircuitMeasurementEndpoint bottomEndpoint = bottomTarget.getMeasurementEndpoint();
                require(topEndpoint == endpoint,
                    "top plated via changed its solver endpoint: " + hole.id);
                require(bottomEndpoint == endpoint,
                    "bottom plated via changed its solver endpoint: " + hole.id);
                return new ViaSelection(hole.id, top, bottom);
            }
        } finally {
            renderer.setViewingFace(savedFace);
            viewport.restore(savedViewport);
            renderer.updateProjection();
        }
        require(false, "no plated via land is reachable on both faces: " + blocked);
        return null;
    }

    private static void verifyViaSurface(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            GeneratedBoardInstance owner, PcbConductorGraph.Snapshot copper,
            ViaSelection via) {
        String padId = viaPad(copper, via.topSurface);
        require(padId != null, "selected plated via has no physical solver island pad: " +
            via.holeId);
        CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(padId);
        PcbBoardHole hole = findHole(owner, via.holeId);
        require(hole != null, "selected plated via has no physical hole: " + via.holeId);
        PcbViewport viewport = renderer.getViewport();
        PcbViewport.State savedViewport = viewport.capture();
        PcbBoardSide savedFace = renderer.getViewingFace();
        try {
            PcbConductorGraph.Surface topSurface = copper.getGraph().getSurface(via.topSurface);
            BoardCopperProbeTarget topTarget = probeViaAtLocalFit(sim, controller, renderer,
                copper, hole, topSurface, PcbBoardSide.TOP);
            require(topTarget != null,
                "production hit did not resolve top plated via land: " + via.holeId);
            require(via.topSurface.equals(topTarget.getSurfaceId()),
                "production hit resolved the wrong top plated via land: " + via.holeId);
            require(topTarget.getMeasurementEndpoint() == endpoint,
                "top plated via changed its solver endpoint: " + via.holeId);
            ProbeTarget capturedTop = topTarget;

            PcbConductorGraph.Surface bottomSurface = copper.getGraph().getSurface(via.bottomSurface);
            BoardCopperProbeTarget bottomTarget = probeViaAtLocalFit(sim, controller, renderer,
                copper, hole, bottomSurface, PcbBoardSide.BOTTOM);
            require(bottomTarget != null,
                "production hit did not resolve bottom plated via land: " + via.holeId);
            require(via.bottomSurface.equals(bottomTarget.getSurfaceId()),
                "production hit resolved the wrong bottom plated via land: " + via.holeId);
            require(bottomTarget.getMeasurementEndpoint() == endpoint,
                "bottom plated via changed its solver endpoint: " + via.holeId);
            require(!capturedTop.isValid(), "captured plated via target remained valid after face flip: " +
                via.holeId);
            require(capturedTop.getMeasurementEndpoint() == null,
                "captured plated via target retained a solver endpoint after face flip: " +
                    via.holeId);
        } finally {
            renderer.setViewingFace(savedFace);
            viewport.restore(savedViewport);
            renderer.updateProjection();
        }
    }

    /**
     * The production renderer rejects a marker that rasterizes inside a drill
     * rectangle.  Fit the same bounded local inspection viewport used by pad
     * admission, then ask the production hit resolver for the exact surface.
     * No synthetic target or direct marker acceptance is allowed here.
     */
    private static BoardCopperProbeTarget probeViaAtLocalFit(CirSim sim,
            PcbWorkbenchController controller, PcbWorkbenchRenderer renderer,
            PcbConductorGraph.Snapshot copper, PcbBoardHole hole,
            PcbConductorGraph.Surface surface, PcbBoardSide face) {
        if (hole == null || surface == null ||
                !PcbCopperProbeAccess.available(copper, surface, face)) return null;
        renderer.setViewingFace(face);
        PcbViewport viewport = renderer.getViewport();
        viewport.fit(new Rectangle(hole.x - 80, hole.y - 80, 160, 160));
        renderer.updateProjection();
        settle(sim);
        Point point = renderer.getCopperPoint(surface.id);
        if (point == null) return null;
        ProbeTarget target = controller.findProbeTarget(point.x, point.y);
        if (!(target instanceof BoardCopperProbeTarget) || !target.isValid()) return null;
        BoardCopperProbeTarget copperTarget = (BoardCopperProbeTarget)target;
        return surface.id.equals(copperTarget.getSurfaceId()) ? copperTarget : null;
    }

    private static PcbBoardHole findHole(GeneratedBoardInstance owner, String holeId) {
        for (PcbBoardHole hole : owner.getPcbLayout().getHoles())
            if (hole.id.equals(holeId)) return hole;
        return null;
    }

    private static String viaPad(PcbConductorGraph.Snapshot copper, String surfaceId) {
        return PcbCopperProbeAccess.connectedPad(copper, surfaceId);
    }

    private static void appendBlocked(StringBuilder blocked, String text) {
        if (blocked.length() != 0) blocked.append(',');
        blocked.append(text);
    }

    private static void settle(CirSim sim) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, "Q30 workbench");
    }

    private static void publishReport(CirSim sim, GeneratedBoardInstance owner, Rb30Plan plan,
            PcbBoardLayout layout, MediumBoardPhysicalPolicy.Statistics statistics,
            PcbWorkbenchRenderer renderer, boolean restored, boolean retained,
            String status, boolean visualPass, int topTargets, int bottomTargets,
            int topInspectable, int bottomInspectable,
            Vector<String> topPadLimits, Vector<String> bottomPadLimits,
            Vector<String> topOverviewLimits, Vector<String> bottomOverviewLimits,
            CopperMetadata copperMetadata) {
        Rectangle outline = layout.getBoardOutline();
        StringBuilder report = new StringBuilder("{\"schema\":1,\"status\":\"")
            .append(escape(status)).append("\",\"visualPass\":")
            .append(visualPass);
        report.append(",\"family\":\"").append(escape(owner.getCircuitFamilyId()))
            .append("\",\"seed\":\"").append(Long.toString(owner.getSeed()))
            .append("\",\"topology\":\"").append(escape(plan.topology()))
            .append("\",\"physicalPolicy\":\"")
            .append(escape(statistics.policyIdentity))
            .append("\",\"layoutSeed\":\"").append(Long.toString(plan.layoutSeed))
            .append("\",\"routingSeed\":\"").append(Long.toString(plan.routingSeed))
            .append("\",\"selectedRoutePolicy\":\"").append(escape(String.valueOf(
                statistics.selectedRoutePolicy)))
            .append("\",\"route\":\"").append(escape(String.valueOf(
                statistics.selectedRoutePolicy)))
            .append("\",\"layout\":{\"x\":").append(outline.x)
            .append(",\"y\":").append(outline.y)
            .append(",\"width\":").append(outline.width)
            .append(",\"height\":").append(outline.height).append('}')
            .append(",\"packages\":").append(owner.getBoard().getComponentIds().size())
            .append(",\"pads\":").append(owner.getBoard().getPadIds().size())
            .append(",\"topTargets\":").append(topTargets)
            .append(",\"bottomTargets\":").append(bottomTargets)
            .append(",\"topInspectable\":").append(topInspectable)
            .append(",\"bottomInspectable\":").append(bottomInspectable)
            .append(",\"padLimits\":{\"TOP\":").append(jsonStrings(topPadLimits))
            .append(",\"BOTTOM\":").append(jsonStrings(bottomPadLimits)).append('}')
            .append(",\"overviewLimits\":{\"TOP\":")
            .append(jsonStrings(topOverviewLimits)).append(",\"BOTTOM\":")
            .append(jsonStrings(bottomOverviewLimits)).append('}')
            .append(",\"face\":\"").append(renderer.getViewingFace())
            .append("\",\"runtimeAssertions\":").append(checks)
            .append(",\"ownerRestored\":").append(restored)
            .append(",\"prototypeRetained\":").append(retained)
            .append(",\"normalAdmission\":false")
            .append(",\"solverMaxTimeStep\":").append(sim.maxTimeStep)
            .append(",\"solverMinTimeStep\":").append(sim.minTimeStep)
            .append(",\"solverAdaptive\":").append(sim.adjustTimeStep)
            .append(",\"hypothesisCount\":").append(owner.getFaultCandidates().size())
            .append(",\"selectedFault\":\"").append(owner.getFaultBinding().getFault().getId()).append('"')
            .append(",\"constructionMillis\":").append(constructionMillis)
            .append(",\"routingMillis\":").append(routingMillis)
            .append(",\"serviceMillis\":").append(serviceMillis)
            .append(",\"qualificationMillis\":").append(System.currentTimeMillis() - qualificationStarted)
            .append(",\"copper\":")
            .append(copperMetadata == null ? "null" : copperMetadata.toJson())
            .append('}');
        publish(report.toString());
    }

    private static String jsonStrings(Vector<String> values) {
        StringBuilder result = new StringBuilder("[");
        if (values != null) for (String value : values) {
            if (result.length() != 1) result.append(',');
            result.append('"').append(escape(value)).append('"');
        }
        return result.append(']').toString();
    }

    private static void publishFailure(long seed, Rb30Plan plan,
            MediumBoardPhysicalPolicy.Result routed, Throwable failure,
            String phase, boolean restored, boolean retained) {
        publishFailure(Long.toString(seed), plan, routed, failure, phase, restored, retained);
    }

    private static void publishFailure(String seed, Rb30Plan plan,
            MediumBoardPhysicalPolicy.Result routed, Throwable failure,
            String phase, boolean restored, boolean retained) {
        StringBuilder report = new StringBuilder("{\"schema\":1,\"status\":\"FAIL\"");
        report.append(",\"visualPass\":false,\"seed\":\"")
            .append(escape(seed)).append('"');
        if (plan != null) report.append(",\"topology\":\"")
            .append(escape(plan.topology())).append('"');
        if (routed != null && routed.getStatistics() != null) {
            report.append(",\"physicalPolicy\":\"").append(escape(
                routed.getStatistics().policyIdentity)).append('"');
            report.append(",\"route\":\"").append(escape(String.valueOf(
                routed.getStatistics().selectedRoutePolicy))).append('"');
        }
        report.append(",\"phase\":\"").append(escape(phase)).append('"');
        report.append(",\"blocker\":\"").append(escape(message(failure)))
            .append("\",\"ownerRestored\":").append(restored)
            .append(",\"prototypeRetained\":").append(retained)
            .append(",\"normalAdmission\":false}");
        publish(report.toString());
    }

    private static String message(Throwable failure) {
        if (failure == null) return "unknown failure";
        String text = failure.getMessage();
        return text == null || text.length() == 0 ? failure.toString() : text;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error) throw (Error)failure;
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        throw new IllegalStateException("Q30 developer workbench failed", failure);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-report", report);
    }-*/;

    private static native void publishCleanup(boolean restored, boolean retained) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-cleanup",
            JSON.stringify({ownerRestored:restored, prototypeRetained:retained}));
    }-*/;

    private static native void publishFace(PcbBoardSide face) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-face", face.toString());
    }-*/;

    private static native void publishTargetsForFace(PcbBoardSide face, String targets) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-targets-" + face, targets);
    }-*/;

    private static native void publishPadLimits(PcbBoardSide face, String limits) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-pad-limits-" + face, limits);
    }-*/;

    private static native void publishOverviewLimits(PcbBoardSide face, String limits) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-overview-limits-" + face, limits);
    }-*/;

    private static native void publishTargets(GeneratedBoardInstance owner,
            PcbWorkbenchRenderer renderer, int top, int bottom,
            int topInspectable, int bottomInspectable) /*-{
        var topTargets = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-targets-TOP") || "[]");
        var bottomTargets = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-targets-BOTTOM") || "[]");
        var topLimits = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-pad-limits-TOP") || "[]");
        var bottomLimits = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-pad-limits-BOTTOM") || "[]");
        var topOverviewLimits = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-overview-limits-TOP") || "[]");
        var bottomOverviewLimits = JSON.parse($doc.documentElement.getAttribute(
            "data-tsj-q30-overview-limits-BOTTOM") || "[]");
        $doc.documentElement.setAttribute("data-tsj-q30-targets",
            JSON.stringify(topTargets.concat(bottomTargets)));
        $doc.documentElement.setAttribute("data-tsj-q30-target-counts",
            JSON.stringify({family:"RB30_CONTROL", top:top, bottom:bottom,
                topInspectable:topInspectable, bottomInspectable:bottomInspectable,
                topPadLimits:topLimits, bottomPadLimits:bottomLimits,
                topOverviewLimits:topOverviewLimits,
                bottomOverviewLimits:bottomOverviewLimits,
                face:renderer.@com.lushprojects.circuitjs1.client.PcbWorkbenchRenderer::getViewingFace()().toString()}));
    }-*/;

    private Q30DeveloperWorkbenchVerifier() { }
}
