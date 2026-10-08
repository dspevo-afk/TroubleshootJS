package com.lushprojects.circuitjs1.client;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Actual RB56 raw graph under its finite adaptive recipe; no installed service or player-flow claim. */
public final class Rb56AssemblyContractTest {
    private static final double DT = Rb30Behavior.RB56_MAX_STEP_SECONDS, STARTUP = .4, CONDITION = .030, SAMPLE = .010;
    private static final double MAIN_OFF = 2.5, ALL_OFF = 3, RESIDUAL = .25, GMIN_MAX = 1.0001e-12;
    private static int assertions, bjtChannels, nmosChannels;
    private static final Field DECISION_STATE = decisionStateField();

    public static void main(String[] args) {
        PrintStream original = System.err;
        RoutineConvergenceStream routine = new RoutineConvergenceStream(original);
        try {
            System.setErr(routine);
            check(DT == .000400 && DT == E06ConverterContract.MAX_AVERAGED_STEP_SECONDS,
                "raw assembly uses the declared RB56 400us averaged-model recipe cap");
            System.out.println("RB56_ASSEMBLY_CRITERIA {\"sensorLowCommandV\":0,\"sensorHighCommandV\":5,\"maximumStepSeconds\":" + number(DT) + ",\"minimumStepSeconds\":0.00000000005,\"adaptive\":true,\"startupSeconds\":0.4,\"conditionSeconds\":0.03," +
                "\"sampleSeconds\":0.01,\"mainOnlyOffSeconds\":2.5,\"allOffSeconds\":3,\"rail12Mean\":[10.64,11.76]," +
                "\"rail12RippleMax\":0.05,\"rail12DriftMax\":0.02,\"rail5\":[4.75,5.25],\"contactOn\":[10.8,12.6]," +
                "\"contactOffMax\":0.05,\"residualMax\":0.25,\"optoGminMax\":1.0001e-12,\"recipe\":\"RB56_ADAPTIVE_400US_MIN_50PS\",\"maximumStepOwner\":\"Rb30Behavior.RB56_MAX_STEP_SECONDS\",\"scope\":\"ACTUAL_ASSEMBLED_GRAPH_ONLY\"}");
            runCase(Rb56Plan.configured(17, 1, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, true, false, 1, 0, false), 40, false);
            runCase(Rb56Plan.reference(77), 55, true);
            runCase(Rb56Plan.configured(42, 2, Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC, true, true, 3, 3, true), 60, false);
            check(bjtChannels > 0 && nmosChannels > 0, "fixed representatives exercise both actual low-side providers");
            System.out.println("PASS: RB56 assembly contracts assertions=" + assertions + " cases=3 BJT=" + bjtChannels +
                " NMOS=" + nmosChannels + " scope=ACTUAL_ASSEMBLED_GRAPH_ONLY");
        } finally { System.setErr(original); routine.report(); }
    }

