package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Vector;

/** Focused D01 contracts: complete populations, safe partitions and value-only reuse. */
public final class D01DiagnosticContractTest {
    private static int assertions;

    private interface Case { void run(); }

    private D01DiagnosticContractTest() { }

    public static void main(String[] args) {
        contextKeysUseCompleteValues();
        realContextCaptureAndDiagnosticCatalog();
        e02RailContextIsAudited();
        partitionRejectsSamplingAndUnsafeLeaves();
        staticPartitionPlanIsMonotonicAndBounded();
        candidateOrderingAndStructuralAdmission();
        cacheHasNoLiveOwnersAndBindsFreshReceipts();
        sensorControlStructuralContextAcrossVariants();
        System.out.println("PASS: D01 diagnostic contracts assertions=" + assertions);
    }

    private static void contextKeysUseCompleteValues() {
        GeneratedDiagnosticContextKey first = new GeneratedDiagnosticContextKey("Aa");
        GeneratedDiagnosticContextKey second = new GeneratedDiagnosticContextKey("BB");
        require(first.hashCode() == second.hashCode(), "collision fixture has equal hash");
        require(!first.equals(second), "context key compares complete canonical value");
        require(first.equals(new GeneratedDiagnosticContextKey("Aa")),
            "equal complete context values reuse their key");
        require(!first.equals(null) && !first.equals("Aa"),
            "context key equality is type-safe");
        require(first.canonical().startsWith("tsj-d01-diagnostic-context-v"),
            "context key carries its interpretation version");

        final String complete = "model=v1;recipe=RB15;source=VIN-5V;physical=front;" +
            "diagnostic=DC_VOLTAGE;repair=CATALOG_INSTALL";
        final String[] changedFields = {
            "model=v2;recipe=RB15;source=VIN-5V;physical=front;diagnostic=DC_VOLTAGE;repair=CATALOG_INSTALL",
            "model=v1;recipe=RB15-alt;source=VIN-5V;physical=front;diagnostic=DC_VOLTAGE;repair=CATALOG_INSTALL",
            "model=v1;recipe=RB15;source=VIN-9V;physical=front;diagnostic=DC_VOLTAGE;repair=CATALOG_INSTALL",
            "model=v1;recipe=RB15;source=VIN-5V;physical=bottom;diagnostic=DC_VOLTAGE;repair=CATALOG_INSTALL",
            "model=v1;recipe=RB15;source=VIN-5V;physical=front;diagnostic=RESISTANCE;repair=CATALOG_INSTALL",
            "model=v1;recipe=RB15;source=VIN-5V;physical=front;diagnostic=DC_VOLTAGE;repair=REMOVE"
        };
        GeneratedDiagnosticContextKey completeKey =
            new GeneratedDiagnosticContextKey(complete);
        for (final String changed : changedFields)
            require(!completeKey.equals(new GeneratedDiagnosticContextKey(changed)),
                "complete context invalidates changed dependency field: " + changed);

        reject("null context key", new Case() { public void run() {
            new GeneratedDiagnosticContextKey(null);
        }});
        reject("empty context key", new Case() { public void run() {
            new GeneratedDiagnosticContextKey("");
        }});
        final StringBuilder oversized = new StringBuilder(1024 * 1024 + 129);
        for (int i = 0; i != 1024 * 1024 + 129; i++) oversized.append('x');
        reject("oversized context key", new Case() { public void run() {
            new GeneratedDiagnosticContextKey(oversized.toString());
        }});

        final GeneratedDiagnosticProofCache cache = new GeneratedDiagnosticProofCache();
        require(cache.lookup(first) == null, "empty cache has no first context");
        require(cache.lookup(second) == null, "hash collision cannot hit a foreign context");
    }

    private static void realContextCaptureAndDiagnosticCatalog() {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        final GeneratedBoardInstance board = new LedIndicatorGenerator().generate(0);
        try {
            installCaptureFixture(sim, board);
            final String requestManifest = "d01-led-capture-request";
            final String realizationManifest = "d01-led-capture-realization";
            GenerationDependencyContext baseline = GenerationDependencyContext.capture(sim, board,
                requestManifest, realizationManifest);
            GeneratedDiagnosticContextKey baselineKey = GeneratedDiagnosticContextKey.capture(
                sim, board, requestManifest, realizationManifest);
            require(baselineKey.equals(baseline.diagnosticKey()),
                "real dependency capture and diagnostic key share the complete canonical value");
            String canonical = baseline.canonical();
            String expectedEpoch = "tsj-generation-dependencies-v16";
            require(canonical.startsWith("V" + expectedEpoch.length() + ":" +
                    expectedEpoch + ";") &&
                    canonical.indexOf("circuitjs-source-load-model-inputs-no-transient-dump-v5") >= 0,
                "repaired E02/E04 model interpretation invalidates older proof values");
            require(canonical.indexOf("diagnostic.hypothesis-population") >= 0 &&
                    canonical.indexOf("diagnostic.provider-repair-catalog") >= 0,
                "real dependency capture contains diagnostic population and repair catalog fields");

            final GeneratedDiagnosticProvider provider = board.getDiagnosticProvider();
            int admittedCount = 0;
            for (GeneratedFaultCandidate candidate : board.getFaultCandidates()) {
                require(canonical.indexOf(candidateFingerprintForContext(candidate)) >= 0,
                    "real dependency capture contains the complete per-candidate serviceability record");
                if (!candidate.isAdmitted()) continue;
                admittedCount++;
                require(candidate.getServiceability() != null,
                    "real admitted LED candidate has serviceability metadata");
                GeneratedFaultLocus locus = candidate.getServiceability().getLocus();
                require(locus != null && locus.getComponentId() != null,
                    "real LED candidate serviceability names its physical owner");
                if (candidate.getServiceability().getFaultClearingRepairActionIds().contains(
                        WorkbenchOperation.CATALOG_INSTALL)) {
                    String catalogId = provider.getCorrectCatalogId(board, locus.getComponentId());
                    require(catalogId != null && catalogId.length() > 0 &&
                            canonical.indexOf(candidate.getHypothesisKey() +
                                "|CATALOG_REQUIRED|" + catalogId) >= 0,
                        "real dependency capture contains each repairable candidate catalog record");
                } else {
                    require(canonical.indexOf(candidate.getHypothesisKey() +
                        "|NO_CATALOG|NOT_REQUIRED") >= 0,
                        "real dependency capture records candidates without a repair catalog");
                }
            }
            require(admittedCount > 0,
                "real LED context retains a nonempty admitted diagnostic population");

            VoltageElm source = null;
            ResistorElm resistor = null;
            LEDElm led = null;
            for (CircuitElm element : board.getSimulationElements()) {
                if (source == null && element instanceof VoltageElm) source = (VoltageElm) element;
                if (resistor == null && element instanceof ResistorElm) resistor = (ResistorElm) element;
                if (led == null && element instanceof LEDElm) led = (LEDElm) element;
            }
            require(source != null && resistor != null && led != null,
                "real LED fixture exposes source, resistor load and model elements");

            final VoltageElm sourceElement = source;
            final double sourceVoltage = source.maxVoltage;
            source.maxVoltage = sourceVoltage + 1;
            GenerationDependencyContext changedSource = GenerationDependencyContext.capture(sim,
                board, requestManifest, realizationManifest);
            require(!baseline.equals(changedSource) &&
                    !baselineKey.equals(changedSource.diagnosticKey()),
                "real source input change invalidates the complete dependency key");
            sourceElement.maxVoltage = sourceVoltage;

            final ResistorElm resistorElement = resistor;
            final double resistance = resistor.getResistance();
            resistor.setResistance(resistance * 2);
            GenerationDependencyContext changedLoad = GenerationDependencyContext.capture(sim,
                board, requestManifest, realizationManifest);
            require(!baseline.equals(changedLoad) &&
                    !baselineKey.equals(changedLoad.diagnosticKey()),
                "real resistor load change invalidates the complete dependency key");
            resistorElement.setResistance(resistance);

            final LEDElm modelElement = led;
            final String modelName = led.modelName;
            led.modelName = "old-default-led".equals(modelName) ? "default-led" : "old-default-led";
            led.setup();
            GenerationDependencyContext changedModel = GenerationDependencyContext.capture(sim,
                board, requestManifest, realizationManifest);
            require(!baseline.equals(changedModel) &&
                    !baselineKey.equals(changedModel.diagnosticKey()),
                "real LED model change invalidates the complete dependency key");
            modelElement.modelName = modelName;
            modelElement.setup();
        } finally {
            sim.boardPowerController.detach();
            sim.generatedBoardInstance = null;
            sim.generatedChallengeController = null;
            sim.boardModificationController = null;
            dispose(board);
        }
    }

