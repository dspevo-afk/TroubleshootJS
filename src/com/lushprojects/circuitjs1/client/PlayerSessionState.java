package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

/** Current-owner capture and private fresh-owner reconstruction, never publication. */
final class PlayerSessionState {
    private static CirSim restoringOwner;

    private PlayerSessionState() { }

    /** Private reconstruction time is not player service time. */
    static boolean isRestoring(CirSim sim) { return sim != null && restoringOwner == sim; }

    static PlayerSessionSave capture(CirSim sim, PlayerLaunchRequest request,
            Vector<PlayerSessionSave.Operation> history, String build) {
        GeneratedBoardInstance owner = requireCurrent(sim);
        if (request == null || request.candidateSearch || request.seed != owner.getSeed() ||
                !request.familyId.equals(owner.getCircuitFamilyId()))
            throw invalid("Session replay does not identify the current board");
        requirePristineCopper(owner);
        GeneratedRuntimeInvariant.verify(sim, owner, sim.getBoardModificationController(), sim.elmList);
        Vector<PlayerSessionSave.Source> sources = sources(owner);
        Vector<PlayerSessionSave.Stress> stress = stress(owner);
        Vector<PlayerSessionSave.Fuse> fuses = fuses(owner);
        return new PlayerSessionSave(request.replay(), build,
            PlayerSessionFingerprint.of(PhysicalBoardFingerprint.of(owner)),
            signature(sim, owner, sources, stress, fuses), history, sources, stress, fuses);
    }

    /** Caller owns Staged.abort on failure and attaches the workbench only after success. */
    static void restore(CirSim sim, GeneratedBoardInstance candidate, PlayerSessionSave save) {
        if (sim == null || save == null || candidate == null || restoringOwner != null ||
                !FreshGeneratedRuntimeInstallation.isInProgress(sim) ||
                !sim.developerVerifierRunning || requireCurrent(sim) != candidate)
            throw invalid("Session reconstruction requires its private fresh owner");
        PlayerLaunchRequest request = PlayerLaunchRequest.parse(save.replay);
        if (request.seed != candidate.getSeed() ||
                !request.familyId.equals(candidate.getCircuitFamilyId()) ||
                !save.realization.equals(PlayerSessionFingerprint.of(PhysicalBoardFingerprint.of(candidate))))
            throw invalid("Session physical realization does not match its replay");
        requirePristineCopper(candidate);
        validateSources(candidate, save.getSources());
        // A failed or incomplete replay must never append to a previously used inventory.
        for (PhysicalPart<?> part : candidate.getPhysicalBoardRuntime().getPhysicalParts())
            if (!part.isOriginal()) throw invalid("Session reconstruction requires fresh inventory");
        if (!sim.getBoardModificationController().isFullyRestored())
            throw invalid("Session reconstruction requires fresh physical connections");
        Vector<PlayerSessionSave.Operation> history = save.getHistory();
        validateInventoryPopulation(candidate.getPhysicalBoardRuntime().getPhysicalParts().size(), history);
        restoringOwner = sim;
        try {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            settle(sim, candidate, "isolate");
            if (!sim.getBoardPowerController().isElectricallyUnpowered())
                throw invalid("Session reconstruction failed to isolate its sources");
            restart(sim, candidate);
            int index = 0;
            for (PlayerSessionSave.Operation operation : history) {
                requirePrivateOwner(sim, candidate);
                checkpoint(sim);
                replay(sim, candidate, operation);
                settle(sim, candidate, "operation " + index++);
                checkpoint(sim);
            }
            // Historical input actions may have stepped storage. The advertised mode restarts it.
            restart(sim, candidate);
            restoreStress(sim, candidate, save.getStress());
            restoreFuses(candidate, save.getFuses());
            sim.requestGeneratedBoardVerification();
            settle(sim, candidate, "permanent damage");
            for (PlayerSessionSave.Source source : save.getSources()) {
                sim.changeBenchSource(candidate, source.id, Boolean.valueOf(source.connected),
                    Double.isNaN(source.limitAmps) ? null : Double.valueOf(source.limitAmps));
                settle(sim, candidate, "source");
            }
            candidate.getPhysicalBoardRuntime().synchronizeSimulationTime(sim.t);
            sim.getGeneratedChallengeController().invalidateCustomerRetest();
            GeneratedRuntimeInvariant.verify(sim, candidate, sim.getBoardModificationController(), sim.elmList);
            requirePristineCopper(candidate);
            String actual = signature(sim, candidate, sources(candidate), stress(candidate), fuses(candidate));
            if (!save.stateSignature.equals(actual))
                throw invalid("Session reconstruction does not match the saved semantic state");
            checkpoint(sim);
        } finally {
            restoringOwner = null;
        }
    }

