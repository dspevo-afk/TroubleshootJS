package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Exact owner boundary for a bounded Task 41 proof.
 *
 * A proof may replace the simulator's active generated graph many times, but
 * it must never replace the player's board identity.  This package-private
 * snapshot keeps the mutable CircuitJS lists and runtime references together
 * so restoration cannot accidentally combine an old controller with a newly
 * generated board.
 */
final class Task41SimulationSnapshot {
    static final int RESTORE_FAILURE_CONTROLLER_DISPOSAL = 1;
    static final int RESTORE_FAILURE_CONTROLLER_ASSIGNMENT = 2;
    static final int RESTORE_FAILURE_WORKBENCH_ATTACH = 3;
    static final int RESTORE_FAILURE_POWER = 4;
    static final int RESTORE_FAILURE_INSTRUMENT = 5;
    static final int RESTORE_FAILURE_GRAPH = 6;
    static final int RESTORE_FAILURE_UI_REFRESH = 7;
    static final int RESTORE_FAILURE_RESTART = 8;

    private static int injectedRestoreFailureStage;

    private final GeneratedBoardInstance board;
    private final GeneratedChallengeController challenge;
    private final BoardModificationController modifications;
    private final PcbWorkbenchController workbench;
    private final GeneratedBoardFamilyState familyState;
    private final GeneratedTemporalBehavior temporalBehavior;
    private final GeneratedFaultBinding faultBinding;
    private final boolean workbenchAttached;
    private final int attachedWorkbenchCount;

    private final Vector<CircuitElm> elmListReference;
    private final Vector<CircuitElm> elmContents;
    private final Vector<Boolean> selectedStates;
    private final Vector<Adjustable> adjustableContents;
    private final Vector<String> undoContents;
    private final Vector<String> redoContents;
    private final Vector<Adjustable> adjustableReference;
    private final Vector<String> undoReference;
    private final Vector<String> redoReference;

    private final Vector<CircuitNode> nodeList;
    private final Vector<CircuitNode> nodeListContents;
    private final Vector<Point> postDrawList;
    private final Vector<Point> postDrawListContents;
    private final Vector<Point> badConnectionList;
    private final Vector<Point> badConnectionListContents;
    private final CircuitElm[] voltageSources;
    private final CircuitElm[] voltageSourcesContents;
    private final HashMap<Point, CirSim.NodeMapEntry> nodeMap;
    private final HashMap<Point, CirSim.NodeMapEntry> nodeMapContents;
    private final HashMap<Point, Integer> postCountMap;
    private final HashMap<Point, Integer> postCountMapContents;
    private final Vector<CirSim.WireInfo> wireInfoList;
    private final Vector<CirSim.WireInfo> wireInfoListContents;
    private final double[][] circuitMatrix;
    private final double[][] circuitMatrixContents;
    private final double[] circuitRightSide;
    private final double[] circuitRightSideContents;
    private final double[] lastNodeVoltages;
    private final double[] lastNodeVoltagesContents;
    private final double[] nodeVoltages;
    private final double[] nodeVoltagesContents;
    private final double[] origRightSide;
    private final double[] origRightSideContents;
    private final double[][] origMatrix;
    private final double[][] origMatrixContents;
    private final RowInfo[] circuitRowInfo;
    private final RowInfo[] circuitRowInfoContents;
    private final int[] circuitPermute;
    private final int[] circuitPermuteContents;

    private final CircuitElm dragElm;
    private final CircuitElm menuElm;
    private final CircuitElm stopElm;
    private final CircuitElm mouseElm;
    private final CircuitElm plotXElm;
    private final CircuitElm plotYElm;
    private final SwitchElm heldSwitchElm;
    private final Rectangle selectedArea;
    private final int mousePost;
    private final int draggingPost;
    private final int scopeCount;
    private final Scope[] scopes;
    private final int[] scopeColCount;
    private final int scopeSelected;
    private final int menuScope;
    private final int menuPlot;
    private final int hintType;
    private final int hintItem1;
    private final int hintItem2;
    private final int mouseMode;
    private final int tempMouseMode;
    private final String mouseModeStr;
    private final boolean dragging;
    private final int dragGridX;
    private final int dragGridY;
    private final int dragScreenX;
    private final int dragScreenY;
    private final int initDragGridX;
    private final int initDragGridY;
    private final long mouseDownTime;
    private final long zoomTime;
    private final int mouseCursorX;
    private final int mouseCursorY;
    private final String lastCursorStyle;
    private final boolean mouseWasOverSplitter;
    private final boolean didSwitch;
    private final long myframes;
    private final long mytime;
    private final long myruntime;
    private final long mydrawtime;
    private final int frames;
    private final int steps;
    private final int framerate;
    private final int steprate;
    private final long lastTime;
    private final long lastFrameTime;
    private final long lastIterTime;
    private final long secTime;
    private final boolean needsRepaint;
    private final Rectangle circuitArea;
    private final double[] transform;
    private final String titleText;

    private final double t;
    private final double timeStep;
    private final double maxTimeStep;
    private final double minTimeStep;
    private final double timeStepAccum;
    private final int timeStepCount;
    private final boolean analyzeFlag;
    private final boolean dcAnalysisFlag;
    private final boolean simRunning;
    private final boolean circuitNonLinear;
    private final int voltageSourceCount;
    private final int circuitMatrixSize;
    private final int circuitMatrixFullSize;
    private final boolean circuitNeedsMap;
    private final String stopMessage;
    private final boolean converged;
    private final int subIterations;
    private final boolean unsavedChanges;

