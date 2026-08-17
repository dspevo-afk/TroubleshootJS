package com.lushprojects.circuitjs1.client;

/** Typed visual metadata shared by installed, replacement, and fixed parts. */
final class PhysicalPartRenderMetadata {
    private final PhysicalSpecification visualSpecification;
    private final boolean reversedInstallation;
    private final PhysicalPartRenderProbeProvider looseProbeProvider;

    PhysicalPartRenderMetadata(PhysicalSpecification visualSpecification,
            boolean reversedInstallation) {
        this(visualSpecification, reversedInstallation, null);
    }

    PhysicalPartRenderMetadata(PhysicalSpecification visualSpecification,
            boolean reversedInstallation, PhysicalPartRenderProbeProvider looseProbeProvider) {
        if (visualSpecification == null)
            throw new IllegalArgumentException("Missing physical render specification");
        this.visualSpecification = visualSpecification;
        this.reversedInstallation = reversedInstallation;
        this.looseProbeProvider = looseProbeProvider;
    }

    PhysicalSpecification getVisualSpecification() { return visualSpecification; }
    boolean isReversedInstallation() { return reversedInstallation; }
    PhysicalPartRenderProbeProvider getLooseProbeProvider() { return looseProbeProvider; }
}
