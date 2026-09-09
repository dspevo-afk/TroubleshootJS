package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused F3 regressions against the production candidate/admission seams. */
public final class A02CandidateContractTest {
    private static final String FAMILY = "A02_F3_FIXTURE";

    public static void main(String[] args) {
        testPredicateCases();
        testMixedSelectionUsesAdmittedPopulation();
        testEmptyPopulationFailsExplicitly();
        testSameOwnerHypothesesRemainDistinct();
        testDuplicateAndConflictingKeysFailClosed();
        testContractAndProofPopulationUseKeys();
        testCanonicalKeyIsRuntimeStable();
        testActualRouteRegenerationAndLayoutVersion();
        testCurrentDiagnosticCorpus();
        testProofPopulationDivergenceFailsClosed();
        System.out.println("PASS: A02CandidateContractTest");
    }

    private static void testPredicateCases() {
        require(!GeneratedFaultServiceabilityAdmission.isAdmitted(null),
            "null candidate was admitted");
        GeneratedFaultServiceability valid = validServiceability("R1");
        GeneratedFault fault = fault("F3_VALID", GeneratedFaultType.RESISTOR_OPEN, "R1");
        GeneratedFaultCandidate incompatible = candidate(fault, valid, false);
        require(!incompatible.isAdmitted() &&
                !GeneratedFaultServiceabilityAdmission.isAdmitted(incompatible),
            "incompatible candidate was admitted");

        GeneratedFaultBinding missingBinding = new GeneratedFaultBinding(fault,
            new SwitchOpenFaultEffect(new SwitchElm(0, 0)), null);
        GeneratedFaultCandidate missing = new GeneratedFaultCandidate(missingBinding, true);
        require(!missing.isAdmitted() &&
                !GeneratedFaultServiceabilityAdmission.isAdmitted(missing),
            "candidate without serviceability was admitted");

        GeneratedFaultCandidate noRepair = candidate(fault, nonAdmissibleServiceability("R1"), true);
        require(!noRepair.isAdmitted() &&
                !GeneratedFaultServiceabilityAdmission.isAdmitted(noRepair),
            "candidate with non-admissible serviceability was admitted");

        GeneratedFaultCandidate admitted = candidate(fault, valid, true);
        require(admitted.isAdmitted() &&
                GeneratedFaultServiceabilityAdmission.isAdmitted(admitted),
            "compatible candidate with admissible serviceability was rejected");
    }

    private static void testMixedSelectionUsesAdmittedPopulation() {
        Vector<GeneratedFaultCandidate> mixed = new Vector<GeneratedFaultCandidate>();
        mixed.add(candidate(fault("F3_BAD_OPEN", GeneratedFaultType.RESISTOR_OPEN, "RBAD"),
            nonAdmissibleServiceability("RBAD"), true));
        GeneratedFaultCandidate good = candidate(
            fault("F3_GOOD_OPEN", GeneratedFaultType.RESISTOR_OPEN, "RGOOD"),
            validServiceability("RGOOD"), true);
        mixed.add(good);

        require(GeneratedFaultServiceabilityAdmission.getAdmittedCandidateCount(mixed) == 1,
            "mixed candidate count included an unserviceable candidate");
        require(GeneratedFaultServiceabilityAdmission.getPhysicalOwnerCount(mixed) == 1,
            "mixed owner count included an unserviceable candidate");
        require(GeneratedFaultEngine.select(0, mixed) == good,
            "seeded selection did not exclude an unserviceable candidate");
        require(GeneratedFaultEngine.select(GeneratedFaultType.RESISTOR_OPEN, mixed) == good,
            "type selection did not exclude an unserviceable candidate");
        require(GeneratedFaultEngine.selectHypothesis(good.getHypothesisKey(), mixed) == good,
            "hypothesis selection did not use the admitted population");
    }

