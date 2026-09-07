package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/** Pure contract matrix for the Task 48 controlled-indicator composition. */
public final class ControlledIndicatorAssemblyContractTest {
    private static int assertions;
    private static final long[] SEEDS = { 0L, 1L, 2L, 3L,
            Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L,
            9007199254740993L };

    private ControlledIndicatorAssemblyContractTest() { }

    public static void main(String[] args) {
        System.out.println("TASK48_ASSEMBLY_RECEIPT_BEGIN");
        for (long seed : SEEDS) verifySeed(seed);
        System.out.println("TASK48_ASSEMBLY_RECEIPT_END");
        verifyReorderInvariance();
        verifyUntypedOpenDrainRejected();
        verifyInvalidFaultTargetRejected();
        verifyUnknownDescriptorRejected();
        verifyUnsupportedInputs();
        verifyDiagnosticPadIdentity();
        System.out.println("PASS: Task48 pure controlled-indicator contracts "
                + assertions + " assertions");
    }

    private static void verifySeed(long seed) {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(seed);
        require(request.getBlocks().size() == 2, "two contributed blocks");
        require(request.getDeviceAdapters().size() == 2, "two device adapters");
        require(request.getAllElectricalContracts().size() == 4,
                "two blocks plus two adapter contracts");
        require(request.getNamespaceDescriptors().size() == 4,
                "complete namespace descriptor set");
        require(request.getConnections().size() == 4,
                "four explicit device joins");
        int components = 0, pads = 0;
        for (FunctionalBlockDescriptor descriptor : request.getNamespaceDescriptors()) {
            components += descriptor.getComponents().size();
            pads += descriptor.getPads().size();
        }
        require(components == 7, "seven physical components");
        require(pads == 15, "fifteen physical pads");
        PortCompatibilityPreflight.Result preflight =
                BoundedAssemblyPlan.preflight(request);
        require(preflight.getDecision() == PortCompatibilityPreflight.Decision.COMPATIBLE,
                "typed controlled relation preflights");
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        require(plan.isControlledIndicator(), "controlled route resolves");
        require(plan.getNetAliases().size() == 12, "twelve local nets retained");
        Set<String> canonical = new HashSet<String>(plan.getNetAliases().values());
        require(canonical.size() == 6, "six canonical joined nets");
        require(plan.netForPort("load", "SUPPLY").equals(
                plan.netForPort("power-adapter", "POWER_OUT")),
                "power adapter join is explicit");
        require(plan.netForPort("driver", "CONTROL").equals(
                plan.netForPort("control-adapter", "CONTROL_OUT")),
                "control adapter join is explicit");
        require(plan.netForPort("driver", "SWITCHED_SINK").equals(
                plan.netForPort("load", "SWITCHED_LOAD")),
                "typed switched join is explicit");
        require(plan.netForPort("driver", "RETURN").equals(
                plan.netForPort("power-adapter", "RETURN")),
                "common return join is explicit");
        require(plan.idFor("driver", EntityKind.COMPONENT, "Q1").equals(
                "tsj-block-v1/controlled-indicator@1/driver/component/Q1"),
                "driver component namespace");
        require(plan.idFor("power-adapter", EntityKind.PAD, "J1.1").equals(
                "tsj-block-v1/controlled-indicator@1/power-adapter/pad/J1.1"),
                "adapter pad namespace");
        require(plan.getFaultDecisionKey().equals(
                ControlledIndicatorBlockContributions.chooseFaultDecision(seed)),
                "only FAULT stream selects the initial owner");
        require(plan.getDecisionOwners().size() == 2,
                "both same-type owners remain candidate owners");
        require(plan.getDriver().getResistors().get("RG").isMutable(),
                "driver series gate resistor is mutable");
        require(!plan.getDriver().getResistors().get("RPD").isMutable(),
                "driver pulldown remains fixed");
        require(plan.getLoad().getResistors().get("RLOAD").isMutable(),
                "load resistor is mutable");
        require(plan.getDriver().getNmosRecipes().size() == 1
                && plan.getLoad().getLedRecipes().size() == 1,
                "fixed NMOS and LED recipes are declared");
        require(plan.getDeviceAdapters().get(0).getComponentLocalId().equals("J2")
                || plan.getDeviceAdapters().get(0).getComponentLocalId().equals("J1"),
                "adapter component identity is retained");
        System.out.println("seed=" + Long.toString(seed) + ";fault=" +
                plan.getFaultDecisionKey() + ";descriptor=" +
                request.getDescriptor().toCanonical().replace('\n', '|'));
    }

