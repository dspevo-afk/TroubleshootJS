package com.lushprojects.circuitjs1.client;

/**
 * Immutable local parameters for an NMOS low-side driver provider.
 *
 * <p>The profile contains only the variation points owned by the NMOS
 * provider.  CircuitJS primitive category and post-map validation remains in
 * {@link ElectricalRealizationSpec}; this type supplies the values consumed
 * by the electrical, physical, and observation adapters.</p>
 */
final class NmosDriverProfile {
    static final String STANDARD_PROVIDER_ID = "nmos-low-side-driver";
    static final String ALTERNATE_PROVIDER_ID = "nmos-low-side-driver-alt";
    static final int VERSION = 1;
    static final String CONTROL_RESISTOR_ID = "RG";
    static final String PULL_DOWN_RESISTOR_ID = "RPD";
    static final String CONTROL_NODE_ID = "GATE";
    static final String MODEL_ID = "NMOS";
    static final String PRIMITIVE_TYPE = "NMOS";
    static final String CONTROL_TERMINAL = "G";
    static final String SWITCHED_TERMINAL = "D";
    static final String RETURN_TERMINAL = "S";
    static final int RETURN_POST_INDEX = 1;
    static final double PULL_DOWN_OHMS = 100000.0;
    static final double RESISTOR_TOLERANCE_FRACTION = 0.05;

    private static final NmosDriverProfile STANDARD = new NmosDriverProfile(
        STANDARD_PROVIDER_ID, VERSION, 1000.0, 1.5, 10.0);
    private static final NmosDriverProfile ALTERNATE = new NmosDriverProfile(
        ALTERNATE_PROVIDER_ID, VERSION, 680.0, 1.2, 6.0);

    private final String providerId;
    private final int version;
    private final double gateResistanceOhms;
    private final double thresholdVolts;
    private final double beta;

    private NmosDriverProfile(String providerId, int version,
            double gateResistanceOhms, double thresholdVolts, double beta) {
        this.providerId = FunctionalBlockDescriptor.requireId(providerId,
            "nmos.profile.providerId");
        if (version < 1)
            throw new IllegalArgumentException("Invalid NMOS profile version");
        this.version = version;
        requireFinitePositive(gateResistanceOhms, "gateResistanceOhms");
        requireFinitePositive(thresholdVolts, "thresholdVolts");
        requireFinitePositive(beta, "beta");
        this.gateResistanceOhms = gateResistanceOhms;
        this.thresholdVolts = thresholdVolts;
        this.beta = beta;
    }

    static NmosDriverProfile standard() { return STANDARD; }
    static NmosDriverProfile alternate() { return ALTERNATE; }

    String getProviderId() { return providerId; }
    int getVersion() { return version; }
    double getGateResistanceOhms() { return gateResistanceOhms; }
    double getThresholdVolts() { return thresholdVolts; }
    double getBeta() { return beta; }
    double getPullDownOhms() { return PULL_DOWN_OHMS; }
    double getResistorToleranceFraction() { return RESISTOR_TOLERANCE_FRACTION; }
    double getControlDemandAmps() {
        return 5.25 / (PULL_DOWN_OHMS * (1.0 - RESISTOR_TOLERANCE_FRACTION));
    }
    String getControlResistorId() { return CONTROL_RESISTOR_ID; }
    String getPullDownResistorId() { return PULL_DOWN_RESISTOR_ID; }
    String getControlNodeId() { return CONTROL_NODE_ID; }
    String getModelId() { return MODEL_ID; }
    String getPrimitiveType() { return PRIMITIVE_TYPE; }
    String getControlTerminal() { return CONTROL_TERMINAL; }
    String getSwitchedTerminal() { return SWITCHED_TERMINAL; }
    String getReturnTerminal() { return RETURN_TERMINAL; }
    int getReturnPostIndex() { return RETURN_POST_INDEX; }
    PhysicalPackage getPhysicalPackage() { return PhysicalPackages.TO92_NMOS; }

    private static void requireFinitePositive(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0)
            throw new IllegalArgumentException("NMOS profile " + field +
                " must be finite and positive");
    }
}
