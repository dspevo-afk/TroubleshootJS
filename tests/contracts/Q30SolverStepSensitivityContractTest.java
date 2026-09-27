package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Paired real-CircuitJS qualification of the Q30 production 5 us maximum
 * step against an independent finer 2.5 us numerical reference. Every result is
 * from a fresh owner on the exact same accepted physical layout; no synthetic
 * readings or solver substitute are used here.
 */
public final class Q30SolverStepSensitivityContractTest {
    private static final double PRODUCTION_STEP = 5e-6;
    private static final double REFERENCE_STEP = 2.5e-6;
    private static final double CANDIDATE_STEP = PRODUCTION_STEP;
    private static final double SAMPLE_SECONDS = .030;
    private static final double SERVICE_WAIT_SECONDS = .050;
    private static final int SERVICE_WAIT_COUNT = 5;
    private static final int MAX_RELAY_DISCHARGE_STEPS = 100;
    private static final double RELATIVE_TOLERANCE = .01;
    private static final double ABSOLUTE_TOLERANCE = .01;
    private static final String[] INPUT_OPERATIONS = {
        Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_A_ONLY,
        Rb30Behavior.SENSORS_B_ONLY, Rb30Behavior.SENSORS_HIGH
    };
    private static final boolean[][] EXPECTED_OUTPUTS = {
        { false, false }, { true, false }, { false, true }, { true, true }
    };
    private static final String[][] FAULTS = {
        { "DREV_OPEN", "DIODE_OPEN", "DREV", "3" },
        { "REN_OPEN", "RESISTOR_OPEN", "REN", "3" },
        { "SENSOR_A_OPEN", "RESISTOR_OPEN", "RSA", "1" },
        { "DRIVE_A_OPEN", "RESISTOR_OPEN", "RDA", "1" },
        { "RELAY_B_COIL_OPEN", "RELAY_COIL_OPEN", "KB", "2" }
    };
    private static final String[][] SAMPLE_PAIRS = {
        { "DREV.K", "J1.2" }, { "REN.2", "J1.2" },
        { "U1.OUTPUT", "J1.2" }, { "RSA.2", "J1.2" },
        { "RDA.1", "J1.2" }, { "RDA.2", "J1.2" },
        { "KB.A2", "J1.2" }, { "JOA.1", "JOA.2" },
        { "JOB.1", "JOB.2" }, { "J1.1", "J1.2" }
    };
    private static final String[] SAMPLE_NAMES = {
        "DREV.K-J1.2", "REN.2-J1.2", "U1.OUTPUT-J1.2", "RSA.2-J1.2",
        "RDA.1-J1.2", "RDA.2-J1.2", "KB.A2-J1.2", "JOA.1-JOA.2",
        "JOB.1-JOB.2", "MAIN_12V"
    };

    private static int assertions;

    private Q30SolverStepSensitivityContractTest() { }

    public static void main(String[] args) {
        long started = System.nanoTime();
        long seed = 7L;
        String selectedFault = null;
        if (args.length != 0) {
            if ((args.length != 2 && args.length != 4) || !"--seed".equals(args[0]) ||
                    (args.length == 4 && !"--fault".equals(args[2])))
                throw new IllegalArgumentException("Usage: --seed <canonical-signed-long> [--fault " +
                    "DREV_OPEN|REN_OPEN|SENSOR_A_OPEN|DRIVE_A_OPEN|RELAY_B_COIL_OPEN]");
            seed = Long.parseLong(args[1]);
            if (!Long.toString(seed).equals(args[1]))
                throw new IllegalArgumentException("Noncanonical Q30 sensitivity seed: " + args[1]);
            if (args.length == 4) {
                selectedFault = args[3];
                faultIndex(selectedFault);
            }
        }
        int cases = 0;
        verifySeed(seed, selectedFault);
        cases = selectedFault == null ? FAULTS.length : 1;
        System.out.println("PASS: Q30 production solver step sensitivity assertions=" + assertions +
            " seed=" + seed + " support=" + Rb30Plan.resolve(seed).supportVariant.name() +
            " faults=" + cases + " referenceMaximumStepSeconds=" + format(REFERENCE_STEP) +
            " productionCandidateMaximumStepSeconds=" +
            format(CANDIDATE_STEP) + " candidateKind=PRODUCTION_5_US" +
            " elapsedMillis=" + elapsedMillis(started));
    }

    private static int faultIndex(String faultId) {
        for (int index = 0; index < FAULTS.length; index++)
            if (FAULTS[index][0].equals(faultId)) return index;
        throw new IllegalArgumentException("Unsupported Q30 sensitivity fault: " + faultId);
    }