    private final BoardPowerState powerState;
    private final GeneratedExternalPowerBindings powerBindings;
    private final boolean modificationsFullyRestored;
    private final InstrumentController.DeveloperState instrumentState;

    private final boolean activeMeasurementOverlay;
    private final int observationalValidationDepth;
    private final int analysisCount;
    private final int generatedVerificationCount;
    private final BoardPowerState pendingBoardPowerState;
    private final boolean requestPowerOnDuringMeasurement;
    private final ActiveMeasurementStimulus lastActiveMeasurementStimulus;
    private final boolean activeMeasurementSolverRestored;
    private final String lastResistanceDiagnostics;
    private final double lastResistanceTestCurrent;
    private final double lastResistanceReferenceCurrent;
    private final double lastDiodeMeasurementVoltage;
    private final double lastDiodeMeasurementCurrent;
    private final int lastResistanceBlackProbeNode;
    private final int lastResistanceReferenceGroundNode;
    private final boolean generatedVerificationPending;
    private final boolean generatedVerificationAnalyzed;
    private final double generatedVerificationStartTime;
    private final boolean developerVerifierRunning;
    private final boolean task41VerificationComplete;
    private final double currentMult;
    private final double powerMult;
    private final CircuitElm staticMouseElmRef;
    private final Color staticWhiteColor;
    private final Color staticSelectColor;
    private final Color staticLightGrayColor;

