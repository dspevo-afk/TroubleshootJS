package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused developer proof for Task 40 physical-locus admission. */
final class Task40DeveloperVerifier {
    private Task40DeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance current = sim.getGeneratedBoardInstance();
        require(current != null, "Task 40 verifier requires a generated board");
        if (!current.isDeveloperOnlyFaultRoute())
            GeneratedFaultServiceabilityAdmission.validate(current, current.getFaultBinding());

        int routeCount = 0;
        routeCount += verify(new LedIndicatorGenerator().generate(0),
            GeneratedFaultType.RESISTOR_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "LED1", "R1" }, sim);
        routeCount += verify(new LedIndicatorGenerator().generate(3),
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
            GeneratedFaultLocusType.COMPONENT_INTERNAL, new String[] { "LED1", "R1" }, sim);
        routeCount += verify(new LedIndicatorGenerator().generateForFaultVerification(0,
            GeneratedFaultType.LED_OPEN), GeneratedFaultType.LED_OPEN,
            GeneratedFaultLocusType.COMPONENT_INTERNAL, new String[] { "LED1", "R1" }, sim);
        routeCount += verify(new DiodeProtectedIndicatorGenerator().generate(0),
            GeneratedFaultType.DIODE_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "D1" }, sim);
        routeCount += verify(new ParallelDualIndicatorGenerator().generate(0),
            GeneratedFaultType.RESISTOR_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "R1" }, sim);
        routeCount += verify(new ParallelDualIndicatorGenerator().generate(3),
            GeneratedFaultType.RESISTOR_INCORRECT_VALUE,
            GeneratedFaultLocusType.COMPONENT_INTERNAL, new String[] { "R1" }, sim);
        routeCount += verify(new RcDelayGenerator().generate(0),
            GeneratedFaultType.CAPACITOR_OPEN, GeneratedFaultLocusType.TERMINAL_ATTACHMENT,
            new String[] { "C1" }, sim);
        routeCount += verify(new RcDelayGenerator().generate(2),
            GeneratedFaultType.CAPACITOR_SHORT, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "C1" }, sim);
        routeCount += verify(new NpnLowSideSwitchGenerator().generate(0),
            GeneratedFaultType.TRANSISTOR_CE_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "Q1", "RB" }, sim);
        routeCount += verify(new NpnLowSideSwitchGenerator().generate(1),
            GeneratedFaultType.TRANSISTOR_CE_SHORT, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "Q1", "RB" }, sim);
        routeCount += verify(new NpnLowSideSwitchGenerator().generate(2),
            GeneratedFaultType.BASE_RESISTOR_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "Q1", "RB" }, sim);
        routeCount += verify(new NmosLowSideSwitchGenerator().generate(0),
            GeneratedFaultType.NMOS_DS_OPEN, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "Q1" }, sim);
        routeCount += verify(new NmosLowSideSwitchGenerator().generate(1),
            GeneratedFaultType.NMOS_DS_SHORT, GeneratedFaultLocusType.COMPONENT_INTERNAL,
            new String[] { "Q1" }, sim);
        routeCount += verify(new NmosLowSideSwitchGenerator().generate(2),
            GeneratedFaultType.NMOS_GATE_OPEN, GeneratedFaultLocusType.TERMINAL_ATTACHMENT,
            new String[] { "Q1" }, sim);

        verifyConnectorAndForcedRejections();
        require(routeCount == admittedNormalCorpusCount(),
            "Task 40 route/candidate corpus mismatch: routes=" + routeCount);
    }

    private static int verify(GeneratedBoardInstance instance, GeneratedFaultType type,
            GeneratedFaultLocusType locusType, String[] expectedOwners, CirSim sim) {
        require(instance.getFaultBinding().getFault().getType() == type,
            "Task 40 route selected the wrong fault: " + type);
        verifyOwnerCatalog(instance, expectedOwners, type);
        GeneratedFaultServiceabilityAdmission.validateCandidate(
            new GeneratedFaultCandidate(instance.getFaultBinding(), true));
        GeneratedFaultServiceability serviceability = instance.getFaultServiceability();
        require(serviceability != null && serviceability.getLocus().getType() == locusType,
            "Task 40 route has the wrong physical locus: " + type);
        require(instance.getFaultPhysicalOwnerCount() == 1 &&
            expectedOwnersContains(expectedOwners, instance.getFaultLocus().getOwnerId()) &&
            serviceability.getObservationActionCount() > 0 &&
            serviceability.getIsolationActionCount() > 0 &&
            serviceability.getRepairActionCount() > 0 &&
            GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(
                serviceability.getCustomerRetestOperationId()),
            "Task 40 route has incomplete serviceability metadata: " + type);
        verifyRuntimeRepairChain(sim, instance, type);
        return 1;
    }

    private static void verifyOwnerCatalog(GeneratedBoardInstance instance,
            String[] expectedOwners, GeneratedFaultType type) {
        Vector<String> owners = instance.getAdmittedFaultPhysicalOwnerIds();
        require(owners.size() == expectedOwners.length,
            "Task 40 owner count changed for " + type + ": " + owners);
        for (int index = 0; index < expectedOwners.length; index++)
            require(expectedOwners[index].equals(owners.get(index)),
                "Task 40 owner ordering changed for " + type + ": " + owners);
        require(owners.equals(instance.getAdmittedFaultPhysicalOwnerIds()),
            "Task 40 owner set is not deterministic for " + type);
        require(instance.getAdmittedFaultPhysicalOwnerCount() == expectedOwners.length,
            "Task 40 owner metric disagrees for " + type);
    }

    private static boolean expectedOwnersContains(String[] expectedOwners, String ownerId) {
        for (String expectedOwner : expectedOwners)
            if (expectedOwner.equals(ownerId)) return true;
        return false;
    }

    private static void verifyRuntimeRepairChain(CirSim sim, GeneratedBoardInstance instance,
            GeneratedFaultType type) {
        GeneratedChallengeController challenge = installReadyChallenge(sim, instance);
        GeneratedFaultServiceabilityAdmission.validateExecutableRuntime(sim, instance,
            instance.getFaultBinding());
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardPowerController().isElectricallyUnpowered(),
            "Task 40 isolation did not establish electrical unpowered state: " + type);

        if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(instance.getCircuitFamilyId())) {
            if (type == GeneratedFaultType.LED_OPEN)
                verifyWrongOwnerR1Repair(sim, instance, challenge);
            else if (type == GeneratedFaultType.RESISTOR_OPEN ||
                    type == GeneratedFaultType.RESISTOR_INCORRECT_VALUE)
                verifyWrongOwnerLedRepair(sim, instance, challenge);
        }

        if (type == GeneratedFaultType.CAPACITOR_OPEN &&
                "C1".equals(instance.getFaultLocus().getComponentId())) {
            exerciseLead(sim, instance, "C1", "+");
            verifyNotRestoredAfterPhysicalWorkflow(sim, challenge,
                "CAPACITOR_OPEN reconnect C1.+");
        }
        if (NmosLowSideSwitchGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()) &&
                "Q1".equals(instance.getFaultLocus().getComponentId())) {
            exerciseLead(sim, instance, "Q1", "G");
            if (type == GeneratedFaultType.NMOS_GATE_OPEN)
                verifyNotRestoredAfterPhysicalWorkflow(sim, challenge,
                    "NMOS_GATE_OPEN reconnect Q1.G");
            exerciseLead(sim, instance, "Q1", "D");
            exerciseLead(sim, instance, "Q1", "S");
        }

        String componentId = instance.getFaultLocus().getComponentId();
        PhysicalPart<?> original = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        require(original != null && ((GeneratedFaultOwningPart) original)
            .ownsGeneratedFault(instance.getFaultBinding()),
            "Task 40 original owner was not installed: " + componentId);
        if (type == GeneratedFaultType.CAPACITOR_OPEN || type == GeneratedFaultType.LED_OPEN ||
                type == GeneratedFaultType.NMOS_GATE_OPEN)
            verifyOriginalReinstallDoesNotRestore(sim, challenge, instance, original);
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original));
        require(instance.getPhysicalBoardRuntime().getInstalledPart(componentId) == null &&
            !original.isInstalled(), "Task 40 provider removal did not detach: " + componentId);

        String correctCatalogId = correctCatalogId(instance, componentId);
        WorkbenchOperation install = WorkbenchOperation.forCatalog(componentId, correctCatalogId);
        dispatch(sim, install);
        PhysicalPart<?> replacement = instance.getPhysicalBoardRuntime()
            .getInstalledPart(componentId);
        require(replacement != null && replacement != original &&
            !((GeneratedFaultOwningPart) replacement).ownsGeneratedFault(
                instance.getFaultBinding()),
            "Task 40 replacement inherited original fault ownership: " + componentId);

        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Task 40 powered solver did not validate repair: " + type);
        require(challenge.performCustomerRetest().isPassed(),
            "Task 40 CUSTOMER_RETEST did not pass: " + type);
        sim.verifyGeneratedBoard();
    }

    private static void verifyNotRestoredAfterPhysicalWorkflow(CirSim sim,
            GeneratedChallengeController challenge, String caseId) {
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "Task 40 physical workflow incorrectly cleared generated fault: " + caseId);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
    }

    private static void verifyOriginalReinstallDoesNotRestore(CirSim sim,
            GeneratedChallengeController challenge, GeneratedBoardInstance instance,
            PhysicalPart<?> original) {
        String componentId = instance.getFaultLocus().getComponentId();
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original));
        require(instance.getPhysicalBoardRuntime().getInstalledPart(componentId) == null,
            "Task 40 original reinstall setup did not remove the fault owner: " + componentId);
        dispatch(sim, WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, original,
            componentId));
        require(instance.getPhysicalBoardRuntime().getInstalledPart(componentId) == original &&
                original.isInstalled() && ((GeneratedFaultOwningPart) original)
                    .ownsGeneratedFault(instance.getFaultBinding()),
            "Task 40 original fault owner was not reinstalled: " + componentId);
        verifyNotRestoredAfterPhysicalWorkflow(sim, challenge,
            "original fault-owning part reinstall " + componentId);
    }

    private static GeneratedChallengeController installReadyChallenge(CirSim sim,
            GeneratedBoardInstance instance) {
        sim.installGeneratedChallenge(instance);
        sim.setSimRunning(true);
        for (int attempt = 0; attempt < 10; attempt++) {
            sim.updateCircuit();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            if (challenge != null && challenge.isReady()) return challenge;
        }
        throw new IllegalStateException("Task 40 generated challenge did not become ready: " +
            instance.getCircuitFamilyId() + "/" + instance.getSeed());
    }

    private static void exerciseLead(CirSim sim, GeneratedBoardInstance instance,
            String componentId, String terminalId) {
        String padId = componentId + "." + terminalId;
        PhysicalPart<?> installed = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        dispatch(sim, WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, installed,
            componentId, padId));
        require(instance.getBoard().getComponent(componentId) != null &&
            sim.getBoardModificationController().getComponentState(componentId) ==
                ComponentPhysicalState.LEAD_LIFTED,
            "Task 40 provider did not lift " + padId);
        installed = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        dispatch(sim, WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD,
            installed, componentId, padId));
        require(sim.getBoardModificationController().isComponentInstalled(componentId),
            "Task 40 provider did not reconnect " + padId);
    }

    private static void dispatch(CirSim sim, WorkbenchOperation operation) {
        if (sim.pcbWorkbenchController == null ||
                !sim.pcbWorkbenchController.isAvailable(operation))
            throw new IllegalStateException("Task 40 runtime action is unavailable: " +
                operation.getId() + " component=" + operation.getComponentId() +
                " pad=" + operation.getPadId() + " state=" +
                sim.getBoardModificationController().getComponentState(
                    operation.getComponentId()) + " connected=" +
                sim.getBoardModificationController().isLeadConnected(
                    operation.getComponentId(), operation.getPadId()) +
                " unpowered=" + sim.getBoardPowerController().isElectricallyUnpowered() +
                " challenge=" + sim.getGeneratedChallengeController().getState());
        require(sim.pcbWorkbenchController.dispatch(operation),
            "Task 40 runtime action did not execute: " + operation.getId());
    }

    private static String correctCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("C1".equals(componentId)) return CapacitorReplacementCatalog.CORRECT;
        if ("D1".equals(componentId)) return DiodeReplacementCatalog.CORRECT;
        if ("LED1".equals(componentId)) return LedReplacementCatalog.CORRECT;
        if ("Q1".equals(componentId))
            return NmosLowSideSwitchGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()) ?
                NmosReplacementCatalog.CORRECT : NpnReplacementCatalog.CORRECT;
        PhysicalSpecification specification = instance.getPhysicalSpecifications()
            .getSpecification(componentId);
        require(specification instanceof ResistorNameplate,
            "Task 40 has no correct catalog mapping: " + componentId);
        return "R_CATALOG_" + (long)((ResistorNameplate) specification)
            .getNominalResistanceOhms();
    }

    private static void verifyWrongOwnerR1Repair(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        PhysicalPart<?> original = instance.getPhysicalBoardRuntime().getInstalledPart("R1");
        require(original != null, "Task 40 LED_OPEN route has no R1 wrong-owner fixture");
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original));
        dispatch(sim, WorkbenchOperation.forCatalog("R1", resistorCatalogId(instance)));
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "LED_OPEN was incorrectly cured by a different-owner R1 replacement");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        PhysicalPart<?> replacement = instance.getPhysicalBoardRuntime().getInstalledPart("R1");
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, replacement));
        dispatch(sim, WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, original, "R1"));
        verifyNotRestoredAfterPhysicalWorkflow(sim, challenge, "LED_OPEN original R1 reinstall");
    }

    private static void verifyWrongOwnerLedRepair(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        PhysicalPart<?> original = instance.getPhysicalBoardRuntime().getInstalledPart("LED1");
        require(original != null, "R1-owned LED route has no LED wrong-owner fixture");
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, original));
        dispatch(sim, WorkbenchOperation.forCatalog("LED1", LedReplacementCatalog.CORRECT));
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "R1-owned LED route was incorrectly cured by an LED replacement");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        PhysicalPart<?> replacement = instance.getPhysicalBoardRuntime().getInstalledPart("LED1");
        dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, replacement));
        dispatch(sim, WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL, original, "LED1"));
        verifyNotRestoredAfterPhysicalWorkflow(sim, challenge, "R1-owned original LED reinstall");
    }

    private static String resistorCatalogId(GeneratedBoardInstance instance) {
        PhysicalSpecification specification = instance.getPhysicalSpecifications()
            .getSpecification("R1");
        require(specification instanceof ResistorNameplate,
            "Task 40 LED_OPEN route has no R1 catalog mapping");
        return "R_CATALOG_" + (long)((ResistorNameplate) specification)
            .getNominalResistanceOhms();
    }

    private static int admittedNormalCorpusCount() {
        int count = 0;
        for (String familyId : QuickPlayFamilyRegistry.getNormalPlayerFamilyIds()) {
            GeneratedBoardInstance representative =
                QuickPlayFamilyRegistry.generate(familyId, 0);
            count += GeneratedDiagnosticSolvabilityAdmission.getAdmittedCandidateCount(
                representative.getFaultCandidates());
        }
        return count;
    }

    private static void verifyConnectorAndForcedRejections() {
        GeneratedFaultServiceability bogusServiceability = new GeneratedFaultServiceability(
            GeneratedFaultLocus.componentInternal("R1"),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { "BOGUS_REPAIR" }, GeneratedBoardOperationIds.CUSTOMER_RETEST);
        GeneratedFaultCandidate bogus = new GeneratedFaultCandidate(new GeneratedFaultBinding(
            new GeneratedFault("TASK40_BOGUS_ACTION", GeneratedFaultType.RESISTOR_OPEN,
                "R1", "LED_INDICATOR", 0), new SwitchOpenFaultEffect(new SwitchElm(0, 0)),
            bogusServiceability), true);
        Vector<GeneratedFaultCandidate> bogusCatalog = new Vector<GeneratedFaultCandidate>();
        bogusCatalog.add(bogus);
        require(!GeneratedFaultServiceabilityAdmission.isAdmitted(bogus) &&
            GeneratedFaultServiceabilityAdmission.getPhysicalOwnerCount(bogusCatalog) == 0,
            "Bogus serviceability action entered Task 40 admission metrics");
        try {
            GeneratedFaultServiceabilityAdmission.validateCandidate(bogus);
            throw new IllegalStateException("Bogus serviceability action was admitted");
        } catch (IllegalArgumentException expected) {
            // Unknown action IDs are rejected before any board/runtime lookup.
        }

        GeneratedFaultCandidate connector = GeneratedFaultEngine.connectorOpenPath(
            "TASK40_CONNECTOR_OPEN", "LED_INDICATOR", 0, "J1",
            new SwitchElm(0, 0), false);
        require(!connector.isCompatible() && !GeneratedFaultServiceabilityAdmission.isAdmitted(
            connector), "Connector candidate became normally admitted");
        try {
            GeneratedFaultServiceabilityAdmission.validateCandidate(connector);
            throw new IllegalStateException("Connector candidate without repair primitive was admitted");
        } catch (IllegalArgumentException expected) {
            // The connector effect remains a developer-visible solver fixture only.
        }

        GeneratedBoardInstance forced = new NpnLowSideSwitchGenerator().generateForFaultVerification(
            0, GeneratedFaultType.LOAD_PATH_OPEN);
        require(forced.isDeveloperOnlyFaultRoute() &&
            forced.getFaultBinding().getFault().getType() == GeneratedFaultType.LOAD_PATH_OPEN &&
            forced.getFaultPhysicalOwnerCount() == 0,
            "NPN load-path effect was not kept outside normal physical admission");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
