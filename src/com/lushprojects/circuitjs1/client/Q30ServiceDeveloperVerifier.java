package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.Timer;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Compiled qualification client of production observations, service and customer retest. */
final class Q30ServiceDeveloperVerifier {
    interface Completion {
        void complete(Throwable failure, long elapsedMillis);
    }

    private enum Stage {
        PREFLIGHT,
        UNREPAIRED_RETEST_BEGIN, RETEST_STEP, SETTLE_AFTER_UNREPAIRED_RETEST,
        OBSERVATION_BEGIN, OBSERVATION_STEP, OBSERVATION_FINISH, CLEAR_INSTRUMENTS,
        START_OBSERVATION_DISCHARGE, DISCHARGE_CHUNK, SETTLE_AFTER_OBSERVATION_DISCHARGE,
        LIFT_LEAD, SETTLE_AFTER_LIFT, CHECK_LIFTED,
        RECONNECT_LEAD, SETTLE_AFTER_RECONNECT,
        REMOVE_ORIGINAL, SETTLE_AFTER_ORIGINAL_REMOVE, CHECK_ORIGINAL_REMOVED,
        INSTALL_ORIGINAL, SETTLE_AFTER_ORIGINAL_INSTALL,
        POWER_ORIGINAL, SETTLE_AFTER_ORIGINAL_POWER,
        ORIGINAL_RETEST_BEGIN, SETTLE_AFTER_ORIGINAL_RETEST,
        START_ORIGINAL_DISCHARGE, SETTLE_AFTER_ORIGINAL_DISCHARGE,
        REMOVE_ORIGINAL_AFTER_RETEST, SETTLE_AFTER_SECOND_ORIGINAL_REMOVE,
        WRONG_RELAY_INSTALL, SETTLE_AFTER_WRONG_INSTALL,
        POWER_WRONG_RELAY, SETTLE_AFTER_WRONG_POWER,
        WRONG_RETEST_BEGIN, SETTLE_AFTER_WRONG_RETEST,
        START_WRONG_RELAY_DISCHARGE, SETTLE_AFTER_WRONG_DISCHARGE,
        REMOVE_WRONG_RELAY, SETTLE_AFTER_WRONG_REMOVE,
        CORRECT_RELAY_INSTALL, SETTLE_AFTER_CORRECT_INSTALL, CHECK_REPLACEMENT,
        POWER_CORRECT_RELAY, SETTLE_AFTER_CORRECT_POWER,
        CORRECT_RETEST_BEGIN, SETTLE_AFTER_CORRECT_RETEST, FINAL_ASSERTIONS,
        COMPLETE
    }

    private Q30ServiceDeveloperVerifier() { }

    static void schedule(CirSim sim, GeneratedBoardInstance owner,
            Completion completion) {
        if (completion == null)
            throw new IllegalArgumentException("Missing Q30 service completion callback");
        final ServiceJob job = new ServiceJob(sim, owner, completion);
        try {
            job.validateSchedulingScope();
            job.start();
        } catch (Throwable failure) {
            job.failAtCurrentPhase(failure);
        }
    }

    private static final class ServiceJob {
        private final CirSim sim;
        private final GeneratedBoardInstance owner;
        private final Completion completion;
        private final long startedMillis = System.currentTimeMillis();
        private final Object graph;
        private final BoardModificationController modifications;
        private final GeneratedChallengeController challenge;
        private final Map<String, CircuitMeasurementEndpoint> pads =
            new TreeMap<String, CircuitMeasurementEndpoint>();

        private Stage stage = Stage.PREFLIGHT;
        private String phase = "schedule";
        private boolean completed;
        private String component;
        private PhysicalPart<?> original;
        private String pad;
        private String fingerprint;
        private Vector<GeneratedDiagnosticSample> samples;
        private GeneratedDiagnosticObservationExecutor.Cursor observationCursor;
        private int observationSteps;
        private int observationStepCount;
        private Stage afterDischarge;
        private int dischargeSteps;
        private GeneratedWork<GeneratedCustomerRetestResult> retestWork;
        private GeneratedChallengeController.RetestCompletionDispatch savedRetestDispatch;
        private boolean retestDispatchSaved;
        private boolean retestExpectedPass;
        private String retestLabel;
        private Stage afterRetest;
        private int retestSteps;