    private Task41SimulationSnapshot(CirSim sim) {
        board = sim.generatedBoardInstance;
        challenge = sim.generatedChallengeController;
        modifications = sim.boardModificationController;
        workbench = sim.pcbWorkbenchController;
        familyState = board.getFamilyState();
        temporalBehavior = board.getTemporalBehavior();
        faultBinding = board.getFaultBinding();
        workbenchAttached = workbench != null &&
            workbench.isAttachedToSidebarForDeveloperVerification();
        attachedWorkbenchCount = sim.getAttachedPcbWorkbenchCountForDeveloperVerification();

        elmListReference = sim.elmList;
        elmContents = copy(sim.elmList);
        selectedStates = new Vector<Boolean>();
        for (CircuitElm element : elmContents)
            selectedStates.add(Boolean.valueOf(element.isSelected()));
        adjustableReference = sim.adjustables;
        adjustableContents = copy(sim.adjustables);
        undoReference = sim.undoStack;
        undoContents = copy(sim.undoStack);
        redoReference = sim.redoStack;
        redoContents = copy(sim.redoStack);

        nodeList = sim.nodeList;
        nodeListContents = copy(sim.nodeList);
        postDrawList = sim.postDrawList;
        postDrawListContents = copy(sim.postDrawList);
        badConnectionList = sim.badConnectionList;
        badConnectionListContents = copy(sim.badConnectionList);
        voltageSources = sim.voltageSources;
        voltageSourcesContents = copy(sim.voltageSources);
        nodeMap = sim.nodeMap;
        nodeMapContents = copy(sim.nodeMap);
        postCountMap = sim.postCountMap;
        postCountMapContents = copy(sim.postCountMap);
        wireInfoList = sim.wireInfoList;
        wireInfoListContents = copy(sim.wireInfoList);
        circuitMatrix = sim.circuitMatrix;
        circuitMatrixContents = copy(sim.circuitMatrix);
        circuitRightSide = sim.circuitRightSide;
        circuitRightSideContents = copy(sim.circuitRightSide);
        lastNodeVoltages = sim.lastNodeVoltages;
        lastNodeVoltagesContents = copy(sim.lastNodeVoltages);
        nodeVoltages = sim.nodeVoltages;
        nodeVoltagesContents = copy(sim.nodeVoltages);
        origRightSide = sim.origRightSide;
        origRightSideContents = copy(sim.origRightSide);
        origMatrix = sim.origMatrix;
        origMatrixContents = copy(sim.origMatrix);
        circuitRowInfo = sim.circuitRowInfo;
        circuitRowInfoContents = copy(sim.circuitRowInfo);
        circuitPermute = sim.circuitPermute;
        circuitPermuteContents = copy(sim.circuitPermute);

        dragElm = sim.dragElm;
        menuElm = sim.menuElm;
        stopElm = sim.stopElm;
        mouseElm = sim.getMouseElmForDeveloperVerification();
        plotXElm = sim.plotXElm;
        plotYElm = sim.plotYElm;
        heldSwitchElm = sim.heldSwitchElm;
        selectedArea = copy(sim.selectedArea);
        mousePost = sim.mousePost;
        draggingPost = sim.draggingPost;
        scopeCount = sim.scopeCount;
        scopes = sim.scopes;
        scopeColCount = copy(sim.scopeColCount);
        scopeSelected = sim.scopeSelected;
        menuScope = sim.menuScope;
        menuPlot = sim.menuPlot;
        hintType = sim.hintType;
        hintItem1 = sim.hintItem1;
        hintItem2 = sim.hintItem2;
        mouseMode = sim.mouseMode;
        tempMouseMode = sim.tempMouseMode;
        mouseModeStr = sim.mouseModeStr;
        dragging = sim.dragging;
        dragGridX = sim.dragGridX;
        dragGridY = sim.dragGridY;
        dragScreenX = sim.dragScreenX;
        dragScreenY = sim.dragScreenY;
        initDragGridX = sim.initDragGridX;
        initDragGridY = sim.initDragGridY;
        mouseDownTime = sim.mouseDownTime;
        zoomTime = sim.zoomTime;
        mouseCursorX = sim.mouseCursorX;
        mouseCursorY = sim.mouseCursorY;
        lastCursorStyle = sim.lastCursorStyle;
        mouseWasOverSplitter = sim.mouseWasOverSplitter;
        didSwitch = sim.didSwitch;
        myframes = sim.myframes;
        mytime = sim.mytime;
        myruntime = sim.myruntime;
        mydrawtime = sim.mydrawtime;
        frames = sim.frames;
        steps = sim.steps;
        framerate = sim.framerate;
        steprate = sim.steprate;
        lastTime = sim.lastTime;
        lastFrameTime = sim.lastFrameTime;
        lastIterTime = sim.lastIterTime;
        secTime = sim.secTime;
        needsRepaint = sim.needsRepaint;
        circuitArea = copy(sim.circuitArea);
        transform = copy(sim.transform);
        titleText = sim.getCircuitTitleForDeveloperVerification();

        t = sim.t;
        timeStep = sim.timeStep;
        maxTimeStep = sim.maxTimeStep;
        minTimeStep = sim.minTimeStep;
        timeStepAccum = sim.timeStepAccum;
        timeStepCount = sim.timeStepCount;
        analyzeFlag = sim.analyzeFlag;
        dcAnalysisFlag = sim.dcAnalysisFlag;
        simRunning = sim.simRunning;
        circuitNonLinear = sim.circuitNonLinear;
        voltageSourceCount = sim.voltageSourceCount;
        circuitMatrixSize = sim.circuitMatrixSize;
        circuitMatrixFullSize = sim.circuitMatrixFullSize;
        circuitNeedsMap = sim.circuitNeedsMap;
        stopMessage = sim.stopMessage;
        converged = sim.converged;
        subIterations = sim.subIterations;
        unsavedChanges = sim.unsavedChanges;

        powerState = sim.getBoardPowerController().getState();
        powerBindings = sim.getBoardPowerController().getBindingsForDeveloperVerification();
        modificationsFullyRestored = modifications != null && modifications.isFullyRestored();
        instrumentState = sim.instrumentController.captureForDeveloperVerification();

        activeMeasurementOverlay = sim.activeMeasurementOverlay;
        observationalValidationDepth = sim.observationalValidationDepth;
        analysisCount = sim.analysisCountForDeveloperVerification;
        generatedVerificationCount = sim.generatedVerificationCountForDeveloperVerification;
        pendingBoardPowerState = sim.pendingBoardPowerState;
        requestPowerOnDuringMeasurement =
            sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification;
        lastActiveMeasurementStimulus = sim.lastActiveMeasurementStimulus;
        activeMeasurementSolverRestored = sim.activeMeasurementSolverRestored;
        lastResistanceDiagnostics = sim.lastResistanceMeasurementDiagnostics;
        lastResistanceTestCurrent = sim.lastResistanceTestCurrent;
        lastResistanceReferenceCurrent = sim.lastResistanceReferenceCurrent;
        lastDiodeMeasurementVoltage = sim.lastDiodeMeasurementVoltage;
        lastDiodeMeasurementCurrent = sim.lastDiodeMeasurementCurrent;
        lastResistanceBlackProbeNode = sim.lastResistanceBlackProbeNode;
        lastResistanceReferenceGroundNode = sim.lastResistanceReferenceGroundNode;
        generatedVerificationPending = sim.generatedBoardVerificationPending;
        generatedVerificationAnalyzed = sim.generatedBoardVerificationAnalyzed;
        generatedVerificationStartTime = sim.generatedBoardVerificationStartTime;
        developerVerifierRunning = sim.developerVerifierRunning;
        task41VerificationComplete = sim.troubleshootTask41VerificationComplete;
        currentMult = CircuitElm.currentMult;
        powerMult = CircuitElm.powerMult;
        staticMouseElmRef = CircuitElm.mouseElmRef;
        staticWhiteColor = CircuitElm.whiteColor;
        staticSelectColor = CircuitElm.selectColor;
        staticLightGrayColor = CircuitElm.lightGrayColor;
    }

    static Task41SimulationSnapshot capture(CirSim sim) {
        if (sim == null || sim.generatedBoardInstance == null ||
                sim.generatedChallengeController == null || sim.elmList == null ||
                sim.adjustables == null || sim.undoStack == null || sim.redoStack == null)
            throw new IllegalStateException("Task 41 snapshot requires an active generated owner");
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("Task 41 snapshot cannot start over a measurement overlay");
        return new Task41SimulationSnapshot(sim);
    }

    void beginProof(CirSim sim) {
        assertOwner(sim);
        sim.detachPcbWorkbenchForDeveloperVerification();
        if (sim.getAttachedPcbWorkbenchCountForDeveloperVerification() != 0)
            throw new IllegalStateException("Task 41 proof retained an attached player workbench");
    }

    static void setInjectedRestoreFailureStageForDeveloperVerification(int stage) {
        if (stage < 0 || stage > RESTORE_FAILURE_RESTART)
            throw new IllegalArgumentException("Unknown Task 41 restore failure stage: " + stage);
        injectedRestoreFailureStage = stage;
    }

    static void clearInjectedRestoreFailureStageForDeveloperVerification() {
        injectedRestoreFailureStage = 0;
    }

