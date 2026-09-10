package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Native pure contract for A05 functional-role providers and repeated
 * controlled-indicator channels. It checks the provider registry, both
 * low-side variants, stable per-instance streams, exact transistor identity,
 * fault ownership, and the fixed supply-present contribution.
 */
public final class A05RoleContractTest {
    private static int assertions;
    private static final long[] SEEDS = { -1L, 0L, 1L, 2L,
            Long.MIN_VALUE, Long.MAX_VALUE };

    private A05RoleContractTest() { }

    public static void main(String[] args) {
        boolean sawNmosNmos = false;
        boolean sawNpnNpn = false;
        boolean sawMixed = false;
        for (long seed : SEEDS) {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forControlledIndicator(seed));
            String first = verifyPlan(seed, plan);
            String second = plan.getBlocks().get(plan.getChannels().get(1)
                    .getDriverKey()).getProviderTypeId();
            if (isNmos(first) && isNmos(second)) sawNmosNmos = true;
            if (isNpn(first) && isNpn(second)) sawNpnNpn = true;
            if (!first.equals(second)) sawMixed = true;
            System.out.println("A05_ROLE_VECTOR seed=" + Long.toString(seed) +
                    ";a=" + first + ";b=" + second + ";fault=" +
                    plan.getFaultDecisionKey());
        }
        require(sawNmosNmos && sawNpnNpn && sawMixed,
                "signed seed corpus covers NMOS/NMOS, NPN/NPN and mixed pairs");
        verifyRegistry();
        verifyUnsupportedEnvelopes();
        verifyInstanceIsolation();
        verifyFaultOwnership();
        verifySupportContribution();
        System.out.println("PASS: A05 role providers " + assertions
                + " assertions");
    }

    private static String verifyPlan(long seed, BoundedAssemblyPlan plan) {
        require(plan.isControlledIndicator() && plan.getChannels().size() == 2,
                "A05 plan retains two stable channels");
        require(plan.getBlocks().size() == 5 && plan.getSupportBlockKey().equals(
                BoundedAssemblyRequest.SUPPORT_BLOCK_KEY),
                "A05 plan retains two drivers, two loads and support");
        String firstType = null;
        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ComposedBlockContribution driver = plan.getBlocks().get(
                    channel.getDriverKey());
            ComposedBlockContribution load = plan.getBlocks().get(
                    channel.getLoadKey());
            require(driver != null && load != null,
                    "stable channel keys resolve both local contributions");
            if (firstType == null) firstType = driver.getProviderTypeId();
            require(isNmos(driver.getProviderTypeId()) ||
                    isNpn(driver.getProviderTypeId()),
                    "driver provider is an admitted low-side variant");
            require(driver.getRoleId().equals(LowSideRoleFamily.ROLE_ID) &&
                    driver.getProviderVersion() == LowSideRoleFamily.VERSION,
                    "driver declares the shared low-side functional role");
            require(load.getProviderTypeId().equals(
                    ControlledIndicatorBlockContributions.LOAD_TYPE_ID) &&
                    load.getResolvedValueRecipe() != null,
                    "channel load has the current resolved value provider");
            verifyDriverDeclaration(plan, channel, driver);
            verifyPhysicalDriver(plan, channel, driver);
        }
        require(firstType != null, "at least one driver provider is declared");
        require(!plan.idFor(plan.getChannels().get(0).getDriverKey(),
                EntityKind.COMPONENT, "Q1").equals(plan.idFor(
                plan.getChannels().get(1).getDriverKey(),
                EntityKind.COMPONENT, "Q1")),
                "repeated Q1 identities are owner-qualified");
        return firstType;
    }

    private static void verifyDriverDeclaration(BoundedAssemblyPlan plan,
            ControlledIndicatorChannel channel, ComposedBlockContribution driver) {
        FunctionalBlockDescriptor descriptor = driver.getDescriptor();
        FunctionalBlockDescriptor.Component q1 = descriptor.getComponents().get("Q1");
        require(q1 != null && q1.getTerminalIds().size() == 3,
                "Q1 declares exactly three transistor terminals");
        require(driver.getResistor("RPD") != null &&
                !driver.getResistor("RPD").isMutable() &&
                driver.getFaultSpec() != null,
                "driver declares a fixed pulldown and one local fault");
        if (isNmos(driver.getProviderTypeId())) {
            require(q1.getTypeId().equals("NMOS") &&
                    q1.getTerminalIds().containsAll(Arrays.asList("G", "D", "S")) &&
                    driver.getResistor("RG") != null &&
                    driver.getResistor("RG").isMutable() &&
                    driver.getNmosRecipes().size() == 1,
                    "NMOS role declares G/D/S and mutable RG");
            require(driver.getFaultSpec().getTargetComponentLocalId().equals("RG"),
                    "NMOS fault owns RG");
        } else {
            require(q1.getTypeId().equals("NPN") &&
                    q1.getTerminalIds().containsAll(Arrays.asList("B", "C", "E")) &&
                    driver.getResistor("RB") != null &&
                    driver.getResistor("RB").isMutable() &&
                    driver.getNmosRecipes().isEmpty(),
                    "NPN role declares B/C/E and mutable RB");
            require(driver.getFaultSpec().getTargetComponentLocalId().equals("RB"),
                    "NPN fault owns RB");
        }
        require(driver.getDescriptor().getInstanceKey().equals(
                channel.getDriverKey()), "driver declaration retains channel owner");
    }

    private static void verifyPhysicalDriver(BoundedAssemblyPlan plan,
            ControlledIndicatorChannel channel, ComposedBlockContribution driver) {
        String owner = channel.getDriverKey();
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        ElectricalRealizationSpec.ElementDeclaration element =
                spec.getElementDeclaration(owner, "Q1");
        ElectricalRealizationSpec.PhysicalUnitSpec unit =
                spec.getPhysicalUnits().get(ElectricalRealizationSpec.elementKey(owner, "Q1"));
        require(element != null && unit != null,
                "Q1 is represented by the provider-owned electrical spec");
        String[] terminals;
        String packageId;
        if (isNmos(driver.getProviderTypeId())) {
            terminals = new String[] { "G", "D", "S" };
            packageId = "TO92_NMOS";
            require(element.getKind().equals("NMOS") &&
                    element.getPostIndex("G") == 0 &&
                    element.getPostIndex("D") == 2 &&
                    element.getPostIndex("S") == 1,
                    "NMOS Q1 maps G/D/S to CircuitJS posts 0/2/1");
        } else {
            terminals = new String[] { "B", "C", "E" };
            packageId = "TO92_NPN";
            require(element.getKind().equals("NPN") &&
                    element.getPostIndex("B") == 0 &&
                    element.getPostIndex("C") == 1 &&
                    element.getPostIndex("E") == 2,
                    "NPN Q1 maps B/C/E to CircuitJS posts 0/1/2");
        }
        require(unit.getPackageId().equals(packageId) &&
                unit.getPackageTerminalByUnitTerminal().keySet()
                    .containsAll(Arrays.asList(terminals)),
                "Q1 uses the role-specific TO92 package terminals");
        for (String terminal : terminals) {
            ElectricalRealizationSpec.TerminalMapping mapping =
                    spec.getTerminalMapping(owner, "Q1", terminal);
            require(mapping != null && mapping.getPackageId().equals(packageId) &&
                    mapping.getPackageTerminalId().equals(terminal) &&
                    mapping.getComponentEndpoint().getOwnerKey().equals(owner) &&
                    mapping.getComponentEndpoint().getElementId().equals("Q1") &&
                    mapping.getComponentEndpoint().getTerminalId().equals(terminal),
                    "Q1 terminal/package mapping retains exact owner and terminal");
        }
    }

    private static void verifyRegistry() {
        List<LowSideRoleFamily.Provider> canonical =
                LowSideRoleFamily.providers();
        require(canonical.size() == 2 &&
                canonical.get(0).getTypeId().equals(
                    ControlledIndicatorBlockContributions.DRIVER_TYPE_ID) &&
                canonical.get(1).getTypeId().equals(
                    ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID),
                "canonical registry orders NMOS before NPN by provider ID");
        ArrayList<LowSideRoleFamily.Provider> reversed =
                new ArrayList<LowSideRoleFamily.Provider>(canonical);
        Collections.reverse(reversed);
        List<LowSideRoleFamily.Provider> reordered =
                LowSideRoleFamily.canonicalProviders(reversed);
        require(providerIds(canonical).equals(providerIds(reordered)),
                "registry reordering does not change canonical registration");
        for (long seed : SEEDS) {
            for (String key : new String[] { "channel-a-driver",
                    "channel-b-driver", "unrelated-driver" }) {
                require(LowSideRoleFamily.select(seed, key,
                        LowSideRoleFamily.Envelope.standard(), canonical).getTypeId()
                        .equals(LowSideRoleFamily.select(seed, key,
                        LowSideRoleFamily.Envelope.standard(), reversed).getTypeId()),
                        "provider selection is invariant to registry order");
            }
        }
        expectRejected(new Rejection() {
            public void run() {
                LowSideRoleFamily.canonicalProviders(Arrays.asList(
                        ControlledIndicatorBlockContributions.nmosDriver(),
                        ControlledIndicatorBlockContributions.nmosDriver()));
            }
        }, "duplicate provider registration");
    }

    private static void verifyUnsupportedEnvelopes() {
        LowSideRoleFamily.Envelope standard = LowSideRoleFamily.Envelope.standard();
        final LowSideRoleFamily.Envelope highSide = new LowSideRoleFamily.Envelope(
                false, standard.getSupplyMinimumVolts(), standard.getSupplyMaximumVolts(),
                standard.getControlHighMinimumVolts(), standard.getControlLowMaximumVolts(),
                standard.getControlCapacityAmps(), standard.getSinkCapacityAmps(),
                standard.getLoadDemandAmps());
        expectRejected(new Rejection() {
            public void run() {
                LowSideRoleFamily.select(0L, "high-side", highSide);
            }
        }, "unsupported high-side role envelope");
        final LowSideRoleFamily.Envelope insufficientControl =
                new LowSideRoleFamily.Envelope(true,
                        standard.getSupplyMinimumVolts(), standard.getSupplyMaximumVolts(),
                        standard.getControlHighMinimumVolts(),
                        standard.getControlLowMaximumVolts(), 1.0e-9,
                        standard.getSinkCapacityAmps(), standard.getLoadDemandAmps());
        expectRejected(new Rejection() {
            public void run() {
                LowSideRoleFamily.select(0L, "insufficient-control", insufficientControl);
            }
        }, "insufficient control source capacity");
    }

    private static void verifyInstanceIsolation() {
        ControlledIndicatorValueSynthesis.Intent intent =
                ControlledIndicatorValueSynthesis.defaultIntent();
        for (long seed : SEEDS) {
            LowSideRoleFamily.Provider first = LowSideRoleFamily.select(seed,
                    "channel-a-driver", LowSideRoleFamily.Envelope.standard());
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipeBefore =
                    ControlledIndicatorValueSynthesis.resolve(seed,
                            "channel-a-load", intent);
            LowSideRoleFamily.select(seed, "unrelated-driver",
                    LowSideRoleFamily.Envelope.standard());
            ControlledIndicatorValueSynthesis.resolve(seed, "unrelated-load", intent);
            LowSideRoleFamily.Provider second = LowSideRoleFamily.select(seed,
                    "channel-a-driver", LowSideRoleFamily.Envelope.standard());
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipeAfter =
                    ControlledIndicatorValueSynthesis.resolve(seed,
                            "channel-a-load", intent);
            require(first.getTypeId().equals(second.getTypeId()) &&
                    recipeBefore.semanticSignature().equals(recipeAfter.semanticSignature()),
                    "unrelated instance cannot shift provider or value selection");
        }
        BoundedAssemblyPlan firstPlan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(2L));
        BoundedAssemblyPlan secondPlan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(2L));
        for (ControlledIndicatorChannel channel : firstPlan.getChannels()) {
            require(firstPlan.idFor(channel.getDriverKey(), EntityKind.COMPONENT, "Q1")
                    .equals(secondPlan.idFor(channel.getDriverKey(),
                            EntityKind.COMPONENT, "Q1")),
                    "same channel retains stable physical identity on replay");
        }
        require(!firstPlan.idFor(firstPlan.getChannels().get(0).getDriverKey(),
                EntityKind.COMPONENT, "Q1").equals(firstPlan.idFor(
                firstPlan.getChannels().get(1).getDriverKey(),
                EntityKind.COMPONENT, "Q1")),
                "repeated channels retain distinct qualified identities");
    }

    private static void verifyFaultOwnership() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(-1L));
        require(plan.getDecisionOwners().containsKey(plan.getFaultDecisionKey()),
                "selected fault is in the decision owner table");
        for (Map.Entry<String, String> entry : plan.getDecisionOwners().entrySet()) {
            boolean actual = false;
            for (ComposedBlockContribution block : plan.getBlocks().values()) {
                ComposedBlockContribution.FaultSpec fault = block.getFaultSpec();
                if (fault != null && entry.getValue().equals(plan.idFor(block,
                        EntityKind.COMPONENT, fault.getTargetComponentLocalId()))) {
                    actual = true;
                    break;
                }
            }
            require(actual, "fault owner maps to the declared component target");
        }
        require(plan.getBlocks().get(plan.getSupportBlockKey()).getFaultSpec() == null,
                "support does not enter the fault owner population");
    }

    private static void verifySupportContribution() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(0L));
        String key = plan.getSupportBlockKey();
        ComposedBlockContribution support = plan.getBlocks().get(key);
        require(support.getProviderTypeId().equals(
                SupplyPresentBlockContributions.TYPE_ID) &&
                support.getProviderVersion() == SupplyPresentBlockContributions.VERSION &&
                support.getRoleId().equals(SupplyPresentBlockContributions.ROLE_ID) &&
                support.getFaultSpec() == null &&
                support.getResistor("RSUP") != null &&
                support.getResistor("RSUP").getResistanceOhms() ==
                    SupplyPresentBlockContributions.RSUP_OHMS &&
                !support.getResistor("RSUP").isMutable() &&
                support.getLedRecipes().get("LED1").getModelId().equals(
                    SupplyPresentBlockContributions.MODEL_ID),
                "support declares fixed RSUP and LED1 without a fault");
        ElectricalPortContract supply = support.getElectricalContract().getPorts()
                .get("SUPPLY");
        require(supply.getAllowedVoltage().getMinimum() == 4.5 &&
                supply.getAllowedVoltage().getMaximum() == 5.5 &&
                supply.getDemandAmps().getValue() ==
                    SupplyPresentBlockContributions.SUPPLY_DEMAND_MAX_AMPS,
                "support supply port declares its typed voltage and demand");
        FunctionalBlockDescriptor descriptor = support.getDescriptor();
        require(descriptor.getParameters().get("healthy-current-min-amps")
                .getValue().getDecimal() == SupplyPresentBlockContributions.HEALTHY_CURRENT_MIN_AMPS &&
                descriptor.getParameters().get("healthy-current-max-amps")
                .getValue().getDecimal() == SupplyPresentBlockContributions.HEALTHY_CURRENT_MAX_AMPS,
                "support declares the typed 2-4 mA healthy current window");
    }

    private static List<String> providerIds(List<LowSideRoleFamily.Provider> providers) {
        ArrayList<String> result = new ArrayList<String>();
        for (LowSideRoleFamily.Provider provider : providers)
            result.add(provider.getTypeId());
        return result;
    }

    private static boolean isNmos(String typeId) {
        return ControlledIndicatorBlockContributions.DRIVER_TYPE_ID.equals(typeId);
    }

    private static boolean isNpn(String typeId) {
        return ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(typeId);
    }

    private interface Rejection { void run(); }

    private static void expectRejected(Rejection action, String label) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, label + " was admitted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
