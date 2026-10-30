package com.lushprojects.circuitjs1.client;

/** Bounded authored reference, shared by native and compiled actual-solver gates. */
final class Rb100PilotReference {
    static final long SEED = 9007199254740993L;
    static final int MAX_WORK = 640, BATCH_STEPS = 64;
    static final long MAX_CASE_MILLIS = 90000, MAX_UNIT_MILLIS = 5000;
    static final long OPERATION_LIMIT_MILLIS = 5000, TRIAL_BOUNDARY_LIMIT_MILLIS = 500;
    static final String[] CONTEXTS = {"LOW", "HIGH", "AC_ONLY", "OVERLOAD", "RECOVERED", "LOW_AGAIN"};
    interface Stepper { void analyze(); void advance(int steps); }
    final Rb100ElectricalPilot.Candidate candidate;
    final Rb100PilotFaults.Fault fault;
    final double[][] healthyObservations = new double[6][];
    private final CirSim sim;
    private final Stepper stepper;
    private final GeneratedFaultEffect effect;
    private final double dt;
    private final long started;
    private final StringBuilder observations = new StringBuilder("[");
    private boolean repair, analyze = true, complete, witness;
    private int context, remaining, work = 1, sampleCount;
    private int acceptedSteps, stepBatches, maxBatchAcceptedSteps;
    private boolean fixedStepVerified;
    private long maxUnitMillis;
    private double maxStepDelta;

