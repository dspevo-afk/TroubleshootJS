package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Maintained pure acceptance checks for the resolved controlled-indicator
 * composition. The request has one current generator epoch and always carries
 * the resolved load recipe.
 */
public final class ControlledIndicatorAssemblyContractTest {
    private static int assertions;
    private static final long[] SEEDS = { 0L, 1L, 2L, 3L,
            Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L,
            9007199254740993L };

    private ControlledIndicatorAssemblyContractTest() { }

    public static void main(String[] args) {
        for (long seed : SEEDS) verifySeed(seed);
        verifyReorderInvariance();
        verifyUntypedOpenDrainRejected();
        verifyInvalidFaultTargetRejected();
        verifyUnknownDescriptorRejected();
        verifyUnsupportedInputs();
        verifyDiagnosticPadIdentity();
        System.out.println("PASS: current controlled-indicator contracts "
                + assertions + " assertions");
    }

    private static void verifySeed(long seed) {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(seed);
        ChallengeDescriptor descriptor = request.getDescriptor();
        require(descriptor.getSchemaVersion() == ChallengeDescriptor.SCHEMA_VERSION &&
                descriptor.getGenerator().getVersion() ==
                    BoundedAssemblyRequest.GENERATOR_VERSION,
                "controlled request uses the current schema and generator");
        require(request.getBlocks().size() >= 2 &&
                request.getDeviceAdapters().size() >= 2 &&
                request.getConnections().size() >= 4,
                "controlled request retains its typed composition shape");

        PortCompatibilityPreflight.Result preflight =
                BoundedAssemblyPlan.preflight(request);
        require(preflight.getDecision() == PortCompatibilityPreflight.Decision.COMPATIBLE,
                "typed controlled relation preflights");
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        require(plan.isControlledIndicator(),
                "controlled route resolves");
        require(plan.getResolvedLoadRecipe() != null &&
                plan.getLoad().getResolvedValueRecipe() == plan.getResolvedLoadRecipe(),
                "controlled route resolves one immutable load recipe");
        require(plan.getDriver().getProviderVersion() ==
                ControlledIndicatorBlockContributions.VERSION &&
                plan.getLoad().getProviderVersion() ==
                ControlledIndicatorBlockContributions.LOAD_VERSION,
                "driver and load retain their declared provider versions");

        ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                plan.getResolvedLoadRecipe();
        require(recipe.getResistanceOhms() > 0.0 &&
                recipe.getResistanceMinimumOhms() < recipe.getResistanceOhms() &&
                recipe.getResistanceMaximumOhms() > recipe.getResistanceOhms() &&
                recipe.getMinimumCurrentAmps() >=
                    recipe.getIntent().getTargetMinimumCurrentAmps() &&
                recipe.getMaximumCurrentAmps() <=
                    recipe.getIntent().getTypedDemandAmps() &&
                recipe.getGuardedPowerWatts() > 0.0,
                "resolved recipe satisfies electrical bounds");
        require(recipe.getPackageId() != null &&
                !recipe.getPackageId().isEmpty() &&
                recipe.getTolerancePercent() > 0.0,
                "resolved recipe retains truthful package and tolerance");

        require(plan.netForPort("load", "SUPPLY").equals(
                plan.netForPort("power-adapter", "POWER_OUT")),
                "power adapter supply join is explicit");
        require(plan.netForPort("driver", "CONTROL").equals(
                plan.netForPort("control-adapter", "CONTROL_OUT")),
                "control adapter join is explicit");
        require(plan.netForPort("driver", "SWITCHED_SINK").equals(
                plan.netForPort("load", "SWITCHED_LOAD")),
                "typed switched join is explicit");
        require(plan.netForPort("driver", "RETURN").equals(
                plan.netForPort("power-adapter", "RETURN")),
                "common return join is explicit");

        String driverComponent = plan.idFor("driver", EntityKind.COMPONENT, "Q1");
        String adapterPad = plan.idFor("power-adapter", EntityKind.PAD, "J1.1");
        require(nonEmpty(driverComponent) && nonEmpty(adapterPad) &&
                driverComponent.contains("driver") &&
                adapterPad.contains("power-adapter"),
                "semantic identity retains owner and local identity");
        require(!driverComponent.equals(adapterPad),
                "different entity kinds and owners remain distinct");
        require(plan.getFaultDecisionKey().equals(
                ControlledIndicatorBlockContributions.chooseFaultDecision(seed)),
                "only the current fault stream selects the initial owner");
        require(!plan.getDecisionOwners().isEmpty(),
                "both current fault owners remain represented");
        require(plan.getDriver().getFaultSpec() != null &&
                plan.getLoad().getFaultSpec() != null,
                "fault declarations are explicit");
        require(plan.getDriver().getResistors().get("RG").isMutable() &&
                !plan.getDriver().getResistors().get("RPD").isMutable() &&
                plan.getLoad().getResistors().get("RLOAD").isMutable(),
                "only repairable resistors are mutable");
        require(plan.getDriver().getNmosRecipes().size() == 1 &&
                plan.getLoad().getLedRecipes().size() == 1,
                "NMOS and LED physical recipes are declared");

        System.out.println("seed=" + Long.toString(seed) + ";fault=" +
                plan.getFaultDecisionKey() + ";catalog=" +
                recipe.getCatalogEntryId());
    }