    private static void verifyReorderInvariance() {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(9007199254740993L);
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
        BoundedAssemblyPlan second = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.reorderedInputs(request.getDescriptor(),
                        blocks, connections, adapters));
        require(first.getSemanticSignature().equals(second.getSemanticSignature()),
                "reordered declarations preserve controlled semantics");
    }

    private static void verifyUntypedOpenDrainRejected() {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(0L);
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(request.getConnections());
        for (int index = 0; index < connections.size(); index++) {
            ElectricalConnection connection = connections.get(index);
            if (ControlledIndicatorBlockContributions.SWITCHED_CONNECTION_ID
                    .equals(connection.getId())) {
                connections.set(index, new ElectricalConnection(connection.getId(),
                        connection.getPorts()));
            }
        }
        BoundedAssemblyRequest untyped = BoundedAssemblyRequest.reorderedInputs(
                request.getDescriptor(), request.getBlocks(), connections,
                request.getDeviceAdapters());
        require(BoundedAssemblyPlan.preflight(untyped).getDecision()
                == PortCompatibilityPreflight.Decision.INSUFFICIENT_INFORMATION,
                "ordinary open-drain join remains fail-closed");
    }

    private static void verifyInvalidFaultTargetRejected() {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(0L);
        boolean rejected = false;
        try {
            BoundedAssemblyPlan.resolveForDiagnosticFault(request,
                    "tsj-block-v1/controlled-indicator@1/driver/component/RPD");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "unadmitted fault target is rejected");
    }

    private static void verifyUnknownDescriptorRejected() {
        ChallengeDescriptor descriptor = new ChallengeDescriptor(
                ChallengeDescriptor.SCHEMA_VERSION, 0L,
                new ChallengeDescriptor.VersionedId(
                        BoundedAssemblyRequest.GENERATOR_ID,
                        BoundedAssemblyRequest.CONTROLLED_GENERATOR_VERSION),
                new ChallengeDescriptor.VersionedId(
                        BoundedAssemblyRequest.CONTROLLED_INTENT_ID,
                        BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION),
                new ChallengeDescriptor.VersionedId("unknown-profile", 1),
                new PcbGeometryContractVersion(BoundedAssemblyRequest.GEOMETRY_VERSION),
                GenerationConstraints.unspecified());
        boolean rejected = false;
        try {
            BoundedAssemblyPlan.resolve(
                    BoundedAssemblyRequest.forControlledIndicator(descriptor));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "unknown controlled profile is rejected");
    }

    private static void verifyUnsupportedInputs() {
        String canonical = BoundedAssemblyRequest.forControlledIndicator(0L)
                .getDescriptor().toCanonical();
        String[][] changed = {
            { "generator=bounded-assembler@2", "generator=bounded-assembler@1" },
            { "generator=bounded-assembler@2", "generator=bounded-assembler@3" },
            { "device-intent=controlled-indicator@1", "device-intent=controlled-indicator@2" },
            { "difficulty-profile=controlled-indicator@1", "difficulty-profile=controlled-indicator@2" },
            { "geometry=3", "geometry=4" },
            { "blocks=~", "blocks=3:3" },
            { "components=~", "components=6:6" },
            { "domains=~", "domains=2:2" },
            { "diagnostic-depth=~", "diagnostic-depth=1:1" },
            { "instruments=~", "instruments=[DC_VOLTAGE]" },
            { "temporal-evidence=~", "temporal-evidence=required" },
            { "parallel-ambiguity=~", "parallel-ambiguity=forbidden" }
        };
        for (String[] change : changed) {
            boolean rejected = false;
            try {
                ChallengeDescriptor unsupported = ChallengeDescriptor.parse(
                    canonical.replace(change[0], change[1]));
                BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(unsupported));
            } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "unsupported version or constraint rejected: " + change[1]);
        }
        ChallengeDescriptor exact = ChallengeDescriptor.parse(canonical.replace("blocks=~", "blocks=2:2")
            .replace("components=~", "components=7:7").replace("domains=~", "domains=1:1"));
        require(BoundedAssemblyPlan.resolve(BoundedAssemblyRequest.forControlledIndicator(exact))
            .isControlledIndicator(), "supported exact constraints honored");
    }

    private static void verifyDiagnosticPadIdentity() {
        String pad = "tsj-block-v1/controlled-indicator@1/driver/pad/Q1.G";
        require(diagnosticPlan("CHECK", pad, pad).getReferenceTargetId().equals(pad),
            "qualified semantic pad survives diagnostic construction");
        require(diagnosticPlan("CHECK", "J1.2", "Q1.G").getReferenceTargetId().equals("J1.2"),
            "legacy diagnostic pad identity is unchanged");
        String[] invalid = {
            pad.replace("/pad/", "/component/"), pad.replace("tsj-block-v1", "tsj-block-v2"),
            pad.replace("@1", "@01"), pad.replace("@1", "@0"), pad + "/extra",
            pad.replace("Q1.G", "PRIVATE_Q1.G")
        };
        for (String value : invalid) {
            boolean rejected = false;
            try { diagnosticPlan("CHECK", value, pad); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "malformed or private diagnostic reference rejected: " + value);
            rejected = false;
            try { diagnosticPlan("CHECK", pad, value); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "malformed or private diagnostic probe rejected: " + value);
        }
        boolean rejected = false;
        try { diagnosticPlan(pad, pad, pad); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "qualified pad cannot become an operation/template identity");
    }

    private static GeneratedDiagnosticPlan diagnosticPlan(String template, String reference, String probe) {
        return new GeneratedDiagnosticPlan(template, reference, new String[] { probe },
            new String[] { "DC_VOLTAGE" }, new String[] { "BOARD_POWER_OFF" },
            new String[] { "REMOVE" }, new String[] { "CATALOG_INSTALL" },
            new String[] { "CUSTOMER_RETEST" }, new String[] { "STEADY_STATE_SAMPLE" },
            new String[] { "RETURN" }, 1, false, false, "NONE");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