    Rb100PilotReference(CirSim sim, Rb100ElectricalPilot.Candidate candidate,
            Rb100PilotFaults.Fault fault, GeneratedFaultEffect effect, Stepper stepper,
            double dt, long started) {
        if (sim == null || candidate == null || stepper == null ||
                (fault == null) != (effect == null) || (dt != .0001 && dt != .00005))
            throw new IllegalArgumentException("Incomplete RB100 reference operation");
        this.sim = sim; this.candidate = candidate; this.fault = fault;
        this.effect = effect; this.stepper = stepper; this.dt = dt; this.started = started;
        require(OPERATION_LIMIT_MILLIS == 5000 && TRIAL_BOUNDARY_LIMIT_MILLIS == 500,
            "frozen temporal reference limits");
        require(CircuitSolverExecutor.TEMPORAL_WALL_LIMIT_MS == OPERATION_LIMIT_MILLIS &&
                CircuitSolverExecutor.WALL_LIMIT_MS == TRIAL_BOUNDARY_LIMIT_MILLIS,
            "solver temporal limits match reference protocol");
        enforceFixedStep();
        maxUnitMillis = System.currentTimeMillis() - started;
        require(maxUnitMillis <= MAX_UNIT_MILLIS, "construction unit budget");
        if (effect != null) effect.setApplied(true);
        configure();
    }
    /** Exactly one bounded analysis or one verified temporal batch of at most 64 steps. */
    boolean advance() {
        require(!complete, "reference already complete");
        long unitStart = System.currentTimeMillis();
        require(++work <= MAX_WORK, "reference work budget");
        if (analyze) {
            enforceFixedStep();
            fixedStepVerified = false;
            stepper.analyze();
            enforceFixedStep();
            analyze = false;
            sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
            fixedStepVerified = true;
        } else {
            int count = Math.min(BATCH_STEPS, remaining);
            require(count >= 1 && count <= BATCH_STEPS, "bounded temporal batch size");
            enforceFixedStep();
            fixedStepVerified = false;
            int acceptedBefore = sim.timeStepCount;
            double before = sim.t;
            stepper.advance(count);
            enforceFixedStep();
            int batchAcceptedSteps = sim.timeStepCount - acceptedBefore;
            require(batchAcceptedSteps == count, "exact accepted solver step count");
            double expected = count * dt, actual = sim.t - before;
            require(Math.abs(actual - expected) < 1e-8, "actual accepted step/time correspondence");
            maxStepDelta = Math.max(maxStepDelta, Math.abs(actual - expected));
            acceptedSteps += batchAcceptedSteps;
            stepBatches++;
            maxBatchAcceptedSteps = Math.max(maxBatchAcceptedSteps, batchAcceptedSteps);
            remaining -= count;
            fixedStepVerified = true;
            if (remaining == 0) {
                sample();
                context++;
                if (context == CONTEXTS.length) {
                    if (fault != null && !repair) {
                        require(witness, "fault witness was retained");
                        effect.setApplied(false); repair = true; context = 0;
                    } else complete = true;
                }
                if (!complete) configure();
            }
        }
        long now = System.currentTimeMillis();
        maxUnitMillis = Math.max(maxUnitMillis, now - unitStart);
        require(now >= started && now - started <= MAX_CASE_MILLIS, "reference wall budget");
        require(now - unitStart <= MAX_UNIT_MILLIS, "reference unit budget");
        return complete;
    }
    private void configure() {
        boolean all = context == 1 || context == 3 || context == 4;
        for (String channel : new String[] {"A", "B", "C", "D"}) {
            boolean high = all || (context == 2 && ("A".equals(channel) || "C".equals(channel)));
            candidate.sensorSources.get(channel).configure(high ? 5 : 0, .25);
        }
        candidate.loads.get("E").configure(context == 3 ? 100 : 180);
        candidate.loads.get("F").configure(context == 3 ? 100 : 180);
        remaining = (int)Math.round((context == 0 ? .4 : .04) / dt);
        analyze = true;
    }
    private void enforceFixedStep() {
        require(fixedStepSettingsMatch(), "fixed timestep configuration");
    }
    private boolean fixedStepSettingsMatch() {
        return sim.timeStep == dt && sim.maxTimeStep == dt && sim.minTimeStep == dt &&
            !sim.adjustTimeStep;
    }
    private void sample() {
        require(sim.stopMessage == null && sim.circuitMatrix != null, "live solved matrix");
        double[] values = values();
        for (double value : values) require(LowVoltageSourceModel.finite(value), "finite electrical observation");
        boolean healthy = fault == null || repair;
        if (sampleCount++ > 0) observations.append(',');
        observations.append("{\"phase\":\"").append(healthy ? (repair ? "REPAIRED" : "HEALTHY") : "FAULT")
            .append("\",\"context\":\"").append(CONTEXTS[context]).append("\",\"time\":").append(sim.t)
            .append(",\"values\":[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) observations.append(','); observations.append(values[i]);
        }
        observations.append("]}");
        if (healthy) {
            checkHealthy(values);
            healthyObservations[context] = new double[values.length];
            System.arraycopy(values, 0, healthyObservations[context], 0, values.length);
        }
        else {
            if ("DE_SHUNT".equals(fault.id) || "DF_SHUNT".equals(fault.id) ||
                    "CSTORE_E_SHUNT".equals(fault.id) || "CSTORE_F_SHUNT".equals(fault.id)) {
                range(values[0], 10, 12.5, "control 12 V rail during load-bus short");
                range(values[1], 4.65, 5.25, "control 5 V rail during load-bus short");
                range(values[2], 3.05, 3.5, "monitor 3.3 V rail during load-bus short");
            }
            if (context == (fault.overload ? 3 : 1)) {
                double observed = observation(fault.witness, values);
                require(fault.above ? observed > fault.threshold : observed < fault.threshold,
                    "independent fault witness " + fault.id + " " + fault.witness + "=" + observed);
                witness = true;
            }
        }
    }
    // Independent sampling at the actual pad endpoints. No scenario-generated readings.
    private double[] values() {
        return new double[] {net("RAIL12"), net("RAIL5"), net("RAIL3V3"),
            output("A"), output("B"), output("C"), output("D"), output("E"), output("F"),
            net("MON_SENSE"), net("MON_FILTER"), net("MON_REF"), net("MON_CMD"), net("AUX_GATE")};
    }
    private double output(String channel) {
        Rb100Plan.Part part = candidate.plan.part("JO" + channel);
        String[] ids = part.terminalIds();
        return pad(part.id + "." + ids[0]) - pad(part.id + "." + ids[1]);
    }
    private double net(String net) { return absoluteNet(net) - absoluteNet("CTRL_RETURN"); }
    private double absoluteNet(String net) {
        for (Rb100Plan.Part part : candidate.plan.parts()) {
            String[] nets = part.netIds(), terminals = part.terminalIds();
            for (int i = 0; i < nets.length; i++) if (net.equals(nets[i])) return pad(part.id + "." + terminals[i]);
        }
        throw new IllegalArgumentException("Missing reference net: " + net);
    }
    private double pad(String id) {
        CircuitMeasurementEndpoint raw = candidate.board().getSimulationBindings().getEndpoint(id);
        require(raw instanceof CircuitPostMeasurementEndpoint, "actual mapped endpoint " + id);
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)raw;
        require(sim.elmList.contains(endpoint.getElement()), "endpoint belongs to installed private graph");
        return endpoint.getElement().getPostVoltage(endpoint.getPostIndex());
    }
    private void checkHealthy(double[] v) {
        range(v[0], 10, 12.5, "converted 12 V rail");
        range(v[1], 4.65, 5.25, "5 V control rail");
        range(v[2], 3.05, 3.5, "3.3 V monitor rail");
        for (int i = 0; i < 6; i++) {
            boolean on = context == 1 || context == 3 || context == 4 ||
                (context == 2 && (i == 0 || i == 2 || i == 4));
            if (context == 3 && (i == 2 || i == 3)) on = false;
            require(on ? v[3 + i] > 9 : Math.abs(v[3 + i]) < .5,
                "healthy " + CONTEXTS[context] + " output " + i + "=" + v[3 + i]);
        }
        require(context == 3 ? v[12] > 2 : Math.abs(v[12]) < .5, "powered monitor state");
        require(context == 3 ? Math.abs(v[13]) < .5 : v[13] > 3.5, "auxiliary return gate");
        require(Math.abs(v[10] - v[9]) < .01, "settled 1 ms monitor filter");
        range(v[11], .14, .19, "actual monitor reference");
        for (BoundedExternalLoadElm load : candidate.loads.values()) require(!load.hasFailed(), "external load remains in model rating");
    }
    private static double observation(String id, double[] v) {
        if ("RAIL12".equals(id)) return v[0];
        if ("RAIL5".equals(id)) return v[1];
        if ("RAIL3V3".equals(id)) return v[2];
        if ("AUX_GATE".equals(id)) return v[13];
        if (id.startsWith("OUT_") && id.length() == 5) return v[3 + id.charAt(4) - 'A'];
        throw new IllegalArgumentException("Unknown reference observation");
    }
    private static void range(double value, double low, double high, String label) {
        require(value >= low && value <= high, label + "=" + value);
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("RB100 reference: " + message);
    }
    String report() {
        require(complete, "incomplete reference cannot report PASS");
        return render("PASS_ELECTRICAL_REFERENCE_ONLY");
    }
    String diagnosticReport() {
        String base = render("INCOMPLETE_ELECTRICAL_REFERENCE");
        StringBuilder parts = new StringBuilder("[");
        for (String id : new String[] {"UAC", "U1", "U33", "UMON", "QINV", "QAUX", "CIN33"}) {
            CircuitElm element = candidate.backing.get(id);
            if (parts.length() > 1) parts.append(',');
            parts.append("{\"id\":\"").append(id).append("\",\"postVoltages\":[");
            for (int post = 0; post < element.getPostCount(); post++) {
                if (post > 0) parts.append(',');
                double value = element.getPostVoltage(post);
                parts.append(LowVoltageSourceModel.finite(value) ? Double.toString(value) : "null");
            }
            parts.append("]}");
        }
        parts.append(']');
        return base.substring(0, base.length() - 1) +
            ",\"componentDiagnosticState\":\"UNACCEPTED_TRIAL_OR_LAST_ACCEPTED\",\"componentDiagnostics\":" + parts + "}";
    }
    private String render(String status) {
        return "{\"protocol\":\"q100-electrical-reference-v3\",\"status\":\"" + status + "\",\"seed\":\"" + candidate.plan.seed +
            "\",\"variant\":\"" + candidate.plan.variant.name() + "\",\"planVersion\":1,\"modelVersion\":" + Rb100Plan.MODEL_VERSION + ",\"faultPopulationVersion\":1," +
            "\"fault\":\"" + (fault == null ? "HEALTHY" : fault.id) + "\",\"dt\":" + dt +
            ",\"advanceMode\":\"TEMPORAL_BATCH\",\"operationLimitMs\":" + OPERATION_LIMIT_MILLIS +
            ",\"trialBoundaryLimitMs\":" + TRIAL_BOUNDARY_LIMIT_MILLIS +
            ",\"maxBatchSteps\":" + BATCH_STEPS + ",\"fixedStepVerified\":" + fixedStepVerified +
            ",\"acceptedSteps\":" + acceptedSteps + ",\"stepBatches\":" + stepBatches +
            ",\"maxBatchAcceptedSteps\":" + maxBatchAcceptedSteps +
            ",\"packages\":100,\"solverElements\":" + sim.elmList.size() +
            ",\"simulatedSeconds\":" + sim.t +
            ",\"matrixFull\":" + sim.circuitMatrixFullSize + ",\"matrixReduced\":" + sim.circuitMatrixSize +
            ",\"work\":" + work + ",\"wallMs\":" + (System.currentTimeMillis() - started) +
            ",\"maxUnitMs\":" + maxUnitMillis + ",\"maxStepTimeError\":" + maxStepDelta +
            ",\"sampleNames\":[\"RAIL12\",\"RAIL5\",\"RAIL3V3\",\"OUT_A\",\"OUT_B\",\"OUT_C\",\"OUT_D\",\"OUT_E\",\"OUT_F\",\"MON_SENSE\",\"MON_FILTER\",\"MON_REF\",\"MON_CMD\",\"AUX_GATE\"]," +
            "\"observations\":" + observations.toString() + "]}";
    }
}
