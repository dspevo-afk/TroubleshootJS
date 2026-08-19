package com.lushprojects.circuitjs1.client;

import java.util.Vector;

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
        Task41SimulationSnapshot originalSnapshot = Task41SimulationSnapshot.capture(sim);
        originalSnapshot.beginProof(sim);
        GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
        try {
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 proof attached a player workbench before candidate evaluation");
            Vector<Route> routes = normalRoutes();
            require(routes.size() == admittedNormalCorpusCount(),
                "Task 41 route/candidate corpus mismatch: routes=" + routes.size());
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
                    int selectedIndex = findEvaluationIndex(evaluations, routeInGroup.type);
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
                    String metric = routeEvidence.getRouteId() + "@" + routeEvidence.getSeed() +
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
    }

    static void verifyAdmissionRoute(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController ownerController) {
        require(owner != null && ownerController != null &&
                sim.getGeneratedBoardInstance() == owner &&
                sim.getGeneratedChallengeController() == ownerController,
            "Task 41 admission proof lost its current challenge owner");
        GeneratedDiagnosticSolvabilityAdmission.validate(sim, owner);
        Task41SimulationSnapshot ownerSnapshot = Task41SimulationSnapshot.capture(sim);
        ownerSnapshot.beginProof(sim);
        GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
        try {
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 admission proof attached a player workbench before candidate evaluation");
            Route route = new Route(owner.getCircuitFamilyId(), owner.getSeed(), null);
            Vector<CandidateEvaluation> evaluations = evaluateCandidateGroup(sim, route);
            Vector<String> equivalentClasses = classifyCandidateEquivalence(evaluations,
                owner.getCircuitFamilyId(), owner.getSeed());
            require(evaluations.size() ==
                    owner.getDiagnosticSolvabilityContract().getAdmittedCandidateCount(),
                "Task 41 admission candidate count changed during live proof");
            for (CandidateEvaluation evaluation : evaluations) {
                require(owner.getTopologyVariantId().equals(evaluation.topologyVariantId) &&
                        owner.getPcbLayout().geometryFingerprint().equals(
                            evaluation.layoutFingerprint),
                    "Task 41 live admission proof changed topology/layout");
                require(evaluation.evidence.isRepairReachable() &&
                        evaluation.evidence.isCustomerRetestPassed() &&
                        evaluation.evidence.isStateIsolated() &&
                        evaluation.evidence.hasUnaffectedFunctionRetestObservation(),
                    "Task 41 live admission repair/retest proof failed");
            }
            validateCandidateSeparation(evaluations, equivalentClasses,
                owner.getCircuitFamilyId(), owner.getSeed());
        } finally {
            try {
                try {
                    sim.instrumentController.clearTargets();
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                } finally {
                    require(!sim.activeMeasurementOverlay,
                        "Task 41 live admission left an active measurement overlay");
                }
            } finally {
                try {
                    ownerSnapshot.restore(sim);
                    ownerSnapshot.assertRestored(sim);
                } finally {
                    GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
                }
            }
        }
    }

    static Vector<GeneratedDiagnosticSolvabilityEvidence> getLastEvidenceForDeveloperVerification() {
        return lastEvidence == null ? new Vector<GeneratedDiagnosticSolvabilityEvidence>() :
            new Vector<GeneratedDiagnosticSolvabilityEvidence>(lastEvidence);
    }

    static String getLastNegativeRejectionReasonForDeveloperVerification() {
        return lastNegativeRejectionReason;
    }

    private static Vector<CandidateEvaluation> evaluateCandidateGroup(CirSim sim, Route route) {
        Vector<CandidateEvaluation> result = new Vector<CandidateEvaluation>();
        GeneratedFaultType[] types = candidateTypes(route.familyId);
        for (GeneratedFaultType type : types) {
            CandidateEvaluation evaluation = verifyCandidate(sim,
                new Route(route.familyId, route.seed, type), planFor(route.familyId));
            if (!result.isEmpty()) {
                CandidateEvaluation first = result.firstElement();
                require(first.topologyVariantId.equals(evaluation.topologyVariantId) &&
                        first.layoutFingerprint.equals(evaluation.layoutFingerprint),
                    "Task 41 candidate group changed topology/layout across candidates: " +
                        route.familyId + "/" + route.seed);
            }
            result.add(evaluation);
        }
        return result;
    }

    private static CandidateEvaluation verifyCandidate(CirSim sim, Route route,
            GeneratedDiagnosticPlan plan) {
        GeneratedBoardInstance evaluated = null;
        try {
            evaluated = route.generate();
            require(route.type == evaluated.getFaultBinding().getFault().getType(),
                "Task 41 route selected an unexpected fault family: " + route.familyId);
            require(!evaluated.isDeveloperOnlyFaultRoute(),
                "Task 41 normal route became developer-only: " + route.familyId);
            sim.installGeneratedChallengeForDeveloperVerification(evaluated);
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Task 41 candidate install attached a player workbench");
            settleReady(sim, evaluated);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, evaluated);
            GeneratedDiagnosticExecutionTrace.Builder trace =
                GeneratedDiagnosticExecutionTrace.builder();
            DiagnosticSignature signature = collectSolverSignature(sim, evaluated, plan, trace);
            sim.instrumentController.clearTargets();
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            require(!sim.activeMeasurementOverlay, "Task 41 measurement overlay survived signature capture");

            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            sim.updateCircuit();
            RepairRetestObservation repairObservation = performRealRepairAndRetest(sim,
                evaluated, trace);
            require(sim.getBoardModificationController().isFullyRestored() &&
                    sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                    !sim.activeMeasurementOverlay,
                "Task 41 repair/retest did not restore the live board state");
            GeneratedDiagnosticSolvabilityEvidence evidence = new GeneratedDiagnosticSolvabilityEvidence(
                evaluated.getCircuitFamilyId() + "/" + evaluated.getTopologyVariantId(),
                evaluated.getCircuitFamilyId(), evaluated.getSeed(),
                evaluated.getDiagnosticSolvabilityContract().getAdmittedCandidateCount(),
                evaluated.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount(),
                plan, signature.getSamples(), trace.freeze(signature.getRepairSemantics()),
                repairObservation.unaffectedFunctionRetestObservation,
                signature.getEquivalentRepairClass(), "PASS", "NONE",
                repairObservation.repairReachable, repairObservation.customerRetestPassed,
                repairObservation.stateIsolated);
            return new CandidateEvaluation(route.type, signature, evidence,
                evaluated.getTopologyVariantId(), evaluated.getPcbLayout().geometryFingerprint());
        } finally {
            try {
                try {
                    sim.instrumentController.clearTargets();
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                } finally {
                    if (sim.activeMeasurementOverlay)
                        throw new IllegalStateException(
                            "Task 41 candidate cleanup left measurement overlay");
                }
            } finally {
                if (evaluated != null)
                    restoreCandidateRoute(sim, route);
            }
        }
    }

    private static GeneratedFaultType[] candidateTypes(String familyId) {
        if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.RESISTOR_OPEN,
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
                GeneratedFaultType.LED_OPEN };
        if (QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.RESISTOR_OPEN,
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE };
        if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.DIODE_OPEN };
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.CAPACITOR_OPEN,
                GeneratedFaultType.CAPACITOR_SHORT };
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.TRANSISTOR_CE_OPEN,
                GeneratedFaultType.TRANSISTOR_CE_SHORT, GeneratedFaultType.BASE_RESISTOR_OPEN };
        if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(familyId))
            return new GeneratedFaultType[] { GeneratedFaultType.NMOS_DS_OPEN,
                GeneratedFaultType.NMOS_DS_SHORT, GeneratedFaultType.NMOS_GATE_OPEN };
        throw new IllegalArgumentException("No Task 41 candidate catalog for " + familyId);
    }

    private static CandidateEvaluation findEvaluation(Vector<CandidateEvaluation> evaluations,
            GeneratedFaultType type) {
        return evaluations.get(findEvaluationIndex(evaluations, type));
    }

    private static int findEvaluationIndex(Vector<CandidateEvaluation> evaluations,
            GeneratedFaultType type) {
        for (int index = 0; index < evaluations.size(); index++)
            if (evaluations.get(index).type == type) return index;
        throw new IllegalStateException("Task 41 candidate was not evaluated: " + type);
    }

    private static boolean sameSignature(DiagnosticSignature left, DiagnosticSignature right) {
        if (left.samples.size() != right.samples.size()) return false;
        for (int index = 0; index < left.samples.size(); index++) {
            GeneratedDiagnosticSample first = left.samples.get(index);
            GeneratedDiagnosticSample second = right.samples.get(index);
            if (!first.getSampleId().equals(second.getSampleId())) return false;
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
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(instance.getCircuitFamilyId()))
            return collectRcTemporalSignature(sim, instance, plan, trace);

        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()) ||
                QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId())) {
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, sim);
            trace.recordInputPowerTransition(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH);
            appendDcSamples(sim, instance, plan, samples, "CONTROL_HIGH", trace);
            instance.invokeOperation(GeneratedBoardOperationIds.CONTROL_INPUT_LOW, sim);
            trace.recordInputPowerTransition(GeneratedBoardOperationIds.CONTROL_INPUT_LOW);
            appendDcSamples(sim, instance, plan, samples, "CONTROL_LOW", trace);
        } else {
            appendDcSamples(sim, instance, plan, samples, "STEADY_STATE", trace);
        }

        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        trace.recordInputPowerTransition("BOARD_POWER_OFF");
        sim.updateCircuit();
        String[] pair = isolationPair(instance.getCircuitFamilyId());
        ProbeTarget first = boardProbe(sim, instance, pair[0]);
        ProbeTarget second = boardProbe(sim, instance, pair[1]);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(first, second);
        addResistanceSample(sim, samples, "OHM_" + pair[0] + "_" + pair[1],
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), trace);
        sim.instrumentController.setContinuityProbesForDeveloperVerification(first, second);
        trace.recordMeterMode("CONTINUITY");
        addSample(samples, "CONTINUITY_" + pair[0] + "_" + pair[1],
            sim.instrumentController.isContinuityDetectedForDeveloperVerification() ? 1 : 0);
        if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(instance.getCircuitFamilyId())) {
            sim.instrumentController.setDiodeProbesForDeveloperVerification(first, second);
            trace.recordMeterMode("DIODE");
            addSample(samples, "DIODE_FORWARD_VOLTAGE",
                sim.getLastDiodeMeasurementVoltageForDeveloperVerification());
            addSample(samples, "DIODE_FORWARD_CURRENT",
                sim.getLastDiodeMeasurementCurrentForDeveloperVerification());
        }
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay, "Task 41 active meter transaction was not restored");
        return new DiagnosticSignature(samples, "NONE",
            GeneratedDiagnosticRepairSemantics.forServiceability(
                instance.getFaultServiceability()));
    }

    private static DiagnosticSignature collectRcTemporalSignature(CirSim sim,
            GeneratedBoardInstance instance, GeneratedDiagnosticPlan plan,
            GeneratedDiagnosticExecutionTrace.Builder trace) {
        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        RcDelayTemporalBehavior temporal = (RcDelayTemporalBehavior) instance.getTemporalBehavior();
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
        trace.recordInputPowerTransition("BOARD_POWER_OFF_INITIAL");
        sim.advanceGeneratedTemporalProfile(.120);
        trace.recordTemporalWaitSample("RC_RESIDUAL_SAMPLE", .120);
        addSample(samples, "RC_RESIDUAL_SAMPLE", measureDc(sim, instance, "J2.1", "J2.2",
            trace));
        sim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
        trace.recordInputPowerTransition("RC_POWER_ON");
        temporal.advanceForDeveloperVerification(sim, .100);
        trace.recordTemporalWaitSample("RC_EARLY_SAMPLE", .100);
        addSample(samples, "RC_EARLY_SAMPLE", measureDc(sim, instance, "J2.1", "J2.2",
            trace));
        temporal.advanceForDeveloperVerification(sim, .700);
        trace.recordTemporalWaitSample("RC_LATE_SAMPLE", .700);
        addSample(samples, "RC_LATE_SAMPLE", measureDc(sim, instance, "J2.1", "J2.2",
            trace));
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        trace.recordInputPowerTransition("BOARD_POWER_OFF_FINAL");
        sim.updateCircuit();
        // The board has just undergone a real power transition.  Advance the
        // existing solver-backed temporal profile until the meter's stored-
        // energy policy has observed the powered-down state.
        sim.advanceGeneratedTemporalProfile(.800);
        trace.recordTemporalWaitSample("RC_POWER_OFF_SETTLE", .800);
        ProbeTarget positive = boardProbe(sim, instance, "C1.+");
        ProbeTarget negative = boardProbe(sim, instance, "C1.-");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(positive, negative);
        addResistanceSample(sim, samples, "OHM_C1+_C1-",
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), trace);
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay, "Task 41 RC measurement was not restored");
        return new DiagnosticSignature(samples, "NONE",
            GeneratedDiagnosticRepairSemantics.forServiceability(
                instance.getFaultServiceability()));
    }

    private static void appendDcSamples(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticPlan plan, Vector<GeneratedDiagnosticSample> samples,
            String prefix, GeneratedDiagnosticExecutionTrace.Builder trace) {
        for (String targetId : plan.getProbeTargetIds()) {
            if (targetId.equals(plan.getReferenceTargetId())) continue;
            addSample(samples, prefix + "_DC_" + targetId,
                measureDc(sim, instance, targetId, plan.getReferenceTargetId(), trace));
        }
    }

    private static void addSample(Vector<GeneratedDiagnosticSample> samples, String sampleId,
            double value) {
        require(!Double.isNaN(value) && !Double.isInfinite(value),
            "Task 41 solver returned a non-finite sample: " + sampleId);
        samples.add(new GeneratedDiagnosticSample(sampleId, value,
            Math.max(.01, Math.abs(value) * .02)));
    }

    /**
     * CircuitJS deliberately reports an open resistance as OL/infinity. Keep
     * that real meter decision while encoding its finite solver-derived lower
     * bound for immutable numeric signature comparison.
     */
    private static void addResistanceSample(CirSim sim,
            Vector<GeneratedDiagnosticSample> samples, String sampleId, double reading,
            GeneratedDiagnosticExecutionTrace.Builder trace) {
        trace.recordMeterMode("RESISTANCE");
        double value = reading;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
                "Task 41 non-finite resistance was not an open-circuit meter result: " +
                    sampleId);
            double current = sim.getLastResistanceTestCurrentForDeveloperVerification();
            require(!Double.isNaN(current) && !Double.isInfinite(current),
                "Task 41 open-circuit resistance had no finite solver current: " + sampleId);
            double boundedCurrent = Math.max(Math.abs(current),
                ResistanceMeasurementStimulus.MINIMUM_TEST_CURRENT);
            value = Math.abs(ResistanceMeasurementStimulus.TEST_VOLTAGE / boundedCurrent) -
                ResistanceMeasurementStimulus.INTERNAL_RESISTANCE;
        }
        addSample(samples, sampleId, value);
    }

    private static double measureDc(CirSim sim, GeneratedBoardInstance instance,
            String redId, String blackId, GeneratedDiagnosticExecutionTrace.Builder trace) {
        ProbeTarget red = boardProbe(sim, instance, redId);
        ProbeTarget black = boardProbe(sim, instance, blackId);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(red, black);
        trace.recordMeterMode("DC_VOLTAGE");
        double reading = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(!Double.isNaN(reading) && !Double.isInfinite(reading),
            "Task 41 solver returned a non-finite DC sample: " + redId);
        return reading;
    }

    private static ProbeTarget boardProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        require(instance.getBoard().getPad(padId) != null && renderer.hasPad(padId),
            "Task 41 route lacks rendered probe target: " + padId);
        BoardPadProbeTarget target = new BoardPadProbeTarget(sim, instance, padId, renderer);
        require(target.isValid(), "Task 41 probe target is not valid: " + padId);
        return target;
    }

    private static String[] isolationPair(String familyId) {
        if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(familyId))
            return new String[] { "D1.A", "D1.K" };
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(familyId))
            return new String[] { "C1.+", "C1.-" };
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId))
            return new String[] { "Q1.B", "Q1.C" };
        if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(familyId))
            return new String[] { "Q1.G", "Q1.D" };
        return new String[] { "R1.1", "R1.2" };
    }

    private static RepairRetestObservation performRealRepairAndRetest(CirSim sim,
            GeneratedBoardInstance instance, GeneratedDiagnosticExecutionTrace.Builder trace) {
        String componentId = instance.getFaultLocus().getComponentId();
        PhysicalPart<?> original = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        require(original != null && original.isInstalled(),
            "Task 41 physical fault owner is not installed: " + componentId);
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original));
        trace.recordRepairAction(WorkbenchOperation.REMOVE);
        String catalogId = correctCatalogId(instance, componentId);
        dispatch(sim, WorkbenchOperation.forCatalog(componentId, catalogId));
        trace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
        require(instance.getPhysicalBoardRuntime().getInstalledPart(componentId) != original,
            "Task 41 replacement reused the faulted physical owner");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        boolean repairReachable = challenge.getRepairStatus() ==
            GeneratedRepairStatus.CORRECTLY_RESTORED;
        require(repairReachable,
            "Task 41 correct physical repair did not restore solver behavior");
        GeneratedCustomerRetestResult retest = challenge.performCustomerRetest();
        trace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
        boolean customerRetestPassed = retest != null && retest.isPassed();
        require(customerRetestPassed,
            "Task 41 legal repair did not pass CUSTOMER_RETEST");
        boolean stateIsolated = !sim.activeMeasurementOverlay &&
            sim.getBoardModificationController().isFullyRestored() &&
            sim.getBoardPowerController().getState() == BoardPowerState.POWERED;
        boolean unaffectedFunctionRetestObservation = customerRetestPassed &&
            challenge.getCustomerRetestResult() == retest && repairReachable && stateIsolated;
        require(stateIsolated,
            "Task 41 CUSTOMER_RETEST changed physical state");
        return new RepairRetestObservation(repairReachable,
            unaffectedFunctionRetestObservation, customerRetestPassed, stateIsolated);
    }

    private static void dispatch(CirSim sim, WorkbenchOperation operation) {
        require(sim.pcbWorkbenchController != null &&
                sim.pcbWorkbenchController.isAvailable(operation),
            "Task 41 legal workbench action unavailable: " + operation.getId());
        require(sim.pcbWorkbenchController.dispatch(operation),
            "Task 41 legal workbench action failed: " + operation.getId());
    }

    private static String correctCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("C1".equals(componentId)) return CapacitorReplacementCatalog.CORRECT;
        if ("D1".equals(componentId)) return DiodeReplacementCatalog.CORRECT;
        if ("LED1".equals(componentId)) return LedReplacementCatalog.CORRECT;
        if ("Q1".equals(componentId))
            return QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(instance.getCircuitFamilyId()) ?
                NmosReplacementCatalog.CORRECT : NpnReplacementCatalog.CORRECT;
        PhysicalSpecification specification = instance.getPhysicalSpecifications()
            .getSpecification(componentId);
        require(specification instanceof ResistorNameplate,
            "Task 41 has no deterministic replacement for " + componentId);
        return "R_CATALOG_" + (long)((ResistorNameplate) specification)
            .getNominalResistanceOhms();
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
        evaluations.add(new CandidateEvaluation(GeneratedFaultType.RESISTOR_OPEN,
            new DiagnosticSignature(samples, "NONE"), null, "NEGATIVE", "NEGATIVE"));
        evaluations.add(new CandidateEvaluation(GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
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
            evaluations.add(new CandidateEvaluation(GeneratedFaultType.TRANSISTOR_CE_OPEN,
                collectorOpenSignature, null,
                collectorOpen.getTopologyVariantId(),
                collectorOpen.getPcbLayout().geometryFingerprint()));
            evaluations.add(new CandidateEvaluation(GeneratedFaultType.BASE_RESISTOR_OPEN,
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
        sim.updateCircuit();
        PhysicalPart<?> baseResistor = instance.getPhysicalBoardRuntime().getInstalledPart("RB");
        require(baseResistor != null && baseResistor.isInstalled(),
            "Task 41 negative repair fixture has no installed NPN base resistor");
        // Remove the base path and lift the collector through the same real
        // player operations for both candidates; do not rewrite meter samples.
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, baseResistor));
        PhysicalPart<?> transistor = instance.getPhysicalBoardRuntime().getInstalledPart("Q1");
        require(transistor != null && transistor.isInstalled(),
            "Task 41 negative repair fixture has no installed NPN transistor");
        dispatch(sim, WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, transistor,
            "Q1", "Q1.C"));
        sim.analyzeCircuit();
        sim.updateCircuit();
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

    private static GeneratedDiagnosticPlan planFor(String familyId) {
        Vector<GeneratedDiagnosticPlan> plans = GeneratedDiagnosticPlanCatalog.forFamily(familyId);
        require(plans.size() == 1, "Task 41 plan catalog is not deterministic for " + familyId);
        return plans.firstElement();
    }

    private static int admittedNormalCorpusCount() {
        int count = 0;
        Vector<String> families = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        for (String familyId : families) {
            GeneratedBoardInstance representative =
                QuickPlayFamilyRegistry.generate(familyId, 0);
            count += GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(
                representative.getFaultCandidates());
        }
        return count;
    }

    private static void verifyOwnerDiversityClassification() {
        for (String familyId : QuickPlayFamilyRegistry.getNormalPlayerFamilyIds()) {
            GeneratedBoardInstance representative =
                QuickPlayFamilyRegistry.generate(familyId, 0);
            GeneratedDiagnosticOwnerDiversity actual = representative
                .getDiagnosticSolvabilityContract().getOwnerDiversity();
            GeneratedDiagnosticOwnerDiversity derived = GeneratedDiagnosticSolvabilityAdmission
                .getOwnerDiversity(representative.getFaultCandidates());
            require(actual == derived,
                "Task 41 owner-diversity contract is not derived for " + familyId);
            if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId) ||
                    QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId))
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
        routes.add(new Route(QuickPlayFamilyRegistry.LED_INDICATOR, 0,
            GeneratedFaultType.RESISTOR_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.LED_INDICATOR, 0,
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE));
        routes.add(new Route(QuickPlayFamilyRegistry.LED_INDICATOR, 0,
            GeneratedFaultType.LED_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR, 0,
            GeneratedFaultType.DIODE_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR, 0,
            GeneratedFaultType.RESISTOR_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR, 0,
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE));
        routes.add(new Route(QuickPlayFamilyRegistry.RC_DELAY, 0,
            GeneratedFaultType.CAPACITOR_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.RC_DELAY, 2,
            GeneratedFaultType.CAPACITOR_SHORT));
        routes.add(new Route(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH, 0,
            GeneratedFaultType.TRANSISTOR_CE_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH, 1,
            GeneratedFaultType.TRANSISTOR_CE_SHORT));
        routes.add(new Route(QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH, 2,
            GeneratedFaultType.BASE_RESISTOR_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH, 0,
            GeneratedFaultType.NMOS_DS_OPEN));
        routes.add(new Route(QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH, 1,
            GeneratedFaultType.NMOS_DS_SHORT));
        routes.add(new Route(QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH, 2,
            GeneratedFaultType.NMOS_GATE_OPEN));
        return routes;
    }

    private static void settleReady(CirSim sim, GeneratedBoardInstance instance) {
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 14; attempt++) {
            sim.updateCircuit();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            if (challenge != null && challenge.isReady()) break;
        }
        require(sim.getGeneratedChallengeController() != null &&
                sim.getGeneratedChallengeController().isReady(),
            "Task 41 generated route did not reach READY: " + instance.getCircuitFamilyId());
    }

    private static void restoreCandidateRoute(CirSim sim, Route route) {
        GeneratedBoardInstance fresh = route.generate();
        sim.installGeneratedChallengeForDeveloperVerification(fresh);
        require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
            "Task 41 candidate restore attached a player workbench");
        settleReady(sim, fresh);
        require(fresh.getFaultBinding().isApplied() &&
                sim.getBoardModificationController().isFullyRestored() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
                !sim.activeMeasurementOverlay,
            "Task 41 candidate cleanup did not restore original fault state");
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
                result.append(route.getRouteId()).append("@").append(route.getSeed())
                    .append("#").append(sample.getSampleId()).append("=")
                    .append(sample.getValue()).append("~")
                    .append(sample.getComparisonTolerance());
            }
        return result.toString();
    }

    private static String signatureEvidence(String routeId, DiagnosticSignature signature) {
        StringBuilder result = new StringBuilder(routeId);
        for (GeneratedDiagnosticSample sample : signature.getSamples())
            result.append("#").append(sample.getSampleId()).append("=")
                .append(sample.getValue()).append("~")
                .append(sample.getComparisonTolerance());
        return result.toString();
    }

    private static String repairEquivalenceEvidence(
            Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        StringBuilder result = new StringBuilder();
        for (GeneratedDiagnosticSolvabilityEvidence route : evidence) {
            if (result.length() != 0) result.append(",");
            result.append(route.getRouteId()).append("@").append(route.getSeed())
                .append("=").append(route.getEquivalentRepairClass()).append("|")
                .append(route.getRepairSemantics().stableDescription());
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Route {
        final String familyId;
        final long seed;
        final GeneratedFaultType type;

        Route(String familyId, long seed, GeneratedFaultType type) {
            this.familyId = familyId;
            this.seed = seed;
            this.type = type;
        }

        GeneratedBoardInstance generate() {
            if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId))
                return new LedIndicatorGenerator().generateForFaultVerification(seed, type);
            if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(familyId))
                return new DiodeProtectedIndicatorGenerator().generate(seed);
            if (QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR.equals(familyId))
                return new ParallelDualIndicatorGenerator().generateForFaultVerification(seed, type);
            if (QuickPlayFamilyRegistry.RC_DELAY.equals(familyId))
                return new RcDelayGenerator().generateForFaultVerification(seed, type);
            if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId))
                return new NpnLowSideSwitchGenerator().generateForDiagnosticSolvability(seed, type);
            if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(familyId))
                return new NmosLowSideSwitchGenerator().generateForFaultVerification(seed, type);
            throw new IllegalArgumentException("Unknown Task 41 route family: " + familyId);
        }
    }

    private static final class CandidateEvaluation {
        final GeneratedFaultType type;
        final DiagnosticSignature signature;
        final GeneratedDiagnosticSolvabilityEvidence evidence;
        final String topologyVariantId;
        final String layoutFingerprint;

        CandidateEvaluation(GeneratedFaultType type, DiagnosticSignature signature,
                GeneratedDiagnosticSolvabilityEvidence evidence, String topologyVariantId,
                String layoutFingerprint) {
            this.type = type;
            this.signature = signature;
            this.evidence = evidence;
            this.topologyVariantId = topologyVariantId;
            this.layoutFingerprint = layoutFingerprint;
        }
    }

    private static final class RepairRetestObservation {
        final boolean repairReachable;
        final boolean unaffectedFunctionRetestObservation;
        final boolean customerRetestPassed;
        final boolean stateIsolated;

        RepairRetestObservation(boolean repairReachable,
                boolean unaffectedFunctionRetestObservation, boolean customerRetestPassed,
                boolean stateIsolated) {
            this.repairReachable = repairReachable;
            this.unaffectedFunctionRetestObservation = unaffectedFunctionRetestObservation;
            this.customerRetestPassed = customerRetestPassed;
            this.stateIsolated = stateIsolated;
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
