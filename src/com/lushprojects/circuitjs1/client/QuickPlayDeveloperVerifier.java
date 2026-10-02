package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused, developer-only proof for the Quick Play session boundary. */
final class QuickPlayDeveloperVerifier {
    private QuickPlayDeveloperVerifier() { }

    static void verify(CirSim sim) {
        require(sim.isQuickPlayMode(), "Quick Play verifier did not enter Quick Play mode");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        QuickPlaySelection selection = sim.getQuickPlaySelectionForDeveloperVerification();
        require(instance != null && challenge != null && challenge.isReady() && selection != null,
            "Quick Play challenge did not become ready");
        require(sim.pcbWorkbenchController != null &&
            sim.pcbWorkbenchController.getPlayerFacingTextForDeveloperVerification().indexOf(
                "Finish Job") >= 0,
            "Quick Play workbench did not expose the Finish Job boundary");
        verifyEligibleFamilies();
        verifyFullCatalogSelectionAndStagedRequests();
        require(selection.getFamilyId().equals(instance.getCircuitFamilyId()) &&
            selection.getSeed() == instance.getSeed() && instance.getSeed() ==
            instance.getChallengeDefinition().getSelectionSeed(),
            "Quick Play selection was not passed to the deterministic generator");
        verifyDeterministicFamilySelection();
        verifySelectionEnvelopes();
        verifyNaturalLedSeedEnvelope();
        verifyNaturalNpnSeedEnvelope();
        verifyNaturalNmosSeedEnvelope();
        require(instance.getFaultBinding().getFault().getType() != GeneratedFaultType.DIODE_SHORT,
            "Quick Play selected the developer-only diode short fault");
        verifyFreshSessionBoundary();
        verifyUnrepairedFinishDoesNotAdvance(sim, challenge);
        verifySeedOneNpnScenario(sim, challenge, instance);
        verifyCorrectRepairCanFinish(sim, challenge, instance);
        verifyCompletedPhysicalMutationRemainsLive(sim, challenge, instance);
        verifyCompletedSemanticOperationsRemainLive(sim, challenge, instance);
        verifyNormalPlayerPrivacy(sim);
        sim.publishQuickPlayVerificationReportForDeveloperVerification(
            "unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated");
        sim.setCircuitTitle("Quick Play verification passed");
    }

    static void verifyExplicitRoute(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(!sim.isQuickPlayMode() &&
            sim.getQuickPlaySelectionForDeveloperVerification() == null,
            "Explicit route was replaced by Quick Play selection");
        require(instance != null && challenge != null && challenge.isReady() &&
            "LED_INDICATOR".equals(instance.getCircuitFamilyId()) && instance.getSeed() == 3,
            "Explicit challenge route did not preserve its family and seed");
        sim.setCircuitTitle("Quick Play explicit-route verification passed");
    }

    private static void verifyEligibleFamilies() {
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        require(families.size() == 9 &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("LED_INDICATOR") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("DIODE_PROTECTED_INDICATOR") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("PARALLEL_DUAL_INDICATOR") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("RC_DELAY") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("NPN_LOW_SIDE_SWITCH") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("NMOS_LOW_SIDE_SWITCH") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible("RELAY_OUTPUT") &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible(QuickPlayFamilyRegistry.SENSOR_CONTROL) &&
            QuickPlayFamilyRegistry.isNormalPlayerEligible(Rb15Plan.FAMILY_ID),
            "Quick Play eligible-family registry changed");
        require(!QuickPlayFamilyRegistry.isNormalPlayerEligible("DIODE_SHORT") &&
            !QuickPlayFamilyRegistry.isNormalPlayerEligible("TASK_37_FUTURE") &&
            !QuickPlayFamilyRegistry.isNormalPlayerEligible(
                ControlledIndicatorBlockContributions.FAMILY_ID) &&
            !QuickPlayFamilyRegistry.isNormalPlayerEligible(Rb30Plan.FAMILY_ID),
            "Quick Play synchronous registry admitted an unsupported family");
    }

