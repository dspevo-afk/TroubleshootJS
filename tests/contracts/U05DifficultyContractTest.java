package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Synthetic information fixtures are mathematical falsifiers, never solver or human trial evidence. */
public final class U05DifficultyContractTest {
    private static int assertions;
    public static void main(String[] args) {
        GeneratedDiagnosticExecutionTrace.Builder trace = GeneratedDiagnosticExecutionTrace.builder();
        trace.recordCompletedSemanticAction(); trace.recordCompletedSemanticAction();
        trace.recordRepairAction(WorkbenchOperation.REMOVE); trace.recordRepairAction(WorkbenchOperation.REMOVE);
        GeneratedDiagnosticRepairSemantics semantics = GeneratedDiagnosticRepairSemantics.forServiceability(
            new GeneratedFaultServiceability(GeneratedFaultLocus.componentInternal("R1"), new String[0],
                new String[0], new String[] {WorkbenchOperation.CATALOG_INSTALL}, GeneratedBoardOperationIds.CUSTOMER_RETEST));
        GeneratedDiagnosticExecutionTrace frozen = trace.freeze(semantics);
        check(frozen.getCompletedSemanticActions()==4 && frozen.getExecutedRepairActionIds().size()==1,
            "ordered semantic executions do not deduplicate repeat actions");
        trace.recordCompletedSemanticAction();
        check(frozen.getCompletedSemanticActions()==4, "published action witness is immutable");
        boolean[][][] twoBits = observations(new int[][] {{0,0,1,1}, {0,1,0,1}});
        DiagnosticReduction binary = new DiagnosticReduction(twoBits, new int[] {0,1,2,3});
        check(binary.readings == 2 && binary.bestSingleRemaining == 2, "two independent binary readings distinguish four repairs");
        DiagnosticReduction direct = new DiagnosticReduction(observations(new int[][] {{0,1,2,3}}), new int[] {0,1,2,3});
        check(direct.readings == 1 && direct.bestSingleRemaining == 1, "one-probe solution is measured honestly");
        check(DifficultyAssessment.classify(16, 4, 4, direct.readings, 13, 4, 6, 6, 4, true) == DifficultyProfile.EASY,
            "one-probe diagnostic solution cannot earn MEDIUM from other large counters");
        DiagnosticReduction sameRepair = new DiagnosticReduction(observations(new int[][] {{0,0,0,0}}), new int[] {0,0,0,0});
        check(sameRepair.readings == 0, "equivalent repairs need no forced human route");
        boolean rejected = false;
        try { new DiagnosticReduction(observations(new int[][] {{0,0,0}, {0,0,0}}), new int[] {0,1,2}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "unresolved legal repairs reject");
        rejected = false;
        try { new DiagnosticReduction(new boolean[][][] {{{true,false},{true,true}}}, new int[] {0,1}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "asymmetric comparison rejects");
        for (int parts : new int[] {3, 5, 16, 20}) {
            check(DifficultyAssessment.classify(parts, 1, 1, 0, 4, 2, 2, 1, 0, false) == DifficultyProfile.EASY,
                "adding inert part count cannot raise difficulty");
            check(DifficultyAssessment.classify(parts, 2, 3, 2, 8, 2, 4, 2, 0, true) == DifficultyProfile.MEDIUM,
                "parallel-path interaction independent of package count");
        }
        rejected = false;
        try { DifficultyAssessment.classify(21, 2, 3, 2, 8, 2, 4, 2, 0, true); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "synthetic difficulty classification remains inside its 20-part calibration envelope");
        check(!DifficultyProfile.HARD.isAvailable() && !DifficultyProfile.PSYCHOTIC.isAvailable(), "advanced bands remain unavailable");
        check(DifficultyAssessment.classify(16, 2, 3, 2, 100, 3, 100, 100, 0, false) == DifficultyProfile.EASY,
            "large proof-route and target-list counts cannot fabricate interaction");
        check(DifficultyAssessment.classify(16, 2, 3, 2, 1, 3, 4, 1, 0, true) == DifficultyProfile.MEDIUM,
            "proof-route category depth does not set the label");
        check(DifficultyAssessment.classify(5, 2, 2, 1, 2, 2, 2, 4, 0, true) == DifficultyProfile.EASY,
            "a certified two-observation route does not reclassify the catalogued easy parallel board");
        for (int badModes : new int[] {0, -1}) {
            rejected = false;
            try { DifficultyAssessment.classify(16, 2, 3, 1, 8, badModes, 4, 2, 0, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "missing instrument cannot earn profile");
        }
        PlayerLaunchRequest easy = new PlayerLaunchRequest("RB15_CONTROL", "9007199254740993", "EASY");
        PlayerLaunchRequest medium = new PlayerLaunchRequest("RB15_CONTROL", "9007199254740993", "MEDIUM");
        check(easy.seed == medium.seed && easy.generation().getDescriptor().toCanonical().equals(medium.generation().getDescriptor().toCanonical()),
            "profile changes admission only, electrical generation inputs remain exact");
        check(!easy.generation().canonical().equals(medium.generation().canonical()), "requested profile participates in proof dependencies");
        PlayerLaunchRequest q30 = PlayerLaunchRequest.random(Rb30Plan.FAMILY_ID,
            "9007199254740993", "MEDIUM");
        GenerationRequest q30Search = q30.generation();
        check(q30.seed == 9007199254740993L && q30.candidateSearch &&
            q30.profile == DifficultyProfile.MEDIUM && q30Search.candidateCount() == 4 &&
            q30Search.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
            MediumBoardNormalAdmission.IDENTITY.equals(
                q30Search.getRequiredPhysicalAdmissionIdentity()) &&
            q30Search.canonical().contains("difficulty=MEDIUM@" + DifficultyProfile.VERSION) &&
            q30Search.canonical().contains("assessment=" + DifficultyAssessment.VERSION),
            "Q30 MEDIUM label requires its current proof epoch and versioned normal physical admission");
        for (int ordinal = 0; ordinal < q30Search.candidateCount(); ordinal++) {
            GenerationRequest candidate = q30Search.candidate(ordinal);
            check(candidate.getDescriptor().getRootSeed() ==
                    QuickPlayAdmission.candidateSeed(q30.seed, ordinal) &&
                candidate.getExecutionPolicy() == GenerationExecutionPolicy.NORMAL_MEDIUM &&
                candidate.getDifficulty() == DifficultyProfile.MEDIUM,
                "Q30 difficulty assessment input follows each exact bounded candidate");
        }
        calibrationFeatureContracts();
        System.out.println("PASS: U05 difficulty contracts assertions=" + assertions);
    }
    /**
     * One actual normally admitted RB56 reference owner and its fixed route.
     * Receipt samples are explicitly mathematical falsifiers, never solver or
     * human-trial evidence; this fixture does not advance or solve a circuit.
     */
    private static void calibrationFeatureContracts() {
        CirSim priorSim = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName;
        String priorZener = ZenerElm.lastZenerModelName;
        String priorTransistor = TransistorElm.lastModelName;
        int priorFlags = MosfetElm.globalFlags;
        double priorBeta = MosfetElm.lastBeta;
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        Vector<CircuitElm> originalGraph = sim.elmList;
        Rb56Generator.Candidate candidate = null, foreignCandidate = null, developerCandidate = null;
        GeneratedBoardInstance owner = null, foreignOwner = null, developerOwner = null, smallOwner = null;
        CircuitElm.sim = sim; CirSim.theSim = sim;
        try {
            Rb56Plan plan = Rb56Plan.reference(77L);
            MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
                .generateWithPolicyResult(plan.board(), plan.layoutSeed, plan.routingSeed);
            check(route != null && route.accepted() && route.getLayout() != null &&
                    MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                        route.getStatistics().selectedRoutePolicy),
                "calibration fixture uses one fixed accepted RB56 P07 route");

            candidate = Rb56Generator.construct(plan, Rb56Generator.selectedFaultId(plan));
            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
                candidate.board(), route, plan.layoutSeed, plan.routingSeed);
            owner = Rb56Generator.assemble(candidate, route.getLayout(), admission);
            owner.requireNormalPhysicalAdmission();

            // Same deterministic plan and route, but a distinct opaque owner token.
            foreignCandidate = Rb56Generator.construct(plan, Rb56Generator.selectedFaultId(plan));
            GeneratedPhysicalAdmission foreignAdmission =
                MediumBoardNormalAdmission.fromAcceptedRb56Route(foreignCandidate.board(), route,
                    plan.layoutSeed, plan.routingSeed);
            foreignOwner = Rb56Generator.assemble(foreignCandidate,
                route.getLayout().copySealed(), foreignAdmission);
            developerCandidate = Rb56Generator.construct(plan, Rb56Generator.selectedFaultId(plan));
            developerOwner = asDeveloperOnlyRb56Fixture(developerCandidate);
            smallOwner = new LedIndicatorGenerator().generate(0);

            GeneratedChallengeController controller = installCaptureOwner(sim, owner);
            GeneratedDiagnosticProgram program = owner.getDiagnosticProvider().getObservationProgram();
            rb56ResistanceObservationContracts(sim, owner, program);
            GeneratedDiagnosticContextKey trusted = captureTrustedKey(sim, owner, plan);
            check(trusted.isTrustedCapture() && !owner.isDeveloperOnlyFaultRoute() &&
                    MediumBoardNormalAdmission.RB56_IDENTITY.equals(owner.getPhysicalAdmission().identity()) &&
                    owner.getBoard().getComponentIds().size() >= Rb56Plan.MIN_PACKAGES &&
                    owner.getBoard().getComponentIds().size() <= Rb56Plan.MAX_PACKAGES,
                "fixture is a cold trusted normal RB56 owner inside the component admission envelope");

            // These distinct sample values are mathematical falsifiers only. No solver or
            // human trial ran, and this contract does not advance a circuit.
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
                mathematicalEvidence(owner, program, true);
            GeneratedDiagnosticProofReceipt receipt = proofReceipt(owner, controller, program,
                evidence, trusted, false);
            DifficultyAssessment features = DifficultyAssessment.calibrationFeatures(owner, receipt);
            String canonical = features.canonical();
            check(features.profile == null && canonical.startsWith(
                    "tsj-difficulty-calibration/" + DifficultyAssessment.CALIBRATION_FEATURE_VERSION +
                        ";status=UNCALIBRATED;parts=") &&
                    canonical.contains(";hypotheses=") && canonical.contains(";temporal="),
                "cold RB56 extractor emits a versioned uncalibrated aggregate");
            check(features.components == owner.getBoard().getComponentIds().size() &&
                    features.hypotheses == evidence.size() &&
                    features.physicalOwners == GeneratedDiagnosticSolvabilityAdmission
                        .getPhysicalOwnerCount(owner.getFaultCandidates()) &&
                    features.readings == 1 && features.bestSingleRemaining == 1 &&
                    features.minWitnessActions == features.maxWitnessActions,
                "aggregate values come from the complete exact-owner synthetic receipt");
            boolean privateDetailsOmitted = !canonical.contains(program.canonical()) &&
                !canonical.contains("1000000");
            for (GeneratedFaultCandidate hypothesis : owner.getFaultCandidates())
                privateDetailsOmitted &= !canonical.contains(hypothesis.getHypothesisKey());
            check(privateDetailsOmitted,
                "canonical aggregate omits program text, hypothesis keys and sample values");

            Vector<GeneratedDiagnosticSolvabilityEvidence> exposed = receipt.getEvidence();
            exposed.clear();
            receipt.getEvidence().firstElement().getSolverSamples().clear();
            check(receipt.getEvidence().size() == evidence.size() &&
                    !receipt.getEvidence().firstElement().getSolverSamples().isEmpty() &&
                    canonical.equals(features.canonical()) &&
                    canonical.equals(DifficultyAssessment.calibrationFeatures(owner, receipt).canonical()),
                "receipt collections and repeated feature reads are immutable");

            reject("null calibration owner", featureAttempt(null, receipt));
            reject("null calibration receipt", featureAttempt(owner, null));
            reject("foreign exact RB56 owner", featureAttempt(foreignOwner, receipt));
            reject("developer-only RB56 owner", featureAttempt(developerOwner, receipt));
            reject("ordinary foreign-family owner", featureAttempt(smallOwner, receipt));
            reject("missing context capture", featureAttempt(owner,
                proofReceipt(owner, controller, program, evidence, null, false)));
            reject("untrusted raw context", featureAttempt(owner, proofReceipt(owner, controller,
                program, evidence, new GeneratedDiagnosticContextKey("u05-untrusted-context"), false)));
            reject("warm proof reuse", featureAttempt(owner,
                proofReceipt(owner, controller, program, evidence, trusted, true)));

            GeneratedDiagnosticProgram changedProgram = withExtraSettle(
                owner.getDiagnosticProvider().getDiagnosticPlan(), program);
            reject("foreign program identity", featureAttempt(owner, proofReceipt(owner, controller,
                changedProgram, mathematicalEvidence(owner, changedProgram, true), trusted, false)));
            reject("incomplete executed-action witness", featureAttempt(owner, proofReceipt(owner,
                controller, program, mathematicalEvidence(owner, program, false), trusted, false)));

            for (DifficultyProfile profile : DifficultyProfile.values())
                rejectUnavailable(features, profile);
            rejectUnavailable(features, null);

            installCaptureOwner(sim, smallOwner);
            GeneratedDiagnosticProgram smallProgram =
                smallOwner.getDiagnosticProvider().getObservationProgram();
            GeneratedDiagnosticProofReceipt smallReceipt = proofReceipt(smallOwner,
                sim.generatedChallengeController, smallProgram,
                mathematicalEvidence(smallOwner, smallProgram, true), null, false);
            DifficultyAssessment ordinary = DifficultyAssessment.assess(smallOwner, smallReceipt);
            check(ordinary.profile == DifficultyProfile.EASY &&
                    ordinary.canonical().startsWith("difficulty/" + DifficultyAssessment.VERSION +
                        ";profile=EASY;parts="),
                "ordinary assessment retains its existing versioned output shape");
        } finally {
            try {
                retireFixtureOwner(sim, smallOwner);
                retireFixtureOwner(sim, developerOwner);
                retireFixtureOwner(sim, foreignOwner);
                retireFixtureOwner(sim, owner);
                disposeUnownedCandidate(candidate, owner);
                disposeUnownedCandidate(foreignCandidate, foreignOwner);
                disposeUnownedCandidate(developerCandidate, developerOwner);
                sim.boardPowerController.detach();
                sim.generatedBoardInstance = null;
                sim.generatedChallengeController = null;
                sim.boardModificationController = null;
                sim.elmList = originalGraph;
            } finally {
                CircuitElm.sim = priorSim; CirSim.theSim = priorSingleton;
                DiodeElm.lastModelName = priorDiode;
                ZenerElm.lastZenerModelName = priorZener;
                TransistorElm.lastModelName = priorTransistor;
                MosfetElm.globalFlags = priorFlags; MosfetElm.lastBeta = priorBeta;
            }
        }
    }

    /** The normal RB56 owner advertises its installed-network Ω observation only after service OFF. */
    private static void rb56ResistanceObservationContracts(CirSim sim,
            GeneratedBoardInstance owner, GeneratedDiagnosticProgram program) {
        final GeneratedDiagnosticPlan plan = owner.getDiagnosticProvider().getDiagnosticPlan();
        Vector<String> modes = plan.getMeterModeIds();
        Vector<String> probeIds = plan.getProbeTargetIds();
        check(modes.contains("DC_VOLTAGE") && modes.contains("RESISTANCE") &&
                !modes.contains("CONTINUITY") && !modes.contains("DIODE"),
            "normal RB56 diagnostic plan advertises DC and resistance without broadening active modes");
        check(probeIds.contains("RDA.1") && probeIds.contains("RDA.2"),
            "RB56 resistance probes are declared semantic board pads");

        int offIndex = -1, resistanceIndex = -1;
        Vector<GeneratedDiagnosticProgram.Step> steps = program.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            GeneratedDiagnosticProgram.Step step = steps.get(i);
            if ((step.kind == GeneratedDiagnosticProgram.Kind.POWER ||
                    step.kind == GeneratedDiagnosticProgram.Kind.PROFILE_POWER) &&
                    step.power == BoardPowerState.UNPOWERED)
                offIndex = i;
            if (step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE &&
                    "DRIVER_A_RESISTANCE".equals(step.id) &&
                    "RDA.1".equals(step.red) && "RDA.2".equals(step.black))
                resistanceIndex = i;
        }
        boolean settledAfterOff = false, inputAfterOff = false, repoweredAfterOff = false;
        double naturalWaitAfterOff = 0;
        if (offIndex >= 0 && resistanceIndex > offIndex) {
            for (int i = offIndex + 1; i < resistanceIndex; i++) {
                GeneratedDiagnosticProgram.Step step = steps.get(i);
                if (step.kind == GeneratedDiagnosticProgram.Kind.SETTLE)
                    settledAfterOff = true;
                if (step.kind == GeneratedDiagnosticProgram.Kind.WAIT) {
                    check(step.readinessMode == GeneratedDiagnosticProgram.Kind.RESISTANCE &&
                            "RDA.1".equals(step.red) && "RDA.2".equals(step.black),
                        "each natural OFF wait uses the same declared meter readiness and physical pair");
                    naturalWaitAfterOff += step.seconds;
                }
                if (step.kind == GeneratedDiagnosticProgram.Kind.INPUT)
                    inputAfterOff = true;
                if ((step.kind == GeneratedDiagnosticProgram.Kind.POWER ||
                        step.kind == GeneratedDiagnosticProgram.Kind.PROFILE_POWER) &&
                        step.power == BoardPowerState.POWERED)
                    repoweredAfterOff = true;
            }
        }
        check(offIndex >= 0 && resistanceIndex > offIndex && settledAfterOff &&
                naturalWaitAfterOff > 0 &&
                naturalWaitAfterOff <= GeneratedDiagnosticProgram.MAX_TOTAL_WAIT_SECONDS &&
                !inputAfterOff && !repoweredAfterOff,
            "resistance observation follows OFF settling and bounded natural decay without input or repower");

        PhysicalPart<?> driverResistor = owner.getPhysicalBoardRuntime().getInstalledPart("RDA");
        PhysicalBoardSlot driverSlot = driverResistor == null ? null : driverResistor.getBoardSlot();
        boolean livePhysicalPads = sim.getGeneratedBoardInstance() == owner &&
            driverResistor instanceof PhysicalResistorPart && driverResistor.isInstalled() &&
            driverSlot != null && driverSlot.getInstalledPart() == driverResistor;
        for (String padId : new String[] {"RDA.1", "RDA.2"}) {
            BoardPad pad = owner.getBoard().getPad(padId);
            CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(padId);
            if (pad == null || driverSlot == null ||
                    !(endpoint instanceof CircuitPostMeasurementEndpoint)) {
                livePhysicalPads = false;
                continue;
            }
            int terminalIndex = driverSlot.getTerminalIds().indexOf(pad.getTerminalId());
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
            livePhysicalPads &= "RDA".equals(pad.getComponentId()) && terminalIndex >= 0 &&
                terminalIndex < driverSlot.getPadIds().size() &&
                padId.equals(driverSlot.getPadIds().get(terminalIndex)) &&
                owner.ownsRuntimeSimulationElement(post.getElement()) &&
                sim.containsElement(post.getElement()) && post.getPostIndex() >= 0 &&
                post.getPostIndex() < post.getElement().getPostCount() &&
                pad.getNetId().equals(owner.getSimulationBindings().getNetIdForEndpoint(post));
        }
        check(livePhysicalPads,
            "RB56 Ω endpoints resolve to live current-owner posts on their declared board nets");

        reject("RB56 plan rejects an unadvertised continuity program", new Attempt() {
            public void run() {
                GeneratedDiagnosticProgram.builder(plan).measure(
                    GeneratedDiagnosticProgram.Kind.CONTINUITY,
                    "U05_UNADVERTISED_CONTINUITY", "RDA.1", "RDA.2").build();
            }
        });
        reject("RB56 plan rejects an unadvertised diode program", new Attempt() {
            public void run() {
                GeneratedDiagnosticProgram.builder(plan).measure(
                    GeneratedDiagnosticProgram.Kind.DIODE,
                    "U05_UNADVERTISED_DIODE", "RDA.1", "RDA.2").build();
            }
        });
    }

