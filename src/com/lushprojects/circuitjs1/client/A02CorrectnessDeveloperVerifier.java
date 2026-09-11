package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/** Bounded A02 compiled proof; ordinary visible player actions are a separate gate. */
final class A02CorrectnessDeveloperVerifier {
    private A02CorrectnessDeveloperVerifier() { }

    static String verify(CirSim sim, boolean forcedFailure) {
        require(sim != null && sim.troubleshootDebug && sim.developerVerifierRunning,
            "explicit developer route required");
        if (forcedFailure) throw new IllegalStateException("a02-explicit-failure-canary");
        GeneratedFault keyFixture = new GeneratedFault("R1_VALUE",
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE, "R1", "A02_KEY", 0, 330, 33000);
        require(keyFixture.getHypothesisKey().equals("fault-hypothesis-v1|7:A02_KEY" +
            "24:RESISTOR_INCORRECT_VALUE2:R18:R1_VALUE" +
            "19:464451303758626816019:4674768299047780352"),
            "GWT hypothesis numeric identity differs from IEEE-754 fixture");
        GeneratedBoardInstance owner = sim.getGeneratedBoardInstance();
        require(owner != null && !owner.isDeveloperOnlyFaultRoute() &&
            sim.getGeneratedChallengeController().isReady(),
            "ready normal owner required");
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "a02-entry");
        require(sim.isGeneratedRuntimeSettled(), "settled normal owner required");
        require(owner.getPcbLayout().getLayoutAlgorithmVersion() ==
            SeededPcbLayoutGenerator.CURRENT_VERSION,
            "ordinary generation did not reach current geometry");
        Task41SimulationSnapshot original = Task41SimulationSnapshot.capture(sim);
        Vector<GeneratedBoardInstance> detached = new Vector<GeneratedBoardInstance>();
        String result = null;
        Throwable primary = null;
        try {
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, owner);
            GeneratedDiagnosticProofService.prove(sim, owner,
                sim.getGeneratedChallengeController());
            original.assertRestored(sim);
            Vector<GeneratedDiagnosticSolvabilityEvidence> proof =
                sim.getGeneratedChallengeController().getDiagnosticProofEvidence();
            Vector<String> provedKeys = new Vector<String>();
            for (GeneratedDiagnosticSolvabilityEvidence item : proof) {
                require(item.isRepairReachable() && item.isCustomerRetestPassed() &&
                    item.isStateIsolated(), "diagnostic hypothesis lacks repair/retest/restoration");
                require(!provedKeys.contains(item.getHypothesisKey()), "duplicate proved hypothesis");
                provedKeys.add(item.getHypothesisKey());
            }
            Collections.sort(provedKeys);
            Vector<String> admittedKeys = owner.getDiagnosticSolvabilityContract().getHypothesisKeys();
            Collections.sort(admittedKeys);
            require(provedKeys.equals(admittedKeys), "admitted/proved population divergence");
            String selectedKey = owner.getFaultBinding().getFault().getHypothesisKey();
            require(provedKeys.contains(selectedKey), "selected hypothesis absent from exact proof");
            verifyUnserviceableExclusion(owner);

