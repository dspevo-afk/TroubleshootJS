package com.lushprojects.circuitjs1.client;

/** Immutable local, insulated raised-conductor clearance. Heights are drawing units, not mm. */
final class RaisedCrossoverGeometry {
    private final Rectangle underpass;
    final int conductorBottomHeight, maximumCopperHeight, insulationThickness;

    RaisedCrossoverGeometry(Rectangle underpass, int conductorBottomHeight,
            int maximumCopperHeight, int insulationThickness) {
        if (underpass == null || underpass.width <= 0 || underpass.height <= 0 ||
                maximumCopperHeight <= 0 || insulationThickness <= 0 ||
                (long)conductorBottomHeight - insulationThickness <= maximumCopperHeight)
            throw new IllegalArgumentException("Raised crossover has no insulated vertical clearance");
        this.underpass = new Rectangle(underpass);
        this.conductorBottomHeight = conductorBottomHeight;
        this.maximumCopperHeight = maximumCopperHeight;
        this.insulationThickness = insulationThickness;
    }

    Rectangle getUnderpass() { return new Rectangle(underpass); }

    void validate(PhysicalPackageGeometry geometry) {
        Rectangle courtyard = geometry.getRoutingCourtyard();
        if (geometry.isDeveloperGeneric() || !contains(courtyard, underpass) ||
                !underpass.intersects(geometry.getBodyBounds()))
            throw new IllegalArgumentException("Raised crossover needs an authoritative body and courtyard");
        // A continuous passage must leave both opposite edges of the courtyard.
        if (!(underpass.y == courtyard.y && underpass.height == courtyard.height) &&
                !(underpass.x == courtyard.x && underpass.width == courtyard.width))
            throw new IllegalArgumentException("Raised underpass does not cross the courtyard");
        for (PhysicalPackageGeometry.Terminal terminal : geometry.getTerminals()) {
            if (underpass.intersects(terminal.getPadBounds()) ||
                    underpass.intersects(terminal.getBoardPadProbeBounds()) ||
                    underpass.intersects(terminal.getConnectedLead().getBounds()) ||
                    underpass.intersects(terminal.getLiftedLead().getBounds()) ||
                    underpass.intersects(terminal.getComponentLeadProbeBounds()) ||
                    underpass.intersects(terminal.getComponentLeadProbeBounds(true)))
                throw new IllegalArgumentException("Underpass intersects exposed terminal metal or probe access");
        }
    }

    RaisedCrossoverGeometry mirroredHorizontally(int width) {
        return new RaisedCrossoverGeometry(new Rectangle(width - underpass.x - underpass.width,
            underpass.y, underpass.width, underpass.height), conductorBottomHeight,
            maximumCopperHeight, insulationThickness);
    }

    boolean isEquivalentTo(RaisedCrossoverGeometry other) {
        return other != null && underpass.equals(other.underpass) &&
            conductorBottomHeight == other.conductorBottomHeight &&
            maximumCopperHeight == other.maximumCopperHeight &&
            insulationThickness == other.insulationThickness;
    }

    String fingerprint() {
        return underpass.x + "," + underpass.y + "," + underpass.width + "," + underpass.height +
            "/bottom=" + conductorBottomHeight + "/copper=" + maximumCopperHeight +
            "/insulation=" + insulationThickness;
    }

    /** Check the whole swept copper/courtyard intersection, not just the centerline. */
    static boolean permits(Rectangle courtyard, Rectangle passage, Rectangle stroke) {
        if (passage == null || stroke == null || stroke.width <= 0 || stroke.height <= 0)
            return false;
        long left = Math.max((long)courtyard.x, stroke.x);
        long top = Math.max((long)courtyard.y, stroke.y);
        long right = Math.min((long)courtyard.x + courtyard.width, (long)stroke.x + stroke.width);
        long bottom = Math.min((long)courtyard.y + courtyard.height, (long)stroke.y + stroke.height);
        return left < right && top < bottom && left >= passage.x && top >= passage.y &&
            right <= (long)passage.x + passage.width && bottom <= (long)passage.y + passage.height;
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long)inner.x + inner.width <= (long)outer.x + outer.width &&
            (long)inner.y + inner.height <= (long)outer.y + outer.height;
    }
}
