package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Provider-owned pure physical declarations keyed by the electrical provider. */
interface PhysicalConstructionProvider {
    String getProviderId();
    int getVersion();
    /** Physical envelope identity supplied by the provider composition. */
    String getBoardFamilyId();
    String getBoardName();
    /** Fault candidates use the same provider-owned family identity. */
    String getFaultFamilyId();
    PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration);
}

/** Pure output of one local physical contribution provider. */
final class PhysicalConstructionContribution {
    private final List<PhysicalConstructionPartDeclaration> parts;
    private final List<PhysicalExternalInputDeclaration> externalInputs;

    PhysicalConstructionContribution(List<PhysicalConstructionPartDeclaration> parts,
            List<PhysicalExternalInputDeclaration> externalInputs) {
        if (parts == null || externalInputs == null)
            throw new IllegalArgumentException("Physical provider output is required");
        this.parts = Collections.unmodifiableList(
            new ArrayList<PhysicalConstructionPartDeclaration>(parts));
        this.externalInputs = Collections.unmodifiableList(
            new ArrayList<PhysicalExternalInputDeclaration>(externalInputs));
    }

    List<PhysicalConstructionPartDeclaration> getParts() { return parts; }
    List<PhysicalExternalInputDeclaration> getExternalInputs() { return externalInputs; }
}

/**
 * Local physical provider registry and composition order.  The registry has
 * no board/runtime state; adding an ordinary local provider is a registration
 * here and does not add a construction branch to the assembler.
 */
final class StandardPhysicalConstructionProviders {
    private static final PhysicalConstructionProvider RESISTIVE_SOURCE =
        new ResistivePhysicalProvider(ResistiveBlockContributions.SOURCE_TYPE_ID);
    private static final PhysicalConstructionProvider RESISTIVE_LOAD =
        new ResistivePhysicalProvider(ResistiveBlockContributions.LOAD_TYPE_ID);
    private static final PhysicalConstructionProvider CONTROLLED_DRIVER =
        new ControlledDriverPhysicalProvider();
    private static final PhysicalConstructionProvider CONTROLLED_LOAD =
        new ControlledLoadPhysicalProvider(ControlledIndicatorBlockContributions.LOAD_VERSION);
    private static final PhysicalConstructionProvider CONTROLLED_DEVICE =
        new DevicePhysicalProvider(true);
    private static final PhysicalConstructionProvider RESISTIVE_DEVICE =
        new DevicePhysicalProvider(false);

    private StandardPhysicalConstructionProviders() { }

    static PhysicalConstructionProvider provider(String providerId, int version) {
        if (ResistiveBlockContributions.SOURCE_TYPE_ID.equals(providerId) &&
                version == ResistiveBlockContributions.VERSION)
            return RESISTIVE_SOURCE;
        if (ResistiveBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ResistiveBlockContributions.VERSION)
            return RESISTIVE_LOAD;
        if (ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.VERSION)
            return CONTROLLED_DRIVER;
        if (ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.LOAD_VERSION)
            return CONTROLLED_LOAD;
        if ("resistive-device-join".equals(providerId) && version == 1)
            return RESISTIVE_DEVICE;
        if ("controlled-device-join".equals(providerId) && version == 1)
            return CONTROLLED_DEVICE;
        throw new IllegalArgumentException("Unknown physical construction provider " +
            providerId + "@" + version);
    }

    static PhysicalConstructionDeclarations describe(BoundedAssemblyPlan plan) {
        if (plan == null)
            throw new IllegalArgumentException("Assembly plan is required");
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        ArrayList<PhysicalConstructionPartDeclaration> local =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        ArrayList<PhysicalConstructionPartDeclaration> device =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        ArrayList<PhysicalExternalInputDeclaration> inputs =
            new ArrayList<PhysicalExternalInputDeclaration>();

        String boardFamilyId = null;
        String boardName = null;
        for (ElectricalRealizationSpec.ProviderDeclaration declaration :
                spec.getProviderDeclarations().values()) {
            PhysicalConstructionProvider provider = provider(declaration.getProviderId(),
                declaration.getProviderVersion());
            if (boardFamilyId == null) {
                boardFamilyId = provider.getBoardFamilyId();
                boardName = provider.getBoardName();
            } else if (!boardFamilyId.equals(provider.getBoardFamilyId()) ||
                    !boardName.equals(provider.getBoardName())) {
                throw new IllegalStateException("Physical providers disagree on board envelope");
            }
            PhysicalConstructionContribution contribution = provider.declare(plan, spec,
                declaration);
            if (contribution == null)
                throw new IllegalStateException("Physical provider returned no contribution: " +
                    declaration.getOwnerKey());
            if (declaration.isDeviceOwner()) {
                device.addAll(contribution.getParts());
            } else {
                local.addAll(contribution.getParts());
            }
            inputs.addAll(contribution.getExternalInputs());
        }
        if (boardFamilyId == null)
            throw new IllegalStateException("No physical provider declarations");
        return new PhysicalConstructionDeclarations(local, device, inputs,
            boardFamilyId, boardName);
    }
}