        ServiceJob(CirSim sim, GeneratedBoardInstance owner,
                Completion completion) {
            this.sim = sim;
            this.owner = owner;
            this.completion = completion;
            graph = sim == null ? null : sim.elmList;
            modifications = sim == null ? null : sim.getBoardModificationController();
            challenge = sim == null ? null : sim.getGeneratedChallengeController();
        }

        void validateSchedulingScope() {
            if (sim == null || owner == null || !sim.troubleshootDebug ||
                    !sim.developerVerifierRunning || !owner.isDeveloperOnlyFaultRoute())
                throw new IllegalStateException(
                    "Q30 service verification requires its active developer-only owner");
            validateCurrent("schedule", false);
        }

        void start() {
            publishProgress("scheduled");
            scheduleNext();
        }

        private void scheduleNext() {
            if (completed) return;
            try {
                new Timer() {
                    public void run() { tick(); }
                }.schedule(1);
            } catch (Throwable failure) {
                failAtCurrentPhase(failure);
            }
        }

        private void tick() {
            if (completed) return;
            boolean priorDeveloperScope = false;
            boolean developerScopeCaptured = false;
            Throwable failure = null;
            try {
                phase = describeStage();
                validateCurrent(phase, stage == Stage.DISCHARGE_CHUNK);
                priorDeveloperScope = sim.developerVerifierRunning;
                developerScopeCaptured = true;
                sim.developerVerifierRunning = true;
                publishProgress(phase);
                runOneStage();
                validateCurrent("after-" + phase,
                    stage == Stage.DISCHARGE_CHUNK);
            } catch (Throwable problem) {
                failure = withPhase(problem);
            } finally {
                if (developerScopeCaptured)
                    sim.developerVerifierRunning = priorDeveloperScope;
            }
            if (failure != null) {
                finish(failure);
                return;
            }
            if (stage == Stage.COMPLETE) {
                finish(null);
                return;
            }
            scheduleNext();
        }

        private String describeStage() {
            if (stage == Stage.OBSERVATION_STEP)
                return "diagnostic-observation-" + (observationSteps + 1) + "/" +
                    observationStepCount;
            if (stage == Stage.RETEST_STEP)
                return retestLabel + "-step-" + (retestSteps + 1);
            if (stage == Stage.DISCHARGE_CHUNK)
                return "discharge-chunk-" + (dischargeSteps + 1);
            return stage.name().toLowerCase().replace('_', '-');
        }

