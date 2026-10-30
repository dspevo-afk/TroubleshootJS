package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.Vector;

/** Exact native census for declared electrical elements and owned service additions. */
public final class A04ElementInventoryRuntimeContractTest {
    private static int assertions;

    private A04ElementInventoryRuntimeContractTest() { }

    public static void main(String[] args) {
        CirSim previous = CircuitElm.sim;
        CirSim previousSingleton = CirSim.theSim;
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        Vector<BoundedGeneratedBoardAssembler.Result> results =
            new Vector<BoundedGeneratedBoardAssembler.Result>();
        Vector<CircuitElm> testOnlyElements = new Vector<CircuitElm>();
        Throwable failure = null;
        try {
            for (long seed : new long[] { 1L, 2L, 3L }) {
                BoundedGeneratedBoardAssembler.Result resistive =
                    BoundedGeneratedBoardAssembler.assemble(
                        BoundedAssemblyRequest.forCanary(seed));
                results.add(resistive);
                verifyPositive(resistive, "resistive/seed-" + seed, seed == 1L);
                BoundedGeneratedBoardAssembler.Result controlled =
                    BoundedGeneratedBoardAssembler.assemble(
                        BoundedAssemblyRequest.forControlledIndicator(seed));
                results.add(controlled);
                verifyPositive(controlled, "controlled/seed-" + seed, false);
            }
            verifyInventoryNegatives(results.get(0), results.get(1), testOnlyElements);
            verifyImmutableRpdRetargetNegative(results.get(1));
        } catch (Throwable caught) {
            failure = caught;
        } finally {
            Throwable cleanupFailure = null;
            for (CircuitElm element : testOnlyElements) {
                try { if (element != null) element.delete(); }
                catch (Throwable cleanup) { cleanupFailure = retain(cleanupFailure, cleanup); }
            }
            for (BoundedGeneratedBoardAssembler.Result result : results) {
                if (result == null || result.getInstance() == null) continue;
                for (CircuitElm element : result.getInstance().getSimulationElements()) {
                    try { if (element != null) element.delete(); }
                    catch (Throwable cleanup) { cleanupFailure = retain(cleanupFailure, cleanup); }
                }
            }
            try {
                CircuitElm.sim = previous;
                CirSim.theSim = previousSingleton;
            } catch (Throwable cleanup) {
                cleanupFailure = retain(cleanupFailure, cleanup);
            }
            if (cleanupFailure != null) {
                if (failure == null) failure = cleanupFailure;
                else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure != null) {
            failure.printStackTrace();
            System.err.println("FAIL: A04 element inventory runtime contracts after " +
                assertions + " assertions: " + failure.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("PASS: A04 element inventory runtime contracts assertions=" +
            assertions + " actualCandidates=" + results.size());
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void verifyPositive(BoundedGeneratedBoardAssembler.Result result,
            String label, boolean verifyConnectorAddition) {
        require(result != null && result.getInstance() != null && result.getPlan() != null,
            label + " did not produce a complete assembly result");
        GeneratedBoardInstance instance = result.getInstance();
        BoundedAssemblyPlan plan = result.getPlan();
        ConstructionReceipt receipt = result.getConstructionReceiptForDeveloperVerification();
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        Vector<CircuitElm> declaredElements = receipt.getElements();
        Vector<CircuitElm> actualElements = instance.getSimulationElements();

        require(receipt.getAllocatedElementCount() == spec.getElementDeclarations().size() &&
                declaredElements.size() == spec.getElementDeclarations().size(),
            label + " receipt is not the exact electrical declaration census");
        Vector<CircuitElm> identityBijection = new Vector<CircuitElm>();
        for (ElectricalRealizationSpec.ElementDeclaration declaration :
                spec.getElementDeclarations().values()) {
            CircuitElm element = receipt.getElement(declaration.getOwnerKey(),
                declaration.getElementId()).getElement();
            require(element != null && containsIdentity(declaredElements, element) &&
                    !containsIdentity(identityBijection, element),
                label + " declaration did not resolve to one receipt element identity: " +
                    declaration.getOwnerKey() + "/" + declaration.getElementId());
            identityBijection.add(element);
        }
        require(identityBijection.size() == declaredElements.size(),
            label + " receipt contains an element without a declaration handle");
        for (CircuitElm element : declaredElements)
            require(element != null && countIdentity(declaredElements, element) == 1 &&
                    containsIdentity(identityBijection, element),
                label + " receipt contains a missing, null, or duplicate declaration identity");

        A04ConstructionDeveloperVerifier.verifyElementInventory(instance, plan, receipt,
            actualElements);
        assertions++;
        verifyResistorEndpointBindings(instance, plan, receipt, label);

        if (verifyConnectorAddition) {
            int serviceAdditions = 0;
            for (CircuitElm element : actualElements)
                if (!containsIdentity(declaredElements, element)) serviceAdditions++;
            require(serviceAdditions > 0,
                label + " produced no actual service-owned additions");
            Vector<CircuitElm> docking = connectorDockingAttachments(instance);
            require(!docking.isEmpty(), label + " connector has no docking attachments");
            for (CircuitElm attachment : docking)
                require(containsIdentity(actualElements, attachment) &&
                        !containsIdentity(declaredElements, attachment),
                    label + " connector docking attachment is not a post-receipt owned addition");
        }
    }

    private static void verifyResistorEndpointBindings(GeneratedBoardInstance instance,
            BoundedAssemblyPlan plan, ConstructionReceipt receipt, String label) {
        boolean sawImmutableRpd = false;
        for (Map.Entry<String, ComposedBlockContribution> block : plan.getBlocks().entrySet()) {
            for (Map.Entry<String, ComposedBlockContribution.ResistorRecipe> entry :
                    block.getValue().getResistors().entrySet()) {
                String owner = block.getKey();
                String local = entry.getValue().getComponentLocalId();
                A04ConstructionDeveloperVerifier.verifyComponentEndpoint(instance, plan,
                    owner, local, "1", 0, false);
                A04ConstructionDeveloperVerifier.verifyComponentEndpoint(instance, plan,
                    owner, local, "2", 1, true);
                assertions += 2;
                String componentId = plan.idFor(owner,
                    FunctionalBlockDescriptor.EntityKind.COMPONENT, local);
                String faultSwitchId = local + "_FAULT_SWITCH";
                ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
                ElectricalRealizationSpec.ElementDeclaration faultSwitchDeclaration =
                    spec.getElementDeclaration(owner, faultSwitchId);
                CircuitElm expectedUpstream = faultSwitchDeclaration != null ?
                    receipt.getElement(owner, faultSwitchId).getElement() :
                    instance.getComponentBindings().getSingleElement(componentId);
                CircuitElm actualUpstream =
                    A04ConstructionDeveloperVerifier.verifyResistorUpstreamProjection(
                        instance, plan, receipt, owner, local);
                require(actualUpstream == expectedUpstream,
                    label + " resistor upstream projection selected the wrong owned element: " +
                        owner + "/" + local);
                if (plan.isControlledIndicator() && "RPD".equals(local)) {
                    require(!entry.getValue().isMutable(),
                        label + " RPD recipe unexpectedly declares fault mutability");
                    PhysicalPart<?> installed = instance.getPhysicalBoardRuntime()
                        .getInstalledPart(componentId);
                    require(installed instanceof PhysicalResistorPart,
                        label + " RPD has no installed physical resistor part");
                    PhysicalResistorPart resistor = (PhysicalResistorPart) installed;
                    ResistorSecondaryOpenPath path = resistor.getSecondaryOpenPath();
                    require(path != null && resistor.getElement() ==
                            instance.getComponentBindings().getSingleElement(componentId) &&
                            containsIdentity(instance.getComponentBindings()
                                .getAuxiliaryElements(componentId), path.getSimulationElement()),
                        label + " immutable RPD lost its owned secondary open path");
                    sawImmutableRpd = true;
                }
            }
        }
        if (plan.isControlledIndicator())
            require(sawImmutableRpd, label + " did not exercise immutable RPD.2");
    }

    private static void verifyInventoryNegatives(
            BoundedGeneratedBoardAssembler.Result candidate,
            BoundedGeneratedBoardAssembler.Result foreignCandidate,
            Vector<CircuitElm> testOnlyElements) {
        GeneratedBoardInstance instance = candidate.getInstance();
        BoundedAssemblyPlan plan = candidate.getPlan();
        ConstructionReceipt receipt = candidate.getConstructionReceiptForDeveloperVerification();
        Vector<CircuitElm> actual = instance.getSimulationElements();
        Vector<CircuitElm> receiptElements = receipt.getElements();

        require(!receiptElements.isEmpty(), "negative inventory canary has no declared elements");
        Vector<CircuitElm> missingDeclared = new Vector<CircuitElm>(actual);
        require(removeIdentity(missingDeclared, receiptElements.firstElement()),
            "could not remove a declared element from the cloned graph");
        expectInventoryReject(instance, plan, receipt, missingDeclared,
            "A04 contract: final element inventory missing owned element",
            "dropped declared element");

        Vector<CircuitElm> docking = connectorDockingAttachments(instance);
        require(!docking.isEmpty(), "negative inventory canary has no connector service element");
        CircuitElm serviceElement = docking.firstElement();
        require(containsIdentity(actual, serviceElement) &&
                !containsIdentity(receiptElements, serviceElement),
            "selected connector element is not a post-receipt service addition");
        Vector<CircuitElm> missingService = new Vector<CircuitElm>(actual);
        require(removeIdentity(missingService, serviceElement),
            "could not remove a service element from the cloned graph");
        expectInventoryReject(instance, plan, receipt, missingService,
            "A04 contract: final element inventory missing owned element",
            "dropped owned service element");

        WireElm unowned = new WireElm(1000, 1000);
        unowned.x2 = 1016;
        unowned.y2 = 1000;
        unowned.setPoints();
        testOnlyElements.add(unowned);
        Vector<CircuitElm> extra = new Vector<CircuitElm>(actual);
        extra.add(unowned);
        expectInventoryReject(instance, plan, receipt, extra,
            "A04 contract: final element inventory contains unowned element",
            "appended unowned solver element");

        Vector<CircuitElm> duplicate = new Vector<CircuitElm>(actual);
        duplicate.add(actual.firstElement());
        expectInventoryReject(instance, plan, receipt, duplicate,
            "A04 contract: final element inventory contains duplicate identity",
            "duplicated solver element identity");

        expectInventoryReject(instance, plan,
            foreignCandidate.getConstructionReceiptForDeveloperVerification(), actual,
            "A04 contract: construction receipt is not the candidate's finished owner",
            "foreign finished construction receipt");
    }

    private static Vector<CircuitElm> connectorDockingAttachments(
            GeneratedBoardInstance instance) {
        String connectorId = null;
        for (String componentId : instance.getBoard().getComponentIds()) {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            if (component != null && component.getPhysicalPackage().isConnector()) {
                connectorId = componentId;
                break;
            }
        }
        require(connectorId != null, "assembled board has no declared connector package");
        PhysicalBoardInstallationProvider.Scoped provider =
            instance.getPhysicalBoardRuntime().getScopedMutationCapability(connectorId);
        require(provider != null && provider.getMutationSlot() instanceof PhysicalMutationSlot.Docking,
            "connector does not expose its scoped docking owner");
        return ((PhysicalMutationSlot.Docking) provider.getMutationSlot())
            .getDockingAttachments();
    }

    private static void verifyImmutableRpdRetargetNegative(
            BoundedGeneratedBoardAssembler.Result result) {
        GeneratedBoardInstance instance = result.getInstance();
        BoundedAssemblyPlan plan = result.getPlan();
        String owner = null;
        ComposedBlockContribution.ResistorRecipe rpd = null;
        for (Map.Entry<String, ComposedBlockContribution> block : plan.getBlocks().entrySet()) {
            ComposedBlockContribution.ResistorRecipe candidate =
                block.getValue().getResistor("RPD");
            if (candidate != null) {
                owner = block.getKey();
                rpd = candidate;
                break;
            }
        }
        require(owner != null && rpd != null && !rpd.isMutable(),
            "retarget negative lacks an immutable RPD recipe");
        String componentId = plan.idFor(owner,
            FunctionalBlockDescriptor.EntityKind.COMPONENT, "RPD");
        String padId = plan.idFor(owner, FunctionalBlockDescriptor.EntityKind.PAD, "RPD.2");
        GeneratedComponentConnectionBinding binding =
            instance.getConnectionBindings().get(componentId, padId);
        CircuitMeasurementEndpoint originalEndpoint = binding.getComponentEndpoint();
        CircuitElm primary = instance.getComponentBindings().getSingleElement(componentId);
        Vector<CircuitElm> ownerElementsBefore = instance.getSimulationElements();
        boolean rejected = false;
        try {
            binding.setComponentEndpoint(new CircuitPostMeasurementEndpoint(primary, 1));
            try {
                A04ConstructionDeveloperVerifier.verifyComponentEndpoint(instance, plan,
                    owner, "RPD", "2", 1, true);
            } catch (IllegalStateException failure) {
                require(failure.getMessage() != null && failure.getMessage().contains(
                        "A04 runtime: secondary endpoint does not match physical terminal"),
                    "retargeted RPD.2 rejected for the wrong reason: " + failure.getMessage());
                rejected = true;
            }
        } finally {
            binding.setComponentEndpoint(originalEndpoint);
        }
        require(rejected, "primary-post retarget of immutable RPD.2 was accepted");
        require(binding.getComponentEndpoint() == originalEndpoint &&
                sameIdentitySequence(ownerElementsBefore, instance.getSimulationElements()),
            "RPD.2 endpoint or owner graph was not restored after the negative canary");
        A04ConstructionDeveloperVerifier.verifyComponentEndpoint(instance, plan,
            owner, "RPD", "2", 1, true);
        assertions++;
    }

    private static void expectInventoryReject(GeneratedBoardInstance instance,
            BoundedAssemblyPlan plan, ConstructionReceipt receipt,
            Vector<CircuitElm> actual, String message, String label) {
        try {
            A04ConstructionDeveloperVerifier.verifyElementInventory(instance, plan, receipt,
                actual);
        } catch (IllegalStateException rejected) {
            require(rejected.getMessage() != null && rejected.getMessage().contains(message),
                label + " rejected for the wrong reason: " + rejected.getMessage());
            return;
        }
        throw new AssertionError(label + " passed the exact element inventory check");
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        for (CircuitElm element : elements)
            if (element == target) return true;
        return false;
    }

    private static int countIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        int count = 0;
        for (CircuitElm element : elements)
            if (element == target) count++;
        return count;
    }

    private static boolean removeIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index) == target) {
                elements.remove(index);
                return true;
            }
        }
        return false;
    }

    private static boolean sameIdentitySequence(Vector<CircuitElm> first,
            Vector<CircuitElm> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++)
            if (first.get(index) != second.get(index)) return false;
        return true;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