/** Common pure helpers used by local providers. */
final class PhysicalConstructionProviderSupport {
    private PhysicalConstructionProviderSupport() { }

    static void requireDeclaration(ElectricalRealizationSpec.ProviderDeclaration declaration,
            String providerId, int version, String expectedOwner) {
        if (declaration == null || !providerId.equals(declaration.getProviderId()) ||
                declaration.getProviderVersion() != version ||
                (expectedOwner != null && !expectedOwner.equals(declaration.getOwnerKey())))
            throw new IllegalArgumentException("Mismatched physical provider declaration");
    }

    static PhysicalPackage physicalPackage(ElectricalRealizationSpec spec,
            String componentId) {
        PhysicalPackage result = spec.getPackageMap().getPackages().get(componentId);
        if (result == null)
            throw new IllegalArgumentException("Missing resolved physical package: " + componentId);
        return result;
    }

    static String componentId(BoundedAssemblyPlan plan, String ownerKey, String localId) {
        return plan.idFor(ownerKey, FunctionalBlockDescriptor.EntityKind.COMPONENT, localId);
    }

    static PhysicalConstructionTerminalDeclaration terminal(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, String ownerKey, String localId,
            String padLocalId, String terminalId, String endpointId) {
        ElectricalRealizationSpec.TerminalMapping mapping =
            spec.getTerminalMapping(ownerKey, localId, terminalId);
        if (mapping == null)
            throw new IllegalArgumentException("Missing resolved terminal mapping " + ownerKey +
                "/" + localId + "." + terminalId);
        String padId = spec.getPadId(ownerKey, padLocalId);
        String manifestKey = ownerKey + "/" + endpointId;
        return new PhysicalConstructionTerminalDeclaration(padId, terminalId,
            mapping.getPackageTerminalId(), mapping.getNetId(), endpointId,
            manifestKey, ownerKey, ownerKey, localId, mapping.getComponentId());
    }

    static PhysicalConstructionTerminalDeclaration deviceTerminal(String padId,
            String terminalId, String netId, String endpointId, String manifestKey,
            String manifestBlockKey) {
        return new PhysicalConstructionTerminalDeclaration(padId, terminalId,
            terminalId, netId, endpointId, manifestKey, manifestBlockKey,
            null, null, null);
    }

    static PhysicalConstructionTerminalDeclaration deviceTerminal(String padId,
            String terminalId, String packageTerminalId, String netId, String endpointId,
            String manifestKey, String manifestBlockKey, String ownerKey, String localId,
            String componentId) {
        return new PhysicalConstructionTerminalDeclaration(padId, terminalId,
            packageTerminalId, netId, endpointId, manifestKey, manifestBlockKey,
            ownerKey, localId, componentId);
    }

    static String requireElementOwner(ElectricalRealizationSpec spec,
            String expectedOwner, String elementId) {
        if (spec.getElementDeclaration(expectedOwner, elementId) == null)
            throw new IllegalArgumentException("Missing declared physical backing element " +
                expectedOwner + "/" + elementId);
        return expectedOwner;
    }

    static String findAttachmentOwner(ElectricalRealizationSpec spec,
            String ownerKey, String localId, boolean first) {
        String local = localId + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        if (spec.getElementDeclaration(ownerKey, local) != null)
            return ownerKey;
        ElectricalRealizationSpec.EndpointRef endpoint = new ElectricalRealizationSpec.EndpointRef(
            ownerKey, localId + (first ? "" : "_SECONDARY"), first ? "1" : "2");
        for (ElectricalRealizationSpec.BridgeSpec bridge : spec.getBridgeSpecs().values()) {
            if (endpoint.equals(bridge.getFirst()) || endpoint.equals(bridge.getSecond()))
                return requireElementOwner(spec, "device", bridge.getBridgeElementId());
        }
        return null;
    }

