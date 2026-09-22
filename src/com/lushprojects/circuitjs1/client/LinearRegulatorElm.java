package com.lushprojects.circuitjs1.client;

/** Finite series-pass regulator with explicit input, output, return and enable posts. */
final class LinearRegulatorElm extends AbstractRailRegulatorElm {
    LinearRegulatorElm(int x, int y) {
        this(x, y, RailRegulationContract.linear5V());
    }

    LinearRegulatorElm(int x, int y, RailRegulationContract contract) {
        super(x, y, requireLinear(contract));
    }

    LinearRegulatorElm(int x, int y, int x2, int y2, int flags,
            StringTokenizer st) {
        this(x, y, x2, y2, flags,
                AbstractRailRegulatorElm.readDumpContract(st, false));
    }

    private LinearRegulatorElm(int x, int y, int x2, int y2, int flags,
            RailRegulationContract contract) {
        super(x, y, x2, y2, flags, requireLinear(contract));
    }

    int getDumpType() { return 454; }

    double modelInputCurrent(double inputVoltage, double outputVoltage,
            double deliveredOutputCurrent, double enableFraction) {
        // A linear pass element draws approximately the delivered current plus
        // declared control/quiescent current.  The resulting input-output
        // difference is the solver-observable dissipation.
        return deliveredOutputCurrent +
                contractQuiescentCurrent() * enableFraction;
    }

    private double contractQuiescentCurrent() {
        return getContract().getQuiescentCurrentAmps();
    }

    private static RailRegulationContract requireLinear(RailRegulationContract contract) {
        if (contract == null || !contract.isLinear())
            throw new IllegalArgumentException("Linear regulator requires a linear rail contract");
        return contract;
    }
}