    private interface Attempt { void run(); }

    private static Attempt featureAttempt(final GeneratedBoardInstance owner,
            final GeneratedDiagnosticProofReceipt receipt) {
        return new Attempt() {
            public void run() { DifficultyAssessment.calibrationFeatures(owner, receipt); }
        };
    }

    private static void rejectUnavailable(final DifficultyAssessment assessment,
            final DifficultyProfile profile) {
        reject("uncalibrated features cannot grant " + profile, new Attempt() {
            public void run() { assessment.require(profile); }
        });
    }

    private static void reject(String message, Attempt attempt) {
        boolean rejected = false;
        try { attempt.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private static GeneratedDiagnosticProofReceipt proofReceipt(GeneratedBoardInstance owner,
            GeneratedChallengeController controller, GeneratedDiagnosticProgram program,
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence,
            GeneratedDiagnosticContextKey context, boolean warm) {
        return new GeneratedDiagnosticProofReceipt(owner, controller, new Object(),
            program, evidence, 1, null, context, warm);
    }

    private static GeneratedDiagnosticContextKey captureTrustedKey(CirSim sim,
            GeneratedBoardInstance owner, Rb56Plan plan) {
        GenerationRequest request = GenerationRequest.forFamilyQualification(
            Rb56Plan.FAMILY_ID, plan.seed);
        return GeneratedDiagnosticContextKey.capture(sim, owner,
            request.candidateManifest(0), plan.canonical());
    }

    private static GeneratedChallengeController installCaptureOwner(final CirSim sim,
            final GeneratedBoardInstance owner) {
        if (sim.generatedBoardInstance != null) sim.boardPowerController.detach();
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.boardModificationController = new BoardModificationController(sim, owner);
        owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner,
            sim.boardModificationController, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        final GeneratedScenario<GeneratedObservedBehavior> selected = owner.getChallengeDefinition()
            .getScenarioCatalog().getCandidates().firstElement();
        GeneratedChallengeController controller = new GeneratedChallengeController(sim, owner) {
            @Override GeneratedScenario<GeneratedObservedBehavior> getScenario() {
                return selected;
            }
        };
        sim.generatedBoardInstance = owner;
        sim.generatedChallengeController = controller;
        return controller;
    }

    private static Vector<GeneratedDiagnosticSolvabilityEvidence> mathematicalEvidence(
            GeneratedBoardInstance owner, GeneratedDiagnosticProgram program,
            boolean completeActionWitness) {
        Vector<GeneratedFaultCandidate> candidates = owner.getFaultCandidates();
        int count = GeneratedFaultServiceabilityAdmission.getAdmittedCandidateCount(candidates);
        int physicalOwners = GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(candidates);
        Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        int hypothesis = 0;
        for (GeneratedFaultCandidate candidate : candidates) {
            if (!candidate.isAdmitted()) continue;
            GeneratedDiagnosticExecutionTrace.Builder trace =
                GeneratedDiagnosticExecutionTrace.builder();
            trace.recordIsolationAction(WorkbenchOperation.REMOVE);
            trace.recordAction(WorkbenchOperation.REMOVE);
            trace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
            trace.recordAction(Rb30Behavior.SENSORS_HIGH);
            trace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
            if (completeActionWitness) trace.recordCompletedSemanticAction();
            for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
                if (step.kind == GeneratedDiagnosticProgram.Kind.SETTLE) continue;
                switch (step.kind) {
                case INPUT:
                case POWER:
                case PROFILE_POWER: trace.recordInputPowerTransition(step.id); break;
                case WAIT: trace.recordTemporalWaitSample(step.id, step.seconds); break;
                case DC_VOLTAGE:
                case RESISTANCE:
                case CONTINUITY:
                case DIODE: trace.recordMeterMode(step.kind.name()); break;
                default: break;
                }
                trace.recordCompletedSemanticAction();
            }
            evidence.add(new GeneratedDiagnosticSolvabilityEvidence(
                "U05-MATHEMATICAL-FALSIFIER", owner.getCircuitFamilyId(), owner.getSeed(),
                candidate.getHypothesisKey(), count, physicalOwners,
                owner.getDiagnosticProvider().getDiagnosticPlan(),
                mathematicalSamples(program, hypothesis++), trace.freeze(
                    GeneratedDiagnosticRepairSemantics.forServiceability(
                        candidate.getServiceability())),
                true, "NONE", "PASS", "NONE", true, true, true));
        }
        return evidence;
    }

    private static Vector<GeneratedDiagnosticSample> mathematicalSamples(
            GeneratedDiagnosticProgram program, int hypothesis) {
        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        int sampleIndex = 0;
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            double value = 1000000 + hypothesis * 100 + sampleIndex;
            switch (step.kind) {
            case DC_VOLTAGE:
            case RESISTANCE:
            case CONTINUITY:
                samples.add(new GeneratedDiagnosticSample(step.id, value, .01));
                sampleIndex++;
                break;
            case DIODE:
                samples.add(new GeneratedDiagnosticSample(step.id + "_VOLTAGE", value, .01));
                samples.add(new GeneratedDiagnosticSample(step.id + "_CURRENT", value + .5, .01));
                sampleIndex += 2;
                break;
            default: break;
            }
        }
        return samples;
    }

