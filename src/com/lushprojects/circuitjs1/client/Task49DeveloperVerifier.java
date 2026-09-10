package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Maintained compiled qualification for the repeated controlled-indicator
 * composition.  The verifier consumes the resolved assembly plan and its
 * execution provider; it does not retain a second driver/load model.
 */
final class Task49DeveloperVerifier {
    /* The parity corpus includes signed boundaries and values above the
     * JavaScript exact-integer range.  Solver-heavy admission is still
     * delegated to the normal candidate path for every selected seed. */
    private static final long[] SOLVED_SEEDS = { -1L, 0L, 1L,
        Long.MIN_VALUE };
    private static final long[] VALUE_SEEDS = { -1L, 0L, 1L, 2L, 3L,
        Long.MIN_VALUE, Long.MAX_VALUE, 9007199254740993L,
        -9007199254740993L };
    private static final long[] ROLE_SEEDS = { -1L, 0L, 1L, 2L,
        Long.MIN_VALUE, Long.MAX_VALUE };
    private static final Vector<GeneratedBoardInstance> candidates =
        new Vector<GeneratedBoardInstance>();
    private static final Vector<GeneratedBoardInstance> disposed =
        new Vector<GeneratedBoardInstance>();
    private static int assertions;
    private static int measurements;
    private static String phase;

    private Task49DeveloperVerifier() { }

    static String verify(CirSim sim, String seedText, boolean forcedFailure) {
        return verifyWithProtocol(sim, seedText, forcedFailure, "TSJ-TASK49-2");
    }

    /** Compatibility entry used by the historical Task 48 URL. */
    static String verifyForTask48(CirSim sim, String seedText,
            boolean forcedFailure) {
        return verifyWithProtocol(sim, seedText, forcedFailure, "TSJ-TASK48-2");
    }

