package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Timer;

/** Compiled installed-owner proof. Programmatic checks are distinct from visible mouse evidence. */
final class E05InstalledRuntimeVerifier {
    interface Completion { void complete(String report, Throwable failure); }
    private E05InstalledRuntimeVerifier() { }
    static void start(CirSim sim, Completion completion) { start(sim, false, completion); }
    static void start(final CirSim sim, final boolean visualHold, final Completion completion) {
        final Run run = new Run(sim, visualHold, completion);
        new Timer() { public void run() { run.begin(); } }.schedule(0);
    }

    private static final class Run {
        private static final int VISUAL_HOLD_MILLIS = 120000;
        private final CirSim sim;
        private final boolean visualHold;
        private final Completion completion;
        private GeneratedBoardInstance original;
        private Task41SimulationSnapshot saved;
        private E05InstalledFixture fixture;
        private boolean finished, restored, disposed;
        private int checks, renderedEndpoints;
        private double primaryRms, secondaryRms, outputDc, residualDc, dischargedDc, resistance;
        private String pendingReadiness, residualReadiness, finalReadiness;
        private String dischargeEvidence = "null", readinessEvidence = "null";
        Run(CirSim sim, boolean visualHold, Completion completion) {
            this.sim = sim; this.visualHold = visualHold; this.completion = completion;
        }
        void begin() {
            try {
                require(sim != null && sim.troubleshootDebug && sim.troubleshootE05Verification &&
                    sim.developerVerifierRunning && sim.isGeneratedRuntimeSettled(), "explicit settled E05 route");
                original = sim.getGeneratedBoardInstance();
                saved = Task41SimulationSnapshot.capture(sim);
                fixture = new E05InstalledFixture();
                saved.beginProof(sim);
                // Preserve the original containers before the ordinary attachment path clears them.
                sim.elmList = new Vector<CircuitElm>(); sim.adjustables = new Vector<Adjustable>();
                sim.undoStack = new Vector<String>(); sim.redoStack = new Vector<String>();
                sim.timeStep = sim.minTimeStep = sim.maxTimeStep = E05AcInputModel.MAX_TIME_STEP_SECONDS;
                sim.adjustTimeStep = false;
                sim.installGeneratedBoardForDeveloperVerification(fixture.instance);
                sim.setSimRunning(true); settle(); sim.setSimRunning(false);
                require(sim.pcbWorkbenchController != null, "real PCB renderer installed");
                verifyConstruction();
                verifyRenderedEndpoints();
                power(BoardPowerState.POWERED);
                advance(.5);
                primaryRms = ac("JAC.1", "JAC.2");
                require(primaryRms > 110 && primaryRms < 125, "loaded primary AC RMS convention: " + primaryRms);
                secondaryRms = ac("T1.S1", "T1.S2");
                require(secondaryRms > 9 && secondaryRms < 13, "actual isolated secondary AC RMS: " + secondaryRms);
                outputDc = dc("JOUT.1", "JOUT.2");
                require(outputDc > 12 && outputDc < 18, "real rectified output DC: " + outputDc);
                double reversed = dc("JOUT.2", "JOUT.1");
                require(reversed < -12 && Math.abs(reversed + outputDc) < 1,
                    "DC polarity follows red minus black");
                verifyReferenceRefusal();
                verifyReadiness();
                verifyRuntime();
                if (sim.troubleshootE05ForcedFailure)
                    throw new IllegalStateException("e05-explicit-failure-canary");
                if (visualHold) beginVisualHold(); else finish(null);
            } catch (Throwable failure) { finish(failure); }
        }