    /**
     * Exercise every restore boundary while the proof workbench is detached.
     * This is deliberately package-private and is called only by the developer
     * verifier; normal challenge admission never injects failures.
     */
    static void verifyInjectedFailureStagesForDeveloperVerification(CirSim sim) {
        Task41SimulationSnapshot snapshot = capture(sim);
        if (snapshot.workbenchAttached || snapshot.attachedWorkbenchCount != 0)
            throw new IllegalStateException("Task 41 restore failure proof requires detached UI");
        int prepareBefore = sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification();
        int startBefore = sim.instrumentController.getContinuityFeedbackStartCountForDeveloperVerification();
        int stopBefore = sim.instrumentController.getContinuityFeedbackStopCountForDeveloperVerification();
        sim.instrumentController.perturbContinuityFeedbackForDeveloperVerification();
        if (sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification() ==
                prepareBefore ||
                sim.instrumentController.getContinuityFeedbackStartCountForDeveloperVerification() ==
                startBefore ||
                sim.instrumentController.getContinuityFeedbackStopCountForDeveloperVerification() ==
                stopBefore)
            throw new IllegalStateException("Task 41 restore failure proof did not perturb continuity state");
        for (int stage = RESTORE_FAILURE_CONTROLLER_DISPOSAL;
                stage <= RESTORE_FAILURE_RESTART; stage++) {
            setInjectedRestoreFailureStageForDeveloperVerification(stage);
            boolean threw = false;
            try {
                snapshot.restore(sim);
            } catch (RuntimeException expected) {
                threw = true;
            } finally {
                clearInjectedRestoreFailureStageForDeveloperVerification();
            }
            if (!threw)
                throw new IllegalStateException("Task 41 restore failure stage did not throw: " +
                    stage);
            snapshot.assertRestored(sim);
            if (sim.getAttachedPcbWorkbenchCountForDeveloperVerification() != 0 ||
                    sim.activeMeasurementOverlay)
                throw new IllegalStateException("Task 41 injected restore changed proof UI state");
        }
        snapshot.restore(sim);
        snapshot.assertRestored(sim);
    }

    void restore(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Missing Task 41 restore simulator");
        try {
            restoreTransactional(sim);
        } catch (RuntimeException failure) {
            bestEffortRestore(sim);
            throw failure;
        } catch (Error failure) {
            bestEffortRestore(sim);
            throw failure;
        }
    }

    private void restoreTransactional(CirSim sim) {
        boolean runningStateChanged = sim.simRunning != simRunning;

        PcbWorkbenchController currentWorkbench = sim.pcbWorkbenchController;
        if (currentWorkbench != null && currentWorkbench != workbench)
            currentWorkbench.disposeForDeveloperVerification();
        if (workbench != null && workbench.isAttachedToSidebarForDeveloperVerification())
            workbench.detachFromSidebar();
        sim.pcbWorkbenchController = null;
        maybeInjectRestoreFailure(RESTORE_FAILURE_CONTROLLER_DISPOSAL);

        restoreOwnerAndCollections(sim);
        sim.pcbWorkbenchController = workbench;
        maybeInjectRestoreFailure(RESTORE_FAILURE_CONTROLLER_ASSIGNMENT);

        if (workbench != null && workbenchAttached)
            workbench.attachToSidebar(sim.verticalPanel);
        maybeInjectRestoreFailure(RESTORE_FAILURE_WORKBENCH_ATTACH);

        sim.getBoardPowerController().restoreForDeveloperVerification(powerBindings, powerState);
        maybeInjectRestoreFailure(RESTORE_FAILURE_POWER);

        sim.instrumentController.restoreForDeveloperVerification(instrumentState);
        maybeInjectRestoreFailure(RESTORE_FAILURE_INSTRUMENT);

        restoreGraphAndRuntimeState(sim);
        maybeInjectRestoreFailure(RESTORE_FAILURE_GRAPH);

        sim.refreshGeneratedUiForDeveloperVerification();
        sim.refreshChallengeInteractionState();
        sim.instrumentController.restoreForDeveloperVerification(instrumentState);
        sim.restoreMouseElmForDeveloperVerification(mouseElm);
        restoreStaticCircuitElmState();
        maybeInjectRestoreFailure(RESTORE_FAILURE_UI_REFRESH);

        if (runningStateChanged)
            sim.setSimRunning(simRunning);
        maybeInjectRestoreFailure(RESTORE_FAILURE_RESTART);
    }

