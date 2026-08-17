package com.lushprojects.circuitjs1.client;

final class LedCatalogEntry extends AbstractPhysicalCatalogEntry<LedNameplate> {

    LedCatalogEntry(String id, String displayName, LedNameplate nameplate,
            boolean reversedInstallation) {
        super(id, nameplate, createPlayerNameplate(id, displayName),
            PhysicalPartOrientation.polarized(reversedInstallation));
    }

    String getDisplayName() { return getPlayerVisibleNameplate().getDisplayName(); }
    LedNameplate getNameplate() { return getSpecification(); }
    boolean isReversedInstallation() {
        return getOrientation() == PhysicalPartOrientation.REVERSED;
    }

    private static PhysicalNameplate createPlayerNameplate(String id, String displayName) {
        return new PhysicalNameplate(id, displayName, "Part", displayName);
    }
}