    static String attachmentId(ElectricalRealizationSpec spec, String ownerKey,
            String localId, boolean first) {
        String local = localId + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        if (spec.getElementDeclaration(ownerKey, local) != null)
            return local;
        ElectricalRealizationSpec.EndpointRef endpoint = new ElectricalRealizationSpec.EndpointRef(
            ownerKey, localId + (first ? "" : "_SECONDARY"), first ? "1" : "2");
        for (ElectricalRealizationSpec.BridgeSpec bridge : spec.getBridgeSpecs().values()) {
            if (endpoint.equals(bridge.getFirst()) || endpoint.equals(bridge.getSecond()))
                return bridge.getBridgeElementId();
        }
        throw new IllegalArgumentException("Missing declared attachment for " + ownerKey +
            "/" + local);
    }

    static PhysicalConstructionPartDeclaration.Builder base(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            String ownerKey, String componentId, PhysicalPackage physicalPackage,
            String publicType, String designator, PhysicalSpecification specification,
            PhysicalNameplate nameplate, String backingOwner, String backingElement,
            PhysicalConstructionPartDeclaration.PartPolicy policy) {
        return PhysicalConstructionPartDeclaration.builder(declaration.getOwnerKey(), ownerKey,
            declaration.getProviderId(), declaration.getProviderVersion(), componentId,
            physicalPackage, publicType, designator, specification, nameplate,
            backingOwner, backingElement, policy);
    }

    static void addResistorFault(BoundedAssemblyPlan plan, ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution contribution,
            ComposedBlockContribution.ResistorRecipe recipe,
            PhysicalConstructionPartDeclaration.Builder builder, String faultFamilyId) {
        ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
        String targetLocalId = fault.getTargetComponentLocalId();
        if (!recipe.getComponentLocalId().equals(targetLocalId))
            return;
        ComposedBlockContribution.FaultSpec.Kind kind = fault.getKind();
        String faultOwner = null;
        String faultElement = null;
        if (kind == ComposedBlockContribution.FaultSpec.Kind.OPEN) {
            faultElement = recipe.getComponentLocalId() + "_FAULT_SWITCH";
            faultOwner = requireElementOwner(spec, declaration.getOwnerKey(), faultElement);
        }
        String componentId = componentId(plan, declaration.getOwnerKey(),
            recipe.getComponentLocalId());
        builder.fault(kind, contribution.getFaultLocalId(),
            contribution.getFaultEffectiveOhms(),
            faultFamilyId,
            faultOwner, faultElement, declaration.getOwnerKey(),
            contribution.getRepairLocalComponentId(),
            componentId(plan, declaration.getOwnerKey(),
                contribution.getRepairLocalComponentId()));
    }

    static PhysicalNameplate resistorNameplate(BoundedAssemblyPlan plan,
            String componentId, String localId, ComposedBlockContribution.ResistorRecipe recipe) {
        if (recipe.getResolvedRecipe() != null)
            return recipe.getResolvedRecipe().getPlayerVisibleNameplate()
                .forPhysicalPartId(localId);
        if (!plan.isControlledIndicator())
            return new PhysicalNameplate(componentId, "Physical resistor markings",
                "Markings", "Color bands");
        if ("RG".equals(localId))
            return new PhysicalNameplate("RG", "Gate drive resistor markings",
                "Markings", "Color bands");
        if ("RPD".equals(localId))
            return new PhysicalNameplate("RPD", "Gate pull-down resistor markings",
                "Markings", "Color bands");
        if ("RLOAD".equals(localId))
            return new PhysicalNameplate("RLOAD", "Load resistor markings",
                "Markings", "Color bands");
        return new PhysicalNameplate(localId, "Physical resistor markings",
            "Markings", "Color bands");
    }
}

/** Physical provider for the two v1 resistive local contributions. */
final class ResistivePhysicalProvider implements PhysicalConstructionProvider {
    private final String providerId;