    private static void testEmptyPopulationFailsExplicitly() {
        try {
            GeneratedFaultEngine.select(0, new Vector<GeneratedFaultCandidate>());
            throw new AssertionError("empty admitted population was selectable");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().indexOf("No serviceable") >= 0,
                "empty selection rejection was not explicit");
        }
        Vector<GeneratedFaultCandidate> onlyInvalid = new Vector<GeneratedFaultCandidate>();
        onlyInvalid.add(candidate(fault("F3_INVALID", GeneratedFaultType.RESISTOR_OPEN, "R1"),
            nonAdmissibleServiceability("R1"), true));
        try {
            GeneratedFaultEngine.select(0, onlyInvalid);
            throw new AssertionError("all-invalid population was selectable");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().indexOf("No serviceable") >= 0,
                "all-invalid selection rejection was not explicit");
        }
    }

    private static void testSameOwnerHypothesesRemainDistinct() {
        GeneratedFaultCandidate open = candidate(
            fault("F3_R1_OPEN", GeneratedFaultType.RESISTOR_OPEN, "R1"),
            validServiceability("R1"), true);
        GeneratedFaultCandidate incorrect = candidate(
            new GeneratedFault("F3_R1_INCORRECT", GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
                "R1", FAMILY, 17, 330, 33000), validServiceability("R1"), true);
        require(!open.getHypothesisKey().equals(incorrect.getHypothesisKey()),
            "same-owner hypotheses share a semantic key");
        Vector<GeneratedFaultCandidate> candidates = vector(open, incorrect);
        require(GeneratedFaultServiceabilityAdmission.getAdmittedCandidateCount(candidates) == 2,
            "same-owner hypotheses were collapsed in the count");
        require(GeneratedFaultServiceabilityAdmission.getPhysicalOwnerCount(candidates) == 1,
            "same-owner hypotheses changed physical-owner count");
        require(GeneratedFaultEngine.selectHypothesis(open.getHypothesisKey(), candidates) == open &&
                GeneratedFaultEngine.selectHypothesis(incorrect.getHypothesisKey(), candidates) == incorrect,
            "same-owner hypotheses were not independently selectable");
        Vector<String> keys = GeneratedFaultServiceabilityAdmission.getHypothesisKeys(candidates);
        require(keys.size() == 2 && keys.contains(open.getHypothesisKey()) &&
                keys.contains(incorrect.getHypothesisKey()),
            "same-owner hypotheses disappeared from the proof population");
    }

    private static void testDuplicateAndConflictingKeysFailClosed() {
        GeneratedFault duplicateFault = fault("F3_DUPLICATE", GeneratedFaultType.RESISTOR_OPEN, "R1");
        Vector<GeneratedFaultCandidate> duplicates = vector(
            candidate(duplicateFault, validServiceability("R1"), true),
            candidate(fault("F3_DUPLICATE", GeneratedFaultType.RESISTOR_OPEN, "R1"),
                validServiceability("R1"), true));
        expectDuplicate(duplicates, "exact duplicate hypothesis key");

        // The fault identity is the same, while the physical serviceability
        // record points at a different owner.  The key collision is therefore
        // a conflicting record and must not be resolved by collection order.
        Vector<GeneratedFaultCandidate> conflict = vector(
            candidate(fault("F3_CONFLICT", GeneratedFaultType.RESISTOR_OPEN, "R1"),
                validServiceability("R1"), true),
            candidate(fault("F3_CONFLICT", GeneratedFaultType.RESISTOR_OPEN, "R1"),
                validServiceability("R2"), true));
        expectDuplicate(conflict, "conflicting hypothesis key");
    }

    private static void testContractAndProofPopulationUseKeys() {
        GeneratedFaultCandidate open = candidate(
            fault("F3_CONTRACT_OPEN", GeneratedFaultType.RESISTOR_OPEN, "R1"),
            validServiceability("R1"), true);
        GeneratedFaultCandidate incorrect = candidate(
            new GeneratedFault("F3_CONTRACT_INCORRECT",
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE, "R1", FAMILY, 0, 680, 68000),
            validServiceability("R1"), true);
        Vector<GeneratedFaultCandidate> candidates = vector(open, incorrect);
        GeneratedDiagnosticSolvabilityContract contract =
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(
                FAMILY, "A02_TOPOLOGY", 0, candidates);
        Vector<String> expected = GeneratedFaultServiceabilityAdmission.getHypothesisKeys(candidates);
        require(contract.getAdmittedCandidateCount() == 2 &&
                contract.getAdmittedPhysicalOwnerCount() == 1 &&
                contract.getHypothesisKeys().equals(expected),
            "contract population diverged from canonical admitted keys");

        Vector<GeneratedFaultCandidate> reversed = vector(incorrect, open);
        require(GeneratedFaultServiceabilityAdmission.getHypothesisKeys(reversed).equals(expected),
            "hypothesis identity depended on collection order");
    }

    private static void testCanonicalKeyIsRuntimeStable() {
        GeneratedFault fault = new GeneratedFault("F3_KEY_BITS",
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE, "R1", FAMILY, 0,
            330.0, 270.5);
        // The key deliberately uses IEEE-754 bits so this exact fixture has
        // one spelling under JDK8 and the bundled GWT2.7 emulation.
        require(fault.getHypothesisKey().equals(
            "fault-hypothesis-v1|14:A02_F3_FIXTURE24:RESISTOR_INCORRECT_VALUE" +
            "2:R111:F3_KEY_BITS19:464451303758626816019:4643466302516625408"),
            "hypothesis key used a runtime-dependent decimal spelling");
    }

    private static void testActualRouteRegenerationAndLayoutVersion() {
        // CircuitElm.drag uses the production simulator's grid owner.  Set up
        // the same minimal runtime context as the native geometry contract so
        // these calls exercise the real generators rather than a test model.
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~(sim.gridSize - 1);
        sim.gridRound = sim.gridSize / 2 - 1;
        CircuitElm.sim = sim;
        GeneratedBoardInstance current = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.LED_INDICATOR, 0);
        Vector<GeneratedBoardInstance> replays = new Vector<GeneratedBoardInstance>();
        try {
            testActualOwnerEnumeration(current);
            Vector<GeneratedFaultCandidate> currentCandidates =
                GeneratedFaultServiceabilityAdmission.getAdmittedCandidates(
                    current.getFaultCandidates());
            require(!currentCandidates.isEmpty(),
                "current production route had no admitted hypotheses");
            for (GeneratedFaultCandidate candidate : currentCandidates) {
                GeneratedBoardInstance replay = regenerate(current, candidate);
                replays.add(replay);
                require(replay.getPcbLayout().getLayoutAlgorithmVersion() ==
                        current.getPcbLayout().getLayoutAlgorithmVersion() &&
                    replay.getFaultBinding().getFault().getHypothesisKey().equals(
                        candidate.getHypothesisKey()),
                    "current proof route changed layout or hypothesis identity");
            }

        } finally {
            dispose(current);
            for (GeneratedBoardInstance replay : replays) dispose(replay);
        }
    }

    /** The current verifier corpus must retain every accepted seed/type case. */
    private static void testCurrentDiagnosticCorpus() {
        String[] expected = {
            "LED_INDICATOR/0/RESISTOR_OPEN", "LED_INDICATOR/0/RESISTOR_INCORRECT_VALUE",
            "LED_INDICATOR/0/LED_OPEN", "DIODE_PROTECTED_INDICATOR/0/DIODE_OPEN",
            "PARALLEL_DUAL_INDICATOR/0/RESISTOR_OPEN",
            "PARALLEL_DUAL_INDICATOR/0/RESISTOR_INCORRECT_VALUE",
            "RC_DELAY/0/CAPACITOR_OPEN", "RC_DELAY/2/CAPACITOR_SHORT",
            "NPN_LOW_SIDE_SWITCH/0/TRANSISTOR_CE_OPEN",
            "NPN_LOW_SIDE_SWITCH/1/TRANSISTOR_CE_SHORT",
            "NPN_LOW_SIDE_SWITCH/2/BASE_RESISTOR_OPEN",
            "NMOS_LOW_SIDE_SWITCH/0/NMOS_DS_OPEN",
            "NMOS_LOW_SIDE_SWITCH/1/NMOS_DS_SHORT", "NMOS_LOW_SIDE_SWITCH/2/NMOS_GATE_OPEN"
        };
        try {
            java.lang.reflect.Method normal = Task41DeveloperVerifier.class
                .getDeclaredMethod("normalRoutes");
            normal.setAccessible(true);
            Vector<?> routes = (Vector<?>) normal.invoke(null);
            Vector<String> actual = new Vector<String>();
            for (Object route : routes) {
                Class<?> routeClass = route.getClass();
                java.lang.reflect.Field family = routeClass.getDeclaredField("familyId");
                java.lang.reflect.Field seed = routeClass.getDeclaredField("seed");
                java.lang.reflect.Field type = routeClass.getDeclaredField("type");
                java.lang.reflect.Field key = routeClass.getDeclaredField("hypothesisKey");
                family.setAccessible(true); seed.setAccessible(true);
                type.setAccessible(true); key.setAccessible(true);
                String fixture = family.get(route) + "/" + seed.get(route) + "/" + type.get(route);
                require(!actual.contains(fixture), "duplicate corpus fixture: " + fixture);
                actual.add(fixture);
                java.lang.reflect.Method generate = routeClass.getDeclaredMethod("generate");
                generate.setAccessible(true);
                GeneratedBoardInstance regenerated = (GeneratedBoardInstance) generate.invoke(route);
                try {
                    require(key.get(route).equals(regenerated.getFaultBinding().getFault()
                        .getHypothesisKey()), "current corpus regenerated a different hypothesis");
                } finally { dispose(regenerated); }
            }
            require(actual.size() == expected.length, "current corpus size changed");
            for (String fixture : expected)
            require(actual.contains(fixture), "missing current corpus fixture: " + fixture);
        } catch (Exception failure) {
            throw new AssertionError("actual current Task41 corpus: " + failure);
        }
    }

    private static GeneratedBoardInstance regenerate(GeneratedBoardInstance owner,
            GeneratedFaultCandidate candidate) {
        try {
            Class<?> routeClass = Class.forName(
                "com.lushprojects.circuitjs1.client.Task41DeveloperVerifier$Route");
            java.lang.reflect.Constructor<?> constructor = routeClass.getDeclaredConstructor(
                String.class, long.class, GeneratedFaultType.class, String.class,
                BoundedAssemblyRequest.class, String.class);
            constructor.setAccessible(true);
            Object route = constructor.newInstance(owner.getCircuitFamilyId(), owner.getSeed(),
                candidate.getFault().getType(), candidate.getFault().getTargetComponentId(),
                null, candidate.getHypothesisKey());
            java.lang.reflect.Method generate = routeClass.getDeclaredMethod("generate");
            generate.setAccessible(true);
            return (GeneratedBoardInstance) generate.invoke(route);
        } catch (Exception failure) {
            throw new AssertionError("actual Task41 route regeneration: " + failure);
        }
    }

    /** Exercise Task41's actual non-null-owner branch with a disposable catalog. */
    @SuppressWarnings("unchecked")
    private static void testActualOwnerEnumeration(GeneratedBoardInstance owner) {
        Vector<GeneratedFaultCandidate> catalog = null;
        Vector<GeneratedFaultCandidate> saved = null;
        try {
            java.lang.reflect.Field catalogField = GeneratedBoardInstance.class
                .getDeclaredField("faultCandidates");
            catalogField.setAccessible(true);
            catalog = (Vector<GeneratedFaultCandidate>) catalogField.get(owner);
            saved = new Vector<GeneratedFaultCandidate>(catalog);
            Class<?> routeClass = Class.forName(
                "com.lushprojects.circuitjs1.client.Task41DeveloperVerifier$Route");
            java.lang.reflect.Constructor<?> constructor = routeClass.getDeclaredConstructor(
                String.class, long.class, GeneratedFaultType.class);
            constructor.setAccessible(true);
            Object route = constructor.newInstance(owner.getCircuitFamilyId(), owner.getSeed(), null);
            java.lang.reflect.Method enumerate = Task41DeveloperVerifier.class.getDeclaredMethod(
                "candidateRoutes", routeClass, GeneratedBoardInstance.class);
            enumerate.setAccessible(true);
            java.lang.reflect.Field keyField = routeClass.getDeclaredField("hypothesisKey");
            keyField.setAccessible(true);
            catalog.clear();
            for (GeneratedFaultCandidate candidate : saved)
                if ("R1".equals(candidate.getFault().getTargetComponentId()) && candidate.isAdmitted())
                    catalog.add(candidate);
            require(catalog.size() == 2 &&
                GeneratedFaultServiceabilityAdmission.getPhysicalOwnerCount(catalog) == 1,
                "real same-owner fixture must contain two supported hypotheses and one owner");
            GeneratedFaultCandidate bad = candidate(new GeneratedFault("F3_BAD_LIVE_OPEN",
                GeneratedFaultType.RESISTOR_OPEN, "R1", owner.getCircuitFamilyId(), owner.getSeed()),
                nonAdmissibleServiceability("R1"), true);
            catalog.add(0, bad);
            Vector<?> routes = (Vector<?>) enumerate.invoke(null, route, owner);
            Vector<String> actualKeys = new Vector<String>();
            for (Object candidateRoute : routes) actualKeys.add((String) keyField.get(candidateRoute));
            java.util.Collections.sort(actualKeys);
            require(actualKeys.equals(GeneratedFaultServiceabilityAdmission.getHypothesisKeys(catalog)) &&
                actualKeys.size() == 2, "actual proof enumeration lost same-owner hypotheses or admitted bad open");
            catalog.clear();
            catalog.add(bad);
            try {
                enumerate.invoke(null, route, owner);
                throw new AssertionError("empty admitted owner received a detached fallback");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                require(expected.getCause() instanceof IllegalStateException &&
                    expected.getCause().getMessage().contains("live owner has no admitted"),
                    "empty live population failed at the wrong boundary");
            }
        } catch (Exception failure) {
            throw new AssertionError("actual owner proof enumeration: " + failure);
        } finally {
            if (catalog != null && saved != null) { catalog.clear(); catalog.addAll(saved); }
        }
    }

    private static void testProofPopulationDivergenceFailsClosed() {
        GeneratedFaultCandidate open = candidate(
            fault("F3_PROOF_OPEN", GeneratedFaultType.RESISTOR_OPEN, "R1"),
            validServiceability("R1"), true);
        GeneratedFaultCandidate incorrect = candidate(
            new GeneratedFault("F3_PROOF_INCORRECT",
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE, "R1", FAMILY, 0,
                680, 68000), validServiceability("R1"), true);
        Vector<GeneratedFaultCandidate> candidates = vector(open, incorrect);
        Vector<String> missing = GeneratedFaultServiceabilityAdmission
            .getHypothesisKeys(candidates);
        missing.remove(0);
        expectPopulationDivergence(candidates, missing, "missing proof hypothesis");

        Vector<String> substituted = GeneratedFaultServiceabilityAdmission
            .getHypothesisKeys(candidates);
        substituted.set(0, "fault-hypothesis-v1|substituted");
        expectPopulationDivergence(candidates, substituted, "substituted proof hypothesis");

        Vector<String> duplicate = GeneratedFaultServiceabilityAdmission
            .getHypothesisKeys(candidates);
        duplicate.add(duplicate.firstElement());
        expectPopulationDivergence(candidates, duplicate, "duplicate proof hypothesis");
    }

    private static void expectPopulationDivergence(
            Vector<GeneratedFaultCandidate> candidates, Vector<String> proved, String label) {
        try {
            GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(candidates, proved);
            throw new AssertionError(label + " was silently accepted");
        } catch (IllegalArgumentException expected) {
            // Missing/duplicate keys are malformed proof receipts.
        } catch (IllegalStateException expected) {
            // A same-size but substituted population is a divergence.
        }
    }

    private static void dispose(GeneratedBoardInstance instance) {
        if (instance == null) return;
        instance.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : instance.getSimulationElements()) element.delete();
    }

    private static void expectDuplicate(Vector<GeneratedFaultCandidate> candidates, String label) {
        try {
            GeneratedFaultServiceabilityAdmission.getAdmittedCandidates(candidates);
            throw new AssertionError(label + " was silently accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().indexOf("Duplicate admitted hypothesis key") >= 0,
                label + " rejection was not deterministic");
        }
    }

    private static GeneratedFaultCandidate candidate(GeneratedFault fault,
            GeneratedFaultServiceability serviceability, boolean compatible) {
        return new GeneratedFaultCandidate(new GeneratedFaultBinding(fault,
            new SwitchOpenFaultEffect(new SwitchElm(0, 0)), serviceability), compatible);
    }

    private static GeneratedFault fault(String id, GeneratedFaultType type, String target) {
        return new GeneratedFault(id, type, target, FAMILY, 0);
    }

    private static GeneratedFaultServiceability validServiceability(String owner) {
        return new GeneratedFaultServiceability(GeneratedFaultLocus.componentInternal(owner),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
    }

    private static GeneratedFaultServiceability nonAdmissibleServiceability(String owner) {
        return new GeneratedFaultServiceability(GeneratedFaultLocus.componentInternal(owner),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE }, new String[0],
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
    }

    private static Vector<GeneratedFaultCandidate> vector(GeneratedFaultCandidate first,
            GeneratedFaultCandidate second) {
        Vector<GeneratedFaultCandidate> result = new Vector<GeneratedFaultCandidate>();
        result.add(first); result.add(second);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