    private static void verifyReorderInvariance() {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(
                        9007199254740993L);
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
        BoundedAssemblyPlan replay = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(
                        ChallengeDescriptor.parse(request.getDescriptor()
                                .toCanonical())));
        require(first.getSemanticSignature().equals(second.getSemanticSignature()) &&
                first.getSemanticSignature().equals(replay.getSemanticSignature()),
                "reordered and canonical replay preserve current semantics");
        require(first.getResolvedLoadRecipe().getCatalogEntryId().equals(
                second.getResolvedLoadRecipe().getCatalogEntryId()) &&
                first.getResolvedLoadRecipe().getCatalogEntryId().equals(
                replay.getResolvedLoadRecipe().getCatalogEntryId()),
                "reordered and replayed requests preserve selected value");
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
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        String wrongTarget = plan.idFor("driver", EntityKind.COMPONENT, "RPD");
        boolean rejected = false;
        try {
            BoundedAssemblyPlan.resolveForDiagnosticFault(request, wrongTarget);
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
                        BoundedAssemblyRequest.GENERATOR_VERSION),
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
        String generator = "generator=" + BoundedAssemblyRequest.GENERATOR_ID +
                "@" + BoundedAssemblyRequest.GENERATOR_VERSION;
        String intent = "device-intent=" +
                BoundedAssemblyRequest.CONTROLLED_INTENT_ID + "@" +
                BoundedAssemblyRequest.CONTROLLED_INTENT_VERSION;
        String profile = "difficulty-profile=" +
                BoundedAssemblyRequest.CONTROLLED_PROFILE_ID + "@" +
                BoundedAssemblyRequest.CONTROLLED_PROFILE_VERSION;
        String[][] changed = {
            { generator, "generator=" + BoundedAssemblyRequest.GENERATOR_ID + "@99" },
            { intent, "device-intent=controlled-indicator@2" },
            { profile, "difficulty-profile=controlled-indicator@2" },
            { "geometry=" + BoundedAssemblyRequest.GEOMETRY_VERSION,
                "geometry=" + (BoundedAssemblyRequest.GEOMETRY_VERSION + 1) },
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
                BoundedAssemblyPlan.resolve(
                        BoundedAssemblyRequest.forControlledIndicator(unsupported));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "unsupported current input rejected: " + change[1]);
        }
        ChallengeDescriptor exact = ChallengeDescriptor.parse(canonical
                .replace("blocks=~", "blocks=2:2")
                .replace("components=~", "components=7:7")
                .replace("domains=~", "domains=1:1"));
        require(BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(exact))
                .isControlledIndicator(), "supported exact constraints honored");
    }

    private static void verifyDiagnosticPadIdentity() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(0L));
        String pad = plan.idFor("driver", EntityKind.PAD, "Q1.G");
        require(diagnosticPlan("CHECK", pad, pad).getReferenceTargetId().equals(pad),
                "qualified semantic pad survives diagnostic construction");
        require(diagnosticPlan("CHECK", "J1.2", "Q1.G").getReferenceTargetId()
                .equals("J1.2"), "local diagnostic pad remains supported");
        String[] invalid = {
            pad + "/extra", "PRIVATE_Q1.G"
        };
        for (String value : invalid) {
            boolean rejected = false;
            try { diagnosticPlan("CHECK", value, pad); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "malformed diagnostic reference rejected");
            rejected = false;
            try { diagnosticPlan("CHECK", pad, value); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "malformed diagnostic probe rejected");
        }
        boolean rejected = false;
        try { diagnosticPlan(pad, pad, pad); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "qualified pad cannot become an operation identity");
    }

    private static GeneratedDiagnosticPlan diagnosticPlan(String template,
            String reference, String probe) {
        return new GeneratedDiagnosticPlan(template, reference,
            new String[] { probe }, new String[] { "DC_VOLTAGE" },
            new String[] { "BOARD_POWER_OFF" }, new String[] { "REMOVE" },
            new String[] { "CATALOG_INSTALL" }, new String[] { "CUSTOMER_RETEST" },
            new String[] { "STEADY_STATE_SAMPLE" }, new String[] { "RETURN" },
            1, false, false, "NONE");
    }

    private static boolean nonEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
