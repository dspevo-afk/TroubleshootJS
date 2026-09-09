package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

        TroubleshootBoard board = new TroubleshootBoard(declarations.getBoardFamilyId());
        addEnvelopeNets(spec, board);

        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            board.addComponent(new BoardComponent(part.getComponentId(), part.getPublicType(),
                part.getPhysicalPackage(), part.getDesignator()));

        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals())
                board.addPad(new BoardPad(terminal.getPadId(), part.getComponentId(),
                    terminal.getTerminalId(), terminal.getNetId()));
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs())
            board.addPowerInput(new ExternalBoardPowerInput(input.getInputId(),
                input.getPositivePadId(), input.getReturnPadId(), input.getPositiveNetId(),
                input.getReturnNetId()));
        board.validate();

        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            specifications.addPhysicalDefinition(part.getComponentId(),
                part.getSpecification(), part.getNameplate(), part.getPhysicalPackage());
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs())
            specifications.addPowerInputNameplate(new PowerInputNameplate(input.getInputId(),
                input.getNominalVoltage()));
        specifications.seal();
        return new PhysicalConstructionMetadata(plan, spec, board, specifications, declarations);
    }

    private static void addEnvelopeNets(ElectricalRealizationSpec spec,
            TroubleshootBoard board) {
        for (String netId : spec.getExpectedNetIds())
            addNet(board, netId);
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
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        if (metadata.getPlan() != plan || metadata.getSpec() != spec ||
                constructionReceipt.getSpec() != spec)
            throw new IllegalArgumentException("Physical materialization provenance mismatch");
        if (!constructionReceipt.belongsToFinishedContext(spec, metadata.getBoard()))
            throw new IllegalArgumentException("Physical materialization receipt belongs to another construction context");
        if (constructionReceipt.isAborted())
            throw new IllegalStateException("Cannot materialize an aborted electrical receipt");
        if (constructionReceipt.getBoard() != metadata.getBoard() ||
                runtime.getBoard() != metadata.getBoard())
            throw new IllegalArgumentException("Physical runtime belongs to another board");
        if (!runtime.getSlots().isEmpty() || !runtime.getPhysicalParts().isEmpty())
            throw new IllegalStateException("Physical runtime has already been materialized");

        PhysicalConstructionDeclarations declarations = metadata.getDeclarations();
        /* Metadata is an immutable value, but it is still an input boundary:
         * callers must not be able to pair a valid board/receipt with a
         * declaration graph that was assembled by another provider.  Re-run
         * the exact package, terminal, backing, and fault correspondence
         * proof immediately before the first runtime mutation. */
        validateDeclarations(plan, spec, declarations);
        Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
        TreeMap<String, GeneratedFaultCandidate> candidatesByComponent =
            new TreeMap<String, GeneratedFaultCandidate>();
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts()) {
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
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts()) {
            if (slots.put(part.getComponentId(), runtime.createSlot(part.getComponentId())) != null)
                throw new IllegalStateException("Duplicate physical slot declaration: " +
                    part.getComponentId());
        }

        TreeMap<String, BoundedGeneratedBoardAssembler.RuntimeTarget> runtimeTargets =
            new TreeMap<String, BoundedGeneratedBoardAssembler.RuntimeTarget>();
        TreeMap<String, LEDElm> operationalLeds = new TreeMap<String, LEDElm>();

        /* Materialization follows the provider-declared board inventory.  The
         * part policy selects the operation; category ordering is not a
         * historical identity contract. */
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts()) {
            if (part.isMutableResistor())
                materializeMutable(runtime, slots.get(part.getComponentId()), part, plan,
                    constructionReceipt, candidatesByComponent, selected, runtimeTargets);
            else
                materializeFixed(runtime, slots.get(part.getComponentId()), part,
                    constructionReceipt, operationalLeds);
        }

        runtime.validateSupportedCompositionProviders();
        runtime.validate();
        return new PhysicalMaterializationReceipt(plan, spec, constructionReceipt, runtime,
            runtimeTargets,
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
            inventoryId, capabilityId, part.getProviderId(),
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

    /**
     * Validates provider declarations at the physical mutation boundary.
     * Package-private visibility lets the native contract exercise this exact
     * pre-mutation gate with deliberately foreign declarations.
     */
    static void validateDeclarations(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, PhysicalConstructionDeclarations declarations) {
        List<PhysicalConstructionPartDeclaration> all = declarations.getBoardParts();
        if (all.size() != spec.getPackageMap().getPackageCount())
            throw new IllegalStateException("Physical declaration/package count mismatch");
        TreeMap<String, PhysicalConstructionPartDeclaration> byComponent =
            new TreeMap<String, PhysicalConstructionPartDeclaration>();
        Set<String> padIds = new HashSet<String>();
        Set<String> actualFaultComponents = new HashSet<String>();
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
            List<ElectricalUnitPackageMap.Unit> units = unitsFor(spec, part.getComponentId());
            if (units.isEmpty())
                throw new IllegalStateException("Physical component has no electrical unit: " +
                    part.getComponentId());
            for (ElectricalUnitPackageMap.Unit unit : units)
                if (!part.getOwnerKey().equals(unit.getOwnerKey()))
                    throw new IllegalStateException("Physical unit owner mismatch: " +
                        part.getComponentId());
            validateTerminals(spec, part, units, padIds);
            validateBacking(spec, part);
            validateFaultDeclaration(spec, part, provider);
            if (part.hasFault()) actualFaultComponents.add(part.getComponentId());
        }
        if (!byComponent.keySet().equals(spec.getPackageMap().getPackages().keySet()))
            throw new IllegalStateException("Physical declarations do not cover package map");
        Set<String> expectedFaultComponents = new HashSet<String>(
            plan.getDecisionOwners().values());
        if (!expectedFaultComponents.equals(actualFaultComponents))
            throw new IllegalStateException("Physical fault declaration set changed");
        validateInputDeclarations(spec, declarations);
    }

    private static List<ElectricalUnitPackageMap.Unit> unitsFor(
            ElectricalRealizationSpec spec, String componentId) {
        ArrayList<ElectricalUnitPackageMap.Unit> result =
            new ArrayList<ElectricalUnitPackageMap.Unit>();
        for (ElectricalUnitPackageMap.Unit unit : spec.getPackageMap().getUnits().values())
            if (componentId.equals(unit.getComponentId())) result.add(unit);
        return result;
    }

    private static void validateTerminals(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part,
            List<ElectricalUnitPackageMap.Unit> units, Set<String> padIds) {
        TreeSet<String> expectedTerminals = new TreeSet<String>();
        TreeSet<String> expectedPackageTerminals = new TreeSet<String>();
        for (ElectricalUnitPackageMap.Unit unit : units) {
            expectedTerminals.addAll(unit.getTerminalIds());
            expectedPackageTerminals.addAll(unit.getPackageTerminalByUnitTerminal().values());
        }
        TreeSet<String> actualTerminals = new TreeSet<String>();
        TreeSet<String> actualPackageTerminals = new TreeSet<String>();
        for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
            if (!terminal.hasElectricalProvenance() ||
                    !part.getOwnerKey().equals(terminal.getOwnerKey()) ||
                    !part.getComponentId().equals(terminal.getComponentId()))
                throw new IllegalStateException("Physical terminal provenance changed: " +
                    part.getComponentId());
            ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
                terminal.getOwnerKey(), terminal.getLocalId(), terminal.getTerminalId());
            ElectricalRealizationSpec.PadBindingSpec pad = spec.getPadBinding(
                terminal.getPadId());
            if (mapping == null || pad == null ||
                    !part.getComponentId().equals(mapping.getComponentId()) ||
                    !part.getComponentId().equals(pad.getComponentId()) ||
                    !terminal.getPackageTerminalId().equals(mapping.getPackageTerminalId()) ||
                    !terminal.getPackageTerminalId().equals(pad.getTerminalId()) ||
                    !terminal.getNetId().equals(mapping.getNetId()) ||
                    !terminal.getNetId().equals(pad.getNetId()) ||
                    !terminal.getOwnerKey().equals(pad.getOwnerKey()) ||
                    !terminal.getLocalId().equals(pad.getLocalId()) ||
                    !terminal.getTerminalId().equals(pad.getTerminalId()) ||
                    !pad.getPadId().equals(terminal.getPadId()) ||
                    !actualTerminals.add(terminal.getTerminalId()) ||
                    !actualPackageTerminals.add(terminal.getPackageTerminalId()) ||
                    !padIds.add(terminal.getPadId()))
                throw new IllegalStateException("Incomplete physical terminal declaration: " +
                    part.getComponentId());
            boolean matchedUnit = false;
            for (ElectricalUnitPackageMap.Unit unit : units) {
                String packageTerminal = unit.getPackageTerminalByUnitTerminal().get(
                    terminal.getTerminalId());
                if (terminal.getPackageTerminalId().equals(packageTerminal)) {
                    matchedUnit = true;
                    break;
                }
            }
            if (!matchedUnit)
                throw new IllegalStateException("Physical terminal is absent from unit map: " +
                    part.getComponentId() + "/" + terminal.getTerminalId());
        }
        if (!expectedTerminals.equals(actualTerminals) ||
                !expectedPackageTerminals.equals(actualPackageTerminals) ||
                !expectedPackageTerminals.equals(new TreeSet<String>(
                    part.getPhysicalPackage().getTerminalIds())))
            throw new IllegalStateException("Physical terminal set does not match package/unit map: " +
                part.getComponentId());
    }

    private static void validateFaultDeclaration(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part,
            ElectricalRealizationSpec.ProviderDeclaration provider) {
        if (!part.hasFault()) return;
        if (provider.getContribution() == null)
            throw new IllegalStateException("Faulted physical part has no local contribution: " +
                part.getComponentId());
        ComposedBlockContribution contribution = provider.getContribution();
        ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
        String targetLocalId = fault.getTargetComponentLocalId();
        String expectedComponentId = spec.getComponentId(provider.getOwnerKey(), targetLocalId);
        if (!expectedComponentId.equals(part.getComponentId()) ||
                !expectedComponentId.equals(part.getRepairComponentId()) ||
                fault.getKind() != part.getFaultKind() ||
                !contribution.getFaultLocalId().equals(part.getFaultLocalId()) ||
                contribution.getFaultEffectiveOhms() != part.getFaultEffectiveOhms() ||
                !part.getOwnerKey().equals(part.getRepairOwnerKey()) ||
                !contribution.getRepairLocalComponentId().equals(
                    part.getRepairLocalComponentId()) ||
                !provider.getOwnerKey().equals(part.getOwnerKey()))
            throw new IllegalStateException("Physical fault declaration changed accepted ownership: " +
                part.getComponentId());
        PhysicalConstructionProvider physicalProvider =
            StandardPhysicalConstructionProviders.provider(provider.getProviderId(),
                provider.getProviderVersion());
        if (!physicalProvider.getFaultFamilyId().equals(part.getFaultFamilyId()))
            throw new IllegalStateException("Physical fault family provenance changed: " +
                part.getComponentId());
        if (fault.getKind() == ComposedBlockContribution.FaultSpec.Kind.OPEN) {
            String faultElement = targetLocalId + "_FAULT_SWITCH";
            String faultOwner = provider.getOwnerKey();
            if (!faultOwner.equals(part.getFaultOwnerKey()) ||
                    !faultElement.equals(part.getFaultElementId()))
                throw new IllegalStateException("Physical open fault switch changed ownership: " +
                    part.getComponentId());
            requireElement(spec, faultOwner, faultElement, "SWITCH", part.getComponentId());
        } else if (part.getFaultOwnerKey() != null || part.getFaultElementId() != null) {
            throw new IllegalStateException("Physical value fault unexpectedly has a switch: " +
                part.getComponentId());
        }
    }

    private static void validateBacking(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part) {
        ElectricalRealizationSpec.ElementDeclaration primary =
            spec.getElementDeclaration(part.getBackingOwnerKey(), part.getBackingElementId());
        String expectedKind = "RESISTOR".equals(part.getPublicType()) ? "RESISTOR" :
            "NMOS_TRANSISTOR".equals(part.getPublicType()) ? "NMOS" :
            "LED".equals(part.getPublicType()) ? "LED" : "SWITCH";
        if (primary == null || !part.getComponentId().equals(primary.getComponentId()) ||
                !expectedKind.equals(primary.getKind()))
            throw new IllegalStateException("Foreign primary physical backing: " +
                part.getComponentId());

        String localId = part.getTerminals().get(0).getLocalId();
        if (part.hasSecondary()) {
            ElectricalRealizationSpec.ElementDeclaration secondary =
                spec.getElementDeclaration(part.getSecondaryOwnerKey(), part.getSecondaryElementId());
            ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
                part.getOwnerKey(), localId, "2");
            ElectricalRealizationSpec.EndpointRef endpoint = mapping == null ? null :
                mapping.getComponentEndpoint();
            if (secondary == null || !"FAULT_HELPER".equals(secondary.getKind()) ||
                    !part.getComponentId().equals(secondary.getComponentId()) || endpoint == null ||
                    !part.getSecondaryOwnerKey().equals(endpoint.getOwnerKey()) ||
                    !part.getSecondaryElementId().equals(endpoint.getElementId()) ||
                    !"2".equals(endpoint.getTerminalId()))
                throw new IllegalStateException("Foreign secondary physical backing: " +
                    part.getComponentId());
        }
        if (part.hasAttachments()) {
            validateAttachment(spec, part, part.getFirstAttachmentOwnerKey(),
                part.getFirstAttachmentElementId(), true, localId);
            validateAttachment(spec, part, part.getSecondAttachmentOwnerKey(),
                part.getSecondAttachmentElementId(), false, localId);
        }
        if (part.hasFault()) {
            if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.OPEN)
                requireElement(spec, part.getFaultOwnerKey(), part.getFaultElementId(),
                    "SWITCH", part.getComponentId());
            if (part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE &&
                    part.getFaultElementId() != null)
                throw new IllegalStateException("Incorrect-resistance physical fault has switch backing");
        }
    }

    private static void validateAttachment(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part, String owner, String id,
            boolean first, String localId) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
            spec.getElementDeclaration(owner, id);
        if (declaration == null || !"WIRE".equals(declaration.getKind()))
            throw new IllegalStateException("Missing or foreign physical attachment " + owner +
                "/" + id);
        String expectedLocal = localId + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        if (part.getOwnerKey().equals(owner)) {
            if (!expectedLocal.equals(id))
                throw new IllegalStateException("Attachment identity changed for " +
                    part.getComponentId());
            return;
        }
        if (!"device".equals(owner))
            throw new IllegalStateException("Foreign attachment owner for " + part.getComponentId());
        ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
            part.getOwnerKey(), localId, first ? "1" : "2");
        ElectricalRealizationSpec.EndpointRef expected = mapping == null ? null :
            mapping.getComponentEndpoint();
        ElectricalRealizationSpec.BridgeSpec bridge = spec.getBridgeSpecs().get(id);
        if (bridge == null || expected == null ||
                !(expected.equals(bridge.getFirst()) || expected.equals(bridge.getSecond())))
            throw new IllegalStateException("Device attachment is not a declared bridge for " +
                part.getComponentId());
    }

    private static void requireElement(ElectricalRealizationSpec spec, String owner,
            String id, String kind, String componentId) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
            spec.getElementDeclaration(owner, id);
        if (declaration == null || !kind.equals(declaration.getKind()) ||
                (componentId != null && !componentId.equals(declaration.getComponentId())))
            throw new IllegalStateException("Missing or foreign physical backing " + owner +
                "/" + id);
    }

    private static void validateInputDeclarations(ElectricalRealizationSpec spec,
            PhysicalConstructionDeclarations declarations) {
        Set<String> inputs = new HashSet<String>();
        for (PhysicalExternalInputDeclaration input : declarations.getExternalInputs()) {
            if (!inputs.add(input.getInputId()))
                throw new IllegalStateException("Duplicate physical external input: " +
                    input.getInputId());
            if (input.getPositivePadId().equals(input.getReturnPadId()))
                throw new IllegalStateException("External input uses one pad twice");
            ElectricalRealizationSpec.PowerInputSpec expected = spec.getPowerInputs().get(
                input.getInputId());
            if (expected == null || !"device".equals(input.getProviderOwnerKey()) ||
                    input.getNominalVoltage() != ElectricalRealizationSpec.EXTERNAL_SUPPLY_VOLTS ||
                    !expected.getPositivePadId().equals(input.getPositivePadId()) ||
                    !expected.getReturnPadId().equals(input.getReturnPadId()) ||
                    !expected.getPositiveNetId().equals(input.getPositiveNetId()) ||
                    !expected.getReturnNetId().equals(input.getReturnNetId()))
                throw new IllegalStateException("Physical external input mapping changed: " +
                    input.getInputId());
        }
        if (!inputs.equals(spec.getPowerInputs().keySet()))
            throw new IllegalStateException("Physical external input inventory changed");
    }
}
