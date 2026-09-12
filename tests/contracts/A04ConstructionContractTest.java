package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Pure conformance for the current provider-owned construction boundary.
 *
 * <p>The controlled fixture now contains two stable channel instances and a
 * fixed supply indicator.  This oracle follows those declared identities and
 * provider declarations instead of asking a plan for a singleton driver or
 * load.</p>
 */
public final class A04ConstructionContractTest {
    private static int assertions;
    private static final long[] SEEDS = { -1L, 0L, 1L, 2L, 9L,
            Long.MIN_VALUE, Long.MAX_VALUE };

    private A04ConstructionContractTest() { }

    public static void main(String[] args) {
        try {
            immutableDeclarationCanaries();
            persistentBoardEndpointCanaries();
            supportedProviderRegistry();
            resistivePlanContract();
            controlledPlanContract();
            reorderedAndRepeatedIdentity();
            System.out.println("PASS: A04ConstructionContractTest assertions="
                    + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.err.println("FAIL: A04ConstructionContractTest after "
                    + assertions + " assertions: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void persistentBoardEndpointCanaries() {
        expectIllegal(new Action() {
            @Override public void run() {
                new ElectricalRealizationSpec.BoardEndpointSpec("owner", "R1.1",
                        new ElectricalRealizationSpec.EndpointRef("owner", "ATTACHMENT", "1"),
                        "ATTACHMENT");
            }
        }, "detachable attachment used as persistent board contact");
        for (long seed : SEEDS) {
            ElectricalRealizationSpec spec = BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forControlledIndicator(seed)).getElectricalRealizationSpec();
            int detachableCount = 0;
            for (Map.Entry<String, ElectricalRealizationSpec.BoardEndpointSpec> entry :
                    spec.getBoardEndpoints().entrySet()) {
                ElectricalRealizationSpec.BoardEndpointSpec pad = entry.getValue();
                if (pad.getAttachmentElementId() == null) continue;
                detachableCount++;
                ElectricalRealizationSpec.EndpointRef endpoint = pad.getEndpoint();
                ElectricalRealizationSpec.ElementDeclaration backing = spec.getElementDeclaration(
                        endpoint.getOwnerKey(), endpoint.getElementId());
                check(!pad.getAttachmentElementId().equals(endpoint.getElementId()) &&
                        "WIRE".equals(backing.getKind()) && backing.getComponentId() == null,
                        "removable resistor contact must remain on persistent board copper");
            }
            check(detachableCount == 8, "both terminals of all four repair owners are persistent");
        }
    }

    private static void immutableDeclarationCanaries() {
        final List<String> elements = new ArrayList<String>(Arrays.asList("z", "a"));
        final List<String> units = new ArrayList<String>(Arrays.asList("unit-b", "unit-a"));
        final List<String> terminals = new ArrayList<String>(Arrays.asList("2", "1"));
        final List<String> joins = new ArrayList<String>(Arrays.asList("join-b", "join-a"));
        final Map<String, Integer> posts = new HashMap<String, Integer>();
        posts.put("G", Integer.valueOf(0));
        posts.put("S", Integer.valueOf(1));
        posts.put("D", Integer.valueOf(2));
        final Map<String, Double> parameters = new HashMap<String, Double>();
        parameters.put("threshold", Double.valueOf(1.5));
        parameters.put("beta", Double.valueOf(10.0));
        final ElectricalRealizationSpec.ElementDeclaration element =
                new ElectricalRealizationSpec.ElementDeclaration("owner", "Q1",
                        "NMOS", "component/Q1", posts, "NMOS", parameters);
        final Map<String, ElectricalRealizationSpec.ElementDeclaration> allElements =
                new HashMap<String, ElectricalRealizationSpec.ElementDeclaration>();
        allElements.put("Q1", element);
        final ElectricalRealizationSpec.ProviderDeclaration declaration =
                new ElectricalRealizationSpec.ProviderDeclaration("owner", "provider", 1,
                        null, elements, units, terminals, joins, false, allElements);
        elements.clear();
        units.clear();
        terminals.clear();
        joins.clear();
        posts.clear();
        parameters.clear();
        check(declaration.getElementIds().equals(Arrays.asList("a", "z")),
                "provider element IDs were not copied and sorted");
        check(declaration.getUnitIds().equals(Arrays.asList("unit-a", "unit-b")),
                "provider unit IDs were not copied and sorted");
        check(declaration.getTerminalIds().equals(Arrays.asList("1", "2")),
                "provider terminal IDs were not copied and sorted");
        check(declaration.getElement("Q1").getPostIndex("G") == 0 &&
                declaration.getElement("Q1").getPostIndex("S") == 1 &&
                declaration.getElement("Q1").getPostIndex("D") == 2,
                "provider declaration lost primitive post choices");
        expectUnsupported(new Action() {
            @Override public void run() { declaration.getElementIds().clear(); }
        }, "provider IDs are mutable");
        expectUnsupported(new Action() {
            @Override public void run() { declaration.getElements().clear(); }
        }, "provider element map is mutable");
        expectUnsupported(new Action() {
            @Override public void run() { declaration.getElement("Q1").getParameters().clear(); }
        }, "element parameter map is mutable");

        final Map<String, String> packageTerminals = new HashMap<String, String>();
        packageTerminals.put("VCC", "VCC");
        packageTerminals.put("GND", "GND");
        final ElectricalRealizationSpec.PhysicalUnitSpec unit =
                new ElectricalRealizationSpec.PhysicalUnitSpec("owner", "unit",
                        "component/U1", "package/U1", packageTerminals);
        packageTerminals.clear();
        check(unit.getPackageTerminalByUnitTerminal().size() == 2,
                "physical unit did not retain its copied terminal map");
        expectUnsupported(new Action() {
            @Override public void run() {
                unit.getPackageTerminalByUnitTerminal().put("X", "X");
            }
        }, "physical unit terminal map is mutable");

        final List<String> joinRefs = new ArrayList<String>(Arrays.asList(
                "load/LED1.A", "driver/Q1.D"));
        final ElectricalRealizationSpec.DeviceJoinSpec join =
                new ElectricalRealizationSpec.DeviceJoinSpec("led-drain", joinRefs, "SIGNAL");
        joinRefs.clear();
        check(join.getTerminalRefs().equals(Arrays.asList("driver/Q1.D", "load/LED1.A")),
                "device join did not canonicalize endpoint references");
        expectUnsupported(new Action() {
            @Override public void run() { join.getTerminalRefs().clear(); }
        }, "device join endpoint list is mutable");

        expectIllegal(new Action() {
            @Override public void run() {
                new ElectricalRealizationSpec.ElementDeclaration("owner", "Q1",
                        "NMOS", "component/Q1", posts("G", 0, "D", 1, "S", 2),
                        "NMOS", numbers("threshold", 1.5, "beta", 10.0));
            }
        }, "NMOS with swapped D/S posts");
        expectIllegal(new Action() {
            @Override public void run() {
                new ElectricalRealizationSpec.ElementDeclaration("owner", "X",
                        "UNKNOWN", "component/X", posts("1", 0), null,
                        Collections.<String, Double>emptyMap());
            }
        }, "unknown primitive kind");
        expectIllegal(new Action() {
            @Override public void run() {
                new ElectricalRealizationSpec.ProviderDeclaration("owner", "provider", 0,
                        null, Arrays.asList("x"), Arrays.asList("u"), Arrays.asList("t"),
                        Collections.<String>emptyList(), false);
            }
        }, "provider version zero");
    }

    private static void supportedProviderRegistry() {
        ElectricalConstructionProvider[] providers = {
            StandardElectricalConstructionProviders.resistiveSource(),
            StandardElectricalConstructionProviders.resistiveLoad(),
            StandardElectricalConstructionProviders.controlledDriver(),
            StandardElectricalConstructionProviders.controlledNpnDriver(),
            StandardElectricalConstructionProviders.controlledLoad(),
            StandardElectricalConstructionProviders.supplyPresent()
        };
        Set<String> ids = new HashSet<String>();
        for (ElectricalConstructionProvider provider : providers) {
            check(provider != null && ids.add(provider.getProviderId()),
                    "standard provider registry has a missing or duplicate provider");
            check(StandardElectricalConstructionProviders.provider(provider.getProviderId(),
                    provider.getVersion()) == provider,
                    "provider registry lookup changed identity for " + provider.getProviderId());
        }
        check(ids.contains(ControlledIndicatorBlockContributions.DRIVER_TYPE_ID) &&
                ids.contains(ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID) &&
                ids.contains(SupplyPresentBlockContributions.TYPE_ID),
                "current role and support providers are not registered");
        expectIllegal(new Action() {
            @Override public void run() {
                StandardElectricalConstructionProviders.provider("unknown-provider", 1);
            }
        }, "unknown provider lookup");
        expectIllegal(new Action() {
            @Override public void run() {
                StandardElectricalConstructionProviders.provider(
                        ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID, 99);
            }
        }, "unsupported provider version lookup");

        List<LowSideRoleFamily.Provider> canonical = LowSideRoleFamily.providers();
        ArrayList<LowSideRoleFamily.Provider> reversed =
                new ArrayList<LowSideRoleFamily.Provider>(canonical);
        Collections.reverse(reversed);
        check(canonicalIds(canonical).equals(canonicalIds(
                LowSideRoleFamily.canonicalProviders(reversed))),
                "low-side registry order is not canonicalized");
        for (long seed : SEEDS) {
            for (String key : new String[] { "channel-a-driver", "channel-b-driver" }) {
                check(LowSideRoleFamily.select(seed, key, LowSideRoleFamily.Envelope.standard(),
                        canonical).getTypeId().equals(LowSideRoleFamily.select(seed, key,
                        LowSideRoleFamily.Envelope.standard(), reversed).getTypeId()),
                        "provider selection changed with registration order");
            }
        }
    }

    private static void resistivePlanContract() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forCanary(1L));
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        check(!plan.isControlledIndicator() && plan.getChannels().isEmpty(),
                "resistive plan was classified as a controlled plan");
        check(spec != null && spec.getVersion() == ElectricalRealizationSpec.VERSION,
                "resistive plan omitted its current electrical spec");
        check(spec.getPackageMap().getPackageCount() == 3 &&
                spec.getPackageMap().getUnitCount() == 3,
                "resistive package/unit inventory changed");
        provider(spec, "source", ResistiveBlockContributions.SOURCE_TYPE_ID, 1);
        provider(spec, "load", ResistiveBlockContributions.LOAD_TYPE_ID, 1);
        element(spec, "source", "R1", "RESISTOR", "1", 0, "2", 1);
        element(spec, "load", "R1", "RESISTOR", "1", 0, "2", 1);
        mapping(spec, plan, "source", "R1", "1", plan.netFor("source", "SUPPLY"));
        mapping(spec, plan, "source", "R1", "2", plan.netFor("source", "OUT"));
        mapping(spec, plan, "load", "R1", "1", plan.netFor("load", "SUPPLY"));
        mapping(spec, plan, "load", "R1", "2", plan.netFor("load", "RETURN"));
        check(plan.netFor("source", "OUT").equals(plan.netFor("load", "SUPPLY")) &&
                plan.netFor("source", "RETURN").equals(plan.netFor("load", "RETURN")),
                "resistive signal and return are not explicit joins");
        mapCompleteness(spec);
    }

