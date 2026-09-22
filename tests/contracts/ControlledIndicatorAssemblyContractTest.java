package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Maintained pure acceptance checks for the current repeated
 * controlled-indicator composition. Two stable channel instances and one
 * fixed supply-present contribution are part of the current contract.
 */
public final class ControlledIndicatorAssemblyContractTest {
    private static int assertions;
    private static final long[] SEEDS = { 0L, 1L, 2L, 3L,
            Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L,
            9007199254740993L };

    private ControlledIndicatorAssemblyContractTest() { }

    public static void main(String[] args) {
        for (long seed : SEEDS) verifySeed(seed);
        verifyProceduralPlayerGeometry();
        verifyInitialMetadataHandoffKeepsReplayFresh();
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
        require(request.getBlocks().size() == 5 &&
                request.getDeviceAdapters().size() == 3 &&
                request.getConnections().size() == 6,
                "controlled request retains both channels and support wiring");

        PortCompatibilityPreflight.Result preflight =
                BoundedAssemblyPlan.preflight(request);
        require(preflight.getDecision() == PortCompatibilityPreflight.Decision.COMPATIBLE,
                "typed controlled relations preflight");
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(request);
        require(plan.isControlledIndicator() && plan.getChannels().size() == 2,
                "controlled route resolves its stable channel set");
        ComposedBlockContribution support =
                plan.getBlocks().get(plan.getSupportBlockKey());
        require(support != null && support.getProviderTypeId().equals(
                SupplyPresentBlockContributions.TYPE_ID) &&
                support.getFaultSpec() == null,
                "support route is a fixed nonfaultable contribution");

        for (ControlledIndicatorChannel channel : plan.getChannels()) {
            ComposedBlockContribution driver =
                    plan.getBlocks().get(channel.getDriverKey());
            ComposedBlockContribution load =
                    plan.getBlocks().get(channel.getLoadKey());
            require(driver != null && load != null &&
                    (driver.getProviderTypeId().equals(
                        ControlledIndicatorBlockContributions.DRIVER_TYPE_ID) ||
                     driver.getProviderTypeId().equals("nmos-low-side-driver-alt") ||
                     driver.getProviderTypeId().equals(
                        ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID)) &&
                    load.getProviderTypeId().equals(
                        ControlledIndicatorBlockContributions.LOAD_TYPE_ID),
                    "each channel resolves an admitted driver and load provider");
            require(load.getProviderVersion() ==
                    ControlledIndicatorBlockContributions.LOAD_VERSION &&
                    load.getResolvedValueRecipe() != null,
                    "each load retains its declared version and recipe");
            ControlledIndicatorValueSynthesis.ResolvedRecipe recipe =
                    load.getResolvedValueRecipe();
            require(recipe.getResistanceOhms() > 0.0 &&
                    recipe.getResistanceMinimumOhms() < recipe.getResistanceOhms() &&
                    recipe.getResistanceMaximumOhms() > recipe.getResistanceOhms() &&
                    recipe.getMinimumCurrentAmps() >=
                        recipe.getIntent().getTargetMinimumCurrentAmps() &&
                    recipe.getMaximumCurrentAmps() <=
                        recipe.getIntent().getTypedDemandAmps() &&
                    recipe.getGuardedPowerWatts() > 0.0,
                    "resolved channel recipe satisfies electrical bounds");
            require(plan.netForPort(channel.getLoadKey(), "SUPPLY").equals(
                    plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY,
                            "POWER_OUT")) &&
                    plan.netForPort(channel.getDriverKey(), "CONTROL").equals(
                    plan.netForPort(channel.getControlAdapterKey(), "CONTROL_OUT")) &&
                    plan.netForPort(channel.getDriverKey(), "SWITCHED_SINK").equals(
                    plan.netForPort(channel.getLoadKey(), "SWITCHED_LOAD")) &&
                    plan.netForPort(channel.getDriverKey(), "RETURN").equals(
                    plan.netForPort(DeviceAdapterContract.POWER_ADAPTER_KEY,
                            "RETURN")),
                    "channel joins retain explicit supply/control/switch/return nets");

            String driverComponent = plan.idFor(channel.getDriverKey(),
                    EntityKind.COMPONENT, "Q1");
            String loadPad = plan.idFor(channel.getLoadKey(), EntityKind.PAD,
                    "LED1.K");
            require(nonEmpty(driverComponent) && nonEmpty(loadPad) &&
                    driverComponent.contains(channel.getDriverKey()) &&
                    loadPad.contains(channel.getLoadKey()) &&
                    !driverComponent.equals(loadPad),
                    "repeated semantic identities retain owner and local identity");
            require(driver.getFaultSpec() != null &&
                    load.getFaultSpec() != null &&
                    driver.getResistor("RPD") != null &&
                    !driver.getResistor("RPD").isMutable() &&
                    load.getResistor("RLOAD") != null &&
                    load.getResistor("RLOAD").isMutable(),
                    "channel fault declarations retain repairable boundaries");
            if (ControlledIndicatorBlockContributions.NPN_DRIVER_TYPE_ID.equals(
                    driver.getProviderTypeId())) {
                require(driver.getResistor("RB") != null &&
                        driver.getResistor("RB").isMutable() &&
                        driver.getNmosRecipes().isEmpty(),
                        "NPN driver retains a mutable base resistor");
            } else {
                require(driver.getResistor("RG") != null &&
                        driver.getResistor("RG").isMutable() &&
                        driver.getNmosRecipes().size() == 1,
                        "NMOS driver retains a mutable gate resistor and Q1 recipe");
            }
            require(load.getLedRecipes().size() == 1,
                    "channel load retains its LED physical recipe");
        }

