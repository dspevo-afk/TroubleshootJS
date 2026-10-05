package com.lushprojects.circuitjs1.client;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import java.util.Collections;
import java.util.Map;
import java.util.Vector;

/**
 * Debug-only cold and warm D01 canary for the normal Q30 owner. The candidate is
 * installed only inside a guarded staged transaction and is always aborted;
 * this verifier never publishes a player board or registers family content.
 */
final class Q30DiagnosticAdmissionVerifier {
    private static final String REQUEST_CANONICAL =
        "tsj-q30-normal-d01-qualification-request@1;family=RB30;profile=MEDIUM";
    private static final int EXPECTED_HYPOTHESIS_COUNT = 5;
    private static final String SOURCE_CONTEXT_INPUT = "MAIN12";
    private static Runner active;

    private Q30DiagnosticAdmissionVerifier() { }

    static void start(CirSim sim, String requestedSeed) {
        if (sim == null || !sim.troubleshootDebug || !sim.troubleshootQ30Verification)
            throw new IllegalStateException("Q30 D01 requires explicit debug verification");
        if (sim.developerVerifierRunning || active != null ||
                !sim.isGeneratedRuntimeSettled())
            throw new IllegalStateException("Q30 D01 requires an idle, settled developer workbench");
        final long seed = parseSeed(requestedSeed);
        Runner runner = new Runner(sim, seed, requestedSeed == null ? "0" : requestedSeed);
        active = runner;
        sim.developerVerifierRunning = true;
        runner.publishState("RUNNING", "construction");
        runner.schedule();
    }

    /**
     * Retries only this verifier's retained cleanup transaction. The exact
     * session and staged handles perform their own current-owner checks.
     */
    static boolean retryPendingCleanup(CirSim sim) {
        Runner runner = active;
        return runner != null && runner.sim == sim && runner.retryPendingCleanup();
    }

    private static long parseSeed(String requestedSeed) {
        if (requestedSeed == null) return 0L;
        if (requestedSeed.length() == 0)
            throw new IllegalArgumentException("Q30 D01 seed cannot be explicitly empty");
        final long seed;
        try { seed = Long.parseLong(requestedSeed); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Q30 D01 seed is not a signed long: " + requestedSeed);
        }
        if (!Long.toString(seed).equals(requestedSeed))
            throw new IllegalArgumentException("Q30 D01 seed is not canonical: " + requestedSeed);
        return seed;
    }

    private static final class Runner {
        private enum Phase {
            CONSTRUCT, PREPARE, CLEANUP_CANARY, PROOF, COMPLETE, CLEANUP,
            CONSTRUCT_WARM, PREPARE_WARM, WARM, SERVICE_CANARIES,
            CLEANUP_PENDING, DONE
        }

        private enum ServiceCanaryStage {
            START_HEALTHY_SETUP, HEALTHY_PROFILE, DRIVE_HIGH, CHECK_ENERGIZED_RELAY,
            POWER_OFF_RELAY, RELAY_FIRST_READINESS, SETTLE_RELAY_ISOLATION,
            RELAY_READINESS, RESISTOR_READINESS, GUARD_CANARIES,
            CANCELLATION, RESTORE_OFF, RESTORE_FAULT_AND_POWER, RESTORE_FAULT_PROFILE,
            VERIFY_RESTORED, DONE
        }

        private final CirSim sim;
        private final long seed;
        private final String requestedSeed;
        private final boolean predecessorVerifierRunning;
        private final boolean predecessorInstallationInProgress;
        private final GeneratedBoardInstance predecessorOwner;
        private final GeneratedChallengeController predecessorController;
        private final Vector<CircuitElm> predecessorGraph;
        private final long startedMillis = System.currentTimeMillis();
        private final JSONObject report = new JSONObject();
        private final JSONObject negativeEvidence = new JSONObject();
        private final Map<String, long[]> proofUnitTimings =
            new java.util.TreeMap<String, long[]>();
        private final Vector<JSONObject> unfinishedNegatives = new Vector<JSONObject>();
        private final Vector<CircuitElm> uninstalledDisposedElements = new Vector<CircuitElm>();
        private final GeneratedDiagnosticProofCache verificationCache =
            new GeneratedDiagnosticProofCache();
        private final Timer timer = new Timer() {
            @Override public void run() { advance(); }
        };

        private Phase phase = Phase.CONSTRUCT;
        private String phaseName = "construction";
        private String failurePhase;
        private Throwable failure;
        private StagedFamilyCapability.ConstructionSession constructionSession;
        private String expectedConstructionPlanCanonical;
        private GenerationRequest.Construction construction;
        private GeneratedBoardInstance owner;
        private GeneratedChallengeController controller;
        private FreshGeneratedRuntimeInstallation.Staged staged;
        private GeneratedDiagnosticProofService.Session proof;
        private GeneratedDiagnosticProofService.Session cleanupFailureSession;
        private GeneratedDiagnosticProofReceipt receipt;
        private GeneratedDiagnosticProofReceipt warmReceipt;
        private GeneratedDiagnosticContextKey contextKey;
        private GeneratedDiagnosticContextKey warmContextKey;
        private GeneratedDiagnosticProofService.CleanupAudit cleanupAudit;
        private GeneratedDiagnosticProofService.CleanupAudit cleanupCanaryAudit;
        private GeneratedDiagnosticProofCache.Entry pendingCacheEntry;
        private Vector<GeneratedDiagnosticSolvabilityEvidence> coldEvidence;
        private String coldPartitionCanonical;
        private String coldProviderId;
        private String coldProgramIdentity;
        private GeneratedDiagnosticContextKey mismatchedContextKey;
        private String realizationManifest;
        private JSONObject contextMismatchEvidence;
        private Vector<CircuitElm> stagedGraph;
        private long phaseStarted = System.currentTimeMillis();
        private long constructionMillis;
        private long preparationMillis;
        private long physicalValidationMillis;
        private long proofMillis;
        private long proofActiveWorkMillis;
        private long cleanupCanaryMillis;
        private long warmConstructionMillis;
        private long warmPreparationMillis;
        private long classificationMillis;
        private long cleanupMillis;
        private int proofUnits;
        private int cleanupCanaryUnits;
        private boolean cleanupFailureInjectionArmed;
        private boolean coldStageCleaned;
        private boolean warmComplete;
        private boolean cleanupComplete;
        private boolean ownerRestored;
        private boolean reportHasEvidence;
        private int cleanupRetryCount;
        private JSONObject servicePreparationCanaries;
        private JSONObject serviceCanaryCases;
        private JSONObject serviceCanaryCleanup;
        private ServiceCanaryStage serviceCanaryStage;
        private GeneratedDiagnosticServicePreparation.Policy serviceCanaryPolicy;
        private GeneratedDiagnosticServicePreparation.Cursor serviceCanaryCursor;
        private GeneratedWork<GeneratedRepairStatus> serviceHealthyWork;
        private GeneratedBoardInstance serviceCanaryOwner;
        private GeneratedChallengeController serviceCanaryController;
        private GeneratedFaultBinding serviceCanaryFaultBinding;
        private GeneratedExternalPowerBindings serviceCanaryBindings;
        private GeneratedExternalPowerBindings.SavedControls serviceCanarySavedControls;
        private String serviceCanaryInitialControlSignature;
        private Vector<CircuitElm> serviceCanaryGraph;
        private Vector<CircuitElm> serviceCanaryGraphContents;
        private Vector<String> serviceCanaryInputIds;
        private ExternalPowerSimulationBinding[] serviceCanaryPowerBindings;
        private LimitedDcSupplyElm[] serviceCanarySources;
        private double[] serviceCanarySourceVoltages;
        private double[] serviceCanarySourceLimits;
        private BoardPowerState serviceCanaryPowerState;
        private boolean serviceCanaryFaultWasApplied;
        private int serviceCanaryInput;
        private boolean serviceCanarySimWasRunning;
        private double serviceCanaryMaximumStep, serviceCanaryMinimumStep;
        private boolean serviceCanaryAdaptiveStep;
        private String serviceRelayComponentId;
        private PhysicalPart<?> serviceRelayTarget;
        private double serviceRelayCoilCurrentBeforePowerOff;
        private double serviceRelayCoilCurrentAfterPowerOff;
        private JSONObject pendingServiceCase;
        private long pendingServiceCaseStartedMillis;
        private long serviceCanaryStartedMillis;
        private int serviceCanaryCreatedCount;
        private int serviceCanaryClosedCount;
        private int serviceCanaryCursorUnits;
        private int serviceCanaryWorkUnitsCompleted;
        private int serviceCanaryCursorAdvances;
        private long serviceCanarySetupMillis;
        private double serviceCanaryInitialSimulationTime;
        private boolean serviceCanaryScopeActive;
        private boolean serviceCanariesComplete;

        Runner(CirSim sim, long seed, String requestedSeed) {
            this.sim = sim;
            this.seed = seed;
            this.requestedSeed = requestedSeed;
            predecessorVerifierRunning = sim.developerVerifierRunning;
            predecessorInstallationInProgress = sim.generatedRuntimeInstallationInProgress;
            predecessorOwner = sim.getGeneratedBoardInstance();
            predecessorController = sim.getGeneratedChallengeController();
            predecessorGraph = sim.elmList;
            put(report, "schema", 2);
            put(report, "seed", Long.toString(seed));
            put(report, "requestedSeed", requestedSeed);
            put(report, "requestManifest", REQUEST_CANONICAL);
            put(report, "normalAdmission", false);
            put(report, "registered", PlayerFamilyCatalog.contains(Rb30Plan.FAMILY_ID));
            put(report, "playerPublished", false);
            put(report, "normalOwner", false);
            put(report, "d01Admission", false);
            put(report, "restored", false);
            put(report, "cleanupPending", false);
            put(report, "cleanupRetryCount", 0);
            JSONObject warm = new JSONObject();
            put(warm, "status", "NOT_RUN");
            report.put("warm", warm);
            report.put("negatives", negativeEvidence);
        }

        void schedule() { timer.schedule(1); }

        private boolean retryPendingCleanup() {
            if (phase != Phase.CLEANUP_PENDING) return false;
            cleanupRetryCount++;
            put(report, "cleanupPending", false);
            put(report, "cleanupRetryCount", cleanupRetryCount);
            put(report, "cleanupRetryRequested", true);
            phase = Phase.CLEANUP;
            phaseName = "cleanupRetry";
            phaseStarted = System.currentTimeMillis();
            timer.cancel();
            schedule();
            publishState("RUNNING", "cleanupRetry");
            return true;
        }

        void publishState(String status, String state) {
            put(report, "status", status);
            put(report, "phase", state);
            put(report, "elapsedMs", elapsed());
            publish(report.toString(), status + ":" + state);
        }

        private void advance() {
            if (phase == Phase.DONE || phase == Phase.CLEANUP_PENDING) return;
            try {
                // Cleanup must remain callable after its debug scope is revoked.
                if (phase != Phase.CLEANUP)
                    require(sim.troubleshootDebug && sim.troubleshootQ30Verification,
                        "Q30 debug scope ended during D01 verification");
                switch (phase) {
                case CONSTRUCT:
                    construct();
                    break;
                case PREPARE:
                    prepare();
                    break;
                case SERVICE_CANARIES:
                    runServicePreparationCanaryUnit();
                    break;
                case CLEANUP_CANARY:
                    cleanupFailureCanaryOneUnit();
                    break;
                case PROOF:
                    proveOneUnit();
                    break;
                case COMPLETE:
                    completeProof();
                    break;
                case CLEANUP:
                    cleanupAndFinish();
                    break;
                case CONSTRUCT_WARM:
                    constructWarm();
                    break;
                case PREPARE_WARM:
                    prepareWarm();
                    break;
                case WARM:
                    reuseWarmCache();
                    break;
                default:
                    return;
                }
            } catch (Throwable problem) {
                if (servicePreparationCanaries != null &&
                        "RUNNING".equals(stringValue(
                            servicePreparationCanaries.get("status")))) {
                    put(servicePreparationCanaries, "status", "FAIL");
                    put(servicePreparationCanaries, "failure", describe(problem));
                    put(servicePreparationCanaries, "failureStage",
                        serviceCanaryStage == null ? phaseName : serviceCanaryStage.name());
                }
                if (failure == null) {
                    failure = problem;
                    failurePhase = phaseName;
                } else {
                    failure = retain(failure, problem);
                }
                if (phase == Phase.CLEANUP_PENDING) {
                    timer.cancel();
                } else if (phase == Phase.CLEANUP) {
                    cleanupMillis += sincePhaseStart();
                    parkCleanupPending("cleanupException", problem);
                } else {
                    constructionSession = null;
                    phase = Phase.CLEANUP;
                    phaseName = "cleanup";
                    phaseStarted = System.currentTimeMillis();
                    publishState("RUNNING", "cleanup");
                    schedule();
                }
                return;
            }
            if (phase != Phase.DONE && phase != Phase.CLEANUP_PENDING) schedule();
        }

        private void parkCleanupPending(String ownerKind, Throwable problem) {
            if (failurePhase == null) failurePhase = "cleanup";
            failure = retain(failure, problem == null ? new IllegalStateException(
                "D01 cleanup remains incomplete") : problem);
            ownerRestored = sim.getGeneratedBoardInstance() == predecessorOwner &&
                sim.getGeneratedChallengeController() == predecessorController &&
                sim.elmList == predecessorGraph &&
                sim.generatedRuntimeInstallationInProgress == predecessorInstallationInProgress;
            cleanupComplete = false;
            put(report, "cleanupPending", true);
            put(report, "cleanupPendingOwner", ownerKind);
            put(report, "cleanupPendingReason", describe(problem));
            put(report, "cleanupRetryCount", cleanupRetryCount);
            put(report, "ownerRestored", ownerRestored);
            put(report, "restored", ownerRestored);
            put(report, "cleanupComplete", false);
            phase = Phase.CLEANUP_PENDING;
            phaseName = "cleanupPending";
            phaseStarted = System.currentTimeMillis();
            timer.cancel();
            publishState("CLEANUP_PENDING", "cleanupPending");
        }

