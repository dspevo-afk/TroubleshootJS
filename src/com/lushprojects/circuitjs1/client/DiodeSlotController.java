package com.lushprojects.circuitjs1.client;

class DiodeSlotController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;

    DiodeSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
    }

    boolean removeInstalledPart() {
        requireSafeMutation();
        DiodeComponentSlot slot = state().getD1Slot();
        if (slot.isEmpty())
            return false;
        PhysicalDiodePart part = slot.getInstalledPart();
        modifications.removeComponent("D1");
        part.setLocation(DiodePartLocation.LOOSE);
        slot.clear();
        finishMutation();
        return true;
    }

    boolean install(String partId) {
        requireSafeMutation();
        DiodeComponentSlot slot = state().getD1Slot();
        if (!slot.isEmpty())
            return false;
        PhysicalDiodePart part = state().getInventory().get(partId);
        if (part.getLocation() != DiodePartLocation.LOOSE)
            return false;
        installPart(part);
        return true;
    }

    boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        if (!state().getD1Slot().isEmpty())
            return false;
        DiodeCatalogEntry entry = state().getCatalog().get(catalogEntryId);
        DiodeElm element = DynamicDiodeBackingAllocator.create(instance.getSimulationElements());
        PhysicalDiodePart part = new PhysicalDiodePart(state().allocatePartId(),
            new DiodeNameplate("D1", entry.getNameplate().getDisplayName(),
                entry.getNameplate().getModelName()), element, null,
            entry.isReversedInstallation(), DiodePartLocation.LOOSE);
        instance.registerRuntimeSimulationElement(element);
        state().getInventory().add(part);
        sim.elmList.add(element);
        installPart(part);
        return true;
    }

    private void installPart(PhysicalDiodePart part) {
        instance.getComponentBindings().replaceSingleElement("D1", part.getElement());
        for (GeneratedComponentConnectionBinding binding :
                instance.getConnectionBindings().getForComponent("D1"))
            binding.setComponentEndpoint(part.getTerminalForBoardPad(binding.getPadId()));
        part.setLocation(DiodePartLocation.INSTALLED);
        state().getD1Slot().install(part);
        modifications.restoreComponent("D1");
        finishMutation();
    }

    private DiodeProtectedIndicatorFamilyState state() {
        return DiodeProtectedIndicatorFamilyState.require(instance);
    }

    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException(
                "Diode replacement requires electrically unpowered generated board");
    }

    private void finishMutation() {
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
        sim.refreshBoardModificationControls();
    }
}