    private static void verifySeed(long seed, String selectedFault) {
        long seedStarted = System.nanoTime();
        CirSim priorGlobalSim = CircuitElm.sim;
        Rb30Plan plan = Rb30Plan.resolve(seed);
        Vector<OwnerResource> resources = new Vector<OwnerResource>();
        boolean cleanupVerified = false;
        long seedCleanupMillis = 0;
        try {
            // Route exactly once through the accepted medium policy. The
            // routed DREV owner is also the first fresh solver owner; all
            // other owners receive copies of that sealed accepted layout.
            SensitivityCirSim routedSim = newSensitivitySim(seed, FAULTS[0][0],
                REFERENCE_STEP);
            CircuitElm.sim = routedSim;
            GeneratedFault firstFault = new GeneratedFault(FAULTS[0][0],
                GeneratedFaultType.valueOf(FAULTS[0][1]), FAULTS[0][2],
                Rb30Plan.FAMILY_ID, seed);
            OwnerResource routedDrev = track(resources, new Rb30Generator()
                .generateForHypothesis(seed, firstFault.getHypothesisKey()), routedSim);
            PcbBoardLayout acceptedLayout = routedDrev.owner.getPcbLayout();
            String layoutFingerprint = acceptedLayout.geometryFingerprint();
            String physicalFingerprint = PhysicalBoardFingerprint.of(routedDrev.owner);
            require(acceptedLayout.isSealed(), "accepted route layout is sealed");
            require(routedDrev.owner.isDeveloperOnlyFaultRoute(),
                "solver sensitivity owners remain developer-only");

            Set<CircuitElm> priorElements = new HashSet<CircuitElm>();
            priorElements.addAll(collectOwnerElements(routedDrev.owner, null));
            int from = selectedFault == null ? 0 : faultIndex(selectedFault);
            int to = selectedFault == null ? FAULTS.length : from + 1;
            for (int fault = from; fault < to; fault++) {
                String faultId = FAULTS[fault][0];
                OwnerResource referenceOwner = fault == 0 ? routedDrev :
                    track(resources, assembleFresh(plan, acceptedLayout, faultId,
                        seed, REFERENCE_STEP));
                OwnerResource candidateOwner = track(resources,
                    assembleFresh(plan, acceptedLayout, faultId,
                        seed, CANDIDATE_STEP));
                for (OwnerResource resource : new OwnerResource[] {
                        referenceOwner, candidateOwner }) {
                    GeneratedBoardInstance owner = resource.owner;
                    require(owner.getFaultBinding().getFault().getId().equals(faultId),
                        "fresh owner selects requested fault " + faultId);
                    require(layoutFingerprint.equals(owner.getPcbLayout().geometryFingerprint()) &&
                            physicalFingerprint.equals(PhysicalBoardFingerprint.of(owner)),
                        "both timestep owners reuse the exact accepted seed route " + faultId);
                    if (resource != routedDrev)
                        for (CircuitElm element : collectOwnerElements(owner, null))
                            require(priorElements.add(element),
                                "fresh solver owners have disjoint CircuitJS elements " + faultId);
                }

                RunResult reference = runOwner(seed, fault, referenceOwner,
                    REFERENCE_STEP);
                RunResult candidate = runOwner(seed, fault, candidateOwner,
                    CANDIDATE_STEP);
                require(referenceOwner.cleanupVerified && candidateOwner.cleanupVerified,
                    "both paired owners are disconnected and deleted before comparison");
                Delta maximum = compare(reference, candidate, faultId);
                System.out.println("Q30_STEP_PAIR seed=" + seed + " support=" +
                    plan.supportVariant.name() + " fault=" + faultId +
                    " referenceMaximumStepSeconds=" + format(REFERENCE_STEP) +
                    " productionCandidateMaximumStepSeconds=" +
                    format(CANDIDATE_STEP) +
                    " candidateKind=PRODUCTION_5_US" +
                    " maxDeltaVolts=" + format(maximum.delta) +
                    " toleranceVolts=" + format(maximum.tolerance) +
                    " phase=" + maximum.phase + " sample=" + maximum.sample +
                    " referenceElapsedMillis=" + reference.elapsedMillis +
                    " productionCandidateElapsedMillis=" + candidate.elapsedMillis +
                    " referenceSimSeconds=" + format(reference.simulatedSeconds) +
                    " productionCandidateSimSeconds=" + format(candidate.simulatedSeconds) +
                    " referenceCleanupMillis=" + referenceOwner.cleanupMillis +
                    " productionCandidateCleanupMillis=" + candidateOwner.cleanupMillis +
                    " cleanup=true");
            }
            System.out.println("Q30_STEP_SEED seed=" + seed + " support=" +
                plan.supportVariant.name() + " referenceMaximumStepSeconds=" +
                format(REFERENCE_STEP) + " productionCandidateMaximumStepSeconds=" +
                format(CANDIDATE_STEP) +
                " candidateKind=PRODUCTION_5_US routeTopology=" + plan.topology() +
                " routeAndPairsElapsedMillis=" + elapsedMillis(seedStarted));
        } finally {
            long cleanupStarted = System.nanoTime();
            try {
                cleanupVerified = disposeAll(resources);
            } finally {
                CircuitElm.sim = priorGlobalSim;
            }
            seedCleanupMillis = elapsedMillis(cleanupStarted);
            System.out.println("Q30_STEP_SEED_CLEANUP seed=" + seed +
                " cleanup=" + cleanupVerified +
                " elapsedMillis=" + seedCleanupMillis);
        }
        require(cleanupVerified, "all seed-scoped owners cleaned up");
    }

    private static OwnerResource track(Vector<OwnerResource> resources,
            GeneratedBoardInstance owner, SensitivityCirSim ownerSim) {
        OwnerResource resource = new OwnerResource(owner, ownerSim);
        resources.add(resource);
        return resource;
    }

    private static OwnerResource track(Vector<OwnerResource> resources,
            OwnerResource resource) {
        if (resource == null) throw new IllegalArgumentException("Missing Q30 owner resource");
        resources.add(resource);
        return resource;
    }

    private static OwnerResource assembleFresh(Rb30Plan plan,
            PcbBoardLayout acceptedLayout, String faultId, long seed, double maxStep) {
        SensitivityCirSim ownerSim = newSensitivitySim(seed, faultId, maxStep);
        CirSim priorGlobalSim = CircuitElm.sim;
        CircuitElm.sim = ownerSim;
        Rb30Generator generator = new Rb30Generator();
        Rb30Generator.Candidate candidate = generator.construct(plan, faultId);
        boolean assembled = false;
        try {
            GeneratedBoardInstance owner = generator.assemble(candidate,
                acceptedLayout.copySealed());
            assembled = true;
            return new OwnerResource(owner, ownerSim);
        } finally {
            if (!assembled) {
                candidate.assembly.power.setConnected(false);
                try {
                    CircuitElm.sim = ownerSim;
                    for (CircuitElm element : candidate.elements()) element.delete();
                } finally {
                    CircuitElm.sim = priorGlobalSim;
                }
            }
        }
    }

    private static SensitivityCirSim newSensitivitySim(long seed, String faultId,
            double maxStep) {
        SensitivityCirSim sim = new SensitivityCirSim();
        Q30ServiceFlowContractTest.configureSimulator(sim, maxStep);
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.sensitivitySeed = seed;
        sim.sensitivityFault = faultId;
        sim.sensitivityMaximumStep = maxStep;
        return sim;
    }

