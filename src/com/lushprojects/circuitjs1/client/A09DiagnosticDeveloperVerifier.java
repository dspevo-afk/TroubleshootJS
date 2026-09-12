package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.user.client.Window;

/** Opt-in client of production admission; never consulted by ordinary challenges. */
final class A09DiagnosticDeveloperVerifier {
    private static int assertions, rejected;
    private interface Case { void run(); }
    private A09DiagnosticDeveloperVerifier() { }

    static void verifyIfRequested(final CirSim sim) {
        if (!sim.troubleshootDebug || !sim.troubleshootTask41Verification ||
                !"true".equals(Window.Location.getParameter("tsjVerifyA09"))) return;
        clearReport();
        if (!sim.developerVerifierRunning)
            throw new IllegalStateException("A09 requires explicit developer verification");
        if ("true".equals(Window.Location.getParameter("tsjForceA09Failure")))
            throw new IllegalStateException("a09-explicit-failure-canary");
        long started = System.currentTimeMillis();
        assertions = 0; rejected = 0;
        Vector<GeneratedDiagnosticSolvabilityEvidence> leaves =
            Task41DeveloperVerifier.getLastEvidenceForDeveloperVerification();
        require(leaves.size() == 14, "retained fourteen leaf hypotheses were actually executed");
        Vector<String> leafKeys = new Vector<String>();
        Vector<String> leafFamilies = new Vector<String>();
        for (GeneratedDiagnosticSolvabilityEvidence leaf : leaves) {
            String key = leaf.getFamilyId() + "/" + leaf.getSeed() + "/" + leaf.getHypothesisKey();
            require(!leafKeys.contains(key), "retained leaf corpus has no substituted duplicate hypothesis");
            leafKeys.add(key);
            if (!leafFamilies.contains(leaf.getFamilyId())) leafFamilies.add(leaf.getFamilyId());
        }
        require(leafFamilies.size() == 6, "all six production leaf provider contributions executed");
        int leafSamples = validateEvidence(leaves);
        int overRangeSamples = verifyResistanceOutcomes(leaves);
        Task41SimulationSnapshot entry = Task41SimulationSnapshot.capture(sim);
        Vector<String> variants = new Vector<String>();
        StringBuilder rows = new StringBuilder("[");
        int composedHypotheses = 0, composedSamples = 0;
        try {
            entry.beginProof(sim);
            GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
            for (long seed : new long[] { 0, 3 }) {
                final GeneratedBoardInstance board = BoundedGeneratedBoardAssembler.assemble(
                    BoundedAssemblyRequest.forControlledIndicator(seed)).getInstance();
                sim.installGeneratedChallengeForDeveloperVerification(board);
                GeneratedRuntimeDeveloperSettlement.settle(sim, board, "a09-composed-entry");
                final GeneratedChallengeController controller = sim.getGeneratedChallengeController();
                Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
                GeneratedDiagnosticProofReceipt receipt = GeneratedDiagnosticProofService.prove(sim, board, controller);
                before.assertRestored(sim);
                require(controller.getDiagnosticProofReceipt() == receipt, "receipt published only to its original controller");
                Vector<GeneratedDiagnosticSolvabilityEvidence> evidence = receipt.getEvidence();
                int samples = validateEvidence(evidence);
                ControlledIndicatorDeviceBehavior behavior = (ControlledIndicatorDeviceBehavior)board.getBehaviorContract();
                require(evidence.size() == behavior.getPlan().getDecisionOwners().size(), "all composed physical hypotheses executed");
                for (ControlledIndicatorChannel channel : behavior.getPlan().getChannels()) {
                    String variant = behavior.getPlan().getBlocks().get(channel.getDriverKey()).getDescriptor().getTypeId();
                    if (!variants.contains(variant)) variants.add(variant);
                }
                if (composedHypotheses != 0) rows.append(',');
                rows.append("{\"seed\":").append(seed).append(",\"hypotheses\":").append(evidence.size())
                    .append(",\"samples\":").append(samples).append(",\"serialProofMs\":").append(receipt.getElapsedMillis())
                    .append(",\"ownerRestored\":true,\"repairRetestPassed\":true}");
                composedHypotheses += evidence.size(); composedSamples += samples;
                receipt.getEvidence().clear();
                require(receipt.getEvidence().size() == evidence.size(), "receipt evidence is immutable");
                verifyReceiptRejection(sim, board, controller, receipt);
            }
            verifyProviderFailures(sim);
        } finally {
            try { entry.restore(sim); entry.assertRestored(sim); }
            finally { GeneratedDiagnosticSolvabilityAdmission.endInternalProof(); }
        }
        require(variants.size() == 3, "all three registered production composition variants exercised");
        require(sim.getGeneratedBoardInstance() != null && !sim.activeMeasurementOverlay &&
                !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning(), "A09 restored its entry graph and proof guard");
        publish("{\"protocol\":\"TSJ-A09-DIAGNOSTIC-1\",\"status\":\"PASS\",\"cleanup\":\"PASS\","
            + "\"assertions\":" + assertions + ",\"rejectedCases\":" + rejected
            + ",\"leafHypotheses\":" + leaves.size() + ",\"leafSamples\":" + leafSamples
            + ",\"overRangeSamples\":" + overRangeSamples
            + ",\"composedHypotheses\":" + composedHypotheses + ",\"composedSamples\":" + composedSamples
            + ",\"blockVariants\":" + variants.size() + ",\"wallMs\":" + (System.currentTimeMillis() - started)
            + ",\"serialProofs\":" + rows + "],\"singleUseReceipt\":true,\"staleReceiptRejected\":true,"
            + "\"emptyProofRejected\":true,\"developerFixtureRejected\":true,\"missingRepairRejected\":true,"
            + "\"answerDependentProgramRejected\":true,\"unavailableInputRejected\":true,\"failureOwnerRestored\":true}");
    }

