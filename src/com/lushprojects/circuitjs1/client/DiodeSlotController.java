package com.lushprojects.circuitjs1.client;

class DiodeSlotController implements PhysicalSlotMutationProvider {
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
            return operation.getPart() != null && ownsPart(operation.getPart().getId()) &&
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

    public boolean removeInstalledPart() {
        requireSafeMutation();
        DiodeComponentSlot slot = capability.getSlot();
        if (slot.isEmpty())
            return false;
        modifications.removeComponent(slot.getComponentId());
        slot.clear();
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
        installPart(part);
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        if (!capability.getSlot().isEmpty())
            return false;
        final DiodeCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final DiodeElm element = DynamicDiodeBackingAllocator.create(instance.getSimulationElements());
        final String componentId = capability.getSlot().getComponentId();
        PhysicalDiodePart part = capability.getInventory().acquire(
            componentId + "_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalDiodePart>() {
                public PhysicalDiodePart create(String partId) {
                    return new PhysicalDiodePart(partId,
                        new DiodeNameplate(componentId, entry.getNameplate().getDisplayName(),
                            entry.getNameplate().getModelName()), element, null,
                        entry.isReversedInstallation(), DiodePartLocation.LOOSE);
                }
            });
        instance.registerRuntimeSimulationElement(element);
        sim.elmList.add(element);
        installPart(part);
        return true;
    }

    private void installPart(PhysicalDiodePart part) {
        String componentId = capability.getSlot().getComponentId();
        instance.getComponentBindings().replaceSingleElement(componentId, part.getElement());
        for (GeneratedComponentConnectionBinding binding :
                instance.getConnectionBindings().getForComponent(componentId))
            binding.setComponentEndpoint(part.getTerminalForBoardPad(binding.getPadId()));
        capability.getSlot().install(part);
        modifications.restoreComponent(componentId);
        finishMutation();
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException(
                "Diode replacement requires electrically unpowered generated board");
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance && !sim.activeMeasurementOverlay &&
            sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered();
    }

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() == capability.getSlot().getInstalledPart();
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
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
        sim.refreshBoardModificationControls();
    }
}
