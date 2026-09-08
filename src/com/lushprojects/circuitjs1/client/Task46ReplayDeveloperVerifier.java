package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/** Bounded real leaf replay proof, called only by the explicit debug route. */
final class Task46ReplayDeveloperVerifier {
    static final class Result {
        final String diagnostics;
        final String snapshot;
        final String summary;
        Result(String diagnostics, String snapshot, String summary) {
            this.diagnostics = diagnostics;
            this.snapshot = snapshot;
            this.summary = summary;
        }
    }

    private Task46ReplayDeveloperVerifier() { }

    static Result verify(CirSim sim) {
        require(sim.troubleshootDebug && sim.developerVerifierRunning,
            "developer boundary is required");
        GeneratedBoardInstance original = sim.getGeneratedBoardInstance();
        require(original != null && sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady(), "initial legacy challenge is not ready");
        final ChallengeDescriptor descriptor = original.getPcbLayout().getLayoutAlgorithmVersion() ==
                SeededPcbLayoutGenerator.CURRENT_VERSION ?
            ChallengeDescriptor.correctedSeeded(original.getCircuitFamilyId(), original.getSeed()) :
            ChallengeDescriptor.legacy(original.getCircuitFamilyId(), original.getSeed());
        final String canonical = descriptor.toCanonical();
        final String originalScenario = scenarioSnapshot(sim);
        final String beforeDiagnostics = sim.dumpCircuit();
        final int beforeAnalysis = sim.analysisCountForDeveloperVerification;
        final String diagnostics = LegacyChallengeReplay.describe(descriptor);
        require(diagnostics.equals(LegacyChallengeReplay.describe(descriptor)) &&
            original == sim.getGeneratedBoardInstance() &&
            beforeDiagnostics.equals(sim.dumpCircuit()) &&
            beforeAnalysis == sim.analysisCountForDeveloperVerification,
            "diagnostic inspection changed the live challenge");
        int rejections = verifyRejections(descriptor);
        require(original == sim.getGeneratedBoardInstance() &&
            beforeDiagnostics.equals(sim.dumpCircuit()), "rejection changed the live challenge");

        GeneratedBoardInstance direct = QuickPlayFamilyRegistry.generate(
            descriptor.getDeviceIntent().getId(), descriptor.getRootSeed(),
            LegacyChallengeReplay.layoutAlgorithmVersion(descriptor));
        GeneratedBoardInstance replay = LegacyChallengeReplay.generate(ChallengeDescriptor.parse(canonical));
        assertFresh(original, direct);
        assertFresh(direct, replay);
        require(replay != original, "replay reused the installed owner");
        final String expected = generationSnapshot(direct);
        requireSnapshotEqual(expected, generationSnapshot(replay));
        require(expected.equals(generationSnapshot(replay)) &&
            diagnostics.equals(LegacyChallengeReplay.describe(descriptor)),
            "inspection changed replay metadata");
        require(expectedFault(descriptor).equals(replay.getChallengeDefinition().getFault().getType().name()),
            "accepted corpus selected a different fault");
        String directScenario;
        String replayScenario;
        RuntimeException primary = null;
        try {
            installAndSettle(sim, direct);
            GeneratedChallengeController directController = sim.getGeneratedChallengeController();
            BoardModificationController directModifications = sim.getBoardModificationController();
            directScenario = scenarioSnapshot(sim);
            require(originalScenario.equals(directScenario), "fresh direct scenario differs from original");
            installAndSettle(sim, replay);
            require(directController != sim.getGeneratedChallengeController() &&
                directModifications != sim.getBoardModificationController(),
                "fresh replay reused a challenge or modification controller");
            replayScenario = scenarioSnapshot(sim);
            require(directScenario.equals(replayScenario), "descriptor replay changed the solved scenario");
        } catch (RuntimeException failure) {
            primary = failure;
            throw failure;
        } finally {
            // A fresh challenge is deliberately distinct from restoring or
            // resetting one of the already exercised runtime owners.
            try {
                GeneratedBoardInstance restored = LegacyChallengeReplay.generate(
                    ChallengeDescriptor.parse(canonical));
                assertFresh(replay, restored);
                require(restored != original && restored != direct, "cleanup reused an old owner");
                installAndSettle(sim, restored);
                require(originalScenario.equals(scenarioSnapshot(sim)), "fresh cleanup changed scenario");
            } catch (RuntimeException cleanup) {
                if (primary != null)
                    throw new IllegalStateException("Task46 replay failed: " + primary.getMessage() +
                        "; fresh challenge cleanup failed: " + cleanup.getMessage(), primary);
                throw cleanup;
            }
        }
        return new Result(diagnostics, expected, "TSJ-TASK46-REPLAY-1\nfamily=" +
            descriptor.getDeviceIntent().getId() + "\nseed=" + Long.toString(descriptor.getRootSeed()) +
            "\nfault=" + replay.getChallengeDefinition().getFault().getId() +
            "\ngeometry-version=" + descriptor.getGeometryVersion().getValue() +
            "\nsemantic-snapshot-equal=true\nfresh-owners=true\nhealthy-faulted-ready=true" +
            "\nscenario=" + replayScenario + "\nrejected-unsupported-inputs=" + rejections +
            "\ncleanup=fresh-challenge-ready\nnormal-admission=initial-legacy-route;" +
            "paired-installs=developer-verification");
    }

