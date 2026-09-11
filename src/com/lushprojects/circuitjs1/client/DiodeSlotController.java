package com.lushprojects.circuitjs1.client;

class DiodeSlotController implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableDiodeBoardCapability capability;

    DiodeSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications,
            ReplaceableDiodeBoardCapability capability) {
        if (capability == null)
            throw new IllegalArgumentException("Missing replaceable diode capability");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("REPLACEABLE_DIODE_" + getComponentId(),
            "Diode workbench", "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify diode";
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install as " + getComponentId();
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
        if (!supports(operation) || !isSafeMutationAvailable()) return false;
        DiodeComponentSlot slot = capability.getSlot();
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
        if (!isAvailable(operation, context)) return false;
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return installNewFromCatalog(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id)) return install(operation.getPart().getId());
        if (WorkbenchOperation.REMOVE.equals(id)) return removeInstalledPart();
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
        DiodeComponentSlot slot = capability.getSlot();
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
        DiodeComponentSlot slot = capability.getSlot();
        if (!slot.isEmpty())
            return false;
        PhysicalDiodePart part = capability.getInventory().get(partId);
        if (part.isInstalled())
            return false;
        PhysicalMutationScope scope = newScope("install", part);
        try {
            scope.replacePrimaryBinding(part.getElement());
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
        final DiodeComponentSlot slot = capability.getSlot();
        if (!slot.isEmpty())
            return false;
        final DiodeCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final DiodeNameplate specification = entry.getSpecification();
        final PhysicalNameplate playerNameplate = entry.getPlayerVisibleNameplate();
        final DiodeElm element = DynamicDiodeBackingAllocator.create(instance.getSimulationElements());
        final String componentId = slot.getComponentId();
        PhysicalMutationScope scope = newScope("catalog", null, null, catalogEntryId);
        try {
        PhysicalDiodePart part = scope.acquire(capability.getInventory(),
            componentId + "_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalDiodePart>() {
                public PhysicalDiodePart create(String partId) {
                    PhysicalDiodePart part = new PhysicalDiodePart(partId, specification, specification,
                        playerNameplate.forPhysicalPartId(partId), element, null,
                        entry.isReversedInstallation(), DiodePartLocation.LOOSE,
                        new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED,
                            partId));
                    slot.getPhysicalSlot().bindGeometryForAcquisition(part);
                    return part;
                }
            });
        scope.registerCanonicalElement(element);
        scope.appendActiveElement(element);
        scope.replacePrimaryBinding(element);
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
                "Diode replacement requires electrically unpowered generated board");
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

    private void retargetComponentLeadBindings(PhysicalDiodePart part,
            PhysicalMutationScope scope) {
        String componentId = capability.getSlot().getComponentId();
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(componentId))
            scope.retargetEndpoint(binding, part.getTerminalForBoardPad(binding.getPadId()));
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
        for (DiodeCatalogEntry entry : capability.getCatalog().getEntries())
            if (catalogEntryId.equals(entry.getId())) return true;
        return false;
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
}
