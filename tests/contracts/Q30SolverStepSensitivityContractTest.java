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
    private static final double SERVICE_WAIT_FINAL_REQUEST_CUSHION_SECONDS = 2e-12;
    private static final int MAX_RELAY_DISCHARGE_STEPS = 100;
    private static final double RELATIVE_TOLERANCE = .01;
    private static final double ABSOLUTE_TOLERANCE = .01;
    private static final String[][] COMMON_FAULTS = {
        { "DREV_OPEN", "DIODE_OPEN", "DREV", "3" },
        { "REN_OPEN", "RESISTOR_OPEN", "REN", "3" },
        { "SENSOR_A_OPEN", "RESISTOR_OPEN", "RSA", "1" },
        { "DRIVE_A_OPEN", "RESISTOR_OPEN", "RDA", "1" }
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
                    "DREV_OPEN|REN_OPEN|SENSOR_A_OPEN|DRIVE_A_OPEN|RELAY_A_COIL_OPEN|RELAY_B_COIL_OPEN]");
            seed = Long.parseLong(args[1]);
            if (!Long.toString(seed).equals(args[1]))
                throw new IllegalArgumentException("Noncanonical Q30 sensitivity seed: " + args[1]);
            if (args.length == 4) {
                selectedFault = args[3];
                faultIndex(selectedFault, faultsFor(Rb30Plan.resolve(seed)));
            }
        }
        Rb30Plan plan = Rb30Plan.resolve(seed);
        String[][] faults = faultsFor(plan);
        int cases = 0;
        verifySeed(seed, selectedFault);
        cases = selectedFault == null ? faults.length : 1;
        System.out.println("PASS: Q30 production solver step sensitivity assertions=" + assertions +
            " seed=" + seed + " support=" + plan.supportIdentity() +
            " topology=" + plan.topology() + " canonicalPlan=" + plan.canonical() +
            " faults=" + cases + " referenceMaximumStepSeconds=" + format(REFERENCE_STEP) +
            " productionCandidateMaximumStepSeconds=" +
            format(CANDIDATE_STEP) + " candidateKind=PRODUCTION_5_US" +
            " elapsedMillis=" + elapsedMillis(started));
    }

    private static int faultIndex(String faultId, String[][] faults) {
        for (int index = 0; index < faults.length; index++)
            if (faults[index][0].equals(faultId)) return index;
        throw new IllegalArgumentException("Unsupported Q30 sensitivity fault: " + faultId);
    }

    private static String[][] faultsFor(Rb30Plan plan) {
        int allChannels = (1 << plan.channelCount) - 1;
        String relay = plan.channelCount == 1 ? "KA" : "KB";
        String relayFault = plan.channelCount == 1 ?
            "RELAY_A_COIL_OPEN" : "RELAY_B_COIL_OPEN";
        String[][] result = new String[5][4];
        for (int index = 0; index < COMMON_FAULTS.length; index++)
            result[index] = COMMON_FAULTS[index].clone();
        result[0][3] = Integer.toString(allChannels);
        result[1][3] = Integer.toString(allChannels);
        result[4] = new String[] { relayFault, "RELAY_COIL_OPEN", relay,
            Integer.toString(1 << (plan.channelCount - 1)) };
        return result;
    }

    private static String[] inputOperations(Rb30Plan plan) {
        return plan.channelCount == 1 ? new String[] {
            Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_HIGH } : new String[] {
            Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_A_ONLY,
            Rb30Behavior.SENSORS_B_ONLY, Rb30Behavior.SENSORS_HIGH };
    }

    private static int[] inputMasks(Rb30Plan plan) {
        int[] masks = new int[1 << plan.channelCount];
        for (int index = 0; index < masks.length; index++) masks[index] = index;
        return masks;
    }

    private static String[][] samplePairs(Rb30Plan plan) {
        int channelCount = plan.channelCount;
        String[][] pairs = new String[7 + channelCount + 1][2];
        String relay = channelCount == 1 ? "KA" : "KB";
        String[] fixed = {
            "DREV.K", "REN.2", "U1.OUTPUT", "RSA.2", "RDA.1", "RDA.2",
            relay + ".A2"
        };
        for (int index = 0; index < fixed.length; index++) {
            pairs[index][0] = fixed[index];
            pairs[index][1] = "J1.2";
        }
        int output = 7;
        for (String channel : plan.channels()) {
            pairs[output][0] = "JO" + channel + ".1";
            pairs[output++][1] = "JO" + channel + ".2";
        }
        pairs[output][0] = "J1.1";
        pairs[output][1] = "J1.2";
        return pairs;
    }

    private static String[] sampleNames(Rb30Plan plan) {
        String[][] pairs = samplePairs(plan);
        String[] names = new String[pairs.length];
        for (int index = 0; index < pairs.length; index++)
            names[index] = pairs[index][0] + "-" + pairs[index][1];
        names[names.length - 1] = "MAIN_12V";
        return names;
    }

    private static boolean outputOn(int inputMask, int channelIndex) {
        return (inputMask & (1 << channelIndex)) != 0;
    }

    private static int observationCount(Rb30Plan plan) {
        return (7 + plan.channelCount) * (1 << plan.channelCount) + 1;
    }

    private static void verifySeed(long seed, String selectedFault) {
        long seedStarted = System.nanoTime();
        CirSim priorGlobalSim = CircuitElm.sim;
        Rb30Plan plan = Rb30Plan.resolve(seed);
        String[][] faults = faultsFor(plan);
        Vector<OwnerResource> resources = new Vector<OwnerResource>();
        boolean cleanupVerified = false;
        long seedCleanupMillis = 0;
        try {
            // Route exactly once through the accepted medium policy. The
            // routed DREV owner is also the first fresh solver owner; all
            // other owners receive copies of that sealed accepted layout.
            SensitivityCirSim routedSim = newSensitivitySim(seed, faults[0][0],
                REFERENCE_STEP);
            CircuitElm.sim = routedSim;
            GeneratedFault firstFault = new GeneratedFault(faults[0][0],
                GeneratedFaultType.valueOf(faults[0][1]), faults[0][2],
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
            int from = selectedFault == null ? 0 : faultIndex(selectedFault, faults);
            int to = selectedFault == null ? faults.length : from + 1;
            for (int fault = from; fault < to; fault++) {
                String faultId = faults[fault][0];
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

                RunResult reference = runOwner(plan, faults, fault, referenceOwner,
                    REFERENCE_STEP);
                RunResult candidate = runOwner(plan, faults, fault, candidateOwner,
                    CANDIDATE_STEP);
                require(referenceOwner.cleanupVerified && candidateOwner.cleanupVerified,
                    "both paired owners are disconnected and deleted before comparison");
                Delta maximum = compare(plan, reference, candidate, faultId);
                System.out.println("Q30_STEP_PAIR seed=" + seed + " support=" +
                    plan.supportIdentity() + " topology=" + plan.topology() +
                    " canonicalPlan=" + plan.canonical() + " fault=" + faultId +
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
                plan.supportIdentity() + " topology=" + plan.topology() +
                " canonicalPlan=" + plan.canonical() +
                " referenceMaximumStepSeconds=" +
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

    private static RunResult runOwner(Rb30Plan plan, String[][] faults,
            int faultIndex, OwnerResource resource, double maxStep) {
        long seed = plan.seed;
        String faultId = faults[faultIndex][0];
        int expectedFaultMask = Integer.parseInt(faults[faultIndex][3]);
        String[] inputOperations = inputOperations(plan);
        int[] inputMasks = inputMasks(plan);
        int samplesPerCondition = 7 + plan.channelCount;
        int conditionCount = 1 << plan.channelCount;
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
        Capture capture = new Capture(owner, behavior, plan);
        sim.capture = capture;
        owner.getFaultBinding().setApplied(false);
        recordStepProfileBoundary(sim, "initial-electrical-settle", true);
        settleElectrical(sim, "initial-electrical-settle");
        recordStepProfileBoundary(sim, "initial-electrical-settle", false);
        verifySupportPresence(owner, plan);
        sampleFused12(owner, "powered-before-service");

        capture.begin("healthy");
        recordStepProfileBoundary(sim, "healthy-preparation", true);
        if (maxStep == PRODUCTION_STEP) {
            behavior.prepareHealthyProfile(sim, owner);
        } else {
            for (int condition = 0; condition < conditionCount; condition++) {
                behavior.setInputs(sim, owner, inputMasks[condition]);
                require(behavior.healthy(owner, inputMasks[condition]),
                    format(maxStep * 1e6) + " us finer-reference healthy condition " +
                    condition + " passes for " + faultId);
            }
            behavior.setInputs(sim, owner, 0);
        }
        recordStepProfileBoundary(sim, "healthy-preparation", false);
        double[] healthy = capture.finish("healthy");
        require(behavior.getInputs() == 0 && behavior.healthy(owner, 0),
            "healthy preparation reaches real LOW before fault application");
        verifyTruth(healthy, 0, "healthy", faultId, plan);

        owner.getFaultBinding().setApplied(true);
        recordStepProfileBoundary(sim, "fault-profile-preparation", true);
        behavior.prepareFaultedProfile(sim, owner);
        recordStepProfileBoundary(sim, "fault-profile-preparation", false);
        require(behavior.getInputs() == conditionCount - 1 &&
                behavior.getObservedBehavior() == GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            "fault application produces a measured HIGH-condition symptom");
        recordStepProfileBoundary(sim, "faulted-channel-input-profile", true);
        capture.begin("faulted");
        int symptomMask = 0;
        for (int condition = 0; condition < conditionCount; condition++) {
            GeneratedCustomerRetestResult inputResult = owner.invokeOperation(
                inputOperations[condition], sim);
            require(inputResult == null, "real input operation " + inputOperations[condition]);
            int channelIndex = 0;
            for (String channel : plan.channels()) {
                double output = Rb30Behavior.voltage(owner,
                    "JO" + channel + ".1", "JO" + channel + ".2");
                if (!matches(output, outputOn(inputMasks[condition], channelIndex)))
                    symptomMask |= 1 << channelIndex;
                channelIndex++;
            }
        }
        double[] faulted = capture.finish("faulted");
        recordStepProfileBoundary(sim, "faulted-channel-input-profile", false);
        require(symptomMask == expectedFaultMask,
            "faulted channel-input truth mask matches independent Q30 oracle");
        verifyTruth(faulted, expectedFaultMask, "faulted", faultId, plan);

        sim.nativeProfilesReady = true;
        recordStepProfileBoundary(sim, "unrepaired-customer-retest", true);
        capture.begin("wrong-original-retest");
        GeneratedCustomerRetestResult unrepaired = owner.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        double[] wrongOriginal = capture.finish("wrong-original-retest");
        recordStepProfileBoundary(sim, "unrepaired-customer-retest", false);
        require(unrepaired != null && !unrepaired.isPassed(),
            "unrepaired actual active-channel customer retest fails");
        verifyTruth(wrongOriginal, expectedFaultMask,
            "wrong-original-retest", faultId, plan);

        String component = faults[faultIndex][2];
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
        verifyTruth(originalAfterReinstall, expectedFaultMask,
            "wrong-original-retest", faultId, plan);

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
        verifyTruth(repairedReadings, 0, "customer-retest", faultId, plan);
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
            sampleFused12(owner, "immediately-after-source-disconnect");
        }
        settleElectrical(sim, "service-power-off-disconnect");
        require(sim.boardPowerController.isElectricallyUnpowered(),
            "all external power is disconnected before service");
        double waitStart = sim.t;
        StringBuilder waitProgress = new StringBuilder();
        double requestedSeconds = 0;
        for (int step = 0; step < SERVICE_WAIT_COUNT; step++) {
            double stepStart = sim.t;
            double deadline = waitStart + (step + 1) * SERVICE_WAIT_SECONDS;
            if (step == SERVICE_WAIT_COUNT - 1) {
                // Request 2 ps past the cumulative endpoint: twice the executor's
                // existing 1 ps completion tolerance, covering cumulative target
                // rounding without changing the acceptance epsilon or physics interval.
                deadline += SERVICE_WAIT_FINAL_REQUEST_CUSHION_SECONDS;
            }
            double request = deadline - sim.t;
            requestedSeconds += request;
            sim.advanceGeneratedTemporalProfile(request);
            if (step != 0) waitProgress.append(';');
            waitProgress.append("step=").append(step + 1)
                .append(" start=").append(preciseDouble(stepStart))
                .append(" target=").append(preciseDouble(deadline))
                .append(" requested=").append(preciseDouble(request))
                .append(" end=").append(preciseDouble(sim.t))
                .append(" advanced=").append(preciseDouble(sim.t - stepStart));
        }
        double elapsed = sim.t - waitStart;
        double waitUpperBound = .250 + SERVICE_WAIT_COUNT * sim.maxTimeStep + 1e-9;
        boolean waitElapsedInBounds = elapsed >= .250 - 1e-12 &&
            elapsed <= .250 + SERVICE_WAIT_COUNT * sim.maxTimeStep + 1e-9;
        String waitTiming = "start=" + preciseDouble(waitStart) +
            " end=" + preciseDouble(sim.t) + " elapsed=" + preciseDouble(elapsed) +
            " elapsedMinus250ms=" + preciseDouble(elapsed - .250) +
            " requestedSeconds=" + preciseDouble(requestedSeconds) +
            " nominalSeconds=" + preciseDouble(SERVICE_WAIT_COUNT * SERVICE_WAIT_SECONDS) +
            " finalRequestCushion=" +
                preciseDouble(SERVICE_WAIT_FINAL_REQUEST_CUSHION_SECONDS) +
            " lowerBound=" + preciseDouble(.250 - 1e-12) +
            " upperBound=" + preciseDouble(waitUpperBound) +
            " declaredMaximumStep=" + preciseDouble(sim.maxTimeStep) +
            " currentStep=" + preciseDouble(sim.timeStep) + " calls=[" + waitProgress + "]";
        System.out.println("Q30_SERVICE_WINDOW seed=" + sensitivitySim.sensitivitySeed +
            " fault=" + sensitivitySim.sensitivityFault + " role=" +
            (sameStep(sensitivitySim.sensitivityMaximumStep, PRODUCTION_STEP) ?
                "PRODUCTION_5_US" : "FINER_2_5_US_REFERENCE") + " " + waitTiming);
        require(waitElapsedInBounds,
            "CircuitJS advances at least the declared 250 ms power-off service guard: " +
                waitTiming);
        sampleFused12(owner, "after-250ms-service-window");
        String relay = owner.getFaultLocus().getComponentId();
        if ("KA".equals(relay) || "KB".equals(relay)) {
            if (!Rb30RelayService.isDischarged(owner, relay)) {
                boolean rejected = false;
                try { runtime.getMutationProvider(relay).removeInstalledPart(); }
                catch (BoardModificationRejectedException expected) { rejected = true; }
                require(rejected && runtime.getInstalledPart(relay) != null,
                    "residual target energy blocks the relay mutation after 250 ms");
                int extra = 0;
                while (!Rb30RelayService.isDischarged(owner, relay) &&
                        extra < MAX_RELAY_DISCHARGE_STEPS) {
                    sim.advanceGeneratedTemporalProfile(SERVICE_WAIT_SECONDS);
                    extra++;
                }
                require(Rb30RelayService.isDischarged(owner, relay),
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

    private static void verifySupportPresence(GeneratedBoardInstance owner,
            Rb30Plan plan) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        require((runtime.getInstalledPart("C12") != null) == plan.hasEntryCapacitor,
            "C12 physical presence matches the procedural support plan");
        PhysicalBoardRuntimeCapability capability = runtime.getCapability(
            PowerDomainRuntimeCapability.CAPABILITY_ID);
        require(capability instanceof PowerDomainContractProvider,
            "real Q30 power-domain observation contract is installed");
        if (capability instanceof PowerDomainContractProvider) {
            PowerDomainContract contract = ((PowerDomainContractProvider) capability).getContract();
            PowerDomainContract.Rail fused12 = contract.getRails().get("FUSED12");
            require(fused12 != null && fused12.getStorageRequirement() ==
                    PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED,
                "FUSED12 stays observation-required when optional C12 is absent");
        }
        for (String channel : plan.channels()) {
            require((runtime.getInstalledPart("LEDOUT_" + channel) != null) ==
                    plan.hasOutputIndicator(channel),
                "output indicator population follows plan flags for " + channel);
            if (plan.hasOutputIndicator(channel))
                require(Math.abs(Rb30Behavior.voltage(owner,
                        "LEDOUT_" + channel + ".K", "JLOAD.2")) <= .05,
                    "output LED cathode follows the real down-channel LOAD_RETURN for " + channel);
        }
    }

    private static void sampleFused12(GeneratedBoardInstance owner, String phase) {
        double value = Rb30Behavior.voltage(owner, "DREV.A", "J1.2");
        require(finite(value), "actual bound FUSED12 endpoint is finite at " + phase);
        System.out.println("Q30_STEP_FUSED12 seed=" + owner.getSeed() +
            " phase=" + phase + " c12=" +
            (owner.getPhysicalBoardRuntime().getInstalledPart("C12") != null) +
            " volts=" + format(value) + " storage=OBSERVATION_REQUIRED");
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

    private static Delta compare(Rb30Plan plan, RunResult reference,
            RunResult candidate, String fault) {
        int count = observationCount(plan);
        String[] phases = { "healthy", "faulted", "wrong-original-retest", "customer-retest" };
        double[][] left = { reference.healthy, reference.faulted,
            reference.wrongOriginal, reference.repaired };
        double[][] right = { candidate.healthy, candidate.faulted,
            candidate.wrongOriginal, candidate.repaired };
        Delta maximum = new Delta();
        for (int phase = 0; phase < phases.length; phase++) {
            require(left[phase].length == count && right[phase].length == count,
                phases[phase] + " contains exactly " + count +
                    " plan-shaped real D01 readings");
            for (int index = 0; index < count; index++) {
                double a = left[phase][index], b = right[phase][index];
                require(finite(a) && finite(b), "finite paired " + phases[phase] +
                    " sample " + index + " for " + fault);
                double tolerance = ABSOLUTE_TOLERANCE + RELATIVE_TOLERANCE *
                    Math.max(Math.abs(a), Math.abs(b));
                double delta = Math.abs(a - b);
                require(delta <= tolerance,
                    "2.5 us reference and 5 us production candidate agree within 0.01 V + 1% at " +
                    phases[phase] + "/" + sampleName(index, plan) + " delta=" + delta +
                    " tolerance=" + tolerance + " fault=" + fault);
                if (delta > maximum.delta) {
                    maximum.delta = delta;
                    maximum.tolerance = tolerance;
                    maximum.phase = phases[phase];
                    maximum.sample = sampleName(index, plan);
                }
            }
        }
        return maximum;
    }

    private static void verifyTruth(double[] readings, int expectedMask,
            String phase, String fault, Rb30Plan plan) {
        int channelCount = plan.channelCount;
        int stride = 7 + channelCount;
        int conditionCount = 1 << channelCount;
        int[] masks = inputMasks(plan);
        require(readings.length == observationCount(plan), phase +
            " retains all " + observationCount(plan) + " plan-shaped diagnostic readings");
        int actualMask = 0;
        for (int condition = 0; condition < conditionCount; condition++) {
            int offset = condition * stride;
            double rail = readings[offset + 2];
            require(finite(rail), phase + " has a finite measured 5 V rail at input " +
                condition + " fault=" + fault);
            if ("healthy".equals(phase) || "customer-retest".equals(phase))
                require(rail >= 4.75 && rail <= 5.25,
                    phase + " measured 5 V rail is in bounds at input " + condition +
                    " fault=" + fault);
            for (int channel = 0; channel < channelCount; channel++)
                if (!matches(readings[offset + 7 + channel],
                        outputOn(masks[condition], channel)))
                    actualMask |= 1 << channel;
        }
        require(actualMask == expectedMask, phase + " has independently expected output mask " +
            expectedMask + " (actual " + actualMask + ") for " + fault);
        if (channelCount == 2 && plan.referenceArrangement ==
                Rb30Plan.ReferenceArrangement.SHARED_DIRECT &&
                ("healthy".equals(phase) || "customer-retest".equals(phase))) {
            int aOnly = stride;
            int bOnly = stride * 2;
            require(matches(readings[aOnly + 7], true) &&
                    matches(readings[aOnly + 8], false) &&
                    matches(readings[bOnly + 7], false) &&
                    matches(readings[bOnly + 8], true),
                phase + " proves shared-direct A-only and B-only outputs remain uncoupled");
        }
        require(matches(readings[conditionCount * stride], true),
            phase + " retains a real main 12 V reading");
        for (int index = 0; index < readings.length; index++)
            require(finite(readings[index]), phase + " has finite real sample " +
                sampleName(index, plan) + " fault=" + fault);
    }

    private static String sampleName(int index, Rb30Plan plan) {
        String[] names = sampleNames(plan);
        int stride = 7 + plan.channelCount;
        int conditionCount = 1 << plan.channelCount;
        if (index == conditionCount * stride) return names[names.length - 1];
        return inputOperations(plan)[index / stride] + "/" + names[index % stride];
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

    private static String preciseDouble(double value) {
        return Double.toString(value);
    }

    private static final class Capture {
        private final GeneratedBoardInstance owner;
        private final Rb30Behavior behavior;
        private final Rb30Plan plan;
        private final String[][] pairs;
        private final int samplesPerCondition;
        private final int conditionCount;
        private String phase;
        private int capturedConditions;
        private final double[] readings;

        Capture(GeneratedBoardInstance owner, Rb30Behavior behavior, Rb30Plan plan) {
            this.owner = owner;
            this.behavior = behavior;
            this.plan = plan;
            pairs = samplePairs(plan);
            samplesPerCondition = 7 + plan.channelCount;
            conditionCount = 1 << plan.channelCount;
            readings = new double[observationCount(plan)];
        }

        void begin(String name) {
            if (phase != null) throw new IllegalStateException("Nested Q30 sample capture");
            phase = name;
            capturedConditions = 0;
        }

        void afterAdvance(SensitivityCirSim sim, double seconds) {
            if (phase == null || Math.abs(seconds - SAMPLE_SECONDS) > 1e-12 ||
                    capturedConditions >= conditionCount) return;
            int expectedInput = capturedConditions;
            require(behavior.getInputs() == expectedInput,
                phase + " captures the actual requested condition " + expectedInput);
            int offset = capturedConditions * samplesPerCondition;
            for (int sample = 0; sample < samplesPerCondition; sample++)
                readings[offset + sample] = Rb30Behavior.voltage(owner,
                    pairs[sample][0], pairs[sample][1]);
            capturedConditions++;
        }

        double[] finish(String expectedPhase) {
            if (phase == null || !phase.equals(expectedPhase))
                throw new IllegalStateException("Unexpected Q30 sample phase");
            require(capturedConditions == conditionCount,
                phase + " captures " + conditionCount +
                    " distinct real 30 ms input settlements");
            readings[conditionCount * samplesPerCondition] = Rb30Behavior.voltage(owner,
                pairs[pairs.length - 1][0], pairs[pairs.length - 1][1]);
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
            boolean hasChannelB = false;
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
                    hasChannelB = Rb30Plan.resolve(owner.getSeed()).channelCount == 2;
                    if (hasChannelB)
                        outputBToOutputBReturn = diagnosticVoltage(owner, "JOB.1",
                            "JOB.2", diagnosticFailures);
                    try {
                        if (owner.getFamilyState() instanceof Rb30Behavior)
                            inputCommands = ((Rb30Behavior) owner.getFamilyState()).getInputs();
                        sensorACommand = diagnosticSensorCommand(owner, "SENSOR_A",
                            diagnosticFailures);
                        if (hasChannelB)
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
                " SENSOR_B_maxVoltage_V=" +
                    (hasChannelB ?
                        format(sensorBCommand) : "not-applicable") +
                " U1_INPUT_to_U1_RETURN_V=" + format(inputToReturn) +
                " U1_INPUT_to_J1_2_V=" + format(inputToMainReturn) +
                " U1_OUTPUT_to_U1_RETURN_V=" + format(outputToReturn) +
                " U1_OUTPUT_to_J1_2_V=" + format(outputToMainReturn) +
                " U1_RETURN_to_J1_2_V=" + format(regulatorReturnToMainReturn) +
                " J1_1_to_J1_2_V=" + format(mainSupplyToMainReturn) +
                " JOA_1_to_JOA_2_V=" + format(outputAToOutputAReturn) +
                " JOB_1_to_JOB_2_V=" +
                    (hasChannelB ?
                        format(outputBToOutputBReturn) : "not-applicable") +
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
