package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * The one device-owned assembly boundary for the Task 47 canary.
 *
 * <p>Resolution is delegated to {@link BoundedAssemblyPlan}; this class then
 * allocates one complete board graph and one physical runtime in a private
 * context.  It intentionally does not call a leaf family generator.  The
 * returned instance can be published by the already accepted fresh-owner
 * installation boundary.</p>
 */
final class BoundedGeneratedBoardAssembler {
    static final String FAMILY_ID = ComposedResistiveDeviceBehavior.FAMILY_ID;
    static final String TOPOLOGY_VARIANT_ID =
        ComposedResistiveDeviceBehavior.TOPOLOGY_VARIANT_ID;

    private static final String BOARD_ID = "RESISTIVE_COUPLING";
    private static final String POWER_INPUT_ID = "VIN_INPUT";
    private static final double SUPPLY_VOLTAGE = 5.0;
    private static final SeededPcbLayoutGenerator PCB_LAYOUT_GENERATOR =
        new SeededPcbLayoutGenerator(SeededPcbLayoutGenerator.LEGACY_VERSION);

    private BoundedGeneratedBoardAssembler() { }

    /** Meaningful private-construction boundaries exposed only to developer proofs. */
    enum Stage {
        MAPPING,
        MERGE,
        SECOND_BLOCK,
        REGISTRATION,
        VALIDATION
    }

    /** Optional fail-closed probe used by the bounded construction verifier. */
    interface FailureProbe {
        void after(Stage stage);
    }

    /**
     * Assemble a candidate without touching the current simulator owner.
     */
    static Result assemble(BoundedAssemblyRequest request) {
        return assemble(request, null);
    }

    /**
     * Assemble a normal diagnostic candidate with an explicit physical fault
     * owner.  Task 41 uses this route to evaluate each admitted owner while
     * retaining the same composition root and graph/runtime boundary.
     */
    static Result assembleForDiagnosticProof(BoundedAssemblyRequest request,
            String qualifiedTargetComponentId) {
        return assembleResolved(request, qualifiedTargetComponentId, null);
    }

    /** Convenience entry for focused developer tests. */
    static Result assemble(long seed) {
        return assemble(BoundedAssemblyRequest.forCanary(seed));
    }

    /**
     * Assemble a request and invoke the supplied probe after every complete
     * private stage.  Pure request failures propagate before a mutable context
     * exists; failures after allocation are reported with a cleanup receipt.
     */
    static Result assemble(BoundedAssemblyRequest request, FailureProbe probe) {
        return assembleResolved(request, null, probe);
    }

    private static Result assembleResolved(BoundedAssemblyRequest request,
            String qualifiedTargetComponentId, FailureProbe probe) {
        // No mutable owner is allocated before this pure resolution step.
        BoundedAssemblyPlan plan = qualifiedTargetComponentId == null ?
            BoundedAssemblyPlan.resolve(request) :
            BoundedAssemblyPlan.resolveForDiagnosticFault(request,
                qualifiedTargetComponentId);
        Context context = new Context(plan, probe);
        try {
            context.begin(Stage.MAPPING);
            context.buildBoardAndSpecifications();
            context.prepareLogicalMapping();
            context.after(Stage.MAPPING);

            context.begin(Stage.MERGE);
            context.buildPrimaryCircuit();
            context.after(Stage.MERGE);

            context.begin(Stage.SECOND_BLOCK);
            context.buildSecondaryCircuit();
            context.bindMappingsAndLayout();
            context.after(Stage.SECOND_BLOCK);

            context.begin(Stage.REGISTRATION);
            context.buildPhysicalRuntime();
            context.after(Stage.REGISTRATION);

            context.begin(Stage.VALIDATION);
            context.buildInstance();
            context.after(Stage.VALIDATION);
            return context.result();
        } catch (Throwable failure) {
            boolean cleanupSucceeded = context.dispose();
            throw new AssemblyFailure(context.getCurrentStage(), context.allocatedElementCount(),
                context.registeredPartCount(), context.mappedIdentityCount,
                context.mergeCount(), cleanupSucceeded, failure);
        }
    }

    /** Publish a prepared candidate through the accepted fresh-owner seam. */
    static Result install(CirSim sim, BoundedAssemblyRequest request,
            boolean attachWorkbench) {
        Result result = assemble(request);
        FreshGeneratedRuntimeInstallation.installComposition(sim, result.getInstance(),
            attachWorkbench);
        return result;
    }

    /** Stable qualified component identity used by device behavior and proofs. */
    static String qualifiedComponent(GeneratedBoardInstance instance, String block) {
        if (instance == null)
            throw new IllegalArgumentException("Missing composed board instance");
        return BoundedGeneratedBoardAssembler.componentId(instance.getCircuitFamilyId(),
            instance.getSeed(), block);
    }

    /** Stable qualified pad identity used by device behavior and proofs. */
    static String qualifiedPad(GeneratedBoardInstance instance, String block,
            int terminal) {
        if (instance == null)
            throw new IllegalArgumentException("Missing composed board instance");
        return BoundedGeneratedBoardAssembler.padId(instance.getCircuitFamilyId(),
            instance.getSeed(), block, terminal);
    }

    private static String componentId(String ignoredFamily, long ignoredSeed,
            String block) {
        if (ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(ignoredFamily)) {
            String local = ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(block) ?
                "RG" : ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(block) ?
                "RLOAD" : block;
            return ControlledIndicatorBlockContributions.componentId(
                ControlledIndicatorBlockContributions.namespace(), block, local);
        }
        return "tsj-block-v1/resistive-coupling@1/" + block +
            "/component/R1";
    }

    private static String padId(String ignoredFamily, long ignoredSeed,
            String block, int terminal) {
        if (terminal < 1 || terminal > 2)
            throw new IllegalArgumentException("Invalid composed resistor terminal");
        if (ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(ignoredFamily)) {
            String local = ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(block) ?
                "RG" : ControlledIndicatorBlockContributions.LOAD_BLOCK_KEY.equals(block) ?
                "RLOAD" : block;
            return ControlledIndicatorBlockContributions.padId(
                ControlledIndicatorBlockContributions.namespace(), block, local + "." + terminal);
        }
        return "tsj-block-v1/resistive-coupling@1/" + block +
            "/pad/R1." + terminal;
    }

    /** Failure receipt preserving the original exception and private cleanup outcome. */
    static final class AssemblyFailure extends IllegalStateException {
        private final Stage stage;
        private final int allocatedElementCount;
        private final int registeredPartCount;
        private final int mappedIdentityCount;
        private final int mergeCount;
        private final boolean cleanupSucceeded;

        AssemblyFailure(Stage stage, int allocatedElementCount, int registeredPartCount,
                int mappedIdentityCount, int mergeCount, boolean cleanupSucceeded,
                Throwable cause) {
            super("Bounded assembly failed" +
                (stage == null ? "" : " after " + stage) + ": " +
                (cause == null ? "unknown failure" : cause.getMessage()), cause);
            this.stage = stage;
            this.allocatedElementCount = allocatedElementCount;
            this.registeredPartCount = registeredPartCount;
            this.mappedIdentityCount = mappedIdentityCount;
            this.mergeCount = mergeCount;
            this.cleanupSucceeded = cleanupSucceeded;
        }

        Stage getStage() { return stage; }
        Stage getFailureStage() { return stage; }
        int getAllocatedElementCount() { return allocatedElementCount; }
        int getRegisteredPartCount() { return registeredPartCount; }
        int getMappedIdentityCount() { return mappedIdentityCount; }
        int getMergeCount() { return mergeCount; }
        boolean isCleanupSucceeded() { return cleanupSucceeded; }
        boolean getCleanupSucceeded() { return cleanupSucceeded; }
        Throwable getOriginalFailure() { return getCause(); }
    }

    /** Successful immutable assembly result and its developer-readable mapping receipt. */
    static final class Result {
        private final GeneratedBoardInstance instance;
        private final BoundedAssemblyPlan plan;
        private final Map<String, RuntimeTarget> runtimeTargets;
        private final Map<String, EndpointManifest> endpointManifest;

        Result(GeneratedBoardInstance instance, BoundedAssemblyPlan plan,
                Map<String, RuntimeTarget> runtimeTargets,
                Map<String, EndpointManifest> endpointManifest) {
            if (instance == null || plan == null || runtimeTargets == null ||
                    endpointManifest == null)
                throw new IllegalArgumentException("Incomplete bounded assembly result");
            this.instance = instance;
            this.plan = plan;
            this.runtimeTargets = Collections.unmodifiableMap(
                new TreeMap<String, RuntimeTarget>(runtimeTargets));
            this.endpointManifest = Collections.unmodifiableMap(
                new TreeMap<String, EndpointManifest>(endpointManifest));
        }

        GeneratedBoardInstance getInstance() { return instance; }
        BoundedAssemblyPlan getPlan() { return plan; }
        Map<String, RuntimeTarget> getRuntimeTargets() { return runtimeTargets; }
        Map<String, RuntimeTarget> getMappingReceipt() { return runtimeTargets; }
        Map<String, EndpointManifest> getEndpointManifest() { return endpointManifest; }
        Map<String, EndpointManifest> getChosenEndpointManifest() { return endpointManifest; }
        Map<String, EndpointManifest> getEndpointMappings() { return endpointManifest; }
    }

