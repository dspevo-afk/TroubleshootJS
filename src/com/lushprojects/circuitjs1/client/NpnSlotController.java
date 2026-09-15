package com.lushprojects.circuitjs1.client;

/** Family-owned graph and identity mutation controller for Q1. */
final class NpnSlotController implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableNpnBoardCapability capability;

    NpnSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, ReplaceableNpnBoardCapability capability) {
        if (sim == null || instance == null || modifications == null || capability == null)
            throw new IllegalArgumentException("Missing NPN slot controller context");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("REPLACEABLE_NPN_" + getComponentId(), "NPN workbench",
            "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify transistor";
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install as " + getComponentId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return capability.getInstallNewLabel();
        if (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ||
                WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId())) {
            BoardPad pad = instance.getBoard().getPad(operation.getPadId());
            return (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()) ?
                "Lift lead " : "Reconnect lead ") + (pad == null ? operation.getPadId() :
                pad.getTerminalId());
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
            WorkbenchOperation.RECONNECT_LEAD.equals(id) || WorkbenchOperation.RESTORE.equals(id);
    }

    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        if (!supports(operation) || !isSafeMutationAvailable()) return false;
        NpnComponentSlot slot = capability.getSlot();
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && hasCatalogEntry(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return operation.getPart() instanceof PhysicalNpnPart &&
                instance.getPhysicalBoardRuntime().isPartInstallableAt(operation.getPart(),
                    getComponentId());
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
        if (WorkbenchOperation.CATALOG_INSTALL.equals(operation.getId()))
            return installNewFromCatalog(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return install(operation.getPart().getId());
        if (WorkbenchOperation.REMOVE.equals(operation.getId())) return removeInstalledPart();
        if (WorkbenchOperation.LIFT_LEAD.equals(operation.getId()))
            return modifications.liftLead(getComponentId(), operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId()))
            return modifications.reconnectLead(getComponentId(), operation.getPadId());
        return modifications.restoreComponent(getComponentId());
    }

    public String getComponentId() { return capability.getSlot().getComponentId(); }
    public boolean ownsPart(String partId) { return capability.ownsPart(partId); }
    public PhysicalMutationSlot getMutationSlot() { return capability.getSlot(); }

    public boolean removeInstalledPart() {
        requireSafeMutation();
        if (capability.getSlot().isEmpty()) return false;
        PhysicalMutationScope scope = newScope("remove", null);
        try {
            modifications.disconnectComponentForMutation(scope, getComponentId());
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
        if (!capability.getSlot().isEmpty()) return false;
        PhysicalPart<?> candidate = instance.getPhysicalBoardRuntime().getPart(partId);
        if (!(candidate instanceof PhysicalNpnPart) ||
                !instance.getPhysicalBoardRuntime().isPartInstallableAt(candidate,
                    getComponentId()))
            return false;
        PhysicalNpnPart part = (PhysicalNpnPart) candidate;
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
        return catalogPart(catalogEntryId, true) != null;
    }

    public PhysicalPart<?> acquireFromCatalog(String catalogEntryId) {
        return catalogPart(catalogEntryId, false);
    }

    private PhysicalNpnPart catalogPart(String catalogEntryId, boolean install) {
        requireSafeMutation();
        final NpnComponentSlot slot = capability.getSlot();
        if (install && !slot.isEmpty()) return null;
        final NpnCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final NpnSpecification specification = entry.getSpecification();
        final NTransistorElm element = DynamicNpnBackingAllocator.create(
            instance.getSimulationElements(), specification);
        final String componentId = slot.getComponentId();
        PhysicalMutationScope scope = newScope(install ? "catalog" : "acquire", null,
            null, catalogEntryId);
        PhysicalNpnPart part;
        try {
            part = scope.acquire(capability.getInventory(), componentId + "_CATALOG_PART",
                new PhysicalPartIdentityFactory<PhysicalNpnPart>() {
                    public PhysicalNpnPart create(String partId) {
                        PhysicalNpnPart created = new PhysicalNpnPart(partId, specification,
                            entry.getPlayerVisibleNameplate().forPhysicalPartId(partId), element,
                            null, NpnPartLocation.LOOSE, new PhysicalPartProvenance(
                                PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                        slot.getPhysicalSlot().bindGeometryForAcquisition(created);
                        return created;
                    }
                });
            scope.registerCanonicalElement(element);
            scope.appendActiveElement(element);
            if (install) {
                scope.replacePrimaryBinding(element);
                retargetComponentLeadBindings(part, scope);
                scope.installPart(part);
                scope.restoreComponentGraph();
            }
            scope.commit();
        } catch (Throwable failure) {
            scope.abort(failure);
            PhysicalMutationScope.rethrow(failure);
            return null;
        }
        scope.closeAfterCommit();
        finishMutation();
        return part;
    }

    private void requireSafeMutation() {
        if (!isSafeMutationAvailable())
            throw new BoardModificationRejectedException(
                "NPN replacement requires electrically unpowered generated board");
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance &&
            sim.getBoardModificationController() == modifications &&
            !sim.activeMeasurementOverlay && sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            !instance.getPhysicalBoardRuntime().isMutationInProgress() &&
            !instance.getPhysicalBoardRuntime().isMutationOwnerQuarantined(getComponentId());
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

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() == capability.getSlot()
            .getInstalledPart();
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

    private void retargetComponentLeadBindings(PhysicalNpnPart part,
            PhysicalMutationScope scope) {
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent(getComponentId()))
            scope.retargetEndpoint(binding, part.getTerminalForBoardPad(binding.getPadId()));
    }

    private boolean hasConnectedPad(String padId) {
        return hasPad(padId) && modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasDisconnectedPad(String padId) {
        return hasPad(padId) && !modifications.isLeadConnected(getComponentId(), padId);
    }

    private boolean hasPad(String padId) {
        BoardPad pad = padId == null ? null : instance.getBoard().getPad(padId);
        return pad != null && getComponentId().equals(pad.getComponentId());
    }

    private boolean hasCatalogEntry(String id) {
        if (id == null) return false;
        for (NpnCatalogEntry entry : capability.getCatalog().getEntries())
            if (id.equals(entry.getId())) return true;
        return false;
    }
}