    ResistivePhysicalProvider(String providerId) { this.providerId = providerId; }
    public String getProviderId() { return providerId; }
    public int getVersion() { return ResistiveBlockContributions.VERSION; }
    public String getBoardFamilyId() { return BoundedGeneratedBoardAssembler.FAMILY_ID; }
    public String getBoardName() { return BoundedGeneratedBoardAssembler.FAMILY_ID; }
    public String getFaultFamilyId() { return BoundedGeneratedBoardAssembler.FAMILY_ID; }

    public PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        PhysicalConstructionProviderSupport.requireDeclaration(declaration, providerId,
            getVersion(), declaration == null ? null : declaration.getOwnerKey());
        ComposedBlockContribution contribution = declaration.getContribution();
        if (contribution == null || contribution.getResistors().isEmpty())
            throw new IllegalArgumentException("Resistive physical contribution is empty");
        ArrayList<PhysicalConstructionPartDeclaration> parts =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        for (ComposedBlockContribution.ResistorRecipe recipe : contribution.getResistors().values())
            parts.add(resistor(plan, spec, declaration, contribution, recipe));
        return new PhysicalConstructionContribution(parts,
            Collections.<PhysicalExternalInputDeclaration>emptyList());
    }

    private PhysicalConstructionPartDeclaration resistor(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution contribution,
            ComposedBlockContribution.ResistorRecipe recipe) {
        String owner = declaration.getOwnerKey();
        String componentId = PhysicalConstructionProviderSupport.componentId(plan, owner,
            recipe.getComponentLocalId());
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        ResistorNameplate specification = new ResistorNameplate(componentId,
            recipe.getResistanceOhms(), recipe.getTolerancePercent(), recipe.getRatedWatts());
        /* Component identity stays fully qualified; the board marking is a
         * concise physical reference designator.  The two resistive blocks
         * both expose a logical R1, so the assembled board assigns distinct
         * source/load markings. */
        String designator = ResistiveBlockContributions.SOURCE_BLOCK_KEY.equals(owner) ?
            "R1" : "R2";
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "RESISTOR", designator, specification,
                PhysicalConstructionProviderSupport.resistorNameplate(plan, componentId,
                    recipe.getComponentLocalId(), recipe), owner,
                recipe.getComponentLocalId(), recipe.isMutable() ?
                    PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR :
                    PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        String secondary = recipe.getComponentLocalId() + "_SECONDARY";
        String secondaryOwner = recipe.isMutable() ?
            PhysicalConstructionProviderSupport.requireElementOwner(spec, owner, secondary) : null;
        if (secondaryOwner != null) builder.secondary(secondaryOwner, secondary);
        String firstId = PhysicalConstructionProviderSupport.attachmentId(spec, owner,
            recipe.getComponentLocalId(), true);
        String secondId = PhysicalConstructionProviderSupport.attachmentId(spec, owner,
            recipe.getComponentLocalId(), false);
        String firstOwner = PhysicalConstructionProviderSupport.findAttachmentOwner(spec,
            owner, recipe.getComponentLocalId(), true);
        String secondOwner = PhysicalConstructionProviderSupport.findAttachmentOwner(spec,
            owner, recipe.getComponentLocalId(), false);
        if (firstOwner == null || secondOwner == null)
            throw new IllegalArgumentException("Mutable resistor attachments are missing: " +
                componentId);
        builder.attachments(firstOwner, firstId, secondOwner, secondId);
        PhysicalConstructionProviderSupport.addResistorFault(plan, spec, declaration,
            contribution, recipe, builder, getFaultFamilyId());
        builder.countInMappedIdentity(true)
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getFirstPadLocalId(), "1",
                recipe.getFirstEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getSecondPadLocalId(), "2",
                recipe.getSecondEndpointLocalId()));
        return builder.build();
    }
}