        private void verifyConstruction() {
            GeneratedBoardInstance owner = fixture.instance;
            PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
            require(owner.isDeveloperOnlyFaultRoute() && owner.getBoard().getComponentIds().size() == 11 &&
                owner.getFaultCandidates().isEmpty(), "fixed eleven-package developer fixture");
            require(owner.getChallengeDefinition() == null && sim.getGeneratedChallengeController() == null,
                "bench is not a customer challenge");
            require(runtime.getInstalledPart("C1") instanceof PhysicalCapacitorPart &&
                runtime.getInstalledPart("D1") instanceof PhysicalDiodePart &&
                runtime.getInstalledPart("RBLEED") instanceof PhysicalResistorPart &&
                runtime.getInstalledPart("RLOAD") instanceof PhysicalResistorPart,
                "current typed physical owners installed");
            require(runtime.getWorkbenchPartsProvider("T1") == null &&
                runtime.getInstalledPart("T1").getPackage() == PhysicalPackages.E05_ISOLATION_TRANSFORMER_4,
                "transformer is honestly fixed and unserviceable");
            for (int i = 0; i < 4; i++) {
                String pad = "T1." + new String[] {"P1", "P2", "S1", "S2"}[i];
                CircuitPostMeasurementEndpoint endpoint = fixture.endpoint(pad);
                require(endpoint.getElement() == fixture.electrical.transformer &&
                    endpoint.getPostIndex() == new int[] {0, 2, 1, 3}[i], "exact transformer post: " + pad);
                require((i < 2 ? "PRIMARY" : "SECONDARY").equals(
                    owner.getBoard().getPlacementConstraints().domainForPad(owner.getBoard(), pad)),
                    "exact transformer terminal domain: " + pad);
            }
            PowerInputNameplate name = owner.getPhysicalSpecifications().getPowerInputNameplate(E05InstalledFixture.INPUT);
            require(name.getConvention() == PowerInputNameplate.Convention.AC_RMS &&
                name.getNominalVoltage() == 120 && name.getFrequencyHz() == 60 &&
                "120 VAC RMS / 60 Hz".equals(name.getDisplayLabel()), "explicit installed source nameplate");
            require(fixture.electrical.transformer.inductance == E05ElectricalFixtures.PRIMARY_HENRIES &&
                fixture.electrical.transformer.ratio == E05ElectricalFixtures.SECONDARY_OVER_PRIMARY_TURNS &&
                fixture.electrical.transformer.couplingCoef == E05ElectricalFixtures.COUPLING &&
                fixture.electrical.bulk.capacitance == E05ElectricalFixtures.SECONDARY_CAP_FARADS &&
                fixture.electrical.bleed.resistance == E05ElectricalFixtures.SECONDARY_BLEED_OHMS &&
                fixture.electrical.load.resistance == E05ElectricalFixtures.SECONDARY_LOAD_OHMS &&
                fixture.electrical.fuse.resistance == E05AcInputModel.FUSE_OHMS &&
                fixture.electrical.fuse.i2t == E05AcInputModel.FUSE_I2T_AMP_SQUARED_SECONDS,
                "full causal model parameters survive typed construction");
            require(owner.getExternalPowerBindings().getBinding(E05InstalledFixture.INPUT) == fixture.electrical.input.binding &&
                owner.getBoard().getPowerInputIds().size() == 1, "one real two-pole input binding");
            boolean rejected = false;
            try { GeneratedDiagnosticSolvabilityAdmission.validate(owner); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "developer fixture cannot become normal diagnostic admission");
            verifyRuntime();
        }
        private void verifyRenderedEndpoints() {
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
            for (PcbBoardSide face : new PcbBoardSide[] {PcbBoardSide.TOP, PcbBoardSide.BOTTOM}) {
                renderer.setViewingFace(face);
                for (String pad : fixture.instance.getBoard().getPadIds()) {
                    Rectangle bounds = renderer.getPadProbeBoundsForDeveloperVerification(pad);
                    ProbeTarget target = renderer.findProbeTarget(sim, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
                    require(target != null && target.isValid() &&
                        target.getMeasurementEndpoint() == fixture.endpoint(pad), "rendered pad endpoint " + face + " " + pad);
                    renderedEndpoints++;
                }
            }
            renderer.setViewingFace(PcbBoardSide.TOP);
            PhysicalPartRenderGeometry geometry = renderer.getInstalledGeometryForDeveloperVerification("T1");
            Rectangle body = geometry.getBodyBounds();
            int x = body.x + body.width / 2, y = body.y + body.height / 2;
            require("T1".equals(renderer.findComponentId(x, y)) && renderer.findProbeTarget(sim, x, y) == null,
                "insulated transformer body is selectable without a phantom electrical endpoint");
        }
        private void verifyReferenceRefusal() {
            CircuitPostMeasurementEndpoint red = fixture.endpoint("T1.P2"), black = fixture.endpoint("T1.S2");
            require(fixture.power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                fixture.endpoint("T1.S1"), black).admitsReading(), "secondary winding differential admission");
            require(!fixture.power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                black, fixture.endpoint("JOUT.2")).admitsReading(), "AC return not invented as rectifier return");
            require(!fixture.power.assessReference(MeasurementReferencePolicy.Mode.EARTH_REFERENCED, red,
                fixture.endpoint("T1.P1")).admitsReading(), "numerical reference is not physical earth");
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            double beforeTime = sim.t;
            Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
            VoltageMeasurementResult dc = sim.measureDcVoltageResult(red, black);
            VoltageMeasurementResult ac = sim.measureAcVoltage(red, black);
            require(dc.getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED && !dc.isNumeric() &&
                ac.getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED && !ac.isNumeric(),
                "AC and DC give typed cross-domain refusal");
            require(sim.observeDifferentialVoltage(red, black) == null && sim.t == beforeTime &&
                graph.equals(sim.elmList) && !sim.activeMeasurementOverlay,
                "reference refusal installs no meter burden or scope and advances no solver time");
            ac("T1.P2", "T1.S2");
            require(Double.isNaN(sim.instrumentController.getLatestAcVoltageForDeveloperVerification()) &&
                sim.instrumentController.getReadingForDeveloperVerification().contains("REF?"),
                "player AC mode clears old numeric value on cross-domain refusal");
            dc("T1.P2", "T1.S2");
            require(Double.isNaN(sim.instrumentController.getLatestDcVoltageForDeveloperVerification()) &&
                sim.instrumentController.getReadingForDeveloperVerification().contains("REF?"),
                "player DC mode clears old numeric value on cross-domain refusal");
        }
        private void verifyReadiness() {
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            double charged = fixture.electrical.bulk.getVoltageDiff();
            require(charged > 12, "physically charged storage before OFF");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            require(fixture.electrical.bulk.getVoltageDiff() == charged &&
                fixture.electrical.input.linePole.position == 1 && fixture.electrical.input.returnPole.position == 1,
                "OFF isolates both source poles without clearing capacitor energy");
            ActiveMeasurementReadiness pending = readiness(); pendingReadiness = pending.name();
            require(!pending.isReady(), "OFF requires a fresh accepted energy observation");
            settle();
            residualDc = dc("JOUT.1", "JOUT.2");
            ActiveMeasurementReadiness residual = readiness(); residualReadiness = residual.name();
            require(residualDc > .25 && residual == ActiveMeasurementReadiness.DISCHARGE &&
                fixture.power.assessPower().areAllSourcesIsolated(), "all isolated with real residual storage");
            selectResistance();
            require(Double.isNaN(sim.instrumentController.getLatestResistanceReadingForDeveloperVerification()) &&
                sim.instrumentController.getReadingForDeveloperVerification().contains("DISCHARGE") &&
                !sim.activeMeasurementOverlay, "active meter refuses charged storage without injecting a source");
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            PhysicalBoardRuntime physical = fixture.instance.getPhysicalBoardRuntime();
            require(((PhysicalCapacitorPart)physical.getInstalledPart("C1")).getElement() == fixture.electrical.bulk &&
                ((PhysicalResistorPart)physical.getInstalledPart("RBLEED")).getElement() == fixture.electrical.bleed &&
                ((PhysicalResistorPart)physical.getInstalledPart("RLOAD")).getElement() == fixture.electrical.load &&
                sim.elmList.contains(fixture.electrical.bulk) && sim.elmList.contains(fixture.electrical.bleed) &&
                sim.elmList.contains(fixture.electrical.load), "RC observations use the exact current typed elements");
            double transientTime = sim.t, transientVoltage = fixture.electrical.bulk.getVoltageDiff();
            double transientPrimary = fixture.electrical.transformer.current[0];
            double transientSecondary = fixture.electrical.transformer.current[1];
            double transientMagnetic = magneticEnergy();
            // Initial OFF residue is already proved above. Coupled magnetic energy
            // can still charge the bulk; use the same bounded settling window as
            // the independently qualified raw graph before the pure-RC interval.
            advance(.05);
            double earlier = fixture.electrical.bulk.getVoltageDiff(), earlierTime = sim.t;
            double earlierPrimary = fixture.electrical.transformer.current[0];
            double earlierSecondary = fixture.electrical.transformer.current[1];
            double earlierMagnetic = magneticEnergy();
            advance(.25);
            double later = fixture.electrical.bulk.getVoltageDiff(), elapsed = sim.t - earlierTime;
            double expectedRatio = Math.exp(-elapsed / (.000470 / (1 / 10000.0 + 1 / 1000.0)));
            dischargeEvidence = "{\"declaredSettlingSeconds\":0.05,\"acceptedSettlingSeconds\":" + (earlierTime - transientTime) +
                ",\"expectedTauSeconds\":" + (.000470 / (1 / 10000.0 + 1 / 1000.0)) +
                ",\"transientStartTimeSeconds\":" + transientTime +
                ",\"transientStartVolts\":" + transientVoltage + ",\"transientPrimaryAmps\":" + transientPrimary +
                ",\"transientSecondaryAmps\":" + transientSecondary + ",\"transientMagneticJoules\":" + transientMagnetic +
                ",\"rcStartTimeSeconds\":" + earlierTime + ",\"rcEndTimeSeconds\":" + sim.t +
                ",\"rcStartVolts\":" + earlier + ",\"rcEndVolts\":" + later + ",\"elapsedSeconds\":" + elapsed +
                ",\"observedRatio\":" + (later / earlier) + ",\"expectedRatio\":" + expectedRatio +
                ",\"rcStartPrimaryAmps\":" + earlierPrimary + ",\"rcStartSecondaryAmps\":" + earlierSecondary +
                ",\"rcStartMagneticJoules\":" + earlierMagnetic +
                ",\"rcEndPrimaryAmps\":" + fixture.electrical.transformer.current[0] +
                ",\"rcEndSecondaryAmps\":" + fixture.electrical.transformer.current[1] +
                ",\"rcEndMagneticJoules\":" + magneticEnergy() +
                ",\"capacitanceFarads\":" + fixture.electrical.bulk.capacitance +
                ",\"capacitorFlags\":" + fixture.electrical.bulk.flags +
                ",\"bleederOhms\":" + fixture.electrical.bleed.resistance +
                ",\"loadOhms\":" + fixture.electrical.load.resistance +
                ",\"typedElementAliasesCurrent\":true,\"rcEndLoadAmps\":" + fixture.electrical.load.getCurrent() +
                ",\"rcEndBleederAmps\":" + fixture.electrical.bleed.getCurrent() + "}";
            require(later > 0 && later < earlier && Math.abs(later / earlier - expectedRatio) < .04,
                "accepted solver discharge follows the independent parallel-RC expectation: " + dischargeEvidence);
            advance(3);
            ActiveMeasurementReadiness beforeFinalDc = readiness();
            dischargedDc = dc("JOUT.1", "JOUT.2");
            ActiveMeasurementReadiness afterFinalDc = readiness();
            finalReadiness = afterFinalDc.name();
            PowerOperatingAssessment finalPower = fixture.power.assessPower();
            readinessEvidence = "{\"sampleTimeSeconds\":" + sim.t +
                ",\"beforeFinalDc\":" + quote(beforeFinalDc.name()) + ",\"afterFinalDc\":" + quote(finalReadiness) +
                ",\"canonicalAcceptedObservation\":" + (sim.solverExecutor.observation(fixture.instance) != null) +
                ",\"bulkVolts\":" + fixture.electrical.bulk.getVoltageDiff() + ",\"meterDcVolts\":" + dischargedDc +
                ",\"powerReadiness\":" + quote(finalPower.getReadiness().name()) +
                ",\"powerRailConditions\":" + quote(finalPower.getRailConditions().toString()) +
                ",\"powerIssues\":" + quote(finalPower.getIssues().toString()) + "}";
            require(Math.abs(dischargedDc) <= ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS &&
                afterFinalDc == ActiveMeasurementReadiness.READY,
                "stored energy reaches actual READY after accepted steps: " + readinessEvidence);
            require(Math.abs(fixture.electrical.transformer.current[0]) < .00001 &&
                Math.abs(fixture.electrical.transformer.current[1]) < .00001,
                "READY observation also has negligible winding currents");
            selectResistance(); resistance = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
            require(PowerDomainContract.finite(resistance) && resistance >= 0 && !sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification(), "actual active meter executes and restores discharged owner");
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            advance(.01);
        }
        private double magneticEnergy() {
            double primary = fixture.electrical.transformer.current[0], secondary = fixture.electrical.transformer.current[1];
            return 2 * primary * primary + .02 * secondary * secondary + .3996 * primary * secondary;
        }
        private ActiveMeasurementReadiness readiness() {
            return sim.getActiveMeasurementReadiness(fixture.endpoint("JOUT.1"), fixture.endpoint("JOUT.2"));
        }
        private double ac(String red, String black) {
            sim.instrumentController.clearTargets(); sim.instrumentController.activateAcVoltageModeForDeveloperVerification();
            place(red, black);
            require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "AC meter burden restores canonical graph");
            return sim.instrumentController.getLatestAcVoltageForDeveloperVerification();
        }
        private double dc(String red, String black) {
            sim.instrumentController.clearTargets(); sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
            place(red, black);
            require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
                "DC meter burden restores canonical graph");
            return sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        }
        private void selectResistance() {
            sim.instrumentController.clearTargets(); sim.instrumentController.activateResistanceModeForDeveloperVerification();
            place("JOUT.1", "JOUT.2");
        }
        private void place(String red, String black) {
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, target(red));
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_RIGHT, target(black));
        }
        private ProbeTarget target(String pad) {
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
            Rectangle bounds = renderer.getPadProbeBoundsForDeveloperVerification(pad);
            ProbeTarget target = renderer.findProbeTarget(sim, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
            require(target != null && target.isValid() && target.getMeasurementEndpoint() == fixture.endpoint(pad),
                "real renderer hit target: " + pad);
            return target;
        }
        private void power(BoardPowerState state) {
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            sim.setBoardPowerState(state); settle();
            require(sim.getBoardPowerController().getState() == state, "ordinary power command applied");
        }
        private void advance(double seconds) {
            require(sim.getGeneratedBoardInstance() == fixture.instance, "current fixture owns solver advance");
            sim.solverExecutor.advanceFor(seconds);
            require(sim.getGeneratedBoardInstance() == fixture.instance, "current fixture retains accepted solver advance");
            // Match the ordinary generated temporal owner: accepted solver time
            // must reach its installed capability lifecycle before readiness.
            fixture.instance.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
            require(sim.stopMessage == null && sim.isGeneratedRuntimeSettled(), "bounded real solver operation settled");
        }
        private void settle() {
            boolean wasRunning = sim.simIsRunning(); sim.setSimRunning(true);
            try {
                for (int i = 0; i < 40; i++) {
                    sim.updateCircuit();
                    require(sim.stopMessage == null, "ordinary installed solver converges");
                    if (sim.isGeneratedRuntimeSettled()) return;
                    if (!sim.analyzeFlag && !sim.dcAnalysisFlag && sim.generatedBoardVerificationAnalyzed &&
                            !sim.generatedVerificationRunning && !sim.activeMeasurementOverlay)
                        sim.solverExecutor.advanceSteps(1);
                }
                throw new IllegalStateException("E05 installed runtime exceeded 40 settlement attempts");
            } finally { sim.setSimRunning(wasRunning); }
        }
        private void verifyRuntime() {
            GeneratedRuntimeInvariant.verify(sim, fixture.instance, sim.getBoardModificationController(), sim.elmList);
            require(PcbConductorProjection.audit(fixture.instance, sim.elmList).pads == fixture.instance.getBoard().getPadIds().size(),
                "all physical copper endpoints agree with actual solver islands");
        }
        private void beginVisualHold() {
            power(BoardPowerState.POWERED); advance(.5);
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            sim.pcbWorkbenchController.getRenderer().setViewingFace(PcbBoardSide.TOP);
            sim.pcbWorkbenchController.attachToSidebar(sim.verticalPanel);
            require(sim.isChallengeInteractionEnabled() && sim.instrumentController.isInteractionEnabledForDeveloperVerification(),
                "ordinary visible power and instrument UI actionable");
            sim.refreshGeneratedUiForDeveloperVerification(); sim.setSimRunning(true); sim.repaint();
            // Freeze both face projections once. The visible test uses ordinary face
            // controls and keeps this initial viewport; no timer polls solver state.
            String top = visualPads(PcbBoardSide.TOP), bottom = visualPads(PcbBoardSide.BOTTOM);
            sim.pcbWorkbenchController.getRenderer().setViewingFace(PcbBoardSide.TOP);
            visual("{\"phase\":\"HOLD\",\"fixture\":\"E05_DEVELOPER_BENCH\",\"holdMillis\":" +
                VISUAL_HOLD_MILLIS + ",\"sourceNameplate\":\"120 VAC RMS / 60 Hz\",\"geometryGuided\":true," +
                "\"canvasWidth\":" + sim.cv.getCoordinateSpaceWidth() + ",\"canvasHeight\":" + sim.cv.getCoordinateSpaceHeight() +
                ",\"initialFace\":\"TOP\",\"faces\":{\"TOP\":{" + top + "},\"BOTTOM\":{" + bottom + "}}}");
            new Timer() { public void run() { finish(null); } }.schedule(VISUAL_HOLD_MILLIS);
        }
        private String visualPads(PcbBoardSide face) {
            PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
            renderer.setViewingFace(face);
            StringBuilder pads = new StringBuilder();
            for (String pad : fixture.instance.getBoard().getPadIds()) {
                if (pads.length() != 0) pads.append(',');
                Rectangle bounds = renderer.getPadProbeBoundsForDeveloperVerification(pad);
                pads.append(quote(pad)).append(":{\"x\":").append(bounds.x + bounds.width / 2)
                    .append(",\"y\":").append(bounds.y + bounds.height / 2).append(",\"width\":").append(bounds.width)
                    .append(",\"height\":").append(bounds.height).append('}');
            }
            return "\"pads\":{" + pads + "}";
        }
        private void finish(Throwable failure) {
            if (finished) return; finished = true;
            Throwable result = failure;
            ActiveMeasurementStimulus finalFixtureStimulus = fixture != null &&
                sim.getGeneratedBoardInstance() == fixture.instance ? sim.lastActiveMeasurementStimulus : null;
            if (saved != null && fixture != null && sim.getGeneratedBoardInstance() != fixture.instance &&
                    sim.getGeneratedBoardInstance() != original)
                result = retain(result, new IllegalStateException("E05 fixture superseded; successor preserved"));
            try {
                if (saved != null && (fixture == null || sim.getGeneratedBoardInstance() == fixture.instance ||
                        sim.getGeneratedBoardInstance() == original)) {
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                    saved.restore(sim); saved.assertRestored(sim);
                    require(sim.getGeneratedBoardInstance() == original && !sim.activeMeasurementOverlay,
                        "exact original owner and meter state restored");
                    restored = true;
                }
            } catch (Throwable cleanup) { result = retain(result, cleanup); }
            try {
                if (fixture != null && sim.getGeneratedBoardInstance() != fixture.instance) {
                    for (CircuitElm element : fixture.instance.getSimulationElements())
                        require(!sim.elmList.contains(element), "retired fixture element absent from successor graph");
                    fixture.instance.getExternalPowerBindings().setConnected(false);
                    for (CircuitElm element : fixture.instance.getSimulationElements()) element.delete();
                    disposed = true;
                    require(fixture.power.getActiveMeasurementReadiness(fixture.endpoint("JOUT.1"), fixture.endpoint("JOUT.2"),
                        BoardPowerState.UNPOWERED, true) == ActiveMeasurementReadiness.UNKNOWN,
                        "retired E05 owner cannot authorize active measurement");
                    require(!fixture.power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                        fixture.endpoint("JOUT.1"), fixture.endpoint("JOUT.2")).admitsReading(),
                        "retired E05 owner cannot admit reference");
                }
            } catch (Throwable cleanup) { result = retain(result, cleanup); }
            boolean meterClean = false;
            try {
                meterClean = sim != null && !sim.activeMeasurementOverlay &&
                    sim.circuitMatrix != null && sim.nodeList != null && sim.voltageSources != null &&
                    stimulusAbsent(finalFixtureStimulus);
                require(meterClean, "temporary meter and canonical solver restored on completion");
            } catch (Throwable cleanup) { result = retain(result, cleanup); }
            if (visualHold) visual("{\"phase\":\"" + (restored ? "RESTORED" : "CANCELLED") + "\",\"ownerRestored\":" + restored + "}");
            String report = result == null ? "{\"protocol\":\"TSJ-E05-INSTALLED-1\",\"status\":\"PASS\",\"assertions\":" + checks +
                ",\"packages\":11,\"renderedEndpoints\":" + renderedEndpoints + ",\"primaryRmsVolts\":" + primaryRms +
                ",\"secondaryRmsVolts\":" + secondaryRms + ",\"rectifiedDcVolts\":" + outputDc +
                ",\"residualDcVolts\":" + residualDc + ",\"dischargedDcVolts\":" + dischargedDc +
                ",\"activeResistanceOhms\":" + resistance + ",\"pendingReadiness\":" + quote(pendingReadiness) +
                ",\"residualReadiness\":" + quote(residualReadiness) + ",\"finalReadiness\":" + quote(finalReadiness) +
                ",\"sourceNameplate\":\"120 VAC RMS / 60 Hz\",\"ownerRestored\":" + restored +
                ",\"candidateDisposed\":" + disposed + ",\"referenceRefusal\":true,\"temporaryMeterClean\":" + meterClean + "," +
                "\"fixedTransformer\":true,\"visualHoldMillis\":" + (visualHold ? VISUAL_HOLD_MILLIS : 0) + "}" :
                "{\"protocol\":\"TSJ-E05-INSTALLED-1\",\"status\":\"FAIL\",\"assertions\":" + checks +
                ",\"error\":" + quote(result.toString()) + ",\"ownerRestored\":" + restored +
                ",\"candidateDisposed\":" + disposed + ",\"temporaryMeterClean\":" + meterClean +
                ",\"dischargedDcVolts\":" + (finalReadiness == null ? "null" : Double.toString(dischargedDc)) +
                ",\"pendingReadiness\":" + quote(pendingReadiness) + ",\"residualReadiness\":" + quote(residualReadiness) +
                ",\"finalReadiness\":" + quote(finalReadiness) + "}";
            report = report.substring(0, report.length() - 1) + ",\"discharge\":" + dischargeEvidence +
                ",\"readiness\":" + readinessEvidence + "}";
            completion.complete(report, result);
        }
        private boolean stimulusAbsent(ActiveMeasurementStimulus stimulus) {
            if (stimulus == null) return true;
            CircuitElm[] temporary = stimulus.getTemporaryElements();
            if (temporary == null) return false;
            for (CircuitElm element : temporary) {
                if (element == null || sim.elmList.contains(element)) return false;
                for (CircuitElm source : sim.voltageSources) if (source == element) return false;
                for (CircuitNode node : sim.nodeList)
                    for (CircuitNodeLink link : node.links) if (link.elm == element) return false;
            }
            return true;
        }
        private void require(boolean passed, String reason) {
            checks++; if (!passed) throw new IllegalStateException("E05 installed runtime: " + reason);
        }
    }
    private static Throwable retain(Throwable prior, Throwable next) {
        if (prior == null) return next;
        if (prior != next) prior.addSuppressed(next);
        return prior;
    }
    private static native String quote(String value) /*-{
        return JSON.stringify(value);
    }-*/;
    private static native void visual(String value) /*-{
        $doc.documentElement.setAttribute("data-tsj-e05-visual", value);
    }-*/;
}
