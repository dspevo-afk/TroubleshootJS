package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Pure reference-based validation for one generated board envelope.
 *
 * <p>This verifier deliberately follows references already owned by the
 * generated board.  It does not rebuild a graph, infer ownership from labels,
 * or mutate any registry.  The bounded resistor scope calls the three
 * argument form while its mutation flag is held; callers that need a settled
 * public-owner check use the CirSim overload.</p>
 */
final class GeneratedRuntimeInvariant {
    private GeneratedRuntimeInvariant() { }

    static void verify(GeneratedBoardInstance instance,
            BoardModificationController modifications, Vector<CircuitElm> activeElements) {
        if (instance == null || modifications == null || activeElements == null)
            throw new IllegalArgumentException("Missing generated runtime invariant context");
        if (modifications.getInstanceForRuntimeValidation() != instance)
            throw new IllegalStateException("Modification controller belongs to another generated board");
        require(instance.getConnectionBindings().getBoardForRuntimeValidation() == instance.getBoard() &&
                instance.getExternalPowerBindings().getBoardForRuntimeValidation() == instance.getBoard(),
            "Connection or power bindings belong to another board");
        if (instance.getPhysicalBoardRuntime().getBoard() != instance.getBoard())
            throw new IllegalStateException("Physical runtime belongs to another board");
        if (instance.getComponentBindings().getBoardForRuntimeValidation() != instance.getBoard())
            throw new IllegalStateException("Component bindings belong to another board");

        Vector<CircuitElm> canonical = instance.getSimulationElements();
        requireUniqueElements(canonical, "canonical generated elements");
        requireUniqueElements(activeElements, "active generated elements");
        for (CircuitElm element : activeElements)
            require(containsIdentity(canonical, element),
                "Active graph contains a foreign generated element");

        Vector<CircuitElm> claimed = new Vector<CircuitElm>();
        verifyComponentBindings(instance, canonical, claimed);
        verifyPowerBindings(instance, canonical, claimed);
        verifyConnectionBindings(instance, canonical, activeElements, claimed);
        verifyInstalledComponentBindings(instance);
        verifyModificationState(instance, modifications, activeElements);
        verifyPhysicalRuntime(instance, canonical, activeElements);
        verifyFaultOwnership(instance, canonical);
    }

    /** Settled-owner form used by simulator verification and independent gates. */
    static void verify(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, Vector<CircuitElm> activeElements) {
        if (sim == null || instance == null || sim.getGeneratedBoardInstance() != instance)
            throw new IllegalStateException("Generated runtime invariant has no current owner");
        if (modifications == null || sim.getBoardModificationController() != modifications)
            throw new IllegalStateException("Generated runtime invariant has no current modification owner");
        if (activeElements != sim.elmList)
            throw new IllegalStateException("Generated runtime invariant graph is not the live graph");
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (challenge != null && challenge.getInstanceForRuntimeValidation() != instance)
            throw new IllegalStateException("Generated challenge belongs to another generated board");
        if (sim.getBoardPowerController().getBindingsForDeveloperVerification() !=
                instance.getExternalPowerBindings())
            throw new IllegalStateException("Generated power controller belongs to another board");
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("Generated runtime invariant cannot run over a measurement overlay");
        if (instance.getPhysicalBoardRuntime().isMutationInProgress())
            throw new IllegalStateException("Generated runtime invariant cannot run during mutation");
        if (!sim.isGeneratedRuntimeSettled())
            throw new IllegalStateException("Generated runtime invariant requires settled generated runtime");
        verify(instance, modifications, activeElements);
        PcbConductorProjection.audit(instance, activeElements);
    }

    private static void verifyComponentBindings(GeneratedBoardInstance instance,
            Vector<CircuitElm> canonical, Vector<CircuitElm> claimed) {
        TroubleshootBoard board = instance.getBoard();
        GeneratedComponentBindings bindings = instance.getComponentBindings();
        for (String componentId : board.getComponentIds()) {
            // Connector/foundation components intentionally have no mutable
            // component-element binding.  Their board-facing endpoints are
            // checked against the installed physical part below instead.
            if (!bindings.hasComponentBinding(componentId))
                continue;
            Vector<CircuitElm> primary = bindings.getElements(componentId);
            require(!primary.isEmpty(), "Component has no primary binding: " + componentId);
            verifyOwnedUniqueElements(primary, canonical, claimed,
                "component binding " + componentId);
            Vector<CircuitElm> auxiliary = bindings.getAuxiliaryElements(componentId);
            verifyOwnedUniqueElements(auxiliary, canonical, claimed,
                "auxiliary component binding " + componentId);
        }

    }