    /**
     * Restore the exact owner graph after a failed transaction.  Each call is
     * isolated so a failure in UI/controller cleanup cannot prevent the old
     * CircuitJS references and physical owner from being put back.
     */
    private void bestEffortRestore(CirSim sim) {
        boolean runningStateChanged = sim.simRunning != simRunning;
        try {
            PcbWorkbenchController current = sim.pcbWorkbenchController;
            if (current != null && current != workbench)
                current.disposeForDeveloperVerification();
        } catch (Throwable ignored) { }
        try {
            if (workbench != null && workbench.isAttachedToSidebarForDeveloperVerification())
                workbench.detachFromSidebar();
        } catch (Throwable ignored) { }
        try {
            sim.pcbWorkbenchController = null;
        } catch (Throwable ignored) { }
        try {
            restoreOwnerAndCollections(sim);
            sim.pcbWorkbenchController = workbench;
        } catch (Throwable ignored) { }
        try {
            if (workbench != null && workbenchAttached)
                workbench.attachToSidebar(sim.verticalPanel);
        } catch (Throwable ignored) { }
        try {
            sim.getBoardPowerController().restoreForDeveloperVerification(powerBindings, powerState);
        } catch (Throwable ignored) { }
        try {
            sim.instrumentController.restoreForDeveloperVerification(instrumentState);
        } catch (Throwable ignored) { }
        try {
            restoreGraphAndRuntimeState(sim);
        } catch (Throwable ignored) { }
        try {
            sim.refreshGeneratedUiForDeveloperVerification();
        } catch (Throwable ignored) { }
        try {
            sim.refreshChallengeInteractionState();
        } catch (Throwable ignored) { }
        try {
            sim.instrumentController.restoreForDeveloperVerification(instrumentState);
        } catch (Throwable ignored) { }
        try {
            sim.restoreMouseElmForDeveloperVerification(mouseElm);
        } catch (Throwable ignored) { }
        try {
            restoreStaticCircuitElmState();
        } catch (Throwable ignored) { }
        try {
            if (runningStateChanged || sim.simRunning != simRunning)
                sim.setSimRunning(simRunning);
        } catch (Throwable ignored) { }
    }

    private void restoreOwnerAndCollections(CirSim sim) {
        restoreVector(sim.elmList, elmListReference, elmContents);
        restoreVector(sim.adjustables, adjustableReference, adjustableContents);
        restoreVector(sim.undoStack, undoReference, undoContents);
        restoreVector(sim.redoStack, redoReference, redoContents);
        sim.elmList = elmListReference;
        sim.adjustables = adjustableReference;
        sim.undoStack = undoReference;
        sim.redoStack = redoReference;
        for (int index = 0; index < elmContents.size(); index++)
            elmContents.get(index).setSelected(selectedStates.get(index).booleanValue());

        sim.generatedBoardInstance = board;
        sim.generatedChallengeController = challenge;
        sim.boardModificationController = modifications;
    }

    private void restoreGraphAndRuntimeState(CirSim sim) {
        // Reassign the captured graph objects themselves.  Do not call
        // analyzeCircuit() here: it allocates a second graph and would leave
        // the restored owner with a mixed old/new solver object graph.
        sim.nodeList = nodeList;
        sim.postDrawList = postDrawList;
        sim.badConnectionList = badConnectionList;
        sim.voltageSources = voltageSources;
        sim.nodeMap = nodeMap;
        sim.postCountMap = postCountMap;
        sim.wireInfoList = wireInfoList;
        sim.circuitMatrix = circuitMatrix;
        sim.circuitRightSide = circuitRightSide;
        sim.lastNodeVoltages = lastNodeVoltages;
        sim.nodeVoltages = nodeVoltages;
        sim.origRightSide = origRightSide;
        sim.origMatrix = origMatrix;
        sim.circuitRowInfo = circuitRowInfo;
        sim.circuitPermute = circuitPermute;
        restoreGraphContents();

        sim.dragElm = dragElm;
        sim.menuElm = menuElm;
        sim.stopElm = stopElm;
        sim.plotXElm = plotXElm;
        sim.plotYElm = plotYElm;
        sim.heldSwitchElm = heldSwitchElm;
        sim.selectedArea = copy(selectedArea);
        sim.mousePost = mousePost;
        sim.draggingPost = draggingPost;
        sim.scopeCount = scopeCount;
        sim.scopes = scopes;
        sim.scopeColCount = copy(scopeColCount);
        sim.scopeSelected = scopeSelected;
        sim.menuScope = menuScope;
        sim.menuPlot = menuPlot;
        sim.hintType = hintType;
        sim.hintItem1 = hintItem1;
        sim.hintItem2 = hintItem2;
        sim.mouseMode = mouseMode;
        sim.tempMouseMode = tempMouseMode;
        sim.mouseModeStr = mouseModeStr;
        sim.dragging = dragging;
        sim.dragGridX = dragGridX;
        sim.dragGridY = dragGridY;
        sim.dragScreenX = dragScreenX;
        sim.dragScreenY = dragScreenY;
        sim.initDragGridX = initDragGridX;
        sim.initDragGridY = initDragGridY;
        sim.mouseDownTime = mouseDownTime;
        sim.zoomTime = zoomTime;
        sim.mouseCursorX = mouseCursorX;
        sim.mouseCursorY = mouseCursorY;
        sim.lastCursorStyle = lastCursorStyle;
        sim.mouseWasOverSplitter = mouseWasOverSplitter;
        sim.didSwitch = didSwitch;
        sim.myframes = myframes;
        sim.mytime = mytime;
        sim.myruntime = myruntime;
        sim.mydrawtime = mydrawtime;
        sim.frames = frames;
        sim.steps = steps;
        sim.framerate = framerate;
        sim.steprate = steprate;
        sim.lastTime = lastTime;
        sim.lastFrameTime = lastFrameTime;
        sim.lastIterTime = lastIterTime;
        sim.secTime = secTime;
        sim.needsRepaint = needsRepaint;
        sim.circuitArea = copy(circuitArea);
        sim.transform = copy(transform);
        sim.restoreCircuitTitleForDeveloperVerification(titleText);

        sim.t = t;
        sim.timeStep = timeStep;
        sim.maxTimeStep = maxTimeStep;
        sim.minTimeStep = minTimeStep;
        sim.timeStepAccum = timeStepAccum;
        sim.timeStepCount = timeStepCount;
        sim.analyzeFlag = analyzeFlag;
        sim.dcAnalysisFlag = dcAnalysisFlag;
        sim.simRunning = simRunning;
        sim.circuitNonLinear = circuitNonLinear;
        sim.voltageSourceCount = voltageSourceCount;
        sim.circuitMatrixSize = circuitMatrixSize;
        sim.circuitMatrixFullSize = circuitMatrixFullSize;
        sim.circuitNeedsMap = circuitNeedsMap;
        sim.stopMessage = stopMessage;
        sim.converged = converged;
        sim.subIterations = subIterations;
        sim.unsavedChanges = unsavedChanges;

        sim.activeMeasurementOverlay = activeMeasurementOverlay;
        sim.observationalValidationDepth = observationalValidationDepth;
        sim.analysisCountForDeveloperVerification = analysisCount;
        sim.generatedVerificationCountForDeveloperVerification = generatedVerificationCount;
        sim.pendingBoardPowerState = pendingBoardPowerState;
        sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification =
            requestPowerOnDuringMeasurement;
        sim.lastActiveMeasurementStimulus = lastActiveMeasurementStimulus;
        sim.activeMeasurementSolverRestored = activeMeasurementSolverRestored;
        sim.lastResistanceMeasurementDiagnostics = lastResistanceDiagnostics;
        sim.lastResistanceTestCurrent = lastResistanceTestCurrent;
        sim.lastResistanceReferenceCurrent = lastResistanceReferenceCurrent;
        sim.lastDiodeMeasurementVoltage = lastDiodeMeasurementVoltage;
        sim.lastDiodeMeasurementCurrent = lastDiodeMeasurementCurrent;
        sim.lastResistanceBlackProbeNode = lastResistanceBlackProbeNode;
        sim.lastResistanceReferenceGroundNode = lastResistanceReferenceGroundNode;
        sim.generatedBoardVerificationPending = generatedVerificationPending;
        sim.generatedBoardVerificationAnalyzed = generatedVerificationAnalyzed;
        sim.generatedBoardVerificationStartTime = generatedVerificationStartTime;
        sim.developerVerifierRunning = developerVerifierRunning;
        sim.troubleshootTask41VerificationComplete = task41VerificationComplete;
        CircuitElm.currentMult = currentMult;
        CircuitElm.powerMult = powerMult;
        CircuitElm.mouseElmRef = staticMouseElmRef;
        CircuitElm.whiteColor = staticWhiteColor;
        CircuitElm.selectColor = staticSelectColor;
        CircuitElm.lightGrayColor = staticLightGrayColor;
    }

