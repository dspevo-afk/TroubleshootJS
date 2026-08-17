package com.lushprojects.circuitjs1.client;

class ResistorSlotController implements PhysicalSlotMutationProvider {
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
        ReplaceableComponentSlot slot = capability.getSlot();
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

    public boolean removeInstalledPart() {
        requireSafeMutation();
        ReplaceableComponentSlot slot = capability.getSlot();
        if (slot.isEmpty())
            return false;
        PhysicalResistorPart part = slot.getInstalledPart();
        modifications.removeComponent(slot.getComponentId());
        slot.clear();
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
        instance.getComponentBindings().replaceSingleElement(slot.getComponentId(), part.getElement());
	instance.getComponentBindings().replaceAuxiliaryComponentElement(slot.getComponentId(),
	    part.getSecondaryOpenPath().getSimulationElement());
	retargetComponentLeadBindings(part);
        slot.install(part);
        modifications.restoreComponent(slot.getComponentId());
        finishMutation();
        return true;
    }

    public boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        ReplaceableComponentSlot slot = capability.getSlot();
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
        PhysicalResistorPart part = capability.getInventory().acquire(
            componentId + "_CATALOG_PART",
            new PhysicalPartIdentityFactory<PhysicalResistorPart>() {
                public PhysicalResistorPart create(String partId) {
                    return new PhysicalResistorPart(partId, specification, specification,
                        playerNameplate.forPhysicalPartId(partId), element, null, openPath,
                        ResistorPartLocation.INSTALLED, new PhysicalPartProvenance(
                            PhysicalPartProvenance.CATALOG_ACQUIRED, partId));
                }
            });
        instance.registerRuntimeSimulationElement(element);
        instance.registerRuntimeSimulationElement(openPath.getSimulationElement());
        capability.getStressDamageSystem().register(part);
        sim.elmList.add(element);
        sim.elmList.add(openPath.getSimulationElement());
        instance.getComponentBindings().replaceSingleElement(slot.getComponentId(), element);
        instance.getComponentBindings().replaceAuxiliaryComponentElement(slot.getComponentId(),
            openPath.getSimulationElement());
        retargetComponentLeadBindings(part);
        slot.install(part);
        modifications.restoreComponent(slot.getComponentId());
        finishMutation();
        return true;
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException(
                "Resistor replacement requires electrically unpowered generated board");
    }

    private void finishMutation() {
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
        sim.refreshBoardModificationControls();
    }

    private void retargetComponentLeadBindings(PhysicalResistorPart part) {
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
            binding.setComponentEndpoint(part.getPublicTerminal(terminal));
        }
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
        for (ResistorCatalogEntry entry : capability.getCatalog().getEntries())
            if (catalogEntryId.equals(entry.getId())) return true;
        return false;
    }

}
