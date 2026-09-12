package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Native contract for the provider-owned physical declaration boundary.
 *
 * <p>The controlled fixture has two independently addressed channels, two
 * low-side primitive families with three providers, and one fixed supply indicator. The
 * oracle follows the resolved electrical spec and the provider declarations;
 * it does not duplicate a singleton board shape.</p>
 */
public final class A04PhysicalDeclarationContractTest {
    private static int assertions;

    private A04PhysicalDeclarationContractTest() { }

    public static void main(String[] args) {
        try {
            resistiveCorpus();
            controlledCorpus();
            declarationCanaries();
            verifierDeclarationCanary();
            provenanceCanary();
            physicalBoundaryCanary();
            System.out.println("PASS: A04PhysicalDeclarationContractTest assertions="
                    + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: A04PhysicalDeclarationContractTest after "
                    + assertions + " assertions: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void resistiveCorpus() {
        boolean sourceDecision = false;
        boolean loadDecision = false;
        for (long seed = 0; seed < 128; seed++) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forCanary(seed));
            assertPhysical(plan);
            sourceDecision |= ResistiveBlockContributions.SOURCE_FAULT_DECISION_KEY
                    .equals(plan.getFaultDecisionKey());
            loadDecision |= ResistiveBlockContributions.LOAD_FAULT_DECISION_KEY
                    .equals(plan.getFaultDecisionKey());
            if (sourceDecision && loadDecision) break;
        }
        check(sourceDecision && loadDecision,
                "resistive corpus did not exercise both fault decisions");
    }

    private static void controlledCorpus() {
        long[] seeds = { -1L, 0L, 1L, 2L, 49L, Long.MAX_VALUE };
        boolean sawNmos = false;
        boolean sawNpn = false;
        boolean sawAlternate = false;
        for (long seed : seeds) {
            BoundedAssemblyRequest request =
                    BoundedAssemblyRequest.forControlledIndicator(seed);
            BoundedAssemblyPlan normal = BoundedAssemblyPlan.resolve(request);
            assertPhysical(normal);
            for (ControlledIndicatorChannel channel : normal.getChannels()) {
                String type = normal.getBlocks().get(channel.getDriverKey())
                        .getProviderTypeId();
                sawNmos |= ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(type);
                sawNpn |= ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(type);
                sawAlternate |= "nmos-low-side-driver-alt".equals(type);
            }
            for (String target : normal.getDecisionOwners().values())
                assertPhysical(BoundedAssemblyPlan.resolveForDiagnosticFault(request, target));
        }
        check(sawNmos && sawNpn && sawAlternate,
                "controlled physical corpus did not cover all three low-side providers");
    }

