package com.lushprojects.circuitjs1.client;

/** Family-owned graph and identity mutation controller for Q1. */
final class NpnSlotController implements PhysicalSlotMutationProvider {
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
        return new WorkbenchCapabilityMetadata("REPLACEABLE_NPN_Q1", "NPN workbench",
            "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify transistor";
        if (WorkbenchOperation.INSTALL.equals(operation.getId()))
            return "Install as Q1";
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
        if (operation == null || !"Q1".equals(operation.getComponentId()))
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
                capability.ownsPart(operation.getPart().getId()) &&
                !operation.getPart().isInstalled() && slot.isEmpty();
        if (WorkbenchOperation.REMOVE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation);
        if (WorkbenchOperation.LIFT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState("Q1") == ComponentPhysicalState.INSTALLED &&
                hasConnectedPad(operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState("Q1") == ComponentPhysicalState.LEAD_LIFTED &&
                hasDisconnectedPad(operation.getPadId());
        if (WorkbenchOperation.RESTORE.equals(id))
            return !slot.isEmpty() && matchesInstalledPart(operation) &&
                modifications.getComponentState("Q1") != ComponentPhysicalState.INSTALLED;
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
            return modifications.liftLead("Q1", operation.getPadId());
        if (WorkbenchOperation.RECONNECT_LEAD.equals(operation.getId()))
            return modifications.reconnectLead("Q1", operation.getPadId());
        return modifications.restoreComponent("Q1");
    }

    public String getComponentId() { return "Q1"; }
    public boolean ownsPart(String partId) { return capability.ownsPart(partId); }

    public boolean removeInstalledPart() {
        requireSafeMutation();
        if (capability.getSlot().isEmpty()) return false;
        modifications.removeComponent("Q1");
        capability.getSlot().clear();
        finishMutation();
        return true;
    }

    public boolean install(String partId) {
        requireSafeMutation();
        if (!capability.getSlot().isEmpty()) return false;
        PhysicalNpnPart part = capability.getInventory().get(partId);
        if (part.isInstalled()) return false;
        installPart(part);
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        if (!capability.getSlot().isEmpty()) return false;
        final NpnCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final NpnSpecification specification = entry.getSpecification();
        final NTransistorElm element = DynamicNpnBackingAllocator.create(
            instance.getSimulationElements(), specification);
        PhysicalNpnPart part = capability.getInventory().acquire("Q1_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalNpnPart>() {
                public PhysicalNpnPart create(String partId) {
                    return new PhysicalNpnPart(partId, specification,
                        entry.getPlayerVisibleNameplate().forPhysicalPartId(partId), element,
                        null, NpnPartLocation.LOOSE, new PhysicalPartProvenance(
                            PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                }
            });
        instance.registerRuntimeSimulationElement(element);
        sim.elmList.add(element);
        installPart(part);
        return true;
    }

    private void installPart(PhysicalNpnPart part) {
        instance.getComponentBindings().replaceSingleElement("Q1", part.getElement());
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent("Q1"))
            binding.setComponentEndpoint(part.getTerminalForBoardPad(binding.getPadId()));
        capability.getSlot().install(part);
        modifications.restoreComponent("Q1");
        finishMutation();
    }

    private void requireSafeMutation() {
        if (!isSafeMutationAvailable())
            throw new BoardModificationRejectedException(
                "NPN replacement requires electrically unpowered generated board");
    }

    private boolean isSafeMutationAvailable() {
        return sim.getGeneratedBoardInstance() == instance && !sim.activeMeasurementOverlay &&
            sim.isChallengeInteractionEnabled() && sim.getBoardPowerController()
                .isElectricallyUnpowered();
    }

    private void finishMutation() {
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
        sim.refreshBoardModificationControls();
    }

    private boolean matchesInstalledPart(WorkbenchOperation operation) {
        return operation.getPart() == null || operation.getPart() == capability.getSlot()
            .getInstalledPart();
    }

    private boolean hasConnectedPad(String padId) {
        return hasPad(padId) && modifications.isLeadConnected("Q1", padId);
    }

    private boolean hasDisconnectedPad(String padId) {
        return hasPad(padId) && !modifications.isLeadConnected("Q1", padId);
    }

    private boolean hasPad(String padId) {
        BoardPad pad = padId == null ? null : instance.getBoard().getPad(padId);
        return pad != null && "Q1".equals(pad.getComponentId());
    }

    private boolean hasCatalogEntry(String id) {
        if (id == null) return false;
        for (NpnCatalogEntry entry : capability.getCatalog().getEntries())
            if (id.equals(entry.getId())) return true;
        return false;
    }
}
