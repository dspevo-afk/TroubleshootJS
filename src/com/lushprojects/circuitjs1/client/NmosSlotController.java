package com.lushprojects.circuitjs1.client;

/** Runtime graph and identity mutation controller for the replaceable NMOS Q1. */
final class NmosSlotController implements PhysicalSlotMutationProvider {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final ReplaceableNmosBoardCapability capability;

    NmosSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, ReplaceableNmosBoardCapability capability) {
        if (sim == null || instance == null || modifications == null || capability == null)
            throw new IllegalArgumentException("Missing NMOS slot controller context");
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.capability = capability;
    }

    public WorkbenchCapabilityMetadata getMetadata() {
        return new WorkbenchCapabilityMetadata("REPLACEABLE_NMOS_Q1", "NMOS workbench",
            "SLOT_OPERATIONS");
    }

    public String getOperationLabel(WorkbenchOperation operation) {
        if (operation == null) return "Modify MOSFET";
        if (WorkbenchOperation.INSTALL.equals(operation.getId())) return "Install as Q1";
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
        if (operation == null || !"Q1".equals(operation.getComponentId())) return false;
        String id = operation.getId();
        return WorkbenchOperation.INSTALL.equals(id) || WorkbenchOperation.REMOVE.equals(id) ||
            WorkbenchOperation.CATALOG_INSTALL.equals(id) || WorkbenchOperation.LIFT_LEAD.equals(id) ||
            WorkbenchOperation.RECONNECT_LEAD.equals(id) || WorkbenchOperation.RESTORE.equals(id);
    }

    public boolean isAvailable(WorkbenchOperation operation, WorkbenchCapabilityContext context) {
        if (!supports(operation) || !isSafeMutationAvailable()) return false;
        NmosComponentSlot slot = capability.getSlot();
        String id = operation.getId();
        if (WorkbenchOperation.CATALOG_INSTALL.equals(id))
            return slot.isEmpty() && hasCatalogEntry(operation.getCatalogEntryId());
        if (WorkbenchOperation.INSTALL.equals(id))
            return operation.getPart() instanceof PhysicalNmosPart &&
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
        if (WorkbenchOperation.INSTALL.equals(operation.getId())) return install(operation.getPart().getId());
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
        PhysicalNmosPart part = capability.getInventory().get(partId);
        if (part.isInstalled()) return false;
        installPart(part);
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        if (!capability.getSlot().isEmpty()) return false;
        final NmosCatalogEntry entry = capability.getCatalog().get(catalogEntryId);
        final NmosSpecification specification = entry.getSpecification();
        final NMosfetElm element = DynamicNmosBackingAllocator.create(
                instance.getSimulationElements(), specification);
        PhysicalNmosPart part = capability.getInventory().acquire("Q1_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalNmosPart>() {
                public PhysicalNmosPart create(String partId) {
                    return new PhysicalNmosPart(partId, specification,
                        entry.getPlayerVisibleNameplate().forPhysicalPartId(partId), element,
                        null, NmosPartLocation.LOOSE, new PhysicalPartProvenance(
                            PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                }
            });
        setOriginalFaultBoardPathEnabled(false);
        instance.registerRuntimeSimulationElement(element);
        sim.elmList.add(element);
        installPart(part);
        return true;
    }

    private void installPart(PhysicalNmosPart part) {
        instance.getComponentBindings().replaceSingleElement("Q1", part.getElement());
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
                .getForComponent("Q1"))
            binding.setComponentEndpoint(part.getTerminalForBoardPad(binding.getPadId()));
        capability.getSlot().install(part);
        setOriginalFaultBoardPathEnabled(part.ownsGeneratedFault(instance.getFaultBinding()));
        modifications.restoreComponent("Q1");
        finishMutation();
    }

    private void setOriginalFaultBoardPathEnabled(boolean enabled) {
        GeneratedFaultBinding binding = instance.getFaultBinding();
        if (binding == null || !(binding.getEffect() instanceof NmosfetDsShortFaultEffect)) return;
        ((NmosfetDsShortFaultEffect) binding.getEffect()).setBoardPathEnabled(enabled);
    }

    private void requireSafeMutation() {
        if (!isSafeMutationAvailable())
            throw new BoardModificationRejectedException(
                "NMOS replacement requires electrically unpowered generated board");
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
        for (NmosCatalogEntry entry : capability.getCatalog().getEntries())
            if (id.equals(entry.getId())) return true;
        return false;
    }
}
