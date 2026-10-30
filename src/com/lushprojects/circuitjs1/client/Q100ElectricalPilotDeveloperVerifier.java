package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.user.client.Event;
import java.util.Vector;

/** Explicit Developer-only raw electrical reference; grants no family admission. */
final class Q100ElectricalPilotDeveloperVerifier {
    private Q100ElectricalPilotDeveloperVerifier() { }
    static void start(final CirSim sim, final String variantText, final String faultText,
            final String dtText, final String canaryText) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    Rb100PilotReference.require(TroubleshootBuild.isDeveloper() && sim.troubleshootDebug &&
                        sim.troubleshootQ100Verification, "explicit Developer route required");
                    Rb100PilotReference.require(!sim.developerVerifierRunning && sim.playerSessionController == null &&
                        !sim.activeMeasurementOverlay && sim.isGeneratedRuntimeSettled() &&
                        !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                        (sim.generationCoordinator == null || !sim.generationCoordinator.isRunning()),
                        "idle settled private workbench required");
                    Rb100Plan.Variant variant = parseVariant(variantText);
                    Rb100PilotFaults.Fault fault = faultText == null || "HEALTHY".equals(faultText) ?
                        null : Rb100PilotFaults.find(faultText);
                    double dt = dtText == null ? (fault == null ? .0001 : .00005) : Double.parseDouble(dtText);
                    Rb100PilotReference.require(dt == .0001 || dt == .00005, "unsupported reference timestep");
                    Rb100PilotReference.require(canaryText == null || "cancel".equals(canaryText) ||
                        "after-step".equals(canaryText) || "interaction-lock".equals(canaryText),
                        "unsupported reference canary");
                    new Runner(sim, variant, fault, dt, canaryText).begin();
                } catch (Throwable failure) {
                    publish("{\"status\":\"REJECTED_BEFORE_PRIVATE_GRAPH\",\"error\":" + quote(failure.getMessage()) +
                        ",\"cleanup\":\"NOT_APPLICABLE\"}");
                    CirSim.publishBrowserVerificationResult("FAIL:q100:preflight:" + failure.getMessage());
                }
            }
        });
    }
    private static Rb100Plan.Variant parseVariant(String text) {
        if (text == null) return Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR;
        for (Rb100Plan.Variant variant : Rb100Plan.Variant.values())
            if (variant.name().equals(text)) return variant;
        throw new IllegalArgumentException("Unknown RB100 structural variant");
    }
    private static final class Runner {
        final CirSim sim;
        final Rb100Plan.Variant variant;
        final Rb100PilotFaults.Fault fault;
        final double dt;
        final String canary;
        final long started = System.currentTimeMillis();
        final Vector<CircuitElm> predecessor;
        final String predecessorDump;
        final GeneratedBoardInstance predecessorOwner;
        final boolean predecessorRunning;
        final String diode, zener, transistor, led;
        PrivateSolverContext proof;
        Rb100ElectricalPilot.Candidate candidate;
        Rb100PilotReference reference;
        boolean installed, finished, injectedCancellation;
        String interactionLockJson;
        int turns;
        Runner(CirSim sim, Rb100Plan.Variant variant, Rb100PilotFaults.Fault fault, double dt, String canary) {
            this.sim = sim; this.variant = variant; this.fault = fault; this.dt = dt; this.canary = canary;
            predecessor = sim.elmList; predecessorDump = sim.dumpCircuit();
            predecessorOwner = sim.generatedBoardInstance; predecessorRunning = sim.simIsRunning();
            diode = DiodeElm.lastModelName; zener = ZenerElm.lastZenerModelName;
            transistor = TransistorElm.lastModelName; led = LEDElm.lastLEDModelName;
        }
        void begin() {
            try {
                proof = PrivateSolverContext.open(sim);
                sim.developerVerifierRunning = true;
                sim.timeStep = sim.maxTimeStep = sim.minTimeStep = dt;
                sim.adjustTimeStep = false;
                candidate = Rb100ElectricalPilot.construct(Rb100Plan.create(Rb100PilotReference.SEED, variant));
                GeneratedFaultEffect effect = fault == null ? null : Rb100PilotFaults.bind(candidate, fault);
                proof.install(candidate.elements()); installed = true;
                reference = new Rb100PilotReference(sim, candidate, fault, effect,
                    new Rb100PilotReference.Stepper() {
                        public void analyze() { proof.analyze(); }
                        public void advance(int count) { proof.advanceFor(count * dt); }
                    }, dt, started);
                CirSim.publishBrowserVerificationResult("RUNNING:q100");
                defer();
            } catch (Throwable failure) { finish(null, failure); }
        }
        void defer() {
            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                public void execute() {
                    if (finished) return;
                    try {
                        boolean done = reference.advance();
                        int turn = ++turns;
                        if (turn == 1 && "interaction-lock".equals(canary))
                            interactionLockJson = verifyInteractionLock();
                        if (turn == 2 && canary != null) {
                            injectedCancellation = "cancel".equals(canary) || "interaction-lock".equals(canary);
                            throw new IllegalStateException("q100-explicit-" + canary + "-after-accepted-step");
                        }
                        if (done) finish(reference.report(), null); else defer();
                    } catch (Throwable failure) { finish(null, failure); }
                }
            });
        }
        String verifyInteractionLock() {
            Rb100PilotReference.require(sim.solverExecutor.isUnavailable() && sim.elmList != null &&
                sim.elmList != predecessor && sim.generatedBoardInstance == null && CircuitElm.sim == sim,
                "interaction lock requires the owned private solver graph");
            final Vector<CircuitElm> graph = sim.elmList;
            final int countBefore = graph.size();
            Rb100PilotReference.require(countBefore > 1 && sim.undoStack != null,
                "interaction lock canary requires an editable private graph");
            final CircuitElm[] elements = new CircuitElm[countBefore];
            final boolean[] selected = new boolean[countBefore];
            graph.firstElement().setSelected(true);
            boolean hasUnselectedElement = false;
            for (int i = 0; i < countBefore; i++) {
                elements[i] = graph.elementAt(i);
                selected[i] = elements[i].isSelected();
                if (!selected[i]) hasUnselectedElement = true;
            }
            Rb100PilotReference.require(hasUnselectedElement,
                "interaction lock canary requires an unselected private element");
            final int undoDepthBefore = sim.undoStack.size();
            final double timeBefore = sim.t;
            final long acceptedStepsBefore = sim.a01AcceptedStepCount;
            final boolean runningBefore = sim.simIsRunning();

            Rb100PilotReference.require(sim.noEditCheckItem != null &&
                !sim.dialogIsShowing() && CirSim.scrollValuePopup == null,
                "wheel interaction lock canary requires an unobstructed private schematic");
            final ResistorElm wheelResistor = candidate.backing.get("RMON") instanceof ResistorElm ?
                (ResistorElm) candidate.backing.get("RMON") : null;
            Rb100PilotReference.require(wheelResistor != null && wheelResistor.getResistance() > 0 &&
                !Double.isNaN(wheelResistor.getResistance()) && !Double.isInfinite(wheelResistor.getResistance()),
                "wheel interaction lock canary requires the actual private RMON resistor");
            final double wheelRBefore = wheelResistor.getResistance();
            final boolean noEditBefore = sim.noEditCheckItem.getState();
            final int scopeBefore = sim.scopeSelected;
            final long zoomTimeBefore = sim.zoomTime;
            final CircuitElm mouseElmBefore = sim.getMouseElmForDeveloperVerification();
            final ScrollValuePopup popupBefore = CirSim.scrollValuePopup;
            final long wheelAttemptZoomTime = System.currentTimeMillis() - 5000L;
            boolean wheelAttemptBlocked = false;
            boolean popupUnchanged = false;
            boolean wheelUiStable = false;
            boolean nativeWheelPreviewBlocked;
            boolean nativeChangePreviewBlocked;
            double wheelRAfter = Double.NaN;
            int wheelDelta = 0;
            try {
                sim.noEditCheckItem.setState(false);
                sim.scopeSelected = -1;
                sim.zoomTime = wheelAttemptZoomTime;
                sim.restoreMouseElmForDeveloperVerification(wheelResistor);

                MouseWheelEvent directWheel = new MouseWheelEvent() { };
                directWheel.setNativeEvent(createWheelNativeEvent());
                wheelDelta = directWheel.getDeltaY();
                Rb100PilotReference.require(wheelDelta != 0,
                    "wheel interaction lock canary requires a nonzero native wheel delta");
                sim.onMouseWheel(directWheel);
                wheelRAfter = wheelResistor.getResistance();
                popupUnchanged = CirSim.scrollValuePopup == popupBefore;
                wheelUiStable = !sim.noEditCheckItem.getState() && sim.scopeSelected == -1 &&
                    sim.zoomTime == wheelAttemptZoomTime &&
                    sim.getMouseElmForDeveloperVerification() == wheelResistor;
                wheelAttemptBlocked = wheelRBefore == wheelRAfter && popupUnchanged && wheelUiStable;

                nativeWheelPreviewBlocked = !Event.fireNativePreviewEvent(createWheelNativeEvent());
                nativeChangePreviewBlocked = !Event.fireNativePreviewEvent(Document.get().createChangeEvent());
            } finally {
                ScrollValuePopup popupAfterAttempt = CirSim.scrollValuePopup;
                try {
                    if (wheelResistor.getResistance() != wheelRBefore)
                        wheelResistor.setResistance(wheelRBefore);
                } finally {
                    try {
                        if (popupAfterAttempt != popupBefore) {
                            CirSim.scrollValuePopup = popupBefore;
                            if (popupAfterAttempt != null) popupAfterAttempt.hide();
                        }
                    } finally {
                        try {
                            sim.noEditCheckItem.setState(noEditBefore);
                            sim.scopeSelected = scopeBefore;
                            sim.zoomTime = zoomTimeBefore;
                        } finally {
                            sim.restoreMouseElmForDeveloperVerification(mouseElmBefore);
                        }
                    }
                }
            }

            sim.menuPerformed("key", "selectAll");
            sim.menuPerformed("elm", "delete");
            sim.toggleSimRunningFromUserInput();

            boolean graphIdentityStable = sim.elmList == graph;
            boolean elementIdentityStable = graph.size() == countBefore;
            boolean selectionStable = graph.size() == countBefore;
            for (int i = 0; i < countBefore && graph.size() == countBefore; i++) {
                elementIdentityStable &= graph.elementAt(i) == elements[i];
                selectionStable &= elements[i].isSelected() == selected[i];
            }
            boolean deletionAttemptBlocked = graphIdentityStable && elementIdentityStable &&
                sim.undoStack.size() == undoDepthBefore;
            boolean selectionAttemptBlocked = selectionStable;
            boolean runToggleBlocked = sim.simIsRunning() == runningBefore;
            boolean privateOwnerStillUnavailable = sim.solverExecutor.isUnavailable();
            double timeAfter = sim.t;
            long acceptedStepsAfter = sim.a01AcceptedStepCount;
            int undoDepthAfter = sim.undoStack.size();
            int countAfter = graph.size();
            boolean privateCircuitStable = graphIdentityStable && elementIdentityStable && selectionStable &&
                timeAfter == timeBefore && acceptedStepsAfter == acceptedStepsBefore &&
                sim.simIsRunning() == runningBefore && undoDepthAfter == undoDepthBefore &&
                wheelAttemptBlocked && wheelUiStable && nativeWheelPreviewBlocked &&
                nativeChangePreviewBlocked && popupUnchanged;
            boolean allBlocked = graphIdentityStable && elementIdentityStable && selectionStable &&
                deletionAttemptBlocked && selectionAttemptBlocked && runToggleBlocked &&
                privateOwnerStillUnavailable && timeAfter == timeBefore &&
                acceptedStepsAfter == acceptedStepsBefore && sim.simIsRunning() == runningBefore &&
                undoDepthAfter == undoDepthBefore && privateCircuitStable;
            Rb100PilotReference.require(allBlocked, "normal schematic input escaped private solver interaction lock");
            return "{\"status\":\"PASS\",\"selectionAttemptBlocked\":true,\"deletionAttemptBlocked\":true," +
                "\"runToggleBlocked\":true,\"graphIdentityStable\":true,\"elementIdentityStable\":true," +
                "\"elementCountBefore\":" + countBefore + ",\"elementCountAfter\":" + countAfter +
                ",\"selectionStable\":true,\"undoDepthBefore\":" + undoDepthBefore +
                ",\"undoDepthAfter\":" + undoDepthAfter + ",\"simulationTimeBefore\":" + timeBefore +
                ",\"simulationTimeAfter\":" + timeAfter + ",\"acceptedStepCountBefore\":" + acceptedStepsBefore +
                ",\"acceptedStepCountAfter\":" + acceptedStepsAfter + ",\"runningBefore\":" + runningBefore +
                ",\"runningAfter\":" + sim.simIsRunning() +
                ",\"privateOwnerStillUnavailable\":true," +
                "\"wheelAttemptBlocked\":" + wheelAttemptBlocked +
                ",\"nativeWheelPreviewBlocked\":" + nativeWheelPreviewBlocked +
                ",\"nativeChangePreviewBlocked\":" + nativeChangePreviewBlocked +
                ",\"privateCircuitStable\":" + privateCircuitStable +
                ",\"wheelRBefore\":" + wheelRBefore + ",\"wheelRAfter\":" + wheelRAfter +
                ",\"wheelDelta\":" + wheelDelta + ",\"popupUnchanged\":" + popupUnchanged + "}";
        }
        void finish(String report, Throwable failure) {
            if (finished) return;
            finished = true;
            if (report == null && reference != null) {
                try { report = reference.diagnosticReport(); }
                catch (Throwable problem) {
                    if (failure == null) failure = problem; else failure.addSuppressed(problem);
                }
            }
            long operationMillis = System.currentTimeMillis() - started;
            long cleanupStarted = System.currentTimeMillis();
            Throwable cleanupFailure = null;
            try { if (proof != null) proof.close(); }
            catch (Throwable problem) { cleanupFailure = problem; }
            try { if (!installed && candidate != null) candidate.dispose(); }
            catch (Throwable problem) {
                if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
            }
            boolean safePredecessorRestored = false;
            try {
                Rb100PilotReference.require(proof != null && cleanupFailure == null && sim.elmList == predecessor &&
                    sim.generatedBoardInstance == predecessorOwner && CircuitElm.sim == sim &&
                    !sim.solverExecutor.isUnavailable() && sim.simIsRunning() == predecessorRunning &&
                    !sim.developerVerifierRunning, "private reference predecessor owner safely restored");
                safePredecessorRestored = true;
            } catch (Throwable problem) {
                if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
            }
            if (safePredecessorRestored) {
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener;
                TransistorElm.lastModelName = transistor; LEDElm.lastLEDModelName = led;
                try {
                    Rb100PilotReference.require(predecessorDump.equals(sim.dumpCircuit()),
                        "exact predecessor restored after private reference");
                } catch (Throwable problem) {
                    if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
                }
            }
            long cleanupMillis = System.currentTimeMillis() - cleanupStarted;
            if (cleanupMillis > 120000 && cleanupFailure == null)
                cleanupFailure = new IllegalStateException("RB100 private cleanup exceeded 120 seconds");
            String status = cleanupFailure != null ? "FAIL_CLEANUP" : failure != null ?
                (injectedCancellation ? "CANCELLED_REFERENCE" : "FAIL_REFERENCE") : "PASS_ELECTRICAL_REFERENCE_ONLY";
            publish("{\"status\":" + quote(status) + ",\"operationMs\":" + operationMillis +
                ",\"cleanupMs\":" + cleanupMillis + ",\"cleanup\":" + quote(cleanupFailure == null ? "PASS" : "FAIL") +
                ",\"error\":" + quote(failure == null ? null : failure.getMessage()) +
                ",\"cleanupError\":" + quote(cleanupFailure == null ? null : cleanupFailure.getMessage()) +
                ",\"canary\":" + quote(canary) + ",\"reference\":" + (report == null ? "null" : report) +
                ("interaction-lock".equals(canary) ? ",\"interactionLock\":" +
                    (interactionLockJson == null ? "null" : interactionLockJson) : "") + "}");
            CirSim.publishBrowserVerificationResult((failure == null && cleanupFailure == null ? "PASS" :
                injectedCancellation && cleanupFailure == null ? "CANCELLED" : "FAIL") + ":q100");
        }
    }
    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
    private static native void publish(String evidence) /*-{
        $doc.documentElement.setAttribute("data-tsj-q100-report", evidence);
    }-*/;
    private static native NativeEvent createWheelNativeEvent() /*-{
        var event = $doc.createEvent("MouseEvents");
        event.initMouseEvent("mousewheel", true, true, $wnd, 3,
            0, 0, 0, 0, false, false, false, false, 0, null);
        try {
            Object.defineProperty(event, "wheelDelta", {value: -120});
        } catch (failure) {
            event.wheelDelta = -120;
        }
        return event;
    }-*/;
}