    private static void verifyInstalledComponentBindings(GeneratedBoardInstance instance) {
        GeneratedComponentBindings bindings = instance.getComponentBindings();
        for (PhysicalBoardSlot slot : instance.getPhysicalBoardRuntime().getSlots()) {
            if (slot == null || !slot.isOccupied())
                continue;
            PhysicalPart<?> part = slot.getInstalledPart();
            if (!bindings.hasComponentBinding(slot.getComponentId())) {
                require(part instanceof FixedPhysicalPart && PhysicalPartProvenance.FIXED_GENERATED
                        .equals(part.getProvenance().getKind()),
                    "Mutable installed part has no component binding: " + slot.getComponentId());
                continue;
            }
            Vector<CircuitElm> backing = part.getElectricalBacking().getCircuitElements();
            Vector<CircuitElm> owned = bindings.getElements(slot.getComponentId());
            owned.addAll(bindings.getAuxiliaryElements(slot.getComponentId()));
            for (CircuitElm element : owned)
                require(containsIdentity(backing, element),
                    "Component binding disagrees with installed part backing: " +
                    slot.getComponentId());
        }
    }

    private static void verifyPowerBindings(GeneratedBoardInstance instance,
            Vector<CircuitElm> canonical, Vector<CircuitElm> claimed) {
        TroubleshootBoard board = instance.getBoard();
        GeneratedExternalPowerBindings power = instance.getExternalPowerBindings();
        Boolean connected = null;
        for (String powerInputId : board.getPowerInputIds()) {
            ExternalPowerSimulationBinding binding;
            try {
                binding = power.getBinding(powerInputId);
            } catch (Throwable failure) {
                throw new IllegalStateException("Missing power binding: " + powerInputId, failure);
            }
            require(binding != null && binding.hasControl(),
                "Power input has no executable control: " + powerInputId);
            Vector<CircuitElm> backing = binding.getBackingElements();
            require(!backing.isEmpty(), "Power input has no backing: " + powerInputId);
            verifyOwnedUniqueElements(backing, canonical, claimed,
                "power backing " + powerInputId);
            boolean inputConnected = binding.isConnected();
            if (connected == null)
                connected = Boolean.valueOf(inputConnected);
            else
                require(connected.booleanValue() == inputConnected,
                    "Power controls disagree across generated inputs");
        }
    }

