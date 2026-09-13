package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent off-grid electrical fixtures for the production meter stimuli. */
final class MeasurementEndpointDeveloperVerifier {
    private int assertions;
    private final StringBuilder cases = new StringBuilder();
    private long operationMs, cleanupMs;

    static void verify(CirSim sim) {
        if (!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("Measurement endpoint proof requires a developer route");
        MeasurementEndpointDeveloperVerifier test = new MeasurementEndpointDeveloperVerifier();
        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        try {
            for (int direction = 0; direction < 2; direction++) {
                test.resistance(sim, direction, 0);
                test.resistance(sim, direction, 680);
                test.resistance(sim, direction, Double.POSITIVE_INFINITY);
                test.diode(sim, direction);
                test.voltage(sim, direction);
            }
            snapshot.assertRestored(sim);
            test.publish("PASS", true, null);
        } catch (Throwable failure) {
            boolean restored = false;
            try { snapshot.assertRestored(sim); restored = true; }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            test.publish("FAIL", restored, failure.toString());
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            throw new IllegalStateException(failure);
        }
    }

    private void resistance(CirSim sim, int direction, double expected) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long started = System.currentTimeMillis();
        try {
            Vector<CircuitElm> graph = new Vector<CircuitElm>();
            CircuitElm first, second;
            if (Double.isInfinite(expected)) {
                first = exact(new WireElm(101, 203), 137, 203);
                second = exact(new WireElm(357, 419), 389, 419);
                graph.add(first); graph.add(second);
            } else {
                first = expected == 0 ? new WireElm(101, 203) : new ResistorElm(101, 203);
                exact(first, 357, 419);
                if (first instanceof ResistorElm) ((ResistorElm)first).setResistance(expected);
                second = first; graph.add(first);
            }
            CircuitPostMeasurementEndpoint a = new CircuitPostMeasurementEndpoint(first, 0);
            CircuitPostMeasurementEndpoint b = new CircuitPostMeasurementEndpoint(second,
                Double.isInfinite(expected) ? 0 : 1);
            ResistanceMeasurementStimulus stimulus = new ResistanceMeasurementStimulus(sim,
                direction == 0 ? a : b, direction == 0 ? b : a);
            add(graph, stimulus); proof.install(graph); proof.analyze(); proof.advanceSteps(4);
            double current = Math.abs(stimulus.getTestCurrent());
            require(finite(current), "finite off-grid resistance current");
            if (Double.isInfinite(expected)) require(current < 1e-7, "open path remains OL");
            else require(Math.abs(current - 1.0 / (1000 + expected)) < 1e-9,
                "off-grid wire/resistor current follows the independent series circuit");
            row("resistance", direction, Double.isInfinite(expected) ? "open" : Double.toString(expected), current);
        } finally { finish(proof, started); }
    }

    private void diode(CirSim sim, int direction) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long started = System.currentTimeMillis();
        try {
            DiodeElm diode = new DiodeElm(101, 203);
            exact(diode, 357, 419); diode.modelName = "1N4148"; diode.setup();
            CircuitPostMeasurementEndpoint a = new CircuitPostMeasurementEndpoint(diode, 0);
            CircuitPostMeasurementEndpoint b = new CircuitPostMeasurementEndpoint(diode, 1);
            DiodeTestStimulus stimulus = new DiodeTestStimulus(sim,
                direction == 0 ? a : b, direction == 0 ? b : a);
            Vector<CircuitElm> graph = new Vector<CircuitElm>(); graph.add(diode); add(graph, stimulus);
            proof.install(graph); proof.analyze(); proof.advanceSteps(8);
            DiodeMeasurementResult result = stimulus.getResult();
            require(finite(result.current) && finite(result.voltage), "finite off-grid diode sample");
            if (direction == 0) require(result.voltage > .4 && result.voltage < 1 &&
                result.current > .002 && result.current < .003, "real forward diode conducts");
            else require(result.current < 1e-7 && result.voltage > 2.9,
                "reversed diode remains nonconductive; polarity is not normalized");
            row("diode", direction, "1N4148", result.voltage);
        } finally { finish(proof, started); }
    }

    private void voltage(CirSim sim, int direction) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        long started = System.currentTimeMillis();
        try {
            DCVoltageElm source = new DCVoltageElm(101, 203);
            exact(source, 229, 203); source.maxVoltage = 5;
            GroundElm ground = new GroundElm(101, 203); exact(ground, 101, 235);
            ResistorElm series = new ResistorElm(229, 203);
            exact(series, 357, 419); series.setResistance(10000000);
            CircuitPostMeasurementEndpoint a = new CircuitPostMeasurementEndpoint(series, 1);
            CircuitPostMeasurementEndpoint b = new CircuitPostMeasurementEndpoint(source, 0);
            DcVoltageMeasurementStimulus stimulus = new DcVoltageMeasurementStimulus(
                direction == 0 ? a : b, direction == 0 ? b : a);
            Vector<CircuitElm> graph = new Vector<CircuitElm>();
            graph.add(source); graph.add(ground); graph.add(series); add(graph, stimulus);
            proof.install(graph); proof.analyze(); proof.advanceSteps(4);
            double measured = stimulus.getVoltage(), expected = direction == 0 ? 2.5 : -2.5;
            require(finite(measured) && Math.abs(measured - expected) < .001,
                "10 Mohm meter loads the off-grid 10 Mohm source in both directions");
            require(Math.abs(Math.abs(series.getCurrent()) - .00000025) < 1e-10,
                "loaded voltage has the independently expected physical current");
            row("dc", direction, "5V/10Mohm", measured);
        } finally { finish(proof, started); }
    }

    private void finish(PrivateSolverContext proof, long started) {
        long cleanupStarted = System.currentTimeMillis();
        operationMs += cleanupStarted - started;
        try { proof.close(); } finally { cleanupMs += System.currentTimeMillis() - cleanupStarted; }
    }
    private static CircuitElm exact(CircuitElm element, int x, int y) {
        element.x2 = x; element.y2 = y; element.setPoints(); return element;
    }
    private static void add(Vector<CircuitElm> graph, ActiveMeasurementStimulus stimulus) {
        for (CircuitElm element : stimulus.getTemporaryElements()) graph.add(element);
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private void require(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError("Measurement endpoint: " + message);
    }
    private void row(String mode, int direction, String fixture, double result) {
        if (cases.length() > 0) cases.append(',');
        cases.append("{\"mode\":").append(quote(mode)).append(",\"direction\":").append(direction)
            .append(",\"fixture\":").append(quote(fixture)).append(",\"result\":").append(result).append('}');
    }
    private void publish(String status, boolean restored, String failure) {
        publishReport("{\"protocol\":\"TSJ-MEASUREMENT-ENDPOINTS-1\",\"status\":" + quote(status) +
            ",\"cases\":[" + cases + "],\"assertions\":" + assertions + ",\"operationMs\":" + operationMs +
            ",\"cleanupMs\":" + cleanupMs + ",\"ownerRestored\":" + restored + ",\"failure\":" + quote(failure) + "}");
    }
    private static native String quote(String value) /*-{ return JSON.stringify(value); }-*/;
    private static native void publishReport(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-measurement-endpoints-report", report);
    }-*/;
}