    private static int verifyResistanceOutcomes(Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        int overRange = 0, numeric = 0;
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence)
            for (GeneratedDiagnosticSample sample : proof.getSolverSamples()) {
                if (!sample.getSampleId().startsWith("OHM_")) continue;
                if (sample.isOverRange()) overRange++;
                else {
                    require(sample.getValue() <= ResistanceInstrumentMode.MAX_RESISTANCE,
                        "compiled resistance evidence cannot reveal hidden over-range numbers");
                    numeric++;
                }
            }
        require(overRange > 0 && numeric > 0, "actual solver corpus exercises numeric and OL resistance outcomes");
        Vector<GeneratedDiagnosticSample> left = new Vector<GeneratedDiagnosticSample>();
        Vector<GeneratedDiagnosticSample> right = new Vector<GeneratedDiagnosticSample>();
        left.add(GeneratedDiagnosticObservationExecutor.resistanceObservation("READING", 20000000));
        for (double value : new double[] {40000000, Double.POSITIVE_INFINITY}) {
            right.clear();
            right.add(GeneratedDiagnosticObservationExecutor.resistanceObservation("READING", value));
            require(GeneratedDiagnosticEquivalence.sameObservations(left, right),
                "compiled OL comparison must not use hidden resistance differences");
        }
        right.clear();
        right.add(GeneratedDiagnosticObservationExecutor.resistanceObservation("READING", 10000000));
        require(!GeneratedDiagnosticEquivalence.sameObservations(left, right), "compiled numeric/OL outcomes remain distinct");
        reject("invalid resistance", "Invalid diagnostic resistance", new Case() { public void run() {
            GeneratedDiagnosticObservationExecutor.resistanceObservation("READING", Double.NaN);
        }});
        return overRange;
    }

    private static int validateEvidence(Vector<GeneratedDiagnosticSolvabilityEvidence> evidence) {
        int samples = 0;
        for (GeneratedDiagnosticSolvabilityEvidence proof : evidence) {
            require(proof.isRepairReachable() && proof.isCustomerRetestPassed() && proof.isStateIsolated() &&
                proof.hasUnaffectedFunctionRetestObservation() && proof.getMeasuredExecutionDepth() > 0 &&
                !proof.getSolverSamples().isEmpty(), "executed solver, legal repair, retest and isolation evidence");
            require(proof.getExecutedRepairActionIds().contains(WorkbenchOperation.REMOVE) &&
                proof.getExecutedRepairActionIds().contains(WorkbenchOperation.CATALOG_INSTALL) &&
                proof.getExecutedActionIds().contains(GeneratedBoardOperationIds.CUSTOMER_RETEST), "actual repair action trace");
            samples += proof.getSolverSamples().size();
        }
        return samples;
    }

    private static void verifyReceiptRejection(final CirSim sim, final GeneratedBoardInstance board,
            final GeneratedChallengeController controller, final GeneratedDiagnosticProofReceipt receipt) {
        final Object attempt = controller.beginDiagnosticAdmission();
        reject("stale receipt", "Stale or foreign", new Case() { public void run() {
            controller.completeDiagnosticAdmission(receipt, attempt);
        }});
        controller.abortDiagnosticAdmission(attempt);
        require(controller.getDiagnosticProofReceipt() == null, "failed admission cannot expose a prior receipt");
        reject("consumed attempt", "already consumed", new Case() { public void run() {
            controller.completeDiagnosticAdmission(receipt, attempt);
        }});
        reject("empty proof", "empty or developer-fixture", new Case() { public void run() {
            new GeneratedDiagnosticProofReceipt(board, controller, new Object(),
                board.getDiagnosticProvider().getObservationProgram(),
                new Vector<GeneratedDiagnosticSolvabilityEvidence>(), 0);
        }});
    }

    private static void verifyProviderFailures(final CirSim sim) {
        for (int mode = 1; mode <= 3; mode++) {
            final GeneratedBoardInstance board = wrap(new LedIndicatorGenerator().generate(0), mode);
            sim.installGeneratedChallengeForDeveloperVerification(board);
            GeneratedRuntimeDeveloperSettlement.settle(sim, board, "a09-negative-provider-entry");
            final GeneratedChallengeController controller = sim.getGeneratedChallengeController();
            Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
            final String expected = mode == 1 ? "no legal replacement" : mode == 2 ?
                "depends on the selected hypothesis" : "Unavailable diagnostic input";
            reject("provider failure " + mode, expected, new Case() { public void run() {
                GeneratedDiagnosticProofService.prove(sim, board, controller);
            }});
            before.assertRestored(sim);
            require(sim.getGeneratedBoardInstance() == board && controller.getDiagnosticProofReceipt() == null,
                "failed provider restores exact owner and emits no receipt");
        }
        final GeneratedBoardInstance fixture = BoundedGeneratedBoardAssembler.assemble(
            BoundedAssemblyRequest.forCanary(0)).getInstance();
        reject("developer fixture", "normal owner", new Case() { public void run() {
            GeneratedDiagnosticProofService.prove(sim, fixture, sim.getGeneratedChallengeController());
        }});
    }

    private static GeneratedBoardInstance wrap(GeneratedBoardInstance source, int mode) {
        return new GeneratedBoardInstance(source.getBoard(), source.getSimulationElements(), source.getSeed(),
            source.getCircuitFamilyId(), source.getTopologyVariantId(), source.getDescription(),
            source.getComponentBindings(), source.getExternalPowerBindings(), source.getConnectionBindings(),
            source.getBehaviorContract(), source.getPcbLayout(), source.getPhysicalSpecifications(), source.getFaultBinding(),
            source.getOperationalStates(), source.getChallengeDefinition(), source.getFamilyState(), source.getPhysicalBoardRuntime(),
            source.getTemporalBehavior(), false, source.getFaultCandidates(), null, new FailingProvider(source, mode));
    }

    /** Only explicit developer code can assemble these deliberately broken contributions. */
    private static final class FailingProvider implements GeneratedDiagnosticProvider {
        private final GeneratedDiagnosticProvider delegate;
        private final int mode;
        private final String selected;
        FailingProvider(GeneratedBoardInstance source, int mode) {
            delegate = source.getDiagnosticProvider(); this.mode = mode;
            selected = source.getFaultBinding().getFault().getHypothesisKey();
        }
        public String getProviderId() { return delegate.getProviderId(); }
        public GeneratedDiagnosticPlan getDiagnosticPlan() {
            if (mode != 3) return delegate.getDiagnosticPlan();
            return new GeneratedDiagnosticPlan("DECLARED_INPUT_CHECK", "R1.2",
                new String[] { "R1.1", "R1.2" }, new String[] { "DC_VOLTAGE" },
                new String[] { GeneratedBoardOperationIds.CONTROL_INPUT_HIGH },
                new String[] { WorkbenchOperation.REMOVE }, new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CONTROL_INPUT_HIGH, GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "PUBLIC_INPUT_SAMPLE" }, new String[] { "VIN", "GND" }, 2, false, true, "NONE");
        }
        public GeneratedDiagnosticProgram getObservationProgram() {
            if (mode == 3) return GeneratedDiagnosticProgram.builder(getDiagnosticPlan())
                .input(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH)
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "READING", "R1.1", "R1.2").build();
            if (mode == 2) return GeneratedDiagnosticProgram.builder(getDiagnosticPlan())
                .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE,
                    selected.indexOf("LED_OPEN") >= 0 ? "READING_A" : "READING_B", "R1.1", "R1.2").build();
            return delegate.getObservationProgram();
        }
        public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
            return wrap(delegate.generateHypothesis(hypothesis), mode);
        }
        public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
            return mode == 1 ? null : delegate.getCorrectCatalogId(instance, componentId);
        }
    }

    private static void reject(String label, String fragment, Case action) {
        try { action.run(); }
        catch (RuntimeException expected) {
            require(expected.getMessage() != null && expected.getMessage().contains(fragment),
                label + " rejected at incorrect boundary: " + expected.getMessage());
            rejected++; return;
        }
        throw new IllegalStateException("A09 accepted invalid " + label);
    }
    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new IllegalStateException("A09: " + label);
    }
    private static native void clearReport() /*-{
        $doc.documentElement.removeAttribute("data-tsj-a09-report");
    }-*/;
    private static native void publish(String value) /*-{
        $doc.documentElement.setAttribute("data-tsj-a09-report", value);
    }-*/;
}
