package com.lushprojects.circuitjs1.client;

/** Family-owned physical/graph mutation controller for C1. */
final class CapacitorSlotController implements PhysicalSlotMutationProvider,
        PhysicalSlotMutationProvider.Scoped, CatalogAcquisitionProvider {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableCapacitorBoardCapability capability;

    CapacitorSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications,
            ReplaceableCapacitorBoardCapability capability) {
        if (sim == null || instance == null || modifications == null || capability == null)
            throw new IllegalArgumentException("Missing capacitor slot controller context");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("REPLACEABLE_CAPACITOR_" + getComponentId(),
            "Capacitor workbench", "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify capacitor";
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
        if (!supports(operation) || !isSafeMutationAvailable())
            return false;
        CapacitorComponentSlot slot = capability.getSlot();
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && hasCatalogEntry(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return operation.getPart() instanceof PhysicalCapacitorPart &&
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
        CapacitorComponentSlot slot = capability.getSlot();
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
        CapacitorComponentSlot slot = capability.getSlot();
        if (!slot.isEmpty())
            return false;
        PhysicalPart<?> candidate = instance.getPhysicalBoardRuntime().getPart(partId);
        if (!(candidate instanceof PhysicalCapacitorPart) ||
                !instance.getPhysicalBoardRuntime().isPartInstallableAt(candidate,
                    getComponentId()))
            return false;
        PhysicalCapacitorPart part = (PhysicalCapacitorPart) candidate;
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

    private PhysicalCapacitorPart catalogPart(String catalogEntryId, boolean install) {
        requireSafeMutation();
        final CapacitorComponentSlot slot = capability.getSlot();
        if (install && !slot.isEmpty())
            return null;
        final CapacitorCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        if (entry.getOrientation() == PhysicalPartOrientation.REVERSED)
            throw new IllegalArgumentException("Reversed capacitor installation is not supported");
        final CapacitorSpecification specification = entry.getSpecification();
        final CapacitorElm element = DynamicCapacitorBackingAllocator.create(
            instance.getSimulationElements(), specification);
        final String componentId = slot.getComponentId();
        PhysicalMutationScope scope = newScope(install ? "catalog" : "acquire", null,
            null, catalogEntryId);
        PhysicalCapacitorPart part;
        try {
            part = scope.acquire(capability.getInventory(), componentId + "_CATALOG_PART",
                new PhysicalPartIdentityFactory<PhysicalCapacitorPart>() {
                    public PhysicalCapacitorPart create(String partId) {
                        PhysicalCapacitorPart created = new PhysicalCapacitorPart(partId,
                            specification, entry.getPlayerVisibleNameplate().forPhysicalPartId(partId),
                            element, null, CapacitorPartLocation.LOOSE, new PhysicalPartProvenance(
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
                "Capacitor replacement requires electrically unpowered generated board");
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance &&
            sim.getBoardModificationController() == modifications &&
            !sim.activeMeasurementOverlay &&
            sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            !instance.getPhysicalBoardRuntime().isMutationInProgress() &&
            !instance.getPhysicalBoardRuntime().isMutationOwnerQuarantined(getComponentId());
    }

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() ==
            capability.getSlot().getInstalledPart();
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

    private void retargetComponentLeadBindings(PhysicalCapacitorPart part,
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
        if (catalogEntryId == null)
            return false;
        for (CapacitorCatalogEntry entry : capability.getCatalog().getEntries())
            if (catalogEntryId.equals(entry.getId()))
                return true;
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
