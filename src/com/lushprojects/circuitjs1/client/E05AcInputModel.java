package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed simulated sine input. No current limiting, physical mains or certification claim. */
final class E05AcInputModel {
    static final double RMS_VOLTS = 120.0;
    static final double PEAK_VOLTS = RMS_VOLTS * Math.sqrt(2.0);
    static final double FREQUENCY_HZ = 60.0;
    static final double SERIES_OHMS = 22.0;
    static final double MAX_TIME_STEP_SECONDS = .00005;
    static final double FUSE_OHMS = .1;
    static final double FUSE_I2T_AMP_SQUARED_SECONDS = .1;

    private E05AcInputModel() { }

    static void requireTimeStep(double seconds) {
        if (!PowerDomainContract.finite(seconds) || seconds <= 0 ||
                seconds > MAX_TIME_STEP_SECONDS)
            throw new IllegalArgumentException("E05 requires an accepted timestep in (0, 50 us]");
    }

    /** Both poles belong to the one existing external power binding/control. */
    static Input create(int x, int y, Point line, Point returned) {
        if (line == null || returned == null ||
                (line.x == returned.x && line.y == returned.y))
            throw new IllegalArgumentException("Distinct AC input terminals required");
        VoltageElm source = end(new VoltageElm(x, y + 160, VoltageElm.WF_AC), x, y);
        source.frequency = FREQUENCY_HZ;
        source.maxVoltage = PEAK_VOLTS; // CircuitJS uses sine PEAK, never RMS.
        source.bias = source.phaseShift = source.freqTimeZero = 0;
        ResistorElm impedance = end(new ResistorElm(x, y), x + 64, y);
        impedance.setResistance(SERIES_OHMS);
        SwitchElm linePole = end(new SwitchElm(x + 64, y), line.x, line.y);
        SwitchElm returnPole = end(new SwitchElm(x, y + 160), returned.x, returned.y);
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(source); elements.add(impedance);
        elements.add(linePole); elements.add(returnPole);
        SwitchExternalPowerControl control = new SwitchExternalPowerControl(
            new SwitchElm[] {linePole, returnPole});
        return new Input(source, impedance, linePole, returnPole, elements,
            new ExternalPowerSimulationBinding(elements, control));
    }

    /** Rating is an explicit bounded simulator parameter, not a commercial fuse selection. */
    static ProtectionFuseElm fuse(Point a, Point b) {
        ProtectionFuseElm result = end(new ProtectionFuseElm(a.x, a.y), b.x, b.y);
        result.resistance = FUSE_OHMS;
        result.i2t = FUSE_I2T_AMP_SQUARED_SECONDS;
        return result;
    }

    private static <T extends CircuitElm> T end(T element, int x, int y) {
        element.x2 = x; element.y2 = y; element.setPoints(); return element;
    }

    static final class Input {
        final VoltageElm source;
        final ResistorElm impedance;
        final SwitchElm linePole, returnPole;
        final Vector<CircuitElm> elements;
        final ExternalPowerSimulationBinding binding;
        Input(VoltageElm source, ResistorElm impedance, SwitchElm linePole,
                SwitchElm returnPole, Vector<CircuitElm> elements,
                ExternalPowerSimulationBinding binding) {
            this.source = source; this.impedance = impedance;
            this.linePole = linePole; this.returnPole = returnPole;
            this.elements = new Vector<CircuitElm>(elements);
            this.binding = binding;
        }
    }
}
