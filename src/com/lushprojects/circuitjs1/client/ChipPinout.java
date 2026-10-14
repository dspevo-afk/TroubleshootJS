package com.lushprojects.circuitjs1.client;

/** Public package information, deliberately independent of the live graph and fault state. */
final class ChipPinout {
    static boolean supports(PhysicalPackage physicalPackage) {
        return physicalPackage == PhysicalPackages.TO220_REGULATOR_4 ||
            physicalPackage == PhysicalPackages.E04_DECISION_CONTROL_5 ||
            physicalPackage == PhysicalPackages.OPTOCOUPLER_4 ||
            physicalPackage == PhysicalPackages.ISOLATED_CONVERTER_7;
    }

    static PhysicalPackageGeometry geometry(PhysicalPackage physicalPackage) {
        if (!supports(physicalPackage)) throw new IllegalArgumentException("Part has no chip pinout");
        return physicalPackage.getGeometry();
    }
}