    private static void assertPhysical(BoundedAssemblyPlan plan) {
        PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(plan);
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        PhysicalConstructionDeclarations declarations = metadata.getDeclarations();
        check(metadata.getPlan() == plan && metadata.getSpec() == spec,
                "physical metadata lost exact plan/spec identity");
        check(metadata.getBoard().getId().equals(declarations.getBoardFamilyId()) &&
                metadata.getSpecifications() != null,
                "physical board envelope changed");
        check(declarations.getBoardParts().size() == spec.getPackageMap().getPackageCount() &&
                declarations.getExternalInputs().size() == spec.getPowerInputs().size(),
                "physical declaration inventory changed");
        check(new HashSet<String>(componentIds(declarations.getBoardParts())).equals(
                spec.getPackageMap().getPackages().keySet()),
                "physical declarations do not cover the package map");
        check(new HashSet<String>(metadata.getBoard().getNetIds()).equals(
                spec.getExpectedNetIds()), "board net envelope changed");
        check(new HashSet<String>(metadata.getBoard().getPadIds()).equals(
                spec.getPadBindings().keySet()), "board pad envelope changed");

        Set<String> actualFaults = new HashSet<String>();
        Set<String> terminalIds = new HashSet<String>();
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts()) {
            String componentId = part.getComponentId();
            PhysicalPackage expectedPackage = spec.getPackageMap().getPackages().get(componentId);
            check(expectedPackage != null && expectedPackage.isEquivalentTo(
                    part.getPhysicalPackage()), "physical package changed: " + componentId);
            String expectedOwner = spec.getPackageMap().getPackageOwners().get(componentId);
            check(expectedOwner != null && expectedOwner.equals(part.getOwnerKey()),
                    "physical package owner changed: " + componentId);
            ElectricalRealizationSpec.ProviderDeclaration provider =
                    spec.getProviderDeclaration(part.getConstructionOwnerKey());
            check(provider != null && provider.getProviderId().equals(part.getProviderId()) &&
                    provider.getProviderVersion() == part.getProviderVersion(),
                    "physical provider identity changed: " + componentId);
            check(!partsFor(spec, componentId).isEmpty(),
                    "physical package has no electrical unit: " + componentId);
            assertTerminals(spec, part, terminalIds);
            assertBacking(spec, part);
            if (part.hasFault()) {
                actualFaults.add(componentId);
                check(part.isMutableResistor() && part.getRepairComponentId() != null,
                        "faulted physical part lost repair ownership: " + componentId);
            }
        }
        check(actualFaults.equals(new HashSet<String>(plan.getDecisionOwners().values())),
                "physical fault declaration set changed");
        check(terminalIds.equals(new HashSet<String>(spec.getPadBindings().keySet())),
                "physical declarations do not cover each board pad exactly once");
        assertInputs(spec, declarations.getExternalInputs());
        PhysicalConstructionMaterializer.validateDeclarations(plan, spec, declarations);
        check(true, "valid provider-owned physical declarations were rejected");
    }

    private static void assertTerminals(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part, Set<String> pads) {
        Set<String> expectedPackageTerminals = new TreeSet<String>();
        for (ElectricalUnitPackageMap.Unit unit : partsFor(spec, part.getComponentId()))
            expectedPackageTerminals.addAll(unit.getPackageTerminalByUnitTerminal().values());
        Set<String> actualPackageTerminals = new TreeSet<String>();
        check(expectedPackageTerminals.equals(new TreeSet<String>(
                part.getPhysicalPackage().getTerminalIds())),
                "physical package terminal inventory changed: " + part.getComponentId());
        for (PhysicalConstructionTerminalDeclaration terminal : part.getTerminals()) {
            ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
                    terminal.getOwnerKey(), terminal.getLocalId(), terminal.getTerminalId());
            ElectricalRealizationSpec.PadBindingSpec pad = spec.getPadBinding(
                    terminal.getPadId());
            check(terminal.hasElectricalProvenance() && mapping != null && pad != null &&
                    terminal.getComponentId().equals(part.getComponentId()) &&
                    terminal.getPadId().equals(pad.getPadId()) &&
                    terminal.getNetId().equals(mapping.getNetId()) &&
                    terminal.getNetId().equals(pad.getNetId()) &&
                    terminal.getPackageTerminalId().equals(mapping.getPackageTerminalId()) &&
                    terminal.getPackageTerminalId().equals(pad.getTerminalId()) &&
                    terminal.getOwnerKey().equals(pad.getOwnerKey()) &&
                    terminal.getLocalId().equals(pad.getLocalId()) &&
                    terminal.getTerminalId().equals(pad.getTerminalId()) &&
                    pads.add(terminal.getPadId()) &&
                    actualPackageTerminals.add(terminal.getPackageTerminalId()),
                    "physical terminal correspondence changed: " + part.getComponentId());
        }
        check(expectedPackageTerminals.equals(actualPackageTerminals),
                "physical terminal set changed: " + part.getComponentId());
    }

    private static void assertBacking(ElectricalRealizationSpec spec,
            PhysicalConstructionPartDeclaration part) {
        ElectricalRealizationSpec.ElementDeclaration primary = spec.getElementDeclaration(
                part.getBackingOwnerKey(), part.getBackingElementId());
        check(primary != null && part.getComponentId().equals(primary.getComponentId()),
                "physical backing lost electrical identity: " + part.getComponentId());
        String expectedKind = null;
        if ("RESISTOR".equals(part.getPublicType())) expectedKind = "RESISTOR";
        else if ("NMOS_TRANSISTOR".equals(part.getPublicType())) expectedKind = "NMOS";
        else if ("NPN_TRANSISTOR".equals(part.getPublicType())) expectedKind = "NPN";
        else if ("LED".equals(part.getPublicType())) expectedKind = "LED";
        else if ("CONNECTOR".equals(part.getPublicType()))
            check("VOLTAGE".equals(primary.getKind()) || "SWITCH".equals(primary.getKind()),
                    "connector physical backing changed: " + part.getComponentId());
        else check(false, "unknown physical public type: " + part.getPublicType());
        if (expectedKind != null)
            check(expectedKind.equals(primary.getKind()),
                    "physical primitive backing changed: " + part.getComponentId());
    }

    private static void assertInputs(ElectricalRealizationSpec spec,
            List<PhysicalExternalInputDeclaration> inputs) {
        Set<String> actual = new HashSet<String>();
        for (PhysicalExternalInputDeclaration input : inputs) {
            ElectricalRealizationSpec.PowerInputSpec expected =
                    spec.getPowerInputs().get(input.getInputId());
            check(expected != null && actual.add(input.getInputId()) &&
                    "device".equals(input.getProviderOwnerKey()) &&
                    input.getNominalVoltage() == ElectricalRealizationSpec.EXTERNAL_SUPPLY_VOLTS &&
                    expected.getPositivePadId().equals(input.getPositivePadId()) &&
                    expected.getReturnPadId().equals(input.getReturnPadId()) &&
                    expected.getPositiveNetId().equals(input.getPositiveNetId()) &&
                    expected.getReturnNetId().equals(input.getReturnNetId()),
                    "physical external input changed: " + input.getInputId());
        }
        check(actual.equals(spec.getPowerInputs().keySet()),
                "physical external input inventory changed");
    }

    private static void declarationCanaries() {
        final PhysicalConstructionPartDeclaration.Builder fixed = baseBuilder(
                "canary/fixed", PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        fixed.terminal(terminal("canary/fixed.1", "1", "1", "NET_1"));
        fixed.terminal(terminal("canary/fixed.2", "2", "2", "NET_2"));
        expectIllegal(new Action() { public void run() {
            fixed.fault(ComposedBlockContribution.FaultSpec.Kind.OPEN, "fault", 10.0,
                    "family", "canary", "fault-switch", "canary", "R", "canary/fixed").build();
        }}, "fixed physical part accepted a fault");

        final PhysicalConstructionPartDeclaration.Builder duplicate = baseBuilder(
                "canary/duplicate", PhysicalConstructionPartDeclaration.PartPolicy.FIXED);
        duplicate.terminal(terminal("canary/duplicate.1", "1", "1", "NET_1"));
        duplicate.terminal(terminal("canary/duplicate.2", "1", "2", "NET_2"));
        expectIllegal(new Action() { public void run() { duplicate.build(); } },
                "duplicate physical terminal accepted");

        final PhysicalConstructionPartDeclaration.Builder mutable = baseBuilder(
                "canary/mutable", PhysicalConstructionPartDeclaration.PartPolicy.MUTABLE_RESISTOR);
        mutable.terminal(terminal("canary/mutable.1", "1", "1", "NET_1"));
        mutable.terminal(terminal("canary/mutable.2", "2", "2", "NET_2"));
        mutable.secondary("canary", "R_SECONDARY")
            .attachments("canary", "R_FIRST_ATTACHMENT", "canary", "R_SECOND_ATTACHMENT");
        expectIllegal(new Action() { public void run() {
            mutable.fault(ComposedBlockContribution.FaultSpec.Kind.OPEN, "fault", 0.0,
                "family", null, null, "canary", "R", "canary/mutable").build();
        }}, "open fault without a backing switch owner accepted");
    }

    private static void verifierDeclarationCanary() {
        final int[] distinctNodes = new int[] { 10, 11, 12, 13, 14, 0 };
        A04ConstructionDeveloperVerifier.verifyDistinctChannelNodes(distinctNodes, "native-positive");
        check(true, "distinct solver nodes remain accepted");
        for (int i = 1; i < distinctNodes.length; i++) {
            for (int j = 0; j < i; j++) {
                final int[] shortedNodes = distinctNodes.clone();
                shortedNodes[i] = shortedNodes[j];
                expectVerifierReject(new Action() { public void run() {
                    A04ConstructionDeveloperVerifier.verifyDistinctChannelNodes(shortedNodes, "native-short");
                }}, "solver-node oracle accepted a pairwise channel short");
            }
        }
        final BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(3L));
        final ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            final String driverOwner = channel.getDriverKey();
            final String loadOwner = channel.getLoadKey();
            A04ConstructionDeveloperVerifier.verifyDeclaredElements(spec, driverOwner,
                    spec.getProviderDeclaration(driverOwner));
            A04ConstructionDeveloperVerifier.verifyDeclaredElements(spec, loadOwner,
                    spec.getProviderDeclaration(loadOwner));
            check(!spec.hasPad(loadOwner, "RETURN") &&
                    plan.netForPort(loadOwner, "RETURN").equals(
                        plan.netForPort(driverOwner, "RETURN")),
                    "controlled load RETURN must remain a logical shared-net reference");
            A04ConstructionDeveloperVerifier.verifyControlledReturnAlias(plan, channel);

            final ElectricalRealizationSpec.ProviderDeclaration driver =
                    spec.getProviderDeclaration(driverOwner);
            String control = plan.getBlocks().get(driverOwner).getResistors().containsKey("RG")
                    ? "RG" : "RB";
            check(driver.getElement(control + "_FAULT_SWITCH").getComponentId().equals(
                    spec.getComponentId(driverOwner, control)) &&
                    driver.getElement("CONTROL_NODE_TRACE").getComponentId() == null,
                    "provider helpers lost participant/null physical associations");

            A04ConstructionDeveloperVerifier.verifyElementShapeAssociation(spec, driverOwner,
                    driver, driver.getElement("CONTROL_NODE_TRACE"));
            final String helperId = "CONTROL_NODE_TRACE";
            final String helperLocalComponentId = driverOwner + "/helper/" + helperId;
            final Map<String, ElectricalRealizationSpec.ElementDeclaration> badElements =
                    new HashMap<String, ElectricalRealizationSpec.ElementDeclaration>(
                            driver.getElements());
            badElements.put(helperId, new ElectricalRealizationSpec.ElementDeclaration(
                    driverOwner, helperId, "WIRE", helperLocalComponentId,
                    ElectricalRealizationSpec.posts("1", 0, "2", 1)));
            final ElectricalRealizationSpec.ProviderDeclaration badAssociation =
                    new ElectricalRealizationSpec.ProviderDeclaration(driverOwner,
                            driver.getProviderId(), driver.getProviderVersion(),
                            driver.getContribution(), driver.getElementIds(),
                            driver.getUnitIds(), driver.getTerminalIds(), driver.getJoinIds(),
                            driver.isDeviceOwner(), badElements);
            expectVerifierReject(new Action() { public void run() {
                A04ConstructionDeveloperVerifier.verifyDeclaredElements(spec, driverOwner,
                        badAssociation);
            }}, "verifier accepted a helper-local component association");

            // Exercise the association predicate itself, after the separate identity gate.
            expectVerifierReject(new Action() { public void run() {
                A04ConstructionDeveloperVerifier.verifyElementShapeAssociation(spec, driverOwner,
                        driver, badElements.get(helperId));
            }}, "association predicate accepted a helper-local nonexistent component");
            // ElementDeclaration rejects malformed primitive maps during construction.
            // Call the independent verifier predicate directly to exercise its own oracle.
            expectVerifierReject(new Action() { public void run() {
                A04ConstructionDeveloperVerifier.verifyPrimitivePostShape("WIRE",
                        ElectricalRealizationSpec.posts("1", 1, "2", 0));
            }}, "shape predicate accepted swapped primitive posts");
            expectVerifierReject(new Action() { public void run() {
                A04ConstructionDeveloperVerifier.verifyPrimitivePostShape("UNSUPPORTED_ORACLE_KIND",
                        ElectricalRealizationSpec.posts("1", 0, "2", 1));
            }}, "shape predicate accepted an unknown primitive kind");

            ArrayList<String> missingElement = new ArrayList<String>(driver.getElementIds());
            missingElement.remove(helperId);
            final ElectricalRealizationSpec.ProviderDeclaration badMembership =
                    new ElectricalRealizationSpec.ProviderDeclaration(driverOwner,
                            driver.getProviderId(), driver.getProviderVersion(),
                            driver.getContribution(), missingElement, driver.getUnitIds(),
                            driver.getTerminalIds(), driver.getJoinIds(), driver.isDeviceOwner(),
                            driver.getElements());
            expectVerifierReject(new Action() { public void run() {
                A04ConstructionDeveloperVerifier.verifyDeclaredElements(spec, driverOwner,
                        badMembership);
            }}, "verifier accepted provider/spec element membership drift");
        }
        A04ConstructionDeveloperVerifier.verifyDeclaredElements(spec,
                plan.getSupportBlockKey(), spec.getProviderDeclaration(plan.getSupportBlockKey()));
    }

    private static void provenanceCanary() {
        final BoundedAssemblyPlan first = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forCanary(17L));
        final BoundedAssemblyPlan second = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(19L));
        final PhysicalConstructionMetadata metadata = PhysicalConstructionMaterializer.describe(first);
        check(metadata.getPlan() == first && metadata.getSpec() == first.getElectricalRealizationSpec(),
                "physical metadata did not retain exact construction provenance");
        expectIllegal(new Action() { public void run() {
            new PhysicalConstructionMetadata(first, second.getElectricalRealizationSpec(),
                    metadata.getBoard(), metadata.getSpecifications(), metadata.getDeclarations());
        }}, "metadata accepted a foreign electrical spec");
    }

    private static void physicalBoundaryCanary() {
        verifyPhysicalMisuse(BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forCanary(23L)));
        verifyPhysicalMisuse(BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(-1L)));
        verifyPhysicalMisuse(BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(0L)));
        sharedPackageControl();
    }

    private static void verifyPhysicalMisuse(final BoundedAssemblyPlan plan) {
        final ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        final PhysicalConstructionDeclarations declarations =
            PhysicalConstructionMaterializer.describe(plan).getDeclarations();
        PhysicalConstructionMaterializer.validateDeclarations(plan, spec, declarations);
        check(true, "valid explicit device bridges rejected");
        ArrayList<PhysicalConstructionPartDeclaration> mutable = new ArrayList<PhysicalConstructionPartDeclaration>();
        for (PhysicalConstructionPartDeclaration part : declarations.getBoardParts())
            if (part.isMutableResistor()) mutable.add(part);
        check(mutable.size() >= 2, "foreign-part control has fewer than two actual mutable owners");
        final PhysicalConstructionPartDeclaration target = mutable.get(0);
        final PhysicalConstructionPartDeclaration foreign = mutable.get(mutable.size()-1);
        check(!target.getOwnerKey().equals(foreign.getOwnerKey()), "foreign fixture reused the target owner");
        for (final String mode : new String[] { "swapped-pad", "swapped-owner", "foreign-provider",
                "foreign-primary", "foreign-secondary", "foreign-attachment", "missing-helper" }) {
            expectBoundaryReject(new Action() { public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(plan, spec,
                    replacePart(declarations, copyPart(target, foreign, mode)));
            }}, "physical boundary accepted " + mode);
        }
        if (target.hasFault() && foreign.hasFault() && target.getFaultElementId() != null &&
                foreign.getFaultElementId() != null) {
            expectBoundaryReject(new Action() { public void run() {
                PhysicalConstructionMaterializer.validateDeclarations(plan, spec,
                    replacePart(declarations, copyPart(target, foreign, "foreign-fault")));
            }}, "physical boundary accepted a same-kind foreign fault switch");
        }
    }

    private static PhysicalConstructionDeclarations replacePart(
            PhysicalConstructionDeclarations declarations,
            PhysicalConstructionPartDeclaration replacement) {
        ArrayList<PhysicalConstructionPartDeclaration> local =
                new ArrayList<PhysicalConstructionPartDeclaration>(declarations.getLocalParts());
        ArrayList<PhysicalConstructionPartDeclaration> device =
                new ArrayList<PhysicalConstructionPartDeclaration>(declarations.getDeviceParts());
        if (!replace(local, replacement) && !replace(device, replacement))
            throw new AssertionError("physical replacement part was not found");
        return new PhysicalConstructionDeclarations(local, device,
                declarations.getExternalInputs(), declarations.getBoardFamilyId(),
                declarations.getBoardName());
    }

    private static boolean replace(List<PhysicalConstructionPartDeclaration> parts,
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
            PhysicalConstructionPartDeclaration part,
            PhysicalConstructionPartDeclaration foreign, String mode) {
        PhysicalConstructionPartDeclaration.Builder builder =
            PhysicalConstructionPartDeclaration.builder(part.getConstructionOwnerKey(),
                part.getOwnerKey(), "foreign-provider".equals(mode) ? "foreign-provider" : part.getProviderId(),
                part.getProviderVersion(), part.getComponentId(), part.getPhysicalPackage(),
                part.getPublicType(), part.getDesignator(), part.getSpecification(), part.getNameplate(),
                "foreign-primary".equals(mode) ? foreign.getBackingOwnerKey() : part.getBackingOwnerKey(),
                "foreign-primary".equals(mode) ? foreign.getBackingElementId() : part.getBackingElementId(),
                part.getPolicy());
        if (part.hasSecondary())
            builder.secondary("foreign-secondary".equals(mode) ? foreign.getSecondaryOwnerKey() : part.getSecondaryOwnerKey(),
                "foreign-secondary".equals(mode) ? foreign.getSecondaryElementId() : part.getSecondaryElementId());
        if (part.hasAttachments())
            builder.attachments("foreign-attachment".equals(mode) ? foreign.getFirstAttachmentOwnerKey() : part.getFirstAttachmentOwnerKey(),
                "foreign-attachment".equals(mode) ? foreign.getFirstAttachmentElementId() :
                    "missing-helper".equals(mode) ? "UNDECLARED_ATTACHMENT" : part.getFirstAttachmentElementId(),
                part.getSecondAttachmentOwnerKey(), part.getSecondAttachmentElementId());
        if (part.hasFault())
            builder.fault(part.getFaultKind(), part.getFaultLocalId(), part.getFaultEffectiveOhms(),
                part.getFaultFamilyId(), "foreign-fault".equals(mode) ? foreign.getFaultOwnerKey() : part.getFaultOwnerKey(),
                "foreign-fault".equals(mode) ? foreign.getFaultElementId() : part.getFaultElementId(),
                part.getRepairOwnerKey(), part.getRepairLocalComponentId(), part.getRepairComponentId());
        builder.countInMappedIdentity(part.isCountedInMappedIdentity());
        for (int index=0; index<part.getTerminals().size(); index++) {
            PhysicalConstructionTerminalDeclaration terminal = part.getTerminals().get(index);
            String net = index == 0 && "swapped-pad".equals(mode) ?
                part.getTerminals().get(1).getNetId() : terminal.getNetId();
            String owner = index == 0 && "swapped-owner".equals(mode) ?
                foreign.getOwnerKey() : terminal.getOwnerKey();
            builder.terminal(new PhysicalConstructionTerminalDeclaration(terminal.getPadId(),
                terminal.getTerminalId(), terminal.getPackageTerminalId(), net, terminal.getEndpointId(),
                terminal.getManifestKey(), terminal.getManifestBlockKey(), owner, terminal.getLocalId(), terminal.getComponentId()));
        }
        return builder.build();
    }

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

    private static List<ElectricalUnitPackageMap.Unit> partsFor(
            ElectricalRealizationSpec spec, String componentId) {
        ArrayList<ElectricalUnitPackageMap.Unit> result =
                new ArrayList<ElectricalUnitPackageMap.Unit>();
        for (ElectricalUnitPackageMap.Unit unit : spec.getPackageMap().getUnits().values())
            if (componentId.equals(unit.getComponentId())) result.add(unit);
        return result;
    }

    private static List<String> componentIds(
            List<PhysicalConstructionPartDeclaration> parts) {
        ArrayList<String> result = new ArrayList<String>();
        for (PhysicalConstructionPartDeclaration part : parts) result.add(part.getComponentId());
        return result;
    }

    private static PhysicalConstructionPartDeclaration.Builder baseBuilder(String componentId,
            PhysicalConstructionPartDeclaration.PartPolicy policy) {
        return PhysicalConstructionPartDeclaration.builder("canary", "canary", "canary",
                1, componentId, PhysicalPackages.AXIAL_RESISTOR, "RESISTOR", "R",
                new ResistorNameplate(componentId, 100.0, 5.0),
                new PhysicalNameplate(componentId, "Canary resistor"), "canary", "R", policy);
    }

    private static PhysicalConstructionTerminalDeclaration terminal(String padId,
            String terminalId, String packageTerminalId, String netId) {
        return new PhysicalConstructionTerminalDeclaration(padId, terminalId,
                packageTerminalId, netId, padId, "canary/R_" + terminalId, "canary");
    }

    private interface Action { void run(); }

    private static void expectIllegal(Action action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError("Expected IllegalArgumentException: " + label);
    }

    private static void expectBoundaryReject(Action action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Expected physical boundary rejection: " + label);
    }

    private static void expectVerifierReject(Action action, String label) {
        try { action.run(); }
        catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError("Expected verifier rejection: " + label);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
