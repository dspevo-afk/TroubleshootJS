package com.lushprojects.circuitjs1.client;

/**
 * Bounded averaged switching regulator.  It models average power conversion
 * only; there is intentionally no oscillator, switching frequency, or ripple
 * waveform to observe.
 */
final class AveragedSwitchingRegulatorElm extends AbstractRailRegulatorElm {
    AveragedSwitchingRegulatorElm(int x, int y) {
        this(x, y, RailRegulationContract.averagedSwitching5V());
    }

    AveragedSwitchingRegulatorElm(int x, int y, RailRegulationContract contract) {
        super(x, y, requireAveraged(contract));
    }

    AveragedSwitchingRegulatorElm(int x, int y, int x2, int y2, int flags,
            StringTokenizer st) {
        this(x, y, x2, y2, flags,
                AbstractRailRegulatorElm.readDumpContract(st, true));
    }

    private AveragedSwitchingRegulatorElm(int x, int y, int x2, int y2,
            int flags, RailRegulationContract contract) {
        super(x, y, x2, y2, flags, requireAveraged(contract));
    }

    int getDumpType() { return 455; }

    double modelInputCurrent(double inputVoltage, double outputVoltage,
            double deliveredOutputCurrent, double enableFraction) {
        // Average conversion follows the actual solved output power.  Using
        // the target voltage here would create power during dropout or current
        // limiting and would hide the causal load/output relationship.
        double convertedPower = Math.max(0.0, outputVoltage) *
                deliveredOutputCurrent;
        double conversionInput = convertedPower /
                (inputVoltage * getContract().getEfficiency());
        return conversionInput + getContract().getQuiescentCurrentAmps() * enableFraction;
    }

    private static RailRegulationContract requireAveraged(RailRegulationContract contract) {
        if (contract == null || !contract.isAveragedSwitching())
            throw new IllegalArgumentException(
                    "Averaged switching regulator requires an averaged rail contract");
        if (contract.supportsSwitchingWaveform() || contract.supportsFrequencyMeasurement())
            throw new IllegalArgumentException(
                    "Averaged switching model cannot claim a waveform or frequency");
        return contract;
    }
}
