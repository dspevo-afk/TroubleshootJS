package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.util.Collections;
import java.util.Map;

/** Focused developer proof for Task 41 diagnostic solvability and complexity. */
final class Task41DeveloperVerifier {
    private static Vector<GeneratedDiagnosticSolvabilityEvidence> lastEvidence;
    private static String lastNegativeRejectionReason;

    private Task41DeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        require(original != null, "Task 41 verifier requires a generated board");
        require(!original.isDeveloperOnlyFaultRoute(),
            "Task 41 normal verifier cannot start from a developer-only route");
        GeneratedDiagnosticSolvabilityAdmission.validate(sim, original);
        lastEvidence = null;
        Task41SimulationSnapshot originalSnapshot = Task41SimulationSnapshot.capture(sim);
        originalSnapshot.beginProof(sim);
        GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
        try {
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 proof attached a player workbench before candidate evaluation");
            Vector<Route> routes = normalRoutes();
            require(routes.size() == admittedNormalCorpusCount(),
                "Task 41 route/candidate corpus mismatch: routes=" + routes.size());
            Vector<String> normalHypothesisKeys = new Vector<String>();
            for (Route route : routes) {
                String proofCaseKey = route.familyId + "/" + route.seed + "/" + route.hypothesisKey;
                require(route.hypothesisKey != null &&
                        !normalHypothesisKeys.contains(proofCaseKey),
                    "Task 41 normal route population has a missing or duplicate hypothesis key");
                normalHypothesisKeys.add(proofCaseKey);
            }
            Collections.sort(normalHypothesisKeys);
            verifyOwnerDiversityClassification();
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
                new Vector<GeneratedDiagnosticSolvabilityEvidence>();
            int declaredDepthMinimum = Integer.MAX_VALUE;
            int declaredDepthWorst = 0;
            int measuredDepthMinimum = Integer.MAX_VALUE;
            int measuredDepthWorst = 0;
            int solverSamples = 0;
            int declaredTransitionCount = 0;
            int declaredIsolationCount = 0;
            int declaredTemporalCount = 0;
            int executedTransitionCount = 0;
            int executedIsolationCount = 0;
            int executedTemporalCount = 0;
            int declaredMeterModeCount = 0;
            int executedMeterModeCount = 0;
            boolean parallelAmbiguity = false;
            boolean retestObserved = true;
            Vector<String> declaredTemplates = new Vector<String>();
            Vector<String> declaredMeterModes = new Vector<String>();
            Vector<String> executedMeterModes = new Vector<String>();
            Vector<String> domains = new Vector<String>();
            Vector<String> declaredTransitions = new Vector<String>();
            Vector<String> executedTransitions = new Vector<String>();
            Vector<String> declaredIsolations = new Vector<String>();
            Vector<String> executedIsolations = new Vector<String>();
            Vector<String> declaredTemporal = new Vector<String>();
            Vector<String> executedTemporal = new Vector<String>();
            Vector<String> candidateMetrics = new Vector<String>();
            Vector<String> evaluatedGroups = new Vector<String>();
            for (Route route : routes) {
                String groupKey = route.familyId + "/" + route.seed;
                if (evaluatedGroups.contains(groupKey)) continue;
                evaluatedGroups.add(groupKey);
                Vector<CandidateEvaluation> evaluations = evaluateCandidateGroup(sim, route);
                Vector<String> equivalentClasses = classifyCandidateEquivalence(evaluations,
                    route.familyId, route.seed);
                for (Route routeInGroup : routes) {
                    if (!groupKey.equals(routeInGroup.familyId + "/" + routeInGroup.seed))
                        continue;
                    int selectedIndex = routeInGroup.hypothesisKey == null ?
                        findEvaluationIndex(evaluations, routeInGroup.type,
                            routeInGroup.targetComponentId) :
                        findEvaluationIndex(evaluations, routeInGroup.hypothesisKey);
                    CandidateEvaluation selected = evaluations.get(selectedIndex);
                    GeneratedDiagnosticSolvabilityEvidence routeEvidence =
                        withEquivalentRepairClass(selected.evidence,
                            equivalentClasses.get(selectedIndex));
                    evidence.add(routeEvidence);
                    declaredDepthMinimum = Math.min(declaredDepthMinimum,
                        routeEvidence.getDeclaredPlanDepth());
                    declaredDepthWorst = Math.max(declaredDepthWorst,
                        routeEvidence.getDeclaredPlanDepth());
                    measuredDepthMinimum = Math.min(measuredDepthMinimum,
                        routeEvidence.getMeasuredExecutionDepth());
                    measuredDepthWorst = Math.max(measuredDepthWorst,
                        routeEvidence.getMeasuredExecutionDepth());
                    solverSamples += routeEvidence.getSolverSamples().size();
                    Vector<String> routeDeclaredTransitions =
                        routeEvidence.getDeclaredInputPowerTransitions();
                    Vector<String> routeExecutedTransitions =
                        routeEvidence.getExecutedInputPowerTransitions();
                    Vector<String> routeDeclaredIsolations =
                        routeEvidence.getDeclaredIsolationActionIds();
                    Vector<String> routeExecutedIsolations =
                        routeEvidence.getExecutedIsolationActionIds();
                    Vector<String> routeDeclaredTemporal =
                        routeEvidence.getDeclaredTemporalWaitSampleIds();
                    Vector<String> routeExecutedTemporal =
                        routeEvidence.getExecutedTemporalWaitSamples();
                    Vector<String> routeDeclaredMeterModes =
                        routeEvidence.getDeclaredMeterModeIds();
                    Vector<String> routeExecutedMeterModes =
                        routeEvidence.getExecutedMeterModeIds();
                    declaredTransitionCount += routeDeclaredTransitions.size();
                    declaredIsolationCount += routeDeclaredIsolations.size();
                    declaredTemporalCount += routeDeclaredTemporal.size();
                    executedTransitionCount += routeExecutedTransitions.size();
                    executedIsolationCount += routeExecutedIsolations.size();
                    executedTemporalCount += routeExecutedTemporal.size();
                    declaredMeterModeCount += routeDeclaredMeterModes.size();
                    executedMeterModeCount += routeExecutedMeterModes.size();
                    parallelAmbiguity |= routeEvidence.hasDeclaredParallelPathAmbiguity();
                    retestObserved &= routeEvidence.hasUnaffectedFunctionRetestObservation() &&
                        routeEvidence.isCustomerRetestPassed();
                    appendUnique(declaredTemplates, routeEvidence.getDeclaredTemplateIds());
                    appendUnique(declaredMeterModes, routeDeclaredMeterModes);
                    appendUnique(executedMeterModes, routeExecutedMeterModes);
                    appendUnique(domains, routeEvidence.getDeclaredRailDomainIds());
                    appendUnique(declaredTransitions, routeDeclaredTransitions);
                    appendUnique(executedTransitions, routeExecutedTransitions);
                    appendUnique(declaredIsolations, routeDeclaredIsolations);
                    appendUnique(executedIsolations, routeExecutedIsolations);
                    appendUnique(declaredTemporal, routeDeclaredTemporal);
                    appendUnique(executedTemporal, routeExecutedTemporal);
                    String metric = routeEvidence.getHypothesisKey() + "@" +
                        routeEvidence.getRouteId() + "@" + routeEvidence.getSeed() +
                        "=" + routeEvidence.getAdmittedCandidateCount() + "/" +
                        routeEvidence.getAdmittedPhysicalOwnerCount();
                    if (!candidateMetrics.contains(metric)) candidateMetrics.add(metric);
                }
            }
            lastNegativeRejectionReason = verifyNegativePlanAdmission(sim);
            Task41SimulationSnapshot.verifyInjectedFailureStagesForDeveloperVerification(sim);
            require(declaredDepthMinimum != Integer.MAX_VALUE &&
                    declaredDepthWorst >= declaredDepthMinimum &&
                    measuredDepthMinimum != Integer.MAX_VALUE &&
                    measuredDepthWorst >= measuredDepthMinimum,
                "Task 41 did not produce deterministic declared/measured plan-depth evidence");
            require(retestObserved, "Task 41 route did not prove unaffected-function retest");
            lastEvidence = evidence;
            sim.publishTask41EvidenceForDeveloperVerification(
                "routes=" + evidence.size() + ";declaredDepth=" + declaredDepthMinimum +
                ".." + declaredDepthWorst + ";measuredDepth=" + measuredDepthMinimum +
                ".." + measuredDepthWorst + ";declaredTemplates=" + declaredTemplates.size() +
                ";solverSamples=" + solverSamples + ";declaredMeterModes=" +
                declaredMeterModeCount + ";executedMeterModes=" + executedMeterModeCount +
                ";declaredTransitions=" + declaredTransitionCount +
                ";executedTransitions=" + executedTransitionCount +
                ";declaredIsolation=" + declaredIsolationCount +
                ";executedIsolation=" + executedIsolationCount +
                ";declaredTemporal=" + declaredTemporalCount +
                ";executedTemporal=" + executedTemporalCount +
                ";declaredRailsDomains=" + domains.size() +
                ";parallelAmbiguity=" + parallelAmbiguity + ";retest=" + retestObserved +
                ";declaredVsExecuted=transitions:" + declaredTransitionCount + ">" +
                executedTransitionCount + ",isolation:" + declaredIsolationCount + ">" +
                executedIsolationCount + ",temporal:" + declaredTemporalCount + ">" +
                executedTemporalCount + ",meterModes:" + declaredMeterModeCount + ">" +
                executedMeterModeCount +
                ";candidateMetrics=" + join(candidateMetrics, ",") +
                ";sampleToleranceEvidence=" + sampleEvidence(evidence) +
                ";repairEquivalence=" + repairEquivalenceEvidence(evidence) +
                ";negative=" + lastNegativeRejectionReason + ";result=PASS");
        } finally {
            try {
                try {
                    sim.instrumentController.clearTargets();
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                } finally {
                    require(!sim.activeMeasurementOverlay,
                        "Task 41 left an active measurement overlay");
                }
            } finally {
                try {
                    originalSnapshot.restore(sim);
                    originalSnapshot.assertRestored(sim);
                } finally {
                    GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
                }
            }
        }
        A09DiagnosticDeveloperVerifier.verifyIfRequested(sim);
    }
    static Vector<GeneratedDiagnosticSolvabilityEvidence> getLastEvidenceForDeveloperVerification() {
        return lastEvidence == null ? new Vector<GeneratedDiagnosticSolvabilityEvidence>() :
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(lastEvidence);
    }
    static String getLastNegativeRejectionReasonForDeveloperVerification() {
        return lastNegativeRejectionReason;
    }

    private static Vector<CandidateEvaluation> evaluateCandidateGroup(CirSim sim, Route route) {
        GeneratedBoardInstance representative = route.generate();
        sim.installGeneratedChallengeForDeveloperVerification(representative);
        settleReady(sim, representative);
        GeneratedDiagnosticProofReceipt receipt = GeneratedDiagnosticProofService.prove(sim,
            representative, sim.getGeneratedChallengeController());
        Vector<CandidateEvaluation> results = new Vector<CandidateEvaluation>();
        for (GeneratedDiagnosticSolvabilityEvidence evidence : receipt.getEvidence()) {
            GeneratedFaultCandidate selected = GeneratedFaultEngine.selectHypothesis(
                evidence.getHypothesisKey(), representative.getFaultCandidates());
            results.add(new CandidateEvaluation(selected.getFault().getType(),
                selected.getFault().getTargetComponentId(), selected.getHypothesisKey(),
                new DiagnosticSignature(evidence.getSolverSamples(), evidence.getEquivalentRepairClass(),
                    evidence.getRepairSemantics()), evidence, representative.getTopologyVariantId(),
                representative.getPcbLayout().geometryFingerprint()));
        }
        return results;
    }

    private static CandidateEvaluation findEvaluation(Vector<CandidateEvaluation> evaluations,
            GeneratedFaultType type) {
        return evaluations.get(findEvaluationIndex(evaluations, type));
    }

    private static int findEvaluationIndex(Vector<CandidateEvaluation> evaluations,
            GeneratedFaultType type) {
        return findEvaluationIndex(evaluations, type, null);
    }

    private static int findEvaluationIndex(Vector<CandidateEvaluation> evaluations,
            String hypothesisKey) {
        if (hypothesisKey == null || hypothesisKey.length() == 0)
            throw new IllegalArgumentException("Missing Task 41 hypothesis key");
        for (int index = 0; index < evaluations.size(); index++)
            if (hypothesisKey.equals(evaluations.get(index).hypothesisKey)) return index;
        throw new IllegalStateException("Task 41 candidate hypothesis was not evaluated: " +
            hypothesisKey);
    }

    private static int findEvaluationIndex(Vector<CandidateEvaluation> evaluations,
            GeneratedFaultType type, String targetComponentId) {
        for (int index = 0; index < evaluations.size(); index++)
            if (evaluations.get(index).type == type &&
                    (targetComponentId == null ||
                     targetComponentId.equals(evaluations.get(index).targetComponentId)))
                return index;
        throw new IllegalStateException("Task 41 candidate was not evaluated: " + type +
            (targetComponentId == null ? "" : "/" + targetComponentId));
    }

    private static boolean sameSignature(DiagnosticSignature left, DiagnosticSignature right) {
        if (left.samples.size() != right.samples.size()) return false;
        for (int index = 0; index < left.samples.size(); index++) {
            GeneratedDiagnosticSample first = left.samples.get(index);
            GeneratedDiagnosticSample second = right.samples.get(index);
            if (!first.getSampleId().equals(second.getSampleId())) return false;
            if (first.getOutcome() != second.getOutcome()) return false;
            if (first.isOverRange()) continue;
            double tolerance = Math.max(first.getComparisonTolerance(),
                second.getComparisonTolerance());
            if (Math.abs(first.getValue() - second.getValue()) > tolerance) return false;
        }
        return true;
    }

    private static Vector<String> classifyCandidateEquivalence(
            Vector<CandidateEvaluation> evaluations, String familyId, long seed) {
        int[] groups = new int[evaluations.size()];
        for (int index = 0; index < groups.length; index++) groups[index] = index;
        for (int left = 0; left < evaluations.size(); left++)
            for (int right = left + 1; right < evaluations.size(); right++)
                if (sameSignature(evaluations.get(left).signature,
                        evaluations.get(right).signature)) {
                    require(sameRepairSemantics(evaluations.get(left).signature,
                            evaluations.get(right).signature),
                        "REPAIR_EQUIVALENCE_REJECTED: identical solver observations have " +
                        "different legal physical repair semantics");
                    union(groups, left, right);
                }
        for (int left = 0; left < evaluations.size(); left++)
            for (int right = left + 1; right < evaluations.size(); right++)
                if (findGroup(groups, left) == findGroup(groups, right) &&
                        !sameSignature(evaluations.get(left).signature,
                            evaluations.get(right).signature))
                    throw new IllegalStateException("Task 41 non-transitive candidate equivalence");
        Vector<String> result = new Vector<String>();
        for (int index = 0; index < evaluations.size(); index++) {
            int group = findGroup(groups, index);
            boolean shared = false;
            for (int other = 0; other < evaluations.size(); other++)
                if (other != index && findGroup(groups, other) == group) shared = true;
            result.add(shared ? "EQUIVALENT_REPAIR_" + familyId + "_" + seed + "_CLASS_" +
                group : "NONE");
        }
        validateCandidateSeparation(evaluations, result, familyId, seed);
        return result;
    }

    private static void validateCandidateSeparation(Vector<CandidateEvaluation> evaluations,
            Vector<String> equivalentClasses, String familyId, long seed) {
        require(evaluations.size() == equivalentClasses.size(),
            "Task 41 candidate equivalence evidence is incomplete");
        for (int candidate = 0; candidate < evaluations.size(); candidate++)
            for (int alternative = 0; alternative < evaluations.size(); alternative++) {
                if (candidate == alternative) continue;
                boolean equivalent = sameSignature(evaluations.get(candidate).signature,
                    evaluations.get(alternative).signature);
                String candidateClass = equivalentClasses.get(candidate);
                String alternativeClass = equivalentClasses.get(alternative);
                if (equivalent)
                    require(!"NONE".equals(candidateClass) &&
                            candidateClass.equals(alternativeClass),
                        "Task 41 indistinguishable candidate pair lacks equivalent-repair class: " +
                            familyId + "/" + seed);
                else
                    require(!candidateClass.equals(alternativeClass) ||
                            "NONE".equals(candidateClass),
                        "Task 41 distinct candidates share an equivalent-repair class: " +
                            familyId + "/" + seed);
            }
        if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId)) {
            CandidateEvaluation ledOpen = findEvaluation(evaluations, GeneratedFaultType.LED_OPEN);
            CandidateEvaluation r1Open = findEvaluation(evaluations, GeneratedFaultType.RESISTOR_OPEN);
            CandidateEvaluation r1Incorrect = findEvaluation(evaluations,
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE);
            require(!sameSignature(ledOpen.signature, r1Open.signature) &&
                    !sameSignature(ledOpen.signature, r1Incorrect.signature),
                "Task 41 LED_OPEN solver evidence collapsed with an R1-owned candidate");
        }
    }

    private static int findGroup(int[] groups, int index) {
        int root = index;
        while (groups[root] != root) root = groups[root];
        while (groups[index] != index) {
            int next = groups[index];
            groups[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] groups, int left, int right) {
        int first = findGroup(groups, left);
        int second = findGroup(groups, right);
        if (first != second) groups[second] = first;
    }

    private static boolean sameRepairSemantics(DiagnosticSignature left,
            DiagnosticSignature right) {
        return left != null && right != null && left.repairSemantics != null &&
            left.repairSemantics.isEquivalentTo(right.repairSemantics);
    }

    private static GeneratedDiagnosticSolvabilityEvidence withEquivalentRepairClass(
            GeneratedDiagnosticSolvabilityEvidence source, String classId) {
        return source.withEquivalentRepairClass(classId);
    }

    private static DiagnosticSignature collectSolverSignature(CirSim sim,
            GeneratedBoardInstance instance, GeneratedDiagnosticPlan plan,
            GeneratedDiagnosticExecutionTrace.Builder trace) {
        GeneratedDiagnosticProgram program = instance.getDiagnosticProvider().getObservationProgram();
        program.validatePlan(plan);
        return new DiagnosticSignature(GeneratedDiagnosticObservationExecutor.collect(
            sim, instance, program, trace), "NONE",
            GeneratedDiagnosticRepairSemantics.forServiceability(instance.getFaultServiceability()));
    }

    private static void dispatch(CirSim sim, WorkbenchOperation operation) {
        require(sim.pcbWorkbenchController != null &&
                sim.pcbWorkbenchController.isAvailable(operation),
            "Task 41 legal workbench action unavailable: " + operation.getId());
        require(sim.pcbWorkbenchController.dispatch(operation),
            "Task 41 legal workbench action failed: " + operation.getId());
    }
    private static String verifyNegativePlanAdmission(CirSim sim) {
        GeneratedDiagnosticPlan invalid = new GeneratedDiagnosticPlan(
            "NEGATIVE_DEVELOPER_OPERATION", "J1.2", new String[] { "J1.1", "J1.2" },
            new String[] { "DC_VOLTAGE" }, new String[] { "BOARD_POWER_ON" },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { "DEVELOPER_CLEAR_FAULT" }, new String[] { "STEADY_STATE_SAMPLE" },
            new String[] { "VIN", "GND" }, 2, false, true, "NONE");
        try {
            GeneratedDiagnosticSolvabilityAdmission.validatePlan(invalid);
            throw new IllegalStateException("Task 41 admitted a developer-only plan operation");
        } catch (IllegalArgumentException expected) {
            verifyNegativeCandidateSeparation();
            String reservedAction = verifyReservedActionRejection();
            String repairEquivalence = verifyNegativeRepairSemanticsEquivalence(sim);
            String excludedObservation = verifyExcludedDeveloperRoutes(sim);
            return "UNSUPPORTED_PLAYER_OPERATION;EQUIVALENT_REPAIR_CLASS_REQUIRED;" +
                reservedAction + ";" + repairEquivalence + ";DIODE_SHORT_EXCLUDED;" +
                excludedObservation;
        }
    }

    private static String verifyReservedActionRejection() {
        GeneratedDiagnosticPlan reserved = new GeneratedDiagnosticPlan(
            "NEGATIVE_RESERVED_RESTORE", "J1.2", new String[] { "J1.1", "J1.2" },
            new String[] { "DC_VOLTAGE" }, new String[] { "BOARD_POWER_ON" },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.RESTORE },
            new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
            new String[] { "STEADY_STATE_SAMPLE" }, new String[] { "VIN", "GND" },
            2, false, false, "NONE");
        try {
            GeneratedDiagnosticSolvabilityAdmission.validatePlan(reserved);
            throw new IllegalStateException("Task 41 admitted reserved RESTORE workflow");
        } catch (IllegalArgumentException expected) {
            return "RESERVED_RESTORE_REJECTED";
        }
    }

    private static void verifyNegativeCandidateSeparation() {
        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        samples.add(new GeneratedDiagnosticSample("NEGATIVE_SAMPLE", 1, .02));
        Vector<CandidateEvaluation> evaluations = new Vector<CandidateEvaluation>();
        evaluations.add(new CandidateEvaluation(GeneratedFaultType.RESISTOR_OPEN, null,
            new DiagnosticSignature(samples, "NONE"), null, "NEGATIVE", "NEGATIVE"));
        evaluations.add(new CandidateEvaluation(GeneratedFaultType.RESISTOR_INCORRECT_VALUE, null,
            new DiagnosticSignature(samples, "NONE"), null, "NEGATIVE", "NEGATIVE"));
        Vector<String> missingClasses = new Vector<String>();
        missingClasses.add("NONE");
        missingClasses.add("NONE");
        try {
            validateCandidateSeparation(evaluations, missingClasses, "NEGATIVE", 0);
            throw new IllegalStateException("Task 41 admitted an unclassified equivalent pair");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().indexOf("equivalent-repair class") >= 0,
                "Task 41 negative separation assertion was not deterministic");
        }
    }

    private static String verifyNegativeRepairSemanticsEquivalence(CirSim sim) {
        /*
         * Keep this negative assertion grounded in the same generated board
         * family used by the live proof.  The two boards receive the same
         * legal player isolation sequence before capture, so their resulting
         * circuits are genuinely live-equivalent.  Each signature is still
         * captured independently from CircuitJS; only the generated repair
         * semantics remain different.
         */
        GeneratedBoardInstance collectorOpen = new NpnLowSideSwitchGenerator()
            .generateForDiagnosticSolvability(0, GeneratedFaultType.TRANSISTOR_CE_OPEN);
        GeneratedBoardInstance baseOpen = new NpnLowSideSwitchGenerator()
            .generateForDiagnosticSolvability(0, GeneratedFaultType.BASE_RESISTOR_OPEN);
        require(collectorOpen.getCircuitFamilyId().equals(baseOpen.getCircuitFamilyId()) &&
                collectorOpen.getTopologyVariantId().equals(baseOpen.getTopologyVariantId()) &&
                collectorOpen.getSeed() == baseOpen.getSeed() &&
                collectorOpen.getPcbLayout().geometryFingerprint().equals(
                    baseOpen.getPcbLayout().geometryFingerprint()),
            "Task 41 negative repair fixture changed NPN topology/layout");

        GeneratedFaultCandidate collectorCandidate = findGeneratedCandidate(collectorOpen,
            GeneratedFaultType.TRANSISTOR_CE_OPEN);
        GeneratedFaultCandidate baseCandidate = findGeneratedCandidate(baseOpen,
            GeneratedFaultType.BASE_RESISTOR_OPEN);
        require(collectorCandidate.getBinding() == collectorOpen.getFaultBinding() &&
                baseCandidate.getBinding() == baseOpen.getFaultBinding(),
            "Task 41 negative repair fixture did not use each board's selected candidate");
        require(GeneratedFaultServiceabilityAdmission.isAdmitted(collectorCandidate) &&
                GeneratedDiagnosticSolvabilityAdmission.isAdmitted(collectorCandidate) &&
                GeneratedFaultServiceabilityAdmission.isAdmitted(baseCandidate) &&
                GeneratedDiagnosticSolvabilityAdmission.isAdmitted(baseCandidate),
            "Task 41 negative repair fixture selected an unadmitted NPN candidate");
        require(collectorCandidate.getServiceability() != null &&
                baseCandidate.getServiceability() != null &&
                collectorCandidate.getServiceability().getLocus() != null &&
                baseCandidate.getServiceability().getLocus() != null,
            "Task 41 negative repair fixture has incomplete serviceability bindings");
        String collectorOwner = collectorCandidate.getServiceability().getLocus().getOwnerId();
        String baseOwner = baseCandidate.getServiceability().getLocus().getOwnerId();
        require(!collectorOwner.equals(baseOwner),
            "Task 41 negative repair fixture did not retain different physical owners");
        GeneratedDiagnosticRepairSemantics collectorRepairSemantics =
            GeneratedDiagnosticRepairSemantics.forServiceability(
                collectorCandidate.getServiceability());
        GeneratedDiagnosticRepairSemantics baseRepairSemantics =
            GeneratedDiagnosticRepairSemantics.forServiceability(baseCandidate.getServiceability());
        require(!collectorRepairSemantics.isEquivalentTo(baseRepairSemantics),
            "Task 41 negative repair fixture unexpectedly has equivalent repair semantics");

        Task41SimulationSnapshot priorSnapshot = Task41SimulationSnapshot.capture(sim);
        priorSnapshot.beginProof(sim);
        DiagnosticSignature collectorOpenSignature = null;
        DiagnosticSignature baseOpenSignature = null;
        try {
            collectorOpenSignature = collectLiveNegativeRepairSignature(sim, collectorOpen);
            baseOpenSignature = collectLiveNegativeRepairSignature(sim, baseOpen);
            require(collectorOpenSignature.getSamples().size() > 0 &&
                    baseOpenSignature.getSamples().size() > 0,
                "Task 41 negative repair fixture produced no live solver samples");
            require(collectorOpenSignature.getSamples().size() ==
                    baseOpenSignature.getSamples().size(),
                "Task 41 negative repair fixture changed live sample shape");
            require(collectorOpenSignature.getRepairSemantics().isEquivalentTo(
                        collectorRepairSemantics) &&
                    baseOpenSignature.getRepairSemantics().isEquivalentTo(baseRepairSemantics),
                "Task 41 negative repair fixture lost generated repair semantics");
            require(sameSignature(collectorOpenSignature, baseOpenSignature),
                "Task 41 constructed NPN isolation pair is not live-equivalent");

            Vector<CandidateEvaluation> evaluations = new Vector<CandidateEvaluation>();
            evaluations.add(new CandidateEvaluation(GeneratedFaultType.TRANSISTOR_CE_OPEN, null,
                collectorOpenSignature, null,
                collectorOpen.getTopologyVariantId(),
                collectorOpen.getPcbLayout().geometryFingerprint()));
            evaluations.add(new CandidateEvaluation(GeneratedFaultType.BASE_RESISTOR_OPEN, null,
                baseOpenSignature, null,
                baseOpen.getTopologyVariantId(),
                baseOpen.getPcbLayout().geometryFingerprint()));
            try {
                classifyCandidateEquivalence(evaluations, "NPN_NEGATIVE_REPAIR_SEMANTICS", 0);
                throw new IllegalStateException("Task 41 formed an equivalent class for different " +
                    "physical repair owners");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().indexOf("REPAIR_EQUIVALENCE_REJECTED") >= 0,
                    "Task 41 live repair-equivalence rejection was not deterministic");
                return "REPAIR_EQUIVALENCE_REJECTED_DIFFERENT_OWNER";
            }
        } finally {
            try {
                try {
                    sim.instrumentController.clearTargets();
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                } finally {
                    if (sim.activeMeasurementOverlay)
                        throw new IllegalStateException(
                            "Task 41 negative repair fixture left measurement overlay");
                }
            } finally {
                priorSnapshot.restore(sim);
                priorSnapshot.assertRestored(sim);
            }
        }
    }

    private static GeneratedFaultCandidate findGeneratedCandidate(GeneratedBoardInstance instance,
            GeneratedFaultType type) {
        for (GeneratedFaultCandidate candidate : instance.getFaultCandidates())
            if (candidate.getFault().getType() == type) return candidate;
        throw new IllegalStateException("Task 41 generated negative fixture has no candidate: " +
            type);
    }

    private static DiagnosticSignature collectLiveNegativeRepairSignature(CirSim sim,
            GeneratedBoardInstance instance) {
        sim.installGeneratedChallengeForDeveloperVerification(instance);
        require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
            "Task 41 negative repair fixture attached a player workbench");
        settleReady(sim, instance);
        GeneratedDiagnosticSolvabilityAdmission.validate(sim, instance);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task41-negative-unpowered");
        PhysicalPart<?> baseResistor = instance.getPhysicalBoardRuntime().getInstalledPart("RB");
        require(baseResistor != null && baseResistor.isInstalled(),
            "Task 41 negative repair fixture has no installed NPN base resistor");
        // Remove the base path and lift the collector through the same real
        // player operations for both candidates; do not rewrite meter samples.
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, baseResistor));
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task41-negative-remove");
        PhysicalPart<?> transistor = instance.getPhysicalBoardRuntime().getInstalledPart("Q1");
        require(transistor != null && transistor.isInstalled(),
            "Task 41 negative repair fixture has no installed NPN transistor");
        dispatch(sim, WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, transistor,
            "Q1", "Q1.C"));
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task41-negative-lift");
        DiagnosticSignature signature = collectSolverSignature(sim, instance,
            planFor(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH),
            GeneratedDiagnosticExecutionTrace.builder());
        sim.instrumentController.clearTargets();
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay,
            "Task 41 negative repair fixture left a measurement overlay after capture");
        return signature;
    }

    private static String verifyExcludedDeveloperRoutes(CirSim sim) {
        GeneratedBoardInstance diode = new DiodeProtectedIndicatorGenerator().generate(0);
        boolean diodeShortAdmitted = false;
        for (GeneratedFaultCandidate candidate : diode.getFaultCandidates())
            if (candidate.getFault().getType() == GeneratedFaultType.DIODE_SHORT &&
                    GeneratedDiagnosticSolvabilityAdmission.isAdmitted(candidate))
                diodeShortAdmitted = true;
        require(!diodeShortAdmitted, "Task 41 admitted normal DIODE_SHORT");

        GeneratedBoardInstance loadPath = new NpnLowSideSwitchGenerator()
            .generateForFaultVerification(0, GeneratedFaultType.LOAD_PATH_OPEN);
        require(loadPath.isDeveloperOnlyFaultRoute(),
            "Task 41 treated NPN LOAD_PATH_OPEN as a normal route");
        return verifyConnectorOnlyObservationRejected() + ";" +
            verifyNpnLoadPathObservationComparison(sim);
    }

    private static String verifyConnectorOnlyObservationRejected() {
        GeneratedFaultServiceability connectorServiceability = new GeneratedFaultServiceability(
            GeneratedFaultLocus.connectorContact("J1", "1"),
            new String[] { GeneratedFaultServiceability.OBSERVE_CONNECTOR_CONTACT },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        GeneratedFaultCandidate connector = new GeneratedFaultCandidate(
            new GeneratedFaultBinding(new GeneratedFault("TASK41_CONNECTOR_OBSERVATION",
                GeneratedFaultType.CONNECTOR_OPEN_PATH, "J1", "LED_INDICATOR", 0),
                new SwitchOpenFaultEffect(new SwitchElm(0, 0)), connectorServiceability), true);
        require(!GeneratedFaultServiceabilityAdmission.isAdmitted(connector),
            "Task 41 admitted connector-only observation evidence");
        return "CONNECTOR_OBSERVATION_REJECTED";
    }

    /**
     * The load-path effect remains a developer-only fixture.  It is still
     * compared against the public-terminal evidence for the same-layout
     * collector/emitter-open route so exclusion is based on a real observation,
     * not on a private fault switch or a metadata distinction.
     */
    private static String verifyNpnLoadPathObservationComparison(CirSim sim) {
        Task41SimulationSnapshot priorSnapshot = Task41SimulationSnapshot.capture(sim);
        priorSnapshot.beginProof(sim);
        DiagnosticSignature collectorOpenSignature = null;
        DiagnosticSignature loadPathSignature = null;
        try {
            GeneratedBoardInstance collectorOpen = new NpnLowSideSwitchGenerator()
                .generateForDiagnosticSolvability(0, GeneratedFaultType.TRANSISTOR_CE_OPEN);
            GeneratedBoardInstance loadPath = new NpnLowSideSwitchGenerator()
                .generateForFaultVerification(0, GeneratedFaultType.LOAD_PATH_OPEN);
            require(collectorOpen.getCircuitFamilyId().equals(loadPath.getCircuitFamilyId()) &&
                    collectorOpen.getTopologyVariantId().equals(loadPath.getTopologyVariantId()) &&
                    collectorOpen.getSeed() == loadPath.getSeed() &&
                    collectorOpen.getPcbLayout().geometryFingerprint().equals(
                        loadPath.getPcbLayout().geometryFingerprint()),
                "Task 41 NPN exclusion comparison did not keep the same topology/layout");
            sim.installGeneratedChallengeForDeveloperVerification(collectorOpen);
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 NPN CE-open comparison attached a player workbench");
            settleReady(sim, collectorOpen);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, collectorOpen);
            collectorOpenSignature = collectSolverSignature(sim, collectorOpen,
                planFor(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH),
                GeneratedDiagnosticExecutionTrace.builder());
            sim.instrumentController.clearTargets();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            require(!sim.activeMeasurementOverlay,
                "Task 41 NPN exclusion CE-open observation left a measurement overlay");

            sim.installGeneratedChallengeForDeveloperVerification(loadPath);
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 NPN load-path comparison attached a player workbench");
            settleReady(sim, loadPath);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, loadPath);
            loadPathSignature = collectSolverSignature(sim, loadPath,
                planFor(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH),
                GeneratedDiagnosticExecutionTrace.builder());
            sim.instrumentController.clearTargets();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            require(!sim.activeMeasurementOverlay,
                "Task 41 NPN exclusion load-path observation left a measurement overlay");
            boolean separated = !sameSignature(collectorOpenSignature, loadPathSignature);
            boolean sameLegalRepairPath = sameRepairSemantics(collectorOpenSignature,
                loadPathSignature);
            String relation = separated ? "SEPARATED" : (sameLegalRepairPath ?
                "EQUIVALENT_REPAIR_CLASS_NPN_CE_OPEN_LOAD_PATH_OPEN" :
                "UNRESOLVED_REPAIR_SEMANTICS");
            return "NPN_LOAD_PATH_OPEN_DEVELOPER_ONLY;NPN_CE_OPEN_VS_LOAD_PATH_OPEN=" +
                relation + ";NPN_EXCLUSION_SAME_LAYOUT;NPN_EXCLUSION_SAMPLE_TOLERANCES=" +
                signatureEvidence("NPN_CE_OPEN", collectorOpenSignature) + "/" +
                signatureEvidence("NPN_LOAD_PATH_OPEN", loadPathSignature) +
                ";NPN_EXCLUSION_REPAIR_SEMANTICS=" +
                collectorOpenSignature.getRepairSemantics().stableDescription() + "/" +
                loadPathSignature.getRepairSemantics().stableDescription();
        } finally {
            try {
                try {
                    sim.instrumentController.clearTargets();
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                } finally {
                    if (sim.activeMeasurementOverlay)
                        throw new IllegalStateException(
                            "Task 41 NPN exclusion comparison left measurement overlay");
                }
            } finally {
                priorSnapshot.restore(sim);
                priorSnapshot.assertRestored(sim);
            }
        }
    }

    private static GeneratedDiagnosticPlan planFor(GeneratedBoardInstance instance) {
        return instance.getDiagnosticProvider().getDiagnosticPlan();
    }

    private static GeneratedDiagnosticPlan planFor(String familyId) {
        return QuickPlayFamilyRegistry.generate(familyId, 0).getDiagnosticProvider().getDiagnosticPlan();
    }

    private static int admittedNormalCorpusCount() {
        int count = 0;
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        for (String familyId : families) {
            if (isControlledIndicatorFamily(familyId)) continue;
            count += normalAdmittedCandidates(familyId, 0).size();
        }
        return count;
    }

    private static void verifyOwnerDiversityClassification() {
        for (String familyId : QuickPlayFamilyRegistry.getNormalPlayerFamilyIds()) {
            if (isControlledIndicatorFamily(familyId)) continue;
            GeneratedBoardInstance representative =
                QuickPlayFamilyRegistry.generate(familyId, 0);
            GeneratedDiagnosticOwnerDiversity actual = representative
                .getDiagnosticSolvabilityContract().getOwnerDiversity();
            GeneratedDiagnosticOwnerDiversity derived = GeneratedDiagnosticSolvabilityAdmission
                .getOwnerDiversity(representative.getFaultCandidates());
            require(actual == derived,
                "Task 41 owner-diversity contract is not derived for " + familyId);
            if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId) ||
                    QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId) ||
                    QuickPlayFamilyRegistry.RELAY_OUTPUT.equals(familyId) || Rb15Plan.FAMILY_ID.equals(familyId))
                require(actual == GeneratedDiagnosticOwnerDiversity.MULTI_OWNER_DIAGNOSTIC,
                    "Task 41 expected multi-owner family was classified as single-owner: " +
                        familyId);
            else
                require(actual == GeneratedDiagnosticOwnerDiversity.GUIDED_EASY_SINGLE_OWNER,
                    "Task 41 expected single-owner family was classified as multi-owner: " +
                        familyId);
        }
    }

    private static Vector<Route> normalRoutes() {
        Vector<Route> routes = new Vector<Route>();
        for (String familyId : QuickPlayFamilyRegistry.getNormalPlayerFamilyIds()) {
            if (isControlledIndicatorFamily(familyId)) continue;
            GeneratedBoardInstance representative = QuickPlayFamilyRegistry.generate(familyId, 0);
            for (GeneratedFaultCandidate candidate : GeneratedFaultServiceabilityAdmission
                    .getAdmittedCandidates(representative.getFaultCandidates())) {
                long seed = normalFixtureSeed(familyId, candidate.getFault().getType());
                // Preserve the established seed/type corpus while requiring the
                // exact semantic hypothesis in that seed's admitted catalog.
                GeneratedFaultCandidate selected = GeneratedFaultEngine.selectHypothesis(
                    candidate.getHypothesisKey(), normalAdmittedCandidates(familyId, seed));
                routes.add(new Route(familyId, seed, selected.getFault().getType(),
                    selected.getFault().getTargetComponentId(),
                    selected.getHypothesisKey()));
            }
        }
        return routes;
    }

    private static long normalFixtureSeed(String familyId, GeneratedFaultType type) {
        if (QuickPlayFamilyRegistry.RELAY_OUTPUT.equals(familyId)) {
            if (type == GeneratedFaultType.RELAY_CONTACT_OPEN) return 2;
            if (type == GeneratedFaultType.BASE_RESISTOR_OPEN) return 4;
        }
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(familyId) &&
                type == GeneratedFaultType.CAPACITOR_SHORT) return 2;
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId)) {
            if (type == GeneratedFaultType.TRANSISTOR_CE_SHORT) return 1;
            if (type == GeneratedFaultType.BASE_RESISTOR_OPEN) return 2;
        }
        if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(familyId)) {
            if (type == GeneratedFaultType.NMOS_DS_SHORT) return 1;
            if (type == GeneratedFaultType.NMOS_GATE_OPEN) return 2;
        }
        return 0;
    }

    private static Vector<GeneratedFaultCandidate> normalAdmittedCandidates(String familyId,
            long seed) {
        GeneratedBoardInstance representative = QuickPlayFamilyRegistry.generate(familyId, seed);
        Vector<GeneratedFaultCandidate> admitted =
            GeneratedFaultServiceabilityAdmission.getAdmittedCandidates(
                representative.getFaultCandidates());
        if (admitted.isEmpty())
            throw new IllegalStateException("Task 41 family has no admitted hypotheses: " +
                familyId + "/" + seed);
        return admitted;
    }

    private static void settleReady(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task41-route-" + instance.getCircuitFamilyId());
    }

    private static Vector<String> singleton(String value) {
        Vector<String> result = new Vector<String>();
        result.add(value);
        return result;
    }

    private static void appendUnique(Vector<String> destination, Vector<String> values) {
        for (String value : values)
            if (!destination.contains(value)) destination.add(value);
    }

    private static String join(Vector<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() != 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static String sampleEvidence(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        StringBuilder result = new StringBuilder();
        for (GeneratedDiagnosticSolvabilityEvidence route : evidence)
            for (GeneratedDiagnosticSample sample : route.getSolverSamples()) {
                if (result.length() != 0) result.append(",");
                result.append(route.getHypothesisKey()).append("@").append(route.getRouteId())
                    .append("@").append(route.getSeed())
                    .append("#").append(sample.getSampleId()).append("=")
                    .append(observationEvidence(sample));
            }
        return result.toString();
    }

    private static String signatureEvidence(String routeId, DiagnosticSignature signature) {
        StringBuilder result = new StringBuilder(routeId);
        for (GeneratedDiagnosticSample sample : signature.getSamples())
            result.append("#").append(sample.getSampleId()).append("=")
                .append(observationEvidence(sample));
        return result.toString();
    }

    private static String observationEvidence(GeneratedDiagnosticSample sample) {
        return sample.isOverRange() ? "OL" :
            sample.getValue() + "~" + sample.getComparisonTolerance();
    }

    private static String repairEquivalenceEvidence(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        StringBuilder result = new StringBuilder();
        for (GeneratedDiagnosticSolvabilityEvidence route : evidence) {
            if (result.length() != 0) result.append(",");
            result.append(route.getHypothesisKey()).append("@").append(route.getRouteId())
                .append("@").append(route.getSeed())
                .append("=").append(route.getEquivalentRepairClass()).append("|")
                .append(route.getRepairSemantics().stableDescription());
        }
        return result.toString();
    }

    private static boolean isControlledIndicatorFamily(String familyId) {
        return "COMPOSED_CONTROLLED_INDICATOR".equals(familyId);
    }

    private static String controlledComponentId(String block, String localId) {
        return ControlledIndicatorBlockContributions.componentId(
            ControlledIndicatorBlockContributions.namespace(), block, localId);
    }

    private static String controlledPadId(String block, String localId) {
        return ControlledIndicatorBlockContributions.padId(
            ControlledIndicatorBlockContributions.namespace(), block, localId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Route {
        final String familyId;
        final long seed;
        final GeneratedFaultType type;
        final String targetComponentId;
        final String hypothesisKey;
        Route(String familyId, long seed, GeneratedFaultType type,
                String targetComponentId, String hypothesisKey) {
            this.familyId = familyId; this.seed = seed; this.type = type;
            this.targetComponentId = targetComponentId; this.hypothesisKey = hypothesisKey;
        }
        GeneratedBoardInstance generate() {
            GeneratedBoardInstance source = QuickPlayFamilyRegistry.generate(familyId, seed);
            GeneratedFaultCandidate selected = hypothesisKey == null ?
                GeneratedFaultEngine.select(type, source.getFaultCandidates()) :
                GeneratedFaultEngine.selectHypothesis(hypothesisKey, source.getFaultCandidates());
            GeneratedBoardInstance result = source.getDiagnosticProvider().generateHypothesis(selected);
            require(selected.getHypothesisKey().equals(result.getFaultBinding().getFault().getHypothesisKey()),
                "Task 41 provider replay selected another hypothesis");
            return result;
        }
    }

    private static final class CandidateEvaluation {
        final GeneratedFaultType type;
        final String targetComponentId;
        final String hypothesisKey;
        final DiagnosticSignature signature;
        final GeneratedDiagnosticSolvabilityEvidence evidence;
        final String topologyVariantId;
        final String layoutFingerprint;

        CandidateEvaluation(GeneratedFaultType type, String targetComponentId,
                DiagnosticSignature signature,
                GeneratedDiagnosticSolvabilityEvidence evidence, String topologyVariantId,
                String layoutFingerprint) {
            this(type, targetComponentId, null, signature, evidence, topologyVariantId,
                layoutFingerprint);
        }

        CandidateEvaluation(GeneratedFaultType type, String targetComponentId,
                String hypothesisKey, DiagnosticSignature signature,
                GeneratedDiagnosticSolvabilityEvidence evidence, String topologyVariantId,
                String layoutFingerprint) {
            this.type = type;
            this.targetComponentId = targetComponentId;
            this.hypothesisKey = hypothesisKey;
            this.signature = signature;
            this.evidence = evidence;
            this.topologyVariantId = topologyVariantId;
            this.layoutFingerprint = layoutFingerprint;
        }
    }

    private static final class DiagnosticSignature {
        private final Vector<GeneratedDiagnosticSample> samples;
        private final String equivalentRepairClass;
        private final GeneratedDiagnosticRepairSemantics repairSemantics;

        DiagnosticSignature(Vector<GeneratedDiagnosticSample> samples,
                String equivalentRepairClass) {
            this(samples, equivalentRepairClass, null);
        }

        DiagnosticSignature(Vector<GeneratedDiagnosticSample> samples,
                String equivalentRepairClass,
                GeneratedDiagnosticRepairSemantics repairSemantics) {
            this.samples = new Vector<GeneratedDiagnosticSample>(samples);
            this.equivalentRepairClass = equivalentRepairClass;
            this.repairSemantics = repairSemantics;
        }

        Vector<GeneratedDiagnosticSample> getSamples() {
            return new Vector<GeneratedDiagnosticSample>(samples);
        }
        String getEquivalentRepairClass() { return equivalentRepairClass; }
        GeneratedDiagnosticRepairSemantics getRepairSemantics() { return repairSemantics; }
    }
}
