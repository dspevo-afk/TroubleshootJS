package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.State;

/** Small provider requirement, not a model implementation or controller. */
final class PoweredProviderOperatingContract {
    enum SignalState { ASSERTED, DEASSERTED, PRESENT, MISSING, UNKNOWN, NOT_REQUIRED }
    enum Status { OPERABLE, UNPOWERED, BROWNOUT, RESET_HELD, CLOCK_MISSING, UNKNOWN, OUT_OF_ENVELOPE }
    private final Range allowedSupplyVolts;
    private final Scalar brownoutThresholdVolts, inputLowMaximumVolts, inputHighMinimumVolts;
    private final Scalar requiredDriveAmps, maximumLoadAmps;
    private final boolean resetRequired, clockRequired;
    PoweredProviderOperatingContract(Range supply, Scalar brownout, Scalar low, Scalar high,
            Scalar drive, Scalar load, boolean reset, boolean clock) {
        if (supply == null || brownout == null || low == null || high == null)
            throw new IllegalArgumentException("Missing provider requirement");
        PowerDomainContract.nonnegative(brownout); PowerDomainContract.nonnegative(drive);
        PowerDomainContract.nonnegative(load);
        if (low.getState() == State.KNOWN && high.getState() == State.KNOWN && low.getValue() >= high.getValue())
            throw new IllegalArgumentException("Overlapping input thresholds");
        if (supply.getState() == State.KNOWN && supply.getMinimum() < 0)
            throw new IllegalArgumentException("This powered-provider envelope requires nonnegative supply");
        if (brownout.getState() == State.KNOWN && supply.getState() == State.KNOWN &&
                brownout.getValue() > supply.getMaximum())
            throw new IllegalArgumentException("Brownout exceeds allowed supply");
        allowedSupplyVolts = supply; brownoutThresholdVolts = brownout;
        inputLowMaximumVolts = low; inputHighMinimumVolts = high;
        requiredDriveAmps = drive; maximumLoadAmps = load; resetRequired = reset; clockRequired = clock;
    }
    Range getAllowedSupplyVolts() { return allowedSupplyVolts; }
    Scalar getBrownoutThresholdVolts() { return brownoutThresholdVolts; }
    Scalar getInputLowMaximumVolts() { return inputLowMaximumVolts; }
    Scalar getInputHighMinimumVolts() { return inputHighMinimumVolts; }
    Scalar getRequiredDriveAmps() { return requiredDriveAmps; }
    Scalar getMaximumLoadAmps() { return maximumLoadAmps; }
    boolean isResetRequired() { return resetRequired; }
    boolean isClockRequired() { return clockRequired; }
    Status evaluate(double volts, SignalState reset, SignalState clock, boolean allRails) {
        if (!PowerDomainContract.finite(volts) || reset == null || clock == null ||
                allowedSupplyVolts.getState() != State.KNOWN) return Status.UNKNOWN;
        if (brownoutThresholdVolts.getState() == State.UNKNOWN || inputLowMaximumVolts.getState() == State.UNKNOWN ||
                inputHighMinimumVolts.getState() == State.UNKNOWN || requiredDriveAmps.getState() == State.UNKNOWN ||
                maximumLoadAmps.getState() == State.UNKNOWN) return Status.UNKNOWN;
        if (volts == 0) return Status.UNPOWERED;
        if (volts < 0 || volts > allowedSupplyVolts.getMaximum()) return Status.OUT_OF_ENVELOPE;
        if (!allRails) return Status.UNKNOWN;
        if (brownoutThresholdVolts.getState() == State.KNOWN && volts < brownoutThresholdVolts.getValue())
            return Status.BROWNOUT;
        if (volts < allowedSupplyVolts.getMinimum()) return Status.OUT_OF_ENVELOPE;
        if (resetRequired) {
            if (reset == SignalState.ASSERTED) return Status.RESET_HELD;
            if (reset != SignalState.DEASSERTED) return Status.UNKNOWN;
        }
        if (clockRequired) {
            if (clock == SignalState.MISSING) return Status.CLOCK_MISSING;
            if (clock != SignalState.PRESENT) return Status.UNKNOWN;
        }
        return Status.OPERABLE;
    }
    boolean acceptsSupplyRange(Range actual) {
        return actual != null && actual.getState() == State.KNOWN &&
            allowedSupplyVolts.getState() == State.KNOWN && allowedSupplyVolts.contains(actual);
    }
    boolean acceptsDriveCapacity(Scalar actual) {
        return actual != null && actual.getState() == State.KNOWN &&
            requiredDriveAmps.getState() == State.KNOWN && actual.getValue() >= requiredDriveAmps.getValue();
    }
    String toCanonical() {
        StringBuilder out = new StringBuilder("powered-provider/1;");
        PowerDomainContract.token(out, PowerDomainContract.range(allowedSupplyVolts));
        for (Scalar s : new Scalar[] {brownoutThresholdVolts, inputLowMaximumVolts,
                inputHighMinimumVolts, requiredDriveAmps, maximumLoadAmps})
            PowerDomainContract.token(out, PowerDomainContract.scalar(s));
        PowerDomainContract.token(out, Boolean.toString(resetRequired));
        PowerDomainContract.token(out, Boolean.toString(clockRequired));
        return out.toString();
    }
}
