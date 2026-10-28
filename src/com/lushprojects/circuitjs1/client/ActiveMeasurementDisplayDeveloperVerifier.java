package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.user.client.ui.VerticalPanel;

/** Exercises the production instrument strategies against real residual RC energy. */
final class ActiveMeasurementDisplayDeveloperVerifier {
    private static final double RESIDUAL_THRESHOLD =
        ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS;
    private static final double COOLDOWN_LIMIT_SECONDS = 3.0;
    private static final double COOLDOWN_SLICE_SECONDS = .05;

    private ActiveMeasurementDisplayDeveloperVerifier() { }

    static void verify() {
        CirSim priorSim = CircuitElm.sim;
        CirSim priorSingleton = CirSim.theSim;
        String priorDiodeModel = DiodeElm.lastModelName;
        MeterSim sim = new MeterSim();
        GeneratedBoardInstance owner = null;
        Vector<CircuitElm> fixture = new Vector<CircuitElm>();
        VerticalPanel panel = new VerticalPanel();
        InstrumentController controller = null;
        Throwable failure = null;
        CircuitElm.sim = sim;
        CirSim.theSim = sim;
        try {
            owner = new LedIndicatorGenerator().generate(3);
            sim.generatedBoardInstance = owner;
            sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
            sim.boardModificationController = new BoardModificationController(sim, owner);
            sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);

            ResistorElm numericLoad = resistor(100000, 100000, 100080, 100000, 1000);
            CapacitorElm numericCapacitor = capacitor(100000, 100000, 100080, 100000);
            add(sim, fixture, numericLoad);
            add(sim, fixture, numericCapacitor);
            owner.getPhysicalBoardRuntime().registerCapability(
                new LiteralCapacitorReadiness(numericCapacitor, "DISPLAY_NUMERIC_RC_ENERGY"));

            ResistorElm olLoad = resistor(102000, 100000, 102080, 100000, 100000);
            CapacitorElm olCapacitor = capacitor(102000, 100000, 102080, 100000);
            add(sim, fixture, olLoad);
            add(sim, fixture, olCapacitor);
            owner.getPhysicalBoardRuntime().registerCapability(
                new LiteralCapacitorReadiness(olCapacitor, "DISPLAY_OL_RC_ENERGY"));

            owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner,
                sim.boardModificationController, 0);
            owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.a01MeasurementRunning = true;
            controller = new InstrumentController(sim, panel);
            sim.instrumentController = controller;

            settle(sim, owner);
            ProbeTarget numericRed = new LiteralProbe(sim, endpoint(numericLoad, 0));
            ProbeTarget numericBlack = new LiteralProbe(sim, endpoint(numericLoad, 1));
            verifyNumericModeLifecycle(sim, owner, numericCapacitor, numericRed, numericBlack,
                "CONTINUITY");
            verifyNumericModeLifecycle(sim, owner, numericCapacitor, numericRed, numericBlack,
                "DIODE");