    private void restoreGraphContents() {
        restoreVector(nodeList, nodeListContents);
        restoreVector(postDrawList, postDrawListContents);
        restoreVector(badConnectionList, badConnectionListContents);
        restoreArray(voltageSources, voltageSourcesContents);
        restoreMap(nodeMap, nodeMapContents);
        restoreMap(postCountMap, postCountMapContents);
        restoreVector(wireInfoList, wireInfoListContents);
        restoreMatrix(circuitMatrix, circuitMatrixContents);
        restoreArray(circuitRightSide, circuitRightSideContents);
        restoreArray(lastNodeVoltages, lastNodeVoltagesContents);
        restoreArray(nodeVoltages, nodeVoltagesContents);
        restoreArray(origRightSide, origRightSideContents);
        restoreMatrix(origMatrix, origMatrixContents);
        restoreArray(circuitRowInfo, circuitRowInfoContents);
        restoreArray(circuitPermute, circuitPermuteContents);
    }

    private void restoreStaticCircuitElmState() {
        CircuitElm.currentMult = currentMult;
        CircuitElm.powerMult = powerMult;
        CircuitElm.mouseElmRef = staticMouseElmRef;
        CircuitElm.whiteColor = staticWhiteColor;
        CircuitElm.selectColor = staticSelectColor;
        CircuitElm.lightGrayColor = staticLightGrayColor;
    }

    private static void maybeInjectRestoreFailure(int stage) {
        if (injectedRestoreFailureStage == stage)
            throw new IllegalStateException("Task 41 injected restore failure stage " + stage);
    }