            ChallengeDescriptor descriptor = ChallengeDescriptor.parse(
                ChallengeDescriptor.current(owner.getCircuitFamilyId(), owner.getSeed())
                    .toCanonical());
            GeneratedBoardInstance replay = LeafChallengeReplay.generate(descriptor);
            detached.add(replay);
            require(replay != owner, "replay reused a mutable owner");
            replay.getPcbLayout().validateGeometry(replay.getBoard());
            require(replay.getPcbLayout().getLayoutAlgorithmVersion() ==
                SeededPcbLayoutGenerator.CURRENT_VERSION,
                "current descriptor did not select current geometry algorithm");
            require(replay.getPcbLayout().geometryFingerprint().equals(
                owner.getPcbLayout().geometryFingerprint()), "normal/replayed geometry differs");
            require(selectedKey.equals(replay.getFaultBinding().getFault().getHypothesisKey()),
                "current replay changed the retained fault selection");
            sim.installGeneratedChallengeForDeveloperVerification(replay);
            GeneratedRuntimeDeveloperSettlement.settle(sim, replay, "a02-current-replay");
            GeneratedDiagnosticProofService.prove(sim, replay,
                sim.getGeneratedChallengeController());
            require(sim.getGeneratedChallengeController().getDiagnosticProofEvidence()
                .size() == admittedKeys.size(), "current replay lost admitted hypotheses");
            original.restore(sim);
            original.assertRestored(sim);
            result = "{\"protocol\":\"TSJ-A02-2\",\"status\":\"PASS\",\"family\":" +
                q(owner.getCircuitFamilyId()) + ",\"seed\":" + q(Long.toString(owner.getSeed())) +
                ",\"hypothesisCount\":" + admittedKeys.size() + ",\"physicalOwnerCount\":" +
                owner.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount() +
                ",\"admittedKeys\":" + strings(admittedKeys) + ",\"provedKeys\":" + strings(provedKeys) +
                ",\"selectedHypothesisKey\":" + q(selectedKey) +
                ",\"compatibleUnserviceableExcluded\":true,\"crossRuntimeHypothesisKey\":true," +
                "\"current\":" + geometry(replay, descriptor) +
                ",\"normalGeometryMatchesCurrentReplay\":true," +
                "\"geometryAndProbeValidation\":true,\"currentAdmissionProof\":true," +
                "\"originalOwnerRestored\":true," +
                "\"candidateCleanup\":\"PASS\"}";
        } catch (Throwable failure) {
            primary = failure;
        } finally {
            try {
                original.restore(sim);
                for (GeneratedBoardInstance candidate : detached) {
                    require(candidate != sim.getGeneratedBoardInstance(), "cannot dispose active owner");
                    candidate.getExternalPowerBindings().setConnected(false);
                    for (CircuitElm element : candidate.getSimulationElements()) element.delete();
                }
                original.assertRestored(sim);
            } catch (Throwable cleanup) {
                throw new IllegalStateException("A02 cleanup failed" + (primary == null ? "" :
                    " after " + primary.getMessage()) + ": " + cleanup.getMessage(), cleanup);
            }
        }
        if (primary instanceof Error) throw (Error) primary;
        if (primary instanceof RuntimeException) throw (RuntimeException) primary;
        if (primary != null) throw new IllegalStateException("A02 verification failed", primary);
        return result;
    }

    private static void verifyUnserviceableExclusion(GeneratedBoardInstance owner) {
        Vector<GeneratedFaultCandidate> mixed = owner.getFaultCandidates();
        GeneratedFaultCandidate admitted = GeneratedFaultEngine.select(0L, mixed);
        GeneratedFault original = admitted.getFault();
        GeneratedFault badFault = new GeneratedFault("A02_UNSERVICEABLE", original.getType(),
            original.getTargetComponentId(), original.getCircuitFamilyId(), owner.getSeed(),
            original.getHealthyValue(), original.getEffectiveValue());
        GeneratedFaultCandidate bad = new GeneratedFaultCandidate(new GeneratedFaultBinding(
            badFault, admitted.getBinding().getEffect(), null), true);
        mixed.insertElementAt(bad, 0);
        require(!GeneratedFaultServiceabilityAdmission.isAdmitted(bad), "unserviceable candidate admitted");
        int count = GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(mixed);
        require(count == owner.getDiagnosticSolvabilityContract().getAdmittedCandidateCount(),
            "unserviceable candidate entered count");
        for (int seed = 0; seed < count; seed++)
            require(GeneratedFaultEngine.select((long) seed, mixed) != bad,
                "unserviceable candidate entered selection");
        require(GeneratedFaultEngine.selectHypothesis(original.getHypothesisKey(), mixed) == admitted,
            "exact normal hypothesis selection drifted");
    }

    private static String geometry(GeneratedBoardInstance board, ChallengeDescriptor descriptor) {
        PcbBoardLayout layout = board.getPcbLayout();
        int bends = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) bends += layout.getTraceBendCount(trace);
        return "{\"descriptor\":" + q(descriptor.toCanonical()) +
            ",\"packageGeometryVersion\":3,\"layoutAlgorithmVersion\":" +
            layout.getLayoutAlgorithmVersion() + ",\"fingerprint\":" + q(layout.geometryFingerprint()) +
            ",\"directionBends\":" + bends + ",\"routeQualityScore\":" +
            layout.getRouteQualityScore(board.getBoard()) + "}";
    }

    private static String strings(Vector<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (String value : values) {
            if (result.length() > 1) result.append(',');
            result.append(q(value));
        }
        return result.append(']').toString();
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("A02: " + label);
    }
}