    private static GeneratedDiagnosticProgram withExtraSettle(GeneratedDiagnosticPlan plan,
            GeneratedDiagnosticProgram source) {
        GeneratedDiagnosticProgram.Builder copy = GeneratedDiagnosticProgram.builder(plan);
        copy.settle();
        for (GeneratedDiagnosticProgram.Step step : source.getSteps()) {
            switch (step.kind) {
            case INPUT: copy.input(step.id); break;
            case POWER: copy.power(step.id, step.power); break;
            case PROFILE_POWER: copy.profilePower(step.id, step.power); break;
            case WAIT:
                if (step.readinessMode == null) copy.waitSample(step.id, step.seconds);
                else copy.waitForReadinessSample(step.id, step.readinessMode, step.red, step.black, step.seconds);
                break;
            case SETTLE: copy.settle(); break;
            default: copy.measure(step.kind, step.id, step.red, step.black); break;
            }
        }
        return copy.build();
    }

    private static GeneratedBoardInstance asDeveloperOnlyRb56Fixture(
            Rb56Generator.Candidate candidate) {
        RelayOutputGenerator.Assembly assembly = candidate.assembly;
        return new GeneratedBoardInstance(assembly.board, assembly.elements,
            candidate.plan.seed, Rb56Plan.FAMILY_ID, candidate.plan.topology(),
            "U05 developer-only RB56 guard fixture", assembly.components, assembly.power,
            assembly.connections, new UnqualifiedBehavior(), null, assembly.specifications,
            candidate.selectedFault, new GeneratedComponentOperationalStates(), null, null,
            candidate.runtime, null, true, candidate.faultCandidates,
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(Rb56Plan.FAMILY_ID,
                candidate.plan.topology(), candidate.plan.seed, candidate.faultCandidates));
    }