        private void runOneStage() {
            switch (stage) {
            case PREFLIGHT:
                preflight();
                stage = Stage.UNREPAIRED_RETEST_BEGIN;
                break;
            case UNREPAIRED_RETEST_BEGIN:
                beginRetest(false, Stage.SETTLE_AFTER_UNREPAIRED_RETEST,
                    "unrepaired-retest");
                break;
            case RETEST_STEP:
                stepRetest();
                break;
            case SETTLE_AFTER_UNREPAIRED_RETEST:
                settle("Q30 unrepaired retest");
                stage = Stage.OBSERVATION_BEGIN;
                break;
            case OBSERVATION_BEGIN:
                beginObservations();
                break;
            case OBSERVATION_STEP:
                stepObservation();
                break;
            case OBSERVATION_FINISH:
                finishObservations();
                break;
            case CLEAR_INSTRUMENTS:
                sim.instrumentController.clearTargets();
                stage = Stage.START_OBSERVATION_DISCHARGE;
                break;
            case START_OBSERVATION_DISCHARGE:
                startDischarge(Stage.SETTLE_AFTER_OBSERVATION_DISCHARGE);
                break;
            case DISCHARGE_CHUNK:
                dischargeChunk();
                break;
            case SETTLE_AFTER_OBSERVATION_DISCHARGE:
                settle("Q30 post-observation discharge");
                if ("KB".equals(component)) {
                    // E03's five-terminal relay owner supports whole-part service.
                    // Absence of lead service is an explicit capability expectation.
                    require(!sim.pcbWorkbenchController.isAvailable(
                        WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD,
                            original, component, pad)) &&
                        !sim.pcbWorkbenchController.isAvailable(
                        WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD,
                            original, component, pad)),
                        "relay unexpectedly exposes individual lead service");
                    stage = Stage.REMOVE_ORIGINAL;
                } else {
                    stage = Stage.LIFT_LEAD;
                }
                break;
            case LIFT_LEAD:
                dispatch(WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD,
                    original, component, pad));
                stage = Stage.SETTLE_AFTER_LIFT;
                break;
            case SETTLE_AFTER_LIFT:
                settle("Q30 lifted lead");
                stage = Stage.CHECK_LIFTED;
                break;
            case CHECK_LIFTED:
                require(!modifications.isFullyRestored(),
                    "lift did not change physical state");
                stage = Stage.RECONNECT_LEAD;
                break;
            case RECONNECT_LEAD:
                dispatch(WorkbenchOperation.forPartLead(
                    WorkbenchOperation.RECONNECT_LEAD, original, component, pad));
                stage = Stage.SETTLE_AFTER_RECONNECT;
                break;
            case SETTLE_AFTER_RECONNECT:
                settle("Q30 reconnected lead");
                stage = Stage.REMOVE_ORIGINAL;
                break;
            case REMOVE_ORIGINAL:
                dispatch(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                    original));
                stage = Stage.SETTLE_AFTER_ORIGINAL_REMOVE;
                break;
            case SETTLE_AFTER_ORIGINAL_REMOVE:
                settle("Q30 original removal");
                stage = Stage.CHECK_ORIGINAL_REMOVED;
                break;
            case CHECK_ORIGINAL_REMOVED:
                require(owner.getPhysicalBoardRuntime().getInstalledPart(component) == null,
                    "remove retained installed part");
                stage = Stage.INSTALL_ORIGINAL;
                break;
            case INSTALL_ORIGINAL:
                dispatch(WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL,
                    original, component));
                stage = Stage.SETTLE_AFTER_ORIGINAL_INSTALL;
                break;
            case SETTLE_AFTER_ORIGINAL_INSTALL:
                settle("Q30 original reinstall");
                stage = Stage.POWER_ORIGINAL;
                break;
            case POWER_ORIGINAL:
                sim.setBoardPowerState(BoardPowerState.POWERED);
                stage = Stage.SETTLE_AFTER_ORIGINAL_POWER;
                break;
            case SETTLE_AFTER_ORIGINAL_POWER:
                settle("Q30 original powered retest");
                stage = Stage.ORIGINAL_RETEST_BEGIN;
                break;
            case ORIGINAL_RETEST_BEGIN:
                beginRetest(false, Stage.SETTLE_AFTER_ORIGINAL_RETEST,
                    "original-retest");
                break;
            case SETTLE_AFTER_ORIGINAL_RETEST:
                settle("Q30 original retest result");
                stage = Stage.START_ORIGINAL_DISCHARGE;
                break;
            case START_ORIGINAL_DISCHARGE:
                startDischarge(Stage.SETTLE_AFTER_ORIGINAL_DISCHARGE);
                break;
            case SETTLE_AFTER_ORIGINAL_DISCHARGE:
                settle("Q30 original retest discharge");
                stage = Stage.REMOVE_ORIGINAL_AFTER_RETEST;
                break;
            case REMOVE_ORIGINAL_AFTER_RETEST:
                dispatch(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,
                    original));
                stage = Stage.SETTLE_AFTER_SECOND_ORIGINAL_REMOVE;
                break;
            case SETTLE_AFTER_SECOND_ORIGINAL_REMOVE:
                settle("Q30 original removal after retest");
                stage = "KB".equals(component) ? Stage.WRONG_RELAY_INSTALL :
                    Stage.CORRECT_RELAY_INSTALL;
                break;
            case WRONG_RELAY_INSTALL:
                dispatch(WorkbenchOperation.forCatalog(component,
                    Rb30RelayService.CATALOG_PREFIX + "KB_12V"));
                stage = Stage.SETTLE_AFTER_WRONG_INSTALL;
                break;
            case SETTLE_AFTER_WRONG_INSTALL:
                settle("Q30 wrong relay install");
                stage = Stage.POWER_WRONG_RELAY;
                break;
            case POWER_WRONG_RELAY:
                sim.setBoardPowerState(BoardPowerState.POWERED);
                stage = Stage.SETTLE_AFTER_WRONG_POWER;
                break;
            case SETTLE_AFTER_WRONG_POWER:
                settle("Q30 wrong relay powered test");
                stage = Stage.WRONG_RETEST_BEGIN;
                break;
            case WRONG_RETEST_BEGIN:
                beginRetest(false, Stage.SETTLE_AFTER_WRONG_RETEST,
                    "wrong-relay-retest");
                break;
            case SETTLE_AFTER_WRONG_RETEST:
                settle("Q30 wrong relay retest result");
                stage = Stage.START_WRONG_RELAY_DISCHARGE;
                break;
            case START_WRONG_RELAY_DISCHARGE:
                startDischarge(Stage.SETTLE_AFTER_WRONG_DISCHARGE);
                break;
            case SETTLE_AFTER_WRONG_DISCHARGE:
                settle("Q30 wrong relay retest discharge");
                stage = Stage.REMOVE_WRONG_RELAY;
                break;
            case REMOVE_WRONG_RELAY:
                PhysicalPart<?> wrong = owner.getPhysicalBoardRuntime()
                    .getInstalledPart(component);
                require(wrong != null, "wrong relay disappeared before removal");
                dispatch(WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, wrong));
                stage = Stage.SETTLE_AFTER_WRONG_REMOVE;
                break;
            case SETTLE_AFTER_WRONG_REMOVE:
                settle("Q30 wrong relay removal");
                stage = Stage.CORRECT_RELAY_INSTALL;
                break;
            case CORRECT_RELAY_INSTALL:
                dispatch(WorkbenchOperation.forCatalog(component,
                    owner.getDiagnosticProvider().getCorrectCatalogId(owner, component)));
                stage = Stage.SETTLE_AFTER_CORRECT_INSTALL;
                break;
            case SETTLE_AFTER_CORRECT_INSTALL:
                settle("Q30 correct relay install");
                stage = Stage.CHECK_REPLACEMENT;
                break;
            case CHECK_REPLACEMENT:
                verifyReplacement();
                stage = Stage.POWER_CORRECT_RELAY;
                break;
            case POWER_CORRECT_RELAY:
                sim.setBoardPowerState(BoardPowerState.POWERED);
                stage = Stage.SETTLE_AFTER_CORRECT_POWER;
                break;
            case SETTLE_AFTER_CORRECT_POWER:
                settle("Q30 replacement powered retest");
                stage = Stage.CORRECT_RETEST_BEGIN;
                break;
            case CORRECT_RETEST_BEGIN:
                beginRetest(true, Stage.SETTLE_AFTER_CORRECT_RETEST,
                    "repaired-customer-retest");
                break;
            case SETTLE_AFTER_CORRECT_RETEST:
                settle("Q30 repaired customer retest result");
                stage = Stage.FINAL_ASSERTIONS;
                break;
            case FINAL_ASSERTIONS:
                require(challenge.getDiagnosticProofReceipt() == null,
                    "developer result became normal admission proof");
                stage = Stage.COMPLETE;
                break;
            case COMPLETE:
                break;
            default:
                throw new IllegalStateException("Unknown Q30 service stage " + stage);
            }
        }

        private void preflight() {
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
            TreeSet<String> population = new TreeSet<String>();
            for (GeneratedFaultCandidate candidate : owner.getFaultCandidates())
                require(candidate.isAdmitted() && population.add(
                    candidate.getFault().getId() + ":" +
                    candidate.getFault().getTargetComponentId()),
                    "rejected or duplicate hypothesis");
            require(population.toString().equals(
                "[DREV_OPEN:DREV, DRIVE_A_OPEN:RDA, RELAY_B_COIL_OPEN:KB, REN_OPEN:REN, SENSOR_A_OPEN:RSA]"),
                "five-candidate population changed: " + population);
            GeneratedFaultServiceabilityAdmission.validate(owner,
                owner.getFaultBinding());
            require(challenge != null && modifications != null,
                "missing current challenge or modification controller");
            for (String id : owner.getBoard().getPadIds())
                pads.put(id, owner.getSimulationBindings().getEndpoint(id));
            fingerprint = PhysicalBoardFingerprint.of(owner);
            component = owner.getFaultLocus().getComponentId();
            original = owner.getPhysicalBoardRuntime().getInstalledPart(component);
            require(original != null, "faulted original part is not installed");
            Vector<String> componentPads = owner.getBoard().getComponent(component)
                .getPadIds();
            require(componentPads != null && !componentPads.isEmpty(),
                "faulted component has no physical terminal");
            pad = componentPads.firstElement();
            stage = Stage.UNREPAIRED_RETEST_BEGIN;
        }

        private void beginObservations() {
            GeneratedDiagnosticProgram program = owner.getDiagnosticProvider()
                .getObservationProgram();
            observationStepCount = program.getSteps().size();
            observationCursor = GeneratedDiagnosticObservationExecutor.begin(sim,
                owner, program, GeneratedDiagnosticExecutionTrace.builder());
            observationSteps = 0;
            stage = Stage.OBSERVATION_STEP;
        }

        private void stepObservation() {
            require(observationCursor != null,
                "missing production diagnostic observation cursor");
            boolean more = observationCursor.step();
            observationSteps++;
            if (!more) stage = Stage.OBSERVATION_FINISH;
        }

        private void finishObservations() {
            require(observationCursor != null,
                "missing production diagnostic observation cursor");
            samples = observationCursor.finish();
            observationCursor = null;
            require(!samples.isEmpty(), "empty diagnostic measurements");
            require(samples.size() == 37,
                "expected all 37 production diagnostic observations, got " + samples.size());
            stage = Stage.CLEAR_INSTRUMENTS;
        }

        private void beginRetest(boolean expectedPass, Stage next,
                String label) {
            require(challenge.isReady() && sim.isGeneratedRuntimeSettled() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
                "customer retest requires the actual settled powered challenge");
            retestExpectedPass = expectedPass;
            afterRetest = next;
            retestLabel = label;
            retestSteps = 0;
            savedRetestDispatch = challenge
                .getRetestCompletionDispatchForDeveloperVerification();
            challenge.setRetestCompletionDispatchForDeveloperVerification(
                new GeneratedChallengeController.RetestCompletionDispatch() {
                    public void dispatch(Runnable completion) {
                        completion.run();
                    }
                });
            retestDispatchSaved = true;
            try {
                retestWork = challenge.beginCustomerRetest();
                require(retestWork != null, "customer retest returned no work");
                stage = Stage.RETEST_STEP;
            } catch (Throwable failure) {
                restoreRetestDispatch();
                throw failure;
            }
        }

        private void stepRetest() {
            require(retestWork != null, "missing customer retest work");
            if (retestWork.step()) {
                retestSteps++;
                return;
            }
            GeneratedCustomerRetestResult result = retestWork.finish();
            retestWork = null;
            retestSteps++;
            restoreRetestDispatch();
            require(result != null, "customer retest returned no result");
            boolean passed = result.isPassed();
            require(passed == retestExpectedPass,
                retestExpectedPass ? "replacement failed customer retest" :
                    retestLabel.startsWith("wrong-") ?
                        "wrong 12 V relay passed the 5 V function test" :
                        retestLabel.startsWith("unrepaired") ?
                            "unrepaired customer retest passed" :
                            "reinstalling original cleared its fault");
            stage = afterRetest;
        }

        private void restoreRetestDispatch() {
            if (!retestDispatchSaved) return;
            if (sim.getGeneratedChallengeController() == challenge)
                challenge.setRetestCompletionDispatchForDeveloperVerification(
                    savedRetestDispatch);
            savedRetestDispatch = null;
            retestDispatchSaved = false;
        }

        private void startDischarge(Stage next) {
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            afterDischarge = next;
            dischargeSteps = 0;
            validateCurrent("start-discharge", true);
            stage = Stage.DISCHARGE_CHUNK;
        }

        private void dischargeChunk() {
            validateCurrent("discharge-chunk", true);
            sim.advanceGeneratedTemporalProfile(.005);
            dischargeSteps++;
            validateCurrent("after-discharge-chunk", true);
            boolean relayTarget = "KB".equals(component);
            boolean discharged = !relayTarget ||
                Rb30RelayService.isDischarged(owner, component);
            if (dischargeSteps < 20 || relayTarget && !discharged &&
                    dischargeSteps < 1000)
                return;
            if (relayTarget)
                require(discharged,
                    "relay still energized after five simulated seconds");
            stage = afterDischarge;
            afterDischarge = null;
        }

        private void verifyReplacement() {
            require(owner.getPhysicalBoardRuntime().getInstalledPart(component) != original,
                "replacement reused original fault owner");
            for (String id : pads.keySet())
                require(owner.getSimulationBindings().getEndpoint(id) == pads.get(id),
                    "physical mutation changed board endpoint " + id);
            require(fingerprint.equals(PhysicalBoardFingerprint.of(owner)),
                "mutation changed board/copper identity");
            owner.getConnectionBindings().validateAgainst(owner.getBoard(),
                owner.getSimulationElements(), owner.getComponentBindings(),
                owner.getExternalPowerBindings(), owner.getFaultBinding());
        }

        private void dispatch(WorkbenchOperation operation) {
            require(sim.pcbWorkbenchController != null &&
                sim.pcbWorkbenchController.isAvailable(operation),
                "unavailable " + operation.getId());
            require(sim.pcbWorkbenchController.dispatch(operation),
                "failed " + operation.getId());
        }

        private void settle(String description) {
            GeneratedRuntimeDeveloperSettlement.settle(sim, owner, description);
        }

        private void validateCurrent(String boundary, boolean requireUnpowered) {
            require(sim != null && owner != null &&
                sim.getGeneratedBoardInstance() == owner && sim.elmList == graph &&
                sim.getBoardModificationController() == modifications &&
                sim.getGeneratedChallengeController() == challenge,
                "lost exact owner before " + boundary);
            require(sim.troubleshootDebug && owner.isDeveloperOnlyFaultRoute(),
                "developer-only verification scope ended before " + boundary);
            if (requireUnpowered)
                require(sim.getBoardPowerController() != null &&
                    sim.getBoardPowerController().isElectricallyUnpowered(),
                    "discharge lost its live unpowered owner at " + boundary);
        }

        private Throwable withPhase(Throwable failure) {
            return new IllegalStateException("Q30 service phase " + phase + ": " +
                failure, failure);
        }

        void failAtCurrentPhase(Throwable failure) {
            finish(withPhase(failure));
        }

        private void finish(Throwable failure) {
            if (completed) return;
            completed = true;
            Throwable outcome = failure;
            if (outcome != null && isCurrentOwner()) {
                boolean priorDeveloperScope = sim.developerVerifierRunning;
                sim.developerVerifierRunning = true;
                try {
                    if (observationCursor != null) {
                        try { observationCursor.cancel(); }
                        catch (Throwable cleanup) { outcome = retain(outcome, cleanup); }
                        observationCursor = null;
                    }
                    if (retestWork != null) {
                        try { retestWork.cancel(); }
                        catch (Throwable cleanup) { outcome = retain(outcome, cleanup); }
                        retestWork = null;
                    }
                    try { restoreRetestDispatch(); }
                    catch (Throwable cleanup) { outcome = retain(outcome, cleanup); }
                } finally {
                    sim.developerVerifierRunning = priorDeveloperScope;
                }
            }
            long elapsedMillis = Math.max(0,
                System.currentTimeMillis() - startedMillis);
            try {
                if (isCurrentOwner())
                    publish(outcome == null ? passReceipt(elapsedMillis) :
                        failureReceipt(outcome, elapsedMillis));
            } catch (Throwable reportFailure) {
                outcome = retain(outcome, reportFailure);
            }
            completion.complete(outcome, elapsedMillis);
        }

        private boolean isCurrentOwner() {
            return sim != null && owner != null &&
                sim.getGeneratedBoardInstance() == owner && sim.elmList == graph &&
                sim.getBoardModificationController() == modifications &&
                sim.getGeneratedChallengeController() == challenge;
        }

        private String passReceipt(long elapsedMillis) {
            StringBuilder json = new StringBuilder(
                "{\"status\":\"PASS\",\"normalAdmission\":false,\"d01Admission\":false,\"seed\":\"")
                .append(Long.toString(owner.getSeed())).append("\",\"fault\":\"")
                .append(owner.getFaultBinding().getFault().getId())
                .append("\",\"hypotheses\":")
                .append(owner.getFaultCandidates().size())
                .append(",\"leadService\":\"")
                .append("KB".equals(component) ? "NOT_SUPPORTED" : "PASS")
                .append("\"")
                .append(",\"unrepairedRetest\":false,\"originalRetest\":false,\"repairedRetest\":true,\"elapsedMillis\":")
                .append(elapsedMillis).append(",\"samples\":[");
            for (GeneratedDiagnosticSample sample : samples) {
                if (json.charAt(json.length() - 1) != '[') json.append(',');
                json.append("{\"id\":\"").append(sample.getSampleId())
                    .append("\",\"value\":")
                    .append(sample.isOverRange() ? "null" :
                        Double.toString(sample.getValue()))
                    .append(",\"tolerance\":")
                    .append(sample.isOverRange() ? "null" :
                        Double.toString(sample.getComparisonTolerance()))
                    .append('}');
            }
            return json.append("]}").toString();
        }

        private String failureReceipt(Throwable failure, long elapsedMillis) {
            return "{\"status\":\"FAIL\",\"normalAdmission\":false,\"d01Admission\":false,\"seed\":\"" +
                (owner == null ? "unknown" : Long.toString(owner.getSeed())) +
                "\",\"phase\":\"" + escape(phase) + "\",\"elapsedMillis\":" +
                elapsedMillis + ",\"blocker\":\"" + escape(String.valueOf(failure)) +
                "\"}";
        }

        private void publishProgress(String currentPhase) {
            publish("{\"status\":\"RUNNING\",\"phase\":\"" +
                escape(currentPhase) + "\",\"elapsedMillis\":" +
                Math.max(0, System.currentTimeMillis() - startedMillis) + "}");
        }

        private Throwable retain(Throwable primary, Throwable secondary) {
            if (primary == null) return secondary;
            if (secondary != null && secondary != primary)
                primary.addSuppressed(secondary);
            return primary;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Q30 service: " + message);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-q30-service", report);
    }-*/;
}
