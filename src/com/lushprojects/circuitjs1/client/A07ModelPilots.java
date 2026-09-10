package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Small solver-only probes. They do not qualify playable provider families. */
final class A07ModelPilots {
    private static int assertions;
    static String run(CirSim sim) {
        assertions = 0;
        StringBuilder rows = new StringBuilder("[");
        double[] relay = new double[2], transformer = new double[2], converter = new double[2];
        for (int pass = 0; pass < 2; pass++) {
            double dt = pass == 0 ? .0001 : .00005;
            relay[pass] = relay(sim, dt, rows);
            transformer[pass] = transformer(sim, dt, rows);
            diode(sim, pass == 0 ? .000005 : .0000025, rows);
            converter[pass] = converter(sim, pass == 0 ? .000005 : .0000025, rows);
        }
        require(Math.abs(relay[0] - relay[1]) < .0001, "relay timestep sensitivity");
        require(Math.abs(transformer[0] - transformer[1]) < .02, "transformer timestep sensitivity");
        require(Math.abs(converter[0] - converter[1]) < .25, "converter timestep sensitivity");
        double baseline = reference(sim, 0, rows);
        require(Math.abs(baseline - reference(sim, 1, rows)) < 1e-6,
            "moving the numerical reference preserves differential voltage");
        require(Math.abs(baseline - reference(sim, 2, rows)) < 1e-6,
            "automatic numerical reference preserves differential voltage");
        rows.append("]");
        return "{\"assertions\":" + assertions + ",\"rows\":" + rows +
            ",\"physicalPackages\":null,\"playableQualification\":false," +
            "\"limits\":[\"synthetic raw circuits; no PCB packages or diagnostic game\"," +
            "\"relay position model is current-based, not mechanical bounce\"," +
            "\"transformer is linear coupled inductance, without saturation or mains qualification\"," +
            "\"converter uses a declared finite-resistance switch, diode and LC; no control loop or thermal model\"]}";
    }
    private static void configure(CirSim sim, double dt) {
        sim.timeStep = sim.maxTimeStep = dt; sim.minTimeStep = dt;
        sim.adjustTimeStep = false;
        sim.a01MeasurementRunning = true;
        sim.a01AnalysisCount = sim.a01StampCount = sim.a01FactorizationCount = 0;
        sim.a01SolveCount = sim.a01IterationCount = sim.a01SubIterationCount = sim.a01AcceptedStepCount = 0;
    }
    private static <T extends CircuitElm> T end(T element, int x, int y) {
        element.x2 = x; element.y2 = y; element.setPoints(); return element;
    }
    private static Point at(CircuitElm element, int post) { return element.getPost(post); }
    private static void wire(Vector<CircuitElm> elements, Point a, Point b) {
        if (a.x != b.x || a.y != b.y) elements.add(end(new WireElm(a.x, a.y), b.x, b.y));
    }
    private static void ground(Vector<CircuitElm> elements, Point a) {
        elements.add(end(new GroundElm(a.x, a.y), a.x, a.y + 32));
    }
    private static ResistorElm resistor(Point a, Point b, double ohms) {
        ResistorElm result = end(new ResistorElm(a.x, a.y), b.x, b.y);
        result.setResistance(ohms); return result;
    }
    private static DCVoltageElm dc(Point negative, Point positive, double volts) {
        DCVoltageElm result = end(new DCVoltageElm(negative.x, negative.y), positive.x, positive.y);
        result.maxVoltage = volts; result.bias = 0; return result;
    }
    private static void addRow(StringBuilder rows, CirSim sim, String model, double dt,
            double value, double expected, double tolerance, long started) {
        if (rows.length() > 1) rows.append(",");
        require(SolverExecutionBoundary.finite(value), "finite " + model + " observation");
        rows.append("{\"model\":\"").append(model).append("\",\"timeStep\":").append(dt)
            .append(",\"value\":").append(value).append(",\"reference\":").append(expected)
            .append(",\"tolerance\":").append(tolerance).append(",\"simulatedSeconds\":").append(sim.t)
            .append(",\"wallMs\":").append(System.currentTimeMillis() - started)
            .append(",\"solverElements\":").append(sim.elmList.size())
            .append(",\"matrixFull\":").append(sim.circuitMatrixFullSize)
            .append(",\"matrixReduced\":").append(sim.circuitMatrixSize)
            .append(",\"acceptedSteps\":").append(sim.a01AcceptedStepCount)
            .append(",\"nonlinearTrials\":").append(sim.a01SubIterationCount)
            .append(",\"analyses\":").append(sim.a01AnalysisCount)
            .append(",\"restamps\":").append(sim.a01StampCount)
            .append(",\"factorizations\":").append(sim.a01FactorizationCount)
            .append(",\"solves\":").append(sim.a01SolveCount).append("}");
    }
    private static void require(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError("A07 model pilot: " + message);
    }
    private static double relay(CirSim sim, double dt, StringBuilder rows) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long start = System.currentTimeMillis();
        try {
            configure(sim, dt);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            RelayElm relay = end(new RelayElm(256, 160), 352, 160); elements.add(relay);
            DCVoltageElm coil = dc(new Point(64, 320), new Point(64, 240), 0); elements.add(coil);
            DCVoltageElm supply = dc(new Point(64, 96), new Point(64, 32), 5); elements.add(supply);
            wire(elements, at(coil, 1), at(relay, 3)); ground(elements, at(coil, 0));
            ground(elements, at(relay, 4)); ground(elements, at(supply, 0));
            wire(elements, at(supply, 1), at(relay, 0));
            ResistorElm offLoad = resistor(at(relay, 1), new Point(448, 128), 1000);
            ResistorElm onLoad = resistor(at(relay, 2), new Point(448, 224), 1000);
            elements.add(offLoad); elements.add(onLoad);
            ground(elements, at(offLoad, 1)); ground(elements, at(onLoad, 1));
            proof.install(elements); proof.analyze(); proof.advanceFor(.001);
            require(relay.i_position == 0 && offLoad.getVoltageDiff() > 4.9 && onLoad.getVoltageDiff() < .01,
                "unenergized relay contacts follow coil state");
            coil.maxVoltage = 1; proof.analyze(); proof.advanceFor(.05);
            double expected = .05 * (1 - Math.exp(-.05 / .01));
            double current = relay.coilCurrent;
            require(Math.abs(current - expected) < .0001, "relay RL startup envelope");
            require(relay.i_position == 1 && onLoad.getVoltageDiff() > 4.9 && offLoad.getVoltageDiff() < .01,
                "energized relay drives actual contact load");
            addRow(rows, sim, "relay-energize", dt, current, expected, .0001, start);
            coil.maxVoltage = 0; proof.analyze(); proof.advanceFor(.08);
            require(Math.abs(relay.coilCurrent) < .0001 && relay.i_position == 0 &&
                offLoad.getVoltageDiff() > 4.9 && onLoad.getVoltageDiff() < .01,
                "relay release follows stored coil current decay");
            addRow(rows, sim, "relay-release", dt, relay.coilCurrent, 0, .0001, start);
            return current;
        } finally { sim.a01MeasurementRunning = false; proof.close(); }
    }
    private static double transformer(CirSim sim, double dt, StringBuilder rows) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long start = System.currentTimeMillis();
        try {
            configure(sim, dt);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            TransformerElm transformer = new TransformerElm(256, 256);
            transformer.drag(384, 320); transformer.ratio = .5; elements.add(transformer);
            VoltageElm source = end(new VoltageElm(64, 320, VoltageElm.WF_AC), 64, 128);
            source.frequency = 50; source.maxVoltage = 5; source.bias = 0; elements.add(source);
            wire(elements, at(source, 1), at(transformer, 0));
            wire(elements, at(source, 0), at(transformer, 2)); ground(elements, at(source, 0));
            ResistorElm load = resistor(at(transformer, 1), at(transformer, 3), 1000); elements.add(load);
            proof.install(elements); proof.analyze(); proof.advanceFor(.04);
            double ratio = rmsRatio(proof, transformer, dt);
            require(Math.abs(ratio - .5) < .02, "linear coupled transformer loaded RMS turns ratio");
            addRow(rows, sim, "transformer-loaded", dt, ratio, .5, .02, start);
            load.setResistance(500); proof.analyze(); proof.advanceFor(.04);
            double loaded = rmsRatio(proof, transformer, dt);
            require(Math.abs(loaded - .5) < .02, "transformer load-step differential RMS");
            addRow(rows, sim, "transformer-load-step", dt, loaded, .5, .02, start);
            return ratio;
        } finally { sim.a01MeasurementRunning = false; proof.close(); }
    }
    private static double rmsRatio(PrivateSolverContext proof, TransformerElm transformer, double dt) {
        double primary = 0, secondary = 0;
        int count = (int)Math.round(.02 / dt);
        for (int step = 0; step < count; step++) {
            proof.advanceSteps(1);
            double vp = transformer.getPostVoltage(0) - transformer.getPostVoltage(2);
            double vs = transformer.getPostVoltage(1) - transformer.getPostVoltage(3);
            primary += vp * vp; secondary += vs * vs;
        }
        require(primary / count > 10, "transformer primary was actually energized");
        return Math.sqrt(secondary / primary);
    }
    private static void diode(CirSim sim, double dt, StringBuilder rows) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long start = System.currentTimeMillis();
        try {
            configure(sim, dt);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            DCVoltageElm source = dc(new Point(64, 256), new Point(64, 128), 5); elements.add(source);
            ResistorElm resistor = resistor(new Point(64, 128), new Point(256, 128), 1000); elements.add(resistor);
            DiodeElm diode = end(new DiodeElm(256, 128), 256, 256); elements.add(diode);
            wire(elements, at(diode, 1), at(source, 0)); ground(elements, at(source, 0));
            proof.install(elements); proof.analyze(); proof.advanceSteps(4);
            double voltage = diode.getVoltageDiff(), current = resistor.getCurrent();
            require(voltage > .3 && voltage < 1 && current > .004 && current < .0047,
                "forward diode is a nonlinear conducting model");
            require(Math.abs(5 - voltage - 1000 * current) < 1e-7, "independent diode-loop KVL");
            require(Math.abs(current - diode.getCurrent()) < 1e-7, "independent diode-loop KCL");
            addRow(rows, sim, "diode-forward", dt, voltage, .65, .35, start);
            source.maxVoltage = -5; proof.analyze(); proof.advanceSteps(4);
            require(Math.abs(resistor.getCurrent()) < .000005 && diode.getVoltageDiff() < -4.99,
                "reverse diode is not a symmetric resistor");
            addRow(rows, sim, "diode-reverse-current", dt, resistor.getCurrent(), 0, .000005, start);
        } finally { sim.a01MeasurementRunning = false; proof.close(); }
    }
    private static double converter(CirSim sim, double dt, StringBuilder rows) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long start = System.currentTimeMillis();
        try {
            configure(sim, dt);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            DCVoltageElm input = dc(new Point(64, 320), new Point(64, 128), 12); elements.add(input);
            AnalogSwitchElm switchElm = end(new AnalogSwitchElm(256, 128), 384, 128);
            switchElm.r_on = .05; switchElm.r_off = 1e10; elements.add(switchElm);
            InductorElm inductor = end(new InductorElm(384, 128), 512, 128);
            inductor.inductance = .01; inductor.flags = Inductor.FLAG_BACK_EULER;
            inductor.ind.setup(inductor.inductance, 0, inductor.flags); elements.add(inductor);
            CapacitorElm capacitor = end(new CapacitorElm(512, 128), 512, 320);
            capacitor.capacitance = .00022; capacitor.initialVoltage = 0;
            capacitor.flags = CapacitorElm.FLAG_BACK_EULER; capacitor.reset(); elements.add(capacitor);
            ResistorElm load = resistor(new Point(608, 128), new Point(608, 320), 20); elements.add(load);
            DiodeElm flywheel = end(new DiodeElm(384, 320), 384, 128); elements.add(flywheel);
            VoltageElm pwm = end(new VoltageElm(96, 448, VoltageElm.WF_SQUARE), 96, 368);
            pwm.frequency = 2000; pwm.maxVoltage = 2.5; pwm.bias = 2.5; pwm.dutyCycle = .5; elements.add(pwm);
            wire(elements, at(input, 1), at(switchElm, 0)); wire(elements, at(pwm, 1), at(switchElm, 2));
            wire(elements, at(capacitor, 0), at(load, 0));
            ground(elements, at(input, 0)); ground(elements, at(pwm, 0));
            ground(elements, at(capacitor, 1)); ground(elements, at(load, 1)); ground(elements, at(flywheel, 0));
            proof.install(elements); proof.analyze(); proof.advanceFor(.04);
            double[] initial = converterWindow(proof, load, dt);
            require(initial[0] > 4.8 && initial[0] < 6.5 && initial[2] - initial[1] < .5,
                "open-loop buck reaches the declared diode-adjusted duty envelope with bounded ripple");
            addRow(rows, sim, "converter-20ohm-mean", dt, initial[0], 5.65, .85, start);
            addRow(rows, sim, "converter-20ohm-ripple", dt, initial[2] - initial[1], 0, .5, start);
            load.setResistance(10); proof.analyze(); proof.advanceFor(.03);
            double[] loaded = converterWindow(proof, load, dt);
            require(loaded[0] > 4.8 && loaded[0] < 6.5 && loaded[2] - loaded[1] < .5,
                "open-loop converter remains bounded after its actual load step");
            require(inductor.getCurrent() > .35 && inductor.getCurrent() < .85,
                "loaded converter uses real inductor current");
            addRow(rows, sim, "converter-10ohm-mean", dt, loaded[0], 5.65, .85, start);
            return initial[0];
        } finally { sim.a01MeasurementRunning = false; proof.close(); }
    }
    private static double[] converterWindow(PrivateSolverContext proof, ResistorElm load, double dt) {
        int count = (int)Math.round(.01 / dt);
        double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            proof.advanceSteps(1); double value = load.getVoltageDiff();
            sum += value; min = Math.min(min, value); max = Math.max(max, value);
        }
        return new double[] {sum / count, min, max};
    }
    private static double reference(CirSim sim, int selectedReference, StringBuilder rows) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long start = System.currentTimeMillis();
        try {
            configure(sim, .000005);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            DCVoltageElm source = dc(new Point(64, 320), new Point(64, 128), 5); elements.add(source);
            ResistorElm first = resistor(at(source, 1), new Point(256, 128), 1000); elements.add(first);
            ResistorElm second = resistor(new Point(256, 128), at(source, 0), 1000); elements.add(second);
            if (selectedReference < 2)
                ground(elements, selectedReference == 0 ? at(source, 0) : at(second, 0));
            proof.install(elements); proof.analyze(); proof.advanceSteps(3);
            double differential = second.getVoltageDiff();
            require(Math.abs(differential - 2.5) < 1e-6 && Math.abs(second.getCurrent() - .0025) < 1e-8,
                "numerical reference " + selectedReference + " preserves the independent divider equations");
            addRow(rows, sim, "reference-" + selectedReference, .000005, differential, 2.5, 1e-6, start);
            return differential;
        } finally { sim.a01MeasurementRunning = false; proof.close(); }
    }
}
