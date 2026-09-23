package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, data-only declaration of a bounded regulated rail.
 *
 * <p>The contract deliberately contains no CircuitJS element, solver, source,
 * or provider reference.  A later family can consume this declaration without
 * depending on whether its electrical owner is the linear or averaged model.
 * An averaged model never advertises a switching frequency or waveform through
 * this contract.</p>
 */
final class RailRegulationContract {
    static final int VERSION = 2;

    /** Explicit standard profile for the six declared roles. */
    private static final double STANDARD_USABLE_REGULATED_CURRENT_FRACTION = .90;
    private static final double STANDARD_REGULATED_VOLTAGE_TOLERANCE_FRACTION = .05;

    static final String INPUT_TERMINAL = "INPUT";
    static final String OUTPUT_TERMINAL = "OUTPUT";
    static final String RETURN_TERMINAL = "RETURN";
    static final String ENABLE_TERMINAL = "ENABLE";

    private final String variantId;
    private final List<String> terminalIds;
    private final double nominalOutputVolts;
    private final double minimumInputVolts;
    private final double maximumInputVolts;
    private final double dropoutVolts;
    private final double maximumOutputCurrentAmps;
    private final double outputResistanceOhms;
    private final double enableLowVolts;
    private final double enableHighVolts;
    private final double quiescentCurrentAmps;
    private final double efficiency;
    private final boolean averagedSwitching;
    private final boolean switchingWaveformSupported;
    private final double usableRegulatedCurrentFraction;
    private final double regulatedVoltageToleranceVolts;

    /**
     * Creates a rail declaration with an explicit normal regulation envelope.
     * The usable-current fraction is below the hard current-limit knee; the
     * voltage tolerance is the maximum declared droop for that normal region.
     */
    RailRegulationContract(String variantId,
            String inputTerminalId, String outputTerminalId,
            String returnTerminalId, String enableTerminalId,
            double nominalOutputVolts, double minimumInputVolts,
            double maximumInputVolts, double dropoutVolts,
            double maximumOutputCurrentAmps, double outputResistanceOhms,
            double enableLowVolts, double enableHighVolts,
            double quiescentCurrentAmps, double efficiency,
            boolean averagedSwitching, boolean switchingWaveformSupported,
            double usableRegulatedCurrentFraction,
            double regulatedVoltageToleranceVolts) {
        this.variantId = requireId(variantId, "variantId");
        String input = requireId(inputTerminalId, "inputTerminalId");
        String output = requireId(outputTerminalId, "outputTerminalId");
        String returned = requireId(returnTerminalId, "returnTerminalId");
        String enable = requireId(enableTerminalId, "enableTerminalId");
        if (input.equals(output) || input.equals(returned) ||
                input.equals(enable) || output.equals(returned) ||
                output.equals(enable) || returned.equals(enable)) {
            throw new IllegalArgumentException("Rail terminal ids must be unique");
        }
        finitePositive(nominalOutputVolts, "nominalOutputVolts");
        finiteNonnegative(minimumInputVolts, "minimumInputVolts");
        finitePositive(maximumInputVolts, "maximumInputVolts");
        finiteNonnegative(dropoutVolts, "dropoutVolts");
        finitePositive(maximumOutputCurrentAmps, "maximumOutputCurrentAmps");
        finitePositive(outputResistanceOhms, "outputResistanceOhms");
        finiteNonnegative(enableLowVolts, "enableLowVolts");
        finitePositive(enableHighVolts, "enableHighVolts");
        finiteNonnegative(quiescentCurrentAmps, "quiescentCurrentAmps");
        finitePositive(efficiency, "efficiency");
        finitePositive(usableRegulatedCurrentFraction,
                "usableRegulatedCurrentFraction");
        finiteNonnegative(regulatedVoltageToleranceVolts,
                "regulatedVoltageToleranceVolts");
        if (maximumInputVolts < minimumInputVolts ||
                maximumInputVolts <= nominalOutputVolts + dropoutVolts ||
                minimumInputVolts < nominalOutputVolts + dropoutVolts ||
                enableHighVolts <= enableLowVolts || efficiency > 1.0 ||
                switchingWaveformSupported ||
                usableRegulatedCurrentFraction >= 1.0 ||
                regulatedVoltageToleranceVolts >= nominalOutputVolts ||
                maximumOutputCurrentAmps * usableRegulatedCurrentFraction *
                    outputResistanceOhms > regulatedVoltageToleranceVolts) {
            throw new IllegalArgumentException("Unsupported rail regulation envelope");
        }
        ArrayList<String> terminals = new ArrayList<String>();
        terminals.add(input);
        terminals.add(output);
        terminals.add(returned);
        terminals.add(enable);
        this.terminalIds = Collections.unmodifiableList(terminals);
        this.nominalOutputVolts = nominalOutputVolts;
        this.minimumInputVolts = minimumInputVolts;
        this.maximumInputVolts = maximumInputVolts;
        this.dropoutVolts = dropoutVolts;
        this.maximumOutputCurrentAmps = maximumOutputCurrentAmps;
        this.outputResistanceOhms = outputResistanceOhms;
        this.enableLowVolts = enableLowVolts;
        this.enableHighVolts = enableHighVolts;
        this.quiescentCurrentAmps = quiescentCurrentAmps;
        this.efficiency = efficiency;
        this.averagedSwitching = averagedSwitching;
        this.switchingWaveformSupported = false;
        this.usableRegulatedCurrentFraction = usableRegulatedCurrentFraction;
        this.regulatedVoltageToleranceVolts = regulatedVoltageToleranceVolts;
    }