        require(plan.getDecisionOwners().containsKey(plan.getFaultDecisionKey()) &&
                everyDecisionOwnerIsActual(plan),
                "selected fault and every advertised owner resolve to actual targets");
        System.out.println("seed=" + Long.toString(seed) + ";fault=" +
                plan.getFaultDecisionKey() + ";a=" + plan.getBlocks().get(
                    plan.getChannels().get(0).getDriverKey()).getProviderTypeId() +
                ";b=" + plan.getBlocks().get(
                    plan.getChannels().get(1).getDriverKey()).getProviderTypeId());
    }

    private static void verifyProceduralPlayerGeometry() {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7; CircuitElm.sim = sim;
        long[] seeds = { 0L, 1L, 2L, 3L, 17L, 42L, 101L, -1L,
            Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993L, 9007199254740993L };
        java.util.HashSet<String> geometries = new java.util.HashSet<String>();
        java.util.HashSet<String> connectorLayouts = new java.util.HashSet<String>();
        java.util.HashSet<String> macroLayouts = new java.util.HashSet<String>();
        java.util.HashSet<String> faults = new java.util.HashSet<String>();
        for (long seed : seeds) {
            GeneratedBoardInstance board = new PlayerLaunchRequest(ControlledIndicatorBlockContributions.FAMILY_ID, Long.toString(seed),
                DifficultyProfile.MEDIUM.name()).generation().resolve(new GenerationRequest.PlanCache())
                .construct().instance;
            PcbBoardLayout layout = board.getPcbLayout();
            layout.validateGeometry(board.getBoard());
            geometries.add(layout.geometryFingerprint());
            faults.add(board.getChallengeDefinition().getFault().getId());
            Rectangle outline = layout.getBoardOutline();
            java.util.Vector<String> ids = board.getBoard().getComponentIds();
            java.util.Collections.sort(ids);
            StringBuilder macro = new StringBuilder();
            macro.append(outline.width / 100).append('x').append(outline.height / 100).append('|');
            for (String id : ids) {
                PcbComponentPlacement placement = layout.getComponent(id);
                macro.append(id).append('@').append(placement.getX() / 100).append(',')
                    .append(placement.getY() / 100).append(';');
            }
            macroLayouts.add(macro.toString());
            StringBuilder connector = new StringBuilder();
            int connectorCount = 0;
            for (String id : ids) {
                if (!board.getBoard().getComponent(id).getPhysicalPackage().isConnector()) continue;
                connectorCount++;
                PcbComponentPlacement placement = layout.getComponent(id);
                Rectangle envelope = placement.getRoutingCourtyard();
                int left = envelope.x - outline.x;
                int right = outline.x + outline.width - envelope.x - envelope.width;
                require(Math.min(left, right) <= 90, "procedural connector remains at a board edge");
                connector.append(id).append('@').append(left <= right ? 'L' : 'R')
                    .append(':').append(placement.getY()).append(';');
            }
            require(connectorCount >= 3, "Medium board exposes all physical connectors to edge placement");
            connectorLayouts.add(connector.toString());
            for (CircuitElm element : board.getSimulationElements()) element.delete();
        }
        GeneratedBoardInstance firstReplay = new PlayerLaunchRequest(
            ControlledIndicatorBlockContributions.FAMILY_ID, "1", DifficultyProfile.MEDIUM.name())
            .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        GeneratedBoardInstance secondReplay = new PlayerLaunchRequest(
            ControlledIndicatorBlockContributions.FAMILY_ID, "1", DifficultyProfile.MEDIUM.name())
            .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
        require(firstReplay.getPcbLayout().geometryFingerprint().equals(
                secondReplay.getPcbLayout().geometryFingerprint()) &&
                firstReplay.getChallengeDefinition().getFault().getId().equals(
                secondReplay.getChallengeDefinition().getFault().getId()),
            "same Medium seed reproduces exact layout and fault identity");
        for (CircuitElm element : firstReplay.getSimulationElements()) element.delete();
        for (CircuitElm element : secondReplay.getSimulationElements()) element.delete();

        require(geometries.size() == seeds.length,
            "selected distinct Medium seeds produce distinct complete PCB geometry");
        require(macroLayouts.size() >= 8,
            "Medium population has meaningful macro placement diversity, not coordinate jitter");
        require(connectorLayouts.size() >= 4,
            "Medium connector edge/position layout varies across seeds");
        require(faults.size() >= 4,
            "Medium seed sample varies actual fault identity");
    }

    private static boolean everyDecisionOwnerIsActual(BoundedAssemblyPlan plan) {
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
            if (!found) return false;
        }
        return true;
    }

    /**
     * The initial routed construction may consume its job-local physical
     * declarations once, while the retained layout must still construct a
     * fresh board/runtime owner for each diagnostic hypothesis.
     */
    private static void verifyInitialMetadataHandoffKeepsReplayFresh() {
        CirSim previous = CircuitElm.sim;
        CirSim simulator = new CirSim();
        simulator.gridSize = 16; simulator.gridMask = ~15; simulator.gridRound = 7;
        CircuitElm.sim = simulator;
        GeneratedBoardInstance initial = null;
        GeneratedBoardInstance replay = null;
        try {
            BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(2L));
            BoundedGeneratedBoardAssembler.LayoutSession session =
                BoundedGeneratedBoardAssembler.beginLayout(plan);
            while (!session.advance()) { }
            BoundedGeneratedBoardAssembler.PreparedLayout layout = session.result();
            PhysicalConstructionMetadata metadata = session.initialMetadata();
            initial = BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan, layout,
                metadata).getInstance();
            require(initial.getBoard() == metadata.getBoard() &&
                    initial.getPcbLayout() != null,
                "initial controlled construction consumes its routed physical metadata");

            String target = plan.getDecisionOwners().get(plan.getFaultDecisionKey());
            replay = BoundedGeneratedBoardAssembler.assembleForDiagnosticProof(
                plan.getRequest(), target, layout).getInstance();
            require(replay != initial && replay.getBoard() != initial.getBoard() &&
                    replay.getPcbLayout().geometryFingerprint().equals(
                        initial.getPcbLayout().geometryFingerprint()) &&
                    target.equals(replay.getFaultBinding().getFault().getTargetComponentId()),
                "diagnostic replay retains a fresh owner with the exact routed geometry");
        } finally {
            if (initial != null)
                for (CircuitElm element : initial.getSimulationElements()) element.delete();
            if (replay != null)
                for (CircuitElm element : replay.getSimulationElements()) element.delete();
            CircuitElm.sim = previous;
        }
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
        for (ControlledIndicatorChannel channel : first.getChannels()) {
            String key = channel.getLoadKey();
            require(first.getBlocks().get(key).getResolvedValueRecipe()
                    .getCatalogEntryId().equals(second.getBlocks().get(key)
                    .getResolvedValueRecipe().getCatalogEntryId()) &&
                    first.getBlocks().get(key).getResolvedValueRecipe()
                    .getCatalogEntryId().equals(replay.getBlocks().get(key)
                    .getResolvedValueRecipe().getCatalogEntryId()),
                    "reordered and replayed requests preserve each channel value");
        }
    }

    private static void verifyUntypedOpenDrainRejected() {
        BoundedAssemblyRequest request =
                BoundedAssemblyRequest.forControlledIndicator(0L);
        ArrayList<ElectricalConnection> connections =
                new ArrayList<ElectricalConnection>(request.getConnections());
        String switchedId = BoundedAssemblyRequest.controlledChannels().get(0)
                .getSwitchedJoinId();
        for (int index = 0; index < connections.size(); index++) {
            ElectricalConnection connection = connections.get(index);
            if (switchedId.equals(connection.getId())) {
                connections.set(index, new ElectricalConnection(connection.getId(),
                        connection.getPorts()));
                break;
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
        ControlledIndicatorChannel channel = plan.getChannels().get(0);
        String wrongTarget = plan.idFor(channel.getDriverKey(),
                EntityKind.COMPONENT, "RPD");
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
            { "blocks=~", "blocks=6:6" },
            { "components=~", "components=16:16" },
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
                .replace("blocks=~", "blocks=5:5")
                .replace("components=~", "components=15:15")
                .replace("domains=~", "domains=1:1"));
        require(BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(exact))
                .isControlledIndicator(), "supported exact constraints honored");
    }

    private static void verifyDiagnosticPadIdentity() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
                BoundedAssemblyRequest.forControlledIndicator(0L));
        ControlledIndicatorChannel channel = plan.getChannels().get(0);
        String terminal = plan.getBlocks().get(channel.getDriverKey()).getDescriptor()
                .getComponents().get("Q1").getTerminalIds().get(0);
        String pad = plan.idFor(channel.getDriverKey(), EntityKind.PAD,
                "Q1." + terminal);
        require(diagnosticPlan("CHECK", pad, pad).getReferenceTargetId().equals(pad),
                "qualified semantic pad survives diagnostic construction");
        require(diagnosticPlan("CHECK", "J1.2", "Q1." + terminal)
                .getReferenceTargetId().equals("J1.2"),
                "local diagnostic pad remains supported");
        String[] invalid = { pad + "/extra", "PRIVATE_Q1." + terminal };
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