    private static void installAndSettle(CirSim sim, GeneratedBoardInstance instance) {
        sim.installGeneratedChallengeForDeveloperVerification(instance);
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task46-replay-install");
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(sim.getGeneratedBoardInstance() == instance && challenge.isReady(),
            "replay did not reach READY through the existing solver lifecycle");
        GeneratedChallengeLifecycleEvidence evidence = challenge.getLifecycleEvidence();
        require(evidence.healthyGenerationInstalled && evidence.healthyGraphAnalyzedAfterTimeAdvance &&
            evidence.healthyFamilyValidated && evidence.selectedFaultApplied &&
            evidence.faultedGraphAnalyzedAfterTimeAdvance && evidence.selectedFaultValidated &&
            evidence.scenarioCompatibilityValidated && evidence.readyAfterValidation,
            "healthy/faulted/scenario lifecycle proof is incomplete");
        require(challenge.getFaultController().isApplied() &&
            sim.getBoardModificationController().isFullyRestored() && !sim.activeMeasurementOverlay,
            "replay left physical mutations or a temporary measurement behind");
        instance.getBoard().validate();
        instance.getPhysicalBoardRuntime().validate();
        instance.getPcbLayout().validateGeometry(instance.getBoard());
        instance.getDiagnosticSolvabilityContract().validate(instance);
    }

    private static String scenarioSnapshot(CirSim sim) {
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        GeneratedScenario<GeneratedObservedBehavior> scenario = challenge.getScenario();
        require(scenario != null, "selected scenario is missing");
        return scenario.getScenarioId() + "|" + scenario.getComplaintId() + "|" +
            scenario.getComplaintText() + "|" + scenario.getObservedBehavior().name() + "|" +
            challenge.getLiveRepairStatus().name() + "|" + sim.getBoardPowerController().getState().name() +
            "|eligible=" + challenge.getDefinition().getScenarioCatalog()
                .getCompatibleScenarioIdsForDeveloperVerification(sim.getGeneratedBoardInstance(),
                    sim.getBoardModificationController(), sim.getBoardPowerController().getState());
    }