/** Physical provider for the controlled low-side driver contribution. */
final class ControlledDriverPhysicalProvider implements PhysicalConstructionProvider {
    public String getProviderId() { return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID; }
    public int getVersion() { return ControlledIndicatorBlockContributions.VERSION; }
    public String getBoardFamilyId() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }
    public String getBoardName() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }
    public String getFaultFamilyId() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }

    public PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        PhysicalConstructionProviderSupport.requireDeclaration(declaration, getProviderId(),
            getVersion(), ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY);
        ComposedBlockContribution contribution = declaration.getContribution();
        ArrayList<PhysicalConstructionPartDeclaration> parts =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        for (ComposedBlockContribution.ResistorRecipe recipe : contribution.getResistors().values())
            parts.add(resistor(plan, spec, declaration, contribution, recipe));
        for (ComposedBlockContribution.NmosRecipe recipe : contribution.getNmosRecipes().values())
            parts.add(nmos(plan, spec, declaration, recipe));
        return new PhysicalConstructionContribution(parts,
            Collections.<PhysicalExternalInputDeclaration>emptyList());
    }

    private PhysicalConstructionPartDeclaration resistor(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution contribution,
            ComposedBlockContribution.ResistorRecipe recipe) {
        String owner = declaration.getOwnerKey();
        String componentId = PhysicalConstructionProviderSupport.componentId(plan, owner,
            recipe.getComponentLocalId());
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        ResistorNameplate specification = new ResistorNameplate(componentId,
            recipe.getResistanceOhms(), recipe.getTolerancePercent(), recipe.getRatedWatts());
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "RESISTOR", recipe.getComponentLocalId(), specification,
                PhysicalConstructionProviderSupport.resistorNameplate(plan, componentId,
                    recipe.getComponentLocalId(), recipe), owner,
                recipe.getComponentLocalId(), recipe.isMutable() ?
                    PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR :
                    PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        String secondary = recipe.getComponentLocalId() + "_SECONDARY";
        String secondaryOwner = recipe.isMutable() ?
            PhysicalConstructionProviderSupport.requireElementOwner(spec, owner, secondary) : null;
        if (recipe.isMutable()) {
            if (secondaryOwner == null)
                throw new IllegalArgumentException("Mutable driver resistor secondary is missing: " +
                    componentId);
            builder.secondary(secondaryOwner, secondary);
        }
        if (recipe.isMutable()) {
            String first = recipe.getComponentLocalId() + "_FIRST_ATTACHMENT";
            String second = recipe.getComponentLocalId() + "_SECOND_ATTACHMENT";
            builder.attachments(owner, first, owner, second);
        }
        PhysicalConstructionProviderSupport.addResistorFault(plan, spec, declaration,
            contribution, recipe, builder, getFaultFamilyId());
        builder.countInMappedIdentity(true)
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getFirstPadLocalId(), "1",
                recipe.getFirstEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getSecondPadLocalId(), "2",
                recipe.getSecondEndpointLocalId()));
        return builder.build();
    }

    private PhysicalConstructionPartDeclaration nmos(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution.NmosRecipe recipe) {
        String owner = declaration.getOwnerKey();
        String componentId = PhysicalConstructionProviderSupport.componentId(plan, owner,
            recipe.getComponentLocalId());
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        NmosSpecification specification = new NmosSpecification(componentId,
            ElectricalRealizationSpec.CONTROLLED_NMOS_THRESHOLD_VOLTS,
            ElectricalRealizationSpec.CONTROLLED_NMOS_BETA);
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "NMOS_TRANSISTOR", recipe.getComponentLocalId(), specification,
                new PhysicalNameplate(recipe.getComponentLocalId(), "N-channel MOSFET",
                    "Part", "N-channel MOSFET"), owner, recipe.getComponentLocalId(),
                PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        builder.countInMappedIdentity(true)
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getGatePadLocalId(), "G",
                recipe.getGateEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getDrainPadLocalId(), "D",
                recipe.getDrainEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getSourcePadLocalId(), "S",
                recipe.getSourceEndpointLocalId()));
        return builder.build();
    }
}