    private static RunResult runOwner(long seed, int faultIndex,
            OwnerResource resource, double maxStep) {
        final GeneratedBoardInstance owner = resource.owner;
        long started = System.nanoTime();
        final SensitivityCirSim sim = resource.ownerSim;
        resource.solverStep = maxStep;
        resource.started = true;
        try {
        CircuitElm.sim = sim;
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        BoardModificationController modifications =
            new BoardModificationController(sim, owner);
        sim.boardModificationController = modifications;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, owner, modifications, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
            @Override boolean allowsWorkbenchInteraction() {
                return sim.nativeProfilesReady;
            }
        };

        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        require(owner.isDeveloperOnlyFaultRoute(), "sensitivity stays developer-only");
        require(sim.timeStep == maxStep && sim.maxTimeStep == maxStep &&
                sim.minTimeStep == 50e-12 && sim.adjustTimeStep,
            "owner is configured for declared maximum " + format(maxStep) +
            " s with unchanged minimum and adaptive solver");
        Rb30Behavior behavior = (Rb30Behavior) owner.getFamilyState();
        Capture capture = new Capture(owner, behavior);
        sim.capture = capture;
        owner.getFaultBinding().setApplied(false);
        recordStepProfileBoundary(sim, "initial-electrical-settle", true);
        settleElectrical(sim, "initial-electrical-settle");
        recordStepProfileBoundary(sim, "initial-electrical-settle", false);

        capture.begin("healthy");
        recordStepProfileBoundary(sim, "healthy-preparation", true);
        if (maxStep == PRODUCTION_STEP) {
            behavior.prepareHealthyProfile(sim, owner);
        } else {
            for (int condition = 0; condition < 4; condition++) {
                behavior.setInputs(sim, owner, condition);
                require(behavior.healthy(owner, condition),
                    format(maxStep * 1e6) + " us finer-reference healthy condition " +
                    condition + " passes for " + FAULTS[faultIndex][0]);
            }
            behavior.setInputs(sim, owner, 0);
        }
        recordStepProfileBoundary(sim, "healthy-preparation", false);
        double[] healthy = capture.finish("healthy");
        require(behavior.getInputs() == 0 && behavior.healthy(owner, 0),
            "healthy preparation reaches real LOW before fault application");
        verifyTruth(healthy, 0, "healthy", FAULTS[faultIndex][0]);

        owner.getFaultBinding().setApplied(true);
        recordStepProfileBoundary(sim, "fault-profile-preparation", true);
        behavior.prepareFaultedProfile(sim, owner);
        recordStepProfileBoundary(sim, "fault-profile-preparation", false);
        require(behavior.getInputs() == 3 &&
                behavior.getObservedBehavior() == GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            "fault application produces a measured HIGH-condition symptom");
        recordStepProfileBoundary(sim, "faulted-four-input-profile", true);
        capture.begin("faulted");
        int symptomMask = 0;
        for (int condition = 0; condition < 4; condition++) {
            GeneratedCustomerRetestResult inputResult = owner.invokeOperation(
                INPUT_OPERATIONS[condition], sim);
            require(inputResult == null, "real input operation " + INPUT_OPERATIONS[condition]);
            double a = Rb30Behavior.voltage(owner, "JOA.1", "JOA.2");
            double b = Rb30Behavior.voltage(owner, "JOB.1", "JOB.2");
            if (!matches(a, EXPECTED_OUTPUTS[condition][0])) symptomMask |= 1;
            if (!matches(b, EXPECTED_OUTPUTS[condition][1])) symptomMask |= 2;
        }
        double[] faulted = capture.finish("faulted");
        recordStepProfileBoundary(sim, "faulted-four-input-profile", false);
        require(symptomMask == Integer.parseInt(FAULTS[faultIndex][3]),
            "faulted four-condition truth mask matches independent Q30 oracle");
        verifyTruth(faulted, Integer.parseInt(FAULTS[faultIndex][3]),
            "faulted", FAULTS[faultIndex][0]);

        sim.nativeProfilesReady = true;
        recordStepProfileBoundary(sim, "unrepaired-customer-retest", true);
        capture.begin("wrong-original-retest");
        GeneratedCustomerRetestResult unrepaired = owner.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        double[] wrongOriginal = capture.finish("wrong-original-retest");
        recordStepProfileBoundary(sim, "unrepaired-customer-retest", false);
        require(unrepaired != null && !unrepaired.isPassed(),
            "unrepaired actual four-input customer retest fails");
        verifyTruth(wrongOriginal, Integer.parseInt(FAULTS[faultIndex][3]),
            "wrong-original-retest", FAULTS[faultIndex][0]);

        String component = FAULTS[faultIndex][2];
        PhysicalPart<?> original = runtime.getInstalledPart(component);
        PhysicalSlotMutationProvider service = runtime.getMutationProvider(component);
        String catalogId = owner.getDiagnosticProvider().getCorrectCatalogId(owner, component);
        String expectedLayout = owner.getPcbLayout().geometryFingerprint();
        String expectedPhysical = PhysicalBoardFingerprint.of(owner);
        require(original != null && service != null && catalogId != null,
            "selected fault has production physical service and catalog owners");
        Map<String, CircuitMeasurementEndpoint> padEndpoints = capturePadEndpoints(owner);

        powerOffAndWait250ms(sim, owner, runtime, modifications);
        require(service.removeInstalledPart(), "production service removes faulted " + component);
        settleMutation(sim, owner, modifications, runtime, "remove-faulted-part");
        require(runtime.getInstalledPart(component) == null,
            "physical removal leaves selected slot empty");
        require(service.install(original.getId()), "original component can be reinstalled");
        settleMutation(sim, owner, modifications, runtime, "reinstall-original-part");
        require(runtime.getInstalledPart(component) == original && owner.getFaultBinding().isApplied(),
            "reinstalled original retains exact identity and injected fault");
        powerOn(sim, owner, runtime, modifications);
        recordStepProfileBoundary(sim, "reinstalled-original-customer-retest", true);
        capture.begin("wrong-original-retest");
        GeneratedCustomerRetestResult originalRetest = owner.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        double[] originalAfterReinstall = capture.finish("wrong-original-retest");
        recordStepProfileBoundary(sim, "reinstalled-original-customer-retest", false);
        require(originalRetest != null && !originalRetest.isPassed(),
            "reinstalling the original remains a wrong repair");
        verifyTruth(originalAfterReinstall, Integer.parseInt(FAULTS[faultIndex][3]),
            "wrong-original-retest", FAULTS[faultIndex][0]);

        powerOffAndWait250ms(sim, owner, runtime, modifications);
        require(service.removeInstalledPart(), "faulted original can be removed for replacement");
        settleMutation(sim, owner, modifications, runtime, "remove-original-before-catalog-replacement");
        require(service.installNewFromCatalog(catalogId),
            "production catalog installs the correct repair part");
        settleMutation(sim, owner, modifications, runtime, "catalog-replacement");
        PhysicalPart<?> replacement = runtime.getInstalledPart(component);
        require(replacement != null && replacement != original,
            "catalog replacement has a distinct physical identity");
        require(expectedLayout.equals(owner.getPcbLayout().geometryFingerprint()) &&
                expectedPhysical.equals(PhysicalBoardFingerprint.of(owner)),
            "service preserves accepted geometry and physical fingerprint");
        verifyPadEndpoints(owner, padEndpoints);
        owner.getConnectionBindings().validateAgainst(owner.getBoard(),
            owner.getSimulationElements(), owner.getComponentBindings(),
            owner.getExternalPowerBindings(), owner.getFaultBinding());
        powerOn(sim, owner, runtime, modifications);
        recordStepProfileBoundary(sim, "repaired-customer-retest", true);
        capture.begin("customer-retest");
        GeneratedCustomerRetestResult repaired = owner.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        double[] repairedReadings = capture.finish("customer-retest");
        recordStepProfileBoundary(sim, "repaired-customer-retest", false);
        require(repaired != null && repaired.isPassed(),
            "correct physical catalog repair passes actual customer retest");
        verifyTruth(repairedReadings, 0, "customer-retest", FAULTS[faultIndex][0]);
        require(expectedLayout.equals(owner.getPcbLayout().geometryFingerprint()) &&
                expectedPhysical.equals(PhysicalBoardFingerprint.of(owner)),
            "repaired owner retains a sealed routed layout");

        sim.capture = null;
        long elapsed = elapsedMillis(started);
        double simulatedSeconds = sim.t;
        return new RunResult(healthy, faulted, wrongOriginal, repairedReadings,
            elapsed, simulatedSeconds);
        } finally {
            cleanupActiveOwner(resource, sim);
        }
    }

    private static void powerOffAndWait250ms(CirSim sim,
            GeneratedBoardInstance owner, PhysicalBoardRuntime runtime,
            BoardModificationController modifications) {
        SensitivityCirSim sensitivitySim = (SensitivityCirSim) sim;
        recordStepProfileBoundary(sensitivitySim, "service-power-off-wait", true);
        if (sim.boardPowerController.getState() != BoardPowerState.UNPOWERED) {
            sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
            runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
        }
        settleElectrical(sim, "service-power-off-disconnect");
        require(sim.boardPowerController.isElectricallyUnpowered(),
            "all external power is disconnected before service");
        double waitStart = sim.t;
        for (int step = 0; step < SERVICE_WAIT_COUNT; step++)
            sim.advanceGeneratedTemporalProfile(SERVICE_WAIT_SECONDS);
        double elapsed = sim.t - waitStart;
        require(elapsed >= .250 - 1e-12 &&
                elapsed <= .250 + SERVICE_WAIT_COUNT * sim.maxTimeStep + 1e-9,
            "CircuitJS advances at least the declared 250 ms power-off service guard");
        if ("KB".equals(owner.getFaultLocus().getComponentId())) {
            if (!Rb30RelayService.isDischarged(owner, "KB")) {
                boolean rejected = false;
                try { runtime.getMutationProvider("KB").removeInstalledPart(); }
                catch (BoardModificationRejectedException expected) { rejected = true; }
                require(rejected && runtime.getInstalledPart("KB") != null,
                    "residual target energy blocks the relay mutation after 250 ms");
                int extra = 0;
                while (!Rb30RelayService.isDischarged(owner, "KB") &&
                        extra < MAX_RELAY_DISCHARGE_STEPS) {
                    sim.advanceGeneratedTemporalProfile(SERVICE_WAIT_SECONDS);
                    extra++;
                }
                require(Rb30RelayService.isDischarged(owner, "KB"),
                    "real relay pins and coil reach the scoped service threshold");
            }
        }
        verifySettledOwner(sim, owner, modifications);
        recordStepProfileBoundary(sensitivitySim, "service-power-off-wait", false);
    }

    private static void recordStepProfileBoundary(SensitivityCirSim sim,
            String profile, boolean entering) {
        if (entering) sim.beginStepObservation(profile);
        else {
            require(profile.equals(sim.observedProfile),
                "step receipt closes the active profile " + profile);
            sim.observeCurrentStep();
        }
        require(sameStep(sim.maxTimeStep, sim.sensitivityMaximumStep),
            profile + " keeps configured maximum equal to declared " +
            format(sim.sensitivityMaximumStep) + " s step");
        require(sim.timeStep > 0 && sim.timeStep <= sim.maxTimeStep + 1e-15,
            profile + " has a valid current CircuitJS step");
        require(finite(sim.observedMinimumStep) &&
                sim.observedMaximumStep <= sim.sensitivityMaximumStep + 1e-15,
            profile + " observed CircuitJS steps stay within the declared maximum");
        require(sameStep(sim.minTimeStep, 50e-12) && sim.adjustTimeStep,
            profile + " keeps the shared minimum step and adaptive solver enabled");
        System.out.println("Q30_STEP_PROFILE seed=" + sim.sensitivitySeed +
            " fault=" + sim.sensitivityFault + " profile=" + profile +
            " boundary=" + (entering ? "BEGIN" : "END") +
            " stepRole=" + (sameStep(sim.sensitivityMaximumStep, PRODUCTION_STEP) ?
                "PRODUCTION_5_US" : "FINER_2_5_US_REFERENCE") +
            " declaredMaximumStepSeconds=" + format(sim.sensitivityMaximumStep) +
            " configuredMaximumStepSeconds=" + format(sim.maxTimeStep) +
            " actualCurrentStepSeconds=" + format(sim.timeStep) +
            " observedMinimumStepSeconds=" + format(sim.observedMinimumStep) +
            " observedMaximumStepSeconds=" + format(sim.observedMaximumStep) +
            " minimumTimeStepSeconds=" + format(sim.minTimeStep) +
            " adaptive=" + sim.adjustTimeStep);
        if (!entering) sim.endStepObservation();
    }

    private static boolean sameStep(double a, double b) {
        return Math.abs(a - b) <= Math.max(1e-18, Math.abs(b) * 1e-12);
    }

    private static void powerOn(CirSim sim, GeneratedBoardInstance owner,
            PhysicalBoardRuntime runtime, BoardModificationController modifications) {
        if (sim.boardPowerController.getState() != BoardPowerState.POWERED) {
            sim.boardPowerController.setState(BoardPowerState.POWERED);
            runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);
        }
        settleElectrical(sim, "service-power-on-reconnect");
        verifySettledOwner(sim, owner, modifications);
    }

    private static void settleMutation(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, PhysicalBoardRuntime runtime,
            String phase) {
        require(sim.generatedBoardVerificationPending && sim.analyzeFlag,
            "real physical mutation schedules solver and board verification");
        settleElectrical(sim, phase);
        verifySettledOwner(sim, owner, modifications);
        owner.getConnectionBindings().validateAgainst(owner.getBoard(),
            owner.getSimulationElements(), owner.getComponentBindings(),
            owner.getExternalPowerBindings(), owner.getFaultBinding());
        require(!runtime.isMutationInProgress() && !runtime.isMutationQuarantined(),
            "production physical mutation completes without quarantine");
    }

    private static void verifySettledOwner(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications) {
        GeneratedRuntimeInvariant.verify(owner, modifications, sim.elmList);
        GeneratedBoardVerifier.verify(owner, sim.boardPowerController.getState(),
            modifications, sim.elmList, false);
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
    }

    private static void settleElectrical(CirSim sim, String phase) {
        SensitivityCirSim sensitivitySim = (SensitivityCirSim) sim;
        double startTime = sim.t;
        try {
            sim.analyzeCircuit();
        } catch (RuntimeException failure) {
            sensitivitySim.reportSettleFailure(phase, "analyzeCircuit", 0, startTime, failure);
            throw failure;
        }
        try {
            sim.solverExecutor.advanceSteps(8);
        } catch (RuntimeException failure) {
            sensitivitySim.reportSettleFailure(phase, "advanceSteps(8)", 8,
                startTime, failure);
            throw failure;
        }
    }

    private static boolean disposeAll(Vector<OwnerResource> resources) {
        boolean clean = true;
        for (OwnerResource resource : resources) {
            if (resource.cleanupVerified) continue;
            try {
                if (resource.started)
                    cleanupActiveOwner(resource, resource.ownerSim);
                else cleanupUnusedOwner(resource);
            } catch (RuntimeException failure) {
                clean = false;
                System.out.println("Q30_STEP_CLEANUP_FAILURE seed=" +
                    resource.owner.getSeed() + " fault=" +
                    resource.owner.getFaultBinding().getFault().getId() +
                    " error=" + failure.getClass().getName() + ":" +
                    String.valueOf(failure.getMessage()));
            }
            clean &= resource.cleanupVerified;
        }
        return clean;
    }

    private static void cleanupUnusedOwner(OwnerResource resource) {
        long started = System.nanoTime();
        GeneratedBoardInstance owner = resource.owner;
        SensitivityCirSim sim = resource.ownerSim;
        CirSim priorGlobalSim = CircuitElm.sim;
        owner.getExternalPowerBindings().setConnected(false);
        owner.getPhysicalBoardRuntime().clearMutationProviders();
        if (!owner.getExternalPowerBindings().areAllDisconnected())
            throw new IllegalStateException("Unused Q30 owner sources stayed connected");
        Set<CircuitElm> elements = collectOwnerElements(owner, null);
        try {
            CircuitElm.sim = sim;
            for (CircuitElm element : elements) element.delete();
        } finally {
            CircuitElm.sim = priorGlobalSim;
        }
        resource.cleanupMillis = elapsedMillis(started);
        resource.cleanupVerified = true;
        printCleanup(resource);
    }

    private static void cleanupActiveOwner(OwnerResource resource,
            SensitivityCirSim sim) {
        CirSim priorGlobalSim = CircuitElm.sim;
        CircuitElm.sim = sim;
        try {
            cleanupActiveOwnerOnBoundSimulator(resource, sim);
        } finally {
            CircuitElm.sim = priorGlobalSim;
        }
    }

    private static void cleanupActiveOwnerOnBoundSimulator(OwnerResource resource,
            SensitivityCirSim sim) {
        long started = System.nanoTime();
        GeneratedBoardInstance owner = resource.owner;
        if (resource.cleanupVerified) return;
        if (sim.generatedBoardInstance != null && sim.generatedBoardInstance != owner)
            throw new IllegalStateException("Refusing to clear a successor Q30 owner");

        if (sim.boardPowerController.getBindingsForDeveloperVerification() != null) {
            sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
            if (sim.generatedBoardInstance == owner)
                owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(
                    BoardPowerState.UNPOWERED);
        }
        owner.getExternalPowerBindings().setConnected(false);
        if (!owner.getExternalPowerBindings().areAllDisconnected())
            throw new IllegalStateException("Q30 owner external sources remain connected");
        if (sim.boardPowerController.getBindingsForDeveloperVerification() != null &&
                !sim.boardPowerController.isElectricallyUnpowered())
            throw new IllegalStateException("Q30 board power controller did not disconnect");

        owner.getPhysicalBoardRuntime().clearMutationProviders();
        if (owner.getPhysicalBoardRuntime().isMutationInProgress() ||
                owner.getPhysicalBoardRuntime().isMutationQuarantined())
            throw new IllegalStateException("Q30 runtime mutation remains active at cleanup");

        Set<CircuitElm> elements = collectOwnerElements(owner, sim);
        sim.solverExecutor.invalidate();
        sim.capture = null;
        sim.activeMeasurementOverlay = false;
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
        sim.generatedChallengeController = null;
        sim.boardModificationController = null;
        sim.generatedBoardInstance = null;
        sim.elmList = new Vector<CircuitElm>();
        sim.adjustables = new Vector<Adjustable>();
        sim.boardPowerController.detach();

        // Keep the owner simulator bound while its own graph elements are deleted.
        for (CircuitElm element : elements) element.delete();
        if (sim.generatedBoardInstance != null || sim.elmList == null ||
                !sim.elmList.isEmpty() || sim.boardModificationController != null ||
                sim.generatedChallengeController != null ||
                sim.boardPowerController.getBindingsForDeveloperVerification() != null ||
                !owner.getExternalPowerBindings().areAllDisconnected())
            throw new IllegalStateException("Q30 native graph or power remained attached");
        resource.cleanupMillis = elapsedMillis(started);
        resource.cleanupVerified = true;
        printCleanup(resource);
    }

    private static Set<CircuitElm> collectOwnerElements(GeneratedBoardInstance owner,
            CirSim sim) {
        Set<CircuitElm> elements = new HashSet<CircuitElm>(owner.getSimulationElements());
        if (sim != null && sim.elmList != null) elements.addAll(sim.elmList);
        for (PhysicalPart<?> part : owner.getPhysicalBoardRuntime().getPhysicalParts()) {
            PhysicalPartElectricalBacking backing = part.getElectricalBacking();
            if (backing != null) elements.addAll(backing.getCircuitElements());
        }
        return elements;
    }

    private static void printCleanup(OwnerResource resource) {
        System.out.println("Q30_STEP_OWNER_CLEANUP seed=" + resource.owner.getSeed() +
            " fault=" + resource.owner.getFaultBinding().getFault().getId() +
            " declaredMaximumStepSeconds=" + format(resource.solverStep) +
            " stepRole=" + (sameStep(resource.solverStep, PRODUCTION_STEP) ?
                "PRODUCTION_5_US" : "FINER_2_5_US_REFERENCE") +
            " cleanup=true" +
            " elapsedMillis=" + resource.cleanupMillis);
    }

    private static Map<String, CircuitMeasurementEndpoint> capturePadEndpoints(
            GeneratedBoardInstance owner) {
        Map<String, CircuitMeasurementEndpoint> endpoints =
            new HashMap<String, CircuitMeasurementEndpoint>();
        for (String pad : owner.getBoard().getPadIds())
            endpoints.put(pad, owner.getSimulationBindings().getEndpoint(pad));
        return endpoints;
    }

    private static void verifyPadEndpoints(GeneratedBoardInstance owner,
            Map<String, CircuitMeasurementEndpoint> endpoints) {
        for (String pad : owner.getBoard().getPadIds())
            require(endpoints.get(pad) == owner.getSimulationBindings().getEndpoint(pad),
                "stable board-probe endpoint survives service: " + pad);
    }

    private static Delta compare(RunResult reference, RunResult candidate, String fault) {
        String[] phases = { "healthy", "faulted", "wrong-original-retest", "customer-retest" };
        double[][] left = { reference.healthy, reference.faulted,
            reference.wrongOriginal, reference.repaired };
        double[][] right = { candidate.healthy, candidate.faulted,
            candidate.wrongOriginal, candidate.repaired };
        Delta maximum = new Delta();
        for (int phase = 0; phase < phases.length; phase++) {
            require(left[phase].length == 37 && right[phase].length == 37,
                phases[phase] + " contains exactly 37 real D01 readings");
            for (int index = 0; index < 37; index++) {
                double a = left[phase][index], b = right[phase][index];
                require(finite(a) && finite(b), "finite paired " + phases[phase] +
                    " sample " + index + " for " + fault);
                double tolerance = ABSOLUTE_TOLERANCE + RELATIVE_TOLERANCE *
                    Math.max(Math.abs(a), Math.abs(b));
                double delta = Math.abs(a - b);
                require(delta <= tolerance,
                    "2.5 us reference and 5 us production candidate agree within 0.01 V + 1% at " +
                    phases[phase] + "/" + sampleName(index) + " delta=" + delta +
                    " tolerance=" + tolerance + " fault=" + fault);
                if (delta > maximum.delta) {
                    maximum.delta = delta;
                    maximum.tolerance = tolerance;
                    maximum.phase = phases[phase];
                    maximum.sample = sampleName(index);
                }
            }
        }
        return maximum;
    }

    private static void verifyTruth(double[] readings, int expectedMask,
            String phase, String fault) {
        require(readings.length == 37, phase + " retains all 37 diagnostic readings");
        int actualMask = 0;
        for (int condition = 0; condition < 4; condition++) {
            int offset = condition * 9;
            double rail = readings[offset + 2];
            require(finite(rail), phase + " has a finite measured 5 V rail at input " +
                condition + " fault=" + fault);
            if ("healthy".equals(phase) || "customer-retest".equals(phase))
                require(rail >= 4.75 && rail <= 5.25,
                    phase + " measured 5 V rail is in bounds at input " + condition +
                    " fault=" + fault);
            double a = readings[offset + 7], b = readings[offset + 8];
            if (!matches(a, EXPECTED_OUTPUTS[condition][0])) actualMask |= 1;
            if (!matches(b, EXPECTED_OUTPUTS[condition][1])) actualMask |= 2;
        }
        require(actualMask == expectedMask, phase + " has independently expected output mask " +
            expectedMask + " (actual " + actualMask + ") for " + fault);
        require(matches(readings[36], true), phase + " retains a real main 12 V reading");
        for (int index = 0; index < readings.length; index++)
            require(finite(readings[index]), phase + " has finite real sample " +
                sampleName(index) + " fault=" + fault);
    }

    private static String sampleName(int index) {
        return index == 36 ? SAMPLE_NAMES[9] :
            INPUT_OPERATIONS[index / 9] + "/" + SAMPLE_NAMES[index % 9];
    }

    private static boolean matches(double volts, boolean on) {
        if (!finite(volts)) return false;
        return on ? volts >= 10.8 && volts <= 12.6 : Math.abs(volts) <= .05;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1000000L;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.9g", value);
    }

    private static final class Capture {
        private final GeneratedBoardInstance owner;
        private final Rb30Behavior behavior;
        private String phase;
        private int capturedConditions;
        private final double[] readings = new double[37];

        Capture(GeneratedBoardInstance owner, Rb30Behavior behavior) {
            this.owner = owner;
            this.behavior = behavior;
        }

        void begin(String name) {
            if (phase != null) throw new IllegalStateException("Nested Q30 sample capture");
            phase = name;
            capturedConditions = 0;
        }

        void afterAdvance(SensitivityCirSim sim, double seconds) {
            if (phase == null || Math.abs(seconds - SAMPLE_SECONDS) > 1e-12 ||
                    capturedConditions >= 4) return;
            int expectedInput = capturedConditions;
            require(behavior.getInputs() == expectedInput,
                phase + " captures the actual requested condition " + expectedInput);
            int offset = capturedConditions * 9;
            for (int sample = 0; sample < 9; sample++)
                readings[offset + sample] = Rb30Behavior.voltage(owner,
                    SAMPLE_PAIRS[sample][0], SAMPLE_PAIRS[sample][1]);
            capturedConditions++;
        }

        double[] finish(String expectedPhase) {
            if (phase == null || !phase.equals(expectedPhase))
                throw new IllegalStateException("Unexpected Q30 sample phase");
            require(capturedConditions == 4,
                phase + " captures four distinct real 30 ms input settlements");
            readings[36] = Rb30Behavior.voltage(owner,
                SAMPLE_PAIRS[9][0], SAMPLE_PAIRS[9][1]);
            phase = null;
            return readings.clone();
        }
    }

    private static final class SensitivityCirSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        Capture capture;
        long sensitivitySeed;
        String sensitivityFault;
        double sensitivityMaximumStep;
        String observedProfile;
        double observedMinimumStep = Double.POSITIVE_INFINITY;
        double observedMaximumStep;
        RuntimeException stampFailure;

        void beginStepObservation(String profile) {
            if (observedProfile != null)
                throw new IllegalStateException("Nested Q30 step profile " + observedProfile);
            observedProfile = profile;
            observedMinimumStep = Double.POSITIVE_INFINITY;
            observedMaximumStep = 0;
            observeCurrentStep();
        }

        void observeCurrentStep() {
            if (observedProfile == null || !finite(timeStep) || timeStep <= 0) return;
            observedMinimumStep = Math.min(observedMinimumStep, timeStep);
            observedMaximumStep = Math.max(observedMaximumStep, timeStep);
        }

        void endStepObservation() {
            observeCurrentStep();
            observedProfile = null;
        }

        @Override void stampCircuit() {
            observeCurrentStep();
            try {
                super.stampCircuit();
            } catch (RuntimeException failure) {
                stampFailure = failure;
                System.out.println("Q30_STEP_STAMP_FAILURE seed=" + sensitivitySeed +
                    " fault=" + sensitivityFault +
                    " declaredMaximumStepSeconds=" + sensitivityMaximumStep +
                    " exception=" + failure.getClass().getName() +
                    " message=" + failure.getMessage());
                reportStepFailure("stamp", Double.NaN, failure);
                failure.printStackTrace(System.out);
                throw failure;
            }
        }

        @Override void advanceGeneratedTemporalProfile(double durationSeconds) {
            observeCurrentStep();
            try {
                super.advanceGeneratedTemporalProfile(durationSeconds);
            } catch (RuntimeException failure) {
                reportStepFailure("temporal-advance", durationSeconds, failure);
                throw failure;
            } finally {
                observeCurrentStep();
            }
            if (capture != null) capture.afterAdvance(this, durationSeconds);
        }

        void reportSettleFailure(String operationPhase, String stage, int requestedSteps,
                double startTime, RuntimeException failure) {
            reportStepFailure(operationPhase, stage, Double.NaN, requestedSteps,
                startTime, failure);
        }

        private void reportStepFailure(String stage, double durationSeconds,
                RuntimeException failure) {
            reportStepFailure(null, stage, durationSeconds, -1, Double.NaN, failure);
        }

        private void reportStepFailure(String operationPhase, String stage,
                double durationSeconds, int requestedSteps, double startTime,
                RuntimeException failure) {
            double inputToReturn = Double.NaN;
            double inputToMainReturn = Double.NaN;
            double outputToReturn = Double.NaN;
            double outputToMainReturn = Double.NaN;
            double regulatorReturnToMainReturn = Double.NaN;
            double mainSupplyToMainReturn = Double.NaN;
            double outputAToOutputAReturn = Double.NaN;
            double outputBToOutputBReturn = Double.NaN;
            double modelInput = Double.NaN;
            double contractMaximumInput = Double.NaN;
            double sensorACommand = Double.NaN;
            double sensorBCommand = Double.NaN;
            int inputCommands = -1;
            StringBuilder diagnosticFailures = new StringBuilder();
            try {
                GeneratedBoardInstance owner = getGeneratedBoardInstance();
                if (owner != null) {
                    inputToReturn = diagnosticVoltage(owner, "U1.INPUT",
                        "U1.RETURN", diagnosticFailures);
                    inputToMainReturn = diagnosticVoltage(owner, "U1.INPUT",
                        "J1.2", diagnosticFailures);
                    outputToReturn = diagnosticVoltage(owner, "U1.OUTPUT",
                        "U1.RETURN", diagnosticFailures);
                    outputToMainReturn = diagnosticVoltage(owner, "U1.OUTPUT",
                        "J1.2", diagnosticFailures);
                    regulatorReturnToMainReturn = diagnosticVoltage(owner,
                        "U1.RETURN", "J1.2", diagnosticFailures);
                    mainSupplyToMainReturn = diagnosticVoltage(owner, "J1.1",
                        "J1.2", diagnosticFailures);
                    outputAToOutputAReturn = diagnosticVoltage(owner, "JOA.1",
                        "JOA.2", diagnosticFailures);
                    outputBToOutputBReturn = diagnosticVoltage(owner, "JOB.1",
                        "JOB.2", diagnosticFailures);
                    try {
                        if (owner.getFamilyState() instanceof Rb30Behavior)
                            inputCommands = ((Rb30Behavior) owner.getFamilyState()).getInputs();
                        sensorACommand = diagnosticSensorCommand(owner, "SENSOR_A",
                            diagnosticFailures);
                        sensorBCommand = diagnosticSensorCommand(owner, "SENSOR_B",
                            diagnosticFailures);
                        PhysicalPart<?> regulatorPart = owner.getPhysicalBoardRuntime()
                            .getInstalledPart("U1");
                        if (regulatorPart != null &&
                                regulatorPart.getElectricalBacking() != null)
                            for (CircuitElm element : regulatorPart.getElectricalBacking()
                                    .getCircuitElements())
                                if (element instanceof AbstractRailRegulatorElm) {
                                    AbstractRailRegulatorElm regulator =
                                        (AbstractRailRegulatorElm) element;
                                    modelInput = regulator.getInputVoltage();
                                    contractMaximumInput = regulator.getContract()
                                        .getMaximumInputVolts();
                                    break;
                                }
                    } catch (RuntimeException failureDuringContractRead) {
                        appendDiagnosticFailure(diagnosticFailures,
                            failureDuringContractRead);
                    }
                }
            } catch (RuntimeException failureDuringDiagnostics) {
                appendDiagnosticFailure(diagnosticFailures, failureDuringDiagnostics);
            }
            String phase = operationPhase == null ?
                (observedProfile == null ? "NONE" : observedProfile) : operationPhase;
            String capturePhase = capture == null || capture.phase == null ? "NONE" : capture.phase;
            String profile = observedProfile == null ? "NONE" : observedProfile;
            System.out.println("Q30_STEP_FAILURE seed=" + sensitivitySeed +
                " fault=" + sensitivityFault + " stage=" + stage +
                " phase=" + phase + " capturePhase=" + capturePhase +
                " profile=" + profile +
                " requestedSteps=" + requestedSteps +
                " simulationTimeStartSeconds=" + format(startTime) +
                " simulationTimeSeconds=" + format(t) +
                " simulationTimeElapsedSeconds=" +
                    (finite(startTime) ? format(t - startTime) : "not-applicable") +
                " durationSeconds=" + format(durationSeconds) +
                " declaredMaximumStepSeconds=" + format(sensitivityMaximumStep) +
                " configuredMaximumStepSeconds=" + format(maxTimeStep) +
                " actualCurrentStepSeconds=" + format(timeStep) +
                " configuredMinimumStepSeconds=" + format(minTimeStep) +
                " adaptive=" + adjustTimeStep +
                " commandedInputs=" + inputCommands +
                " SENSOR_A_maxVoltage_V=" + format(sensorACommand) +
                " SENSOR_B_maxVoltage_V=" + format(sensorBCommand) +
                " U1_INPUT_to_U1_RETURN_V=" + format(inputToReturn) +
                " U1_INPUT_to_J1_2_V=" + format(inputToMainReturn) +
                " U1_OUTPUT_to_U1_RETURN_V=" + format(outputToReturn) +
                " U1_OUTPUT_to_J1_2_V=" + format(outputToMainReturn) +
                " U1_RETURN_to_J1_2_V=" + format(regulatorReturnToMainReturn) +
                " J1_1_to_J1_2_V=" + format(mainSupplyToMainReturn) +
                " JOA_1_to_JOA_2_V=" + format(outputAToOutputAReturn) +
                " JOB_1_to_JOB_2_V=" + format(outputBToOutputBReturn) +
                " regulatorModelInput_V=" + format(modelInput) +
                " E02_maximumInput_V=" + format(contractMaximumInput) +
                " stopMessage=" + (stopMessage == null ? "none" : stopMessage) +
                " diagnosticFailure=" + (diagnosticFailures.length() == 0 ?
                    "none" : diagnosticFailures.toString()) +
                " originalException=" + failure.getClass().getName() + ":" +
                String.valueOf(failure.getMessage()));
        }

        private double diagnosticSensorCommand(GeneratedBoardInstance owner,
                String inputId, StringBuilder failures) {
            try {
                ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings()
                    .getBinding(inputId);
                LimitedDcSupplyElm supply = binding.getLimitedSupply();
                if (supply == null)
                    throw new IllegalStateException("Missing controlled source for " + inputId);
                return supply.maxVoltage;
            } catch (RuntimeException failure) {
                appendDiagnosticFailure(failures, failure);
                return Double.NaN;
            }
        }

        private double diagnosticVoltage(GeneratedBoardInstance owner,
                String positive, String negative, StringBuilder failures) {
            try {
                return Rb30Behavior.voltage(owner, positive, negative);
            } catch (RuntimeException failure) {
                appendDiagnosticFailure(failures, failure);
                return Double.NaN;
            }
        }

        private void appendDiagnosticFailure(StringBuilder failures,
                RuntimeException failure) {
            if (failures.length() != 0) failures.append(';');
            failures.append(failure.getClass().getName()).append(':')
                .append(String.valueOf(failure.getMessage()));
        }
    }

    private static final class OwnerResource {
        final GeneratedBoardInstance owner;
        final SensitivityCirSim ownerSim;
        double solverStep;
        long cleanupMillis;
        boolean started;
        boolean cleanupVerified;

        OwnerResource(GeneratedBoardInstance owner, SensitivityCirSim ownerSim) {
            if (owner == null || ownerSim == null)
                throw new IllegalArgumentException("Missing Q30 owner or simulator");
            this.owner = owner;
            this.ownerSim = ownerSim;
            this.solverStep = ownerSim.sensitivityMaximumStep;
        }
    }

    private static final class RunResult {
        final double[] healthy, faulted, wrongOriginal, repaired;
        final long elapsedMillis;
        final double simulatedSeconds;

        RunResult(double[] healthy, double[] faulted, double[] wrongOriginal,
                double[] repaired, long elapsedMillis, double simulatedSeconds) {
            this.healthy = healthy;
            this.faulted = faulted;
            this.wrongOriginal = wrongOriginal;
            this.repaired = repaired;
            this.elapsedMillis = elapsedMillis;
            this.simulatedSeconds = simulatedSeconds;
        }
    }

    private static final class Delta {
        double delta, tolerance;
        String phase = "none", sample = "none";
    }
}