        private void construct() {
            phaseName = "construction";
            if (constructionSession == null) beginFamilyConstruction();
            if (!constructionSession.advance()) {
                publishProgress("construction", 0, 0);
                return;
            }
            construction = constructionSession.result();
            constructionSession = null;
            constructionMillis += sincePhaseStart();
            owner = construction.instance;
            require(expectedConstructionPlanCanonical.equals(
                    construction.realizationManifest),
                "Q30 staged construction changed its resolved plan identity");
            require(owner != null && !owner.isDeveloperOnlyFaultRoute(),
                "Q30 normal generator returned a developer-only owner");
            require(owner.getSeed() == seed && Rb30Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()),
                "Q30 normal generator returned a foreign family or seed");
            owner.requireNormalPhysicalAdmission();
            realizationManifest = construction.realizationManifest;
            put(report, "normalOwner", true);
            staged = new FreshGeneratedRuntimeInstallation.Staged(sim, owner);
            phase = Phase.PREPARE;
            phaseName = "stagedPreparation";
            phaseStarted = System.currentTimeMillis();
            staged.prepare(false);
            if (!staged.isPreparationComplete()) staged.pause();
            publishProgress("stagedPreparation", 0, 0);
        }

        private void prepare() {
            phaseName = "stagedPreparation";
            if (!staged.isPreparationComplete()) {
                staged.finishPreparation();
                if (!staged.isPreparationComplete()) staged.pause();
            }
            if (!staged.isPreparationComplete()) {
                publishProgress("stagedPreparation", 0, 0);
                return;
            }
            preparationMillis += sincePhaseStart();
            controller = sim.getGeneratedChallengeController();
            GeneratedChallengeLifecycleEvidence lifecycle = controller == null ? null :
                controller.getLifecycleEvidence();
            require(controller != null && lifecycle != null && lifecycle.healthyFamilyValidated &&
                    lifecycle.selectedFaultValidated && sim.getGeneratedBoardInstance() == owner,
                "Q30 staged owner lacks live healthy and selected-fault validation");

            phaseName = "physicalValidation";
            phaseStarted = System.currentTimeMillis();
            staged.validatePhysical();
            physicalValidationMillis += sincePhaseStart();

            if (!serviceCanariesComplete) {
                beginServicePreparationCanaries();
                return;
            }

            contextKey = GeneratedDiagnosticContextKey.capture(sim, owner,
                REQUEST_CANONICAL, construction.realizationManifest);
            require(contextKey.isTrustedCapture(), "Q30 D01 context is not a complete capture");
            if (serviceCanaryCleanup != null) {
                put(serviceCanaryCleanup, "contextCapturedAfterCanaries", true);
                put(servicePreparationCanaries, "status", "PASS");
                put(servicePreparationCanaries, "passedCount", 7);
                put(servicePreparationCanaries, "totalSolverAdvances",
                    serviceCanaryCursorAdvances);
                put(servicePreparationCanaries, "healthyProfileAdvanceUnits",
                    serviceCanaryCursorUnits);
                put(servicePreparationCanaries, "serviceCursorWorkUnitsCompleted",
                    serviceCanaryWorkUnitsCompleted);
                put(servicePreparationCanaries, "setupElapsedMs", serviceCanarySetupMillis);
                put(servicePreparationCanaries, "elapsedMs",
                    System.currentTimeMillis() - serviceCanaryStartedMillis);
                put(servicePreparationCanaries, "cleanupComplete", true);
            }
            require(owner.getDiagnosticProvider() != null &&
                    owner.getDiagnosticProvider().getProviderId() != null,
                "Q30 normal owner has no diagnostic provider");
            stagedGraph = sim.elmList;

            runIncompleteSessionCanary();
            runForeignHypothesisCanary();
            beginCleanupFailureCanary();
        }

        private void beginServicePreparationCanaries() {
            require(servicePreparationCanaries == null && owner != null && controller != null,
                "Q30 service-preparation canaries already started or lack a current owner");
            require(owner.getDiagnosticProvider() instanceof
                    GeneratedDiagnosticServicePreparation.Provider,
                "Q30 normal provider has no service-preparation policy");
            serviceCanaryPolicy = ((GeneratedDiagnosticServicePreparation.Provider)
                owner.getDiagnosticProvider()).getServicePreparationPolicy();
            require(serviceCanaryPolicy != null && serviceCanaryPolicy.getWorkUnits() == 5 &&
                    serviceCanaryPolicy.getMaximumAdvanceSeconds() == .05,
                "Q30 service-preparation policy differs from the qualified 5 x 50 ms bound");
            serviceCanaryOwner = owner;
            serviceCanaryController = controller;
            serviceCanaryFaultBinding = owner.getFaultBinding();
            serviceCanaryBindings = owner.getExternalPowerBindings();
            serviceCanarySavedControls = serviceCanaryBindings.saveControls();
            serviceCanaryInitialControlSignature = serviceCanaryBindings.controlSignature();
            serviceCanaryGraph = sim.elmList;
            serviceCanaryGraphContents = new Vector<CircuitElm>(sim.elmList);
            serviceCanaryPowerState = sim.getBoardPowerController().getState();
            serviceCanaryFaultWasApplied = serviceCanaryFaultBinding != null &&
                serviceCanaryFaultBinding.isApplied();
            require(serviceCanaryFaultWasApplied &&
                    serviceCanaryPowerState == BoardPowerState.POWERED &&
                    serviceCanarySavedControls.matches(),
                "Q30 canary requires the original powered, fault-applied owner");
            if (!(owner.getTemporalBehavior() instanceof Rb30Behavior))
                throw new IllegalStateException("Q30 canary requires the real Rb30 behavior");
            serviceCanaryInput = ((Rb30Behavior)owner.getTemporalBehavior()).getInputs();
            int highMask = owner.getBoard().getComponent("JOB") == null ? 1 : 3;
            require(serviceCanaryInput == highMask,
                "Q30 staged selected-fault owner did not retain its declared HIGH input");
            serviceCanarySimWasRunning = sim.simIsRunning();
            serviceCanaryInitialSimulationTime = sim.t;
            serviceCanaryMaximumStep = sim.maxTimeStep;
            serviceCanaryMinimumStep = sim.minTimeStep;
            serviceCanaryAdaptiveStep = sim.adjustTimeStep;
            serviceCanaryInputIds = owner.getBoard().getPowerInputIds();
            serviceCanaryPowerBindings = new ExternalPowerSimulationBinding[
                serviceCanaryInputIds.size()];
            serviceCanarySources = new LimitedDcSupplyElm[serviceCanaryInputIds.size()];
            serviceCanarySourceVoltages = new double[serviceCanaryInputIds.size()];
            serviceCanarySourceLimits = new double[serviceCanaryInputIds.size()];
            for (int i = 0; i < serviceCanaryInputIds.size(); i++) {
                ExternalPowerSimulationBinding binding = serviceCanaryBindings.getBinding(
                    serviceCanaryInputIds.get(i));
                LimitedDcSupplyElm source = binding.getLimitedSupply();
                require(source != null && owner.getSimulationElements().contains(source),
                    "Q30 canary lost a current external source");
                serviceCanaryPowerBindings[i] = binding;
                serviceCanarySources[i] = source;
                serviceCanarySourceVoltages[i] = source.maxVoltage;
                serviceCanarySourceLimits[i] = source.getLimitAmps();
            }

            servicePreparationCanaries = new JSONObject();
            serviceCanaryCases = new JSONObject();
            serviceCanaryCleanup = new JSONObject();
            put(servicePreparationCanaries, "status", "RUNNING");
            put(servicePreparationCanaries, "providerId",
                owner.getDiagnosticProvider().getProviderId());
            put(servicePreparationCanaries, "policyCanonical", serviceCanaryPolicy.canonical());
            put(servicePreparationCanaries, "caseCount", 7);
            put(servicePreparationCanaries, "passedCount", 0);
            put(servicePreparationCanaries, "totalSolverAdvances", 0);
            put(servicePreparationCanaries, "healthyProfileAdvanceUnits", 0);
            put(servicePreparationCanaries, "cleanupComplete", false);
            servicePreparationCanaries.put("cases", serviceCanaryCases);
            servicePreparationCanaries.put("cleanup", serviceCanaryCleanup);
            report.put("servicePreparationCanaries", servicePreparationCanaries);
            serviceCanaryStartedMillis = System.currentTimeMillis();
            serviceCanaryStage = ServiceCanaryStage.START_HEALTHY_SETUP;
            serviceCanaryScopeActive = true;
            sim.setSimRunning(false);
            controller.beginDeveloperVerificationScope();
            phase = Phase.SERVICE_CANARIES;
            phaseName = "servicePreparationCanaries";
            phaseStarted = System.currentTimeMillis();
            publishProgress("servicePreparationCanaries", 0, 7);
        }