    /** All acquired parts remain owned across removal and reset; reject before replay mutates. */
    static void validateInventoryPopulation(int originalParts, Vector<PlayerSessionSave.Operation> history) {
        if (originalParts < 0 || originalParts > PlayerSessionSave.MAX_PARTS || history == null)
            throw invalid("Session parts inventory exceeds the supported limit");
        int retained = originalParts;
        for (PlayerSessionSave.Operation operation : history)
            if (("ACQUIRE".equals(operation.kind) || "CATALOG".equals(operation.kind)) &&
                    ++retained > PlayerSessionSave.MAX_PARTS)
                throw invalid("Session parts inventory exceeds the supported limit");
    }

    private static void replay(CirSim sim, GeneratedBoardInstance owner,
            PlayerSessionSave.Operation operation) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        BoardModificationController modifications = sim.getBoardModificationController();
        String kind = operation.kind;
        if ("SOURCE".equals(kind)) {
            // Source commands are retained in history, but playback runs isolated. Final source
            // controls below are authoritative; electrical damage is restored from its owner.
            ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(operation.owner);
            if (!binding.hasControl() || (binding.getLimitedSupply() == null) != "NONE".equals(operation.result))
                throw invalid("Session source command has no matching source control");
            return;
        }
        if ("INPUT".equals(kind)) {
            if (GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(operation.owner) ||
                    owner.getOperationCatalog() == null ||
                    owner.getOperationCatalog().find(operation.owner) == null ||
                    !sim.invokeGeneratedPlayerOperation(operation.owner))
                throw invalid("Unsupported session input command");
            return;
        }
        if ("RESET".equals(kind)) { restart(sim, owner); return; }
        if (!sim.getBoardPowerController().isElectricallyUnpowered())
            throw invalid("Session physical reconstruction lost source isolation");
        if ("LEAD".equals(kind)) {
            boolean changed = "CONNECTED".equals(operation.result) ?
                modifications.reconnectLead(operation.owner, operation.argument) :
                modifications.liftLead(operation.owner, operation.argument);
            requireChanged(changed); return;
        }
        if ("GRAPH_REMOVE".equals(kind)) {
            requireChanged(modifications.removeComponent(operation.owner)); return;
        }
        if ("GRAPH_RESTORE".equals(kind)) {
            requireChanged(modifications.restoreComponent(operation.owner)); return;
        }
        PhysicalSlotMutationProvider provider = runtime.getMutationProvider(operation.owner);
        if (provider == null) throw invalid("Session operation has no physical provider");
        if ("REMOVE".equals(kind)) {
            PhysicalPart<?> installed = runtime.getInstalledPart(operation.owner);
            if (installed == null || !installed.getId().equals(operation.argument))
                throw invalid("Session removal has a different installed part");
            requireChanged(provider.removeInstalledPart()); return;
        }
        if ("INSTALL".equals(kind)) {
            requireChanged(provider.install(operation.argument)); return;
        }
        if ("ACQUIRE".equals(kind) || "CATALOG".equals(kind)) {
            if (runtime.getPart(operation.result) != null)
                throw invalid("Session acquisition reuses an existing part identity");
            int previousCount = runtime.getPartOrder().size();
            PhysicalPart<?> acquired;
            if ("ACQUIRE".equals(kind)) {
                if (!(provider instanceof CatalogAcquisitionProvider))
                    throw invalid("Session provider cannot acquire a loose part");
                acquired = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(operation.argument);
            } else {
                requireChanged(provider.installNewFromCatalog(operation.argument));
                acquired = runtime.getInstalledPart(operation.owner);
            }
            Vector<String> order = runtime.getPartOrder();
            if (acquired == null || !operation.result.equals(acquired.getId()) ||
                    order.size() != previousCount + 1 || !operation.result.equals(order.lastElement()) ||
                    runtime.getPart(operation.result) != acquired || !provider.ownsPart(operation.result))
                throw invalid("Session acquisition changed its identity or inventory order");
            return;
        }
        throw invalid("Unsupported session operation");
    }

    private static void restart(CirSim sim, GeneratedBoardInstance owner) {
        if (!sim.getBoardPowerController().isElectricallyUnpowered())
            throw invalid("Session transient restart requires isolated sources");
        // resetAction owns time, solver retirement, element reset, and capability lifecycle.
        sim.resetAction();
        settle(sim, owner, "transient restart");
    }

    private static void settle(CirSim sim, GeneratedBoardInstance owner, String phase) {
        checkpoint(sim);
        requirePrivateOwner(sim, owner);
        if (!sim.isGeneratedRuntimeSettled())
            GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "session " + phase);
        requirePrivateOwner(sim, owner);
        checkpoint(sim);
    }

    private static void checkpoint(CirSim sim) {
        if (sim.generationCoordinator != null && sim.generationCoordinator.isRunning())
            sim.generationCoordinator.getJob().checkpoint();
    }

    private static void requirePrivateOwner(CirSim sim, GeneratedBoardInstance owner) {
        if (!isRestoring(sim) || !FreshGeneratedRuntimeInstallation.isInProgress(sim) ||
                !sim.developerVerifierRunning || sim.getGeneratedBoardInstance() != owner ||
                sim.getGeneratedChallengeController() == null ||
                sim.getGeneratedChallengeController().getInstanceForRuntimeValidation() != owner)
            throw invalid("Session reconstruction lost its private owner");
    }

    private static GeneratedBoardInstance requireCurrent(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null ||
                sim.getGeneratedBoardInstance().isDeveloperOnlyFaultRoute() ||
                sim.activeMeasurementOverlay || !sim.isGeneratedRuntimeSettled())
            throw invalid("Session state requires a settled normal board");
        return sim.getGeneratedBoardInstance();
    }

    private static void requirePristineCopper(GeneratedBoardInstance owner) {
        PcbConductorGraph.Snapshot copper = owner.getCurrentConductorSnapshot();
        if (copper == null || copper.getGraph() != owner.getPristineConductorGraph() ||
                !copper.getCutEdgeIds().isEmpty())
            throw invalid("Session format does not support live copper cuts");
    }

    private static Vector<PlayerSessionSave.Source> sources(GeneratedBoardInstance owner) {
        Vector<PlayerSessionSave.Source> result = new Vector<PlayerSessionSave.Source>();
        Vector<String> ids = owner.getBoard().getPowerInputIds();
        Collections.sort(ids);
        for (String id : ids) {
            ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(id);
            if (!binding.hasControl()) throw invalid("Session source lacks an executable control");
            LimitedDcSupplyElm supply = binding.getLimitedSupply();
            result.add(new PlayerSessionSave.Source(id, binding.isConnected(),
                supply == null ? Double.NaN : supply.getLimitAmps()));
        }
        return result;
    }

    private static void validateSources(GeneratedBoardInstance owner,
            Vector<PlayerSessionSave.Source> saved) {
        Vector<PlayerSessionSave.Source> expected = sources(owner);
        if (saved.size() != expected.size()) throw invalid("Session source population is incomplete");
        HashSet<String> ids = new HashSet<String>();
        for (PlayerSessionSave.Source source : saved) {
            ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(source.id);
            if (!ids.add(source.id) || !binding.hasControl() ||
                    (binding.getLimitedSupply() == null) != Double.isNaN(source.limitAmps))
                throw invalid("Session source control does not match its board");
        }
    }

    /** Enumerate each source inventory, including parts mounted in another slot or left loose. */
    private static HashMap<String, ResistorStressState> stressStates(GeneratedBoardInstance owner) {
        HashMap<String, ResistorStressState> states = new HashMap<String, ResistorStressState>();
        for (PhysicalBoardRuntimeCapability capability : owner.getPhysicalBoardRuntime().getCapabilities()) {
            if (!(capability instanceof ReplaceableResistorBoardCapability)) continue;
            ReplaceableResistorBoardCapability resistor = (ReplaceableResistorBoardCapability)capability;
            ResistorStressDamageSystem system = resistor.getStressDamageSystem();
            for (PhysicalResistorPart part : resistor.getInventory().getAll()) {
                ResistorStressState state = system.getState(part.getId());
                if (!system.ownsState(part) || state.getPart() != part || states.put(part.getId(), state) != null)
                    throw invalid("Session resistor stress ownership is inconsistent");
            }
        }
        return states;
    }

    private static Vector<PlayerSessionSave.Stress> stress(GeneratedBoardInstance owner) {
        HashMap<String, ResistorStressState> states = stressStates(owner);
        Vector<String> ids = new Vector<String>(states.keySet());
        Collections.sort(ids);
        Vector<PlayerSessionSave.Stress> result = new Vector<PlayerSessionSave.Stress>();
        for (String id : ids) {
            ResistorStressState state = states.get(id);
            if (state.getPart().getSecondaryOpenPath().isOpen() != state.isFailed())
                throw invalid("Session resistor failure disagrees with its electrical path");
            result.add(new PlayerSessionSave.Stress(id, state.getAccumulatedDamage(),
                state.getServiceTime(), state.isFailed(), state.getFailureServiceTime()));
        }
        return result;
    }

    private static void restoreStress(CirSim sim, GeneratedBoardInstance owner,
            Vector<PlayerSessionSave.Stress> saved) {
        HashMap<String, ResistorStressState> states = stressStates(owner);
        if (saved.size() != states.size()) throw invalid("Session stress population is incomplete");
        HashSet<String> seen = new HashSet<String>();
        for (PlayerSessionSave.Stress value : saved)
            if (!states.containsKey(value.partId) || !seen.add(value.partId))
                throw invalid("Session stress references a foreign or duplicate part");
        for (PlayerSessionSave.Stress value : saved) {
            ResistorStressState state = states.get(value.partId);
            state.accumulatedDamage = value.damage;
            state.serviceTime = value.serviceTime;
            state.failureServiceTime = value.failureTime;
            state.failed = value.failed;
            state.actualPower = 0;
            state.stressRatio = 0;
            if (value.failed) state.getPart().getSecondaryOpenPath().open();
            else state.getPart().getSecondaryOpenPath().resetForBoardReset();
        }
        owner.getPhysicalBoardRuntime().synchronizeSimulationTime(sim.t);
    }

    /** Fuse state belongs to each physical instance, including removed originals and loose purchases. */
    private static HashMap<String, ProtectionFuseElm> fuseElements(GeneratedBoardInstance owner) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        HashMap<String, ProtectionFuseElm> result = new HashMap<String, ProtectionFuseElm>();
        Vector<ProtectionFuseElm> claimed = new Vector<ProtectionFuseElm>();
        for (PhysicalPart<?> part : runtime.getPhysicalParts()) {
            ProtectionFuseElm fuse = null;
            for (CircuitElm element : part.getElectricalBacking().getCircuitElements()) {
                if (!(element instanceof ProtectionFuseElm)) continue;
                if (fuse != null || claimed.contains(element) || !owner.ownsRuntimeSimulationElement(element))
                    throw invalid("Session fuse has ambiguous or foreign physical backing");
                fuse = (ProtectionFuseElm)element;
            }
            if (fuse == null) continue;
            if (runtime.getPart(part.getId()) != part || !runtime.isPartOwnedByRegisteredProvider(part) ||
                    result.put(part.getId(), fuse) != null)
                throw invalid("Session fuse has no unique physical inventory owner");
            claimed.add(fuse);
        }
        // Every supported fuse in the graph needs a retrievable physical owner, never an index.
        for (CircuitElm element : owner.getSimulationElements())
            if (element instanceof ProtectionFuseElm && !claimed.contains(element))
                throw invalid("Session fuse is not owned by a supported physical part");
        return result;
    }

    private static Vector<PlayerSessionSave.Fuse> fuses(GeneratedBoardInstance owner) {
        HashMap<String, ProtectionFuseElm> elements = fuseElements(owner);
        Vector<String> ids = new Vector<String>(elements.keySet());
        Collections.sort(ids);
        Vector<PlayerSessionSave.Fuse> result = new Vector<PlayerSessionSave.Fuse>();
        for (String id : ids) {
            ProtectionFuseElm fuse = elements.get(id);
            requireFuseState(fuse, fuse.heat, fuse.blown);
            result.add(new PlayerSessionSave.Fuse(id, fuse.heat, fuse.blown));
        }
        return result;
    }

    private static void restoreFuses(GeneratedBoardInstance owner, Vector<PlayerSessionSave.Fuse> saved) {
        HashMap<String, ProtectionFuseElm> elements = fuseElements(owner);
        if (saved.size() != elements.size()) throw invalid("Session fuse population is incomplete");
        HashSet<String> seen = new HashSet<String>();
        for (PlayerSessionSave.Fuse value : saved) {
            ProtectionFuseElm fuse = elements.get(value.partId);
            if (fuse == null || !seen.add(value.partId))
                throw invalid("Session fuse references a foreign or duplicate part");
            requireFuseState(fuse, value.heat, value.blown);
        }
        for (PlayerSessionSave.Fuse value : saved) {
            ProtectionFuseElm fuse = elements.get(value.partId);
            fuse.heat = value.heat;
            fuse.blown = value.blown;
        }
    }

    private static void requireFuseState(ProtectionFuseElm fuse, double heat, boolean blown) {
        if (!LowVoltageSourceModel.finite(fuse.i2t) || fuse.i2t <= 0 ||
                !LowVoltageSourceModel.finite(heat) || heat < 0 || blown != (heat >= fuse.i2t))
            throw invalid("Session fuse state disagrees with its physical failure threshold");
    }

    /** Independent final-state oracle: no journal entries, solved voltages, or node identities. */
    static String semanticSignature(CirSim sim) {
        GeneratedBoardInstance owner = requireCurrent(sim);
        return signature(sim, owner, sources(owner), stress(owner), fuses(owner));
    }

    private static String signature(CirSim sim, GeneratedBoardInstance owner,
            Vector<PlayerSessionSave.Source> sources, Vector<PlayerSessionSave.Stress> stress,
            Vector<PlayerSessionSave.Fuse> fuses) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        StringBuilder out = new StringBuilder("player-semantic-state@1;");
        field(out, PhysicalBoardFingerprint.of(owner));
        field(out, PcbConductorState.encode(owner.getCurrentConductorSnapshot()));
        field(out, owner.getFamilyState() == null ? "NONE" : owner.getFamilyState().getSessionInputSignature());
        for (String id : runtime.getSlotOrder()) {
            PhysicalBoardSlot slot = runtime.getSlot(id);
            field(out, "SLOT"); field(out, slot.getId()); field(out, slot.getComponentId());
            field(out, slot.getPhysicalPackage().getId());
            field(out, slot.getInstalledPart() == null ? "" : slot.getInstalledPart().getId());
            geometry(out, slot.getGeometryRealization());
            Vector<String> pads = slot.getPadIds(), terminals = slot.getTerminalIds(), nets = slot.getNetIds();
            for (int i = 0; i < pads.size(); i++) {
                field(out, pads.get(i)); field(out, terminals.get(i)); field(out, nets.get(i));
            }
        }
        for (String id : runtime.getPartOrder()) {
            PhysicalPart<?> part = runtime.getPart(id);
            field(out, "PART"); field(out, id); field(out, runtime.getInventoryIdForPart(id));
            field(out, part.getSpecification().getSpecificationId());
            field(out, part.getPackage().getId()); field(out, part.getOrientation().name());
            field(out, part.getProvenance().getKind()); field(out, part.getProvenance().getSourceId());
            field(out, part.getBoardSlot() == null ? "" : part.getBoardSlot().getId());
            field(out, part.getFailureState().getKind()); field(out, Boolean.toString(part.getFailureState().isFailed()));
            geometry(out, part.getGeometryRealization());
            for (PhysicalPartTerminal terminal : part.getTerminals()) {
                field(out, terminal.getId()); field(out, terminal.getTerminalName());
            }
        }
        Vector<String> inventories = runtime.getInventoryIds();
        Collections.sort(inventories);
        for (String id : inventories) {
            field(out, "INVENTORY"); field(out, id);
            for (String part : runtime.getInventoryPartIds(id)) field(out, part);
        }
        for (String namespace : runtime.getPartSerialNamespaces()) {
            field(out, "SERIAL"); field(out, namespace);
            field(out, runtime.getNextPartSerial(namespace).toString());
        }
        Vector<String> componentIds = owner.getBoard().getComponentIds();
        Collections.sort(componentIds);
        for (String id : componentIds) {
            Vector<GeneratedComponentConnectionBinding> bindings = owner.getConnectionBindings().getForComponentOrEmpty(id);
            Vector<String> records = new Vector<String>();
            for (GeneratedComponentConnectionBinding binding : bindings) {
                StringBuilder record = new StringBuilder();
                field(record, binding.getPadId());
                field(record, Boolean.toString(sim.getBoardModificationController().isLeadConnected(id, binding.getPadId())));
                Vector<String> terminalIds = new Vector<String>();
                for (PhysicalPart<?> part : runtime.getPhysicalParts())
                    for (PhysicalPartTerminal terminal : part.getTerminals())
                        if (GeneratedComponentConnectionBindings.sameEndpoint(terminal.getEndpoint(), binding.getComponentEndpoint()))
                            terminalIds.add(terminal.getId());
                Collections.sort(terminalIds);
                for (String terminal : terminalIds) field(record, terminal);
                records.add(record.toString());
            }
            Collections.sort(records);
            field(out, "CONNECTIONS"); field(out, id);
            for (String record : records) field(out, record);
        }
        for (PlayerSessionSave.Source source : sources) {
            field(out, "SOURCE"); field(out, source.id); field(out, Boolean.toString(source.connected));
            field(out, number(source.limitAmps));
        }
        for (PlayerSessionSave.Stress state : stress) {
            field(out, "STRESS"); field(out, state.partId); field(out, number(state.damage));
            field(out, number(state.serviceTime)); field(out, Boolean.toString(state.failed));
            field(out, number(state.failureTime));
        }
        for (PlayerSessionSave.Fuse fuse : fuses) {
            field(out, "FUSE"); field(out, fuse.partId); field(out, number(fuse.heat));
            field(out, Boolean.toString(fuse.blown));
        }
        // Private canonical topology and failure ownership never enter the exported artifact.
        return PlayerSessionFingerprint.of(out.toString());
    }

    private static void geometry(StringBuilder out, PhysicalGeometryRealization geometry) {
        field(out, geometry == null ? "UNFORMED" : geometry.fingerprint());
    }
    private static String number(double value) {
        return Double.isNaN(value) ? "NONE" : PlayerSessionSave.number(value);
    }
    private static void field(StringBuilder out, String value) {
        if (value == null) { out.append("-1:"); return; }
        out.append(value.length()).append(':').append(value);
    }
    private static void requireChanged(boolean changed) {
        if (!changed) throw invalid("Session history contains an unsuccessful physical operation");
    }
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