    /** E02's bounded regulator/source dumps must participate in the D01 key. */
    private static void e02RailContextIsAudited() {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        GeneratedBoardInstance board = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.SENSOR_CONTROL, 0L);
        try {
            installCaptureFixture(sim, board);
            GenerationDependencyContext baseline = GenerationDependencyContext.capture(sim, board,
                "d01-e02-rail-request", "d01-e02-rail-realization");
            int regulators = 0;
            E02FiniteSourceElm input = null;
            for (CircuitElm element : board.getSimulationElements()) {
                if (element instanceof AbstractRailRegulatorElm) regulators++;
                if (input == null && element instanceof E02FiniteSourceElm)
                    input = (E02FiniteSourceElm) element;
            }
            require(regulators == 1 && input != null,
                "E04 dependency fixture retains its real E02 regulator and finite source");
            double originalVoltage = input.getSourceVoltage();
            input.setVoltage(originalVoltage - 1.0);
            GenerationDependencyContext changed = GenerationDependencyContext.capture(sim, board,
                "d01-e02-rail-request", "d01-e02-rail-realization");
            require(!baseline.equals(changed) &&
                    !baseline.diagnosticKey().equals(changed.diagnosticKey()),
                "E02 finite input state invalidates the complete D01 context key");
            input.setVoltage(originalVoltage);
        } finally {
            sim.boardPowerController.detach();
            sim.generatedBoardInstance = null;
            sim.generatedChallengeController = null;
            sim.boardModificationController = null;
            dispose(board);
        }
    }

    private static void partitionRejectsSamplingAndUnsafeLeaves() {
        final GeneratedDiagnosticProvider provider = new LedIndicatorDiagnosticProvider(0);
        final GeneratedDiagnosticPlan plan = provider.getDiagnosticPlan();
        final GeneratedDiagnosticProgram program = provider.getObservationProgram();
        final Vector<String> keys = new Vector<String>();
        keys.add("H-A"); keys.add("H-B"); keys.add("H-C");
        final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        evidence.add(proof("H-A", "R1", "FIXTURE", 0, plan, program, 3, 1, 1));
        evidence.add(proof("H-B", "R1", "FIXTURE", 0, plan, program, 3, 1, 1));
        evidence.add(proof("H-C", "R1", "FIXTURE", 0, plan, program, 3, 1, 2));

        final GeneratedDiagnosticPartitionPlan partition = GeneratedDiagnosticPartitionPlan.buildForKeys(
            keys, provider.getProviderId(), program, evidence);
        require(partition.getLeafCount() >= 2, "adaptive partition contains separated leaves");
        Vector<String> leafKeys = new Vector<String>();
        verifyPlanNode(partition.getRoot(), program, leafKeys);
        Collections.sort(leafKeys);
        Vector<String> expectedKeys = new Vector<String>(keys);
        Collections.sort(expectedKeys);
        require(expectedKeys.equals(leafKeys),
            "adaptive partition covers every canonical hypothesis exactly once");
        partition.validateEvidenceRoutes(evidence);
        for (final GeneratedDiagnosticSolvabilityEvidence proof : evidence) {
            GeneratedDiagnosticPartitionPlan.Cursor cursor = partition.beginCursor();
            while (!cursor.isComplete()) {
                String sampleId = cursor.getNextSampleId();
                cursor.accept(findSample(proof.getSolverSamples(), sampleId));
            }
            require(cursor.getResolvedHypothesisKeys().contains(proof.getHypothesisKey()),
                "adaptive cursor routes each complete proof to its own leaf");
            require(cursor.getEquivalentRepairClass() != null &&
                    cursor.getObservationCount() <= partition.getMaximumObservationDepth(),
                "adaptive cursor resolves within the certified observation depth");
        }
        final GeneratedDiagnosticPartitionPlan.Cursor undeclaredSampleCursor =
            partition.beginCursor();
        reject("cursor undeclared sample", new Case() { public void run() {
            undeclaredSampleCursor.accept(new GeneratedDiagnosticSample("UNDECLARED", 1, .01));
        }});
        final GeneratedDiagnosticPartitionPlan.Cursor outOfOrderCursor = partition.beginCursor();
        final Vector<GeneratedDiagnosticSample> firstProofSamples = evidence.firstElement().getSolverSamples();
        reject("cursor out-of-order sample", new Case() { public void run() {
            outOfOrderCursor.accept(firstProofSamples.lastElement());
        }});
        final GeneratedDiagnosticPartitionPlan.Cursor unmatchedBranchCursor = partition.beginCursor();
        reject("cursor sample with no legal branch", new Case() { public void run() {
            unmatchedBranchCursor.accept(new GeneratedDiagnosticSample(
                unmatchedBranchCursor.getNextSampleId(), 999, .01));
        }});
        String canonical = partition.canonical();

        Vector<GeneratedDiagnosticSolvabilityEvidence> reversed =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        Collections.reverse(reversed);
        GeneratedDiagnosticPartitionPlan warmPlan = GeneratedDiagnosticPartitionPlan.buildForKeys(
            keys, provider.getProviderId(), program, reversed);
        require(canonical.equals(warmPlan.canonical()),
            "partition canonical ordering is independent of evidence collection order");
        require(partition.getNodeCount() == warmPlan.getNodeCount() &&
                partition.getLeafCount() == warmPlan.getLeafCount() &&
                partition.getHypothesisKeys().equals(warmPlan.getHypothesisKeys()) &&
                partition.getProviderId().equals(warmPlan.getProviderId()) &&
                partition.getProgramIdentity().equals(warmPlan.getProgramIdentity()),
            "warm and cold plans preserve the same semantic population and shape");
        GeneratedDiagnosticPartitionPlan changedProvider =
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId() + "-changed",
                program, evidence);
        require(!canonical.equals(changedProvider.canonical()),
            "partition identity includes provider ownership");
        Vector<String> exposedPlanKeys = partition.getHypothesisKeys();
        exposedPlanKeys.clear();
        require(partition.getHypothesisKeys().size() == expectedKeys.size(),
            "partition population is defensively copied");

        final Vector<GeneratedDiagnosticSolvabilityEvidence> duplicateEvidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        duplicateEvidence.set(2, evidence.get(1));
        reject("duplicate diagnostic evidence coverage", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, duplicateEvidence);
        }});
        final Vector<GeneratedDiagnosticSolvabilityEvidence> foreignEvidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        foreignEvidence.set(2, proof("H-X", "R1", "FIXTURE", 0, plan, program, 3, 1, 2));
        reject("substituted diagnostic evidence coverage", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, foreignEvidence);
        }});

        final Vector<String> duplicateKeys = new Vector<String>(keys);
        duplicateKeys.add("H-A");
        reject("duplicate diagnostic hypothesis key", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(duplicateKeys, provider.getProviderId(),
                program, evidence);
        }});

        final Vector<GeneratedDiagnosticSolvabilityEvidence> truncatedPopulation =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        truncatedPopulation.remove(2);
        reject("arbitrary hypothesis truncation", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, truncatedPopulation);
        }});

        final Vector<GeneratedDiagnosticSolvabilityEvidence> truncatedSamples =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        Vector<GeneratedDiagnosticSample> shortSamples = evidence.get(1).getSolverSamples();
        shortSamples.remove(shortSamples.size() - 1);
        truncatedSamples.set(1, proofWithSamples("H-B", "R1", "FIXTURE", 0, plan,
            shortSamples, 3, 1));
        reject("arbitrary sample truncation", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, truncatedSamples);
        }});

        final Vector<GeneratedDiagnosticSample> reorderedSamples = sampleValues(program, 1);
        require(reorderedSamples.size() > 1, "fixture program has multiple observation samples");
        GeneratedDiagnosticSample firstSample = reorderedSamples.get(0);
        reorderedSamples.set(0, reorderedSamples.get(1));
        reorderedSamples.set(1, firstSample);
        final Vector<GeneratedDiagnosticSolvabilityEvidence> reorderedPopulation =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        reorderedPopulation.set(0, proofWithSamples("H-A", "R1", "FIXTURE", 0, plan,
            reorderedSamples, 3, 1));
        reject("reordered diagnostic samples", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, reorderedPopulation);
        }});

        final Vector<GeneratedDiagnosticSolvabilityEvidence> missingRouteEvidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
        final Vector<GeneratedDiagnosticSample> noRouteSamples =
            new Vector<GeneratedDiagnosticSample>();
        missingRouteEvidence.set(0, proofWithSamples("H-A", "R1", "FIXTURE", 0, plan,
            noRouteSamples, 3, 1));
        reject("cursor route with an omitted sample", new Case() { public void run() {
            partition.validateEvidenceRoutes(missingRouteEvidence);
        }});

        final Vector<GeneratedDiagnosticSolvabilityEvidence> incompatible =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        incompatible.add(proof("H-A", "R1", "FIXTURE", 0, plan, program, 2, 1, 1));
        incompatible.add(proof("H-B", "R2", "FIXTURE", 0, plan, program, 2, 1, 1));
        final Vector<String> pair = new Vector<String>(); pair.add("H-A"); pair.add("H-B");
        reject("identical observations with incompatible repair classes", new Case() {
            public void run() {
                GeneratedDiagnosticPartitionPlan.buildForKeys(pair,
                    provider.getProviderId(), program, incompatible);
            }
        });

        final Vector<String> overlappingIntervalKeys = new Vector<String>();
        overlappingIntervalKeys.add("H-OVERLAP-A");
        overlappingIntervalKeys.add("H-OVERLAP-B");
        final Vector<GeneratedDiagnosticSample> overlapA = sampleValues(program, 0);
        final Vector<GeneratedDiagnosticSample> overlapB = sampleValues(program, 0);
        String overlapSampleId = overlapA.firstElement().getSampleId();
        overlapA.set(0, new GeneratedDiagnosticSample(overlapSampleId, 0, .10));
        overlapB.set(0, new GeneratedDiagnosticSample(overlapSampleId, .15, .10));
        final Vector<GeneratedDiagnosticSolvabilityEvidence> overlappingIntervals =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        overlappingIntervals.add(proofWithSamples("H-OVERLAP-A", "R1", "FIXTURE", 0,
            plan, overlapA, 2, 1));
        overlappingIntervals.add(proofWithSamples("H-OVERLAP-B", "R1", "FIXTURE", 0,
            plan, overlapB, 2, 1));
        reject("overlapping measurement tolerance intervals cannot become branches", new Case() {
            public void run() {
                GeneratedDiagnosticPartitionPlan.buildForKeys(overlappingIntervalKeys,
                    provider.getProviderId(), program, overlappingIntervals);
            }
        });

        final GeneratedDiagnosticPartitionPlan.Node root = partition.getRoot();
        final Vector<GeneratedDiagnosticPartitionPlan.Branch> oneBranch =
            new Vector<GeneratedDiagnosticPartitionPlan.Branch>();
        oneBranch.add(root.getBranches().firstElement());
        reject("single-child adaptive branch", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.Node.branch(root.getSampleId(), oneBranch);
        }});
        reject("adaptive branch without an observation", new Case() { public void run() {
            GeneratedDiagnosticPartitionPlan.Node.branch("", root.getBranches());
        }});
    }

    private static void candidateOrderingAndStructuralAdmission() {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        final GeneratedBoardInstance board = new LedIndicatorGenerator().generate(0);
        try {
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(board);
            Vector<GeneratedFaultCandidate> first = GeneratedDiagnosticProofService.hypothesesFor(board);
            Vector<GeneratedFaultCandidate> second = GeneratedDiagnosticProofService.hypothesesFor(board);
            Vector<String> expected = GeneratedDiagnosticSolvabilityAdmission.getHypothesisKeys(
                board.getFaultCandidates());
            require(first.size() == second.size() && !first.isEmpty(),
                "structural admission exposes the complete candidate population");
            require(expected.equals(board.getDiagnosticSolvabilityContract().getHypothesisKeys()),
                "diagnostic contract records the exact admitted candidate population");
            require(first.size() == expected.size(),
                "proof candidate count equals the exact admitted population");
            for (int i = 0; i < first.size(); i++) {
                String firstKey = first.get(i).getHypothesisKey();
                require(firstKey.equals(second.get(i).getHypothesisKey()) &&
                        firstKey.equals(expected.get(i)) &&
                        GeneratedDiagnosticSolvabilityAdmission.isAdmitted(first.get(i)),
                    "candidate order is deterministic and exactly covers admitted keys");
                if (i > 0) require(first.get(i - 1).getHypothesisKey().compareTo(firstKey) < 0,
                    "candidate order is canonical and unique");
            }
            final Vector<String> omitted = new Vector<String>(expected);
            omitted.remove(0);
            reject("omitted candidate proof coverage", new Case() { public void run() {
                GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(
                    board.getFaultCandidates(), omitted);
            }});
            final Vector<String> substituted = new Vector<String>(expected);
            substituted.set(0, substituted.get(0) + "-SUBSTITUTED");
            reject("substituted candidate proof coverage", new Case() { public void run() {
                GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(
                    board.getFaultCandidates(), substituted);
            }});
            final Vector<String> duplicate = new Vector<String>(expected);
            duplicate.add(expected.firstElement());
            reject("duplicate candidate proof coverage", new Case() { public void run() {
                GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(
                    board.getFaultCandidates(), duplicate);
            }});
        } finally {
            dispose(board);
        }
    }

    private static void staticPartitionPlanIsMonotonicAndBounded() {
        final GeneratedDiagnosticProvider provider = new LedIndicatorDiagnosticProvider(0);
        final GeneratedDiagnosticProgram program = provider.getObservationProgram();
        final GeneratedDiagnosticPlan plan = provider.getDiagnosticPlan();
        final Vector<GeneratedDiagnosticSample> first = sampleValues(program, 1);
        final Vector<GeneratedDiagnosticSample> second = sampleValues(program, 1);
        GeneratedDiagnosticSample last = second.lastElement();
        second.set(second.size() - 1, last.isOverRange() ?
            new GeneratedDiagnosticSample(last.getSampleId(), 99, .01) :
            new GeneratedDiagnosticSample(last.getSampleId(), last.getValue() + 99, .01));
        final Vector<String> keys = new Vector<String>();
        keys.add("H-MONO-A"); keys.add("H-MONO-B");
        final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        evidence.add(proofWithSamples("H-MONO-A", "R1", "FIXTURE", 0, plan, first, 2, 1));
        evidence.add(proofWithSamples("H-MONO-B", "R1", "FIXTURE", 0, plan, second, 2, 1));
        final GeneratedDiagnosticPartitionPlan partition =
            GeneratedDiagnosticPartitionPlan.buildForKeys(keys, provider.getProviderId(),
                program, evidence);
        partition.validateExecutableProgram(program);
        require(partition.getMaximumObservationDepth() == 1,
            "static route chooses one final separating observation");
        GeneratedDiagnosticPartitionPlan.Cursor cursor = partition.beginCursor();
        cursor.accept(first.lastElement());
        require(cursor.isComplete() && cursor.getObservationCount() == 1 &&
                cursor.getResolvedHypothesisKeys().contains("H-MONO-A"),
            "static route executes the relevant observation subset");

        int selectedStep = -1;
        for (int i = 0; i < program.getSteps().size(); i++) {
            GeneratedDiagnosticProgram.Step step = program.getSteps().get(i);
            if (step.kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY ||
                    step.kind == GeneratedDiagnosticProgram.Kind.DIODE) {
                String id = step.kind == GeneratedDiagnosticProgram.Kind.DIODE ?
                    step.id + "_VOLTAGE" : step.id;
                if (id.equals(first.lastElement().getSampleId())) selectedStep = i;
            }
        }
        require(selectedStep > 0, "separating observation follows canonical transitions");
        int earlierMeterActions = 0;
        for (int i = 0; i < selectedStep; i++) {
            GeneratedDiagnosticProgram.Kind kind = program.getSteps().get(i).kind;
            if (kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    kind == GeneratedDiagnosticProgram.Kind.CONTINUITY ||
                    kind == GeneratedDiagnosticProgram.Kind.DIODE) earlierMeterActions++;
        }
        require(earlierMeterActions > 0,
            "static route has earlier meter actions available to skip");
        final GeneratedDiagnosticProgram foreignProgram =
            GeneratedDiagnosticProgram.builder(provider.getDiagnosticPlan())
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "FOREIGN",
                    provider.getDiagnosticPlan().getProbeTargetIds().firstElement(),
                    provider.getDiagnosticPlan().getReferenceTargetId())
                .build();
        reject("static route foreign program", new Case() { public void run() {
            partition.validateExecutableProgram(foreignProgram);
        }});
        final GeneratedDiagnosticProgram extraSettleProgram =
            copyProgramWithExtraSettle(provider.getDiagnosticPlan(), program);
        reject("static partition changed non-meter semantic prefix", new Case() { public void run() {
            partition.validateExecutableProgram(extraSettleProgram);
        }});

        final GeneratedDiagnosticProvider diodeProvider =
            new DiodeProtectedIndicatorDiagnosticProvider(0);
        final GeneratedDiagnosticProgram diodeProgram = diodeProvider.getObservationProgram();
        final Vector<GeneratedDiagnosticSample> diodeFirst = sampleValues(diodeProgram, 2);
        final Vector<GeneratedDiagnosticSample> diodeSecond =
            new Vector<GeneratedDiagnosticSample>(diodeFirst);
        int currentIndex = -1;
        int voltageIndex = -1;
        for (int i = 0; i < diodeSecond.size(); i++) {
            if (diodeSecond.get(i).getSampleId().equals("DIODE_FORWARD_VOLTAGE")) voltageIndex = i;
            if (diodeSecond.get(i).getSampleId().equals("DIODE_FORWARD_CURRENT")) currentIndex = i;
        }
        require(voltageIndex >= 0 && currentIndex > voltageIndex,
            "diode action exposes two canonical sample IDs");
        GeneratedDiagnosticSample diodeCurrent = diodeSecond.get(currentIndex);
        diodeSecond.set(currentIndex, new GeneratedDiagnosticSample(diodeCurrent.getSampleId(), 77, .01));
        Vector<GeneratedDiagnosticSample> diodeThird =
            new Vector<GeneratedDiagnosticSample>(diodeFirst);
        GeneratedDiagnosticSample diodeVoltage = diodeThird.get(voltageIndex);
        diodeCurrent = diodeThird.get(currentIndex);
        diodeThird.set(voltageIndex,
            new GeneratedDiagnosticSample(diodeVoltage.getSampleId(), 88, .01));
        diodeThird.set(currentIndex,
            new GeneratedDiagnosticSample(diodeCurrent.getSampleId(), 89, .01));
        Vector<String> diodeKeys = new Vector<String>();
        diodeKeys.add("H-DIODE-A"); diodeKeys.add("H-DIODE-B"); diodeKeys.add("H-DIODE-C");
        Vector<GeneratedDiagnosticSolvabilityEvidence> diodeEvidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        diodeEvidence.add(proofWithSamples("H-DIODE-A", "D1", "FIXTURE", 0,
            diodeProvider.getDiagnosticPlan(), diodeFirst, 3, 1));
        diodeEvidence.add(proofWithSamples("H-DIODE-B", "D1", "FIXTURE", 0,
            diodeProvider.getDiagnosticPlan(), diodeSecond, 3, 1));
        diodeEvidence.add(proofWithSamples("H-DIODE-C", "D1", "FIXTURE", 0,
            diodeProvider.getDiagnosticPlan(), diodeThird, 3, 1));
        GeneratedDiagnosticPartitionPlan diodePartition =
            GeneratedDiagnosticPartitionPlan.buildForKeys(diodeKeys, diodeProvider.getProviderId(),
                diodeProgram, diodeEvidence);
        diodePartition.validateExecutableProgram(diodeProgram);
        require(diodePartition.getRoot().getSampleId().equals("DIODE_FORWARD_VOLTAGE"),
            "diode static plan starts with the first emitted sibling");
        GeneratedDiagnosticPartitionPlan.Cursor diodeCursor = diodePartition.beginCursor();
        diodeCursor.accept(findSample(diodeFirst, "DIODE_FORWARD_VOLTAGE"));
        require(!diodeCursor.isComplete() &&
                diodeCursor.getNextSampleId().equals("DIODE_FORWARD_CURRENT"),
            "diode static plan keeps same-action voltage/current siblings in emitted order");
        diodeCursor.accept(findSample(diodeFirst, "DIODE_FORWARD_CURRENT"));
        require(diodeCursor.isComplete() &&
                diodeCursor.getResolvedHypothesisKeys().contains("H-DIODE-A"),
            "diode static plan resolves the serialized sibling path");
    }

    private static void cacheHasNoLiveOwnersAndBindsFreshReceipts() {
        final CirSim sim = new CirSim();
            sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        final GeneratedBoardInstance board = new LedIndicatorGenerator().generate(0);
        final GeneratedBoardInstance foreign = new LedIndicatorGenerator().generate(1);
        try {
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(board);
            final GeneratedDiagnosticProvider provider = board.getDiagnosticProvider();
            Vector<GeneratedFaultCandidate> candidates =
                GeneratedDiagnosticProofService.hypothesesFor(board);
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
                new Vector<GeneratedDiagnosticSolvabilityEvidence>();
            for (GeneratedFaultCandidate candidate : candidates)
                evidence.add(proof(candidate.getHypothesisKey(),
                    candidate.getServiceability().getLocus().getOwnerId(),
                    board.getCircuitFamilyId(), board.getSeed(), provider.getDiagnosticPlan(),
                    provider.getObservationProgram(), candidates.size(),
                    GeneratedDiagnosticSolvabilityAdmission.getPhysicalOwnerCount(
                        board.getFaultCandidates()), evidence.size() + 1));
            installCaptureFixture(sim, board);
            final GeneratedChallengeController controller = sim.generatedChallengeController;
            final GeneratedDiagnosticContextKey rawKey =
                new GeneratedDiagnosticContextKey("owner-context");
            final GeneratedDiagnosticContextKey foreignKey =
                new GeneratedDiagnosticContextKey("foreign-context");
            final GeneratedDiagnosticContextKey changedKey =
                new GeneratedDiagnosticContextKey("owner-context;model-changed");
            final GeneratedDiagnosticContextKey key = GeneratedDiagnosticContextKey.capture(sim, board,
                "d01-cache-request", "d01-cache-realization");
            final GeneratedDiagnosticProofCache cache = new GeneratedDiagnosticProofCache();
            final int rawKeyMisses = cache.getMisses();
            reject("service rejects raw context key", new Case() { public void run() {
                GeneratedDiagnosticProofService.reuseCachedIfPresent(sim, board, controller,
                    cache, rawKey);
            }});
            require(cache.getMisses() == rawKeyMisses,
                "untrusted raw context key is rejected before cache lookup");
            int coldMisses = cache.getMisses();
            require(GeneratedDiagnosticProofService.reuseCachedIfPresent(sim, board, controller,
                cache, key) == null && cache.getMisses() == coldMisses + 1,
                "cold cache lookup does not invent a proof receipt");
            final GeneratedDiagnosticProofReceipt source = new GeneratedDiagnosticProofReceipt(board,
                controller, new Object(), provider.getObservationProgram(), evidence, 4,
                null, key, false);
            final Vector<GeneratedDiagnosticSolvabilityEvidence> partialEvidence =
                new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence);
            partialEvidence.remove(partialEvidence.size() - 1);
            reject("partial proof cannot publish an adaptive partition", new Case() { public void run() {
                new GeneratedDiagnosticProofReceipt(board, controller, new Object(),
                    provider.getObservationProgram(), partialEvidence, 4, null, key, false);
            }});
            reject("cache rejects foreign receipt context", new Case() { public void run() {
                cache.put(foreignKey, board, source);
            }});
            final GeneratedDiagnosticProofCache.Entry prepared = cache.prepare(key, board, source);
            require(cache.size() == 0, "prepared cache entry is not published before owner commit");
            cache.storePrepared(prepared);
            final GeneratedDiagnosticProofCache.Entry entry = cache.lookup(key);
            require(entry != null, "completed proof is cacheable");
            reject("null prepared cache entry", new Case() { public void run() {
                cache.storePrepared(null);
            }});
            reject("changed context cannot prepare a cache entry", new Case() { public void run() {
                cache.prepare(changedKey, board, source);
            }});
            require(!source.isWarmReuseForDeveloperVerification() && source.getElapsedMillis() == 4,
                "cold receipt retains its measured proof provenance");
            require(!entry.retainsRuntimeObjectsForDeveloperVerification() &&
                    !cache.retainsRuntimeObjectsForDeveloperVerification(),
                "cache entry retains value evidence only");
            for (Field field : entry.getClass().getDeclaredFields()) {
                Class<?> type = field.getType();
                require(!GeneratedBoardInstance.class.isAssignableFrom(type) &&
                        !GeneratedChallengeController.class.isAssignableFrom(type) &&
                        !CirSim.class.isAssignableFrom(type) &&
                        !CircuitElm.class.isAssignableFrom(type),
                    "cache entry field has no live owner type: " + field.getName());
            }
            Vector<String> entryKeys = entry.getHypothesisKeys();
            entryKeys.clear();
            require(entry.getHypothesisKeys().size() == candidates.size(),
                "cache entry hypothesis values are defensively copied");
            Vector<GeneratedDiagnosticSolvabilityEvidence> entryEvidence = entry.getEvidence();
            entryEvidence.clear();
            require(entry.getEvidence().size() == candidates.size(),
                "cache entry evidence values are defensively copied");
            require(entry.getPartition() != null && source.getPartitionPlan() != null &&
                    entry.getPartition().canonical().equals(source.getPartitionPlan().canonical()) &&
                    entry.getPartition().getNodeCount() == source.getPartitionPlan().getNodeCount() &&
                    entry.getPartition().getLeafCount() == source.getPartitionPlan().getLeafCount(),
                "warm cache retains the cold partition plan semantics");
            GeneratedDiagnosticPartitionPlan rebuilt = GeneratedDiagnosticPartitionPlan.build(
                candidates, provider.getProviderId(), provider.getObservationProgram(),
                source.getEvidence());
            require(rebuilt.canonical().equals(entry.getPartition().canonical()),
                "cold and warm partition semantics rebuild identically from value evidence");
            Vector<GeneratedDiagnosticSolvabilityEvidence> sourceEvidence = source.getEvidence();
            sourceEvidence.clear();
            require(source.getEvidence().size() == candidates.size(),
                "cold receipt evidence is defensively copied");
            final GeneratedDiagnosticProofReceipt warm = entry.issueReceipt(board, controller,
                new Object(), key);
            require(warm != source && warm.isWarmReuseForDeveloperVerification(),
                "warm reuse issues a fresh owner-bound receipt");
            require(warm.getElapsedMillis() == 0 && warm.getContextKey().equals(key) &&
                    warm.getPartitionPlan() == entry.getPartition(),
                "warm receipt binds a fresh attempt to the cached immutable plan");
            require(evidenceEquivalent(source.getEvidence(), warm.getEvidence()),
                "warm receipt preserves the cold value evidence semantics");
            reject("foreign receipt context", new Case() { public void run() {
                warm.requireContextKey(foreignKey);
            }});
            reject("changed complete receipt context", new Case() { public void run() {
                warm.requireContextKey(changedKey);
            }});
            reject("changed complete cache reuse context", new Case() { public void run() {
                entry.validateForReuse(changedKey, board);
            }});
            require(controller.getDiagnosticProofReceipt() == null,
                "manual warm receipt does not publish without the service boundary");
            require(cache.lookup(changedKey) == null,
                "complete-key change is a cache miss after publication");
            require(cache.lookup(foreignKey) == null,
                "foreign context cannot hit the proof cache");
            final GeneratedDiagnosticProofCache.Entry foreignOwnerEntry = entry;
            reject("foreign owner cache reuse", new Case() { public void run() {
                foreignOwnerEntry.validateForReuse(key, foreign);
            }});
            reject("receipt rejects foreign context", new Case() { public void run() {
                source.requireContextKey(foreignKey);
            }});
            require(cache.getHits() >= 1 && cache.getMisses() >= 3,
                "cache hit/miss counters include cold and invalidated lookups");
        } finally {
            sim.boardPowerController.detach();
            sim.generatedBoardInstance = null;
            sim.generatedChallengeController = null;
            dispose(board);
            dispose(foreign);
        }
    }

    /** Native-safe E04 population/context seam; serial proof/cache runs in the browser verifier. */
    private static void sensorControlStructuralContextAcrossVariants() {
        final long[] seeds = { 0L, 1L };
        final String[] variants = { SensorControlGenerator.DIRECT_VARIANT,
            SensorControlGenerator.HYSTERETIC_VARIANT };
        for (int index = 0; index < seeds.length; index++) {
            final long seed = seeds[index];
            CirSim sim = new CirSim();
            sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            CircuitElm.sim = sim;
            GeneratedBoardInstance board = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.SENSOR_CONTROL, seed);
            try {
                GeneratedDiagnosticSolvabilityAdmission.validateStructural(board);
                GeneratedDiagnosticProvider provider = board.getDiagnosticProvider();
                GeneratedDiagnosticPlan plan = provider.getDiagnosticPlan();
                GeneratedDiagnosticProgram program = provider.getObservationProgram();
                Vector<GeneratedFaultCandidate> candidates =
                    GeneratedDiagnosticProofService.hypothesesFor(board);
                Vector<String> expectedKeys = GeneratedDiagnosticSolvabilityAdmission
                    .getHypothesisKeys(board.getFaultCandidates());
                require(variants[index].equals(board.getTopologyVariantId()),
                    "deterministic E04 seed selects the expected direct/hysteretic variant");
                require(candidates.size() == 3 && expectedKeys.size() == 3 &&
                        candidates.size() == expectedKeys.size() &&
                        expectedKeys.equals(board.getDiagnosticSolvabilityContract().getHypothesisKeys()),
                    "E04 structural admission retains all three canonical resistor hypotheses");
                for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                    GeneratedFaultCandidate candidate = candidates.get(candidateIndex);
                    require(candidate.isAdmitted() && candidate.getServiceability() != null &&
                            candidate.getServiceability().isAdmissible() &&
                            candidate.getServiceability().getLocus() != null &&
                            candidate.getServiceability().getLocus().getComponentId() != null &&
                            candidate.getHypothesisKey().equals(expectedKeys.get(candidateIndex)),
                        "E04 admitted candidate order names a physical resistor owner");
                }
                GeneratedDiagnosticSolvabilityAdmission.validatePlan(plan);
                program.validatePlan(plan);
                require("sensor-control-diagnostic@1".equals(provider.getProviderId()) &&
                        "SENSOR_CONTROL_CONDITION_SWEEP".equals(plan.getTemplateId()) &&
                        plan.getInputPowerTransitions().contains(
                            GeneratedBoardOperationIds.SENSOR_CONDITION_LOW) &&
                        plan.getInputPowerTransitions().contains(
                            GeneratedBoardOperationIds.SENSOR_CONDITION_MID) &&
                        plan.getInputPowerTransitions().contains(
                            GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH) &&
                        program.getSteps().size() > plan.getDepth(),
                    "E04 observation program is executable and exposes the complete condition sweep");

                installCaptureFixture(sim, board);
                String requestManifest = "d01-e04-structural-request-" + seed;
                String realizationManifest = "d01-e04-structural-realization-" + seed;
                GenerationDependencyContext context = GenerationDependencyContext.capture(sim,
                    board, requestManifest, realizationManifest);
                GeneratedDiagnosticContextKey key = GeneratedDiagnosticContextKey.capture(
                    sim, board, requestManifest, realizationManifest);
                require(key.equals(context.diagnosticKey()) &&
                        context.canonical().indexOf(provider.getProviderId()) >= 0 &&
                        context.canonical().indexOf(plan.getTemplateId()) >= 0,
                    "E04 complete context captures provider and observation-plan identity");
                for (GeneratedFaultCandidate candidate : candidates)
                    require(context.canonical().indexOf(candidateFingerprintForContext(candidate)) >= 0,
                        "E04 complete context captures every admitted candidate record");
                E02FiniteSourceElm input = null;
                for (CircuitElm element : board.getSimulationElements())
                    if (element instanceof E02FiniteSourceElm) { input = (E02FiniteSourceElm) element; break; }
                require(input != null, "E04 context fixture exposes its real finite E02 source");
                double originalVoltage = input.getSourceVoltage();
                input.setVoltage(originalVoltage - 1.0);
                GenerationDependencyContext changed = GenerationDependencyContext.capture(sim,
                    board, requestManifest, realizationManifest);
                GeneratedDiagnosticContextKey changedKey = GeneratedDiagnosticContextKey.capture(
                    sim, board, requestManifest, realizationManifest);
                require(!context.equals(changed) && !key.equals(changedKey),
                    "E04 finite-source change invalidates the complete diagnostic context");
                input.setVoltage(originalVoltage);
                SensorControlFamilyState familyState =
                    (SensorControlFamilyState) board.getFamilyState();
                familyState.getModel().setSensorVoltage(1.234);
                GenerationDependencyContext changedSensor = GenerationDependencyContext.capture(sim,
                    board, requestManifest, realizationManifest);
                GeneratedDiagnosticContextKey changedSensorKey =
                    GeneratedDiagnosticContextKey.capture(sim, board, requestManifest,
                        realizationManifest);
                require(!context.equals(changedSensor) && !key.equals(changedSensorKey),
                    "E04 variable sensor-source request invalidates the complete diagnostic context");
                familyState.getModel().setSensorCondition(
                    E04SensorControlModel.SensorCondition.SENSOR_LOW);
            } finally {
                sim.boardPowerController.detach();
                sim.generatedBoardInstance = null;
                sim.generatedChallengeController = null;
                sim.boardModificationController = null;
                dispose(board);
            }
        }
    }

    private static GeneratedDiagnosticSolvabilityEvidence proof(String key, String owner,
            String family, long seed, GeneratedDiagnosticPlan plan,
            GeneratedDiagnosticProgram program, int count, int owners, double base) {
        return proofWithSamples(key, owner, family, seed, plan, sampleValues(program, base),
            count, owners);
    }

    private static GeneratedDiagnosticSolvabilityEvidence proofWithSamples(String key,
            String owner, String family, long seed, GeneratedDiagnosticPlan plan,
            Vector<GeneratedDiagnosticSample> samples, int count, int owners) {
        GeneratedFaultServiceability serviceability = new GeneratedFaultServiceability(
            GeneratedFaultLocus.componentInternal(owner),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        GeneratedDiagnosticExecutionTrace.Builder trace = GeneratedDiagnosticExecutionTrace.builder();
        Vector<String> modes = plan.getMeterModeIds();
        trace.recordMeterMode(modes.firstElement());
        trace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
        trace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
        return new GeneratedDiagnosticSolvabilityEvidence("D01/" + key, family, seed, key,
            count, owners, plan, samples, trace.freeze(
                GeneratedDiagnosticRepairSemantics.forServiceability(serviceability)), true,
            "NONE", "PASS", "NONE", true, true, true);
    }

    private static Vector<GeneratedDiagnosticSample> sampleValues(
            GeneratedDiagnosticProgram program, double base) {
        Vector<GeneratedDiagnosticSample> result = new Vector<GeneratedDiagnosticSample>();
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            switch (step.kind) {
            case DC_VOLTAGE:
            case RESISTANCE:
            case CONTINUITY:
                result.add(new GeneratedDiagnosticSample(step.id, base, .01));
                break;
            case DIODE:
                result.add(new GeneratedDiagnosticSample(step.id + "_VOLTAGE", base, .01));
                result.add(new GeneratedDiagnosticSample(step.id + "_CURRENT", base, .01));
                break;
            default:
                break;
            }
        }
        return result;
    }

    private static void verifyPlanNode(GeneratedDiagnosticPartitionPlan.Node node,
            GeneratedDiagnosticProgram program, Vector<String> allKeys) {
        require(node != null, "adaptive partition node is non-null");
        if (node.isLeaf()) {
            GeneratedDiagnosticPartitionPlan.Leaf leaf = node.getLeaf();
            Vector<String> keys = leaf.getHypothesisKeys();
            require(!keys.isEmpty(), "partition leaf is nonempty");
            require(keys.size() == 1 || !"NONE".equals(leaf.getEquivalentRepairClass()),
                "partition leaf is one hypothesis or an equivalent repair class");
            String previous = "";
            for (String key : keys) {
                require(previous.compareTo(key) < 0, "partition leaf keys are canonical and unique");
                require(!allKeys.contains(key), "partition leaves do not overlap hypotheses");
                allKeys.add(key);
                previous = key;
            }
            return;
        }
        Vector<GeneratedDiagnosticPartitionPlan.Branch> branches = node.getBranches();
        require(node.getSampleId() != null && node.getSampleId().length() > 0,
            "adaptive branch names an observation");
        Vector<GeneratedDiagnosticSample> samples = sampleValues(program, 0);
        boolean knownSample = false;
        for (GeneratedDiagnosticSample sample : samples)
            if (node.getSampleId().equals(sample.getSampleId())) knownSample = true;
        require(knownSample, "adaptive branch uses a declared observation");
        require(branches.size() >= 2, "adaptive branch has at least two outcomes");
        String previousLabel = "";
        for (GeneratedDiagnosticPartitionPlan.Branch branch : branches) {
            require(branch != null && branch.getLabel() != null && branch.getLabel().length() > 0 &&
                    previousLabel.compareTo(branch.getLabel()) < 0 &&
                    branch.getRepresentative() != null && branch.getChild() != null &&
                    branch.getLabel().equals(sampleIdentity(branch.getRepresentative())),
                "adaptive branch labels and children are canonical");
            previousLabel = branch.getLabel();
            verifyPlanNode(branch.getChild(), program, allKeys);
        }
    }

    private static GeneratedDiagnosticSample findSample(
            Vector<GeneratedDiagnosticSample> samples, String sampleId) {
        for (GeneratedDiagnosticSample sample : samples)
            if (sampleId.equals(sample.getSampleId())) return sample;
        throw new AssertionError("Missing fixture sample: " + sampleId);
    }

    /** Preserves every meter sample while changing only a semantic program slot. */
    private static GeneratedDiagnosticProgram copyProgramWithExtraSettle(
            GeneratedDiagnosticPlan plan, GeneratedDiagnosticProgram source) {
        GeneratedDiagnosticProgram.Builder copy = GeneratedDiagnosticProgram.builder(plan);
        copy.settle();
        for (GeneratedDiagnosticProgram.Step step : source.getSteps()) {
            switch (step.kind) {
            case INPUT:
                copy.input(step.id);
                break;
            case POWER:
                copy.power(step.id, step.power);
                break;
            case PROFILE_POWER:
                copy.profilePower(step.id, step.power);
                break;
            case WAIT:
                copy.waitSample(step.id, step.seconds);
                break;
            case SETTLE:
                copy.settle();
                break;
            default:
                copy.measure(step.kind, step.id, step.red, step.black);
                break;
            }
        }
        return copy.build();
    }

    private static String sampleIdentity(GeneratedDiagnosticSample sample) {
        if (sample.isOverRange()) return "OL";
        return "N:" + Long.toHexString(Double.doubleToLongBits(sample.getValue())) +
            ":" + Long.toHexString(Double.doubleToLongBits(sample.getComparisonTolerance()));
    }

    private static boolean evidenceEquivalent(Vector<GeneratedDiagnosticSolvabilityEvidence> first,
            Vector<GeneratedDiagnosticSolvabilityEvidence> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            GeneratedDiagnosticSolvabilityEvidence left = first.get(i);
            GeneratedDiagnosticSolvabilityEvidence right = second.get(i);
            if (!left.getHypothesisKey().equals(right.getHypothesisKey()) ||
                    !left.getFamilyId().equals(right.getFamilyId()) || left.getSeed() != right.getSeed() ||
                    !left.getEquivalentRepairClass().equals(right.getEquivalentRepairClass()) ||
                    !left.getRepairSemantics().isEquivalentTo(right.getRepairSemantics()) ||
                    left.getSolverSamples().size() != right.getSolverSamples().size()) return false;
            Vector<GeneratedDiagnosticSample> leftSamples = left.getSolverSamples();
            Vector<GeneratedDiagnosticSample> rightSamples = right.getSolverSamples();
            for (int j = 0; j < leftSamples.size(); j++) {
                GeneratedDiagnosticSample a = leftSamples.get(j);
                GeneratedDiagnosticSample b = rightSamples.get(j);
                if (!a.getSampleId().equals(b.getSampleId()) || a.getOutcome() != b.getOutcome())
                    return false;
                if (!a.isOverRange() && (Double.doubleToLongBits(a.getValue()) !=
                        Double.doubleToLongBits(b.getValue()) ||
                        Double.doubleToLongBits(a.getComparisonTolerance()) !=
                        Double.doubleToLongBits(b.getComparisonTolerance()))) return false;
            }
        }
        return true;
    }

    private static void installCaptureFixture(final CirSim sim, final GeneratedBoardInstance board) {
        sim.elmList = new Vector<CircuitElm>(board.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.boardModificationController = new BoardModificationController(sim, board);
        board.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, board,
            sim.boardModificationController, 0);
        sim.boardPowerController.attach(board.getExternalPowerBindings());
        final GeneratedScenario<GeneratedObservedBehavior> selected = board.getChallengeDefinition()
            .getScenarioCatalog().getCandidates().firstElement();
        GeneratedChallengeController controller = new GeneratedChallengeController(sim, board) {
            @Override GeneratedScenario<GeneratedObservedBehavior> getScenario() {
                return selected;
            }
        };
        sim.generatedBoardInstance = board;
        sim.generatedChallengeController = controller;
    }

    private static String candidateFingerprintForContext(GeneratedFaultCandidate candidate) {
        GeneratedFault fault = candidate.getFault();
        StringBuilder value = new StringBuilder();
        appendContextLocal(value, candidate.getHypothesisKey());
        StringBuilder faultMetadata = new StringBuilder();
        appendContextLocal(faultMetadata, fault.getId());
        appendContextLocal(faultMetadata, fault.getType().name());
        appendContextLocal(faultMetadata, fault.getTargetComponentId());
        appendContextLocal(faultMetadata, fault.getCircuitFamilyId());
        appendContextLocal(faultMetadata, Long.toString(fault.getSelectionSeed()));
        appendContextLocal(faultMetadata, doubleBitsForContext(fault.getHealthyValue()));
        appendContextLocal(faultMetadata, doubleBitsForContext(fault.getEffectiveValue()));
        appendContextLocal(faultMetadata, fault.getHypothesisKey());
        appendContextLocal(value, faultMetadata.toString());
        appendContextLocal(value, Boolean.toString(candidate.isCompatible()));
        appendContextLocal(value, Boolean.toString(candidate.isServiceable()));
        appendContextLocal(value, Boolean.toString(candidate.isAdmitted()));

        GeneratedFaultServiceability serviceability = candidate.getServiceability();
        if (serviceability == null) {
            appendContextLocal(value, "null");
        } else {
            StringBuilder metadata = new StringBuilder();
            GeneratedFaultLocus locus = serviceability.getLocus();
            appendContextLocal(metadata, locus == null ? null : locus.getType().name());
            appendContextLocal(metadata, locus == null ? null : locus.getComponentId());
            appendContextLocal(metadata, locus == null ? null : locus.getTerminalId());
            appendContextLocal(metadata, locus == null ? null : locus.getPathId());
            appendContextList(metadata, "observe", serviceability.getObservationActionIds());
            appendContextList(metadata, "isolate", serviceability.getIsolationActionIds());
            appendContextList(metadata, "repair", serviceability.getRepairActionIds());
            appendContextList(metadata, "workflow", serviceability.getWorkflowActionIds());
            appendContextLocal(metadata, serviceability.getCustomerRetestOperationId());
            appendContextLocal(metadata, Boolean.toString(serviceability.isAdmissible()));
            appendContextLocal(value, metadata.toString());
        }
        return value.toString();
    }

    private static void appendContextList(StringBuilder out, String key, Vector<String> values) {
        out.append('F');
        appendContextFrame(out, key);
        appendContextFrame(out, Integer.toString(values.size()));
        for (String value : values) appendContextFrame(out, value);
    }

    private static void appendContextLocal(StringBuilder out, String value) {
        appendContextFrame(out, value);
    }

    private static void appendContextFrame(StringBuilder out, String value) {
        if (value == null) {
            out.append("N;");
        } else {
            out.append('V').append(value.length()).append(':').append(value).append(';');
        }
    }

    private static String doubleBitsForContext(double value) {
        return Long.toHexString(Double.doubleToLongBits(value));
    }

    private static void reject(String label, Case action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Accepted invalid D01 case: " + label);
    }

    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static void dispose(GeneratedBoardInstance board) {
        if (board == null) return;
        board.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : board.getSimulationElements()) element.delete();
    }
}
