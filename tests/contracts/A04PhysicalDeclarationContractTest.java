package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Native contract for the provider-owned A04 physical declaration boundary.
 *
 * <p>The test consumes the real resolved plans and the real pure
 * {@link PhysicalConstructionMaterializer#describe(BoundedAssemblyPlan)} path.
 * It does not manufacture a board, CircuitJS graph, or a candidate.  The
 * expected declaration is intentionally kept here as a small, explicit oracle
 * so a provider cannot make a malformed declaration self-consistent.</p>
 */
public final class A04PhysicalDeclarationContractTest {
    private static int assertions;

    private A04PhysicalDeclarationContractTest() { }

    public static void main(String[] args) {
        try {
            resistiveBothFaultDecisions();
            controlledBothFaultDecisions(2);
            controlledBothFaultDecisions(3);
            declarationValidationCanaries();
            System.out.println("PASS: A04PhysicalDeclarationContractTest assertions="
                    + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: A04PhysicalDeclarationContractTest after "
                    + assertions + " assertions: " + failure.getMessage());
            System.exit(1);
        }
    }

    /** v1 has one selected decision per seed, but both physical candidates remain declared. */
    private static void resistiveBothFaultDecisions() {
        BoundedAssemblyPlan sourcePlan = null;
        BoundedAssemblyPlan loadPlan = null;
        for (long seed = 0; seed < 128 && (sourcePlan == null || loadPlan == null); seed++) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forCanary(seed));
            if (ResistiveBlockContributions.SOURCE_FAULT_DECISION_KEY.equals(
                    plan.getFaultDecisionKey()) && sourcePlan == null)
                sourcePlan = plan;
            if (ResistiveBlockContributions.LOAD_FAULT_DECISION_KEY.equals(
                    plan.getFaultDecisionKey()) && loadPlan == null)
                loadPlan = plan;
        }
        check(sourcePlan != null && loadPlan != null,
                "v1 deterministic corpus did not exercise both fault decisions");
        assertPhysical(sourcePlan, 1, sourcePlan.getFaultDecisionKey());
        assertPhysical(loadPlan, 1, loadPlan.getFaultDecisionKey());
    }

    /** Controlled v2/v3 expose both admitted targets through the diagnostic resolver. */
    private static void controlledBothFaultDecisions(int version) {
        long seed = version == 2 ? 47L : 49L;
        BoundedAssemblyRequest request = version == 2
                ? BoundedAssemblyRequest.forControlledIndicator(seed)
                : BoundedAssemblyRequest.forControlledIndicatorValues(seed);
        BoundedAssemblyPlan shape = BoundedAssemblyPlan.resolve(request);
        String driverId = shape.idFor("driver", EntityKind.COMPONENT, "RG");
        String loadId = shape.idFor("load", EntityKind.COMPONENT, "RLOAD");
        BoundedAssemblyPlan driverPlan = BoundedAssemblyPlan.resolveForDiagnosticFault(
                request, driverId);
        BoundedAssemblyPlan loadPlan = BoundedAssemblyPlan.resolveForDiagnosticFault(
                request, loadId);
        assertPhysical(driverPlan, version,
                ControlledIndicatorBlockContributions.DRIVER_FAULT_DECISION_KEY);
        assertPhysical(loadPlan, version,
                ControlledIndicatorBlockContributions.LOAD_FAULT_DECISION_KEY);
    }

    private static void assertPhysical(BoundedAssemblyPlan plan, int version,
            String expectedDecision) {
        check(plan.getFaultDecisionKey().equals(expectedDecision),
                "fault decision was not retained for v" + version);
        PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(plan);
        PhysicalConstructionDeclarations declarations = metadata.getDeclarations();
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        boolean controlled = version != 1;

        check(plan.getRequest().getDescriptor().getGenerator().getVersion() == version,
                "generator version changed for v" + version);
        check(metadata.getBoard().getId().equals(controlled
                ? ControlledIndicatorDeviceBehavior.FAMILY_ID
                : BoundedGeneratedBoardAssembler.FAMILY_ID),
                "board family changed for v" + version);
        check(spec.getPackageMap().getPackageCount() == (controlled ? 7 : 3),
                "physical package count changed for v" + version);
        check(spec.getPackageMap().getUnitCount() == (controlled ? 7 : 3),
                "physical unit count changed for v" + version);

        List<String> localIds = controlled
                ? componentIds(plan, new String[][] {
                    { "driver", "RG" }, { "driver", "RPD" },
                    { "driver", "Q1" }, { "load", "RLOAD" },
                    { "load", "LED1" } })
                : componentIds(plan, new String[][] {
                    { "source", "R1" }, { "load", "R1" } });
        List<String> deviceIds = controlled
                ? componentIds(plan, new String[][] {
                    { DeviceAdapterContract.POWER_ADAPTER_KEY, "J1" },
                    { DeviceAdapterContract.CONTROL_ADAPTER_KEY, "J2" } })
                : Arrays.asList("J1");
        check(componentIds(declarations.getLocalParts()).equals(localIds),
                "local physical declaration order changed for v" + version);
        check(componentIds(declarations.getDeviceParts()).equals(deviceIds),
                "device physical declaration order changed for v" + version);
        check(componentIds(declarations.getBoardParts()).equals(concat(localIds, deviceIds)),
                "board physical declaration order changed for v" + version);
        check(componentIds(declarations.getSlotParts(controlled)).equals(controlled
                ? concat(localIds, deviceIds) : concat(deviceIds, localIds)),
                "slot physical declaration order changed for v" + version);

        if (controlled)
            assertControlled(plan, version, declarations, spec);
        else
            assertResistive(plan, declarations, spec);
        assertPackageOwnership(declarations, spec);
        assertBoardPads(metadata.getBoard(), declarations);
        assertInputs(plan, declarations.getExternalInputs(), controlled);
        assertNetEnvelope(plan, metadata.getBoard(), controlled);
        if (version == 3) {
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                    plan.getResolvedLoadRecipe();
            check(recipe != null && spec.getResolvedLoadRecipe() == recipe,
                    "v3 resolved recipe identity was not retained");
            check(plan.getLoad().getResolvedRecipe() == recipe,
                    "v3 load contribution lost resolved recipe identity");
        } else {
            check(spec.getResolvedLoadRecipe() == null,
                    "non-v3 plan unexpectedly contains a resolved recipe");
        }
    }

    private static void assertResistive(BoundedAssemblyPlan plan,
            PhysicalConstructionDeclarations declarations, ElectricalRealizationSpec spec) {
        PhysicalConstructionPartDeclaration source = declarations.getLocalParts().get(0);
        PhysicalConstructionPartDeclaration load = declarations.getLocalParts().get(1);
        assertResistiveResistor(plan, source, spec, "source", true);
        assertResistiveResistor(plan, load, spec, "load", false);

        PhysicalConstructionPartDeclaration connector = declarations.getDeviceParts().get(0);
        check("device".equals(connector.getConstructionOwnerKey())
                && "J1".equals(connector.getOwnerKey())
                && "resistive-device-join".equals(connector.getProviderId())
                && connector.getProviderVersion() == 1
                && "resistive-device-join".equals(connector.getRuntimeProviderId()),
                "v1 connector provider ownership changed");
        check("J1".equals(connector.getComponentId())
                && "CONNECTOR".equals(connector.getPublicType())
                && "J1".equals(connector.getDesignator())
                && connector.getPhysicalPackage() == PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "v1 connector identity/package changed");
        check(connector.getSpecification() instanceof BasicPhysicalSpecification
                && "J1_CONNECTOR".equals(connector.getSpecification().getSpecificationId()),
                "v1 connector specification changed");
        assertNameplate(connector.getNameplate(), "J1", "Power input connector", null, null);
        check("device".equals(connector.getBackingOwnerKey())
                && "CONNECTOR".equals(connector.getBackingElementId())
                && !connector.hasSecondary() && !connector.hasAttachments()
                && !connector.hasFault() && !connector.isCountedInMappedIdentity(),
                "v1 connector backing/mutation policy changed");
        assertTerminal(connector, 0, "J1.1", "1", "1", "J1_1",
                "J1.1", "J1", plan.netFor("source", "SUPPLY"));
        assertTerminal(connector, 1, "J1.2", "2", "2", "J1_2",
                "J1.2", "J1", plan.netFor("source", "RETURN"));
    }

    private static void assertResistiveResistor(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec,
            String owner, boolean source) {
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, "R1");
        ComposedBlockContribution.ResistorRecipe recipe = plan.getBlocks().get(owner)
                .getResistor("R1");
        check(owner.equals(part.getConstructionOwnerKey()) && owner.equals(part.getOwnerKey())
                && ("source".equals(owner) ? "resistive-source" : "resistive-load")
                    .equals(part.getProviderId())
                && part.getProviderVersion() == 1
                && part.getProviderId().equals(part.getRuntimeProviderId()),
                "v1 " + owner + " provider ownership changed");
        check(componentId.equals(part.getComponentId())
                && "RESISTOR".equals(part.getPublicType())
                && componentId.equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.AXIAL_RESISTOR,
                "v1 " + owner + " resistor identity/package changed");
        check(part.getSpecification() instanceof ResistorNameplate,
                "v1 " + owner + " resistor specification type changed");
        ResistorNameplate specification = (ResistorNameplate) part.getSpecification();
        check(componentId.equals(specification.getComponentId())
                && specification.getNominalResistanceOhms() == recipe.getResistanceOhms()
                && specification.getTolerancePercent() == 5.0
                && specification.getRatedWattage() == ComposedBlockContribution.RATED_WATTS,
                "v1 " + owner + " resistor specification changed");
        assertNameplate(part.getNameplate(), componentId, "Physical resistor markings",
                "Markings", "Color bands");
        check(owner.equals(part.getBackingOwnerKey())
                && "R1".equals(part.getBackingElementId())
                && part.hasSecondary() && owner.equals(part.getSecondaryOwnerKey())
                && "R1_SECONDARY".equals(part.getSecondaryElementId())
                && part.hasAttachments() && "device".equals(part.getFirstAttachmentOwnerKey())
                && "device".equals(part.getSecondAttachmentOwnerKey())
                && (source ? "SOURCE_FIRST_ATTACHMENT" : "LOAD_FIRST_ATTACHMENT")
                    .equals(part.getFirstAttachmentElementId())
                && (source ? "SOURCE_SECOND_ATTACHMENT" : "LOAD_SECOND_ATTACHMENT")
                    .equals(part.getSecondAttachmentElementId()),
                "v1 " + owner + " backing/attachments changed");
        check(part.hasFault()
                && part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE
                && ResistiveBlockContributions.FAULT_LOCAL_ID.equals(part.getFaultLocalId())
                && part.getFaultEffectiveOhms() == ComposedBlockContribution.FAULT_RESISTANCE_OHMS
                && BoundedGeneratedBoardAssembler.FAMILY_ID.equals(part.getFaultFamilyId())
                && part.getFaultOwnerKey() == null && part.getFaultElementId() == null
                && owner.equals(part.getRepairOwnerKey())
                && "R1".equals(part.getRepairLocalComponentId())
                && componentId.equals(part.getRepairComponentId()),
                "v1 " + owner + " fault/repair declaration changed");
        check(part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "v1 " + owner + " mutability/identity policy changed");
        assertTerminal(part, 0, spec.getPadId(owner, "R1.1"), "1", "1", "R1_1",
                owner + "/R1_1", owner, plan.netFor(owner, "SUPPLY"));
        assertTerminal(part, 1, spec.getPadId(owner, "R1.2"), "2", "2", "R1_2",
                owner + "/R1_2", owner, plan.netFor(owner, source ? "OUT" : "RETURN"));
    }

    private static void assertControlled(BoundedAssemblyPlan plan, int version,
            PhysicalConstructionDeclarations declarations, ElectricalRealizationSpec spec) {
        List<PhysicalConstructionPartDeclaration> parts = declarations.getLocalParts();
        assertControlledDriver(plan, parts.get(0), spec, "RG");
        assertControlledDriverPulldown(plan, parts.get(1), spec);
        assertControlledNmos(plan, parts.get(2), spec);
        assertControlledLoad(plan, version, parts.get(3), spec);
        assertControlledLed(plan, parts.get(4), spec);

        PhysicalConstructionPartDeclaration power = declarations.getDeviceParts().get(0);
        PhysicalConstructionPartDeclaration control = declarations.getDeviceParts().get(1);
        assertControlledConnector(plan, power, DeviceAdapterContract.POWER_ADAPTER_KEY,
                "J1", "LOAD_CONNECTOR", "Load supply connector");
        assertControlledConnector(plan, control, DeviceAdapterContract.CONTROL_ADAPTER_KEY,
                "J2", "CONTROL_COMMAND", "Control input connector");
    }

    private static void assertControlledDriver(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec,
            String local) {
        String owner = "driver";
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, local);
        check(owner.equals(part.getConstructionOwnerKey()) && owner.equals(part.getOwnerKey())
                && ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(part.getProviderId())
                && part.getProviderVersion() == 1
                && ControlledIndicatorBlockContributions.FAMILY_ID.equals(part.getRuntimeProviderId()),
                "controlled driver provider ownership changed");
        check(componentId.equals(part.getComponentId()) && "RESISTOR".equals(part.getPublicType())
                && local.equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.AXIAL_RESISTOR,
                "controlled RG identity/package changed");
        ResistorNameplate value = resistorSpecification(part, componentId);
        check(value.getNominalResistanceOhms() == ControlledIndicatorBlockContributions.RG_OHMS
                && value.getTolerancePercent() == 5.0
                && value.getRatedWattage() == ComposedBlockContribution.RATED_WATTS,
                "controlled RG specification changed");
        assertNameplate(part.getNameplate(), "RG", "Gate drive resistor markings",
                "Markings", "Color bands");
        assertMutableControlledResistor(part, owner, local, componentId, "RG_FAULT_SWITCH",
                "RG_FIRST_ATTACHMENT", "RG_SECOND_ATTACHMENT");
        assertTerminal(part, 0, spec.getPadId(owner, "RG.1"), "1", "1", "RG_1",
                "driver/RG_1", owner, plan.netFor(owner, "CONTROL"));
        assertTerminal(part, 1, spec.getPadId(owner, "RG.2"), "2", "2", "RG_2",
                "driver/RG_2", owner, plan.netFor(owner, "GATE"));
    }

    private static void assertControlledDriverPulldown(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec) {
        String owner = "driver";
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, "RPD");
        check(componentId.equals(part.getComponentId()) && "RESISTOR".equals(part.getPublicType())
                && "RPD".equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.AXIAL_RESISTOR,
                "controlled RPD identity/package changed");
        ResistorNameplate value = resistorSpecification(part, componentId);
        check(value.getNominalResistanceOhms() == ControlledIndicatorBlockContributions.RPD_OHMS,
                "controlled RPD value changed");
        assertNameplate(part.getNameplate(), "RPD", "Gate pull-down resistor markings",
                "Markings", "Color bands");
        check(owner.equals(part.getBackingOwnerKey()) && "RPD".equals(part.getBackingElementId())
                && !part.hasSecondary() && !part.hasAttachments() && !part.hasFault()
                && !part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "controlled RPD backing/mutation policy changed");
        assertTerminal(part, 0, spec.getPadId(owner, "RPD.1"), "1", "1", "RPD_1",
                "driver/RPD_1", owner, plan.netFor(owner, "GATE"));
        assertTerminal(part, 1, spec.getPadId(owner, "RPD.2"), "2", "2", "RPD_2",
                "driver/RPD_2", owner, plan.netFor(owner, "RETURN"));
    }

    private static void assertControlledNmos(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec) {
        String owner = "driver";
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, "Q1");
        check(componentId.equals(part.getComponentId())
                && "NMOS_TRANSISTOR".equals(part.getPublicType())
                && "Q1".equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.TO92_NMOS,
                "controlled Q1 identity/package changed");
        check(part.getSpecification() instanceof NmosSpecification,
                "controlled Q1 specification type changed");
        NmosSpecification value = (NmosSpecification) part.getSpecification();
        check(componentId.equals(value.getSpecificationId())
                && value.getThresholdVoltage() == BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_THRESHOLD_VOLTS
                && value.getBeta() == BoundedGeneratedBoardAssembler.CONTROLLED_NMOS_BETA,
                "controlled Q1 specification changed");
        assertNameplate(part.getNameplate(), "Q1", "N-channel MOSFET", "Part",
                "N-channel MOSFET");
        check(owner.equals(part.getBackingOwnerKey()) && "Q1".equals(part.getBackingElementId())
                && !part.hasSecondary() && !part.hasAttachments() && !part.hasFault()
                && !part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "controlled Q1 backing/mutation policy changed");
        assertTerminal(part, 0, spec.getPadId(owner, "Q1.G"), "G", "G", "Q1_G",
                "driver/Q1_G", owner, plan.netFor(owner, "GATE"));
        assertTerminal(part, 1, spec.getPadId(owner, "Q1.D"), "D", "D", "Q1_D",
                "driver/Q1_D", owner, plan.netFor(owner, "SWITCHED_SINK"));
        assertTerminal(part, 2, spec.getPadId(owner, "Q1.S"), "S", "S", "Q1_S",
                "driver/Q1_S", owner, plan.netFor(owner, "RETURN"));
    }

    private static void assertControlledLoad(BoundedAssemblyPlan plan, int version,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec) {
        String owner = "load";
        String local = "RLOAD";
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, local);
        ComposedBlockContribution.ResistorRecipe recipe = plan.getLoad().getResistor(local);
        check(owner.equals(part.getConstructionOwnerKey()) && owner.equals(part.getOwnerKey())
                && ControlledIndicatorBlockContributions.LOAD_TYPE_ID.equals(part.getProviderId())
                && part.getProviderVersion() == (version == 3 ? 2 : 1)
                && ControlledIndicatorBlockContributions.FAMILY_ID.equals(part.getRuntimeProviderId()),
                "controlled load provider ownership changed");
        check(componentId.equals(part.getComponentId()) && "RESISTOR".equals(part.getPublicType())
                && local.equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.AXIAL_RESISTOR,
                "controlled RLOAD identity/package changed");
        ResistorNameplate value = resistorSpecification(part, componentId);
        check(value.getNominalResistanceOhms() == recipe.getResistanceOhms()
                && value.getTolerancePercent() == recipe.getTolerancePercent()
                && value.getRatedWattage() == recipe.getRatedWatts(),
                "controlled RLOAD specification changed");
        if (version == 2)
            assertNameplate(part.getNameplate(), "RLOAD", "Load resistor markings",
                    "Markings", "Color bands");
        else {
            PhysicalNameplate expected = recipe.getResolvedRecipe()
                    .getPlayerVisibleNameplate();
            assertNameplate(part.getNameplate(), "RLOAD", expected.getDisplayName(),
                    expected.getWorkbenchDetailLabel(), expected.getWorkbenchDetailValue());
        }
        assertMutableControlledResistor(part, owner, local, componentId,
                "RLOAD_FAULT_SWITCH", "RLOAD_FIRST_ATTACHMENT", "RLOAD_SECOND_ATTACHMENT");
        assertTerminal(part, 0, spec.getPadId(owner, "RLOAD.1"), "1", "1", "RLOAD_1",
                "load/RLOAD_1", owner, plan.netFor(owner, "SUPPLY"));
        assertTerminal(part, 1, spec.getPadId(owner, "RLOAD.2"), "2", "2", "RLOAD_2",
                "load/RLOAD_2", owner, plan.netFor(owner, "LED_NODE"));
    }

    private static void assertControlledLed(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, ElectricalRealizationSpec spec) {
        String owner = "load";
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, "LED1");
        check(componentId.equals(part.getComponentId()) && "LED".equals(part.getPublicType())
                && "LED1".equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.THROUGH_HOLE_LED,
                "controlled LED identity/package changed");
        check(part.getSpecification() instanceof LedNameplate,
                "controlled LED specification type changed");
        LedNameplate value = (LedNameplate) part.getSpecification();
        check(componentId.equals(value.getSpecificationId())
                && "Generic red LED".equals(value.getDisplayName())
                && BoundedGeneratedBoardAssembler.CONTROLLED_LED_MODEL.equals(value.getModelName())
                && value.getRed() == 1.0 && value.getGreen() == 0.0 && value.getBlue() == 0.0,
                "controlled LED specification changed");
        assertNameplate(part.getNameplate(), "LED1", "Generic red LED", null, null);
        check(owner.equals(part.getBackingOwnerKey()) && "LED1".equals(part.getBackingElementId())
                && !part.hasSecondary() && !part.hasAttachments() && !part.hasFault()
                && !part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "controlled LED backing/mutation policy changed");
        assertTerminal(part, 0, spec.getPadId(owner, "LED1.A"), "A", "A", "LED1_A",
                "load/LED1_A", owner, plan.netFor(owner, "LED_NODE"));
        assertTerminal(part, 1, spec.getPadId(owner, "LED1.K"), "K", "K", "LED1_K",
                "load/LED1_K", owner, plan.netFor(owner, "SWITCHED_LOAD"));
    }

    private static void assertMutableControlledResistor(PhysicalConstructionPartDeclaration part,
            String owner, String local, String componentId, String switchId,
            String firstAttachment, String secondAttachment) {
        check(owner.equals(part.getBackingOwnerKey()) && local.equals(part.getBackingElementId())
                && part.hasSecondary() && owner.equals(part.getSecondaryOwnerKey())
                && (local + "_SECONDARY").equals(part.getSecondaryElementId())
                && part.hasAttachments() && owner.equals(part.getFirstAttachmentOwnerKey())
                && owner.equals(part.getSecondAttachmentOwnerKey())
                && firstAttachment.equals(part.getFirstAttachmentElementId())
                && secondAttachment.equals(part.getSecondAttachmentElementId()),
                "controlled " + local + " backing/attachments changed");
        check(part.hasFault()
                && part.getFaultKind() == ComposedBlockContribution.FaultSpec.Kind.OPEN
                && local.equals(part.getFaultLocalId())
                && part.getFaultEffectiveOhms() == ComposedBlockContribution.FAULT_RESISTANCE_OHMS
                && ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(part.getFaultFamilyId())
                && owner.equals(part.getFaultOwnerKey()) && switchId.equals(part.getFaultElementId())
                && owner.equals(part.getRepairOwnerKey())
                && local.equals(part.getRepairLocalComponentId())
                && componentId.equals(part.getRepairComponentId())
                && part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "controlled " + local + " fault/repair declaration changed");
    }

    private static void assertControlledConnector(BoundedAssemblyPlan plan,
            PhysicalConstructionPartDeclaration part, String owner, String local,
            String backing, String display) {
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, local);
        check("device".equals(part.getConstructionOwnerKey()) && owner.equals(part.getOwnerKey())
                && "controlled-device-join".equals(part.getProviderId())
                && part.getProviderVersion() == 1
                && "controlled-device-join".equals(part.getRuntimeProviderId())
                && componentId.equals(part.getComponentId())
                && "CONNECTOR".equals(part.getPublicType()) && local.equals(part.getDesignator())
                && part.getPhysicalPackage() == PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                "controlled " + local + " connector ownership/package changed");
        check(part.getSpecification() instanceof BasicPhysicalSpecification
                && (local + "_CONNECTOR").equals(part.getSpecification().getSpecificationId()),
                "controlled " + local + " connector specification changed");
        assertNameplate(part.getNameplate(), local, display, null, null);
        check("device".equals(part.getBackingOwnerKey()) && backing.equals(part.getBackingElementId())
                && !part.hasSecondary() && !part.hasAttachments() && !part.hasFault()
                && !part.isMutableResistor() && part.isCountedInMappedIdentity(),
                "controlled " + local + " connector backing/mutation policy changed");
        String pad1 = plan.idFor(owner, EntityKind.PAD, local + ".1");
        String pad2 = plan.idFor(owner, EntityKind.PAD, local + ".2");
        assertTerminal(part, 0, pad1, "1", "1", local + "_1", owner + "/" + local + "_1",
                owner, plan.netFor(owner, "OUTPUT"));
        assertTerminal(part, 1, pad2, "2", "2", local + "_2", owner + "/" + local + "_2",
                owner, plan.netFor(owner, "RETURN"));
    }

    private static ResistorNameplate resistorSpecification(
            PhysicalConstructionPartDeclaration part, String componentId) {
        check(part.getSpecification() instanceof ResistorNameplate,
                "resistor specification type changed for " + componentId);
        ResistorNameplate result = (ResistorNameplate) part.getSpecification();
        check(componentId.equals(result.getComponentId()),
                "resistor specification escaped component identity for " + componentId);
        return result;
    }

    private static void assertPackageOwnership(PhysicalConstructionDeclarations declarations,
            ElectricalRealizationSpec spec) {
        List<PhysicalConstructionPartDeclaration> all = declarations.getBoardParts();
        Set<String> components = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : all) {
            check(components.add(part.getComponentId()),
                    "duplicate physical component declaration " + part.getComponentId());
            PhysicalPackage mapped = spec.getPackageMap().getPackages().get(part.getComponentId());
            check(mapped == part.getPhysicalPackage()
                    && part.getOwnerKey().equals(spec.getPackageMap().getPackageOwners()
                        .get(part.getComponentId())),
                    "package owner/identity changed for " + part.getComponentId());
            ElectricalUnitPackageMap.Unit unit = null;
            for (ElectricalUnitPackageMap.Unit candidate : spec.getPackageMap().getUnits().values())
                if (part.getComponentId().equals(candidate.getComponentId())) {
                    check(unit == null, "multiple physical units share " + part.getComponentId());
                    unit = candidate;
                }
            check(unit != null && part.getOwnerKey().equals(unit.getOwnerKey())
                    && unit.getComponentId().equals(part.getComponentId()),
                    "unit/package ownership changed for " + part.getComponentId());
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
                check(unit.getPackageTerminalByUnitTerminal().containsKey(terminal.getTerminalId())
                        && terminal.getPackageTerminalId().equals(unit
                            .getPackageTerminalByUnitTerminal().get(terminal.getTerminalId())),
                        "unit terminal mapping changed for " + part.getComponentId());
            }
        }
        check(components.equals(spec.getPackageMap().getPackages().keySet()),
                "physical declaration/package map coverage changed");
    }

    private static void assertBoardPads(TroubleshootBoard board,
            PhysicalConstructionDeclarations declarations) {
        Set<String> pads = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
                check(pads.add(terminal.getPadId()), "duplicate board pad " + terminal.getPadId());
                BoardPad pad = board.getPad(terminal.getPadId());
                check(pad != null && part.getComponentId().equals(pad.getComponentId())
                        && terminal.getTerminalId().equals(pad.getTerminalId())
                        && terminal.getNetId().equals(pad.getNetId()),
                        "board pad mapping changed for " + terminal.getPadId());
            }
    }

    private static void assertInputs(BoundedAssemblyPlan plan,
            List<PhysicalExternalInputDeclaration> inputs, boolean controlled) {
        check(inputs.size() == (controlled ? 2 : 1),
                "external input count changed");
        for (PhysicalExternalInputDeclaration input : inputs)
            check("device".equals(input.getProviderOwnerKey())
                    && input.getNominalVoltage() == ElectricalRealizationSpec.EXTERNAL_SUPPLY_VOLTS,
                    "external input ownership/voltage changed");
        if (!controlled) {
            PhysicalExternalInputDeclaration input = inputs.get(0);
            check(BoundedGeneratedBoardAssembler.POWER_INPUT_ID.equals(input.getInputId())
                    && "J1.1".equals(input.getPositivePadId())
                    && "J1.2".equals(input.getReturnPadId())
                    && plan.netFor("source", "SUPPLY").equals(input.getPositiveNetId())
                    && plan.netFor("source", "RETURN").equals(input.getReturnNetId()),
                    "v1 external power mapping changed");
            return;
        }
        PhysicalExternalInputDeclaration load = inputs.get(0);
        PhysicalExternalInputDeclaration control = inputs.get(1);
        check(ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID.equals(load.getInputId())
                && plan.idFor(DeviceAdapterContract.POWER_ADAPTER_KEY, EntityKind.PAD, "J1.1")
                    .equals(load.getPositivePadId())
                && plan.idFor(DeviceAdapterContract.POWER_ADAPTER_KEY, EntityKind.PAD, "J1.2")
                    .equals(load.getReturnPadId())
                && plan.netFor("load", "SUPPLY").equals(load.getPositiveNetId())
                && plan.netFor(DeviceAdapterContract.POWER_ADAPTER_KEY, "RETURN")
                    .equals(load.getReturnNetId()),
                "controlled load power mapping changed");
        check(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID.equals(control.getInputId())
                && plan.idFor(DeviceAdapterContract.CONTROL_ADAPTER_KEY, EntityKind.PAD, "J2.1")
                    .equals(control.getPositivePadId())
                && plan.idFor(DeviceAdapterContract.CONTROL_ADAPTER_KEY, EntityKind.PAD, "J2.2")
                    .equals(control.getReturnPadId())
                && plan.netFor("driver", "CONTROL").equals(control.getPositiveNetId())
                && plan.netFor(DeviceAdapterContract.CONTROL_ADAPTER_KEY, "RETURN")
                    .equals(control.getReturnNetId()),
                "controlled control power mapping changed");
    }

    private static void assertNetEnvelope(BoundedAssemblyPlan plan, TroubleshootBoard board,
            boolean controlled) {
        Set<String> expected = new HashSet<String>();
        if (controlled) {
            expected.add(plan.netFor("load", "SUPPLY"));
            expected.add(plan.netFor("driver", "CONTROL"));
            expected.add(plan.netFor("load", "LED_NODE"));
            expected.add(plan.netFor("driver", "SWITCHED_SINK"));
            expected.add(plan.netFor("driver", "GATE"));
            expected.add(plan.netFor("driver", "RETURN"));
        } else {
            expected.add(plan.netFor("source", "SUPPLY"));
            expected.add(plan.netFor("source", "OUT"));
            expected.add(plan.netFor("source", "RETURN"));
        }
        check(new HashSet<String>(board.getNetIds()).equals(expected),
                "board net envelope changed");
    }

    private static void assertTerminal(PhysicalConstructionPartDeclaration part, int index,
            String padId, String terminalId, String packageTerminalId, String endpointId,
            String manifestKey, String manifestBlockKey, String netId) {
        PhysicalConstructionTerminalDeclaration terminal = part.getTerminals().get(index);
        check(padId.equals(terminal.getPadId()) && terminalId.equals(terminal.getTerminalId())
                && packageTerminalId.equals(terminal.getPackageTerminalId())
                && endpointId.equals(terminal.getEndpointId()) && netId.equals(terminal.getNetId())
                && manifestKey.equals(terminal.getManifestKey())
                && manifestBlockKey.equals(terminal.getManifestBlockKey()),
                "physical terminal mapping changed for " + part.getComponentId()
                    + " index " + index);
    }

    private static void assertNameplate(PhysicalNameplate plate, String id, String display,
            String detailLabel, String detailValue) {
        check(plate != null && id.equals(plate.getId()) && display.equals(plate.getDisplayName())
                && ((detailLabel == null && !plate.hasWorkbenchDetail())
                    || (detailLabel != null && plate.hasWorkbenchDetail()
                        && detailLabel.equals(plate.getWorkbenchDetailLabel())
                        && detailValue.equals(plate.getWorkbenchDetailValue()))),
                "physical nameplate changed for " + id);
    }

    private static void declarationValidationCanaries() {
        final PhysicalConstructionPartDeclaration.Builder fixed =
                baseBuilder("canary/component", PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        fixed.terminal(new PhysicalConstructionTerminalDeclaration("canary.1", "1", "1",
                "CANARY_NET_1", "canary_1", "canary/1", "canary"));
        fixed.terminal(new PhysicalConstructionTerminalDeclaration("canary.2", "2", "2",
                "CANARY_NET_2", "canary_2", "canary/2", "canary"));
        expectIllegal(new Runnable() {
            @Override public void run() {
                fixed.fault(ComposedBlockContribution.FaultSpec.Kind.INCORRECT_RESISTANCE,
                        "fault", 10.0, "family", null, null, "canary", "canary/component",
                        "canary/component").build();
            }
        }, "fixed part accepted a physical fault");

        final PhysicalConstructionPartDeclaration.Builder mutable = baseBuilder(
                "canary/mutable", PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR);
        mutable.terminal(new PhysicalConstructionTerminalDeclaration("mutable.1", "1", "1",
                "MUTABLE_NET_1", "mutable_1", "mutable/1", "mutable"));
        mutable.terminal(new PhysicalConstructionTerminalDeclaration("mutable.2", "2", "2",
                "MUTABLE_NET_2", "mutable_2", "mutable/2", "mutable"));
        expectIllegal(new Runnable() {
            @Override public void run() {
                mutable.fault(ComposedBlockContribution.FaultSpec.Kind.OPEN, "fault", 10.0,
                        "family", null, null, "canary", "mutable", "canary/mutable").build();
            }
        }, "open fault without switch owner accepted");

        final PhysicalConstructionPartDeclaration.Builder duplicate = baseBuilder(
                "canary/duplicate", PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        duplicate.terminal(new PhysicalConstructionTerminalDeclaration("duplicate.1", "1", "1",
                "DUP_NET_1", "duplicate_1", "duplicate/1", "duplicate"));
        duplicate.terminal(new PhysicalConstructionTerminalDeclaration("duplicate.2", "1", "2",
                "DUP_NET_2", "duplicate_2", "duplicate/2", "duplicate"));
        expectIllegal(new Runnable() {
            @Override public void run() { duplicate.build(); }
        }, "duplicate terminal ID accepted");
    }

    private static PhysicalConstructionPartDeclaration.Builder baseBuilder(String componentId,
            PhysicalConstructionPartDeclaration.PartPolicy policy) {
        return PhysicalConstructionPartDeclaration.builder("canary", "canary", "canary-provider",
                1, "canary-runtime", componentId, PhysicalPackages.AXIAL_RESISTOR, "RESISTOR",
                "R", new ResistorNameplate(componentId, 100.0, 5.0),
                new PhysicalNameplate(componentId, "Canary resistor"), "canary", "R", policy);
    }

    private static List<String> componentIds(
            List<PhysicalConstructionPartDeclaration> parts) {
        ArrayList<String> result = new ArrayList<String>();
        for (PhysicalConstructionPartDeclaration part : parts) result.add(part.getComponentId());
        return result;
    }

    private static List<String> componentIds(BoundedAssemblyPlan plan, String[][] values) {
        ArrayList<String> result = new ArrayList<String>();
        for (String[] value : values)
            result.add(plan.idFor(value[0], EntityKind.COMPONENT, value[1]));
        return result;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        ArrayList<String> result = new ArrayList<String>(first);
        result.addAll(second);
        return result;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + message);
    }
}
