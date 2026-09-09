package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Generic physical board/runtime materializer for the bounded A04 envelope.
 * Providers contribute declarations; this class owns the one board runtime,
 * inventories, slots, candidates, and fixed/mutable resistor distinction.
 */
final class PhysicalConstructionMaterializer {
    private PhysicalConstructionMaterializer() { }

    static PhysicalConstructionMetadata describe(BoundedAssemblyPlan plan) {
        if (plan == null)
            throw new IllegalArgumentException("Assembly plan is required");
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        PhysicalConstructionDeclarations declarations =
            StandardPhysicalConstructionProviders.describe(plan);
        validateDeclarations(plan, spec, declarations);

        TroubleshootBoard board = new TroubleshootBoard(plan.isControlledIndicator() ?
            ControlledIndicatorDeviceBehavior.FAMILY_ID : "RESISTIVE_COUPLING");
        addEnvelopeNets(plan, board);

        /* Keep the accepted component/slot order explicit; it is not identity. */
        for (PhysicalConstructionPartDeclaration part : declarations.getSlotParts(
                plan.isControlledIndicator()))
            board.addComponent(new BoardComponent(part.getComponentId(), part.getPublicType(),
                part.getPhysicalPackage(), part.getDesignator()));

        /* Controlled legacy board pads expose adapters before local parts. */
        ArrayList<PhysicalConstructionPartDeclaration> padParts =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        padParts.addAll(declarations.getDeviceParts());
        padParts.addAll(declarations.getLocalParts());
        for (PhysicalConstructionPartDeclaration part : padParts)
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals())
                board.addPad(new BoardPad(terminal.getPadId(), part.getComponentId(),
                    terminal.getTerminalId(), terminal.getNetId()));
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs())
            board.addPowerInput(new ExternalBoardPowerInput(input.getInputId(),
                input.getPositivePadId(), input.getReturnPadId(), input.getPositiveNetId(),
                input.getReturnNetId()));
        board.validate();

        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        for (PhysicalConstructionPartDeclaration part : padParts)
            specifications.addPhysicalDefinition(part.getComponentId(),
                part.getSpecification(), part.getNameplate(), part.getPhysicalPackage());
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs())
            specifications.addPowerInputNameplate(new PowerInputNameplate(input.getInputId(),
                input.getNominalVoltage()));
        specifications.seal();
        return new PhysicalConstructionMetadata(board, specifications, declarations);
    }

    private static void addEnvelopeNets(BoundedAssemblyPlan plan, TroubleshootBoard board) {
        if (plan.isControlledIndicator()) {
            addNet(board, plan.netFor("load", "SUPPLY"));
            addNet(board, plan.netFor("driver", "CONTROL"));
            addNet(board, plan.netFor("load", "LED_NODE"));
            addNet(board, plan.netFor("driver", "SWITCHED_SINK"));
            addNet(board, plan.netFor("driver", "GATE"));
            addNet(board, plan.netFor("driver", "RETURN"));
        } else {
            addNet(board, plan.netFor("source", "SUPPLY"));
            addNet(board, plan.netFor("source", "OUT"));
            addNet(board, plan.netFor("source", "RETURN"));
        }
    }

    private static void addNet(TroubleshootBoard board, String netId) {
        if (board.getNet(netId) == null)
            board.addNet(new BoardNet(netId));
    }

    static PhysicalMaterializationReceipt materialize(PhysicalBoardRuntime runtime,
            PhysicalConstructionMetadata metadata, BoundedAssemblyPlan plan,
            ConstructionReceipt constructionReceipt) {
        if (runtime == null || metadata == null || plan == null || constructionReceipt == null)
            throw new IllegalArgumentException("Physical materialization inputs are required");
        if (constructionReceipt.isAborted())
            throw new IllegalStateException("Cannot materialize an aborted electrical receipt");
        if (runtime.getBoard() != metadata.getBoard())
            throw new IllegalArgumentException("Physical runtime belongs to another board");

        PhysicalConstructionDeclarations declarations = metadata.getDeclarations();
        Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
        TreeMap<String, GeneratedFaultCandidate> candidatesByComponent =
            new TreeMap<String, GeneratedFaultCandidate>();
        for (PhysicalConstructionPartDeclaration part : declarations.getLocalParts()) {
            if (!part.hasFault())
                continue;
            GeneratedFaultCandidate candidate = createCandidate(part, plan, constructionReceipt);
            if (candidatesByComponent.put(part.getComponentId(), candidate) != null)
                throw new IllegalStateException("Duplicate physical fault owner: " +
                    part.getComponentId());
            candidates.add(candidate);
        }
        if (candidates.isEmpty())
            throw new IllegalStateException("No physical fault candidates were declared");
        GeneratedFaultEngine.clearAll(candidates);
        validateCandidates(candidates, declarations, plan);
        String selectedComponent = plan.getDecisionOwners().get(plan.getFaultDecisionKey());
        GeneratedFaultCandidate selected = candidatesByComponent.get(selectedComponent);
        if (selected == null)
            throw new IllegalStateException("Resolved fault target has no physical declaration: " +
                selectedComponent);
        selected = GeneratedFaultEngine.selectHypothesis(selected.getHypothesisKey(), candidates);

        TreeMap<String, PhysicalBoardSlot> slots = new TreeMap<String, PhysicalBoardSlot>();
        for (PhysicalConstructionPartDeclaration part : declarations.getSlotParts(
                plan.isControlledIndicator())) {
            if (slots.put(part.getComponentId(), runtime.createSlot(part.getComponentId())) != null)
                throw new IllegalStateException("Duplicate physical slot declaration: " +
                    part.getComponentId());
        }

        TreeMap<String, BoundedGeneratedBoardAssembler.RuntimeTarget> runtimeTargets =
            new TreeMap<String, BoundedGeneratedBoardAssembler.RuntimeTarget>();
        TreeMap<String, LEDElm> operationalLeds = new TreeMap<String, LEDElm>();

        /* Append-only runtime order is part of the accepted physical receipt. */
        for (PhysicalConstructionPartDeclaration part : declarations.getLocalParts())
            if (part.isMutableResistor())
                materializeMutable(runtime, slots.get(part.getComponentId()), part, plan,
                    constructionReceipt, candidatesByComponent, selected, runtimeTargets);
        for (PhysicalConstructionPartDeclaration part : declarations.getSlotParts(
                plan.isControlledIndicator()))
            if (!part.isMutableResistor())
                materializeFixed(runtime, slots.get(part.getComponentId()), part,
                    constructionReceipt, operationalLeds);

        runtime.validateSupportedCompositionProviders();
        runtime.validate();
        return new PhysicalMaterializationReceipt(runtime, runtimeTargets,
            new ArrayList<GeneratedFaultCandidate>(candidates), selected, operationalLeds);
    }

    private static GeneratedFaultCandidate createCandidate(
            PhysicalConstructionPartDeclaration part, BoundedAssemblyPlan plan,
            ConstructionReceipt receipt) {
        if (!(part.getSpecification() instanceof ResistorNameplate))
            throw new IllegalStateException("Faulted physical part is not a resistor: " +
                part.getComponentId());
        ResistorElm resistor = (ResistorElm) element(receipt, part.getBackingOwnerKey(),
            part.getBackingElementId(), "RESISTOR");
        String faultId = part.getComponentId() + "/fault/" + part.getFaultLocalId();
        if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE)
            return GeneratedFaultEngine.resistorIncorrectValue(faultId,
                part.getFaultFamilyId(), plan.getRequest().getDescriptor().getRootSeed(),
                part.getComponentId(), resistor,
                ((ResistorNameplate) part.getSpecification()).getNominalResistanceOhms(),
                part.getFaultEffectiveOhms());
        if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.OPEN) {
            SwitchElm faultSwitch = (SwitchElm) element(receipt, part.getFaultOwnerKey(),
                part.getFaultElementId(), "SWITCH");
            return GeneratedFaultEngine.resistorOpen(faultId, part.getFaultFamilyId(),
                plan.getRequest().getDescriptor().getRootSeed(), part.getComponentId(),
                faultSwitch);
        }
        throw new IllegalStateException("Unsupported physical fault policy");
    }

    private static void validateCandidates(Vector<GeneratedFaultCandidate> candidates,
            PhysicalConstructionDeclarations declarations, BoundedAssemblyPlan plan) {
        TreeMap<String, PhysicalConstructionPartDeclaration> byComponent =
            new TreeMap<String, PhysicalConstructionPartDeclaration>();
        for (PhysicalConstructionPartDeclaration part : declarations.getLocalParts())
            if (part.hasFault()) byComponent.put(part.getComponentId(), part);
        for (GeneratedFaultCandidate candidate : candidates) {
            GeneratedFault fault = candidate.getFault();
            PhysicalConstructionPartDeclaration part = byComponent.get(
                fault.getTargetComponentId());
            if (part == null)
                throw new IllegalStateException("Generated fault has a foreign physical owner");
            String expectedId = part.getComponentId() + "/fault/" + part.getFaultLocalId();
            GeneratedFaultType expectedType = part.getFaultKind() ==
                ComposedBlockContribution.FaultSpec.Kind.OPEN ?
                GeneratedFaultType.RESISTOR_OPEN : GeneratedFaultType.RESISTOR_INCORRECT_VALUE;
            if (!expectedId.equals(fault.getId()) || fault.getType() != expectedType ||
                    !part.getComponentId().equals(fault.getTargetComponentId()) ||
                    !part.getFaultFamilyId().equals(fault.getCircuitFamilyId()))
                throw new IllegalStateException("Physical fault declaration mismatch: " +
                    part.getComponentId());
            if (expectedType == GeneratedFaultType.RESISTOR_INCORRECT_VALUE &&
                    fault.getEffectiveValue() != part.getFaultEffectiveOhms())
                throw new IllegalStateException("Physical incorrect-resistance value changed: " +
                    part.getComponentId());
        }
    }

    private static void materializeMutable(PhysicalBoardRuntime runtime,
            PhysicalBoardSlot slot, PhysicalConstructionPartDeclaration part,
            BoundedAssemblyPlan plan, ConstructionReceipt receipt,
            Map<String, GeneratedFaultCandidate> candidatesByComponent,
            GeneratedFaultCandidate selected,
            Map<String, BoundedGeneratedBoardAssembler.RuntimeTarget> runtimeTargets) {
        if (slot == null || !(part.getSpecification() instanceof ResistorNameplate))
            throw new IllegalStateException("Mutable physical resistor slot is incomplete");
        ResistorElm resistor = (ResistorElm) element(receipt, part.getBackingOwnerKey(),
            part.getBackingElementId(), "RESISTOR");
        ResistorSecondaryOpenPath secondary = part.hasSecondary() ?
            receipt.getSecondary(part.getSecondaryOwnerKey(), part.getSecondaryElementId()).getPath() :
            null;
        WireElm firstAttachment = (WireElm) element(receipt,
            part.getFirstAttachmentOwnerKey(), part.getFirstAttachmentElementId(), "WIRE");
        WireElm secondAttachment = (WireElm) element(receipt,
            part.getSecondAttachmentOwnerKey(), part.getSecondAttachmentElementId(), "WIRE");
        GeneratedFaultCandidate candidate = candidatesByComponent.get(part.getComponentId());
        if (candidate == null)
            throw new IllegalStateException("Mutable physical resistor has no fault candidate: " +
                part.getComponentId());
        GeneratedFaultBinding binding = candidate != null && candidate.getFault() == selected.getFault()
            ? candidate.getBinding() : null;
        ResistorNameplate specification = (ResistorNameplate) part.getSpecification();
        PhysicalResistorPart original = new PhysicalResistorPart(
            part.getComponentId() + "/part/original", specification, specification,
            part.getNameplate(), resistor, binding, secondary,
            ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                part.getComponentId()));
        String inventoryId = part.getComponentId() + "/inventory/replacements";
        PhysicalPartInventory<PhysicalResistorPart> inventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime, inventoryId,
                PhysicalResistorPart.class);
        inventory.add(original);
        ReplaceableComponentSlot componentSlot = new ReplaceableComponentSlot(
            part.getComponentId(), specification, original, firstAttachment,
            secondAttachment, slot);
        ResistorReplacementCatalog catalog = new ResistorReplacementCatalog();
        String capabilityId = part.getComponentId() + "/capability/replaceable-resistor";
        runtime.registerCapability(new ReplaceableResistorBoardCapability(capabilityId,
            componentSlot, inventory, catalog,
            part.getComponentId().equals(part.getDesignator()) ? null : part.getDesignator()));

        PhysicalBoardSlot repairSlot = runtime.getSlot(part.getRepairComponentId());
        if (repairSlot == null || runtime.getWorkbenchPartsProvider(part.getRepairComponentId()) == null)
            throw new IllegalStateException("Physical repair target is not yet registered: " +
                part.getRepairComponentId());
        if (!part.getRepairComponentId().equals(part.getComponentId()))
            throw new IllegalStateException("Unsupported cross-component resistor repair target: " +
                part.getRepairComponentId());
        runtimeTargets.put(part.getOwnerKey(), new BoundedGeneratedBoardAssembler.RuntimeTarget(
            part.getOwnerKey(), part.getComponentId(), slot.getId(), original.getId(),
            inventoryId, capabilityId, part.getRuntimeProviderId(),
            candidate.getFault().getId(), part.getRepairComponentId(), repairSlot.getId()));
    }

    private static void materializeFixed(PhysicalBoardRuntime runtime,
            PhysicalBoardSlot slot, PhysicalConstructionPartDeclaration part,
            ConstructionReceipt receipt, Map<String, LEDElm> operationalLeds) {
        if (slot == null)
            throw new IllegalStateException("Fixed physical slot is missing: " +
                part.getComponentId());
        CircuitElm backing = element(receipt, part.getBackingOwnerKey(),
            part.getBackingElementId(), null);
        FixedPhysicalPart fixed = PhysicalFoundationPartFactory.fromSlotBindings(slot,
            part.getSpecification(), part.getNameplate(), runtime.getBoard().getSimulationBindings(), backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,
                part.getComponentId()));
        slot.install(fixed);
        if ("LED".equals(part.getPublicType())) {
            if (!(backing instanceof LEDElm))
                throw new IllegalStateException("LED physical backing is not an LED element");
            operationalLeds.put(part.getComponentId(), (LEDElm) backing);
        }
    }

    private static CircuitElm element(ConstructionReceipt receipt, String ownerKey,
            String elementId, String expectedKind) {
        ElectricalConstructionContext.ElementHandle handle =
            receipt.getElement(ownerKey, elementId);
        if (expectedKind != null && !expectedKind.equals(handle.getKind()))
            throw new IllegalStateException("Physical backing kind mismatch: " + ownerKey +
                "/" + elementId);
        return handle.getElement();
    }

    private static void validateDeclarations(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, PhysicalConstructionDeclarations declarations) {
        ArrayList<PhysicalConstructionPartDeclaration> all =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        all.addAll(declarations.getLocalParts());
        all.addAll(declarations.getDeviceParts());
        if (all.size() != spec.getPackageMap().getPackageCount() ||
                all.size() != spec.getPackageMap().getUnitCount())
            throw new IllegalStateException("Physical declaration/package count mismatch");
        TreeMap<String, PhysicalConstructionPartDeclaration> byComponent =
            new TreeMap<String, PhysicalConstructionPartDeclaration>();
        Set<String> padIds = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : all) {
            if (byComponent.put(part.getComponentId(), part) != null)
                throw new IllegalStateException("Duplicate physical component declaration: " +
                    part.getComponentId());
            if (part.hasFault() && !part.isMutableResistor())
                throw new IllegalStateException("Fixed physical support part is faulted: " +
                    part.getComponentId());
            PhysicalPackage mapped = spec.getPackageMap().getPackages().get(part.getComponentId());
            String mappedOwner = spec.getPackageMap().getPackageOwners().get(part.getComponentId());
            if (mapped == null || !mapped.isEquivalentTo(part.getPhysicalPackage()) ||
                    !part.getOwnerKey().equals(mappedOwner))
                throw new IllegalStateException("Foreign or mismatched physical package: " +
                    part.getComponentId());
            ElectricalRealizationSpec.ProviderDeclaration provider =
                spec.getProviderDeclaration(part.getConstructionOwnerKey());
            if (provider == null || !part.getProviderId().equals(provider.getProviderId()) ||
                    part.getProviderVersion() != provider.getProviderVersion())
                throw new IllegalStateException("Physical provider identity changed: " +
                    part.getComponentId());
            ElectricalUnitPackageMap.Unit unit = unitFor(spec, part.getComponentId());
            if (unit == null || !part.getOwnerKey().equals(unit.getOwnerKey()))
                throw new IllegalStateException("Physical unit owner mismatch: " +
                    part.getComponentId());
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
                String mappedPackageTerminal = unit.getPackageTerminalByUnitTerminal().get(
                    terminal.getTerminalId());
                if (!part.getPhysicalPackage().getTerminalIds().contains(
                        terminal.getPackageTerminalId()) ||
                        mappedPackageTerminal == null ||
                        !mappedPackageTerminal.equals(terminal.getPackageTerminalId()) ||
                        !padIds.add(terminal.getPadId()))
                    throw new IllegalStateException("Incomplete physical terminal declaration: " +
                        part.getComponentId());
            }
            validateBacking(spec, part);
            validateFaultDeclaration(plan, spec, part);
        }
        if (!byComponent.keySet().equals(spec.getPackageMap().getPackages().keySet()))
            throw new IllegalStateException("Physical declarations do not cover package map");
        validateFaultOwnerSet(plan, declarations);
        validateInputDeclarations(plan, declarations);
    }

    /**
     * The contribution's fault metadata is not an authority for physical
     * ownership.  Reconcile it with the accepted versioned assembler mapping
     * so a malformed provider cannot make a self-consistent but foreign
     * candidate declaration pass validation.
     */
    private static void validateFaultDeclaration(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, PhysicalConstructionPartDeclaration part) {
        if (!part.hasFault())
            return;
        ElectricalRealizationSpec.ProviderDeclaration provider =
            spec.getProviderDeclaration(part.getOwnerKey());
        if (provider == null || provider.getContribution() == null)
            throw new IllegalStateException("Faulted physical part has no local contribution: " +
                part.getComponentId());
        ComposedBlockContribution contribution = provider.getContribution();
        ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
        boolean legacyResistive = !plan.isControlledIndicator() &&
            plan.getRequest().getDescriptor().getGenerator().getVersion() ==
                BoundedAssemblyRequest.GENERATOR_VERSION;
        String targetLocalId = legacyResistive ? contribution.getRepairLocalComponentId() :
            fault.getTargetComponentLocalId();
        String expectedComponentId = plan.idFor(part.getOwnerKey(), EntityKind.COMPONENT,
            targetLocalId);
        ComposedBlockContribution.FaultSpec.Kind expectedKind = legacyResistive ?
            ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE : fault.getKind();
        String expectedFamily = plan.isControlledIndicator() ?
            ControlledIndicatorDeviceBehavior.FAMILY_ID : BoundedGeneratedBoardAssembler.FAMILY_ID;
        if (!expectedComponentId.equals(part.getComponentId()) ||
                expectedKind != part.getFaultKind() ||
                !contribution.getFaultLocalId().equals(part.getFaultLocalId()) ||
                contribution.getFaultEffectiveOhms() != part.getFaultEffectiveOhms() ||
                !expectedFamily.equals(part.getFaultFamilyId()) ||
                !part.getOwnerKey().equals(part.getRepairOwnerKey()) ||
                !contribution.getRepairLocalComponentId().equals(
                    part.getRepairLocalComponentId()) ||
                !plan.idFor(part.getOwnerKey(), EntityKind.COMPONENT,
                    contribution.getRepairLocalComponentId()).equals(part.getRepairComponentId()))
            throw new IllegalStateException("Physical fault declaration changed accepted ownership: " +
                part.getComponentId());
        if (expectedKind == ComposedBlockContribution.FaultSpec.Kind.OPEN) {
            String faultElement = targetLocalId + "_FAULT_SWITCH";
            String faultOwner = PhysicalConstructionProviderSupport.requireElementOwner(
                spec, part.getOwnerKey(), faultElement);
            if (!faultOwner.equals(part.getFaultOwnerKey()) ||
                    !faultElement.equals(part.getFaultElementId()))
                throw new IllegalStateException("Physical open fault switch changed ownership: " +
                    part.getComponentId());
        } else if (part.getFaultOwnerKey() != null || part.getFaultElementId() != null) {
            throw new IllegalStateException("Physical value fault unexpectedly has a switch: " +
                part.getComponentId());
        }
    }

    private static void validateFaultOwnerSet(BoundedAssemblyPlan plan,
            PhysicalConstructionDeclarations declarations) {
        Set<String> expected = new HashSet<String>();
        String[] owners = plan.isControlledIndicator() ?
            new String[] { "driver", "load" } : new String[] { "source", "load" };
        boolean legacyResistive = !plan.isControlledIndicator() &&
            plan.getRequest().getDescriptor().getGenerator().getVersion() ==
                BoundedAssemblyRequest.GENERATOR_VERSION;
        for (String owner : owners) {
            ComposedBlockContribution contribution = plan.getBlocks().get(owner);
            if (contribution == null)
                throw new IllegalStateException("Missing physical fault contribution: " + owner);
            String local = legacyResistive ? contribution.getRepairLocalComponentId() :
                contribution.getFaultSpec().getTargetComponentLocalId();
            expected.add(plan.idFor(owner, EntityKind.COMPONENT, local));
        }
        Set<String> actual = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : declarations.getLocalParts())
            if (part.hasFault()) actual.add(part.getComponentId());
        if (!expected.equals(actual))
            throw new IllegalStateException("Physical fault owner set changed: expected " +
                expected + " but found " + actual);
    }

    private static ElectricalUnitPackageMap.Unit unitFor(ElectricalRealizationSpec spec,
            String componentId) {
        ElectricalUnitPackageMap.Unit result = null;
        for (ElectricalUnitPackageMap.Unit unit : spec.getPackageMap().getUnits().values())
            if (componentId.equals(unit.getComponentId())) {
                if (result != null)
                    throw new IllegalStateException("Multiple physical units share an unsupported visible part: " +
                        componentId);
                result = unit;
            }
        return result;
    }

    private static void validateBacking(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part) {
        ElectricalRealizationSpec.ElementDeclaration primary =
            spec.getElementDeclaration(part.getBackingOwnerKey(), part.getBackingElementId());
        if (primary == null || (primary.getComponentId() != null &&
                !part.getComponentId().equals(primary.getComponentId())))
            throw new IllegalStateException("Foreign primary physical backing: " +
                part.getComponentId());
        String expectedKind = "RESISTOR".equals(part.getPublicType()) ? "RESISTOR" :
            "NMOS_TRANSISTOR".equals(part.getPublicType()) ? "NMOS" :
            "LED".equals(part.getPublicType()) ? "LED" : "SWITCH";
        if (!expectedKind.equals(primary.getKind()))
            throw new IllegalStateException("Primary physical backing kind mismatch: " +
                part.getComponentId());
        if (part.hasSecondary())
            requireElement(spec, part.getSecondaryOwnerKey(), part.getSecondaryElementId(),
                "FAULT_HELPER");
        if (part.hasAttachments()) {
            requireElement(spec, part.getFirstAttachmentOwnerKey(),
                part.getFirstAttachmentElementId(), "WIRE");
            requireElement(spec, part.getSecondAttachmentOwnerKey(),
                part.getSecondAttachmentElementId(), "WIRE");
        }
        if (part.hasFault()) {
            if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.OPEN)
                requireElement(spec, part.getFaultOwnerKey(), part.getFaultElementId(), "SWITCH");
            if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE &&
                    part.getFaultElementId() != null)
                throw new IllegalStateException("Incorrect-resistance physical fault has switch backing");
        }
    }

    private static void requireElement(ElectricalRealizationSpec spec, String owner,
            String id, String kind) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
            spec.getElementDeclaration(owner, id);
        if (declaration == null || !kind.equals(declaration.getKind()))
            throw new IllegalStateException("Missing or foreign physical backing " + owner +
                "/" + id);
    }

    private static void validateInputDeclarations(BoundedAssemblyPlan plan,
            PhysicalConstructionDeclarations declarations) {
        Set<String> inputs = new HashSet<String>();
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs()) {
            if (!inputs.add(input.getInputId()))
                throw new IllegalStateException("Duplicate physical external input: " +
                    input.getInputId());
            if (input.getPositivePadId().equals(input.getReturnPadId()))
                throw new IllegalStateException("External input uses one pad twice");
            validateInput(plan, input);
        }
        if (inputs.size() != (plan.isControlledIndicator() ? 2 : 1))
            throw new IllegalStateException("Physical external input count changed");
    }

    private static void validateInput(BoundedAssemblyPlan plan,
            PhysicalExternalInputDeclaration input) {
        if (!"device".equals(input.getProviderOwnerKey()) ||
                input.getNominalVoltage() != ElectricalRealizationSpec.EXTERNAL_SUPPLY_VOLTS)
            throw new IllegalStateException("Physical external input ownership changed: " +
                input.getInputId());
        if (!plan.isControlledIndicator()) {
            if (!BoundedGeneratedBoardAssembler.POWER_INPUT_ID.equals(input.getInputId()) ||
                    !"J1.1".equals(input.getPositivePadId()) ||
                    !"J1.2".equals(input.getReturnPadId()) ||
                    !plan.netFor("source", "SUPPLY").equals(input.getPositiveNetId()) ||
                    !plan.netFor("source", "RETURN").equals(input.getReturnNetId()))
                throw new IllegalStateException("Legacy physical power input mapping changed");
            return;
        }
        String expectedInput;
        String expectedAdapter;
        String expectedOutputNet;
        if (ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID.equals(input.getInputId())) {
            expectedInput = ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID;
            expectedAdapter = DeviceAdapterContract.POWER_ADAPTER_KEY;
            expectedOutputNet = plan.netFor("load", "SUPPLY");
        } else if (ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID.equals(
                input.getInputId())) {
            expectedInput = ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID;
            expectedAdapter = DeviceAdapterContract.CONTROL_ADAPTER_KEY;
            expectedOutputNet = plan.netFor("driver", "CONTROL");
        } else {
            throw new IllegalStateException("Unknown controlled physical power input: " +
                input.getInputId());
        }
        String local = DeviceAdapterContract.POWER_ADAPTER_KEY.equals(expectedAdapter) ?
            "J1" : "J2";
        if (!expectedInput.equals(input.getInputId()) ||
                !plan.idFor(expectedAdapter, EntityKind.PAD, local + ".1").equals(
                    input.getPositivePadId()) ||
                !plan.idFor(expectedAdapter, EntityKind.PAD, local + ".2").equals(
                    input.getReturnPadId()) || !expectedOutputNet.equals(input.getPositiveNetId()) ||
                !plan.netFor(expectedAdapter, "RETURN").equals(input.getReturnNetId()))
            throw new IllegalStateException("Controlled physical power input mapping changed: " +
                input.getInputId());
    }
}