    /** Immutable physical/fault/repair ownership record; IDs resolve through the runtime. */
    static final class RuntimeTarget {
        private final String blockKey;
        private final String qualifiedComponentId;
        private final String slotId;
        private final String originalPartId;
        private final String inventoryViewId;
        private final String capabilityId;
        private final String providerId;
        private final String faultId;
        private final String repairComponentId;
        private final String repairSlotId;

        RuntimeTarget(String blockKey, String qualifiedComponentId, String slotId,
                String originalPartId, String inventoryViewId, String capabilityId,
                String providerId, String faultId, String repairComponentId,
                String repairSlotId) {
            this.blockKey = required(blockKey, "blockKey");
            this.qualifiedComponentId = required(qualifiedComponentId, "componentId");
            this.slotId = required(slotId, "slotId");
            this.originalPartId = required(originalPartId, "originalPartId");
            this.inventoryViewId = required(inventoryViewId, "inventoryViewId");
            this.capabilityId = required(capabilityId, "capabilityId");
            this.providerId = required(providerId, "providerId");
            this.faultId = required(faultId, "faultId");
            this.repairComponentId = required(repairComponentId, "repairComponentId");
            this.repairSlotId = required(repairSlotId, "repairSlotId");
        }

        String getBlockKey() { return blockKey; }
        String getQualifiedComponentId() { return qualifiedComponentId; }
        String getComponentId() { return qualifiedComponentId; }
        String getSlotId() { return slotId; }
        String getOriginalPartId() { return originalPartId; }
        String getInventoryViewId() { return inventoryViewId; }
        String getInventoryId() { return inventoryViewId; }
        String getCapabilityId() { return capabilityId; }
        String getProviderId() { return providerId; }
        String getFaultId() { return faultId; }
        String getRepairComponentId() { return repairComponentId; }
        String getRepairTargetComponentId() { return repairComponentId; }
        String getRepairSlotId() { return repairSlotId; }
        String getRepairTargetSlotId() { return repairSlotId; }
    }

    /** Immutable record of a chosen real board endpoint and its actual CircuitJS post. */
    static final class EndpointManifest {
        private final String key;
        private final String blockKey;
        private final String localEndpointId;
        private final String qualifiedPadId;
        private final String netId;
        private final CircuitElm element;
        private final int postIndex;

        EndpointManifest(String key, String blockKey, String localEndpointId,
                String qualifiedPadId, String netId, CircuitElm element, int postIndex) {
            this.key = required(key, "endpoint key");
            this.blockKey = required(blockKey, "endpoint block");
            this.localEndpointId = required(localEndpointId, "local endpoint");
            this.qualifiedPadId = required(qualifiedPadId, "qualified pad");
            this.netId = required(netId, "endpoint net");
            if (element == null || postIndex < 0 || postIndex >= element.getPostCount())
                throw new IllegalArgumentException("Invalid endpoint manifest post");
            this.element = element;
            this.postIndex = postIndex;
        }

        String getKey() { return key; }
        String getBlockKey() { return blockKey; }
        String getLocalEndpointId() { return localEndpointId; }
        String getQualifiedPadId() { return qualifiedPadId; }
        String getPadId() { return qualifiedPadId; }
        String getNetId() { return netId; }
        CircuitElm getElement() { return element; }
        String getElementType() { return element.getClass().getSimpleName(); }
        String getElementClassName() { return element.getClass().getName(); }
        int getPostIndex() { return postIndex; }
        CircuitPostMeasurementEndpoint asEndpoint() {
            return new CircuitPostMeasurementEndpoint(element, postIndex);
        }
    }

    private static final class Context {
        private final BoundedAssemblyPlan plan;
        private final FailureProbe probe;
        private TroubleshootBoard board;
        private BoardPhysicalSpecifications specifications;
        private Vector<CircuitElm> elements = new Vector<CircuitElm>();
        private GeneratedComponentBindings componentBindings;
        private GeneratedExternalPowerBindings powerBindings;
        private GeneratedComponentConnectionBindings connectionBindings;
        private PhysicalBoardRuntime runtime;
        private PcbBoardLayout layout;
        private GeneratedBoardInstance instance;
        private final TreeMap<String, RuntimeTarget> runtimeTargets =
            new TreeMap<String, RuntimeTarget>();
        private final TreeMap<String, EndpointManifest> endpointManifest =
            new TreeMap<String, EndpointManifest>();
        private int mappedIdentityCount;
        private Stage currentStage;

        Context(BoundedAssemblyPlan plan, FailureProbe probe) {
            this.plan = plan;
            this.probe = probe;
        }

