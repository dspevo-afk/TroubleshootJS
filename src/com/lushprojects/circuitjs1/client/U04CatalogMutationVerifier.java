package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Developer-only failure canaries over real catalog providers and CircuitJS owners. */
final class U04CatalogMutationVerifier {
    private final CirSim sim;
    private int checks;
    private String providerType;
    private final StringBuilder rows = new StringBuilder();

    U04CatalogMutationVerifier(CirSim sim) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("Catalog mutation evidence is developer-only");
        this.sim = sim;
    }

    static String type(PhysicalPart<?> part) {
        if (part instanceof PhysicalResistorPart) return "resistor";
        if (part instanceof PhysicalDiodePart) return "diode";
        if (part instanceof PhysicalCapacitorPart) return "capacitor";
        if (part instanceof PhysicalNpnPart) return "npn";
        if (part instanceof PhysicalNmosPart) return "nmos";
        if (part instanceof PhysicalLedPart) return "led";
        if (part instanceof PhysicalRelayPart) return "relay";
        throw new AssertionError("Uncovered catalog part type");
    }

    int verify(final PhysicalSlotMutationProvider provider, final String catalogId,
            final PhysicalPart<?> loose) {
        providerType = type(loose);
        final PhysicalPart<?> original = sim.getGeneratedBoardInstance()
            .getPhysicalBoardRuntime().getInstalledPart(provider.getComponentId());
        require(original != null && !loose.isInstalled(), "mounted original and separate spare");
        sameInstanceSuccessor(provider, catalogId);
        final Runnable acquire = new Runnable() {
            public void run() { ((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalogId); }
        };
        PhysicalMutationScope.FailureStage[] acquireStages = {
            PhysicalMutationScope.FailureStage.AFTER_INVENTORY_ACQUIRE,
            PhysicalMutationScope.FailureStage.AFTER_CANONICAL_REGISTER,
            PhysicalMutationScope.FailureStage.AFTER_GRAPH_APPEND,
            PhysicalMutationScope.FailureStage.AFTER_COMMIT
        };
        for (PhysicalMutationScope.FailureStage stage : acquireStages)
            injected("acquire", stage, 1, acquire);
        final Runnable remove = new Runnable() {
            public void run() { require(provider.removeInstalledPart(), "remove reached owner"); }
        };
        injected("remove", PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT, 1, remove);
        injected("remove", PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR, 1, remove);
        injected("remove", PhysicalMutationScope.FailureStage.AFTER_EMPTY_SLOT_REBIND, 1, remove);
        injected("remove", PhysicalMutationScope.FailureStage.AFTER_COMMIT, 1, remove);
        require(provider.removeInstalledPart(), "prepare empty slot through actual removal");
        settle();
        final Runnable catalog = new Runnable() {
            public void run() { require(provider.installNewFromCatalog(catalogId), "catalog reached owner"); }
        };
        for (int i = 0; i < 3; i++) injected("catalog", acquireStages[i], 1, catalog);
        final Runnable install = new Runnable() {
            public void run() { require(provider.install(loose.getId()), "tray install reached owner"); }
        };
        PhysicalMutationScope.FailureStage[] installStages = {
            PhysicalMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
            PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
            PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
            PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
            PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
            PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
            PhysicalMutationScope.FailureStage.AFTER_COMMIT
        };
        for (PhysicalMutationScope.FailureStage stage : installStages) {
            int occurrences = stage == PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET ||
                stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT ? original.getTerminalCount() : 1;
            for (int occurrence = 1; occurrence <= occurrences; occurrence++) {
                injected("catalog", stage, occurrence, catalog);
                injected("install", stage, occurrence, install);
            }
        }
        require(provider.install(original.getId()), "restore exact original through tray installation");
        settle();
        require(sim.getGeneratedBoardInstance().getPhysicalBoardRuntime()
            .getInstalledPart(provider.getComponentId()) == original, "original remains installed");
        return checks;
    }

    /**
     * Exercises a purchased resistor through two other real resistor slots.
     * The source inventory remains authoritative for the mounted identity;
     * target slots only own the temporary mount and electrical bindings.
     */
    int verifyCrossTarget() {
        providerType = "resistor-cross";
        final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        final PhysicalBoardRuntime runtime = board.getPhysicalBoardRuntime();
        final BoardPowerState savedPower = sim.getBoardPowerController().getState();
        final CrossTargetFixture fixture = findCrossTargetFixture(runtime);
        final String sourceInventoryId = runtime.getInventoryIdForPart(fixture.part.getId());
        final Vector<String> sourceInventoryBefore = runtime.getInventoryPartIds(sourceInventoryId);
        final Vector<String> partOrderBefore = runtime.getPartOrder();
        final PhysicalPartElectricalBacking backing = fixture.part.getElectricalBacking();
        final PhysicalGeometryRealization geometry = fixture.part.getGeometryRealization();
        final PhysicalPartProvenance provenance = fixture.part.getProvenance();
        final Vector<PhysicalPartTerminal> terminals = fixture.part.getTerminals();
        final ResistorStressState stressState = fixture.source.getStressDamageSystem()
            .getState(fixture.part.getId());
        final StressSnapshot stressBefore = new StressSnapshot(stressState, fixture.part);
        try {
            require(savedPower == BoardPowerState.UNPOWERED ||
                savedPower == BoardPowerState.POWERED,
                "cross fixture requires a defined board power state");
            ensureUnpowered();

            // The occupied target is not an install destination until its
            // current physical owner is removed through the real adapter.
            rejectCandidate("OCCUPIED", fixture.targetOne.getController(), fixture.part,
                true);
            require(fixture.targetOne.getController().removeInstalledPart(),
                "cross target removal reached the real adapter");
            settle();

            PhysicalPart<?> wrongType = findNonResistor(runtime);
            if (wrongType == null)
                record("cross-eligibility", "WRONG_TYPE", 1, "NOT_APPLICABLE");
            else
                rejectCandidate("WRONG_TYPE", fixture.targetOne.getController(), wrongType, true);

            PhysicalResistorPart wrongGeometry = findLooseMismatchedGeometry(runtime, geometry);
            if (wrongGeometry == null)
                record("cross-eligibility", "WRONG_GEOMETRY", 1, "NOT_APPLICABLE");
            else
                rejectCandidate("WRONG_GEOMETRY", fixture.targetOne.getController(),
                    wrongGeometry, true);

            PhysicalResistorPart sameIdForeign = makeForeignResistor(board, fixture.part,
                fixture.part.getId());
            sameIdForeign.bindGeometryRealization(geometry);
            rejectForeignCandidate("FOREIGN_SAME_ID", fixture.targetOne.getController(),
                sameIdForeign);
            GeneratedBoardInstance foreignOwner = new LedIndicatorGenerator().generate(3);
            PhysicalBoardRuntime foreignRuntime = foreignOwner.getPhysicalBoardRuntime();
            ReplaceableResistorBoardCapability foreignCapability =
                findResistorCapability(foreignRuntime);
            PhysicalResistorPart foreignBoard = makeForeignResistor(foreignOwner,
                fixture.part, fixture.part.getId() + "_FOREIGN_BOARD");
            foreignBoard.bindGeometryRealization(geometry);
            foreignCapability.getInventory().add(foreignBoard);
            require(foreignRuntime.getPart(foreignBoard.getId()) == foreignBoard &&
                foreignRuntime.getInventoryIdForPart(foreignBoard.getId()) != null &&
                foreignRuntime.getWorkbenchPartsProviderForPart(foreignBoard.getId()) ==
                    foreignCapability &&
                foreignRuntime != runtime && runtime.getPart(foreignBoard.getId()) == null,
                "foreign-board candidate is owned by a distinct constructed runtime");
            rejectForeignCandidate("FOREIGN_BOARD", fixture.targetOne.getController(),
                foreignBoard);
            rejectStaleProvider(fixture);
            rejectPowered(fixture);

            final Runnable crossInstall = new Runnable() {
                public void run() {
                    require(fixture.targetOne.getController().install(fixture.part.getId()),
                        "cross install reached the real target adapter");
                }
            };
            PhysicalMutationScope.FailureStage[] installStages = {
                PhysicalMutationScope.FailureStage.AFTER_PRIMARY_BINDING,
                PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET,
                PhysicalMutationScope.FailureStage.AFTER_ATTACHMENT,
                PhysicalMutationScope.FailureStage.AFTER_SLOT_MOUNT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT,
                PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE,
                PhysicalMutationScope.FailureStage.AFTER_COMMIT
            };
            for (PhysicalMutationScope.FailureStage stage : installStages) {
                int occurrences = stage == PhysicalMutationScope.FailureStage.AFTER_ENDPOINT_RETARGET ||
                    stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_CONNECT ?
                    fixture.part.getTerminalCount() : 1;
                for (int occurrence = 1; occurrence <= occurrences; occurrence++)
                    injected("cross-install", "install", stage, occurrence, crossInstall);
            }

            require(fixture.targetOne.getController().install(fixture.part.getId()),
                "cross install reached the real target adapter after failure cases");
            settle();
            assertCrossInstalled(fixture, sourceInventoryId, sourceInventoryBefore,
                partOrderBefore, backing, geometry, provenance, terminals,
                fixture.targetOne);
            record("cross-install", "COMMIT", 1, "SOURCE_INVENTORY_RETAINED");

            verifyCrossInstalledStress(fixture, stressState, stressBefore);

            ensureUnpowered();
            final Runnable crossRemove = new Runnable() {
                public void run() {
                    require(fixture.targetOne.getController().removeInstalledPart(),
                        "cross removal reached the real target adapter");
                }
            };
            for (PhysicalMutationScope.FailureStage stage : new PhysicalMutationScope.FailureStage[] {
                    PhysicalMutationScope.FailureStage.AFTER_GRAPH_DISCONNECT,
                    PhysicalMutationScope.FailureStage.AFTER_SLOT_CLEAR,
                    PhysicalMutationScope.FailureStage.AFTER_EMPTY_SLOT_REBIND,
                    PhysicalMutationScope.FailureStage.AFTER_COMMIT })
                injected("cross-remove", "remove", stage, 1, crossRemove);
            require(fixture.targetOne.getController().removeInstalledPart(),
                "cross removal reached the real target adapter");
            settle();
            assertCrossLoose(fixture, sourceInventoryId, sourceInventoryBefore,
                partOrderBefore, backing, geometry, provenance, terminals,
                fixture.targetOne);
            record("cross-remove", "COMMIT", 1, "SOURCE_LOOSE_RETAINED");

            require(fixture.targetTwo.getController().removeInstalledPart(),
                "alternate target removal reached the real adapter");
            settle();
            require(fixture.targetOneOriginal.isOriginal(),
                "original-target restriction fixture lost original provenance");
            rejectCandidate("ORIGINAL_TARGET", fixture.targetTwo.getController(),
                fixture.targetOneOriginal, true);
            require(fixture.targetTwo.getController().install(fixture.part.getId()),
                "cross reinstall reached the alternate target adapter");
            settle();
            assertCrossInstalled(fixture, sourceInventoryId, sourceInventoryBefore,
                partOrderBefore, backing, geometry, provenance, terminals,
                fixture.targetTwo);
            record("cross-reinstall", "COMMIT", 1, "ALTERNATE_TARGET");

            ensureUnpowered();
            require(fixture.targetTwo.getController().removeInstalledPart(),
                "alternate cross removal reached the real adapter");
            settle();
            require(fixture.targetTwo.getController().install(fixture.targetTwoOriginal.getId()),
                "alternate original restoration reached the real adapter");
            settle();
            require(fixture.targetOne.getController().install(fixture.targetOneOriginal.getId()),
                "source target original restoration reached the real adapter");
            settle();
            assertOriginalTargetsRestored(fixture);
            return checks;
        } finally {
            // A failure in any checkpoint still leaves the fixture with both
            // target slots and the board power state restored for Alpha's
            // subsequent selected-fault repair.
            ensureUnpowered();
            restoreTarget(fixture.targetTwo, fixture.targetTwoOriginal);
            restoreTarget(fixture.targetOne, fixture.targetOneOriginal);
            restoreStressState(stressState, fixture.part, stressBefore);
            if (savedPower == BoardPowerState.POWERED) {
                sim.setBoardPowerState(BoardPowerState.POWERED);
                settle();
            } else {
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                settle();
            }
            assertOriginalTargetsRestored(fixture);
        }
    }

    private CrossTargetFixture findCrossTargetFixture(PhysicalBoardRuntime runtime) {
        Vector<ReplaceableResistorBoardCapability> capabilities =
            new Vector<ReplaceableResistorBoardCapability>();
        for (PhysicalBoardRuntimeCapability capability : runtime.getCapabilities())
            if (capability instanceof ReplaceableResistorBoardCapability)
                capabilities.add((ReplaceableResistorBoardCapability) capability);
        for (ReplaceableResistorBoardCapability source : capabilities) {
            if (!isDriverResistorTarget(source))
                continue;
            PhysicalResistorPart part = findTenOhmLooseResistor(source);
            PhysicalResistorPart sourceOriginal = source.getSlot().getInstalledPart();
            if (part == null || sourceOriginal == null || source.getController() == null)
                continue;
            for (ReplaceableResistorBoardCapability targetOne : capabilities) {
                if (targetOne == source || !isNmosLoadResistorTarget(targetOne) ||
                        targetOne.getController() == null ||
                        targetOne.getSlot().getInstalledPart() == null ||
                        !sameGeometry(part.getGeometryRealization(),
                            targetOne.getSlot().getPhysicalSlot().getGeometryRealization()))
                    continue;
                for (ReplaceableResistorBoardCapability targetTwo : capabilities) {
                    if (targetTwo == source || targetTwo == targetOne ||
                            targetTwo.getController() == null ||
                            targetTwo.getSlot().getInstalledPart() == null ||
                            !sameGeometry(part.getGeometryRealization(),
                                targetTwo.getSlot().getPhysicalSlot().getGeometryRealization()))
                        continue;
                    return new CrossTargetFixture(source, sourceOriginal, part,
                        targetOne, targetOne.getSlot().getInstalledPart(), targetTwo,
                        targetTwo.getSlot().getInstalledPart());
                }
            }
        }
        throw new AssertionError("U04 catalog mutation: composed board lacks a loose source resistor, " +
            "compatible NMOS-driven load and alternate target");
    }

    private boolean isDriverResistorTarget(ReplaceableResistorBoardCapability capability) {
        return capability != null && (endsWithComponent(capability.getComponentId(), "RB") ||
            endsWithComponent(capability.getComponentId(), "RG"));
    }

    private boolean isNmosLoadResistorTarget(ReplaceableResistorBoardCapability capability) {
        if (capability == null || !endsWithComponent(capability.getComponentId(), "RLOAD"))
            return false;
        GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        if (!(board.getBehaviorContract() instanceof ControlledIndicatorDeviceBehavior))
            return false;
        BoundedAssemblyPlan plan = ((ControlledIndicatorDeviceBehavior)
            board.getBehaviorContract()).getPlan();
        // The NPN channel's finite base drive keeps the 10 Ohm fixture just
        // below its rating. Select the real NMOS-driven load by resolved
        // component type; neither a lower rating nor synthetic power is used.
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            String driverId = plan.idFor(channel.getDriverKey(),
                FunctionalBlockDescriptor.EntityKind.COMPONENT, "Q1");
            String loadId = plan.idFor(channel.getLoadKey(),
                FunctionalBlockDescriptor.EntityKind.COMPONENT, "RLOAD");
            if (capability.getComponentId().equals(loadId) &&
                    "NMOS_TRANSISTOR".equals(board.getBoard().getComponent(driverId).getType()))
                return true;
        }
        return false;
    }

    private static boolean endsWithComponent(String componentId, String localId) {
        return componentId != null && (componentId.equals(localId) ||
            componentId.endsWith("/" + localId));
    }

    private ReplaceableResistorBoardCapability findResistorCapability(
            PhysicalBoardRuntime runtime) {
        for (PhysicalBoardRuntimeCapability capability : runtime.getCapabilities())
            if (capability instanceof ReplaceableResistorBoardCapability)
                return (ReplaceableResistorBoardCapability) capability;
        throw new AssertionError("U04 catalog mutation: detached runtime has no resistor capability");
    }

    private PhysicalResistorPart findTenOhmLooseResistor(
            ReplaceableResistorBoardCapability capability) {
        for (PhysicalResistorPart candidate : capability.getInventory().getLooseParts())
            if (candidate != null && !candidate.isOriginal() &&
                    candidate.getSpecification().getNominalResistanceOhms() == 10.0)
                return candidate;
        return null;
    }

    private PhysicalResistorPart findLowestLooseResistor(
            ReplaceableResistorBoardCapability capability) {
        PhysicalResistorPart result = null;
        for (PhysicalResistorPart candidate : capability.getInventory().getLooseParts()) {
            if (candidate == null || candidate.isOriginal() || candidate.getGeometryRealization() == null)
                continue;
            if (result == null || candidate.getSpecification().getNominalResistanceOhms() <
                    result.getSpecification().getNominalResistanceOhms())
                result = candidate;
        }
        return result;
    }

    private PhysicalPart<?> findNonResistor(PhysicalBoardRuntime runtime) {
        for (PhysicalPart<?> part : runtime.getPhysicalParts())
            if (part != null && !(part instanceof PhysicalResistorPart))
                return part;
        return null;
    }

    private PhysicalResistorPart findLooseMismatchedGeometry(PhysicalBoardRuntime runtime,
            PhysicalGeometryRealization expected) {
        for (PhysicalPart<?> candidate : runtime.getPhysicalParts())
            if (candidate instanceof PhysicalResistorPart && !candidate.isInstalled() &&
                    !sameGeometry(expected, candidate.getGeometryRealization()))
                return (PhysicalResistorPart) candidate;
        return null;
    }

    private void rejectCandidate(String stage, ResistorSlotController target,
            PhysicalPart<?> candidate, boolean invoke) {
        WorkbenchOperation operation = WorkbenchOperation.forPartAtSlot(
            WorkbenchOperation.INSTALL, candidate, target.getComponentId());
        require(!target.isAvailable(operation, null),
            "target adapter exposed an ineligible " + stage + " candidate");
        if (invoke) {
            boolean installed = target.install(candidate.getId());
            require(!installed, "target adapter accepted an ineligible " + stage + " candidate");
        }
        record("cross-eligibility", stage, 1, "REJECTED");
    }

    private void rejectForeignCandidate(String stage, ResistorSlotController target,
            PhysicalResistorPart candidate) {
        WorkbenchOperation operation = WorkbenchOperation.forPartAtSlot(
            WorkbenchOperation.INSTALL, candidate, target.getComponentId());
        require(!target.isAvailable(operation, null),
            "target adapter exposed a foreign " + stage + " candidate");
        boolean rejected = false;
        try {
            PhysicalMutationIntent.prepare(sim.getGeneratedBoardInstance().getPhysicalBoardRuntime(),
                sim.getGeneratedBoardInstance(), sim.getBoardModificationController(),
                target.getMutationSlot(), "install", null, null, candidate);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "shared mutation admission accepted a foreign " + stage + " candidate");
        record("cross-eligibility", stage, 1, "REJECTED");
    }

    private void rejectStaleProvider(CrossTargetFixture fixture) {
        Observation before = new Observation();
        BoardModificationController saved = sim.getBoardModificationController();
        boolean rejected = false;
        try {
            sim.boardModificationController = new BoardModificationController(sim, before.board);
            try {
                rejected = !fixture.targetOne.getController().install(fixture.part.getId());
            } catch (BoardModificationRejectedException expected) {
                rejected = true;
            }
        } finally {
            sim.boardModificationController = saved;
        }
        require(rejected, "retained target provider accepted a stale controller");
        before.assertSame();
        record("cross-eligibility", "STALE_PROVIDER", 1, "REJECTED");
    }

    private void rejectPowered(CrossTargetFixture fixture) {
        settle();
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle();
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                !sim.getBoardPowerController().isElectricallyUnpowered(),
            "powered rejection fixture did not reach actual powered state");
        WorkbenchOperation operation = WorkbenchOperation.forPartAtSlot(
            WorkbenchOperation.INSTALL, fixture.part, fixture.targetOne.getComponentId());
        require(!fixture.targetOne.getController().isAvailable(operation, null),
            "powered target exposed a cross install");
        boolean rejected = false;
        try {
            fixture.targetOne.getController().install(fixture.part.getId());
        } catch (BoardModificationRejectedException expected) {
            rejected = true;
        } finally {
            settle();
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle();
        }
        require(sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "powered rejection fixture did not restore actual isolation");
        require(rejected && fixture.targetOne.getSlot().isEmpty() && !fixture.part.isInstalled(),
            "powered target changed cross ownership");
        GeneratedRuntimeInvariant.verify(sim.getGeneratedBoardInstance(),
            sim.getBoardModificationController(), sim.elmList);
        record("cross-eligibility", "POWERED", 1, "REJECTED");
    }

    private void verifyCrossInstalledStress(CrossTargetFixture fixture,
            ResistorStressState state, StressSnapshot before) {
        ResistorStressDamageSystem stress = fixture.source.getStressDamageSystem();
        GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        require(board.getBehaviorContract() instanceof ControlledIndicatorDeviceBehavior,
            "cross stress requires the public controlled-indicator driver");
        ControlledIndicatorDeviceBehavior behavior =
            (ControlledIndicatorDeviceBehavior) board.getBehaviorContract();
        Vector<Boolean> channelStates = captureChannelStates(behavior);
        try {
            // Drive the actual public control source high before powering the
            // board.  This makes the overload observation solver-backed while
            // leaving the command handles available to restore exactly.
            behavior.setAllChannels(sim, true);
            settle();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle();
            require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                    !sim.getBoardPowerController().isElectricallyUnpowered(),
                "cross stress did not reach actual powered state");
            stress.refreshSolvedMeasurements();
            require(state.getPart() == fixture.part && fixture.part.isInstalled() &&
                    fixture.part.getBoardSlot() == fixture.targetOne.getSlot().getPhysicalSlot(),
                "source stress state did not follow the cross-mounted identity");
            double actualPower = state.getActualPower();
            double ratedPower = fixture.part.getRatedWattage();
            require(!Double.isNaN(actualPower) && !Double.isInfinite(actualPower) &&
                    !Double.isNaN(ratedPower) && !Double.isInfinite(ratedPower) &&
                    actualPower > ratedPower && ratedPower > 0,
                "cross-mounted resistor did not observe a real solver overload; power=" +
                    actualPower + "; rating=" + ratedPower + "; current=" +
                    fixture.part.getElement().getCurrent() + "; target=" +
                    fixture.targetOne.getComponentId());
            double serviceBefore = state.getServiceTime();
            double damageBefore = state.getAccumulatedDamage();
            // One bounded service interval is enough for a positive overload
            // to produce damage; it also proves service time is attached to
            // the same physical part after the cross-slot mount.
            stress.advanceServiceTimeForDeveloperVerification(.5);
            double serviceAfter = state.getServiceTime();
            double damageAfter = state.getAccumulatedDamage();
            require(serviceAfter > serviceBefore && damageAfter > damageBefore &&
                    state.getPart() == fixture.part,
                "cross-mounted resistor did not damage the same part from solver power");
            recordCrossStress(actualPower, ratedPower, serviceBefore, serviceAfter,
                damageBefore, damageAfter);
        } finally {
            settle();
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle();
            require(sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                    sim.getBoardPowerController().isElectricallyUnpowered(),
                "cross stress did not restore actual isolation");
            restoreChannelStates(behavior, channelStates);
            settle();
            restoreStressState(state, fixture.part, before);
        }
    }

    private Vector<Boolean> captureChannelStates(ControlledIndicatorDeviceBehavior behavior) {
        Vector<Boolean> result = new Vector<Boolean>();
        for (ControlledIndicatorChannel channel : behavior.getPlan().getChannels())
            result.add(Boolean.valueOf(behavior.getConstructionReceipt().getDeviceReceipt()
                .getCommand(channel.getControlJoinId()).isHigh()));
        return result;
    }

    private void restoreChannelStates(ControlledIndicatorDeviceBehavior behavior,
            Vector<Boolean> states) {
        Vector<ControlledIndicatorChannel> channels =
            new Vector<ControlledIndicatorChannel>(behavior.getPlan().getChannels());
        require(states.size() == channels.size(), "controlled channel state count changed");
        for (int index = 0; index < channels.size(); index++)
            behavior.getConstructionReceipt().getDeviceReceipt()
                .getCommand(channels.get(index).getControlJoinId())
                .setHigh(states.get(index).booleanValue());
    }

    private void assertCrossInstalled(CrossTargetFixture fixture, String inventoryId,
            Vector<String> inventoryBefore, Vector<String> partOrderBefore,
            PhysicalPartElectricalBacking backing, PhysicalGeometryRealization geometry,
            PhysicalPartProvenance provenance, Vector<PhysicalPartTerminal> terminals,
            ReplaceableResistorBoardCapability target) {
        PhysicalBoardRuntime runtime = sim.getGeneratedBoardInstance().getPhysicalBoardRuntime();
        PhysicalResistorPart targetPart = target.getSlot().getInstalledPart();
        require(targetPart == fixture.part && fixture.part.isInstalled() &&
                fixture.part.getBoardSlot() == target.getSlot().getPhysicalSlot(),
            "cross install changed the physical part identity or mount");
        require(fixture.source.getSlot().getInstalledPart() == fixture.sourceOriginal &&
                fixture.sourceOriginal.isInstalled(), "source slot was altered by cross install");
        require(fixture.source.ownsPart(fixture.part.getId()) &&
                fixture.source.getPart(fixture.part.getId()) == fixture.part &&
                !target.ownsPart(fixture.part.getId()),
            "cross install transferred source inventory membership");
        require(runtime.getWorkbenchPartsProviderForPart(fixture.part.getId()) == fixture.source &&
                runtime.getMutationProviderForPart(fixture.part.getId()) == fixture.source.getController(),
            "cross install changed the source provider identity");
        require(inventoryId.equals(runtime.getInventoryIdForPart(fixture.part.getId())) &&
                inventoryBefore.equals(runtime.getInventoryPartIds(inventoryId)) &&
                partOrderBefore.equals(runtime.getPartOrder()),
            "cross install changed source inventory order or runtime part order");
        require(fixture.part.getElectricalBacking() == backing &&
                fixture.part.getGeometryRealization() == geometry &&
                fixture.part.getProvenance() == provenance,
            "cross install changed backing, geometry, or provenance identity");
        require(fixture.part.getTerminals().size() == terminals.size(),
            "cross install changed terminal count");
        for (int index = 0; index < terminals.size(); index++)
            require(fixture.part.getTerminal(index) == terminals.get(index),
                "cross install changed terminal identity");
        for (CircuitElm element : backing.getCircuitElements())
            require(sim.elmList.contains(element),
                "cross install lost the part backing from active CircuitJS graph");
        assertResistorBindings(target.getComponentId(), fixture.part);
    }

    private void assertCrossLoose(CrossTargetFixture fixture, String inventoryId,
            Vector<String> inventoryBefore, Vector<String> partOrderBefore,
            PhysicalPartElectricalBacking backing, PhysicalGeometryRealization geometry,
            PhysicalPartProvenance provenance, Vector<PhysicalPartTerminal> terminals,
            ReplaceableResistorBoardCapability target) {
        PhysicalResistorPart targetOriginal = target == fixture.targetOne ?
            fixture.targetOneOriginal : fixture.targetTwoOriginal;
        PhysicalBoardRuntime runtime = sim.getGeneratedBoardInstance().getPhysicalBoardRuntime();
        require(target.getSlot().isEmpty() && !fixture.part.isInstalled() &&
                fixture.part.getBoardSlot() == null && targetOriginal != fixture.part &&
                !targetOriginal.isInstalled(), "cross removal did not return a loose source part");
        require(fixture.source.getPart(fixture.part.getId()) == fixture.part &&
                fixture.source.ownsPart(fixture.part.getId()) &&
                runtime.getWorkbenchPartsProviderForPart(fixture.part.getId()) == fixture.source &&
                runtime.getMutationProviderForPart(fixture.part.getId()) == fixture.source.getController(),
            "cross removal lost source ownership");
        require(inventoryId.equals(runtime.getInventoryIdForPart(fixture.part.getId())) &&
                inventoryBefore.equals(runtime.getInventoryPartIds(inventoryId)) &&
                partOrderBefore.equals(runtime.getPartOrder()) &&
                fixture.part.getElectricalBacking() == backing &&
                fixture.part.getGeometryRealization() == geometry &&
                fixture.part.getProvenance() == provenance,
            "cross removal changed source identity or inventory order");
        for (int index = 0; index < terminals.size(); index++)
            require(fixture.part.getTerminal(index) == terminals.get(index),
                "cross removal changed terminal identity");
        record("cross-remove-check", "IDENTITY", 1, "SAME_SOURCE_PART");
    }

    private void assertResistorBindings(String componentId, PhysicalResistorPart part) {
        GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        Vector<GeneratedComponentConnectionBinding> bindings = board.getConnectionBindings()
            .getForComponent(componentId);
        require(bindings.size() == part.getTerminalCount(),
            "cross target has an unexpected terminal binding count");
        for (GeneratedComponentConnectionBinding binding : bindings) {
            BoardPad pad = board.getBoard().getPad(binding.getPadId());
            int terminal = "1".equals(pad.getTerminalId()) ? 0 :
                "2".equals(pad.getTerminalId()) ? 1 : -1;
            require(terminal >= 0 && binding.getComponentEndpoint() ==
                    part.getPublicTerminal(terminal),
                "cross target endpoint mapping did not follow source part identity");
        }
        require(board.getComponentBindings().getElements(componentId).contains(part.getElement()) &&
                board.getComponentBindings().getAuxiliaryElements(componentId)
                    .contains(part.getSecondaryOpenPath().getSimulationElement()),
            "cross target canonical bindings did not follow source backing");
    }

    private void assertOriginalTargetsRestored(CrossTargetFixture fixture) {
        require(fixture.source.getSlot().getInstalledPart() == fixture.sourceOriginal &&
                fixture.sourceOriginal.isInstalled() &&
                fixture.targetOne.getSlot().getInstalledPart() == fixture.targetOneOriginal &&
                fixture.targetOneOriginal.isInstalled() &&
                fixture.targetTwo.getSlot().getInstalledPart() == fixture.targetTwoOriginal &&
                fixture.targetTwoOriginal.isInstalled() && !fixture.part.isInstalled() &&
                fixture.part.getBoardSlot() == null &&
                fixture.source.ownsPart(fixture.part.getId()) &&
                fixture.source.getPart(fixture.part.getId()) == fixture.part,
            "cross fixture did not restore source and target slot ownership");
    }

    private void restoreTarget(ReplaceableResistorBoardCapability target,
            PhysicalResistorPart original) {
        PhysicalResistorPart installed = target.getSlot().getInstalledPart();
        if (installed != null && installed != original) {
            require(target.getController().removeInstalledPart(),
                "cross cleanup could not remove temporary target part");
            settle();
        }
        if (target.getSlot().isEmpty()) {
            require(target.getController().install(original.getId()),
                "cross cleanup could not restore target original");
            settle();
        }
        require(target.getSlot().getInstalledPart() == original,
            "cross cleanup restored the wrong target part");
    }

    private void ensureUnpowered() {
        settle();
        if (!sim.getBoardPowerController().isElectricallyUnpowered()) {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle();
        }
        require(sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "cross fixture requires actual board isolation");
    }

    private void recordCrossStress(double actualPower, double ratedPower,
            double serviceBefore, double serviceAfter, double damageBefore,
            double damageAfter) {
        checks++;
        if (rows.length() > 0) rows.append(',');
        rows.append("{\"provider\":\"").append(providerType)
            .append("\",\"operation\":\"cross-stress\",\"stage\":\"ACTUAL_CURRENT\"")
            .append(",\"occurrence\":1,\"status\":\"DAMAGED_SAME_PART\"")
            .append(",\"actualPowerW\":").append(actualPower)
            .append(",\"ratedPowerW\":").append(ratedPower)
            .append(",\"serviceBefore\":").append(serviceBefore)
            .append(",\"serviceAfter\":").append(serviceAfter)
            .append(",\"damageBefore\":").append(damageBefore)
            .append(",\"damageAfter\":").append(damageAfter)
            .append(",\"samePart\":true}");
    }

    private PhysicalResistorPart makeForeignResistor(GeneratedBoardInstance owner,
            PhysicalResistorPart template, String id) {
        ResistorElm element = DynamicResistorBackingAllocator.create(
            owner.getSimulationElements(),
            template.getSpecification().getNominalResistanceOhms());
        ResistorSecondaryOpenPath openPath = ResistorSecondaryOpenPath.create(
            new CircuitPostMeasurementEndpoint(element, 1));
        ResistorNameplate specification = template.getSpecification();
        ResistorNameplate physicalNameplate = template.getNameplate();
        PhysicalNameplate playerNameplate = new PhysicalNameplate(id,
            specification.getDisplayValue(), "Value", specification.getDisplayValue());
        return new PhysicalResistorPart(id, specification, physicalNameplate, playerNameplate, element, null,
            openPath, ResistorPartLocation.LOOSE,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, id));
    }

    private void restoreStressState(ResistorStressState state, PhysicalResistorPart part,
            StressSnapshot snapshot) {
        state.actualPower = snapshot.actualPower;
        state.stressRatio = snapshot.stressRatio;
        state.accumulatedDamage = snapshot.accumulatedDamage;
        state.serviceTime = snapshot.serviceTime;
        state.failureServiceTime = snapshot.failureServiceTime;
        state.failed = snapshot.failed;
        if (part.getSecondaryOpenPath() != null) {
            if (snapshot.secondaryOpen)
                part.getSecondaryOpenPath().open();
            else
                part.getSecondaryOpenPath().resetForBoardReset();
        }
    }

    private static boolean sameGeometry(PhysicalGeometryRealization first,
            PhysicalGeometryRealization second) {
        return first != null && second != null && first.isEquivalentTo(second);
    }

    private void sameInstanceSuccessor(PhysicalSlotMutationProvider provider, String catalogId) {
        Observation before = new Observation();
        BoardModificationController saved = sim.getBoardModificationController();
        boolean rejected = false;
        try {
            // A retained provider must reject even when its board identity is unchanged.
            sim.boardModificationController = new BoardModificationController(sim, before.board);
            try { ((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalogId); }
            catch (BoardModificationRejectedException expected) { rejected = true; }
        } finally { sim.boardModificationController = saved; }
        require(rejected, "same-instance successor rejects retained provider with a valid catalog ID");
        before.assertSame();
        record("acquire", "OWNER_CHANGE", 1, "REJECTED");
    }

    private void injected(final String operation, final PhysicalMutationScope.FailureStage stage,
            final int occurrence, Runnable action) {
        injected(operation, operation, stage, occurrence, action);
    }

    private void injected(final String operation, final String receiptOperation,
            final PhysicalMutationScope.FailureStage stage, final int occurrence, Runnable action) {
        final Observation before = new Observation();
        final String marker = "u04-injected-" + operation + "-" + stage + "-" + occurrence;
        final int[] seen = {0};
        final boolean[] reached = {false};
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage actual) {
                if (actual != stage || ++seen[0] != occurrence) return;
                reached[0] = true;
                Observation intermediate = new Observation();
                require(!before.state.equals(intermediate.state) || !before.identities.equals(intermediate.identities) ||
                    !before.active.equals(intermediate.active), "injection follows an actual write");
                throw new IllegalStateException(marker);
            }
        });
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalStateException failure) {
            require(marker.equals(failure.getMessage()), "original injected error retained: " + failure);
            require(failure.getSuppressed().length == 0, "compensation has no hidden cleanup failure");
            rejected = true;
        } finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        require(reached[0] && rejected, "required failure checkpoint was reached: " + marker);
        before.assertSame();
        PhysicalBoardRuntime runtime = before.board.getPhysicalBoardRuntime();
        PhysicalMutationReceipt receipt = runtime.getLastMutationReceipt();
        require(receipt != null && receipt.isCompensated() && receipt.isValidationPassed() &&
            receipt.isCompensationComplete() && receiptOperation.equals(receipt.getOperation()),
            "actual compensated receipt for " + marker + "; expected operation=" + receiptOperation +
                "; observed=" + (receipt == null ? "missing" : receipt.getOperation()));
        require(sim.isGeneratedRuntimeSettled() && !sim.activeMeasurementOverlay &&
            !runtime.isMutationInProgress() && !runtime.isMutationQuarantined(), "failed action leaves actionable isolated owner");
        GeneratedRuntimeInvariant.verify(before.board, before.modifications, sim.elmList);
        record(operation, stage.name(), occurrence, "COMPENSATED");
    }

    String rows() { return rows.toString(); }

    int checkCount() { return checks; }

    private void record(String operation, String stage, int occurrence, String status) {
        checks++;
        if (rows.length() > 0) rows.append(',');
        rows.append("{\"provider\":\"").append(providerType).append("\",\"operation\":\"")
            .append(operation).append("\",\"stage\":\"").append(stage).append("\",\"occurrence\":")
            .append(occurrence).append(",\"status\":\"").append(status).append("\"}");
    }

    private void settle() {
        GeneratedRuntimeDeveloperSettlement.settle(sim, sim.getGeneratedBoardInstance(), "U04 catalog compensation");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError("U04 catalog mutation: " + message);
    }

    private static final class CrossTargetFixture {
        final ReplaceableResistorBoardCapability source;
        final PhysicalResistorPart sourceOriginal;
        final PhysicalResistorPart part;
        final ReplaceableResistorBoardCapability targetOne;
        final PhysicalResistorPart targetOneOriginal;
        final ReplaceableResistorBoardCapability targetTwo;
        final PhysicalResistorPart targetTwoOriginal;

        CrossTargetFixture(ReplaceableResistorBoardCapability source,
                PhysicalResistorPart sourceOriginal, PhysicalResistorPart part,
                ReplaceableResistorBoardCapability targetOne,
                PhysicalResistorPart targetOneOriginal,
                ReplaceableResistorBoardCapability targetTwo,
                PhysicalResistorPart targetTwoOriginal) {
            this.source = source;
            this.sourceOriginal = sourceOriginal;
            this.part = part;
            this.targetOne = targetOne;
            this.targetOneOriginal = targetOneOriginal;
            this.targetTwo = targetTwo;
            this.targetTwoOriginal = targetTwoOriginal;
        }
    }

    private static final class StressSnapshot {
        final double actualPower;
        final double stressRatio;
        final double accumulatedDamage;
        final double serviceTime;
        final double failureServiceTime;
        final boolean failed;
        final boolean secondaryOpen;

        StressSnapshot(ResistorStressState state, PhysicalResistorPart part) {
            actualPower = state.getActualPower();
            stressRatio = state.getStressRatio();
            accumulatedDamage = state.getAccumulatedDamage();
            serviceTime = state.getServiceTime();
            failureServiceTime = state.getFailureServiceTime();
            failed = state.isFailed();
            secondaryOpen = part.getSecondaryOpenPath() != null &&
                part.getSecondaryOpenPath().isOpen();
        }
    }

    /** Independent observation of live state; does not read the compensation ledger. */
    private final class Observation {
        final GeneratedBoardInstance board = sim.getGeneratedBoardInstance();
        final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        final BoardModificationController modifications = sim.getBoardModificationController();
        final Vector<CircuitElm> graph = sim.elmList;
        final Vector<CircuitElm> active = new Vector<CircuitElm>(graph);
        final Vector<CircuitElm> canonical = board.getSimulationElements();
        final Vector<Object> identities = new Vector<Object>();
        final String state;
        Observation() {
            PhysicalBoardRuntime runtime = board.getPhysicalBoardRuntime();
            StringBuilder values = new StringBuilder();
            values.append(sim.getBoardPowerController().getState()).append('|').append(sim.t)
                .append('|').append(board.getFaultBinding().isApplied());
            if (board.getFaultBinding().getEffect() instanceof NmosfetDsShortFaultEffect)
                values.append('|').append(((NmosfetDsShortFaultEffect)board.getFaultBinding().getEffect()).isBoardPathEnabled());
            for (CircuitElm element : canonical) values.append('|').append(element.dump());
            for (GeneratedComponentConnectionBinding binding : board.getConnectionBindings().getAll()) {
                identities.add(binding); identities.add(binding.getBoardEndpoint()); identities.add(binding.getComponentEndpoint());
                values.append('|').append(binding.getPadId()).append(':')
                    .append(modifications.isLeadConnected(binding.getComponentId(), binding.getPadId()));
            }
            for (PhysicalPart<?> part : runtime.getPhysicalParts()) {
                identities.add(part); identities.add(part.getMountState()); identities.add(part.getBoardSlot());
                identities.add(part.getGeometryRealization());
                for (PhysicalPartTerminal terminal : part.getTerminals()) {
                    identities.add(terminal); identities.add(terminal.getEndpoint());
                }
                values.append('|').append(part.getId()).append(':').append(part.isInstalled()).append(':')
                    .append(part.isFaulted()).append(':').append(runtime.getInventoryIdForPart(part.getId()));
            }
            for (String id : board.getBoard().getComponentIds()) {
                identities.add(runtime.getSlot(id)); identities.add(runtime.getInstalledPart(id));
                if (board.getComponentBindings().hasComponentBinding(id)) {
                    identities.addAll(board.getComponentBindings().getElements(id));
                    identities.addAll(board.getComponentBindings().getAuxiliaryElements(id));
                }
                values.append('|').append(id).append(':').append(runtime.getNextPartSerial(id + "_CATALOG_PART"));
            }
            for (String id : runtime.getInventoryIds())
                values.append('|').append(id).append(':').append(runtime.getInventoryPartIds(id));
            state = values.toString();
        }
        void assertSame() {
            Observation after = new Observation();
            require(board == after.board && challenge == after.challenge && modifications == after.modifications && graph == after.graph,
                "exact current owners survive failure");
            require(active.equals(after.active) && canonical.equals(after.canonical) && identities.equals(after.identities),
                "exact graph, part, binding, endpoint, geometry and mount identities survive failure");
            require(state.equals(after.state), "electrical, attachment, inventory, serial and fault state survive failure");
        }
    }
}
