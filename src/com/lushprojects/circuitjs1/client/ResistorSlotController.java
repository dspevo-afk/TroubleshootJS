package com.lushprojects.circuitjs1.client;

class ResistorSlotController implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableResistorBoardCapability capability;

    ResistorSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications,
            ReplaceableResistorBoardCapability capability) {
        if (capability == null)
            throw new IllegalArgumentException("Missing replaceable resistor capability");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("REPLACEABLE_RESISTOR_" + getComponentId(),
            "Resistor workbench", "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify resistor";
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install as " + capability.getPlayerComponentLabel();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return capability.getInstallNewLabel();
        if (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ||
                WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId())) {
            BoardPad pad = instance.getBoard().getPad(operation.getPadId());
            String action = WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ?
                "Lift lead " : "Reconnect lead ";
            return action + (pad == null ? operation.getPadId() : pad.getTerminalId());
        }
        if (WorkbenchOperation.RESTORE.equals(operation.getId())) return "Restore component";
        return "Remove component";
    }

    public boolean supports(WorkbenchOperation operation) {
        if (operation == null || !getComponentId().equals(operation.getComponentId()))
            return false;
        String id = operation.getId();
        return WorkbenchOperation.INSTALL.equals(id) || WorkbenchOperation.REMOVE.equals(id) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(id) ||
            WorkbenchOperation.LIFT_LEAD.equals(id) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(id) ||
            WorkbenchOperation.RESTORE.equals(id);
    }

    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        if (!supports(operation) || !isSafeMutationAvailable())
            return false;
        final ReplaceableComponentSlot slot = capability.getSlot();
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && hasCatalogEntry(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return operation.getPart() != null && ownsPartIdentity(operation.getPart()) &&
                !operation.getPart().isInstalled() && slot.isEmpty();
        if (WorkbenchOperation.REMOVE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation);
        if (WorkbenchOperation.LIFT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) == ComponentPhysicalState.INSTALLED &&
                hasConnectedPad(operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) == ComponentPhysicalState.LEAD_LIFTED &&
                hasDisconnectedPad(operation.getPadId());
        if (WorkbenchOperation.RESTORE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState(getComponentId()) != ComponentPhysicalState.INSTALLED;
        return false;
    }

    public boolean invoke(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        if (!isAvailable(operation, context))
            return false;
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return installNewFromCatalog(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return install(operation.getPart().getId());
        if (WorkbenchOperation.REMOVE.equals(id))
            return removeInstalledPart();
        if (WorkbenchOperation.LIFT_LEAD.equals(id))
            return modifications.liftLead(getComponentId(), operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return modifications.reconnectLead(getComponentId(), operation.getPadId());
        return modifications.restoreComponent(getComponentId());
    }

    public String getComponentId() { return capability.getSlot().getComponentId(); }
    public boolean ownsPart(String partId) { return capability.getInventory().contains(partId); }
    public PhysicalMutationSlot getMutationSlot() { return capability.getSlot(); }

    public boolean removeInstalledPart() {
        requireSafeMutation();
        ReplaceableComponentSlot slot = capability.getSlot();
        if (slot.isEmpty())
            return false;
        PhysicalMutationScope scope = newScope("remove", null);
        try {
            modifications.disconnectComponentForMutation(scope, slot.getComponentId());
            scope.clearPart();
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutation();
        return true;
    }

    public boolean install(String partId) {
        requireSafeMutation();
        ReplaceableComponentSlot slot = capability.getSlot();
        if (!slot.isEmpty())
            return false;
        PhysicalResistorPart part = capability.getInventory().get(partId);
        if (part.isInstalled())
            return false;
        PhysicalMutationScope scope = newScope("install", part);
        try {
            scope.replacePrimaryBinding(part.getElement());
	    scope.replaceAuxiliaryBinding(part.getSecondaryOpenPath().getSimulationElement());
	    retargetComponentLeadBindings(part, scope);
            scope.installPart(part);
            scope.restoreComponentGraph();
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutation();
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        final ReplaceableComponentSlot slot = capability.getSlot();
        if (!slot.isEmpty())
            return false;
        final ResistorCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final ResistorNameplate specification = entry.getSpecification();
        final PhysicalNameplate playerNameplate = entry.getPlayerVisibleNameplate();
        final ResistorElm element = DynamicResistorBackingAllocator.create(instance.getSimulationElements(),
            specification.getNominalResistanceOhms());
        final ResistorSecondaryOpenPath openPath = ResistorSecondaryOpenPath.create(
            new CircuitPostMeasurementEndpoint(element, 1));
        final String componentId = slot.getComponentId();
        PhysicalMutationScope scope = newScope("catalog", null, null, catalogEntryId);
        try {
        PhysicalResistorPart part = scope.acquire(capability.getInventory(),
            componentId + "_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalResistorPart>() {
                public PhysicalResistorPart create(String partId) {
                    PhysicalResistorPart part = new PhysicalResistorPart(partId, specification, specification,
                        playerNameplate.forPhysicalPartId(partId), element, null, openPath,
                        ResistorPartLocation.INSTALLED, new PhysicalPartProvenance(
                        PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                    slot.getPhysicalSlot().bindGeometryForAcquisition(part);
                    return part;
                }
            });
        scope.registerCanonicalElement(element);
        scope.registerCanonicalElement(openPath.getSimulationElement());
        registerStress(scope, part);
        scope.appendActiveElement(element);
        scope.appendActiveElement(openPath.getSimulationElement());
        scope.replacePrimaryBinding(element);
        scope.replaceAuxiliaryBinding(openPath.getSimulationElement());
        retargetComponentLeadBindings(part, scope);
        scope.installPart(part);
        scope.restoreComponentGraph();
        scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return false;
        }
        scope.closeAfterCommit();
        finishMutation();
        return true;
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered() ||
                instance.getPhysicalBoardRuntime().isMutationInProgress() ||
                instance.getPhysicalBoardRuntime().isMutationQuarantined())
            throw new BoardModificationRejectedException(
                "Resistor replacement requires electrically unpowered generated board");
    }

    private void finishMutation() {
        try {
            if (sim.getGeneratedChallengeController() != null)
                sim.getGeneratedChallengeController().invalidateCustomerRetest();
            sim.needAnalyze();
            sim.requestGeneratedBoardVerification();
            sim.refreshBoardModificationControls();
        } catch (Throwable failure) {
            sim.markGeneratedRuntimeFailure(instance, failure);
            PhysicalMutationScope.rethrow(failure);
        }
    }

    private void retargetComponentLeadBindings(PhysicalResistorPart part,
            PhysicalMutationScope scope) {
        String componentId = capability.getSlot().getComponentId();
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId)) {
            BoardPad pad = instance.getBoard().getPad(binding.getPadId());
            int terminal;
            if ("1".equals(pad.getTerminalId()))
                terminal = 0;
            else if ("2".equals(pad.getTerminalId()))
                terminal = 1;
            else
                throw new IllegalStateException("Replaceable resistor pad is not terminal 1 or 2: " +
                    binding.getPadId());
            scope.retargetEndpoint(binding, part.getPublicTerminal(terminal));
        }
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance && !sim.activeMeasurementOverlay &&
            sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            !instance.getPhysicalBoardRuntime().isMutationInProgress() &&
            !instance.getPhysicalBoardRuntime().isMutationOwnerQuarantined(getComponentId());
    }

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() == capability.getSlot().getInstalledPart();
    }

    private boolean ownsPartIdentity(PhysicalPart<?> part) {
        return part != null && part.getId() != null &&
            capability.getInventory().contains(part.getId()) &&
            capability.getInventory().get(part.getId()) == part;
    }

    private void registerStress(final PhysicalMutationScope scope,
            final PhysicalResistorPart part) {
        final ResistorStressDamageSystem stress = capability.getStressDamageSystem();
        stress.register(part);
        scope.setProviderCompensation(new PhysicalMutationScope.ProviderCompensation() {
            public void compensate() {
                stress.unregister(part);
            }
        });
        scope.afterStressRegister();
    }

    private PhysicalMutationScope newScope(String operation, PhysicalPart<?> requestedPart) {
        return newScope(operation, requestedPart, null, null);
    }

    private PhysicalMutationScope newScope(String operation, PhysicalPart<?> requestedPart,
            String padId, String catalogEntryId) {
        PhysicalMutationIntent intent = PhysicalMutationIntent.prepare(
            instance.getPhysicalBoardRuntime(), instance, modifications,
            capability.getSlot(), operation, padId, catalogEntryId, requestedPart);
        return new PhysicalMutationScope(sim, instance, modifications, intent);
    }

    private boolean hasConnectedPad(String padId) {
        return hasPad(padId) && modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasDisconnectedPad(String padId) {
        return hasPad(padId) && !modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasPad(String padId) {
        return padId != null && instance.getBoard().getPad(padId) != null &&
            getComponentId().equals(instance.getBoard().getPad(padId).getComponentId());
    }

    private boolean hasCatalogEntry(String catalogEntryId) {
        if (catalogEntryId == null) return false;
        for (ResistorCatalogEntry entry : capability.getCatalog().getEntries())
            if (catalogEntryId.equals(entry.getId())) return true;
        return false;
    }

}