    void assertRestored(CirSim sim) {
        assertOwner(sim);
        assertGraphRestored(sim);
        if (sim.elmList != elmListReference || !sameIdentity(sim.elmList, elmContents))
            throw new IllegalStateException("Task 41 restore changed active element list/order");
        if (sim.adjustables != adjustableReference || !sameIdentity(sim.adjustables,
                adjustableContents))
            throw new IllegalStateException("Task 41 restore changed adjustable state");
        if (sim.undoStack != undoReference || !sim.undoStack.equals(undoContents) ||
                sim.redoStack != redoReference || !sim.redoStack.equals(redoContents))
            throw new IllegalStateException("Task 41 restore changed command history");
        if (sim.pcbWorkbenchController != workbench ||
                sim.getAttachedPcbWorkbenchCountForDeveloperVerification() !=
                    attachedWorkbenchCount ||
                (workbench != null && workbench.isAttachedToSidebarForDeveloperVerification() !=
                    workbenchAttached))
            throw new IllegalStateException("Task 41 restore changed workbench ownership");
        if (workbenchAttached)
            sim.assertSingleAttachedPcbWorkbenchForDeveloperVerification();
        if (sim.getBoardPowerController().getBindingsForDeveloperVerification() != powerBindings ||
                sim.getBoardPowerController().getState() != powerState)
            throw new IllegalStateException("Task 41 restore changed board power ownership");
        if (powerState == BoardPowerState.POWERED && !powerBindings.areAllConnected())
            throw new IllegalStateException("Task 41 restored a disconnected powered board");
        if (powerState == BoardPowerState.UNPOWERED && !powerBindings.areAllDisconnected())
            throw new IllegalStateException("Task 41 restored a connected unpowered board");
        if (sim.boardModificationController != modifications ||
                sim.boardModificationController == null ||
                sim.boardModificationController.isFullyRestored() != modificationsFullyRestored)
            throw new IllegalStateException("Task 41 restore changed physical modification ownership");
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("Task 41 restore retained a measurement overlay");
        if (sim.getMouseElmForDeveloperVerification() != mouseElm ||
                sim.simRunning != simRunning || sim.t != t ||
                sim.myframes != myframes || sim.mytime != mytime ||
                sim.myruntime != myruntime || sim.mydrawtime != mydrawtime ||
                !sameDouble(CircuitElm.currentMult, currentMult) ||
                !sameDouble(CircuitElm.powerMult, powerMult) ||
                CircuitElm.mouseElmRef != staticMouseElmRef ||
                CircuitElm.whiteColor != staticWhiteColor ||
                CircuitElm.selectColor != staticSelectColor ||
                CircuitElm.lightGrayColor != staticLightGrayColor)
            throw new IllegalStateException("Task 41 restore changed live simulation state");
        if (!sameInstrumentState(sim.instrumentController.captureForDeveloperVerification(),
                instrumentState))
            throw new IllegalStateException("Task 41 restore changed instrument state");
        for (int index = 0; index < elmContents.size(); index++)
            if (elmContents.get(index).isSelected() != selectedStates.get(index).booleanValue())
                throw new IllegalStateException("Task 41 restore changed element selection");
    }

    private void assertGraphRestored(CirSim sim) {
        if (sim.nodeList != nodeList || !sameIdentity(sim.nodeList, nodeListContents) ||
                sim.postDrawList != postDrawList ||
                !sameIdentity(sim.postDrawList, postDrawListContents) ||
                sim.badConnectionList != badConnectionList ||
                !sameIdentity(sim.badConnectionList, badConnectionListContents) ||
                sim.voltageSources != voltageSources ||
                !sameIdentity(sim.voltageSources, voltageSourcesContents) ||
                sim.nodeMap != nodeMap || !sameIdentity(sim.nodeMap, nodeMapContents) ||
                sim.postCountMap != postCountMap ||
                !sameIdentity(sim.postCountMap, postCountMapContents) ||
                sim.wireInfoList != wireInfoList ||
                !sameIdentity(sim.wireInfoList, wireInfoListContents) ||
                sim.circuitMatrix != circuitMatrix ||
                !sameMatrix(sim.circuitMatrix, circuitMatrixContents) ||
                sim.circuitRightSide != circuitRightSide ||
                !sameValues(sim.circuitRightSide, circuitRightSideContents) ||
                sim.lastNodeVoltages != lastNodeVoltages ||
                !sameValues(sim.lastNodeVoltages, lastNodeVoltagesContents) ||
                sim.nodeVoltages != nodeVoltages ||
                !sameValues(sim.nodeVoltages, nodeVoltagesContents) ||
                sim.origRightSide != origRightSide ||
                !sameValues(sim.origRightSide, origRightSideContents) ||
                sim.origMatrix != origMatrix ||
                !sameMatrix(sim.origMatrix, origMatrixContents) ||
                sim.circuitRowInfo != circuitRowInfo ||
                !sameIdentity(sim.circuitRowInfo, circuitRowInfoContents) ||
                sim.circuitPermute != circuitPermute ||
                !sameValues(sim.circuitPermute, circuitPermuteContents))
            throw new IllegalStateException("Task 41 restore changed the CircuitJS graph object");
    }

    private void assertOwner(CirSim sim) {
        if (sim.generatedBoardInstance != board || sim.generatedChallengeController != challenge ||
                sim.boardModificationController != modifications ||
                board.getFamilyState() != familyState ||
                board.getTemporalBehavior() != temporalBehavior ||
                board.getFaultBinding() != faultBinding)
            throw new IllegalStateException("Task 41 proof lost its exact generated owner");
    }

    private static <T> Vector<T> copy(Vector<T> source) {
        return source == null ? null : new Vector<T>(source);
    }

    private static Rectangle copy(Rectangle source) {
        return source == null ? null :
            new Rectangle(source.x, source.y, source.width, source.height);
    }

    private static double[] copy(double[] source) {
        if (source == null) return null;
        double[] result = new double[source.length];
        for (int index = 0; index < source.length; index++) result[index] = source[index];
        return result;
    }

    private static int[] copy(int[] source) {
        if (source == null) return null;
        int[] result = new int[source.length];
        for (int index = 0; index < source.length; index++) result[index] = source[index];
        return result;
    }

    private static CircuitElm[] copy(CircuitElm[] source) {
        if (source == null) return null;
        CircuitElm[] result = new CircuitElm[source.length];
        for (int index = 0; index < source.length; index++) result[index] = source[index];
        return result;
    }

    private static RowInfo[] copy(RowInfo[] source) {
        if (source == null) return null;
        RowInfo[] result = new RowInfo[source.length];
        for (int index = 0; index < source.length; index++) result[index] = source[index];
        return result;
    }

