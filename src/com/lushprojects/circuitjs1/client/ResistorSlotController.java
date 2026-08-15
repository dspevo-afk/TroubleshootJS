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
        ReplaceableComponentSlot slot = instance.getR1Slot();
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
        ReplaceableComponentSlot slot = instance.getR1Slot();
        if (!slot.isEmpty())
            return false;
        PhysicalResistorPart part = instance.getResistorInventory().get(partId);
        if (part.getLocation() != ResistorPartLocation.LOOSE)
            return false;
        instance.getComponentBindings().replaceSingleElement("R1", part.getElement());
        part.setLocation(ResistorPartLocation.INSTALLED);
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
}