        /** One healthy-profile or service-readiness unit per verifier timer turn. */
        private void runServicePreparationCanaryUnit() {
            phaseName = "servicePreparationCanaries";
            switch (serviceCanaryStage) {
            case START_HEALTHY_SETUP:
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-isolate-before-healthy");
                require(serviceCanaryController.getFaultController()
                        .clearForDeveloperVerification() &&
                        !serviceCanaryFaultBinding.isApplied(),
                    "Q30 healthy relay canary did not clear the selected fault through its binding");
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-clear-selected-fault");
                sim.setBoardPowerState(BoardPowerState.POWERED);
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-power-healthy-owner");
                serviceHealthyWork = serviceCanaryOwner.getTemporalBehavior().beginProfile(
                    sim, serviceCanaryOwner, GeneratedTemporalBehavior.Profile.HEALTHY);
                int expectedProfileUnits = ((Rb30Behavior)
                    serviceCanaryOwner.getTemporalBehavior()).getProfileWorkUnits();
                require(serviceHealthyWork != null &&
                        serviceHealthyWork.getWorkUnits() == expectedProfileUnits,
                    "Q30 healthy relay canary profile units differ from active-channel recipe");
                serviceCanaryStage = ServiceCanaryStage.HEALTHY_PROFILE;
                publishProgress("servicePreparationCanaries", 0, 7);
                return;
            case HEALTHY_PROFILE:
                double healthyTime = sim.t;
                boolean moreHealthy = serviceHealthyWork.step();
                if (sim.t > healthyTime) serviceCanaryCursorUnits++;
                if (!moreHealthy) {
                    require(serviceHealthyWork.finish() == GeneratedRepairStatus.CORRECTLY_RESTORED,
                        "Q30 healthy setup did not prove both output channels on the live graph");
                    serviceHealthyWork = null;
                    serviceCanaryStage = ServiceCanaryStage.DRIVE_HIGH;
                }
                publishProgress("servicePreparationCanaries", 0, 7);
                return;
            case DRIVE_HIGH:
                ((Rb30Behavior)serviceCanaryOwner.getTemporalBehavior()).setInputs(
                    sim, serviceCanaryOwner,
                    serviceCanaryOwner.getBoard().getComponent("JOB") == null ? 1 : 3);
                serviceCanaryStage = ServiceCanaryStage.CHECK_ENERGIZED_RELAY;
                return;
            case CHECK_ENERGIZED_RELAY:
                selectEnergizedRelayForCanary();
                serviceCanarySetupMillis = System.currentTimeMillis() - serviceCanaryStartedMillis;
                serviceCanaryStage = ServiceCanaryStage.POWER_OFF_RELAY;
                return;
            case POWER_OFF_RELAY:
                double powerOffTime = sim.t;
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                require(sim.t == powerOffTime &&
                        sim.getBoardPowerController().isElectricallyUnpowered(),
                    "Q30 relay canary did not isolate without advancing stored energy");
                // Capture the real charged graph before ordinary settlement's
                // isolated live cadence can discharge it. The cursor owns the
                // first bounded off-state solver advance; settlement follows.
                serviceRelayCoilCurrentAfterPowerOff = relayCoilCurrent(serviceRelayTarget);
                require(Math.abs(serviceRelayCoilCurrentAfterPowerOff) >=
                        RelayOutputBehavior.DISCHARGED_AMPS,
                    "Q30 relay energy disappeared before the bounded service cursor ran");
                PhysicalPart<?> relay = serviceRelayTarget;
                require(!Rb30RelayService.isDischarged(serviceCanaryOwner,
                        serviceRelayComponentId),
                    "Q30 charged relay did not retain its physical residual-energy guard");
                require(!serviceCanaryAvailable(serviceRelayComponentId, relay),
                    "Q30 energized relay REMOVE became available before bounded discharge work");
                serviceCanaryCursor = beginServiceCanaryCursor(serviceRelayComponentId, relay);
                serviceCanaryCreatedCount++;
                pendingServiceCase = serviceCase("relayDelayed", "DELAYED_READY_WITHIN_BOUND",
                    serviceRelayComponentId, relay);
                put(pendingServiceCase, "availableBefore", false);
                put(pendingServiceCase, "coilCurrentBeforePowerOffAmps",
                    serviceRelayCoilCurrentBeforePowerOff);
                put(pendingServiceCase, "coilCurrentAtCursorCaptureAmps",
                    serviceRelayCoilCurrentAfterPowerOff);
                put(pendingServiceCase, "physicalRelayDischargedAtCursorCapture", false);
                put(pendingServiceCase, "runtimeSettledAtCursorCapture",
                    sim.isGeneratedRuntimeSettled());
                put(pendingServiceCase, "maxSolverAdvances", serviceCanaryPolicy.getWorkUnits());
                serviceCanaryStage = ServiceCanaryStage.RELAY_FIRST_READINESS;
                return;
            case RELAY_FIRST_READINESS:
                double firstCursorTime = sim.t;
                runServiceCanaryCursorUnit("relayDelayed", "DELAYED_READY_WITHIN_BOUND");
                require(serviceCanaryCursor != null && pendingServiceCase != null &&
                        serviceCanaryCursor.getCompletedUnits() == 1 && sim.t > firstCursorTime,
                    "Q30 charged relay did not consume its first real bounded cursor advance");
                put(pendingServiceCase, "coilCurrentAfterFirstCursorAdvanceAmps",
                    relayCoilCurrent(serviceRelayTarget));
                put(pendingServiceCase, "physicalRelayDischargedAfterFirstCursorAdvance",
                    Rb30RelayService.isDischarged(serviceCanaryOwner, serviceRelayComponentId));
                serviceCanaryStage = ServiceCanaryStage.SETTLE_RELAY_ISOLATION;
                return;
            case SETTLE_RELAY_ISOLATION:
                double isolationSettlementTime = sim.t;
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-settle-after-first-relay-cursor-advance");
                require(sim.getBoardPowerController().isElectricallyUnpowered() &&
                        sim.isGeneratedRuntimeSettled() && !sim.simIsRunning(),
                    "Q30 relay canary settlement lost isolated, settled, paused ownership");
                // This is ordinary pending verification work, not cursor work.
                put(pendingServiceCase, "isolationSettlementAdvanceSeconds",
                    sim.t - isolationSettlementTime);
                put(pendingServiceCase, "availableAfterIsolationSettlement",
                    serviceCanaryAvailable(serviceRelayComponentId, serviceRelayTarget));
                serviceCanaryStage = ServiceCanaryStage.RELAY_READINESS;
                return;
            case RELAY_READINESS:
                runServiceCanaryCursorUnit("relayDelayed", "DELAYED_READY_WITHIN_BOUND");
                if (serviceCanaryCursor == null)
                    serviceCanaryStage = ServiceCanaryStage.RESISTOR_READINESS;
                return;
            case RESISTOR_READINESS:
                if (pendingServiceCase == null) {
                    PhysicalPart<?> resistor = serviceCanaryOwner.getPhysicalBoardRuntime()
                        .getInstalledPart("RDA");
                    require(resistor != null && resistor.isInstalled() &&
                            serviceCanaryAvailable("RDA", resistor),
                        "Q30 resistor REMOVE is not actually available on the unpowered workbench");
                    serviceCanaryCursor = beginServiceCanaryCursor("RDA", resistor);
                    serviceCanaryCreatedCount++;
                    pendingServiceCase = serviceCase("resistorImmediate",
                        "IMMEDIATE_READY_NO_ADVANCE", "RDA", resistor);
                    put(pendingServiceCase, "availableBefore", true);
                }
                runServiceCanaryCursorUnit("resistorImmediate", "IMMEDIATE_READY_NO_ADVANCE");
                if (serviceCanaryCursor == null)
                    serviceCanaryStage = ServiceCanaryStage.GUARD_CANARIES;
                return;
            case GUARD_CANARIES:
                runServicePreparationGuardCanaries();
                serviceCanaryStage = ServiceCanaryStage.CANCELLATION;
                publishProgress("servicePreparationCanaries", 6, 7);
                return;
            case CANCELLATION:
                runServicePreparationCancellationCanary();
                serviceCanaryStage = ServiceCanaryStage.RESTORE_OFF;
                publishProgress("servicePreparationCanaries", 7, 7);
                return;
            case RESTORE_OFF:
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-final-isolation");
                restoreServiceCanarySourceCommands();
                sim.maxTimeStep = serviceCanaryMaximumStep;
                sim.minTimeStep = serviceCanaryMinimumStep;
                sim.adjustTimeStep = serviceCanaryAdaptiveStep;
                ((Rb30Behavior)serviceCanaryOwner.getTemporalBehavior()).setInputs(
                    sim, serviceCanaryOwner, serviceCanaryInput);
                serviceCanaryStage = ServiceCanaryStage.RESTORE_FAULT_AND_POWER;
                return;
            case RESTORE_FAULT_AND_POWER:
                if (serviceCanaryFaultWasApplied && !serviceCanaryFaultBinding.isApplied()) {
                    require(serviceCanaryController.getFaultController().apply(),
                        "Q30 canary could not reapply the selected fault through its binding");
                }
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-settle-restored-fault-while-off");
                sim.setBoardPowerState(serviceCanaryPowerState);
                require(sim.getBoardPowerController().getState() == serviceCanaryPowerState,
                    "Q30 canary could not restore the original public power state");
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-restore-original-power");
                serviceCanaryStage = ServiceCanaryStage.RESTORE_FAULT_PROFILE;
                return;
            case RESTORE_FAULT_PROFILE:
                serviceCanaryOwner.getTemporalBehavior().prepareFaultedProfile(sim,
                    serviceCanaryOwner);
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-restore-selected-fault-profile");
                serviceCanaryController.endDeveloperVerificationScope();
                serviceCanaryScopeActive = false;
                sim.setSimRunning(serviceCanarySimWasRunning);
                serviceCanaryStage = ServiceCanaryStage.VERIFY_RESTORED;
                return;
            case VERIFY_RESTORED:
                put(serviceCanaryCleanup, "physicalValidationAfterRestore", false);
                recordServiceCanarySummary();
                staged.validatePhysical();
                put(serviceCanaryCleanup, "physicalValidationAfterRestore", true);
                put(serviceCanaryCleanup, "profileRepreparedAfterCanaries", true);
                put(serviceCanaryCleanup, "contextCapturedAfterCanaries", false);
                put(serviceCanaryCleanup, "simulationTimeBefore",
                    serviceCanaryInitialSimulationTime);
                put(serviceCanaryCleanup, "simulationTimeAfter", sim.t);
                put(serviceCanaryCleanup, "simulationTimeAdvanced",
                    sim.t > serviceCanaryInitialSimulationTime);
                put(serviceCanaryCleanup, "cursorCreatedCount", serviceCanaryCreatedCount);
                put(serviceCanaryCleanup, "cursorClosedCount", serviceCanaryClosedCount);
                put(serviceCanaryCleanup, "cursorRetainedCount",
                    serviceCanaryCreatedCount - serviceCanaryClosedCount);
                put(serviceCanaryCleanup, "mutationCount", 0);
                put(serviceCanaryCleanup, "elapsedMs",
                    System.currentTimeMillis() - serviceCanaryStartedMillis);
                verifyServiceCanaryRestoration();
                serviceCanaryStage = ServiceCanaryStage.DONE;
                serviceCanariesComplete = true;
                phase = Phase.PREPARE;
                phaseName = "stagedPreparation";
                phaseStarted = System.currentTimeMillis();
                publishProgress("servicePreparationCanaries", 7, 7);
                return;
            case DONE:
                throw new IllegalStateException("Q30 service-preparation canaries ran twice");
            default:
                throw new IllegalStateException("Unknown Q30 service-preparation canary stage");
            }
        }

        private void selectEnergizedRelayForCanary() {
            require(serviceCanaryFaultBinding != null && !serviceCanaryFaultBinding.isApplied() &&
                    serviceCanaryOwner.getTemporalBehavior() instanceof Rb30Behavior,
                "Q30 energized-relay fixture is not a real healthy owner");
            Vector<String> relayIds = new Vector<String>();
            if (serviceCanaryOwner.getBoard().getComponent("KA") != null)
                relayIds.add("KA");
            if (serviceCanaryOwner.getBoard().getComponent("KB") != null)
                relayIds.add("KB");
            for (String componentId : relayIds) {
                PhysicalPart<?> candidate = serviceCanaryOwner.getPhysicalBoardRuntime()
                    .getInstalledPart(componentId);
                if (!(candidate instanceof PhysicalRelayPart)) continue;
                double current = ((PhysicalRelayPart)candidate).getElement().coilCurrent;
                if (PowerDomainContract.finite(current) &&
                        Math.abs(current) >= RelayOutputBehavior.DISCHARGED_AMPS) {
                    serviceRelayComponentId = componentId;
                    serviceRelayTarget = candidate;
                    serviceRelayCoilCurrentBeforePowerOff = current;
                    break;
                }
            }
            require(serviceRelayTarget != null,
                "Q30 healthy HIGH profile did not energize a real relay coil");
        }

        private GeneratedDiagnosticServicePreparation.Cursor beginServiceCanaryCursor(
                String componentId, PhysicalPart<?> target) {
            final GeneratedBoardInstance exactOwner = serviceCanaryOwner;
            final GeneratedChallengeController exactController = serviceCanaryController;
            final Vector<CircuitElm> exactGraph = serviceCanaryGraph;
            final PcbWorkbenchController exactWorkbench = sim.pcbWorkbenchController;
            return GeneratedDiagnosticServicePreparation.begin(sim, exactOwner,
                exactController, componentId, target, serviceCanaryPolicy,
                new GeneratedDiagnosticServicePreparation.Checkpoint() {
                    public void check() {
                        require(sim.getGeneratedBoardInstance() == exactOwner &&
                                sim.getGeneratedChallengeController() == exactController &&
                                sim.elmList == exactGraph &&
                                sim.pcbWorkbenchController == exactWorkbench &&
                                !sim.generatedRuntimeInstallationInProgress,
                            "Q30 service canary lost its exact installed owner");
                    }
                });
        }

        private JSONObject serviceCase(String name, String outcome,
                String componentId, PhysicalPart<?> target) {
            JSONObject value = new JSONObject();
            put(value, "status", "RUNNING");
            put(value, "outcome", outcome);
            put(value, "componentId", componentId);
            put(value, "targetId", target.getId());
            put(value, "workUnits", serviceCanaryPolicy.getWorkUnits());
            put(value, "completedUnits", 0);
            put(value, "solverAdvances", 0);
            put(value, "advancedSeconds", 0.0);
            put(value, "elapsedMs", 0);
            put(value, "maximumAdvanceSeconds",
                serviceCanaryPolicy.getMaximumAdvanceSeconds());
            pendingServiceCaseStartedMillis = System.currentTimeMillis();
            serviceCanaryCases.put(name, value);
            return value;
        }

        private void runServiceCanaryCursorUnit(String name, String outcome) {
            require(serviceCanaryCursor != null && pendingServiceCase != null,
                "Q30 service canary lost its live cursor");
            double before = sim.t;
            boolean more = serviceCanaryCursor.step();
            double advanced = sim.t - before;
            if (advanced > 1e-15) {
                require(advanced <= serviceCanaryPolicy.getMaximumAdvanceSeconds() + 1e-12,
                    "Q30 service-preparation cursor exceeded one declared solver advance bound");
                serviceCanaryCursorAdvances++;
                put(pendingServiceCase, "solverAdvances",
                    intValue(pendingServiceCase.get("solverAdvances")) + 1);
                put(pendingServiceCase, "advancedSeconds",
                    numberValue(pendingServiceCase.get("advancedSeconds")) + advanced);
            }
            put(pendingServiceCase, "completedUnits",
                serviceCanaryCursor.getCompletedUnits());
            serviceCanaryWorkUnitsCompleted++;
            if (more) return;
            serviceCanaryCursor.finish();
            serviceCanaryCursor = null;
            serviceCanaryClosedCount++;
            String componentId = stringValue(pendingServiceCase.get("componentId"));
            PhysicalPart<?> target = serviceCanaryOwner.getPhysicalBoardRuntime()
                .getInstalledPart(componentId);
            boolean available = serviceCanaryAvailable(componentId, target);
            put(pendingServiceCase, "availableAfter", available);
            put(pendingServiceCase, "cursorClosed", true);
            put(pendingServiceCase, "outcome", outcome);
            put(pendingServiceCase, "status", "PASS");
            put(pendingServiceCase, "elapsedMs",
                System.currentTimeMillis() - pendingServiceCaseStartedMillis);
            if ("resistorImmediate".equals(name))
                require(available && intValue(pendingServiceCase.get("solverAdvances")) == 0,
                    "Q30 resistor readiness advanced the solver or lost actual availability");
            else {
                require(available && intValue(pendingServiceCase.get("solverAdvances")) > 0 &&
                        intValue(pendingServiceCase.get("solverAdvances")) <=
                            serviceCanaryPolicy.getWorkUnits(),
                    "Q30 energized relay did not require and complete bounded real advances");
                require(Rb30RelayService.isDischarged(serviceCanaryOwner, componentId),
                    "Q30 relay cursor finished without actual physical residual-energy readiness");
                put(pendingServiceCase, "physicalRelayDischargedAfter", true);
            }
            pendingServiceCase = null;
            publishProgress("servicePreparationCanaries", serviceCanaryClosedCount, 7);
        }

        private void runServicePreparationGuardCanaries() {
            runServicePreparationGuardCanary("staleOwner");
            runServicePreparationGuardCanary("stalePower");
            runServicePreparationGuardCanary("staleSourceCommand");
            runServicePreparationGuardCanary("staleSolverRecipe");
        }

