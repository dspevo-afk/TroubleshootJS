package com.lushprojects.circuitjs1.client;

class ResistorSlotController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;

    ResistorSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
    }

    boolean removeInstalledPart() {
        requireSafeMutation();
        ReplaceableComponentSlot slot = LedIndicatorFamilyState.require(instance).getR1Slot();
        if (slot.isEmpty())
            return false;
        PhysicalResistorPart part = slot.getInstalledPart();
        modifications.removeComponent(slot.getComponentId());
        part.setLocation(ResistorPartLocation.LOOSE);
        slot.clear();
        finishMutation();
        return true;
    }

    boolean install(String partId) {
        requireSafeMutation();
        ReplaceableComponentSlot slot = LedIndicatorFamilyState.require(instance).getR1Slot();
        if (!slot.isEmpty())
            return false;
        PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getResistorInventory().get(partId);
        if (part.getLocation() != ResistorPartLocation.LOOSE)
            return false;
        instance.getComponentBindings().replaceSingleElement("R1", part.getElement());
	retargetComponentLeadBindings(part);
        part.setLocation(ResistorPartLocation.INSTALLED);
        slot.install(part);
        modifications.restoreComponent("R1");
        finishMutation();
        return true;
    }

    boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        ReplaceableComponentSlot slot = LedIndicatorFamilyState.require(instance).getR1Slot();
        if (!slot.isEmpty())
            return false;
        ResistorCatalogEntry entry = LedIndicatorFamilyState.require(instance).getResistorCatalog().get(catalogEntryId);
        ResistorElm element = DynamicResistorBackingAllocator.create(instance.getSimulationElements(),
            entry.getNameplate().getNominalResistanceOhms());
        PhysicalResistorPart part = new PhysicalResistorPart(LedIndicatorFamilyState.require(instance).allocateCatalogPartId(),
            new ResistorNameplate("R1", entry.getNameplate().getNominalResistanceOhms(), 5), element,
            null, ResistorPartLocation.INSTALLED);
        instance.registerRuntimeSimulationElement(element);
        LedIndicatorFamilyState.require(instance).getResistorInventory().add(part);
        sim.elmList.add(element);
        instance.getComponentBindings().replaceSingleElement("R1", element);
        retargetComponentLeadBindings(part);
        slot.install(part);
        modifications.restoreComponent("R1");
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
    for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings()
        .getForComponent("R1")) {
        int terminal = "R1.1".equals(binding.getPadId()) ? 0 : 1;
        binding.setComponentEndpoint(part.getPublicTerminal(terminal));
    }
    }
}