    static RailRegulationContract linear12V() {
        return linear("linear-12v", 12.0, 12.8, 24.0, .20, .001);
    }

    static RailRegulationContract linear5V() {
        return linear("linear-5v", 5.0, 5.8, 24.0, .20, .001);
    }

    static RailRegulationContract linear3V3() {
        return linear("linear-3v3", 3.3, 4.1, 24.0, .20, .001);
    }

    static RailRegulationContract averagedSwitching12V() {
        return averaged("averaged-switching-12v", 12.0, 12.8, 24.0,
                .20, .003, .90);
    }

    static RailRegulationContract averagedSwitching5V() {
        return averaged("averaged-switching-5v", 5.0, 5.8, 24.0,
                .20, .003, .90);
    }

    static RailRegulationContract averagedSwitching3V3() {
        return averaged("averaged-switching-3v3", 3.3, 4.1, 24.0,
                .20, .003, .90);
    }

    private static RailRegulationContract linear(String id, double nominal,
            double minimumInput, double maximumInput, double current,
            double quiescent) {
        return new RailRegulationContract(id, INPUT_TERMINAL, OUTPUT_TERMINAL,
                RETURN_TERMINAL, ENABLE_TERMINAL, nominal, minimumInput,
                maximumInput, .8, current, .1, .8, 2.0, quiescent, 1.0,
                false, false, STANDARD_USABLE_REGULATED_CURRENT_FRACTION,
                nominal * STANDARD_REGULATED_VOLTAGE_TOLERANCE_FRACTION);
    }

    private static RailRegulationContract averaged(String id, double nominal,
            double minimumInput, double maximumInput, double current,
            double quiescent, double efficiency) {
        return new RailRegulationContract(id, INPUT_TERMINAL, OUTPUT_TERMINAL,
                RETURN_TERMINAL, ENABLE_TERMINAL, nominal, minimumInput,
                maximumInput, .8, current, .1, .8, 2.0, quiescent,
                efficiency, true, false, STANDARD_USABLE_REGULATED_CURRENT_FRACTION,
                nominal * STANDARD_REGULATED_VOLTAGE_TOLERANCE_FRACTION);
    }

    String getVariantId() { return variantId; }
    int getVersion() { return VERSION; }
    List<String> getTerminalIds() { return terminalIds; }
    String getInputTerminalId() { return terminalIds.get(0); }
    String getOutputTerminalId() { return terminalIds.get(1); }
    String getReturnTerminalId() { return terminalIds.get(2); }
    String getEnableTerminalId() { return terminalIds.get(3); }

    double getNominalOutputVolts() { return nominalOutputVolts; }
    double getOutputNominalVolts() { return nominalOutputVolts; }
    double getMinimumInputVolts() { return minimumInputVolts; }
    double getInputMinimumVolts() { return minimumInputVolts; }
    double getMaximumInputVolts() { return maximumInputVolts; }
    double getInputMaximumVolts() { return maximumInputVolts; }
    double getDropoutVolts() { return dropoutVolts; }
    double getDropoutVoltage() { return dropoutVolts; }
    double getMaximumOutputCurrentAmps() { return maximumOutputCurrentAmps; }
    double getCurrentLimitAmps() { return maximumOutputCurrentAmps; }
    double getOutputResistanceOhms() { return outputResistanceOhms; }
    double getEnableLowVolts() { return enableLowVolts; }
    double getEnableHighVolts() { return enableHighVolts; }
    double getQuiescentCurrentAmps() { return quiescentCurrentAmps; }
    double getEfficiency() { return efficiency; }
    double getUsableRegulatedCurrentFraction() {
        return usableRegulatedCurrentFraction;
    }
    double getUsableRegulatedCurrentAmps() {
        return maximumOutputCurrentAmps * usableRegulatedCurrentFraction;
    }
    double getRegulatedVoltageToleranceVolts() {
        return regulatedVoltageToleranceVolts;
    }
    double getMinimumRegulatedOutputVolts() {
        return nominalOutputVolts - regulatedVoltageToleranceVolts;
    }
    boolean isAveragedSwitching() { return averagedSwitching; }
    boolean isLinear() { return !averagedSwitching; }

    /** Always false: an averaged model does not contain a switching waveform. */
    boolean supportsSwitchingWaveform() { return switchingWaveformSupported; }
    boolean supportsFrequencyMeasurement() { return false; }
    boolean hasSwitchingWaveform() { return false; }

    private static String requireId(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static void finitePositive(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0)
            throw new IllegalArgumentException(name + " must be finite and positive");
    }

    private static void finiteNonnegative(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
}