    private static double[][] copy(double[][] source) {
        if (source == null) return null;
        double[][] result = new double[source.length][];
        for (int index = 0; index < source.length; index++)
            result[index] = copy(source[index]);
        return result;
    }

    private static <K, V> HashMap<K, V> copy(HashMap<K, V> source) {
        return source == null ? null : new HashMap<K, V>(source);
    }

    private static <T> void restoreVector(Vector<T> current, Vector<T> reference,
            Vector<T> contents) {
        restoreVector(reference, contents);
    }

    private static <T> void restoreVector(Vector<T> target, Vector<T> contents) {
        if (target == null) return;
        target.removeAllElements();
        if (contents != null)
            target.addAll(contents);
    }

    private static <T> void restoreArray(T[] target, T[] contents) {
        if (target == null) return;
        int length = contents == null ? 0 : contents.length;
        int limit = Math.min(target.length, length);
        for (int index = 0; index < limit; index++) target[index] = contents[index];
        for (int index = limit; index < target.length; index++) target[index] = null;
    }

    private static void restoreArray(double[] target, double[] contents) {
        if (target == null) return;
        int length = contents == null ? 0 : contents.length;
        int limit = Math.min(target.length, length);
        for (int index = 0; index < limit; index++) target[index] = contents[index];
        for (int index = limit; index < target.length; index++) target[index] = 0;
    }

    private static void restoreArray(int[] target, int[] contents) {
        if (target == null) return;
        int length = contents == null ? 0 : contents.length;
        int limit = Math.min(target.length, length);
        for (int index = 0; index < limit; index++) target[index] = contents[index];
        for (int index = limit; index < target.length; index++) target[index] = 0;
    }

    private static <K, V> void restoreMap(HashMap<K, V> target, HashMap<K, V> contents) {
        if (target == null) return;
        target.clear();
        if (contents != null)
            target.putAll(contents);
    }

    private static void restoreMatrix(double[][] target, double[][] contents) {
        if (target == null) return;
        int length = contents == null ? 0 : contents.length;
        int limit = Math.min(target.length, length);
        for (int index = 0; index < limit; index++)
            restoreArray(target[index], contents[index]);
        for (int index = limit; index < target.length; index++)
            restoreArray(target[index], null);
    }

    private static boolean sameIdentity(Vector<?> actual, Vector<?> expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.size() != expected.size()) return false;
        for (int index = 0; index < actual.size(); index++)
            if (actual.get(index) != expected.get(index)) return false;
        return true;
    }

    private static <T> boolean sameIdentity(T[] actual, T[] expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.length != expected.length) return false;
        for (int index = 0; index < actual.length; index++)
            if (actual[index] != expected[index]) return false;
        return true;
    }

    private static <K, V> boolean sameIdentity(HashMap<K, V> actual,
            HashMap<K, V> expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.size() != expected.size()) return false;
        for (Map.Entry<K, V> entry : expected.entrySet()) {
            if (!actual.containsKey(entry.getKey()) ||
                    actual.get(entry.getKey()) != entry.getValue()) return false;
        }
        return true;
    }

    private static boolean sameValues(double[] actual, double[] expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.length != expected.length) return false;
        for (int index = 0; index < actual.length; index++)
            if (!sameDouble(actual[index], expected[index])) return false;
        return true;
    }

    private static boolean sameValues(int[] actual, int[] expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.length != expected.length) return false;
        for (int index = 0; index < actual.length; index++)
            if (actual[index] != expected[index]) return false;
        return true;
    }

    private static boolean sameMatrix(double[][] actual, double[][] expected) {
        if (actual == null || expected == null)
            return actual == expected;
        if (actual.length != expected.length) return false;
        for (int index = 0; index < actual.length; index++)
            if (!sameValues(actual[index], expected[index])) return false;
        return true;
    }

    private static boolean sameInstrumentState(InstrumentController.DeveloperState actual,
            InstrumentController.DeveloperState expected) {
        if (actual.activeStrategy != expected.activeStrategy || actual.redProbe != expected.redProbe ||
                actual.blackProbe != expected.blackProbe ||
                actual.interactionEnabled != expected.interactionEnabled ||
                !same(actual.readingText, expected.readingText) ||
                actual.continuityVisible != expected.continuityVisible ||
                actual.continuityRequested != expected.continuityRequested ||
                actual.continuityPrepareCount != expected.continuityPrepareCount ||
                actual.continuityStartCount != expected.continuityStartCount ||
                actual.continuityStopCount != expected.continuityStopCount ||
                actual.dcVoltagePlaceholderDisplayCount != expected.dcVoltagePlaceholderDisplayCount ||
                actual.dcVoltageDisplayChangeCount != expected.dcVoltageDisplayChangeCount ||
                actual.modeStates.size() != expected.modeStates.size()) return false;
        for (int index = 0; index < actual.modeStates.size(); index++) {
            InstrumentController.ModeState left = actual.modeStates.get(index);
            InstrumentController.ModeState right = expected.modeStates.get(index);
            if (!same(left.id, right.id) || !same(left.displayText, right.displayText) ||
                    !sameDouble(left.primaryValue, right.primaryValue) ||
                    !sameDouble(left.secondaryValue, right.secondaryValue) ||
                    left.measurementCount != right.measurementCount ||
                    left.continuityDetected != right.continuityDetected ||
                    left.refreshPending != right.refreshPending) return false;
        }
        return true;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean sameDouble(double left, double right) {
        return left == right || (Double.isNaN(left) && Double.isNaN(right));
    }
}