    private static void retireFixtureOwner(CirSim sim, GeneratedBoardInstance owner) {
        if (owner == null) return;
        sim.boardPowerController.detach();
        sim.generatedBoardInstance = null;
        sim.generatedChallengeController = null;
        sim.boardModificationController = null;
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        for (CircuitElm element : owner.getSimulationElements()) element.delete();
    }

    private static void disposeUnownedCandidate(Rb56Generator.Candidate candidate,
            GeneratedBoardInstance owner) {
        if (candidate == null || owner != null) return;
        RuntimeException cleanupCause = new RuntimeException("U05 fixture cleanup");
        Rb56Generator.disposeUnpublished(candidate.assembly, cleanupCause);
        if (cleanupCause.getSuppressed().length != 0)
            throw new AssertionError("U05 failed to retire an unowned candidate", cleanupCause);
    }

    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) {
            throw new UnsupportedOperationException("U05 fixture has no electrical qualification");
        }
        public void verifyFaulted(GeneratedBoardInstance owner,
                BoardModificationController modifications, BoardPowerState state) {
            throw new UnsupportedOperationException("U05 fixture has no electrical qualification");
        }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
                BoardModificationController modifications, BoardPowerState state, boolean overlay) {
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
                BoardModificationController modifications, BoardPowerState state, boolean overlay) {
            return false;
        }
    }
    private static boolean[][][] observations(int[][] values) {
        boolean[][][] result = new boolean[values.length][values[0].length][values[0].length];
        for (int m=0;m<values.length;m++) for (int a=0;a<values[m].length;a++) for(int b=0;b<values[m].length;b++)
            result[m][a][b]=values[m][a]==values[m][b];
        return result;
    }
    private static void check(boolean ok, String text) { assertions++; if(!ok)throw new AssertionError(text); }
}
