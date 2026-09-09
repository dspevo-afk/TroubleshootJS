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
    private static final PhysicalConstructionProvider CONTROLLED_LOAD_V1 =
        new ControlledLoadPhysicalProvider(ControlledIndicatorBlockContributions.VERSION);
    private static final PhysicalConstructionProvider CONTROLLED_LOAD_V2 =
        new ControlledLoadPhysicalProvider(ControlledIndicatorBlockContributions.VALUE_LOAD_VERSION);
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
                version == ControlledIndicatorBlockContributions.VERSION)
            return CONTROLLED_LOAD_V1;
        if (ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(providerId) &&
                version == ControlledIndicatorBlockContributions.VALUE_LOAD_VERSION)
            return CONTROLLED_LOAD_V2;
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

        /*
         * This is composition order, not identity resolution.  It preserves
         * the accepted v1 slot/part order and controlled provider order while
         * keeping the materializer independent of concrete component names.
         */
        if (plan.isControlledIndicator()) {
            append(plan, spec, "driver", local, inputs);
            append(plan, spec, "load", local, inputs);
            append(plan, spec, "device", device, inputs);
        } else {
            append(plan, spec, "device", device, inputs);
            append(plan, spec, "source", local, inputs);
            append(plan, spec, "load", local, inputs);
        }
        return new PhysicalConstructionDeclarations(local, device, inputs);
    }

    private static void append(BoundedAssemblyPlan plan, ElectricalRealizationSpec spec,
            String ownerKey, List<PhysicalConstructionPartDeclaration> parts,
            List<PhysicalExternalInputDeclaration> inputs) {
        ElectricalRealizationSpec.ProviderDeclaration declaration =
            spec.getProviderDeclaration(ownerKey);
        if (declaration == null)
            throw new IllegalStateException("Missing physical provider declaration: " + ownerKey);
        PhysicalConstructionProvider provider = provider(declaration.getProviderId(),
            declaration.getProviderVersion());
        PhysicalConstructionContribution contribution = provider.declare(plan, spec,
            declaration);
        if (contribution == null)
            throw new IllegalStateException("Physical provider returned no contribution: " +
                ownerKey);
        parts.addAll(contribution.getParts());
        inputs.addAll(contribution.getExternalInputs());
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
            manifestKey, ownerKey);
    }

    static PhysicalConstructionTerminalDeclaration deviceTerminal(String padId,
            String terminalId, String netId, String endpointId, String manifestKey,
            String manifestBlockKey) {
        return new PhysicalConstructionTerminalDeclaration(padId, terminalId,
            terminalId, netId, endpointId, manifestKey, manifestBlockKey);
    }

    static String findElementOwner(ElectricalRealizationSpec spec,
            String preferredOwner, String elementId) {
        if (spec.getElementDeclaration(preferredOwner, elementId) != null)
            return preferredOwner;
        if (spec.getElementDeclaration("device", elementId) != null)
            return "device";
        String result = null;
        for (ElectricalRealizationSpec.ElementDeclaration declaration :
                spec.getElementDeclarations().values()) {
            if (!elementId.equals(declaration.getElementId()))
                continue;
            if (result != null && !result.equals(declaration.getOwnerKey()))
                throw new IllegalArgumentException("Ambiguous physical backing element " +
                    elementId);
            result = declaration.getOwnerKey();
        }
        return result;
    }

    static String requireElementOwner(ElectricalRealizationSpec spec,
            String preferredOwner, String elementId) {
        String result = findElementOwner(spec, preferredOwner, elementId);
        if (result == null)
            throw new IllegalArgumentException("Missing physical backing element " + elementId);
        return result;
    }

    static String findAttachmentOwner(ElectricalRealizationSpec spec,
            String ownerKey, String localId, boolean first) {
        String local = localId + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        String result = findElementOwner(spec, ownerKey, local);
        if (result != null)
            return result;
        String legacyPrefix = "source".equals(ownerKey) ? "SOURCE" :
            "load".equals(ownerKey) ? "LOAD" : null;
        if (legacyPrefix == null)
            return null;
        return requireElementOwner(spec, ownerKey, legacyPrefix +
            (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT"));
    }

    static String attachmentId(String ownerKey, String localId, boolean first) {
        String local = localId + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        if ("source".equals(ownerKey)) return "SOURCE" + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        if ("load".equals(ownerKey)) return "LOAD" + (first ? "_FIRST_ATTACHMENT" : "_SECOND_ATTACHMENT");
        return local;
    }

    static PhysicalConstructionPartDeclaration.Builder base(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            String ownerKey, String componentId, PhysicalPackage physicalPackage,
            String publicType, String designator, PhysicalSpecification specification,
            PhysicalNameplate nameplate, String backingOwner, String backingElement,
            PhysicalConstructionPartDeclaration.PartPolicy policy) {
        return PhysicalConstructionPartDeclaration.builder(declaration.getOwnerKey(), ownerKey,
            declaration.getProviderId(), declaration.getProviderVersion(),
            declaration.getContribution() == null ? declaration.getProviderId() :
                declaration.getContribution().getProviderTypeId(), componentId,
            physicalPackage, publicType, designator, specification, nameplate,
            backingOwner, backingElement, policy);
    }

    static void addResistorFault(BoundedAssemblyPlan plan, ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration,
            ComposedBlockContribution contribution,
            ComposedBlockContribution.ResistorRecipe recipe,
            PhysicalConstructionPartDeclaration.Builder builder) {
        ComposedBlockContribution.FaultSpec fault = contribution.getFaultSpec();
        /*
         * The v1 resistive contribution predates FaultSpec's component-target
         * vocabulary.  Its "high-resistance" value is a historical fault
         * label, while the accepted assembler applies the value mutation to
         * the contribution's repair component (R1).  Keep that label for the
         * durable fault suffix; only use the repair ID to decide which recipe
         * owns the physical fault.  Controlled v2/v3 use the typed target
         * component directly and retain their RG/RLOAD suffixes.
         */
        boolean legacyResistive = !plan.isControlledIndicator() &&
            plan.getRequest().getDescriptor().getGenerator().getVersion() ==
                BoundedAssemblyRequest.GENERATOR_VERSION;
        String targetLocalId = legacyResistive ? contribution.getRepairLocalComponentId() :
            fault.getTargetComponentLocalId();
        if (!recipe.getComponentLocalId().equals(targetLocalId))
            return;
        ComposedBlockContribution.FaultSpec.Kind kind = legacyResistive ?
            ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE : fault.getKind();
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
            plan.isControlledIndicator() ? ControlledIndicatorDeviceBehavior.FAMILY_ID :
                BoundedGeneratedBoardAssembler.FAMILY_ID,
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
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionProviderSupport.base(plan, spec, declaration, owner, componentId,
                physicalPackage, "RESISTOR", componentId, specification,
                PhysicalConstructionProviderSupport.resistorNameplate(plan, componentId,
                    recipe.getComponentLocalId(), recipe), owner,
                recipe.getComponentLocalId(), recipe.isMutable() ?
                    PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR :
                    PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        String secondary = recipe.getComponentLocalId() + "_SECONDARY";
        String secondaryOwner = PhysicalConstructionProviderSupport.findElementOwner(spec,
            owner, secondary);
        if (secondaryOwner != null) builder.secondary(secondaryOwner, secondary);
        String firstId = PhysicalConstructionProviderSupport.attachmentId(owner,
            recipe.getComponentLocalId(), true);
        String secondId = PhysicalConstructionProviderSupport.attachmentId(owner,
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
            contribution, recipe, builder);
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
        String secondaryOwner = PhysicalConstructionProviderSupport.findElementOwner(spec,
            owner, secondary);
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
            contribution, recipe, builder);
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
            BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_THRESHOLD_VOLTS,
            BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_BETA);
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
            contribution, recipe, builder);
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
            BoundedGeneratedBoardAssembler.CONTROLLED_LED_MODEL, 1, 0, 0);
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

    public PhysicalConstructionContribution declare(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec,
            ElectricalRealizationSpec.ProviderDeclaration declaration) {
        PhysicalConstructionProviderSupport.requireDeclaration(declaration, getProviderId(),
            getVersion(), "device");
        if (controlled != plan.isControlledIndicator())
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
            .terminal(PhysicalConstructionProviderSupport.deviceTerminal("J1.1", "1",
                plan.netFor("source", "SUPPLY"), "J1_1", "J1.1", "J1"))
            .terminal(PhysicalConstructionProviderSupport.deviceTerminal("J1.2", "2",
                plan.netFor("source", "RETURN"), "J1_2", "J1.2", "J1"));
        PhysicalExternalInputDeclaration input = new PhysicalExternalInputDeclaration("device",
            BoundedGeneratedBoardAssembler.POWER_INPUT_ID, "J1.1", "J1.2",
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
                .terminal(PhysicalConstructionProviderSupport.deviceTerminal(pad1, "1",
                    outputNet, adapter.getComponentLocalId() + "_1",
                    key + "/" + adapter.getComponentLocalId() + "_1", key))
                .terminal(PhysicalConstructionProviderSupport.deviceTerminal(pad2, "2",
                    returnNet, adapter.getComponentLocalId() + "_2",
                    key + "/" + adapter.getComponentLocalId() + "_2", key));
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