    private static String verifyWithProtocol(CirSim sim, String seedText,
            boolean forcedFailure, String protocol) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route is required");
        if (forcedFailure)
            throw new AssertionError("task49-explicit-assertion-canary");
        assertions = 0;
        measurements = 0;
        phase = "entry";
        candidates.clear();
        disposed.clear();
        String canonicalSeed = seedText == null ? "0" : seedText;
        long requestedSeed = Long.parseLong(canonicalSeed);
        require(Long.toString(requestedSeed).equals(canonicalSeed),
            "noncanonical signed seed");

        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        require(originalOwner != null && sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady() &&
            sim.isGeneratedRuntimeSettled(), "settled original owner is required");
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        boolean priorQuickPlay = sim.quickPlayActive;
        QuickPlaySession priorSession = sim.quickPlaySession;
        StringBuilder cases = new StringBuilder();
        StringBuilder valueCases = new StringBuilder();
        Vector<String> roleVectors = new Vector<String>();
        Throwable primary = null;
        try {
            for (long seed : VALUE_SEEDS) {
                String[] result;
                if (isSolvedSeed(seed)) {
                    restore(sim, original, originalOwner, priorQuickPlay, priorSession);
                    result = verifySeed(sim, seed);
                } else {
                    result = pureValueCase(seed);
                }
                if (result[0] != null) {
                    if (cases.length() != 0) cases.append(',');
                    cases.append(result[0]);
                }
                if (valueCases.length() != 0) valueCases.append(',');
                valueCases.append(result[2] == null ? result[0] : result[2]);
                if (isRoleSeed(seed)) {
                    if (roleVectors.size() >= ROLE_SEEDS.length)
                        throw new IllegalStateException("role/value corpus over-produced role records");
                    roleVectors.add(result[1]);
                }
            }
            require(cases.length() != 0 && valueCases.length() != 0 &&
                roleVectors.size() == ROLE_SEEDS.length,
                "role/value corpus did not retain one selection record per seed");
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String construction = verifyConstructionFailures(sim, requestedSeed);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            String succession = verifySuccession(sim, requestedSeed,
                requestedSeed == Long.MAX_VALUE ? 0L : requestedSeed + 1L);
            restore(sim, original, originalOwner, priorQuickPlay, priorSession);
            return "{\"protocol\":\"" + protocol + "\",\"status\":\"PASS\",\"requestedSeed\":" +
                q(canonicalSeed) + ",\"cases\":[" + cases +
                "],\"valueCases\":[" + valueCases +
                "],\"construction\":" +
                construction + ",\"succession\":" + succession +
                ",\"roleSelectionVectors\":[" + joinQuoted(roleVectors) +
                "],\"assertions\":" + assertions + ",\"measurementCases\":" +
                measurements + ",\"originalOwnerRestored\":true,\"candidateCleanup\":\"PASS\"}";
        } catch (Throwable problem) {
            primary = new IllegalStateException("Task49: " + phase + ": " +
                problem.getMessage(), problem);
            rethrow(primary);
            return null;
        } finally {
            FreshGeneratedRuntimeInstallation.setFailureForDeveloperVerification(null);
            ResistorMutationScope.clearFailureHookForDeveloperVerification();
            ResistorMutationScope.clearAbortFailureHookForDeveloperVerification();
            try {
                restore(sim, original, originalOwner, priorQuickPlay, priorSession);
                for (GeneratedBoardInstance candidate : candidates)
                    dispose(sim, candidate, originalOwner);
                original.assertRestored(sim);
            } catch (Throwable cleanup) {
                if (primary != null)
                    throw new IllegalStateException("Task49 original failure: " +
                        primary.getMessage() + "; cleanup failure: " +
                        cleanup.getMessage(), primary);
                rethrow(cleanup);
            } finally {
                candidates.clear();
                disposed.clear();
            }
        }
    }

    /** Return the JSON case and the stable role-selection evidence separately. */
    private static String[] verifySeed(CirSim sim, long seed) {
        phase = "seed " + seed + " declaration/replay";
        int assertionStart = assertions;
        GeneratedBoardInstance before = sim.getGeneratedBoardInstance();
        String circuitBefore = sim.dumpCircuit();
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forControlledIndicator(seed);
        ChallengeDescriptor descriptor = ChallengeDescriptor.parse(
            request.getDescriptor().toCanonical());
        require(descriptor.getRootSeed() == seed, "descriptor lost signed-long seed");

        BoundedGeneratedBoardAssembler.Result direct = assemble(request);
        BoundedGeneratedBoardAssembler.Result replay = assemble(
            BoundedAssemblyRequest.forControlledIndicator(descriptor));
        BoundedGeneratedBoardAssembler.Result reordered = assemble(
            BoundedAssemblyRequest.reorderedInputs(descriptor,
                reverse(request.getBlocks()), reverse(request.getConnections()),
                reverse(request.getDeviceAdapters())));
        require(before == sim.getGeneratedBoardInstance() &&
            circuitBefore.equals(sim.dumpCircuit()),
            "private composition changed the live owner");
        FreshGeneratedRuntimeInstallation.requireDisjoint(direct.getInstance(),
            replay.getInstance());
        require(snapshot(direct).equals(snapshot(replay)),
            "descriptor replay changed the runtime construction");
        require(snapshot(direct).equals(snapshot(reordered)),
            "reordered request changed the runtime construction");
        BoundedAssemblyPlan plan = direct.getPlan();
        require(plan.isControlledIndicator() && plan.getChannels().size() == 2,
            "controlled plan did not retain two declared channels");
        require(plan.getDecisionOwners().size() == 4,
            "controlled plan did not retain four physical fault owners");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ComposedBlockContribution load = plan.getBlocks().get(channel.getLoadKey());
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                load == null ? null : load.getResolvedValueRecipe();
            require(recipe != null && containsCurrentCatalogEntry(recipe),
                "channel recipe is not catalog-backed: " + channel.getKey());
        }

        installNormal(sim, direct.getInstance());
        GeneratedBoardInstance board = direct.getInstance();
        verifyNormalAdmission(sim, board);
        ControlledIndicatorDeviceBehavior behavior = behavior(board);
        require(board.getFaultLocus() != null && board.getFaultBinding() != null &&
            board.getFaultBinding().isApplied(),
            "normal admission did not publish an applied physical fault");
        behavior.verifyFaulted(board, sim.getBoardModificationController(),
            BoardPowerState.POWERED);
        require(!retest(sim), "unrepaired indicator passed customer retest");

        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        challenge.beginDeveloperVerificationScope();
        require(challenge.getFaultController().clearForDeveloperVerification(),
            "selected fault did not clear");
        settle(sim);
        behavior.verifyHealthy(board, BoardPowerState.POWERED);
        verifyIndependentChannels(sim, board);
        phase = "seed " + seed + " physical correspondence";
        String physical = Task43PPhysicalTruthDeveloperVerifier
            .verifyControlledIndicatorComposition(sim);
        phase = "seed " + seed + " per-owner mutation and restore";
        Task48MutationDeveloperVerifier.Receipt mutation =
            Task48MutationDeveloperVerifier.verifyDetailed(sim);
        if (seed == 0L) {
            phase = "seed " + seed + " measurements";
            verifyMeasurements(sim, board);
            behavior.verifyHealthy(board, BoardPowerState.POWERED);
        }
        // The positive retest can complete this candidate. Run it last, before
        // the outer proof restores its separate original owner.
        phase = "seed " + seed + " support and retest";
        String support = verifyBrokenSupport(sim, board);

        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
            Task41DeveloperVerifier.getLastControlledAdmissionEvidenceForDeveloperVerification();
        String role = roleVector(plan, seed);
        String values = channelValues(plan);
        String valueCase = pureValueCase(request, plan);
        String result = "{\"seed\":" + q(Long.toString(seed)) +
            ",\"channels\":[" + values +
            "],\"faultDecision\":" + q(plan.getFaultDecisionKey()) +
            ",\"faultOwner\":" + q(board.getFaultLocus().getComponentId()) +
            ",\"freshReplay\":true,\"inputOrderIndependent\":true" +
            ",\"normalAdmission\":" + admissionReceipt(evidence) +
            ",\"physicalCorrespondence\":" + physical +
            ",\"independentControls\":true,\"faultRepairs\":" +
            mutation.faultRepairsJson + ",\"support\":" + support +
            ",\"mutations\":" + mutation.json +
            ",\"repairReachable\":true,\"assertions\":" +
            (assertions - assertionStart) + "}";
        return new String[] { result, role, valueCase };
    }

    private static String[] pureValueCase(long seed) {
        BoundedAssemblyRequest request =
            BoundedAssemblyRequest.forControlledIndicator(seed);
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        require(plan.isControlledIndicator() && plan.getChannels().size() == 2,
            "pure value case lost controlled channels");
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                plan.getBlocks().get(channel.getLoadKey()).getResolvedValueRecipe();
            require(recipe != null && containsCurrentCatalogEntry(recipe),
                "pure value case is not catalog-backed: " + channel.getKey());
        }
        return new String[] { null, roleVector(plan, seed), pureValueCase(request, plan) };
    }

    private static String pureValueCase(BoundedAssemblyRequest request,
            BoundedAssemblyPlan plan) {
        return "{\"seed\":" + q(Long.toString(request.getDescriptor().getRootSeed())) +
            ",\"channels\":[" + channelValues(plan) +
            "],\"faultDecision\":" + q(plan.getFaultDecisionKey()) +
            ",\"descriptor\":" + q(request.getDescriptor().toCanonical()) + "}";
    }

    private static String channelValues(BoundedAssemblyPlan plan) {
        StringBuilder values = new StringBuilder();
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            if (values.length() != 0) values.append(',');
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                plan.getBlocks().get(channel.getLoadKey()).getResolvedValueRecipe();
            values.append("{\"channel\":").append(q(channel.getKey()))
                .append(",\"provider\":").append(q(plan.getBlocks()
                    .get(channel.getDriverKey()).getProviderTypeId()))
                .append(",\"catalog\":").append(q(recipe.getCatalogEntryId()))
                .append(",\"resistanceOhms\":").append(recipe.getResistanceOhms())
                .append(",\"resistanceMinimumOhms\":").append(
                    recipe.getResistanceMinimumOhms())
                .append(",\"resistanceMaximumOhms\":").append(
                    recipe.getResistanceMaximumOhms())
                .append(",\"minimumCurrentAmps\":").append(
                    recipe.getMinimumCurrentAmps())
                .append(",\"maximumCurrentAmps\":").append(
                    recipe.getMaximumCurrentAmps())
                .append(",\"guardedPowerWatts\":").append(
                    recipe.getGuardedPowerWatts())
                .append(",\"tolerancePercent\":").append(recipe.getTolerancePercent())
                .append('}');
        }
        return values.toString();
    }

    private static void verifyNormalAdmission(CirSim sim,
            GeneratedBoardInstance board) {
        require(!board.isDeveloperOnlyFaultRoute() &&
            !board.getDiagnosticSolvabilityContract().isDeveloperFixture() &&
            !board.getDiagnosticSolvabilityContract().getPlans().isEmpty(),
            "normal route used a developer fixture");
        BoundedAssemblyPlan plan = plan(board);
        require(board.getFaultCandidates().size() == plan.getDecisionOwners().size() &&
            board.getAdmittedFaultPhysicalOwnerCount() == plan.getDecisionOwners().size(),
            "normal route lacks the complete physical owner population");
        require(board.getBoard().getComponentIds().size() == 15 &&
            board.getBoard().getPadIds().size() == 32 &&
            board.getBoard().getPowerInputIds().size() == 3,
            "controlled repeated inventory cardinality changed");
        require(!GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
            !FreshGeneratedRuntimeInstallation.isInProgress(sim),
            "private admission guard leaked");
        Vector<GeneratedDiagnosticSolvabilityEvidence> executed =
            Task41DeveloperVerifier.getLastControlledAdmissionEvidenceForDeveloperVerification();
        require(executed.size() == plan.getDecisionOwners().size(),
            "normal admission did not execute every declared owner hypothesis");
        for (GeneratedDiagnosticSolvabilityEvidence proof : executed) {
            require(proof.getSeed() == board.getSeed() &&
                proof.getFamilyId().equals(board.getCircuitFamilyId()) &&
                proof.getMeasuredExecutionDepth() > 0 &&
                !proof.getSolverSamples().isEmpty() && proof.isRepairReachable() &&
                proof.isCustomerRetestPassed() && proof.isStateIsolated() &&
                proof.hasUnaffectedFunctionRetestObservation(),
                "normal admission receipt lacks executed repair/retest evidence");
            boolean ownerSeen = false;
            for (String owner : plan.getDecisionOwners().values())
                ownerSeen |= proof.getRouteId().endsWith("/" + owner);
            require(ownerSeen, "admission receipt names an undeclared physical owner");
        }
        GeneratedChallengeLifecycleEvidence lifecycle =
            sim.getGeneratedChallengeController().getLifecycleEvidence();
        require(lifecycle.healthyGenerationInstalled &&
            lifecycle.healthyGraphAnalyzedAfterTimeAdvance && lifecycle.healthyFamilyValidated &&
            lifecycle.selectedFaultApplied && lifecycle.selectedFaultValidated &&
            lifecycle.faultedGraphAnalyzedAfterTimeAdvance &&
            lifecycle.scenarioCompatibilityValidated && lifecycle.readyAfterValidation,
            "normal lifecycle omitted healthy or faulted solver verification");
        board.getPhysicalBoardRuntime().validateSupportedCompositionProviders();
        invariant(sim);
    }

    private static void verifyIndependentChannels(CirSim sim,
            GeneratedBoardInstance board) {
        BoundedAssemblyPlan plan = plan(board);
        ControlledIndicatorDeviceBehavior behavior = behavior(board);
        behavior.setAllChannels(sim, false);
        settle(sim);
        for (ControlledIndicatorChannel selected : plan.getChannels()) {
            board.invokeOperation(selected.getHighOperationId(), sim);
            settle(sim);
            for (ControlledIndicatorChannel channel : plan.getChannels()) {
                ControlledIndicatorBlockContributions.Provider provider =
                    ControlledIndicatorBlockContributions.resolve(plan.getBlocks()
                        .get(channel.getDriverKey()).getProviderTypeId(),
                        plan.getBlocks().get(channel.getDriverKey()).getProviderVersion());
                ControlledIndicatorChannelObservation observation =
                    new ControlledIndicatorChannelObservation(board, plan, channel);
                ControlledIndicatorDriverObservation driver =
                    ControlledIndicatorDriverObservations.forProvider(provider.getTypeId());
                if (selected.getKey().equals(channel.getKey()))
                    require(driver.isHealthyOn(observation),
                        "selected channel did not respond to its own HIGH operation");
                else
                    require(driver.isHealthyOff(observation),
                        "unselected channel changed during independent HIGH operation");
            }
            board.invokeOperation(selected.getLowOperationId(), sim);
            settle(sim);
        }
        require(!behavior.isCommandedOn(), "independent proof left a channel HIGH");
    }

    private static void verifyMeasurements(CirSim sim, GeneratedBoardInstance board) {
        GeneratedDiagnosticExecutionProvider provider = executionProvider(board);
        power(sim, BoardPowerState.UNPOWERED, board);
        require(board.getExternalPowerBindings().areAllDisconnected(),
            "power off did not isolate every external source");
        String[][] pairs = provider.getIsolationPairs();
        require(pairs.length == plan(board).getDecisionOwners().size(),
            "isolation pairs do not cover every serviceable owner");
        for (String[] pair : pairs) {
            require(pair != null && pair.length == 2,
                "malformed provider isolation pair");
            CircuitMeasurementEndpoint redEndpoint = board.getSimulationBindings()
                .getEndpoint(pair[0]);
            CircuitMeasurementEndpoint blackEndpoint = board.getSimulationBindings()
                .getEndpoint(pair[1]);
            require(redEndpoint instanceof CircuitPostMeasurementEndpoint &&
                blackEndpoint instanceof CircuitPostMeasurementEndpoint,
                "isolation pair lost solver endpoint");
            Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList);
            double measured = sim.measureResistance((CircuitPostMeasurementEndpoint) redEndpoint,
                (CircuitPostMeasurementEndpoint) blackEndpoint);
            require(finite(measured) && measured > 0.0,
                "isolated owner measurement is not solver-backed");
            require(before.equals(sim.elmList) && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                board.getExternalPowerBindings().areAllDisconnected(),
                "active measurement left source or solver residue");
            measurements++;
        }
        power(sim, BoardPowerState.POWERED, board);
        behavior(board).verifyHealthy(board, BoardPowerState.POWERED);
    }

    private static String admissionReceipt(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        StringBuilder result = new StringBuilder("{\"status\":\"EXECUTED\",\"routes\":[");
        boolean first = true;
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) {
            if (!first) result.append(',');
            first = false;
            result.append("{\"route\":").append(q(proof.getRouteId()))
                .append(",\"measuredDepth\":").append(proof.getMeasuredExecutionDepth())
                .append(",\"samples\":").append(proof.getSolverSamples().size())
                .append(",\"repair\":true,\"retest\":true}");
        }
        return result.append("]}").toString();
    }

    private static String roleVector(BoundedAssemblyPlan plan, long seed) {
        ComposedBlockContribution aDriver = plan.getBlocks().get("channel-a-driver");
        ComposedBlockContribution bDriver = plan.getBlocks().get("channel-b-driver");
        require(aDriver != null && bDriver != null,
            "missing driver role selection");
        String a = aDriver.getProviderTypeId();
        String b = bDriver.getProviderTypeId();
        return "seed=" + Long.toString(seed) + ";a=" + a + ";b=" + b +
            ";fault=" + plan.getFaultDecisionKey();
    }

    private static boolean isSolvedSeed(long seed) {
        for (long value : SOLVED_SEEDS) if (value == seed) return true;
        return false;
    }

    private static boolean isRoleSeed(long seed) {
        for (long value : ROLE_SEEDS) if (value == seed) return true;
        return false;
    }

    private static String verifyBrokenSupport(CirSim sim,
            GeneratedBoardInstance board) {
        phase = "support broken-function negative";
        ControlledIndicatorDeviceBehavior behavior = behavior(board);
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        BoundedAssemblyPlan plan = plan(board);
        String componentId = plan.idFor(plan.getSupportBlockKey(),
            FunctionalBlockDescriptor.EntityKind.COMPONENT,
            SupplyPresentBlockContributions.RSUP_COMPONENT_ID);
        CircuitElm backing = board.getComponentBindings().getSingleElement(componentId);
        require(backing instanceof ResistorElm && sim.elmList.contains(backing),
            "support resistor is not an active owned solver element");
        ResistorElm supportResistor = (ResistorElm) backing;
        double healthyResistance = supportResistor.getResistance();
        Vector<CircuitElm> originalElements = new Vector<CircuitElm>(sim.elmList);
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        Throwable primary = null;
        power(sim, BoardPowerState.UNPOWERED, board);
        try {
            // A finite high-resistance failure uses CircuitJS's existing model
            // and preserves every declared element, endpoint and nameplate.
            supportResistor.setResistance(1.0e9);
            sim.needAnalyze();
            settle(sim, board);
            power(sim, BoardPowerState.POWERED, board);
            ControlledIndicatorChannelObservation support =
                new ControlledIndicatorChannelObservation(board, plan, plan.getChannels().get(0));
            double brokenCurrent = support.supportCurrent();
            double brokenLedCurrent = support.supportLedCurrent();
            require(finite(brokenCurrent) && finite(brokenLedCurrent) &&
                brokenCurrent < .000001 && brokenLedCurrent < .000001 &&
                !behavior.isHealthySupport(board),
                "broken support retained its healthy solved function");
            verifyIndependentChannels(sim, board);
            boolean healthyRejected = false;
            try {
                behavior.verifyHealthy(board, BoardPowerState.POWERED);
            } catch (IllegalStateException expected) {
                healthyRejected = true;
            }
            settle(sim, board);
            GeneratedCustomerRetestResult brokenRetest =
                sim.performCustomerRetest();
            settle(sim, board);
            require(healthyRejected && brokenRetest != null &&
                !brokenRetest.isPassed() && challenge.getCustomerRetestResult() == brokenRetest,
                "broken support did not execute a failing healthy/customer check");
            power(sim, BoardPowerState.UNPOWERED, board);
            return "{\"brokenHealthyRejected\":true," +
                "\"brokenRetestRejected\":true,\"restoredPassed\":true," +
                "\"brokenResistanceOhms\":1.0e9,\"brokenCurrentAmps\":" + brokenCurrent +
                ",\"brokenLedCurrentAmps\":" + brokenLedCurrent + "}";
        } catch (Throwable failure) {
            primary = failure;
            rethrow(failure);
            return null;
        } finally {
            Throwable cleanup = null;
            // Restore the actual model before any fallible cleanup operation.
            supportResistor.setResistance(healthyResistance);
            try {
                sim.needAnalyze();
                settle(sim, board);
                require(owner == sim.getGeneratedBoardInstance(),
                    "support restoration changed board ownership");
                power(sim, BoardPowerState.POWERED, board);
                behavior.verifyHealthy(board, BoardPowerState.POWERED);
                settle(sim, board);
                GeneratedCustomerRetestResult restoredRetest =
                    sim.performCustomerRetest();
                settle(sim, board);
                require(restoredRetest != null && restoredRetest.isPassed() &&
                    challenge.getCustomerRetestResult() == restoredRetest,
                    "restored support failed positive customer retest");
                require(supportResistor.getResistance() == healthyResistance &&
                    originalElements.equals(sim.elmList) && owner == sim.getGeneratedBoardInstance(),
                    "support fixture did not restore the exact model and graph inventory");
                invariant(sim);
            } catch (Throwable failure) {
                cleanup = failure;
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else rethrow(cleanup);
            }
        }
    }

    private static String verifyConstructionFailures(CirSim sim, long seed) {
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        String circuit = sim.dumpCircuit();
        StringBuilder failed = new StringBuilder();
        for (final BoundedGeneratedBoardAssembler.Stage target :
                BoundedGeneratedBoardAssembler.Stage.values()) {
            final RuntimeException injected =
                new IllegalStateException("task49-private-" + target);
            boolean caught = false;
            try {
                BoundedGeneratedBoardAssembler.assemble(
                    BoundedAssemblyRequest.forControlledIndicator(seed),
                    new BoundedGeneratedBoardAssembler.FailureProbe() {
                        public void after(BoundedGeneratedBoardAssembler.Stage stage) {
                            if (stage == target) throw injected;
                        }
                    });
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure failure) {
                caught = failure.getStage() == target && failure.isCleanupSucceeded() &&
                    failure.getOriginalFailure() == injected;
            }
            require(caught && owner == sim.getGeneratedBoardInstance() &&
                circuit.equals(sim.dumpCircuit()) && sim.isGeneratedRuntimeSettled(),
                "construction stage failed to preserve original owner: " + target);
            if (failed.length() != 0) failed.append(',');
            failed.append(q(target.name()));
        }
        return "{\"failedStages\":[" + failed +
            "],\"originalOwnerPreserved\":true}";
    }

    private static String verifySuccession(CirSim sim, long firstSeed,
            long secondSeed) {
        BoundedGeneratedBoardAssembler.Result first = assemble(
            BoundedAssemblyRequest.forControlledIndicator(firstSeed));
        install(sim, first.getInstance());
        GeneratedBoardInstance firstBoard = first.getInstance();
        for (ControlledIndicatorChannel channel : plan(firstBoard).getChannels()) {
            firstBoard.invokeOperation(channel.getHighOperationId(), sim);
            settle(sim);
            firstBoard.invokeOperation(channel.getLowOperationId(), sim);
            settle(sim);
        }
        GeneratedChallengeController old = sim.getGeneratedChallengeController();
        final Vector<Runnable> completions = new Vector<Runnable>();
        old.setRetestCompletionDispatchForDeveloperVerification(
            new GeneratedChallengeController.RetestCompletionDispatch() {
                public void dispatch(Runnable completion) { completions.add(completion); }
            });
        try {
            GeneratedCustomerRetestResult firstResult = sim.performCustomerRetest();
            settle(sim);
            GeneratedCustomerRetestResult latest = sim.performCustomerRetest();
            settle(sim);
            require(firstResult != null && latest != null && firstResult != latest &&
                completions.size() == 2, "retest scheduler did not queue distinct requests");
            BoundedGeneratedBoardAssembler.Result second = assemble(
                BoundedAssemblyRequest.forControlledIndicator(secondSeed));
            FreshGeneratedRuntimeInstallation.requireDisjoint(firstBoard,
                second.getInstance());
            install(sim, second.getInstance());
            completions.get(1).run();
            completions.get(0).run();
            require(sim.getGeneratedBoardInstance() == second.getInstance() &&
                sim.getGeneratedChallengeController().getCustomerRetestResult() == null,
                "stale retest callback crossed to the replacement owner");
            return "{\"firstSeed\":" + q(Long.toString(firstSeed)) +
                ",\"secondSeed\":" + q(Long.toString(secondSeed)) +
                ",\"boardReplacement\":true,\"staleCompletion\":true}";
        } finally {
            old.setRetestCompletionDispatchForDeveloperVerification(null);
        }
    }

    private static BoundedGeneratedBoardAssembler.Result assemble(
            BoundedAssemblyRequest request) {
        BoundedGeneratedBoardAssembler.Result result =
            BoundedGeneratedBoardAssembler.assemble(request);
        candidates.add(result.getInstance());
        return result;
    }

    private static void installNormal(CirSim sim, GeneratedBoardInstance candidate) {
        FreshGeneratedRuntimeInstallation.installNormalComposition(sim, candidate, false);
        invariant(sim);
    }

    private static void install(CirSim sim, GeneratedBoardInstance candidate) {
        FreshGeneratedRuntimeInstallation.install(sim, candidate, false);
        invariant(sim);
    }

    private static String snapshot(BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance board = result.getInstance();
        StringBuilder value = new StringBuilder(result.getPlan().getSemanticSignature());
        for (CircuitElm element : board.getSimulationElements())
            value.append('\n').append(element.dump());
        value.append('\n').append(board.getPcbLayout().geometryFingerprint());
        value.append("\n/components=").append(board.getBoard().getComponentIds());
        value.append("\n/pads=").append(board.getBoard().getPadIds());
        value.append("\n/nets=").append(board.getBoard().getNetIds());
        for (String id : board.getBoard().getComponentIds()) {
            PhysicalBoardSlot slot = board.getPhysicalBoardRuntime().getSlot(id);
            value.append('\n').append(id).append('|').append(slot.getId()).append('|')
                .append(slot.getInstalledPart() == null ? "empty" :
                    slot.getInstalledPart().getId());
        }
        return value.toString();
    }

    private static void restore(CirSim sim, Task41SimulationSnapshot snapshot,
            GeneratedBoardInstance owner, boolean quickPlay, QuickPlaySession session) {
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        sim.quickPlayActive = quickPlay;
        sim.quickPlaySession = session;
        if (current != owner) {
            snapshot.restore(sim);
            dispose(sim, current, owner);
        }
        snapshot.assertRestored(sim);
    }

    private static void dispose(CirSim sim, GeneratedBoardInstance candidate,
            GeneratedBoardInstance protectedOwner) {
        if (candidate == null || disposed.contains(candidate)) return;
        require(candidate != protectedOwner && candidate != sim.getGeneratedBoardInstance(),
            "cannot dispose active owner");
        candidate.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : candidate.getSimulationElements()) element.delete();
        disposed.add(candidate);
    }

    private static boolean retest(CirSim sim) {
        settle(sim);
        GeneratedCustomerRetestResult result = sim.performCustomerRetest();
        require(result != null, "customer retest operation was not executed");
        settle(sim);
        return result.isPassed();
    }

    private static void power(CirSim sim, BoardPowerState state,
            GeneratedBoardInstance board) {
        settle(sim, board);
        sim.setBoardPowerState(state);
        settle(sim, board);
        require(sim.getBoardPowerController().getState() == state,
            "power transition did not settle: " + state);
    }

    private static void settle(CirSim sim) {
        GeneratedRuntimeDeveloperSettlement.settle(sim,
            "task49-dependent-operation");
        require(sim.isGeneratedRuntimeSettled() && sim.stopMessage == null &&
            !sim.activeMeasurementOverlay,
            "controlled runtime did not settle safely");
    }

    private static void settle(CirSim sim, GeneratedBoardInstance board) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, board,
            "task49-dependent-operation");
        require(sim.isGeneratedRuntimeSettled() && sim.stopMessage == null &&
            !sim.activeMeasurementOverlay,
            "controlled runtime did not settle safely");
    }

    private static ControlledIndicatorDeviceBehavior behavior(
            GeneratedBoardInstance board) {
        require(board != null && board.getBehaviorContract() instanceof
            ControlledIndicatorDeviceBehavior,
            "controlled behavior is required");
        return (ControlledIndicatorDeviceBehavior) board.getBehaviorContract();
    }

    private static BoundedAssemblyPlan plan(GeneratedBoardInstance board) {
        return behavior(board).getAssemblyPlan();
    }

    private static GeneratedDiagnosticExecutionProvider executionProvider(
            GeneratedBoardInstance board) {
        require(board != null && board.getBehaviorContract() instanceof
            GeneratedDiagnosticExecutionProvider,
            "diagnostic execution provider is required");
        return (GeneratedDiagnosticExecutionProvider) board.getBehaviorContract();
    }

    private static boolean containsCurrentCatalogEntry(
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe) {
        if (recipe == null || recipe.getCatalogEntryId() == null) return false;
        for (ControlledIndicatorValueSynthesis.Candidate candidate :
                ControlledIndicatorValueSynthesis.validCandidates(recipe.getIntent()))
            if (recipe.getCatalogEntryId().equals(candidate.getId()) &&
                recipe.getResistanceOhms() == candidate.getResistanceOhms()) return true;
        return false;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String joinQuoted(Vector<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) result.append(',');
            result.append(q(value));
        }
        return result.toString();
    }

    private static <T> java.util.List<T> reverse(java.util.List<T> values) {
        java.util.ArrayList<T> result = new java.util.ArrayList<T>(values);
        java.util.Collections.reverse(result);
        return result;
    }

    private static void invariant(CirSim sim) {
        GeneratedRuntimeInvariant.verify(sim, sim.getGeneratedBoardInstance(),
            sim.getBoardModificationController(), sim.elmList);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition)
            throw new IllegalStateException("Task49: " + phase + ": " + message);
    }

    private static void rethrow(Throwable problem) {
        if (problem instanceof Error) throw (Error) problem;
        if (problem instanceof RuntimeException) throw (RuntimeException) problem;
        throw new IllegalStateException("Task49 failure", problem);
    }

    private static String q(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