        void buildBoardAndSpecifications() {
            if (plan.isControlledIndicator()) {
                buildControlledBoardAndSpecifications();
                return;
            }
            board = new TroubleshootBoard(BOARD_ID);
            String sourceSupply = plan.netFor("source", "SUPPLY");
            String output = plan.netFor("source", "OUT");
            String returned = plan.netFor("source", "RETURN");
            board.addNet(new BoardNet(sourceSupply));
            board.addNet(new BoardNet(output));
            board.addNet(new BoardNet(returned));

            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            board.addComponent(new BoardComponent("J1", "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
            board.addComponent(new BoardComponent(sourceComponent, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));
            board.addComponent(new BoardComponent(loadComponent, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));

            board.addPad(new BoardPad("J1.1", "J1", "1", sourceSupply));
            board.addPad(new BoardPad("J1.2", "J1", "2", returned));
            board.addPad(new BoardPad(plan.idFor("source", EntityKind.PAD, "R1.1"),
                sourceComponent, "1", sourceSupply));
            board.addPad(new BoardPad(plan.idFor("source", EntityKind.PAD, "R1.2"),
                sourceComponent, "2", output));
            board.addPad(new BoardPad(plan.idFor("load", EntityKind.PAD, "R1.1"),
                loadComponent, "1", output));
            board.addPad(new BoardPad(plan.idFor("load", EntityKind.PAD, "R1.2"),
                loadComponent, "2", returned));
            board.addPowerInput(new ExternalBoardPowerInput(POWER_INPUT_ID, "J1.1", "J1.2",
                sourceSupply, returned));
            board.validate();

            specifications = new BoardPhysicalSpecifications();
            specifications.addPhysicalDefinition("J1",
                new BasicPhysicalSpecification("J1_CONNECTOR"),
                new PhysicalNameplate("J1", "Power input connector"),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
            for (String block : new String[] { "source", "load" }) {
                ComposedBlockContribution contribution = plan.getBlocks().get(block);
                String componentId = plan.idFor(block, EntityKind.COMPONENT, "R1");
                ResistorNameplate resistor = new ResistorNameplate(componentId,
                    contribution.getResistanceOhms(), 5.0,
                    contribution.getRatedWatts());
                specifications.addPhysicalDefinition(componentId, resistor,
                    new PhysicalNameplate(componentId, "Physical resistor markings",
                        "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
            }
            specifications.addPowerInputNameplate(new PowerInputNameplate(POWER_INPUT_ID,
                SUPPLY_VOLTAGE));
        }

        private void buildControlledBoardAndSpecifications() {
            board = new TroubleshootBoard(ControlledIndicatorDeviceBehavior.FAMILY_ID);
            ComposedBlockContribution loadContribution = plan.getLoad();
            ComposedBlockContribution.ResistorRecipe loadRecipe =
                loadContribution == null ? null : loadContribution.getResistor("RLOAD");
            if (loadRecipe == null)
                throw new IllegalStateException("Controlled load has no resolved resistor recipe");
            if (plan.isControlledIndicatorValues() &&
                    (!ControlledIndicatorValueSynthesis.PACKAGE_ID.equals(
                        plan.getResolvedLoadRecipe().getPackageId()) ||
                     !PhysicalPackages.AXIAL_RESISTOR.getId().equals(
                        plan.getResolvedLoadRecipe().getPackageId())))
                throw new IllegalStateException("Resolved controlled load package is not axial");
            String loadSupply = plan.netFor("load", "SUPPLY");
            String control = plan.netFor("driver", "CONTROL");
            String loadNode = plan.netFor("load", "LED_NODE");
            String switched = plan.netFor("driver", "SWITCHED_SINK");
            String gate = plan.netFor("driver", "GATE");
            String returned = plan.netFor("driver", "RETURN");
            addNet(loadSupply);
            addNet(control);
            addNet(loadNode);
            addNet(switched);
            addNet(gate);
            addNet(returned);

            String rg = plan.idFor("driver", EntityKind.COMPONENT, "RG");
            String rpd = plan.idFor("driver", EntityKind.COMPONENT, "RPD");
            String q1 = plan.idFor("driver", EntityKind.COMPONENT, "Q1");
            String rload = plan.idFor("load", EntityKind.COMPONENT, "RLOAD");
            String led1 = plan.idFor("load", EntityKind.COMPONENT, "LED1");
            String j1 = plan.idFor("power-adapter", EntityKind.COMPONENT, "J1");
            String j2 = plan.idFor("control-adapter", EntityKind.COMPONENT, "J2");
            board.addComponent(new BoardComponent(rg, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR, "RG"));
            board.addComponent(new BoardComponent(rpd, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR, "RPD"));
            board.addComponent(new BoardComponent(q1, "NMOS_TRANSISTOR",
                PhysicalPackages.TO92_NMOS, "Q1"));
            board.addComponent(new BoardComponent(rload, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR, "RLOAD"));
            board.addComponent(new BoardComponent(led1, "LED",
                PhysicalPackages.THROUGH_HOLE_LED, "LED1"));
            board.addComponent(new BoardComponent(j1, "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, "J1"));
            board.addComponent(new BoardComponent(j2, "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, "J2"));

            addPad("power-adapter", "J1.1", j1, "1", loadSupply);
            addPad("power-adapter", "J1.2", j1, "2", returned);
            addPad("control-adapter", "J2.1", j2, "1", control);
            addPad("control-adapter", "J2.2", j2, "2", returned);
            addPad("driver", "RG.1", rg, "1", control);
            addPad("driver", "RG.2", rg, "2", gate);
            addPad("driver", "RPD.1", rpd, "1", gate);
            addPad("driver", "RPD.2", rpd, "2", returned);
            addPad("driver", "Q1.G", q1, "G", gate);
            addPad("driver", "Q1.D", q1, "D", switched);
            addPad("driver", "Q1.S", q1, "S", returned);
            addPad("load", "RLOAD.1", rload, "1", loadSupply);
            addPad("load", "RLOAD.2", rload, "2", loadNode);
            addPad("load", "LED1.A", led1, "A", loadNode);
            addPad("load", "LED1.K", led1, "K", switched);

            String j11 = plan.idFor("power-adapter", EntityKind.PAD, "J1.1");
            String j12 = plan.idFor("power-adapter", EntityKind.PAD, "J1.2");
            String j21 = plan.idFor("control-adapter", EntityKind.PAD, "J2.1");
            String j22 = plan.idFor("control-adapter", EntityKind.PAD, "J2.2");
            board.addPowerInput(new ExternalBoardPowerInput(
                ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID, j11, j12,
                loadSupply, returned));
            board.addPowerInput(new ExternalBoardPowerInput(
                ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID, j21, j22,
                control, returned));
            board.validate();

            specifications = new BoardPhysicalSpecifications();
            specifications.addPhysicalDefinition(j1,
                new BasicPhysicalSpecification("J1_CONNECTOR"),
                new PhysicalNameplate("J1", "Load supply connector"),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
            specifications.addPhysicalDefinition(j2,
                new BasicPhysicalSpecification("J2_CONNECTOR"),
                new PhysicalNameplate("J2", "Control input connector"),
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
            specifications.addPhysicalDefinition(rg,
                new ResistorNameplate(rg, ControlledIndicatorBlockContributions.RG_OHMS,
                    SUPPLY_VOLTAGE, ComposedBlockContribution.RATED_WATTS),
                new PhysicalNameplate("RG", "Gate drive resistor markings",
                    "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
            specifications.addPhysicalDefinition(rpd,
                new ResistorNameplate(rpd, ControlledIndicatorBlockContributions.RPD_OHMS,
                    SUPPLY_VOLTAGE, ComposedBlockContribution.RATED_WATTS),
                new PhysicalNameplate("RPD", "Gate pull-down resistor markings",
                    "Markings", "Color bands"), PhysicalPackages.AXIAL_RESISTOR);
            specifications.addPhysicalDefinition(q1,
                new NmosSpecification(q1, 1.5, 10.0),
                new PhysicalNameplate("Q1", "N-channel MOSFET", "Part",
                    "N-channel MOSFET"), PhysicalPackages.TO92_NMOS);
            specifications.addPhysicalDefinition(rload,
                new ResistorNameplate(rload, loadRecipe.getResistanceOhms(),
                    loadRecipe.getTolerancePercent(), loadRecipe.getRatedWatts()),
                plan.isControlledIndicatorValues() ?
                    loadContribution.getResolvedValueRecipe().getPlayerVisibleNameplate()
                        .forPhysicalPartId("RLOAD") :
                    new PhysicalNameplate("RLOAD", "Load resistor markings",
                        "Markings", "Color bands"),
                PhysicalPackages.AXIAL_RESISTOR);
            specifications.addPhysicalDefinition(led1,
                new LedNameplate(led1, "Generic red LED", "default-led", 1, 0, 0),
                new PhysicalNameplate("LED1", "Generic red LED"),
                PhysicalPackages.THROUGH_HOLE_LED);
            specifications.addPowerInputNameplate(new PowerInputNameplate(
                ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID, SUPPLY_VOLTAGE));
            specifications.addPowerInputNameplate(new PowerInputNameplate(
                ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID, SUPPLY_VOLTAGE));
        }

        private void addNet(String netId) {
            if (board.getNet(netId) == null)
                board.addNet(new BoardNet(netId));
        }

        private void addPad(String blockKey, String localPadId, String componentId,
                String terminalId, String netId) {
            board.addPad(new BoardPad(plan.idFor(blockKey, EntityKind.PAD, localPadId),
                componentId, terminalId, netId));
        }

        /** Complete the pure logical namespace mapping before graph allocation. */
        void prepareLogicalMapping() {
            if (plan.isControlledIndicator()) {
                prepareControlledLogicalMapping();
                return;
            }
            mappedIdentityCount = board.getComponentIds().size() + board.getPadIds().size() +
                board.getNetIds().size();
            for (String block : new String[] { "source", "load" }) {
                // Resolve every identity that the later graph/physical stages
                // will consume while the operation is still pure.
                plan.idFor(block, EntityKind.COMPONENT, "R1");
                plan.idFor(block, EntityKind.PAD, "R1.1");
                plan.idFor(block, EntityKind.PAD, "R1.2");
                plan.idFor(block, EntityKind.ENDPOINT, "R1_1");
                plan.idFor(block, EntityKind.ENDPOINT, "R1_2");
                plan.netFor(block, "RETURN");
            }
            plan.netFor("source", "SUPPLY");
            plan.netFor("source", "OUT");
        }

        private void prepareControlledLogicalMapping() {
            mappedIdentityCount = board.getComponentIds().size() + board.getPadIds().size() +
                board.getNetIds().size();
            for (String block : new String[] { "driver", "load" }) {
                ComposedBlockContribution contribution = plan.getBlocks().get(block);
                if (contribution == null)
                    throw new IllegalStateException("Missing controlled block mapping: " + block);
                for (String local : contribution.getResistors().keySet()) {
                    plan.idFor(block, EntityKind.COMPONENT, local);
                    ComposedBlockContribution.ResistorRecipe recipe =
                        contribution.getResistor(local);
                    plan.idFor(block, EntityKind.PAD, recipe.getFirstPadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getSecondPadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getFirstEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getSecondEndpointLocalId());
                }
                for (ComposedBlockContribution.NmosRecipe recipe :
                        contribution.getNmosRecipes().values()) {
                    plan.idFor(block, EntityKind.COMPONENT, recipe.getComponentLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getGatePadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getDrainPadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getSourcePadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getGateEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getDrainEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getSourceEndpointLocalId());
                }
                for (ComposedBlockContribution.LedRecipe recipe :
                        contribution.getLedRecipes().values()) {
                    plan.idFor(block, EntityKind.COMPONENT, recipe.getComponentLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getAnodePadLocalId());
                    plan.idFor(block, EntityKind.PAD, recipe.getCathodePadLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getAnodeEndpointLocalId());
                    plan.idFor(block, EntityKind.ENDPOINT, recipe.getCathodeEndpointLocalId());
                }
                for (String net : contribution.getDescriptor().getNetIds())
                    plan.netFor(block, net);
            }
            for (DeviceAdapterContract adapter : plan.getDeviceAdapters()) {
                plan.idFor(adapter.getKey(), EntityKind.COMPONENT,
                    adapter.getComponentLocalId());
                plan.idFor(adapter.getKey(), EntityKind.PAD,
                    adapter.getComponentLocalId() + ".1");
                plan.idFor(adapter.getKey(), EntityKind.PAD,
                    adapter.getComponentLocalId() + ".2");
                plan.idFor(adapter.getKey(), EntityKind.ENDPOINT,
                    adapter.getComponentLocalId() + "_1");
                plan.idFor(adapter.getKey(), EntityKind.ENDPOINT,
                    adapter.getComponentLocalId() + "_2");
                for (String net : adapter.getDescriptor().getNetIds())
                    plan.netFor(adapter.getKey(), net);
            }
            for (String block : new String[] { "driver", "load" })
                for (String localNet : plan.getBlocks().get(block).getDescriptor().getNetIds())
                    plan.netFor(block, localNet);
        }

        /** Allocate the source half and the explicit output merge point. */
        void buildPrimaryCircuit() {
            if (plan.isControlledIndicator()) {
                buildControlledCircuit();
                return;
            }
            double sourceResistance = plan.getBlocks().get("source").getResistanceOhms();

            // Add each element immediately after construction.  A later drag,
            // parameter write, or binding failure therefore still leaves the
            // private candidate's cleanup list complete.
            DCVoltageElm supply = new DCVoltageElm(snap(100), snap(320));
            add(supply);
            supply.drag(100, 160);
            supply.maxVoltage = SUPPLY_VOLTAGE;
            SwitchElm isolation = new SwitchElm(snap(100), snap(160));
            add(isolation);
            isolation.drag(180, 160);
            SwitchElm connector = new SwitchElm(snap(180), snap(160));
            add(connector);
            connector.drag(220, 160);
            WireElm supplyTrace = wire(220, 160, 230, 160);
            WireElm sourceFirstAttachment = wire(230, 160, 260, 160);
            ResistorElm sourceResistor = resistor(260, 160, 340, 160, sourceResistance);
            ResistorSecondaryOpenPath sourceSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(sourceResistor, 1));
            add(sourceSecondary.getSimulationElement());
            WireElm sourceSecondAttachment = wire(372, 160, 420, 160);

            // The output trace is the actual private merge boundary.  The
            // load half is deliberately allocated only after the MERGE hook.
            WireElm outputTrace = wire(420, 160, 500, 160);

            componentBindings = new GeneratedComponentBindings(board);
            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            componentBindings.bindComponent(sourceComponent, sourceResistor);
            componentBindings.bindAuxiliaryComponentElement(sourceComponent,
                sourceSecondary.getSimulationElement());

            powerBindings = new GeneratedExternalPowerBindings(board);
            Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
            powerElements.add(supply);
            powerElements.add(isolation);
            powerBindings.bindPowerInput(POWER_INPUT_ID,
                new ExternalPowerSimulationBinding(powerElements,
                    new SwitchExternalPowerControl(isolation)));

            this.connectionBindings = new GeneratedComponentConnectionBindings(board);
            BoardSimulationBindings bindings = board.getSimulationBindings();
            bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(connector, 1));
            String sourcePad1 = plan.idFor("source", EntityKind.PAD, "R1.1");
            String sourcePad2 = plan.idFor("source", EntityKind.PAD, "R1.2");
            bindings.bindPad(sourcePad1, new CircuitPostMeasurementEndpoint(supplyTrace, 1));
            bindings.bindPad(sourcePad2, new CircuitPostMeasurementEndpoint(outputTrace, 0));
            connectionBindings.bind(sourceComponent, sourcePad1,
                bindings.getEndpoint(sourcePad1), new CircuitPostMeasurementEndpoint(sourceResistor, 0),
                sourceFirstAttachment);
            connectionBindings.bind(sourceComponent, sourcePad2,
                bindings.getEndpoint(sourcePad2), sourceSecondary.getPublicTerminal(),
                sourceSecondAttachment);

            this.sourceResistor = sourceResistor;
            this.sourceSecondary = sourceSecondary;
            this.sourceFirstAttachment = sourceFirstAttachment;
            this.sourceSecondAttachment = sourceSecondAttachment;
            this.connector = connector;
            this.supply = supply;
            this.isolation = isolation;
            this.supplyTrace = supplyTrace;
            this.outputTrace = outputTrace;
        }

        /** Allocate the complete controlled-indicator graph in this context. */
        private void buildControlledCircuit() {
            // Load branch: source -> RLOAD -> LED -> NMOS drain.
            controlledLoadSupply = new DCVoltageElm(snap(112), snap(416));
            add(controlledLoadSupply);
            controlledLoadSupply.drag(snap(112), snap(176));
            controlledLoadSupply.maxVoltage = ControlledIndicatorDeviceBehavior.SUPPLY_VOLTAGE;
            controlledLoadIsolation = new SwitchElm(snap(112), snap(176));
            add(controlledLoadIsolation);
            controlledLoadIsolation.drag(snap(192), snap(176));
            controlledLoadConnector = new SwitchElm(snap(192), snap(176));
            add(controlledLoadConnector);
            controlledLoadConnector.drag(snap(224), snap(176));
            controlledLoadInputTrace = wire(224, 176, 280, 176);
            controlledLoadFirstAttachment = wire(280, 176, 340, 176);
            controlledRload = resistor(340, 176, 420, 176,
                plan.getLoad().getResistor("RLOAD").getResistanceOhms());
            controlledRloadFaultSwitch = new SwitchElm(
                controlledRload.getPost(1).x, controlledRload.getPost(1).y);
            add(controlledRloadFaultSwitch);
            controlledRloadFaultSwitch.drag(
                controlledRload.getPost(1).x + 32,
                controlledRload.getPost(1).y);
            controlledRloadSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(controlledRloadFaultSwitch, 1));
            add(controlledRloadSecondary.getSimulationElement());
            Point rloadPublic = controlledRloadSecondary.getPublicTerminal()
                .getElement().getPost(controlledRloadSecondary.getPublicTerminal().getPostIndex());
            controlledRloadSecondAttachment = wire(rloadPublic.x, rloadPublic.y,
                540, 176);
            controlledLoadNodeTrace = wire(540, 176, 620, 176);
            controlledLed = new LEDElm(snap(620), snap(176));
            add(controlledLed);
            controlledLed.drag(snap(620), snap(256));
            controlledLed.modelName = "default-led";
            controlledLed.setup();
            controlledLed.colorR = 1;
            controlledLed.colorG = 0;
            controlledLed.colorB = 0;

            // Control branch: source -> command -> RG -> gate, with a real
            // RPD from that gate node to the shared return.
            controlledControlSupply = new DCVoltageElm(snap(112), snap(496));
            add(controlledControlSupply);
            controlledControlSupply.drag(snap(112), snap(96));
            controlledControlSupply.maxVoltage = ControlledIndicatorDeviceBehavior.SUPPLY_VOLTAGE;
            controlledControlIsolation = new SwitchElm(snap(112), snap(96));
            add(controlledControlIsolation);
            controlledControlIsolation.drag(snap(192), snap(96));
            controlledControlInputTrace = wire(192, 96, 240, 96);
            controlledControlCommand = new SwitchElm(snap(240), snap(96));
            add(controlledControlCommand);
            controlledControlCommand.drag(snap(272), snap(96));
            controlledControlBoardTrace = wire(272, 96, 368, 96);
            controlledRgFirstAttachment = wire(368, 96, 432, 96);
            controlledRg = resistor(432, 96, 512, 96,
                ControlledIndicatorBlockContributions.RG_OHMS);
            controlledRgFaultSwitch = new SwitchElm(
                controlledRg.getPost(1).x, controlledRg.getPost(1).y);
            add(controlledRgFaultSwitch);
            controlledRgFaultSwitch.drag(controlledRg.getPost(1).x + 32,
                controlledRg.getPost(1).y);
            controlledRgSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(controlledRgFaultSwitch, 1));
            add(controlledRgSecondary.getSimulationElement());

            controlledQ1 = new NMosfetElm(snap(720), snap(288));
            add(controlledQ1);
            controlledQ1.drag(snap(800), snap(288));
            controlledQ1.vt = 1.5;
            controlledQ1.beta = 10.0;
            controlledRpd = resistor(640, 96, 640, 176,
                ControlledIndicatorBlockContributions.RPD_OHMS);
            CircuitPostMeasurementEndpoint rgPublicEndpoint =
                controlledRgSecondary.getPublicTerminal();
            Point rgPublic = rgPublicEndpoint.getElement().getPost(
                rgPublicEndpoint.getPostIndex());
            controlledRgSecondAttachment = wire(rgPublic.x, rgPublic.y, 640, 96);
            controlledGateNodeTrace = wire(640, 96,
                controlledQ1.getPost(0).x, controlledQ1.getPost(0).y);
            controlledDrainTrace = wire(controlledLed.getPost(1).x,
                controlledLed.getPost(1).y, controlledQ1.getPost(2).x,
                controlledQ1.getPost(2).y);

            controlledGround = new GroundElm(snap(900), snap(416));
            add(controlledGround);
            controlledGround.drag(snap(900), snap(448));
            controlledLoadReturn = wire(112, 416, 900, 416);
            controlledControlReturn = wire(112, 496, 112, 416);
            controlledPullDownReturn = wire(640, 176, 900, 416);
            controlledSourceReturn = wire(controlledQ1.getPost(1).x,
                controlledQ1.getPost(1).y, 900, 416);

            componentBindings = new GeneratedComponentBindings(board);
            componentBindings.bindComponent(plan.idFor("driver", EntityKind.COMPONENT, "RG"),
                controlledRg);
            componentBindings.bindAuxiliaryComponentElement(
                plan.idFor("driver", EntityKind.COMPONENT, "RG"),
                controlledRgSecondary.getSimulationElement());
            componentBindings.bindComponent(plan.idFor("driver", EntityKind.COMPONENT, "RPD"),
                controlledRpd);
            componentBindings.bindComponent(plan.idFor("driver", EntityKind.COMPONENT, "Q1"),
                controlledQ1);
            componentBindings.bindComponent(plan.idFor("load", EntityKind.COMPONENT, "RLOAD"),
                controlledRload);
            componentBindings.bindAuxiliaryComponentElement(
                plan.idFor("load", EntityKind.COMPONENT, "RLOAD"),
                controlledRloadSecondary.getSimulationElement());
            componentBindings.bindComponent(plan.idFor("load", EntityKind.COMPONENT, "LED1"),
                controlledLed);

            powerBindings = new GeneratedExternalPowerBindings(board);
            Vector<CircuitElm> loadPower = new Vector<CircuitElm>();
            loadPower.add(controlledLoadSupply);
            loadPower.add(controlledLoadIsolation);
            powerBindings.bindPowerInput(ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID,
                new ExternalPowerSimulationBinding(loadPower,
                    new SwitchExternalPowerControl(controlledLoadIsolation)));
            Vector<CircuitElm> controlPower = new Vector<CircuitElm>();
            controlPower.add(controlledControlSupply);
            controlPower.add(controlledControlIsolation);
            powerBindings.bindPowerInput(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID,
                new ExternalPowerSimulationBinding(controlPower,
                    new SwitchExternalPowerControl(controlledControlIsolation)));

            BoardSimulationBindings bindings = board.getSimulationBindings();
            bindings.bindPad(plan.idFor("power-adapter", EntityKind.PAD, "J1.1"),
                new CircuitPostMeasurementEndpoint(controlledLoadConnector, 1));
            bindings.bindPad(plan.idFor("power-adapter", EntityKind.PAD, "J1.2"),
                new CircuitPostMeasurementEndpoint(controlledGround, 0));
            bindings.bindPad(plan.idFor("control-adapter", EntityKind.PAD, "J2.1"),
                new CircuitPostMeasurementEndpoint(controlledControlCommand, 1));
            bindings.bindPad(plan.idFor("control-adapter", EntityKind.PAD, "J2.2"),
                new CircuitPostMeasurementEndpoint(controlledGround, 0));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "RG.1"),
                new CircuitPostMeasurementEndpoint(controlledControlBoardTrace, 1));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "RG.2"),
                new CircuitPostMeasurementEndpoint(controlledGateNodeTrace, 0));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "RPD.1"),
                new CircuitPostMeasurementEndpoint(controlledRpd, 0));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "RPD.2"),
                new CircuitPostMeasurementEndpoint(controlledRpd, 1));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "Q1.G"),
                new CircuitPostMeasurementEndpoint(controlledQ1, 0));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "Q1.D"),
                new CircuitPostMeasurementEndpoint(controlledQ1, 2));
            bindings.bindPad(plan.idFor("driver", EntityKind.PAD, "Q1.S"),
                new CircuitPostMeasurementEndpoint(controlledQ1, 1));
            bindings.bindPad(plan.idFor("load", EntityKind.PAD, "RLOAD.1"),
                new CircuitPostMeasurementEndpoint(controlledLoadInputTrace, 1));
            bindings.bindPad(plan.idFor("load", EntityKind.PAD, "RLOAD.2"),
                new CircuitPostMeasurementEndpoint(controlledLoadNodeTrace, 0));
            bindings.bindPad(plan.idFor("load", EntityKind.PAD, "LED1.A"),
                new CircuitPostMeasurementEndpoint(controlledLed, 0));
            bindings.bindPad(plan.idFor("load", EntityKind.PAD, "LED1.K"),
                new CircuitPostMeasurementEndpoint(controlledLed, 1));

            connectionBindings = new GeneratedComponentConnectionBindings(board);
            String rgId = plan.idFor("driver", EntityKind.COMPONENT, "RG");
            String rg1 = plan.idFor("driver", EntityKind.PAD, "RG.1");
            String rg2 = plan.idFor("driver", EntityKind.PAD, "RG.2");
            connectionBindings.bind(rgId, rg1, bindings.getEndpoint(rg1),
                new CircuitPostMeasurementEndpoint(controlledRg, 0),
                controlledRgFirstAttachment);
            connectionBindings.bind(rgId, rg2, bindings.getEndpoint(rg2),
                controlledRgSecondary.getPublicTerminal(), controlledRgSecondAttachment);
            String rloadId = plan.idFor("load", EntityKind.COMPONENT, "RLOAD");
            String rload1 = plan.idFor("load", EntityKind.PAD, "RLOAD.1");
            String rload2 = plan.idFor("load", EntityKind.PAD, "RLOAD.2");
            connectionBindings.bind(rloadId, rload1, bindings.getEndpoint(rload1),
                new CircuitPostMeasurementEndpoint(controlledRload, 0),
                controlledLoadFirstAttachment);
            connectionBindings.bind(rloadId, rload2, bindings.getEndpoint(rload2),
                controlledRloadSecondary.getPublicTerminal(), controlledRloadSecondAttachment);

            this.controlledBuilt = true;
        }

        /** Allocate the load half, complete board endpoint mapping, and route layout. */
        void buildSecondaryCircuit() {
            if (plan.isControlledIndicator()) {
                if (!controlledBuilt)
                    throw new IllegalStateException("Controlled graph was not allocated");
                return;
            }
            double loadResistance = plan.getBlocks().get("load").getResistanceOhms();
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            WireElm loadFirstAttachment = wire(500, 160, 580, 160);
            ResistorElm loadResistor = resistor(580, 160, 660, 160, loadResistance);
            ResistorSecondaryOpenPath loadSecondary = ResistorSecondaryOpenPath.create(
                new CircuitPostMeasurementEndpoint(loadResistor, 1));
            add(loadSecondary.getSimulationElement());
            WireElm loadSecondAttachment = wire(692, 160, 740, 160);
            WireElm returnTrace = wire(740, 160, 740, 320);
            GroundElm ground = new GroundElm(snap(740), snap(320));
            add(ground);
            ground.drag(740, 352);
            WireElm returnBottom = wire(100, 320, 740, 320);

            componentBindings.bindComponent(loadComponent, loadResistor);
            componentBindings.bindAuxiliaryComponentElement(loadComponent,
                loadSecondary.getSimulationElement());

            BoardSimulationBindings bindings = board.getSimulationBindings();
            bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
            String loadPad1 = plan.idFor("load", EntityKind.PAD, "R1.1");
            String loadPad2 = plan.idFor("load", EntityKind.PAD, "R1.2");
            bindings.bindPad(loadPad1, new CircuitPostMeasurementEndpoint(outputTrace, 1));
            bindings.bindPad(loadPad2, new CircuitPostMeasurementEndpoint(returnTrace, 0));
            connectionBindings.bind(loadComponent, loadPad1,
                bindings.getEndpoint(loadPad1), new CircuitPostMeasurementEndpoint(loadResistor, 0),
                loadFirstAttachment);
            connectionBindings.bind(loadComponent, loadPad2,
                bindings.getEndpoint(loadPad2), loadSecondary.getPublicTerminal(),
                loadSecondAttachment);

            this.loadResistor = loadResistor;
            this.loadSecondary = loadSecondary;
            this.loadFirstAttachment = loadFirstAttachment;
            this.loadSecondAttachment = loadSecondAttachment;
            this.ground = ground;
            this.returnTrace = returnTrace;
            // returnBottom is a real owned graph element; retaining it is
            // unnecessary after add(), since disposal walks the complete list.
            if (returnBottom == null)
                throw new IllegalStateException("Missing return path");
        }

        private ResistorElm sourceResistor;
        private ResistorElm loadResistor;
        private ResistorSecondaryOpenPath sourceSecondary;
        private ResistorSecondaryOpenPath loadSecondary;
        private WireElm sourceFirstAttachment;
        private WireElm sourceSecondAttachment;
        private WireElm loadFirstAttachment;
        private WireElm loadSecondAttachment;
        private SwitchElm connector;
        private DCVoltageElm supply;
        private SwitchElm isolation;
        private GroundElm ground;
        private WireElm supplyTrace;
        private WireElm outputTrace;
        private WireElm returnTrace;

        // Controlled-indicator graph and physical identity owners.
        private boolean controlledBuilt;
        private DCVoltageElm controlledLoadSupply;
        private SwitchElm controlledLoadIsolation;
        private SwitchElm controlledLoadConnector;
        private WireElm controlledLoadInputTrace;
        private WireElm controlledLoadFirstAttachment;
        private ResistorElm controlledRload;
        private SwitchElm controlledRloadFaultSwitch;
        private ResistorSecondaryOpenPath controlledRloadSecondary;
        private WireElm controlledRloadSecondAttachment;
        private WireElm controlledLoadNodeTrace;
        private LEDElm controlledLed;
        private WireElm controlledDrainTrace;
        private DCVoltageElm controlledControlSupply;
        private SwitchElm controlledControlIsolation;
        private WireElm controlledControlInputTrace;
        private SwitchElm controlledControlCommand;
        private WireElm controlledControlBoardTrace;
        private WireElm controlledRgFirstAttachment;
        private ResistorElm controlledRg;
        private SwitchElm controlledRgFaultSwitch;
        private ResistorSecondaryOpenPath controlledRgSecondary;
        private WireElm controlledRgSecondAttachment;
        private ResistorElm controlledRpd;
        private NMosfetElm controlledQ1;
        private WireElm controlledGateNodeTrace;
        private GroundElm controlledGround;
        private WireElm controlledLoadReturn;
        private WireElm controlledControlReturn;
        private WireElm controlledPullDownReturn;
        private WireElm controlledSourceReturn;

        void bindMappingsAndLayout() {
            if (plan.isControlledIndicator()) {
                bindControlledMappingsAndLayout();
                return;
            }
            layout = PCB_LAYOUT_GENERATOR.generate(board, plan.getRequest().getDescriptor()
                .getRootSeed());
            layout.validateGeometry(board);
            for (String block : new String[] { "source", "load" }) {
                String componentId = plan.idFor(block, EntityKind.COMPONENT, "R1");
                for (int terminal = 1; terminal <= 2; terminal++) {
                    String padId = plan.idFor(block, EntityKind.PAD, "R1." + terminal);
                    CircuitMeasurementEndpoint endpoint = board.getSimulationBindings()
                        .getEndpoint(padId);
                    if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                        throw new IllegalStateException("Composed mapping has no CircuitJS endpoint: " +
                            padId);
                    CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
                    endpointManifest.put(block + "/R1_" + terminal,
                        new EndpointManifest(block + "/R1_" + terminal, block,
                            "R1_" + terminal, padId, board.getPad(padId).getNetId(),
                            post.getElement(), post.getPostIndex()));
                }
                mappedIdentityCount += 2;
                if (board.getComponent(componentId) == null)
                    throw new IllegalStateException("Composed mapping has no component: " +
                        componentId);
            }
            // The fixed connector endpoints are retained in the same receipt.
            addFoundationManifest("J1.1", "J1", "1");
            addFoundationManifest("J1.2", "J1", "2");
        }

        private void bindControlledMappingsAndLayout() {
            layout = ControlledIndicatorPcbLayoutFactory.create(board, specifications, plan);
            layout.validateGeometry(board);
            addControlledEndpointManifest("power-adapter", "J1.1", "J1_1");
            addControlledEndpointManifest("power-adapter", "J1.2", "J1_2");
            addControlledEndpointManifest("control-adapter", "J2.1", "J2_1");
            addControlledEndpointManifest("control-adapter", "J2.2", "J2_2");
            addControlledEndpointManifest("driver", "RG.1", "RG_1");
            addControlledEndpointManifest("driver", "RG.2", "RG_2");
            addControlledEndpointManifest("driver", "RPD.1", "RPD_1");
            addControlledEndpointManifest("driver", "RPD.2", "RPD_2");
            addControlledEndpointManifest("driver", "Q1.G", "Q1_G");
            addControlledEndpointManifest("driver", "Q1.D", "Q1_D");
            addControlledEndpointManifest("driver", "Q1.S", "Q1_S");
            addControlledEndpointManifest("load", "RLOAD.1", "RLOAD_1");
            addControlledEndpointManifest("load", "RLOAD.2", "RLOAD_2");
            addControlledEndpointManifest("load", "LED1.A", "LED1_A");
            addControlledEndpointManifest("load", "LED1.K", "LED1_K");
            mappedIdentityCount += board.getPadIds().size();
        }

        private void addControlledEndpointManifest(String block, String localPad,
                String localEndpoint) {
            String padId = plan.idFor(block, EntityKind.PAD, localPad);
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings()
                .getEndpoint(padId);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Controlled mapping has no CircuitJS endpoint: " +
                    padId);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            String key = block + "/" + localEndpoint;
            endpointManifest.put(key, new EndpointManifest(key, block, localEndpoint, padId,
                board.getPad(padId).getNetId(), post.getElement(), post.getPostIndex()));
        }

        private void addFoundationManifest(String key, String component, String terminal) {
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(key);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Composed connector mapping has no endpoint: " + key);
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            endpointManifest.put(key, new EndpointManifest(key, component, component + "_" + terminal,
                key, board.getPad(key).getNetId(), post.getElement(), post.getPostIndex()));
        }

        void buildPhysicalRuntime() {
            if (plan.isControlledIndicator()) {
                buildControlledPhysicalRuntime();
                return;
            }
            runtime = new PhysicalBoardRuntime(board);
            String sourceComponent = plan.idFor("source", EntityKind.COMPONENT, "R1");
            String loadComponent = plan.idFor("load", EntityKind.COMPONENT, "R1");
            ComposedBlockContribution sourceContribution = plan.getBlocks().get("source");
            ComposedBlockContribution loadContribution = plan.getBlocks().get("load");
            requireContribution(sourceContribution, "source");
            requireContribution(loadContribution, "load");
            PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
            PhysicalBoardSlot sourceSlot = runtime.createSlot(sourceComponent);
            PhysicalBoardSlot loadSlot = runtime.createSlot(loadComponent);
            PhysicalPartInventory<PhysicalResistorPart> sourceInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    sourceComponent + "/inventory/replacements", PhysicalResistorPart.class);
            PhysicalPartInventory<PhysicalResistorPart> loadInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    loadComponent + "/inventory/replacements", PhysicalResistorPart.class);
            ResistorReplacementCatalog sourceCatalog = new ResistorReplacementCatalog();
            ResistorReplacementCatalog loadCatalog = new ResistorReplacementCatalog();

            ResistorNameplate sourceSpecification =
                (ResistorNameplate) specifications.getSpecification(sourceComponent);
            ResistorNameplate loadSpecification =
                (ResistorNameplate) specifications.getSpecification(loadComponent);
            PhysicalNameplate sourcePlayerNameplate = specifications.getNameplate(sourceComponent);
            PhysicalNameplate loadPlayerNameplate = specifications.getNameplate(loadComponent);

            Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
            GeneratedFaultCandidate sourceCandidate = GeneratedFaultEngine.resistorIncorrectValue(
                sourceComponent + "/fault/" + sourceContribution.getFaultLocalId(), FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), sourceComponent,
                sourceResistor, sourceSpecification.getNominalResistanceOhms(),
                sourceContribution.getFaultEffectiveOhms());
            GeneratedFaultCandidate loadCandidate = GeneratedFaultEngine.resistorIncorrectValue(
                loadComponent + "/fault/" + loadContribution.getFaultLocalId(), FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), loadComponent,
                loadResistor, loadSpecification.getNominalResistanceOhms(),
                loadContribution.getFaultEffectiveOhms());
            candidates.add(sourceCandidate);
            candidates.add(loadCandidate);
            GeneratedFaultEngine.clearAll(candidates);
            validateContributionFault(sourceContribution, sourceComponent, sourceCandidate);
            validateContributionFault(loadContribution, loadComponent, loadCandidate);
            GeneratedFaultCandidate selected = GeneratedFaultEngine.selectHypothesis(
                ("source".equals(plan.getFaultBlockKey()) ? sourceCandidate : loadCandidate)
                    .getHypothesisKey(), candidates);
            GeneratedFault fault = selected.getFault();
            GeneratedFaultBinding faultBinding = selected.getBinding();

            PhysicalResistorPart sourceOriginal = new PhysicalResistorPart(
                sourceComponent + "/part/original", sourceSpecification, sourceSpecification,
                sourcePlayerNameplate, sourceResistor, fault == sourceCandidate.getFault() ?
                    faultBinding : null, sourceSecondary, ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    sourceComponent));
            PhysicalResistorPart loadOriginal = new PhysicalResistorPart(
                loadComponent + "/part/original", loadSpecification, loadSpecification,
                loadPlayerNameplate, loadResistor, fault == loadCandidate.getFault() ?
                    faultBinding : null, loadSecondary, ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    loadComponent));
            sourceInventory.add(sourceOriginal);
            loadInventory.add(loadOriginal);
            ReplaceableComponentSlot sourceComponentSlot = new ReplaceableComponentSlot(
                sourceComponent, sourceSpecification, sourceOriginal, sourceFirstAttachment,
                sourceSecondAttachment, sourceSlot);
            ReplaceableComponentSlot loadComponentSlot = new ReplaceableComponentSlot(
                loadComponent, loadSpecification, loadOriginal, loadFirstAttachment,
                loadSecondAttachment, loadSlot);
            String sourceCapabilityId = sourceComponent + "/capability/replaceable-resistor";
            String loadCapabilityId = loadComponent + "/capability/replaceable-resistor";
            ReplaceableResistorBoardCapability sourceCapability =
                new ReplaceableResistorBoardCapability(sourceCapabilityId, sourceComponentSlot,
                    sourceInventory, sourceCatalog);
            ReplaceableResistorBoardCapability loadCapability =
                new ReplaceableResistorBoardCapability(loadCapabilityId, loadComponentSlot,
                    loadInventory, loadCatalog);
            runtime.registerCapability(sourceCapability);
            runtime.registerCapability(loadCapability);

            validateContributionRequirements(sourceContribution, loadContribution, null);

            String sourceRepairComponent = plan.idFor("source", EntityKind.COMPONENT,
                sourceContribution.getRepairLocalComponentId());
            String loadRepairComponent = plan.idFor("load", EntityKind.COMPONENT,
                loadContribution.getRepairLocalComponentId());
            validateRepairTarget(sourceContribution, sourceRepairComponent, sourceCatalog);
            validateRepairTarget(loadContribution, loadRepairComponent, loadCatalog);

            FixedPhysicalPart<BasicPhysicalSpecification> connectorPart =
                PhysicalFoundationPartFactory.fromBoardBindings("J1",
                    (BasicPhysicalSpecification) specifications.getSpecification("J1"),
                    specifications.getNameplate("J1"), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
                    board.getSimulationBindings(), connector,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "J1"));
            j1Slot.install(connectorPart);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();

            runtimeTargets.put("source", new RuntimeTarget("source", sourceComponent,
                sourceSlot.getId(), sourceOriginal.getId(), sourceInventory.getInventoryId(),
                sourceCapabilityId, sourceContribution.getProviderTypeId(),
                sourceCandidate.getFault().getId(), sourceRepairComponent,
                runtime.getSlot(sourceRepairComponent).getId()));
            runtimeTargets.put("load", new RuntimeTarget("load", loadComponent,
                loadSlot.getId(), loadOriginal.getId(), loadInventory.getInventoryId(),
                loadCapabilityId, loadContribution.getProviderTypeId(),
                loadCandidate.getFault().getId(), loadRepairComponent,
                runtime.getSlot(loadRepairComponent).getId()));
            this.candidates = candidates;
            this.selectedCandidate = selected;
        }

        private void buildControlledPhysicalRuntime() {
            runtime = new PhysicalBoardRuntime(board);
            String rgComponent = plan.idFor("driver", EntityKind.COMPONENT, "RG");
            String rpdComponent = plan.idFor("driver", EntityKind.COMPONENT, "RPD");
            String q1Component = plan.idFor("driver", EntityKind.COMPONENT, "Q1");
            String rloadComponent = plan.idFor("load", EntityKind.COMPONENT, "RLOAD");
            String ledComponent = plan.idFor("load", EntityKind.COMPONENT, "LED1");
            String j1Component = plan.idFor("power-adapter", EntityKind.COMPONENT, "J1");
            String j2Component = plan.idFor("control-adapter", EntityKind.COMPONENT, "J2");
            PhysicalBoardSlot rgSlot = runtime.createSlot(rgComponent);
            PhysicalBoardSlot rpdSlot = runtime.createSlot(rpdComponent);
            PhysicalBoardSlot q1Slot = runtime.createSlot(q1Component);
            PhysicalBoardSlot rloadSlot = runtime.createSlot(rloadComponent);
            PhysicalBoardSlot ledSlot = runtime.createSlot(ledComponent);
            PhysicalBoardSlot j1Slot = runtime.createSlot(j1Component);
            PhysicalBoardSlot j2Slot = runtime.createSlot(j2Component);

            ResistorNameplate rgSpecification = (ResistorNameplate)
                specifications.getSpecification(rgComponent);
            ResistorNameplate rpdSpecification = (ResistorNameplate)
                specifications.getSpecification(rpdComponent);
            ResistorNameplate rloadSpecification = (ResistorNameplate)
                specifications.getSpecification(rloadComponent);
            PhysicalNameplate rgPlayerNameplate = specifications.getNameplate(rgComponent);
            PhysicalNameplate rloadPlayerNameplate = specifications.getNameplate(rloadComponent);
            PhysicalPartInventory<PhysicalResistorPart> rgInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    rgComponent + "/inventory/replacements", PhysicalResistorPart.class);
            PhysicalPartInventory<PhysicalResistorPart> rloadInventory =
                new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                    rloadComponent + "/inventory/replacements", PhysicalResistorPart.class);
            ResistorReplacementCatalog rgCatalog = new ResistorReplacementCatalog();
            ResistorReplacementCatalog rloadCatalog = new ResistorReplacementCatalog();

            ComposedBlockContribution driver = plan.getDriver();
            ComposedBlockContribution load = plan.getLoad();
            requireControlledContribution(driver, "driver");
            requireControlledContribution(load, "load");
            Vector<GeneratedFaultCandidate> controlledCandidates =
                new Vector<GeneratedFaultCandidate>();
            GeneratedFaultCandidate rgCandidate = GeneratedFaultEngine.resistorOpen(
                rgComponent + "/fault/" + driver.getFaultLocalId(),
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), rgComponent,
                controlledRgFaultSwitch);
            GeneratedFaultCandidate rloadCandidate = GeneratedFaultEngine.resistorOpen(
                rloadComponent + "/fault/" + load.getFaultLocalId(),
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                plan.getRequest().getDescriptor().getRootSeed(), rloadComponent,
                controlledRloadFaultSwitch);
            controlledCandidates.add(rgCandidate);
            controlledCandidates.add(rloadCandidate);
            GeneratedFaultEngine.clearAll(controlledCandidates);
            validateControlledFault(rgCandidate, rgComponent, driver.getFaultLocalId());
            validateControlledFault(rloadCandidate, rloadComponent, load.getFaultLocalId());
            GeneratedFaultCandidate selected = GeneratedFaultEngine.selectHypothesis(
                (ControlledIndicatorBlockContributions.DRIVER_BLOCK_KEY.equals(
                    plan.getFaultBlockKey()) ? rgCandidate : rloadCandidate).getHypothesisKey(),
                controlledCandidates);

            PhysicalResistorPart rgOriginal = new PhysicalResistorPart(
                rgComponent + "/part/original", rgSpecification, rgSpecification,
                rgPlayerNameplate, controlledRg, selected == rgCandidate ?
                    rgCandidate.getBinding() : null, controlledRgSecondary,
                ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    rgComponent));
            PhysicalResistorPart rloadOriginal = new PhysicalResistorPart(
                rloadComponent + "/part/original", rloadSpecification, rloadSpecification,
                rloadPlayerNameplate, controlledRload, selected == rloadCandidate ?
                    rloadCandidate.getBinding() : null, controlledRloadSecondary,
                ResistorPartLocation.INSTALLED,
                new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL,
                    rloadComponent));
            rgInventory.add(rgOriginal);
            rloadInventory.add(rloadOriginal);
            ReplaceableComponentSlot rgComponentSlot = new ReplaceableComponentSlot(
                rgComponent, rgSpecification, rgOriginal, controlledRgFirstAttachment,
                controlledRgSecondAttachment, rgSlot);
            ReplaceableComponentSlot rloadComponentSlot = new ReplaceableComponentSlot(
                rloadComponent, rloadSpecification, rloadOriginal,
                controlledLoadFirstAttachment, controlledRloadSecondAttachment, rloadSlot);
            String rgCapabilityId = rgComponent + "/capability/replaceable-resistor";
            String rloadCapabilityId = rloadComponent + "/capability/replaceable-resistor";
            runtime.registerCapability(new ReplaceableResistorBoardCapability(rgCapabilityId,
                rgComponentSlot, rgInventory, rgCatalog, "RG"));
            runtime.registerCapability(new ReplaceableResistorBoardCapability(rloadCapabilityId,
                rloadComponentSlot, rloadInventory, rloadCatalog, "RLOAD"));

            FixedPhysicalPart<ResistorNameplate> rpdPart =
                PhysicalFoundationPartFactory.fromSlotBindings(rpdSlot,
                    rpdSpecification, specifications.getNameplate(rpdComponent),
                    board.getSimulationBindings(),
                    controlledRpd, new PhysicalPartProvenance(
                        PhysicalPartProvenance.FIXED_GENERATED, rpdComponent));
            rpdSlot.install(rpdPart);
            FixedPhysicalPart<NmosSpecification> q1Part =
                PhysicalFoundationPartFactory.fromSlotBindings(q1Slot,
                    (NmosSpecification) specifications.getSpecification(q1Component),
                    specifications.getNameplate(q1Component),
                    board.getSimulationBindings(), controlledQ1,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,
                        q1Component));
            q1Slot.install(q1Part);
            FixedPhysicalPart<LedNameplate> ledPart =
                PhysicalFoundationPartFactory.fromSlotBindings(ledSlot,
                    (LedNameplate) specifications.getSpecification(ledComponent),
                    specifications.getNameplate(ledComponent),
                    board.getSimulationBindings(), controlledLed,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,
                        ledComponent));
            ledSlot.install(ledPart);
            FixedPhysicalPart<BasicPhysicalSpecification> j1Part =
                PhysicalFoundationPartFactory.fromSlotBindings(j1Slot,
                    (BasicPhysicalSpecification) specifications.getSpecification(j1Component),
                    specifications.getNameplate(j1Component),
                    board.getSimulationBindings(), controlledLoadConnector,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,
                        j1Component));
            j1Slot.install(j1Part);
            FixedPhysicalPart<BasicPhysicalSpecification> j2Part =
                PhysicalFoundationPartFactory.fromSlotBindings(j2Slot,
                    (BasicPhysicalSpecification) specifications.getSpecification(j2Component),
                    specifications.getNameplate(j2Component),
                    board.getSimulationBindings(), controlledControlCommand,
                    new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED,
                        j2Component));
            j2Slot.install(j2Part);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();

            runtimeTargets.put("driver", new RuntimeTarget("driver", rgComponent,
                rgSlot.getId(), rgOriginal.getId(), rgInventory.getInventoryId(),
                rgCapabilityId, driver.getProviderTypeId(), rgCandidate.getFault().getId(),
                rgComponent, rgSlot.getId()));
            runtimeTargets.put("load", new RuntimeTarget("load", rloadComponent,
                rloadSlot.getId(), rloadOriginal.getId(), rloadInventory.getInventoryId(),
                rloadCapabilityId, load.getProviderTypeId(), rloadCandidate.getFault().getId(),
                rloadComponent, rloadSlot.getId()));
            candidates = controlledCandidates;
            selectedCandidate = selected;
        }

        private void requireControlledContribution(ComposedBlockContribution contribution,
                String block) {
            if (contribution == null || contribution.getFaultSpec() == null ||
                    contribution.getFaultSpec().getKind() != ComposedBlockContribution.FaultSpec.Kind.OPEN)
                throw new IllegalStateException("Incomplete controlled " + block + " contribution");
        }

        private void validateControlledFault(GeneratedFaultCandidate candidate,
                String componentId, String localFaultId) {
            GeneratedFault fault = candidate == null ? null : candidate.getFault();
            String expectedId = componentId + "/fault/" + localFaultId;
            if (fault == null || fault.getType() != GeneratedFaultType.RESISTOR_OPEN ||
                    !expectedId.equals(fault.getId()) ||
                    !componentId.equals(fault.getTargetComponentId()) ||
                    !ControlledIndicatorDeviceBehavior.FAMILY_ID.equals(
                        fault.getCircuitFamilyId()))
                throw new IllegalStateException("Controlled fault metadata does not match " +
                    componentId);
        }

        private void requireContribution(ComposedBlockContribution contribution,
                String block) {
            if (contribution == null)
                throw new IllegalStateException("Missing resolved " + block +
                    " contribution");
            if (contribution.getFaultLocalId() == null ||
                    contribution.getFaultLocalId().length() == 0 ||
                    !finite(contribution.getFaultEffectiveOhms()) ||
                    contribution.getFaultEffectiveOhms() <= 0.0 ||
                    contribution.getRepairLocalComponentId() == null ||
                    contribution.getRepairLocalComponentId().length() == 0)
                throw new IllegalStateException("Incomplete resolved " + block +
                    " contribution metadata");
        }

        private void validateContributionFault(ComposedBlockContribution contribution,
                String componentId, GeneratedFaultCandidate candidate) {
            String expectedId = componentId + "/fault/" + contribution.getFaultLocalId();
            GeneratedFault fault = candidate == null ? null : candidate.getFault();
            if (fault == null || !expectedId.equals(fault.getId()) ||
                    !componentId.equals(fault.getTargetComponentId()) ||
                    !finite(fault.getEffectiveValue()) ||
                    fault.getEffectiveValue() != contribution.getFaultEffectiveOhms())
                throw new IllegalStateException("Resolved contribution fault metadata " +
                    "does not match the generated candidate: " + componentId);
        }

        private void validateRepairTarget(ComposedBlockContribution contribution,
                String repairComponentId, ResistorReplacementCatalog catalog) {
            if (runtime.getSlot(repairComponentId) == null ||
                    runtime.getWorkbenchPartsProvider(repairComponentId) == null ||
                    catalog.getEntries().isEmpty())
                throw new IllegalStateException("Resolved contribution repair target is " +
                    "not executable: " + repairComponentId);
            if (!contribution.getComponentLocalId().equals(
                    contribution.getRepairLocalComponentId()))
                throw new IllegalStateException("Unsupported composed repair component: " +
                    contribution.getRepairLocalComponentId());
        }

        private void validateContributionRequirements(
                ComposedBlockContribution sourceContribution,
                ComposedBlockContribution loadContribution,
                GeneratedBoardFamilyState familyState) {
            for (ComposedBlockContribution contribution : new ComposedBlockContribution[] {
                    sourceContribution, loadContribution }) {
                for (String requirement : contribution.getInputRequirements()) {
                    if (!"BOARD_POWER".equals(requirement) || board.getPowerInput(POWER_INPUT_ID) == null ||
                            powerBindings == null || !powerBindings.hasControlsForAllInputs())
                        throw new IllegalStateException("Unsupported or unsatisfied contribution " +
                            "input requirement: " + requirement);
                }
                for (String requirement : contribution.getRetestRequirements()) {
                    if (!"STEADY_DC_POWERED".equals(requirement) ||
                            (familyState != null &&
                                (familyState.getCustomerRetestProfile() == null ||
                                 familyState.getOperationCatalog() == null ||
                                 familyState.getCustomerRetestProfile().getRequiredPowerTransition() == null ||
                                 familyState.getCustomerRetestProfile().getRequiredInputTransition() == null ||
                                 familyState.getOperationCatalog().find(
                                     GeneratedBoardOperationIds.CUSTOMER_RETEST) == null)))
                        throw new IllegalStateException("Unsupported contribution retest requirement: " +
                            requirement);
                }
            }
        }

        private Vector<GeneratedFaultCandidate> candidates;
        private GeneratedFaultCandidate selectedCandidate;

        void buildInstance() {
            if (plan.isControlledIndicator()) {
                buildControlledInstance();
                return;
            }
            ComposedResistiveDeviceBehavior behavior =
                new ComposedResistiveDeviceBehavior(plan);
            GeneratedBoardFamilyState familyState = behavior.createFamilyState();
            validateContributionRequirements(plan.getBlocks().get("source"),
                plan.getBlocks().get("load"), familyState);
            GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
                behavior.createScenarioCatalog();
            GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
                "RESISTIVE_COUPLING_CHALLENGE", FAMILY_ID, TOPOLOGY_VARIANT_ID,
                plan.getRequest().getDescriptor().getRootSeed(), scenarios,
                "Repair verified. The resistive transfer path operates normally.",
                selectedCandidate.getFault(), selectedCandidate.getBinding(), behavior);
            GeneratedDiagnosticSolvabilityContract diagnostic =
                GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(FAMILY_ID,
                    TOPOLOGY_VARIANT_ID, plan.getRequest().getDescriptor().getRootSeed(), candidates);
            instance = new GeneratedBoardInstance(board, elements,
                plan.getRequest().getDescriptor().getRootSeed(), FAMILY_ID,
                TOPOLOGY_VARIANT_ID,
                "Generated resistive coupling canary, seed " +
                    plan.getRequest().getDescriptor().getRootSeed(),
                componentBindings, powerBindings, connectionBindings, behavior, layout,
                specifications, selectedCandidate.getBinding(), null, challenge, familyState,
                runtime, null, true, candidates, diagnostic);
            // Construction has already run the strict board/connection/runtime
            // checks.  Repeat the cheap detached checks before publication.
            board.validate();
            layout.validateGeometry(board);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();
        }

        private void buildControlledInstance() {
            ControlledIndicatorDeviceBehavior behavior =
                new ControlledIndicatorDeviceBehavior(plan, controlledControlCommand);
            GeneratedBoardFamilyState familyState = behavior.createFamilyState();
            GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
                behavior.createScenarioCatalog();
            GeneratedComponentOperationalStates operationalStates =
                new GeneratedComponentOperationalStates();
            operationalStates.bindLed(plan.idFor("load", EntityKind.COMPONENT, "LED1"),
                controlledLed);
            GeneratedFault fault = selectedCandidate.getFault();
            GeneratedFaultBinding faultBinding = selectedCandidate.getBinding();
            GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
                "CONTROLLED_INDICATOR_CHALLENGE",
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                ControlledIndicatorDeviceBehavior.TOPOLOGY_VARIANT_ID,
                plan.getRequest().getDescriptor().getRootSeed(), scenarios,
                "Repair verified. The controlled indicator switches normally.",
                fault, faultBinding, behavior);
            instance = new GeneratedBoardInstance(board, elements,
                plan.getRequest().getDescriptor().getRootSeed(),
                ControlledIndicatorDeviceBehavior.FAMILY_ID,
                ControlledIndicatorDeviceBehavior.TOPOLOGY_VARIANT_ID,
                "Generated controlled indicator, seed " +
                    plan.getRequest().getDescriptor().getRootSeed(),
                componentBindings, powerBindings, connectionBindings, behavior, layout,
                specifications, faultBinding, operationalStates, challenge, familyState, runtime, null,
                false, candidates, null);
            board.validate();
            layout.validateGeometry(board);
            runtime.validateSupportedCompositionProviders();
            runtime.validate();
        }

        Result result() {
            return new Result(instance, plan, runtimeTargets, endpointManifest);
        }

        void begin(Stage stage) {
            if (stage == null)
                throw new IllegalArgumentException("Assembly stage is required");
            currentStage = stage;
        }

        Stage getCurrentStage() { return currentStage; }

        int allocatedElementCount() { return elements.size(); }
        int registeredPartCount() { return runtime == null ? 0 : runtime.getPhysicalParts().size(); }
        int mergeCount() { return plan.getMergeProvenance().size(); }

        void after(Stage stage) {
            if (probe != null)
                probe.after(stage);
        }

        boolean dispose() {
            boolean success = true;
            try {
                if (powerBindings != null && powerBindings.hasControlsForAllInputs())
                    powerBindings.setConnected(false);
            } catch (Throwable failure) {
                success = false;
            }
            // Candidate elements have never been placed in the active graph.
            // delete() only needs the simulator for slider cleanup, so avoid
            // calling it in pure JVM construction tests without an initialized
            // CircuitElm owner.
            if (CircuitElm.sim != null) {
                for (int index = elements.size() - 1; index >= 0; index--) {
                    try {
                        elements.get(index).delete();
                    } catch (Throwable failure) {
                        success = false;
                    }
                }
            }
            return success;
        }

        private void add(CircuitElm... values) {
            for (CircuitElm value : values) {
                if (value == null)
                    throw new IllegalArgumentException("Composed graph contains a null element");
                if (elements.contains(value))
                    throw new IllegalStateException("Composed graph allocated an element twice");
                elements.add(value);
            }
        }

        private WireElm wire(int x, int y, int x2, int y2) {
            WireElm result = new WireElm(snap(x), snap(y));
            add(result);
            result.drag(x2, y2);
            return result;
        }

        private ResistorElm resistor(int x, int y, int x2, int y2, double resistance) {
            ResistorElm result = new ResistorElm(snap(x), snap(y));
            add(result);
            result.drag(x2, y2);
            result.setResistance(resistance);
            return result;
        }

        private int snap(int value) {
            if (CircuitElm.sim == null)
                throw new IllegalStateException("CircuitJS owner is required for graph allocation");
            return CircuitElm.sim.snapGrid(value);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