    private static void controlledPlanContract() {
        boolean sawNmosNmos = false;
        boolean sawNpnNpn = false;
        boolean sawMixed = false;
        for (long seed : SEEDS) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forControlledIndicator(seed));
            check(plan.isControlledIndicator() && plan.getChannels().size() == 2,
                    "controlled plan retains both stable channels");
            check(plan.getBlocks().size() == 5 &&
                    BoundedAssemblyRequest.SUPPORT_BLOCK_KEY.equals(plan.getSupportBlockKey()),
                    "controlled plan retains two drivers, two loads and support");
            check(spec(plan).getPackageMap().getPackageCount() == 15 &&
                    spec(plan).getPackageMap().getUnitCount() == 15,
                    "controlled plan declares the expected fifteen physical parts");
            Set<String> expectedKeys = new TreeSet<String>(Arrays.asList(
                    "channel-a-driver", "channel-a-load", "channel-b-driver",
                    "channel-b-load", BoundedAssemblyRequest.SUPPORT_BLOCK_KEY));
            check(new TreeSet<String>(plan.getBlocks().keySet()).equals(expectedKeys),
                    "controlled plan changed stable block identities");

            String firstType = null;
            String secondType = null;
            for (int index = 0; index < plan.getChannels().size(); index++) {
                ControlledIndicatorChannel channel = plan.getChannels().get(index);
                ComposedBlockContribution driver = plan.getBlocks().get(channel.getDriverKey());
                ComposedBlockContribution load = plan.getBlocks().get(channel.getLoadKey());
                check(driver != null && load != null,
                        "channel does not resolve both provider contributions");
                if (index == 0) firstType = driver.getProviderTypeId();
                else secondType = driver.getProviderTypeId();
                check(isDriverType(driver.getProviderTypeId()) &&
                        driver.getRoleId().equals(LowSideRoleFamily.ROLE_ID) &&
                        driver.getProviderVersion() == LowSideRoleFamily.VERSION,
                        "channel driver is not an admitted low-side provider");
                check(load.getProviderTypeId().equals(
                        ControlledIndicatorBlockContributions.LOAD_TYPE_ID) &&
                        load.getResolvedValueRecipe() != null,
                        "channel load does not retain the resolved current value recipe");
                ElectricalRealizationSpec.ProviderDeclaration driverDeclaration =
                        spec(plan).getProviderDeclaration(channel.getDriverKey());
                ElectricalRealizationSpec.ProviderDeclaration loadDeclaration =
                        spec(plan).getProviderDeclaration(channel.getLoadKey());
                check(driverDeclaration != null && loadDeclaration != null &&
                        driverDeclaration.getContribution() == driver &&
                        loadDeclaration.getContribution() == load &&
                        loadDeclaration.getContribution().getResolvedValueRecipe() ==
                            load.getResolvedValueRecipe(),
                        "provider declarations lost per-channel contribution/value identity");
                verifyDriver(plan, channel, driver);
                verifyLoad(plan, channel, load);
                check(plan.netForPort(channel.getLoadKey(), "SUPPLY").equals(
                        plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY, "POWER_OUT")) &&
                        plan.netForPort(channel.getDriverKey(), "CONTROL").equals(
                        plan.netForPort(channel.getControlAdapterKey(), "CONTROL_OUT")) &&
                        plan.netForPort(channel.getDriverKey(), "SWITCHED_SINK").equals(
                        plan.netForPort(channel.getLoadKey(), "SWITCHED_LOAD")) &&
                        plan.netForPort(channel.getDriverKey(), "RETURN").equals(
                        plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY, "RETURN")),
                        "channel supply/control/switch/return joins changed");
                check(!plan.idFor(channel.getDriverKey(), EntityKind.COMPONENT, "Q1").equals(
                        plan.idFor(channel.getLoadKey(), EntityKind.COMPONENT, "RLOAD")),
                        "channel component identities collided");
            }
            check(firstType != null && secondType != null,
                    "controlled plan did not expose two driver provider identities");
            if (isNmos(firstType) && isNmos(secondType)) sawNmosNmos = true;
            if (isNpn(firstType) && isNpn(secondType)) sawNpnNpn = true;
            if (!firstType.equals(secondType)) sawMixed = true;
            ComposedBlockContribution support = plan.getBlocks().get(
                    plan.getSupportBlockKey());
            check(support != null && SupplyPresentBlockContributions.TYPE_ID.equals(
                    support.getProviderTypeId()) && support.getFaultSpec() == null &&
                    support.getResistor(SupplyPresentBlockContributions.RSUP_COMPONENT_ID) != null,
                    "support contribution is fixed and nonfaultable");
            verifyFaultOwners(plan);
            mapCompleteness(spec(plan));
        }
        check(sawNmosNmos && sawNpnNpn && sawMixed,
                "signed seed corpus covers NMOS/NMOS, NPN/NPN and mixed channels");
    }

    private static void reorderedAndRepeatedIdentity() {
        BoundedAssemblyRequest request = BoundedAssemblyRequest.forControlledIndicator(
                Long.MAX_VALUE);
        ArrayList<ElectricalBlockContract> blocks =
                new ArrayList<ElectricalBlockContract>(request.getBlocks());
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(request.getConnections());
        ArrayList<DeviceAdapterContract> adapters =
                new ArrayList<DeviceAdapterContract>(request.getDeviceAdapters());
        Collections.reverse(blocks);
        Collections.reverse(connections);
        Collections.reverse(adapters);
        BoundedAssemblyPlan first = BoundedAssemblyPlan.resolve(request);
        BoundedAssemblyPlan reordered = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.reorderedInputs(request.getDescriptor(), blocks,
                        connections, adapters));
        BoundedAssemblyPlan repeated = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(Long.MAX_VALUE));
        check(first.getSemanticSignature().equals(reordered.getSemanticSignature()) &&
                first.getSemanticSignature().equals(repeated.getSemanticSignature()),
                "reordered inputs changed current construction semantics");
        for (ControlledIndicatorChannel channel : first.getChannels()) {
            String key = channel.getLoadKey();
            check(first.getBlocks().get(key).getResolvedValueRecipe().semanticSignature().equals(
                    reordered.getBlocks().get(key).getResolvedValueRecipe().semanticSignature()) &&
                    first.getBlocks().get(key).getResolvedValueRecipe().semanticSignature().equals(
                    repeated.getBlocks().get(key).getResolvedValueRecipe().semanticSignature()),
                    "repeated channel value selection changed on equivalent input");
        }
        check(!first.idFor(first.getChannels().get(0).getDriverKey(), EntityKind.COMPONENT, "Q1")
                .equals(first.idFor(first.getChannels().get(1).getDriverKey(),
                        EntityKind.COMPONENT, "Q1")),
                "repeated Q1 identities were not owner-qualified");
        check(first.getChannels().get(0).getControlAdapterKey().equals("channel-a-control") &&
                first.getChannels().get(1).getControlAdapterKey().equals("channel-b-control"),
                "stable channel control owners changed");
    }

    private static void verifyDriver(BoundedAssemblyPlan plan,
            ControlledIndicatorChannel channel, ComposedBlockContribution driver) {
        String owner = channel.getDriverKey();
        FunctionalBlockDescriptor.Component q1 = driver.getDescriptor().getComponents().get("Q1");
        check(q1 != null && q1.getTerminalIds().size() == 3,
                "driver Q1 declaration has three terminals");
        ElectricalRealizationSpec.ElementDeclaration element =
                spec(plan).getElementDeclaration(owner, "Q1");
        check(element != null && element.getModelId() != null &&
                element.getParameters().size() >= 1,
                "driver Q1 declaration omitted model or parameters");
        if (isNmos(driver.getProviderTypeId())) {
            check("NMOS".equals(q1.getTypeId()) && q1.getTerminalIds().containsAll(
                    Arrays.asList("G", "D", "S")) && driver.getResistor("RG") != null &&
                    driver.getResistor("RG").isMutable() && driver.getNmosRecipes().size() == 1,
                    "NMOS channel does not declare G/D/S and mutable RG");
            check(element.getPostIndex("G") == 0 && element.getPostIndex("D") == 2 &&
                    element.getPostIndex("S") == 1,
                    "NMOS channel Q1 posts changed from G/D/S 0/2/1");
        } else {
            check("NPN".equals(q1.getTypeId()) && q1.getTerminalIds().containsAll(
                    Arrays.asList("B", "C", "E")) && driver.getResistor("RB") != null &&
                    driver.getResistor("RB").isMutable() && driver.getNmosRecipes().isEmpty(),
                    "NPN channel does not declare B/C/E and mutable RB");
            check(element.getPostIndex("B") == 0 && element.getPostIndex("C") == 1 &&
                    element.getPostIndex("E") == 2,
                    "NPN channel Q1 posts changed from B/C/E 0/1/2");
        }
        check(driver.getFaultSpec() != null &&
                driver.getFaultSpec().getTargetComponentLocalId().equals(
                        isNpn(driver.getProviderTypeId()) ? "RB" : "RG"),
                "driver fault target does not follow the selected role");
    }

    private static void verifyLoad(BoundedAssemblyPlan plan,
            ControlledIndicatorChannel channel, ComposedBlockContribution load) {
        String owner = channel.getLoadKey();
        check(load.getResistor("RLOAD") != null && load.getResistor("RLOAD").isMutable() &&
                load.getLedRecipes().get("LED1") != null,
                "channel load does not declare mutable RLOAD and LED1");
        ElectricalRealizationSpec.ElementDeclaration led =
                spec(plan).getElementDeclaration(owner, "LED1");
        check(led != null && "LED".equals(led.getKind()) &&
                led.getPostIndex("A") == 0 && led.getPostIndex("K") == 1,
                "channel LED polarity or post mapping changed");
        check(load.getResolvedValueRecipe().getResistanceOhms() > 0.0 &&
                load.getResolvedValueRecipe().getResistanceMinimumOhms() <
                    load.getResolvedValueRecipe().getResistanceOhms() &&
                load.getResolvedValueRecipe().getResistanceMaximumOhms() >
                    load.getResolvedValueRecipe().getResistanceOhms(),
                "channel value recipe is outside its declared resistance window");
    }

    private static void verifyFaultOwners(BoundedAssemblyPlan plan) {
        check(plan.getDecisionOwners().containsKey(plan.getFaultDecisionKey()),
                "selected fault is present in the decision-owner map");
        for (Map.Entry<String, String> entry : plan.getDecisionOwners().entrySet()) {
            boolean found = false;
            for (ComposedBlockContribution block : plan.getBlocks().values()) {
                ComposedBlockContribution.FaultSpec fault = block.getFaultSpec();
                if (fault != null && entry.getValue().equals(plan.idFor(block,
                        EntityKind.COMPONENT, fault.getTargetComponentLocalId()))) {
                    found = true;
                    break;
                }
            }
            check(found, "fault owner does not resolve to a declared component");
        }
    }

    private static void mapCompleteness(ElectricalRealizationSpec spec) {
        check(spec.getPackageMap().getPackages().keySet().equals(
                spec.getPackageMap().getPackageOwners().keySet()),
                "package owners do not cover exactly the package map");
        Set<String> unitComponents = new HashSet<String>();
        for (ElectricalUnitPackageMap.Unit unit : spec.getPackageMap().getUnits().values()) {
            check(spec.getPackageMap().getPackages().containsKey(unit.getComponentId()) &&
                    spec.getPackageMap().getPackageOwners().get(unit.getComponentId()).equals(
                        unit.getOwnerKey()) && unit.getTerminalIds().size() ==
                        unit.getPackageTerminalByUnitTerminal().size(),
                    "physical unit lost package ownership or terminal correspondence");
            check(unitComponents.add(unit.getComponentId()),
                    "two units unexpectedly share a current component identity");
        }
        check(unitComponents.equals(spec.getPackageMap().getPackages().keySet()),
                "package map and physical-unit map cover different identities");
        for (ElectricalRealizationSpec.ProviderDeclaration provider :
                spec.getProviderDeclarations().values()) {
            check(provider.getElements() != null && provider.getChoices() != null,
                    "provider declaration omitted its immutable element/value maps");
        }
    }

    private static void provider(ElectricalRealizationSpec spec, String owner,
            String providerId, int version) {
        ElectricalRealizationSpec.ProviderDeclaration declaration =
                spec.getProviderDeclaration(owner);
        check(declaration != null && providerId.equals(declaration.getProviderId()) &&
                version == declaration.getProviderVersion() &&
                declaration.getContribution() != null,
                "provider declaration changed for " + owner);
    }

    private static void element(ElectricalRealizationSpec spec, String owner, String id,
            String kind, String first, int firstPost, String second, int secondPost) {
        ElectricalRealizationSpec.ElementDeclaration declaration =
                spec.getElementDeclaration(owner, id);
        check(declaration != null && kind.equals(declaration.getKind()) &&
                declaration.getPostIndex(first) == firstPost &&
                declaration.getPostIndex(second) == secondPost,
                "element declaration changed for " + owner + "/" + id);
    }

    private static void mapping(ElectricalRealizationSpec spec, BoundedAssemblyPlan plan,
            String owner, String local, String terminal, String net) {
        ElectricalRealizationSpec.TerminalMapping mapping =
                spec.getTerminalMapping(owner, local, terminal);
        check(mapping != null && owner.equals(mapping.getOwnerKey()) &&
                local.equals(mapping.getLocalId()) && terminal.equals(mapping.getTerminalId()) &&
                net.equals(mapping.getNetId()) && mapping.getComponentId().equals(
                    spec.getComponentId(owner, local)) &&
                mapping.getComponentEndpoint() != null,
                "terminal/package/net mapping changed for " + owner + "/" + local + "." + terminal);
        check(spec.getPadBinding(spec.getPadId(owner, local + "." + terminal)) != null,
                "terminal lost its declared physical pad " + owner + "/" + local);
    }

    private static ElectricalRealizationSpec spec(BoundedAssemblyPlan plan) {
        return plan.getElectricalRealizationSpec();
    }

    private static Map<String, Integer> posts(Object... values) {
        HashMap<String, Integer> result = new HashMap<String, Integer>();
        for (int index = 0; index < values.length; index += 2)
            result.put((String) values[index], (Integer) values[index + 1]);
        return result;
    }

    private static Map<String, Double> numbers(Object... values) {
        HashMap<String, Double> result = new HashMap<String, Double>();
        for (int index = 0; index < values.length; index += 2)
            result.put((String) values[index], (Double) values[index + 1]);
        return result;
    }

    private static List<String> canonicalIds(List<LowSideRoleFamily.Provider> providers) {
        ArrayList<String> result = new ArrayList<String>();
        for (LowSideRoleFamily.Provider provider : providers) result.add(provider.getTypeId());
        return result;
    }

    private static boolean isDriverType(String providerId) {
        return isNmos(providerId) || isNpn(providerId);
    }

    private static boolean isNmos(String providerId) {
        return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(providerId) ||
                "nmos-low-side-driver-alt".equals(providerId);
    }

    private static boolean isNpn(String providerId) {
        return ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(providerId);
    }

    private interface Action { void run(); }

    private static void expectIllegal(Action action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException: " + label);
    }

    private static void expectUnsupported(Action action, String label) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            assertions++;
            return;
        }
        throw new AssertionError("Expected UnsupportedOperationException: " + label);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
