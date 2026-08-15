package com.lushprojects.circuitjs1.client;

class LedSlotController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;

    LedSlotController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
    }

    boolean removeInstalledPart() {
        requireSafeMutation();
        LedComponentSlot slot = state().getLed1Slot();
        if (slot.isEmpty()) return false;
        PhysicalLedPart part = slot.getInstalledPart();
        modifications.removeComponent("LED1");
        part.setLocation(LedPartLocation.LOOSE);
        slot.clear();
        finishMutation();
        return true;
    }

    boolean install(String partId) {
        requireSafeMutation();
        if (!state().getLed1Slot().isEmpty()) return false;
        PhysicalLedPart part = state().getLedInventory().get(partId);
        if (part.getLocation() != LedPartLocation.LOOSE) return false;
        installPart(part);
        return true;
    }

    boolean installNewFromCatalog(String catalogEntryId) {
        requireSafeMutation();
        if (!state().getLed1Slot().isEmpty()) return false;
        LedCatalogEntry entry = state().getLedCatalog().get(catalogEntryId);
        LEDElm element = DynamicLedBackingAllocator.create(instance.getSimulationElements(),
            entry.getNameplate());
        PhysicalLedPart part = new PhysicalLedPart(state().allocateLedPartId(),
            new LedNameplate("LED1", entry.getNameplate().getDisplayName(),
                entry.getNameplate().getModelName(), entry.getNameplate().getRed(),
                entry.getNameplate().getGreen(), entry.getNameplate().getBlue()),
            element, entry.isReversedInstallation(), LedPartLocation.LOOSE);
        instance.registerRuntimeSimulationElement(element);
        state().getLedInventory().add(part);
        sim.elmList.add(element);
        installPart(part);
        return true;
    }

    private void installPart(PhysicalLedPart part) {
        instance.getComponentBindings().replaceSingleElement("LED1", part.getElement());
        instance.getOperationalStates().replaceLed("LED1", part.getElement());
        for (GeneratedComponentConnectionBinding binding :
                instance.getConnectionBindings().getForComponent("LED1"))
            binding.setComponentEndpoint(part.getTerminalForBoardPad(binding.getPadId()));
        part.setLocation(LedPartLocation.INSTALLED);
        state().getLed1Slot().install(part);
        modifications.restoreComponent("LED1");
        finishMutation();
    }

    private LedIndicatorFamilyState state() { return LedIndicatorFamilyState.require(instance); }
    private void requireSafeMutation() {
        if (sim.getGeneratedBoardInstance() != instance || sim.activeMeasurementOverlay ||
                !sim.isChallengeInteractionEnabled() ||
                !sim.getBoardPowerController().isElectricallyUnpowered())
            throw new BoardModificationRejectedException(
                "LED replacement requires electrically unpowered generated board");
    }
    private void finishMutation() {
        sim.needAnalyze();
        sim.requestGeneratedBoardVerification();
        sim.refreshBoardModificationControls();
    }
}