    /**
     * Sorted semantic rows, including the actual initial CircuitJS dump values
     * and endpoint attachments. Coordinates here are observed graph/layout
     * evidence, never a source of durable identity or random seed derivation.
     */
    private static String generationSnapshot(GeneratedBoardInstance instance) {
        List<String> rows = new ArrayList<String>();
        boolean correctedSeeded = instance.getPcbLayout().getLayoutAlgorithmVersion() ==
            SeededPcbLayoutGenerator.CURRENT_VERSION;
        TroubleshootBoard board = instance.getBoard();
        rows.add("identity|" + board.getId() + "|" + instance.getCircuitFamilyId() + "|" +
            instance.getTopologyVariantId() + "|" + Long.toString(instance.getSeed()));
        for (String id : board.getComponentIds()) {
            BoardComponent component = board.getComponent(id);
            PhysicalPackage physicalPackage = component.getPhysicalPackage();
            rows.add("component|" + id + "|" + component.getType() + "|" + physicalPackage.getId() +
                "|geometry=" + physicalPackage.getGeometryContractVersionValue() +
                "|pads=" + sorted(component.getPadIds()));
            for (GeneratedComponentConnectionBinding binding :
                    instance.getConnectionBindings().getForComponentOrEmpty(id))
                rows.add("connection|" + id + "|" + binding.getPadId() + "|" +
                    endpoint(binding.getBoardEndpoint()) + "|" + endpoint(binding.getComponentEndpoint()) +
                    "|" + binding.getConnectionElement().dump());
        }
        for (String id : board.getNetIds()) rows.add("net|" + id + "|" + sorted(board.getNet(id).getPadIds()));
        for (String id : board.getPadIds()) {
            BoardPad pad = board.getPad(id);
            rows.add("pad|" + id + "|" + pad.getComponentId() + "|" + pad.getTerminalId() + "|" +
                pad.getNetId() + "|" + endpoint(instance.getSimulationBindings().getEndpoint(id)));
        }
        for (String id : board.getPowerInputIds()) {
            ExternalBoardPowerInput input = board.getPowerInput(id);
            PowerInputNameplate nameplate = instance.getPhysicalSpecifications().getPowerInputNameplate(id);
            rows.add("input|" + id + "|" + input.getPositivePadId() + "|" + input.getReturnPadId() +
                "|" + input.getPositiveNetId() + "|" + input.getReturnNetId() + "|" +
                (nameplate == null ? "unspecified" : Double.toString(nameplate.getNominalVoltage())));
        }
        for (String id : instance.getPhysicalSpecifications().getPhysicalComponentIds()) {
            PhysicalSpecification specification = instance.getPhysicalSpecifications().getSpecification(id);
            PhysicalNameplate nameplate = instance.getPhysicalSpecifications().getNameplate(id);
            rows.add("specification|" + id + "|" + specification.getSpecificationId() + "|" +
                nameplate.getId() + "|" + nameplate.getDisplayName() + "|" +
                nameplate.getWorkbenchDetailLabel() + "|" + nameplate.getWorkbenchDetailValue());
            if (specification instanceof ResistorNameplate) {
                ResistorNameplate resistor = (ResistorNameplate) specification;
                rows.add("resistor-value|" + id + "|" + resistor.getNominalResistanceOhms() +
                    "|" + resistor.getTolerancePercent() + "|" + resistor.getRatedWattage());
            }
        }
        for (CircuitElm element : instance.getSimulationElements()) rows.add("solver-element|" + element.dump());
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts()) {
            rows.add("part|" + part.getId() + "|" + part.getSpecification().getSpecificationId() +
                "|" + part.getPackage().getId() + "|installed=" + part.getMountState().isInstalled() + "|" +
                part.isOriginal() + "|" + part.isFaulted() + "|slot=" +
                (part.getBoardSlot() == null ? "none" : part.getBoardSlot().getId()));
            for (PhysicalPartTerminal terminal : part.getTerminals())
                rows.add("terminal|" + terminal.getId() + "|" + terminal.getTerminalName() +
                    "|" + endpoint(terminal.getEndpoint()));
        }
        for (GeneratedFaultCandidate candidate : instance.getFaultCandidates()) {
            rows.add("candidate|" + fault(candidate.getFault()) + "|compatible=" + candidate.isCompatible() +
                "|admitted=" + candidate.isAdmitted());
            if (candidate.getServiceability() != null)
                rows.add("repair-semantics|" + candidate.getFault().getId() + "|" +
                    GeneratedDiagnosticRepairSemantics.forServiceability(candidate.getServiceability())
                        .stableDescription());
        }
        GeneratedChallengeDefinition challenge = instance.getChallengeDefinition();
        rows.add("challenge|" + challenge.getId() + "|" + Long.toString(challenge.getSelectionSeed()));
        rows.add("selected-fault|" + fault(challenge.getFault()));
        if (correctedSeeded) {
            rows.add("layout-algorithm|" + instance.getPcbLayout().getLayoutAlgorithmVersion());
            rows.add("selected-hypothesis|" + challenge.getFault().getHypothesisKey());
            for (String key : instance.getDiagnosticSolvabilityContract().getHypothesisKeys())
                rows.add("admitted-hypothesis|" + key);
        }
        rows.add("retest|" + instance.getCustomerRetestProfile().getStableId());
        rows.add("layout-v" + PcbGeometryContractVersion.CURRENT + "|" +
            instance.getPcbLayout().geometryFingerprint());
        Collections.sort(rows);
        StringBuilder result = new StringBuilder(correctedSeeded ?
            "TSJ-REPLAY-SNAPSHOT-2" : "TSJ-REPLAY-SNAPSHOT-1");
        for (String row : rows) result.append('\n').append(row.length()).append(':').append(row);
        return result.toString();
    }

    private static String endpoint(CircuitMeasurementEndpoint endpoint) {
        require(endpoint instanceof CircuitPostMeasurementEndpoint, "non-post leaf endpoint is unsupported");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return post.getElement().dump() + "|post=" + post.getPostIndex();
    }

    private static void requireSnapshotEqual(String expected, String actual) {
        if (expected.equals(actual)) return;
        String[] expectedRows = expected.split("\n");
        String[] actualRows = actual.split("\n");
        for (int row = 0; row < Math.min(expectedRows.length, actualRows.length); row++)
            if (!expectedRows[row].equals(actualRows[row]))
                throw new IllegalStateException("Task46 replay semantic mismatch at row " + row +
                    ": direct=" + expectedRows[row] + "; replay=" + actualRows[row]);
        throw new IllegalStateException("Task46 replay semantic row count mismatch: direct=" +
            expectedRows.length + "; replay=" + actualRows.length);
    }

    private static String fault(GeneratedFault fault) {
        return fault.getId() + "|" + fault.getType().name() + "|" + fault.getTargetComponentId() + "|" +
            fault.getCircuitFamilyId() + "|" + Long.toString(fault.getSelectionSeed()) + "|" +
            Double.toString(fault.getHealthyValue()) + "|" + Double.toString(fault.getEffectiveValue());
    }

    private static List<String> sorted(Vector<String> values) {
        List<String> result = new ArrayList<String>(values);
        Collections.sort(result);
        return result;
    }

    private static void assertFresh(GeneratedBoardInstance first, GeneratedBoardInstance second) {
        require(first != second && first.getBoard() != second.getBoard() &&
            first.getPhysicalBoardRuntime() != second.getPhysicalBoardRuntime() &&
            first.getChallengeDefinition() != second.getChallengeDefinition() &&
            first.getFaultBinding() != second.getFaultBinding() &&
            first.getFamilyState() != second.getFamilyState() &&
            first.getSimulationBindings() != second.getSimulationBindings(), "runtime owner was reused");
        for (CircuitElm element : second.getSimulationElements())
            require(!first.getSimulationElements().contains(element), "solver element was reused");
        for (PhysicalPart<?> part : second.getPhysicalBoardRuntime().getPhysicalParts()) {
            PhysicalPart<?> prior = first.getPhysicalBoardRuntime().getPart(part.getId());
            require(prior != null && prior != part, "physical part identity was lost or reused");
            for (PhysicalPartTerminal terminal : part.getTerminals()) {
                boolean matched = false;
                for (PhysicalPartTerminal other : prior.getTerminals())
                    if (other.getId().equals(terminal.getId())) {
                        matched = true;
                        require(other != terminal && other.getEndpoint() != terminal.getEndpoint(),
                            "terminal runtime owner was reused");
                    }
                require(matched, "stable terminal ID changed on fresh replay");
            }
        }
    }

    private static int verifyRejections(ChallengeDescriptor descriptor) {
        int count = 0;
        String encoded = descriptor.toCanonical();
        String generator = "generator=" + descriptor.getGenerator().toString();
        count += reject(encoded.replace(generator, "generator=legacy-leaf@99"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "generator");
        count += reject(encoded.replace(generator, "generator=unknown@1"),
            ChallengeContractException.Code.UNSUPPORTED_ID, "generator");
        String intent = "device-intent=" + descriptor.getDeviceIntent().getId() + "@1";
        count += reject(encoded.replace(intent, "device-intent=" + descriptor.getDeviceIntent().getId() + "@2"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "device-intent");
        count += reject(encoded.replace(intent, "device-intent=UNKNOWN_FAMILY@1"),
            ChallengeContractException.Code.UNSUPPORTED_ID, "device-intent");
        count += reject(encoded.replace("difficulty-profile=legacy-default@1", "difficulty-profile=legacy-default@2"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "difficulty-profile");
        count += reject(encoded.replace("difficulty-profile=legacy-default@1", "difficulty-profile=future-profile@1"),
            ChallengeContractException.Code.UNSUPPORTED_ID, "difficulty-profile");
        count += reject(encoded.replace("geometry=3", "geometry=4"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "geometry");
        for (GenerationConstraints.CountMetric metric : GenerationConstraints.CountMetric.values()) {
            GenerationConstraints constraints = new GenerationConstraints(1,
                Arrays.asList(new GenerationConstraints.CountRequest(metric,
                    GenerationConstraints.CountRange.exact(0))),
                GenerationConstraints.Requirement.UNSPECIFIED, GenerationConstraints.Requirement.UNSPECIFIED,
                GenerationConstraints.AllowedInstruments.unspecified());
            count += reject(withConstraints(descriptor, constraints).toCanonical(),
                ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT, "constraints");
        }
        GenerationConstraints noneAllowed = new GenerationConstraints(1,
            Collections.<GenerationConstraints.CountRequest>emptyList(),
            GenerationConstraints.Requirement.UNSPECIFIED, GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.AllowedInstruments.only(Collections.<String>emptyList()));
        count += reject(withConstraints(descriptor, noneAllowed).toCanonical(),
            ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT, "constraints");
        GenerationConstraints specifiedRequirement = new GenerationConstraints(1,
            Collections.<GenerationConstraints.CountRequest>emptyList(),
            GenerationConstraints.Requirement.FORBIDDEN, GenerationConstraints.Requirement.UNSPECIFIED,
            GenerationConstraints.AllowedInstruments.unspecified());
        count += reject(withConstraints(descriptor, specifiedRequirement).toCanonical(),
            ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT, "constraints");
        return count;
    }

    private static ChallengeDescriptor withConstraints(ChallengeDescriptor original, GenerationConstraints constraints) {
        return new ChallengeDescriptor(original.getSchemaVersion(), original.getRootSeed(),
            original.getGenerator(), original.getDeviceIntent(), original.getDifficultyProfile(),
            original.getGeometryVersion(), constraints);
    }

    private static int reject(String canonical, ChallengeContractException.Code code, String field) {
        ChallengeDescriptor descriptor = ChallengeDescriptor.parse(canonical);
        try {
            LegacyChallengeReplay.generate(descriptor);
        } catch (ChallengeContractException expected) {
            require(expected.getCode() == code && field.equals(expected.getFieldId()),
                "unsupported input reported a different stable rejection: " + expected.getMessage());
            require(LegacyChallengeReplay.describe(descriptor).indexOf(
                "replay-rejection=" + code.name() + ";field=" + field) >= 0,
                "diagnostics omitted the deterministic replay rejection");
            return 1;
        }
        throw new IllegalStateException("Task46 replay accepted unsupported input: " + field);
    }

    /** Literal accepted fault cases, independent of the adapter's dispatch. */
    private static String expectedFault(ChallengeDescriptor descriptor) {
        String family = descriptor.getDeviceIntent().getId();
        long seed = descriptor.getRootSeed();
        if ("NPN_LOW_SIDE_SWITCH".equals(family)) {
            if (seed == 0) return "TRANSISTOR_CE_OPEN";
            if (seed == 1) return "TRANSISTOR_CE_SHORT";
            if (seed == 2) return "BASE_RESISTOR_OPEN";
        } else if ("NMOS_LOW_SIDE_SWITCH".equals(family)) {
            if (seed == 0) return "NMOS_DS_OPEN";
            if (seed == 1) return "NMOS_DS_SHORT";
            if (seed == 2) return "NMOS_GATE_OPEN";
        } else if ("LED_INDICATOR".equals(family) && seed == 4) {
            return "LED_OPEN";
        } else if (seed == 0 || seed == 2 || seed == 3) {
            if ("LED_INDICATOR".equals(family) || "PARALLEL_DUAL_INDICATOR".equals(family))
                return seed == 3 ? "RESISTOR_INCORRECT_VALUE" : "RESISTOR_OPEN";
            if ("DIODE_PROTECTED_INDICATOR".equals(family)) return "DIODE_OPEN";
            if ("RC_DELAY".equals(family)) return seed == 2 ? "CAPACITOR_SHORT" : "CAPACITOR_OPEN";
        }
        throw new IllegalStateException("Task46 verifier route is outside the closed 19-case corpus");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Task46: " + message);
    }
}
