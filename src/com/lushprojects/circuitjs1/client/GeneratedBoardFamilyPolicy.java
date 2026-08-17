package com.lushprojects.circuitjs1.client;

/** Shared challenge policy derived from the runtime without owning physical state. */
final class GeneratedBoardFamilyPolicy {
    private GeneratedBoardFamilyPolicy() {}

    static boolean isFaultedTargetInstalled(GeneratedBoardInstance instance,
            String componentId) {
        if (instance == null || componentId == null)
            return false;
        GeneratedFaultBinding selectedFault = instance.getFaultBinding();
        if (selectedFault == null || !selectedFault.isApplied() ||
                !componentId.equals(selectedFault.getFault().getTargetComponentId()))
            return false;
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
        if (slot == null)
            return false;
        if (slot.getPhysicalPackage().isConnector())
            return true;
        PhysicalPart<?> installedPart = slot.getInstalledPart();
        return installedPart instanceof GeneratedFaultOwningPart &&
            ((GeneratedFaultOwningPart) installedPart).ownsGeneratedFault(selectedFault);
    }
}