/** Physical provider for the controlled resistor/LED load contribution. */
final class ControlledLoadPhysicalProvider implements PhysicalConstructionProvider {
    private final int version;
    ControlledLoadPhysicalProvider(int version) { this.version = version; }
    public String getProviderId() { return ControlledIndicatorBlockContributions.LOAD_TYPE_ID; }
    public int getVersion() { return version; }
    public String getBoardFamilyId() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }
    public String getBoardName() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }
    public String getFaultFamilyId() { return ControlledIndicatorDeviceBehavior.FAMILY_ID; }

    public PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        PhysicalConstructionProviderSupport.requireDeclaration(declaration, getProviderId(),
            version, ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY);
        ComposedBlockContribution contribution = declaration.getContribution();
        ArrayList<PhysicalConstructionPartDeclaration> parts =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        for (ComposedBlockContribution.ResistorRecipe recipe : contribution.getResistors().values())
            parts.add(resistor(plan, spec, declaration, contribution, recipe));
        for (ComposedBlockContribution.LedRecipe recipe : contribution.getLedRecipes().values())
            parts.add(led(plan, spec, declaration, recipe));
        return new PhysicalConstructionContribution(parts,
            Collections.<PhysicalExternalInputDeclaration>emptyList());
    }

    private PhysicalConstructionPartDeclaration resistor(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution contribution,
            ComposedBlockContribution.ResistorRecipe recipe) {
        String owner = declaration.getOwnerKey();
        String componentId = PhysicalConstructionProviderSupport.componentId(plan, owner,
            recipe.getComponentLocalId());
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        ResistorNameplate specification = new ResistorNameplate(componentId,
            recipe.getResistanceOhms(), recipe.getTolerancePercent(), recipe.getRatedWatts());
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "RESISTOR", recipe.getComponentLocalId(), specification,
                PhysicalConstructionProviderSupport.resistorNameplate(plan, componentId,
                    recipe.getComponentLocalId(), recipe), owner,
                recipe.getComponentLocalId(), recipe.isMutable() ?
                    PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR :
                    PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        String secondary = recipe.getComponentLocalId() + "_SECONDARY";
        builder.secondary(PhysicalConstructionProviderSupport.requireElementOwner(spec, owner,
            secondary), secondary);
        builder.attachments(owner, recipe.getComponentLocalId() + "_FIRST_ATTACHMENT",
            owner, recipe.getComponentLocalId() + "_SECOND_ATTACHMENT");
        PhysicalConstructionProviderSupport.addResistorFault(plan, spec, declaration,
            contribution, recipe, builder, getFaultFamilyId());
        builder.countInMappedIdentity(true)
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getFirstPadLocalId(), "1",
                recipe.getFirstEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getSecondPadLocalId(), "2",
                recipe.getSecondEndpointLocalId()));
        return builder.build();
    }

    private PhysicalConstructionPartDeclaration led(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution.LedRecipe recipe) {
        String owner = declaration.getOwnerKey();
        String componentId = PhysicalConstructionProviderSupport.componentId(plan, owner,
            recipe.getComponentLocalId());
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        /* The contribution's LED token is logical; the pinned CircuitJS model is explicit. */
        LedNameplate specification = new LedNameplate(componentId, "Generic red LED",
            ElectricalRealizationSpec.CONTROLLED_LED_MODEL, 1, 0, 0);
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "LED", recipe.getComponentLocalId(), specification,
                new PhysicalNameplate(recipe.getComponentLocalId(), "Generic red LED"), owner,
                recipe.getComponentLocalId(), PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        builder.countInMappedIdentity(true)
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getAnodePadLocalId(), "A",
                recipe.getAnodeEndpointLocalId()))
            .terminal(PhysicalConstructionProviderSupport.terminal(plan, spec, owner,
                recipe.getComponentLocalId(), recipe.getCathodePadLocalId(), "K",
                recipe.getCathodeEndpointLocalId()));
        return builder.build();
    }
}

/** Device-owned connector/external-input provider for the two supported envelopes. */
final class DevicePhysicalProvider implements PhysicalConstructionProvider {
    private final boolean controlled;
    DevicePhysicalProvider(boolean controlled) { this.controlled = controlled; }
    public String getProviderId() {
        return controlled ? "controlled-device-join" : "resistive-device-join";
    }
    public int getVersion() { return 1; }
    public String getBoardFamilyId() {
        return controlled ? ControlledIndicatorDeviceBehavior.FAMILY_ID :
            BoundedGeneratedBoardAssembler.FAMILY_ID;
    }
    public String getBoardName() { return getBoardFamilyId(); }
    public String getFaultFamilyId() { return getBoardFamilyId(); }

