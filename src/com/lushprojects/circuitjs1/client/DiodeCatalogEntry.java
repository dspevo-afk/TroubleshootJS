package com.lushprojects.circuitjs1.client;

final class DiodeCatalogEntry extends AbstractPhysicalCatalogEntry<DiodeNameplate> {

    DiodeCatalogEntry(String id, DiodeNameplate nameplate, boolean reversedInstallation) {
        super(id, nameplate, createPlayerNameplate(id, nameplate),
            PhysicalPartOrientation.polarized(reversedInstallation));
    }

    DiodeNameplate getNameplate() { return getSpecification(); }
    boolean isReversedInstallation() {
        return getOrientation() == PhysicalPartOrientation.REVERSED;
    }

    private static PhysicalNameplate createPlayerNameplate(String id, DiodeNameplate nameplate) {
        return new PhysicalNameplate(id, nameplate.getDisplayName(), "Part",
            nameplate.getDisplayName());
    }
}
