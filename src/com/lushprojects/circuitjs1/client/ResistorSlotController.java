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
        ReplaceableResistorFamilyState family = requireFamily();
        ReplaceableComponentSlot slot = family.getReplaceableResistorSlot();
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
        ReplaceableResistorFamilyState family = requireFamily();
        ReplaceableComponentSlot slot = family.getReplaceableResistorSlot();
        if (!slot.isEmpty())
            return false;
        PhysicalResistorPart part = family.getResistorInventory().get(partId);
        if (part.getLocation() != ResistorPartLocation.LOOSE)
            return false;
        instance.getComponentBindings().replaceSingleElement(slot.getComponentId(), part.getElement());
	retargetComponentLeadBindings(part);
        part.setLocation(ResistorPartLocation.INSTALLED);
        slot.install(part);
        modifications.restoreComponent(slot.getComponentId());
        finishMutation();
        return true;
    }

    boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        ReplaceableResistorFamilyState family = requireFamily();
        ReplaceableComponentSlot slot = family.getReplaceableResistorSlot();
        if (!slot.isEmpty())
            return false;
        ResistorCatalogEntry entry = family.getResistorCatalog().get(catalogEntryId);
        ResistorElm element = DynamicResistorBackingAllocator.create(instance.getSimulationElements(),
            entry.getNameplate().getNominalResistanceOhms());
        PhysicalResistorPart part = new PhysicalResistorPart(family.allocateCatalogPartId(),
            new ResistorNameplate(slot.getComponentId(), entry.getNameplate().getNominalResistanceOhms(), 5), element,
            null, ResistorPartLocation.INSTALLED);
        instance.registerRuntimeSimulationElement(element);
        family.getResistorInventory().add(part);
        sim.elmList.add(element);
        instance.getComponentBindings().replaceSingleElement(slot.getComponentId(), element);
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
        String componentId = requireFamily().getReplaceableResistorSlot().getComponentId();
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

    private ReplaceableResistorFamilyState requireFamily() {
        if (!(instance.getFamilyState() instanceof ReplaceableResistorFamilyState))
            throw new IllegalStateException("Generated family has no replaceable resistor contract");
        return (ReplaceableResistorFamilyState) instance.getFamilyState();
    }
}
