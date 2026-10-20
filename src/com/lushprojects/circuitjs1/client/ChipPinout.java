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

    /** Inert package ink beside the first named terminal, not a commercial pin number. */
    static Rectangle referenceMark(PhysicalPackageGeometry geometry) {
        if (geometry == null || geometry.getTerminal(0) == null)
            throw new IllegalArgumentException("Missing chip reference geometry");
        Rectangle body = geometry.getBodyBounds();
        Point first = geometry.getTerminal(0).getConnectedLead().getBodyPoint();
        int diameter = Math.min(10, Math.min(body.width, body.height) / 4);
        int inset = diameter + 2;
        int x = Math.max(body.x + inset, Math.min(first.x, body.x + body.width - inset));
        int y = Math.max(body.y + inset, Math.min(first.y, body.y + body.height - inset));
        return new Rectangle(x - diameter / 2, y - diameter / 2, diameter, diameter);
    }
}