    private static void verifyConnectionBindings(GeneratedBoardInstance instance,
            Vector<CircuitElm> canonical, Vector<CircuitElm> activeElements,
            Vector<CircuitElm> claimed) {
        TroubleshootBoard board = instance.getBoard();
        GeneratedComponentConnectionBindings bindings = instance.getConnectionBindings();
        for (String padId : board.getPadIds()) {
            GeneratedComponentConnectionBinding binding = bindings.getOrNull(padId);
            BoardPad pad = board.getPad(padId);
            require(pad != null, "Generated board has an unknown pad: " + padId);
            CircuitMeasurementEndpoint boardEndpoint = board.getSimulationBindings()
                .getEndpoint(padId);
            require(boardEndpoint != null, "Board pad has no simulation endpoint: " + padId);
            verifyEndpoint(boardEndpoint, canonical, padId);
            require(GeneratedComponentConnectionBindings.sameEndpoint(
                    instance.getDeveloperBoardEndpointOracle().getEndpoint(padId),
                    boardEndpoint),
                "Board endpoint changed the immutable board endpoint: " + padId);

            if (binding == null) {
                // A fixed/foundation pad is not detachable.  Its board
                // endpoint must continue to be the terminal of the installed
                // physical part, when the component has a physical slot.
                verifyFoundationPadEndpoint(instance, pad, boardEndpoint);
                continue;
            }

            require(pad.getComponentId().equals(binding.getComponentId()),
                "Connection binding component disagrees with pad: " + padId);
            require(GeneratedComponentConnectionBindings.sameEndpoint(
                    boardEndpoint, binding.getBoardEndpoint()),
                "Connection binding board endpoint disagrees with pad: " + padId);
            require(GeneratedComponentConnectionBindings.sameEndpoint(
                    instance.getDeveloperBoardEndpointOracle().getEndpoint(padId),
                    binding.getBoardEndpoint()),
                "Connection binding changed the immutable board endpoint: " + padId);
            verifyEndpoint(binding.getBoardEndpoint(), canonical, padId);
            verifyEndpoint(binding.getComponentEndpoint(), canonical, padId);
            PhysicalPart<?> installed = instance.getPhysicalBoardRuntime().getInstalledPart(
                pad.getComponentId());
            PhysicalBoardInstallationProvider.Scoped scoped = instance.getPhysicalBoardRuntime()
                .getScopedMutationCapability(pad.getComponentId());
            if (installed != null && scoped != null) {
                CircuitMeasurementEndpoint expected = scoped.getMutationSlot().getExpectedEndpoint(installed, pad);
                require(expected != null && GeneratedComponentConnectionBindings.sameEndpoint(
                        expected, binding.getComponentEndpoint()),
                    "Physical connection disagrees with installed terminal: " + padId);
            }
            require(containsIdentity(canonical, binding.getConnectionElement()),
                "Connection element is outside generated canonical elements: " + padId);
            // Endpoints may deliberately reference a shared board node. A
            // detachable element itself has one owner and cannot double as
            // any component, power input, or another detachable connection.
            require(!containsIdentity(claimed, binding.getConnectionElement()),
                "Mutable generated element has duplicate ownership: connection " + padId);
            claimed.add(binding.getConnectionElement());
            require(!instance.getExternalPowerBindings().isBackingElement(
                    binding.getConnectionElement()),
                "Connection element is external power infrastructure: " + padId);
            int count = countIdentity(activeElements, binding.getConnectionElement());
            // A connection is structurally checked against its logical state
            // below.  The element may be absent while its lead is lifted.
            require(count <= 1, "Connection element occurs more than once: " + padId);
        }
        // Reuse the established endpoint/geometry checks after the complete
        // one-binding-per-pad and immutable-oracle checks above.
        bindings.validateAgainst(board, canonical, instance.getComponentBindings(),
            instance.getExternalPowerBindings(), instance.getFaultBinding());
    }

    private static void verifyFoundationPadEndpoint(GeneratedBoardInstance instance,
            BoardPad pad, CircuitMeasurementEndpoint boardEndpoint) {
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(
            pad.getComponentId());
        if (slot == null)
            return;
        PhysicalPart<?> part = slot.getInstalledPart();
        // Foundation wrappers retain the board-facing endpoints. Other
        // fixed native parts can expose component-side posts through their
        // own backing; the retained board oracle remains the board authority.
        if (!(part instanceof FixedPhysicalPart))
            return;
        PhysicalPartTerminal terminal = null;
        for (PhysicalPartTerminal candidate : part.getTerminals()) {
            if (pad.getTerminalId().equals(candidate.getTerminalName())) {
                terminal = candidate;
                break;
            }
        }
        require(terminal != null,
            "Installed foundation part has no terminal for pad: " + pad.getId());
        require(GeneratedComponentConnectionBindings.sameEndpoint(
                boardEndpoint, terminal.getEndpoint()),
            "Board endpoint disagrees with installed foundation terminal: " + pad.getId());
    }

    private static void verifyModificationState(GeneratedBoardInstance instance,
            BoardModificationController modifications, Vector<CircuitElm> activeElements) {
        HashMap<String, Boolean> states = modifications.getConnectionStatesForValidation();
        Vector<String> padIds = instance.getBoard().getPadIds();
        require(states.size() == instance.getConnectionBindings().getAll().size(),
            "Modification state does not cover every detachable connection");
        for (String padId : padIds) {
            GeneratedComponentConnectionBinding binding = instance.getConnectionBindings()
                .getOrNull(padId);
            Boolean connected = states.get(padId);
            if (binding == null) {
                require(connected == null,
                    "Modification state has a foundation-pad entry: " + padId);
                continue;
            }
            require(connected != null,
                "Modification state has a dangling connection entry: " + padId);
            int occurrences = countIdentity(activeElements, binding.getConnectionElement());
            require(occurrences == (connected.booleanValue() ? 1 : 0),
                "Connection state disagrees with active graph: " + padId);
        }
        // A removed physical part cannot retain an electrically attached
        // detachable lead.  This catches a slot-only mutation that leaves the
        // graph and the physical mount in different states.
        for (PhysicalBoardSlot slot : instance.getPhysicalBoardRuntime().getSlots()) {
            if (slot == null)
                continue;
            Vector<GeneratedComponentConnectionBinding> componentConnections =
                instance.getConnectionBindings().getForComponentOrEmpty(slot.getComponentId());
            if (componentConnections.isEmpty())
                continue;
            if (!slot.isOccupied()) {
                for (GeneratedComponentConnectionBinding binding : componentConnections) {
                    Boolean connected = states.get(binding.getPadId());
                    require(connected != null && !connected.booleanValue(),
                        "Empty physical slot retains a connected lead: " +
                        slot.getComponentId());
                }
            }
        }
        modifications.verifyStructuralState();
    }

