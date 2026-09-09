package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

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
            controlledBothFaultDecisions();
            declarationValidationCanaries();
            constructionProvenanceCanaries();
            physicalBoundaryCanaries();
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

    /** The current controlled route exposes both admitted targets through the diagnostic resolver. */
    private static void controlledBothFaultDecisions() {
        long seed = 49L;
        int version = BoundedAssemblyRequest.GENERATOR_VERSION;
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forControlledIndicator(seed);
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

        check(plan.getRequest().getDescriptor().getGenerator().getVersion() ==
                BoundedAssemblyRequest.GENERATOR_VERSION,
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
        check(new HashSet<String>(componentIds(declarations.getLocalParts())).equals(
                new HashSet<String>(localIds)),
                "local physical declaration coverage changed for v" + version);
        check(new HashSet<String>(componentIds(declarations.getDeviceParts())).equals(
                new HashSet<String>(deviceIds)),
                "device physical declaration coverage changed for v" + version);
        check(isSorted(componentIds(declarations.getBoardParts())),
                "board slot declarations are not deterministic for v" + version);

        if (controlled)
            assertControlled(plan, version, declarations, spec);
        else
            assertResistive(plan, declarations, spec);
        assertPackageOwnership(declarations, spec);
        assertBoardPads(metadata.getBoard(), declarations);
        assertElectricalTerminalCorrespondence(declarations, spec);
        assertInputs(plan, declarations.getExternalInputs(), controlled);
        assertNetEnvelope(metadata.getBoard(), spec);
        if (version != 1) {
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                    plan.getResolvedLoadRecipe();
            check(recipe != null && spec.getResolvedLoadRecipe() == recipe,
                    "current resolved recipe identity was not retained");
            check(plan.getLoad().getResolvedValueRecipe() == recipe,
                    "current load contribution lost resolved recipe identity");
        } else {
            check(spec.getResolvedLoadRecipe() == null,
                    "non-v3 plan unexpectedly contains a resolved recipe");
        }
    }

    private static void assertResistive(BoundedAssemblyPlan plan,
            PhysicalConstructionDeclarations declarations, ElectricalRealizationSpec spec) {
        PhysicalConstructionPartDeclaration source = findPart(declarations, "source", "R1");
        PhysicalConstructionPartDeclaration load = findPart(declarations, "load", "R1");
        assertResistiveResistor(plan, source, spec, "source", true);
        assertResistiveResistor(plan, load, spec, "load", false);

        PhysicalConstructionPartDeclaration connector = declarations.getDeviceParts().get(0);
        check("device".equals(connector.getConstructionOwnerKey())
                && "device".equals(connector.getOwnerKey())
                && "resistive-device-join".equals(connector.getProviderId())
                && connector.getProviderVersion() == 1,
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
                && part.getProviderVersion() == 1,
                "v1 " + owner + " provider ownership changed");
        check(componentId.equals(part.getComponentId())
                && "RESISTOR".equals(part.getPublicType())
                && (source ? "R1" : "R2").equals(part.getDesignator())
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
                && "R1".equals(part.getFaultLocalId())
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
        assertControlledDriver(plan, findPart(declarations, "driver", "RG"), spec, "RG");
        assertControlledDriverPulldown(plan, findPart(declarations, "driver", "RPD"), spec);
        assertControlledNmos(plan, findPart(declarations, "driver", "Q1"), spec);
        assertControlledLoad(plan, version, findPart(declarations, "load", "RLOAD"), spec);
        assertControlledLed(plan, findPart(declarations, "load", "LED1"), spec);

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
                && part.getProviderVersion() == 1,
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
                && value.getThresholdVoltage() == ElectricalRealizationSpec.CONTROLLED_NMOS_THRESHOLD_VOLTS
                && value.getBeta() == ElectricalRealizationSpec.CONTROLLED_NMOS_BETA,
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
                && part.getProviderVersion() == ControlledIndicatorBlockContributions.LOAD_VERSION,
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
        PhysicalNameplate expected = recipe.getResolvedRecipe().getPlayerVisibleNameplate();
        assertNameplate(part.getNameplate(), "RLOAD", expected.getDisplayName(),
                expected.getWorkbenchDetailLabel(), expected.getWorkbenchDetailValue());
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
                && ElectricalRealizationSpec.CONTROLLED_LED_MODEL.equals(value.getModelName())
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

    /** Every physical terminal must point at the exact spec-owned pad mapping. */
    private static void assertElectricalTerminalCorrespondence(
            PhysicalConstructionDeclarations declarations,
            ElectricalRealizationSpec spec) {
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts()) {
            Set<String> terminalIds = new HashSet<String>();
            Set<String> padIds = new HashSet<String>();
            for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
                check(terminal.hasElectricalProvenance()
                        && part.getOwnerKey().equals(terminal.getOwnerKey())
                        && part.getComponentId().equals(terminal.getComponentId()),
                        "terminal lost owner/component provenance for " + part.getComponentId());
                ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
                        terminal.getOwnerKey(), terminal.getLocalId(), terminal.getTerminalId());
                ElectricalRealizationSpec.PadBindingSpec pad = spec.getPadBinding(
                        terminal.getPadId());
                check(mapping != null && pad != null
                        && part.getComponentId().equals(mapping.getComponentId())
                        && part.getComponentId().equals(pad.getComponentId())
                        && terminal.getPackageTerminalId().equals(mapping.getPackageTerminalId())
                        && terminal.getPackageTerminalId().equals(pad.getTerminalId())
                        && terminal.getNetId().equals(mapping.getNetId())
                        && terminal.getNetId().equals(pad.getNetId())
                        && terminal.getOwnerKey().equals(pad.getOwnerKey())
                        && terminal.getLocalId().equals(pad.getLocalId())
                        && terminal.getTerminalId().equals(pad.getTerminalId())
                        && terminalIds.add(terminal.getTerminalId())
                        && padIds.add(terminal.getPadId()),
                        "terminal/package/net/pad correspondence changed for " +
                            part.getComponentId());
            }
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
            check(ElectricalRealizationSpec.LEGACY_POWER_INPUT_ID.equals(input.getInputId())
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

    private static void assertNetEnvelope(TroubleshootBoard board,
            ElectricalRealizationSpec spec) {
        check(new HashSet<String>(board.getNetIds()).equals(
                new HashSet<String>(spec.getExpectedNetIds())),
                "board net envelope changed from the electrical spec");
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

    /** Plan/spec identity is part of the metadata boundary, not a value key. */
    private static void constructionProvenanceCanaries() {
        final BoundedAssemblyPlan resistive = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forCanary(17L));
        final BoundedAssemblyPlan controlled = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(19L));
        final PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(resistive);
        check(metadata.getPlan() == resistive
                && metadata.getSpec() == resistive.getElectricalRealizationSpec(),
                "physical metadata did not retain exact plan/spec identity");
        expectIllegal(new Runnable() {
            @Override public void run() {
                new PhysicalConstructionMetadata(resistive,
                        controlled.getElectricalRealizationSpec(), metadata.getBoard(),
                        metadata.getSpecifications(), metadata.getDeclarations());
            }
        }, "metadata accepted a spec from another construction plan");
    }

    /** Exercise the actual pre-mutation declaration gate with foreign records. */
    private static void physicalBoundaryCanaries() {
        final BoundedAssemblyPlan resistive = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forCanary(23L));
        final PhysicalConstructionMetadata resistiveMetadata =
                PhysicalConstructionMaterializer.describe(resistive);
        final PhysicalConstructionDeclarations resistiveDeclarations =
                resistiveMetadata.getDeclarations();
        final ElectricalRealizationSpec resistiveSpec =
                resistive.getElectricalRealizationSpec();

        /* Valid device-owned bridge declarations remain an admitted control. */
        PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                resistiveDeclarations);
        check(true, "valid device bridge declaration was rejected");

        final PhysicalConstructionPartDeclaration source = findPart(
                resistiveDeclarations, "source", "R1");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                    replacePart(resistiveDeclarations, copyPart(source, "swapped-pad")));
            }
        }, "physical boundary accepted a swapped pad/net declaration");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                    replacePart(resistiveDeclarations, copyPart(source, "foreign-provider")));
            }
        }, "physical boundary accepted a foreign provider identity");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                    replacePart(resistiveDeclarations, copyPart(source, "swapped-owner")));
            }
        }, "physical boundary accepted a foreign terminal owner declaration");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                    replacePart(resistiveDeclarations, copyPart(source, "foreign-secondary")));
            }
        }, "physical boundary accepted a same-kind foreign secondary");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(resistive, resistiveSpec,
                    replacePart(resistiveDeclarations, copyPart(source, "foreign-attachment")));
            }
        }, "physical boundary accepted a same-kind foreign attachment");

        final BoundedAssemblyPlan controlled = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(29L));
        final PhysicalConstructionMetadata controlledMetadata =
                PhysicalConstructionMaterializer.describe(controlled);
        final PhysicalConstructionDeclarations controlledDeclarations =
                controlledMetadata.getDeclarations();
        final ElectricalRealizationSpec controlledSpec =
                controlled.getElectricalRealizationSpec();
        PhysicalConstructionMaterializer.validateDeclarations(controlled, controlledSpec,
                controlledDeclarations);
        check(true, "valid controlled bridge declaration was rejected");
        final PhysicalConstructionPartDeclaration gateResistor = findPart(
                controlledDeclarations, "driver", "RG");
        expectBoundaryReject(new Runnable() {
            @Override public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(controlled, controlledSpec,
                    replacePart(controlledDeclarations, copyPart(gateResistor, "foreign-fault")));
            }
        }, "physical boundary accepted a same-kind foreign fault switch");

        sharedPackageControl();
    }

    /** A shared physical package with two explicit logical units is valid. */
    private static void sharedPackageControl() {
        Vector<String> terminals = new Vector<String>(Arrays.asList(
                "P1", "P2", "VCC", "GND"));
        PhysicalPackage shared = PhysicalPackage.developerPackageWithGenericGeometry(
                "A04_SHARED_BOUNDARY_PACKAGE", terminals, new Vector<String>(), false);
        String componentId = "board/U1";
        String owner = "multi-unit";
        Map<String, PhysicalPackage> packages = new HashMap<String, PhysicalPackage>();
        packages.put(componentId, shared);
        Map<String, String> owners = new HashMap<String, String>();
        owners.put(componentId, owner);
        Map<String, String> firstMap = new HashMap<String, String>();
        firstMap.put("IN", "P1");
        firstMap.put("OUT", "P2");
        firstMap.put("VCC", "VCC");
        firstMap.put("GND", "GND");
        Map<String, String> secondMap = new HashMap<String, String>();
        secondMap.put("A", "P2");
        secondMap.put("K", "P1");
        secondMap.put("VCC", "VCC");
        secondMap.put("GND", "GND");
        ElectricalUnitPackageMap.Unit first = new ElectricalUnitPackageMap.Unit(
                owner, "analog", componentId, new ArrayList<String>(firstMap.keySet()),
                firstMap);
        ElectricalUnitPackageMap.Unit second = new ElectricalUnitPackageMap.Unit(
                owner, "indicator", componentId, new ArrayList<String>(secondMap.keySet()),
                secondMap);
        ElectricalUnitPackageMap map = new ElectricalUnitPackageMap(
                ElectricalUnitPackageMap.VERSION, packages, owners,
                Arrays.asList(first, second));
        check(map.getPackageCount() == 1 && map.getUnitCount() == 2,
                "valid shared package control was split or dropped");
        check("VCC".equals(first.getPackageTerminalByUnitTerminal().get("VCC"))
                && "VCC".equals(second.getPackageTerminalByUnitTerminal().get("VCC"))
                && "GND".equals(first.getPackageTerminalByUnitTerminal().get("GND"))
                && "GND".equals(second.getPackageTerminalByUnitTerminal().get("GND")),
                "valid shared package control lost explicit supply pin mappings");
    }

    private static PhysicalConstructionPartDeclaration findPart(
            PhysicalConstructionDeclarations declarations, String owner, String local) {
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            if (owner.equals(part.getOwnerKey()) && local.equals(part.getBackingElementId()))
                return part;
        throw new AssertionError("Missing physical canary part " + owner + "/" + local);
    }

    private static PhysicalConstructionDeclarations replacePart(
            PhysicalConstructionDeclarations declarations,
            PhysicalConstructionPartDeclaration replacement) {
        List<PhysicalConstructionPartDeclaration> local =
                new ArrayList<PhysicalConstructionPartDeclaration>(declarations.getLocalParts());
        List<PhysicalConstructionPartDeclaration> device =
                new ArrayList<PhysicalConstructionPartDeclaration>(declarations.getDeviceParts());
        boolean replaced = replacePart(local, replacement) || replacePart(device, replacement);
        if (!replaced) throw new AssertionError("Canary replacement part was not found");
        return new PhysicalConstructionDeclarations(local, device,
                declarations.getExternalInputs(), declarations.getBoardFamilyId(),
                declarations.getBoardName());
    }

    private static boolean replacePart(List<PhysicalConstructionPartDeclaration> parts,
            PhysicalConstructionPartDeclaration replacement) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).getComponentId().equals(replacement.getComponentId())) {
                parts.set(index, replacement);
                return true;
            }
        }
        return false;
    }

    private static PhysicalConstructionPartDeclaration copyPart(
            PhysicalConstructionPartDeclaration part, String tamper) {
        PhysicalConstructionPartDeclaration.Builder builder =
                PhysicalConstructionPartDeclaration.builder(part.getConstructionOwnerKey(),
                    part.getOwnerKey(), "foreign-provider".equals(tamper) ?
                        "foreign-provider" : part.getProviderId(), part.getProviderVersion(),
                    part.getComponentId(),
                    part.getPhysicalPackage(), part.getPublicType(), part.getDesignator(),
                    part.getSpecification(), part.getNameplate(), part.getBackingOwnerKey(),
                    part.getBackingElementId(), part.getPolicy());
        if (part.hasSecondary()) {
            String secondaryOwner = part.getSecondaryOwnerKey();
            if ("foreign-secondary".equals(tamper))
                secondaryOwner = "load";
            builder.secondary(secondaryOwner, part.getSecondaryElementId());
        }
        if (part.hasAttachments()) {
            String firstOwner = part.getFirstAttachmentOwnerKey();
            String firstId = part.getFirstAttachmentElementId();
            if ("foreign-attachment".equals(tamper))
                firstId = "LOAD_FIRST_ATTACHMENT";
            builder.attachments(firstOwner, firstId, part.getSecondAttachmentOwnerKey(),
                    part.getSecondAttachmentElementId());
        }
        if (part.hasFault()) {
            String faultOwner = part.getFaultOwnerKey();
            String faultId = part.getFaultElementId();
            if ("foreign-fault".equals(tamper)) {
                faultOwner = "load";
                faultId = "RLOAD_FAULT_SWITCH";
            }
            builder.fault(part.getFaultKind(), part.getFaultLocalId(),
                    part.getFaultEffectiveOhms(), part.getFaultFamilyId(), faultOwner,
                    faultId, part.getRepairOwnerKey(), part.getRepairLocalComponentId(),
                    part.getRepairComponentId());
        }
        builder.countInMappedIdentity(part.isCountedInMappedIdentity());
        for (int index = 0; index < part.getTerminals().size(); index++) {
            PhysicalConstructionTerminalDeclaration terminal = part.getTerminals().get(index);
            String owner = terminal.getOwnerKey();
            String net = terminal.getNetId();
            if (index == 0 && "swapped-pad".equals(tamper))
                net = part.getTerminals().get(1).getNetId();
            if (index == 0 && "swapped-owner".equals(tamper))
                owner = "load";
            builder.terminal(new PhysicalConstructionTerminalDeclaration(
                    terminal.getPadId(), terminal.getTerminalId(),
                    terminal.getPackageTerminalId(), net, terminal.getEndpointId(),
                    terminal.getManifestKey(), terminal.getManifestBlockKey(), owner,
                    terminal.getLocalId(), terminal.getComponentId()));
        }
        return builder.build();
    }

    private static PhysicalConstructionPartDeclaration.Builder baseBuilder(String componentId,
            PhysicalConstructionPartDeclaration.PartPolicy policy) {
        return PhysicalConstructionPartDeclaration.builder("canary", "canary", "canary-provider",
                1, componentId, PhysicalPackages.AXIAL_RESISTOR, "RESISTOR",
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

    private static boolean isSorted(List<String> values) {
        for (int index = 1; index < values.size(); index++)
            if (values.get(index - 1).compareTo(values.get(index)) > 0)
                return false;
        return true;
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

    private static void expectBoundaryReject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        } catch (IllegalStateException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected physical boundary rejection: " + message);
    }
}