    public PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        PhysicalConstructionProviderSupport.requireDeclaration(declaration, getProviderId(),
            getVersion(), "device");
        if (controlled != "controlled-device-join".equals(declaration.getProviderId()))
            throw new IllegalArgumentException("Device physical provider envelope mismatch");
        return controlled ? controlled(plan, spec, declaration) : resistive(plan, spec, declaration);
    }

    private PhysicalConstructionContribution resistive(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        String componentId = "J1";
        PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
            spec, componentId);
        String owner = spec.getPackageMap().getPackageOwners().get(componentId);
        if (owner == null) throw new IllegalStateException("v1 connector package owner is missing");
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "CONNECTOR", "J1",
                new BasicPhysicalSpecification("J1_CONNECTOR"),
                new PhysicalNameplate("J1", "Power input connector"), "device", "CONNECTOR",
                PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        builder.countInMappedIdentity(false)
            .terminal(PhysicalConstructionProviderSupport.deviceTerminal("J1.1", "1", "1",
                plan.netFor("source", "SUPPLY"), "J1_1", "J1.1", "J1",
                "device", "J1", componentId))
            .terminal(PhysicalConstructionProviderSupport.deviceTerminal("J1.2", "2", "2",
                plan.netFor("source", "RETURN"), "J1_2", "J1.2", "J1",
                "device", "J1", componentId));
        PhysicalExternalInputDeclaration input = new PhysicalExternalInputDeclaration("device",
            ElectricalRealizationSpec.LEGACY_POWER_INPUT_ID, "J1.1", "J1.2",
            plan.netFor("source", "SUPPLY"), plan.netFor("source", "RETURN"), 5.0);
        return new PhysicalConstructionContribution(Arrays.asList(builder.build()),
            Arrays.asList(input));
    }

    private PhysicalConstructionContribution controlled(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        ArrayList<PhysicalConstructionPartDeclaration> parts =
            new ArrayList<PhysicalConstructionPartDeclaration>();
        ArrayList<PhysicalExternalInputDeclaration> inputs =
            new ArrayList<PhysicalExternalInputDeclaration>();
        String[] keys = new String[] {
            DeviceAdapterContract.POWER_ADAPTER_KEY,
            DeviceAdapterContract.CONTROL_ADAPTER_KEY
        };
        for (String key : keys) {
            DeviceAdapterContract adapter = adapter(plan, key);
            String componentId = plan.idFor(key,
                FunctionalBlockDescriptor.EntityKind.COMPONENT,
                adapter.getComponentLocalId());
            PhysicalPackage physicalPackage = PhysicalConstructionProviderSupport.physicalPackage(
                spec, componentId);
            String inputId = adapter.getExternalInputId();
            String pad1 = plan.idFor(key, FunctionalBlockDescriptor.EntityKind.PAD,
                adapter.getComponentLocalId() + ".1");
            String pad2 = plan.idFor(key, FunctionalBlockDescriptor.EntityKind.PAD,
                adapter.getComponentLocalId() + ".2");
            String outputNet = plan.netFor(key, "OUTPUT");
            String returnNet = plan.netFor(key, "RETURN");
            String backing = DeviceAdapterContract.POWER_ADAPTER_KEY.equals(key) ?
                "LOAD_CONNECTOR" : "CONTROL_COMMAND";
            PhysicalConstructionPartDeclaration.Builder builder =
                PhysicalConstructionProviderSupport.base(plan, spec, declaration, key,
                    componentId, physicalPackage, "CONNECTOR", adapter.getComponentLocalId(),
                    new BasicPhysicalSpecification(adapter.getComponentLocalId() + "_CONNECTOR"),
                    new PhysicalNameplate(adapter.getComponentLocalId(),
                        DeviceAdapterContract.POWER_ADAPTER_KEY.equals(key) ?
                            "Load supply connector" : "Control input connector"),
                    "device", backing, PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
            builder.countInMappedIdentity(true)
                .terminal(PhysicalConstructionProviderSupport.deviceTerminal(pad1, "1", "1",
                    outputNet, adapter.getComponentLocalId() + "_1",
                    key + "/" + adapter.getComponentLocalId() + "_1", key,
                    key, adapter.getComponentLocalId(), componentId))
                .terminal(PhysicalConstructionProviderSupport.deviceTerminal(pad2, "2", "2",
                    returnNet, adapter.getComponentLocalId() + "_2",
                    key + "/" + adapter.getComponentLocalId() + "_2", key,
                    key, adapter.getComponentLocalId(), componentId));
            parts.add(builder.build());
            inputs.add(new PhysicalExternalInputDeclaration("device", inputId, pad1, pad2,
                outputNet, returnNet, 5.0));
        }
        return new PhysicalConstructionContribution(parts, inputs);
    }

    private static DeviceAdapterContract adapter(BoundedAssemblyPlan plan, String key) {
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
            if (key.equals(adapter.getKey())) return adapter;
        throw new IllegalArgumentException("Missing device adapter " + key);
    }
}