    private static void verifyPhysicalRuntime(GeneratedBoardInstance instance,
            Vector<CircuitElm> canonical, Vector<CircuitElm> activeElements) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        TroubleshootBoard board = instance.getBoard();
        Vector<String> slotOrder = runtime.getSlotOrder();
        require(slotOrder.size() == board.getComponentIds().size(),
            "Physical runtime slot order has an unexpected size");
        Vector<PhysicalBoardSlot> slots = runtime.getSlots();
        require(slots.size() == slotOrder.size(), "Physical runtime slot view is incomplete");
        for (int index = 0; index < slotOrder.size(); index++) {
            String componentId = slotOrder.get(index);
            require(componentId != null && count(slotOrder, componentId) == 1,
                "Physical runtime slot order contains a duplicate");
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            require(slot != null && slots.get(index) == slot && slot.getRuntime() == runtime,
                "Physical runtime slot identity is not stable: " + componentId);
            BoardComponent component = board.getComponent(componentId);
            require(component != null && component.getPhysicalPackage() != null &&
                    component.getPhysicalPackage().isEquivalentTo(slot.getPhysicalPackage()),
                "Physical runtime slot package changed: " + componentId);
            require(component.getPadIds().equals(slot.getPadIds()),
                "Physical runtime slot pads changed: " + componentId);
            for (String padId : slot.getPadIds()) {
                BoardPad pad = board.getPad(padId);
                require(pad != null && slot.getTerminalIds().contains(pad.getTerminalId()) &&
                        slot.getNetIds().contains(pad.getNetId()),
                    "Physical runtime slot pad metadata changed: " + componentId);
            }
            if (slot.isOccupied())
                verifyInstalledPart(runtime, slot, canonical, activeElements);
        }

        Vector<String> partOrder = runtime.getPartOrder();
        Vector<PhysicalPart> parts = runtime.getPhysicalParts();
        require(partOrder.size() == parts.size(), "Physical runtime part order is incomplete");
        Vector<CircuitElm> claimedPartBacking = new Vector<CircuitElm>();
        for (int index = 0; index < partOrder.size(); index++) {
            String partId = partOrder.get(index);
            require(partId != null && count(partOrder, partId) == 1,
                "Physical runtime part order contains a duplicate");
            PhysicalPart<?> part = runtime.getPart(partId);
            require(part != null && parts.get(index) == part,
                "Physical runtime part identity is not stable: " + partId);
            verifyPart(runtime, part, canonical, activeElements, claimedPartBacking);
        }
        Vector<String> partIds = runtime.getPartIds();
        require(partIds.size() == partOrder.size(), "Physical runtime has a dangling part map entry");
        for (String partId : partIds)
            require(count(partOrder, partId) == 1, "Physical runtime part map is not ordered: " + partId);

        Vector<String> inventoryIds = runtime.getInventoryIds();
        Vector<String> inventoryOwnedParts = new Vector<String>();
        for (String inventoryId : inventoryIds) {
            require(inventoryId != null && inventoryId.length() > 0,
                "Physical runtime has an invalid inventory identity");
            Vector<String> ids = runtime.getInventoryPartIds(inventoryId);
            for (String partId : ids) {
                require(partId != null && count(ids, partId) == 1,
                    "Physical inventory contains a duplicate part: " + partId);
                require(count(inventoryOwnedParts, partId) == 0,
                    "Physical part belongs to more than one inventory: " + partId);
                inventoryOwnedParts.add(partId);
                PhysicalPart<?> part = runtime.getPart(partId);
                require(part != null && runtime.getInventoryIdForPart(partId) != null &&
                        inventoryId.equals(runtime.getInventoryIdForPart(partId)),
                    "Physical inventory reverse map disagrees: " + partId);
                require(runtime.getInventoryParts(inventoryId).contains(part),
                    "Physical inventory forward map disagrees: " + partId);
            }
        }
        for (String partId : partOrder) {
            PhysicalPart<?> part = runtime.getPart(partId);
            String inventoryId = runtime.getInventoryIdForPart(partId);
            if (part.isInstalled()) {
                require(part.getBoardSlot() != null &&
                        runtime.getSlot(part.getBoardSlot().getComponentId()) == part.getBoardSlot(),
                    "Installed physical part has a dangling slot: " + partId);
            } else {
                require(inventoryId != null && count(inventoryOwnedParts, partId) == 1,
                    "Loose physical part has no unique inventory owner: " + partId);
            }
            if (inventoryId != null)
                require(count(inventoryOwnedParts, partId) == 1,
                    "Physical inventory reverse map has no unique forward entry: " + partId);
        }