        private void runServicePreparationGuardCanary(String name) {
            PhysicalPart<?> target = serviceCanaryOwner.getPhysicalBoardRuntime()
                .getInstalledPart("RDA");
            require(target != null && serviceCanaryAvailable("RDA", target),
                "Q30 guard fixture lacks immediate physical REMOVE readiness");
            GeneratedDiagnosticServicePreparation.Cursor cursor =
                beginServiceCanaryCursor("RDA", target);
            serviceCanaryCreatedCount++;
            long started = System.currentTimeMillis();
            double beforeTime = sim.t;
            BoardPowerState powerBefore = sim.getBoardPowerController().getState();
            boolean disconnectedBefore = serviceCanaryBindings.areAllDisconnected();
            require(powerBefore == BoardPowerState.UNPOWERED && disconnectedBefore,
                "Q30 stale-context guard fixture must begin electrically unpowered");
            GeneratedExternalPowerBindings.SavedControls guardControls =
                serviceCanaryBindings.saveControls();
            double[] guardVoltages = new double[serviceCanarySources.length];
            double[] guardLimits = new double[serviceCanarySources.length];
            for (int i = 0; i < serviceCanarySources.length; i++) {
                guardVoltages[i] = serviceCanarySources[i].maxVoltage;
                guardLimits[i] = serviceCanarySources[i].getLimitAmps();
            }
            double guardMaximumStep = sim.maxTimeStep;
            double guardMinimumStep = sim.minTimeStep;
            boolean guardAdaptiveStep = sim.adjustTimeStep;
            double afterStepTime = beforeTime;
            String controlsBefore = serviceCanaryBindings.controlSignature();
            boolean changed = false;
            Throwable rejection = null;
            double cursorTimeBefore = beforeTime;
            try {
                if ("staleOwner".equals(name)) {
                    sim.generatedBoardInstance = null;
                    changed = true;
                } else if ("stalePower".equals(name)) {
                    changed = true;
                    sim.setBoardPowerState(BoardPowerState.POWERED);
                    require(sim.getBoardPowerController().getState() ==
                            BoardPowerState.POWERED,
                        "Q30 stale-power guard did not apply the public POWERED transition");
                    GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                        "q30-service-canary-settle-powered-guard");
                } else if ("staleSourceCommand".equals(name)) {
                    int index = serviceCanaryInputIds.indexOf("MAIN12");
                    require(index >= 0, "Q30 guard fixture lost MAIN12");
                    changed = true;
                    serviceCanarySources[index].configure(
                        guardVoltages[index] + .25, guardLimits[index]);
                } else if ("staleSolverRecipe".equals(name)) {
                    sim.maxTimeStep = guardMaximumStep + .000001;
                    changed = true;
                } else throw new IllegalArgumentException("Unknown Q30 stale-context canary");
                cursorTimeBefore = sim.t;
                try { cursor.step(); }
                catch (Throwable expected) { rejection = expected; }
                afterStepTime = sim.t;
            } finally {
                if (changed) restoreServiceGuardMutation(name, guardVoltages,
                    guardLimits, guardMaximumStep);
            }
            boolean rejectedBeforeAdvance = rejection instanceof IllegalStateException &&
                afterStepTime == cursorTimeBefore;
            Throwable closeFailure = null;
            try { cursor.cancel(); }
            catch (Throwable problem) { closeFailure = problem; }
            if (closeFailure == null) serviceCanaryClosedCount++;
            boolean controlsRestored = guardControls.matches();
            boolean commandsRestored = serviceCanarySourceCommandsMatch(
                guardVoltages, guardLimits);
            boolean recipeRestored = sim.maxTimeStep == guardMaximumStep &&
                sim.minTimeStep == guardMinimumStep &&
                sim.adjustTimeStep == guardAdaptiveStep;
            boolean guardRestored = serviceGuardMutationRestored(name, guardControls,
                guardVoltages, guardLimits, guardMaximumStep) && controlsRestored &&
                commandsRestored && recipeRestored;
            JSONObject evidence = new JSONObject();
            put(evidence, "status", "PASS");
            put(evidence, "outcome", "REJECTED_BEFORE_ADVANCE");
            put(evidence, "componentId", "RDA");
            put(evidence, "targetId", target.getId());
            put(evidence, "workUnits", serviceCanaryPolicy.getWorkUnits());
            put(evidence, "completedUnits", 0);
            put(evidence, "maxSolverAdvances", serviceCanaryPolicy.getWorkUnits());
            put(evidence, "rejected", rejection != null);
            put(evidence, "rejection", rejection == null ? "" : describe(rejection));
            put(evidence, "solverAdvances", 0);
            put(evidence, "setupAdvancedSeconds", cursorTimeBefore - beforeTime);
            put(evidence, "rejectedBeforeAdvance", rejectedBeforeAdvance);
            put(evidence, "guardRestored", guardRestored);
            put(evidence, "ownerRestored",
                sim.getGeneratedBoardInstance() == serviceCanaryOwner);
            put(evidence, "powerStateRestored",
                sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                    sim.getBoardPowerController().isElectricallyUnpowered());
            put(evidence, "powerStateBefore", powerBefore.name());
            put(evidence, "powerStateAfter",
                sim.getBoardPowerController().getState().name());
            put(evidence, "allSourcesDisconnectedBefore", disconnectedBefore);
            put(evidence, "allSourcesDisconnectedAfter",
                serviceCanaryBindings.areAllDisconnected());
            put(evidence, "sourceControlsRestored", controlsRestored);
            put(evidence, "sourceCommandRestored", commandsRestored);
            put(evidence, "controlRevisionAdvanced",
                !controlsBefore.equals(serviceCanaryBindings.controlSignature()));
            put(evidence, "controlStateRestored", controlsRestored);
            put(evidence, "solverRecipeRestored", recipeRestored);
            put(evidence, "cursorCancelled", closeFailure == null);
            put(evidence, "elapsedMs", System.currentTimeMillis() - started);
            serviceCanaryCases.put(name, evidence);
            require(rejectedBeforeAdvance && guardRestored &&
                    closeFailure == null,
                "Q30 service cursor accepted or failed cleanup for " + name);
        }

        private void restoreServiceGuardMutation(String name, double[] guardVoltages,
                double[] guardLimits, double guardMaximumStep) {
            if ("staleOwner".equals(name)) {
                sim.generatedBoardInstance = serviceCanaryOwner;
            } else if ("stalePower".equals(name)) {
                if (sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                        !sim.isGeneratedRuntimeSettled())
                    GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                        "q30-service-canary-settle-powered-guard-before-restore");
                sim.setBoardPowerState(BoardPowerState.UNPOWERED);
                GeneratedRuntimeDeveloperSettlement.settle(sim, serviceCanaryOwner,
                    "q30-service-canary-settle-unpowered-guard-restore");
            } else if ("staleSourceCommand".equals(name)) {
                int index = serviceCanaryInputIds.indexOf("MAIN12");
                serviceCanarySources[index].configure(guardVoltages[index], guardLimits[index]);
            } else if ("staleSolverRecipe".equals(name)) {
                sim.maxTimeStep = guardMaximumStep;
            }
        }

        private boolean serviceGuardMutationRestored(String name,
                GeneratedExternalPowerBindings.SavedControls guardControls,
                double[] guardVoltages, double[] guardLimits, double guardMaximumStep) {
            if ("staleOwner".equals(name))
                return sim.getGeneratedBoardInstance() == serviceCanaryOwner;
            if ("stalePower".equals(name))
                return sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                    sim.getBoardPowerController().isElectricallyUnpowered() &&
                    guardControls.matches();
            if ("staleSourceCommand".equals(name))
                return serviceCanarySourceCommandsMatch(guardVoltages, guardLimits);
            if ("staleSolverRecipe".equals(name))
                return sim.maxTimeStep == guardMaximumStep;
            return false;
        }

        private void runServicePreparationCancellationCanary() {
            PhysicalPart<?> target = serviceCanaryOwner.getPhysicalBoardRuntime()
                .getInstalledPart("RDA");
            require(target != null && serviceCanaryAvailable("RDA", target),
                "Q30 cancellation fixture lacks immediate physical REMOVE readiness");
            GeneratedDiagnosticServicePreparation.Cursor cursor =
                beginServiceCanaryCursor("RDA", target);
            serviceCanaryCreatedCount++;
            long started = System.currentTimeMillis();
            double before = sim.t;
            BoardPowerState powerBefore = sim.getBoardPowerController().getState();
            boolean disconnectedBefore = serviceCanaryBindings.areAllDisconnected();
            cursor.cancel();
            serviceCanaryClosedCount++;
            JSONObject evidence = new JSONObject();
            put(evidence, "status", "PASS");
            put(evidence, "outcome", "CANCELLED_NO_ADVANCE_OR_REPOWER");
            put(evidence, "componentId", "RDA");
            put(evidence, "targetId", target.getId());
            put(evidence, "workUnits", serviceCanaryPolicy.getWorkUnits());
            put(evidence, "completedUnits", 0);
            put(evidence, "maxSolverAdvances", serviceCanaryPolicy.getWorkUnits());
            put(evidence, "solverAdvances", 0);
            put(evidence, "powerStateBefore", powerBefore.name());
            put(evidence, "powerStateAfter", sim.getBoardPowerController().getState().name());
            put(evidence, "allSourcesDisconnectedBefore", disconnectedBefore);
            put(evidence, "allSourcesDisconnectedAfter",
                serviceCanaryBindings.areAllDisconnected());
            put(evidence, "cursorCancelled", true);
            put(evidence, "elapsedMs", System.currentTimeMillis() - started);
            serviceCanaryCases.put("cancellation", evidence);
            require(sim.t == before && powerBefore == BoardPowerState.UNPOWERED &&
                    sim.getBoardPowerController().getState() == powerBefore &&
                    disconnectedBefore && serviceCanaryBindings.areAllDisconnected(),
                "Q30 cursor cancellation advanced or repowered its owner");
        }

        private boolean serviceCanaryAvailable(String componentId, PhysicalPart<?> target) {
            return sim.pcbWorkbenchController.isAvailable(WorkbenchOperation.forPart(
                WorkbenchOperation.REMOVE, target));
        }

        private double relayCoilCurrent(PhysicalPart<?> target) {
            if (!(target instanceof PhysicalRelayPart))
                throw new IllegalStateException("Q30 energized relay target is not a relay part");
            return ((PhysicalRelayPart)target).getElement().coilCurrent;
        }

        private void restoreServiceCanarySourceCommands() {
            for (int i = 0; i < serviceCanarySources.length; i++)
                serviceCanarySources[i].configure(serviceCanarySourceVoltages[i],
                    serviceCanarySourceLimits[i]);
        }

        private boolean serviceCanarySourceCommandsMatch() {
            return serviceCanarySourceCommandsMatch(serviceCanarySourceVoltages,
                serviceCanarySourceLimits);
        }

        private boolean serviceCanarySourceCommandsMatch(double[] expectedVoltages,
                double[] expectedLimits) {
            if (serviceCanaryBindings == null || serviceCanaryBindings !=
                    serviceCanaryOwner.getExternalPowerBindings() ||
                    serviceCanaryBindings.getBoardForRuntimeValidation() != serviceCanaryOwner.getBoard() ||
                    expectedVoltages == null || expectedLimits == null ||
                    expectedVoltages.length != serviceCanarySources.length ||
                    expectedLimits.length != serviceCanarySources.length)
                return false;
            for (int i = 0; i < serviceCanarySources.length; i++) {
                ExternalPowerSimulationBinding binding;
                try { binding = serviceCanaryBindings.getBinding(serviceCanaryInputIds.get(i)); }
                catch (RuntimeException invalid) { return false; }
                if (binding != serviceCanaryPowerBindings[i] ||
                        binding.getLimitedSupply() != serviceCanarySources[i] ||
                        serviceCanarySources[i].maxVoltage != expectedVoltages[i] ||
                        serviceCanarySources[i].getLimitAmps() != expectedLimits[i])
                    return false;
            }
            return true;
        }

        private boolean serviceCanarySolverRecipeMatches() {
            return sim.maxTimeStep == serviceCanaryMaximumStep &&
                sim.minTimeStep == serviceCanaryMinimumStep &&
                sim.adjustTimeStep == serviceCanaryAdaptiveStep;
        }

        private void recordServiceCanarySummary() {
            put(servicePreparationCanaries, "passedCount", serviceCanaryClosedCount);
            put(servicePreparationCanaries, "totalSolverAdvances",
                serviceCanaryCursorAdvances);
            put(servicePreparationCanaries, "healthyProfileAdvanceUnits",
                serviceCanaryCursorUnits);
            put(servicePreparationCanaries, "serviceCursorWorkUnitsCompleted",
                serviceCanaryWorkUnitsCompleted);
            put(servicePreparationCanaries, "setupElapsedMs", serviceCanarySetupMillis);
            put(servicePreparationCanaries, "elapsedMs",
                System.currentTimeMillis() - serviceCanaryStartedMillis);
            put(servicePreparationCanaries, "cleanupComplete", false);
        }

        private void verifyServiceCanaryRestoration() {
            GeneratedChallengeLifecycleEvidence lifecycle =
                serviceCanaryController.getLifecycleEvidence();
            boolean exactOwner = sim.getGeneratedBoardInstance() == serviceCanaryOwner;
            boolean exactController = sim.getGeneratedChallengeController() ==
                serviceCanaryController;
            boolean exactGraphIdentity = sim.elmList == serviceCanaryGraph;
            boolean exactGraphContents = sameCircuitElements(sim.elmList,
                serviceCanaryGraphContents);
            boolean exactFaultBinding = serviceCanaryOwner.getFaultBinding() ==
                serviceCanaryFaultBinding;
            boolean selectedFaultRestored = exactFaultBinding &&
                serviceCanaryFaultBinding.isApplied() == serviceCanaryFaultWasApplied;
            boolean challengeReady = serviceCanaryController.isReady();
            boolean healthyFamilyValidated = lifecycle != null &&
                lifecycle.healthyFamilyValidated;
            boolean selectedFaultValidated = lifecycle != null &&
                lifecycle.selectedFaultValidated;
            boolean selectedFaultApplied = lifecycle != null &&
                lifecycle.selectedFaultApplied;
            boolean powerStateRestored = sim.getBoardPowerController().getState() ==
                serviceCanaryPowerState;
            boolean sourceControlsRestored = serviceCanarySavedControls.matches();
            boolean sourceCommandsRestored = serviceCanarySourceCommandsMatch();
            boolean solverRecipeRestored = serviceCanarySolverRecipeMatches();
            boolean inputStateRestored = serviceCanaryOwner.getTemporalBehavior() instanceof
                Rb30Behavior && ((Rb30Behavior)serviceCanaryOwner.getTemporalBehavior())
                    .getInputs() == serviceCanaryInput;
            boolean runtimeSettled = sim.isGeneratedRuntimeSettled();
            boolean cursorCleanupComplete = serviceCanaryCreatedCount == 7 &&
                serviceCanaryClosedCount == 7 && serviceCanaryCursor == null &&
                serviceHealthyWork == null && pendingServiceCase == null;

            JSONObject cleanup = serviceCanaryCleanup;
            put(cleanup, "exactOwnerRestored", exactOwner);
            put(cleanup, "exactControllerRestored", exactController);
            put(cleanup, "exactGraphIdentityRestored", exactGraphIdentity);
            put(cleanup, "exactGraphContentsRestored", exactGraphContents);
            put(cleanup, "faultBindingIdentityRestored", exactFaultBinding);
            put(cleanup, "selectedFaultRestored", selectedFaultRestored);
            put(cleanup, "challengeReady", challengeReady);
            put(cleanup, "lifecycleEvidencePresent", lifecycle != null);
            put(cleanup, "healthyFamilyValidated", healthyFamilyValidated);
            put(cleanup, "selectedFaultValidated", selectedFaultValidated);
            put(cleanup, "selectedFaultApplied", selectedFaultApplied);
            put(cleanup, "powerStateRestored", powerStateRestored);
            put(cleanup, "allSourceControlsRestored", sourceControlsRestored);
            put(cleanup, "sourceControlRevisionAdvanced",
                !serviceCanaryInitialControlSignature.equals(
                    serviceCanaryBindings.controlSignature()));
            put(cleanup, "allSourceCommandsRestored", sourceCommandsRestored);
            put(cleanup, "solverRecipeRestored", solverRecipeRestored);
            put(cleanup, "inputStateRestored", inputStateRestored);
            put(cleanup, "generatedRuntimeSettled", runtimeSettled);
            put(cleanup, "cursorCleanupComplete", cursorCleanupComplete);
            put(cleanup, "challengeState", serviceCanaryController.getState().name());
            put(cleanup, "powerState", sim.getBoardPowerController().getState().name());
            put(cleanup, "electricallyUnpowered",
                sim.getBoardPowerController().isElectricallyUnpowered());
            put(cleanup, "runtimeInstallationInProgress",
                sim.generatedRuntimeInstallationInProgress);
            put(cleanup, "generatedVerificationRunning", sim.generatedVerificationRunning);
            put(cleanup, "generatedVerificationPending", sim.generatedBoardVerificationPending);
            put(cleanup, "generatedVerificationAnalyzed",
                sim.generatedBoardVerificationAnalyzed);
            put(cleanup, "analysisPending", sim.analyzeFlag);
            put(cleanup, "dcAnalysisPending", sim.dcAnalysisFlag);
            put(cleanup, "measurementOverlayActive", sim.activeMeasurementOverlay);
            put(cleanup, "pendingPowerState", sim.pendingBoardPowerState == null ?
                "NONE" : sim.pendingBoardPowerState.name());
            put(cleanup, "stopMessagePresent", sim.stopMessage != null);
            put(cleanup, "observationalValidationDepth",
                sim.observationalValidationDepth);
            put(cleanup, "physicalMutationInProgress",
                serviceCanaryOwner.getPhysicalBoardRuntime().isMutationInProgress());
            put(cleanup, "challengeOperationInProgress",
                serviceCanaryController.isOperationInProgress());
            put(cleanup, "failedOwnerMatches", sim.failedGeneratedRuntimeOwner ==
                serviceCanaryOwner);
            put(cleanup, "generationCoordinatorBetweenSteps",
                sim.generationCoordinator != null &&
                    sim.generationCoordinator.isBetweenSteps());
            put(cleanup, "cursorCreatedCount", serviceCanaryCreatedCount);
            put(cleanup, "cursorClosedCount", serviceCanaryClosedCount);
            put(cleanup, "cursorRetainedCount",
                serviceCanaryCreatedCount - serviceCanaryClosedCount);

            StringBuilder failed = new StringBuilder();
            appendFailedCheck(failed, "exactOwnerRestored", exactOwner);
            appendFailedCheck(failed, "exactControllerRestored", exactController);
            appendFailedCheck(failed, "exactGraphIdentityRestored", exactGraphIdentity);
            appendFailedCheck(failed, "exactGraphContentsRestored", exactGraphContents);
            appendFailedCheck(failed, "faultBindingIdentityRestored", exactFaultBinding);
            appendFailedCheck(failed, "selectedFaultRestored", selectedFaultRestored);
            appendFailedCheck(failed, "challengeReady", challengeReady);
            appendFailedCheck(failed, "lifecycleEvidencePresent", lifecycle != null);
            appendFailedCheck(failed, "healthyFamilyValidated", healthyFamilyValidated);
            appendFailedCheck(failed, "selectedFaultValidated", selectedFaultValidated);
            appendFailedCheck(failed, "selectedFaultApplied", selectedFaultApplied);
            appendFailedCheck(failed, "powerStateRestored", powerStateRestored);
            appendFailedCheck(failed, "allSourceControlsRestored", sourceControlsRestored);
            appendFailedCheck(failed, "allSourceCommandsRestored", sourceCommandsRestored);
            appendFailedCheck(failed, "solverRecipeRestored", solverRecipeRestored);
            appendFailedCheck(failed, "inputStateRestored", inputStateRestored);
            appendFailedCheck(failed, "generatedRuntimeSettled", runtimeSettled);
            appendFailedCheck(failed, "cursorCleanupComplete", cursorCleanupComplete);
            put(cleanup, "failedChecks", failed.toString());
            put(cleanup, "status", failed.length() == 0 ? "PASS" : "FAIL");
            require(failed.length() == 0,
                "Q30 service canary restoration failed: " + failed);
        }

        private static void appendFailedCheck(StringBuilder failed, String name,
                boolean passed) {
            if (passed) return;
            if (failed.length() > 0) failed.append(',');
            failed.append(name);
        }

        private static boolean sameCircuitElements(Vector<CircuitElm> current,
                Vector<CircuitElm> captured) {
            if (current == null || captured == null || current.size() != captured.size()) return false;
            for (int i = 0; i < current.size(); i++)
                if (current.get(i) != captured.get(i)) return false;
            return true;
        }

        private static int intValue(com.google.gwt.json.client.JSONValue value) {
            return value == null || value.isNumber() == null ? 0 :
                (int)value.isNumber().doubleValue();
        }

        private static long longValue(com.google.gwt.json.client.JSONValue value) {
            return value == null || value.isNumber() == null ? 0L :
                (long)value.isNumber().doubleValue();
        }

        private static double numberValue(com.google.gwt.json.client.JSONValue value) {
            return value == null || value.isNumber() == null ? 0.0 :
                value.isNumber().doubleValue();
        }

        private void runIncompleteSessionCanary() {
            JSONObject evidence = beginNegative("incompleteSession");
            GeneratedBoardInstance expectedOwner = owner;
            GeneratedChallengeController expectedController = controller;
            Vector<CircuitElm> expectedGraph = sim.elmList;
            GeneratedDiagnosticProofService.Session incomplete =
                GeneratedDiagnosticProofService.begin(sim, owner, controller);
            cleanupFailureSession = incomplete;
            Throwable rejection = null;
            try {
                incomplete.finish(contextKey);
            } catch (Throwable problem) {
                rejection = problem;
            }
            try { incomplete.cancel(); }
            catch (Throwable problem) { rejection = retain(rejection, problem); }
            boolean rejectedAsIncomplete = rejection != null &&
                describe(rejection).toLowerCase().indexOf("incomplete") >= 0;
            boolean noReceipt = controller.getDiagnosticProofReceipt() == null;
            boolean restored = !incomplete.hasPendingPrivateCleanup() && noReceipt &&
                sim.getGeneratedBoardInstance() == expectedOwner &&
                sim.getGeneratedChallengeController() == expectedController &&
                sim.elmList == expectedGraph && !sim.generatedRuntimeInstallationInProgress;
            require(rejectedAsIncomplete && restored,
                "Incomplete D01 session did not reject without a receipt and restore its exact owner");
            put(evidence, "rejected", true);
            put(evidence, "rejection", describe(rejection));
            put(evidence, "receiptIssued", !noReceipt);
            put(evidence, "exactOwnerRestored", restored);
            completeNegative(evidence);
            cleanupFailureSession = null;
            staged.pause();
        }

        private void runForeignHypothesisCanary() {
            JSONObject evidence = beginNegative("foreignHypothesis");
            long foreignSeed = seed == Long.MAX_VALUE ? seed - 1 : seed + 1;
            Rb30Generator.Candidate foreignConstruction = null;
            GeneratedBoardInstance unexpectedReplay = null;
            Throwable providerFailure = null;
            Throwable cleanupFailure = null;
            String rejection = null;
            String hypothesisKey = null;
            try {
                foreignConstruction = new Rb30Generator().construct(Rb30Plan.resolve(foreignSeed));
                Vector<GeneratedFaultCandidate> foreignCandidates =
                    foreignConstruction.faultCandidates;
                require(foreignCandidates != null && !foreignCandidates.isEmpty(),
                    "Foreign hypothesis canary has no candidate");
                GeneratedFaultCandidate foreignCandidate = foreignCandidates.firstElement();
                require(foreignCandidate.isAdmitted() &&
                        Rb30Plan.FAMILY_ID.equals(
                            foreignCandidate.getFault().getCircuitFamilyId()) &&
                        foreignCandidate.getFault().getSelectionSeed() == foreignSeed,
                    "Foreign hypothesis canary did not obtain an admitted candidate for another seed");
                hypothesisKey = foreignCandidate.getHypothesisKey();
                try {
                    unexpectedReplay = owner.getDiagnosticProvider().generateHypothesis(foreignCandidate);
                } catch (IllegalArgumentException expected) {
                    rejection = describe(expected);
                }
            } catch (Throwable problem) {
                providerFailure = problem;
            } finally {
                cleanupFailure = retain(cleanupFailure,
                    disposeUninstalledOwner(unexpectedReplay, "foreign-replay"));
                cleanupFailure = retain(cleanupFailure,
                    disposeUninstalledCandidate(foreignConstruction,
                        "foreign-candidate-construction"));
            }
            if (cleanupFailure != null) providerFailure = retain(providerFailure, cleanupFailure);
            require(providerFailure == null && rejection != null && unexpectedReplay == null,
                "Current Q30 provider failed to reject and clean a foreign normal hypothesis" +
                    (providerFailure == null ? "" : ": " + describe(providerFailure)));
            put(evidence, "foreignSeed", Long.toString(foreignSeed));
            put(evidence, "hypothesisKey", hypothesisKey);
            put(evidence, "rejected", true);
            put(evidence, "rejection", rejection);
            put(evidence, "foreignConstructionDisposed", true);
            completeNegative(evidence);
            staged.validatePhysical();
            staged.pause();
        }

        private Throwable disposeUninstalledCandidate(Rb30Generator.Candidate candidate,
                String label) {
            if (candidate == null) return null;
            Vector<CircuitElm> elements = candidate.elements();
            for (CircuitElm element : elements) {
                boolean safe = (predecessorGraph == null || !predecessorGraph.contains(element)) &&
                    (sim.elmList == null || !sim.elmList.contains(element)) &&
                    (owner == null || !owner.getSimulationElements().contains(element));
                if (!safe)
                    return new IllegalStateException(
                        "Refused foreign candidate cleanup over an installed owner: " + label);
            }
            try {
                candidate.assembly.power.setConnected(false);
                require(candidate.assembly.power.areAllDisconnected(),
                    "Foreign Q30 candidate retained connected external power: " + label);
            }
            catch (Throwable powerFailure) { return powerFailure; }
            Throwable failure = null;
            for (CircuitElm element : elements) {
                try { element.delete(); }
                catch (Throwable deleteFailure) { failure = retain(failure, deleteFailure); }
            }
            return failure;
        }

        private Throwable disposeUninstalledOwner(GeneratedBoardInstance candidate, String label) {
            if (candidate == null) return null;
            Throwable failure = null;
            Vector<CircuitElm> elements = null;
            boolean disjoint = true;
            try {
                elements = candidate.getSimulationElements();
                for (CircuitElm element : elements) {
                    boolean safe = (predecessorGraph == null || !predecessorGraph.contains(element)) &&
                        (sim.elmList == null || !sim.elmList.contains(element)) &&
                        (owner == null || owner == candidate ||
                            !owner.getSimulationElements().contains(element));
                    if (!safe) {
                        disjoint = false;
                        failure = retain(failure, new IllegalStateException(
                            "Refused foreign canary cleanup over an installed owner: " + label));
                        break;
                    }
                }
            } catch (Throwable guardFailure) {
                failure = retain(failure, guardFailure);
                disjoint = false;
            }
            if (!disjoint || elements == null) return failure;
            try {
                candidate.getExternalPowerBindings().setConnected(false);
                require(candidate.getExternalPowerBindings().areAllDisconnected(),
                    "Foreign canary owner retained connected power bindings: " + label);
            } catch (Throwable powerFailure) {
                return retain(failure, powerFailure);
            }
            if (elements != null) {
                for (CircuitElm element : elements) {
                    try { element.delete(); }
                    catch (Throwable deleteFailure) { failure = retain(failure, deleteFailure); }
                }
            }
            return failure;
        }

        private void beginCleanupFailureCanary() {
            JSONObject evidence = beginNegative("failedCleanupRetry");
            phase = Phase.CLEANUP_CANARY;
            phaseName = "failedCleanupCanary";
            phaseStarted = System.currentTimeMillis();
            GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(true);
            cleanupFailureInjectionArmed = true;
            cleanupFailureSession = GeneratedDiagnosticProofService.begin(sim, owner, controller);
            publishProgress("failedCleanupCanary", 0, cleanupFailureSession.getTotalCount());
        }

        private void cleanupFailureCanaryOneUnit() {
            phaseName = "failedCleanupCanary";
            cleanupCanaryUnits++;
            boolean more;
            try {
                more = cleanupFailureSession.step();
            } catch (Throwable expectedOrFailure) {
                cleanupCanaryMillis += sincePhaseStart();
                GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(false);
                cleanupFailureInjectionArmed = false;
                boolean injectedFailureObserved = hasMessage(expectedOrFailure,
                    "Injected diagnostic private cleanup failure");
                boolean pendingBeforeRetry = cleanupFailureSession.hasPendingPrivateCleanup();
                Throwable retryFailure = null;
                try { cleanupFailureSession.cancel(); }
                catch (Throwable problem) { retryFailure = problem; }
                cleanupCanaryAudit = cleanupFailureSession.getCleanupAudit();
                boolean noReceipt = controller.getDiagnosticProofReceipt() == null;
                boolean exactOwnerRestored = sim.getGeneratedBoardInstance() == owner &&
                    sim.getGeneratedChallengeController() == controller && sim.elmList == stagedGraph &&
                    !sim.generatedRuntimeInstallationInProgress;
                boolean cleanupWasRetriedAndCompleted = cleanupFailureAuditClean();
                report.put("cleanupFailureCanaryAudit", cleanupJson(cleanupCanaryAudit));
                require(injectedFailureObserved && pendingBeforeRetry && retryFailure == null && noReceipt &&
                        exactOwnerRestored && cleanupWasRetriedAndCompleted &&
                        !cleanupFailureSession.hasPendingPrivateCleanup(),
                    "One-shot D01 cleanup failure did not fail closed and retry exact cleanup" +
                        (retryFailure == null ? "" : ": " + describe(retryFailure)));
                JSONObject negative = negativeEvidence.get("failedCleanupRetry").isObject();
                put(negative, "injectedFailure", true);
                put(negative, "failure", describe(expectedOrFailure));
                put(negative, "receiptIssued", !noReceipt);
                put(negative, "exactOwnerRestored", exactOwnerRestored);
                put(negative, "pendingCleanupBeforeRetry", pendingBeforeRetry);
                put(negative, "pendingCleanupAfterRetry",
                    cleanupFailureSession.hasPendingPrivateCleanup());
                put(negative, "exactRetryInvoked", true);
                negative.put("cleanupAudit", cleanupJson(cleanupCanaryAudit));
                put(negative, "retryCompleted", cleanupWasRetriedAndCompleted);
                cleanupFailureSession = null;
                staged.validatePhysical();
                GeneratedDiagnosticContextKey afterCanaries =
                    GeneratedDiagnosticContextKey.capture(sim, owner, REQUEST_CANONICAL,
                        construction.realizationManifest);
                require(afterCanaries.isTrustedCapture() && afterCanaries.equals(contextKey),
                    "D01 negative canaries changed the cold proof context before accepted replay");
                put(negative, "contextRestoredAfterCanaries", true);
                completeNegative(negative);
                staged.pause();
                startColdProof();
                return;
            }
            cleanupFailureSession.pause();
            staged.pause();
            if (!more) {
                GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(false);
                cleanupFailureInjectionArmed = false;
                cleanupCanaryMillis += sincePhaseStart();
                cleanupFailureSession.cancel();
                cleanupCanaryAudit = cleanupFailureSession.getCleanupAudit();
                JSONObject negative = negativeEvidence.get("failedCleanupRetry").isObject();
                put(negative, "injectedFailure", false);
                put(negative, "receiptIssued", controller.getDiagnosticProofReceipt() != null);
                negative.put("cleanupAudit", cleanupJson(cleanupCanaryAudit));
                throw new IllegalStateException(
                    "D01 cleanup failure canary reached completion without its injected failure");
            }
            if ((cleanupCanaryUnits & 3) == 0)
                publishProgress("failedCleanupCanary", cleanupFailureSession.getCompletedCount(),
                    cleanupFailureSession.getTotalCount());
        }

        private boolean cleanupFailureAuditClean() {
            return cleanupCanaryAudit != null &&
                cleanupCanaryAudit.getDisposalFailureCount() == 1 &&
                cleanupCanaryAudit.getDisposedHypothesisCount() == 1 &&
                cleanupCanaryAudit.wasLastCleanupComplete() &&
                cleanupCanaryAudit.wasLastOwnerGuardPassed() &&
                cleanupCanaryAudit.wereLastBindingsActuallyDisconnected() &&
                cleanupCanaryAudit.wereLastElementsActuallyDeleted() &&
                cleanupCanaryAudit.wasLastGraphDetached() &&
                cleanupCanaryAudit.getLastBindingCount() ==
                    cleanupCanaryAudit.getLastDisconnectedBindingCount() &&
                cleanupCanaryAudit.getLastElementCount() ==
                    cleanupCanaryAudit.getLastDeletedElementCount();
        }

        private void startColdProof() {
            phase = Phase.PROOF;
            phaseName = "coldProof";
            phaseStarted = System.currentTimeMillis();
            proof = GeneratedDiagnosticProofService.begin(sim, owner, controller);
            publishProgress("coldProof", 0, proof.getTotalCount());
        }

        private void proveOneUnit() {
            phaseName = "coldProof";
            String workLabel = proof.nextWorkLabelForDeveloperVerification();
            long workStarted = System.currentTimeMillis();
            boolean more;
            try { more = proof.step(); }
            finally {
                long duration = Math.max(0, System.currentTimeMillis() - workStarted);
                proofActiveWorkMillis += duration;
                long[] timing = proofUnitTimings.get(workLabel);
                if (timing == null) {
                    timing = new long[3];
                    proofUnitTimings.put(workLabel, timing);
                }
                timing[0]++;
                timing[1] += duration;
                timing[2] = Math.max(timing[2], duration);
            }
            proofUnits++;
            proof.pause();
            staged.pause();
            if (more) {
                if ((proofUnits & 3) == 0)
                    publishProgress("coldProof", proof.getCompletedCount(), proof.getTotalCount());
                return;
            }
            proofMillis += proof.getElapsedMillis();
            phase = Phase.COMPLETE;
            phaseName = "classification";
            phaseStarted = System.currentTimeMillis();
        }

        private void completeProof() {
            phaseName = "classification";
            receipt = proof.finish(contextKey);
            classificationMillis += sincePhaseStart();
            cleanupAudit = proof.getCleanupAudit();
            phaseName = "postProofPhysicalValidation";
            phaseStarted = System.currentTimeMillis();
            staged.validatePhysical();
            physicalValidationMillis += sincePhaseStart();
            validateReceiptAndEvidence();
            phase = Phase.CLEANUP;
            phaseName = "stagedCleanup";
            phaseStarted = System.currentTimeMillis();
            publishProgress("stagedCleanup", EXPECTED_HYPOTHESIS_COUNT,
                EXPECTED_HYPOTHESIS_COUNT);
        }

        private void validateReceiptAndEvidence() {
            int expectedSamplesPerHypothesis = expectedSampleCount();
            require(receipt != null && receipt.getContextKey() != null,
                "Cold D01 proof returned no context-bound receipt");
            receipt.requireContextKey(contextKey);
            receipt.requireAssessmentOwner(owner);
            require(receipt.getContextKey().isTrustedCapture(),
                "Cold D01 proof receipt lost its trusted context");
            require(controller.getDiagnosticProofReceipt() == receipt,
                "Cold D01 receipt was not issued by the current owner controller");
            require(!owner.isDeveloperOnlyFaultRoute() && receipt.getEvidence().size() ==
                    EXPECTED_HYPOTHESIS_COUNT,
                "Cold D01 proof did not cover the normal five-hypothesis population");

            Vector<String> expected = GeneratedDiagnosticSolvabilityAdmission
                .getHypothesisKeys(owner.getFaultCandidates());
            Vector<String> actual = new Vector<String>();
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
            JSONArray rows = new JSONArray();
            int observations = 0;
            for (GeneratedDiagnosticSolvabilityEvidence item : evidence) {
                actual.add(item.getHypothesisKey());
                require(item.getAdmittedCandidateCount() == EXPECTED_HYPOTHESIS_COUNT,
                    "D01 evidence declared a partial hypothesis population");
                require(item.isRepairReachable() && item.isCustomerRetestPassed() &&
                        item.isStateIsolated() && item.hasUnaffectedFunctionRetestObservation(),
                    "D01 hypothesis lacks repair, customer retest or restored-state evidence: " +
                        item.getHypothesisKey());
                Vector<GeneratedDiagnosticSample> samples = item.getSolverSamples();
                require(samples.size() == expectedSamplesPerHypothesis,
                    "D01 hypothesis has " + samples.size() + " actual samples; expected " +
                        expectedSamplesPerHypothesis);
                JSONObject row = new JSONObject();
                put(row, "hypothesisKey", item.getHypothesisKey());
                put(row, "routeId", item.getRouteId());
                put(row, "admittedCandidateCount", item.getAdmittedCandidateCount());
                put(row, "repairReachable", item.isRepairReachable());
                put(row, "customerRetestPassed", item.isCustomerRetestPassed());
                put(row, "stateIsolated", item.isStateIsolated());
                put(row, "deterministicResult", item.getDeterministicResult());
                put(row, "deterministicRejectionReason", item.getDeterministicRejectionReason());
                put(row, "equivalentRepairClass", item.getEquivalentRepairClass());
                row.put("executedRepairActionIds", strings(item.getExecutedRepairActionIds()));
                JSONArray sampleRows = new JSONArray();
                Vector<String> sampleIds = new Vector<String>();
                for (GeneratedDiagnosticSample sample : samples) {
                    require(sample != null && !sampleIds.contains(sample.getSampleId()),
                        "D01 evidence contains a missing or duplicate sample");
                    sampleIds.add(sample.getSampleId());
                    JSONObject sampleRow = new JSONObject();
                    put(sampleRow, "id", sample.getSampleId());
                    put(sampleRow, "outcome", sample.getOutcome().name());
                    if (!sample.isOverRange()) {
                        put(sampleRow, "value", sample.getValue());
                        put(sampleRow, "tolerance", sample.getComparisonTolerance());
                    }
                    sampleRows.set(sampleRows.size(), sampleRow);
                    observations++;
                }
                row.put("sampleCount", new JSONNumber(samples.size()));
                row.put("samples", sampleRows);
                rows.set(rows.size(), row);
            }
            Collections.sort(expected);
            Collections.sort(actual);
            require(expected.equals(actual), "Cold D01 proof changed the exact hypothesis population");
            require(observations == EXPECTED_HYPOTHESIS_COUNT *
                    expectedSamplesPerHypothesis,
                "Cold D01 proof did not retain the full per-hypothesis observation population");

            GeneratedDiagnosticProgram program = owner.getDiagnosticProvider().getObservationProgram();
            Vector<String> expectedSampleIds = observationSampleIds(program);
            require(expectedSampleIds.size() == expectedSamplesPerHypothesis,
                "Q30 normal observation program differs from the active-channel contract");
            for (GeneratedDiagnosticSolvabilityEvidence item : evidence) {
                Vector<GeneratedDiagnosticSample> samples = item.getSolverSamples();
                for (int index = 0; index < expectedSampleIds.size(); index++)
                    require(expectedSampleIds.get(index).equals(samples.get(index).getSampleId()),
                        "D01 evidence sample order differs from the declared normal program");
            }
            put(report, "family", owner.getCircuitFamilyId());
            put(report, "topology", owner.getTopologyVariantId());
            put(report, "providerId", owner.getDiagnosticProvider().getProviderId());
            put(report, "programIdentity", program.canonical());
            put(report, "realizationManifest", construction.realizationManifest);
            put(report, "contextKeyHash", contextKey.hashCode());
            put(report, "contextCanonical", contextKey.canonical());
            put(report, "contextTrusted", contextKey.isTrustedCapture());
            put(report, "explicitCompletion", false);
            put(report, "hypothesisCount", evidence.size());
            put(report, "actualSampleCount", observations);
            JSONObject cold = new JSONObject();
            put(cold, "status", "PASS");
            put(cold, "elapsedMillis", proof.getElapsedMillis());
            put(cold, "workUnits", proofUnits);
            put(cold, "activeWorkMillis", proofActiveWorkMillis);
            cold.put("evidence", rows);
            cold.put("cleanupAudit", cleanupJson(cleanupAudit));
            report.put("cold", cold);
            put(report, "d01Admission", true);
            reportHasEvidence = true;
            put(report, "proofUnits", proofUnits);
            put(report, "serviceElapsedMs", proof.getElapsedMillis());
            report.put("cleanupAudit", cleanupJson(cleanupAudit));
            require(cleanupAudit != null && cleanupAudit.getDisposalFailureCount() == 0 &&
                    cleanupAudit.getDisposedHypothesisCount() == EXPECTED_HYPOTHESIS_COUNT &&
                    cleanupAudit.wasLastCleanupComplete() &&
                    cleanupAudit.wasLastOwnerGuardPassed() &&
                    cleanupAudit.wereLastBindingsActuallyDisconnected() &&
                    cleanupAudit.wereLastElementsActuallyDeleted() &&
                    cleanupAudit.wasLastGraphDetached() &&
                    cleanupAudit.getLastBindingCount() == cleanupAudit.getLastDisconnectedBindingCount() &&
                    cleanupAudit.getLastElementCount() == cleanupAudit.getLastDeletedElementCount(),
                "Cold D01 proof cleanup audit is incomplete");
            coldEvidence = receipt.getEvidence();
            coldPartitionCanonical = receipt.getPartitionPlan().canonical();
            coldProviderId = receipt.getProviderId();
            coldProgramIdentity = receipt.getProgramIdentity();
            pendingCacheEntry = verificationCache.prepare(contextKey, owner, receipt);
            require(!pendingCacheEntry.retainsRuntimeObjectsForDeveloperVerification(),
                "Verifier-only D01 cache artifact retained a runtime object");
        }

        private void constructWarm() {
            phaseName = "warmConstruction";
            JSONObject warm = (JSONObject)report.get("warm").isObject();
            if (constructionSession == null) {
                phaseStarted = System.currentTimeMillis();
                put(warm, "status", "RUNNING");
                beginFamilyConstruction();
            }
            if (!constructionSession.advance()) {
                publishProgress("warmConstruction", 0, 0);
                return;
            }
            construction = constructionSession.result();
            constructionSession = null;
            owner = construction.instance;
            require(owner != null && !owner.isDeveloperOnlyFaultRoute() &&
                    owner.getSeed() == seed && Rb30Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()),
                "Q30 warm capture did not construct a fresh normal owner for the cold seed");
            owner.requireNormalPhysicalAdmission();
            require(expectedConstructionPlanCanonical.equals(construction.realizationManifest) &&
                    realizationManifest.equals(construction.realizationManifest),
                "Fresh same-seed normal owner changed its consumed realization manifest");
            warmConstructionMillis += sincePhaseStart();
            staged = new FreshGeneratedRuntimeInstallation.Staged(sim, owner);
            phase = Phase.PREPARE_WARM;
            phaseName = "warmPreparation";
            phaseStarted = System.currentTimeMillis();
            staged.prepare(false);
            if (!staged.isPreparationComplete()) staged.pause();
            publishProgress("warmPreparation", 0, 0);
        }

        private void beginFamilyConstruction() {
            StagedFamilyCapability family =
                PlayerFamilyCatalog.stagedCapability(Rb30Plan.FAMILY_ID);
            require(family != null && Rb30Plan.FAMILY_ID.equals(family.familyId()) &&
                    family.supportsPrivateQualification(),
                "Q30 D01 has no registered private staged-family capability");
            StagedFamilyCapability.Plan plan = family.resolve(seed);
            String planCanonical = family.canonicalIdentity(plan);
            require(planCanonical != null && planCanonical.length() != 0,
                "Q30 staged family returned no resolved plan identity");
            if (expectedConstructionPlanCanonical == null) {
                expectedConstructionPlanCanonical = planCanonical;
            } else {
                require(expectedConstructionPlanCanonical.equals(planCanonical),
                    "Fresh same-seed Q30 staged family changed its resolved plan identity");
            }
            constructionSession = family.beginConstruction(plan);
            require(constructionSession != null,
                "Q30 staged family returned no construction session");
        }

        private void prepareWarm() {
            phaseName = "warmPreparation";
            if (!staged.isPreparationComplete()) {
                staged.finishPreparation();
                if (!staged.isPreparationComplete()) staged.pause();
            }
            if (!staged.isPreparationComplete()) {
                publishProgress("warmPreparation", 0, 0);
                return;
            }
            warmPreparationMillis += sincePhaseStart();
            controller = sim.getGeneratedChallengeController();
            GeneratedChallengeLifecycleEvidence lifecycle = controller == null ? null :
                controller.getLifecycleEvidence();
            require(controller != null && lifecycle != null && lifecycle.healthyFamilyValidated &&
                    lifecycle.selectedFaultValidated && sim.getGeneratedBoardInstance() == owner,
                "Fresh warm owner lacks live healthy and selected-fault validation");
            staged.validatePhysical();
            stagedGraph = sim.elmList;
            warmContextKey = GeneratedDiagnosticContextKey.capture(sim, owner,
                REQUEST_CANONICAL, construction.realizationManifest);
            require(warmContextKey.isTrustedCapture() && warmContextKey.equals(contextKey),
                "Fresh same-seed normal owner did not reproduce the cold trusted D01 context");

            verifySourceContextChange();
            verifyMismatchedContextMiss();
            phase = Phase.WARM;
            phaseName = "warmCacheReuse";
            phaseStarted = System.currentTimeMillis();
            publishProgress("warmCacheReuse", 0, EXPECTED_HYPOTHESIS_COUNT);
        }

        private int expectedSampleCount() {
            int channels = owner.getBoard().getComponent("JOB") == null ? 1 : 2;
            return (7 + channels) * (1 << channels) + 1;
        }

        private void verifySourceContextChange() {
            JSONObject evidence = beginNegative("sourceContextChange");
            Map<String, PowerOperatingAssessment.SourceState> sourceStates =
                owner.getExternalPowerBindings().getSourceStates();
            PowerOperatingAssessment.SourceState original = sourceStates.get(SOURCE_CONTEXT_INPUT);
            require(original != null &&
                    original.getConnection() != PowerOperatingAssessment.Connection.UNKNOWN,
                "Q30 source context canary has no known MAIN12 connection state");
            final boolean originallyConnected =
                original.getConnection() == PowerOperatingAssessment.Connection.CONNECTED;
            GeneratedDiagnosticContextKey changed = null;
            try {
                sim.getBoardPowerController().setSourceConnected(SOURCE_CONTEXT_INPUT,
                    !originallyConnected);
                changed = GeneratedDiagnosticContextKey.capture(sim, owner,
                    REQUEST_CANONICAL, construction.realizationManifest);
            } finally {
                sim.getBoardPowerController().setSourceConnected(SOURCE_CONTEXT_INPUT,
                    originallyConnected);
                GeneratedRuntimeDeveloperSettlement.settle(sim, owner,
                    "q30-d01-source-context-restore");
            }
            staged.validatePhysical();
            GeneratedDiagnosticContextKey restored = GeneratedDiagnosticContextKey.capture(sim,
                owner, REQUEST_CANONICAL, construction.realizationManifest);
            require(changed != null && changed.isTrustedCapture() &&
                    !changed.equals(warmContextKey) && restored.isTrustedCapture() &&
                    restored.equals(warmContextKey) && sim.isGeneratedRuntimeSettled(),
                "Source-state context change was not captured and exactly restored before warm reuse");
            put(evidence, "inputId", SOURCE_CONTEXT_INPUT);
            put(evidence, "changedContextCaptured", true);
            put(evidence, "changedContextHash", changed.hashCode());
            put(evidence, "sourceConnectionRestored", true);
            put(evidence, "restoredContextMatchesWarm", true);
            completeNegative(evidence);
        }

        private void verifyMismatchedContextMiss() {
            contextMismatchEvidence = beginNegative("mismatchedContext");
            mismatchedContextKey = GeneratedDiagnosticContextKey.capture(sim, owner,
                REQUEST_CANONICAL + ";verifier-context-canary=changed@1",
                construction.realizationManifest);
            require(mismatchedContextKey.isTrustedCapture() &&
                    !mismatchedContextKey.equals(warmContextKey),
                "Changed D01 request manifest did not produce a trusted distinct context");
            int before = verificationCache.getMisses();
            GeneratedDiagnosticProofReceipt mismatchedReuse =
                GeneratedDiagnosticProofService.reuseCachedIfPresent(sim, owner, controller,
                    verificationCache, mismatchedContextKey);
            require(mismatchedReuse == null && verificationCache.getMisses() == before + 1,
                "D01 cache did not miss for an actual trusted mismatched context capture");
            put(contextMismatchEvidence, "mismatchedRequestCaptured", true);
            put(contextMismatchEvidence, "mismatchedContextHash", mismatchedContextKey.hashCode());
            put(contextMismatchEvidence, "cacheMissed", true);
            put(contextMismatchEvidence, "cacheMissCount", verificationCache.getMisses());
        }

        private void reuseWarmCache() {
            phaseName = "warmCacheReuse";
            int hitsBefore = verificationCache.getHits();
            int missesBefore = verificationCache.getMisses();
            warmReceipt = GeneratedDiagnosticProofService.reuseCachedIfPresent(sim, owner,
                controller, verificationCache, warmContextKey);
            require(warmReceipt != null && verificationCache.getHits() == hitsBefore + 1 &&
                    verificationCache.getMisses() == missesBefore,
                "Fresh normal owner did not receive one genuine verifier-only cache hit");
            warmReceipt.requireContextKey(warmContextKey);
            warmReceipt.requireAssessmentOwner(owner);
            require(controller.getDiagnosticProofReceipt() == warmReceipt &&
                    warmReceipt.isWarmReuseForDeveloperVerification() &&
                    warmReceipt.getElapsedMillis() == 0 &&
                    sameEvidenceValues(coldEvidence, warmReceipt.getEvidence()),
                "Warm D01 receipt is not newly owner-bound to the exact cold immutable evidence");
            require(coldProviderId.equals(warmReceipt.getProviderId()) &&
                    coldProgramIdentity.equals(warmReceipt.getProgramIdentity()) &&
                    coldPartitionCanonical.equals(warmReceipt.getPartitionPlan().canonical()),
                "Warm D01 receipt changed its provider, program or partition value");

            boolean staleReceiptRejected = false;
            try { warmReceipt.requireContextKey(mismatchedContextKey); }
            catch (IllegalStateException expected) { staleReceiptRejected = true; }
            require(staleReceiptRejected,
                "Warm D01 receipt accepted a different trusted request context");
            put(contextMismatchEvidence, "receiptRejectedMismatchedContext", staleReceiptRejected);
            completeNegative(contextMismatchEvidence);

            staged.validatePhysical();
            JSONObject warm = (JSONObject)report.get("warm").isObject();
            put(warm, "status", "PASS");
            put(warm, "cacheScope", "verifierOnly");
            put(warm, "cacheHit", true);
            put(warm, "cacheHits", verificationCache.getHits());
            put(warm, "cacheMisses", verificationCache.getMisses());
            put(warm, "sameTrustedContext", true);
            put(warm, "sameProviderProgramPartition", true);
            put(warm, "sameImmutableEvidence", true);
            put(warm, "samplePopulationPerHypothesis", expectedSampleCount());
            put(warm, "hypothesisCount", EXPECTED_HYPOTHESIS_COUNT);
            put(warm, "receiptBoundToFreshOwner", true);
            put(warm, "warmReuseReceipt", warmReceipt.isWarmReuseForDeveloperVerification());
            put(warm, "elapsedMillis", warmReceipt.getElapsedMillis());
            put(warm, "ownerSeed", Long.toString(owner.getSeed()));
            warmComplete = true;
            put(report, "d01Admission", true);
            phase = Phase.CLEANUP;
            phaseName = "warmStagedCleanup";
            phaseStarted = System.currentTimeMillis();
            publishProgress("warmStagedCleanup", EXPECTED_HYPOTHESIS_COUNT,
                EXPECTED_HYPOTHESIS_COUNT);
        }

        private void cleanupAndFinish() {
            phaseName = "cleanup";
            // Dropping unfinished family work abandons private route planning; the
            // Q30 session creates an electrical owner only when it returns complete.
            constructionSession = null;
            Throwable cleanupFailure = null;
            if (serviceCanaryScopeActive && serviceCanaryController != null) {
                try {
                    serviceCanaryController.endDeveloperVerificationScope();
                    serviceCanaryScopeActive = false;
                } catch (Throwable problem) {
                    cleanupFailure = retain(cleanupFailure, problem);
                }
            }
            if (serviceHealthyWork != null) {
                try {
                    serviceHealthyWork.cancel();
                    serviceHealthyWork = null;
                } catch (Throwable problem) {
                    cleanupFailure = retain(cleanupFailure, problem);
                }
            }
            if (serviceCanaryCursor != null) {
                try {
                    serviceCanaryCursor.cancel();
                    serviceCanaryCursor = null;
                    serviceCanaryClosedCount++;
                } catch (Throwable problem) {
                    cleanupFailure = retain(cleanupFailure, problem);
                }
            }
            if (cleanupFailureInjectionArmed) {
                GeneratedDiagnosticProofService.setCleanupFailureForDeveloperVerification(false);
                cleanupFailureInjectionArmed = false;
            }
            if (cleanupFailureSession != null) {
                try { cleanupFailureSession.cancel(); }
                catch (Throwable problem) { cleanupFailure = retain(cleanupFailure, problem); }
                if (cleanupFailureSession.hasPendingPrivateCleanup())
                    cleanupFailure = retain(cleanupFailure, new IllegalStateException(
                        "D01 cleanup canary retained pending private cleanup"));
            }
            if (cleanupFailureSession != null &&
                    cleanupFailureSession.hasPendingPrivateCleanup()) {
                cleanupMillis += sincePhaseStart();
                parkCleanupPending("canarySession", cleanupFailure);
                return;
            }
            if (proof != null) {
                try { proof.cancel(); }
                catch (Throwable problem) { cleanupFailure = retain(cleanupFailure, problem); }
                if (proof.hasPendingPrivateCleanup())
                    cleanupFailure = retain(cleanupFailure, new IllegalStateException(
                        "D01 proof retained pending private cleanup"));
            }
            if (proof != null && proof.hasPendingPrivateCleanup()) {
                cleanupMillis += sincePhaseStart();
                parkCleanupPending("proofSession", cleanupFailure);
                return;
            }
            if (staged != null) {
                try { staged.abort(); }
                catch (Throwable problem) {
                    cleanupMillis += sincePhaseStart();
                    parkCleanupPending("stagedAbort", retain(cleanupFailure, problem));
                    return;
                }
            } else if (owner != null) {
                // Construction may succeed before staged preflight rejects.
                // Dispose that private graph only after ruling out predecessor
                // identity reuse; there is no snapshot transaction to abort yet.
                try {
                    disposeUninstalledOwner();
                } catch (Throwable problem) {
                    cleanupMillis += sincePhaseStart();
                    parkCleanupPending("uninstalledOwner", retain(cleanupFailure, problem));
                    return;
                }
            }
            cleanupMillis += sincePhaseStart();
            ownerRestored = sim.getGeneratedBoardInstance() == predecessorOwner &&
                sim.getGeneratedChallengeController() == predecessorController &&
                sim.elmList == predecessorGraph &&
                sim.generatedRuntimeInstallationInProgress == predecessorInstallationInProgress;
            cleanupComplete = cleanupFailure == null && ownerRestored;
            if (!ownerRestored)
                cleanupFailure = retain(cleanupFailure, new IllegalStateException(
                    "Staged D01 cleanup did not restore the exact predecessor owner"));
            if (!ownerRestored) {
                parkCleanupPending("predecessorRestore", cleanupFailure);
                return;
            }
            if (cleanupFailure != null) failure = retain(failure, cleanupFailure);
            if (cleanupAudit != null) report.put("cleanupAudit", cleanupJson(cleanupAudit));

            if (failure == null && reportHasEvidence && !coldStageCleaned &&
                    pendingCacheEntry != null && cleanupComplete) {
                try {
                    verificationCache.storePrepared(pendingCacheEntry);
                    require(verificationCache.size() == 1 &&
                            !pendingCacheEntry.retainsRuntimeObjectsForDeveloperVerification(),
                        "Verifier-only D01 cache did not retain exactly one value artifact");
                    coldStageCleaned = true;
                    put(report, "cacheScope", "verifierOnly");
                    put(report, "cacheEntries", verificationCache.size());
                    put(report, "coldStageCleanupComplete", true);
                    clearCurrentStageReferences();
                    ownerRestored = false;
                    put(report, "ownerRestored", false);
                    put(report, "restored", false);
                    put(report, "cleanupComplete", true);
                    phase = Phase.CONSTRUCT_WARM;
                    phaseName = "warmConstruction";
                    phaseStarted = System.currentTimeMillis();
                    publishState("RUNNING", "warmConstruction");
                    return;
                } catch (Throwable problem) {
                    failure = retain(failure, problem);
                }
            }

            if (!reportHasEvidence) {
                JSONObject cold = new JSONObject();
                put(cold, "status", failure == null ? "NOT_RUN" : "FAIL");
                put(cold, "elapsedMillis", proof == null ? 0 : proof.getElapsedMillis());
                put(cold, "workUnits", proofUnits);
                if (cleanupAudit != null) cold.put("cleanupAudit", cleanupJson(cleanupAudit));
                report.put("cold", cold);
            }
            finishPendingNegativeStatuses();
            put(report, "ownerRestored", ownerRestored);
            put(report, "restored", ownerRestored);
            put(report, "cleanupComplete", cleanupComplete);
            putTimings();
            put(report, "normalAdmission", false);
            put(report, "registered", PlayerFamilyCatalog.contains(Rb30Plan.FAMILY_ID));
            put(report, "playerPublished", false);
            put(report, "d01Admission", warmComplete);
            put(report, "cleanupPending", false);
            put(report, "cleanupRetryCount", cleanupRetryCount);
            JSONObject warm = (JSONObject)report.get("warm").isObject();
            if (!warmComplete && "RUNNING".equals(stringValue(warm.get("status")))) {
                put(warm, "status", failure == null ? "NOT_RUN" : "FAIL");
                if (failure != null) put(warm, "reason", describe(failure));
            }
            boolean passed = failure == null && reportHasEvidence && warmComplete &&
                cleanupComplete && ownerRestored;
            put(report, "status", passed ? "PASS" : "FAIL");
            if (failure != null) {
                put(report, "failurePhase", failurePhase == null ? phaseName : failurePhase);
                put(report, "failure", describe(failure));
            }
            phase = Phase.DONE;
            timer.cancel();
            sim.developerVerifierRunning = predecessorVerifierRunning;
            if (sim.generatedRuntimeInstallationInProgress != predecessorInstallationInProgress &&
                    ownerRestored) {
                sim.generatedRuntimeInstallationInProgress = predecessorInstallationInProgress;
            }
            clearCurrentStageReferences();
            warmReceipt = null;
            construction = null;
            publish(report.toString(), passed ? "PASS" : "FAIL");
            if (active == this) active = null;
        }

        private void disposeUninstalledOwner() {
            Vector<CircuitElm> elements = owner.getSimulationElements();
            require(sim.getGeneratedBoardInstance() != owner,
                "Refused uninstalled candidate cleanup after it became the live owner");
            for (CircuitElm element : elements) {
                require(!predecessorGraph.contains(element) &&
                        (sim.elmList == null || !sim.elmList.contains(element)),
                    "Refused uninstalled candidate cleanup over a live graph");
            }
            owner.getExternalPowerBindings().setConnected(false);
            require(owner.getExternalPowerBindings().areAllDisconnected(),
                "Uninstalled candidate cleanup retained connected power bindings");
            Throwable failure = null;
            for (CircuitElm element : elements) {
                if (uninstalledDisposedElements.contains(element)) continue;
                try {
                    element.delete();
                    uninstalledDisposedElements.add(element);
                } catch (Throwable problem) {
                    failure = retain(failure, problem);
                }
            }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure != null)
                throw new IllegalStateException("Uninstalled candidate disposal failed", failure);
        }

        private void clearCurrentStageReferences() {
            constructionSession = null;
            proof = null;
            cleanupFailureSession = null;
            receipt = null;
            warmReceipt = null;
            staged = null;
            owner = null;
            controller = null;
            construction = null;
            stagedGraph = null;
            serviceCanaryCursor = null;
            serviceHealthyWork = null;
            serviceCanaryOwner = null;
            serviceCanaryController = null;
            serviceCanaryFaultBinding = null;
            serviceCanaryBindings = null;
            serviceCanarySavedControls = null;
            serviceCanaryGraph = null;
            serviceCanaryGraphContents = null;
            serviceCanaryPolicy = null;
            serviceCanaryInputIds = null;
            serviceCanaryPowerBindings = null;
            serviceCanarySources = null;
            serviceCanarySourceVoltages = null;
            serviceCanarySourceLimits = null;
        }

        private void finishPendingNegativeStatuses() {
            for (JSONObject negative : unfinishedNegatives)
                put(negative, "status", failure == null ? "NOT_RUN" : "FAIL");
            unfinishedNegatives.clear();
        }

        private void publishProgress(String state, int completed, int total) {
            put(report, "status", "RUNNING");
            put(report, "phase", state);
            put(report, "completedHypotheses", completed);
            put(report, "totalHypotheses", total);
            put(report, "elapsedMs", elapsed());
            publish(report.toString(), "RUNNING:" + state);
        }

        private JSONObject beginNegative(String key) {
            JSONObject evidence = new JSONObject();
            put(evidence, "status", "RUNNING");
            negativeEvidence.put(key, evidence);
            unfinishedNegatives.add(evidence);
            return evidence;
        }

        private void completeNegative(JSONObject evidence) {
            put(evidence, "status", "PASS");
            unfinishedNegatives.remove(evidence);
        }

        private void putTimings() {
            JSONObject timings = new JSONObject();
            put(timings, "constructionMs", constructionMillis);
            put(timings, "stagedPreparationMs", preparationMillis);
            put(timings, "physicalValidationMs", physicalValidationMillis);
            put(timings, "coldProofMs", proofMillis);
            put(timings, "coldProofActiveWorkMs", proofActiveWorkMillis);
            put(timings, "failedCleanupCanaryMs", cleanupCanaryMillis);
            put(timings, "warmConstructionMs", warmConstructionMillis);
            put(timings, "warmPreparationMs", warmPreparationMillis);
            put(timings, "classificationMs", classificationMillis);
            put(timings, "cleanupMs", cleanupMillis);
            put(timings, "totalMs", elapsed());
            report.put("phaseElapsedMs", timings);
            JSONArray units = new JSONArray();
            for (Map.Entry<String, long[]> entry : proofUnitTimings.entrySet()) {
                JSONObject unit = new JSONObject();
                put(unit, "phase", entry.getKey());
                put(unit, "units", entry.getValue()[0]);
                put(unit, "activeMillis", entry.getValue()[1]);
                put(unit, "maximumUnitMillis", entry.getValue()[2]);
                units.set(units.size(), unit);
            }
            report.put("coldProofUnitTimings", units);
        }

        private long elapsed() { return Math.max(0, System.currentTimeMillis() - startedMillis); }
        private long sincePhaseStart() {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0, now - phaseStarted);
            phaseStarted = now;
            return elapsed;
        }
    }

    private static JSONArray strings(Vector<String> values) {
        JSONArray result = new JSONArray();
        for (String value : values) result.set(result.size(), new JSONString(value));
        return result;
    }

    /** The warm artifact must preserve all immutable evidence fields and samples. */
    private static boolean sameEvidenceValues(
            Vector<GeneratedDiagnosticSolvabilityEvidence> first,
            Vector<GeneratedDiagnosticSolvabilityEvidence> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            GeneratedDiagnosticSolvabilityEvidence left = first.get(index);
            GeneratedDiagnosticSolvabilityEvidence right = second.get(index);
            if (left == null || right == null ||
                    !left.getRouteId().equals(right.getRouteId()) ||
                    !left.getFamilyId().equals(right.getFamilyId()) || left.getSeed() != right.getSeed() ||
                    !left.getHypothesisKey().equals(right.getHypothesisKey()) ||
                    left.getAdmittedCandidateCount() != right.getAdmittedCandidateCount() ||
                    left.getAdmittedPhysicalOwnerCount() != right.getAdmittedPhysicalOwnerCount() ||
                    left.getDeclaredPlanDepth() != right.getDeclaredPlanDepth() ||
                    !left.getDeclaredTemplateIds().equals(right.getDeclaredTemplateIds()) ||
                    !left.getDeclaredProbeTargetIds().equals(right.getDeclaredProbeTargetIds()) ||
                    !left.getDeclaredInputPowerTransitions().equals(
                        right.getDeclaredInputPowerTransitions()) ||
                    !left.getDeclaredIsolationActionIds().equals(
                        right.getDeclaredIsolationActionIds()) ||
                    !left.getDeclaredRepairActionIds().equals(right.getDeclaredRepairActionIds()) ||
                    !left.getDeclaredWorkflowActionIds().equals(right.getDeclaredWorkflowActionIds()) ||
                    !left.getDeclaredPlayerOperationIds().equals(
                        right.getDeclaredPlayerOperationIds()) ||
                    !left.getDeclaredMeterModeIds().equals(right.getDeclaredMeterModeIds()) ||
                    !left.getDeclaredTemporalWaitSampleIds().equals(
                        right.getDeclaredTemporalWaitSampleIds()) ||
                    !left.getDeclaredRailDomainIds().equals(right.getDeclaredRailDomainIds()) ||
                    left.hasDeclaredParallelPathAmbiguity() !=
                        right.hasDeclaredParallelPathAmbiguity() ||
                    !left.getExecutedActionIds().equals(right.getExecutedActionIds()) ||
                    !left.getExecutedRepairActionIds().equals(right.getExecutedRepairActionIds()) ||
                    !left.getExecutedMeterModeIds().equals(right.getExecutedMeterModeIds()) ||
                    !left.getExecutedInputPowerTransitions().equals(
                        right.getExecutedInputPowerTransitions()) ||
                    !left.getExecutedIsolationActionIds().equals(
                        right.getExecutedIsolationActionIds()) ||
                    !left.getExecutedTemporalWaitSamples().equals(
                        right.getExecutedTemporalWaitSamples()) ||
                    left.getMeasuredExecutionDepth() != right.getMeasuredExecutionDepth() ||
                    left.getCompletedSemanticActions() != right.getCompletedSemanticActions() ||
                    !left.getRepairSemantics().isEquivalentTo(right.getRepairSemantics()) ||
                    left.hasUnaffectedFunctionRetestObservation() !=
                        right.hasUnaffectedFunctionRetestObservation() ||
                    !left.getEquivalentRepairClass().equals(right.getEquivalentRepairClass()) ||
                    !left.getDeterministicResult().equals(right.getDeterministicResult()) ||
                    !left.getDeterministicRejectionReason().equals(
                        right.getDeterministicRejectionReason()) ||
                    left.isRepairReachable() != right.isRepairReachable() ||
                    left.isCustomerRetestPassed() != right.isCustomerRetestPassed() ||
                    left.isStateIsolated() != right.isStateIsolated() ||
                    !sameSamplesExactly(left.getSolverSamples(), right.getSolverSamples()))
                return false;
        }
        return true;
    }

    private static boolean sameSamplesExactly(Vector<GeneratedDiagnosticSample> first,
            Vector<GeneratedDiagnosticSample> second) {
        if (first == null || second == null || first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            GeneratedDiagnosticSample left = first.get(index);
            GeneratedDiagnosticSample right = second.get(index);
            if (left == null || right == null ||
                    !left.getSampleId().equals(right.getSampleId()) ||
                    left.getOutcome() != right.getOutcome() ||
                    left.isOverRange() != right.isOverRange()) return false;
            if (!left.isOverRange() &&
                    (Double.doubleToLongBits(left.getValue()) !=
                        Double.doubleToLongBits(right.getValue()) ||
                     Double.doubleToLongBits(left.getComparisonTolerance()) !=
                        Double.doubleToLongBits(right.getComparisonTolerance()))) return false;
        }
        return true;
    }

    private static String stringValue(com.google.gwt.json.client.JSONValue value) {
        if (value == null || value.isString() == null) return "";
        return value.isString().stringValue();
    }

    private static boolean hasMessage(Throwable problem, String fragment) {
        if (problem == null) return false;
        String message = problem.getMessage();
        if (message != null && message.indexOf(fragment) >= 0) return true;
        if (hasMessage(problem.getCause(), fragment)) return true;
        Throwable[] suppressed = problem.getSuppressed();
        for (Throwable item : suppressed)
            if (hasMessage(item, fragment)) return true;
        return false;
    }

    private static Vector<String> observationSampleIds(GeneratedDiagnosticProgram program) {
        Vector<String> result = new Vector<String>();
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            if (step.kind == GeneratedDiagnosticProgram.Kind.DIODE) {
                result.add(step.id + "_VOLTAGE");
                result.add(step.id + "_CURRENT");
            } else if (step.kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY) {
                result.add(step.id);
            }
        }
        return result;
    }

    private static JSONObject cleanupJson(GeneratedDiagnosticProofService.CleanupAudit audit) {
        JSONObject result = new JSONObject();
        put(result, "cleanupAttemptCount", audit.getCleanupAttemptCount());
        put(result, "disposedHypothesisCount", audit.getDisposedHypothesisCount());
        put(result, "disconnectedBindingCount", audit.getDisconnectedBindingCount());
        put(result, "disposedElementCount", audit.getDisposedElementCount());
        put(result, "disposalFailureCount", audit.getDisposalFailureCount());
        put(result, "lastBindingCount", audit.getLastBindingCount());
        put(result, "lastDisconnectedBindingCount", audit.getLastDisconnectedBindingCount());
        put(result, "lastElementCount", audit.getLastElementCount());
        put(result, "lastDeletedElementCount", audit.getLastDeletedElementCount());
        put(result, "lastOwnerGuardPassed", audit.wasLastOwnerGuardPassed());
        put(result, "lastBindingsActuallyDisconnected", audit.wereLastBindingsActuallyDisconnected());
        put(result, "lastElementsActuallyDeleted", audit.wereLastElementsActuallyDeleted());
        put(result, "lastGraphDetached", audit.wasLastGraphDetached());
        put(result, "lastCleanupComplete", audit.wasLastCleanupComplete());
        return result;
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "unknown failure";
        String message = failure.getMessage();
        return message == null || message.length() == 0 ? failure.toString() : message;
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next != null && next != first) first.addSuppressed(next);
        return first;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Q30 D01: " + message);
    }

    private static void put(JSONObject json, String key, String value) {
        json.put(key, new JSONString(value == null ? "" : value));
    }
    private static void put(JSONObject json, String key, boolean value) {
        json.put(key, JSONBoolean.getInstance(value));
    }
    private static void put(JSONObject json, String key, int value) {
        json.put(key, new JSONNumber(value));
    }
    private static void put(JSONObject json, String key, long value) {
        json.put(key, new JSONNumber(value));
    }
    private static void put(JSONObject json, String key, double value) {
        json.put(key, new JSONNumber(value));
    }

    private static native void publish(String report, String state) /*-{
        var root = $doc.documentElement;
        root.setAttribute("data-tsj-q30-d01-report", report);
        root.setAttribute("data-tsj-q30-d01-state", state);
    }-*/;
}
