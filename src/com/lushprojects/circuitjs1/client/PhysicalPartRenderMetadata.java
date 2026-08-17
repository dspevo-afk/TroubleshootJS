package com.lushprojects.circuitjs1.client;

/** Typed visual metadata shared by installed, replacement, and fixed parts. */
final class PhysicalPartRenderMetadata {
    private final PhysicalSpecification visualSpecification;
    private final PhysicalPartOrientation orientation;
    private final PhysicalPartRenderProbeProvider looseProbeProvider;

    PhysicalPartRenderMetadata(PhysicalSpecification visualSpecification,
            boolean reversedInstallation) {
        this(visualSpecification, PhysicalPartOrientation.polarized(reversedInstallation), null);
    }

    PhysicalPartRenderMetadata(PhysicalSpecification visualSpecification,
            boolean reversedInstallation, PhysicalPartRenderProbeProvider looseProbeProvider) {
        this(visualSpecification, PhysicalPartOrientation.polarized(reversedInstallation),
            looseProbeProvider);
    }

    PhysicalPartRenderMetadata(PhysicalSpecification visualSpecification,
            PhysicalPartOrientation orientation, PhysicalPartRenderProbeProvider looseProbeProvider) {
        if (visualSpecification == null)
            throw new IllegalArgumentException("Missing physical render specification");
        if (orientation == null)
            throw new IllegalArgumentException("Missing physical part orientation");
        this.visualSpecification = visualSpecification;
        this.orientation = orientation;
        this.looseProbeProvider = looseProbeProvider;
    }

    PhysicalSpecification getVisualSpecification() { return visualSpecification; }
    PhysicalPartOrientation getOrientation() { return orientation; }
    boolean isReversedInstallation() { return orientation == PhysicalPartOrientation.REVERSED; }
    PhysicalPartRenderProbeProvider getLooseProbeProvider() { return looseProbeProvider; }
}
