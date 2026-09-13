package com.lushprojects.circuitjs1.client;

/** Low-voltage sacrificial fuse; only replacement creates a healthy new owner. */
final class ProtectionFuseElm extends FuseElm {
    ProtectionFuseElm(int x, int y) {
        super(x, y);
        resistance = .1;
        i2t = .0002;
    }
    ProtectionFuseElm(int x, int y, int x2, int y2, int flags, StringTokenizer st) {
        super(x, y, x2, y2, flags, st);
        if (!LowVoltageSourceModel.finite(resistance) || resistance <= 0 ||
                !LowVoltageSourceModel.finite(i2t) || i2t <= 0 ||
                !LowVoltageSourceModel.finite(heat) || heat < 0)
            throw new IllegalArgumentException("Unsupported protection fuse");
    }
    int getDumpType() { return 451; }
    // Newton retries and rejected trial steps must not accumulate irreversible damage.
    void startIteration() { }
    void stepFinished() {
        if (blown || sim.dcAnalysisFlag) return;
        heat = Math.max(0, heat + (current * current - i2t / 3) * sim.timeStep);
        if (heat >= i2t) blown = true;
    }
    void reset() {
        // Resetting solver time or cycling power is not replacing this physical part.
        current = curcount = 0;
    }
}