    private static void verifyFreshSessionBoundary() {
        QuickPlaySession firstSession = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 0, 3 }));
        QuickPlaySession nextSession = QuickPlaySession.create(new QuickPlayFixedRandomSource(
            new long[] { 1, 2 }));
        QuickPlaySelection first = firstSession.getSelection();
        QuickPlaySelection next = nextSession.getSelection();
        GeneratedBoardInstance firstBoard = firstSession.getInstance();
        GeneratedBoardInstance nextBoard = nextSession.getInstance();
        require(!first.getFamilyId().equals(next.getFamilyId()) &&
            first.getSeed() != next.getSeed() && firstBoard != nextBoard &&
            firstBoard.getBoard() != nextBoard.getBoard() &&
            firstBoard.getPhysicalBoardRuntime() != nextBoard.getPhysicalBoardRuntime(),
            "Quick Play next session reused board, runtime, family, or seed state");
        verifyFreshBoardState(firstBoard);
        verifyFreshBoardState(nextBoard);
    }

    private static void verifyFreshBoardState(GeneratedBoardInstance instance) {
        BoardModificationController modifications =
            new BoardModificationController(null, instance);
        require(modifications.isFullyRestored(),
            "Quick Play session did not create fresh modification state");
        Vector<PhysicalBoardSlot> slots = instance.getPhysicalBoardRuntime().getSlots();
        require(!slots.isEmpty() && !instance.getPhysicalBoardRuntime().getPhysicalParts().isEmpty(),
            "Quick Play session did not create fresh physical state");
        for (PhysicalBoardSlot slot : slots) {
            if (slot.isOccupied())
                require(isFreshPhysicalPart(slot.getInstalledPart()) &&
                    slot.getInstalledPart().isInstalled() &&
                    slot.getInstalledPart().getBoardSlot() == slot,
                    "Quick Play session retained a non-fresh physical slot state");
        }
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts())
            require(isFreshPhysicalPart(part) && (!part.isInstalled() || part.getBoardSlot() != null),
                "Quick Play session retained a replacement or detached physical part");
    }

    private static boolean isFreshPhysicalPart(PhysicalPart<?> part) {
        String kind = part.getProvenance().getKind();
        return PhysicalPartProvenance.GENERATED_ORIGINAL.equals(kind) ||
            PhysicalPartProvenance.FIXED_GENERATED.equals(kind);
    }

    private static void verifyDeterministicFamilySelection() {
        // This is the retained nine-family synchronous leaf construction oracle.
        // Full catalog selection and staged family admission are checked separately.
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        for (int i = 0; i < families.size(); i++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { i, 3 }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(families.elementAt(i).equals(selection.getFamilyId()) &&
                generated.getCircuitFamilyId().equals(selection.getFamilyId()) &&
                generated.getSeed() == selection.getSeed() &&
                generated.getFaultBinding().getFault().getType() != GeneratedFaultType.DIODE_SHORT,
                "Quick Play family did not generate through its deterministic normal route");
        }
    }

    /**
     * The menu selects from enabled registrations, but staged families cannot fall
     * through the synchronous leaf generator. Both normal request factories
     * retain the declared profile, exact seed, bounded candidates and policy.
     */
    private static void verifyFullCatalogSelectionAndStagedRequests() {
        Vector<String> catalog = PlayerFamilyCatalog.families();
        Vector<String> expected = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        expected.add(ControlledIndicatorBlockContributions.FAMILY_ID);
        require(catalog.size() == 10 && catalog.equals(expected),
            "Quick Play enabled catalog order or normal family census changed");
        expected.add(Rb30Plan.FAMILY_ID);
        Vector<String> registered = PlayerFamilyCatalog.registeredFamilies();
        require(registered.size() == 11 && registered.equals(expected) &&
            !PlayerFamilyCatalog.isNormalPlayerEnabled(Rb30Plan.FAMILY_ID),
            "Blocked Q30 must retain its registered construction and identity contracts");

        long[] roots = { Long.MIN_VALUE, 9007199254740993L, Long.MAX_VALUE };
        for (int familyIndex = 0; familyIndex < registered.size(); familyIndex++) {
            String family = registered.elementAt(familyIndex);
            DifficultyProfile profile = PlayerFamilyCatalog.candidateProfile(family);
            require(QuickPlayAdmission.supports(family, profile),
                "Registered family has no declared request profile: " + family);
            for (long root : roots) {
                boolean enabled = catalog.contains(family);
                QuickPlaySelection selection = enabled ? new QuickPlaySelector(
                    new QuickPlayFixedRandomSource(new long[] { familyIndex, root })).select() :
                    new QuickPlaySelection(family, root);
                require(family.equals(selection.getFamilyId()) && selection.getSeed() == root,
                    "Catalog selection or registered identity changed family order or signed-long seed: " + family);
                if (!enabled) {
                    boolean blocked = false;
                    try { PlayerFamilyCatalog.requireNormalPlayerEnabled(family); }
                    catch (IllegalArgumentException expectedFailure) { blocked = true; }
                    require(blocked, "Disabled registration entered normal admission: " + family);
                }

                GenerationRequest staged = GenerationRequest.stagedQuickPlay(selection);
                require(staged.isQuickPlay() && staged.getDifficulty() == profile &&
                    staged.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES &&
                    staged.getDescriptor().getRootSeed() == root,
                    "Selected menu family did not enter bounded staged Quick Play: " + family);

                PlayerLaunchRequest player = PlayerLaunchRequest.random(family,
                    Long.toString(root), profile.name());
                GenerationRequest normal = player.generation();
                boolean normalMedium = Rb30Plan.FAMILY_ID.equals(family);
                require(player.seed == root && player.candidateSearch &&
                    normal.getDifficulty() == profile && !normal.isQuickPlay() &&
                    normal.candidateCount() == QuickPlayAdmission.MAX_CANDIDATES &&
                    normal.getDescriptor().getRootSeed() == root &&
                    normal.getExecutionPolicy() == (normalMedium ?
                        GenerationExecutionPolicy.NORMAL_MEDIUM :
                        GenerationExecutionPolicy.SMALL_BOARD) &&
                    normal.getRequiredPhysicalAdmissionIdentity().equals(normalMedium ?
                        MediumBoardNormalAdmission.IDENTITY : SupportedEnvelope.current().identity()),
                    "Normal player request lost profile, seed, or physical capability: " + family);

                if (familyIndex < QuickPlayFamilyRegistry.getNormalPlayerFamilyIds().size()) {
                    require(QuickPlayFamilyRegistry.isNormalPlayerEligible(family),
                        "Synchronous leaf disappeared from its construction registry");
                } else {
                    require(!QuickPlayFamilyRegistry.isNormalPlayerEligible(family) &&
                        staged.isComposition() ==
                            ControlledIndicatorBlockContributions.FAMILY_ID.equals(family),
                        "Staged family crossed the synchronous leaf boundary: " + family);
                    boolean rejected = false;
                    try { new QuickPlaySelector(new QuickPlayFixedRandomSource(
                        new long[] { familyIndex, root })).generate(selection); }
                    catch (IllegalArgumentException expectedFailure) { rejected = true; }
                    require(rejected,
                        "Synchronous Quick Play generation accepted staged family: " + family);
                }
            }
        }
    }

    /**
     * Exercises arbitrary selector values through the ordinary Quick Play
     * selector/generator boundary and checks each family against its own
     * validated seed envelope.  The expected sets are intentionally explicit
     * here: this is the canary for accidental changes to the registry boundary.
     */
    private static void verifySelectionEnvelopes() {
        Vector<String> families=QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        long[] roots={Long.MIN_VALUE,Long.MAX_VALUE,-4518705223253195925L,
            -5365808313541656343L,-17,-7,-1,0,1,2,3,4,17,42,101,9007199254740993L};
        for(int familyIndex=0;familyIndex<families.size();familyIndex++)for(long root:roots) {
            String family=families.get(familyIndex);
            QuickPlaySelection selection=new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[]{familyIndex,root})).select();
            require(family.equals(selection.getFamilyId()) && selection.getSeed()==root &&
                QuickPlayFamilyRegistry.selectNormalPlayerSeed(family,root)==root &&
                GenerationRequest.leaf(family,root,true).candidateCount()==4,
                "Normal selection remapped entropy or bypassed bounded procedural admission");
            // Selection is not qualification. The all-family physical corpus and
            // actual compiled generation gates separately exercise admission.
        }
    }

    /**
     * Exercises the ordinary selector/generator boundary.  This intentionally
     * does not use generateForFaultVerification: the public Quick Play path
     * must reach the validated NPN envelope through its normal seed.
     */
    private static void verifyNaturalNpnSeedEnvelope() {
        long[] seeds = { 0, 1, 2 };
        double[] loadVoltages = { 9, 12, 5 };
        GeneratedFaultType[] faults = {
            GeneratedFaultType.TRANSISTOR_CE_OPEN,
            GeneratedFaultType.TRANSISTOR_CE_SHORT,
            GeneratedFaultType.BASE_RESISTOR_OPEN
        };
        for (int index = 0; index < seeds.length; index++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { 4, seeds[index] }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(
                    selection.getFamilyId()) && selection.getSeed() == seeds[index] &&
                    generated.getSeed() == seeds[index] && generated.getFaultBinding().getFault()
                        .getType() == faults[index],
                "Natural NPN Quick Play seed boundary changed at seed " + seeds[index]);
            PowerInputNameplate loadInput = generated.getPhysicalSpecifications()
                .getPowerInputNameplate("LOAD_VIN_INPUT");
            require(loadInput != null &&
                Math.abs(loadInput.getNominalVoltage() - loadVoltages[index]) < .0001,
                "Natural NPN Quick Play load voltage changed at seed " + seeds[index]);
        }
    }

    /**
     * The LED route keeps its three legacy selections and adds one normal
     * seed for the second physical fault owner.
     */
    private static void verifyNaturalLedSeedEnvelope() {
        long[] seeds = { 0, 2, 3, 4 };
        GeneratedFaultType[] faults = {
            GeneratedFaultType.RESISTOR_OPEN,
            GeneratedFaultType.RESISTOR_OPEN,
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
            GeneratedFaultType.LED_OPEN
        };
        for (int index = 0; index < seeds.length; index++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { 0, seeds[index] }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(QuickPlayFamilyRegistry.LED_INDICATOR.equals(selection.getFamilyId()) &&
                    selection.getSeed() == seeds[index] && generated.getSeed() == seeds[index] &&
                    generated.getFaultBinding().getFault().getType() == faults[index],
                "Natural LED Quick Play seed boundary changed at seed " + seeds[index]);
        }
    }

    /**
     * Permanent normal-player canary for every NMOS fault admitted by Quick
     * Play.  This intentionally exercises the selector and normal generator,
     * rather than the developer-only forced-fault route.
     */
    private static void verifyNaturalNmosSeedEnvelope() {
        long[] seeds = { 0, 1, 2 };
        double[] loadVoltages = { 9, 12, 5 };
        GeneratedFaultType[] faults = {
            GeneratedFaultType.NMOS_DS_OPEN,
            GeneratedFaultType.NMOS_DS_SHORT,
            GeneratedFaultType.NMOS_GATE_OPEN
        };
        for (int index = 0; index < seeds.length; index++) {
            QuickPlaySelector selector = new QuickPlaySelector(new QuickPlayFixedRandomSource(
                new long[] { 5, seeds[index] }));
            QuickPlaySelection selection = selector.select();
            GeneratedBoardInstance generated = selector.generate(selection);
            require(QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(
                    selection.getFamilyId()) && selection.getSeed() == seeds[index] &&
                    generated.getSeed() == seeds[index] && generated.getFaultBinding().getFault()
                        .getType() == faults[index],
                "Natural NMOS Quick Play seed boundary changed at seed " + seeds[index]);
            PowerInputNameplate loadInput = generated.getPhysicalSpecifications()
                .getPowerInputNameplate("LOAD_VIN_INPUT");
            require(loadInput != null &&
                Math.abs(loadInput.getNominalVoltage() - loadVoltages[index]) < .0001,
                "Natural NMOS Quick Play load voltage changed at seed " + seeds[index]);
        }
    }

    private static void verifySeedOneNpnScenario(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        if (!QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()) ||
                instance.getSeed() != 1)
            return;
        require(challenge.getScenario() != null &&
            challenge.getScenario().getObservedBehavior() ==
                GeneratedObservedBehavior.NPN_LOAD_STUCK_ACTIVE &&
            "The controlled load stays active when control is low.".equals(
                challenge.getComplaintText()),
            "Quick Play NPN seed 1 did not present the exact stuck-active complaint");
        NpnLowSideSwitchFamilyState state = (NpnLowSideSwitchFamilyState)
            instance.getFamilyState();
        double control = NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.1") -
            NpnLowSideSwitchGeneratedBoardValidator.voltage(instance, "J2.2");
        require(!state.isCommandedOn() && control < 1 &&
            NpnLowSideSwitchGeneratedBoardValidator.loadCurrent(instance) > .005 &&
            NpnLowSideSwitchGeneratedBoardValidator.collectorVoltage(instance) < 1,
            "Quick Play NPN seed 1 did not present live low-control, stuck-active behavior");
    }

    private static void verifyUnrepairedFinishDoesNotAdvance(CirSim sim,
            GeneratedChallengeController challenge) {
        require(challenge.getState() == GeneratedChallengeState.READY &&
            !sim.finishQuickPlayJob() && !challenge.isCompleted() &&
            challenge.getState() == GeneratedChallengeState.READY,
            "Finish Job advanced an unrepaired challenge");
        settle(sim, sim.getGeneratedBoardInstance());
    }

    private static void settle(CirSim sim, GeneratedBoardInstance owner) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "Quick Play public action");
    }

    private static void power(CirSim sim, GeneratedBoardInstance owner, BoardPowerState state) {
        settle(sim, owner);
        sim.setBoardPowerState(state);
        if (owner.getTemporalBehavior() != null) sim.advanceGeneratedTemporalProfile(.025);
        settle(sim, owner);
        require(sim.getBoardPowerController().getState() == state &&
            (state != BoardPowerState.UNPOWERED || sim.getBoardPowerController().isElectricallyUnpowered()),
            "Quick Play public power action did not reach requested electrical isolation");
    }

    private static void replace(CirSim sim, GeneratedBoardInstance owner,
            PhysicalSlotMutationProvider provider, String catalogId) {
        require(provider != null, "Quick Play repair has no current slot provider");
        power(sim, owner, BoardPowerState.UNPOWERED);
        require(provider.removeInstalledPart(), "Quick Play separate removal was not accepted");
        settle(sim, owner);
        require(provider.installNewFromCatalog(catalogId), "Quick Play catalog replacement was not accepted");
        settle(sim, owner);
    }

    private static void finishRepaired(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController challenge) {
        settle(sim, owner);
        require(challenge.performCustomerRetest().isPassed(), "Quick Play repaired customer retest failed");
        settle(sim, owner);
        require(sim.finishQuickPlayJob() && challenge.isCompleted(),
            "Quick Play passed customer retest did not finish its current owner");
        settle(sim, owner);
    }

    private static void verifyCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(instance.getCircuitFamilyId())) {
            verifyRcCorrectRepairCanFinish(sim, challenge, instance);
            return;
        }
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId())) {
            if (instance.getSeed() == 1) {
                verifySeedOneNpnRepairCanFinish(sim, challenge, instance);
                return;
            }
            verifyNpnCorrectRepairCanFinish(sim, challenge, instance);
            return;
        }
        if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId())) {
            verifyNmosCorrectRepairCanFinish(sim, challenge);
            return;
        }
        if (QuickPlayFamilyRegistry.SENSOR_CONTROL.equals(instance.getCircuitFamilyId())) {
            verifySensorControlCorrectRepairCanFinish(sim, challenge, instance);
            return;
        }
        require("LED_INDICATOR".equals(instance.getCircuitFamilyId()) && instance.getSeed() == 3,
            "Quick Play verification selection is not the deterministic LED proof");
        ResistorSlotController slots = sim.getResistorSlotController();
        require(slots != null, "Quick Play LED proof has no resistor capability");
        replace(sim, instance, slots, "R_CATALOG_1000");
        power(sim, instance, BoardPowerState.POWERED);
        sim.verifyGeneratedBoard();
        require(challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED, false) ==
            GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correctly restored Quick Play challenge did not report generic repair status");
        finishRepaired(sim, instance, challenge);
    }

    private static void verifyCompletedPhysicalMutationRemainsLive(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        require(challenge.isCompleted() && challenge.isReady(),
            "Quick Play correct repair did not enter latched completed state");
        String componentId = challenge.getDefinition().getFault().getTargetComponentId();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider(componentId);
        require(provider != null && componentId.equals(provider.getComponentId()),
            "Completed Quick Play target has no active physical mutation provider: " + componentId);
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        require(slot != null && slot.getInstalledPart() != null,
            "Completed Quick Play target has no installed physical part: " + componentId);
        PhysicalPart<?> installed = slot.getInstalledPart();
        GeneratedCustomerRetestResult passedRetest = challenge.getCustomerRetestResult();
        require(passedRetest != null && passedRetest.isPassed(),
            "Completed Quick Play mutation proof has no passed customer retest to preserve");
        BoardModificationController modifications = sim.getBoardModificationController();
        ComponentPhysicalState componentState = modifications.getComponentState(componentId);
        boolean fullyRestored = modifications.isFullyRestored();
        Vector<GeneratedComponentConnectionBinding> bindings =
            instance.getConnectionBindings().getForComponent(componentId);
        boolean[] connected = new boolean[bindings.size()];
        CircuitMeasurementEndpoint[] componentEndpoints =
            new CircuitMeasurementEndpoint[bindings.size()];
        for (int index = 0; index < bindings.size(); index++)
        {
            GeneratedComponentConnectionBinding binding = bindings.get(index);
            connected[index] = modifications.isLeadConnected(componentId, binding.getPadId());
            componentEndpoints[index] = binding.getComponentEndpoint();
        }
        Vector<PhysicalPart> physicalParts = new Vector<PhysicalPart>(runtime.getPhysicalParts());
        // dumpCircuit includes solver transient values; the canonical mutation
        // oracle here is the settled active graph and its endpoint ownership.
        Vector<CircuitElm> topology = new Vector<CircuitElm>(sim.elmList);
        int undo = sim.undoStack.size();
        int redo = sim.redoStack.size();
        boolean unsaved = sim.unsavedChanges;
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Completed Quick Play mutation proof did not start powered");
        require(componentState == ComponentPhysicalState.INSTALLED && fullyRestored,
            "Completed Quick Play mutation proof did not start fully installed");
        for (int index = 0; index < bindings.size(); index++)
            require(connected[index] && componentEndpoints[index] != null &&
                    containsElementIdentity(sim.elmList, bindings.get(index).getConnectionElement()),
                "Completed Quick Play mutation proof did not start with every lead connected");
        try {
            sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
            settle(sim, instance);
            require(sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED,
                "Completed Quick Play mutation proof could not enter developer power-off state");
            require(provider.removeInstalledPart(),
                "Completed Quick Play physical removal was rejected");
            settle(sim, instance);
            require(challenge.isCompleted() && challenge.isReady() &&
                    challenge.getCustomerRetestResult() == passedRetest && passedRetest.isPassed(),
                "Completed Quick Play physical removal invalidated the passed customer retest");
            GeneratedRuntimeInvariant.verify(sim, instance, modifications, sim.elmList);
            require(slot.getInstalledPart() == null && runtime.getInstalledPart(componentId) == null &&
                    !installed.isInstalled() && installed.getBoardSlot() == null &&
                    modifications.getComponentState(componentId) == ComponentPhysicalState.REMOVED &&
                    !modifications.isFullyRestored() && physicalParts.equals(runtime.getPhysicalParts()) &&
                    !sameElementIdentities(topology, sim.elmList),
                "Completed Quick Play physical removal did not change the live board");
            for (int index = 0; index < bindings.size(); index++)
                require(!modifications.isLeadConnected(componentId, bindings.get(index).getPadId()) &&
                        !containsElementIdentity(sim.elmList, bindings.get(index).getConnectionElement()),
                    "Completed Quick Play physical removal did not disconnect every lead");
            require(provider.install(installed.getId()),
                "Completed Quick Play physical removal could not reinstall the original part");
            settle(sim, instance);
            require(challenge.isCompleted() && challenge.isReady() &&
                    challenge.getCustomerRetestResult() == passedRetest && passedRetest.isPassed(),
                "Completed Quick Play original-part reinstall invalidated the passed customer retest");
            GeneratedRuntimeInvariant.verify(sim, instance, modifications, sim.elmList);
            require(slot.getInstalledPart() == installed && runtime.getInstalledPart(componentId) == installed &&
                    installed.isInstalled() && installed.getBoardSlot() == slot &&
                    componentState == modifications.getComponentState(componentId) &&
                    fullyRestored == modifications.isFullyRestored() &&
                    physicalParts.equals(runtime.getPhysicalParts()) &&
                    sameElementIdentities(topology, sim.elmList),
                "Completed Quick Play original-part reinstall did not restore the board");
            for (int index = 0; index < bindings.size(); index++) {
                GeneratedComponentConnectionBinding binding = bindings.get(index);
                require(connected[index] == modifications.isLeadConnected(componentId,
                        binding.getPadId()) &&
                        instance.getConnectionBindings().get(componentId,
                            binding.getPadId()) == binding &&
                        containsElementIdentity(sim.elmList, binding.getConnectionElement()) &&
                        GeneratedComponentConnectionBindings.sameEndpoint(componentEndpoints[index],
                            binding.getComponentEndpoint()),
                    "Completed Quick Play original-part reinstall changed lead state");
            }
        } finally {
            sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
        }
        settle(sim, instance);
        GeneratedRuntimeInvariant.verify(sim, instance, modifications, sim.elmList);
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                challenge.isCompleted() && challenge.isReady() &&
                challenge.getCustomerRetestResult() == passedRetest && passedRetest.isPassed() &&
                slot.getInstalledPart() == installed && runtime.getInstalledPart(componentId) == installed &&
                installed.isInstalled() && installed.getBoardSlot() == slot &&
                componentState == modifications.getComponentState(componentId) &&
                fullyRestored == modifications.isFullyRestored() &&
                physicalParts.equals(runtime.getPhysicalParts()) &&
                sameElementIdentities(topology, sim.elmList) &&
                undo == sim.undoStack.size() && redo == sim.redoStack.size() &&
                unsaved == sim.unsavedChanges,
            "Completed Quick Play physical mutation proof did not restore powered state unchanged");
        for (int index = 0; index < bindings.size(); index++) {
            GeneratedComponentConnectionBinding binding = bindings.get(index);
            require(connected[index] == modifications.isLeadConnected(componentId,
                    binding.getPadId()) &&
                    instance.getConnectionBindings().get(componentId,
                        binding.getPadId()) == binding &&
                    containsElementIdentity(sim.elmList, binding.getConnectionElement()) &&
                    GeneratedComponentConnectionBindings.sameEndpoint(componentEndpoints[index],
                        binding.getComponentEndpoint()),
                "Completed Quick Play restoration changed lead state");
        }
    }

    private static void verifyCompletedSemanticOperationsRemainLive(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        if (!QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()) &&
                !QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()))
            return;
        require(challenge.isCompleted() && challenge.isReady(),
            "Completed switch challenge did not retain semantic readiness");
        boolean priorCommand = QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(
            instance.getCircuitFamilyId()) ?
            ((NpnLowSideSwitchFamilyState) instance.getFamilyState()).isCommandedOn() :
            ((NmosLowSideSwitchFamilyState) instance.getFamilyState()).isCommandedOn();
        try {
            require(sim.invokeGeneratedPlayerOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH),
                "Completed switch challenge rejected public HIGH operation");
            settle(sim, instance);
            if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()))
                require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
                    "Completed NPN HIGH operation did not remain solver-backed");
            else
                require(NmosLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
                    "Completed NMOS HIGH operation did not remain solver-backed");
            require(sim.invokeGeneratedPlayerOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW),
                "Completed switch challenge rejected public LOW operation");
            settle(sim, instance);
            if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()))
                require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                    "Completed NPN LOW operation did not remain solver-backed");
            else
                require(NmosLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
                    "Completed NMOS LOW operation did not remain solver-backed");
        } finally {
            require(sim.invokeGeneratedPlayerOperation(priorCommand ?
                    GeneratedBoardOperationIds.CONTROL_INPUT_HIGH :
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW),
                "Completed switch challenge could not restore prior public operation state");
            settle(sim, instance);
        }
    }

    private static void verifySeedOneNpnRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        NpnSlotController slots = sim.getNpnSlotController();
        require(slots != null, "Quick Play NPN seed 1 has no Q1 slot controller");
        replace(sim, instance, slots, NpnReplacementCatalog.CORRECT);
        power(sim, instance, BoardPowerState.POWERED);
        NpnLowSideSwitchFamilyState state = (NpnLowSideSwitchFamilyState)
            instance.getFamilyState();
        instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
        settle(sim, instance);
        require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOn(instance),
            "Quick Play NPN seed 1 replacement did not restore real ON behavior");
        instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
        settle(sim, instance);
        require(NpnLowSideSwitchGeneratedBoardValidator.isHealthyOff(instance),
            "Quick Play NPN seed 1 replacement did not restore real OFF behavior");
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Quick Play NPN seed 1 correct replacement did not report generic repair status");
        finishRepaired(sim, instance, challenge);
    }

    private static void verifySensorControlCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        GeneratedFault fault = challenge.getDefinition().getFault();
        String target = fault.getTargetComponentId();
        require(fault.getType() == GeneratedFaultType.RESISTOR_OPEN &&
                ("RBIAS".equals(target) || "RREF".equals(target) || "RFB".equals(target)) &&
                instance.getFamilyState() instanceof SensorControlFamilyState,
            "Quick Play sensor proof has no serviceable open-resistor fault");
        require(!challenge.performCustomerRetest().isPassed(),
            "Quick Play unrepaired sensor fault passed its LOW/HIGH customer retest");
        require(instance.getDiagnosticProvider() != null,
            "Quick Play sensor proof has no current diagnostic provider");
        String correct = instance.getDiagnosticProvider().getCorrectCatalogId(instance, target);
        replace(sim, instance, sim.getResistorSlotController(target), correct);
        power(sim, instance, BoardPowerState.POWERED);
        SensorControlFamilyState state = (SensorControlFamilyState) instance.getFamilyState();
        instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_LOW, sim);
        settle(sim, instance);
        require(SensorControlGeneratedBoardValidator.isHealthyLow(instance),
            "Quick Play sensor replacement did not restore actual LOW behavior");
        instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_MID, sim);
        settle(sim, instance);
        require(state.getCommandedCondition() == E04SensorControlModel.SensorCondition.SENSOR_MID &&
                state.getModel().getSensorCondition() == E04SensorControlModel.SensorCondition.SENSOR_MID,
            "Quick Play sensor MID command did not reach the current owner");
        instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH, sim);
        settle(sim, instance);
        require(SensorControlGeneratedBoardValidator.isHealthyHigh(instance),
            "Quick Play sensor replacement did not restore actual HIGH behavior");
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correctly restored sensor challenge did not report generic repair status");
        finishRepaired(sim, instance, challenge);
        require(state.getCommandedCondition() == E04SensorControlModel.SensorCondition.SENSOR_HIGH &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                sim.getBoardModificationController().isFullyRestored(),
            "Quick Play sensor retest did not restore its input, power and physical state");
    }

    private static void verifyNpnCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        String target = challenge.getDefinition().getFault().getTargetComponentId();
        if ("Q1".equals(target)) {
            NpnSlotController slots = sim.getNpnSlotController();
            replace(sim, instance, slots, NpnReplacementCatalog.CORRECT);
        } else {
            ResistorSlotController slots = sim.getResistorSlotController(target);
            replace(sim, instance, slots, "R_CATALOG_" + ("RB".equals(target) ? "1000" : "330"));
        }
        power(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correctly restored NPN Quick Play challenge did not report generic repair status");
        finishRepaired(sim, instance, challenge);
    }

    private static void verifyNmosCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge) {
        NmosSlotController slots = sim.getNmosSlotController();
        require(slots != null, "Quick Play NMOS challenge has no Q1 slot controller");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        replace(sim, instance, slots, NmosReplacementCatalog.CORRECT);
        for (CircuitElm element : sim.getGeneratedBoardInstance().getFaultBinding()
                .getPrivateSimulationElements())
            require(sim.elmList.contains(element),
                "Quick Play NMOS catalog replacement lost declared private fault graph");
        if (sim.getGeneratedBoardInstance().getFaultBinding().getEffect() instanceof
                NmosfetDsShortFaultEffect)
            require(!((NmosfetDsShortFaultEffect) sim.getGeneratedBoardInstance()
                .getFaultBinding().getEffect()).isBoardPathEnabled(),
                "Quick Play NMOS catalog replacement retained original private board path");
        require(!((PhysicalNmosPart) sim.getGeneratedBoardInstance().getPhysicalBoardRuntime()
                .getInstalledPart("Q1")).ownsGeneratedFault(
                    sim.getGeneratedBoardInstance().getFaultBinding()),
            "Quick Play NMOS catalog replacement retained original fault identity");
        power(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Correctly restored NMOS Quick Play challenge did not report generic repair status");
        finishRepaired(sim, instance, challenge);
    }

    private static void verifyRcCorrectRepairCanFinish(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        CapacitorSlotController slots = sim.getCapacitorSlotController();
        require(slots != null, "Quick Play RC proof has no capacitor capability");
        replace(sim, instance, slots, CapacitorReplacementCatalog.CORRECT);
        power(sim, instance, BoardPowerState.POWERED);
        GeneratedRepairStatus status = challenge.getRepairStatus();
        require(status == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC Quick Play repair did not report generic repair status");
        finishRepaired(sim, instance, challenge);
        verifyCompletedRcFinishIsNoOp(sim, challenge, instance);
    }

    /**
     * Completion keeps the retained workbench live, while a second direct
     * Finish Job call remains a strict no-op; in particular, it cannot enter
     * RcDelayTemporalBehavior and replay the real power-cycle profile.
     */
    private static void verifyCompletedRcFinishIsNoOp(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance) {
        CircuitPostMeasurementEndpoint output = endpoint(instance, "J2.1");
        CircuitPostMeasurementEndpoint ground = endpoint(instance, "J2.2");
        BoardModificationController modifications = sim.getBoardModificationController();
        ReplaceableCapacitorBoardCapability capability =
            ReplaceableCapacitorBoardCapability.require(instance);
        require(!capability.getSlot().isEmpty(),
            "Completed RC Quick Play proof has no installed replacement");
        PhysicalCapacitorPart installed = capability.getSlot().getInstalledPart();
        String installedId = installed.getId();
        Vector<CircuitElm> topology = new Vector<CircuitElm>(sim.elmList);
        String circuit = sim.dumpCircuit();
        int undo = sim.undoStack.size();
        int redo = sim.redoStack.size();
        boolean unsaved = sim.unsavedChanges;
        double solverTime = sim.t;
        double outputVoltage = voltage(output, ground);
        BoardPowerState powerState = sim.getBoardPowerController().getState();
        GeneratedChallengeState challengeState = challenge.getState();
        boolean overlay = sim.activeMeasurementOverlay;
        boolean fullyRestored = modifications.isFullyRestored();
        ComponentPhysicalState c1State = modifications.getComponentState("C1");
        boolean c1PositiveConnected = modifications.isLeadConnected("C1", "C1.+");
        boolean c1NegativeConnected = modifications.isLeadConnected("C1", "C1.-");
        boolean faultApplied = instance.getFaultBinding().isApplied();

        require(challengeState == GeneratedChallengeState.COMPLETED &&
            !sim.finishQuickPlayJob() && challengeState == challenge.getState(),
            "Completed RC Quick Play Finish Job was not a terminal no-op");
        require(sameBits(solverTime, sim.t),
            "Completed RC Quick Play Finish Job replayed solver time");
        require(powerState == sim.getBoardPowerController().getState() &&
            sameBits(outputVoltage, voltage(output, ground)) && overlay == sim.activeMeasurementOverlay,
            "Completed RC Quick Play Finish Job changed power, RC_OUT, or meter overlay state");
        require(sim.elmList.equals(topology) && circuit.equals(sim.dumpCircuit()) &&
            undo == sim.undoStack.size() && redo == sim.redoStack.size() &&
            unsaved == sim.unsavedChanges,
            "Completed RC Quick Play Finish Job changed solver topology or history");
        require(fullyRestored == modifications.isFullyRestored() &&
            c1State == modifications.getComponentState("C1") &&
            c1PositiveConnected == modifications.isLeadConnected("C1", "C1.+") &&
            c1NegativeConnected == modifications.isLeadConnected("C1", "C1.-") &&
            capability.getSlot().getInstalledPart() == installed && installed.isInstalled() &&
            installedId.equals(capability.getSlot().getInstalledPart().getId()) &&
            faultApplied == instance.getFaultBinding().isApplied(),
            "Completed RC Quick Play Finish Job changed board modification or physical-part state");
    }

    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance instance,
            String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("RC Quick Play pad is not CircuitJS-backed: " + padId);
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static double voltage(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement().getPostVoltage(first.getPostIndex()) -
            second.getElement().getPostVoltage(second.getPostIndex());
    }

    private static boolean sameBits(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private static void verifyNormalPlayerPrivacy(CirSim sim) {
        String text = sim.pcbWorkbenchController == null ? "" :
            sim.pcbWorkbenchController.getPlayerFacingTextForDeveloperVerification();
        String lower = text.toLowerCase();
        String[] hiddenTerms = { "fault", "stress", "damage", "rating",
            "specification", "answer" };
        String leakedTerms = "";
        for (String term : hiddenTerms)
            if ("rating".equals(term) ? lower.matches(".*\\brating\\b.*") :
                    lower.indexOf(term) >= 0)
                leakedTerms += term + " ";
        require(leakedTerms.length() == 0,
            "Quick Play normal-player UI exposed hidden metadata terms: " + leakedTerms);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static boolean contains(long[] values, long expected) {
        for (long value : values)
            if (value == expected)
                return true;
        return false;
    }

    /** Compare the canonical active graph by element identity, independent of list order. */
    private static boolean sameElementIdentities(Vector<CircuitElm> expected,
            Vector<CircuitElm> actual) {
        if (expected == null || actual == null || expected.size() != actual.size())
            return false;
        for (CircuitElm element : expected)
            if (!containsElementIdentity(actual, element))
                return false;
        return true;
    }

    private static boolean containsElementIdentity(Vector<CircuitElm> values,
            CircuitElm expected) {
        for (CircuitElm value : values)
            if (value == expected)
                return true;
        return false;
    }
}