        verifyProviders(instance);
        verifyResistorStress(instance);
    }

    private static void verifyInstalledPart(PhysicalBoardRuntime runtime,
            PhysicalBoardSlot slot, Vector<CircuitElm> canonical,
            Vector<CircuitElm> activeElements) {
        PhysicalPart<?> part = slot.getInstalledPart();
        require(part != null && part.isInstalled() && part.getBoardSlot() == slot,
            "Slot and physical mount disagree: " + slot.getComponentId());
        require(runtime.getPart(part.getId()) == part,
            "Installed physical part is outside runtime registry: " + part.getId());
        require(part.getPackage() != null &&
                slot.getPhysicalPackage().isEquivalentTo(part.getPackage()),
            "Installed physical part package does not fit slot: " + part.getId());
        for (CircuitElm element : part.getElectricalBacking().getCircuitElements())
            require(countIdentity(activeElements, element) == 1,
                "Installed physical part backing is missing or duplicated in active graph: " +
                part.getId());
    }

    private static void verifyPart(PhysicalBoardRuntime runtime, PhysicalPart<?> part,
            Vector<CircuitElm> canonical, Vector<CircuitElm> activeElements,
            Vector<CircuitElm> claimedPartBacking) {
        require(part.getId() != null && part.getId().length() > 0 &&
                part.getMountState() != null && part.getProvenance() != null,
            "Physical part has incomplete identity: " + part.getId());
        if (part.isInstalled())
            require(part.getBoardSlot() != null && part.getBoardSlot().getRuntime() == runtime,
                "Physical part is mounted outside its runtime: " + part.getId());
        if (part.isInstalled()) {
            PhysicalBoardSlot slot = part.getBoardSlot();
            require(runtime.getSlot(slot.getComponentId()) == slot &&
                    slot.getInstalledPart() == part,
                "Physical part mount has no exact slot back-reference: " + part.getId());
        }
        else
            require(part.getBoardSlot() == null,
                "Loose physical part retains a mount: " + part.getId());
        PhysicalPartElectricalBacking backing = part.getElectricalBacking();
        require(backing != null && backing.getTerminalCount() == part.getTerminalCount(),
            "Physical part electrical backing has the wrong terminal count: " + part.getId());
        Vector<CircuitElm> backingElements = backing.getCircuitElements();
        require(!backingElements.isEmpty(), "Physical part has no electrical backing: " + part.getId());
        requireUniqueElements(backingElements, "physical part backing " + part.getId());
        for (CircuitElm element : backingElements) {
            require(containsIdentity(canonical, element),
                "Physical part backing is outside canonical elements: " + part.getId());
            require(countIdentity(activeElements, element) == 1,
                "Physical part backing is missing or duplicated in active graph: " + part.getId());
            require(!containsIdentity(claimedPartBacking, element),
                "Physical part backing has duplicate mutable ownership: " + part.getId());
            claimedPartBacking.add(element);
        }
        Vector<PhysicalPartTerminal> terminals = part.getTerminals();
        require(terminals.size() == part.getTerminalCount(),
            "Physical part terminal list has the wrong size: " + part.getId());
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPartTerminal terminal = terminals.get(index);
            require(terminal != null && terminal.getEndpoint() != null,
                "Physical part has a missing terminal: " + part.getId());
            verifyEndpoint(terminal.getEndpoint(), canonical, part.getId());
            require(containsEndpoint(backing, terminal.getEndpoint()),
                "Physical part terminal is outside its backing: " + part.getId());
            for (int previous = 0; previous < index; previous++)
                require(!terminal.getTerminalName().equals(terminals.get(previous).getTerminalName()),
                    "Physical part has duplicate terminal names: " + part.getId());
        }
    }

    private static void verifyProviders(GeneratedBoardInstance instance) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<PhysicalBoardRuntimeCapability> capabilities = runtime.getCapabilities();
        Vector<String> capabilityOrder = runtime.getCapabilityOrder();
        require(capabilities.size() == capabilityOrder.size(),
            "Physical runtime capability order is incomplete");
        for (int index = 0; index < capabilityOrder.size(); index++) {
            String id = capabilityOrder.get(index);
            require(id != null && count(capabilityOrder, id) == 1 &&
                    capabilities.get(index) == runtime.getCapability(id),
                "Physical runtime capability identity is not stable: " + id);
        }

        Vector<WorkbenchPartsProvider> partsProviders = runtime.getWorkbenchPartsProviders();
        Vector<String> providerComponents = new Vector<String>();
        for (WorkbenchPartsProvider provider : partsProviders) {
            require(provider != null && provider.getComponentId() != null,
                "Physical runtime has an invalid workbench provider");
            String componentId = provider.getComponentId();
            require(count(providerComponents, componentId) == 0,
                "More than one workbench provider owns a component: " + componentId);
            providerComponents.add(componentId);
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            PhysicalSlotMutationProvider mutation = runtime.getMutationProvider(componentId);
            require(slot != null && mutation != null && mutation.getComponentId().equals(componentId),
                "Workbench provider has no matching runtime slot/provider: " + componentId);
            PhysicalPart<?> installed = slot.getInstalledPart();
            if (installed != null)
                require(provider.ownsPart(installed.getId()) &&
                        provider.getPart(installed.getId()) == installed &&
                        mutation.ownsPart(installed.getId()),
                    "Workbench provider lost installed part identity: " + componentId);
            for (PhysicalPart<?> loose : provider.getLooseParts()) {
                require(loose != null && !loose.isInstalled() &&
                        runtime.getPart(loose.getId()) == loose && provider.ownsPart(loose.getId()) &&
                        provider.getPart(loose.getId()) == loose && mutation.ownsPart(loose.getId()),
                    "Workbench provider returned a foreign loose part: " + componentId);
            }
        }
        for (PhysicalSlotMutationProvider provider : runtime.getMutationProviders()) {
            require(provider != null && provider.getComponentId() != null &&
                    runtime.getMutationProvider(provider.getComponentId()) == provider,
                "Physical runtime mutation provider is not registry-owned");
            require(containsProviderComponent(partsProviders, provider.getComponentId()),
                "Physical runtime mutation provider has no workbench owner: " +
                    provider.getComponentId());
        }

        // Every inventory-owned physical identity must have exactly one typed
        // provider.  Fixed generated parts are intentionally provider-free.
        for (String partId : runtime.getPartOrder()) {
            PhysicalPart<?> part = runtime.getPart(partId);
            if (runtime.getInventoryIdForPart(partId) == null)
                continue;
            int ownerCount = 0;
            for (WorkbenchPartsProvider provider : partsProviders)
                if (provider.ownsPart(partId)) {
                    ownerCount++;
                    require(provider.getPart(partId) == part,
                        "Workbench provider returned a foreign physical identity: " + partId);
                }
            require(ownerCount == 1, "Physical part has ambiguous provider ownership: " + partId);
        }
    }

    private static void verifyResistorStress(GeneratedBoardInstance instance) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        for (PhysicalBoardRuntimeCapability capability : runtime.getCapabilities()) {
            if (!(capability instanceof ReplaceableResistorBoardCapability))
                continue;
            ReplaceableResistorBoardCapability resistor =
                (ReplaceableResistorBoardCapability) capability;
            ResistorStressDamageSystem stress = resistor.getStressDamageSystem();
            Vector<ResistorStressState> states = stress.getStates();
            Vector<String> stateIds = new Vector<String>();
            for (ResistorStressState state : states) {
                require(state != null && state.getPart() != null,
                    "Resistor stress registry contains a null state");
                PhysicalResistorPart part = state.getPart();
                require(count(stateIds, part.getId()) == 0 &&
                        runtime.getPart(part.getId()) == part &&
                        resistor.getInventory().contains(part.getId()) &&
                        stress.ownsState(part),
                    "Resistor stress state has foreign inventory ownership: " + part.getId());
                stateIds.add(part.getId());
            }
            require(states.size() == resistor.getInventory().size(),
                "Resistor stress registry does not cover inventory");
            for (PhysicalResistorPart part : resistor.getInventory().getAll())
                require(count(stateIds, part.getId()) == 1 && stress.ownsState(part),
                    "Resistor inventory has no stress state: " + part.getId());
        }
    }

    private static void verifyFaultOwnership(GeneratedBoardInstance instance,
            Vector<CircuitElm> canonical) {
        GeneratedFaultBinding binding = instance.getFaultBinding();
        if (binding == null)
            return;
        for (CircuitElm element : binding.getPrivateSimulationElements())
            require(containsIdentity(canonical, element), "Fault private element is outside canonical graph");
        String targetComponentId = binding.getFault().getTargetComponentId();
        require(instance.getBoard().getComponent(targetComponentId) != null,
            "Selected generated fault targets an unknown component");
        Vector<PhysicalPart<?>> owners = new Vector<PhysicalPart<?>>();
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts())
            if (part instanceof GeneratedFaultOwningPart &&
                    ((GeneratedFaultOwningPart) part).ownsGeneratedFault(binding))
                owners.add(part);
        if (!owners.isEmpty()) {
            require(owners.size() == 1 && owners.firstElement().isOriginal(),
                "Selected generated fault escaped its original physical owner");
            PhysicalPart<?> owner = owners.firstElement();
            require(owner.getProvenance() != null && owner.getProvenance().isOriginal(),
                "Selected generated fault owner lost original provenance");
            String inventoryId = instance.getPhysicalBoardRuntime().getInventoryIdForPart(owner.getId());
            require(inventoryId != null,
                "Selected generated fault owner is not retained in inventory");
        }
    }

    private static void verifyEndpoint(CircuitMeasurementEndpoint endpoint,
            Vector<CircuitElm> canonical, String owner) {
        require(endpoint instanceof CircuitPostMeasurementEndpoint,
            "Unsupported generated endpoint: " + owner);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        require(post.getElement() != null && containsIdentity(canonical, post.getElement()) &&
                post.getPostIndex() >= 0 && post.getPostIndex() < post.getElement().getPostCount(),
            "Generated endpoint is outside canonical graph: " + owner);
    }

    private static boolean containsEndpoint(PhysicalPartElectricalBacking backing,
            CircuitMeasurementEndpoint expected) {
        for (int index = 0; index < backing.getTerminalCount(); index++)
            if (GeneratedComponentConnectionBindings.sameEndpoint(
                    backing.getTerminalEndpoint(index), expected))
                return true;
        return false;
    }

    private static void verifyOwnedUniqueElements(Vector<CircuitElm> elements,
            Vector<CircuitElm> canonical, Vector<CircuitElm> claimed, String owner) {
        for (CircuitElm element : elements) {
            require(element != null && containsIdentity(canonical, element),
                owner + " references a foreign element");
            require(!containsIdentity(elementsBefore(elements, element), element),
                owner + " contains a duplicate element");
            require(!containsIdentity(claimed, element),
                "Mutable generated element has duplicate ownership: " + owner);
            claimed.add(element);
        }
    }

    private static Vector<CircuitElm> elementsBefore(Vector<CircuitElm> values,
            CircuitElm target) {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        for (CircuitElm value : values) {
            if (value == target)
                break;
            result.add(value);
        }
        return result;
    }

    private static void requireUniqueElements(Vector<CircuitElm> values, String owner) {
        for (int index = 0; index < values.size(); index++)
            for (int previous = 0; previous < index; previous++)
                require(values.get(index) != values.get(previous),
                    owner + " contains a duplicate element");
    }

    private static int count(Vector<String> values, String expected) {
        int result = 0;
        for (String value : values)
            if (expected == null ? value == null : expected.equals(value)) result++;
        return result;
    }

    private static int countIdentity(Vector<CircuitElm> values, CircuitElm expected) {
        int result = 0;
        for (CircuitElm value : values)
            if (value == expected) result++;
        return result;
    }

    private static boolean containsIdentity(Vector<?> values, Object expected) {
        for (Object value : values)
            if (value == expected) return true;
        return false;
    }

    private static boolean containsProviderComponent(Vector<WorkbenchPartsProvider> providers,
            String componentId) {
        for (WorkbenchPartsProvider provider : providers)
            if (provider != null && componentId.equals(provider.getComponentId())) return true;
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