    private static void runCase(Rb56Plan plan, int packages, boolean enableCanary) {
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName, priorZener = ZenerElm.lastZenerModelName, priorTransistor = TransistorElm.lastModelName;
        int priorMosFlags = MosfetElm.globalFlags; double priorBeta = MosfetElm.lastBeta;
        TestCirSim sim = new TestCirSim();
        Vector<CircuitElm> original = sim.elmList, owned = new Vector<CircuitElm>();
        RelayOutputGenerator.Assembly a = null;
        Rb56PowerStageAssembly power = null;
        Rb56ControlTail.Result tail = null;
        Result result = new Result(plan, packages);
        Throwable failure = null; AcceptedObserver observer = null;
        CircuitElm.sim = sim;
        try {
            a = new RelayOutputGenerator.Assembly(plan.board());
            for (String net : a.board.getNetIds()) a.net(net, a.board.getNet(net).getRoutingRole());
            power = new Rb56PowerStageAssembly(a, plan);
            tail = Rb56ControlTail.build(a, plan);
            a.requireCompleteManifest();
            GroundElm ground = end(new GroundElm(power.stage.input.source.getPost(0).x,
                power.stage.input.source.getPost(0).y), new Point(-256, 20288));
            a.elements.add(ground); owned.addAll(a.elements);
            structural(a, plan, power, tail, packages);
            sim.elmList = a.elements; sim.a01MeasurementRunning = true;
            sim.solverExecutor.analyze(); sim.analyzeFlag = false;
            nodeBindings(a, power);
            observer = new AcceptedObserver(sim, power.stage, tail, result);
            observer.arm();
            if (enableCanary) {
                result.phase = "EN_LEAD_OPEN";
                check(a.elements.remove(power.stage.enableLead), "remove the actual adopted EN lead");
                reanalyzePreservingStorage(sim);
                result.targetTime = .15;
                advanceAccepted(sim, result, result.targetTime);
                double maximum = result.enableOpenMaxRail12;
                check(maximum <= RESIDUAL && diff(power.stage.bulk, 0, 1) >= 95 && diff(power.stage.module.bias, 2, 1) > 3,
                    "actual EN opening blocks startup despite charged input and powered bias");
                a.elements.add(power.stage.enableLead); reanalyzePreservingStorage(sim);
                check(a.elements.contains(power.stage.enableLead), "reconnect the exact original EN lead without replacement/reset");
                a.connections.validateAgainst(a.board, a.elements, a.components, a.power, null);
            }
            result.phase = "STARTUP_LOW";
            setInputs(tail, plan, 0); reanalyzePreservingStorage(sim);
            Window startup = advanceWindow(sim, power.stage, tail, result, STARTUP, .05);
            startup.validateRails(); startup.validateInputs(0, tail); startup.validateContacts(0, tail, plan); result.windows.add(startup.report("STARTUP_LOW"));
            for (int mask = 0; mask < (1 << plan.channelCount); mask++) {
                result.phase = "SENSOR_MASK_" + mask;
                setInputs(tail, plan, mask); reanalyzePreservingStorage(sim);
                Window window = advanceWindow(sim, power.stage, tail, result, CONDITION, SAMPLE);
                window.validateRails(); window.validateInputs(mask, tail); window.validateContacts(mask, tail, plan);
                result.windows.add(window.report(result.phase));
            }
            result.phase = "MAINAC_ONLY_OFF";
            a.power.getBinding("MAINAC").setConnected(false);
            check(power.stage.input.linePole.position == 1 && power.stage.input.returnPole.position == 1,
                "MAINAC isolation opens both actual source poles");
            for (String input : a.board.getPowerInputIds()) if (!"MAINAC".equals(input))
                check(a.power.getBinding(input).isConnected(), "external sensors/load remain physically powered during MAINAC-only OFF");
            reanalyzePreservingStorage(sim);
            Window mainOff = advanceWindow(sim, power.stage, tail, result, MAIN_OFF, SAMPLE);
            result.mainOffRail12 = result.last.rail12; result.mainOffRail5 = result.last.rail5;
            result.windows.add(mainOff.report("MAINAC_ONLY_OFF"));
            for (int n = 0; n < plan.channelCount; n++) check(result.last.raws[n] >= tail.configuration.sensorHighVolts &&
                result.last.controlStates[n] == E04SensorControlModel.ControlState.BROWNOUT,
                "actual HIGH sensor remains present while the unpowered decision is in brownout");
            check(mainOff.rail12.absoluteMax() <= RESIDUAL && mainOff.rail5.absoluteMax() <= RESIDUAL,
                "decayed main power cannot be replaced by external sensor/load backfeed");
            mainOff.validateContacts(0, tail, plan);
            for (String channel : plan.channels()) {
                check(tail.sensorSources.get(channel).maxVoltage == Rb56Plan.SENSOR_HIGH_VOLTS,
                    "MAINAC-only OFF leaves actual external sensor command HIGH");
                check(tail.relays.get(channel).i_position == 0 && Math.abs(tail.relays.get(channel).coilCurrent) < RelayOutputBehavior.DISCHARGED_AMPS,
                    "actual relay releases after primary power decay despite powered external inputs");
            }
            result.phase = "ALL_INPUTS_OFF";
            a.power.setConnected(false); check(a.power.areAllDisconnected(), "all declared external source controls are OFF");
            reanalyzePreservingStorage(sim);
            Window allOff = advanceWindow(sim, power.stage, tail, result, ALL_OFF, SAMPLE);
            result.windows.add(allOff.report("ALL_INPUTS_OFF"));
            result.finalResidual = maximumResidual(a, plan, power.stage);
            result.finalTime = sim.t; result.acceptedSteps = sim.a01AcceptedStepCount;
            result.finalBulk = diff(power.stage.bulk, 0, 1); result.finalInductor = power.stage.outputInductor.getCurrent();
            check(result.finalResidual <= RESIDUAL && allOff.rail12.absoluteMax() <= RESIDUAL && allOff.rail5.absoluteMax() <= RESIDUAL,
                "actual all-inputs OFF tail discharges every capacitor and declared rail within unchanged voltage bound");
            check(sim.stopMessage == null && !sim.solverExecutor.isUnavailable() && result.maxOptoGmin <= GMIN_MAX,
                "production solver owner remains available with no elevated opto gmin");
            double expected = (enableCanary ? .15 : 0) + STARTUP + CONDITION * (1 << plan.channelCount) + MAIN_OFF + ALL_OFF;
            check(Math.abs(result.targetTime - expected) < 1e-12 &&
                sim.t + 1e-12 >= expected && sim.t - expected <= DT + 1e-12 &&
                result.sampledAcceptedSteps == result.acceptedSteps,
                "every actual accepted step is observed through the full declared physical timeline");
            System.out.println("RB56_ASSEMBLY " + result.report("PASS"));
        } catch (RuntimeException error) { failure = error; result.finalTime = sim.t; result.acceptedSteps = sim.a01AcceptedStepCount; System.out.println("RB56_ASSEMBLY " + result.report("FAIL")); throw error;
        } catch (Error error) { failure = error; result.finalTime = sim.t; result.acceptedSteps = sim.a01AcceptedStepCount; System.out.println("RB56_ASSEMBLY " + result.report("FAIL")); throw error;
        } finally {
            Throwable cleanup = null; boolean disposed = true;
            try {
                if (observer != null) observer.cancel();
                sim.a01MeasurementRunning = false; sim.elmList = original;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; } catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null;
                if (a != null) for (CircuitElm e : a.elements) if (!owned.contains(e)) owned.add(e);
                for (CircuitElm e : owned) try { e.delete(); }
                catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = priorDiode; ZenerElm.lastZenerModelName = priorZener; TransistorElm.lastModelName = priorTransistor;
                MosfetElm.globalFlags = priorMosFlags; MosfetElm.lastBeta = priorBeta; CircuitElm.sim = prior; CirSim.theSim = priorSingleton;
            }
            boolean observerCancelled = observer == null || observer.isCancelled();
            boolean clean = observerCancelled && cleanup == null && disposed && sim.elmList == original && original.isEmpty() && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == priorSingleton;
            System.out.println("RB56_ASSEMBLY_CLEANUP {\"packages\":" + packages + ",\"rawGraphRestored\":" +
                (sim.elmList == original && original.isEmpty()) + ",\"acceptedObserverCancelled\":" + observerCancelled + ",\"candidateDisposed\":" + disposed + ",\"singletonRestored\":" +
                (CircuitElm.sim == prior && CirSim.theSim == priorSingleton) + ",\"clean\":" + clean + "}");
            if (failure != null) { if (cleanup != null) failure.addSuppressed(cleanup); if (!clean) failure.addSuppressed(new AssertionError("RB56 assembly cleanup failed")); }
            else { if (cleanup != null) throw new AssertionError("RB56 assembly cleanup failed", cleanup); check(clean, "exact raw graph/global cleanup"); }
        }
    }

    private static void structural(RelayOutputGenerator.Assembly a, Rb56Plan plan, Rb56PowerStageAssembly p, Rb56ControlTail.Result tail, int count) {
        check(plan.physicalPackageCount() == count && a.board.getComponentIds().size() == count && plan.powerParts().size() == 20,
            "actual declared/reference package census");
        check(a.board.getPowerInputIds().size() == plan.channelCount + 2 && a.power.hasControlsForAllInputs(), "exact controlled source census");
        IdentityHashMap<CircuitElm, Boolean> unique = new IdentityHashMap<CircuitElm, Boolean>(); int sources = 0, grounds = 0;
        for (CircuitElm e : a.elements) {
            check(unique.put(e, Boolean.TRUE) == null, "adoption contains each actual element once");
            if (e instanceof VoltageElm) sources++;
            if (e instanceof GroundElm) grounds++;
        }
        check(sources == plan.channelCount + 2 && grounds == 1 && !a.board.getPowerInputIds().contains("MAIN12"),
            "only declared external sources and one primary numerical ground exist");
        check(a.power.getBinding("MAINAC") == p.stage.input.binding && p.stage.input.impedance.resistance == 22,
            "one original AC owner retains actual external 22 ohm impedance");
        a.connections.validateAgainst(a.board, a.elements, a.components, a.power, null);
        for (Rb56Plan.Part part : plan.parts()) if (part.kind != Rb56Plan.Kind.SOURCE && !"JAC".equals(part.id)) {
            check(a.components.hasComponentBinding(part.id), "declared function has actual owned component backing");
            check(a.connections.getForComponent(part.id).size() == part.physicalPackage.getTerminalCount(), "all physical pins have declared detachable connections");
        }
        for (Rb56Plan.Part part : plan.powerParts()) if (!"JAC".equals(part.id) && !"UAC".equals(part.id)) {
            String[] terminals = part.terminalIds();
            for (int n = 0; n < terminals.length; n++) {
                GeneratedComponentConnectionBinding connection = a.connections.get(part.id, part.id + "." + terminals[n]);
                CircuitPostMeasurementEndpoint board = endpoint(connection.getBoardEndpoint()), component = endpoint(connection.getComponentEndpoint());
                boolean reverse = n == 1 && terminals.length == 2 &&
                    ("RESISTOR".equals(part.type) || "CAPACITOR".equals(part.type) || "DIODE".equals(part.type));
                CircuitElm lead = connection.getConnectionElement();
                Point boardPoint = board.getElement().getPost(board.getPostIndex()), componentPoint = component.getElement().getPost(component.getPostIndex());
                check(lead.getPost(reverse ? 1 : 0).equals(boardPoint) && lead.getPost(reverse ? 0 : 1).equals(componentPoint),
                    "actual power lead orientation follows its declared terminal: " + part.id + "." + terminals[n]);
            }
        }
        for (String channel : plan.channels()) {
            if (plan.driver(channel) instanceof RelayDriverProvider.Bjt) bjtChannels++; else nmosChannels++;
            check(tail.decisions.containsKey(channel) && tail.relays.containsKey(channel) && tail.loads.containsKey(channel), "every declared channel owns its real decision/relay/load");
        }
        for (int n = 0; n < 7; n++) {
            String pad = "UAC." + p.stage.module.terminalId(n);
            GeneratedComponentConnectionBinding b = a.connections.get("UAC", pad);
            CircuitPostMeasurementEndpoint board = endpoint(b.getBoardEndpoint()), component = endpoint(b.getComponentEndpoint());
            check(b.getConnectionElement() == p.stage.moduleLeads[n] && board.getElement() != b.getConnectionElement() &&
                GeneratedComponentConnectionBindings.sameEndpoint(component, p.stage.module.terminal(n)), "one persistent anchor and original actual lead bind each module terminal");
        }
        check(p.stage.module.converter.x == 32768 && p.stage.module.converter.y == 52768, "module original service island remains unchanged");
        for (Rb56Plan.Part part : plan.powerParts()) if (!"JAC".equals(part.id) && !"UAC".equals(part.id))
            for (CircuitElm e : p.backing(part.id)) check(e.x >= 100000 && e.y >= 99000, "whole physical backing moved off stationary copper");
        check(p.stage.outputInductor.getPost(1).equals(p.stage.windingResistance.getPost(0)), "inductor/winding-resistance shared internal post moves intact");
        check(p.stage.outputCap.getPost(0).equals(p.stage.capacitorEsr.getPost(1)), "capacitor/ESR shared internal post moves intact");
        check(endpoint(a.connections.get("L1", "L1.2").getComponentEndpoint()).getElement() == p.stage.windingResistance &&
            endpoint(a.connections.get("COUT", "COUT.+").getComponentEndpoint()).getElement() == p.stage.capacitorEsr,
            "outer L/C physical ports include their actual series losses");
    }
    private static void nodeBindings(RelayOutputGenerator.Assembly a, Rb56PowerStageAssembly p) {
        TreeMap<String, Integer> nodes = new TreeMap<String, Integer>();
        for (String id : a.board.getPadIds()) {
            CircuitPostMeasurementEndpoint e = endpoint(a.board.getSimulationBindings().getEndpoint(id));
            String net = a.board.getPad(id).getNetId(); int node = e.getElement().nodes[e.getPostIndex()];
            Integer prior = nodes.get(net); check(prior == null || prior.intValue() == node, "all actual pad endpoints of a named net agree: " + net);
            nodes.put(net, node);
        }
        check(new HashSet<Integer>(nodes.values()).size() == nodes.size(), "different declared nets were not silently merged by adoption");
        check(!connected(a.elements, node(a, "UAC.IN-"), node(a, "UAC.OUT-")), "actual model graph retains primary/secondary isolation without a copper bond");
        check(p.stage.outputInductor.nodes[1] == p.stage.windingResistance.nodes[0] && p.stage.outputCap.nodes[0] == p.stage.capacitorEsr.nodes[1],
            "series helper junctions remain actual solver nodes within their physical bundles");
    }
    private static boolean connected(Vector<CircuitElm> elements, int from, int to) {
        HashSet<Integer> seen = new HashSet<Integer>(); Vector<Integer> queue = new Vector<Integer>(); seen.add(from); queue.add(from);
        for (int q = 0; q < queue.size(); q++) {
            int at = queue.get(q); if (at == to) return true;
            for (CircuitElm e : elements) for (int i = 0; i < e.getPostCount(); i++) if (e.nodes[i] == at)
                for (int j = 0; j < e.getPostCount(); j++) if (i != j && e.getConnection(i, j) && seen.add(e.nodes[j])) queue.add(e.nodes[j]);
        }
        return false;
    }
    private static int node(RelayOutputGenerator.Assembly a, String pad) { CircuitPostMeasurementEndpoint e = endpoint(a.board.getSimulationBindings().getEndpoint(pad)); return e.getElement().nodes[e.getPostIndex()]; }
    private static void setInputs(Rb56ControlTail.Result tail, Rb56Plan plan, int mask) {
        for (int n = 0; n < plan.channelCount; n++) tail.sensorSources.get(plan.channels()[n]).configure(
            (mask & (1 << n)) == 0 ? Rb56Plan.SENSOR_LOW_VOLTS : Rb56Plan.SENSOR_HIGH_VOLTS, .25);
    }
    private static void reanalyzePreservingStorage(TestCirSim sim) {
        IdentityHashMap<CapacitorElm, Double> history = new IdentityHashMap<CapacitorElm, Double>();
        for (CircuitElm e : sim.elmList) if (e instanceof CapacitorElm) history.put((CapacitorElm)e, ((CapacitorElm)e).voltdiff);
        sim.solverExecutor.invalidate(); sim.solverExecutor.analyze(); sim.analyzeFlag = false;
        for (Map.Entry<CapacitorElm, Double> e : history.entrySet()) check(e.getKey().voltdiff == e.getValue().doubleValue(), "reanalyze retains actual accepted capacitor history");
    }
    private static Window advanceWindow(TestCirSim sim, Rb56PowerStage stage, Rb56ControlTail.Result tail, Result result, double duration, double sample) {
        result.targetTime += duration;
        Window window = new Window(result.targetTime - sample, result.targetTime, tail.plan.channelCount); result.current = window;
        Window sensitivity = sample == SAMPLE ? window : new Window(result.targetTime - SAMPLE, result.targetTime, tail.plan.channelCount);
        result.sensitivityCurrent = sensitivity;
        window.actualPhaseStart = sensitivity.actualPhaseStart = sim.t;
        advanceAccepted(sim, result, result.targetTime);
        window.actualPhaseEnd = sensitivity.actualPhaseEnd = sim.t;
        check(sim.t + 1e-12 >= result.targetTime && sim.t - result.targetTime <= DT + 1e-12,
            "actual adaptive phase end is within the declared maximum-step delay");
        window.validateCoverage(); sensitivity.validateCoverage();
        result.sensitivityWindows.add(sensitivity.report(result.phase));
        return window;
    }
    private static void advanceAccepted(TestCirSim sim, Result result, double target) {
        result.receipts.print(sim, result, "PHASE_START", target);
        try {
            while (sim.t + 1e-12 < target) {
                sim.solverExecutor.advanceFor(Math.min(.030, target - sim.t));
            }
            result.receipts.print(sim, result, "PHASE_END", target);
        } catch (RuntimeException error) {
            result.failedTrials(sim); result.receipts.print(sim, result, "PHASE_FAILURE", target); throw error;
        } catch (Error error) {
            result.failedTrials(sim); result.receipts.print(sim, result, "PHASE_FAILURE", target); throw error;
        }
    }
    /** Compact native phase receipts outside solver batches; never changes graph or solver state. */
    private static final class PhaseReceipts {
        final long startedNanos = System.nanoTime();
        double lastAcceptedTime;
        long lastNanos = startedNanos, lastAccepted, lastTrials;
        void print(TestCirSim sim, Result result, String receipt, double target) {
            long now = System.nanoTime(), accepted = sim.a01AcceptedStepCount, trials = sim.a01SubIterationCount;
            System.out.println("RB56_ASSEMBLY_PHASE {\"receipt\":\"" + receipt + "\",\"packages\":" + result.packages +
                ",\"seed\":\"" + result.plan.seed + "\",\"phase\":\"" + result.phase + "\",\"acceptedTimeSeconds\":" + number(sim.t) +
                ",\"phaseTargetSeconds\":" + number(target) + ",\"elapsedMs\":" + number((now - startedNanos) / 1e6) +
                ",\"sincePriorReceiptMs\":" + number((now - lastNanos) / 1e6) +
                ",\"sincePriorReceiptAcceptedSeconds\":" + number(sim.t - lastAcceptedTime) +
                ",\"reducedMatrixSize\":" + sim.circuitMatrixSize + ",\"elementCount\":" + sim.elmList.size() +
                ",\"acceptedSteps\":" + accepted + ",\"acceptedDelta\":" + (accepted - lastAccepted) +
                ",\"attemptedNewtonTrials\":" + trials + ",\"newtonDelta\":" + (trials - lastTrials) +
                ",\"sampledAcceptedSteps\":" + result.sampledAcceptedSteps +
                ",\"discardedTimestepAttemptTrials\":" + result.discardedAttemptTrials +
                ",\"failedUnacceptedAttemptTrials\":" + result.failedAttemptTrials +
                ",\"minimumAcceptedDtSeconds\":" + number(result.minimumAcceptedDt) +
                ",\"maximumAcceptedDtSeconds\":" + number(result.maximumAcceptedDt) +
                ",\"maxIterations\":" + result.maxIterations + ",\"maxOptoGmin\":" + number(result.maxOptoGmin) +
                ",\"rail12V\":" + number(result.last == null ? Double.NaN : result.last.rail12) +
                ",\"rail5V\":" + number(result.last == null ? Double.NaN : result.last.rail5) +
                ",\"bulkV\":" + number(result.last == null ? Double.NaN : result.last.bulk) + "}");
            lastNanos = now; lastAcceptedTime = sim.t; lastAccepted = accepted; lastTrials = trials;
        }
    }
    /** Existing accepted-event seam; no element, stamp, timestep, or graph mutation. */
    private static final class AcceptedObserver implements SolverEventQueue.Action {
        final TestCirSim sim; final Rb56PowerStage stage; final Rb56ControlTail.Result tail; final Result result;
        final SolverEventQueue queue; SolverEventQueue.Event pending; boolean closed;
        double priorTime; long priorAccepted;
        AcceptedObserver(TestCirSim sim, Rb56PowerStage stage, Rb56ControlTail.Result tail, Result result) {
            this.sim = sim; this.stage = stage; this.tail = tail; this.result = result;
            queue = sim.solverExecutor.events(null);
            priorTime = sim.t; priorAccepted = sim.a01AcceptedStepCount; result.trialCursor = sim.a01SubIterationCount;
        }
        void arm() { pending = queue.schedule(Math.nextUp(sim.t), this); }
        public void fire(double scheduledTime, double acceptedTime) {
            check(!closed && acceptedTime == sim.t && sim.a01AcceptedStepCount == priorAccepted + 1,
                "one actual after-stepFinished observation per accepted CircuitJS step");
            double before = priorTime, dt = acceptedTime - before;
            long trials = sim.a01SubIterationCount - result.trialCursor, finalSolveTrials = sim.subIterations + 1L;
            result.attemptedTrials += trials; result.acceptedSolveTrials += finalSolveTrials;
            result.discardedAttemptTrials += trials - finalSolveTrials;
            result.trialCursor = sim.a01SubIterationCount; priorTime = acceptedTime; priorAccepted = sim.a01AcceptedStepCount;
            result.sampledAcceptedSteps++;
            result.minimumAcceptedDt = Math.min(result.minimumAcceptedDt, dt);
            result.maximumAcceptedDt = Math.max(result.maximumAcceptedDt, dt);
            check(finite(dt) && dt > 0 && dt <= DT + 1e-12 && trials >= finalSolveTrials,
                "actual adaptive accepted time/trial accounting is finite and bounded");
            observe(sim, stage, tail, result);
            if ("EN_LEAD_OPEN".equals(result.phase)) {
                if (!finite(result.enableOpenMaxRail12)) result.enableOpenMaxRail12 = 0;
                result.enableOpenMaxRail12 = Math.max(result.enableOpenMaxRail12, Math.abs(result.last.rail12));
                check(!stage.module.converter.isActive(), "open actual EN suppresses module activation");
            }
            if (result.current != null && acceptedTime > result.current.start && before < result.current.end)
                result.current.add(result.last, before, acceptedTime);
            if (result.sensitivityCurrent != null && result.sensitivityCurrent != result.current &&
                    acceptedTime > result.sensitivityCurrent.start && before < result.sensitivityCurrent.end)
                result.sensitivityCurrent.add(result.last, before, acceptedTime);
            if ("MAINAC_ONLY_OFF".equals(result.phase)) for (int n = 0; n < result.last.channels.length; n++)
                if (result.last.controlStates[n] == E04SensorControlModel.ControlState.BROWNOUT &&
                    !finite(result.firstOffBrownout[n])) result.firstOffBrownout[n] = acceptedTime;
            pending = queue.schedule(Math.nextUp(acceptedTime), this);
        }
        void cancel() { closed = true; if (pending != null) pending.cancel(); }
        boolean isCancelled() { return closed && (pending == null || pending.cancelled); }
    }
    private static void observe(TestCirSim sim, Rb56PowerStage stage, Rb56ControlTail.Result tail, Result result) {
        if (result.last == null) result.last = new Snapshot(stage, tail);
        else result.last.update(stage, tail);
        result.maxIterations = Math.max(result.maxIterations, sim.subIterations);
        result.maxOptoGmin = Math.max(result.maxOptoGmin, stage.opto.transistor.gmin);
        result.minimumBiasLoss = Math.min(result.minimumBiasLoss, result.last.biasLoss);
        check(finite(result.last.rail12) && finite(result.last.rail5) && finite(result.last.biasInputW) && finite(result.last.biasOutputW) && finite(result.last.moduleInputW) &&
            finite(result.last.preLDeliveredW) && result.last.biasLoss >= -1e-9, "finite actual scalar powers and unchanged bias passivity");
        check(finite(stage.opto.transistor.gmin) && stage.opto.transistor.gmin <= GMIN_MAX, "no accepted elevated opto gmin");
    }
    private static double maximumResidual(RelayOutputGenerator.Assembly a, Rb56Plan plan, Rb56PowerStage stage) {
        double maximum = 0;
        for (CircuitElm e : a.elements) if (e instanceof CapacitorElm) maximum = Math.max(maximum, Math.abs(diff(e, 0, 1)));
        for (Rb56Plan.Part part : plan.parts()) {
            String[] nets = part.netIds(), ids = part.terminalIds();
            for (int n = 0; n < ids.length; n++) {
                String reference = part.kind == Rb56Plan.Kind.POWER && !("CTRL_RETURN".equals(nets[n]) || "RAIL12".equals(nets[n]) ||
                    "PRE_L".equals(nets[n]) || "SENSE_ZENER".equals(nets[n]) || "SENSE_LED".equals(nets[n])) ? "UAC.IN-" :
                    ("LOAD12".equals(nets[n]) || "LOAD_RETURN".equals(nets[n]) || nets[n].startsWith("OUT_") || nets[n].startsWith("NC_") || nets[n].startsWith("LED_OUT_")) ?
                        "JLOAD.2" : "UAC.OUT-";
                maximum = Math.max(maximum, Math.abs(padVoltage(a, part.id + "." + ids[n]) - padVoltage(a, reference)));
            }
        }
        return maximum;
    }
    private static double padVoltage(RelayOutputGenerator.Assembly a, String pad) { CircuitPostMeasurementEndpoint e = endpoint(a.board.getSimulationBindings().getEndpoint(pad)); return e.getElement().getPostVoltage(e.getPostIndex()); }
    private static CircuitPostMeasurementEndpoint endpoint(CircuitMeasurementEndpoint e) { check(e instanceof CircuitPostMeasurementEndpoint, "actual post endpoint"); return (CircuitPostMeasurementEndpoint)e; }
    private static double diff(CircuitElm e, int a, int b) { return e.getPostVoltage(a) - e.getPostVoltage(b); }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static String number(double v) { return finite(v) ? Double.toString(v) : "null"; }
    private static void check(boolean pass, String label) { assertions++; if (!pass) throw new AssertionError("RB56 assembly: " + label); }
    private static <T extends CircuitElm> T end(T e, Point at) { e.x2 = at.x; e.y2 = at.y; e.setPoints(); return e; }

    /** Native-only diagnostic read of the actual model enum; never changes model state. */
    private static Field decisionStateField() {
        try {
            Field field = E04SensorControlModel.DecisionElement.class.getDeclaredField("controlState");
            field.setAccessible(true); return field;
        } catch (ReflectiveOperationException error) { throw new AssertionError("Actual E04 state unavailable", error); }
    }
    private static E04SensorControlModel.ControlState decisionState(E04SensorControlModel.DecisionElement decision) {
        try { return (E04SensorControlModel.ControlState) DECISION_STATE.get(decision); }
        catch (IllegalAccessException error) { throw new AssertionError("Actual E04 state inaccessible", error); }
    }
    private static final class Snapshot {
        double rail12, rail5, bulk, biasInputW, biasOutputW, biasLoss, moduleInputW, preLDeliveredW;
        final double[] contacts, coils, sourceCommands, sources, raws, senses, references, decisionOutputs;
        final String[] channels; final int[] positions; final E04SensorControlModel.ControlState[] controlStates;
        final ServiceRelayElm[] liveRelays; final LimitedDcSupplyElm[] liveSources;
        final E04SensorControlModel.DecisionElement[] liveDecisions;
        final CircuitElm[] liveLoads, liveRawResistors; CircuitElm liveRegulator;
        private Snapshot(String[] channelIds) {
            channels = channelIds; contacts = new double[channels.length]; coils = new double[channels.length];
            positions = new int[channels.length]; sourceCommands = new double[channels.length]; sources = new double[channels.length];
            raws = new double[channels.length]; senses = new double[channels.length]; references = new double[channels.length]; decisionOutputs = new double[channels.length];
            controlStates = new E04SensorControlModel.ControlState[channels.length];
            liveRelays = new ServiceRelayElm[channels.length]; liveSources = new LimitedDcSupplyElm[channels.length];
            liveDecisions = new E04SensorControlModel.DecisionElement[channels.length];
            liveLoads = new CircuitElm[channels.length]; liveRawResistors = new CircuitElm[channels.length];
        }
        Snapshot(Rb56PowerStage s, Rb56ControlTail.Result tail) {
            this(tail.plan.channels());
            liveRegulator = tail.regulator;
            for (int n = 0; n < channels.length; n++) {
                String channel = channels[n];
                liveRelays[n] = tail.relays.get(channel); liveSources[n] = tail.sensorSources.get(channel);
                liveDecisions[n] = tail.decisions.get(channel); liveLoads[n] = tail.loads.get(channel);
                liveRawResistors[n] = tail.backing.get("RS" + channel);
            }
            update(s, tail);
        }
        Snapshot(Snapshot source) {
            this(source.channels.clone()); copyFrom(source);
        }
        void copyFrom(Snapshot source) {
            rail12 = source.rail12; rail5 = source.rail5; bulk = source.bulk;
            biasInputW = source.biasInputW; biasOutputW = source.biasOutputW; biasLoss = source.biasLoss;
            moduleInputW = source.moduleInputW; preLDeliveredW = source.preLDeliveredW;
            System.arraycopy(source.contacts, 0, contacts, 0, contacts.length);
            System.arraycopy(source.coils, 0, coils, 0, coils.length);
            System.arraycopy(source.positions, 0, positions, 0, positions.length);
            System.arraycopy(source.sourceCommands, 0, sourceCommands, 0, sourceCommands.length);
            System.arraycopy(source.sources, 0, sources, 0, sources.length);
            System.arraycopy(source.raws, 0, raws, 0, raws.length);
            System.arraycopy(source.senses, 0, senses, 0, senses.length);
            System.arraycopy(source.references, 0, references, 0, references.length);
            System.arraycopy(source.decisionOutputs, 0, decisionOutputs, 0, decisionOutputs.length);
            System.arraycopy(source.controlStates, 0, controlStates, 0, controlStates.length);
        }
        void update(Rb56PowerStage s, Rb56ControlTail.Result tail) {
            bulk = diff(s.bulk, 0, 1);
            double returned = s.outputCap.getPostVoltage(1);
            rail12 = s.capacitorEsr.getPostVoltage(0) - returned;
            rail5 = diff(liveRegulator, 1, 2);
            biasInputW = diff(s.module.bias, 0, 1) * s.module.bias.getInputCurrent();
            biasOutputW = diff(s.module.bias, 2, 1) * s.module.bias.getOutputCurrent(); biasLoss = biasInputW - biasOutputW;
            moduleInputW = s.module.converter.getInputVoltage() * s.module.converter.getInputCurrent();
            preLDeliveredW = s.module.converter.getOutputVoltage() * s.module.converter.getCurrentIntoNode(2);
            for (int n = 0; n < channels.length; n++) {
                ServiceRelayElm relay = liveRelays[n]; LimitedDcSupplyElm source = liveSources[n];
                E04SensorControlModel.DecisionElement decision = liveDecisions[n];
                contacts[n] = diff(liveLoads[n], 0, 1); coils[n] = relay.coilCurrent; positions[n] = relay.i_position;
                sourceCommands[n] = source.maxVoltage; sources[n] = source.getOutputVoltage();
                raws[n] = liveRawResistors[n].getPostVoltage(0) - decision.getPostVoltage(4);
                senses[n] = diff(decision, 0, 4); references[n] = diff(decision, 1, 4); decisionOutputs[n] = diff(decision, 3, 4);
                controlStates[n] = decisionState(decision);
            }
        }
        String report() { StringBuilder b = new StringBuilder("{\"rail12V\":" + number(rail12) + ",\"rail5V\":" + number(rail5) + ",\"bulkV\":" + number(bulk) +
            ",\"biasInputW\":" + number(biasInputW) + ",\"biasOutputW\":" + number(biasOutputW) + ",\"biasLossW\":" + number(biasLoss) +
            ",\"moduleInputW\":" + number(moduleInputW) + ",\"preLDeliveredW\":" + number(preLDeliveredW) + ",\"contactsV\":[");
            for (int n = 0; n < contacts.length; n++) { if (n > 0) b.append(','); b.append(number(contacts[n])); }
            b.append("],\"coilA\":["); for (int n = 0; n < coils.length; n++) { if (n > 0) b.append(','); b.append(number(coils[n])); }
            b.append("],\"positions\":["); for (int n = 0; n < positions.length; n++) { if (n > 0) b.append(','); b.append(positions[n]); }
            b.append("],\"channels\":[");
            for (int n = 0; n < channels.length; n++) {
                if (n > 0) b.append(',');
                b.append("{\"id\":\"").append(channels[n]).append("\",\"sourceCommandV\":").append(number(sourceCommands[n])).append(",\"sourceActualV\":").append(number(sources[n]))
                    .append(",\"rawV\":").append(number(raws[n])).append(",\"senseV\":").append(number(senses[n])).append(",\"referenceV\":").append(number(references[n]))
                    .append(",\"decisionOutputV\":").append(number(decisionOutputs[n])).append(",\"controlState\":\"").append(controlStates[n].name()).append("\"}");
            }
            return b.append("]}").toString(); }
    }
    private static final class Samples {
        int count; double sum, first, second, duration, firstDuration, secondDuration;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        void add(double value, double weight, double firstWeight) {
            count++; sum += value * weight; duration += weight;
            first += value * firstWeight; firstDuration += firstWeight;
            second += value * (weight - firstWeight); secondDuration += weight - firstWeight;
            min = Math.min(min, value); max = Math.max(max, value);
        }
        double mean() { return sum / duration; }
        double absoluteMax() { return Math.max(Math.abs(min), Math.abs(max)); }
        double ripple() { return (max - min) / Math.abs(mean()); }
        double drift() { return Math.abs(first / firstDuration - second / secondDuration) / Math.abs(mean()); }
        String report() { return "{\"count\":" + count + ",\"coveredSeconds\":" + number(duration) +
            ",\"firstHalfSeconds\":" + number(firstDuration) + ",\"secondHalfSeconds\":" + number(secondDuration) +
            ",\"mean\":" + number(mean()) + ",\"min\":" + number(min) + ",\"max\":" + number(max) +
            ",\"ripple\":" + number(ripple()) + ",\"drift\":" + number(drift()) + "}"; }
    }
    private static final class Window {
        final Samples rail12 = new Samples(), rail5 = new Samples(); final Samples[] contacts, coils;
        final double start, end; double actualPhaseStart, actualPhaseEnd = Double.NaN; int positionMismatchMask; Snapshot last;
        Window(double start, double end, int channels) {
            this.start = start; this.end = end;
            contacts = new Samples[channels]; coils = new Samples[channels];
            for (int n = 0; n < channels; n++) { contacts[n] = new Samples(); coils[n] = new Samples(); }
        }
        void add(Snapshot s, double acceptedStart, double acceptedEnd) {
            double from = Math.max(start, acceptedStart), until = Math.min(end, acceptedEnd), weight = until - from;
            if (weight <= 0) return;
            double firstWeight = Math.max(0, Math.min(until, (start + end) / 2) - from);
            if (last == null) last = new Snapshot(s); else last.copyFrom(s);
            rail12.add(s.rail12, weight, firstWeight); rail5.add(s.rail5, weight, firstWeight);
            for (int n = 0; n < contacts.length; n++) {
                contacts[n].add(s.contacts[n], weight, firstWeight); coils[n].add(s.coils[n], weight, firstWeight);
                if (s.positions[n] != 0 && s.positions[n] != 1) positionMismatchMask |= 1 << n;
            }
        }
        void validateCoverage() {
            check(Math.abs(rail12.duration - (end - start)) <= 1e-12 &&
                Math.abs(rail5.duration - (end - start)) <= 1e-12 &&
                Math.abs(rail12.firstDuration - (end - start) / 2) <= 1e-12 &&
                Math.abs(rail12.secondDuration - (end - start) / 2) <= 1e-12,
                "exact adaptive physical measurement-window coverage and half-window weights");
        }
        void validateRails() {
            validateCoverage();
            check(rail12.mean() >= 10.64 && rail12.mean() <= 11.76 && rail12.ripple() <= .05 && rail12.drift() <= .02, "frozen healthy converted-rail range/ripple/drift");
            check(rail5.min >= 4.75 && rail5.max <= 5.25, "actual E02 regulated rail retains existing healthy bounds");
        }
        void validateInputs(int mask, Rb56ControlTail.Result tail) {
            for (int n = 0; n < last.channels.length; n++) {
                boolean high = (mask & (1 << n)) != 0;
                check(last.sourceCommands[n] == (high ? Rb56Plan.SENSOR_HIGH_VOLTS : Rb56Plan.SENSOR_LOW_VOLTS),
                    "actual source settings use declared Q60 customer commands");
                check(finite(last.sources[n]) && finite(last.raws[n]) && finite(last.senses[n]) && finite(last.references[n]) && finite(last.decisionOutputs[n]),
                    "actual raw/conditioned/reference/output nodes are finite");
                check(high ? last.raws[n] >= tail.configuration.sensorHighVolts : Math.abs(last.raws[n]) <= tail.configuration.sensorLowVolts,
                    "real source reaches the requested raw sensor condition");
                check(last.controlStates[n] == (high ? E04SensorControlModel.ControlState.HIGH : E04SensorControlModel.ControlState.LOW),
                    "actual E04 state follows the declared external command");
                double threshold = last.references[n] + (tail.plan.sharedHystereticReference() ?
                    (high ? tail.configuration.risingThresholdOffsetVolts : tail.configuration.fallingThresholdOffsetVolts) : tail.configuration.directThresholdOffsetVolts);
                check(high ? last.senses[n] >= threshold : last.senses[n] <= threshold,
                    "actual conditioned sensor passes its unchanged E04 physical threshold");
            }
        }
        void validateContacts(int mask, Rb56ControlTail.Result tail, Rb56Plan plan) {
            for (int n = 0; n < contacts.length; n++) {
                boolean high = (mask & (1 << n)) != 0;
                check(high ? contacts[n].min >= 10.8 && contacts[n].max <= 12.6 : contacts[n].absoluteMax() <= .05,
                    "actual contact load follows sensor combination channel " + plan.channels()[n]);
                check(last.positions[n] == (high ? 1 : 0) && positionMismatchMask == 0,
                    "actual relay mechanical position follows the accepted condition");
                check(high ? coils[n].min >= tail.relays.get(plan.channels()[n]).onCurrent : Math.abs(coils[n].mean()) < RelayOutputBehavior.DISCHARGED_AMPS,
                    "actual relay coil current agrees with the response");
            }
        }
        String report(String name) { StringBuilder b = new StringBuilder("{\"phase\":\"" + name + "\",\"startSeconds\":" + number(start) + ",\"endSeconds\":" + number(end) + ",\"actualPhaseStartSeconds\":" + number(actualPhaseStart) + ",\"actualPhaseEndSeconds\":" + number(actualPhaseEnd) + ",\"rail12\":" + rail12.report() + ",\"rail5\":" + rail5.report() + ",\"contacts\":[");
            for (int n = 0; n < contacts.length; n++) { if (n > 0) b.append(','); b.append(contacts[n].report()); }
            b.append("],\"coils\":[");
            for (int n = 0; n < coils.length; n++) { if (n > 0) b.append(','); b.append(coils[n].report()); }
            return b.append("],\"last\":").append(last == null ? "null" : last.report()).append('}').toString(); }
    }
    private static final class Result {
        final PhaseReceipts receipts = new PhaseReceipts();
        final Rb56Plan plan; final int packages; final Vector<String> windows = new Vector<String>(), sensitivityWindows = new Vector<String>(); String phase = "CONSTRUCTION"; Snapshot last; Window current, sensitivityCurrent;
        int maxIterations; long acceptedSteps, sampledAcceptedSteps, trialCursor, attemptedTrials, acceptedSolveTrials, discardedAttemptTrials, failedAttemptTrials;
        double maxOptoGmin, minimumBiasLoss = Double.POSITIVE_INFINITY, targetTime;
        double minimumAcceptedDt = Double.POSITIVE_INFINITY, maximumAcceptedDt;
        final double[] firstOffBrownout;
        double enableOpenMaxRail12 = Double.NaN, mainOffRail12 = Double.NaN, mainOffRail5 = Double.NaN, finalResidual = Double.NaN;
        double finalTime = Double.NaN, finalBulk = Double.NaN, finalInductor = Double.NaN;
        Result(Rb56Plan plan, int packages) {
            this.plan = plan; this.packages = packages; firstOffBrownout = new double[plan.channelCount];
            for (int n = 0; n < firstOffBrownout.length; n++) firstOffBrownout[n] = Double.NaN;
        }
        void failedTrials(TestCirSim sim) {
            long failed = sim.a01SubIterationCount - trialCursor;
            attemptedTrials += failed; failedAttemptTrials += failed; trialCursor = sim.a01SubIterationCount;
        }
        String brownoutTimes() {
            StringBuilder b = new StringBuilder("[");
            for (int n = 0; n < firstOffBrownout.length; n++) { if (n > 0) b.append(','); b.append(number(firstOffBrownout[n])); }
            return b.append(']').toString();
        }
        String report(String status) { StringBuilder b = new StringBuilder("{\"status\":\"" + status + "\",\"packages\":" + packages + ",\"seed\":\"" + plan.seed + "\",\"topology\":\"" + plan.topology() +
            "\",\"phase\":\"" + phase + "\",\"maxIterations\":" + maxIterations + ",\"maxOptoGmin\":" + number(maxOptoGmin) + ",\"minBiasLossW\":" + number(minimumBiasLoss) +
            ",\"enableOpenMaxRail12V\":" + number(enableOpenMaxRail12) + ",\"mainOffRail12V\":" + number(mainOffRail12) + ",\"mainOffRail5V\":" + number(mainOffRail5) +
            ",\"finalResidualV\":" + number(finalResidual) + ",\"finalBulkV\":" + number(finalBulk) + ",\"finalInductorA\":" + number(finalInductor) + ",\"acceptedSteps\":" + acceptedSteps +
            ",\"sampledAcceptedSteps\":" + sampledAcceptedSteps + ",\"attemptedNewtonTrials\":" + attemptedTrials + ",\"acceptedFinalSolveTrials\":" + acceptedSolveTrials +
            ",\"discardedTimestepAttemptTrials\":" + discardedAttemptTrials + ",\"failedUnacceptedAttemptTrials\":" + failedAttemptTrials +
            ",\"minimumAcceptedDtSeconds\":" + number(minimumAcceptedDt) + ",\"maximumAcceptedDtSeconds\":" + number(maximumAcceptedDt) +
            ",\"firstOffBrownoutSeconds\":" + brownoutTimes() +
            ",\"requestedFinalTime\":" + number(targetTime) + ",\"finalTime\":" + number(finalTime) + ",\"last\":" + (last == null ? "null" : last.report()) + ",\"currentWindow\":" + (current == null ? "null" : current.report(phase)) + ",\"windows\":[");
            for (int n = 0; n < windows.size(); n++) { if (n > 0) b.append(','); b.append(windows.get(n)); }
            b.append("],\"sensitivityWindows\":[");
            for (int n = 0; n < sensitivityWindows.size(); n++) { if (n > 0) b.append(','); b.append(sensitivityWindows.get(n)); }
            return b.append("]}").toString(); }
    }
    private static final class RoutineConvergenceStream extends PrintStream {
        final PrintStream original; long count; int max; final Pattern pattern = Pattern.compile("^converged after ([0-9]+) iterations, timeStep = ([0-9.E+-]+)$");
        RoutineConvergenceStream(PrintStream original) { super(original, true); this.original = original; }
        @Override public synchronized void println(String line) { Matcher m = line == null ? null : pattern.matcher(line);
            if (m != null && m.matches()) try { int iterations = Integer.parseInt(m.group(1)); double dt = Double.parseDouble(m.group(2));
                if (iterations >= 1 && iterations <= 5000 && finite(dt) &&
                        dt >= Rb30Behavior.SOLVER_MIN_STEP_SECONDS && dt <= DT) { count++; max = Math.max(max, iterations); return; }
            } catch (NumberFormatException unknown) { /* Preserve unknown lines. */ } original.println(line); }
        void report() { original.println("RB56_ASSEMBLY_ROUTINE {\"count\":" + count + ",\"max\":" + max + "}"); }
    }
    private static final class TestCirSim extends CirSim {
        TestCirSim() { gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>(); maxTimeStep = timeStep = DT; minTimeStep = Rb30Behavior.SOLVER_MIN_STEP_SECONDS; adjustTimeStep = true; }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm e) { stopMessage = message; circuitMatrix = null; stopElm = e; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}