            ProbeTarget olRed = new LiteralProbe(sim, endpoint(olLoad, 0));
            ProbeTarget olBlack = new LiteralProbe(sim, endpoint(olLoad, 1));
            verifyDiodeOlLifecycle(sim, owner, olCapacitor, olRed, olBlack);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            RuntimeException cleanupFailure = null;
            if (controller != null) {
                try {
                    controller.exitInstrumentModeForDeveloperVerification();
                    if (!controller.isIdleForGeneratedSettlement())
                        throw new IllegalStateException("Temporary meter retained a deferred callback");
                } catch (RuntimeException error) {
                    cleanupFailure = error;
                }
            }
            if (owner != null && !sim.getBoardPowerController().isElectricallyUnpowered()) {
                try {
                    sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                } catch (RuntimeException error) {
                    if (cleanupFailure == null) cleanupFailure = error;
                }
            }
            sim.a01MeasurementRunning = false;
            boolean overlayClear = !sim.activeMeasurementOverlay;
            sim.instrumentController = null;
            sim.generatedBoardInstance = null;
            sim.boardModificationController = null;
            sim.solverExecutor.retire();
            sim.solverExecutor.invalidate();
            sim.circuitMatrix = null;
            sim.getBoardPowerController().detach();
            panel.clear();
            for (CircuitElm element : fixture) element.delete();
            if (owner != null)
                for (CircuitElm element : owner.getSimulationElements()) element.delete();
            sim.elmList.clear();
            DiodeElm.lastModelName = priorDiodeModel;
            CircuitElm.sim = priorSim;
            CirSim.theSim = priorSingleton;
            if (failure == null && cleanupFailure != null) throw cleanupFailure;
            if (failure == null && !overlayClear)
                throw new IllegalStateException("Temporary meter overlay survived display verification");
        }
    }

    private static void verifyNumericModeLifecycle(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, ProbeTarget red, ProbeTarget black, String modeId) {
        capacitor.reset();
        settle(sim, owner);
        InstrumentController controller = sim.instrumentController;
        require(controller.getActiveMeasurementReadinessForMode(modeId, red, black) ==
                ActiveMeasurementReadiness.READY,
            modeId + " literal RC fixture begins ready");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        int before = measurementCount(controller, modeId);
        double started = sim.t;
        selectAndMeasure(controller, modeId, red, black);
        double elapsed = sim.t - started;
        InstrumentController.ModeState sampled = modeState(controller, modeId);
        double sampledPrimary = sampled.primaryValue;
        String sampledDisplay = controller.getReadingForDeveloperVerification();
        require(measurementCount(controller, modeId) == before + 1 &&
                PowerDomainContract.finite(sampledPrimary) && sampledPrimary >= 0 &&
                sampledDisplay.equals(sampled.displayText) && !sampledDisplay.startsWith("---") &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD &&
                controller.getActiveMeasurementReadinessForMode(modeId, red, black) ==
                    ActiveMeasurementReadiness.DISCHARGE &&
                "Reading held; discharge first".equals(controller.captureForDeveloperVerification().statusText),
            modeId + " actual strategy captured a numeric result that left real capacitor energy");
        require(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
            modeId + " uses the bounded production meter acquisition window");
        restored(sim, owner, graph);

        double heldAt = sim.t;
        for (int repeat = 0; repeat < 3; repeat++) {
            require(sim.isChallengeInteractionEnabled() && !sim.activeMeasurementOverlay,
                modeId + " production step callback is available after meter cleanup");
            controller.setDeveloperReadingForVerification("stale display");
            controller.onSimulationStepComplete(false);
            InstrumentController.ModeState held = modeState(controller, modeId);
            require(sim.t == heldAt && measurementCount(controller, modeId) == before + 1 &&
                    held.primaryValue == sampledPrimary &&
                    sampledDisplay.equals(controller.getReadingForDeveloperVerification()) &&
                    "Reading held; discharge first".equals(
                        controller.captureForDeveloperVerification().statusText),
                modeId + " repeated production display steps retain the captured reading without a new source");
        }

        double cooled = advanceUntilReady(sim, owner, capacitor, controller, modeId, red, black,
            graph);
        controller.onSimulationStepComplete(false);
        require(controller.getReadingForDeveloperVerification().equals(sampledDisplay) &&
                modeState(controller, modeId).primaryValue == sampledPrimary &&
                controller.captureForDeveloperVerification().statusText.length() == 0 &&
                measurementCount(controller, modeId) == before + 1 && sim.t > heldAt && cooled > 0,
            modeId + " natural RC cooldown releases the held hint without injecting another sample");

        double refreshStart = sim.t;
        selectAndMeasure(controller, modeId, red, black);
        require(measurementCount(controller, modeId) == before + 2 && sim.t > refreshStart &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD &&
                controller.getActiveMeasurementReadinessForMode(modeId, red, black) ==
                    ActiveMeasurementReadiness.DISCHARGE,
            modeId + " explicit selection is admitted again after natural cooldown");
        restored(sim, owner, graph);

        double blockedAt = sim.t;
        selectAndMeasure(controller, modeId, red, black);
        InstrumentController.ModeState cleared = modeState(controller, modeId);
        require(sim.t == blockedAt && measurementCount(controller, modeId) == before + 2 &&
                Double.isNaN(cleared.primaryValue) &&
                "DISCHARGE".equals(controller.getReadingForDeveloperVerification()) &&
                !"Reading held; discharge first".equals(
                    controller.captureForDeveloperVerification().statusText) &&
                !sim.activeMeasurementOverlay,
            modeId + " explicit blocked refresh clears the stale result without injecting");

        advanceUntilReady(sim, owner, capacitor, controller, modeId, red, black, graph);
        double powerSampleStart = sim.t;
        selectAndMeasure(controller, modeId, red, black);
        require(sim.t > powerSampleStart && measurementCount(controller, modeId) == before + 3,
            modeId + " can take a later actual sample after a second cooldown");
        verifyPoweredTransitionClears(sim, owner, controller, modeId, red, black,
            sampledDisplay, before + 3);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        settle(sim, owner);
        controller.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay && controller.isIdleForGeneratedSettlement(),
            modeId + " lifecycle exit clears the temporary controller mode");
        ensureReadyAfterPowerCycle(sim, owner, capacitor, controller, modeId, red, black,
            graph, modeId + " post-power lifecycle");
    }

    private static void verifyDiodeOlLifecycle(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, ProbeTarget red, ProbeTarget black) {
        capacitor.reset();
        settle(sim, owner);
        InstrumentController controller = sim.instrumentController;
        require(controller.getActiveMeasurementReadinessForMode("DIODE", red, black) ==
                ActiveMeasurementReadiness.READY,
            "literal high-resistance RC diode fixture begins ready");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        int before = controller.getDiodeMeasurementCountForDeveloperVerification();
        double started = sim.t;
        controller.setDiodeProbesForDeveloperVerification(red, black);
        double elapsed = sim.t - started;
        InstrumentController.ModeState sampled = modeState(controller, "DIODE");
        double sampledCurrent = sampled.secondaryValue;
        require("OL".equals(controller.getReadingForDeveloperVerification()) &&
                Double.isNaN(sampled.primaryValue) && PowerDomainContract.finite(sampledCurrent) &&
                sampledCurrent >= InstrumentController.DIODE_MINIMUM_CURRENT &&
                Math.abs(sampledCurrent - (3.0 / 101000.0)) < 3e-7 &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == before + 1 &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD &&
                controller.getActiveMeasurementReadinessForMode("DIODE", red, black) ==
                    ActiveMeasurementReadiness.DISCHARGE &&
                "Reading held; discharge first".equals(
                    controller.captureForDeveloperVerification().statusText),
            "actual finite over-compliance diode observation remains OL while its capacitor is charged");
        require(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
            "actual diode OL acquisition uses the bounded production meter window");
        restored(sim, owner, graph);

        double heldAt = sim.t;
        for (int repeat = 0; repeat < 3; repeat++) {
            require(sim.isChallengeInteractionEnabled() && !sim.activeMeasurementOverlay,
                "DIODE production step callback is available after meter cleanup");
            controller.setDeveloperReadingForVerification("stale display");
            controller.onSimulationStepComplete(false);
            InstrumentController.ModeState held = modeState(controller, "DIODE");
            require(sim.t == heldAt &&
                    controller.getDiodeMeasurementCountForDeveloperVerification() == before + 1 &&
                    Double.isNaN(held.primaryValue) && held.secondaryValue == sampledCurrent &&
                    "OL".equals(controller.getReadingForDeveloperVerification()) &&
                    "Reading held; discharge first".equals(
                        controller.captureForDeveloperVerification().statusText),
                "repeated actual diode display steps retain finite OL evidence without reinjection");
        }

        verifyCompletedDiodeSnapshotRestore(sim, controller, sampledCurrent, before + 1);

        double cooled = advanceUntilReady(sim, owner, capacitor, controller, "DIODE", red, black,
            graph);
        controller.onSimulationStepComplete(false);
        require("OL".equals(controller.getReadingForDeveloperVerification()) &&
                Double.isNaN(modeState(controller, "DIODE").primaryValue) &&
                controller.captureForDeveloperVerification().statusText.length() == 0 &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == before + 1 &&
                cooled > 0,
            "natural RC cooldown retains the actual OL display and clears only its hold hint");

        double refreshAt = sim.t;
        controller.setDiodeProbesForDeveloperVerification(red, black);
        require(sim.t > refreshAt &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == before + 2 &&
                "OL".equals(controller.getReadingForDeveloperVerification()) &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD,
            "a fresh over-compliance result is admitted after natural cooldown");
        double blockedAt = sim.t;
        controller.setDiodeProbesForDeveloperVerification(red, black);
        InstrumentController.ModeState blocked = modeState(controller, "DIODE");
        require(sim.t == blockedAt &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == before + 2 &&
                Double.isNaN(blocked.primaryValue) && Double.isNaN(blocked.secondaryValue) &&
                blocked.refreshPending &&
                "DISCHARGE".equals(controller.getReadingForDeveloperVerification()) &&
                !"Reading held; discharge first".equals(
                    controller.captureForDeveloperVerification().statusText),
            "a blocked diode refresh drops stale OL state without changing solver time");

        verifyPendingDiodeSnapshotRestore(sim, controller, before + 2);

        advanceUntilReady(sim, owner, capacitor, controller, "DIODE", red, black, graph);
        controller.setDiodeProbesForDeveloperVerification(red, black);
        InstrumentController.ModeState heldAgain = modeState(controller, "DIODE");
        require(controller.getDiodeMeasurementCountForDeveloperVerification() == before + 3 &&
                "OL".equals(controller.getReadingForDeveloperVerification()) &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD &&
                heldAgain.secondaryValue >= InstrumentController.DIODE_MINIMUM_CURRENT,
            "diode OL recovers into a new valid completed result");

        verifyPoweredTransitionClears(sim, owner, controller, "DIODE", red, black,
            "OL", before + 3);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        settle(sim, owner);
        controller.exitInstrumentModeForDeveloperVerification();
        require(controller.isIdleForGeneratedSettlement(),
            "diode power transition lifecycle exit clears callbacks and active mode");
        ensureReadyAfterPowerCycle(sim, owner, capacitor, controller, "DIODE", red, black,
            graph, "DIODE post-power lifecycle");

        controller.setDiodeProbesForDeveloperVerification(red, black);
        InstrumentController.ModeState recoveredOl = modeState(controller, "DIODE");
        require(controller.getDiodeMeasurementCountForDeveloperVerification() == before + 4 &&
                "OL".equals(controller.getReadingForDeveloperVerification()) &&
                recoveredOl.secondaryValue >= InstrumentController.DIODE_MINIMUM_CURRENT &&
                Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD,
            "diode mode captures another actual OL result after a powered lifecycle");
        double beforeModeChange = sim.t;
        controller.setContinuityProbesForDeveloperVerification(red, black);
        InstrumentController.ModeState clearedDiode = modeState(controller, "DIODE");
        require(sim.t == beforeModeChange &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == before + 4 &&
                Double.isNaN(clearedDiode.primaryValue) && Double.isNaN(clearedDiode.secondaryValue) &&
                "--- V".equals(clearedDiode.displayText) &&
                "DISCHARGE".equals(controller.getReadingForDeveloperVerification()) &&
                !sim.activeMeasurementOverlay,
            "switching modes clears retained diode OL without injecting during discharge");
        controller.exitInstrumentModeForDeveloperVerification();
        require(controller.isIdleForGeneratedSettlement(),
            "diode OL lifecycle exit clears callbacks and active mode");
        advanceUntilReady(sim, owner, capacitor, controller, "DIODE", red, black, graph);
    }

    private static void verifyCompletedDiodeSnapshotRestore(MeterSim sim,
            InstrumentController controller, double sampledCurrent, int expectedCount) {
        InstrumentController.DeveloperState baseline =
            controller.captureForDeveloperVerification();
        InstrumentController.ModeState baselineDiode = modeState(controller, "DIODE");
        double started = sim.t;
        require("DIODE".equals(baseline.activeStrategy.getId()) &&
                baselineDiode.secondaryValue == sampledCurrent &&
                Double.isNaN(baselineDiode.primaryValue) &&
                "OL".equals(baselineDiode.displayText) && baselineDiode.refreshPending == false,
            "held OL snapshot records its finite current and generic mode state");

        controller.exitInstrumentModeForDeveloperVerification();
        InstrumentController.ModeState exitedDiode = modeState(controller, "DIODE");
        require(controller.isIdleForGeneratedSettlement() &&
                Double.isNaN(exitedDiode.primaryValue) && Double.isNaN(exitedDiode.secondaryValue) &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == expectedCount &&
                sim.t == started && !sim.activeMeasurementOverlay,
            "exiting diode mode clears the live OL state before baseline restoration");

        controller.restoreForDeveloperVerification(baseline);
        controller.onSimulationStepComplete(false);
        InstrumentController.ModeState restoredDiode = modeState(controller, "DIODE");
        require("DIODE".equals(controller.captureForDeveloperVerification().activeStrategy.getId()) &&
                controller.getActiveMeasurementReadinessForMode("DIODE",
                    baseline.redProbe, baseline.blackProbe) == ActiveMeasurementReadiness.DISCHARGE &&
                Double.isNaN(restoredDiode.primaryValue) &&
                restoredDiode.secondaryValue == sampledCurrent &&
                "OL".equals(restoredDiode.displayText) &&
                "OL".equals(controller.getReadingForDeveloperVerification()) &&
                "Reading held; discharge first".equals(
                    controller.captureForDeveloperVerification().statusText) &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == expectedCount &&
                sim.t == started && !sim.activeMeasurementOverlay,
            "restored actual diode snapshot retains OL and its discharge hint on the next display step");
    }

    private static void verifyPendingDiodeSnapshotRestore(MeterSim sim,
            InstrumentController controller, int expectedCount) {
        InstrumentController.DeveloperState baseline =
            controller.captureForDeveloperVerification();
        InstrumentController.ModeState baselineDiode = modeState(controller, "DIODE");
        double started = sim.t;
        require(baselineDiode.refreshPending &&
                Double.isNaN(baselineDiode.secondaryValue) &&
                "DISCHARGE".equals(baselineDiode.displayText),
            "blocked diode refresh snapshot records the pending generic mode state");

        controller.exitInstrumentModeForDeveloperVerification();
        require(!modeState(controller, "DIODE").refreshPending &&
                controller.isIdleForGeneratedSettlement(),
            "exiting diode mode clears pending refresh state");
        controller.restoreForDeveloperVerification(baseline);
        InstrumentController.ModeState restoredDiode = modeState(controller, "DIODE");
        require(restoredDiode.refreshPending,
            "restoring diode baseline reinstates generic pending refresh state");
        controller.onSimulationStepComplete(false);
        restoredDiode = modeState(controller, "DIODE");
        require(restoredDiode.refreshPending && Double.isNaN(restoredDiode.secondaryValue) &&
                "DISCHARGE".equals(restoredDiode.displayText) &&
                "DISCHARGE".equals(controller.getReadingForDeveloperVerification()) &&
                controller.getActiveMeasurementReadinessForMode("DIODE",
                    baseline.redProbe, baseline.blackProbe) == ActiveMeasurementReadiness.DISCHARGE &&
                controller.getDiodeMeasurementCountForDeveloperVerification() == expectedCount &&
                sim.t == started && !sim.activeMeasurementOverlay,
            "restored pending diode state remains blocked without injecting or losing its refresh");
    }

    private static void verifyPoweredTransitionClears(MeterSim sim, GeneratedBoardInstance owner,
            InstrumentController controller, String modeId, ProbeTarget red, ProbeTarget black,
            String priorDisplay, int expectedCount) {
        InstrumentController.ModeState beforePower = modeState(controller, modeId);
        require(beforePower.measurementCount == expectedCount &&
                priorDisplay.equals(beforePower.displayText) &&
                priorDisplay.equals(controller.getReadingForDeveloperVerification()) &&
                ("DIODE".equals(modeId) ?
                    PowerDomainContract.finite(beforePower.secondaryValue) :
                    PowerDomainContract.finite(beforePower.primaryValue)),
            modeId + " power-transition fixture starts with its actual captured result" +
                diagnosticState(sim, controller, modeId,
                    owner.getExternalPowerBindings().areAllConnected()));
        double transitionAt = sim.t;
        sim.setBoardPowerState(BoardPowerState.POWERED);
        InstrumentController.ModeState immediatelyPowered = modeState(controller, modeId);
        InstrumentController.DeveloperState immediateController =
            controller.captureForDeveloperVerification();
        boolean sourceConnected = owner.getExternalPowerBindings().areAllConnected();
        require(sim.t == transitionAt &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                !sim.getBoardPowerController().isElectricallyUnpowered() && sourceConnected &&
                measurementCount(controller, modeId) == expectedCount &&
                Double.isNaN(immediatelyPowered.primaryValue) &&
                Double.isNaN(immediatelyPowered.secondaryValue) &&
                !priorDisplay.equals(controller.getReadingForDeveloperVerification()) &&
                immediateController.statusText.length() == 0 &&
                !sim.activeMeasurementOverlay,
            modeId + " power transition immediately clears its captured display without injection" +
                diagnosticState(sim, controller, modeId, sourceConnected));

        settle(sim, owner);
        double callbackAt = sim.t;
        controller.onSimulationStepComplete(true);
        double settledAt = sim.t;
        InstrumentController.ModeState powered = modeState(controller, modeId);
        InstrumentController.DeveloperState settledController =
            controller.captureForDeveloperVerification();
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForMode(modeId, red, black);
        sourceConnected = owner.getExternalPowerBindings().areAllConnected();
        require(readiness == ActiveMeasurementReadiness.POWER_OFF &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                !sim.getBoardPowerController().isElectricallyUnpowered() && sourceConnected &&
                measurementCount(controller, modeId) == expectedCount &&
                Double.isNaN(powered.primaryValue) && Double.isNaN(powered.secondaryValue) &&
                "POWER OFF".equals(powered.displayText) &&
                "POWER OFF".equals(controller.getReadingForDeveloperVerification()) &&
                settledController.statusText.length() == 0 && sim.t == callbackAt &&
                settledAt == callbackAt &&
                !sim.activeMeasurementOverlay,
            modeId + " settled live power state displays POWER OFF without another injection" +
                diagnosticState(sim, controller, modeId, sourceConnected));
    }

    private static String diagnosticState(MeterSim sim, InstrumentController controller,
            String modeId, boolean sourceConnected) {
        InstrumentController.ModeState state = modeState(controller, modeId);
        InstrumentController.DeveloperState snapshot = controller.captureForDeveloperVerification();
        return " [display=" + controller.getReadingForDeveloperVerification() +
            ", modeDisplay=" + state.displayText + ", primary=" + state.primaryValue +
            ", secondary=" + state.secondaryValue + ", power=" +
            sim.getBoardPowerController().getState() + ", electricallyUnpowered=" +
            sim.getBoardPowerController().isElectricallyUnpowered() + ", sourcesConnected=" +
            sourceConnected + ", count=" + state.measurementCount + ", t=" + sim.t +
            ", readiness=" + controller.getActiveMeasurementReadinessForMode(modeId,
                snapshot.redProbe, snapshot.blackProbe) +
            ", analyzeFlag=" + sim.analyzeFlag + ", stopMessage=" + sim.stopMessage + "]";
    }

    private static double advanceUntilReady(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, InstrumentController controller, String modeId,
            ProbeTarget red, ProbeTarget black, Vector<CircuitElm> graph) {
        return advanceUntilReady(sim, owner, capacitor, controller, modeId, red, black, graph,
            "natural RC cooldown");
    }

    private static void ensureReadyAfterPowerCycle(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, InstrumentController controller, String modeId,
            ProbeTarget red, ProbeTarget black, Vector<CircuitElm> graph, String phase) {
        double start = sim.t;
        ActiveMeasurementStimulus stimulus = sim.lastActiveMeasurementStimulus;
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForMode(modeId, red, black);
        if (readiness == ActiveMeasurementReadiness.READY) {
            double capacitorVoltage = capacitor.getVoltageDiff();
            require(PowerDomainContract.finite(capacitorVoltage) &&
                    Math.abs(capacitorVoltage) <= RESIDUAL_THRESHOLD &&
                    sim.t == start && sim.lastActiveMeasurementStimulus == stimulus &&
                    sim.getGeneratedBoardInstance() == owner && sim.elmList.equals(graph) &&
                    sim.stopMessage == null && !sim.activeMeasurementOverlay &&
                    sim.getBoardPowerController().isElectricallyUnpowered() &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                modeId + " " + phase + " already READY with verified residual energy and restored solver" +
                    cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness,
                        phase));
            return;
        }
        advanceUntilReady(sim, owner, capacitor, controller, modeId, red, black, graph,
            phase + " residual-energy cooldown");
    }

    private static double advanceUntilReady(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, InstrumentController controller, String modeId,
            ProbeTarget red, ProbeTarget black, Vector<CircuitElm> graph, String phase) {
        double start = sim.t;
        ActiveMeasurementStimulus stimulus = sim.lastActiveMeasurementStimulus;
        ActiveMeasurementReadiness readiness =
            controller.getActiveMeasurementReadinessForMode(modeId, red, black);
        require(readiness == ActiveMeasurementReadiness.DISCHARGE ||
                readiness == ActiveMeasurementReadiness.WAITING,
            modeId + " " + phase + " must begin from real residual energy" +
                cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
        int slices = 0;
        while (readiness == ActiveMeasurementReadiness.DISCHARGE ||
                readiness == ActiveMeasurementReadiness.WAITING) {
            double elapsed = sim.t - start;
            require(elapsed < COOLDOWN_LIMIT_SECONDS,
                modeId + " " + phase + " residual capacitor did not discharge within bounded solver time" +
                    cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
            double priorTime = sim.t;
            sim.solverExecutor.advanceFor(Math.min(COOLDOWN_SLICE_SECONDS,
                COOLDOWN_LIMIT_SECONDS - elapsed));
            owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
            require(sim.t > priorTime && sim.stopMessage == null &&
                    sim.getGeneratedBoardInstance() == owner && sim.elmList.equals(graph) &&
                    sim.lastActiveMeasurementStimulus == stimulus &&
                    !sim.activeMeasurementOverlay &&
                    sim.getBoardPowerController().isElectricallyUnpowered(),
                modeId + " " + phase + " changes no owner, graph, meter stimulus, or power state" +
                    cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
            readiness = controller.getActiveMeasurementReadinessForMode(modeId, red, black);
            require(readiness == ActiveMeasurementReadiness.READY ||
                    readiness == ActiveMeasurementReadiness.DISCHARGE ||
                    readiness == ActiveMeasurementReadiness.WAITING,
                modeId + " " + phase + " stopped at an unexpected readiness state" +
                    cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
            require(++slices <= 1000,
                modeId + " " + phase + " exceeded its bounded slice count" +
                    cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
        }
        double elapsed = sim.t - start;
        require(readiness == ActiveMeasurementReadiness.READY && elapsed > 0 &&
                elapsed <= COOLDOWN_LIMIT_SECONDS + .001 &&
                Math.abs(capacitor.getVoltageDiff()) <= RESIDUAL_THRESHOLD &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            modeId + " " + phase + " reaches actual READY with source absent" +
                cooldownDiagnostic(sim, owner, capacitor, graph, stimulus, readiness, phase));
        return elapsed;
    }

    private static String cooldownDiagnostic(MeterSim sim, GeneratedBoardInstance owner,
            CapacitorElm capacitor, Vector<CircuitElm> graph, ActiveMeasurementStimulus stimulus,
            ActiveMeasurementReadiness readiness, String phase) {
        return " [phase=" + phase + ", readiness=" + readiness + ", capacitorVoltage=" +
            capacitor.getVoltageDiff() + ", threshold=" + RESIDUAL_THRESHOLD + ", t=" + sim.t +
            ", ownerMatches=" + (sim.getGeneratedBoardInstance() == owner) + ", graphMatches=" +
            sim.elmList.equals(graph) + ", powerOff=" +
            sim.getBoardPowerController().isElectricallyUnpowered() + ", overlay=" +
            sim.activeMeasurementOverlay + ", stimulusUnchanged=" +
            (sim.lastActiveMeasurementStimulus == stimulus) + ", solverRestored=" +
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification() +
            ", stopMessage=" + sim.stopMessage + "]";
    }

    private static void selectAndMeasure(InstrumentController controller, String modeId,
            ProbeTarget red, ProbeTarget black) {
        if ("CONTINUITY".equals(modeId))
            controller.setContinuityProbesForDeveloperVerification(red, black);
        else if ("DIODE".equals(modeId))
            controller.setDiodeProbesForDeveloperVerification(red, black);
        else
            throw new IllegalArgumentException("Unsupported active display mode: " + modeId);
    }

    private static int measurementCount(InstrumentController controller, String modeId) {
        return "CONTINUITY".equals(modeId) ?
            controller.getContinuityMeasurementCountForDeveloperVerification() :
            controller.getDiodeMeasurementCountForDeveloperVerification();
    }

    private static InstrumentController.ModeState modeState(InstrumentController controller,
            String id) {
        InstrumentController.DeveloperState state = controller.captureForDeveloperVerification();
        for (InstrumentController.ModeState mode : state.modeStates)
            if (id.equals(mode.id)) return mode;
        throw new IllegalStateException("Missing instrument mode state: " + id);
    }

    private static void settle(MeterSim sim, GeneratedBoardInstance owner) {
        sim.solverExecutor.invalidate();
        sim.analyzeCircuit();
        sim.analyzeFlag = false;
        sim.solverExecutor.advanceSteps(8);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        require(sim.stopMessage == null && sim.isGeneratedRuntimeSettled(),
            "literal display fixture settles through the live CircuitJS solver");
    }

    private static ResistorElm resistor(int x, int y, int x2, int y2, double ohms) {
        ResistorElm load = new ResistorElm(x, y);
        load.setPosition(x, y, x2, y2);
        load.setResistance(ohms);
        return load;
    }

    private static CapacitorElm capacitor(int x, int y, int x2, int y2) {
        CapacitorElm result = new CapacitorElm(x, y);
        result.setPosition(x, y, x2, y2);
        result.capacitance = 1e-6;
        result.initialVoltage = 0;
        result.reset();
        return result;
    }

    private static void add(MeterSim sim, Vector<CircuitElm> fixture, CircuitElm element) {
        fixture.add(element);
        sim.elmList.add(element);
    }

    private static CircuitPostMeasurementEndpoint endpoint(CircuitElm element, int post) {
        return new CircuitPostMeasurementEndpoint(element, post);
    }

    private static void restored(MeterSim sim, GeneratedBoardInstance owner,
            Vector<CircuitElm> graph) {
        require(sim.getGeneratedBoardInstance() == owner && sim.elmList.equals(graph) &&
                !sim.activeMeasurementOverlay && sim.stopMessage == null &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "actual instrument transaction restores the current circuit and solver state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class LiteralProbe implements ProbeTarget {
        private final MeterSim sim;
        private final CircuitPostMeasurementEndpoint endpoint;
        LiteralProbe(MeterSim sim, CircuitPostMeasurementEndpoint endpoint) {
            this.sim = sim;
            this.endpoint = endpoint;
        }
        public boolean isValid() { return sim.containsElement(endpoint.getElement()); }
        public boolean isSameTarget(ProbeTarget other) { return other == this; }
        public Point getMarkerPoint() { return null; }
        public CircuitMeasurementEndpoint getMeasurementEndpoint() { return endpoint; }
    }

    private static final class LiteralCapacitorReadiness
            implements ActiveMeasurementReadinessCapability {
        private final CapacitorElm capacitor;
        private final String id;
        LiteralCapacitorReadiness(CapacitorElm capacitor, String id) {
            this.capacitor = capacitor;
            this.id = id;
        }
        public String getCapabilityId() { return id; }
        public ActiveMeasurementReadiness getActiveMeasurementReadiness(
                CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
                BoardPowerState power, boolean allSourcesOff) {
            if (power != BoardPowerState.UNPOWERED || !allSourcesOff)
                return ActiveMeasurementReadiness.POWER_OFF;
            return Math.abs(capacitor.getVoltageDiff()) > RESIDUAL_THRESHOLD ?
                ActiveMeasurementReadiness.DISCHARGE : ActiveMeasurementReadiness.READY;
        }
    }

    private static final class MeterSim extends CirSim {
        MeterSim() {
            gridSize = 16;
            gridMask = ~15;
            gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>();
            redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = .00005;
            adjustTimeStep = false;
        }
        @Override void runCircuit(boolean didAnalyze) { solverExecutor.advanceSteps(8); }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message;
            circuitMatrix = null;
            stopElm = element;
            setSimRunning(false);
            analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
